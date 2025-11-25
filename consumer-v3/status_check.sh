#!/bin/bash

##############################################
# Quick Status Check Script
# Shows current state of all components
##############################################

echo "=================================================="
echo "  System Status Check"
echo "=================================================="
echo ""

# 1. Consumer Status
echo "🔹 Consumer Status:"
if pgrep -f consumer-1.0-SNAPSHOT.jar > /dev/null; then
    echo "   ✅ Consumer is running (PID: $(pgrep -f consumer-1.0-SNAPSHOT.jar))"
    
    # Get latest status from logs
    BUFFER=$(tail -100 consumer.log 2>/dev/null | grep "Buffer size:" | tail -1)
    if [ ! -z "$BUFFER" ]; then
        echo "   $BUFFER"
    fi
else
    echo "   ❌ Consumer is NOT running"
fi
echo ""

# 2. MongoDB Status
echo "🔹 MongoDB Status:"
DB_COUNT=$(mongosh --quiet --eval "db.messages.countDocuments()" chat_system 2>/dev/null)
if [ ! -z "$DB_COUNT" ]; then
    echo "   ✅ MongoDB is running"
    echo "   Messages in DB: $DB_COUNT"
    
    # Get recent throughput
    INSERTS=$(mongosh --quiet --eval "db.serverStatus().opcounters.insert" chat_system 2>/dev/null)
    echo "   Total inserts: $INSERTS"
else
    echo "   ❌ Cannot connect to MongoDB"
fi
echo ""

# 3. RabbitMQ Status
echo "🔹 RabbitMQ Status:"
QUEUE_DEPTH=$(rabbitmqadmin -q list queues name messages 2>/dev/null | awk '{sum+=$2} END {print sum}')
if [ ! -z "$QUEUE_DEPTH" ]; then
    echo "   ✅ RabbitMQ is running"
    echo "   Total queue depth: $QUEUE_DEPTH messages"
    
    # Show per-queue breakdown
    echo "   Per-queue depths:"
    rabbitmqadmin -q list queues name messages 2>/dev/null | head -20 | awk '{printf "      %s: %s\n", $1, $2}'
else
    echo "   ❌ Cannot connect to RabbitMQ"
fi
echo ""

# 4. Server Status
echo "🔹 Chat Server Status:"
if pgrep -f chatserver.jar > /dev/null; then
    echo "   ✅ Server is running (PID: $(pgrep -f chatserver.jar))"
else
    echo "   ❌ Server is NOT running"
fi
echo ""

# 5. System Resources
echo "🔹 System Resources:"
echo "   Java processes:"
ps aux | grep java | grep -v grep | awk '{printf "      PID: %s, CPU: %s%%, MEM: %s%%, CMD: %s\n", $2, $3, $4, $11}'
echo ""

echo "=================================================="
echo "  To monitor continuously, use:"
echo "    ./monitor_all.sh"
echo "=================================================="
