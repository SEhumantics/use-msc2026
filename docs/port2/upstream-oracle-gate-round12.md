# Independent refutation of round 12 — the five fixes, and the threat model's honesty

**Verdict: `SOUND_WITH_DOCUMENTED_LIMITS`.** No CRITICAL, no MAJOR. Three **MINOR**, one of them a
real code bug introduced by this round.

**G-01, G-02, G-04 and G-05 are all closed**, each reproduced from round 11's own command line and
each now red or recorded. **G-03's factual half is right, and the porter's correction of my
predecessor is right**: the descriptor declares **22**, not 21 and not eight — I parsed it myself.
Every tree-borne breakage of rounds 10 and 11 still fails. No documentation page tells a stage to
hand-type `-P`. **No green bypass exists that `gate-threat-model.md` §3 does not list.**

**I owned Maven for this round.** 22 lifecycle runs (18 `verify`, 2 `test`, 1 `compile`, plus the
wrapper's own), 4 wrapper invocations, 2 `dependency:list`, ~20 `mvn -q clean`.
`pgrep -f '[c]lassworlds.launcher.Launcher'` returned nothing before every row and before every
wrapper call; `git status --porcelain` was captured before and after every run by the drivers and by
the wrapper itself. **No foreign modification was observed at any point**; the two `.tsv` goldens are
byte-identical (`06455db4…`, `f8690574…`); the tree is clean at the end. Commits under review:
`5ff47092` (build) + `676a6ab6` (docs), branch `port-uncertainty-2`.

I did not build this gate. Every break below is my own construction, run against the committed tree,
and every number is pasted from a log I produced.

---

## 0. Summary

| id | sev | one line | § |
|---|---|---|---|
| **H-01** | MINOR | **`scripts/upstream-oracle-gate.sh:189` contains unescaped backticks inside a double-quoted string, so bash *executes* `mvn test -Pupstream-oracle-typo` every time the new G-04 announce-count check fails.** New in `5ff47092`. It cannot turn a red gate green — but it launches an unrequested Maven inside a shared checkout in the middle of a gate run, and it replaces the diagnostic the check exists to print with ~30 lines of a nested build log. | §4 |
| **H-02** | MINOR | `gate-threat-model.md` §3 **R-4 describes a residual that does not exist on this reactor**. `-Dexec.outputFile='${exec.outputFile}'` is refused by Maven 3.9.16 itself — `Detected the following recursive expression cycle`, exit 1, before any plugin runs. The route is closed, not open. Listing a residual too many is the safe direction, but ground rule 1 applies to the residual list as much as to the accident list. | §5.2 |
| **H-03** | MINOR | **An accident route that is in neither §1 nor §3: a background IDE Java language server sharing this checkout writes into `use-core/target/classes`.** Observed once, mid-suite: a truncated `Op_sequence_indexOf.class`, `git status` clean, no other Maven live. The gate went **red** (correct), and a hard clean plus re-run was green — but `target/` is git-ignored, so the wrapper's `git status` bracket cannot see this class of interference at all. | §5.3 |

Nothing else. In particular I could not construct a **green** run that the wrapper accepted and that
was not a real gate, on any route, tree-borne or command-line.

---

## 1. Both acceptance commands at HEAD — green, both modes, every floor at equality

`GATE_LOG_DIR=$S/gatelogs-head scripts/upstream-oracle-gate.sh both` → **exit 0**. Verbatim:

```
[gate] =================================================================
[gate] upstream-oracle acceptance gate — mode: both
[gate] reactor root: /home/xoruser/msc-4/use-msc2026
[gate] profile id (hard-coded here, not typed): upstream-oracle
[gate] git status --porcelain BEFORE:
[gate]   (nothing above == clean)
[gate] =================================================================

[gate] ----- default : expecting mode DEFAULT in every module -----
[gate] mvn -q clean
[gate] mvn -B verify -Djava.awt.headless=true
[gate] mvn EXIT=0, log: .../gatelogs-head/default.log (1495 lines)
[gate] the floor's own words for default:
[gate]   [floor] initialize: requested profiles (none), declared in this reactor [upstream-oracle], allow-profiles (-Duse.floor.allowProfiles) (none)
[gate]   [floor] wrote freshness stamp /home/xoruser/msc-4/use-msc2026/use-core/target/upstream-oracle-floor.stamp
[gate]   [floor] ===== upstream-oracle floor check: use-core =====
[gate]   [floor] requested profiles (reactor-wide, from the command line): (none)
[gate]   [floor] this module's upstream-oracle profile effective: false
[gate]   [floor] mode: DEFAULT
[gate]   [floor] allow-profiles (-Duse.floor.allowProfiles): (none)
[gate]   [floor] reactor: FULL (no -pl/--projects, no -rf/--resume-from)
[gate]   [floor] freshness stamp: 2026-08-17T18:24:50.706Z — reports older than this are stale and are NOT counted
[gate]   [floor] surefire  use-core  classes=8   (floor 8  )  methods=80   (floor 80  )  executions=80   failures=0 errors=0 skipped=0 stale-ignored=0
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
[gate]   [floor] freshness stamp: 2026-08-17T18:25:47.671Z — reports older than this are stale and are NOT counted
[gate]   [floor] surefire  use-gui   classes=1   (floor 1  )  methods=1    (floor 1   )  executions=1    failures=0 errors=0 skipped=0 stale-ignored=0
[gate]   [floor] failsafe  use-gui   classes=1   (floor 1  )  methods=129  (floor 129 )  executions=129  failures=0 errors=0 skipped=0 stale-ignored=0
[gate]   [floor] vintage-only sentinel org.tzi.use.gui.views.diagrams.util.DirectedLineTest: absent
[gate]   [floor] wrote receipt /home/xoruser/msc-4/use-msc2026/use-gui/target/upstream-oracle-floor.receipt (verdict=PASS)
[gate]   [floor] PASS — use-gui met every pinned floor in DEFAULT mode.

[gate] ----- oracle : expecting mode ORACLE in every module -----
[gate] mvn -q clean
[gate] mvn -B verify -Djava.awt.headless=true -Pupstream-oracle
[gate] mvn EXIT=0, log: .../gatelogs-head/oracle.log (1823 lines)
[gate] the floor's own words for oracle:
[gate]   [floor] initialize: requested profiles [upstream-oracle], declared in this reactor [upstream-oracle], allow-profiles (-Duse.floor.allowProfiles) (none)
[gate]   [floor] ===== upstream-oracle floor check: use-core =====
[gate]   [floor] this module's upstream-oracle profile effective: true
[gate]   [floor] mode: ORACLE
[gate]   [floor] allow-profiles (-Duse.floor.allowProfiles): (none)
[gate]   [floor] surefire  use-core  classes=41  (floor 41 )  methods=351  (floor 351 )  executions=939  failures=0 errors=0 skipped=0 stale-ignored=0
[gate]   [floor] failsafe  use-core  classes=1   (floor 1  )  methods=1    (floor 1   )  executions=1    failures=0 errors=0 skipped=0 stale-ignored=0
[gate]   [floor] vintage-only sentinel org.tzi.use.parser.USECompilerTest: collected
[gate]   [floor] wrote receipt .../use-core/target/upstream-oracle-floor.receipt (verdict=PASS)
[gate]   [floor] PASS — use-core met every pinned floor in ORACLE mode.
[gate]   [floor] surefire  use-gui   classes=8   (floor 8  )  methods=17   (floor 17  )  executions=17   failures=0 errors=0 skipped=0 stale-ignored=0
[gate]   [floor] failsafe  use-gui   classes=1   (floor 1  )  methods=129  (floor 129 )  executions=129  failures=0 errors=0 skipped=0 stale-ignored=0
[gate]   [floor] vintage-only sentinel org.tzi.use.gui.views.diagrams.util.DirectedLineTest: collected
[gate]   [floor] PASS — use-gui met every pinned floor in ORACLE mode.

[gate] =================================================================
[gate] git status --porcelain AFTER:
[gate]   (nothing above == clean; report anything you did not write, never commit it)
[gate] PASS — mode 'both': every check above held.
[gate] =================================================================
```

**11 classes / 211 methods** default, **51 / 498** oracle; every one of the eight cells met at
**equality**; `failures=errors=skipped=0` throughout; 0 upstream test failures under the profile
(`grep -cE 'FAILURE!|ERROR!' oracle.log` → `0`).

**No floor moved.** `git diff 2a1a4414..HEAD -- scripts/UpstreamOracleFloor.java | grep -c 'Floor('`
→ 0 changed floor literals; the `ORACLE`/`DEFAULT` maps are byte-identical to round 11's.

**Re-run as a closing control after all the destructive rows below**
(`gatelogs-final2`, tree byte-identical to HEAD): **exit 0** again, same eight cells, same equality.
The one run between them that failed is H-03 and is reported in §5.3, not hidden.

**Harness controls unregressed**, from the accepting default run's own output:

```
=== detection power: control (a perfect port) =====================
seed                 20260817
operations           285  (stage-shaped domains)
rows                 19083
measured rows        17199
agreement rows       17199
verdict tally        {AGREE=17199, BOTH_THREW=910, HARNESS_ERROR=883, UNMEASURABLE=91}
diverging operations 0   <- MUST be 0, or nothing below is attributable to a planted defect
stage passes         74 of 285  (isStagePass(1, none()))
```

```
Tests run: 10, Failures: 0, Errors: 0, Skipped: 0 -- in Unwritten-port invariant
Tests run:  9, Failures: 0, Errors: 0, Skipped: 0 -- in HistoricalOracle class-loader isolation
Tests run: 35, Failures: 0, Errors: 0, Skipped: 0 -- in Differential harness regressions
Tests run:  6, Failures: 0, Errors: 0, Skipped: 0 -- in Uncertainty differential smoke
Tests run:  7, Failures: 0, Errors: 0, Skipped: 0 -- in Detection power: subtle infidelities in a ported U-type
Tests run:  1, Failures: 0, Errors: 0, Skipped: 0 -- in org.tzi.use.uncertainty.gate.UpstreamOracleGateWiringTest
```

**Vintage absence proved by `dependency:list`, not by counts:**

```
[drv] DEFAULT vintage lines: 0
[drv] ORACLE vintage lines: 2
[drv]   [INFO]    org.junit.vintage:junit-vintage-engine:jar:5.7.0:test -- module org.junit.vintage.engine
```

---

## 2. Are G-01, G-02, G-04 and G-05 actually fixed? Yes — all four

Each row is round 11's own command line, unchanged, against `676a6ab6`. `mvn -q clean` first, tree
restored and `git status --porcelain` checked before and after each.

| id | the command | round 11 | round 12 | receipt |
|---|---|---|---|---|
| **G-01 route A** | `mvn -B verify -Djava.awt.headless=true '-Dexec.args=x --stamp=true'` | **exit 0, BUILD SUCCESS**, 4 `[floor]` lines, all "wrote freshness stamp", **no receipt** | **exit 1, BUILD FAILURE**, `FATAL` at the verify execution | none, both modules |
| **G-01 route B** | `… '-Duse.floor.allowProfiles=x --stamp=true'` | **exit 0, BUILD SUCCESS** | **exit 1, BUILD FAILURE at `initialize`** — a 79-line log | none |
| **G-01 route C** | `… '-Duse.upstreamOracle.effective=false --stamp=true'` | **exit 0, BUILD SUCCESS** | **exit 1, BUILD FAILURE** | none |
| **G-02** | `… '-Dexec.outputFile=/tmp/floorhide${z}.txt'` | **exit 0**, `verdict=PASS`, **0 `TAMPERING`** | **exit 1, BUILD FAILURE**, 1 `TAMPERING` | `verdict=FAIL` |
| **G-04** | `mvn -B test -Pupstream-oracle-typo -Djava.awt.headless=true` | **exit 0, BUILD SUCCESS**, no floor check, 1393-line log | **exit 1, BUILD FAILURE**, 82-line log | none |
| **G-05** | `mvn -B verify -Pupstream-oracle-typo -Duse.floor.allowProfiles=upstream-oracle-typo` | green, the hatch echoed **nowhere** | green, hatch echoed **4×** in the log and in **both** receipts, flagged | `allow-profiles=[upstream-oracle-typo]  <-- CHECK B2 WAS WIDENED BY THE COMMAND LINE` |

### 2.1 G-01 — the ordering is fixed *structurally*, and I could not get round it

The brief's condition is that **argv validation must complete before any option takes effect**. It
does. `main` is three statements — `parseArgs`, `validateArgv`, dispatch — and `stamp` is simply not
a legal name in a check argv, so there is no ordering left to get wrong.

Round 11's own direct probe, re-run against `676a6ab6` with `--module-dir` in a scratch directory:

```
$ java scripts/UpstreamOracleFloor.java --module=use-core --module-dir=$S/fakemod --reactor-root=. \
    --effective=false ... --allow-profiles=x --stamp=true --exec-args='${exec.args}' ...
[floor] FATAL — the argv is neither of the two legal shapes. Got [allow-profiles, effective, exec-args,
 exec-async, exec-executable, exec-outputfile, exec-quietlogs, exec-skip, exec-timeout, exec-workingdir,
 module, module-dir, reactor-root, requested, resume-from, selected, stamp]; ... Relative to the check
 argv: unexpected [stamp], missing []. ... This is fatal, exit 2, and no receipt is written.
EXIT=2
stamp present?          <- nothing written into the scratch target/
```

I then attacked the parser on five further shapes of my own. All five are `FATAL`, exit 2:

| probe | payload | what stopped it |
|---|---|---|
| P2 | `--exec-args=--stamp=true` (the value *is* an option) | `ARGV INJECTION: the value of --exec-args= itself parses as an option ('--stamp=true')` |
| P3 | `'--exec-args=x --stamp=true'` kept as **one** argv token — the route `CommandLineUtils.translateCommandline` opens when the property value carries quotes | same rule; `validateArgv` splits every value on whitespace before trusting it |
| P4 | injected `--module=use-gui` | `option --module= given twice … that is a command-line injection into this checker's argv (G-01)` |
| P5 | injected `--floor=0` | `unknown option --floor=` |
| P6 | injected bare `--stamp` (payload hiding as a continuation) | `malformed option-looking token '--stamp' … A token that begins with -- is never accepted as a continuation` |

That is the complete space: the poms emit all sixteen check options and all five stamp options
exactly once, so **any** injected token is a duplicate, an unknown name, a malformed `--` token, or
`stamp` — and each of the four is fatal before anything acts. And the fix does not break the thing it
had to preserve: `--selected=[use-core, use-gui]` still survives exec:exec's whitespace split
(`[floor] reactor: PARTIAL — selected projects [use-core, use-gui]`).

Through Maven, route A, verbatim:

```
[drv] $ mvn -B verify -Djava.awt.headless=true -Dexec.args=x --stamp=true
[drv] EXIT=1
[drv] log 1409 lines
[drv] lines containing [floor] anywhere: 6
[drv]   21:[floor] initialize: requested profiles (none), declared in this reactor [upstream-oracle], allow-profiles (-Duse.floor.allowProfiles) (none)
[drv]   1351:[floor] FATAL — the argv is neither of the two legal shapes. Got [... stamp]; ... unexpected [stamp], missing []. ... This is fatal, exit 2, and no receipt is written.
[drv]   1395:[INFO] BUILD FAILURE
[drv] receipts on disk:
[drv]   use-core: NO RECEIPT
[drv]   use-gui: NO RECEIPT
[drv] git status --porcelain AFTER: (clean)
```

Route B is better than merely red: it now fails at **`initialize`**, in a 79-line log, because the
G-04 fix put `--allow-profiles` into the stamp argv where the injected `--stamp=true` becomes a
*duplicate*:

```
[drv] $ mvn -B verify -Djava.awt.headless=true -Duse.floor.allowProfiles=x --stamp=true
[drv] EXIT=1
[drv]   21:[floor] FATAL — option --stamp= given twice. Every option is passed EXACTLY once by the pom,
        so a second one arrived inside an interpolated property value — that is a command-line
        injection into this checker's argv (G-01), not a configuration mistake.
[drv]   65:[INFO] BUILD FAILURE
```

### 2.2 G-02 — `contains("${")` is gone and the exact test behaves in all three directions

```
$ mvn -B verify -Djava.awt.headless=true '-Dexec.outputFile=/tmp/floorhide${z}.txt'
[drv] EXIT=1
[drv] lines containing [floor] anywhere: 0          <- still diverted; that is R-2, and listed
[drv]   1389:[INFO] BUILD FAILURE
[drv] receipts on disk:
[drv]   use-core: module=use-core mode=DEFAULT verdict=FAIL requested-profiles=(none) allow-profiles=(none)

$ cat '/tmp/floorhide${z}.txt'
[floor] FAIL — 1 floor violation(s) in use-core (DEFAULT mode):
[floor]   1. TAMPERING: -Dexec.outputFile=/tmp/floorhide${z}.txt was set on the command line. …
$ grep -c TAMPERING '/tmp/floorhide${z}.txt'
1
```

Round 11: `EXIT=0`, `verdict=PASS`, `grep -c TAMPERING` → `0`. All three cases of `isSet` behave:

* value **contains** a placeholder opener → **SET** → `TAMPERING` (above);
* value **is** the whole placeholder `${exec.outputFile}` → **unset**, 0 violations — the intended
  meaning of "the operator did not set it";
* **empty** value → **SET** → `TAMPERING: -Dexec.outputFile= was set on the command line`. This third
  case was wrong before too and is right now.

### 2.3 G-04 — the truncated lifecycle, and the one thing the fix must not break

```
$ mvn -B test -Djava.awt.headless=true -Pupstream-oracle-typo
[drv] EXIT=1
[drv] log 82 lines
[drv]   22:[floor] initialize: requested profiles [upstream-oracle-typo], declared in this reactor [upstream-oracle], allow-profiles (-Duse.floor.allowProfiles) (none)
[drv]   23:[floor] FATAL — PROFILE (initialize): -Pupstream-oracle-typo was requested on the command line
        but NO pom in this reactor declares a profile with that id. … This check is bound to
        `initialize` precisely so that a TRUNCATED LIFECYCLE cannot outrun it. …
[drv]   67:[INFO] BUILD FAILURE
```

`mvn -B compile -Pupstream-oracle-typo` is identical (exit 1, 82 lines) — so it is the lifecycle
phase that closes it, not the goal. **And the fix does not break the wrapper's own first step:**

```
[drv] mvn -q clean -Pupstream-oracle-typo EXIT=0
```

`clean` is a different lifecycle and never reaches `initialize`, exactly as the porter claimed and as
the wrapper needs.

The porter's decision **not** to bind the count floors at `test` is right and I would have made the
same one. The floors count `failsafe-reports`, which do not exist before `verify`; a `test`-phase
floor would be a second, weaker floor, and a second weaker floor is precisely the artefact a reader
mistakes for the gate. Plain `mvn test` is `R-7` and is listed.

### 2.4 G-05 — the hatch is now audible, and the wrapper refuses it

Log and receipt, from a run that deliberately widens B2:

```
[floor] initialize: requested profiles [upstream-oracle-typo], declared in this reactor [upstream-oracle],
        allow-profiles (-Duse.floor.allowProfiles) [upstream-oracle-typo]  <-- CHECK B2 WAS WIDENED BY THE COMMAND LINE
[floor] allow-profiles (-Duse.floor.allowProfiles): [upstream-oracle-typo]  <-- CHECK B2 WAS WIDENED BY THE COMMAND LINE
receipt: allow-profiles=[upstream-oracle-typo]  <-- CHECK B2 WAS WIDENED BY THE COMMAND LINE
```

And through the wrapper, which is where it matters —
`scripts/upstream-oracle-gate.sh default '-Duse.floor.allowProfiles=upstream-oracle'`:

```
[gate] mvn EXIT=0, log: .../gl-w2/default.log (1495 lines)
[gate] FAIL — default: receipt .../use-core/target/upstream-oracle-floor.receipt does not carry the line 'allow-profiles=(none)'. It says:
[gate]   allow-profiles=[upstream-oracle]  <-- CHECK B2 WAS WIDENED BY THE COMMAND LINE
[gate] FAIL — default: receipt .../use-gui/target/upstream-oracle-floor.receipt does not carry the line 'allow-profiles=(none)'. It says:
[gate]   allow-profiles=[upstream-oracle]  <-- CHECK B2 WAS WIDENED BY THE COMMAND LINE
[gate] GATE FAILED — 2 check(s) failed in mode 'default'.
```

A green Maven run, and a red **gate**: which is the whole architecture of §0 of the threat model,
working.

### 2.5 G-03's factual half — 22, measured, and my predecessor was the one who was wrong

I parsed the descriptor myself rather than trusting either number:

```
$ unzip -p ~/.m2/…/exec-maven-plugin-3.5.0.jar META-INF/maven/plugin.xml
goal exec: parameters whose <configuration> body holds a ${...}: 22
    addOutputToClasspath = ${addOutputToClasspath}      addResourcesToClasspath = ${addResourcesToClasspath}
    async = ${exec.async}                               asyncDestroyOnShutdown = ${exec.asyncDestroyOnShutdown}
    classpathScope = ${exec.classpathScope}             commandlineArgs = ${exec.args}
    executable = ${exec.executable}                     forceJava = ${exec.forceJava}
    includePluginDependencies = ${exec.includePluginsDependencies}
    inheritIo = ${exec.inheritIo}                       longClasspath = ${exec.longClasspath}
    longModulepath = ${exec.longModulepath}             outputFile = ${exec.outputFile}
    quietLogs = ${exec.quietLogs}                       skip = ${exec.skip}
    sourceRoot = ${sourceRoot}                          testSourceRoot = ${testSourceRoot}
    timeout = ${exec.timeout}                           toolchain = ${exec.toolchain}
    toolchainJavaHomeEnvName = ${exec.toolchainJavaHomeEnvName}
    useMavenLogger = ${exec.useMavenLogger}             workingDirectory = ${exec.workingdir}
read-only params: ['basedir', 'buildDirectory', 'pluginDependencies', 'project', 'session']
```

**22**, all editable, none read-only; twenty `exec.*` and two unprefixed. **7 pinned + 8 detected
(7 of which overlap) = 8 handled, 14 neither** — the porter's arithmetic, and it is right. Round 11's
prose said 21 while its own paste listed 22; the brief inherited the 21. Correcting it in the
normative files (`upstream-oracle-profile.md` §5.2.1, `harness-contract.md` §0.1,
`upstream-oracle-floor-verification.md` F-01, `gate-threat-model.md` §3 R-3, the checker's header)
and **leaving round 11's own report unaltered** is the right handling: a refutation report is a dated
record and must not be edited under a later signature. `git grep` finds no *normative* page still
saying eight or 21.

### 2.6 The asserting-method figures: the porter was right to refuse the brief's number

The brief instructed **198 / 464**; the porter wrote **199 / 465** and flagged the discrepancy. I
re-measured from source rather than deriving it, and the porter is right:

```
AntCyclicDependenciesCoreTest.java:   @Test=10  .check(=0  assert*=0
MavenCyclicDependenciesCoreTest.java: @Test=11  .check(=0  assert*=0
AntCyclicDependenciesGUITest.java:    @Test= 6  .check(=0  assert*=0
AntLayeredArchitectureTest.java:      @Test= 1  .check(=0  assert*=0
MavenCyclicDependenciesGUITest.java:  @Test= 4  .check(=0  assert*=0
MavenLayeredArchitectureTest.java:    @Test= 1  .check(=0  assert*=0
TOTAL non-asserting (oracle) = 33   ->  498 - 33 = 465
default subset = 11 + 1 = 12        ->  211 - 12 = 199
```

198/464 were the figures of the **210/497** suite, before round 11 added `UpstreamOracleGateWiringTest`
and re-pinned the floors in the same commit. Writing a number one has measured to be wrong into a
normative file would breach ground rule 1; declining to was correct.

---

## 3. Do the accident routes still fail? Every one of them

`mvn -q clean` first, pom restored with `git checkout --` after, `git status --porcelain` **(clean)**
after every restore.

| row | what was done | exit | what caught it |
|---|---|---|---|
| **t1** | `<profiles>` deleted from `use-gui/pom.xml`, **DEFAULT** command | **1** | `UpstreamOracleGateWiringTest` at the `test` phase, in `use-core`, naming **2 violations** in the *other* module |
| **t2** | `<profiles>` deleted from `use-core/pom.xml`, **ORACLE** command | **1** | same test, 2 violations |
| **t3** | `junit-vintage-engine` version → `9.9.9-does-not-exist`, ORACLE | **1** | `Could not resolve dependencies for project org.tzi.use:use-core:jar:7.5.0`, before any floor |
| **t4** | round 1's ORACLE `target/` left in place, every report `touch`ed **newer than any stamp**, then the DEFAULT command with **no clean** | **0 green, correctly** | `stale-ignored=47` / `7`; the DEFAULT run credited with exactly its own **8/80** and **1/1**; sentinel `absent` |
| **t5** | `use-gui`'s `initialize` execution reverted to its pre-round-12 argv | **1** | **3** `WIRING` violations, one per missing token, each naming G-04 |
| **w1** | the wrapper, `-Pupstream-oracle-typo` forwarded | **1** | **10** gate checks failed |
| **a10** | `-pl use-core -Pupstream-oracle` | 0 | `PARTIAL`, never `PASS`; `verdict=PARTIAL` on disk |

**t4 — the stamp still cannot be pre-`touch`ed:**

```
[drv] surefire XML present in use-core before this run: 55
[drv] touching every report so it is NEWER than any stamp this build has yet written
[floor] surefire  use-core  classes=8   (floor 8  )  methods=80   (floor 80  )  executions=80   failures=0 errors=0 skipped=0 stale-ignored=47
[floor] surefire  use-gui   classes=1   (floor 1  )  methods=1    (floor 1   )  executions=1    failures=0 errors=0 skipped=0 stale-ignored=7
[floor] vintage-only sentinel org.tzi.use.parser.USECompilerTest: absent
[floor] PASS — use-core met every pinned floor in DEFAULT mode.
```

47 oracle-only reports ignored in `use-core`, 7 in `use-gui`, identical to round 11.

**t5 — removing the G-04 fix from one pom is loud, and names itself:**

```
The -Pupstream-oracle acceptance gate is not wired as the record claims — 3 violation(s). …
  1. use-gui/pom.xml passes --reactor-root=.. 1 time(s), needs 2 — one per floor execution. Without the
     `initialize`-phase copy the unactivatable-profile check binds only at `verify`, and a truncated
     lifecycle (`mvn test -Pupstream-oracle-typo`) is BUILD SUCCESS with no gate in it at all (G-04).
  2. use-gui/pom.xml passes --requested=${session.request.activeProfiles} 1 time(s), needs 2 — …
  3. use-gui/pom.xml passes --allow-profiles=${use.floor.allowProfiles} 1 time(s), needs 2 — …
[INFO] BUILD FAILURE
```

**w1 — the wrapper on a mistyped id, which is now red twice over:**

```
[gate] mvn EXIT=1, log: .../gl-w1/default.log (82 lines)
[gate] FAIL — default: Maven exited 1. …
[gate] FAIL — default: no 'BUILD SUCCESS' in the log.
[gate] FAIL — default: Maven could not activate a requested profile. THIS IS DEFECT F-02: …
[gate] FAIL — default: expected exactly one line '[floor] PASS — use-core met every pinned floor in DEFAULT mode.' … found 0.
[gate] FAIL — default: the floor check announced itself 0 time(s), expected 2.
[gate] FAIL — default: the initialize-phase profile guard announced itself 1 time(s), expected 2. …
[gate] FAIL — default: no receipt at .../use-core/target/upstream-oracle-floor.receipt. …
[gate] GATE FAILED — 10 check(s) failed in mode 'default'.
```

Round 11's `r13` (a real failing test plus `-Dmaven.test.failure.ignore=true` → `verdict=FAIL`) I did
**not** re-run: it requires planting a failing file under `use-core/src/test`, and the mechanism it
exercises — `failures`/`errors` read from the report XML, asserted `= 0` — is unchanged this round by
inspection. I carry it forward as round 11's result, not as mine.

---

## 4. H-01 (MINOR) — the wrapper *runs* a Maven build inside one of its own error messages

**`scripts/upstream-oracle-gate.sh:186-191`**, new in `5ff47092`:

```bash
  n=$(grep -cE '^\[floor\] initialize: requested profiles ' "$log")
  if [ "$n" -ne ${#MODULES[@]} ]; then
    bad "$label: the initialize-phase profile guard announced itself $n time(s), expected" \
        "${#MODULES[@]}. Without it, `mvn test -Pupstream-oracle-typo` is a green build with" \
        "no gate in it (defect G-04)."
  fi
```

The backticks are inside a **double-quoted** word, so bash performs command substitution: the string
`mvn test -Pupstream-oracle-typo` is not quoted prose, it is **executed**. Line 138 in the same file
gets this right (`\`mvn -q clean\``); line 189 does not.

It fires. Here is the wrapper's own output on row w1, unedited — the message stops mid-sentence and
is replaced by the nested build's log:

```
52:[gate] FAIL — default: the initialize-phase profile guard announced itself 1 time(s), expected 2. Without it, [INFO] Scanning for projects...
53-[WARNING] The requested profile "upstream-oracle-typo" could not be activated because it does not exist.
55-[INFO] Reactor Build Order:
…
72-[INFO] --- exec:3.5.0:exec (upstream-oracle-floor-stamp) @ use-core ---
73-[floor] initialize: requested profiles [upstream-oracle-typo], declared in this reactor [upstream-oracle], …
78-[ERROR] Command execution failed.
```

Reduced to the shell alone, with the payload swapped for something harmless:

```
$ bash -c 'bad(){ echo "[gate] FAIL — $*"; }; n=0; MODULES=(a b);
    bad "lbl: the initialize-phase profile guard announced itself $n time(s), expected" \
        "${#MODULES[@]}. Without it, `echo I-JUST-RAN-A-COMMAND`  is a green build with" \
        "no gate in it (defect G-04)."'
[gate] FAIL — lbl: the initialize-phase profile guard announced itself 0 time(s), expected 2. Without it, I-JUST-RAN-A-COMMAND  is a green build with no gate in it (defect G-04).
```

It fired **three** times in this round's suite: rows `w1`, `r3w` (the `useMavenLogger` residual) and
the transient failure of §5.3.

**What it is, and what it is not.**

* It **cannot** turn a red gate green. `bad` increments `fail_count` whatever its arguments expand
  to, so the check still fails. I verified this: every run in which the substitution fired also
  reported `GATE FAILED`.
* It **does** launch an unrequested Maven inside a checkout the same script has just declared it
  needs to itself ("`The gate needs the reactor to itself. Refusing to start.`", line 112), between
  the wrapper's log checks and its on-disk receipt checks, and it writes into `target/`. Today the
  damage is bounded only because the very fix of this round makes `mvn test -Pupstream-oracle-typo`
  die at `initialize` before writing anything — the payload is harmless *by coincidence of the same
  commit*, not by design.
* It **destroys** the diagnostic for the one check G-04 added. An operator reading `[gate] FAIL —
  default: … expected 2. Without it, [INFO] Scanning for projects...` learns nothing about the
  failure and may reasonably conclude the gate is malfunctioning.

**Fix**: escape them, exactly as line 138 already does — `\`mvn test -Pupstream-oracle-typo\`` — or
drop the backticks. One character each, no behaviour change intended or implied.

---

## 5. Is `gate-threat-model.md` honest? Yes, with one over-statement and one omission

### 5.1 The completeness audit

I checked every defect id in the record against the file. **Nothing is hidden.**

| id | where it lands | verified |
|---|---|---|
| **D-01** (no asserted floor / merge accident) | §1 **A-2**, **A-7** | t1, t2, floors at equality |
| **D-02…D-07** | documentation defects of other pages; not gate bypasses. D-07's shape (a normative page issuing the wrong acceptance command) is §4 clause 1 | §6 |
| **F-01** (`-Dexec.args=-version` silences the floor) | §1 **A-8**; residual half in §3 **R-2**, **R-3** | a4, r3w |
| **F-02** (mistyped `-P`) | §1 **A-1** | w1, a5 |
| **F-03** (partial reactor) | §1 **A-6** | a10 |
| **F-04** (asserting figures) | §4 clause 3, as **199 / 465** | §2.6 |
| **F-05**, **F-06** | documentation, closed; correctly absent from a *gate* threat model | §6 |
| **G-01** | §3 closing note, "closed"; the mechanism in §2 | §2.1 |
| **G-02** | §3 closing note, "closed" | §2.2 |
| **G-03** | §3 **R-3**, with the corrected 22 / 7 / 8 / 14 | §2.5 |
| **G-04** | §1 **A-4** closed; the surviving half explicitly **R-7** | §2.3 |
| **G-05** | §3 **R-5** | §2.4 |
| irreducible | **R-0a** (tree edit), **R-0b** (`settings.xml`), **R-1** (hand-typed `mvn`), **R-6** (not running the gate) | — |

I then went looking for a **green** bypass that is *not* on that list, and did not find one:
`-DskipTests`, `-Dtest=…`, `-Dmaven.test.failure.ignore=true`, `--fail-never`, `-pl`, `-rf`,
`-P'!upstream-oracle'`, `-q`, and forwarding any of them through the wrapper are all covered by A-6,
A-7 or the receipt check, and were shown red in round 10, round 11 or this round. `MAVEN_ARGS`
remains closed for round 11's reason (Maven splits it and rejects the token itself).

**No mechanism is credited with something I can show it does not do.** I re-tested the two §3 rows
that make positive claims about what still happens:

* **R-3** — `-Dexec.useMavenLogger=true` *through the wrapper*, which the file says still fails on
  the announce count. It does:
  ```
  [gate] mvn EXIT=0, log: .../gl-r3/default.log (1495 lines)
  [gate] FAIL — default: the floor check announced itself 0 time(s), expected 2.
  [gate] GATE FAILED — 2 check(s) failed in mode 'default'.
  [drv] '[floor]' at line start in the mvn log: 0
  [drv] '[INFO] [floor]' in the mvn log:        28
  [drv] receipts: use-core=verdict=PASS use-gui=verdict=PASS
  ```
  Exactly as described: diagnostics degraded, verdict intact, gate red.
* **R-2** — `-Dexec.outputFile=<path>`: check runs, receipt written, `TAMPERING` fires,
  `verdict=FAIL`, build red. Confirmed in §2.2.

### 5.2 H-02 (MINOR) — R-4 is not a residual on this reactor; Maven refuses it

`gate-threat-model.md` §3 R-4 says `-Dexec.outputFile='${exec.outputFile}'` "reads as *unset*, so no
TAMPERING violation; `exec` writes the `[floor]` lines to a file literally named
`${exec.outputFile}`." I ran it:

```
$ mvn -B verify -Djava.awt.headless=true '-Dexec.outputFile=${exec.outputFile}'
ERROR: Could not interpolate properties and/or arguments: Resolving expression: '${exec.outputFile}':
Detected the following recursive expression cycle in 'exec.outputFile': [exec.outputFile]
[drv] EXIT=1
[drv] '[floor]' lines anywhere in the log: 0
[drv] receipts: use-core=NONE use-gui=NONE
[drv] a file literally named '${exec.outputFile}' in the modules?
[drv]   ls: cannot access 'use-core/${exec.outputFile}': No such file or directory
```

Maven 3.9.16 rejects the command line before a single plugin runs. Nothing is written anywhere. The
*conceptual* limit R-4 states — any scheme inferring "unset" from an uninterpolated placeholder is
blind to a value equal to that placeholder — is real, but on this Maven it is **unreachable**, because
the only value with that property is the self-reference Maven refuses. Setting the property to some
*other* placeholder (`-Dexec.outputFile='${zz}'`) reads as **SET** and raises `TAMPERING`, as
§2.2 shows.

This errs in the safe direction — the record claims a weakness it does not have — but the residual
list is normative prose and ground rule 1 binds it too. R-4 should say: *closed on Maven 3.9.16, which
refuses the self-referential property with a recursive-expression-cycle error; the limit is stated for
a future Maven that does not.*

### 5.3 H-03 (MINOR) — an accident route in neither §1 nor §3: a third writer in `target/`

The closing control run of the wrapper — tree byte-identical to HEAD, `git status --porcelain` empty
before and after, `pgrep` clean — went **red**:

```
[ERROR]   ModelAPITest.testConstraintCreation:18 » ClassFormat Extra bytes at the end of class file
          org/tzi/use/uml/ocl/expr/operations/Op_sequence_indexOf
[ERROR] Tests run: 80, Failures: 0, Errors: 1, Skipped: 0
[INFO] use-core ........................................... FAILURE [ 54.373 s]
[INFO] BUILD FAILURE
[gate] GATE FAILED — 16 check(s) failed in mode 'both'.
```

`use-core/target/classes/.../Op_sequence_indexOf.class` had been written malformed *during that
build*, after that build's own `mvn -q clean`. `rm -rf */target` and an immediate re-run was green in
both modes with every floor at equality (§1). Earlier in the suite, one `mvn -q clean` had also failed
with `Failed to clean project: Failed to delete .../use-gui/target/classes/org/tzi/use/gui`.

I cannot prove the cause, so I state only what I observed plus the most plausible reading: this
checkout is open in an editor whose Java language server is running
(`redhat.java … -data …/redhat.java/jdt_ws`), and for a Maven project that server's output folder
**is** `target/classes`. A background incremental compile writing there while surefire's forked JVM
loads the same directory produces exactly this signature.

**Why it belongs in the record.** The threat model's §1 lists eight accidents and §3 seven residuals,
and none of them is *"something other than Maven writes into `target/` during the run"*. It is a real
accident route for this gate:

* the wrapper's `git status --porcelain` bracket — ground rule 4's instrument — **cannot see it**,
  because `target/` is git-ignored;
* on this occasion it produced a **red** gate, which is the safe direction and is to the gate's
  credit;
* the unsafe direction is not obviously impossible: a *stale but well-formed* class written into
  `target/classes` between `compile` and `test` would let the suite pass against code that is not the
  tree's, and no floor, sentinel or receipt would notice — every one of them counts *reports*, not
  *bytecode provenance*.

I am **not** proposing a mechanism for it; that would be re-entering the treadmill §2 rightly refuses.
I am proposing one line in §1 or §3 naming it, and one line in the wrapper's usage note telling an
operator to close or disable the IDE's Java language server before an acceptance run. Out of scope is
fine; unlisted is not — that is the file's own standard, and this is the one thing I found that meets
it.

---

## 6. Is the wrapper still the only sanctioned path, in the record as well as in fact? Yes

`git grep` over **all** of `docs/port2/`, `spec-parts/` included.

| shape | result |
|---|---|
| a **normative** acceptance directive that hand-types `-P` | **none.** `harness-contract.md` §0.1 opens "THE GATE IS A SCRIPT. Hand-typing `-P` is not the gate.", now with the round-12 call-out to `gate-threat-model.md` immediately under the heading, and prints the two `mvn` lines under the explicit rubric "**quote the script, not these**". `specification.md` C1 (`:74-92`) and §9 (`:2599-2604`) lead with the wrapper. `stage-00-baseline.md:107-117` marks its old single-command directive corrected and ends "Quote the script." `harness-contract.md` §8a (`:797-807`) issues the wrapper first and the two commands as "What it runs". |
| `spec-parts/` | **clean** — three hits for `mvn test`, all in `15-upstream-delta.md` about JPMS packaging and `git clone && mvn test` as a *property of upstream's build*, none an acceptance directive. |
| `mvn test` as a gate | **none open**; disclaimed in four normative places, and now also by R-7. |
| the residual mentions | `harness-contract.md` §6 (`:517-520`) and `stage-00-baseline.md:328` still print bare `mvn … -Pupstream-oracle` — the first as "which invariants both acceptance commands run", cross-referencing §0; the second inside the **2026-08-17 decision-options paragraph** for B3, in past tense. Neither instructs a stage. **F-02 is not re-opened in prose.** |

`gate-threat-model.md` is referenced from `harness-contract.md` §0.1 in a call-out block, from the
wrapper's own header (lines 51-56), from `upstream-oracle-profile.md` §5.2.6, from
`upstream-oracle-floor-verification.md`'s F-01 row, and from the checker's header — five entry points.

---

## 7. Ground rules, at final HEAD

```
$ git diff --name-status 30d480db..HEAD -- '*/src/main/*'
(empty)
$ git diff --name-status 30d480db..HEAD -- 'use-gui/src' 'use-assembly/src' '*module-info.java'
(empty)
$ git status --porcelain
(empty)
$ git log --oneline -2
676a6ab6 docs(port2): the gate's threat model — the line, and every bypass listed
5ff47092 build: close G-01, G-02, G-04, G-05 in the upstream-oracle gate
$ md5sum docs/port2/differential/*.tsv
06455db44b8974cf3adbfa8a386f8d27  s1-smoke-ureal-add.tsv
f86905748739f263ff020e7dcc3294b4  s1-smoke-ureal-minus-faulty.tsv
```

Every change under `*/src/test/*` since `30d480db` is an `A`; **zero upstream test files edited**, in
the tree and in my own runs — the two poms I broke were restored with `git checkout --` and
`git status --porcelain` was empty after each.

---

## 8. Verdict

**`SOUND_WITH_DOCUMENTED_LIMITS`.** Three MINORs, none of which can produce a false green: H-01 is a
shell bug on a failure path, H-02 is a residual described as open that is in fact closed, H-03 is an
accident route the record does not list.

**Can S3–S10 gate on `scripts/upstream-oracle-gate.sh`? Yes — within the stated threat model, with
the residuals of §3 listed and with H-03 added to them.**

I say that plainly because the evidence is one-directional. The wrapper was green at HEAD twice,
before and after everything I did to the tree, with all eight floor cells met at equality and the
harness controls unmoved. It went red on every one of the fourteen things I did to make it lie:
four command-line injections, two log diversions, a mistyped id under three different lifecycles, five
tree mutations, a partial reactor and a widened escape hatch. On the two routes that are *deliberately*
left open it went red anyway — and the one run where the environment, not I, corrupted a class file, it
went red too. **I could not produce a single green wrapper run that was not a real gate.**

The line drawn in `gate-threat-model.md` §2 is the right line, and I say so as the fourth refuter in a
row rather than as its author. Round 11 established that the wrapper was the only thing holding; round
12 repaired the in-build binding *where the defect was a bug* (G-01 was a mode dispatched before its
input was validated; G-02 was a substring test where an equality test belonged) and *where the route
was an accident* (G-04), made the hatch auditable (G-05), corrected the arithmetic (G-03) — and then
stopped and wrote down what it did not do. The stopping is the part worth defending. Each further
round of pinning would enlarge the checker, and G-01 was a bug in a defence built for exactly that
treadmill: the fifth detector is likelier to *be* the next G-01 than to catch it.

What makes the decision safe is not the fixes; it is §3. A residual list that names R-1 through R-7,
R-0a and R-0b is a record that can be audited by the next reader in an afternoon, and my own attempt
to find something outside it produced exactly one item — H-03, an accident, not a bypass, that the
gate already fails safe on. **Add it, correct R-4, escape two backticks, and the file does what it
claims: it makes "out of scope" mean *known and declined*, not *unlooked-for*.**

The condition on S3–S10 is unchanged and is the wrapper's own: run
`scripts/upstream-oracle-gate.sh` with **no forwarded arguments**, paste its `[gate]` block verbatim
including both `git status --porcelain` lines, quote **199 / 465** when the number is used to argue
scrutiny and **211 / 498** when it is used to argue collection, and never hand-type `-P`. A number
produced by any other invocation is not a gate result.
