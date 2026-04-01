package com.okx.trading.service;

import com.okx.trading.model.market.Candlestick;
import com.okx.trading.model.market.Ticker;

import java.util.List;

/**
 * Tushare API服务接口
 * 用于获取A股市场数据
 */
public interface TushareApiService {

    /**
     * 获取K线数据（日线）
     *
     * @param tsCode    股票代码，如000001.SZ
     * @param startDate 开始日期，格式：YYYYMMDD
     * @param endDate   结束日期，格式：YYYYMMDD
     * @param limit     获取数据条数
     * @return K线数据列表
     */
    List<Candlestick> getDailyKlineData(String tsCode, String startDate, String endDate, Integer limit);

    /**
     * 获取分钟级K线数据
     *
     * @param tsCode    股票代码，如000001.SZ
     * @param freq      频率：1min, 5min, 15min, 30min, 60min
     * @param startDate 开始日期，格式：YYYY-MM-DD HH:MM:SS
     * @param endDate   结束日期，格式：YYYY-MM-DD HH:MM:SS
     * @param limit     获取数据条数
     * @return K线数据列表
     */
    List<Candlestick> getMinuteKlineData(String tsCode, String freq, String startDate, String endDate, Integer limit);

    /**
     * 获取最新行情数据
     *
     * @param tsCode 股票代码，如000001.SZ
     * @return 行情数据
     */
    Ticker getTicker(String tsCode);

    /**
     * 获取股票列表
     *
     * @param exchange 交易所：SSE-上交所, SZSE-深交所
     * @param listStatus 上市状态：L-上市, D-退市, P-暂停上市
     * @return 股票列表
     */
    List<String> getStockList(String exchange, String listStatus);
    
    /**
     * 获取股票信息列表（包含代码、名称等详细信息）
     *
     * @param exchange 交易所：SSE-上交所, SZSE-深交所, null-全部
     * @param listStatus 上市状态：L-上市, D-退市, P-暂停上市
     * @return 股票信息列表
     */
    List<com.okx.trading.model.dto.StockInfo> getStockInfoList(String exchange, String listStatus);

    /**
     * 获取历史K线数据（兼容原有接口）
     *
     * @param symbol    股票代码
     * @param interval  K线间隔：1D, 1W, 1M, 1min, 5min, 15min, 30min, 60min
     * @param startTime 开始时间戳（毫秒）
     * @param endTime   结束时间戳（毫秒）
     * @param limit     获取数据条数
     * @return K线数据列表
     */
    List<Candlestick> getHistoryKlineData(String symbol, String interval, Long startTime, Long endTime, Integer limit);

    /**
     * 测试API连接
     *
     * @return 是否连接成功
     */
    boolean testConnection();
}
