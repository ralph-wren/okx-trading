package com.okx.trading.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 策略对话记录实体
 * 用于保存策略更新时与AI的对话历史记录
 */
@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "strategy_conversation")
public class StrategyConversationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 关联的策略ID
     */
    @Column(name = "strategy_id", nullable = false)
    private Long strategyId;

    /**
     * 用户输入的描述
     */
    @Column(name = "user_input", columnDefinition = "TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci")
    private String userInput;

    /**
     * AI返回的完整响应
     */
    @Column(name = "ai_response", columnDefinition = "TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci")
    private String aiResponse;

    /**
     * 对话类型：generate(生成) 或 update(更新)
     */
    @Column(name = "conversation_type", nullable = false)
    private String conversationType;

    /**
     * 策略编译错误信息
     */
    @Column(name = "compile_error", columnDefinition = "TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci")
    private String compileError;

    /**
     * AI返回的原始策略代码（保持换行格式，方便阅读）
     */
    @Column(name = "original_code", columnDefinition = "TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci")
    private String originalCode;

    /**
     * 创建时间
     */
    @Column(name = "create_time", nullable = false, updatable = false)
    private LocalDateTime createTime;

    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
    }
}