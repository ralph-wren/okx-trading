package com.okx.trading.service;

import com.alibaba.fastjson.JSONObject;

/**
 * K线数据 Kafka 生产者服务接口
 * 用于将 WebSocket 接收到的 K线数据发送到 Kafka
 */
public interface KlineKafkaProducerService {
    
    /**
     * 发送 K线数据到 Kafka
     * 
     * @param symbol 交易对
     * @param interval 时间周期
     * @param klineData K线数据（JSON格式）
     */
    void sendKlineData(String symbol, String interval, JSONObject klineData);
    
    /**
     * 检查 Kafka 是否已启用
     * 
     * @return true=已启用，false=未启用
     */
    boolean isEnabled();
}
