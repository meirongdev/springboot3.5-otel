# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Test Commands

```bash
# Build
./gradlew build
./gradlew clean build

# Test
./gradlew test
./gradlew :hello-service:test --tests "*HelloServiceTest*"   # Single test
./gradlew testCodeCoverageReport                              # JaCoCo coverage

# Formatting (Google Java Format, 2-space indentation)
./gradlew spotlessApply    # Auto-fix
./gradlew spotlessCheck    # Validate

# Run services locally (separate terminals)
./gradlew :hello-service:bootRun     # :8080
./gradlew :user-service:bootRun      # :8081
./gradlew :greeting-service:bootRun  # :8082
```

**Make shortcuts:** `make build`, `make test`, `make fmt`, `make check`, `make coverage`, `make up` (Docker Compose), `make verify-otel`

**Quality gates enforced on every build:**
- Spotless (Google Java Format 1.28.0)
- Error Prone static analysis
- JaCoCo 60% minimum coverage
- Pact contract tests (consumer + provider)
- ArchUnit architecture rules (including Java 25 record enforcement, no manual OTel SDK construction)

## Architecture

Three Spring Boot 3.5 / Java 25 microservices demonstrating distributed tracing with OpenTelemetry:

```
Client → hello-service (:8080) → user-service (:8081)     [Spring Data JDBC + H2 + Flyway]
                               → greeting-service (:8082)  [multi-language greetings]
```

**`shared` module** (no bootable JAR) provides minimal OTel wiring:
- `OtelLogAppenderInstaller` — connects Spring-managed `OpenTelemetry` bean to the Logback appender declared in `logback-spring.xml`
- `AcceptLanguageNormalizer` — parses weighted `Accept-Language` headers (e.g. `"zh-CN,zh;q=0.9"` → `"zh"`)

Most OTel functionality is handled by Spring Boot 3.5 auto-configuration — JVM metrics, OTLP trace/metric/log export, and context propagation require **zero manual Java code**. Configuration is done entirely in `application.yaml`.

**hello-service** uses `@Observed` on `HelloService` methods to create custom spans. HTTP calls to downstream services use Spring `RestClient`; W3C Trace Context is propagated automatically. Virtual Threads enabled via `spring.threads.virtual.enabled=true`.

**Observability backend:** `grafana/otel-lgtm` (all-in-one Tempo + Loki + Prometheus). OTLP HTTP endpoint: `:4318`. Grafana UI at `http://localhost:3000` (admin/admin). Custom dashboards (Services Overview, JVM Metrics, Logs & Traces) with metrics using `_milliseconds` naming convention from `micrometer-registry-otlp`.

**Profiling:** JFR (Java Flight Recorder) enabled via `JDK_JAVA_OPTIONS` in Docker Compose for continuous profiling in production.

## OTel Stack Notes (Spring Boot 3.5)

Spring Boot 3.5 auto-configures most OTel components. The required dependencies:
- `micrometer-tracing-bridge-otel` — tracing bridge
- `opentelemetry-exporter-otlp` — trace export
- `micrometer-registry-otlp` — metrics export (metric names use `_milliseconds` suffix, not `_seconds`)
- `opentelemetry-logback-appender-1.0` — Logback → OTLP log export

**Key config in `application.yaml`:**
- `management.otlp.logging.endpoint` — OTLP logs export (Spring Boot 3.4+ feature)
- `spring.threads.virtual.enabled: true` — Virtual Threads (replaces manual `ThreadPoolTaskExecutor`)
- `management.observations.annotations.enabled: true` — enables `@Observed`

**Logback appender**: Declared in `shared/src/main/resources/logback-spring.xml`, connected to OTel SDK by `OtelLogAppenderInstaller` (Spring Boot 3.5 does not auto-install this bridge).

## Test Structure

| Type | Tools | Location |
|------|-------|----------|
| Unit | JUnit 5, Mockito | `*Test.java` |
| Integration | `@SpringBootTest`, H2 | `*IntegrationTest.java` |
| Contract (consumer) | Pact JVM 4.6.17 | `*PactConsumerTest.java` in hello-service |
| Contract (provider) | Pact JVM | `*PactProviderTest.java` in user-service, greeting-service |
| Architecture | ArchUnit 1.4.1 | `arch-tests/` module |
| End-to-end | Embedded `HttpServer` stubs | `HelloControllerEndToEndTest` |

ArchUnit enforces: `shared` has no dependencies on service modules; controllers don't access repositories directly; no circular dependencies; DTO/Response/Request types must be records; no manual OTel SDK provider construction.

## CI Pipeline

`.github/workflows/ci.yml`: spotlessCheck → `clean build` (runs all tests + coverage verification) → upload pact files + coverage reports. Java 25 Temurin. Gradle configuration cache enabled.
