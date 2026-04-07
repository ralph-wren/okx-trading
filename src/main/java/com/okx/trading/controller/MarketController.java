package com.okx.trading.controller;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.okx.trading.model.common.ApiResponse;
import com.okx.trading.model.entity.CandlestickEntity;
import com.okx.trading.model.market.Candlestick;
import com.okx.trading.model.market.Ticker;
import com.okx.trading.service.HistoricalDataService;
import com.okx.trading.service.OkxApiService;
import com.okx.trading.service.RedisCacheService;
import com.okx.trading.service.KlineCacheService;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.TimeUnit;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import static com.okx.trading.constant.IndicatorInfo.ALL_COIN_RT_PRICE;

/**
 * 市场数据控制器
 * 提供K线数据获取和技术指标计算的接口
 */
@Slf4j
@Validated
@RestController
@RequestMapping("/api/market")
@Tag(name = "市场数据接口")
public class MarketController {

    private final OkxApiService okxApiService;
    private final HistoricalDataService historicalDataService;
    private final RedisCacheService redisCacheService;
    private final KlineCacheService klineCacheService;
    private final RedisTemplate<String, Object> redisTemplate;
    private final com.okx.trading.service.TushareApiService tushareApiService;

    @Autowired
    public MarketController(OkxApiService okxApiService,
                            HistoricalDataService historicalDataService,
                            RedisCacheService redisCacheService,
                            KlineCacheService klineCacheService, 
                            RedisTemplate<String, Object> redisTemplate,
                            com.okx.trading.service.TushareApiService tushareApiService) {
        this.okxApiService = okxApiService;
        this.historicalDataService = historicalDataService;
        this.redisCacheService = redisCacheService;
        this.klineCacheService = klineCacheService;
        this.redisTemplate = redisTemplate;
        this.tushareApiService = tushareApiService;
    }

    // 判断是否为开发环境，用于控制日志详细程度
    @Value("${spring.profiles.active:dev}")
    private String activeProfile;

    /**
     * 判断是否为开发环境
     */
    private boolean isDevelopmentEnvironment() {
        return "dev".equals(activeProfile) || "development".equals(activeProfile);
    }

    /**
     * 获取K线数据
     *
     * @param symbol   交易对，如BTC-USDT
     * @param interval K线间隔，如1m, 5m, 15m, 30m, 1H, 2H, 4H, 6H, 12H, 1D, 1W, 1M
     * @param limit    获取数据条数，最大为1000
     * @return K线数据列表
     */
    @Operation(description = "订阅实时标记价格K线数据,订阅完成后自动推送最新行情信息")
    @Parameters({
            @Parameter(name = "symbol", description = "交易对", required = true,   example = "BTC-USDT" ),
            @Parameter(name = "interval", description = "K线间隔 (1m=1分钟, 5m=5分钟, 15m=15分钟, 30m=30分钟, 1H=1小时, 2H=2小时, 4H=4小时, 6H=6小时, 12H=12小时, 1D=1天, 1W=1周, 1M=1个月)",
                    required = true,  example = "1m"),
            @Parameter(name = "limit", description = "获取数据条数，最大为1000，不传默认返回500条数据",
                    required = false, example = "100" )
    })
    @GetMapping("/subscribe_klines")
    public ApiResponse<List<Candlestick>> subscribeKlineData(
            @NotBlank(message = "交易对不能为空") @RequestParam String symbol,
            @NotBlank(message = "K线间隔不能为空") @RequestParam String interval,
            @RequestParam(required = false) @Min(value = 1, message = "数据条数必须大于0") Integer limit) {

        log.info("获取K线数据, symbol: {}, interval: {}, limit: {}", symbol, interval, limit);

        List<Candlestick> candlesticks = okxApiService.getKlineData(symbol, interval, limit);

        return ApiResponse.success(candlesticks);
    }

    /**
     * 获取最新行情数据
     *
     * @param symbol 交易对，如BTC-USDT
     * @return 行情数据
     */
    @Operation(summary = "获取最新行情", description = "获取指定交易对的最新价格、24小时涨跌幅等行情数据")
    @Parameters({
            @Parameter(name = "symbol", description = "交易对 (格式为 基础资产-计价资产，如BTC-USDT、ETH-USDT等)",
                    required = true,  example = "BTC-USDT" )
    })
    @GetMapping("/ticker")
    public ApiResponse<Ticker> getTicker(
            @NotBlank(message = "交易对不能为空") @RequestParam String symbol) {

        log.info("获取最新行情, symbol: {}", symbol);

        Ticker ticker = okxApiService.getTicker(symbol);

        return ApiResponse.success(ticker);
    }

    /**
     * 取消订阅K线数据
     *
     * @param symbol   交易对，如BTC-USDT
     * @param interval K线间隔，如1m, 5m, 15m, 30m, 1H, 2H, 4H, 6H, 12H, 1D, 1W, 1M
     * @return 操作结果
     */
    @Operation(summary = "取消订阅K线数据", description = "取消订阅指定交易对的K线数据")
    @Parameters({
            @Parameter(name = "symbol", description = "交易对", required = true,  example = "BTC-USDT"),
            @Parameter(name = "interval", description = "K线间隔 (1m=1分钟, 5m=5分钟, 15m=15分钟, 30m=30分钟, 1H=1小时, 2H=2小时, 4H=4小时, 6H=6小时, 12H=12小时, 1D=1天, 1W=1周, 1M=1个月)",
                    required = true,  example = "1m")
    })
    @GetMapping("/unsubscribe_klines")
    public ApiResponse<Boolean> unsubscribeKlineData(
            @NotBlank(message = "交易对不能为空") @RequestParam String symbol,
            @NotBlank(message = "K线间隔不能为空") @RequestParam String interval) {

        log.info("取消订阅K线数据, symbol: {}, interval: {}", symbol, interval);

        boolean result = okxApiService.unsubscribeKlineData(symbol, interval);

        return ApiResponse.success(result);
    }

    /**
     * 取消订阅行情数据
     *
     * @param symbol 交易对，如BTC-USDT
     * @return 操作结果
     */
    @Operation(summary = "取消订阅行情数据", description = "取消订阅指定交易对的实时行情数据")
    @Parameters({
            @Parameter(name = "symbol", description = "交易对 (格式为 基础资产-计价资产，如BTC-USDT、ETH-USDT等)",
                    required = true,  example = "BTC-USDT" )
    })
    @GetMapping("/unsubscribe_ticker")
    public ApiResponse<Boolean> unsubscribeTicker(
            @NotBlank(message = "交易对不能为空") @RequestParam String symbol) {

        log.info("取消订阅行情数据, symbol: {}", symbol);

        boolean result = okxApiService.unsubscribeTicker(symbol);

        return ApiResponse.success(result);
    }

    /**
     * 获取最新的K线数据
     *
     * @param symbol   交易对，如BTC-USDT
     * @param interval K线间隔，如1m, 5m, 15m, 30m, 1H, 2H, 4H, 6H, 12H, 1D, 1W, 1M
     * @param limit    获取数据条数，默认100
     * @return 最新的K线数据列表
     */
    @Operation(summary = "获取最新K线数据", description = "从数据库获取最新的K线数据，按时间降序排列")
    @Parameters({
            @Parameter(name = "symbol", description = "交易对", required = true,  example = "BTC-USDT" ),
            @Parameter(name = "interval", description = "K线间隔 (1m=1分钟, 5m=5分钟, 15m=15分钟, 30m=30分钟, 1H=1小时, 2H=2小时, 4H=4小时, 6H=6小时, 12H=12小时, 1D=1天, 1W=1周, 1M=1个月)",
                    required = true,  example = "1m"),
            @Parameter(name = "limit", description = "获取数据条数，默认100",
                    required = false, example = "100")
    })
    @GetMapping("/latest_klines")
    public ApiResponse<List<CandlestickEntity>> getLatestKlineData(
            @NotBlank(message = "交易对不能为空") @RequestParam String symbol,
            @NotBlank(message = "K线间隔不能为空") @RequestParam String interval,
            @RequestParam(required = false, defaultValue = "100") @Min(value = 1, message = "数据条数必须大于0") Integer limit) {

        log.info("获取最新K线数据, symbol: {}, interval: {}, limit: {}", symbol, interval, limit);

        List<CandlestickEntity> candlesticks = historicalDataService.getLatestHistoricalData(symbol, interval, limit);

        return ApiResponse.success(candlesticks);
    }

    /**
     * 查询数据库中已保存的历史K线数据
     *
     * @param symbol       交易对，如BTC-USDT
     * @param interval     K线间隔，如1m, 5m, 15m, 30m, 1H, 2H, 4H, 6H, 12H, 1D, 1W, 1M
     * @param startTimeStr 开始时间 (yyyy-MM-dd HH:mm:ss)
     * @param endTimeStr   结束时间 (yyyy-MM-dd HH:mm:ss)
     * @return 历史K线数据列表
     */
    @Operation(summary = "查询已保存的历史K线数据", description = "查询数据库中已保存的历史K线数据")
    @Parameters({
            @Parameter(name = "symbol", description = "交易对", required = true,  example = "BTC-USDT" ),
            @Parameter(name = "interval", description = "K线间隔 (1m=1分钟, 5m=5分钟, 15m=15分钟, 30m=30分钟, 1H=1小时, 2H=2小时, 4H=4小时, 6H=6小时, 12H=12小时, 1D=1天, 1W=1周, 1M=1个月)",
                    required = true,  example = "1m"),
            @Parameter(name = "startTimeStr", description = "开始时间 (yyyy-MM-dd HH:mm:ss)", required = true,  example = "2023-01-01 00:00:00" ),
            @Parameter(name = "endTimeStr", description = "结束时间 (yyyy-MM-dd HH:mm:ss)", required = true,  example = "2023-01-02 00:00:00" )
    })
    @GetMapping("/query_saved_history")
    public ApiResponse<List<CandlestickEntity>> querySavedHistoricalData(
            @NotBlank(message = "交易对不能为空") @RequestParam String symbol,
            @NotBlank(message = "K线间隔不能为空") @RequestParam String interval,
            @NotBlank(message = "开始时间不能为空") @RequestParam String startTimeStr,
            @NotBlank(message = "结束时间不能为空") @RequestParam String endTimeStr) {

        log.info("查询已保存的历史K线数据, symbol: {}, interval: {}, startTime: {}, endTime: {}",
                symbol, interval, startTimeStr, endTimeStr);

        try {
            // 将字符串时间转换为LocalDateTime
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            LocalDateTime startTime = LocalDateTime.parse(startTimeStr, formatter);
            LocalDateTime endTime = LocalDateTime.parse(endTimeStr, formatter);

            // 查询数据
            List<CandlestickEntity> data = historicalDataService.getHistoricalData(symbol, interval, startTime, endTime);

            return ApiResponse.success(data);
        } catch (Exception e) {
            log.error("查询历史K线数据失败: {}", e.getMessage(), e);
            return ApiResponse.error(500, "查询历史K线数据失败: " + e.getMessage());
        }
    }


    /**
     * 获取历史K线数据并保存，智能计算需要获取的数据量
     *
     * @param symbol       交易对，如BTC-USDT
     * @param interval     K线间隔，如1m, 5m, 15m, 30m, 1H, 2H, 4H, 6H, 12H, 1D, 1W, 1M
     * @param startTimeStr 开始时间 (yyyy-MM-dd HH:mm:ss)
     * @param endTimeStr   结束时间 (yyyy-MM-dd HH:mm:ss)
     * @return 操作结果，包含获取的K线数据
     */
    @Operation(summary = "智能获取历史K线数据", description = "根据入参计算需要获取的K线数量，扣除已有数据，按需获取并保存")
    @Parameters({
            @Parameter(name = "symbol", description = "交易对", required = true,  example = "BTC-USDT"),
            @Parameter(name = "interval", description = "K线间隔 (1m=1分钟, 5m=5分钟, 15m=15分钟, 30m=30分钟, 1H=1小时, 2H=2小时, 4H=4小时, 6H=6小时, 12H=12小时, 1D=1天, 1W=1周, 1M=1个月)",
                    required = true,  example = "1m"),
            @Parameter(name = "startTimeStr", description = "开始时间 (yyyy-MM-dd HH:mm:ss)", required = true,  example = "2018-01-01 00:00:00" ),
            @Parameter(name = "endTimeStr", description = "结束时间 (yyyy-MM-dd HH:mm:ss)", required = true,  example = "2025-04-01 00:00:00" )
    })
    @GetMapping("/fetch_history_with_integrity_check")
    public ApiResponse<List<CandlestickEntity>> fetchAndSaveHistoryWithIntegrityCheck(
            @NotBlank(message = "交易对不能为空") @RequestParam String symbol,
            @NotBlank(message = "K线间隔不能为空") @RequestParam String interval,
            @NotBlank(message = "开始时间不能为空") @RequestParam String startTimeStr,
            @NotBlank(message = "结束时间不能为空") @RequestParam String endTimeStr) {

        log.info("收到历史数据查询请求: symbol={}, interval={}, startTime={}, endTime={}", 
                symbol, interval, startTimeStr, endTimeStr);
        
        // 添加详细的interval参数日志
        log.info("interval参数详情: 原始值='{}', 长度={}, 最后一个字符='{}', 大小写检查: M={}, m={}", 
                interval, interval.length(), 
                interval.substring(interval.length() - 1),
                interval.endsWith("M"), interval.endsWith("m"));
        
        try {
            // 判断是否为股票代码（格式：XXXXXX.SZ 或 XXXXXX.SH）
            boolean isStock = symbol.matches("^\\d{6}\\.(SZ|SH)$");
            
            log.info("判断交易对类型: symbol={}, isStock={}", symbol, isStock);
            
            if (isStock) {
                // 股票：调用Tushare API
                log.info("检测到股票代码: {}, 使用Tushare API获取数据, interval={}", symbol, interval);
                
                // 将时间字符串转换为时间戳（毫秒）
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                long startTime = LocalDateTime.parse(startTimeStr, formatter)
                        .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
                long endTime = LocalDateTime.parse(endTimeStr, formatter)
                        .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
                
                // 调用Tushare API获取股票数据
                List<Candlestick> stockData = tushareApiService.getHistoryKlineData(
                        symbol, interval, startTime, endTime, 5000);
                
                // 转换为CandlestickEntity
                List<CandlestickEntity> entities = stockData.stream()
                        .map(this::convertToEntity)
                        .collect(Collectors.toList());
                
                return ApiResponse.success(entities);
            } else {
                // 加密货币：使用原有逻辑
                log.info("检测到加密货币: {}, 使用OKX API获取数据, interval={}", symbol, interval);
                List<CandlestickEntity> candlestickEntities = historicalDataService
                        .fetchAndSaveHistoryWithIntegrityCheck(symbol, interval, startTimeStr, endTimeStr);
                return ApiResponse.success(candlestickEntities);
            }
        } catch (Exception e) {
            log.error("❌ 智能获取历史K线数据失败: {}", e.getMessage(), e);
            return ApiResponse.error(500, "获取历史K线数据失败: " + e.getMessage());
        }
    }
    
    /**
     * 将Candlestick转换为CandlestickEntity
     */
    private CandlestickEntity convertToEntity(Candlestick candlestick) {
        CandlestickEntity entity = new CandlestickEntity();
        entity.setSymbol(candlestick.getSymbol());
        entity.setIntervalVal(candlestick.getIntervalVal());
        entity.setOpenTime(candlestick.getOpenTime());
        entity.setCloseTime(candlestick.getCloseTime());
        entity.setOpen(candlestick.getOpen());
        entity.setHigh(candlestick.getHigh());
        entity.setLow(candlestick.getLow());
        entity.setClose(candlestick.getClose());
        entity.setVolume(candlestick.getVolume());
        entity.setQuoteVolume(candlestick.getQuoteVolume());
        return entity;
    }

    /**
     * 查看Redis中已有的K线订阅数据
     * 用于调试和检查当前订阅状态
     */
    @Operation(summary = "查看已有订阅", description = "查看Redis中已保存的K线订阅数据")
    @GetMapping("/subscriptions")
    public ApiResponse<Set<String>> getSubscriptions() {
        log.info("查看Redis中已有的K线订阅数据");

        Set<String> subscriptions = klineCacheService.getAllSubscribedKlines();

        log.info("发现 {} 个订阅记录: {}", subscriptions.size(), subscriptions);

        return ApiResponse.success(subscriptions);
    }

    /**
     * 获取所有订阅币种的最新行情数据
     *
     * @param filter 可选的过滤条件（默认为空，可选值：all=所有币种, hot=热门币种, rise=涨幅最大, fall=跌幅最大）
     * @param search 搜索币种名称（可以是部分匹配，不区分大小写）
     * @param limit  返回的数据条数，默认为50
     * @return 所有订阅币种的行情数据列表
     */
    @Operation(summary = "获取单个币种信息", description = "根据交易对符号获取币种详细信息")
    @Parameters({
            @Parameter(name = "symbol", description = "交易对符号，如BTC-USDT",
                    required = true, example = "BTC-USDT")
    })
    @GetMapping("/crypto/{symbol}")
    public ApiResponse<Ticker> getCryptoInfo(@PathVariable String symbol) {
        try {
            log.info("获取币种信息: symbol={}", symbol);
            
            // 从 Hash 缓存中获取单个币种信息
            String hashKey = "market:crypto:hash";
            Object cachedValue = redisTemplate.opsForHash().get(hashKey, symbol);
            
            if (cachedValue != null) {
                String jsonString = cachedValue.toString();
                Ticker ticker = JSONObject.parseObject(jsonString, Ticker.class);
                log.info("从缓存中获取币种信息: {}", symbol);
                return ApiResponse.success(ticker);
            }
            
            // 如果缓存不存在，从短期缓存或API获取
            log.info("缓存不存在，尝试从实时数据获取: {}", symbol);
            Ticker ticker = okxApiService.getTicker(symbol);
            return ApiResponse.success(ticker);
        } catch (Exception e) {
            log.error("获取币种信息失败", e);
            return ApiResponse.error(500, "获取币种信息失败: " + e.getMessage());
        }
    }

    @Operation(summary = "获取所有币种最新行情", description = "获取所有已订阅币种的最新价格、24小时涨跌幅等行情数据")
    @Parameters({
            @Parameter(name = "filter", description = "过滤条件（all=所有币种, hot=热门币种, rise=涨幅最大, fall=跌幅最大）",
                    required = false,  example = "hot"),
            @Parameter(name = "search", description = "搜索币种名称，如BTC、ETH等（不区分大小写）",
                    required = false,  example = "BTC"),
            @Parameter(name = "limit", description = "返回数据条数，默认为50",
                    required = false, example = "20")
    })
    @GetMapping("/all_tickers")
    public ApiResponse<List<Ticker>> getAllTickers(
            @RequestParam(required = false, defaultValue = "all") String filter,
            @RequestParam(required = false) String search,
            @RequestParam(required = false, defaultValue = "50") Integer limit) {
        
        // 构建缓存键（5秒内的重复请求直接返回缓存）
        String cacheKey = "api:all_tickers:" + filter + ":" + (search != null ? search : "null") + ":" + limit;
        String cachedResult = (String) redisTemplate.opsForValue().get(cacheKey);
        if (cachedResult != null) {
            log.debug("✅ 使用请求级别缓存，跳过查询");
            List<Ticker> cachedTickers = JSONArray.parseArray(cachedResult, Ticker.class);
            return ApiResponse.success(cachedTickers);
        }
        
        log.info("获取所有币种最新行情, filter: {}, search: {}, limit: {}", filter, search, limit);

        List<Ticker> tickers = null;
        
        // 优先从7天缓存中获取（启动时初始化的 Hash 缓存）
        String hashKey = "market:crypto:hash";
        Long hashSize = redisTemplate.opsForHash().size(hashKey);
        
        if (hashSize != null && hashSize > 0) {
            log.info("从7天缓存中获取加密货币列表，共 {} 个交易对", hashSize);
            
            // 从短期缓存中获取详细行情数据（包含实时价格）
            Set<Object> members = redisTemplate.opsForSet().members(ALL_COIN_RT_PRICE);
            if (!CollectionUtils.isEmpty(members)) {
                tickers = members.stream()
                    .map(x -> JSONObject.parseObject((String) x, Ticker.class))
                    .distinct()
                    .collect(Collectors.toList());
                log.info("从短期缓存查询所有币种价格");
            } else {
                // 如果短期缓存不存在，从 Hash 缓存中获取基础数据
                java.util.Map<Object, Object> cryptoMap = redisTemplate.opsForHash().entries(hashKey);
                tickers = new ArrayList<>();
                for (Object value : cryptoMap.values()) {
                    String jsonString = value.toString();
                    Ticker ticker = JSONObject.parseObject(jsonString, Ticker.class);
                    tickers.add(ticker);
                }
                log.info("从7天 Hash 缓存获取加密货币数据");
                
                // 更新短期缓存
                if (!tickers.isEmpty()) {
                    redisTemplate.opsForSet().add(ALL_COIN_RT_PRICE, 
                        (Object[])Arrays.stream(tickers.toArray()).map(Object::toString).toArray(String[]::new));
                    redisTemplate.expire(ALL_COIN_RT_PRICE, 10, TimeUnit.MINUTES);
                }
            }
        } else {
            // 如果7天缓存不存在，尝试从短期缓存获取
            Set<Object> members = redisTemplate.opsForSet().members(ALL_COIN_RT_PRICE);
            if (!CollectionUtils.isEmpty(members)) {
                tickers = members.stream()
                    .map(x -> JSONObject.parseObject((String) x, Ticker.class))
                    .distinct()
                    .collect(Collectors.toList());
                log.info("从短期缓存查询所有币种价格");
            } else {
                // 从 API 获取
                tickers = okxApiService.getAllTickers();
                log.info("从接口查询所有币种价格");
            }

            // 更新短期缓存
            if (tickers != null && !tickers.isEmpty()) {
                redisTemplate.opsForSet().add(ALL_COIN_RT_PRICE, 
                    (Object[])Arrays.stream(tickers.toArray()).map(Object::toString).toArray(String[]::new));
                redisTemplate.expire(ALL_COIN_RT_PRICE, 10, TimeUnit.MINUTES);
            }
        }

        // 如果有搜索条件，先过滤
        if (search != null && !search.trim().isEmpty()) {
            String searchTerm = search.trim().toUpperCase();
            tickers = tickers.stream()
                    .filter(ticker -> ticker.getSymbol().toUpperCase().contains(searchTerm))
                    .collect(Collectors.toList());
        }

        // 根据过滤条件处理数据
        if ("hot".equalsIgnoreCase(filter)) {
            // 热门币种：按24小时成交量降序排序
            tickers.sort((t1, t2) -> t2.getQuoteVolume().compareTo(t1.getQuoteVolume()));
        } else if ("rise".equalsIgnoreCase(filter)) {
            // 涨幅最大：按涨跌幅降序排序
            tickers.sort((t1, t2) -> t2.getPriceChangePercent().compareTo(t1.getPriceChangePercent()));
        } else if ("fall".equalsIgnoreCase(filter)) {
            // 跌幅最大：按涨跌幅升序排序
            tickers.sort((t1, t2) -> t1.getPriceChangePercent().compareTo(t2.getPriceChangePercent()));
        }

        // 限制返回数量
        if (limit != null && limit > 0 && tickers.size() > limit) {
            tickers = tickers.subList(0, limit);
        }

        // 保存请求级别缓存（5秒有效期）
        redisTemplate.opsForValue().set(cacheKey, JSONArray.toJSONString(tickers), 5, TimeUnit.SECONDS);

        return ApiResponse.success(tickers);
    }

}
