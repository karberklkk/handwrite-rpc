# M1 导览：动态代理版"伪 RPC"怎么想、怎么写

> 阅读对象：完成了 M0（能构建、有提交）的你。
> 本文**只讲思路**，不给成品代码——关键代码请自己敲（铁律）。
> 对照文件：`DESIGN.md`（接口草案）、`ROADMAP.md`（M1 验收标准）。

## 1. 先回答一个根本问题：RPC 到底在"骗"谁？

你写业务代码时是这样的：

```java
HelloService service = ???;      // 我想要一个能调用的对象
String r = service.sayHello("world");   // 然后像本地方法一样调它
```

在 RPC 里，`service` **不是真正的实现对象**，而是一个"长得一模一样、但行为是把调用打包发出去的替身"。
**动态代理就是造这个替身的技术。** 面试问"RPC 为什么用动态代理"，答案就是：让调用方无感知 —— 调用方以为在调本地方法，实际请求被代理截获、编码、发送、等待响应、再返回。

## 2. M1 的裁剪：先不联网

M1 故意砍掉网络，把链路切成最朴素的一版：

```
Consumer 代码 → 代理对象(InvocationHandler)
   → 构造 RpcRequest(服务名/方法名/参数…)
   → 交给"本地服务注册表"找到 Provider 实现对象
   → 反射执行真实方法 → 得到结果 → 包成 RpcResponse 返回给调用方
```

跑通这一版，你就把 RPC 的"**骨架逻辑**"掌握了；M2 只是把"交给本地注册表"换成"通过网络发出去"。

## 3. 你要写的四个东西（都在 DESIGN.md 有草案签名）

### ① 两个注解
```java
// 标注在 Provider 实现类上：这个类对外提供哪些服务？
@RpcService(interfaceClass = HelloService.class)   // 或 value=接口名
public class HelloServiceImpl implements HelloService { ... }

// 标注在 Consumer 要注入的字段上：这个字段要用远程代理填充
@RpcReference
private HelloService helloService;
```
**自问（面试点）**：注解的 `@Retention` 该选 RUNTIME 还是 CLASS？为什么？
（提示：你的代码要在**运行时**通过反射读注解，所以……？）

### ② 两个消息对象
- `RpcRequest`：一次"请帮我调用这个方法"的完整描述。**至少**要有：
  `serviceName`（调哪个服务）、`methodName`（调哪个方法）、`parameterTypes`（参数类型数组，反射需要）、`parameters`（实参数组）
- `RpcResponse`：一次调用的结果。**成功**返回数据；**失败**要把异常传回去（不能吞掉！调用方要知道哪里错了）

**自问（面试点）**：为什么 `RpcRequest` 里要带 `parameterTypes` 而不是只带 `parameters`？
（提示：反射 `getMethod(name, 参数类型…)` 需要精确的类型信息；类型擦除会导致重载方法匹配出错。）

### ③ 一个"服务注册表"（M1 用最朴素的实现即可）
`Map<服务名/接口名, 实现对象>`。Provider 启动时把自己塞进去。
M3 会把它换成真正的注册中心（ZK），所以 M1 可以先不抽象接口、写死一个类，也可以提前抽象——**自己决定**，想清楚理由。

### ④ 动态代理工厂 + 调用处理
```java
// 大概思路（自己写）
public Object getProxy(Class<?> interfaceClass) {
    // Proxy.newProxyInstance(类加载器, 接口数组, handler)
}
// handler.invoke 里：
//   1) 把 method/参数 组装成 RpcRequest
//   2) 从注册表找到实现对象
//   3) 反射调用
//   4) 把结果或异常装进 RpcResponse 返回
```

## 4. 一个关键的心理建设

第一次写会纠结"我应该把 Proxy 放哪个包""Map 的 key 用接口名还是全限定名"。**不要追求一次完美**。
先把能跑的写出来 → 再想这五个问题：

1. 一个 Provider 实现多个接口怎么办？（Map 的 key 该怎么定？）
2. 调用不存在的服务/方法，你的代码会怎样？（要抛什么异常、怎么抛？）
3. `@RpcReference` 的字段谁来填充？M1 可以先不做"自动注入"，直接在 main 里手动 `getProxy(HelloService.class)` —— 这是**有意的裁剪**，面试时能说清"我为什么先这么做、Spring 版怎么扩展"就行。
4. Provider 端是单例还是每次 new？（想想 Spring bean 的 scope）
5. 参数/返回值里如果含有 null，你的代码会怎样？（为 M2 序列化埋个伏笔）

## 5. 写完怎么自测（验收标准）

```java
// example 模块里写一个 main：
//   1) 手工把 HelloServiceImpl 注册进注册表
//   2) HelloService proxy = 你的工厂.getProxy(HelloService.class)
//   3) proxy.sayHello("world") 打印返回
//   4) 故意调一个不存在的方法，确认异常信息清晰
```
跑通后再对照 guide-rpc-framework 看它的包结构和设计，把差异记进 `docs/NOTES.md`。

## 6. 写代码的顺序建议（防止卡死）

1. 先写 `RpcRequest` / `RpcResponse`（纯数据类，最简单，先建立信心）
2. 再写注解（两行声明 + 一行注释）
3. 写 `HelloService` + `HelloServiceImpl`（example 模块）
4. 写服务注册表（一个 HashMap 的事）
5. 最后写代理工厂 —— 卡住时回来读第 3 节 ④ 的提示

> 卡壳超过 1 小时：描述"你想做什么 → 卡在哪 → 试过什么"来找 AI 要提示。
