package ar.com.benchmarks;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

import ar.com.logger.PanamaLogger;
import ar.com.ringbuffer.RingBuffer;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class IngestionBenchmark {

    private PanamaLogger logger;
    private RingBuffer ringBuffer;
    private Thread consumerThread;
    private AtomicBoolean running;
    
    private byte[] transferBuffer;
    private int msgLen;

    @Setup(Level.Trial)
    public void setup() {
        byte[] rawBytes = "MARKET_DATA|SYM=ETH|PX=2500.00".getBytes(StandardCharsets.US_ASCII);
        msgLen = rawBytes.length;
        transferBuffer = new byte[2048];
        System.arraycopy(rawBytes, 0, transferBuffer, 0, msgLen);

        logger = new PanamaLogger();
        ringBuffer = new RingBuffer(1024 * 16);
        running = new AtomicBoolean(true);

        consumerThread = new Thread(() -> {
            while (running.get()) {
                if (ringBuffer.drain(logger) == 0) Thread.onSpinWait();
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
    public void testRingBufferIngestion() {
        while (!ringBuffer.tryPublish(transferBuffer, 0, msgLen)) {
            Thread.onSpinWait();
        }
    }
    
    public static void main(String[] args) throws Exception {
        org.openjdk.jmh.Main.main(args);
    }
}