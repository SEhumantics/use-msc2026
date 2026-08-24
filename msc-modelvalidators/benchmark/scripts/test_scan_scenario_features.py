#!/usr/bin/env python3
"""Regression tests for scan-scenario-features.py.

Run with:
  python3 -m unittest scripts/test_scan_scenario_features.py
from msc-modelvalidators/benchmark.
"""
import importlib.util
import tempfile
import unittest
from pathlib import Path


SCRIPT = Path(__file__).with_name("scan-scenario-features.py")
SPEC = importlib.util.spec_from_file_location("scan_scenario_features", SCRIPT)
scanner = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(scanner)


class ScanScenarioFeaturesTest(unittest.TestCase):
    def test_block_comment_syntax_does_not_create_candidates(self):
        with tempfile.TemporaryDirectory() as directory:
            model = Path(directory) / "CommentOnly.use"
            model.write_text(
                "model CommentOnly\n"
                "/* ->collect(x | x)\n"
                "   associationclass Fake between\n"
                "     A [1] role a\n"
                "     B [1] role b\n"
                "   end */\n"
                "class A end\n",
                encoding="utf-8",
            )

            found = {feature_id for feature_id, _line, _description in scanner.scan_file(model)}

        self.assertNotIn("ocl.collect", found)
        self.assertNotIn("assoc.association-class", found)
        self.assertNotIn("assoc.binary", found)


if __name__ == "__main__":
    unittest.main()
