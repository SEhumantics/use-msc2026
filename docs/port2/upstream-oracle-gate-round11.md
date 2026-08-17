# Independent refutation of the round-11 gate — the invocation surface

**Verdict: `DEFECTIVE`.** One **CRITICAL**, two **MAJOR**, two **MINOR**.

**I owned Maven for this round.** 17 lifecycle runs (16 `verify`, 1 `test`), 17 `mvn -q clean` (one of
which failed by design — §2.6, w2), and 2 `dependency:list`: 36 `mvn` invocations in all. `pgrep -f '[c]lassworlds.launcher.Launcher'` returned
nothing before the suite started and before every driver row; `git status --porcelain` was captured
before and after every run by `scratchpad/drive.sh` and by the wrapper itself. **No foreign
modification was observed at any point**, no `docs/port2/differential/*.tsv` changed content
(`git status --porcelain -- docs/port2/differential/` empty; the two files are rewritten byte-identical
by the smoke test, which is pre-existing `audit-04-wildcard.md` F4, not a round-11 change), and the tree
is clean at the end. Commits under review: `cdcbea54` (build) + `f06fff38` + `2d541f66` (docs), branch
`port-uncertainty-2`.

I did not build this gate. Every break below is my own construction, run against the committed tree,
and every number is pasted from a log I produced.

---

## 0. Summary

**The gate, defined as `scripts/upstream-oracle-gate.sh`, held against every attack I constructed.** It
was the *only* thing that held on the four bypass routes below: its post-Maven, on-disk receipt check
is doing the work that three of the four advertised mechanisms are claimed to do and do not.

**The build binding does not hold.** Round 10's F-01 — *green build, exit 0, zero substantive `[floor]`
lines, no edit to any tracked file* — **is reproducible at `2d541f66` on three independent routes**, and
the property that reproduces it most cheaply is `exec.args`: the very property F-01 was about.

```
----- r2-inject-execargs : mvn -B verify -Djava.awt.headless=true -Dexec.args=x --stamp=true -----
----- r2-inject-execargs : EXIT=0 -----
[driver] number of [floor] lines in the log: 4
[driver]   21:[floor] wrote freshness stamp .../use-core/target/upstream-oracle-floor.stamp
[driver]   1350:[floor] wrote freshness stamp .../use-core/target/upstream-oracle-floor.stamp
[driver]   1358:[floor] wrote freshness stamp .../use-gui/target/upstream-oracle-floor.stamp
[driver]   1434:[floor] wrote freshness stamp .../use-gui/target/upstream-oracle-floor.stamp
[driver]   1467:[INFO] BUILD SUCCESS
[driver] receipts on disk:
[driver]   use-core: NO RECEIPT
[driver]   use-gui: NO RECEIPT
[driver] git status --porcelain AFTER:
```

No wiring check, no count floor, no sentinel, no `TAMPERING` violation — and the `[floor]` lines that
*are* there say "wrote freshness stamp", which reads like a working gate to anyone skimming.

| id | sev | one line | §|
|---|---|---|---|
| **G-01** | **CRITICAL** | **`--stamp=true` injected through any interpolated property short-circuits the whole checker and exits 0.** `-Dexec.args=`, `-Duse.floor.allowProfiles=` and `-Duse.upstreamOracle.effective=` each reproduce F-01 exactly, defeating mechanisms 1, 2 **and** 3 with one `-D` and no tree edit. | §2 |
| **G-02** | **MAJOR** | A2's set/unset test is `value.contains("${")`, so any value containing `${` reads as *unset*. `-Dexec.outputFile='/tmp/floorhide${z}.txt'` → 0 `[floor]` lines, `BUILD SUCCESS`, receipt `verdict=PASS`, **no `TAMPERING` violation**. The record says detection "is the whole defence" for this parameter. | §3 |
| **G-03** | **MAJOR** | `exec:exec` exposes **21** user properties, not the "eight" the record enumerates. `-Dexec.useMavenLogger=true` is neither pinned nor detected; it re-prefixes every line to `[INFO] [floor] …`, which zeroes both of the wrapper's **anchored** greps, and with `-q` the floor becomes **completely invisible** while still green. | §4 |
| **G-04** | MINOR | `mvn -B test -Pupstream-oracle-typo` → `BUILD SUCCESS`, exit 0, **no floor check at all**, no receipt, the typo a `[WARNING]`. B2 cannot fire because the floor is bound to `verify`. | §5 |
| **G-05** | MINOR | `-Duse.floor.allowProfiles` is documented as widening "THIS check and nothing else". It is also an argv-injection vector (G-01), and its value is echoed **nowhere** — not in the log, not in the receipt — so a widened allow-set leaves no trace in a green run. | §6 |

**G-01 is strictly worse than F-01 was.** F-01 defeated one mechanism. G-01 defeats three: the pins
(mechanism 1) are bypassed rather than overridden, check A2 (mechanism 2) never executes because the
short-circuit precedes it, and the Jupiter test (mechanism 3) passes because the injected execution
*writes a fresh stamp* — the exact artefact the test's runtime clause looks for.

---

## 1. Both acceptance commands at HEAD — green, floors met exactly

`scripts/upstream-oracle-gate.sh both`, `GATE_LOG_DIR` outside the reactor, `real 3m13.350s`, **exit 0**.
Pasted verbatim and unfiltered:

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
[gate] mvn EXIT=0, log: .../gatelogs/default.log (1491 lines)
[gate] the floor's own words for default:
[gate]   [floor] ===== upstream-oracle floor check: use-core =====
[gate]   [floor] requested profiles (reactor-wide, from the command line): (none)
[gate]   [floor] this module's upstream-oracle profile effective: false
[gate]   [floor] mode: DEFAULT
[gate]   [floor] reactor: FULL (no -pl/--projects, no -rf/--resume-from)
[gate]   [floor] surefire  use-core  classes=8   (floor 8  )  methods=80   (floor 80  )  executions=80   failures=0 errors=0 skipped=0 stale-ignored=0
[gate]   [floor] failsafe  use-core  classes=1   (floor 1  )  methods=1    (floor 1   )  executions=1    failures=0 errors=0 skipped=0 stale-ignored=0
[gate]   [floor] vintage-only sentinel org.tzi.use.parser.USECompilerTest: absent
[gate]   [floor] wrote receipt .../use-core/target/upstream-oracle-floor.receipt (verdict=PASS)
[gate]   [floor] PASS — use-core met every pinned floor in DEFAULT mode.
[gate]   [floor] surefire  use-gui   classes=1   (floor 1  )  methods=1    (floor 1   )  executions=1    failures=0 errors=0 skipped=0 stale-ignored=0
[gate]   [floor] failsafe  use-gui   classes=1   (floor 1  )  methods=129  (floor 129 )  executions=129  failures=0 errors=0 skipped=0 stale-ignored=0
[gate]   [floor] vintage-only sentinel org.tzi.use.gui.views.diagrams.util.DirectedLineTest: absent
[gate]   [floor] PASS — use-gui met every pinned floor in DEFAULT mode.

[gate] ----- oracle : expecting mode ORACLE in every module -----
[gate] mvn -q clean
[gate] mvn -B verify -Djava.awt.headless=true -Pupstream-oracle
[gate] mvn EXIT=0, log: .../gatelogs/oracle.log (1819 lines)
[gate]   [floor] requested profiles (reactor-wide, from the command line): [upstream-oracle]
[gate]   [floor] this module's upstream-oracle profile effective: true
[gate]   [floor] mode: ORACLE
[gate]   [floor] surefire  use-core  classes=41  (floor 41 )  methods=351  (floor 351 )  executions=939  failures=0 errors=0 skipped=0 stale-ignored=0
[gate]   [floor] failsafe  use-core  classes=1   (floor 1  )  methods=1    (floor 1   )  executions=1    failures=0 errors=0 skipped=0 stale-ignored=0
[gate]   [floor] vintage-only sentinel org.tzi.use.parser.USECompilerTest: collected
[gate]   [floor] PASS — use-core met every pinned floor in ORACLE mode.
[gate]   [floor] surefire  use-gui   classes=8   (floor 8  )  methods=17   (floor 17  )  executions=17   failures=0 errors=0 skipped=0 stale-ignored=0
[gate]   [floor] failsafe  use-gui   classes=1   (floor 1  )  methods=129  (floor 129 )  executions=129  failures=0 errors=0 skipped=0 stale-ignored=0
[gate]   [floor] vintage-only sentinel org.tzi.use.gui.views.diagrams.util.DirectedLineTest: collected
[gate]   [floor] PASS — use-gui met every pinned floor in ORACLE mode.

[gate] =================================================================
[gate] git status --porcelain AFTER:
[gate]   (nothing above == clean; report anything you did not write, never commit it)
[gate] PASS — mode 'both': every check above held.
```

Totals: **11 classes / 211 methods** (8+1+1+1, 80+1+1+129) and **51 / 498** (41+8+1+1, 351+17+1+129).
Every floor is met at **equality**, both modes, all eight cells.

**The re-pinning is a raise, not a lowering.** Diffed myself, `8080add5` → `HEAD`:

```
        round 10                                HEAD
ORACLE  "use-core/surefire", Floor(40, 350)     Floor(41, 351)
        "use-gui/surefire",  Floor(8, 17)       Floor(8, 17)
        "use-core/failsafe", Floor(1, 1)        Floor(1, 1)
        "use-gui/failsafe",  Floor(1, 129)      Floor(1, 129)
DEFAULT "use-core/surefire", Floor(7, 79)       Floor(8, 80)
        (other three cells identical)
```

`+1/+1` in exactly one cell of each table, seven cells untouched, nothing reduced. And the `+1/+1` is
accounted for: `git diff --name-status 8080add5..HEAD -- '*/src/test/*' '*/src/it/*'` is a single line,
`A use-core/src/test/java/org/tzi/use/uncertainty/gate/UpstreamOracleGateWiringTest.java`. So the 79
original default methods are all still there and one asserting method was added.

**Ground rule 2, checked independently.** `git diff --name-status 30d480db..HEAD -- '*/src/main/*'` →
empty. `git diff --name-status 8080add5..HEAD -- '*module-info.java' 'use-gui/src' 'use-assembly/src'` →
empty. Zero upstream tests edited; zero upstream test failures in either mode.

**Vintage absence proved by `dependency:list`, not by counts:**

```
[d1] DEFAULT vintage lines: 0
[d1] ORACLE vintage lines: 2
[INFO]    org.junit.vintage:junit-vintage-engine:jar:5.7.0:test -- module org.junit.vintage.engine
[INFO]    org.junit.vintage:junit-vintage-engine:jar:5.7.0:test -- module org.junit.vintage.engine
```

**Harness controls unregressed** (from the accepting default run's own output):

```
=== detection power: control (a perfect port) =====================
rows                 19083
measured rows        17199
agreement rows       17199
diverging operations 0   <- MUST be 0, or nothing below is attributable to a planted defect
stage passes         74 of 285  (isStagePass(1, none()))
```

`Unwritten-port invariant` 10/10, `HistoricalOracle class-loader isolation` 9/9,
`Differential harness regressions` 35/35, `Uncertainty differential smoke` 6/6, all with
`Failures: 0, Errors: 0, Skipped: 0`.

---

## 2. G-01 (CRITICAL) — the checker has a bypass in its own argv, reachable from ten user properties

### 2.1 The mechanism

`use-core/pom.xml:420` (and `use-gui/pom.xml`, identically) passes **one string** to `exec:exec`:

```xml
<commandlineArgs>../scripts/UpstreamOracleFloor.java --module=use-core --module-dir=. --reactor-root=..
  --effective=${use.upstreamOracle.effective} --selected=${session.request.selectedProjects} ...
  --allow-profiles=${use.floor.allowProfiles} --exec-args=${exec.args} ... </commandlineArgs>
```

Three facts compose into the defect:

1. `exec:exec` splits `<commandlineArgs>` with `CommandLineUtils.translateCommandline`, **after**
   Maven has interpolated it. A `${...}` whose value contains a space therefore becomes **two or more
   argv tokens**. Ten of the interpolated values are settable from the command line:
   `use.upstreamOracle.effective`, `use.floor.allowProfiles`, and the eight `exec.*`.
2. `UpstreamOracleFloor.parseArgs` (`scripts/UpstreamOracleFloor.java:831-858`) accepts any token of
   the form `--name=value` whose `name` is in `KNOWN_OPTIONS` — and `KNOWN_OPTIONS` contains
   **`stamp`** (`:238`), because the same program serves the `initialize` execution.
3. `main` tests for the stamp mode **first**, before every check (`:258-270`):

```java
    public static void main(String[] args) throws Exception {
        Map<String, String> opt = parseArgs(args);

        if (opt.containsKey("stamp")) {
            ...
            System.out.println("[floor] wrote freshness stamp " + stamp);
            return;
        }
```

So a single injected token — `--stamp=true` — makes the *verify-phase* execution rewrite the freshness
stamp and `return` with status 0. Everything after that line is skipped: check A (wiring), **check A2
(tampering)**, check B, check B2, the count floors, the sentinel, the verdict, **and the receipt**.

Verified against the checker directly, before spending a build (`--module-dir` pointed at a scratch
directory so nothing in the reactor was touched):

```
$ java scripts/UpstreamOracleFloor.java --module=use-core --module-dir=$S/fakemod --reactor-root=. \
    --effective=false --selected='${session.request.selectedProjects}' ... \
    --allow-profiles=x --stamp=true --exec-args='${exec.args}' ... --exec-workingdir='${exec.workingdir}'
[floor] wrote freshness stamp /tmp/.../fakemod/target/upstream-oracle-floor.stamp
EXIT=0
```

### 2.2 Route A — `-Dexec.args`, the property F-01 itself was about

```
[driver] git status --porcelain BEFORE:
[driver] mvn -q clean rc=0
----- r2-inject-execargs : mvn -B verify -Djava.awt.headless=true -Dexec.args=x --stamp=true -----
----- r2-inject-execargs : EXIT=0 -----
[driver] log: .../r2-inject-execargs.log (1471 lines)
[driver] number of [floor] lines in the log: 4
[driver] number of lines containing [floor] anywhere: 4
[driver] floor verdict/summary lines:
[driver]   21:[floor] wrote freshness stamp .../use-core/target/upstream-oracle-floor.stamp
[driver]   1350:[floor] wrote freshness stamp .../use-core/target/upstream-oracle-floor.stamp
[driver]   1358:[floor] wrote freshness stamp .../use-gui/target/upstream-oracle-floor.stamp
[driver]   1434:[floor] wrote freshness stamp .../use-gui/target/upstream-oracle-floor.stamp
[driver] floor violation detail:
[driver]   1319:[INFO] Tests run: 1, Failures: 0, ... -- in org.tzi.use.uncertainty.gate.UpstreamOracleGateWiringTest
[driver]   1467:[INFO] BUILD SUCCESS
[driver] receipts on disk:
[driver]   use-core: NO RECEIPT
[driver]   use-core stamp: present
[driver]   use-gui: NO RECEIPT
[driver]   use-gui stamp: present
[driver] git status --porcelain AFTER:
[driver] (end of r2-inject-execargs)
```

Note line 1319: **the Jupiter test passed.** Its runtime clause asks for a stamp that exists and is not
older than the newest receipt; the injected execution wrote a *fresh* stamp and no receipt exists to
compare against, so the clause is satisfied by the attack itself.

### 2.3 Route B — `-Duse.floor.allowProfiles`, the gate's own escape hatch, under the profile

```
----- r3-inject-allowprofiles-oracle : mvn -B verify -Pupstream-oracle -Djava.awt.headless=true -Duse.floor.allowProfiles=x --stamp=true -----
----- r3-inject-allowprofiles-oracle : EXIT=0 -----
[driver] log: .../r3-inject-allowprofiles-oracle.log (1799 lines)
[driver] number of [floor] lines in the log: 4
[driver]   21:[floor] wrote freshness stamp .../use-core/target/upstream-oracle-floor.stamp
[driver]   1640:[floor] wrote freshness stamp .../use-core/target/upstream-oracle-floor.stamp
[driver]   1648:[floor] wrote freshness stamp .../use-gui/target/upstream-oracle-floor.stamp
[driver]   1762:[floor] wrote freshness stamp .../use-gui/target/upstream-oracle-floor.stamp
[driver]   1795:[INFO] BUILD SUCCESS
[driver] receipts on disk:
[driver]   use-core: NO RECEIPT
[driver]   use-gui: NO RECEIPT
[driver] git status --porcelain AFTER:
```

The 51 classes / 498 methods really did run in this build. Nothing asserted a single one of them.

### 2.4 Route C — `-Duse.upstreamOracle.effective`, with no `exec.*` anywhere on the line

```
----- r5-inject-effective : mvn -B verify -Djava.awt.headless=true -Duse.upstreamOracle.effective=false --stamp=true -----
----- r5-inject-effective : EXIT=0 -----
[driver] number of [floor] lines in the log: 4
[driver]   ... 4 × "wrote freshness stamp" ...
[driver]   1467:[INFO] BUILD SUCCESS
[driver] receipts on disk:
[driver]   use-core: NO RECEIPT
[driver]   use-gui: NO RECEIPT
```

This route matters on its own: an operator's command line carries **no `exec.` prefix at all**, so
nothing about it looks like an attack on the exec plugin, and check A2 — which exists to make such an
attempt loud — is unreachable.

### 2.5 What the record claims, and what the runs show

| where | the claim | status |
|---|---|---|
| `upstream-oracle-profile.md` §5.2.1 mech. 2 | "Maven interpolates one only if the operator set it … any set property **fails the build**" | **refuted** — `exec.args` was set on r2 and the build was green and silent |
| `upstream-oracle-floor-verification.md` banner, F-01 row | "all eight `exec.*` user properties handed back to the checker so that *setting one fails the build*" | **refuted**, same run |
| `UpstreamOracleFloor.java:327-344` (check A2's comment) | "an attempt to switch the gate off is a BUILD FAILURE, not a silent no-op" | **refuted** — silent no-op, exit 0 |
| `use-core/pom.xml:341-385` (the pin comment) | "EVERY SILENCING PROPERTY IS PINNED" | **refuted** — pinning the *element* does not stop the *value* from reaching the argv |
| `UpstreamOracleGateWiringTest` javadoc | "three independent mechanisms, so that defeating one leaves the others standing" | **refuted** — one token defeats 1, 2 and 3 together |
| `upstream-oracle-profile.md` §5.2.4 rows B1/B4/B5 | `-Dexec.args=-version` → FAIL exit 1 | **still true**, and irrelevant: the payload, not the property, decides |

### 2.6 What survived: mechanism 4

The wrapper caught it. `scripts/upstream-oracle-gate.sh default '-Dexec.args=x --stamp=true'`:

```
[gate] forwarding extra Maven arguments: -Dexec.args=x --stamp=true
[gate] mvn EXIT=0, log: .../gatelogs-w1/default.log (1471 lines)
[gate] FAIL — default: expected exactly one line '[floor] PASS — use-core met every pinned floor in DEFAULT mode.' in the log, found 0. Every [floor] verdict line the log does have:
[gate]   (none at all — the floor check did not run: defect F-01)
[gate] FAIL — default: expected exactly one line '[floor] PASS — use-gui met every pinned floor in DEFAULT mode.' in the log, found 0. ...
[gate] FAIL — default: the floor check announced itself 0 time(s), expected 2.
[gate] FAIL — default: no receipt at .../use-core/target/upstream-oracle-floor.receipt. The verify-phase floor check did not run to completion in use-core. A silenced exec binding leaves exactly this trace (F-01).
[gate] FAIL — default: no receipt at .../use-gui/target/upstream-oracle-floor.receipt. ...
[gate] GATE FAILED — 5 check(s) failed in mode 'default'.
[w1] GATE EXIT=1
```

The environment route is closed for a different reason. `MAVEN_ARGS` is split on whitespace by Maven
itself, so the payload cannot survive as one argv element:

```
[w2] MAVEN_ARGS='-Dexec.args=x --stamp=true'
[gate] mvn -q clean
[gate] FAIL — default: `mvn -q clean` exited 1; see .../gatelogs-w2/default.clean.log
[w2] GATE EXIT=1
$ head -1 .../gatelogs-w2/default.clean.log
Unable to parse command line options: Unrecognized option: --stamp=true
```

So the injection needs a shell that keeps `-Dexec.args=x --stamp=true` as a single argument — i.e. a
human typing (or a CI step quoting) a Maven command, which is exactly the population §0.1 warns about.

### 2.7 The fix is inside the checker, not inside Maven

This is **not** an irreducible Maven property. Two independent one-line-class fixes:

* make stamp mode exact — `if (opt.containsKey("stamp"))` becomes reachable only when the argv is
  *exactly* `{stamp, module-dir}`; any injected `--stamp=true` in the check argv then hits `die(...)`,
  which is exit 2, no receipt, build red;
* reject injection generally — a value that arrives with whitespace in it is either a Maven `List`
  (starts `[`) or an attack; and `unsetIfUninterpolated` should compare against the exact literal
  `"${" + property + "}"` rather than `contains("${")` (that is G-02).

Either one makes all three routes red. Both together also make the payload class empty.

---

## 3. G-02 (MAJOR) — `contains("${")` is not a set/unset test

`scripts/UpstreamOracleFloor.java:864-866`:

```java
    static String unsetIfUninterpolated(String value) {
        return value == null || value.contains("${") ? "" : value;
    }
```

Any value containing `${` anywhere reads as *unset*, so check A2 never fires on it. `exec.outputFile`
is the one exec parameter the record says cannot be pinned, making detection "the whole defence":

```
----- r6-outputfile-dollarbrace : mvn -B verify -Djava.awt.headless=true -Dexec.outputFile=/tmp/floorhide${z}.txt -----
----- r6-outputfile-dollarbrace : EXIT=0 -----
[driver] number of [floor] lines in the log: 0
[driver] number of lines containing [floor] anywhere: 0
[driver]   1463:[INFO] BUILD SUCCESS
[driver] receipts on disk:
[driver]   use-core: module=use-core mode=DEFAULT verdict=PASS partial-reactor=false surefire.classes=8 surefire.methods=80
[driver]   use-gui: module=use-gui mode=DEFAULT verdict=PASS partial-reactor=false surefire.classes=1 surefire.methods=1
[driver] git status --porcelain AFTER:

$ grep -c TAMPERING r6-outputfile-dollarbrace.log
0
$ cat '/tmp/floorhide${z}.txt'
[floor] ===== upstream-oracle floor check: use-gui =====
...
[floor] PASS — use-gui met every pinned floor in DEFAULT mode.
```

The check ran and passed, so this is log-hiding, not a vacuous pass — but the record's compensating
claim ("check A2 detects the property") is false, and `[floor]` appears **nowhere** in a 1463-line green
log. Round 11's own `issues` list says of the plain `-Dexec.outputFile=/dev/null` case "AIM HERE FIRST";
aiming here shows the case is one character worse than recorded.

---

## 4. G-03 (MAJOR) — the parameter enumeration is wrong, and one of the missing ones blinds the wrapper

The record says (`upstream-oracle-profile.md` §5.2.1): *"`exec:exec` declares **eight** parameters with
user properties that could silence or divert the floor"*, and §5.1.2 asserts *"all seven overridable
`exec:exec` parameters are pinned"*. I read the same descriptor the porter says they read:

```
$ unzip -p ~/.m2/.../exec-maven-plugin-3.5.0.jar META-INF/maven/plugin.xml   # goal: exec
    async = ${exec.async}                          asyncDestroyOnShutdown = ${exec.asyncDestroyOnShutdown}
    addOutputToClasspath = ${addOutputToClasspath}  addResourcesToClasspath = ${addResourcesToClasspath}
    classpathScope = ${exec.classpathScope}        commandlineArgs = ${exec.args}
    executable = ${exec.executable}                forceJava = ${exec.forceJava}
    includePluginDependencies = ${exec.includePluginsDependencies}
    inheritIo = ${exec.inheritIo}                  longClasspath = ${exec.longClasspath}
    longModulepath = ${exec.longModulepath}        outputFile = ${exec.outputFile}
    quietLogs = ${exec.quietLogs}                  skip = ${exec.skip}
    sourceRoot = ${sourceRoot}                     testSourceRoot = ${testSourceRoot}
    timeout = ${exec.timeout}                      toolchain = ${exec.toolchain}
    toolchainJavaHomeEnvName = ${exec.toolchainJavaHomeEnvName}
    useMavenLogger = ${exec.useMavenLogger}        workingDirectory = ${exec.workingdir}
```

**21 user-property expressions, not eight.** Seven are pinned, eight are handed to A2, and thirteen are
neither. One of the thirteen matters:

```
----- r7-usemavenlogger : mvn -B verify -Djava.awt.headless=true -Dexec.useMavenLogger=true -----
----- r7-usemavenlogger : EXIT=0 -----
[driver] number of [floor] lines in the log: 0
[driver] number of lines containing [floor] anywhere: 24
[driver]   1350:[INFO] [floor] ===== upstream-oracle floor check: use-core =====
[driver]   1360:[INFO] [floor] PASS — use-core met every pinned floor in DEFAULT mode.
```

Every line is now `[INFO] [floor] …`, which zeroes both of the wrapper's anchored greps —
`grep -cE '^\[floor\] ===== upstream-oracle floor check:'` (check 3's announce count) and
`grep -qE '^\[floor\] (FAIL|FATAL|PARTIAL)'` (check 3's verdict detector). The wrapper still fails the
run, on the announce count — but its **FAIL/PARTIAL detector is blind** under this property, and only
the receipt would notice a real FAIL. Combined with `-q` the floor vanishes entirely while the check
still runs and passes:

```
----- r12-quiet-usemavenlogger : mvn -q verify -Djava.awt.headless=true -Dexec.useMavenLogger=true -----
----- r12-quiet-usemavenlogger : EXIT=0 -----
[driver] number of [floor] lines in the log: 0
[driver] number of lines containing [floor] anywhere: 0
[driver] receipts on disk:
[driver]   use-core: module=use-core mode=DEFAULT verdict=PASS partial-reactor=false surefire.classes=8 surefire.methods=80
```

For the record, plain `-q` alone does **not** hide the floor — `exec:exec` writes the child's output to
Maven's own stdout, which `-q` does not filter (`r9-quiet-oracle`: 24 `[floor]` lines, `PASS` twice,
receipts `mode=ORACLE verdict=PASS`). It takes `useMavenLogger` to route the lines through the logger
where `-q` can suppress them. The claim that any `-Dexec.*` "is a violation in its own right" is false
for thirteen of the twenty-one.

---

## 5. G-04 (MINOR) — the floor binds at `verify`, so a truncated lifecycle has no gate

```
----- r8-test-phase-typo : mvn -B test -Pupstream-oracle-typo -Djava.awt.headless=true -----
----- r8-test-phase-typo : EXIT=0 -----
[driver] number of [floor] lines in the log: 2
[driver]   22:[floor] wrote freshness stamp .../use-core/target/upstream-oracle-floor.stamp
[driver]   1335:[floor] wrote freshness stamp .../use-gui/target/upstream-oracle-floor.stamp
[driver]   2:[WARNING] The requested profile "upstream-oracle-typo" could not be activated because it does not exist.
[driver]   1320:[INFO] Tests run: 1, ... -- in org.tzi.use.uncertainty.gate.UpstreamOracleGateWiringTest
[driver]   1393:[INFO] BUILD SUCCESS
[driver] receipts on disk:
[driver]   use-core: NO RECEIPT
[driver]   use-gui: NO RECEIPT
```

F-02's original shape, intact, one phase earlier: green, exit 0, mistyped profile a warning, B2 never
reached. The Jupiter test runs here and passes — it asserts *wiring*, and cannot see the `-P` list.

**Why this is MINOR and not MAJOR:** the record disclaims `mvn test` as a gate, repeatedly and
normatively — `harness-contract.md:79` ("Neither is `mvn test`, which never runs the 130 failsafe
methods"), `specification.md:94`, `stage-00-baseline.md:60,100`. **But** ground rule 4 says a dormant
sibling loop in this very checkout will fire `mvn -B clean test`, and the only thing separating that
from an apparently-successful acceptance is a reader who knows which command they are looking at. If a
cheap closure is wanted, the Jupiter test is already in the `test` phase and could be handed the
requested-profile list through surefire `<systemPropertyVariables>`; that would make the typo red at
`test` too.

---

## 6. G-05 (MINOR) — the allow-set escape hatch is silent

`-Duse.floor.allowProfiles` is documented (three places, e.g. `upstream-oracle-profile.md` §5.2.2) as
widening B2 "and nothing else". Two corrections:

1. it is an argv-injection vector — §2.3 above;
2. its value is printed **nowhere**. The checker echoes `requested`, `effective`, mode, partiality,
   the stamp, every tally and the sentinel; `writeReceipt` (`:526-563`) records
   `module/mode/verdict/partial-reactor/selected-projects/resume-from/requested-profiles/effective/stamp/violations`
   and the tier counts. Neither prints the allow-set. So
   `mvn -B verify -Pupstream-oracle-typo -Duse.floor.allowProfiles=upstream-oracle-typo` is a green
   DEFAULT-mode run whose log and receipt contain no evidence that a check was widened.

That is the same "inert is not loud" principle the porter applied to `exec.*` in check A2, not applied
to the porter's own escape hatch.

---

## 7. What held — every route I could not break

| # | route (tree intact unless stated) | exit | `[floor]` lines | receipt | outcome |
|---|---|---|---|---|---|
| r9 | `mvn -q verify -Pupstream-oracle` | 0 green | 24 | `mode=ORACLE verdict=PASS` | **HOLDS** — `-q` does not hide the floor |
| r11 | `-Pupstream-oracle` **and** `-P'!upstream-oracle'` | **1** | 19 | `verdict=FAIL` | **HOLDS** — EFFECTIVENESS + both floors + SENTINEL |
| r13 | a real failing test **and** `-Dmaven.test.failure.ignore=true` | **1** | 16 | `verdict=FAIL` | **HOLDS** — `failures=1` read from the XML |
| t4 | previous oracle run's reports left in place and `touch`ed, then the DEFAULT command with **no clean** | 0 green | 24 | `verdict=PASS` | **HOLDS** — `stale-ignored=47` / `7`, sentinel `absent`, counts 8/80 and 1/1 |
| w1 | the wrapper, injection forwarded | **1** | — | absent | **HOLDS** — 5 checks |
| w2 | `MAVEN_ARGS` injection, wrapper called bare | **1** | — | — | **HOLDS** — Maven rejects the token itself |
| t1 | `<profiles>` deleted from `use-gui/pom.xml`, **DEFAULT** command | **1** | 1 | absent | **HOLDS** — at the `test` phase, via the Jupiter test |
| t2 | `<profiles>` deleted from `use-core/pom.xml`, ORACLE command | **1** | 1 | absent | **HOLDS** — same |
| t3 | vintage version mangled to something unresolvable, ORACLE | **1** | 0 | absent | **HOLDS** — dependency resolution |

Selected messages, verbatim.

**r13 — a failure surefire was told to ignore is still a floor violation:**

```
[floor] surefire  use-core  classes=9   (floor 8  )  methods=81   (floor 80  )  executions=81   failures=1 errors=0 skipped=0 stale-ignored=0
[floor] wrote receipt .../use-core/target/upstream-oracle-floor.receipt (verdict=FAIL)
[floor] FAIL — 1 floor violation(s) in use-core (DEFAULT mode):
[floor]   1. FLOOR use-core/surefire: failures=1 errors=0, both must be 0.
[ERROR] Tests run: 81, Failures: 1, Errors: 0, Skipped: 0
[INFO] BUILD FAILURE
```

(The probe was my own file under `use-core/src/test`, added and deleted inside the run; `git status
--porcelain` empty afterwards.)

**r11 — a deactivated-but-requested profile is named for what it costs:**

```
[floor] mode: ORACLE
[floor] surefire  use-core  classes=8   (floor 41 )  methods=80   (floor 351 )  ...
[floor] FAIL — 4 floor violation(s) in use-core (ORACLE mode):
[floor]   1. EFFECTIVENESS: -Pupstream-oracle WAS REQUESTED ON THE COMMAND LINE BUT IS NOT EFFECTIVE IN use-core. ... That is D-01's merge accident, and it is an error, not a pass.
[floor]   2. FLOOR use-core/surefire: 8 distinct test classes < floor 41. ...
[floor]   3. FLOOR use-core/surefire: 80 distinct test methods < floor 351. ...
[floor]   4. SENTINEL use-core: org.tzi.use.parser.USECompilerTest produced no report under -Pupstream-oracle. ...
```

**t4 — the freshness stamp cannot be pre-`touch`ed, because it is written after the touch:**

```
[t4] touching every surefire report so it is NEWER than any stamp this build will write
[floor] surefire  use-core  classes=8   (floor 8  )  methods=80   (floor 80  )  executions=80   failures=0 errors=0 skipped=0 stale-ignored=47
[floor] vintage-only sentinel org.tzi.use.parser.USECompilerTest: absent
[floor] PASS — use-core met every pinned floor in DEFAULT mode.
```

47 oracle-only reports ignored in `use-core`, 7 in `use-gui`; the DEFAULT run is credited with exactly
its own 8/80 and 1/1. The stamp is written at `initialize`, so any pre-existing file is older by
construction; to beat it an attacker must touch files *during* the build, which is not an invocation.

**t1 — the merge accident still fails the DEFAULT command, now one phase earlier:**

```
[ERROR] org.tzi.use.uncertainty.gate.UpstreamOracleGateWiringTest.theUpstreamOracleGateIsWiredAndCannotBeSilencedFromTheCommandLine -- <<< FAILURE!
The -Pupstream-oracle acceptance gate is not wired as the record claims — 2 violation(s). ...
  1. use-gui/pom.xml HAS NO <profiles> ELEMENT — the upstream-oracle profile has been deleted, so -Pupstream-oracle would silently collect the default build's tests in this module (D-01's merge accident).
  2. use-gui/pom.xml's upstream-oracle profile does not set use.upstreamOracle.effective=true, so the gate loses its requested-but-not-effective detector.
[INFO] use-core ........................................... FAILURE [ 55.579 s]
```

---

## 8. The documentation sweep — F-04, F-05, F-06 and the five stale-recommendation shapes

Swept independently with `git grep` over **all** of `docs/port2/`, including `spec-parts/`.

| shape | result |
|---|---|
| acceptance command lacking the profile **or** the wrapper, in a **normative** place | **none open.** `harness-contract.md` §0.1 opens "THE GATE IS A SCRIPT. Hand-typing `-P` is not the gate."; `specification.md` C1 (`:68-92`) leads with the wrapper; `stage-00-baseline.md:100-113` now issues the wrapper and marks the old single-command directive as corrected (F-05 closed); `foundation-verdict.md:70`'s single command is a dated record of the run at `c91277ff`, not a directive. The ~90 other hits are dated round-3…round-8 verification records. |
| `mvn test` as a gate | **none.** `harness-contract.md:79`, `specification.md:94-103`, `stage-00-baseline.md:60`, `audit-03-acceptance.md` all disclaim it. (This is why G-04 is MINOR.) |
| SBoolean called scope-limited | **none open** — the only live use is `b7-fix-plan.md:821` describing the *rejected* scope-limited plan in past tense, and `specification.md:2494` marks full port as decided. |
| the port called bug-for-bug faithful | **none open.** 20 hits, every one either struck, marked "NOT taken", or a historical quotation — including `spec-parts/12-expressions.md:161`, F-06's site, which now reads `~~the fix is out of scope for a faithful copy~~ **SUPERSEDED 2026-08-17 (B7, defect F-06)**` and keeps the struck instruction visible with "do not act on it". |
| coverage called prose-only | **none open** — `foundation-verdict.md:244,269`, `harness-contract.md:153,384,474,717` all mark the prose position as recommended and not taken and point at `h14-coverage-design.md`. |
| F-04's asserting figures | **closed** — `harness-contract.md` §0.1 carries the per-tier table with **199 of 211** and **465 of 498**, the 12/33 ArchUnit arithmetic, and an explicit instruction on which figure to quote. |

I found nothing missed in the sweep. The two documentation defects I do file are the ones §2.5 and §6
list: statements about the *build* that the build does not honour.

---

## 9. Verdict

`DEFECTIVE` — one CRITICAL (G-01), two MAJOR (G-02, G-03), two MINOR (G-04, G-05).

**Can S3–S10 gate on this?** Yes, with one condition and one repair.

* **The condition:** the gate is `scripts/upstream-oracle-gate.sh`, invoked with **no forwarded `-D`**,
  and a stage quotes the script's own `[gate] PASS` block. On every route in this report the wrapper
  gave the right answer, including all four bypasses. `harness-contract.md` §0.1 already says this.
* **The repair:** G-01 must be closed before the record's claims about the build binding are true.
  Until then, the two `mvn -B verify …` lines that §0.1, C1 and `stage-00-baseline.md` print beside the
  script are **not** self-checking, and any of ten `-D`s turns one of them into a green, silent,
  assertion-free build whose only trace is an absent receipt file that nothing but the wrapper reads.

**None of G-01…G-03 is an irreducible property of Maven.** G-01 is a reachable short-circuit in the
porter's own 907-line checker; G-02 is a substring test where an equality test belongs; G-03 is an
enumeration that can be read off the plugin descriptor in one command. All three are repairable inside
the tree, and §2.7 gives the shape.

Two limits *are* irreducible and are correctly documented already: a sufficiently deliberate tree edit
can always delete a check from the tree (`upstream-oracle-profile.md` §5.1.3 item 3), and Maven will
always accept a `-P` id that some `settings.xml` outside the reactor declares and that does nothing
(§5.2.2). Those two are as good as a Maven gate gets, and the documentation that makes them acceptable
is §0.1's rule that acceptance is a *committed invocation whose post-conditions are checked outside
Maven* — the receipt-on-disk check. This report's finding is that that one mechanism is currently
carrying the whole load, while the record describes four.
