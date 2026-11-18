import importlib.util
from pathlib import Path
import sys
import unittest

HERE = Path(__file__).parent


def load(name):
    spec = importlib.util.spec_from_file_location(name, HERE / f"{name}.py")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


class ToolsTest(unittest.TestCase):
    def test_startup_requires_deterministic_ready(self):
        tool = load("measure_startup")
        command = [sys.executable, "-c", "print('READY answer=42')"]
        outcome = tool.measure(command, runs=2)
        self.assertEqual(outcome["runs"], 2)
        self.assertEqual(outcome["ready"], "READY answer=42")
        self.assertGreaterEqual(outcome["p50_ms"], 0)

    def test_nonzero_exit_is_not_measured_as_success(self):
        tool = load("measure_startup")
        with self.assertRaises(RuntimeError):
            tool.measure([sys.executable, "-c", "raise RuntimeError('bad')"], runs=1)

    def test_pss_not_mistaken_for_private_memory(self):
        tool = load("linux_memory")
        parsed = tool.parse_rollup(
            "Rss: 190 kB\nPss: 85 kB\n"
            "Private_Clean: 10 kB\nPrivate_Dirty: 15 kB\n"
        )
        self.assertEqual(parsed["Rss"], 190)
        self.assertEqual(parsed["Pss"], 85)
        self.assertEqual(parsed["Private"], 25)

    def test_missing_pss_is_rejected(self):
        tool = load("linux_memory")
        with self.assertRaises(ValueError):
            tool.parse_rollup("Rss: 10 kB\n")
