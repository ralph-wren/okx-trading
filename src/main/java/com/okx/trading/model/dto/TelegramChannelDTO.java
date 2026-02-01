package com.okx.trading.model.dto;

import lombok.Data;

@Data
public class TelegramChannelDTO {
    private String name; // handle, e.g. @jinse2017
    private String title; // Display Name
    private Long subscribers;
    private String avatarUrl;
    private String description;
}
