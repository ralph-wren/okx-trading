#!/bin/bash

# 部署股票功能到远程服务器
# 远程服务器: 192.168.54.68
# 用户: skysi
# 密码: skysi

REMOTE_HOST="192.168.54.68"
REMOTE_USER="skysi"
REMOTE_PASS="skysi"
REMOTE_DIR="~/okx-trading"

echo "========================================="
echo "部署股票功能到远程服务器"
echo "========================================="
echo "远程服务器: $REMOTE_HOST"
echo "远程目录: $REMOTE_DIR"
echo ""

# 1. 本地编译打包
echo "[步骤 1/6] 本地编译打包..."
mvn clean package -DskipTests

if [ $? -ne 0 ]; then
    echo "✗ 编译失败"
    exit 1
fi

echo "✓ 编译成功"
echo ""

# 2. 停止远程应用
echo "[步骤 2/6] 停止远程应用..."
sshpass -p "$REMOTE_PASS" ssh ${REMOTE_USER}@${REMOTE_HOST} << 'EOF'
    cd ~/okx-trading
    
    # 查找并停止 Java 进程
    PID=$(ps aux | grep "okx-trading.*jar" | grep -v grep | awk '{print $2}')
    
    if [ -n "$PID" ]; then
        echo "找到运行中的应用进程: $PID"
        kill $PID
        sleep 3
        
        # 如果还在运行，强制停止
        if ps -p $PID > /dev/null 2>&1; then
            echo "强制停止进程..."
            kill -9 $PID
        fi
        
        echo "✓ 应用已停止"
    else
        echo "应用未运行"
    fi
EOF

echo ""

# 3. 备份远程旧版本
echo "[步骤 3/6] 备份远程旧版本..."
sshpass -p "$REMOTE_PASS" ssh ${REMOTE_USER}@${REMOTE_HOST} << 'EOF'
    cd ~/okx-trading
    
    if [ -f "okx-trading.jar" ]; then
        BACKUP_NAME="okx-trading-backup-$(date +%Y%m%d-%H%M%S).jar"
        mv okx-trading.jar "$BACKUP_NAME"
        echo "✓ 已备份为: $BACKUP_NAME"
    else
        echo "无需备份（首次部署）"
    fi
EOF

echo ""

# 4. 上传新版本 JAR
echo "[步骤 4/6] 上传新版本 JAR..."
sshpass -p "$REMOTE_PASS" scp target/okx-trading-0.0.1-SNAPSHOT.jar \
    ${REMOTE_USER}@${REMOTE_HOST}:${REMOTE_DIR}/okx-trading.jar

if [ $? -eq 0 ]; then
    echo "✓ JAR 文件上传成功"
else
    echo "✗ JAR 文件上传失败"
    exit 1
fi

echo ""

# 5. 上传配置文件（如果有更新）
echo "[步骤 5/6] 上传配置文件..."
sshpass -p "$REMOTE_PASS" scp src/main/resources/application.properties \
    ${REMOTE_USER}@${REMOTE_HOST}:${REMOTE_DIR}/application.properties

if [ $? -eq 0 ]; then
    echo "✓ 配置文件上传成功"
else
    echo "⚠ 配置文件上传失败（可能不影响运行）"
fi

echo ""

# 6. 启动远程应用
echo "[步骤 6/6] 启动远程应用..."
sshpass -p "$REMOTE_PASS" ssh ${REMOTE_USER}@${REMOTE_HOST} << 'EOF'
    cd ~/okx-trading
    
    # 创建日志目录
    mkdir -p logs
    
    # 启动应用
    echo "启动应用..."
    nohup java -jar okx-trading.jar \
        --spring.config.location=./application.properties \
        > logs/app.log 2>&1 &
    
    echo "应用已启动，PID: $!"
    
    # 等待应用启动
    echo "等待应用启动..."
    sleep 10
    
    # 检查应用是否启动成功
    if ps aux | grep "okx-trading.*jar" | grep -v grep > /dev/null; then
        echo "✓ 应用启动成功"
        
        # 显示最后几行日志
        echo ""
        echo "最近的日志："
        tail -20 logs/app.log
    else
        echo "✗ 应用启动失败"
        echo ""
        echo "错误日志："
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
    echo "应用访问地址："
    echo "  http://${REMOTE_HOST}:8088"
    echo ""
    echo "Swagger UI:"
    echo "  http://${REMOTE_HOST}:8088/swagger-ui.html"
    echo ""
    echo "测试股票接口："
    echo "  curl http://${REMOTE_HOST}:8088/api/stock/market/test"
    echo ""
    echo "查看日志："
    echo "  ssh ${REMOTE_USER}@${REMOTE_HOST} 'tail -f ~/okx-trading/logs/app.log'"
    echo ""
else
    echo ""
    echo "========================================="
    echo "✗ 部署失败"
    echo "========================================="
    echo ""
    echo "请检查错误信息并重试"
    exit 1
fi
