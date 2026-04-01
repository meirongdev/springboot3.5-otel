# Harness Engineering Implementation Plan

## 实施顺序

```
Phase H1 (Quality) → Phase H2 (Test) → Phase H3 (Container) → Phase H4 (CI/CD) → Phase H5 (Observability)
```

每个 Phase 独立可交付，完成后即可验证。

---

## Phase H1: Quality Harness

**目标**：建立代码质量基线，所有后续代码变更自动检查。

### H1.1 Spotless 代码格式化
- [ ] 根 `build.gradle.kts` 添加 `com.diffplug.spotless` 插件 (8.4.0)
- [ ] 配置 `googleJavaFormat("1.28.0")` + `removeUnusedImports` + `trimTrailingWhitespace`
- [ ] 配置 `kotlinGradle { ktlint() }` 对 `.gradle.kts` 文件格式化
- [ ] 运行 `./gradlew spotlessApply` 格式化现有代码
- [ ] 验证 `./gradlew spotlessCheck` 通过

### H1.2 Error Prone 静态分析
- [ ] 根 `build.gradle.kts` 添加 `net.ltgt.errorprone` 插件 (5.1.0)
- [ ] 各子模块添加 `errorprone("com.google.errorprone:error_prone_core:2.48.0")` 依赖
- [ ] 配置 `disableWarningsInGeneratedCode = true`
- [ ] 运行 `./gradlew compileJava` 验证无 Error Prone 报错
- [ ] 如有报错，逐一修复

### H1.3 JaCoCo 覆盖率
- [ ] 根 `build.gradle.kts` 添加 `jacoco-report-aggregation` 插件
- [ ] 添加 `jacocoAggregation` 依赖指向各子模块
- [ ] 各 service 模块 `build.gradle.kts` 配置 `jacoco` + `jacocoTestCoverageVerification`
- [ ] 设置初始覆盖率门禁：60% 行覆盖率
- [ ] 验证 `./gradlew testCodeCoverageReport` 生成聚合报告

### H1.4 Gradle 缓存
- [ ] `gradle.properties` 添加 `org.gradle.caching=true`
- [ ] `gradle.properties` 添加 `org.gradle.configuration-cache=true`
- [ ] 运行 `./gradlew build` 两次，确认第二次有缓存命中

**验证标准**：
- `./gradlew spotlessCheck compileJava build` 全部通过
- `build/reports/jacoco/` 下有聚合覆盖率 HTML 报告

---

## Phase H2: Test Harness

**目标**：建立三层测试体系（单元 → 集成 → 契约），覆盖关键路径。

### H2.1 依赖配置
- [ ] 根 `build.gradle.kts` 添加 Testcontainers BOM (`org.testcontainers:testcontainers-bom:2.0.4`)
- [ ] 各 service 模块添加测试依赖：
  ```
  testImplementation("org.testcontainers:testcontainers-junit-jupiter")
  testImplementation("org.springframework.boot:spring-boot-testcontainers")
  ```
- [ ] hello-service 和 user-service 添加 Pact 依赖：
  ```
  testImplementation("au.com.dius.pact.consumer:junit5:4.6.x")
  testImplementation("au.com.dius.pact.provider:junit5spring:4.6.x")
  ```
- [ ] shared 模块添加 ArchUnit：
  ```
  testImplementation("com.tngtech.archunit:archunit-junit5:1.4.1")
  ```

### H2.2 单元测试
- [ ] `GreetingControllerTest` — `@WebMvcTest`，验证多语言问候语返回
- [ ] `UserControllerTest` — `@WebMvcTest`，mock Repository，验证 CRUD
- [ ] `HelloControllerTest` — `@WebMvcTest`，mock HelloService
- [ ] `HelloServiceTest` — 纯 JUnit 5，mock 两个下游 client

### H2.3 集成测试
- [ ] `UserServiceIntegrationTest` — `@SpringBootTest` + H2，验证完整请求链路
- [ ] `HelloServiceIntegrationTest` — `@SpringBootTest` + WireMock 模拟下游服务
- [ ] `OtelExportIntegrationTest` — Testcontainers 启动 OTel Collector，验证 traces 导出

### H2.4 契约测试
- [ ] `UserServicePactConsumerTest`（在 hello-service 中）— 定义对 user-service 的 API 契约
- [ ] `GreetingServicePactConsumerTest`（在 hello-service 中）— 定义对 greeting-service 的 API 契约
- [ ] `UserServicePactProviderTest`（在 user-service 中）— 验证 user-service 满足契约
- [ ] `GreetingServicePactProviderTest`（在 greeting-service 中）— 验证 greeting-service 满足契约
- [ ] 配置 Pact 文件输出到 `build/pacts/`，Provider 端使用 `@PactFolder` 引用

### H2.5 架构守护测试
- [ ] `ArchitectureRulesTest`（在 shared 中）：
  - shared 模块不依赖任何 service 包
  - Controller 不直接依赖 Repository
  - 无循环依赖
  - 命名规范检查（`*Controller`、`*Service`、`*Repository`）

**验证标准**：
- `./gradlew test` 全部通过
- Pact 文件生成在 `build/pacts/` 目录
- 覆盖率 ≥ 60%

---

## Phase H3: Container Harness

**目标**：所有服务容器化，一键启动完整环境。

### H3.1 Dockerfile
- [ ] 创建 `hello-service/Dockerfile` — multi-stage build (temurin:25-jdk → temurin:25-jre-alpine)
- [ ] 创建 `user-service/Dockerfile` — 同上
- [ ] 创建 `greeting-service/Dockerfile` — 同上
- [ ] 每个 Dockerfile 均配置非 root 用户运行
- [ ] 验证 `docker build -t hello-service ./hello-service` 构建成功

### H3.2 Docker Compose 扩展
- [ ] `compose.yaml` 添加 hello-service、user-service、greeting-service 服务定义
- [ ] 配置服务间环境变量覆盖（service URL 指向容器名、OTel endpoint 指向 LGTM 容器）
- [ ] 配置 `depends_on` + healthcheck 确保启动顺序
- [ ] 挂载 Grafana dashboard 目录（Phase H5 产出）
- [ ] 验证 `docker compose up --build` 一键启动全部服务

### H3.3 .dockerignore
- [ ] 各 service 目录创建 `.dockerignore`，排除 `build/`、`.gradle/`、`*.md` 等

**验证标准**：
- `docker compose up --build` 成功启动
- `curl http://localhost:8080/api/1` 返回正确结果
- Grafana `http://localhost:3000` 可看到 traces

---

## Phase H4: CI/CD Harness

**目标**：PR 自动检查，主分支自动构建 Docker 镜像。

### H4.1 PR 检查流水线
- [ ] 创建 `.github/workflows/ci.yml`
- [ ] 配置 `actions/checkout@v4` + `actions/setup-java@v5` (temurin, java 25)
- [ ] 配置 `gradle/actions/setup-gradle@v6`（不要在 setup-java 中配置 cache）
- [ ] 步骤：spotlessCheck → build → testCodeCoverageReport
- [ ] Upload coverage report 为 artifact
- [ ] 验证 workflow 在 PR 中正确触发

### H4.2 Docker 构建流水线（可选）
- [ ] 创建 `.github/workflows/docker.yml`，仅在 main 分支 push 时触发
- [ ] 构建三个 service 的 Docker 镜像
- [ ] 推送到 GitHub Container Registry (ghcr.io)
- [ ] Tag 策略：`latest` + git SHA

**验证标准**：
- PR 检查流水线绿色通过
- Gradle 缓存命中率 > 0%

---

## Phase H5: Observability Harness 增强

**目标**：Dashboard as Code，一键获得完整可观测性视图。

### H5.1 Grafana Provisioning 配置
- [ ] 创建 `grafana/provisioning/dashboards.yaml`
- [ ] `compose.yaml` 中 LGTM 容器挂载 provisioning 目录

### H5.2 Dashboard JSON
- [ ] `grafana/dashboards/services-overview.json`：
  - Request Rate（按服务分组）
  - Latency P50/P95/P99（按服务分组）
  - Error Rate（5xx 比例）
  - 服务健康状态
- [ ] `grafana/dashboards/jvm-metrics.json`：
  - 堆内存使用量
  - GC 暂停时间
  - 活跃线程数
  - 类加载数

### H5.3 验证
- [ ] `docker compose up --build` 后 Grafana 自动加载自定义 dashboard
- [ ] Dashboard 中有数据展示（发送几个请求后）

**验证标准**：
- Grafana 首页显示自定义 dashboard
- 发送请求后 dashboard 有数据

---

## 依赖关系

```
H1 (Quality) ─────────────────────────────────► H4 (CI/CD)
                                                    ▲
H2 (Test) ──────────────────────────────────────────┘
                                                    
H3 (Container) ───────────────────────────────► H5 (Observability)
```

- H1 和 H2 互相独立，可并行
- H4 依赖 H1 + H2（CI 流水线运行格式检查和测试）
- H5 依赖 H3（Dashboard 挂载需要 Docker Compose 配置）

## 工作量估计

| Phase | 预计文件变更 | 复杂度 |
|-------|------------|--------|
| H1 Quality | 5-8 文件 | 低 |
| H2 Test | 15-20 文件 | 中高 |
| H3 Container | 6-8 文件 | 低 |
| H4 CI/CD | 1-2 文件 | 低 |
| H5 Observability | 4-5 文件 | 中 |
