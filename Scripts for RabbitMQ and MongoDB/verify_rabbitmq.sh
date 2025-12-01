#!/bin/bash
# verify_rabbitmq.sh - Verify RabbitMQ setup for Option A

set -e

echo "============================================================"
echo "  RabbitMQ Setup Verification - Option A"
echo "============================================================"
echo ""

# Colors
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m'

ERRORS=0

# 1. Check RabbitMQ is running
echo "1. Checking RabbitMQ service..."
if sudo systemctl is-active --quiet rabbitmq-server; then
    echo -e "${GREEN}✓${NC} RabbitMQ is running"
else
    echo -e "${RED}❌ RabbitMQ is NOT running!${NC}"
    exit 1
fi
echo ""

# 2. Check exchange exists
echo "2. Checking exchange..."
EXCHANGE_COUNT=$(sudo rabbitmqctl list_exchanges 2>/dev/null | grep "^chat.exchange" | wc -l)
if [ $EXCHANGE_COUNT -eq 1 ]; then
    echo -e "${GREEN}✓${NC} chat.exchange exists (type: topic)"
else
    echo -e "${RED}❌ chat.exchange NOT found!${NC}"
    ERRORS=$((ERRORS + 1))
fi
echo ""

# 3. Check broadcast queues (HOT PATH)
echo "3. Checking broadcast queues (HOT PATH)..."
for i in {1..4}; do
    QUEUE_EXISTS=$(sudo rabbitmqctl list_queues name 2>/dev/null | grep "^server.$i.broadcast" | wc -l)
    if [ $QUEUE_EXISTS -eq 1 ]; then
        echo -e "${GREEN}  ✓${NC} server.$i.broadcast exists"
    else
        echo -e "${RED}  ❌ server.$i.broadcast NOT found!${NC}"
        ERRORS=$((ERRORS + 1))
    fi
done
echo ""

# 4. Check persistence queues (COLD PATH)
echo "4. Checking persistence queues (COLD PATH)..."
ROOM_QUEUES=$(sudo rabbitmqctl list_queues name 2>/dev/null | grep "^room\." | wc -l)
if [ $ROOM_QUEUES -eq 20 ]; then
    echo -e "${GREEN}✓${NC} All 20 room queues exist (room.1 to room.20)"
else
    echo -e "${RED}❌ Expected 20 room queues, found $ROOM_QUEUES${NC}"
    ERRORS=$((ERRORS + 1))
fi
echo ""

# 5. Check total queue count
echo "5. Checking total queue count..."
TOTAL_QUEUES=$(sudo rabbitmqctl list_queues 2>/dev/null | grep -E "(server\.|room\.)" | wc -l)
if [ $TOTAL_QUEUES -eq 24 ]; then
    echo -e "${GREEN}✓${NC} Total queues: 24 (4 broadcast + 20 persistence)"
else
    echo -e "${YELLOW}⚠${NC} Total queues: $TOTAL_QUEUES (expected 24)"
    ERRORS=$((ERRORS + 1))
fi
echo ""

# 6. Check bindings
echo "6. Checking bindings..."
TOTAL_BINDINGS=$(sudo rabbitmqctl list_bindings 2>/dev/null | grep "chat.exchange" | wc -l)
if [ $TOTAL_BINDINGS -eq 24 ]; then
    echo -e "${GREEN}✓${NC} Total bindings: 24 (4 fan-out + 20 specific)"
else
    echo -e "${YELLOW}⚠${NC} Total bindings: $TOTAL_BINDINGS (expected 24)"
    ERRORS=$((ERRORS + 1))
fi

# Check fan-out bindings specifically
FANOUT_BINDINGS=$(sudo rabbitmqctl list_bindings 2>/dev/null | grep "chat.exchange.*broadcast.*room.#" | wc -l)
echo "  Fan-out bindings: $FANOUT_BINDINGS (expected 4)"
if [ $FANOUT_BINDINGS -ne 4 ]; then
    ERRORS=$((ERRORS + 1))
fi

# Check specific bindings
SPECIFIC_BINDINGS=$(sudo rabbitmqctl list_bindings 2>/dev/null | grep "chat.exchange.*room\.[0-9]" | grep -v broadcast | wc -l)
echo "  Specific bindings: $SPECIFIC_BINDINGS (expected 20)"
if [ $SPECIFIC_BINDINGS -ne 20 ]; then
    ERRORS=$((ERRORS + 1))
fi
echo ""

# 7. Test message duplication
echo "7. Testing message duplication..."
echo "  Publishing test message to: chat.exchange (routing_key=room.5)"
sudo rabbitmqadmin publish exchange=chat.exchange routing_key=room.5 payload="test message" 2>/dev/null

sleep 2

echo "  Checking message distribution:"
DUPLICATION_COUNT=0

for i in {1..4}; do
    MSG_COUNT=$(sudo rabbitmqctl list_queues name messages 2>/dev/null | grep "server.$i.broadcast" | awk '{print $2}')
    if [ "$MSG_COUNT" -gt 0 ]; then
        echo -e "${GREEN}    ✓${NC} server.$i.broadcast received message"
        DUPLICATION_COUNT=$((DUPLICATION_COUNT + 1))
    else
        echo -e "${RED}    ❌ server.$i.broadcast did NOT receive message${NC}"
        ERRORS=$((ERRORS + 1))
    fi
done

ROOM5_COUNT=$(sudo rabbitmqctl list_queues name messages 2>/dev/null | grep "^room.5 " | awk '{print $2}')
if [ "$ROOM5_COUNT" -gt 0 ]; then
    echo -e "${GREEN}    ✓${NC} room.5 received message"
    DUPLICATION_COUNT=$((DUPLICATION_COUNT + 1))
else
    echo -e "${RED}    ❌ room.5 did NOT receive message${NC}"
    ERRORS=$((ERRORS + 1))
fi

if [ $DUPLICATION_COUNT -eq 5 ]; then
    echo -e "${GREEN}  ✓${NC} Message duplication works! (1 message → 5 queues)"
else
    echo -e "${RED}  ❌ Duplication failed (expected 5 queues, got $DUPLICATION_COUNT)${NC}"
fi
echo ""

# 8. Cleanup test messages
echo "8. Cleaning up test messages..."
for i in {1..4}; do
    sudo rabbitmqadmin purge queue name=server.$i.broadcast 2>/dev/null || true
done
sudo rabbitmqadmin purge queue name=room.5 2>/dev/null || true
echo -e "${GREEN}✓${NC} Test messages purged"
echo ""

# Final summary
echo "============================================================"
if [ $ERRORS -eq 0 ]; then
    echo -e "${GREEN}  ✅ ALL CHECKS PASSED!${NC}"
    echo "============================================================"
    echo ""
    echo "RabbitMQ is ready for Option A architecture!"
    echo ""
    echo "Summary:"
    echo "  ✓ 1 exchange (chat.exchange)"
    echo "  ✓ 4 broadcast queues (server.1-4.broadcast)"
    echo "  ✓ 20 persistence queues (room.1-20)"
    echo "  ✓ 24 bindings (4 fan-out + 20 specific)"
    echo "  ✓ Message duplication tested"
    echo ""
    echo "Next steps:"
    echo "  1. Deploy 4 servers with IDs: server-1, server-2, server-3, server-4"
    echo "  2. Each server will consume from: server.N.broadcast queue"
    echo "  3. Deploy consumer (will consume from: room.1-20 queues)"
    echo ""
else
    echo -e "${RED}  ⚠ SETUP INCOMPLETE (${ERRORS} errors)${NC}"
    echo "============================================================"
    echo ""
    echo "Please fix errors above and run again."
    echo ""
    exit 1
fi
