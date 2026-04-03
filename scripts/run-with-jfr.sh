#!/bin/bash
# =============================================================================
# Local Development Script with JFR Profiling
# =============================================================================
# Usage: ./scripts/run-with-jfr.sh [service-name] [jfr-options]
#
# Examples:
#   ./scripts/run-with-jfr.sh hello-service
#   ./scripts/run-with-jfr.sh hello-service --duration 60s
#   ./scripts/run-with-jfr.sh hello-service --profile memory
# =============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
LOGS_DIR="${PROJECT_ROOT}/logs"
JFR_DIR="${PROJECT_ROOT}/jfr-recordings"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)

# Default values
SERVICE_NAME="${1:-hello-service}"
JFR_DURATION="continuous"
JFR_MAXSIZE="200m"
JFR_MAXAGE="2h"
JFR_SETTINGS="profile"
PROFILE_TYPE="cpu"

# Parse arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --duration)
            JFR_DURATION="$2"
            shift 2
            ;;
        --maxsize)
            JFR_MAXSIZE="$2"
            shift 2
            ;;
        --maxage)
            JFR_MAXAGE="$2"
            shift 2
            ;;
        --settings)
            JFR_SETTINGS="$2"
            shift 2
            ;;
        --profile)
            PROFILE_TYPE="$2"
            shift 2
            ;;
        --help|-h)
            echo "Usage: $0 [service-name] [options]"
            echo ""
            echo "Options:"
            echo "  --duration <time>    JFR recording duration (default: continuous)"
            echo "  --maxsize <size>     Max JFR file size (default: 200m)"
            echo "  --maxage <time>      Max JFR file age (default: 2h)"
            echo "  --settings <type>    JFR settings profile (default: profile)"
            echo "  --profile <type>     Profile type: cpu, memory, lock (default: cpu)"
            exit 0
            ;;
        *)
            SERVICE_NAME="$1"
            shift
            ;;
    esac
done

# Ensure directories exist
mkdir -p "$LOGS_DIR" "$JFR_DIR"

# Colors
BLUE='\033[0;34m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

log_info() { echo -e "${BLUE}[INFO]${NC} $1"; }
log_success() { echo -e "${GREEN}[INFO]${NC} $1"; }
log_warn() { echo -e "${YELLOW}[WARN]${NC} $1"; }

# Build JFR options
JFR_OPTIONS="-XX:StartFlightRecording=name=dev_${TIMESTAMP},duration=${JFR_DURATION},filename=${JFR_DIR}/profile_${SERVICE_NAME}_${TIMESTAMP}.jfr,dumponexit=true,settings=${JFR_SETTINGS}"

# GC and memory options
GC_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+UseG1GC -XX:MaxGCPauseMillis=200"

# GC logging
GC_LOG="-Xlog:gc*:file=${LOGS_DIR}/gc_${SERVICE_NAME}_${TIMESTAMP}.log:time,uptime:filecount=3,filesize=10M"

# Heap dump on OOM
OOM_OPTIONS="-XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=${JFR_DIR}/"

# Combine all options
export JDK_JAVA_OPTIONS="$JFR_OPTIONS $GC_OPTIONS $GC_LOG $OOM_OPTIONS"

log_info "Starting $SERVICE_NAME with JFR Profiling"
echo ""
log_info "JFR Configuration:"
echo "  Duration:  $JFR_DURATION"
echo "  Max Size:  $JFR_MAXSIZE"
echo "  Max Age:   $JFR_MAXAGE"
echo "  Settings:  $JFR_SETTINGS"
echo "  Profile:   $PROFILE_TYPE"
echo ""
log_info "Logs directory: $LOGS_DIR"
log_info "JFR output: $JFR_DIR"
echo ""
log_info "JDK_JAVA_OPTIONS:"
echo "$JDK_JAVA_OPTIONS"
echo ""

# Determine Gradle task
case "$SERVICE_NAME" in
    hello-service)
        GRADLE_TASK=":hello-service:bootRun"
        PORT="8080"
        ;;
    user-service)
        GRADLE_TASK=":user-service:bootRun"
        PORT="8081"
        ;;
    greeting-service)
        GRADLE_TASK=":greeting-service:bootRun"
        PORT="8082"
        ;;
    *)
        log_warn "Unknown service: $SERVICE_NAME, using hello-service"
        GRADLE_TASK=":hello-service:bootRun"
        PORT="8080"
        ;;
esac

log_info "Service will be available at: http://localhost:$PORT"
log_info "Press Ctrl+C to stop"
echo ""

# Trap to cleanup on exit
trap 'log_info "Stopping service..."; exit 0' INT TERM

# Run the service
cd "$PROJECT_ROOT"
./gradlew "$GRADLE_TASK"
