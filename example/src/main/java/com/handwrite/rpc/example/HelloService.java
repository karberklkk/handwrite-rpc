package com.handwrite.rpc.example;

/**
 * 演示用服务接口:将被 Provider 实现、被 Consumer 远程调用
 */
public interface HelloService {

    /**
     * 打个招呼
     */
    String sayHello(String name);
}