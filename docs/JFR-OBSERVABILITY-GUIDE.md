# JFR + OpenTelemetry Observability 最佳实践指南

**适用版本**: Java 25 + Spring Boot 3.5  
**更新时间**: 2026 年 4 月

---

## 📋 目录

1. [JDK_JAVA_OPTIONS vs JAVA_TOOL_OPTIONS](#jdk_java_options-vs-java_tool_options)
2. [JFR 基础配置](#jfr-基础配置)
3. [OpenTelemetry Profiling 集成](#opentelemetry-profiling-集成)
4. [Spring Boot 3.5 项目配置](#spring-boot-35-项目配置)
5. [生产环境最佳实践](#生产环境最佳实践)
6. [故障排查](#故障排查)

---

## JDK_JAVA_OPTIONS vs JAVA_TOOL_OPTIONS

### 优先级对比

| 环境变量 | 优先级 | 影响范围 | 推荐场景 |
|---------|--------|---------|---------|
| `_JAVA_OPTIONS` | 最高 | 所有 Java 工具 | 强制覆盖（慎用） |
| `JDK_JAVA_OPTIONS` | 高 | `java` 命令 | **生产环境推荐** |
| `JAVA_TOOL_OPTIONS` | 中 | 所有 JDK 工具 | 全局 JDK 配置 |
| `JAVA_OPTS` | 低 | 仅脚本支持 | 遗留脚本兼容 |

### 为什么选择 JDK_JAVA_OPTIONS？

```bash
# ✅ 推荐：语义清晰，仅影响 java 命令
export JDK_JAVA_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+UseG1GC"
java -jar app.jar

# ⚠️ 不推荐：可能影响 javac 等构建工具
export JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
java -jar app.jar
```

**优势**：
1. **语义清晰**：明确用于 Java 应用启动
2. **更高优先级**：可覆盖其他配置源
3. **安全性**：不影响构建工具（`javac`, `gradle`）
4. **现代标准**：Java 9+ 推荐方式

---

## JFR 基础配置

### 核心参数说明

```bash
-XX:StartFlightRecording=<settings>
```

| 参数 | 说明 | 推荐值 |
|-----|------|--------|
| `name` | 录制名称 | `production` |
| `duration` | 录制时长 | `continuous` (持续) |
| `maxsize` | 最大内存 (MB) | `200` |
| `maxage` | 最大年龄 | `2h` |
| `settings` | 事件配置模板 | `profile` |
| `dumponexit` | 退出时转储 | `true` (调试用) |

### 配置示例

```bash
# 生产环境：持续录制，循环缓冲区
-XX:StartFlightRecording=name=production,maxsize=200m,maxage=2h,settings=profile

# 调试模式：退出时保存 JFR 文件
-XX:StartFlightRecording=name=debug,duration=60s,filename=/tmp/profile.jfr,dumponexit=true

# 低开销模式：仅关键事件
-XX:StartFlightRecording=name=lowoverhead,settings=lowoverhead
```

---

## OpenTelemetry Profiling 集成

### ⚠️ Spring Boot 官方推荐方案

根据 [Spring 官方博客 (2025-11-18)](https://spring.io/blog/2025/11/18/opentelemetry-with-spring-boot)：

> **"We may be biased, but this is our favorite option to get observability in a Spring Boot application."**
> 
> Spring 团队推荐使用 **Micrometer Tracing** 方案，而非 OpenTelemetry Java Agent。

### 方案对比

| 方案 | 优点 | 缺点 | 推荐场景 |
|-----|------|------|---------|
| **Micrometer Tracing** (Spring Boot 原生) | ✅ 无缝集成，自动配置<br>✅ 与 Micrometer Observation API 统一<br>✅ 无版本冲突风险<br>✅ GraalVM Native 友好 | ❌ 日志需要手动配置 Appender<br>❌ 异步任务需手动配置 Context Propagation | **Spring Boot 应用首选** |
| **OpenTelemetry Java Agent** | ✅ 零代码改动<br>✅ 自动字节码插桩 | ❌ 版本敏感，易冲突<br>❌ GraalVM Native 不兼容<br>❌ 可能与 Spring 自动配置冲突 | 遗留应用快速接入 |

### 架构概览

```
┌─────────────────────────────────────────────────────────────┐
│  Java Application (Java 25 + Spring Boot 3.5)               │
│  ┌──────────────┐  ┌──────────────────┐  ┌──────────────┐  │
│  │   JFR        │  │  Micrometer      │  │  Spring Boot │  │
│  │  Recorder    │  │  Tracing + OTLP  │  │  Actuator    │  │
│  └──────┬───────┘  └────────┬─────────┘  └──────┬───────┘  │
│         │                   │                    │          │
│         └───────────────────┼────────────────────┘          │
│                             │                                │
└─────────────────────────────┼────────────────────────────────┘
                              │ OTLP (gRPC/HTTP)
                              ▼
                   ┌──────────────────────┐
                   │  Grafana OTel LGTM   │
                   │  - Tempo (Traces)    │
                   │  - Prometheus (Metrics) │
                   │  - Loki (Logs)       │
                   └──────────────────────┘
```

### 配置方式

#### Spring Boot 3.5 原生 OTLP (推荐)

Spring Boot 3.5 内置 Micrometer Tracing，通过 `micrometer-tracing-bridge-otel` 桥接 OpenTelemetry：

```properties
# application.properties
# Tracing (Micrometer Tracing + OTLP Bridge)
management.otlp.tracing.endpoint=http://otel-collector:4318/v1/traces
management.tracing.sampling.probability=1.0

# Metrics (Micrometer OTLP Registry)
management.otlp.metrics.export.url=http://otel-collector:4318/v1/metrics
management.otlp.metrics.export.step=10s

# Logs (手动配置 Logback Appender)
management.otlp.logging.endpoint=http://otel-collector:4318/v1/logs

# JFR (通过 Actuator)
management.endpoints.web.exposure.include=health,metrics,threaddump,jfr
management.metrics.tags.application=${spring.application.name}
```

**依赖** (已在项目中配置)：
```kotlin
// build.gradle.kts
dependencies {
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("io.micrometer:micrometer-tracing-bridge-otel")
    implementation("io.opentelemetry:opentelemetry-exporter-otlp")
    implementation("io.micrometer:micrometer-registry-otlp")
}
```

---

## Spring Boot 3.5 项目配置

### Docker Compose 配置

```yaml
services:
  hello-service:
    build:
      context: .
      dockerfile: hello-service/Dockerfile
    ports:
      - "8080:8080"
    environment:
      # 服务发现
      - USER_SERVICE_URL=http://user-service:8081
      - GREETING_SERVICE_URL=http://greeting-service:8082
      
      # OpenTelemetry
      - MANAGEMENT_OTLP_TRACING_ENDPOINT=http://grafana-otel-lgtm:4318/v1/traces
      - MANAGEMENT_OTLP_METRICS_EXPORT_URL=http://grafana-otel-lgtm:4318/v1/metrics
      - MANAGEMENT_OTLP_LOGGING_ENDPOINT=http://grafana-otel-lgtm:4318/v1/logs
      
      # JFR Profiling (JDK_JAVA_OPTIONS)
      - JDK_JAVA_OPTIONS=-XX:StartFlightRecording=name=production,maxsize=200m,maxage=2h,settings=profile -XX:MaxRAMPercentage=75 -XX:+UseG1GC -Xlog:gc*:file=/logs/gc.log:time,uptime:filecount=5,filesize=10M
      
      # Actuator
      - MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health,metrics,threaddump
      - MANAGEMENT_METRICS_TAGS_APPLICATION=${spring.application.name}
    
    volumes:
      - ./logs/hello-service:/logs
    
    depends_on:
      grafana-otel-lgtm:
        condition: service_healthy
```

### Dockerfile 更新

```dockerfile
FROM eclipse-temurin:25-jre-alpine
WORKDIR /app

# 创建非 root 用户
RUN addgroup -S app && adduser -S app -G app

# 复制应用
COPY --from=builder /build/hello-service/build/libs/*.jar app.jar

# 创建日志目录
RUN mkdir -p /logs && chown -R app:app /logs

USER app

EXPOSE 8080

# 使用 JDK_JAVA_OPTIONS 而非硬编码参数
ENTRYPOINT ["java", "-jar", "app.jar"]
```

### 本地开发配置

```bash
# scripts/run-with-jfr.sh
#!/bin/bash

export JDK_JAVA_OPTIONS="
  -XX:StartFlightRecording=name=dev,duration=60s,filename=profile.jfr,dumponexit=true
  -XX:MaxRAMPercentage=75
  -XX:+UseG1GC
  -Xlog:gc*:file=logs/gc.log:time,uptime
"

java -jar hello-service/build/libs/hello-service.jar
```

---

## 生产环境最佳实践

### 1. 内存管理 (Kubernetes/容器化)

```yaml
# ✅ 推荐：百分比配置，自适应容器限制
env:
  - name: JDK_JAVA_OPTIONS
    value: >-
      -XX:MaxRAMPercentage=75.0
      -XX:InitialRAMPercentage=50.0
      -XX:+UseG1GC
      -XX:MaxGCPauseMillis=200

# ❌ 避免：固定值，可能导致 OOM 或资源浪费
# -Xms2g -Xmx2g
```

### 2. GC 日志与诊断

```bash
-XX:+UseG1GC \
-XX:MaxGCPauseMillis=200 \
-Xlog:gc*:file=/logs/gc.log:time,uptime:filecount=5,filesize=10M \
-XX:+HeapDumpOnOutOfMemoryError \
-XX:HeapDumpPath=/logs/ \
-XX:+ExitOnOutOfMemoryError
```

### 3. JFR 持续录制配置

```bash
# 生产环境：低开销持续录制
-XX:StartFlightRecording=name=production,maxsize=200m,maxage=2h,settings=profile

# 事件触发录制（动态控制）
# 使用 jcmd 在检测到问题时启动
jcmd <PID> JFR.start name=diagnostic,duration=10m,filename=diagnostic.jfr
```

### 4. Virtual Thread 监控 (Java 21+)

```java
// 检测 Virtual Thread Pinning
@EventListener
public void onApplicationEvent(ApplicationReadyEvent event) {
    // 注册 JFR Event Listener
    new Thread(() -> {
        try (var subscription = new EventStream()) {
            subscription.enable("jdk.VirtualThreadPinned");
            subscription.start();
            for (var event : subscription) {
                // 上报到监控系统
                log.warn("Virtual thread pinned: {}", event);
            }
        }
    }).start();
}
```

### 5. 自定义 JFR 事件

```java
// src/main/java/com/example/demo/jfr/OrderProcessedEvent.java
import jdk.jfr.*;

@Name("com.example.OrderProcessed")
@Label("Order Processed")
@Category("Application")
@Description("Fired when an order is successfully processed")
public class OrderProcessedEvent extends Event {
    
    @Label("Order ID")
    public String orderId;
    
    @Label("Total Amount")
    public double totalAmount;
    
    @Label("Processing Time MS")
    public long processingTimeMs;
    
    public static void record(String orderId, double amount, long duration) {
        if (isEnabled()) {
            var event = new OrderProcessedEvent();
            event.orderId = orderId;
            event.totalAmount = amount;
            event.processingTimeMs = duration;
            event.commit();
        }
    }
    
    public static boolean isEnabled() {
        return Event.isEnabled("com.example.OrderProcessed");
    }
}
```

---

## 故障排查

### JFR 验证

```bash
# 检查 JFR 是否启用
jcmd <PID> JFR.check

# 查看活动录制
jcmd <PID> JFR.check

# 导出录制
jcmd <PID> JFR.dump filename=profile.jfr

# 停止录制
jcmd <PID> JFR.stop
```

### 分析 JFR 文件

```bash
# 使用 jfr 工具
jfr print profile.jfr
jfr summary profile.jfr

# 使用 JDK Mission Control
jmc &
# 打开 profile.jfr

# 使用 async-profiler 生成火焰图
asprof -jfr profile.jfr --flamegraph
```

### Grafana 查看 Profiling

1. 访问 http://localhost:3000
2. 导航到 **Explore** → **Tempo**
3. 选择 **Profile** 数据源
4. 查询服务名：`hello-service`

### 常见问题

| 问题 | 原因 | 解决方案 |
|-----|------|---------|
| JFR 文件过大 | `maxsize` 设置过大 | 调整为 `100m-200m` |
| Profiling 开销高 | 采样间隔过短 | 设置为 `10ms` 或更高 |
| 无 Profile 数据 | OTLP 端点错误 | 检查 `otel.exporter.otlp.endpoint` |
| Virtual Thread Pinning | 阻塞操作 | 使用 `subscribeOn(Schedulers.boundedElastic())` |

---

## 参考链接

### 官方文档
- [JDK 25 JFR Documentation](https://docs.oracle.com/en/java/javase/25/docs/specs/jfr/index.html)
- [Spring Boot OpenTelemetry](https://spring.io/blog/2025/11/18/opentelemetry-with-spring-boot) - **Spring 官方推荐方案**
- [Micrometer Tracing](https://micrometer.io/docs/tracing)
- [Spring Boot 3.5 Actuator](https://docs.spring.io/spring-boot/docs/3.5.0/reference/html/actuator.html)

### 方案选择
- [docs/SPRINGBOOT-OTEL-RECOMMENDATION.md](SPRINGBOOT-OTEL-RECOMMENDATION.md) - Spring Boot OTel 方案选择指南

### 工具下载
- [JDK Mission Control](https://jdk.java.net/jmc/)
- [async-profiler](https://github.com/jvm-profiling-tools/async-profiler)

### 学习资源
- [JFR: The Ultimate Performance Tool](https://www.baeldung.com/java-flight-recorder)
- [Continuous Profiling with JFR + OpenTelemetry](https://oneuptime.com/blog/post/2026-02-06-instrument-java-continuous-profiling-otel-profiling-signal/view)

---

## 快速开始

```bash
# 1. 启动 Grafana OTel LGTM
docker compose up -d grafana-otel-lgtm

# 2. 启动服务（带 JFR Profiling）
export JDK_JAVA_OPTIONS="-XX:StartFlightRecording=name=prod,maxsize=200m,maxage=2h"
./gradlew :hello-service:bootRun

# 3. 生成负载
curl http://localhost:8080/api/1

# 4. 查看 Grafana
open http://localhost:3000
# 登录：admin/admin
```

---

*最后更新：2026 年 4 月 3 日*
