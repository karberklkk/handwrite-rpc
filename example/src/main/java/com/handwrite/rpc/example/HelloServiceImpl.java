package com.handwrite.rpc.example;

import com.handwrite.rpc.api.RpcService;

/**
 * HelloService 的实现——Provider 端真正的服务逻辑
 */
@RpcService(HelloService.class)
public class HelloServiceImpl implements HelloService {

    @Override
    public String sayHello(String name) {
        return "Hello, " + name + "!";
    }
}