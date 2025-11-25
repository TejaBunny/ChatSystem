#!/bin/bash

##############################################
# Performance Data Analysis Script
# Analyzes collected metrics files
##############################################

if [ $# -lt 1 ]; then
    echo "Usage: $0 <mongodb_metrics_file.csv>"
    echo "Example: $0 mongodb_metrics_20241121_143000.csv"
    exit 1
fi

METRICS_FILE=$1

if [ ! -f "$METRICS_FILE" ]; then
    echo "Error: File '$METRICS_FILE' not found"
    exit 1
fi

echo "=================================================="
echo "  Performance Analysis Report"
echo "=================================================="
echo "File: $METRICS_FILE"
echo ""

# Extract timestamp range
START_TIME=$(tail -n +2 "$METRICS_FILE" | head -1 | cut -d',' -f1)
END_TIME=$(tail -1 "$METRICS_FILE" | cut -d',' -f1)
DURATION=$((END_TIME - START_TIME))

echo "📅 Test Duration:"
echo "   Start: $(date -d @$START_TIME '+%Y-%m-%d %H:%M:%S')"
echo "   End:   $(date -d @$END_TIME '+%Y-%m-%d %H:%M:%S')"
echo "   Duration: $DURATION seconds ($(($DURATION / 60)) minutes)"
echo ""

# Calculate throughput statistics
echo "📊 Write Throughput (messages/second):"
awk -F',' 'NR>1 && $3>0 {
    sum+=$3; 
    count++; 
    if ($3>max || max=="") max=$3;
    if ($3<min || min=="") min=$3;
    values[count]=$3;
} 
END {
    avg=sum/count;
    
    # Calculate median
    n=asort(values);
    if (n%2) {
        median=values[(n+1)/2];
    } else {
        median=(values[n/2]+values[n/2+1])/2;
    }
    
    printf "   Average: %.2f msg/sec\n", avg;
    printf "   Median:  %.2f msg/sec\n", median;
    printf "   Peak:    %.2f msg/sec\n", max;
    printf "   Min:     %.2f msg/sec\n", min;
}' "$METRICS_FILE"
echo ""

# Calculate latency statistics
echo "⏱️  Write Latency (milliseconds):"
awk -F',' 'NR>1 && $4>0 {
    sum+=$4; 
    count++;
    if ($4>max || max=="") max=$4;
    if ($4<min || min=="") min=$4;
    values[count]=$4;
}
END {
    if (count>0) {
        avg=sum/count;
        
        # Calculate p95 and p99
        n=asort(values);
        p95_idx=int(n*0.95);
        p99_idx=int(n*0.99);
        
        printf "   Average: %.2f ms\n", avg;
        printf "   Min:     %.2f ms\n", min;
        printf "   Max:     %.2f ms\n", max;
        printf "   p95:     %.2f ms\n", values[p95_idx];
        printf "   p99:     %.2f ms\n", values[p99_idx];
    } else {
        print "   No latency data collected";
    }
}' "$METRICS_FILE"
echo ""

# Calculate total messages
echo "📦 Total Messages:"
FINAL_COUNT=$(tail -1 "$METRICS_FILE" | cut -d',' -f2)
INITIAL_COUNT=$(tail -n +2 "$METRICS_FILE" | head -1 | cut -d',' -f2)
MESSAGES_WRITTEN=$((FINAL_COUNT - INITIAL_COUNT))
echo "   Messages written during test: $MESSAGES_WRITTEN"
echo "   Final DB count: $FINAL_COUNT"
echo ""

# Calculate resource usage
echo "💻 Resource Usage:"
awk -F',' 'NR>1 {
    sum_conn+=$5; 
    sum_mem+=$6; 
    count++;
    if ($5>max_conn || max_conn=="") max_conn=$5;
    if ($6>max_mem || max_mem=="") max_mem=$6;
}
END {
    printf "   Avg Connections: %.0f\n", sum_conn/count;
    printf "   Peak Connections: %d\n", max_conn;
    printf "   Avg Memory: %.0f MB\n", sum_mem/count;
    printf "   Peak Memory: %d MB\n", max_mem;
}' "$METRICS_FILE"
echo ""

echo "=================================================="
echo ""

# Check for queue depth file
QUEUE_FILE=$(echo $METRICS_FILE | sed 's/mongodb/rabbitmq/')
if [ -f "$QUEUE_FILE" ]; then
    echo "📈 Queue Depth Analysis:"
    awk -F',' 'NR>1 {
        depth=$NF;
        sum+=depth;
        count++;
        if (depth>max || max=="") max=depth;
    }
    END {
        printf "   Average queue depth: %.0f messages\n", sum/count;
        printf "   Peak queue depth: %d messages\n", max;
    }' "$QUEUE_FILE"
    echo ""
fi

# Performance rating
echo "✅ Performance Rating:"
AVG_THROUGHPUT=$(awk -F',' 'NR>1 && $3>0 {sum+=$3; count++} END {print sum/count}' "$METRICS_FILE")

if (( $(echo "$AVG_THROUGHPUT > 8000" | bc -l) )); then
    echo "   🌟 EXCELLENT - Throughput exceeds 8,000 msg/sec"
elif (( $(echo "$AVG_THROUGHPUT > 5000" | bc -l) )); then
    echo "   👍 GOOD - Throughput above 5,000 msg/sec"
elif (( $(echo "$AVG_THROUGHPUT > 2000" | bc -l) )); then
    echo "   ⚠️  NEEDS IMPROVEMENT - Throughput below 5,000 msg/sec"
else
    echo "   ❌ POOR - Throughput very low, investigate bottlenecks"
fi

echo ""
echo "=================================================="
