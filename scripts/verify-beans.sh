#!/bin/bash

echo "========================================="
echo "验证 Tushare Bean 创建"
echo "========================================="
echo ""

echo "运行集成测试..."
echo ""

mvn test -Dtest=TushareIntegrationTest

if [ $? -eq 0 ]; then
    echo ""
    echo "========================================="
    echo "✓ 所有 Bean 创建成功"
    echo "========================================="
    echo ""
    echo "现在请重新启动应用："
    echo "  mvn spring-boot:run"
    echo ""
    echo "然后访问："
    echo "  http://localhost:8088/swagger-ui.html"
    echo ""
else
    echo ""
    echo "========================================="
    echo "✗ Bean 创建失败"
    echo "========================================="
    echo ""
    echo "请检查上面的错误信息"
    echo ""
fi
