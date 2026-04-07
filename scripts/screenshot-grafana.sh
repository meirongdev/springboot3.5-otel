#!/usr/bin/env bash
#
# screenshot-grafana.sh - Open Grafana dashboards for manual screenshots
#
# Usage: ./scripts/screenshot-grafana.sh
#
# This script:
# 1. Opens each Grafana dashboard in your default browser
# 2. Tells you exactly what to screenshot
# 3. Updates README.md with the screenshots once you've saved them
#

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
SCREENSHOTS_DIR="$PROJECT_DIR/docs/screenshots"
GRAFANA_URL="http://localhost:3000"

echo ""
echo "=========================================="
echo "📸 Grafana Dashboard Screenshot Helper"
echo "=========================================="
echo ""
echo "I'll open each dashboard in your browser."
echo "For each one:"
echo "  1. Wait for charts to load"
echo "  2. Press Cmd+Shift+4 (macOS) to screenshot"
echo "  3. Save to: $SCREENSHOTS_DIR/<filename>"
echo ""

# Dashboard definitions
declare -a URLS=(
    "$GRAFANA_URL/d/services-overview"
    "$GRAFANA_URL/d/logs-dashboard"
    "$GRAFANA_URL/d/jvm-metrics"
)

declare -a NAMES=(
    "Services Overview"
    "Logs & Traces"
    "JVM Metrics"
)

declare -a FILES=(
    "services-overview.png"
    "logs-traces.png"
    "jvm-metrics.png"
)

# Open each dashboard
for i in "${!URLS[@]}"; do
    echo "📊 Opening: ${NAMES[$i]}"
    echo "   Save screenshot as: ${FILES[$i]}"
    open "${URLS[$i]}" 2>/dev/null || true
    echo ""
    sleep 2
done

echo "=========================================="
echo "✅ All dashboards opened in browser"
echo "=========================================="
echo ""
echo "Next steps:"
echo "  1. Take screenshots of each dashboard"
echo "  2. Save them to: $SCREENSHOTS_DIR/"
echo "  3. Run this script again to update README.md"
echo ""
echo "Or to update README.md now (with placeholders):"
echo "  git add -A"
echo "  git commit -m \"docs: add Grafana screenshots section\""
echo ""
