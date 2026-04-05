# Kafka 集成说明文档

## 概述

为了解决 WebSocket 实时行情数据在任务重启后丢失的问题，我们引入了 Kafka 作为可选的数据缓冲层。

## 架构设计

### 数据流向

#### 未启用 Kafka（默认）
```
WebSocket → handleKlineMessage → 直接处理 → RealTimeStrategyManager
```

#### 启用 Kafka
```
WebSocket → handleKlineMessage → Kafka Producer → Kafka Topic
                                                        ↓
                                    Kafka Consumer → 处理 → RealTimeStrategyManager
```

### 核心特性

1. **可配置切换**：通过配置文件一键启用/禁用 Kafka
2. **最小化改造**：原有代码逻辑保持不变，只在 WebSocket 处理层添加分支
3. **偏移量管理**：使用手动提交模式，确保任务重启后继续消费
4. **数据不丢失**：Kafka 持久化存储，重启后自动恢复

## 配置说明

### 1. 添加 Maven 依赖

在 `pom.xml` 中添加 Kafka 依赖：

```xml
<!-- Kafka -->
<dependency>
    <groupId>org.springframework.kafka</groupId>
    <artifactId>spring-kafka</artifactId>
</dependency>
```

### 2. 配置文件

在 `application.properties` 中添加以下配置：

```properties
# Kafka Configuration for K-line Data Buffer
# 是否启用 Kafka 缓冲（false=直接处理，true=通过Kafka缓冲）
kline.kafka.enabled=false

# Kafka 服务器地址
spring.kafka.bootstrap-servers=localhost:9092

# Kafka 生产者配置
spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer
spring.kafka.producer.value-serializer=org.apache.kafka.common.serialization.StringSerializer
spring.kafka.producer.acks=1
spring.kafka.producer.retries=3

# Kafka 消费者配置
spring.kafka.consumer.group-id=okx-trading-kline-consumer
spring.kafka.consumer.key-deserializer=org.apache.kafka.common.serialization.StringDeserializer
spring.kafka.consumer.value-deserializer=org.apache.kafka.common.serialization.StringDeserializer
spring.kafka.consumer.auto-offset-reset=earliest
spring.kafka.consumer.enable-auto-commit=false

# K线数据 Topic 名称
kline.kafka.topic=okx-kline-data
```

### 3. 环境变量（可选）

也可以通过环境变量配置：

```bash
export KLINE_KAFKA_ENABLED=true
export KAFKA_BOOTSTRAP_SERVERS=localhost:9092
```

## 使用方式

### 启用 Kafka 模式

1. 确保 Kafka 服务已启动
2. 修改配置文件：`kline.kafka.enabled=true`
3. 重启应用

### 禁用 Kafka 模式（默认）

1. 修改配置文件：`kline.kafka.enabled=false`
2. 重启应用

## 核心组件

### 1. KlineKafkaProducerService

**接口**：定义 Kafka 生产者服务
- `sendKlineData()`: 发送 K线数据到 Kafka
- `isEnabled()`: 检查是否启用

**实现**：
- `KlineKafkaProducerServiceImpl`: Kafka 启用时的实现
- `KlineKafkaProducerNoOpServiceImpl`: Kafka 禁用时的空实现

### 2. KlineKafkaConsumerService

**功能**：从 Kafka 消费 K线数据并处理
- 使用 `@KafkaListener` 监听 Topic
- 手动提交偏移量（`AckMode.MANUAL`）
- 解析数据并通知 `RealTimeStrategyManager`

### 3. KafkaConfig

**功能**：Kafka 配置类
- 生产者工厂配置
- 消费者工厂配置
- 监听器容器工厂配置
- 只在 `kline.kafka.enabled=true` 时加载

### 4. OkxApiWebSocketServiceImpl

**修改点**：`handleKlineMessage()` 方法
- 检查 `klineKafkaProducerService.isEnabled()`
- 启用：发送到 Kafka
- 禁用：直接处理（原有逻辑）

## 数据格式

### Kafka 消息格式

```json
{
  "symbol": "BTC-USDT",
  "interval": "1m",
  "timestamp": 1712345678900,
  "data": {
    "ts": 1712345678000,
    "o": "50000.00",
    "h": "50100.00",
    "l": "49900.00",
    "c": "50050.00",
    "vol": "123.45"
  }
}
```

### Kafka Topic 分区策略

- **Key**: `{symbol}_{interval}` (例如: `BTC-USDT_1m`)
- **分区**: 根据 Key 的 hash 值自动分配
- **好处**: 同一交易对的数据在同一分区，保证顺序

## 偏移量管理

### 手动提交模式

```java
@KafkaListener(...)
public void consumeKlineData(..., Acknowledgment acknowledgment) {
    try {
        // 处理数据
        processData();
        
        // 手动提交偏移量
        acknowledgment.acknowledge();
    } catch (Exception e) {
        // 不提交偏移量，下次重启会重新消费
        log.error("处理失败，不提交偏移量");
    }
}
```

### 重启恢复机制

1. **首次启动**: `auto-offset-reset=earliest`，从最早的消息开始消费
2. **正常重启**: 从上次提交的偏移量继续消费
3. **异常重启**: 未提交的消息会重新消费（至少一次语义）

## 性能优化

### 生产者优化

```properties
# 批量发送延迟（毫秒）
spring.kafka.producer.linger-ms=10

# 批量大小（字节）
spring.kafka.producer.batch-size=16384
```

### 消费者优化

```properties
# 每次拉取最多记录数
spring.kafka.consumer.max-poll-records=100

# 并发消费者数量（在 KafkaConfig 中配置）
concurrency=3
```

## 监控和日志

### 日志级别

```properties
logging.level.org.apache.kafka=INFO
logging.level.org.springframework.kafka=INFO
logging.level.com.okx.trading.service.KlineKafkaProducerService=DEBUG
logging.level.com.okx.trading.service.KlineKafkaConsumerService=DEBUG
```

### 关键日志

- `✅ K线数据已发送到 Kafka`: 生产成功
- `📥 接收到 Kafka 消息`: 消费开始
- `✅ 从 Kafka 处理 K线数据`: 处理成功
- `✅ 偏移量已提交`: 偏移量提交成功
- `❌ 处理 Kafka K线数据失败`: 处理失败（不提交偏移量）

## 故障处理

### 1. Kafka 服务不可用

**现象**: 应用启动失败或生产者发送失败

**解决方案**:
- 检查 Kafka 服务是否启动
- 检查 `bootstrap-servers` 配置是否正确
- 临时禁用 Kafka: `kline.kafka.enabled=false`

### 2. 消费者处理失败

**现象**: 日志显示处理失败，偏移量未提交

**解决方案**:
- 检查错误日志，修复数据处理逻辑
- 重启应用，自动从上次成功的偏移量继续消费

### 3. 消息积压

**现象**: Kafka 中消息堆积，消费速度慢

**解决方案**:
- 增加消费者并发数: `concurrency=5`
- 增加分区数（需要重建 Topic）
- 优化数据处理逻辑

## 测试建议

### 1. 功能测试

```bash
# 1. 启动 Kafka
docker run -d --name kafka -p 9092:9092 apache/kafka:latest

# 2. 创建 Topic
kafka-topics.sh --create --topic okx-kline-data --bootstrap-server localhost:9092 --partitions 3 --replication-factor 1

# 3. 启用 Kafka 模式
kline.kafka.enabled=true

# 4. 启动应用，观察日志
```

### 2. 重启测试

```bash
# 1. 启动应用，订阅 K线数据
# 2. 观察 Kafka 中的消息
kafka-console-consumer.sh --topic okx-kline-data --bootstrap-server localhost:9092

# 3. 停止应用
# 4. 重启应用
# 5. 确认从上次偏移量继续消费
```

### 3. 性能测试

```bash
# 监控 Kafka 消费者组状态
kafka-consumer-groups.sh --bootstrap-server localhost:9092 --group okx-trading-kline-consumer --describe
```

## 最佳实践

1. **生产环境建议启用 Kafka**：确保数据不丢失
2. **开发环境可禁用 Kafka**：简化部署，快速调试
3. **定期监控消费者 Lag**：避免消息积压
4. **合理设置分区数**：根据交易对数量和消费能力调整
5. **配置 Kafka 数据保留策略**：避免磁盘占满

## 迁移步骤

### 从直接处理迁移到 Kafka 模式

1. 添加 Maven 依赖
2. 添加配置文件
3. 启动 Kafka 服务
4. 创建 Topic
5. 修改配置 `kline.kafka.enabled=true`
6. 重启应用
7. 验证数据流向

### 从 Kafka 模式回退到直接处理

1. 修改配置 `kline.kafka.enabled=false`
2. 重启应用
3. （可选）停止 Kafka 服务

## 常见问题

### Q1: 为什么选择手动提交偏移量？

A: 手动提交可以确保只有成功处理的消息才提交偏移量，避免数据丢失。

### Q2: 重启后会重复消费吗？

A: 可能会重复消费少量未提交的消息（至少一次语义），业务逻辑需要支持幂等性。

### Q3: Kafka 模式会影响性能吗？

A: 会有轻微延迟（通常 < 100ms），但换来了数据可靠性和重启恢复能力。

### Q4: 可以动态切换模式吗？

A: 需要重启应用才能生效，不支持运行时动态切换。

## 总结

通过引入 Kafka 作为可选的数据缓冲层，我们实现了：

✅ 数据不丢失：Kafka 持久化存储  
✅ 重启恢复：偏移量管理，自动继续消费  
✅ 最小改造：原有代码逻辑保持不变  
✅ 灵活配置：一键启用/禁用  
✅ 生产就绪：手动提交、批量处理、并发消费

---

文档版本：1.0  
创建时间：2026-04-05  
作者：Kiro AI Assistant
