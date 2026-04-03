# Copilot instructions — springboot3.5-otel

Purpose
- Short, targeted guidance for future Copilot sessions working in this repository: where to find build/test/lint commands, the high-level architecture, and repository-specific conventions Copilot should follow or reference.

Build / test / lint (quick commands)
- Build all: ./gradlew clean build
- Run all tests: ./gradlew test
- Run a single test class (per-module):
  ./gradlew :hello-service:test --tests com.example.hello.HelloControllerTest
- Run a single test method:
  ./gradlew :hello-service:test --tests "com.example.hello.HelloControllerTest.testMethodName"
- Pattern match single test class:
  ./gradlew :hello-service:test --tests "*HelloServiceTest*"
- JaCoCo coverage report: ./gradlew testCodeCoverageReport
- Formatting (Spotless): ./gradlew spotlessApply  (auto-fix)
- Formatting check: ./gradlew spotlessCheck
- Run a service locally: ./gradlew :<service>:bootRun  (e.g. :hello-service:bootRun)
- Useful Make shortcuts (recommended): make build, make test, make fmt, make up, make verify-otel

High-level architecture (big picture)
- Multi-module Gradle project (root settings include: shared, hello-service, user-service, greeting-service, arch-tests).
- hello-service (:8080) is the orchestrator; it calls user-service (:8081) and greeting-service (:8082) and returns a composed response.
- shared/ holds common telemetry wiring (OtelLogAppenderInstaller), logback-spring.xml, and shared DTOs/clients. It is intentionally lightweight and must not depend on service modules.
- Observability stack: Micrometer Tracing + OpenTelemetry bridge for tracing, micrometer-registry-otlp for metrics, and opentelemetry-logback-appender for logs. All services export OTLP to the Grafana OTEL LGTM backend defined in compose.yaml (Grafana @ :3000).
- JVM profiling: Java Flight Recorder (JFR) enabled via JDK_JAVA_OPTIONS; helper scripts in scripts/ and Makefile targets for jfr-* commands.
- Verification harness: scripts/verify-otel.sh and make verify-otel validate traces/metrics/logs after services start.

Key repository conventions & rules Copilot should respect
- Formatting & style
  - Java: Google Java Format (spotless), 2-space indentation (see .editorconfig).
  - Build scripts (Kotlin DSL): 4-space indentation.
  - Enforce spotlessCheck and Error Prone on CI; do not suggest changes that break those checks.

- Telemetry / Observability
  - Prefer Spring Micrometer Tracing + OpenTelemetry bridge and Spring Boot 3.5 auto-configuration over manual OTel SDK wiring.
  - Do NOT construct or register a manual OpenTelemetry SDK provider in application code; the repo enforces this via ArchUnit rules.
  - Logback-to-OTLP bridging is handled by shared/OtelLogAppenderInstaller and logback-spring.xml — changes to logging should keep this bridge intact.
  - Custom spans in code use @Observed (see HelloService) — prefer @Observed for fine-grained spans.
  - HTTP clients must use Spring RestClient so Trace Context propagates automatically.

- Module & architecture constraints (enforced by arch-tests)
  - shared/ must not depend on service modules.
  - Controllers should not access repositories directly; follow existing layering.
  - DTOs/responses are implemented as Java records (ArchUnit enforces this).
  - Maintain no circular dependencies between modules.

- Testing & contracts
  - Contract testing: Pact consumer/provider tests exist; test runs rely on system property pact.rootDir (Gradle config sets this per test task). Avoid changing how Pact files are produced unless updating scripts/publish-pacts.sh.
  - Coverage gate: JaCoCo minimum coverage (60%) is enforced in CI; keep changes within coverage expectations or add tests to accompany code changes.
  - Arch and contract tests are part of CI; local changes that affect these tests need corresponding test updates.

Practical pointers for code edits and Copilot responses
- When suggesting code changes that affect telemetry, include required config edits (application.yaml, logback-spring.xml) and reference shared/OtelLogAppenderInstaller.
- For service runs/tests, prefer Makefile targets (make up, make verify-otel) in documentation snippets; include direct Gradle equivalents for precise test invocation.
- When proposing new dependencies that touch tracing/logging, mention expected OTLP/micrometer artifacts and gradle.properties version keys.
- If changing API shapes, ensure Pact contract tests are updated and instruct how to regenerate/publish pacts (scripts/publish-pacts.sh).

Where to look for specifics
- Quick reference: README.md (root) and CLAUDE.md — contain detailed commands and examples.
- Telemetry wiring: shared/src/main/java and shared/src/main/resources/logback-spring.xml
- Verification harness and scripts: scripts/verify-otel.sh and docs/VERIFICATION-HARNESS.md
- CI workflow: .github/workflows/ci.yml
- Grafana dashboards and provisioning: grafana/

Contact points for human review (if unsure)
- Update PR description to call out telemetry, contract, and architecture impacts.
- Add/mention tests (unit + contract + arch) when behavior changes across services.

---

Created by analyzing README.md, CLAUDE.md, and AGENTS.md. If any section should be expanded (examples, more single-test patterns, or Copilot-specific templates), say which area to extend.