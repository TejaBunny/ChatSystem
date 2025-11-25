#!/bin/bash

##############################################
# MongoDB Performance Monitoring Script
# Tracks: Throughput, Latency, Operations
##############################################

OUTPUT_FILE="mongodb_metrics_$(date +%Y%m%d_%H%M%S).csv"

echo "Timestamp,Total_Inserts,Inserts_Per_Sec,Avg_Latency_ms,Active_Connections,Memory_MB" > $OUTPUT_FILE

echo "======================================"
echo "  MongoDB Performance Monitor"
echo "======================================"
echo "Logging to: $OUTPUT_FILE"
echo "Collecting metrics every 5 seconds..."
echo ""

PREV_INSERTS=0
INTERVAL=5

while true; do
    TIMESTAMP=$(date +%s)
    TIME_STR=$(date +%T)
    
    # Get server status
    STATUS=$(mongosh --quiet --eval "
        var stats = db.serverStatus();
        print(
            stats.opcounters.insert + ',' +
            stats.connections.current + ',' +
            Math.round(stats.mem.resident)
        );
    " chat_system 2>/dev/null)
    
    if [ ! -z "$STATUS" ]; then
        IFS=',' read -r INSERTS CONNECTIONS MEMORY <<< "$STATUS"
        
        # Calculate throughput
        if [ $PREV_INSERTS -ne 0 ]; then
            DIFF=$((INSERTS - PREV_INSERTS))
            THROUGHPUT=$((DIFF / INTERVAL))
        else
            THROUGHPUT=0
        fi
        PREV_INSERTS=$INSERTS
        
        # Get average write latency from recent operations
        LATENCY=$(mongosh --quiet --eval "
            var profile = db.system.profile.find(
                {op: 'insert', ts: {\$gt: new Date(Date.now() - 5000)}}
            ).toArray();
            if (profile.length > 0) {
                var sum = 0;
                profile.forEach(function(op) { sum += op.millis; });
                print(Math.round(sum / profile.length));
            } else {
                print(0);
            }
        " chat_system 2>/dev/null)
        
        # Print to console
        echo "[$TIME_STR] Inserts: $INSERTS | Throughput: $THROUGHPUT/sec | Latency: ${LATENCY}ms | Connections: $CONNECTIONS | Memory: ${MEMORY}MB"
        
        # Log to CSV
        echo "$TIMESTAMP,$INSERTS,$THROUGHPUT,${LATENCY:-0},$CONNECTIONS,$MEMORY" >> $OUTPUT_FILE
    else
        echo "[$TIME_STR] Unable to connect to MongoDB"
    fi
    
    sleep $INTERVAL
done
