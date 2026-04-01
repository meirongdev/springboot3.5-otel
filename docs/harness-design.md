# Harness Engineering Design: Spring Boot 3.5 + Java 25 OTel Demo

## 1. Overview

本文档定义项目的工程化基础设施（Harness Engineering），覆盖测试、质量、CI/CD、容器化、可观测性增强五个维度。所有方案基于 2026 年 4 月各开源项目最新稳定版本。

### 1.1 设计原则

- **Demo 项目优先**：选择轻量、易理解的方案，避免过度工程化
- **Convention over Configuration**：最大化利用 Spring Boot 3.5 + Gradle 9.x 的内置能力
- **Infrastructure as Code**：所有基础设施（CI、Dashboard、Docker）均可版本管理
- **增量引入**：每个 harness 模块独立，可分阶段实施

### 1.2 版本基线

| 工具 | 版本 | 备注 |
|------|------|------|
| Gradle | 9.4.1 | 已在用，启用 Configuration Cache |
| Testcontainers | 2.0.4 | 2.0 大版本，新包路径 |
| ArchUnit | 1.4.1 | 支持 Java 25 class file |
| Spotless Plugin | 8.4.0 | google-java-format 1.28.0 |
| Error Prone | 2.48.0 / Plugin 5.1.0 | Java 25 兼容 |
| JaCoCo | 0.8.15 | ASM 9.9，支持 Java 25 |
| Pact JVM | 4.6.x | 消费者驱动契约测试 |
| Eclipse Temurin | 25-jre-alpine | 运行时基础镜像 |

## 2. Test Harness

### 2.1 测试金字塔

```
         ┌──────────┐
         │ Contract │  ← Pact: 跨服务 API 契约
         ├──────────┤
         │Integration│  ← Testcontainers: OTel Collector、H2 → 真实数据库
         ├──────────┤
         │   Unit   │  ← JUnit 5 + Mockito: Controller/Service 层
         └──────────┘
```

### 2.2 单元测试

每个 service 模块包含：

- **Controller 测试**：使用 `@WebMvcTest` 切片测试，mock 依赖
- **Service 测试**：纯 JUnit 5 + Mockito

```java
@WebMvcTest(HelloController.class)
class HelloControllerTest {
    @Autowired MockMvc mockMvc;
    @MockBean HelloService helloService;

    @Test
    void shouldReturnHello() throws Exception {
        when(helloService.hello(1L)).thenReturn("Hello, Alice!");
        mockMvc.perform(get("/api/1"))
               .andExpect(status().isOk())
               .andExpect(content().string(containsString("Alice")));
    }
}
```

### 2.3 集成测试 — Testcontainers 2.0

**核心场景**：验证 OTel 遥测数据实际导出到 Collector。

Testcontainers 2.0 关键变化：
- 包路径从 `org.testcontainers.containers.*` 改为模块专属包
- JUnit 4 支持已移除
- Spring Boot `@ServiceConnection` 无缝集成

```java
@SpringBootTest(webEnvironment = RANDOM_PORT)
@Testcontainers
class UserServiceIntegrationTest {

    @Container
    @ServiceConnection
    static GenericContainer<?> otelCollector =
        new GenericContainer<>("otel/opentelemetry-collector-contrib:latest")
            .withExposedPorts(4318);

    @Autowired TestRestTemplate restTemplate;

    @Test
    void shouldExportTracesOnRequest() {
        restTemplate.getForEntity("/api/users/1", User.class);
        // 验证 trace 已导出（通过 collector 的 logging exporter 或 in-memory exporter）
    }
}
```

### 2.4 契约测试 — Pact

选择 Pact 而非 Spring Cloud Contract 的理由：
- 项目未使用 Spring Cloud 生态，Pact 依赖更轻
- Pact 是消费者驱动（Consumer-Driven），与微服务边界天然契合
- 语言无关，未来扩展灵活

**Consumer 端**（hello-service 定义期望）：

```java
@ExtendWith(PactConsumerTestExt.class)
class UserServicePactTest {

    @Pact(consumer = "hello-service", provider = "user-service")
    V4Pact userExists(PactDslWithProvider builder) {
        return builder
            .given("user 1 exists")
            .uponReceiving("get user by id")
            .path("/api/users/1")
            .method("GET")
            .willRespondWith()
            .status(200)
            .body(newJsonBody(b -> {
                b.integerType("id", 1);
                b.stringType("name", "Alice");
            }).build())
            .toPact(V4Pact.class);
    }

    @Test
    @PactTestFor(pactMethod = "userExists")
    void shouldParseUserResponse(MockServer mockServer) {
        var client = new UserServiceClient(mockServer.getUrl());
        var user = client.getUser(1L);
        assertThat(user.name()).isEqualTo("Alice");
    }
}
```

**Provider 端**（user-service 验证）：

```java
@Provider("user-service")
@PactBroker // 或 @PactFolder("pacts")
@SpringBootTest(webEnvironment = RANDOM_PORT)
class UserServiceProviderTest {

    @TestTemplate
    @ExtendWith(PactVerificationInvocationContextProvider.class)
    void verifyPact(PactVerificationContext context) {
        context.verifyInteraction();
    }

    @State("user 1 exists")
    void setupUser() {
        // 插入测试数据
    }
}
```

### 2.5 架构守护测试 — ArchUnit

```java
@AnalyzeClasses(packages = "com.example")
class ArchitectureRulesTest {

    // shared 模块不应依赖任何 service 模块
    @ArchTest
    static final ArchRule sharedModuleIndependence =
        noClasses().that().resideInAPackage("com.example.shared..")
            .should().dependOnClassesThat()
            .resideInAnyPackage(
                "com.example.hello..",
                "com.example.user..",
                "com.example.greeting.."
            );

    // Controller 不应直接访问 Repository
    @ArchTest
    static final ArchRule controllerDoesNotAccessRepository =
        noClasses().that().haveSimpleNameEndingWith("Controller")
            .should().dependOnClassesThat()
            .haveSimpleNameEndingWith("Repository");

    // 无循环依赖
    @ArchTest
    static final ArchRule noCycles =
        slices().matching("com.example.(*)..")
            .should().beFreeOfCycles();
}
```

## 3. Quality Harness

### 3.1 代码格式化 — Spotless

在根 `build.gradle.kts` 配置，对所有子模块生效：

```kotlin
plugins {
    id("com.diffplug.spotless") version "8.4.0"
}

spotless {
    java {
        target("**/*.java")
        googleJavaFormat("1.28.0")
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
    }
    kotlinGradle {
        ktlint()
    }
}
```

- `./gradlew spotlessCheck` — CI 中检查格式
- `./gradlew spotlessApply` — 本地自动修复

### 3.2 静态分析 — Error Prone

编译期 bug 检测，零运行时开销：

```kotlin
// root build.gradle.kts
plugins {
    id("net.ltgt.errorprone") version "5.1.0" apply false
}

subprojects {
    apply(plugin = "net.ltgt.errorprone")
    dependencies {
        "errorprone"("com.google.errorprone:error_prone_core:2.48.0")
    }
    tasks.withType<JavaCompile> {
        options.errorprone {
            disableWarningsInGeneratedCode = true
        }
    }
}
```

### 3.3 代码覆盖率 — JaCoCo

使用 Gradle 内置的 `jacoco-report-aggregation` 插件聚合多模块覆盖率：

```kotlin
// root build.gradle.kts
plugins {
    id("jacoco-report-aggregation")
}

dependencies {
    jacocoAggregation(project(":shared"))
    jacocoAggregation(project(":hello-service"))
    jacocoAggregation(project(":user-service"))
    jacocoAggregation(project(":greeting-service"))
}

reporting {
    reports {
        val testCodeCoverageReport by creating(JacocoCoverageReport::class) {
            testType = TestSuiteType.UNIT_TEST
        }
    }
}
```

聚合报告：`./gradlew testCodeCoverageReport`
输出位置：`build/reports/jacoco/testCodeCoverageReport/html/index.html`

### 3.4 覆盖率门禁

各服务模块配置最低覆盖率要求：

```kotlin
// 各 service 的 build.gradle.kts
jacoco {
    toolVersion = "0.8.15"
}

tasks.jacocoTestCoverageVerification {
    violationRules {
        rule {
            limit {
                minimum = "0.60".toBigDecimal() // 60% 行覆盖率
            }
        }
    }
}

tasks.check {
    dependsOn(tasks.jacocoTestCoverageVerification)
}
```

## 4. CI/CD Harness — GitHub Actions

### 4.1 PR 检查流水线

```yaml
# .github/workflows/ci.yml
name: CI

on:
  push:
    branches: [main]
  pull_request:
    branches: [main]

permissions:
  contents: read

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - uses: actions/setup-java@v5
        with:
          distribution: temurin
          java-version: '25'

      - uses: gradle/actions/setup-gradle@v6

      - name: Check code format
        run: ./gradlew spotlessCheck

      - name: Build & Test
        run: ./gradlew build

      - name: Coverage Report
        run: ./gradlew testCodeCoverageReport

      - name: Upload Coverage
        uses: actions/upload-artifact@v4
        with:
          name: coverage-report
          path: build/reports/jacoco/testCodeCoverageReport/html/
```

### 4.2 Gradle 缓存优化

在 `gradle.properties` 中启用：

```properties
org.gradle.caching=true
org.gradle.configuration-cache=true
```

`gradle/actions/setup-gradle@v6` 自动处理 Gradle User Home 缓存，无需额外配置。Configuration Cache 在 Gradle 9.x 中为推荐模式，可跳过重复的配置阶段，CI 构建时间预计减少 50-65%。

## 5. Container Harness

### 5.1 Dockerfile — Multi-stage Build

每个 service 一个 Dockerfile，统一模式：

```dockerfile
# hello-service/Dockerfile
FROM eclipse-temurin:25-jdk AS builder
WORKDIR /build
COPY gradle/ gradle/
COPY gradlew build.gradle.kts settings.gradle.kts gradle.properties ./
COPY shared/ shared/
COPY hello-service/ hello-service/
RUN ./gradlew :hello-service:bootJar --no-daemon

FROM eclipse-temurin:25-jre-alpine
WORKDIR /app
RUN addgroup -S app && adduser -S app -G app
USER app
COPY --from=builder /build/hello-service/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

### 5.2 完整 Docker Compose

扩展现有 `compose.yaml`，加入所有服务：

```yaml
services:
  grafana-otel-lgtm:
    image: grafana/otel-lgtm:latest
    ports:
      - "3000:3000"
      - "4317:4317"
      - "4318:4318"
    volumes:
      - lgtm-data:/var/lib/grafana
      - ./grafana/dashboards:/otel-lgtm/grafana/conf/provisioning/dashboards/custom
      - ./grafana/provisioning/dashboards.yaml:/otel-lgtm/grafana/conf/provisioning/dashboards/custom.yaml
    healthcheck:
      test: ["CMD", "wget", "-q", "--spider", "http://localhost:3000/api/health"]
      interval: 10s
      timeout: 5s
      retries: 3
      start_period: 30s

  hello-service:
    build: ./hello-service
    ports: ["8080:8080"]
    environment:
      - USER_SERVICE_URL=http://user-service:8081
      - GREETING_SERVICE_URL=http://greeting-service:8082
      - MANAGEMENT_OTLP_TRACING_ENDPOINT=http://grafana-otel-lgtm:4318/v1/traces
      - MANAGEMENT_OTLP_METRICS_EXPORT_URL=http://grafana-otel-lgtm:4318/v1/metrics
    depends_on:
      grafana-otel-lgtm: { condition: service_healthy }
      user-service: { condition: service_started }
      greeting-service: { condition: service_started }

  user-service:
    build: ./user-service
    ports: ["8081:8081"]
    environment:
      - MANAGEMENT_OTLP_TRACING_ENDPOINT=http://grafana-otel-lgtm:4318/v1/traces
      - MANAGEMENT_OTLP_METRICS_EXPORT_URL=http://grafana-otel-lgtm:4318/v1/metrics
    depends_on:
      grafana-otel-lgtm: { condition: service_healthy }

  greeting-service:
    build: ./greeting-service
    ports: ["8082:8082"]
    environment:
      - MANAGEMENT_OTLP_TRACING_ENDPOINT=http://grafana-otel-lgtm:4318/v1/traces
      - MANAGEMENT_OTLP_METRICS_EXPORT_URL=http://grafana-otel-lgtm:4318/v1/metrics
    depends_on:
      grafana-otel-lgtm: { condition: service_healthy }

volumes:
  lgtm-data:
```

一键启动：`docker compose up --build`

## 6. Observability Harness 增强

### 6.1 Dashboards as Code

```
grafana/
├── provisioning/
│   └── dashboards.yaml          # Grafana provisioning config
└── dashboards/
    ├── services-overview.json   # 服务总览：请求率、延迟 p50/p95/p99、错误率
    ├── jvm-metrics.json         # JVM 指标：堆内存、GC、线程
    └── traces-explorer.json     # 链路追踪浏览快捷入口
```

Provisioning 配置：

```yaml
# grafana/provisioning/dashboards.yaml
apiVersion: 1
providers:
  - name: custom
    type: file
    updateIntervalSeconds: 30
    options:
      path: /otel-lgtm/grafana/conf/provisioning/dashboards/custom
      foldersFromFilesStructure: true
```

### 6.2 Dashboard 设计要点

**Services Overview Dashboard** 核心 Panel：

| Panel | 数据源 | 查询 |
|-------|--------|------|
| Request Rate | Prometheus | `rate(http_server_requests_seconds_count[1m])` |
| Latency P95 | Prometheus | `histogram_quantile(0.95, rate(http_server_requests_seconds_bucket[5m]))` |
| Error Rate | Prometheus | `rate(http_server_requests_seconds_count{status=~"5.."}[1m])` |
| Active Spans | Tempo | TraceQL: `{resource.service.name =~ ".*-service"}` |

## 7. 目录结构变更

实施后的项目新增结构：

```
springboot3.5-otel/
├── .github/
│   └── workflows/
│       └── ci.yml
├── grafana/
│   ├── provisioning/
│   │   └── dashboards.yaml
│   └── dashboards/
│       ├── services-overview.json
│       └── jvm-metrics.json
├── hello-service/
│   ├── Dockerfile
│   └── src/test/java/...
├── user-service/
│   ├── Dockerfile
│   └── src/test/java/...
├── greeting-service/
│   ├── Dockerfile
│   └── src/test/java/...
├── shared/
│   └── src/test/java/...        # ArchUnit 架构测试
├── compose.yaml                  # 扩展：包含所有服务
├── build.gradle.kts              # 新增：Spotless, Error Prone, JaCoCo
└── gradle.properties             # 新增：caching, configuration-cache
```
