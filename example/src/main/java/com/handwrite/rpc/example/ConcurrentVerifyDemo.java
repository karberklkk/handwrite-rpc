package com.handwrite.rpc.example;

import com.handwrite.rpc.core.proxy.RpcClientProxy;
import com.handwrite.rpc.core.transport.NettyClient;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * M2-3 验收演示(先启动 ServerDemo):
 * 用多线程在【同一条连接】上并发调用,验证 requestId + Future 的匹配是否正确。
 *
 * 每个线程每次调用都传入唯一字符串 t{线程号}-{序号},
 * 只有当返回值恰好是 "Hello, t{线程号}-{序号}!" 才计为成功 —— 任何"拿错响应"都会被抓出来。
 *
 * 建议压测时关闭 trace 日志(否则控制台会被刷屏):
 *     java -Drpc.trace=false -cp ... com.handwrite.rpc.example.ConcurrentVerifyDemo 28888
 */
public class ConcurrentVerifyDemo {

    private static final int THREADS = 10;            // 并发线程数
    private static final int CALLS_PER_THREAD = 500;  // 每线程调用次数

    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : ServerDemo.PORT;

        NettyClient nettyClient = new NettyClient("127.0.0.1", port);
        RpcClientProxy proxyFactory = new RpcClientProxy(nettyClient);
        // 代理只持有连接与接口信息,无状态 → 可以安全地被多个线程共享
        HelloService helloService = proxyFactory.getProxy(HelloService.class);

        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch startGate = new CountDownLatch(1);   // 让所有线程同时开跑,制造真正的并发
        AtomicInteger ok = new AtomicInteger();
        AtomicInteger mismatch = new AtomicInteger();
        AtomicInteger errors = new AtomicInteger();
        List<Future<?>> tasks = new ArrayList<>();

        for (int t = 0; t < THREADS; t++) {
            final int threadNo = t;
            tasks.add(pool.submit(() -> {
                try {
                    startGate.await();
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
                for (int i = 1; i <= CALLS_PER_THREAD; i++) {
                    String arg = "t" + threadNo + "-" + i;
                    String expected = "Hello, " + arg + "!";
                    try {
                        String actual = helloService.sayHello(arg);
                        if (expected.equals(actual)) {
                            ok.incrementAndGet();
                        } else {
                            mismatch.incrementAndGet();
                            if (mismatch.get() <= 3) {
                                System.out.println("[Concurrent] 响应错配: 期望=" + expected + ", 实际=" + actual);
                            }
                        }
                    } catch (Exception e) {
                        errors.incrementAndGet();
                        if (errors.get() <= 3) {
                            System.out.println("[Concurrent] 调用异常: " + e.getMessage());
                        }
                    }
                }
            }));
        }

        int total = THREADS * CALLS_PER_THREAD;
        System.out.println("[Concurrent] 开始: " + THREADS + " 线程 × " + CALLS_PER_THREAD
                + " 次 = " + total + " 次并发调用(共用同一条连接)");
        long start = System.currentTimeMillis();
        startGate.countDown();
        for (Future<?> task : tasks) {
            task.get();
        }
        long cost = Math.max(System.currentTimeMillis() - start, 1);

        pool.shutdown();
        System.out.println("[Concurrent] 完成: 成功=" + ok.get()
                + ", 错配=" + mismatch.get()
                + ", 异常=" + errors.get()
                + ", 耗时=" + cost + "ms"
                + ", 吞吐≈" + (total * 1000L / cost) + " 次/秒");
        System.out.println("[Concurrent] 结论: "
                + (mismatch.get() == 0 && errors.get() == 0 ? "✅ 并发匹配全部正确" : "❌ 存在问题,需要排查"));

        nettyClient.close();
    }
}
