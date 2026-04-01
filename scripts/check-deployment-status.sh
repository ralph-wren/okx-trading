#!/bin/bash

REMOTE_HOST="192.168.54.68"
REMOTE_USER="skysi"
REMOTE_PASSWORD="skysi"

GREEN='\033[0;32m'
BLUE='\033[0;34m'
RED='\033[0;31m'
YELLOW='\033[0;33m'
NC='\033[0m'

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}   OKX Trading 部署状态检查${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""

sshpass -p "$REMOTE_PASSWORD" ssh -o StrictHostKeyChecking=no $REMOTE_USER@$REMOTE_HOST bash << 'ENDSSH'

GREEN='\033[0;32m'
BLUE='\033[0;34m'
RED='\033[0;31m'
YELLOW='\033[0;33m'
NC='\033[0m'

echo -e "${BLUE}1. Docker容器状态${NC}"
echo -e "${BLUE}-------------------${NC}"
docker ps --filter "name=okx-trading" --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}" 2>&1 | grep -v "Warning"
echo ""

echo -e "${BLUE}2. MySQL连接测试${NC}"
echo -e "${BLUE}-------------------${NC}"
if docker exec okx-trading-mysql mysql -uroot -pPassword123? -e "SELECT VERSION();" 2>&1 | grep -q "8.0"; then
    echo -e "${GREEN}✓ MySQL连接成功${NC}"
    MYSQL_VERSION=$(docker exec okx-trading-mysql mysql -uroot -pPassword123? -e "SELECT VERSION();" 2>&1 | grep -v "Warning" | tail -1)
    echo -e "  版本: ${MYSQL_VERSION}"
else
    echo -e "${RED}✗ MySQL连接失败${NC}"
fi
echo ""

echo -e "${BLUE}3. Redis连接测试${NC}"
echo -e "${BLUE}-------------------${NC}"
if docker exec okx-trading-redis redis-cli ping 2>&1 | grep -q "PONG"; then
    echo -e "${GREEN}✓ Redis连接成功${NC}"
    REDIS_VERSION=$(docker exec okx-trading-redis redis-cli INFO server 2>&1 | grep "redis_version" | cut -d: -f2 | tr -d '\r')
    echo -e "  版本: ${REDIS_VERSION}"
else
    echo -e "${RED}✗ Redis连接失败${NC}"
fi
echo ""

echo -e "${BLUE}4. 数据库信息${NC}"
echo -e "${BLUE}-------------------${NC}"
echo -e "数据库名: okx_trading"
TABLE_COUNT=$(docker exec okx-trading-mysql mysql -uroot -pPassword123? -e "USE okx_trading; SHOW TABLES;" 2>&1 | grep -v "Warning" | grep -v "Tables_in" | wc -l)
echo -e "表数量: ${TABLE_COUNT}"
echo ""

echo -e "${BLUE}5. 数据库表列表${NC}"
echo -e "${BLUE}-------------------${NC}"
docker exec okx-trading-mysql mysql -uroot -pPassword123? -e "USE okx_trading; SHOW TABLES;" 2>&1 | grep -v "Warning"
echo ""

echo -e "${BLUE}6. 连接信息${NC}"
echo -e "${BLUE}-------------------${NC}"
echo -e "${GREEN}MySQL:${NC}"
echo -e "  主机: 192.168.54.68"
echo -e "  端口: 3306"
echo -e "  用户: root"
echo -e "  密码: Password123?"
echo -e "  数据库: okx_trading"
echo ""
echo -e "${GREEN}Redis:${NC}"
echo -e "  主机: 192.168.54.68"
echo -e "  端口: 6379"
echo -e "  密码: (无)"
echo ""

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}   部署状态检查完成${NC}"
echo -e "${GREEN}========================================${NC}"

ENDSSH
