# Kafka K线数据架构总结

## 架构概览

项目支持两种 K线数据采集模式:

### 模式 1: data-warehouse 统一采集 (推荐)
```
OKX WebSocket → data-warehouse (Flink) → Kafka (OKX原始格式) → okx-trading (Consumer) → RealTimeStrategyManager
```

**配置**: `kline.kafka.enabled=false`

**特点**:
- ✅ 数据采集集中管理
- ✅ 减少 WebSocket 连接数
- ✅ 更好的可扩展性
- ✅ 统一的数据质量控制

### 模式 2: okx-trading 自己采集
```
OKX WebSocket → okx-trading (Producer) → Kafka (OKX原始格式) → okx-trading (Consumer) → RealTimeStrategyManager
```

**配置**: `kline.kafka.enabled=true`

**特点**:
- ✅ 独立部署,不依赖 data-warehouse
- ⚠️ 需要管理 WebSocket 连接
- ⚠️ 每个实例都需要订阅

## 核心组件

### 1. Kafka Producer (KlineKafkaProducerServiceImpl)

**启动条件**: `kline.kafka.enabled=true`

**功能**:
- 接收 OKX WebSocket 的 K线数据
- 直接发送 OKX 原始格式到 Kafka (不包装)
- 使用 `symbol_interval` 作为消息 key,确保同一币种同一周期的数据顺序

**关键代码**:
```java
String key = symbol + "_" + interval;  // 例如: BTC-USDT_1D
kafkaTemplate.send(topic, key, klineData.toJSONString());
```

**数据格式**:
```json
{
  "arg": {"channel": "candle1D", "instId": "BTC-USDT"},
  "data": [["1775404800000", "67304.5", "70273.4", ...]]
}
```

### 2. Kafka Consumer (KlineKafkaConsumerServiceImpl)

**启动条件**: 始终启动 (无条件)

**功能**:
- 从 Kafka 消费 K线数据
- 解析 OKX 原始格式
- 更新邮件通知服务的最新价格
- **关键**: 调用 `RealTimeStrategyManager.handleNewKlineData()` 触发策略信号

**关键代码**:
```java
@KafkaListener(
    topics = "${kline.kafka.topic}",
    groupId = "${spring.kafka.consumer.group-id}"
)
public void consumeKlineData(
        @Payload String message,
        @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
        @Header(KafkaHeaders.OFFSET) long offset) {
    // 解析 OKX 原始格式
    // 调用 realTimeStrategyManager.handleNewKlineData()
}
```

### 3. Kafka Monitor (KafkaMonitorServiceImpl)

**启动条件**: `kafka.monitor.enabled=true` (默认启用)

**功能**:
- 监控数据新鲜度 (每 60 秒检查一次)
- 监控消费者 lag (每 120 秒检查一次)
- 超过阈值时发送邮件告警

**配置项**:
```properties
kafka.monitor.enabled=true
kafka.monitor.data-freshness-threshold-seconds=300
kafka.monitor.consumer-lag-threshold=100
kafka.monitor.alert-interval-minutes=30
```

### 4. WebSocket Reconnect Listener (WebSocketReconnectEventListener)

**启动条件**: 始终启动

**功能**:
- 监听 WebSocket 重连事件
- **只有当 `kline.kafka.enabled=true` 时才重新订阅**
- 如果 `kline.kafka.enabled=false`,数据来自 Kafka,不需要订阅

**关键代码**:
```java
public void handleWebSocketReconnect(WebSocketReconnectEvent event) {
    if (!klineKafkaEnabled) {
        log.info("kline.kafka.enabled=false，数据来自 Kafka，跳过 WebSocket 重新订阅");
        return;
    }
    // 重新订阅逻辑
}
```

### 5. Real-Time Strategy Manager (RealTimeStrategyManager)

**启动条件**: 始终启动

**功能**:
- 接收 K线数据 (从 `handleNewKlineData()` 方法)
- 更新 BarSeries
- 执行策略信号判断 (`processStrategySignal()`)
- 执行交易信号

**数据来源**:
- 模式 1: Kafka Consumer → handleNewKlineData()
- 模式 2: Kafka Consumer → handleNewKlineData()

## 数据顺序保证

### Kafka 分区策略

使用 `symbol_interval` 作为消息 key:
```java
String key = symbol + "_" + interval;  // 例如: BTC-USDT_1D
```

**保证**:
- 同一个 key 的消息会发送到同一个分区
- 同一个分区内的消息顺序严格保证
- 因此,同一币种同一周期的 K线数据顺序不会乱

**示例**:
```
BTC-USDT_1D → Partition 0 → 顺序: msg1, msg2, msg3, ...
ETH-USDT_1D → Partition 1 → 顺序: msg1, msg2, msg3, ...
BTC-USDT_4H → Partition 2 → 顺序: msg1, msg2, msg3, ...
```

## 配置说明

### 推荐配置 (模式 1)

```properties
# 不启用 Kafka Producer 和 WebSocket 订阅
kline.kafka.enabled=false

# Kafka 服务器地址
spring.kafka.bootstrap-servers=localhost:9093

# Kafka Consumer 配置
spring.kafka.consumer.group-id=okx-trading-kline-consumer
spring.kafka.consumer.auto-offset-reset=latest
spring.kafka.consumer.enable-auto-commit=true

# K线数据 Topic
kline.kafka.topic=okx-kline-data

# Kafka 监控配置
kafka.monitor.enabled=true
kafka.monitor.data-freshness-threshold-seconds=300
kafka.monitor.consumer-lag-threshold=100
kafka.monitor.alert-interval-minutes=30
```

### 独立部署配置 (模式 2)

```properties
# 启用 Kafka Producer 和 WebSocket 订阅
kline.kafka.enabled=true

# 其他配置同上
```

## 问题修复记录

### 1. Kafka Consumer 不触发策略信号

**问题**: 新创建的 `KafkaKlineConsumerServiceImpl` 只更新 Redis,没有调用 `RealTimeStrategyManager.handleNewKlineData()`

**解决**: 
- 删除重复的新类
- 保留旧类并改为接口+实现类形式
- 移除条件启动限制,让 Consumer 始终启动
- 修复数据格式解析,直接解析 OKX 原始格式

**文档**: `okx-trading/docs/KAFKA_CONSUMER_FIX.md`

### 2. Kafka Monitor 不打印日志

**问题**: `KafkaMonitorServiceImpl` 的启动条件绑定到 `kline.kafka.enabled=true`,但默认配置是 `false`

**解决**:
- 修改条件注解为 `kafka.monitor.enabled`
- 设置 `matchIfMissing = true`,默认启用
- 与 Kafka Consumer 的启动逻辑保持一致

**文档**: `okx-trading/docs/KAFKA_MONITOR_FIX.md`

### 3. WebSocket 重连时不应该重新订阅

**问题**: 当 `kline.kafka.enabled=false` 时,WebSocket 重连仍然会重新订阅,但数据应该来自 Kafka

**解决**:
- 在 `WebSocketReconnectEventListener` 中添加 `kline.kafka.enabled` 检查
- 只有当 `kline.kafka.enabled=true` 时才重新订阅
- 如果 `kline.kafka.enabled=false`,跳过重新订阅

### 4. 数据格式不一致

**问题**: okx-trading 和 data-warehouse 往 Kafka 写的数据格式不一致

**解决**:
- 统一使用 OKX 原始格式 (不包装)
- `KlineKafkaProducerServiceImpl` 直接发送 OKX 数据
- `KlineKafkaConsumerServiceImpl` 解析 OKX 原始格式

## 验证步骤

### 1. 验证 Kafka Consumer 启动

```bash
# 查看日志
grep "初始化 Kafka K线数据消费者" logs/application.log
```

### 2. 验证数据消费

```bash
# 查看消费日志
grep "接收到 Kafka 消息" logs/application.log
grep "从 Kafka 处理 K线数据" logs/application.log
```

### 3. 验证策略信号触发

```bash
# 查看策略信号日志
grep "处理策略信号" logs/application.log
```

### 4. 验证监控服务

```bash
# 查看监控日志
grep "Kafka 数据新鲜度检查" logs/application.log
grep "Kafka 消费者 lag 检查" logs/application.log
```

### 5. 验证 WebSocket 重连

```bash
# 查看重连日志
grep "收到WebSocket重连事件" logs/application.log
grep "跳过 WebSocket 重新订阅" logs/application.log  # 当 kline.kafka.enabled=false
```

## 相关文件

### 核心代码
- `okx-trading/src/main/java/com/okx/trading/service/KlineKafkaConsumerService.java` (接口)
- `okx-trading/src/main/java/com/okx/trading/service/impl/KlineKafkaConsumerServiceImpl.java` (实现)
- `okx-trading/src/main/java/com/okx/trading/service/impl/KlineKafkaProducerServiceImpl.java`
- `okx-trading/src/main/java/com/okx/trading/service/impl/KafkaMonitorServiceImpl.java`
- `okx-trading/src/main/java/com/okx/trading/listener/WebSocketReconnectEventListener.java`
- `okx-trading/src/main/java/com/okx/trading/strategy/RealTimeStrategyManager.java`

### 配置文件
- `okx-trading/src/main/resources/application.properties`

### 文档
- `okx-trading/docs/KAFKA_CONSUMER_FIX.md`
- `okx-trading/docs/KAFKA_MONITOR_FIX.md`
- `okx-trading/docs/KAFKA_ARCHITECTURE_SUMMARY.md` (本文档)

## 总结

通过一系列修复,确保了:
1. ✅ Kafka Consumer 始终启动并正确触发策略信号
2. ✅ Kafka Monitor 始终启动并监控数据状态
3. ✅ WebSocket 重连时根据配置决定是否重新订阅
4. ✅ 数据格式统一为 OKX 原始格式
5. ✅ 同一币种同一周期的数据顺序保证
6. ✅ 支持两种部署模式,灵活切换

整个架构现在更加清晰、可靠、易于维护!
