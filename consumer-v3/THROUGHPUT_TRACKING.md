# Throughput Calculation - Complete Explanation

## 🎯 How Write Throughput is Calculated

### **Formula:**

```
Throughput (msg/s) = Total Messages Written / Elapsed Time (seconds)

Overall Throughput = currentMessageCount × 1000 / elapsedMilliseconds
Interval Throughput = intervalMessages × 1000 / intervalMilliseconds
```

---

## 📊 Where Throughput is Printed

### **1. Every 10,000 Messages (DatabaseWriter):**

**Example Output:**
```
✓ 10,000 messages | Throughput: 9,523 msg/s (overall) | 10,000 msg/s (interval)
✓ 20,000 messages | Throughput: 9,756 msg/s (overall) | 9,800 msg/s (interval)
✓ 30,000 messages | Throughput: 9,900 msg/s (overall) | 10,200 msg/s (interval)
...
✓ 500,000 messages | Throughput: 10,204 msg/s (overall) | 10,500 msg/s (interval)
```

**Metrics shown:**
- **Total messages:** Cumulative count
- **Overall throughput:** Average since consumer started
- **Interval throughput:** Rate since last report (last 10K messages)

**Where in code:**
```java
// DatabaseWriter.java, line ~88:
if (written % 10000 < batchSize) {
    throughputTracker.report(written);
    // Prints throughput
}
```

---

### **2. Final Shutdown Statistics:**

**Example Output:**
```
=================================================
  Final Statistics
=================================================
Total: 500,000 messages in 48.52 seconds | Throughput: 10,304 msg/s
Messages in DB: 500,000
Messages in DLQ: 0
=================================================
```

**Where in code:**
```java
// MessageConsumer.java, shutdown() method:
System.out.println(throughputTracker.getFinalStats(totalMessages));
```

---

### **3. Status Updates (Every 60 Seconds):**

**Example Output:**
```
[STATUS] Buffer: 234/10000 | CB: CLOSED | DB: 125,000 | DLQ: 0
[STATUS] Buffer: 456/10000 | CB: CLOSED | DB: 250,000 | DLQ: 0
```

**Note:** Status does NOT show throughput (just snapshot)
- Shows buffer state, circuit breaker, counts
- Throughput shown separately in milestone logs

---

## 🧮 Calculation Breakdown

### **ThroughputTracker Internals:**

```java
Start time: 10:00:00.000 (recorded when consumer starts)

Report at 10:00:10.500 (after 10,000 messages):
  currentCount = 10,000
  elapsedTotal = 10.5 seconds
  intervalElapsed = 10.5 seconds (first report)
  intervalMessages = 10,000
  
  overallThroughput = 10,000 × 1000 / 10,500 = 9,523 msg/s
  intervalThroughput = 10,000 × 1000 / 10,500 = 9,523 msg/s
  
  Output: "✓ 10,000 messages | Throughput: 9,523 msg/s (overall) | 9,523 msg/s (interval)"

Report at 10:00:21.000 (after 20,000 messages):
  currentCount = 20,000
  elapsedTotal = 21.0 seconds
  lastCount = 10,000 (from previous report)
  intervalElapsed = 10.5 seconds (since last report)
  intervalMessages = 20,000 - 10,000 = 10,000
  
  overallThroughput = 20,000 × 1000 / 21,000 = 9,523 msg/s
  intervalThroughput = 10,000 × 1000 / 10,500 = 9,523 msg/s
  
  Output: "✓ 20,000 messages | Throughput: 9,523 msg/s (overall) | 9,523 msg/s (interval)"
```

---

## 📈 What Throughput Numbers Mean

### **Overall Throughput:**
```
= Total messages / Total time since start
= Average throughput across entire run
= Smooths out bursts and variations
```

**Use for:**
- Final performance number in report
- Comparing different configurations
- Overall system performance

**Example:**
```
500,000 messages in 50 seconds = 10,000 msg/s overall
```

---

### **Interval Throughput:**
```
= Messages since last report / Time since last report
= Current instantaneous rate
= Shows real-time performance
```

**Use for:**
- Detecting performance degradation
- Monitoring system health
- Identifying bottlenecks

**Example:**
```
First 10K: 10,500 msg/s (fast!)
Next 10K: 9,800 msg/s (slowing down?)
Next 10K: 10,200 msg/s (recovered)
```

**If interval throughput drops significantly:** Indicates problem (MongoDB slow, circuit breaker, etc.)

---

## 🎯 Expected Output During Test

### **Complete Log Example:**

```
=== Consumer Config (Option A) ===
Batch: 1000, Flush: 500ms, Threads: 20
==================================

✓ MongoDB: mongodb://172.31.x.x:27017 (pool=30)
✓ 5 indexes created (1 unique + 4 analytics)
CircuitBreaker: threshold=5

=== Starting Consumer (Option A) ===
✓ Throughput tracking started
✓ 20 readers + 20 writers started
✓ Consumer running (Persistence only - No broadcasting)
====================================

[Client starts sending messages...]

✓ 10,000 messages | Throughput: 9,524 msg/s (overall) | 10,000 msg/s (interval)
✓ 20,000 messages | Throughput: 9,756 msg/s (overall) | 9,800 msg/s (interval)
✓ 30,000 messages | Throughput: 9,900 msg/s (overall) | 10,200 msg/s (interval)
✓ 40,000 messages | Throughput: 10,050 msg/s (overall) | 10,500 msg/s (interval)
✓ 50,000 messages | Throughput: 10,101 msg/s (overall) | 10,400 msg/s (interval)

[STATUS] Buffer: 1234/10000 | CB: CLOSED | DB: 52,500 | DLQ: 0

✓ 60,000 messages | Throughput: 10,152 msg/s (overall) | 10,300 msg/s (interval)
✓ 70,000 messages | Throughput: 10,189 msg/s (overall) | 10,450 msg/s (interval)
...
✓ 500,000 messages | Throughput: 10,204 msg/s (overall) | 10,500 msg/s (interval)

[Ctrl+C pressed]

=== Shutting down ===
Processing buffer (234 msgs)...

=================================================
  Final Statistics
=================================================
Total: 500,000 messages in 48.98 seconds | Throughput: 10,208 msg/s
Messages in DB: 500,000
Messages in DLQ: 0
=================================================
```

---

## 📊 How to Interpret Results

### **Good Performance:**
```
Overall throughput: 10,000+ msg/s
Interval throughput: Consistent (±5% variation)
No circuit breaker activations
DLQ: 0 messages

Indicates: Stable, high-performance system ✓
```

---

### **Performance Issues:**

**Degrading Performance:**
```
✓ 10,000 messages | Overall: 10,500 msg/s | Interval: 10,500 msg/s
✓ 20,000 messages | Overall: 10,200 msg/s | Interval: 9,900 msg/s  ← Dropping
✓ 30,000 messages | Overall: 9,800 msg/s | Interval: 9,000 msg/s   ← Still dropping

Interval throughput decreasing over time
Indicates: System degrading (memory leak? MongoDB slow?)
```

**Bursty Performance:**
```
✓ 10,000 messages | Interval: 11,000 msg/s  ← Fast
✓ 20,000 messages | Interval: 5,000 msg/s   ← Slow
✓ 30,000 messages | Interval: 12,000 msg/s  ← Fast again

High variation in interval throughput
Indicates: Unstable (network issues? MongoDB checkpoints?)
```

---

## 🔍 Throughput Breakdown by Component

### **Where Throughput Happens:**

**1. Client Send Throughput:**
```
Client sends: ~40,000 msg/s
Measured: In client logs (not consumer)
Location: Client-part2 PerformanceReport
```

**2. RabbitMQ Ingestion:**
```
Servers publish: ~40,000 msg/s
Not measured explicitly
Can see: RabbitMQ Management UI (publish rate)
```

**3. Consumer Read Throughput (QueueReaders):**
```
20 QueueReaders consume: ~40,000 msg/s total
Not measured explicitly
Fast (not bottleneck)
```

**4. Consumer Write Throughput (DatabaseWriters):** ⭐
```
20 DatabaseWriters write: ~10,000-20,000 msg/s
MEASURED by ThroughputTracker ✓
Printed every 10,000 messages ✓
THIS is the critical metric!
```

**5. MongoDB Actual Throughput:**
```
MongoDB operations: ~10,000-20,000 msg/s
Can measure: via mongosh or MongoDB monitoring
Not tracked in consumer code
```

---

## 🎯 What Gets Reported

### **Consumer Logs Show:**

✅ **Write throughput** (messages/second to MongoDB)
- Overall average
- Interval rate
- Every 10,000 messages
- Final summary

❌ **NOT shown:**
- Client send rate (in client logs)
- RabbitMQ routing rate
- QueueReader consumption rate
- Individual batch latency

**Focus: MongoDB write throughput (the bottleneck!)**

---

## 📝 For Your Performance Report

### **Use These Numbers:**

```markdown
## Consumer Write Throughput

Configuration:
- DatabaseWriter threads: 20
- Batch size: 1000 messages
- Flush interval: 500ms
- MongoDB connection pool: 30

Results:
- Overall throughput: 10,208 msg/s
- Peak interval: 10,500 msg/s
- Minimum interval: 9,800 msg/s
- Variation: ±3.5% (stable)
- Total duration: 48.98 seconds
- Success rate: 100% (500,000 / 500,000)

[Include screenshot of consumer logs showing throughput]
```

---

## 🎉 Summary

**Throughput is now calculated and displayed:**

✅ **Where:** Consumer DatabaseWriter logs
✅ **When:** Every 10,000 messages + final shutdown
✅ **What:** Overall and interval msg/s rates
✅ **How:** ThroughputTracker class
✅ **Why:** Monitor MongoDB write performance

**Example output:**
```
✓ 10,000 messages | Throughput: 9,524 msg/s (overall) | 10,000 msg/s (interval)
```

**Final output:**
```
Total: 500,000 messages in 48.98 seconds | Throughput: 10,208 msg/s
```

**Perfect for documenting performance results!** 📊💪
