package com.okx.trading.service;

/**
 * 数据初始化服务接口
 * 用于应用启动时初始化缓存数据
 */
public interface DataInitializationService {

    /**
     * 初始化股票列表缓存
     * 获取所有上市股票并缓存7天
     */
    void initStockListCache();

    /**
     * 初始化加密货币列表缓存
     * 获取所有USDT交易对并缓存7天
     */
    void initCryptoListCache();

    /**
     * 初始化所有数据缓存
     * 包括股票列表和加密货币列表
     */
    void initAllDataCache();
}
