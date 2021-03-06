package web.core;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslHandler;
import jndc.core.*;
import jndc.core.config.ServerConfig;
import jndc.core.config.UnifiedConfiguration;
import jndc.server.NDCServerConfigCenter;
import jndc.utils.InetUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import web.utils.SslOneWayContextFactory;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import java.io.FileInputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.security.KeyStore;

public class WebServer {


    private final Logger logger = LoggerFactory.getLogger(getClass());

    private EventLoopGroup eventLoopGroup = NettyComponentConfig.getNioEventLoopGroup();


    public void start() {
        UnifiedConfiguration bean = UniqueBeanManage.getBean(UnifiedConfiguration.class);
        ServerConfig serverConfig = bean.getServerConfig();
        int manageCenterPort = serverConfig.getManagementApiPort();
        InetAddress localInetAddress = InetUtils.localInetAddress;
        InetSocketAddress inetSocketAddress = new InetSocketAddress(localInetAddress, manageCenterPort);




        ChannelInitializer<Channel> channelInitializer = new ChannelInitializer<Channel>() {

            @Override
            protected void initChannel(Channel channel) throws Exception {







                ChannelPipeline pipeline = channel.pipeline();

                String http = "http";//HttpServerCodec
                String oag = "oag";//HttpObjectAggregator
                String ws = "ws";//WebSocketServerProtocolHandler


                if (serverConfig.isUseSsl()){
                    SSLContext serverSSLContext = serverConfig.getServerSSLContext();
                    SSLEngine sslEngine = serverSSLContext.createSSLEngine();
                    sslEngine.setUseClientMode(false);//设置为服务器模式
                    pipeline.addFirst(CustomSslHandler.NAME, new CustomSslHandler(sslEngine));
                }



                pipeline.addLast( http,new HttpServerCodec());
                pipeline.addAfter(http, oag, new HttpObjectAggregator(2 * 1024 * 1024));//限制缓冲最大值为2mb
                pipeline.addAfter(oag, AuthTokenChecker.NAME,new AuthTokenChecker());
                pipeline.addAfter(AuthTokenChecker.NAME, JNDCRequestDecoder.NAME, new JNDCRequestDecoder());
                pipeline.addAfter(JNDCRequestDecoder.NAME, WebContentHandler.NAME, new WebContentHandler());
                pipeline.addAfter(WebContentHandler.NAME,ws,new WebSocketServerProtocolHandler("/ws"));
                pipeline.addAfter(ws, WebSocketHandle.NAME, new WebSocketHandle());


            }
        };

        ServerBootstrap b = new ServerBootstrap();
        b.group(eventLoopGroup)
                .channel(NioServerSocketChannel.class)//
                .localAddress(inetSocketAddress)//　
                .childHandler(channelInitializer);

        b.bind().addListener(x -> {
            if (x.isSuccess()) {
                logger.info("bind manage center : " + inetSocketAddress + " success");
            } else {
                logger.error("bind manage center : " + inetSocketAddress + " fail");
            }

        });
    }

}
