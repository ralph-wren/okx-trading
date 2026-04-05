package com.okx.trading.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 策略信息实体
 * 用于保存交易策略的详细信息，包括策略代码、名称、描述、参数说明、默认参数和分类等
 */
@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "strategy_info")
public class StrategyInfoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 策略唯一标识（可与回测表 strategy_code 关联）；AI 生成 + 去重后缀可能较长，需与库列长度一致
     */
    @Column(name = "strategy_code", nullable = false, unique = true,
            columnDefinition = "VARCHAR(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci")
    private String strategyCode;

    /**
     * 策略名称，如简单移动平均线策略
     */
    @Column(name = "strategy_name", nullable = false, columnDefinition = "VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci")
    private String strategyName;

    /**
     * 策略描述
     */
    @Column(name = "description", columnDefinition = "TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci")
    private String description;

    /**
     * 策略描述
     */
    @Column(name = "comments", columnDefinition = "TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci")
    private String comments;
    /**
     * 参数说明
     */
    @Column(name = "params_desc", columnDefinition = "TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci")
    private String paramsDesc;

    /**
     * 默认参数值
     */
    @Column(name = "default_params", columnDefinition = "VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci")
    private String defaultParams;

    /**
     * 策略分类，如移动平均线、震荡指标等
     */
    @Column(name = "category", columnDefinition = "VARCHAR(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci")
    private String category;

    /**
     * 策略源代码，存储lambda函数的序列化字符串
     */
    @Column(name = "source_code", columnDefinition = "TEXT")
    private String sourceCode;

    /**
     * 策略加载错误信息
     */
    @Column(name = "load_error", columnDefinition = "TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci")
    private String loadError;

    /**
     * 创建时间
     */
    @Column(name = "create_time", nullable = false, updatable = false)
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @Column(name = "update_time", nullable = false)
    private LocalDateTime updateTime;

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        this.createTime = now;
        this.updateTime = now;
    }

    @PreUpdate
    public void preUpdate() {
        this.updateTime = LocalDateTime.now();
    }
}
