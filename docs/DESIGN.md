# handwrite-rpc 架构与设计草案

> 本文是**设计草案**：随着你动手写代码会不断修正。核心接口签名供你参考，最终以你写出来的为准。

## 1. 目标

做一个"麻雀虽小五脏俱全"的 RPC 框架，覆盖一条真实 RPC 调用链路涉及的所有关键点：

```
Consumer(本地调用) → 动态代理 → 请求编码(序列化+协议头)
    → 网络传输(Netty) → 服务端解码 → 服务定位 → 反射执行 Provider 实现
    → 响应编码 → 网络回传 → Consumer 解码 → 返回给调用方
```

## 2. 模块划分与依赖方向（单向，禁止反向）

```
example ──► rpc-core ──► rpc-common ──► rpc-api
```

| 模块 | 职责 | 放什么 |
|---|---|---|
| rpc-api | 对外契约 | `RpcRequest` / `RpcResponse`、`@RpcService` / `@RpcReference` |
| rpc-common | 通用 + 扩展点 | 协议常量、序列化器/注册中心/负载均衡 **接口（SPI 定义）**、工具类 |
| rpc-core | 具体实现 | Netty Server/Client、动态代理、各 SPI 的默认实现、容错/超时 |
| example | 演示 | 服务接口 + Provider + Consumer 的 main |

> 为什么接口放 common 而实现放 core？答：调用方(example/consumer)只依赖抽象，不依赖实现 —— 面试常问的"面向接口编程 + SPI 的意义"就在这里落地。

## 3. 关键扩展点接口草案（M1/M3/M4/M5 逐项实现）

```java
// rpc-common: 服务注册中心抽象（M3）
public interface ServiceRegistry {
    void register(String serviceName, String address);   // provider 启动时调用
    void unregister(String serviceName, String address); // 停机时调用
}

// rpc-common: 服务发现抽象（M3）
public interface ServiceDiscovery {
    List<String> discover(String serviceName);           // 返回可用地址列表(host:port)
}
```

```java
// rpc-common: 负载均衡抽象（M4）
public interface LoadBalancer {
    String select(List<String> addresses, RpcRequest request); // request 供一致性哈希取 key
}
```

```java
// rpc-common: 序列化抽象（M5）
public interface Serializer {
    byte[] serialize(Object obj);
    <T> T deserialize(byte[] bytes, Class<T> clazz);
    byte getType(); // 写入协议头，保证两端一致
}
```

```java
// rpc-api: 消息对象（M1）
public class RpcRequest { /* serviceName, methodName, parameterTypes, parameters */ }
public class RpcResponse { /* 成功则返回 data；失败则携带异常信息 */ }
```

> 各接口的具体方法签名由你在实现时按需调整 —— 草案不是圣经。

## 4. 协议草案（M2 由你定稿，先记录约束）

- 必须能区分**消息边界**（解决粘包/半包）→ 用长度字段，配合 Netty `LengthFieldBasedFrameDecoder`
- 必须带 **requestId** → 客户端靠它把异步响应匹配回对应调用
- 必须带**消息类型**（request / response / heartbeat）与**序列化类型** → 同一端口多种用途、编解码可演进
- 建议开头放**魔数**（如 `0xCAFEBABE` 类似物）→ 快速识别非法连接

## 5. 已决策事项（ADR 简表）

| # | 决策 | 理由 | 备注 |
|---|---|---|---|
| 1 | 编译目标 `--release 17`（本机 JDK 26） | 17 是 LTS、主流公司版本；JDK26 可向下编译 | 若想用 21 的语法再议 |
| 2 | Netty 4.1.136.Final（4.1 线最新） | 教程与线上资料最多 | 如 JDK26 兼容问题 → 升 4.2.17.Final |
| 3 | Maven 3.9.16 | 当前稳定版 | 已装入 `..\.tools` |
| 4 | groupId `com.handwrite` | 占位，可改 | 推到 GitHub 前可全局替换成你的命名空间 |
| 5 | 先本地注册中心、后 ZK | 降低起步依赖，先把链路跑通 | ZK 在 M3 引入 |

## 6. 开放问题（随进度消解）

- [ ] 协议头里 requestId 用 8B long 是否够用/是否用 UUID？
- [ ] Consumer 端 Channel 池化还是单连接复用？如何保证并发写安全？
- [ ] 序列化 null / 泛型擦除问题怎么处理？
- [ ] 未来要不要做 `rpc-spring-boot-starter` 模块（用注解扫描替代手动注册）？

## 7. 演进路线（简历里的"亮点规划"）

1. **现在 → M6**：把 8 周里程碑做完，主链路稳定
2. **寒假后可选增强**（按简历需要取舍）：
   - `rpc-spring-boot-starter`：注解自动注册/注入（贴近真实用法，展示 Spring 功底）
   - 引入 Nacos 注册中心（对齐国内主流中间件）
   - 压测对比 Netty vs BIO，出一张数据表
   - 一致性哈希带虚拟节点的实现讲解 + 测试
