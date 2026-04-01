#!/bin/bash

# 设置变量
REMOTE_HOST="8.210.141.61"  # 替换为你的远程服务器IP或域名
REMOTE_USER="root"         # 替换为你的远程服务器用户名
REMOTE_DIR="/opt/okx-trading"   # 远程服务器上的部署目录
APP_NAME="okx-trading"
JAR_FILE="target/okx-trading-0.0.1-SNAPSHOT.jar"
CONFIG_DIR="src/main/resources"

# 颜色定义
GREEN='\033[0;32m'
BLUE='\033[0;34m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo -e "${BLUE}===== 开始部署后端服务 =====${NC}"

# 清理并打包项目
echo -e "${BLUE}正在打包项目...${NC}"
mvn clean package -DskipTests

# 检查打包是否成功
if [ ! -f "$JAR_FILE" ]; then
  echo -e "${RED}打包失败，JAR文件不存在${NC}"
  exit 1
fi

echo -e "${GREEN}打包成功${NC}"

# 创建临时配置目录
echo -e "${BLUE}准备配置文件...${NC}"
TEMP_DIR="deploy_temp"
mkdir -p $TEMP_DIR

# 准备服务启动脚本
cat > $TEMP_DIR/start.sh << 'EOF'
#!/bin/bash
# 服务启动脚本
APP_NAME="okx-trading"
JAR_FILE="okx-trading.jar"

# 检查Java版本
java_version=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}')
echo "检测到Java版本: $java_version"

# 设置JVM参数
JAVA_OPTS="-Xms512m -Xmx1024m -XX:MetaspaceSize=128m -XX:MaxMetaspaceSize=256m"

# 设置环境变量（如需修改，请更新这些变量值）
export MYSQL_USERNAME="root"
export MYSQL_PASSWORD="Hg19951030@"  # 修复MySQL密码
export OKX_API_KEY="ca940079-e4ea-4839-b47e-97bbe4cc3267"
export OKX_SECRET_KEY="88022A3C11CEB4E2561985777C88ACD6"
export OKX_PASSPHRASE="Hg19951030@"
export DEEPSEEK_API_KEY="sk-a1d79a988f694e52a06d3b0ef97d1742"

# 检查MySQL服务
echo "检查MySQL服务状态..."
if systemctl is-active --quiet mysqld; then
  echo "MySQL服务正在运行"
else
  echo "MySQL服务未运行，尝试启动..."
  systemctl start mysqld
  sleep 3
  if systemctl is-active --quiet mysqld; then
    echo "MySQL服务已启动"
  else
    echo "无法启动MySQL服务，请检查MySQL安装"
  fi
fi

# 检查是否有旧进程，如果有则杀掉
PID=$(ps -ef | grep $JAR_FILE | grep -v grep | awk '{print $2}')
if [ -n "$PID" ]; then
  echo "停止旧的应用进程: $PID"
  kill $PID
  sleep 5

  # 检查进程是否仍在运行
  if ps -p $PID > /dev/null; then
    echo "进程未能正常停止，强制终止"
    kill -9 $PID
  fi
fi

# 创建日志目录
mkdir -p logs/all logs/api logs/error

# 启动应用
echo "正在启动 $APP_NAME..."
# 将服务器绑定到所有网络接口
nohup java $JAVA_OPTS -jar $JAR_FILE --server.address=0.0.0.0 --spring.config.location=file:./application.properties --spring.config.additional-location=file:./resources/ > ./logs/startup.log 2>&1 &
# java $JAVA_OPTS -jar $JAR_FILE --server.address=0.0.0.0 --spring.config.location=file:./application.properties --spring.config.additional-location=file:./resources/

# 获取新进程ID
NEW_PID=$!
echo "$APP_NAME 已启动，进程ID: $NEW_PID"

# 保存PID到文件
echo $NEW_PID > pid.file

# 检查应用是否成功启动
sleep 10
if ps -p $NEW_PID > /dev/null; then
  echo "$APP_NAME 启动成功!"
  echo "正在等待应用完全初始化..."
  
  # 等待端口被监听
  for i in {1..12}; do
    if netstat -tulpn | grep -q ":8088"; then
      echo "应用已在端口8088上监听"
      break
    fi
    echo "等待应用初始化 ($i/12)..."
    sleep 10
  done
  
  # 检查应用是否正常响应
  echo "检查应用响应..."
  HEALTH_CODE=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:8088/api/health || echo "连接失败")
  if [ "$HEALTH_CODE" = "200" ]; then
    echo "应用健康检查成功 (HTTP $HEALTH_CODE)"
  else
    echo "应用健康检查失败 (HTTP $HEALTH_CODE)"
    echo "应用可能仍在启动中，请稍后检查日志"
  fi
else
  echo "$APP_NAME 启动失败，请检查日志"
  exit 1
fi
EOF

# 准备停止脚本
cat > $TEMP_DIR/stop.sh << 'EOF'
#!/bin/bash
# 服务停止脚本
APP_NAME="okx-trading"
JAR_FILE="okx-trading.jar"

# 查找进程ID
PID=$(ps -ef | grep $JAR_FILE | grep -v grep | awk '{print $2}')

if [ -n "$PID" ]; then
  echo "正在停止 $APP_NAME (PID: $PID)..."
  kill $PID

  # 等待进程终止
  for i in {1..30}; do
    if ! ps -p $PID > /dev/null; then
      echo "$APP_NAME 已停止"
      exit 0
    fi
    sleep 1
  done

  # 如果进程仍在运行，强制终止
  echo "强制终止 $APP_NAME 进程"
  kill -9 $PID

  if ! ps -p $PID > /dev/null; then
    echo "$APP_NAME 已停止"
  else
    echo "无法停止 $APP_NAME，请手动检查"
    exit 1
  fi
else
  echo "$APP_NAME 未在运行"
fi
EOF

# 创建配置文件目录
mkdir -p $TEMP_DIR/resources

# 拷贝整个resources目录
echo -e "${BLUE}复制resources目录...${NC}"
cp -r $CONFIG_DIR/* $TEMP_DIR/resources/

# 拷贝主配置文件到根目录
cp $CONFIG_DIR/application.properties $TEMP_DIR/
# 修改数据库和Redis连接为本地
sed -i 's/spring.datasource.url=.*/spring.datasource.url=jdbc:mysql:\/\/localhost:3306\/okx_trading?useUnicode=true\&characterEncoding=UTF-8\&serverTimezone=Asia\/Shanghai\&useSSL=false\&allowPublicKeyRetrieval=true\&connectionCollation=utf8mb4_unicode_ci/' $TEMP_DIR/application.properties
sed -i 's/spring.redis.host=.*/spring.redis.host=localhost/' $TEMP_DIR/application.properties

# 添加服务器环境配置
echo "# 添加服务器环境配置" >> $TEMP_DIR/application.properties
echo "spring.profiles.active=prod" >> $TEMP_DIR/application.properties
echo "server.address=0.0.0.0" >> $TEMP_DIR/application.properties

# 复制JAR文件到临时目录
cp $JAR_FILE $TEMP_DIR/okx-trading.jar

# 设置脚本执行权限
chmod +x $TEMP_DIR/start.sh $TEMP_DIR/stop.sh

echo -e "${BLUE}正在连接到远程服务器...${NC}"

# 检查远程目录是否存在，不存在则创建
ssh $REMOTE_USER@$REMOTE_HOST "mkdir -p $REMOTE_DIR"

# 停止远程服务器上运行的应用（如果存在）
echo -e "${BLUE}停止远程服务器上的应用（如果在运行）...${NC}"
ssh $REMOTE_USER@$REMOTE_HOST "if [ -f $REMOTE_DIR/stop.sh ]; then cd $REMOTE_DIR && bash stop.sh; fi"

# 上传文件到远程服务器
echo -e "${BLUE}上传文件到远程服务器...${NC}"
scp -r $TEMP_DIR/* $REMOTE_USER@$REMOTE_HOST:$REMOTE_DIR/

# 启动应用
echo -e "${BLUE}启动远程服务器上的应用...${NC}"
ssh $REMOTE_USER@$REMOTE_HOST "cd $REMOTE_DIR && bash start.sh"

# 清理临时文件
echo -e "${BLUE}清理临时文件...${NC}"
rm -rf $TEMP_DIR

echo -e "${GREEN}===== 后端服务部署完成 =====${NC}"
echo -e "${GREEN}服务已部署到 $REMOTE_HOST:$REMOTE_DIR${NC}"
echo -e "${BLUE}重要提示: 应用已在后台启动，这是正常现象${NC}"
echo -e "${BLUE}可以使用以下命令检查应用状态:${NC}"
echo -e "${GREEN}./check-status.sh${NC}"
