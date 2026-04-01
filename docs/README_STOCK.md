# A股市场数据集成 - README

## 🎯 项目目标

将现有的加密货币交易系统扩展为支持 A 股市场数据，使用 Tushare API 获取股票行情和历史数据，同时保持原有功能完全不变。

## ✨ 核心特性

- ✅ **最小化改动**：仅新增 4 个核心类，不修改原有代码
- ✅ **完全兼容**：原有加密货币功能保持不变
- ✅ **数据复用**：复用现有的 Candlestick 和 Ticker 数据模型
- ✅ **回测支持**：现有回测框架直接支持股票数据
- ✅ **易于使用**：RESTful API + Swagger 文档

## 📦 新增内容

### 核心代码（4个文件）

1. **TushareConfig.java** - Tushare API 配置
2. **TushareApiService.java** - 服务接口
3. **TushareApiServiceImpl.java** - 服务实现（核心）
4. **StockMarketController.java** - REST API 控制器

### 配置更新

- **application.properties** - 添加 Tushare 配置项

### 文档（4个文件）

1. **STOCK_API_GUIDE.md** - 详细 API 使用指南
2. **QUICK_START_STOCK.md** - 快速启动指南
3. **STOCK_INTEGRATION_SUMMARY.md** - 集成总结
4. **CHECKLIST.md** - 部署检查清单

### 测试脚本（2个文件）

1. **test-tushare-api.sh** - Bash 测试脚本
2. **test_tushare_direct.py** - Python 测试脚本

## 🚀 快速开始

### 1. 配置 Token

编辑 `src/main/resources/application.properties`：

```properties
tushare.api.token=你的Tushare_Token
```

**默认测试 Token（已配置）：**
```
krMJzeduYQNpQxOMToDCwfZGqmATGWnSalvyjEjbzJZuLZEvpMpEiRSEDKkPBFiu
```

### 2. 启动应用

```bash
mvn spring-boot:run
```

### 3. 测试连接

```bash
curl http://localhost:8088/api/stock/market/test
```

### 4. 获取股票数据

```bash
# 获取平安银行日线数据
curl "http://localhost:8088/api/stock/market/kline/daily?tsCode=000001.SZ&limit=10"

# 获取股票列表
curl "http://localhost:8088/api/stock/market/stock/list?exchange=SSE"

# 获取最新行情
curl "http://localhost:8088/api/stock/market/ticker?tsCode=000001.SZ"
```

## 📚 API 接口

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/stock/market/test` | GET | 测试连接 |
| `/api/stock/market/stock/list` | GET | 获取股票列表 |
| `/api/stock/market/kline/daily` | GET | 获取日线数据 |
| `/api/stock/market/kline/minute` | GET | 获取分钟线数据 |
| `/api/stock/market/kline/history` | GET | 获取历史K线 |
| `/api/stock/market/ticker` | GET | 获取最新行情 |

## 🔧 配置说明

### 必需配置

```properties
# Tushare API Token（必填）
tushare.api.token=你的Token
```

### 可选配置

```properties
# API URL（默认已配置）
tushare.api.url=http://111.170.34.57:8010/

# 超时时间（秒）
tushare.api.timeout=30

# 代理配置（如需要）
tushare.api.proxy-enabled=false
tushare.api.proxy-host=127.0.0.1
tushare.api.proxy-port=7890
```

## 📊 股票代码格式

- **上海证券交易所**：`股票代码.SH`（如 `600000.SH` - 浦发银行）
- **深圳证券交易所**：`股票代码.SZ`（如 `000001.SZ` - 平安银行）

## 🧪 测试

### 运行测试脚本

```bash
# Bash 测试
./test-tushare-api.sh

# Python 测试
python3 test_tushare_direct.py
```

### 手动测试

```bash
# 1. 测试连接
curl http://localhost:8088/api/stock/market/test

# 2. 获取日线数据
curl "http://localhost:8088/api/stock/market/kline/daily?tsCode=000001.SZ&limit=10"

# 3. 获取股票列表
curl "http://localhost:8088/api/stock/market/stock/list?exchange=SSE"
```

## 🎯 回测示例

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

## 📖 详细文档

- **[QUICK_START_STOCK.md](QUICK_START_STOCK.md)** - 快速启动指南
- **[STOCK_API_GUIDE.md](STOCK_API_GUIDE.md)** - 详细 API 使用说明
- **[STOCK_INTEGRATION_SUMMARY.md](STOCK_INTEGRATION_SUMMARY.md)** - 技术实现总结
- **[CHECKLIST.md](CHECKLIST.md)** - 部署检查清单

## 🌐 Swagger 文档

启动应用后访问：
```
http://localhost:8088/swagger-ui.html
```

## 🔍 常见问题

### Q: Token 不对怎么办？

A: 请检查：
1. Token 是否正确配置
2. 是否配置了正确的 API URL
3. 网络是否可以访问 Tushare API

### Q: 如何获取分钟线数据？

A: 分钟线数据需要更高的 Tushare 权限等级。如果权限不足，建议使用日线数据。

### Q: 原有的加密货币功能还能用吗？

A: 完全可以！本次集成采用最小化改动原则，原有功能完全不受影响。

## 📝 技术栈

- **Java 21**
- **Spring Boot 3.2.5**
- **OkHttp 4.12.0** - HTTP 客户端
- **Jackson** - JSON 解析
- **Tushare API** - 数据源

## 🏗️ 架构设计

```
前端/客户端
    ↓
StockMarketController (REST API)
    ↓
TushareApiService (服务层)
    ↓
TushareApiServiceImpl (HTTP + 数据解析)
    ↓
Tushare API (数据源)
```

## ✅ 兼容性

| 功能 | 状态 | 说明 |
|------|------|------|
| 加密货币交易 | ✅ | 完全兼容 |
| 回测框架 | ✅ | 直接支持股票 |
| 数据模型 | ✅ | 复用现有模型 |
| 策略系统 | ✅ | 所有策略可用 |

## 🚧 后续优化

### 短期（1-2周）
- [ ] 数据缓存机制
- [ ] 批量查询优化
- [ ] 错误重试机制

### 中期（1个月）
- [ ] 实时行情推送
- [ ] 更多数据接口
- [ ] 数据同步任务

### 长期（3个月+）
- [ ] 智能数据管理
- [ ] 性能优化
- [ ] 功能扩展

## 📞 技术支持

- **Tushare 文档**：https://www.yuque.com/a493465197/fl1fxx/ixwtsutxwaf0chdc
- **项目文档**：查看 docs 目录
- **Swagger API**：http://localhost:8088/swagger-ui.html

## 📄 许可证

本项目遵循原项目的许可证。

---

**开发完成时间**：2024-03-31  
**版本**：v1.0.0  
**状态**：✅ 已完成，可投入使用
