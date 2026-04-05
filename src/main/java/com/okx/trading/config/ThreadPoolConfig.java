package com.okx.trading.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;

/**
 * 线程池配置类
 * 统一配置和管理应用中的线程池
 */
@Configuration
@EnableAsync
public class ThreadPoolConfig{

    @Value("${okx.historical-data.max-threads:10}")
    private int maxHistoricalDataThreads;

    @Value("${okx.price-update.max-threads:5}")
    private int maxPriceUpdateThreads;

    /**
     * 创建带有命名前缀的线程工厂
     *
     * @param namePrefix 线程名称前缀
     * @return 线程工厂
     */
    private ThreadFactory createThreadFactory(String namePrefix){
        return r -> {
            Thread thread = new Thread(r);
            thread.setName(namePrefix + "-" + thread.getId());
            // 设置为守护线程，随主线程退出而退出
            thread.setDaemon(true);
            return thread;
        };
    }

    /**
     * 历史数据查询线程池
     * 用于执行历史数据查询任务
     */
    @Bean(name = "historicalDataExecutorService")
    public ExecutorService historicalDataExecutorService(){
        return Executors.newFixedThreadPool(maxHistoricalDataThreads,
            createThreadFactory("历史数据查询"));
    }

    /**
     * 历史数据批处理线程池
     * 用于处理历史数据的批量保存任务
     */
    @Bean(name = "batchHistoricalDataExecutorService")
    public ExecutorService batchHistoricalDataExecutorService(){
        return Executors.newFixedThreadPool(5,
            createThreadFactory("历史数据批处理"));
    }

    /**
     * 价格更新线程池
     * 用于执行价格更新任务
     */
    @Bean(name = "priceUpdateExecutorService")
    @Primary
    public ExecutorService priceUpdateExecutorService(){
        return Executors.newFixedThreadPool(maxPriceUpdateThreads,
            createThreadFactory("价格更新"));
    }

    @Bean(name = "tradeIndicatorCalculateScheduler")
    public ExecutorService tradeIndicatorCalculateScheduler(){
        return Executors.newFixedThreadPool(20,
            createThreadFactory("交易指标计算"));
    }

    @Bean(name = "realTimeTradeIndicatorCalculateScheduler")
    public ExecutorService realTimeTradeIndicatorCalculateScheduler(){
        return Executors.newFixedThreadPool(20,
            createThreadFactory("实时策略计算"));
    }

    @Bean(name = "executeTradeScheduler")
    public ExecutorService executeTradeScheduler(){
        return Executors.newFixedThreadPool(20,
                createThreadFactory("执行交易和数据库更新"));
    }

    /**
     * WebSocket心跳线程池
     * 用于定期发送WebSocket心跳消息
     */
    @Bean(name = "websocketPingScheduler")
    public ScheduledExecutorService websocketPingScheduler(){
        return Executors.newSingleThreadScheduledExecutor(
            createThreadFactory("WebSocket心跳"));
    }

    /**
     * WebSocket重连线程池
     * 用于WebSocket连接断开后的重连任务
     * 优化: 使用多线程池避免阻塞，提高重连效率
     */
    @Bean(name = "websocketReconnectScheduler")
    public ScheduledExecutorService websocketReconnectScheduler(){
        return Executors.newScheduledThreadPool(3,
            createThreadFactory("WebSocket重连"));
    }

    @Bean(name = "websocketConnectScheduler")
    public ScheduledExecutorService websocketConnectScheduler(){
        return Executors.newScheduledThreadPool(5,
                createThreadFactory("WebSocket初始化"));
    }

    @Bean(name = "coinSubscribeScheduler")
    public ScheduledExecutorService coinSubscribeScheduler(){
        return Executors.newScheduledThreadPool(3,
                createThreadFactory("WebSocket重连"));
    }

    @Bean(name = "databaseUpdateScheduler")
    public ScheduledExecutorService databaseUpdateScheduler(){
        return Executors.newScheduledThreadPool(3,
            createThreadFactory("数据库更新"));
    }


    /**
     * WebSocket重连线程池
     * 用于WebSocket连接断开后的重连任务
     */
    @Bean(name = "klineUpdateScheduler")
    public ScheduledExecutorService klineUpdateScheduler(){
        return Executors.newSingleThreadScheduledExecutor(
            createThreadFactory("K线更新"));
    }

    @Bean(name = "indicatorCalculateScheduler")
    public ScheduledExecutorService indicatorCalculateScheduler(){
        return Executors.newSingleThreadScheduledExecutor(
            createThreadFactory("指标计算"));
    }

    @Bean(name = "klineHandleScheduler")
    public ScheduledExecutorService klineHandleScheduler(){
        return Executors.newSingleThreadScheduledExecutor(
                createThreadFactory("k线处理"));
    }

    /**
     * Kafka 监控线程池
     * 用于定期检查 Kafka 数据新鲜度和消费者 lag
     */
    @Bean(name = "kafkaMonitorScheduler")
    public ScheduledExecutorService kafkaMonitorScheduler(){
        return Executors.newScheduledThreadPool(2,
                createThreadFactory("Kafka监控"));
    }
}
