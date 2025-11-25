# Client-Part2 Updates for Assignment 3

## Architecture Changes

### **Before (Assignment 2):**
```
256 threads × 1 connection each = 256 WebSocket connections
│
├─ Thread 1 → Own WebSocket → Server
├─ Thread 2 → Own WebSocket → Server
├─ ...
└─ Thread 256 → Own WebSocket → Server
```

### **After (Assignment 3 - Connection Pool):**
```
256 threads sharing 20 connections = 20 WebSocket connections
│
Connection Pool (20 connections, one per room):
├─ Room 1 connection ← Thread 1, Thread 21, Thread 41...
├─ Room 2 connection ← Thread 2, Thread 22, Thread 42...
├─ ...
└─ Room 20 connection ← Thread 20, Thread 40, Thread 60...
```

---

## Files Changed

### 1. **WebSocketConnectionPool.java** (NEW)
**Purpose:** Manages 20 shared WebSocket connections (one per room)

**Key Features:**
- Creates 20 connections on initialization (one per room 1-20)
- Provides thread-safe `sendMessage()` method
- Per-room synchronization (threads for different rooms don't block each other)
- Automatic reconnection on failure
- Built-in retry logic (5 attempts with exponential backoff)
- Latency measurement for each send

**Synchronization Strategy:**
```java
// One lock per room (20 locks total)
synchronized (locks[roomIndex]) {
    // Send message
    // Only threads sending to SAME room block each other
    // Threads sending to different rooms run in parallel
}
```

---

### 2. **MessageSender.java** (UPDATED)
**Purpose:** Sends messages using the shared connection pool

**Major Changes:**
- ❌ **Removed:** WebSocket connection creation
- ❌ **Removed:** Connection management code
- ❌ **Removed:** Retry logic (now in pool)
- ✅ **Added:** Uses `connectionPool.sendMessage()`
- ✅ **Simplified:** Just takes messages from queue and uses pool

**Before:**
```java
WebSocketClient client = new WebSocketClient(...);
client.connect();
// Send messages
client.close();
```

**After:**
```java
WebSocketConnectionPool.MessageResponse response = 
    connectionPool.sendMessage(roomId, message);
```

---

### 3. **LoadTesterClient.java** (UPDATED)
**Purpose:** Main test driver using connection pool

**Major Changes:**
- ✅ **Added:** Create `WebSocketConnectionPool` once at startup
- ✅ **Updated:** Pass pool to all `MessageSender` threads
- ✅ **Added:** Close pool at end (`connectionPool.closeAll()`)
- ✅ **Updated:** Thread count optimal for pool design (256 threads)

**Initialization:**
```java
// Create pool once (20 connections)
WebSocketConnectionPool pool = new WebSocketConnectionPool(SERVER_URL_BASE);

// All threads share the pool
for (int i = 0; i < numThreads; i++) {
    Thread sender = new Thread(
        new MessageSender(pool, roomId, queue, latch, messages)
    );
    sender.start();
}
```

---

### 4. **Other Files** (NO CHANGES)
These files remain unchanged:
- ✓ MessageGenerator.java
- ✓ ChatMessage.java
- ✓ MetricsCollector.java
- ✓ PerformanceReport.java
- ✓ BaselineLatencyTester.java

---

## Configuration

### Test Parameters:
```java
WARMUP_THREADS = 32
WARMUP_MESSAGES = 32,000 (32 × 1000)

MAIN_THREADS = 256
MAIN_MESSAGES = 468,000
TOTAL_MESSAGES = 500,000
```

### Connection Pool:
```java
CONNECTIONS = 20 (one per room)
ROOMS = 1-20
```

### Server URL:
```java
SERVER_URL_BASE = "ws://cs6650-alb-1497811358.us-west-2.elb.amazonaws.com:80/chat/"
// Update this to your server address!
```

---

## Benefits of Connection Pool Design

### 1. **Reduced Connection Overhead**
- Before: 256 connections × 3-way TCP handshake = high overhead
- After: 20 connections × 3-way TCP handshake = minimal overhead

### 2. **More Realistic Load**
- Real chat systems: Users stay connected to specific rooms
- Simulates persistent connections better

### 3. **Server Optimization**
- Server handles fewer connections (20 vs 256)
- Matches our server's 20-channel pool design
- More efficient resource usage

### 4. **Better Throughput**
- Less time establishing connections
- More time actually sending messages
- Potentially higher messages/second

### 5. **Cleaner Architecture**
- Connection management centralized in pool
- MessageSender simplified (just sends messages)
- Easier to maintain and debug

---

## How It Works

### **Thread Execution Flow:**

```
Thread 42 (assigned to Room 5):
│
1. Get message from queue
   │
2. Call connectionPool.sendMessage("5", message)
   │
   ├─ Pool acquires lock for Room 5
   ├─ Uses Room 5's WebSocket connection
   ├─ Sends message
   ├─ Waits for server response (with timeout)
   ├─ Records latency
   └─ Releases lock
   │
3. Get metrics response (success/failure, latency)
   │
4. Record in MetricsCollector
   │
5. Repeat for next message
```

### **Parallel Execution:**

```
Time 0ms:
├─ Thread 1 (Room 5)  ← Acquires Room 5 lock
├─ Thread 2 (Room 3)  ← Acquires Room 3 lock (parallel!)
├─ Thread 3 (Room 5)  ← WAITS (Room 5 locked by Thread 1)
└─ Thread 4 (Room 10) ← Acquires Room 10 lock (parallel!)

Key: Threads sending to DIFFERENT rooms run in PARALLEL
     Threads sending to SAME room run SEQUENTIALLY
```

---

## Testing

### Build:
```bash
cd client-part2
mvn clean package
```

### Run:
```bash
java -jar target/client-part2-1.0-SNAPSHOT.jar
```

### Expected Output:
```
=================================================
  Load Tester - Assignment 3 (Connection Pool)
  Architecture: 20 connections shared by threads
=================================================

Initializing connection pool with 20 connections (one per room)...
✓ Connected to room 1
✓ Connected to room 2
...
✓ Connected to room 20
✓ Connection pool initialized: 20 connections ready

--- Starting Warmup Phase ---
Threads: 32
Messages: 32000
...
✓ Warmup Phase Complete

--- Starting Main Testing Phase ---
Threads: 256
Messages: 468000
...
✓ Main Testing Phase Complete!

--- Generating Performance Report ---
...
Test Complete! Check performance_results.csv
```

---

## Troubleshooting

### Problem: "Failed to connect to room X"
**Solution:** Check server is running and URL is correct

### Problem: "All retries failed"
**Solution:** 
- Check server can handle concurrent connections
- Increase timeout in pool (currently 5 seconds)
- Check network latency

### Problem: Low throughput
**Solution:**
- Check if threads are blocking on same room
- Monitor server logs for bottlenecks
- Verify RabbitMQ queues are draining

### Problem: High failure rate
**Solution:**
- Check server capacity
- Reduce number of threads
- Increase timeout values

---

## Performance Expectations

### Connection Overhead:
- **Before:** ~256 × 100ms connection time = 25.6 seconds overhead
- **After:** ~20 × 100ms connection time = 2 seconds overhead
- **Savings:** ~23 seconds!

### Throughput:
- **Expected:** 8,000-10,000 messages/second
- **Duration:** ~50-60 seconds for 500K messages
- **Latency:** p50 < 50ms, p95 < 150ms, p99 < 500ms

### Resource Usage:
- **Client:** Lower (fewer connections)
- **Server:** Lower (20 connections vs 256)
- **Network:** More efficient (persistent connections)

---

## Compatibility

| Component | Status | Notes |
|-----------|--------|-------|
| Server-v3 | ✅ | Compatible (expects 20 connections) |
| RabbitMQ | ✅ | Works with default exchange |
| Metrics | ✅ | All metrics still collected |
| Reports | ✅ | CSV and statistics generated |

---

## Next Steps

1. ✅ Build updated client
2. ⏳ Update SERVER_URL_BASE with your server address
3. ⏳ Test locally or deploy
4. ⏳ Run full 500K message test
5. ⏳ Analyze performance results

---

**Your client is now optimized for the connection pool design!** 🚀
