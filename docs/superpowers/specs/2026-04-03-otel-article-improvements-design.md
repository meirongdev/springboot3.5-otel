# OTel article improvements design

Date: 2026-04-03

## Problem

The repository already demonstrates Spring Boot 3.5 + Java 25 + OpenTelemetry well, but several points presented in the article `spring-boot-35-otel-best-practices` are still stronger in prose than in code:

- applications export directly to `grafana-otel-lgtm` instead of an explicit OpenTelemetry Collector
- standard OpenTelemetry resource attributes are not configured
- sampling is hard-coded to `1.0` instead of being environment-driven
- the verification harness proves telemetry exists, but does not verify Collector routing or required resource attributes, and does not emit a reusable evidence report

This work should close those gaps with the smallest change set that still creates a complete, verifiable story for both the project and the article.

## Goals

1. Add an explicit `otel-collector` hop between the services and LGTM.
2. Configure standard resource attributes in service configuration.
3. Make sampling configurable by environment while keeping local development defaults simple.
4. Upgrade the verification harness so it can validate the Collector-based path and export structured evidence that can be reused in the article.

## Non-goals

- no changes to service business APIs or orchestration logic
- no tail-based sampling in this iteration
- no new backend beyond Collector + existing LGTM stack
- no Native Image work in this iteration
- no heavy performance benchmark harness in this iteration

## Recommended approach

Implement a minimal-but-complete observability hardening pass:

1. introduce a dedicated `otel-collector` service in `compose.yaml`
2. route all OTLP traffic from the Spring Boot services to the Collector
3. configure resource attributes and environment-overridable sampling in application config
4. extend `scripts/verify-otel.sh` so verification becomes an evidence generator instead of only a smoke check

This keeps the business code stable while making the article claims about Agentless + Collector + resource attributes + environment-aware sampling true in the running system.

## Design

### 1. Runtime topology

Current:

`hello-service | user-service | greeting-service -> grafana-otel-lgtm`

Target:

`hello-service | user-service | greeting-service -> otel-collector -> grafana-otel-lgtm`

The Collector will receive OTLP/HTTP on port `4318` and export traces, metrics, and logs to the existing LGTM container. The LGTM container remains the only backend visible to Grafana users.

### 2. Collector responsibilities

The Collector configuration should stay intentionally lightweight:

- `memory_limiter` to avoid unbounded buffering
- `batch` to improve export efficiency
- `resource` processor to add or normalize shared metadata when needed
- `attributes` processor with conservative defaults for removing or redacting high-risk keys if they appear later

This iteration deliberately avoids tail sampling and complex routing. The purpose is to make the deployment topology and article guidance concrete without making the local demo fragile.

### 3. Service configuration changes

Each service configuration should include standard resource attributes:

- `service.name`
- `service.namespace`
- `service.version`
- `deployment.environment`

Sampling should be configurable through a property placeholder so local development can stay at `1.0` while production examples can override the value through environment variables.

The default outcome should be:

- local/dev: full sampling
- production-like runs: lower sampling via environment override, without changing source code

### 4. Verification harness changes

`scripts/verify-otel.sh` should move from existence checks to evidence-oriented verification:

- confirm Collector reachability
- confirm the services are configured to send OTLP traffic to the Collector
- generate test traffic
- verify traces and metrics are still observable through LGTM after the Collector hop
- verify required resource attributes are present in exported telemetry
- keep logs verification explicit but tolerant: missing logs in Loki remains a warning if trace-context logging is still present in service logs

The script should produce:

- terminal summary for humans
- machine-readable report file under `build/reports/otel/`

The report should include per-service counts and sample identifiers that can be cited in the article, such as:

- services seen
- traces found
- metrics series found
- sample trace IDs
- required resource attributes discovered

### 5. Failure handling

Verification should fail fast and exit non-zero when any of the following are true:

- Collector is unavailable
- no traces are found after traffic generation
- no metrics are found for a service
- required resource attributes are missing

Logs remain advisory in this iteration:

- trace/span correlation missing in application logs => warning or failure depending on implementation simplicity
- Loki not containing recent OTLP logs => warning, not a hard fail

### 6. Files expected to change

- `compose.yaml`
- new Collector config file under a repository-owned config path
- `shared/src/main/resources/application.yaml`
- `hello-service/src/main/resources/application.yaml`
- `user-service/src/main/resources/application.yaml`
- `greeting-service/src/main/resources/application.yaml`
- `scripts/verify-otel.sh`
- directly related docs that describe the runtime topology and verification output

## Testing and validation plan

1. Run existing formatting, tests, and build checks to preserve baseline quality gates.
2. Start the updated observability stack and services.
3. Run the upgraded verification harness.
4. Confirm the structured evidence file is generated.
5. Use the evidence output as the validation artifact for the article-supporting claims.

## Acceptance criteria

- OTLP traffic flows through an explicit Collector in Docker Compose
- all services publish standard resource attributes
- sampling is source-controlled but environment-overridable
- verification fails when Collector or required resource attributes are missing
- verification emits a reusable evidence report
- existing build and test workflows continue to work
