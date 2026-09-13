package com.handwrite.rpc.core.loadbalance;

import com.handwrite.rpc.common.exception.RpcException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 一致性哈希策略(M4)。
 *
 * ── 要解决的问题 ──────────────────────────────────────────
 * Random / RoundRobin 有个共同缺陷:同一个用户的请求会被打到不同机器上。
 * 如果服务端有【本地缓存 / 本地状态】(比如按用户 id 缓存的会话),
 * 那么换一台机器就等于缓存失效。
 * 一致性哈希保证:**同一个 key(比如 userId)永远落在同一台机器上。**
 *
 * ── 基本原理 ──────────────────────────────────────────────
 *   1. 把 [0, 2^64) 想象成一个"环"
 *   2. 每个服务地址,通过哈希映射到环上的某个点
 *   3. 请求 key 也哈希到环上,然后【顺时针】找第一个节点
 *
 * ── 虚拟节点(本类的 VIRTUAL_NODES)────────────────────────
 * 只映射 1 个点的话,节点在环上分布会很不均匀(数据倾斜):
 * 可能某个节点"管"了半个环,另一个只"管"了一小块。
 *
 * 解法:每个物理地址映射成 160 个"虚拟节点"(加后缀再哈希),
 *       让每个物理节点在环上散布成一大片点,分布就均匀了。
 *
 *       增删节点时,受影响的也只有环上相邻的一小段 —— 这就是
 *       "一致性哈希在扩缩容时只迁移少量数据"的来源。
 */
public class ConsistentHashLoadBalancer implements LoadBalancer {

    /** 每个物理节点生成多少个虚拟节点(经验值:100~200) */
    private static final int VIRTUAL_NODES = 160;

    /** serviceName → 哈希环(TreeMap 有序,支持"找顺时针第一个") */
    private final Map<String, TreeMap<Long, String>> rings = new ConcurrentHashMap<>();

    /** serviceName → 当前环里装的是哪一批地址(用于判断是否需要重建) */
    private final Map<String, Set<String>> ringNodes = new ConcurrentHashMap<>();

    @Override
    public String select(String serviceName, List<String> addresses, String hashKey) {
        if (addresses == null || addresses.isEmpty()) {
            throw new RpcException("没有可用服务实例: " + serviceName);
        }

        TreeMap<Long, String> ring = ringFor(serviceName, addresses);
        long key = hash(hashKey == null ? serviceName : hashKey);

        // 顺时针找第一个 >= key 的节点
        Map.Entry<Long, String> entry = ring.ceilingEntry(key);
        if (entry == null) {
            entry = ring.firstEntry();       // 走到了环末尾 → 绕回环首
        }
        return entry.getValue();
    }

    /** 取(必要时重建)某个服务的哈希环 */
    private TreeMap<Long, String> ringFor(String serviceName, List<String> addresses) {
        Set<String> current = new HashSet<>(addresses);
        Set<String> known = ringNodes.get(serviceName);

        if (known == null || !known.equals(current)) {
            TreeMap<Long, String> ring = new TreeMap<>();
            for (String address : current) {
                for (int i = 0; i < VIRTUAL_NODES; i++) {
                    ring.put(hash(address + "#VN" + i), address);
                }
            }
            rings.put(serviceName, ring);
            ringNodes.put(serviceName, current);
        }
        return rings.get(serviceName);
    }

    /**
     * 把字符串哈希成一个 64 位整数。
     * 用 MD5 而不是 String.hashCode():后者只有 32 位且分布一般,
     * 在环上容易扎堆;MD5 取前 8 字节得到 64 位,分布均匀得多。
     */
    private long hash(String key) {
        try {
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            byte[] digest = md5.digest(key.getBytes(StandardCharsets.UTF_8));
            long h = 0;
            for (int i = 0; i < 8; i++) {
                h = (h << 8) | (digest[i] & 0xFF);
            }
            return h;
        } catch (NoSuchAlgorithmException e) {
            throw new RpcException("MD5 算法不可用", e);
        }
    }

    @Override
    public String name() {
        return "ConsistentHash";
    }
}
