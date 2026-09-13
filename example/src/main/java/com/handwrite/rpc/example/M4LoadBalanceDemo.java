package com.handwrite.rpc.example;

import com.handwrite.rpc.common.registry.ServiceDiscovery;
import com.handwrite.rpc.common.registry.ServiceRegistry;
import com.handwrite.rpc.core.discovery.ServiceResolver;
import com.handwrite.rpc.core.loadbalance.ConsistentHashLoadBalancer;
import com.handwrite.rpc.core.loadbalance.LoadBalancer;
import com.handwrite.rpc.core.loadbalance.RandomLoadBalancer;
import com.handwrite.rpc.core.loadbalance.RoundRobinLoadBalancer;
import com.handwrite.rpc.core.proxy.RpcClientProxy;
import com.handwrite.rpc.core.registry.LocalServiceRegistry;
import com.handwrite.rpc.core.registry.ServiceProvider;
import com.handwrite.rpc.core.transport.NettyServer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * M4 验收 Demo:负载均衡 + 容错(全在同一个 JVM 里跑,便于观察)
 *
 * 验证四件事:
 *   ① 随机 / 轮询 / 一致性哈希 三种策略的【分布差异】
 *   ② 一致性哈希的【粘性】:同一个 key 恒定落同一台
 *   ③ 轮询策略的【顺序确定性】(进轮询前先排序的作用)
 *   ④ Failover 容错:某节点连不上时自动换下一个
 *
 * ⚠️ 资源释放:main 用 try/finally 包住,保证异常时也能关掉所有 Provider。
 *    否则 Netty 的 EventLoop 线程是非守护线程,JVM 会【挂住不退出】。
 */
public class M4LoadBalanceDemo {

    private static final int[] PORTS = {28891, 28892, 28893};
    private static final String SERVICE = HelloService.class.getName();

    /** 一个"没有服务在监听"的假地址,用来模拟某个节点挂掉 */
    private static final String DEAD_ADDRESS = "127.0.0.1:28999";

    public static void main(String[] args) throws Exception {
        List<NettyServer> servers = new ArrayList<>();
        RpcClientProxy fastProxy = null;
        RpcClientProxy failoverProxy = null;

        try {
            System.out.println("==================== M4:负载均衡 + 容错 ====================");

            LocalServiceRegistry registry = new LocalServiceRegistry();
            ServiceRegistry registrar = registry;
            ServiceDiscovery discovery = registry;

            // ── 起 3 个 Provider,端口各不同,返回值带端口标记 ──
            for (int port : PORTS) {
                ServiceProvider sp = new ServiceProvider();
                sp.register(SERVICE, new TaggedHelloService(port));

                NettyServer server = new NettyServer(port, sp);
                servers.add(server);

                Thread t = new Thread(() -> {
                    try {
                        server.start();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }, "provider-" + port);
                t.setDaemon(true);
                t.start();
            }
            for (NettyServer s : servers) {
                waitUntilRunning(s, 5000);
                registrar.register(SERVICE, s.localAddress());
            }
            System.out.println("已启动 3 个 Provider 并全部注册:");
            for (String addr : discovery.discover(SERVICE)) {
                System.out.println("   " + addr);
            }

            // ══════════════════════════════════════════════════
            System.out.println();
            System.out.println("──────── ① 三种负载均衡策略的分布(各 30 次请求)────────");
            for (LoadBalancer lb : List.of(
                    new RandomLoadBalancer(),
                    new RoundRobinLoadBalancer(),
                    new ConsistentHashLoadBalancer())) {
                showDistribution(lb, discovery, 30);
            }

            // ══════════════════════════════════════════════════
            System.out.println();
            System.out.println("──────── ② 一致性哈希的「粘性」验证 ────────");
            ServiceResolver hashResolver =
                    new ServiceResolver(discovery, new ConsistentHashLoadBalancer());

            Map<String, String> firstHit = new LinkedHashMap<>();
            boolean sticky = true;
            for (int i = 0; i < 10; i++) {
                String port = callOnce(hashResolver, 0, "user-123");
                firstHit.putIfAbsent("user-123", port);
                if (!firstHit.get("user-123").equals(port)) {
                    sticky = false;
                    break;
                }
            }
            System.out.println("   同一个 key \"user-123\" 连续调用 10 次,始终落在端口: "
                    + firstHit.get("user-123"));
            check("一致性哈希:同一 key 恒定落同一台", sticky);

            Map<String, String> spread = new TreeMap<>();
            for (int u = 1; u <= 10; u++) {
                spread.put("user-" + u, callOnce(hashResolver, 0, "user-" + u));
            }
            System.out.println("   10 个不同 userId 的落点: " + spread.values());
            check("不同 key 会分散到不同节点(不是全挤一台)",
                    spread.values().stream().distinct().count() > 1);

            // ══════════════════════════════════════════════════
            System.out.println();
            System.out.println("──────── ③ 轮询策略的顺序确定性 ────────");
            ServiceResolver rrResolver = new ServiceResolver(discovery, new RoundRobinLoadBalancer());
            List<String> seq = new ArrayList<>();
            for (int i = 0; i < 6; i++) {
                seq.add(callOnce(rrResolver, 0, "x"));
            }
            System.out.println("   连续 6 次落点顺序: " + String.join(" → ", seq));
            System.out.println("   (每 3 次必然把三台机器各走一遍 —— 进轮询前做了排序)");

            boolean cyclic = seq.size() == 6;
            for (int i = 3; cyclic && i < seq.size(); i++) {
                cyclic = seq.get(i).equals(seq.get(i - 3));
            }
            check("轮询呈固定循环(第 4/5/6 次与第 1/2/3 次完全对应)", cyclic);

            // ══════════════════════════════════════════════════
            System.out.println();
            System.out.println("──────── ④ Failover 容错验证 ────────");
            registrar.register(SERVICE, DEAD_ADDRESS);
            System.out.println("   故意注册一个【没有服务监听】的地址: " + DEAD_ADDRESS);
            System.out.println("   现在共有 " + discovery.discover(SERVICE).size()
                    + " 个地址(其中 1 个是坏的)");

            int rounds = 20;
            int failfastFail = 0;
            int failoverFail = 0;

            ServiceResolver ff = new ServiceResolver(discovery, new RoundRobinLoadBalancer());
            fastProxy = new RpcClientProxy(ff, 0);                 // Failfast
            HelloService fastHello = fastProxy.getProxy(HelloService.class);
            for (int i = 0; i < rounds; i++) {
                try {
                    fastHello.sayHello("ff");
                } catch (Exception e) {
                    failfastFail++;
                }
            }

            ServiceResolver fo = new ServiceResolver(discovery, new RoundRobinLoadBalancer());
            failoverProxy = new RpcClientProxy(fo, 3);             // Failover
            HelloService failoverHello = failoverProxy.getProxy(HelloService.class);
            for (int i = 0; i < rounds; i++) {
                try {
                    failoverHello.sayHello("fo");
                } catch (Exception e) {
                    failoverFail++;
                }
            }

            System.out.println();
            System.out.printf("   Failfast(重试 0 次) 打 %d 次 → 失败 %d 次%n", rounds, failfastFail);
            System.out.printf("   Failover(重试 3 次) 打 %d 次 → 失败 %d 次%n", rounds, failoverFail);
            check("Failfast 会踩到坏节点(失败 > 0)", failfastFail > 0);
            check("Failover 能自动绕开坏节点(失败 = 0)", failoverFail == 0);

            System.out.println();
            System.out.println("==================== M4 验收结束 ====================");

        } finally {
            // 无论正常结束还是抛异常,都要释放资源 ——
            // Netty 的 EventLoop 是非守护线程,不关掉 JVM 就退不出去。
            if (fastProxy != null) {
                fastProxy.close();
            }
            if (failoverProxy != null) {
                failoverProxy.close();
            }
            for (NettyServer s : servers) {
                s.close();
            }
            System.out.println("--- 所有 Provider 与客户端资源已释放 ---");
        }
    }

    // ─────────────────────────────────────────────────────

    private static void showDistribution(LoadBalancer lb, ServiceDiscovery discovery, int times)
            throws Exception {
        ServiceResolver resolver = new ServiceResolver(discovery, lb);
        RpcClientProxy proxy = new RpcClientProxy(resolver, 0);
        HelloService hello = proxy.getProxy(HelloService.class);

        Map<String, Integer> counter = new TreeMap<>();
        try {
            for (int i = 0; i < times; i++) {
                counter.merge(portOf(hello.sayHello("u" + i)), 1, Integer::sum);
            }
        } finally {
            proxy.close();
        }

        StringBuilder line = new StringBuilder();
        counter.forEach((port, n) -> line.append(port).append(":").append(n).append("  "));
        System.out.printf("   %-16s %s%n", lb.name(), line);
    }

    /** 调一次,返回落到的端口 */
    private static String callOnce(ServiceResolver resolver, int retries, String name) throws Exception {
        RpcClientProxy proxy = new RpcClientProxy(resolver, retries);
        try {
            HelloService hello = proxy.getProxy(HelloService.class);
            return portOf(hello.sayHello(name));
        } finally {
            proxy.close();
        }
    }

    /** 从 "Hello, xxx! (来自端口 28891)" 里把端口抠出来 */
    private static String portOf(String response) {
        int idx = response.lastIndexOf(' ');
        return response.substring(idx + 1).replace(")", "");
    }

    private static void waitUntilRunning(NettyServer server, long timeoutMillis)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (!server.isRunning()) {
            if (System.currentTimeMillis() > deadline) {
                throw new IllegalStateException("Provider 启动超时: " + server.getPort());
            }
            Thread.sleep(50);
        }
    }

    private static void check(String title, boolean ok) {
        System.out.println((ok ? "   [PASS] " : "   [FAIL] ") + title);
    }
}
