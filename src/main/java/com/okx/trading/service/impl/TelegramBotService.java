package com.okx.trading.service.impl;

import com.okx.trading.model.entity.TelegramMessageEntity;
import com.okx.trading.repository.TelegramMessageRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Slf4j
@Service
public class TelegramBotService extends TelegramLongPollingBot {

    @Value("${telegram.bot.username}")
    private String botUsername;

    @Value("${telegram.bot.token}")
    private String botToken;

    private final TelegramMessageRepository messageRepository;

    public TelegramBotService(TelegramMessageRepository messageRepository) {
        this.messageRepository = messageRepository;
    }

    @Override
    public String getBotUsername() {
        return botUsername;
    }

    @Override
    public String getBotToken() {
        return botToken;
    }

    @Override
    public void onUpdateReceived(Update update) {
        try {
            if (update.hasChannelPost()) {
                saveMessage(update.getChannelPost());
            } else if (update.hasMessage()) {
                saveMessage(update.getMessage());
            }
        } catch (Exception e) {
            log.error("Error processing Telegram update", e);
        }
    }

    private void saveMessage(Message message) {
        if (message.hasText()) {
            try {
                TelegramMessageEntity entity = new TelegramMessageEntity();
                entity.setChatId(message.getChatId());
                entity.setChatTitle(message.getChat().getTitle());
                entity.setMessageId(message.getMessageId());
                entity.setText(message.getText());
                
                if (message.getFrom() != null) {
                    String firstName = message.getFrom().getFirstName();
                    String lastName = message.getFrom().getLastName();
                    entity.setSenderName((firstName != null ? firstName : "") + " " + (lastName != null ? lastName : ""));
                    entity.setSenderUsername(message.getFrom().getUserName());
                } else {
                    entity.setSenderName(message.getChat().getTitle());
                }

                entity.setReceivedAt(LocalDateTime.now());
                entity.setMessageDate(LocalDateTime.ofInstant(Instant.ofEpochSecond(message.getDate()), ZoneId.systemDefault()));

                messageRepository.save(entity);
                log.info("Saved Telegram message from chat: {}", message.getChatId());
            } catch (Exception e) {
                log.error("Error saving Telegram message", e);
            }
        }
    }
}
