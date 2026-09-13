package com.handwrite.rpc.core.loadbalance;

import com.handwrite.rpc.common.exception.RpcException;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 随机策略(M4):从候选地址里随机挑一个。
 *
 * 优点:实现最简单,不需要维护状态,天然"无状态可水平扩展"。
 * 缺点:短时间窗口内可能连续选中同一台(但长期看是均匀的)。
 *
 * 为什么用 ThreadLocalRandom 而不是 new Random()?
 *   Random 是线程安全的(内部 CAS),多线程争抢同一个种子会互相拖慢;
 *   ThreadLocalRandom 每个线程一个独立种子,没有竞争,吞吐更高。
 */
public class RandomLoadBalancer implements LoadBalancer {

    @Override
    public String select(String serviceName, List<String> addresses, String hashKey) {
        if (addresses == null || addresses.isEmpty()) {
            throw new RpcException("没有可用服务实例: " + serviceName);
        }
        int index = ThreadLocalRandom.current().nextInt(addresses.size());
        return addresses.get(index);
    }

    @Override
    public String name() {
        return "Random";
    }
}
