# Swagger 接口不显示问题修复指南

## 问题描述

新增的股票市场接口在 Swagger UI 中看不到。

## 可能的原因

1. **应用未重新编译** - 最常见的原因
2. **依赖注入失败** - TushareApiService 或 TushareConfig 未正确加载
3. **包扫描问题** - Controller 未被 Spring 扫描到
4. **Swagger 缓存** - 浏览器缓存了旧的 API 文档

## 解决方案

### 方案 1：重新编译和启动（推荐）

```bash
# 1. 停止当前运行的应用（Ctrl+C）

# 2. 清理并重新编译
mvn clean compile

# 3. 重新启动应用
mvn spring-boot:run

# 4. 等待应用完全启动（看到 "Started OkxTradingApplication" 日志）

# 5. 清除浏览器缓存并刷新 Swagger UI
# 访问: http://localhost:8088/swagger-ui.html
```

### 方案 2：使用诊断脚本

```bash
# 运行诊断脚本
./diagnose-swagger.sh

# 根据诊断结果进行修复
```

### 方案 3：检查启动日志

启动应用时，检查日志中是否有以下信息：

```
✓ 正常日志：
- "Mapped \"{[/api/stock/market/test]}" - 接口映射成功
- "Creating bean 'tushareApiServiceImpl'" - Service 创建成功
- "Creating bean 'stockMarketController'" - Controller 创建成功

✗ 错误日志：
- "Error creating bean" - Bean 创建失败
- "UnsatisfiedDependencyException" - 依赖注入失败
- "NoSuchBeanDefinitionException" - Bean 未找到
```

### 方案 4：手动验证接口

即使 Swagger 不显示，接口可能已经可用：

```bash
# 测试连接接口
curl http://localhost:8088/api/stock/market/test

# 如果返回 JSON 响应，说明接口正常工作
# 只是 Swagger 显示有问题
```

### 方案 5：检查配置文件

确保 `application.properties` 中有以下配置：

```properties
# Swagger 配置
springfox.documentation.swagger-ui.enabled=true
springdoc.api-docs.path=/api-docs
springdoc.swagger-ui.path=/swagger-ui.html

# Tushare 配置
tushare.api.token=krMJzeduYQNpQxOMToDCwfZGqmATGWnSalvyjEjbzJZuLZEvpMpEiRSEDKkPBFiu
tushare.api.url=http://111.170.34.57:8010/
```

### 方案 6：检查依赖

确保 `pom.xml` 中有 Swagger 依赖：

```xml
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
    <version>2.3.0</version>
</dependency>
```

## 详细排查步骤

### 步骤 1：检查编译

```bash
# 清理并编译
mvn clean compile

# 检查是否有编译错误
# 特别注意 TushareConfig, TushareApiServiceImpl, StockMarketController
```

### 步骤 2：检查 Bean 创建

启动应用时，在日志中搜索：

```bash
# 搜索 TushareConfig
grep -i "tushareconfig" logs/application.log

# 搜索 TushareApiServiceImpl
grep -i "tushareapi" logs/application.log

# 搜索 StockMarketController
grep -i "stockmarket" logs/application.log
```

### 步骤 3：检查接口映射

启动日志中应该看到：

```
Mapped "{[/api/stock/market/test],methods=[GET]}" onto ...
Mapped "{[/api/stock/market/stock/list],methods=[GET]}" onto ...
Mapped "{[/api/stock/market/kline/daily],methods=[GET]}" onto ...
...
```

### 步骤 4：访问 API 文档

尝试不同的 Swagger URL：

```bash
# 方式 1
http://localhost:8088/swagger-ui.html

# 方式 2
http://localhost:8088/swagger-ui/index.html

# 方式 3 - 直接查看 API 文档 JSON
http://localhost:8088/v3/api-docs

# 方式 4 - 查看特定分组
http://localhost:8088/v3/api-docs/swagger-config
```

### 步骤 5：清除缓存

```bash
# 清除浏览器缓存
# Chrome: Ctrl+Shift+Delete
# Firefox: Ctrl+Shift+Delete
# Safari: Command+Option+E

# 或使用隐私/无痕模式访问
```

## 常见错误及解决方法

### 错误 1：UnsatisfiedDependencyException

```
Error creating bean with name 'stockMarketController': 
Unsatisfied dependency expressed through field 'tushareApiService'
```

**原因：** TushareApiService 未被创建

**解决：**
1. 检查 TushareApiServiceImpl 是否有 @Service 注解
2. 检查 TushareConfig 是否有 @Configuration 注解
3. 重新编译项目

### 错误 2：NoSuchBeanDefinitionException

```
No qualifying bean of type 'com.okx.trading.service.TushareApiService'
```

**原因：** Service 实现类未被扫描到

**解决：**
1. 确认包路径正确：`com.okx.trading.service.impl`
2. 检查主应用类的 @SpringBootApplication 注解
3. 添加 @ComponentScan 注解（如果需要）

### 错误 3：BeanCreationException

```
Error creating bean with name 'tushareConfig': 
Could not bind properties
```

**原因：** 配置属性绑定失败

**解决：**
1. 检查 application.properties 中的配置
2. 确认 TushareConfig 类的 @ConfigurationProperties 注解
3. 检查属性名称是否匹配

## 验证修复

修复后，执行以下验证：

```bash
# 1. 测试接口
curl http://localhost:8088/api/stock/market/test

# 预期输出：
# {"code":200,"message":"Tushare API连接成功","data":true}

# 2. 查看 API 文档
curl http://localhost:8088/v3/api-docs | jq '.paths | keys[]' | grep stock

# 预期输出：
# "/api/stock/market/kline/daily"
# "/api/stock/market/kline/history"
# "/api/stock/market/kline/minute"
# "/api/stock/market/stock/list"
# "/api/stock/market/test"
# "/api/stock/market/ticker"

# 3. 访问 Swagger UI
# 打开浏览器访问: http://localhost:8088/swagger-ui.html
# 应该能看到 "股票市场数据" 分组
```

## 如果问题仍然存在

### 1. 收集信息

```bash
# 收集启动日志
mvn spring-boot:run > startup.log 2>&1

# 查看错误信息
grep -i "error\|exception\|failed" startup.log

# 查看 Bean 创建信息
grep -i "creating bean" startup.log | grep -i "tushare\|stock"
```

### 2. 检查文件

确认以下文件存在且内容正确：

```bash
# 检查文件是否存在
ls -l src/main/java/com/okx/trading/config/TushareConfig.java
ls -l src/main/java/com/okx/trading/service/TushareApiService.java
ls -l src/main/java/com/okx/trading/service/impl/TushareApiServiceImpl.java
ls -l src/main/java/com/okx/trading/controller/StockMarketController.java

# 检查文件内容
grep -n "@Configuration" src/main/java/com/okx/trading/config/TushareConfig.java
grep -n "@Service" src/main/java/com/okx/trading/service/impl/TushareApiServiceImpl.java
grep -n "@RestController" src/main/java/com/okx/trading/controller/StockMarketController.java
```

### 3. 临时解决方案

如果 Swagger 仍然不显示，但接口可以正常工作：

```bash
# 使用 curl 或 Postman 直接调用接口
# 或者使用以下脚本测试所有接口
./test-tushare-api.sh
```

## 联系支持

如果以上方法都无法解决问题，请提供：

1. 完整的启动日志
2. `mvn clean compile` 的输出
3. `curl http://localhost:8088/api/stock/market/test` 的结果
4. Java 版本：`java -version`
5. Maven 版本：`mvn -version`

## 快速命令参考

```bash
# 完整的重启流程
mvn clean compile && mvn spring-boot:run

# 测试接口
curl http://localhost:8088/api/stock/market/test

# 查看 API 文档
curl http://localhost:8088/v3/api-docs | jq '.'

# 运行诊断
./diagnose-swagger.sh

# 运行测试
./test-tushare-api.sh
```
