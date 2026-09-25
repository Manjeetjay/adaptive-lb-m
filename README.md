# Adaptive Load Balancer for Microservices (ALB-M)

> **Adaptive Load Balancing for Microservice Architectures Using Real-Time Service Metrics**

A research-grade Layer-7 adaptive routing fabric that dynamically optimizes microservice traffic dispatch using multi-dimensional runtime telemetry (CPU, heap memory, rolling latency, active concurrency, and error rates).

---

## 📖 Complete Documentation Suite

All system specifications, mathematical formulations, benchmarks, and research outlines are located in the [`docs/`](file:///m:/code/projects/automated-lb/docs/README.md) directory:

1. [**System Architecture & Design**](file:///m:/code/projects/automated-lb/docs/01-system-architecture.md) — Topology, Spring Cloud Gateway integration, asynchronous metric caching, health monitor, Docker network.
2. [**Routing Algorithms Specification**](file:///m:/code/projects/automated-lb/docs/02-routing-algorithms-spec.md) — Formulations for Round Robin, WRR, Least Connections, and Multi-Metric Adaptive Scoring with hysteresis and Softmax dispatch.
3. [**Metrics & Observability Pipeline**](file:///m:/code/projects/automated-lb/docs/03-metrics-and-observability.md) — Micrometer, Prometheus scrape profiles, Gateway metric cache, Grafana panels, and PromQL queries.
4. [**Service Contracts & REST APIs**](file:///m:/code/projects/automated-lb/docs/04-service-contracts-and-apis.md) — Gateway traffic endpoints, runtime algorithm switching API, worker workload endpoints, and chaos injection APIs.
5. [**Experimental Methodology & Benchmark**](file:///m:/code/projects/automated-lb/docs/05-experimental-framework.md) — Scientific testbed design, k6 load scenarios, 7 empirical experiments, and statistical hypothesis tests.
6. [**Chaos & Fault Injection Suite**](file:///m:/code/projects/automated-lb/docs/06-chaos-and-fault-injection.md) — Programmatic CPU burn, artificial latency/jitter, error bursts, container termination, and adaptation time ($T_{\text{adapt}}$) tracking.
7. [**Implementation Roadmap & Milestones**](file:///m:/code/projects/automated-lb/docs/07-implementation-roadmap.md) — Phase 1 to Phase 9 engineering plan, module layout, dependencies, and test criteria.
8. [**Research Paper Outline & Thesis Guide**](file:///m:/code/projects/automated-lb/docs/08-research-paper-outline.md) — 15-section academic paper outline, formal hypotheses ($H_1-H_4$), target venues (IEEE Cloud, ACM SoCC), and figure blueprints.

---

## 🛠 Tech Stack

- **Gateway & Routing:** Java 21, Spring Boot 3.3.x, Spring Cloud Gateway (WebFlux / Reactor Netty)
- **Service Discovery:** Spring Cloud Netflix Eureka
- **Telemetry & Monitoring:** Micrometer, Prometheus, Grafana
- **Load Generation:** k6
- **Chaos Engineering:** Custom Spring WebFilter & Thread Pool Chaos Harness
- **Analysis & Plotting:** Python 3, Pandas, NumPy, SciPy, Matplotlib
- **Orchestration:** Docker Compose
