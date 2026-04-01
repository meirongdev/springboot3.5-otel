# Implementation Plan: Spring Boot 3.5 + Java 25 OpenTelemetry Demo

## Phase 1: Project Scaffolding

### 1.1 Gradle 多模块项目初始化
- [ ] 创建根项目 `settings.gradle.kts`，包含 4 个子模块：`shared`、`hello-service`、`user-service`、`greeting-service`
- [ ] 创建根 `build.gradle.kts`，配置：
  - Spring Boot 3.5.x plugin
  - Java 25 toolchain
  - 公共依赖版本管理（OpenTelemetry BOM）
- [ ] 配置 `gradle.properties`（Spring Boot 版本、OTel 版本）
- [ ] 添加 Gradle Wrapper（8.x）

### 1.2 依赖配置

各模块核心依赖：

**shared:**
```
- spring-boot-starter-web
- spring-boot-starter-actuator
- micrometer-tracing-bridge-otel
- opentelemetry-exporter-otlp
- opentelemetry-logback-appender
- opentelemetry-api (手动 Span/Metrics)
- micrometer-registry-otlp
```

**hello-service:**
```
- project(":shared")
- spring-boot-starter-web
```

**user-service:**
```
- project(":shared")
- spring-boot-starter-data-jdbc
- flyway-core
- h2 (runtime)
```

**greeting-service:**
```
- project(":shared")
```

## Phase 2: Shared Module

### 2.1 OpenTelemetry 配置类
- [ ] `OpenTelemetryConfig` - 注册 JVM 指标（ProcessorMetrics, JvmMemoryMetrics, JvmThreadMetrics, ClassLoaderMetrics）
- [ ] `ContextPropagationConfig` - 提供 `ContextPropagatingTaskDecorator` Bean
- [ ] `FilterConfig` - 注册 HTTP 过滤器（Header Logger、Trace ID 响应头）
- [ ] `InstallOpenTelemetryAppender` - 启动时安装 Logback OTel Appender

### 2.2 公共工具类
- [ ] `HeaderLoggerFilter` - 记录 HTTP 请求头
- [ ] `AddTraceIdFilter` - 将 Trace ID 添加到 HTTP 响应头

## Phase 3: Greeting Service (最简单，先实现)

### 3.1 业务逻辑
- [ ] `GreetingServiceApplication` - 启动类，导入 shared 配置
- [ ] `GreetingController` - `GET /api/greetings`，根据 `Accept-Language` 返回问候语
- [ ] `Greeting` record - 数据模型
- [ ] 支持语言：en、zh、ja

### 3.2 配置
- [ ] `application.properties` - 端口 8082、OTel 配置

## Phase 4: User Service

### 4.1 数据层
- [ ] `User` entity
- [ ] `UserRepository` (Spring Data JDBC)
- [ ] Flyway 迁移脚本：建表 + 初始数据

### 4.2 业务逻辑
- [ ] `UserServiceApplication` - 启动类
- [ ] `UserController` - `GET /api/users/{id}`

### 4.3 配置
- [ ] `application.properties` - 端口 8081、H2、Flyway、OTel 配置

## Phase 5: Hello Service (编排服务)

### 5.1 HTTP 客户端
- [ ] `HttpClientConfig` - 配置 RestClient 连接 user-service 和 greeting-service
- [ ] `UserServiceClient` - 封装 user-service 调用
- [ ] `GreetingServiceClient` - 封装 greeting-service 调用

### 5.2 业务逻辑
- [ ] `HelloServiceApplication` - 启动类，启用异步支持
- [ ] `HelloController` - `GET /api/{userId}`
- [ ] `HelloService` - 编排逻辑，支持同步/异步模式

### 5.3 OTel API 直接使用示例
- [ ] `OpenTelemetryApiDemoComponent` - 手动创建 Span、自定义 Counter
- [ ] `OpenTelemetryMetricsConfig` - 配置 OTel Metrics SDK（MeterProvider + PeriodicMetricReader）

### 5.4 配置
- [ ] `application.properties` - 端口 8080、服务 URL、异步开关、OTel 配置

## Phase 6: Docker Compose & 可观测性后端

- [ ] `compose.yaml` - Grafana OTEL LGTM 容器
- [ ] `otel-collector-config.yaml` - OTel Collector 配置（可选，LGTM 镜像内置）

## Phase 7: 文档 & 测试

### 7.1 文档
- [x] `docs/design.md` - 设计文档
- [x] `docs/plan.md` - 实施计划
- [ ] `README.md` - 项目说明、快速启动、使用指南

### 7.2 验证
- [ ] 启动所有服务，发送请求验证链路追踪
- [ ] 在 Grafana 中查看 Traces、Metrics、Logs
- [ ] 验证异步模式下的上下文传播

## Implementation Order

```
Phase 1 (Scaffolding) → Phase 2 (Shared) → Phase 3 (Greeting) → Phase 4 (User) → Phase 5 (Hello) → Phase 6 (Docker) → Phase 7 (Docs & Test)
```

每个 Phase 完成后应可独立编译通过。Phase 3-5 的服务可以独立启动验证。

## Key Adaptation Notes (Spring Boot 4 → 3.5)

1. **无 `spring-boot-starter-opentelemetry`**：需要手动添加 `micrometer-tracing-bridge-otel` + `opentelemetry-exporter-otlp`
2. **OTLP Metrics 导出**：使用 `micrometer-registry-otlp` 替代 SB4 的内置 OTLP metrics 支持
3. **Logback Appender**：需要手动通过 `InitializingBean` 安装 `OpenTelemetryAppender`
4. **RestClient**：Spring Boot 3.5 支持 RestClient（3.2+ 引入），可以直接使用
5. **Observation API**：Spring Boot 3.5 完整支持 `management.observations.annotations.enabled`
6. **Java 25**：Spring Boot 3.5 兼容 Java 25
