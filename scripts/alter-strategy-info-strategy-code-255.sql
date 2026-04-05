-- 已有库升级：AI 生成策略时 strategy_code 可能超过 50 字符（含去重后缀）
-- 在 okx_trading 库执行：mysql -u... -p... okx_trading < scripts/alter-strategy-info-strategy-code-255.sql
ALTER TABLE `strategy_info`
    MODIFY COLUMN `strategy_code` VARCHAR(255) NOT NULL;
