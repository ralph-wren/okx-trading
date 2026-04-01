#!/bin/bash

REMOTE_HOST="192.168.54.68"
REMOTE_USER="skysi"
REMOTE_PASSWORD="skysi"

GREEN='\033[0;32m'
BLUE='\033[0;34m'
RED='\033[0;31m'
YELLOW='\033[0;33m'
NC='\033[0m'

echo -e "${BLUE}===== 检查远程应用状态 =====${NC}"

sshpass -p "$REMOTE_PASSWORD" ssh -o StrictHostKeyChecking=no $REMOTE_USER@$REMOTE_HOST bash << 'ENDSSH'

GREEN='\033[0;32m'
BLUE='\033[0;34m'
RED='\033[0;31m'
YELLOW='\033[0;33m'
NC='\033[0m'

echo -e "${BLUE}1. 检查应用目录${NC}"
if [ -d ~/okx-trading ]; then
    echo -e "${GREEN}✓ 目录存在: ~/okx-trading${NC}"
    ls -lh ~/okx-trading/*.jar 2>/dev/null || echo -e "${YELLOW}未找到jar文件${NC}"
else
    echo -e "${RED}✗ 目录不存在: ~/okx-trading${NC}"
fi
echo ""

echo -e "${BLUE}2. 检查应用进程${NC}"
if ps aux | grep -v grep | grep "okx-trading" | grep "java"; then
    echo -e "${GREEN}✓ 应用正在运行${NC}"
else
    echo -e "${YELLOW}应用未运行${NC}"
fi
echo ""

echo -e "${BLUE}3. 检查启动脚本${NC}"
if [ -f ~/okx-trading/start.sh ]; then
    echo -e "${GREEN}✓ start.sh 存在${NC}"
else
    echo -e "${YELLOW}start.sh 不存在${NC}"
fi
echo ""

echo -e "${BLUE}4. 检查日志文件${NC}"
if [ -d ~/okx-trading/logs ]; then
    echo -e "${GREEN}✓ logs目录存在${NC}"
    echo "最新日志文件:"
    ls -lht ~/okx-trading/logs/*.log 2>/dev/null | head -5
else
    echo -e "${YELLOW}logs目录不存在${NC}"
fi
echo ""

echo -e "${BLUE}5. 检查Clash VPN目录${NC}"
if [ -d ~/clash ]; then
    echo -e "${GREEN}✓ clash目录存在${NC}"
    ls -lh ~/clash/
else
    echo -e "${YELLOW}clash目录不存在${NC}"
fi

ENDSSH
