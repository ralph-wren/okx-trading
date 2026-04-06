# WebSocket 订阅重复问题修复

## 问题描述

在使用 data-warehouse 统一采集 K线数据的架构中，okx-trading 的实时策略启动时会自动订阅 WebSocket，导致数据重复处理：

```
data-warehouse ──▶ Kafka ──▶ okx-trading ──▶ Redis (路径1)
                                  │
OKX WebSocket ────────────────────┘──▶ Redis (路径2，重复！)
```

## 修复方案

在 `RealTimeStrategyManager.startExecuteRealTimeStrategy()` 方法中，检查 `kline.kafka.consumer.enabled` 配置：

- 如果 `kline.kafka.consumer.enabled=true`，则**不订阅** WebSocket（数据由 data-warehouse 提供）
- 如果 `kline.kafka.consumer.enabled=false`，则订阅 WebSocket（自己获取数据）

## 修复内容

### 1. 注入 Environment

在 `RealTimeStrategyManager` 类中添加 `Environment` 字段：

```java
private final Environment environment;

public RealTimeStrategyManager(...,
                               Environment environment) {
    // ...
    this.environment = environment;
}
```

### 2. 添加条件判断

在 `startExecuteRealTimeStrategy()` 方法中添加配置检查：

```java
// 订阅K线数据
// 如果启用了 Kafka Consumer，则不需要订阅 WebSocket（数据由 data-warehouse 提供）
// 如果未启用 Kafka Consumer，则需要订阅 WebSocket（自己获取数据）
boolean kafkaConsumerEnabled = environment.getProperty("kline.kafka.consumer.enabled", Boolean.class, false);

if (!kafkaConsumerEnabled) {
    // 未启用 Kafka Consumer，需要订阅 WebSocket
    try {
        webSocketService.subscribeKlineData(strategyEntity.getSymbol(), strategyEntity.getInterval());
        log.info("✓ 已订阅 WebSocket K线数据: symbol={}, interval={}", 
                strategyEntity.getSymbol(), strategyEntity.getInterval());
    } catch (Exception e) {
        log.error("订阅K线数据失败: {}", e.getMessage(), e);
        response.put("message", "订阅K线数据失败");
        response.put("status", CANCELED);
        return response;
    }
} else {
    // 已启用 Kafka Consumer，不订阅 WebSocket
    log.info("✓ Kafka Consumer 已启用，跳过 WebSocket 订阅: symbol={}, interval={}", 
            strategyEntity.getSymbol(), strategyEntity.getInterval());
}
```

### 3. 添加 Import

```java
import org.springframework.core.env.Environment;
```

## 修复后的数据流

### 配置 1: 统一采集模式（推荐）

```properties
kline.kafka.enabled=false
kline.kafka.consumer.enabled=true
```

**数据流**:
```
data-warehouse ──▶ Kafka ──▶ okx-trading ──▶ Redis
```

**特点**:
- ✅ 数据源统一
- ✅ 无重复处理
- ✅ WebSocket 不会被订阅

### 配置 2: 独立模式

```properties
kline.kafka.enabled=false
kline.kafka.consumer.enabled=false
```

**数据流**:
```
OKX WebSocket ──▶ okx-trading ──▶ Redis
```

**特点**:
- ✅ 简单快速
- ✅ 适合单应用部署

## 验证方法

### 1. 查看日志

启动 okx-trading，查看实时策略启动日志：

```bash
tail -f logs/all/all.log | grep "WebSocket\|Kafka Consumer"
```

**正确的日志（kline.kafka.consumer.enabled=true）**:
```
✓ Kafka Consumer 已启用，跳过 WebSocket 订阅: symbol=BTC-USDT, interval=1D
```

**正确的日志（kline.kafka.consumer.enabled=false）**:
```
✓ 已订阅 WebSocket K线数据: symbol=BTC-USDT, interval=1D
```

### 2. 验证数据流

```bash
# 1. 确认 data-warehouse 正在写入 Kafka
tail -f data-warehouse/logs/*.log | grep "发送K线数据到Kafka"

# 2. 确认 okx-trading 正在消费 Kafka
tail -f okx-trading/logs/all/all.log | grep "K线消费统计"

# 3. 确认 Redis 被正确更新
redis-cli
> KEYS kline:*
> GET kline:BTC-USDT:1D
```

### 3. 检查是否有重复

如果看到以下两种日志同时出现，说明有重复（旧版本）：

```
# WebSocket 路径
✓ 已订阅 WebSocket K线数据: symbol=BTC-USDT, interval=1D
✓ 更新K线数据: symbol=BTC-USDT, interval=1D, ...

# Kafka 路径
K线消费统计: 总数=100, 成功=100, ...
✓ 更新K线数据: symbol=BTC-USDT, interval=1D, ...
```

修复后，只会看到一种日志。

## 配置决策表

| 场景 | kline.kafka.enabled | kline.kafka.consumer.enabled | WebSocket 订阅 | 说明 |
|------|---------------------|------------------------------|---------------|------|
| 单应用，不需要数据仓库 | `false` | `false` | ✅ 是 | 最简单 |
| 使用 data-warehouse（推荐） | `false` | `true` | ❌ 否 | 无重复 |
| 自己采集，分享给其他系统 | `true` | `false` | ✅ 是 | 可以 |
| ❌ 不推荐 | `true` | `true` | ❌ 否 | 自己采集+消费 |

## 相关文件

- `okx-trading/src/main/java/com/okx/trading/strategy/RealTimeStrategyManager.java` - 修复代码
- `okx-trading/KAFKA_KLINE_FLOW.md` - 详细的数据流说明
- `okx-trading/KAFKA_KLINE_CONSUMER_CONFIG.md` - Kafka 配置说明
- `okx-trading/KAFKA_KLINE_ARCHITECTURE.md` - 架构说明

## 总结

通过在实时策略启动时检查 `kline.kafka.consumer.enabled` 配置，成功避免了 WebSocket 订阅重复的问题。现在：

- ✅ 当使用 data-warehouse 统一采集时，okx-trading 不会订阅 WebSocket
- ✅ 当独立运行时，okx-trading 会订阅 WebSocket
- ✅ 数据流清晰，无重复处理
- ✅ 配置灵活，支持多种部署模式
