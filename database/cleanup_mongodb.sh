#!/bin/bash
# cleanup_mongodb.sh - Clean MongoDB data for fresh test
# Run this on MongoDB EC2 instance before each new test

echo "============================================================"
echo "  MongoDB Cleanup - Clear Data for New Test"
echo "============================================================"
echo ""

# Colors
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m'

# Check if MongoDB is running
if ! sudo systemctl is-active --quiet mongod; then
    echo -e "${RED}❌ MongoDB is not running!${NC}"
    echo "Start it with: sudo systemctl start mongod"
    exit 1
fi

echo -e "${YELLOW}⚠ WARNING: This will delete ALL messages in chat_system database!${NC}"
echo ""
read -p "Are you sure? Type 'yes' to continue: " CONFIRM

if [ "$CONFIRM" != "yes" ]; then
    echo "Cancelled."
    exit 0
fi

echo ""
echo "Cleaning database..."

# Get counts before cleanup
BEFORE_MESSAGES=$(mongosh --quiet --eval "db.messages.countDocuments()" chat_system 2>/dev/null || echo "0")
BEFORE_DLQ=$(mongosh --quiet --eval "db.failed_writes.countDocuments()" chat_system 2>/dev/null || echo "0")

echo "Before cleanup:"
echo "  Messages: $BEFORE_MESSAGES"
echo "  Failed writes (DLQ): $BEFORE_DLQ"
echo ""

# FIX: Removed 'use chat_system;' and purely used JS
mongosh --quiet --eval "
  db.messages.deleteMany({});
  db.failed_writes.deleteMany({});
  print('✓ All documents deleted');
" chat_system

# Verify cleanup
AFTER_MESSAGES=$(mongosh --quiet --eval "db.messages.countDocuments()" chat_system 2>/dev/null)
AFTER_DLQ=$(mongosh --quiet --eval "db.failed_writes.countDocuments()" chat_system 2>/dev/null)

echo ""
echo "After cleanup:"
echo "  Messages: $AFTER_MESSAGES"
echo "  Failed writes (DLQ): $AFTER_DLQ"
echo ""

# Check indexes still exist
INDEX_COUNT=$(mongosh --quiet --eval "db.messages.getIndexes().length" chat_system 2>/dev/null || echo "0")
echo "Indexes preserved: $INDEX_COUNT"
echo ""

if [ "$AFTER_MESSAGES" -eq 0 ] && [ "$AFTER_DLQ" -eq 0 ]; then
    echo -e "${GREEN}✅ Cleanup complete!${NC}"
    echo ""
    echo "Database is ready for new test run."
else
    echo -e "${RED}❌ Cleanup failed${NC}"
    exit 1
fi