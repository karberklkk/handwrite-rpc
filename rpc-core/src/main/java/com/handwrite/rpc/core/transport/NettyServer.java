package com.handwrite.rpc.core.transport;

import com.handwrite.rpc.common.RpcConstants;
import com.handwrite.rpc.core.registry.ServiceProvider;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;

/**
 * M2-1 Provider 端 Netty 服务器:
 * 监听端口,收到 RpcRequest 后交给 RpcRequestHandler 反射执行,回写 RpcResponse。
 *
 * 说明(M2-1 简化):这里直接用 Netty 自带的 ObjectEncoder/ObjectDecoder
 * 完成"JDK 序列化 + 长度切帧";M2-2 会替换成自研协议头 + 自研编解码器。
 */
public class NettyServer {

    private final int port;
    private final ServiceProvider serviceProvider;

    public NettyServer(int port, ServiceProvider serviceProvider) {
        this.port = port;
        this.serviceProvider = serviceProvider;
    }

    /**
     * 启动并阻塞(Provider 是长驻进程)
     */
    public void start() throws InterruptedException {
        EventLoopGroup bossGroup = new NioEventLoopGroup(1);   // 领位员:接受新连接
        EventLoopGroup workerGroup = new NioEventLoopGroup();  // 服务员:处理连接上的 IO
        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .option(ChannelOption.SO_BACKLOG, 128)
                    .childOption(ChannelOption.SO_KEEPALIVE, true)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            // 流水线(M2-2 自研协议):
                            // 出站:业务 → RpcEncoder → 字节
                            // 入站:字节 → 按长度切帧 → RpcDecoder 解析头并反序列化 → 业务处理器
                            ch.pipeline()
                                    .addLast(new RpcEncoder())
                                    .addLast(new LengthFieldBasedFrameDecoder(
                                            RpcConstants.MAX_FRAME_LENGTH,
                                            RpcConstants.LENGTH_FIELD_OFFSET,
                                            RpcConstants.LENGTH_FIELD_LENGTH,
                                            0,   // lengthAdjustment:长度值就是 body 长度
                                            0))  // initialBytesToStrip:不剥离头,交给 RpcDecoder 解析
                                    .addLast(new RpcDecoder())
                                    .addLast(new RpcRequestHandler(serviceProvider));
                        }
                    });
            ChannelFuture future = bootstrap.bind(port).sync();
            System.out.println("[NettyServer] 启动成功,监听端口: " + port);
            // 阻塞,直到服务器关闭
            future.channel().closeFuture().sync();
        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }
}
