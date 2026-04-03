# JFR + OpenTelemetry Profiling 快速参考

**版本**: Java 25 + Spring Boot 3.5  
**日期**: 2026 年 4 月

---

## 🚀 快速开始

### 1. 启动服务（带 JFR Profiling）

```bash
# 启动 Grafana OTel LGTM
make up

# 启动 hello-service（带 JFR）
make jfr-run

# 或者使用 Docker Compose（所有服务自动启用 JFR）
docker compose up -d
```

### 2. 生成负载

```bash
# 发送测试请求
curl http://localhost:8080/api/1

# 高并发测试
ab -n 1000 -c 10 http://localhost:8080/api/1
```

### 3. 查看 Profiling 数据

```bash
# 检查活动录制
make jfr-check

# 导出录制
make jfr-dump

# 生成火焰图
make jfr-flame

# 分析录制
make jfr-analyze
```

---

## 📊 JDK_JAVA_OPTIONS vs JAVA_TOOL_OPTIONS

| 环境变量 | 优先级 | 推荐场景 |
|---------|--------|---------|
| `JDK_JAVA_OPTIONS` | 高 | **生产环境（推荐）** |
| `JAVA_TOOL_OPTIONS` | 中 | 全局 JDK 配置 |
| `JAVA_OPTS` | 低 | 遗留脚本兼容 |

**为什么选择 JDK_JAVA_OPTIONS？**
- ✅ 语义清晰：仅影响 `java` 命令
- ✅ 更高优先级：可覆盖其他配置
- ✅ 安全性：不影响构建工具（`javac`, `gradle`）

---

## ⚙️ 生产环境配置示例

### Docker Compose (compose.yaml)

```yaml
services:
  hello-service:
    environment:
      # JFR 持续录制（开销 <2%）
      - JDK_JAVA_OPTIONS=-XX:StartFlightRecording=name=production,maxsize=200m,maxage=2h,settings=profile -XX:MaxRAMPercentage=75 -XX:+UseG1GC -Xlog:gc*:file=/logs/gc.log:time,uptime:filecount=5,filesize=10M
      
      # OpenTelemetry
      - MANAGEMENT_OTLP_TRACING_ENDPOINT=http://grafana-otel-lgtm:4318/v1/traces
      - MANAGEMENT_OTLP_METRICS_EXPORT_URL=http://grafana-otel-lgtm:4318/v1/metrics
```

### Kubernetes

```yaml
env:
  - name: JDK_JAVA_OPTIONS
    value: >-
      -XX:MaxRAMPercentage=75.0
      -XX:InitialRAMPercentage=50.0
      -XX:+UseG1GC
      -XX:StartFlightRecording=name=production,maxsize=200m,maxage=2h,settings=profile
      -Xlog:gc*:file=/logs/gc.log:time,uptime:filecount=5,filesize=10M
```

---

## 🔧 JFR 命令参考

### jcmd 命令

```bash
# 检查 JFR 状态
jcmd <PID> JFR.check

# 启动录制
jcmd <PID> JFR.start name=diagnostic,duration=60s,filename=profile.jfr

# 导出录制
jcmd <PID> JFR.dump name=production filename=profile.jfr

# 停止录制
jcmd <PID> JFR.stop name=diagnostic
```

### jfr 工具

```bash
# 打印摘要
jfr summary profile.jfr

# 打印事件
jfr print profile.jfr

# 查看 GC 事件
jfr print --events jdk.GCPhasePause profile.jfr
```

### async-profiler 火焰图

```bash
# 生成火焰图
asprof -jfr profile.jfr --flamegraph flame.html

# 生成 JFR 格式
asprof -jfr profile.jfr --jfr output.jfr
```

---

## 📈 OpenTelemetry 集成

### Spring Boot 官方推荐方案

根据 [Spring 官方博客](https://spring.io/blog/2025/11/18/opentelemetry-with-spring-boot)，Spring 团队推荐使用 **Micrometer Tracing**，而非 Java Agent。

| 方案 | 优点 | 缺点 | 推荐场景 |
|-----|------|------|---------|
| **Micrometer Tracing** | ✅ 无缝集成，自动配置<br>✅ 无版本冲突<br>✅ GraalVM Native 友好 | ❌ 日志需手动配置 | **Spring Boot 首选** |
| **Java Agent** | ✅ 零代码改动 | ❌ 版本敏感，易冲突<br>❌ GraalVM 不兼容 | 遗留应用 |

### 配置方式（Micrometer Tracing）

```properties
# application.properties
management.otlp.tracing.endpoint=http://otel-collector:4318/v1/traces
management.otlp.metrics.export.url=http://otel-collector:4318/v1/metrics
management.otlp.logging.endpoint=http://otel-collector:4318/v1/logs
```

### ⚠️ Java Agent 方案（不推荐）

仅在遗留应用快速接入时使用：

```bash
java \
  -javaagent:opentelemetry-javaagent.jar \
  -Dotel.service.name=hello-service \
  -Dotel.exporter.otlp.endpoint=http://grafana-otel-lgtm:4317 \
  -jar app.jar
```

---

## 🎯 关键配置参数

### JFR 参数

| 参数 | 推荐值 | 说明 |
|-----|--------|------|
| `maxsize` | `200m` | 最大内存占用 |
| `maxage` | `2h` | 循环缓冲区时长 |
| `settings` | `profile` | 事件配置模板 |
| `duration` | `continuous` | 持续录制 |

### GC 参数

| 参数 | 推荐值 | 说明 |
|-----|--------|------|
| `MaxRAMPercentage` | `75` | 最大堆内存百分比 |
| `InitialRAMPercentage` | `50` | 初始堆内存百分比 |
| `UseG1GC` | `true` | 使用 G1 GC |
| `MaxGCPauseMillis` | `200` | 目标 GC 暂停时间 |

### OpenTelemetry Profiling

| 参数 | 推荐值 | 说明 |
|-----|--------|------|
| `otel.profiling.enabled` | `true` | 启用 Profiling |
| `otel.profiling.sampling.interval` | `10ms` | CPU 采样间隔 |
| `otel.profiling.export.interval` | `60s` | 导出间隔 |

---

## 🐛 故障排查

### JFR 未启用

```bash
# 检查是否启用 JFR
jcmd <PID> JFR.check

# 错误：JFR not available
# 解决：启动时添加 -XX:StartFlightRecording=...
```

### 无 Profile 数据

1. 检查 OTLP 端点配置
2. 验证 Grafana OTel LGTM 运行状态
3. 查看服务日志：`docker compose logs hello-service`

### 分析 JFR 文件

```bash
# 使用 JDK Mission Control
jmc &
# File > Open Flight Recorder Recording

# 使用命令行
jfr summary profile.jfr
jfr print profile.jfr | head -100
```

---

## 📚 相关文档

- [docs/JFR-OBSERVABILITY-GUIDE.md](docs/JFR-OBSERVABILITY-GUIDE.md) - 完整指南
- [jdk-java-options.env](jdk-java-options.env) - 配置模板
- [otel-profiling.env](otel-profiling.env) - OTel Agent 配置

---

## 🔗 参考链接

### 官方文档
- [JDK 25 JFR](https://docs.oracle.com/en/java/javase/25/docs/specs/jfr/index.html)
- [OpenTelemetry Profiling](https://opentelemetry.io/docs/specs/otel/profiles/)
- [Spring Boot 3.5 Actuator](https://docs.spring.io/spring-boot/docs/3.5.0/reference/html/actuator.html)

### 工具
- [OpenTelemetry Java Agent](https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases)
- [JDK Mission Control](https://jdk.java.net/jmc/)
- [async-profiler](https://github.com/jvm-profiling-tools/async-profiler)

---

*最后更新：2026 年 4 月 3 日*
