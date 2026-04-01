#!/bin/bash

REMOTE_HOST="192.168.54.68"
REMOTE_USER="skysi"
REMOTE_PASSWORD="skysi"

GREEN='\033[0;32m'
BLUE='\033[0;34m'
RED='\033[0;31m'
YELLOW='\033[0;33m'
NC='\033[0m'

echo -e "${BLUE}===== 测试新会话代理（模拟新登录）=====${NC}"

# 使用 -t 强制分配伪终端，模拟交互式登录
sshpass -p "$REMOTE_PASSWORD" ssh -t -o StrictHostKeyChecking=no $REMOTE_USER@$REMOTE_HOST bash -l << 'ENDSSH'

GREEN='\033[0;32m'
BLUE='\033[0;34m'
RED='\033[0;31m'
YELLOW='\033[0;33m'
NC='\033[0m'

echo -e "${BLUE}===== 新会话代理测试 =====${NC}"
echo ""

echo -e "${BLUE}1. 环境变量${NC}"
echo "http_proxy: $http_proxy"
echo "https_proxy: $https_proxy"
echo ""

echo -e "${BLUE}2. 测试访问${NC}"

echo "测试Google..."
if timeout 10 curl -s https://www.google.com > /dev/null 2>&1; then
    echo -e "${GREEN}✓ 可以访问Google${NC}"
else
    echo -e "${YELLOW}⚠ 无法访问Google（可能需要等待）${NC}"
fi

echo "获取IP地址..."
PROXY_IP=$(timeout 10 curl -s https://api.ipify.org 2>/dev/null)
if [ -n "$PROXY_IP" ]; then
    echo -e "${GREEN}✓ 当前IP: $PROXY_IP${NC}"
else
    echo -e "${YELLOW}⚠ 无法获取IP${NC}"
fi

echo ""
echo -e "${BLUE}3. 测试命令${NC}"
echo "wget测试..."
if timeout 10 wget -q --spider https://www.google.com 2>/dev/null; then
    echo -e "${GREEN}✓ wget可以使用代理${NC}"
else
    echo -e "${YELLOW}⚠ wget测试失败${NC}"
fi

echo ""
echo -e "${GREEN}===== 测试完成 =====${NC}"

exit
ENDSSH

echo ""
echo -e "${GREEN}===== 完成 =====${NC}"
