# OpenTelemetry Verification Harness

## Overview

The OpenTelemetry verification harness automatically validates that telemetry data is flowing through the running topology:

`All services -> OTLP -> otel-collector -> grafana-otel-lgtm`

It treats Collector availability, OTLP endpoint alignment, metrics, traces, and required resource attributes as critical checks. Loki/log evidence remains advisory.

## Quick Start

```bash
# Basic verification
make verify-otel

# Verbose output with detailed information
make verify-otel-verbose

# Wait for Grafana and the internal Collector to be ready before verifying
make verify-otel-wait
```

`make verify-otel-wait` writes a machine-readable evidence report to `build/reports/otel/verification-report.json`.

## What It Checks

### 1. Collector Availability

Verifies the internal Compose Collector is reachable:
- **Network Path**: `grafana-otel-lgtm` can reach `http://otel-collector:13133/`
- **Collector Role**: `otel/opentelemetry-collector-contrib:0.149.0` receives OTLP from services and forwards it to LGTM

**Expected Result**: Collector health endpoint is reachable from the Compose network.

### 2. Metrics Collection (Prometheus)

Verifies that each service is exporting metrics:
- **JVM Metrics**: Memory usage, GC pauses, thread counts, class loading
- **HTTP Metrics**: Server request counts, response times, active requests
- **Custom Metrics**: Application-specific counters and timers

**Expected Result**: All three services (`hello-service`, `user-service`, `greeting-service`) should have metrics visible in LGTM with matching `service_name`, `service_namespace`, `service_version`, and `deployment_environment` labels.

### 3. Traces Collection (Tempo)

Verifies distributed tracing is working:
- **Service Spans**: Each service creates spans for incoming requests
- **Trace Context**: Trace IDs are propagated across service boundaries
- **Distributed Tracing**: Single request creates spans in multiple services

**Expected Result**: Traces should show cross-service calls (hello-service → user-service + greeting-service).

### 4. Required Resource Attributes

Verifies each service exports the resource attributes required by the running system:
- **service.name**: matches the service name
- **service.namespace**: comes from `management.opentelemetry.resource-attributes`
- **service.version**: comes from service config / env
- **deployment.environment**: comes from service config / env

**Expected Result**: Prometheus-visible series include all four attributes with the configured values.

### 5. Logs Configuration

Verifies logging evidence where available:
- **Trace Context in Logs**: Log lines contain trace ID and span ID
- **Loki Accessibility**: Grafana can query Loki for logs
- **Log Correlation**: Logs can be correlated with traces

**Note**: Application logs are currently captured in Docker stdout. Loki evidence is advisory; OTLP log ingestion is not required for overall pass/fail.

### 6. Distributed Tracing

Verifies end-to-end trace correlation:
- **Multi-service Traces**: Single trace spans multiple services
- **Service Dependency**: Correctly shows service call graph
- **Trace Completeness**: All spans in a trace are captured

## Output Format

```
========================================
  OpenTelemetry Verification Script
========================================

[PASS] Grafana is accessible
[PASS] Collector reachable from Compose network
[INFO] Generating test traffic...
[PASS] Test traffic generated
[INFO] Verifying metrics collection...
[PASS]   hello-service: 24 JVM metric series visible in LGTM
[PASS]   user-service: 24 JVM metric series visible in LGTM
[PASS]   greeting-service: 24 JVM metric series visible in LGTM
[PASS] Metrics verification: PASSED
[INFO] Verifying traces collection...
[PASS]   hello-service: sample trace 7c6ecfc3cb4f2e9f5b1bde6fe63d0ef9
[PASS] Traces verification: PASSED
[INFO] Verifying required resource attributes...
[PASS]   hello-service: required resource attributes present
[PASS]   user-service: required resource attributes present
[PASS]   greeting-service: required resource attributes present
[INFO] Verifying logs configuration...
[WARN] Logs: advisory only; OTLP log ingestion is not required for pass/fail
[INFO] Verifying distributed tracing...
[PASS] Distributed tracing: 3 services in trace (greeting-service,hello-service,user-service)
[PASS] Distributed tracing verification: PASSED

========================================
  OpenTelemetry Verification Summary
========================================

  Collector:  ✓ HEALTHY
  hello-service: ✓ metrics=24 trace=7c6ecfc3cb4f2e9f5b1bde6fe63d0ef9
  user-service: ✓ metrics=24 trace=4e915424233643efab8c82337d741abc
  greeting-service: ✓ metrics=24 trace=1e64ca41a71d44be936d2175c7b2cdef
  Logs:       ⚠ ADVISORY

  Report: build/reports/otel/verification-report.json

Overall: verification evidence generated successfully.

Access Grafana: http://localhost:3000 (admin/admin)
```

## Integration with CI/CD

### GitHub Actions Example

```yaml
- name: Verify OpenTelemetry
  run: |
    docker compose up -d
    make verify-otel-wait
    test -f build/reports/otel/verification-report.json
```

## Configuration Baseline

The harness expects service-owned OTLP and resource attribute configuration like this:

```yaml
management:
  otlp:
    tracing:
      endpoint: http://otel-collector:4318/v1/traces
    metrics:
      export:
        url: http://otel-collector:4318/v1/metrics
        step: 10s
    logging:
      endpoint: http://otel-collector:4318/v1/logs
  tracing:
    sampling:
      probability: ${OTEL_TRACING_SAMPLING_PROBABILITY:1.0}
  opentelemetry:
    resource-attributes:
      service.name: ${spring.application.name}
      service.namespace: ${OTEL_SERVICE_NAMESPACE:springboot3.5-otel}
      service.version: ${OTEL_SERVICE_VERSION:1.0.0}
      deployment.environment: ${OTEL_DEPLOYMENT_ENVIRONMENT:local}
```

The Collector stays internal to Compose; the services provide the required resource attributes in their own config.

### Local Development

Add to your development workflow:

```bash
# Full development cycle
make dev-full

# Or just verify OTel after manual testing
make verify-otel
```

## Troubleshooting

### No Metrics Found

1. Ensure services are running and exporting metrics:
   ```bash
   docker compose logs hello-service | grep "Publishing metrics"
   ```

2. Check OTLP endpoint configuration:
   ```bash
   docker compose exec hello-service env | grep OTLP
   ```

3. Verify required resource attributes:
   ```bash
   docker compose exec hello-service env | grep OTEL_
   ```

4. Verify Prometheus is scraping:
   ```bash
   make verify-otel-verbose
   ```

### No Traces Found

1. Generate traffic to create traces:
   ```bash
   curl http://localhost:8080/api/1
   ```

2. Check service connectivity:
   ```bash
   docker compose ps
   ```

3. Verify trace context propagation:
   ```bash
   docker compose logs hello-service | grep traceId
   ```

### Collector Not Reachable

1. Confirm the internal Collector service is running:
   ```bash
   docker compose ps otel-collector
   ```

2. Retry the readiness-aware verification:
   ```bash
   make verify-otel-wait
   ```

3. Inspect Collector logs:
   ```bash
   docker compose logs otel-collector
   ```

### Grafana Not Accessible

1. Wait for Grafana to start (up to 60 seconds):
   ```bash
   make verify-otel-wait
   ```

2. Check container health:
   ```bash
   docker compose ps
   ```

3. View Grafana logs:
   ```bash
   docker compose logs grafana-otel-lgtm
   ```

## Script Options

| Option | Description |
|--------|-------------|
| `--verbose` | Show detailed debug output |
| `--wait` | Wait for Grafana and the internal `otel-collector` to be ready (max 60s) |

## Exit Codes

| Code | Meaning |
|------|---------|
| 0 | All critical checks passed (collector + config + metrics + traces + resource attributes) |
| 1 | One or more critical checks failed |

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                   verify-otel.sh                            │
├─────────────────────────────────────────────────────────────┤
│  1. Check Grafana and Collector availability                │
│  2. Generate test traffic                                   │
│  3. Verify Metrics (Prometheus via Docker API)              │
│  4. Verify Traces (Tempo via Docker API)                    │
│  5. Verify required resource attributes                     │
│  6. Verify Logs (advisory Loki + Docker logs)               │
│  7. Verify Distributed Tracing (cross-service traces)       │
│  8. Generate JSON summary report                            │
└─────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌────────────────────┐    OTLP    ┌────────────────────┐    OTLP    ┌─────────────────────────────┐
│   Spring services  │ ─────────► │   otel-collector   │ ─────────► │     grafana-otel-lgtm      │
│ hello/user/greeting│            │ Compose internal    │            │ Prometheus / Tempo / Loki  │
└────────────────────┘            └────────────────────┘            └─────────────────────────────┘
```

## Related Documentation

- [Main README](../README.md) - User guide and quick start
- [QWEN.md](../QWEN.md) - Development context
- [Architecture](./ARCHITECTURE.md) - System architecture

## Future Enhancements

- [ ] Add log content verification in Loki
- [ ] Add alerting rule verification
- [ ] Add dashboard provisioning verification
- [ ] Add performance benchmarking
- [ ] Add trace quality metrics (completeness, latency accuracy)
