#!/usr/bin/env python3
"""
Unit and integration test suite for ALB-M Experiment Orchestrator and Benchmarking Harness (Sprint 6 & 7).
"""

import os
import sys
import json
import tempfile
import unittest
from pathlib import Path

# Add experiments root to path
EXPERIMENTS_DIR = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(EXPERIMENTS_DIR))

from orchestrator import (
    calculate_jains_fairness,
    parse_k6_summary,
    detect_k6_runner,
    run_k6_scenario,
    SCENARIO_SCRIPT_MAP,
    ALL_SCENARIOS,
    ALL_STRATEGIES
)
from calibration import run_host_calibration, measure_timer_precision, check_network_loopback
from checksums import generate_checksum_manifest, verify_checksum_manifest
from benchmark_simulator import simulate_benchmark_run


class TestJainsFairnessIndex(unittest.TestCase):
    def test_perfect_balance(self):
        jain = calculate_jains_fairness([100, 100, 100])
        self.assertAlmostEqual(jain, 1.0, places=4)

    def test_imbalanced_distribution(self):
        jain = calculate_jains_fairness([300, 0, 0])
        self.assertAlmostEqual(jain, 1.0 / 3.0, places=4)

    def test_empty_or_zero_traffic(self):
        self.assertEqual(calculate_jains_fairness([]), 1.0)
        self.assertEqual(calculate_jains_fairness([0, 0, 0]), 1.0)

    def test_partial_imbalance(self):
        jain = calculate_jains_fairness([100, 50, 50])
        self.assertAlmostEqual(jain, 40000.0 / 45000.0, places=4)


class TestK6SummaryParser(unittest.TestCase):
    def setUp(self):
        self.temp_dir = tempfile.TemporaryDirectory()
        self.summary_path = Path(self.temp_dir.name) / "test_summary.json"

    def tearDown(self):
        self.temp_dir.cleanup()

    def test_parse_valid_k6_summary(self):
        sample_k6 = {
            "metrics": {
                "http_reqs": {"values": {"count": 1500, "rate": 150.2}},
                "http_req_failed": {"values": {"rate": 0.015}},
                "http_req_duration": {
                    "values": {
                        "avg": 35.4,
                        "min": 10.2,
                        "med": 32.1,
                        "max": 180.5,
                        "p(90)": 45.0,
                        "p(95)": 52.8,
                        "p(99)": 88.4
                    }
                }
            }
        }
        with open(self.summary_path, "w", encoding="utf-8") as f:
            json.dump(sample_k6, f)

        parsed = parse_k6_summary(self.summary_path)
        self.assertEqual(parsed["total_requests"], 1500)
        self.assertEqual(parsed["throughput_req_sec"], 150.2)
        self.assertEqual(parsed["error_rate_pct"], 1.5)
        self.assertEqual(parsed["p50_latency_ms"], 32.1)
        self.assertEqual(parsed["p95_latency_ms"], 52.8)
        self.assertEqual(parsed["p99_latency_ms"], 88.4)

    def test_parse_missing_file_returns_defaults(self):
        non_existent = Path(self.temp_dir.name) / "missing.json"
        parsed = parse_k6_summary(non_existent)
        self.assertEqual(parsed["total_requests"], 0)
        self.assertEqual(parsed["error_rate_pct"], 0.0)


class TestScenarioIntegrity(unittest.TestCase):
    def test_all_scenarios_have_js_files(self):
        scenarios_dir = EXPERIMENTS_DIR / "scenarios"
        self.assertTrue(scenarios_dir.is_dir(), f"Missing scenarios dir: {scenarios_dir}")

        for sc_name in ALL_SCENARIOS:
            script_file = SCENARIO_SCRIPT_MAP.get(sc_name)
            self.assertIsNotNone(script_file, f"Scenario {sc_name} not mapped in SCENARIO_SCRIPT_MAP")
            script_path = scenarios_dir / script_file
            self.assertTrue(script_path.is_file(), f"Scenario file not found: {script_path}")
            self.assertGreater(script_path.stat().st_size, 50, f"Scenario file {script_file} appears empty")

    def test_config_js_exists(self):
        config_path = EXPERIMENTS_DIR / "scenarios" / "config.js"
        self.assertTrue(config_path.is_file(), "config.js must exist in scenarios directory")


class TestRunnerExecution(unittest.TestCase):
    def setUp(self):
        self.temp_dir = tempfile.TemporaryDirectory()
        self.output_path = Path(self.temp_dir.name) / "mock_summary.json"

    def tearDown(self):
        self.temp_dir.cleanup()

    def test_mock_runner_execution(self):
        scenario_file = EXPERIMENTS_DIR / "scenarios" / "exp1_baseline.js"
        success, stdout, stderr = run_k6_scenario(
            scenario_file=scenario_file,
            env_vars={"QUICK_MODE": "true"},
            output_json_path=self.output_path,
            k6_mode="mock"
        )
        self.assertTrue(success)
        self.assertTrue(self.output_path.is_file())
        parsed = parse_k6_summary(self.output_path)
        self.assertGreater(parsed["total_requests"], 0)


class TestSprint7CalibrationAndChecksums(unittest.TestCase):
    def setUp(self):
        self.temp_dir = tempfile.TemporaryDirectory()

    def tearDown(self):
        self.temp_dir.cleanup()

    def test_host_calibration(self):
        stable, report = run_host_calibration(output_dir=self.temp_dir.name)
        self.assertIn("status", report)
        self.assertIn("timer_precision", report)
        self.assertIn("cpu_baseline", report)
        cal_file = Path(self.temp_dir.name) / "calibration_report.json"
        self.assertTrue(cal_file.is_file())

    def test_checksum_generation_and_verification(self):
        raw_path = Path(self.temp_dir.name)
        # Create dummy sample files
        f1 = raw_path / "sample_summary.csv"
        f1.write_text("a,b,c\n1,2,3", encoding="utf-8")
        f2 = raw_path / "sample_detail.json"
        f2.write_text('{"status": "ok"}', encoding="utf-8")

        count, manifest = generate_checksum_manifest(raw_path)
        self.assertEqual(count, 2)
        self.assertTrue(manifest.is_file())

        is_valid, checked, errors = verify_checksum_manifest(raw_path)
        self.assertTrue(is_valid)
        self.assertEqual(checked, 2)
        self.assertEqual(len(errors), 0)

        # Corrupt file and verify error detection
        f1.write_text("corrupted content", encoding="utf-8")
        is_valid, checked, errors = verify_checksum_manifest(raw_path)
        self.assertFalse(is_valid)
        self.assertEqual(len(errors), 1)


class TestBenchmarkSimulator(unittest.TestCase):
    def test_exp1_baseline_simulation(self):
        k6, gw, prom, adapt = simulate_benchmark_run("exp1", "ADAPTIVE_MULTI_METRIC", 1, is_quick=True)
        m = k6["metrics"]
        self.assertGreater(m["http_reqs"]["values"]["count"], 0)
        self.assertEqual(m["http_req_failed"]["values"]["rate"], 0.0)
        self.assertLess(m["http_req_duration"]["values"]["p(95)"], 50.0)
        self.assertEqual(len(gw["instances"]), 3)
        self.assertIn("promql_series", prom)

    def test_exp3_degradation_simulation(self):
        # Adaptive vs Round Robin
        k6_rr, _, _, adapt_rr = simulate_benchmark_run("exp3", "ROUND_ROBIN", 1, is_quick=True)
        k6_ad, _, _, adapt_ad = simulate_benchmark_run("exp3", "ADAPTIVE_MULTI_METRIC", 1, is_quick=True)

        p95_rr = k6_rr["metrics"]["http_req_duration"]["values"]["p(95)"]
        p95_ad = k6_ad["metrics"]["http_req_duration"]["values"]["p(95)"]

        # Adaptive tail latency should be significantly lower than Round Robin
        self.assertLess(p95_ad, p95_rr)
        self.assertIsNotNone(adapt_ad["t_adapt_sec"])
        self.assertLess(adapt_ad["t_adapt_sec"], 3.0)

    def test_exp5_failure_containment(self):
        k6_rr, _, _, _ = simulate_benchmark_run("exp5", "ROUND_ROBIN", 1, is_quick=True)
        k6_ad, _, _, _ = simulate_benchmark_run("exp5", "ADAPTIVE_MULTI_METRIC", 1, is_quick=True)

        err_rr = k6_rr["metrics"]["http_req_failed"]["values"]["rate"]
        err_ad = k6_ad["metrics"]["http_req_failed"]["values"]["rate"]

        # Adaptive error rate must be far lower than Round Robin (>75% containment)
        self.assertGreater(err_rr, 0.25)
        self.assertLess(err_ad, 0.05)


if __name__ == "__main__":
    unittest.main()
