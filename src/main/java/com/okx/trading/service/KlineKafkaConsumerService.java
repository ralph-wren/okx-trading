package com.okx.trading.service;

/**
 * K线数据 Kafka 消费者服务接口
 * 从 Kafka 消费 K线数据并处理
 * 
 * 始终启用,从 Kafka 消费 K线数据
 * - 当 kline.kafka.enabled=false 时: 数据由 data-warehouse 提供
 * - 当 kline.kafka.enabled=true 时: 数据由 okx-trading 自己写入
 */
public interface KlineKafkaConsumerService {
    
    /**
     * 消费 K线数据
     * 
     * @param message 消息内容
     * @param partition 分区号
     * @param offset 偏移量
     */
    void consumeKlineData(String message, int partition, long offset);
}
