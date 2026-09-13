package com.handwrite.rpc.common.exception;

/**
 * RPC 统一异常(M4 引入)。
 *
 * 为什么要自定义异常,而不是到处抛 RuntimeException?
 *   1. 调用方可以【精确捕获】RPC 相关的问题:`catch (RpcException e)`
 *   2. 可以把"网络问题 / 服务不存在 / 超时 / 业务异常"包成同一种对外表现,
 *      但通过 message 和 cause 保留细节
 *   3. 继承 RuntimeException(非受检),调用方不必被迫写 try-catch,
 *      但仍然可以按需捕获
 */
public class RpcException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public RpcException(String message) {
        super(message);
    }

    public RpcException(String message, Throwable cause) {
        super(message, cause);
    }
}
