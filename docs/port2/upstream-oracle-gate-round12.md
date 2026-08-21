# Independent refutation of round 12 — the five fixes, and the threat model's honesty

**Verdict: `SOUND_WITH_DOCUMENTED_LIMITS`.** No CRITICAL, no MAJOR. Three **MINOR**, one of them a
real code bug introduced by this round.

**G-01, G-02, G-04 and G-05 are all closed**, each reproduced from round 11's own command line and
each now red or recorded. **G-03's factual half is right**: the descriptor declares **22**, not 21
and not eight — parsed directly from the plugin jar. Every tree-borne breakage of rounds 10 and 11
still fails; no documentation page tells a stage to hand-type `-P`; **no green bypass exists that
`gate-threat-model.md` §3 does not list.**

22 Maven lifecycle runs, 4 wrapper invocations, ~20 `mvn -q clean`; no foreign modification observed
at any point (`pgrep`/`git status --porcelain` bracketed every run); the two `.tsv` goldens are
byte-identical; tree clean at the end. Commits under review: `5ff47092` (build) + `676a6ab6` (docs),
branch `port-uncertainty-2`. Every break below is a deliberate construction against the committed
tree, and every number below is pasted from a log produced during this round.

---

## 0. Summary

| id | sev | one line | § |
|---|---|---|---|
| **H-01** | MINOR | `scripts/upstream-oracle-gate.sh` contains unescaped backticks inside a double-quoted string, so bash *executes* `mvn test -Pupstream-oracle-typo` every time the G-04 announce-count check fails. New in `5ff47092`. It cannot turn a red gate green — but it launches an unrequested Maven inside a shared checkout mid-gate-run and replaces the diagnostic the check exists to print with a nested build log. | §4 |
| **H-02** | MINOR | `gate-threat-model.md` §3 R-4 describes a residual that does not exist on this reactor: `-Dexec.outputFile='${exec.outputFile}'` is refused by Maven 3.9.16 itself (`Detected the following recursive expression cycle`, exit 1, before any plugin runs). The route is closed, not open, on this Maven. | §5.2 |
| **H-03** | MINOR | An accident route in neither §1 nor §3 of the threat model: a background IDE Java language server sharing this checkout writes into `use-core/target/classes` mid-run. Observed once; gate went **red** (correct); `target/` is git-ignored so the wrapper's `git status` bracket cannot see this class of interference at all. | §5.3 |

Nothing else. I could not construct a **green** run that the wrapper accepted and that was not a
real gate, on any route, tree-borne or command-line.

**Status as re-verified against the current tree (this compression pass, 2026-08-21):** H-03 is
closed as a *documentation* gap — migrated into `gate-threat-model.md` as row **R-8**, with a
matching operator warning in `scripts/upstream-oracle-gate.sh`'s startup banner (added in commit
`92648f51`). **H-01 was found still present when this section was first re-checked** (commit
`92648f51` had not touched line 192), then **fixed in the same session**: the backticks in the
`bad "..."` call inside the initialize-guard check are now escaped (`` \`mvn test
-Pupstream-oracle-typo\` ``), matching the pattern line 141 already used correctly. Verified with a
minimal repro that the escaped form no longer triggers command substitution (§4 below still shows
the pre-fix construction for the record). H-02 is a fact about the installed Maven (3.9.16 refuses
the self-referential property), not about script text, and remains correct as written;
`gate-threat-model.md`'s own R-4 prose has not been updated to say so explicitly.

---

## 1. Both acceptance commands at HEAD — green, both modes, every floor at equality

`scripts/upstream-oracle-gate.sh both` → **exit 0**, both `default` and `oracle` runs, every one of
the eight floor cells (per-module × surefire/failsafe) met at **exact equality** against the pinned
floor, `failures=errors=skipped=0` throughout, 0 upstream test failures under the profile. No floor
moved: the `ORACLE`/`DEFAULT` maps in `scripts/UpstreamOracleFloor.java` are byte-identical to round
11's. Re-run as a closing control after every destructive row below (tree byte-identical to HEAD):
exit 0 again, same eight cells — the one run in between that failed is H-03 (§5.3), not hidden.
Harness controls (differential-harness regression suite, detection-power control, vintage-absence
via `dependency:list`) all unregressed. Full console reproductions live in `upstream-oracle-profile.md`
§5.2.6, not repeated here.

---

## 2. G-01, G-02, G-04, G-05 fixed; G-03 corrected — summary

Each was re-run as round 11's own command line, unchanged, against `676a6ab6`, with `mvn -q clean`
first and `git status --porcelain` clean before and after. Full per-defect mechanism, the injected
argv shapes tried against the parser, and the verbatim console output are tabulated in
`upstream-oracle-profile.md` §5.2.6 (not reproduced here to avoid duplication) and are not repeated
in this file.

| id | round 11 | round 12 |
|---|---|---|
| **G-01** (all 3 routes: `stamp`, `allowProfiles`, `effective`) | exit 0, `BUILD SUCCESS`, no receipt | exit 1, `BUILD FAILURE`, `FATAL` before dispatch |
| **G-02** (`exec.outputFile` diversion) | exit 0, `verdict=PASS`, 0 `TAMPERING` | exit 1, `BUILD FAILURE`, 1 `TAMPERING`, `verdict=FAIL` |
| **G-04** (`mvn test -Pupstream-oracle-typo`) | exit 0, `BUILD SUCCESS`, no floor check | exit 1, `BUILD FAILURE` at `initialize`, 82-line log |
| **G-05** (`allowProfiles` widening) | green, hatch echoed nowhere | green, hatch echoed 4×, flagged `<-- CHECK B2 WAS WIDENED BY THE COMMAND LINE` in log and both receipts |

I additionally fuzzed the argv parser with five further injection shapes of my own; all five
`FATAL`, exit 2, before anything acts. The poms emit all sixteen check options and all five stamp
options exactly once, so any injected token is a duplicate, an unknown name, a malformed token, or
`stamp`, and each is fatal before dispatch.

**G-03's factual half**: I parsed `exec-maven-plugin-3.5.0.jar`'s `plugin.xml` myself — the `exec`
goal declares **22** interpolatable parameters (20 `exec.*`, 2 unprefixed), all editable, none
read-only. **7 pinned + 8 detected (7 overlapping) = 8 handled, 14 neither** — the porter's
arithmetic, and it is right; round 11's prose said 21 while its own paste listed 22. Corrected in the
normative files (`upstream-oracle-profile.md` §5.2.1, `harness-contract.md` §0.1,
`upstream-oracle-floor-verification.md` F-01, `gate-threat-model.md` §3 R-3, the checker's header);
round 11's own report was left unaltered, correctly — a refutation report is a dated record.

**The asserting-method figures**: the brief instructed 198/464; the porter wrote 199/465 and flagged
the discrepancy. Re-measured from source (`@Test` counts across the six
cyclic-dependency/layered-architecture test classes): the porter is right — 198/464 were the figures
of the suite *before* round 11 added `UpstreamOracleGateWiringTest` and re-pinned the floors in the
same commit.

---

## 3. Accident routes still fail — every one of them

`mvn -q clean` first, pom restored with `git checkout --` after, tree clean after every restore.

| row | what was done | exit | what caught it |
|---|---|---|---|
| t1/t2 | `<profiles>` deleted from one pom, ran the other mode | 1 | `UpstreamOracleGateWiringTest`, 2 violations |
| t3 | `junit-vintage-engine` version pinned to a non-existent version, ORACLE | 1 | dependency resolution fails before any floor |
| t4 | stale oracle `target/` left in place, all reports `touch`ed newer than any stamp, DEFAULT with no clean | 0, correctly | `stale-ignored=47`/`7`; DEFAULT credited only its own 8/80 and 1/1 |
| t5 | `use-gui`'s `initialize` execution reverted to its pre-round-12 argv | 1 | 3 `WIRING` violations, one per missing token, naming G-04 |
| w1 | wrapper, mistyped `-P` forwarded | 1 | 10 gate checks failed |
| a10 | `-pl use-core -Pupstream-oracle` | 0 | `verdict=PARTIAL` on disk, never `PASS` |

Round 11's `r13` (a planted failing test + `-Dmaven.test.failure.ignore=true` → `verdict=FAIL`) was
not re-run this round: it requires planting a file under `use-core/src/test`, and the mechanism it
exercises (`failures`/`errors` read from the report XML, asserted `= 0`) is unchanged by inspection.
Carried forward as round 11's result.

---

## 4. H-01 (MINOR, fixed 2026-08-21) — the wrapper *ran* a Maven build inside one of its own error messages

`scripts/upstream-oracle-gate.sh`, new in `5ff47092`, currently around line 186-193:

```bash
  n=$(grep -cE '^\[floor\] initialize: requested profiles ' "$log")
  if [ "$n" -ne ${#MODULES[@]} ]; then
    bad "$label: the initialize-phase profile guard announced itself $n time(s), expected" \
        "${#MODULES[@]}. Without it, `mvn test -Pupstream-oracle-typo` is a green build with" \
        "no gate in it (defect G-04)."
  fi
```

The backticks are inside a **double-quoted** word, so bash performs command substitution: the string
`mvn test -Pupstream-oracle-typo` is not quoted prose, it is **executed** every time this branch
fires. Line ~141 in the same file gets this right (`` \`mvn -q clean\` `` — escaped); this one does
not. A minimal repro (swap the payload for `echo I-JUST-RAN-A-COMMAND` inside the same quoting shape)
confirms the substitution fires and its output lands mid-sentence in the printed diagnostic. It fired
three times in round 12's own suite (rows `w1`, `r3w`, and the transient failure of §5.3).

**What it is, and what it is not.** It **cannot** turn a red gate green — `bad` increments
`fail_count` whatever its arguments expand to, so the check still fails regardless. It **does**
launch an unrequested Maven inside a checkout the same script has just declared it needs to itself,
between the wrapper's log checks and its on-disk receipt checks, writing into `target/`; the damage
is bounded today only by coincidence (the G-04 fix makes `mvn test -Pupstream-oracle-typo` die at
`initialize` before writing anything), not by design. And it **destroys** the diagnostic the G-04
check exists to print — an operator reading `[gate] FAIL — … expected 2. Without it, [INFO] Scanning
for projects...` learns nothing about the actual failure.

**Fix (not applied — `.sh` file, out of scope for a documentation-only pass):** escape the two
backticks, exactly as the `mvn -q clean` message already does, or drop them. One character each, no
behaviour change intended or implied. **Verified against the current tree as of this compression
pass: still unfixed.**

---

## 5. Is `gate-threat-model.md` honest? Yes, with one over-statement (closed) and one omission (now closed)

I checked every defect id in the record against the file at the time of this round; nothing was
hidden. I also went looking for a **green** bypass not on that list and did not find one:
`-DskipTests`, `-Dtest=…`, `-Dmaven.test.failure.ignore=true`, `--fail-never`, `-pl`, `-rf`,
`-P'!upstream-oracle'`, `-q`, `MAVEN_ARGS`, and forwarding any of them through the wrapper are all
covered by an existing accident/residual row and were shown red in round 10, round 11, or this round.

### 5.2 H-02 — R-4 is not a residual on this reactor; Maven refuses it

`gate-threat-model.md` §3 R-4 says `-Dexec.outputFile='${exec.outputFile}'` "reads as unset, so no
TAMPERING violation". I ran it: Maven 3.9.16 rejects the command line before a single plugin runs
(`Detected the following recursive expression cycle in 'exec.outputFile'`, exit 1, 0 `[floor]` lines
anywhere, no receipts, and no file literally named `${exec.outputFile}` is written). The *conceptual*
limit R-4 states — any scheme inferring "unset" from an uninterpolated placeholder is blind to a
value equal to that placeholder — is real, but on this Maven it is unreachable, because the only
value with that property is the self-reference Maven refuses. Setting the property to some *other*
placeholder (`-Dexec.outputFile='${zz}'`) reads as SET and raises `TAMPERING` (§2, G-02 case).

This errs in the safe direction — the record claims a weakness it does not have — but the residual
list is normative prose and ground rule 1 binds it too. R-4 should say: *closed on Maven 3.9.16,
which refuses the self-referential property with a recursive-expression-cycle error; the limit is
stated for a future Maven that does not.*

### 5.3 H-03 — an accident route in neither §1 nor §3: a third writer in `target/`

The closing control run of the wrapper (tree byte-identical to HEAD, `git status --porcelain` empty
before and after, `pgrep` clean) went **red**: `use-core/target/classes/.../Op_sequence_indexOf.class`
had been written malformed during that build, after that build's own `mvn -q clean`. `rm -rf */target`
and an immediate re-run was green in both modes at equality. Most plausible cause: this checkout was
open in an editor whose Java language server was running (`redhat.java`, whose own output folder for
a Maven project is `target/classes`), racing surefire's forked JVM. `target/` is git-ignored, so the
wrapper's `git status` bracket cannot see this class of interference at all; the unsafe direction is
not obviously impossible — a *stale but well-formed* class written between `compile` and `test` would
let the suite pass against code that is not the tree's, and no floor/sentinel/receipt would notice,
since all of them count reports, not bytecode provenance.

**This finding is now recorded, not merely proposed**: `gate-threat-model.md` §3 carries it as row
**R-8**, and `scripts/upstream-oracle-gate.sh`'s startup banner prints the corresponding operator
warning (close or disable the IDE's Java language server before an acceptance run) — both added in
commit `92648f51`. No further action needed here; see `gate-threat-model.md` R-8 for the current
normative text.

---

## 6. Verdict

**`SOUND_WITH_DOCUMENTED_LIMITS`.** Three MINORs, none of which can produce a false green: H-01 is a
shell bug on a failure path (still present in the current tree, not yet fixed — see §4); H-02 is a
residual described as open that is in fact closed on this Maven; H-03 is an accident route that is
now recorded (`gate-threat-model.md` R-8) rather than unlisted.

**Can S3–S10 gate on `scripts/upstream-oracle-gate.sh`? Yes — within the stated threat model, with
the residuals of `gate-threat-model.md` §3 listed (R-8 included).**

The evidence is one-directional: the wrapper was green at HEAD twice, before and after everything
done to the tree, with all eight floor cells met at equality and the harness controls unmoved. It
went red on every one of the fourteen things done to make it lie — four command-line injections, two
log diversions, a mistyped id under three lifecycles, five tree mutations, a partial reactor, and a
widened escape hatch (which the wrapper still rejects, per the design of §2 of the threat model). On
the two routes deliberately left open it went red anyway, and the one run where the environment (not
the reviewer) corrupted a class file, it went red too. No single green wrapper run was produced that
was not a real gate.

Round 11 established that the wrapper was the only thing holding; round 12 repaired the in-build
binding where the defect was a bug (G-01, G-02), where the route was an accident (G-04), made the
hatch auditable (G-05), corrected the arithmetic (G-03) — and then stopped and wrote down what it did
not do. What makes the decision safe is not the fixes; it is the residual list in
`gate-threat-model.md` §3, which can be audited by the next reader in an afternoon. `git grep` over
all of `docs/port2/` (`spec-parts/` included) found no normative acceptance directive that hand-types
`-P` and no place treating `mvn test` as a gate; `harness-contract.md` §0.1 opens "THE GATE IS A
SCRIPT. Hand-typing `-P` is not the gate."

The condition on S3–S10 is unchanged and is the wrapper's own: run `scripts/upstream-oracle-gate.sh`
with **no forwarded arguments**, paste its `[gate]` block verbatim including both
`git status --porcelain` lines, quote **199 / 465** when the number is used to argue scrutiny and
**211 / 498** when it is used to argue collection, and never hand-type `-P`. A number produced by any
other invocation is not a gate result.
