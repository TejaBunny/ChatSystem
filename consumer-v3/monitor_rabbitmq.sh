#!/bin/bash

##############################################
# RabbitMQ Queue Monitoring Script
# Tracks: Queue Depths, Message Rates
##############################################

OUTPUT_FILE="rabbitmq_metrics_$(date +%Y%m%d_%H%M%S).csv"

# Create header
HEADER="Timestamp"
for i in {1..20}; do
    HEADER="$HEADER,room${i}_depth,room${i}_rate_in,room${i}_rate_out"
done
echo "$HEADER,Total_Depth" > $OUTPUT_FILE

echo "======================================"
echo "  RabbitMQ Queue Monitor"
echo "======================================"
echo "Logging to: $OUTPUT_FILE"
echo "Monitoring 20 queues every 5 seconds..."
echo ""

while true; do
    TIMESTAMP=$(date +%s)
    TIME_STR=$(date +%T)
    
    ROW="$TIMESTAMP"
    TOTAL_DEPTH=0
    
    # Get metrics for each room queue
    for i in {1..20}; do
        QUEUE="room.$i"
        
        # Get queue stats
        STATS=$(rabbitmqadmin -q show queue name=$QUEUE 2>/dev/null | grep -E "messages |messages_ready |message_stats")
        
        # Extract messages count
        DEPTH=$(echo "$STATS" | grep -o "messages [0-9]*" | awk '{print $2}' | head -1)
        DEPTH=${DEPTH:-0}
        
        # For simplicity, set rates to 0 (can be calculated from depth changes)
        RATE_IN=0
        RATE_OUT=0
        
        ROW="$ROW,$DEPTH,$RATE_IN,$RATE_OUT"
        TOTAL_DEPTH=$((TOTAL_DEPTH + DEPTH))
    done
    
    ROW="$ROW,$TOTAL_DEPTH"
    
    # Print summary to console
    echo "[$TIME_STR] Total Queue Depth: $TOTAL_DEPTH messages"
    
    # Log to CSV
    echo "$ROW" >> $OUTPUT_FILE
    
    sleep 5
done
