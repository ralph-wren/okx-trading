#!/bin/bash

# Kafka 快速启动脚本
# 用于本地开发和测试

echo "========================================="
echo "Kafka 快速启动脚本"
echo "========================================="

# 检查 Docker 是否安装
if ! command -v docker &> /dev/null; then
    echo "❌ Docker 未安装，请先安装 Docker"
    exit 1
fi

# 1. 启动 Kafka（使用 Docker）
echo ""
echo "📦 启动 Kafka 容器..."
docker run -d \
  --name kafka-okx-trading \
  -p 9092:9092 \
  -e KAFKA_NODE_ID=1 \
  -e KAFKA_PROCESS_ROLES=broker,controller \
  -e KAFKA_LISTENERS=PLAINTEXT://0.0.0.0:9092,CONTROLLER://0.0.0.0:9093 \
  -e KAFKA_ADVERTISED_LISTENERS=PLAINTEXT://localhost:9092 \
  -e KAFKA_CONTROLLER_LISTENER_NAMES=CONTROLLER \
  -e KAFKA_LISTENER_SECURITY_PROTOCOL_MAP=CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT \
  -e KAFKA_CONTROLLER_QUORUM_VOTERS=1@localhost:9093 \
  -e KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR=1 \
  -e KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR=1 \
  -e KAFKA_TRANSACTION_STATE_LOG_MIN_ISR=1 \
  -e KAFKA_GROUP_INITIAL_REBALANCE_DELAY_MS=0 \
  -e KAFKA_NUM_PARTITIONS=3 \
  apache/kafka:latest

if [ $? -eq 0 ]; then
    echo "✅ Kafka 容器启动成功"
else
    echo "❌ Kafka 容器启动失败"
    exit 1
fi

# 等待 Kafka 启动
echo ""
echo "⏳ 等待 Kafka 服务启动（30秒）..."
sleep 30

# 2. 创建 Topic
echo ""
echo "📝 创建 K线数据 Topic..."
docker exec kafka-okx-trading \
  /opt/kafka/bin/kafka-topics.sh \
  --create \
  --topic okx-kline-data \
  --bootstrap-server localhost:9092 \
  --partitions 3 \
  --replication-factor 1 \
  --if-not-exists

if [ $? -eq 0 ]; then
    echo "✅ Topic 创建成功"
else
    echo "⚠️  Topic 可能已存在或创建失败"
fi

# 3. 查看 Topic 信息
echo ""
echo "📊 Topic 信息："
docker exec kafka-okx-trading \
  /opt/kafka/bin/kafka-topics.sh \
  --describe \
  --topic okx-kline-data \
  --bootstrap-server localhost:9092

echo ""
echo "========================================="
echo "✅ Kafka 启动完成！"
echo "========================================="
echo ""
echo "📌 Kafka 地址: localhost:9092"
echo "📌 Topic 名称: okx-kline-data"
echo "📌 分区数量: 3"
echo ""
echo "🔧 常用命令："
echo "  - 查看消息: docker exec kafka-okx-trading /opt/kafka/bin/kafka-console-consumer.sh --topic okx-kline-data --bootstrap-server localhost:9092 --from-beginning"
echo "  - 查看消费者组: docker exec kafka-okx-trading /opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server localhost:9092 --group okx-trading-kline-consumer --describe"
echo "  - 停止 Kafka: docker stop kafka-okx-trading"
echo "  - 删除容器: docker rm kafka-okx-trading"
echo ""
echo "📝 下一步："
echo "  1. 修改 application.properties: kline.kafka.enabled=true"
echo "  2. 重启应用"
echo "  3. 观察日志，确认数据流向 Kafka"
echo ""
