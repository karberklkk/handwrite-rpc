package com.handwrite.rpc.core.registry;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 服务提供者本地持有表(原 ServiceRegistry,M3 重命名)。
 *
 * 职责:在 **Provider 进程内部** 保存 "服务接口全限定名 → 实现对象",
 * 收到 RpcRequest 时按服务名取出实现,再反射执行。
 *
 * 与注册中心(common.registry.ServiceRegistry)的区别 —— 这是两个不同层次的东西:
 *   - ServiceProvider(本类):**本地**持有实现对象,只在本进程内可见,用于"执行"
 *   - ServiceRegistry(注册中心):保存 **地址**(host:port),跨进程/跨机器共享,用于"找到"
 *
 * 一次完整调用里两者配合:Provider 用 ServiceProvider 执行方法;
 * Consumer 用注册中心查到地址后发起网络调用。
 */
public class ServiceProvider {

    /** 服务名(接口全限定名) → 实现对象 */
    private final Map<String, Object> services = new ConcurrentHashMap<>();

    /**
     * 注册服务实现:serviceName 用接口全限定名,如 HelloService.class.getName()
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
     * 当前持有几个服务实现(调试用)
     */
    public int size() {
        return services.size();
    }
}
