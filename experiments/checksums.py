#!/usr/bin/env python3
"""
ALB-M: Dataset Checksum Generator & Verification Tool (Sprint 7 - T7.6)

Generates and verifies cryptographic SHA-256 checksums across all raw benchmark
results, k6 summaries, telemetry snapshots, and consolidated CSV datasets to
guarantee immutable data integrity.
"""

import os
import sys
import hashlib
import logging
from pathlib import Path

logger = logging.getLogger("checksums")


def calculate_sha256(filepath):
    """Computes SHA-256 hex digest for a file."""
    sha256 = hashlib.sha256()
    with open(filepath, "rb") as f:
        for chunk in iter(lambda: f.read(65536), b""):
            sha256.update(chunk)
    return sha256.hexdigest()


def generate_checksum_manifest(raw_dir=None):
    """
    Computes SHA-256 for all files in raw_dir and writes checksums.sha256.
    Returns (count, manifest_path).
    """
    if raw_dir is None:
        raw_dir = Path(__file__).resolve().parent / "results" / "raw"
    raw_path = Path(raw_dir).resolve()
    manifest_path = raw_path / "checksums.sha256"

    files_to_hash = []
    for root, _, files in os.walk(raw_path):
        for f in sorted(files):
            if f in ("checksums.sha256", ".gitkeep"):
                continue
            full_path = Path(root) / f
            rel_path = full_path.relative_to(raw_path).as_posix()
            files_to_hash.append((full_path, rel_path))

    manifest_lines = []
    for full_p, rel_p in files_to_hash:
        digest = calculate_sha256(full_p)
        manifest_lines.append(f"{digest}  {rel_p}\n")

    with open(manifest_path, "w", encoding="utf-8") as f:
        f.writelines(manifest_lines)

    logger.info(f"Generated SHA-256 manifest with {len(manifest_lines)} entries: {manifest_path}")
    return len(manifest_lines), manifest_path


def verify_checksum_manifest(raw_dir=None):
    """
    Verifies all files listed in checksums.sha256 match their digests.
    Returns (all_valid, total_checked, errors).
    """
    if raw_dir is None:
        raw_dir = Path(__file__).resolve().parent / "results" / "raw"
    raw_path = Path(raw_dir).resolve()
    manifest_path = raw_path / "checksums.sha256"

    if not manifest_path.exists():
        logger.error(f"Checksum manifest not found: {manifest_path}")
        return False, 0, [f"Manifest not found: {manifest_path}"]

    total_checked = 0
    errors = []

    with open(manifest_path, "r", encoding="utf-8") as f:
        for line_num, line in enumerate(f, 1):
            line = line.strip()
            if not line or line.startswith("#"):
                continue
            parts = line.split(None, 1)
            if len(parts) != 2:
                errors.append(f"Malformed line {line_num}: {line}")
                continue

            expected_hash, rel_path = parts[0], parts[1].strip()
            target_file = raw_path / rel_path

            if not target_file.exists():
                errors.append(f"Missing file: {rel_path}")
                continue

            actual_hash = calculate_sha256(target_file)
            total_checked += 1
            if actual_hash != expected_hash:
                errors.append(f"Hash mismatch for {rel_path}: expected {expected_hash}, got {actual_hash}")

    is_valid = len(errors) == 0 and total_checked > 0
    if is_valid:
        logger.info(f"Successfully verified {total_checked} files with zero anomalies.")
    else:
        logger.warning(f"Verification completed with {len(errors)} anomalies across {total_checked} files.")

    return is_valid, total_checked, errors


if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")
    raw_dir = Path(__file__).resolve().parent / "results" / "raw"
    if "--verify" in sys.argv:
        valid, checked, errs = verify_checksum_manifest(raw_dir)
        sys.exit(0 if valid else 1)
    else:
        cnt, mf = generate_checksum_manifest(raw_dir)
        print(f"Archived {cnt} checksums into {mf}")
