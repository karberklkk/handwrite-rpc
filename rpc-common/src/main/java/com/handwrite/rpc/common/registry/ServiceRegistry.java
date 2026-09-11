package com.handwrite.rpc.common.registry;

/**
 * 服务注册(M3 注册中心抽象)。
 *
 * 职责:Provider 启动时把自己的地址"报到"注册中心,下线时注销。
 * 与 ServiceDiscovery 配对:一个负责写,一个负责读。
 *
 * 注意:这里存的是**地址**(host:port),不是实现对象 ——
 * 因为真实场景下 Provider 在另一台机器上,Consumer 只能拿到"到哪去找它"。
 *
 * 实现演进:
 *   - LocalServiceRegistry:进程内内存版(用于先跑通逻辑 / 单元测试)
 *   - ZookeeperServiceRegistry:M3 目标实现,临时节点 + 会话断开自动删除
 */
public interface ServiceRegistry {

    /**
     * 注册一个服务地址
     *
     * @param serviceName 服务名(用接口全限定名,如 com.handwrite.rpc.example.HelloService)
     * @param address     服务地址,格式 host:port
     */
    void register(String serviceName, String address);

    /**
     * 注销服务地址(Provider 优雅下线时调用)
     */
    void unregister(String serviceName, String address);
}
