# Adaptive Load Balancer for Microservices (ALB-M)
## Technical Documentation & Research Engineering Blueprint

Welcome to the comprehensive documentation suite for **Adaptive Load Balancing for Microservice Architectures Using Real-Time Service Metrics**.

This repository contains the design, implementation, evaluation framework, and research artifacts for a next-generation adaptive routing system designed to optimize tail latency ($P_{95}/P_{99}$), throughput, and fault resilience across dynamic microservice clusters.

---

## 📚 Documentation Index

| Document | Title | Purpose |
| :--- | :--- | :--- |
| [01-system-architecture.md](file:///m:/code/projects/automated-lb/docs/01-system-architecture.md) | **System Architecture & Design** | Component topology, Spring Cloud Gateway integration, asynchronous metric caching, health monitor, Docker network. |
| [02-routing-algorithms-spec.md](file:///m:/code/projects/automated-lb/docs/02-routing-algorithms-spec.md) | **Routing Algorithms Specification** | Mathematical formulations for Round Robin, WRR, Least Connections, and Multi-Metric Adaptive Scoring with hysteresis and probabilistic dispatch. |
| [03-metrics-and-observability.md](file:///m:/code/projects/automated-lb/docs/03-metrics-and-observability.md) | **Metrics & Observability Pipeline** | Micrometer, Prometheus scraping, Gateway background metric cache, Grafana dashboard panels, and query definitions. |
| [04-service-contracts-and-apis.md](file:///m:/code/projects/automated-lb/docs/04-service-contracts-and-apis.md) | **Service Contracts & REST APIs** | Gateway endpoints, runtime algorithm switching API, worker service contract, and fault injection endpoints. |
| [05-experimental-framework.md](file:///m:/code/projects/automated-lb/docs/05-experimental-framework.md) | **Experimental Methodology & Benchmark** | Scientific testbed design, k6 load scenarios, 7 empirical experiments, statistical hypothesis tests ($H_1-H_4$). |
| [06-chaos-and-fault-injection.md](file:///m:/code/projects/automated-lb/docs/06-chaos-and-fault-injection.md) | **Chaos & Fault Injection Suite** | Degradation simulations (CPU burn, latency spike, memory leak, HTTP 500 bursts), container termination, and adaptation time calculations. |
| [07-implementation-roadmap.md](file:///m:/code/projects/automated-lb/docs/07-implementation-roadmap.md) | **Implementation Roadmap & Milestones** | Phase 1 through Phase 9 engineering plan, module directory layout, Spring Boot dependency blueprints, and test criteria. |
| [08-research-paper-outline.md](file:///m:/code/projects/automated-lb/docs/08-research-paper-outline.md) | **Research Paper Outline & Thesis Guide** | 15-section academic paper structure, formal hypothesis formulations, related work positioning, and publication strategy. |
| [09-project-timeline-and-tasks.md](file:///m:/code/projects/automated-lb/docs/09-project-timeline-and-tasks.md) | **Project Timeline & Sequential Tasks** | 8-sprint milestone plan, dependency graph, task catalog (T1.1–T8.5), and risk mitigation. |

---

## 🎯 The Three-Layer Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    RESEARCH LAYER                           │
│  Hypothesis testing (H1-H4), empirical benchmarking,        │
│  statistical validation, CDF latency analysis, trade-off    │
│  evaluations (Adaptation Speed vs. Monitoring Overhead).   │
└──────────────────────────────┬──────────────────────────────┘
                               │
┌──────────────────────────────▼──────────────────────────────┐
│                   ALGORITHM LAYER                           │
│  • Round Robin               • Least Connections            │
│  • Weighted Round Robin      • Multi-Metric Adaptive Router │
│  Normalizer, Dynamic Scorer, Hysteresis Dampener, Softmax.   │
└──────────────────────────────┬──────────────────────────────┘
                               │
┌──────────────────────────────▼──────────────────────────────┐
│                    SYSTEM LAYER                             │
│  Spring Cloud Gateway, Eureka Registry, Actuator/Micrometer,│
│  Prometheus Scraper, Metrics Cache, Worker Replicas, Docker.│
└─────────────────────────────────────────────────────────────┘
```

---

## 🚀 Key Research Questions

1. **Primary Question:** Can adaptive load balancing based on real-time service conditions improve latency, throughput, and resource utilization compared with traditional load-balancing strategies under dynamic workloads?
2. **Subquestions:**
   - **$Q_1$ (Tail Latency):** How significantly does adaptive routing truncate $P_{95}$ and $P_{99}$ latency tails under heterogeneous worker degradation?
   - **$Q_2$ (Adaptation Reaction):** What is the exact mean time to adapt ($T_{\text{adapt}}$) from the onset of node degradation to traffic re-balancing?
   - **$Q_3$ (Observability Trade-off):** What is the trade-off curve between Prometheus scrape interval ($100\text{ ms} \leftrightarrow 5000\text{ ms}$) and routing convergence vs CPU/network overhead?
   - **$Q_4$ (Instability & Flapping):** How effectively do hysteresis thresholds ($\delta$) and probabilistic routing prevent traffic oscillation (the "thundering herd" phenomenon)?
