# Client Part 2 - Clean Rebuild Instructions

## Issue Resolution: "getTotalRequests() method not found"

**Status:** ✅ FIXED - The code is already correct!

The LoadTesterClient does NOT call `getTotalRequests()`. Instead, it calculates:
```java
int totalRequests = metrics.getSuccessfulRequests() + metrics.getFailedRequests();
```

This is the correct approach since MetricsCollector provides these two separate methods.

---

## Clean Build Process

### 1. Clean Previous Build
```bash
cd /Users/teja/Desktop/dev/cs6650/client-part2
mvn clean
```

### 2. Compile and Package
```bash
mvn package
```

### 3. Verify Build
Check for:
- `target/client-part2-1.0-SNAPSHOT.jar` (shaded JAR with dependencies)

---

## Run the Client

### Basic Run (Default: localhost)
```bash
java -jar target/client-part2-1.0-SNAPSHOT.jar
```

### Run with Custom Server URL
Edit `LoadTesterClient.java` line 11:
```java
private static final String SERVER_URL_BASE = "ws://YOUR_ALB_OR_SERVER:8080/chat/";
```

Then rebuild:
```bash
mvn clean package
java -jar target/client-part2-1.0-SNAPSHOT.jar
```

---

## File Compatibility Summary

✅ **LoadTesterClient.java**
- Calls: `getSuccessfulRequests()`, `getFailedRequests()`, `reset()`
- Calculates: `totalRequests = successful + failed`

✅ **MetricsCollector.java** 
- Provides: `recordRequest()`, `getSuccessfulRequests()`, `getFailedRequests()`, `getCsvData()`, `reset()`

✅ **MessageSender.java**
- Uses: `metrics.recordRequest()`, `connectionPool.sendMessage()`

✅ **WebSocketConnectionPool.java**
- Provides: `sendMessage()` returning `MessageResponse`

✅ **PerformanceReport.java**
- Uses: `metrics.getCsvData()`, `getSuccessfulRequests()`, `getFailedRequests()`

✅ **ChatMessage.java**
- Data model with validation

✅ **MessageGenerator.java**
- Generates messages for BlockingQueue

---

## Architecture Flow

```
LoadTesterClient (main)
    ↓
MessageGenerator → BlockingQueue
    ↓
MessageSender (20 threads, one per room)
    ↓
WebSocketConnectionPool (20 connections, synchronized)
    ↓
MetricsCollector (records: timestamp, type, latency, status, room)
    ↓
PerformanceReport (analyzes and prints)
```

---

## Expected Output

```
=================================================
  Load Tester - Assignment 3
  Warmup: 32 threads (Assignment 1 spec)
  Main: 20 threads (optimal for 20 rooms)
=================================================

Initializing connection pool with 20 connections (one per room)...
✓ Connected to room 1
✓ Connected to room 2
...
✓ Connection pool initialized: 20 connections ready

--- Starting Warmup Phase ---
Configuration: 32 threads × 1000 messages (Assignment 1)
Total messages: 32000
...
--- Warmup Phase Results ---
Threads: 32
Total Requests: 32000
Successful: 32000
Failed: 0
Duration: 15234 ms
Throughput: 2100.52 msg/sec

--- Starting Main Testing Phase ---
Configuration: 20 threads (one per room - OPTIMAL)
Total messages: 468000
Messages per thread: 23400
...
✓ Main Testing Phase Complete!

--- Generating Performance Report ---

--- Response Time (ms) Statistics ---
Mean response time: 45.23
Median response time: 42.00
Min response time: 12
Max response time: 234
95th percentile response time: 89.50
99th percentile response time: 145.32

--- Throughput per Room (messages/sec) ---
Room 1: 2340.56
Room 2: 2345.12
...

--- Final Summary ---
Total Successful Messages: 468000
Total Failed Messages: 0
Total Runtime (ms): 45678
Overall Throughput (messages/sec): 10245.67

=================================================
  Test Complete! Check performance_results.csv
=================================================
```

---

## Troubleshooting

### Problem: "getTotalRequests() method not found"
**Solution:** This method doesn't exist and isn't needed! The code correctly calculates `totalRequests` manually. If you still see this error:
1. Run `mvn clean` to clear old compiled classes
2. Run `mvn package` to rebuild fresh
3. Make sure you're running the latest version from `target/client-part2-1.0-SNAPSHOT.jar`

### Problem: Connection refused
**Solution:** 
- Check server is running on correct port
- Update `SERVER_URL_BASE` in LoadTesterClient.java
- For ALB, use: `ws://YOUR_ALB_DNS:8080/chat/`

### Problem: Maven dependency errors
**Solution:**
```bash
mvn clean
mvn dependency:resolve
mvn package
```

---

## Key Features

✅ **Warmup Phase:** 32 threads × 1000 messages (Assignment 1 spec)
✅ **Main Phase:** 20 threads (optimal: one per room)
✅ **Connection Pooling:** 20 shared WebSocket connections
✅ **Synchronized Access:** Room-specific locks for thread-safety
✅ **Metrics Collection:** Timestamp, type, latency, status, room
✅ **Performance Analysis:** Statistics, percentiles, throughput over time
✅ **Automatic Retries:** Exponential backoff, up to 5 attempts
✅ **Graceful Error Handling:** Failed messages marked in metrics

---

**All files are compatible and ready to run!** 🚀
