package com.okx.trading.util;

import com.okx.trading.model.dto.BacktestResultDTO;
import com.okx.trading.model.dto.TradeRecordDTO;
import com.okx.trading.model.entity.BacktestSummaryEntity;
import com.okx.trading.model.entity.BacktestTradeEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 回测结果打印工具类
 * 提供各种格式化输出回测结果的方法
 */
public class BacktestResultPrinter {
    
    private static final Logger log = LoggerFactory.getLogger(BacktestResultPrinter.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    /**
     * 打印回测结果汇总信息
     * 
     * @param result 回测结果DTO
     */
    public static void printSummary(BacktestResultDTO result) {
        if (result == null) {
            log.warn("回测结果为空，无法打印汇总信息");
            return;
        }
        
        StringBuilder sb = new StringBuilder();
        String separator = "================================================================";
        
        sb.append("\n").append(separator).append("\n");
        sb.append("==================== 回测汇总信息 ====================\n");
        sb.append(separator).append("\n");
        
        // 策略信息
        sb.append("策略名称: ").append(result.getStrategyName()).append("\n");
        sb.append("策略参数: ").append(result.getParameterDescription()).append("\n");
        sb.append("------------------------------------------------------\n");
        
        // 财务指标
        String initialAmountFormatted = String.format("%,.2f", result.getInitialAmount());
        String finalAmountFormatted = String.format("%,.2f", result.getFinalAmount());
        String totalProfitFormatted = String.format("%,.2f", result.getTotalProfit());
        String totalReturnFormatted = String.format("%.2f%%", result.getTotalReturn().multiply(new BigDecimal("100")));
        
        sb.append("初始资金: ").append(initialAmountFormatted).append("\n");
        sb.append("最终资金: ").append(finalAmountFormatted).append("\n");
        sb.append("总盈亏: ").append(totalProfitFormatted).append("\n");
        sb.append("总收益率: ").append(totalReturnFormatted).append("\n");
        sb.append("------------------------------------------------------\n");
        
        // 交易指标
        String winRateFormatted = String.format("%.2f%%", result.getWinRate().multiply(new BigDecimal("100")));
        String maxDrawdownFormatted = String.format("%.2f%%", result.getMaxDrawdown().multiply(new BigDecimal("100")));
        
        sb.append("交易次数: ").append(result.getNumberOfTrades()).append("\n");
        sb.append("盈利交易: ").append(result.getProfitableTrades()).append("\n");
        sb.append("亏损交易: ").append(result.getUnprofitableTrades()).append("\n");
        sb.append("胜率: ").append(winRateFormatted).append("\n");
        sb.append("夏普比率: ").append(String.format("%.4f", result.getSharpeRatio())).append("\n");
        sb.append("最大回撤: ").append(maxDrawdownFormatted).append("\n");
        sb.append(separator).append("\n");
        
        log.info(sb.toString());
    }
    
    /**
     * 打印回测汇总实体信息
     * 
     * @param entity 回测汇总实体
     */
    public static void printSummaryEntity(BacktestSummaryEntity entity) {
        if (entity == null) {
            log.warn("回测汇总实体为空，无法打印汇总信息");
            return;
        }
        
        StringBuilder sb = new StringBuilder();
        String separator = "================================================================";
        
        sb.append("\n").append(separator).append("\n");
        sb.append("==================== 回测汇总信息 ====================\n");
        sb.append(separator).append("\n");
        
        // 基本信息
        sb.append("回测ID: ").append(entity.getId()).append("\n");
        sb.append("交易对: ").append(entity.getSymbol()).append("\n");
        sb.append("时间间隔: ").append(entity.getIntervalVal()).append("\n");
        sb.append("策略名称: ").append(entity.getStrategyName()).append("\n");
        sb.append("策略参数: ").append(entity.getStrategyParams()).append("\n");
        
        if (entity.getStartTime() != null && entity.getEndTime() != null) {
            sb.append("回测时间范围: ").append(entity.getStartTime().format(DATE_FORMATTER))
              .append(" 至 ").append(entity.getEndTime().format(DATE_FORMATTER)).append("\n");
        }
        
        sb.append("------------------------------------------------------\n");
        
        // 财务指标
        String initialAmountFormatted = String.format("%,.2f", entity.getInitialAmount());
        String finalAmountFormatted = String.format("%,.2f", entity.getFinalAmount());
        String totalProfitFormatted = String.format("%,.2f", entity.getTotalProfit());
        String totalReturnFormatted = String.format("%.2f%%", entity.getTotalReturn().multiply(new BigDecimal("100")));
        
        sb.append("初始资金: ").append(initialAmountFormatted).append("\n");
        sb.append("最终资金: ").append(finalAmountFormatted).append("\n");
        sb.append("总盈亏: ").append(totalProfitFormatted).append("\n");
        sb.append("总收益率: ").append(totalReturnFormatted).append("\n");
        sb.append("------------------------------------------------------\n");
        
        // 交易指标
        String winRateFormatted = String.format("%.2f%%", entity.getWinRate().multiply(new BigDecimal("100")));
        String maxDrawdownFormatted = String.format("%.2f%%", entity.getMaxDrawdown().multiply(new BigDecimal("100")));
        
        sb.append("交易次数: ").append(entity.getNumberOfTrades()).append("\n");
        sb.append("盈利交易: ").append(entity.getProfitableTrades()).append("\n");
        sb.append("亏损交易: ").append(entity.getUnprofitableTrades()).append("\n");
        sb.append("胜率: ").append(winRateFormatted).append("\n");
        sb.append("夏普比率: ").append(String.format("%.4f", entity.getSharpeRatio())).append("\n");
        sb.append("最大回撤: ").append(maxDrawdownFormatted).append("\n");
        sb.append(separator).append("\n");
        
        log.info(sb.toString());
    }
    
    /**
     * 打印交易记录详情
     * 
     * @param trades 交易记录列表
     */
    public static void printTradeDetails(List<TradeRecordDTO> trades) {
        if (trades == null || trades.isEmpty()) {
            log.warn("交易记录为空，无法打印详情");
            return;
        }
        
        StringBuilder sb = new StringBuilder();
        String separator = "================================================================";
        
        sb.append("\n").append(separator).append("\n");
        sb.append("==================== 交易明细记录 ====================\n");
        sb.append(separator).append("\n");
        
        // 表头
        sb.append(String.format("%-4s | %-10s | %-19s | %-12s | %-19s | %-12s | %-12s | %-9s | %s\n",
                "序号", "类型", "入场时间", "入场价格", "出场时间", "出场价格", "盈亏金额", "盈亏比例", "状态"));
        
        sb.append("----------------------------------------------------------------\n");
        
        // 内容
        for (TradeRecordDTO trade : trades) {
            String entryTime = trade.getEntryTime() != null ? trade.getEntryTime().format(DATE_FORMATTER) : "-";
            String exitTime = trade.getExitTime() != null ? trade.getExitTime().format(DATE_FORMATTER) : "-";
            
            String profitFormatted = trade.getProfit() != null ? String.format("%,.2f", trade.getProfit()) : "-";
            String profitPercentageFormatted = trade.getProfitPercentage() != null ? 
                    String.format("%.2f%%", trade.getProfitPercentage().multiply(new BigDecimal("100"))) : "-";
            
            sb.append(String.format("%-4d | %-10s | %-19s | %-12s | %-19s | %-12s | %-12s | %-9s | %s\n",
                    trade.getIndex(),
                    trade.getType(),
                    entryTime,
                    trade.getEntryPrice(),
                    exitTime,
                    trade.getExitPrice(),
                    profitFormatted,
                    profitPercentageFormatted,
                    trade.isClosed() ? "已平仓" : "持仓中"));
        }
        
        sb.append(separator).append("\n");
        
        log.info(sb.toString());
    }
    
    /**
     * 完整打印回测结果（包括汇总和交易详情）
     * 
     * @param result 回测结果
     */
    public static void printFullResult(BacktestResultDTO result) {
        printSummary(result);
        printTradeDetails(result.getTrades());
    }
    
    /**
     * 打印回测汇总摘要（简短格式）
     * 
     * @param summary 回测汇总实体
     */
    public static void printSummaryBrief(BacktestSummaryEntity summary) {
        if (summary == null) {
            log.warn("回测汇总实体为空，无法打印");
            return;
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("\n----- 回测结果摘要 -----\n");
        sb.append("策略: ").append(summary.getStrategyName()).append(", ");
        sb.append("交易对: ").append(summary.getSymbol()).append(", ");
        sb.append("周期: ").append(summary.getIntervalVal()).append("\n");
        
        sb.append("初始资金: ").append(String.format("%,.2f", summary.getInitialAmount()));
        sb.append(" → 最终资金: ").append(String.format("%,.2f", summary.getFinalAmount()));
        sb.append(" (收益率: ").append(String.format("%.2f%%", summary.getTotalReturn().multiply(new BigDecimal("100")))).append(")\n");
        
        sb.append("交易次数: ").append(summary.getNumberOfTrades());
        sb.append(", 胜率: ").append(String.format("%.2f%%", summary.getWinRate().multiply(new BigDecimal("100"))));
        sb.append(", 最大回撤: ").append(String.format("%.2f%%", summary.getMaxDrawdown().multiply(new BigDecimal("100"))));
        if (summary.getSharpeRatio() != null) {
            sb.append(", 夏普比率: ").append(String.format("%.2f", summary.getSharpeRatio()));
        }
        sb.append("\n");
        
        log.info(sb.toString());
    }
    
    /**
     * 打印回测交易记录列表
     * 
     * @param backtestId 回测ID
     * @param trades 交易记录列表
     */
    public static void printTradeRecords(String backtestId, List<BacktestTradeEntity> trades) {
        if (trades == null || trades.isEmpty()) {
            log.warn("交易记录列表为空，无法打印");
            return;
        }
        
        StringBuilder sb = new StringBuilder();
        String separator = "==================================================================";
        
        sb.append("\n").append(separator).append("\n");
        sb.append("====== 回测ID: ").append(backtestId).append(" 的交易记录 ======\n");
        sb.append(separator).append("\n");
        
        // 表头
        sb.append(String.format("%-5s | %-10s | %-19s | %-10s | %-19s | %-10s | %-10s | %-8s | %s\n",
                "序号", "类型", "入场时间", "入场价格", "出场时间", "出场价格", "盈亏金额", "盈亏率", "状态"));
        
        sb.append("-----------------------------------------------------------------\n");
        
        // 内容
        for (BacktestTradeEntity trade : trades) {
            String entryTime = trade.getEntryTime() != null ? trade.getEntryTime().format(DATE_FORMATTER) : "-";
            String exitTime = trade.getExitTime() != null ? trade.getExitTime().format(DATE_FORMATTER) : "-";
            
            String profitFormatted = trade.getProfit() != null ? String.format("%,.2f", trade.getProfit()) : "-";
            String profitPercentageFormatted = trade.getProfitPercentage() != null ? 
                    String.format("%.2f%%", trade.getProfitPercentage().multiply(new BigDecimal("100"))) : "-";
            
            sb.append(String.format("%-5d | %-10s | %-19s | %-10s | %-19s | %-10s | %-10s | %-8s | %s\n",
                    trade.getIndex(),
                    trade.getType(),
                    entryTime,
                    trade.getEntryPrice(),
                    exitTime,
                    trade.getExitPrice(),
                    profitFormatted,
                    profitPercentageFormatted,
                    trade.getClosed() ? "已平仓" : "持仓中"));
        }
        
        sb.append(separator).append("\n");
        
        log.info(sb.toString());
    }
} 