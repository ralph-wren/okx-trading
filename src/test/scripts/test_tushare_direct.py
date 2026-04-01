#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
直接测试 Tushare API 连接
用于验证 Token 和 API 配置是否正确
"""

import json
import requests

# Tushare 配置
TUSHARE_TOKEN = "krMJzeduYQNpQxOMToDCwfZGqmATGWnSalvyjEjbzJZuLZEvpMpEiRSEDKkPBFiu"
TUSHARE_URL = "http://111.170.34.57:8010/"


def test_tushare_api():
    """测试 Tushare API 连接"""
    print("=" * 50)
    print("测试 Tushare API 连接")
    print("=" * 50)
    print()

    # 测试 1: 获取指数基本信息
    print("[测试 1/3] 获取指数基本信息...")
    try:
        request_body = {
            "api_name": "index_basic",
            "token": TUSHARE_TOKEN,
            "params": {},
            "fields": "ts_code,name"
        }
        
        response = requests.post(TUSHARE_URL, json=request_body, timeout=30)
        response.raise_for_status()
        
        data = response.json()
        if data.get("code") == 0:
            print("✓ 连接成功")
            items = data.get("data", {}).get("items", [])
            print(f"  获取到 {len(items)} 条指数数据")
            if items:
                print(f"  示例: {items[0]}")
        else:
            print(f"✗ 连接失败: {data.get('msg', '未知错误')}")
    except Exception as e:
        print(f"✗ 请求异常: {e}")
    print()

    # 测试 2: 获取平安银行日线数据
    print("[测试 2/3] 获取平安银行(000001.SZ)日线数据...")
    try:
        request_body = {
            "api_name": "daily",
            "token": TUSHARE_TOKEN,
            "params": {
                "ts_code": "000001.SZ",
                "limit": 5
            },
            "fields": "ts_code,trade_date,open,high,low,close,vol"
        }
        
        response = requests.post(TUSHARE_URL, json=request_body, timeout=30)
        response.raise_for_status()
        
        data = response.json()
        if data.get("code") == 0:
            print("✓ 获取成功")
            items = data.get("data", {}).get("items", [])
            fields = data.get("data", {}).get("fields", [])
            print(f"  字段: {fields}")
            print(f"  数据条数: {len(items)}")
            if items:
                print(f"  最新数据: {items[0]}")
        else:
            print(f"✗ 获取失败: {data.get('msg', '未知错误')}")
    except Exception as e:
        print(f"✗ 请求异常: {e}")
    print()

    # 测试 3: 获取股票列表
    print("[测试 3/3] 获取上交所股票列表（前5个）...")
    try:
        request_body = {
            "api_name": "stock_basic",
            "token": TUSHARE_TOKEN,
            "params": {
                "exchange": "SSE",
                "list_status": "L",
                "limit": 5
            },
            "fields": "ts_code,symbol,name,area,industry"
        }
        
        response = requests.post(TUSHARE_URL, json=request_body, timeout=30)
        response.raise_for_status()
        
        data = response.json()
        if data.get("code") == 0:
            print("✓ 获取成功")
            items = data.get("data", {}).get("items", [])
            fields = data.get("data", {}).get("fields", [])
            print(f"  字段: {fields}")
            print(f"  股票数量: {len(items)}")
            for item in items:
                print(f"  - {item}")
        else:
            print(f"✗ 获取失败: {data.get('msg', '未知错误')}")
    except Exception as e:
        print(f"✗ 请求异常: {e}")
    print()

    print("=" * 50)
    print("测试完成")
    print("=" * 50)


if __name__ == "__main__":
    test_tushare_api()
