# Service Contracts & REST APIs

## 1. Overview

This document specifies all HTTP API contracts across the system:
1. **Client Gateway API:** Public endpoints through which clients send requests.
2. **Gateway Management API:** Administrative and research endpoints to dynamically switch algorithms, modify weights, tune hyperparameters, and inspect telemetry snapshots.
3. **Worker Service API:** Downstream microservice workloads (CPU-bound, I/O-bound).
4. **Chaos Injection API:** Controlled fault-injection endpoints on worker instances for automated experiment execution.

---

## 2. Gateway Client API

### 2.1 Forwarded Business Routes
All requests sent to `/api/v1/**` on the Gateway (`:8080`) are intercepted by the routing engine and dispatched to an available worker instance (`alb-worker-X`).

```http
GET /api/v1/compute?iterations=500000 HTTP/1.1
Host: localhost:8080
```

#### Response:
```json
{
  "status": "COMPLETED",
  "workerId": "worker-1",
  "executionTimeMs": 42.6,
  "resultHash": "a94a8fe5ccb19ba61c4c0873d391e987982fbbd3"
}
```

---

## 3. Gateway Management & Research API

The Gateway exposes management endpoints on port `8080` (under `/admin/routing`) for automated experiment orchestration.

### 3.1 Get Current Routing Status
`GET /admin/routing/status`

Retrieves active strategy, weights, telemetry snapshots, and calculated probabilities.

#### Response:
```json
{
  "activeStrategy": "ADAPTIVE_MULTI_METRIC",
  "refreshIntervalMs": 500,
  "hysteresisDelta": 0.10,
  "softmaxTemperature": 0.25,
  "weights": {
    "cpu": 0.25,
    "memory": 0.10,
    "latency": 0.35,
    "connections": 0.10,
    "errors": 0.20
  },
  "instances": [
    {
      "instanceId": "worker-1:8081",
      "uri": "http://alb-worker-1:8081",
      "status": "HEALTHY",
      "metrics": {
        "cpuUsage": 0.28,
        "memoryUsageRatio": 0.34,
        "latencyEmaMs": 35.2,
        "activeConnections": 8,
        "errorRate": 0.00
      },
      "compositeScore": 0.884,
      "routingProbability": 0.582
    },
    {
      "instanceId": "worker-2:8082",
      "uri": "http://alb-worker-2:8082",
      "status": "DEGRADED",
      "metrics": {
        "cpuUsage": 0.89,
        "memoryUsageRatio": 0.62,
        "latencyEmaMs": 280.5,
        "activeConnections": 34,
        "errorRate": 0.04
      },
      "compositeScore": 0.312,
      "routingProbability": 0.065
    },
    {
      "instanceId": "worker-3:8083",
      "uri": "http://alb-worker-3:8083",
      "status": "HEALTHY",
      "metrics": {
        "cpuUsage": 0.32,
        "memoryUsageRatio": 0.38,
        "latencyEmaMs": 41.0,
        "activeConnections": 11,
        "errorRate": 0.00
      },
      "compositeScore": 0.841,
      "routingProbability": 0.353
    }
  ]
}
```

---

### 3.2 Switch Routing Strategy
`POST /admin/routing/strategy`

Dynamically switches the active algorithm with immediate effect.

#### Request Body:
```json
{
  "strategy": "ROUND_ROBIN" 
}
```
*Supported values:* `ROUND_ROBIN`, `WEIGHTED_ROUND_ROBIN`, `LEAST_CONNECTIONS`, `ADAPTIVE_MULTI_METRIC`.

#### Response:
```json
{
  "status": "SUCCESS",
  "previousStrategy": "ADAPTIVE_MULTI_METRIC",
  "activeStrategy": "ROUND_ROBIN",
  "updatedAt": "2026-09-25T17:10:00Z"
}
```

---

### 3.3 Update Adaptive Weights
`POST /admin/routing/weights`

Updates scoring weights for sensitivity analysis (Experiment 7). Must sum to $1.00 \pm 0.001$.

#### Request Body:
```json
{
  "cpu": 0.40,
  "memory": 0.10,
  "latency": 0.20,
  "connections": 0.10,
  "errors": 0.20
}
```

#### Response:
```json
{
  "status": "UPDATED",
  "weights": {
    "cpu": 0.40,
    "memory": 0.10,
    "latency": 0.20,
    "connections": 0.10,
    "errors": 0.20
  }
}
```

---

### 3.4 Tune Hyperparameters
`POST /admin/routing/config`

#### Request Body:
```json
{
  "refreshIntervalMs": 250,
  "hysteresisDelta": 0.15,
  "softmaxTemperature": 0.30
}
```

---

## 4. Worker Microservice API (`demo-worker`)

Workers expose endpoints representing realistic computational workloads:

### 4.1 Compute-Intensive Endpoint
`GET /api/compute`

Executes CPU-bound mathematical operations (e.g. Monte Carlo $\pi$ estimation or SHA-256 rounds).
- **Query Parameters:**
  - `iterations` (integer, default: 100,000)

### 4.2 I/O Simulated Delay Endpoint
`GET /api/io-wait`

Executes non-blocking/blocking artificial latency simulating downstream database latency.
- **Query Parameters:**
  - `delayMs` (integer, default: 50)
  - `jitterMs` (integer, default: 10)

---

## 5. Worker Chaos & Fault Injection API

Each worker provides internal hooks to simulate degradation deterministically without needing OS-level access:

### 5.1 Inject Artificial Latency
`POST /chaos/latency`

#### Request Body:
```json
{
  "delayMs": 350,
  "jitterMs": 50,
  "probability": 1.0,
  "durationSeconds": 60
}
```

### 5.2 Burn CPU Cycles
`POST /chaos/cpu-burn`

Spawns background threads running busy loops to simulate host CPU saturation.

#### Request Body:
```json
{
  "threads": 4,
  "targetCpuPercent": 90,
  "durationSeconds": 45
}
```

### 5.3 Inject Error Burst (HTTP 500)
`POST /chaos/error-burst`

Forces the worker to return HTTP 500 Internal Server Error for a fraction of incoming requests.

#### Request Body:
```json
{
  "errorRate": 0.50,
  "durationSeconds": 30
}
```

### 5.4 Reset All Faults
`POST /chaos/reset`

Immediately terminates all active chaos simulations, restoring the instance to normal operation.
