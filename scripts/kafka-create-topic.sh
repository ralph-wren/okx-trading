#!/bin/bash

# Kafka Topic 创建脚本
# 用于创建 okx-kline-data topic，包含4个分区

TOPIC_NAME="okx-kline-data"
PARTITIONS=4
REPLICATION_FACTOR=1
KAFKA_CONTAINER="kafka-okx-trading"

echo "🚀 开始创建/更新 Kafka Topic: $TOPIC_NAME"
echo "分区数: $PARTITIONS"
echo "副本因子: $REPLICATION_FACTOR"
echo ""

# 检查 Kafka 容器是否运行
if ! docker ps | grep -q $KAFKA_CONTAINER; then
    echo "❌ Kafka 容器未运行，请先启动 Kafka"
    echo "运行命令: docker-compose -f docker-compose-kafka.yml up -d"
    exit 1
fi

echo "✅ Kafka 容器正在运行"
echo ""

# 检查 topic 是否已存在
echo "📋 检查 topic 是否存在..."
TOPIC_EXISTS=$(docker exec $KAFKA_CONTAINER kafka-topics.sh \
    --bootstrap-server localhost:9092 \
    --list | grep "^${TOPIC_NAME}$")

if [ -n "$TOPIC_EXISTS" ]; then
    echo "⚠️  Topic 已存在，查看当前配置..."
    docker exec $KAFKA_CONTAINER kafka-topics.sh \
        --bootstrap-server localhost:9092 \
        --describe \
        --topic $TOPIC_NAME
    
    echo ""
    read -p "是否要增加分区数到 $PARTITIONS? (y/n): " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        echo "📈 增加分区数..."
        docker exec $KAFKA_CONTAINER kafka-topics.sh \
            --bootstrap-server localhost:9092 \
            --alter \
            --topic $TOPIC_NAME \
            --partitions $PARTITIONS
        
        if [ $? -eq 0 ]; then
            echo "✅ 分区数已更新"
        else
            echo "❌ 更新分区数失败"
            exit 1
        fi
    else
        echo "⏭️  跳过更新"
    fi
else
    echo "📝 创建新 topic..."
    docker exec $KAFKA_CONTAINER kafka-topics.sh \
        --bootstrap-server localhost:9092 \
        --create \
        --topic $TOPIC_NAME \
        --partitions $PARTITIONS \
        --replication-factor $REPLICATION_FACTOR \
        --config retention.ms=604800000 \
        --config segment.bytes=1073741824
    
    if [ $? -eq 0 ]; then
        echo "✅ Topic 创建成功"
    else
        echo "❌ Topic 创建失败"
        exit 1
    fi
fi

echo ""
echo "📊 最终 Topic 配置:"
docker exec $KAFKA_CONTAINER kafka-topics.sh \
    --bootstrap-server localhost:9092 \
    --describe \
    --topic $TOPIC_NAME

echo ""
echo "✅ 完成！"
echo ""
echo "💡 提示:"
echo "  - 分区数只能增加，不能减少"
echo "  - 相同币种的数据会通过 hash 分配到同一个分区"
echo "  - 可以通过 Kafka UI 查看: http://localhost:8090"
