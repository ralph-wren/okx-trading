# Kafka 监控线程池优化说明

## 优化内容

将 Kafka 监控任务从 Spring 默认的定时任务线程池迁移到独立的线程池，避免影响主线程和其他业务逻辑。

## 线程池配置

### 位置
`com.okx.trading.config.ThreadPoolConfig`

### 配置详情

```java
@Bean(name = "kafkaMonitorScheduler")
public ScheduledExecutorService kafkaMonitorScheduler(){
    return Executors.newScheduledThreadPool(2,
            createThreadFactory("Kafka监控"));
}
```

### 线程池特性

- **线程数量**：2个线程
  - 1个用于数据新鲜度检查（每60秒）
  - 1个用于消费者 lag 检查（每120秒）
- **线程名称**：`Kafka监控-{threadId}`
- **线程类型**：守护线程（Daemon Thread）
  - 随主线程退出而退出
  - 不会阻止 JVM 关闭
- **调度类型**：`ScheduledExecutorService`
  - 支持固定频率的定时任务

## 任务调度

### 初始化方式

在 `KafkaMonitorServiceImpl` 的 `@PostConstruct` 方法中初始化：

```java
@PostConstruct
public void initMonitorTasks() {
    // 数据新鲜度检查（每60秒）
    kafkaMonitorScheduler.scheduleAtFixedRate(() -> {
        checkDataFreshness();
    }, 60, 60, TimeUnit.SECONDS);

    // 消费者 lag 检查（每120秒）
    kafkaMonitorScheduler.scheduleAtFixedRate(() -> {
        checkConsumerLag();
    }, 120, 120, TimeUnit.SECONDS);
}
```

### 任务特性

1. **数据新鲜度检查**
   - 初始延迟：60秒
   - 执行间隔：60秒
   - 线程：`Kafka监控-{id}`

2. **消费者 Lag 检查**
   - 初始延迟：120秒
   - 执行间隔：120秒
   - 线程：`Kafka监控-{id}`

## 异步邮件发送

### 配置

邮件发送方法使用 `@Async` 注解，复用现有的异步线程池：

```java
@Async("customAsyncTaskExecutor")
private void sendDataFreshnessAlert(long secondsSinceLastData) {
    // 发送告警邮件
}

@Async("customAsyncTaskExecutor")
private void sendConsumerLagAlert(long totalLag, Map<Integer, Long> lagByPartition) {
    // 发送告警邮件
}
```

### 异步线程池

使用 `AsyncConfig` 中配置的 `customAsyncTaskExecutor`：
- 核心线程数：5
- 最大线程数：10
- 队列容量：100
- 线程名称：`WebSocketAsyn订阅-{id}`

## 优势

### 1. 资源隔离

- Kafka 监控任务在独立线程池中运行
- 不占用主线程资源
- 不影响其他业务线程池（如 WebSocket、交易执行等）

### 2. 可控性

- 线程数量固定（2个），资源消耗可预测
- 守护线程，不会阻止应用关闭
- 异常隔离，监控任务失败不影响其他任务

### 3. 可观测性

- 线程名称明确（`Kafka监控-{id}`）
- 便于日志追踪和问题排查
- 可以通过线程名称过滤监控相关日志

### 4. 性能优化

- 邮件发送异步执行，不阻塞监控检查
- 监控任务并行执行，互不影响
- 避免与其他定时任务竞争资源

## 线程池层次结构

```
应用线程池体系
├── 主线程池（业务处理）
├── WebSocket 线程池
│   ├── websocketPingScheduler（心跳）
│   ├── websocketReconnectScheduler（重连）
│   └── websocketConnectScheduler（初始化）
├── 交易线程池
│   ├── executeTradeScheduler（执行交易）
│   ├── tradeIndicatorCalculateScheduler（指标计算）
│   └── realTimeTradeIndicatorCalculateScheduler（实时策略）
├── 数据处理线程池
│   ├── historicalDataExecutorService（历史数据）
│   ├── priceUpdateExecutorService（价格更新）
│   └── klineUpdateScheduler（K线更新）
├── 异步任务线程池
│   └── customAsyncTaskExecutor（异步任务，包括邮件发送）
└── Kafka 监控线程池 ⭐ 新增
    └── kafkaMonitorScheduler（监控任务）
```

## 监控日志示例

### 启动日志

```
INFO  - ✅ 初始化 Kafka 监控任务，使用独立线程池
INFO  - ✅ Kafka 监控任务已启动: 数据新鲜度检查(每60秒), 消费者lag检查(每120秒)
```

### 运行日志

```
DEBUG [Kafka监控-123] - Kafka 数据新鲜度检查: 距离上次接收数据 45 秒
INFO  [Kafka监控-124] - Kafka 消费者 lag 检查: 总 lag = 150, 阈值 = 1000
```

### 告警日志

```
WARN  [Kafka监控-123] - 已发送 Kafka 数据新鲜度告警邮件，距离上次接收数据 350 秒
WARN  [Kafka监控-124] - 已发送 Kafka 消费者 lag 告警邮件，总 lag: 1500
```

## 配置建议

### 生产环境

当前配置（2个线程）已足够：
- 监控任务轻量级，不需要更多线程
- 2个线程分别处理2个监控任务，互不干扰
- 守护线程，不影响应用关闭

### 如需调整

如果需要增加监控任务，可以在 `ThreadPoolConfig` 中调整线程池大小：

```java
@Bean(name = "kafkaMonitorScheduler")
public ScheduledExecutorService kafkaMonitorScheduler(){
    return Executors.newScheduledThreadPool(3,  // 增加到3个线程
            createThreadFactory("Kafka监控"));
}
```

## 故障排查

### 查看线程状态

使用 JVM 工具查看线程：

```bash
# 查看所有线程
jstack <pid> | grep "Kafka监控"

# 查看线程数量
jstack <pid> | grep "Kafka监控" | wc -l
```

### 查看线程池状态

在代码中添加监控：

```java
if (kafkaMonitorScheduler instanceof ThreadPoolExecutor) {
    ThreadPoolExecutor executor = (ThreadPoolExecutor) kafkaMonitorScheduler;
    log.info("Kafka监控线程池状态: 活跃线程={}, 队列大小={}, 完成任务数={}", 
        executor.getActiveCount(),
        executor.getQueue().size(),
        executor.getCompletedTaskCount());
}
```

## 总结

通过使用独立的线程池，Kafka 监控服务实现了：
- ✅ 资源隔离，不影响主业务
- ✅ 可控的资源消耗
- ✅ 清晰的线程命名和日志追踪
- ✅ 异步邮件发送，不阻塞监控
- ✅ 守护线程，优雅关闭

这种设计确保了监控服务的稳定性和可维护性。
