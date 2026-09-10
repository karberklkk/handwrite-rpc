package com.handwrite.rpc.core.transport;

import com.handwrite.rpc.api.RpcResponse;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

import java.util.function.Consumer;

/**
 * NettyClient 内部处理器:收到 RpcResponse 后交给回调处理。
 *
 * M2-3 改造:不再往共享队列里塞,而是把响应交给 NettyClient 按 requestId 唤醒对应 Future。
 */
public class SimpleClientHandler extends SimpleChannelInboundHandler<RpcResponse> {

    private final Consumer<RpcResponse> onResponse;

    public SimpleClientHandler(Consumer<RpcResponse> onResponse) {
        this.onResponse = onResponse;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, RpcResponse response) {
        onResponse.accept(response);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        System.out.println("[Consumer] 连接异常,已关闭: " + cause.getMessage());
        ctx.close();
    }
}
