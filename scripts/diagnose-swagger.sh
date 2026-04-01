#!/bin/bash

echo "========================================="
echo "Swagger 接口诊断工具"
echo "========================================="
echo ""

BASE_URL="http://localhost:8088"

# 1. 检查应用是否启动
echo "[检查 1/5] 检查应用是否启动..."
if curl -s -o /dev/null -w "%{http_code}" "$BASE_URL/actuator/health" 2>/dev/null | grep -q "200\|404"; then
    echo "✓ 应用已启动"
else
    echo "✗ 应用未启动或无法访问"
    echo "  请先启动应用: mvn spring-boot:run"
    exit 1
fi
echo ""

# 2. 检查 Swagger UI 是否可访问
echo "[检查 2/5] 检查 Swagger UI..."
HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" "$BASE_URL/swagger-ui.html")
if [ "$HTTP_CODE" = "200" ] || [ "$HTTP_CODE" = "302" ]; then
    echo "✓ Swagger UI 可访问"
    echo "  URL: $BASE_URL/swagger-ui.html"
else
    echo "✗ Swagger UI 无法访问 (HTTP $HTTP_CODE)"
    echo "  尝试访问: $BASE_URL/swagger-ui/index.html"
fi
echo ""

# 3. 检查 API Docs
echo "[检查 3/5] 检查 API 文档..."
HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" "$BASE_URL/v3/api-docs")
if [ "$HTTP_CODE" = "200" ]; then
    echo "✓ API 文档可访问"
    
    # 检查是否包含股票接口
    if curl -s "$BASE_URL/v3/api-docs" | grep -q "stock/market"; then
        echo "✓ 股票市场接口已注册"
    else
        echo "✗ 股票市场接口未找到"
        echo "  可能原因："
        echo "  1. 应用需要重新编译"
        echo "  2. Controller 未被扫描到"
        echo "  3. 依赖注入失败"
    fi
else
    echo "✗ API 文档无法访问 (HTTP $HTTP_CODE)"
fi
echo ""

# 4. 直接测试股票接口
echo "[检查 4/5] 直接测试股票接口..."
HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" "$BASE_URL/api/stock/market/test")
if [ "$HTTP_CODE" = "200" ]; then
    echo "✓ 股票接口可访问"
    curl -s "$BASE_URL/api/stock/market/test" | jq '.' 2>/dev/null || curl -s "$BASE_URL/api/stock/market/test"
else
    echo "✗ 股票接口无法访问 (HTTP $HTTP_CODE)"
    echo "  可能原因："
    echo "  1. TushareApiService 依赖注入失败"
    echo "  2. TushareConfig 配置问题"
    echo "  3. 应用未重新编译"
fi
echo ""

# 5. 检查所有可用的 API 端点
echo "[检查 5/5] 列出所有可用的 API 端点..."
if curl -s "$BASE_URL/v3/api-docs" 2>/dev/null | jq -r '.paths | keys[]' 2>/dev/null | head -20; then
    echo ""
    echo "提示: 如果看不到 /api/stock/market 相关接口，请："
    echo "  1. 重新编译: mvn clean compile"
    echo "  2. 重启应用: mvn spring-boot:run"
    echo "  3. 检查日志中是否有错误信息"
else
    echo "无法获取 API 端点列表"
fi
echo ""

echo "========================================="
echo "诊断完成"
echo "========================================="
echo ""
echo "如果问题仍然存在，请执行以下步骤："
echo ""
echo "1. 重新编译项目："
echo "   mvn clean compile"
echo ""
echo "2. 重启应用："
echo "   mvn spring-boot:run"
echo ""
echo "3. 查看启动日志，检查是否有错误："
echo "   - TushareConfig 是否加载成功"
echo "   - TushareApiServiceImpl 是否创建成功"
echo "   - StockMarketController 是否注册成功"
echo ""
echo "4. 访问 Swagger UI："
echo "   $BASE_URL/swagger-ui.html"
echo "   或"
echo "   $BASE_URL/swagger-ui/index.html"
echo ""
