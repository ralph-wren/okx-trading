# OKX Trading 数据库部署总结

## 部署完成状态 ✅

### 1. 服务器信息
- **服务器地址**: 192.168.54.68
- **用户**: skysi
- **部署时间**: 2026-03-31

### 2. 已部署服务

#### MySQL 8.0.45
- **容器名**: okx-trading-mysql
- **端口**: 3306
- **用户**: root
- **密码**: Password123?
- **数据库**: okx_trading
- **字符集**: utf8mb4_unicode_ci
- **状态**: ✅ 运行中

#### Redis 6.2.21
- **容器名**: okx-trading-redis
- **端口**: 6379
- **密码**: 无
- **持久化**: AOF (appendonly yes)
- **状态**: ✅ 运行中

### 3. 数据库表结构 (共15个表)

#### 核心业务表
1. **candlestick_history** - K线历史数据
2. **real_time_strategy** - 实时策略
3. **real_time_orders** - 实时订单
4. **strategy_info** - 策略信息
5. **strategy_conversation** - 策略对话记录

#### 回测相关表
6. **backtest_summary** - 回测汇总
7. **backtest_trade** - 回测交易详情
8. **backtest_equity_curve** - 回测资金曲线

#### 其他功能表
9. **fund_data** - 资金中心数据
10. **indicator_distribution** - 指标分布
11. **telegram_channels** - Telegram频道
12. **telegram_messages** - Telegram消息
13. **trades** - 交易记录
14. **users** - 用户表
15. **user_api_keys** - 用户API密钥


### 4. 部署脚本说明

#### 已创建的脚本
- **deploy-mysql-redis-v2.sh** - MySQL和Redis部署脚本
- **fix-mysql.sh** - MySQL修复脚本
- **reset-and-import-schema.sh** - 重置并导入数据库schema
- **check-deployment-status.sh** - 检查部署状态

#### 使用方法
```bash
# 检查部署状态
./check-deployment-status.sh

# 重新导入schema（如果需要）
./reset-and-import-schema.sh
```

### 5. 应用配置

在应用的 `application.properties` 中使用以下配置：

```properties
# MySQL配置
spring.datasource.url=jdbc:mysql://192.168.54.68:3306/okx_trading?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true
spring.datasource.username=root
spring.datasource.password=Password123?

# Redis配置
spring.redis.host=192.168.54.68
spring.redis.port=6379
```

### 6. 验证步骤

✅ Docker容器运行正常
✅ MySQL连接测试通过
✅ Redis连接测试通过
✅ 数据库schema导入成功
✅ 所有15个表创建完成

### 7. 下一步操作

1. 更新应用配置文件中的数据库连接信息
2. 部署应用到服务器
3. 测试应用与数据库的连接

### 8. 注意事项

- MySQL密码包含特殊字符，在配置文件中需要正确转义
- 容器设置为自动重启 (restart: always)
- 数据持久化到宿主机目录 ~/okx-trading-db/
