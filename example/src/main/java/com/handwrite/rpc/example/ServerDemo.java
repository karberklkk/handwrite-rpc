package com.handwrite.rpc.example;

import com.handwrite.rpc.common.registry.ServiceRegistry;
import com.handwrite.rpc.core.registry.LocalServiceRegistry;
import com.handwrite.rpc.core.registry.ServiceProvider;
import com.handwrite.rpc.core.transport.NettyServer;

/**
 * M3 Provider 进程(单独运行):
 *   1. 把服务实现放进【本地持有表】(ServiceProvider)—— 用来"执行"
 *   2. 把自己的 host:port 报进【注册中心】(ServiceRegistry)—— 让别人"找到"
 *   3. 注册 JVM shutdown hook,进程退出前【注销地址 + 关闭服务器】
 *
 * 运行(两个终端各跑一个):
 *   Provider: java ... example.ServerDemo [端口,默认 28888]
 *   Consumer: java ... example.ClientDemo [端口,默认 28888]
 *
 * ⚠️ 已知限制:LocalServiceRegistry 是【进程内内存版】,
 *    所以 ClientDemo(另一个 JVM)其实看不到这里注册的地址。
 *    真正跨进程的注册发现要等 ZooKeeper 实现(M3 第 4 步)。
 *    同进程内的完整验证请看 M3LocalDemo。
 */
public class ServerDemo {

    public static final int PORT = 28888;

    public static void main(String[] args) throws InterruptedException {
        // 支持命令行指定端口(便于同时启动多个 Provider,给 M4 负载均衡用)
        int port = args.length > 0 ? Integer.parseInt(args[0]) : PORT;
        String serviceName = HelloService.class.getName();

        // ① 本地持有表:服务名 → 实现对象
        ServiceProvider serviceProvider = new ServiceProvider();
        serviceProvider.register(serviceName, new HelloServiceImpl());
        System.out.println("[Server] 已注册服务实现: " + serviceProvider.size() + " 个");

        // ② 注册中心:把地址报上去
        ServiceRegistry registry = new LocalServiceRegistry();
        NettyServer server = new NettyServer(port, serviceProvider);
        String address = server.localAddress();
        registry.register(serviceName, address);
        System.out.println("[Server] 已上报地址: " + serviceName + " → " + address);

        // ③ 优雅下线:Ctrl+C 时由 JVM 触发这个钩子
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println();
            System.out.println("[Server] 收到退出信号,开始优雅下线...");
            registry.unregister(serviceName, address);
            System.out.println("[Server] 已从注册中心注销地址: " + address);
            server.close();
            System.out.println("[Server] 服务器已关闭,退出完成");
        }, "shutdown-hook"));

        // ④ 启动并阻塞(长驻进程)
        server.start();
    }
}
