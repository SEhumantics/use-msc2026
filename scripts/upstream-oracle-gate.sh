#!/usr/bin/env bash
# =====================================================================================
# THE ACCEPTANCE GATE. This script IS the gate; a hand-typed `mvn ... -Pupstream-oracle`
# is not.
#
# WHY (defect F-02, docs/port2/upstream-oracle-floor-verification.md sec. 3.5). Maven only
# WARNS on an unknown -P id, and the floor checker cannot detect a request it never saw:
#
#     $ mvn -B verify -Pupstream-oracle-typo -Djava.awt.headless=true
#     [WARNING] The requested profile "upstream-oracle-typo" could not be activated ...
#     [floor] mode: DEFAULT
#     [floor] PASS — use-core met every pinned floor in DEFAULT mode.
#     [INFO] BUILD SUCCESS      EXIT=0
#
# Exit 0, the floor says PASS twice, and the 40 classes / 287 methods the profile exists to
# revive were never collected — with two [WARNING] lines in a 1487-line log as the only
# signal. That cannot be fixed inside the build, because "requested-but-not-effective"
# needs a request. It is fixed by removing the typo-able step from the operator: ONE
# committed invocation, with the profile id written down ONCE, on line PROFILE_ID below.
# A typo now fails to find this script instead of silently degrading the gate.
#
# WHAT THIS SCRIPT ADDS TO `mvn verify`, all of it outside Maven's reach:
#   1. the profile id is hard-coded here and nowhere else in an operator's command;
#   2. `could not be activated` anywhere in the log is a FAILURE, naming the profile —
#      so a typo, a settings.xml that swallows the profile, or a renamed profile block
#      all fail loudly instead of degrading to a green default run;
#   3. the floor checker must have reported the EXPECTED MODE and an unqualified PASS for
#      BOTH modules by name — which catches a partial reactor (F-03 prints PARTIAL, never
#      PASS) and catches -Dexec.outputFile / -Dexec.quietLogs hiding the [floor] lines;
#   4. each module's target/upstream-oracle-floor.receipt is verified ON DISK after Maven
#      has exited: present, newer than this run's start marker, and recording the expected
#      module, mode and verdict=PASS. No Maven property can reach a check that runs after
#      Maven has exited. This is the third of the three independent F-01 mechanisms;
#   5. `git status --porcelain` before and after, because another session shares this
#      checkout (ground rule 4), and a refusal to start while another Maven is live.
#
# USAGE
#     scripts/upstream-oracle-gate.sh                 # both acceptance commands (THE gate)
#     scripts/upstream-oracle-gate.sh default         # the vintage-free build only
#     scripts/upstream-oracle-gate.sh oracle          # the upstream-oracle build only
#     scripts/upstream-oracle-gate.sh both -o         # extra args are forwarded to Maven
#
# DO NOT pass -P. If you do, it is forwarded and check 2 or check 3 will fail the gate:
# that is deliberate, and it is what makes a mistyped id loud instead of silent.
#
# Exit 0 = the gate passed. Any non-zero = the gate did NOT pass; read the [gate] lines.
# =====================================================================================

set -u -o pipefail

# ---- THE SINGLE SOURCE OF TRUTH -----------------------------------------------------
PROFILE_ID='upstream-oracle'
MODULES=(use-core use-gui)
BASE_ARGS=(-B verify -Djava.awt.headless=true)
RECEIPT='target/upstream-oracle-floor.receipt'
# -------------------------------------------------------------------------------------

ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT" || exit 2
# OUTSIDE the reactor on purpose: this script runs `mvn -q clean`, which deletes every
# target/ in the tree, and the logs and start markers must survive it. Nothing is written
# into the tracked tree, so the gate cannot dirty `git status`. Override with GATE_LOG_DIR.
LOGDIR="${GATE_LOG_DIR:-${TMPDIR:-/tmp}/use-upstream-oracle-gate}"
mkdir -p "$LOGDIR" || exit 2

MODE="${1:-both}"
shift 2>/dev/null || true
EXTRA=("$@")

case "$MODE" in
  default|oracle|both) ;;
  *)
    echo "[gate] FATAL — unknown mode '$MODE'."
    echo "[gate] usage: scripts/upstream-oracle-gate.sh [default|oracle|both] [extra mvn args]"
    exit 2
    ;;
esac

fail_count=0
note() { echo "[gate] $*"; }
bad()  { echo "[gate] FAIL — $*"; fail_count=$((fail_count + 1)); }

if [ ${#EXTRA[@]} -gt 0 ]; then
  note "forwarding extra Maven arguments: ${EXTRA[*]}"
  for a in "${EXTRA[@]}"; do
    case "$a" in
      -P*|--activate-profiles*)
        note "NOTE: you passed a profile selector ($a). Profile selection is this script's job,"
        note "      not yours — that is defect F-02. It is forwarded, and if it names a profile"
        note "      Maven cannot activate, the gate will fail on it below."
        ;;
    esac
  done
fi

# ---- ground rule 4: nobody else may be holding the reactor ---------------------------
if pgrep -f '[c]lassworlds.launcher.Launcher' >/dev/null 2>&1; then
  echo "[gate] FATAL — another Maven is already running in this checkout:"
  pgrep -af '[c]lassworlds.launcher.Launcher'
  echo "[gate] The gate needs the reactor to itself. Refusing to start."
  exit 2
fi

echo "[gate] ================================================================="
echo "[gate] upstream-oracle acceptance gate — mode: $MODE"
echo "[gate] reactor root: $ROOT"
echo "[gate] profile id (hard-coded here, not typed): $PROFILE_ID"
echo "[gate] git status --porcelain BEFORE:"
git status --porcelain | sed 's/^/[gate]   /'
echo "[gate]   (nothing above == clean)"
echo "[gate] ================================================================="

# run_one <label> <expected-mode> [maven args...]
run_one() {
  local label="$1" expect="$2"; shift 2
  local log="$LOGDIR/$label.log"
  local marker="$LOGDIR/$label.start"

  echo
  echo "[gate] ----- $label : expecting mode $expect in every module -----"

  note "mvn -q clean"
  mvn -q clean >"$LOGDIR/$label.clean.log" 2>&1
  local cleanrc=$?
  if [ $cleanrc -ne 0 ]; then
    bad "$label: \`mvn -q clean\` exited $cleanrc; see $LOGDIR/$label.clean.log"
    return
  fi
  # The start marker is laid down AFTER the clean and BEFORE the build, so a receipt that is
  # not newer than it cannot have been written by this build.
  : > "$marker"

  note "mvn ${*}"
  mvn "$@" >"$log" 2>&1
  local rc=$?
  note "mvn EXIT=$rc, log: $log ($(wc -l <"$log") lines)"

  # --- 1. Maven itself -----------------------------------------------------------
  if [ $rc -ne 0 ]; then
    bad "$label: Maven exited $rc. Tail of the log:"
    tail -n 25 "$log" | sed 's/^/[gate]   /'
  fi
  if ! grep -q '^\[INFO\] BUILD SUCCESS' "$log"; then
    bad "$label: no 'BUILD SUCCESS' in the log."
  fi

  # --- 2. F-02: an unactivatable profile is a FAILURE, not a warning --------------
  if grep -q 'could not be activated' "$log"; then
    bad "$label: Maven could not activate a requested profile. THIS IS DEFECT F-02: without" \
        "this check the build is green, the floor prints PASS in DEFAULT mode, and the" \
        "revived upstream classes are silently uncollected. The offending line(s):"
    grep -n 'could not be activated' "$log" | sed 's/^/[gate]   /'
  fi

  # --- 3. the floor must have spoken, for BOTH modules, in the expected mode ------
  local m expected_pass n
  for m in "${MODULES[@]}"; do
    expected_pass="[floor] PASS — $m met every pinned floor in $expect mode."
    n=$(grep -F -c -- "$expected_pass" "$log")
    if [ "$n" -ne 1 ]; then
      bad "$label: expected exactly one line '$expected_pass' in the log, found $n." \
          "Every [floor] verdict line the log does have:"
      grep -nE '^\[floor\] (PASS|PARTIAL|FAIL|FATAL)' "$log" | sed 's/^/[gate]   /' \
        || echo "[gate]   (none at all — the floor check did not run: defect F-01)"
    fi
  done
  n=$(grep -cE '^\[floor\] ===== upstream-oracle floor check:' "$log")
  if [ "$n" -ne ${#MODULES[@]} ]; then
    bad "$label: the floor check announced itself $n time(s), expected ${#MODULES[@]}."
  fi
  if grep -qE '^\[floor\] (FAIL|FATAL|PARTIAL)' "$log"; then
    bad "$label: the floor reported FAIL, FATAL or PARTIAL:"
    grep -nE '^\[floor\] (FAIL|FATAL|PARTIAL)' "$log" | sed 's/^/[gate]   /'
  fi

  # --- 4. the receipts, on disk, after Maven has exited (F-01 mechanism 3) --------
  for m in "${MODULES[@]}"; do
    local r="$ROOT/$m/$RECEIPT"
    if [ ! -f "$r" ]; then
      bad "$label: no receipt at $r. The verify-phase floor check did not run to completion" \
          "in $m. A silenced exec binding leaves exactly this trace (F-01)."
      continue
    fi
    if [ ! "$r" -nt "$marker" ]; then
      bad "$label: the receipt $r is NOT newer than this run's start marker $marker — it is a" \
          "previous build's receipt, so this build's floor check did not write one."
      continue
    fi
    local want
    for want in "module=$m" "mode=$expect" "verdict=PASS" "partial-reactor=false"; do
      if ! grep -qxF -- "$want" "$r"; then
        bad "$label: receipt $r does not carry the line '$want'. It says:"
        sed 's/^/[gate]   /' "$r"
        break
      fi
    done
  done

  # --- what the run actually reported, for the record ----------------------------
  echo "[gate] the floor's own words for $label:"
  grep -E '^\[floor\]' "$log" | sed 's/^/[gate]   /'
}

case "$MODE" in
  default) run_one 'default' 'DEFAULT' "${BASE_ARGS[@]}" ${EXTRA[@]+"${EXTRA[@]}"} ;;
  oracle)  run_one 'oracle'  'ORACLE'  "${BASE_ARGS[@]}" "-P$PROFILE_ID" ${EXTRA[@]+"${EXTRA[@]}"} ;;
  both)
    run_one 'default' 'DEFAULT' "${BASE_ARGS[@]}" ${EXTRA[@]+"${EXTRA[@]}"}
    run_one 'oracle'  'ORACLE'  "${BASE_ARGS[@]}" "-P$PROFILE_ID" ${EXTRA[@]+"${EXTRA[@]}"}
    ;;
esac

echo
echo "[gate] ================================================================="
echo "[gate] git status --porcelain AFTER:"
git status --porcelain | sed 's/^/[gate]   /'
echo "[gate]   (nothing above == clean; report anything you did not write, never commit it)"
if [ $fail_count -eq 0 ]; then
  echo "[gate] PASS — mode '$MODE': every check above held."
  echo "[gate] ================================================================="
  exit 0
fi
echo "[gate] GATE FAILED — $fail_count check(s) failed in mode '$MODE'."
echo "[gate] Do NOT lower a floor, edit an upstream test, or weaken a check to clear this."
echo "[gate] See docs/port2/harness-contract.md sec. 0 and"
echo "[gate]     docs/port2/upstream-oracle-floor-verification.md."
echo "[gate] ================================================================="
exit 1
