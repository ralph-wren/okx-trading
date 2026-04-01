# 脚本目录

本目录包含项目的所有Shell脚本文件。

## 脚本分类

### 部署脚本
- `deploy-backend.sh` - 后端部署
- `deploy-mysql-redis.sh` - MySQL和Redis部署
- `deploy-mysql-redis-v2.sh` - MySQL和Redis部署（v2版本）
- `deploy-stock-to-remote.sh` - 股票功能远程部署
- `deploy.sh` - 通用部署脚本
- `quick-deploy.sh` - 快速部署

### 数据库脚本
- `import-schema.sh` - 导入数据库schema
- `import-schema-v2.sh` - 导入数据库schema（v2版本）
- `import-data.sh` - 导入数据
- `reset-and-import-schema.sh` - 重置并导入schema
- `fix-mysql.sh` - 修复MySQL问题

### 应用管理
- `rebuild-and-start.sh` - 重新编译并启动
- `restart-app.sh` - 重启应用
- `check-remote-app.sh` - 检查远程应用状态
- `check-deployment-status.sh` - 检查部署状态
- `check-app-logs-and-clash.sh` - 检查应用日志和Clash状态

### 代理相关（Clash）
- `install-clash-meta.sh` - 安装Clash Meta
- `fix-clash-installation.sh` - 修复Clash安装
- `start-clash-and-test-app.sh` - 启动Clash并测试应用
- `start-and-create-clash-script.sh` - 启动并创建Clash脚本
- `restart-clash.sh` - 重启Clash
- `setup-system-proxy.sh` - 设置系统代理
- `test-clash-proxy.sh` - 测试Clash代理
- `test-new-session-proxy.sh` - 测试新会话代理
- `check-clash-error.sh` - 检查Clash错误
- `verify-proxy-config.sh` - 验证代理配置

### 测试脚本
- `test-stock-ticker-api.sh` - 测试股票行情API
- `test-stock-backtest.sh` - 测试股票回测
- `test-remote-stock-api.sh` - 测试远程股票API
- `test-tushare-api.sh` - 测试Tushare API
- `diagnose-swagger.sh` - 诊断Swagger问题

### 远程部署
- `create-remote-deploy-script.sh` - 创建远程部署脚本

## 使用说明

所有脚本都应该从项目根目录执行，例如：

```bash
# 从项目根目录执行
./scripts/quick-deploy.sh

# 或者进入scripts目录执行
cd scripts
./quick-deploy.sh
```

## 测试脚本

Python测试脚本位于 `../src/test/scripts/` 目录。
