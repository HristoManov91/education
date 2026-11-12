"""Deterministic offline tests; no scanner download, network or CVE feed required."""
import json
import tempfile
import unittest
from pathlib import Path

from compare_sboms import components, expected_components, reconcile

FIXTURES = Path(__file__).parent / "fixtures"


class SbomCoverageTest(unittest.TestCase):
    def test_complete_release_contains_reviewed_direct_and_transitive_dependencies(self):
        result = reconcile(expected_components(FIXTURES / "expected-runtime.json"),
                           components(FIXTURES / "maven-source.cdx.json"),
                           components(FIXTURES / "image-complete.cdx.json"))
        self.assertEqual(set(), result["missing_in_source"])
        self.assertEqual(set(), result["missing_in_release"])
        self.assertIn("pkg:deb/debian/base-files@12", result["unreviewed_in_release"])

    def test_missing_transitive_component_is_detected_even_when_direct_is_present(self):
        result = reconcile(expected_components(FIXTURES / "expected-runtime.json"),
                           components(FIXTURES / "maven-source.cdx.json"),
                           components(FIXTURES / "image-missing-transitive.cdx.json"))
        self.assertEqual({"pkg:maven/example/transitive-lib@2.0.0"}, result["missing_in_release"])

    def test_purl_identity_includes_version_and_deduplicates_component_occurrences(self):
        with tempfile.TemporaryDirectory() as workdir:
            bom = Path(workdir) / "bom.json"
            bom.write_text(json.dumps({"bomFormat": "CycloneDX", "components": [
                {"purl": "pkg:maven/example/client@1.0.0?type=jar"},
                {"purl": "pkg:maven/example/client@1.0.0"},
                {"purl": "pkg:maven/example/client@2.0.0"}]}), encoding="utf-8")
            self.assertEqual({"pkg:maven/example/client@1.0.0", "pkg:maven/example/client@2.0.0"}, components(bom))

    def test_invalid_format_fails_closed(self):
        with tempfile.TemporaryDirectory() as workdir:
            bom = Path(workdir) / "bom.json"
            bom.write_text(json.dumps({"components": []}), encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "CycloneDX"):
                components(bom)

    def test_expected_runtime_must_be_reviewed_not_duplicated(self):
        with tempfile.TemporaryDirectory() as workdir:
            file = Path(workdir) / "expected.json"
            file.write_text(json.dumps(["pkg:maven/example/a@1", "pkg:maven/example/a@1"]), encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "duplicated"):
                expected_components(file)


if __name__ == "__main__":
    unittest.main()
