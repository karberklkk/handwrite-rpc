package com.handwrite.rpc.core.discovery;

import com.handwrite.rpc.common.exception.RpcException;
import com.handwrite.rpc.common.registry.ServiceDiscovery;
import com.handwrite.rpc.core.loadbalance.LoadBalancer;

import java.util.ArrayList;
import java.util.List;

/**
 * 地址解析器(M3 引入,M4 扩强)。
 *
 * 职责:把「我要调 HelloService」翻译成「具体去 127.0.0.1:28888 找它」。
 *
 * 为什么单独抽一个类,而不是写在 RpcClientProxy 里?
 *   因为 [发现] 和 [选一个] 是两件事:
 *     - ServiceDiscovery:从注册中心拿到【全部】地址
 *     - LoadBalancer    :从全部地址里【选一个】
 *   分开之后,M4 换负载均衡策略只改一行构造参数,别的代码一个字不用动。
 *
 * ── 地址格式约定 ──────────────────────────────────────────
 *   "host:port",例如 "127.0.0.1:28888"
 *   本类用 lastIndexOf(':') 解析,所以 IPv6 的 "[::1]:8080" 也能正确处理
 *   (如果用 split(":") 就会被 IPv6 里的冒号坑到)。
 */
public class ServiceResolver {

    private final ServiceDiscovery discovery;
    private final LoadBalancer loadBalancer;

    public ServiceResolver(ServiceDiscovery discovery, LoadBalancer loadBalancer) {
        this.discovery = discovery;
        this.loadBalancer = loadBalancer;
    }

    /**
     * 解析出一个可用地址。
     *
     * @param hashKey 一致性哈希用的"粘性 key"(可以是 null / 空,其他策略会忽略)
     * @throws RpcException 该服务当前没有任何可用实例时抛出
     */
    public String resolve(String serviceName, String hashKey) {
        List<String> addresses = resolveAll(serviceName);
        return loadBalancer.select(serviceName, addresses, hashKey);
    }

    /**
     * 拿到该服务的【全部】可用地址(升序、去重)。
     * Failover 容错需要它 —— 一个节点失败时,才有"下一个"可试。
     */
    public List<String> resolveAll(String serviceName) {
        List<String> addresses = discovery.discover(serviceName);
        if (addresses == null || addresses.isEmpty()) {
            throw new RpcException("没有找到可用服务实例: " + serviceName
                    + ",请确认 Provider 已启动并完成注册");
        }
        List<String> copy = new ArrayList<>(addresses);
        copy.sort(String::compareTo);          // 排序,让顺序确定(见 RoundRobin 的说明)
        return copy;
    }

    /** 当前该服务有几个实例(留给容错逻辑判断"最多能重试几次") */
    public int instanceCount(String serviceName) {
        List<String> addresses = discovery.discover(serviceName);
        return addresses == null ? 0 : addresses.size();
    }

    public LoadBalancer getLoadBalancer() {
        return loadBalancer;
    }

    // ─────────────────────────────────────────────────────
    //  地址解析小工具
    // ─────────────────────────────────────────────────────

    /** 把 "127.0.0.1:28888" 拆成 {"127.0.0.1", "28888"} */
    public static String[] splitAddress(String address) {
        if (address == null) {
            throw new RpcException("地址为 null");
        }
        int idx = address.lastIndexOf(':');    // 用 lastIndexOf,兼容 IPv6
        if (idx <= 0 || idx == address.length() - 1) {
            throw new RpcException("非法的地址格式(应为 host:port): " + address);
        }
        return new String[]{address.substring(0, idx), address.substring(idx + 1)};
    }

    /** 把 "127.0.0.1:28888" 的端口部分取出来 */
    public static int portOf(String address) {
        String portText = splitAddress(address)[1];
        try {
            return Integer.parseInt(portText);
        } catch (NumberFormatException e) {
            throw new RpcException("地址里的端口不是数字: " + address);
        }
    }

    /** 把 "127.0.0.1:28888" 的主机部分取出来 */
    public static String hostOf(String address) {
        return splitAddress(address)[0];
    }
}
