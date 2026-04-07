## Screenshots

> **Note**: Replace these placeholder images with actual Grafana dashboard screenshots.
>
> **Quick way to capture:**
> 1. Open http://localhost:3000 (admin/admin)
> 2. Navigate to each dashboard
> 3. Press `Cmd + Shift + 4` (macOS) to screenshot
> 4. Save to `docs/screenshots/` with the filename shown below

### Services Overview

![Services Overview](docs/screenshots/services-overview.png)

*Services dashboard showing request rate, error rate, and latency across all services*

**URL**: http://localhost:3000/d/services-overview

### Logs & Traces

![Logs & Traces](docs/screenshots/logs-traces.png)

*Combined view of application logs (Loki) and distributed traces (Tempo)*

**URL**: http://localhost:3000/d/logs-dashboard

### JVM Metrics

![JVM Metrics](docs/screenshots/jvm-metrics.png)

*JVM resource utilization: CPU, memory, threads, garbage collection*

**URL**: http://localhost:3000/d/jvm-metrics
