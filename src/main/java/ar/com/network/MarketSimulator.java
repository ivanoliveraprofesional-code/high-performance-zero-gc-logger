package ar.com.network;

import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.StandardProtocolFamily;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.charset.StandardCharsets;

public class MarketSimulator extends Thread {
    
    private static final String MULTICAST_IP = "230.0.0.1";
    
    private static final int MCAST_PORT = 5000;
    private static final int SENDER_PORT = 5001;   
    private static final int MSG_POOL_SIZE = 1024;
    
    private final ByteBuffer[] messagePool = new ByteBuffer[MSG_POOL_SIZE];
    private volatile boolean running = true;

    public MarketSimulator() {
        for (int i = 0; i < MSG_POOL_SIZE; i++) {
            String msg = "SEQ=" + i + "|SYM=BTC-USD|PX=" + (90000 + i) + ".50|QTY=0.5|SIDE=BUY";
            byte[] bytes = msg.getBytes(StandardCharsets.US_ASCII);
            messagePool[i] = ByteBuffer.allocateDirect(bytes.length);
            messagePool[i].put(bytes);
            messagePool[i].flip();
        }
    }

    @Override
    public void run() {
        try (DatagramChannel channel = DatagramChannel.open(StandardProtocolFamily.INET)) {
            channel.setOption(StandardSocketOptions.IP_MULTICAST_IF, NetworkInterface.getByInetAddress(java.net.InetAddress.getLocalHost()));
            channel.setOption(StandardSocketOptions.SO_REUSEADDR, true);            
            channel.bind(new InetSocketAddress(SENDER_PORT));
            
            InetSocketAddress group = new InetSocketAddress(MULTICAST_IP, MCAST_PORT);
            System.out.println("[SIMULATOR] Enviando desde puerto " + SENDER_PORT + " -> Multicast " + MCAST_PORT);
            
            int msgIdx = 0;
            int mask = MSG_POOL_SIZE - 1;
            
            while (running) {
                ByteBuffer buffer = messagePool[msgIdx & mask];
                buffer.rewind();
                channel.send(buffer, group);
                msgIdx++;
                Thread.onSpinWait(); 
            }
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    public void stopSimulator() {
        running = false;
    }
}