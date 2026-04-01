# 股票功能远程部署指南

## 概述

本指南说明如何将新增的股票功能部署到远程服务器 `192.168.54.68`。

## 前提条件

- ✅ 本地已完成代码开发
- ✅ 远程服务器已部署 MySQL 和 Redis
- ✅ 远程服务器可通过 SSH 访问
- ✅ 本地已安装 `sshpass`（用于自动化部署）

## 快速部署

### 一键部署（推荐）

```bash
# 运行自动化部署脚本
./deploy-stock-to-remote.sh
```

这个脚本会自动完成：
1. ✅ 本地编译打包
2. ✅ 停止远程应用
3. ✅ 备份远程旧版本
4. ✅ 上传新版本 JAR
5. ✅ 上传配置文件
6. ✅ 启动远程应用

### 部署后测试

```bash
# 测试远程股票 API
./test-remote-stock-api.sh
```

## 手动部署步骤

如果自动化脚本失败，可以手动执行以下步骤：

### 步骤 1：本地编译

```bash
# 清理并编译
mvn clean package -DskipTests

# 检查 JAR 文件
ls -lh target/okx-trading-0.0.1-SNAPSHOT.jar
```

### 步骤 2：停止远程应用

```bash
# SSH 登录远程服务器
ssh skysi@192.168.54.68

# 查找并停止应用
ps aux | grep okx-trading
kill <PID>

# 或使用脚本
cd ~/okx-trading
./stop.sh
```

### 步骤 3：上传 JAR 文件

```bash
# 从本地上传
scp target/okx-trading-0.0.1-SNAPSHOT.jar \
    skysi@192.168.54.68:~/okx-trading/okx-trading.jar
```

### 步骤 4：上传配置文件

```bash
# 上传配置文件
scp src/main/resources/application.properties \
    skysi@192.168.54.68:~/okx-trading/
```

### 步骤 5：启动远程应用

```bash
# SSH 登录
ssh skysi@192.168.54.68

# 启动应用
cd ~/okx-trading
nohup java -jar okx-trading.jar \
    --spring.config.location=./application.properties \
    > logs/app.log 2>&1 &

# 查看日志
tail -f logs/app.log
```

### 步骤 6：验证部署

```bash
# 测试接口
curl http://192.168.54.68:8088/api/stock/market/test

# 访问 Swagger
open http://192.168.54.68:8088/swagger-ui.html
```

## 部署验证

### 1. 检查应用状态

```bash
# 在远程服务器上
ssh skysi@192.168.54.68

# 检查进程
ps aux | grep okx-trading

# 检查端口
netstat -tlnp | grep 8088

# 查看日志
tail -100 ~/okx-trading/logs/app.log
```

### 2. 测试股票接口

```bash
# 从本地测试
./test-remote-stock-api.sh

# 或手动测试
curl http://192.168.54.68:8088/api/stock/market/test
curl "http://192.168.54.68:8088/api/stock/market/kline/daily?tsCode=000001.SZ&limit=5"
```

### 3. 检查 Swagger UI

访问：http://192.168.54.68:8088/swagger-ui.html

应该能看到：
- ✅ 股票市场数据分组
- ✅ 6 个新增接口
- ✅ 原有的加密货币接口

## 常见问题

### 问题 1：上传失败

**错误：** `Permission denied` 或 `Connection refused`

**解决：**
```bash
# 检查 SSH 连接
ssh skysi@192.168.54.68

# 检查目录权限
ssh skysi@192.168.54.68 'ls -ld ~/okx-trading'

# 创建目录（如果不存在）
ssh skysi@192.168.54.68 'mkdir -p ~/okx-trading/logs'
```

### 问题 2：应用启动失败

**错误：** 应用无法启动或立即退出

**解决：**
```bash
# 查看完整日志
ssh skysi@192.168.54.68 'cat ~/okx-trading/logs/app.log'

# 检查配置
ssh skysi@192.168.54.68 'cat ~/okx-trading/application.properties'

# 检查 Java 版本
ssh skysi@192.168.54.68 'java -version'
```

### 问题 3：接口返回 404

**错误：** 访问接口返回 404 Not Found

**解决：**
```bash
# 检查应用是否完全启动
ssh skysi@192.168.54.68 'tail -100 ~/okx-trading/logs/app.log | grep "Started OkxTradingApplication"'

# 检查接口映射
ssh skysi@192.168.54.68 'grep "Mapped.*stock/market" ~/okx-trading/logs/app.log'

# 如果没有映射，说明 Bean 创建失败，检查错误日志
ssh skysi@192.168.54.68 'grep -i "error\|exception" ~/okx-trading/logs/app.log | grep -i "tushare\|stock"'
```

### 问题 4：Tushare API 连接失败

**错误：** 测试接口返回连接失败

**解决：**
```bash
# 检查网络连接
ssh skysi@192.168.54.68 'curl -I http://111.170.34.57:8010/'

# 检查代理配置（如果使用）
ssh skysi@192.168.54.68 'cat ~/okx-trading/application.properties | grep proxy'

# 测试 Tushare API
ssh skysi@192.168.54.68 'curl -X POST http://111.170.34.57:8010/ \
  -H "Content-Type: application/json" \
  -d "{\"api_name\":\"index_basic\",\"token\":\"krMJzeduYQNpQxOMToDCwfZGqmATGWnSalvyjEjbzJZuLZEvpMpEiRSEDKkPBFiu\",\"params\":{},\"fields\":\"ts_code,name\"}"'
```

## 回滚操作

如果新版本有问题，可以快速回滚：

```bash
# SSH 登录
ssh skysi@192.168.54.68

# 停止当前应用
cd ~/okx-trading
kill $(ps aux | grep okx-trading | grep -v grep | awk '{print $2}')

# 恢复备份
ls -lt okx-trading-backup-*.jar | head -1  # 查看最新备份
mv okx-trading-backup-YYYYMMDD-HHMMSS.jar okx-trading.jar

# 重新启动
nohup java -jar okx-trading.jar > logs/app.log 2>&1 &
```

## 监控和维护

### 查看日志

```bash
# 实时查看日志
ssh skysi@192.168.54.68 'tail -f ~/okx-trading/logs/app.log'

# 查看错误日志
ssh skysi@192.168.54.68 'grep -i error ~/okx-trading/logs/app.log'

# 查看最近的请求
ssh skysi@192.168.54.68 'grep "stock/market" ~/okx-trading/logs/app.log | tail -20'
```

### 重启应用

```bash
# 使用部署脚本重启
./deploy-stock-to-remote.sh

# 或手动重启
ssh skysi@192.168.54.68 << 'EOF'
    cd ~/okx-trading
    
    # 停止
    kill $(ps aux | grep okx-trading | grep -v grep | awk '{print $2}')
    sleep 3
    
    # 启动
    nohup java -jar okx-trading.jar > logs/app.log 2>&1 &
    
    # 检查
    sleep 5
    ps aux | grep okx-trading
EOF
```

### 性能监控

```bash
# 检查内存使用
ssh skysi@192.168.54.68 'ps aux | grep okx-trading'

# 检查磁盘空间
ssh skysi@192.168.54.68 'df -h ~/okx-trading'

# 检查日志大小
ssh skysi@192.168.54.68 'du -sh ~/okx-trading/logs/*'
```

## 自动化部署脚本说明

### deploy-stock-to-remote.sh

完整的自动化部署脚本，包括：
- 本地编译
- 远程停止
- 备份旧版本
- 上传新版本
- 启动应用
- 验证部署

### test-remote-stock-api.sh

远程 API 测试脚本，测试：
- Tushare 连接
- 股票列表
- K线数据
- 最新行情
- Swagger UI

## 最佳实践

1. **部署前测试**
   - 在本地完整测试所有功能
   - 运行单元测试和集成测试
   - 验证配置文件正确

2. **备份策略**
   - 每次部署前自动备份
   - 保留最近 5 个版本
   - 定期清理旧备份

3. **监控告警**
   - 监控应用启动状态
   - 监控接口响应时间
   - 监控错误日志

4. **文档更新**
   - 记录每次部署的变更
   - 更新 API 文档
   - 记录已知问题

## 快速命令参考

```bash
# 一键部署
./deploy-stock-to-remote.sh

# 测试远程 API
./test-remote-stock-api.sh

# 查看远程日志
ssh skysi@192.168.54.68 'tail -f ~/okx-trading/logs/app.log'

# 重启远程应用
ssh skysi@192.168.54.68 'cd ~/okx-trading && ./restart.sh'

# 检查远程应用状态
ssh skysi@192.168.54.68 'ps aux | grep okx-trading'
```

## 联系支持

如有问题，请提供：
1. 部署脚本输出
2. 远程应用日志
3. 测试脚本结果
4. 错误截图

---

**最后更新：** 2024-03-31  
**版本：** v1.0.0
