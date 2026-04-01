#!/bin/bash

REMOTE_HOST="192.168.54.68"
REMOTE_USER="skysi"
REMOTE_PASSWORD="skysi"

GREEN='\033[0;32m'
BLUE='\033[0;34m'
RED='\033[0;31m'
YELLOW='\033[0;33m'
NC='\033[0m'

echo -e "${BLUE}===== 测试Clash代理 =====${NC}"

sshpass -p "$REMOTE_PASSWORD" ssh -o StrictHostKeyChecking=no $REMOTE_USER@$REMOTE_HOST bash << 'ENDSSH'

GREEN='\033[0;32m'
BLUE='\033[0;34m'
RED='\033[0;31m'
YELLOW='\033[0;33m'
NC='\033[0m'

echo -e "${BLUE}1. Clash进程状态${NC}"
if ps aux | grep -v grep | grep "./clash" > /dev/null; then
    echo -e "${GREEN}✓ Clash正在运行${NC}"
    ps aux | grep -v grep | grep "./clash"
else
    echo -e "${RED}✗ Clash未运行${NC}"
    exit 1
fi
echo ""

echo -e "${BLUE}2. 端口监听状态${NC}"
netstat -tulpn 2>/dev/null | grep -E "7890|7891|9090" || ss -tulpn 2>/dev/null | grep -E "7890|7891|9090"
echo ""

echo -e "${BLUE}3. 测试HTTP代理${NC}"
if curl -x http://127.0.0.1:7890 -s --connect-timeout 10 https://www.google.com > /dev/null 2>&1; then
    echo -e "${GREEN}✓ HTTP代理工作正常${NC}"
else
    echo -e "${YELLOW}⚠ HTTP代理测试失败（可能是网络问题或Clash还在初始化）${NC}"
fi
echo ""

echo -e "${BLUE}4. 测试获取IP${NC}"
PROXY_IP=$(curl -x http://127.0.0.1:7890 -s --connect-timeout 10 https://api.ipify.org 2>/dev/null)
if [ -n "$PROXY_IP" ]; then
    echo -e "${GREEN}✓ 通过代理获取到IP: $PROXY_IP${NC}"
else
    echo -e "${YELLOW}⚠ 无法通过代理获取IP${NC}"
fi
echo ""

echo -e "${BLUE}5. Clash日志（最后20行）${NC}"
tail -20 ~/clash/clash.log
echo ""

echo -e "${GREEN}===== Clash信息 =====${NC}"
echo -e "${BLUE}HTTP代理:${NC}   http://192.168.54.68:7890"
echo -e "${BLUE}SOCKS5代理:${NC} socks5://192.168.54.68:7891"
echo -e "${BLUE}控制面板:${NC}   http://192.168.54.68:9090"
echo ""
echo -e "${BLUE}启动脚本:${NC}   ~/clash/start.sh"
echo -e "${BLUE}停止命令:${NC}   pkill -f './clash'"

ENDSSH
