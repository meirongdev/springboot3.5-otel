#!/bin/bash
# .gemini/hooks/harness-status.sh
# Provides real-time context about the Spring Boot 3.5 OTel Harness Engineering state.

echo "### Harness Engineering Status ###"

# 1. Docker / Observability Backend
echo "#### Docker Containers"
if command -v docker &> /dev/null; then
  docker compose ps --format "table {{.Name}}\t{{.Status}}\t{{.Ports}}" 2>/dev/null || echo "Docker Compose not running or not in project root."
else
  echo "Docker command not found."
fi

# 2. Spring Boot Service Health
echo -e "\n#### Service Health (Spring Boot Actuator)"
check_health() {
  local name=$1
  local port=$2
  local response=$(curl -s -m 2 http://localhost:$port/actuator/health)
  local status=$(echo "$response" | grep -o '"status":"[^"]*"' | head -n 1 | cut -d'"' -f4)
  if [ -z "$status" ]; then
    echo "- $name (:$port): DOWN/NOT REACHABLE"
  else
    echo "- $name (:$port): $status"
  fi
}

check_health "hello-service" 8080
check_health "user-service" 8081
check_health "greeting-service" 8082

# 3. Quality & Tests
echo -e "\n#### Quality & Test Results"
if [ -d "build/reports/jacoco/testCodeCoverageReport/html" ]; then
  echo "- JaCoCo: Coverage report exists (build/reports/jacoco/...)"
else
  echo "- JaCoCo: No aggregated coverage report found."
fi

if [ -d "build/pacts" ]; then
  pact_count=$(find build/pacts -name "*.json" | wc -l)
  echo "- Pact: $pact_count contract(s) generated."
else
  echo "- Pact: No contract files found in build/pacts."
fi

# Check for recent test failures
failed_tests=$(find . -name "TEST-*.xml" -exec grep -l "<failure" {} + | wc -l)
if [ "$failed_tests" -gt 0 ]; then
  echo "- Tests: $failed_tests failure(s) detected in XML reports."
else
  echo "- Tests: No recent failures detected in XML reports."
fi

# 4. Git Status (Harness related files)
echo -e "\n#### Recently Modified Harness Files"
git status --porcelain docs/ build.gradle.kts compose.yaml .github/ | head -n 5
