package com.handwrite.rpc.core.proxy;

import com.handwrite.rpc.api.RpcRequest;
import com.handwrite.rpc.api.RpcResponse;
import com.handwrite.rpc.core.registry.ServiceProvider;
import com.handwrite.rpc.core.transport.NettyClient;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * 动态代理工厂:
 * 为 Consumer 生成服务接口的"替身",替身被调用时按如下链路处理:
 * 1) 组装 RpcRequest(服务名/方法名/参数类型/参数)
 * 2) 两种"传输"二选一:
 *    - 本地模式(构造器传入 ServiceProvider):查 Map 拿实现对象,反射执行 —— M1 伪 RPC
 *    - 网络模式(构造器传入 NettyClient):把请求发给远端 Provider,等待响应 —— M2
 * 3) 结果装进 RpcResponse 返回(异常则原样抛给调用方)
 *
 * 两种模式并存只是过渡,后续里程碑(M3/M5)会通过注册中心 + SPI 统一。
 */
public class RpcClientProxy {

    private final ServiceProvider serviceProvider;
    private final NettyClient nettyClient;

    /** M1 本地模式 */
    public RpcClientProxy(ServiceProvider serviceProvider) {
        this.serviceProvider = serviceProvider;
        this.nettyClient = null;
    }

    /** M2 网络模式 */
    public RpcClientProxy(NettyClient nettyClient) {
        this.serviceProvider = null;
        this.nettyClient = nettyClient;
    }

    /**
     * 为指定接口生成代理对象
     */
    @SuppressWarnings("unchecked")
    public <T> T getProxy(Class<T> interfaceClass) {
        InvocationHandler handler = (proxy, method, args) -> {
            // 1) 组装请求:一次远程调用的完整描述
            RpcRequest request = new RpcRequest(
                    interfaceClass.getName(),      // 服务名:接口全限定名
                    method.getName(),              // 方法名
                    method.getParameterTypes(),    // 参数类型(反射定位方法必需)
                    args == null ? new Object[0] : args); // 实参

            // 2) 传输请求,拿到响应
            RpcResponse response;
            if (serviceProvider != null) {
                response = localInvoke(request);          // M1:本地伪 RPC
            } else {
                response = nettyClient.sendRequest(request); // M2:真·网络 RPC
            }

            // 3) 失败则把远端错误抛给调用方
            if (!response.isSuccess()) {
                throw new RuntimeException("RPC 调用失败: " + response.getErrorMessage());
            }
            return response.getData();
        };
        return (T) Proxy.newProxyInstance(
                interfaceClass.getClassLoader(),
                new Class<?>[]{interfaceClass},
                handler);
    }

    /**
     * M1 本地模式:查注册表 + 反射执行(伪 RPC 的"假网络")
     */
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
     * 反射执行(本地与远端共用的核心逻辑)
     */
    private Object invokeMethod(Object service, RpcRequest request) throws Throwable {
        Method method = service.getClass().getMethod(
                request.getMethodName(), request.getParameterTypes());
        try {
            return method.invoke(service, request.getParameters());
        } catch (InvocationTargetException e) {
            // 把实现类真实抛出的异常原样传出
            throw e.getCause();
        }
    }
}
