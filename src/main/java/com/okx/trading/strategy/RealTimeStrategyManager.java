package com.okx.trading.strategy;

import com.okx.trading.model.market.Candlestick;
import com.okx.trading.model.trade.Order;
import com.okx.trading.model.entity.RealTimeOrderEntity;
import com.okx.trading.model.entity.RealTimeStrategyEntity;
import com.okx.trading.adapter.CandlestickBarSeriesConverter;
import com.okx.trading.repository.RealTimeStrategyRepository;
import com.okx.trading.service.*;
import com.okx.trading.controller.TradeController;
import com.okx.trading.service.impl.OkxApiWebSocketServiceImpl;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.ta4j.core.*;
import org.ta4j.core.num.DecimalNum;

import java.math.BigDecimal;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import static com.okx.trading.constant.IndicatorInfo.*;


/**
 * 实时策略管理器
 * 管理正在运行的实时策略，处理WebSocket推送的K线数据
 */
@Slf4j
@Service
@Data
@Scope(proxyMode = ScopedProxyMode.TARGET_CLASS)
@Component
public class RealTimeStrategyManager implements ApplicationRunner {

    // 定义常量替代javax.print.attribute.standard.JobState.CANCELED
    private static final String CANCELED = "CANCELED";
    private static final String SUCCESS = "SUCCESS";

    private final OkxApiWebSocketServiceImpl webSocketService;
    private final RealTimeOrderService realTimeOrderService;
    private final TradeController tradeController;
    private final HistoricalDataService historicalDataService;
    private final OkxApiService okxApiService;
    @Lazy
    private final RealTimeStrategyService realTimeStrategyService;
    private final CandlestickBarSeriesConverter barSeriesConverter;
    private final StrategyInfoService strategyInfoService;
    private final RealTimeStrategyRepository realTimeStrategyRepository;
    private final int kLineNum = 100;
    private boolean loadedStrategies = false;
    private final NotificationService notificationService;
    private ExecutorService executorService;
    private RedisTemplate redisTemplate;
    private final Environment environment;


    public RealTimeStrategyManager(@Lazy OkxApiWebSocketServiceImpl webSocketService,
                                   RealTimeOrderService realTimeOrderService,
                                   TradeController tradeController,
                                   HistoricalDataService historicalDataService, OkxApiService okxApiService,
                                   @Lazy RealTimeStrategyService realTimeStrategyService,
                                   CandlestickBarSeriesConverter barSeriesConverter,
                                   StrategyInfoService strategyInfoService,
                                   RealTimeStrategyRepository realTimeStrategyRepository,
                                   NotificationService notificationService,
                                   @Qualifier("executeTradeScheduler") ExecutorService executorService,
                                   RedisTemplate redisTemplate,
                                   Environment environment) {
        this.webSocketService = webSocketService;
        this.realTimeOrderService = realTimeOrderService;
        this.tradeController = tradeController;
        this.historicalDataService = historicalDataService;
        this.okxApiService = okxApiService;
        this.realTimeStrategyService = realTimeStrategyService;
        this.barSeriesConverter = barSeriesConverter;
        this.strategyInfoService = strategyInfoService;
        this.realTimeStrategyRepository = realTimeStrategyRepository;
        this.notificationService = notificationService;
        this.executorService = executorService;
        this.redisTemplate = redisTemplate;
        this.environment = environment;
    }

    // 存储正在运行的策略信息
    // key: strategyCode_symbol_interval, value: 策略运行状态
    private final Map<Long, RealTimeStrategyEntity> runningStrategies = new ConcurrentHashMap<>();
    private final Map<String, BarSeries> runningBarSeries = new ConcurrentHashMap<>();
    private final Map<String, Long> clientOrderId2StrategyIdMap = new HashMap<>();

    private final DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 处理新的K线数据
     * 由WebSocket服务调用
     */
    public void handleNewKlineData(String symbol, String interval, Candlestick candlestick) {
        // 查找使用该symbol和interval的所有策略
        if (runningStrategies.isEmpty()) {
            return;
        }
        runningStrategies.entrySet().stream()
                .filter(entry -> {
                    RealTimeStrategyEntity state = entry.getValue();
                    return state.getSymbol().equals(symbol) && state.getInterval().equals(interval);
                })
                .forEach(entry -> {
                    RealTimeStrategyEntity state = entry.getValue();
                    try {
                        if (state.getStrategy() != null) {
                            processStrategySignal(state, candlestick);
                        }
                    } catch (Exception e) {
                        log.error("处理策略信号失败: key={}, error={}", buildStrategyKey(state.getStrategyCode(), state.getSymbol(), state.getInterval()), e.getMessage(), e);
                    }
                });
    }

    /**
     * 处理策略信号
     * 真正执行实时策略逻辑，判断买卖信号的地方
     */
    private void processStrategySignal(RealTimeStrategyEntity state, Candlestick candlestick) {

        // 更新BarSeries - 智能判断是更新还是添加新bar
        Bar newBar = createBarFromCandlestick(candlestick);
        BarSeries series = runningBarSeries.get(state.getSymbol() + "_" + state.getInterval());
        boolean shouldReplace = shouldReplaceLastBar(series, newBar, state.getInterval());
        series.addBar(newBar, shouldReplace);
        if (!shouldReplace) {
            series = series.getSubSeries(series.getBeginIndex() + 1, series.getEndIndex() + 1);
        }

        //同一策略同周期内不能重复交易，买、卖只能触发一次，防止短时间都满足多次交易的情况
        synchronized (state) {
            // 控制同一个周期内只能交易一次
            boolean forbiddenTradeTime = false;
            boolean signalOfSamePeriod = false;

            long intervalSeconds = historicalDataService.getIntervalMinutes(candlestick.getIntervalVal()) * 60;
            // 在每个周期的最后15秒判断信号是否触发，而不是在周期刚开始就触发了就执行交易
            // 提到上面，无论是否策略的首次交易都要求在每个周期的最后15秒才触发交易
            forbiddenTradeTime = Duration.between(candlestick.getOpenTime().plusSeconds(intervalSeconds), LocalDateTime.now()).abs().get(ChronoUnit.SECONDS) > 15;
            if (forbiddenTradeTime) {
                return;
            }

            if (state.getLastTradeTime() != null) {
                LocalDateTime lastTradeTime = state.getLastTradeTime();
                //同周期只触发一次交易信号
                signalOfSamePeriod = lastTradeTime.isAfter(candlestick.getOpenTime()) && lastTradeTime.isBefore(candlestick.getOpenTime().plusSeconds(intervalSeconds));
                // 如果是同一周期内的信号，不执行任何交易操作
                if (signalOfSamePeriod) {
                    return;
                }
            }

            // 检查交易信号
            int currentIndex = series.getEndIndex();
            boolean shouldBuy = state.getStrategy().shouldEnter(currentIndex);
            boolean shouldSell = state.getStrategy().shouldExit(currentIndex);


            // 处理买入信号 - 只有在上一次不是买入时才触发
            if (shouldBuy && (StringUtils.isBlank(state.getLastTradeType()) || SELL.equals(state.getLastTradeType()))) {
                executeTradeSignal(state, candlestick, BUY);
            }

            // 处理卖出信号 - 只有在上一次是买入时才触发
            if (shouldSell && BUY.equals(state.getLastTradeType())) {
                executeTradeSignal(state, candlestick, SELL);
            }
        }
    }

    /**
     * 判断是否应该替换最后一个bar（同一周期更新）还是添加新bar（不同周期）
     *
     * @param series   BarSeries
     * @param newBar   新的Bar
     * @param interval K线间隔
     * @return true表示替换最后一个bar（同一周期），false表示添加新bar（不同周期）
     */
    private boolean shouldReplaceLastBar(BarSeries series, Bar newBar, String interval) {
        // 如果series为空或没有bar，直接添加新bar
        if (series == null || series.isEmpty()) {
            return false;
        }

        Bar lastBar = series.getLastBar();
        LocalDateTime newBarStartTime = newBar.getBeginTime().atZone(ZoneId.of("UTC+8")).toLocalDateTime();
        LocalDateTime lastBarStartTime = lastBar.getBeginTime().atZone(ZoneId.of("UTC+8")).toLocalDateTime();

        // 计算周期的开始时间
        LocalDateTime newPeriodStart = getPeriodStartTime(newBarStartTime, interval);
        LocalDateTime lastPeriodStart = getPeriodStartTime(lastBarStartTime, interval);

        // 如果是同一个周期，则替换；否则添加新bar
        return newPeriodStart.equals(lastPeriodStart);
    }

    /**
     * 执行交易信号
     */
    public void executeTradeSignal(RealTimeStrategyEntity state, Candlestick candlestick, String side) {
//        CompletableFuture.runAsync(() -> {
        try {

//            // 防止没更新状态的时候同时去更新状态
//            synchronized (state) {
//                if (redisTemplate.opsForValue().get(TRADE_FLAG + state.getId()) != null) {
//                    // 已经执行过交易，买或者卖，同一策略同一周期内只能执行一次交易，防止频繁交易
//                    log.warn("同一策略同一周期内只能交易一次，跳过交易: strategyId={}", state.getId());
//                    return;
//                }
//            }

            BigDecimal preAmount = null;
            BigDecimal preQuantity = null;
            LocalDateTime singalTime = LocalDateTime.now();

            // 计算交易数量
            if (BUY.equals(side)) {
                // 买入：按照给定金额买入
                if (StringUtils.isBlank(state.getLastTradeType())) {
                    // 没有卖出记录，使用最初金额
                    preAmount = BigDecimal.valueOf(state.getTradeAmount());
                } else {
                    // 上次卖出剩下的钱
                    preAmount = BigDecimal.valueOf(state.getLastTradeAmount());
                }
            } else {
                // 卖出：全仓卖出买入的数量
                if (state.getLastTradeQuantity() != null && state.getLastTradeQuantity() > 0) {
                    preQuantity = BigDecimal.valueOf(state.getLastTradeQuantity());
                } else {
//                        log.warn("卖出信号触发但没有持仓数量，跳过交易: strategyCode={}", state.getStrategyCode());
                    return;
                }
            }

            Order order = tradeController.createSpotOrder(
                    state.getSymbol(),
                    null,
                    side,
                    null,
                    preQuantity,
                    preAmount,
                    null, null, null, null,
                    false, state.getId()
            ).getData();

            if (order != null) {
                // 保存订单记录
                RealTimeOrderEntity orderEntity = realTimeOrderService.createOrderRecord(
                        state.getStrategyCode(),
                        state.getSymbol(),
                        order,
                        side + "_SIGNAL",
                        side,
                        candlestick.getClose().toString(),
                        false,
                        preAmount,
                        preQuantity,
                        singalTime);  // 打算买入金额，不是成交金额

                // 利润统计
                // 更新累计统计信息
                if (orderEntity.getSide().equals(SELL)) {
                    // executedAmount 已经是扣除手续费的金额，卖出的执行金额就是最后剩下的金额，减去上次卖出执行金额以及手续费就是利润
                    double profit = orderEntity.getExecutedAmount().doubleValue() - state.getLastTradeAmount() - state.getLastTradeFee();
//                            orderEntity.getFee().doubleValue() - state.getLastTradeFee();
                    state.setTotalProfit(state.getTotalProfit() + profit);
                    state.setTotalProfitRate(state.getTotalProfit() / state.getTradeAmount());
                    state.setLastTradeProfit(profit);
                    orderEntity.setProfit(BigDecimal.valueOf(profit));
                    orderEntity.setProfitRate(BigDecimal.valueOf(profit / (state.getLastTradeAmount() + state.getLastTradeFee())));
                }
                // 费用每次都有
                state.setTotalFees(state.getTotalFees() + orderEntity.getFee().doubleValue());
                // 更新策略状态
                state.setLastTradeType(orderEntity.getSide());
                // 买入时记录购买数量
                state.setLastTradeAmount(orderEntity.getExecutedAmount().doubleValue());
                state.setLastTradeQuantity(orderEntity.getExecutedQty().doubleValue());
                state.setLastTradePrice(orderEntity.getPrice().doubleValue());
                state.setLastTradeTime(orderEntity.getCreateTime());
                state.setLastTradeFee(orderEntity.getFee().doubleValue());
                state.setLastSingalTime(singalTime);
                if (BUY.equals(side)) {
                    state.setIsInPosition(true);
                } else {
                    state.setIsInPosition(false);
                }
                // 成交次数统计
                state.setTotalTrades(state.getTotalTrades() + 1);
                if (FILLED.equals(order.getStatus())) {
                    state.setSuccessfulTrades(state.getSuccessfulTrades() + 1);
                }
                // 更新数据库中的交易信息
                RealTimeStrategyEntity realTimeStrategy = realTimeStrategyService.updateTradeInfo(state);
                // 更新订单信息
                orderEntity.setStrategyId(realTimeStrategy.getId());
                realTimeOrderService.saveOrder(orderEntity);
                //更新交易控制标记,过期时间是本周期还剩的剩余的时间
//                long seconds = Duration.between(candlestick.getOpenTime().plus(
//                                historicalDataService.getIntervalMinutes(state.getInterval()), ChronoUnit.MINUTES),
//                        LocalDateTime.now()).abs().getSeconds();
//                redisTemplate.opsForValue().set(TRADE_FLAG + realTimeStrategy.getId(), String.valueOf(seconds), seconds, TimeUnit.SECONDS);

                log.info("执行{}订单成功: symbol={}, price={}, amount={}, quantity={}", side, state.getSymbol(), state.getLastTradePrice(),
                        state.getLastTradeAmount(), state.getLastTradeQuantity());

                // 发送交易通知
                try {
                    notificationService.sendTradeNotification(state, order, side, candlestick.getClose().toString());
                } catch (Exception e) {
                    log.error("发送交易通知失败: {}", e.getMessage(), e);
                }
            }
        } catch (Exception e) {
            runningStrategies.remove(state.getId());
            state.setIsActive(false);
            state.setStatus("ERROR");
            state.setEndTime(LocalDateTime.now());
            realTimeStrategyRepository.save(state);
            log.error("执行策略 {} {}订单失败，停止策略: {},", state.getStrategyName(), side, e.getMessage(), e);

            // 发送错误通知
            try {
                notificationService.sendStrategyErrorNotification(state, state.getMessage());
            } catch (Exception ex) {
                log.error("发送错误通知失败: {}", ex.getMessage(), ex);
            }
        }
//        }, executorService);
    }

    /**
     * 从Candlestick创建Bar
     */
    private Bar createBarFromCandlestick(Candlestick candlestick) {
        long intervalMinutes = historicalDataService.getIntervalMinutes(candlestick.getIntervalVal());

        // 计算endTime：如果closeTime为null，则根据openTime和interval计算
        LocalDateTime endTime;
        if (candlestick.getCloseTime() != null) {
            endTime = candlestick.getCloseTime();
        } else {
            // 根据openTime和interval计算closeTime
            endTime = calculateEndTimeFromInterval(candlestick.getOpenTime(), candlestick.getIntervalVal());
        }

        // 使用Ta4j 0.18版本的BaseBar构造函数
        return new BaseBar(
                Duration.ofMinutes(intervalMinutes),
                endTime.atZone(ZoneId.of("UTC+8")).toInstant(),
                DecimalNum.valueOf(candlestick.getOpen()),
                DecimalNum.valueOf(candlestick.getHigh()),
                DecimalNum.valueOf(candlestick.getLow()),
                DecimalNum.valueOf(candlestick.getClose()),
                DecimalNum.valueOf(candlestick.getVolume()),
                DecimalNum.valueOf(BigDecimal.ZERO), // 默认成交额为0
                0L // 添加交易次数参数，默认为0
        );
    }

    /**
     * 构建策略键
     */
    public String buildStrategyKey(String strategyCode, String symbol, String interval) {
        return strategyCode + "_" + symbol + "_" + interval;
    }

    /**
     * 程序启动时执行，从MySQL加载有效策略
     */
    @Override
    public void run(ApplicationArguments args) throws Exception {
        log.info("程序启动，开始加载有效的实时策略...");

        try {
            // 获取运行中的状态
            List<RealTimeStrategyEntity> strategies = realTimeStrategyService.getStrategiesToAutoStart();
            if (strategies.isEmpty()) {
                log.info("没有找到需要自动启动的策略");
                loadedStrategies = true;
                return;
            }
            log.info("找到 {} 个需要自动启动的策略", strategies.size());

            LocalDateTime now = LocalDateTime.now();
            for (RealTimeStrategyEntity strategyEntity : strategies) {
                try {
                    log.info("准备启动策略: strategyCode={}, symbol={}, interval={}",
                            strategyEntity.getStrategyCode(), strategyEntity.getSymbol(), strategyEntity.getInterval());
                    Map<String, Object> response = startExecuteRealTimeStrategy(strategyEntity);
                    String status = (String) response.get("status");
                    if (status.equals(SUCCESS)) {
                        log.info("策略启动成功: {}({})", strategyEntity.getStrategyName(), strategyEntity.getStrategyCode());
                    } else {
                        log.info("策略启动失败: {}({})", strategyEntity.getStrategyName(), strategyEntity.getStrategyCode());
                    }
                } catch (Exception e) {
                    log.error("启动策略失败: strategyCode={}, error={}",
                            strategyEntity.getStrategyCode(), e.getMessage(), e);
                }
            }
            loadedStrategies = true;
            log.info("完成加载 {} 个需要自动启动的策略", strategies.size());

        } catch (Exception e) {
            log.error("加载策略失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 保存策略到数据库
     */
    private void createAndSaveStrategy(RealTimeStrategyEntity state) {
        try {

            realTimeStrategyService.createRealTimeStrategy(state);

        } catch (Exception e) {
            log.error("保存策略到数据库失败: strategyCode={}, error={}", state.getStrategyCode(), e.getMessage(), e);
            throw e;
        }
    }


    /**
     * 获取所有运行中的策略
     */
    public Map<Long, RealTimeStrategyEntity> getAllRunningStrategies() {
        return new ConcurrentHashMap<>(runningStrategies);
    }

    public Map<String, Object> startExecuteRealTimeStrategy(RealTimeStrategyEntity strategyEntity) {
        Map<String, Object> response = new HashMap<>();

        response.put("strategyName", strategyEntity.getStrategyName());
        response.put("strategyCode", strategyEntity.getStrategyCode());
        response.put("symbol", strategyEntity.getSymbol());
        response.put("interval", strategyEntity.getInterval());
        response.put("tradeAmount", strategyEntity.getTradeAmount());

        response.put("startTime", strategyEntity.getStartTime());

        // 新增币种的barSeries
        String barSeriesKey = strategyEntity.getSymbol() + "_" + strategyEntity.getInterval();
        if (!runningBarSeries.containsKey(barSeriesKey)) {
            BarSeries barSeries = historicalDataService.fetchLastestedBars(strategyEntity.getSymbol(), strategyEntity.getInterval(), kLineNum);
            if (barSeries != null) {
                runningBarSeries.put(barSeriesKey, barSeries);
            }
        } else {
            response.put("message", "实时回测已经存在，跳过执行");
            response.put("status", CANCELED);
        }

        // 订阅K线数据
        // 如果启用了 Kafka（kline.kafka.enabled=true），则订阅 WebSocket 并写入 Kafka
        // 如果未启用 Kafka（kline.kafka.enabled=false），则不订阅 WebSocket，直接从 Kafka 消费（由 data-warehouse 提供）
        boolean kafkaEnabled = environment.getProperty("kline.kafka.enabled", Boolean.class, false);
        
        if (kafkaEnabled) {
            // 启用了 Kafka，需要订阅 WebSocket 并写入 Kafka
            try {
                webSocketService.subscribeKlineData(strategyEntity.getSymbol(), strategyEntity.getInterval());
                log.info("✓ 已订阅 WebSocket K线数据: symbol={}, interval={}", 
                        strategyEntity.getSymbol(), strategyEntity.getInterval());
            } catch (Exception e) {
                log.error("订阅K线数据失败: {}", e.getMessage(), e);
                response.put("message", "订阅K线数据失败");
                response.put("status", CANCELED);
                return response;
            }
        } else {
            // 未启用 Kafka，不订阅 WebSocket，数据由 data-warehouse 通过 Kafka 提供
            log.info("✓ Kafka 未启用，跳过 WebSocket 订阅，将从 Kafka 消费数据: symbol={}, interval={}", 
                    strategyEntity.getSymbol(), strategyEntity.getInterval());
        }

        // 根据strategyEntity创建具体的Strategy实例
        Strategy ta4jStrategy;
        try {
            ta4jStrategy = StrategyRegisterCenter.
                    createStrategy(runningBarSeries.get(strategyEntity.getSymbol() + "_" + strategyEntity.getInterval()), strategyEntity.getStrategyCode());
            strategyEntity = realTimeStrategyRepository.save(strategyEntity);
            strategyEntity.setStrategy(ta4jStrategy);
        } catch (Exception e) {
            log.error("获取策略失败: {}", e.getMessage(), e);
            response.put("message", "获取策略失败");
            response.put("status", CANCELED);
            return response;
        }

        // 添加到运行中策略列表
        runningStrategies.put(strategyEntity.getId(), strategyEntity);

        log.info("已添加策略: strategyCode={}, symbol={}, interval={}", strategyEntity.getStrategyCode(), strategyEntity.getSymbol(), strategyEntity.getInterval());
        response.put("id", strategyEntity.getId());
        response.put("message", "实时回测已经开始执行");
        response.put("status", SUCCESS);
        return response;

    }

    /**
     * 根据时间和间隔计算周期的开始时间
     *
     * @param dateTime 时间
     * @param interval K线间隔（如1m, 5m, 1H, 1D等）
     * @return 周期开始时间
     */
    private LocalDateTime getPeriodStartTime(LocalDateTime dateTime, String interval) {
        if (dateTime == null || interval == null) {
            return dateTime;
        }

        // 解析时间单位和数量
        String unit = interval.substring(interval.length() - 1);
        int amount;
        try {
            amount = Integer.parseInt(interval.substring(0, interval.length() - 1));
        } catch (NumberFormatException e) {
            amount = 1;
        }

        switch (unit) {
            case "m": // 分钟
                int minute = dateTime.getMinute();
                int periodMinute = (minute / amount) * amount;
                return dateTime.withMinute(periodMinute).withSecond(0).withNano(0);

            case "H": // 小时
                int hour = dateTime.getHour();
                int periodHour = (hour / amount) * amount;
                return dateTime.withHour(periodHour).withMinute(0).withSecond(0).withNano(0);

            case "D": // 天
                return dateTime.withHour(0).withMinute(0).withSecond(0).withNano(0);

            case "W": // 周
                // 计算本周的周一
                return dateTime.with(DayOfWeek.MONDAY).withHour(0).withMinute(0).withSecond(0).withNano(0);

            case "M": // 月
                return dateTime.withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0);

            default:
                return dateTime.withSecond(0).withNano(0);
        }
    }

    /**
     * 根据开盘时间和K线间隔计算收盘时间
     */
    private LocalDateTime calculateEndTimeFromInterval(LocalDateTime openTime, String interval) {
        if (openTime == null || interval == null) {
            return LocalDateTime.now();
        }

        // 解析时间单位和数量
        String unit = interval.substring(interval.length() - 1);
        int amount;
        try {
            amount = Integer.parseInt(interval.substring(0, interval.length() - 1));
        } catch (NumberFormatException e) {
            // 如果解析失败，使用默认值1
            amount = 1;
        }

        switch (unit) {
            case "m":
                return openTime.plusMinutes(amount);
            case "H":
                return openTime.plusHours(amount);
            case "D":
                return openTime.plusDays(amount);
            case "W":
                return openTime.plusWeeks(amount);
            case "M":
                return openTime.plusMonths(amount);
            default:
                return openTime.plusMinutes(1); // 默认1分钟
        }
    }

    /**
     * 构建最终结果
     */
    private Map<String, Object> buildFinalResult(RealTimeStrategyEntity state) {
        Map<String, Object> result = new ConcurrentHashMap<>();
        result.put("status", "COMPLETED");
        result.put("totalTrades", state.getTotalTrades());
        result.put("totalProfit", state.getTotalProfit());
        result.put("successfulTrades", state.getSuccessfulTrades());
        result.put("successRate", state.getTotalTrades() > 0 ?
                (double) state.getSuccessfulTrades() / state.getTotalTrades() : 0.0);
        return result;
    }
}
