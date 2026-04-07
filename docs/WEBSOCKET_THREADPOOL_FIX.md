# WebSocket 线程池 RejectedExecutionException 修复方案

## 问题描述

应用关闭或重启时,出现以下错误:

```
java.util.concurrent.RejectedExecutionException: Task ... rejected from 
java.util.concurrent.ScheduledThreadPoolExecutor@22b0fe7e[Terminated, pool size = 0, active threads = 0, queued tasks = 0, completed tasks = 1]
```

## 问题分析

### 根本原因

1. 应用关闭时,Spring 容器会关闭所有线程池
2. 但 WebSocket 连接可能还在活动状态
3. WebSocket 的 `onFailure` 或 `onOpen` 回调尝试提交任务到已关闭的线程池
4. 导致 `RejectedExecutionException`

### 错误堆栈分析

```
at com.okx.trading.util.WebSocketUtil.restorePublicOperations(WebSocketUtil.java:965)
at com.okx.trading.util.WebSocketUtil$2.onOpen(WebSocketUtil.java:276)
```

```
at com.okx.trading.util.WebSocketUtil.schedulePublicReconnect(WebSocketUtil.java:1354)
at com.okx.trading.util.WebSocketUtil$2.onFailure(WebSocketUtil.java:290)
```

### 问题场景

1. **场景 1**: 应用关闭时
   - Spring 容器关闭线程池
   - WebSocket 连接断开触发 `onFailure`
   - 尝试调用 `schedulePublicReconnect()` 提交重连任务
   - 线程池已关闭,抛出异常

2. **场景 2**: 应用重启时
   - 线程池被关闭
   - WebSocket 重连成功触发 `onOpen`
   - 尝试调用 `restorePublicOperations()` 提交恢复任务
   - 线程池已关闭,抛出异常

## 解决方案

### 方案 1: 添加线程池状态检查 (推荐)

在提交任务前检查线程池是否已关闭:

```java
private void restorePublicOperations() {
    // 检查线程池是否已关闭
    if (reconnectScheduler.isShutdown() || reconnectScheduler.isTerminated()) {
        logger.warn("线程池已关闭,跳过恢复公共频道操作");
        return;
    }
    
    CompletableFuture.runAsync(() -> {
        // 原有逻辑
    }, reconnectScheduler);
}

private void schedulePublicReconnect() {
    // 检查线程池是否已关闭
    if (reconnectScheduler.isShutdown() || reconnectScheduler.isTerminated()) {
        logger.warn("线程池已关闭,跳过公共频道重连");
        return;
    }
    
    if (isCurrentlyReconnecting("public")) {
        debugLog("公共频道已在重连中，跳过本次重连请求");
        return;
    }
    
    // 原有逻辑
}
```

### 方案 2: 使用 try-catch 捕获异常

```java
private void schedulePublicReconnect() {
    try {
        reconnectScheduler.submit(() -> {
            // 重连逻辑
        });
    } catch (RejectedExecutionException e) {
        logger.warn("线程池已关闭,无法提交重连任务: {}", e.getMessage());
    }
}
```

### 方案 3: 添加应用关闭标志

```java
private final AtomicBoolean applicationShuttingDown = new AtomicBoolean(false);

@PreDestroy
public void shutdown() {
    applicationShuttingDown.set(true);
    // 关闭 WebSocket 连接
    // 关闭线程池
}

private void schedulePublicReconnect() {
    if (applicationShuttingDown.get()) {
        logger.info("应用正在关闭,跳过重连");
        return;
    }
    // 原有逻辑
}
```

## 推荐实现

结合方案 1 和方案 3,提供最佳实践:

```java
public class WebSocketUtil {
    
    private final AtomicBoolean isShuttingDown = new AtomicBoolean(false);
    
    @PreDestroy
    public void shutdown() {
        logger.info("WebSocketUtil 开始关闭...");
        isShuttingDown.set(true);
        
        // 关闭所有 WebSocket 连接
        closeAllConnections();
        
        logger.info("WebSocketUtil 关闭完成");
    }
    
    private void closeAllConnections() {
        try {
            if (publicWebSocket != null) {
                publicWebSocket.close(1000, "Application shutdown");
            }
            if (businessWebSocket != null) {
                businessWebSocket.close(1000, "Application shutdown");
            }
            if (privateWebSocket != null) {
                privateWebSocket.close(1000, "Application shutdown");
            }
        } catch (Exception e) {
            logger.error("关闭 WebSocket 连接失败", e);
        }
    }
    
    private void restorePublicOperations() {
        // 检查应用是否正在关闭
        if (isShuttingDown.get()) {
            logger.debug("应用正在关闭,跳过恢复公共频道操作");
            return;
        }
        
        // 检查线程池是否已关闭
        if (reconnectScheduler.isShutdown() || reconnectScheduler.isTerminated()) {
            logger.warn("线程池已关闭,跳过恢复公共频道操作");
            return;
        }
        
        try {
            CompletableFuture.runAsync(() -> {
                logger.info("开始恢复公共频道订阅，共 {} 个待执行操作", publicPendingOperations.size());
                // 原有逻辑
            }, reconnectScheduler);
        } catch (RejectedExecutionException e) {
            logger.warn("提交恢复任务失败,线程池可能已关闭: {}", e.getMessage());
        }
    }
    
    private void schedulePublicReconnect() {
        // 检查应用是否正在关闭
        if (isShuttingDown.get()) {
            logger.debug("应用正在关闭,跳过公共频道重连");
            return;
        }
        
        // 检查线程池是否已关闭
        if (reconnectScheduler.isShutdown() || reconnectScheduler.isTerminated()) {
            logger.warn("线程池已关闭,跳过公共频道重连");
            return;
        }
        
        if (isCurrentlyReconnecting("public")) {
            debugLog("公共频道已在重连中，跳过本次重连请求");
            return;
        }
        
        try {
            markReconnectStart("public");
            
            reconnectScheduler.submit(() -> {
                // 重连逻辑
            });
        } catch (RejectedExecutionException e) {
            logger.warn("提交重连任务失败,线程池可能已关闭: {}", e.getMessage());
            markReconnectEnd("public");
        }
    }
    
    // 同样的逻辑应用到其他频道的重连方法:
    // - scheduleBusinessReconnect()
    // - schedulePrivateReconnect()
    // - restoreBusinessOperations()
    // - restorePrivateOperations()
}
```

## 需要修改的方法

### 公共频道
1. `restorePublicOperations()` - 恢复公共频道操作
2. `schedulePublicReconnect()` - 安排公共频道重连

### 业务频道
3. `restoreBusinessOperations()` - 恢复业务频道操作 (如果存在)
4. `scheduleBusinessReconnect()` - 安排业务频道重连

### 私有频道
5. `restorePrivateOperations()` - 恢复私有频道操作 (如果存在)
6. `schedulePrivateReconnect()` - 安排私有频道重连

## 验证步骤

### 1. 正常关闭测试

```bash
# 启动应用
java -jar okx-trading.jar

# 正常关闭 (Ctrl+C 或 kill)
kill -15 <pid>

# 检查日志,应该看到:
# - "WebSocketUtil 开始关闭..."
# - "应用正在关闭,跳过重连"
# - 没有 RejectedExecutionException
```

### 2. 重启测试

```bash
# 重启应用
systemctl restart okx-trading

# 检查日志,应该没有 RejectedExecutionException
```

### 3. WebSocket 断线重连测试

```bash
# 模拟网络断线
# 检查日志,应该正常重连,没有异常
```

## 注意事项

1. **线程池关闭顺序**: Spring 容器关闭时,线程池可能在 WebSocket 之前关闭
2. **异步任务**: `CompletableFuture.runAsync()` 需要指定线程池,否则使用 ForkJoinPool
3. **优雅关闭**: 添加 `@PreDestroy` 方法确保资源正确释放
4. **日志级别**: 使用 `warn` 而不是 `error`,因为这是预期的关闭行为

## 相关配置

### application.properties

```properties
# 优雅关闭配置
server.shutdown=graceful
spring.lifecycle.timeout-per-shutdown-phase=30s
```

### ThreadPoolConfig.java

确保线程池配置了合理的关闭策略:

```java
@Bean(name = "websocketReconnectScheduler", destroyMethod = "shutdown")
public ScheduledExecutorService websocketReconnectScheduler(){
    return Executors.newScheduledThreadPool(3,
        createThreadFactory("WebSocket重连"));
}
```

## 总结

通过添加线程池状态检查和应用关闭标志,可以有效避免 `RejectedExecutionException`。这是一个常见的优雅关闭问题,需要在所有异步任务提交点进行防护。

## 相关文件

- `okx-trading/src/main/java/com/okx/trading/util/WebSocketUtil.java`
- `okx-trading/src/main/java/com/okx/trading/config/ThreadPoolConfig.java`
- `okx-trading/src/main/resources/application.properties`
