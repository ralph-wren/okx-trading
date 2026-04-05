# Kafka 监控服务快速开始

## 快速配置

### 1. 基本配置（使用默认值）

在 `application.properties` 中已包含默认配置，无需修改即可使用：

```properties
# Kafka 监控默认配置
kafka.monitor.enabled=true                                    # 启用监控
kafka.monitor.data-freshness-threshold-seconds=300            # 5分钟未收到数据告警
kafka.monitor.consumer-lag-threshold=1000                     # lag 超过 1000 条告警
kafka.monitor.alert-interval-minutes=30                       # 30分钟内不重复告警
```

### 2. 自定义配置（通过环境变量）

```bash
# 设置数据新鲜度阈值为 10 分钟
export KAFKA_MONITOR_DATA_FRESHNESS_THRESHOLD=600

# 设置 lag 阈值为 2000 条
export KAFKA_MONITOR_CONSUMER_LAG_THRESHOLD=2000

# 设置告警间隔为 1 小时
export KAFKA_MONITOR_ALERT_INTERVAL=60
```

### 3. 邮件配置（必需）

确保邮件服务已配置：

```properties
# 邮件配置
spring.mail.host=smtp.qq.com
spring.mail.port=587
spring.mail.username=your-email@qq.com
spring.mail.password=your-password
notification.email.recipient=alert-receiver@example.com
```

## 启动应用

```bash
cd okx-trading
mvn spring-boot:run
```

## 验证监控是否工作

### 查看启动日志

```
INFO  - Kafka 监控服务已启用
DEBUG - Kafka 数据新鲜度检查: 距离上次接收数据 45 秒
INFO  - Kafka 消费者 lag 检查: 总 lag = 150, 阈值 = 1000
```

### 测试数据新鲜度告警

1. 停止 WebSocket 连接或生产者
2. 等待 5 分钟（或配置的阈值时间）
3. 检查邮箱是否收到告警邮件

### 测试消费者 lag 告警

1. 停止消费者（注释掉 `@KafkaListener` 或设置 `kline.kafka.enabled=false`）
2. 让生产者继续发送数据
3. 等待 lag 积累超过阈值
4. 检查邮箱是否收到告警邮件

## 常见问题

### Q1: 收不到告警邮件？

**检查清单**：
- [ ] 邮件服务是否配置正确？
- [ ] `notification.email.recipient` 是否配置？
- [ ] `kafka.monitor.enabled=true`？
- [ ] 查看应用日志是否有邮件发送错误？

### Q2: 告警邮件太频繁？

**解决方法**：
- 增加 `kafka.monitor.alert-interval-minutes` 值（如 60）
- 适当提高阈值

### Q3: 监控不工作？

**检查清单**：
- [ ] `kline.kafka.enabled=true`？
- [ ] `kafka.monitor.enabled=true`？
- [ ] Kafka 是否正常运行？
- [ ] 查看应用日志是否有错误？

## 监控指标说明

### 数据新鲜度

- **含义**：距离上次接收 Kafka 数据的时间
- **正常值**：< 300 秒（5分钟）
- **告警条件**：> 300 秒

### 消费者 Lag

- **含义**：消费者未处理的消息数量
- **正常值**：< 1000 条
- **告警条件**：> 1000 条

## 配置建议

### 高频交易场景

```properties
kafka.monitor.data-freshness-threshold-seconds=60    # 1分钟
kafka.monitor.consumer-lag-threshold=500             # 500条
kafka.monitor.alert-interval-minutes=15              # 15分钟
```

### 低频交易场景

```properties
kafka.monitor.data-freshness-threshold-seconds=600   # 10分钟
kafka.monitor.consumer-lag-threshold=2000            # 2000条
kafka.monitor.alert-interval-minutes=60              # 60分钟
```

### 生产环境

```properties
kafka.monitor.data-freshness-threshold-seconds=300   # 5分钟
kafka.monitor.consumer-lag-threshold=1000            # 1000条
kafka.monitor.alert-interval-minutes=30              # 30分钟
```

## 更多信息

- 详细文档：[docs/KAFKA_MONITORING.md](docs/KAFKA_MONITORING.md)
- 实现总结：[KAFKA_MONITOR_IMPLEMENTATION.md](KAFKA_MONITOR_IMPLEMENTATION.md)
