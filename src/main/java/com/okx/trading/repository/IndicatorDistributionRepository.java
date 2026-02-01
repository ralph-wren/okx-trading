package com.okx.trading.repository;

import com.okx.trading.model.entity.IndicatorDistributionEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 指标分布数据Repository
 */
@Repository
public interface IndicatorDistributionRepository extends JpaRepository<IndicatorDistributionEntity, Long> {

    /**
     * 查询所有当前版本的指标分布
     */
    List<IndicatorDistributionEntity> findByIsCurrentTrue();

    /**
     * 分页查询当前版本的指标分布
     */
    @Query("SELECT i FROM IndicatorDistributionEntity i WHERE i.isCurrent = true " +
           "AND (:searchTerm IS NULL OR i.indicatorName LIKE %:searchTerm% OR i.indicatorDisplayName LIKE %:searchTerm%) " +
           "AND (:indicatorType IS NULL OR i.indicatorType = :indicatorType)")
    Page<IndicatorDistributionEntity> findCurrentWithFilters(
            @Param("searchTerm") String searchTerm,
            @Param("indicatorType") IndicatorDistributionEntity.IndicatorType indicatorType,
            Pageable pageable);

    /**
     * 根据指标名称查询当前版本的分布
     */
    Optional<IndicatorDistributionEntity> findByIndicatorNameAndIsCurrentTrue(String indicatorName);

    /**
     * 查询指定版本的所有指标分布
     */
    List<IndicatorDistributionEntity> findByVersionOrderByIndicatorName(Long version);

    /**
     * 查询最新的版本号
     */
    @Query("SELECT MAX(version) FROM IndicatorDistributionEntity")
    Long findMaxVersion();

    /**
     * 将所有记录的isCurrent设置为false
     */
    @Modifying
    @Query("UPDATE IndicatorDistributionEntity SET isCurrent = false")
    void markAllAsNotCurrent();

    /**
     * 将指定版本的记录设置为当前版本
     */
    @Modifying
    @Query("UPDATE IndicatorDistributionEntity SET isCurrent = true WHERE version = :version")
    void markVersionAsCurrent(@Param("version") Long version);

    /**
     * 删除指定版本之前的历史数据（保留最近几个版本）
     */
    @Modifying
    @Query("DELETE FROM IndicatorDistributionEntity WHERE version < :version")
    void deleteOldVersions(@Param("version") Long version);

    /**
     * 查询指定指标的所有历史版本
     */
    List<IndicatorDistributionEntity> findByIndicatorNameOrderByVersionDesc(String indicatorName);

    /**
     * 统计当前版本的指标数量
     */
    @Query("SELECT COUNT(*) FROM IndicatorDistributionEntity WHERE isCurrent = true")
    Long countCurrentIndicators();
}
