package ar.com.ringbuffer;

import java.util.concurrent.atomic.AtomicLong;
import ar.com.logger.PanamaLogger;

/**
 * MPSC (Multi-Producer Single-Consumer) Lock-Free Ring Buffer.
 * Optimized for Cache Line efficiency and minimal volatile reads.
 */
public class RingBuffer {
    
    // --- PADDING (Cache Line Isolation) ---
    long p1, p2, p3, p4, p5, p6, p7;
    
    // PRODUCER STATE
    private final AtomicLong head = new AtomicLong(0);
    
    // TAIL CACHE (Optimización 3A): 
    // Esta variable NO es volatile. Es una copia local que ven los productores.
    // Vive en la caché L1/L2 de los núcleos productores.
    private long tailCache = 0;
    
    long a1, a2, a3, a4, a5, a6, a7; 

    // CONSUMER STATE
    private final AtomicLong tail = new AtomicLong(0);
    
    long b1, b2, b3, b4, b5, b6, b7; 

    private final LogEvent[] buffer;
    private final int mask;
    private final int capacity;

    public RingBuffer(int capacity) {
        if (Integer.bitCount(capacity) != 1) throw new IllegalArgumentException("Capacity must be power of 2");
        this.capacity = capacity;
        this.mask = capacity - 1;
        this.buffer = new LogEvent[capacity];
        for (int i = 0; i < capacity; i++) buffer[i] = new LogEvent();
    }

    public boolean tryPublish(byte[] data, int offset, int length) {
        long currentHead;
        long limit;
        
        do {
            currentHead = head.get();
            // ... (Misma lógica de Tail Cache que ya tenías) ...
            limit = tailCache + capacity;
            if (currentHead >= limit) {
                long actualTail = tail.get();
                tailCache = actualTail;
                if (currentHead >= actualTail + capacity) return false;
            }
        } while (!head.compareAndSet(currentHead, currentHead + 1));
        
        // --- ESCRIBIR DATOS ---
        int index = (int) (currentHead & mask);
        LogEvent event = buffer[index];
        
        // Zero-GC Copy
        event.set(data, offset, length);
        
        event.committed = true; // Store Fence
        return true;
    }

    /**
     * SOLUCIÓN 2B: DRAIN PATTERN (Batch Consumer)
     * En lugar de devolver un evento y confiar en que el usuario llame a free(),
     * tomamos el control del ciclo.
     * * Procesa TODOS los eventos disponibles hasta vaciar el buffer o alcanzar un límite.
     * @param logger El consumidor final (PanamaLogger)
     * @return Cantidad de eventos procesados
     */
    public int drain(PanamaLogger logger) {
        long currentTail = tail.get();
        long currentHead = head.get();
        
        if (currentTail >= currentHead) return 0;
        
        int processedCount = 0;
        long nextSequence = currentTail;
        
        while (nextSequence < currentHead) {
            int index = (int) (nextSequence & mask);
            LogEvent event = buffer[index];
            
            // LOAD FENCE (Volatile Read)
            if (!event.committed) {
                break; 
            }
            
            // --- ZERO-GC VICTORY ---
            // Pasamos el array byte[] interno directamente a la memoria Off-Heap.
            // CERO objetos creados. CERO basura.
            logger.logBytes(event.getBuffer(), event.getLength());
            // -----------------------
            
            event.clear();
            nextSequence++;
            processedCount++;
        }
        
        if (processedCount > 0) {
            tail.set(nextSequence); // Store Fence (volatile set de AtomicLong es suficiente)
        }
        
        return processedCount;
    }
}