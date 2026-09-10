package com.handwrite.rpc.common;

/**
 * 轻量日志开关(M2-3)。
 *
 * 为什么需要:框架里的 trace 日志在调试时非常有用,但在并发压测时会把控制台刷爆。
 * 用系统属性控制:默认开启,压测时关闭 ——
 *
 *     关闭 trace:java -Drpc.trace=false -cp ... 主类
 *     开启 trace:java -cp ... 主类        (默认)
 */
public final class RpcLogger {

    private RpcLogger() {
    }

    /** 是否打印 trace 级日志(可用 -Drpc.trace=false 关闭) */
    public static final boolean TRACE = Boolean.parseBoolean(System.getProperty("rpc.trace", "true"));

    /**
     * 打印一条 trace 日志(关闭时不输出)
     */
    public static void trace(String message) {
        if (TRACE) {
            System.out.println(message);
        }
    }
}
