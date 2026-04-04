package com.okx.trading.service.impl;

import com.alibaba.fastjson.JSON;
import com.okx.trading.model.dto.StockInfo;
import com.okx.trading.model.market.Ticker;
import com.okx.trading.service.DataInitializationService;
import com.okx.trading.service.OkxApiService;
import com.okx.trading.service.TushareApiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 数据初始化服务实现
 * 在应用启动时自动获取并缓存股票列表和加密货币列表
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataInitializationServiceImpl implements DataInitializationService {

    private final TushareApiService tushareApiService;
    private final OkxApiService okxApiService;
    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * 缓存过期时间：7天（单位：秒）
     */
    private static final long CACHE_TIMEOUT_SECONDS = 7 * 24 * 60 * 60;

    /**
     * 股票列表缓存键（Hash结构）
     */
    private static final String STOCK_HASH_KEY = "market:stock:hash";

    /**
     * 加密货币列表缓存键（Hash结构）
     */
    private static final String CRYPTO_HASH_KEY = "market:crypto:hash";

    @Override
    public void initStockListCache() {
        try {
            log.info("开始初始化股票列表缓存...");

            // 获取所有上市股票（L-上市状态）
            List<StockInfo> stockList = tushareApiService.getStockInfoList(null, "L");

            if (stockList == null || stockList.isEmpty()) {
                log.warn("获取股票列表为空，跳过缓存");
                return;
            }

            // 使用 Hash 结构存储，key 为股票代码，value 为股票信息的 JSON 字符串
            for (StockInfo stock : stockList) {
                String stockCode = stock.getCode();
                String stockJson = JSON.toJSONString(stock);
                redisTemplate.opsForHash().put(STOCK_HASH_KEY, stockCode, stockJson);
            }

            // 设置过期时间
            redisTemplate.expire(STOCK_HASH_KEY, CACHE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            log.info("股票列表缓存初始化成功，共 {} 只股票，缓存时间：7天", stockList.size());

        } catch (Exception e) {
            log.error("初始化股票列表缓存失败: {}", e.getMessage(), e);
        }
    }

    @Override
    public void initCryptoListCache() {
        try {
            log.info("开始初始化加密货币列表缓存...");

            // 获取所有USDT交易对的行情数据
            List<Ticker> allTickers = okxApiService.getAllTickers();

            if (allTickers == null || allTickers.isEmpty()) {
                log.warn("获取加密货币列表为空，跳过缓存");
                return;
            }

            // 使用 Hash 结构存储，key 为交易对符号，value 为交易对信息的 JSON 字符串
            for (Ticker ticker : allTickers) {
                String symbol = ticker.getSymbol();
                String tickerJson = JSON.toJSONString(ticker);
                redisTemplate.opsForHash().put(CRYPTO_HASH_KEY, symbol, tickerJson);
            }

            // 设置过期时间
            redisTemplate.expire(CRYPTO_HASH_KEY, CACHE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            log.info("加密货币列表缓存初始化成功，共 {} 个交易对，缓存时间：7天", allTickers.size());

        } catch (Exception e) {
            log.error("初始化加密货币列表缓存失败: {}", e.getMessage(), e);
        }
    }

    @Override
    public void initAllDataCache() {
        log.info("开始初始化所有数据缓存...");

        // 初始化股票列表
        initStockListCache();

        // 初始化加密货币列表
        initCryptoListCache();

        log.info("所有数据缓存初始化完成");
    }
}
