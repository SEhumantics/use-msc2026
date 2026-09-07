#!/usr/bin/env bash
# One command to regenerate every experimental result the paper reports.
#
# Usage: scripts/run-experiments.sh [outputDir]
#   outputDir   where raw results are written (default: docs/experiments/generated)
#
# Steps, in order:
#   1. corpus benchmark   -- all 82 configurations x 6 solver configurations, 5 measured repeats
#                            after 1 warm-up, per-example overrides in manifest.json
#   2. scenario scaling   -- n = 1..8 uncertainty coordinates (2^n scenarios) x 3 policies
#                            x {persistent, one-shot} solver, 5 repeats after 1 warm-up
#   3. paper numbers      -- regenerates every count and timing the manuscript cites
#
# Exit codes: 0 = every step succeeded; 1 = a step failed (the failing step is named).
#
# EXPECTED RUNTIME on the reference machine (Intel i5-14400F, 16 cores, WSL2):
#   step 1 ~20 min, step 2 ~4 min, step 3 <5 s.  Total well under 30 minutes.
set -uo pipefail
cd "$(dirname "$0")/.."

# Fresh-clone reproducibility: see run-tests.sh. MSC_M2_REPO isolates the Maven repository;
# bootstrap it once with scripts/bootstrap-maven.sh, or unset it to use ~/.m2 as before.
MAVEN_REPO_ARGS=()
if [[ -n "${MSC_M2_REPO:-}" ]]; then
  if [[ ! -d "$MSC_M2_REPO" || -z "$(ls -A "$MSC_M2_REPO" 2>/dev/null)" ]]; then
    echo "ERROR: MSC_M2_REPO=$MSC_M2_REPO is empty or missing - no dependencies bootstrapped." >&2
    echo "Bootstrap it once (online), then retry:" >&2
    echo "  scripts/bootstrap-maven.sh \"$MSC_M2_REPO\"" >&2
    exit 2
  fi
  export MAVEN_ARGS="${MAVEN_ARGS:+$MAVEN_ARGS }-Dmaven.repo.local=$MSC_M2_REPO"
  echo "== using isolated Maven repository: $MSC_M2_REPO =="
fi
OUT="${1:-docs/experiments/generated}"
mkdir -p "$OUT"

echo "== 1/3 corpus benchmark =="
# Snapshot the evaluated source state BEFORE building: a SHA-256 over every evaluated file
# (everything except .git, Maven target/ and IDE settings) goes into the output directory, so
# the run is auditable even while the working tree is uncommitted. A clean committed rerun whose
# gitDirty=false needs no bridge, but recording the manifest always costs nothing.
OUT_ABS="$(mkdir -p "$OUT" && cd "$OUT" && pwd)"
find . -type f -not -path "./.git/*" -not -path "*/target/*" -not -path "./.idea/*" \
  -not -path "$OUT_ABS/*" \
  -print0 | sort -z | xargs -0 sha256sum > "$OUT_ABS/source-manifest.sha256"
# install first: the benchmark resolves unc-modelvalidator from ~/.m2, so skipping this silently
# measures a STALE jar rather than the working tree.
( cd msc-modelvalidators && mvn -q -o -pl unc-modelvalidator,benchmark -am install -DskipTests ) \
  || { echo "FAILED: build"; exit 1; }
bash msc-modelvalidators/benchmark/scripts/run-benchmark.sh 5 30 1 3600 on \
  || { echo "FAILED: step 1 (corpus benchmark)"; exit 1; }

# The benchmark writes its canonical outputs to msc-modelvalidators/benchmark/target/benchmark-run
# (its incremental-write machinery is keyed to that directory). Every raw artifact is copied here
# so one output directory is self-contained: results, per-repeat times and all, run metadata with
# git revision + dirty flag + full environment, SOIL validation, and the rendered report.
echo "== copying the corpus run into the isolated output directory =="
BENCH_RUN=msc-modelvalidators/benchmark/target/benchmark-run
mkdir -p "$OUT/corpus"
cp -p "$BENCH_RUN/results.json" "$BENCH_RUN/run-metadata.json" \
      "$BENCH_RUN/soil-validation-results.json" "$BENCH_RUN/report.html" "$OUT/corpus/" \
  || { echo "FAILED: corpus copy"; exit 1; }

echo "== 2/3 scenario scaling =="
# The scaling run shares the corpus run's provenance (same checkout, same machine, minutes apart);
# a sidecar metadata file records that context instead of duplicating the environment block.
cat > "$OUT/experiment-metadata.json" << EOF
{
  "corpusRun": "corpus/run-metadata.json",
  "experimentTimestamp": "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
}
EOF
# Resolve the output directory to an absolute path BEFORE cd-ing into the benchmark module:
# the runner writes the file itself, so a relative path would be resolved against its CWD.
OUT_ABS="$(mkdir -p "$OUT" && cd "$OUT" && pwd)"
( cd msc-modelvalidators/benchmark \
  && mvn -o -q dependency:build-classpath -Dmdep.outputFile=/tmp/msc-bench-cp.txt \
  && java -cp "target/classes:$(cat /tmp/msc-bench-cp.txt)" \
       org.tzi.msc.benchmark.ScenarioScalingRunner "$OUT_ABS/scaling.json" 8 5 1 ) \
  || { echo "FAILED: step 2 (scenario scaling)"; exit 1; }

echo "== 3/3 regenerating the paper's numbers =="
python3 scripts/regenerate-paper-numbers.py "$OUT" \
  || { echo "FAILED: step 3 (paper numbers)"; exit 1; }

echo "== all experiments complete; raw results in $OUT =="
