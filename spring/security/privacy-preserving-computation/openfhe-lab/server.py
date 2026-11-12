"""Отделен процес: вижда само serialized context и ciphertext, без secret key.

Не изпращай тук plaintext, encrypted private key или здравни данни.
Това е local file transport, НЕ production HTTP server.
"""
from __future__ import annotations

import json
import sys
import time
from pathlib import Path

from protocol import FIELDS, WEIGHTS, verify_request


def evaluate(request_dir: Path) -> dict:
    # Проверката на файла/схемата се изпълнява преди импорт на native code.
    count, inbound_bytes = verify_request(request_dir)
    import openfhe as fhe  # ИСТИНСКАТА OpenFHE C++ библиотека, Python bindings.

    crypto_context, ok = fhe.DeserializeCryptoContext(str(request_dir / "context.bin"), fhe.BINARY)
    if not ok:
        raise ValueError("Invalid OpenFHE crypto context")
    crypto_context.Enable(fhe.PKESchemeFeature.PKE)
    crypto_context.Enable(fhe.PKESchemeFeature.LEVELEDSHE)

    ciphertexts = {}
    for field in FIELDS:
        value, ok = fhe.DeserializeCiphertext(str(request_dir / f"{field}.bin"), fhe.BINARY)
        if not ok:
            raise ValueError(f"Could not deserialize {field}")
        ciphertexts[field] = value

    started = time.perf_counter_ns()
    result = None
    for field in FIELDS:
        # Отрицателните коефициенти също са ПЛЕЙНТЕКСТ в server model-а.
        # EvalMult(ciphertext, plaintext) не използва secretKey.
        encoded_weight = crypto_context.MakePackedPlaintext([WEIGHTS[field]] * count)
        term = crypto_context.EvalMult(ciphertexts[field], encoded_weight)
        result = term if result is None else crypto_context.EvalAdd(result, term)
    evaluate_ns = time.perf_counter_ns() - started

    response = request_dir / "response.bin"
    started = time.perf_counter_ns()
    if not fhe.SerializeToFile(str(response), result, fhe.BINARY):
        raise RuntimeError("OpenFHE response serialization failed")
    serialize_ns = time.perf_counter_ns() - started
    result_metrics = {
        "evaluate_ns": evaluate_ns,
        "serialize_ns": serialize_ns,
        "input_bytes": inbound_bytes,
        "response_bytes": response.stat().st_size,
        "batch_size": count,
    }
    (request_dir / "metrics.json").write_text(json.dumps(result_metrics, sort_keys=True), encoding="utf-8")
    return result_metrics


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("usage: server.py <temporary-request-directory>")
    # stderr/exit code are transport diagnostics. No decrypted patient data.
    print(json.dumps(evaluate(Path(sys.argv[1])), sort_keys=True))
