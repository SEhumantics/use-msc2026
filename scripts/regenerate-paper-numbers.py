#!/usr/bin/env python3
"""Regenerate every number the manuscript cites, from raw experimental results.

Usage:  scripts/regenerate-paper-numbers.py [outputDir] [--latex-dir DIR]

Reads
  <outputDir>/corpus/results.json       (corpus benchmark; falls back to the benchmark's
                                         target/benchmark-run/results.json when the copy is absent,
                                         and records which source was used)
  <outputDir>/corpus/run-metadata.json  (run provenance; same fallback)
  msc-modelvalidators/benchmark/src/main/resources/manifest.json    (reference outcomes)
  msc-modelvalidators/benchmark/examples/<dir>/<section of .properties>  (configured bounds)
  <outputDir>/scaling.json              (scenario-scaling experiment)

Writes, into <outputDir>,
  paper-numbers.json   every count and timing the manuscript cites
  records.json         one enriched record per (configuration, backend) with bounds, scenario
                       domains, per-repeat runtimes, instrumentation, reference outcome,
                       classification, witness-validation facts, refusal reason and run provenance
  populations.json     membership and exclusion lists for every population the manuscript reports,
                       recomputed from raw records, plus the reconciliation verdict
  tab-scaling.tex      the scenario-scaling table, generated from scaling.json
                       (also copied to --latex-dir when given)

Reconciliation is a gate, not a report: if any headline population total disagrees with the raw
records the script prints the mismatch and exits 3, so a stale manuscript number cannot pass
silently.

Exit codes: 0 success; 2 a required input is missing; 3 a reconciliation check failed.
"""
import json
import os
import re
import statistics
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
KK_SOLVERS = ["DefaultSAT4J", "LightSAT4J", "MiniSat", "MiniSatProver", "Lingeling"]
SMT = "SMT-Z3"
REAL = {"SATISFIABLE", "UNSATISFIABLE"}
EXAMPLES_DIR = os.path.join(ROOT, "msc-modelvalidators/benchmark/examples")

# The manuscript's headline totals. Recomputed populations must match these exactly; a divergence
# is a finding to resolve, never something this script patches over.
EXPECTED_TOTALS = {
    "corpusConfigurations": 82,
    "compatibilityCandidates": 75,
    "mutuallyAnalyzable": 43,
    "compatibilityExclusions": 32,
    "diagnosticCases": 7,
    "policyProfileFixtures": 6,
    "scalingConfigurations": 24,
    "timingConfigurations": 46,
}


def load(path, what):
    if not os.path.exists(path):
        print(f"MISSING {what}: {path}", file=sys.stderr)
        sys.exit(2)
    with open(path) as handle:
        return json.load(handle)


def quartiles(values):
    ordered = sorted(values)
    n = len(ordered)
    return ordered[n // 4], ordered[(3 * n) // 4]


def split_set_literal(text):
    """Splits a USE Set{...} literal on top-level commas, respecting single-quoted strings."""
    inner = text.strip()
    if inner.lower().startswith("set{") and inner.endswith("}"):
        inner = inner[4:-1]
    items, buf, quoted = [], "", False
    for ch in inner:
        if ch == "'":
            quoted = not quoted
            buf += ch
        elif ch == "," and not quoted:
            items.append(buf.strip())
            buf = ""
        else:
            buf += ch
    if buf.strip():
        items.append(buf.strip())
    return items


def association_names(example):
    """The declared association names of the example's .use model, so configured link bounds can
    be told apart from class bounds (both use <Name>_min / <Name>_max keys)."""
    path = os.path.join(EXAMPLES_DIR, example["directory"], example["useFile"])
    if not os.path.exists(path):
        return set()
    with open(path, encoding="utf-8", errors="replace") as handle:
        return set(re.findall(r"\bassociation\s+(\w+)", handle.read(), flags=re.IGNORECASE))


def parse_properties(example):
    """Parses the example's configured section: class/association bounds, attribute component
    domains, active invariants and the query key. Returns a JSON-ready dict."""
    path = os.path.join(EXAMPLES_DIR, example["directory"], example["propertiesFile"])
    sections, current = {}, None
    if not os.path.exists(path):
        return {"error": f"properties file not found: {path}"}
    with open(path, encoding="utf-8", errors="replace") as handle:
        for raw in handle:
            line = raw.strip()
            if not line or line.startswith(("#", "//", "--", "!")):
                continue
            if line.startswith("[") and line.endswith("]"):
                current = line[1:-1].strip()
                sections.setdefault(current, [])
                continue
            if "=" in line and current is not None:
                key, _, value = line.partition("=")
                sections[current].append((key.strip(), value.strip()))
            elif "=" in line:
                key, _, value = line.partition("=")
                sections.setdefault(None, []).append((key.strip(), value.strip()))
    section_name = example.get("section")
    if section_name is None:
        named = [name for name in sections if name is not None]
        section_name = named[0] if named else None
    entries = sections.get(section_name, [])
    assocs = association_names(example)
    parsed = {
        "section": section_name,
        "classBounds": {},
        "associationBounds": {},
        "attributeDomains": [],
        "activeInvariants": [],
        "query": None,
    }
    for key, value in entries:
        if key == "query":
            parsed["query"] = value
            continue
        if key.endswith("_min") or key.endswith("_max"):
            name, _, bound = key.rpartition("_")
            try:
                number = int(value)
            except ValueError:
                continue
            target = parsed["associationBounds"] if name in assocs else parsed["classBounds"]
            target.setdefault(name, {})["min" if bound == "min" else "max"] = number
            continue
        match = re.match(r"^(.+)_(value|uncertainty|confidence|probability|lowerBound|upperBound)$", key)
        if match:
            attr, component = match.group(1), match.group(2)
            parsed["attributeDomains"].append({
                "attribute": attr,
                "component": component,
                "values": split_set_literal(value),
            })
            continue
        if value.lower() == "active":
            parsed["activeInvariants"].append(key)
            continue
    return parsed


def main():
    args = sys.argv[1:]
    latex_dir = None
    if "--latex-dir" in args:
        index = args.index("--latex-dir")
        latex_dir = args[index + 1]
        del args[index:index + 2]
    out_dir = args[0] if args else os.path.join(ROOT, "docs/experiments/generated")
    os.makedirs(out_dir, exist_ok=True)

    corpus_dir = os.path.join(out_dir, "corpus")
    fallback_dir = os.path.join(
        ROOT, "msc-modelvalidators/benchmark/target/benchmark-run")
    if os.path.exists(os.path.join(corpus_dir, "results.json")):
        results_source = os.path.join(corpus_dir, "results.json")
        metadata_source = os.path.join(corpus_dir, "run-metadata.json")
    else:
        results_source = os.path.join(fallback_dir, "results.json")
        metadata_source = os.path.join(fallback_dir, "run-metadata.json")
    results = load(results_source, "corpus results")
    metadata = load(metadata_source, "run metadata")
    manifest = load(
        os.path.join(ROOT, "msc-modelvalidators/benchmark/src/main/resources/manifest.json"),
        "manifest")
    examples = manifest["examples"]

    by_example = {}
    for row in results:
        by_example.setdefault(row["exampleId"], {})[row["solver"]] = row

    environment_id = f"{metadata.get('hostname', 'unknown')} | {metadata.get('cpuModel', 'unknown cpu')}"
    provenance = {
        "resultsSource": os.path.relpath(results_source, ROOT),
        "experimentTimestamp": metadata.get("timestamp"),
        "gitRevision": metadata.get("gitRevision"),
        "gitDirty": metadata.get("gitDirty"),
        "javaVersion": metadata.get("javaVersion"),
        "useVersion": metadata.get("useVersion"),
        "z3Version": metadata.get("z3Version"),
        "os": metadata.get("os"),
        "cpuModel": metadata.get("cpuModel"),
        "memoryTotalKb": metadata.get("memoryTotalKb"),
        "hostname": metadata.get("hostname"),
        "environmentId": environment_id,
    }

    declared = {e["id"]: e for e in examples}

    # ---- enriched records: one per (configuration, backend) -----------------------------------
    records = []
    for row in results:
        example = declared.get(row["exampleId"], {})
        bounds = parse_properties(example) if example else {"error": "not in manifest"}
        scenario_domains = [
            d for d in bounds.get("attributeDomains", [])
            if d["component"] in ("uncertainty", "confidence")
        ]
        record = {
            "configurationId": row["exampleId"],
            "modelId": example.get("directory", row["exampleId"]),
            "category": example.get("category"),
            "backend": row["solver"],
            "policy": row.get("policy"),
            "configuredScenarios": row.get("configuredScenarios"),
            "reportedScenarios": row.get("reportedScenarios"),
            "bounds": bounds,
            "scenarioDomains": scenario_domains,
            "declaredReference": example.get("expected"),
            "observedOutcome": row.get("outcome"),
            "classification": row.get("classification"),
            "witnessValidated": row.get("useChecked"),
            "witnessReconstructed": row.get("reconstructed"),
            "refusalReason": row.get("error"),
            "repeats": row.get("repeats"),
            "warmups": row.get("warmups"),
            "wallMsPerRepeat": row.get("wallMsPerRepeat", []),
            "medianWallMs": row.get("medianWallMs"),
            "solverCalls": row.get("solverCalls"),
            "scriptCharacters": row.get("scriptCharacters"),
            "provenance": provenance,
        }
        records.append(record)
    with open(os.path.join(out_dir, "records.json"), "w") as handle:
        json.dump(records, handle, indent=1, sort_keys=True)

    # ---- populations, recomputed from raw rows -------------------------------------------------

    def verdict(row):
        return {"SATISFIABLE": "sat", "UNSATISFIABLE": "unsat"}.get(row.get("outcome"))

    smt_rows = {e["id"]: by_example.get(e["id"], {}).get(SMT) for e in examples}
    kk_real = {
        e["id"]: all(by_example.get(e["id"], {}).get(s, {}).get("outcome") in REAL
                     for s in KK_SOLVERS)
        for e in examples
    }
    smt_real = {
        e["id"]: smt_rows[e["id"]] is not None
                 and smt_rows[e["id"]].get("classification") in {"SAT_VALIDATED", "UNSAT_EXACT"}
        for e in examples
    }

    pop_all = [e["id"] for e in examples]
    diagnostics = [e["id"] for e in examples if "supersession" in (e.get("scenarioTags") or [])]
    compat = [e["id"] for e in examples if e["id"] not in diagnostics]
    profiles = [e["id"] for e in examples if "scenario-profile" in (e.get("scenarioTags") or [])]
    analyzable = [i for i in compat if smt_real[i] and kk_real[i]]
    exclusions = [i for i in compat if i not in analyzable]

    def exclusion_reason(config_id):
        smt = smt_rows[config_id]
        parts = []
        if smt is None:
            parts.append("prototype produced no SMT row")
        elif smt.get("outcome") not in REAL:
            parts.append(f"prototype {smt.get('classification') or 'ERROR'}"
                         + (f": {smt.get('error')}" if smt.get("error") else ""))
        kk = by_example.get(config_id, {})
        bad = [s for s in KK_SOLVERS
               if kk.get(s, {}).get("outcome") not in REAL]
        if bad:
            outcomes = sorted({str(kk.get(s, {}).get("outcome")) for s in bad})
            parts.append("incumbent " + " / ".join(outcomes))
        return "; ".join(parts) if parts else "unspecified"

    timing = [i for i in pop_all if smt_real[i] and kk_real[i]]
    populations = {
        "POP-001": {
            "definition": "all declared corpus configurations",
            "members": pop_all,
            "size": len(pop_all),
            "relation": "universe for corpus analyses",
        },
        "POP-002": {
            "definition": "compatibility candidates: POP-001 minus the held-out diagnostic cases",
            "members": compat,
            "size": len(compat),
            "relation": "subset of POP-001; disjoint from POP-005 by study design",
        },
        "POP-003": {
            "definition": ("mutually analyzable compatibility configurations: a definite prototype "
                           "classification and a real verdict from every incumbent backend"),
            "members": analyzable,
            "size": len(analyzable),
            "relation": "subset of POP-002",
        },
        "POP-004": {
            "definition": "compatibility exclusions: POP-002 minus POP-003",
            "members": exclusions,
            "size": len(exclusions),
            "relation": "POP-002 minus POP-003",
            "reasons": {i: exclusion_reason(i) for i in exclusions},
            "rawAgreesWithReference": sorted(
                i for i in exclusions
                if next(iter({verdict(by_example[i][s]) for s in KK_SOLVERS}
                             - {None}), None) == declared[i]["expected"]["classification"]
            ),
        },
        "POP-005": {
            "definition": "targeted diagnostic configurations (manifest supersession tag)",
            "members": diagnostics,
            "size": len(diagnostics),
            "relation": "disjoint from POP-002 by study design; together with POP-002 covers POP-001",
        },
        "POP-006": {
            "definition": "policy-profile fixtures (manifest scenario-profile tag)",
            "members": profiles,
            "size": len(profiles),
            "relation": "subset of POP-002",
        },
        "POP-008": {
            "definition": ("timing-eligible configurations: a real verdict from the prototype and "
                           "from every incumbent backend"),
            "members": timing,
            "size": len(timing),
            "relation": "superset of POP-003 by construction (adds backend-timed members of POP-005)",
        },
    }

    totals = {
        "corpusConfigurations": len(pop_all),
        "compatibilityCandidates": len(compat),
        "mutuallyAnalyzable": len(analyzable),
        "compatibilityExclusions": len(exclusions),
        "diagnosticCases": len(diagnostics),
        "policyProfileFixtures": len(profiles),
        "timingConfigurations": len(timing),
    }

    # ---- corpus shape -------------------------------------------------------------------------
    numbers = {"sources": provenance}
    numbers["corpusConfigurations"] = len(examples)
    numbers["corpusModels"] = len({e["directory"] for e in examples})
    numbers["referenceUnsat"] = sum(
        1 for e in examples if e["expected"]["classification"] == "unsat")
    numbers["referenceSat"] = sum(
        1 for e in examples if e["expected"]["classification"] == "sat")

    # ---- result classification (the reportable vocabulary) ------------------------------------
    tally = {}
    unsat_ref_tally = {}
    for e in examples:
        row = smt_rows[e["id"]]
        cls = (row or {}).get("classification") or "ABSENT"
        tally[cls] = tally.get(cls, 0) + 1
        if e["expected"]["classification"] == "unsat":
            unsat_ref_tally[cls] = unsat_ref_tally.get(cls, 0) + 1
    numbers["classification"] = tally
    numbers["classificationOfReferenceUnsatRows"] = unsat_ref_tally
    numbers["verdictsOnReferenceUnsatRows"] = (
        unsat_ref_tally.get("UNSAT_EXACT", 0) + unsat_ref_tally.get("INCONCLUSIVE_NUMERICAL", 0))

    # ---- compatibility agreement ---------------------------------------------------------------
    agreements, disagreements = [], []
    for config_id in analyzable:
        smt_verdict = verdict(smt_rows[config_id])
        kk_verdicts = {verdict(by_example[config_id][s]) for s in KK_SOLVERS}
        if len(kk_verdicts) == 1 and next(iter(kk_verdicts)) == smt_verdict:
            agreements.append(config_id)
        else:
            disagreements.append(config_id)
    numbers["compatibility"] = {
        "candidates": len(compat),
        "mutuallyAnalyzable": len(analyzable),
        "analyzableIds": analyzable,
        "agreements": len(agreements),
        "agreementIds": agreements,
        "disagreementIds": disagreements,
        "exclusions": {i: exclusion_reason(i) for i in exclusions},
    }

    # ---- all-candidate raw agreement (REVIEW CORRECTION) ---------------------------------------
    # Normalization: over ALL compatibility candidates, take an incumbent backend verdict as
    # "real" only when all five backends returned SATISFIABLE or UNSATISFIABLE (this is the same
    # real-verdict definition the paper uses; trivially satisfied/unsatisfied and error rows are
    # not real verdicts). A candidate then raw-agrees iff that unanimous incumbent verdict equals
    # its declared reference outcome. This deliberately includes the two GraphColoring
    # configurations whose prototype runs refuse: exclusion from mutual analyzability is about
    # semantic comparability, not about erasing coincidental incumbent agreement.
    raw_agree, raw_disagree, raw_no_verdict = [], [], []
    for config_id in compat:
        kk_verdicts = {verdict(by_example[config_id][s]) for s in KK_SOLVERS}
        reference = declared[config_id]["expected"]["classification"]
        if None in kk_verdicts or len(kk_verdicts) != 1:
            raw_no_verdict.append(config_id)
        elif next(iter(kk_verdicts)) == reference:
            raw_agree.append(config_id)
        else:
            raw_disagree.append(config_id)
    numbers["compatibility"]["rawAgreementAllCandidates"] = {
        "definition": ("incumbent real verdict (all five backends unanimous SATISFIABLE/"
                       "UNSATISFIABLE) equals the declared reference classification, over all "
                       "compatibility candidates"),
        "denominator": len(compat),
        "agreements": len(raw_agree),
        "agreementIds": sorted(raw_agree),
        "disagreements": len(raw_disagree),
        "disagreementIds": sorted(raw_disagree),
        "noRealVerdict": len(raw_no_verdict),
        "noRealVerdictIds": sorted(raw_no_verdict),
    }

    # ---- SOIL instance validation --------------------------------------------------------------
    soil_path = os.path.join(corpus_dir, "soil-validation-results.json")
    if not os.path.exists(soil_path):
        soil_path = os.path.join(fallback_dir, "soil-validation-results.json")
    if os.path.exists(soil_path):
        soil = load(soil_path, "soil validation")
        numbers["soilValidation"] = {
            "fixtures": len(soil),
            "fixturesPassed": sum(1 for f in soil if f.get("passed")),
            "models": len({f["exampleId"] for f in soil}),
        }

    # ---- timing set: both sides return a real verdict ------------------------------------------
    timing_rows = []
    for example_id in timing:
        smt = smt_rows[example_id]
        timing_rows.append((example_id, smt, by_example[example_id]["DefaultSAT4J"]))

    z3 = [r[1]["medianWallMs"] for r in timing_rows]
    kk = [r[2]["medianWallMs"] for r in timing_rows]
    ratios = [r[2]["medianWallMs"] and r[1]["medianWallMs"] / r[2]["medianWallMs"] for r in timing_rows]
    numbers["timingSetSize"] = len(timing_rows)
    numbers["kkFasterOn"] = sum(1 for a, b in zip(z3, kk) if b < a)
    numbers["smtFasterOn"] = sum(1 for a, b in zip(z3, kk) if a < b)
    numbers["medianRatioSmtOverKk"] = round(statistics.median(ratios), 2)
    for name, series in (("z3", z3), ("kkDefaultSAT4J", kk)):
        q1, q3 = quartiles(series)
        numbers[name] = {
            "median": round(statistics.median(series), 2),
            "q1": round(q1, 2),
            "q3": round(q3, 2),
            "mean": round(statistics.mean(series), 1),
            "max": round(max(series), 1),
        }
    slowest = max(
        ((row["maxWallMs"], example_id, row["solver"])
         for example_id, solvers in by_example.items() for row in solvers.values()
         if row.get("maxWallMs") is not None),
        default=(0, None, None))
    numbers["slowestSingleSolveMs"] = round(slowest[0], 1)
    numbers["slowestSingleSolveOn"] = f"{slowest[1]} / {slowest[2]}"
    numbers["repeatsPerConfiguration"] = sorted({r["repeats"] for r in results if r.get("repeats")})
    numbers["warmupsPerConfiguration"] = sorted({r["warmups"] for r in results if r.get("warmups") is not None})

    # ---- scenario scaling ----------------------------------------------------------------------
    scaling_path = os.path.join(out_dir, "scaling.json")
    scaling = None
    if os.path.exists(scaling_path):
        with open(scaling_path) as handle:
            scaling = json.load(handle)
        persistent = [r for r in scaling if r.get("solverMode") == "persistent"]
        configs = {(r["coordinates"], r["policy"]) for r in scaling}
        numbers["scaling"] = {
            "rows": len(scaling),
            "configurations": len(configs),
            "maxCoordinates": max(r["coordinates"] for r in scaling),
            "maxScenarios": max(r["configuredScenarios"] for r in scaling),
            "byPolicyAtMaxN": {
                r["policy"]: {
                    "solverCalls": r["solverCalls"],
                    "scriptCharacters": r["scriptCharacters"],
                    "totalMs": r["totalMs"],
                }
                for r in persistent
                if r["coordinates"] == max(x["coordinates"] for x in persistent)
            },
            "oneShotVsPersistentCoverAtMaxN": {
                r["solverMode"]: r["totalMs"]
                for r in scaling
                if r["policy"] == "cover"
                and r["coordinates"] == max(x["coordinates"] for x in scaling)
            },
        }
        totals["scalingConfigurations"] = len(configs)
        write_scaling_table(scaling, os.path.join(out_dir, "tab-scaling.tex"))
        if latex_dir:
            destination = os.path.join(latex_dir, "tab-scaling.tex")
            with open(os.path.join(out_dir, "tab-scaling.tex"), encoding="utf-8") as handle:
                table_text = handle.read()
            with open(destination, "w", encoding="utf-8") as handle:
                handle.write(table_text)
    else:
        numbers["scaling"] = "UNAVAILABLE (run ScenarioScalingRunner first)"

    # ---- not derivable from raw results ---------------------------------------------------------
    numbers["featureInventory"] = (
        "UNAVAILABLE from raw results: the 74/50/11 feature counts come from the hand-built "
        "taxonomy in docs/modelvalidator-feature-matrix.json, not from a measured run")

    with open(os.path.join(out_dir, "paper-numbers.json"), "w") as handle:
        json.dump(numbers, handle, indent=2, sort_keys=True)

    with open(os.path.join(out_dir, "populations.json"), "w") as handle:
        json.dump({"totals": totals, "expectedTotals": EXPECTED_TOTALS,
                   "populations": populations}, handle, indent=1, sort_keys=True)

    # ---- record-level validation ----------------------------------------------------------------
    record_problems = []
    for row in results:
        rid = f"{row.get('exampleId','?')}/{row.get('solver','?')}"
        # timing sanity: no median above 120 s without a named exception
        med = row.get('medianWallMs', 0) or 0
        if med > 120_000:
            record_problems.append(f"{rid}: median {med:.0f} ms exceeds 120 s")
        # classification consistency: outcome and classification must not contradict
        cls = row.get('classification')
        outc = row.get('outcome')
        if cls == 'SAT_VALIDATED' and outc != 'SATISFIABLE':
            record_problems.append(f"{rid}: SAT_VALIDATED but outcome={outc}")
        if cls == 'UNSAT_EXACT' and outc != 'UNSATISFIABLE':
            record_problems.append(f"{rid}: UNSAT_EXACT but outcome={outc}")
        # ERROR rows must not carry a SAT/UNSAT outcome
        if outc == 'ERROR' and cls in ('SAT_VALIDATED', 'UNSAT_EXACT'):
            record_problems.append(f"{rid}: ERROR outcome with classification {cls}")
    if record_problems:
        print("RECORD VALIDATION FAILED:", file=sys.stderr)
        for problem in record_problems:
            print(" ", problem, file=sys.stderr)
        return 1

    # ---- reconciliation gate --------------------------------------------------------------------
    raw = numbers["compatibility"]["rawAgreementAllCandidates"]
    gate_extra = []
    if raw["denominator"] != raw["agreements"] + raw["disagreements"] + raw["noRealVerdict"]:
        gate_extra.append(
            "rawAgreementAllCandidates: denominator "
            f"{raw['denominator']} != agreements {raw['agreements']} + disagreements "
            f"{raw['disagreements']} + noRealVerdict {raw['noRealVerdict']}")
    gate_extra.extend(
        f"rawAgreementAllCandidates: expected agreements=44, got {raw['agreements']}"
        for _ in [0] if raw["agreements"] != 44)
    failed = [name for name, expected in EXPECTED_TOTALS.items() if totals.get(name) != expected]
    failed.extend(gate_extra)
    if failed:
        print("RECONCILIATION FAILED:", file=sys.stderr)
        for name in failed:
            print(f"  {name}: expected {EXPECTED_TOTALS[name]}, raw records give "
                  f"{totals.get(name)}", file=sys.stderr)
        return 3

    print(json.dumps(numbers, indent=2, sort_keys=True))
    print(f"\nwrote paper-numbers.json, records.json, populations.json, tab-scaling.tex "
          f"in {out_dir}")
    print("reconciliation: all population totals match the raw records")
    return 0


def write_scaling_table(scaling, path):
    """Generates the scenario-scaling table straight from the raw rows and validates its own
    cells against them: the COVER call count must equal the configured scenario count, and the
    reported grid must be exactly eight coordinate values by three policies."""
    persistent = [r for r in scaling if r["solverMode"] == "persistent"]
    sizes = sorted({r["coordinates"] for r in persistent})
    rows = []
    for n in sizes:
        by_policy = {}
        for policy in ("exists", "cover", "uniform"):
            matches = [r for r in persistent if r["coordinates"] == n and r["policy"] == policy]
            if len(matches) != 1:
                raise SystemExit(f"scaling rows incomplete at n={n} ({policy})")
            by_policy[policy] = matches[0]
        exists, cover, uniform = by_policy["exists"], by_policy["cover"], by_policy["uniform"]
        if cover["solverCalls"] != cover["configuredScenarios"]:
            raise SystemExit(
                f"scaling self-check failed at n={n}: COVER solverCalls "
                f"{cover['solverCalls']} != configuredScenarios {cover['configuredScenarios']}")
        rows.append((n, exists["configuredScenarios"], exists, cover, uniform))
    policies = {r["policy"] for r in scaling}
    modes = {r["solverMode"] for r in scaling}
    if policies != {"exists", "cover", "uniform"} or modes != {"persistent", "one-shot"}:
        raise SystemExit(f"unexpected scaling policy/mode set: {policies} {modes}")
    if len(scaling) != 2 * 3 * len(rows):
        raise SystemExit(f"scaling rows {len(scaling)} != 2 solver modes x 3 policies x "
                         f"{len(rows)} sizes")

    lines = [
        "% GENERATED by scripts/regenerate-paper-numbers.py from scaling.json -- do not edit.",
        "\\begin{table}[!ht]",
        "\\centering",
        ("\\caption{Scenario scaling on the generated model. \\emph{calls} counts "
         "\\texttt{(check-sat)} invocations; \\emph{script} is total SMT-LIB handed to the solver."),
        ("Medians of %d repeats after %d warm-up%s, one reused solver process.}"
         % (scaling[0]["repeats"], scaling[0]["warmups"],
            "s" if scaling[0]["warmups"] != 1 else "")),
        "\\label{tab:scaling}",
        "\\scriptsize",
        "\\setlength{\\tabcolsep}{4.5pt}",
        "\\begin{tabular}{@{}rr rr rr r@{}}",
        "\\toprule",
        ("& & \\multicolumn{1}{c}{\\EXISTS} & \\multicolumn{2}{c}{\\COVER} & "
         "\\multicolumn{2}{c}{\\UNIFORM} \\\\"),
        "\\cmidrule(lr){3-3}\\cmidrule(lr){4-5}\\cmidrule(lr){6-7}",
        "$n$ & $|\\Scen_B|$ & ms & calls & ms & script (kB) & ms \\\\",
        "\\midrule",
    ]
    for n, scenarios, exists, cover, uniform in rows:
        lines.append("%d & %d & %.0f & %d & %.0f & %.0f & %.0f \\\\" % (
            n, scenarios, exists["totalMs"], cover["solverCalls"], cover["totalMs"],
            uniform["scriptCharacters"] / 1000.0, uniform["totalMs"]))
    lines += ["\\bottomrule", "\\end{tabular}", "\\end{table}"]
    with open(path, "w", encoding="utf-8") as handle:
        handle.write("\n".join(lines) + "\n")


if __name__ == "__main__":
    sys.exit(main())
