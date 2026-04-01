package com.okx.trading.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

/**
 * Tushare API配置
 */
@Configuration
@Component
@ConfigurationProperties(prefix = "tushare.api")
@Data
public class TushareConfig {
    
    /**
     * Tushare API Token
     */
    private String token = "krMJzeduYQNpQxOMToDCwfZGqmATGWnSalvyjEjbzJZuLZEvpMpEiRSEDKkPBFiu";
    
    /**
     * Tushare API URL
     */
    private String url = "http://111.170.34.57:8010/";
    
    /**
     * 是否启用代理
     */
    private boolean proxyEnabled = false;
    
    /**
     * 代理主机
     */
    private String proxyHost;
    
    /**
     * 代理端口
     */
    private Integer proxyPort;
    
    /**
     * 请求超时时间（秒）
     */
    private int timeout = 30;
}
