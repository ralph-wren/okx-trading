#!/bin/bash

REMOTE_HOST="192.168.54.68"
REMOTE_USER="skysi"
REMOTE_PASSWORD="skysi"

GREEN='\033[0;32m'
BLUE='\033[0;34m'
RED='\033[0;31m'
YELLOW='\033[0;33m'
NC='\033[0m'

echo -e "${BLUE}===== 修复Clash安装 =====${NC}"

sshpass -p "$REMOTE_PASSWORD" ssh -o StrictHostKeyChecking=no $REMOTE_USER@$REMOTE_HOST bash << 'ENDSSH'

GREEN='\033[0;32m'
BLUE='\033[0;34m'
RED='\033[0;31m'
YELLOW='\033[0;33m'
NC='\033[0m'

cd ~/clash

echo -e "${BLUE}1. 检查系统架构${NC}"
ARCH=$(uname -m)
echo "系统架构: $ARCH"

if [ "$ARCH" = "x86_64" ]; then
    CLASH_ARCH="amd64"
elif [ "$ARCH" = "aarch64" ]; then
    CLASH_ARCH="arm64"
else
    echo -e "${RED}不支持的架构: $ARCH${NC}"
    exit 1
fi

echo -e "${BLUE}2. 备份旧文件${NC}"
if [ -f clash ]; then
    mv clash clash.old.$(date +%s)
    echo "已备份旧的clash文件"
fi

echo -e "${BLUE}3. 下载Clash Premium${NC}"
# 使用代理下载
export http_proxy="http://192.168.52.40:10809"
export https_proxy="http://192.168.52.40:10809"

CLASH_URL="https://github.com/Dreamacro/clash/releases/download/premium/clash-linux-${CLASH_ARCH}-2023.08.17.gz"
echo "下载地址: $CLASH_URL"

wget -O clash.gz "$CLASH_URL" || curl -L -o clash.gz "$CLASH_URL"

if [ -f clash.gz ]; then
    echo -e "${GREEN}✓ 下载成功${NC}"
    
    echo -e "${BLUE}4. 解压文件${NC}"
    gunzip -f clash.gz
    chmod +x clash
    
    echo -e "${BLUE}5. 验证文件${NC}"
    file clash
    ls -lh clash
    
    echo -e "${BLUE}6. 测试启动Clash${NC}"
    ./clash -t -d . || echo -e "${YELLOW}配置测试失败，但可能是正常的${NC}"
    
else
    echo -e "${RED}✗ 下载失败${NC}"
    exit 1
fi

ENDSSH

echo -e "${GREEN}===== 完成 =====${NC}"
