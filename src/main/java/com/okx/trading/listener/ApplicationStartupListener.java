package com.okx.trading.listener;

import com.okx.trading.service.DataInitializationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 应用启动监听器
 * 在应用启动完成后执行数据初始化任务
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApplicationStartupListener {

    private final DataInitializationService dataInitializationService;

    /**
     * 应用启动完成后的回调
     * 初始化股票列表和加密货币列表缓存
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        log.info("应用启动完成，开始初始化数据缓存...");
        
        try {
            // 初始化所有数据缓存（股票列表和加密货币列表）
            dataInitializationService.initAllDataCache();
            
            log.info("数据缓存初始化完成");
        } catch (Exception e) {
            log.error("数据缓存初始化失败", e);
        }
    }
}
