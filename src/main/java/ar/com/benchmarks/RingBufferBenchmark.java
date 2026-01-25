package ar.com.benchmarks;

import ar.com.logger.PanamaLogger;
import ar.com.ringbuffer.RingBuffer;
import org.openjdk.jmh.annotations.*;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput) // Medimos operaciones por segundo
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class RingBufferBenchmark {

    private PanamaLogger logger;
    private RingBuffer ringBuffer;
    private Thread consumerThread;
    private AtomicBoolean running;

    // El payload pre-calculado (byte array) para no crear Strings en el benchmark
    private byte[] payload;

    @Setup(Level.Trial)
    public void setup() {
        // 1. Preparamos el mensaje raw (Zero-GC)
        payload = "MARKET_DATA|SYMBOL=BTC|PRICE=98000.00|QTY=0.01".getBytes(StandardCharsets.US_ASCII);

        // 2. Iniciamos el Logger y el Buffer
        logger = new PanamaLogger();
        ringBuffer = new RingBuffer(1024 * 16); // 16K slots
        running = new AtomicBoolean(true);

        // 3. Arrancamos el Consumidor en un hilo separado (Background)
        // Este hilo es vital: si no vacía el buffer, el benchmark se traba.
        consumerThread = new Thread(() -> {
            while (running.get()) {
                // Drenamos constantemente
                if (ringBuffer.drain(logger) == 0) {
                    Thread.onSpinWait();
                }
            }
        });
        consumerThread.start();
    }

    @TearDown(Level.Trial)
    public void tearDown() throws InterruptedException {
        running.set(false);
        consumerThread.join();
        logger.close();
    }

    @Benchmark
    public void testZeroGcThroughput() {
        // Intentamos publicar. 
        // Si el buffer está lleno (porque el consumidor es lento), giramos (spin).
        // JMH medirá cuántas veces por segundo logramos entrar aquí exitosamente.
        while (!ringBuffer.tryPublish(payload, 0, payload.length)) {
            Thread.onSpinWait();
        }
    }
    
    // Hack para correrlo desde Eclipse si quieres
    public static void main(String[] args) throws Exception {
        org.openjdk.jmh.Main.main(args);
    }
}