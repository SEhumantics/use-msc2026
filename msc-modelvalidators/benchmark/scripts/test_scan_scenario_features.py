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

    def test_unrelated_slashes_outside_a_block_comment_do_not_swallow_real_code(self):
        # Regression test: a naive "text between two `/` characters" regex (e.g. one with an
        # extra backslash, `/\\*.*?\\*/` instead of `/\*.*?\*/`) doesn't actually match `/* */`
        # delimiters -- it matches ANY span between two bare slashes. A single, unpaired `/`
        # anywhere in a `--` comment (e.g. citing a path fragment like "row/col", this codebase's
        # own common style) then pairs with the NEXT unrelated `/` found anywhere later in the
        # file, swallowing every real declaration in between as if it were one giant comment. Two
        # separated single slashes (not an even run inside one line, which pair off harmlessly
        # among themselves) is exactly the shape that broke 15 of 17 real example files when this
        # bug was introduced (wiping 20-96% of their content, e.g. Redefines.use: a path comment
        # near the top left one slash dangling, which then swallowed everything down to the next
        # stray slash dozens of lines later).
        with tempfile.TemporaryDirectory() as directory:
            model = Path(directory) / "DanglingSlashThenCode.use"
            model.write_text(
                "-- see the row/col discussion below\n"
                "model DanglingSlashThenCode\n"
                "\n"
                "class A\n"
                "attributes\n"
                "  x : Integer\n"
                "end\n"
                "\n"
                "association AB between\n"
                "  A[*] role a\n"
                "  A[*] role b\n"
                "end\n"
                "\n"
                "-- another unrelated aside: this/that\n",
                encoding="utf-8",
            )

            found = {feature_id for feature_id, _line, _description in scanner.scan_file(model)}

        self.assertIn("assoc.self-referential", found,
                "the association declaration between two unrelated dangling slashes must still be scanned")

    def test_real_block_comment_is_still_stripped(self):
        with tempfile.TemporaryDirectory() as directory:
            model = Path(directory) / "RealBlockComment.use"
            model.write_text(
                "model RealBlockComment\n"
                "/* a genuine block comment spanning\n"
                "   multiple lines */\n"
                "class A end\n",
                encoding="utf-8",
            )
            stripped = scanner.strip_comments(model.read_text(encoding="utf-8"))

        self.assertNotIn("genuine block comment", stripped)
        self.assertIn("class A end", stripped)


if __name__ == "__main__":
    unittest.main()
