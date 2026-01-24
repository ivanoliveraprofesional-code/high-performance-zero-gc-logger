# High-Performance Zero-Allocation Logger (Java 21 FFM)

![Java](https://img.shields.io/badge/Java-21%20(Preview)-orange.svg)
![License](https://img.shields.io/badge/License-MIT-blue.svg)
![Architecture](https://img.shields.io/badge/Architecture-Off--Heap-red.svg)

An experimental, ultra-low latency logging library designed for **High-Frequency Trading (HFT)** environments. This implementation leverages the modern **Java 21 Foreign Function & Memory (FFM) API (Project Panama)** to achieve **sub-microsecond latency** and **Zero-Garbage Collection (Zero-GC)** pressure, replacing dangerous legacy `Unsafe` practices with safer, standard APIs.

## 🎯 Project Goal

In latency-critical systems, standard logging libraries (Log4j, SLF4J) introduce unacceptable overhead due to object allocation (`String`, `LogEvent`, `StringBuilder`). These allocations trigger "Stop-the-World" Garbage Collection pauses, which can lead to financial loss in trading engines.

This project demonstrates how to use **Off-Heap MemorySegments** and **Arenas** to manage memory manually, achieving C++ levels of performance within the Java Virtual Machine.

## 📊 Benchmarks

Benchmarks were executed using **JMH (Java Microbenchmark Harness)**.

| Implementation | Latency (Score) | GC Allocation Rate | Throughput Impact |
| :--- | :--- | :--- | :--- |
| **Standard IO** (`FileWriter`) | `3.697 us/op` | `~1,240 B/op` | 🔴 Severe Blocking |
| **Buffered IO** (Heap Buffer) | `0.020 us/op` | `~2,650 MB/sec`* | 🟡 High GC Pressure |
| **Panama Logger** (FFM API) | **`0.041 us/op`** | **`≈ 0 B/op`** | 🟢 Invisible |

*> Note: While Buffered IO is fast, it generates massive garbage (Strings) at high throughput (2.6GB/s), eventually saturating the CPU with GC cycles. Panama Logger generates zero garbage.*

## ⚙️ Technical Architecture

### 1. Modern Off-Heap Management (Project Panama)
Instead of using legacy `sun.misc.Unsafe` (which is unsafe and being deprecated), this library uses the standard **FFM API**:
* **Arenas:** Explicit lifecycle management (`malloc`/`free`).
* **MemorySegment:** Safe bounds-checked memory access that the JIT compiler optimizes to raw assembly.
* **Benefit:** We gain memory safety (no random RAM corruption) without sacrificing the performance of raw pointers.

### 2. Zero-Copy String Processing
Standard Java logging converts `String` to `byte[]` using `getBytes()`, creating new objects.
* **Optimization:** We iterate over the `CharSequence` and cast `char` (2 bytes) directly to `byte` (1 byte) into the raw memory segment.
* **SIMD Optimization:** Constant segments (Prefixes/Suffixes) are copied using `MemorySegment.copy`, allowing the CPU to use AVX vector instructions for bulk transfers.

### 3. Fail-Silent IO
Standard `printStackTrace()` calls are blocking. This logger swallows IO exceptions (incrementing a static counter) to prevent disk failures from freezing the trading thread, prioritizing system liveness over log completeness.

## ⚠️ Security & Design Trade-offs

This project deliberately trades safety for speed. The following risks are analyzed:

### 1. Race Conditions (Thread Safety)
* **Analysis:** The Logger relies on a `currentOffset` pointer. Concurrent access by multiple threads would cause a **race condition**, leading to memory corruption.
* **Design Decision:** This class is explicitly **Not Thread-Safe**. It is designed to be:
    * Pinned to a single thread.
    * Or used behind a **Single-Producer/Single-Consumer (SPSC) Ring Buffer**.

### 2. Data Integrity (ASCII vs UTF-16)
* **Analysis:** Java `char` is UTF-16. To avoid encoding overhead, we cast directly to `byte`.
* **Impact:** Non-ASCII characters (e.g., emojis, multibyte symbols) will suffer data truncation.
* **Constraint:** This logger is strictly for ASCII-based protocols (e.g., FIX, SBE, JSON-ASCII).

## 💻 Usage

```java
// Requires --enable-preview
try (PanamaLogger logger = new PanamaLogger()) {
    
    // Log without creating objects
    logger.log("Order MATCHED: ID 1029384 Symbol BTC-USDT");
    
    // Explicit flush if needed (otherwise auto-flushes at 8KB)
    logger.flush();
    
} // Auto-closes the Arena and frees native memory safely
```
## 🛠️ Build & Run
This project requires Java 21+.

```bash
# Build the project and shade dependencies
mvn clean package

# Run the JMH Benchmarks (GC Profiling enabled)
java --enable-preview -jar target/benchmarks.jar -prof gc
```

## 📜 License
MIT License.