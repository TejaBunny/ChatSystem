#!/bin/bash
# monitor.sh - Universal Resource Monitor for Assignment 3 Endurance Test
# Usage: ./monitor.sh <PROCESS_PATTERN> <PORT> <OUTPUT_FILE>
# Example: ./monitor.sh consumer.jar 8081 metrics_consumer.csv

if [ "$#" -ne 3 ]; then
    echo "Usage: $0 <PROCESS_PATTERN> <PORT> <OUTPUT_FILE>"
    echo "Examples:"
    echo "  Consumer: ./monitor.sh consumer.jar 0 metrics_consumer.csv"
    echo "  MongoDB:  ./monitor.sh mongod 27017 metrics_mongo.csv"
    echo "  RabbitMQ: ./monitor.sh beam 5672 metrics_rabbit.csv"
    exit 1
fi

PROCESS_PATTERN=$1
PORT=$2
OUTPUT_FILE=$3

# Write Header
echo "Timestamp,Memory_MB,Disk_Usage_%,Connections" > "$OUTPUT_FILE"

echo "Starting monitoring for process '$PROCESS_PATTERN' on port $PORT..."
echo "Logging to $OUTPUT_FILE (Ctrl+C to stop)"

while true; do
    # 1. Timestamp
    TS=$(date +"%H:%M:%S")

    # 2. Memory (Resident Set Size in MB)
    # We find the PID of the process matching the pattern
    PID=$(pgrep -f "$PROCESS_PATTERN" | head -n 1)
    
    if [ -z "$PID" ]; then
        MEM="0"
    else
        # ps -o rss= outputs in KB, so we divide by 1024 for MB
        KB=$(ps -o rss= -p "$PID" 2>/dev/null | awk '{print $1}')
        if [ -z "$KB" ]; then MEM="0"; else MEM=$((KB / 1024)); fi
    fi

    # 3. Disk Usage (% of root partition)
    DISK=$(df -h / | awk 'NR==2 {print $5}' | tr -d '%')

    # 4. Connections (Established TCP connections on specific port)
    if [ "$PORT" -eq 0 ]; then
        CONNS="0" # For Consumer (outgoing), hard to track specific port easily
    else
        CONNS=$(netstat -an | grep ":$PORT " | grep ESTABLISHED | wc -l)
    fi

    # Log to CSV
    echo "$TS,$MEM,$DISK,$CONNS" >> "$OUTPUT_FILE"
    
    # Print to console for liveness check
    echo "[$TS] Mem: ${MEM}MB | Disk: ${DISK}% | Conn: ${CONNS}"

    # Wait 10 seconds
    sleep 10
done