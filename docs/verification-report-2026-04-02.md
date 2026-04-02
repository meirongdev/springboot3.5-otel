# Verification Report: Spring Boot 3.5 + Java 25 OTel Demo

## 1. Overview
**Date:** Thursday, 2 April 2026  
**Status:** ✅ All services started, functional tests passed, and observability backend is accessible.

This report archives the verification of the microservices ecosystem, focusing on service interconnection and OpenTelemetry integration.

## 2. Environment Verification

| Component | Status | Version / Detail |
|-----------|--------|-------------------|
| Java | ✅ | OpenJDK 25.0.2 (Temurin) |
| Docker | ✅ | Docker Engine Running |
| Gradle | ✅ | 9.4.1 (with configuration cache) |
| OS | ✅ | Darwin (macOS) |

## 3. Infrastructure (Observability Backend)

The `grafana-otel-lgtm` container was started via `docker compose up -d`.

- **Grafana UI:** [http://localhost:3000](http://localhost:3000) (Accessible)
- **OTLP Endpoints:** 
  - gRPC: `localhost:4317`
  - HTTP: `http://localhost:4318/v1/traces`

### Healthcheck Configuration
```yaml
healthcheck:
  test: ["CMD", "curl", "-f", "-s", "http://localhost:3000/api/health"]
  interval: 10s
  timeout: 5s
  retries: 3
  start_period: 60s
```

## 4. Service Health Check

All three microservices were started via Docker Compose.

| Service | Port | Health Endpoint | Status |
|---------|------|-----------------|--------|
| `hello-service` | 8080 | [http://localhost:8080/actuator/health](http://localhost:8080/actuator/health) | `UP` |
| `user-service` | 8081 | [http://localhost:8081/actuator/health](http://localhost:8081/actuator/health) | `UP` |
| `greeting-service` | 8082 | [http://localhost:8082/actuator/health](http://localhost:8082/actuator/health) | `UP` |

## 5. Functional Testing (Distributed Flow)

The following `curl` requests were used to verify the orchestration logic (`hello-service` calling `user-service` and `greeting-service`).

### 5.1 English Greeting (Default)
**Command:** `curl -s http://localhost:8080/api/1`  
**Result:**
```json
{"userId":1,"userName":"Alice","greeting":"Hello, World!","language":"en"}
```

### 5.2 Chinese Greeting
**Command:** `curl -s -H "Accept-Language: zh" http://localhost:8080/api/1`  
**Result:**
```json
{"userId":1,"userName":"Alice","greeting":"你好，世界！","language":"zh"}
```

### 5.3 Japanese Greeting
**Command:** `curl -s -H "Accept-Language: ja" http://localhost:8080/api/1`  
**Result:**
```json
{"userId":1,"userName":"Alice","greeting":"こんにちは世界！","language":"ja"}
```

## 6. Observability Verification (OTel)

- **Trace Correlation:** ✅ Verified that `hello-service` logs include Trace IDs and Span IDs
- **Log Correlation:** ✅ Log patterns follow `%5p [${spring.application.name:},%X{traceId:-},%X{spanId:-}]`
- **Grafana Tempo:** ✅ Distributed traces visible in Tempo explorer, showing request path from `hello-service` to downstream services
- **Grafana Loki:** ✅ Logs correlated with Trace IDs
- **Grafana Prometheus:** ✅ JVM metrics (CPU, memory, threads) collected

## 7. Docker Build Verification

### Build Issues Fixed
1. **arch-tests module missing** - Removed from `.dockerignore`
2. **Other service modules missing** - All Dockerfiles now copy all module directories
3. **Grafana healthcheck failure** - Changed from `wget` to `curl`

### Files Modified
- `.dockerignore` - Removed `arch-tests/` exclusion
- `hello-service/Dockerfile` - Added all module copies
- `user-service/Dockerfile` - Added all module copies
- `greeting-service/Dockerfile` - Added all module copies
- `compose.yaml` - Fixed healthcheck to use `curl`

See: [docker-fixes.md](docker-fixes.md) for detailed troubleshooting.

## 8. Conclusion
The system is fully operational. The Spring Boot 3.5 adaptation for OpenTelemetry is working as intended, providing distributed tracing and health monitoring across the Java 25 based microservices.

All Docker containers build and run successfully with proper health checks.
