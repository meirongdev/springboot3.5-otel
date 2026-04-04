# Architecture & Implementation Guide

**Last Updated:** April 2, 2026  
**Status:** ✅ Complete and Operational

This document consolidates the architecture design, implementation plan, and operational notes for the Spring Boot 3.5 + Java 25 OpenTelemetry Demo project.

---

## Quick Start

```bash
# Start observability backend
make up

# Build all services
./gradlew build

# Run all services (3 terminals)
./gradlew :greeting-service:bootRun
./gradlew :user-service:bootRun
./gradlew :hello-service:bootRun

# Test
curl http://localhost:8080/api/1
```

**Grafana:** http://localhost:3000 (admin/admin)

---

## 1. System Architecture

### 1.1 Microservices

```
                         ┌──────────────────┐
                         │   hello-service   │
                         │    (port 8080)    │
                         │   Orchestrator    │
                         └────────┬─────────┘
                                  │
                    ┌─────────────┼─────────────┐
                    │                           │
           ┌────────▼─────────┐       ┌────────▼─────────┐
           │   user-service   │       │ greeting-service  │
           │   (port 8081)    │       │   (port 8082)     │
           │   User Data      │       │   Localization    │
           └──────────────────┘       └───────────────────┘
```

| Service | Port | Responsibility |
|---------|------|----------------|
| `hello-service` | 8080 | Entry point, orchestrates calls to downstream services |
| `user-service` | 8081 | User data (H2 + Spring Data JDBC + Flyway) |
| `greeting-service` | 8082 | Multi-language greetings (en/zh/ja) |

### 1.2 Observability Backend

```
  Services ──OTLP──► Grafana OTEL LGTM (all-in-one)
                      ├── Grafana  (UI, :3000)
                      ├── Tempo    (Traces)
                      ├── Loki     (Logs)
                      └── Prometheus (Metrics)
```

All services export telemetry via OTLP (gRPC :4317 / HTTP :4318).

---

## 2. Technology Stack

### 2.1 Core Dependencies

| Component | Technology | Version |
|-----------|------------|---------|
| Runtime | Java | 25 |
| Framework | Spring Boot | 3.5.0 |
| Build | Gradle (Kotlin DSL) | 9.4.1 |
| Tracing | Micrometer Tracing + OTel Bridge | Latest |
| Metrics | Micrometer + OTLP Registry | Latest |
| Logs | Logback + OTel Appender | Latest |
| HTTP Client | Spring RestClient | 3.5+ |
| Database | H2 + Spring Data JDBC + Flyway | Latest |

### 2.2 Quality & Testing

| Tool | Purpose | Version |
|------|---------|---------|
| Spotless | Code formatting | 8.4.0 (google-java-format 1.28.0) |
| Error Prone | Static analysis | 2.48.0 |
| JaCoCo | Coverage | 0.8.15 (60% minimum) |
| Spring Cloud Contract | Contract testing | 2024.0.x |
| ArchUnit | Architecture tests | 1.4.1 |
| Testcontainers | Integration tests | 2.0.4 |

### 2.3 Spring Boot 3.5 vs 4 Adaptation

| Feature | Spring Boot 4 | Spring Boot 3.5 |
|---------|---------------|-----------------|
| OTel Starter | `spring-boot-starter-opentelemetry` | Not available |
| Tracing Bridge | Built-in | `micrometer-tracing-bridge-otel` |
| OTLP Export | Auto-configured | Manual `opentelemetry-exporter-otlp` |
| Logback Appender | Auto-installed | `logback-spring.xml` + `OtelLogAppenderInstaller` |
| Virtual Threads | N/A | `spring.threads.virtual.enabled: true` |
| Metrics Export | Built-in OTLP | `micrometer-registry-otlp` |

---

## 3. Module Structure

```
springboot3.5-otel/
├── shared/              # Shared OTel configuration
├── hello-service/       # Orchestrator (:8080)
├── user-service/        # User data (:8081)
├── greeting-service/    # Localization (:8082)
├── arch-tests/          # ArchUnit architecture tests
├── compose.yaml         # Docker Compose (Grafana + services)
├── docs/                # Documentation
├── grafana/             # Grafana dashboards & provisioning
└── .github/workflows/   # CI/CD
```

### 3.1 shared Module

Provides shared observability configuration:

- `OtelLogAppenderInstaller` - Bridges Spring-managed `OpenTelemetry` bean to Logback appender (Spring Boot 3.5 does not auto-install this)
- `AcceptLanguageNormalizer` - Language tag normalization utility (weighted `Accept-Language` → simple tag)
- `logback-spring.xml` - Declares the OpenTelemetry Logback appender for OTLP log export

JVM metrics, OTLP trace/metric export, and async context propagation are all handled by Spring Boot auto-configuration. Virtual Threads (`spring.threads.virtual.enabled: true`) replace manual `ThreadPoolTaskExecutor`.

### 3.2 Service Modules

| Module | Components |
|--------|------------|
| `hello-service` | `HelloController`, `HelloService`, `UserServiceClient`, `GreetingServiceClient` |
| `user-service` | `UserController`, `UserService`, `UserRepository`, `User` entity, Flyway migrations |
| `greeting-service` | `GreetingController`, multi-language support (en/zh/ja) |

---

## 4. Configuration

### 4.1 OpenTelemetry Export (application.yaml)

```yaml
spring:
  threads:
    virtual:
      enabled: true          # Java 25 Virtual Threads

management:
  otlp:
    tracing:
      endpoint: http://localhost:4318/v1/traces
    metrics:
      export:
        url: http://localhost:4318/v1/metrics
        step: 10s
    logging:
      endpoint: http://localhost:4318/v1/logs   # Spring Boot 3.4+
  tracing:
    sampling:
      probability: 1.0
  observations:
    annotations:
      enabled: true
```

> **Note**: `micrometer-registry-otlp` exports durations in milliseconds (`http_server_requests_milliseconds_*`), not seconds.

### 4.2 Docker Compose Healthcheck

```yaml
healthcheck:
  test: ["CMD", "curl", "-f", "-s", "http://localhost:3000/api/health"]
  interval: 10s
  timeout: 5s
  retries: 3
  start_period: 60s
```

### 4.3 Docker Build Configuration

All Dockerfiles copy all modules (required by `settings.gradle.kts`):

```dockerfile
FROM eclipse-temurin:25-jdk AS builder
WORKDIR /build
COPY gradle/ gradle/
COPY gradlew build.gradle.kts settings.gradle.kts gradle.properties ./
COPY shared/ shared/
COPY arch-tests/ arch-tests/
COPY hello-service/ hello-service/
COPY user-service/ user-service/
COPY greeting-service/ greeting-service/
RUN ./gradlew :service-name:bootJar --no-daemon -x test
```

**.dockerignore:**
```
build/
.gradle/
*.md
.github/
**/build/
**/.gradle/
```

---

## 5. Test Harness

### 5.1 Test Pyramid

```
         ┌──────────┐
         │ Contract │  ← Spring Cloud Contract (producer-driven stubs)
         ├──────────┤
         │Integration│  ← Testcontainers, H2
         ├──────────┤
         │   Unit   │  ← JUnit 5 + Mockito
         └──────────┘
```

### 5.2 Test Types

| Type | Tools | Location |
|------|-------|----------|
| Unit Tests | JUnit 5 + Mockito | All service modules |
| Integration Tests | Testcontainers 2.0, `@SpringBootTest` | All service modules |
| Contract Tests | Spring Cloud Contract | greeting-service, user-service (providers), hello-service (consumer) |
| Architecture Tests | ArchUnit | arch-tests module |
| End-to-End Tests | Embedded HTTP + stubs | hello-service |

### 5.3 Quality Gates

| Gate | Tool | Threshold |
|------|------|-----------|
| Formatting | Spotless | Google Java Format |
| Static Analysis | Error Prone | Enabled |
| Coverage | JaCoCo | 60% minimum |
| Contracts | Spring Cloud Contract | Consumer stub verification + Provider stub generation |
| Architecture | ArchUnit | Module rules enforced |

---

## 6. CI/CD

### 6.1 GitHub Actions Workflow

```yaml
name: CI
on: [push, pull_request]
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v5 (temurin, java 25)
      - uses: gradle/actions/setup-gradle@v5
      - run: ./gradlew spotlessCheck build testCodeCoverageReport
      - upload: coverage-report
```

### 6.2 Gradle Caching

```properties
org.gradle.caching=true
org.gradle.configuration-cache=true
```

---

## 7. OTel Features Demonstrated

1. **Automatic HTTP Tracing** - Spring Boot Actuator + Micrometer
2. **Distributed Tracing** - Cross-service request correlation
3. **Context Propagation** - Trace context in async tasks
4. **JVM Metrics** - CPU, memory, threads, class loading
5. **Custom Metrics** - OTel API Counter creation
6. **Log Correlation** - Logs with Trace ID / Span ID
7. **Manual Spans** - Programmatic Span creation
8. **OTLP Export** - gRPC/HTTP telemetry export

---

## 8. Troubleshooting

### 8.1 Docker Build Issues

**Problem:** `arch-tests module missing`  
**Solution:** Removed from `.dockerignore`

**Problem:** `Other service modules missing`  
**Solution:** All Dockerfiles copy all module directories

**Problem:** `Grafana healthcheck failure`  
**Solution:** Changed from `wget` to `curl` in healthcheck

See [docker-fixes.md](docker-fixes.md) for detailed troubleshooting.

### 8.2 Common Commands

```bash
# Build and test
./gradlew clean build

# Format check
./gradlew spotlessCheck

# Coverage report
./gradlew testCodeCoverageReport

# Start observability backend
make up

# Stop all
make down

# View logs
docker compose logs -f
```

---

## 9. Related Documentation

| Document | Purpose |
|----------|---------|
| [../README.md](../README.md) | User-facing quick start |
| [../QWEN.md](../QWEN.md) | Developer context |
| [docker-fixes.md](docker-fixes.md) | Docker troubleshooting |
| [verification-report-2026-04-02.md](verification-report-2026-04-02.md) | Verification report |

---

## 10. Future Work (2026 Recommendations)

### Priority 1
- **Test layering convention**: Introduce tags (`unit`, `integration`, `contract`, `smoke`)
- **Docs-as-code validation**: Markdown link checks, command synchronization

### Priority 2
- **Container orchestration smoke tests**: Docker Compose-based end-to-end validation
- **Deterministic injection points**: `Clock`, random sources for time-sensitive tests

### Priority 3
- **Quality dashboard**: Coverage trends, contract test summaries, cache hit rates
