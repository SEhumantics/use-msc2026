#!/usr/bin/env python3
"""Regenerate every scenario's `features` array in manifest.json by inverting
docs/modelvalidator-feature-matrix.json: for each scenario ID, collect every
feature ID whose kk-modelvalidator support entry cites that scenario in
satScenarioIds / unsatScenarioIds / validationOracleScenarioIds.

The matrix is the source of truth for "which feature does this scenario
exercise" -- manifest.json's `features` field is a derived, denormalized
copy kept only so the dashboard doesn't have to invert the matrix at page
load. Never hand-edit manifest.json's `features` arrays; edit the matrix's
support entries instead, then re-run this script. (This is the second half
of the same problem sync-feature-matrix-md.py solves for the .md: both used
to be one-off scratchpad scripts that had to be reconstructed by hand more
than once in the same thesis session.)

Also reports (does not fail on) two classes of drift, since both are real
bugs worth a human's attention:
  - a scenario ID cited by the matrix that doesn't exist in manifest.json
    (typo, or a scenario that got renamed/removed after the matrix was
    written)
  - a scenario in manifest.json cited by zero matrix features (expected for
    a scenario nobody has tagged yet; worth checking that's actually why)

Usage: python3 sync-manifest-features.py [--check]
       (--check: exit 1 if applying the sync would change manifest.json,
        without writing -- useful as a CI/pre-commit guard)
"""
import json
import sys
from pathlib import Path


def invert_matrix(matrix, plugin_id="kk-modelvalidator"):
    """Returns {scenario_id: sorted [feature_id, ...]}."""
    by_scenario = {}
    for area in matrix["areas"]:
        for f in area["features"]:
            sup = f["support"].get(plugin_id)
            if not sup:
                continue
            cited = set()
            for key in ("satScenarioIds", "unsatScenarioIds", "validationOracleScenarioIds"):
                cited.update(sup.get(key) or [])
            for sid in cited:
                by_scenario.setdefault(sid, set()).add(f["id"])
    return {sid: sorted(fids) for sid, fids in by_scenario.items()}


def main():
    check_only = "--check" in sys.argv
    repo_root = Path(__file__).resolve().parents[3]
    matrix_path = repo_root / "docs" / "modelvalidator-feature-matrix.json"
    manifest_path = repo_root / "msc-modelvalidators" / "benchmark" / "src" / "main" / "resources" / "manifest.json"

    matrix = json.loads(matrix_path.read_text(encoding="utf-8"))
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    examples = manifest["examples"]
    example_ids = {ex["id"] for ex in examples}

    features_by_scenario = invert_matrix(matrix)

    unknown_scenarios = sorted(set(features_by_scenario) - example_ids)
    if unknown_scenarios:
        print("WARNING: matrix cites scenario IDs not present in manifest.json "
              "(typo, or renamed/removed scenario):", file=sys.stderr)
        for sid in unknown_scenarios:
            print(f"  {sid}", file=sys.stderr)

    uncited = sorted(example_ids - set(features_by_scenario))
    if uncited:
        print("NOTE: scenarios cited by zero matrix features (not necessarily wrong -- "
              "check whether they're just not tagged yet):", file=sys.stderr)
        for sid in uncited:
            print(f"  {sid}", file=sys.stderr)

    changed = 0
    for ex in examples:
        new_features = features_by_scenario.get(ex["id"], [])
        old_features = ex.get("features", [])
        if new_features != old_features:
            changed += 1
            if not check_only:
                ex["features"] = new_features

    if check_only:
        if changed:
            print(f"\n{changed} scenario(s) out of sync with the matrix. Run without --check to fix.", file=sys.stderr)
            sys.exit(1)
        print("manifest.json features are in sync with the matrix.")
        return

    if changed:
        manifest_path.write_text(json.dumps(manifest, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
        print(f"Updated {changed} scenario(s)' features in {manifest_path}")
    else:
        print("Already in sync -- no changes written.")


if __name__ == "__main__":
    main()
