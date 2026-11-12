"""Учебен file-based transport за OpenFHE BFV: НИКАКВА secret key на worker-а.

Файлът не използва OpenFHE и може да бъде тестван само с Python stdlib.
SHA-256 в manifest е проверка за случайно повреждане, НЕ authentication:
активен атакуващ може да преизчисли hash-а. За истински RPC са нужни
TLS, автентикация, лимити, key/context identity и криптографски commitment.
"""
from __future__ import annotations

import csv
import hashlib
import json
from dataclasses import dataclass
from pathlib import Path
from typing import Mapping

MODEL_VERSION = "synthetic-risk-v1"
MODULUS = 65_537
DEPTH = 1
FIELDS = (
    "age", "total_cholesterol", "hdl_cholesterol", "systolic_pressure",
    "hypertension", "smoker",
)
WEIGHTS = {
    "age": 3, "total_cholesterol": 1, "hdl_cholesterol": -2,
    "systolic_pressure": 2, "hypertension": 75, "smoker": 120,
}
MAX_VALUES = {
    "age": 120, "total_cholesterol": 400, "hdl_cholesterol": 200,
    "systolic_pressure": 250, "hypertension": 1, "smoker": 1,
}
MAX_BATCH = 16  # учебен ограничител, не е production rate limiting
MAX_BLOB_BYTES = 8 * 1024 * 1024
FIXTURES = Path(__file__).resolve().parents[1] / "src/test/resources/risk-cases.csv"


@dataclass(frozen=True)
class RiskCase:
    name: str
    values: dict[str, int]
    expected_score: int
    expected_label: str


def validate_values(values: Mapping[str, int]) -> None:
    if set(values) != set(FIELDS):
        raise ValueError("Field list does not match the fixed risk model schema")
    for field in FIELDS:
        value = values[field]
        if isinstance(value, bool) or not isinstance(value, int) or not 0 <= value <= MAX_VALUES[field]:
            raise ValueError(f"Invalid {field}: expected integer in [0, {MAX_VALUES[field]}]")


def score(values: Mapping[str, int]) -> int:
    """Independent plaintext oracle; cannot be called by server.py."""
    validate_values(values)
    result = sum(values[field] * WEIGHTS[field] for field in FIELDS)
    if abs(result) > (MODULUS - 1) // 2:
        raise ValueError("BFV signed plaintext domain overflow")
    return result


def label(value: int) -> str:
    return "LOW" if value < 500 else ("MEDIUM" if value < 1000 else "HIGH")


def load_cases() -> list[RiskCase]:
    with FIXTURES.open(newline="", encoding="utf-8") as stream:
        records = list(csv.DictReader(stream))
    if not 1 <= len(records) <= MAX_BATCH:
        raise ValueError("Empty batch or batch exceeds MAX_BATCH")
    cases = []
    for row in records:
        values = {field: int(row[field]) for field in FIELDS}
        validate_values(values)
        case = RiskCase(row["case"], values, int(row["expected_score"]), row["expected_label"])
        if score(values) != case.expected_score or label(case.expected_score) != case.expected_label:
            raise ValueError(f"Stale oracle fixture: {case.name}")
        cases.append(case)
    return cases


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def validate_blob(path: Path, expected_sha: str) -> int:
    if not path.is_file() or path.is_symlink():
        raise ValueError(f"Missing or invalid binary blob: {path.name}")
    size = path.stat().st_size
    if size <= 0 or size > MAX_BLOB_BYTES:
        raise ValueError(f"Blob size outside expected range: {path.name}")
    if sha256(path.read_bytes()) != expected_sha:
        raise ValueError(f"SHA-256 mismatch for {path.name}")
    return size


def manifest(context_path: Path, field_paths: Mapping[str, Path], count: int) -> dict:
    if set(field_paths) != set(FIELDS) or not 1 <= count <= MAX_BATCH:
        raise ValueError("Invalid field manifest or batch size")
    return {
        "schema_version": 1,
        "model_version": MODEL_VERSION,
        "scheme": "BFV",
        "plaintext_modulus": MODULUS,
        "multiplicative_depth": DEPTH,
        "count": count,
        "context_sha256": sha256(context_path.read_bytes()),
        "ciphertext_sha256": {field: sha256(field_paths[field].read_bytes()) for field in FIELDS},
    }


def verify_request(root: Path) -> tuple[int, int]:
    """Fail closed on schema drift and corrupt files; does NOT authenticate the caller."""
    path = root / "manifest.json"
    if not path.is_file() or path.is_symlink() or path.stat().st_size > 4096:
        raise ValueError("Missing or oversized manifest")
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except (ValueError, UnicodeDecodeError) as exc:
        raise ValueError("Malformed request manifest") from exc
    required = {"schema_version", "model_version", "scheme", "plaintext_modulus",
                "multiplicative_depth", "count", "context_sha256", "ciphertext_sha256"}
    if not isinstance(data, dict) or set(data) != required:
        raise ValueError("Unexpected manifest attributes")
    if (data["schema_version"] != 1 or data["model_version"] != MODEL_VERSION
            or data["scheme"] != "BFV" or data["plaintext_modulus"] != MODULUS
            or data["multiplicative_depth"] != DEPTH):
        raise ValueError("Unsupported BFV model or parameter profile")
    count = data["count"]
    if type(count) is not int or not 1 <= count <= MAX_BATCH:
        raise ValueError("Batch size outside configured limits")
    hashes = data["ciphertext_sha256"]
    if not isinstance(hashes, dict) or set(hashes) != set(FIELDS):
        raise ValueError("Missing or unknown ciphertext field")
    if not isinstance(data["context_sha256"], str):
        raise ValueError("Invalid context fingerprint")
    total_bytes = validate_blob(root / "context.bin", data["context_sha256"])
    for field in FIELDS:
        if not isinstance(hashes[field], str):
            raise ValueError("Invalid ciphertext hash")
        total_bytes += validate_blob(root / f"{field}.bin", hashes[field])
    return count, total_bytes
