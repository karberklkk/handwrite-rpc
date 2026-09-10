package com.handwrite.rpc.core.transport;

import com.handwrite.rpc.api.RpcRequest;
import com.handwrite.rpc.api.RpcResponse;
import com.handwrite.rpc.common.RpcConstants;
import com.handwrite.rpc.common.RpcLogger;
import com.handwrite.rpc.core.serialize.JdkSerializer;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;

import java.util.List;

/**
 * 入站解码器(M2-2 自研协议):
 * 字节 → 校验魔数 → 解析 17 字节头 → 读 body → 反序列化成 RpcRequest/RpcResponse。
 *
 * 前面已经由 LengthFieldBasedFrameDecoder 按"长度字段"切好完整的帧,
 * 所以这里一般能一次拿到完整报文;但仍保留"半包等待"的判断,便于理解边界处理。
 */
public class RpcDecoder extends ByteToMessageDecoder {

    private final JdkSerializer serializer = new JdkSerializer();

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
        // 头都还没收全,等下一次数据
        if (in.readableBytes() < RpcConstants.HEADER_LENGTH) {
            return;
        }
        in.markReaderIndex();

        // 1) 校验魔数
        short magic = in.readShort();
        if (magic != RpcConstants.MAGIC) {
            throw new IllegalArgumentException(String.format(
                    "非法魔数 0x%04X,不是本协议的数据,关闭连接", magic & 0xFFFF));
        }

        // 2) 解析头部剩余字段(顺序必须与 RpcEncoder 一致!)
        byte version = in.readByte();
        byte messageType = in.readByte();
        byte serializerType = in.readByte();
        long requestId = in.readLong();
        int bodyLength = in.readInt();

        if (version != RpcConstants.VERSION) {
            throw new IllegalArgumentException("不支持的协议版本: " + version);
        }
        if (bodyLength < 0 || bodyLength > RpcConstants.MAX_FRAME_LENGTH) {
            throw new IllegalArgumentException("非法的 body 长度: " + bodyLength);
        }

        // 3) body 还没收全(半包)→ 回退读指针,等下一批数据
        if (in.readableBytes() < bodyLength) {
            in.resetReaderIndex();
            return;
        }

        // 4) 读取 body 并反序列化
        byte[] body = new byte[bodyLength];
        in.readBytes(body);
        Object message = serializer.deserialize(body);

        // 5) 让对象里的 requestId 与协议头保持一致(响应靠它匹配请求)
        if (message instanceof RpcRequest request) {
            request.setRequestId(requestId);
        } else if (message instanceof RpcResponse response) {
            response.setRequestId(requestId);
        } else {
            throw new IllegalArgumentException("未知消息对象: " + message.getClass().getName());
        }
        out.add(message);

        // trace:观察协议帧(可用 -Drpc.trace=false 关闭)
        RpcLogger.trace("[Decoder] 收到帧: type=" + messageType + ", requestId=" + requestId
                + ", body=" + bodyLength + "B"
                + ", 线程=" + Thread.currentThread().getName());
    }
}
