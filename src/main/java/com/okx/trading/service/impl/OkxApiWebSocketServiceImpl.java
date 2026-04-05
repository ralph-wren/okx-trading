package com.okx.trading.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.okx.trading.config.OkxApiConfig;
import com.okx.trading.exception.BusinessException;
import com.okx.trading.exception.OkxApiException;
import com.okx.trading.model.account.AccountBalance;
import com.okx.trading.model.account.AccountBalance.AssetBalance;
import com.okx.trading.model.entity.RealTimeStrategyEntity;
import com.okx.trading.model.market.Candlestick;
import com.okx.trading.model.market.Ticker;
import com.okx.trading.model.trade.Order;
import com.okx.trading.model.trade.OrderRequest;
import com.okx.trading.service.*;
import com.okx.trading.strategy.RealTimeStrategyManager;
import com.okx.trading.util.BigDecimalUtil;
import com.okx.trading.util.HttpUtil;
import com.okx.trading.util.SignatureUtil;
import com.okx.trading.util.WebSocketUtil;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

import okhttp3.Request;
import okhttp3.Response;

import static com.okx.trading.constant.IndicatorInfo.BALANCE;
import static com.okx.trading.constant.IndicatorInfo.RUNNING;
import static com.okx.trading.service.impl.OkxApiRestServiceImpl.MARKET_PATH;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;


/**
 * OKX API WebSocket服务实现类
 * 通过WebSocket连接实现与OKX交易所的交互
 */
@Slf4j
@Service
@RequiredArgsConstructor
//@Data
@ConditionalOnProperty(
        name = "okx.api.connection-mode",
        havingValue = "WEBSOCKET",
        matchIfMissing = true
)
public class OkxApiWebSocketServiceImpl implements OkxApiService {

    private final OkxApiConfig okxApiConfig;
    private final WebSocketUtil webSocketUtil;
    private final RedisCacheService redisCacheService;
    private final OkHttpClient okHttpClient;
    @Lazy
    private final KlineCacheService klineCacheService;
    @Lazy
    private final RealTimeStrategyServiceImpl realTimeStrategyService;

    @Lazy
    @Autowired(required = false)
    private RealTimeStrategyManager realTimeStrategyManager;

    @Autowired
    private NotificationService emailNotificationService;

    @Autowired
    private KlineKafkaProducerService klineKafkaProducerService;

    // 缓存和回调
    private final Map<String, CompletableFuture<Ticker>> tickerFutures = new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<List<Candlestick>>> klineFutures = new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<AccountBalance>> balanceFutures = new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<List<Order>>> ordersFutures = new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<Order>> orderFutures = new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<Boolean>> cancelOrderFutures = new ConcurrentHashMap<>();

    // 跟踪当前已订阅的币种+周期
    private final Set<String> subscribedSymbols = Collections.synchronizedSet(new HashSet<>());

    // 消息ID生成
    private final AtomicLong messageIdGenerator = new AtomicLong(1);
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    // instId -> instIdCode 映射缓存 (根据 OKX 2026-03-26 更新，WebSocket 订单需要使用 instIdCode)
    private final Map<String, Integer> instIdCodeCache = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        // 注册消息处理器
        webSocketUtil.registerHandler("tickers", this::handleTickerMessage);

        // 注册标准K线处理器
        webSocketUtil.registerHandler("candle1m", this::handleKlineMessage);
        webSocketUtil.registerHandler("candle5m", this::handleKlineMessage);
        webSocketUtil.registerHandler("candle15m", this::handleKlineMessage);
        webSocketUtil.registerHandler("candle30m", this::handleKlineMessage);
        webSocketUtil.registerHandler("candle1H", this::handleKlineMessage);
        webSocketUtil.registerHandler("candle2H", this::handleKlineMessage);
        webSocketUtil.registerHandler("candle4H", this::handleKlineMessage);
        webSocketUtil.registerHandler("candle6H", this::handleKlineMessage);
        webSocketUtil.registerHandler("candle12H", this::handleKlineMessage);
        webSocketUtil.registerHandler("candle1D", this::handleKlineMessage);
        webSocketUtil.registerHandler("candle1W", this::handleKlineMessage);
        webSocketUtil.registerHandler("candle1M", this::handleKlineMessage);
        webSocketUtil.registerHandler("candle3M", this::handleKlineMessage);

        // 注册标记价格K线处理器
        webSocketUtil.registerHandler("mark-price", this::handleTickerMessage);

        webSocketUtil.registerHandler("account", this::handleAccountMessage);
        webSocketUtil.registerHandler("orders", this::handleOrdersMessage);
        webSocketUtil.registerHandler("order", this::handleOrderMessage);
    }

    /**
     * 获取交易对的 instIdCode
     * 根据 OKX 2026-03-26 更新，WebSocket 订单 API 需要使用 instIdCode 而不是 instId
     * 
     * @param instId 交易对标识，如 BTC-USDT
     * @param instType 产品类型，如 SPOT, SWAP 等
     * @return instIdCode 整数值
     */
    private Integer getInstIdCode(String instId, String instType) {
        // 先从缓存中查找
        if (instIdCodeCache.containsKey(instId)) {
            return instIdCodeCache.get(instId);
        }

        // 缓存中没有，调用 API 获取
        try {
            // 使用公共 API 端点获取产品信息
            String apiUrl = okxApiConfig.getBaseUrl() + "/api/v5/public/instruments?instType=" + instType + "&instId=" + instId;
            
            log.info("调用 instruments API: {}", apiUrl);
            
            // 公共 API 不需要签名
            Request request = new Request.Builder()
                    .url(apiUrl)
                    .addHeader("Content-Type", "application/json")
                    .get()
                    .build();

            try (Response response = okHttpClient.newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    String responseBody = response.body().string();
                    log.info("获取 instruments 响应: {}", responseBody);

                    JSONObject responseJson = JSONObject.parseObject(responseBody);
                    if ("0".equals(responseJson.getString("code"))) {
                        JSONArray data = responseJson.getJSONArray("data");
                        if (data != null && !data.isEmpty()) {
                            JSONObject instrument = data.getJSONObject(0);
                            Integer instIdCode = instrument.getInteger("instIdCode");
                            if (instIdCode != null) {
                                // 缓存结果
                                instIdCodeCache.put(instId, instIdCode);
                                log.info("成功获取并缓存 instIdCode: {} -> {}", instId, instIdCode);
                                return instIdCode;
                            } else {
                                log.warn("响应中没有 instIdCode 字段: {}", instrument.toJSONString());
                            }
                        } else {
                            log.warn("API 返回空数据，可能交易对不存在: instId={}, instType={}", instId, instType);
                        }
                    } else {
                        log.error("获取 instIdCode 失败，错误码: {}, 错误信息: {}", 
                                responseJson.getString("code"), responseJson.getString("msg"));
                    }
                } else {
                    log.error("API 请求失败，HTTP 状态码: {}, 响应: {}", 
                            response.code(), response.body() != null ? response.body().string() : "null");
                }
            }
        } catch (Exception e) {
            log.error("获取 instIdCode 异常: instId={}, 错误: {}", instId, e.getMessage(), e);
        }

        return null;
    }

    /**
     * 处理Ticker消息
     */
    private void handleTickerMessage(JSONObject message) {
        try {
            String channel = message.getJSONObject("arg").getString("channel");
            String symbol = message.getJSONObject("arg").getString("instId");
            JSONArray data = message.getJSONArray("data");
            if (data != null && !data.isEmpty()) {
                JSONObject tickerData = data.getJSONObject(0);
                Ticker ticker = parseTicker(tickerData, symbol, channel);

                log.debug("获取实时指数行情信息: {}", ticker);

                // 将最新价格写入Redis缓存
                BigDecimal lastPrice = ticker.getLastPrice();
                if (lastPrice != null) {
//                    redisCacheService.updateCoinPrice(symbol, lastPrice);
                    // 更新邮件通知服务的最新价格
                    emailNotificationService.updateLatestPrice(symbol, lastPrice);
                }


                CompletableFuture<Ticker> future = tickerFutures.get(channel + "_" + symbol);
                if (future != null && !future.isDone()) {
                    future.complete(ticker);
                }
            }
        } catch (Exception e) {
            log.error("处理Ticker消息失败", e);
        }
    }

    /**
     * 处理K线消息,实时行情消息,都是标记价格
     */
    private void handleKlineMessage(JSONObject message) {
        try {
            if (!message.containsKey("arg") || !message.containsKey("data")) {
                log.debug("忽略不包含必要字段的K线消息: {}", message);
                return;
            }

            JSONObject arg = message.getJSONObject("arg");
            String symbol = arg.getString("instId");
            String channel = arg.getString("channel");

            // 从bar参数获取interval
            String interval = channel.replaceAll("candle", "");
            // 构建缓存键 - 确保与getKlineData和unsubscribeKlineData方法使用相同的键格式
            String key = channel + "_" + symbol + "_" + interval;

            // 获取数据并解析
            List<Candlestick> candlesticks = new ArrayList<>();

            // 处理data字段 - 不同类型
            Object dataObj = message.get("data");

            // 数组格式 - 可能是数组的数组或对象的数组
            JSONArray dataArray = (JSONArray) dataObj;
            for (int i = 0; i < dataArray.size(); i++) {
                Object item = dataArray.get(i);
                Candlestick candlestick = null;

                // 标准K线格式：数组的数组
                JSONArray candleData = (JSONArray) item;
                candlestick = parseCandlestick(candleData, symbol, channel);


                if (candlestick != null) {
                    candlestick.setIntervalVal(interval);

                    // 检查是否启用 Kafka 缓冲
                    if (klineKafkaProducerService.isEnabled()) {
                        // 启用 Kafka：将原始数据发送到 Kafka，由消费者处理
                        JSONObject klineDataJson = new JSONObject();
                        klineDataJson.put("ts", candleData.getLongValue(0));
                        klineDataJson.put("o", candleData.getString(1));
                        klineDataJson.put("h", candleData.getString(2));
                        klineDataJson.put("l", candleData.getString(3));
                        klineDataJson.put("c", candleData.getString(4));
                        klineDataJson.put("vol", candleData.getString(5));
                        
                        klineKafkaProducerService.sendKlineData(symbol, interval, klineDataJson);
                        log.debug("📤 K线数据已发送到 Kafka: symbol={}, interval={}", symbol, interval);
                    } else {
                        // 未启用 Kafka：直接处理（原有逻辑）
                        // 更新邮件通知服务的最新价格
                        emailNotificationService.updateLatestPrice(symbol, candlestick.getClose());

                        log.debug("获取实时标记价格k线数据: {}", candlestick);

                        // 通知实时策略管理器处理新的K线数据
                        if (realTimeStrategyManager != null) {
                            realTimeStrategyManager.handleNewKlineData(symbol, interval, candlestick);
                        }
                    }
                }
            }
            // 如果解析到了数据，完成等待中的Future
            if (!candlesticks.isEmpty()) {
                CompletableFuture<List<Candlestick>> future = klineFutures.get(key);
                if (future != null && !future.isDone()) {
                    log.debug("完成K线数据Future，符号: {}, 间隔: {}, 数据量: {}", symbol, interval, candlesticks.size());
                    future.complete(candlesticks);
                }
            }
        } catch (Exception e) {
            log.error("处理K线消息失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 从JSONObject解析K线数据
     * 用于处理非标准格式的K线数据
     */
    private Candlestick parseCandlestickFromObject(JSONObject candleObj, String symbol, String channel) {
        try {
            Candlestick candlestick = new Candlestick();
            candlestick.setSymbol(symbol);
            candlestick.setChannel(channel);

            // 解析时间戳
            if (candleObj.containsKey("ts")) {
                long timestamp = candleObj.getLongValue("ts");
                LocalDateTime time = LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.of("UTC+8"));
                candlestick.setOpenTime(time);
            }

            // 解析标记价格K线特有字段
            if (candleObj.containsKey("markPx")) {
                BigDecimal markPrice = BigDecimalUtil.safeGen(candleObj.getString("markPx"));
                candlestick.setOpen(markPrice);
                candlestick.setHigh(markPrice);
                candlestick.setLow(markPrice);
                candlestick.setClose(markPrice);
                // 标记价格K线可能没有交易量
                candlestick.setVolume(BigDecimal.ZERO);
                return candlestick;
            }

            // 解析标准K线字段
            if (candleObj.containsKey("o")) {
                candlestick.setOpen(BigDecimalUtil.safeGen(candleObj.getString("o")));
            }
            if (candleObj.containsKey("h")) {
                candlestick.setHigh(BigDecimalUtil.safeGen(candleObj.getString("h")));
            }
            if (candleObj.containsKey("l")) {
                candlestick.setLow(BigDecimalUtil.safeGen(candleObj.getString("l")));
            }
            if (candleObj.containsKey("c")) {
                candlestick.setClose(BigDecimalUtil.safeGen(candleObj.getString("c")));
            }
            if (candleObj.containsKey("vol")) {
                candlestick.setVolume(BigDecimalUtil.safeGen(candleObj.getString("vol")));
            }

            return candlestick;
        } catch (Exception e) {
            log.error("解析K线对象失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 处理账户消息
     */
    private void handleAccountMessage(JSONObject message) {
        try {
            if (!message.containsKey("data")) {
                return;
            }
            JSONArray data = message.getJSONArray("data");
            if (data != null && !data.isEmpty()) {
                JSONObject balanceData = data.getJSONObject(0);
                AccountBalance accountBalance = parseAccountBalance(balanceData);

                // 将余额信息转换为Map并存入Redis
                if (accountBalance.getAssetBalances() != null) {
                    for (AccountBalance.AssetBalance assetBalance : accountBalance.getAssetBalances()) {
                        redisTemplate.opsForHash().put(BALANCE, assetBalance.getAsset(), assetBalance.getAvailable().toString());
                        redisTemplate.expire(BALANCE, 10, TimeUnit.MINUTES);
                    }
                }

                String key = message.getJSONObject("arg").containsKey("simulated") ? "simulated" : "real";
                CompletableFuture<AccountBalance> future = balanceFutures.get(key);
                if (future != null && !future.isDone()) {
                    future.complete(accountBalance);
                }
            }
        } catch (Exception e) {
            log.error("处理账户消息失败", e);
        }
    }

    /**
     * 处理订单列表消息
     */
    private void handleOrdersMessage(JSONObject message) {
        try {
            String symbol = message.getJSONObject("arg").getString("instId");
            JSONArray data = message.getJSONArray("data");
            if (data != null) {
                List<Order> orders = new ArrayList<>();
                for (int i = 0; i < data.size(); i++) {
                    JSONObject orderData = data.getJSONObject(i);
                    Order order = parseOrder(orderData);
                    orders.add(order);
                }

                String key = symbol + "_orders";
                CompletableFuture<List<Order>> future = ordersFutures.get(key);
                if (future != null && !future.isDone()) {
                    future.complete(orders);
                }
            }
        } catch (Exception e) {
            log.error("处理订单列表消息失败", e);
        }
    }

    /**
     * 处理订单消息
     */
    private void handleOrderMessage(JSONObject message) {
        try {
            JSONArray data = message.getJSONArray("data");
            if (data != null && !data.isEmpty()) {
                JSONObject orderData = data.getJSONObject(0);
                Order order = parseOrder(orderData);

                // 使用clOrdId查找对应的future，而不是ordId
                String clientOrderId = orderData.getString("clOrdId");
                String sMsg = orderData.getString("sMsg");

                log.info("收到订单消息: orderId={}, clientOrderId={}, status={}, sMsg={}",
                        orderData.getString("ordId"), clientOrderId, orderData.getString("state"), sMsg);


                if (order.getSCode() != 0) {
                    // 如果sMsg不为空，将内容保存到对应策略的mysql表里
                    if (StringUtils.isNotBlank(sMsg)) {
                        Long strategyId = realTimeStrategyManager.getClientOrderId2StrategyIdMap().get(clientOrderId);
                        Optional<RealTimeStrategyEntity> realTimeStrategyById = realTimeStrategyService.getRealTimeStrategyById(strategyId);
                        if (realTimeStrategyById.isPresent()) {
                            RealTimeStrategyEntity realTimeStrategy = realTimeStrategyById.get();
                            realTimeStrategy.setMessage(sMsg);
                            // 同时将错误信息更新到内存和mysql
                            realTimeStrategy.setStatus("ERROR");
                            realTimeStrategy.setIsActive(false);
                            realTimeStrategyService.saveRealTimeStrategy(realTimeStrategy);
                            realTimeStrategyManager.getRunningStrategies().remove(realTimeStrategy.getId());
                        }
                    }
//                    throw new BusinessException(order.getSCode(), order.getClientOrderId() + ": " + order.getSMsg());
                }

                CompletableFuture<Order> future = orderFutures.get(clientOrderId);
                if (future != null && !future.isDone()) {
                    future.complete(order);
                }

                // 处理取消订单的响应
                if ("canceled".equals(orderData.getString("state"))) {
                    String orderId = orderData.getString("ordId");
                    CompletableFuture<Boolean> cancelFuture = cancelOrderFutures.get(orderId);
                    if (cancelFuture != null && !cancelFuture.isDone()) {
                        cancelFuture.complete(true);
                    }
                }


            }
        } catch (Exception e) {
            log.error("处理订单消息失败", e);
        }
    }

    @Override
    public List<Candlestick> getKlineData(String symbol, String interval, Integer limit) {
        try {
            // 构建标记价格K线的正确频道名和参数
            String channel = "candle" + interval;
            // 确保键格式统一
            String key = channel + "_" + symbol + "_" + interval;

            CompletableFuture<List<Candlestick>> future = new CompletableFuture<>();
            klineFutures.put(key, future);

            // 创建完整的WebSocket参数对象
            JSONObject arg = new JSONObject();
            arg.put("channel", channel);
            arg.put("instId", symbol);

            log.debug("订阅标记价格K线数据，符号: {}, 间隔: {}", symbol, interval);
            webSocketUtil.subscribePublicTopicWithArgs(arg, symbol);

            // 获取K线数据通常需要更长时间，使用配置值的1.5倍
            int klineTimeout = (int) (okxApiConfig.getTimeout() * 1.5);
            klineTimeout = Math.max(15, klineTimeout); // 至少15秒

            try {
                List<Candlestick> candlesticks = future.get(klineTimeout, TimeUnit.SECONDS);
                klineFutures.remove(key);

                // 添加间隔信息
                candlesticks.forEach(c -> c.setIntervalVal(interval));

                // 限制返回数量
                int size = limit != null && limit > 0 ? Math.min(limit, candlesticks.size()) : candlesticks.size();
                return candlesticks.subList(0, size);
            } catch (TimeoutException e) {
                log.error("获取K线数据超时，符号: {}, 间隔: {}, 超时时间: {}秒", symbol, interval, klineTimeout);
                throw new OkxApiException("获取K线数据超时，请稍后重试", e);
            }
        } catch (Exception e) {
            log.error("获取K线数据失败: {}", e.getMessage(), e);
            throw new OkxApiException("获取K线数据失败: " + e.getMessage(), e);
        }
    }

    @Override
    public Ticker getTicker(String symbol){
        try{
            String url = okxApiConfig.getBaseUrl() + MARKET_PATH + "/ticker";
            url = url + "?instId=" + symbol;

            String response = HttpUtil.get(okHttpClient, url, null);
            JSONObject jsonResponse = JSON.parseObject(response);

            if(! "0".equals(jsonResponse.getString("code"))){
                throw new OkxApiException(jsonResponse.getIntValue("code"), jsonResponse.getString("msg"));
            }

            JSONObject data = jsonResponse.getJSONArray("data").getJSONObject(0);

            Ticker ticker = new Ticker();
            ticker.setSymbol(symbol);
            ticker.setLastPrice(BigDecimalUtil.safeGen(data.getString("last")));
            // 计算24小时价格变动
            BigDecimal open24h = BigDecimalUtil.safeGen(data.getString("open24h"));
            BigDecimal priceChange = ticker.getLastPrice().subtract(open24h);
            ticker.setPriceChange(priceChange);
            // 将最新价格写入Redis缓存
            BigDecimal lastPrice = ticker.getLastPrice();
            if(lastPrice != null){
                redisCacheService.updateCoinPrice(symbol, lastPrice);
            }
            // 计算24小时价格变动百分比
            if(open24h.compareTo(BigDecimal.ZERO) > 0){
                BigDecimal changePercent = priceChange.multiply(BigDecimalUtil.safeGen("100")).divide(open24h, 2, BigDecimal.ROUND_HALF_UP);
                ticker.setPriceChangePercent(changePercent);
            }else{
                ticker.setPriceChangePercent(BigDecimal.ZERO);
            }

            ticker.setHighPrice(BigDecimalUtil.safeGen(data.getString("high24h")));
            ticker.setLowPrice(BigDecimalUtil.safeGen(data.getString("low24h")));
            ticker.setVolume(BigDecimalUtil.safeGen(data.getString("vol24h")));
            ticker.setQuoteVolume(BigDecimalUtil.safeGen(data.getString("volCcy24h")));

            ticker.setBidPrice(BigDecimalUtil.safeGen(data.getString("bidPx")));
            ticker.setBidQty(BigDecimalUtil.safeGen(data.getString("bidSz")));
            ticker.setAskPrice(BigDecimalUtil.safeGen(data.getString("askPx")));
            ticker.setAskQty(BigDecimalUtil.safeGen(data.getString("askSz")));

            // 转换时间戳为LocalDateTime
            long timestamp = data.getLongValue("ts");
            ticker.setTimestamp(LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(timestamp),
                    ZoneId.of("UTC+8")));

            return ticker;
        }catch(OkxApiException e){
            throw e;
        }catch(Exception e){
            log.error("获取行情数据异常", e);
            throw new OkxApiException("获取行情数据失败: " + e.getMessage(), e);
        }
    }


//    @Override
//    public Ticker getTicker(String symbol) {
//        try {
//            // 检查是否已订阅，避免重复订阅
//            if (subscribedSymbols.contains(symbol)) {
//                log.debug("币种 {} 已经订阅，跳过重复订阅", symbol);
//                // 从Redis获取最新价格
//                BigDecimal price = redisCacheService.getCoinPrice(symbol);
//                if (price != null) {
//                    Ticker ticker = new Ticker();
//                    ticker.setSymbol(symbol);
//                    ticker.setLastPrice(price);
//                    ticker.setTimestamp(LocalDateTime.now());
//                    return ticker;
//                }
//
//                // 如果Redis中没有价格，可能是连接重置后未收到新的价格更新
//                // 主动重新订阅以获取最新数据
//                log.info("币种 {} 已订阅但Redis中无价格数据，重新触发订阅", symbol);
//            }
//
//            String channel = "tickers";
//            String key = channel + "_" + symbol;
//
//            CompletableFuture<Ticker> future = new CompletableFuture<>();
//            tickerFutures.put(key, future);
//
//            log.info("订阅币种 {} 行情数据", symbol);
//            webSocketUtil.subscribePublicTopic(channel, symbol);
//
//            // 标记为已订阅
//            subscribedSymbols.add(symbol);
//
//            // 获取配置的超时时间，默认为10秒
//            int timeout = okxApiConfig.getTimeout() > 0 ? okxApiConfig.getTimeout() : 10;
//
//            // 添加重试逻辑
//            Ticker ticker = null;
//            int retryCount = 0;
//            int maxRetries = 3;
//
//            while (ticker == null && retryCount < maxRetries) {
//                try {
//                    // 等待获取数据，使用配置中的超时时间
//                    ticker = future.get(timeout, TimeUnit.SECONDS);
//                } catch (TimeoutException e) {
//                    retryCount++;
//                    if (retryCount >= maxRetries) {
//                        log.error("获取行情数据超时，已重试{}次", retryCount);
//                        throw e;
//                    }
//                    log.warn("获取行情数据超时，正在进行第{}次重试", retryCount);
//                    // 重新订阅以触发新的数据
//                    webSocketUtil.subscribePublicTopic(channel, symbol);
//                }
//            }
//
//            tickerFutures.remove(key);
//            return ticker;
//        } catch (Exception e) {
//            log.error("获取行情数据失败", e);
//            throw new OkxApiException("获取行情数据失败: " + e.getMessage(), e);
//        }
//    }

    @Override
    public AccountBalance getAccountBalance() {
        try {
            AccountBalance accountBalance = new AccountBalance();
            String balance = (String) redisTemplate.opsForHash().get(BALANCE, "USDT");
            if (StringUtils.isNotBlank(balance)) {
                accountBalance.setAvailableBalance(BigDecimal.valueOf(Double.valueOf(balance)));
                return accountBalance;
            }

//            CompletableFuture<AccountBalance> future = new CompletableFuture<>();
//            balanceFutures.put("real", future);
//
//            webSocketUtil.subscribePrivateTopic("account");
//
//            // 获取配置的超时时间
//            accountBalance = future.get(okxApiConfig.getTimeout(), TimeUnit.SECONDS);
//            balanceFutures.remove("real");

            return accountBalance;
        } catch (Exception e) {
            log.error("获取账户余额失败", e);
            throw new OkxApiException("获取账户余额失败: " + e.getMessage(), e);
        }
    }

    @Override
    public AccountBalance getSimulatedAccountBalance() {
        try {
            CompletableFuture<AccountBalance> future = new CompletableFuture<>();
            balanceFutures.put("simulated", future);

            // 向服务器发送请求获取模拟账户信息
            JSONObject requestMessage = new JSONObject();
            requestMessage.put("id", messageIdGenerator.getAndIncrement());
            requestMessage.put("op", "request");

            JSONObject arg = new JSONObject();
            arg.put("channel", "account");
            arg.put("simulated", "1");

            JSONObject[] args = new JSONObject[]{arg};
            requestMessage.put("args", args);

            // 发送请求
            webSocketUtil.sendPrivateRequest(requestMessage.toJSONString());

            // 获取配置的超时时间
            int timeout = okxApiConfig.getTimeout() > 0 ? okxApiConfig.getTimeout() : 10;
            AccountBalance accountBalance = future.get(timeout, TimeUnit.SECONDS);
            balanceFutures.remove("simulated");

            return accountBalance;
        } catch (Exception e) {
            log.error("获取模拟账户余额失败", e);
            throw new OkxApiException("获取模拟账户余额失败: " + e.getMessage(), e);
        }
    }

    @Override
    public List<Order> getOrders(String symbol, String status, Integer limit) {
        try {
            String key = symbol + "_orders";
            CompletableFuture<List<Order>> future = new CompletableFuture<>();
            ordersFutures.put(key, future);

            // 向服务器发送请求获取订单列表
            JSONObject requestMessage = new JSONObject();
            requestMessage.put("id", messageIdGenerator.getAndIncrement());
            requestMessage.put("op", "subscribe");

            JSONObject arg = new JSONObject();
            arg.put("channel", "orders");
            arg.put("instId", symbol);
            if (status != null && !status.isEmpty()) {
                arg.put("state", mapToOkxOrderStatus(status));
            }
            if (limit != null && limit > 0) {
                arg.put("limit", limit.toString());
            }

            JSONObject[] args = new JSONObject[]{arg};
            requestMessage.put("args", args);

            // 发送请求
            webSocketUtil.sendPrivateRequest(requestMessage.toJSONString());

            // 获取配置的超时时间
            int timeout = okxApiConfig.getTimeout() > 0 ? okxApiConfig.getTimeout() : 10;
            List<Order> orders = future.get(timeout, TimeUnit.SECONDS);
            ordersFutures.remove(key);

            return orders;
        } catch (Exception e) {
            log.error("获取订单列表失败", e);
            throw new OkxApiException("获取订单列表失败: " + e.getMessage(), e);
        }
    }

    @Override
    public Order createSpotOrder(OrderRequest orderRequest) {
        return createOrder(orderRequest, "SPOT", orderRequest.getSimulated() != null && orderRequest.getSimulated());
    }

    @Override
    public Order createFuturesOrder(OrderRequest orderRequest) {
        return createOrder(orderRequest, "SWAP", orderRequest.getSimulated() != null && orderRequest.getSimulated());
    }

    /**
     * 创建订单
     */
    private Order createOrder(OrderRequest orderRequest, String instType, boolean isSimulated) {
        // 生成订单ID
        String orderId = UUID.randomUUID().toString();
        String clientOrderId = orderRequest.getClientOrderId() != null ?
                orderRequest.getClientOrderId() : System.currentTimeMillis() + orderId.substring(0, 8);

        try {
            realTimeStrategyManager.getClientOrderId2StrategyIdMap().put(clientOrderId, orderRequest.getStrategyId());
            CompletableFuture<Order> future = new CompletableFuture<>();
            // 将future与clientOrderId关联，而不是orderId
            orderFutures.put(clientOrderId, future);

            log.info("准备创建订单, symbol: {}, type: {}, side: {}, clientOrderId: {}",
                    orderRequest.getSymbol(), orderRequest.getType(), orderRequest.getSide(), clientOrderId);

            // 构建订单请求
            JSONObject requestMessage = new JSONObject();
            requestMessage.put("id", messageIdGenerator.getAndIncrement());
            requestMessage.put("op", "order");

            JSONObject arg = new JSONObject();
            
            // 根据 OKX 2026-03-26 更新：WebSocket 订单需要使用 instIdCode 而不是 instId
            // Phase 1 (2026-03-26): WS Place order 和 Place multiple orders 已废弃 instId
            // Phase 2 (2026-04-07): WS Amend/Cancel order 将废弃 instId
            Integer instIdCode = getInstIdCode(orderRequest.getSymbol(), instType);
            if (instIdCode == null) {
                log.error("无法获取 instIdCode: symbol={}, instType={}", orderRequest.getSymbol(), instType);
                throw new OkxApiException("无法获取交易对的 instIdCode，请检查交易对是否有效");
            }
            
            arg.put("instIdCode", instIdCode);
            arg.put("tdMode", "cash"); // 资金模式，cash为现钞
            arg.put("side", orderRequest.getSide().toLowerCase());
            if (orderRequest.getType() != null) {
                arg.put("ordType", mapToOkxOrderType(orderRequest.getType())); // MARKET LIMIT
            } else {
                arg.put("ordType", "market");
            }


            //币币市价单委托数量sz的单位,base_ccy: 交易货币 ；quote_ccy：计价货币,仅适用于币币市价订单,默认买单为quote_ccy，卖单为base_ccy
            if (orderRequest.getAmount() != null) {
                // 市价\限价,指定金额
                arg.put("sz", orderRequest.getAmount().toString());
                arg.put("tgtCcy", "quote_ccy");
            } else if (orderRequest.getQuantity() != null) {
                //指定数量,市价单不指定价格,限价单指定价格
                arg.put("sz", orderRequest.getQuantity().toString());
                arg.put("tgtCcy", "base_ccy");
                // 限价单指定价格
                if (orderRequest.getType() != null && orderRequest.getType().equals("LIMIT")) {
                    if (orderRequest.getPrice() != null) {
                        arg.put("px", orderRequest.getPrice().toString());
                    } else {
                        BigDecimal coinPrice = redisCacheService.getCoinPrice(orderRequest.getSymbol());
                        arg.put("px", coinPrice.toString());
                    }
                }
            }

            // 设置客户端订单ID
            arg.put("clOrdId", clientOrderId);

            // 设置杠杆倍数（合约交易）
            if ("SWAP".equals(instType) && orderRequest.getLeverage() != null) {
                arg.put("lever", orderRequest.getLeverage().toString());
            }

            // 设置模拟交易
            if (isSimulated) {
                arg.put("simulated", "1");
            }

            JSONObject[] args = new JSONObject[]{arg};
            requestMessage.put("args", args);

            // 记录发送的订单请求
            log.info("发送订单请求: {}", requestMessage.toJSONString());

            // 检查WebSocket连接状态
            if (!webSocketUtil.isPrivateSocketConnected()) {
                log.error("私有WebSocket未连接，无法发送订单请求");
                throw new OkxApiException("WebSocket连接已断开，请重新连接后再尝试");
            }
            // 发送请求
            webSocketUtil.sendPrivateRequest(requestMessage.toJSONString());
            Thread.sleep(1000);
        } catch (Exception e) {
            log.error("创建订单失败: {}", e.getMessage(), e);
        }
        // 增加订单超时处理
        Order order = null;
        // 使用正确的API接口：直接使用order接口按clientOrderId查询单个订单
        // 构建API请求路径
        StringBuilder apiUrlBuilder = new StringBuilder(okxApiConfig.getBaseUrl())
                .append("/api/v5/trade/order?instId=")
                .append(orderRequest.getSymbol())
                .append("&clOrdId=")
                .append(clientOrderId);

        // 记录API请求路径
        log.info("查询订单API URL: {}", apiUrlBuilder.toString());

        // 确保时间戳精确
        String timestamp = SignatureUtil.getIsoTimestamp();

        // 构建请求路径（不含baseUrl，用于签名）
        String requestPath = "/api/v5/trade/order?instId="
                + orderRequest.getSymbol() + "&clOrdId=" + clientOrderId;

        // 生成签名
        String sign = SignatureUtil.sign(timestamp, "GET", requestPath, "", okxApiConfig.getSecretKey());

        Request.Builder requestBuilder = new Request.Builder()
                .url(apiUrlBuilder.toString())
                .addHeader("Content-Type", "application/json")
                .addHeader("OK-ACCESS-KEY", okxApiConfig.getApiKey())
                .addHeader("OK-ACCESS-SIGN", sign)
                .addHeader("OK-ACCESS-TIMESTAMP", timestamp)
                .addHeader("OK-ACCESS-PASSPHRASE", okxApiConfig.getPassphrase());

        // 如果是模拟交易需要额外添加标志
        if (isSimulated) {
            requestBuilder.addHeader("x-simulated-trading", "1");
        }

        Request request = requestBuilder.get().build();
        try (Response response = okHttpClient.newCall(request).execute();) {
            if (response.isSuccessful() && response.body() != null) {
                String responseBody = response.body().string();
                log.info("REST API查询订单响应: {}", responseBody);

                // 将响应数据写入CSV文件
                appendOrderResponseToCsv(responseBody, orderRequest.getSymbol(), clientOrderId);

                JSONObject responseJson = JSONObject.parseObject(responseBody);
                if ("0".equals(responseJson.getString("code"))) {
                    JSONArray data = responseJson.getJSONArray("data");
                    if (data != null && !data.isEmpty()) {
                        // 直接解析第一个订单，因为是按clientOrderId精确查询的
                        JSONObject orderData = data.getJSONObject(0);
                        order = parseOrder(orderData);
                        log.info("通过REST API查询到订单: clientOrderId={}, orderId={}, status={}",
                                clientOrderId, order.getOrderId(), order.getStatus());
                    }
                }
            }
        } catch (Exception e) {
            log.error("订单请求异常, symbol: {}, type: {}, side: {}, clientOrderId: {}, 错误: {}",
                    orderRequest.getSymbol(), orderRequest.getType(), orderRequest.getSide(),
                    clientOrderId, e.getMessage(), e);
            throw new OkxApiException("订单请求异常: " + e.getMessage(), e);
        } finally {
            // 清理资源
            orderFutures.remove(clientOrderId);
        }
        return order;
    }

    /**
     * 将订单响应数据追加到CSV文件
     * @param responseBody 响应数据JSON字符串
     * @param symbol 交易对
     * @param clientOrderId 客户端订单ID
     */
    private void appendOrderResponseToCsv(String responseBody, String symbol, String clientOrderId) {
        try {
            // 确保日志目录存在
            String logDir = "logs/orders";
            Files.createDirectories(Paths.get(logDir));

            // 创建CSV文件名，使用当前日期
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
            String fileName = logDir + "/orders_" + dateFormat.format(new Date()) + ".csv";
            File file = new File(fileName);

            // 如果文件不存在，创建文件并写入表头
            boolean newFile = !file.exists();

            // 解析JSON响应数据
            JSONObject responseJson = JSONObject.parseObject(responseBody);
            if (!"0".equals(responseJson.getString("code"))) {
                log.error("订单查询返回错误码: {}", responseJson.getString("code"));
                return;
            }

            JSONArray data = responseJson.getJSONArray("data");
            if (data == null || data.isEmpty()) {
                log.error("订单查询无数据返回");
                return;
            }

            JSONObject orderData = data.getJSONObject(0);

            // 获取所有字段作为schema
            Set<String> fields = orderData.keySet();
            List<String> fieldNames = new ArrayList<>(fields);
            // 排序字段，确保schema顺序一致
            Collections.sort(fieldNames);

            try (FileWriter writer = new FileWriter(file, true)) {
                // 如果是新文件，写入schema
                if (newFile) {
                    writer.write(String.join(",", fieldNames) + "\n");
                }

                // 按照schema顺序写入数据
                StringBuilder line = new StringBuilder();
                for (int i = 0; i < fieldNames.size(); i++) {
                    String fieldName = fieldNames.get(i);
                    String value = orderData.getString(fieldName);

                    // 处理值中可能包含的逗号和引号
                    if (value != null) {
                        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
                            value = "\"" + value.replace("\"", "\"\"") + "\"";
                        }
                    } else {
                        value = "";
                    }

                    line.append(value);
                    if (i < fieldNames.size() - 1) {
                        line.append(",");
                    }
                }
                line.append("\n");

                // 写入数据
                writer.write(line.toString());

                log.info("订单响应数据已按schema追加到CSV文件: {}", fileName);
            }
        } catch (IOException e) {
            log.error("写入订单响应数据到CSV文件失败: {}", e.getMessage(), e);
        } catch (Exception e) {
            log.error("解析订单响应数据失败: {}", e.getMessage(), e);
        }
    }

    @Override
    public boolean cancelOrder(String symbol, String orderId) {
        try {
            CompletableFuture<Boolean> future = new CompletableFuture<>();
            cancelOrderFutures.put(orderId, future);

            // 构建取消订单请求
            JSONObject requestMessage = new JSONObject();
            requestMessage.put("id", messageIdGenerator.getAndIncrement());
            requestMessage.put("op", "cancel-order");

            JSONObject arg = new JSONObject();
            arg.put("instId", symbol);
            arg.put("ordId", orderId);

            JSONObject[] args = new JSONObject[]{arg};
            requestMessage.put("args", args);

            // 发送请求
            webSocketUtil.sendPrivateRequest(requestMessage.toJSONString());

            // 获取配置的超时时间
            int timeout = okxApiConfig.getTimeout() > 0 ? okxApiConfig.getTimeout() : 10;
            boolean success = future.get(timeout, TimeUnit.SECONDS);
            cancelOrderFutures.remove(orderId);

            return success;
        } catch (Exception e) {
            log.error("取消订单失败", e);
            throw new OkxApiException("取消订单失败: " + e.getMessage(), e);
        }
    }

    /**
     * 解析Ticker数据
     */
    private Ticker parseTicker(JSONObject tickerData, String symbol, String channel) {
        Ticker ticker = new Ticker();
        ticker.setSymbol(symbol);
        ticker.setChannel(channel);
        ticker.setLastPrice(BigDecimalUtil.safeGen(tickerData.getString("last")));
        ticker.setBidPrice(BigDecimalUtil.safeGen(tickerData.getString("bidPx")));
        ticker.setAskPrice(BigDecimalUtil.safeGen(tickerData.getString("askPx")));
        ticker.setHighPrice(BigDecimalUtil.safeGen(tickerData.getString("high24h")));
        ticker.setLowPrice(BigDecimalUtil.safeGen(tickerData.getString("low24h")));
        ticker.setVolume(BigDecimalUtil.safeGen(tickerData.getString("vol24h")));
        ticker.setQuoteVolume(BigDecimalUtil.safeGen(tickerData.getString("volCcy24h")));

        // 解析时间戳
        long timestamp = tickerData.getLongValue("ts");
        LocalDateTime time = LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.of("UTC+8"));
        ticker.setTimestamp(time);

        // 计算24小时涨跌幅
        if (tickerData.containsKey("open24h") && tickerData.containsKey("last")) {
            BigDecimal open = BigDecimalUtil.safeGen(tickerData.getString("open24h"));
            BigDecimal last = BigDecimalUtil.safeGen(tickerData.getString("last"));
            if (open.compareTo(BigDecimal.ZERO) > 0) {
                ticker.setPriceChange(last.subtract(open));
                ticker.setPriceChangePercent(last.subtract(open).divide(open, 4, BigDecimal.ROUND_HALF_UP).multiply(new BigDecimal("100")));
            }
        }

        return ticker;
    }

    /**
     * 解析K线数据
     */
    private Candlestick parseCandlestick(JSONArray candleData, String symbol, String channel) {
        Candlestick candlestick = new Candlestick();
        candlestick.setSymbol(symbol);
        candlestick.setChannel(channel);

        // 解析时间戳
        long timestamp = Long.parseLong(candleData.getString(0));
        LocalDateTime time = LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.of("UTC+8"));
        candlestick.setOpenTime(time);

        candlestick.setOpen(BigDecimalUtil.safeGen(candleData.getString(1)));
        candlestick.setHigh(BigDecimalUtil.safeGen(candleData.getString(2)));
        candlestick.setLow(BigDecimalUtil.safeGen(candleData.getString(3)));
        candlestick.setClose(BigDecimalUtil.safeGen(candleData.getString(4)));
        candlestick.setVolume(BigDecimalUtil.safeGen(candleData.getString(5)));
        candlestick.setVolCcy(BigDecimalUtil.safeGen(candleData.getString(6)));
        candlestick.setQuoteVolume(BigDecimalUtil.safeGen(candleData.getString(7)));
        candlestick.setState(Integer.parseInt(candleData.getString(8)));

        return candlestick;
    }

    /**
     * 解析账户余额数据
     */
    private AccountBalance parseAccountBalance(JSONObject balanceData) {
        AccountBalance accountBalance = new AccountBalance();
        accountBalance.setTotalEquity(BigDecimalUtil.safeGen(balanceData.getString("totalEq")));

        List<AssetBalance> assetBalances = new ArrayList<>();
        JSONArray detailsArray = balanceData.getJSONArray("details");

        for (int i = 0; i < detailsArray.size(); i++) {
            JSONObject detail = detailsArray.getJSONObject(i);
            AssetBalance assetBalance = new AssetBalance();
            assetBalance.setAsset(detail.getString("ccy"));
            assetBalance.setAvailable(BigDecimalUtil.safeGen(detail.getString("availEq")));
            assetBalance.setFrozen(BigDecimalUtil.safeGen(detail.getString("frozenBal")));
            assetBalance.setTotal(BigDecimalUtil.safeGen(detail.getString("eq")));
            assetBalance.setUsdValue(BigDecimalUtil.safeGen(detail.getString("eqUsd")));
            assetBalances.add(assetBalance);
        }

        accountBalance.setAvailableBalance(assetBalances.stream().map(x -> x.getAvailable().divide(x.getTotal(), 8, RoundingMode.HALF_UP).multiply(x.getUsdValue())).reduce(BigDecimal::add).orElseGet(() -> BigDecimal.ZERO));
        accountBalance.setFrozenBalance(accountBalance.getTotalEquity().subtract(accountBalance.getAvailableBalance()));
        accountBalance.setAssetBalances(assetBalances);
        return accountBalance;
    }

    /**
     * 解析订单数据{"pxType":"","fee":"-0.000003112","px":"","tpTriggerPxType":"","source":"","ordId":"2626683924340039680","attachAlgoClOrdId":"","clOrdId":"1750783677807ccdf3fd5","ccy":"",
     * "linkedAlgoOrd":{"algoId":""},"state":"filled","tag":"","quickMgnType":"","attachAlgoOrds":[],"slTriggerPxType":"","stpId":"","lever":"","tdMode":"cash","tgtCcy":"quote_ccy","tpOrdPx":"",
     * "cancelSourceReason":"","pnl":"0","instType":"SPOT","reduceOnly":"false","slOrdPx":"","pxUsd":"","ordType":"market","fillSz":"0.003112","pxVol":"","algoClOrdId":"","cTime":"1750783681123",
     * "tpTriggerPx":"","accFillSz":"0.003112","posSide":"net","isTpLimit":"false","stpMode":"cancel_both","side":"buy","fillPx":"642.5","algoId":"","rebate":"0","sz":"2","instId":"BNB-USDT",
     * "avgPx":"642.5","cancelSource":"","slTriggerPx":"","uTime":"1750783681126","fillTime":"1750783681123","category":"normal","rebateCcy":"USDT","tradeId":"26069441","feeCcy":"BNB"}
     */
    private Order parseOrder(JSONObject orderData) {
        Order order = new Order();
        order.setOrderId(orderData.getString("ordId"));
        order.setClientOrderId(orderData.getString("clOrdId"));
        if (orderData.containsKey("instId")) {
            order.setSymbol(orderData.getString("instId"));
        }

        if (orderData.containsKey("sMsg")) {
            order.setSMsg(orderData.getString("sMsg"));
        }

        if (orderData.containsKey("sCode")) {
            order.setSCode(Integer.parseInt(orderData.getString("sCode")));
        }

        // 下单数量 买 "sz":"4.7"  下单usdt金额 ，卖 "sz":"0.00004225"  交易币的数量
        if (orderData.containsKey("sz") && !orderData.getString("sz").isEmpty()) {
            order.setOrigQty(BigDecimalUtil.safeGen(orderData.getString("sz")));
        }

        // 买是买的货币数量 "fee":"-0.00000004324"  ，卖是usdt数量 "fee":"-0.004593758"
        if (orderData.containsKey("fee") && !orderData.getString("fee").isEmpty()) {
            if (orderData.getString("feeCcy").equals("USDT")) {
                order.setFee(BigDecimalUtil.safeGen(orderData.getString("fee")).abs());
            } else {
                order.setFee(BigDecimalUtil.safeGen(orderData.getString("fee")).multiply(BigDecimalUtil.safeGen(orderData.getString("fillPx"))).abs());
            }
        }

        // 成交数量，扣除手续费 ，买入扣除的是交易货币，要参照成交价格转换成usdt
        // 接口返回的成交数量没有扣除手续费，需要扣除才是实际成交数量，成交金额也是，返回的费用是负数，所以要加上
        // 卖买都一样都是币的数量，不是usdt的数量 "accFillSz":"0.00004324"   "fillSz":"0.00004324"
        if (orderData.containsKey("accFillSz")) {
            if (orderData.containsKey("side") && orderData.getString("side").equals("buy")) {
                order.setExecutedQty(BigDecimalUtil.safeGen(orderData.getString("accFillSz")).subtract(BigDecimalUtil.safeGen(orderData.getString("fee")).abs()));
            } else {
                BigDecimal accFillFeeSz = order.getFee().divide(BigDecimal.valueOf(Double.valueOf(orderData.getString("fillPx"))), 12, BigDecimal.ROUND_DOWN);
                order.setExecutedQty(BigDecimalUtil.safeGen(orderData.getString("accFillSz")).subtract(accFillFeeSz));
            }
        }

        // 成交金额，扣除手续费  avgPx  fillPx  都是成交价，一般一样
        if (orderData.containsKey("fillPx") && !orderData.getString("fillPx").isEmpty()) {
            BigDecimal fillPrice = BigDecimalUtil.safeGen(orderData.getString("fillPx"));
            order.setCummulativeQuoteQty(order.getExecutedQty().multiply(fillPrice));
        }

        //成交价格
        if (orderData.containsKey("fillPx")) {
            order.setPrice(BigDecimalUtil.safeGen(orderData.getString("fillPx")));
        }

        if (orderData.containsKey("state")) {
            order.setStatus(mapOrderStatus(orderData.getString("state")));
        }
        if (orderData.containsKey("ordType")) {
            order.setType(mapOrderType(orderData.getString("ordType")));
        }
        if (orderData.containsKey("side")) {
            order.setSide(orderData.getString("side").toUpperCase());
        }


        if (orderData.containsKey("feeCcy")) {
            order.setFeeCurrency(orderData.getString("feeCcy"));
        }

        // 解析时间戳
        if (orderData.containsKey("cTime")) {
            long createTime = Long.parseLong(orderData.getString("cTime"));
            order.setCreateTime(LocalDateTime.ofInstant(Instant.ofEpochMilli(createTime), ZoneId.of("UTC+8")));
        }

        if (orderData.containsKey("uTime")) {
            long updateTime = Long.parseLong(orderData.getString("uTime"));
            order.setUpdateTime(LocalDateTime.ofInstant(Instant.ofEpochMilli(updateTime), ZoneId.of("UTC+8")));
        }
        log.info("返回订单信息:{}", order);
        return order;
    }

    /**
     * 格式化K线间隔
     */
    private String formatInterval(String interval) {
        return interval;
    }

    /**
     * 映射OKX订单状态到标准状态
     */
    private String mapOrderStatus(String okxStatus) {
        switch (okxStatus) {
            case "live":
                return "NEW";
            case "partially_filled":
                return "PARTIALLY_FILLED";
            case "filled":
                return "FILLED";
            case "canceled":
                return "CANCELED";
            case "canceling":
                return "CANCELING";
            default:
                return okxStatus.toUpperCase();
        }
    }

    /**
     * 映射标准订单状态到OKX订单状态
     */
    private String mapToOkxOrderStatus(String standardStatus) {
        switch (standardStatus.toUpperCase()) {
            case "NEW":
                return "live";
            case "PARTIALLY_FILLED":
                return "partially_filled";
            case "FILLED":
                return "filled";
            case "CANCELED":
                return "canceled";
            case "CANCELING":
                return "canceling";
            default:
                return standardStatus.toLowerCase();
        }
    }

    /**
     * 映射OKX订单类型到标准类型
     */
    private String mapOrderType(String okxType) {
        switch (okxType) {
            case "limit":
                return "LIMIT";
            case "market":
                return "MARKET";
            default:
                return okxType.toUpperCase();
        }
    }

    /**
     * 映射标准订单类型到OKX订单类型
     */
    private String mapToOkxOrderType(String standardType) {
        switch (standardType.toUpperCase()) {
            case "LIMIT":
                return "limit";
            case "MARKET":
                return "market";
            default:
                return standardType.toLowerCase();
        }
    }

    @Override
    public boolean unsubscribeTicker(String symbol) {
        try {
            // 检查是否已订阅
            if (!subscribedSymbols.contains(symbol)) {
                log.debug("币种 {} 未订阅，无需取消", symbol);
                return true;
            }

            String channel = "tickers";
            String key = channel + "_" + symbol;

            log.info("取消订阅行情数据，交易对: {}", symbol);
            webSocketUtil.unsubscribePublicTopic(channel, symbol);

            // 移除订阅标记
            subscribedSymbols.remove(symbol);

            // 清理相关Future
            tickerFutures.remove(key);
            return true;
        } catch (Exception e) {
            log.error("取消订阅行情数据失败: {}", e.getMessage(), e);
            return false;
        }
    }

    @Override
    public boolean subscribeKlineData(String symbol, String interval) {
        try {
            // 订阅K线数据
            String channel = "candle" + interval;
            String key = channel + "_" + symbol + "_" + interval;

            // 检查是否已订阅
            String subscribedSymbol = symbol + ":" + interval;
            if (klineCacheService.getAllSubscribedKlines().contains(subscribedSymbol) && subscribedSymbols.contains(subscribedSymbol)) {
                log.info("币种 {} 已订阅，无需重复订阅", symbol);
                return true;
            }

            log.info("订阅K线数据，交易对: {}, 间隔: {}", symbol, interval);

            // 创建订阅参数
            JSONObject arg = new JSONObject();
            arg.put("channel", channel);
            arg.put("instId", symbol);

            // 发送订阅请求
            webSocketUtil.subscribePublicTopicWithArgs(arg, symbol);

            // 添加已订阅标记
            klineCacheService.subscribeKline(symbol, interval);
            subscribedSymbols.add(subscribedSymbol);

            return true;
        } catch (Exception e) {
            log.error("订阅K线数据失败: {}", e.getMessage(), e);
            return false;
        }
    }

    @Override
    public boolean unsubscribeKlineData(String symbol, String interval) {
        try {
            // 取消订阅标记价格K线数据
            String channel = "candle" + interval;
            String key = channel + "_" + symbol + "_" + interval;

            // 检查是否已订阅
            if (!klineCacheService.getAllSubscribedKlines().contains(symbol + ":" + interval)) {
                log.debug("币种 {} 未订阅，无需取消", symbol);
                return true;
            }

            log.info("取消订阅K线数据，交易对: {}, 间隔: {}", symbol, interval);

            // 创建取消订阅参数
            JSONObject arg = new JSONObject();
            arg.put("channel", channel);
            arg.put("instId", symbol);

            // 发送取消订阅请求
            webSocketUtil.unsubscribePublicTopicWithArgs(arg, symbol);

            // 移除已订阅标记
            klineCacheService.getAllSubscribedKlines().remove(symbol + ":" + interval);
            klineFutures.remove(key);

            return true;
        } catch (Exception e) {
            log.error("取消订阅K线数据失败: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 检查币种是否已订阅
     *
     * @param symbol 交易对符号
     * @return 是否已订阅
     */
    public boolean isSymbolSubscribed(String symbol) {
        return subscribedSymbols.contains(symbol);
    }

    /**
     * 获取所有已订阅的币种
     *
     * @return 已订阅币种集合
     */
    public Set<String> getSubscribedSymbols() {
        return new HashSet<>(subscribedSymbols);
    }

    /**
     * @param symbol    交易对，如BTC-USDT
     * @param interval  K线间隔，如1m, 5m, 15m, 30m, 1H, 2H, 4H, 6H, 12H, 1D, 1W, 1M
     * @param startTime 开始时间戳（毫秒） 不包括
     * @param endTime   结束时间戳（毫秒） 不包括
     * @param limit     获取数据条数，最大为300
     * @return
     */
    @Override
    public List<Candlestick> getHistoryKlineData(String symbol, String interval, Long startTime, Long endTime, Integer limit) {
        try {
            // WebSocket模式下，历史K线通过REST API获取
            startTime = startTime - 1;
            endTime = endTime + 1;
            log.info("获取历史K线数据, symbol: {}, interval: {}, startTime: {}, endTime: {}, limit: {}",
                    symbol, interval, startTime, endTime, limit);

            // 使用现有的已经注入的依赖，向REST API发起请求  beafore 开始时间 after 结束时间  左开右开
            String url = okxApiConfig.getBaseUrl() + "/api/v5/market/history-candles";
            url = url + "?instId=" + symbol + "&bar=" + interval;

            if (startTime != null) {
                url = url + "&before=" + startTime;

            }

            if (endTime != null) {
                url = url + "&after=" + endTime;
            }

            if (limit != null && limit > 0) {
                url = url + "&limit=" + limit;
            }

            // 这里不需要认证，直接发送GET请求
            String response = HttpUtil.get(okHttpClient, url, null);
            JSONObject jsonResponse = JSONObject.parseObject(response);

            if (!"0".equals(jsonResponse.getString("code"))) {
                throw new OkxApiException(jsonResponse.getIntValue("code"), jsonResponse.getString("msg"));
            }

            JSONArray dataArray = jsonResponse.getJSONArray("data");
            List<Candlestick> result = new ArrayList<>();

            for (int i = 0; i < dataArray.size(); i++) {
                JSONArray item = dataArray.getJSONArray(i);

                // OKX API返回格式：[时间戳, 开盘价, 最高价, 最低价, 收盘价, 成交量, 成交额]
                Candlestick candlestick = new Candlestick();
                candlestick.setSymbol(symbol);
                candlestick.setIntervalVal(interval);

                // 转换时间戳为LocalDateTime
                long timestamp = item.getLongValue(0);
                LocalDateTime dateTime = LocalDateTime.ofInstant(
                        Instant.ofEpochMilli(timestamp),
                        ZoneId.of("UTC+8"));

                candlestick.setOpenTime(dateTime);
                candlestick.setOpen(BigDecimalUtil.safeGen(item.getString(1)));
                candlestick.setHigh(BigDecimalUtil.safeGen(item.getString(2)));
                candlestick.setLow(BigDecimalUtil.safeGen(item.getString(3)));
                candlestick.setClose(BigDecimalUtil.safeGen(item.getString(4)));
                candlestick.setVolume(BigDecimalUtil.safeGen(item.getString(5)));
                candlestick.setQuoteVolume(BigDecimalUtil.safeGen(item.getString(6)));

                // 收盘时间根据interval计算
                candlestick.setCloseTime(calculateCloseTimeFromInterval(dateTime, interval));

                // 成交笔数，OKX API可能没提供，设为0
                candlestick.setTrades(0L);

                result.add(candlestick);
            }

            return result;
        } catch (Exception e) {
            log.error("获取历史K线数据异常", e);
            throw new OkxApiException("获取历史K线数据失败: " + e.getMessage(), e);
        }
    }

    /**
     * 根据开盘时间和K线间隔计算收盘时间
     */
    private LocalDateTime calculateCloseTimeFromInterval(LocalDateTime openTime, String interval) {
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
     * 在活跃订单列表中查找指定订单
     *
     * @param clientOrderId 客户端订单ID
     * @param symbol        交易对
     * @param isSimulated   是否是模拟交易
     * @return 找到的订单，未找到则返回null
     */
    private Order findOrderInPendingList(String clientOrderId, String symbol, boolean isSimulated) {
        try {
            // 构建API请求路径，查询活跃订单
            StringBuilder apiUrlBuilder = new StringBuilder(okxApiConfig.getBaseUrl())
                    .append("/api/v5/trade/orders-pending?instType=SPOT&instId=")
                    .append(symbol);

            log.info("查询活跃订单API URL: {}", apiUrlBuilder.toString());

            // 时间戳
            String timestamp = String.valueOf(System.currentTimeMillis() / 1000);

            // 构建请求路径（用于签名）
            String requestPath = "/api/v5/trade/orders-pending?instType=SPOT&instId=" + symbol;

            // 生成签名
            String sign = SignatureUtil.sign(timestamp, "GET", requestPath, "", okxApiConfig.getSecretKey());

            Request.Builder requestBuilder = new Request.Builder()
                    .url(apiUrlBuilder.toString())
                    .addHeader("Content-Type", "application/json")
                    .addHeader("OK-ACCESS-KEY", okxApiConfig.getApiKey())
                    .addHeader("OK-ACCESS-SIGN", sign)
                    .addHeader("OK-ACCESS-TIMESTAMP", timestamp)
                    .addHeader("OK-ACCESS-PASSPHRASE", okxApiConfig.getPassphrase());

            // 如果是模拟交易
            if (isSimulated) {
                requestBuilder.addHeader("x-simulated-trading", "1");
            }

            Request request = requestBuilder.get().build();

            try (Response response = okHttpClient.newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    String responseBody = response.body().string();
                    log.info("活跃订单查询响应: {}", responseBody);

                    JSONObject responseJson = JSONObject.parseObject(responseBody);

                    if ("0".equals(responseJson.getString("code"))) {
                        JSONArray data = responseJson.getJSONArray("data");
                        if (data != null && !data.isEmpty()) {
                            // 遍历所有活跃订单，查找匹配的clientOrderId
                            for (int i = 0; i < data.size(); i++) {
                                JSONObject orderData = data.getJSONObject(i);
                                String respClientOrderId = orderData.getString("clOrdId");

                                if (clientOrderId.equals(respClientOrderId)) {
                                    Order order = parseOrder(orderData);
                                    log.info("在活跃订单中找到匹配订单: clientOrderId={}, orderId={}, status={}",
                                            clientOrderId, order.getOrderId(), order.getStatus());
                                    return order;
                                }
                            }
                        }
                    } else {
                        log.warn("活跃订单查询失败: code={}, msg={}",
                                responseJson.getString("code"), responseJson.getString("msg"));
                    }
                }
            }

            // 如果没找到，返回null
            return null;
        } catch (Exception e) {
            log.warn("查询活跃订单列表异常", e);
            return null;
        }
    }


    @Override
    public void clearSubscribeCache() {
        subscribedSymbols.clear();
    }

    /**
     * 获取所有已订阅币种的最新行情数据
     *
     * @return 所有已订阅币种的行情数据列表
     */
    @Override
    public List<Ticker> getAllTickers() {
        try {
            // 使用REST API获取所有SPOT交易对的行情数据
            String url = okxApiConfig.getBaseUrl() + "/api/v5/market/tickers?instType=SPOT";

            okhttp3.Request request = new okhttp3.Request.Builder()
                    .url(url)
                    .get()
                    .build();

            okhttp3.Response response = okHttpClient.newCall(request).execute();
            String responseBody = response.body() != null ? response.body().string() : null;

            if (responseBody == null) {
                log.error("获取所有币种行情数据失败: 响应为空");
                return Collections.emptyList();
            }

            JSONObject jsonResponse = JSON.parseObject(responseBody);

            if (!"0".equals(jsonResponse.getString("code"))) {
                log.error("获取所有币种行情数据失败: {}", jsonResponse.getString("msg"));
                return Collections.emptyList();
            }

            JSONArray dataArray = jsonResponse.getJSONArray("data");
            List<Ticker> tickers = new ArrayList<>();

            for (int i = 0; i < dataArray.size(); i++) {
                JSONObject data = dataArray.getJSONObject(i);
                String symbol = data.getString("instId");

                // 只处理以USDT结尾的交易对
                if (!symbol.endsWith("-USDT")) {
                    continue;
                }

                // 使用现有的parseTicker方法解析Ticker对象
                Ticker ticker = parseTicker(data, symbol, "tickers");

                tickers.add(ticker);
            }

            return tickers;
        } catch (Exception e) {
            log.error("获取所有币种行情数据异常", e);

            // 如果REST API调用失败，回退到使用已订阅的币种
            log.info("尝试从已订阅币种中获取行情数据...");
            List<Ticker> tickers = new ArrayList<>();
            Set<String> subscribedSymbols = getSubscribedSymbols();

            // 对每个已订阅的币种获取行情数据
            for (String symbol : subscribedSymbols) {
                try {
                    Ticker ticker = getTicker(symbol);
                    if (ticker != null) {
                        tickers.add(ticker);
                    }
                } catch (Exception ex) {
                    log.error("获取{}行情数据失败: {}", symbol, ex.getMessage());
                }
            }

            // 如果没有已订阅的币种或获取失败，返回主要币种的行情
            if (tickers.isEmpty()) {
                log.info("没有已订阅的币种或获取失败，尝试获取主要币种行情...");
                List<String> mainCoins = Arrays.asList("BTC-USDT", "ETH-USDT", "SOL-USDT", "BNB-USDT", "XRP-USDT");
                for (String symbol : mainCoins) {
                    try {
                        Ticker ticker = getTicker(symbol);
                        if (ticker != null) {
                            tickers.add(ticker);
                        }
                    } catch (Exception ex) {
                        log.error("获取{}行情数据失败: {}", symbol, ex.getMessage());
                    }
                }
            }

            return tickers;
        }
    }
}
