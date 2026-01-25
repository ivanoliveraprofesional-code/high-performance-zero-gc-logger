package ar.com.ringbuffer;

import java.util.concurrent.atomic.AtomicLong;
import ar.com.logger.PanamaLogger;

public class RingBuffer {
    
    long p1, p2, p3, p4, p5, p6, p7;
    
    private final AtomicLong head = new AtomicLong(0);
    
    private long tailCache = 0;
    
    long a1, a2, a3, a4, a5, a6, a7; 

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
            limit = tailCache + capacity;
            if (currentHead >= limit) {
                long actualTail = tail.get();
                tailCache = actualTail;
                if (currentHead >= actualTail + capacity) return false;
            }
        } while (!head.compareAndSet(currentHead, currentHead + 1));
        
        int index = (int) (currentHead & mask);
        LogEvent event = buffer[index];
        
        event.set(data, offset, length);
        
        event.committed = true;
        return true;
    }

    public int drain(PanamaLogger logger) {
        long currentTail = tail.get();
        long currentHead = head.get();
        
        if (currentTail >= currentHead) return 0;
        
        int processedCount = 0;
        long nextSequence = currentTail;
        
        while (nextSequence < currentHead) {
            int index = (int) (nextSequence & mask);
            LogEvent event = buffer[index];
            
            if (!event.committed) {
                break; 
            }
            
            logger.logBytes(event.getBuffer(), event.getLength());
            
            event.clear();
            nextSequence++;
            processedCount++;
        }
        
        if (processedCount > 0) {
            tail.set(nextSequence);
        }
        
        return processedCount;
    }
}