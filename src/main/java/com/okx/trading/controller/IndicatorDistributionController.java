package com.okx.trading.controller;

import com.google.common.collect.ImmutableMap;
import com.okx.trading.model.common.ApiResponse;
import com.okx.trading.model.dto.IndicatorWeightConfig;
import com.okx.trading.model.entity.IndicatorDistributionEntity;
import com.okx.trading.service.IndicatorDistributionService;
import com.okx.trading.service.IndicatorWeightService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 指标分布控制器
 * 提供指标分布统计和权重配置相关功能
 */
@Slf4j
@RestController
@RequestMapping("/api/indicator-distribution")
@RequiredArgsConstructor
@Tag(name = "指标分布管理")
public class IndicatorDistributionController {

    private final IndicatorDistributionService indicatorDistributionService;
    private final IndicatorWeightService indicatorWeightService;

    /**
     * 更新指标分布数据
     */
    @PostMapping("/update")
    @Operation(summary = "更新指标分布统计数据")
    public ResponseEntity<ApiResponse<List<IndicatorDistributionEntity>>> updateIndicatorDistributions() {
        try {
            log.info("开始更新指标分布数据...");
            List<IndicatorDistributionEntity> distributions = indicatorDistributionService.updateIndicatorDistributions();

            if (distributions.isEmpty()) {
                return ResponseEntity.ok(ApiResponse.error("没有生成任何指标分布数据"));
            }

            log.info("指标分布数据更新成功，共生成 {} 个指标分布", distributions.size());
            return ResponseEntity.ok(ApiResponse.success("指标分布数据更新成功", distributions));
        } catch (Exception e) {
            log.error("更新指标分布数据失败: {}", e.getMessage(), e);
            return ResponseEntity.ok(ApiResponse.error("更新指标分布数据失败: " + e.getMessage()));
        }
    }

    /**
     * 获取当前指标分布详情 (支持分页和过滤)
     */
    @GetMapping("/current")
    @Operation(summary = "获取当前指标分布详情")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getCurrentDistributions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "15") int size,
            @RequestParam(required = false) String searchTerm,
            @RequestParam(required = false) String filterType) {
        try {
            org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page, size, 
                org.springframework.data.domain.Sort.by("indicatorType").ascending().and(org.springframework.data.domain.Sort.by("indicatorDisplayName").ascending()));
            
            org.springframework.data.domain.Page<IndicatorDistributionEntity> distributionPage = 
                indicatorDistributionService.getCurrentDistributions(searchTerm, filterType, pageable);

            Map<String, Object> result = ImmutableMap.of(
                    "totalCount", distributionPage.getTotalElements(),
                    "totalPages", distributionPage.getTotalPages(),
                    "currentPage", distributionPage.getNumber(),
                    "pageSize", distributionPage.getSize(),
                    "indicatorDetails", distributionPage.getContent().stream()
                            .map(dist -> ImmutableMap.of(
                                    "name", dist.getIndicatorName(),
                                    "displayName", dist.getIndicatorDisplayName(),
                                    "type", dist.getIndicatorType(),
                                    "sampleCount", dist.getSampleCount(),
                                    "range", ImmutableMap.of(
                                            "min", dist.getMinValue(),
                                            "max", dist.getMaxValue(),
                                            "avg", dist.getAvgValue()
                                    ),
                                    "percentiles", ImmutableMap.<String, Object>builder()
                                            .put("p10", dist.getP10())
                                            .put("p20", dist.getP20())
                                            .put("p30", dist.getP30())
                                            .put("p40", dist.getP40())
                                            .put("p50", dist.getP50())
                                            .put("p60", dist.getP60())
                                            .put("p70", dist.getP70())
                                            .put("p80", dist.getP80())
                                            .put("p90", dist.getP90())
                                            .build()
                                    ))
                            .collect(java.util.stream.Collectors.toList())
            );

            return ResponseEntity.ok(ApiResponse.success(result, "获取指标分布详情成功"));
        } catch (Exception e) {
            log.error("获取指标分布详情失败: {}", e.getMessage(), e);
            return ResponseEntity.ok(ApiResponse.error("获取指标分布详情失败: " + e.getMessage()));
        }
    }

    /**
     * 计算动态评分
     */
    @PostMapping("/calculate-score")
    @Operation(summary = "基于真实分布计算动态评分")
    public ResponseEntity<ApiResponse<Map<String, Object>>> calculateDynamicScore(@RequestBody Map<String, BigDecimal> indicatorValues) {
        try {
            Map<String, Double> scores = indicatorDistributionService.calculateIndicatorScores(indicatorValues);

            Map<String, Object> result = ImmutableMap.of(
                    "inputIndicators", indicatorValues,
                    "scores", scores,
                    "scoreRules", "基于历史数据分布的8分制评分，分位数越高得分越高"
            );

            return ResponseEntity.ok(ApiResponse.success(result, "动态评分计算成功"));
        } catch (Exception e) {
            log.error("计算动态评分失败: {}", e.getMessage(), e);
            return ResponseEntity.ok(ApiResponse.error("计算动态评分失败: " + e.getMessage()));
        }
    }

    /**
     * 获取分布统计信息
     */
    @GetMapping("/statistics")
    @Operation(summary = "获取指标分布统计信息")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getDistributionStatistics() {
        try {
            Map<String, Object> statistics = indicatorDistributionService.getDistributionStatistics();
            return ResponseEntity.ok(ApiResponse.success(statistics, "获取分布统计信息成功"));
        } catch (Exception e) {
            log.error("获取分布统计信息失败: {}", e.getMessage(), e);
            return ResponseEntity.ok(ApiResponse.error("获取分布统计信息失败: " + e.getMessage()));
        }
    }

    // ========== 权重配置相关接口 ==========

    /**
     * 获取权重配置统计信息
     */
    @GetMapping("/weight-config/statistics")
    @Operation(summary = "获取权重配置统计信息")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getWeightConfigStatistics() {
        try {
            Map<String, Object> statistics = indicatorWeightService.getConfigStatistics();
            return ResponseEntity.ok(ApiResponse.success(statistics, "获取权重配置统计信息成功"));
        } catch (Exception e) {
            log.error("获取权重配置统计信息失败: {}", e.getMessage(), e);
            return ResponseEntity.ok(ApiResponse.error("获取权重配置统计信息失败: " + e.getMessage()));
        }
    }

    /**
     * 重新加载权重配置
     */
    @PostMapping("/weight-config/reload")
    @Operation(summary = "重新加载权重配置")
    public ResponseEntity<ApiResponse<String>> reloadWeightConfig() {
        try {
            boolean success = indicatorWeightService.reloadConfig();
            if (success) {
                return ResponseEntity.ok(ApiResponse.success("权重配置重新加载成功"));
            } else {
                return ResponseEntity.ok(ApiResponse.error("权重配置重新加载失败"));
            }
        } catch (Exception e) {
            log.error("重新加载权重配置失败: {}", e.getMessage(), e);
            return ResponseEntity.ok(ApiResponse.error("重新加载权重配置失败: " + e.getMessage()));
        }
    }

    /**
     * 基于权重配置计算综合评分
     */
    @PostMapping("/weight-config/calculate-comprehensive-score")
    @Operation(summary = "基于权重配置计算综合评分")
    public ResponseEntity<ApiResponse<Map<String, Object>>> calculateComprehensiveScore(@RequestBody Map<String, BigDecimal> indicatorValues) {
        try {
            // 首先计算各指标的评分（8分制）
            Map<String, Double> indicatorScores = indicatorDistributionService.calculateIndicatorScores(indicatorValues);

            // 使用权重配置计算综合评分
            BigDecimal comprehensiveScore = indicatorWeightService.calculateComprehensiveScore(indicatorValues, indicatorScores);

            // 获取维度评分详情
            Map<String, Object> dimensionDetails = indicatorWeightService.getDimensionScoreDetails(indicatorValues, indicatorScores);

            Map<String, Object> result = ImmutableMap.of(
                    "comprehensiveScore", comprehensiveScore,
                    "scoreScale", "10分制",
                    "inputIndicators", indicatorValues,
                    "indicatorScores", indicatorScores,
                    "dimensionDetails", dimensionDetails,
                    "description", "基于真实数据分布和科学权重配置的综合评分"
            );

            return ResponseEntity.ok(ApiResponse.success(result, "综合评分计算成功"));
        } catch (Exception e) {
            log.error("计算综合评分失败: {}", e.getMessage(), e);
            return ResponseEntity.ok(ApiResponse.error("计算综合评分失败: " + e.getMessage()));
        }
    }

    /**
     * 获取权重配置详情
     */
    @GetMapping("/weight-config/details")
    @Operation(summary = "获取权重配置详情")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getWeightConfigDetails() {
        try {
            IndicatorWeightConfig config = indicatorWeightService.getCurrentConfig();
            if (config == null) {
                return ResponseEntity.ok(ApiResponse.error("权重配置未加载"));
            }

            Map<String, Object> result = ImmutableMap.of(
                    "dimensions", config.getDimensions(),
                    "indicators", config.getIndicators(),
                    "specialRules", config.getSpecialRules() != null ? config.getSpecialRules() : ImmutableMap.of(),
                    "configInfo", config.getConfig() != null ? config.getConfig() : ImmutableMap.of(),
                    "statistics", indicatorWeightService.getConfigStatistics()
            );

            return ResponseEntity.ok(ApiResponse.success(result, "获取权重配置详情成功"));
        } catch (Exception e) {
            log.error("获取权重配置详情失败: {}", e.getMessage(), e);
            return ResponseEntity.ok(ApiResponse.error("获取权重配置详情失败: " + e.getMessage()));
        }
    }
}
