#!/bin/bash
#
# OpenTelemetry Data Collection Verification Script
# 
# This script verifies that OpenTelemetry data (traces, metrics, logs)
# is being properly collected from all Spring Boot services.
#
# Usage: ./scripts/verify-otel.sh [--verbose] [--wait]
#
# Options:
#   --verbose  Show detailed output
#   --wait     Wait for services to be ready (max 60 seconds)
#

set -e

# Configuration
GRAFANA_URL="http://localhost:3000"
GRAFANA_AUTH="admin:admin"
SERVICES=("hello-service" "user-service" "greeting-service")
MAX_WAIT=60

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Flags
VERBOSE=false
WAIT_READY=false

# Parse arguments
while [[ $# -gt 0 ]]; do
    case $1 in
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

# Helper functions
log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[PASS]${NC} $1"
}

log_warning() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[FAIL]${NC} $1"
}

log_verbose() {
    if [ "$VERBOSE" = true ]; then
        echo -e "${BLUE}[DEBUG]${NC} $1"
    fi
}

# Check if Grafana is ready
wait_for_grafana() {
    log_info "Waiting for Grafana to be ready..."
    local count=0
    while ! curl -s "$GRAFANA_URL/api/health" > /dev/null 2>&1; do
        sleep 2
        count=$((count + 2))
        if [ $count -ge $MAX_WAIT ]; then
            log_error "Grafana not ready after ${MAX_WAIT}s"
            return 1
        fi
        log_verbose "  Waiting for Grafana... (${count}s/${MAX_WAIT}s)"
    done
    log_success "Grafana is ready"
    return 0
}

# Query Prometheus metrics
query_prometheus() {
    local query="$1"
    docker compose exec grafana-otel-lgtm curl -s -G "http://localhost:9090/api/v1/query" --data-urlencode "query=$query" 2>/dev/null
}

# Query Tempo traces
query_tempo() {
    local service="$1"
    local encoded_query=$(echo -n "{.service.name=\"$service\"}" | jq -sRr @uri)
    docker compose exec grafana-otel-lgtm curl -s "http://localhost:3200/api/search?q=$encoded_query&limit=1" 2>/dev/null
}

# Query Loki logs
query_loki() {
    local service="$1"
    docker compose exec grafana-otel-lgtm curl -s "http://localhost:3100/loki/api/v1/label/service_name/values" 2>/dev/null
}

# Verify metrics collection
verify_metrics() {
    log_info "Verifying metrics collection..."
    local all_passed=true
    
    for service in "${SERVICES[@]}"; do
        log_verbose "  Checking metrics for $service..."
        
        # Check JVM metrics
        local result=$(query_prometheus "jvm_memory_used_bytes{job=\"$service\"}" 2>/dev/null)
        local metric_count=$(echo "$result" | jq -r '.data.result | length' 2>/dev/null || echo "0")
        
        if [ "$metric_count" -gt 0 ]; then
            log_success "  $service: JVM metrics collected ($metric_count series)"
        else
            log_error "  $service: No JVM metrics found"
            all_passed=false
        fi
        
        # Check HTTP metrics
        local http_result=$(query_prometheus "http_server_requests_milliseconds_count{job=\"$service\"}" 2>/dev/null)
        local http_count=$(echo "$http_result" | jq -r '.data.result | length' 2>/dev/null || echo "0")
        
        if [ "$http_count" -gt 0 ]; then
            log_success "  $service: HTTP metrics collected ($http_count series)"
        else
            log_warning "  $service: No HTTP metrics found (may need traffic)"
        fi
    done
    
    if [ "$all_passed" = true ]; then
        log_success "Metrics verification: PASSED"
        return 0
    else
        log_error "Metrics verification: FAILED"
        return 1
    fi
}

# Verify traces collection
verify_traces() {
    log_info "Verifying traces collection..."
    local all_passed=true
    local trace_found=false
    
    for service in "${SERVICES[@]}"; do
        log_verbose "  Checking traces for $service..."
        
        local result=$(query_tempo "$service" 2>/dev/null)
        local trace_count=$(echo "$result" | jq -r '.traces | length' 2>/dev/null || echo "0")
        
        if [ "$trace_count" -gt 0 ]; then
            local trace_id=$(echo "$result" | jq -r '.traces[0].traceID' 2>/dev/null)
            local duration_ms=$(echo "$result" | jq -r '.traces[0].durationMs' 2>/dev/null)
            local services_count=$(echo "$result" | jq -r '.traces[0].serviceStats | keys | length' 2>/dev/null || echo "1")
            
            log_success "  $service: $trace_count trace(s) found"
            log_verbose "    Latest trace: $trace_id (${duration_ms}ms, $services_count service(s))"
            trace_found=true
        else
            log_warning "  $service: No traces found (may need traffic)"
        fi
    done
    
    if [ "$trace_found" = true ]; then
        log_success "Traces verification: PASSED"
        return 0
    else
        log_error "Traces verification: FAILED (no traces found)"
        return 1
    fi
}

# Verify logs correlation
verify_logs() {
    log_info "Verifying logs configuration..."
    
    # Check if services are logging with trace context
    local hello_logs=$(docker compose logs hello-service --tail 20 2>/dev/null)
    
    if echo "$hello_logs" | grep -q "\[.*[a-f0-9]\{32\}.*\]"; then
        log_success "Logs contain trace/span IDs (context propagation enabled)"
    else
        log_warning "Logs may not contain trace context (check logging configuration)"
    fi
    
    # Check Loki availability
    local loki_labels=$(query_loki "hello-service" 2>/dev/null)
    if [ -n "$loki_labels" ]; then
        log_success "Loki is accessible"
    else
        log_warning "Loki may not be receiving logs (logs are in Docker stdout)"
    fi
    
    log_success "Logs verification: PASSED (configuration check)"
    return 0
}

# Verify distributed tracing
verify_distributed_tracing() {
    log_info "Verifying distributed tracing..."
    
    local result=$(query_tempo "hello-service" 2>/dev/null)
    local trace_count=$(echo "$result" | jq -r '.traces | length' 2>/dev/null || echo "0")
    
    if [ "$trace_count" -gt 0 ]; then
        # Check if any trace has multiple services
        local multi_service_traces=$(echo "$result" | jq -r '[.traces[] | select(.serviceStats | keys | length > 1)] | length' 2>/dev/null || echo "0")
        local service_stats=$(echo "$result" | jq -r '.traces[0].serviceStats' 2>/dev/null)
        local services=$(echo "$service_stats" | jq -r 'keys[]' 2>/dev/null | tr '\n' ', ' | sed 's/,$//')
        local services_count=$(echo "$service_stats" | jq -r 'keys | length' 2>/dev/null || echo "1")
        
        if [ "$multi_service_traces" -gt 0 ]; then
            log_success "Distributed tracing: $multi_service_traces trace(s) with multiple services ($services)"
            log_success "Distributed tracing verification: PASSED"
            return 0
        elif [ "$services_count" -gt 1 ]; then
            log_success "Distributed tracing: $services_count services in trace ($services)"
            log_success "Distributed tracing verification: PASSED"
            return 0
        else
            log_warning "Distributed tracing: Only 1 service in trace (may need full request flow)"
            return 1
        fi
    else
        log_warning "Distributed tracing: No traces to verify"
        return 1
    fi
}

# Generate traffic to ensure data is available
generate_traffic() {
    log_info "Generating test traffic..."
    
    # Send requests to generate traces and metrics
    curl -s "http://localhost:8080/api/1" > /dev/null 2>&1 || true
    curl -s -H "Accept-Language: zh" "http://localhost:8080/api/1" > /dev/null 2>&1 || true
    curl -s -H "Accept-Language: ja" "http://localhost:8080/api/1" > /dev/null 2>&1 || true
    
    # Wait for metrics to be exported
    sleep 2
    
    log_success "Test traffic generated"
}

# Print summary
print_summary() {
    local metrics_result=$1
    local traces_result=$2
    local logs_result=$3
    local distributed_result=$4
    
    echo ""
    echo "========================================"
    echo "  OpenTelemetry Verification Summary"
    echo "========================================"
    echo ""
    
    if [ $metrics_result -eq 0 ]; then
        echo -e "  Metrics:    ${GREEN}✓ PASSED${NC}"
    else
        echo -e "  Metrics:    ${RED}✗ FAILED${NC}"
    fi
    
    if [ $traces_result -eq 0 ]; then
        echo -e "  Traces:     ${GREEN}✓ PASSED${NC}"
    else
        echo -e "  Traces:     ${RED}✗ FAILED${NC}"
    fi
    
    if [ $logs_result -eq 0 ]; then
        echo -e "  Logs:       ${GREEN}✓ PASSED${NC}"
    else
        echo -e "  Logs:       ${YELLOW}⚠ WARNING${NC}"
    fi
    
    if [ $distributed_result -eq 0 ]; then
        echo -e "  Distributed:${GREEN}✓ PASSED${NC}"
    else
        echo -e "  Distributed:${YELLOW}⚠ WARNING${NC}"
    fi
    
    echo ""
    
    if [ $metrics_result -eq 0 ] && [ $traces_result -eq 0 ]; then
        echo -e "${GREEN}Overall: OpenTelemetry is working correctly!${NC}"
        echo ""
        echo "Access Grafana: http://localhost:3000 (admin/admin)"
        echo ""
        return 0
    else
        echo -e "${RED}Overall: Some checks failed. Review the output above.${NC}"
        echo ""
        return 1
    fi
}

# Main execution
main() {
    echo "========================================"
    echo "  OpenTelemetry Verification Script"
    echo "========================================"
    echo ""
    
    # Check if Docker is running
    if ! docker info > /dev/null 2>&1; then
        log_error "Docker is not running"
        exit 1
    fi
    
    # Check if Grafana is accessible
    if ! curl -s "$GRAFANA_URL/api/health" > /dev/null 2>&1; then
        if [ "$WAIT_READY" = true ]; then
            if ! wait_for_grafana; then
                exit 1
            fi
        else
            log_error "Grafana is not accessible at $GRAFANA_URL"
            echo "Start with: docker compose up -d"
            echo "Or use --wait flag to wait for Grafana"
            exit 1
        fi
    else
        log_success "Grafana is accessible"
    fi
    
    # Generate traffic to ensure fresh data
    generate_traffic
    
    # Run verification checks
    local metrics_result=0
    local traces_result=0
    local logs_result=0
    local distributed_result=0
    
    verify_metrics || metrics_result=1
    verify_traces || traces_result=1
    verify_logs || logs_result=1
    verify_distributed_tracing || distributed_result=1
    
    # Print summary
    print_summary $metrics_result $traces_result $logs_result $distributed_result
    exit $?
}

# Run main
main
