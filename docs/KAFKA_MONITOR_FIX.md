# Kafka 监控服务不打印日志问题修复

## 问题描述

用户报告 `KafkaMonitorServiceImpl` 不打印日志了,怀疑监控服务没有正常运行。

## 问题分析

### 根本原因

`KafkaMonitorServiceImpl` 有一个条件注解:

```java
@ConditionalOnProperty(name = "kline.kafka.enabled", havingValue = "true")
```

这意味着**只有当 `kline.kafka.enabled=true` 时,监控服务才会启动**。

但是当前配置:
```properties
kline.kafka.enabled=${KLINE_KAFKA_ENABLED:false}
```

默认值是 `false`,所以监控服务根本没有启动,自然不会打印任何日志!

### 为什么会这样?

之前的设计逻辑:
- `kline.kafka.enabled=true`: okx-trading 自己采集数据并写入 Kafka,需要监控
- `kline.kafka.enabled=false`: 不采集数据,不需要监控

但是现在的架构已经改变:
- Kafka Consumer **始终启动**,无论 `kline.kafka.enabled` 如何配置
- 数据可能来自 data-warehouse 或 okx-trading 自己
- 因此监控服务也应该**始终启动**

## 解决方案

修改 `KafkaMonitorServiceImpl` 的条件注解,让它始终启动:

### 修改前
```java
@Service
@ConditionalOnProperty(name = "kline.kafka.enabled", havingValue = "true")
public class KafkaMonitorServiceImpl implements KafkaMonitorService {
```

### 修改后
```java
@Service
@ConditionalOnProperty(name = "kafka.monitor.enabled", havingValue = "true", matchIfMissing = true)
public class KafkaMonitorServiceImpl implements KafkaMonitorService {
```

### 配置说明

新的条件注解:
- 使用独立的配置项: `kafka.monitor.enabled`
- `matchIfMissing = true`: 如果配置项不存在,默认启用
- 这样监控服务默认会启动,除非明确配置 `kafka.monitor.enabled=false`

现有配置项:
```properties
# Kafka 监控配置
kafka.monitor.enabled=${KAFKA_MONITOR_ENABLED:true}
kafka.monitor.data-freshness-threshold-seconds=${KAFKA_MONITOR_DATA_FRESHNESS_THRESHOLD:300}
kafka.monitor.consumer-lag-threshold=${KAFKA_MONITOR_CONSUMER_LAG_THRESHOLD:100}
kafka.monitor.alert-interval-minutes=${KAFKA_MONITOR_ALERT_INTERVAL:30}
```

## 监控功能

`KafkaMonitorServiceImpl` 提供两个监控功能:

### 1. 数据新鲜度监控

- 检查频率: 每 60 秒
- 监控指标: 距离上次接收 Kafka 数据的时间
- 告警阈值: 默认 300 秒 (5 分钟)
- 告警方式: 邮件通知

日志示例:
```
Kafka 数据新鲜度检查: 距离上次接收数据 45 秒
```

### 2. 消费者 Lag 监控

- 检查频率: 每 120 秒
- 监控指标: Consumer Group 的总 lag (积压消息数)
- 告警阈值: 默认 100 条消息
- 告警方式: 邮件通知

日志示例:
```
Kafka 消费者 lag 检查: 总 lag = 25, 阈值 = 100
分区 0 lag: 15 (当前偏移: 6280, 最新偏移: 6295)
分区 1 lag: 10 (当前偏移: 6285, 最新偏移: 6295)
```

## 验证步骤

1. 编译项目:
```bash
cd okx-trading
mvn clean compile -DskipTests
```

2. 启动应用 (Rebel 自动编译生效)

3. 检查日志,确认监控服务启动:
```
✅ 初始化 Kafka 监控任务，使用独立线程池: ScheduledThreadPoolExecutor
✅ Kafka 监控任务已启动: 数据新鲜度检查(每60秒), 消费者lag检查(每120秒)
```

4. 等待 3 分钟后,检查是否有监控日志:
```
执行数据新鲜度检查，线程: kafka-monitor-1
Kafka 数据新鲜度检查: 距离上次接收数据 45 秒
执行消费者lag检查，线程: kafka-monitor-2
Kafka 消费者 lag 检查: 总 lag = 25, 阈值 = 100
```

## 配置项说明

### 启用/禁用监控

```properties
# 启用监控（默认）
kafka.monitor.enabled=true

# 禁用监控
kafka.monitor.enabled=false
```

### 数据新鲜度阈值

```properties
# 5 分钟未收到数据则告警
kafka.monitor.data-freshness-threshold-seconds=300

# 10 分钟未收到数据则告警
kafka.monitor.data-freshness-threshold-seconds=600
```

### 消费者 Lag 阈值

```properties
# lag 超过 100 条消息则告警
kafka.monitor.consumer-lag-threshold=100

# lag 超过 1000 条消息则告警
kafka.monitor.consumer-lag-threshold=1000
```

### 告警间隔

```properties
# 每 30 分钟最多发送一次告警
kafka.monitor.alert-interval-minutes=30

# 每 60 分钟最多发送一次告警
kafka.monitor.alert-interval-minutes=60
```

## 总结

问题的根本原因是 `KafkaMonitorServiceImpl` 的启动条件绑定到了 `kline.kafka.enabled`,而这个配置项现在默认是 `false`。

通过修改条件注解,使用独立的 `kafka.monitor.enabled` 配置项,并设置 `matchIfMissing = true`,确保监控服务默认启动,与 Kafka Consumer 的启动逻辑保持一致。

## 相关文件

- `okx-trading/src/main/java/com/okx/trading/service/impl/KafkaMonitorServiceImpl.java`
- `okx-trading/src/main/resources/application.properties`
