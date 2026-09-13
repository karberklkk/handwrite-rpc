package com.handwrite.rpc.example;

import com.handwrite.rpc.common.registry.ServiceDiscovery;
import com.handwrite.rpc.common.registry.ServiceRegistry;
import com.handwrite.rpc.core.discovery.ServiceResolver;
import com.handwrite.rpc.core.loadbalance.RoundRobinLoadBalancer;
import com.handwrite.rpc.core.proxy.RpcClientProxy;
import com.handwrite.rpc.core.registry.LocalServiceRegistry;

/**
 * M3 Consumer 进程(单独运行,先启动 ServerDemo):
 * 通过【服务发现 + 负载均衡】拿到地址,再像调本地方法一样调用。
 *
 * ⚠️ 跨进程的现实限制:
 *   LocalServiceRegistry 是进程内内存版,本进程读不到 ServerDemo 注册的信息。
 *   所以这里必须【手动把地址填进注册中心】,相当于"假装注册中心里已经有了"。
 *
 *   这一步验证的重点是:Consumer 侧走的是「发现」这条路,而不是「硬编码」。
 *   真正的跨进程自动发现,要等 M3 第 4 步的 ZooKeeper 实现。
 */
public class ClientDemo {

    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : ServerDemo.PORT;
        String serviceName = HelloService.class.getName();

        // 注册中心(本进程内),手动填入 Provider 的地址
        LocalServiceRegistry registry = new LocalServiceRegistry();
        ServiceRegistry registrar = registry;
        ServiceDiscovery discovery = registry;
        registrar.register(serviceName, "127.0.0.1:" + port);

        // Consumer 侧:发现 → 负载均衡 → 代理
        ServiceResolver resolver = new ServiceResolver(discovery, new RoundRobinLoadBalancer());
        RpcClientProxy proxyFactory = new RpcClientProxy(resolver);
        HelloService helloService = proxyFactory.getProxy(HelloService.class);

        for (int i = 1; i <= 3; i++) {
            String result = helloService.sayHello("netty-" + i);
            System.out.println("[Client] 第 " + i + " 次调用结果: " + result);
        }

        proxyFactory.close();
    }
}
