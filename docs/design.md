# Design Document: Spring Boot 3.5 + Java 25 OpenTelemetry Demo

## 1. Overview

本项目展示如何在 Spring Boot 3.5 + Java 25 环境下集成 OpenTelemetry，实现完整的可观测性（Observability）：**分布式追踪（Traces）**、**指标（Metrics）**、**日志（Logs）**。

参考项目：[spring-boot-and-opentelemetry](https://github.com/mhalbritter/spring-boot-and-opentelemetry)（基于 Spring Boot 4），本项目针对 Spring Boot 3.5 进行适配。

## 2. Architecture

### 2.1 微服务架构

采用三个微服务的分布式架构，展示跨服务的链路追踪能力：

```
                         ┌──────────────────┐
                         │   hello-service   │
                         │    (port 8080)    │
                         │   编排/入口服务    │
                         └────────┬─────────┘
                                  │
                    ┌─────────────┼─────────────┐
                    │                           │
           ┌────────▼─────────┐       ┌────────▼─────────┐
           │   user-service   │       │ greeting-service  │
           │   (port 8081)    │       │   (port 8082)     │
           │   用户数据服务    │       │   问候语服务       │
           └──────────────────┘       └───────────────────┘
```

### 2.2 可观测性架构

```
  Services ──OTLP──► Grafana OTEL LGTM (all-in-one)
                      ├── Grafana  (UI, :3000)
                      ├── Tempo    (Traces)
                      ├── Loki     (Logs)
                      └── Prometheus (Metrics)
```

所有服务通过 OTLP 协议（gRPC :4317 / HTTP :4318）将遥测数据发送到 Grafana OTEL LGTM 容器。

## 3. Spring Boot 3.5 vs Spring Boot 4 的关键差异

| 特性 | Spring Boot 4 (参考项目) | Spring Boot 3.5 (本项目) |
|------|-------------------------|-------------------------|
| OTel Starter | `spring-boot-starter-opentelemetry` | 不可用，需手动配置依赖 |
| Tracing 桥接 | 内置 | `micrometer-tracing-bridge-otel` |
| OTLP 导出 | 自动配置 | `opentelemetry-exporter-otlp` + 手动配置 |
| Logback Appender | 自动安装 | 手动注册 `OpenTelemetryAppender` |
| Metrics 导出 | 内置 OTLP 支持 | `opentelemetry-exporter-otlp` + actuator 配置 |
| Java 版本 | Java 25 | Java 25 (兼容) |

## 4. 技术栈

### 4.1 核心依赖

- **Spring Boot 3.5.x**
- **Java 25**
- **Gradle** (构建工具，Kotlin DSL)
- **Micrometer Tracing** + OpenTelemetry Bridge
- **OpenTelemetry SDK** + OTLP Exporter
- **OpenTelemetry Logback Appender**
- **Spring Data JDBC** + **H2** (user-service 数据存储)
- **Flyway** (数据库迁移)
- **RestClient** (服务间 HTTP 调用)

### 4.2 可观测性后端

- **Grafana OTEL LGTM** (all-in-one Docker 镜像)
  - Grafana (可视化)
  - Tempo (分布式追踪)
  - Loki (日志聚合)
  - Prometheus (指标存储)

## 5. 模块设计

### 5.1 shared 模块

提供所有服务共用的可观测性配置：

- **OpenTelemetryConfiguration** - JVM 指标注册（CPU、内存、线程、类加载）、HTTP 请求观测约定
- **ContextPropagationConfiguration** - 异步任务的追踪上下文传播
- **FilterConfiguration** - HTTP 过滤器（Header 日志、Trace ID 响应头）
- **InstallOpenTelemetryAppender** - Logback OTel Appender 安装

### 5.2 hello-service

- **REST 端点**：`GET /api/{userId}` - 根据用户 ID 返回个性化问候
- 调用 user-service 获取用户信息
- 调用 greeting-service 获取本地化问候语
- 支持同步/异步执行模式
- 可选的 OpenTelemetry API 直接使用示例（手动创建 Span、自定义 Counter）

### 5.3 user-service

- **REST 端点**：`GET /api/users/{id}` - 返回用户数据
- 使用 Spring Data JDBC + H2 内存数据库
- 使用 Flyway 管理初始数据

### 5.4 greeting-service

- **REST 端点**：`GET /api/greetings` - 根据 Accept-Language 返回本地化问候语
- 支持多语言：English、中文、日文

## 6. 配置设计

### 6.1 公共配置（每个服务的 application.properties）

```properties
# OpenTelemetry OTLP 导出
management.otlp.tracing.endpoint=http://localhost:4318/v1/traces
management.otlp.metrics.export.url=http://localhost:4318/v1/metrics
management.otlp.metrics.export.step=10s

# 追踪采样
management.tracing.sampling.probability=1.0

# 观测注解
management.observations.annotations.enabled=true

# 日志级别
logging.level.com.example=debug
```

### 6.2 Docker Compose

使用 `grafana/otel-lgtm:latest` 一体化镜像，暴露：
- `:3000` - Grafana UI
- `:4317` - OTLP gRPC
- `:4318` - OTLP HTTP

## 7. 展示的 OTel 特性

1. **自动 HTTP 追踪** - Spring Boot Actuator + Micrometer 自动追踪 HTTP 请求
2. **分布式追踪** - 跨三个服务的请求链路
3. **上下文传播** - 异步任务中的 Trace Context 传递
4. **JVM 指标** - CPU、内存、线程、类加载器指标
5. **自定义指标** - 使用 OTel API 创建自定义 Counter
6. **日志关联** - 日志自动关联 Trace ID 和 Span ID
7. **手动 Span** - 使用 OTel API 手动创建和管理 Span
