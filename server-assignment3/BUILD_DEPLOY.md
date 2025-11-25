# Build & Deploy Guide - Auto-Scaling Server

## 🎯 What Changed

✅ **ChannelPool.java** - Now auto-scales from 5-25 channels based on load
✅ **ChatServer.java** - Tracks connections and monitors channel pool
✅ **Added monitoring** - Real-time statistics every 30-60 seconds
✅ **Observable scaling** - Clear logs when pool grows/shrinks

---

## 🔧 Build

### **Step 1: Clean & Rebuild**
```bash
cd /Users/teja/Desktop/dev/cs6650/server-v3

# Clean old build
mvn clean

# Build with new auto-scaling code
mvn package

# Verify JAR created
ls -lh target/server-v2-1.0-SNAPSHOT.jar
```

**Expected output:**
```
-rw-r--r-- 1 user user 15M Nov 21 14:30 target/server-v2-1.0-SNAPSHOT.jar
```

---

## 🚀 Deploy to EC2

### **Step 2: Upload to Server**

**If deploying to 1 server:**
```bash
scp -i cs6650-assignment1-key.pem \
    target/server-v2-1.0-SNAPSHOT.jar \
    ec2-user@YOUR_SERVER_IP:~/chatserver.jar
```

**If deploying to 4 servers behind ALB:**
```bash
# Server 1
scp -i cs6650-assignment1-key.pem target/server-v2-1.0-SNAPSHOT.jar \
    ec2-user@SERVER1_IP:~/chatserver.jar

# Server 2
scp -i cs6650-assignment1-key.pem target/server-v2-1.0-SNAPSHOT.jar \
    ec2-user@SERVER2_IP:~/chatserver.jar

# Server 3
scp -i cs6650-assignment1-key.pem target/server-v2-1.0-SNAPSHOT.jar \
    ec2-user@SERVER3_IP:~/chatserver.jar

# Server 4
scp -i cs6650-assignment1-key.pem target/server-v2-1.0-SNAPSHOT.jar \
    ec2-user@SERVER4_IP:~/chatserver.jar
```

### **Step 3: Stop Old Server (if running)**
```bash
ssh -i cs6650-assignment1-key.pem ec2-user@SERVER_IP

# Find and kill old server
pkill -9 java

# Verify port is free
sudo lsof -i :8080
```

### **Step 4: Start New Server**
```bash
# Start with logging
nohup java -jar chatserver.jar > chatserver.log 2>&1 &

# Check it started
tail -20 chatserver.log
```

**Expected startup output:**
```
=================================================
  Auto-Scaling Channel Pool Initialized
=================================================
  Initial Size: 5 channels
  Min Size: 5
  Max Size: 25
  Growth Trigger: Wait time > 50ms
  Exchange: chat.exchange (topic, durable)
=================================================

✓ Channel Pool monitor thread started

=======================================================
  ChatServer v3 (Assignment 3) - STARTED
  ✨ Auto-Scaling Channel Pool Enabled
=======================================================
  Port: 8080
  Server ID: i-0abc123def456
  Exchange: chat.exchange (topic)
  Channel Pool: Auto-scaling (5-25 channels)
=======================================================

✓ Connection monitor thread started
```

---

## 📊 Monitor During Test

### **Terminal 1: Follow Server Logs**
```bash
tail -f chatserver.log
```

**What to watch for:**
- Connection events (joined/left)
- Scaling events (📈 SCALING UP / 📉 SCALING DOWN)
- Periodic statistics (every 30-60s)

### **Terminal 2: Watch Scaling Events Only**
```bash
tail -f chatserver.log | grep "SCALING"
```

### **Terminal 3: Watch Statistics**
```bash
tail -f chatserver.log | grep "Statistics"
```

---

## 🧪 Testing

### **Test 1: Verify Auto-Scaling Works**

**On your local machine:**
```bash
cd /Users/teja/Desktop/dev/cs6650/client-part2

# Run with 50K messages first (quick test)
java -jar target/client-part2-1.0-SNAPSHOT.jar
```

**On server, you should see:**
```
1. Pool starts at 5 channels
2. As connections arrive, pool grows
3. Reaches 11-15 channels (single server) or 5-7 (with ALB)
4. During test: utilization 50-80%
5. After test: pool shrinks back to 5
```

### **Test 2: Full 500K Messages**

**Update client to send 500K:**
```bash
# Edit LoadTesterClient.java if needed
# TOTAL_MESSAGES = 500000

# Rebuild
cd /Users/teja/Desktop/dev/cs6650/client-part2
mvn package

# Run
java -jar target/client-part2-1.0-SNAPSHOT.jar
```

**Monitor server throughout full test:**
```bash
# Watch for:
✅ Pool scales up smoothly
✅ No "MAX_POOL_SIZE" warnings
✅ Utilization stays healthy (50-80%)
✅ After test, pool shrinks back
```

---

## 📸 Screenshots for Assignment

Capture these for your report:

### **1. Initial Startup**
```bash
tail -30 chatserver.log
```
Shows pool initialized at 5 channels

### **2. During Load - Scaling Up**
```bash
# Wait for scaling event
tail -f chatserver.log | grep "SCALING UP" -A 5
```
Shows pool growing to meet demand

### **3. Statistics During Load**
```bash
# Wait for statistics output
tail -f chatserver.log | grep "Channel Pool Statistics" -A 8
```
Shows utilization, growth events

### **4. After Load - Scaling Down**
```bash
# Wait 30+ seconds after test
tail -f chatserver.log | grep "SCALING DOWN" -A 5
```
Shows pool shrinking back

### **5. Connection Statistics**
```bash
# During or after test
tail -f chatserver.log | grep "Connection Statistics" -A 6
```
Shows active connections, peak connections

---

## 🔍 Troubleshooting

### **Problem: Server won't start**
```bash
# Check logs
tail -50 chatserver.log

# Common issues:
# 1. Port 8080 in use
sudo lsof -i :8080
pkill -9 java

# 2. RabbitMQ not accessible
# Check RabbitMQ is running on correct IP

# 3. Permission issues
chmod +x chatserver.jar
```

### **Problem: Pool not scaling**
```bash
# Check if monitor threads started
grep "monitor thread started" chatserver.log

# Should see:
✓ Channel Pool monitor thread started
✓ Connection monitor thread started
```

### **Problem: Can't see scaling events**
```bash
# Make sure you're running with enough connections
# Single server needs 10+ connections to trigger growth

# For ALB scenario, each server gets ~5 connections
# May not see much scaling (this is GOOD!)
```

---

## ✅ Deployment Checklist

**Before running test:**
- [ ] Server rebuilt with new auto-scaling code
- [ ] Deployed to EC2 instance(s)
- [ ] Server started successfully
- [ ] Logs show "Auto-Scaling Channel Pool Enabled"
- [ ] Monitor threads started
- [ ] Initial pool size is 5 channels

**During test:**
- [ ] Watching server logs in real-time
- [ ] Seeing connection events
- [ ] Seeing scaling events (if load is high enough)
- [ ] Seeing periodic statistics

**After test:**
- [ ] Captured scaling up events
- [ ] Captured statistics during load
- [ ] Captured scaling down events
- [ ] Pool returned to 5 channels

---

## 📊 Expected Results

### **Single Server:**
```
Initial:  5 channels
Peak:     11-15 channels (during 500K test)
Final:    5 channels (after test)

Total growth events: 2-4
Total shrink events: 2-3
```

### **4 Servers with ALB:**
```
Each server:
  Initial:  5 channels
  Peak:     5-7 channels
  Final:    5 channels

Total across all:
  Peak: 20-28 channels (vs 80 with fixed pool!)
  Savings: 65-75%!
```

---

## 🎯 Key Benefits You'll Observe

1. **Efficiency:** Pool size matches actual load
2. **Automatic:** No manual tuning needed
3. **Observable:** Clear logs show what's happening
4. **Optimal:** Each deployment scenario gets right size
5. **Production-ready:** Thread-safe, handles edge cases

---

**Your server is now ready with auto-scaling channel pool! 🚀**

For detailed behavior explanations, see:
- **AUTO_SCALING.md** - Complete documentation
- **QUICK_REFERENCE.md** - What you'll see in logs
