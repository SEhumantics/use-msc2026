#!/usr/bin/env bash
# Runs the full benchmark pipeline end-to-end and reproducibly:
#   1. BenchmarkRunner    -- finding (Kodkod/-validate search), in-process, nanoTime-timed
#   2. SoilValidationRunner -- validating (check -v fixtures), real use-gui.jar -nogui subprocess
#   3. ReportBuilder      -- renders both, plus run-metadata.json and the feature matrix, into one
#                            self-contained HTML file
# This replaces what was previously a manual, undocumented multi-command sequence.
#
# Usage: scripts/run-benchmark.sh [repeats] [soilTimeoutSeconds] [warmups] [overallTimeoutSeconds] [safety]
#   repeats                per-example solver repeats, unless overridden in manifest.json (default: 5)
#   soilTimeoutSeconds     per-fixture subprocess timeout for SoilValidationRunner (default: 30)
#   warmups                discarded JIT warm-up iterations before each example/solver's measured
#                          repeats, unless overridden in manifest.json (default: 0, i.e. unchanged
#                          behavior -- opt in explicitly since the slowest examples (NQueens,
#                          GraphColoring, Sudoku) already cap repeats at 1-2 specifically to bound
#                          wall-clock cost, and a warmup roughly doubles that cost for them)
#   overallTimeoutSeconds  hard ceiling on the whole BenchmarkRunner step when safety=on (default:
#                          1800 = 30min, well above a normal ~3-5min run). Ignored when safety=off.
#   safety                 "on" (default) or "off". This benchmark's whole point is producing numbers
#                          comparable against the ORIGINAL, unmodified kk-modelvalidator plugin, which
#                          has no timeout/watchdog/incremental-output machinery at all -- so that
#                          machinery is fully optional, not baked in. safety=on: the outer `timeout`
#                          wrapper below is active AND BenchmarkRunner runs its per-solve watchdog
#                          thread (see BenchmarkRunner's class javadoc for exactly what that does and
#                          why no safe in-process solve timeout exists at all). safety=off: neither
#                          runs -- BenchmarkRunner's timed region is byte-for-byte what it was before
#                          this safety layer existed, at the cost of no hang protection whatsoever.
#                          Incremental result-writing is NOT gated by this flag: it changes no measured
#                          value (it happens entirely outside every timed region), so there is no
#                          fairness reason to disable it even in safety=off / original-comparison runs.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BENCHMARK_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
MSC_MODEL_VALIDATORS_DIR="$(cd "$BENCHMARK_DIR/.." && pwd)"
KK_DIR="$MSC_MODEL_VALIDATORS_DIR/kk-modelvalidator"
# Examples live inside benchmark/ itself (moved from kk-modelvalidator/examples so this module is
# self-contained/shareable) -- vendored-solvers stays under kk-modelvalidator/, unaffected.
EXAMPLES_DIR="$BENCHMARK_DIR/examples"
USE_MSC2026_DIR="$(cd "$MSC_MODEL_VALIDATORS_DIR/.." && pwd)"
USE_GUI_JAR="$USE_MSC2026_DIR/use-gui/target/use-gui.jar"

REPEATS="${1:-5}"
SOIL_TIMEOUT="${2:-30}"
WARMUPS="${3:-0}"
OVERALL_TIMEOUT="${4:-1800}"
SAFETY="${5:-on}"
if [ "$SAFETY" != "on" ] && [ "$SAFETY" != "off" ]; then
  echo "safety must be 'on' or 'off', got: $SAFETY" >&2
  exit 1
fi

OUT_DIR="$BENCHMARK_DIR/target/benchmark-run"
mkdir -p "$OUT_DIR"

echo "[1/5] Compiling and building classpath..."
mvn -q -f "$MSC_MODEL_VALIDATORS_DIR/pom.xml" -pl benchmark -am compile
CP_FILE="$OUT_DIR/classpath.txt"
mvn -q -f "$MSC_MODEL_VALIDATORS_DIR/pom.xml" -pl benchmark -am dependency:build-classpath \
    -Dmdep.outputFile="$CP_FILE" -Dmdep.includeScope=test
CP="$(cat "$CP_FILE"):$BENCHMARK_DIR/target/classes"

echo "[2/5] Recording run metadata..."
GIT_REV="unknown"
GIT_DIRTY="false"
if git -C "$USE_MSC2026_DIR" rev-parse HEAD >/dev/null 2>&1; then
  GIT_REV="$(git -C "$USE_MSC2026_DIR" rev-parse HEAD)"
  [ -n "$(git -C "$USE_MSC2026_DIR" status --short 2>/dev/null)" ] && GIT_DIRTY="true"
fi
cat > "$OUT_DIR/run-metadata.json" << EOF
{
  "timestamp": "$(date -u +%Y-%m-%dT%H:%M:%SZ)",
  "gitRevision": "$GIT_REV",
  "gitDirty": $GIT_DIRTY,
  "javaVersion": "$(java -version 2>&1 | head -1 | tr -d '"')",
  "os": "$(uname -srm)",
  "hostname": "$(hostname)",
  "repeats": $REPEATS,
  "warmups": $WARMUPS,
  "soilTimeoutSeconds": $SOIL_TIMEOUT,
  "overallTimeoutSeconds": $OVERALL_TIMEOUT,
  "safety": "$SAFETY"
}
EOF

if [ "$SAFETY" = "on" ]; then
  echo "[3/5] Running BenchmarkRunner (finding, $REPEATS repeats + $WARMUPS warmups per example," \
       "outer ${OVERALL_TIMEOUT}s ceiling, watchdog on)..."
  # `timeout` sends SIGTERM at the deadline and, if that isn't enough, SIGKILL 30s later (-k) -- this is
  # the actual hang-protection mechanism (see the usage comment above for why it lives here, at the OS
  # process level, rather than inside BenchmarkRunner itself). Captured manually (not `set -e`) so a
  # timeout can be handled gracefully instead of aborting this whole script: BenchmarkRunner writes
  # results.json incrementally, so whatever was measured before the kill is still there to report on.
  set +e
  timeout -k 30s "${OVERALL_TIMEOUT}s" \
      java -Djava.library.path="$KK_DIR/vendored-solvers/x64" -cp "$CP" org.tzi.msc.benchmark.BenchmarkRunner \
      "$EXAMPLES_DIR" "$OUT_DIR/results.json" "$REPEATS" "$WARMUPS" true
  BENCHMARK_EXIT=$?
  set -e
  if [ "$BENCHMARK_EXIT" -eq 124 ] || [ "$BENCHMARK_EXIT" -eq 137 ]; then
    echo "  WARNING: BenchmarkRunner was killed after exceeding the ${OVERALL_TIMEOUT}s ceiling -- likely a" \
         "genuinely hung solve (see the last '=== ... ===' / cell line above for roughly where). Continuing" \
         "with whichever cells were written to results.json before the kill; re-run with a larger ceiling or" \
         "investigate that specific example/solver directly." >&2
  elif [ "$BENCHMARK_EXIT" -ne 0 ]; then
    echo "  BenchmarkRunner failed (exit $BENCHMARK_EXIT), not a timeout -- aborting." >&2
    exit "$BENCHMARK_EXIT"
  fi
else
  echo "[3/5] Running BenchmarkRunner (finding, $REPEATS repeats + $WARMUPS warmups per example," \
       "safety=off: no outer timeout, no watchdog -- original, unprotected timing)..."
  java -Djava.library.path="$KK_DIR/vendored-solvers/x64" -cp "$CP" org.tzi.msc.benchmark.BenchmarkRunner \
      "$EXAMPLES_DIR" "$OUT_DIR/results.json" "$REPEATS" "$WARMUPS" false
fi

echo "[4/5] Running SoilValidationRunner (validating, real use-gui.jar subprocess)..."
if [ -f "$USE_GUI_JAR" ]; then
  java -cp "$CP" org.tzi.msc.benchmark.SoilValidationRunner \
      "$EXAMPLES_DIR" "$USE_GUI_JAR" "$OUT_DIR/soil-validation-results.json" "$SOIL_TIMEOUT"
else
  echo "  SKIPPED: $USE_GUI_JAR not found -- build it first (mvn -pl use-gui -am package)." >&2
  echo "[]" > "$OUT_DIR/soil-validation-results.json"
fi

echo "[5/5] Building report..."
java -cp "$CP" org.tzi.msc.benchmark.ReportBuilder \
    "$BENCHMARK_DIR/src/main/resources/manifest.json" "$OUT_DIR/results.json" \
    "$OUT_DIR/report.html" "$OUT_DIR/soil-validation-results.json" "$OUT_DIR/run-metadata.json" \
    "$USE_MSC2026_DIR/docs/modelvalidator-feature-matrix.json"

echo
echo "Done."
echo "  results:         $OUT_DIR/results.json"
echo "  SOIL validation: $OUT_DIR/soil-validation-results.json"
echo "  run metadata:    $OUT_DIR/run-metadata.json"
echo "  report:          $OUT_DIR/report.html"
