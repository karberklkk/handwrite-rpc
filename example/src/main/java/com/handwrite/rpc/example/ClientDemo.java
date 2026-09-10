package com.handwrite.rpc.example;

import com.handwrite.rpc.core.proxy.RpcClientProxy;
import com.handwrite.rpc.core.transport.NettyClient;

/**
 * M2 Consumer 进程(单独运行,先启动 ServerDemo):
 * 通过代理像调本地方法一样调用"另一台进程"里的 HelloService。
 */
public class ClientDemo {

    public static void main(String[] args) throws Exception {
        // 支持命令行指定端口,默认 28888
        int port = args.length > 0 ? Integer.parseInt(args[0]) : ServerDemo.PORT;

        // Consumer 侧:网络模式代理(连接 ServerDemo 的端口)
        NettyClient nettyClient = new NettyClient("127.0.0.1", port);
        RpcClientProxy proxyFactory = new RpcClientProxy(nettyClient);
        HelloService helloService = proxyFactory.getProxy(HelloService.class);

        // 像本地方法一样调用(实际跨进程执行!)
        for (int i = 1; i <= 3; i++) {
            String result = helloService.sayHello("netty-" + i);
            System.out.println("[Client] 第 " + i + " 次调用结果: " + result);
        }

        nettyClient.close();
    }
}
