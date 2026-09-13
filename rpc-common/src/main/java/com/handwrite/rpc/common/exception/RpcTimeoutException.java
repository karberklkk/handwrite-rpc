package com.handwrite.rpc.common.exception;

/**
 * RPC 调用超时(M4 引入)。
 *
 * 为什么要把"超时"从 RpcException 里单独分出来?
 * ────────────────────────────────────────────────────────
 * 因为超时和"连接失败"在【能否重试】上完全不同:
 *
 *   · 连接失败(连不上 / 连接被拒)
 *       → 请求【根本没发出去】→ 换一台机器重试是安全的 ✅
 *
 *   · 调用超时
 *       → 请求【可能已经到达服务端并且执行完了】,只是响应没回来
 *       → 这时候重试,可能造成【重复执行】
 *         比如"扣款 100 元""下单""减少库存" —— 重试一次就是扣两次 ⚠️
 *
 * 所以 Failover 策略必须能区分这两种失败:
 *   连接失败 → 可以换节点重试
 *   超时     → 默认【不重试】,直接把异常抛给调用方
 *
 * 这就是面试常问的「**什么请求不能盲目重试**」:
 *   答案的核心是【幂等性】—— 幂等的可以重试,非幂等的要慎之又慎。
 */
public class RpcTimeoutException extends RpcException {

    private static final long serialVersionUID = 1L;

    private final long timeoutMillis;

    public RpcTimeoutException(String message, long timeoutMillis) {
        super(message);
        this.timeoutMillis = timeoutMillis;
    }

    public long getTimeoutMillis() {
        return timeoutMillis;
    }
}
