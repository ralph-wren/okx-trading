#!/bin/bash

# 测试股票回测功能
# 这个脚本会测试股票代码是否能正确路由到Tushare API

echo "=========================================="
echo "测试股票历史数据获取（用于回测）"
echo "=========================================="

# 测试贵州茅台 (600519.SH) 的历史数据
echo ""
echo "1. 测试贵州茅台 (600519.SH) 日线数据..."
curl -X GET "http://localhost:8080/api/market/fetch_history_with_integrity_check?symbol=600519.SH&interval=1D&startTimeStr=2024-01-01%2000:00:00&endTimeStr=2024-01-31%2023:59:59" \
  -H "accept: application/json" | jq '.'

echo ""
echo "=========================================="
echo "测试完成！"
echo "=========================================="
echo ""
echo "如果看到返回的数据包含 K线信息，说明股票回测功能已修复"
echo "如果看到 'Instrument ID doesn't exist' 错误，说明还在调用 OKX API"
