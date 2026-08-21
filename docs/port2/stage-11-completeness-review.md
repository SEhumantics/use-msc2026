# S11 — closing out the U-types/SBoolean completeness review

**Role: Record.** Written 2026-08-21 on branch `port-uncertainty-2`. This document closes out a
completeness review of the Uncertainty Types (`UReal`/`UInteger`/`UBoolean`/`UString`) and Subjective
Boolean (`SBoolean`) port that the user commissioned via `/goal` on 2026-08-21. The review compared
the current implementation against `.git/reference-repositories/uncertainty/` (the original fork) and
against this project's own `docs/port2/` audit trail — the accumulated record of every prior stage's
findings and fixes. It ran as seven prior tasks plus this closing one; every fix and test it produced
is already committed, in order, on this branch. This document adds nothing new to the code — it runs
the acceptance gate one final time and records what the review did and did not establish.

---

## 1. Findings

### Finding 1 — the "delete if dangling" cuts were all correct

An independent method-level diff of all five vendored datatype classes (`UBoolean`, `UInteger`,
`UReal`, `UString`, `SBoolean`) against the old fork found that every method cut during porting was
already unreachable from the OCL grammar in the old fork too — none of the cuts removed anything that
had a live grammar path this project's standing "delete if dangling" rule would have required keeping.
Nothing needed restoring. `UUnlimitedNatural` (what the user refers to as "UUnlimitedInteger") stays
excluded from the port per the user's 2026-08-21 confirmation, consistent with the "do not add"
determination already recorded in `docs/port2/stage-03-scope.md` §3/§5.

No commit — this is a negative finding (nothing to restore), stated here for the record.

### Finding 2 — broken `Cloneable` contract on three classes, fixed

`UBoolean`, `UInteger` and `UReal` all declared `implements Cloneable` with no working `clone()`
method backing it — the `clone()` implementation was dropped during porting, correctly, since it was
itself dangling (no grammar path called it), but the `Cloneable` interface declaration on the class
should have been dropped in the same pass and was not. A class that implements `Cloneable` without
overriding `clone()` inherits `Object.clone()`'s protected, `CloneNotSupportedException`-throwing
behaviour behind a public marker interface that promises the opposite.

Fixed by commit `d50a4a9c`.

### Finding 3 — an undocumented (but legitimate) SBoolean cut, now documented

`SBoolean.union`, both `weightedUnion` overloads, and the binary `ccFusion(SBoolean)` overload were
dropped during porting without an audit-trail note recording the cut, unlike their six sibling fusion
wrappers (`minimumFusion`, `majorityFusion`, `averageFusion`, `cumulativeFusion`,
`epistemicCumulativeFusion`, `weightedFusion`), which were removed with a documented rationale. The
cut itself was legitimate — same "ungrammared, unreachable" class as the documented six — the gap was
purely in the audit trail.

Documented by commits `7bc15154` (the original note) and `09dea22d` (a numbering-consistency fix
caught by task review: the note's bullet numbering and intro count in `SBoolean.java`'s header comment
had drifted out of sync with the sibling notes already present).

### Finding 4 — the 8 SBoolean fusion operators (+ discount) had shape coverage but no value-correctness coverage; now closed, and it found a real defect

Prior to this review, `SBoolean`'s fusion operators (`minimumBeliefFusion`, `majorityBeliefFusion`,
`averageBeliefFusion`, `aleatoryCumulativeBeliefFusion`, `epistemicCumulativeBeliefFusion`,
`weightedBeliefFusion`, `beliefConstraintFusion`, `consensusAndCompromiseFusion`) and `discount` were
exercised only for shape — that a call type-checks, dispatches, and returns something of the right
declared type — never for whether the returned belief/disbelief/uncertainty/base-rate values were
numerically correct against the operators' own documented formulas. Closed by 20 new tests across five
commits, all in the new `SBooleanFusionValueTest.java`:

- `7d071d37` — value-correctness coverage for `minimumBeliefFusion`/`majorityBeliefFusion`
- `adf260e6` — value-correctness coverage for `discount`
- `f88ccdb6` — value-correctness coverage for `beliefConstraintFusion`
- `904e4576` — value-correctness coverage for `averageBeliefFusion`/`aleatoryCumulativeBeliefFusion`/
  `epistemicCumulativeBeliefFusion`/`weightedBeliefFusion`, **and a real production defect fix**
- `62b3ea7c` — hazard and degenerate-case coverage for `consensusAndCompromiseFusion`

Commit `904e4576` also fixes a real production defect this test-writing found, in
`weightedFusion`'s all-vacuous branch: the method's own comment documents that branch as falling back
to a "plain average" of the receiver's base rate against every other opinion's base rate, but the
shipped code only ever averaged the receiver's own base rate, dividing by the full opinion count
regardless of how many opinions actually contributed — silently dropping every other opinion's base
rate from the average. For three opinions with base rates `{0.5, 0.3, 0.4}`, the mathematically
correct plain average is `0.4`; the shipped (pre-fix) behaviour computed `0.1667`. The fix and its
regression test are both in `904e4576`.

---

## 2. The acceptance gate — both profiles, real output

Run from the repository root, per this review's own instructions (the `cd use-core` below scopes
each hand-typed `mvn verify` to that one module only; it does not cover `use-gui`. The full-reactor
evidence, covering both `use-core` and `use-gui`, is §2.3's gate-script run below):

```
cd use-core && mvn verify
```

and separately:

```
cd use-core && mvn verify -Pupstream-oracle
```

Both completed with `BUILD SUCCESS`. The floor check embedded in the build (`UpstreamOracleFloor`,
run at the Maven `verify` phase) printed an unqualified `PASS` for the correct mode in both runs, on
disk in each module's `target/upstream-oracle-floor.receipt`.

A note on terminology, for anyone comparing this against `docs/port2/harness-contract.md` §0.1: that
section documents `scripts/upstream-oracle-gate.sh` as *the* committed acceptance gate (which prints a
`[gate]`-prefixed banner) and treats a hand-typed `mvn verify -Pupstream-oracle` as a thing to be
wary of automating around. This review's own Step 1 instructions specified the two hand-typed `mvn
verify` invocations shown above, run directly inside `use-core`, not the wrapper script — so what is
summarized below is genuinely `[floor] PASS` (the in-build floor check's own banner), not `[gate] PASS`
(the wrapper script's banner). The two are not the same invocation; see §3 below for what that means
this review did not attempt.

### 2.1 `mvn verify` (default profile)

`BUILD SUCCESS`, total time 01:56 min, finished 2026-08-21T05:59:23+07:00.

- Surefire (unit): **414 tests, 0 failures, 0 errors, 0 skipped** — 81 classes / 414 methods, both
  above the pinned floor (70 classes / 393 methods).
- Failsafe (integration): **1 test, 0 failures, 0 errors** (`OCLExpressionIT`).
- `[floor] PASS — use-core met every pinned floor in DEFAULT mode.`

### 2.2 `mvn verify -Pupstream-oracle` (oracle profile)

`BUILD SUCCESS`, total time 02:01 min, finished 2026-08-21T06:01:30+07:00.

- Surefire (unit): **685 methods / 1273 executions, 0 failures, 0 errors, 0 skipped** — 114 classes,
  above the pinned floor (103 classes / 664 methods). Executions exceed methods because several
  legacy JUnit 3 suites run both via their `AllTests` wrapper and directly.
- Failsafe (integration): **1 test, 0 failures, 0 errors** (`OCLExpressionIT`).
- `[floor] PASS — use-core met every pinned floor in ORACLE mode.`
- Vintage-only sentinel `org.tzi.use.parser.USECompilerTest`: collected under this profile (it is
  `absent` under the default profile in §2.1, as expected).

*(Through 2026-08-21 this section pasted the full, untrimmed console output of both runs — every
`[INFO] Running ...` / `Tests run: ...` line, several thousand lines each. It was replaced with the
summary above to keep this document reviewable; nothing it recorded is lost, since both commands are
reproducible verbatim from §2's two command blocks, and the original transcript remains in this
file's git history for anyone who needs it.)*

### 2.3 Gate script confirmation

Section 2.1/2.2 above quote raw, hand-typed `mvn verify` output. Per
`docs/port2/harness-contract.md` §0.1, "THE GATE IS A SCRIPT" — the
project's actual acceptance evidence is `scripts/upstream-oracle-gate.sh`'s
own `[gate] PASS` banner, not a hand-typed `mvn` invocation's `[floor] PASS`
alone. A task review of this closeout caught that gap; this subsection
closes it without removing the evidence above, which stays as real,
useful context (and the `[floor] PASS` lines it captured reach the same
verdict against the same pinned floors as the gate script's own run below —
not byte-identical output, since separate runs carry their own freshness
timestamps and stale-ignored counts (e.g. §2.1 above shows
`stale-ignored=47` for `use-core` surefire where §2.3 below shows
`stale-ignored=0` for the same classes/methods counts), just the same
PASS/floor numbers, wrapped in the script's additional freshness/tamper/
partial-reactor checks).

```
$ bash scripts/upstream-oracle-gate.sh both
[gate] =================================================================
[gate] upstream-oracle acceptance gate — mode: both
[gate] reactor root: /home/xoruser/msc-4/use-msc2026
[gate] profile id (hard-coded here, not typed): upstream-oracle
[gate] git status --porcelain BEFORE:
[gate]   ?? docs/superpowers/
[gate]   (nothing above == clean)
[gate] =================================================================

[gate] ----- default : expecting mode DEFAULT in every module -----
[gate] mvn -q clean
[gate] mvn -B verify -Djava.awt.headless=true
[gate] mvn EXIT=0, log: /tmp/use-upstream-oracle-gate/default.log (7539 lines)
[gate] the floor's own words for default:
[gate]   [floor] initialize: requested profiles (none), declared in this reactor [upstream-oracle], allow-profiles (-Duse.floor.allowProfiles) (none)
[gate]   [floor] wrote freshness stamp /home/xoruser/msc-4/use-msc2026/use-core/target/upstream-oracle-floor.stamp
[gate]   [floor] ===== upstream-oracle floor check: use-core =====
[gate]   [floor] requested profiles (reactor-wide, from the command line): (none)
[gate]   [floor] this module's upstream-oracle profile effective: false
[gate]   [floor] mode: DEFAULT
[gate]   [floor] allow-profiles (-Duse.floor.allowProfiles): (none)
[gate]   [floor] reactor: FULL (no -pl/--projects, no -rf/--resume-from)
[gate]   [floor] freshness stamp: 2026-08-20T23:13:00.964Z — reports older than this are stale and are NOT counted
[gate]   [floor] surefire  use-core  classes=81  (floor 70 )  methods=414  (floor 393 )  executions=414  failures=0 errors=0 skipped=0 stale-ignored=0
[gate]   [floor] failsafe  use-core  classes=1   (floor 1  )  methods=1    (floor 1   )  executions=1    failures=0 errors=0 skipped=0 stale-ignored=0
[gate]   [floor] vintage-only sentinel org.tzi.use.parser.USECompilerTest: absent
[gate]   [floor] wrote receipt /home/xoruser/msc-4/use-msc2026/use-core/target/upstream-oracle-floor.receipt (verdict=PASS)
[gate]   [floor] PASS — use-core met every pinned floor in DEFAULT mode.
[gate]   [floor] initialize: requested profiles (none), declared in this reactor [upstream-oracle], allow-profiles (-Duse.floor.allowProfiles) (none)
[gate]   [floor] wrote freshness stamp /home/xoruser/msc-4/use-msc2026/use-gui/target/upstream-oracle-floor.stamp
[gate]   [floor] ===== upstream-oracle floor check: use-gui =====
[gate]   [floor] requested profiles (reactor-wide, from the command line): (none)
[gate]   [floor] this module's upstream-oracle profile effective: false
[gate]   [floor] mode: DEFAULT
[gate]   [floor] allow-profiles (-Duse.floor.allowProfiles): (none)
[gate]   [floor] reactor: FULL (no -pl/--projects, no -rf/--resume-from)
[gate]   [floor] freshness stamp: 2026-08-20T23:14:54.583Z — reports older than this are stale and are NOT counted
[gate]   [floor] surefire  use-gui   classes=1   (floor 1  )  methods=1    (floor 1   )  executions=1    failures=0 errors=0 skipped=0 stale-ignored=0
[gate]   [floor] failsafe  use-gui   classes=1   (floor 1  )  methods=129  (floor 129 )  executions=129  failures=0 errors=0 skipped=0 stale-ignored=0
[gate]   [floor] vintage-only sentinel org.tzi.use.gui.views.diagrams.util.DirectedLineTest: absent
[gate]   [floor] wrote receipt /home/xoruser/msc-4/use-msc2026/use-gui/target/upstream-oracle-floor.receipt (verdict=PASS)
[gate]   [floor] PASS — use-gui met every pinned floor in DEFAULT mode.

[gate] ----- oracle : expecting mode ORACLE in every module -----
[gate] mvn -q clean
[gate] mvn -B verify -Djava.awt.headless=true -Pupstream-oracle
[gate] mvn EXIT=0, log: /tmp/use-upstream-oracle-gate/oracle.log (7867 lines)
[gate] the floor's own words for oracle:
[gate]   [floor] initialize: requested profiles [upstream-oracle], declared in this reactor [upstream-oracle], allow-profiles (-Duse.floor.allowProfiles) (none)
[gate]   [floor] wrote freshness stamp /home/xoruser/msc-4/use-msc2026/use-core/target/upstream-oracle-floor.stamp
[gate]   [floor] ===== upstream-oracle floor check: use-core =====
[gate]   [floor] requested profiles (reactor-wide, from the command line): [upstream-oracle]
[gate]   [floor] this module's upstream-oracle profile effective: true
[gate]   [floor] mode: ORACLE
[gate]   [floor] allow-profiles (-Duse.floor.allowProfiles): (none)
[gate]   [floor] reactor: FULL (no -pl/--projects, no -rf/--resume-from)
[gate]   [floor] freshness stamp: 2026-08-20T23:15:33.136Z — reports older than this are stale and are NOT counted
[gate]   [floor] surefire  use-core  classes=114 (floor 103)  methods=685  (floor 664 )  executions=1273 failures=0 errors=0 skipped=0 stale-ignored=0
[gate]   [floor] failsafe  use-core  classes=1   (floor 1  )  methods=1    (floor 1   )  executions=1    failures=0 errors=0 skipped=0 stale-ignored=0
[gate]   [floor] vintage-only sentinel org.tzi.use.parser.USECompilerTest: collected
[gate]   [floor] wrote receipt /home/xoruser/msc-4/use-msc2026/use-core/target/upstream-oracle-floor.receipt (verdict=PASS)
[gate]   [floor] PASS — use-core met every pinned floor in ORACLE mode.
[gate]   [floor] initialize: requested profiles [upstream-oracle], declared in this reactor [upstream-oracle], allow-profiles (-Duse.floor.allowProfiles) (none)
[gate]   [floor] wrote freshness stamp /home/xoruser/msc-4/use-msc2026/use-gui/target/upstream-oracle-floor.stamp
[gate]   [floor] ===== upstream-oracle floor check: use-gui =====
[gate]   [floor] requested profiles (reactor-wide, from the command line): [upstream-oracle]
[gate]   [floor] this module's upstream-oracle profile effective: true
[gate]   [floor] mode: ORACLE
[gate]   [floor] allow-profiles (-Duse.floor.allowProfiles): (none)
[gate]   [floor] reactor: FULL (no -pl/--projects, no -rf/--resume-from)
[gate]   [floor] freshness stamp: 2026-08-20T23:17:34.987Z — reports older than this are stale and are NOT counted
[gate]   [floor] surefire  use-gui   classes=8   (floor 8  )  methods=17   (floor 17  )  executions=17   failures=0 errors=0 skipped=0 stale-ignored=0
[gate]   [floor] failsafe  use-gui   classes=1   (floor 1  )  methods=129  (floor 129 )  executions=129  failures=0 errors=0 skipped=0 stale-ignored=0
[gate]   [floor] vintage-only sentinel org.tzi.use.gui.views.diagrams.util.DirectedLineTest: collected
[gate]   [floor] wrote receipt /home/xoruser/msc-4/use-msc2026/use-gui/target/upstream-oracle-floor.receipt (verdict=PASS)
[gate]   [floor] PASS — use-gui met every pinned floor in ORACLE mode.

[gate] =================================================================
[gate] git status --porcelain AFTER:
[gate]   ?? docs/superpowers/
[gate]   (nothing above == clean; report anything you did not write, never commit it)
[gate] PASS — mode 'both': every check above held.
[gate] =================================================================
```

This is the project's canonical acceptance evidence for this review:
`[gate] PASS — mode 'both': every check above held.`

---

## 3. What this review did not re-verify

This review's scope was restoring and fixing the *ported types themselves* — `UReal`, `UInteger`,
`UBoolean`, `UString`, `SBoolean` — against the fork and the project's own audit trail. It was not a
re-examination of the differential test harness's own measurement fidelity or of the Maven build
infrastructure that runs the acceptance gate. Both of those are pre-existing, previously-identified,
harness/build-infrastructure-level concerns, and this review left them exactly as it found them:

- **The differential harness's own known-open items**, as recorded in
  `docs/port2/harness-contract.md`:
  - **D-29** (open, MAJOR) — the gate is not satisfiable by fidelity alone: a perfect port reaches
    `isStagePass(1, none())` on only 74 of 285 operations; the remaining 211 are refused by clause 3
    (119, by design) or clause 2 (92, for `BOTH_THREW`/`HARNESS_ERROR`/`UNMEASURABLE` rows a faithful
    port cannot avoid), so on those 92 an infidelity can change the rows and counts without changing
    the pass bit.
  - **D-30** (open, MAJOR) — input-domain coverage is unmeasured: `distinctReferenceValues()` measures
    the codomain reached, but its dual (how much of the input domain was reached) is computed nowhere,
    published nowhere, and gated nowhere. Decision H14 (2026-08-17) called for building this measure;
    it remains unimplemented (design in `docs/port2/h14-coverage-design.md`).
  - **D-52** (open, MAJOR) — the "escape hatch" for stating a ported value's observed Java type moved
    from a `String` parameter to an `Object` whose class the harness reads via
    `getClass().getName()`; the harness believes whatever object it is handed, which is not checkable
    by the harness itself, and drives the type-mismatch count to exactly 0 in a case measured to
    contain a real 401-row, 9-operation wrong-class defect.

  These three are named in the review's brief as the harness's own open items; they are not
  re-examined here.

- **The Maven-gate hardening items**, as recorded in `docs/port2/upstream-oracle-gate-round12.md`:
  - **H-01** (MINOR) — `scripts/upstream-oracle-gate.sh:189` contains unescaped backticks inside a
    double-quoted string, so bash *executes* `mvn test -Pupstream-oracle-typo` every time the G-04
    announce-count check fails; it cannot turn a red gate green, but it launches an unrequested Maven
    build mid-gate-run and replaces the intended diagnostic with a nested build log.
  - **H-02** (MINOR) — `docs/port2/gate-threat-model.md` §3's residual R-4 describes a route
    (`-Dexec.outputFile='${exec.outputFile}'`) that Maven 3.9.16 itself refuses outright with a
    recursive-expression-cycle error before any plugin runs; the route is documented as open but is in
    fact already closed.
  - **H-03** (MINOR) — an accident route in neither the gate's threat list nor its residual list: a
    background IDE Java language server sharing the same checkout can write into
    `use-core/target/classes` mid-build (observed once, produced a truncated `.class` file); `target/`
    is git-ignored, so the wrapper's `git status` check cannot see this class of interference.

  All three are pre-existing MINOR findings against the gate script and its threat model, not against
  the ported types. They are named here, per this review's brief, and not re-examined.

This review's own gate run (§2 above) did not attempt to re-measure D-29/D-30/D-52's open figures, and
did not re-*audit* `scripts/upstream-oracle-gate.sh` itself's own logic or threat model (per
`docs/port2/gate-threat-model.md`) — that deeper audit is genuinely out of scope here. A run of the
script did happen: §2.3 executed `bash scripts/upstream-oracle-gate.sh both` directly and captured its
`[gate] PASS` banner, in addition to §2.1/§2.2's hand-typed `mvn verify` / `mvn verify -Pupstream-oracle`
invocations run per this review's own Step 1 instructions. But §2.3's run only exercised the script's
normal, successful path; it did not probe the harder edge cases below — so H-01/H-02/H-03, which are
specifically about those edge cases, remain unconfirmed and unrefuted by anything run here.
