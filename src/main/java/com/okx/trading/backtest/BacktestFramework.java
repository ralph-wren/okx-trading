package com.okx.trading.backtest;

import com.okx.trading.model.entity.CandlestickEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 回测框架基础类
 */
@Slf4j
public abstract class BacktestFramework {

    /**
     * 策略名称
     */
    @Getter
    protected String strategyName;

    /**
     * 初始资金
     */
    @Getter
    protected BigDecimal initialBalance;

    /**
     * 当前现金
     */
    @Getter
    protected BigDecimal cash;

    /**
     * 持仓数量
     */
    @Getter
    protected BigDecimal position;

    /**
     * 当前持仓价值
     */
    @Getter
    protected BigDecimal positionValue;

    /**
     * 手续费率
     */
    @Getter
    protected BigDecimal feeRate;

    /**
     * 交易记录
     */
    @Getter
    protected List<TradeRecord> tradeRecords = new ArrayList<>();

    /**
     * 账户净值记录
     */
    @Getter
    protected List<BalanceRecord> balanceRecords = new ArrayList<>();

    /**
     * 日期格式
     */
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 构造函数
     *
     * @param strategyName   策略名称
     * @param initialBalance 初始资金
     * @param feeRate        手续费率
     */
    public BacktestFramework(String strategyName, BigDecimal initialBalance, BigDecimal feeRate) {
        this.strategyName = strategyName;
        this.initialBalance = initialBalance;
        this.cash = initialBalance;
        this.position = BigDecimal.ZERO;
        this.positionValue = BigDecimal.ZERO;
        this.feeRate = feeRate;
    }

    /**
     * 买入
     *
     * @param time          时间
     * @param price         价格
     * @param amount        数量
     * @param reason        原因
     */
    protected void buy(LocalDateTime time, BigDecimal price, BigDecimal amount, String reason) {
        // 计算买入价值和手续费
        BigDecimal value = price.multiply(amount);
        BigDecimal fee = value.multiply(feeRate);

        // 检查是否有足够的现金
        if (cash.compareTo(value.add(fee)) < 0) {
            amount = cash.divide(price.multiply(BigDecimal.ONE.add(feeRate)), 8, RoundingMode.DOWN);
            value = price.multiply(amount);
            fee = value.multiply(feeRate);

            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                log.info("现金不足，无法买入: 时间={}, 价格={}, 可用现金={}",
                        time.format(DATE_FORMATTER), price, cash);
                return;
            }

            log.info("现金不足，调整买入数量: {} -> {}",
                    amount.divide(price.multiply(BigDecimal.ONE.add(feeRate)), 8, RoundingMode.DOWN), amount);
        }

        // 更新持仓和现金
        BigDecimal oldCash = cash;
        BigDecimal oldPosition = position;
        BigDecimal oldPositionValue = positionValue;
        BigDecimal oldTotalBalance = cash.add(positionValue);

        position = position.add(amount);
        cash = cash.subtract(value).subtract(fee);
        positionValue = position.multiply(price);
        BigDecimal newTotalBalance = cash.add(positionValue);

        // 记录交易
        TradeRecord trade = new TradeRecord();
        trade.setTime(time);
        trade.setType("买入");
        trade.setPrice(price);
        trade.setAmount(amount);
        trade.setValue(value);
        trade.setFee(fee);
        trade.setCash(cash);
        trade.setPosition(position);
        trade.setTotalBalance(newTotalBalance);
        trade.setReason(reason);

        tradeRecords.add(trade);

        log.info("买入成功: 时间={}, 价格={}, 数量={}, 价值={}, 手续费={}",
                time.format(DATE_FORMATTER), price, amount, value, fee);
        log.info("账户变动: 现金: {} -> {} (变化: {}), 持仓: {} -> {} (变化: {}), 持仓价值: {} -> {} (变化: {}), 总资产: {} -> {} (变化: {})",
                oldCash, cash, cash.subtract(oldCash),
                oldPosition, position, position.subtract(oldPosition),
                oldPositionValue, positionValue, positionValue.subtract(oldPositionValue),
                oldTotalBalance, newTotalBalance, newTotalBalance.subtract(oldTotalBalance));
        log.info("持仓情况: 数量={}, 成本={}, 当前价值={}, 当前现金={}, 总资产={}",
                position, value, positionValue, cash, newTotalBalance);
    }

    /**
     * 卖出
     *
     * @param time          时间
     * @param price         价格
     * @param amount        数量
     * @param reason        原因
     */
    protected void sell(LocalDateTime time, BigDecimal price, BigDecimal amount, String reason) {
        // 检查是否有足够的持仓
        if (position.compareTo(amount) < 0) {
            log.info("持仓不足，调整卖出数量: {} -> {}", amount, position);
            amount = position;
        }

        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            log.info("持仓为空，无法卖出: 时间={}, 价格={}",
                    time.format(DATE_FORMATTER), price);
            return;
        }

        // 计算卖出价值和手续费
        BigDecimal value = price.multiply(amount);
        BigDecimal fee = value.multiply(feeRate);

        // 更新前的账户状态
        BigDecimal oldCash = cash;
        BigDecimal oldPosition = position;
        BigDecimal oldPositionValue = positionValue;
        BigDecimal oldTotalBalance = cash.add(positionValue);

        // 更新持仓和现金
        position = position.subtract(amount);
        cash = cash.add(value).subtract(fee);
        positionValue = position.multiply(price);
        BigDecimal newTotalBalance = cash.add(positionValue);

        // 记录交易
        TradeRecord trade = new TradeRecord();
        trade.setTime(time);
        trade.setType("卖出");
        trade.setPrice(price);
        trade.setAmount(amount);
        trade.setValue(value);
        trade.setFee(fee);
        trade.setCash(cash);
        trade.setPosition(position);
        trade.setTotalBalance(newTotalBalance);
        trade.setReason(reason);

        tradeRecords.add(trade);

        log.info("卖出成功: 时间={}, 价格={}, 数量={}, 价值={}, 手续费={}",
                time.format(DATE_FORMATTER), price, amount, value, fee);
        log.info("账户变动: 现金: {} -> {} (变化: {}), 持仓: {} -> {} (变化: {}), 持仓价值: {} -> {} (变化: {}), 总资产: {} -> {} (变化: {})",
                oldCash, cash, cash.subtract(oldCash),
                oldPosition, position, position.subtract(oldPosition),
                oldPositionValue, positionValue, positionValue.subtract(oldPositionValue),
                oldTotalBalance, newTotalBalance, newTotalBalance.subtract(oldTotalBalance));
        log.info("持仓情况: 数量={}, 当前价值={}, 当前现金={}, 总资产={}",
                position, positionValue, cash, newTotalBalance);
    }

    /**
     * 记录账户净值
     *
     * @param time      时间
     * @param price     价格
     */
    protected void recordBalance(LocalDateTime time, BigDecimal price) {
        BigDecimal oldPositionValue = positionValue;
        positionValue = position.multiply(price);
        BigDecimal totalBalance = cash.add(positionValue);

        // 记录价格变动信息
        if (balanceRecords.size() > 0) {
            BalanceRecord lastRecord = balanceRecords.get(balanceRecords.size() - 1);
            BigDecimal lastPrice = lastRecord.getPrice();
            BigDecimal priceChange = price.subtract(lastPrice);
            BigDecimal priceChangePercent = BigDecimal.ZERO;
            if (lastPrice.compareTo(BigDecimal.ZERO) > 0) {
                priceChangePercent = priceChange.divide(lastPrice, 6, RoundingMode.HALF_UP).multiply(new BigDecimal("100"));
            }

            if (priceChange.compareTo(BigDecimal.ZERO) != 0) {
                log.debug("价格变动: {} -> {} (变化: {}, {}%), 持仓价值变化: {} -> {} (变化: {})",
                        lastPrice, price, priceChange, priceChangePercent,
                        oldPositionValue, positionValue, positionValue.subtract(oldPositionValue));
            }
        }

        BalanceRecord record = new BalanceRecord();
        record.setTime(time);
        record.setCash(cash);
        record.setPosition(position);
        record.setPrice(price);
        record.setPositionValue(positionValue);
        record.setTotalBalance(totalBalance);

        balanceRecords.add(record);
    }

    /**
     * 计算策略性能指标
     */
    protected void calculatePerformance() {
        if (balanceRecords.isEmpty()) {
            log.info("没有账户记录，无法计算性能");
            return;
        }

        BalanceRecord first = balanceRecords.get(0);
        BalanceRecord last = balanceRecords.get(balanceRecords.size() - 1);

        BigDecimal totalReturn = last.getTotalBalance().subtract(initialBalance);
        BigDecimal returnRate = totalReturn.divide(initialBalance, 4, RoundingMode.HALF_UP).multiply(new BigDecimal("100"));

        // 计算最大回撤
        BigDecimal maxDrawdown = calculateMaxDrawdown();

        log.info("====== 策略 {} 回测结果 ======", strategyName);
        log.info("初始资金: {}", initialBalance);
        log.info("结束资金: {}", last.getTotalBalance());
        log.info("总收益: {} ({}%)", totalReturn, returnRate);
        log.info("最大回撤: {}%", maxDrawdown);
        log.info("交易次数: {}", tradeRecords.size());

        // 打印持仓信息
        log.info("最终持仓: {} 单位, 价值: {}", last.getPosition(), last.getPositionValue());
        log.info("最终现金: {}", last.getCash());

        // 打印详细交易记录
        log.info("====== 交易记录 ======");
        for (TradeRecord trade : tradeRecords) {
            log.info("{} | {} | 价格: {} | 数量: {} | 价值: {} | 手续费: {} | 原因: {}",
                    trade.getTime().format(DATE_FORMATTER),
                    trade.getType(),
                    trade.getPrice(),
                    trade.getAmount(),
                    trade.getValue(),
                    trade.getFee(),
                    trade.getReason());
        }
    }

    /**
     * 计算最大回撤
     *
     * @return 最大回撤百分比
     */
    private BigDecimal calculateMaxDrawdown() {
        if (balanceRecords.size() < 2) {
            return BigDecimal.ZERO;
        }

        BigDecimal maxDrawdown = BigDecimal.ZERO;
        BigDecimal highWaterMark = balanceRecords.get(0).getTotalBalance();

        for (BalanceRecord record : balanceRecords) {
            BigDecimal currentBalance = record.getTotalBalance();

            // 更新最高水位线
            if (currentBalance.compareTo(highWaterMark) > 0) {
                highWaterMark = currentBalance;
            }
            // 计算当前回撤
            else if (highWaterMark.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal drawdown = BigDecimal.ONE.subtract(currentBalance.divide(highWaterMark, 6, RoundingMode.HALF_UP))
                        .multiply(new BigDecimal("100"));

                // 更新最大回撤
                if (drawdown.compareTo(maxDrawdown) > 0) {
                    maxDrawdown = drawdown;
                }
            }
        }

        return maxDrawdown.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 运行回测
     *
     * @param candlesticks K线数据
     */
    public abstract void runBacktest(List<CandlestickEntity> candlesticks);

    /**
     * 交易记录
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TradeRecord {
        private LocalDateTime time;
        private String type;
        private BigDecimal price;
        private BigDecimal amount;
        private BigDecimal value;
        private BigDecimal fee;
        private BigDecimal cash;
        private BigDecimal position;
        private BigDecimal totalBalance;
        private String reason;
    }

    /**
     * 账户净值记录
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BalanceRecord {
        private LocalDateTime time;
        private BigDecimal cash;
        private BigDecimal position;
        private BigDecimal price;
        private BigDecimal positionValue;
        private BigDecimal totalBalance;
    }
}
