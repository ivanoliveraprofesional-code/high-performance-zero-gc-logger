package ar.com.network;

import ar.com.ringbuffer.RingBuffer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.StandardProtocolFamily;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;

public class NioReceiver implements Runnable {

    private static final String MULTICAST_IP = "230.0.0.1";
    
    private static final int MCAST_PORT = 5000;   
    private static final int SENDER_PORT = 5001;    
    private static final int BUFFER_SIZE = 2048;
    
    private final RingBuffer ringBuffer;
    private volatile boolean running = true;

    public NioReceiver(RingBuffer ringBuffer) {
        this.ringBuffer = ringBuffer;
    }

    @Override
    public void run() {
        System.out.println("[RECEIVER] Zero-Allocation Mode (Connected UDP + Busy Spin)...");
        
        try (DatagramChannel channel = DatagramChannel.open(StandardProtocolFamily.INET)) {
            channel.configureBlocking(false);
            channel.setOption(StandardSocketOptions.SO_REUSEADDR, true);
            
            NetworkInterface ni = NetworkInterface.getByInetAddress(java.net.InetAddress.getLocalHost());
            
            channel.setOption(StandardSocketOptions.IP_MULTICAST_IF, ni);           
            channel.bind(new InetSocketAddress(MCAST_PORT));           
            channel.join(java.net.InetAddress.getByName(MULTICAST_IP), ni);            
            channel.connect(new InetSocketAddress("127.0.0.1", SENDER_PORT));

            ByteBuffer buffer = ByteBuffer.allocateDirect(BUFFER_SIZE);
            byte[] transferBuffer = new byte[BUFFER_SIZE]; 

            while (running) {
                buffer.clear();
                
                int bytesRead = channel.read(buffer);
                
                if (bytesRead > 0) {
                    buffer.flip();
                    buffer.get(transferBuffer, 0, bytesRead);
                    
                    while (!ringBuffer.tryPublish(transferBuffer, 0, bytesRead)) {
                        Thread.onSpinWait(); 
                    }
                } else {
                    Thread.onSpinWait();
                }
            }
            
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    public void stop() {
        running = false;
    }
}