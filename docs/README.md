# Documentation Index

**Last Updated:** April 2, 2026

---

## 📚 Core Documentation

| Document | Purpose | Status |
|----------|---------|--------|
| **[ARCHITECTURE.md](ARCHITECTURE.md)** | **Main reference** - Architecture, implementation, test harness, CI/CD | ✅ Complete |
| **[LOGGING-GUIDE.md](LOGGING-GUIDE.md)** | **Logging configuration** - Current setup, 2026 best practices assessment | ✅ Complete |
| **[LOGGING-PRODUCTION.md](LOGGING-PRODUCTION.md)** | **Production logging** - Deployment considerations, compliance, optimization | ✅ Complete |
| **[docker-fixes.md](docker-fixes.md)** | Docker build & runtime troubleshooting | ✅ Reference |
| **[verification-report-2026-04-02.md](verification-report-2026-04-02.md)** | Verification test report (April 2, 2026) | 📋 Archive |

---

## 🚀 Quick Navigation

### For New Developers
1. Start with **[ARCHITECTURE.md](ARCHITECTURE.md)** - System overview and quick start
2. Read **[../README.md](../README.md)** - User-facing guide
3. Reference **[../QWEN.md](../QWEN.md)** - Developer context

### For Development
- **Build & Test:** `[ARCHITECTURE.md](ARCHITECTURE.md)#6-cicd`
- **Validation Pipeline:** `[../README.md](../README.md)#验证流水线`
- **Git Hooks:** `[../README.md](../README.md)#git-hooks`
- **Docker Issues:** `[docker-fixes.md](docker-fixes.md)`
- **Architecture Rules:** `[ARCHITECTURE.md](ARCHITECTURE.md)#5-test-harness`
- **Logging Configuration:** `[LOGGING-GUIDE.md](LOGGING-GUIDE.md)`

### For Operations
- **Start Services:** `[ARCHITECTURE.md](ARCHITECTURE.md)#quick-start`
- **Healthcheck Config:** `[ARCHITECTURE.md](ARCHITECTURE.md)#42-docker-compose-healthcheck`
- **Grafana Access:** http://localhost:3000 (admin/admin)
- **Production Logging:** `[LOGGING-PRODUCTION.md](LOGGING-PRODUCTION.md)`

---

## 📁 Document Structure

```
docs/
├── ARCHITECTURE.md                  # Main reference (consolidated)
├── LOGGING-GUIDE.md                 # Logging configuration & best practices
├── LOGGING-PRODUCTION.md            # Production logging considerations
├── docker-fixes.md                  # Docker troubleshooting
├── verification-report-*.md         # Verification reports (archive)
└── README.md                        # This index
```

---

## 🔍 Key Topics

### Architecture
- Microservices: hello-service, user-service, greeting-service
- Observability: Grafana OTEL LGTM (Tempo, Loki, Prometheus)
- See: [ARCHITECTURE.md](ARCHITECTURE.md)#1-system-architecture

### Technology Stack
- Java 25, Spring Boot 3.5, Gradle 9.4.1
- OpenTelemetry, Micrometer Tracing
- See: [ARCHITECTURE.md](ARCHITECTURE.md)#2-technology-stack

### Testing
- Unit, Integration, Contract (Spring Cloud Contract), Architecture (ArchUnit)
- 60% coverage minimum
- See: [ARCHITECTURE.md](ARCHITECTURE.md)#5-test-harness

### CI/CD
- GitHub Actions with Gradle caching
- Spotless, Error Prone, JaCoCo
- See: [ARCHITECTURE.md](ARCHITECTURE.md)#6-cicd

### Docker
- Multi-module builds
- Healthcheck configuration
- Troubleshooting: [docker-fixes.md](docker-fixes.md)

### Logging
- Current configuration: [LOGGING-GUIDE.md](LOGGING-GUIDE.md)
- Production deployment: [LOGGING-PRODUCTION.md](LOGGING-PRODUCTION.md)
- 2026 best practices assessment
- OTLP log export to Grafana Loki

---

## 📊 Document History

| Date | Change |
|------|--------|
| 2026-04-06 | Added logging documentation (LOGGING-GUIDE.md, LOGGING-PRODUCTION.md) |
| 2026-04-02 | Consolidated 7 docs into 3 (ARCHITECTURE.md, docker-fixes.md, index) |
| 2026-04-02 | Fixed Docker build and healthcheck issues |
| 2026-04-02 | Verification report published |

---

## 🔗 External Links

- **Reference Project:** [spring-boot-and-opentelemetry](https://github.com/mhalbritter/spring-boot-and-opentelemetry)
- **Grafana OTEL LGTM:** [GitHub Repository](https://github.com/grafana/otel-lgtm)
- **Spring Boot 3.5 Docs:** [Spring.io](https://spring.io/projects/spring-boot)
