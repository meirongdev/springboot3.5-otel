Repository Guidelines
================================

**Last Updated:** April 2, 2026

---

# Project Structure & Module Organization

**Spring Boot 3.5 + Java 25 microservices demo** with OpenTelemetry observability (traces, metrics, logs).

**Root modules:**
- `greeting-service/` - Multi-language greetings (port 8082)
- `hello-service/` - Orchestrator, calls downstream services (port 8080)
- `user-service/` - User data with H2 + Flyway (port 8081)
- `shared/` - Common utilities, HTTP clients, OpenTelemetry config
- `arch-tests/` - ArchUnit architecture validation tests

**Key directories:**
```
docs/           - Architecture, verification harness, Docker fixes
grafana/        - Dashboards and provisioning for OTEL LGTM
scripts/        - verify-otel.sh, publish-stubs.sh utilities
.github/        - CI workflow (ci.yml)
```

---

# Build, Test, and Development Commands

**Gradle commands:**
- `./gradlew build` - Build all services
- `./gradlew test` - Run all tests (unit, integration, contract)
- `./gradlew :<service>:bootRun` - Run a specific service
- `./gradlew spotlessCheck` - Validate formatting
- `./gradlew spotlessApply` - Auto-format code
- `./gradlew testCodeCoverageReport` - Generate JaCoCo coverage

**Make shortcuts:**
- `make build` / `make test` / `make clean` / `make coverage`
- `make fmt` - Apply spotless formatting
- `make check` - Validate formatting + static analysis
- `make validate-fast` - Quick validation: format + compile (~15s)
- `make validate` - Full validation: format + compile + all tests (~2-3 min)
- `make up` / `make down` / `make restart` - Docker Compose backend
- `make run-all` - Start all services in background
- `make hooks-install` - Install pre-commit git hook
- `make hooks-remove` - Remove pre-commit git hook
- `make verify-otel` - Run OTel data collection verification

**Git Hooks:**
- `make hooks-install` installs `.githooks/pre-commit` → `.git/hooks/pre-commit`
- Pre-commit runs: spotlessCheck + compile + all tests + docker config validation

---

# Coding Style & Naming Conventions

**Java:**
- **Indentation:** 2-space soft tabs (`.editorconfig`)
- **Formatting:** Google Java Format 1.28.0 via Spotless
- **Naming:** PascalCase for types (`GreetingController`), camelCase for members (`acceptLanguage`)

**Kotlin (build scripts):**
- 4-space indentation for `build.gradle.kts` files

**Static Analysis:**
- Error Prone (`com.google.errorprone:error_prone_core`) runs at compile time

---

# Testing Guidelines

**Frameworks:** JUnit 5, AssertJ, Testcontainers, Spring Cloud Contract, ArchUnit

**Test organization per module:**
- `*Test.java` - Unit tests
- `*IntegrationTest.java` - Integration tests with Testcontainers
- `*ContractTest.java` - Contract tests

**Run tests:**
```bash
./gradlew :hello-service:test --tests HelloControllerTest
./gradlew test --tests "*Contract*"         # Contract tests
./gradlew :arch-tests:test              # ArchUnit tests
./gradlew testCodeCoverageReport        # Coverage report
```

**Coverage threshold:** 60% minimum via JaCoCo

---

# Commit & Pull Request Guidelines

**Commit messages:** Follow conventional commits:
- `feat: implement quality harness (H1-H5)`
- `chore: init repo`
- `fix: resolve null pointer exception`

**PR requirements:**
- Link related issues in description
- Brief summary of changes
- Screenshots for UI/API changes (if applicable)

---

# Agent-Specific Instructions

**Common workflows:**
```bash
# Start everything
make up
./gradlew :greeting-service:bootRun  # Terminal 1
./gradlew :user-service:bootRun      # Terminal 2
./gradlew :hello-service:bootRun     # Terminal 3

# Verify OpenTelemetry
make verify-otel
make verify-otel-verbose

# Full quality check
make dev-full

# Quick validation (format + compile only)
make validate-fast

# Full validation (all tests included)
make validate
```

**Service ports:** hello:8080, user:8081, greeting:8082
**Grafana UI:** http://localhost:3000 (admin/admin)
**OTLP endpoint:** gRPC :4317 / HTTP :4318

---

# Documentation

- [README.md](../README.md) - Main project guide
- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) - System architecture
- [docs/VERIFICATION-HARNESS.md](docs/VERIFICATION-HARNESS.md) - OTel verification
- [docs/VERIFICATION-HARNESS-IMPLEMENTATION.md](docs/VERIFICATION-HARNESS-IMPLEMENTATION.md) - Implementation details

---

*Generated for repository: springboot3.5-otel*
