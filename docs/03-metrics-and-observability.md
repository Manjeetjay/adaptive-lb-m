# Metrics & Observability Pipeline

## 1. Overview

A research-oriented adaptive load balancer relies entirely on the accuracy, granularity, and latency of its telemetry feedback loop.

This document specifies the metrics pipeline:
1. **Source Generation:** Spring Boot Actuator and Micrometer instrumentation inside worker instances and the gateway.
2. **Scraping & Aggregation:** Prometheus configuration with high-frequency research scrape profiles.
3. **Gateway Cache Subsystem:** The low-latency in-memory cache architecture that decouples telemetry ingestion from the request forwarding path.
4. **Dashboards & Visualizations:** Grafana panel layouts, PromQL queries, and exportable datasets.

---

## 2. Telemetry Schema & Metric Catalog

### 2.1 Worker Microservice Metrics

Each worker instance exposes standard and custom metrics on `/actuator/prometheus`:

| Metric Name | Type | Unit | Description / Formula |
| :--- | :--- | :--- | :--- |
| `system_cpu_usage` | Gauge | Ratio $[0.0, 1.0]$ | Recent CPU usage for the entire operating system. |
| `process_cpu_usage` | Gauge | Ratio $[0.0, 1.0]$ | CPU usage of the JVM worker process. |
| `jvm_memory_used_bytes` | Gauge | Bytes | Current JVM heap memory utilized (`area="heap"`). |
| `jvm_memory_max_bytes` | Gauge | Bytes | Maximum configured heap capacity (`-Xmx`). |
| `http_server_requests_seconds_count` | Counter | Total requests | Total incoming requests tagged by status (`status="200"`, `"500"`). |
| `http_server_requests_seconds_sum` | Counter | Seconds | Total time spent processing HTTP requests. |
| `alb_worker_active_requests` | Gauge | Integer | Custom gauge tracking concurrent requests currently in-flight. |
| `alb_worker_simulated_delay_ms` | Gauge | Milliseconds | Active artificial latency injected via chaos endpoints. |

### 2.2 Gateway-Level Telemetry

The Gateway produces metrics measuring routing overhead and decision accuracy:

| Metric Name | Type | Unit | Description |
| :--- | :--- | :--- | :--- |
| `alb_gateway_route_decisions_total` | Counter | Count | Requests routed, tagged by `strategy` and `target_instance`. |
| `alb_gateway_routing_eval_duration_seconds` | Histogram | Seconds | Processing time taken by `RoutingStrategy.select()`. |
| `alb_gateway_instance_health_score` | Gauge | Ratio $[0.0, 1.0]$ | Computed composite health score per instance. |
| `alb_gateway_instance_probability` | Gauge | Ratio $[0.0, 1.0]$ | Softmax routing probability per instance. |
| `alb_gateway_cache_staleness_ms` | Gauge | Milliseconds | Age of the metrics snapshot currently driving routing decisions. |

---

## 3. Prometheus Scrape Configuration (`prometheus.yml`)

For academic experimentation, Prometheus is configured with high-frequency collection intervals (1 second) to capture rapid workload transitions:

```yaml
global:
  scrape_interval: 1s
  evaluation_interval: 1s

scrape_configs:
  - job_name: 'alb-gateway'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['alb-gateway:8080']
        labels:
          role: 'gateway'

  - job_name: 'alb-workers'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets:
          - 'alb-worker-1:8081'
          - 'alb-worker-2:8082'
          - 'alb-worker-3:8083'
        labels:
          role: 'worker'
```

---

## 4. In-Memory Metric Cache Architecture

To achieve sub-millisecond per-request overhead, the gateway must **never query Prometheus synchronously** during an incoming user request.

### 4.1 Hybrid Telemetry Ingestion Model
The Gateway uses a dual-source synchronization model:
1. **Local Concurrency Counter (Synchronous Push):**
   - Active connections per instance are incremented and decremented directly in the Gateway's local state.
   - Provides immediate, zero-latency feedback on in-flight traffic.
2. **Background Poller (Asynchronous Pull):**
   - A scheduled reactive poller queries worker metrics every $T_{\text{refresh}}$ (configurable from $100\text{ ms}$ to $5000\text{ ms}$).
   - Computes normalized scores and atomically replaces the `RoutingTable` reference:
     ```java
     AtomicReference<RoutingSnapshot> currentRoutingSnapshot;
     ```

### 4.2 Data Model: `InstanceTelemetrySnapshot`
```java
public record InstanceTelemetrySnapshot(
    String instanceId,
    String uri,
    double cpuUsage,
    double memoryUsageRatio,
    double latencyEmaMs,
    int activeConnections,
    double errorRate,
    double compositeScore,
    double softmaxProbability,
    HealthStatus status,
    long timestampMs
) {}
```

---

## 5. Grafana Dashboard Panels & PromQL Reference

The included Grafana dashboard (`alb-research-dashboard.json`) visualizes the empirical comparison across algorithms:

### Panel 1: Real-Time Traffic Distribution ($req/s$)
Shows whether traffic dynamically diverts away from degraded instances.
```promql
sum by (instance) (rate(http_server_requests_seconds_count{role="worker"}[5s]))
```

### Panel 2: Latency Percentiles ($P_{50}, P_{95}, P_{99}$)
Measures user-perceived performance and tail latency degradation.
```promql
# P95 Tail Latency per instance
histogram_quantile(0.95, sum by (le, instance) (rate(http_server_requests_seconds_bucket[10s])))

# Gateway End-to-End P99 Latency
histogram_quantile(0.99, sum by (le) (rate(http_server_requests_seconds_bucket{role="gateway"}[10s])))
```

### Panel 3: Instantaneous Node CPU Saturation
Verifies whether the adaptive load balancer prevents nodes from exceeding saturation thresholds.
```promql
system_cpu_usage{role="worker"} * 100
```

### Panel 4: Computed Health Scores ($Score(i)$)
Tracks the real-time fitness scores evaluated by the Gateway's adaptive engine.
```promql
alb_gateway_instance_health_score
```

### Panel 5: Error Rate (%)
Measures 5xx fault containment during node failure injection.
```promql
sum by (instance) (rate(http_server_requests_seconds_count{status=~"5.."}[10s])) 
/ 
sum by (instance) (rate(http_server_requests_seconds_count[10s])) * 100
```
