#!/bin/bash

echo "========================================="
echo "重启应用并验证股票接口"
echo "========================================="
echo ""

# 1. 清理并编译
echo "[步骤 1/4] 清理并重新编译..."
mvn clean compile

if [ $? -ne 0 ]; then
    echo "✗ 编译失败，请检查错误信息"
    exit 1
fi

echo "✓ 编译成功"
echo ""

# 2. 提示用户启动应用
echo "[步骤 2/4] 请在另一个终端窗口启动应用："
echo "  mvn spring-boot:run"
echo ""
echo "等待应用启动完成后，按回车继续..."
read

# 3. 测试接口
echo "[步骤 3/4] 测试股票接口..."
sleep 2

HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" "http://localhost:8088/api/stock/market/test")

if [ "$HTTP_CODE" = "200" ]; then
    echo "✓ 股票接口可访问"
    echo ""
    echo "接口响应："
    curl -s "http://localhost:8088/api/stock/market/test" | jq '.' 2>/dev/null || curl -s "http://localhost:8088/api/stock/market/test"
else
    echo "✗ 股票接口无法访问 (HTTP $HTTP_CODE)"
    echo "  请检查应用是否已启动"
    exit 1
fi

echo ""

# 4. 检查 Swagger
echo "[步骤 4/4] 检查 Swagger UI..."
echo ""
echo "请访问以下 URL 查看 Swagger 文档："
echo "  http://localhost:8088/swagger-ui.html"
echo "  或"
echo "  http://localhost:8088/swagger-ui/index.html"
echo ""
echo "在 Swagger UI 中，你应该能看到："
echo "  - 股票市场数据 (Stock Market Controller)"
echo "  - 6 个新增接口"
echo ""

# 5. 列出所有接口
echo "所有可用的股票接口："
echo "  1. GET  /api/stock/market/test - 测试连接"
echo "  2. GET  /api/stock/market/stock/list - 获取股票列表"
echo "  3. GET  /api/stock/market/kline/daily - 获取日线数据"
echo "  4. GET  /api/stock/market/kline/minute - 获取分钟线数据"
echo "  5. GET  /api/stock/market/kline/history - 获取历史K线"
echo "  6. GET  /api/stock/market/ticker - 获取最新行情"
echo ""

echo "========================================="
echo "✓ 验证完成"
echo "========================================="
