package com.handwrite.rpc.core.proxy;

import com.handwrite.rpc.api.RpcRequest;
import com.handwrite.rpc.api.RpcResponse;
import com.handwrite.rpc.common.RpcLogger;
import com.handwrite.rpc.common.exception.RpcException;
import com.handwrite.rpc.common.exception.RpcTimeoutException;
import com.handwrite.rpc.core.discovery.ServiceResolver;
import com.handwrite.rpc.core.registry.ServiceProvider;
import com.handwrite.rpc.core.transport.NettyClient;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 动态代理工厂 —— 三种模式并存,正好对应项目三个里程碑的演进:
 *
 *   M1 本地模式      new RpcClientProxy(ServiceProvider)  → 查本地表 + 反射执行(伪 RPC)
 *   M2 直连模式      new RpcClientProxy(NettyClient)      → 直连一个写死的地址
 *   M3+M4 发现模式   new RpcClientProxy(ServiceResolver)  → 注册中心发现 + 负载均衡 + 容错
 *
 * 三种模式靠构造器重载区分,内部按"哪个字段非 null"分发。
 */
public class RpcClientProxy {

    // ── 模式一:本地伪 RPC(M1)──
    private final ServiceProvider serviceProvider;

    // ── 模式二:直连固定地址(M2)──
    private final NettyClient nettyClient;

    // ── 模式三:发现 + 负载均衡 + 容错(M3 / M4)──
    private final ServiceResolver serviceResolver;
    private final int maxRetries;

    /**
     * 按地址缓存客户端连接 —— 连接复用。
     * 每个地址保持一条长连接,而不是每次调用都新建(新建 TCP 要三次握手,很贵)。
     */
    private final Map<String, NettyClient> clientPool = new ConcurrentHashMap<>();

    /** M1 本地模式 */
    public RpcClientProxy(ServiceProvider serviceProvider) {
        this.serviceProvider = serviceProvider;
        this.nettyClient = null;
        this.serviceResolver = null;
        this.maxRetries = 0;
    }

    /** M2 网络直连模式 */
    public RpcClientProxy(NettyClient nettyClient) {
        this.serviceProvider = null;
        this.nettyClient = nettyClient;
        this.serviceResolver = null;
        this.maxRetries = 0;
    }

    /** M3/M4 发现模式(默认最多额外重试 2 次) */
    public RpcClientProxy(ServiceResolver serviceResolver) {
        this(serviceResolver, 2);
    }

    /**
     * M3/M4 发现模式。
     *
     * @param maxRetries 失败后最多再换几个节点重试。
     *                   ★ 传 0   → Failfast(快速失败,一次都不重试)
     *                   ★ 传 >0  → Failover(换节点重试)
     *                   两种容错策略,在这里只是【一个数字】的差别
     */
    public RpcClientProxy(ServiceResolver serviceResolver, int maxRetries) {
        this.serviceProvider = null;
        this.nettyClient = null;
        this.serviceResolver = serviceResolver;
        this.maxRetries = Math.max(0, maxRetries);
    }

    /**
     * 为指定接口生成代理对象。
     * 调用方拿到的是运行时动态生成的"替身",像调本地方法一样用它。
     */
    @SuppressWarnings("unchecked")
    public <T> T getProxy(Class<T> interfaceClass) {
        InvocationHandler handler = (proxy, method, args) -> {
            // 1) 组装请求:一次远程调用的完整描述
            RpcRequest request = new RpcRequest(
                    interfaceClass.getName(),
                    method.getName(),
                    method.getParameterTypes(),
                    args == null ? new Object[0] : args);

            // 2) 按当前模式选择传输方式
            RpcResponse response;
            if (serviceProvider != null) {
                response = localInvoke(request);              // M1
            } else if (nettyClient != null) {
                response = nettyClient.sendRequest(request);  // M2
            } else {
                response = remoteInvokeWithFailover(request); // M3 + M4
            }

            // 3) 失败则把远端错误抛给调用方
            if (!response.isSuccess()) {
                throw new RpcException("RPC 调用失败: " + response.getErrorMessage());
            }
            return response.getData();
        };
        return (T) Proxy.newProxyInstance(
                interfaceClass.getClassLoader(),
                new Class<?>[]{interfaceClass},
                handler);
    }

    // ─────────────────────────────────────────────────────
    //  M3 + M4:服务发现 + 负载均衡 + 容错
    // ─────────────────────────────────────────────────────

    private RpcResponse remoteInvokeWithFailover(RpcRequest request) throws Exception {
        String serviceName = request.getServiceName();

        // 1) 拿到【全部】候选地址(内部已排序,顺序确定)
        List<String> candidates = new ArrayList<>(serviceResolver.resolveAll(serviceName));

        // 2) 让负载均衡器决定"先试哪一个",再把它排到队首
        String preferred = serviceResolver.getLoadBalancer()
                .select(serviceName, candidates, hashKeyOf(request));
        candidates.remove(preferred);
        candidates.add(0, preferred);

        // 3) Failover:依次尝试,直到成功或用完机会
        int attempts = Math.min(candidates.size(), maxRetries + 1);
        RpcException lastError = null;

        for (int i = 0; i < attempts; i++) {
            String address = candidates.get(i);
            try {
                RpcLogger.trace("[Consumer] 第 " + (i + 1) + " 次尝试 → " + address
                        + " ,策略=" + serviceResolver.getLoadBalancer().name());
                return clientFor(address).sendRequest(request);

            } catch (RpcTimeoutException e) {
                // ⚠️ 超时【不重试】:请求可能已经在服务端执行完了,只是响应丢了。
                //    重试可能造成重复执行 —— 比如"扣款"被执行两次。
                //    这正是面试高频考点:「什么请求不能盲目重试?」
                //    答案核心是【幂等性】:幂等的可以重试,非幂等的要非常谨慎。
                RpcLogger.trace("[Consumer] " + address + " 调用超时,按策略【不重试】");
                throw e;

            } catch (Exception e) {
                // 连接类失败:请求根本没发出去 → 换一台重试是安全的
                lastError = new RpcException("调用 " + address + " 失败: " + e.getMessage(), e);
                RpcLogger.trace("[Consumer] " + address + " 失败,准备换节点重试: " + e.getMessage());
                discardClient(address);      // 坏连接从池里清掉,下次重建
            }
        }

        throw lastError != null ? lastError
                : new RpcException("调用失败,没有可用实例: " + serviceName);
    }

    /** 取(或创建)某个地址对应的客户端。双重检查锁,避免并发下重复创建连接。 */
    private NettyClient clientFor(String address) {
        NettyClient cached = clientPool.get(address);
        if (cached != null) {
            return cached;
        }
        synchronized (clientPool) {
            cached = clientPool.get(address);
            if (cached != null) {
                return cached;
            }
            NettyClient client = new NettyClient(
                    ServiceResolver.hostOf(address),
                    ServiceResolver.portOf(address));
            clientPool.put(address, client);
            RpcLogger.trace("[Consumer] 为 " + address + " 新建了一条连接");
            return client;
        }
    }

    /** 把一个地址的连接从池里移除并关闭 */
    private void discardClient(String address) {
        NettyClient dead = clientPool.remove(address);
        if (dead != null) {
            try {
                dead.close();
            } catch (Exception ignored) {
                // 关不掉的连接不值得再往外抛异常
            }
        }
    }

    /**
     * 一致性哈希用的"粘性 key"。
     * 这里取【第一个参数】—— 约定"第一个参数是 userId / 订单号"这类业务主键,
     * 于是同一个用户的请求会稳定落到同一台机器上(服务端本地缓存才有效)。
     */
    private String hashKeyOf(RpcRequest request) {
        Object[] params = request.getParameters();
        if (params != null && params.length > 0 && params[0] != null) {
            return String.valueOf(params[0]);
        }
        return request.getServiceName();
    }

    /** 关闭所有缓存连接(调用方退出前记得调) */
    public void close() {
        if (nettyClient != null) {
            nettyClient.close();
        }
        clientPool.forEach((addr, client) -> {
            try {
                client.close();
            } catch (Exception ignored) {
            }
        });
        clientPool.clear();
    }

    // ─────────────────────────────────────────────────────
    //  M1:本地伪 RPC
    // ─────────────────────────────────────────────────────

    private RpcResponse localInvoke(RpcRequest request) {
        Object service = serviceProvider.getService(request.getServiceName());
        if (service == null) {
            return new RpcResponse(null, "没有找到服务: " + request.getServiceName()
                    + ", 请确认 Provider 已注册该服务");
        }
        try {
            Object result = invokeMethod(service, request);
            return new RpcResponse(result, null);
        } catch (Throwable t) {
            return new RpcResponse(null, t.getMessage());
        }
    }

    /**
     * 反射执行(M1 本地模式用)。
     * 注意:Provider 端的同类逻辑写在 RpcRequestHandler 里 —— 两边各有一份,
     *       因为一个跑在调用方进程、一个跑在服务方进程,无法共享代码。
     */
    private Object invokeMethod(Object service, RpcRequest request) throws Throwable {
        Method method = service.getClass().getMethod(
                request.getMethodName(), request.getParameterTypes());
        try {
            return method.invoke(service, request.getParameters());
        } catch (InvocationTargetException e) {
            // 把实现类真实抛出的异常原样传出(剥掉反射的包装)
            throw e.getCause();
        }
    }
}
