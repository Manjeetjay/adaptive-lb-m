#!/usr/bin/env python3
"""
ALB-M: Sprint 1 through Sprint 5 End-to-End Test Suite
Automated Python verification script for validating milestones up to Sprint 5.
"""

import sys
import time
import json
import subprocess
from urllib.request import urlopen, Request
from urllib.error import URLError, HTTPError

GATEWAY_URL = "http://localhost:8080"
WORKER1_URL = "http://localhost:8081"
WORKER2_URL = "http://localhost:8082"
WORKER3_URL = "http://localhost:8083"
EUREKA_URL  = "http://localhost:8761"

passed = 0
failed = 0

def color(text, code):
    return f"\033[{code}m{text}\033[0m"

def step(title):
    print(f"\n{color('=' * 70, 36)}")
    print(f"  {color(title, 36)}")
    print(f"{color('=' * 70, 36)}")

def report(condition, pass_msg, fail_msg):
    global passed, failed
    if condition:
        print(f"  {color('[PASS]', 32)} {pass_msg}")
        passed += 1
    else:
        print(f"  {color('[FAIL]', 31)} {fail_msg}")
        failed += 1

def http_req(url, method="GET", data=None, headers=None, timeout=5):
    if headers is None:
        headers = {}
    if data is not None and isinstance(data, (dict, list)):
        data = json.dumps(data).encode("utf-8")
        headers["Content-Type"] = "application/json"
    req = Request(url, data=data, headers=headers, method=method)
    try:
        with urlopen(req, timeout=timeout) as resp:
            content = resp.read().decode("utf-8")
            try:
                return resp.status, json.loads(content)
            except Exception:
                return resp.status, content
    except HTTPError as e:
        content = e.read().decode("utf-8")
        try:
            return e.code, json.loads(content)
        except Exception:
            return e.code, content
    except URLError as e:
        return None, str(e.reason)

def main():
    skip_unit_tests = "--skip-unit-tests" in sys.argv

    # --------------------------------------------------------------------------
    # STEP 0: Maven Build & Unit Tests Check
    # --------------------------------------------------------------------------
    if not skip_unit_tests:
        step("STEP 0: Running Comprehensive Maven Unit & Integration Tests")
        mvn_cmd = "mvnw.cmd" if sys.platform.startswith("win") else "./mvnw"
        res = subprocess.run([mvn_cmd, "test"], capture_output=True, text=True)
        report(res.returncode == 0, "Maven clean test succeeded with zero test failures", f"Maven tests failed (exit code {res.returncode})")

    # --------------------------------------------------------------------------
    # STEP 1: Discovery & Service Health Checks
    # --------------------------------------------------------------------------
    step("STEP 1: Sprint 1 - Discovery & Service Health Checks")
    status, body = http_req(f"{EUREKA_URL}/eureka/apps", headers={"Accept": "application/json"})
    report(status == 200, f"Eureka Server reachable at {EUREKA_URL}", f"Eureka unreachable (status={status})")

    status, body = http_req(f"{GATEWAY_URL}/actuator/health")
    report(status == 200 and isinstance(body, dict) and body.get("status") == "UP", f"Gateway is UP at {GATEWAY_URL}", f"Gateway unreachable (status={status})")

    # --------------------------------------------------------------------------
    # STEP 2: Baseline Routing Algorithms & Dynamic Switching
    # --------------------------------------------------------------------------
    step("STEP 2: Sprint 2 - Baseline Strategies & Dynamic Switching")
    for strat in ["ROUND_ROBIN", "SMOOTH_WEIGHTED_ROUND_ROBIN", "LEAST_CONNECTIONS"]:
        status, body = http_req(f"{GATEWAY_URL}/admin/routing/strategy", method="POST", data={"strategy": strat})
        report(status == 200 and isinstance(body, dict) and (body.get("activeStrategy") == strat or body.get("status") == "SUCCESS"),
               f"Switched routing strategy to {strat}", f"Failed to switch to {strat} (status={status})")

    # --------------------------------------------------------------------------
    # STEP 3: Observability & Telemetry Pipeline
    # --------------------------------------------------------------------------
    step("STEP 3: Sprint 3 - Prometheus Telemetry Verification")
    status, body = http_req(f"{WORKER1_URL}/actuator/prometheus")
    if status == 200 and isinstance(body, str):
        has_metrics = "alb_worker_active_requests" in body and "alb_worker_cpu_usage" in body
        report(has_metrics, "Worker exposes custom Micrometer gauges (active_requests, cpu_usage)", "Missing worker metrics")
    else:
        report(False, "", f"Worker 1 prometheus scrape failed (status={status})")

    # --------------------------------------------------------------------------
    # STEP 4: Multi-Metric Adaptive Routing Engine (MM-AR)
    # --------------------------------------------------------------------------
    step("STEP 4: Sprint 4 - Multi-Metric Adaptive Engine & Control Plane")
    status, body = http_req(f"{GATEWAY_URL}/admin/routing/strategy", method="POST", data={"strategy": "ADAPTIVE_MULTI_METRIC"})
    report(status == 200 and isinstance(body, dict) and body.get("activeStrategy") == "ADAPTIVE_MULTI_METRIC",
           "Activated ADAPTIVE_MULTI_METRIC strategy", "Failed activating ADAPTIVE_MULTI_METRIC")

    status, body = http_req(f"{GATEWAY_URL}/admin/routing/status")
    report(status == 200 and isinstance(body, dict) and "activeStrategy" in body and "weights" in body,
           "GET /admin/routing/status returned full telemetry and weights snapshot", "Failed reading routing status")

    weights_payload = {"cpu": 0.30, "memory": 0.10, "latency": 0.30, "connections": 0.10, "errors": 0.20}
    status, body = http_req(f"{GATEWAY_URL}/admin/routing/weights", method="POST", data=weights_payload)
    report(status == 200 and isinstance(body, dict) and body.get("status") == "UPDATED",
           "POST /admin/routing/weights updated scoring weights dynamically", "Weights update failed")

    config_payload = {"refreshIntervalMs": 400, "hysteresisDelta": 0.12, "softmaxTemperature": 0.30}
    status, body = http_req(f"{GATEWAY_URL}/admin/routing/config", method="POST", data=config_payload)
    report(status == 200 and isinstance(body, dict) and body.get("status") == "UPDATED",
           "POST /admin/routing/config tuned hyperparameters dynamically", "Config update failed")

    # --------------------------------------------------------------------------
    # STEP 5: Chaos Engineering & Fault Injection Suite
    # --------------------------------------------------------------------------
    step("STEP 5: Sprint 5 - Chaos Engineering & Fault Injection Validation")
    target_worker = WORKER2_URL

    # 5.1 Latency
    status, body = http_req(f"{target_worker}/chaos/latency", method="POST",
                            data={"delayMs": 250, "jitterMs": 30, "probability": 1.0, "durationSeconds": 20})
    report(status == 200 and isinstance(body, dict) and body.get("fault") == "LATENCY",
           "T5.2: Injected artificial latency (250ms with 30ms jitter)", "Latency injection failed")

    # 5.2 CPU Burn
    status, body = http_req(f"{target_worker}/chaos/cpu-burn", method="POST",
                            data={"threads": 2, "targetCpuPercent": 85, "durationSeconds": 15})
    report(status == 200 and isinstance(body, dict) and body.get("fault") == "CPU_BURN",
           "T5.1: Injected CPU Burn (2 threads, 85% target load)", "CPU burn injection failed")

    # 5.3 Error Burst
    status, body = http_req(f"{target_worker}/chaos/error-burst", method="POST",
                            data={"errorRate": 0.40, "durationSeconds": 15})
    report(status == 200 and isinstance(body, dict) and body.get("fault") == "ERROR_BURST",
           "T5.3: Injected HTTP 500 Error Burst (rate=0.40)", "Error burst injection failed")

    # 5.4 Chaos Status Check
    status, body = http_req(f"{target_worker}/chaos/status")
    report(status == 200 and isinstance(body, dict) and body.get("status") == "OK" and "chaos" in body,
           "T5.4: GET /chaos/status correctly reports active faults and remaining durations", "Chaos status check failed")

    # 5.5 Fault Immunity for Control / Telemetry Endpoints
    status, body = http_req(f"{target_worker}/actuator/health")
    report(status == 200, "Fault Immunity: /actuator/health remains 200 OK during active chaos", "Actuator was disrupted by chaos")

    # 5.6 Chaos Reset
    status, body = http_req(f"{target_worker}/chaos/reset", method="POST")
    report(status == 200 and isinstance(body, dict) and body.get("status") == "RESET",
           "T5.4: POST /chaos/reset restored worker to healthy baseline", "Reset failed")

    print(f"\n{color('=' * 70, 36)}")
    print(f"  {color('TEST SUMMARY REPORT', 36)}")
    print(f"{color('=' * 70, 36)}")
    print(f"  {color(f'Passed checks: {passed}', 32)}")
    print(f"  {color(f'Failed checks: {failed}', 31 if failed > 0 else 32)}")
    print(f"{color('=' * 70, 36)}\n")

if __name__ == "__main__":
    main()
