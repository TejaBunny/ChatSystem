# 📊 Complete Monitoring & Metrics Collection Guide

## Quick Start

### 1️⃣ Make Scripts Executable
```bash
cd /home/ec2-user/consumer
chmod +x *.sh
```

### 2️⃣ Check Current Status
```bash
./status_check.sh
```

### 3️⃣ Start Monitoring (Before Running Tests)
```bash
# Terminal 1: Overall monitoring
./monitor_all.sh

# Terminal 2: MongoDB detailed
./monitor_mongodb.sh

# Terminal 3: RabbitMQ queues
./monitor_rabbitmq.sh
```

### 4️⃣ Run Your Test
```bash
# Terminal 4: Consumer
java -jar consumer-1.0-SNAPSHOT.jar --batchSize=1000 --flushInterval=500

# Terminal 5: Client
cd /home/ec2-user/client-part2
java -jar client-part2-1.0-SNAPSHOT.jar
```

### 5️⃣ Analyze Results After Test
```bash
# Stop monitoring (Ctrl+C in each terminal)

# Analyze the collected data
./analyze_results.sh mongodb_metrics_*.csv
```

---

## 📁 Files Created

| Script | Purpose |
|--------|---------|
| `monitor_all.sh` | Overall system monitoring (buffer, DB, queues) |
| `monitor_mongodb.sh` | Detailed MongoDB metrics (throughput, latency) |
| `monitor_rabbitmq.sh` | Queue depth tracking for all 20 rooms |
| `status_check.sh` | Quick snapshot of system status |
| `analyze_results.sh` | Analyzes collected CSV files |

---

## 📊 Metrics Collected

### Consumer Metrics (Built-in)
- ✅ Buffer utilization (printed every 5 seconds)
- ✅ Circuit breaker state
- ✅ Messages in database
- ✅ Messages in dead letter queue

### MongoDB Metrics
- ✅ Write throughput (messages/second)
- ✅ Write latency (p50, p95, p99)
- ✅ Total inserts
- ✅ Active connections
- ✅ Memory usage

### RabbitMQ Metrics
- ✅ Queue depth per room
- ✅ Total queue depth
- ✅ Message rates (in/out)

### System Metrics
- ✅ CPU usage
- ✅ Memory usage
- ✅ Network I/O

---

## 🎯 Real-Time Monitoring Commands

### MongoDB
```bash
# Watch insert operations in real-time
watch -n 2 'mongosh --quiet --eval "db.serverStatus().opcounters" chat_system'

# Check current message count
mongosh --quiet --eval "db.messages.countDocuments()" chat_system

# View recent writes with latency
mongosh --eval "db.system.profile.find({op:'insert'}).sort({ts:-1}).limit(5).pretty()" chat_system
```

### RabbitMQ
```bash
# Watch queue depths
watch -n 2 'rabbitmqadmin list queues name messages'

# Total messages in all queues
rabbitmqadmin list queues name messages | awk '{sum+=$2} END {print "Total:", sum}'

# Check specific queue
rabbitmqadmin show queue name=room.1
```

### Consumer
```bash
# Follow consumer logs
tail -f consumer.log

# Watch status updates
tail -f consumer.log | grep "Status Update"

# Check for errors
tail -f consumer.log | grep "ERROR\|❌"
```

### System Resources
```bash
# Monitor Java processes
htop -p $(pgrep -d',' -f java)

# Or use top
top -p $(pgrep -d',' -f java)

# Memory usage
free -m
```

---

## 📈 Understanding the Output

### Good Performance Indicators

**Consumer:**
```
Buffer size: 234/10000           ← Under 50% capacity ✅
Circuit breaker: CLOSED          ← No failures ✅
Messages in DB: 125000           ← Growing steadily ✅
Messages in DLQ: 0               ← No failed messages ✅
```

**MongoDB:**
```
Throughput: 8500 msg/sec         ← High sustained rate ✅
Latency: 15ms average            ← Low latency ✅
p99: 45ms                        ← Good tail latency ✅
```

**RabbitMQ:**
```
Total Queue Depth: 450           ← Stable, not exploding ✅
room.1: 23 messages              ← Low per-queue ✅
```

### Warning Signs

**Consumer:**
```
Buffer size: 9500/10000          ⚠️ Buffer almost full - increase writers
Circuit breaker: OPEN            ❌ MongoDB connection issues
Messages in DLQ: 150             ❌ Write failures occurring
```

**MongoDB:**
```
Throughput: 500 msg/sec          ⚠️ Too slow - bottleneck!
Latency: 250ms average           ⚠️ High latency
```

**RabbitMQ:**
```
Total Queue Depth: 45000         ❌ Queues exploding - consumer too slow
room.1: 2300 messages            ❌ Not draining fast enough
```

---

## 🧪 Assignment 3 Test Matrix

Run these configurations and compare results:

| Test | Batch Size | Flush Interval | Writers | Expected Throughput |
|------|------------|----------------|---------|---------------------|
| 1    | 100        | 100ms          | 10      | ~3,000 msg/sec      |
| 2    | 500        | 100ms          | 10      | ~5,000 msg/sec      |
| 3    | 1000       | 500ms          | 10      | ~8,000 msg/sec      |
| 4    | 5000       | 1000ms         | 10      | ~9,000 msg/sec      |
| 5    | 1000       | 500ms          | 20      | ~10,000 msg/sec     |

**For each test:**
1. Start monitoring scripts
2. Run consumer with configuration
3. Run client (500K messages)
4. Let it complete + queues drain
5. Stop monitoring
6. Analyze results with `analyze_results.sh`

---

## 📝 Creating Your Assignment Report

### Data to Include

1. **Configuration Table**
   - List all test configurations
   - Batch size, flush interval, thread counts

2. **Performance Results Table**
   | Config | Throughput | p50 Latency | p99 Latency | Duration |
   |--------|------------|-------------|-------------|----------|
   | Test 1 | 3,200 msg/s| 12ms        | 45ms        | 156s     |
   | Test 2 | 5,400 msg/s| 18ms        | 52ms        | 92s      |
   | ...    | ...        | ...         | ...         | ...      |

3. **Graphs** (Create in Excel/Google Sheets from CSV files)
   - Queue depth over time (line chart)
   - Throughput over time (line chart)
   - Latency comparison (bar chart)
   - Buffer utilization (line chart)

4. **Analysis**
   - Which configuration performed best?
   - What were the bottlenecks?
   - Trade-offs observed (latency vs throughput)
   - Why did you choose your final configuration?

### Importing CSVs to Excel/Sheets

1. Open Excel/Google Sheets
2. File → Import → Select CSV file
3. Create pivot tables and charts
4. Export graphs as images for report

---

## 🔧 Troubleshooting

### Problem: Scripts not running
```bash
# Make executable
chmod +x *.sh

# Run with bash explicitly
bash monitor_all.sh
```

### Problem: "mongosh: command not found"
```bash
# Use mongo instead
mongo --eval "db.serverStatus()" chat_system

# Or install mongosh
sudo yum install -y mongodb-mongosh
```

### Problem: "rabbitmqadmin: command not found"
```bash
# Download rabbitmqadmin
cd /usr/local/bin
sudo wget http://localhost:15672/cli/rabbitmqadmin
sudo chmod +x rabbitmqadmin

# Test
rabbitmqadmin list queues
```

### Problem: No data in CSV files
```bash
# Check if scripts are running
ps aux | grep monitor

# Check file permissions
ls -la *.csv

# Manually run a command to test
mongosh --quiet --eval "db.serverStatus().opcounters" chat_system
```

---

## 🎓 Understanding the Architecture

```
┌─────────────┐
│   Client    │ ─── Sends 500K messages
└─────┬───────┘
      │
      ↓
┌─────────────┐
│   Server    │ ─── Publishes to RabbitMQ
└─────┬───────┘
      │
      ↓
┌─────────────┐
│  RabbitMQ   │ ─── 20 queues (room.1 - room.20)
└─────┬───────┘
      │
      ↓
┌─────────────┐
│  Consumer   │ ─── 20 readers → Buffer → 10 writers
│  (This!)    │     - QueueReaders pull from queues
│             │     - Buffer decouples reading/writing
│             │     - DatabaseWriters batch & persist
└─────┬───────┘
      │
      ↓
┌─────────────┐
│  MongoDB    │ ─── Persistent storage
└─────────────┘
```

**What We're Measuring:**
- How fast can we **read** from queues? (QueueReaders)
- How fast can we **write** to database? (DatabaseWriters)
- Does the system stay **stable** under load? (Buffer, Circuit Breaker)

---

## ✅ Success Checklist

Before submitting Assignment 3:

- [ ] Tested at least 5 different configurations
- [ ] Collected metrics for each test run
- [ ] Analyzed CSV files with `analyze_results.sh`
- [ ] Created graphs showing queue depth stability
- [ ] Calculated throughput, latency percentiles
- [ ] Identified optimal configuration
- [ ] Documented bottlenecks and trade-offs
- [ ] All queues drained to zero after each test
- [ ] No messages in dead letter queue
- [ ] Circuit breaker stayed CLOSED throughout

---

## 🚀 Ready to Test!

You now have everything needed to:
1. ✅ Monitor your system in real-time
2. ✅ Collect comprehensive performance data
3. ✅ Analyze results for optimal configuration
4. ✅ Create professional performance reports

**Start with:** `./status_check.sh` to verify everything is ready!

For detailed testing workflow, see: `TESTING_GUIDE.md`
