#!/bin/bash

REMOTE_HOST="192.168.54.68"
REMOTE_USER="skysi"
REMOTE_PASSWORD="skysi"

GREEN='\033[0;32m'
BLUE='\033[0;34m'
RED='\033[0;31m'
YELLOW='\033[0;33m'
NC='\033[0m'

echo -e "${BLUE}===== 安装Clash Meta (mihomo) =====${NC}"

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

echo -e "${BLUE}2. 下载Clash Meta${NC}"
export http_proxy="http://192.168.52.40:10809"
export https_proxy="http://192.168.52.40:10809"

# Clash Meta最新版本
CLASH_URL="https://github.com/MetaCubeX/mihomo/releases/download/v1.18.1/mihomo-linux-arm64-v1.18.1.gz"
echo "下载地址: $CLASH_URL"

wget -O mihomo.gz "$CLASH_URL"

if [ -f mihomo.gz ]; then
    echo -e "${GREEN}✓ 下载成功${NC}"
    
    echo -e "${BLUE}3. 解压文件${NC}"
    gunzip -f mihomo.gz
    mv mihomo clash
    chmod +x clash
    
    echo -e "${BLUE}4. 验证文件${NC}"
    file clash
    ls -lh clash
    
    echo -e "${BLUE}5. 测试Clash版本${NC}"
    ./clash -v
    
else
    echo -e "${RED}✗ 下载失败${NC}"
    exit 1
fi

ENDSSH

echo -e "${GREEN}===== 完成 =====${NC}"
