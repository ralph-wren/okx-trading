-- 为 strategy_conversation 表添加 original_code 字段
-- 用于存储 AI 返回的原始策略代码（保持换行格式）

ALTER TABLE strategy_conversation 
ADD COLUMN original_code TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT 'AI返回的原始策略代码（保持换行格式，方便阅读）' 
AFTER compile_error;

-- 添加索引以提高查询性能
CREATE INDEX idx_strategy_conversation_strategy_id ON strategy_conversation(strategy_id);
CREATE INDEX idx_strategy_conversation_create_time ON strategy_conversation(create_time);
