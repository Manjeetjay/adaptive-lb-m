#!/usr/bin/env python3
"""
ALB-M: Host Environment Calibration & Background Noise Profiler (Sprint 7 - T7.1)

Validates host environment stability, timer precision, CPU/RAM baseline,
and ensures low background noise before launching empirical benchmarks.
"""

import os
import sys
import time
import json
import socket
import logging
from datetime import datetime
from pathlib import Path

logger = logging.getLogger("calibration")


def get_system_telemetry():
    """Captures CPU core count, platform, and memory statistics."""
    telemetry = {
        "os_name": os.name,
        "platform": sys.platform,
        "python_version": sys.version.split()[0],
        "cpu_cores_logical": os.cpu_count() or 1,
        "timestamp": datetime.now().isoformat()
    }

    # Attempt to read memory info via platform-specific calls
    try:
        if sys.platform == "win32":
            import ctypes
            class MEMORYSTATUSEX(ctypes.Structure):
                _fields_ = [
                    ("dwLength", ctypes.c_ulong),
                    ("dwMemoryLoad", ctypes.c_ulong),
                    ("ullTotalPhys", ctypes.c_ulonglong),
                    ("ullAvailPhys", ctypes.c_ulonglong),
                    ("ullTotalPageFile", ctypes.c_ulonglong),
                    ("ullAvailPageFile", ctypes.c_ulonglong),
                    ("ullTotalVirtual", ctypes.c_ulonglong),
                    ("ullAvailVirtual", ctypes.c_ulonglong),
                    ("ullAvailExtendedVirtual", ctypes.c_ulonglong),
                ]
            stat = MEMORYSTATUSEX()
            stat.dwLength = ctypes.sizeof(MEMORYSTATUSEX)
            ctypes.windll.kernel32.GlobalMemoryStatusEx(ctypes.byref(stat))
            telemetry["total_memory_gb"] = round(stat.ullTotalPhys / (1024 ** 3), 2)
            telemetry["avail_memory_gb"] = round(stat.ullAvailPhys / (1024 ** 3), 2)
            telemetry["memory_load_pct"] = stat.dwMemoryLoad
    except Exception as e:
        logger.debug(f"Memory stat lookup skipped: {e}")

    return telemetry


def measure_timer_precision(samples=50):
    """Measures monotonic timer resolution and sleep jitter."""
    delays = []
    target_sleep = 0.005  # 5ms

    for _ in range(samples):
        t0 = time.perf_counter()
        time.sleep(target_sleep)
        t1 = time.perf_counter()
        delays.append((t1 - t0) * 1000.0)

    avg_delay = sum(delays) / len(delays)
    jitter = max(abs(d - 5.0) for d in delays)

    return {
        "target_sleep_ms": 5.0,
        "avg_sleep_measured_ms": round(avg_delay, 3),
        "max_jitter_ms": round(jitter, 3),
        "timer_resolution_ns": time.get_clock_info("perf_counter").resolution
    }


def measure_cpu_baseline(samples=5, sample_interval=0.2):
    """
    Measures CPU baseline load by timing an idle tight arithmetic loop
    relative to calibrated clock cycles.
    """
    ratios = []
    for _ in range(samples):
        t0 = time.perf_counter()
        # Light arithmetic loop to measure responsiveness
        total = 0
        for i in range(100000):
            total += i
        elapsed = time.perf_counter() - t0
        ratios.append(elapsed * 1000.0)
        time.sleep(sample_interval)

    avg_loop_ms = sum(ratios) / len(ratios)
    std_dev = (sum((r - avg_loop_ms) ** 2 for r in ratios) / len(ratios)) ** 0.5

    return {
        "loop_duration_ms": round(avg_loop_ms, 3),
        "loop_jitter_ms": round(std_dev, 3),
        "baseline_stable": std_dev < 15.0
    }


def check_network_loopback():
    """Checks loopback socket responsiveness."""
    try:
        t0 = time.perf_counter()
        sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        sock.settimeout(1.0)
        sock.bind(("127.0.0.1", 0))
        sock.listen(1)
        port = sock.getsockname()[1]

        client = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        client.connect(("127.0.0.1", port))
        conn, _ = sock.accept()
        client.sendall(b"ping")
        data = conn.recv(4)
        client.close()
        conn.close()
        sock.close()
        roundtrip_ms = (time.perf_counter() - t0) * 1000.0
        return {"loopback_ok": data == b"ping", "roundtrip_ms": round(roundtrip_ms, 3)}
    except Exception as e:
        return {"loopback_ok": False, "error": str(e)}


def run_host_calibration(output_dir=None):
    """
    Executes complete host calibration check and exports calibration report.
    Returns (is_stable, report_dict).
    """
    logger.info("Executing host calibration test (T7.1)...")
    system_info = get_system_telemetry()
    timer_info = measure_timer_precision()
    cpu_info = measure_cpu_baseline()
    network_info = check_network_loopback()

    is_stable = (
        timer_info["max_jitter_ms"] < 25.0 and
        cpu_info["baseline_stable"] and
        network_info.get("loopback_ok", False)
    )

    report = {
        "calibration_timestamp": datetime.now().isoformat(),
        "task_id": "T7.1",
        "status": "CALIBRATION_STABLE" if is_stable else "CALIBRATION_WARNING",
        "system_telemetry": system_info,
        "timer_precision": timer_info,
        "cpu_baseline": cpu_info,
        "network_loopback": network_info,
        "recommendation": "Host environment ready for scientific benchmarking" if is_stable else "High background jitter detected"
    }

    if output_dir:
        out_path = Path(output_dir).resolve()
        out_path.mkdir(parents=True, exist_ok=True)
        report_file = out_path / "calibration_report.json"
        with open(report_file, "w", encoding="utf-8") as f:
            json.dump(report, f, indent=2)
        logger.info(f"Calibration report archived to {report_file}")

    return is_stable, report


if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")
    out_dir = Path(__file__).resolve().parent / "results" / "raw"
    stable, rep = run_host_calibration(out_dir)
    logger.info(f"Calibration result: stable={stable}")
    print(json.dumps(rep, indent=2))
