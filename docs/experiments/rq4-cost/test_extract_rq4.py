#!/usr/bin/env python3
"""Regression tests for extract-rq4.py's median calculation.

Bug fixed 2026-09-02: summarize()'s 'median' was vals[len(vals)//2] -- the
upper-middle element for an even-length list, NOT a real median (average of
the two middle values). See extract-rq4.py's summarize() docstring comment
for the fix rationale and the Q1/Q3 convention decision.

Run with:
  python3 -m unittest docs/experiments/rq4-cost/test_extract_rq4.py
from the repo root, or:
  python3 -m unittest test_extract_rq4.py
from docs/experiments/rq4-cost/.
"""
import importlib.util
import json
import statistics
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

SCRIPT = Path(__file__).with_name("extract-rq4.py")
RAW_RESULTS = Path(__file__).with_name("raw-results-repeats5.json")

SPEC = importlib.util.spec_from_file_location("extract_rq4", SCRIPT)
extract_rq4 = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(extract_rq4)


class SummarizeMedianTest(unittest.TestCase):
    """Isolated tests of the median bug: even-length lists must average the
    two middle values, not return the upper-middle element."""

    def test_even_length_median_is_average_of_two_middle_values(self):
        # 6 values, sorted. Old buggy code: vals[6//2] == vals[3] == 40.
        # Real median: average of vals[2] and vals[3] == (30+40)/2 == 35.
        vals = [10, 20, 30, 40, 50, 60]
        result = extract_rq4.summarize(vals)
        self.assertEqual(result['median'], 35.0)
        self.assertNotEqual(result['median'], 40)  # the old bug's answer

    def test_even_length_median_needs_second_decimal_place(self):
        # Mirrors the real KK distribution shape: two 1-decimal middle values
        # that only average to an exact value with 2 decimals (1.75).
        # Old buggy code would have returned vals[len(vals)//2] == 1.9.
        vals = [0.9, 1.2, 1.6, 1.9, 8.3, 19762.5]
        result = extract_rq4.summarize(vals)
        self.assertEqual(result['median'], 1.75)
        self.assertNotEqual(result['median'], 1.9)  # the old bug's answer

    def test_odd_length_median_is_middle_value(self):
        # Odd-length case was never buggy (vals[n//2] IS the true middle
        # element for odd n); this locks that behavior in place.
        vals = [10, 20, 30, 40, 50]
        result = extract_rq4.summarize(vals)
        self.assertEqual(result['median'], 30)

    def test_summarize_matches_stdlib_statistics_median(self):
        # Cross-check against the stdlib as an independent oracle for a
        # handful of shapes, even/odd, including the n=46-like even case.
        for vals in (
            [1, 2, 3, 4],
            [1, 2, 3, 4, 5],
            list(range(1, 47)),  # n=46, same parity as the real dataset
            [5.6, 6.2] + list(range(3, 47)),
        ):
            with self.subTest(vals=vals[:4], n=len(vals)):
                svals = sorted(vals)
                expected = round(statistics.median(svals), 2)
                self.assertEqual(extract_rq4.summarize(svals)['median'], expected)


class MedianRatioTest(unittest.TestCase):
    """The 'medianRatio' aggregate in main() shares the identical
    vals[len(vals)//2] bug pattern; verified end-to-end below via the real
    pipeline (MedianRatioEndToEndTest), independent of summarize()."""

    def test_manual_even_length_ratio_median_reference(self):
        # Pure reference computation (no dependency on the script) showing
        # what a correct even-length median must be, to sanity check the
        # end-to-end test's expectation below.
        ratios = [3.89, 3.98]
        self.assertEqual(statistics.median(ratios), 3.935)


class EndToEndRealDataTest(unittest.TestCase):
    """Runs the actual script, unmodified invocation, against the real
    committed raw-results-repeats5.json (not a synthetic fixture) and checks
    the produced studyC-rq4-cost.json against values independently
    hand-verified straight from the raw file (see task notes / commit
    message for the by-hand derivation)."""

    @classmethod
    def setUpClass(cls):
        if not RAW_RESULTS.exists():
            raise unittest.SkipTest(f"raw results file not found: {RAW_RESULTS}")
        cls.tmpdir = tempfile.TemporaryDirectory()
        subprocess.run(
            [sys.executable, str(SCRIPT), str(RAW_RESULTS), cls.tmpdir.name],
            check=True, capture_output=True, text=True,
        )
        with open(Path(cls.tmpdir.name) / "studyC-rq4-cost.json") as f:
            cls.out = json.load(f)

    @classmethod
    def tearDownClass(cls):
        cls.tmpdir.cleanup()

    def test_intersection_size_is_46(self):
        self.assertEqual(self.out['partition']['intersection'], 46)
        self.assertEqual(self.out['summary']['intersectionSize'], 46)

    def test_z3_median_is_5_9_not_the_old_buggy_6_2(self):
        self.assertEqual(self.out['summary']['z3']['median'], 5.9)
        self.assertNotEqual(self.out['summary']['z3']['median'], 6.2)

    def test_kk_median_is_1_75_not_the_old_buggy_1_9(self):
        self.assertEqual(self.out['summary']['kkDefaultSAT4J']['median'], 1.75)
        self.assertNotEqual(self.out['summary']['kkDefaultSAT4J']['median'], 1.9)

    def test_median_ratio_is_3_935_not_the_old_buggy_3_98(self):
        self.assertEqual(self.out['summary']['medianRatio'], 3.935)
        self.assertNotEqual(self.out['summary']['medianRatio'], 3.98)

    def test_medians_independently_cross_checked_against_raw_rows(self):
        # Independent of summarize()/main()'s own median code: recompute
        # from the per-row (unaggregated) output using stdlib statistics,
        # which is a different code path than the one under test.
        rows = self.out['rows']
        self.assertEqual(len(rows), 46)
        z3_vals = sorted(r['z3MedianWallMs'] for r in rows)
        kk_vals = sorted(r['kkMedianWallMs'] for r in rows)
        self.assertEqual(round(statistics.median(z3_vals), 2), 5.9)
        self.assertEqual(round(statistics.median(kk_vals), 2), 1.75)


if __name__ == '__main__':
    unittest.main()
