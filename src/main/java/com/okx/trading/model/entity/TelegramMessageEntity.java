package com.okx.trading.model.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "telegram_messages")
public class TelegramMessageEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long chatId;
    private String chatTitle;
    private Integer messageId;
    
    @Column(columnDefinition = "TEXT")
    private String text;
    
    private String senderName;
    private String senderUsername;
    
    private LocalDateTime receivedAt;
    private LocalDateTime messageDate;
}
