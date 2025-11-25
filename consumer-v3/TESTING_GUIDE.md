# Assignment 3 - Performance Testing & Metrics Collection Guide

## Overview
This guide explains how to monitor and collect all metrics required for Assignment 3 performance testing.

---

## 📋 **What Metrics Do We Need to Collect?**

### Required by Assignment 3:
1. **Database Write Throughput** - Messages persisted per second
2. **Write Latency** - p50, p95, p99 percentiles for batch writes
3. **Queue Depth Over Time** - Stability graph
4. **Buffer Utilization** - Consumer buffer size
5. **Resource Usage** - CPU, Memory for consumer & MongoDB
6. **System Stability** - Circuit breaker state, error rates

---

## 🚀 **Step-by-Step Testing Workflow**

### **Before Testing: Setup**

1. **Start MongoDB with Profiling**
```bash
mongosh

use chat_system

# Enable profiling to capture write latencies
db.setProfilingLevel(2)  # Profile ALL operations during test

# Verify
db.getProfilingLevel()
```

2. **Start All Monitoring Scripts** (In separate terminal windows)

**Terminal 1: Overall Monitoring**
```bash
cd /home/ec2-user/consumer
chmod +x monitor_all.sh
./monitor_all.sh
```

**Terminal 2: MongoDB Detailed**
```bash
chmod +x monitor_mongodb.sh
./monitor_mongodb.sh
```

**Terminal 3: RabbitMQ Queues**
```bash
chmod +x monitor_rabbitmq.sh
./monitor_rabbitmq.sh
```

**Terminal 4: System Resources**
```bash
# Monitor CPU, Memory in real-time
top -b -d 5 > system_resources.log
```

---

### **During Testing: Run Your Test**

**Terminal 5: Start Consumer**
```bash
cd /home/ec2-user/consumer

# Example: Test with batch size 1000, flush interval 500ms
java -jar target/consumer-1.0-SNAPSHOT.jar \
  --batchSize=1000 \
  --flushInterval=500 \
  --dbWriterThreads=10 \
  > consumer.log 2>&1
```

**Terminal 6: Run Client Load Test**
```bash
cd /home/ec2-user/client-part2

# Run 500K message test
java -jar target/client-part2-1.0-SNAPSHOT.jar
```

---

### **After Testing: Collect Results**

1. **Stop Monitoring Scripts** (Ctrl+C in each terminal)

2. **Collect MongoDB Statistics**
```bash
mongosh

use chat_system

# Total messages written
db.messages.countDocuments()

# Write latency statistics from profiler
db.system.profile.aggregate([
  { $match: { op: "insert", ns: "chat_system.messages" } },
  { $group: {
      _id: null,
      count: { $sum: 1 },
      avgLatency: { $avg: "$millis" },
      minLatency: { $min: "$millis" },
      maxLatency: { $max: "$millis" },
      p95: { $percentile: { input: "$millis", p: [0.95], method: 'approximate' } },
      p99: { $percentile: { input: "$millis", p: [0.99], method: 'approximate' } }
    }
  }
])

# Disable profiling after test
db.setProfilingLevel(0)
```

3. **Check Consumer Final Stats**
```bash
tail -50 consumer.log
```

Look for:
```
=================================================
  Final Statistics
=================================================
Messages persisted: 500000
Messages in DLQ: 0
=================================================
```

4. **Verify Queue Depths**
```bash
# All queues should be empty after test completes
rabbitmqadmin list queues name messages
```

---

## 📊 **Data Files Generated**

After each test run, you'll have:

```
performance_metrics_YYYYMMDD_HHMMSS.csv    # Overall metrics
mongodb_metrics_YYYYMMDD_HHMMSS.csv        # MongoDB detailed
rabbitmq_metrics_YYYYMMDD_HHMMSS.csv       # Queue depths
system_resources.log                        # CPU/Memory
consumer.log                                # Consumer output
```

---

## 📈 **Analyzing the Results**

### **1. Calculate Write Throughput**

From `mongodb_metrics_*.csv`:
```bash
# Average throughput
awk -F',' 'NR>1 {sum+=$3; count++} END {print "Average Throughput:", sum/count, "msg/sec"}' mongodb_metrics_*.csv

# Peak throughput
awk -F',' 'NR>1 {if ($3>max) max=$3} END {print "Peak Throughput:", max, "msg/sec"}' mongodb_metrics_*.csv
```

### **2. Analyze Queue Stability**

From `rabbitmq_metrics_*.csv`:
```bash
# Plot queue depth over time (use in Excel/Python)
cut -d',' -f1,22 rabbitmq_metrics_*.csv > queue_depth_timeline.csv

# Check if queue depth stayed stable
awk -F',' 'NR>1 {print $22}' rabbitmq_metrics_*.csv | sort -n
```

**Good Profile:**
- Queue depth grows initially
- Stabilizes or grows slowly
- Drains to near-zero after client stops

**Bad Profile:**
- Queue depth explodes (> 10,000 per queue)
- Never stabilizes
- Doesn't drain after test

### **3. Calculate Latency Percentiles**

From MongoDB profiler:
```bash
mongosh --quiet --eval "
  var latencies = db.system.profile.find(
    {op: 'insert', ns: 'chat_system.messages'}
  ).map(function(doc) { return doc.millis; }).sort();
  
  var count = latencies.length;
  var p50 = latencies[Math.floor(count * 0.50)];
  var p95 = latencies[Math.floor(count * 0.95)];
  var p99 = latencies[Math.floor(count * 0.99)];
  
  print('p50 (median):', p50, 'ms');
  print('p95:', p95, 'ms');
  print('p99:', p99, 'ms');
" chat_system
```

### **4. Check System Stability**

```bash
# Circuit breaker should always be CLOSED
grep "Circuit breaker:" consumer.log | sort -u

# Should show only: Circuit breaker: CLOSED
```

---

## 🧪 **Test Matrix for Assignment 3**

Run tests with different configurations:

### **Test 1: Baseline** (Batch=1000, Flush=500ms)
```bash
java -jar consumer.jar --batchSize=1000 --flushInterval=500
```

### **Test 2: Small Batches** (Batch=100, Flush=100ms)
```bash
java -jar consumer.jar --batchSize=100 --flushInterval=100
```

### **Test 3: Large Batches** (Batch=5000, Flush=1000ms)
```bash
java -jar consumer.jar --batchSize=5000 --flushInterval=1000
```

### **Test 4: More Writers** (Batch=1000, Writers=20)
```bash
java -jar consumer.jar --batchSize=1000 --flushInterval=500 --dbWriterThreads=20
```

**For each test:**
1. Start monitoring scripts
2. Run test
3. Collect metrics
4. Compare results

---

## 📝 **Creating Performance Graphs**

### **Import CSVs into Excel/Google Sheets**

**Graph 1: Queue Depth Over Time**
- X-axis: Timestamp
- Y-axis: Total Queue Depth
- Type: Line chart

**Graph 2: Database Throughput Over Time**
- X-axis: Timestamp  
- Y-axis: Inserts Per Second
- Type: Line chart

**Graph 3: Write Latency Distribution**
- Show: p50, p95, p99 as bar chart
- Compare across different configurations

**Graph 4: Buffer Utilization**
- X-axis: Timestamp
- Y-axis: Buffer Size / Capacity (%)
- Type: Line chart

---

## 🎯 **Success Criteria**

Your system is performing well if:

✅ **Throughput:** 8,000-10,000 messages/second sustained
✅ **Queue Depth:** Stays < 1,000 per queue during steady state
✅ **Buffer:** Stays < 50% capacity
✅ **Latency:** p99 < 100ms for batch writes
✅ **Circuit Breaker:** Always CLOSED
✅ **Dead Letters:** 0 messages in DLQ
✅ **Stability:** System runs for full test duration without crashes

---

## 🔍 **Troubleshooting During Tests**

### **Problem: Queue depths growing rapidly**
- **Cause:** Consumer too slow
- **Fix:** Increase `--dbWriterThreads` or `--batchSize`

### **Problem: Low throughput despite low queue depth**
- **Cause:** Client sending slowly OR network issues
- **Fix:** Check client logs, network latency

### **Problem: High latency**
- **Cause:** MongoDB overloaded, large batch size
- **Fix:** Reduce batch size, increase connection pool

### **Problem: Circuit breaker keeps opening**
- **Cause:** MongoDB connection issues
- **Fix:** Check MongoDB status, increase timeout thresholds

---

## 📧 **What to Include in Assignment Report**

1. **Test Configuration Table**
   - Batch sizes tested
   - Flush intervals tested
   - Thread counts tested

2. **Performance Results**
   - Throughput for each configuration
   - Latency percentiles
   - Resource utilization

3. **Graphs**
   - Queue depth over time (stability)
   - Throughput over time
   - Latency comparison

4. **Analysis**
   - Which configuration performed best?
   - What were the bottlenecks?
   - Trade-offs observed

5. **System Architecture**
   - Diagram showing components
   - Configuration chosen and why

---

## 🚀 **Quick Reference Commands**

```bash
# Monitor everything
./monitor_all.sh

# Check MongoDB status
mongosh --eval "db.serverStatus().opcounters" chat_system

# Check queue depths
rabbitmqadmin list queues name messages

# Check consumer
tail -f consumer.log | grep "Status Update"

# Check message count
mongosh --eval "db.messages.countDocuments()" chat_system

# System resources
htop -p $(pgrep -f java)
```

---

**Ready to run your performance tests!** 🎯
