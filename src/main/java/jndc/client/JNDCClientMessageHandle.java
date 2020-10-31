package jndc.client;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.EventLoop;
import io.netty.channel.SimpleChannelInboundHandler;
import jndc.core.NDCMessageProtocol;
import jndc.core.UniqueBeanManage;
import jndc.core.message.RegistrationMessage;
import jndc.test.ClientTest;
import jndc.utils.InetUtils;
import jndc.utils.LogPrint;
import jndc.utils.ObjectSerializableUtils;

public class JNDCClientMessageHandle extends SimpleChannelInboundHandler<NDCMessageProtocol> {

    private JNDCClient client;

    public static final String NAME = "NDC_CLIENT_HANDLE";

    public JNDCClientMessageHandle(JNDCClient jndcClient) {
        this.client = jndcClient;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        RegistrationMessage registrationMessage = new RegistrationMessage();
        registrationMessage.setEquipmentId(InetUtils.uniqueInetTag);
        byte[] bytes = ObjectSerializableUtils.object2bytes(registrationMessage);


        NDCMessageProtocol tqs = NDCMessageProtocol.of(InetUtils.localInetAddress, InetUtils.localInetAddress, 0, 777, 80, NDCMessageProtocol.MAP_REGISTER);
        tqs.setData(bytes);
        ctx.writeAndFlush(tqs);

    }

    @Override
    protected void channelRead0(ChannelHandlerContext channelHandlerContext, NDCMessageProtocol ndcMessageProtocol) throws Exception {
        Integer type = ndcMessageProtocol.getType();
        try {


            if (type == NDCMessageProtocol.TCP_DATA) {
                //todo TCP_DATA
                JNDCClientConfigCenter bean = UniqueBeanManage.getBean(JNDCClientConfigCenter.class);
                bean.addMessageToReceiveQueue(ndcMessageProtocol);
            }


            if (type == NDCMessageProtocol.TCP_ACTIVE) {
                //todo TCP_ACTIVE
                JNDCClientConfigCenter bean = UniqueBeanManage.getBean(JNDCClientConfigCenter.class);
                bean.addMessageToReceiveQueue(ndcMessageProtocol);
            }

            if (type == NDCMessageProtocol.MAP_REGISTER) {
                //todo MAP_REGISTER

                //print msg
                RegistrationMessage object = ndcMessageProtocol.getObject(RegistrationMessage.class);
                LogPrint.log(object.getMessage());


                //register channel,client just hold one channelHandlerContext
                JNDCClientConfigCenter bean = UniqueBeanManage.getBean(JNDCClientConfigCenter.class);
                bean.registerMessageChannel(0, channelHandlerContext);


            }

            if (type == NDCMessageProtocol.CONNECTION_INTERRUPTED) {
                //todo CONNECTION_INTERRUPTED

                JNDCClientConfigCenter bean = UniqueBeanManage.getBean(JNDCClientConfigCenter.class);
                bean.shutDownClientPortProtector(ndcMessageProtocol);
            }

            if (type == NDCMessageProtocol.NO_ACCESS) {
                //todo CONNECTION_INTERRUPTED
                LogPrint.log(new String(ndcMessageProtocol.getData()));
            }

            if (type == NDCMessageProtocol.USER_ERROR) {
                //todo UN_CATCHABLE_ERROR
                LogPrint.log(new String(ndcMessageProtocol.getData()));

            }

            if (type == NDCMessageProtocol.UN_CATCHABLE_ERROR) {
                //todo UN_CATCHABLE_ERROR
                LogPrint.log(new String(ndcMessageProtocol.getData()));
            }

        } catch (Exception e) {
            //
            NDCMessageProtocol copy = ndcMessageProtocol.copy();
            copy.setType(NDCMessageProtocol.UN_CATCHABLE_ERROR);
            channelHandlerContext.writeAndFlush(copy);
        }


    }


    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        LogPrint.log("client connection interrupted");
        EventLoop eventExecutors = ctx.channel().eventLoop();
        client.createClient(eventExecutors);
    }



    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        LogPrint.err("unCatchable client error：" + cause.getMessage() );
    }

}
