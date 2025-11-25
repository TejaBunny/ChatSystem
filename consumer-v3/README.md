# Consumer Application - README

## Overview

This consumer application reads messages from RabbitMQ queues and persists them to MongoDB.

**Assignment 3 Focus: Persistence ONLY**
- NO broadcasting to clients
- NO room management
- Just: Queue → Buffer → Batch → MongoDB

---

## Architecture

```
20 QueueReader threads → Shared Buffer → 10 DatabaseWriter threads → MongoDB
      (one per queue)      (10K capacity)      (configurable)
```

### Components

1. **QueueReader (20 threads)** - Read from RabbitMQ
   - One thread per queue (room.1 to room.20)
   - Push mode with manual acknowledgment
   - Prefetch configured
   - Puts messages in buffer

2. **DatabaseWriter (configurable threads)** - Write to MongoDB
   - Collects batches (size OR timeout trigger)
   - Circuit breaker check
   - Bulk insert to MongoDB
   - Retry with exponential backoff
   - Acknowledge after successful write

3. **Shared Buffer** - Decouples reading from writing
   - BlockingQueue (10,000 capacity)
   - Thread-safe
   - Allows readers and writers to operate independently

4. **Circuit Breaker** - Protects against MongoDB failures
   - Opens after threshold failures
   - Blocks requests when OPEN (fail fast)
   - Tests recovery after timeout

5. **Dead Letter Handler** - Stores failed messages
   - Separate MongoDB collection
   - For later inspection/retry

---

## Build

```bash
cd /Users/teja/Desktop/dev/cs6650/consumer
mvn clean package
```

**Output:** `target/consumer-1.0-SNAPSHOT.jar`

---

## Run

### Default Configuration:
```bash
java -jar target/consumer-1.0-SNAPSHOT.jar
```

### Custom Configuration:
```bash
java -jar target/consumer-1.0-SNAPSHOT.jar \
  --rabbitMQHost=172.31.12.56 \
  --prefetchCount=100 \
  --dbWriterThreads=10 \
  --batchSize=1000 \
  --flushInterval=500 \
  --mongoHost=localhost \
  --mongoConnectionPool=50
```

---

## Configuration Parameters

### Required Testing (Assignment 3):

**Batch Sizes to Test:**
```bash
--batchSize=100
--batchSize=500
--batchSize=1000
--batchSize=5000
```

**Flush Intervals to Test:**
```bash
--flushInterval=100
--flushInterval=500
--flushInterval=1000
```

**Test Matrix:**
```
Run 1:  --batchSize=100  --flushInterval=100
Run 2:  --batchSize=100  --flushInterval=500
Run 3:  --batchSize=100  --flushInterval=1000
Run 4:  --batchSize=500  --flushInterval=100
Run 5:  --batchSize=500  --flushInterval=500
Run 6:  --batchSize=500  --flushInterval=1000
Run 7:  --batchSize=1000 --flushInterval=100
Run 8:  --batchSize=1000 --flushInterval=500  ← Recommended default
Run 9:  --batchSize=1000 --flushInterval=1000
Run 10: --batchSize=5000 --flushInterval=100
Run 11: --batchSize=5000 --flushInterval=500
Run 12: --batchSize=5000 --flushInterval=1000

For each run, record:
- Throughput (messages/second)
- Latency (write time)
- Queue depth stability
- Resource usage
```

### Other Configurable Parameters:

```bash
--dbWriterThreads=10      # Test: 5, 10, 20, 30
--prefetchCount=100       # Test: 50, 100, 200
--mongoConnectionPool=50  # Test: 20, 50, 100
--bufferCapacity=10000    # Usually fine at default
```

---

## Prerequisites

### RabbitMQ:
```bash
# Must have exchange and bindings!
rabbitmqadmin declare exchange name=chat.exchange type=topic durable=true

for i in {1..20}; do
  rabbitmqadmin declare queue name=room.$i durable=true
  rabbitmqadmin declare binding source=chat.exchange destination=room.$i routing_key=room.$i
done
```

### MongoDB:
```bash
# Start MongoDB
sudo systemctl start mongod

# Or MongoDB Atlas (update --mongoHost)
```

---

## Expected Output

```
=================================================
  Consumer Configuration
=================================================
RabbitMQ:
  Host: 172.31.12.56
  Queue Reader Threads: 20 (one per queue)
  Prefetch Count: 100

Database Writers:
  Threads: 10
  Batch Size: 1000 messages
  Flush Interval: 500 ms

MongoDB:
  Host: localhost:27017
  Database: chat_system
  Connection Pool: 50

Buffer:
  Capacity: 10000

Circuit Breaker:
  Threshold: 5 failures
  Time Window: 2 seconds
  Open Duration: 30000 ms
=================================================

Initializing consumer components...

✓ Connected to RabbitMQ: 172.31.12.56
✓ Connected to MongoDB: mongodb://localhost:27017
  Database: chat_system
  Connection Pool: 50
Creating MongoDB indexes...
  ✓ Unique index on messageId
  ✓ Compound index on (roomId, timestamp)
  ✓ Compound index on (userId, timestamp)
  ✓ Index on userId
✓ All indexes created successfully

Circuit Breaker initialized:
  Threshold: 5 failures
  Time Window: 2 seconds
  Open Duration: 30 seconds
✓ Buffer created: capacity 10000

=================================================
  Starting Consumer Application
=================================================

Starting 20 QueueReader threads...
✓ QueueReader started: room.1 (prefetch: 100)
✓ QueueReader started: room.2 (prefetch: 100)
...
✓ QueueReader started: room.20 (prefetch: 100)
✓ All QueueReaders started

Starting 10 DatabaseWriter threads...
✓ DatabaseWriter started (Thread: pool-2-thread-1)
✓ DatabaseWriter started (Thread: pool-2-thread-2)
...
✓ DatabaseWriter started (Thread: pool-2-thread-10)
✓ All DatabaseWriters started

=================================================
  Consumer Application RUNNING
  Consuming from: 20 queues (room.1 to room.20)
  Writing to: MongoDB (chat_system)
  Press Ctrl+C to stop
=================================================

room.1: Consumed 1000 messages
room.5: Consumed 1000 messages
✓ Batch written: 1000 messages (Total: 1000, Batches: 1)
room.3: Consumed 1000 messages
✓ Batch written: 1000 messages (Total: 2000, Batches: 2)
...

--- Status Update ---
Buffer size: 234/10000
Circuit breaker: CLOSED
Messages in DB: 125000
Messages in DLQ: 0
```

---

## File Structure

```
consumer/
├── pom.xml
└── src/main/java/
    ├── MessageConsumer.java           ← Main application
    ├── ConsumerConfig.java            ← Configuration
    ├── QueueMessage.java              ← Data model
    ├── MessageWithDeliveryInfo.java   ← Delivery wrapper
    ├── RabbitMQManager.java           ← RabbitMQ connection
    ├── QueueReader.java               ← Reads from queues
    ├── DatabaseWriter.java            ← Writes to MongoDB
    ├── MongoDBManager.java            ← MongoDB operations
    ├── CircuitBreaker.java            ← Error handling
    └── DeadLetterHandler.java         ← Failed message storage
```

---

## Testing Different Configurations

### Test 1: Small batches, short timeout
```bash
java -jar target/consumer-1.0-SNAPSHOT.jar \
  --batchSize=100 \
  --flushInterval=100
```

### Test 2: Large batches, longer timeout
```bash
java -jar target/consumer-1.0-SNAPSHOT.jar \
  --batchSize=5000 \
  --flushInterval=1000
```

### Test 3: More DB writer threads
```bash
java -jar target/consumer-1.0-SNAPSHOT.jar \
  --dbWriterThreads=20 \
  --batchSize=1000
```

### Compare Results:
- Throughput (messages/second written to DB)
- Queue depth stability
- Buffer utilization
- Resource usage (CPU, memory)

---

## Monitoring

### Check MongoDB:
```bash
# Connect to MongoDB
mongo

# Use database
use chat_system

# Count messages
db.messages.count()

# Count failed messages
db.failed_writes.count()

# Check recent messages
db.messages.find().sort({timestamp: -1}).limit(10)
```

### Check RabbitMQ:
- Open Management UI: http://172.31.12.56:15672
- Go to Queues tab
- Monitor queue depths (should be decreasing!)

---

## Troubleshooting

### Problem: "Failed to connect to RabbitMQ"
**Solution:** Check RabbitMQ host IP in config or --rabbitMQHost parameter

### Problem: "Failed to connect to MongoDB"
**Solution:** 
- Check MongoDB is running: `sudo systemctl status mongod`
- Check host: `--mongoHost=localhost`

### Problem: Queue depths not decreasing
**Solution:**
- Check consumer is running
- Check no errors in logs
- Increase --dbWriterThreads

### Problem: Circuit breaker keeps opening
**Solution:**
- Check MongoDB is healthy
- Check network connectivity
- Check MongoDB logs for errors

### Problem: High memory usage
**Solution:**
- Reduce --bufferCapacity
- Reduce --batchSize
- Increase flush frequency

---

## Performance Expectations

### Throughput:
- **Target:** 8,000-10,000 messages/second
- **With optimal config:** Should match client send rate

### Queue Depth:
- **Target:** < 1000 messages per queue
- **Good profile:** Stable or slowly growing during load, then draining

### Latency:
- **Batch write:** 10-50ms per batch
- **Individual message:** Amortized to <1ms with batching

---

## Key Features

✅ **20 queue readers** (one per queue)
✅ **Configurable batching** (size + timeout)
✅ **Manual acknowledgment** (after DB write)
✅ **Batch acknowledgment** (efficient)
✅ **Circuit breaker** (MongoDB failure protection)
✅ **Connection pooling** (RabbitMQ + MongoDB)
✅ **Dead letter queue** (failed message storage)
✅ **Idempotent writes** (unique index on messageId)
✅ **Graceful shutdown** (processes remaining messages)

---

**Consumer is ready to build and test!** 🚀
