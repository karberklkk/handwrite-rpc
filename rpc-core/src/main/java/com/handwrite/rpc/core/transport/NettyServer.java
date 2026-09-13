package com.handwrite.rpc.core.transport;

import com.handwrite.rpc.common.RpcConstants;
import com.handwrite.rpc.core.registry.ServiceProvider;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;

/**
 * Provider 端 Netty 服务器(M2 建立;M3 补上优雅停机与地址暴露)。
 *
 * 流水线(入站按 addLast 顺序):
 *   字节 → LengthFieldBasedFrameDecoder(按 length 切帧) → RpcDecoder(解协议) → RpcRequestHandler(反射执行)
 *
 * ⚠️ 出站方向是相反的(事件从 tail 往 head 走),所以写在最前面的 RpcEncoder
 *    反而是出站时【最后一个】被执行到的 —— 这正好是我们要的顺序。
 */
public class NettyServer {

    private final int port;
    private final ServiceProvider serviceProvider;

    // 改成字段:这样 close() 才拿得到它们(原来是 start() 里的局部变量)
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private volatile Channel channel;

    public NettyServer(int port, ServiceProvider serviceProvider) {
        this.port = port;
        this.serviceProvider = serviceProvider;
    }

    /** 本机监听地址 —— 注册中心里报上去的就是它 */
    public String localAddress() {
        return "127.0.0.1:" + port;
    }

    public int getPort() {
        return port;
    }

    /** 服务器是否已在监听 */
    public boolean isRunning() {
        return channel != null && channel.isActive();
    }

    /**
     * 启动并【阻塞】(Provider 是长驻进程)。
     * 想让它返回,只能从另一个线程调用 close()。
     */
    public void start() throws InterruptedException {
        bossGroup = new NioEventLoopGroup(1);    // 领位员:只负责 accept 新连接
        workerGroup = new NioEventLoopGroup();   // 服务员:处理已连接上的 IO
        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .option(ChannelOption.SO_BACKLOG, 128)
                    .childOption(ChannelOption.SO_KEEPALIVE, true)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ch.pipeline()
                                    .addLast(new RpcEncoder())
                                    .addLast(new LengthFieldBasedFrameDecoder(
                                            RpcConstants.MAX_FRAME_LENGTH,
                                            RpcConstants.LENGTH_FIELD_OFFSET,
                                            RpcConstants.LENGTH_FIELD_LENGTH,
                                            0,
                                            0))
                                    .addLast(new RpcDecoder())
                                    .addLast(new RpcRequestHandler(serviceProvider));
                        }
                    });

            ChannelFuture future = bootstrap.bind(port).sync();
            channel = future.channel();
            System.out.println("[NettyServer] 启动成功,监听地址: " + localAddress());

            // 阻塞在这里,直到 channel 被关闭
            channel.closeFuture().sync();
        } finally {
            shutdownGroups();
        }
    }

    /**
     * 优雅停机(M3 新增)。
     *
     * 停不下来的原因在于:start() 是【阻塞】的,它自己没办法"退出"。
     * 所以必须从另一个线程调 close() —— ServerDemo 正是通过
     * JVM shutdown hook(Ctrl+C 时由 JVM 触发)来做这件事。
     *
     * 做了两件事:
     *   1. 关闭监听 channel      → 不再接受新连接
     *   2. 释放两个线程组         → 回收 Netty 的线程资源
     */
    public void close() {
        Channel ch = channel;
        if (ch != null) {
            ch.close();          // 这一步会让 start() 里的 closeFuture().sync() 返回
        }
        shutdownGroups();
    }

    /** 释放线程组(置空,保证重复调用安全) */
    private void shutdownGroups() {
        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
            bossGroup = null;
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
            workerGroup = null;
        }
    }
}
