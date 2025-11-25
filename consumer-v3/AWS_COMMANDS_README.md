# AWS Commands - Quick Reference

## Setup (One-time)

1. **Update server IPs in `aws_commands.sh`:**
   ```bash
   nano aws_commands.sh
   
   # Update these lines:
   CONSUMER_IP="<your-consumer-private-ip>"
   MONGODB_IP="<your-mongodb-private-ip>"
   ALB_DNS="<your-alb-dns-name>"
   ```

2. **Make scripts executable:**
   ```bash
   chmod +x cleanup.sh
   chmod +x aws_commands.sh
   chmod +x analyze_results.sh
   ```

3. **Run cleanup (moves old scripts to backup):**
   ```bash
   ./cleanup.sh
   ```

---

## Quick Commands

### Deploy & Start
```bash
# Build and deploy consumer to EC2
./aws_commands.sh deploy

# Start consumer on EC2
./aws_commands.sh start
# Returns PID - save this!
```

### Monitor
```bash
# Check system status (consumer, MongoDB, RabbitMQ)
./aws_commands.sh status

# Check MongoDB count
./aws_commands.sh count

# Watch count in real-time (updates every 5s)
./aws_commands.sh watch
# Press Ctrl+C to stop

# Check RabbitMQ queue depths
./aws_commands.sh queues
```

### Get Results
```bash
# Download consumer logs
./aws_commands.sh logs
# Saves to logs/consumer_TIMESTAMP.log

# Analyze metrics (if you have CSV)
./analyze_results.sh results/mongodb_metrics.csv
```

### Cleanup
```bash
# Stop consumer
./aws_commands.sh stop

# Clean MongoDB (for next test)
./aws_commands.sh clean
# Will ask for confirmation
```

---

## Manual SSH Commands (Alternative)

If you prefer direct SSH:

### Start Consumer
```bash
ssh -i cs6650-assignment1-key.pem ec2-user@<consumer-ip> \
  "nohup java -jar consumer-1.0-SNAPSHOT.jar \
  --rabbitMQHost=172.31.12.56 \
  --mongoHost=<mongodb-ip> \
  --batchSize=1000 \
  --flushInterval=500 \
  --dbWriterThreads=10 \
  > logs/consumer.log 2>&1 & echo \$!"
```

### Check MongoDB Count
```bash
ssh -i cs6650-assignment1-key.pem ec2-user@<mongodb-ip> \
  "mongosh chat_system --quiet --eval 'db.messages.count()'"
```

### Download Logs
```bash
scp -i cs6650-assignment1-key.pem \
  ec2-user@<consumer-ip>:logs/consumer.log \
  ./logs/
```

### Stop Consumer
```bash
ssh -i cs6650-assignment1-key.pem ec2-user@<consumer-ip> \
  "pkill -f consumer-1.0-SNAPSHOT.jar"
```

---

## Full Testing Workflow

### 1. Prepare
```bash
# Clean MongoDB
./aws_commands.sh clean

# Make sure client is configured for ALB
cd ../client-part2
# Edit ChatClient.java: WS_URL = "ws://<alb-dns>/chat/"
# Edit ChatClient.java: TOTAL_MESSAGES = 500_000
mvn clean package
cd ../consumer
```

### 2. Deploy and Start
```bash
# Deploy consumer
./aws_commands.sh deploy

# Start consumer
./aws_commands.sh start
# Note the PID!
```

### 3. Run Test
```bash
# In Terminal 1: Watch MongoDB count
./aws_commands.sh watch

# In Terminal 2: Run client
cd ../client-part2
time java -jar target/client-part2-1.0-SNAPSHOT.jar | tee logs/client.log
```

### 4. Collect Results
```bash
# Wait for MongoDB to reach 500000
# Press Ctrl+C in Terminal 1 (watching count)

# Stop consumer
./aws_commands.sh stop

# Download logs
./aws_commands.sh logs

# Check final stats
./aws_commands.sh status
```

### 5. Test Queries
```bash
ssh -i cs6650-assignment1-key.pem ec2-user@<consumer-ip> \
  "java -cp consumer-1.0-SNAPSHOT.jar QueryTestClient <mongodb-ip> 27017"
```

---

## Files in This Directory

```
consumer/
├── analyze_results.sh         ← Analyze CSV metrics
├── aws_commands.sh            ← All AWS commands
├── cleanup.sh                 ← Cleanup old scripts
├── AWS_COMMANDS_README.md     ← This file
├── cs6650-assignment1-key.pem ← SSH key
├── pom.xml
├── src/
│   └── main/java/
│       ├── QueryOperations.java      ← Add this!
│       ├── QueryTestClient.java      ← Add this!
│       └── ... (other files)
└── old_scripts_backup/        ← Backup of old scripts
```

---

## Troubleshooting

### "Permission denied" when running scripts
```bash
chmod +x cleanup.sh aws_commands.sh analyze_results.sh
```

### "Connection refused" to EC2
```bash
# Check security groups allow SSH (port 22)
# Check key permissions
chmod 400 cs6650-assignment1-key.pem
```

### Consumer won't start
```bash
# SSH to consumer and check logs
ssh -i cs6650-assignment1-key.pem ec2-user@<consumer-ip>
tail -50 logs/consumer.log
```

### No messages in MongoDB
```bash
# Check consumer is running
./aws_commands.sh status

# Check RabbitMQ has messages
./aws_commands.sh queues

# Check consumer logs for errors
./aws_commands.sh logs
tail -100 logs/consumer_*.log
```

---

## Next Steps

1. ✅ Run `./cleanup.sh` to clean old scripts
2. ✅ Update IPs in `aws_commands.sh`
3. ✅ Add `QueryOperations.java` and `QueryTestClient.java`
4. ✅ Follow AWS_ACTION_PLAN.md for full deployment
5. ✅ Run tests and document results

---

**Simple and clean!** 🚀
