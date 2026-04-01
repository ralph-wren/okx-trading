# 远程服务器部署指南

## 概述

现在你可以直接在远程服务器 `192.168.54.68` 的 `~/okx-trading` 目录下执行部署，无需从本地登录。

---

## 已创建的脚本

在远程服务器 `~/okx-trading` 目录下，已创建以下脚本：

| 脚本 | 功能 | 说明 |
|------|------|------|
| `deploy.sh` | 部署/更新应用 | 拉取代码、编译、打包、部署、启动 |
| `stop.sh` | 停止应用 | 优雅停止应用进程 |
| `restart.sh` | 重启应用 | 停止后重新部署启动 |
| `status.sh` | 查看状态 | 查看应用运行状态和日志 |

---

## 使用方法

### 1. 登录远程服务器

```bash
ssh skysi@192.168.54.68
# 密码: skysi
```

### 2. 进入项目目录

```bash
cd ~/okx-trading
```

### 3. 执行部署

```bash
./deploy.sh
```

部署脚本会自动完成以下操作：
1. ✅ 停止旧进程
2. ✅ 拉取最新代码（如果是Git仓库）
3. ✅ Maven编译打包
4. ✅ 备份旧版本
5. ✅ 部署新版本
6. ✅ 启动应用
7. ✅ 验证启动状态

---

## 常用命令

### 查看应用状态
```bash
cd ~/okx-trading
./status.sh
```

### 停止应用
```bash
cd ~/okx-trading
./stop.sh
```

### 重启应用
```bash
cd ~/okx-trading
./restart.sh
```

### 查看实时日志
```bash
cd ~/okx-trading
tail -f logs/all/all.log
```

### 查看启动日志
```bash
cd ~/okx-trading
tail -f logs/startup.log
```

---

## 部署流程示例

```bash
# 1. SSH登录
ssh skysi@192.168.54.68

# 2. 进入目录
cd ~/okx-trading

# 3. 查看当前状态
./status.sh

# 4. 部署新版本
./deploy.sh

# 5. 查看日志
tail -f logs/all/all.log
```

---

## 配置说明

### 数据库配置
- **地址**: localhost:3306
- **数据库**: okx_trading
- **用户**: root
- **密码**: Password123?

### Redis配置
- **地址**: localhost:6379
- **密码**: 无

### 应用配置
- **端口**: 8088
- **访问地址**: http://192.168.54.68:8088

---

## 环境变量

部署脚本会自动设置以下环境变量：

```bash
MYSQL_USERNAME=root
MYSQL_PASSWORD=Password123?
SPRING_DATASOURCE_URL=jdbc:mysql://localhost:3306/okx_trading...
SPRING_REDIS_HOST=localhost
SPRING_REDIS_PORT=6379
```

---

## 日志文件

| 日志文件 | 说明 |
|---------|------|
| `logs/startup.log` | 应用启动日志 |
| `logs/all/all.log` | 所有日志 |
| `logs/api/api.log` | API请求日志 |
| `logs/error/error.log` | 错误日志 |

---

## 故障排查

### 问题1：应用启动失败

```bash
# 查看启动日志
tail -100 logs/startup.log

# 查看错误日志
tail -100 logs/error/error.log

# 检查端口占用
netstat -tulpn | grep 8088
```

### 问题2：Maven打包失败

```bash
# 手动打包查看详细错误
mvn clean package -DskipTests

# 检查Java版本
java -version

# 检查Maven版本
mvn -version
```

### 问题3：数据库连接失败

```bash
# 检查MySQL状态
docker ps | grep mysql

# 测试MySQL连接
docker exec okx-trading-mysql mysql -uroot -pPassword123? -e "SELECT 1;"
```

### 问题4：Redis连接失败

```bash
# 检查Redis状态
docker ps | grep redis

# 测试Redis连接
docker exec okx-trading-redis redis-cli ping
```

---

## 备份和回滚

### 自动备份
每次部署时，旧版本会自动备份为：
```
okx-trading-0.0.1-SNAPSHOT.jar.backup.YYYYMMDD_HHMMSS
```

### 手动回滚
```bash
cd ~/okx-trading

# 停止当前应用
./stop.sh

# 恢复备份文件
cp okx-trading-0.0.1-SNAPSHOT.jar.backup.20260331_170000 okx-trading-0.0.1-SNAPSHOT.jar

# 启动应用
nohup java -jar okx-trading-0.0.1-SNAPSHOT.jar > logs/startup.log 2>&1 &
```

---

## 进程管理

### 查看进程
```bash
ps -ef | grep okx-trading
```

### 手动停止
```bash
# 优雅停止
kill <PID>

# 强制停止
kill -9 <PID>
```

### 查看端口
```bash
netstat -tulpn | grep 8088
# 或
ss -tulpn | grep 8088
```

---

## 性能监控

### 查看JVM内存
```bash
jps -v | grep okx-trading
```

### 查看系统资源
```bash
# CPU和内存使用
top -p <PID>

# 详细进程信息
ps aux | grep okx-trading
```

---

## 自动化部署

### 方式1：从本地触发远程部署

创建本地脚本 `remote-deploy.sh`：
```bash
#!/bin/bash
ssh skysi@192.168.54.68 "cd ~/okx-trading && ./deploy.sh"
```

### 方式2：定时自动部署

在远程服务器上设置cron任务：
```bash
# 编辑crontab
crontab -e

# 每天凌晨2点自动部署
0 2 * * * cd ~/okx-trading && ./deploy.sh >> logs/auto-deploy.log 2>&1
```

---

## 注意事项

1. ⚠️ 部署前确保MySQL和Redis服务正常运行
2. ⚠️ 部署会自动停止旧进程，可能导致短暂服务中断
3. ⚠️ 确保有足够的磁盘空间（至少1GB）
4. ✅ 每次部署会自动备份旧版本
5. ✅ 部署脚本会自动创建日志目录
6. ✅ 应用会自动绑定到0.0.0.0，可从外部访问

---

## 快速参考

```bash
# 部署
cd ~/okx-trading && ./deploy.sh

# 状态
cd ~/okx-trading && ./status.sh

# 停止
cd ~/okx-trading && ./stop.sh

# 重启
cd ~/okx-trading && ./restart.sh

# 日志
tail -f ~/okx-trading/logs/all/all.log
```

---

## 联系支持

如遇问题，请检查：
1. 应用日志：`~/okx-trading/logs/`
2. MySQL状态：`docker ps | grep mysql`
3. Redis状态：`docker ps | grep redis`
4. 网络连接：`curl http://localhost:8088`
