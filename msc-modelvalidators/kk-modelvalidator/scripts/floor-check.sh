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
# 2026-08-21 -- so summing across every *.xml file does not double-count. This total (currently 3321)
# is intentionally not the same number `mvn test`'s console summary prints (currently 6045): the
# console tally additionally counts rerun attempts for failing tests, which the XML reports collapse
# to one row each. That distinction doesn't matter here -- this script only needs to be
# self-consistent across runs, not to match the console number.
#
# PER-TEST IDENTITY, not just an aggregate count: an aggregate ceiling alone lets a future change fix
# some of the known failures while introducing an equal-or-smaller number of NEW, uncharacterized
# failures elsewhere and still print PASS -- the count never moves, but the SET underneath it does.
# scripts/known-failing-tests.txt pins the exact (classname#testname) set; this script recomputes the
# current set the same way (scripts/canonical-failing.py) and diffs it against that file byte-for-byte,
# failing loudly on ANY difference -- an added test, a removed one, or a same-size swap alike. If you
# genuinely fix (or newly characterize) a failure, regenerate the baseline in the same commit:
#   python3 scripts/canonical-failing.py | sort > scripts/known-failing-tests.txt
# and say why in the commit message -- this script only checks that the set is INTENTIONAL, not that
# it is small.
set -euo pipefail

MODULE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
REPORTS_DIR="$MODULE_DIR/target/surefire-reports"
BASELINE_FILE="$MODULE_DIR/scripts/known-failing-tests.txt"

# Bumped 2026-09-02 from 3311/461 to 3321/462: two new committed test classes exercising real,
# non-behavioral fixes/reproductions land in the same commit as this floor change --
# KodkodModelValidatorErrorReportingTest (2 tests, 0 failures: KodkodModelValidator#validationError()
# error-reporting fix) and DispatchBugProbeTest (8 tests, 1 deliberate failure:
# skipLevelInheritedOverrideShouldBeSatisfiable, the reproduction backing the
# ocl.operation-polymorphic-override "known-defect" row in docs/modelvalidator-feature-matrix.json).
# Neither the fix nor the reproduction changes kk-modelvalidator's actual solving/transformation
# semantics -- see that commit's message. The original 461 failures are unchanged (see
# scripts/known-failing-tests.txt, still exactly the 9 classes documented above); this floor tracks
# ADDED evidence, not a regression.
FLOOR_MIN_TESTS=3321
FLOOR_MAX_FAILURES=462
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

if [ ! -f "$BASELINE_FILE" ]; then
  echo "[kk-floor] FAIL: no baseline file at $BASELINE_FILE -- did it get deleted or never committed?" >&2
  FAIL=1
else
  CURRENT_FAILING="$(python3 "$MODULE_DIR/scripts/canonical-failing.py" "$REPORTS_DIR" | sort)"
  BASELINE_FAILING="$(sort "$BASELINE_FILE")"

  if [ "$CURRENT_FAILING" != "$BASELINE_FAILING" ]; then
    echo "[kk-floor] FAIL: the exact failing (classname#testname) set differs from scripts/known-failing-tests.txt -- an aggregate count match alone is not enough: the identity of what's failing must match too, otherwise fixing some known failures while silently introducing new, uncharacterized ones could still pass this gate." >&2
    ADDED="$(comm -13 <(echo "$BASELINE_FAILING") <(echo "$CURRENT_FAILING"))"
    REMOVED="$(comm -23 <(echo "$BASELINE_FAILING") <(echo "$CURRENT_FAILING"))"
    if [ -n "$ADDED" ]; then
      echo "[kk-floor]   NEW (failing now, not in baseline -- investigate; if genuinely characterized, add to the baseline with a citation):" >&2
      echo "$ADDED" | sed 's/^/[kk-floor]     + /' >&2
    fi
    if [ -n "$REMOVED" ]; then
      echo "[kk-floor]   GONE (in baseline, not failing now -- likely fixed; remove from the baseline and lower FLOOR_MAX_FAILURES in the same commit, with a citation):" >&2
      echo "$REMOVED" | sed 's/^/[kk-floor]     - /' >&2
    fi
    FAIL=1
  fi
fi

if [ "$FAIL" -ne 0 ]; then
  exit 1
fi

echo "[kk-floor] PASS -- within the pinned, characterized floor, and the exact failing set matches scripts/known-failing-tests.txt."
