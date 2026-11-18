#!/usr/bin/env python3
"""Measure end-to-end process launch to exit, NOT only the Java main() method."""
from __future__ import annotations

import argparse
import json
import statistics
import subprocess
import time
from typing import Sequence


def measure(command: Sequence[str], runs: int, timeout: float = 30.0) -> dict:
    if not command or runs < 1:
        raise ValueError("Nonempty command and positive runs required")
    samples_ms: list[float] = []
    ready_payload: str | None = None
    for _ in range(runs):
        started = time.perf_counter_ns()
        result = subprocess.run(
            list(command), check=False, capture_output=True, text=True, timeout=timeout
        )
        elapsed = (time.perf_counter_ns() - started) / 1_000_000
        if result.returncode != 0:
            raise RuntimeError(f"Process exit={result.returncode}: {result.stderr[:400]}")
        lines = [line for line in result.stdout.splitlines() if line.startswith("READY ")]
        if len(lines) != 1:
            raise ValueError(f"Expected exactly one READY line: {result.stdout[:200]}")
        if ready_payload is None:
            ready_payload = lines[0]
        elif ready_payload != lines[0]:
            raise ValueError("Different outputs across runs; cannot compare")
        samples_ms.append(round(elapsed, 3))
    return {
        "command": list(command),
        "runs": runs,
        "p50_ms": round(statistics.median(samples_ms), 3),
        "min_ms": min(samples_ms),
        "max_ms": max(samples_ms),
        "samples_ms": samples_ms,
        "ready": ready_payload,
        "metric": "process launch -> exit (wall-clock)",
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--runs", type=int, default=10)
    parser.add_argument("--timeout", type=float, default=30.0)
    parser.add_argument("command", nargs=argparse.REMAINDER)
    options = parser.parse_args()
    command = options.command
    if command and command[0] == "--":
        command = command[1:]
    print(json.dumps(measure(command, options.runs, options.timeout), indent=2))


if __name__ == "__main__":
    main()
