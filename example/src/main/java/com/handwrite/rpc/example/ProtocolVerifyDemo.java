package com.handwrite.rpc.example;

import com.handwrite.rpc.core.proxy.RpcClientProxy;
import com.handwrite.rpc.core.transport.NettyClient;

import java.io.OutputStream;
import java.net.Socket;

/**
 * M2-2 验收演示(先启动 ServerDemo):
 * 1) 连续 1000 次调用,逐条校验响应与请求一一对应(证明协议 + requestId 匹配有效)
 * 2) 发一段"非法魔数"的垃圾字节,验证服务端识别并关闭连接,而不是崩溃
 * 3) 再发一次正常调用,证明服务端依然健在
 */
public class ProtocolVerifyDemo {

    private static final int ROUNDS = 1000;

    public static void main(String[] args) throws Exception {
        NettyClient nettyClient = new NettyClient("127.0.0.1", ServerDemo.PORT);
        RpcClientProxy proxyFactory = new RpcClientProxy(nettyClient);
        HelloService helloService = proxyFactory.getProxy(HelloService.class);

        // ========== 1) 1000 次连续调用,验证响应匹配 ==========
        System.out.println("[Verify] 开始连续调用 " + ROUNDS + " 次...");
        long start = System.currentTimeMillis();
        int mismatch = 0;
        for (int i = 1; i <= ROUNDS; i++) {
            String expected = "Hello, n" + i + "!";
            String actual = helloService.sayHello("n" + i);
            if (!expected.equals(actual)) {
                mismatch++;
                if (mismatch <= 3) {
                    System.out.println("[Verify] 第 " + i + " 次响应不匹配: 期望=" + expected + ", 实际=" + actual);
                }
            }
        }
        long cost = System.currentTimeMillis() - start;
        System.out.println("[Verify] 完成: " + ROUNDS + " 次, 不匹配 " + mismatch + " 条, 耗时 " + cost
                + "ms, 平均 " + String.format("%.2f", cost * 1.0 / ROUNDS) + "ms/次");

        // ========== 2) 非法魔数测试:发 20 字节垃圾 ==========
        System.out.println("[Verify] 发送非法魔数数据(20 字节垃圾)...");
        try (Socket socket = new Socket("127.0.0.1", ServerDemo.PORT)) {
            OutputStream out = socket.getOutputStream();
            out.write(new byte[20]);   // 全 0,魔数不是 0xCAFE
            out.flush();
            socket.setSoTimeout(3000);
            int read = socket.getInputStream().read();   // 期望 -1:服务端主动关闭连接
            System.out.println("[Verify] 非法连接被服务端关闭: " + (read == -1 ? "是 ✅" : "否(读到 " + read + ")"));
        } catch (Exception e) {
            System.out.println("[Verify] 非法连接处理异常: " + e.getMessage());
        }

        // ========== 3) 再来一次正常调用,证明服务端没被搞挂 ==========
        String after = helloService.sayHello("alive");
        System.out.println("[Verify] 非法请求之后,正常调用结果: " + after
                + (after.equals("Hello, alive!") ? "  ✅ 服务端健在" : "  ❌ 服务端异常"));

        nettyClient.close();
    }
}
