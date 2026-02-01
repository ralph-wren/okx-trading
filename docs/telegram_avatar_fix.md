# Telegram 频道头像修复文档

## 问题描述
配置文件 `application.properties` 中配置的默认频道（如 `jinse2017`）在初始化时未设置头像。后续的爬虫任务尝试从 `https://t.me/s/{channel}` 抓取头像，但由于网络环境或页面结构问题（如缺少 `og:image` 标签或连接失败），导致头像一直缺失。

## 解决方案
为了增强系统的健壮性，我们在 `TelegramScraperService` 中增加了多级头像获取回退策略。当默认爬取失败时，依次尝试以下来源：

1.  **Telesou 搜索 (原有逻辑)**: 尝试调用 `searchChannels` 接口（基于 `telesou.com`）搜索频道信息。如果找到完全匹配的频道，则更新头像、标题和订阅数。
2.  **TgStat 抓取 (新增逻辑)**: 如果 Telesou 未找到结果（例如 `jinse2017` 未被收录），则尝试访问 `https://tgstat.com/channel/@{channelName}`，并解析页面中的 `img.img-thumbnail` 元素获取头像 URL。

## 代码变更
文件：`src/main/java/com/okx/trading/service/impl/TelegramScraperService.java`

主要修改点：
1.  在 `scrapeChannel` 方法的 `finally` 阶段（或 catch 之后）调用 `updateChannelMetadataFromSearch`。
2.  重构 `updateChannelMetadataFromSearch` 方法：
    *   优先检查是否已有头像，若有则跳过。
    *   尝试 Telesou 搜索并匹配结果。
    *   若 Telesou 失败或无结果，调用 `searchAvatarFromTgStat`。
3.  新增 `searchAvatarFromTgStat` 方法：
    *   构造 `https://tgstat.com/channel/@...` URL。
    *   使用 Jsoup 连接（复用代理配置）。
    *   提取头像 URL 并自动处理协议前缀（`//` -> `https:`）。

## 验证方法
1.  启动应用或调用刷新接口：`POST /api/telegram/refresh`。
2.  查看频道列表：`GET /api/telegram/channels`。
3.  确认 `jinse2017` 等频道的 `avatarUrl` 字段已被正确填充。

## 备注
*   该修复依赖于 `okx.proxy` 配置，确保代理可用且支持访问 `tgstat.com`。
*   TgStat 的图片链接通常为 `static.tgstat.ru` 域名，需确保客户端能访问该域名。
