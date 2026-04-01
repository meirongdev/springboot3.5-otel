# Spring Boot 3.5 + Java 25 OpenTelemetry Demo

基于 Spring Boot 3.5 和 Java 25 的 OpenTelemetry 可观测性示例项目，展示分布式追踪（Traces）、指标（Metrics）和日志（Logs）的完整集成。

> 参考 [spring-boot-and-opentelemetry](https://github.com/mhalbritter/spring-boot-and-opentelemetry)（Spring Boot 4），适配为 Spring Boot 3.5 版本。

## Architecture

```
                    ┌──────────────────┐
  curl/browser ───► │  hello-service   │ :8080
                    └────────┬─────────┘
                             │
               ┌─────────────┼─────────────┐
               │                           │
      ┌────────▼─────────┐       ┌────────▼─────────┐
      │  user-service    │       │ greeting-service  │
      │     :8081        │       │     :8082         │
      └──────────────────┘       └───────────────────┘

  All services ──OTLP──► Grafana OTEL LGTM (:3000)
```

- **hello-service** - 入口编排服务，调用下游服务组合响应
- **user-service** - 用户数据服务（H2 + Spring Data JDBC）
- **greeting-service** - 多语言问候语服务

## Tech Stack

- Java 25
- Spring Boot 3.5
- Micrometer Tracing + OpenTelemetry Bridge
- OpenTelemetry SDK + OTLP Exporter
- Grafana OTEL LGTM（Grafana + Tempo + Loki + Prometheus）
- Gradle (Kotlin DSL)

## Quick Start

### Prerequisites

- Java 25+
- Docker & Docker Compose

### 1. Start Observability Backend

```bash
docker compose up -d
```

Grafana UI: [http://localhost:3000](http://localhost:3000)

### 2. Start Services

```bash
# Terminal 1 - Greeting Service
./gradlew :greeting-service:bootRun

# Terminal 2 - User Service
./gradlew :user-service:bootRun

# Terminal 3 - Hello Service
./gradlew :hello-service:bootRun
```

### 3. Send Requests

```bash
# English greeting
curl http://localhost:8080/api/1

# Chinese greeting
curl -H "Accept-Language: zh" http://localhost:8080/api/1

# Japanese greeting
curl -H "Accept-Language: ja" http://localhost:8080/api/1
```

### 4. Explore in Grafana

打开 [http://localhost:3000](http://localhost:3000)：

- **Explore > Tempo** - 查看分布式追踪链路
- **Explore > Loki** - 查看关联 Trace ID 的日志
- **Explore > Prometheus** - 查看 JVM 和自定义指标

## OTel Features Demonstrated

| Feature | Description |
|---------|-------------|
| Distributed Tracing | 跨三个服务的请求链路追踪 |
| Context Propagation | 异步任务中的 Trace Context 自动传播 |
| JVM Metrics | CPU、内存、线程、类加载器指标采集 |
| Custom Metrics | 使用 OTel API 创建自定义 Counter |
| Log Correlation | 日志自动关联 Trace ID / Span ID |
| Manual Spans | 使用 OTel API 手动创建 Span |
| OTLP Export | 通过 OTLP 协议导出所有遥测数据 |

## Project Structure

```
├── shared/              # 公共 OTel 配置模块
├── hello-service/       # 编排服务 (:8080)
├── user-service/        # 用户服务 (:8081)
├── greeting-service/    # 问候语服务 (:8082)
├── compose.yaml         # Grafana OTEL LGTM
├── docs/
│   ├── design.md        # 设计文档
│   └── plan.md          # 实施计划
└── README.md
```

## Spring Boot 3.5 Adaptation Notes

本项目与基于 Spring Boot 4 的参考项目的主要区别：

1. **无 `spring-boot-starter-opentelemetry`** - 手动添加 `micrometer-tracing-bridge-otel` + `opentelemetry-exporter-otlp`
2. **Metrics 导出** - 使用 `micrometer-registry-otlp` 导出指标
3. **Logback Appender** - 通过 `InitializingBean` 手动安装 `OpenTelemetryAppender`
4. **RestClient** - Spring Boot 3.5 原生支持（3.2+ 引入）

## License

MIT
