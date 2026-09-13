package com.handwrite.rpc.example;

/**
 * 带端口标记的 HelloService 实现(M4 演示专用)。
 *
 * 作用:返回值里带上自己的端口,于是从调用结果就能看出
 *       「这次请求落到了哪台 Provider」—— 负载均衡的分布才看得见。
 *
 * ⚠️ 为什么必须是【顶层 public 类】,而不是写在 Demo 里的内部类?
 *   因为服务端是用【反射】执行的:
 *       method.invoke(service, args)
 *   反射调用要求:方法 public 【并且】它所在的类对调用方可见。
 *   如果写成 `static class TaggedHelloService`(默认包级可见),
 *   而 RpcRequestHandler 在另一个包(com.handwrite.rpc.core.transport),
 *   就会报:
 *       IllegalAccessException: ... cannot access a member of class ... with modifiers "public"
 *   方法虽然写了 public,但类本身不可见,照样调不到。
 *
 *   —— 这是 RPC 框架里很典型的一个坑:Provider 端暴露的实现类必须是 public。
 */
public class TaggedHelloService implements HelloService {

    private final int port;

    public TaggedHelloService(int port) {
        this.port = port;
    }

    @Override
    public String sayHello(String name) {
        return "Hello, " + name + "! (来自端口 " + port + ")";
    }
}
