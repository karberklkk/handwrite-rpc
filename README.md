# handwrite-rpc

> 从零手写的一个迷你 RPC 框架 —— 学习 / 求职项目。
> 目标：不是"又一个照着教程抄的 demo"，而是**每个模块都能被面试官深挖、每一行核心代码都讲得清**的项目。

## 一句话简介

基于 Java 实现的一个轻量级 RPC 框架：通过动态代理让本地代码像调用本地方法一样调用远程服务，
内部包含 Netty 通信、自定义协议、服务注册发现、负载均衡、容错重试与可插拔扩展等模块。

## 技术栈（随里程碑逐步引入）

- Java 17（本机 JDK 26 编译，`--release 17`）
- Netty 4.1（NIO 通信 + 自定义协议编解码）
- JDK 动态代理 + 反射
- 序列化：JDK 原生 → 后续扩展 Kryo / Hessian（SPI 可插拔）
- 注册中心：本地内存版 → ZooKeeper（curator）【规划中】
- Maven 多模块

## 模块结构

```
handwrite-rpc
├── rpc-api       # 对外契约：RpcRequest / RpcResponse、@RpcService / @RpcReference
├── rpc-common    # 协议常量、工具类、扩展点接口（序列化/注册中心/负载均衡 SPI）
├── rpc-core      # 核心实现：Netty 服务端客户端、动态代理、注册发现、负载均衡、容错
├── example       # 示例：服务接口 + Provider / Consumer
└── docs          # 路线图、设计文档、笔记
```

## 里程碑进度

- [ ] M0 环境与工程骨架（多模块构建通过、GitHub 建仓）
- [ ] M1 动态代理 + 本地"伪 RPC"打通调用链路
- [ ] M2 Netty 通信 + 自定义协议（解决粘包/半包、同步转异步）
- [ ] M3 服务注册与发现（本地注册中心 → ZooKeeper）
- [ ] M4 负载均衡 + 容错重试 + 超时控制
- [ ] M5 SPI 可插拔扩展 + 心跳保活 + 优雅停机
- [ ] M6 压测、文档、简历收尾（README 补全 + 面试 QA 30 问）

> 详细周计划见 [`docs/ROADMAP.md`](docs/ROADMAP.md)，架构设计见 [`docs/DESIGN.md`](docs/DESIGN.md)，环境搭建见 [`docs/SETUP.md`](docs/SETUP.md)，学习笔记见 [`docs/NOTES.md`](docs/NOTES.md)。

## 快速开始

（M2 打通网络调用后补充：如何启动 provider / consumer、运行示例）

## 学习资料

- 参考项目：[Snailclimb/guide-rpc-framework](https://github.com/Snailclimb/guide-rpc-framework) —— 仅用于理解思路，核心代码为自己手写实现
