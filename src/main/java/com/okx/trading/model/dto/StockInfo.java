package com.okx.trading.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 股票基本信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockInfo {
    /**
     * 股票代码（带后缀），如 000001.SZ
     */
    private String code;
    
    /**
     * 股票名称
     */
    private String name;
    
    /**
     * 所属板块：main-主板, chinext-创业板, star-科创板, bse-北交所
     */
    private String market;
    
    /**
     * 交易所：SSE-上交所, SZSE-深交所
     */
    private String exchange;
    
    /**
     * 所属行业
     */
    private String industry;
    
    /**
     * 上市日期
     */
    private String listDate;
}
