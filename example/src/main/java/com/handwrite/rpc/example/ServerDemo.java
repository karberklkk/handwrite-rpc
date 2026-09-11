package com.handwrite.rpc.example;

import com.handwrite.rpc.core.registry.ServiceProvider;
import com.handwrite.rpc.core.transport.NettyServer;

/**
 * M2 Provider 进程(单独运行):
 * 注册 HelloService 实现,启动 Netty 服务器,等待远端的调用。
 *
 * 运行(两个终端各跑一个):
 *   Provider: java ... com.handwrite.rpc.example.ServerDemo [端口,默认28888]
 *   Consumer: java ... com.handwrite.rpc.example.ClientDemo [端口,默认28888]
 */
public class ServerDemo {

    public static final int PORT = 28888;

    public static void main(String[] args) throws InterruptedException {
        // 支持命令行指定端口(便于同时启动多个 Provider,为 M4 负载均衡做准备)
        int port = args.length > 0 ? Integer.parseInt(args[0]) : PORT;

        // Provider 侧:把服务实现放进本地持有表(ServiceProvider)
        ServiceProvider serviceProvider = new ServiceProvider();
        serviceProvider.register(HelloService.class.getName(), new HelloServiceImpl());
        System.out.println("[Server] 已注册服务: " + serviceProvider.size() + ", 使用端口: " + port);

        // 启动 Netty 服务器并阻塞(长驻进程)
        NettyServer server = new NettyServer(port, serviceProvider);
        server.start();
    }
}
