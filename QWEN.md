# Spring Boot 3.5 + Java 25 OpenTelemetry Demo

## Project Overview

This project demonstrates OpenTelemetry integration with Spring Boot 3.5 and Java 25, showcasing full observability: **distributed tracing**, **metrics**, and **logs**.

**Reference**: Adapted from [spring-boot-and-opentelemetry](https://github.com/mhalbritter/spring-boot-and-opentelemetry) (Spring Boot 4) for Spring Boot 3.5 compatibility.

## Architecture

### Microservices

```
                    ┌──────────────────┐
                    │  hello-service   │ :8080
                    │  (Orchestrator)  │
                    └────────┬─────────┘
                             │
               ┌─────────────┼─────────────┐
               │                           │
      ┌────────▼─────────┐       ┌────────▼─────────┐
      │  user-service    │       │ greeting-service  │
      │     :8081        │       │     :8082         │
      │  (User Data)     │       │  (Localization)   │
      └──────────────────┘       └───────────────────┘
```

### Observability Backend

All services export telemetry via OTLP to **Grafana OTEL LGTM** (all-in-one):
- **Grafana** - UI (:3000)
- **Tempo** - Traces
- **Loki** - Logs
- **Prometheus** - Metrics

## Project Structure

```
springboot3.5-otel/
├── shared/              # Shared OpenTelemetry configuration module
├── hello-service/       # Orchestrator service (:8080)
├── user-service/        # User data service (:8081)
├── greeting-service/    # Greeting localization service (:8082)
├── compose.yaml         # Grafana OTEL LGTM Docker Compose
├── docs/
│   ├── design.md        # Design document
│   └── plan.md          # Implementation plan
├── README.md            # User-facing documentation
├── QWEN.md              # This file - development context
└── spec.md              # Original specification
```

## Tech Stack

| Component | Technology |
|-----------|------------|
| Runtime | Java 25 |
| Framework | Spring Boot 3.5.x |
| Build | Gradle (Kotlin DSL) |
| Tracing | Micrometer Tracing + OpenTelemetry Bridge |
| Metrics | Micrometer + OTLP Registry |
| Logs | Logback + OpenTelemetry Appender |
| HTTP Client | Spring RestClient |
| Database | H2 + Spring Data JDBC + Flyway |
| Backend | Grafana OTEL LGTM |

## Building and Running

### Prerequisites

- Java 25+
- Docker & Docker Compose
- Gradle 8.x (Wrapper included)

### Start Observability Backend

```bash
docker compose up -d
```

Access Grafana: http://localhost:3000

### Build All Services

```bash
./gradlew build
```

### Run Services

```bash
# Terminal 1 - Greeting Service
./gradlew :greeting-service:bootRun

# Terminal 2 - User Service
./gradlew :user-service:bootRun

# Terminal 3 - Hello Service
./gradlew :hello-service:bootRun
```

### Test Endpoints

```bash
# English greeting
curl http://localhost:8080/api/1

# Chinese greeting
curl -H "Accept-Language: zh" http://localhost:8080/api/1

# Japanese greeting
curl -H "Accept-Language: ja" http://localhost:8080/api/1
```

## Key Configuration

### OpenTelemetry Export (application.properties)

```properties
# OTLP Traces endpoint
management.otlp.tracing.endpoint=http://localhost:4318/v1/traces
# OTLP Metrics endpoint
management.otlp.metrics.export.url=http://localhost:4318/v1/metrics
# Metrics export interval
management.otlp.metrics.export.step=10s
# Sampling rate (1.0 = 100%)
management.tracing.sampling.probability=1.0
# Enable observation annotations
management.observations.annotations.enabled=true
```

## Spring Boot 3.5 Adaptation Notes

| Feature | Spring Boot 4 | Spring Boot 3.5 |
|---------|---------------|-----------------|
| OTel Starter | `spring-boot-starter-opentelemetry` | Not available |
| Tracing Bridge | Built-in | `micrometer-tracing-bridge-otel` |
| OTLP Export | Auto-configured | Manual `opentelemetry-exporter-otlp` |
| Logback Appender | Auto-installed | Manual `OpenTelemetryAppender` setup |
| Metrics Export | Built-in OTLP | `micrometer-registry-otlp` |

## Development Conventions

- **Code Style**: Follow Spring Boot conventions
- **Package Structure**: `com.example.<service-name>`
- **Configuration**: Use `application.properties` (not YAML)
- **Testing**: Integration tests with `@SpringBootTest`
- **Documentation**: Keep docs/ updated with design decisions

## Implementation Status

See `docs/plan.md` for detailed implementation phases.

## OTel Features Demonstrated

1. **Distributed Tracing** - Cross-service request correlation
2. **Context Propagation** - Trace context in async tasks
3. **JVM Metrics** - CPU, memory, threads, class loading
4. **Custom Metrics** - OTel API Counter creation
5. **Log Correlation** - Logs with Trace ID / Span ID
6. **Manual Spans** - Programmatic Span creation
7. **OTLP Export** - gRPC/HTTP telemetry export
