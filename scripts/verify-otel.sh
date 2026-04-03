#!/bin/bash
#
# OpenTelemetry verification evidence generator.
#
# Usage: ./scripts/verify-otel.sh [--verbose] [--wait]
#

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "$REPO_ROOT"

GRAFANA_URL="http://localhost:3000"
REPORT_DIR="$REPO_ROOT/build/reports/otel"
REPORT_FILE="$REPORT_DIR/verification-report.json"
MAX_WAIT=60
SERVICES=("hello-service" "user-service" "greeting-service")
RUNTIME_SERVICES=("grafana-otel-lgtm" "otel-collector" "hello-service" "user-service" "greeting-service")

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

VERBOSE=false
WAIT_READY=false
GENERATED_AT="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
COMPOSE_CONFIG_JSON=""
COLLECTOR_REPORT='{"status":"unverified","endpoint":"http://otel-collector:13133/","checkedVia":"grafana-otel-lgtm"}'
LOGS_REPORT='{"status":"warning","message":"Log verification not yet executed."}'
declare -a SERVICE_REPORTS=()

while [[ $# -gt 0 ]]; do
  case "$1" in
    --verbose|-v)
      VERBOSE=true
      shift
      ;;
    --wait|-w)
      WAIT_READY=true
      shift
      ;;
    *)
      echo "Unknown option: $1"
      echo "Usage: $0 [--verbose] [--wait]"
      exit 1
      ;;
  esac
done

log_info() {
  echo -e "${BLUE}[INFO]${NC} $1" >&2
}

log_success() {
  echo -e "${GREEN}[PASS]${NC} $1" >&2
}

log_warning() {
  echo -e "${YELLOW}[WARN]${NC} $1" >&2
}

log_error() {
  echo -e "${RED}[FAIL]${NC} $1" >&2
}

die() {
  log_error "$1"
  exit 1
}

log_verbose() {
  if [ "$VERBOSE" = true ]; then
    echo -e "${BLUE}[DEBUG]${NC} $1" >&2
  fi
}

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    log_error "Missing required command: $1"
    exit 1
  fi
}

wait_until() {
  local description="$1"
  shift
  local elapsed=0

  until "$@"; do
    if [ "$elapsed" -ge "$MAX_WAIT" ]; then
      log_error "$description not ready after ${MAX_WAIT}s"
      return 1
    fi

    sleep 2
    elapsed=$((elapsed + 2))
    log_verbose "  Waiting for $description... (${elapsed}s/${MAX_WAIT}s)"
  done

  return 0
}

trim_output() {
  printf '%s' "${1:-}" | tr '\n' ' ' | sed 's/[[:space:]]\+/ /g' | cut -c1-200
}

compose_service_running() {
  local service="$1"
  docker compose ps --status running --services 2>/dev/null | grep -qx "$service"
}

all_compose_services_running() {
  local service
  for service in "${RUNTIME_SERVICES[@]}"; do
    compose_service_running "$service" || return 1
  done
}

http_ready() {
  local url="$1"
  curl -fsS "$url" >/dev/null 2>&1
}

collector_ready() {
  docker compose exec -T grafana-otel-lgtm sh -lc "curl -fsS http://otel-collector:13133/" >/dev/null 2>&1
}

all_application_endpoints_ready() {
  http_ready "${GRAFANA_URL}/api/health" \
    && http_ready "http://localhost:8080/actuator/health" \
    && http_ready "http://localhost:8081/actuator/health" \
    && http_ready "http://localhost:8082/actuator/health"
}

query_prometheus() {
  local query="$1"
  docker compose exec -T grafana-otel-lgtm curl -fsS -G "http://localhost:9090/api/v1/query" \
    --data-urlencode "query=$query" 2>/dev/null
}

query_prometheus_series() {
  local matcher="$1"
  docker compose exec -T grafana-otel-lgtm curl -fsS "http://localhost:9090/api/v1/series" \
    --data-urlencode "$matcher" 2>/dev/null
}

query_tempo_search() {
  local service="$1"
  local encoded_query
  encoded_query="$(jq -rn --arg q "{.service.name=\"${service}\"}" '$q|@uri')"
  docker compose exec -T grafana-otel-lgtm curl -fsS "http://localhost:3200/api/search?q=${encoded_query}&limit=1" 2>/dev/null
}

query_tempo_trace() {
  local trace_id="$1"
  docker compose exec -T grafana-otel-lgtm curl -fsS "http://localhost:3200/api/traces/${trace_id}" 2>/dev/null
}

query_loki_service_labels() {
  docker compose exec -T grafana-otel-lgtm curl -fsS "http://localhost:3100/loki/api/v1/label/service_name/values" 2>/dev/null
}

compose_env() {
  local service="$1"
  local key="$2"
  jq -r --arg service "$service" --arg key "$key" '.services[$service].environment[$key] // empty' <<<"$COMPOSE_CONFIG_JSON"
}

json_array_from_lines() {
  if [ $# -eq 0 ]; then
    jq -cn '[]'
  else
    printf '%s\n' "$@" | jq -Rcs 'split("\n")[:-1]'
  fi
}

normalize_number() {
  local value="${1:-0}"
  value="$(printf '%s' "$value" | tr -d '\r' | awk 'NF {print $1; exit}')"
  if [ -z "$value" ]; then
    echo "0"
  else
    echo "$value"
  fi
}

prepare_runtime() {
  local runtime_ready=true
  local service

  if [ "$WAIT_READY" = true ]; then
    wait_until "compose services" all_compose_services_running || runtime_ready=false
  else
    all_compose_services_running || runtime_ready=false
  fi

  for service in "${RUNTIME_SERVICES[@]}"; do
    if compose_service_running "$service"; then
      log_success "Compose service running: ${service}"
    else
      log_error "Compose service not running in this worktree: ${service}"
      runtime_ready=false
    fi
  done

  if [ "$runtime_ready" != true ]; then
    return 1
  fi

  if [ "$WAIT_READY" = true ]; then
    wait_until "application endpoints" all_application_endpoints_ready || runtime_ready=false
  else
    all_application_endpoints_ready || runtime_ready=false
  fi

  if [ "$runtime_ready" = true ]; then
    log_success "Application endpoints are reachable"
    return 0
  fi

  log_error "One or more application endpoints are not reachable"
  return 1
}

verify_collector() {
  local collector_body=""
  local status="unavailable"
  local message="Collector health endpoint could not be reached from the Compose network."

  if [ "$WAIT_READY" = true ] && compose_service_running "grafana-otel-lgtm"; then
    wait_until "collector health endpoint" collector_ready || true
  fi

  if collector_body="$(docker compose exec -T grafana-otel-lgtm sh -lc "curl -fsS http://otel-collector:13133/" 2>/dev/null)"; then
    status="healthy"
    message="Collector health endpoint reachable from grafana-otel-lgtm."
    log_success "Collector reachable from Compose network"
  else
    log_error "Collector is not reachable from grafana-otel-lgtm via http://otel-collector:13133/"
  fi

  COLLECTOR_REPORT="$(jq -cn \
    --arg status "$status" \
    --arg endpoint "http://otel-collector:13133/" \
    --arg checkedVia "grafana-otel-lgtm" \
    --arg message "$message" \
    --arg response "$(trim_output "$collector_body")" \
    '{
      status: $status,
      endpoint: $endpoint,
      checkedVia: $checkedVia,
      message: $message,
      response: (if $response == "" then null else $response end)
    }')"
}

generate_traffic() {
  log_info "Generating verification traffic..."

  curl -fsS "http://localhost:8080/api/1" >/dev/null 2>&1 || true
  curl -fsS -H "Accept-Language: zh" "http://localhost:8080/api/1" >/dev/null 2>&1 || true
  curl -fsS -H "Accept-Language: ja" "http://localhost:8080/api/1" >/dev/null 2>&1 || true

  sleep 5
  log_success "Verification traffic generated"
}

collect_service_evidence() {
  local service="$1"
  local expected_trace_endpoint="http://otel-collector:4318/v1/traces"
  local expected_metrics_endpoint="http://otel-collector:4318/v1/metrics"
  local expected_logs_endpoint="http://otel-collector:4318/v1/logs"
  local trace_endpoint metrics_endpoint logs_endpoint namespace version deployment
  local config_status="valid"
  local config_issues=()
  local config_advisories=()
  local expected_attrs_json
  local metric_series_count="0"
  local request_metric_series_count="0"
  local trace_count="0"
  local sample_trace_id=""
  local trace_services_json="{}"
  local resource_validation_json='{"actual":{},"missing":[],"valid":false}'
  local trace_search_response=""
  local resource_series_response=""
  local resource_status="invalid"
  local service_status="failed"
  local config_issues_json='[]'
  local config_advisories_json='[]'

  trace_endpoint="$(compose_env "$service" "MANAGEMENT_OTLP_TRACING_ENDPOINT")"
  metrics_endpoint="$(compose_env "$service" "MANAGEMENT_OTLP_METRICS_EXPORT_URL")"
  logs_endpoint="$(compose_env "$service" "MANAGEMENT_OTLP_LOGGING_ENDPOINT")"
  namespace="$(compose_env "$service" "OTEL_SERVICE_NAMESPACE")"
  version="$(compose_env "$service" "OTEL_SERVICE_VERSION")"
  deployment="$(compose_env "$service" "OTEL_DEPLOYMENT_ENVIRONMENT")"

  if [ "$trace_endpoint" != "$expected_trace_endpoint" ]; then
    config_status="invalid"
    config_issues+=("traces-endpoint")
  fi

  if [ "$metrics_endpoint" != "$expected_metrics_endpoint" ]; then
    config_status="invalid"
    config_issues+=("metrics-endpoint")
  fi

  if [ "$logs_endpoint" != "$expected_logs_endpoint" ]; then
    config_advisories+=("logs-endpoint")
  fi

  expected_attrs_json="$(jq -cn \
    --arg service "$service" \
    --arg namespace "$namespace" \
    --arg version "$version" \
    --arg deployment "$deployment" \
    '{
      "service.name": $service,
      "service.namespace": $namespace,
      "service.version": $version,
      "deployment.environment": $deployment
    }')"

  if metric_response="$(query_prometheus "count({service_name=\"${service}\",service_namespace=\"${namespace}\",service_version=\"${version}\",deployment_environment=\"${deployment}\"})" || true)"; then
    metric_series_count="$(jq -r 'try ((.data.result[0].value[1] // 0) | tonumber) catch 0' <<<"${metric_response:-{}}" 2>/dev/null || true)"
  fi
  metric_series_count="$(normalize_number "$metric_series_count")"

  if request_metric_response="$(query_prometheus "count({service_name=\"${service}\",service_namespace=\"${namespace}\",service_version=\"${version}\",deployment_environment=\"${deployment}\",__name__=~\"http_server_requests.*_count\"})" || true)"; then
    request_metric_series_count="$(jq -r 'try ((.data.result[0].value[1] // 0) | tonumber) catch 0' <<<"${request_metric_response:-{}}" 2>/dev/null || true)"
  fi
  request_metric_series_count="$(normalize_number "$request_metric_series_count")"

  if trace_search_response="$(query_tempo_search "$service" || true)"; then
    trace_count="$(jq -r 'try ((.traces // []) | length) catch 0' <<<"${trace_search_response:-{}}" 2>/dev/null || true)"
    sample_trace_id="$(jq -r '.traces[0].traceID // empty' <<<"${trace_search_response:-{}}" 2>/dev/null || true)"
    trace_services_json="$(jq -c 'try ((.traces[0].serviceStats // {}) | with_entries(.value = .value.spanCount)) catch {}' <<<"${trace_search_response:-{}}" 2>/dev/null || true)"
  fi
  trace_count="$(normalize_number "$trace_count")"
  if [ -z "$trace_services_json" ]; then
    trace_services_json='{}'
  fi

  if resource_series_response="$(query_prometheus_series "match[]={service_name=\"${service}\",service_namespace!=\"\",service_version!=\"\",deployment_environment!=\"\"}" || true)"; then
    resource_validation_json="$(jq -c \
      --argjson expected "$expected_attrs_json" '
      (.data[0] // {}) as $series
      | ({
          "service.name": ($series.service_name // empty),
          "service.namespace": ($series.service_namespace // empty),
          "service.version": ($series.service_version // empty),
          "deployment.environment": ($series.deployment_environment // empty)
        } | with_entries(select(.value != ""))) as $actual
      | {
          actual: $actual,
          missing: [
            $expected
            | to_entries[]
            | select($actual[.key] != .value)
            | .key
          ],
          valid: (
            [
              $expected
              | to_entries[]
              | select($actual[.key] != .value)
            ] | length == 0
          )
        }' <<<"${resource_series_response:-{}}" 2>/dev/null || true)"
  else
    resource_validation_json="$(jq -cn --argjson expected "$expected_attrs_json" '{actual:{},missing:($expected|keys),valid:false}')"
  fi
  if [ -z "$resource_validation_json" ]; then
    resource_validation_json="$(jq -cn --argjson expected "$expected_attrs_json" '{actual:{},missing:($expected|keys),valid:false}')"
  fi

  resource_status="$(jq -r 'if .valid then "valid" else "invalid" end' <<<"$resource_validation_json" 2>/dev/null || true)"
  if [ -z "$resource_status" ]; then
    resource_status="invalid"
  fi

  if [ "$config_status" = "valid" ] && [ "$metric_series_count" -gt 0 ] && [ "$trace_count" -gt 0 ] && [ "$resource_status" = "valid" ]; then
    service_status="verified"
  fi

  if [ "$config_status" = "valid" ]; then
    log_success "  ${service}: OTLP trace/metrics endpoints target otel-collector"
  else
    log_error "  ${service}: OTLP endpoint configuration mismatch (${config_issues[*]})"
  fi

  if [ "${#config_advisories[@]}" -gt 0 ]; then
    log_warning "  ${service}: logging endpoint mismatch remains advisory (${config_advisories[*]})"
  fi

  if [ "$metric_series_count" -gt 0 ]; then
    log_success "  ${service}: ${metric_series_count} JVM metric series visible in LGTM"
  else
    log_error "  ${service}: required metrics not visible in LGTM"
  fi

  if [ "$trace_count" -gt 0 ] && [ -n "$sample_trace_id" ]; then
    log_success "  ${service}: sample trace ${sample_trace_id}"
  else
    log_error "  ${service}: required traces not visible in Tempo"
  fi

  if [ "$resource_status" = "valid" ]; then
    log_success "  ${service}: required resource attributes present"
  else
    log_error "  ${service}: missing required resource attributes ($(jq -r '.missing | join(", ")' <<<"$resource_validation_json"))"
  fi

  if [ "${#config_issues[@]}" -gt 0 ]; then
    config_issues_json="$(json_array_from_lines "${config_issues[@]}")"
  fi

  if [ "${#config_advisories[@]}" -gt 0 ]; then
    config_advisories_json="$(json_array_from_lines "${config_advisories[@]}")"
  fi

  jq -cn \
    --arg service "$service" \
    --arg configStatus "$config_status" \
    --arg traceEndpoint "$trace_endpoint" \
    --arg metricsEndpoint "$metrics_endpoint" \
    --arg logsEndpoint "$logs_endpoint" \
    --argjson configIssues "$config_issues_json" \
    --argjson configAdvisories "$config_advisories_json" \
    --arg sampleTraceId "$sample_trace_id" \
    --argjson traceCount "$trace_count" \
    --argjson metricSeriesCount "$metric_series_count" \
    --argjson requestMetricSeriesCount "$request_metric_series_count" \
    --argjson traceServices "$trace_services_json" \
    --argjson resourceValidation "$resource_validation_json" \
    --argjson expectedResourceAttributes "$expected_attrs_json" \
    --arg status "$service_status" \
    '{
      serviceName: $service,
      status: $status,
      configuration: {
        status: $configStatus,
        tracesEndpoint: $traceEndpoint,
        metricsEndpoint: $metricsEndpoint,
        logsEndpoint: $logsEndpoint,
        issues: $configIssues,
        advisories: $configAdvisories
      },
      sampleTraceId: (if $sampleTraceId == "" then null else $sampleTraceId end),
      traceCount: $traceCount,
      traceServices: $traceServices,
      metricSeriesCount: $metricSeriesCount,
      requestMetricSeriesCount: $requestMetricSeriesCount,
      expectedResourceAttributes: $expectedResourceAttributes,
      resourceAttributes: $resourceValidation.actual,
      missingResourceAttributes: $resourceValidation.missing,
      resourceAttributeStatus: (if $resourceValidation.valid then "valid" else "invalid" end)
    }'
}

verify_logs() {
  local hello_logs trace_context_present="false"
  local loki_labels_response=""
  local loki_service_labels="[]"
  local status="warning"
  local message="Loki evidence remains advisory; the script cannot conclusively prove OTLP log ingestion."

  hello_logs="$(docker compose logs --no-color hello-service --tail 20 2>/dev/null || true)"
  if printf '%s' "$hello_logs" | grep -Eq '\[[^]]*[a-f0-9]{32}[^]]*\]'; then
    trace_context_present="true"
  fi

  if loki_labels_response="$(query_loki_service_labels || true)"; then
    loki_service_labels="$(jq -c '(.data // [])' <<<"${loki_labels_response:-{}}" 2>/dev/null || true)"
    if [ -z "$loki_service_labels" ]; then
      loki_service_labels='[]'
    fi
    if jq -e 'index("hello-service") and index("user-service") and index("greeting-service")' <<<"$loki_service_labels" >/dev/null 2>&1; then
      status="passed"
      message="Loki exposes service labels for all demo services."
    fi
  fi

  if [ "$status" = "passed" ]; then
    log_success "Logs: Loki exposes service labels for all services"
  else
    log_warning "Logs: advisory only; OTLP log ingestion is not required for pass/fail"
  fi

  LOGS_REPORT="$(jq -cn \
    --arg status "$status" \
    --arg message "$message" \
    --argjson traceContextInDockerLogs "$trace_context_present" \
    --argjson lokiServiceLabels "$loki_service_labels" \
    '{
      status: $status,
      message: $message,
      traceContextInDockerLogs: $traceContextInDockerLogs,
      lokiServiceLabels: $lokiServiceLabels
    }')"
}

write_report() {
  local services_json summary_json report_tmp

  if [ "${#SERVICE_REPORTS[@]}" -eq 0 ]; then
    services_json='[]'
  else
    if ! services_json="$(printf '%s\n' "${SERVICE_REPORTS[@]}" | jq -s '.')"; then
      die "Failed to build services report JSON from collected verification evidence."
    fi
  fi

  if ! summary_json="$(jq -cn \
    --arg reportFile "build/reports/otel/verification-report.json" \
    --argjson collector "$COLLECTOR_REPORT" \
    --argjson services "$services_json" \
    --argjson logs "$LOGS_REPORT" '
    {
      criticalFailures:
        ((if $collector.status != "healthy" then ["collector"] else [] end)
        + [ $services[] | select(.configuration.status != "valid") | "\(.serviceName):configuration" ]
        + [ $services[] | select(.metricSeriesCount <= 0) | "\(.serviceName):metrics" ]
        + [ $services[] | select(.traceCount <= 0 or .sampleTraceId == null) | "\(.serviceName):traces" ]
        + [ $services[] | select(.resourceAttributeStatus != "valid") | "\(.serviceName):resource-attributes" ]),
      warnings:
        ((if $logs.status == "warning" then ["logs"] else [] end)
        + [ $services[] | .serviceName as $serviceName | .configuration.advisories[]? | "\($serviceName):\(.)" ]),
      reportFile: $reportFile
    }
    | .success = (.criticalFailures | length == 0)
    | .overallStatus = (if .success then "passed" else "failed" end)')"; then
    die "Failed to build verification summary JSON."
  fi

  mkdir -p "$REPORT_DIR"
  report_tmp="${REPORT_FILE}.tmp"
  rm -f "$report_tmp"
  if ! jq -n \
    --arg generatedAt "$GENERATED_AT" \
    --argjson collector "$COLLECTOR_REPORT" \
    --argjson services "$services_json" \
    --argjson logs "$LOGS_REPORT" \
    --argjson summary "$summary_json" \
    '{
      generatedAt: $generatedAt,
      collector: $collector,
      services: $services,
      logs: $logs,
      summary: $summary
    }' >"$report_tmp"; then
    rm -f "$report_tmp"
    die "Failed to write verification report JSON."
  fi

  if ! mv "$report_tmp" "$REPORT_FILE"; then
    rm -f "$report_tmp"
    die "Failed to move verification report into place at $REPORT_FILE."
  fi
}

print_summary() {
  local overall_status collector_status
  overall_status="$(jq -r '.summary.overallStatus' "$REPORT_FILE")"
  collector_status="$(jq -r '.collector.status' "$REPORT_FILE")"

  echo ""
  echo "========================================"
  echo "  OpenTelemetry Verification Summary"
  echo "========================================"
  echo ""

  if [ "$collector_status" = "healthy" ]; then
    echo -e "  Collector:  ${GREEN}✓ HEALTHY${NC}"
  else
    echo -e "  Collector:  ${RED}✗ FAILED${NC}"
  fi

  jq -r '.services[] | [.serviceName, .status, (.metricSeriesCount|tostring), (.sampleTraceId // "-"), .resourceAttributeStatus] | @tsv' "$REPORT_FILE" \
    | while IFS=$'\t' read -r service status metric_count trace_id resource_status; do
      if [ "$status" = "verified" ] && [ "$resource_status" = "valid" ]; then
        echo -e "  ${service}: ${GREEN}✓${NC} metrics=${metric_count} trace=${trace_id}"
      else
        echo -e "  ${service}: ${RED}✗${NC} metrics=${metric_count} trace=${trace_id}"
      fi
    done

  if jq -e '.logs.status == "warning"' "$REPORT_FILE" >/dev/null 2>&1; then
    echo -e "  Logs:       ${YELLOW}⚠ ADVISORY${NC}"
  else
    echo -e "  Logs:       ${GREEN}✓ PASSED${NC}"
  fi

  echo ""
  echo "  Report: build/reports/otel/verification-report.json"
  echo ""

  if [ "$overall_status" = "passed" ]; then
    echo -e "${GREEN}Overall: verification evidence generated successfully.${NC}"
  else
    echo -e "${RED}Overall: critical verification checks failed.${NC}"
    echo -e "${RED}Failures:${NC} $(jq -r '.summary.criticalFailures | join(", ")' "$REPORT_FILE")"
  fi
}

main() {
  local runtime_ready=false
  local service_report=""

  require_command docker
  require_command jq
  require_command curl

  if ! COMPOSE_CONFIG_JSON="$(docker compose config --format json 2>&1)"; then
    die "Unable to load Docker Compose configuration with 'docker compose config --format json': $(trim_output "$COMPOSE_CONFIG_JSON")"
  fi

  echo "========================================"
  echo "  OpenTelemetry Evidence Generator"
  echo "========================================"
  echo ""

  if docker info >/dev/null 2>&1; then
    log_success "Docker is running"
  else
    log_error "Docker is not running"
    exit 1
  fi

  if prepare_runtime; then
    runtime_ready=true
  fi

  verify_collector

  if [ "$runtime_ready" = true ] && jq -e '.status == "healthy"' <<<"$COLLECTOR_REPORT" >/dev/null 2>&1; then
    generate_traffic
  else
    log_warning "Skipping traffic generation because runtime prerequisites are incomplete"
  fi

  log_info "Collecting telemetry verification evidence..."
  for service in "${SERVICES[@]}"; do
    service_report="$(collect_service_evidence "$service")"
    SERVICE_REPORTS+=("$service_report")
  done

  verify_logs
  write_report
  print_summary

  if jq -e '.summary.success' "$REPORT_FILE" >/dev/null 2>&1; then
    exit 0
  fi

  exit 1
}

main
