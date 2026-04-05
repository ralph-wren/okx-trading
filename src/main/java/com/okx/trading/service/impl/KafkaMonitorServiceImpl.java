package com.okx.trading.service.impl;

import com.okx.trading.service.KafkaMonitorService;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.ConsumerGroupDescription;
import org.apache.kafka.clients.admin.ListConsumerGroupOffsetsResult;
import org.apache.kafka.clients.admin.MemberDescription;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Kafka 监控服务实现
 * 监控数据新增和消费者 lag
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "kline.kafka.enabled", havingValue = "true")
public class KafkaMonitorServiceImpl implements KafkaMonitorService {

    @Autowired
    private KafkaAdmin kafkaAdmin;

    @Autowired(required = false)
    private JavaMailSender mailSender;

    @Autowired(required = false)
    @Qualifier("kafkaMonitorScheduler")
    private ScheduledExecutorService kafkaMonitorScheduler;

    @Value("${kline.kafka.topic}")
    private String topic;

    @Value("${spring.kafka.consumer.group-id}")
    private String consumerGroupId;

    @Value("${kafka.monitor.data-freshness-threshold-seconds:300}")
    private long dataFreshnessThresholdSeconds;

    @Value("${kafka.monitor.consumer-lag-threshold:100}")
    private long consumerLagThreshold;

    @Value("${kafka.monitor.alert-interval-minutes:30}")
    private long alertIntervalMinutes;

    @Value("${kafka.monitor.enabled:true}")
    private boolean monitorEnabled;

    @Value("${notification.email.recipient:}")
    private String emailRecipient;

    @Value("${spring.mail.username:}")
    private String fromEmail;

    // 记录最后一次数据接收时间
    private final AtomicLong lastDataReceivedTime = new AtomicLong(System.currentTimeMillis());

    // 记录最后一次告警时间
    private Long lastDataFreshnessAlertTime;
    private Long lastConsumerLagAlertTime;

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 初始化监控任务
     * 使用独立的线程池调度监控任务
     */
    @jakarta.annotation.PostConstruct
    public void initMonitorTasks() {
        if (!monitorEnabled) {
            log.info("Kafka 监控未启用");
            return;
        }

        if (kafkaMonitorScheduler == null) {
            log.error("❌ kafkaMonitorScheduler 线程池注入失败，监控任务无法启动！");
            return;
        }

        log.info("✅ 初始化 Kafka 监控任务，使用独立线程池: {}", kafkaMonitorScheduler.getClass().getSimpleName());

        // 数据新鲜度检查任务（每分钟执行一次）
        kafkaMonitorScheduler.scheduleAtFixedRate(() -> {
            try {
                log.debug("执行数据新鲜度检查，线程: {}", Thread.currentThread().getName());
                checkDataFreshness();
            } catch (Exception e) {
                log.error("数据新鲜度检查任务执行失败", e);
            }
        }, 180, 60, TimeUnit.SECONDS);

        // 消费者 lag 检查任务（每2分钟执行一次）
        kafkaMonitorScheduler.scheduleAtFixedRate(() -> {
            try {
                log.debug("执行消费者lag检查，线程: {}", Thread.currentThread().getName());
                checkConsumerLag();
            } catch (Exception e) {
                log.error("消费者 lag 检查任务执行失败", e);
            }
        }, 180, 120, TimeUnit.SECONDS);

        log.info("✅ Kafka 监控任务已启动: 数据新鲜度检查(每60秒), 消费者lag检查(每120秒)");
    }

    @Override
    public Long getLastDataReceivedTime() {
        return lastDataReceivedTime.get();
    }

    @Override
    public void updateLastDataReceivedTime() {
        lastDataReceivedTime.set(System.currentTimeMillis());
        log.debug("更新 Kafka 数据接收时间: {}", LocalDateTime.now().format(DATE_TIME_FORMATTER));
    }

    /**
     * 定时检查数据新鲜度
     * 由线程池调度执行
     */
    @Override
    public void checkDataFreshness() {
        if (!monitorEnabled) {
            return;
        }

        try {
            long now = System.currentTimeMillis();
            long lastReceived = lastDataReceivedTime.get();
            long secondsSinceLastData = (now - lastReceived) / 1000;

            log.info("Kafka 数据新鲜度检查: 距离上次接收数据 {} 秒", secondsSinceLastData);

            // 如果超过阈值，发送告警
            if (secondsSinceLastData > dataFreshnessThresholdSeconds) {
                // 检查是否需要发送告警（避免频繁告警）
                if (shouldSendAlert(lastDataFreshnessAlertTime)) {
                    sendDataFreshnessAlert(secondsSinceLastData);
                    lastDataFreshnessAlertTime = now;
                }
            }
        } catch (Exception e) {
            log.error("检查 Kafka 数据新鲜度时发生错误", e);
        }
    }

    /**
     * 定时检查消费者 lag
     * 由线程池调度执行
     */
    @Override
    public void checkConsumerLag() {
        if (!monitorEnabled) {
            return;
        }

        try (AdminClient adminClient = AdminClient.create(kafkaAdmin.getConfigurationProperties())) {
            // 获取消费者组的偏移量
            ListConsumerGroupOffsetsResult offsetsResult = adminClient.listConsumerGroupOffsets(consumerGroupId);
            Map<TopicPartition, OffsetAndMetadata> offsets = offsetsResult.partitionsToOffsetAndMetadata().get();

            // 获取每个分区的最新偏移量
            Map<TopicPartition, Long> endOffsets = getEndOffsets(adminClient);

            // 计算总 lag
            long totalLag = 0;
            Map<Integer, Long> lagByPartition = new HashMap<>();

            for (Map.Entry<TopicPartition, OffsetAndMetadata> entry : offsets.entrySet()) {
                TopicPartition partition = entry.getKey();
                
                // 只监控指定的 topic
                if (!topic.equals(partition.topic())) {
                    continue;
                }

                long currentOffset = entry.getValue().offset();
                long endOffset = endOffsets.getOrDefault(partition, 0L);
                long lag = endOffset - currentOffset;

                lagByPartition.put(partition.partition(), lag);
                totalLag += lag;

                log.debug("分区 {} lag: {} (当前偏移: {}, 最新偏移: {})", 
                    partition.partition(), lag, currentOffset, endOffset);
            }

            log.info("Kafka 消费者 lag 检查: 总 lag = {}, 阈值 = {}", totalLag, consumerLagThreshold);

            // 如果总 lag 超过阈值，发送告警
            if (totalLag > consumerLagThreshold) {
                if (shouldSendAlert(lastConsumerLagAlertTime)) {
                    sendConsumerLagAlert(totalLag, lagByPartition);
                    lastConsumerLagAlertTime = System.currentTimeMillis();
                }
            }

        } catch (ExecutionException | InterruptedException e) {
            log.error("检查 Kafka 消费者 lag 时发生错误", e);
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.error("检查 Kafka 消费者 lag 时发生未知错误", e);
        }
    }

    /**
     * 获取每个分区的最新偏移量
     */
    private Map<TopicPartition, Long> getEndOffsets(AdminClient adminClient) {
        Map<TopicPartition, Long> endOffsets = new HashMap<>();
        
        try {
            // 获取 topic 的所有分区
            Set<TopicPartition> partitions = new HashSet<>();
            var topicDescription = adminClient.describeTopics(Collections.singletonList(topic))
                .allTopicNames().get();
            
            if (topicDescription.containsKey(topic)) {
                topicDescription.get(topic).partitions().forEach(partitionInfo -> {
                    partitions.add(new TopicPartition(topic, partitionInfo.partition()));
                });
            }

            // 获取每个分区的最新偏移量
            var offsetsResult = adminClient.listOffsets(
                partitions.stream().collect(
                    java.util.stream.Collectors.toMap(
                        tp -> tp,
                        tp -> org.apache.kafka.clients.admin.OffsetSpec.latest()
                    )
                )
            );

            offsetsResult.all().get().forEach((partition, offsetInfo) -> {
                endOffsets.put(partition, offsetInfo.offset());
            });

        } catch (Exception e) {
            log.error("获取分区最新偏移量失败", e);
        }

        return endOffsets;
    }

    /**
     * 判断是否应该发送告警（避免频繁告警）
     */
    private boolean shouldSendAlert(Long lastAlertTime) {
        if (lastAlertTime == null) {
            return true;
        }

        long now = System.currentTimeMillis();
        long minutesSinceLastAlert = (now - lastAlertTime) / 1000 / 60;

        return minutesSinceLastAlert >= alertIntervalMinutes;
    }

    /**
     * 发送数据新鲜度告警邮件（异步）
     */
    @Async("customAsyncTaskExecutor")
    private void sendDataFreshnessAlert(long secondsSinceLastData) {
        try {
            if (mailSender == null || emailRecipient == null || emailRecipient.isEmpty()) {
                log.warn("邮件服务未配置，无法发送数据新鲜度告警");
                return;
            }

            String subject = "【Kafka 监控告警】数据超过 " + (secondsSinceLastData / 60) + " 分钟未更新";
            String content = buildDataFreshnessAlertContent(secondsSinceLastData);

            sendEmail(emailRecipient, subject, content);
            log.warn("已发送 Kafka 数据新鲜度告警邮件，距离上次接收数据 {} 秒", secondsSinceLastData);

        } catch (Exception e) {
            log.error("发送数据新鲜度告警邮件失败", e);
        }
    }

    /**
     * 构建数据新鲜度告警邮件内容
     */
    private String buildDataFreshnessAlertContent(long secondsSinceLastData) {
        StringBuilder content = new StringBuilder();
        content.append("<div style='font-family: Arial, sans-serif; padding: 20px; background-color: #f5f5f5;'>");
        content.append("<h2 style='color: #cc0000;'>Kafka 数据新鲜度告警</h2>");
        content.append("<div style='background-color: white; padding: 15px; border-radius: 5px; box-shadow: 0 2px 4px rgba(0,0,0,0.1);'>");

        content.append("<h3 style='color: #cc0000;'>告警详情</h3>");
        content.append("<p><strong>Topic：</strong>").append(topic).append("</p>");
        content.append("<p><strong>告警时间：</strong>").append(LocalDateTime.now().format(DATE_TIME_FORMATTER)).append("</p>");
        content.append("<p><strong>距离上次接收数据：</strong><span style='color: #cc0000; font-weight: bold;'>")
            .append(secondsSinceLastData).append("</span> 秒（约 ")
            .append(secondsSinceLastData / 60).append(" 分钟）</p>");
        content.append("<p><strong>告警阈值：</strong>").append(dataFreshnessThresholdSeconds).append(" 秒</p>");

        content.append("<div style='background-color: #fff8e1; padding: 10px; border-left: 4px solid #ffca28; margin: 15px 0;'>");
        content.append("<p style='margin: 0;'><strong>建议操作：</strong></p>");
        content.append("<ul style='margin: 5px 0;'>");
        content.append("<li>检查 Kafka 生产者是否正常运行</li>");
        content.append("<li>检查 WebSocket 连接是否正常</li>");
        content.append("<li>检查网络连接是否正常</li>");
        content.append("<li>查看应用日志排查问题</li>");
        content.append("</ul>");
        content.append("</div>");

        content.append("</div>");
        content.append("<p style='font-size: 12px; color: #666; margin-top: 20px;'>此邮件由系统自动发送，请勿回复。</p>");
        content.append("</div>");

        return content.toString();
    }

    /**
     * 发送消费者 lag 告警邮件（异步）
     */
    @Async("customAsyncTaskExecutor")
    private void sendConsumerLagAlert(long totalLag, Map<Integer, Long> lagByPartition) {
        try {
            if (mailSender == null || emailRecipient == null || emailRecipient.isEmpty()) {
                log.warn("邮件服务未配置，无法发送消费者 lag 告警");
                return;
            }

            String subject = "【Kafka 监控告警】消费者 lag 积压过多（" + totalLag + " 条消息）";
            String content = buildConsumerLagAlertContent(totalLag, lagByPartition);

            sendEmail(emailRecipient, subject, content);
            log.warn("已发送 Kafka 消费者 lag 告警邮件，总 lag: {}", totalLag);

        } catch (Exception e) {
            log.error("发送消费者 lag 告警邮件失败", e);
        }
    }

    /**
     * 构建消费者 lag 告警邮件内容
     */
    private String buildConsumerLagAlertContent(long totalLag, Map<Integer, Long> lagByPartition) {
        StringBuilder content = new StringBuilder();
        content.append("<div style='font-family: Arial, sans-serif; padding: 20px; background-color: #f5f5f5;'>");
        content.append("<h2 style='color: #cc0000;'>Kafka 消费者 Lag 告警</h2>");
        content.append("<div style='background-color: white; padding: 15px; border-radius: 5px; box-shadow: 0 2px 4px rgba(0,0,0,0.1);'>");

        content.append("<h3 style='color: #cc0000;'>告警详情</h3>");
        content.append("<p><strong>Topic：</strong>").append(topic).append("</p>");
        content.append("<p><strong>消费者组：</strong>").append(consumerGroupId).append("</p>");
        content.append("<p><strong>告警时间：</strong>").append(LocalDateTime.now().format(DATE_TIME_FORMATTER)).append("</p>");
        content.append("<p><strong>总 Lag：</strong><span style='color: #cc0000; font-weight: bold; font-size: 18px;'>")
            .append(totalLag).append("</span> 条消息</p>");
        content.append("<p><strong>告警阈值：</strong>").append(consumerLagThreshold).append(" 条消息</p>");

        // 各分区 lag 详情
        content.append("<h3 style='color: #0066cc;'>各分区 Lag 详情</h3>");
        content.append("<table style='border-collapse: collapse; width: 100%; margin: 15px 0;'>");
        content.append("<tr style='background-color: #f2f2f2;'>");
        content.append("<th style='padding: 8px; text-align: left; border: 1px solid #ddd;'>分区</th>");
        content.append("<th style='padding: 8px; text-align: right; border: 1px solid #ddd;'>Lag（条）</th>");
        content.append("</tr>");

        lagByPartition.entrySet().stream()
            .sorted(Map.Entry.<Integer, Long>comparingByValue().reversed())
            .forEach(entry -> {
                String lagStyle = entry.getValue() > consumerLagThreshold / 4 ? 
                    " style='color: #cc0000; font-weight: bold;'" : "";
                
                content.append("<tr>");
                content.append("<td style='padding: 8px; border: 1px solid #ddd;'>分区 ")
                    .append(entry.getKey()).append("</td>");
                content.append("<td style='padding: 8px; text-align: right; border: 1px solid #ddd;'><span")
                    .append(lagStyle).append(">").append(entry.getValue()).append("</span></td>");
                content.append("</tr>");
            });

        content.append("</table>");

        content.append("<div style='background-color: #fff8e1; padding: 10px; border-left: 4px solid #ffca28; margin: 15px 0;'>");
        content.append("<p style='margin: 0;'><strong>建议操作：</strong></p>");
        content.append("<ul style='margin: 5px 0;'>");
        content.append("<li>检查消费者是否正常运行</li>");
        content.append("<li>检查消费者处理速度是否过慢</li>");
        content.append("<li>考虑增加消费者实例数量</li>");
        content.append("<li>检查是否有消费者异常或卡死</li>");
        content.append("<li>查看应用日志排查问题</li>");
        content.append("</ul>");
        content.append("</div>");

        content.append("</div>");
        content.append("<p style='font-size: 12px; color: #666; margin-top: 20px;'>此邮件由系统自动发送，请勿回复。</p>");
        content.append("</div>");

        return content.toString();
    }

    /**
     * 发送邮件
     */
    private boolean sendEmail(String to, String subject, String content) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(content, true);

            mailSender.send(message);
            log.info("邮件发送成功: {}", subject);
            return true;
        } catch (MessagingException e) {
            log.error("邮件发送失败: {}", e.getMessage(), e);
            return false;
        }
    }
}
