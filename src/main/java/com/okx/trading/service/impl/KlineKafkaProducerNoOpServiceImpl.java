package com.okx.trading.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.okx.trading.service.KlineKafkaProducerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * K线数据 Kafka 生产者空实现
 * 当 kline.kafka.enabled=false 时启用
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "kline.kafka.enabled", havingValue = "false", matchIfMissing = true)
public class KlineKafkaProducerNoOpServiceImpl implements KlineKafkaProducerService {

    @Override
    public void sendKlineData(String symbol, String interval, JSONObject klineData) {
        // 空实现，不做任何操作
        log.trace("Kafka 未启用，跳过 K线数据发送");
    }

    @Override
    public boolean isEnabled() {
        return false;
    }
}
