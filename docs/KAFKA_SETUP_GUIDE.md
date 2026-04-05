# Kafka 完整配置指南

## 快速开始（推荐）

### 1. 启动 Kafka 服务

使用 Docker Compose 一键启动：

```bash
cd okx-trading
docker-compose -f docker-compose-kafka.yml up -d
```

这将启动：
- Kafka 服务（端口 9092）
- Kafka UI 管理界面（端口 8090）

### 2. 初始化 Topic

等待 Kafka 启动后（约 30 秒），运行初始化脚本：

```bash
chmod +x kafka-init.sh
./kafka-init.sh
```

### 3. 验证配置

访问 Kafka UI 管理界面：

```
http://localhost:8090
```

在界面中可以看到：
- Topic: okx-kline-data
- 分区数: 3
- 消费者组: okx-trading-kline-consumer

### 4. 启动应用

```bash
mvn clean install
mvn spring-boot:run
```

应用启动后，查看日志确认 Kafka 已启用：

```
✅ Kafka 生产者配置已初始化: bootstrapServers=localhost:9092
✅ Kafka 消费者配置已初始化: bootstrapServers=localhost:9092, groupId=okx-trading-kline-consumer
✅ Kafka 监听器容器工厂已初始化: concurrency=3, ackMode=MANUAL
```

### 5. 测试数据流

订阅一个交易对的 K线数据，观察日志：

```
📤 K线数据已发送到 Kafka: symbol=BTC-USDT, interval=1m
📥 接收到 Kafka 消息: partition=0, offset=123
✅ 从 Kafka 处理 K线数据: symbol=BTC-USDT, interval=1m, close=50000.00
✅ 偏移量已提交: partition=0, offset=123
```

## 详细配置说明

### Maven 依赖

已在 `pom.xml` 中添加：

```xml
<dependency>
    <groupId>org.springframework.kafka</groupId>
    <artifactId>spring-kafka</artifactId>
</dependency>
```

### 应用配置

已在 `application.properties` 中配置：

```properties
# Kafka 启用（默认）
kline.kafka.enabled=true

# Kafka 服务器地址
spring.kafka.bootstrap-servers=localhost:9092

# 生产者配置
spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer
spring.kafka.producer.value-serializer=org.apache.kafka.common.serialization.StringSerializer
spring.kafka.producer.acks=1
spring.kafka.producer.retries=3

# 消费者配置
spring.kafka.consumer.group-id=okx-trading-kline-consumer
spring.kafka.consumer.key-deserializer=org.apache.kafka.common.serialization.StringDeserializer
spring.kafka.consumer.value-deserializer=org.apache.kafka.common.serialization.StringDeserializer
spring.kafka.consumer.auto-offset-reset=earliest
spring.kafka.consumer.enable-auto-commit=false

# Topic 名称
kline.kafka.topic=okx-kline-data
```

### Docker Compose 配置

`docker-compose-kafka.yml` 包含：

1. **Kafka 服务**
   - 使用 KRaft 模式（无需 Zookeeper）
   - 端口: 9092（客户端）、9093（控制器）
   - 数据持久化: kafka-data volume
   - 健康检查: 自动检测服务状态

2. **Kafka UI**
   - 可视化管理界面
   - 端口: 8090
   - 功能: 查看 Topic、消息、消费者组等

### Topic 配置

`kafka-init.sh` 创建的 Topic 配置：

- **名称**: okx-kline-data
- **分区数**: 3（支持并发消费）
- **副本数**: 1（单节点部署）
- **保留时间**: 7天（604800000 ms）
- **分段时间**: 1天（86400000 ms）
- **压缩类型**: lz4（高效压缩）

## 常用操作

### 查看 Kafka 日志

```bash
docker logs -f kafka-okx-trading
```

### 查看消费者组状态

```bash
docker exec kafka-okx-trading \
  /opt/kafka/bin/kafka-consumer-groups.sh \
  --bootstrap-server localhost:9092 \
  --group okx-trading-kline-consumer \
  --describe
```

输出示例：

```
GROUP                           TOPIC           PARTITION  CURRENT-OFFSET  LOG-END-OFFSET  LAG
okx-trading-kline-consumer      okx-kline-data  0          1234            1234            0
okx-trading-kline-consumer      okx-kline-data  1          5678            5678            0
okx-trading-kline-consumer      okx-kline-data  2          9012            9012            0
```

- **LAG = 0**: 消费者已追上生产者，无积压
- **LAG > 0**: 有消息积压，需要优化消费速度

### 查看最新消息

```bash
docker exec kafka-okx-trading \
  /opt/kafka/bin/kafka-console-consumer.sh \
  --topic okx-kline-data \
  --bootstrap-server localhost:9092 \
  --from-beginning \
  --max-messages 10
```

### 重置消费者偏移量

如果需要重新消费所有消息：

```bash
# 1. 停止应用
# 2. 重置偏移量到最早
docker exec kafka-okx-trading \
  /opt/kafka/bin/kafka-consumer-groups.sh \
  --bootstrap-server localhost:9092 \
  --group okx-trading-kline-consumer \
  --reset-offsets \
  --to-earliest \
  --topic okx-kline-data \
  --execute

# 3. 重启应用
```

### 删除 Topic（慎用）

```bash
docker exec kafka-okx-trading \
  /opt/kafka/bin/kafka-topics.sh \
  --delete \
  --topic okx-kline-data \
  --bootstrap-server localhost:9092
```

### 停止 Kafka 服务

```bash
docker-compose -f docker-compose-kafka.yml down
```

保留数据：

```bash
docker-compose -f docker-compose-kafka.yml down
```

删除数据：

```bash
docker-compose -f docker-compose-kafka.yml down -v
```

## 生产环境部署

### 1. 多节点集群

修改 `docker-compose-kafka.yml`，添加多个 Kafka 节点：

```yaml
services:
  kafka-1:
    # ... 配置节点1
  kafka-2:
    # ... 配置节点2
  kafka-3:
    # ... 配置节点3
```

### 2. 增加副本数

```bash
docker exec kafka-okx-trading \
  /opt/kafka/bin/kafka-topics.sh \
  --alter \
  --topic okx-kline-data \
  --replication-factor 3 \
  --bootstrap-server localhost:9092
```

### 3. 监控和告警

推荐使用：
- **Prometheus + Grafana**: 监控 Kafka 指标
- **Kafka Exporter**: 导出 Kafka 指标
- **AlertManager**: 配置告警规则

### 4. 性能优化

```properties
# 增加生产者批量大小
spring.kafka.producer.batch-size=32768
spring.kafka.producer.linger-ms=20

# 增加消费者并发数
# 在 KafkaConfig.java 中修改
factory.setConcurrency(5);

# 增加分区数
# 重建 Topic 或使用 kafka-topics.sh --alter
```

## 故障排查

### 问题1: Kafka 启动失败

**症状**: Docker 容器反复重启

**解决方案**:
1. 查看日志: `docker logs kafka-okx-trading`
2. 检查端口占用: `netstat -an | grep 9092`
3. 清理数据重启: `docker-compose -f docker-compose-kafka.yml down -v && docker-compose -f docker-compose-kafka.yml up -d`

### 问题2: 应用连接 Kafka 失败

**症状**: 日志显示连接超时

**解决方案**:
1. 检查 Kafka 是否启动: `docker ps | grep kafka`
2. 检查配置: `spring.kafka.bootstrap-servers=localhost:9092`
3. 测试连接: `telnet localhost 9092`

### 问题3: 消费者不消费消息

**症状**: LAG 持续增长

**解决方案**:
1. 检查消费者日志: 查找异常信息
2. 检查偏移量: 使用 `kafka-consumer-groups.sh --describe`
3. 重启应用: 触发重新平衡

### 问题4: 消息丢失

**症状**: 生产者发送成功，但消费者未收到

**解决方案**:
1. 检查 Topic 保留时间: 是否已过期
2. 检查偏移量: 是否跳过了某些消息
3. 检查日志: 查找处理失败的消息

## 监控指标

### 关键指标

1. **生产者指标**
   - 发送速率（messages/sec）
   - 发送延迟（ms）
   - 失败率（%）

2. **消费者指标**
   - 消费速率（messages/sec）
   - 消费延迟（LAG）
   - 处理时间（ms）

3. **Kafka 指标**
   - 磁盘使用率（%）
   - 网络吞吐量（MB/s）
   - 分区数量

### 查看指标

使用 Kafka UI（http://localhost:8090）查看：
- Topics → okx-kline-data → Metrics
- Consumers → okx-trading-kline-consumer → Lag

## 备份和恢复

### 备份 Kafka 数据

```bash
# 导出消息到文件
docker exec kafka-okx-trading \
  /opt/kafka/bin/kafka-console-consumer.sh \
  --topic okx-kline-data \
  --bootstrap-server localhost:9092 \
  --from-beginning \
  --timeout-ms 10000 > kafka-backup.json
```

### 恢复 Kafka 数据

```bash
# 从文件导入消息
cat kafka-backup.json | docker exec -i kafka-okx-trading \
  /opt/kafka/bin/kafka-console-producer.sh \
  --topic okx-kline-data \
  --bootstrap-server localhost:9092
```

## 总结

✅ **已完成配置**:
- Maven 依赖已添加
- 应用配置已完成（默认启用）
- Docker Compose 文件已创建
- 初始化脚本已创建
- 完整文档已提供

🚀 **下一步**:
1. 运行 `docker-compose -f docker-compose-kafka.yml up -d`
2. 运行 `./kafka-init.sh`
3. 启动应用
4. 访问 http://localhost:8090 查看 Kafka UI

📚 **参考文档**:
- [KAFKA_INTEGRATION.md](KAFKA_INTEGRATION.md) - 集成说明
- [KAFKA_DEPENDENCIES.md](KAFKA_DEPENDENCIES.md) - 依赖说明
- [kafka-quickstart.sh](./kafka-quickstart.sh) - 快速启动脚本

---

配置完成时间：2026-04-05  
作者：Kiro AI Assistant
