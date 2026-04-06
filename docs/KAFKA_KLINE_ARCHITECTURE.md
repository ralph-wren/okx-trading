# Kafka K线数据架构说明

## 配置控制流程

### kline.kafka.enabled 配置的作用

`kline.kafka.enabled` 配置决定 okx-trading 是否将 K线数据写入 Kafka。

#### 配置位置

```properties
# application.properties
kline.kafka.enabled=${KLINE_KAFKA_ENABLED:false}
```

#### 使用位置

**文件**: `KlineKafkaProducerServiceImpl.java`

```java
@Service
@ConditionalOnProperty(name = "kline.kafka.enabled", havingValue = "true")
public class KlineKafkaProducerServiceImpl implements KlineKafkaProducerService {
    
    @Value("${kline.kafka.enabled}")
    private boolean enabled;
    
    @Override
    public void sendKlineData(String symbol, String interval, JSONObject klineData) {
        if (!enabled) {
            log.debug("Kafka 未启用，跳过发送");
            return;
        }
        // ... 发送到 Kafka
    }
}
```

#### 两层控制机制

1. **Bean 创建控制**（编译时）
   ```java
   @ConditionalOnProperty(name = "kline.kafka.enabled", havingValue = "true")
   ```
   - 如果 `kline.kafka.enabled=false`，Spring 不会创建 `KlineKafkaProducerServiceImpl` Bean
   - 如果 `kline.kafka.enabled=true`，Spring 创建这个 Bean

2. **运行时检查**（运行时）
   ```java
   @Value("${kline.kafka.enabled}")
   private boolean enabled;
   
   if (!enabled) {
       return;
   }
   ```
   - 即使 Bean 被创建，运行时仍然检查配置
   - 提供双重保险

---

## 两种架构模式

### 模式 1: okx-trading 自己采集（旧模式）

```
┌─────────────────────────────────────────────────────────────┐
│                        okx-trading                          │
│                                                             │
│  ┌──────────────┐                                          │
│  │ OKX WebSocket│                                          │
│  │   订阅 K线    │                                          │
│  └──────┬───────┘                                          │
│         │                                                   │
│         ▼                                                   │
│  ┌──────────────┐      ┌─────────────────┐               │
│  │ K线数据处理   │─────▶│ Redis 缓存      │               │
│  └──────┬───────┘      └─────────────────┘               │
│         │                                                   │
│         │ (可选)                                            │
│         ▼                                                   │
│  ┌──────────────┐                                          │
│  │ Kafka Producer│                                         │
│  │ (kline.kafka. │                                         │
│  │  enabled=true)│                                         │
│  └──────┬───────┘                                          │
│         │                                                   │
└─────────┼───────────────────────────────────────────────────┘
          │
          ▼
    ┌─────────┐
    │  Kafka  │
    │  Topic  │
    └─────────┘
```

**配置**:
```properties
kline.kafka.enabled=true
kline.kafka.consumer.enabled=false
```

**特点**:
- okx-trading 直接订阅 OKX WebSocket
- 自己处理 K线数据
- 可选地写入 Kafka（用于其他系统消费）

---

### 模式 2: data-warehouse 统一采集（新模式，推荐）

```
┌─────────────────────────────────────────────────────────────┐
│                      data-warehouse                         │
│                                                             │
│  ┌──────────────┐                                          │
│  │ OKX WebSocket│                                          │
│  │   订阅 K线    │                                          │
│  └──────┬───────┘                                          │
│         │                                                   │
│         ▼                                                   │
│  ┌──────────────┐                                          │
│  │ Flink K线采集 │                                          │
│  │   作业        │                                          │
│  └──────┬───────┘                                          │
│         │                                                   │
└─────────┼───────────────────────────────────────────────────┘
          │
          ▼
    ┌─────────┐
    │  Kafka  │
    │  Topic  │
    │ okx-kline│
    │  -data   │
    └────┬────┘
         │
         ▼
┌─────────────────────────────────────────────────────────────┐
│                        okx-trading                          │
│                                                             │
│  ┌──────────────┐                                          │
│  │ Kafka Consumer│                                         │
│  │ (kline.kafka. │                                         │
│  │  consumer.    │                                         │
│  │  enabled=true)│                                         │
│  └──────┬───────┘                                          │
│         │                                                   │
│         ▼                                                   │
│  ┌──────────────┐      ┌─────────────────┐               │
│  │ K线数据解析   │─────▶│ Redis 缓存      │               │
│  └──────────────┘      └─────────────────┘               │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

**配置**:
```properties
kline.kafka.enabled=false
kline.kafka.consumer.enabled=true
```

**特点**:
- data-warehouse 统一采集所有 K线数据
- okx-trading 从 Kafka 消费数据
- 数据源统一，便于管理和监控
- 避免重复订阅 WebSocket

---

## 配置对比

| 配置项 | 模式 1（自己采集） | 模式 2（统一采集，推荐） |
|--------|-------------------|------------------------|
| `kline.kafka.enabled` | `true` | `false` |
| `kline.kafka.consumer.enabled` | `false` | `true` |
| WebSocket 订阅 | okx-trading | data-warehouse |
| 数据写入 Kafka | okx-trading | data-warehouse |
| 数据消费 Kafka | - | okx-trading |
| Redis 更新 | 直接更新 | 从 Kafka 消费后更新 |

---

## 配置决策树

```
开始
  │
  ├─ 是否需要统一数据采集？
  │   │
  │   ├─ 是 ──▶ 使用模式 2（推荐）
  │   │         kline.kafka.enabled=false
  │   │         kline.kafka.consumer.enabled=true
  │   │
  │   └─ 否 ──▶ 使用模式 1
  │             kline.kafka.enabled=true
  │             kline.kafka.consumer.enabled=false
  │
  └─ 是否需要将 K线数据提供给其他系统？
      │
      ├─ 是 ──▶ 必须启用 Kafka
      │         (模式 1 或模式 2 都可以)
      │
      └─ 否 ──▶ 可以不使用 Kafka
                kline.kafka.enabled=false
                kline.kafka.consumer.enabled=false
```

---

## 代码调用链

### 模式 1: 自己采集

```
OkxApiWebSocketServiceImpl.handleKlineMessage()
  │
  ├─▶ klineCacheService.updateKlineData()  // 更新 Redis
  │
  └─▶ klineKafkaProducerService.sendKlineData()  // 写入 Kafka
       │
       └─▶ 检查 kline.kafka.enabled
            │
            ├─ true  ──▶ kafkaTemplate.send()
            │
            └─ false ──▶ 跳过发送
```

### 模式 2: 统一采集

```
data-warehouse: OKXKlineWebSocketSourceFunction
  │
  └─▶ Kafka Topic: okx-kline-data
       │
       └─▶ okx-trading: KafkaKlineConsumerServiceImpl
            │
            ├─▶ 检查 kline.kafka.consumer.enabled
            │    │
            │    ├─ true  ──▶ 启动消费者
            │    │
            │    └─ false ──▶ 不启动消费者
            │
            └─▶ processKlineMessage()
                 │
                 └─▶ redisCacheService.updateKlineData()  // 更新 Redis
```

---

## Bean 创建条件

### KlineKafkaProducerServiceImpl

```java
@ConditionalOnProperty(name = "kline.kafka.enabled", havingValue = "true")
```

| kline.kafka.enabled | Bean 是否创建 | 说明 |
|---------------------|--------------|------|
| `true` | ✅ 创建 | 可以发送 K线数据到 Kafka |
| `false` | ❌ 不创建 | 不会发送数据到 Kafka |
| 未配置 | ❌ 不创建 | 默认值为 `false` |

### KafkaKlineConsumerServiceImpl

```java
@ConditionalOnProperty(name = "kline.kafka.consumer.enabled", havingValue = "true", matchIfMissing = false)
```

| kline.kafka.consumer.enabled | Bean 是否创建 | 说明 |
|------------------------------|--------------|------|
| `true` | ✅ 创建 | 从 Kafka 消费 K线数据 |
| `false` | ❌ 不创建 | 不消费 Kafka 数据 |
| 未配置 | ❌ 不创建 | `matchIfMissing=false` |

---

## 迁移指南

### 从模式 1 迁移到模式 2

1. **停止 okx-trading**
   ```bash
   # 停止应用
   ```

2. **修改配置**
   ```properties
   # 禁用 K线数据生产
   kline.kafka.enabled=false
   
   # 启用 K线数据消费
   kline.kafka.consumer.enabled=true
   ```

3. **启动 data-warehouse K线采集作业**
   ```bash
   cd data-warehouse
   bash run-flink-kline-collector.sh
   ```

4. **配置 Redis 订阅**
   ```bash
   redis-cli SADD kline:subscriptions "BTC-USDT:1D"
   redis-cli SADD kline:subscriptions "ETH-USDT:4H"
   ```

5. **启动 okx-trading**
   ```bash
   # 启动应用
   ```

6. **验证数据流**
   ```bash
   # 查看 Kafka 消费统计
   tail -f logs/all/all.log | grep "K线消费统计"
   
   # 查看 Redis 数据
   redis-cli ZRANGE kline:BTC-USDT:1D 0 -1
   ```

---

## 监控和调试

### 检查当前模式

```bash
# 查看配置
grep "kline.kafka" application.properties

# 查看 Bean 是否创建
curl http://localhost:8088/actuator/beans | grep -i kline
```

### 查看数据流

**模式 1**:
```bash
# 查看 WebSocket 订阅
tail -f logs/all/all.log | grep "订阅K线数据"

# 查看 Kafka 发送
tail -f logs/all/all.log | grep "K线数据已发送到 Kafka"
```

**模式 2**:
```bash
# 查看 Kafka 消费
tail -f logs/all/all.log | grep "K线消费统计"

# 查看 Redis 更新
tail -f logs/all/all.log | grep "更新K线数据"
```

---

## 常见问题

### Q1: 两个配置可以同时启用吗？

A: 技术上可以，但不推荐。会导致：
- okx-trading 同时订阅 WebSocket 和消费 Kafka
- 数据重复处理
- 资源浪费

### Q2: 如何选择使用哪种模式？

A: 
- **单应用场景**：使用模式 1（简单）
- **多应用场景**：使用模式 2（推荐）
- **需要数据仓库**：必须使用模式 2

### Q3: 切换模式需要清理数据吗？

A: 
- Redis 数据：不需要清理，会自动覆盖
- Kafka 数据：不需要清理，消费者会从最新位置开始

### Q4: 如何验证配置生效？

A:
```bash
# 查看日志
tail -f logs/all/all.log | grep -i "kafka"

# 模式 1 会看到: "K线数据已发送到 Kafka"
# 模式 2 会看到: "K线消费统计"
```

---

## 相关文档

- [KAFKA_KLINE_CONSUMER_CONFIG.md](KAFKA_KLINE_CONSUMER_CONFIG.md) - 消费者配置详解
- [KAFKA_INTEGRATION.md](KAFKA_INTEGRATION.md) - Kafka 集成架构
- [data-warehouse/KLINE_COLLECTOR_GUIDE.md](../data-warehouse/KLINE_COLLECTOR_GUIDE.md) - K线采集器指南
