#!/bin/bash

REMOTE_HOST="192.168.54.68"
REMOTE_USER="skysi"
REMOTE_PASSWORD="skysi"

GREEN='\033[0;32m'
BLUE='\033[0;34m'
RED='\033[0;31m'
YELLOW='\033[0;33m'
NC='\033[0m'

echo -e "${BLUE}===== 验证代理配置 =====${NC}"

sshpass -p "$REMOTE_PASSWORD" ssh -o StrictHostKeyChecking=no $REMOTE_USER@$REMOTE_HOST bash << 'ENDSSH'

GREEN='\033[0;32m'
BLUE='\033[0;34m'
RED='\033[0;31m'
YELLOW='\033[0;33m'
NC='\033[0m'

# 加载环境变量
source ~/.bashrc

echo -e "${BLUE}1. 检查环境变量${NC}"
echo "http_proxy: $http_proxy"
echo "https_proxy: $https_proxy"
echo "all_proxy: $all_proxy"
echo ""

echo -e "${BLUE}2. 检查Clash状态${NC}"
if ps aux | grep -v grep | grep "./clash" > /dev/null; then
    echo -e "${GREEN}✓ Clash正在运行${NC}"
else
    echo -e "${RED}✗ Clash未运行，正在启动...${NC}"
    cd ~/clash && nohup ./clash -d . > clash.log 2>&1 &
    sleep 5
fi
echo ""

echo -e "${BLUE}3. 测试代理连接${NC}"

echo "测试Google..."
if curl -s --connect-timeout 10 https://www.google.com > /dev/null 2>&1; then
    echo -e "${GREEN}✓ 可以访问Google${NC}"
else
    echo -e "${RED}✗ 无法访问Google${NC}"
fi

echo "测试GitHub..."
if curl -s --connect-timeout 10 https://github.com > /dev/null 2>&1; then
    echo -e "${GREEN}✓ 可以访问GitHub${NC}"
else
    echo -e "${RED}✗ 无法访问GitHub${NC}"
fi

echo "获取代理IP..."
PROXY_IP=$(curl -s --connect-timeout 10 https://api.ipify.org 2>/dev/null)
if [ -n "$PROXY_IP" ]; then
    echo -e "${GREEN}✓ 当前IP: $PROXY_IP${NC}"
else
    echo -e "${RED}✗ 无法获取IP${NC}"
fi
echo ""

echo -e "${BLUE}4. 测试Git代理${NC}"
git config --global --get http.proxy
git config --global --get https.proxy
echo ""

echo -e "${BLUE}5. 检查配置文件${NC}"
echo "~/.bashrc 代理配置:"
grep -A 5 "Clash Proxy Configuration" ~/.bashrc
echo ""

echo -e "${BLUE}6. 测试wget下载${NC}"
if wget -q --spider --timeout=10 https://www.google.com 2>/dev/null; then
    echo -e "${GREEN}✓ wget可以使用代理${NC}"
else
    echo -e "${RED}✗ wget无法使用代理${NC}"
fi
echo ""

echo -e "${GREEN}===== 验证完成 =====${NC}"
echo ""
echo -e "${BLUE}提示:${NC}"
echo "- 新开的SSH会话会自动使用代理"
echo "- 当前会话需要执行: source ~/.bashrc"
echo "- 查看代理状态: echo \$http_proxy"
echo "- 临时禁用代理: unset http_proxy https_proxy all_proxy"

ENDSSH
