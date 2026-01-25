package ar.com.logger;

import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.LongAdder;

public class PanamaLogger implements AutoCloseable {

    private static final String TARGET = System.getProperty("os.name").toLowerCase().contains("win") ? "NUL" : "/dev/null";
    
    private final Arena arena;
    private final MemorySegment bufferSegment;
    private final long bufferCapacity = 1024 * 8; // 8KB
    private long currentOffset = 0; 
    
    private final FileChannel channel;
    private final ByteBuffer flushBuffer;

    public static final LongAdder IO_ERRORS = new LongAdder();

    private static final MemorySegment PREFIX_SEG = MemorySegment.ofArray("LOG: ".getBytes(StandardCharsets.US_ASCII));
    private static final MemorySegment NEWLINE_SEG = MemorySegment.ofArray("\n".getBytes(StandardCharsets.US_ASCII));

    public PanamaLogger() {
        this.arena = Arena.ofShared(); 
        try {
            this.channel = new FileOutputStream(TARGET, true).getChannel();
            this.bufferSegment = arena.allocate(bufferCapacity);
            this.flushBuffer = bufferSegment.asByteBuffer();
        } catch (Exception e) {
            arena.close();
            throw new RuntimeException("Failed to initialize PanamaLogger", e);
        }
    }
    
    /**
     * ZERO-GC / ZERO-COPY LOGGING
     * Escribe bytes crudos directamente al buffer off-heap.
     */
    public void logBytes(byte[] msg, int len) {
        if (!arena.scope().isAlive()) throw new IllegalStateException("Logger is closed");

        long prefixLen = PREFIX_SEG.byteSize();
        long newlineLen = NEWLINE_SEG.byteSize();
        
        // 1. Escribir PREFIX (Bulk Copy)
        if (bufferCapacity - currentOffset < prefixLen) flush();
        MemorySegment.copy(PREFIX_SEG, 0, bufferSegment, currentOffset, prefixLen);
        currentOffset += prefixLen;

        // 2. Escribir MENSAJE (Byte-by-Byte Loop -> JIT Vectorized)
        // Check de espacio para el mensaje
        int bytesWritten = 0;
        while (bytesWritten < len) {
            long remaining = bufferCapacity - currentOffset;
            if (remaining == 0) {
                flush();
                remaining = bufferCapacity;
            }

            long toWrite = Math.min(remaining, len - bytesWritten);

            // Bucle Crudo: El JIT adora esto. Lo convierte en instrucciones SIMD.
            // No usamos MemorySegment.ofArray() para evitar crear el objeto wrapper del segmento.
            for (int i = 0; i < toWrite; i++) {
                byte b = msg[bytesWritten + i];
                bufferSegment.set(ValueLayout.JAVA_BYTE, currentOffset + i, b);
            }
            
            currentOffset += toWrite;
            bytesWritten += toWrite;
        }

        // 3. Escribir NEWLINE (Bulk Copy)
        if (bufferCapacity - currentOffset < newlineLen) flush();
        MemorySegment.copy(NEWLINE_SEG, 0, bufferSegment, currentOffset, newlineLen);
        currentOffset += newlineLen;
    }

    public void log(String msg) {
        if (!arena.scope().isAlive()) throw new IllegalStateException("Logger is closed");

        int msgLen = msg.length();
        long prefixLen = PREFIX_SEG.byteSize();
        long newlineLen = NEWLINE_SEG.byteSize();

        if (bufferCapacity - currentOffset < prefixLen) flush();
        
        MemorySegment.copy(PREFIX_SEG, 0, bufferSegment, currentOffset, prefixLen);
        currentOffset += prefixLen;

        int charsWritten = 0;
        while (charsWritten < msgLen) {
            long remaining = bufferCapacity - currentOffset;
            if (remaining == 0) {
                flush();
                remaining = bufferCapacity;
            }

            long toWrite = Math.min(remaining, msgLen - charsWritten);

            for (int i = 0; i < toWrite; i++) {
                bufferSegment.set(ValueLayout.JAVA_BYTE, currentOffset + i, (byte) msg.charAt(charsWritten + i));
            }
            
            currentOffset += toWrite;
            charsWritten += toWrite;
        }

        if (bufferCapacity - currentOffset < newlineLen) flush();
        MemorySegment.copy(NEWLINE_SEG, 0, bufferSegment, currentOffset, newlineLen);
        currentOffset += newlineLen;
    }

    public void flush() {
        if (currentOffset == 0) return;
        try {
            flushBuffer.position(0);
            flushBuffer.limit((int) currentOffset);
            channel.write(flushBuffer);
            currentOffset = 0; 
        } catch (IOException e) {
            IO_ERRORS.increment();
        }
    }

    @Override
    public void close() {
        if (!arena.scope().isAlive()) return;
        flush();
        try { channel.close(); } catch (IOException e) {}
        arena.close(); 
    }
}