package com.handwrite.rpc.example;

import com.handwrite.rpc.common.registry.ServiceDiscovery;
import com.handwrite.rpc.common.registry.ServiceRegistry;
import com.handwrite.rpc.core.registry.LocalServiceRegistry;

import java.util.List;

/**
 * M3 阶段 A-0:注册中心本地实现的冒烟测试。
 *
 * 为什么要先做这个?—— 不碰网络、不碰 Netty,先把新加的抽象单独验一遍。
 * 如果这里就错了,后面接进调用链时会很难定位是"注册中心错了"还是"接线错了"。
 *
 * 期望输出(6 项全 PASS 才算过):
 *   [PASS] 1. 注册 2 个地址后,发现到 2 个
 *   [PASS] 2. 重复注册同一地址,数量不变(Set 去重)
 *   [PASS] 3. 注销 1 个后,剩 1 个
 *   [PASS] 3b. 剩下的是 ADDR_2
 *   [PASS] 4. 全部注销后,发现到 0 个
 *   [PASS] 5. 查不存在的服务,返回空列表而不是 null
 */
public class RegistrySmokeTest {

    /** 用接口全限定名当服务名 —— 和真实 RPC 里的约定保持一致 */
    private static final String SERVICE = HelloService.class.getName();

    /** 两个假的 Provider 地址(本测试不联网,只是字符串) */
    private static final String ADDR_1 = "127.0.0.1:28888";
    private static final String ADDR_2 = "127.0.0.1:28889";

    public static void main(String[] args) {

        // ===== 准备工作:一个对象,两个视角 =====
        // LocalServiceRegistry 同时实现了两个接口,所以同一个实例可以
        // 用不同的「引用类型」去看它 —— 这就是多态(接口隔离)。
        // 用哪个引用,就只能调哪个接口里声明的方法。
        LocalServiceRegistry registry  = new LocalServiceRegistry();
        ServiceRegistry      registrar = registry;   // 注册视角:只有 register / unregister
        ServiceDiscovery     finder    = registry;   // 发现视角:只有 discover

        // ===== 测试 1:注册 2 个,发现到 2 个 =====
        registrar.register(SERVICE, ADDR_1);
        registrar.register(SERVICE, ADDR_2);
        List<String> afterRegister = finder.discover(SERVICE);
        printList("1) 注册 2 个后", afterRegister);
        check("1. 注册 2 个地址后,发现到 2 个", afterRegister.size() == 2);

        // ===== 测试 2:重复注册同一地址,数量不变(验证 Set 去重)=====
        registrar.register(SERVICE, ADDR_1);          // 再注册一次 ADDR_1
        List<String> afterDup = finder.discover(SERVICE);
        printList("2) 重复注册 ADDR_1 后", afterDup);
        check("2. 重复注册同一地址,数量不变(Set 去重)", afterDup.size() == 2);

        // ===== 测试 3:注销 1 个,剩 1 个 =====
        registrar.unregister(SERVICE, ADDR_1);
        List<String> afterUnregister = finder.discover(SERVICE);
        printList("3) 注销 ADDR_1 后", afterUnregister);
        check("3. 注销 1 个后,剩 1 个", afterUnregister.size() == 1);
        check("3b. 剩下的是 ADDR_2", afterUnregister.contains(ADDR_2));

        // ===== 测试 4:全部注销,发现到 0 个 =====
        registrar.unregister(SERVICE, ADDR_2);
        List<String> afterAllRemoved = finder.discover(SERVICE);
        printList("4) 全部注销后", afterAllRemoved);
        check("4. 全部注销后,发现到 0 个", afterAllRemoved.isEmpty());

        // ===== 测试 5:查不存在的服务,必须是空列表而不是 null =====
        List<String> notExist = finder.discover("com.handwrite.rpc.example.NotExistService");
        printList("5) 查不存在的服务", notExist);
        check("5. 查不存在的服务,返回空列表而不是 null",
                notExist != null && notExist.isEmpty());

        // ===== 彩蛋(思考题,不是测试)=====
        // LocalServiceRegistry.unregister() 里,当地址集合被清空时,会把整个
        // serviceName 这个 key 从 Map 里 remove 掉。
        // 想一想:如果【不】去掉这个 key,会留下什么?长期运行会怎样?
        System.out.println();
        System.out.println("彩蛋思考:unregister 里为什么要把空集合的 key 也删掉?");
    }

    /**
     * 小工具:统一打印 PASS/FAIL
     */
    private static void check(String title, boolean ok) {
        System.out.println((ok ? "[PASS] " : "[FAIL] ") + title);
    }

    /**
     * 小工具:打印一个地址列表
     */
    private static void printList(String label, List<String> list) {
        System.out.println(label + " -> " + list + "  (size=" + list.size() + ")");
    }
}
