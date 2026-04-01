#!/bin/bash

REMOTE_HOST="192.168.54.68"
REMOTE_USER="skysi"
REMOTE_PASSWORD="skysi"

GREEN='\033[0;32m'
BLUE='\033[0;34m'
RED='\033[0;31m'
NC='\033[0m'

echo -e "${BLUE}===== 创建远程部署脚本 =====${NC}"

sshpass -p "$REMOTE_PASSWORD" ssh -o StrictHostKeyChecking=no $REMOTE_USER@$REMOTE_HOST bash << 'ENDSSH'

GREEN='\033[0;32m'
BLUE='\033[0;34m'
RED='\033[0;31m'
YELLOW='\033[0;33m'
NC='\033[0m'

cd ~/okx-trading

echo -e "${BLUE}创建部署脚本: deploy.sh${NC}"

cat > deploy.sh << 'EOF'
#!/bin/bash

# OKX Trading 应用部署脚本
# 在远程服务器上直接执行，无需本地登录

GREEN='\033[0;32m'
BLUE='\033[0;34m'
RED='\033[0;31m'
YELLOW='\033[0;33m'
NC='\033[0m'

APP_NAME="okx-trading"
JAR_FILE="okx-trading-0.0.1-SNAPSHOT.jar"
APP_DIR="$HOME/okx-trading"

echo -e "${BLUE}===== OKX Trading 部署脚本 =====${NC}"
echo ""

cd $APP_DIR

# 1. 检查并停止旧进程
echo -e "${BLUE}1. 检查现有进程${NC}"
OLD_PID=$(ps -ef | grep "$JAR_FILE" | grep -v grep | awk '{print $2}')
if [ -n "$OLD_PID" ]; then
    echo -e "${YELLOW}发现运行中的进程 (PID: $OLD_PID)，正在停止...${NC}"
    kill $OLD_PID
    sleep 5
    
    # 检查是否成功停止
    if ps -p $OLD_PID > /dev/null 2>&1; then
        echo -e "${YELLOW}进程未能正常停止，强制终止...${NC}"
        kill -9 $OLD_PID
        sleep 2
    fi
    echo -e "${GREEN}✓ 旧进程已停止${NC}"
else
    echo -e "${GREEN}✓ 没有运行中的进程${NC}"
fi
echo ""

# 2. 拉取最新代码
echo -e "${BLUE}2. 拉取最新代码${NC}"
if [ -d ".git" ]; then
    git pull origin main || git pull origin master
    if [ $? -eq 0 ]; then
        echo -e "${GREEN}✓ 代码更新成功${NC}"
    else
        echo -e "${YELLOW}⚠ 代码更新失败，使用现有代码${NC}"
    fi
else
    echo -e "${YELLOW}⚠ 不是Git仓库，跳过代码更新${NC}"
fi
echo ""

# 3. 编译打包
echo -e "${BLUE}3. 编译打包项目${NC}"
mvn clean package -DskipTests

if [ ! -f "target/$JAR_FILE" ]; then
    echo -e "${RED}✗ 打包失败，JAR文件不存在${NC}"
    exit 1
fi
echo -e "${GREEN}✓ 打包成功${NC}"
echo ""

# 4. 备份旧版本
echo -e "${BLUE}4. 备份旧版本${NC}"
if [ -f "$JAR_FILE" ]; then
    BACKUP_NAME="$JAR_FILE.backup.$(date +%Y%m%d_%H%M%S)"
    mv "$JAR_FILE" "$BACKUP_NAME"
    echo -e "${GREEN}✓ 已备份为: $BACKUP_NAME${NC}"
fi
echo ""

# 5. 复制新版本
echo -e "${BLUE}5. 部署新版本${NC}"
cp "target/$JAR_FILE" "$JAR_FILE"
echo -e "${GREEN}✓ 新版本已部署${NC}"
echo ""

# 6. 启动应用
echo -e "${BLUE}6. 启动应用${NC}"

# 设置JVM参数
JAVA_OPTS="-Xms512m -Xmx1024m -XX:MetaspaceSize=128m -XX:MaxMetaspaceSize=256m"

# 设置环境变量
export MYSQL_USERNAME="root"
export MYSQL_PASSWORD="Password123?"
export SPRING_DATASOURCE_URL="jdbc:mysql://localhost:3306/okx_trading?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai&useSSL=false"
export SPRING_REDIS_HOST="localhost"
export SPRING_REDIS_PORT="6379"

# 创建日志目录
mkdir -p logs/all logs/api logs/error

# 启动应用
echo "正在启动 $APP_NAME..."
nohup java $JAVA_OPTS -jar $JAR_FILE \
    --server.address=0.0.0.0 \
    --server.port=8088 \
    > logs/startup.log 2>&1 &

NEW_PID=$!
echo "$APP_NAME 已启动，进程ID: $NEW_PID"
echo $NEW_PID > pid.file
echo ""

# 7. 等待启动
echo -e "${BLUE}7. 等待应用启动${NC}"
sleep 10

if ps -p $NEW_PID > /dev/null 2>&1; then
    echo -e "${GREEN}✓ 应用进程运行正常${NC}"
    
    # 等待端口监听
    echo "等待端口8088监听..."
    for i in {1..12}; do
        if netstat -tulpn 2>/dev/null | grep -q ":8088" || ss -tulpn 2>/dev/null | grep -q ":8088"; then
            echo -e "${GREEN}✓ 应用已在端口8088上监听${NC}"
            break
        fi
        echo "等待中... ($i/12)"
        sleep 5
    done
    
    echo ""
    echo -e "${GREEN}===== 部署完成 =====${NC}"
    echo -e "${BLUE}应用信息:${NC}"
    echo "  PID: $NEW_PID"
    echo "  端口: 8088"
    echo "  访问地址: http://192.168.54.68:8088"
    echo ""
    echo -e "${BLUE}管理命令:${NC}"
    echo "  查看日志: tail -f logs/all/all.log"
    echo "  查看启动日志: tail -f logs/startup.log"
    echo "  停止应用: ./stop.sh"
    echo "  重启应用: ./restart.sh"
else
    echo -e "${RED}✗ 应用启动失败${NC}"
    echo "查看启动日志:"
    tail -50 logs/startup.log
    exit 1
fi
EOF

chmod +x deploy.sh
echo -e "${GREEN}✓ deploy.sh 创建成功${NC}"
echo ""

# 创建停止脚本
echo -e "${BLUE}创建停止脚本: stop.sh${NC}"

cat > stop.sh << 'EOF'
#!/bin/bash

GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[0;33m'
NC='\033[0m'

APP_NAME="okx-trading"
JAR_FILE="okx-trading-0.0.1-SNAPSHOT.jar"

echo -e "${YELLOW}正在停止 $APP_NAME...${NC}"

PID=$(ps -ef | grep "$JAR_FILE" | grep -v grep | awk '{print $2}')

if [ -n "$PID" ]; then
    echo "找到进程 PID: $PID"
    kill $PID
    
    # 等待进程终止
    for i in {1..30}; do
        if ! ps -p $PID > /dev/null 2>&1; then
            echo -e "${GREEN}✓ $APP_NAME 已停止${NC}"
            exit 0
        fi
        sleep 1
    done
    
    # 强制终止
    echo -e "${YELLOW}强制终止进程...${NC}"
    kill -9 $PID
    sleep 2
    
    if ! ps -p $PID > /dev/null 2>&1; then
        echo -e "${GREEN}✓ $APP_NAME 已停止${NC}"
    else
        echo -e "${RED}✗ 无法停止 $APP_NAME${NC}"
        exit 1
    fi
else
    echo -e "${YELLOW}$APP_NAME 未在运行${NC}"
fi
EOF

chmod +x stop.sh
echo -e "${GREEN}✓ stop.sh 创建成功${NC}"
echo ""

# 创建重启脚本
echo -e "${BLUE}创建重启脚本: restart.sh${NC}"

cat > restart.sh << 'EOF'
#!/bin/bash

GREEN='\033[0;32m'
BLUE='\033[0;34m'
NC='\033[0m'

echo -e "${BLUE}===== 重启应用 =====${NC}"

# 停止应用
./stop.sh

# 等待2秒
sleep 2

# 启动应用
./deploy.sh
EOF

chmod +x restart.sh
echo -e "${GREEN}✓ restart.sh 创建成功${NC}"
echo ""

# 创建状态检查脚本
echo -e "${BLUE}创建状态检查脚本: status.sh${NC}"

cat > status.sh << 'EOF'
#!/bin/bash

GREEN='\033[0;32m'
BLUE='\033[0;34m'
RED='\033[0;31m'
YELLOW='\033[0;33m'
NC='\033[0m'

APP_NAME="okx-trading"
JAR_FILE="okx-trading-0.0.1-SNAPSHOT.jar"

echo -e "${BLUE}===== $APP_NAME 状态 =====${NC}"
echo ""

# 检查进程
PID=$(ps -ef | grep "$JAR_FILE" | grep -v grep | awk '{print $2}')
if [ -n "$PID" ]; then
    echo -e "${GREEN}✓ 应用正在运行${NC}"
    echo "  PID: $PID"
    
    # 检查端口
    if netstat -tulpn 2>/dev/null | grep -q ":8088" || ss -tulpn 2>/dev/null | grep -q ":8088"; then
        echo -e "${GREEN}✓ 端口8088正在监听${NC}"
    else
        echo -e "${YELLOW}⚠ 端口8088未监听${NC}"
    fi
    
    # 显示进程信息
    echo ""
    echo -e "${BLUE}进程信息:${NC}"
    ps -fp $PID
    
    # 显示最新日志
    echo ""
    echo -e "${BLUE}最新日志 (最后10行):${NC}"
    if [ -f logs/all/all.log ]; then
        tail -10 logs/all/all.log
    elif [ -f logs/startup.log ]; then
        tail -10 logs/startup.log
    fi
else
    echo -e "${RED}✗ 应用未运行${NC}"
fi

echo ""
echo -e "${BLUE}访问地址:${NC} http://192.168.54.68:8088"
EOF

chmod +x status.sh
echo -e "${GREEN}✓ status.sh 创建成功${NC}"
echo ""

echo -e "${GREEN}===== 所有脚本创建完成 =====${NC}"
echo ""
echo -e "${BLUE}可用脚本:${NC}"
echo "  ./deploy.sh   - 部署/更新应用"
echo "  ./stop.sh     - 停止应用"
echo "  ./restart.sh  - 重启应用"
echo "  ./status.sh   - 查看应用状态"
echo ""
echo -e "${BLUE}使用方法:${NC}"
echo "  cd ~/okx-trading"
echo "  ./deploy.sh"

ENDSSH

echo -e "${GREEN}===== 完成 =====${NC}"
