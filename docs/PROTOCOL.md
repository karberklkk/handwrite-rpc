# handwrite-rpc 通信协议设计(M2-2 施工图)

> 状态:M2-1 已用 Netty 自带 `ObjectEncoder/ObjectDecoder`(JDK 序列化 + 内置切帧)跑通双进程通信。
> 本文定义 **M2-2 要替换成的自研协议**,目的是:解决粘包半包、支持请求-响应匹配、为序列化 SPI 与心跳扩展留位。
> 实现顺序:先按本文写常量类 → Encoder → Decoder → 替换 pipeline。

## 1. 设计目标

| 目标 | 对应字段/机制 |
|---|---|
| 快速识别非法/错误连接 | 魔数(Magic Number) |
| 未来协议可演进(加字段不改老逻辑) | 版本号 |
| 一个连接既能发请求也能收响应/心跳 | 消息类型 |
| 支持多种序列化方案(JDK/Kryo…) | 序列化类型(M5 用) |
| 请求与响应一一对应(异步化的钥匙) | requestId |
| 解决 TCP 粘包/半包 | body 长度 + LengthFieldBasedFrameDecoder |

## 2. 报文结构(定长头 + 变长体)

```
+--------+---------+--------+------------+-----------+-----------+---------+
| magic  | version |  type  | serializer | requestId |  length   |  body   |
|  2B    |   1B    |   1B   |     1B     |    8B     |    4B     |  变长   |
+--------+---------+--------+------------+-----------+-----------+---------+
 偏移0     偏移2     偏移3     偏移4        偏移5       偏移13      偏移17
```

| 偏移 | 长度 | 字段 | 取值/说明 |
|---|---|---|---|
| 0 | 2 | magic | 固定 `0xCAFE`(short)。对不上 → 非法连接,直接关闭 |
| 2 | 1 | version | 当前 `0x01` |
| 3 | 1 | messageType | `1`=REQUEST, `2`=RESPONSE, `3`=HEARTBEAT(M5) |
| 4 | 1 | serializerType | `1`=JDK(M2-2 默认), `2`=Kryo(M5) |
| 5 | 8 | requestId | 请求唯一标识(long);响应必须回填同一个值 |
| 13 | 4 | length | body 的字节数(int) |
| 17 | length | body | 序列化后的 `RpcRequest` / `RpcResponse` 字节 |

> 头部固定 **17 字节**;`length` 只描述 body 长度,不包含头。

## 3. 编解码流程

### 出站(Consumer 发请求 / Provider 回响应)
```
对象(RpcRequest/RpcResponse)
 → Serializer 序列化成 byte[]            ← M2-2 先用 JDK 序列化,M5 换 SPI 可插拔
 → 组装 17B 头(magic/version/type/serializerType/requestId/length)
 → ByteBuf 写出
```

### 入站(收到字节)
```
① LengthFieldBasedFrameDecoder 切帧:
     lengthFieldOffset   = 13   (长度字段从第 13 字节开始)
     lengthFieldLength   = 4    (长度字段占 4 字节)
     lengthAdjustment    = 0    (长度值就是 body 长度)
     initialBytesToStrip = 0    (先不剥离头,交给自定义 Decoder 解析)
② 自定义 RpcDecoder:
     校验 magic → 读 version/type/serializerType/requestId/length
     → 读 length 字节 body → 反序列化成对象
③ 交给业务 handler:
     RpcRequestHandler(Provider 端,反射执行)
     SimpleClientHandler(Consumer 端,把 RpcResponse 交给等待的调用方)
```

## 4. 常量定义(建议放 `rpc-common`)

```java
public final class RpcConstants {
    public static final short MAGIC = (short) 0xCAFE;
    public static final byte VERSION = 1;
    // 消息类型
    public static final byte TYPE_REQUEST = 1;
    public static final byte TYPE_RESPONSE = 2;
    public static final byte TYPE_HEARTBEAT = 3;   // M5
    // 序列化类型
    public static final byte SERIALIZER_JDK = 1;
    public static final byte SERIALIZER_KRYO = 2;  // M5
    // 头长度(便于 decoder 计算)
    public static final int HEADER_LENGTH = 17;
    public static final int LENGTH_FIELD_OFFSET = 13;
    public static final int LENGTH_FIELD_LENGTH = 4;
}
```

## 5. 一次请求的字节示意(示例)

调用 `helloService.sayHello("netty")`,requestId = 1:

```
CA FE | 01 | 01 | 01 | 00 00 00 00 00 00 00 01 | 00 00 01 2C | <body 300 字节>
魔数    版本 请求   JDK       requestId=1             length=300      JDK 序列化后的 RpcRequest
```

Provider 回响应:头里 type 改为 `02`(RESPONSE),**requestId 必须回填 1**,body 为序列化后的 `RpcResponse`。

## 6. M2-2 要新增/修改的文件(施工清单)

| 文件 | 动作 | 职责 |
|---|---|---|
| `rpc-common/.../RpcConstants.java` | 新增 | 协议常量(见第 4 节) |
| `rpc-core/transport/RpcEncoder.java` | 新增 | 对象 → 字节(加 17B 头) |
| `rpc-core/transport/RpcDecoder.java` | 新增 | 字节 → 对象(校验魔数、解析头、反序列化) |
| `rpc-core/transport/NettyServer.java` | 修改 | pipeline 换成 `LengthFieldBasedFrameDecoder` + `RpcDecoder` + `RpcEncoder` |
| `rpc-core/transport/NettyClient.java` | 修改 | 同上(入站用 RpcDecoder,出站用 RpcEncoder) |
| `rpc-core/serialize/JdkSerializer.java` | 新增(M2-2 可内联) | JDK 序列化实现(用 ByteArrayOutputStream/ObjectOutputStream) |

> M2-3 再引入:requestId 生成器(AtomicLong)、`Map<Long, CompletableFuture<RpcResponse>>`、`RpcResponse` 按 requestId 唤醒等待者。

## 7. 验收标准(M2-2)

1. 双进程互调依旧成功(与 M2-1 结果一致)
2. 连续快速发送 1000 次请求,响应全部正确匹配(不再依赖"共享队列顺序")
3. 故意发一段非法字节(魔数不对),服务端能识别并关闭连接而不是崩掉

## 8. 开放问题(动手前先想清楚)

- [ ] `requestId` 由谁生成?建议客户端用 `AtomicLong` 自增(简单、可读、便于日志排查)
- [ ] 响应体的异常信息怎么表达?延续 M2-1 做法:`RpcResponse.errorMessage` 携带字符串
- [ ] 要不要在头里加"压缩类型"?→ 先不加,留到有压测数据后再评估
- [ ] JDK 序列化的安全问题(反序列化漏洞)面试怎么答?→ 说明生产会用 Kryo/Protobuf,并做类白名单
