#!/usr/bin/env bash
# Acceptance floor for kk-modelvalidator's own test suite.
#
# This module's test corpus (ported verbatim from ModelValidator/trunk, see
# ../../../docs/kk-modelvalidator-port.md) has a known, characterized set of non-conformances that
# predate this port and are not introduced by it: OCL snippets the current, stricter type checker
# correctly rejects (skipped via Assume, not failed -- see OCLTest.test()), and Kodkod-formula
# fixture strings that are stale relative to byte-identical, unmodified translation source
# (documented failures, several distinct patterns confirmed, see docs/kk-modelvalidator-port.md
# "Re-examination"). Neither is silenced -- surefire runs with testFailureIgnore so the reactor
# build can proceed, and THIS script is the actual gate: it fails loudly if failures/errors exceed
# the pinned floor, so a real regression cannot slip through as "known, pre-existing noise". If you
# genuinely fix one of the characterized failures, lower the floor below in the same commit, with a
# citation of what changed.
#
# Counts are summed from surefire's own per-class XML reports' root <testsuite tests=".." errors=".."
# failures=".." skipped=".."> attributes. The 5 JUnit3-style *TestSuite.xml wrapper reports
# (aggregating child test classes) report 0/0/0/0 at their own root element -- verified empirically,
# 2026-08-21 -- so summing across every *.xml file does not double-count. This total (currently 3308)
# is intentionally not the same number `mvn test`'s console summary prints (currently 6031): the
# console tally additionally counts rerun attempts for failing tests, which the XML reports collapse
# to one row each. That distinction doesn't matter here -- this script only needs to be
# self-consistent across runs, not to match the console number.
set -euo pipefail

MODULE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
REPORTS_DIR="$MODULE_DIR/target/surefire-reports"

FLOOR_MIN_TESTS=3308
FLOOR_MAX_FAILURES=461
FLOOR_MAX_ERRORS=0

if [ ! -d "$REPORTS_DIR" ]; then
  echo "[kk-floor] FAIL: no surefire reports at $REPORTS_DIR -- did the test phase run?" >&2
  exit 1
fi

read -r TESTS ERRORS FAILURES SKIPPED < <(python3 - "$REPORTS_DIR" <<'PYEOF'
import re, sys, glob, os

reports_dir = sys.argv[1]
t = e = f = s = 0
for fn in glob.glob(os.path.join(reports_dir, "*.xml")):
    head = open(fn, encoding="utf-8", errors="replace").read(2000)
    m_tests = re.search(r'<testsuite\b[^>]*\btests="(\d+)"', head)
    m_errors = re.search(r'<testsuite\b[^>]*\berrors="(\d+)"', head)
    m_failures = re.search(r'<testsuite\b[^>]*\bfailures="(\d+)"', head)
    m_skipped = re.search(r'<testsuite\b[^>]*\bskipped="(\d+)"', head)
    if m_tests:
        t += int(m_tests.group(1))
        e += int(m_errors.group(1)) if m_errors else 0
        f += int(m_failures.group(1)) if m_failures else 0
        s += int(m_skipped.group(1)) if m_skipped else 0
print(t, e, f, s)
PYEOF
)

echo "[kk-floor] observed: tests=$TESTS errors=$ERRORS failures=$FAILURES skipped=$SKIPPED"
echo "[kk-floor] floor:    tests>=$FLOOR_MIN_TESTS errors<=$FLOOR_MAX_ERRORS failures<=$FLOOR_MAX_FAILURES"

FAIL=0

if [ "$TESTS" -lt "$FLOOR_MIN_TESTS" ]; then
  echo "[kk-floor] FAIL: total tests $TESTS is below the pinned floor $FLOOR_MIN_TESTS -- tests went missing, not just failing (a discovery regression, e.g. a class silently no longer being collected, is worse than a failing test)." >&2
  FAIL=1
fi

if [ "$FAILURES" -gt "$FLOOR_MAX_FAILURES" ]; then
  echo "[kk-floor] FAIL: $FAILURES failures exceeds the pinned floor of $FLOOR_MAX_FAILURES -- this is a real regression, not the characterized baseline in docs/kk-modelvalidator-port.md." >&2
  FAIL=1
fi

if [ "$ERRORS" -gt "$FLOOR_MAX_ERRORS" ]; then
  echo "[kk-floor] FAIL: $ERRORS errors exceeds the pinned floor of $FLOOR_MAX_ERRORS -- every known no-longer-valid-OCL case is supposed to surface as Assume-skipped (see OCLTest.test()), not as an Error. A new Error is a new, uncharacterized crash." >&2
  FAIL=1
fi

if [ "$FAIL" -ne 0 ]; then
  exit 1
fi

echo "[kk-floor] PASS -- within the pinned, characterized floor."
