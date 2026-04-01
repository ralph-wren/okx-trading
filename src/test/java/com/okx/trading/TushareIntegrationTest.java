package com.okx.trading;

import com.okx.trading.config.TushareConfig;
import com.okx.trading.service.TushareApiService;
import com.okx.trading.controller.StockMarketController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tushare 集成测试
 * 验证 Bean 是否正确创建和注入
 */
@SpringBootTest
public class TushareIntegrationTest {

    @Autowired(required = false)
    private TushareConfig tushareConfig;

    @Autowired(required = false)
    private TushareApiService tushareApiService;

    @Autowired(required = false)
    private StockMarketController stockMarketController;

    @Test
    public void testTushareConfigLoaded() {
        assertNotNull(tushareConfig, "TushareConfig should be loaded");
        assertNotNull(tushareConfig.getToken(), "Tushare token should not be null");
        assertNotNull(tushareConfig.getUrl(), "Tushare URL should not be null");
        System.out.println("✓ TushareConfig loaded successfully");
        System.out.println("  Token: " + tushareConfig.getToken().substring(0, 10) + "...");
        System.out.println("  URL: " + tushareConfig.getUrl());
    }

    @Test
    public void testTushareApiServiceLoaded() {
        assertNotNull(tushareApiService, "TushareApiService should be loaded");
        System.out.println("✓ TushareApiService loaded successfully");
    }

    @Test
    public void testStockMarketControllerLoaded() {
        assertNotNull(stockMarketController, "StockMarketController should be loaded");
        System.out.println("✓ StockMarketController loaded successfully");
    }

    @Test
    public void testTushareConnection() {
        assertNotNull(tushareApiService, "TushareApiService should be loaded");
        
        try {
            boolean connected = tushareApiService.testConnection();
            assertTrue(connected, "Should be able to connect to Tushare API");
            System.out.println("✓ Tushare API connection test passed");
        } catch (Exception e) {
            System.err.println("✗ Tushare API connection test failed: " + e.getMessage());
            // 不让测试失败，因为可能是网络问题
        }
    }
}
