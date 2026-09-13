# handwrite-rpc 学习路线图（8 周冲刺版）

> 配套 README：`../README.md`　设计文档：`DESIGN.md`
> 今天：2026-09-09。目标：**12 月底前**达到"能讲透 + 能演示 + 有压测数据"，用于寒假/日常实习投递；
> 之后继续打磨作为秋招(2027)主打项目。

## 铁律（先读三遍）

1. **核心代码由你自己写**。AI 负责：搭骨架、Review 你的代码、答疑、做你写不动的基建（如环境）。面试官问到的任何一行，都必须是你自己敲过、想过的。
2. 参考 [guide-rpc-framework](https://github.com/Snailclimb/guide-rpc-framework) 的 README 和它的实现步骤文章来**理解思路**，但**禁止复制粘贴它的源码**。正确姿势：看明白 → 合上文档 → 自己写 → 对比差异 → 思考为什么它那样写。
3. **每个里程碑结束必须 git commit**（规范 message，如 `feat(core): 完成 Netty 客户端请求-响应映射`）。提交历史就是你学习过程最诚实的证据。
4. 每周固定 10–15h 分配：写代码 40% / 读文档+读参考源码 30% / 写笔记文档 20% / 复盘面试题 10%。

## 里程碑总览

| 里程碑 | 主题 | 建议周期 | 关键产出 |
|---|---|---|---|
| M0 | 环境与骨架 | 第 1 周 | 多模块构建通过 + GitHub 仓库有首提交 |
| M1 | 动态代理"伪 RPC" | 第 2 周 | 本地调用链路打通，理解 RPC 本质 |
| M2 | Netty + 自定义协议 | 第 3–4 周 | 两进程跨网络调用，解决粘包/半包、异步转同步 |
| M3 | 服务注册发现 | 第 5 周 | 本地注册中心 → ZooKeeper |
| M4 | 负载均衡/容错/超时 | 第 6 周 | 多 provider 可容错、可超时 |
| M5 | SPI 扩展 + 心跳 + 优雅停机 | 第 7 周 | 序列化可插拔、连接保活 |
| M6 | 压测、文档、简历收尾 | 第 8 周 | README + 压测数据 + 面试 QA |

---

## M0 环境与工程骨架（本周）

- [x] 确认 JDK（本机 26）与 Maven（已装 3.9.16 到 `..\.tools\`，记得把 `..\.tools\apache-maven-3.9.16\bin` 加入系统 PATH，以后直接用 `mvn`）
- [x] 创建多模块骨架：`rpc-api` / `rpc-common` / `rpc-core` / `example`
- [ ] 运行 `mvn clean package` 构建通过
- [ ] 配置 git 身份并做首次提交：
  ```powershell
  cd "C:\Users\L8619\Desktop\work place\handwrite-rpc"
  git config --local user.name  "你的GitHub用户名"
  git config --local user.email "你的GitHub邮箱"
  git add . && git commit -m "chore: init maven multi-module skeleton"
  ```
- [ ] 在 GitHub 新建同名仓库 `handwrite-rpc`（不要勾选自动生成 README），按提示关联并推送：
  ```powershell
  git branch -M main
  git remote add origin git@github.com:你的用户名/handwrite-rpc.git
  git push -u origin main
  ```
  > SSH 方式需要先确认你的 `id_ed25519` 已添加到 GitHub（Settings → SSH and GPG keys），指纹 `SHA256:PlONC95hfiGG/PWlw+VoGtT3HaslHhNmLznNYGTqlQo`。
- [ ] 通读 guide-rpc-framework 的 README，在 `docs/NOTES.md` 里用你自己的话写一段：**一次 RPC 调用要经过哪几步？**

**验收**：Maven 构建通过；GitHub 有第一个 commit；能一句话讲清"RPC 是什么"。
**面试热身**：聊聊你的 Maven 多模块为什么这样分（高内聚/低耦合、依赖方向单向）。

---

## M1 动态代理版"伪 RPC"（第 2 周）

**任务**
1. `rpc-api` 定义：
   - 注解 `@RpcService`（标注在实现类上，value=服务名/接口）、`@RpcReference`（标注在消费方字段上）
   - `RpcRequest`（serviceName、methodName、参数类型数组、参数数组）、`RpcResponse`（返回数据或异常）
2. `example` 定义服务接口 `HelloService` + 实现 `HelloServiceImpl`
3. `rpc-core`：用 JDK 动态代理给"消费方"生成代理对象；调用发生时，代理把 `RpcRequest` 交给一个本地"注册表"，直接反射调用注册表里的 Provider 实现并返回 —— **先不联网**，目的是把调用链路的骨架打通

**学习要点**：动态代理（Proxy/InvocationHandler）、反射（Method.invoke）、**RPC 的本质 = 把"本地方法调用"编码成"一次可传输的请求"再还原执行**。
**验收**：一个 main 跑通 `helloService.sayHello("world")`；打断点能看到 InvocationHandler 拦截；参数/返回值正确。
**简历句**：设计基于 JDK 动态代理的透明 RPC 调用层，调用方无感知完成请求构造与分发。

---

## M2 Netty 通信 + 自定义协议（第 3–4 周，本周最重）

**3a（半天）先用纯 Socket 打通跨进程** —— ✅ 已并入 M2-1 用 Netty 直接实现(见 `example/ServerDemo`、`ClientDemo`)

**3b 引入 Netty** —— ✅ 已完成(`transport/NettyServer`、`NettyClient`、`RpcRequestHandler`)

**3c 自定义协议** —— ✅ **设计稿已完成:[`PROTOCOL.md`](PROTOCOL.md)**(17 字节定长头:魔数/版本/类型/序列化/requestId/长度;含常量定义、编解码流程、验收标准)
参考 Dubbo / guide-rpc 的思路自行设计，例如：
```
魔数(4B) | 版本(1B) | 消息类型(1B: request/response/heartbeat) | 序列化类型(1B)
| requestId(8B) | body长度(4B) | body(变长)
```

**3d 编解码器** —— ✅ 已完成(`transport/RpcEncoder`、`RpcDecoder` + `LengthFieldBasedFrameDecoder`)
- 入站用 `LengthFieldBasedFrameDecoder` 解决 TCP 粘包/半包
- 自己写 Encoder / Decoder（注意 `ByteBuf` 引用计数释放、异常传播、handler 顺序）
- 不同消息类型（请求/响应/心跳）如何分发

**3e 同步调用的异步化（最核心的难点）** —— ✅ 已完成(requestId + `Map<Long, CompletableFuture<RpcResponse>>`,连接复用)
问题：RPC 是异步收包的，但调用方要同步拿结果。
方案：每个请求带唯一 `requestId`；客户端持 `Map<requestId, CompletableFuture/RpcFuture>`；收到响应后按 id 找到对应的 future 并 complete；调用方在 future 上等待。

**验收** ✅:provider 与 consumer 各自独立进程互调成功;顺序 1000 次 0 错配(1.08ms/次);**并发 10 线程 × 500 次 = 5000 次调用 0 错配、0 异常,吞吐约 2100 次/秒**;非法魔数被拒且服务端存活。
**已完成产物**:`ServerDemo` / `ClientDemo` / `ProtocolVerifyDemo` / `ConcurrentVerifyDemo`;`RpcLogger` 支持 `-Drpc.trace=false` 关闭 trace 日志。
**学习要点**：Netty 线程模型（EventLoop、pipeline、handler 执行线程）、ByteBuf、粘包半包、Future/Promise、Channel 复用与并发安全。
**面试弹药**：粘包半包为什么出现、怎么解决？Netty 为什么比 BIO 快（多路复用/零拷贝/线程模型）？为什么自己造协议而不是直接传对象？

---

## M3 服务注册与发现（第 5 周）

> ✅ **进度（2026-09-13）**：第 1–3 步已完成并推送（提交 `39f3381`）。
> 第 4 步 ZooKeeper **待做**（需先准备 curator 依赖 + 本机 ZK 服务）。
> 验收：`M3LocalDemo` **5/5 PASS** —— discover 能拿到地址、地址内容正确、
> 三次真网络调用成功、查不存在的服务抛 `RpcException`（不是 NPE）、注销后 discover 为空。
> 📄 设计说明与面试问答见 [`M3-M4-实现报告.md`](M3-M4-实现报告.md)

**任务**
1. 在 `rpc-common` 抽象接口：`ServiceRegistry`（register/unregister）、`ServiceDiscovery`（discover，返回可用地址列表）
2. 本地实现：`ConcurrentHashMap<String, Set<URL>>` —— 先跑通单机
3. Provider 启动时把自己的 host:port 注册上去；Consumer 调用前先 discover + 本地缓存（并处理缓存失效）
4. （加分，强烈建议）用 ZooKeeper 实现：curator-framework，临时节点 `/handwrite-rpc/{serviceName}/{host:port}`，会话断开自动删除；Consumer 用 watcher 感知上下线

> 📌 **版本搭配（2026-09 已核实）**：`curator-framework 5.9.0` + `zookeeper 3.9.5`（zookeeper 作为测试依赖时注意 curator 对 zk 版本有要求，别乱升）。本地起单机 ZK 服务器：到 Apache 官网下载 zookeeper-3.9.5 二进制 zip，解压后运行 `bin\zkServer.cmd`（Windows）。

**验收**：先本地注册中心跑通；再起单机 ZK 跑通（Windows 下 ZK 的启动脚本在 `bin/zkServer.cmd`）。
**学习要点**：服务注册发现的语义；ZK 临时/持久节点、watcher 机制；从 CAP 看 ZK 与 Nacos/Redis 的定位差异。
**简历句**：抽象注册中心接口并落地 ZooKeeper 实现，支持服务上下线感知。

---

## M4 负载均衡 + 容错 + 超时（第 6 周）

> ✅ **进度（2026-09-13）**：已完成并推送（提交 `39f3381`）。
> 验收：`M4LoadBalanceDemo` **5/5 PASS** ——
> 三种策略分布 Random 9/10/11、RoundRobin 10/10/10、ConsistentHash 18/8/4；
> 一致性哈希同一 key 连续 10 次落同一台；轮询呈固定循环；
> **Failfast 失败 5/20，Failover 失败 0/20**。
> 📄 设计说明与面试问答见 [`M3-M4-实现报告.md`](M3-M4-实现报告.md)

**任务**
1. `LoadBalancer` 接口：Random / RoundRobin / **一致性哈希**（带虚拟节点），注册中心返回多个地址时选择其一
2. 容错策略：`Failover`（默认：换下一个节点重试）/ `Failfast`；封装统一 `RpcException`
3. 超时控制：客户端 future 带超时等待，超时抛异常并**从 requestId Map 移除，防止泄漏**

**验收**：起 2 个 provider，日志可见请求被轮流分发；手动 kill 掉一个 provider，调用仍然成功；provider 里 sleep 3s 能稳定触发超时。
**学习要点**：一致性哈希原理与虚拟节点作用（数据倾斜）；重试在非幂等场景的副作用（结合真实场景思考）；超时与资源回收。
**面试弹药**：为什么一致性哈希要虚拟节点？什么请求不能盲目重试？Map 里堆积的 future 会怎样（内存泄漏）？

---

## M5 SPI 可插拔扩展 + 心跳 + 优雅停机（第 7 周）

**任务**
1. 仿照 SPI 思路：定义 `Serializer` 接口，通过 `META-INF/services`（JDK SPI）或自研扩展机制加载；实现 **JDK 原生序列化**与 **Kryo/Hessian** 两版，配置可切换，消息头携带序列化类型

> 📌 依赖版本（2026-09 已核实）：`com.esotericsoftware:kryo:5.6.2`；JDK 原生序列化无需第三方依赖。
2. 心跳保活：`IdleStateHandler`，客户端空闲发送心跳，服务端超时剔除；连接长期空闲不断
3. 优雅停机：JVM shutdown hook —— 关闭 acceptor、释放线程池、ZK 注销节点

**验收**：改一行配置即从 JDK 序列化切换 Kryo；空闲 5 分钟连接不断；Ctrl+C 进程无异常日志退出。
**学习要点**：SPI 原理与 Dubbo SPI 的差异（为什么 Dubbo 要自己写）；几种序列化方案的性能/跨语言/安全性对比（JDK 序列化还有什么安全漏洞）。
**简历句**：基于 SPI 机制实现序列化/负载均衡/注册中心可插拔，核心思想对标 Dubbo 扩展机制。

---

## M6 压测、文档、简历收尾（第 8 周）

**任务**
1. 压测：JMH 或简单循环压测脚本，记录 **QPS / RT（avg、p99）**，写明环境（本机 CPU/内存、数据包大小、是否同一进程 loopback）

> 📌 版本（2026-09 已核实）：`org.openjdk.jmh:jmh-core:1.37`（配 `jmh-generator-annprocess`，同一版本号）。
2. 补全 README：架构图（mermaid 或手绘）、模块说明、快速开始、**压测结果表**、目录结构、待办/已知限制
3. `docs/QA.md`：自己整理 **30 个本项目相关面试问答**（网络、协议、并发、序列化、设计取舍各一组）
4. （可选）写一篇复盘博客，README 里放链接

**验收**：README 完整漂亮；你能做到 —— 3 分钟讲清全貌，被面试官连续深挖 20 分钟不露怯。

---

## 每周节奏与记录

每周结束时在 `docs/LOG.md` 追加：
```
## 第 N 周（日期）
- 本周完成：
- 踩坑记录：
- 卡住的问题：
- 下周计划：
```
卡住超过 1 小时的问题：先自己搜 → 记笔记 → 再问 AI（描述：现象/你试过什么/你的猜测）。

## 常见坑预警

- Netty handler 里忘释放 ByteBuf / 用了阻塞操作
- Channel 并发写导致消息错乱（要用同一个 EventLoop 写或加锁/队列）
- future 的 Map 只增不减（内存泄漏，M4 超时章节处理）
- 用 JDK 自带序列化序列化接口/实现类时的类加载问题
- ZK 版本与 curator 版本不匹配（记得到 M3 再查对应版本）
- 提交里混入 `target/`、`.idea/`（已加 .gitignore）
