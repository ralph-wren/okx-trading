# Kafka 监控服务文档

## 概述

Kafka 监控服务用于实时监控 Kafka 数据流的健康状况，包括：
1. **数据新鲜度监控**：检测 Kafka 是否持续接收新数据
2. **消费者 Lag 监控**：检测消费者是否存在消息积压

当检测到异常情况时，系统会自动发送邮件告警。

## 功能特性

### 1. 数据新鲜度监控

- 实时跟踪最后一次接收 Kafka 数据的时间
- 定时检查（每分钟）数据是否在阈值时间内更新
- 超过阈值时间未收到新数据时发送告警邮件

### 2. 消费者 Lag 监控

- 定时检查（每2分钟）消费者的消息积压情况
- 计算每个分区的 lag 和总 lag
- 当总 lag 超过阈值时发送告警邮件
- 提供各分区 lag 详情

### 3. 智能告警

- 避免频繁告警：设置告警间隔，同一问题在间隔时间内只告警一次
- 详细的告警信息：包含问题详情、建议操作等
- HTML 格式邮件：美观易读的告警邮件

## 配置说明

在 `application.properties` 中配置以下参数：

```properties
# Kafka 监控配置
# 是否启用 Kafka 监控
kafka.monitor.enabled=${KAFKA_MONITOR_ENABLED:true}

# 数据新鲜度阈值（秒），超过此时间未收到新数据则告警
# 默认：300秒（5分钟）
kafka.monitor.data-freshness-threshold-seconds=${KAFKA_MONITOR_DATA_FRESHNESS_THRESHOLD:300}

# 消费者 lag 阈值（条），超过此数量则告警
# 默认：1000条消息
kafka.monitor.consumer-lag-threshold=${KAFKA_MONITOR_CONSUMER_LAG_THRESHOLD:1000}

# 告警间隔（分钟），避免频繁告警
# 默认：30分钟
kafka.monitor.alert-interval-minutes=${KAFKA_MONITOR_ALERT_INTERVAL:30}
```

### 配置参数说明

| 参数 | 说明 | 默认值 | 推荐值 |
|------|------|--------|--------|
| `kafka.monitor.enabled` | 是否启用监控 | `true` | `true` |
| `kafka.monitor.data-freshness-threshold-seconds` | 数据新鲜度阈值（秒） | `300` | 根据数据更新频率调整，建议 300-600 |
| `kafka.monitor.consumer-lag-threshold` | 消费者 lag 阈值（条） | `1000` | 根据消息量调整，建议 1000-5000 |
| `kafka.monitor.alert-interval-minutes` | 告警间隔（分钟） | `30` | 建议 30-60 |

## 环境变量配置

可以通过环境变量覆盖默认配置：

```bash
# 启用监控
export KAFKA_MONITOR_ENABLED=true

# 设置数据新鲜度阈值为 10 分钟
export KAFKA_MONITOR_DATA_FRESHNESS_THRESHOLD=600

# 设置 lag 阈值为 2000 条
export KAFKA_MONITOR_CONSUMER_LAG_THRESHOLD=2000

# 设置告警间隔为 60 分钟
export KAFKA_MONITOR_ALERT_INTERVAL=60
```

## 告警邮件示例

### 数据新鲜度告警

当 Kafka 超过阈值时间未收到新数据时，会收到如下告警邮件：

**主题**：【Kafka 监控告警】数据超过 X 分钟未更新

**内容**：
- Topic 名称
- 告警时间
- 距离上次接收数据的时间
- 告警阈值
- 建议操作（检查生产者、WebSocket、网络等）

### 消费者 Lag 告警

当消费者 lag 超过阈值时，会收到如下告警邮件：

**主题**：【Kafka 监控告警】消费者 lag 积压过多（X 条消息）

**内容**：
- Topic 名称
- 消费者组 ID
- 告警时间
- 总 lag 数量
- 各分区 lag 详情（表格形式）
- 建议操作（检查消费者、处理速度等）

## 监控原理

### 数据新鲜度监控

```
生产者发送数据 → 更新最后接收时间
                ↓
消费者接收数据 → 更新最后接收时间
                ↓
定时任务（每分钟）→ 检查距离上次接收时间
                ↓
超过阈值 → 发送告警邮件
```

### 消费者 Lag 监控

```
定时任务（每2分钟）
    ↓
使用 Kafka AdminClient 获取消费者组偏移量
    ↓
获取各分区的最新偏移量
    ↓
计算 lag = 最新偏移量 - 当前偏移量
    ↓
总 lag 超过阈值 → 发送告警邮件
```

## 使用场景

### 场景 1：生产者故障

**问题**：WebSocket 连接断开，生产者停止发送数据

**监控响应**：
- 数据新鲜度监控检测到超过 5 分钟未收到新数据
- 发送告警邮件通知管理员
- 管理员检查 WebSocket 连接和生产者日志

### 场景 2：消费者处理缓慢

**问题**：消费者处理逻辑耗时过长，导致消息积压

**监控响应**：
- 消费者 lag 监控检测到 lag 超过 1000 条
- 发送告警邮件，包含各分区 lag 详情
- 管理员优化消费者处理逻辑或增加消费者实例

### 场景 3：网络故障

**问题**：网络不稳定，Kafka 连接中断

**监控响应**：
- 数据新鲜度监控和 lag 监控同时触发告警
- 管理员检查网络连接和 Kafka 服务状态

## 最佳实践

### 1. 合理设置阈值

- **数据新鲜度阈值**：根据数据更新频率设置
  - 高频数据（秒级）：建议 60-300 秒
  - 中频数据（分钟级）：建议 300-600 秒
  - 低频数据（小时级）：建议 1800-3600 秒

- **Lag 阈值**：根据消息量和处理能力设置
  - 低流量：建议 500-1000 条
  - 中流量：建议 1000-5000 条
  - 高流量：建议 5000-10000 条

### 2. 告警间隔设置

- 避免告警疲劳：设置合理的告警间隔（建议 30-60 分钟）
- 紧急情况：可以临时降低告警间隔

### 3. 监控日志

监控服务会输出详细的日志信息：

```
INFO  - Kafka 消费者 lag 检查: 总 lag = 150, 阈值 = 1000
DEBUG - Kafka 数据新鲜度检查: 距离上次接收数据 45 秒
WARN  - 已发送 Kafka 数据新鲜度告警邮件，距离上次接收数据 350 秒
```

### 4. 定期检查

- 定期查看监控日志，了解系统运行状况
- 根据实际情况调整阈值和告警间隔
- 关注告警邮件，及时处理问题

## 故障排查

### 问题 1：收不到告警邮件

**可能原因**：
1. 邮件服务未配置或配置错误
2. 监控服务未启用
3. 邮件接收地址未配置

**解决方法**：
1. 检查 `application.properties` 中的邮件配置
2. 确认 `kafka.monitor.enabled=true`
3. 确认 `notification.email.recipient` 已配置

### 问题 2：频繁收到告警邮件

**可能原因**：
1. 阈值设置过低
2. 告警间隔设置过短
3. 系统确实存在问题

**解决方法**：
1. 适当提高阈值
2. 增加告警间隔时间
3. 检查系统日志，排查根本问题

### 问题 3：监控服务不工作

**可能原因**：
1. Kafka 未启用（`kline.kafka.enabled=false`）
2. 监控服务未启用
3. 依赖服务未注入

**解决方法**：
1. 确认 `kline.kafka.enabled=true`
2. 确认 `kafka.monitor.enabled=true`
3. 检查应用启动日志

## 技术实现

### 核心类

- `KafkaMonitorService`：监控服务接口
- `KafkaMonitorServiceImpl`：监控服务实现
- `KlineKafkaProducerServiceImpl`：生产者服务（集成监控）
- `KlineKafkaConsumerService`：消费者服务（集成监控）

### 定时任务

使用 Spring `@Scheduled` 注解实现定时监控：

```java
// 数据新鲜度检查（每分钟）
@Scheduled(fixedRate = 60000)
public void checkDataFreshness() { ... }

// 消费者 lag 检查（每2分钟）
@Scheduled(fixedRate = 120000)
public void checkConsumerLag() { ... }
```

### Kafka AdminClient

使用 Kafka AdminClient API 获取消费者组和分区信息：

```java
AdminClient adminClient = AdminClient.create(kafkaAdmin.getConfigurationProperties());
ListConsumerGroupOffsetsResult offsetsResult = adminClient.listConsumerGroupOffsets(consumerGroupId);
```

## 总结

Kafka 监控服务提供了全面的 Kafka 健康监控能力，帮助及时发现和解决数据流问题。通过合理配置阈值和告警间隔，可以在保证监控效果的同时避免告警疲劳。
