Repository Guidelines
================================

# Project Structure & Module Organization

This is a **Spring Boot 3.5 + Java 25 microservices demo** showcasing OpenTelemetry observability (traces, metrics, logs).

**Root directories:**
- `greeting-service/`, `hello-service/`, `user-service/` - Independent Spring Boot microservices
- `shared/` - Shared utilities (HTTP client, language normalization, OpenTelemetry config)
- `arch-tests/` - ArchUnit architecture validation tests
- `gradle/` - Gradle wrapper and configuration
- `grafana/` - Grafana OTEL LGTM backend configuration
- `docs/` - Project documentation

**Source layout** (per service):
```
src/main/java/com/example/<service>/
   ├── *.java - Spring Boot controllers, services, configurations
src/test/java/com/example/<service>/
   ├── *Test.java - JUnit 5 unit, integration, contract tests
```

---

# Build, Test, and Development Commands

| Command | Description |
|---------|-------------|
| `./gradlew build` | Build all services |
| `./gradlew test` | Run all tests |
| `./gradlew :<service>:bootRun` | Run a specific service |
| `./gradlew spotlessCheck` | Validate code formatting |
| `./gradlew spotlessApply` | Auto-format code |
| `./gradlew testCodeCoverageReport` | Generate JaCoCo coverage report |

**Make shortcuts:**
- `make build` - Build all services
- `make test` - Run all tests
- `make fmt` - Auto-format code
- `make check` - Validate formatting
- `make up` - Start Grafana OTEL backend
- `make run-all` - Start all services

---

# Coding Style & Naming Conventions

**Java:**
- **Indentation:** 2-space soft tabs
- **Formatting:** Google Java Format 1.28.0 (via Spotless)
- **Naming:** PascalCase for types (`GreetingController`), camelCase for members (`acceptLanguage`), uppercase CONSTANTS

**Gradle/Kotlin:**
- 4-space indentation
- File names: `build.gradle.kts`, `settings.gradle.kts`

**Linting:** Error Prone (`com.google.errorprone:error_prone_core`) runs during compile

---

# Testing Guidelines

**Frameworks:** JUnit 5 (Jupiter), AssertJ, Testcontainers, Pact, ArchUnit

**Test types:**
- `*Test.java` - Unit tests
- `*IntegrationTest.java` - Integration tests with Testcontainers
- `*PactConsumerTest.java` / `*PactProviderTest.java` - Contract tests

**Run specific tests:**
```bash
./gradlew :hello-service:test --tests HelloControllerTest
./gradlew test --tests "*Pact*"          # Contract tests
./gradlew :arch-tests:test              # Architecture tests
```

---

# Commit & Pull Request Guidelines

**Commit messages:** Follow conventional commits pattern:
- `feat: implement quality harness (H1-H5)`
- `chore: init repo`
- `fix: resolve null pointer exception`

**PRs:**
- Linked issue/PR number in description
- Brief description of changes
- Screenshots for UI changes (if applicable)

---

# Agent-Specific Instructions

- Use `./gradlew` for all build/test operations
- Services run on ports 8080 (hello), 8081 (user), 8082 (greeting)
- Grafana OTEL backend: [localhost:3000](http://localhost:3000) (admin/admin)
- Architecture tests validate modular boundaries - do not bypass

---

*Generated for repository: springboot3.5-otel*
