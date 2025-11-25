#!/bin/bash
# monitoring_scripts.sh - Collection of monitoring utilities

# =============================================================================
# Script 1: Real-time Consumer Monitoring
# =============================================================================
monitor_consumer() {
    LOG_FILE=$1
    
    echo "=== Real-Time Consumer Monitor ==="
    echo "Monitoring: $LOG_FILE"
    echo "Press Ctrl+C to stop"
    echo ""
    
    while true; do
        clear
        echo "=== Consumer Status ($(date)) ==="
        echo ""
        
        # Messages written
        echo "📊 Messages Written:"
        LAST_BATCH=$(grep "Batch written" "$LOG_FILE" 2>/dev/null | tail -1)
        if [ -n "$LAST_BATCH" ]; then
            echo "  $LAST_BATCH"
        else
            echo "  Waiting for first batch..."
        fi
        echo ""
        
        # Buffer status
        echo "📦 Buffer Status:"
        BUFFER=$(grep "Buffer size:" "$LOG_FILE" 2>/dev/null | tail -1)
        if [ -n "$BUFFER" ]; then
            echo "  $BUFFER"
        else
            echo "  No buffer data yet"
        fi
        echo ""
        
        # Circuit breaker
        echo "⚡ Circuit Breaker:"
        CB=$(grep "Circuit breaker:" "$LOG_FILE" 2>/dev/null | tail -1)
        if [ -n "$CB" ]; then
            echo "  $CB"
        else
            echo "  CLOSED"
        fi
        echo ""
        
        # Dead letter queue
        echo "💀 Dead Letter Queue:"
        DLQ_COUNT=$(grep "Saving.*messages to Dead Letter Queue" "$LOG_FILE" 2>/dev/null | wc -l)
        echo "  DLQ saves: $DLQ_COUNT"
        echo ""
        
        # Throughput calculation
        echo "🚀 Throughput:"
        TOTAL=$(grep "Batch written" "$LOG_FILE" 2>/dev/null | tail -1 | grep -oP 'Total: \K[0-9]+' || echo "0")
        BATCHES=$(grep "Batch written" "$LOG_FILE" 2>/dev/null | tail -1 | grep -oP 'Batches: \K[0-9]+' || echo "0")
        if [ "$BATCHES" -gt 0 ]; then
            AVG_BATCH=$((TOTAL / BATCHES))
            echo "  Average batch size: $AVG_BATCH messages"
            echo "  Total messages: $TOTAL"
            echo "  Total batches: $BATCHES"
        fi
        echo ""
        
        sleep 5
    done
}

# =============================================================================
# Script 2: RabbitMQ Queue Monitor
# =============================================================================
monitor_rabbitmq() {
    echo "=== RabbitMQ Queue Monitor ==="
    echo "Monitoring queue depths..."
    echo "Press Ctrl+C to stop"
    echo ""
    
    while true; do
        clear
        echo "=== RabbitMQ Queue Depths ($(date)) ==="
        echo ""
        
        # Get queue depths
        for i in {1..20}; do
            DEPTH=$(sudo rabbitmqctl list_queues name messages 2>/dev/null | grep "room.$i" | awk '{print $2}' || echo "0")
            printf "Room %2d: %6s messages" "$i" "$DEPTH"
            
            # Visual bar
            BAR_LENGTH=$((DEPTH / 50))
            if [ $BAR_LENGTH -gt 50 ]; then BAR_LENGTH=50; fi
            printf " ["
            for ((j=0; j<BAR_LENGTH; j++)); do printf "█"; done
            printf "]\n"
        done
        
        echo ""
        echo "Total messages in all queues:"
        TOTAL=$(sudo rabbitmqctl list_queues messages 2>/dev/null | grep "room." | awk '{sum+=$2} END {print sum}' || echo "0")
        echo "  $TOTAL messages"
        echo ""
        
        sleep 5
    done
}

# =============================================================================
# Script 3: MongoDB Statistics Monitor
# =============================================================================
monitor_mongodb() {
    echo "=== MongoDB Statistics Monitor ==="
    echo "Press Ctrl+C to stop"
    echo ""
    
    LAST_COUNT=0
    START_TIME=$(date +%s)
    
    while true; do
        clear
        CURRENT_TIME=$(date +%s)
        ELAPSED=$((CURRENT_TIME - START_TIME))
        
        echo "=== MongoDB Stats (Runtime: ${ELAPSED}s) ==="
        echo ""
        
        # Message count
        COUNT=$(mongo chat_system --quiet --eval "db.messages.count()" 2>/dev/null || echo "0")
        echo "📊 Messages in DB: $COUNT"
        
        # Calculate rate
        if [ $LAST_COUNT -gt 0 ]; then
            RATE=$(( (COUNT - LAST_COUNT) / 5 ))
            echo "📈 Insert rate: $RATE msg/s"
        fi
        LAST_COUNT=$COUNT
        echo ""
        
        # DLQ count
        DLQ=$(mongo chat_system --quiet --eval "db.failed_writes.count()" 2>/dev/null || echo "0")
        echo "💀 Failed messages: $DLQ"
        echo ""
        
        # Database stats
        echo "💾 Database Statistics:"
        mongo chat_system --quiet --eval "
            var stats = db.messages.stats();
            print('  Size: ' + (stats.size / 1024 / 1024).toFixed(2) + ' MB');
            print('  Avg document: ' + stats.avgObjSize + ' bytes');
            print('  Indexes: ' + stats.nindexes);
        " 2>/dev/null
        echo ""
        
        # Unique counts
        USERS=$(mongo chat_system --quiet --eval "db.messages.distinct('userId').length" 2>/dev/null || echo "0")
        ROOMS=$(mongo chat_system --quiet --eval "db.messages.distinct('roomId').length" 2>/dev/null || echo "0")
        echo "👥 Unique users: $USERS"
        echo "🏠 Unique rooms: $ROOMS"
        echo ""
        
        sleep 5
    done
}

# =============================================================================
# Script 4: System Resource Monitor
# =============================================================================
monitor_resources() {
    echo "=== System Resource Monitor ==="
    echo "Press Ctrl+C to stop"
    echo ""
    
    while true; do
        clear
        echo "=== System Resources ($(date)) ==="
        echo ""
        
        # CPU and Memory for Java processes
        echo "🖥️  Java Processes:"
        ps aux | grep java | grep -v grep | awk '{
            printf "  PID %s: CPU %s%% MEM %s%% CMD: %s\n", $2, $3, $4, $11
        }'
        echo ""
        
        # System-wide stats
        echo "💻 System Overview:"
        echo "  CPU Usage: $(top -bn1 | grep "Cpu(s)" | awk '{print $2}' | cut -d'%' -f1)%"
        echo "  Memory:"
        free -h | grep Mem | awk '{
            printf "    Total: %s, Used: %s, Available: %s\n", $2, $3, $7
        }'
        echo ""
        
        # Disk I/O
        echo "💿 Disk I/O:"
        iostat -x 1 2 | tail -1 | awk '{
            printf "    Read: %.2f MB/s, Write: %.2f MB/s\n", $6/1024, $7/1024
        }'
        echo ""
        
        # Network
        echo "🌐 Network:"
        RX=$(cat /sys/class/net/eth0/statistics/rx_bytes 2>/dev/null || echo "0")
        TX=$(cat /sys/class/net/eth0/statistics/tx_bytes 2>/dev/null || echo "0")
        sleep 1
        RX2=$(cat /sys/class/net/eth0/statistics/rx_bytes 2>/dev/null || echo "0")
        TX2=$(cat /sys/class/net/eth0/statistics/tx_bytes 2>/dev/null || echo "0")
        RX_RATE=$(( (RX2 - RX) / 1024 ))
        TX_RATE=$(( (TX2 - TX) / 1024 ))
        echo "    RX: $RX_RATE KB/s, TX: $TX_RATE KB/s"
        echo ""
        
        sleep 4
    done
}

# =============================================================================
# Script 5: All-in-One Monitor (Tmux)
# =============================================================================
monitor_all() {
    TEST_NAME=$1
    LOG_DIR="logs"
    
    if [ -z "$TEST_NAME" ]; then
        echo "Usage: monitor_all <test_name>"
        echo "Example: monitor_all test1_baseline"
        exit 1
    fi
    
    echo "Starting comprehensive monitoring for: $TEST_NAME"
    echo "This will create a tmux session with 4 panes"
    echo ""
    
    # Create tmux session
    SESSION_NAME="monitor_${TEST_NAME}"
    
    # Start tmux with 4 panes
    tmux new-session -d -s "$SESSION_NAME"
    
    # Split into 4 panes
    tmux split-window -h -t "$SESSION_NAME"
    tmux split-window -v -t "$SESSION_NAME:0.0"
    tmux split-window -v -t "$SESSION_NAME:0.2"
    
    # Pane 0: Consumer log
    tmux send-keys -t "$SESSION_NAME:0.0" "tail -f $LOG_DIR/consumer_$TEST_NAME.log" C-m
    
    # Pane 1: MongoDB stats
    tmux send-keys -t "$SESSION_NAME:0.1" "./monitoring_scripts.sh mongodb" C-m
    
    # Pane 2: RabbitMQ queues
    tmux send-keys -t "$SESSION_NAME:0.2" "./monitoring_scripts.sh rabbitmq" C-m
    
    # Pane 3: System resources
    tmux send-keys -t "$SESSION_NAME:0.3" "./monitoring_scripts.sh resources" C-m
    
    # Attach to session
    echo "Attaching to monitoring session..."
    echo "Use Ctrl+B then D to detach"
    sleep 2
    tmux attach-session -t "$SESSION_NAME"
}

# =============================================================================
# Script 6: Test Results Collector
# =============================================================================
collect_results() {
    TEST_NAME=$1
    CONSUMER_LOG=$2
    CLIENT_LOG=$3
    OUTPUT_DIR="results"
    
    if [ -z "$TEST_NAME" ]; then
        echo "Usage: collect_results <test_name> <consumer_log> <client_log>"
        exit 1
    fi
    
    mkdir -p "$OUTPUT_DIR"
    RESULT_FILE="$OUTPUT_DIR/${TEST_NAME}_results.txt"
    
    echo "=== Test Results: $TEST_NAME ===" > "$RESULT_FILE"
    echo "Timestamp: $(date)" >> "$RESULT_FILE"
    echo "" >> "$RESULT_FILE"
    
    # Consumer metrics
    echo "=== Consumer Metrics ===" >> "$RESULT_FILE"
    echo "Total batches: $(grep 'Batch written' "$CONSUMER_LOG" | wc -l)" >> "$RESULT_FILE"
    
    LAST_BATCH=$(grep "Batch written" "$CONSUMER_LOG" | tail -1)
    TOTAL_MSGS=$(echo "$LAST_BATCH" | grep -oP 'Total: \K[0-9]+' || echo "0")
    TOTAL_BATCHES=$(echo "$LAST_BATCH" | grep -oP 'Batches: \K[0-9]+' || echo "0")
    
    echo "Total messages written: $TOTAL_MSGS" >> "$RESULT_FILE"
    echo "Total batches: $TOTAL_BATCHES" >> "$RESULT_FILE"
    
    if [ "$TOTAL_BATCHES" -gt 0 ]; then
        AVG_BATCH=$((TOTAL_MSGS / TOTAL_BATCHES))
        echo "Average batch size: $AVG_BATCH" >> "$RESULT_FILE"
    fi
    
    echo "Circuit breaker activations: $(grep 'Circuit breaker: CLOSED → OPEN' "$CONSUMER_LOG" | wc -l)" >> "$RESULT_FILE"
    echo "DLQ saves: $(grep 'Saving.*messages to Dead Letter Queue' "$CONSUMER_LOG" | wc -l)" >> "$RESULT_FILE"
    echo "" >> "$RESULT_FILE"
    
    # Client metrics
    if [ -f "$CLIENT_LOG" ]; then
        echo "=== Client Metrics ===" >> "$RESULT_FILE"
        grep "Total runtime" "$CLIENT_LOG" >> "$RESULT_FILE"
        grep "Messages per second" "$CLIENT_LOG" >> "$RESULT_FILE"
        grep "Connection failures" "$CLIENT_LOG" >> "$RESULT_FILE"
        echo "" >> "$RESULT_FILE"
    fi
    
    # MongoDB stats
    echo "=== MongoDB Statistics ===" >> "$RESULT_FILE"
    mongo chat_system --quiet --eval "
        print('Messages in DB: ' + db.messages.count());
        print('Messages in DLQ: ' + db.failed_writes.count());
        print('Unique users: ' + db.messages.distinct('userId').length);
        print('Unique rooms: ' + db.messages.distinct('roomId').length);
        var stats = db.messages.stats();
        print('DB size: ' + (stats.size / 1024 / 1024).toFixed(2) + ' MB');
    " >> "$RESULT_FILE" 2>&1
    
    echo ""
    echo "✓ Results saved to: $RESULT_FILE"
    cat "$RESULT_FILE"
}

# =============================================================================
# Main command dispatcher
# =============================================================================
case "$1" in
    consumer)
        monitor_consumer "$2"
        ;;
    rabbitmq)
        monitor_rabbitmq
        ;;
    mongodb)
        monitor_mongodb
        ;;
    resources)
        monitor_resources
        ;;
    all)
        monitor_all "$2"
        ;;
    collect)
        collect_results "$2" "$3" "$4"
        ;;
    *)
        echo "Usage: $0 {consumer|rabbitmq|mongodb|resources|all|collect}"
        echo ""
        echo "Commands:"
        echo "  consumer <log_file>              - Monitor consumer application"
        echo "  rabbitmq                         - Monitor RabbitMQ queue depths"
        echo "  mongodb                          - Monitor MongoDB statistics"
        echo "  resources                        - Monitor system resources"
        echo "  all <test_name>                  - Start all monitors in tmux"
        echo "  collect <name> <consumer> <client> - Collect test results"
        echo ""
        echo "Examples:"
        echo "  ./monitoring_scripts.sh consumer logs/consumer.log"
        echo "  ./monitoring_scripts.sh all test1_baseline"
        echo "  ./monitoring_scripts.sh collect test1 logs/consumer.log logs/client.log"
        exit 1
        ;;
esac