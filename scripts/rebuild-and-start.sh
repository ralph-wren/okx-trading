#!/bin/bash

echo "========================================="
echo "重新编译并启动应用"
echo "========================================="
echo ""

# 1. 清理
echo "[步骤 1/5] 清理项目..."
mvn clean

if [ $? -ne 0 ]; then
    echo "✗ 清理失败"
    exit 1
fi
echo "✓ 清理完成"
echo ""

# 2. 编译
echo "[步骤 2/5] 编译项目..."
mvn compile -DskipTests

if [ $? -ne 0 ]; then
    echo "✗ 编译失败，请检查错误信息"
    exit 1
fi
echo "✓ 编译完成"
echo ""

# 3. 运行测试（可选）
echo "[步骤 3/5] 运行 Bean 验证测试..."
mvn test -Dtest=TushareIntegrationTest 2>&1 | grep -E "(TushareConfig|TushareApiService|StockMarketController|BUILD SUCCESS|BUILD FAILURE)"

echo ""

# 4. 打包
echo "[步骤 4/5] 打包应用..."
mvn package -DskipTests

if [ $? -ne 0 ]; then
    echo "✗ 打包失败"
    exit 1
fi
echo "✓ 打包完成"
echo ""

# 5. 启动说明
echo "[步骤 5/5] 启动应用"
echo ""
echo "========================================="
echo "✓ 编译完成！"
echo "========================================="
echo ""
echo "现在请启动应用："
echo "  mvn spring-boot:run"
echo ""
echo "或者使用 JAR 文件："
echo "  java -jar target/okx-trading-0.0.1-SNAPSHOT.jar"
echo ""
echo "启动后，访问："
echo "  Swagger UI: http://localhost:8088/swagger-ui.html"
echo "  测试接口: curl http://localhost:8088/api/stock/market/test"
echo ""
echo "如果看到 'no static resource' 错误，请检查启动日志中是否有："
echo "  - Bean creation errors"
echo "  - Dependency injection failures"
echo "  - Configuration property binding errors"
echo ""
