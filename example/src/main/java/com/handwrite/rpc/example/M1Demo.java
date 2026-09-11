package com.handwrite.rpc.example;

import com.handwrite.rpc.core.proxy.RpcClientProxy;
import com.handwrite.rpc.core.registry.ServiceProvider;

/**
 * M1 验收演示(单进程伪 RPC):
 * 同一个进程里分别扮演 Provider(注册服务)和 Consumer(代理调用),
 * 验证"像调本地方法一样调远程服务"的完整链路。
 *
 * 运行: mvn -pl example -am package 后
 *       java -cp "example/target/classes;rpc-core/target/classes;rpc-common/target/classes;rpc-api/target/classes" com.handwrite.rpc.example.M1Demo
 */
public class M1Demo {

    public static void main(String[] args) {
        // ===== Provider 侧:把服务实现放进本地持有表 =====
        ServiceProvider serviceProvider = new ServiceProvider();
        serviceProvider.register(HelloService.class.getName(), new HelloServiceImpl());
        System.out.println("[Provider] 已注册服务数: " + serviceProvider.size());

        // ===== Consumer 侧:通过代理像本地方法一样调用 =====
        RpcClientProxy proxyFactory = new RpcClientProxy(serviceProvider);
        HelloService helloService = proxyFactory.getProxy(HelloService.class);

        String result = helloService.sayHello("handwrite-rpc");
        System.out.println("[Consumer] 调用结果: " + result);
    }
}
