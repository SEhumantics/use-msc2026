#!/usr/bin/env python3
"""Regression test for the raw-agreement statistic (REVIEW CORRECTION).

An earlier draft of Section 4.3 claimed "none of the 32 excluded configurations agrees with its
reference outcome (raw agreement 42 of 75)". Both halves were wrong: GraphColoring (reference
SAT; all five incumbent backends SATISFIABLE) and GraphColoring-UNSAT (reference UNSAT; all five
backends UNSATISFIABLE) DO agree on the incumbent's raw real verdicts, so the configuration-level
raw count is 44/75. This test pins the corrected statistic to the actual raw records so the paper
cannot cite the wrong number again.

Run:  python3 scripts/test_raw_agreement_statistic.py
"""

import json
import os
import shutil
import subprocess
import sys
import tempfile
import unittest

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
REFERENCE_RUN = os.environ.get(
    "RAW_AGREEMENT_TEST_DATA",
    os.path.join(ROOT, "..", "output", "experiments", "cycle1-2026-09-04"))

GRAPH_COLORING = {"GraphColoring", "GraphColoring-UNSAT"}


class TestRawAgreementStatistic(unittest.TestCase):
  @classmethod
  def setUpClass(cls):
    cls.tmp = tempfile.mkdtemp(prefix="raw-agreement-test-")
    shutil.copytree(
        os.path.join(REFERENCE_RUN, "corpus"), os.path.join(cls.tmp, "corpus"))
    shutil.copy(
        os.path.join(REFERENCE_RUN, "scaling.json"),
        os.path.join(cls.tmp, "scaling.json"))
    result = subprocess.run(
        [
            sys.executable,
            os.path.join(ROOT, "scripts", "regenerate-paper-numbers.py"),
            cls.tmp,
        ],
        capture_output=True,
        text=True,
        cwd=ROOT,
    )
    cls.exit_code = result.returncode
    with open(os.path.join(cls.tmp, "paper-numbers.json")) as handle:
      cls.numbers = json.load(handle)
    with open(os.path.join(cls.tmp, "populations.json")) as handle:
      cls.populations = json.load(handle)

  def test_generation_succeeds_and_reconciles(self):
    self.assertEqual(
        0, self.exit_code, "the generator must pass its own reconciliation gate")

  def test_raw_agreement_is_44_of_75(self):
    raw = self.numbers["compatibility"]["rawAgreementAllCandidates"]
    self.assertEqual(75, raw["denominator"])
    self.assertEqual(44, raw["agreements"])

  def test_both_graph_coloring_configurations_are_raw_agreements(self):
    raw = self.numbers["compatibility"]["rawAgreementAllCandidates"]
    self.assertTrue(
        GRAPH_COLORING.issubset(set(raw["agreementIds"])),
        "GraphColoring and GraphColoring-UNSAT must be counted as raw agreements")

  def test_graph_coloring_stays_excluded_from_mutual_analyzability(self):
    pop = self.populations["populations"]
    self.assertFalse(GRAPH_COLORING & set(pop["POP-003"]["members"]))
    self.assertTrue(GRAPH_COLORING.issubset(set(pop["POP-004"]["members"])))

  def test_mutually_analyzable_population_is_unchanged(self):
    pop = self.populations["populations"]
    self.assertEqual(43, pop["POP-003"]["size"])
    self.assertEqual(32, pop["POP-004"]["size"])
    self.assertEqual(42, self.numbers["compatibility"]["agreements"])
    self.assertEqual(
        ["UnionNav-UNSAT"], self.numbers["compatibility"]["disagreementIds"])

  def test_strata_partition_the_candidates(self):
    raw = self.numbers["compatibility"]["rawAgreementAllCandidates"]
    self.assertEqual(
        75,
        raw["agreements"] + raw["disagreements"] + raw["noRealVerdict"])


if __name__ == "__main__":
  import os

  unittest.main()
