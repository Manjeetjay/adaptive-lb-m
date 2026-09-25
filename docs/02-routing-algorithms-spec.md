# Routing Algorithms Specification

## 1. Overview

To scientifically validate whether an **Adaptive Load Balancer** improves system performance, we evaluate four distinct routing strategies under identical workload and failure scenarios.

This document formally specifies the mathematical models, state representations, pseudocode, and stability algorithms for all four implementations.

---

## 2. Algorithm 1: Round Robin (RR)

### 2.1 Description
The baseline static algorithm. It dispatches incoming requests cyclically across all available, registered instances without regard to server load, response time, or active concurrency.

### 2.2 Mathematical Model & State
Let the candidate instances pool be $I = \{i_1, i_2, \dots, i_N\}$ where $N = |I|$.
Let $C \in \mathbb{N}_0$ be a monotonically increasing atomic sequence counter initialized to $0$.

The index of the selected instance for the $k$-th request is:
$$\text{Index}(k) = C \pmod N$$
$$C \leftarrow C + 1$$

### 2.3 Java Pseudocode
```java
public class RoundRobinStrategy implements RoutingStrategy {
    private final AtomicInteger counter = new AtomicInteger(0);

    @Override
    public ServiceInstance select(List<ServiceInstance> instances, MetricSnapshot snapshot) {
        if (instances.isEmpty()) return null;
        int idx = Math.abs(counter.getAndIncrement() % instances.size());
        return instances.get(idx);
    }
}
```

---

## 3. Algorithm 2: Smooth Weighted Round Robin (WRR)

### 3.1 Description
Static capacity-based routing where instances are assigned fixed static weights $W = \{w_1, w_2, \dots, w_N\}$ reflecting heterogeneous hardware capabilities (e.g., Worker A has 4 vCPUs $\to w_A=4$; Worker B has 2 vCPUs $\to w_B=2$).

To prevent clumping (e.g., $A, A, A, A, B, B$ which causes micro-bursts on server $A$), we implement **Nginx's Smooth Weighted Round Robin Algorithm**.

### 3.2 Mathematical Formulation
Each instance $i$ maintains a dynamic state variable: $\text{current\_weight}(i)$, initialized to $0$.
Let $\sum W = \sum_{j=1}^N w_j$ be the sum of all configured static weights.

For each routing decision:
1. For every instance $i \in I$:
   $$\text{current\_weight}(i) \leftarrow \text{current\_weight}(i) + w_i$$
2. Select the instance $i^*$ with the maximum current weight:
   $$i^* = \arg\max_{i \in I} \left( \text{current\_weight}(i) \right)$$
3. Decrement the selected instance's current weight:
   $$\text{current\_weight}(i^*) \leftarrow \text{current\_weight}(i^*) - \sum W$$
4. Route request to $i^*$.

---

## 4. Algorithm 3: Least Connections (LC)

### 4.1 Description
A dynamic strategy based on active concurrency. Incoming requests are forwarded to the instance currently processing the lowest number of active HTTP connections.

### 4.2 Mathematical Formulation
Let $A(i, t)$ be the number of active in-flight requests on instance $i$ at timestamp $t$.

The selected instance $i^*$ is:
$$i^* = \arg\min_{i \in I} \left( A(i, t) \right)$$

If multiple instances have identical active connections, a tie-breaker (Round Robin or Random) is applied.

### 4.3 Gateway Concurrency Tracking
Spring Cloud Gateway increments a local gauge when a request initiates and decrements it via the reactive `doFinally` signal:
```java
public class LeastConnectionsStrategy implements RoutingStrategy {
    private final ConcurrentMap<String, AtomicInteger> activeRequests = new ConcurrentHashMap<>();

    @Override
    public ServiceInstance select(List<ServiceInstance> instances, MetricSnapshot snapshot) {
        return instances.stream()
            .min(Comparator.comparingInt(inst -> activeRequests.getOrDefault(inst.getInstanceId(), new AtomicInteger(0)).get()))
            .orElse(instances.get(0));
    }
}
```

---

## 5. Algorithm 4: Multi-Metric Adaptive Routing (MM-AR)

### 5.1 Formulation & Objective
The Multi-Metric Adaptive Routing strategy continuously assimilates real-time health indicators (CPU utilization, heap memory usage, rolling latency percentiles, active connections, and error rates) into a composite **Health Fitness Score** $S(i) \in [0, 1]$.

Instances with higher fitness receive a proportionally greater fraction of traffic.

---

### 5.2 Metric Normalization Functions
Metrics have varying dimensions and scales. Each metric $m$ is normalized to a dimensionless fitness score $s_m \in [0, 1]$, where **$1.0$ represents optimal state** and **$0.0$ represents total saturation or failure**.

#### 1. CPU Saturation Score ($S_{\text{cpu}}$)
Let $U_{\text{cpu}}(i) \in [0.0, 1.0]$ be the host CPU utilization:
$$S_{\text{cpu}}(i) = 1.0 - \min(1.0, U_{\text{cpu}}(i))$$

#### 2. Memory Utilization Score ($S_{\text{mem}}$)
Let $M_{\text{used}}(i)$ and $M_{\text{max}}(i)$ be current JVM heap consumption and maximum heap:
$$S_{\text{mem}}(i) = 1.0 - \min\left(1.0, \frac{M_{\text{used}}(i)}{M_{\text{max}}(i)}\right)$$

#### 3. Latency Score ($S_{\text{lat}}$)
Raw latency spikes must be penalized non-linearly. Let $L(i)$ be the exponential moving average (EMA) or $P_{95}$ latency of instance $i$ in milliseconds. We define a baseline threshold $L_{\text{base}}$ (e.g., 20ms) and maximum tolerated latency $L_{\text{max}}$ (e.g., 500ms):
$$S_{\text{lat}}(i) = \max\left(0.0, 1.0 - \frac{L(i) - L_{\text{base}}}{L_{\text{max}} - L_{\text{base}}}\right)$$
Alternatively, a smooth logistic decay function is supported:
$$S_{\text{lat}}(i) = \frac{1}{1 + \exp\left( k \cdot (L(i) - L_{\text{threshold}}) \right)}$$

#### 4. Active Connections Score ($S_{\text{conn}}$)
Let $C(i)$ be the in-flight requests and $C_{\text{limit}}$ be the maximum capacity threshold:
$$S_{\text{conn}}(i) = 1.0 - \min\left(1.0, \frac{C(i)}{C_{\text{limit}}}\right)$$

#### 5. Error Rate Score ($S_{\text{err}}$)
Let $E(i)$ be the ratio of HTTP 5xx responses to total requests over the preceding evaluation window:
$$S_{\text{err}}(i) = 1.0 - \min(1.0, E(i))$$

---

### 5.3 Composite Health Score
The aggregate fitness score $Score(i)$ is a weighted linear combination:
$$Score(i) = w_{\text{cpu}} S_{\text{cpu}}(i) + w_{\text{mem}} S_{\text{mem}}(i) + w_{\text{lat}} S_{\text{lat}}(i) + w_{\text{conn}} S_{\text{conn}}(i) + w_{\text{err}} S_{\text{err}}(i)$$

Subject to the constraint:
$$\sum_{k \in \{\text{cpu}, \text{mem}, \text{lat}, \text{conn}, \text{err}\}} w_k = 1.0, \quad \forall w_k \ge 0$$

#### Default Calibration Matrix:
| Metric | Weight Symbol | Recommended Default | Rationale |
| :--- | :--- | :--- | :--- |
| Latency | $w_{\text{lat}}$ | **0.35** | Direct proxy for end-user SLA violation |
| CPU Usage | $w_{\text{cpu}}$ | **0.25** | Leading indicator of impending queuing delay |
| Error Rate | $w_{\text{err}}$ | **0.20** | Immediate safety breaker against failing instances |
| Active Concurrency | $w_{\text{conn}}$ | **0.10** | Damps short-term queuing disparities |
| Memory Usage | $w_{\text{mem}}$ | **0.10** | Protection against GC pause saturation |

---

## 6. Stability & Anti-Oscillation Engineering

A naive implementation that routes 100% of requests to $i^* = \arg\max Score(i)$ produces catastrophic **traffic flapping** (the "thundering herd" or "ping-pong" effect):
```
Instance A is fast ──▶ All traffic routes to A ──▶ A becomes overloaded
                   ──▶ Score(A) drops ──▶ All traffic shifts to B
                   ──▶ B becomes overloaded ──▶ Ping-Pong Instability!
```

To eliminate this phenomenon, our architecture integrates three stabilizing mechanisms:

### 6.1 Softmax Probabilistic Routing
Instead of hard deterministic routing to the highest score, traffic is routed stochastically using a **Boltzmann/Softmax probability distribution**:

$$P(i) = \frac{\exp(Score(i) / \tau)}{\sum_{j=1}^N \exp(Score(j) / \tau)}$$

Where:
- $\tau > 0$ is the **temperature parameter**.
  - As $\tau \to \infty$: $P(i) \to \frac{1}{N}$ (approaches uniform Round Robin).
  - As $\tau \to 0^+$: $P(i) \to 1.0$ for the highest score (greedy deterministic routing).
  - Configured default: $\tau = 0.25$, ensuring high-performing nodes receive the majority of requests while lower-performing nodes still receive enough traffic for continuous telemetry sampling.

### 6.2 Hysteresis Threshold ($\delta$)
When deterministic sticky routing or primary server designation is enabled, the router does not switch the designated preferred instance from $i_{\text{current}}$ to $i_{\text{candidate}}$ unless:
$$Score(i_{\text{candidate}}) \ge Score(i_{\text{current}}) \times (1 + \delta)$$
Where $\delta$ is the **hysteresis dampener** (default $\delta = 0.10$, i.e., candidate must be at least 10% superior).

### 6.3 Metric Smoothing via Exponential Moving Average (EMA)
To prevent transient spikes from generating wild score swings, raw metric values $M_t$ are smoothed:
$$\bar{M}_t = \alpha \cdot M_t + (1 - \alpha) \cdot \bar{M}_{t-1}$$
Where $\alpha \in (0, 1]$ is the smoothing factor (default $\alpha = 0.3$).

---

## 7. Health Degradation Failsafe Tiers

| Tier | Condition | Gateway Action |
| :--- | :--- | :--- |
| **Tier 1: Healthy** | $Score \ge 0.50$ AND $S_{\text{err}} > 0.98$ | Full participation in probabilistic routing pool. |
| **Tier 2: Degraded** | $0.20 \le Score < 0.50$ | Traffic allocation throttled; temperature $\tau$ lowered to protect node. |
| **Tier 3: Unhealthy** | $Score < 0.20$ OR $3\times$ Consecutive Timeouts | Evicted from candidate pool for $\Delta t_{\text{quarantine}} = 15\text{s}$. Sent only single periodic canary probes. |
