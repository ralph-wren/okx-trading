package com.okx.trading.repository;

import com.okx.trading.model.entity.TelegramChannelEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TelegramChannelRepository extends JpaRepository<TelegramChannelEntity, Long> {
    Optional<TelegramChannelEntity> findByChannelName(String channelName);
    boolean existsByChannelName(String channelName);
    List<TelegramChannelEntity> findByIsActiveTrue();
}
