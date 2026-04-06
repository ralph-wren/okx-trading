# Kafka 消费者使用指南

## 概述

okx-trading 项目支持从 data-warehouse 的 Kafka Topics 消费行情数据，实现数据共享和解耦。

## 功能特性

- ✅ 自动数据格式转换（TickerData → Ticker）
- ✅ 支持现货和合约数据
- ✅ 自动更新 Redis 缓存
- ✅ 通知实时策略管理器
- ✅ 统计信息监控
- ✅ 自动重连和容错

## 配置说明

### 1. 启用 Kafka 消费者

在 `application.properties` 中配置：

```properties
# 启用 Kafka 消费者
kafka.consumer.enabled=true

# Kafka 服务器地址
spring.kafka.bootstrap-servers=localhost:9093

# 消费者组 ID
spring.kafka.consumer.group-id=okx-trading-ticker-consumer

# 订阅的 Topics（逗号分隔）
kafka.consumer.topics=crypto-ticker-spot,crypto-ticker-swap

# 消费起始位置（latest=最新，earliest=最早）
spring.kafka.consumer.auto-offset-reset=latest
```

### 2. 环境变量配置

```bash
# .env 文件
KAFKA_CONSUMER_ENABLED=true
KAFKA_BOOTSTRAP_SERVERS=localhost:9093
KAFKA_CONSUMER_TOPICS=crypto-ticker-spot,crypto-ticker-swap
```

## 数据格式转换

### data-warehouse 格式（Kafka 消息）

```json
{
  "instId": "BTC-USDT",
  "last": "50000.5",
  "askPx": "50001.0",
  "askSz": "1.5",
  "bidPx": "49999.5",
  "bidSz": "2.0",
  "open24h": "49500.0",
  "high24h": "50500.0",
  "low24h": "49000.0",
  "volCcy24h": "1000000.0",
  "vol24h": "20.5",
  "ts": "1711234567890"
}
```

### okx-trading 格式（自动转换）

```json
{
  "symbol": "BTC-USDT",
  "channel": "tickers",
  "lastPrice": 50000.5,
  "priceChange": 500.5,
  "priceChangePercent": 1.01,
  "highPrice": 50500.0,
  "lowPrice": 49000.0,
  "volume": 20.5,
  "quoteVolume": 1000000.0,
  "bidPrice": 49999.5,
  "bidQty": 2.0,
  "askPrice": 50001.0,
  "askQty": 1.5,
  "timestamp": "2024-03-24 12:34:56"
}
```

### 转换逻辑

`TickerDataConverter` 类负责数据转换：

```java
// 从 Kafka 消息转换
Ticker ticker = TickerDataConverter.fromKafkaMessage(kafkaMessage);

// 验证数据有效性
if (TickerDataConverter.isValid(ticker)) {
    // 处理数据
}

// 判断现货/合约
boolean isSpot = TickerDataConverter.isSpot(ticker.getSymbol());
boolean isSwap = TickerDataConverter.isSwap(ticker.getSymbol());

// 获取基础交易对
String baseSymbol = TickerDataConverter.getBaseSymbol("BTC-USDT-SWAP");
// 返回: "BTC-USDT"
```

## 使用场景

### 场景 1：只使用 Kafka 数据（推荐）

```properties
# 禁用 WebSocket 直接采集
kline.kafka.enabled=false

# 启用 Kafka 消费者
kafka.consumer.enabled=true
```

**优势**：
- 数据由 data-warehouse 统一采集
- okx-trading 专注交易逻辑
- 避免重复连接 WebSocket

### 场景 2：同时使用 WebSocket 和 Kafka

```properties
# 禁用 WebSocket 写入 Kafka
kline.kafka.enabled=false

# 启用 Kafka 消费者（消费 data-warehouse 的数据）
kafka.consumer.enabled=true
```

**优势**：
- WebSocket 用于实时交易
- Kafka 用于历史数据回放
- 数据来源灵活

### 场景 3：完全独立运行

```properties
# 禁用 Kafka 功能
kline.kafka.enabled=false
kafka.consumer.enabled=false
```

**优势**：
- 完全独立，不依赖 data-warehouse
- 适合简单场景

## 启动和监控

### 1. 启动服务

```bash
# 确保 Kafka 已启动
cd data-warehouse
./manage-kafka.sh start

# 确保 data-warehouse 数据采集器已启动
./run-collector.sh

# 启动 okx-trading
cd okx-trading
mvn spring-boot:run
```

### 2. 查看日志

```bash
# 查看 Kafka 消费日志
tail -f logs/okx-trading.log | grep "Kafka"

# 输出示例
2024-03-24 12:34:56 INFO  Kafka Ticker Consumer started successfully
2024-03-24 12:34:57 INFO  Subscribed to topics: [crypto-ticker-spot, crypto-ticker-swap]
2024-03-24 12:35:00 INFO  Consumed 100 messages (SPOT: 50, SWAP: 50, Success: 100, Failed: 0)
```

### 3. 查询统计信息

通过 REST API 查询消费统计：

```bash
# 查询统计信息（需要添加对应的 Controller）
curl "http://localhost:8088/api/kafka/statistics"

# 响应
{
  "totalMessages": 1000,
  "successMessages": 998,
  "failedMessages": 2,
  "spotMessages": 500,
  "swapMessages": 500
}
```

## 数据流向

```
OKX WebSocket
    ↓
data-warehouse (采集)
    ↓
Kafka Topics
    ├─ crypto-ticker-spot
    └─ crypto-ticker-swap
    ↓
okx-trading (消费)
    ↓
    ├─ Redis 缓存更新
    ├─ 实时策略触发
    └─ 邮件通知
```

## 性能优化

### 1. 消费者配置

```properties
# 批量拉取消息数量
spring.kafka.consumer.max-poll-records=100

# 拉取超时时间
spring.kafka.consumer.fetch-max-wait=500

# 最小拉取字节数
spring.kafka.consumer.fetch-min-size=1024
```

### 2. 并发处理

```java
// 如需提高吞吐量，可以增加消费线程数
@Value("${kafka.consumer.threads:1}")
private int consumerThreads;

// 创建多个消费者线程
for (int i = 0; i < consumerThreads; i++) {
    executorService.submit(this::consumeMessages);
}
```

### 3. 批量处理

```java
// 批量更新 Redis
List<Ticker> batch = new ArrayList<>();
for (ConsumerRecord<String, String> record : records) {
    Ticker ticker = TickerDataConverter.fromKafkaMessage(record.value());
    batch.add(ticker);
}

// 批量更新
redisCacheService.batchUpdatePrices(batch);
```

## 故障排查

### 问题 1：消费者无法启动

**症状**：`kafka.consumer.enabled=true` 但服务未启动

**解决方案**：
```bash
# 1. 检查 Kafka 是否启动
docker ps | grep kafka

# 2. 检查配置
grep "kafka.consumer.enabled" application.properties

# 3. 查看日志
tail -f logs/okx-trading.log | grep "Kafka Consumer"
```

### 问题 2：消费延迟

**症状**：数据延迟较大

**解决方案**：
```bash
# 1. 查看消费者 Lag
docker exec -it kafka-okx-trading kafka-consumer-groups.sh \
  --bootstrap-server localhost:9092 \
  --describe \
  --group okx-trading-ticker-consumer

# 2. 增加消费者线程数
kafka.consumer.threads=4

# 3. 增加批量拉取数量
spring.kafka.consumer.max-poll-records=500
```

### 问题 3：数据格式错误

**症状**：`Failed to convert Kafka message to Ticker`

**解决方案**：
```bash
# 1. 查看 Kafka 原始数据
docker exec -it kafka-okx-trading kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 \
  --topic crypto-ticker-spot \
  --max-messages 1

# 2. 验证 JSON 格式
echo '{"instId":"BTC-USDT","last":"50000"}' | jq .

# 3. 检查转换器日志
tail -f logs/okx-trading.log | grep "TickerDataConverter"
```

## 监控指标

### 1. 消费速率

```java
// 每分钟消费的消息数
long messagesPerMinute = totalMessages.get() / (System.currentTimeMillis() - startTime) * 60000;
```

### 2. 成功率

```java
// 消费成功率
double successRate = (double) successMessages.get() / totalMessages.get() * 100;
```

### 3. 延迟监控

```java
// 消息延迟（消息时间戳 vs 处理时间）
long latency = System.currentTimeMillis() - messageTimestamp;
```

## 最佳实践

### 1. 配置管理

- ✅ 使用环境变量管理配置
- ✅ 区分开发/生产环境配置
- ✅ 定期备份配置文件
- ❌ 不要在代码中硬编码配置

### 2. 错误处理

- ✅ 记录详细的错误日志
- ✅ 实现重试机制
- ✅ 设置告警阈值
- ❌ 不要忽略异常

### 3. 性能优化

- ✅ 批量处理消息
- ✅ 异步更新缓存
- ✅ 使用连接池
- ❌ 避免阻塞操作

### 4. 监控告警

- ✅ 监控消费 Lag
- ✅ 监控错误率
- ✅ 监控延迟
- ✅ 设置告警规则

## 集成示例

### Spring Boot 自动配置

```java
@Configuration
@ConditionalOnProperty(name = "kafka.consumer.enabled", havingValue = "true")
public class KafkaConsumerConfig {
    
    @Bean
    public KafkaTickerConsumerService kafkaTickerConsumerService() {
        return new KafkaTickerConsumerServiceImpl();
    }
    
    @Bean
    public CommandLineRunner startKafkaConsumer(KafkaTickerConsumerService service) {
        return args -> {
            service.start();
            log.info("Kafka Ticker Consumer started");
        };
    }
}
```

### 自定义消息处理

```java
@Service
public class CustomTickerProcessor {
    
    @Autowired
    private KafkaTickerConsumerService consumerService;
    
    @PostConstruct
    public void init() {
        // 启动消费者
        consumerService.start();
        
        // 定期打印统计
        Executors.newScheduledThreadPool(1).scheduleAtFixedRate(() -> {
            ConsumerStatistics stats = consumerService.getStatistics();
            log.info("Kafka Consumer Stats: {}", stats);
        }, 60, 60, TimeUnit.SECONDS);
    }
}
```

## 总结

Kafka 消费者功能让 okx-trading 可以灵活地从 data-warehouse 获取数据，实现数据共享和系统解耦。

**核心优势**：
- 🔄 自动数据格式转换
- 📊 实时统计监控
- 🛡️ 容错和重试机制
- 🚀 高性能批量处理
- 🔌 易于集成和扩展

**推荐配置**：
- data-warehouse: 统一数据采集入口
- okx-trading: 从 Kafka 消费数据
- 避免重复采集，降低维护成本
