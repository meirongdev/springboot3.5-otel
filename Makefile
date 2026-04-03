# Spring Boot 3.5 + Java 25 OpenTelemetry Demo
# Makefile for common development tasks

.PHONY: help build test clean run run-all stop fmt check docs up down restart logs

# Default target
help:
	@echo "Spring Boot 3.5 + Java 25 OpenTelemetry Demo"
	@echo ""
	@echo "Usage:"
	@echo "  make build          - Build all services"
	@echo "  make test           - Run all tests"
	@echo "  make clean          - Clean build artifacts"
	@echo "  make fmt            - Format code"
	@echo "  make check          - Run formatting and static analysis checks"
	@echo "  make coverage       - Generate JaCoCo coverage report"
	@echo ""
	@echo "  make up             - Start Grafana OTEL LGTM backend"
	@echo "  make down           - Stop Grafana OTEL LGTM backend"
	@echo "  make restart        - Restart Grafana OTEL LGTM backend"
	@echo "  make logs           - View Grafana logs"
	@echo ""
	@echo "  make run-all        - Start all services (requires backend running)"
	@echo "  make stop-all       - Stop all services"
	@echo ""
	@echo "  make run-hello      - Start hello-service only"
	@echo "  make run-user       - Start user-service only"
	@echo "  make run-greeting   - Start greeting-service only"
	@echo ""
	@echo "  make test-e2e       - Run end-to-end tests"
	@echo "  make test-contract  - Run Pact contract tests"
	@echo "  make test-arch      - Run architecture tests"
	@echo ""
	@echo "  make verify-otel    - Verify OpenTelemetry data collection"
	@echo "  make verify-otel-verbose - Verify OTel with detailed output"
	@echo "  make verify-otel-wait    - Verify OTel (wait for Grafana)"
	@echo "  make dev-full       - Full dev workflow: build + test + verify OTel"
	@echo ""
	@echo "Profiling (JFR):"
	@echo "  make jfr-run        - Run hello-service with JFR profiling"
	@echo "  make jfr-check      - Check active JFR recordings"
	@echo "  make jfr-dump       - Dump current JFR recording"
	@echo "  make jfr-flame      - Generate flame graph from latest JFR"
	@echo "  make jfr-analyze    - Analyze latest JFR recording"
	@echo ""
	@echo "  make docs           - Open documentation in browser"
	@echo ""
	@echo "Grafana: http://localhost:3000 (admin/admin)"

# Build commands
build:
	./gradlew build

test:
	./gradlew test

clean:
	./gradlew clean

fmt:
	./gradlew spotlessApply

check:
	./gradlew spotlessCheck

coverage:
	./gradlew testCodeCoverageReport
	@echo "Coverage report: build/reports/jacoco/testCodeCoverageReport/html/index.html"

# Docker Compose commands (Observability backend)
up:
	docker compose up -d
	@echo "Waiting for Grafana to be ready..."
	@until curl -s http://localhost:3000/api/health > /dev/null 2>&1; do \
		echo "  Waiting for Grafana..."; \
		sleep 2; \
	done
	@echo "Grafana is ready: http://localhost:3000 (admin/admin)"

down:
	docker compose down

restart:
	docker compose restart

logs:
	docker compose logs -f

# Run services
run-all:
	@echo "Starting all services in background..."
	./gradlew :greeting-service:bootRun &
	./gradlew :user-service:bootRun &
	./gradlew :hello-service:bootRun &
	@echo "Services started. Press Ctrl+C to stop."

stop-all:
	@echo "Stopping all services..."
	-@lsof -ti:8080,8081,8082 | xargs kill -9 2>/dev/null || true
	@echo "Services stopped."

run-hello:
	./gradlew :hello-service:bootRun

run-user:
	./gradlew :user-service:bootRun

run-greeting:
	./gradlew :greeting-service:bootRun

# Test targets
test-e2e:
	./gradlew :hello-service:test --tests "*.HelloControllerEndToEndTest"

test-contract:
	./gradlew test --tests "*Pact*"

test-arch:
	./gradlew :arch-tests:test

# Documentation
docs:
	@echo "Opening documentation..."
	@open README.md || xdg-open README.md 2>/dev/null || echo "Open README.md manually"

# Quick test command for development
dev-test: clean fmt check test coverage

# OpenTelemetry verification
verify-otel:
	@chmod +x scripts/verify-otel.sh
	@./scripts/verify-otel.sh

verify-otel-verbose:
	@chmod +x scripts/verify-otel.sh
	@./scripts/verify-otel.sh --verbose

verify-otel-wait:
	@chmod +x scripts/verify-otel.sh
	@./scripts/verify-otel.sh --wait

# Full development workflow with OTel verification
dev-full: dev-test verify-otel

# =============================================================================
# JFR Profiling Commands
# =============================================================================

jfr-run:
	@chmod +x scripts/run-with-jfr.sh
	@./scripts/run-with-jfr.sh hello-service

jfr-run-user:
	@chmod +x scripts/run-with-jfr.sh
	@./scripts/run-with-jfr.sh user-service

jfr-run-greeting:
	@chmod +x scripts/run-with-jfr.sh
	@./scripts/run-with-jfr.sh greeting-service

jfr-check:
	@chmod +x scripts/jfr-profiling.sh
	@./scripts/jfr-profiling.sh check hello-service

jfr-dump:
	@chmod +x scripts/jfr-profiling.sh
	@./scripts/jfr-profiling.sh dump hello-service

jfr-stop:
	@chmod +x scripts/jfr-profiling.sh
	@./scripts/jfr-profiling.sh stop hello-service

jfr-analyze:
	@chmod +x scripts/jfr-profiling.sh
	@./scripts/jfr-profiling.sh analyze $$(ls -t jfr-recordings/*.jfr 2>/dev/null | head -1)

jfr-flame:
	@chmod +x scripts/jfr-profiling.sh
	@./scripts/jfr-profiling.sh flame $$(ls -t jfr-recordings/*.jfr 2>/dev/null | head -1)

jfr-help:
	@chmod +x scripts/jfr-profiling.sh
	@./scripts/jfr-profiling.sh help
