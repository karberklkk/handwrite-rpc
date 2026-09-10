package com.handwrite.rpc.core.transport;

import com.handwrite.rpc.api.RpcRequest;
import com.handwrite.rpc.api.RpcResponse;
import com.handwrite.rpc.common.RpcConstants;
import com.handwrite.rpc.common.RpcLogger;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

/**
 * M2-3 Consumer 端 Netty 客户端:requestId + CompletableFuture 异步化。
 *
 * 核心思想("取餐叫号"):
 *   1) 发送前生成唯一 requestId,并注册一个 CompletableFuture 到 Map 里
 *   2) 把请求写进同一条连接(连接复用,不需要每个请求新建连接)
 *   3) 调用方在 future 上等待结果(get 带超时)
 *   4) 响应回来时,按响应里的 requestId 找到对应的 future 并 complete
 *
 * 与 M2-1 的区别:M2-1 用一个共享阻塞队列,只能顺序调用;
 * 现在一个连接上可以同时挂多个在途请求,互不干扰(并发安全)。
 */
public class NettyClient {

    /** 默认超时:10 秒 */
    private static final long DEFAULT_TIMEOUT_MILLIS = 10_000;

    private final String host;
    private final int port;
    private final long timeoutMillis;
    private final EventLoopGroup group = new NioEventLoopGroup();

    /** 在途请求:requestId → 等待中的 Future(并发安全) */
    private final Map<Long, CompletableFuture<RpcResponse>> pendingRequests = new ConcurrentHashMap<>();

    /** 请求号生成器:每个请求一个唯一 id,响应用同一个 id 回来 */
    private final AtomicLong requestIdGenerator = new AtomicLong();

    private volatile Channel channel;

    public NettyClient(String host, int port) {
        this(host, port, DEFAULT_TIMEOUT_MILLIS);
    }

    public NettyClient(String host, int port, long timeoutMillis) {
        this.host = host;
        this.port = port;
        this.timeoutMillis = timeoutMillis;
    }

    /**
     * 建立连接(只连一次,之后复用;连不上则抛异常)
     */
    public synchronized void connect() throws InterruptedException {
        if (channel != null && channel.isActive()) {
            return;
        }
        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(group)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.TCP_NODELAY, true)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        // 流水线(M2-2 自研协议):出站 RpcEncoder;入站 切帧 → RpcDecoder → 响应分发
                        ch.pipeline()
                                .addLast(new RpcEncoder())
                                .addLast(new LengthFieldBasedFrameDecoder(
                                        RpcConstants.MAX_FRAME_LENGTH,
                                        RpcConstants.LENGTH_FIELD_OFFSET,
                                        RpcConstants.LENGTH_FIELD_LENGTH,
                                        0,
                                        0))
                                .addLast(new RpcDecoder())
                                .addLast(new SimpleClientHandler(NettyClient.this::completeResponse));
                    }
                });
        channel = bootstrap.connect(host, port).sync().channel();
        RpcLogger.trace("[NettyClient] 已连接 " + host + ":" + port);
    }

    /**
     * 发送请求并等待响应(超时抛异常)
     */
    public RpcResponse sendRequest(RpcRequest request) throws Exception {
        connect();

        // 1) 分配唯一请求号,并注册等待中的 Future
        long requestId = requestIdGenerator.incrementAndGet();
        request.setRequestId(requestId);

        CompletableFuture<RpcResponse> future = new CompletableFuture<>();
        pendingRequests.put(requestId, future);

        try {
            // 2) 写出请求(同一连接复用,Netty 保证线程安全)
            RpcLogger.trace("[Consumer] 发送 requestId=" + requestId
                    + ", 线程=" + Thread.currentThread().getName());
            channel.writeAndFlush(request);

            // 3) 等待响应:由 completeResponse 按 requestId 唤醒
            RpcResponse response = future.get(timeoutMillis, TimeUnit.MILLISECONDS);
            RpcLogger.trace("[Consumer] 收到响应 requestId=" + response.getRequestId()
                    + ", 线程=" + Thread.currentThread().getName());
            return response;

        } catch (TimeoutException e) {
            throw new RuntimeException("RPC 调用超时(" + timeoutMillis + "ms): "
                    + request.getServiceName() + "#" + request.getMethodName()
                    + ", requestId=" + requestId);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            throw cause instanceof Exception ? (Exception) cause : new RuntimeException(cause);
        } finally {
            // 4) 无论成功、超时还是异常都要移除,避免 Map 无限增长(内存泄漏)
            pendingRequests.remove(requestId);
        }
    }

    /**
     * 收到响应时的回调:按 requestId 唤醒对应的等待者
     */
    private void completeResponse(RpcResponse response) {
        CompletableFuture<RpcResponse> future = pendingRequests.get(response.getRequestId());
        if (future != null) {
            future.complete(response);
        } else {
            // 例如已超时被移除,属于正常情况(迟到响应),记录一下即可
            RpcLogger.trace("[NettyClient] 收到无主响应(可能已超时),丢弃 requestId=" + response.getRequestId());
        }
    }

    /**
     * 关闭连接与线程组(顺便让所有在途请求立即失败,避免调用方一直挂着)
     */
    public void close() {
        pendingRequests.forEach((id, future) ->
                future.completeExceptionally(new RuntimeException("客户端已关闭, requestId=" + id)));
        pendingRequests.clear();
        if (channel != null) {
            channel.close();
        }
        group.shutdownGracefully();
    }
}
