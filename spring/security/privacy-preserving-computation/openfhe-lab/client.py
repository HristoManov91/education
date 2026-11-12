"""Клиент: държи secretKey само в паметта и стартира отделен FHE worker.

Работи върху 8 синтетични сценария чрез SIMD batching; нито един
plaintext input не се сериализира към worker-а.
"""
from __future__ import annotations

import json
import subprocess
import sys
import tempfile
import time
from dataclasses import dataclass
from pathlib import Path

from protocol import DEPTH, FIELDS, MODULUS, RiskCase, label, load_cases, manifest, score


@dataclass(frozen=True)
class Timings:
    context_and_keygen_ns: int
    encrypt_ns: int
    serialize_request_ns: int
    worker_roundtrip_ns: int
    server_evaluate_ns: int
    server_serialize_ns: int
    decrypt_ns: int
    input_bytes: int
    response_bytes: int


@dataclass(frozen=True)
class CompletedBatch:
    values: tuple[int, ...]
    labels: tuple[str, ...]
    timings: Timings


def signed_centered(value: int) -> int:
    """OpenFHE може да върне signed value или положителен modular residue."""
    return value - MODULUS if value > (MODULUS - 1) // 2 else value


def run_batch(cases: list[RiskCase], *, timeout_seconds: int = 120) -> CompletedBatch:
    if not 1 <= len(cases) <= 16:
        raise ValueError("Empty or oversized request batch")
    for case in cases:
        if score(case.values) != case.expected_score or label(case.expected_score) != case.expected_label:
            raise ValueError(f"Invalid trusted fixture: {case.name}")

    import openfhe as fhe  # реален native FHE; dependency е изолирана от Maven.

    begin = time.perf_counter_ns()
    params = fhe.CCParamsBFVRNS()
    params.SetPlaintextModulus(MODULUS)
    params.SetMultiplicativeDepth(DEPTH)
    crypto_context = fhe.GenCryptoContext(params)
    crypto_context.Enable(fhe.PKESchemeFeature.PKE)
    crypto_context.Enable(fhe.PKESchemeFeature.LEVELEDSHE)
    key_pair = crypto_context.KeyGen()
    init_ns = time.perf_counter_ns() - begin

    begin = time.perf_counter_ns()
    encrypted_fields = {}
    for field in FIELDS:
        numbers = [case.values[field] for case in cases]
        plaintext = crypto_context.MakePackedPlaintext(numbers)
        encrypted_fields[field] = crypto_context.Encrypt(key_pair.publicKey, plaintext)
    encrypt_ns = time.perf_counter_ns() - begin

    with tempfile.TemporaryDirectory(prefix="education-fhe-") as directory:
        root = Path(directory)
        begin = time.perf_counter_ns()
        context_path = root / "context.bin"
        if not fhe.SerializeToFile(str(context_path), crypto_context, fhe.BINARY):
            raise RuntimeError("Could not serialize BFV context")
        field_paths = {}
        for field in FIELDS:
            path = root / f"{field}.bin"
            if not fhe.SerializeToFile(str(path), encrypted_fields[field], fhe.BINARY):
                raise RuntimeError(f"Could not serialize encrypted field: {field}")
            field_paths[field] = path
        descriptor = manifest(context_path, field_paths, len(cases))
        (root / "manifest.json").write_text(json.dumps(descriptor, sort_keys=True), encoding="utf-8")
        serialize_ns = time.perf_counter_ns() - begin

        # Единствените файлове, предадени на отделния процес, са manifest,
        # public crypto context и ciphertext-и. SecretKey никога не се serializе-ва.
        begin = time.perf_counter_ns()
        result = subprocess.run(
            [sys.executable, str(Path(__file__).with_name("server.py")), str(root)],
            text=True, capture_output=True, check=True, timeout=timeout_seconds,
        )
        roundtrip_ns = time.perf_counter_ns() - begin
        server_metrics = json.loads(result.stdout.strip().splitlines()[-1])

        begin = time.perf_counter_ns()
        ciphertext, success = fhe.DeserializeCiphertext(str(root / "response.bin"), fhe.BINARY)
        if not success:
            raise ValueError("Could not deserialize encrypted response")
        plaintext = crypto_context.Decrypt(key_pair.secretKey, ciphertext)
        plaintext.SetLength(len(cases))
        values = tuple(signed_centered(int(item)) for item in plaintext.GetPackedValue()[:len(cases)])
        decrypt_ns = time.perf_counter_ns() - begin

    return CompletedBatch(
        values, tuple(label(value) for value in values),
        Timings(init_ns, encrypt_ns, serialize_ns, roundtrip_ns,
                server_metrics["evaluate_ns"], server_metrics["serialize_ns"],
                decrypt_ns, server_metrics["input_bytes"], server_metrics["response_bytes"]),
    )


def main() -> int:
    cases = load_cases()
    result = run_batch(cases)
    for case, actual, category in zip(cases, result.values, result.labels, strict=True):
        expected = score(case.values)
        if actual != expected or category != label(expected):
            raise AssertionError(f"{case.name}: BFV={actual}/{category}, plaintext={expected}/{label(expected)}")
        print(f"{case.name}: plaintext={expected}, decrypted_BFV={actual}, label={category} PASS")
    t = result.timings
    print(f"timings_ms: context/keygen={t.context_and_keygen_ns/1e6:.2f}, "
          f"encrypt={t.encrypt_ns/1e6:.2f}, serialize={t.serialize_request_ns/1e6:.2f}, "
          f"worker_roundtrip={t.worker_roundtrip_ns/1e6:.2f}, "
          f"server_compute={t.server_evaluate_ns/1e6:.2f}, "
          f"server_serialize={t.server_serialize_ns/1e6:.2f}, "
          f"decrypt={t.decrypt_ns/1e6:.2f}")
    print(f"payload_bytes: request={t.input_bytes}, response={t.response_bytes}")
    print("These are one-run demonstration timings, NOT comparative benchmarks.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
