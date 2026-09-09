package com.handwrite.rpc.core.registry;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * M1 服务注册表(伪 RPC 版):
 * 保存 "服务接口全限定名 → 实现对象"。
 * Provider 启动时注册,调用时按名取实现。
 *
 * 说明:这里保存的是"本地实现对象";M3 引入 ZooKeeper 后
 * 会新增"注册中心"概念(存地址、跨机器共享),本类仍然负责 Provider 端持有实现对象。
 */
public class ServiceRegistry {

    private final Map<String, Object> services = new ConcurrentHashMap<>();

    /**
     * 注册服务:serviceName 用接口全限定名,如 HelloService.class.getName()
     */
    public void register(String serviceName, Object service) {
        services.put(serviceName, service);
    }

    /**
     * 按服务名取实现对象,取不到返回 null
     */
    public Object getService(String serviceName) {
        return services.get(serviceName);
    }

    /**
     * 当前注册了几个服务(调试用)
     */
    public int size() {
        return services.size();
    }
}
