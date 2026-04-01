#!/bin/bash

# 远程服务器配置
REMOTE_HOST="192.168.54.68"
REMOTE_USER="skysi"
REMOTE_PASSWORD="skysi"

# 颜色定义
GREEN='\033[0;32m'
BLUE='\033[0;34m'
RED='\033[0;31m'
YELLOW='\033[0;33m'
NC='\033[0m' # No Color

echo -e "${BLUE}===== 开始在远程服务器部署MySQL和Redis =====${NC}"

# 使用sshpass连接远程服务器并执行部署命令（使用sudo）
sshpass -p "$REMOTE_PASSWORD" ssh -o StrictHostKeyChecking=no $REMOTE_USER@$REMOTE_HOST << 'ENDSSH'

# 颜色定义
GREEN='\033[0;32m'
BLUE='\033[0;34m'
RED='\033[0;31m'
YELLOW='\033[0;33m'
NC='\033[0m'

echo -e "${BLUE}===== 检查Docker是否安装 =====${NC}"
if ! command -v docker &> /dev/null; then
    echo -e "${RED}Docker未安装，请先安装Docker${NC}"
    exit 1
else
    echo -e "${GREEN}Docker已安装${NC}"
fi

# 确保当前用户可以使用docker（添加到docker组）
if ! docker ps &> /dev/null; then
    echo -e "${YELLOW}当前用户无Docker权限，尝试添加到docker组...${NC}"
    echo "skysi" | sudo -S usermod -aG docker $USER
    echo -e "${YELLOW}需要重新登录才能生效，使用sudo运行docker命令${NC}"
fi

# 创建部署目录
echo -e "${BLUE}===== 创建部署目录 =====${NC}"
mkdir -p ~/okx-trading-db/mysql/init
mkdir -p ~/okx-trading-db/mysql/data
mkdir -p ~/okx-trading-db/redis/data

# 停止并删除旧容器（如果存在）
echo -e "${BLUE}===== 停止并删除旧容器 =====${NC}"
echo "skysi" | sudo -S docker stop okx-trading-mysql okx-trading-redis 2>/dev/null || true
echo "skysi" | sudo -S docker rm okx-trading-mysql okx-trading-redis 2>/dev/null || true

# 创建网络
echo -e "${BLUE}===== 创建Docker网络 =====${NC}"
echo "skysi" | sudo -S docker network create okx-network 2>/dev/null || echo "网络已存在"

# 启动MySQL
echo -e "${BLUE}===== 启动MySQL容器 =====${NC}"
echo "skysi" | sudo -S docker run -d \
  --name okx-trading-mysql \
  --network okx-network \
  --restart always \
  -p 3306:3306 \
  -e MYSQL_ROOT_PASSWORD=Password123? \
  -e MYSQL_DATABASE=okx_trading \
  -e TZ=Asia/Shanghai \
  -v ~/okx-trading-db/mysql/data:/var/lib/mysql \
  -v ~/okx-trading-db/mysql/init:/docker-entrypoint-initdb.d \
  mysql:8.0 \
  --character-set-server=utf8mb4 \
  --collation-server=utf8mb4_unicode_ci \
  --default-time-zone=+8:00 \
  --default-authentication-plugin=mysql_native_password

# 启动Redis
echo -e "${BLUE}===== 启动Redis容器 =====${NC}"
echo "skysi" | sudo -S docker run -d \
  --name okx-trading-redis \
  --network okx-network \
  --restart always \
  -p 6379:6379 \
  -v ~/okx-trading-db/redis/data:/data \
  redis:6.2-alpine \
  redis-server --appendonly yes

# 等待MySQL启动
echo -e "${BLUE}===== 等待MySQL启动完成 =====${NC}"
sleep 15

# 检查容器状态
echo -e "${BLUE}===== 检查容器状态 =====${NC}"
echo "skysi" | sudo -S docker ps | grep okx-trading

# 测试MySQL连接
echo -e "${BLUE}===== 测试MySQL连接 =====${NC}"
for i in {1..10}; do
    if echo "skysi" | sudo -S docker exec okx-trading-mysql mysql -uroot -pPassword123? -e "SELECT 1;" &> /dev/null; then
        echo -e "${GREEN}MySQL连接成功${NC}"
        break
    else
        echo -e "${YELLOW}等待MySQL启动... ($i/10)${NC}"
        sleep 3
    fi
done

# 测试Redis连接
echo -e "${BLUE}===== 测试Redis连接 =====${NC}"
if echo "skysi" | sudo -S docker exec okx-trading-redis redis-cli ping | grep -q PONG; then
    echo -e "${GREEN}Redis连接成功${NC}"
else
    echo -e "${RED}Redis连接失败${NC}"
fi

echo -e "${GREEN}===== MySQL和Redis部署完成 =====${NC}"
echo -e "${BLUE}MySQL信息:${NC}"
echo -e "  主机: 192.168.54.68"
echo -e "  端口: 3306"
echo -e "  用户: root"
echo -e "  密码: Password123?"
echo -e "  数据库: okx_trading"
echo -e ""
echo -e "${BLUE}Redis信息:${NC}"
echo -e "  主机: 192.168.54.68"
echo -e "  端口: 6379"
echo -e "  密码: (无)"

ENDSSH

echo -e "${GREEN}===== 远程部署完成 =====${NC}"
