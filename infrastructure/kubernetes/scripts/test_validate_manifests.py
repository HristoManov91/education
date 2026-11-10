"""Unit tests for the offline manifest assertions; uses PyYAML, not a running cluster."""
from pathlib import Path
from tempfile import TemporaryDirectory
import unittest
import yaml
from validate_manifests import MANIFESTS, inspect


class ManifestAssertionsTest(unittest.TestCase):
    def test_current_manifests_are_consistent(self):
        self.assertEqual([], inspect())

    def test_rejects_service_selector_drift(self):
        with TemporaryDirectory() as tmp:
            dest = Path(tmp)
            for source in MANIFESTS.glob("*.yaml"):
                (dest / source.name).write_text(source.read_text(encoding="utf-8"), encoding="utf-8")
            path = dest / "20-service.yaml"
            doc = yaml.safe_load(path.read_text(encoding="utf-8"))
            doc["spec"]["selector"]["app"] = "typo"
            path.write_text(yaml.safe_dump(doc), encoding="utf-8")
            self.assertIn("service selector must match pod labels", inspect(dest))

    def test_rejects_missing_security_controls(self):
        with TemporaryDirectory() as tmp:
            dest = Path(tmp)
            for source in MANIFESTS.glob("*.yaml"):
                (dest / source.name).write_text(source.read_text(encoding="utf-8"), encoding="utf-8")
            path = dest / "30-deployment.yaml"
            doc = yaml.safe_load(path.read_text(encoding="utf-8"))
            doc["spec"]["template"]["spec"]["containers"][0]["securityContext"]["readOnlyRootFilesystem"] = False
            path.write_text(yaml.safe_dump(doc), encoding="utf-8")
            self.assertIn("container privilege/escalation/file-system hardening missing", inspect(dest))


if __name__ == "__main__":
    unittest.main()
