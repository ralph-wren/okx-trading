package com.okx.trading.service.impl;


import com.okx.trading.model.entity.BacktestSummaryEntity;
import com.okx.trading.model.entity.IndicatorDistributionEntity;
import com.okx.trading.repository.BacktestSummaryRepository;
import com.okx.trading.repository.IndicatorDistributionRepository;
import com.okx.trading.service.IndicatorDistributionService;
import com.okx.trading.util.MapUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 指标分布服务实现类
 * 基于历史回测数据动态计算指标分布，提供数据驱动的评分机制
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IndicatorDistributionServiceImpl implements IndicatorDistributionService {

    private final BacktestSummaryRepository backtestSummaryRepository;
    private final IndicatorDistributionRepository indicatorDistributionRepository;

    /**
     * 指标配置：指标名称 -> 指标类型
     */
    private static final Map<String, IndicatorDistributionEntity.IndicatorType> INDICATOR_CONFIGS = MapUtils.of(
            // 收益类指标（越大越好）
            "totalReturn", IndicatorDistributionEntity.IndicatorType.POSITIVE,
            "annualizedReturn", IndicatorDistributionEntity.IndicatorType.POSITIVE,
            "averageProfit", IndicatorDistributionEntity.IndicatorType.POSITIVE,
            "riskAdjustedReturn", IndicatorDistributionEntity.IndicatorType.POSITIVE,

            // 风险调整收益比率（越大越好）
            "sharpeRatio", IndicatorDistributionEntity.IndicatorType.POSITIVE,
            "sortinoRatio", IndicatorDistributionEntity.IndicatorType.POSITIVE,
            "calmarRatio", IndicatorDistributionEntity.IndicatorType.POSITIVE,
            "treynorRatio", IndicatorDistributionEntity.IndicatorType.POSITIVE,
            "informationRatio", IndicatorDistributionEntity.IndicatorType.POSITIVE,
            "sterlingRatio", IndicatorDistributionEntity.IndicatorType.POSITIVE,
            "burkeRatio", IndicatorDistributionEntity.IndicatorType.POSITIVE,
            "modifiedSharpeRatio", IndicatorDistributionEntity.IndicatorType.POSITIVE,
            "omega", IndicatorDistributionEntity.IndicatorType.POSITIVE,

            // 风险指标（越小越好）
            "maxDrawdown", IndicatorDistributionEntity.IndicatorType.NEGATIVE,
            "maximumLoss", IndicatorDistributionEntity.IndicatorType.NEGATIVE,
            "volatility", IndicatorDistributionEntity.IndicatorType.NEGATIVE,
            "ulcerIndex", IndicatorDistributionEntity.IndicatorType.NEGATIVE,
            "painIndex", IndicatorDistributionEntity.IndicatorType.NEGATIVE,
            "downsideDeviation", IndicatorDistributionEntity.IndicatorType.NEGATIVE,
            "cvar", IndicatorDistributionEntity.IndicatorType.NEGATIVE,
            "var95", IndicatorDistributionEntity.IndicatorType.NEGATIVE,
            "var99", IndicatorDistributionEntity.IndicatorType.NEGATIVE,
            "trackingError", IndicatorDistributionEntity.IndicatorType.NEGATIVE,
            "maxDrawdownDuration", IndicatorDistributionEntity.IndicatorType.NEGATIVE,
            "downtrendCapture", IndicatorDistributionEntity.IndicatorType.NEGATIVE,

            // 交易统计
            "winRate", IndicatorDistributionEntity.IndicatorType.POSITIVE,
            "numberOfTrades", IndicatorDistributionEntity.IndicatorType.NEUTRAL,
            "profitFactor", IndicatorDistributionEntity.IndicatorType.POSITIVE,

            // 市场相关性
            "alpha", IndicatorDistributionEntity.IndicatorType.POSITIVE,
            "beta", IndicatorDistributionEntity.IndicatorType.NEUTRAL,
            "uptrendCapture", IndicatorDistributionEntity.IndicatorType.POSITIVE,

            // 分布特征
            "skewness", IndicatorDistributionEntity.IndicatorType.NEUTRAL,
            "kurtosis", IndicatorDistributionEntity.IndicatorType.NEUTRAL
    );

    /**
     * 指标中文名称映射
     */
    private static final Map<String, String> INDICATOR_DISPLAY_NAMES = MapUtils.of(
            "totalReturn", "总收益率",
            "annualizedReturn", "年化收益率",
            "averageProfit", "平均盈利",
            "riskAdjustedReturn", "风险调整收益",
            "sharpeRatio", "夏普比率",
            "sortinoRatio", "Sortino比率",
            "calmarRatio", "Calmar比率",
            "treynorRatio", "Treynor比率",
            "informationRatio", "信息比率",
            "sterlingRatio", "Sterling比率",
            "burkeRatio", "Burke比率",
            "modifiedSharpeRatio", "修正夏普比率",
            "omega", "Omega比率",
            "maxDrawdown", "最大回撤",
            "maximumLoss", "最大损失",
            "volatility", "波动率",
            "ulcerIndex", "溃疡指数",
            "painIndex", "痛苦指数",
            "downsideDeviation", "下行偏差",
            "cvar", "条件风险价值",
            "var95", "95%风险价值",
            "var99", "99%风险价值",
            "trackingError", "跟踪误差",
            "maxDrawdownDuration", "最大回撤持续时间",
            "downtrendCapture", "下跌捕获比率",
            "winRate", "胜率",
            "numberOfTrades", "交易次数",
            "profitFactor", "盈利因子",
            "alpha", "Alpha系数",
            "beta", "Beta系数",
            "uptrendCapture", "上涨捕获比率",
            "skewness", "偏度",
            "kurtosis", "峰度"
    );

    @Override
    @Transactional
    public List<IndicatorDistributionEntity> updateIndicatorDistributions() {
        log.info("开始更新指标分布数据...");

        // 1. 查询所有有交易记录的回测数据
        List<BacktestSummaryEntity> allBacktests = backtestSummaryRepository.findAll();
        List<BacktestSummaryEntity> validBacktests = allBacktests.stream()
                .filter(bt -> bt.getNumberOfTrades() != null && bt.getNumberOfTrades() > 0)
                .collect(Collectors.toList());

        if (validBacktests.isEmpty()) {
            log.warn("没有找到有效的回测数据，无法计算指标分布");
            return Collections.emptyList();
        }

        log.info("找到 {} 条有效回测记录", validBacktests.size());

        // 2. 生成新版本号
        Long maxVersion = indicatorDistributionRepository.findMaxVersion();
        Long newVersion = (maxVersion == null) ? 1L : maxVersion + 1;

        // 3. 计算每个指标的分布
        List<IndicatorDistributionEntity> newDistributions = new ArrayList<>();

        for (Map.Entry<String, IndicatorDistributionEntity.IndicatorType> entry : INDICATOR_CONFIGS.entrySet()) {
            String indicatorName = entry.getKey();
            IndicatorDistributionEntity.IndicatorType indicatorType = entry.getValue();

            try {
                IndicatorDistributionEntity distribution = calculateIndicatorDistribution(
                        indicatorName, indicatorType, validBacktests, newVersion);
                if (distribution != null) {
                    newDistributions.add(distribution);
                }
            } catch (Exception e) {
                log.error("计算指标 {} 的分布时发生错误: {}", indicatorName, e.getMessage(), e);
            }
        }

        // 4. 保存新的分布数据
        if (!newDistributions.isEmpty()) {
            // 将所有旧记录标记为非当前版本
            indicatorDistributionRepository.markAllAsNotCurrent();

            // 保存新的分布数据
            List<IndicatorDistributionEntity> savedDistributions = indicatorDistributionRepository.saveAll(newDistributions);

            log.info("成功更新 {} 个指标的分布数据，新版本号: {}", savedDistributions.size(), newVersion);

            // 5. 清理历史版本（保留最近5个版本）
            cleanOldVersions(5);

            return savedDistributions;
        } else {
            log.warn("没有成功计算出任何指标分布");
            return Collections.emptyList();
        }
    }

    /**
     * 计算单个指标的分布
     */
    private IndicatorDistributionEntity calculateIndicatorDistribution(
            String indicatorName,
            IndicatorDistributionEntity.IndicatorType indicatorType,
            List<BacktestSummaryEntity> backtests,
            Long version) {

        // 提取指标值
        List<BigDecimal> values = extractIndicatorValues(indicatorName, backtests);
        if (values.isEmpty()) {
            log.warn("指标 {} 没有有效数据", indicatorName);
            return null;
        }

        // 计算统计信息
        Collections.sort(values);
        int size = values.size();

        IndicatorDistributionEntity distribution = new IndicatorDistributionEntity();
        distribution.setIndicatorName(indicatorName);
        distribution.setIndicatorDisplayName(INDICATOR_DISPLAY_NAMES.get(indicatorName));
        distribution.setIndicatorType(indicatorType);
        distribution.setSampleCount(size);
        distribution.setVersion(version);
        distribution.setIsCurrent(true);

        // 基本统计
        distribution.setMinValue(values.get(0));
        distribution.setMaxValue(values.get(size - 1));
        distribution.setAvgValue(calculateAverage(values));

        // 计算分位数（8个区间）
        distribution.setP10(calculatePercentile(values, 0.10));
        distribution.setP20(calculatePercentile(values, 0.20));
        distribution.setP30(calculatePercentile(values, 0.30));
        distribution.setP40(calculatePercentile(values, 0.40));
        distribution.setP50(calculatePercentile(values, 0.50));
        distribution.setP60(calculatePercentile(values, 0.60));
        distribution.setP70(calculatePercentile(values, 0.70));
        distribution.setP80(calculatePercentile(values, 0.80));
        distribution.setP90(calculatePercentile(values, 0.90));

        log.debug("指标 {} 分布计算完成: 样本数={}, 范围=[{}, {}], 中位数={}",
                indicatorName, size, distribution.getMinValue(), distribution.getMaxValue(), distribution.getP50());

        return distribution;
    }

    /**
     * 从回测数据中提取指标值
     */
    private List<BigDecimal> extractIndicatorValues(String indicatorName, List<BacktestSummaryEntity> backtests) {
        List<BigDecimal> values = new ArrayList<>();

        for (BacktestSummaryEntity backtest : backtests) {
            BigDecimal value = getIndicatorValue(backtest, indicatorName);
            if (value != null) {
                values.add(value);
            }
        }

        return values;
    }

    /**
     * 根据指标名称获取回测实体中的对应字段值
     */
    private BigDecimal getIndicatorValue(BacktestSummaryEntity backtest, String indicatorName) {
        switch (indicatorName) {
            case "totalReturn": return backtest.getTotalReturn();
            case "annualizedReturn": return backtest.getAnnualizedReturn();
            case "averageProfit": return backtest.getAverageProfit();
            case "riskAdjustedReturn": return backtest.getRiskAdjustedReturn();
            case "sharpeRatio": return backtest.getSharpeRatio();
            case "sortinoRatio": return backtest.getSortinoRatio();
            case "calmarRatio": return backtest.getCalmarRatio();
            case "treynorRatio": return backtest.getTreynorRatio();
            case "informationRatio": return backtest.getInformationRatio();
            case "sterlingRatio": return backtest.getSterlingRatio();
            case "burkeRatio": return backtest.getBurkeRatio();
            case "modifiedSharpeRatio": return backtest.getModifiedSharpeRatio();
            case "omega": return backtest.getOmega();
            case "maxDrawdown": return backtest.getMaxDrawdown();
            case "maximumLoss": return backtest.getMaximumLoss();
            case "volatility": return backtest.getVolatility();
            case "ulcerIndex": return backtest.getUlcerIndex();
            case "painIndex": return backtest.getPainIndex();
            case "downsideDeviation": return backtest.getDownsideDeviation();
            case "cvar": return backtest.getCvar();
            case "var95": return backtest.getVar95();
            case "var99": return backtest.getVar99();
            case "trackingError": return backtest.getTrackingError();
            case "maxDrawdownDuration": return backtest.getMaxDrawdownDuration();
            case "downtrendCapture": return backtest.getDowntrendCapture();
            case "winRate": return backtest.getWinRate();
            case "numberOfTrades": return backtest.getNumberOfTrades() != null ?
                    BigDecimal.valueOf(backtest.getNumberOfTrades()) : null;
            case "profitFactor": return backtest.getProfitFactor();
            case "alpha": return backtest.getAlpha();
            case "beta": return backtest.getBeta();
            case "uptrendCapture": return backtest.getUptrendCapture();
            case "skewness": return backtest.getSkewness();
            case "kurtosis": return backtest.getKurtosis();
            default:
                log.warn("未知的指标名称: {}", indicatorName);
                return null;
        }
    }

    /**
     * 计算分位数
     */
    private BigDecimal calculatePercentile(List<BigDecimal> sortedValues, double percentile) {
        if (sortedValues.isEmpty()) return null;

        int size = sortedValues.size();
        double index = percentile * (size - 1);
        int lowerIndex = (int) Math.floor(index);
        int upperIndex = (int) Math.ceil(index);

        if (lowerIndex == upperIndex) {
            return sortedValues.get(lowerIndex);
        } else {
            BigDecimal lower = sortedValues.get(lowerIndex);
            BigDecimal upper = sortedValues.get(upperIndex);
            double weight = index - lowerIndex;

            return lower.add(upper.subtract(lower).multiply(BigDecimal.valueOf(weight)));
        }
    }

    /**
     * 计算平均值
     */
    private BigDecimal calculateAverage(List<BigDecimal> values) {
        if (values.isEmpty()) return null;

        BigDecimal sum = values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(values.size()), 8, BigDecimal.ROUND_HALF_UP);
    }

    @Override
    public Map<String, IndicatorDistributionEntity> getCurrentDistributions() {
        List<IndicatorDistributionEntity> currentDistributions = indicatorDistributionRepository.findByIsCurrentTrue();
        return currentDistributions.stream()
                .collect(Collectors.toMap(
                        IndicatorDistributionEntity::getIndicatorName,
                        entity -> entity
                ));
    }

    @Override
    public org.springframework.data.domain.Page<IndicatorDistributionEntity> getCurrentDistributions(
            String searchTerm, String filterType, org.springframework.data.domain.Pageable pageable) {
        
        IndicatorDistributionEntity.IndicatorType type = null;
        if (filterType != null && !filterType.equalsIgnoreCase("all")) {
            try {
                type = IndicatorDistributionEntity.IndicatorType.valueOf(filterType.toUpperCase());
            } catch (IllegalArgumentException e) {
                log.warn("无效的指标类型: {}", filterType);
            }
        }
        
        String search = (searchTerm != null && !searchTerm.trim().isEmpty()) ? searchTerm.trim() : null;
        
        return indicatorDistributionRepository.findCurrentWithFilters(search, type, pageable);
    }

    @Override
    public double calculateIndicatorScore(String indicatorName, BigDecimal value) {
        Optional<IndicatorDistributionEntity> distributionOpt =
                indicatorDistributionRepository.findByIndicatorNameAndIsCurrentTrue(indicatorName);

        if (distributionOpt.isPresent()) {
            return distributionOpt.get().calculateScore(value);
        } else {
            log.warn("未找到指标 {} 的分布数据，返回默认评分", indicatorName);
            return 4.0; // 默认中等分
        }
    }

    @Override
    public Map<String, Double> calculateIndicatorScores(Map<String, BigDecimal> indicatorValues) {
        Map<String, Double> scores = new HashMap<>();

        for (Map.Entry<String, BigDecimal> entry : indicatorValues.entrySet()) {
            String indicatorName = entry.getKey();
            BigDecimal value = entry.getValue();
            double score = calculateIndicatorScore(indicatorName, value);
            scores.put(indicatorName, score);
        }

        return scores;
    }

    @Override
    public Map<String, Object> getDistributionStatistics() {
        List<IndicatorDistributionEntity> currentDistributions = indicatorDistributionRepository.findByIsCurrentTrue();
        Long currentVersion = indicatorDistributionRepository.findMaxVersion();

        Map<String, Object> statistics = new HashMap<>();
        statistics.put("currentVersion", currentVersion);
        statistics.put("indicatorCount", currentDistributions.size());
        statistics.put("lastUpdateTime",
                currentDistributions.stream()
                        .map(IndicatorDistributionEntity::getUpdateTime)
                        .max(LocalDateTime::compareTo)
                        .orElse(null));

        // 按类型分组统计
        Map<IndicatorDistributionEntity.IndicatorType, Long> typeCount = currentDistributions.stream()
                .collect(Collectors.groupingBy(
                        IndicatorDistributionEntity::getIndicatorType,
                        Collectors.counting()));
        statistics.put("indicatorTypeCount", typeCount);

        // 样本数统计
        OptionalInt totalSamples = currentDistributions.stream()
                .mapToInt(IndicatorDistributionEntity::getSampleCount)
                .max();
        statistics.put("maxSampleCount", totalSamples.orElse(0));

        return statistics;
    }

    @Override
    @Transactional
    public void cleanOldVersions(int keepVersions) {
        Long maxVersion = indicatorDistributionRepository.findMaxVersion();
        if (maxVersion != null && maxVersion > keepVersions) {
            Long deleteBeforeVersion = maxVersion - keepVersions + 1;
            indicatorDistributionRepository.deleteOldVersions(deleteBeforeVersion);
            log.info("清理了版本号小于 {} 的历史数据", deleteBeforeVersion);
        }
    }

    @Override
    public List<IndicatorDistributionEntity> getIndicatorHistory(String indicatorName) {
        return indicatorDistributionRepository.findByIndicatorNameOrderByVersionDesc(indicatorName);
    }
}
