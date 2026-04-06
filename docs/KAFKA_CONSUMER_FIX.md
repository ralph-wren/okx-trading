# Kafka Consumer 不触发策略信号问题修复

## 问题描述

用户报告 `RealTimeStrategyManager.processStrategySignal` 方法没有被触发,怀疑 Kafka Consumer 没有消费数据。

## 问题分析

经过排查发现了以下问题:

### 1. 重复的 Kafka Consumer 类

项目中存在两个功能重复的 Kafka Consumer 类:

- **旧类**: `KlineKafkaConsumerService`
  - 使用 `@KafkaListener` 注解自动消费
  - 带条件启动: `@ConditionalOnProperty(name = "kline.kafka.enabled", havingValue = "true")`
  - 已经实现了调用 `realTimeStrategyManager.handleNewKlineData()`

- **新类**: `KafkaKlineConsumerServiceImpl` (已删除)
  - 使用手动 poll 方式消费
  - 始终启动,无条件限制
  - 只更新 Redis,没有调用 `realTimeStrategyManager.handleNewKlineData()`

### 2. 根本原因

`KafkaKlineConsumerServiceImpl` 在消费到 Kafka 数据后,只调用了:
```java
redisCacheService.updateCandlestick(candlestick);
```

而 `RedisCacheService.updateCandlestick()` 方法只是把数据写入 Redis,**并没有通知 `RealTimeStrategyManager` 去处理新的 K线数据**。

因此,即使 Kafka Consumer 正常消费数据并更新了 Redis,策略管理器也不会被触发。

### 3. 参数解析错误

运行时出现错误:
```
Cannot convert from [java.lang.String] to [org.springframework.kafka.support.Acknowledgment]
```

原因是 `@KafkaListener` 方法的 `Acknowledgment` 参数没有正确配置。Spring Kafka 需要特殊处理 `Acknowledgment` 参数,但由于配置了自动提交(`enable.auto.commit=true`),不需要手动确认。

## 解决方案

### 1. 删除重复类

删除了以下文件:
- `okx-trading/src/main/java/com/okx/trading/service/impl/KafkaKlineConsumerServiceImpl.java`
- `okx-trading/src/main/java/com/okx/trading/service/KafkaKlineConsumerService.java` (接口)

### 2. 修改旧类配置

修改 `KlineKafkaConsumerService`:
- 移除 `@ConditionalOnProperty` 注解,让 Kafka Consumer 始终启动
- 移除 `Acknowledgment` 参数,使用自动提交
- 移除 `containerFactory` 配置,使用默认配置
- 修改数据解析逻辑,直接解析 OKX 原始格式 (不是包装格式)
- 保留调用 `realTimeStrategyManager.handleNewKlineData()` 的逻辑

修改前:
```java
@Service
@ConditionalOnProperty(name = "kline.kafka.enabled", havingValue = "true")
public class KlineKafkaConsumerService {
    @KafkaListener(
        topics = "${kline.kafka.topic}",
        groupId = "${spring.kafka.consumer.group-id}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeKlineData(
            @Payload String message,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        // ...
        acknowledgment.acknowledge();
    }
}
```

修改后:
```java
@Service
public class KlineKafkaConsumerService {
    @KafkaListener(
        topics = "${kline.kafka.topic}",
        groupId = "${spring.kafka.consumer.group-id}"
    )
    public void consumeKlineData(
            @Payload String message,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset) {
        // 解析 OKX 原始格式
        // 自动提交偏移量
    }
}
```

### 3. 修复数据格式解析

修改数据解析逻辑,直接解析 OKX 原始格式:

```java
// 解析 arg
JSONObject arg = messageObj.getJSONObject("arg");
String channel = arg.getString("channel");
String symbol = arg.getString("instId");
String interval = channel.replace("candle", ""); // candle1D -> 1D

// 解析 data 数组
JSONArray dataArray = messageObj.getJSONArray("data");
JSONArray klineData = dataArray.getJSONArray(0);

// 解析 K线数据
Candlestick candlestick = parseOKXKlineData(symbol, interval, klineData);
```

### 4. 使用 Builder 模式

使用 Builder 模式替代 setter 方法,避免 Lombok 编译问题:

```java
Candlestick.CandlestickBuilder builder = Candlestick.builder()
        .symbol(symbol)
        .intervalVal(interval)
        .openTime(openTime)
        .open(open)
        .high(high)
        .low(low)
        .close(close)
        .volume(volume)
        .quoteVolume(quoteVolume)
        .state(state);

return builder.build();
```

## 配置说明

修改后的配置逻辑:

- `kline.kafka.enabled=false` (推荐)
  - Kafka Consumer: ✅ 启动,从 Kafka 消费数据
  - WebSocket 订阅: ❌ 不订阅
  - Kafka Producer: ❌ 不写入
  - 数据来源: data-warehouse 统一提供

- `kline.kafka.enabled=true`
  - Kafka Consumer: ✅ 启动,从 Kafka 消费数据
  - WebSocket 订阅: ✅ 订阅 OKX WebSocket
  - Kafka Producer: ✅ 写入 Kafka
  - 数据来源: okx-trading 自己采集

## 数据流

### 模式 1: data-warehouse 统一采集 (推荐)
```
OKX WebSocket → data-warehouse (Flink) → Kafka (OKX原始格式) → okx-trading (Consumer) → RealTimeStrategyManager
```

### 模式 2: okx-trading 自己采集
```
OKX WebSocket → okx-trading (Producer) → Kafka (OKX原始格式) → okx-trading (Consumer) → RealTimeStrategyManager
```

## OKX K线数据格式

Kafka 中的数据格式 (OKX 原始格式):
```json
{
  "arg": {
    "channel": "candle1D",
    "instId": "BTC-USDT"
  },
  "data": [[
    "1775404800000",  // 开盘时间戳
    "67304.5",        // 开盘价
    "70273.4",        // 最高价
    "67157.0",        // 最低价
    "69613.8",        // 收盘价
    "6497.72918844",  // 成交量（币）
    "449268149.417438678",  // 成交量（USDT）
    "449268149.417438678",  // 成交量（USDT）- 重复
    "0"               // 确认状态：0=未确认，1=已确认
  ]]
}
```

## 验证步骤

1. 编译项目:
```bash
cd okx-trading
mvn clean compile -DskipTests
```

2. 启动应用 (Rebel 自动编译生效)

3. 检查日志,确认 Kafka Consumer 启动:
```
📥 接收到 Kafka 消息: partition=0, offset=6294
✅ 从 Kafka 处理 K线数据: symbol=BTC-USDT, interval=1D, close=69613.8
```

4. 检查策略信号是否触发:
```
处理策略信号: symbol=BTC-USDT, interval=1D
```

## 总结

问题的根本原因有两个:
1. 新创建的 `KafkaKlineConsumerServiceImpl` 只更新了 Redis,没有通知 `RealTimeStrategyManager`
2. `@KafkaListener` 方法的 `Acknowledgment` 参数配置不正确

通过删除重复类,保留使用 `@KafkaListener` 的旧类,移除条件启动限制和手动确认参数,修复数据格式解析,确保 Kafka Consumer 始终启动并正确触发策略信号处理。

## 相关文件

- `okx-trading/src/main/java/com/okx/trading/service/KlineKafkaConsumerService.java`
- `okx-trading/src/main/java/com/okx/trading/strategy/RealTimeStrategyManager.java`
- `okx-trading/src/main/resources/application.properties`


## 代码重构

### 接口和实现类分离

为了更好的代码结构和可测试性,将 `KlineKafkaConsumerService` 重构为接口和实现类:

**接口**: `okx-trading/src/main/java/com/okx/trading/service/KlineKafkaConsumerService.java`
```java
public interface KlineKafkaConsumerService {
    void consumeKlineData(String message, int partition, long offset);
}
```

**实现类**: `okx-trading/src/main/java/com/okx/trading/service/impl/KlineKafkaConsumerServiceImpl.java`
```java
@Slf4j
@Service
public class KlineKafkaConsumerServiceImpl implements KlineKafkaConsumerService {
    
    @Override
    @KafkaListener(
        topics = "${kline.kafka.topic}",
        groupId = "${spring.kafka.consumer.group-id}"
    )
    public void consumeKlineData(
            @Payload String message,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset) {
        // 实现逻辑
    }
}
```

**优点:**
- 符合面向接口编程原则
- 便于单元测试和Mock
- 更好的代码组织结构
- 与项目中其他Service保持一致的风格
