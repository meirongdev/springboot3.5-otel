# Spring Boot OpenTelemetry 方案选择指南

**更新时间**: 2026 年 4 月 3 日

---

## 📌 Spring Boot 官方推荐

根据 [Spring 官方博客 (2025-11-18)](https://spring.io/blog/2025/11/18/opentelemetry-with-spring-boot)：

> **"We may be biased, but this is our favorite option to get observability in a Spring Boot application."**

Spring 团队明确推荐使用 **Micrometer Tracing** 方案，而非 OpenTelemetry Java Agent。

在本仓库的运行拓扑中，导出路径明确为：

`All services -> OTLP -> otel-collector -> grafana-otel-lgtm`

---

## 🎯 方案对比

### 1. Micrometer Tracing（Spring Boot 原生）✅ 推荐

**依赖**：
```kotlin
dependencies {
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("io.micrometer:micrometer-tracing-bridge-otel")
    implementation("io.opentelemetry:opentelemetry-exporter-otlp")
    implementation("io.micrometer:micrometer-registry-otlp")
}
```

**配置**：

> 说明：仓库内的 `application.yaml` 默认值仍是 `http://localhost:4318/...`；下面的 `otel-collector` 地址表示 Docker Compose 运行时通过环境变量/覆盖配置生效后的**有效 OTLP 目标**。

```yaml
# application.yaml
management:
  otlp:
    tracing:
      endpoint: http://otel-collector:4318/v1/traces
    metrics:
      export:
        url: http://otel-collector:4318/v1/metrics
    logging:
      endpoint: http://otel-collector:4318/v1/logs
  tracing:
    sampling:
      probability: ${OTEL_TRACING_SAMPLING_PROBABILITY:1.0}
  opentelemetry:
    resource-attributes:
      service.name: ${spring.application.name}
      service.namespace: ${OTEL_SERVICE_NAMESPACE:demo}
      service.version: ${OTEL_SERVICE_VERSION:1.0.0}
      deployment.environment: ${OTEL_DEPLOYMENT_ENVIRONMENT:local}
```

> 说明：推荐由服务配置声明资源属性，再通过 Collector 转发，不依赖外部系统补齐属性。

**优点**：
- ✅ 无缝集成 Spring Boot 自动配置
- ✅ 与 Micrometer Observation API 统一编程模型
- ✅ 无版本冲突风险
- ✅ GraalVM Native Image 友好
- ✅ Spring 团队首选方案

**缺点**：
- ❌ 日志需要 `logback-spring.xml` + `OtelLogAppenderInstaller` 手动桥接
- ❌ 配置项在 Spring Boot 3.5 中需手动添加（4.0 将内置）

**推荐场景**：**所有 Spring Boot 应用（首选）**

---

### 2. OpenTelemetry Java Agent ⚠️ 不推荐

**使用方式**：
```bash
java -javaagent:opentelemetry-javaagent.jar \
     -Dotel.service.name=my-app \
     -Dotel.exporter.otlp.endpoint=http://otel-collector:4317 \
     -jar app.jar
```

**优点**：
- ✅ 零代码改动
- ✅ 自动字节码插桩

**缺点**：
- ❌ 版本敏感，容易与 Spring Boot 自动配置冲突
- ❌ GraalVM Native Image 不兼容
- ❌ 可能导致类加载问题和难以诊断的冲突
- ❌ 需要维护 Agent 版本与应用依赖的兼容性

**推荐场景**：
- 遗留应用快速接入，无法修改代码
- 非 Spring Boot 应用

---

## 📊 详细对比表

| 特性 | Micrometer Tracing | Java Agent |
|-----|-------------------|------------|
| **集成方式** | Maven/Gradle 依赖 | `-javaagent` 参数 |
| **代码侵入** | 低（自动配置） | 零侵入 |
| **Spring 集成** | 原生支持 | 字节码插桩 |
| **版本兼容性** | 高（Spring 管理） | 低（需手动匹配） |
| **GraalVM Native** | ✅ 支持 | ❌ 不支持 |
| **冲突风险** | 低 | 高 |
| **维护成本** | 低 | 高 |
| **Spring 官方推荐** | ✅ 是 | ❌ 否 |

---

## 🛠️ 本项目配置

本项目采用 **Micrometer Tracing** 方案，配置如下。Compose 中的 `otel-collector` 使用固定镜像 `otel/opentelemetry-collector-contrib:0.149.0`，并且只在 Compose 网络内部暴露给服务和 `grafana-otel-lgtm`。

### build.gradle.kts (shared)

```kotlin
dependencies {
    api("org.springframework.boot:spring-boot-starter-actuator")
    api("io.micrometer:micrometer-tracing-bridge-otel")
    api("io.opentelemetry:opentelemetry-exporter-otlp")
    api("io.micrometer:micrometer-registry-otlp")
}
```

### application.yaml

> 说明：这里的 `otel-collector` 仍然是 Compose 部署时的运行时覆盖值；checked-in 默认配置保留 `localhost` 便于本地启动。

```yaml
spring:
  threads:
    virtual:
      enabled: true          # Java 25 Virtual Threads

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

> **Note**: `micrometer-registry-otlp` 导出的 duration 指标使用 `_milliseconds` 后缀（非 `_seconds`）。

这些资源属性会进入 Collector，再作为 Tempo / Prometheus / Loki 查询时使用的服务维度。验证脚本也会把 `deployment.environment` 等属性作为必需检查项。

### JFR Profiling（独立于 OTel）

JFR 是 JDK 内置工具，与 OTel 方案无关：

```bash
JDK_JAVA_OPTIONS="-XX:StartFlightRecording=name=production,maxsize=200m,maxage=2h"
```

---

## 📚 参考资料

### 官方文档
- [Spring Boot OpenTelemetry](https://spring.io/blog/2025/11/18/opentelemetry-with-spring-boot) - Spring 官方博客
- [Micrometer Tracing](https://micrometer.io/docs/tracing) - Micrometer 官方文档
- [Spring Boot Actuator](https://docs.spring.io/spring-boot/docs/3.5.0/reference/html/actuator.html) - Spring Boot 3.5 文档

### OpenTelemetry
- [OpenTelemetry Java](https://opentelemetry.io/docs/instrumentation/java/) - OTel Java 文档
- [OTLP Protocol](https://opentelemetry.io/docs/specs/otlp/) - OTLP 协议规范

### 对比分析
- [Java Agent vs Micrometer](https://hackernoon.com/opentelemetry-tracing-in-spring-boot-choosing-between-java-agent-and-micrometer) - 详细对比

---

## 💡 最佳实践建议

1. **新 Spring Boot 项目**：始终使用 **Micrometer Tracing**
2. **遗留应用迁移**：优先迁移到 Micrometer Tracing
3. **Java Agent 使用**：仅在无法修改代码的遗留系统中使用
4. **JFR Profiling**：与 OTel 方案无关，推荐所有生产环境启用

---

*最后更新：2026 年 4 月 3 日*
