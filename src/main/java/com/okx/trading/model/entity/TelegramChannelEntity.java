package com.okx.trading.model.entity;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "telegram_channels")
public class TelegramChannelEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String channelName;

    private String title;
    
    private Long subscribers;
    
    private String avatarUrl;

    private String description;

    private boolean isActive = true;
}
