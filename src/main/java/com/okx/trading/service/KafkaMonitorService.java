package com.okx.trading.service;

/**
 * Kafka 监控服务接口
 * 监控 Kafka 数据新增和消费者 lag 情况
 */
public interface KafkaMonitorService {
    
    /**
     * 检查 Kafka 数据是否正常新增
     */
    void checkDataFreshness();
    
    /**
     * 检查消费者 lag 是否超过阈值
     */
    void checkConsumerLag();
    
    /**
     * 获取最后一次数据接收时间
     * @return 最后接收时间的时间戳
     */
    Long getLastDataReceivedTime();
    
    /**
     * 更新最后一次数据接收时间
     */
    void updateLastDataReceivedTime();
}
