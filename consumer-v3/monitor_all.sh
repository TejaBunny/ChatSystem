#!/bin/bash

##############################################
# Complete Monitoring Script for Assignment 3
# Monitors: Consumer, MongoDB, RabbitMQ
##############################################

LOG_FILE="performance_metrics_$(date +%Y%m%d_%H%M%S).csv"

# Create CSV header
echo "Timestamp,Consumer_Buffer,DB_Messages,DB_Throughput,Queue_Total_Depth,Circuit_Breaker" > $LOG_FILE

echo "======================================"
echo "  Performance Monitoring Dashboard"
echo "======================================"
echo "Logging to: $LOG_FILE"
echo "Press Ctrl+C to stop"
echo ""

PREV_DB_COUNT=0
INTERVAL=5

while true; do
    TIMESTAMP=$(date +%s)
    TIME_STR=$(date +%T)
    
    # 1. Get Consumer Buffer Status (from consumer logs)
    BUFFER_SIZE=$(tail -100 consumer.log | grep "Buffer size:" | tail -1 | awk '{print $4}' | cut -d'/' -f1)
    
    # 2. Get MongoDB Message Count
    DB_COUNT=$(mongosh --quiet --eval "db.messages.countDocuments()" chat_system 2>/dev/null)
    
    # 3. Calculate MongoDB Throughput
    if [ ! -z "$DB_COUNT" ] && [ $PREV_DB_COUNT -ne 0 ]; then
        DIFF=$((DB_COUNT - PREV_DB_COUNT))
        THROUGHPUT=$((DIFF / INTERVAL))
    else
        THROUGHPUT=0
    fi
    PREV_DB_COUNT=$DB_COUNT
    
    # 4. Get Total Queue Depth (sum of all 20 queues)
    QUEUE_DEPTH=$(rabbitmqadmin -q list queues name messages | awk '{sum+=$2} END {print sum}')
    
    # 5. Get Circuit Breaker State (from consumer logs)
    CIRCUIT_STATE=$(tail -100 consumer.log | grep "Circuit breaker:" | tail -1 | awk '{print $4}')
    
    # Print to console
    echo "[$TIME_STR] Buffer: ${BUFFER_SIZE:-N/A} | DB: ${DB_COUNT:-N/A} msgs | Throughput: $THROUGHPUT msg/sec | Queue: ${QUEUE_DEPTH:-N/A} | Circuit: ${CIRCUIT_STATE:-N/A}"
    
    # Log to CSV
    echo "$TIMESTAMP,${BUFFER_SIZE:-0},${DB_COUNT:-0},$THROUGHPUT,${QUEUE_DEPTH:-0},${CIRCUIT_STATE:-UNKNOWN}" >> $LOG_FILE
    
    sleep $INTERVAL
done
