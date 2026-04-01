#!/bin/bash

# 导入data.sql数据到远程MySQL数据库
# 远程服务器: 192.168.54.68
# 用户: skysi
# MySQL密码: Password123?

REMOTE_HOST="192.168.54.68"
REMOTE_USER="skysi"
REMOTE_PASS="skysi"
MYSQL_PASSWORD="Password123?"
MYSQL_DATABASE="okx_trading"
MYSQL_CONTAINER="okx-trading-mysql"

echo "========================================="
echo "导入data.sql数据到远程MySQL数据库"
echo "========================================="
echo "远程服务器: $REMOTE_HOST"
echo "MySQL容器: $MYSQL_CONTAINER"
echo "数据库: $MYSQL_DATABASE"
echo ""

# 1. 上传data.sql到远程服务器
echo "[步骤 1/3] 上传data.sql到远程服务器..."
sshpass -p "$REMOTE_PASS" scp src/main/resources/data.sql ${REMOTE_USER}@${REMOTE_HOST}:~/okx-trading/

if [ $? -eq 0 ]; then
    echo "✓ data.sql上传成功"
else
    echo "✗ data.sql上传失败"
    exit 1
fi

echo ""

# 2. 在远程服务器上导入数据
echo "[步骤 2/3] 在远程服务器上导入数据..."
sshpass -p "$REMOTE_PASS" ssh ${REMOTE_USER}@${REMOTE_HOST} << EOF
    echo "开始导入数据到MySQL..."
    docker exec -i $MYSQL_CONTAINER mysql -uroot -p$MYSQL_PASSWORD $MYSQL_DATABASE < ~/okx-trading/data.sql
    
    if [ \$? -eq 0 ]; then
        echo "✓ 数据导入成功"
    else
        echo "✗ 数据导入失败"
        exit 1
    fi
EOF

if [ $? -ne 0 ]; then
    echo "✗ 远程执行失败"
    exit 1
fi

echo ""

# 3. 验证数据导入
echo "[步骤 3/3] 验证数据导入..."
sshpass -p "$REMOTE_PASS" ssh ${REMOTE_USER}@${REMOTE_HOST} << EOF
    echo "查询strategy_info表记录数..."
    RECORD_COUNT=\$(docker exec $MYSQL_CONTAINER mysql -uroot -p$MYSQL_PASSWORD -D$MYSQL_DATABASE -se "SELECT COUNT(*) FROM strategy_info;")
    
    echo "strategy_info表记录数: \$RECORD_COUNT"
    
    if [ \$RECORD_COUNT -gt 0 ]; then
        echo "✓ 数据验证成功，共导入 \$RECORD_COUNT 条策略记录"
        
        # 显示部分数据样例
        echo ""
        echo "数据样例（前5条）:"
        docker exec $MYSQL_CONTAINER mysql -uroot -p$MYSQL_PASSWORD -D$MYSQL_DATABASE -e "SELECT strategy_code, strategy_name, category FROM strategy_info LIMIT 5;"
    else
        echo "✗ 数据验证失败，表中没有数据"
        exit 1
    fi
EOF

if [ $? -eq 0 ]; then
    echo ""
    echo "========================================="
    echo "✓ 数据导入完成！"
    echo "========================================="
else
    echo ""
    echo "========================================="
    echo "✗ 数据导入失败"
    echo "========================================="
    exit 1
fi
