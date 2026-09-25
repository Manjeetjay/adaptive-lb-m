# Experimental Methodology & Benchmark Suite

## 1. Scientific Research Design

The core objective of this study is to test the following central thesis:

> **Hypothesis ($H_1$):** Under heterogeneous service degradation, sudden traffic bursts, and worker node failures, a multi-metric adaptive load-balancing strategy statistically significantly reduces tail latency ($P_{95}, P_{99}$) and error rates compared to static Round Robin, Weighted Round Robin, and Least Connections strategies, with acceptable control plane overhead.

---

## 2. Experimental Metrics & Formulas

To ensure rigorous quantitative comparison, all benchmarks record the following primary evaluation metrics:

### 2.1 Tail Latency Distribution
- **$P_{50}$ (Median Latency):** Typical user experience.
- **$P_{95}$ & $P_{99}$ (Tail Latency):** Worst-case user experience under queuing buildup.
- Measured in milliseconds at the client/gateway boundary.

### 2.2 System Throughput & Goodput
$$\text{Throughput} = \frac{N_{\text{total requests}}}{\Delta t_{\text{seconds}}}$$
$$\text{Goodput} = \frac{N_{\text{successful HTTP 2xx requests}}}{\Delta t_{\text{seconds}}}$$
$$\text{Error Rate } (E) = \frac{N_{\text{5xx}} + N_{\text{timeouts}}}{N_{\text{total requests}}} \times 100\%$$

### 2.3 Traffic Imbalance Metric: Jain's Fairness Index ($\mathcal{J}$)
Measures how evenly or proportionally requests are distributed among healthy nodes:
$$\mathcal{J}(x_1, x_2, \dots, x_N) = \frac{\left( \sum_{i=1}^N x_i \right)^2}{N \sum_{i=1}^N x_i^2}$$
Where $x_i$ is the request count on instance $i$.
- $\mathcal{J} = 1.0$: Perfectly balanced distribution.
- $\mathcal{J} \to \frac{1}{N}$: Severe polarization.

### 2.4 Adaptation Reaction Time ($T_{\text{adapt}}$)
Defined as the elapsed time from the instant fault injection begins ($t_{\text{fault}}$) to the instant traffic dispatched to the degraded node drops below $10\%$ of total incoming traffic ($t_{\text{stabilized}}$):
$$T_{\text{adapt}} = t_{\text{stabilized}} - t_{\text{fault}}$$

### 2.5 Recovery Time ($T_{\text{recover}}$)
The elapsed time from fault cessation ($t_{\text{resolved}}$) until the restored node safely resumes receiving its proportional baseline share of traffic:
$$T_{\text{recover}} = t_{\text{restored}} - t_{\text{resolved}}$$

---

## 3. Benchmark Experiments Specification

Each experiment is executed across all four algorithms:
1. `ROUND_ROBIN` (RR)
2. `WEIGHTED_ROUND_ROBIN` (WRR)
3. `LEAST_CONNECTIONS` (LC)
4. `ADAPTIVE_MULTI_METRIC` (MM-AR)

```
Test Run Matrix: 7 Scenarios × 4 Algorithms × 5 Replications = 140 Executions
```

---

### Experiment 1: Baseline Steady-State Workload
- **Workload Profile:** Constant 150 req/s for 10 minutes.
- **Node Conditions:** All 3 worker nodes are completely healthy and identical.
- **Objective:** Quantify routing algorithm overhead and verify that the adaptive strategy does not degrade baseline performance when all nodes are healthy.

---

### Experiment 2: Escalating Scalability & Saturation Curve
- **Workload Profile:** Step-wise increase:
  - 100 req/s (2 min) $\to$ 500 req/s (2 min) $\to$ 1000 req/s (2 min) $\to$ 2000 req/s (2 min) $\to$ 3000 req/s (2 min).
- **Node Conditions:** Static homogeneous nodes.
- **Objective:** Identify the saturation ceiling and breaking point for each algorithm.

---

### Experiment 3: Single-Node Heterogeneous Degradation
- **Workload Profile:** 600 req/s steady-state traffic for 10 minutes.
- **Chaos Injection:**
  - $t = 0\text{s}$ to $120\text{s}$: All nodes healthy.
  - $t = 120\text{s}$: Inject **Worker 2** with 300ms artificial latency and 85% CPU load.
  - $t = 480\text{s}$: Terminate fault injection on Worker 2.
- **Primary Measurements:** $P_{95}/P_{99}$ latency, $T_{\text{adapt}}$, and traffic diversion percentage.

---

### Experiment 4: Sudden Traffic Burst
- **Workload Profile:**
  - 100 req/s baseline for 60s.
  - Sudden instantaneous surge to **3500 req/s** for 30s.
  - Return to 100 req/s for 60s.
- **Objective:** Test queue dampening, active connection rebalancing, and prevention of cascading failure.

---

### Experiment 5: Catastrophic Worker Node Failure
- **Workload Profile:** 800 req/s constant load.
- **Fault Event:** Hard kill of container `alb-worker-2` (`docker stop alb-worker-2`).
- **Measurements:** Number of dropped requests, error rate ($E\%$), and time until complete eviction from gateway routing table.

---

### Experiment 6: Telemetry Scrape Frequency Trade-Off Analysis
- **Workload:** Re-run Experiment 3 using the Adaptive Strategy across 6 telemetry intervals:
  - $T_{\text{refresh}} \in \{100\text{ ms}, 250\text{ ms}, 500\text{ ms}, 1000\text{ ms}, 2000\text{ ms}, 5000\text{ ms}\}$.
- **Evaluation Dimensions:**
  - Adaptation Speed ($T_{\text{adapt}}$).
  - CPU & Network overhead induced on workers by Actuator scraping.
  - Routing stability (flapping index).

---

### Experiment 7: Scoring Weight Sensitivity Analysis
- **Workload:** Run under mixed CPU and latency stress.
- **Evaluated Weight Configurations:**
  1. **Latency-Heavy:** $w_{\text{lat}} = 0.60, w_{\text{cpu}} = 0.15, w_{\text{err}} = 0.15, w_{\text{conn}} = 0.05, w_{\text{mem}} = 0.05$
  2. **CPU-Heavy:** $w_{\text{cpu}} = 0.60, w_{\text{lat}} = 0.15, w_{\text{err}} = 0.15, w_{\text{conn}} = 0.05, w_{\text{mem}} = 0.05$
  3. **Balanced (Default):** $w_{\text{lat}} = 0.35, w_{\text{cpu}} = 0.25, w_{\text{err}} = 0.20, w_{\text{conn}} = 0.10, w_{\text{mem}} = 0.10$
  4. **Error-Focused:** $w_{\text{err}} = 0.60, w_{\text{lat}} = 0.15, w_{\text{cpu}} = 0.15, w_{\text{conn}} = 0.05, w_{\text{mem}} = 0.05$

---

## 4. k6 Load Testing Script Example (`exp3_degradation.js`)

```javascript
import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  scenarios: {
    constant_request_rate: {
      executor: 'constant-arrival-rate',
      rate: 600,
      timeUnit: '1s',
      duration: '10m',
      preAllocatedVUs: 50,
      maxVUs: 200,
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.02'],
    http_req_duration: ['p(95)<400'],
  },
};

export default function () {
  const res = http.get('http://localhost:8080/api/v1/compute?iterations=200000');
  check(res, {
    'status is 200': (r) => r.status === 200,
  });
}
```

---

## 5. Statistical Validation Protocol

To validate academic claims without bias:
1. **Normality Test:** Use the **Shapiro-Wilk test** to determine if latency distributions are Gaussian. (Typically, latency is long-tailed and non-Gaussian).
2. **Hypothesis Testing:**
   - If non-normal, apply the **Wilcoxon Signed-Rank Test** (paired) or **Mann-Whitney U Test** (independent) to compare $P_{95}$ distributions between Adaptive and Round Robin.
   - Significance threshold: $\alpha = 0.01$ ($p < 0.01$).
3. **Effect Size:** Calculate **Cliff's Delta** ($\delta$) or **Cohen's $d$** to quantify the magnitude of improvement beyond mere statistical significance.
