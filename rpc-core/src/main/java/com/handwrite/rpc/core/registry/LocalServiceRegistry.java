package com.handwrite.rpc.core.registry;

import com.handwrite.rpc.common.registry.ServiceDiscovery;
import com.handwrite.rpc.common.registry.ServiceRegistry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 本地内存版注册中心(M3 第一步):同时实现"注册"与"发现"两个接口。
 *
 * 用途:先把 Provider 注册 → Consumer 发现的链路跑通,并作为 ZK 版的**对照实现**,
 * 方便对比"内存 Map"与"分布式协调服务"的差异。
 *
 * 局限(必须能主动说清,面试会问):
 *   1. 注册信息只活在本 JVM 内存里 —— 跨进程/跨机器无法共享
 *   2. 进程重启数据即丢失(没有持久化)
 *   3. 无法感知其他实例的上下线(没有会话/心跳机制)
 * 这三条正是要换成 ZooKeeper 的原因。
 */
public class LocalServiceRegistry implements ServiceRegistry, ServiceDiscovery {

    /** 服务名 → 地址集合(Set 去重:同一实例重复注册不会产生重复地址) */
    private final Map<String, Set<String>> services = new ConcurrentHashMap<>();

    @Override
    public void register(String serviceName, String address) {
        services.computeIfAbsent(serviceName, key -> ConcurrentHashMap.newKeySet()).add(address);
    }

    @Override
    public void unregister(String serviceName, String address) {
        Set<String> addresses = services.get(serviceName);
        if (addresses != null) {
            addresses.remove(address);
            // 某个服务已经没有任何实例时,把整个 key 移除,避免留下空集合
            if (addresses.isEmpty()) {
                services.remove(serviceName);
            }
        }
    }

    @Override
    public List<String> discover(String serviceName) {
        Set<String> addresses = services.get(serviceName);
        if (addresses == null || addresses.isEmpty()) {
            return Collections.emptyList();   // 约定:没有实例时返回空列表,绝不返回 null
        }
        return new ArrayList<>(addresses);
    }
}
