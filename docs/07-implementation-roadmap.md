# Implementation Roadmap & Engineering Milestones

## 1. Project Organization & Directory Layout

The project is structured as a Maven multi-module repository with supporting infrastructure, load generation, and analytics directories:

```
automated-lb/
├── pom.xml                               # Parent POM (Spring Boot 3.3.x, Spring Cloud 2023.x)
├── docs/                                 # Complete Technical & Research Documentation
├── service-registry/                     # Eureka Service Discovery Server
│   ├── pom.xml
│   └── src/main/java/com/alb/registry/
├── gateway/                              # Spring Cloud Gateway + Adaptive Routing Core
│   ├── pom.xml
│   └── src/main/java/com/alb/gateway/
│       ├── routing/                      # Route filters & WebFlux interceptors
│       ├── algorithms/                   # RR, WRR, LeastConnections, AdaptiveStrategy
│       ├── telemetry/                    # MetricCache, Background poller, Normalizer
│       ├── health/                       # HealthEvaluator, Quarantine manager
│       └── admin/                        # REST management & dynamic strategy switching
├── demo-worker/                          # Target Microservice with Workload & Chaos Endpoints
│   ├── pom.xml
│   └── src/main/java/com/alb/worker/
│       ├── controller/                   # /compute, /io-wait endpoints
│       ├── chaos/                        # /chaos/latency, /chaos/cpu-burn endpoints
│       └── metrics/                      # Custom Micrometer gauges & counters
├── docker/                               # Containerization & Orchestration
│   ├── docker-compose.yml                # Orchestrates Eureka, Gateway, 3 Workers, Prometheus, Grafana
│   ├── prometheus/
│   │   └── prometheus.yml                # 1s high-resolution scrape config
│   └── grafana/
│       ├── provisioning/
│       └── dashboards/
│           └── alb-research-dashboard.json
├── experiments/                          # Scientific Benchmarking Suite
│   ├── scenarios/                        # k6 JavaScript load test scripts (exp1 to exp7)
│   ├── orchestrator.py                   # Automated experiment runner & chaos coordinator
│   ├── analysis/                         # Python scripts (Pandas, Matplotlib, SciPy)
│   └── results/                          # CSV exports and generated CDF / boxplot figures
└── README.md
```

---

## 2. Phase-by-Phase Execution Plan

```
Phase 1 ──▶ Phase 2 ──▶ Phase 3 ──▶ Phase 4 ──▶ Phase 5 ──▶ Phase 6 ──▶ Phase 7 ──▶ Phase 8 ──▶ Phase 9
[Skeleton]  [Eureka+GW] [Baselines] [Metrics]   [Adaptive]  [Chaos]     [k6 Load]   [Execution] [Analysis]
```

### Phase 1: Repository Skeleton & Dependency Management
- **Goal:** Set up parent Maven project with unified dependency versions.
- **Tech Stack:** Java 21, Spring Boot `3.3.3`, Spring Cloud `2023.0.3`.
- **Key Dependencies:**
  - `spring-cloud-starter-gateway` (WebFlux, Netty)
  - `spring-cloud-starter-netflix-eureka-server` / `client`
  - `spring-boot-starter-actuator`
  - `micrometer-registry-prometheus`
  - `lombok`
- **Acceptance Criteria:** `mvn clean compile` succeeds across all modules.

---

### Phase 2: Service Registry & Gateway Forwarding
- **Goal:** Establish dynamic discovery and reverse-proxy routing.
- **Tasks:**
  1. Implement `service-registry` (`@EnableEurekaServer`, port `8761`).
  2. Implement `demo-worker` registering with Eureka as `DEMO-SERVICE`.
  3. Configure Gateway to discover `DEMO-SERVICE` instances dynamically.
- **Acceptance Criteria:** `curl http://localhost:8080/api/v1/compute` returns response from a worker instance.

---

### Phase 3: Traditional Routing Strategies
- **Goal:** Implement the three baseline algorithms under a unified interface.
- **Tasks:**
  1. Define `RoutingStrategy` interface.
  2. Implement `RoundRobinStrategy` (atomic modulo counter).
  3. Implement `SmoothWeightedRoundRobinStrategy` (Nginx-style weight decay).
  4. Implement `LeastConnectionsStrategy` (reactive tracking of in-flight requests).
  5. Add REST endpoint `POST /admin/routing/strategy` to switch algorithms at runtime.
- **Acceptance Criteria:** Unit tests verify strict cyclic distribution for RR and proper connection-based dispatch for LC.

---

### Phase 4: Telemetry Pipeline & Observability
- **Goal:** Instrument services and stand up Prometheus & Grafana.
- **Tasks:**
  1. Expose custom Micrometer gauges in `demo-worker` (`alb_worker_active_requests`).
  2. Configure Prometheus Docker container with 1s scrape frequency.
  3. Build Grafana dashboard with panels for request throughput, $P_{50}/P_{95}/P_{99}$ latency, CPU utilization, and error rates.
- **Acceptance Criteria:** Grafana displays live telemetry updating every second when load is applied.

---

### Phase 5: Multi-Metric Adaptive Strategy (MM-AR)
- **Goal:** Implement the primary research routing engine.
- **Tasks:**
  1. Build asynchronous `MetricCache` updated by a background poller (`Flux.interval`).
  2. Implement metric normalizers for CPU, Memory, Latency EMA, Concurrency, and Errors.
  3. Implement composite scoring: $Score(i) = \sum w_k S_k(i)$.
  4. Implement Softmax probabilistic distribution: $P(i) = \frac{\exp(Score(i)/\tau)}{\sum \exp(Score(j)/\tau)}$.
  5. Add Hysteresis dampening ($\delta = 0.10$) and degradation quarantine tiering.
- **Acceptance Criteria:** Gateway dynamically shifts traffic away from a worker when artificial latency or CPU load rises.

---

### Phase 6: Chaos & Fault Injection Suite
- **Goal:** Provide programmatic hooks for reproducible failure scenarios.
- **Tasks:**
  1. Add `ChaosController` and `ChaosService` inside `demo-worker`.
  2. Implement CPU burn (multi-threaded ALU loops).
  3. Implement latency injection filter with configurable delay and Gaussian jitter.
  4. Implement HTTP 500 error rate generator.
- **Acceptance Criteria:** Calling `POST /chaos/latency` results in instantaneous measured latency increase in Prometheus.

---

### Phase 7: Automated k6 Benchmark Harness
- **Goal:** Automate load generation across all 7 experimental scenarios.
- **Tasks:**
  1. Write k6 JavaScript scripts for Scenarios 1–7.
  2. Write Python orchestrator (`orchestrator.py`) to automate sequential execution:
     - Select algorithm $\to$ Start k6 $\to$ Trigger chaos $\to$ Collect metrics $\to$ Reset state.
- **Acceptance Criteria:** Orchestrator executes a multi-stage benchmark autonomously and exports raw execution logs.

---

### Phase 8: Experiment Execution & Data Gathering
- **Goal:** Execute the full benchmark matrix (7 scenarios $\times$ 4 algorithms $\times$ 5 replications = 140 runs).
- **Tasks:**
  1. Run comprehensive test suite in isolated Docker environment.
  2. Export Prometheus time-series and k6 summary JSON/CSV outputs into `experiments/results/`.
- **Acceptance Criteria:** 140 clean datasets saved with zero unhandled test harness failures.

---

### Phase 9: Statistical Data Analysis & Scientific Figures
- **Goal:** Process raw telemetry and generate publication-quality figures.
- **Tasks:**
  1. Implement Python analysis pipeline (`analysis/plot_results.py`) using Pandas and SciPy.
  2. Compute $P_{50}, P_{95}, P_{99}$ latency, throughput, goodput, and Jain's Fairness Index.
  3. Perform Wilcoxon Signed-Rank tests ($p$-values) and calculate Cohen's $d$.
  4. Generate publication graphs:
     - Latency Cumulative Distribution Function (CDF) curves.
     - Box plots of tail latency across algorithms.
     - Time-series adaptation curves showing $T_{\text{adapt}}$ and $T_{\text{recover}}$.
     - Radar chart of multi-objective performance.
- **Acceptance Criteria:** Automated generation of all charts required for the research paper.
