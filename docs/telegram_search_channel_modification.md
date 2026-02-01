# Telegram 频道搜索功能修改文档

## 修改原因
原定的 `https://telegramsearchengine.com/` 搜索功能无法访问（返回 404）。
1. **Google Search**: 频繁出现 429 Too Many Requests 错误。
2. **Bing Search**: 返回内容为空或相关性极低。
3. **DuckDuckGo HTML 版**: 存在 Bot 验证（Captcha）且搜索结果相关性不稳定。

## 解决方案
切换为使用 DuckDuckGo Lite 版搜索 (`https://lite.duckduckgo.com/lite/`) 配合 `site:t.me/s/` 语法来查找 Telegram 频道。

### 技术实现 details
1.  **搜索源**: DuckDuckGo Lite Search
    *   URL: `https://lite.duckduckgo.com/lite/?q=site:t.me/s/ "{query}"`
    *   优势: 
        *   纯 HTML 表格布局，结构极简，易于解析。
        *   **无 JS 渲染需求**，加载速度快。
        *   **反爬策略最宽松**，不易触发 Bot 验证。
        *   支持精确匹配（使用引号包裹关键词）。
2.  **过滤规则**:
    *   查询词: `site:t.me/s/ "{query}"` (确保只搜索频道预览页面，且关键词精确匹配)
    *   结果解析: 提取包含 `t.me/s/` 的链接。
3.  **解析逻辑**:
    *   链接选择器: `a.result-link` (直接定位结果链接)
    *   标题提取: 链接文本
    *   描述提取: 链接所在行 (`tr`) 的下一行 (`nextElementSibling`) 中的 `.result-snippet` 元素。
    *   频道ID提取: 从 URL 中截取 `/s/` 之后的部分，并去除查询参数。
4.  **代理配置**:
    *   沿用原有的代理配置 (`okx.proxy.host`, `okx.proxy.port`, `okx.proxy.enabled`)。

## 验证方法
1.  调用接口 `/api/telegram/search?query=okx`
2.  查看日志确认使用 DuckDuckGo Lite 搜索且解析出结果。
3.  确认返回结果中只包含 Telegram 频道信息，且内容与关键词相关。

## 历史尝试记录
1.  **Google Search**: 遇到 429 错误，IP 被封禁。
2.  **Bing Search**: 返回 HTTP 200 但搜索结果中没有 `t.me/s/` 链接，或内容被 JS 隐藏。
3.  **DuckDuckGo (Standard/HTML)**: 遇到 Bot 挑战页面，无法获取结果；使用 `intitle:` 语法时返回空结果。
4.  **DuckDuckGo Lite**: 测试通过，返回纯 HTML 表格，包含相关频道链接和描述。
