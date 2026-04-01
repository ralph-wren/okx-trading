#!/bin/bash

# 测试股票行情 API
# 用于验证股票市场数据接口是否正常工作

echo "=========================================="
echo "测试股票行情 API"
echo "=========================================="
echo ""

# API 基础 URL
BASE_URL="http://localhost:8088"

# 测试连接
echo "1. 测试 Tushare 连接..."
curl -s "${BASE_URL}/api/stock/market/test" | jq '.'
echo ""
echo ""

# 测试主流股票行情
STOCKS=("000001.SZ" "600519.SH" "000858.SZ" "600036.SH" "601318.SH" "000333.SZ")

echo "2. 测试主流股票行情..."
for stock in "${STOCKS[@]}"; do
    echo "获取 ${stock} 行情..."
    response=$(curl -s "${BASE_URL}/api/stock/market/ticker?tsCode=${stock}")
    
    # 检查是否成功
    code=$(echo "$response" | jq -r '.code')
    if [ "$code" == "200" ]; then
        symbol=$(echo "$response" | jq -r '.data.symbol')
        lastPrice=$(echo "$response" | jq -r '.data.lastPrice')
        priceChange=$(echo "$response" | jq -r '.data.priceChange')
        priceChangePercent=$(echo "$response" | jq -r '.data.priceChangePercent')
        
        echo "  ✓ ${symbol}: ¥${lastPrice} (${priceChangePercent}%)"
    else
        msg=$(echo "$response" | jq -r '.msg')
        echo "  ✗ 失败: ${msg}"
    fi
    echo ""
done

echo ""
echo "=========================================="
echo "测试完成"
echo "=========================================="
