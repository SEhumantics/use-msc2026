# Independent refutation of the `-Pupstream-oracle` floor — round 10

**Verdict: `DEFECTIVE`.** Two MAJOR defects, four MINOR.

**I owned Maven for this round.** 17 full `mvn verify` invocations (plus one `dependency:list` and a `mvn -q clean` before each), every one preceded by
`pgrep -f '[c]lassworlds.launcher.Launcher'` returning nothing, `git status --porcelain` captured
before and after every one. No foreign modification was observed at any point; the tree is clean at
the end. Commit under review: `6702f06e` (build) + `f5ffd520` (docs), branch `port-uncertainty-2`.

I did not build this floor. Every break below is my own construction, run against the committed
tree, and every number in this file is pasted from a log I produced. Where I reached a wrong
conclusion and then refuted myself, the wrong conclusion is kept (§5.2).

---

## 0. Summary

The floor is **real**. It is a build-enforced, correctly-pinned mechanism, it fails on every
tree-borne route to a lost population that I could construct, and it cannot be satisfied without the
upstream tests actually running — I tried to seed it with genuine oracle reports and the freshness
stamp rejected all 55 of them. The four count floors are literals transcribed from figures that were
already committed **in the parent commit**, so they provably do not derive from the run they
validate. The default build is untouched: 210 methods, 0 failures, `dependency:list | grep -c
vintage` = 0. The harness has not regressed on any control.

It is nonetheless `DEFECTIVE`, because **D-01's shape survives on two routes**, and D-01's shape is
"the gate can silently revert to vacuity and still be `BUILD SUCCESS`":

| id | sev | one line | evidence |
|---|---|---|---|
| **F-01** | **MAJOR** | `-Dexec.args=-version` silences the entire floor. Green build, exit 0, **zero** `[floor]` lines, clean `git status`. The record claims the check cannot be silenced from the command line. | §3.7 |
| **F-02** | **MAJOR** | `-Pupstream-oracle-typo` → `BUILD SUCCESS`, floor **PASSES** in DEFAULT mode, the 40 revived classes / 287 revived methods uncollected. This is the empirical refutation's **RB-1**, whose surviving half is recorded in no document. | §3.5 |
| **F-03** | MINOR | `-pl use-core -Pupstream-oracle` prints `PASS` for half a gate. | §3.8 |
| **F-04** | MINOR | The prior refutation's operating condition — *treat 198 and 464, not 210 and 497, as the asserting-method figures* — appears on exactly one line of the whole record and not in the now-normative `harness-contract.md` §0.1. | §6.1 |
| **F-05** | MINOR | `stage-00-baseline.md:100-101` still issues a **single-command** acceptance directive to S3–S10 in bold. D-07's shape, in a file the D-04..D-07 sweep did not open. | §6.2 |
| **F-06** | MINOR | `spec-parts/12-expressions.md:161` states B7's reversed recommendation as instruction: "the fix is out of scope for a faithful copy". | §6.3 |

F-01 is strictly worse than the residual hole the porter documented at
`upstream-oracle-profile.md` §5.1.3 item 3: that one needs a visible two-file pom edit, this one
needs no tree edit at all.

---

## 1. Both acceptance commands at HEAD — green, twice each

`mvn -q clean` before each. Runs `R1`/`R2` at the start of the round, `z1`/`z2` after the whole
break suite had finished and the tree had been restored.

### 1.1 Default — `mvn -B verify -Djava.awt.headless=true`

```
[floor] ===== upstream-oracle floor check: use-core =====
[floor] requested profiles (reactor-wide, from the command line): (none)
[floor] this module's upstream-oracle profile effective: false
[floor] mode: DEFAULT
[floor] freshness stamp: 2026-08-17T15:11:51.438Z — reports older than this are stale and are NOT counted
[floor] surefire  use-core  classes=7   (floor 7  )  methods=79   (floor 79  )  executions=79   failures=0 errors=0 skipped=0 stale-ignored=0
[floor] failsafe  use-core  classes=1   (floor 1  )  methods=1    (floor 1   )  executions=1    failures=0 errors=0 skipped=0 stale-ignored=0
[floor] vintage-only sentinel org.tzi.use.parser.USECompilerTest: absent
[floor] PASS — use-core met every pinned floor in DEFAULT mode.
[floor] ===== upstream-oracle floor check: use-gui =====
[floor] mode: DEFAULT
[floor] surefire  use-gui   classes=1   (floor 1  )  methods=1    (floor 1   )  executions=1    failures=0 errors=0 skipped=0 stale-ignored=0
[floor] failsafe  use-gui   classes=1   (floor 1  )  methods=129  (floor 129 )  executions=129  failures=0 errors=0 skipped=0 stale-ignored=0
[floor] vintage-only sentinel org.tzi.use.gui.views.diagrams.util.DirectedLineTest: absent
[floor] PASS — use-gui met every pinned floor in DEFAULT mode.
[INFO] BUILD SUCCESS
real	1m37.225s
```

7 + 1 + 1 + 1 = **10 classes**; 79 + 1 + 1 + 129 = **210 methods**; surefire 80, failsafe 130.
Exactly the figure the brief fixes.

### 1.2 Under the profile — `mvn -B verify -Pupstream-oracle -Djava.awt.headless=true`

```
[floor] requested profiles (reactor-wide, from the command line): [upstream-oracle]
[floor] this module's upstream-oracle profile effective: true
[floor] mode: ORACLE
[floor] surefire  use-core  classes=40  (floor 40 )  methods=350  (floor 350 )  executions=938  failures=0 errors=0 skipped=0 stale-ignored=0
[floor] failsafe  use-core  classes=1   (floor 1  )  methods=1    (floor 1   )  executions=1    failures=0 errors=0 skipped=0 stale-ignored=0
[floor] vintage-only sentinel org.tzi.use.parser.USECompilerTest: collected
[floor] PASS — use-core met every pinned floor in ORACLE mode.
[floor] mode: ORACLE
[floor] surefire  use-gui   classes=8   (floor 8  )  methods=17   (floor 17  )  executions=17   failures=0 errors=0 skipped=0 stale-ignored=0
[floor] failsafe  use-gui   classes=1   (floor 1  )  methods=129  (floor 129 )  executions=129  failures=0 errors=0 skipped=0 stale-ignored=0
[floor] vintage-only sentinel org.tzi.use.gui.views.diagrams.util.DirectedLineTest: collected
[floor] PASS — use-gui met every pinned floor in ORACLE mode.
[INFO] BUILD SUCCESS
real	1m52.859s
```

40 + 8 + 1 + 1 = **50 classes**; 350 + 17 + 1 + 129 = **497 methods**. Every floor met **exactly**,
which is the interesting part: there is no slack anywhere, so any loss of any population fails.

### 1.3 Counts cross-checked by an instrument the porter did not write

My own counter, over the report XML of the accepting oracle run:

```
use-core  surefire  classes= 40 methods(per-file-distinct)= 350 executions= 938 f=0 e=0 s=0
use-core  failsafe  classes=  1 methods(per-file-distinct)=   1 executions=   1 f=0 e=0 s=0
use-gui   surefire  classes=  8 methods(per-file-distinct)=  17 executions=  17 f=0 e=0 s=0
use-gui   failsafe  classes=  1 methods(per-file-distinct)= 129 executions= 129 f=0 e=0 s=0
REACTOR   TOTAL     classes=50 methods(per-file-distinct)=497 executions=1085
```

---

## 2. Is the floor a literal pinned before the run, or derived from it? — **A PINNED LITERAL**

`harness-contract.md` §8 step 2: *"A floor chosen after the run is not a floor"*, and `0` is
*"rejected outright"*.

**Read from the source.** `scripts/UpstreamOracleFloor.java:97-107` — the floors are
`static final Map<String, Floor>` initialised from integer literals:

```java
    static final Map<String, Floor> ORACLE = Map.of(
            "use-core/surefire", new Floor(40, 350),
            "use-gui/surefire", new Floor(8, 17),
            "use-core/failsafe", new Floor(1, 1),
            "use-gui/failsafe", new Floor(1, 129));

    static final Map<String, Floor> DEFAULT = Map.of(
            "use-core/surefire", new Floor(7, 79),
            "use-gui/surefire", new Floor(1, 1),
            "use-core/failsafe", new Floor(1, 1),
            "use-gui/failsafe", new Floor(1, 129));
```

There is no code path that computes, refreshes or relaxes a floor. The only write the program
performs anywhere is the freshness stamp (`:136-141`); there is no `--refresh`, no property, no
file that could carry a floor. So it is structurally impossible for the floor to be derived from the
run it validates, which is the failure mode §8 step 2 names. No floor is `0`.

**Provenance, checked without relying on the porter's word.** The porter told me the four DEFAULT
figures were its own derivation. That is not what the repository shows — all eight literals are
transcriptions of figures **already committed in `9986e0e0`, the parent of the floor commit**:

```
$ git show 9986e0e0:docs/port2/upstream-oracle-profile.md | grep -n ...
168:surefire  use-core  classes=  7 methods=  79 executions=  79 failures=0 errors=0 skipped=0
172:TOTAL               classes= 10 methods= 210 executions= 210 failures=0 errors=0 skipped=0

$ git show 9986e0e0:docs/port2/upstream-oracle-verification.md | grep -n ...
278:surefire  use-core  nonempty_classes= 40 distinct_methods= 350  zero_testcase_files_excluded=14
281:failsafe  use-gui   nonempty_classes=  1 distinct_methods= 129
282:TOTAL nonempty_classes=50 distinct_methods=497
```

The floor is therefore pinned to a measurement that predates the commit that introduced the floor,
by a different author, in a document written for a different purpose. That is a stronger guarantee
than "I typed them first" and it is the one a reviewer can check. **§2 is SOUND.**

One caveat I cannot remove: the accepting run met all eight floors *exactly*, so a floor equal to the
measurement is indistinguishable from a floor fitted to it by inspection of the numbers alone. The
provenance above, not the equality, is what settles it.

---

## 3. Breaking the gate — my own suite, 11 experiments

Driver: `mvn -q clean` then the acceptance command, tree restored with `git checkout --` and
`git status --porcelain` pasted after every one. Preconditions:

```
################ PRECONDITION ################
[driver] HEAD: f5ffd520
[driver] git status --porcelain:
[driver] (empty above == clean)
```

| # | what I broke | command | result | names what was lost? |
|---|---|---|---|---|
| a1 | `<profiles>` deleted from `use-gui/pom.xml` | **default**, no `-P` | **FAIL** exit 1 | yes |
| a2 | same | `-Pupstream-oracle` | **FAIL** exit 1 | yes |
| b | `<profiles>` deleted from `use-core/pom.xml` | `-Pupstream-oracle` | **FAIL** exit 1, 5 violations | yes |
| c | vintage engine version unresolvable | `-Pupstream-oracle` | **FAIL** exit 1 (Maven, pre-test) | yes, by artifact |
| **d** | **nothing — `-P` id mistyped** | `-Pupstream-oracle-typo` | **PASS, exit 0** | **NO — F-02** |
| e | `-Dexec.skip=true` + a1 | default | **FAIL** exit 1 | yes |
| f | `-Dexec.args` + `-Dexec.executable` + a1 | default | FAIL exit 1 (garbled cmdline) | no, but closed |
| g | `-DskipTests` | `-Pupstream-oracle` | **FAIL** exit 1, 7 violations | yes |
| h | `-pl use-core` | `-Pupstream-oracle` | **PASS, exit 0** | **NO — F-03** |
| l | both exec plugin blocks + a1 deleted | default | **PASS, exit 0** | **NO — documented §5.1.3-3** |
| **m/n** | **nothing — `-Dexec.args=-version`** | both | **PASS, exit 0** | **NO — F-01** |

### 3.1 (a1) The D-01 merge accident, from the DEFAULT command

The central claim of the fix is that deleting only `use-gui`'s profile block fails the build even
when no profile is requested. It does.

```
----- a1-gui-del-default : mvn EXIT=1 -----
[floor] requested profiles (reactor-wide, from the command line): (none)
[floor] this module's upstream-oracle profile effective: false
[floor] mode: DEFAULT
[floor] surefire  use-core  classes=7   (floor 7  )  methods=79   (floor 79  )  executions=79   failures=0 errors=0 skipped=0 stale-ignored=0
[floor] failsafe  use-core  classes=1   (floor 1  )  methods=1    (floor 1   )  executions=1    failures=0 errors=0 skipped=0 stale-ignored=0
[floor] vintage-only sentinel org.tzi.use.parser.USECompilerTest: absent
[floor] ###############################################################
[floor] FAIL — 1 floor violation(s) in use-core (DEFAULT mode):
[floor]   1. WIRING: use-gui/pom.xml HAS NO <profiles> ELEMENT — the upstream-oracle profile has been deleted. The upstream JUnit 3/4 oracle cannot be activated in this module and -Pupstream-oracle would silently collect the default build's tests instead (D-01).
[floor] Do NOT lower a floor to make this pass. See D-01 in docs/port2/upstream-oracle-static-review.md and harness-contract.md sec. 8.
[floor] ###############################################################
[INFO] BUILD FAILURE
[ERROR] Failed to execute goal org.codehaus.mojo:exec-maven-plugin:3.5.0:exec (upstream-oracle-floor) on project use-core: Command execution failed. Process exited with an error: 1 (Exit value: 1) -> [Help 1]
[driver] git status --porcelain:
[driver] (empty above == clean)
```

The message names the file, the element, the population (*"the upstream JUnit 3/4 oracle cannot be
activated in this module"*) and the defect id. This is the property the static review asked for, and
it holds. (a2), the same break under the profile, fails identically in `use-core` — before `use-gui`
is ever built, so the 8/17 loss is never even reached.

### 3.2 (b) All four mechanisms at once

```
----- b-core-del-oracle : mvn EXIT=1 -----
[floor] requested profiles (reactor-wide, from the command line): [upstream-oracle]
[floor] this module's upstream-oracle profile effective: false
[floor] mode: ORACLE
[floor] surefire  use-core  classes=7   (floor 40 )  methods=79   (floor 350 )  executions=79   failures=0 errors=0 skipped=0 stale-ignored=0
[floor] failsafe  use-core  classes=1   (floor 1  )  methods=1    (floor 1   )  executions=1    failures=0 errors=0 skipped=0 stale-ignored=0
[floor] vintage-only sentinel org.tzi.use.parser.USECompilerTest: absent
[floor] FAIL — 5 floor violation(s) in use-core (ORACLE mode):
[floor]   1. WIRING: use-core/pom.xml HAS NO <profiles> ELEMENT — the upstream-oracle profile has been deleted. ... (D-01).
[floor]   2. EFFECTIVENESS: -Pupstream-oracle WAS REQUESTED ON THE COMMAND LINE BUT IS NOT EFFECTIVE IN use-core. Its profile block is missing, renamed or inactive, so this module collected DEFAULT-build tests while the gate was asked for the upstream oracle. That is D-01's merge accident, and it is an error, not a pass.
[floor]   3. FLOOR use-core/surefire: 7 distinct test classes < floor 40. The upstream JUnit 3/4 tree was not collected: check that junit-vintage-engine is present at <scope>test</scope> inside use-core's upstream-oracle profile and that junit:junit is still on the test classpath.
[floor]   4. FLOOR use-core/surefire: 79 distinct test methods < floor 350. ...
[floor]   5. SENTINEL use-core: org.tzi.use.parser.USECompilerTest produced no report under -Pupstream-oracle. It extends junit.framework.TestCase, so its absence means the vintage engine was not on the test classpath — the profile was requested and did nothing.
[INFO] BUILD FAILURE
[driver] git status --porcelain:
[driver] (empty above == clean)
```

Reproduces the porter's break (b) independently, violation for violation.

### 3.3 (c) The vintage engine made unresolvable

```
----- c-vintage-unresolvable-oracle : mvn EXIT=1 -----
[INFO] BUILD FAILURE
[ERROR] Failed to execute goal on project use-core: Could not resolve dependencies for project org.tzi.use:use-core:jar:7.5.0
[ERROR] dependency: org.junit.vintage:junit-vintage-engine:jar:0.0.0-NO-SUCH-VERSION (test)
[ERROR] 	Could not find artifact org.junit.vintage:junit-vintage-engine:jar:0.0.0-NO-SUCH-VERSION in central
[driver] git status --porcelain:
[driver] (empty above == clean)
```

`grep -c '^\[floor\]'` over this log = **0**: the floor never ran. The protection here is Maven's,
not the checker's, and it fires before any test executes. The message does name the missing artifact
by coordinate, which identifies the enabling mechanism of the lost population; it does not name the
population. Adequate, and honestly not the floor's doing. The variant that matters — engine
resolvable but never on the test classpath — is the porter's break (c); I did not re-run it, and it
is the one case only a count floor can see.

### 3.4 (g) `-DskipTests`, and (e) `-Dexec.skip=true`

`-DskipTests` under the profile:

```
----- g-skiptests-oracle : mvn EXIT=1 -----
[floor] mode: ORACLE
[floor] surefire  use-core  classes=0   (floor 40 )  methods=0    (floor 350 )  executions=0    failures=0 errors=0 skipped=0 stale-ignored=0
[floor] failsafe  use-core  classes=0   (floor 1  )  methods=0    (floor 1   )  executions=0    failures=0 errors=0 skipped=0 stale-ignored=0
[floor] FAIL — 7 floor violation(s) in use-core (ORACLE mode):
[floor]   1. FLOOR use-core/surefire: the report directory does not exist (.../use-core/target/surefire-reports). Nothing was collected. 0 is rejected outright (harness-contract.md sec. 8 step 2).
[floor]   2. FLOOR use-core/surefire: 0 distinct test classes < floor 40. ...
[floor]   4. FLOOR use-core/failsafe: the report directory does not exist ... 0 is rejected outright ...
[floor]   7. SENTINEL use-core: org.tzi.use.parser.USECompilerTest produced no report under -Pupstream-oracle. ...
[INFO] BUILD FAILURE
```

Confirms the deliberate behaviour change of §5.1.2: `mvn verify -DskipTests` now fails. `0` is
rejected outright, as the contract requires.

`-Dexec.skip=true` does **not** silence the check — POM `<configuration>` beats the user property,
exactly as `use-core/pom.xml:362,378` intends:

```
----- e-execskip-default : mvn EXIT=1 -----
[floor] FAIL — 1 floor violation(s) in use-core (DEFAULT mode):
[floor]   1. WIRING: use-gui/pom.xml HAS NO <profiles> ELEMENT — ... (D-01).
[INFO] BUILD FAILURE
```

### 3.5 (d) **F-02 (MAJOR) — a mistyped profile id is a green, vacuous gate**

Tree completely intact. One character wrong on the command line.

```
----- d-typo-profile : mvn EXIT=0 -----
[floor] ===== upstream-oracle floor check: use-core =====
[floor] requested profiles (reactor-wide, from the command line): [upstream-oracle-typo]
[floor] this module's upstream-oracle profile effective: false
[floor] mode: DEFAULT
[floor] surefire  use-core  classes=7   (floor 7  )  methods=79   (floor 79  )  executions=79   failures=0 errors=0 skipped=0 stale-ignored=0
[floor] failsafe  use-core  classes=1   (floor 1  )  methods=1    (floor 1   )  executions=1    failures=0 errors=0 skipped=0 stale-ignored=0
[floor] vintage-only sentinel org.tzi.use.parser.USECompilerTest: absent
[floor] PASS — use-core met every pinned floor in DEFAULT mode.
[floor] ===== upstream-oracle floor check: use-gui =====
[floor] requested profiles (reactor-wide, from the command line): [upstream-oracle-typo]
[floor] mode: DEFAULT
[floor] surefire  use-gui   classes=1   (floor 1  )  methods=1    (floor 1   )  executions=1    failures=0 errors=0 skipped=0 stale-ignored=0
[floor] failsafe  use-gui   classes=1   (floor 1  )  methods=129  (floor 129 )  executions=129  failures=0 errors=0 skipped=0 stale-ignored=0
[floor] vintage-only sentinel org.tzi.use.gui.views.diagrams.util.DirectedLineTest: absent
[floor] PASS — use-gui met every pinned floor in DEFAULT mode.
[INFO] BUILD SUCCESS
[WARNING] The requested profile "upstream-oracle-typo" could not be activated because it does not exist.
[driver] no tree edit to restore
[driver] git status --porcelain:
[driver] (empty above == clean)
```

Exit 0. The floor says **PASS** twice. **The 40 classes / 287 methods the profile exists to revive
were not collected** (`upstream-oracle-profile.md` §4.4), and the operator asked for them. The only signals are two `[WARNING]` lines, at
line 2 and line 1487 of a 1487-line log, and the words `mode: DEFAULT` in a `[floor]` block that
otherwise reads like a success.

This is not a new discovery in this project — it is **RB-1 (MAJOR)**, raised by the empirical
refutation at `upstream-oracle-verification.md:1112`, titled *"the gate is green when it is not
running"*, and measured there with `-Pupstream-oracl`:

> ```
> $ mvn -B -pl use-core test -Pupstream-oracl -Djava.awt.headless=true      # one character short
> [WARNING] The requested profile "upstream-oracl" could not be activated because it does not exist.
> [INFO] Tests run: 79, Failures: 0, Errors: 0, Skipped: 0
> [INFO] BUILD SUCCESS
> EXIT=0
> ```
> A mistyped `-P` id, a `settings.xml` that swallows the profile, an offline resolution failure of the
> vintage jar, or a future jupiter bump that skews the platform train, all land in the same place:
> `BUILD SUCCESS`, exit 0, and the vacuous 210-method default gate — with at most one `[WARNING]` in a
> 1300-line log.

**Three things make this a defect of this round and not merely an inherited one.**

1. **The floor closes the other half of RB-1 and not this one, and the record does not say so.**
   `git grep -n 'RB-1' -- docs/` returns exactly two hits, both inside the file that raised it. The
   floor commit, `upstream-oracle-profile.md` §5.1, and `harness-contract.md` §0 never mention RB-1.
   §5.1.3's seven "what the floor cannot catch" items — the section whose own heading says *"a limit
   nobody wrote down is a false claim"* — do not include it. `git grep -iE 'typo|orcale'` over
   `upstream-oracle-profile.md` and `harness-contract.md` returns nothing. The porter disclosed this
   hazard to me in its handover and did not write it down.
2. **RB-1's recommended guard was not taken and is not marked as not-taken.**
   `upstream-oracle-verification.md:1150-1152` recommends *"add `-Dtest=TypeTest
   -DfailIfNoTests=true` (or `-Dsurefire.failIfNoSpecifiedTests=true`) as a one-line canary run
   before the gate proper: under the profile it passes 38 tests; with the profile inactive for any
   reason it fails the build instead of printing 0"*. That guard **does** catch the typo, because a
   canary invoked with the same mistyped `-P` collects nothing and fails. It was not adopted, and
   this is precisely the "not-taken recommendation" class the D-04..D-07 sweep exists to mark.
3. **The porter's stated reason for not enforcing it is refutable.** The handover says enforcing it
   "would mean the checker knowing every legitimate profile id, which it cannot". It already text-parses
   both poms for `<id>upstream-oracle</id>` (`UpstreamOracleFloor.java:320`); collecting every `<id>`
   declared under `<profiles>` in the reactor poms and failing on a requested id that matches none is
   the same parse. On this machine the pom set is the complete authority — verified:
   ```
   $ ls -la .mvn            -> ls: cannot access '.mvn': No such file or directory
   $ ls -la ~/.m2/settings.xml -> ls: cannot access '/home/xoruser/.m2/settings.xml': No such file
   $ echo "MAVEN_OPTS=[$MAVEN_OPTS] MAVEN_ARGS=[$MAVEN_ARGS]"  -> MAVEN_OPTS=[] MAVEN_ARGS=[]
   ```
   and `upstream-oracle-verification.md` §11 already records "no activation block and no `settings.xml` exist, so off-by-default is structural". Even if a
   future machine had one, an explicit allow-list property would keep the check enforceable.

### 3.6 (f) What break (f) revealed — the lead to F-01

I aimed `-Dexec.args=--stamp=true -Dexec.executable=/bin/true` at the plugin, expecting the POM to
win on both. It won on one:

```
[INFO] --- exec:3.5.0:exec (upstream-oracle-floor-stamp) @ use-core ---
Unrecognized option: --stamp=true
Error: Could not create the Java Virtual Machine.
Error: A fatal exception has occurred. Program will exit.
[ERROR] Command execution failed.
[ERROR] Failed to execute goal org.codehaus.mojo:exec-maven-plugin:3.5.0:exec (upstream-oracle-floor-stamp) on project use-core: ...
```

Read it carefully. `<executable>` held — the process launched was still `java`, not `/bin/true`. But
the JVM received `--stamp=true` as a **JVM option**, which means `-Dexec.args` **replaced the POM's
`<arguments>` list entirely** (`use-core/pom.xml:364,380`). exec-maven-plugin's `commandlineArgs`
parameter carries the user property `exec.args` and takes precedence over `arguments`. It failed
closed here only because the resulting command line happened to be garbage.

### 3.7 (m/n) **F-01 (MAJOR) — the floor is silenceable from the command line**

If `exec.args` controls the argument list, then any argument list that makes `java` exit 0 disables
the check. `-version` does.

```
################ R-m1 BYPASS: -Dexec.args=-version on an INTACT tree, DEFAULT command ################
----- m1-execargs-version-intact : mvn EXIT=0 -----
[driver] number of [floor] lines in the log: 0
[INFO] BUILD SUCCESS
[driver] git status --porcelain:
[driver] (empty above == clean)

################ R-m2 BYPASS + the D-01 MERGE ACCIDENT: use-gui profile deleted, DEFAULT ################
[driver] removed <profiles> from use-gui/pom.xml
 use-gui/pom.xml | 16 ----------------
 1 file changed, 16 deletions(-)
----- m2-execargs-version-gui-deleted : mvn EXIT=0 -----
[driver] number of [floor] lines in the log: 0
[INFO] BUILD SUCCESS
[driver] git status --porcelain:
[driver] (empty above == clean)

################ R-n BYPASS under the ORACLE command, tree intact ################
----- n-execargs-version-oracle : mvn EXIT=0 -----
[driver] number of [floor] lines in the log: 0
[INFO] BUILD SUCCESS
```

**Zero `[floor]` lines in all three.** The stamp is not written, no counts are read, no wiring is
checked, no sentinel is looked for, and both acceptance commands print `BUILD SUCCESS`. R-m2 is D-01's
merge accident restored to green: the pom deletion is present during that run, exactly as it would be
after the accident, and the build no longer objects. In R-m1 and R-n nothing is edited at all — the
tree is clean *during* the run, and the gate still does not execute.

The record makes the opposite claim, in three places:

* `use-core/pom.xml:345` — *"`<skip>false</skip>` is pinned here so `-Dexec.skip` cannot silence it."*
* `upstream-oracle-profile.md` §5.1.2 — *"`<skip>false</skip>` is still pinned"* among the wiring assertions.
* the porter's design note — *"`<skip>false</skip>` is pinned in the POM so `-Dexec.skip=true` cannot
  silence it (POM config beats a user property)"*, and *"it is the only shape that fails by itself"*.

Each statement is true of `skip` and false of the check. One unpinned user property of the same
plugin defeats it. The wiring check cannot see this: it inspects pom text, and the pom is untouched.

**The fix is one element per pom, and my own evidence establishes the mechanism.** Break (f) proves
POM `<configuration>` beats the user property for `executable`; the same rule applies to
`commandlineArgs`. Declaring `<commandlineArgs>` in both floor executions (with the argument string
the executions need) makes `exec.args` inert. Verifying that fix is the porter's job, not mine — I
report that the hole is real and that the mechanism for closing it is demonstrated in this file.

### 3.8 (h) **F-03 (MINOR) — `-pl` prints PASS for half a gate**

```
----- h-pl-usecore-oracle : mvn EXIT=0 -----
[floor] requested profiles (reactor-wide, from the command line): [upstream-oracle]
[floor] this module's upstream-oracle profile effective: true
[floor] mode: ORACLE
[floor] surefire  use-core  classes=40  (floor 40 )  methods=350  (floor 350 )  executions=938  failures=0 errors=0 skipped=0 stale-ignored=0
[floor] failsafe  use-core  classes=1   (floor 1  )  methods=1    (floor 1   )  executions=1    failures=0 errors=0 skipped=0 stale-ignored=0
[floor] vintage-only sentinel org.tzi.use.parser.USECompilerTest: collected
[floor] PASS — use-core met every pinned floor in ORACLE mode.
[INFO] BUILD SUCCESS
```

`use-gui`'s 8/17 and 1/129 were never built and never checked. `-pl` is a deliberate flag, not a
merge accident, so this is MINOR — but the log's last word is an unqualified `PASS` and a reader
sampling the log cannot tell a whole gate from a half one. The same interpolation the checker already
uses for `${session.request.activeProfiles}` exposes `${session.request.selectedProjects}`; asserting
it empty, or printing `PARTIAL` instead of `PASS`, closes it.

### 3.9 (l) The documented residual hole — confirmed real

```
[driver] removed exec-maven-plugin from use-core/pom.xml   (42 deletions)
[driver] removed exec-maven-plugin from use-gui/pom.xml    (42 deletions)
[driver] removed <profiles> from use-gui/pom.xml           (58 deletions)
----- l-both-exec-deleted-default : mvn EXIT=0 -----
[INFO] BUILD SUCCESS
[driver] git status --porcelain:
[driver] (empty above == clean)
```

Exactly as `upstream-oracle-profile.md` §5.1.3 item 3 states: nothing outside the two poms defends
the wiring check. Honestly disclosed, correctly scoped, and a three-file visible edit. **Not** filed
as a defect of this round — it is a documented limit that I confirm rather than discover. F-01 is the
same hole reachable without touching a file.

---

## 4. Can the floor be satisfied without the upstream tests running?

Six clause-level tests, run **standalone** against a byte copy of the accepting oracle run's
`use-core/target`, because the three clauses below cannot be reached from a build without editing an
upstream test, which ground rule 2 forbids. Full output in `scratchpad/clauses.out`; the checker was
invoked as
`java scripts/UpstreamOracleFloor.java --module=use-core --module-dir=<copy> --reactor-root=<repo> --requested='[upstream-oracle]' --effective=true`.

```
################ CLAUSE 1 — control: unmodified snapshot must PASS ################
[floor] surefire  use-core  classes=40  (floor 40 )  methods=350  (floor 350 )  executions=938  failures=0 errors=0 skipped=0 stale-ignored=0
[floor] PASS — use-core met every pinned floor in ORACLE mode.
[clause] exit=0

################ CLAUSE 2 — one upstream test recorded as FAILED (failures=1) ################
[driver] doctored TEST-org.tzi.use.parser.USECompilerTest.xml -> failures="1" (what -Dmaven.test.failure.ignore=true would leave behind)
[floor] surefire  use-core  classes=40  (floor 40 )  methods=350  (floor 350 )  executions=938  failures=1 errors=0 skipped=0 stale-ignored=0
[floor] FAIL — 1 floor violation(s) in use-core (ORACLE mode):
[floor]   1. FLOOR use-core/surefire: failures=1 errors=0, both must be 0.
[clause] exit=1

################ CLAUSE 3 — one upstream test SKIPPED (skipped=1) ################
[floor] FAIL — 1 floor violation(s) in use-core (ORACLE mode):
[floor]   1. FLOOR use-core/surefire: skipped=1, must be 0 — a skipped test is silence, which is the defect this check exists to abolish.
[clause] exit=1

################ CLAUSE 4 — freshness stamp DELETED ################
[floor] surefire  use-core  classes=0   (floor 40 )  methods=0    (floor 350 )  executions=0    failures=0 errors=0 skipped=0 stale-ignored=54
[floor] FAIL — 6 floor violation(s) in use-core (ORACLE mode):
[floor]   1. FRESHNESS: no upstream-oracle-floor.stamp in .../target. The `upstream-oracle-floor-stamp` execution did not run, so this check cannot tell reports written by THIS build from stale reports left by an earlier -Pupstream-oracle run. Counting them would be exactly the vacuous pass D-01 is about.
[clause] exit=1

################ CLAUSE 5 — every report STALE (the hazard: an old oracle run's XML) ################
[floor] surefire  use-core  classes=0   (floor 40 )  methods=0    (floor 350 )  executions=0    failures=0 errors=0 skipped=0 stale-ignored=54
[floor] failsafe  use-core  classes=0   (floor 1  )  methods=0    (floor 1   )  executions=0    failures=0 errors=0 skipped=0 stale-ignored=1
[floor] FAIL — 5 floor violation(s) in use-core (ORACLE mode)
[clause] exit=1

################ CLAUSE 6 — SEEDING ATTACK: 40 real oracle reports, stamp NEWER ################
[driver] reports pre-seeded (real, from a green oracle run) but all older than this build's stamp
[floor] surefire  use-core  classes=0   (floor 40 )  methods=0    (floor 350 )  executions=0    failures=0 errors=0 skipped=0 stale-ignored=54
[floor] FAIL — 5 floor violation(s) in use-core (ORACLE mode)
[clause] exit=1
```

**Answer: no route through the evidence.** Consequences worth stating:

* **`-Dmaven.test.failure.ignore=true` cannot buy a green gate** (clause 2). This matters beyond the
  floor: it is what makes S3's expected `TypeTest#testSupertype` failure (B5) undismissable.
* **`skipped != 0` fails** (clause 3), so an `@Ignore` on an upstream test is not a silent exemption.
* **The stale-report hazard is genuinely closed** (clauses 4-6). The porter reported observing a
  DEFAULT-mode run read `classes=40 methods=350` out of a stale directory. I did not reproduce that
  original observation, but I confirmed the guard: 54 real, valid, green oracle report files placed
  in the directory with the stamp newer than them are counted as `stale-ignored=54` and yield
  `classes=0`, and a missing stamp is itself a violation.
* **The only remaining routes to a green ORACLE gate with the population absent are F-01 and F-02**,
  and neither goes through the evidence — they stop the check from running or from being in ORACLE
  mode at all.
* Padding the counts with new Jupiter tests to clear 40/350 while the engine is absent is blocked by
  the **sentinel**: `org.tzi.use.parser.USECompilerTest` extends `junit.framework.TestCase`, so no
  engine but vintage can collect it, and the name is already taken. The porter's own caveat stands:
  the sentinels are two hand-picked names and would fail for an unrelated reason if either test were
  ever deleted upstream.

One wording nit, not filed: clause 2's message says `failures=1 errors=0, both must be 0` without
naming the class that failed. Surefire's own output names it three lines earlier, so nothing is lost.

---

## 5. Is the counting rule honest? — yes, and I got this wrong first

### 5.1 The rule, checked against the authoritative attribute

The floor counts a **class** as a report file whose root `<testsuite>` holds at least one
`<testcase>`, and **methods** as distinct `<testcase>` `@name` values within that file
(`UpstreamOracleFloor.java:391-441`). Checked against the `<testsuite tests="…">` header, which
surefire writes from the runner and which no aggregator re-entry inflates:

```
use-core/surefire, over the 40 method-bearing files:
  sum of <testsuite tests="..">      = 350
  sum of per-file distinct @name     = 350   <-- the floor's rule
  disagreements: NONE
  the 14 zero-testcase files and their header tests= :
     org.tzi.use.AllTests tests= 0
     org.tzi.use.graph.AllTests tests= 0
     ... (all 14 AllTests aggregators, every one tests=0)
```

Exact agreement on all 40 files, and the 14 excluded files are exactly the 14 JUnit-3 `AllTests`
aggregators, each declaring `tests="0"`. **497 is an honest "distinct methods over 50 method-bearing
classes".** The `executions=938` line the checker prints is the `<testcase>` element count, correctly
labelled *executions* and never used as a floor.

### 5.2 A wrong conclusion I reached and then refuted, kept because it nearly became a defect report

My first cross-check deduplicated `<testcase>` by `(classname, @name)` across the whole reactor and
got **464**, against the floor's 497 — a gap of 33. `upstream-oracle-verification.md:833`
independently says *"Treat 198 (not 210) and 464 (not 497) as the asserting-method figures"*, with
`497 − 33 = 464` and `210 − 12 = 198`. I was one step from filing "the floor pins a mislabelled
quantity, and the prior refutation already computed the right one".

It is wrong, twice over.

```
$ grep -o '<testcase name="[^"]*" classname="[^"]*"' TEST-org.tzi.use.uml.ocl.type.TypeTest.xml | head -2
<testcase name="testIsKindOfEnum" classname="testIsKindOfEnum"
<testcase name="testIsKindOfEnum" classname="testIsKindOfEnum"
$ head -c 600 TEST-org.tzi.use.uml.ocl.type.TypeTest.xml | ...
<testsuite ... name="org.tzi.use.uml.ocl.type.TypeTest" ... tests="38" errors="0" skipped="0" failures="0">
$ grep -c '<testcase' TEST-org.tzi.use.uml.ocl.type.TypeTest.xml
152
```

1. For a vintage JUnit-3 `TestCase`, surefire writes `classname` **equal to the method name**, so
   `(classname, @name)` is degenerate — it collapses same-named methods of different classes and
   measures nothing. `tests="38"` is the truth; 152 is 38 methods × 4 entries (direct run plus three
   `AllTests` re-entries), and the floor's per-file distinct-name rule recovers 38 correctly.
2. The prior refutation's 464 is **not** a deduplication figure at all. It is 497 minus 33 methods
   that *cannot fail* — a coverage statement, not a counting statement (`§4.6` caveat 1's narrower
   version: 21 of the revived 287 assert nothing, upper bound 266). My arriving at 464 by an
   unrelated route is a numerical coincidence, and treating it as corroboration would have been the
   exact error this project keeps catching.

**No defect on the counting rule.** The 464 does, however, matter — see F-04.

---

## 6. The documentation sweep — independent `git grep`

I re-ran the D-04..D-07 categories from scratch. **Three of the four categories are clean.**

* **Bug-for-bug (B7).** Every occurrence is either marked not-taken or is a conditional about what a
  bug-compatible port *would* require (`specification.md:173,188,2621`, `foundation-verdict.md:243,262`,
  `harness-contract.md:101,727`, `spec-parts/10-values.md:609,614,960`,
  `spec-parts/16-modernization-ledger.md:39,48,51`, `spec-parts/20-ops-UInteger.md:210-212`,
  `stage-02.md:360`). One exception: F-06 below.
* **SBoolean scope (B2).** `spec-parts/19-open-questions.md:432-448` carries the reversal in place —
  option 1 struck through and labelled *"recommended in this file, NOT taken"*, option 3 marked
  *"← DECIDED 2026-08-17 (B2). This is the plan."* `foundation-verdict.md:245,257`,
  `harness-contract.md:103`, `specification.md:174,183` all mark it. **Clean.** No document describes
  SBoolean as scope-limited without marking it.
* **H14 coverage (prose-only).** `foundation-verdict.md:244,269`, `harness-contract.md:102,333,423,666`,
  `stage-02.md:362` all mark prose-stated domains as the not-taken position and point at
  `h14-coverage-design.md`. **Clean.**
* **Acceptance commands.** `harness-contract.md` §0.1 and `specification.md` C1 now carry both, with
  the floor table. Historical records of past single-command runs (`foundation-verdict.md:70`,
  the `stage-01-*` series) are dated evidence and correctly left alone. One exception: F-05 below.

### 6.1 F-04 (MINOR) — the asserting-method condition reaches the normative file nowhere

`upstream-oracle-verification.md:833`, in a section headed *"Conditions the gate should be operated
under"*:

> * Treat **198** (not 210) and **464** (not 497) as the asserting-method figures if the gate is ever
>   used to argue coverage; 12 and 33 methods respectively cannot fail (R-5).

```
$ git grep -nE '\b464\b' -- docs/port2/ | grep -v 'differential/'
docs/port2/b7-fix-plan.md:1178:sed -n '464,475p' $H/HistoricalOracle.java     # supports()
docs/port2/h14-coverage-design.md:109:  ... (`specification.md:464`)
docs/port2/h14-coverage-design.md:688:  ... (`specification.md:464` — the class is ...)
docs/port2/spec-parts/12-expressions.md:400:  ... (T:460-464)
docs/port2/spec-parts/20-ops-SBoolean.md:389:  ... `eval` L460-464 -> ...
docs/port2/spec-parts/20-ops-SBoolean.md:755:  ... `SBooleanValue.applyOn(Value)` L461-464 ...
docs/port2/stage-01-static-review-post-fix.md:360:`HistoricalOracle.invoke` :464-470 ...
docs/port2/stage-01-static-review-round4.md:105:| `isClean()` | `:462-464` | ...
docs/port2/stage-01-static-review-round4.md:405:(`HistoricalOracle.java:464-474`); ...
docs/port2/upstream-oracle-verification.md:833:* Treat **198** (not 210) and **464** (not 497) as the asserting-...
```

Ten hits. Nine are source line-number references that happen to contain the digits. **One** is the
figure, and it is in the file that raised it.

One line, in the file that raised it. `harness-contract.md` §0.1 is now normative, is now the file a
stage is bound by, and carries the floor table plus the instruction to quote 210 and 497 — with no
mention that 12 and 33 of those methods cannot fail. `upstream-oracle-profile.md` §5.1.3 item 1
carries only the narrower 21-of-287 / 266 figure for the revived subset, not the whole-population
33/464 nor the default build's 12/198. A stage that quotes 497 as evidence of scrutiny rather than of
discovery is making a claim the record already knows is 33 methods too strong.

### 6.2 F-05 (MINOR) — a single-command acceptance directive still addressed to S3–S10

`stage-00-baseline.md:100-101`:

> **Consequence for every later stage: the acceptance command is `mvn verify`, not `mvn test`.**
> Every "suite green" claim in S3–S10 must be made against `mvn verify`.

Bolded, addressed to exactly the stages B3 governs, and `-Pupstream-oracle` appears nowhere on the
page. This is D-07's shape — *"a stage held to this file would have run the wrong gate"* — in a file
the sweep did not open: `f5ffd520` touched 10 documents and `stage-00-baseline.md` is not among them.
Same page, `:107` and `:110` still read *"the upstream test tree is ~93% dormant"* and *"**38 of the
41 never execute.**"*, which the profile has made false. (The porter lists D-08 — §1's "59 JUnit 3/4
sources" against a union of 47 — as knowingly unfixed; this is the same class in a different file and
is not listed.)

### 6.3 F-06 (MINOR) — B7's reversed recommendation stated as instruction

`spec-parts/12-expressions.md:161`, on `ExpConstUReal`'s missing type guard:

> Flag this in the port; **the fix is out of scope for a faithful copy** but should be listed as a
> known defect.

B7 is *FIX the historical defects, documenting each*. "Out of scope for a faithful copy" is the
not-taken recommendation, unmarked, in a per-row instruction a stage acts on. Two neighbours in the
same corpus got the treatment this line did not: `spec-parts/10-values.md:609` and
`spec-parts/20-ops-UInteger.md:210`.

---

## 7. Did the fix disturb the default build? — no

**Vintage absence proved from the resolved classpath, not from counts:**

```
$ mvn -B dependency:list          (default build, no -P)
$ grep -c vintage <log>
0
$ grep -iE 'junit' <log> | sort -u
junit:junit:jar:4.13.1:test -- module junit [auto]
junit:junit:jar:4.13.2:test -- module junit [auto]
org.junit.jupiter:junit-jupiter-api:jar:5.7.0:test -- module org.junit.jupiter.api
org.junit.jupiter:junit-jupiter-engine:jar:5.7.0:test -- module org.junit.jupiter.engine
org.junit.jupiter:junit-jupiter-params:jar:5.7.0:test -- module org.junit.jupiter.params
org.junit.jupiter:junit-jupiter:jar:5.7.0:test -- module org.junit.jupiter
org.junit.platform:junit-platform-commons:jar:1.7.0:test -- module org.junit.platform.commons
org.junit.platform:junit-platform-engine:jar:1.7.0:test -- module org.junit.platform.engine
com.tngtech.archunit:archunit-junit5-*:jar:1.3.0:test  (4 artifacts)
```

`junit:junit` 4.13.1/4.13.2 are on the test classpath transitively, as they always were; **no
vintage engine**, so the JUnit 3/4 tree compiles and is not collected. The floor's own sentinel
agrees from the other direction, in every DEFAULT run: `vintage-only sentinel
org.tzi.use.parser.USECompilerTest: absent`.

**Exactly 210 methods, 0 failures**, and unchanged by the floor's introduction:

```
[INFO] Tests run: 79, Failures: 0, Errors: 0, Skipped: 0      <- use-core surefire
[INFO] Tests run: 1,  Failures: 0, Errors: 0, Skipped: 0      <- use-gui  surefire
[INFO] Tests run: 1,  Failures: 0, Errors: 0, Skipped: 0      <- use-core failsafe (OCLExpressionIT)
[INFO] Tests run: 129, Failures: 0, Errors: 0, Skipped: 0     <- use-gui  failsafe (ShellIT)
[INFO] BUILD SUCCESS
```

No test file was added this round by the porter and none by me, so the 210 is unmoved by
construction as well as by measurement.

**Ground rule 2:**

```
$ git diff --name-status 30d480db..HEAD -- '*/src/main/*'
(empty)
$ git diff --name-status 30d480db..HEAD | grep -E 'src/test|src/it'
A	use-core/src/test/.../differential/*.java        (19 files, all A)
A	use-core/src/test/resources/historical/atenearesearchgroup.uncertainty.jar
```

Every entry is `A`. No `M` on any pre-existing test anywhere on the branch.

---

## 8. No regression of the harness

Measured in `z1` (default) and `z2` (oracle), both after the break suite:

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
...
  P0-perfect                                0          0       74          0            0
  P12-boxed-primitive                       0          0       74       3445           0
  P13-factory-typed-adapter                 0          0       74       3445        3445
  P14-observing-adapter                     0          0       74          0            0
stage passes         control 74 -> boxed 74; lost 0: []
golden (matched)     .../docs/port2/differential/s1-smoke-ureal-add.tsv
golden (matched)     .../docs/port2/differential/s1-smoke-ureal-minus-faulty.tsv
[INFO] Tests run: 10, ... -- in Unwritten-port invariant
[INFO] Tests run: 35, ... -- in Differential harness regressions
[INFO] Tests run: 7,  ... -- in Detection power: subtle infidelities in a ported U-type
[INFO] Tests run: 9,  ... -- in HistoricalOracle class-loader isolation
[INFO] Tests run: 6,  ... -- in Uncertainty differential smoke
```

Control 0 divergence, 74 stage passes, both goldens matched, all five harness classes green.

**Determinism, four runs, two modes** — md5 over every numeric harness line
(`seed|operations|rows|measured rows|agreement rows|verdict tally|diverging operations|stage passes|literals the subject holds`):

```
e3bb57d0491e2a69e0cedfb3c7e9c04d  R1-default.log
e3bb57d0491e2a69e0cedfb3c7e9c04d  R2-oracle.log
e3bb57d0491e2a69e0cedfb3c7e9c04d  brk-z1-final-default.log
e3bb57d0491e2a69e0cedfb3c7e9c04d  brk-z2-final-oracle.log
```

Byte-identical across runs **and** across modes: the profile and the floor perturb no harness number.

**Goldens unchanged and pinned:**

```
$ git diff --stat 9986e0e0..HEAD -- docs/port2/differential/     -> (empty)
$ git status --porcelain docs/port2/differential/                 -> (empty)
$ sha256sum docs/port2/differential/*.tsv
f66af22251fe3a0ebaa1f55e42c019a138e13b8beb23f660361e8b781d671059  s1-smoke-ureal-add.tsv
a4c3eb7ea17c9ce066069d86bee6c891712364d6a6fc56f4aa4f4e09e354e68b  s1-smoke-ureal-minus-faulty.tsv
```

Both hashes are recorded in `upstream-oracle-verification.md`. Last legitimate refresh was `1ec7d59f`
(H21), with a stated reason. **No refresh happened this round and none was requested.**

---

## 9. Concurrency

PID 494057 is still armed — a loop that fires `mvn -B clean test` after 60 s of Maven silence. It did
**not** fire during any of my 17 runs; `pgrep -f '[c]lassworlds.launcher.Launcher'` was captured
before every one and returned nothing but my own shell. `git status --porcelain` was captured before
and after every run and every restore, and was empty every time. `docs/port2/differential/*.tsv` are
byte-identical to `9986e0e0`, so the golden-refresh run an earlier review observed did not recur.
**No foreign modification was observed and none was committed.**

One live hazard worth recording for whoever runs next: that loop's `mvn -B clean test` stops short of
`verify`, so it **writes the freshness stamp and never runs the floor check** — a green `clean test`
from that loop is not evidence about the gate.

---

## 10. What I did not verify

* I did not re-run the porter's break (c) (vintage declared `<type>pom</type>`) — the one case only a
  count floor can see. My break (c) is the harder resolution failure. The porter's paste and my (b)
  together make the count floor's behaviour credible, but that specific configuration is the porter's
  measurement, not mine.
* I did not reproduce the porter's original observation of a stale-directory DEFAULT run reading
  `classes=40 methods=350`. I verified the guard instead (§4, clauses 4-6).
* I did not verify that pinning `<commandlineArgs>` closes F-01. I established the mechanism
  (POM beats user property, proved for `executable` in break (f)) and leave the fix and its proof to
  the porter.
* I did not re-derive R-5's 33 and 12 non-asserting methods, nor `§4.6`'s 21/266. F-04 is about those
  figures' *absence from the normative file*, not about their correctness.
* I did not audit D-08..D-14, which the porter lists as knowingly out of scope, except where F-05
  overlaps D-08's class.
* I did not re-check the static review's own arithmetic in its §2.1.

---

## 11. Verdict

**`DEFECTIVE`** — and the distance travelled since the last round should not be understated. The gate
went from a human-read number to a build-enforced, per-module, per-tier, freshness-guarded floor with
a vintage-only sentinel, pinned to literals that provably predate the run they validate, that cannot
be satisfied by stale or seeded evidence, that fails on `-DskipTests`, on `failures != 0`, on
`skipped != 0`, on `-Dexec.skip=true`, and that catches D-01's merge accident from the **default**
command with no profile requested. Of my thirteen experiments, **seven fail correctly**, most naming
the lost population and the defect id in the failure text; six reach a green build — three of those
six are the one `exec.args` bypass (F-01), one is the mistyped id (F-02), one is `-pl` (F-03), and one
is the residual hole the porter had already documented.

It is `DEFECTIVE` because the specific property the last round was convened to establish does not yet
hold universally. `-Dexec.args=-version` reduces both acceptance commands to `BUILD SUCCESS` with
zero floor lines and a clean tree, while the record asserts in three places that the check cannot be
silenced from the command line; and `-Pupstream-oracle-typo` yields a green `PASS` with the 40 revived
upstream classes uncollected, which is RB-1 — a MAJOR already on file, whose surviving half is written in no
document and whose recommended guard is not marked as not taken. §8's own rule — *"a floor chosen
after the run is not a floor"* — has a sibling this round tests: **a floor that can be told not to
run is not a floor either.**

Neither defect needs new machinery. F-01 is one `<commandlineArgs>` element per pom. F-02 is an
`<id>`-harvest over reactor poms the checker already parses, plus the paragraph in §5.1.3 that should
have been written either way.
