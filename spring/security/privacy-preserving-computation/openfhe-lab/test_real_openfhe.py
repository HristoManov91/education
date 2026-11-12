"""No mock: integration test actually performs OpenFHE BFV inside a separate worker.

Run ONLY in a matching Ubuntu wheel environment with openfhe installed.
Import failure is intentional: CI must not claim PASS by skipping real FHE.
"""
import unittest

from client import run_batch
from protocol import MODULUS, label, load_cases, score


class RealBfvTest(unittest.TestCase):

    def test_eight_bfv_decryptions_match_independent_plaintext_oracle(self):
        cases = load_cases()
        result = run_batch(cases)
        actual = list(result.values)
        expected = [score(case.values) for case in cases]
        self.assertEqual(expected, actual)
        self.assertEqual([label(x) for x in expected], list(result.labels))

        # Boundary: BFV must preserve signed HDL effect (not silently wrap).
        self.assertEqual(-400, actual[3])
        self.assertTrue(all(-MODULUS // 2 < n < MODULUS // 2 for n in actual))

        # This measured ciphertext is real, not a stub; no performance claims.
        self.assertGreater(result.timings.input_bytes, 0)
        self.assertGreater(result.timings.response_bytes, 0)
        self.assertGreater(result.timings.server_evaluate_ns, 0)


if __name__ == "__main__":
    unittest.main()
