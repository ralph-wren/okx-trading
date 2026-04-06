package com.okx.trading.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.okx.trading.model.market.Candlestick;
import com.okx.trading.service.KafkaMonitorService;
import com.okx.trading.service.KlineKafkaConsumerService;
import com.okx.trading.service.NotificationService;
import com.okx.trading.strategy.RealTimeStrategyManager;
import com.okx.trading.util.BigDecimalUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * K线数据 Kafka 消费者服务实现
 */
@Slf4j
@Service
public class KlineKafkaConsumerServiceImpl implements KlineKafkaConsumerService {

    @Lazy
    @Autowired(required = false)
    private RealTimeStrategyManager realTimeStrategyManager;

    @Autowired
    private NotificationService emailNotificationService;

    @Autowired(required = false)
    private KafkaMonitorService kafkaMonitorService;

    /**
     * 消费 K线数据
     * 
     * @param message 消息内容
     * @param partition 分区号
     * @param offset 偏移量
     */
    @Override
    @KafkaListener(
        topics = "${kline.kafka.topic}",
        groupId = "${spring.kafka.consumer.group-id}"
    )
    public void consumeKlineData(
            @Payload String message,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset) {
        
        try {
            log.debug("📥 接收到 Kafka 消息: partition={}, offset={}", partition, offset);

            // 更新监控服务的数据接收时间
            if (kafkaMonitorService != null) {
                kafkaMonitorService.updateLastDataReceivedTime();
            }

            // 解析消息 - 直接解析 OKX 原始格式
            JSONObject messageObj = JSON.parseObject(message);
            
            // 解析 arg
            JSONObject arg = messageObj.getJSONObject("arg");
            if (arg == null) {
                log.warn("K线消息缺少 arg 字段: {}", message);
                return;
            }
            
            String channel = arg.getString("channel");
            String symbol = arg.getString("instId");
            
            if (channel == null || symbol == null) {
                log.warn("K线消息缺少 channel 或 instId: {}", message);
                return;
            }
            
            // 从 channel 提取 interval (例如: candle1D -> 1D)
            String interval = channel.replace("candle", "");
            
            // 解析 data 数组
            JSONArray dataArray = messageObj.getJSONArray("data");
            if (dataArray == null || dataArray.isEmpty()) {
                log.debug("K线消息没有数据: {}", message);
                return;
            }
            
            // 处理第一条 K线数据
            JSONArray klineData = dataArray.getJSONArray(0);
            if (klineData == null || klineData.size() < 9) {
                log.warn("K线数据格式不正确: {}", klineData);
                return;
            }

            // 解析 K线数据
            Candlestick candlestick = parseOKXKlineData(symbol, interval, klineData);
            
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

        } catch (Exception e) {
            log.error("❌ 处理 Kafka K线数据失败: partition={}, offset={}, error={}", 
                partition, offset, e.getMessage(), e);
        }
    }

    /**
     * 解析 OKX K线数据 (原始格式)
     * <p>
     * OKX K线数据数组格式:
     * [
     *   "1712188800000",  // 0: 开盘时间戳
     *   "67000.0",        // 1: 开盘价
     *   "68000.0",        // 2: 最高价
     *   "66500.0",        // 3: 最低价
     *   "67500.0",        // 4: 收盘价
     *   "1234.56",        // 5: 成交量（币）
     *   "83456789.12",    // 6: 成交量（USDT）
     *   "83456789.12",    // 7: 成交量（USDT）- 重复
     *   "1"               // 8: 确认状态：0=未确认，1=已确认
     * ]
     */
    private Candlestick parseOKXKlineData(String symbol, String interval, JSONArray klineData) {
        try {
            // 时间戳
            long timestamp = klineData.getLongValue(0);
            LocalDateTime openTime = LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(timestamp), 
                    ZoneId.of("UTC+8")
            );
            
            Candlestick.CandlestickBuilder builder = Candlestick.builder()
                    .symbol(symbol)
                    .intervalVal(interval)
                    .openTime(openTime)
                    .open(BigDecimalUtil.safeGen(klineData.getString(1)))
                    .high(BigDecimalUtil.safeGen(klineData.getString(2)))
                    .low(BigDecimalUtil.safeGen(klineData.getString(3)))
                    .close(BigDecimalUtil.safeGen(klineData.getString(4)))
                    .volume(BigDecimalUtil.safeGen(klineData.getString(5)))
                    .quoteVolume(BigDecimalUtil.safeGen(klineData.getString(6)));
            
            // 确认状态：0=未完结，1=已完结
            String confirmStatus = klineData.getString(8);
            builder.state("1".equals(confirmStatus) ? 1 : 0);

            return builder.build();
        } catch (Exception e) {
            log.error("解析 OKX K线数据失败: symbol={}, interval={}", symbol, interval, e);
            return null;
        }
    }
}
