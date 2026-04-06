# Kafka K线数据格式不一致问题

## 问题描述

okx-trading 和 data-warehouse 写入 Kafka 的 K线数据格式不一致，导致 okx-trading 的 Kafka Consumer 无法正确解析数据。

## 数据格式对比

### 1. okx-trading 写入的格式（KlineKafkaProducerServiceImpl）

```json
{
  "symbol": "BTC-USDT",
  "interval": "4H",
  "data": {
    "arg": {
      "channel": "candle4H",
      "instId": "BTC-USDT"
    },
    "data": [
      ["1712188800000", "67000.0", "68000.0", "66500.0", "67500.0", "1234.56", "83456789.12", "83456789.12", "1"]
    ]
  },
  "timestamp": 1712188800000
}
```

**特点**:
- 包装了 symbol 和 interval 字段
- OKX 原始数据在 `data` 字段中
- 添加了 timestamp 字段

### 2. data-warehouse 写入的格式（OKXKlineWebSocketSourceFunction）

```json
{
  "arg": {
    "channel": "candle4H",
    "instId": "BTC-USDT"
  },
  "data": [
    ["1712188800000", "67000.0", "68000.0", "66500.0", "67500.0", "1234.56", "83456789.12", "83456789.12", "1"]
  ]
}
```

**特点**:
- 直接转发 OKX WebSocket 原始消息
- 没有额外包装
- symbol 在 `arg.instId` 中
- interval 在 `arg.channel` 中（需要解析）

### 3. okx-trading Consumer 期望的格式（KafkaKlineConsumerServiceImpl）

```java
// 解析消息
JSONObject message = JSON.parseObject(record.value());

// 期望格式：OKX 原始格式（与 data-warehouse 一致）
JSONObject arg = message.getJSONObject("arg");
String channel = arg.getString("channel");  // "candle4H"
String instId = arg.getString("instId");    // "BTC-USDT"

JSONArray dataArray = message.getJSONArray("data");
```

**结论**: okx-trading Consumer 期望的是 data-warehouse 的格式（OKX 原始格式）

## 问题根源

okx-trading 的 `KlineKafkaProducerServiceImpl` 对 OKX 原始数据进行了包装，添加了额外的字段，导致：

1. 当 `kline.kafka.enabled=true` 时，okx-trading 写入的是包装格式
2. 当 `kline.kafka.consumer.enabled=true` 时，okx-trading 期望读取的是原始格式
3. 如果两者同时启用，会导致数据格式不匹配

## 解决方案

### 方案 A: 修改 okx-trading Producer，使用原始格式（推荐）

修改 `KlineKafkaProducerServiceImpl.sendKlineData()` 方法，直接发送 OKX 原始数据：

```java
@Override
public void sendKlineData(String symbol, String interval, JSONObject klineData) {
    if (!enabled) {
        log.debug("Kafka 未启用，跳过发送");
        return;
    }

    try {
        // 构建消息键：symbol_interval，用于分区
        String key = symbol + "_" + interval;
        
        // ✅ 直接发送 OKX 原始数据，不进行包装
        // klineData 已经是 OKX WebSocket 的原始格式：
        // {
        //   "arg": {"channel": "candle4H", "instId": "BTC-USDT"},
        //   "data": [["1712188800000", ...]]
        // }

        // 异步发送到 Kafka
        CompletableFuture<SendResult<String, String>> future = 
            kafkaTemplate.send(topic, key, klineData.toJSONString());

        future.whenComplete((result, ex) -> {
            if (ex == null) {
                if (kafkaMonitorService != null) {
                    kafkaMonitorService.updateLastDataReceivedTime();
                }
                
                log.debug("✅ K线数据已发送到 Kafka: topic={}, partition={}, offset={}, key={}", 
                    topic, 
                    result.getRecordMetadata().partition(),
                    result.getRecordMetadata().offset(),
                    key);
            } else {
                log.error("❌ K线数据发送到 Kafka 失败: key={}, error={}", key, ex.getMessage(), ex);
            }
        });

    } catch (Exception e) {
        log.error("发送 K线数据到 Kafka 异常: symbol={}, interval={}", symbol, interval, e);
    }
}
```

**优点**:
- ✅ 与 data-warehouse 格式一致
- ✅ okx-trading Consumer 可以正确解析
- ✅ 数据格式统一，便于维护

**缺点**:
- ⚠️ 失去了额外的 timestamp 字段（但可以从 K线数据中获取）

### 方案 B: 修改 okx-trading Consumer，支持两种格式

修改 `KafkaKlineConsumerServiceImpl` 的 `handleKlineMessage()` 方法，兼容两种格式：

```java
private void handleKlineMessage(ConsumerRecord<String, String> record) {
    try {
        String message = record.value();
        JSONObject jsonMessage = JSON.parseObject(message);
        
        // 检查是否是包装格式（okx-trading Producer）
        if (jsonMessage.containsKey("symbol") && jsonMessage.containsKey("interval")) {
            // 包装格式：提取原始数据
            String symbol = jsonMessage.getString("symbol");
            String interval = jsonMessage.getString("interval");
            JSONObject klineData = jsonMessage.getJSONObject("data");
            
            // 处理原始数据
            processKlineData(symbol, interval, klineData);
            
        } else if (jsonMessage.containsKey("arg") && jsonMessage.containsKey("data")) {
            // 原始格式（data-warehouse）
            JSONObject arg = jsonMessage.getJSONObject("arg");
            String channel = arg.getString("channel");
            String instId = arg.getString("instId");
            
            // 解析 symbol 和 interval
            String symbol = instId;
            String interval = parseIntervalFromChannel(channel);
            
            // 处理原始数据
            processKlineData(symbol, interval, jsonMessage);
            
        } else {
            log.warn("未知的消息格式: {}", message);
        }
        
    } catch (Exception e) {
        log.error("处理 K线消息失败: {}", e.getMessage(), e);
    }
}
```

**优点**:
- ✅ 兼容两种格式
- ✅ 向后兼容

**缺点**:
- ⚠️ 代码复杂度增加
- ⚠️ 维护成本高

### 方案 C: 统一使用 data-warehouse（推荐）

**配置**:
```properties
# okx-trading
kline.kafka.enabled=false
kline.kafka.consumer.enabled=true
```

**数据流**:
```
data-warehouse ──▶ Kafka (原始格式) ──▶ okx-trading Consumer ──▶ Redis
```

**优点**:
- ✅ 数据源统一
- ✅ 格式一致
- ✅ 无需修改代码
- ✅ 已经通过 WebSocket 订阅修复避免了重复

**缺点**:
- ⚠️ 依赖 data-warehouse

## 推荐方案

### 短期方案（立即可用）

使用方案 C：统一使用 data-warehouse

```properties
# okx-trading/src/main/resources/application.properties
kline.kafka.enabled=false
kline.kafka.consumer.enabled=true
```

### 长期方案（代码优化）

实施方案 A：修改 okx-trading Producer 使用原始格式

这样即使 `kline.kafka.enabled=true`，okx-trading 也能正确消费自己写入的数据。

## 验证方法

### 1. 查看 Kafka 中的数据格式

```bash
# 消费 Kafka Topic，查看数据格式
kafka-console-consumer.sh --bootstrap-server localhost:9093 \
  --topic okx-kline-data \
  --from-beginning \
  --max-messages 1
```

### 2. 检查 okx-trading 日志

```bash
# 查看 Consumer 日志
tail -f okx-trading/logs/all/all.log | grep "K线消费统计\|解析K线消息失败"
```

### 3. 检查 Redis 数据

```bash
redis-cli
> GET kline:BTC-USDT:4H
```

## 当前状态

- ✅ data-warehouse 写入格式：OKX 原始格式
- ⚠️ okx-trading Producer 写入格式：包装格式（不一致）
- ✅ okx-trading Consumer 期望格式：OKX 原始格式
- ✅ 推荐配置：`kline.kafka.enabled=false, kline.kafka.consumer.enabled=true`

## 相关文件

- `okx-trading/src/main/java/com/okx/trading/service/impl/KlineKafkaProducerServiceImpl.java` - Producer（需要修改）
- `okx-trading/src/main/java/com/okx/trading/service/impl/KafkaKlineConsumerServiceImpl.java` - Consumer（已正确）
- `data-warehouse/src/main/java/com/crypto/dw/flink/source/OKXKlineWebSocketSourceFunction.java` - data-warehouse Source（已正确）
