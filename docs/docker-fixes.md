# Docker Build and Runtime Fixes

This document records the fixes applied to resolve Docker build and runtime issues.

## Problem Summary

The `make up` command was failing due to:
1. Missing `arch-tests` module in Docker build context
2. Missing other service modules required by Gradle's multi-module configuration
3. Grafana container healthcheck using unavailable `wget` command

## Issues and Solutions

### Issue 1: arch-tests Module Missing

**Error:**
```
Configuring project ':arch-tests' without an existing directory is not allowed. 
The configured projectDirectory '/build/arch-tests' does not exist
```

**Root Cause:**
- `.dockerignore` excluded `arch-tests/` directory
- `settings.gradle.kts` includes `arch-tests` module
- Gradle requires all included modules to exist in the build context

**Fix:**
Removed `arch-tests/` from `.dockerignore`:

```diff
  build/
  .gradle/
  *.md
  .github/
- arch-tests/
  **/build/
  **/.gradle/
```

### Issue 2: Other Service Modules Missing

**Error:**
```
Configuring project ':user-service' without an existing directory is not allowed.
The configured projectDirectory '/build/user-service' does not exist
```

**Root Cause:**
- Each service Dockerfile only copied its own module directory
- `settings.gradle.kts` includes all modules (`hello-service`, `user-service`, `greeting-service`)
- Gradle requires all modules to be present during configuration phase

**Fix:**
Updated all service Dockerfiles to copy all module directories:

```dockerfile
FROM eclipse-temurin:25-jdk AS builder
WORKDIR /build
COPY gradle/ gradle/
COPY gradlew build.gradle.kts settings.gradle.kts gradle.properties ./
COPY shared/ shared/
COPY arch-tests/ arch-tests/
COPY hello-service/ hello-service/
COPY user-service/ user-service/
COPY greeting-service/ greeting-service/
RUN ./gradlew :service-name:bootJar --no-daemon -x test
```

Files updated:
- `hello-service/Dockerfile`
- `user-service/Dockerfile`
- `greeting-service/Dockerfile`

### Issue 3: Grafana Healthcheck Failure

**Error:**
```
dependency failed to start: container grafana-otel-lgtm is unhealthy
```

**Root Cause:**
- Healthcheck used `wget` command: `["CMD", "wget", "-q", "--spider", "http://localhost:3000/api/health"]`
- `wget` is not available in the `grafana/otel-lgtm` container
- Container was marked unhealthy even though Grafana was running correctly

**Fix:**
Changed healthcheck to use `curl` (which is available) and increased `start_period`:

```yaml
healthcheck:
  test: ["CMD", "curl", "-f", "-s", "http://localhost:3000/api/health"]
  interval: 10s
  timeout: 5s
  retries: 3
  start_period: 60s  # Increased from 30s
```

File updated: `compose.yaml`

## Verification

After applying all fixes:

```bash
# All containers should start successfully
make up

# Check container status
docker compose ps

# Expected output:
# NAME                                   STATUS
# grafana-otel-lgtm                      Up (healthy)
# springboot35-otel-hello-service-1      Up
# springboot35-otel-user-service-1       Up
# springboot35-otel-greeting-service-1   Up

# Test the service
curl http://localhost:8080/api/1
# Expected: {"userId":1,"userName":"Alice","greeting":"Hello, World!","language":"en"}
```

## Files Modified

| File | Change |
|------|--------|
| `.dockerignore` | Removed `arch-tests/` exclusion |
| `hello-service/Dockerfile` | Added `COPY arch-tests/`, `COPY user-service/`, `COPY greeting-service/` |
| `user-service/Dockerfile` | Added `COPY arch-tests/`, `COPY hello-service/`, `COPY greeting-service/` |
| `greeting-service/Dockerfile` | Added `COPY arch-tests/`, `COPY hello-service/`, `COPY user-service/` |
| `compose.yaml` | Changed healthcheck from `wget` to `curl`, increased `start_period` to 60s |

## Lessons Learned

1. **Multi-module Gradle projects in Docker**: All modules listed in `settings.gradle.kts` must be present in the Docker build context, even if only building one module.

2. **Container healthchecks**: Always verify that the commands used in healthchecks are available in the container image.

3. **Startup time for LGTM stack**: The Grafana OTEL LGTM container runs multiple services (Grafana, Loki, Tempo, Prometheus, Pyroscope, OTEL Collector). A 60-second `start_period` provides adequate time for all components to initialize.
