#!/usr/bin/env bash
# Runs the full benchmark pipeline end-to-end and reproducibly:
#   1. BenchmarkRunner    -- finding (Kodkod/-validate search), in-process, nanoTime-timed
#   2. SoilValidationRunner -- validating (check -v fixtures), real use-gui.jar -nogui subprocess
#   3. ReportBuilder      -- renders both, plus run-metadata.json and the feature matrix, into one
#                            self-contained HTML file
# This replaces what was previously a manual, undocumented multi-command sequence.
#
# Prerequisites: a JDK and Maven, and nothing else -- step 1 drives the ROOT reactor
# (use-msc2026/pom.xml), so use-core / kk-modelvalidator / unc-modelvalidator are all built here from
# this checkout's source and a completely empty ~/.m2 is fine. Step 4 additionally needs
# use-gui/target/use-gui.jar, which is NOT built by step 1 (the benchmark classpath does not depend on
# it -- SoilValidationRunner shells out to it as a jar); build it once with
#   mvn -f <repo root>/pom.xml -pl use-gui -am package -DskipTests
# or the SOIL validation step reports SKIPPED and the report shows no validation results.
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
CP_FILE="$OUT_DIR/classpath.txt"
# Two things here are load-bearing, and getting either wrong silently benchmarks code that is not
# this checkout's:
#
#   * ONE Maven invocation, with the lifecycle phase and dependency:build-classpath together.
#     `dependency:build-classpath` run as a bare goal in its own invocation is not part of a
#     lifecycle, so Maven has no reactor build to substitute and resolves every module dependency
#     from ~/.m2 -- i.e. from the last `mvn install`, however old. Run in the same invocation as
#     `compile`, the reactor substitution applies and each module resolves to its own target/classes.
#   * The ROOT pom, not msc-modelvalidators/pom.xml. use-core is a SIBLING of msc-modelvalidators,
#     so a reactor rooted at msc-modelvalidators cannot contain it -- use-core would come from ~/.m2
#     no matter what else is done. Rooting at the repo also means a completely fresh clone with an
#     empty ~/.m2 works, instead of failing to resolve org.tzi.use:use-core:7.5.0 with no explanation.
#
# verify_source_freshness below is the belt-and-braces check: it fails the run rather than let a
# stale jar quietly stand in for changed source.
mvn -q -f "$USE_MSC2026_DIR/pom.xml" -pl msc-modelvalidators/benchmark -am \
    compile dependency:build-classpath \
    -Dmdep.outputFile="$CP_FILE" -Dmdep.includeScope=test
CP="$(cat "$CP_FILE"):$BENCHMARK_DIR/target/classes"

# Fails the run if a benchmarked module resolved to an installed jar that predates its own source.
# $1 label, $2 module directory, $3 extended regex selecting that module's classpath entry.
verify_source_freshness() {
  local label="$1" module_dir="$2" entry_regex="$3" entry newer_source
  entry="$(tr ':' '\n' < "$CP_FILE" | grep -E "$entry_regex" | head -1 || true)"
  if [ -z "$entry" ]; then
    echo "  FAIL: $label is not on the benchmark classpath at all -- the classpath build is broken." >&2
    exit 1
  fi
  case "$entry" in
    */target/classes)
      echo "  $label: this checkout's reactor build ($entry)"
      return 0
      ;;
  esac
  newer_source="$(find "$module_dir/src/main/java" -name '*.java' -newer "$entry" -print -quit 2>/dev/null || true)"
  if [ -n "$newer_source" ]; then
    echo "  FAIL: $label resolved to the installed jar" >&2
    echo "          $entry" >&2
    echo "        but $newer_source is newer than it. Benchmarking that jar would silently report" >&2
    echo "        results for code this checkout no longer contains. Run:" >&2
    echo "          mvn -f $USE_MSC2026_DIR/pom.xml -pl msc-modelvalidators/benchmark -am install -DskipTests" >&2
    exit 1
  fi
  echo "  WARNING: $label resolved to the installed jar $entry, not this checkout's target/classes." >&2
}
verify_source_freshness "use-core" "$USE_MSC2026_DIR/use-core" '/use-core(/target/classes|-[0-9])'
verify_source_freshness "kk-modelvalidator" "$KK_DIR" '/kk-modelvalidator(/target/classes|-[0-9])'
verify_source_freshness "unc-modelvalidator" "$MSC_MODEL_VALIDATORS_DIR/unc-modelvalidator" \
    '/unc-modelvalidator(/target/classes|-[0-9])'

echo "[2/5] Recording run metadata..."
GIT_REV="unknown"
GIT_DIRTY="false"
if git -C "$USE_MSC2026_DIR" rev-parse HEAD >/dev/null 2>&1; then
  GIT_REV="$(git -C "$USE_MSC2026_DIR" rev-parse HEAD)"
  [ -n "$(git -C "$USE_MSC2026_DIR" status --short 2>/dev/null)" ] && GIT_DIRTY="true"
fi
RUN_TIMESTAMP="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
JAVA_VERSION="$(java -version 2>&1 | head -1 | tr -d '"')"
OS_DESC="$(uname -srm)"
HOST_NAME="$(hostname)"
# Reproducibility provenance beyond the bare Java/OS pair: the exact CPU model and memory size
# (timings are hardware-dependent), the Z3 version the SMT rows actually used (resolved the same
# way SolverBinary.resolve() resolves it: repo tools/z3/bin/z3 when present, else $PATH), and the
# USE release this checkout carries (root pom version).
CPU_MODEL="$(awk -F: '/model name/ {gsub(/^ /,"",$2); print $2; exit}' /proc/cpuinfo 2>/dev/null)"
MEM_TOTAL_KB="$(awk '/^MemTotal/ {print $2}' /proc/meminfo 2>/dev/null)"
case "$MEM_TOTAL_KB" in (*[!0-9]*|"") MEM_TOTAL_KB=0;; esac
Z3_BIN="$USE_MSC2026_DIR/tools/z3/bin/z3"
[ -x "$Z3_BIN" ] || Z3_BIN="$(command -v z3 || true)"
Z3_VERSION="unknown"
if [ -n "$Z3_BIN" ]; then Z3_VERSION="$("$Z3_BIN" --version 2>/dev/null | head -1)"; fi
USE_VERSION="$(sed -n 's|.*<version>\(.*\)</version>.*|\1|p' "$USE_MSC2026_DIR/pom.xml" | head -1)"

# benchmarkStatus is what tells a complete run from a killed one. It is written "running" before the
# run and REWRITTEN afterwards, because a report built from a killed run is otherwise
# indistinguishable from a complete one -- same file set, same shape, just fewer rows, which nobody
# reading the HTML would notice. ReportBuilder passes this through verbatim and the report footer
# renders it (see report-template.html's footer section). Written via temp file + mv for the same
# reason results.json is: this file is rewritten while the run is in progress.
# $1 = one of running | completed | timed-out | failed, $2 = BenchmarkRunner's exit code ("null" if
# it has not run yet).
write_run_metadata() {
  cat > "$OUT_DIR/run-metadata.json.tmp" << EOF
{
  "timestamp": "$RUN_TIMESTAMP",
  "gitRevision": "$GIT_REV",
  "gitDirty": $GIT_DIRTY,
  "javaVersion": "$JAVA_VERSION",
  "os": "$OS_DESC",
  "hostname": "$HOST_NAME",
  "cpuModel": "$CPU_MODEL",
  "memoryTotalKb": $MEM_TOTAL_KB,
  "z3Version": "$Z3_VERSION",
  "useVersion": "$USE_VERSION",
  "repeats": $REPEATS,
  "warmups": $WARMUPS,
  "soilTimeoutSeconds": $SOIL_TIMEOUT,
  "overallTimeoutSeconds": $OVERALL_TIMEOUT,
  "safety": "$SAFETY",
  "benchmarkStatus": "$1",
  "benchmarkExitCode": $2
}
EOF
  mv "$OUT_DIR/run-metadata.json.tmp" "$OUT_DIR/run-metadata.json"
}
write_run_metadata running null

BENCHMARK_TIMED_OUT=0
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
    BENCHMARK_TIMED_OUT=1
    write_run_metadata timed-out "$BENCHMARK_EXIT"
    echo "  WARNING: BenchmarkRunner was killed after exceeding the ${OVERALL_TIMEOUT}s ceiling -- likely a" \
         "genuinely hung solve (see the last '=== ... ===' / cell line above for roughly where). Continuing" \
         "with whichever cells were written to results.json before the kill; re-run with a larger ceiling or" \
         "investigate that specific example/solver directly. The report will be marked INCOMPLETE and this" \
         "script will exit $BENCHMARK_EXIT." >&2
  elif [ "$BENCHMARK_EXIT" -ne 0 ]; then
    write_run_metadata failed "$BENCHMARK_EXIT"
    echo "  BenchmarkRunner failed (exit $BENCHMARK_EXIT), not a timeout -- aborting." >&2
    exit "$BENCHMARK_EXIT"
  else
    write_run_metadata completed 0
  fi
else
  echo "[3/5] Running BenchmarkRunner (finding, $REPEATS repeats + $WARMUPS warmups per example," \
       "safety=off: no outer timeout, no watchdog -- original, unprotected timing)..."
  java -Djava.library.path="$KK_DIR/vendored-solvers/x64" -cp "$CP" org.tzi.msc.benchmark.BenchmarkRunner \
      "$EXAMPLES_DIR" "$OUT_DIR/results.json" "$REPEATS" "$WARMUPS" false
  write_run_metadata completed 0
fi

echo "[4/5] Running SoilValidationRunner (validating, real use-gui.jar subprocess)..."
if [ -f "$USE_GUI_JAR" ]; then
  java -cp "$CP" org.tzi.msc.benchmark.SoilValidationRunner \
      "$EXAMPLES_DIR" "$USE_GUI_JAR" "$OUT_DIR/soil-validation-results.json" "$SOIL_TIMEOUT"
else
  echo "  SKIPPED: $USE_GUI_JAR not found -- build it first with" \
       "\`mvn -f $USE_MSC2026_DIR/pom.xml -pl use-gui -am package -DskipTests', then re-run." >&2
  echo "[]" > "$OUT_DIR/soil-validation-results.json"
fi

echo "[5/5] Building report..."
java -cp "$CP" org.tzi.msc.benchmark.ReportBuilder \
    "$BENCHMARK_DIR/src/main/resources/manifest.json" "$OUT_DIR/results.json" \
    "$OUT_DIR/report.html" "$OUT_DIR/soil-validation-results.json" "$OUT_DIR/run-metadata.json" \
    "$USE_MSC2026_DIR/docs/modelvalidator-feature-matrix.json"

echo
echo "  results:         $OUT_DIR/results.json"
echo "  SOIL validation: $OUT_DIR/soil-validation-results.json"
echo "  run metadata:    $OUT_DIR/run-metadata.json"
echo "  report:          $OUT_DIR/report.html"
echo
if [ "$BENCHMARK_TIMED_OUT" -ne 0 ]; then
  # A killed run must not look like a successful one from the outside either: a CI job or a wrapper
  # script that only checks $? would otherwise archive a partial report as a finished result.
  echo "INCOMPLETE: BenchmarkRunner was killed at the ${OVERALL_TIMEOUT}s ceiling (exit $BENCHMARK_EXIT)." \
       "results.json holds only the cells finished before the kill, run-metadata.json records" \
       "benchmarkStatus=timed-out, and the report footer says so." >&2
  exit "$BENCHMARK_EXIT"
fi
echo "Done."
