package com.handwrite.rpc.core.proxy;

import com.handwrite.rpc.api.RpcRequest;
import com.handwrite.rpc.api.RpcResponse;
import com.handwrite.rpc.core.registry.ServiceRegistry;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * M1 动态代理工厂(伪 RPC 版):
 * 为 Consumer 生成服务接口的"替身",替身被调用时按如下链路处理:
 * 1) 组装 RpcRequest(服务名/方法名/参数类型/参数)
 * 2) 从本地 ServiceRegistry 找到实现对象(伪 RPC 的关键:不发网络)
 * 3) 反射执行真实方法
 * 4) 结果装进 RpcResponse 返回(异常则原样抛给调用方)
 *
 * M2 中只需把第 2 步替换成"序列化后经 Netty 发给 Provider",其余保持不变。
 */
public class RpcClientProxy {

    private final ServiceRegistry serviceRegistry;

    public RpcClientProxy(ServiceRegistry serviceRegistry) {
        this.serviceRegistry = serviceRegistry;
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

            // 2) 伪 RPC:从本地注册表取实现对象(真 RPC 这里是网络发送)
            Object service = serviceRegistry.getService(interfaceClass.getName());
            if (service == null) {
                throw new RuntimeException("没有找到服务: " + interfaceClass.getName()
                        + ", 请确认 Provider 已注册该服务");
            }

            // 3) 反射执行,并把实现类真实抛出的异常原样传给调用方
            Object result;
            try {
                result = method.invoke(service, request.getParameters());
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }

            // 4) 包装返回(消息对象开始派上用场)
            RpcResponse response = new RpcResponse(result, null);
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
}
