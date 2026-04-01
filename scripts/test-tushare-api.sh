#!/bin/bash

# 测试Tushare API集成
# 确保应用已启动在 http://localhost:8088

BASE_URL="http://localhost:8088"

echo "========================================="
echo "测试Tushare API集成"
echo "========================================="
echo ""

# 1. 测试连接
echo "[测试 1/5] 测试Tushare API连接..."
curl -s "${BASE_URL}/api/stock/market/test" | jq '.'
echo ""

# 2. 获取股票列表（上交所）
echo "[测试 2/5] 获取上交所股票列表（前10个）..."
curl -s "${BASE_URL}/api/stock/market/stock/list?exchange=SSE&listStatus=L" | jq '.data[:10]'
echo ""

# 3. 获取平安银行日线数据
echo "[测试 3/5] 获取平安银行(000001.SZ)最近10天日线数据..."
curl -s "${BASE_URL}/api/stock/market/kline/daily?tsCode=000001.SZ&limit=10" | jq '.data | length'
echo ""

# 4. 获取平安银行5分钟线数据
echo "[测试 4/5] 获取平安银行(000001.SZ)最近10条5分钟线数据..."
curl -s "${BASE_URL}/api/stock/market/kline/minute?tsCode=000001.SZ&freq=5min&limit=10" | jq '.data | length'
echo ""

# 5. 获取平安银行最新行情
echo "[测试 5/5] 获取平安银行(000001.SZ)最新行情..."
curl -s "${BASE_URL}/api/stock/market/ticker?tsCode=000001.SZ" | jq '.'
echo ""

echo "========================================="
echo "测试完成"
echo "========================================="
