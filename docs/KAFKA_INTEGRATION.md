# Kafka 集成说明

## 架构设计

### 数据流向

```
OKX WebSocket API
    ↓
data-warehouse 项目 (数据采集)
    ↓
Kafka Topics
    ├─ crypto-ticker-spot (现货数据)
    └─ crypto-ticker-swap (合约数据)
    ↓
    ├─→ data-warehouse Flink 作业 (数据仓库 ODS/DWD/DWS/ADS 层)
    └─→ okx-trading (可选：实时策略消费)
```

## 配置说明

### 1. okx-trading 项目配置

#### 默认配置（推荐）

```properties
# 不启用 Kafka 生产者（数据由 data-warehouse 统一采集）
kline.kafka.enabled=false
```

**说明**：
- okx-trading 专注于实时交易策略执行
- 不再重复消费 WebSocket 并写入 Kafka
- 避免与 data-warehouse 的数据采集功能重复

#### 可选配置：从 Kafka 消费数据

如果 okx-trading 需要从 Kafka 消费历史数据或实时数据流，可以添加 Kafka Consumer：

```properties
# Kafka Consumer 配置（可选）
spring.kafka.consumer.bootstrap-servers=localhost:9093
spring.kafka.consumer.group-id=okx-trading-consumer
spring.kafka.consumer.auto-offset-reset=latest
spring.kafka.consumer.enable-auto-commit=false

# 订阅的 Topics
kafka.consumer.topics=crypto-ticker-spot,crypto-ticker-swap
```

### 2. data-warehouse 项目配置

data-warehouse 项目已完成 WebSocket → Kafka 的数据采集功能：

#### 配置文件位置
- `data-warehouse/src/main/resources/config/application-dev.yml`
- `data-warehouse/src/main/resources/config/application-docker.yml`
- `data-warehouse/src/main/resources/config/application-prod.yml`

#### 核心配置

```yaml
# OKX WebSocket 配置
okx:
  websocket:
    url: wss://ws.okx.com:8443/ws/v5/public
  symbols:
    spot: BTC-USDT,ETH-USDT,SOL-USDT,BNB-USDT,XRP-USDT
    swap: BTC-USDT-SWAP,ETH-USDT-SWAP,SOL-USDT-SWAP,BNB-USDT-SWAP

# Kafka 配置
kafka:
  bootstrap-servers: localhost:9093
  topic:
    crypto-ticker-spot: crypto-ticker-spot  # 现货 Topic
    crypto-ticker-swap: crypto-ticker-swap  # 合约 Topic
  producer:
    acks: 1
    retries: 3
    compression-type: lz4
```

## 核心组件

### data-warehouse 项目

#### 1. OKXWebSocketClient
- 位置：`data-warehouse/src/main/java/com/crypto/dw/collector/OKXWebSocketClient.java`
- 功能：
  - 连接 OKX WebSocket API
  - 同时订阅现货和合约数据
  - 自动重连机制
  - 数据分流到不同 Kafka Topics

#### 2. KafkaProducerManager
- 位置：`data-warehouse/src/main/java/com/crypto/dw/kafka/KafkaProducerManager.java`
- 功能：
  - 管理 Kafka Producer 连接
  - 支持指定 Topic 发送消息
  - 异步发送，性能优化
  - 统计成功/失败次数

#### 3. DataCollectorMain
- 位置：`data-warehouse/src/main/java/com/crypto/dw/collector/DataCollectorMain.java`
- 功能：
  - 启动数据采集服务
  - 初始化 WebSocket 和 Kafka 连接
  - 优雅关闭处理

## 数据格式

### Kafka 消息格式

```json
{
  "instId": "BTC-USDT",
  "last": "50000.5",
  "lastSz": "0.1",
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

### Topic 分配规则

- **现货数据**：`instId` 不包含 `-SWAP` 后缀 → `crypto-ticker-spot`
- **合约数据**：`instId` 包含 `-SWAP` 后缀 → `crypto-ticker-swap`

## 启动顺序

### 1. 启动 Kafka

```bash
# 进入 data-warehouse 目录
cd data-warehouse

# 启动 Kafka
./manage-kafka.sh start
```

### 2. 启动 data-warehouse 数据采集

```bash
# 方式 1：使用脚本
./run-collector.sh

# 方式 2：使用 Maven
mvn exec:java -Dexec.mainClass="com.crypto.dw.collector.DataCollectorMain" -Dexec.args="--APP_ENV dev"
```

### 3. 启动 okx-trading（可选）

```bash
# 进入 okx-trading 目录
cd okx-trading

# 启动应用
mvn spring-boot:run
```

## 监控和验证

### 1. 查看 Kafka Topics

```bash
# 列出所有 Topics
docker exec -it kafka-okx-trading kafka-topics.sh --bootstrap-server localhost:9092 --list

# 查看 Topic 详情
docker exec -it kafka-okx-trading kafka-topics.sh --bootstrap-server localhost:9092 --describe --topic crypto-ticker-spot
```

### 2. 消费 Kafka 消息（测试）

```bash
# 消费现货数据
docker exec -it kafka-okx-trading kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 \
  --topic crypto-ticker-spot \
  --from-beginning

# 消费合约数据
docker exec -it kafka-okx-trading kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 \
  --topic crypto-ticker-swap \
  --from-beginning
```

### 3. 查看 Kafka UI

访问：http://localhost:8090

- 查看 Topics 列表
- 查看消息数量
- 查看 Consumer Groups

## 故障排查

### 问题 1：Kafka 连接失败

**症状**：`Connection refused` 或 `Timeout`

**解决方案**：
1. 检查 Kafka 是否启动：`docker ps | grep kafka`
2. 检查端口是否正确：`9092`（容器内）或 `9093`（宿主机）
3. 检查防火墙设置

### 问题 2：WebSocket 连接失败

**症状**：`WebSocket connection failed`

**解决方案**：
1. 检查网络连接
2. 检查代理设置（如果使用代理）
3. 查看日志：`tail -f logs/collector.log`

### 问题 3：数据未写入 Kafka

**症状**：Kafka Topic 中没有数据

**解决方案**：
1. 检查 WebSocket 是否连接成功
2. 检查订阅的交易对是否正确
3. 查看 Kafka Producer 日志
4. 验证 Topic 是否存在

## 性能优化

### 1. Kafka Producer 配置

```yaml
kafka:
  producer:
    acks: 1  # 平衡性能和可靠性
    retries: 3  # 重试次数
    batch-size: 16384  # 批量大小
    linger-ms: 10  # 批量延迟
    compression-type: lz4  # 压缩算法
```

### 2. WebSocket 配置

```yaml
okx:
  websocket:
    reconnect:
      max-retries: 10  # 最大重连次数
      initial-delay: 1000  # 初始延迟
      max-delay: 60000  # 最大延迟
```

## 总结

- **data-warehouse**：统一的数据采集入口，WebSocket → Kafka
- **okx-trading**：专注实时交易策略，不再重复采集数据
- **架构优势**：职责清晰、避免重复、易于维护

如需从 Kafka 消费数据，okx-trading 可以添加 Kafka Consumer 功能（可选）。
