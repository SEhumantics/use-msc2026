# Reproducing the evaluation

Every measured number reported in the paper is regenerated from raw results by
`scripts/regenerate-paper-numbers.py`. Nothing measured is transcribed by hand; the one
exception is the feature inventory (74 = 50 + 11 + 13), which is a hand-built taxonomy in
`docs/modelvalidator-feature-matrix.json`, not a measured run.

## Environment this was measured on

| Component | Version |
|---|---|
| OS | Ubuntu 24.04.4 LTS on WSL2, kernel 6.6.87.2 |
| CPU | Intel Core i5-14400F (16 logical cores) |
| JDK | OpenJDK 21.0.12 |
| Maven | 3.9.16 |
| USE | 7.5.0 (this repository) |
| Z3 | 5.1.0, vendored at `tools/z3/bin/z3` |
| SAT backends | DefaultSAT4J, LightSAT4J, MiniSat, MiniSatProver, Lingeling (bundled with kk-modelvalidator) |

Z3 is vendored in-tree, so no download step is needed. Maven dependency versions are
pinned in the `pom.xml` files; build offline with `-o` to guarantee the pinned set.

## Two-phase reproduction: bootstrap, then run offline

The run/test scripts execute Maven OFFLINE (`-o`) so a measured run can never silently pull
different plugin or dependency versions. A fresh clone therefore needs a ONE-TIME online
bootstrap into an isolated Maven repository:

```
scripts/bootstrap-maven.sh /path/to/fresh-m2     # online, ~15-20 min (runs the test suites)
MSC_M2_REPO=/path/to/fresh-m2 scripts/run-tests.sh --ours-only
MSC_M2_REPO=/path/to/fresh-m2 scripts/run-experiments.sh <output-dir>
```

`MSC_M2_REPO` redirects every Maven call to that repository (`-Dmaven.repo.local`, Maven 3.9+);
an empty or missing repository fails fast with the bootstrap command instead of silently
falling back to `~/.m2`. Users with a prepared cache can omit `MSC_M2_REPO` entirely.
Requires network access for the bootstrap only, and Maven 3.9+.

## One command for the tests

```
scripts/run-tests.sh --ours-only
```

Expected: `unc-modelvalidator` 1043 tests and `benchmark` 115 tests, **0 failures**,
about 3 minutes. Exit 0 on success, 1 on any failure.

Dropping `--ours-only` also runs the vendored `kk-modelvalidator` suite, which has
**~462 pre-existing failures** unrelated to this work; `docs/kk-modelvalidator-port.md`
accounts for them as upstream test-fixture staleness and run-to-run non-determinism.

## One command for the experiments

```
scripts/run-experiments.sh <outputDir>
```

`outputDir` defaults to `docs/experiments/generated`; pass an empty directory to keep a
run fully isolated. Expected runtime ~25 minutes on the machine above. Exit 0 on
success, 1 naming the failing step. It runs, in order:

1. **Corpus benchmark** — 82 configurations x 6 solver configurations, 5 measured
   repeats after 1 warm-up. Raw rows: `msc-modelvalidators/benchmark/target/benchmark-run/results.json`,
   copied with run metadata, SOIL validation and the report into `<outputDir>/corpus/`
   so one output directory is self-contained.
2. **Scenario scaling** — n = 1..8 uncertainty coordinates, 3 policies, 2 solver
   invocation modes. Raw rows: `<outputDir>/scaling.json`
   (a reference copy is checked in at `docs/experiments/scaling/scaling-results.json`).
3. **Paper numbers** — `<outputDir>/paper-numbers.json`, `<outputDir>/records.json`
   (one enriched record per configuration x backend: bounds, scenario domains, policy,
   scenario counts, per-repeat runtimes, solver calls, script size, reference outcome,
   classification, witness-validation facts, refusal reason, run provenance),
   `<outputDir>/populations.json` (membership and exclusion lists for every reported
   population, recomputed from raw rows), and `<outputDir>/tab-scaling.tex`
   (the scaling table, generated).

Step 3 is a **reconciliation gate**: if any headline total (82 configurations,
75 compatibility candidates, 43 mutually analyzable, 32 exclusions, 7 diagnostics,
6 policy-profile fixtures, 24 scaling configurations, 46 timing configurations)
disagrees with the raw records, the script names the mismatch and exits 3. Pass
`--latex-dir DIR` to also copy the generated scaling table into the paper's LaTeX tree.

> The benchmark resolves `unc-modelvalidator` from `~/.m2`. `run-experiments.sh`
> installs first for exactly this reason; running `run-benchmark.sh` directly without a
> preceding `mvn install` silently measures a **stale jar**.

## The paper's Robot Battle witness

```
java -cp msc-modelvalidators/benchmark/target/classes:$(cat /tmp/msc-bench-cp.txt) \
    org.tzi.msc.benchmark.RobotBattleWitness robotbattle-witness.json
```

(build the classpath first with `mvn -o dependency:build-classpath
-Dmdep.outputFile=/tmp/msc-bench-cp.txt` inside `msc-modelvalidators/benchmark`).
The dump records, for each of the eight configured scenarios of the banded case study,
its measurement quality and — where COVER witnessed one — the reconstructed snapshot's
object identities, links and representative values. The paper's object diagram reports
values from this dump.

## Which claim comes from which artifact

| Paper claim | Regenerated from | Key in `paper-numbers.json` |
|---|---|---|
| 82 configurations over 35 models | manifest | `corpusConfigurations`, `corpusModels` |
| 37 reference-UNSAT configurations | manifest | `referenceUnsat` |
| 29 exact / 7 inconclusive / 1 refused | results | `classificationOfReferenceUnsatRows` |
| 0 solver-unknown, 0 validation errors | results | `classification` |
| 43 comparable, 42 agreements, 1 disagreement | results | `compatibility.mutuallyAnalyzable`, `compatibility.agreements`, `compatibility.disagreementIds` |
| 46 timing configurations | results | `timingSetSize` |
| medians, quartiles, median ratio | results | `z3`, `kkDefaultSAT4J`, `medianRatioSmtOverKk` |
| scenario-scaling table | scaling | `scaling` |
| 74/50/11 feature categories | **not** a measured run — the hand-built taxonomy in `docs/modelvalidator-feature-matrix.json` | reported as UNAVAILABLE |

## The three-valued oracle and its test map

The oracle that re-evaluates every witness is `ThreeValuedEvaluator` (verify package): it walks
Boolean connectives and quantifiers itself -- because USE's own evaluator collapses an undefined
quantifier element to FALSE (`ExpQuery.evalForAll0`/`evalExists0`) -- and delegates every leaf to
USE. The rules it implements, and the tests that pin each:

| Semantic rule | Pinned by |
|---|---|
| Strong-Kleene and/or/implies/xor over all T/F/U operand pairs | `OracleSemanticKernelMatrixTest` (full 3x3 matrix), `KleeneStrictnessTest` (and-undef strictness), `OracleXorAndEqualityTest` |
| `not` over T/F/U | `OracleSemanticKernelMatrixTest` |
| Total `=` / `<>`: true iff both sides undefined or both defined and equal | `OracleSemanticKernelMatrixTest` (undef=undef, undef<>false), `OracleXorAndEqualityTest` |
| `forAll`: a defined-false element dominates undefined elements | `OracleThreeValuedDepthTest`, `OracleSemanticKernelMatrixTest` (forAllDominance) |
| `exists`: a defined-true element dominates undefined elements | `OracleSemanticKernelMatrixTest` (existsDominance) |
| Nested quantifiers stay UNDEFINED at every depth (no collapse to FALSE) | `OracleThreeValuedDepthTest`, `OracleSiblingGapsTest` |
| `isDefined` / `isUndefined` read the real definedness | `OracleSiblingGapsTest` (Finding 4), `OracleSemanticKernelMatrixTest` |
| Encoder-vs-evaluator agreement over generated U-type expressions | `ExpressionAgreementDifferentialTest`, `MultiVariableContextOracleAgreementTest` |
| Invariant acceptance: only definitely TRUE satisfies an active invariant | enforced throughout (`ResultClassification`); undefined and false verdicts both refute, and the verdict OUTCOME distinguishes them (`ResultClassificationOracleAgreesTest`, `KleeneStrictnessTest`) |

These tests are empirical agreement evidence for the encoder and the oracle wrapper; they are
not a proof of encoder correctness.

## Result classification

Every finder run is reported as exactly one of
`SAT_VALIDATED`, `UNSAT_EXACT`, `INCONCLUSIVE_NUMERICAL`, `UNSUPPORTED`,
`SOLVER_UNKNOWN`, `VALIDATION_ERROR`
(`unc-modelvalidator/.../smt/finder/ResultClassification.java`), and the classification
travels into `results.json` on every SMT row.

`INCONCLUSIVE_NUMERICAL` is decided by counting the quantile enclosures the encoding
actually performed (`QuantileInstrumentation`), not by re-deriving the condition from
the model, so a negative result touching a confidence threshold can never be recorded as
an exact refutation.

## Known limitations of the harness

- Encoding time in the scaling experiment is derived by subtracting measured solver and
  witness time from the total; it is not separately instrumented.
- A malformed solver response is currently classified `SOLVER_UNKNOWN` rather than
  `VALIDATION_ERROR`, because `scenarioOutcomeOf` maps everything that is not `UNSAT` to
  `UNRESOLVED`. No corpus row triggers this today (`SOLVER_UNKNOWN` count is 0).

## Exact evaluated source state

The reference run in this cycle wrote `run-metadata.json` with `gitDirty: true` (the working
tree carries uncommitted fixes alongside untracked tooling). To make the evaluated state
auditable anyway, the run directory carries `source-manifest.sha256`: a SHA-256 checksum over
every evaluated source file (everything except `.git/`, Maven `target/` and IDE settings). A
third party can verify a checkout against it with `sha256sum -c source-manifest.sha256`.
Before final submission, commit the working tree and re-run `scripts/run-experiments.sh` from
the clean revision so `gitDirty` is `false` and the recorded revision alone identifies the code.
