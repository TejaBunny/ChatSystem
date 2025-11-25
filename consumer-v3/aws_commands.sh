#!/bin/bash
# AWS Quick Commands Reference
# Save your server IPs here and use these commands

# ============================================
# CONFIGURATION - UPDATE THESE!
# ============================================
CONSUMER_IP="<your-consumer-private-ip>"
MONGODB_IP="<your-mongodb-private-ip>"
RABBITMQ_IP="172.31.12.56"
KEY="cs6650-assignment1-key.pem"
ALB_DNS="<your-alb-dns-name>"

# ============================================
# QUICK COMMANDS
# ============================================

show_help() {
    echo "=================================="
    echo "  AWS Quick Commands"
    echo "=================================="
    echo ""
    echo "Usage: ./aws_commands.sh <command>"
    echo ""
    echo "Commands:"
    echo "  deploy           - Build and deploy consumer to EC2"
    echo "  start            - Start consumer on EC2"
    echo "  stop             - Stop consumer on EC2"
    echo "  status           - Check system status"
    echo "  count            - Check MongoDB message count"
    echo "  watch            - Watch MongoDB count (updates every 5s)"
    echo "  logs             - Download consumer logs"
    echo "  clean            - Clean MongoDB"
    echo "  queues           - Check RabbitMQ queue depths"
    echo ""
    echo "Before using, update the IPs at the top of this script!"
    echo ""
}

deploy() {
    echo "Building consumer..."
    mvn clean package || { echo "Build failed!"; exit 1; }
    
    echo "Deploying to EC2..."
    scp -i $KEY target/consumer-1.0-SNAPSHOT.jar ec2-user@$CONSUMER_IP:~/ || { echo "Deploy failed!"; exit 1; }
    
    echo "✅ Deployment complete!"
}

start_consumer() {
    echo "Starting consumer on EC2..."
    echo "Configuration:"
    echo "  RabbitMQ: $RABBITMQ_IP"
    echo "  MongoDB: $MONGODB_IP"
    echo "  Batch: 1000, Flush: 500ms, Threads: 10"
    echo ""
    
    ssh -i $KEY ec2-user@$CONSUMER_IP \
        "nohup java -jar consumer-1.0-SNAPSHOT.jar \
        --rabbitMQHost=$RABBITMQ_IP \
        --mongoHost=$MONGODB_IP \
        --batchSize=1000 \
        --flushInterval=500 \
        --dbWriterThreads=10 \
        > logs/consumer.log 2>&1 & echo \$!"
    
    echo ""
    echo "✅ Consumer started! PID shown above."
    echo "To check if running: ./aws_commands.sh status"
}

stop_consumer() {
    echo "Stopping consumer..."
    ssh -i $KEY ec2-user@$CONSUMER_IP "pkill -f consumer-1.0-SNAPSHOT.jar"
    echo "✅ Consumer stopped"
}

check_status() {
    echo "=================================="
    echo "  System Status"
    echo "=================================="
    echo ""
    
    echo "Consumer:"
    ssh -i $KEY ec2-user@$CONSUMER_IP \
        "ps aux | grep consumer-1.0-SNAPSHOT.jar | grep -v grep" && echo "  ✅ Running" || echo "  ❌ Not running"
    
    echo ""
    echo "MongoDB:"
    COUNT=$(ssh -i $KEY ec2-user@$MONGODB_IP \
        "mongosh chat_system --quiet --eval 'db.messages.count()'" 2>/dev/null)
    echo "  Messages: $COUNT"
    
    echo ""
    echo "RabbitMQ:"
    QUEUE_DEPTH=$(ssh -i $KEY ec2-user@$RABBITMQ_IP \
        "sudo rabbitmqctl list_queues 2>/dev/null | grep room | awk '{sum+=\$2} END {print sum}'")
    echo "  Total queue depth: ${QUEUE_DEPTH:-0} messages"
    
    echo ""
}

check_count() {
    echo "MongoDB message count:"
    ssh -i $KEY ec2-user@$MONGODB_IP \
        "mongosh chat_system --quiet --eval 'db.messages.count()'"
}

watch_count() {
    echo "Watching MongoDB count (Ctrl+C to stop)..."
    watch -n 5 "ssh -i $KEY ec2-user@$MONGODB_IP \
        'mongosh chat_system --quiet --eval \"db.messages.count()\"'"
}

download_logs() {
    echo "Downloading consumer logs..."
    mkdir -p logs
    scp -i $KEY ec2-user@$CONSUMER_IP:logs/consumer.log ./logs/consumer_$(date +%Y%m%d_%H%M%S).log
    echo "✅ Logs downloaded to logs/"
}

clean_mongodb() {
    echo "⚠️  This will delete ALL messages in MongoDB!"
    read -p "Are you sure? (yes/no): " confirm
    
    if [ "$confirm" = "yes" ]; then
        echo "Cleaning MongoDB..."
        ssh -i $KEY ec2-user@$MONGODB_IP \
            "mongosh chat_system --eval 'db.messages.deleteMany({}); db.failed_writes.deleteMany({});'"
        echo "✅ MongoDB cleaned"
    else
        echo "Cancelled"
    fi
}

check_queues() {
    echo "RabbitMQ Queue Depths:"
    ssh -i $KEY ec2-user@$RABBITMQ_IP \
        "sudo rabbitmqctl list_queues name messages 2>/dev/null | grep room" | \
        awk '{printf "  %s: %s messages\n", $1, $2}'
    
    echo ""
    TOTAL=$(ssh -i $KEY ec2-user@$RABBITMQ_IP \
        "sudo rabbitmqctl list_queues 2>/dev/null | grep room | awk '{sum+=\$2} END {print sum}'")
    echo "Total: ${TOTAL:-0} messages"
}

# Main
if [ $# -eq 0 ]; then
    show_help
    exit 0
fi

case "$1" in
    deploy)
        deploy
        ;;
    start)
        start_consumer
        ;;
    stop)
        stop_consumer
        ;;
    status)
        check_status
        ;;
    count)
        check_count
        ;;
    watch)
        watch_count
        ;;
    logs)
        download_logs
        ;;
    clean)
        clean_mongodb
        ;;
    queues)
        check_queues
        ;;
    *)
        echo "Unknown command: $1"
        show_help
        exit 1
        ;;
esac
