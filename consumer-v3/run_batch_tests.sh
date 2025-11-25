#!/bin/bash
# run_batch_tests.sh - Automated batch optimization testing for Assignment 3

set -e  # Exit on error

# Configuration
CONSUMER_JAR="target/consumer-1.0-SNAPSHOT.jar"
CLIENT_JAR="../client-part2/target/client-part2-1.0-SNAPSHOT.jar"
LOG_DIR="logs"
RESULTS_DIR="results"
MONGO_DB="chat_system"

# Create directories
mkdir -p "$LOG_DIR" "$RESULTS_DIR"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo "=============================================="
echo "  Assignment 3: Batch Optimization Testing"
echo "=============================================="
echo ""

# Check prerequisites
check_prerequisites() {
    echo "Checking prerequisites..."
    
    # Check consumer JAR
    if [ ! -f "$CONSUMER_JAR" ]; then
        echo -e "${RED}❌ Consumer JAR not found!${NC}"
        echo "Run: mvn clean package"
        exit 1
    fi
    echo -e "${GREEN}✓${NC} Consumer JAR found"
    
    # Check client JAR
    if [ ! -f "$CLIENT_JAR" ]; then
        echo -e "${RED}❌ Client JAR not found!${NC}"
        echo "Run: cd ../client-part2 && mvn clean package"
        exit 1
    fi
    echo -e "${GREEN}✓${NC} Client JAR found"
    
    # Check MongoDB
    if ! pgrep -x mongod > /dev/null; then
        echo -e "${RED}❌ MongoDB not running!${NC}"
        echo "Run: sudo systemctl start mongod"
        exit 1
    fi
    echo -e "${GREEN}✓${NC} MongoDB running"
    
    # Check RabbitMQ connection
    if ! nc -z 172.31.12.56 5672 2>/dev/null; then
        echo -e "${YELLOW}⚠${NC}  Can't reach RabbitMQ (testing local only)"
    else
        echo -e "${GREEN}✓${NC} RabbitMQ reachable"
    fi
    
    echo ""
}

# Clean database and queues
clean_environment() {
    echo "Cleaning environment..."
    
    # Clean MongoDB
    mongo "$MONGO_DB" --quiet --eval "
        db.messages.deleteMany({});
        db.failed_writes.deleteMany({});
        print('✓ MongoDB cleaned');
    " 2>/dev/null || echo -e "${RED}⚠ MongoDB cleanup failed${NC}"
    
    # Clean RabbitMQ queues (if accessible)
    if command -v rabbitmqadmin &> /dev/null; then
        echo "Purging RabbitMQ queues..."
        for i in {1..20}; do
            sudo rabbitmqadmin purge queue name=room.$i 2>/dev/null || true
        done
        echo "✓ RabbitMQ queues purged"
    fi
    
    echo ""
}

# Run a single batch test
run_batch_test() {
    local batch_size=$1
    local flush_interval=$2
    local db_threads=$3
    local test_name="b${batch_size}_f${flush_interval}_t${db_threads}"
    
    echo "=============================================="
    echo "  Test: Batch=$batch_size, Flush=${flush_interval}ms, Threads=$db_threads"
    echo "=============================================="
    
    # Clean environment
    clean_environment
    
    # Start consumer
    echo "Starting consumer..."
    java -jar "$CONSUMER_JAR" \
        --batchSize=$batch_size \
        --flushInterval=$flush_interval \
        --dbWriterThreads=$db_threads \
        > "$LOG_DIR/consumer_${test_name}.log" 2>&1 &
    
    CONSUMER_PID=$!
    echo "Consumer PID: $CONSUMER_PID"
    
    # Wait for consumer to initialize
    echo "Waiting for consumer to initialize..."
    sleep 10
    
    # Check if consumer is still running
    if ! ps -p $CONSUMER_PID > /dev/null; then
        echo -e "${RED}❌ Consumer failed to start!${NC}"
        cat "$LOG_DIR/consumer_${test_name}.log"
        return 1
    fi
    echo -e "${GREEN}✓${NC} Consumer running"
    
    # Run client
    echo "Running client (sending 500K messages)..."
    START_TIME=$(date +%s)
    
    java -jar "$CLIENT_JAR" > "$LOG_DIR/client_${test_name}.log" 2>&1
    CLIENT_EXIT=$?
    
    END_TIME=$(date +%s)
    DURATION=$((END_TIME - START_TIME))
    
    if [ $CLIENT_EXIT -ne 0 ]; then
        echo -e "${RED}❌ Client failed!${NC}"
        tail "$LOG_DIR/client_${test_name}.log"
        kill $CONSUMER_PID 2>/dev/null || true
        return 1
    fi
    echo -e "${GREEN}✓${NC} Client finished in ${DURATION}s"
    
    # Wait for consumer to process remaining messages
    echo "Waiting for consumer to process remaining messages..."
    sleep 30
    
    # Check buffer is empty
    BUFFER_SIZE=$(grep "Buffer size:" "$LOG_DIR/consumer_${test_name}.log" | tail -1 | grep -oP 'Buffer size: \K[0-9]+' || echo "unknown")
    echo "Final buffer size: $BUFFER_SIZE"
    
    # Stop consumer
    echo "Stopping consumer..."
    kill $CONSUMER_PID
    wait $CONSUMER_PID 2>/dev/null || true
    
    # Collect results
    echo "Collecting results..."
    
    # MongoDB counts
    MESSAGES_IN_DB=$(mongo "$MONGO_DB" --quiet --eval "db.messages.count()" 2>/dev/null)
    MESSAGES_IN_DLQ=$(mongo "$MONGO_DB" --quiet --eval "db.failed_writes.count()" 2>/dev/null)
    
    # Consumer metrics
    TOTAL_BATCHES=$(grep "Batch written" "$LOG_DIR/consumer_${test_name}.log" | wc -l)
    LAST_BATCH=$(grep "Batch written" "$LOG_DIR/consumer_${test_name}.log" | tail -1)
    TOTAL_WRITTEN=$(echo "$LAST_BATCH" | grep -oP 'Total: \K[0-9]+' || echo "0")
    
    # Calculate throughput
    if [ "$DURATION" -gt 0 ] && [ "$TOTAL_WRITTEN" -gt 0 ]; then
        THROUGHPUT=$((TOTAL_WRITTEN / DURATION))
    else
        THROUGHPUT=0
    fi
    
    # Circuit breaker activations
    CB_ACTIVATIONS=$(grep "Circuit breaker: CLOSED → OPEN" "$LOG_DIR/consumer_${test_name}.log" | wc -l)
    
    # Queue depth stats (if available)
    MAX_QUEUE_DEPTH=$(grep "Queue depth" "$LOG_DIR/consumer_${test_name}.log" | awk '{print $3}' | sort -n | tail -1 || echo "N/A")
    
    # Save results
    RESULT_FILE="$RESULTS_DIR/${test_name}_results.txt"
    cat > "$RESULT_FILE" <<EOF
Test: $test_name
Timestamp: $(date)

Configuration:
  Batch Size: $batch_size
  Flush Interval: ${flush_interval}ms
  DB Writer Threads: $db_threads

Results:
  Duration: ${DURATION}s
  Messages in DB: $MESSAGES_IN_DB
  Messages in DLQ: $MESSAGES_IN_DLQ
  Total batches: $TOTAL_BATCHES
  Throughput: $THROUGHPUT msg/s
  Circuit breaker activations: $CB_ACTIVATIONS
  Max queue depth: $MAX_QUEUE_DEPTH

Success: $([ "$MESSAGES_IN_DB" -eq 500000 ] && echo "YES" || echo "NO")
EOF
    
    # Display results
    echo ""
    echo "================================"
    echo "  Results Summary"
    echo "================================"
    cat "$RESULT_FILE"
    echo ""
    
    # Append to CSV
    CSV_FILE="$RESULTS_DIR/batch_optimization_results.csv"
    if [ ! -f "$CSV_FILE" ]; then
        echo "Test,BatchSize,FlushInterval,DBThreads,Duration(s),MessagesInDB,DLQ,Throughput(msg/s),Batches,CBActivations" > "$CSV_FILE"
    fi
    echo "$test_name,$batch_size,$flush_interval,$db_threads,$DURATION,$MESSAGES_IN_DB,$MESSAGES_IN_DLQ,$THROUGHPUT,$TOTAL_BATCHES,$CB_ACTIVATIONS" >> "$CSV_FILE"
    
    # Success check
    if [ "$MESSAGES_IN_DB" -eq 500000 ] && [ "$MESSAGES_IN_DLQ" -eq 0 ]; then
        echo -e "${GREEN}✅ Test PASSED${NC}"
        return 0
    else
        echo -e "${RED}⚠ Test completed with warnings${NC}"
        return 1
    fi
}

# Main test suite
run_test_suite() {
    local mode=$1
    
    echo "Running test suite: $mode"
    echo ""
    
    case "$mode" in
        quick)
            echo "Running quick tests (3 configs)..."
            run_batch_test 500 500 10
            sleep 5
            run_batch_test 1000 500 10
            sleep 5
            run_batch_test 1000 1000 10
            ;;
        
        standard)
            echo "Running standard tests (6 configs)..."
            run_batch_test 100 500 10
            sleep 5
            run_batch_test 500 500 10
            sleep 5
            run_batch_test 1000 100 10
            sleep 5
            run_batch_test 1000 500 10
            sleep 5
            run_batch_test 1000 1000 10
            sleep 5
            run_batch_test 5000 500 10
            ;;
        
        full)
            echo "Running full test suite (12 configs)..."
            for batch in 100 500 1000 5000; do
                for flush in 100 500 1000; do
                    run_batch_test $batch $flush 10
                    sleep 5
                done
            done
            ;;
        
        custom)
            # Read from command line
            if [ $# -lt 4 ]; then
                echo "Usage: $0 custom <batch_size> <flush_interval> <db_threads>"
                exit 1
            fi
            run_batch_test $2 $3 $4
            ;;
        
        *)
            echo "Unknown mode: $mode"
            echo "Available modes: quick, standard, full, custom"
            exit 1
            ;;
    esac
}

# Generate summary report
generate_summary() {
    CSV_FILE="$RESULTS_DIR/batch_optimization_results.csv"
    
    if [ ! -f "$CSV_FILE" ]; then
        echo "No results file found!"
        return
    fi
    
    echo ""
    echo "=============================================="
    echo "  Test Summary"
    echo "=============================================="
    echo ""
    
    # Find best configuration
    echo "Best configurations:"
    echo ""
    
    # Sort by throughput
    echo "Top 3 by Throughput:"
    tail -n +2 "$CSV_FILE" | sort -t',' -k8 -n -r | head -3 | \
        awk -F',' '{printf "  %s: %s msg/s (batch=%s, flush=%sms)\n", $1, $8, $2, $3}'
    echo ""
    
    # Show all results
    echo "All results:"
    column -t -s',' "$CSV_FILE"
    echo ""
    
    echo "Detailed results saved in: $RESULTS_DIR/"
    echo "CSV summary: $CSV_FILE"
}

# Parse command line arguments
if [ $# -eq 0 ]; then
    echo "Usage: $0 <mode> [args]"
    echo ""
    echo "Modes:"
    echo "  quick           - Run 3 quick tests (~30 minutes)"
    echo "  standard        - Run 6 standard tests (~1 hour)"
    echo "  full            - Run all 12 tests (~2 hours)"
    echo "  custom <b> <f> <t> - Run custom config"
    echo ""
    echo "Examples:"
    echo "  $0 quick"
    echo "  $0 standard"
    echo "  $0 custom 1000 500 10"
    exit 1
fi

# Run
check_prerequisites
run_test_suite "$@"
generate_summary

echo ""
echo -e "${GREEN}✅ Batch testing complete!${NC}"
echo ""