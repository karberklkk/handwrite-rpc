package com.handwrite.rpc.core.transport;

import com.handwrite.rpc.api.RpcRequest;
import com.handwrite.rpc.api.RpcResponse;
import com.handwrite.rpc.common.RpcConstants;
import com.handwrite.rpc.common.RpcLogger;
import com.handwrite.rpc.core.serialize.JdkSerializer;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

/**
 * 出站编码器(M2-2 自研协议):
 * RpcRequest / RpcResponse → 序列化成 body → 加 17 字节协议头 → ByteBuf 写出。
 *
 * 注意:这里继承了 Netty 的 MessageToByteEncoder,它只处理 ByteBuf 输出的消息类型,
 * 我们统一收 Object,内部按 instanceof 判断消息类型。
 */
public class RpcEncoder extends MessageToByteEncoder<Object> {

    private final JdkSerializer serializer = new JdkSerializer();

    @Override
    protected void encode(ChannelHandlerContext ctx, Object msg, ByteBuf out) {
        // 1) 判断消息类型与 requestId
        byte messageType;
        long requestId;
        if (msg instanceof RpcRequest request) {
            messageType = RpcConstants.TYPE_REQUEST;
            requestId = request.getRequestId();
        } else if (msg instanceof RpcResponse response) {
            messageType = RpcConstants.TYPE_RESPONSE;
            requestId = response.getRequestId();
        } else {
            throw new IllegalArgumentException("不支持的消息类型: " + msg.getClass().getName());
        }

        // 2) 序列化 body
        byte[] body = serializer.serialize(msg);

        // 3) 写协议头(顺序必须与 RpcDecoder 完全一致!)
        out.writeShort(RpcConstants.MAGIC);
        out.writeByte(RpcConstants.VERSION);
        out.writeByte(messageType);
        out.writeByte(RpcConstants.SERIALIZER_JDK);
        out.writeLong(requestId);
        out.writeInt(body.length);

        // 4) 写 body
        out.writeBytes(body);

        // trace:观察协议帧(可用 -Drpc.trace=false 关闭)
        RpcLogger.trace("[Encoder] 发出帧: type=" + messageType + ", requestId=" + requestId
                + ", body=" + body.length + "B, 总长=" + (RpcConstants.HEADER_LENGTH + body.length) + "B"
                + ", 线程=" + Thread.currentThread().getName());
    }
}
