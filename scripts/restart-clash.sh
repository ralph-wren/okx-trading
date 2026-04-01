#!/bin/bash

REMOTE_HOST="192.168.54.68"
REMOTE_USER="skysi"
REMOTE_PASSWORD="skysi"

GREEN='\033[0;32m'
BLUE='\033[0;34m'
RED='\033[0;31m'
NC='\033[0m'

echo -e "${BLUE}===== 重启Clash =====${NC}"

sshpass -p "$REMOTE_PASSWORD" ssh -o StrictHostKeyChecking=no $REMOTE_USER@$REMOTE_HOST bash << 'ENDSSH'

GREEN='\033[0;32m'
BLUE='\033[0;34m'
RED='\033[0;31m'
YELLOW='\033[0;33m'
NC='\033[0m'

cd ~/clash

echo -e "${BLUE}1. 停止旧进程${NC}"
pkill -f "./clash" 2>/dev/null
sleep 2

echo -e "${BLUE}2. 删除损坏的GeoIP文件${NC}"
rm -f geoip.metadb geosite.dat Country.mmdb
echo "已删除旧的地理数据库文件"

echo -e "${BLUE}3. 使用代理下载GeoIP数据库${NC}"
export http_proxy="http://192.168.52.40:10809"
export https_proxy="http://192.168.52.40:10809"

wget -O geoip.metadb https://github.com/MetaCubeX/meta-rules-dat/releases/download/latest/geoip.metadb || \
curl -L -o geoip.metadb https://github.com/MetaCubeX/meta-rules-dat/releases/download/latest/geoip.metadb

if [ -f geoip.metadb ] && [ -s geoip.metadb ]; then
    echo -e "${GREEN}✓ GeoIP数据库下载成功${NC}"
    ls -lh geoip.metadb
else
    echo -e "${YELLOW}⚠ GeoIP下载失败，但Clash仍可运行（部分功能受限）${NC}"
fi

unset http_proxy https_proxy

echo -e "${BLUE}4. 启动Clash${NC}"
nohup ./clash -d . > clash.log 2>&1 &
CLASH_PID=$!
echo "Clash PID: $CLASH_PID"
sleep 5

echo -e "${BLUE}5. 检查状态${NC}"
if ps -p $CLASH_PID > /dev/null 2>&1; then
    echo -e "${GREEN}✓ Clash运行正常${NC}"
    
    # 显示日志
    echo -e "${BLUE}最新日志:${NC}"
    tail -10 clash.log
    
    echo ""
    echo -e "${GREEN}===== Clash已启动 =====${NC}"
    echo -e "${BLUE}HTTP代理:${NC}   http://192.168.54.68:7890"
    echo -e "${BLUE}SOCKS5代理:${NC} socks5://192.168.54.68:7891"
    echo -e "${BLUE}控制面板:${NC}   http://192.168.54.68:9090"
else
    echo -e "${RED}✗ Clash启动失败${NC}"
    echo "完整日志:"
    cat clash.log
    exit 1
fi

ENDSSH

echo -e "${GREEN}===== 完成 =====${NC}"
