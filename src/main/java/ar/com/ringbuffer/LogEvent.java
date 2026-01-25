package ar.com.ringbuffer;

import java.nio.charset.StandardCharsets;

public class LogEvent {
    public static final int MAX_MSG_LENGTH = 128;
    
    private final byte[] messageBuffer = new byte[MAX_MSG_LENGTH];
    private int length;
    
    // PADDING INTERNO (Para separar el flag del contenido en caché si es posible)
    long p1, p2, p3, p4, p5, p6, p7;
    
    // CRÍTICO: volatile asegura "Happens-Before".
    // Cuando el consumidor lea 'true', GARANTIZADO verá los datos escritos antes.
    public volatile boolean committed = false;
    
    public void set(byte[] src, int offset, int len) {
        // Validación de seguridad (truncar si es necesario)
        int copyLen = Math.min(len, MAX_MSG_LENGTH);
        
        // System.arraycopy es intrínseco de JVM (memcpy)
        System.arraycopy(src, offset, this.messageBuffer, 0, copyLen);
        this.length = copyLen;
    }

    // Mantenemos el método String solo para conveniencia en tests lentos
    public void set(String msg) {
        byte[] bytes = msg.getBytes(StandardCharsets.US_ASCII); // <-- ESTO GENERA BASURA
        set(bytes, 0, bytes.length);
    }
    
    public byte[] getBuffer() { return messageBuffer; }
    public int getLength() { return length; }
    
    public void clear() {
        this.committed = false; // Reset simple
    }
}