package jndc.server;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import jndc.core.IPFilter;
import jndc.core.NDCMessageProtocol;
import jndc.core.NettyComponentConfig;

import jndc.core.data_store.DBWrapper;
import jndc.utils.UniqueInetTagProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * port bind context
 */
public class ServerPortProtector {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private int port;

    private ServerBootstrap serverBootstrap;

    private EventLoopGroup eventLoopGroup;


    private Map<String, ServerTCPDataHandle> faceTCPMap = new ConcurrentHashMap<>();//store tcp


    public ServerPortProtector(int port) {
        this.port = port;
    }

    public void start() {
        InnerActiveCallBack innerActiveCallBack = (uniqueTag, serverTCPDataHandle) -> faceTCPMap.put(uniqueTag, serverTCPDataHandle);


        //create  Initializer
        ChannelInitializer<Channel> channelInitializer = new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel channel) throws Exception {
                ChannelPipeline pipeline = channel.pipeline();
                pipeline.addFirst(IPFilter.NAME, IPFilter.STATIC_INSTANCE);
                pipeline.addAfter(IPFilter.NAME, ServerTCPDataHandle.NAME, new ServerTCPDataHandle(innerActiveCallBack));
            }
        };


        eventLoopGroup = NettyComponentConfig.getNioEventLoopGroup();

        serverBootstrap = new ServerBootstrap();
        serverBootstrap.group(eventLoopGroup)
                .channel(NioServerSocketChannel.class)//
                .localAddress(new InetSocketAddress(port))//　
                .childHandler(channelInitializer);

        serverBootstrap.bind().addListener(x -> {
            if (x.isSuccess()) {
                logger.info("bind server port:" + port + " success");
            } else {
                logger.error("bind server port:" + port + " fail");
            }

        });
    }

    public void receiveMessage(NDCMessageProtocol ndcMessageProtocol) {
        InetAddress remoteInetAddress = ndcMessageProtocol.getRemoteInetAddress();
        int remotePort = ndcMessageProtocol.getRemotePort();
        String client = UniqueInetTagProducer.get4Client(remoteInetAddress, remotePort);
        ServerTCPDataHandle serverTCPDataHandle = faceTCPMap.get(client);
        if (serverTCPDataHandle == null) {
            //todo drop
            logger.error("not found the tcp connection:" + client);
            return;
        }

        serverTCPDataHandle.receiveMessage(Unpooled.copiedBuffer(ndcMessageProtocol.getData()));


    }

    /**
     * close the port listener and interrupt all tcp connection
     */
    public void releaseRelatedResources() {
        if (eventLoopGroup != null) {
            eventLoopGroup.shutdownGracefully().addListener(x -> {
                if (x.isSuccess()) {
                    logger.info(" release serverPortProtector for port " + port + " success");
                    eventLoopGroup = null;
                } else {
                    logger.error("serverPortProtector for port " + port + " release fail");
                }
            });
        }

        if (faceTCPMap != null) {
            faceTCPMap.forEach((k, v) -> {
                v.releaseRelatedResources();
            });
            faceTCPMap = null;
        }
        serverBootstrap = null;

        //update db info
        DBWrapper<ServerPortBind> dbWrapper = DBWrapper.getDBWrapper(ServerPortBind.class);
        dbWrapper.customExecute("update server_port_bind set portEnable=0 where port=?", port);
    }


    /**
     * interrupt one tcp connection
     * @param ndcMessageProtocol
     */
    public void connectionInterrupt(NDCMessageProtocol ndcMessageProtocol) {
        InetAddress remoteInetAddress = ndcMessageProtocol.getRemoteInetAddress();
        int remotePort = ndcMessageProtocol.getRemotePort();
        String client = UniqueInetTagProducer.get4Client(remoteInetAddress, remotePort);

        ServerTCPDataHandle serverTCPDataHandle = faceTCPMap.get(client);
        if (serverTCPDataHandle == null) {
            //todo drop
            logger.error("not found the tcp connection:" + client);
            return;
        }
        serverTCPDataHandle.releaseRelatedResources();

    }


    public interface InnerActiveCallBack {

        public void register(String uniqueTag, ServerTCPDataHandle serverTCPDataHandle);
    }


}
