package com.okx.trading.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.okx.trading.model.market.Candlestick;
import com.okx.trading.strategy.RealTimeStrategyManager;
import com.okx.trading.util.BigDecimalUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Lazy;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * K线数据 Kafka 消费者服务
 * 从 Kafka 消费 K线数据并处理
 * 只在 kline.kafka.enabled=true 时启用
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "kline.kafka.enabled", havingValue = "true")
public class KlineKafkaConsumerService {

    @Lazy
    @Autowired(required = false)
    private RealTimeStrategyManager realTimeStrategyManager;

    @Autowired
    private NotificationService emailNotificationService;

    /**
     * 消费 K线数据
     * 
     * @param message 消息内容
     * @param partition 分区号
     * @param offset 偏移量
     * @param acknowledgment 手动确认对象
     */
    @KafkaListener(
        topics = "${kline.kafka.topic}",
        groupId = "${spring.kafka.consumer.group-id}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeKlineData(
            @Payload String message,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        try {
            log.debug("📥 接收到 Kafka 消息: partition={}, offset={}", partition, offset);

            // 解析消息
            JSONObject messageObj = JSON.parseObject(message);
            String symbol = messageObj.getString("symbol");
            String interval = messageObj.getString("interval");
            JSONObject klineData = messageObj.getJSONObject("data");

            // 解析 K线数据
            Candlestick candlestick = parseKlineData(symbol, interval, klineData);
            
            if (candlestick != null) {
                // 更新邮件通知服务的最新价格
                emailNotificationService.updateLatestPrice(symbol, candlestick.getClose());

                log.debug("✅ 从 Kafka 处理 K线数据: symbol={}, interval={}, close={}", 
                    symbol, interval, candlestick.getClose());

                // 通知实时策略管理器处理新的K线数据
                if (realTimeStrategyManager != null) {
                    realTimeStrategyManager.handleNewKlineData(symbol, interval, candlestick);
                }
            }

            // 手动提交偏移量
            acknowledgment.acknowledge();
            log.trace("✅ 偏移量已提交: partition={}, offset={}", partition, offset);

        } catch (Exception e) {
            log.error("❌ 处理 Kafka K线数据失败: partition={}, offset={}, error={}", 
                partition, offset, e.getMessage(), e);
            // 不提交偏移量，下次重启会重新消费
            // 如果需要跳过错误消息，可以调用 acknowledgment.acknowledge()
        }
    }

    /**
     * 解析 K线数据
     */
    private Candlestick parseKlineData(String symbol, String interval, JSONObject klineData) {
        try {
            Candlestick candlestick = new Candlestick();
            candlestick.setSymbol(symbol);
            candlestick.setIntervalVal(interval);

            // 解析时间戳
            if (klineData.containsKey("ts")) {
                long timestamp = klineData.getLongValue("ts");
                LocalDateTime time = LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(timestamp), 
                    ZoneId.of("UTC+8")
                );
                candlestick.setOpenTime(time);
            }

            // 解析 OHLCV 数据
            if (klineData.containsKey("o")) {
                candlestick.setOpen(BigDecimalUtil.safeGen(klineData.getString("o")));
            }
            if (klineData.containsKey("h")) {
                candlestick.setHigh(BigDecimalUtil.safeGen(klineData.getString("h")));
            }
            if (klineData.containsKey("l")) {
                candlestick.setLow(BigDecimalUtil.safeGen(klineData.getString("l")));
            }
            if (klineData.containsKey("c")) {
                candlestick.setClose(BigDecimalUtil.safeGen(klineData.getString("c")));
            }
            if (klineData.containsKey("vol")) {
                candlestick.setVolume(BigDecimalUtil.safeGen(klineData.getString("vol")));
            }

            return candlestick;
        } catch (Exception e) {
            log.error("解析 K线数据失败: symbol={}, interval={}", symbol, interval, e);
            return null;
        }
    }
}
