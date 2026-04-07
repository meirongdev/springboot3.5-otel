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
- Spring Cloud Contract contract tests (producer stubs + consumer verification)
- ArchUnit architecture rules (including Java 25 record enforcement, no manual OTel SDK construction)

## Architecture

Three Spring Boot 3.5 / Java 25 microservices demonstrating distributed tracing with OpenTelemetry:

```
Client → hello-service (:8080) → user-service (:8081)     [Spring Data JDBC + H2 + Flyway + JDBC Tracing]
                               → greeting-service (:8082)  [multi-language greetings + Redis cache]
```

**`shared` module** (no bootable JAR) provides minimal OTel wiring:
- `OtelLogAppenderInstaller` — connects Spring-managed `OpenTelemetry` bean to the Logback appender declared in `logback-spring.xml`
- `AcceptLanguageNormalizer` — parses weighted `Accept-Language` headers (e.g. `"zh-CN,zh;q=0.9"` → `"zh"`)

Most OTel functionality is handled by Spring Boot 3.5 auto-configuration — JVM metrics, OTLP trace/metric/log export, and context propagation require **zero manual Java code**. Configuration is done entirely in `application.yaml`.

**hello-service** uses `@Observed` on `HelloService.getHello()` (§四 场景①), enriches the current span with `ObservationRegistry.getCurrentObservation().highCardinalityKeyValue("user.id", ...)` (§四 场景③), and delegates parallel downstream calls via an explicit `TaskExecutor` for correct OTel context propagation.

**greeting-service** has a `GreetingService` layer with:
- `@Observed` on the public entry point (§四 场景①)
- Manual `Observation.createNotStarted()` for the cache-miss path (§四 场景②)
- `StringRedisTemplate` for Redis cache — auto-generates `db.redis` spans via Lettuce (§五 Redis)

**user-service** uses `datasource-micrometer-spring-boot` for automatic JDBC span generation (§五 JDBC).

**All `GlobalExceptionHandler` classes** call `Span.current().recordException(ex)` and `Span.current().setStatus(StatusCode.ERROR, ...)` (§四 场景④).

**Observability backend:** `grafana/otel-lgtm` (all-in-one Tempo + Loki + Prometheus). OTLP HTTP endpoint: `:4318`. Grafana UI at `http://localhost:3000` (admin/admin). Custom dashboards (Services Overview, JVM Metrics, Logs & Traces) with metrics using `_milliseconds` naming convention from `micrometer-registry-otlp`.

**Profiling:** JFR (Java Flight Recorder) enabled via `JDK_JAVA_OPTIONS` in Docker Compose for continuous profiling in production.

## OTel Stack Notes (Spring Boot 3.5)

Spring Boot 3.5 auto-configures most OTel components. The required dependencies:
- `micrometer-tracing-bridge-otel` — tracing bridge
- `opentelemetry-exporter-otlp` — trace export
- `micrometer-registry-otlp` — metrics export (metric names use `_milliseconds` suffix, not `_seconds`)
- `opentelemetry-logback-appender-1.0` — Logback → OTLP log export
- `datasource-micrometer-spring-boot:1.0.6` — JDBC span generation (user-service only)
- `spring-boot-starter-data-redis` — Redis tracing via Lettuce auto-instrumentation (greeting-service only)

**Kafka observation properties** (correct paths per Spring Boot):
- `spring.kafka.template.observation-enabled: true` — KafkaTemplate producer tracing
- `spring.kafka.listener.observation-enabled: true` — listener container consumer tracing
- ⚠️ `spring.kafka.producer.*` / `spring.kafka.consumer.*` are native Kafka client properties — NOT observation flags

**Key config in `application.yaml`:**
- `management.otlp.logging.endpoint` — OTLP logs export (Spring Boot 3.4+ feature)
- `spring.threads.virtual.enabled: true` + `spring.main.keep-alive: true` — Virtual Threads (keep-alive prevents JVM exit since virtual threads are daemon threads)
- `management.observations.annotations.enabled: true` — enables `@Observed`

**Logback appender**: Declared in `shared/src/main/resources/logback-spring.xml`, connected to OTel SDK by `OtelLogAppenderInstaller` (Spring Boot 3.5 does not auto-install this bridge).

## Test Structure

| Type | Tools | Location |
|------|-------|----------|
| Unit | JUnit 5, Mockito | `*Test.java` |
| Integration | `@SpringBootTest`, H2 | `*IntegrationTest.java` |
| Contract (producer) | Spring Cloud Contract | `src/test/resources/contracts/*.groovy` in greeting-service, user-service |
| Contract (consumer) | Spring Cloud Contract StubRunner | `*ContractTest.java` in hello-service |
| Architecture | ArchUnit 1.4.1 | `arch-tests/` module |
| End-to-end | Embedded `HttpServer` stubs | `HelloControllerEndToEndTest` |

**ArchUnit enforces:** `shared` has no dependencies on service modules; controllers don't access repositories directly; no circular dependencies; DTO/Response/Request types must be records; no manual OTel SDK provider construction; `Span.current()` is only allowed in `GlobalExceptionHandler` classes.

## CI Pipeline

`.github/workflows/ci.yml`: spotlessCheck → `clean build` (runs all tests + coverage verification) → publish stubs to local Maven. Java 25 Temurin. Gradle configuration cache enabled.
