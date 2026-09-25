# Chaos & Fault Injection Suite

## 1. Overview

In static environments with homogeneous, unstressed servers, traditional Round Robin performs adequately.

The entire theoretical advantage of an **Adaptive Load Balancer** manifests when the distributed environment suffers from:
1. Heterogeneous CPU/memory saturation.
2. Latency anomalies (slow disks, GC pauses, lock contention).
3. Transient HTTP 5xx error bursts.
4. Hard node crashes and network partitions.

This document describes the automated chaos harness used to inject deterministic, reproducible faults during experimental runs.

---

## 2. Failure Models & Injection Mechanisms

```
┌────────────────────────────────────────────────────────────────────────┐
│                        CHAOS INJECTION HARNESS                         │
├────────────────────────────────────────────────────────────────────────┤
│  1. Application-Level Faults (Internal Java Hooks)                     │
│     ├── CPU Throttling / Busy-Loop Burn                                │
│     ├── Artificial Delay & Gaussian Jitter                             │
│     ├── Memory Pressure & Heap Saturation                              │
│     └── Controlled HTTP 500 Internal Error Rates                       │
│                                                                        │
│  2. Infrastructure-Level Faults (Docker & Linux cgroups)               │
│     ├── Container SIGKILL / SIGSTOP (`docker kill / pause`)            │
│     └── Network Emulation (Packet delay, jitter, drop via Pumba/tc)    │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 3. Application-Level Fault Injectors

Each worker microservice embeds a dedicated `ChaosService` controlled via the `/chaos/*` REST endpoints:

### 3.1 CPU Saturation Injector
Spawns daemon worker threads executing tight non-blocking arithmetic loops until the target OS CPU utilization is achieved:

```java
@Service
public class ChaosService {
    private final AtomicBoolean cpuBurnActive = new AtomicBoolean(false);
    private final List<Thread> burnThreads = new CopyOnWriteArrayList<>();

    public void startCpuBurn(int numThreads, int durationSeconds) {
        cpuBurnActive.set(true);
        for (int i = 0; i < numThreads; i++) {
            Thread t = new Thread(() -> {
                while (cpuBurnActive.get()) {
                    // Prevent JIT compiler optimization while consuming ALU cycles
                    Math.sin(Math.random() * Math.PI);
                }
            }, "chaos-cpu-burner-" + i);
            burnThreads.add(t);
            t.start();
        }

        // Auto-terminate after duration
        Schedulers.boundedElastic().schedule(() -> stopCpuBurn(), durationSeconds, TimeUnit.SECONDS);
    }

    public void stopCpuBurn() {
        cpuBurnActive.set(false);
        burnThreads.clear();
    }
}
```

### 3.2 Artificial Latency & Jitter Injector
Uses a custom Spring WebFilter to intercept requests and inject controlled delay before hitting the business controller:

```java
@Component
public class LatencyInjectionFilter implements WebFilter {
    private volatile int artificialDelayMs = 0;
    private volatile int jitterMs = 0;
    private volatile double injectionProbability = 0.0;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (injectionProbability > 0 && ThreadLocalRandom.current().nextDouble() < injectionProbability) {
            int delay = artificialDelayMs + (jitterMs > 0 ? ThreadLocalRandom.current().nextInt(-jitterMs, jitterMs + 1) : 0);
            return Mono.delay(Duration.ofMillis(Math.max(0, delay)))
                       .then(chain.filter(exchange));
        }
        return chain.filter(exchange);
    }
}
```

### 3.3 HTTP 500 Error Burst Injector
Injects synthetic runtime exceptions on designated endpoints to simulate unhandled database disconnections or internal bugs:

```java
if (errorRate > 0 && ThreadLocalRandom.current().nextDouble() < errorRate) {
    throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Chaos: Simulated 500 Error");
}
```

---

## 4. Automated Python Chaos Orchestrator

The test harness provides an automated experiment runner (`experiments/chaos_orchestrator.py`) that coordinates k6 traffic generation and fault injection:

```python
import time
import requests
import subprocess

def run_experiment_3(strategy_name):
    print(f"=== Starting Experiment 3 with Strategy: {strategy_name} ===")
    
    # 1. Configure Gateway Strategy
    requests.post("http://localhost:8080/admin/routing/strategy", 
                  json={"strategy": strategy_name})
    
    # 2. Reset Worker Chaos
    requests.post("http://localhost:8082/chaos/reset")
    
    # 3. Start k6 in background
    k6_process = subprocess.Popen(["k6", "run", "experiments/scenarios/exp3.js"])
    
    # 4. Wait for steady-state (120 seconds)
    print("Waiting 120s for baseline steady-state...")
    time.sleep(120)
    
    # 5. Inject CPU burn and latency into Worker 2
    print("Injecting 300ms latency and 4 CPU burning threads into Worker 2...")
    t_start = time.time()
    requests.post("http://localhost:8082/chaos/latency", 
                  json={"delayMs": 300, "jitterMs": 25, "probability": 1.0, "durationSeconds": 300})
    requests.post("http://localhost:8082/chaos/cpu-burn", 
                  json={"threads": 4, "durationSeconds": 300})
    
    # 6. Allow experiment to run under fault for 300s
    time.sleep(300)
    
    # 7. Clear fault and observe recovery
    print("Stopping fault injection, observing recovery...")
    requests.post("http://localhost:8082/chaos/reset")
    
    # 8. Wait for k6 to finish
    k6_process.wait()
    print(f"=== Experiment 3 Completed for {strategy_name} ===")
```

---

## 5. Adaptation Timeline Quantification

The adaptation timeline during Experiment 3 is tracked via Prometheus time-series:

```
Traffic
Share
 ▲
100%│                     Worker 1 & 3 (Healthy)
    │                  ┌──────────────────────────────────────────
 50%│     Baseline     │
    │  ───┬─────┬──────┘
    │     │     │
  0%│  ───┴─────┴──────┐
    │                  └──────────────────────────────────────────
    │                     Worker 2 (Degraded)
────┼─────┼─────┼──────┼──────────────────────────────────────────▶ Time (s)
    │     │     │      │
    │  t_fault  │   t_stabilized
    │           │
    └───────────┴─────────────────▶  T_adapt = t_stabilized - t_fault
```

1. **$t_{\text{fault}}$:** Recorded at timestamp of API call to `/chaos/*`.
2. **$t_{\text{detected}}$:** Timestamp when Prometheus/Gateway records score drop ($Score < 0.5$).
3. **$t_{\text{stabilized}}$:** Timestamp when Worker 2's request rate drops to $< 10\%$ of cluster traffic.
4. **$T_{\text{adapt}}$:** The final metric recorded for comparison across scrape intervals.
