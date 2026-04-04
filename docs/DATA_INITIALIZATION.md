# 数据初始化功能说明

## 功能概述

系统在启动时会自动获取股票列表和加密货币列表，并以 HashMap 形式缓存到 Redis 中，缓存时间为 7 天。使用 Hash 结构可以：
- 自动去重（相同 code 的数据会被覆盖）
- 快速查询单个股票或加密货币信息
- 高效存储和访问

## Redis 存储结构

### Hash 结构说明
使用 Redis Hash 数据结构存储：
- **股票 Hash**: `market:stock:hash`
  - Key: 股票代码（如 `000001.SZ`）
  - Value: 股票信息的 JSON 字符串
  
- **加密货币 Hash**: `market:crypto:hash`
  - Key: 交易对符号（如 `BTC-USDT`）
  - Value: 交易对信息的 JSON 字符串

### Hash 结构优势
1. **自动去重**: 相同 key 的数据会自动覆盖，无需手动去重
2. **快速查询**: O(1) 时间复杂度查询单个元素
3. **内存高效**: Hash 结构比多个独立 key 更节省内存
4. **批量操作**: 支持批量获取所有数据或部分数据

## 前端数据加载方式

### 加密货币列表
- **前端触发方式**：用户点击交易对选择器时，前端调用 `/api/market/all_tickers` 接口
- **后端缓存策略**：
  - 优先从 7 天 Hash 缓存（`market:crypto:hash`）中获取交易对列表
  - 然后从 10 分钟短期缓存中获取详细行情数据（价格、涨跌幅等）
  - 如果缓存都不存在，才调用 OKX API 获取实时数据
- **单个查询**: 新增 `/api/market/crypto/{symbol}` 接口，可快速查询单个交易对信息

### 股票列表
- **前端触发方式**：用户访问股票相关页面时，前端调用 `/api/stock/market/stock/info/list` 接口
- **后端缓存策略**：
  - 优先从 7 天 Hash 缓存（`market:stock:hash`）中获取
  - 支持按交易所过滤（SSE、SZSE）
  - 如果缓存不存在，才调用 Tushare API 获取
- **单个查询**: 新增 `/api/stock/market/stock/info/{code}` 接口，可快速查询单个股票信息

## 实现原理

### 1. 启动监听器
- `ApplicationStartupListener` 监听应用启动完成事件（`ApplicationReadyEvent`）
- 启动完成后异步执行数据初始化任务，不阻塞应用启动

### 2. 数据初始化服务
- `DataInitializationService` 接口定义了数据初始化的方法
- `DataInitializationServiceImpl` 实现类负责具体的数据获取和缓存逻辑
- 使用 Redis Hash 结构存储，每个股票/加密货币作为一个 field

### 3. 缓存策略
- **股票 Hash 键**: `market:stock:hash`
- **加密货币 Hash 键**: `market:crypto:hash`
- **缓存时间**: 7 天（604800 秒）
- **短期行情缓存**: `ALL_COIN_RT_PRICE`（10 分钟，用于实时价格数据）

## 数据来源

### 股票列表
- 通过 `TushareApiService.getStockInfoList()` 获取
- 获取所有上市状态（L）的股票
- 包含股票代码、名称、交易所、行业等信息
- 以股票代码为 key 存储到 Hash 中

### 加密货币列表
- 通过 `OkxApiService.getAllTickers()` 获取
- 获取所有 USDT 交易对
- 包含交易对符号、价格、涨跌幅、成交量等信息
- 以交易对符号为 key 存储到 Hash 中

## 使用方式

### 自动初始化
系统启动时会自动执行数据初始化，无需手动操作。

### 前端调用

#### 获取列表数据
```typescript
// 获取加密货币列表
const result = await fetchAllTickers(filter, limit);

// 获取股票列表
const response = await fetch('/api/stock/market/stock/info/list?listStatus=L');
```

#### 获取单个数据（新增）
```typescript
// 获取单个加密货币信息
const response = await fetch('/api/market/crypto/BTC-USDT');

// 获取单个股票信息
const response = await fetch('/api/stock/market/stock/info/000001.SZ');
```

### 手动触发刷新
如果需要手动刷新缓存，可以调用以下方法：

```java
@Autowired
private DataInitializationService dataInitializationService;

// 初始化所有数据
dataInitializationService.initAllDataCache();

// 只初始化股票列表
dataInitializationService.initStockListCache();

// 只初始化加密货币列表
dataInitializationService.initCryptoListCache();
```

### 直接访问 Redis Hash
可以通过 `RedisTemplate` 直接访问 Hash 数据：

```java
@Autowired
private RedisTemplate<String, Object> redisTemplate;

// 获取单个股票信息
Object stockJson = redisTemplate.opsForHash().get("market:stock:hash", "000001.SZ");
StockInfo stock = JSON.parseObject(stockJson.toString(), StockInfo.class);

// 获取所有股票信息
Map<Object, Object> allStocks = redisTemplate.opsForHash().entries("market:stock:hash");

// 获取单个加密货币信息
Object cryptoJson = redisTemplate.opsForHash().get("market:crypto:hash", "BTC-USDT");
Ticker ticker = JSON.parseObject(cryptoJson.toString(), Ticker.class);

// 检查是否存在
Boolean exists = redisTemplate.opsForHash().hasKey("market:stock:hash", "000001.SZ");

// 获取总数
Long count = redisTemplate.opsForHash().size("market:stock:hash");
```

## 日志输出

系统会在日志中记录初始化过程：

```
应用启动完成，开始初始化数据缓存...
开始初始化股票列表缓存...
股票列表缓存初始化成功，共 5000 只股票，缓存时间：7天
开始初始化加密货币列表缓存...
加密货币列表缓存初始化成功，共 500 个交易对，缓存时间：7天
所有数据缓存初始化完成
数据缓存初始化完成
```

接口调用时的日志：

```
从7天缓存中获取股票列表，共 5000 只股票
从7天缓存中获取加密货币列表，共 500 个交易对
从短期缓存查询所有币种价格
从缓存中获取股票信息: 000001.SZ
从缓存中获取币种信息: BTC-USDT
```

## 异常处理

- 如果获取数据失败，会记录错误日志，但不会影响应用启动
- 缓存失败也会记录日志，但不会抛出异常
- 系统会继续正常运行，只是缓存数据可能为空
- 如果缓存不存在，接口会降级到直接调用外部 API

## 配置说明

### 缓存时间调整
如需修改缓存时间，可以在 `DataInitializationServiceImpl` 中修改常量：

```java
// 缓存过期时间：7天（单位：秒）
private static final long CACHE_TIMEOUT_SECONDS = 7 * 24 * 60 * 60;
```

### 异步执行配置
启动监听器使用 `@Async` 注解异步执行，需要确保 Spring Boot 应用启用了异步支持：

```java
@SpringBootApplication
@EnableAsync  // 确保有这个注解
public class OkxTradingApplication {
    // ...
}
```

## 性能影响

- 数据初始化在后台异步执行，不影响应用启动速度
- 首次获取数据可能需要几秒钟，取决于网络和 API 响应速度
- Hash 结构查询单个元素的时间复杂度为 O(1)，非常快
- 7 天内不会重复调用外部 API，大幅减少 API 调用次数
- 前端用户体验：首次打开页面时，数据已经在缓存中，响应速度快
- Hash 结构比 List 更节省内存，特别是数据量大时

## 缓存层级

系统采用两级缓存策略：

1. **长期 Hash 缓存（7天）**：
   - 存储完整的股票和加密货币信息
   - 启动时初始化
   - 支持快速查询单个元素
   - 自动去重

2. **短期缓存（10分钟）**：
   - 存储实时行情数据（价格、涨跌幅等）
   - 每次访问时更新
   - 保证数据的实时性

## 新增接口

### 获取单个股票信息
```
GET /api/stock/market/stock/info/{code}
```
参数：
- `code`: 股票代码，如 `000001.SZ`

返回：单个股票的详细信息

### 获取单个加密货币信息
```
GET /api/market/crypto/{symbol}
```
参数：
- `symbol`: 交易对符号，如 `BTC-USDT`

返回：单个交易对的详细信息

## 注意事项

1. 确保 Redis 服务正常运行
2. 确保 Tushare API 和 OKX API 配置正确
3. 如果 API 调用失败，缓存可能为空，需要检查日志
4. 长期缓存过期后会自动失效，需要重启应用或手动刷新
5. 短期行情缓存会自动更新，保持数据最新
6. 前端是手动触发（点击选择器）而非自动加载，避免不必要的请求
7. Hash 结构会自动去重，相同 code 的数据会被覆盖
8. 单个查询接口性能极高，适合频繁查询场景

## 相关文件

- `src/main/java/com/okx/trading/listener/ApplicationStartupListener.java` - 启动监听器
- `src/main/java/com/okx/trading/service/DataInitializationService.java` - 服务接口
- `src/main/java/com/okx/trading/service/impl/DataInitializationServiceImpl.java` - 服务实现（Hash 存储）
- `src/main/java/com/okx/trading/controller/MarketController.java` - 加密货币接口（已修改，支持 Hash 读取）
- `src/main/java/com/okx/trading/controller/StockMarketController.java` - 股票接口（已修改，支持 Hash 读取）

