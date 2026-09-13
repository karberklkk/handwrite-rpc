package com.handwrite.rpc.example;

import com.handwrite.rpc.common.exception.RpcException;
import com.handwrite.rpc.common.registry.ServiceDiscovery;
import com.handwrite.rpc.common.registry.ServiceRegistry;
import com.handwrite.rpc.core.discovery.ServiceResolver;
import com.handwrite.rpc.core.loadbalance.RoundRobinLoadBalancer;
import com.handwrite.rpc.core.proxy.RpcClientProxy;
import com.handwrite.rpc.core.registry.LocalServiceRegistry;
import com.handwrite.rpc.core.registry.ServiceProvider;
import com.handwrite.rpc.core.transport.NettyServer;

import java.util.List;

/**
 * M3 阶段 A 验收 Demo:在【同一个 JVM 内】跑通
 *     注册 → 发现 → 负载均衡选地址 → 真网络调用
 *
 * ── 为什么要"同一个 JVM" ──────────────────────────────────
 * LocalServiceRegistry 的数据存在【本进程的内存】里。
 * 如果 Provider 和 Consumer 是两个进程,Consumer 根本读不到注册信息。
 * 所以这一步先在同进程里把【接线】验证正确,跨进程留给 ZooKeeper 那一步。
 *
 * 这一步验证了什么:
 *   ✅ Provider 启动时把 host:port 注册进注册中心
 *   ✅ Consumer 通过 discover() 拿到地址,而不是硬编码
 *   ✅ 地址解析(host:port 拆分)正确
 *   ✅ 注册中心里没有该服务时,抛出【清晰的异常】而不是 NPE
 *   ✅ 注销之后 discover 返回空
 */
public class M3LocalDemo {

    private static final int PORT = 28899;
    private static final String SERVICE = HelloService.class.getName();

    public static void main(String[] args) throws Exception {
        System.out.println("==================== M3 阶段 A:单进程注册发现 ====================");

        // ── 1. 注册中心(一个对象,两个视角)──
        LocalServiceRegistry registry = new LocalServiceRegistry();
        ServiceRegistry registrar = registry;    // 注册视角
        ServiceDiscovery discovery = registry;   // 发现视角

        // ── 2. Provider:注册实现 + 启动服务器 ──
        ServiceProvider serviceProvider = new ServiceProvider();
        serviceProvider.register(SERVICE, new HelloServiceImpl());

        NettyServer server = new NettyServer(PORT, serviceProvider);
        Thread providerThread = new Thread(() -> {
            try {
                server.start();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "provider-thread");
        providerThread.setDaemon(true);          // 主线程结束时自动收尾
        providerThread.start();

        waitUntilRunning(server, 5000);
        System.out.println("[Provider] 服务器已就绪,地址: " + server.localAddress());

        // ── 3. Provider 把地址"报到"注册中心 ──
        String address = server.localAddress();
        registrar.register(SERVICE, address);
        System.out.println("[Provider] 已注册: " + SERVICE);
        System.out.println("           → " + address);

        // ── 4. Consumer:用 discover 找到地址(不再硬编码!)──
        List<String> found = discovery.discover(SERVICE);
        System.out.println("[Consumer] discover 结果: " + found);
        check("discover 能拿到 1 个地址", found.size() == 1);
        check("地址内容正确", found.contains(address));

        // ── 5. 组装 Consumer:发现 + 负载均衡 + 代理 ──
        ServiceResolver resolver = new ServiceResolver(discovery, new RoundRobinLoadBalancer());
        RpcClientProxy proxyFactory = new RpcClientProxy(resolver);
        HelloService helloService = proxyFactory.getProxy(HelloService.class);

        System.out.println();
        System.out.println("[Consumer] 开始通过【发现到的地址】发起真实 RPC 调用:");
        for (int i = 1; i <= 3; i++) {
            String result = helloService.sayHello("m3-" + i);
            System.out.println("           第 " + i + " 次 → " + result);
        }
        check("三次调用都成功", true);

        // ── 6. 验证"没有实例"时抛出清晰异常(而不是 NPE)──
        System.out.println();
        System.out.println("[Consumer] 验证查一个【不存在】的服务:");
        try {
            resolver.resolve("com.handwrite.rpc.example.NotExistService", null);
            check("不存在的服务应该抛异常", false);
        } catch (RpcException e) {
            System.out.println("           捕获到预期异常: " + e.getMessage());
            check("不存在的服务抛出 RpcException(不是 NPE)", true);
        }

        // ── 7. 注销:Provider 优雅下线 ──
        System.out.println();
        System.out.println("[Provider] 模拟优雅下线,注销地址...");
        registrar.unregister(SERVICE, address);
        List<String> afterUnregister = discovery.discover(SERVICE);
        System.out.println("[Consumer] 注销后 discover 结果: " + afterUnregister + " (size="
                + afterUnregister.size() + ")");
        check("注销后 discover 到 0 个", afterUnregister.isEmpty());

        // ── 8. 收尾 ──
        proxyFactory.close();
        server.close();
        System.out.println();
        System.out.println("==================== M3 阶段 A 验收结束 ====================");
    }

    /** 轮询等待服务器进入监听状态(最多等 timeoutMillis 毫秒) */
    private static void waitUntilRunning(NettyServer server, long timeoutMillis)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (!server.isRunning()) {
            if (System.currentTimeMillis() > deadline) {
                throw new IllegalStateException("Provider 启动超时(端口可能被占用)");
            }
            Thread.sleep(50);
        }
    }

    private static void check(String title, boolean ok) {
        System.out.println((ok ? "   [PASS] " : "   [FAIL] ") + title);
    }
}
