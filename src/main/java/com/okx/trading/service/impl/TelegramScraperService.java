package com.okx.trading.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.okx.trading.model.dto.TelegramChannelDTO;
import com.okx.trading.model.entity.TelegramChannelEntity;
import com.okx.trading.model.entity.TelegramMessageEntity;
import com.okx.trading.repository.TelegramChannelRepository;
import com.okx.trading.repository.TelegramMessageRepository;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class TelegramScraperService {

    @Autowired
    private TelegramMessageRepository telegramMessageRepository;

    @Autowired
    private TelegramChannelRepository telegramChannelRepository;

    @Value("${telegram.scraper.channels:jinse2017}")
    private List<String> defaultChannels;

    @Value("${okx.proxy.host:localhost}")
    private String proxyHost;

    @Value("${okx.proxy.port:10809}")
    private int proxyPort;

    @Value("${okx.proxy.enabled:false}")
    private boolean proxyEnabled;

    @Value("${okx.api.base-url:https://www.okx.com}")
    private String okxBaseUrl;

    @PostConstruct
    public void init() {
        // Initialize default channels if DB is empty
        if (telegramChannelRepository.count() == 0) {
            for (String channelName : defaultChannels) {
                if (!telegramChannelRepository.existsByChannelName(channelName)) {
                    addChannel(channelName, null, null, null);
                }
            }
        }
    }

    public void addChannel(String channelName) {
        addChannel(channelName, null, null, null);
    }

    public void addChannel(String channelName, String title, Long subscribers, String avatarUrl) {
        if (!telegramChannelRepository.existsByChannelName(channelName)) {
            TelegramChannelEntity entity = new TelegramChannelEntity();
            entity.setChannelName(channelName);
            entity.setTitle(title != null ? title : channelName);
            entity.setSubscribers(subscribers);
            entity.setAvatarUrl(avatarUrl);
            entity.setActive(true);
            telegramChannelRepository.save(entity);
            log.info("Added new channel: {} ({})", channelName, title);
        }
    }

    public void removeChannel(String channelName) {
        telegramChannelRepository.findByChannelName(channelName).ifPresent(entity -> {
            telegramChannelRepository.delete(entity);
            log.info("Removed channel: {}", channelName);
        });
    }

    public List<TelegramChannelEntity> getAllChannels() {
        return telegramChannelRepository.findAll();
    }

    public List<TelegramChannelDTO> searchChannels(String query) {
        List<TelegramChannelDTO> results = new ArrayList<>();
        try {
            String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8.toString());
            String url = "https://telesou.com/api/search?q=" + encodedQuery;
            
            Connection connect = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                    .header("Referer", "https://telesou.com/")
                    .ignoreContentType(true)
                    .timeout(10000);
            
            // Telesou API returns JSON
            String jsonBody = connect.execute().body();
            JSONObject response = JSON.parseObject(jsonBody);
            
            if (response != null && response.getInteger("code") == 0) {
                JSONObject data = response.getJSONObject("data");
                if (data != null) {
                    JSONArray items = data.getJSONArray("items");
                    if (items != null) {
                        for (int i = 0; i < items.size(); i++) {
                            JSONObject item = items.getJSONObject(i);
                            TelegramChannelDTO dto = new TelegramChannelDTO();
                            
                            String handle = item.getString("handle"); // e.g. @jinse2017
                            if (handle != null && handle.startsWith("@")) {
                                handle = handle.substring(1);
                            }
                            
                            dto.setName(handle);
                            dto.setTitle(item.getString("name"));
                            dto.setSubscribers(item.getLong("subscriber_count"));
                            
                            String icon = item.getString("icon_url");
                            if (icon != null && icon.startsWith("/")) {
                                dto.setAvatarUrl("https://telesou.com" + icon);
                            } else {
                                dto.setAvatarUrl(icon);
                            }
                            
                            dto.setDescription(item.getString("description"));
                            results.add(dto);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to search channels via telesou: {}", query, e);
        }
        return results;
    }

    @Scheduled(fixedRate = 60000) // Run every minute
    public void scrapeChannels() {
        List<TelegramChannelEntity> channels = telegramChannelRepository.findAll();
        
        for (TelegramChannelEntity channel : channels) {
            if (!channel.isActive()) continue;

            String name = channel.getChannelName();
            if ("OKX公告".equalsIgnoreCase(name) || "OKX Announcements".equalsIgnoreCase(name)) {
                scrapeOkxAnnouncements();
            } else {
                scrapeChannel(name);
            }
        }
    }

    private void scrapeOkxAnnouncements() {
        String url = okxBaseUrl + "/api/v5/support/announcements";
        try {
            log.debug("Scraping OKX Announcements: {}", url);
            Connection connect = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                    .timeout(10000)
                    .ignoreContentType(true);

            if (proxyEnabled) {
                connect.proxy(proxyHost, proxyPort);
            }

            String jsonBody = connect.execute().body();
            JSONObject response = JSON.parseObject(jsonBody);
            
            if (!"0".equals(response.getString("code"))) {
                log.warn("Failed to fetch OKX announcements: {}", response.getString("msg"));
                return;
            }

            JSONArray data = response.getJSONArray("data").getJSONObject(0).getJSONArray("details");
            if (data == null) return;

            for (int i = 0; i < data.size(); i++) {
                try {
                    JSONObject item = data.getJSONObject(i);
                    String title = item.getString("title");
                    String pTime = item.getString("pTime"); // Timestamp in millis
                    String annUrl = item.getString("url"); // Usually relative or absolute

                    // Handle ID
                    Integer messageId = annUrl.hashCode();
                    // Check existence
                    if (telegramMessageRepository.existsByChatTitleAndMessageId("OKX公告", messageId)) {
                        continue;
                    }

                    // Parse Date
                    LocalDateTime messageDate = LocalDateTime.now();
                    try {
                        long timestamp = Long.parseLong(pTime);
                        messageDate = LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault());
                    } catch (Exception e) {
                        // ignore
                    }

                    // Construct Text (HTML)
                    if (annUrl != null && !annUrl.startsWith("http")) {
                         // If relative, prepend base url
                         annUrl = "https://www.okx.com/help/" + annUrl;
                    }
                    
                    String html = String.format("<b>%s</b><br/><a href=\"%s\" target=\"_blank\">查看详情</a>", title, annUrl);

                    TelegramMessageEntity entity = new TelegramMessageEntity();
                    entity.setChatTitle("OKX公告");
                    entity.setChatId(0L);
                    entity.setMessageId(messageId);
                    entity.setText(html);
                    entity.setSenderName("OKX Official");
                    entity.setSenderUsername("okx_announcements");
                    entity.setReceivedAt(LocalDateTime.now());
                    entity.setMessageDate(messageDate);

                    telegramMessageRepository.save(entity);
                    log.info("Saved OKX announcement: {} - {}", messageId, title);

                } catch (Exception e) {
                    log.error("Error parsing OKX announcement item", e);
                }
            }

        } catch (IOException e) {
            log.error("Failed to fetch OKX announcements", e);
        }
    }

    private void scrapeChannel(String channelName) {
        String url = "https://t.me/s/" + channelName;
        try {
            log.debug("Scraping channel: {}", channelName);
            Connection connect = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                    .timeout(10000);

            if (proxyEnabled) {
                connect.proxy(proxyHost, proxyPort);
            }

            Document doc = connect.get();

            // Try to extract Channel Title and Subscribers if missing in DB
            try {
                TelegramChannelEntity channelEntity = telegramChannelRepository.findByChannelName(channelName).orElse(null);
                if (channelEntity != null) {
                    boolean updated = false;
                    
                    // Update Title
                    if (channelEntity.getTitle() == null || channelEntity.getTitle().isEmpty() || channelEntity.getTitle().equals(channelName)) {
                        String pageTitle = doc.select("meta[property=og:title]").attr("content");
                        if (pageTitle != null && !pageTitle.isEmpty()) {
                            channelEntity.setTitle(pageTitle);
                            updated = true;
                        }
                    }
                    
                    // Update Avatar
                    if (channelEntity.getAvatarUrl() == null || channelEntity.getAvatarUrl().isEmpty()) {
                        String image = doc.select("meta[property=og:image]").attr("content");
                        if (image != null && !image.isEmpty()) {
                            channelEntity.setAvatarUrl(image);
                            updated = true;
                        }
                    }
                    
                    // Update Subscribers (Roughly from description or extra info if available)
                    // Telegram web view shows "X subscribers" in .tgme_page_extra
                    Element extraInfo = doc.selectFirst(".tgme_page_extra");
                    if (extraInfo != null) {
                        String extraText = extraInfo.text(); // e.g. "1.2K subscribers"
                        if (extraText.contains("subscribers")) {
                            String subStr = extraText.replace("subscribers", "").trim();
                            // Parse K/M suffixes... simplification for now
                            // Maybe not strictly necessary if we get it from search
                        }
                    }

                    if (updated) {
                        telegramChannelRepository.save(channelEntity);
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to update channel metadata for {}", channelName);
            }

            Elements messages = doc.select(".tgme_widget_message_wrap");
            
            for (Element messageWrap : messages) {
                try {
                    Element messageContent = messageWrap.selectFirst(".tgme_widget_message_text");
                    if (messageContent == null) continue;

                    String html = messageContent.html();

                    // Try to get message ID from data-post attribute
                    Element msgDiv = messageWrap.selectFirst(".tgme_widget_message");
                    String dataPost = msgDiv != null ? msgDiv.attr("data-post") : "";
                    // data-post is like "jinse2017/12345"
                    Integer messageId = null;
                    if (dataPost.contains("/")) {
                        try {
                            messageId = Integer.parseInt(dataPost.split("/")[1]);
                        } catch (NumberFormatException e) {
                            // ignore
                        }
                    }

                    if (messageId == null) continue;

                    // Check if exists
                    if (telegramMessageRepository.existsByChatTitleAndMessageId(channelName, messageId)) {
                        continue;
                    }

                    // Date
                    Element timeElement = messageWrap.selectFirst("time");
                    LocalDateTime messageDate = LocalDateTime.now();
                    if (timeElement != null) {
                        String datetime = timeElement.attr("datetime");
                        // Format: 2024-05-23T08:00:00+00:00
                        try {
                            messageDate = LocalDateTime.parse(datetime, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
                        } catch (Exception e) {
                            // Fallback
                        }
                    }

                    TelegramMessageEntity entity = new TelegramMessageEntity();
                    entity.setChatTitle(channelName);
                    entity.setChatId(0L); 
                    entity.setMessageId(messageId);
                    entity.setText(html);
                    entity.setSenderName(channelName);
                    entity.setSenderUsername(channelName);
                    entity.setReceivedAt(LocalDateTime.now());
                    entity.setMessageDate(messageDate);

                    telegramMessageRepository.save(entity);
                    log.info("Saved scraped message: {} from {}", messageId, channelName);

                } catch (Exception e) {
                    log.error("Error parsing message in channel {}", channelName, e);
                }
            }

        } catch (IOException e) {
            log.error("Failed to connect to Telegram channel: {}", channelName, e);
        }
    }
}
