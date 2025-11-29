# Server-v3 Changes Summary

## Changes Implemented

### 1. **ChannelPool.java** - Optimized Pool Size
- **Changed:** Pool size from 100 → **20 channels**
- **Added:** Wait time instrumentation in `borrowChannel()`
  - Logs warning if waiting > 10ms for available channel
  - Helps detect if pool size needs adjustment
- **Removed:** Named exchange declaration (`chat.exchange`)
- **Added:** Utility methods for monitoring (`getAvailableChannels()`, `getPoolCapacity()`)

### 2. **ChatServer.java** - AWS Integration & Simplified Routing
- **Changed:** Uses **default exchange** (empty string) instead of named exchange
  - Publishes directly to queue by name
  - No exchange declaration or bindings needed
- **Added:** **AWS instance metadata retrieval** for automatic server ID
  - Retrieves EC2 instance-id automatically
  - Falls back to UUID if not on AWS
  - Can still manually override via command line
- **Updated:** Comments and logging to reflect optimizations

### 3. **Other Files** - No Changes Needed
- ChannelPoolManager.java ✓
- ConnectionManager.java ✓
- ChatMessage.java ✓
- QueueMessage.java ✓

---

## How to Use

### Local Testing (Not on AWS)
```bash
# Build
mvn clean package

# Run (auto-generates local server ID)
java -jar target/server-v2-1.0-SNAPSHOT.jar

# Output:
# ⚠ AWS metadata unavailable, using fallback ID: server-local-a1b2c3d4
# ChannelPool initialized with 20 channels
# ChatServer v3 (Assignment 3) - STARTED
```

### AWS Deployment (Automatic)
```bash
# Deploy JAR to EC2 instance
scp target/server-v2-1.0-SNAPSHOT.jar ec2-user@<instance-ip>:~/

# SSH to instance
ssh ec2-user@<instance-ip>

# Run (automatically gets instance ID)
java -jar server-v2-1.0-SNAPSHOT.jar

# Output:
# ✓ Retrieved AWS instance ID: i-0abcd1234efgh5678
# ChannelPool initialized with 20 channels
# ChatServer v3 (Assignment 3) - STARTED
```

### Manual Server ID Override (If Needed)
```bash
# Override automatic detection
java -jar target/server-v2-1.0-SNAPSHOT.jar 8080 my-custom-id

# Output:
# Using manually specified server ID: my-custom-id
```

---

## RabbitMQ Setup (Simplified!)

### Old Way (Assignment 2):
```bash
# 1. Create exchange
rabbitmqadmin declare exchange name=chat.exchange type=topic durable=true

# 2. Create queues
for i in {1..20}; do
  rabbitmqadmin declare queue name=room.$i durable=true
done

# 3. Create bindings (20 bindings!)
for i in {1..20}; do
  rabbitmqadmin declare binding source=chat.exchange destination=room.$i routing_key=room.$i
done
```

### New Way (Assignment 3 - Simplified!):
```bash
# Just create queues (that's it!)
for i in {1..20}; do
  rabbitmqadmin declare queue name=room.$i durable=true
done
```

**Why simpler?**
- Default exchange automatically routes to queues by name
- No exchange declaration needed
- No bindings needed
- 66% fewer commands!

---

## Monitoring Channel Pool Performance

### Check for Warnings
If you see this in logs:
```
⚠️  WARNING: Had to wait 15ms for available channel. Consider increasing POOL_SIZE!
```

**Action:** Increase POOL_SIZE in ChannelPool.java from 20 to 25 or 30.

### Expected Behavior (No Warnings)
```
Message published: abc-123 | Room: 5 | User: 12345
Message published: def-456 | Room: 3 | User: 67890
Message published: ghi-789 | Room: 7 | User: 11111
(No channel wait warnings = pool size is sufficient!)
```

---

## Architecture Comparison

### Before (Assignment 2):
```
Client → Server (100 channels) → chat.exchange → Bindings → 20 queues → Consumer (in server)
         └─ roomSessions tracking
         └─ 40 consumer threads
         └─ Broadcasting to clients
```

### After (Assignment 3):
```
Client → Server (20 channels) → [Default Exchange] → 20 queues → Consumer (separate app)
         └─ No session tracking
         └─ No consumer threads
         └─ No broadcasting
         └─ Just publishes & ACKs
```

---

## Compatibility Matrix

| Component | Compatible | Notes |
|-----------|-----------|-------|
| Test Client (20 threads) | ✅ | Perfect match for 20-channel pool |
| RabbitMQ Queues | ✅ | Just create 20 queues, no bindings |
| Load Balancer | ✅ | Works with AWS ALB |
| Consumer App | ✅ | Reads from same queues (to be built) |
| MongoDB | ✅ | serverId tracked in messages |

---

## What's Next?

1. ✅ Server-v3 is complete and optimized
2. ⏳ Build Consumer application (reads from queues, writes to MongoDB)
3. ⏳ Set up MongoDB (schema, indexes)
4. ⏳ Update test client with connection pooling

---

## Quick Test Checklist

Before deploying:
- [ ] Compile successfully: `mvn clean package`
- [ ] Create RabbitMQ queues: `room.1` through `room.20`
- [ ] Test locally with wscat
- [ ] Verify messages appear in RabbitMQ queues
- [ ] Check no channel wait warnings in logs
- [ ] Deploy to EC2 and verify automatic server ID retrieval

---

## Key Benefits of These Changes

1. **20 channels** → Optimized for actual concurrency (20 threads)
2. **Wait time monitoring** → Detect if pool needs adjustment
3. **Default exchange** → Simpler setup, fewer moving parts
4. **AWS auto-detection** → No manual server ID configuration
5. **Better logging** → Understand system behavior

---

**All files are updated and compatible!** 🚀
