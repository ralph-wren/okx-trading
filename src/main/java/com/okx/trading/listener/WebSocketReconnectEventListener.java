package com.okx.trading.listener;

import com.okx.trading.event.WebSocketReconnectEvent;
import com.okx.trading.service.KlineCacheService;
import com.okx.trading.service.OkxApiService;
import com.okx.trading.strategy.RealTimeStrategyManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * WebSocket重连事件监听器
 * 处理WebSocket重连后的K线数据重新订阅
 * 
 * 注意: 只有当 kline.kafka.enabled=true 时才会重新订阅
 * 如果 kline.kafka.enabled=false，数据来自 Kafka，不需要订阅 WebSocket
 */
@Component
public class WebSocketReconnectEventListener {

    private static final Logger log = LoggerFactory.getLogger(WebSocketReconnectEventListener.class);

    private final KlineCacheService klineCacheService;
    private final OkxApiService okxApiService;
    private final RealTimeStrategyManager realTimeStrategyManager;

    @Value("${kline.kafka.enabled:false}")
    private boolean klineKafkaEnabled;

    @Autowired
    public WebSocketReconnectEventListener(KlineCacheService klineCacheService,
                                           @Lazy OkxApiService okxApiService, RealTimeStrategyManager realTimeStrategyManager) {
        this.klineCacheService = klineCacheService;
        this.okxApiService = okxApiService;
        this.realTimeStrategyManager = realTimeStrategyManager;
    }

    /**
     * 处理WebSocket重连事件
     * 公共频道或业务频道重连时，重新订阅所有K线数据
     * 
     * 注意: 只有当 kline.kafka.enabled=true 时才会重新订阅
     */
    @EventListener
    @Async("websocketReconnectScheduler")
    public void handleWebSocketReconnect(WebSocketReconnectEvent event) {
        log.info("收到WebSocket重连事件，重连类型: {}, kline.kafka.enabled={}", event.getType(), klineKafkaEnabled);

        // 如果 Kafka 未启用，说明数据来自 Kafka，不需要订阅 WebSocket
        if (!klineKafkaEnabled) {
            log.info("kline.kafka.enabled=false，数据来自 Kafka，跳过 WebSocket 重新订阅");
            return;
        }

        try {
            // 公共频道和业务频道重连时都需要重新订阅K线数据
            if (event.getType() == WebSocketReconnectEvent.ReconnectType.PUBLIC) {
                // 延迟3秒等待连接稳定
                Thread.sleep(3000);

                log.info("开始重新订阅WebSocket重连前的K线数据(公共频道重连)...");
                resubscribeAllKlineData();
            } else if (event.getType() == WebSocketReconnectEvent.ReconnectType.BUSINESS) {
                // 业务频道重连时也可能需要重新订阅某些数据
                Thread.sleep(2000);

                log.info("业务频道重连完成，检查是否需要重新订阅数据...");
                // 目前K线订阅主要通过公共频道，业务频道重连暂时不需要特殊处理
                // 但可以在这里添加业务频道相关的重新订阅逻辑
            }
        } catch (Exception e) {
            log.error("处理WebSocket重连事件失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 重新订阅所有K线数据
     */
    private void resubscribeAllKlineData() {
        try {
            // 从缓存服务获取所有之前订阅的K线数据
            Set<String> allSubscribedKlines = realTimeStrategyManager.getRunningStrategies().values().stream()
                    .map(x -> x.getSymbol() + ":" + x.getInterval()).collect(Collectors.toSet());

            if (allSubscribedKlines.isEmpty()) {
                log.info("没有发现之前订阅的K线数据，跳过重新订阅");
                return;
            }

            log.info("发现 {} 个K线订阅记录，开始重新订阅...", allSubscribedKlines.size());

            okxApiService.clearSubscribeCache();
            int totalResubscribed = 0;
            int successCount = 0;

            // 遍历所有订阅的交易对和时间间隔
            for (String symbolInterval : allSubscribedKlines) {
                String[] split = symbolInterval.split(":", -1);
                if (split.length != 2) {
                    log.warn("无效的订阅格式: {}", symbolInterval);
                    continue;
                }

                String symbol = split[0];
                String interval = split[1];

                totalResubscribed++;
                try {
                    // 重新订阅K线数据
                    boolean success = okxApiService.subscribeKlineData(symbol, interval);
                    if (success) {
                        successCount++;
                        log.debug("重新订阅K线数据成功: {} {}", symbol, interval);
                    } else {
                        log.warn("重新订阅K线数据失败: {} {}", symbol, interval);
                    }

                    // 添加小延迟避免频率过高
                    Thread.sleep(100);
                } catch (Exception e) {
                    log.error("重新订阅K线数据异常: {} {}, 错误: {}", symbol, interval, e.getMessage());
                }
            }

            log.info("K线数据重新订阅完成: 总计 {} 个，成功 {} 个，失败 {} 个",
                    totalResubscribed, successCount, totalResubscribed - successCount);

        } catch (Exception e) {
            log.error("重新订阅所有K线数据失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 异步重新订阅指定交易对的K线数据
     */
    @Async("customAsyncTaskExecutor")
    public CompletableFuture<Void> resubscribeKlineDataAsync(String symbol, List<String> intervals) {
        return CompletableFuture.runAsync(() -> {
            try {
                log.info("异步重新订阅交易对 {} 的K线数据，时间间隔: {}", symbol, intervals);

                for (String interval : intervals) {
                    try {
                        boolean success = okxApiService.subscribeKlineData(symbol, interval);
                        if (success) {
                            log.debug("异步重新订阅K线数据成功: {} {}", symbol, interval);
                        } else {
                            log.warn("异步重新订阅K线数据失败: {} {}", symbol, interval);
                        }
                        Thread.sleep(100); // 添加延迟
                    } catch (Exception e) {
                        log.error("异步重新订阅K线数据异常: {} {}, 错误: {}", symbol, interval, e.getMessage());
                    }
                }
            } catch (Exception e) {
                log.error("异步重新订阅K线数据失败: {} {}", symbol, e.getMessage(), e);
            }
        });
    }
}
