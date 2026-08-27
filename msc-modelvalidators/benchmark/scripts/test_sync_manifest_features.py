#!/usr/bin/env python3
"""Regression tests for sync-manifest-features.py.

Run with:
  python3 -m unittest scripts/test_sync_manifest_features.py
from msc-modelvalidators/benchmark.
"""
import importlib.util
import json
import unittest
from pathlib import Path


SCRIPT = Path(__file__).with_name("sync-manifest-features.py")
SPEC = importlib.util.spec_from_file_location("sync_manifest_features", SCRIPT)
syncer = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(syncer)

REPO_ROOT = Path(__file__).resolve().parents[3]
MATRIX_PATH = REPO_ROOT / "docs" / "modelvalidator-feature-matrix.json"
MANIFEST_PATH = (REPO_ROOT / "msc-modelvalidators" / "benchmark" / "src" / "main"
                 / "resources" / "manifest.json")


def _support(status="supported", sat=(), unsat=(), oracle=()):
    return {
        "status": status,
        "evidence": "fixture",
        "satScenarioIds": list(sat),
        "unsatScenarioIds": list(unsat),
        "validationOracleScenarioIds": list(oracle),
    }


TWO_PLUGIN_MATRIX = {
    "plugins": [
        {"id": "first-plugin", "name": "First"},
        {"id": "second-plugin", "name": "Second"},
    ],
    "areas": [
        {
            "key": "area-one",
            "name": "Area one",
            "features": [
                {
                    "id": "feat.only-first-cites",
                    "name": "Cited by the first plugin alone",
                    "support": {"first-plugin": _support(sat=["ScenarioA"])},
                },
                {
                    "id": "feat.only-second-cites",
                    "name": "Cited by the second plugin alone",
                    "support": {"second-plugin": _support(sat=["ScenarioB"])},
                },
                {
                    "id": "feat.both-cite",
                    "name": "Cited by both, on different scenarios",
                    "support": {
                        "first-plugin": _support(unsat=["ScenarioA"]),
                        "second-plugin": _support(oracle=["ScenarioB"]),
                    },
                },
            ],
        }
    ],
}


class InvertMatrixTest(unittest.TestCase):
    def test_inversion_unions_every_registered_plugin(self):
        # A scenario's `features` says which features THAT SCENARIO exercises. It is a property of
        # the scenario, not of any one plugin's ability to handle it. Inverting a single hardcoded
        # plugin (the pre-fix `plugin_id="kk-modelvalidator"` default) silently produced
        # `features: []` for every scenario only the second plugin exercises -- which is precisely
        # the set of scenarios that differentiates this project -- and that empty array flowed
        # through manifest.json into the Study A parity table's `Features` column as a literal 0.
        # Same class of bug as Task 3.13's `primary_plugin = plugins[0]` in sync-feature-matrix-md.py.
        inverted = syncer.invert_matrix(TWO_PLUGIN_MATRIX)

        self.assertEqual(["feat.both-cite", "feat.only-first-cites"], inverted.get("ScenarioA"))
        self.assertEqual(["feat.both-cite", "feat.only-second-cites"], inverted.get("ScenarioB"),
                         "a scenario cited only by a non-first plugin must still get its feature tags")

    def test_inversion_over_the_real_matrix_is_additive_over_the_first_plugin_alone(self):
        # The union must never REMOVE a tag: every (scenario, feature) pair the old single-plugin
        # inversion produced has to survive. Asserted against the real matrix, not a fixture.
        matrix = json.loads(MATRIX_PATH.read_text(encoding="utf-8"))
        first_plugin = matrix["plugins"][0]["id"]

        single = syncer.invert_matrix(matrix, [first_plugin])
        union = syncer.invert_matrix(matrix)

        for scenario, features in single.items():
            self.assertIn(scenario, union)
            missing = sorted(set(features) - set(union[scenario]))
            self.assertEqual([], missing,
                             f"{scenario} lost feature tags {missing} -- the fix must be additive only")

    def test_real_matrix_tags_every_scenario_a_non_first_plugin_cites(self):
        # The real-data form of the first test: on the actual matrix, scenarios cited only by
        # unc-modelvalidator (URealThreshold-*, UIntegerThreshold-*, ScenarioProfiles-*) must come
        # out tagged. A revert to the single-plugin inversion leaves every one of them empty.
        matrix = json.loads(MATRIX_PATH.read_text(encoding="utf-8"))
        union = syncer.invert_matrix(matrix)

        for plugin in matrix["plugins"]:
            per_plugin = syncer.invert_matrix(matrix, [plugin["id"]])
            self.assertTrue(per_plugin, f"{plugin['id']} cites no scenario at all")
            untagged = sorted(sid for sid in per_plugin if not union.get(sid))
            self.assertEqual([], untagged,
                             f"scenarios cited by {plugin['id']} came out with no features: {untagged}")

    def test_every_manifest_scenario_is_cited_by_at_least_one_plugin(self):
        # `features: []` in the shipped manifest is never harmless -- it renders as `Features = 0`
        # in the Study A parity table. Either the scenario genuinely exercises nothing (it does
        # not; every corpus scenario declares classes and invariants), or the matrix is missing a
        # citation. This guards the fix from decaying as new scenarios are added.
        matrix = json.loads(MATRIX_PATH.read_text(encoding="utf-8"))
        manifest = json.loads(MANIFEST_PATH.read_text(encoding="utf-8"))
        union = syncer.invert_matrix(matrix)

        uncited = sorted(ex["id"] for ex in manifest["examples"] if not union.get(ex["id"]))
        self.assertEqual([], uncited, "every corpus scenario needs at least one matrix citation")


if __name__ == "__main__":
    unittest.main()
