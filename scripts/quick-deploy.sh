#!/bin/bash

# 快速部署（跳过测试）
# 远程服务器: 192.168.54.68

REMOTE_HOST="192.168.54.68"
REMOTE_USER="skysi"
REMOTE_PASS="skysi"
REMOTE_DIR="~/okx-trading"

echo "========================================="
echo "快速部署到远程服务器（跳过测试）"
echo "========================================="
echo ""

# 1. 清理并编译（跳过测试）
echo "[步骤 1/5] 清理并编译..."
mvn clean package -DskipTests -Dmaven.test.skip=true

if [ $? -ne 0 ]; then
    echo "✗ 编译失败"
    echo ""
    echo "请查看上面的错误信息"
    echo "常见问题："
    echo "  1. 检查 Java 版本: java -version (需要 Java 21)"
    echo "  2. 检查 Maven 版本: mvn -version (需要 Maven 3.6+)"
    echo "  3. 检查代码语法错误"
    exit 1
fi

echo "✓ 编译成功"
echo ""

# 2. 停止远程应用
echo "[步骤 2/5] 停止远程应用..."
sshpass -p "$REMOTE_PASS" ssh ${REMOTE_USER}@${REMOTE_HOST} << 'EOF'
    PID=$(ps aux | grep "okx-trading.*jar" | grep -v grep | awk '{print $2}')
    if [ -n "$PID" ]; then
        echo "停止进程: $PID"
        kill $PID
        sleep 3
        if ps -p $PID > /dev/null 2>&1; then
            kill -9 $PID
        fi
        echo "✓ 应用已停止"
    else
        echo "应用未运行"
    fi
EOF

echo ""

# 3. 备份
echo "[步骤 3/5] 备份旧版本..."
sshpass -p "$REMOTE_PASS" ssh ${REMOTE_USER}@${REMOTE_HOST} << 'EOF'
    cd ~/okx-trading
    if [ -f "okx-trading.jar" ]; then
        mv okx-trading.jar "okx-trading-backup-$(date +%Y%m%d-%H%M%S).jar"
        echo "✓ 已备份"
    fi
EOF

echo ""

# 4. 上传
echo "[步骤 4/5] 上传 JAR 文件..."
sshpass -p "$REMOTE_PASS" scp target/okx-trading-0.0.1-SNAPSHOT.jar \
    ${REMOTE_USER}@${REMOTE_HOST}:${REMOTE_DIR}/okx-trading.jar

if [ $? -ne 0 ]; then
    echo "✗ 上传失败"
    exit 1
fi

echo "✓ 上传成功"
echo ""

# 5. 启动
echo "[步骤 5/5] 启动应用..."
sshpass -p "$REMOTE_PASS" ssh ${REMOTE_USER}@${REMOTE_HOST} << 'EOF'
    cd ~/okx-trading
    mkdir -p logs
    
    nohup java -jar okx-trading.jar > logs/app.log 2>&1 &
    
    echo "等待应用启动..."
    sleep 10
    
    if ps aux | grep "okx-trading.*jar" | grep -v grep > /dev/null; then
        echo "✓ 应用启动成功"
        echo ""
        echo "最近日志："
        tail -20 logs/app.log
    else
        echo "✗ 应用启动失败"
        tail -50 logs/app.log
        exit 1
    fi
EOF

if [ $? -eq 0 ]; then
    echo ""
    echo "========================================="
    echo "✓ 部署成功！"
    echo "========================================="
    echo ""
    echo "测试接口："
    echo "  curl http://${REMOTE_HOST}:8088/api/stock/market/test"
    echo ""
    echo "访问 Swagger:"
    echo "  http://${REMOTE_HOST}:8088/swagger-ui.html"
    echo ""
else
    echo ""
    echo "✗ 部署失败"
    exit 1
fi
