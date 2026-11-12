"""Stdlib tests for the transport contract; no FHE package required."""
import json
import tempfile
import unittest
from pathlib import Path

from protocol import FIELDS, MODULUS, load_cases, manifest, score, validate_values, verify_request


def fake_request(root: Path, *, count: int = 2) -> None:
    context = root / "context.bin"
    context.write_bytes(b"synthetic-context-only")
    paths = {}
    for field in FIELDS:
        path = root / f"{field}.bin"
        path.write_bytes(field.encode("ascii"))
        paths[field] = path
    descriptor = manifest(context, paths, count)
    (root / "manifest.json").write_text(json.dumps(descriptor), encoding="utf-8")


class ProtocolTest(unittest.TestCase):

    def test_shared_fixture_contains_boundary_and_negative_scores(self):
        cases = load_cases()
        self.assertEqual(8, len(cases))
        self.assertEqual([430, 500, 1000, -400, 1455, 625, 499, 999],
                         [score(case.values) for case in cases])

    def test_rejects_unvalidated_out_of_range_input(self):
        values = load_cases()[0].values | {"hypertension": 2}
        with self.assertRaisesRegex(ValueError, "hypertension"):
            validate_values(values)
        values = load_cases()[0].values | {"extra": 999}
        with self.assertRaisesRegex(ValueError, "Field list"):
            validate_values(values)

    def test_unsigned_modulus_wrap_is_not_an_arbitrary_integer_calculation(self):
        self.assertEqual(MODULUS - 400, (-400) % MODULUS)
        self.assertNotEqual(33_000, 33_000 % MODULUS if 33_000 < 0 else 33_000 - MODULUS)

    def test_valid_manifest_accepts_reviewed_field_list(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            fake_request(root)
            count, transferred_bytes = verify_request(root)
            self.assertEqual(2, count)
            self.assertGreater(transferred_bytes, 0)

    def test_corrupted_ciphertext_fails_before_loading_native_library(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            fake_request(root)
            (root / "smoker.bin").write_bytes(b"tampered-but-not-reflected-in-manifest")
            with self.assertRaisesRegex(ValueError, "SHA-256 mismatch"):
                verify_request(root)

    def test_wrong_profile_is_rejected_before_decryption(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            fake_request(root)
            path = root / "manifest.json"
            data = json.loads(path.read_text())
            data["plaintext_modulus"] = 257
            path.write_text(json.dumps(data))
            with self.assertRaisesRegex(ValueError, "Unsupported BFV"):
                verify_request(root)

    def test_unknown_ciphertext_field_is_rejected(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            fake_request(root)
            path = root / "manifest.json"
            data = json.loads(path.read_text())
            data["ciphertext_sha256"]["unrecognized"] = "abc"
            path.write_text(json.dumps(data))
            with self.assertRaisesRegex(ValueError, "Missing or unknown"):
                verify_request(root)

    def test_missing_ciphertext_is_rejected(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            fake_request(root)
            (root / "age.bin").unlink()
            with self.assertRaisesRegex(ValueError, "Missing or invalid"):
                verify_request(root)


if __name__ == "__main__":
    unittest.main()
