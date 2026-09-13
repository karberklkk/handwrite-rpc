package com.handwrite.rpc.core.loadbalance;

import com.handwrite.rpc.common.exception.RpcException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 轮询策略(M4):按顺序一个一个来,到末尾绕回开头。
 *
 * ⚠️ 这里有一个真实踩过的坑,值得单独讲:
 *
 *   注册中心返回的地址列表,**顺序是不保证稳定的**。
 *   LocalServiceRegistry 底层是 ConcurrentHashMap + Set,
 *   迭代顺序由哈希值决定 —— 既不是插入顺序,增删元素后还可能变化。
 *
 *   如果直接 `addresses.get(index % size)`,那么 index 的"含义"会漂移:
 *   上一次 index=0 指向 A,这一次 index=0 可能指向 B。
 *   结果就是"看起来像轮询,实际接近随机",而且**没有任何报错**,极难排查。
 *
 *   解法:进轮询之前**先排序**,让顺序变成确定的。
 */
public class RoundRobinLoadBalancer implements LoadBalancer {

    /** serviceName → 该服务自己的轮询计数器(每个服务独立计数) */
    private final Map<String, AtomicInteger> counters = new ConcurrentHashMap<>();

    @Override
    public String select(String serviceName, List<String> addresses, String hashKey) {
        if (addresses == null || addresses.isEmpty()) {
            throw new RpcException("没有可用服务实例: " + serviceName);
        }

        // 关键一步:先排序,把"不确定的顺序"变成"确定的顺序"
        List<String> sorted = new ArrayList<>(addresses);
        Collections.sort(sorted);

        int size = sorted.size();
        int index = counters
                .computeIfAbsent(serviceName, k -> new AtomicInteger())
                .getAndIncrement();

        // Math.floorMod 而不是 %:
        //   index 是 AtomicInteger,理论上会溢出成负数;
        //   负数 % size 会得到负数 → get() 抛 IndexOutOfBounds。
        //   floorMod 保证结果恒为非负。
        return sorted.get(Math.floorMod(index, size));
    }

    @Override
    public String name() {
        return "RoundRobin";
    }
}
