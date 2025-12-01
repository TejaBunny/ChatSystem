#!/bin/bash
# verify_mongodb.sh - Verify MongoDB setup and test from Consumer
# Run this on MongoDB EC2 instance

echo "============================================================"
echo "  MongoDB Setup Verification"
echo "============================================================"
echo ""

# Colors
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m'

ERRORS=0

# ============================================================
# Check 1: MongoDB Service Running
# ============================================================
echo "1. Checking MongoDB service..."
if sudo systemctl is-active --quiet mongod; then
    echo -e "${GREEN}✓${NC} MongoDB is running"
else
    echo -e "${RED}❌ MongoDB is NOT running${NC}"
    echo "  Start with: sudo systemctl start mongod"
    ERRORS=$((ERRORS + 1))
fi
echo ""

# ============================================================
# Check 2: MongoDB Configuration
# ============================================================
echo "2. Checking MongoDB configuration..."
BIND_IP=$(sudo grep "bindIp: " /etc/mongod.conf | grep -v "#" | awk '{print $2}')
echo "  bindIp: $BIND_IP"

if [[ $BIND_IP == "0.0.0.0" ]]; then
    echo -e "${GREEN}✓${NC} Configured for remote access"
else
    echo -e "${RED}❌ Not configured for remote access${NC}"
    echo "  Current: $BIND_IP"
    echo "  Should be: 0.0.0.0"
    ERRORS=$((ERRORS + 1))
fi
echo ""

# ============================================================
# Check 3: MongoDB Listening on Port 27017
# ============================================================
echo "3. Checking MongoDB network binding..."
LISTENING=$(sudo ss -tulpn | grep 27017)

if [[ $LISTENING == *"0.0.0.0:27017"* ]]; then
    echo -e "${GREEN}✓${NC} MongoDB listening on all interfaces (0.0.0.0:27017)"
else
    echo -e "${RED}❌ MongoDB not listening correctly${NC}"
    echo "  Current: $LISTENING"
    ERRORS=$((ERRORS + 1))
fi
echo ""

# ============================================================
# Check 4: Local Connection Test
# ============================================================
echo "4. Testing local MongoDB connection..."
if mongosh --quiet --eval "db.version()" chat_system &>/dev/null; then
    VERSION=$(mongosh --quiet --eval "db.version()" chat_system)
    echo -e "${GREEN}✓${NC} Local connection successful"
    echo "  MongoDB version: $VERSION"
else
    echo -e "${RED}❌ Cannot connect to MongoDB locally${NC}"
    ERRORS=$((ERRORS + 1))
fi
echo ""

# ============================================================
# Check 5: Database Exists
# ============================================================
echo "5. Checking database..."
DB_EXISTS=$(mongosh --quiet --eval "db.getName()" chat_system)
if [ "$DB_EXISTS" == "chat_system" ]; then
    echo -e "${GREEN}✓${NC} Database 'chat_system' exists"
else
    echo -e "${YELLOW}⚠${NC} Database not found (will be created by consumer)"
fi
echo ""

# ============================================================
# Check 6: Collections (if data exists)
# ============================================================
echo "6. Checking collections..."
COLLECTIONS=$(mongosh --quiet --eval "db.getCollectionNames()" chat_system 2>/dev/null)
if [[ $COLLECTIONS == *"messages"* ]]; then
    MSG_COUNT=$(mongosh --quiet --eval "db.messages.countDocuments()" chat_system)
    echo -e "${GREEN}✓${NC} Collection 'messages' exists ($MSG_COUNT documents)"
    
    # Check indexes
    INDEX_COUNT=$(mongosh --quiet --eval "db.messages.getIndexes().length" chat_system)
    echo "  Indexes: $INDEX_COUNT"
else
    echo -e "${YELLOW}⚠${NC} Collections not created yet (normal - consumer will create)"
fi
echo ""

# ============================================================
# Check 7: Server Info
# ============================================================
echo "7. Server information..."
PRIVATE_IP=$(curl -s http://169.254.169.254/latest/meta-data/local-ipv4 2>/dev/null || echo "localhost")
PUBLIC_IP=$(curl -s http://169.254.169.254/latest/meta-data/public-ipv4 2>/dev/null || echo "N/A")

echo "  Private IP: $PRIVATE_IP (use this for consumer)"
echo "  Public IP: $PUBLIC_IP"
echo ""

# ============================================================
# Final Summary
# ============================================================
echo "============================================================"
if [ $ERRORS -eq 0 ]; then
    echo -e "${GREEN}  ✅ MongoDB Ready!"
    echo "============================================================"
    echo ""
    echo "Configuration:"
    echo "  ✓ MongoDB running and accessible"
    echo "  ✓ Remote connections enabled (0.0.0.0:27017)"
    echo "  ✓ Database: chat_system"
    echo ""
    echo "Connection string for consumer:"
    echo "  mongodb://$PRIVATE_IP:27017"
    echo ""
    echo "Next steps:"
    echo "  1. Configure Security Group (allow port 27017 from Consumer)"
    echo "  2. Test from consumer:"
    echo "     ssh consumer-instance"
    echo "     mongosh mongodb://$PRIVATE_IP:27017/chat_system"
    echo "  3. Start consumer with:"
    echo "     --mongoHost=$PRIVATE_IP"
    echo ""
    echo "Consumer will automatically create:"
    echo "  - Collection: messages"
    echo "  - Collection: failed_writes"
    echo "  - 5 indexes on messages"
    echo "============================================================"
else
    echo -e "${RED}  ⚠ Setup Incomplete ($ERRORS errors)"
    echo "============================================================"
    echo ""
    echo "Please fix errors above before proceeding."
    echo "============================================================"
    exit 1
fi
