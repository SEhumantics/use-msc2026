#!/usr/bin/env bash
# One command to run every automated test this thesis's claims rest on.
#
# Usage: scripts/run-tests.sh [--ours-only]
#   (no flag)     the whole reactor, including the VENDORED kk-modelvalidator test suite
#   --ours-only   only the modules authored here (unc-modelvalidator, benchmark)
#
# Exit codes: 0 = all selected tests passed; 1 = a test failed; 2 = the build itself failed.
#
# EXPECTED RESULTS on a clean checkout (Java 21, Maven 3.9):
#   unc-modelvalidator   ~1030 tests, 0 failures      (~2 min)
#   benchmark            115 tests,  0 failures       (~1 min)
#   kk-modelvalidator    6047 tests, ~462 FAILURES    (~3 min)
#
# The kk-modelvalidator failures are PRE-EXISTING and are NOT caused by this work: that module is a
# vendored port of USE's own Kodkod model validator, and docs/kk-modelvalidator-port.md accounts for
# them as test-fixture staleness and run-to-run non-determinism in the upstream suite. Use
# --ours-only for a clean pass/fail signal on the code authored here.
set -uo pipefail
cd "$(dirname "$0")/.."

# Fresh-clone reproducibility: set MSC_M2_REPO to an ISOLATED Maven repository and every Maven
# call below resolves from it (and stays offline). An empty or missing repository fails fast
# with the bootstrap command instead of silently falling back to ~/.m2. Requires Maven 3.9+.
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

MODULES="msc-modelvalidators"
if [[ "${1:-}" == "--ours-only" ]]; then
  echo "== running only the modules authored here =="
  # Build the dependencies WITHOUT running their tests, then test only our two modules.
  # Passing -am to `test` would also run the vendored kk-modelvalidator suite, which is
  # exactly what this flag exists to skip.
  ( cd msc-modelvalidators \
      && mvn -q -o -pl unc-modelvalidator,benchmark -am install -DskipTests \
      && mvn -o -pl unc-modelvalidator,benchmark test ) || exit 1
else
  echo "== running the full reactor (includes the vendored kk-modelvalidator suite) =="
  ( cd "$MODULES" && mvn -o test ) || {
    echo
    echo "NOTE: ~462 failures in kk-modelvalidator are expected and pre-existing."
    echo "      See docs/kk-modelvalidator-port.md. Re-run with --ours-only to check this work alone."
    exit 1
  }
fi
echo "== tests complete =="
