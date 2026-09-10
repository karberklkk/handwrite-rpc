package com.handwrite.rpc.common;

/**
 * RPC 通信协议常量(M2-2)。
 * 报文结构:魔数(2B) | 版本(1B) | 消息类型(1B) | 序列化类型(1B)
 *          | requestId(8B) | body长度(4B) | body(变长)
 * 头部固定 17 字节。
 */
public final class RpcConstants {

    private RpcConstants() {
        // 常量类不允许实例化
    }

    /** 魔数:用于快速识别"是不是本协议的包" */
    public static final short MAGIC = (short) 0xCAFE;

    /** 协议版本 */
    public static final byte VERSION = 1;

    /** 消息类型 */
    public static final byte TYPE_REQUEST = 1;
    public static final byte TYPE_RESPONSE = 2;
    public static final byte TYPE_HEARTBEAT = 3;   // M5 心跳

    /** 序列化类型 */
    public static final byte SERIALIZER_JDK = 1;
    public static final byte SERIALIZER_KRYO = 2;  // M5

    /** 头部长度(2+1+1+1+8+4) */
    public static final int HEADER_LENGTH = 17;

    /** 长度字段起始偏移(魔数2+版本1+类型1+序列化1+requestId8) */
    public static final int LENGTH_FIELD_OFFSET = 13;

    /** 长度字段占几个字节 */
    public static final int LENGTH_FIELD_LENGTH = 4;

    /** 单帧最大长度(防止超大包打爆内存),8MB */
    public static final int MAX_FRAME_LENGTH = 8 * 1024 * 1024;
}
