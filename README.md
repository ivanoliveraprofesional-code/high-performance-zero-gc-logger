# Ultra-Low Latency HFT Logging Engine

![Java](https://img.shields.io/badge/Java-21%20(Preview)-orange.svg)
![Architecture](https://img.shields.io/badge/Architecture-Lock--Free-blueviolet.svg)
![Performance](https://img.shields.io/badge/Throughput-18M%20ops%2Fs-brightgreen.svg)
![License](https://img.shields.io/badge/License-MIT-blue.svg)

A ground-up implementation of a **Multi-Producer Single-Consumer (MPSC) Lock-Free Ring Buffer** coupled with a **Zero-GC Off-Heap Logger** (Project Panama). 

Designed for **High-Frequency Trading (HFT)** environments, this engine achieves **~18 million messages per second** on commodity hardware by utilizing **"Mechanical Sympathy"** techniques: CPU cache awareness, false sharing prevention, and SIMD vectorization.

## 🎯 Project Goal

In latency-critical systems, standard logging libraries (Log4j, SLF4J) and blocking queues introduce unacceptable overhead and GC pauses. 

This project demonstrates a "Zero-Compromise" architecture:
1.  **Zero-Garbage Collection:** No objects are allocated on the hot-path (End-to-End).
2.  **Zero-Locking:** Uses CAS (Compare-And-Swap) and Memory Barriers instead of OS Mutexes.
3.  **Off-Heap Storage:** Logs are written directly to native memory using Java 21 FFM API.

## 📊 Performance Benchmarks

Benchmarks executed using **JMH (Java Microbenchmark Harness)**.

### 1. System Throughput (Ring Buffer + Logger)
End-to-End measurement: Producer Thread -> Ring Buffer -> Panama Logger -> Native Memory.

| Metric | Result | Alloc Rate | Note |
| :--- | :--- | :--- | :--- |
| **Throughput** | **18,656,187 ops/sec** | `≈ 0 B/op` | 100% Zero-GC Pipeline |
| **Latency** | **~53 ns/op** | N/A | Amortized write cost |

### 2. Component Latency (Logger Only)
Comparison of the underlying write mechanisms.

| Implementation | Latency | GC Pressure | Status |
| :--- | :--- | :--- | :--- |
| **Standard IO** (`FileWriter`) | `3.697 us/op` | `~1,240 B/op` | 🔴 Blocking |
| **Buffered IO** (Heap) | `0.020 us/op` | `~2,650 MB/sec` | 🟡 High GC Risk |
| **Panama Logger** (FFM) | **`0.041 us/op`** | **`0 B/op`** | 🟢 **Selected** |

*> Note: While Heap Buffers are raw-latency fast, they generate GBs of garbage per second. The Panama Logger trades 20ns of latency for total stability (Zero-GC).*

## 🧠 Architecture & Engineering

### 1. The Lock-Free Ring Buffer (Disruptor Pattern)
Instead of blocking queues, we use a pre-allocated circular array.
* **Concurrency:** Producers use `AtomicLong.compareAndSet` (CAS) to claim slots without locking.
* **Bitwise Indexing:** Capacity is forced to a Power-of-2, allowing us to replace expensive Modulo (`%`) instructions with fast Bitwise AND (`&`).
* **False Sharing Prevention:** Critical counters (`head`, `tail`) are padded with unused `long` fields to force them into separate **64-byte CPU Cache Lines**, preventing core-to-core contention.

### 2. Off-Heap Memory (Project Panama)
We utilize the **Foreign Function & Memory (FFM) API** (Java 21) to bypass the Java Heap.
* **Safety:** Uses `Arena` scopes to prevent "Use-After-Free" bugs common in `Unsafe`.
* **Vectorization:** The JIT compiler optimizes byte copies into **AVX vector instructions** (SIMD) for bulk data transfer.

### 3. The "Drain" Pattern (Batching)
The Consumer thread does not acknowledge every single message (which would require expensive volatile writes). Instead, it **drains** all available messages in a tight loop and updates the `tail` sequence only once per batch, amortizing synchronization costs.

### 4. Zero-Copy String Processing
To achieve true Zero-GC, strings are banned from the hot-path.
* Producers write raw `byte[]` into the `LogEvent`.
* The Logger reads these bytes and writes them to Off-Heap memory.
* **Result:** Data travels from Application to Disk without ever becoming a `java.lang.String`.

## 💻 Usage

```java
// 1. Initialize Engine (16K slots, Power of 2)
PanamaLogger logger = new PanamaLogger();
RingBuffer buffer = new RingBuffer(16384);

// 2. Producer Thread (Lock-Free Write)
// Pass raw bytes to avoid String allocation
byte[] marketData = "SYM=BTC|PX=100000".getBytes(StandardCharsets.US_ASCII);

while (!buffer.tryPublish(marketData, 0, marketData.length)) {
    Thread.onSpinWait(); // Backpressure strategy
}

// 3. Consumer Thread (Batch Drain)
// Efficiently drains the buffer to disk/memory
buffer.drain(logger);
```
## 🛠️ Build & Run
This project requires Java 21+.

```bash
# Build the project
mvn clean package

# Run the Throughput Benchmark (Scientific Proof of Zero-GC)
java --enable-preview -jar target/benchmarks.jar RingBufferBenchmark -prof gc
```

## 📜 License
MIT License.