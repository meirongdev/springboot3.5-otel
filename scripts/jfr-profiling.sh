#!/bin/bash
# =============================================================================
# JFR Profiling Utility Script
# =============================================================================
# Usage: ./scripts/jfr-profiling.sh [command] [options]
#
# Commands:
#   start     - Start JFR recording
#   stop      - Stop JFR recording and download
#   dump      - Dump current recording without stopping
#   check     - Check active recordings
#   analyze   - Analyze JFR file with jfr tool
#   flame     - Generate flame graph from JFR file
#
# Examples:
#   ./scripts/jfr-profiling.sh start hello-service
#   ./scripts/jfr-profiling.sh stop hello-service --output profile.jfr
#   ./scripts/jfr-profiling.sh flame profile.jfr
# =============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
JFR_OUTPUT_DIR="${PROJECT_ROOT}/jfr-recordings"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Logging functions
log_info()    { echo -e "${BLUE}[INFO]${NC} $1"; }
log_success() { echo -e "${GREEN}[SUCCESS]${NC} $1"; }
log_warn()    { echo -e "${YELLOW}[WARN]${NC} $1"; }
log_error()   { echo -e "${RED}[ERROR]${NC} $1"; }

# Ensure output directory exists
mkdir -p "$JFR_OUTPUT_DIR"

# Find Java process PID for a service
find_pid() {
    local service_name="$1"
    local pid
    
    # Try to find by service name pattern
    pid=$(jps -l | grep -i "$service_name" | awk '{print $1}' | head -1)
    
    if [ -z "$pid" ]; then
        # Fallback: find any Java process
        pid=$(jps -l | grep -i "jar" | awk '{print $1}' | head -1)
    fi
    
    if [ -z "$pid" ]; then
        log_error "No Java process found for '$service_name'"
        echo "Available Java processes:"
        jps -l
        exit 1
    fi
    
    echo "$pid"
}

# Check if JFR is available
check_jfr() {
    local pid="$1"
    log_info "Checking JFR availability for PID $pid..."
    
    if ! jcmd "$pid" JFR.check > /dev/null 2>&1; then
        log_error "JFR is not available or process doesn't support it"
        echo "Try running with JFR enabled:"
        echo "  JDK_JAVA_OPTIONS='-XX:StartFlightRecording=name=prod,maxsize=200m,maxage=2h'"
        exit 1
    fi
    
    log_success "JFR is available"
}

# Start JFR recording
start_recording() {
    local service_name="${1:-app}"
    local duration="${2:-300s}"  # Default 5 minutes
    local name="${3:-diagnostic_$TIMESTAMP}"
    local filename="${4:-${JFR_OUTPUT_DIR}/${name}.jfr}"
    
    local pid
    pid=$(find_pid "$service_name")
    
    log_info "Starting JFR recording for '$service_name' (PID: $pid)"
    log_info "Duration: $duration, Output: $filename"
    
    # Check for existing recordings
    local existing
    existing=$(jcmd "$pid" JFR.check 2>/dev/null | grep -c "name=$name" || true)
    if [ "$existing" -gt 0 ]; then
        log_warn "Recording '$name' already exists, stopping..."
        jcmd "$pid" JFR.stop "$name" 2>/dev/null || true
    fi
    
    # Start recording
    jcmd "$pid" JFR.start \
        name="$name" \
        duration="$duration" \
        filename="$filename" \
        settings=profile \
        maxsize=100m \
        maxage=1h
    
    log_success "JFR recording started: $name"
    echo ""
    echo "To stop early, run:"
    echo "  $0 stop $service_name --name $name"
    echo ""
    echo "To check status:"
    echo "  $0 check $service_name"
}

# Stop JFR recording
stop_recording() {
    local service_name="${1:-app}"
    local name="${2:-}"
    local output="${3:-}"
    
    local pid
    pid=$(find_pid "$service_name")
    
    if [ -n "$name" ]; then
        log_info "Stopping JFR recording '$name'..."
        jcmd "$pid" JFR.stop "$name"
        log_success "Recording stopped: $name"
    else
        log_info "Stopping all JFR recordings..."
        # List and stop all recordings
        jcmd "$pid" JFR.check | grep "Name:" | awk '{print $2}' | while read -r recording_name; do
            jcmd "$pid" JFR.stop "$recording_name"
            log_success "Stopped: $recording_name"
        done
    fi
    
    if [ -n "$output" ]; then
        log_info "Output will be saved to: $output"
    fi
}

# Dump current recording
dump_recording() {
    local service_name="${1:-app}"
    local name="${2:-production}"
    local output="${3:-${JFR_OUTPUT_DIR}/dump_${TIMESTAMP}.jfr}"
    
    local pid
    pid=$(find_pid "$service_name")
    
    log_info "Dumping JFR recording '$name'..."
    
    jcmd "$pid" JFR.dump "$name" filename="$output"
    
    log_success "Recording dumped to: $output"
}

# Check active recordings
check_recordings() {
    local service_name="${1:-app}"
    
    local pid
    pid=$(find_pid "$service_name")
    
    log_info "Active JFR recordings for PID $pid:"
    echo ""
    jcmd "$pid" JFR.check
}

# Analyze JFR file
analyze_jfr() {
    local jfr_file="$1"
    
    if [ ! -f "$jfr_file" ]; then
        log_error "File not found: $jfr_file"
        exit 1
    fi
    
    log_info "Analyzing JFR file: $jfr_file"
    echo ""
    
    # Check if jfr command is available
    if ! command -v jfr &> /dev/null; then
        log_error "jfr command not found. Please install JDK."
        exit 1
    fi
    
    # Print summary
    log_info "=== JFR Summary ==="
    jfr summary "$jfr_file"
    
    echo ""
    log_info "=== Event Count ==="
    jfr print --events "$jfr_file" | head -50
}

# Generate flame graph
generate_flame() {
    local jfr_file="$1"
    local output="${2:-${JFR_OUTPUT_DIR}/flame_${TIMESTAMP}.html}"
    
    if [ ! -f "$jfr_file" ]; then
        log_error "File not found: $jfr_file"
        exit 1
    fi
    
    log_info "Generating flame graph from: $jfr_file"
    
    # Check if async-profiler is available
    if ! command -v asprof &> /dev/null; then
        log_warn "asprof (async-profiler) not found. Installing..."
        # Try to install async-profiler
        if command -v brew &> /dev/null; then
            brew install async-profiler
        elif command -v apt &> /dev/null; then
            sudo apt-get install -y async-profiler
        else
            log_error "Cannot install async-profiler automatically."
            echo "Please install from: https://github.com/jvm-profiling-tools/async-profiler"
            exit 1
        fi
    fi
    
    asprof -jfr "$jfr_file" --flamegraph "$output"
    
    log_success "Flame graph generated: $output"
    
    # Open in browser if possible
    if command -v open &> /dev/null; then
        open "$output"
    elif command -v xdg-open &> /dev/null; then
        xdg-open "$output"
    else
        echo "Open in browser: $output"
    fi
}

# Show help
show_help() {
    cat << EOF
JFR Profiling Utility Script

Usage: $0 <command> [options]

Commands:
  start [service] [duration]     Start JFR recording (default: 5min)
  stop [service] [name]          Stop JFR recording
  dump [service] [name] [output] Dump current recording
  check [service]                Check active recordings
  analyze <jfr-file>             Analyze JFR file
  flame <jfr-file> [output]      Generate flame graph
  help                           Show this help

Examples:
  $0 start hello-service 60s
  $0 stop hello-service --name diagnostic_20260403_120000
  $0 check hello-service
  $0 analyze jfr-recordings/profile.jfr
  $0 flame jfr-recordings/profile.jfr output.html

Environment Variables:
  JFR_OUTPUT_DIR    Output directory for JFR files (default: ./jfr-recordings)

EOF
}

# Main command dispatcher
main() {
    local command="${1:-help}"
    shift || true
    
    case "$command" in
        start)
            start_recording "$@"
            ;;
        stop)
            stop_recording "$@"
            ;;
        dump)
            dump_recording "$@"
            ;;
        check)
            check_recordings "$@"
            ;;
        analyze)
            analyze_jfr "$@"
            ;;
        flame)
            generate_flame "$@"
            ;;
        help|--help|-h)
            show_help
            ;;
        *)
            log_error "Unknown command: $command"
            show_help
            exit 1
            ;;
    esac
}

main "$@"
