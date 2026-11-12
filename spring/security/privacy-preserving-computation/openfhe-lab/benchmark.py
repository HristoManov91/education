"""Exploratory demonstration timings; not production performance sizing."""
import argparse
import statistics
import time

from client import run_batch
from protocol import load_cases, score


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--runs", type=int, default=3)
    args = parser.parse_args()
    if not 1 <= args.runs <= 10:
        parser.error("--runs must be 1..10 to bound expensive FHE operations")
    cases = load_cases()
    times = []
    for index in range(args.runs):
        result = run_batch(cases)
        if list(result.values) != [score(case.values) for case in cases]:
            raise AssertionError("FHE result differs from trusted plaintext oracle")
        times.append(result.timings)
        print(f"Run {index + 1}: real BFV PASS for {len(cases)} packed scores")

    for name, field in [
        ("keygen+context", "context_and_keygen_ns"),
        ("encryption", "encrypt_ns"),
        ("request serialization", "serialize_request_ns"),
        ("worker roundtrip incl subprocess startup", "worker_roundtrip_ns"),
        ("server eval only", "server_evaluate_ns"),
        ("server response serialization", "server_serialize_ns"),
        ("decryption", "decrypt_ns"),
    ]:
        median_ms = statistics.median(getattr(t, field) for t in times) / 1_000_000
        print(f"{name}: median {median_ms:.3f} ms")
    ref_samples = []
    for _ in range(10):
        start = time.perf_counter_ns()
        for _ in range(1000):
            [score(case.values) for case in cases]
        ref_samples.append((time.perf_counter_ns() - start) / 1000)
    print(f"Python in-process reference: median {statistics.median(ref_samples)/1e6:.4f} ms / 8 scores")
    print(f"serialized request: {times[-1].input_bytes} bytes; response: {times[-1].response_bytes} bytes")
    print("NOT apples-to-apples; do not derive a universal performance ratio.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
