# A股市场数据接口使用指南

## 概述

本项目已集成 Tushare API，支持获取 A 股市场数据。通过最小化改动，保留了原有的加密货币交易功能，同时新增了股票市场数据查询接口。

## 配置

### 1. 配置 Tushare API Token

在 `application.properties` 中配置：

```properties
# Tushare API Configuration
tushare.api.token=你的Tushare_Token
tushare.api.url=http://111.170.34.57:8010/
tushare.api.timeout=30
```

或通过环境变量配置：

```bash
export TUSHARE_API_TOKEN=你的Tushare_Token
```

### 2. 代理配置（可选）

如果需要使用代理访问 Tushare API：

```properties
tushare.api.proxy-enabled=true
tushare.api.proxy-host=127.0.0.1
tushare.api.proxy-port=7890
```

## API 接口

### 1. 测试连接

测试 Tushare API 是否可以正常连接。

**请求：**
```bash
GET /api/stock/market/test
```

**响应示例：**
```json
{
  "code": 200,
  "message": "Tushare API连接成功",
  "data": true
}
```

### 2. 获取股票列表

获取指定交易所的股票列表。

**请求：**
```bash
GET /api/stock/market/stock/list?exchange=SSE&listStatus=L
```

**参数：**
- `exchange`（可选）：交易所代码
  - `SSE`：上海证券交易所
  - `SZSE`：深圳证券交易所
- `listStatus`（可选，默认 L）：上市状态
  - `L`：上市
  - `D`：退市
  - `P`：暂停上市

**响应示例：**
```json
{
  "code": 200,
  "message": "success",
  "data": [
    "000001.SZ",
    "000002.SZ",
    "600000.SH"
  ]
}
```

### 3. 获取日线数据

获取指定股票的日线 K 线数据。

**请求：**
```bash
GET /api/stock/market/kline/daily?tsCode=000001.SZ&startDate=20240101&endDate=20240331&limit=100
```

**参数：**
- `tsCode`（必填）：股票代码，如 `000001.SZ`
- `startDate`（可选）：开始日期，格式 `YYYYMMDD`
- `endDate`（可选）：结束日期，格式 `YYYYMMDD`
- `limit`（可选，默认 100）：获取数据条数

**响应示例：**
```json
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "symbol": "000001.SZ",
      "interval": "1D",
      "timestamp": 1704038400000,
      "open": 10.50,
      "high": 10.80,
      "low": 10.40,
      "close": 10.75,
      "volume": 1000000,
      "quoteVolume": 10750000
    }
  ]
}
```

### 4. 获取分钟线数据

获取指定股票的分钟级 K 线数据。

**请求：**
```bash
GET /api/stock/market/kline/minute?tsCode=000001.SZ&freq=5min&limit=100
```

**参数：**
- `tsCode`（必填）：股票代码，如 `000001.SZ`
- `freq`（可选，默认 5min）：频率
  - `1min`：1分钟
  - `5min`：5分钟
  - `15min`：15分钟
  - `30min`：30分钟
  - `60min`：60分钟
- `startDate`（可选）：开始时间，格式 `YYYY-MM-DD HH:MM:SS`
- `endDate`（可选）：结束时间，格式 `YYYY-MM-DD HH:MM:SS`
- `limit`（可选，默认 100）：获取数据条数

### 5. 获取历史K线数据（兼容接口）

获取历史 K 线数据，兼容原有的加密货币接口格式。

**请求：**
```bash
GET /api/stock/market/kline/history?symbol=000001.SZ&interval=1D&startTime=1704038400000&endTime=1711900800000&limit=100
```

**参数：**
- `symbol`（必填）：股票代码
- `interval`（可选，默认 1D）：K线间隔
  - 日线：`1D`, `1W`, `1M`
  - 分钟线：`1m`, `5m`, `15m`, `30m`, `1H`
- `startTime`（可选）：开始时间戳（毫秒）
- `endTime`（可选）：结束时间戳（毫秒）
- `limit`（可选，默认 100）：获取数据条数

### 6. 获取最新行情

获取指定股票的最新行情数据。

**请求：**
```bash
GET /api/stock/market/ticker?tsCode=000001.SZ
```

**参数：**
- `tsCode`（必填）：股票代码

**响应示例：**
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "symbol": "000001.SZ",
    "lastPrice": 10.75,
    "highPrice": 10.80,
    "lowPrice": 10.40,
    "volume": 1000000,
    "timestamp": 1704038400000
  }
}
```

## 股票代码格式

Tushare 使用的股票代码格式：

- **上海证券交易所**：`股票代码.SH`，如 `600000.SH`（浦发银行）
- **深圳证券交易所**：`股票代码.SZ`，如 `000001.SZ`（平安银行）

## 测试

运行测试脚本验证 API 集成：

```bash
./test-tushare-api.sh
```

## 回测功能

原有的回测功能完全兼容股票数据。只需要：

1. 使用股票代码（如 `000001.SZ`）替代加密货币交易对
2. 选择合适的时间间隔（日线或分钟线）
3. 其他回测参数保持不变

**示例：**

```bash
# 对平安银行进行回测
POST /api/backtest/run
{
  "symbol": "000001.SZ",
  "interval": "1D",
  "strategyCode": "SMA",
  "startTime": 1704038400000,
  "endTime": 1711900800000,
  "initialCapital": 100000
}
```

## 注意事项

1. **数据权限**：Tushare 不同权限等级可获取的数据范围不同，请确保你的 Token 有足够权限
2. **请求频率**：注意 Tushare API 的请求频率限制
3. **交易时间**：A 股交易时间为工作日 9:30-11:30, 13:00-15:00
4. **数据延迟**：分钟线数据可能有一定延迟
5. **兼容性**：原有的加密货币功能保持不变，可以同时使用

## 常见问题

### Q: Token 不对怎么办？

A: 请检查：
1. Token 是否正确配置
2. 是否添加了 `pro._DataApi__http_url = "http://111.170.34.57:8010/"` 配置
3. 网络是否可以访问 Tushare API

### Q: 如何获取更多历史数据？

A: 调整 `limit` 参数，或者使用 `startDate` 和 `endDate` 指定时间范围。

### Q: 分钟线数据获取失败？

A: 分钟线数据需要更高的 Tushare 权限等级，请确认你的账户权限。

## 技术架构

### 新增组件

1. **TushareConfig**：Tushare API 配置类
2. **TushareApiService**：Tushare API 服务接口
3. **TushareApiServiceImpl**：Tushare API 服务实现
4. **StockMarketController**：股票市场数据控制器

### 数据模型

复用原有的数据模型：
- `Candlestick`：K线数据模型
- `Ticker`：行情数据模型

### 改动说明

- ✅ 最小化改动原则
- ✅ 保留原有加密货币功能
- ✅ 新增股票数据接口
- ✅ 兼容原有回测框架
- ✅ 复用现有数据模型

## 后续优化建议

1. 添加股票数据缓存机制
2. 实现实时行情推送（WebSocket）
3. 添加更多技术指标
4. 支持更多 Tushare 数据接口（财务数据、公告等）
5. 优化数据存储和查询性能
