package com.handwrite.rpc.core.transport;

import com.handwrite.rpc.api.RpcRequest;
import com.handwrite.rpc.api.RpcResponse;
import com.handwrite.rpc.common.RpcLogger;
import com.handwrite.rpc.core.registry.ServiceProvider;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * M2-1 Provider 端业务处理器:
 * 收到 RpcRequest → 从注册表找实现 → 反射执行 → 结果/错误装进 RpcResponse 回写。
 *
 * 注意:错误信息放在 RpcResponse.errorMessage 里"传回去",
 * 而不是把异常对象抛过网络(异常类序列化不可靠,这是 RPC 通用做法)。
 */
public class RpcRequestHandler extends SimpleChannelInboundHandler<RpcRequest> {

    private final ServiceProvider serviceProvider;

    public RpcRequestHandler(ServiceProvider serviceProvider) {
        this.serviceProvider = serviceProvider;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, RpcRequest request) {
        RpcLogger.trace("[Provider] 收到请求 requestId=" + request.getRequestId()
                + ", service=" + request.getServiceName()
                + ", method=" + request.getMethodName()
                + ", 线程=" + Thread.currentThread().getName());
        RpcResponse response;
        try {
            // 1) 按服务名找实现对象
            Object service = serviceProvider.getService(request.getServiceName());
            if (service == null) {
                response = new RpcResponse(null, "没有找到服务: " + request.getServiceName());
            } else {
                // 2) 反射定位方法并执行(parameterTypes 在这里派上用场)
                Method method = service.getClass().getMethod(
                        request.getMethodName(), request.getParameterTypes());
                Object result = method.invoke(service, request.getParameters());
                response = new RpcResponse(result, null);
            }
        } catch (InvocationTargetException e) {
            // 实现类抛的真实异常,把它的信息带回给调用方
            Throwable cause = e.getCause();
            response = new RpcResponse(null,
                    cause != null ? cause.getMessage() : e.getMessage());
        } catch (Throwable t) {
            response = new RpcResponse(null, t.getMessage());
        }
        // 3) 原样回填 requestId:Consumer 靠它把响应对应回那次调用
        response.setRequestId(request.getRequestId());
        // 4) 回写响应
        ctx.writeAndFlush(response);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        // 协议异常(如魔数不对)不应该拖垮服务器:记录一行并关闭这条连接
        System.out.println("[Provider] 连接异常,已关闭: " + cause.getMessage());
        ctx.close();
    }
}
