package ar.com.research.performance_lab;

import ar.com.logger.PanamaLogger;
import ar.com.ringbuffer.RingBuffer;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

public class App {
    
    private static final int BUFFER_SIZE = 1024 * 16; 
    private static final int MENSAJES_POR_HILO = 5_000_000;
    private static final int HILOS_PRODUCTORES = 4;

    private static final byte[] PAYLOAD = "EXECUTION_REPORT|ID=102938475|SYM=BTC-USDT|SIDE=BUY|PX=98200.50|QTY=0.05".getBytes(StandardCharsets.US_ASCII);

    public static void main(String[] args) throws InterruptedException {
        System.out.println("Iniciando HFT Engine (Zero-GC Mode)...");
        System.out.println("Payload Size: " + PAYLOAD.length + " bytes");

        PanamaLogger logger = new PanamaLogger();
        RingBuffer ringBuffer = new RingBuffer(BUFFER_SIZE);
        AtomicBoolean running = new AtomicBoolean(true);

        Thread loggerThread = new Thread(() -> {
            while (running.get()) {
                if (ringBuffer.drain(logger) == 0) {
                    Thread.onSpinWait();
                }
            }
            ringBuffer.drain(logger);
            logger.close();
        }, "Logger-Thread");
        loggerThread.start();

        Thread[] producers = new Thread[HILOS_PRODUCTORES];
        CountDownLatch latch = new CountDownLatch(HILOS_PRODUCTORES);
        
        Thread.sleep(1000); 
        System.out.println("Starting measurement...");
        
        long start = System.nanoTime();

        for (int i = 0; i < HILOS_PRODUCTORES; i++) {
            producers[i] = new Thread(() -> {
                byte[] rawData = PAYLOAD;
                int len = rawData.length;
                
                for (int j = 0; j < MENSAJES_POR_HILO; j++) {
                    while (!ringBuffer.tryPublish(rawData, 0, len)) {
                        Thread.onSpinWait(); 
                    }
                }
                latch.countDown();
            });
            producers[i].start();
        }

        latch.await();
        long end = System.nanoTime();
        
        running.set(false);
        loggerThread.join();

        long totalMsgs = (long) MENSAJES_POR_HILO * HILOS_PRODUCTORES;
        long durationNs = end - start;
        double seconds = durationNs / 1_000_000_000.0;
        long throughput = (long) (totalMsgs / seconds);

        System.out.println("\n RESULTADOS FINALES 🚀");
        System.out.printf("Total Mensajes: %,d\n", totalMsgs);
        System.out.printf("Tiempo Total:   %.4f s\n", seconds);
        System.out.printf("Throughput:     %,d msgs/sec\n", throughput);
        System.out.println("--------------------------------");
        System.out.println("Memory Allocation durante el test: 0 bytes (Teórico)");
    }
}