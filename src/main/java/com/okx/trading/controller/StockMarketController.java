package com.okx.trading.controller;

import com.alibaba.fastjson.JSON;
import com.okx.trading.model.market.Candlestick;
import com.okx.trading.model.market.Ticker;
import com.okx.trading.service.TushareApiService;
import com.okx.trading.util.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 股票市场数据控制器
 * 提供A股市场数据查询接口
 */
@Slf4j
@RestController
@RequestMapping("/api/stock/market")
@Tag(name = "股票市场数据", description = "A股市场数据查询接口")
public class StockMarketController {

    @Autowired
    private TushareApiService tushareApiService;
    
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @GetMapping("/test")
    @Operation(summary = "测试Tushare连接", description = "测试Tushare API是否可以正常连接")
    public ApiResponse<Boolean> testConnection() {
        try {
            boolean success = tushareApiService.testConnection();
            if (success) {
                return ApiResponse.success("Tushare API连接成功", true);
            } else {
                return ApiResponse.error(500, "Tushare API连接失败");
            }
        } catch (Exception e) {
            log.error("测试Tushare连接失败", e);
            return ApiResponse.error(500, "测试连接异常: " + e.getMessage());
        }
    }

    @GetMapping("/kline/daily")
    @Operation(summary = "获取日线数据", description = "获取指定股票的日线K线数据")
    public ApiResponse<List<Candlestick>> getDailyKline(
            @Parameter(description = "股票代码，如000001.SZ") @RequestParam String tsCode,
            @Parameter(description = "开始日期，格式：YYYYMMDD") @RequestParam(required = false) String startDate,
            @Parameter(description = "结束日期，格式：YYYYMMDD") @RequestParam(required = false) String endDate,
            @Parameter(description = "获取数据条数，默认100") @RequestParam(required = false, defaultValue = "100") Integer limit) {
        try {
            log.info("获取日线数据: tsCode={}, startDate={}, endDate={}, limit={}", tsCode, startDate, endDate, limit);
            List<Candlestick> klines = tushareApiService.getDailyKlineData(tsCode, startDate, endDate, limit);
            return ApiResponse.success(klines);
        } catch (Exception e) {
            log.error("获取日线数据失败", e);
            return ApiResponse.error(500, "获取日线数据失败: " + e.getMessage());
        }
    }

    @GetMapping("/kline/minute")
    @Operation(summary = "获取分钟线数据", description = "获取指定股票的分钟级K线数据")
    public ApiResponse<List<Candlestick>> getMinuteKline(
            @Parameter(description = "股票代码，如000001.SZ") @RequestParam String tsCode,
            @Parameter(description = "频率：1min, 5min, 15min, 30min, 60min") @RequestParam(defaultValue = "5min") String freq,
            @Parameter(description = "开始时间，格式：YYYY-MM-DD HH:MM:SS") @RequestParam(required = false) String startDate,
            @Parameter(description = "结束时间，格式：YYYY-MM-DD HH:MM:SS") @RequestParam(required = false) String endDate,
            @Parameter(description = "获取数据条数，默认100") @RequestParam(required = false, defaultValue = "100") Integer limit) {
        try {
            log.info("获取分钟线数据: tsCode={}, freq={}, startDate={}, endDate={}, limit={}", 
                    tsCode, freq, startDate, endDate, limit);
            List<Candlestick> klines = tushareApiService.getMinuteKlineData(tsCode, freq, startDate, endDate, limit);
            return ApiResponse.success(klines);
        } catch (Exception e) {
            log.error("获取分钟线数据失败", e);
            return ApiResponse.error(500, "获取分钟线数据失败: " + e.getMessage());
        }
    }

    @GetMapping("/kline/history")
    @Operation(summary = "获取历史K线数据", description = "获取指定股票的历史K线数据（兼容原有接口）")
    public ApiResponse<List<Candlestick>> getHistoryKline(
            @Parameter(description = "股票代码，如000001.SZ") @RequestParam String symbol,
            @Parameter(description = "K线间隔：1D, 1W, 1M, 1m, 5m, 15m, 30m, 1H") @RequestParam(defaultValue = "1D") String interval,
            @Parameter(description = "开始时间戳（毫秒）") @RequestParam(required = false) Long startTime,
            @Parameter(description = "结束时间戳（毫秒）") @RequestParam(required = false) Long endTime,
            @Parameter(description = "获取数据条数，默认100") @RequestParam(required = false, defaultValue = "100") Integer limit) {
        try {
            log.info("获取历史K线数据: symbol={}, interval={}, startTime={}, endTime={}, limit={}", 
                    symbol, interval, startTime, endTime, limit);
            List<Candlestick> klines = tushareApiService.getHistoryKlineData(symbol, interval, startTime, endTime, limit);
            return ApiResponse.success(klines);
        } catch (Exception e) {
            log.error("获取历史K线数据失败", e);
            return ApiResponse.error(500, "获取历史K线数据失败: " + e.getMessage());
        }
    }

    @GetMapping("/ticker")
    @Operation(summary = "获取最新行情", description = "获取指定股票的最新行情数据")
    public ApiResponse<Ticker> getTicker(
            @Parameter(description = "股票代码，如000001.SZ") @RequestParam String tsCode) {
        try {
            log.info("获取最新行情: tsCode={}", tsCode);
            Ticker ticker = tushareApiService.getTicker(tsCode);
            if (ticker != null) {
                return ApiResponse.success(ticker);
            } else {
                return ApiResponse.error(404, "未找到行情数据");
            }
        } catch (Exception e) {
            log.error("获取最新行情失败", e);
            return ApiResponse.error(500, "获取最新行情失败: " + e.getMessage());
        }
    }

    @GetMapping("/stock/list")
    @Operation(summary = "获取股票列表", description = "获取指定交易所的股票列表")
    public ApiResponse<List<String>> getStockList(
            @Parameter(description = "交易所：SSE-上交所, SZSE-深交所") @RequestParam(required = false) String exchange,
            @Parameter(description = "上市状态：L-上市, D-退市, P-暂停上市") @RequestParam(required = false, defaultValue = "L") String listStatus) {
        try {
            log.info("获取股票列表: exchange={}, listStatus={}", exchange, listStatus);
            List<String> stockList = tushareApiService.getStockList(exchange, listStatus);
            return ApiResponse.success(stockList);
        } catch (Exception e) {
            log.error("获取股票列表失败", e);
            return ApiResponse.error(500, "获取股票列表失败: " + e.getMessage());
        }
    }
    
    @GetMapping("/stock/info/list")
    @Operation(summary = "获取股票信息列表", description = "获取指定交易所的股票详细信息列表（包含代码、名称、板块等）")
    public ApiResponse<List<com.okx.trading.model.dto.StockInfo>> getStockInfoList(
            @Parameter(description = "交易所：SSE-上交所, SZSE-深交所, 不传则返回全部") @RequestParam(required = false) String exchange,
            @Parameter(description = "上市状态：L-上市, D-退市, P-暂停上市") @RequestParam(required = false, defaultValue = "L") String listStatus) {
        try {
            log.info("获取股票信息列表: exchange={}, listStatus={}", exchange, listStatus);
            
            // 优先从7天缓存中获取（启动时初始化的 Hash 缓存）
            String hashKey = "market:stock:hash";
            Long hashSize = redisTemplate.opsForHash().size(hashKey);
            
            if (hashSize != null && hashSize > 0) {
                // 从 Hash 中获取所有股票信息
                java.util.Map<Object, Object> stockMap = redisTemplate.opsForHash().entries(hashKey);
                List<com.okx.trading.model.dto.StockInfo> cachedStockList = new java.util.ArrayList<>();
                
                for (Object value : stockMap.values()) {
                    String jsonString = value.toString();
                    com.okx.trading.model.dto.StockInfo stock = 
                        JSON.parseObject(jsonString, com.okx.trading.model.dto.StockInfo.class);
                    cachedStockList.add(stock);
                }
                
                log.info("从7天缓存中获取股票列表，共 {} 只股票", cachedStockList.size());
                
                // 根据参数过滤
                List<com.okx.trading.model.dto.StockInfo> filteredList = cachedStockList;
                if (exchange != null && !exchange.trim().isEmpty()) {
                    filteredList = cachedStockList.stream()
                        .filter(stock -> exchange.equals(stock.getExchange()))
                        .collect(java.util.stream.Collectors.toList());
                }
                
                return ApiResponse.success(filteredList);
            }
            
            // 如果缓存不存在，从API获取
            log.info("缓存不存在，从 Tushare API 获取股票列表");
            List<com.okx.trading.model.dto.StockInfo> stockList = 
                tushareApiService.getStockInfoList(exchange, listStatus);
            return ApiResponse.success(stockList);
        } catch (Exception e) {
            log.error("获取股票信息列表失败", e);
            return ApiResponse.error(500, "获取股票信息列表失败: " + e.getMessage());
        }
    }
    
    @GetMapping("/stock/info/{code}")
    @Operation(summary = "获取单个股票信息", description = "根据股票代码获取股票详细信息")
    public ApiResponse<com.okx.trading.model.dto.StockInfo> getStockInfo(
            @Parameter(description = "股票代码，如000001.SZ") @PathVariable String code) {
        try {
            log.info("获取股票信息: code={}", code);
            
            // 从 Hash 缓存中获取单个股票信息
            String hashKey = "market:stock:hash";
            Object cachedValue = redisTemplate.opsForHash().get(hashKey, code);
            
            if (cachedValue != null) {
                String jsonString = cachedValue.toString();
                com.okx.trading.model.dto.StockInfo stock = 
                    JSON.parseObject(jsonString, com.okx.trading.model.dto.StockInfo.class);
                log.info("从缓存中获取股票信息: {}", code);
                return ApiResponse.success(stock);
            }
            
            // 如果缓存不存在，返回未找到
            log.warn("股票信息不存在: {}", code);
            return ApiResponse.error(404, "股票信息不存在");
        } catch (Exception e) {
            log.error("获取股票信息失败", e);
            return ApiResponse.error(500, "获取股票信息失败: " + e.getMessage());
        }
    }
}
