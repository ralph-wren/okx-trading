package com.okx.trading.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.okx.trading.model.entity.CandlestickEntity;

/**
 * K线数据仓库
 */
@Repository
public interface CandlestickRepository extends JpaRepository<CandlestickEntity, String> {

    /**
     * 根据交易对和时间间隔查询指定时间范围内的K线数据
     *
     * @param symbol 交易对
     * @param interval 时间间隔
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return K线数据列表
     */
    @Query("SELECT c FROM CandlestickEntity c WHERE c.symbol = :symbol AND BINARY(c.intervalVal) = BINARY(:interval_val) AND c.openTime BETWEEN :startTime AND :endTime ORDER BY c.openTime ASC")
    List<CandlestickEntity> findBySymbolAndIntervalAndOpenTimeBetweenOrderByOpenTimeAsc(
            @Param("symbol") String symbol, @Param("interval_val") String intervalVal,
            @Param("startTime") LocalDateTime startTime, @Param("endTime") LocalDateTime endTime);


    /**
     * 根据交易对和时间间隔查询指定时间范围内的K线数据
     *
     * @param symbol 交易对
     * @param interval 时间间隔
     * @return K线数据列表
     */
    @Query("SELECT c FROM CandlestickEntity c WHERE c.symbol = :symbol AND BINARY(c.intervalVal) = BINARY(:interval_val) ORDER BY c.openTime ASC")
    List<CandlestickEntity> findBySymbolAndIntervalAsc(
            @Param("symbol") String symbol, @Param("interval_val") String intervalVal);


    /**
     * 根据交易对和时间间隔查询最新的K线数据
     *
     * @param symbol 交易对
     * @param interval 时间间隔
     * @param pageable 分页参数
     * @return K线数据列表
     */
    @Query("SELECT c FROM CandlestickEntity c WHERE c.symbol = :symbol AND BINARY(c.intervalVal) = BINARY(:interval_val) ORDER BY c.openTime DESC")
    List<CandlestickEntity> findLatestBySymbolAndInterval(
            @Param("symbol") String symbol, @Param("interval_val") String intervalVal, Pageable pageable);

    /**
     * 查询时间范围内缺失的K线时间点
     *
     * @param symbol 交易对
     * @param interval 时间间隔
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 已存在的K线开盘时间列表
     */
    @Query("SELECT c.openTime FROM CandlestickEntity c WHERE c.symbol = :symbol AND BINARY(c.intervalVal) = BINARY(:interval_val) AND c.openTime >= :startTime AND c.openTime < :endTime ORDER BY c.openTime ASC")
    List<LocalDateTime> findExistingOpenTimesBySymbolAndIntervalBetween(
            @Param("symbol") String symbol, @Param("interval_val") String intervalVal,
            @Param("startTime") LocalDateTime startTime, @Param("endTime") LocalDateTime endTime);

    /**
     * 批量保存K线数据（避免重复）
     *
     * @param symbol 交易对
     * @param interval 时间间隔
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 删除的记录数
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM CandlestickEntity c WHERE c.symbol = :symbol AND BINARY(c.intervalVal) = BINARY(:interval_val) AND c.openTime BETWEEN :startTime AND :endTime")
    int deleteBySymbolAndIntervalAndOpenTimeBetween(
            @Param("symbol") String symbol, @Param("interval_val") String intervalVal,
            @Param("startTime") LocalDateTime startTime, @Param("endTime") LocalDateTime endTime);
}
