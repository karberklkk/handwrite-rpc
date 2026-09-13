package com.handwrite.rpc.core.loadbalance;

import java.util.List;

/**
 * 负载均衡策略(M4)。
 *
 * 出现的位置:Consumer 拿到注册中心返回的【地址列表】后,由本接口决定"这次用哪一个"。
 *
 * 三种经典策略的对比(面试常问):
 *   ┌──────────────┬────────────────────┬──────────────────────────┐
 *   │ 策略          │ 优点                │ 缺点                      │
 *   ├──────────────┼────────────────────┼──────────────────────────┤
 *   │ Random       │ 实现最简单          │ 短时间可能连续打同一个      │
 *   │ RoundRobin   │ 绝对均匀,可预期      │ 不感知机器性能差异          │
 *   │ ConsistentHash│ 同一 key 恒定落同一台│ 节点少时容易倾斜(需虚拟节点)│
 *   └──────────────┴────────────────────┴──────────────────────────┘
 */
public interface LoadBalancer {

    /**
     * 从候选地址里挑一个。
     *
     * @param serviceName 服务名。RoundRobin 用它做"每个服务各自计数"的 key
     * @param addresses   可用地址列表(调用方需保证非空)
     * @param hashKey     本次请求的"粘性 key"。只有一致性哈希会用它;
     *                    其他策略可以忽略(传 null 也不会出错)
     * @return 选中的地址,形如 "127.0.0.1:28888"
     */
    String select(String serviceName, List<String> addresses, String hashKey);

    /** 策略名,用于日志和演示输出 */
    String name();
}
