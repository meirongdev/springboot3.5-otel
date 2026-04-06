# Logging Production Considerations

**Last Updated:** April 6, 2026  
**Status:** ⚠️ For production deployment planning

---

## 1. Overview

This document outlines production-specific logging considerations for the Spring Boot 3.5 + OpenTelemetry microservices. The current demo configuration prioritizes **debugging visibility** over **production efficiency**. This guide details what needs to change before production deployment.

---

## 2. Log Level Strategy

### 2.1 Current vs Production

| Environment | `com.example` | Framework | Purpose |
|-------------|---------------|-----------|---------|
| **Local/Demo** | `debug` | Default | Full visibility for development |
| **Staging** | `info` | `warn` | Realistic production simulation |
| **Production** | `info` | `warn` | Cost-optimized, noise-reduced |

### 2.2 Environment-Specific Configuration

```yaml
# application-prod.yaml
logging:
  level:
    com.example: info
    org.springframework.web: warn
    org.springframework.security: info  # Keep security logs visible
    org.hibernate: warn
    io.opentelemetry: warn
```

### 2.3 Dynamic Log Level Management

**Enable Actuator Loggers Endpoint:**

```yaml
management:
  endpoint:
    loggers:
      enabled: true
  endpoints:
    web:
      exposure:
        include: health,info,loggers,metrics
```

**Runtime Adjustment:**

```bash
# Check current log level
curl http://localhost:8080/actuator/loggers/com.example.hello

# Increase verbosity temporarily (debugging production issue)
curl -X POST http://localhost:8080/actuator/loggers/com.example.hello \
  -H "Content-Type: application/json" \
  -d '{"configuredLevel":"DEBUG"}'

# Revert to default
curl -X POST http://localhost:8080/actuator/loggers/com.example.hello \
  -H "Content-Type: application/json" \
  -d '{"configuredLevel":null}'
```

**Best Practice:** Use **Spring Cloud Config** or **externalized configuration** for centralized log level management across all services.

---

## 3. Sampling Strategy

### 3.1 Current Configuration

```yaml
management:
  tracing:
    sampling:
      probability: ${OTEL_TRACING_SAMPLING_PROBABILITY:1.0}  # 100% sampling
```

**Problem:** 100% sampling generates massive telemetry volume in production.

### 3.2 Production Recommendation

```yaml
management:
  tracing:
    sampling:
      probability: ${OTEL_TRACING_SAMPLING_PROBABILITY:0.1}  # 10% default
      # Consider parent-based sampling for distributed traces
```

### 3.3 Sampling Strategies Comparison

| Strategy | Description | Use Case | Cost |
|----------|-------------|----------|------|
| **Probability (Current)** | Random sampling per span | Simple deployments | Medium |
| **Parent-Based** | Inherit parent's sampling decision | Distributed traces | Low |
| **Rate-Limiting** | Fixed spans per second | High-traffic services | Predictable |
| **Tail-Based** | Sample based on outcome (errors, latency) | Error investigation | High (requires collector) |

**2026 Recommendation:** Use **parent-based sampling** for microservices to maintain trace consistency:

```yaml
management:
  tracing:
    sampling:
      probability: 0.1
      # Future: Consider OpenTelemetry Collector tail-sampling processor
```

---

## 4. Performance Optimization

### 4.1 Async Log Appender

**Current:** Synchronous `OpenTelemetryAppender` (blocking)

**Production:** Wrap in async appender:

```xml
<appender name="OpenTelemetry"
          class="io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender">
  <captureExperimentalAttributes>true</captureExperimentalAttributes>
  <captureKeyValuePairAttributes>true</captureKeyValuePairAttributes>
  <captureCodeAttributes>true</captureCodeAttributes>
  <captureMarkerAttribute>true</captureMarkerAttribute>
  <captureLoggerContext>true</captureLoggerContext>
</appender>

<!-- Async wrapper for non-blocking log writes -->
<appender name="AsyncOpenTelemetry" class="ch.qos.logback.classic.AsyncAppender">
  <appender-ref ref="OpenTelemetry"/>
  <queueSize>8192</queueSize>
  <discardingThreshold>0</discardingThreshold>
  <maxFlushTime>1000</maxFlushTime>
  <includeCallerData>false</includeCallerData>
</appender>

<root level="INFO">
  <appender-ref ref="CONSOLE"/>
  <appender-ref ref="AsyncOpenTelemetry"/>
</root>
```

#### Async Appender Parameters

| Parameter | Value | Rationale |
|-----------|-------|-----------|
| `queueSize` | 8192 | Handle log bursts without dropping |
| `discardingThreshold` | 0 | Never discard logs (compliance requirement) |
| `maxFlushTime` | 1000ms | Graceful shutdown timeout |
| `includeCallerData` | false | Performance optimization (code attributes already captured) |

### 4.2 Batch Export Configuration

**OpenTelemetry SDK defaults are usually sufficient**, but tune if needed:

```yaml
management:
  opentelemetry:
    # OTLP exporter tuning (optional)
    resource-attributes:
      service.name: ${spring.application.name}
      deployment.environment: ${OTEL_DEPLOYMENT_ENVIRONMENT:production}
```

**For advanced tuning**, configure via environment variables:

```bash
# Batch span processor (traces)
OTEL_BSP_MAX_QUEUE_SIZE=4096
OTEL_BSP_MAX_EXPORT_BATCH_SIZE=512

# Batch log processor (logs) - if supported by SDK version
OTEL_BLRP_MAX_QUEUE_SIZE=4096
OTEL_BLRP_MAX_EXPORT_BATCH_SIZE=512
```

---

## 5. Security & Compliance

### 5.1 PII Redaction

**Problem:** Logs may contain sensitive data (emails, phone numbers, tokens).

**Solution:** Implement custom Logback filter:

```java
package com.example.shared.otel;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.filter.Filter;
import ch.qos.logback.core.spi.FilterReply;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Redacts sensitive data from log messages before export.
 */
public class PiiRedactionFilter extends Filter<ILoggingEvent> {

  private static final Pattern EMAIL_PATTERN = 
      Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}");
  private static final Pattern PHONE_PATTERN = 
      Pattern.compile("\\b\\d{3}[-.]?\\d{3}[-.]?\\d{4}\\b");
  private static final Pattern TOKEN_PATTERN = 
      Pattern.compile("(Bearer|token)[=:\\s]+([a-zA-Z0-9._-]+)");

  @Override
  public FilterReply decide(ILoggingEvent event) {
    // Redaction happens at the message level before export
    return FilterReply.NEUTRAL;
  }

  public static String redact(String message) {
    if (message == null) return null;
    
    String redacted = message;
    redacted = EMAIL_PATTERN.matcher(redacted).replaceAll("***@***.***");
    redacted = PHONE_PATTERN.matcher(redacted).replaceAll("***-***-****");
    redacted = TOKEN_PATTERN.matcher(redacted).replaceAll("$1=***REDACTED***");
    
    return redacted;
  }
}
```

**Register in logback-spring.xml:**

```xml
<appender name="OpenTelemetry"
          class="io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender">
  <filter class="com.example.shared.otel.PiiRedactionFilter"/>
  <!-- ... other config ... -->
</appender>
```

### 5.2 Audit Logging

**Use SLF4J Markers for audit trails:**

```java
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

private static final Marker AUDIT = MarkerFactory.getMarker("AUDIT");
private static final Marker SECURITY = MarkerFactory.getMarker("SECURITY");

// Usage
log.info(AUDIT, "User login userId={} ip={}", userId, ipAddress);
log.warn(SECURITY, "Failed login attempt userId={} ip={}", userId, ipAddress);
```

**With `captureMarkerAttribute=true`**, these markers are exported to Loki for filtering:

```logql
# Query audit logs in Grafana
{service_name="hello-service"} | marker="AUDIT"
```

### 5.3 GDPR Compliance Checklist

- [ ] PII redaction filter enabled
- [ ] Log retention policy configured (Loki)
- [ ] Right to erasure: logs containing user data deletable
- [ ] Access logging for all personal data operations
- [ ] No raw tokens, passwords, or payment data in logs

---

## 6. Log Retention & Storage

### 6.1 Loki Retention Configuration

**Docker Compose (demo):**

```yaml
# compose.yaml
services:
  lgtm:
    image: grafana/otel-lgtm:latest
    environment:
      - Loki retention settings
      # Note: All-in-one LGTM has limited retention config
```

**Production Loki:**

```yaml
# Production Loki configuration
auth_enabled: false

server:
  http_listen_port: 3100

common:
  path_prefix: /loki
  storage:
    filesystem:
      chunks_directory: /loki/chunks
      rules_directory: /loki/rules
  replication_factor: 1

schema_config:
  configs:
    - from: 2024-01-01
      store: tsdb
      object_store: filesystem
      schema: v13
      index:
        prefix: index_
        period: 24h

limits_config:
  retention_period: 720h  # 30 days
  max_query_series: 500

compactor:
  working_directory: /loki/compactor
  compaction_interval: 10m
  retention_enabled: true
  retention_delete_delay: 2h
  retention_delete_worker_count: 150
```

### 6.2 Storage Estimation

**Formula:**
```
Daily Log Volume = (logs/sec) × (avg log size) × 86400 sec × services
```

**Example:**
```
100 logs/sec × 500 bytes × 86400 × 3 services = 12.96 GB/day
30-day retention = 388.8 GB
```

**Cost Optimization:**
- Reduce log level to `info` or `warn`
- Implement sampling (10% default)
- Use Loki's `tsdb` schema (50% storage reduction vs `boltdb-shipper`)
- Compress old logs (Loki 2.9+ supports compression)

---

## 7. Monitoring & Alerting

### 7.1 Log Volume Metrics

**Grafana Dashboard Panels:**

1. **Log Rate by Service**
   ```logql
   sum by (service_name) (rate({job=~".+"} [1m]))
   ```

2. **Error Log Rate**
   ```logql
   sum by (service_name) (rate({job=~".+"} |= "ERROR" [1m]))
   ```

3. **Log Volume (bytes)**
   ```logql
   sum by (service_name) (rate({job=~".+"} [5m])) * 1024
   ```

### 7.2 Alerting Rules

**Grafana Alert Examples:**

| Alert | Condition | Threshold | Action |
|-------|-----------|-----------|--------|
| High Error Rate | Error logs/min | > 100 | PagerDuty |
| Log Volume Spike | Log volume vs baseline | > 3x normal | Slack notification |
| Missing Logs | Service log rate | = 0 for 5min | PagerDuty (service down) |
| PII Detected | Regex match patterns | > 0 | Immediate investigation |

---

## 8. Deployment Checklist

### 8.1 Pre-Deployment

- [ ] Change log levels to `info` for application code
- [ ] Set `OTEL_TRACING_SAMPLING_PROBABILITY=0.1` (or lower)
- [ ] Enable async log appender (`AsyncAppender`)
- [ ] Add PII redaction filter
- [ ] Configure Loki retention policy (30 days minimum)
- [ ] Test `/actuator/loggers` endpoint access (restrict to admin network)
- [ ] Verify all services export logs to correct Loki instance

### 8.2 Post-Deployment

- [ ] Monitor log volume for first 24 hours
- [ ] Verify trace correlation (traceId/spanId in logs)
- [ ] Test dynamic log level adjustment (debug production issue)
- [ ] Confirm alerting rules fire correctly (test with synthetic errors)
- [ ] Review Loki storage usage vs estimates

### 8.3 Ongoing Maintenance

- [ ] Weekly: Review log volume trends
- [ ] Monthly: Audit PII redaction effectiveness
- [ ] Quarterly: Review retention policy vs compliance requirements
- [ ] After incidents: Temporarily increase log level, then revert

---

## 9. Migration Plan (Demo → Production)

### Phase 1: Immediate (Low Risk)

```diff
# logback-spring.xml
<appender name="OpenTelemetry"
          class="io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender">
  <captureExperimentalAttributes>true</captureExperimentalAttributes>
  <captureKeyValuePairAttributes>true</captureKeyValuePairAttributes>
+ <captureCodeAttributes>true</captureCodeAttributes>
+ <captureMarkerAttribute>true</captureMarkerAttribute>
+ <captureLoggerContext>true</captureLoggerContext>
</appender>
```

### Phase 2: Pre-Production (Medium Risk)

```diff
# application-prod.yaml
logging:
  level:
-   com.example: debug
+   com.example: info

management:
  tracing:
    sampling:
-     probability: 1.0
+     probability: 0.1
```

### Phase 3: Production (Requires Testing)

```diff
# logback-spring.xml
<root level="INFO">
  <appender-ref ref="CONSOLE"/>
- <appender-ref ref="OpenTelemetry"/>
+ <appender-ref ref="AsyncOpenTelemetry"/>
</root>
```

---

## 10. Troubleshooting Production Issues

### 10.1 Diagnosing Log Loss

**Symptom:** Logs stopped appearing in Loki

**Diagnostic Steps:**

```bash
# 1. Check if OTel appender is installed
kubectl logs <pod> | grep "OpenTelemetry Logback appender installed"

# 2. Verify OTLP endpoint connectivity
curl -v http://<loki-endpoint>:4318/v1/logs

# 3. Check appender queue saturation
# (If using async appender, monitor queue size via JMX or metrics)

# 4. Temporarily increase log level
curl -X POST http://localhost:8080/actuator/loggers/com.example \
  -H "Content-Type: application/json" \
  -d '{"configuredLevel":"DEBUG"}'
```

### 10.2 High Latency from Logging

**Symptom:** Increased request latency after enabling OTel logs

**Causes & Fixes:**

| Cause | Fix |
|-------|-----|
| Synchronous appender blocking | Switch to `AsyncAppender` |
| OTLP endpoint slow | Check Loki/Collector health |
| Batch export too small | Increase `OTEL_BLRP_MAX_EXPORT_BATCH_SIZE` |
| Network saturation | Use gRPC instead of HTTP (port 4317 vs 4318) |

### 10.3 Missing Trace Correlation

**Symptom:** Logs in Loki lack `traceId` or `spanId`

**Causes:**

1. Virtual threads not propagating context
2. Micrometer Tracing not configured
3. Log pattern not reading MDC

**Fix:**

```yaml
# Verify context propagation
spring:
  threads:
    virtual:
      enabled: true

# Verify observation
management:
  observations:
    annotations:
      enabled: true
```

---

## 11. Related Documentation

| Document | Purpose |
|----------|---------|
| [LOGGING-GUIDE.md](LOGGING-GUIDE.md) | Current logging configuration and 2026 best practices |
| [ARCHITECTURE.md](ARCHITECTURE.md) | System architecture overview |
| [JFR-OBSERVABILITY-GUIDE.md](JFR-OBSERVABILITY-GUIDE.md) | JFR + OTel integration |
| [VERIFICATION-HARNESS.md](VERIFICATION-HARNESS.md) | OTel verification harness |

---

## 12. Change History

| Date | Change |
|------|--------|
| 2026-04-06 | Initial production considerations document created |
| 2026-04-06 | Documented sampling strategy, async appender, PII redaction |
| 2026-04-06 | Added migration plan (demo → production) |
