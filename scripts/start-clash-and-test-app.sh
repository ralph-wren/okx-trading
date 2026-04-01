#!/bin/bash

REMOTE_HOST="192.168.54.68"
REMOTE_USER="skysi"
REMOTE_PASSWORD="skysi"

GREEN='\033[0;32m'
BLUE='\033[0;34m'
RED='\033[0;31m'
YELLOW='\033[0;33m'
NC='\033[0m'

echo -e "${BLUE}===== 启动Clash并测试应用 =====${NC}"

sshpass -p "$REMOTE_PASSWORD" ssh -o StrictHostKeyChecking=no $REMOTE_USER@$REMOTE_HOST bash << 'ENDSSH'

GREEN='\033[0;32m'
BLUE='\033[0;34m'
RED='\033[0;31m'
YELLOW='\033[0;33m'
NC='\033[0m'

echo -e "${BLUE}1. 启动Clash${NC}"
cd ~/clash

# 检查Clash是否已经在运行
if ps aux | grep -v grep | grep "./clash" > /dev/null; then
    echo -e "${YELLOW}Clash已经在运行，先停止...${NC}"
    pkill -f "./clash"
    sleep 2
fi

# 后台启动Clash
nohup ./clash -d . > clash.log 2>&1 &
CLASH_PID=$!
echo -e "${GREEN}Clash已启动，PID: $CLASH_PID${NC}"
sleep 3

# 检查Clash是否成功启动
if ps -p $CLASH_PID > /dev/null; then
    echo -e "${GREEN}✓ Clash运行正常${NC}"
    
    # 检查Clash端口
    echo -e "${BLUE}Clash端口状态:${NC}"
    netstat -tulpn 2>/dev/null | grep -E "7890|7891|9090" || ss -tulpn 2>/dev/null | grep -E "7890|7891|9090"
    
    # 显示Clash日志
    echo -e "${BLUE}Clash日志 (最后10行):${NC}"
    tail -10 clash.log
else
    echo -e "${RED}✗ Clash启动失败${NC}"
    cat clash.log
fi
echo ""

echo -e "${BLUE}2. 测试应用健康检查${NC}"
cd ~/okx-trading

# 测试应用是否响应
if curl -s http://localhost:8088/actuator/health > /dev/null 2>&1; then
    echo -e "${GREEN}✓ 应用健康检查通过${NC}"
    curl -s http://localhost:8088/actuator/health
elif curl -s http://localhost:8088/ > /dev/null 2>&1; then
    echo -e "${GREEN}✓ 应用响应正常${NC}"
else
    echo -e "${YELLOW}应用可能还在启动中...${NC}"
fi
echo ""

echo -e "${BLUE}3. 检查应用日志${NC}"
# 查找最新的日志文件
if [ -f logs/all/all.log ]; then
    echo "应用日志 (最后20行):"
    tail -20 logs/all/all.log
elif [ -f nohup.out ]; then
    echo "nohup日志 (最后20行):"
    tail -20 nohup.out
else
    echo "查找进程输出..."
    # 尝试从进程输出获取信息
    ps aux | grep java | grep okx-trading
fi
echo ""

echo -e "${BLUE}4. 服务访问信息${NC}"
echo -e "${GREEN}应用地址:${NC} http://192.168.54.68:8088"
echo -e "${GREEN}Clash代理:${NC}"
echo -e "  HTTP代理: 192.168.54.68:7890"
echo -e "  SOCKS5代理: 192.168.54.68:7891"
echo -e "  控制面板: http://192.168.54.68:9090"
echo ""

echo -e "${BLUE}5. 设置系统代理环境变量${NC}"
echo "export http_proxy=http://127.0.0.1:7890"
echo "export https_proxy=http://127.0.0.1:7890"
echo "export all_proxy=socks5://127.0.0.1:7891"

ENDSSH

echo -e "${GREEN}===== 完成 =====${NC}"
