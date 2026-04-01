#!/bin/bash

REMOTE_HOST="192.168.54.68"
REMOTE_USER="skysi"
REMOTE_PASSWORD="skysi"

GREEN='\033[0;32m'
BLUE='\033[0;34m'
RED='\033[0;31m'
YELLOW='\033[0;33m'
NC='\033[0m'

echo -e "${BLUE}===== 修复MySQL部署 =====${NC}"

sshpass -p "$REMOTE_PASSWORD" ssh -o StrictHostKeyChecking=no $REMOTE_USER@$REMOTE_HOST << 'ENDSSH'

GREEN='\033[0;32m'
BLUE='\033[0;34m'
RED='\033[0;31m'
YELLOW='\033[0;33m'
NC='\033[0m'

# 检查Docker服务状态
echo -e "${BLUE}===== 检查Docker服务 =====${NC}"
echo "skysi" | sudo -S systemctl status docker | grep Active

# 重新拉取MySQL镜像
echo -e "${BLUE}===== 拉取MySQL 8.0镜像 =====${NC}"
echo "skysi" | sudo -S docker pull mysql:8.0

# 检查镜像是否拉取成功
if echo "skysi" | sudo -S docker images | grep -q mysql; then
    echo -e "${GREEN}MySQL镜像拉取成功${NC}"
    
    # 停止并删除旧容器
    echo -e "${BLUE}===== 停止旧MySQL容器 =====${NC}"
    echo "skysi" | sudo -S docker stop okx-trading-mysql 2>/dev/null || true
    echo "skysi" | sudo -S docker rm okx-trading-mysql 2>/dev/null || true
    
    # 启动MySQL容器
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
    
    # 等待MySQL启动
    echo -e "${BLUE}===== 等待MySQL启动 =====${NC}"
    sleep 20
    
    # 测试连接
    for i in {1..20}; do
        if echo "skysi" | sudo -S docker exec okx-trading-mysql mysql -uroot -pPassword123? -e "SELECT 1;" &> /dev/null; then
            echo -e "${GREEN}MySQL连接成功！${NC}"
            echo -e "${BLUE}数据库列表:${NC}"
            echo "skysi" | sudo -S docker exec okx-trading-mysql mysql -uroot -pPassword123? -e "SHOW DATABASES;"
            break
        else
            echo -e "${YELLOW}等待MySQL启动... ($i/20)${NC}"
            sleep 3
        fi
    done
    
    # 显示容器状态
    echo -e "${BLUE}===== 容器状态 =====${NC}"
    echo "skysi" | sudo -S docker ps | grep okx-trading
    
else
    echo -e "${RED}MySQL镜像拉取失败${NC}"
    echo -e "${YELLOW}尝试查看Docker日志:${NC}"
    echo "skysi" | sudo -S journalctl -u docker -n 50 --no-pager
fi

ENDSSH

echo -e "${GREEN}===== 完成 =====${NC}"
