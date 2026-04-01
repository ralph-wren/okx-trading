#!/bin/bash

REMOTE_HOST="192.168.54.68"
REMOTE_USER="skysi"
REMOTE_PASSWORD="skysi"

GREEN='\033[0;32m'
BLUE='\033[0;34m'
RED='\033[0;31m'
YELLOW='\033[0;33m'
NC='\033[0m'

echo -e "${BLUE}===== 导入数据库Schema =====${NC}"

# 上传schema.sql到远程服务器
echo -e "${BLUE}上传schema.sql到远程服务器...${NC}"
sshpass -p "$REMOTE_PASSWORD" scp -o StrictHostKeyChecking=no \
  src/main/resources/schema.sql \
  $REMOTE_USER@$REMOTE_HOST:~/okx-trading-db/

# 在远程服务器执行SQL导入
sshpass -p "$REMOTE_PASSWORD" ssh -o StrictHostKeyChecking=no $REMOTE_USER@$REMOTE_HOST << 'ENDSSH'

GREEN='\033[0;32m'
BLUE='\033[0;34m'
RED='\033[0;31m'
YELLOW='\033[0;33m'
NC='\033[0m'

cd ~/okx-trading-db

echo -e "${BLUE}===== 导入Schema到MySQL =====${NC}"

# 使用docker exec执行SQL导入
echo "skysi" | sudo -S docker exec -i okx-trading-mysql mysql -uroot -pPassword123? okx_trading < schema.sql 2>&1

if [ $? -eq 0 ]; then
    echo -e "${GREEN}Schema导入成功${NC}"
    
    # 显示所有表
    echo -e "${BLUE}当前数据库表列表:${NC}"
    echo "skysi" | sudo -S docker exec okx-trading-mysql mysql -uroot -pPassword123? -e "USE okx_trading; SHOW TABLES;" 2>&1
    
else
    echo -e "${YELLOW}尝试不使用sudo...${NC}"
    docker exec -i okx-trading-mysql mysql -uroot -pPassword123? okx_trading < schema.sql 2>&1
    
    if [ $? -eq 0 ]; then
        echo -e "${GREEN}Schema导入成功${NC}"
        docker exec okx-trading-mysql mysql -uroot -pPassword123? -e "USE okx_trading; SHOW TABLES;" 2>&1
    else
        echo -e "${RED}Schema导入失败${NC}"
        exit 1
    fi
fi

ENDSSH

echo -e "${GREEN}===== 完成 =====${NC}"
