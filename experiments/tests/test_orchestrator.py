#!/usr/bin/env python3
"""
Unit and integration test suite for ALB-M Experiment Orchestrator and Benchmarking Harness (Sprint 6).
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


class TestJainsFairnessIndex(unittest.TestCase):
    def test_perfect_balance(self):
        # When all nodes handle equal traffic, Jain's index is 1.0
        jain = calculate_jains_fairness([100, 100, 100])
        self.assertAlmostEqual(jain, 1.0, places=4)

    def test_imbalanced_distribution(self):
        # When one node handles all traffic, Jain's index approaches 1/N
        jain = calculate_jains_fairness([300, 0, 0])
        self.assertAlmostEqual(jain, 1.0 / 3.0, places=4)

    def test_empty_or_zero_traffic(self):
        self.assertEqual(calculate_jains_fairness([]), 1.0)
        self.assertEqual(calculate_jains_fairness([0, 0, 0]), 1.0)

    def test_partial_imbalance(self):
        # 100, 50, 50: sum=200, sum^2=40000; sum_sq=10000+2500+2500=15000; N=3; 40000/(3*15000) = 40000/45000 = 0.8888
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


if __name__ == "__main__":
    unittest.main()
