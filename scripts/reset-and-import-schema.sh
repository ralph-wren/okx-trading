#!/bin/bash

REMOTE_HOST="192.168.54.68"
REMOTE_USER="skysi"
REMOTE_PASSWORD="skysi"

GREEN='\033[0;32m'
BLUE='\033[0;34m'
RED='\033[0;31m'
YELLOW='\033[0;33m'
NC='\033[0m'

echo -e "${BLUE}===== 重置并导入数据库Schema =====${NC}"

# 上传schema.sql到远程服务器
echo -e "${BLUE}上传schema.sql到远程服务器...${NC}"
sshpass -p "$REMOTE_PASSWORD" scp -o StrictHostKeyChecking=no \
  src/main/resources/schema.sql \
  $REMOTE_USER@$REMOTE_HOST:~/okx-trading-db/

# 在远程服务器执行SQL导入
sshpass -p "$REMOTE_PASSWORD" ssh -o StrictHostKeyChecking=no $REMOTE_USER@$REMOTE_HOST bash << 'ENDSSH'

GREEN='\033[0;32m'
BLUE='\033[0;34m'
RED='\033[0;31m'
YELLOW='\033[0;33m'
NC='\033[0m'

cd ~/okx-trading-db

echo -e "${YELLOW}===== 删除并重建数据库 =====${NC}"

# 删除并重建数据库
docker exec okx-trading-mysql mysql -uroot -pPassword123? -e "DROP DATABASE IF EXISTS okx_trading; CREATE DATABASE okx_trading DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;" 2>&1

echo -e "${BLUE}===== 导入Schema到MySQL =====${NC}"

# 导入schema
docker exec -i okx-trading-mysql mysql -uroot -pPassword123? okx_trading < schema.sql 2>&1

if [ $? -eq 0 ]; then
    echo -e "${GREEN}Schema导入成功${NC}"
    
    # 显示所有表
    echo -e "${BLUE}当前数据库表列表:${NC}"
    docker exec okx-trading-mysql mysql -uroot -pPassword123? -e "USE okx_trading; SHOW TABLES;" 2>&1 | grep -v "Warning"
    
    # 统计表数量
    TABLE_COUNT=$(docker exec okx-trading-mysql mysql -uroot -pPassword123? -e "USE okx_trading; SHOW TABLES;" 2>&1 | grep -v "Warning" | grep -v "Tables_in" | wc -l)
    echo -e "${GREEN}共创建 ${TABLE_COUNT} 个表${NC}"
    
    # 显示一些关键表的结构
    echo -e "${BLUE}===== 关键表结构预览 =====${NC}"
    echo -e "${YELLOW}real_time_strategy 表:${NC}"
    docker exec okx-trading-mysql mysql -uroot -pPassword123? -e "USE okx_trading; DESC real_time_strategy;" 2>&1 | grep -v "Warning"
    
    echo -e "${YELLOW}real_time_orders 表:${NC}"
    docker exec okx-trading-mysql mysql -uroot -pPassword123? -e "USE okx_trading; DESC real_time_orders;" 2>&1 | grep -v "Warning"
    
else
    echo -e "${RED}Schema导入失败${NC}"
    exit 1
fi

ENDSSH

echo -e "${GREEN}===== 完成 =====${NC}"
