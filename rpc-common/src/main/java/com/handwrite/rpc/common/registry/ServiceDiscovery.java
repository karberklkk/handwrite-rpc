package com.handwrite.rpc.common.registry;

import java.util.List;

/**
 * 服务发现(M3 注册中心抽象)。
 *
 * 职责:Consumer 调用前,按服务名查到当前可用的地址列表。
 * 返回 List 而不是单个地址 —— 因为同一服务可能有多个实例,
 * 这也是 M4 负载均衡的前提(有了列表才能"选一个")。
 */
public interface ServiceDiscovery {

    /**
     * 查询某个服务的全部可用地址
     *
     * @param serviceName 服务名(接口全限定名)
     * @return 地址列表(host:port);没有可用实例时返回空列表(不要返回 null)
     */
    List<String> discover(String serviceName);
}
