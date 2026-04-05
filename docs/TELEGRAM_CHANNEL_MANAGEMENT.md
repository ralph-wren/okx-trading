# Telegram 频道管理指南

## 问题说明

从日志可以看到，某些 Telegram 频道（如 `ppbbb`、`hwpc28`）出现 SSL 握手失败的问题：

```
SSL handshake failed for channel ppbbb (attempt 3/3): Remote host terminated the handshake
Failed to connect to Telegram channel ppbbb after 3 attempts due to SSL error
```

## 可能的原因

1. **频道不存在或已被删除**
2. **网络连接问题**：无法访问 Telegram 服务
3. **代理配置问题**：代理服务器不可用或配置错误
4. **频道被封禁**：在某些地区无法访问

## 自动优化

系统已经添加了自动禁用机制：

- ✅ **自动禁用**：连续失败 3 次后，频道会被自动禁用
- ✅ **避免重复错误**：禁用后不再尝试抓取，减少日志噪音
- ✅ **可手动重新启用**：通过 API 或数据库可以重新启用频道

## 查看频道状态

### 方法 1：通过 API

访问：`GET /api/telegram/channels`

返回所有频道列表，包括状态：

```json
[
  {
    "channelName": "jinse2017",
    "title": "金色财经",
    "active": true,
    "subscribers": 12000
  },
  {
    "channelName": "ppbbb",
    "title": "ppbbb",
    "active": false,
    "subscribers": null
  }
]
```

### 方法 2：查询数据库

```sql
SELECT * FROM telegram_channels WHERE active = false;
```

## 管理频道

### 禁用频道

```sql
UPDATE telegram_channels SET active = false WHERE channel_name = 'ppbbb';
```

或通过 API：

```bash
DELETE /api/telegram/channels/{channelName}
```

### 启用频道

```sql
UPDATE telegram_channels SET active = true WHERE channel_name = 'jinse2017';
```

### 添加新频道

```bash
POST /api/telegram/channels
{
  "channelName": "jinse2017",
  "title": "金色财经"
}
```

### 搜索频道

```bash
GET /api/telegram/search?q=金色财经
```

## 配置说明

在 `application.properties` 中：

```properties
# 默认频道列表（首次启动时自动添加）
telegram.scraper.channels=jinse2017

# 是否启用抓取（全局开关）
telegram.scraper.enabled=true

# 最大重试次数
telegram.scraper.retry.max=3

# 重试延迟（毫秒）
telegram.scraper.retry.delay=2000
```

## 代理配置

如果需要通过代理访问 Telegram：

```properties
# 启用代理
okx.proxy.enabled=true
okx.proxy.host=localhost
okx.proxy.port=10809
```

## 推荐的频道

### 中文加密货币资讯

- `jinse2017` - 金色财经（已配置）
- `btc123com` - BTC123
- `chainnews` - 链闻
- `odaily` - Odaily星球日报

### 英文加密货币资讯

- `cointelegraph` - Cointelegraph
- `coindesk` - CoinDesk
- `cryptonews` - Crypto News

### OKX 官方

- `OKX公告` - OKX 官方公告（特殊处理，从 API 获取）

## 故障排查

### 1. 检查频道是否存在

访问：`https://t.me/s/{channelName}`

例如：`https://t.me/s/jinse2017`

如果显示 "Channel not found"，说明频道不存在或已被删除。

### 2. 检查代理连接

```bash
curl -x http://localhost:10809 https://t.me/s/jinse2017
```

### 3. 检查网络连接

```bash
curl https://t.me/s/jinse2017
```

### 4. 查看详细日志

```properties
logging.level.com.okx.trading.service.impl.TelegramScraperService=DEBUG
```

## 清理无效频道

如果有很多无效频道，可以批量清理：

```sql
-- 查看所有禁用的频道
SELECT * FROM telegram_channels WHERE active = false;

-- 删除所有禁用的频道
DELETE FROM telegram_channels WHERE active = false;

-- 或者只删除特定频道
DELETE FROM telegram_channels WHERE channel_name IN ('ppbbb', 'hwpc28');
```

## 监控建议

1. **定期检查日志**：查看是否有频道被自动禁用
2. **监控活跃频道数**：确保有足够的有效频道
3. **定期更新频道列表**：添加新的优质频道，移除失效频道

## 总结

✅ **自动禁用机制已启用**：连续失败的频道会被自动禁用  
✅ **减少日志噪音**：禁用后不再尝试抓取  
✅ **灵活管理**：可通过 API 或数据库管理频道  
✅ **推荐频道列表**：提供了常用的加密货币资讯频道

---

创建时间：2026-04-05  
作者：Kiro AI Assistant
