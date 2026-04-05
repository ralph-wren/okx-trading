# Kafka 监控服务实现总结

## 实现内容

已完成 Kafka 监控服务的开发，用于监控 Kafka 数据流的健康状况并在异常时发送邮件告警。

## 新增文件

### 1. 服务接口和实现

- `src/main/java/com/okx/trading/service/KafkaMonitorService.java`
  - Kafka 监控服务接口
  - 定义了数据新鲜度检查和消费者 lag 检查方法

- `src/main/java/com/okx/trading/service/impl/KafkaMonitorServiceImpl.java`
  - Kafka 监控服务实现类
  - 实现数据新鲜度监控（每分钟检查一次）
  - 实现消费者 lag 监控（每2分钟检查一次）
  - 集成邮件告警功能

### 2. 文档

- `docs/KAFKA_MONITORING.md`
  - 详细的 Kafka 监控服务使用文档
  - 包含配置说明、使用场景、最佳实践等

- `KAFKA_MONITOR_IMPLEMENTATION.md`（本文件）
  - 实现总结文档

## 修改文件

### 1. 配置文件

`src/main/resources/application.properties`
- 新增 Kafka 监控配置项：
  ```properties
  kafka.monitor.enabled=true
  kafka.monitor.data-freshness-threshold-seconds=300
  kafka.monitor.consumer-lag-threshold=1000
  kafka.monitor.alert-interval-minutes=30
  ```

### 2. 生产者服务

`src/main/java/com/okx/trading/service/impl/KlineKafkaProducerServiceImpl.java`
- 注入 `KafkaMonitorService`
- 在成功发送数据后更新监控时间

### 3. 消费者服务

`src/main/java/com/okx/trading/service/KlineKafkaConsumerService.java`
- 注入 `KafkaMonitorService`
- 在接收到数据后更新监控时间

## 功能特性

### 1. 数据新鲜度监控

- **监控频率**：每分钟检查一次
- **监控指标**：距离上次接收数据的时间
- **告警条件**：超过配置的阈值时间（默认 300 秒）
- **告警内容**：
  - Topic 名称
  - 距离上次接收数据的时间
  - 告警阈值
  - 建议操作

### 2. 消费者 Lag 监控

- **监控频率**：每2分钟检查一次
- **监控指标**：消费者组的总 lag 和各分区 lag
- **告警条件**：总 lag 超过配置的阈值（默认 1000 条）
- **告警内容**：
  - Topic 名称和消费者组 ID
  - 总 lag 数量
  - 各分区 lag 详情（表格形式）
  - 建议操作

### 3. 智能告警机制

- **避免频繁告警**：同一问题在告警间隔时间内（默认 30 分钟）只告警一次
- **HTML 格式邮件**：美观易读的告警邮件
- **详细的问题诊断**：提供建议操作帮助快速定位问题

## 配置说明

### 默认配置

```properties
# 启用监控
kafka.monitor.enabled=true

# 数据新鲜度阈值：300秒（5分钟）
kafka.monitor.data-freshness-threshold-seconds=300

# 消费者 lag 阈值：1000条消息
kafka.monitor.consumer-lag-threshold=1000

# 告警间隔：30分钟
kafka.monitor.alert-interval-minutes=30
```

### 环境变量覆盖

可以通过环境变量覆盖默认配置：

```bash
export KAFKA_MONITOR_ENABLED=true
export KAFKA_MONITOR_DATA_FRESHNESS_THRESHOLD=600
export KAFKA_MONITOR_CONSUMER_LAG_THRESHOLD=2000
export KAFKA_MONITOR_ALERT_INTERVAL=60
```

## 使用场景

### 场景 1：生产者故障检测

当 WebSocket 连接断开或生产者停止工作时：
- 数据新鲜度监控会在 5 分钟后检测到异常
- 自动发送告警邮件通知管理员
- 邮件中包含建议操作（检查 WebSocket、生产者、网络等）

### 场景 2：消费者积压检测

当消费者处理速度过慢导致消息积压时：
- 消费者 lag 监控会检测到 lag 超过阈值
- 自动发送告警邮件，包含各分区 lag 详情
- 邮件中包含建议操作（优化处理逻辑、增加消费者实例等）

### 场景 3：系统健康监控

定期监控 Kafka 数据流健康状况：
- 每分钟检查数据新鲜度
- 每2分钟检查消费者 lag
- 及时发现潜在问题

## 技术实现

### 核心技术

1. **独立线程池**：使用 `kafkaMonitorScheduler` 线程池（2个线程）
   - 线程名称前缀：`Kafka监控-`
   - 守护线程，随主线程退出
   - 不影响主业务线程和其他线程池
2. **ScheduledExecutorService**：定时任务调度
3. **Kafka AdminClient**：获取消费者组和分区信息
4. **JavaMailSender + @Async**：异步发送 HTML 格式邮件
5. **AtomicLong**：线程安全的时间戳记录

### 监控流程

```
数据流动：
WebSocket → 生产者 → Kafka → 消费者 → 策略处理
              ↓                    ↓
         更新监控时间          更新监控时间

监控检查：
定时任务 → 检查数据新鲜度 → 超过阈值 → 发送告警
定时任务 → 检查消费者 lag → 超过阈值 → 发送告警
```

### 条件启用

监控服务只在以下条件下启用：
- `kline.kafka.enabled=true`（Kafka 功能已启用）
- `kafka.monitor.enabled=true`（监控功能已启用）

## 测试建议

### 1. 数据新鲜度测试

```bash
# 停止生产者或 WebSocket 连接
# 等待 5 分钟（或配置的阈值时间）
# 检查是否收到告警邮件
```

### 2. 消费者 Lag 测试

```bash
# 停止消费者
# 让生产者继续发送数据
# 等待 lag 积累超过阈值
# 检查是否收到告警邮件
```

### 3. 告警间隔测试

```bash
# 触发告警
# 在告警间隔时间内再次触发
# 验证不会收到重复告警
# 等待告警间隔时间后再次触发
# 验证会收到新的告警
```

## 监控指标

### 关键指标

1. **最后数据接收时间**：`lastDataReceivedTime`
2. **总消费者 lag**：所有分区 lag 之和
3. **各分区 lag**：每个分区的 lag 详情

### 日志输出

```
INFO  - Kafka 消费者 lag 检查: 总 lag = 150, 阈值 = 1000
DEBUG - Kafka 数据新鲜度检查: 距离上次接收数据 45 秒
DEBUG - 更新 Kafka 数据接收时间: 2026-04-06 12:30:45
WARN  - 已发送 Kafka 数据新鲜度告警邮件，距离上次接收数据 350 秒
WARN  - 已发送 Kafka 消费者 lag 告警邮件，总 lag: 1500
```

## 后续优化建议

### 1. 监控指标扩展

- 添加生产者发送失败率监控
- 添加消费者处理耗时监控
- 添加 Kafka 分区健康状态监控

### 2. 告警方式扩展

- 支持钉钉、企业微信等即时通讯工具告警
- 支持短信告警（紧急情况）
- 支持 Webhook 告警

### 3. 监控可视化

- 集成 Prometheus + Grafana
- 提供监控仪表板
- 历史数据趋势分析

### 4. 自动恢复

- 检测到异常时自动尝试重启生产者/消费者
- 自动调整消费者实例数量
- 自动清理积压消息（可选）

## 总结

Kafka 监控服务已完整实现，提供了：
- ✅ 数据新鲜度监控
- ✅ 消费者 lag 监控
- ✅ 邮件告警功能
- ✅ 智能告警间隔
- ✅ 详细的配置选项
- ✅ 完善的文档

所有配置项都可以通过配置文件或环境变量灵活调整，满足不同场景的监控需求。
