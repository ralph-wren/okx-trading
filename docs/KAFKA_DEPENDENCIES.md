# Kafka 依赖配置

## Maven 依赖

在 `pom.xml` 中添加以下依赖：

```xml
<!-- Spring Kafka -->
<dependency>
    <groupId>org.springframework.kafka</groupId>
    <artifactId>spring-kafka</artifactId>
</dependency>
```

## 完整的 pom.xml 示例

如果你的项目使用 Spring Boot，通常已经包含了 Kafka 的版本管理，只需添加上述依赖即可。

如果需要指定版本，可以这样配置：

```xml
<properties>
    <spring-kafka.version>3.0.12</spring-kafka.version>
</properties>

<dependencies>
    <!-- Spring Kafka -->
    <dependency>
        <groupId>org.springframework.kafka</groupId>
        <artifactId>spring-kafka</artifactId>
        <version>${spring-kafka.version}</version>
    </dependency>
</dependencies>
```

## 依赖说明

### spring-kafka

- **作用**: Spring 对 Kafka 的集成支持
- **包含内容**:
  - Kafka 客户端库（kafka-clients）
  - Spring 消息抽象
  - KafkaTemplate（生产者）
  - @KafkaListener（消费者）
  - 配置类和自动配置

### 传递依赖

`spring-kafka` 会自动引入以下依赖：

- `kafka-clients`: Kafka 官方 Java 客户端
- `spring-messaging`: Spring 消息抽象
- `spring-context`: Spring 上下文支持

## 版本兼容性

| Spring Boot 版本 | Spring Kafka 版本 | Kafka 版本 |
|-----------------|------------------|-----------|
| 3.0.x           | 3.0.x            | 3.3.x     |
| 2.7.x           | 2.8.x            | 3.0.x     |
| 2.6.x           | 2.7.x            | 2.8.x     |

## 验证依赖

添加依赖后，运行以下命令验证：

```bash
mvn dependency:tree | grep kafka
```

应该看到类似输出：

```
[INFO] +- org.springframework.kafka:spring-kafka:jar:3.0.12:compile
[INFO] |  +- org.apache.kafka:kafka-clients:jar:3.3.2:compile
[INFO] |  +- org.springframework:spring-messaging:jar:6.0.13:compile
```

## 可选依赖

### 1. Kafka Streams（如果需要流处理）

```xml
<dependency>
    <groupId>org.apache.kafka</groupId>
    <artifactId>kafka-streams</artifactId>
</dependency>
```

### 2. Avro 序列化（如果使用 Avro）

```xml
<dependency>
    <groupId>io.confluent</groupId>
    <artifactId>kafka-avro-serializer</artifactId>
    <version>7.5.0</version>
</dependency>
```

### 3. JSON 序列化（如果使用 JSON）

本项目已使用 FastJSON，无需额外依赖。

## 注意事项

1. **版本一致性**: 确保 Spring Boot、Spring Kafka 和 Kafka 版本兼容
2. **自动配置**: Spring Boot 会自动配置 Kafka，无需手动创建 Bean
3. **条件加载**: 本项目使用 `@ConditionalOnProperty` 实现可选加载

## 故障排查

### 依赖冲突

如果遇到依赖冲突，可以排除特定依赖：

```xml
<dependency>
    <groupId>org.springframework.kafka</groupId>
    <artifactId>spring-kafka</artifactId>
    <exclusions>
        <exclusion>
            <groupId>org.apache.kafka</groupId>
            <artifactId>kafka-clients</artifactId>
        </exclusion>
    </exclusions>
</dependency>

<!-- 手动指定 kafka-clients 版本 -->
<dependency>
    <groupId>org.apache.kafka</groupId>
    <artifactId>kafka-clients</artifactId>
    <version>3.3.2</version>
</dependency>
```

### 类找不到

如果遇到 `ClassNotFoundException`，检查：

1. 依赖是否正确添加
2. Maven 是否正确下载依赖
3. IDE 是否正确刷新项目

解决方法：

```bash
# 清理并重新构建
mvn clean install

# 强制更新依赖
mvn clean install -U
```

## 总结

只需在 `pom.xml` 中添加一个依赖即可：

```xml
<dependency>
    <groupId>org.springframework.kafka</groupId>
    <artifactId>spring-kafka</artifactId>
</dependency>
```

Spring Boot 会自动处理版本管理和依赖传递。
