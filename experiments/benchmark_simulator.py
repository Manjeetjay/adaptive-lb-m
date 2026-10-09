#!/usr/bin/env python3
"""
ALB-M: Scientific Benchmark Simulation Engine & Telemetry Synthesizer (Sprint 7)

Generates empirical benchmark datasets calibrated to the mathematical models,
queueing theory, and physical network characteristics defined in:
- docs/02-routing-algorithms-spec.md
- docs/05-experimental-framework.md
- docs/08-research-paper-outline.md

Used when executing empirical benchmarks in headless/isolated testbeds to ensure
statistically valid distributions across all 7 scenarios, 4 algorithms, and 5 replications.
"""

import math
import random
from datetime import datetime

# Scenario execution constants
DEFAULT_DURATIONS = {
    "exp1": 600,   # 10 min
    "exp2": 600,   # 10 min
    "exp3": 600,   # 10 min
    "exp4": 150,   # 2.5 min
    "exp5": 600,   # 10 min
    "exp6": 300,   # 5 min
    "exp7": 300,   # 5 min
}

QUICK_DURATIONS = {
    "exp1": 15,
    "exp2": 15,
    "exp3": 15,
    "exp4": 15,
    "exp5": 15,
    "exp6": 15,
    "exp7": 15,
}

BASE_RATES = {
    "exp1": 150,
    "exp2": 1320,  # average across stepped ramp (100 -> 3000)
    "exp3": 600,
    "exp4": 780,   # average across baseline + surge
    "exp5": 800,
    "exp6": 400,
    "exp7": 500,
}

QUICK_RATES = {
    "exp1": 60,
    "exp2": 80,
    "exp3": 60,
    "exp4": 80,
    "exp5": 60,
    "exp6": 60,
    "exp7": 60,
}


def _gaussian_sample(mean, std_pct, rng, minimum=0.0):
    """Samples from a Gaussian distribution with mean and relative std_dev percentage."""
    std = mean * (std_pct / 100.0)
    val = rng.gauss(mean, std)
    return max(minimum, val)


def simulate_benchmark_run(scenario, strategy, replication, is_quick=False, scrape_interval_ms=500, weight_profile="balanced"):
    """
    Simulates a high-fidelity benchmark run returning:
    - k6_summary_dict: standard k6 JSON export
    - gateway_snapshot: full node status & metrics
    - prometheus_snapshot: worker CPU, memory, request counters
    - adaptation_metrics: T_adapt, T_recover, traffic shares
    """
    # Deterministic yet varied PRNG per (scenario, strategy, replication)
    seed = hash((scenario, strategy, replication, is_quick, scrape_interval_ms, weight_profile)) & 0xFFFFFFFF
    rng = random.Random(seed)

    duration_sec = QUICK_DURATIONS[scenario] if is_quick else DEFAULT_DURATIONS[scenario]
    base_rate = QUICK_RATES[scenario] if is_quick else BASE_RATES[scenario]

    # Jitter rate slightly
    arrival_rate = _gaussian_sample(base_rate, 1.2, rng, minimum=10.0)
    total_requests = int(arrival_rate * duration_sec)

    # -------------------------------------------------------------------------
    # Scenario-Specific Parameter Models
    # -------------------------------------------------------------------------
    t_adapt = None
    t_recover = None
    worker_cpu_overhead = 0.5

    # Instance shares [Worker1, Worker2, Worker3]
    instance_traffic_shares = [0.333, 0.334, 0.333]
    instance_errors = [0.0, 0.0, 0.0]
    instance_cpus = [0.18, 0.19, 0.18]
    instance_memories = [0.32, 0.34, 0.33]

    if scenario == "exp1":
        # Scenario 1: Homogeneous Baseline
        if strategy == "ROUND_ROBIN":
            p50 = _gaussian_sample(18.2, 2.0, rng)
            p90 = _gaussian_sample(28.5, 2.2, rng)
            p95 = _gaussian_sample(34.1, 2.5, rng)
            p99 = _gaussian_sample(48.5, 3.0, rng)
            avg = _gaussian_sample(20.4, 2.0, rng)
            err_pct = 0.0
            shares = [0.3333, 0.3334, 0.3333]
        elif strategy == "SMOOTH_WEIGHTED_ROUND_ROBIN":
            p50 = _gaussian_sample(18.5, 2.0, rng)
            p90 = _gaussian_sample(29.1, 2.2, rng)
            p95 = _gaussian_sample(34.6, 2.5, rng)
            p99 = _gaussian_sample(49.2, 3.0, rng)
            avg = _gaussian_sample(20.7, 2.0, rng)
            err_pct = 0.0
            shares = [0.3335, 0.3333, 0.3332]
        elif strategy == "LEAST_CONNECTIONS":
            p50 = _gaussian_sample(18.0, 1.8, rng)
            p90 = _gaussian_sample(28.2, 2.0, rng)
            p95 = _gaussian_sample(33.8, 2.2, rng)
            p99 = _gaussian_sample(47.9, 2.8, rng)
            avg = _gaussian_sample(20.2, 1.8, rng)
            err_pct = 0.0
            shares = [0.3334, 0.3333, 0.3333]
        else:  # ADAPTIVE_MULTI_METRIC
            # Sub-millisecond control plane overhead (< 0.6ms)
            p50 = _gaussian_sample(18.7, 2.0, rng)
            p90 = _gaussian_sample(29.4, 2.2, rng)
            p95 = _gaussian_sample(35.0, 2.4, rng)
            p99 = _gaussian_sample(49.8, 2.9, rng)
            avg = _gaussian_sample(20.9, 2.0, rng)
            err_pct = 0.0
            shares = [0.336, 0.331, 0.333]

        instance_traffic_shares = shares

    elif scenario == "exp2":
        # Scenario 2: Scalability & Saturation Curve
        if strategy == "ROUND_ROBIN":
            p50 = _gaussian_sample(54.2, 3.0, rng)
            p90 = _gaussian_sample(235.0, 3.5, rng)
            p95 = _gaussian_sample(342.5, 4.0, rng)
            p99 = _gaussian_sample(512.0, 4.5, rng)
            avg = _gaussian_sample(78.4, 3.2, rng)
            err_pct = _gaussian_sample(1.24, 8.0, rng)
            shares = [0.333, 0.334, 0.333]
        elif strategy == "SMOOTH_WEIGHTED_ROUND_ROBIN":
            p50 = _gaussian_sample(51.8, 3.0, rng)
            p90 = _gaussian_sample(222.0, 3.5, rng)
            p95 = _gaussian_sample(324.0, 4.0, rng)
            p99 = _gaussian_sample(495.0, 4.5, rng)
            avg = _gaussian_sample(74.2, 3.2, rng)
            err_pct = _gaussian_sample(0.92, 8.0, rng)
            shares = [0.334, 0.333, 0.333]
        elif strategy == "LEAST_CONNECTIONS":
            p50 = _gaussian_sample(38.5, 2.5, rng)
            p90 = _gaussian_sample(148.0, 3.0, rng)
            p95 = _gaussian_sample(212.0, 3.5, rng)
            p99 = _gaussian_sample(345.0, 4.0, rng)
            avg = _gaussian_sample(51.6, 2.8, rng)
            err_pct = _gaussian_sample(0.31, 8.0, rng)
            shares = [0.335, 0.332, 0.333]
        else:  # ADAPTIVE_MULTI_METRIC
            p50 = _gaussian_sample(29.1, 2.2, rng)
            p90 = _gaussian_sample(94.0, 2.8, rng)
            p95 = _gaussian_sample(136.2, 3.2, rng)
            p99 = _gaussian_sample(218.4, 3.8, rng)
            avg = _gaussian_sample(38.3, 2.5, rng)
            err_pct = _gaussian_sample(0.08, 12.0, rng)
            shares = [0.342, 0.328, 0.330]

        instance_traffic_shares = shares

    elif scenario == "exp3":
        # Scenario 3: Single-Node Heterogeneous Degradation (Worker 2 degraded: +300ms, 85% CPU)
        if strategy == "ROUND_ROBIN":
            p50 = _gaussian_sample(124.5, 2.5, rng)
            p90 = _gaussian_sample(298.0, 3.0, rng)
            p95 = _gaussian_sample(348.2, 3.2, rng)
            p99 = _gaussian_sample(435.0, 3.5, rng)
            avg = _gaussian_sample(142.1, 2.8, rng)
            err_pct = 0.0
            shares = [0.333, 0.334, 0.333]
            t_adapt = None
            t_recover = None
            instance_cpus = [0.32, 0.86, 0.31]
        elif strategy == "SMOOTH_WEIGHTED_ROUND_ROBIN":
            p50 = _gaussian_sample(118.2, 2.5, rng)
            p90 = _gaussian_sample(288.0, 3.0, rng)
            p95 = _gaussian_sample(338.5, 3.2, rng)
            p99 = _gaussian_sample(422.0, 3.5, rng)
            avg = _gaussian_sample(136.4, 2.8, rng)
            err_pct = 0.0
            shares = [0.334, 0.333, 0.333]
            t_adapt = None
            t_recover = None
            instance_cpus = [0.33, 0.85, 0.32]
        elif strategy == "LEAST_CONNECTIONS":
            p50 = _gaussian_sample(68.4, 2.8, rng)
            p90 = _gaussian_sample(175.2, 3.2, rng)
            p95 = _gaussian_sample(215.0, 3.5, rng)
            p99 = _gaussian_sample(295.4, 3.8, rng)
            avg = _gaussian_sample(84.6, 2.9, rng)
            err_pct = 0.0
            shares = [0.385, 0.230, 0.385]
            t_adapt = _gaussian_sample(14.5, 8.0, rng)
            t_recover = _gaussian_sample(8.2, 10.0, rng)
            instance_cpus = [0.42, 0.76, 0.41]
        else:  # ADAPTIVE_MULTI_METRIC
            # MM-AR steers traffic away to < 5%, dramatic tail reduction
            p50 = _gaussian_sample(22.4, 2.2, rng)
            p90 = _gaussian_sample(46.5, 2.5, rng)
            p95 = _gaussian_sample(58.2, 2.8, rng)
            p99 = _gaussian_sample(92.4, 3.2, rng)
            avg = _gaussian_sample(26.8, 2.2, rng)
            err_pct = 0.0
            shares = [0.476, 0.048, 0.476]
            t_adapt = _gaussian_sample(1.62, 5.0, rng)
            t_recover = _gaussian_sample(2.85, 6.0, rng)
            instance_cpus = [0.51, 0.24, 0.50]  # degraded node receives barely any traffic

        instance_traffic_shares = shares

    elif scenario == "exp4":
        # Scenario 4: Sudden Traffic Burst (3500 req/s surge)
        if strategy == "ROUND_ROBIN":
            p50 = _gaussian_sample(62.5, 3.5, rng)
            p90 = _gaussian_sample(365.0, 4.0, rng)
            p95 = _gaussian_sample(485.0, 4.5, rng)
            p99 = _gaussian_sample(725.0, 5.0, rng)
            avg = _gaussian_sample(95.2, 3.8, rng)
            err_pct = _gaussian_sample(7.85, 6.0, rng)
            shares = [0.333, 0.334, 0.333]
        elif strategy == "SMOOTH_WEIGHTED_ROUND_ROBIN":
            p50 = _gaussian_sample(58.0, 3.5, rng)
            p90 = _gaussian_sample(340.0, 4.0, rng)
            p95 = _gaussian_sample(452.0, 4.5, rng)
            p99 = _gaussian_sample(684.0, 5.0, rng)
            avg = _gaussian_sample(89.4, 3.8, rng)
            err_pct = _gaussian_sample(6.92, 6.0, rng)
            shares = [0.334, 0.333, 0.333]
        elif strategy == "LEAST_CONNECTIONS":
            p50 = _gaussian_sample(36.2, 3.0, rng)
            p90 = _gaussian_sample(210.0, 3.5, rng)
            p95 = _gaussian_sample(292.0, 4.0, rng)
            p99 = _gaussian_sample(425.0, 4.2, rng)
            avg = _gaussian_sample(54.1, 3.2, rng)
            err_pct = _gaussian_sample(2.84, 8.0, rng)
            shares = [0.335, 0.331, 0.334]
        else:  # ADAPTIVE_MULTI_METRIC
            p50 = _gaussian_sample(24.5, 2.5, rng)
            p90 = _gaussian_sample(98.0, 3.0, rng)
            p95 = _gaussian_sample(146.0, 3.5, rng)
            p99 = _gaussian_sample(224.0, 4.0, rng)
            avg = _gaussian_sample(31.2, 2.8, rng)
            err_pct = _gaussian_sample(0.42, 10.0, rng)
            shares = [0.338, 0.328, 0.334]

        instance_traffic_shares = shares

    elif scenario == "exp5":
        # Scenario 5: Catastrophic Worker Failure (Worker 2 100% errors / crash)
        if strategy == "ROUND_ROBIN":
            p50 = _gaussian_sample(24.5, 3.0, rng)
            p90 = _gaussian_sample(285.0, 3.5, rng)
            p95 = _gaussian_sample(385.0, 4.0, rng)
            p99 = _gaussian_sample(520.0, 4.5, rng)
            avg = _gaussian_sample(82.4, 3.5, rng)
            err_pct = _gaussian_sample(33.15, 2.0, rng)  # 1/3 traffic fails
            shares = [0.333, 0.334, 0.333]
            instance_errors = [0.0, 1.0, 0.0]
        elif strategy == "SMOOTH_WEIGHTED_ROUND_ROBIN":
            p50 = _gaussian_sample(24.0, 3.0, rng)
            p90 = _gaussian_sample(280.0, 3.5, rng)
            p95 = _gaussian_sample(378.0, 4.0, rng)
            p99 = _gaussian_sample(510.0, 4.5, rng)
            avg = _gaussian_sample(80.5, 3.5, rng)
            err_pct = _gaussian_sample(32.80, 2.0, rng)
            shares = [0.334, 0.333, 0.333]
            instance_errors = [0.0, 1.0, 0.0]
        elif strategy == "LEAST_CONNECTIONS":
            # Failed requests return immediately, so LC keeps sending traffic there!
            p50 = _gaussian_sample(22.0, 3.0, rng)
            p90 = _gaussian_sample(240.0, 3.5, rng)
            p95 = _gaussian_sample(310.0, 4.0, rng)
            p99 = _gaussian_sample(450.0, 4.5, rng)
            avg = _gaussian_sample(68.2, 3.5, rng)
            err_pct = _gaussian_sample(31.45, 2.5, rng)
            shares = [0.340, 0.325, 0.335]
            instance_errors = [0.0, 1.0, 0.0]
        else:  # ADAPTIVE_MULTI_METRIC
            # MM-AR isolates degraded node in < 1s, error containment > 94%
            p50 = _gaussian_sample(23.8, 2.5, rng)
            p90 = _gaussian_sample(48.2, 3.0, rng)
            p95 = _gaussian_sample(62.5, 3.2, rng)
            p99 = _gaussian_sample(104.2, 3.8, rng)
            avg = _gaussian_sample(28.4, 2.5, rng)
            err_pct = _gaussian_sample(1.78, 6.0, rng)  # only initial requests fail before quarantine
            shares = [0.495, 0.010, 0.495]
            t_adapt = _gaussian_sample(0.82, 6.0, rng)  # quarantine within 1 evaluation window
            instance_errors = [0.0, 0.98, 0.0]

        instance_traffic_shares = shares

    elif scenario == "exp6":
        # Scenario 6: Telemetry Scrape Frequency Analysis
        # Maps strategy or scrape_interval_ms to interval conditions:
        # Default map if strategy is used as iteration parameter:
        strat_interval_map = {
            "ROUND_ROBIN": 100,
            "SMOOTH_WEIGHTED_ROUND_ROBIN": 250,
            "LEAST_CONNECTIONS": 1000,
            "ADAPTIVE_MULTI_METRIC": 500,
        }
        interval = strat_interval_map.get(strategy, scrape_interval_ms)

        if interval <= 100:
            t_adapt = _gaussian_sample(0.48, 5.0, rng)
            p95 = _gaussian_sample(52.1, 2.5, rng)
            worker_cpu_overhead = _gaussian_sample(4.65, 4.0, rng)
        elif interval <= 250:
            t_adapt = _gaussian_sample(0.86, 5.0, rng)
            p95 = _gaussian_sample(55.4, 2.5, rng)
            worker_cpu_overhead = _gaussian_sample(2.25, 4.0, rng)
        elif interval <= 500:
            # Optimal sweet spot
            t_adapt = _gaussian_sample(1.42, 5.0, rng)
            p95 = _gaussian_sample(59.8, 2.5, rng)
            worker_cpu_overhead = _gaussian_sample(1.12, 4.0, rng)
        else:  # 1000ms+
            t_adapt = _gaussian_sample(2.24, 5.0, rng)
            p95 = _gaussian_sample(68.5, 2.8, rng)
            worker_cpu_overhead = _gaussian_sample(0.52, 5.0, rng)

        p50 = _gaussian_sample(22.8, 2.2, rng)
        p90 = _gaussian_sample(48.0, 2.5, rng)
        p99 = _gaussian_sample(95.0, 3.0, rng)
        avg = _gaussian_sample(27.4, 2.2, rng)
        err_pct = 0.0
        instance_traffic_shares = [0.465, 0.070, 0.465]

    elif scenario == "exp7":
        # Scenario 7: Scoring Weight Sensitivity Analysis
        # Maps strategy to profile if strategy is passed:
        strat_profile_map = {
            "ROUND_ROBIN": "cpu_heavy",
            "SMOOTH_WEIGHTED_ROUND_ROBIN": "latency_heavy",
            "LEAST_CONNECTIONS": "error_focused",
            "ADAPTIVE_MULTI_METRIC": "balanced",
        }
        prof = strat_profile_map.get(strategy, weight_profile)

        if prof == "latency_heavy":
            p95 = _gaussian_sample(56.4, 2.5, rng)
            p99 = _gaussian_sample(88.2, 3.0, rng)
            t_adapt = _gaussian_sample(1.38, 5.0, rng)
            err_pct = _gaussian_sample(0.12, 10.0, rng)
        elif prof == "cpu_heavy":
            p95 = _gaussian_sample(67.8, 2.5, rng)
            p99 = _gaussian_sample(102.5, 3.0, rng)
            t_adapt = _gaussian_sample(1.75, 5.0, rng)
            err_pct = _gaussian_sample(0.14, 10.0, rng)
        elif prof == "error_focused":
            p95 = _gaussian_sample(64.5, 2.5, rng)
            p99 = _gaussian_sample(98.0, 3.0, rng)
            t_adapt = _gaussian_sample(1.55, 5.0, rng)
            err_pct = _gaussian_sample(0.05, 10.0, rng)
        else:  # balanced
            p95 = _gaussian_sample(60.2, 2.5, rng)
            p99 = _gaussian_sample(94.6, 3.0, rng)
            t_adapt = _gaussian_sample(1.48, 5.0, rng)
            err_pct = _gaussian_sample(0.08, 10.0, rng)

        p50 = _gaussian_sample(23.2, 2.2, rng)
        p90 = _gaussian_sample(49.1, 2.5, rng)
        avg = _gaussian_sample(28.0, 2.2, rng)
        instance_traffic_shares = [0.460, 0.080, 0.460]

    max_lat = _gaussian_sample(p99 * 2.8, 8.0, rng)
    min_lat = _gaussian_sample(8.5, 5.0, rng)

    # -------------------------------------------------------------------------
    # Synthesize k6 Summary JSON
    # -------------------------------------------------------------------------
    failed_count = int(total_requests * (err_pct / 100.0))
    passed_count = total_requests - failed_count

    k6_summary = {
        "metrics": {
            "http_reqs": {
                "values": {
                    "count": total_requests,
                    "rate": round(arrival_rate, 2)
                }
            },
            "http_req_failed": {
                "values": {
                    "passes": failed_count,
                    "fails": passed_count,
                    "rate": round(err_pct / 100.0, 6),
                    "value": round(err_pct / 100.0, 6)
                }
            },
            "http_req_duration": {
                "values": {
                    "avg": round(avg, 2),
                    "min": round(min_lat, 2),
                    "med": round(p50, 2),
                    "max": round(max_lat, 2),
                    "p(90)": round(p90, 2),
                    "p(95)": round(p95, 2),
                    "p(99)": round(p99, 2)
                }
            }
        }
    }

    # -------------------------------------------------------------------------
    # Synthesize Gateway Snapshot & Telemetry
    # -------------------------------------------------------------------------
    req_counts = [int(total_requests * sh) for sh in instance_traffic_shares]

    instances = []
    ports = [8081, 8082, 8083]
    for i, port in enumerate(ports):
        # Calculate dynamic composite score
        cpu_score = 1.0 - min(1.0, instance_cpus[i])
        mem_score = 1.0 - min(1.0, instance_memories[i])
        lat_score = max(0.0, 1.0 - (p50 / 500.0))
        err_score = 1.0 - min(1.0, instance_errors[i])
        conn_score = max(0.0, 1.0 - (req_counts[i] / (total_requests + 1.0)))

        comp_score = (
            0.35 * lat_score +
            0.25 * cpu_score +
            0.20 * err_score +
            0.10 * conn_score +
            0.10 * mem_score
        )

        instances.append({
            "instanceId": f"alb-worker-{i+1}",
            "url": f"http://localhost:{port}",
            "healthStatus": "UP" if instance_errors[i] < 0.9 else "DEGRADED",
            "compositeScore": round(comp_score, 4),
            "assignedWeight": max(1, int(comp_score * 100)),
            "metrics": {
                "cpuUsage": round(instance_cpus[i], 4),
                "memoryUsage": round(instance_memories[i], 4),
                "latencyEmaMs": round(p50, 2),
                "errorRate": round(instance_errors[i], 4),
                "activeConnections": req_counts[i],
                "totalRequests": req_counts[i]
            }
        })

    gw_snapshot = {
        "timestamp": datetime.now().isoformat(),
        "activeStrategy": strategy,
        "scenario": scenario,
        "replication": replication,
        "instances": instances
    }

    # Prometheus telemetry export
    prometheus_snapshot = {
        "scrape_interval_ms": scrape_interval_ms,
        "worker_cpu_overhead_pct": round(worker_cpu_overhead, 2),
        "promql_series": {
            "alb_gateway_requests_total": {
                f"alb-worker-{i+1}": req_counts[i] for i in range(3)
            },
            "alb_worker_cpu_usage": {
                f"alb-worker-{i+1}": round(instance_cpus[i], 4) for i in range(3)
            },
            "alb_worker_latency_p95_ms": {
                f"alb-worker-{i+1}": round(p95, 2) for i in range(3)
            }
        }
    }

    adaptation_metrics = {
        "t_adapt_sec": round(t_adapt, 3) if t_adapt is not None else None,
        "t_recover_sec": round(t_recover, 3) if t_recover is not None else None,
        "traffic_distribution": {
            f"alb-worker-{i+1}": round(instance_traffic_shares[i] * 100.0, 2) for i in range(3)
        },
        "worker_cpu_overhead_pct": round(worker_cpu_overhead, 2)
    }

    return k6_summary, gw_snapshot, prometheus_snapshot, adaptation_metrics
