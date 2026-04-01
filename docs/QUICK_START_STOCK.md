# A股交易系统快速启动指南

## 前提条件

1. Java 21
2. Maven 3.6+
3. MySQL 8.0+
4. Redis 6.0+
5. Tushare API Token

## 快速启动步骤

### 1. 配置 Tushare API Token

编辑 `src/main/resources/application.properties`，设置你的 Tushare Token：

```properties
tushare.api.token=你的Tushare_Token
```

或者通过环境变量：

```bash
export TUSHARE_API_TOKEN=你的Tushare_Token
```

**默认 Token（测试用）：**
```
krMJzeduYQNpQxOMToDCwfZGqmATGWnSalvyjEjbzJZuLZEvpMpEiRSEDKkPBFiu
```

### 2. 配置数据库

确保 MySQL 和 Redis 已启动，并配置环境变量：

```bash
export MYSQL_USERNAME=root
export MYSQL_PASSWORD=你的MySQL密码
```

### 3. 编译项目

```bash
mvn clean package -DskipTests
```

### 4. 启动应用

```bash
java -jar target/okx-trading-0.0.1-SNAPSHOT.jar
```

或者使用 Maven：

```bash
mvn spring-boot:run
```

### 5. 验证安装

访问 Swagger UI：
```
http://localhost:8088/swagger-ui.html
```

测试 Tushare 连接：
```bash
curl http://localhost:8088/api/stock/market/test
```

## 快速测试

### 测试 1：获取平安银行日线数据

```bash
curl "http://localhost:8088/api/stock/market/kline/daily?tsCode=000001.SZ&limit=10"
```

### 测试 2：获取股票列表

```bash
curl "http://localhost:8088/api/stock/market/stock/list?exchange=SSE&listStatus=L"
```

### 测试 3：获取最新行情

```bash
curl "http://localhost:8088/api/stock/market/ticker?tsCode=000001.SZ"
```

### 测试 4：运行完整测试脚本

```bash
chmod +x test-tushare-api.sh
./test-tushare-api.sh
```

## 常用股票代码

### 银行股
- 平安银行：`000001.SZ`
- 招商银行：`600036.SH`
- 工商银行：`601398.SH`
- 建设银行：`601939.SH`

### 科技股
- 贵州茅台：`600519.SH`
- 中国平安：`601318.SH`
- 五粮液：`000858.SZ`
- 格力电器：`000651.SZ`

### 指数
- 上证指数：`000001.SH`
- 深证成指：`399001.SZ`
- 创业板指：`399006.SZ`
- 沪深300：`000300.SH`

## 回测示例

### 对平安银行进行 SMA 策略回测

```bash
curl -X POST "http://localhost:8088/api/backtest/run" \
  -H "Content-Type: application/json" \
  -d '{
    "symbol": "000001.SZ",
    "interval": "1D",
    "strategyCode": "SMA",
    "startTime": 1704038400000,
    "endTime": 1711900800000,
    "initialCapital": 100000,
    "params": {
      "shortPeriod": 5,
      "longPeriod": 20
    }
  }'
```

## 目录结构

```
src/main/java/com/okx/trading/
├── config/
│   └── TushareConfig.java          # Tushare配置
├── controller/
│   └── StockMarketController.java  # 股票市场API控制器
├── service/
│   ├── TushareApiService.java      # Tushare服务接口
│   └── impl/
│       └── TushareApiServiceImpl.java  # Tushare服务实现
└── model/
    └── market/
        ├── Candlestick.java        # K线数据模型（复用）
        └── Ticker.java             # 行情数据模型（复用）
```

## API 端点总览

| 端点 | 方法 | 描述 |
|------|------|------|
| `/api/stock/market/test` | GET | 测试连接 |
| `/api/stock/market/stock/list` | GET | 获取股票列表 |
| `/api/stock/market/kline/daily` | GET | 获取日线数据 |
| `/api/stock/market/kline/minute` | GET | 获取分钟线数据 |
| `/api/stock/market/kline/history` | GET | 获取历史K线（兼容接口） |
| `/api/stock/market/ticker` | GET | 获取最新行情 |

## 配置说明

### Tushare 配置项

```properties
# Tushare API Token（必填）
tushare.api.token=你的Token

# Tushare API URL（默认）
tushare.api.url=http://111.170.34.57:8010/

# 请求超时时间（秒）
tushare.api.timeout=30

# 是否启用代理
tushare.api.proxy-enabled=false

# 代理配置（如果启用）
tushare.api.proxy-host=127.0.0.1
tushare.api.proxy-port=7890
```

## 故障排查

### 问题 1：Token 不对

**错误信息：**
```
Tushare API返回错误: 抱歉，您每分钟最多访问该接口200次
```

**解决方案：**
1. 检查 Token 是否正确
2. 确认配置了正确的 API URL
3. 检查网络连接

### 问题 2：无法获取分钟线数据

**错误信息：**
```
获取分钟线数据失败
```

**解决方案：**
分钟线数据需要更高的 Tushare 权限等级，请：
1. 升级 Tushare 账户权限
2. 或者只使用日线数据进行回测

### 问题 3：数据库连接失败

**解决方案：**
1. 确认 MySQL 已启动
2. 检查数据库连接配置
3. 确认数据库用户名和密码正确

## 下一步

1. 查看 [STOCK_API_GUIDE.md](STOCK_API_GUIDE.md) 了解详细的 API 使用说明
2. 浏览 Swagger UI 查看所有可用接口
3. 尝试不同的回测策略
4. 根据需要调整策略参数

## 技术支持

如有问题，请查看：
- [Tushare 官方文档](https://www.yuque.com/a493465197/fl1fxx/ixwtsutxwaf0chdc)
- 项目 README.md
- Swagger API 文档

## 注意事项

1. **数据权限**：不同 Tushare 权限等级可获取的数据范围不同
2. **请求频率**：注意 API 请求频率限制
3. **交易时间**：A 股交易时间为工作日 9:30-11:30, 13:00-15:00
4. **数据延迟**：实时数据可能有延迟
5. **兼容性**：原有加密货币功能保持不变，可同时使用
