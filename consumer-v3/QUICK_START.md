# Consumer Quick Start Guide

## 1. Build
```bash
cd consumer
mvn clean package
```

## 2. Setup MongoDB
```bash
# Start MongoDB locally
sudo systemctl start mongod

# Or use MongoDB Atlas (update --mongoHost)
```

## 3. Verify RabbitMQ Setup
```bash
# SSH to RabbitMQ server
ssh -i key.pem ec2-user@172.31.12.56

# Check bindings exist
sudo rabbitmqadmin list bindings | grep chat.exchange | wc -l
# Should output: 20
```

## 4. Run Consumer
```bash
# Default configuration
java -jar target/consumer-1.0-SNAPSHOT.jar

# Or custom configuration
java -jar target/consumer-1.0-SNAPSHOT.jar \
  --batchSize=1000 \
  --flushInterval=500 \
  --dbWriterThreads=10
```

## 5. Verify Working
```bash
# In another terminal, check MongoDB
mongo
> use chat_system
> db.messages.count()  # Should be increasing!

# Check RabbitMQ Management UI
# http://172.31.12.56:15672
# Queue depths should be decreasing
```

## 6. Run Client Test
```bash
# In another terminal
cd client-part2
java -jar target/client-part2-1.0-SNAPSHOT.jar

# Watch consumer process messages!
```

## 7. Monitor Progress
```
Consumer will print:
✓ Batch written: 1000 messages (Total: 1000, Batches: 1)
✓ Batch written: 1000 messages (Total: 2000, Batches: 2)
...

--- Status Update ---
Buffer size: 234/10000
Circuit breaker: CLOSED
Messages in DB: 125000
Messages in DLQ: 0
```

---

## Testing Different Configurations

### Test 1: Baseline (default)
```bash
java -jar target/consumer-1.0-SNAPSHOT.jar
# Uses: batch=1000, flush=500ms, threads=10
```

### Test 2: Small batches
```bash
java -jar target/consumer-1.0-SNAPSHOT.jar \
  --batchSize=100 \
  --flushInterval=100
```

### Test 3: Large batches
```bash
java -jar target/consumer-1.0-SNAPSHOT.jar \
  --batchSize=5000 \
  --flushInterval=1000
```

### Test 4: More DB threads
```bash
java -jar target/consumer-1.0-SNAPSHOT.jar \
  --dbWriterThreads=20
```

**For each test, measure:**
- Messages/second throughput
- Queue depth profile
- Resource usage
- Write latency

---

## Troubleshooting

### Can't connect to RabbitMQ
```bash
# Check host
--rabbitMQHost=172.31.12.56

# Check RabbitMQ is running
ssh ec2-user@172.31.12.56
sudo systemctl status rabbitmq-server
```

### Can't connect to MongoDB
```bash
# Check MongoDB is running
sudo systemctl status mongod

# Or update host
--mongoHost=your-mongodb-host
```

### Messages not being consumed
```bash
# Check queues have messages
rabbitmqadmin list queues name messages

# Check bindings exist
rabbitmqadmin list bindings | grep chat.exchange
```

---

## Success Criteria

✅ All 20 QueueReaders started
✅ All DatabaseWriters started
✅ Buffer receiving messages
✅ Batches being written to MongoDB
✅ Queue depths decreasing
✅ No circuit breaker openings (unless MongoDB fails)
✅ Messages in DB increasing
✅ DLQ count stays at 0 (no failures)

---

**Ready to process 500K messages!** 🚀
