#!/bin/bash
# run_load_tests.sh - Automated load testing for Assignment 3

set -e  # Exit on error

# Configuration
CONSUMER_JAR="target/consumer-1.0-SNAPSHOT.jar"
CLIENT_DIR="../client-part2"
CLIENT_JAR="$CLIENT_DIR/target/client-part2-1.0-SNAPSHOT.jar"
LOG_DIR="logs"
RESULTS_DIR="results"
MONGO_DB="chat_system"

# Optimal configuration (from batch optimization tests)
OPTIMAL_BATCH_SIZE=1000
OPTIMAL_FLUSH_INTERVAL=500
OPTIMAL_DB_THREADS=10

# Create directories
mkdir -p "$LOG_DIR" "$RESULTS_DIR"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo "=============================================="
echo "  Assignment 3: Load Testing Suite"
echo "=============================================="
echo ""

# Check prerequisites
check_prerequisites() {
    echo "Checking prerequisites..."
    
    if [ ! -f "$CONSUMER_JAR" ]; then
        echo -e "${RED}❌ Consumer JAR not found!${NC}"
        echo "Run: mvn clean package"
        exit 1
    fi
    echo -e "${GREEN}✓${NC} Consumer JAR found"
    
    if [ ! -f "$CLIENT_JAR" ]; then
        echo -e "${RED}❌ Client JAR not found!${NC}"
        echo "Run: cd $CLIENT_DIR && mvn clean package"
        exit 1
    fi
    echo -e "${GREEN}✓${NC} Client JAR found"
    
    if ! pgrep -x mongod > /dev/null; then
        echo -e "${RED}❌ MongoDB not running!${NC}"
        echo "Run: sudo systemctl start mongod"
        exit 1
    fi
    echo -e "${GREEN}✓${NC} MongoDB running"
    
    echo ""
}

# Clean environment
clean_environment() {
    echo "Cleaning environment..."
    
    # Clean MongoDB
    mongo "$MONGO_DB" --quiet --eval "
        db.messages.deleteMany({});
        db.failed_writes.deleteMany({});
        print('✓ MongoDB cleaned: messages=' + db.messages.count() + ', dlq=' + db.failed_writes.count());
    " 2>/dev/null
    
    # Clean RabbitMQ queues (if accessible)
    if command -v rabbitmqadmin &> /dev/null; then
        for i in {1..20}; do
            sudo rabbitmqadmin purge queue name=room.$i 2>/dev/null || true
        done
        echo "✓ RabbitMQ queues purged"
    fi
    
    echo ""
}

# Start consumer with monitoring
start_consumer() {
    local test_name=$1
    
    echo "Starting consumer..."
    echo "  Configuration: batch=$OPTIMAL_BATCH_SIZE, flush=${OPTIMAL_FLUSH_INTERVAL}ms, threads=$OPTIMAL_DB_THREADS"
    
    java -jar "$CONSUMER_JAR" \
        --batchSize=$OPTIMAL_BATCH_SIZE \
        --flushInterval=$OPTIMAL_FLUSH_INTERVAL \
        --dbWriterThreads=$OPTIMAL_DB_THREADS \
        > "$LOG_DIR/consumer_${test_name}.log" 2>&1 &
    
    CONSUMER_PID=$!
    echo "  Consumer PID: $CONSUMER_PID"
    
    # Wait for initialization
    echo "  Waiting for initialization..."
    sleep 10
    
    # Check if still running
    if ! ps -p $CONSUMER_PID > /dev/null; then
        echo -e "${RED}❌ Consumer failed to start!${NC}"
        cat "$LOG_DIR/consumer_${test_name}.log"
        exit 1
    fi
    
    echo -e "${GREEN}✓${NC} Consumer running"
    echo ""
    
    return $CONSUMER_PID
}

# Stop consumer gracefully
stop_consumer() {
    local consumer_pid=$1
    local test_name=$2
    
    echo "Stopping consumer (PID: $consumer_pid)..."
    
    # Send SIGTERM for graceful shutdown
    kill $consumer_pid 2>/dev/null || true
    
    # Wait up to 60 seconds for graceful shutdown
    for i in {1..60}; do
        if ! ps -p $consumer_pid > /dev/null 2>&1; then
            echo -e "${GREEN}✓${NC} Consumer stopped gracefully"
            return 0
        fi
        sleep 1
    done
    
    # Force kill if still running
    echo -e "${YELLOW}⚠${NC} Forcing consumer shutdown..."
    kill -9 $consumer_pid 2>/dev/null || true
    sleep 2
    
    echo ""
}

# Monitor test progress
monitor_test() {
    local consumer_log=$1
    local expected_messages=$2
    local test_name=$3
    
    echo "Monitoring test progress..."
    echo "  Expected messages: $expected_messages"
    echo ""
    
    local last_count=0
    local start_time=$(date +%s)
    local no_progress_count=0
    
    while true; do
        sleep 10
        
        # Check MongoDB count
        local current_count=$(mongo "$MONGO_DB" --quiet --eval "db.messages.count()" 2>/dev/null || echo "0")
        local current_time=$(date +%s)
        local elapsed=$((current_time - start_time))
        
        # Calculate rate
        if [ "$last_count" -gt 0 ]; then
            local new_messages=$((current_count - last_count))
            local rate=$((new_messages / 10))  # Per second (10 sec interval)
            
            # Progress bar
            local percent=$((current_count * 100 / expected_messages))
            local bar_length=$((percent / 2))
            printf "\r  Progress: [%-50s] %d%% (%d/%d) Rate: %d msg/s" \
                "$(printf '#%.0s' $(seq 1 $bar_length))" \
                "$percent" "$current_count" "$expected_messages" "$rate"
            
            # Check for stalled progress
            if [ "$new_messages" -eq 0 ]; then
                ((no_progress_count++))
                if [ "$no_progress_count" -ge 6 ]; then  # 60 seconds no progress
                    echo ""
                    echo -e "${YELLOW}⚠ Warning: No progress for 60 seconds${NC}"
                    echo "  Checking consumer status..."
                    if grep -q "Circuit breaker: OPEN" "$consumer_log"; then
                        echo -e "${RED}❌ Circuit breaker is OPEN!${NC}"
                    fi
                    no_progress_count=0
                fi
            else
                no_progress_count=0
            fi
        fi
        
        last_count=$current_count
        
        # Check if complete
        if [ "$current_count" -ge "$expected_messages" ]; then
            echo ""
            echo -e "${GREEN}✓${NC} All messages received in MongoDB!"
            break
        fi
        
        # Timeout after 15 minutes
        if [ "$elapsed" -gt 900 ]; then
            echo ""
            echo -e "${RED}⚠ Timeout after 15 minutes${NC}"
            echo "  Messages received: $current_count / $expected_messages"
            break
        fi
    done
    
    echo ""
}

# Collect detailed results
collect_results() {
    local test_name=$1
    local expected_messages=$2
    local consumer_log="$LOG_DIR/consumer_${test_name}.log"
    local client_log="$LOG_DIR/client_${test_name}.log"
    local result_file="$RESULTS_DIR/${test_name}_results.txt"
    
    echo "Collecting results..."
    
    # MongoDB counts
    local messages_in_db=$(mongo "$MONGO_DB" --quiet --eval "db.messages.count()" 2>/dev/null)
    local messages_in_dlq=$(mongo "$MONGO_DB" --quiet --eval "db.failed_writes.count()" 2>/dev/null)
    local unique_users=$(mongo "$MONGO_DB" --quiet --eval "db.messages.distinct('userId').length" 2>/dev/null)
    local unique_rooms=$(mongo "$MONGO_DB" --quiet --eval "db.messages.distinct('roomId').length" 2>/dev/null)
    
    # Consumer metrics
    local total_batches=$(grep "Batch written" "$consumer_log" | wc -l)
    local last_batch=$(grep "Batch written" "$consumer_log" | tail -1)
    local total_written=$(echo "$last_batch" | grep -oP 'Total: \K[0-9]+' || echo "0")
    local cb_activations=$(grep "Circuit breaker: CLOSED → OPEN" "$consumer_log" | wc -l)
    
    # Client metrics
    local client_duration="N/A"
    local client_throughput="N/A"
    if [ -f "$client_log" ]; then
        client_duration=$(grep "Total runtime" "$client_log" | grep -oP '[0-9]+\.[0-9]+' || echo "N/A")
        client_throughput=$(grep "Messages per second" "$client_log" | grep -oP '[0-9]+\.[0-9]+' || echo "N/A")
    fi
    
    # Calculate overall throughput
    local first_timestamp=$(grep "Batch written" "$consumer_log" | head -1 | grep -oP '\d{2}:\d{2}:\d{2}' || echo "")
    local last_timestamp=$(grep "Batch written" "$consumer_log" | tail -1 | grep -oP '\d{2}:\d{2}:\d{2}' || echo "")
    
    # Database stats
    local db_size=$(mongo "$MONGO_DB" --quiet --eval "printjson(db.messages.stats().size)" 2>/dev/null)
    local db_size_mb=$(echo "scale=2; $db_size / 1024 / 1024" | bc 2>/dev/null || echo "N/A")
    
    # Create result file
    cat > "$result_file" <<EOF
============================================
  Load Test Results: $test_name
============================================
Timestamp: $(date)

Test Configuration:
  Expected Messages: $expected_messages
  Consumer Config: batch=$OPTIMAL_BATCH_SIZE, flush=${OPTIMAL_FLUSH_INTERVAL}ms, threads=$OPTIMAL_DB_THREADS

Results:
  Messages in DB: $messages_in_db
  Messages in DLQ: $messages_in_dlq
  Success Rate: $(echo "scale=2; $messages_in_db * 100 / $expected_messages" | bc)%
  
Client Metrics:
  Duration: ${client_duration}s
  Throughput: ${client_throughput} msg/s
  
Consumer Metrics:
  Total Batches: $total_batches
  Total Written: $total_written
  Circuit Breaker Activations: $cb_activations
  
Database Metrics:
  Unique Users: $unique_users
  Unique Rooms: $unique_rooms
  Database Size: ${db_size_mb} MB
  
Status: $([ "$messages_in_db" -eq "$expected_messages" ] && echo "✅ PASSED" || echo "⚠ INCOMPLETE")
============================================
EOF
    
    # Display results
    echo ""
    cat "$result_file"
    echo ""
    
    # Append to summary CSV
    local summary_csv="$RESULTS_DIR/load_test_summary.csv"
    if [ ! -f "$summary_csv" ]; then
        echo "Test,ExpectedMessages,MessagesInDB,DLQ,SuccessRate%,ClientDuration(s),ClientThroughput,Batches,CBActivations,DBSize(MB)" > "$summary_csv"
    fi
    echo "$test_name,$expected_messages,$messages_in_db,$messages_in_dlq,$(echo "scale=2; $messages_in_db * 100 / $expected_messages" | bc),$client_duration,$client_throughput,$total_batches,$cb_activations,$db_size_mb" >> "$summary_csv"
}

# Test 1: Baseline (500K messages)
test_baseline() {
    local test_name="test1_baseline_500k"
    local expected_messages=500000
    
    echo ""
    echo "=============================================="
    echo "  Test 1: Baseline (500K messages)"
    echo "=============================================="
    echo ""
    
    # Clean
    clean_environment
    
    # Start consumer
    start_consumer "$test_name"
    local consumer_pid=$!
    
    # Run client
    echo "Starting client (500K messages)..."
    local start_time=$(date +%s)
    
    java -jar "$CLIENT_JAR" > "$LOG_DIR/client_${test_name}.log" 2>&1 &
    local client_pid=$!
    
    # Monitor progress
    monitor_test "$LOG_DIR/consumer_${test_name}.log" "$expected_messages" "$test_name"
    
    # Wait for client to finish
    wait $client_pid 2>/dev/null || true
    
    local end_time=$(date +%s)
    local duration=$((end_time - start_time))
    echo "Client finished in ${duration}s"
    
    # Wait extra time for consumer to finish processing
    echo "Waiting 30s for consumer to finish processing..."
    sleep 30
    
    # Stop consumer
    stop_consumer $consumer_pid "$test_name"
    
    # Collect results
    collect_results "$test_name" "$expected_messages"
    
    echo -e "${GREEN}✅ Test 1 complete${NC}"
}

# Test 2: Stress Test (1M messages)
test_stress() {
    local test_name="test2_stress_1m"
    local expected_messages=1000000
    
    echo ""
    echo "=============================================="
    echo "  Test 2: Stress Test (1M messages)"
    echo "=============================================="
    echo ""
    
    # Check if client needs to be modified
    echo "Checking client configuration..."
    if grep -q "TOTAL_MESSAGES = 500_000" "$CLIENT_DIR/src/main/java/ChatClient.java" 2>/dev/null; then
        echo -e "${YELLOW}⚠ Client is configured for 500K messages${NC}"
        echo ""
        echo "To run 1M message test, please:"
        echo "1. Edit $CLIENT_DIR/src/main/java/ChatClient.java"
        echo "2. Change: private static final int TOTAL_MESSAGES = 1_000_000;"
        echo "3. Rebuild: cd $CLIENT_DIR && mvn clean package"
        echo ""
        read -p "Press Enter when ready, or Ctrl+C to skip this test..."
    fi
    
    # Clean
    clean_environment
    
    # Start consumer
    start_consumer "$test_name"
    local consumer_pid=$!
    
    # Run client
    echo "Starting client (1M messages)..."
    local start_time=$(date +%s)
    
    java -jar "$CLIENT_JAR" > "$LOG_DIR/client_${test_name}.log" 2>&1 &
    local client_pid=$!
    
    # Monitor progress
    monitor_test "$LOG_DIR/consumer_${test_name}.log" "$expected_messages" "$test_name"
    
    # Wait for client
    wait $client_pid 2>/dev/null || true
    
    local end_time=$(date +%s)
    local duration=$((end_time - start_time))
    echo "Client finished in ${duration}s"
    
    # Wait for consumer
    echo "Waiting 60s for consumer to finish processing..."
    sleep 60
    
    # Stop consumer
    stop_consumer $consumer_pid "$test_name"
    
    # Collect results
    collect_results "$test_name" "$expected_messages"
    
    echo -e "${GREEN}✅ Test 2 complete${NC}"
}

# Test 3: Endurance Test (30 minutes)
test_endurance() {
    local test_name="test3_endurance_30min"
    local duration_minutes=30
    local target_rate=8000  # 80% of max throughput
    local expected_messages=$((target_rate * duration_minutes * 60))
    
    echo ""
    echo "=============================================="
    echo "  Test 3: Endurance Test (30 minutes)"
    echo "=============================================="
    echo ""
    echo "Target: $target_rate msg/s for $duration_minutes minutes"
    echo "Expected messages: $expected_messages"
    echo ""
    
    echo -e "${YELLOW}⚠ This test requires client modification${NC}"
    echo ""
    echo "To run endurance test:"
    echo "1. Edit $CLIENT_DIR/src/main/java/ChatClient.java"
    echo "2. Set: TOTAL_MESSAGES = $expected_messages"
    echo "3. Adjust NUM_THREADS to sustain target rate"
    echo "4. Rebuild: cd $CLIENT_DIR && mvn clean package"
    echo ""
    read -p "Press Enter when ready, or Ctrl+C to skip this test..."
    
    # Clean
    clean_environment
    
    # Start consumer
    start_consumer "$test_name"
    local consumer_pid=$!
    
    # Start monitoring in background
    ./monitoring_scripts.sh mongodb > "$LOG_DIR/monitor_${test_name}.log" 2>&1 &
    local monitor_pid=$!
    
    # Run client
    echo "Starting client (endurance test)..."
    local start_time=$(date +%s)
    
    java -jar "$CLIENT_JAR" > "$LOG_DIR/client_${test_name}.log" 2>&1 &
    local client_pid=$!
    
    # Monitor with checkpoints
    echo "Monitoring endurance test..."
    for minute in {5,10,15,20,25,30}; do
        sleep 300  # 5 minutes
        local current_count=$(mongo "$MONGO_DB" --quiet --eval "db.messages.count()" 2>/dev/null)
        local elapsed=$((minute * 60))
        local current_rate=$((current_count / elapsed))
        echo "  ${minute}min checkpoint: $current_count messages ($current_rate msg/s)"
    done
    
    # Wait for client
    wait $client_pid 2>/dev/null || true
    
    local end_time=$(date +%s)
    local total_duration=$((end_time - start_time))
    echo "Client finished in ${total_duration}s"
    
    # Stop monitoring
    kill $monitor_pid 2>/dev/null || true
    
    # Wait for consumer
    echo "Waiting 60s for consumer to finish processing..."
    sleep 60
    
    # Stop consumer
    stop_consumer $consumer_pid "$test_name"
    
    # Collect results
    collect_results "$test_name" "$expected_messages"
    
    echo -e "${GREEN}✅ Test 3 complete${NC}"
}

# Generate final report
generate_report() {
    local report_file="$RESULTS_DIR/LOAD_TEST_REPORT.md"
    
    echo ""
    echo "Generating final report..."
    
    cat > "$report_file" <<'EOF'
# Load Test Report

## Test Summary

This report summarizes the results of three load tests:
1. Baseline Test (500K messages)
2. Stress Test (1M messages)
3. Endurance Test (30 minutes sustained)

## Results

EOF
    
    # Append CSV data
    if [ -f "$RESULTS_DIR/load_test_summary.csv" ]; then
        echo '```' >> "$report_file"
        column -t -s',' "$RESULTS_DIR/load_test_summary.csv" >> "$report_file"
        echo '```' >> "$report_file"
        echo "" >> "$report_file"
    fi
    
    # Add individual test results
    for result in "$RESULTS_DIR"/test*_results.txt; do
        if [ -f "$result" ]; then
            echo "## $(basename $result .txt)" >> "$report_file"
            echo '```' >> "$report_file"
            cat "$result" >> "$report_file"
            echo '```' >> "$report_file"
            echo "" >> "$report_file"
        fi
    done
    
    echo -e "${GREEN}✓${NC} Report generated: $report_file"
}

# Main menu
show_menu() {
    echo "Select test to run:"
    echo "  1) Baseline Test (500K messages) - ~1 hour"
    echo "  2) Stress Test (1M messages) - ~2 hours"
    echo "  3) Endurance Test (30 min sustained) - ~30 minutes"
    echo "  4) Run all tests - ~4 hours"
    echo "  5) Generate report from existing results"
    echo "  q) Quit"
    echo ""
    read -p "Choice: " choice
    
    case "$choice" in
        1)
            test_baseline
            generate_report
            ;;
        2)
            test_stress
            generate_report
            ;;
        3)
            test_endurance
            generate_report
            ;;
        4)
            test_baseline
            echo ""
            read -p "Press Enter to continue to Stress Test..."
            test_stress
            echo ""
            read -p "Press Enter to continue to Endurance Test..."
            test_endurance
            generate_report
            ;;
        5)
            generate_report
            ;;
        q|Q)
            echo "Exiting..."
            exit 0
            ;;
        *)
            echo "Invalid choice"
            exit 1
            ;;
    esac
}

# Parse command line
check_prerequisites

if [ $# -eq 0 ]; then
    show_menu
else
    case "$1" in
        baseline)
            test_baseline
            generate_report
            ;;
        stress)
            test_stress
            generate_report
            ;;
        endurance)
            test_endurance
            generate_report
            ;;
        all)
            test_baseline
            test_stress
            test_endurance
            generate_report
            ;;
        report)
            generate_report
            ;;
        *)
            echo "Usage: $0 [baseline|stress|endurance|all|report]"
            echo "   or: $0 (interactive menu)"
            exit 1
            ;;
    esac
fi

echo ""
echo "=============================================="
echo "  Load Testing Complete!"
echo "=============================================="
echo ""
echo "Results saved in: $RESULTS_DIR/"
echo "Logs saved in: $LOG_DIR/"
echo ""