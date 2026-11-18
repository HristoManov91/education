#!/usr/bin/env python3
"""Linux /proc smaps_rollup: RSS, PSS and private bytes for 1..N processes."""
from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
import subprocess
import time
from typing import Sequence


def parse_rollup(text: str) -> dict[str, int]:
    values: dict[str, int] = {}
    for line in text.splitlines():
        key, separator, amount = line.partition(":")
        if separator and key in {"Rss", "Pss", "Private_Clean", "Private_Dirty"}:
            values[key] = int(amount.strip().split()[0])
    if "Rss" not in values or "Pss" not in values:
        raise ValueError("smaps_rollup lacks Rss/Pss (permission or kernel support?)")
    values["Private"] = values.get("Private_Clean", 0) + values.get("Private_Dirty", 0)
    return values


def run_processes(binaries: Sequence[str], hold_ms: int,
                  library_root: str | None = None) -> dict:
    if not binaries or hold_ms < 1500 or hold_ms > 120_000:
        raise ValueError("Need 1+ binaries and hold_ms between 1500 and 120000")
    env = os.environ.copy()
    if library_root:
        base = Path(library_root)
        if not base.is_dir():
            raise FileNotFoundError(library_root)
        directories = sorted(
            {str(path.parent.resolve()) for path in base.rglob("lib*.so")}
        )
        if not directories:
            raise ValueError("No .so files in layer root: cannot launch layered binaries")
        env["LD_LIBRARY_PATH"] = os.pathsep.join(directories + [
            env.get("LD_LIBRARY_PATH", "")
        ])
    children: list[subprocess.Popen[str]] = []
    try:
        for binary in binaries:
            child = subprocess.Popen(
                [binary, f"--hold-ms={hold_ms}"],
                env=env, text=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
            )
            children.append(child)
        time.sleep(0.8)
        measurements: list[dict] = []
        for binary, child in zip(binaries, children):
            if child.poll() is not None:
                raise RuntimeError(f"{binary} exited before memory sampling")
            data = Path(f"/proc/{child.pid}/smaps_rollup").read_text()
            mem = parse_rollup(data)
            measurements.append({"binary": binary, "pid": child.pid, **mem})
        return {
            "unit": "KiB",
            "processes": measurements,
            "sum_rss_kib": sum(item["Rss"] for item in measurements),
            "sum_pss_kib": sum(item["Pss"] for item in measurements),
            "sum_private_kib": sum(item["Private"] for item in measurements),
            "explanation": "PSS proportionally divides shared pages; sum RSS double-counts sharing",
        }
    finally:
        for child in children:
            if child.poll() is None:
                child.terminate()
        for child in children:
            try:
                child.communicate(timeout=3)
            except subprocess.TimeoutExpired:
                child.kill()
                child.communicate()


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--hold-ms", type=int, default=10000)
    parser.add_argument("--layer-root", default=None)
    parser.add_argument("binary", nargs="+")
    args = parser.parse_args()
    print(json.dumps(run_processes(args.binary, args.hold_ms, args.layer_root), indent=2))


if __name__ == "__main__":
    main()
