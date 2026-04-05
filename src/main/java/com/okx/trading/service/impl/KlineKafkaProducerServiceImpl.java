package com.okx.trading.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.okx.trading.service.KafkaMonitorService;
import com.okx.trading.service.KlineKafkaProducerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

/**
 * K线数据 Kafka 生产者服务实现
 * 只在 kline.kafka.enabled=true 时启用
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "kline.kafka.enabled", havingValue = "true")
public class KlineKafkaProducerServiceImpl implements KlineKafkaProducerService {

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired(required = false)
    private KafkaMonitorService kafkaMonitorService;

    @Value("${kline.kafka.topic}")
    private String topic;

    @Value("${kline.kafka.enabled}")
    private boolean enabled;

    @Override
    public void sendKlineData(String symbol, String interval, JSONObject klineData) {
        if (!enabled) {
            log.debug("Kafka 未启用，跳过发送");
            return;
        }

        try {
            // 构建消息键：symbol_interval，用于分区
            String key = symbol + "_" + interval;
            
            // 添加元数据
            JSONObject message = new JSONObject();
            message.put("symbol", symbol);
            message.put("interval", interval);
            message.put("data", klineData);
            message.put("timestamp", System.currentTimeMillis());

            // 异步发送到 Kafka
            CompletableFuture<SendResult<String, String>> future = 
                kafkaTemplate.send(topic, key, message.toJSONString());

            future.whenComplete((result, ex) -> {
                if (ex == null) {
                    // 更新监控服务的数据发送时间
                    if (kafkaMonitorService != null) {
                        kafkaMonitorService.updateLastDataReceivedTime();
                    }
                    
                    log.debug("✅ K线数据已发送到 Kafka: topic={}, partition={}, offset={}, key={}", 
                        topic, 
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset(),
                        key);
                } else {
                    log.error("❌ K线数据发送到 Kafka 失败: key={}, error={}", key, ex.getMessage(), ex);
                }
            });

        } catch (Exception e) {
            log.error("发送 K线数据到 Kafka 异常: symbol={}, interval={}", symbol, interval, e);
        }
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
