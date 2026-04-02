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
├── arch-tests/          # ArchUnit architecture tests
├── compose.yaml         # Grafana OTEL LGTM Docker Compose
├── docs/
│   ├── design.md        # Design document
│   ├── plan.md          # Implementation plan
│   ├── harness-design.md
│   ├── harness-plan.md
│   └── harness-recommendations-2026.md
├── grafana/
│   ├── dashboards/      # Grafana dashboard definitions
│   └── provisioning/    # Grafana provisioning config
├── scripts/
│   └── publish-pacts.sh # Pact contract publishing script
├── README.md            # User-facing documentation
├── QWEN.md              # This file - development context
└── spec.md              # Original specification
```

## Tech Stack

| Component | Technology |
|-----------|------------|
| Runtime | Java 25 |
| Framework | Spring Boot 3.5.0 |
| Build | Gradle 9.4.1 (Kotlin DSL) |
| Tracing | Micrometer Tracing + OpenTelemetry Bridge |
| Metrics | Micrometer + OTLP Registry |
| Logs | Logback + OpenTelemetry Appender |
| HTTP Client | Spring RestClient |
| Database | H2 + Spring Data JDBC + Flyway |
| Backend | Grafana OTEL LGTM |
| Testing | JUnit 5, Pact (Contract Testing), ArchUnit, Testcontainers |
| CI | GitHub Actions |

## Building and Running

### Prerequisites

- Java 25+
- Docker & Docker Compose
- Gradle 9.x (Wrapper included)

### Start Observability Backend

```bash
docker compose up -d
```

Access Grafana: http://localhost:3000 (admin/admin)

> **Note**: The `compose.yaml` uses a healthcheck with `curl` to verify Grafana readiness.
> The `start_period` is set to 60 seconds to allow all LGTM components to start.

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

## Development Conventions

### Code Style

- **Formatting**: Google Java Format 1.28.0 via Spotless
- **Static Analysis**: Error Prone enabled
- **Command**: `./gradlew spotlessCheck`

### Testing Practices

- **Unit Tests**: JUnit 5
- **Contract Tests**: Pact for consumer-driven contracts
- **Architecture Tests**: ArchUnit for module dependency rules
- **End-to-End**: Embedded HTTP smoke tests with downstream stubs
- **Coverage Gate**: JaCoCo with 60% minimum coverage

### Build Commands

```bash
# Format check
./gradlew spotlessCheck

# Full build with tests
./gradlew clean build

# Coverage report
./gradlew testCodeCoverageReport

# Run specific service tests
./gradlew :hello-service:test
```

### CI/CD

GitHub Actions workflow (`.github/workflows/ci.yml`):
- Java 25 with Temurin distribution
- Spotless formatting check
- Full build and test
- Artifact uploads: Pact files, JaCoCo reports, test results
- Optional Pact Broker publishing

## Spring Boot 3.5 Adaptation Notes

| Feature | Spring Boot 4 | Spring Boot 3.5 |
|---------|---------------|-----------------|
| OTel Starter | `spring-boot-starter-opentelemetry` | Not available |
| Tracing Bridge | Built-in | `micrometer-tracing-bridge-otel` |
| OTLP Export | Auto-configured | Manual `opentelemetry-exporter-otlp` |
| Logback Appender | Auto-installed | Manual `OpenTelemetryAppender` via `@PostConstruct` |
| Metrics Export | Built-in OTLP | `micrometer-registry-otlp` |

## Module Details

### shared

Core OpenTelemetry configuration shared across all services:

- `OpenTelemetryConfig` - JVM metrics (CPU, memory, threads, class loading)
- `ContextPropagationConfig` - Async task trace context propagation
- `FilterConfig` - HTTP filters (header logging, trace ID response header)
- `InstallOpenTelemetryAppender` - Logback OTel appender installation
- `AcceptLanguageNormalizer` - Language tag normalization utility

### hello-service

Orchestrator service that calls downstream services:

- `HelloController` - `GET /api/{userId}` endpoint
- `HelloService` - Orchestration logic (sync/async support)
- `UserServiceClient` - REST client for user-service
- `GreetingServiceClient` - REST client for greeting-service
- End-to-end test: `HelloControllerEndToEndTest`

### user-service

User data service with database:

- `UserController` - `GET /api/users/{id}` endpoint
- `UserService` - Business logic
- `UserRepository` - Spring Data JDBC repository
- `User` - JPA entity
- Flyway migrations for schema + seed data

### greeting-service

Multi-language greeting service:

- `GreetingController` - `GET /api/greetings` endpoint
- Supports: en, zh, ja
- Accept-Language header parsing

### arch-tests

Architecture tests using ArchUnit:

- Module dependency rules
- Package structure validation
- Coding convention enforcement

## OTel Features Demonstrated

1. **Distributed Tracing** - Cross-service request correlation
2. **Context Propagation** - Trace context in async tasks
3. **JVM Metrics** - CPU, memory, threads, class loading
4. **Custom Metrics** - OTel API Counter creation
5. **Log Correlation** - Logs with Trace ID / Span ID
6. **Manual Spans** - Programmatic Span creation
7. **OTLP Export** - gRPC/HTTP telemetry export
8. **HTTP Observation** - Auto-tracing of HTTP requests

## Quality Gates

| Gate | Tool | Threshold |
|------|------|-----------|
| Formatting | Spotless | Google Java Format |
| Static Analysis | Error Prone | Enabled |
| Coverage | JaCoCo | 60% minimum |
| Contracts | Pact | Consumer + Provider verification |
| Architecture | ArchUnit | Module rules enforced |

## Implementation Status

See `docs/plan.md` for detailed implementation phases. All core features are implemented and tested.

## Docker Build Notes

### Multi-Module Build Configuration

The Docker build requires all project modules to be present in the build context because `settings.gradle.kts` includes all modules:
- `shared/`
- `arch-tests/`
- `hello-service/`
- `user-service/`
- `greeting-service/`

Each service Dockerfile copies all modules to satisfy Gradle's project configuration requirements.

### .dockerignore Configuration

The `.dockerignore` file excludes build artifacts but includes all source modules:
```
build/
.gradle/
*.md
.github/
**/build/
**/.gradle/
```

### Healthcheck Configuration

The Grafana OTEL LGTM container uses `curl` for health checks (not `wget`, which is unavailable):
```yaml
healthcheck:
  test: ["CMD", "curl", "-f", "-s", "http://localhost:3000/api/health"]
  interval: 10s
  timeout: 5s
  retries: 3
  start_period: 60s
```

## Related Documentation

- `docs/design.md` - Architecture and design decisions
- `docs/plan.md` - Implementation plan and phases
- `docs/harness-recommendations-2026.md` - 2026 engineering recommendations
- `README.md` - User-facing quick start guide
