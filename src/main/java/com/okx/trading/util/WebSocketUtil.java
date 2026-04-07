package com.okx.trading.util;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.okx.trading.config.OkxApiConfig;
import com.okx.trading.exception.OkxApiException;
import com.okx.trading.event.WebSocketReconnectEvent;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * WebSocket工具类
 * 处理与OKX交易所的WebSocket连接和消息
 */
@Slf4j
@Component
public class WebSocketUtil {

    private final OkxApiConfig okxApiConfig;
    private final OkHttpClient okHttpClient;
    private final ApplicationEventPublisher applicationEventPublisher;

    private WebSocket publicWebSocket;
    private WebSocket bussinessWebSocket;
    private WebSocket privateWebSocket;

    private final Map<String, Consumer<JSONObject>> messageHandlers = new ConcurrentHashMap<>();
    private final ScheduledExecutorService pingScheduler;
    private final ScheduledExecutorService reconnectScheduler;
    private final ScheduledExecutorService websocketConnectScheduler;

    // 添加队列存储待执行的操作
    private final ConcurrentLinkedQueue<PendingOperation> publicPendingOperations = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<PendingOperation> privatePendingOperations = new ConcurrentLinkedQueue<>();

    // 保存已订阅的主题
    private final Set<String> publicSubscribedTopics = ConcurrentHashMap.newKeySet();
    private final Set<String> privateSubscribedTopics = ConcurrentHashMap.newKeySet();

    // 连接状态标志
    private final AtomicBoolean publicConnected = new AtomicBoolean(false);
    private final AtomicBoolean privateConnected = new AtomicBoolean(false);
    private final AtomicBoolean bussinessConnected = new AtomicBoolean(false);

    // 重连计数器 - 持久化重试状态
    private final AtomicInteger publicRetryCount = new AtomicInteger(0);
    private final AtomicInteger privateRetryCount = new AtomicInteger(0);
    private final AtomicInteger businessRetryCount = new AtomicInteger(0);

    // 最后接收消息时间，用于检测连接活跃度
    private final AtomicLong lastPublicMessageTime = new AtomicLong(System.currentTimeMillis());
    private final AtomicLong lastPrivateMessageTime = new AtomicLong(System.currentTimeMillis());
    private final AtomicLong lastBusinessMessageTime = new AtomicLong(System.currentTimeMillis());

    // 重连锁，防止并发重连
    private final Object publicReconnectLock = new Object();
    private final Object privateReconnectLock = new Object();
    private final Object businessReconnectLock = new Object();

    // 添加静态Logger以解决编译问题
    private static final Logger logger = org.slf4j.LoggerFactory.getLogger(WebSocketUtil.class);

    // 环境检测
    @Value("${spring.profiles.active:prod}")
    private String activeProfile;

    // 添加重连状态管理
    private final Set<String> reconnectingChannels = ConcurrentHashMap.newKeySet();

    // 应用关闭标志 - 用于防止在关闭过程中提交任务到已终止的线程池
    private final AtomicBoolean isShuttingDown = new AtomicBoolean(false);

    @Autowired
    public WebSocketUtil(OkxApiConfig okxApiConfig, @Qualifier("webSocketHttpClient") OkHttpClient okHttpClient, ApplicationEventPublisher applicationEventPublisher,
                         @Qualifier("websocketPingScheduler") ScheduledExecutorService pingScheduler,
                         @Qualifier("websocketReconnectScheduler") ScheduledExecutorService reconnectScheduler,
                         @Qualifier("websocketConnectScheduler") ScheduledExecutorService websocketConnectScheduler) {
        this.okxApiConfig = okxApiConfig;
        this.okHttpClient = okHttpClient;
        this.applicationEventPublisher = applicationEventPublisher;
        this.pingScheduler = pingScheduler;
        this.reconnectScheduler = reconnectScheduler;
        this.websocketConnectScheduler = websocketConnectScheduler;
    }

    /**
     * 检查是否为开发环境
     */
    private boolean isDevelopmentEnvironment() {
        return "dev".equals(activeProfile) || "development".equals(activeProfile) || "local".equals(activeProfile);
    }

    /**
     * 开发环境日志输出
     */
    private void debugLog(String message, Object... args) {
        if (isDevelopmentEnvironment()) {
            logger.info(message, args);
        }
    }

    /**
     * 初始化WebSocket连接
     */
    @PostConstruct
    public void init() {
        CompletableFuture.runAsync(() -> {
            try {
                if (okxApiConfig.isWebSocketMode()) {
                    logger.info("初始化WebSocket连接，模式: {}", okxApiConfig.getConnectionMode());
                    logger.info("公共频道URL: {}", okxApiConfig.getWs().getPublicChannel());
                    logger.info("业务频道URL: {}", okxApiConfig.getWs().getBussinessChannel());
                    logger.info("私有频道URL: {}", okxApiConfig.getWs().getPrivateChannel());

                    // 连接公共频道
                    try {
                        connectPublicChannel();
                    } catch (Exception e) {
                        logger.error("连接公共频道失败: {}", e.getMessage(), e);
                    }

                    // 连接业务频道
                    try {
                        connectBussinessChannel();
                    } catch (Exception e) {
                        logger.error("连接业务频道失败: {}", e.getMessage(), e);
                    }

                    // 连接私有频道
                    try {
                        connectPrivateChannel();
                    } catch (Exception e) {
                        logger.error("连接私有频道失败: {}", e.getMessage(), e);
                    }

                    // 优化ping频率，减少系统负载
                    pingScheduler.scheduleAtFixedRate(this::pingWebSockets, 15, 15, TimeUnit.SECONDS);

                    // 优化连接检查频率，减少无谓检查
                    reconnectScheduler.scheduleAtFixedRate(this::checkConnectionsAndReconnect, 30, 30, TimeUnit.SECONDS);
                } else {
                    logger.info("WebSocket模式未启用，使用REST模式");
                }
            } catch (Exception e) {
                logger.error("初始化WebSocket连接失败: {}", e.getMessage(), e);
            }
        }, websocketConnectScheduler);
    }

    /**
     * 清理资源
     */
    @PreDestroy
    public void cleanup() {
        // 1. 首先设置关闭标志,防止新的任务提交
        isShuttingDown.set(true);
        logger.info("应用正在关闭,设置关闭标志");

        // 2. 关闭所有WebSocket连接,防止回调继续提交任务
        try {
            if (publicWebSocket != null) {
                publicWebSocket.close(1000, "Application shutting down");
                logger.info("已关闭公共频道WebSocket连接");
            }
        } catch (Exception e) {
            logger.warn("关闭公共频道WebSocket失败: {}", e.getMessage());
        }

        try {
            if (bussinessWebSocket != null) {
                bussinessWebSocket.close(1000, "Application shutting down");
                logger.info("已关闭业务频道WebSocket连接");
            }
        } catch (Exception e) {
            logger.warn("关闭业务频道WebSocket失败: {}", e.getMessage());
        }

        try {
            if (privateWebSocket != null) {
                privateWebSocket.close(1000, "Application shutting down");
                logger.info("已关闭私有频道WebSocket连接");
            }
        } catch (Exception e) {
            logger.warn("关闭私有频道WebSocket失败: {}", e.getMessage());
        }

        // 3. 等待一小段时间,让WebSocket回调完成
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // 4. 关闭线程池
        logger.info("开始关闭线程池");
        pingScheduler.shutdown();
        reconnectScheduler.shutdown();
        websocketConnectScheduler.shutdown();

        try {
            if (!pingScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                pingScheduler.shutdownNow();
                logger.warn("Ping调度器强制关闭");
            }
            if (!reconnectScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                reconnectScheduler.shutdownNow();
                logger.warn("重连调度器强制关闭");
            }
            if (!websocketConnectScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                websocketConnectScheduler.shutdownNow();
                logger.warn("WebSocket连接调度器强制关闭");
            }
        } catch (InterruptedException e) {
            pingScheduler.shutdownNow();
            reconnectScheduler.shutdownNow();
            websocketConnectScheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }

        logger.info("WebSocket资源清理完成");
    }

    /**
     * 连接bussiness频道
     */
    private void connectBussinessChannel() {
        CompletableFuture.runAsync(() -> {
            try {
                Request request = new Request.Builder()
                        .url(okxApiConfig.getWs().getBussinessChannel())
                        .build();

                bussinessWebSocket = okHttpClient.newWebSocket(request, new WebSocketListener() {
                    @Override
                    public void onOpen(WebSocket webSocket, Response response) {
                        logger.info("业务频道WebSocket连接成功");
                        bussinessConnected.set(true);
                        lastBusinessMessageTime.set(System.currentTimeMillis());

                        // 重连成功，重置重试计数器
                        businessRetryCount.set(0);

                        // 恢复业务频道的操作
                        restoreBusinessOperations();
                    }

                    @Override
                    public void onMessage(WebSocket webSocket, String text) {
                        lastBusinessMessageTime.set(System.currentTimeMillis());
                        handleMessage(text);
                    }

                    @Override
                    public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                        logger.error("业务频道WebSocket连接失败", t);
                        bussinessConnected.set(false);
                        // 尝试重连
                        scheduleBusinessReconnect();
                    }

                    @Override
                    public void onClosed(WebSocket webSocket, int code, String reason) {
                        logger.info("业务频道WebSocket连接关闭: {}, {}", code, reason);
                        bussinessConnected.set(false);
                        // 如果不是应用主动关闭，尝试重连
                        if (code != 1000) {
                            scheduleBusinessReconnect();
                        }
                    }
                });
            } catch (Exception e) {
                logger.error("创建业务频道WebSocket连接失败", e);
                bussinessConnected.set(false);
                scheduleBusinessReconnect();
            }
        }, websocketConnectScheduler);
    }

    /**
     * 连接公共频道
     */
    private void connectPublicChannel() {
        CompletableFuture.runAsync(() -> {
            try {
                Request request = new Request.Builder()
                        .url(okxApiConfig.getWs().getPublicChannel())
                        .build();

                publicWebSocket = okHttpClient.newWebSocket(request, new WebSocketListener() {
                    @Override
                    public void onOpen(WebSocket webSocket, Response response) {
                        logger.info("公共频道WebSocket连接成功");
                        publicConnected.set(true);
                        lastPublicMessageTime.set(System.currentTimeMillis());

                        // 重连成功，重置重试计数器
                        publicRetryCount.set(0);

                        // 恢复之前的操作
                        restorePublicOperations();
                    }

                    @Override
                    public void onMessage(WebSocket webSocket, String text) {
                        lastPublicMessageTime.set(System.currentTimeMillis());
                        handleMessage(text);
                    }

                    @Override
                    public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                        logger.error("公共频道WebSocket连接失败", t);
                        publicConnected.set(false);
                        // 尝试重连
                        schedulePublicReconnect();
                    }

                    @Override
                    public void onClosed(WebSocket webSocket, int code, String reason) {
                        logger.info("公共频道WebSocket连接关闭: {}, {}", code, reason);
                        publicConnected.set(false);
                        // 如果不是应用主动关闭，尝试重连
                        if (code != 1000) {
                            schedulePublicReconnect();
                        }
                    }
                });
            } catch (Exception e) {
                logger.error("创建公共频道WebSocket连接失败", e);
                publicConnected.set(false);
                schedulePublicReconnect();
            }
        }, websocketConnectScheduler);
    }

    /**
     * 连接私有频道
     */
    private void connectPrivateChannel() {
        CompletableFuture.runAsync(() -> {
            try {
                // 登录认证参数
                String timestamp = System.currentTimeMillis() / 1000 + "";
                String method = "GET";
                String requestPath = "/users/self/verify";
                String body = "";
                String sign = SignatureUtil.sign(timestamp, method, requestPath, body, okxApiConfig.getSecretKey());

                Request request = new Request.Builder()
                        .url(okxApiConfig.getWs().getPrivateChannel())
                        .build();

                privateWebSocket = okHttpClient.newWebSocket(request, new WebSocketListener() {
                    @Override
                    public void onOpen(WebSocket webSocket, Response response) {
                        logger.info("私有频道WebSocket连接成功");
                        lastPrivateMessageTime.set(System.currentTimeMillis());

                        // 重连成功，重置重试计数器
                        privateRetryCount.set(0);

                        // 发送登录消息 - 登录成功后会在登录响应中恢复订阅
                        sendLoginMessage(webSocket, timestamp, sign);
                    }

                    @Override
                    public void onMessage(WebSocket webSocket, String text) {
                        lastPrivateMessageTime.set(System.currentTimeMillis());
                        handleMessage(text);
                    }

                    @Override
                    public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                        logger.error("私有频道WebSocket连接失败", t);
                        privateConnected.set(false);
                        // 尝试重连
                        schedulePrivateReconnect();
                    }

                    @Override
                    public void onClosed(WebSocket webSocket, int code, String reason) {
                        logger.info("私有频道WebSocket连接关闭: {}, {}", code, reason);
                        privateConnected.set(false);
                        // 如果不是应用主动关闭，且不是Reconnecting状态，尝试重连
                        if (code != 1000 || !"Application shutting down".equals(reason)) {
                            // 添加延迟重连，避免立即重连
                            reconnectScheduler.schedule(() -> {
                                if (!privateConnected.get()) {
                                    schedulePrivateReconnect();
                                }
                            }, 2, TimeUnit.SECONDS);
                        }
                    }
                });
            } catch (Exception e) {
                logger.error("连接私有频道失败", e);
                privateConnected.set(false);
                schedulePrivateReconnect();
            }
        }, websocketConnectScheduler);
    }

    /**
     * 发送登录消息
     */
    private void sendLoginMessage(WebSocket webSocket, String timestamp, String sign) {
        try {
            JSONObject loginMessage = new JSONObject();
            loginMessage.put("op", "login");

            JSONObject arg = new JSONObject();
            arg.put("apiKey", okxApiConfig.getApiKey());
            arg.put("passphrase", okxApiConfig.getPassphrase());
            arg.put("timestamp", timestamp);
            arg.put("sign", sign);

            JSONObject[] args = new JSONObject[]{arg};
            loginMessage.put("args", args);

            webSocket.send(loginMessage.toJSONString());
            debugLog("发送登录消息: {}", loginMessage);
        } catch (Exception e) {
            logger.error("发送登录消息失败", e);
        }
    }

    /**
     * 安排私有频道重连 - 优化版本
     */
    private void schedulePrivateReconnect() {
        // 检查应用是否正在关闭
        if (isShuttingDown.get()) {
            logger.info("应用正在关闭,跳过私有频道重连");
            return;
        }

        // 检查线程池是否已关闭
        if (reconnectScheduler.isShutdown() || reconnectScheduler.isTerminated()) {
            logger.warn("重连调度器已关闭,无法安排私有频道重连");
            return;
        }

        if (isCurrentlyReconnecting("private")) {
            debugLog("私有频道已在重连中，跳过本次重连请求");
            return;
        }

        markReconnectStart("private");

        try {
            reconnectScheduler.submit(() -> {
            try {
                int currentRetry = privateRetryCount.getAndIncrement();

                // 优化重连延迟策略：更快的初始重连，渐进式增加延迟
                long delaySeconds;
                if (currentRetry == 0) {
                    delaySeconds = 1; // 首次重连立即执行
                } else if (currentRetry <= 3) {
                    delaySeconds = currentRetry * 2; // 2, 4, 6秒
                } else if (currentRetry <= 10) {
                    delaySeconds = 10; // 保持10秒
                } else {
                    delaySeconds = 20; // 最大20秒，而不是30秒
                    privateRetryCount.set(10); // 限制最大重试次数
                }

                debugLog("私有频道重连尝试 #{}, 延迟 {} 秒", currentRetry, delaySeconds);

                if (delaySeconds > 1) {
                    Thread.sleep(delaySeconds * 1000);
                }

                if (privateWebSocket != null) {
                    try {
                        privateWebSocket.close(1000, "Reconnecting");
                    } catch (Exception e) {
                        debugLog("关闭旧私有频道连接失败: {}", e.getMessage());
                    }
                }

                connectPrivateChannel();

                // 检查重连是否成功 - 延长等待时间，因为私有频道需要登录
                Thread.sleep(5000); // 等待5秒检查连接状态
                if (privateConnected.get()) {
                    // 重连成功，重置重试计数器
                    privateRetryCount.set(0);
                    logger.info("私有频道重连成功");
                } else {
                    // 如果重连失败，继续安排下一次重试
                    logger.warn("私有频道重连失败，将安排下一次重试");
                    markReconnectEnd("private");
                    schedulePrivateReconnect();
                    return;
                }
            } catch (Exception e) {
                logger.error("重连私有频道失败", e);
                markReconnectEnd("private");
                schedulePrivateReconnect();
                return;
            } finally {
                markReconnectEnd("private");
            }
        });
        } catch (RejectedExecutionException e) {
            logger.warn("无法提交私有频道重连任务,线程池可能已关闭: {}", e.getMessage());
            markReconnectEnd("private");
        } catch (Exception e) {
            logger.error("安排私有频道重连失败", e);
            markReconnectEnd("private");
        }
    }

    /**
     * 发送ping消息，保持连接活跃 - 优化版本
     */
    private void pingWebSockets() {
        try {
            long currentTime = System.currentTimeMillis();

            // 检查公共频道连接活跃度 - 只有连接正常时才发送ping
            if (publicWebSocket != null && publicConnected.get()) {
                // 延长ping检查时间，避免频繁检测
                if (currentTime - lastPublicMessageTime.get() > 60000) {
                    logger.warn("公共频道超过60秒没有收到消息，触发重连");
                    schedulePublicReconnect();
                } else {
                    try {
                        publicWebSocket.send("ping");
//                        debugLog("发送公共频道ping消息");
                    } catch (Exception e) {
                        logger.warn("发送公共频道ping消息失败，将尝试重连", e);
                        schedulePublicReconnect();
                    }
                }
            }

            // 检查业务频道连接活跃度 - 业务频道不强制要求频繁ping
            if (bussinessWebSocket != null && bussinessConnected.get()) {
                // 业务频道可能长时间没有消息，所以不基于消息时间触发重连
                try {
                    bussinessWebSocket.send("ping");
//                    debugLog("发送业务频道ping消息");
                } catch (Exception e) {
                    logger.warn("发送业务频道ping消息失败，将尝试重连", e);
                    scheduleBusinessReconnect();
                }
            }

            // 检查私有频道连接活跃度 - 私有频道也不强制要求频繁ping
            if (privateWebSocket != null && privateConnected.get()) {
                // 私有频道可能长时间没有消息，所以不基于消息时间触发重连
                try {
                    privateWebSocket.send("ping");
//                    debugLog("发送私有频道ping消息");
                } catch (Exception e) {
                    logger.warn("发送私有频道ping消息失败，将尝试重连", e);
                    schedulePrivateReconnect();
                }
            }
        } catch (Exception e) {
            logger.error("发送ping消息失败", e);
        }
    }

    /**
     * 处理接收到的WebSocket消息
     */
    @Async("klineHandleScheduler")
    private void handleMessage(String message) {
        try {
            // 处理简单的ping-pong响应
            if ("ping".equals(message)) {
                if (publicWebSocket != null) {
                    publicWebSocket.send("pong");
                }
                if (privateWebSocket != null) {
                    privateWebSocket.send("pong");
                }
                debugLog("收到ping消息，已回复pong");
                return;
            }

            if ("pong".equals(message)) {
//                debugLog("收到简单pong响应");
                return;
            }

            JSONObject jsonMessage;
            try {
                jsonMessage = JSON.parseObject(message);
            } catch (Exception e) {
                logger.warn("无法解析WebSocket消息为JSON: {}", message);
                return;
            }

            // 处理错误消息
            if (jsonMessage.containsKey("event") && "error".equals(jsonMessage.getString("event"))) {
                String errorCode = jsonMessage.getString("code");
                String errorMsg = jsonMessage.getString("msg");

//                logger.error("收到WebSocket错误: code={}, msg={}", errorCode, errorMsg);

                // 处理特定错误
                switch (errorCode) {
                    case "60004": // 时间戳错误
                        logger.info("时间戳错误，尝试重新连接私有频道");
                        // 立即尝试重新连接
                        if (privateWebSocket != null) {
                            privateWebSocket.close(1000, "Reconnecting due to timestamp error");
                        }

                        // 使用短延迟重连
                        reconnectScheduler.schedule(this::connectPrivateChannel, 1, TimeUnit.SECONDS);
                        break;
                    case "60012": // 非法请求
                    case "60018": // 非法请求
                    case "60013":
//                        logger.warn("非法请求错误: {}", errorMsg);
//                        break;
                    default:
//                        logger.warn("未处理的WebSocket错误: code={}, msg={}", errorCode, errorMsg);
                        break;
                }

                throw new OkxApiException(Integer.parseInt(errorCode), "WebSocket错误: " + errorMsg);
            }

            // 处理JSON格式的pong响应
            if (jsonMessage.containsKey("op") && "pong".equals(jsonMessage.getString("op"))) {
                debugLog("收到JSON格式pong响应: {}", message);
                return;
            }

            // 处理登录事件响应 - 处理 {"event":"login","msg":"","code":"0","connId":"b0a88f7d"} 格式
            if (jsonMessage.containsKey("event") && "login".equals(jsonMessage.getString("event"))) {
                if (jsonMessage.containsKey("code") && "0".equals(jsonMessage.getString("code"))) {
                    // 登录成功
                    logger.info("WebSocket登录成功(事件方式): connId={}", jsonMessage.getString("connId"));
                    privateConnected.set(true);
                    // 登录成功后恢复私有频道的订阅
                    restorePrivateOperations();
                    // 自动订阅账户余额更新
                    subscribeToBalanceUpdates();
                } else {
                    // 登录失败
                    String code = jsonMessage.getString("code");
                    String msg = jsonMessage.getString("msg");
                    logger.error("WebSocket登录失败(事件方式): code={}, msg={}", code, msg);
                    privateConnected.set(false);

                    // 如果是时间戳错误，重新连接
                    if ("60004".equals(code)) {
                        logger.info("登录时间戳错误，重新连接");
                        schedulePrivateReconnect();
                    }
                }
                return;
            }

            // 处理频道连接计数 - 处理 {"event":"channel-conn-count","channel":"account","connCount":"1","connId":"b0a88f7d"} 格式
            if (jsonMessage.containsKey("event") && "channel-conn-count".equals(jsonMessage.getString("event"))) {
                String channel = jsonMessage.getString("channel");
                String connCount = jsonMessage.getString("connCount");
                String connId = jsonMessage.getString("connId");
                logger.info("频道连接计数: channel={}, connCount={}, connId={}", channel, connCount, connId);
                return;
            }

            // 处理OP类型登录响应 - 处理 {"op":"login"} 格式
            if (jsonMessage.containsKey("op") && "login".equals(jsonMessage.getString("op"))) {
                if (jsonMessage.containsKey("code") && !"0".equals(jsonMessage.getString("code"))) {
                    // 登录失败
                    logger.error("WebSocket登录失败(op方式): {}", message);
                    privateConnected.set(false);

                    // 如果是时间戳错误，重新连接
                    if ("60004".equals(jsonMessage.getString("code"))) {
                        logger.info("登录时间戳错误，重新连接");
                        schedulePrivateReconnect();
                    }
                } else {
                    // 登录成功
                    logger.info("WebSocket登录成功(op方式): {}", message);
                    privateConnected.set(true);
                    // 登录成功后恢复私有频道的订阅
                    restorePrivateOperations();
                    // 自动订阅账户余额更新
                    subscribeToBalanceUpdates();
                }
                return;
            }

            // 根据消息类型路由到相应的处理器
            String topic = null;
            if (jsonMessage.containsKey("arg") && jsonMessage.getJSONObject("arg").containsKey("channel")) {
                topic = jsonMessage.getJSONObject("arg").getString("channel");
            }

            if (jsonMessage.containsKey("op")) {
                topic = jsonMessage.getString("op");
            }

            if (topic != null && messageHandlers.containsKey(topic)) {
                messageHandlers.get(topic).accept(jsonMessage);
            } else {
                debugLog("收到未处理的WebSocket消息: {}", message);
            }

        } catch (Exception e) {
            logger.error("解析WebSocket消息失败: {}", message, e);
        }
    }

    /**
     * 注册消息处理器
     *
     * @param topic   订阅主题
     * @param handler 消息处理器
     */
    public void registerHandler(String topic, Consumer<JSONObject> handler) {
        messageHandlers.put(topic, handler);
    }

    /**
     * 订阅公共频道主题
     *
     * @param topic  主题
     * @param symbol 交易对
     */
    public void subscribePublicTopic(String topic, String symbol) {
        // 创建订阅消息
        JSONObject subscribeMessage = new JSONObject();
        subscribeMessage.put("op", "subscribe");

        JSONObject arg = new JSONObject();
        arg.put("channel", topic);
        arg.put("instId", symbol);

        JSONObject[] args = new JSONObject[]{arg};
        subscribeMessage.put("args", args);

        // 将主题添加到已订阅集合中
        String key = topic + ":" + symbol;
        publicSubscribedTopics.add(key);

        // 检查连接状态，决定是立即发送还是加入待执行队列
        if (publicConnected.get() && publicWebSocket != null) {
            publicWebSocket.send(subscribeMessage.toJSONString());
            logger.info("订阅公共频道主题: {}, 交易对: {}", topic, symbol);
        } else {
            // 如果未连接，加入待执行队列
            PendingOperation operation = new PendingOperation(
                    "公共频道订阅: " + key,
                    () -> {
                        if (publicWebSocket != null) {
                            publicWebSocket.send(subscribeMessage.toJSONString());
                            logger.info("恢复订阅公共频道主题: {}, 交易对: {}", topic, symbol);
                        } else {
                            throw new OkxApiException("公共频道WebSocket未连接");
                        }
                    }
            );
            publicPendingOperations.offer(operation);
            logger.info("添加待执行的公共频道订阅: {}, 交易对: {}", topic, symbol);
        }
    }

    /**
     * 订阅私有频道主题
     *
     * @param topic 主题
     */
    public void subscribePrivateTopic(String topic) {
        // 创建订阅消息
        JSONObject subscribeMessage = new JSONObject();
        subscribeMessage.put("op", "subscribe");

        JSONObject arg = new JSONObject();
        arg.put("channel", topic);
        if (topic.equals("orders")) {
            arg.put("instType", "SPOT");
        }

        JSONObject[] args = new JSONObject[]{arg};
        subscribeMessage.put("args", args);

        // 将主题添加到已订阅集合中
        privateSubscribedTopics.add(topic);

        // 检查连接状态，决定是立即发送还是加入待执行队列
        if (privateConnected.get() && privateWebSocket != null) {
            privateWebSocket.send(subscribeMessage.toJSONString());
            logger.info("订阅私有频道主题: {}", topic);
        } else {
            // 如果未连接，加入待执行队列
            PendingOperation operation = new PendingOperation(
                    "私有频道订阅: " + topic,
                    () -> {
                        if (privateWebSocket != null) {
                            privateWebSocket.send(subscribeMessage.toJSONString());
                            logger.info("恢复订阅私有频道主题: {}", topic);
                        } else {
                            throw new OkxApiException("私有频道WebSocket未连接");
                        }
                    }
            );
            privatePendingOperations.offer(operation);
            logger.info("添加待执行的私有频道订阅: {}", topic);
        }
    }

    /**
     * 取消订阅公共频道主题
     *
     * @param topic  主题
     * @param symbol 交易对
     */
    public void unsubscribePublicTopic(String topic, String symbol) {
        if (publicWebSocket == null) {
            return;
        }

        JSONObject unsubscribeMessage = new JSONObject();
        unsubscribeMessage.put("op", "unsubscribe");

        JSONObject arg = new JSONObject();
        arg.put("channel", topic);
        arg.put("instId", symbol);

        JSONObject[] args = new JSONObject[]{arg};
        unsubscribeMessage.put("args", args);

        // 从已订阅集合中移除
        String key = topic + ":" + symbol;
        publicSubscribedTopics.remove(key);

        // 仅在连接可用时发送
        if (publicConnected.get()) {
            publicWebSocket.send(unsubscribeMessage.toJSONString());
            logger.info("取消订阅公共频道主题: {}, 交易对: {}", topic, symbol);
        }
    }

    /**
     * 取消订阅私有频道主题
     *
     * @param topic 主题
     */
    public void unsubscribePrivateTopic(String topic) {
        if (privateWebSocket == null) {
            return;
        }

        JSONObject unsubscribeMessage = new JSONObject();
        unsubscribeMessage.put("op", "unsubscribe");

        JSONObject arg = new JSONObject();
        arg.put("channel", topic);

        JSONObject[] args = new JSONObject[]{arg};
        unsubscribeMessage.put("args", args);

        // 从已订阅集合中移除
        privateSubscribedTopics.remove(topic);

        // 仅在连接可用时发送
        if (privateConnected.get()) {
            privateWebSocket.send(unsubscribeMessage.toJSONString());
            logger.info("取消订阅私有频道主题: {}", topic);
        }
    }

    /**
     * 发送私有请求消息
     *
     * @param message 请求消息
     */
    public void sendPrivateRequest(String message) {
        if (privateConnected.get() && privateWebSocket != null) {
            privateWebSocket.send(message);
            logger.info("发送私有请求: {}", message);
        } else {
            // 如果未连接，加入待执行队列
            PendingOperation operation = new PendingOperation(
                    "私有频道请求: " + message,
                    () -> {
                        if (privateWebSocket != null) {
                            privateWebSocket.send(message);
                            logger.info("恢复发送私有请求: {}", message);
                        } else {
                            throw new OkxApiException("私有频道WebSocket未连接");
                        }
                    }
            );
            privatePendingOperations.offer(operation);
            logger.info("添加待执行的私有频道请求");
        }
    }

    /**
     * 订阅公共频道主题（带自定义参数）
     *
     * @param arg 订阅参数对象
     */
    public void subscribePublicTopicWithArgs(JSONObject arg, String... symbols) {
        // 创建订阅消息
        JSONObject subscribeMessage = new JSONObject();
        subscribeMessage.put("op", "subscribe");

        JSONObject[] args = new JSONObject[]{arg};
        subscribeMessage.put("args", args);

        // 生成一个唯一标识
        String key = "custom:" + arg.toJSONString();
        publicSubscribedTopics.add(key);

        WebSocket targetSocket;
        ConcurrentLinkedQueue<PendingOperation> targetQueue;

        if (symbols != null && symbols.length > 0) {
            targetSocket = bussinessWebSocket;
            if (!bussinessConnected.get() || targetSocket == null) {
                // 如果未连接，加入待执行队列
                PendingOperation operation = new PendingOperation(
                        "业务频道自定义订阅: " + key,
                        () -> {
                            if (bussinessWebSocket != null) {
                                bussinessWebSocket.send(subscribeMessage.toJSONString());
                                logger.info("恢复订阅业务频道自定义主题，参数: {}", arg);
                            } else {
                                throw new OkxApiException("业务频道WebSocket未连接");
                            }
                        }
                );
                publicPendingOperations.offer(operation);
                logger.info("添加待执行的业务频道自定义订阅，参数: {}", arg);
                return;
            }
        } else {
            targetSocket = publicWebSocket;
            if (!publicConnected.get() || targetSocket == null) {
                // 如果未连接，加入待执行队列
                PendingOperation operation = new PendingOperation(
                        "公共频道自定义订阅: " + key,
                        () -> {
                            if (publicWebSocket != null) {
                                publicWebSocket.send(subscribeMessage.toJSONString());
//                                logger.info("恢复订阅公共频道自定义主题，参数: {}", arg);
                            } else {
                                throw new OkxApiException("公共频道WebSocket未连接");
                            }
                        }
                );
                publicPendingOperations.offer(operation);
                logger.info("添加待执行的公共频道自定义订阅，参数: {}", arg);
                return;
            }
        }

        // 直接发送
        targetSocket.send(subscribeMessage.toJSONString());
        debugLog("订阅公共频道主题，参数: {}", arg);
    }

    /**
     * 取消订阅公共频道主题（带自定义参数）
     *
     * @param arg 取消订阅参数对象
     */
    public void unsubscribePublicTopicWithArgs(JSONObject arg, String... symbols) {
        if (publicWebSocket == null || bussinessWebSocket == null) {
            logger.warn("公共频道WebSocket未连接，无法取消订阅");
            return;
        }

        JSONObject unsubscribeMessage = new JSONObject();
        unsubscribeMessage.put("op", "unsubscribe");

        JSONObject[] args = new JSONObject[]{arg};
        unsubscribeMessage.put("args", args);

        // 从已订阅集合中移除
        String key = "custom:" + arg.toJSONString();
        publicSubscribedTopics.remove(key);

        WebSocket targetSocket;
        if (symbols != null && symbols.length > 0) {
            targetSocket = bussinessWebSocket;
            if (!bussinessConnected.get()) {
                return;
            }
        } else {
            targetSocket = publicWebSocket;
            if (!publicConnected.get()) {
                return;
            }
        }

        // 直接发送
        targetSocket.send(unsubscribeMessage.toJSONString());
        logger.info("取消订阅公共频道主题，参数: {}", arg);
    }

    /**
     * 恢复公共频道的操作
     */
    private void restorePublicOperations() {
        // 检查应用是否正在关闭
        if (isShuttingDown.get()) {
            logger.info("应用正在关闭,跳过恢复公共频道操作");
            return;
        }

        // 检查线程池是否已关闭
        if (websocketConnectScheduler.isShutdown() || websocketConnectScheduler.isTerminated()) {
            logger.warn("WebSocket连接调度器已关闭,无法恢复公共频道操作");
            return;
        }

        try {
            CompletableFuture.runAsync(() -> {
                logger.info("开始恢复公共频道订阅，共 {} 个待执行操作", publicPendingOperations.size());

            // 如果两个连接都已经就绪，才开始恢复操作
            if (!publicConnected.get() || !bussinessConnected.get()) {
                logger.info("公共频道连接尚未就绪，等待所有连接建立后再恢复");
                return;
            }

            // 首先恢复队列中所有待执行的操作
            int count = 0;
            while (!publicPendingOperations.isEmpty()) {
                PendingOperation operation = publicPendingOperations.poll();
                if (operation != null) {
                    try {
                        logger.info("恢复执行公共频道操作: {}", operation.getDescription());
                        operation.execute();
                        count++;
                    } catch (Exception e) {
                        logger.error("恢复执行公共频道操作失败: {}", operation.getDescription(), e);
                        // 如果执行失败，重新添加到队列末尾
                        publicPendingOperations.offer(operation);
                    }
                }
            }

            // 重新订阅所有已记录的主题，确保所有币种都能及时更新价格
            int topicCount = 0;
            for (String topic : publicSubscribedTopics) {
                try {
                    // 解析主题格式（例如："tickers:BTC-USDT"）
                    if (topic.contains(":")) {
                        String[] parts = topic.split(":");
                        String channel = parts[0];
                        String symbol = parts[1];

                        // 如果主题格式正确且不是自定义主题，重新订阅
                        if (parts.length == 2 && !channel.equals("custom")) {
                            logger.info("重新订阅主题: {}, 交易对: {}", channel, symbol);

                            // 创建订阅消息
                            JSONObject subscribeMessage = new JSONObject();
                            subscribeMessage.put("op", "subscribe");

                            JSONObject arg = new JSONObject();
                            arg.put("channel", channel);
                            arg.put("instId", symbol);

                            JSONObject[] args = new JSONObject[]{arg};
                            subscribeMessage.put("args", args);

                            // 直接发送订阅请求
                            publicWebSocket.send(subscribeMessage.toJSONString());
                            topicCount++;
                        }
                    }
                } catch (Exception e) {
                    logger.error("重新订阅主题失败: {}", topic, e);
                }
            }

            logger.info("公共频道操作恢复完成，成功执行 {} 个操作，重新订阅 {} 个主题", count, topicCount);

            // 发布WebSocket重连事件
            try {
                // 由于WebSocketUtil不是Spring Bean，无法直接访问ApplicationEventPublisher
                // 使用反射查找Spring上下文，获取ApplicationEventPublisher
                // 静态方法获取ApplicationContext
                if (applicationEventPublisher != null) {
                    logger.info("发布WebSocket公共频道重连事件");
                    applicationEventPublisher.publishEvent(new WebSocketReconnectEvent(this, WebSocketReconnectEvent.ReconnectType.PUBLIC));
                }
            } catch (Exception e) {
                logger.error("发布WebSocket重连事件失败", e);
            }
        }, websocketConnectScheduler);
        } catch (RejectedExecutionException e) {
            logger.warn("无法提交恢复公共频道操作任务,线程池可能已关闭: {}", e.getMessage());
        } catch (Exception e) {
            logger.error("恢复公共频道操作失败", e);
        }
    }

    /**
     * 恢复私有频道的操作
     */
    private void restorePrivateOperations() {
        // 检查应用是否正在关闭
        if (isShuttingDown.get()) {
            logger.info("应用正在关闭,跳过恢复私有频道操作");
            return;
        }

        // 检查线程池是否已关闭
        if (reconnectScheduler.isShutdown() || reconnectScheduler.isTerminated()) {
            logger.warn("重连调度器已关闭,无法恢复私有频道操作");
            return;
        }

        try {
            CompletableFuture.runAsync(() -> {
            logger.info("开始恢复私有频道订阅，共 {} 个待执行操作", privatePendingOperations.size());

            if (!privateConnected.get()) {
                logger.info("私有频道连接尚未就绪，等待连接建立后再恢复");
                return;
            }

            // 遍历并执行所有待执行的操作
            int count = 0;
            while (!privatePendingOperations.isEmpty()) {
                PendingOperation operation = privatePendingOperations.poll();
                if (operation != null) {
                    try {
                        logger.info("恢复执行私有频道操作: {}", operation.getDescription());
                        operation.execute();
                        count++;
                    } catch (Exception e) {
                        logger.error("恢复执行私有频道操作失败: {}", operation.getDescription(), e);
                        // 如果执行失败，重新添加到队列末尾
                        privatePendingOperations.offer(operation);
                    }
                }
            }

            logger.info("私有频道操作恢复完成，成功执行 {} 个操作", count);

            // 确保account频道被订阅
            if (privateSubscribedTopics.isEmpty() || !privateSubscribedTopics.contains("account")) {
                subscribeToBalanceUpdates();
            }

            // 发布WebSocket重连事件
            try {
                if (applicationEventPublisher != null) {
                    logger.info("发布WebSocket私有频道重连事件");
                    applicationEventPublisher.publishEvent(new WebSocketReconnectEvent(this, WebSocketReconnectEvent.ReconnectType.PRIVATE));
                }
            } catch (Exception e) {
                logger.error("发布WebSocket重连事件失败", e);
            }
        }, reconnectScheduler);
        } catch (RejectedExecutionException e) {
            logger.warn("无法提交恢复私有频道操作任务,线程池可能已关闭: {}", e.getMessage());
        } catch (Exception e) {
            logger.error("恢复私有频道操作失败", e);
        }
    }

    /**
     * 恢复业务频道的操作
     */
    private void restoreBusinessOperations() {
        // 检查应用是否正在关闭
        if (isShuttingDown.get()) {
            logger.info("应用正在关闭,跳过恢复业务频道操作");
            return;
        }

        // 检查线程池是否已关闭
        if (reconnectScheduler.isShutdown() || reconnectScheduler.isTerminated()) {
            logger.warn("重连调度器已关闭,无法恢复业务频道操作");
            return;
        }

        try {
            CompletableFuture.runAsync(() -> {
            logger.info("开始恢复业务频道操作");

            if (!bussinessConnected.get()) {
                logger.info("业务频道连接尚未就绪，等待连接建立后再恢复");
                return;
            }

            // 目前业务频道没有特定的待执行操作队列
            // 可以在这里添加业务频道相关的恢复逻辑
            logger.info("业务频道操作恢复完成");

            // 发布WebSocket重连事件（已在调用处处理）
        }, reconnectScheduler);
        } catch (RejectedExecutionException e) {
            logger.warn("无法提交恢复业务频道操作任务,线程池可能已关闭: {}", e.getMessage());
        } catch (Exception e) {
            logger.error("恢复业务频道操作失败", e);
        }
    }

    /**
     * 检查私有WebSocket是否已连接
     *
     * @return 如果私有WebSocket已连接则返回true，否则返回false
     */
    public boolean isPrivateSocketConnected() {
        return privateWebSocket != null;
    }

    /**
     * 检查公共WebSocket是否已连接
     *
     * @return 如果公共WebSocket已连接则返回true，否则返回false
     */
    public boolean isPublicSocketConnected() {
        return publicWebSocket != null;
    }

    /**
     * 定时检查所有WebSocket连接并在需要时重新连接 - 优化版本
     */
    private void checkConnectionsAndReconnect() {
        try {
            long currentTime = System.currentTimeMillis();

            // 检查公共频道连接状态 - 公共频道必须保持活跃
            if (!publicConnected.get() || publicWebSocket == null ||
                    (currentTime - lastPublicMessageTime.get() > 120000)) { // 增加到2分钟容忍度
                logger.warn("公共频道连接检测失败，状态: {}, WebSocket: {}, 最后消息时间: {} 秒前",
                        publicConnected.get(),
                        publicWebSocket != null ? "存在" : "null",
                        (currentTime - lastPublicMessageTime.get()) / 1000);

                // 避免并发重连
                if (!isCurrentlyReconnecting("public")) {
                    schedulePublicReconnect();
                }
            }

            // 检查业务频道连接状态 - 业务频道容忍度更高
            if (!bussinessConnected.get() || bussinessWebSocket == null) {
                // 如果连接状态为false或WebSocket为null，才认为需要重连
                // 不基于消息时间判断，因为业务频道可能长时间没有消息
                logger.warn("业务频道连接检测失败，状态: {}, WebSocket: {}, 最后消息时间: {} 秒前",
                        bussinessConnected.get(),
                        bussinessWebSocket != null ? "存在" : "null",
                        (currentTime - lastBusinessMessageTime.get()) / 1000);

                if (!isCurrentlyReconnecting("business")) {
                    scheduleBusinessReconnect();
                }
            } else if (currentTime - lastBusinessMessageTime.get() > 300000) {
                // 只有超过5分钟没有消息时才基于消息时间重连
                logger.warn("业务频道超过5分钟没有消息，尝试重连");
                if (!isCurrentlyReconnecting("business")) {
                    scheduleBusinessReconnect();
                }
            }

            // 检查私有频道连接状态 - 私有频道容忍度更高
            if (!privateConnected.get() || privateWebSocket == null) {
                logger.warn("私有频道连接检测失败，状态: {}, WebSocket: {}, 最后消息时间: {} 秒前",
                        privateConnected.get(),
                        privateWebSocket != null ? "存在" : "null",
                        (currentTime - lastPrivateMessageTime.get()) / 1000);

                if (!isCurrentlyReconnecting("private")) {
                    schedulePrivateReconnect();
                }
            } else if (currentTime - lastPrivateMessageTime.get() > 300000) {
                // 只有超过5分钟没有消息时才基于消息时间重连
                logger.warn("私有频道超过5分钟没有消息，尝试重连");
                if (!isCurrentlyReconnecting("private")) {
                    schedulePrivateReconnect();
                }
            }
        } catch (Exception e) {
            logger.error("检查WebSocket连接状态时出错", e);
        }
    }

    /**
     * 检查指定频道是否正在重连中
     */
    private boolean isCurrentlyReconnecting(String channel) {
        return reconnectingChannels.contains(channel);
    }

    /**
     * 标记频道开始重连
     */
    private void markReconnectStart(String channel) {
        reconnectingChannels.add(channel);
    }

    /**
     * 标记频道重连结束
     */
    private void markReconnectEnd(String channel) {
        reconnectingChannels.remove(channel);
    }

    /**
     * 检查是否正在重连中，避免并发重连
     */
    private boolean isReconnecting(Object lock) {
        // 简化重连检查逻辑
        return false; // 始终允许重连，由新的重连状态管理器控制
    }

    /**
     * 检查WebSocket连接是否有效 - 优化版本
     *
     * @param webSocket 要检查的WebSocket连接
     * @return 连接是否有效
     */
    private boolean isWebSocketConnected(WebSocket webSocket) {
        if (webSocket == null) {
            return false;
        }

        try {
            // 使用更轻量级的方式检查连接状态，避免过多的ping消息
            return webSocket.send("ping");
        } catch (Exception e) {
            debugLog("检查WebSocket连接状态失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 待执行操作类
     */
    private static class PendingOperation {
        private final String description;
        private final Runnable operation;

        public PendingOperation(String description, Runnable operation) {
            this.description = description;
            this.operation = operation;
        }

        public void execute() {
            operation.run();
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * 安排业务频道重连 - 优化版本
     */
    private void scheduleBusinessReconnect() {
        // 检查应用是否正在关闭
        if (isShuttingDown.get()) {
            logger.info("应用正在关闭,跳过业务频道重连");
            return;
        }

        // 检查线程池是否已关闭
        if (reconnectScheduler.isShutdown() || reconnectScheduler.isTerminated()) {
            logger.warn("重连调度器已关闭,无法安排业务频道重连");
            return;
        }

        if (isCurrentlyReconnecting("business")) {
            debugLog("业务频道已在重连中，跳过本次重连请求");
            return;
        }

        markReconnectStart("business");

        try {
            reconnectScheduler.submit(() -> {
            try {
                int currentRetry = businessRetryCount.getAndIncrement();

                // 优化重连延迟策略：更快的初始重连，渐进式增加延迟
                long delaySeconds;
                if (currentRetry == 0) {
                    delaySeconds = 1; // 首次重连立即执行
                } else if (currentRetry <= 3) {
                    delaySeconds = currentRetry * 2; // 2, 4, 6秒
                } else if (currentRetry <= 10) {
                    delaySeconds = 10; // 保持10秒
                } else {
                    delaySeconds = 20; // 最大20秒，而不是30秒
                    businessRetryCount.set(10); // 限制最大重试次数
                }

                debugLog("业务频道重连尝试 #{}, 延迟 {} 秒", currentRetry, delaySeconds);

                if (delaySeconds > 1) {
                    Thread.sleep(delaySeconds * 1000);
                }

                if (bussinessWebSocket != null) {
                    try {
                        bussinessWebSocket.close(1000, "Reconnecting");
                    } catch (Exception e) {
                        debugLog("关闭旧业务频道连接失败: {}", e.getMessage());
                    }
                }

                connectBussinessChannel();

                // 检查重连是否成功 - 缩短等待时间
                Thread.sleep(3000); // 等待3秒检查连接状态
                if (bussinessConnected.get()) {
                    // 重连成功，重置重试计数器
                    businessRetryCount.set(0);
                    logger.info("业务频道重连成功");

                    // 恢复业务频道的操作（如果有）
                    restoreBusinessOperations();

                    // 发布业务频道重连事件
                    try {
                        if (applicationEventPublisher != null) {
                            logger.info("发布WebSocket业务频道重连事件");
                            applicationEventPublisher.publishEvent(new WebSocketReconnectEvent(this, WebSocketReconnectEvent.ReconnectType.BUSINESS));
                        }
                    } catch (Exception e) {
                        logger.error("发布WebSocket业务频道重连事件失败", e);
                    }
                } else {
                    // 如果重连失败，继续安排下一次重试
                    logger.warn("业务频道重连失败，将安排下一次重试");
                    markReconnectEnd("business"); // 先结束当前重连标记
                    scheduleBusinessReconnect(); // 再安排下一次重连
                    return;
                }
            } catch (Exception e) {
                logger.error("重连业务频道失败", e);
                markReconnectEnd("business");
                scheduleBusinessReconnect();
                return;
            } finally {
                markReconnectEnd("business");
            }
        });
        } catch (RejectedExecutionException e) {
            logger.warn("无法提交业务频道重连任务,线程池可能已关闭: {}", e.getMessage());
            markReconnectEnd("business");
        } catch (Exception e) {
            logger.error("安排业务频道重连失败", e);
            markReconnectEnd("business");
        }
    }

    /**
     * 安排公共频道重连 - 优化版本
     */
    private void schedulePublicReconnect() {
        // 检查应用是否正在关闭
        if (isShuttingDown.get()) {
            logger.info("应用正在关闭,跳过公共频道重连");
            return;
        }

        // 检查线程池是否已关闭
        if (reconnectScheduler.isShutdown() || reconnectScheduler.isTerminated()) {
            logger.warn("重连调度器已关闭,无法安排公共频道重连");
            return;
        }

        if (isCurrentlyReconnecting("public")) {
            debugLog("公共频道已在重连中，跳过本次重连请求");
            return;
        }

        markReconnectStart("public");

        try {
            reconnectScheduler.submit(() -> {
            try {
                int currentRetry = publicRetryCount.getAndIncrement();

                // 优化重连延迟策略：更快的初始重连，渐进式增加延迟
                long delaySeconds;
                if (currentRetry == 0) {
                    delaySeconds = 1; // 首次重连立即执行
                } else if (currentRetry <= 3) {
                    delaySeconds = currentRetry * 2; // 2, 4, 6秒
                } else if (currentRetry <= 10) {
                    delaySeconds = 10; // 保持10秒
                } else {
                    delaySeconds = 20; // 最大20秒，而不是30秒
                    publicRetryCount.set(10); // 限制最大重试次数
                }

                debugLog("公共频道重连尝试 #{}, 延迟 {} 秒", currentRetry, delaySeconds);

                if (delaySeconds > 1) {
                    Thread.sleep(delaySeconds * 1000);
                }

                if (publicWebSocket != null) {
                    try {
                        publicWebSocket.close(1000, "Reconnecting");
                    } catch (Exception e) {
                        debugLog("关闭旧公共频道连接失败: {}", e.getMessage());
                    }
                }

                connectPublicChannel();

                // 检查重连是否成功 - 缩短等待时间
                Thread.sleep(3000); // 等待3秒检查连接状态
                if (publicConnected.get()) {
                    // 重连成功，重置重试计数器
                    publicRetryCount.set(0);
                    logger.info("公共频道重连成功");
                } else {
                    // 如果重连失败，继续安排下一次重试
                    logger.warn("公共频道重连失败，将安排下一次重试");
                    markReconnectEnd("public");
                    schedulePublicReconnect();
                    return;
                }
            } catch (Exception e) {
                logger.error("重连公共频道失败", e);
                markReconnectEnd("public");
                schedulePublicReconnect();
                return;
            } finally {
                markReconnectEnd("public");
            }
        });
        } catch (RejectedExecutionException e) {
            logger.warn("无法提交公共频道重连任务,线程池可能已关闭: {}", e.getMessage());
            markReconnectEnd("public");
        } catch (Exception e) {
            logger.error("安排公共频道重连失败", e);
            markReconnectEnd("public");
        }
    }

    /**
     * 自动订阅账户余额更新
     * 在程序启动和重连时自动订阅account频道
     */
    private void subscribeToBalanceUpdates() {
        try {
            logger.info("自动订阅账户余额更新");
            subscribePrivateTopic("account");
        } catch (Exception e) {
            logger.error("订阅账户余额更新失败", e);
        }
    }
}
