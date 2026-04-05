# OKX WebSocket 订单 API instIdCode 迁移

## 背景

根据 OKX 官方文档 2025-03-26 更新日志，WebSocket 订单 API 已经从使用 `instId`（字符串）迁移到使用 `instIdCode`（整数）。

### 迁移时间表

- **Phase 1 (2026-03-26)**: WS Place order 和 Place multiple orders 已废弃 `instId`
- **Phase 2 (2026-04-07)**: WS Amend/Cancel order 将废弃 `instId`

### 错误信息

使用旧的 `instId` 参数会导致以下错误：
```
Parameter instIdCode can not be empty (错误码: 50014)
```

## 解决方案

### 1. 添加 instIdCode 缓存

在 `OkxApiWebSocketServiceImpl` 类中添加了 `instId` -> `instIdCode` 的映射缓存：

```java
// instId -> instIdCode 映射缓存 (根据 OKX 2026-03-26 更新，WebSocket 订单需要使用 instIdCode)
private final Map<String, Integer> instIdCodeCache = new ConcurrentHashMap<>();
```

### 2. 实现 getInstIdCode 方法

创建了 `getInstIdCode` 方法来获取和缓存 `instIdCode`：

```java
private Integer getInstIdCode(String instId, String instType) {
    // 先从缓存中查找
    if (instIdCodeCache.containsKey(instId)) {
        return instIdCodeCache.get(instId);
    }

    // 缓存中没有，调用 API 获取
    // 调用 GET /api/v5/market/instruments?instType={instType}&instId={instId}
    // 解析响应中的 instIdCode 字段并缓存
}
```

### 3. 修改 createOrder 方法

更新了 `createOrder` 方法，使用 `instIdCode` 而不是 `instId`：

```java
// 旧代码
arg.put("instId", orderRequest.getSymbol());

// 新代码
Integer instIdCode = getInstIdCode(orderRequest.getSymbol(), instType);
if (instIdCode == null) {
    log.error("无法获取 instIdCode: symbol={}, instType={}", orderRequest.getSymbol(), instType);
    throw new OkxApiException("无法获取交易对的 instIdCode，请检查交易对是否有效");
}
arg.put("instIdCode", instIdCode);
```

## 工作原理

1. **首次调用**: 当创建订单时，系统会调用 OKX REST API `/api/v5/market/instruments` 获取交易对的 `instIdCode`
2. **缓存机制**: 获取到的 `instIdCode` 会被缓存到内存中，避免重复调用 API
3. **订单创建**: 使用缓存的 `instIdCode` 创建 WebSocket 订单请求

## API 调用示例

### 获取 instIdCode

**请求**:
```
GET /api/v5/public/instruments?instType=SPOT&instId=BTC-USDT
```

注意：这是公共 API 端点，不需要签名认证。

**响应**:
```json
{
  "code": "0",
  "msg": "",
  "data": [
    {
      "instId": "BTC-USDT",
      "instIdCode": 123456,
      "instType": "SPOT",
      ...
    }
  ]
}
```

### WebSocket 订单请求

**旧格式（已废弃）**:
```json
{
  "op": "order",
  "args": [{
    "instId": "BTC-USDT",
    "tdMode": "cash",
    "side": "buy",
    "ordType": "market",
    "sz": "10.0"
  }]
}
```

**新格式（当前使用）**:
```json
{
  "op": "order",
  "args": [{
    "instIdCode": 123456,
    "tdMode": "cash",
    "side": "buy",
    "ordType": "market",
    "sz": "10.0"
  }]
}
```

## 优势

1. **符合 OKX 最新 API 规范**: 避免使用已废弃的参数
2. **性能优化**: 使用整数 `instIdCode` 比字符串 `instId` 更高效
3. **缓存机制**: 减少 API 调用次数，提高响应速度
4. **错误处理**: 如果无法获取 `instIdCode`，会抛出明确的错误信息

## 测试

编译测试通过：
```bash
mvn clean compile -DskipTests
[INFO] BUILD SUCCESS
```

## 相关文件

- `okx-trading/src/main/java/com/okx/trading/service/impl/OkxApiWebSocketServiceImpl.java`

## 参考文档

- OKX WebSocket API 文档: https://www.okx.com/docs-v5/en/#websocket-api-trade
- OKX 更新日志 (2025-03-26): WebSocket 订单 API 迁移到 instIdCode
