package ar.com.benchmarks;

import java.util.concurrent.TimeUnit;

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

import ar.com.logger.BadLogger;
import ar.com.logger.BufferedLogger;
import ar.com.logger.PanamaLogger;
import ar.com.logger.ZeroGcLogger;

@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class LoggerBenchmark {

    private BadLogger badLogger;
    private BufferedLogger bufferedLogger;
    private ZeroGcLogger zeroGcLogger;
    private PanamaLogger panamaLogger;

    @Setup(Level.Trial) 
    public void setup() {
        badLogger = new BadLogger();
        bufferedLogger = new BufferedLogger();
        zeroGcLogger = new ZeroGcLogger();
        panamaLogger = new PanamaLogger();
    }
    
    @TearDown(Level.Trial)
    public void tearDown() {
        bufferedLogger.close();
        zeroGcLogger.close();
        panamaLogger.close();
    }
      
    @Benchmark
    public void testBadLog() {
        badLogger.log("User login attempt failed code 503");
    }

    @Benchmark
    public void testBufferedLog() {
        bufferedLogger.log("User login attempt failed code 503");
    }
    
    @Benchmark
    public void testZeroGcLog() {
        // Test de enteros (int)
        zeroGcLogger.logInt(503);
    }

    @Benchmark
    public void testZeroGcCharSequence() {
        // Test de texto con CharSequence
        zeroGcLogger.logCharSequence("User login attempt failed code 503");
    }

    @Benchmark
    public void testPanamaLog() {
        // Test con Java 21 FFM API (Standard)
        panamaLogger.log("User login attempt failed code 503");
    }
    
    public static void main(String[] args) throws Exception {
        org.openjdk.jmh.Main.main(args);
    }
}