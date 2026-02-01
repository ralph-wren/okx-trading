package com.okx.trading.service;

import com.okx.trading.model.entity.IndicatorDistributionEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 指标分布服务接口
 * 用于动态计算各个指标的分布情况，并提供基于分布的评分机制
 */
public interface IndicatorDistributionService {

    /**
     * 更新指标分布数据
     * 从所有历史回测记录中计算指标分布，生成新版本的分布数据
     * 
     * @return 更新后的指标分布列表
     */
    List<IndicatorDistributionEntity> updateIndicatorDistributions();

    /**
     * 获取当前版本的所有指标分布
     * 
     * @return 当前版本的指标分布Map，key为指标名称
     */
    Map<String, IndicatorDistributionEntity> getCurrentDistributions();

    /**
     * 分页获取当前版本的指标分布
     * 
     * @param searchTerm 搜索关键词
     * @param filterType 指标类型
     * @param pageable 分页参数
     * @return 分页结果
     */
    Page<IndicatorDistributionEntity> getCurrentDistributions(
            String searchTerm, String filterType, Pageable pageable);

    /**
     * 根据指标名称和值计算评分
     * 
     * @param indicatorName 指标名称
     * @param value 指标值
     * @return 1-8分的评分
     */
    double calculateIndicatorScore(String indicatorName, BigDecimal value);

    /**
     * 批量计算指标评分
     * 
     * @param indicatorValues 指标名称和值的Map
     * @return 指标名称和评分的Map
     */
    Map<String, Double> calculateIndicatorScores(Map<String, BigDecimal> indicatorValues);

    /**
     * 获取指标分布统计信息
     * 
     * @return 分布统计信息
     */
    Map<String, Object> getDistributionStatistics();

    /**
     * 清理历史版本数据
     * 保留最近N个版本，删除更老的版本
     * 
     * @param keepVersions 保留的版本数量
     */
    void cleanOldVersions(int keepVersions);

    /**
     * 获取指标的历史分布变化
     * 
     * @param indicatorName 指标名称
     * @return 历史分布数据
     */
    List<IndicatorDistributionEntity> getIndicatorHistory(String indicatorName);
}
