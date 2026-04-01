#!/bin/bash

REMOTE_HOST="192.168.54.68"
REMOTE_USER="skysi"
REMOTE_PASSWORD="skysi"

GREEN='\033[0;32m'
BLUE='\033[0;34m'
RED='\033[0;31m'
YELLOW='\033[0;33m'
NC='\033[0m'

echo -e "${BLUE}===== 检查应用日志和Clash状态 =====${NC}"

sshpass -p "$REMOTE_PASSWORD" ssh -o StrictHostKeyChecking=no $REMOTE_USER@$REMOTE_HOST bash << 'ENDSSH'

GREEN='\033[0;32m'
BLUE='\033[0;34m'
RED='\033[0;31m'
YELLOW='\033[0;33m'
NC='\033[0m'

echo -e "${BLUE}1. 应用进程详情${NC}"
ps aux | grep -v grep | grep "okx-trading"
echo ""

echo -e "${BLUE}2. 检查应用端口${NC}"
netstat -tulpn 2>/dev/null | grep 8088 || ss -tulpn 2>/dev/null | grep 8088
echo ""

echo -e "${BLUE}3. 应用日志目录${NC}"
cd ~/okx-trading
pwd
ls -lh logs/ 2>/dev/null || echo "logs目录为空或不存在"
echo ""

echo -e "${BLUE}4. 最新应用日志 (最后30行)${NC}"
if [ -f logs/startup.log ]; then
    tail -30 logs/startup.log
elif [ -f nohup.out ]; then
    tail -30 nohup.out
else
    echo "未找到日志文件"
fi
echo ""

echo -e "${BLUE}5. Clash状态检查${NC}"
cd ~/clash
echo "Clash文件:"
ls -lh
echo ""

echo -e "${BLUE}6. 检查Clash进程${NC}"
if ps aux | grep -v grep | grep clash; then
    echo -e "${GREEN}✓ Clash正在运行${NC}"
else
    echo -e "${YELLOW}Clash未运行${NC}"
fi
echo ""

echo -e "${BLUE}7. 尝试启动Clash${NC}"
if [ -f ~/clash/clash ]; then
    chmod +x ~/clash/clash
    echo "Clash可执行文件权限已设置"
    
    # 检查配置文件
    if [ -f ~/clash/config.yaml ]; then
        echo -e "${GREEN}✓ config.yaml存在${NC}"
        echo "配置文件前20行:"
        head -20 ~/clash/config.yaml
    else
        echo -e "${RED}✗ config.yaml不存在${NC}"
    fi
else
    echo -e "${RED}✗ clash可执行文件不存在${NC}"
fi

ENDSSH
