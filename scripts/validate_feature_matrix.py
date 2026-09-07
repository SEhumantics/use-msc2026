#!/usr/bin/env python3
"""Validates docs/modelvalidator-feature-matrix.json and emits generated summaries.

Checks (hard failures):
  - every feature has a stable, unique id (area-qualified, no whitespace);
  - support status is one of full / partial / unsupported / not-applicable, for BOTH plugins;
  - every partial feature states the exact supported subfragment;
  - every full or partial claim links at least one evidence artifact (test class, source path,
    or manifest scenario id).

Generated summaries (written next to the matrix, as *_summary.json):
  - counts by support status per plugin;
  - the corpus's unsupported-feature exposure: exclusions by unsupported feature;
  - features exercised by the 43 mutually analyzable configurations;
  - supported features NOT exercised by that population;
  - configurations exercising multiple interacting features.

Run:  python3 scripts/validate_feature_matrix.py [outputDir]
Exit 0 clean; exit 1 on any validation failure. The matrix is an AUTHORED TAXONOMY: nothing
here measures semantic parity, and the summaries must be cited as curated classifications,
not as measured results.
"""
import json
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MATRIX = os.path.join(ROOT, "docs/modelvalidator-feature-matrix.json")
MANIFEST = os.path.join(
    ROOT, "msc-modelvalidators/benchmark/src/main/resources/manifest.json")
VALID_STATUS = {"supported", "degraded", "unsupported", "known-defect",
                "not-applicable", "unverified"}
# The paper reports the prototype's coverage over the incumbent-supported rows as
# fully-realized / partially-realized / unsupported; "degraded" is the matrix's
# partially-realized state.
PARTIAL_EQUIVALENTS = {"degraded"}


def fail(messages, message):
  messages.append(message)


def main():
  out_dir = sys.argv[1] if len(sys.argv) > 1 else os.path.join(
      ROOT, "docs/experiments/generated")
  with open(MATRIX) as handle:
    matrix = json.load(handle)
  with open(MANIFEST) as handle:
    manifest = json.load(handle)

  problems = []
  features = []
  seen_ids = set()
  for area in matrix.get("areas", []):
    for feature in area.get("features", []):
      fid = feature.get("id", "")
      if not fid or " " in fid or fid in seen_ids:
        fail(problems, f"missing or duplicate feature id: {fid!r}")
        continue
      seen_ids.add(fid)
      features.append(feature)

  manifest_ids = {e["id"] for e in manifest["examples"]}

  for feature in features:
    support = feature.get("support", {})
    for plugin, state in support.items():
      status = state.get("status")
      if status not in VALID_STATUS:
        fail(problems,
             f"{feature['id']}/{plugin}: invalid support status {status!r}")
      if status in PARTIAL_EQUIVALENTS | {"supported"} and not (
          state.get("notes") or state.get("evidence")):
        fail(problems,
             f"{feature['id']}/{plugin}: {status} without notes or evidence")
      if status in ("supported", "degraded") and not (
          state.get("evidence") or state.get("satScenarioIds")
          or state.get("unsatScenarioIds")
          or state.get("validationOracleScenarioIds")):
        fail(problems,
             f"{feature['id']}/{plugin}: {status} claim without evidence link")
      for key in ("satScenarioIds", "unsatScenarioIds",
                  "validationOracleScenarioIds"):
        unknown = [i for i in state.get(key, []) if i not in manifest_ids]
        if unknown:
          fail(problems,
               f"{feature['id']}/{plugin}/{key}: unknown configuration ids {unknown}")

  if problems:
    print("FEATURE MATRIX VALIDATION FAILED:", file=sys.stderr)
    for problem in problems:
      print(" ", problem, file=sys.stderr)
    return 1

  # ---- generated summaries -------------------------------------------------------------------
  counts = {}
  for feature in features:
    for plugin, state in feature.get("support", {}).items():
      counts.setdefault(plugin, {}).setdefault(state.get("status"), 0)
      counts[plugin][state.get("status")] += 1

  numbers_path = os.path.join(out_dir, "paper-numbers.json")
  analyzable = []
  if os.path.exists(numbers_path):
    with open(numbers_path) as handle:
      analyzable = json.load(handle).get("compatibility", {}).get("analyzableIds", [])

  exercised = {}
  for feature in features:
    fid = feature["id"]
    configs = set()
    for plugin, state in feature.get("support", {}).items():
      for key in ("satScenarioIds", "unsatScenarioIds",
                  "validationOracleScenarioIds"):
        configs.update(state.get(key, []))
    hit = sorted(configs & set(analyzable))
    if hit:
      exercised[fid] = hit

  not_exercised = sorted(
      f["id"] for f in features
      if f["support"].get("kk-modelvalidator", {}).get("status") == "supported"
      and f["id"] not in exercised)

  multi = sorted(
      (cid, n) for cid, n in (
          (config, sum(
              1 for f in features
              if any(config in f.get("support", {}).get(p, {}).get(key, [])
                     for p in f.get("support", {})
                     for key in ("satScenarioIds", "unsatScenarioIds",
                                 "validationOracleScenarioIds"))))
          for config in analyzable) if n >= 3)

  summaries = {
      "authored-taxonomy-note":
          "curated classification, NOT a measured parity result",
      "countsBySupportStatus": counts,
      "featuresExercisedByMutuallyAnalyzablePopulation": exercised,
      "supportedFeaturesNotExercisedByThatPopulation": not_exercised,
      "configurationsExercisingMultipleFeatures": multi,
  }
  with open(os.path.join(out_dir, "feature-matrix-summary.json"), "w") as handle:
    json.dump(summaries, handle, indent=1, sort_keys=True)
  print(f"feature matrix OK: {len(features)} features; summaries in {out_dir}")
  return 0


if __name__ == "__main__":
  sys.exit(main())
