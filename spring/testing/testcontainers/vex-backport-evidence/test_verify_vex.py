"""Adversarial checks for exact release, BOM-Link, analysis, patch and review scope.

The fixtures are synthetic: a green consistency gate does NOT authenticate the
supplier, prove the backport correct, or approve suppressing a scanner alert.
"""
import copy
from datetime import date
import json
from pathlib import Path
import subprocess
import sys
import unittest

from verify_vex import EvidenceError, check_review, load_blob, load_json

FIXTURES = Path(__file__).with_name("fixtures")
AS_OF = date(2026, 11, 13)


class VexBackportEvidenceTest(unittest.TestCase):

    def setUp(self):
        self.sbom = load_json(FIXTURES / "release.cdx.json")
        self.vex = load_json(FIXTURES / "vex.cdx.json")
        self.evidence = load_json(FIXTURES / "review.json")
        self.blobs = {key: load_blob(FIXTURES / filename) for key, filename in {
            "artifact": "release-artifact.txt",
            "patch": "backport.diff",
            "regression": "regression-report.txt",
            "reachability": "reachability-report.txt",
        }.items()}

    def run_gate(self, as_of=AS_OF):
        return check_review(self.sbom, self.vex, self.evidence, self.blobs, as_of=as_of)

    def blocked(self, phrase):
        with self.assertRaisesRegex(EvidenceError, phrase):
            self.run_gate()

    def test_positive_two_scoped_vulnerability_decisions_are_ready_for_human_review(self):
        result = self.run_gate()
        self.assertEqual(2, result.reviewed_findings)
        self.assertEqual(
            ("not_affected", "resolved_with_pedigree"), result.states
        )
        self.assertEqual(self.evidence["release"]["sha256"], result.artifact_sha256)

    def test_documented_cli_is_executable_without_third_party_dependencies(self):
        completed = subprocess.run(
            [sys.executable, str(Path(__file__).with_name("verify_vex.py")),
             "--fixtures", str(FIXTURES), "--as-of", "2026-11-13"],
            text=True, capture_output=True, check=False,
        )
        self.assertEqual(0, completed.returncode, completed.stdout + completed.stderr)
        self.assertIn("READY_FOR_HUMAN_REVIEW", completed.stdout)
        self.assertIn("NOT a vulnerability fix proof", completed.stdout)

    def test_changed_release_artifact_is_rejected_even_when_names_match(self):
        self.blobs["artifact"] += b"tampered"
        self.blocked("release digest")

    def test_sbom_product_digest_must_match_exact_release(self):
        self.sbom["metadata"]["component"]["hashes"][0]["content"] = "0" * 64
        self.blocked("SBOM product digest")

    def test_vex_product_digest_must_match_exact_release(self):
        self.vex["metadata"]["component"]["hashes"][0]["content"] = "f" * 64
        self.blocked("VEX product digest")

    def test_foreign_bom_link_cannot_reuse_a_not_affected_assessment(self):
        self.vex["vulnerabilities"][1]["affects"][0]["ref"] = \
            "urn:cdx:22222222-2222-4222-8222-222222222222/1#legacy-parser-fork-1.2.0-edu1"
        self.blocked("does not target the exact SBOM component")

    def test_old_bom_version_is_not_the_same_reviewed_release(self):
        self.sbom["version"] = 2
        self.blocked("another SBOM identity")

    def test_retargeting_a_different_dependency_purl_is_rejected(self):
        self.evidence["component_purl"] = "pkg:maven/edu.example/other-parser@1.2.0"
        self.blocked("PURL must resolve")

    def test_duplicate_component_or_vulnerability_cannot_hide_target(self):
        self.sbom["components"].append(copy.deepcopy(self.sbom["components"][0]))
        self.blocked("Duplicate SBOM components")
        self.sbom["components"].pop()
        self.vex["vulnerabilities"].append(copy.deepcopy(self.vex["vulnerabilities"][0]))
        self.blocked("Duplicate VEX findings")

    def test_no_assessment_can_pass_while_still_in_triage(self):
        self.vex["vulnerabilities"][0]["analysis"]["state"] = "in_triage"
        self.evidence["decisions"][0]["state"] = "in_triage"
        self.blocked("NOT cleared")

    def test_not_affected_requires_a_reason_and_scoped_evidence(self):
        self.vex["vulnerabilities"][1]["analysis"].pop("justification")
        self.blocked("not_affected must explain")
        self.vex["vulnerabilities"][1]["analysis"]["justification"] = "code_not_reachable"
        self.blobs["reachability"] = self.blobs["reachability"].replace(
            b"CVE-2099-90002", b"CVE-2099-99999")
        self.blocked("reachability report bytes")

    def test_vague_or_missing_impact_analysis_cannot_suppress_alerts(self):
        self.vex["vulnerabilities"][1]["analysis"]["detail"] = "all good"
        self.blocked("vague or absent VEX")

    def test_false_owner_or_expired_review_cannot_pass(self):
        self.evidence["decisions"][0]["owner"] = ""
        self.blocked("missing accountable owner")
        self.evidence["decisions"][0]["owner"] = "education-security-review"
        self.evidence["decisions"][0]["expires_at"] = "2026-11-12"
        self.blocked("expired")

    def test_backport_patch_bytes_must_match_evidence_digest(self):
        self.blobs["patch"] = self.blobs["patch"] + b"unexpected patch"
        self.blocked("backport patch bytes")

    def test_backport_must_be_in_pedigree_and_resolve_exact_vulnerability(self):
        patch = self.sbom["components"][0]["pedigree"]["patches"][0]
        patch["resolves"][0]["id"] = "CVE-2099-90002"
        self.blocked("backport pedigree")

    def test_duplicate_or_unreviewed_vulnerability_fails_closed(self):
        self.evidence["decisions"].pop()
        self.blocked("Every VEX finding")

    def test_test_report_cannot_be_mismatched_with_backport_review(self):
        self.blobs["regression"] = b"FAKE GREEN"
        self.blocked("test report bytes")

    def test_full_positive_fixture_expires_when_review_is_stale(self):
        with self.assertRaisesRegex(EvidenceError, "expired"):
            self.run_gate(as_of=date(2027, 2, 2))


if __name__ == "__main__":
    unittest.main()
