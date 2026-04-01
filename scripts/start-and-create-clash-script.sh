#!/bin/bash

REMOTE_HOST="192.168.54.68"
REMOTE_USER="skysi"
REMOTE_PASSWORD="skysi"

GREEN='\033[0;32m'
BLUE='\033[0;34m'
RED='\033[0;31m'
YELLOW='\033[0;33m'
NC='\033[0m'

echo -e "${BLUE}===== 启动Clash并创建启动脚本 =====${NC}"

sshpass -p "$REMOTE_PASSWORD" ssh -o StrictHostKeyChecking=no $REMOTE_USER@$REMOTE_HOST bash << 'ENDSSH'

GREEN='\033[0;32m'
BLUE='\033[0;34m'
RED='\033[0;31m'
YELLOW='\033[0;33m'
NC='\033[0m'

cd ~/clash

echo -e "${BLUE}1. 检查Clash是否已在运行${NC}"
if ps aux | grep -v grep | grep "./clash" > /dev/null; then
    echo -e "${YELLOW}Clash已在运行，先停止...${NC}"
    pkill -f "./clash"
    sleep 2
fi

echo -e "${BLUE}2. 启动Clash${NC}"
nohup ./clash -d . > clash.log 2>&1 &
CLASH_PID=$!
echo -e "${GREEN}Clash已启动，PID: $CLASH_PID${NC}"
sleep 5

echo -e "${BLUE}3. 验证Clash状态${NC}"
if ps -p $CLASH_PID > /dev/null 2>&1; then
    echo -e "${GREEN}✓ Clash运行正常${NC}"
    
    # 检查端口
    echo -e "${BLUE}检查端口监听:${NC}"
    netstat -tulpn 2>/dev/null | grep -E "7890|7891|9090" || ss -tulpn 2>/dev/null | grep -E "7890|7891|9090"
    
    # 测试代理
    echo -e "${BLUE}测试HTTP代理:${NC}"
    if curl -x http://127.0.0.1:7890 -s --connect-timeout 5 https://www.google.com > /dev/null 2>&1; then
        echo -e "${GREEN}✓ HTTP代理工作正常${NC}"
    else
        echo -e "${YELLOW}HTTP代理测试失败（可能需要等待Clash完全启动）${NC}"
    fi
    
    # 显示日志
    echo -e "${BLUE}Clash日志（最后15行）:${NC}"
    tail -15 clash.log
else
    echo -e "${RED}✗ Clash启动失败${NC}"
    cat clash.log
    exit 1
fi

echo -e "${BLUE}4. 创建启动脚本${NC}"
cat > start.sh << 'EOF'
#!/bin/bash

# Clash 启动脚本
# 用途：启动Clash代理服务

GREEN='\033[0;32m'
BLUE='\033[0;34m'
RED='\033[0;31m'
YELLOW='\033[0;33m'
NC='\033[0m'

cd ~/clash

echo -e "${BLUE}===== Clash 启动脚本 =====${NC}"

# 检查是否已在运行
if ps aux | grep -v grep | grep "./clash -d" > /dev/null; then
    echo -e "${YELLOW}Clash已在运行${NC}"
    ps aux | grep -v grep | grep "./clash"
    echo ""
    echo -e "${GREEN}代理地址:${NC}"
    echo "  HTTP:   http://192.168.54.68:7890"
    echo "  SOCKS5: socks5://192.168.54.68:7891"
    echo "  控制面板: http://192.168.54.68:9090"
    exit 0
fi

# 启动Clash
echo -e "${BLUE}正在启动Clash...${NC}"
nohup ./clash -d . > clash.log 2>&1 &
CLASH_PID=$!

sleep 3

# 验证启动
if ps -p $CLASH_PID > /dev/null 2>&1; then
    echo -e "${GREEN}✓ Clash启动成功！${NC}"
    echo -e "  PID: $CLASH_PID"
    echo ""
    echo -e "${GREEN}代理地址:${NC}"
    echo "  HTTP:   http://192.168.54.68:7890"
    echo "  SOCKS5: socks5://192.168.54.68:7891"
    echo "  控制面板: http://192.168.54.68:9090"
    echo ""
    echo -e "${BLUE}环境变量配置:${NC}"
    echo "  export http_proxy=http://127.0.0.1:7890"
    echo "  export https_proxy=http://127.0.0.1:7890"
    echo "  export all_proxy=socks5://127.0.0.1:7891"
    echo ""
    echo -e "${BLUE}查看日志:${NC} tail -f ~/clash/clash.log"
    echo -e "${BLUE}停止服务:${NC} pkill -f './clash'"
else
    echo -e "${RED}✗ Clash启动失败${NC}"
    echo "查看日志: cat ~/clash/clash.log"
    exit 1
fi
EOF

chmod +x start.sh

echo -e "${GREEN}✓ 启动脚本已创建: ~/clash/start.sh${NC}"
echo ""
echo -e "${BLUE}使用方法:${NC}"
echo "  cd ~/clash && ./start.sh"
echo ""
echo -e "${BLUE}当前Clash状态:${NC}"
echo -e "${GREEN}代理地址:${NC}"
echo "  HTTP:   http://192.168.54.68:7890"
echo "  SOCKS5: socks5://192.168.54.68:7891"
echo "  控制面板: http://192.168.54.68:9090"

ENDSSH

echo -e "${GREEN}===== 完成 =====${NC}"
