-- 完整的数据库初始化脚本
-- 包含所有实体类对应的表结构

CREATE DATABASE IF NOT EXISTS okx_trading DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE okx_trading;

-- 1. K线历史数据表
CREATE TABLE IF NOT EXISTS `candlestick_history` (
  `id` BIGINT(20) NOT NULL AUTO_INCREMENT,
  `symbol` VARCHAR(20) NOT NULL COMMENT '交易对，如BTC-USDT',
  `interval_val` VARCHAR(10) NOT NULL COMMENT 'K线间隔',
  `open_time` DATETIME NOT NULL COMMENT '开盘时间',
  `close_time` DATETIME COMMENT '收盘时间',
  `open` DECIMAL(30, 15) COMMENT '开盘价',
  `high` DECIMAL(30, 15) COMMENT '最高价',
  `low` DECIMAL(30, 15) COMMENT '最低价',
  `close` DECIMAL(30, 15) COMMENT '收盘价',
  `volume` DECIMAL(30, 15) COMMENT '成交量',
  `quote_volume` DECIMAL(30, 15) COMMENT '成交额',
  `trades` BIGINT(20) COMMENT '成交笔数',
  `fetch_time` DATETIME COMMENT '数据获取时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_symbol_interval_opentime` (`symbol`, `interval_val`, `open_time`),
  INDEX `idx_symbol_interval` (`symbol`, `interval_val`),
  INDEX `idx_opentime` (`open_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='K线历史数据';

-- 2. 实时策略表
CREATE TABLE IF NOT EXISTS `real_time_strategy` (
    `id` BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,
    `strategy_code` VARCHAR(50) NOT NULL UNIQUE,
    `symbol` VARCHAR(20) NOT NULL,
    `interval_val` VARCHAR(10) NOT NULL,
    `start_time` DATETIME NOT NULL,
    `trade_amount` DOUBLE,
    `is_active` BOOLEAN NOT NULL DEFAULT TRUE,
    `status` VARCHAR(20) DEFAULT 'STOPPED',
    `error_message` TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
    `create_time` DATETIME NOT NULL,
    `update_time` DATETIME NOT NULL,
    INDEX `idx_strategy_code` (`strategy_code`),
    INDEX `idx_symbol` (`symbol`),
    INDEX `idx_status` (`status`),
    INDEX `idx_is_active` (`is_active`),
    INDEX `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='实时运行策略表';

-- 3. 策略信息表
CREATE TABLE IF NOT EXISTS `strategy_info` (
    `id` BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,
    `strategy_code` VARCHAR(50) NOT NULL UNIQUE,
    `strategy_name` VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
    `description` TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
    `comments` TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
    `params_desc` TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
    `default_params` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
    `category` VARCHAR(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
    `source_code` TEXT,
    `load_error` TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
    `create_time` DATETIME NOT NULL,
    `update_time` DATETIME NOT NULL,
    INDEX `idx_strategy_code` (`strategy_code`),
    INDEX `idx_category` (`category`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='策略信息表';

-- 4. 策略对话记录表
CREATE TABLE IF NOT EXISTS `strategy_conversation` (
    `id` BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,
    `strategy_id` BIGINT NOT NULL,
    `user_input` TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
    `ai_response` TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
    `conversation_type` VARCHAR(20) NOT NULL,
    `compile_error` TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
    `create_time` DATETIME NOT NULL,
    INDEX `idx_strategy_id` (`strategy_id`),
    INDEX `idx_conversation_type` (`conversation_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='策略对话记录表';

-- 5. 回测汇总表
CREATE TABLE IF NOT EXISTS `backtest_summary` (
    `id` BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,
    `backtest_id` VARCHAR(50) NOT NULL UNIQUE,
    `batch_backtest_id` VARCHAR(50),
    `strategy_name` VARCHAR(100) NOT NULL,
    `strategy_code` VARCHAR(50) NOT NULL,
    `strategy_params` TEXT,
    `symbol` VARCHAR(20) NOT NULL,
    `interval_val` VARCHAR(10) NOT NULL,
    `start_time` DATETIME,
    `end_time` DATETIME,
    `initial_amount` DECIMAL(20, 8) NOT NULL,
    `final_amount` DECIMAL(20, 8),
    `total_profit` DECIMAL(20, 8),
    `total_return` DECIMAL(10, 4),
    `annualized_return` DECIMAL(10, 4),
    `number_of_trades` INT,
    `profitable_trades` INT,
    `unprofitable_trades` INT,
    `win_rate` DECIMAL(10, 4),
    `average_profit` DECIMAL(10, 4),
    `max_drawdown` DECIMAL(10, 4),
    `max_drawdown_period` DECIMAL(10, 4),
    `sharpe_ratio` DECIMAL(10, 4),
    `sortino_ratio` DECIMAL(10, 4),
    `calmar_ratio` DECIMAL(10, 4),
    `maximum_loss` DECIMAL(20, 8),
    `maximum_loss_period` DECIMAL(20, 8),
    `volatility` DECIMAL(10, 4),
    `total_fee` DECIMAL(20, 8),
    `omega` DECIMAL(10, 4),
    `alpha` DECIMAL(10, 4),
    `beta` DECIMAL(10, 4),
    `treynor_ratio` DECIMAL(10, 4),
    `ulcer_index` DECIMAL(10, 4),
    `skewness` DECIMAL(10, 4),
    `profit_factor` DECIMAL(10, 4),
    `comprehensive_score` DECIMAL(3, 2),
    `kurtosis` DECIMAL(10, 4),
    `cvar` DECIMAL(10, 4),
    `var95` DECIMAL(10, 4),
    `var99` DECIMAL(10, 4),
    `information_ratio` DECIMAL(10, 4),
    `tracking_error` DECIMAL(10, 4),
    `sterling_ratio` DECIMAL(10, 4),
    `burke_ratio` DECIMAL(10, 4),
    `modified_sharpe_ratio` DECIMAL(10, 4),
    `downside_deviation` DECIMAL(10, 4),
    `uptrend_capture` DECIMAL(10, 4),
    `downtrend_capture` DECIMAL(10, 4),
    `max_drawdown_duration` DECIMAL(10, 2),
    `pain_index` DECIMAL(10, 4),
    `risk_adjusted_return` DECIMAL(10, 4),
    `is_real` INT DEFAULT -1,
    `create_time` DATETIME,
    INDEX `idx_backtest_id` (`backtest_id`),
    INDEX `idx_batch_backtest_id` (`batch_backtest_id`),
    INDEX `idx_strategy_code` (`strategy_code`),
    INDEX `idx_symbol` (`symbol`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='回测汇总表';

-- 6. 回测交易详情表
CREATE TABLE IF NOT EXISTS `backtest_trade` (
    `id` BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,
    `backtest_id` VARCHAR(50) NOT NULL,
    `strategy_name` VARCHAR(100) NOT NULL,
    `strategy_code` VARCHAR(50) NOT NULL,
    `strategy_params` TEXT,
    `trade_index` INT,
    `trade_type` VARCHAR(20) NOT NULL,
    `symbol` VARCHAR(20) NOT NULL,
    `entry_time` DATETIME,
    `entry_price` DECIMAL(20, 8),
    `entry_amount` DECIMAL(20, 8),
    `entry_position_percentage` DECIMAL(10, 4),
    `exit_time` DATETIME,
    `exit_price` DECIMAL(20, 8),
    `exit_amount` DECIMAL(20, 8),
    `profit` DECIMAL(20, 8),
    `profit_percentage` DECIMAL(10, 4),
    `periods` DECIMAL(10, 4),
    `profit_percentage_per_period` DECIMAL(10, 4),
    `total_assets` DECIMAL(20, 8),
    `max_drawdown` DECIMAL(10, 4),
    `max_loss` DECIMAL(10, 4),
    `max_drawdown_period` DECIMAL(10, 4),
    `max_loss_period` DECIMAL(10, 4),
    `closed` BOOLEAN,
    `volume` DECIMAL(20, 8),
    `fee` DECIMAL(20, 8),
    `remark` VARCHAR(500),
    `create_time` DATETIME,
    INDEX `idx_backtest_id` (`backtest_id`),
    INDEX `idx_strategy_code` (`strategy_code`),
    INDEX `idx_symbol` (`symbol`),
    INDEX `idx_entry_time` (`entry_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='回测交易详情表';

-- 7. 回测资金曲线表
CREATE TABLE IF NOT EXISTS `backtest_equity_curve` (
    `id` BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,
    `backtest_id` VARCHAR(50) NOT NULL,
    `timestamp` DATETIME NOT NULL,
    `equity_value` DECIMAL(20, 8) NOT NULL,
    `index_position` INT,
    INDEX `idx_backtest_id` (`backtest_id`),
    INDEX `idx_timestamp` (`timestamp`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='回测资金曲线表';

-- 8. 资金中心数据表
CREATE TABLE IF NOT EXISTS `fund_data` (
    `id` BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,
    `total_investment` DECIMAL(20, 8) NOT NULL,
    `total_profit` DECIMAL(20, 8) NOT NULL,
    `total_fund` DECIMAL(20, 8) NOT NULL,
    `record_time` DATETIME NOT NULL,
    INDEX `idx_record_time` (`record_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='资金中心数据表';
