package com.okx.trading.repository;

import com.okx.trading.model.entity.TelegramMessageEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TelegramMessageRepository extends JpaRepository<TelegramMessageEntity, Long> {
    List<TelegramMessageEntity> findAllByOrderByReceivedAtDesc();
    Page<TelegramMessageEntity> findAllByOrderByReceivedAtDesc(Pageable pageable);
    
    // Renamed method to ensure fresh compilation
    Page<TelegramMessageEntity> findByChatTitleInOrderByReceivedAtDesc(List<String> chatTitles, Pageable pageable);
    
    boolean existsByChatTitleAndMessageId(String chatTitle, Integer messageId);
}
