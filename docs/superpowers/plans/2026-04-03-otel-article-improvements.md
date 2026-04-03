# OTel Collector & Evidence Harness Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an explicit OpenTelemetry Collector hop, standard resource attributes, environment-overridable sampling, and a stronger verification harness that emits reusable evidence for the Spring Boot 3.5 OTel article.

**Architecture:** Keep the three-service demo unchanged at the business layer. Route OTLP traffic from the Spring Boot services to a dedicated Collector with lightweight processors, then export to the existing LGTM container and validate the full path with the shell-based harness.

**Tech Stack:** Docker Compose, OpenTelemetry Collector, Spring Boot 3.5 `application.yaml`, Bash + `curl` + `jq`, Grafana OTEL LGTM, Gradle, Make

---

## File map

- `compose.yaml` — define the `otel-collector` service and reroute service OTLP environment variables away from `grafana-otel-lgtm`
- `grafana/otel-collector-config.yaml` — own the Collector receivers/processors/exporters/pipelines
- `shared/src/main/resources/application.yaml` — shared defaults for OTLP sampling and resource attributes
- `hello-service/src/main/resources/application.yaml` — keep service-specific settings and inherit the new OTel defaults
- `user-service/src/main/resources/application.yaml` — same as above for `user-service`
- `greeting-service/src/main/resources/application.yaml` — same as above for `greeting-service`
- `scripts/verify-otel.sh` — add Collector validation, resource-attribute checks, and report generation
- `README.md` — document the new runtime topology and evidence output
- `docs/VERIFICATION-HARNESS.md` — document the stronger verification contract and report file
- `docs/SPRINGBOOT-OTEL-RECOMMENDATION.md` — align the recommendation doc with the now-real Collector topology and resource attributes

### Task 1: Add an explicit OpenTelemetry Collector hop

**Files:**
- Create: `grafana/otel-collector-config.yaml`
- Modify: `compose.yaml:1-99`
- Test: `docker compose config`

- [ ] **Step 1: Define the failing infrastructure check**

```bash
docker compose config | rg -n "otel-collector|http://otel-collector:4318"
```

Expected: no matches, because the current Compose file has no `otel-collector` service and still points every OTLP endpoint at `grafana-otel-lgtm`.

- [ ] **Step 2: Run the check to confirm the gap**

Run:

```bash
docker compose config | rg -n "otel-collector|http://otel-collector:4318"
```

Expected: exit code `1`.

- [ ] **Step 3: Create the Collector config**

Create `grafana/otel-collector-config.yaml` with this content:

```yaml
receivers:
  otlp:
    protocols:
      http:
        endpoint: 0.0.0.0:4318

processors:
  memory_limiter:
    check_interval: 1s
    limit_mib: 256
    spike_limit_mib: 64
  batch:
    timeout: 5s
    send_batch_size: 1024
  resource/defaults:
    attributes:
      - key: service.namespace
        action: upsert
        value: springboot3.5-otel
      - key: deployment.environment
        action: upsert
        value: ${env:OTEL_DEPLOYMENT_ENVIRONMENT}
  attributes/sanitize:
    actions:
      - key: user.id
        action: delete
      - key: user.email
        action: delete

exporters:
  otlphttp/lgtm:
    endpoint: http://grafana-otel-lgtm:4318

extensions:
  health_check:
    endpoint: 0.0.0.0:13133

service:
  extensions: [health_check]
  pipelines:
    traces:
      receivers: [otlp]
      processors: [memory_limiter, resource/defaults, attributes/sanitize, batch]
      exporters: [otlphttp/lgtm]
    metrics:
      receivers: [otlp]
      processors: [memory_limiter, resource/defaults, attributes/sanitize, batch]
      exporters: [otlphttp/lgtm]
    logs:
      receivers: [otlp]
      processors: [memory_limiter, resource/defaults, attributes/sanitize, batch]
      exporters: [otlphttp/lgtm]
```

- [ ] **Step 4: Wire the Collector into Compose**

Update `compose.yaml` so it includes a dedicated Collector and reroutes the service OTLP endpoints:

```yaml
  otel-collector:
    image: otel/opentelemetry-collector-contrib:0.124.1
    container_name: otel-collector
    command: ["--config=/etc/otelcol/config.yaml"]
    environment:
      - OTEL_DEPLOYMENT_ENVIRONMENT=docker-compose
    volumes:
      - ./grafana/otel-collector-config.yaml:/etc/otelcol/config.yaml:ro
    ports:
      - "13133:13133"
    depends_on:
      grafana-otel-lgtm:
        condition: service_healthy

  hello-service:
    environment:
      - MANAGEMENT_OTLP_TRACING_ENDPOINT=http://otel-collector:4318/v1/traces
      - MANAGEMENT_OTLP_METRICS_EXPORT_URL=http://otel-collector:4318/v1/metrics
      - MANAGEMENT_OTLP_LOGGING_ENDPOINT=http://otel-collector:4318/v1/logs

  user-service:
    environment:
      - MANAGEMENT_OTLP_TRACING_ENDPOINT=http://otel-collector:4318/v1/traces
      - MANAGEMENT_OTLP_METRICS_EXPORT_URL=http://otel-collector:4318/v1/metrics
      - MANAGEMENT_OTLP_LOGGING_ENDPOINT=http://otel-collector:4318/v1/logs

  greeting-service:
    environment:
      - MANAGEMENT_OTLP_TRACING_ENDPOINT=http://otel-collector:4318/v1/traces
      - MANAGEMENT_OTLP_METRICS_EXPORT_URL=http://otel-collector:4318/v1/metrics
      - MANAGEMENT_OTLP_LOGGING_ENDPOINT=http://otel-collector:4318/v1/logs
```

Also make each application service depend on `otel-collector` instead of sending directly to LGTM.

- [ ] **Step 5: Re-run the Compose check**

Run:

```bash
docker compose config | rg -n "otel-collector|http://otel-collector:4318"
```

Expected: matches for the new `otel-collector` service and OTLP endpoints for all three apps.

- [ ] **Step 6: Commit the infrastructure change**

```bash
git add compose.yaml grafana/otel-collector-config.yaml
git commit -m "feat: add otel collector hop" -m "Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>"
```

### Task 2: Add resource attributes and environment-driven sampling

**Files:**
- Modify: `shared/src/main/resources/application.yaml:1-29`
- Modify: `hello-service/src/main/resources/application.yaml:1-47`
- Modify: `user-service/src/main/resources/application.yaml:1-54`
- Modify: `greeting-service/src/main/resources/application.yaml:1-39`
- Modify: `compose.yaml:24-95`
- Test: `rg`, `./gradlew :hello-service:test --tests "*HelloServiceIntegrationTest"`, `./gradlew :user-service:test --tests "*UserServiceIntegrationTest"`, `./gradlew :greeting-service:test --tests "*GreetingControllerTest"`

- [ ] **Step 1: Define the failing configuration check**

```bash
rg -n "resource-attributes|OTEL_TRACING_SAMPLING_PROBABILITY|OTEL_DEPLOYMENT_ENVIRONMENT|OTEL_SERVICE_VERSION" \
  shared/src/main/resources/application.yaml \
  hello-service/src/main/resources/application.yaml \
  user-service/src/main/resources/application.yaml \
  greeting-service/src/main/resources/application.yaml \
  compose.yaml
```

Expected: no `resource-attributes` block and no explicit environment-driven sampling variables.

- [ ] **Step 2: Run the check to confirm the gap**

Run the command from Step 1.

Expected: exit code `1`.

- [ ] **Step 3: Add shared OTel defaults**

Update `shared/src/main/resources/application.yaml` so the OTel section becomes:

```yaml
management:
  otlp:
    tracing:
      endpoint: http://localhost:4318/v1/traces
    metrics:
      export:
        url: http://localhost:4318/v1/metrics
        step: 10s
    logging:
      endpoint: http://localhost:4318/v1/logs
  tracing:
    sampling:
      probability: ${OTEL_TRACING_SAMPLING_PROBABILITY:1.0}
  observations:
    annotations:
      enabled: true
  opentelemetry:
    resource-attributes:
      service.name: ${spring.application.name}
      service.namespace: ${OTEL_SERVICE_NAMESPACE:springboot3.5-otel}
      service.version: ${OTEL_SERVICE_VERSION:1.0.0}
      deployment.environment: ${OTEL_DEPLOYMENT_ENVIRONMENT:local}
```

- [ ] **Step 4: Keep each service config aligned with the shared defaults**

Update each service `application.yaml` to mirror the same sampling/resource-attribute block. The service-specific version should look like:

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  endpoint:
    health:
      show-details: always
  otlp:
    tracing:
      endpoint: http://localhost:4318/v1/traces
    metrics:
      export:
        url: http://localhost:4318/v1/metrics
        step: 10s
    logging:
      endpoint: http://localhost:4318/v1/logs
  tracing:
    sampling:
      probability: ${OTEL_TRACING_SAMPLING_PROBABILITY:1.0}
  observations:
    annotations:
      enabled: true
  opentelemetry:
    resource-attributes:
      service.name: ${spring.application.name}
      service.namespace: ${OTEL_SERVICE_NAMESPACE:springboot3.5-otel}
      service.version: ${OTEL_SERVICE_VERSION:1.0.0}
      deployment.environment: ${OTEL_DEPLOYMENT_ENVIRONMENT:local}
```

- [ ] **Step 5: Add the environment variables in Compose**

For each application service in `compose.yaml`, add:

```yaml
      - OTEL_SERVICE_NAMESPACE=springboot3.5-otel
      - OTEL_SERVICE_VERSION=1.0.0
      - OTEL_DEPLOYMENT_ENVIRONMENT=docker-compose
      - OTEL_TRACING_SAMPLING_PROBABILITY=1.0
```

- [ ] **Step 6: Re-run the config checks and targeted app tests**

Run:

```bash
rg -n "resource-attributes|OTEL_TRACING_SAMPLING_PROBABILITY|OTEL_DEPLOYMENT_ENVIRONMENT|OTEL_SERVICE_VERSION" \
  shared/src/main/resources/application.yaml \
  hello-service/src/main/resources/application.yaml \
  user-service/src/main/resources/application.yaml \
  greeting-service/src/main/resources/application.yaml \
  compose.yaml && \
./gradlew :hello-service:test --tests "*HelloServiceIntegrationTest" && \
./gradlew :user-service:test --tests "*UserServiceIntegrationTest" && \
./gradlew :greeting-service:test --tests "*GreetingControllerTest"
```

Expected: `rg` prints the new configuration lines and all three targeted tests pass.

- [ ] **Step 7: Commit the configuration change**

```bash
git add compose.yaml \
  shared/src/main/resources/application.yaml \
  hello-service/src/main/resources/application.yaml \
  user-service/src/main/resources/application.yaml \
  greeting-service/src/main/resources/application.yaml
git commit -m "feat: add otel resource metadata defaults" -m "Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>"
```

### Task 3: Upgrade `verify-otel.sh` into an evidence generator

**Files:**
- Modify: `scripts/verify-otel.sh:1-358`
- Test: `./scripts/verify-otel.sh --wait`, `jq -e ... build/reports/otel/verification-report.json`

- [ ] **Step 1: Define the failing evidence check**

```bash
rm -rf build/reports/otel
./scripts/verify-otel.sh --wait && test -f build/reports/otel/verification-report.json
```

Expected: the script may pass some smoke checks, but `test -f` fails because no evidence file exists yet.

- [ ] **Step 2: Run the failing check**

Run the command from Step 1.

Expected: exit code `1`.

- [ ] **Step 3: Add Collector-aware verification helpers**

Extend `scripts/verify-otel.sh` with these functions near the current query helpers:

```bash
REPORT_DIR="build/reports/otel"
REPORT_FILE="$REPORT_DIR/verification-report.json"
COLLECTOR_HEALTH_URL="http://localhost:13133"
REQUIRED_RESOURCE_ATTRIBUTES=("service.name" "service.namespace" "service.version" "deployment.environment")
COLLECTOR_STATUS="unverified"
SERVICES_REPORT_JSON='[]'

query_tempo_trace() {
    local trace_id="$1"
    docker compose exec grafana-otel-lgtm curl -s "http://localhost:3200/api/traces/$trace_id" 2>/dev/null
}

verify_collector() {
    log_info "Verifying Collector availability..."
    if curl -s "$COLLECTOR_HEALTH_URL/" > /dev/null 2>&1; then
        COLLECTOR_STATUS="healthy"
        log_success "Collector is accessible"
        return 0
    fi
    COLLECTOR_STATUS="unhealthy"
    log_error "Collector is not accessible at $COLLECTOR_HEALTH_URL"
    return 1
}
```

- [ ] **Step 4: Add resource-attribute verification and report writing**

Implement a report writer and a resource-attribute check using the latest trace per service:

```bash
verify_required_resource_attributes() {
    local service="$1"
    local trace_id="$2"
    local trace_json
    trace_json=$(query_tempo_trace "$trace_id")

    for attribute in "${REQUIRED_RESOURCE_ATTRIBUTES[@]}"; do
        if ! echo "$trace_json" | jq -e --arg key "$attribute" '
            .. | objects | select(has("attributes")) | .attributes[]? |
            select(.key == $key)
        ' > /dev/null; then
            log_error "  $service: missing resource attribute $attribute"
            return 1
        fi
    done

    log_success "  $service: required resource attributes found"
    return 0
}

verify_resource_attributes() {
    local all_passed=true
    SERVICES_REPORT_JSON='[]'

    for service in "${SERVICES[@]}"; do
        local search_result trace_id trace_json resource_attributes_json
        search_result=$(query_tempo "$service")
        trace_id=$(echo "$search_result" | jq -r '.traces[0].traceID // empty')

        if [ -z "$trace_id" ]; then
            log_error "  $service: no trace available for resource-attribute verification"
            all_passed=false
            continue
        fi

        trace_json=$(query_tempo_trace "$trace_id")
        resource_attributes_json=$(echo "$trace_json" | jq '
          [
            .. | objects | select(has("attributes")) | .attributes[]? |
            select(.key == "service.name" or .key == "service.namespace" or .key == "service.version" or .key == "deployment.environment") |
            {key: .key, value: (.value.stringValue // .value.intValue // .value.boolValue // .value.doubleValue)}
          ] | from_entries
        ')

        if verify_required_resource_attributes "$service" "$trace_id"; then
            SERVICES_REPORT_JSON=$(echo "$SERVICES_REPORT_JSON" | jq \
              --arg serviceName "$service" \
              --arg sampleTraceId "$trace_id" \
              --argjson resourceAttributes "$resource_attributes_json" \
              '. + [{serviceName: $serviceName, sampleTraceId: $sampleTraceId, resourceAttributes: $resourceAttributes}]')
        else
            all_passed=false
        fi
    done

    [ "$all_passed" = true ]
}

write_report() {
    mkdir -p "$REPORT_DIR"
    jq -n \
      --arg generatedAt "$(date -u +%FT%TZ)" \
      --arg collectorStatus "$COLLECTOR_STATUS" \
      --argjson services "$SERVICES_REPORT_JSON" \
      '{
        generatedAt: $generatedAt,
        collector: {status: $collectorStatus},
        services: $services
      }' > "$REPORT_FILE"
    log_success "Evidence report written to $REPORT_FILE"
}
```

- [ ] **Step 5: Thread the new checks through main execution**

Update `main()` so the execution order is:

```bash
verify_collector || collector_result=1
generate_traffic
verify_metrics || metrics_result=1
verify_traces || traces_result=1
verify_logs || logs_result=1
verify_distributed_tracing || distributed_result=1
verify_resource_attributes || resource_result=1
write_report
print_summary $collector_result $metrics_result $traces_result $logs_result $distributed_result $resource_result
```

Also make `print_summary()` fail the run when `collector_result` or `resource_result` is non-zero.

- [ ] **Step 6: Re-run the harness and assert the report shape**

Run:

```bash
./scripts/verify-otel.sh --wait && \
jq -e '
  .collector.status == "healthy" and
  (.services | length == 3) and
  all(.services[]; .resourceAttributes["deployment.environment"] == "docker-compose")
' build/reports/otel/verification-report.json
```

Expected: the script passes, the report file exists, and the `jq` assertion returns exit code `0`.

- [ ] **Step 7: Commit the harness change**

```bash
git add scripts/verify-otel.sh
git commit -m "feat: generate otel verification evidence" -m "Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>"
```

### Task 4: Update the docs to match the running system

**Files:**
- Modify: `README.md:7-27,54-95,133-154,196-256`
- Modify: `docs/VERIFICATION-HARNESS.md:1-227`
- Modify: `docs/SPRINGBOOT-OTEL-RECOMMENDATION.md:31-59,119-176`
- Test: `rg -n "otel-collector|resource-attributes|verification-report.json|deployment.environment" README.md docs/VERIFICATION-HARNESS.md docs/SPRINGBOOT-OTEL-RECOMMENDATION.md`

- [ ] **Step 1: Define the failing documentation check**

```bash
rg -n "verification-report.json|resource-attributes|otel-collector -> grafana-otel-lgtm" \
  README.md \
  docs/VERIFICATION-HARNESS.md \
  docs/SPRINGBOOT-OTEL-RECOMMENDATION.md
```

Expected: no match for the new evidence file and no explicit end-to-end `otel-collector -> grafana-otel-lgtm` topology statement in all three docs.

- [ ] **Step 2: Run the check to confirm the docs gap**

Run the command from Step 1.

Expected: exit code `1`.

- [ ] **Step 3: Update `README.md`**

Apply these documentation changes:

```markdown
- 在架构图和 Quick Start 中把导出链路改成：All services -> OTLP -> otel-collector -> grafana-otel-lgtm
- 在 YAML 配置示例里加入 `management.opentelemetry.resource-attributes`
- 在验证章节加入结果文件路径：`build/reports/otel/verification-report.json`
```

- [ ] **Step 4: Update `docs/VERIFICATION-HARNESS.md` and `docs/SPRINGBOOT-OTEL-RECOMMENDATION.md`**

Document the stronger contract explicitly:

```markdown
- Collector availability is now a required check
- Required resource attributes are now a required check
- Logs in Loki remain advisory
- The harness writes a machine-readable report under `build/reports/otel/`
- Production guidance now matches the actual Collector topology used by Compose
```

- [ ] **Step 5: Re-run the documentation check**

Run:

```bash
rg -n "verification-report.json|resource-attributes|otel-collector|deployment.environment" \
  README.md \
  docs/VERIFICATION-HARNESS.md \
  docs/SPRINGBOOT-OTEL-RECOMMENDATION.md
```

Expected: matches in all three updated files.

- [ ] **Step 6: Commit the docs update**

```bash
git add README.md docs/VERIFICATION-HARNESS.md docs/SPRINGBOOT-OTEL-RECOMMENDATION.md
git commit -m "docs: document collector-backed otel verification" -m "Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>"
```

### Task 5: Run the full validation flow and capture the final evidence

**Files:**
- No source changes expected
- Test: `./gradlew clean build`, `docker compose up -d --build`, `make verify-otel-wait`, `jq ... build/reports/otel/verification-report.json`

- [ ] **Step 1: Run the full Gradle quality gate**

Run:

```bash
./gradlew clean build
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2: Start the updated stack**

Run:

```bash
docker compose down && docker compose up -d --build
```

Expected: `grafana-otel-lgtm`, `otel-collector`, `hello-service`, `user-service`, and `greeting-service` all reach a running state.

- [ ] **Step 3: Run the end-to-end telemetry verification**

Run:

```bash
make verify-otel-wait
```

Expected: the summary shows Collector, Metrics, Traces, and Resource Attributes as passed; logs may be pass or warning.

- [ ] **Step 4: Inspect the final evidence file**

Run:

```bash
jq '{collector, services: [.services[] | {serviceName: .serviceName, sampleTraceId: .sampleTraceId, resourceAttributes: .resourceAttributes}]}' \
  build/reports/otel/verification-report.json
```

Expected: a JSON summary covering all three services and showing `deployment.environment`, `service.namespace`, and `service.version`.

- [ ] **Step 5: Shut the stack down cleanly after validation**

Run:

```bash
docker compose down
```

Expected: all containers stop without errors.

## Self-review checklist

- Spec coverage: Task 1 covers the explicit Collector hop; Task 2 covers resource attributes and environment-driven sampling; Task 3 covers stronger verification and evidence output; Task 4 aligns the docs; Task 5 validates the whole system end to end.
- Placeholder scan: no `TODO`, `TBD`, or “similar to above” shortcuts remain.
- Type consistency: property names stay consistent across YAML, Compose, and the evidence report (`OTEL_TRACING_SAMPLING_PROBABILITY`, `OTEL_DEPLOYMENT_ENVIRONMENT`, `service.namespace`, `deployment.environment`, `verification-report.json`).
