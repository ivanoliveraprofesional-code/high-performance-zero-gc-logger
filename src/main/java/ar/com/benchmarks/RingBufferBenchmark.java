package ar.com.benchmarks;

import ar.com.logger.PanamaLogger;
import ar.com.ringbuffer.RingBuffer;
import org.openjdk.jmh.annotations.*;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class RingBufferBenchmark {

    private PanamaLogger logger;
    private RingBuffer ringBuffer;
    private Thread consumerThread;
    private AtomicBoolean running;

    private byte[] payload;

    @Setup(Level.Trial)
    public void setup() {
        payload = "MARKET_DATA|SYMBOL=BTC|PRICE=98000.00|QTY=0.01".getBytes(StandardCharsets.US_ASCII);

        logger = new PanamaLogger();
        ringBuffer = new RingBuffer(1024 * 16);
        running = new AtomicBoolean(true);

        consumerThread = new Thread(() -> {
            while (running.get()) {
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
        while (!ringBuffer.tryPublish(payload, 0, payload.length)) {
            Thread.onSpinWait();
        }
    }
    
    public static void main(String[] args) throws Exception {
        org.openjdk.jmh.Main.main(args);
    }
}