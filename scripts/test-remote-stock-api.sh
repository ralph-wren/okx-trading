#!/bin/bash

# 测试远程服务器的股票 API
REMOTE_HOST="192.168.54.68"
BASE_URL="http://${REMOTE_HOST}:8088"

# 禁用代理以直接访问内网服务器
export no_proxy="192.168.54.68,localhost,127.0.0.1"

echo "========================================="
echo "测试远程股票 API"
echo "========================================="
echo "远程服务器: $REMOTE_HOST"
echo "基础 URL: $BASE_URL"
echo ""

# 1. 测试连接
echo "[测试 1/5] 测试 Tushare 连接..."
curl -s "${BASE_URL}/api/stock/market/test" | jq '.' 2>/dev/null || curl -s "${BASE_URL}/api/stock/market/test"
echo ""

# 2. 获取股票列表
echo "[测试 2/5] 获取上交所股票列表（前5个）..."
curl -s "${BASE_URL}/api/stock/market/stock/list?exchange=SSE&listStatus=L" | jq '.data[:5]' 2>/dev/null || echo "请求失败"
echo ""

# 3. 获取平安银行日线数据
echo "[测试 3/5] 获取平安银行(000001.SZ)最近5天日线数据..."
curl -s "${BASE_URL}/api/stock/market/kline/daily?tsCode=000001.SZ&limit=5" | jq '.data | length' 2>/dev/null || echo "请求失败"
echo ""

# 4. 获取平安银行最新行情
echo "[测试 4/5] 获取平安银行(000001.SZ)最新行情..."
curl -s "${BASE_URL}/api/stock/market/ticker?tsCode=000001.SZ" | jq '.' 2>/dev/null || curl -s "${BASE_URL}/api/stock/market/ticker?tsCode=000001.SZ"
echo ""

# 5. 检查 Swagger
echo "[测试 5/5] 检查 Swagger UI..."
HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" "${BASE_URL}/swagger-ui.html")
if [ "$HTTP_CODE" = "200" ] || [ "$HTTP_CODE" = "302" ]; then
    echo "✓ Swagger UI 可访问"
    echo "  访问: ${BASE_URL}/swagger-ui.html"
else
    echo "✗ Swagger UI 无法访问 (HTTP $HTTP_CODE)"
fi
echo ""

echo "========================================="
echo "测试完成"
echo "========================================="
echo ""
echo "如果所有测试通过，说明股票功能已成功部署！"
echo ""
echo "访问 Swagger UI 查看所有接口："
echo "  ${BASE_URL}/swagger-ui.html"
echo ""
