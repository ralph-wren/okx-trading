# Kafka 集成 - 完整文档索引

## 📚 文档列表

### 1. [KAFKA_SETUP_GUIDE.md](KAFKA_SETUP_GUIDE.md) - 完整配置指南 ⭐
**推荐首先阅读**

包含内容：
- 快速开始（5分钟上手）
- 详细配置说明
- 常用操作命令
- 生产环境部署
- 故障排查
- 监控指标
- 备份和恢复

### 2. [KAFKA_INTEGRATION.md](KAFKA_INTEGRATION.md) - 集成说明文档
**技术实现细节**

包含内容：
- 架构设计
- 核心特性
- 配置说明
- 核心组件
- 数据格式
- 偏移量管理
- 性能优化
- 最佳实践

### 3. [KAFKA_DEPENDENCIES.md](KAFKA_DEPENDENCIES.md) - 依赖说明
**Maven 配置**

包含内容：
- Maven 依赖配置
- 版本兼容性
- 可选依赖
- 故障排查

## 🚀 快速启动

### 方式一：使用 Docker Compose（推荐）

```bash
# 1. 启动 Kafka
docker-compose -f docker-compose-kafka.yml up -d

# 2. 初始化 Topic
chmod +x kafka-init.sh
./kafka-init.sh

# 3. 启动应用
mvn spring-boot:run
```

### 方式二：使用快速启动脚本

```bash
# 1. 启动 Kafka 并创建 Topic
chmod +x kafka-quickstart.sh
./kafka-quickstart.sh

# 2. 启动应用
mvn spring-boot:run
```

## 📁 文件清单

### 配置文件
- `application.properties` - 应用配置（已添加 Kafka 配置）
- `pom.xml` - Maven 依赖（已添加 spring-kafka）

### Docker 文件
- `docker-compose-kafka.yml` - Kafka 服务编排
- `kafka-init.sh` - Topic 初始化脚本
- `kafka-quickstart.sh` - 快速启动脚本

### 文档文件
- `KAFKA_README.md` - 本文件（文档索引）
- `KAFKA_SETUP_GUIDE.md` - 完整配置指南
- `KAFKA_INTEGRATION.md` - 集成说明文档
- `KAFKA_DEPENDENCIES.md` - 依赖说明

### 源代码文件
- `KlineKafkaProducerService.java` - 生产者服务接口
- `KlineKafkaProducerServiceImpl.java` - 生产者实现（Kafka 启用）
- `KlineKafkaProducerNoOpServiceImpl.java` - 生产者实现（Kafka 禁用）
- `KlineKafkaConsumerService.java` - 消费者服务
- `KafkaConfig.java` - Kafka 配置类
- `OkxApiWebSocketServiceImpl.java` - WebSocket 服务（已集成 Kafka）

## 🎯 核心特性

✅ **可配置切换**: 通过 `kline.kafka.enabled` 一键启用/禁用  
✅ **最小化改造**: 原有代码逻辑保持不变  
✅ **偏移量管理**: 手动提交模式，确保重启后继续消费  
✅ **数据不丢失**: Kafka 持久化存储，重启自动恢复  
✅ **条件加载**: 使用 `@ConditionalOnProperty` 避免不必要的 Bean 加载  
✅ **生产就绪**: 批量处理、并发消费、错误重试

## 🔧 配置说明

### 启用 Kafka（默认）

```properties
kline.kafka.enabled=true
spring.kafka.bootstrap-servers=localhost:9092
```

### 禁用 Kafka

```properties
kline.kafka.enabled=false
```

## 📊 数据流向

### 启用 Kafka（默认）
```
WebSocket → handleKlineMessage → Kafka Producer → Kafka Topic
                                                        ↓
                                    Kafka Consumer → 处理 → RealTimeStrategyManager
```

### 禁用 Kafka
```
WebSocket → handleKlineMessage → 直接处理 → RealTimeStrategyManager
```

## 🌐 管理界面

启动 Kafka 后，访问管理界面：

```
http://localhost:8090
```

功能：
- 查看 Topic 列表
- 查看消息内容
- 查看消费者组状态
- 查看分区和副本信息
- 实时监控指标

## 📈 监控命令

### 查看消费者组状态

```bash
docker exec kafka-okx-trading \
  /opt/kafka/bin/kafka-consumer-groups.sh \
  --bootstrap-server localhost:9092 \
  --group okx-trading-kline-consumer \
  --describe
```

### 查看最新消息

```bash
docker exec kafka-okx-trading \
  /opt/kafka/bin/kafka-console-consumer.sh \
  --topic okx-kline-data \
  --bootstrap-server localhost:9092 \
  --from-beginning \
  --max-messages 10
```

### 查看 Topic 信息

```bash
docker exec kafka-okx-trading \
  /opt/kafka/bin/kafka-topics.sh \
  --describe \
  --topic okx-kline-data \
  --bootstrap-server localhost:9092
```

## 🐛 故障排查

### 常见问题

1. **Kafka 启动失败**
   - 查看日志: `docker logs kafka-okx-trading`
   - 检查端口: `netstat -an | grep 9092`

2. **应用连接失败**
   - 检查配置: `spring.kafka.bootstrap-servers`
   - 测试连接: `telnet localhost 9092`

3. **消费者不消费**
   - 查看 LAG: `kafka-consumer-groups.sh --describe`
   - 检查日志: 查找异常信息

详细排查步骤请参考 [KAFKA_SETUP_GUIDE.md](KAFKA_SETUP_GUIDE.md#故障排查)

## 📝 日志示例

### 启动成功日志

```
✅ Kafka 生产者配置已初始化: bootstrapServers=localhost:9092
✅ Kafka 消费者配置已初始化: bootstrapServers=localhost:9092, groupId=okx-trading-kline-consumer
✅ Kafka 监听器容器工厂已初始化: concurrency=3, ackMode=MANUAL
```

### 数据流转日志

```
📤 K线数据已发送到 Kafka: symbol=BTC-USDT, interval=1m
📥 接收到 Kafka 消息: partition=0, offset=123
✅ 从 Kafka 处理 K线数据: symbol=BTC-USDT, interval=1m, close=50000.00
✅ 偏移量已提交: partition=0, offset=123
```

## 🎓 学习路径

1. **初学者**: 
   - 阅读 [KAFKA_SETUP_GUIDE.md](KAFKA_SETUP_GUIDE.md) 快速开始部分
   - 运行 Docker Compose 启动 Kafka
   - 启动应用，观察日志

2. **进阶用户**:
   - 阅读 [KAFKA_INTEGRATION.md](KAFKA_INTEGRATION.md) 了解技术细节
   - 学习偏移量管理和性能优化
   - 配置监控和告警

3. **生产部署**:
   - 阅读生产环境部署章节
   - 配置多节点集群
   - 设置备份和恢复策略

## 🔗 相关链接

- [Apache Kafka 官方文档](https://kafka.apache.org/documentation/)
- [Spring Kafka 官方文档](https://spring.io/projects/spring-kafka)
- [Kafka UI GitHub](https://github.com/provectus/kafka-ui)

## 💡 最佳实践

1. **生产环境建议启用 Kafka**: 确保数据不丢失
2. **开发环境可禁用 Kafka**: 简化部署，快速调试
3. **定期监控消费者 LAG**: 避免消息积压
4. **合理设置分区数**: 根据交易对数量和消费能力调整
5. **配置数据保留策略**: 避免磁盘占满

## 📞 技术支持

如有问题，请查看：
1. [KAFKA_SETUP_GUIDE.md](KAFKA_SETUP_GUIDE.md) 故障排查章节
2. 应用日志: `logs/spring.log`
3. Kafka 日志: `docker logs kafka-okx-trading`

---

**版本**: 1.0  
**创建时间**: 2026-04-05  
**作者**: Kiro AI Assistant  
**状态**: ✅ 配置完成，默认启用
