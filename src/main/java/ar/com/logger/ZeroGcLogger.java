package ar.com.logger;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;

public class ZeroGcLogger {

    private static final String TARGET = System.getProperty("os.name").toLowerCase().contains("win") ? "NUL" : "/dev/null";
    
    private FileChannel channel;
    
    private ByteBuffer buffer;

    private static final byte[] PREFIX = "LOG: ".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] NEWLINE = "\n".getBytes(StandardCharsets.US_ASCII);

    public ZeroGcLogger() {
        try {
            this.channel = new FileOutputStream(TARGET, true).getChannel();
            this.buffer = ByteBuffer.allocateDirect(1024 * 8); 
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void logInt(int code) {
        if (buffer.remaining() < 20) flush();
        
        buffer.put(PREFIX);
        buffer.putInt(code); 
        buffer.put(NEWLINE);
    }
    
    public void logCharSequence(CharSequence msg) {
        
        int len = msg.length();

        if (buffer.remaining() < PREFIX.length + len + NEWLINE.length) {
            flush();
        }

        buffer.put(PREFIX);
        
        for (int i = 0; i < len; i++) {
            buffer.put((byte) msg.charAt(i));
        }
        
        buffer.put(NEWLINE);
    }

    private void flush() {
        try {
            buffer.flip();
            channel.write(buffer);
            buffer.clear();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    public void close() {
        flush();
        try { channel.close(); } catch (IOException e) {}
    }
}