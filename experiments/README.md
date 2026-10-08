# ALB-M Experimental Benchmarking & Orchestration Harness

This directory contains the automated scientific load-generation, fault-injection, and benchmarking harness for the **Adaptive Load Balancer for Microservices (ALB-M)** project (Sprint 6).

---

## 1. Directory Structure

```
experiments/
├── orchestrator.py            # Master Python experiment orchestrator (CLI)
├── README.md                  # Comprehensive harness & scenario guide
├── scenarios/                 # k6 JavaScript load testing scripts
│   ├── config.js              # Common target & request helper module
│   ├── exp1_baseline.js       # Scenario 1: Baseline steady-state (150 req/s, homogeneous)
│   ├── exp2_scalability.js    # Scenario 2: Escalating scalability (100 -> 3000 req/s ramp)
│   ├── exp3_degradation.js    # Scenario 3: Single-node heterogeneous degradation (600 req/s)
│   ├── exp4_burst.js          # Scenario 4: Sudden traffic burst (3500 req/s surge)
│   ├── exp5_failure.js        # Scenario 5: Worker node termination & hard failure (800 req/s)
│   ├── exp6_scrape_frequency.js # Scenario 6: Telemetry scrape interval trade-off analysis
│   └── exp7_weight_sensitivity.js # Scenario 7: Scoring weight sensitivity evaluation
├── tests/
│   └── test_orchestrator.py   # Unit & regression tests for orchestrator & parsing logic
└── results/
    └── raw/                   # Output folder for raw CSV summaries and detailed JSON runs
        ├── benchmark_summary.csv
        └── <run_id>_detail.json
```

---

## 2. Benchmark Scenarios Catalog

| Scenario ID | Script | Workload Profile | Fault / Chaos Injection Event | Key Objective |
| :--- | :--- | :--- | :--- | :--- |
| **exp1** | `exp1_baseline.js` | Constant 150 req/s, 10 min | None (All 3 nodes healthy) | Quantify algorithm routing overhead under homogeneous baseline conditions. |
| **exp2** | `exp2_scalability.js` | Stepped arrival: 100 $\to$ 500 $\to$ 1000 $\to$ 2000 $\to$ 3000 req/s (2 min each) | None (Scalability stress) | Identify throughput saturation ceiling and queue breakdown point. |
| **exp3** | `exp3_degradation.js` | Constant 600 req/s, 10 min | $t=120\text{s}$: Inject Worker 2 with +300ms latency & 85% CPU burn. $t=480\text{s}$: Reset fault. | Measure $T_{\text{adapt}}$, tail latency containment ($P_{95}/P_{99}$), and traffic shedding. |
| **exp4** | `exp4_burst.js` | 100 req/s (60s) $\to$ 3500 req/s (30s) $\to$ 100 req/s (60s) | Instantaneous traffic spike | Test reactive connection rebalancing and prevention of cascading crash. |
| **exp5** | `exp5_failure.js` | Constant 800 req/s | Mid-run simulated node crash / 100% error burst on Worker 2 | Measure error rate containment and quarantine/eviction speed. |
| **exp6** | `exp6_scrape_frequency.js` | Constant 400 req/s under degradation | Scrape interval swept across $T_{\text{refresh}} \in \{100, 250, 500, 1000, 2000, 5000\}\text{ms}$ | Quantify trade-off between adaptation reaction speed and scrape overhead. |
| **exp7** | `exp7_weight_sensitivity.js` | Constant 500 req/s under mixed CPU & latency stress | Gateway weights rotated across Latency-Heavy, CPU-Heavy, Balanced, and Error-Focused | Evaluate system sensitivity to objective weight vector priorities. |

---

## 3. Supported Routing Algorithms

The orchestrator dynamically reconfigures the Gateway via `POST /admin/routing/strategy` before each benchmark run:
- `ROUND_ROBIN` (RR)
- `SMOOTH_WEIGHTED_ROUND_ROBIN` (WRR)
- `LEAST_CONNECTIONS` (LC)
- `ADAPTIVE_MULTI_METRIC` (MM-AR)

---

## 4. Running Benchmarks

### 4.1 Prerequisites
Ensure the ALB-M cluster is running:
```bash
docker compose -f docker/docker-compose.yml up -d
```
All services should be healthy:
- Service Registry: `http://localhost:8761`
- Gateway: `http://localhost:8080`
- Worker 1 / 2 / 3: `http://localhost:8081`, `8082`, `8083`
- Prometheus: `http://localhost:9090`
- Grafana: `http://localhost:3000`

### 4.2 Single Scenario Execution
Run Experiment 1 with Round Robin:
```bash
python experiments/orchestrator.py --scenario exp1 --strategy ROUND_ROBIN
```

Run Experiment 3 with the Adaptive Multi-Metric Strategy:
```bash
python experiments/orchestrator.py --scenario exp3 --strategy ADAPTIVE_MULTI_METRIC
```

### 4.3 Quick Verification Mode (Smoke Test)
For rapid end-to-end verification and automated checks:
```bash
python experiments/orchestrator.py --scenario exp3 --strategy ADAPTIVE_MULTI_METRIC --quick
```

### 4.4 Full Benchmark Matrix Run
To execute all scenarios across all four algorithms:
```bash
python experiments/orchestrator.py --scenario all --strategy all --replications 5
```

### 4.5 Execution Engine Modes (`--k6-mode`)
The orchestrator automatically selects the most suitable k6 runner:
- `auto` (Default): Uses host `k6` if available, otherwise seamlessly uses Docker container `grafana/k6:latest`.
- `docker`: Forces execution inside `grafana/k6:latest` attached to `docker_alb-net`.
- `local`: Forces execution using the host machine's native `k6` executable.
- `mock`: Standalone dry-run mode for unit test pipelines without requiring Docker or k6.

---

## 5. Output Datasets & Metrics

Results are recorded in `experiments/results/raw/`:
- **`benchmark_summary.csv`**: Appendable tabular dataset containing:
  - `run_id`, `scenario`, `strategy`, `replication`, `timestamp`, `elapsed_seconds`
  - `total_requests`, `throughput_req_sec`, `error_rate_pct`
  - `p50_latency_ms`, `p90_latency_ms`, `p95_latency_ms`, `p99_latency_ms`, `avg_latency_ms`, `max_latency_ms`
  - `jains_fairness_index` ($\mathcal{J} = \frac{(\sum x_i)^2}{N \sum x_i^2}$)
  - `active_instances_count`, `status`
- **`<run_id>_detail.json`**: Complete snapshot combining k6 raw metrics, thresholds, and Gateway node telemetry scores.

---

## 6. Running Harness Unit Tests

```bash
python experiments/tests/test_orchestrator.py
```
Validates mathematical calculations (Jain's index), k6 summary parsers, scenario mapping integrity, and mock execution engines.
