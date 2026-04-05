package com.okx.trading.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.okx.trading.config.TushareConfig;
import com.okx.trading.model.market.Candlestick;
import com.okx.trading.model.market.Ticker;
import com.okx.trading.service.TushareApiService;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Tushare API服务实现
 */
@Slf4j
@Service
public class TushareApiServiceImpl implements TushareApiService {

    @Autowired
    private TushareConfig tushareConfig;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private OkHttpClient httpClient;

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    
    // 日期格式化
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final SimpleDateFormat SDF_DATE = new SimpleDateFormat("yyyyMMdd");
    private static final SimpleDateFormat SDF_DATETIME = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    /**
     * 初始化HTTP客户端
     */
    private OkHttpClient getHttpClient() {
        if (httpClient == null) {
            OkHttpClient.Builder builder = new OkHttpClient.Builder()
                    .connectTimeout(tushareConfig.getTimeout(), TimeUnit.SECONDS)
                    .readTimeout(tushareConfig.getTimeout(), TimeUnit.SECONDS)
                    .writeTimeout(tushareConfig.getTimeout(), TimeUnit.SECONDS);

            // 配置代理
            if (tushareConfig.isProxyEnabled() && tushareConfig.getProxyHost() != null) {
                Proxy proxy = new Proxy(Proxy.Type.HTTP,
                        new InetSocketAddress(tushareConfig.getProxyHost(), tushareConfig.getProxyPort()));
                builder.proxy(proxy);
                log.info("Tushare API使用代理: {}:{}", tushareConfig.getProxyHost(), tushareConfig.getProxyPort());
            }

            httpClient = builder.build();
        }
        return httpClient;
    }

    /**
     * 发送Tushare API请求
     */
    private JsonNode sendRequest(String apiName, Map<String, Object> params, String fields) throws IOException {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("api_name", apiName);
        requestBody.put("token", tushareConfig.getToken());
        requestBody.put("params", params == null ? new HashMap<>() : params);
        if (fields != null && !fields.isEmpty()) {
            requestBody.put("fields", fields);
        }

        String jsonBody = objectMapper.writeValueAsString(requestBody);
        log.debug("Tushare请求: {}", jsonBody);

        RequestBody body = RequestBody.create(jsonBody, JSON);
        Request request = new Request.Builder()
                .url(tushareConfig.getUrl())
                .post(body)
                .build();

        try (Response response = getHttpClient().newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Tushare API请求失败: " + response);
            }

            String responseBody = response.body().string();
            log.debug("Tushare响应: {}", responseBody);
            
            JsonNode rootNode = objectMapper.readTree(responseBody);
            
            // 检查错误
            if (rootNode.has("code") && rootNode.get("code").asInt() != 0) {
                String errorMsg = rootNode.has("msg") ? rootNode.get("msg").asText() : "未知错误";
                throw new IOException("Tushare API返回错误: " + errorMsg);
            }
            
            return rootNode.get("data");
        }
    }

    @Override
    public List<Candlestick> getDailyKlineData(String tsCode, String startDate, String endDate, Integer limit) {
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("ts_code", tsCode);
            if (startDate != null) params.put("start_date", startDate);
            if (endDate != null) params.put("end_date", endDate);
            if (limit != null) params.put("limit", limit);

            String fields = "ts_code,trade_date,open,high,low,close,vol,amount";
            JsonNode dataNode = sendRequest("daily", params, fields);

            return parseCandlestickData(dataNode, tsCode, "1D");
        } catch (Exception e) {
            log.error("获取日线数据失败: tsCode={}, error={}", tsCode, e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    @Override
    public List<Candlestick> getMinuteKlineData(String tsCode, String freq, String startDate, String endDate, Integer limit) {
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("ts_code", tsCode);
            params.put("freq", freq);
            if (startDate != null) params.put("start_time", startDate);
            if (endDate != null) params.put("end_time", endDate);
            if (limit != null) params.put("limit", limit);

            String fields = "ts_code,trade_time,open,high,low,close,vol,amount";
            JsonNode dataNode = sendRequest("stk_mins", params, fields);

            return parseCandlestickData(dataNode, tsCode, convertFreqToInterval(freq));
        } catch (Exception e) {
            log.error("获取分钟线数据失败: tsCode={}, freq={}, error={}", tsCode, freq, e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * 获取周线数据
     */
    public List<Candlestick> getWeeklyKlineData(String tsCode, String startDate, String endDate, Integer limit) {
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("ts_code", tsCode);
            if (startDate != null) params.put("start_date", startDate);
            if (endDate != null) params.put("end_date", endDate);
            if (limit != null) params.put("limit", limit);

            String fields = "ts_code,trade_date,open,high,low,close,vol,amount";
            JsonNode dataNode = sendRequest("weekly", params, fields);

            return parseCandlestickData(dataNode, tsCode, "1W");
        } catch (Exception e) {
            log.error("获取周线数据失败: tsCode={}, error={}", tsCode, e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * 获取月线数据
     */
    public List<Candlestick> getMonthlyKlineData(String tsCode, String startDate, String endDate, Integer limit) {
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("ts_code", tsCode);
            if (startDate != null) params.put("start_date", startDate);
            if (endDate != null) params.put("end_date", endDate);
            if (limit != null) params.put("limit", limit);

            String fields = "ts_code,trade_date,open,high,low,close,vol,amount";
            JsonNode dataNode = sendRequest("monthly", params, fields);

            return parseCandlestickData(dataNode, tsCode, "1M");
        } catch (Exception e) {
            log.error("获取月线数据失败: tsCode={}, error={}", tsCode, e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    @Override
    public Ticker getTicker(String tsCode) {
        try {
            // 获取最近2天的日线数据来计算涨跌幅
            List<Candlestick> klines = getDailyKlineData(tsCode, null, null, 2);
            if (klines.isEmpty()) {
                return null;
            }

            Candlestick latestKline = klines.get(klines.size() - 1); // 最新的一天
            Ticker ticker = new Ticker();
            ticker.setSymbol(tsCode);
            ticker.setLastPrice(latestKline.getClose());
            ticker.setHighPrice(latestKline.getHigh());
            ticker.setLowPrice(latestKline.getLow());
            ticker.setVolume(latestKline.getVolume());
            ticker.setTimestamp(latestKline.getOpenTime());

            // 计算涨跌幅
            if (klines.size() >= 2) {
                Candlestick previousKline = klines.get(klines.size() - 2); // 前一天
                BigDecimal priceChange = latestKline.getClose().subtract(previousKline.getClose());
                BigDecimal priceChangePercent = priceChange
                        .divide(previousKline.getClose(), 4, BigDecimal.ROUND_HALF_UP)
                        .multiply(new BigDecimal("100"));
                
                ticker.setPriceChange(priceChange);
                ticker.setPriceChangePercent(priceChangePercent);
            } else {
                // 如果只有一天数据，涨跌幅为0
                ticker.setPriceChange(BigDecimal.ZERO);
                ticker.setPriceChangePercent(BigDecimal.ZERO);
            }

            return ticker;
        } catch (Exception e) {
            log.error("获取行情数据失败: tsCode={}, error={}", tsCode, e.getMessage(), e);
            return null;
        }
    }

    @Override
    public List<String> getStockList(String exchange, String listStatus) {
        try {
            Map<String, Object> params = new HashMap<>();
            if (exchange != null) params.put("exchange", exchange);
            if (listStatus != null) params.put("list_status", listStatus);

            String fields = "ts_code,symbol,name,area,industry,list_date";
            JsonNode dataNode = sendRequest("stock_basic", params, fields);

            List<String> stockList = new ArrayList<>();
            if (dataNode != null && dataNode.has("items")) {
                JsonNode items = dataNode.get("items");
                for (JsonNode item : items) {
                    if (item.isArray() && item.size() > 0) {
                        stockList.add(item.get(0).asText()); // ts_code
                    }
                }
            }

            log.info("获取股票列表成功: exchange={}, count={}", exchange, stockList.size());
            return stockList;
        } catch (Exception e) {
            log.error("获取股票列表失败: exchange={}, error={}", exchange, e.getMessage(), e);
            return Collections.emptyList();
        }
    }
    
    @Override
    public List<com.okx.trading.model.dto.StockInfo> getStockInfoList(String exchange, String listStatus) {
        try {
            Map<String, Object> params = new HashMap<>();
            if (exchange != null) params.put("exchange", exchange);
            if (listStatus != null) params.put("list_status", listStatus);

            String fields = "ts_code,symbol,name,area,industry,list_date,exchange";
            JsonNode dataNode = sendRequest("stock_basic", params, fields);

            List<com.okx.trading.model.dto.StockInfo> stockList = new ArrayList<>();
            if (dataNode != null && dataNode.has("items")) {
                JsonNode items = dataNode.get("items");
                for (JsonNode item : items) {
                    if (item.isArray() && item.size() >= 3) {
                        String tsCode = item.get(0).asText(); // ts_code
                        String name = item.get(2).asText();   // name
                        String industry = item.size() > 4 ? item.get(4).asText() : "";
                        String listDate = item.size() > 5 ? item.get(5).asText() : "";
                        String exchangeVal = item.size() > 6 ? item.get(6).asText() : "";
                        
                        // 根据股票代码判断所属板块
                        String market = determineMarket(tsCode);
                        
                        com.okx.trading.model.dto.StockInfo stockInfo = com.okx.trading.model.dto.StockInfo.builder()
                                .code(tsCode)
                                .name(name)
                                .market(market)
                                .exchange(exchangeVal)
                                .industry(industry)
                                .listDate(listDate)
                                .build();
                        
                        stockList.add(stockInfo);
                    }
                }
            }

            log.info("获取股票信息列表成功: exchange={}, count={}", exchange, stockList.size());
            return stockList;
        } catch (Exception e) {
            log.error("获取股票信息列表失败: exchange={}, error={}", exchange, e.getMessage(), e);
            return Collections.emptyList();
        }
    }
    
    /**
     * 根据股票代码判断所属板块
     */
    private String determineMarket(String tsCode) {
        if (tsCode == null || tsCode.length() < 6) {
            return "main";
        }
        
        String code = tsCode.substring(0, 6);
        
        // 科创板：688开头
        if (code.startsWith("688")) {
            return "star";
        }
        
        // 创业板：300开头
        if (code.startsWith("300")) {
            return "chinext";
        }
        
        // 北交所：8开头（3位数）
        if (code.matches("^8\\d{5}")) {
            return "bse";
        }
        
        // 其他都是主板
        return "main";
    }

    @Override
    public List<Candlestick> getHistoryKlineData(String symbol, String interval, Long startTime, Long endTime, Integer limit) {
        try {
            log.info("获取历史K线数据: symbol={}, interval={}, startTime={}, endTime={}, limit={}", 
                    symbol, interval, startTime, endTime, limit);
            
            // 转换时间戳为日期字符串
            String startDate = null;
            String endDate = null;

            if (startTime != null) {
                LocalDateTime startDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(startTime), ZoneId.systemDefault());
                if (isMinuteInterval(interval)) {
                    startDate = startDateTime.format(DATETIME_FORMATTER);
                    log.info("分钟级别数据，开始时间格式化为: {}", startDate);
                } else {
                    startDate = startDateTime.format(DATE_FORMATTER);
                    log.info("日线级别数据，开始时间格式化为: {}", startDate);
                }
            }

            if (endTime != null) {
                LocalDateTime endDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(endTime), ZoneId.systemDefault());
                if (isMinuteInterval(interval)) {
                    endDate = endDateTime.format(DATETIME_FORMATTER);
                    log.info("分钟级别数据，结束时间格式化为: {}", endDate);
                } else {
                    endDate = endDateTime.format(DATE_FORMATTER);
                    log.info("日线级别数据，结束时间格式化为: {}", endDate);
                }
            }

            // 根据interval类型调用不同的API
            if (isMinuteInterval(interval)) {
                String freq = convertIntervalToFreq(interval);
                log.info("调用分钟线API: freq={}", freq);
                return getMinuteKlineData(symbol, freq, startDate, endDate, limit);
            } else if ("1W".equalsIgnoreCase(interval)) {
                log.info("调用周线API");
                return getWeeklyKlineData(symbol, startDate, endDate, limit);
            } else if ("1M".equalsIgnoreCase(interval)) {
                log.info("调用月线API");
                return getMonthlyKlineData(symbol, startDate, endDate, limit);
            } else {
                log.info("调用日线API");
                return getDailyKlineData(symbol, startDate, endDate, limit);
            }
        } catch (Exception e) {
            log.error("获取历史K线数据失败: symbol={}, interval={}, error={}", symbol, interval, e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    @Override
    public boolean testConnection() {
        try {
            // 测试获取指数基本信息
            Map<String, Object> params = new HashMap<>();
            params.put("limit", 1);
            JsonNode dataNode = sendRequest("index_basic", params, "ts_code,name");
            
            log.info("Tushare API连接测试成功");
            return dataNode != null;
        } catch (Exception e) {
            log.error("Tushare API连接测试失败: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 解析K线数据
     */
    private List<Candlestick> parseCandlestickData(JsonNode dataNode, String symbol, String interval) {
        List<Candlestick> candlesticks = new ArrayList<>();

        if (dataNode == null || !dataNode.has("items")) {
            return candlesticks;
        }

        JsonNode fields = dataNode.get("fields");
        JsonNode items = dataNode.get("items");

        // 构建字段索引映射
        Map<String, Integer> fieldIndexMap = new HashMap<>();
        for (int i = 0; i < fields.size(); i++) {
            fieldIndexMap.put(fields.get(i).asText(), i);
        }

        // 解析每一行数据
        for (JsonNode item : items) {
            try {
                Candlestick candlestick = new Candlestick();
                candlestick.setSymbol(symbol);
                candlestick.setIntervalVal(interval);

                // 解析时间
                String timeField = fieldIndexMap.containsKey("trade_time") ? "trade_time" : "trade_date";
                String timeStr = item.get(fieldIndexMap.get(timeField)).asText();
                LocalDateTime openTime = parseDateTime(timeStr, interval);
                candlestick.setOpenTime(openTime);
                
                // 设置收盘时间
                LocalDateTime closeTime = calculateCloseTime(openTime, interval);
                candlestick.setCloseTime(closeTime);

                // 解析价格和成交量
                candlestick.setOpen(new BigDecimal(item.get(fieldIndexMap.get("open")).asText()));
                candlestick.setHigh(new BigDecimal(item.get(fieldIndexMap.get("high")).asText()));
                candlestick.setLow(new BigDecimal(item.get(fieldIndexMap.get("low")).asText()));
                candlestick.setClose(new BigDecimal(item.get(fieldIndexMap.get("close")).asText()));
                candlestick.setVolume(new BigDecimal(item.get(fieldIndexMap.get("vol")).asText()));

                // amount字段可能不存在
                if (fieldIndexMap.containsKey("amount")) {
                    candlestick.setQuoteVolume(new BigDecimal(item.get(fieldIndexMap.get("amount")).asText()));
                }

                candlesticks.add(candlestick);
            } catch (Exception e) {
                log.warn("解析K线数据失败: {}", e.getMessage());
            }
        }

        // Tushare返回的数据是倒序的，需要反转
        Collections.reverse(candlesticks);
        
        log.info("解析K线数据成功: symbol={}, interval={}, count={}", symbol, interval, candlesticks.size());
        return candlesticks;
    }
    
    /**
     * 根据开盘时间和周期计算收盘时间
     */
    private LocalDateTime calculateCloseTime(LocalDateTime openTime, String interval) {
        switch (interval.toLowerCase()) {
            case "1min":
                return openTime.plusMinutes(1);
            case "5min":
                return openTime.plusMinutes(5);
            case "15min":
                return openTime.plusMinutes(15);
            case "30min":
                return openTime.plusMinutes(30);
            case "60min":
                return openTime.plusHours(1);
            case "1h":
                return openTime.plusHours(1);
            case "2h":
                return openTime.plusHours(2);
            case "4h":
                return openTime.plusHours(4);
            case "6h":
                return openTime.plusHours(6);
            case "12h":
                return openTime.plusHours(12);
            case "1d":
                return openTime.plusDays(1);
            case "1w":
                return openTime.plusWeeks(1);
            case "1mon":
                return openTime.plusMonths(1);
            default:
                // 默认加1天
                return openTime.plusDays(1);
        }
    }

    /**
     * 解析时间为 LocalDateTime
     */
    private LocalDateTime parseDateTime(String timeStr, String interval) throws ParseException {
        if (isMinuteInterval(interval)) {
            // 分钟线: yyyy-MM-dd HH:mm:ss
            return LocalDateTime.parse(timeStr, DATETIME_FORMATTER);
        } else {
            // 日线: yyyyMMdd
            return LocalDateTime.parse(timeStr + " 00:00:00", 
                    DateTimeFormatter.ofPattern("yyyyMMdd HH:mm:ss"));
        }
    }

    /**
     * 判断是否为分钟级别（包括小时级别，因为Tushare用分钟接口处理）
     */
    private boolean isMinuteInterval(String interval) {
        if (interval == null) return false;
        String lower = interval.toLowerCase();
        // 分钟级别：1m, 5m, 15m, 30m, 1min, 5min等
        // 小时级别：1h, 2h, 4h, 6h, 12h等（Tushare用60min接口处理）
        boolean isMinute = lower.contains("min") || 
               lower.matches(".*\\d+m$") || 
               lower.matches(".*\\d+h$");
        
        log.debug("判断interval是否为分钟级别: interval={}, isMinute={}", interval, isMinute);
        return isMinute;
    }

    /**
     * 转换interval到Tushare的freq格式
     */
    private String convertIntervalToFreq(String interval) {
        switch (interval.toLowerCase()) {
            case "1m":
            case "1min":
                return "1min";
            case "5m":
            case "5min":
                return "5min";
            case "15m":
            case "15min":
                return "15min";
            case "30m":
            case "30min":
                return "30min";
            case "60m":
            case "1h":
            case "60min":
                return "60min";
            // 对于其他小时级别，Tushare不直接支持，使用60min代替
            // 前端需要自己聚合数据
            case "2h":
            case "4h":
            case "6h":
            case "12h":
                log.warn("Tushare不支持{}周期，使用60min代替，前端需要聚合数据", interval);
                return "60min";
            default:
                log.warn("未知的interval: {}, 使用1min代替", interval);
                return "1min";
        }
    }

    /**
     * 转换Tushare的freq到interval格式
     */
    private String convertFreqToInterval(String freq) {
        switch (freq.toLowerCase()) {
            case "1min":
                return "1m";
            case "5min":
                return "5m";
            case "15min":
                return "15m";
            case "30min":
                return "30m";
            case "60min":
                return "1H";
            default:
                return freq;
        }
    }
}
