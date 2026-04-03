# OpenTelemetry Verification Harness

## Overview

The OpenTelemetry verification harness automatically validates that telemetry data (traces, metrics, and logs) is being properly collected from all Spring Boot services.

## Quick Start

```bash
# Basic verification
make verify-otel

# Verbose output with detailed information
make verify-otel-verbose

# Wait for Grafana to be ready before verifying
make verify-otel-wait
```

## What It Checks

### 1. Metrics Collection (Prometheus)

Verifies that each service is exporting metrics:
- **JVM Metrics**: Memory usage, GC pauses, thread counts, class loading
- **HTTP Metrics**: Server request counts, response times, active requests
- **Custom Metrics**: Application-specific counters and timers

**Expected Result**: All three services (`hello-service`, `user-service`, `greeting-service`) should have metrics with `job` label matching service name.

### 2. Traces Collection (Tempo)

Verifies distributed tracing is working:
- **Service Spans**: Each service creates spans for incoming requests
- **Trace Context**: Trace IDs are propagated across service boundaries
- **Distributed Tracing**: Single request creates spans in multiple services

**Expected Result**: Traces should show cross-service calls (hello-service → user-service + greeting-service).

### 3. Logs Configuration

Verifies logging is properly configured:
- **Trace Context in Logs**: Log lines contain trace ID and span ID
- **Loki Accessibility**: Grafana can query Loki for logs
- **Log Correlation**: Logs can be correlated with traces

**Note**: Application logs are currently captured in Docker stdout. Loki integration for OTLP log export is optional.

### 4. Distributed Tracing

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
[INFO] Generating test traffic...
[PASS] Test traffic generated
[INFO] Verifying metrics collection...
[PASS]   hello-service: JVM metrics collected (24 series)
[PASS]   user-service: JVM metrics collected (24 series)
[PASS]   greeting-service: JVM metrics collected (24 series)
[PASS] Metrics verification: PASSED
[INFO] Verifying traces collection...
[PASS]   hello-service: 5 trace(s) found
[PASS] Traces verification: PASSED
[INFO] Verifying logs configuration...
[PASS] Logs contain trace/span IDs (context propagation enabled)
[PASS] Logs verification: PASSED
[INFO] Verifying distributed tracing...
[PASS] Distributed tracing: 3 services in trace (greeting-service,hello-service,user-service)
[PASS] Distributed tracing verification: PASSED

========================================
  OpenTelemetry Verification Summary
========================================

  Metrics:    ✓ PASSED
  Traces:     ✓ PASSED
  Logs:       ✓ PASSED
  Distributed:✓ PASSED

Overall: OpenTelemetry is working correctly!

Access Grafana: http://localhost:3000 (admin/admin)
```

## Integration with CI/CD

### GitHub Actions Example

```yaml
- name: Verify OpenTelemetry
  run: |
    docker compose up -d
    make verify-otel-wait
```

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

3. Verify Prometheus is scraping:
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
| `--wait` | Wait for Grafana to be ready (max 60s) |

## Exit Codes

| Code | Meaning |
|------|---------|
| 0 | All critical checks passed (metrics + traces) |
| 1 | One or more critical checks failed |

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                   verify-otel.sh                            │
├─────────────────────────────────────────────────────────────┤
│  1. Check Grafana availability                              │
│  2. Generate test traffic                                   │
│  3. Verify Metrics (Prometheus via Docker API)              │
│  4. Verify Traces (Tempo via Docker API)                    │
│  5. Verify Logs (Loki + Docker logs)                        │
│  6. Verify Distributed Tracing (cross-service traces)       │
│  7. Generate summary report                                 │
└─────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│              Grafana OTEL LGTM Container                    │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐         │
│  │  Prometheus │  │    Tempo    │  │    Loki     │         │
│  │  (Metrics)  │  │  (Traces)   │  │   (Logs)    │         │
│  └─────────────┘  └─────────────┘  └─────────────┘         │
│                                                             │
│  ┌─────────────────────────────────────────────────────┐   │
│  │        OpenTelemetry Collector (OTLP)               │   │
│  └─────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
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
