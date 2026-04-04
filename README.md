# Spring Boot 3.5 + Java 25 OpenTelemetry Demo

基于 Spring Boot 3.5 和 Java 25 的 OpenTelemetry 可观测性示例项目，展示分布式追踪（Traces）、指标（Metrics）和日志（Logs）的完整集成。

> 参考 [spring-boot-and-opentelemetry](https://github.com/mhalbritter/spring-boot-and-opentelemetry)（Spring Boot 4），适配为 Spring Boot 3.5 版本。

## Architecture

```
                    ┌──────────────────┐
  curl/browser ───► │  hello-service   │ :8080
                    └────────┬─────────┘
                             │
               ┌─────────────┼─────────────┐
               │                           │
      ┌────────▼─────────┐       ┌────────▼─────────┐
      │  user-service    │       │ greeting-service  │
      │     :8081        │       │     :8082         │
      └──────────────────┘       └───────────────────┘

  All services ──OTLP──► otel-collector ──OTLP──► grafana-otel-lgtm (:3000)
```

Export path: `All services -> OTLP -> otel-collector -> grafana-otel-lgtm`

- **hello-service** - 入口编排服务，调用下游服务组合响应
- **user-service** - 用户数据服务（H2 + Spring Data JDBC + Flyway）
- **greeting-service** - 多语言问候语服务（支持 en/zh/ja）
- **otel-collector** - Compose 内部 Collector（`otel/opentelemetry-collector-contrib:0.149.0`），统一接收服务 OTLP 数据并转发到 `grafana-otel-lgtm`

## Tech Stack

| Component | Technology |
|-----------|------------|
| Runtime | Java 25 |
| Framework | Spring Boot 3.5.0 |
| Build | Gradle 9.4.1 (Kotlin DSL) |
| Tracing | **Micrometer Tracing** + OpenTelemetry Bridge (Spring 官方推荐) |
| Metrics | Micrometer + OTLP Registry (`_milliseconds` 单位) |
| Logs | Logback + OpenTelemetry Appender + `logback-spring.xml` |
| Profiling | JFR (Java Flight Recorder, JDK 内置) |
| HTTP Client | Spring RestClient |
| Database | H2 + Spring Data JDBC + Flyway |
| Backend | Grafana OTEL LGTM |
| Testing | JUnit 5, Spring Cloud Contract (Contract Testing), ArchUnit |
| CI | GitHub Actions |

> **Note**: 本项目采用 Spring Boot 官方推荐的 **Micrometer Tracing** 方案，而非 OpenTelemetry Java Agent。详见 [Spring 官方博客](https://spring.io/blog/2025/11/18/opentelemetry-with-spring-boot)。

### Service OTel Configuration Pattern

仓库中各服务的 `application.yaml` 默认值仍使用 `http://localhost:4318/...`，便于本地直连调试；在 Docker Compose 运行时，`compose.yaml` 会把这些值覆盖为 Compose 网络内的 `http://otel-collector:4318/...`。下面的示例展示的是 Compose 部署下的**有效运行时配置**：

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

这与当前运行系统保持一致：资源属性来自各服务配置，而不是由外部 Collector 注入。`otel-collector` 只是 Compose 运行时的 OTLP 目标，不是服务仓库里硬编码的默认值。

## Quick Start

### Prerequisites

- Java 25+
- Docker & Docker Compose

### 1. Start Observability Backend

```bash
# Using Make (recommended)
make up

# Or directly
docker compose up -d
```

Grafana UI: [http://localhost:3000](http://localhost:3000) (admin/admin)

> **Note**: The `make up` command waits for Grafana to be healthy before returning. `otel-collector` stays internal to Docker Compose and forwards telemetry to `grafana-otel-lgtm`.

### 2. Start Services

```bash
# Terminal 1 - Greeting Service
./gradlew :greeting-service:bootRun

# Terminal 2 - User Service
./gradlew :user-service:bootRun

# Terminal 3 - Hello Service
./gradlew :hello-service:bootRun
```

### 3. Send Requests

```bash
# English greeting
curl http://localhost:8080/api/1

# Chinese greeting
curl -H "Accept-Language: zh" http://localhost:8080/api/1

# Japanese greeting
curl -H "Accept-Language: ja" http://localhost:8080/api/1

# Complex Accept-Language header (normalized automatically)
curl -H "Accept-Language: zh-CN,zh;q=0.9,en;q=0.8" http://localhost:8080/api/1
```

### 4. Explore in Grafana

打开 [http://localhost:3000](http://localhost:3000)：

- **Dashboards > Services Overview** - 按 `service_name` 查看请求率、错误率和平均延迟
- **Dashboards > Logs & Traces** - 同时查看 Log Volume、Recent Traces（Tempo）和 Application Logs（Loki）
- **Explore > Tempo** - 查看分布式追踪链路
- **Explore > Loki** - 查看关联 Trace ID 的日志
- **Explore > Prometheus** - 查看 JVM 和自定义指标

## OTel Features Demonstrated

| Feature | Description |
|---------|-------------|
| Distributed Tracing | 跨三个服务的请求链路追踪 |
| Context Propagation | 异步任务中的 Trace Context 自动传播 |
| JVM Metrics | CPU、内存、线程、类加载器指标采集 |
| Custom Metrics | 使用 OTel API 创建自定义 Counter |
| Log Correlation | 日志自动关联 Trace ID / Span ID |
| Manual Spans | 使用 OTel API 手动创建 Span |
| OTLP Export | 通过 OTLP 协议导出所有遥测数据 |
| HTTP Observation | 自动追踪 HTTP 请求 |
| **JFR Profiling** | **Java Flight Recorder 持续性能分析** |

## Quality & Test Harness

当前仓库的质量门禁和端到端验证面包括：

| Gate | Tool | Description |
|------|------|-------------|
| Formatting | Spotless | Google Java Format 1.28.0 |
| Static Analysis | Error Prone | 编译时静态检查 |
| Coverage | JaCoCo | 60% 最低覆盖率门禁 |
| Contract Tests | Spring Cloud Contract | 消费者驱动契约测试 |
| Architecture Tests | ArchUnit | 模块依赖规则验证 |
| End-to-End Smoke | JUnit + Stubs | 真实 HTTP 链路测试 |
| **OTel Verification** | **verify-otel.sh** | **自动验证遥测数据收集** |

### OpenTelemetry Verification Harness

使用自动化脚本验证 OpenTelemetry 数据是否正确收集：

```bash
# 基本验证
make verify-otel

# 详细输出
make verify-otel-verbose

# 等待 Grafana 就绪后验证
make verify-otel-wait
```

这些 `make verify-otel*` 目标会先执行 `docker compose up -d --build --wait`，再用 `--wait` 模式运行验证脚本，避免拿旧容器/旧镜像或冷启动中的遥测去校验最新源码。

`make verify-otel-wait` 会生成机器可读证据报告：`build/reports/otel/verification-report.json`

验证内容包括：
- ✅ **Collector Availability** - `grafana-otel-lgtm` 可通过 Compose 内部地址访问 `otel-collector`
- ✅ **Metrics** - JVM 指标、HTTP 请求指标
- ✅ **Traces** - 分布式追踪链路
- ✅ **Required Resource Attributes** - `service.name`、`service.namespace`、`service.version`、`deployment.environment`
- ✅ **Distributed Tracing** - 跨服务追踪
- ⚠️ **Logs / Loki** - 保留为 advisory 证据；不会单独决定验证失败

仓库里预置的两个自定义 Grafana dashboard 直接建立在这套数据面上：`Services Overview` 使用 `service_name` 维度展示 RED 指标；`Logs & Traces` 则把 Tempo 最近 trace 查询和 Loki 日志放在同一个页面。共享模块里的请求完成日志会在每次 HTTP 请求后输出 `method/path/status/durationMs`，所以对 `/api/1` 打几次流量后，两个 dashboard 都会持续出现新数据。

详见：[docs/VERIFICATION-HARNESS.md](docs/VERIFICATION-HARNESS.md)

## JFR Profiling (Java 25)

### Quick Start

```bash
# 方式 1: 使用 Make 命令（推荐）
make jfr-run

# 方式 2: 使用脚本
./scripts/run-with-jfr.sh hello-service

# 方式 3: Docker Compose（生产环境配置）
# JFR 已通过 JDK_JAVA_OPTIONS 在 compose.yaml 中启用
```

### JFR 命令

```bash
make jfr-check   # 查看活动录制
make jfr-dump    # 导出当前录制
make jfr-stop    # 停止录制
make jfr-analyze # 分析最新 JFR 文件
make jfr-flame   # 生成火焰图
```

### 生产环境配置

所有服务已通过 `JDK_JAVA_OPTIONS` 启用 JFR 持续录制：

```yaml
environment:
  - JDK_JAVA_OPTIONS=-XX:StartFlightRecording=name=production,maxsize=200m,maxage=2h,settings=profile
```

**开销**: <2% CPU，适用于生产环境持续运行。

详见：[docs/JFR-OBSERVABILITY-GUIDE.md](docs/JFR-OBSERVABILITY-GUIDE.md)

---

## 常用命令

**使用 Makefile (推荐):**

```bash
make help            # 显示所有可用命令
make build           # 构建所有服务
make test            # 运行所有测试
make clean           # 清理构建产物
make fmt             # 格式化代码
make check           # 格式化和静态分析检查
make coverage        # 生成 JaCoCo 覆盖率报告
make dev-test        # 完整开发测试流程 (clean + fmt + check + test + coverage)
make dev-full        # 完整流程 + OTel 验证
make verify-otel     # 验证 OpenTelemetry 数据收集
```

**Docker Compose:**

```bash
make up              # 启动 Grafana OTEL LGTM 后端（等待健康检查）
make down            # 停止后端
make restart         # 重启后端
make logs            # 查看后端日志
```

**运行服务:**

```bash
make run-all         # 启动所有服务 (后台运行)
make stop-all        # 停止所有服务
make run-hello       # 仅启动 hello-service
make run-user        # 仅启动 user-service
make run-greeting    # 仅启动 greeting-service
```

**测试:**

```bash
make test-e2e        # 运行端到端测试
make test-contract   # 运行 Spring Cloud Contract 契约测试
make test-arch       # 运行架构测试
```

**直接使用 Gradle:**

```bash
# 格式化检查
./gradlew spotlessCheck

# 完整构建和测试
./gradlew clean build

# 覆盖率报告
./gradlew testCodeCoverageReport

# 仅运行特定服务测试
./gradlew :hello-service:test
```

更多建议见：`docs/harness-recommendations-2026.md`

## Project Structure

```
springboot3.5-otel/
├── shared/              # 公共 OTel 配置模块（OtelLogAppenderInstaller + logback-spring.xml）
├── hello-service/       # 编排服务 (:8080)
├── user-service/        # 用户服务 (:8081)
├── greeting-service/    # 问候语服务 (:8082)
├── arch-tests/          # ArchUnit 架构测试
├── compose.yaml         # Grafana OTEL LGTM Docker Compose
├── Makefile             # 常用命令快捷方式
├── .editorconfig        # 统一 IDE 格式化配置
├── jdk-java-options.env # JFR 生产环境配置模板
├── otel-profiling.env   # OpenTelemetry Agent 配置模板
├── docs/
│   ├── design.md                    # 架构设计文档
│   ├── README.md                    # 文档说明
│   ├── docker-fixes.md              # Docker 问题修复
│   ├── verification-report-2026-04-02.md  # 验证报告
│   ├── VERIFICATION-HARNESS.md      # OTel 验证 harness 文档
│   ├── JFR-OBSERVABILITY-GUIDE.md   # JFR + OTel 最佳实践指南
│   ├── JFR-QUICK-REFERENCE.md       # JFR 快速参考
│   └── SPRINGBOOT-OTEL-RECOMMENDATION.md # Spring Boot OTel 方案选择
├── grafana/
│   ├── dashboards/      # Grafana 仪表板定义
│   └── provisioning/    # Grafana 配置
├── scripts/
│   ├── publish-stubs.sh # Spring Cloud Contract stub 发布脚本
│   ├── verify-otel.sh   # OTel 数据收集验证脚本
│   ├── jfr-profiling.sh # JFR 录制管理工具
│   └── run-with-jfr.sh  # 本地开发 JFR 启动脚本
├── .github/workflows/
│   └── ci.yml           # GitHub Actions CI 配置
├── README.md            # 本文件
├── QWEN.md              # 开发者上下文文档
└── spec.md              # 原始规格说明
```

## Spring Boot 3.5 Adaptation Notes

本项目与基于 Spring Boot 4 的参考项目的主要区别：

| Feature | Spring Boot 4 | Spring Boot 3.5 |
|---------|---------------|-----------------|
| OTel Starter | `spring-boot-starter-opentelemetry` | 不可用 |
| Tracing Bridge | 内置 | `micrometer-tracing-bridge-otel` |
| OTLP Export | 自动配置 | 手动添加 `opentelemetry-exporter-otlp` |
| Logback Appender | 自动安装 | `logback-spring.xml` + `OtelLogAppenderInstaller`（需手动桥接） |
| Metrics Export | 内置 OTLP | `micrometer-registry-otlp`（`_milliseconds` 单位） |
| Virtual Threads | N/A | `spring.threads.virtual.enabled: true` |
| RestClient | N/A | Spring Boot 3.5 原生支持（3.2+ 引入） |

## CI/CD

GitHub Actions 工作流 (`.github/workflows/ci.yml`)：

- **触发条件**: Push 到 main/master，或任意分支的 Pull Request
- **Java 环境**: Java 25 Temurin
- **Gradle 缓存**: 使用 `gradle/actions/setup-gradle@v5` 自动缓存
- **质量检查**: Spotless 格式化、Error Prone 静态分析
- **测试**: 单元测试、契约测试、架构测试
- **产物**: Spring Cloud Contract stubs、JaCoCo 报告、测试报告

## License

MIT
