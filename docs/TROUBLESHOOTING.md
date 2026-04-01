# 故障排查指南 - "no static resource" 错误

## 问题描述

访问 `/api/stock/market/test` 时显示 "no static resource" 错误。

## 根本原因

这个错误表示 Spring 没有找到对应的 Controller 映射，通常是因为：

1. **Bean 创建失败** - TushareConfig、TushareApiService 或 StockMarketController 未被创建
2. **依赖注入失败** - Controller 依赖的 Service 无法注入
3. **配置属性绑定失败** - @ConfigurationProperties 无法绑定配置

## 解决方案

### 方案 1：完整重新编译（推荐）

```bash
# 运行自动化脚本
./rebuild-and-start.sh

# 然后启动应用
mvn spring-boot:run
```

### 方案 2：手动步骤

```bash
# 1. 停止应用（如果正在运行）
# Ctrl+C

# 2. 清理
mvn clean

# 3. 编译
mvn compile

# 4. 打包
mvn package -DskipTests

# 5. 启动
mvn spring-boot:run
```

### 方案 3：验证 Bean 创建

```bash
# 运行测试验证 Bean 是否正确创建
mvn test -Dtest=TushareIntegrationTest

# 查看测试输出，应该看到：
# ✓ TushareConfig loaded successfully
# ✓ TushareApiService loaded successfully
# ✓ StockMarketController loaded successfully
```

## 检查启动日志

启动应用时，仔细查看日志，寻找以下信息：

### ✓ 正常日志（应该看到）

```
Creating bean 'tushareConfig'
Creating bean 'tushareApiServiceImpl'
Creating bean 'stockMarketController'
Mapped "{[/api/stock/market/test],methods=[GET]}" onto ...
Mapped "{[/api/stock/market/kline/daily],methods=[GET]}" onto ...
```

### ✗ 错误日志（不应该看到）

```
Error creating bean with name 'tushareConfig'
Error creating bean with name 'stockMarketController'
UnsatisfiedDependencyException
NoSuchBeanDefinitionException
Could not bind properties
```

## 常见错误及解决

### 错误 1：TushareConfig 创建失败

**错误信息：**
```
Error creating bean with name 'tushareConfig': 
Could not bind properties under 'tushare.api'
```

**原因：** 配置属性绑定失败

**解决：**
1. 检查 `application.properties` 中的配置
2. 确认 `TushareConfig` 有 `@Configuration` 和 `@Component` 注解
3. 重新编译项目

### 错误 2：TushareApiService 注入失败

**错误信息：**
```
Error creating bean with name 'stockMarketController': 
Unsatisfied dependency expressed through field 'tushareApiService'
```

**原因：** TushareApiServiceImpl 未被创建

**解决：**
1. 检查 `TushareApiServiceImpl` 是否有 `@Service` 注解
2. 检查包路径是否正确：`com.okx.trading.service.impl`
3. 确认 `TushareConfig` 已成功创建

### 错误 3：找不到 Bean

**错误信息：**
```
No qualifying bean of type 'com.okx.trading.service.TushareApiService'
```

**原因：** Service 实现类未被扫描

**解决：**
1. 确认文件在正确的包路径下
2. 检查 `@Service` 注解是否存在
3. 重新编译项目

## 验证修复

### 1. 检查 Bean 是否创建

```bash
# 运行测试
mvn test -Dtest=TushareIntegrationTest

# 应该看到所有测试通过
```

### 2. 检查接口映射

启动应用后，在日志中搜索：

```bash
# 搜索接口映射
grep "Mapped.*stock/market" logs/spring.log

# 应该看到 6 个接口映射
```

### 3. 测试接口

```bash
# 测试连接
curl http://localhost:8088/api/stock/market/test

# 预期输出：
# {"code":200,"message":"Tushare API连接成功","data":true}
```

### 4. 检查 Swagger

访问 http://localhost:8088/swagger-ui.html

应该能看到 "股票市场数据" 分组和 6 个接口。

## 如果问题仍然存在

### 收集诊断信息

```bash
# 1. 运行诊断脚本
./diagnose-swagger.sh > diagnosis.log 2>&1

# 2. 运行测试
mvn test -Dtest=TushareIntegrationTest > test.log 2>&1

# 3. 启动应用并保存日志
mvn spring-boot:run > startup.log 2>&1
```

### 检查文件

```bash
# 确认文件存在
ls -l src/main/java/com/okx/trading/config/TushareConfig.java
ls -l src/main/java/com/okx/trading/service/TushareApiService.java
ls -l src/main/java/com/okx/trading/service/impl/TushareApiServiceImpl.java
ls -l src/main/java/com/okx/trading/controller/StockMarketController.java

# 检查注解
grep "@Configuration" src/main/java/com/okx/trading/config/TushareConfig.java
grep "@Service" src/main/java/com/okx/trading/service/impl/TushareApiServiceImpl.java
grep "@RestController" src/main/java/com/okx/trading/controller/StockMarketController.java
```

### 查看详细错误

```bash
# 启动应用并查看详细日志
mvn spring-boot:run -X 2>&1 | tee detailed.log

# 搜索错误
grep -i "error\|exception\|failed" detailed.log | grep -i "tushare\|stock"
```

## 最后的手段

如果以上方法都不行，尝试：

### 1. 删除编译缓存

```bash
# 删除 target 目录
rm -rf target/

# 删除 Maven 缓存（谨慎）
rm -rf ~/.m2/repository/com/okx/

# 重新编译
mvn clean install -DskipTests
```

### 2. 检查 Java 版本

```bash
# 检查 Java 版本
java -version

# 应该是 Java 21
# 如果不是，请切换到 Java 21
```

### 3. 检查 Maven 版本

```bash
# 检查 Maven 版本
mvn -version

# 应该是 Maven 3.6+
```

### 4. 使用 IDE

如果使用 IntelliJ IDEA 或 Eclipse：

1. 右键项目 -> Maven -> Reimport
2. Build -> Rebuild Project
3. 运行 OkxTradingApplication 主类

## 快速命令参考

```bash
# 完整重新编译
./rebuild-and-start.sh

# 验证 Bean
mvn test -Dtest=TushareIntegrationTest

# 诊断问题
./diagnose-swagger.sh

# 启动应用
mvn spring-boot:run

# 测试接口
curl http://localhost:8088/api/stock/market/test
```

## 需要帮助？

如果问题仍未解决，请提供：

1. 完整的启动日志（startup.log）
2. 测试结果（test.log）
3. 诊断结果（diagnosis.log）
4. Java 版本和 Maven 版本
5. 操作系统信息

将这些信息发送给技术支持。
