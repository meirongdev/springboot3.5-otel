# Logging Configuration Guide

**Last Updated:** April 6, 2026  
**Status:** ✅ Current setup reviewed and production considerations documented

---

## 1. Current Logging Architecture

### 1.1 Overview

This project uses a **dual-output logging strategy**:

```
Application Logs
       ↓
   Logback (via logback-spring.xml)
       ↓
   ┌─────────────┴─────────────┐
   ↓                           ↓
CONSOLE Appender        OpenTelemetry Appender
(Human-readable)         (OTLP → Loki via gRPC/HTTP)
```

### 1.2 Key Components

| Component | File | Purpose |
|-----------|------|---------|
| **Logback Configuration** | `shared/src/main/resources/logback-spring.xml` | Declares console + OTel appenders |
| **OTel Bridge** | `shared/.../otel/OtelLogAppenderInstaller.java` | Connects Spring-managed `OpenTelemetry` bean to Logback appender |
| **OTel Config** | `shared/src/main/resources/application-otel.yaml` | OTLP endpoints, tracing, baggage settings |

### 1.3 Why Manual Bridge Is Required

**Critical:** Spring Boot 3.5 does **NOT** auto-install the OTel Logback appender.

```java
// OtelLogAppenderInstaller.java - REQUIRED for OTLP log export
@Component
public class OtelLogAppenderInstaller {
  @Autowired(required = false)
  private OpenTelemetry openTelemetry;

  @PostConstruct
  void install() {
    if (openTelemetry != null) {
      OpenTelemetryAppender.install(openTelemetry);
    }
  }
}
```

**Flow:**
```
Application Logs → Logback → OpenTelemetryAppender → OTel SDK → OTLP → Grafana Loki
```

**Warning:** `spring-boot-starter-observation` handles tracing/metrics but has **nothing to do with log export**. Do not remove `OtelLogAppenderInstaller` or `opentelemetry-logback-appender-1.0`.

---

## 2. Current Configuration (As of April 6, 2026)

### 2.1 logback-spring.xml

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
  <include resource="org/springframework/boot/logging/logback/defaults.xml"/>
  <include resource="org/springframework/boot/logging/logback/console-appender.xml"/>

  <appender name="OpenTelemetry"
            class="io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender">
    <captureExperimentalAttributes>true</captureExperimentalAttributes>
    <captureKeyValuePairAttributes>true</captureKeyValuePairAttributes>
  </appender>

  <root level="INFO">
    <appender-ref ref="CONSOLE"/>
    <appender-ref ref="OpenTelemetry"/>
  </root>
</configuration>
```

### 2.2 application-otel.yaml (Logging Section)

```yaml
logging:
  level:
    com.example: debug  # Demo environment - verbose for debugging
  pattern:
    level: "%5p [${spring.application.name:},%X{traceId:-},%X{spanId:-}]"
```

### 2.3 Baggage Configuration

```yaml
management:
  tracing:
    baggage:
      remote-fields: user-type, request-source, tenant-id
      correlation:
        fields: user-type, request-source, tenant-id
```

This automatically injects baggage fields into MDC for log correlation.

---

## 3. 2026 Best Practices Assessment

### 3.1 What's Working Well ✅

| Feature | Status | Notes |
|---------|--------|-------|
| **OTLP Log Export** | ✅ Correct | Uses `opentelemetry-logback-appender-1.0:2.13.0-alpha` |
| **Trace Correlation** | ✅ Correct | `traceId` and `spanId` in MDC via Micrometer Tracing |
| **Centralized Config** | ✅ Correct | Shared module for all services |
| **Experimental Attributes** | ✅ Enabled | Captures thread name, logger name, etc. |
| **KeyValuePair Attributes** | ✅ Enabled | Structured key-value pairs in OTLP logs |
| **Structured Code Logging** | ✅ Good | Code uses `method={} path={}` pattern |
| **Health Check Filtering** | ✅ Good | `RequestCompletionLoggingFilter` skips `/actuator/*` |

### 3.2 Misconceptions Clarified ❌

| Misconception | Reality |
|---------------|---------|
| "Need JSON logging for Loki" | ❌ **OTLP is already structured** (protobuf format). Console pattern only affects human-readable output. |
| "Spring Boot 3.5 auto-installs OTel appender" | ❌ **False**. Manual `OtelLogAppenderInstaller` is required. |
| "Add logstash-logback-encoder" | ❌ **Redundant** when using OTLP export. Only needed for file-based JSON logging. |

### 3.3 Log Export Strategy: OpenTelemetryAppender vs ECS

**结论：本项目使用 `OpenTelemetryAppender` 是正确的最佳实践，ECS 不适合本项目技术栈。**

#### 为什么 OpenTelemetryAppender 是正确选择

本项目的日志链路为：

```
Logback → OpenTelemetryAppender
               ↓
          OTel SDK（Spring Boot 自动配置管理）
               ↓  OTLP HTTP
          grafana/otel-lgtm (:4318)
               ↓
          Loki（logs）+ Tempo（traces）— 自动关联
```

核心价值：
- **Trace 关联免配置** — `traceId`/`spanId` 在 Loki 与 Tempo 中是同一个值，可直接从日志跳转到 trace
- **无 Agent/Sidecar** — 不需要 Filebeat、Fluentd 等边车进程
- **三信号统一** — 同一个 OTLP 端点处理 logs/metrics/traces，配置最简
- **日志已结构化** — 通过 OTLP protobuf 传输，Loki 端无需解析文本；`logging.pattern.level` 只影响本地控制台输出

#### 为什么 ECS 不适合

ECS（Elastic Common Schema）是 **Elastic Stack 专用格式**，与本项目的 OTel/LGTM 技术栈根本不匹配：

| 维度 | 本项目（OTel/LGTM） | ECS 适用场景 |
|------|---------------------|-------------|
| 后端 | Grafana / Loki / Tempo | Elasticsearch / Kibana |
| 日志传输 | OTLP 协议（直连） | Filebeat → Elasticsearch |
| Trace 关联 | OTel TraceID | Elastic APM TraceID |
| Schema 标准 | OTel Semantic Conventions | Elastic Common Schema |

切换到 ECS 的代价：
1. 引入 Filebeat/Logstash 等额外组件（日志写文件再采集）
2. 废弃现有 OTLP log export 和 `OtelLogAppenderInstaller`
3. Trace 关联需要改用 Elastic APM Agent，与现有 Micrometer/OTel 体系冲突

#### ECS 的适用场景

| 场景 | 推荐方案 |
|------|----------|
| Elastic Stack（ELK/ECK） | ✅ ECS + ecs-logging-java |
| OTel/LGTM Stack（本项目） | ✅ OpenTelemetryAppender + OTLP |
| 多后端兼容（ELK + Grafana） | OTel Collector 做格式转换 |

---

### 3.4 Areas for Improvement 🔧

| Priority | Improvement | Impact | Context |
|----------|-------------|--------|---------|
| **P1** | Add `captureCodeAttributes`, `captureMarkerAttribute`, `captureLoggerContext` | Enhanced debugging info in Loki | **Recommended now** |
| **P2** | Environment-specific log levels | Production cost reduction | Production only |
| **P3** | Add baggage fields to console pattern | Local debugging visibility | Optional |
| **P4** | Async appender wrapper | High-throughput performance | Production only |
| **P5** | Parent-based sampling strategy | Trace volume control | Production only |
| **P6** | PII redaction filter | Compliance (GDPR, etc.) | Production only |

---

## 4. Recommended Improvements (Current Setup)

### 4.1 Enhanced OpenTelemetry Appender Configuration

**Status:** ⭐ **Recommended to implement now**

Add three attributes to `logback-spring.xml`:

```xml
<appender name="OpenTelemetry"
          class="io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender">
  <captureExperimentalAttributes>true</captureExperimentalAttributes>
  <captureKeyValuePairAttributes>true</captureKeyValuePairAttributes>
  
  <!-- NEW: Enhanced attributes (2026 best practices) -->
  <captureCodeAttributes>true</captureCodeAttributes>
  <captureMarkerAttribute>true</captureMarkerAttribute>
  <captureLoggerContext>true</captureLoggerContext>
</appender>
```

#### What These Attributes Capture

| Attribute | Captured Data | Use Case |
|-----------|---------------|----------|
| `captureCodeAttributes` | Source file, line number, method name | Precise error location in Loki |
| `captureMarkerAttribute` | SLF4J Markers (e.g., `SECURITY`, `AUDIT`) | Filter logs by category |
| `captureLoggerContext` | Logger context data (e.g., `logger.name`) | Trace log source hierarchy |

#### 2026 Stability Status

| Attribute | Status | Notes |
|-----------|--------|-------|
| `captureCodeAttributes` | ✅ Stable | Supported in `opentelemetry-logback-appender-1.0` |
| `captureMarkerAttribute` | ✅ Stable | Enables marker-based filtering in Loki |
| `captureLoggerContext` | ✅ Stable | Captures logger hierarchy |

### 4.2 Optional: Add Baggage Fields to Console Pattern

**Status:** 🔧 **Optional - useful for local debugging**

Current console pattern shows `traceId` and `spanId` but not baggage fields:

```yaml
# Current
logging.pattern.level: "%5p [${spring.application.name:},%X{traceId:-},%X{spanId:-}]"

# Enhanced (optional)
logging.pattern.level: "%5p [${spring.application.name:},%X{traceId:-},%X{spanId:-}] [%X{user-type:-},%X{request-source:-},%X{tenant-id:-}]"
```

**Note:** Baggage fields are **already sent to Loki** via `baggage.correlation.fields` config. This only affects console output.

---

## 5. Production Considerations

**See:** [LOGGING-PRODUCTION.md](LOGGING-PRODUCTION.md) for detailed production deployment guide.

### 5.1 Quick Summary

| Concern | Current (Demo) | Production Recommendation |
|---------|----------------|---------------------------|
| **Log Level** | `com.example: debug` | `com.example: info`, environment-specific profiles |
| **Sampling** | 100% (`probability: 1.0`) | 10% default with parent-based sampling |
| **Async Writes** | Synchronous (blocking) | Async appender with batch size + queue |
| **PII Handling** | No filtering | Redact sensitive data (email, phone, etc.) |
| **Dynamic Control** | Static config | Spring Boot Actuator `/actuator/loggers` endpoint |
| **Log Retention** | N/A (demo) | Loki retention policies + log compaction |

### 5.2 Environment-Specific Configuration

```yaml
# application-prod.yaml
logging:
  level:
    com.example: info
    org.springframework.web: warn
    org.hibernate: warn

management:
  tracing:
    sampling:
      probability: 0.1  # 10% sampling for production
```

### 5.3 Dynamic Log Level Adjustment

Enable via Actuator:

```yaml
# application.yaml
management:
  endpoint:
    loggers:
      enabled: true
  endpoints:
    web:
      exposure:
        include: loggers
```

**Usage:**
```bash
# Change log level at runtime
curl -X POST http://localhost:8080/actuator/loggers/com.example.hello \
  -H "Content-Type: application/json" \
  -d '{"configuredLevel":"DEBUG"}'

# Reset to default
curl -X POST http://localhost:8080/actuator/loggers/com.example.hello \
  -H "Content-Type: application/json" \
  -d '{"configuredLevel":null}'
```

---

## 6. Logging Code Patterns

### 6.1 Recommended Patterns

```java
// ✅ Good: Structured key-value pairs
log.info("request completed method={} path={} status={} durationMs={}",
    request.getMethod(), requestUri, response.getStatus(), durationMs);

// ✅ Good: Contextual error logging
log.warn("Failed to publish greeting event: {}", e.getMessage());

// ✅ Good: SLF4J Logger (not java.util.logging)
private static final Logger log = LoggerFactory.getLogger(MyService.class);
```

### 6.2 Anti-Patterns to Avoid

```java
// ❌ Bad: String concatenation (evaluates even if log level is off)
log.info("User " + userId + " performed action " + action);

// ❌ Bad: Logging exceptions without context
log.error("Error occurred", e);  // Missing: what operation failed?

// ❌ Bad: Using System.out.println
System.out.println("Debug info");  // Bypasses logging entirely
```

---

## 7. Troubleshooting

### 7.1 Logs Not Appearing in Loki

**Checklist:**

1. **Verify OTel Appender Installation:**
   ```bash
   # Look for this in startup logs:
   # "OpenTelemetry Logback appender installed successfully"
   ```

2. **Check OTLP Endpoint:**
   ```bash
   curl -s http://localhost:4318/v1/logs | head
   # Should not return 404
   ```

3. **Verify Dependencies:**
   ```kotlin
   // shared/build.gradle.kts
   api("io.opentelemetry.instrumentation:opentelemetry-logback-appender-1.0:2.13.0-alpha")
   ```

4. **Check logback-spring.xml:**
   ```xml
   <appender name="OpenTelemetry" 
             class="io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender">
   ```

### 7.2 Trace IDs Missing from Logs

**Causes:**

1. Micrometer Tracing not on classpath
2. Virtual threads not properly propagating context
3. Log pattern not reading MDC fields

**Fix:**
```yaml
# Verify in application.yaml
management:
  observations:
    annotations:
      enabled: true
  tracing:
    sampling:
      probability: 1.0
```

### 7.3 High Log Volume in Production

**Solutions:**

1. Reduce sampling probability: `OTEL_TRACING_SAMPLING_PROBABILITY=0.1`
2. Change log level: `logging.level.com.example=info`
3. Filter noisy packages: `logging.level.org.springframework.web=warn`

---

## 8. Related Documentation

| Document | Purpose |
|----------|---------|
| [LOGGING-PRODUCTION.md](LOGGING-PRODUCTION.md) | Production deployment considerations |
| [ARCHITECTURE.md](ARCHITECTURE.md) | System architecture overview |
| [VERIFICATION-HARNESS.md](VERIFICATION-HARNESS.md) | OTel verification harness |
| [JFR-OBSERVABILITY-GUIDE.md](JFR-OBSERVABILITY-GUIDE.md) | JFR + OTel integration |

---

## 9. Change History

| Date | Change |
|------|--------|
| 2026-04-06 | Initial logging guide created based on 2026 best practices review |
| 2026-04-06 | Identified need for `captureCodeAttributes`, `captureMarkerAttribute`, `captureLoggerContext` |
| 2026-04-06 | Clarified misconceptions about JSON logging and Spring Boot 3.5 native OTel support |
| 2026-04-06 | Added section 3.3: OpenTelemetryAppender vs ECS tradeoff analysis |
