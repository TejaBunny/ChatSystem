#!/bin/bash
# setup_rabbitmq.sh - OPTIMIZED for Dynamic Consumers
# Only creates the Exchange and Persistence Queues

set -e

echo "============================================================"
echo "  RabbitMQ Setup - Option A (Dynamic Hot Path)"
echo "============================================================"
echo ""

# 1. Cleanup Everything First
echo "Cleaning up old queues..."
sudo rabbitmqadmin delete exchange name=chat.exchange 2>/dev/null || true
for i in {1..20}; do
  sudo rabbitmqadmin delete queue name=room.$i 2>/dev/null || true
done
# Delete the confusing static queues
for i in {1..4}; do
  sudo rabbitmqadmin delete queue name=server.$i.broadcast 2>/dev/null || true
done

# 2. Create Exchange
echo "Creating Exchange..."
sudo rabbitmqadmin declare exchange name=chat.exchange type=topic durable=true
echo "✓ Exchange created"

# 3. Create Persistence Queues (Cold Path) ONLY
echo "Creating Persistence Queues..."
for i in {1..20}; do
  # Create Queue
  sudo rabbitmqadmin declare queue name=room.$i durable=true
  # Bind Queue
  sudo rabbitmqadmin declare binding source=chat.exchange destination=room.$i routing_key=room.$i
done
echo "✓ 20 Room queues created and bound"

echo ""
echo "============================================================"
echo "  ✅ Setup Complete"
echo "============================================================"
echo "  - Cold Path: 20 'room.N' queues created for Consumer."
echo "  - Hot Path:  ChatServers will create their own queues automatically."
echo "============================================================"