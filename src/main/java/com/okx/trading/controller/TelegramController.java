package com.okx.trading.controller;

import com.okx.trading.model.dto.TelegramChannelDTO;
import com.okx.trading.model.entity.TelegramChannelEntity;
import com.okx.trading.model.entity.TelegramMessageEntity;
import com.okx.trading.repository.TelegramChannelRepository;
import com.okx.trading.repository.TelegramMessageRepository;
import com.okx.trading.service.impl.TelegramScraperService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/telegram")
@Slf4j
public class TelegramController {

    @Autowired
    private TelegramMessageRepository messageRepository;

    @Autowired
    private TelegramChannelRepository channelRepository;

    @Autowired
    private TelegramScraperService telegramScraperService;

    @GetMapping("/messages")
    public Page<TelegramMessageEntity> getMessages(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        List<String> activeChannels = channelRepository.findByIsActiveTrue()
                .stream()
                .map(TelegramChannelEntity::getChannelName)
                .collect(Collectors.toList());
        
        log.info("Fetching messages for active channels: {}", activeChannels);

        if (activeChannels.isEmpty()) {
            return Page.empty();
        }

        Pageable pageable = PageRequest.of(page, size);
        return messageRepository.findByChatTitleInOrderByReceivedAtDesc(activeChannels, pageable);
    }

    @GetMapping("/channels")
    public List<TelegramChannelEntity> getChannels() {
        return telegramScraperService.getAllChannels();
    }

    @GetMapping("/search")
    public List<TelegramChannelDTO> searchChannels(@RequestParam String query) {
        return telegramScraperService.searchChannels(query);
    }

    @PostMapping("/channels")
    public void addChannel(
            @RequestParam String channelName,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) Long subscribers,
            @RequestParam(required = false) String avatarUrl
    ) {
        telegramScraperService.addChannel(channelName, title, subscribers, avatarUrl);
    }

    @DeleteMapping("/channels")
    public void removeChannel(@RequestParam String channelName) {
        telegramScraperService.removeChannel(channelName);
    }

    @PostMapping("/refresh")
    public void refresh() {
        telegramScraperService.scrapeChannels();
    }
}
