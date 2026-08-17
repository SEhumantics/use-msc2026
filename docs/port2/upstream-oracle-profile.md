# `-Pupstream-oracle` — making upstream's own test tree the oracle

**Decision B3, decided by the user on 2026-08-17. Recommendation `(b)` was taken.**
Built on branch `port-uncertainty-2`, commit `e3668a04` (build config) with the H21 behaviour change
in `1ec7d59f`. Java `openjdk 21.0.11`, Maven `3.9.16`, surefire `3.5.4`, failsafe `2.22.2`.

---

## 1. What the profile does, and what it deliberately does not do

It adds **one dependency block per module** — `org.junit.vintage:junit-vintage-engine:5.7.0`, scope
`test` — to `use-core/pom.xml` and `use-gui/pom.xml`, inside a profile that is **off by default**.

```xml
<profiles>
    <profile>
        <id>upstream-oracle</id>
        <dependencies>
            <dependency>
                <groupId>org.junit.vintage</groupId>
                <artifactId>junit-vintage-engine</artifactId>
                <version>5.7.0</version>
                <scope>test</scope>
            </dependency>
        </dependencies>
    </profile>
</profiles>
```

That is the whole change. **No test file is edited, migrated or renamed** — that is the entire reason
this option was chosen over a JUnit 5 migration. The previous port on `origin/main` migrated them by
hand (`TypeTest.java` at +1172/−1096), which re-authored the oracle with the same change it was
supposed to judge; `stage-00-baseline.md` §3 measures that and explains why "green" then stops
meaning "still behaves like upstream USE".

**Why anything was needed.** Upstream USE's test tree is overwhelmingly JUnit 3
(`junit.framework.TestCase`, 45 files under `use-core/src/test`) and JUnit 4 (`org.junit.Test`, 14
files). The 7.5.0 reactor declares only `junit-jupiter`, so surefire's JUnit Platform has no engine
that can collect any of it. Those tests are **not skipped and not reported — they are never
collected**, so 38 of the 41 `*Test.java` files present never execute and nothing says so
(`stage-00-baseline.md` §3). Two consequences the port cares about: "full suite green" was a
near-vacuous S3–S7 gate, and ground rule 4 ("never edit an upstream test") had no automatic signal
behind it at all — an edit to a dormant test changes nothing observable.

**Why a profile and not the product build.** The specified target toolchain is JUnit 5 Jupiter with
no vintage engine. The profile keeps the product build exactly that, and makes the oracle real on a
second, deliberate invocation. The cost is that the gate must be run on purpose; §5 turns that into
a rule.

**One thing the profile does not add: a JUnit 4 artefact.** `junit:junit` is already on the default
test classpath of both modules — `4.13.2` in `use-core` (transitively, via `guava-testlib`) and
`4.13.1` in `use-gui` (declared). That is why the 59 JUnit 3/4 test *sources* compile today despite
never running. The profile adds only the engine that can *discover* them.

---

## 2. The exact commands

```bash
mvn -B verify -Djava.awt.headless=true                     # default build, vintage-free
mvn -B verify -Pupstream-oracle -Djava.awt.headless=true   # + upstream's own JUnit 3/4 tree, unedited
```

Counting is done from the surefire/failsafe XML, not from the console headline, for the reason in
§4.1. The script is reproduced in full so the numbers below can be re-derived:

```python
#!/usr/bin/env python3
"""Distinct test classes / methods from surefire+failsafe XML.

Surefire's headline is a count of METHOD EXECUTIONS. Under -Pupstream-oracle the 14
JUnit-3 AllTests aggregators re-run their member classes, so a class executed by
`AllTests` and directly is written into ONE file (TEST-<class>.xml) as several
<testcase> elements with the same name. The distinct figure is the meaningful one.

Class   = the XML root's @name.
Methods = distinct <testcase> @name within that file.
"""
import glob, sys, xml.etree.ElementTree as ET

def scan(pattern):
    classes, methods, execs, f, e, s = 0, 0, 0, 0, 0, 0
    rows = []
    for p in sorted(glob.glob(pattern)):
        r = ET.parse(p).getroot()
        cls = r.get('name')
        names = [tc.get('name') for tc in r.iter('testcase')]
        distinct = len(set(names))
        f += int(r.get('failures', '0')); e += int(r.get('errors', '0'))
        s += int(r.get('skipped', '0')); execs += len(names)
        if distinct:
            classes += 1; methods += distinct
            rows.append((cls, distinct, len(names)))
    return classes, methods, execs, f, e, s, rows

grand = [0, 0, 0, 0, 0, 0]
for tier, pat in (('surefire', '{}/target/surefire-reports/TEST-*.xml'),
                  ('failsafe', '{}/target/failsafe-reports/TEST-*.xml')):
    for mod in ('use-core', 'use-gui'):
        c, m, x, f, e, s, rows = scan(pat.format(mod))
        print(f"{tier:9s} {mod:9s} classes={c:3d} methods={m:4d} executions={x:4d} "
              f"failures={f} errors={e} skipped={s}")
        for i, v in enumerate((c, m, x, f, e, s)):
            grand[i] += v
print(f"{'TOTAL':19s} classes={grand[0]:3d} methods={grand[1]:4d} executions={grand[2]:4d} "
      f"failures={grand[3]} errors={grand[4]} skipped={grand[5]}")
```

---

## 3. The default build does not change — measured twice

### 3.1 With the profile added and nothing else touched (commit `e3668a04`)

This is the measurement that answers "does the profile leak?". `mvn -B clean` first, then:

```
$ mvn -B verify -Djava.awt.headless=true
EXIT=0
[INFO] Tests run: 11, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 4.623 s -- in org.tzi.use.architecture.MavenCyclicDependenciesCoreTest
[INFO] Tests run: 7, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 1.832 s -- in Detection power: subtle infidelities in a ported U-type
[INFO] Tests run: 6, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.116 s -- in Uncertainty differential smoke
[INFO] Tests run: 10, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 42.32 s -- in Unwritten-port invariant
[INFO] Tests run: 9, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.021 s -- in HistoricalOracle class-loader isolation
[INFO] Tests run: 34, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.158 s -- in Differential harness regressions
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.134 s -- in org.tzi.use.uml.mm.ModelAPITest
[INFO] Tests run: 78, Failures: 0, Errors: 0, Skipped: 0
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.172 s - in org.tzi.use.OCLExpressionIT
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 4.094 s -- in org.tzi.use.architecture.MavenLayeredArchitectureTest
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
[INFO] Tests run: 129, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 7.144 s - in org.tzi.use.main.shell.ShellIT
[INFO] Tests run: 129, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
[INFO] Total time:  01:40 min
```

surefire `78 + 1 = 79`, failsafe `1 + 129 = 130`, **total 209 methods, 0 failures, 0 errors** —
exactly the figure the round-8 record carries. **The profile does not leak into the default build.**

### 3.2 Independent evidence for "does not leak", from the resolved classpath

The count above is circumstantial (an engine could be present and find nothing). This is direct:

```
$ mvn -B dependency:list                       | grep -c vintage   ->  0
$ mvn -B dependency:list -Pupstream-oracle     | grep -c vintage   ->  2
$ mvn -B dependency:list -Pupstream-oracle     | grep vintage | sort -u
[INFO]    org.junit.vintage:junit-vintage-engine:jar:5.7.0:test -- module org.junit.vintage.engine
```

Two lines under the profile, one per module; zero without it.

### 3.3 After the H21 change (commit `1ec7d59f`) — 209 becomes 210, and why

```
$ mvn -B clean && mvn -B verify -Djava.awt.headless=true
EXIT=0
[INFO] Tests run: 35, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.224 s -- in Differential harness regressions
[INFO] Tests run: 79, Failures: 0, Errors: 0, Skipped: 0        <- use-core surefire
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0         <- use-core failsafe
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0         <- use-gui surefire
[INFO] Tests run: 129, Failures: 0, Errors: 0, Skipped: 0       <- use-gui failsafe
[INFO] BUILD SUCCESS
[INFO] Total time:  01:42 min

$ python3 count.py
surefire  use-core  classes=  7 methods=  79 executions=  79 failures=0 errors=0 skipped=0
surefire  use-gui   classes=  1 methods=   1 executions=   1 failures=0 errors=0 skipped=0
failsafe  use-core  classes=  1 methods=   1 executions=   1 failures=0 errors=0 skipped=0
failsafe  use-gui   classes=  1 methods= 129 executions= 129 failures=0 errors=0 skipped=0
TOTAL               classes= 10 methods= 210 executions= 210 failures=0 errors=0 skipped=0
```

**209 → 210, delta +1, fully accounted for:** `Differential harness regressions` goes 34 → 35, the
single H21 regression test
(`DifferentialHarnessRegressionTest#theTypeMismatchTotalIsSplitBySubjectTypeProvenance`). Nothing
else moves. `executions == methods` in the default build: there are no JUnit-3 aggregators in it.

---

## 4. Under the profile

### 4.1 Read the deduplicated figure, not the headline

```
$ mvn -B clean && mvn -B verify -Pupstream-oracle -Djava.awt.headless=true
EXIT=0
[INFO] --- surefire:3.5.4:test (default-test) @ use-core ---
[INFO] Tests run: 938, Failures: 0, Errors: 0, Skipped: 0
[INFO] --- failsafe:2.22.2:integration-test (default) @ use-core ---
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
[INFO] --- surefire:3.5.4:test (default-test) @ use-gui ---
[INFO] Tests run: 17, Failures: 0, Errors: 0, Skipped: 0
[INFO] --- failsafe:2.22.2:integration-test (default) @ use-gui ---
[INFO] Tests run: 129, Failures: 0, Errors: 0, Skipped: 0
[INFO] Reactor Summary for use 7.5.0:
[INFO] BUILD SUCCESS
[INFO] Total time:  02:00 min
```

**`938` is a count of method *executions*, and it overcounts.** The tree contains 14
`AllTests.java` JUnit-3 aggregators (`org.tzi.use.AllTests`, `org.tzi.use.uml.AllTests`,
`org.tzi.use.uml.mm.AllTests`, …), nested, so a member class is run once directly and once per
enclosing suite. Surefire writes every execution into the one file named after the *member* class, so
`TEST-org.tzi.use.uml.ocl.type.TypeTest.xml` holds **152 `<testcase>` elements over 38 distinct
method names** — factor 4, one direct run plus three suite levels — while its own root attribute
reads `tests="38"`. The aggregator files themselves hold zero test cases:

```
$ python3 - <<EOS
import glob, xml.etree.ElementTree as ET
for p in sorted(glob.glob('use-core/target/surefire-reports/TEST-*.xml')):
    r = ET.parse(p).getroot()
    print(int(r.get("tests","0")), "tests", len(list(r.iter("testcase"))), "testcases ", r.get("name"))
EOS
(excerpt, aligned)
    0 tests     0 testcases  org.tzi.use.AllTests
   38 tests   152 testcases  org.tzi.use.uml.ocl.type.TypeTest
   55 tests    55 testcases  org.tzi.use.uml.mm.MImportedModelTest
   11 tests    55 testcases  org.tzi.use.uml.sys.soil.StatementEffectTest
    2 tests     6 testcases  org.tzi.use.parser.USECompilerTest
```

28 of `use-core`'s 40 test classes are inflated this way; the other 12, and all 8 in `use-gui`, run
once. **Deduplicated:**

```
$ python3 count.py
surefire  use-core  classes= 40 methods= 350 executions= 938 failures=0 errors=0 skipped=0
surefire  use-gui   classes=  8 methods=  17 executions=  17 failures=0 errors=0 skipped=0
failsafe  use-core  classes=  1 methods=   1 executions=   1 failures=0 errors=0 skipped=0
failsafe  use-gui   classes=  1 methods= 129 executions= 129 failures=0 errors=0 skipped=0
TOTAL               classes= 50 methods= 497 executions=1085 failures=0 errors=0 skipped=0
```

### 4.2 The headline numbers

| | default | `-Pupstream-oracle` | gain |
|---|---|---|---|
| distinct test classes | **10** | **50** | +40 |
| distinct test methods | **210** | **497** | **+287** |
| failures | 0 | **0** | — |
| errors | 0 | **0** | — |
| skipped | 0 | **0** | — |
| raw executions (console headline) | 210 | 1085 | — |
| wall clock | 01:42 | 02:00 | +18 s |

Per tier and module, deduplicated:

| tier | module | classes | methods |
|---|---|---|---|
| surefire | `use-core` | 40 | 350 |
| surefire | `use-gui` | 8 | 17 |
| failsafe | `use-core` | 1 | 1 (`OCLExpressionIT`) |
| failsafe | `use-gui` | 1 | 129 (`ShellIT`) |

### 4.3 Reconciliation against the earlier probes — every delta accounted for

`stage-00-baseline.md` §4 recorded two throwaway-worktree probes. Neither is contradicted.

| measurement | surefire classes | surefire methods |
|---|---|---|
| probe 1, `b7aaa99c`, pre-S1 (`mvn test`) | 43 | 300 |
| probe 2, `8789e035`, post-S1 (`mvn test`) | 45 | 315 |
| this profile at the pom-only commit `e3668a04`, post-S2 | 48 | 366 |
| **this profile at `1ec7d59f`, post-S2 + H21** | **48** | **367** |

* 315 → 366 is **+3 classes / +51 methods**, and that is *exactly* S2's three new harness classes:
  `PortedInfidelityDetectionPowerTest` 7 + `UnwrittenPortInvariantTest` 10 +
  `DifferentialHarnessRegressionTest` 34 = **51**. (Cross-check from the other side: the default
  surefire tier went 28 → 79 over the same interval, also +51.)
* 366 → 367 is **+1**, the H21 regression test of commit `1ec7d59f`.
* The probes ran `mvn test`, so neither included the 130 failsafe methods. 367 + 130 = **497**, and
  48 + 2 = **50 classes**.

### 4.4 What the profile revives: 40 classes, 287 methods

Eight classes / 80 methods already ran by default. The other **40 classes / 287 methods** are what
the profile buys. Distinct methods; a parenthesised figure is the inflated execution count.

```
    55  use-core org.tzi.use.uml.mm.MImportedModelTest
    38  use-core org.tzi.use.uml.ocl.type.TypeTest              (152 executions)
    13  use-core org.tzi.use.uml.ocl.expr.ExpQueryTest           (52 executions)
    12  use-core org.tzi.use.parser.soil.ASTConstructionTest     (48 executions)
    12  use-core org.tzi.use.parser.soil.StatementGenerationTest (48 executions)
    12  use-core org.tzi.use.uml.ocl.expr.ExprNavigationTest     (48 executions)
    11  use-core org.tzi.use.uml.ocl.expr.ExpStdOpTest           (44 executions)
    11  use-core org.tzi.use.uml.ocl.value.ValueTest             (44 executions)
    11  use-core org.tzi.use.uml.sys.soil.StatementEffectTest    (55 executions)
    10  use-core org.tzi.use.architecture.AntCyclicDependenciesCoreTest
     9  use-core org.tzi.use.parser.shell.ASTConstructionTest    (36 executions)
     8  use-core org.tzi.use.uml.sys.LinkTest                    (32 executions)
     8  use-core org.tzi.use.utilcore.soil.VariableEnvironmentTest (32 executions)
     6  use-gui  org.tzi.use.architecture.AntCyclicDependenciesGUITest
     6  use-core org.tzi.use.uml.mm.MAssociationClassTest        (24 executions)
     6  use-core org.tzi.use.uml.ocl.expr.NavigationTest         (24 executions)
     6  use-core org.tzi.use.utilcore.AbstractBagTest            (18 executions)
     4  use-gui  org.tzi.use.architecture.MavenCyclicDependenciesGUITest
     4  use-core org.tzi.use.uml.mm.ModelCreationTest            (16 executions)
     4  use-core org.tzi.use.uml.mm.statemachines.TestProtocolStateMachine
     4  use-core org.tzi.use.uml.sys.DeletionTest                (16 executions)
     4  use-core org.tzi.use.utilcore.StringUtilTest             (12 executions)
     4  use-core org.tzi.use.utilcore.soil.StateChangesTest      (16 executions)
     4  use-core org.tzi.use.utilcore.soil.VariableSetTest       (16 executions)
     3  use-core org.tzi.use.uml.sys.MSystemStateTest
     2  use-core org.tzi.use.graph.GraphTest                      (6 executions)
     2  use-core org.tzi.use.parser.USECompilerTest               (6 executions)
     2  use-core org.tzi.use.parser.shell.StatementGenerationTest (8 executions)
     2  use-core org.tzi.use.uml.sys.MCmdDestroyObjectsTest       (8 executions)
     2  use-gui  org.tzi.use.util.test.DiagramUtilTest
     2  use-core org.tzi.use.utilcore.CombinationTest             (6 executions)
     2  use-core org.tzi.use.utilcore.soil.SymbolTableTest        (8 executions)
     1  use-gui  org.tzi.use.architecture.AntLayeredArchitectureTest
     1  use-gui  org.tzi.use.gui.views.diagrams.util.CreationTimeRecorderTest
     1  use-gui  org.tzi.use.gui.views.diagrams.util.DirectedLineTest
     1  use-gui  org.tzi.use.gui.views.diagrams.util.DirectionTest
     1  use-core org.tzi.use.uml.mm.MMultiplicityTest             (4 executions)
     1  use-core org.tzi.use.uml.mm.statemachines.TestSignals
     1  use-core org.tzi.use.uml.ocl.expr.EvaluatorTest           (4 executions)
     1  use-core org.tzi.use.utilcore.ReportTest                  (3 executions)
```

The load-bearing ones for this port are near the top and are exactly the oracles the port must not
break: **`TypeTest` 38** (upstream's own type-conformance oracle, the one **B5** predicts will break
10 of 12 assertions in `testSupertype` the moment the fork lattice lands), **`ExpStdOpTest` 11**,
**`ValueTest` 11**, **`ExpQueryTest` 13** (relevant to **B9**), **`USECompilerTest` 2**
(corpus-driven — see the caveat in §4.6), **`ExprNavigationTest` 12**.

### 4.5 Does any upstream test FAIL under the profile?

**No. 0 failures, 0 errors, 0 skipped, `BUILD SUCCESS`.** Nothing in this round needed a waiver and
no upstream test file was touched. `grep -iE "<<< FAILURE|<<< ERROR|BUILD FAILURE"` over the full
build log returns nothing.

This is the expected result and is *not* the interesting case. The interesting case arrives at S3:
the profile exists so that when the fork's lattice lands, `TypeTest#testSupertype` **fails**, in
upstream's own words, and that failure is a decision (**B5**) rather than a silence.

### 4.6 Three honest caveats about the 287

1. **21 of the 287 contain no assertion.** Four ArchUnit classes are revived —
   `AntCyclicDependenciesCoreTest` 10, `AntCyclicDependenciesGUITest` 6,
   `MavenCyclicDependenciesGUITest` 4, `AntLayeredArchitectureTest` 1 — and each calls
   `.evaluate(…)`, never `.check(…)`:

   ```
   AntCyclicDependenciesCoreTest.java:   evaluate=1 check=0 assert=0
   AntCyclicDependenciesGUITest.java:    evaluate=1 check=0 assert=0
   MavenCyclicDependenciesGUITest.java:  evaluate=1 check=0 assert=0
   AntLayeredArchitectureTest.java:      evaluate=1 check=0 assert=0
   ```

   They pass unconditionally and write a report. This is exactly constraint **C2** of
   `specification.md`, in its new population: the count of *assertion-bearing* revived methods is
   **at most 266**, not 287. **At most**, and stated that way deliberately — 21 were measured to
   assert nothing, and the remaining 266 were *not* individually audited for assertions in this
   round, so 266 is an upper bound and not a measurement. It does not change the decision — even a
   loose 266 upstream-authored assertion-bearing methods is two orders of magnitude more signal than
   the baseline's **one** (`ModelAPITest`, per C2) — but a stage document must not quote 287 as if it
   were 287 assertions.

2. **`USECompilerTest`'s 2 methods may be vacuous, per B12.** It resolves its fixtures from
   `System.getProperty("user.dir") + "/src/test/…"`; under Maven the module root is `use-core/`, so
   the directory listing can come back `null` or empty and the loop runs zero times. It **passes**
   here, which under B12's analysis is not by itself evidence that anything was compiled. B12 is
   unchanged by this profile and still needs deciding.

3. **JUnit-3 suite ordering is now load-bearing, by construction.** The aggregators run, so
   `USECompilerUncertaintyTest`'s process-global write to
   `Options.explicitVariableDeclarations` — B12's second half — is live under this profile in a way
   it is not under the default build. Nothing observed it in this round (0 failures), and that is a
   fact about today's tree, not a guarantee.

---

## 5. THE RULE — every stage from S3 runs BOTH

> **A stage is not accepted until both of these are green, and a stage document that quotes only one
> of them has not stated its acceptance.**
>
> ```bash
> mvn -B verify -Djava.awt.headless=true
> mvn -B verify -Pupstream-oracle -Djava.awt.headless=true
> ```
>
> Quote, for each: `BUILD SUCCESS`, the deduplicated class and method counts from §2's script, and
> failures/errors/skipped. **Never quote surefire's raw headline as a method count under the
> profile** — it is an execution count and it overcounts by a factor of up to 4 (§4.1).
>
> **If an upstream test fails under the profile, that is a finding and it belongs in the stage
> document with the full failure output. It is not something to fix by editing the test.** The
> options are: change the port; or record a waiver in `upstream-test-waivers.md` naming the
> upstream assertion, why the port's behaviour is nevertheless correct, and which blocking decision
> licensed it (**B5** is the one already predicted). Ground rule 2 is unchanged and absolute:
> `git diff --name-status 30d480db..HEAD -- '*/src/main/*'` stays empty, and no upstream test file
> is ever edited.

Two properties this rule buys that the default build cannot:

* **Ground rule 4 becomes enforceable by the suite** rather than by diff review. Editing a dormant
  test changed nothing observable; editing a live one fails a build.
* **S10's non-regression step stops being a formality.** "Run upstream-main's test tree unmodified
  against the ported `use-core` and report the delta" is a **497**-method check against assertions
  upstream authored, not a 13-method one.

---

## 6. The diff — what this round touched

Ground rule 2 permits exactly two non-docs, non-differential-harness paths this round:
`use-core/pom.xml` and `use-gui/pom.xml`. Verified:

```
$ git diff --name-status 30d480db..HEAD -- '*/src/main/*'
(empty)
```

```
$ git diff --name-status 30d480db..HEAD
A	.gitattributes
M	.gitignore
A	docs/port2/audit-00-verdict.md
A	docs/port2/audit-01-harness.md
A	docs/port2/audit-02-specification.md
A	docs/port2/audit-03-acceptance.md
A	docs/port2/audit-04-wildcard.md
A	docs/port2/differential/s1-smoke-ureal-add.tsv
A	docs/port2/differential/s1-smoke-ureal-minus-faulty.tsv
A	docs/port2/foundation-verdict.md
A	docs/port2/harness-contract.md
A	docs/port2/spec-parts/10-values.md
A	docs/port2/spec-parts/11-types-oracle.sh
A	docs/port2/spec-parts/11-types.md
A	docs/port2/spec-parts/12-expressions.md
A	docs/port2/spec-parts/13-grammar.md
A	docs/port2/spec-parts/14-historical-tests.md
A	docs/port2/spec-parts/15-upstream-delta.md
A	docs/port2/spec-parts/16-modernization-ledger.md
A	docs/port2/spec-parts/17-refutation-classification.md
A	docs/port2/spec-parts/18-refutation-delta.md
A	docs/port2/spec-parts/19-open-questions.md
A	docs/port2/spec-parts/20-ops-SBoolean.md
A	docs/port2/spec-parts/20-ops-UBoolean.md
A	docs/port2/spec-parts/20-ops-UInteger.md
A	docs/port2/spec-parts/20-ops-UReal.md
A	docs/port2/spec-parts/20-ops-UString.md
A	docs/port2/specification.md
A	docs/port2/stage-00-baseline.md
A	docs/port2/stage-01-refutation-empirical.md
A	docs/port2/stage-01-refutation-fidelity.md
A	docs/port2/stage-01-refutation-isolation.md
A	docs/port2/stage-01-round6-fixes.md
A	docs/port2/stage-01-static-review-post-fix.md
A	docs/port2/stage-01-static-review-round3.md
A	docs/port2/stage-01-static-review-round4.md
A	docs/port2/stage-01-static-review-round5.md
A	docs/port2/stage-01-verification-post-fix.md
A	docs/port2/stage-01-verification-round3.md
A	docs/port2/stage-01-verification-round4.md
A	docs/port2/stage-01-verification-round5.md
A	docs/port2/stage-01-verification-round6.md
A	docs/port2/stage-01-verification-round7.md
A	docs/port2/stage-01-verification-round8.md
A	docs/port2/stage-01.md
A	docs/port2/stage-02.md
A	docs/port2/upstream-oracle-profile.md
A	docs/port2/upstream-test-waivers.md
M	use-core/pom.xml
A	use-core/src/test/java/org/tzi/use/uncertainty/differential/AcceptedDegenerateOperations.java
A	use-core/src/test/java/org/tzi/use/uncertainty/differential/AcceptedThrowPairs.java
A	use-core/src/test/java/org/tzi/use/uncertainty/differential/Candidate.java
A	use-core/src/test/java/org/tzi/use/uncertainty/differential/DiffReportWriter.java
A	use-core/src/test/java/org/tzi/use/uncertainty/differential/DiffRow.java
A	use-core/src/test/java/org/tzi/use/uncertainty/differential/DiffVerdict.java
A	use-core/src/test/java/org/tzi/use/uncertainty/differential/DifferentialHarnessRegressionTest.java
A	use-core/src/test/java/org/tzi/use/uncertainty/differential/DifferentialSweep.java
A	use-core/src/test/java/org/tzi/use/uncertainty/differential/HarnessMarshallingException.java
A	use-core/src/test/java/org/tzi/use/uncertainty/differential/HistoricalOracle.java
A	use-core/src/test/java/org/tzi/use/uncertainty/differential/HistoricalOracleIsolationTest.java
A	use-core/src/test/java/org/tzi/use/uncertainty/differential/InputGenerator.java
A	use-core/src/test/java/org/tzi/use/uncertainty/differential/IsolatedJarClassLoader.java
A	use-core/src/test/java/org/tzi/use/uncertainty/differential/PortedInfidelityDetectionPowerTest.java
A	use-core/src/test/java/org/tzi/use/uncertainty/differential/StubCandidate.java
A	use-core/src/test/java/org/tzi/use/uncertainty/differential/UOp.java
A	use-core/src/test/java/org/tzi/use/uncertainty/differential/UValue.java
A	use-core/src/test/java/org/tzi/use/uncertainty/differential/UncertaintyDifferentialSmokeTest.java
A	use-core/src/test/java/org/tzi/use/uncertainty/differential/UnwrittenPortInvariantTest.java
A	use-core/src/test/resources/historical/atenearesearchgroup.uncertainty.jar
A	use-core/src/test/resources/historical/use.jar
M	use-gui/pom.xml
```

Filtered to the non-`docs/` paths, which is where the ground rule bites:

```
$ git diff --name-status 30d480db..HEAD | grep -v '\tdocs/'
A	.gitattributes
M	.gitignore
M	use-core/pom.xml
A	use-core/src/test/java/org/tzi/use/uncertainty/differential/AcceptedDegenerateOperations.java
A	use-core/src/test/java/org/tzi/use/uncertainty/differential/AcceptedThrowPairs.java
A	use-core/src/test/java/org/tzi/use/uncertainty/differential/Candidate.java
A	use-core/src/test/java/org/tzi/use/uncertainty/differential/DiffReportWriter.java
A	use-core/src/test/java/org/tzi/use/uncertainty/differential/DiffRow.java
A	use-core/src/test/java/org/tzi/use/uncertainty/differential/DiffVerdict.java
A	use-core/src/test/java/org/tzi/use/uncertainty/differential/DifferentialHarnessRegressionTest.java
A	use-core/src/test/java/org/tzi/use/uncertainty/differential/DifferentialSweep.java
A	use-core/src/test/java/org/tzi/use/uncertainty/differential/HarnessMarshallingException.java
A	use-core/src/test/java/org/tzi/use/uncertainty/differential/HistoricalOracle.java
A	use-core/src/test/java/org/tzi/use/uncertainty/differential/HistoricalOracleIsolationTest.java
A	use-core/src/test/java/org/tzi/use/uncertainty/differential/InputGenerator.java
A	use-core/src/test/java/org/tzi/use/uncertainty/differential/IsolatedJarClassLoader.java
A	use-core/src/test/java/org/tzi/use/uncertainty/differential/PortedInfidelityDetectionPowerTest.java
A	use-core/src/test/java/org/tzi/use/uncertainty/differential/StubCandidate.java
A	use-core/src/test/java/org/tzi/use/uncertainty/differential/UOp.java
A	use-core/src/test/java/org/tzi/use/uncertainty/differential/UValue.java
A	use-core/src/test/java/org/tzi/use/uncertainty/differential/UncertaintyDifferentialSmokeTest.java
A	use-core/src/test/java/org/tzi/use/uncertainty/differential/UnwrittenPortInvariantTest.java
A	use-core/src/test/resources/historical/atenearesearchgroup.uncertainty.jar
A	use-core/src/test/resources/historical/use.jar
M	use-gui/pom.xml
```


Reading of the non-docs paths, against the ground rules:

| path | status | licensed by |
|---|---|---|
| `use-core/pom.xml`, `use-gui/pom.xml` | **M** | the B3 exception — the only pom change this round, one profile block each |
| `use-core/src/test/java/org/tzi/use/uncertainty/differential/*` | **A** | the port's own differential harness (S1), not an upstream test |
| `use-core/src/test/resources/historical/*.jar` | **A** | the vendored reference jars the harness loads (S1) |
| `.gitattributes`, `.gitignore` | **A** / **M** | S0 housekeeping, `stage-00-baseline.md` §1 |
| `docs/port2/**` | **A** / **M** | documentation |

**No upstream test file appears anywhere in it.** Every `src/test` path is inside
`org/tzi/use/uncertainty/differential/`, which this port wrote.

---

## 7. Acceptance

| Criterion | Status |
|---|---|
| profile adds vintage at test scope to `use-core` and `use-gui` | met — §1 |
| default build stays vintage-free | met — **measured two ways**: 209/210 methods unchanged (§3.1, §3.3) and 0 vintage lines on the resolved classpath (§3.2) |
| default build still 79 + 130 = 209 at pom-only state | met — §3.1, pasted |
| default build 80 + 130 = 210 after H21, delta accounted | met — §3.3, the delta is the one new test |
| profile counts reported as distinct classes/methods, deduplicated | met — §4.1, §4.2: 50 classes / 497 methods |
| profile deltas reconciled against both earlier probes | met — §4.3, every delta named |
| failures and errors reported honestly | met — §4.5: 0/0/0, and §4.6's three caveats on what the 287 is worth |
| no test file edited, migrated or renamed | met — §6; every `src/test` path in the diff is the port's own harness |
| `*/src/main/*` diff empty | met — §6 |
