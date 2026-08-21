# `-Pupstream-oracle` — making upstream's own test tree the oracle

**Decision B3, decided by the user on 2026-08-17. Recommendation `(b)` was taken.**
Built on branch `port-uncertainty-2`, commit `e3668a04` (build config) with the H21 behaviour change
in `1ec7d59f`. Java `openjdk 21.0.11`, Maven `3.9.16`, surefire `3.5.4`, failsafe `2.22.2`.

**Provenance note (2026-08-21).** `upstream-oracle-verification.md`, cited below, was consolidated
during a documentation cleanup and no longer exists as a separate file; its findings (RB-1 and the
R-1..R-6 minors) are already closed/tabulated below in §5.2.6. Full original content in git history.

> **ROUND 9, `6702f06e`: the gate now asserts its own floor, and §4.6's caveats are corrected.** A
> static refuter returned **DEFECTIVE** on this document and on the profile
> ([`upstream-oracle-static-review.md`](upstream-oracle-static-review.md), D-01…D-07); the stricter
> reading was adopted. **D-01** — the gate had no asserted floor and could revert to vacuity while
> printing `BUILD SUCCESS` — is closed by **§5.1** and demonstrated failing five ways in **§7.3**.
> **D-02** and **D-03** — two of §4.6's three "honest caveats" were false about the tree they describe
> — are corrected in place in **§4.6**, with both wrong claims kept verbatim and attributed. §1's
> "the 59 JUnit 3/4 test sources" (**D-08**, the union is 47) and the pom comment's "3 test classes out
> of the 41" (**D-09**) are **not** fixed by this round and remain open.

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

This is the measurement that answers "does the profile leak?". From a clean tree,
`mvn -B verify -Djava.awt.headless=true`: `BUILD SUCCESS`, exit 0. Surefire `78 + 1 = 79`, failsafe
`1 + 129 = 130` — **209 methods total, 0 failures, 0 errors, 0 skipped**, exactly the figure the
round-8 record carries. **The profile does not leak into the default build.**

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

From clean, `mvn -B verify -Djava.awt.headless=true`: `BUILD SUCCESS`, exit 0. Deduplicated (§2's
script): `surefire use-core 7/79`, `surefire use-gui 1/1`, `failsafe use-core 1/1`, `failsafe
use-gui 1/129` — **TOTAL 10 classes / 210 methods, 0 failures / 0 errors / 0 skipped**.

**209 → 210, delta +1, fully accounted for:** `Differential harness regressions` goes 34 → 35, the
single H21 regression test
(`DifferentialHarnessRegressionTest#theTypeMismatchTotalIsSplitBySubjectTypeProvenance`). Nothing
else moves. `executions == methods` in the default build: there are no JUnit-3 aggregators in it.

---

## 4. Under the profile

### 4.1 Read the deduplicated figure, not the headline

From clean, `mvn -B verify -Pupstream-oracle -Djava.awt.headless=true`: `BUILD SUCCESS`, exit 0,
console headline `938 + 1 + 17 + 129 = 1085` test executions — but **that headline counts method
*executions*, and it overcounts.** The tree contains 14 `AllTests.java` JUnit-3 aggregators
(`org.tzi.use.AllTests`, `org.tzi.use.uml.AllTests`, `org.tzi.use.uml.mm.AllTests`, …), nested, so a
member class is run once directly and once per enclosing suite. Surefire writes every execution into
the one file named after the *member* class, so `TEST-org.tzi.use.uml.ocl.type.TypeTest.xml` holds
**152 `<testcase>` elements over 38 distinct method names** — factor 4, one direct run plus three
suite levels — while its own root attribute reads `tests="38"`. The aggregator files themselves hold
zero test cases (e.g. `org.tzi.use.AllTests`: `0 tests, 0 testcases`; contrast
`org.tzi.use.uml.mm.MImportedModelTest`: `55 tests, 55 testcases`, not inflated). 28 of `use-core`'s
40 test classes are inflated this way; the other 12, and all 8 in `use-gui`, run once. Deduplicated
counts are in §4.2.

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

### 4.6 Three caveats about the 287 — one verified, one REFUTED, one CORRECTED

> **Round-9 correction notice.** This section was published as "three honest caveats". Two of the
> three were false about the tree they describe, and the static refuter said so
> (`docs/port2/upstream-oracle-static-review.md` §2.2, D-02, D-03). Caveat 1 is verified and stands
> unchanged. Caveat 2 is **refuted**: the class it names cannot pass vacuously. Caveat 3 is
> **substantively right but named a class that is not in the reactor**, so it had no checkable
> citation; the two real `file:line`s are now recorded. Both wrong claims are kept verbatim below
> rather than deleted, so the record shows what was claimed, who refuted it, and when.

1. **21 of the 287 contain no assertion.** Four ArchUnit classes are revived —
   `AntCyclicDependenciesCoreTest` 10, `AntCyclicDependenciesGUITest` 6,
   `MavenCyclicDependenciesGUITest` 4, `AntLayeredArchitectureTest` 1 — and each calls
   `.evaluate(…)`, never `.check(…)`, measured directly in the four source files
   (`evaluate=1 check=0 assert=0` in each). They pass unconditionally and write a report. This is
   exactly constraint **C2** of `specification.md`, in its new population: the count of
   *assertion-bearing* revived methods is **at most 266**, not 287. **At most**, and stated that way
   deliberately — 21 were measured to assert nothing, and the remaining 266 were *not* individually
   audited for assertions in this round, so 266 is an upper bound and not a measurement. It does not
   change the decision — even a loose 266 upstream-authored assertion-bearing methods is two orders
   of magnitude more signal than the baseline's **one** (`ModelAPITest`, per C2) — but a stage
   document must not quote 287 as if it were 287 assertions.

2. **~~`USECompilerTest`'s 2 methods may be vacuous, per B12.~~ — REFUTED, and this caveat was
   wrong about the class it named.**

   > **The caveat as written (kept verbatim, because deleting a refuted claim hides the
   > correction):** "`USECompilerTest`'s 2 methods may be vacuous, per B12. It resolves its
   > fixtures from `System.getProperty("user.dir") + "/src/test/…"`; under Maven the module root
   > is `use-core/`, so the directory listing can come back `null` or empty and the loop runs zero
   > times."

   **Refuted by the static refuter, `docs/port2/upstream-oracle-static-review.md` D-02 (:188-214),
   2026-08-17, and re-verified independently while fixing it.** `USECompilerTest` **cannot** pass
   vacuously and contains no `user.dir` at all (`grep -n "user.dir"
   use-core/src/test/java/org/tzi/use/parser/USECompilerTest.java` → no match). What it actually
   does is **classpath** resolution instead: `ClassLoader.getSystemResource("org/tzi/use/parser")`
   at `:77-79` (and the same for `examples` and `test_expr.in`, so the Maven working directory is
   irrelevant to fixture resolution); `fail("Folders including tests are missing!")` in the static
   initialiser at `:84` if that classpath resource is absent; `assertNotNull(files)` on the test and
   examples directories at `:293` and `:116`; and `assertEquals("make sure that all test files can
   be found …", 49, fileList.size())` at `:297-301` (`EXPECTED = 49`, `:69`). A directory listing
   that came back empty would fail on `49 != 0`, not pass.

   **Why the correction matters more than the wording.** Acting on the caveat as written meant
   looking for a defect in an upstream test that has none, and "fixing" it would be an edit to
   `USECompilerTest.java` — the one thing ground rule 3 forbids absolutely, invited by this
   document. The `user.dir` shape belongs to the **fork's** `USECompilerUncertaintyTest`, which is
   what B12/CF-8 is about everywhere else in the record (`specification.md:188`, `:2172`,
   `spec-parts/16-modernization-ledger.md:45`). B12 is untouched by this profile and still needs
   deciding — **about the fork's class, not upstream's.**

3. **JUnit-3 suite ordering is now load-bearing, by construction — the hazard is real, but the
   class this caveat named is not in the reactor. CORRECTED.**

   > **The caveat as written (kept verbatim):** "The aggregators run, so
   > `USECompilerUncertaintyTest`'s process-global write to `Options.explicitVariableDeclarations`
   > — B12's second half — is live under this profile in a way it is not under the default build."

   **Corrected after `docs/port2/upstream-oracle-static-review.md` D-03 (:216-237), 2026-08-17.**
   `USECompilerUncertaintyTest` is **not in the reactor** and is not run by this profile — it
   survives only inside the vendored historical jar
   (`use-core/src/test/resources/historical/use.jar`; `git grep -ln USECompilerUncertaintyTest --
   use-core use-gui` matches only that path). The hazard is nevertheless **live**, in two classes the
   profile *does* revive, and these are the `file:line`s the caveat should always have carried:
   `USECompilerTest.java:111` and `parser/soil/StatementGenerationTest.java:64` both set
   `Options.explicitVariableDeclarations = false`. The field defaults to `true`
   (`use-core/src/main/java/org/tzi/use/config/Options.java:138`), `StatementGenerationTest` does so
   in `setUp()` — so once per its 12 methods (`:61-64`) — and **neither class has a `tearDown` that
   restores it** (`grep -n "tearDown" …` matches in neither). Both classes are in §4.4's revived list
   (`USECompilerTest` 2 methods; `parser/soil/StatementGenerationTest` 12 methods / 48 executions),
   and the 14 `AllTests` aggregators change the order in which they run relative to everything
   else. Nothing observed the leak in this round (0 failures), and that is a fact about today's
   tree, not a guarantee — but it is now a checkable one.

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
> Quote, for each: `BUILD SUCCESS`, the deduplicated class and method counts, and
> failures/errors/skipped. **Never quote surefire's raw headline as a method count under the
> profile** — it is an execution count and it overcounts by a factor of up to 4 (§4.1).
>
> **AMENDED 2026-08-17 (static-review defect D-01). The counts are no longer a number a human reads
> off a run: the build asserts them.** The rule as first published was "quote the deduplicated class
> and method counts" and nothing checked that anything had been collected — see §5.1. Both commands
> now run `scripts/UpstreamOracleFloor.java` at phase `verify` in both test-bearing modules, and a run
> below the pinned per-module, per-tier floor **fails**. Quote the `[floor]` lines from the log; they
> are the evidence, and they are what makes "both commands green" and "the upstream methods ran" the
> same claim instead of two.
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

## 5.1 THE FLOOR — what asserts the counts, and what it cannot catch

**The defect this closes, in the refuter's words** (`upstream-oracle-static-review.md` D-01, :171-186):

> **the gate has no asserted floor; it can silently revert to vacuity and still be `BUILD SUCCESS`.**
> […] The rule in §5 is "quote … the deduplicated class and method counts" — a **human-read** number.
> *Failure scenario.* Delete the profile block from `use-gui/pom.xml` only (a plausible merge
> accident): the 7 revived `use-gui` classes / 17 methods stop being collected and **both acceptance
> commands still print `BUILD SUCCESS`, 0 failures**. […] **The upstream-oracle gate is held to a
> weaker standard than the harness the same repository built.**

*(Quoted verbatim. The review says "7 revived" in D-01 and "8 revived" in its verdict paragraph; the
collected figure is **8 classes / 17 methods**, of which `MavenLayeredArchitectureTest` is the one the
default build already ran, so 7 are newly revived. Both readings describe the same 17 methods, and 8/17
is what the floor pins.)*

That reading is adopted in full. The port has already rejected reported-but-unenforced numbers three
times (**D-15**, **D-43**, **D-11**); this was the fourth and it was in the gate itself.

### 5.1.1 What was built

`scripts/UpstreamOracleFloor.java` — a single-file Java program (JEP 330), run by Maven at phase
`verify` in `use-core` and `use-gui` (`exec-maven-plugin` 3.5.0, execution `upstream-oracle-floor`),
plus a second execution `upstream-oracle-floor-stamp` at phase `initialize`. It exits non-zero listing
**every** violation, in the shape of `requireStagePass` — read all the clauses, not the first.

**Why this design and not the two alternatives.**

* *A Jupiter test using the JUnit Platform Launcher to discover upstream tests and assert the count*
  cannot enforce the `use-gui` **counts**. A test can only live in `use-core/src/test` — **ground rule 2
  forbids adding anything under `use-gui/src`** — and from there it can see neither `use-gui`'s test
  classpath nor `use-gui`'s reports, which do not exist yet when `use-core` runs. It also could not see
  the failsafe tier at all.

  > **CORRECTED 2026-08-17 (round 11, defect F-01).** The rest of this bullet used to read *"a new test
  > class would change the very counts being asserted: the default build must stay at **exactly 210**
  > methods, so **no test was added in this round** and the totals are unmoved."* That reasoning was
  > wrong, and it cost the gate its floor: with no test in the tree, the whole check hung off one
  > `exec-maven-plugin` binding, and `-Dexec.args=-version` switched it off from the command line with
  > no edit to any file. **Correctness beats a round number.** Round 11 adds
  > `use-core/src/test/java/org/tzi/use/uncertainty/gate/UpstreamOracleGateWiringTest.java` — one class,
  > **one** test method — and re-pins the floors in the same commit: `210 → 211` and `497 → 498`. A test
  > cannot assert `use-gui`'s counts, and it does not try; it asserts **both poms' WIRING**, which is
  > exactly the division of labour the gate needed, because wiring is the thing a command-line property
  > can strip.
* *A committed script that the gate is merely "defined as running"* re-creates the defect one level up:
  a check a human must remember to run is a human-read number with extra steps — **so the script is not
  a substitute for the build binding, and since round 11 it is not an alternative to it either but an
  addition** (§5.2, defect F-02): the build binding cannot see a `-P` id Maven never accepted, and a
  script is the only place the profile id can be written down once.
* **A build binding is the shape that fails by itself** — necessary, and on its own **not sufficient**.
  The check runs because `mvn verify` runs it, in the default build as well as under the profile.

  > **CORRECTED 2026-08-17 (round 11, defect F-01).** This bullet used to end *"with `<skip>false</skip>`
  > pinned in the POM so `-Dexec.skip=true` cannot silence it (a POM configuration value overrides a user
  > property)"*, and `use-core/pom.xml` carried the same sentence. Every word of it is true **of `skip`**
  > and was false **of the check**: `exec:exec` has seven other overridable parameters, and
  > `commandlineArgs` — user property `exec.args` — *replaces* the configured `<arguments>` list, so
  > `mvn -B verify -Dexec.args=-version` ran `java -version` in place of the checker: `BUILD SUCCESS`,
  > exit 0, **zero** `[floor]` lines, clean `git status`. See §5.2 for what replaced it.

**How the `use-gui` floor is enforced despite ground rule 2 — the crux.** Two mechanisms, neither of
which needs a file under `use-gui/src`:

1. `use-gui/pom.xml` runs the checker on **itself** at `verify`, and it is the pom, not `src`, that
   ground rule 2 explicitly permits editing.
2. **Every module's run text-checks BOTH poms.** `use-core` builds first, so *`use-core`'s* check is
   what catches `use-gui`'s profile block going missing — and it catches it in the **default** build
   too, not only under the profile. That is why breakage (a) below fails `mvn -B verify` with no
   profile requested at all: the merge accident cannot reach a green build by either command.

### 5.1.2 The four checks, and the floors — pinned BEFORE the accepting run

`harness-contract.md` §8 step 2: *"A floor chosen after the run is not a floor"*, and `0` is *"rejected
outright"*. The literals below were written into the script from the round-8/round-9 measurements and
**then** the accepting run was made; §7.1/§7.2 confirm it. Floors are `>=`, so the suite may grow and
may never shrink.

| | `use-core` surefire | `use-gui` surefire | `use-core` failsafe | `use-gui` failsafe | total |
|---|---|---|---|---|---|
| DEFAULT mode | 8 classes / 80 methods | 1 / 1 | 1 / 1 | 1 / 129 | **11 / 211** |
| ORACLE mode | 41 / 351 | 8 / 17 | 1 / 1 | 1 / 129 | **51 / 498** |

*(**RE-PINNED 2026-08-17, round 11.** Was `7/79` → `10/210` and `40/350` → `50/497`. The `+1/+1` in the
`use-core` surefire cell is `UpstreamOracleGateWiringTest`, the F-01 fix — raised in the same commit that
grew the suite, which is what §8 step 7 clause 1 requires. No floor was lowered. Both poms have since
been re-pinned further as the port grew; `scripts/UpstreamOracleFloor.java`'s own header carries the
current, up-to-date literals and the running log of every re-pinning — that file, not this table, is
the floor's live source of truth.)*

**Per module and per tier, because a reactor-wide total is not a floor:** `use-core`'s 350 dwarf
`use-gui`'s 17, so one number would stay green through exactly the accident D-01 describes. Losing any
one of the four populations fails.

* **A — WIRING** (both poms, every run, both modes). The `<profiles>` element and `<id>upstream-oracle</id>`
  exist; `junit-vintage-engine` is declared **inside** the profile and **nowhere else** (so the default
  build cannot inherit it); no profile is `activeByDefault`; the effectiveness property is set; both
  floor executions are bound at the right phases; **all seven overridable `exec:exec` parameters are
  pinned in both executions** (`skip`, `commandlineArgs`, `async`, `timeout`, `quietLogs`, `executable`,
  `workingDirectory`); the verify execution passes every `exec.*` user property and the profile allow-set
  back to the checker; and the two other halves of the gate exist on disk —
  `use-core/src/test/.../UpstreamOracleGateWiringTest.java` and `scripts/upstream-oracle-gate.sh`
  *(round 11, F-01/F-02)*. Matching is done on the pom text with XML comments stripped and whitespace
  removed, so reformatting does not fool it and the pom's own prose cannot satisfy it.
* **A2 — TAMPERING** *(round 11, F-01)*. `exec-maven-plugin` is bound in this reactor to the floor and to
  nothing else, so **any** `-Dexec.*` on the command line is an attempt on the gate and is a violation in
  its own right, even though the pins already make it inert. `exec.outputFile` is the one parameter with
  no pinnable value, so for it detection is the whole defence. *Pinning makes an attack inert; A2 makes it
  loud, and F-01 was a silence nobody noticed.*
* **B2 — AN UNACTIVATABLE REQUESTED PROFILE** *(round 11, F-02)*. Every `<id>` declared inside a
  `<profiles>` element of any reactor pom is collected — the same text parse check A already ran — and a
  requested `-P` id matching none **fails the build**. Round 10 §3.5 item 3 said the earlier refusal to do
  this ("the checker would have to know every legitimate profile id, which it cannot") was refutable, and
  it was right. So even the hand-typed `mvn -Pupstream-oracle-typo` is now red. `-Duse.floor.allowProfiles=<id>`
  widens the allow-set and nothing else, for a future machine that declares profiles outside this reactor.
* **B — REQUESTED vs EFFECTIVE.** `--requested=${session.request.activeProfiles}` is the **reactor-wide
  `-P` list from the command line**, which no per-module pom edit can change; `--effective` comes from
  `use.upstreamOracle.effective`, which only a module's own profile block sets to `true`. **Requesting
  `-Pupstream-oracle` and collecting default-build counts is an error, not a pass** — the requirement
  that the merge accident cannot be reported as green. The converse also fails: effective without being
  requested (an `activeByDefault` accident) means the product build is no longer vintage-free.
* **C — COUNT FLOORS**, as tabled, plus `failures == 0`, `errors == 0` and **`skipped == 0`** in every
  tier. A skipped test is silence, which is the defect. There is no exemption for `-DskipTests`: the
  check is bound to `verify`, so lifecycle invocations that stop short of `verify` (`mvn package
  -DskipTests`) are unaffected, but `mvn verify -DskipTests` now **fails**, deliberately.
* **D — SENTINEL.** One `junit.framework.TestCase` subclass per module — `org.tzi.use.parser.USECompilerTest`
  and `org.tzi.use.gui.views.diagrams.util.DirectedLineTest` — which no engine but vintage can collect.
  It **must** have produced a report under the profile and **must not** have produced one in the default
  build. This is the check that answers "the engine resolved, but did it discover anything?" in one bit.

**One hazard found while building this, which the review did not predict and which had to be closed
before any floor meant anything.** Surefire does **not** empty `target/surefire-reports`: a report
written by an earlier `-Pupstream-oracle` run survives into a later default run, so a checker that
counted the files on disk would be handed 40 classes / 350 methods by a build that collected 7 / 79.
This was **observed on this tree**, not imagined — the first standalone run of the checker read
`classes=40 methods=350 executions=938` out of a stale directory in DEFAULT mode. The
`upstream-oracle-floor-stamp` execution therefore dates every build at `initialize`, and only report
files at least as new as the stamp are counted; a missing stamp is itself a failure, and stale files
are reported as `stale-ignored=N`. **A floor computed over stale evidence is not a floor either.**

### 5.1.3 What the floor cannot catch — stated because a limit nobody wrote down is a false claim

1. **It cannot tell a real assertion from a vacuous one.** It counts *collected methods*. §4.6 caveat 1
   stands unchanged: 21 of the revived 287 assert nothing, and the assertion-bearing figure is an upper
   bound of **266**. A floor of 351 methods is a floor on *discovery*, not on *scrutiny*. **Whole-gate
   asserting figures, added here 2026-08-17 (F-04): 199 of the default build's 211 methods and 465 of the
   profile's 498 can fail** — the six ArchUnit classes call `.evaluate()` and never `.check()`, so 12 of
   the default gate's methods and 33 of the profile's assert nothing
   (`upstream-oracle-verification.md` **R-5**). `harness-contract.md` §0.1 carries the same figures and is
   normative.
2. **It cannot detect a test that is silently weakened in place.** Upstream tests are never edited
   (ground rule 3, verified against `upstream-main` in §6), so a weakening edit would show as a
   `git diff`, not as a count change — but the count floor is blind to it. The class/method count is
   the same whether an assertion passes or was deleted from a body.
3. ~~**It cannot survive the deletion of both bindings.**~~ **CLOSED 2026-08-17 (round 11).** This item
   used to read: *"Removing the exec plugin executions from both poms disables the floor entirely, and
   nothing in the reactor then checks it. … it is the residual hole: the floor defends the profile, and
   the wiring check defends the floor, but nothing outside the poms defends the wiring check. A pom-shape
   assertion in `use-core/src/test` would close it and was rejected only because it would move the 210."*
   That assertion now exists — `UpstreamOracleGateWiringTest` — and the hole is measured closed:
   deleting the `exec-maven-plugin` block from **both** poms was `BUILD SUCCESS`, exit 0 in round 10
   (its break `(l)`); it is now `BUILD FAILURE` with **41** named violations, produced from the `test`
   phase, with **0** `[floor]` lines because the exec binding really is gone. **The new residual is one
   step further out:** deleting both exec blocks *and* the test file — a three-file, clearly visible edit
   that also drops the default count from 80 to 79 methods with no checker left to notice. Nothing
   outside the poms and that one test defends the gate, and the honest floor of this whole construction
   is that a sufficiently deliberate edit to the tree can always remove a check from the tree.
4. **It pins counts, not the plugin that produces them (D-10 is unchanged).** No pom declares
   `maven-surefire-plugin`; `3.5.4` is Maven 3.9.16's default binding, and the entire yield depends on
   surefire's default `<includes>` — the only reason the 14 `AllTests.java` files are collected at all.
   A different Maven is a different gate. The floor now makes that visible (the counts would move and
   the build would fail) instead of silent, which is strictly better but is not the same as pinning the
   plugin.
5. **It is per module and hard-codes two modules (D-14 is unchanged).** A third module gaining tests
   would have no floor. `use-assembly` has no `src/test` today
   (`find use-assembly -path "*src/test*" -name "*.java"` → empty).
6. **It asserts a minimum, so it is silent about growth.** 498 methods becoming 600 passes. The
   *distinct-method* figure is the meaningful one and a stage still has to read it; what the floor
   guarantees is that nobody reads a number that was never collected.
7. **It says nothing about fidelity.** Every one of the 497 methods passing means the port still
   satisfies upstream's assertions — the interesting case, per §4.5, arrives at S3 when `TypeTest#testSupertype`
   is *expected* to fail and that failure becomes a decision (**B5**) rather than a silence.

### 5.1.4 A floor nobody has seen fail is not known to work — five deliberate breakages

Each was applied to the committed tree, run, and the tree restored with `git status --porcelain` empty
afterwards. Pass/fail summary of each run is in §7.3.

| # | Breakage | Command | Result |
|---|---|---|---|
| a1 | `<profiles>` deleted from `use-gui/pom.xml` | **default** (no `-P`) | **FAILS** in `use-core` — WIRING |
| a2 | same | `-Pupstream-oracle` | **FAILS** in `use-core` — WIRING |
| b | `<profiles>` deleted from `use-core/pom.xml` | `-Pupstream-oracle` | **FAILS** — 5 violations: WIRING, EFFECTIVENESS (requested but not effective), both count floors, SENTINEL |
| c | vintage engine declared `<type>pom</type>` in `use-core` — resolves, wiring intact, profile *effective*, engine JAR never on the test classpath | `-Pupstream-oracle` | **FAILS** — 3 violations: `7 < 40` classes, `79 < 350` methods, SENTINEL. **The count floor alone catches this one** |
| d | vintage version mangled to a version that cannot resolve | `-Pupstream-oracle` | **FAILS** — Maven cannot resolve dependencies for `use-core` |
| e | tree intact; the gate narrowed from the command line with `-Dtest=…` | `-Pupstream-oracle` | **FAILS** — `1 < 40` classes, `6 < 350` methods, SENTINEL |

---

## 5.2 ROUND 11 — closing F-01, F-02 and F-03: a guard the guarded party can turn off is not a guard

Round 10's independent refutation (`upstream-oracle-floor-verification.md`) returned **`DEFECTIVE`**. It
confirmed the floor was real, correctly pinned before the run it validated, and unsatisfiable without the
upstream tests actually running — and then defeated it twice **without editing a single tracked file**:

```
$ mvn -B verify -Djava.awt.headless=true -Dexec.args=-version       # F-01
[driver] number of [floor] lines in the log: 0
[INFO] BUILD SUCCESS

$ mvn -B verify -Pupstream-oracle-typo -Djava.awt.headless=true     # F-02
[WARNING] The requested profile "upstream-oracle-typo" could not be activated because it does not exist.
[floor] mode: DEFAULT
[floor] PASS — use-core met every pinned floor in DEFAULT mode.
[INFO] BUILD SUCCESS                                                 EXIT=0
```

Both are now red. What follows is the mechanism, then the measurement.

### 5.2.1 F-01 — three independent mechanisms, because one is a single point of failure

> **CORRECTED 2026-08-18, round 11 defect G-03, and the refuter's own count corrected in turn.**
> This section said `exec:exec` declares "**eight**" parameters with user properties. Round 11's
> refuter said **21**, having pasted a list of 22. The measured number is **22**, counted from the
> plugin descriptor (`unzip -p ~/.m2/repository/org/codehaus/mojo/exec-maven-plugin/3.5.0/
> exec-maven-plugin-3.5.0.jar META-INF/maven/plugin.xml`, counting `exec`-goal parameters whose
> `<configuration>` body holds a `${...}` expression): 20 are `exec.*` — `addOutputToClasspath`,
> `addResourcesToClasspath`, `commandlineArgs` (`exec.args`), `async`, `asyncDestroyOnShutdown`,
> `classpathScope`, `executable`, `forceJava`, `includePluginDependencies`, `inheritIo`,
> `longClasspath`, `longModulepath`, `outputFile`, `quietLogs`, `skip`, `timeout`, `toolchain`,
> `toolchainJavaHomeEnvName`, `useMavenLogger`, `workingDirectory` (`exec.workingdir`) — and 2 are
> unprefixed (`sourceRoot`, `testSourceRoot`); none are read-only.
>
> Seven are pinned, eight are handed to check A2, fourteen are neither — and that residual is
> **deliberate and listed**, not overlooked. See [`gate-threat-model.md`](gate-threat-model.md) §2
> and §3: closing the other fourteen would defend the in-build binding against an operator who
> hand-types a `-D` to disable their own acceptance check, and that is not the adversary this gate
> exists for. The wrapper still fails such a run.
>
> The claim below that "**any** set property fails the build" was also refuted for a different
> reason (G-01/G-02) and is **now true**, since 2026-08-18 — see §5.2.6.

`exec:exec` declares **22** parameters with user properties, of which **eight** could silence or
divert the floor and are handled here. Pinning `<skip>false</skip>` closed one of the eight. The
others include `commandlineArgs` (`exec.args`), which does not merely *configure* the argument list —
it **replaces** it.

1. **Pin all seven pinnable parameters, in both executions of both poms** — `skip`, `commandlineArgs`,
   `async`, `timeout`, `quietLogs`, `executable`, `workingDirectory`. A POM `<configuration>` element
   beats a `@Parameter(property=…)` default, which round 10's own break `(f)` proved on this tree when the
   pinned `<executable>` held while the unpinned argument list was replaced. `async` mattered as much as
   `args`: `-Dexec.async=true` would have detached the process and left its exit code unread.
2. **Detect the attempt as well as blocking it (check A2).** Inert is not loud. Each pom now hands all
   eight `exec.*` user properties back to the checker (`--exec-args=${exec.args}` and its siblings);
   Maven interpolates one only if the operator set it, so a set property is detectable, and any set
   property **fails the build**. This is also the only defence against `exec.outputFile`, the one
   parameter with no pinnable value.
3. **A Jupiter test no `exec` property can reach.**
   `use-core/src/test/java/org/tzi/use/uncertainty/gate/UpstreamOracleGateWiringTest.java` — one method —
   re-asserts both poms' wiring from the `test` phase, and adds the one thing text cannot check: at
   runtime, the `initialize`-phase stamp must exist, and must not be older than the newest receipt on
   disk (a receipt is written at `verify`, after its own build's stamp, so an inverted order proves the
   stamp is a previous build's).
4. **A receipt verified after Maven has exited.** The checker writes
   `<module>/target/upstream-oracle-floor.receipt` — module, mode, verdict, partiality, counts — on FAIL
   as well as PASS, and `scripts/upstream-oracle-gate.sh` requires it to exist, to be newer than that
   run's start marker, and to say `verdict=PASS`. No Maven property reaches a check that runs after Maven.

**The argv the checker now requires**, and what it does otherwise: exactly
`--module --module-dir --reactor-root --effective --selected --resume-from --requested --allow-profiles`
and the eight `--exec-*` options — all required, no others accepted. An unknown option, a missing option,
a duplicate, or a leading token that is not an option is **FATAL: exit 2, no receipt, build fails**.
It cannot defend itself against not being invoked at all; that is what mechanisms 3 and 4 are for.

### 5.2.6 G-01, G-02, G-04, G-05 — closed 2026-08-18 (round 12)

Round 11's independent refutation (`upstream-oracle-gate-round11.md`) broke the *in-build* binding on
four routes. All four are closed; the ordering defect that made them possible is closed structurally.

| id | what it was | what closes it |
|---|---|---|
| **G-01** (CRITICAL) | `mvn -B verify -Dexec.args='x --stamp=true'` → **exit 0, BUILD SUCCESS**, four `[floor] wrote freshness stamp` lines and nothing else, **no receipt**. `exec:exec` splits `<commandlineArgs>` *after* Maven interpolates it, so an operator-set property contributed extra argv tokens; `main` tested `opt.containsKey("stamp")` **before** the argv was fully validated, so the verify-phase execution rewrote the stamp and returned 0, skipping checks A, A2, B, B2, the floors, the sentinel and the receipt. Reproduced identically through `use.floor.allowProfiles` and `use.upstreamOracle.effective`. | **Exact-set argv matching, and validation before dispatch.** `UpstreamOracleFloor.parseArgs` now parses only; `validateArgv` then (1) rejects any option *value* that itself parses as an option, (2) requires the option-name set to equal `STAMP_OPTIONS` or `CHECK_OPTIONS` **exactly**, (3) only then chooses a mode. `stamp` is simply not a legal name in a check argv. All three routes are now `FATAL`, exit 2, **no receipt**, `BUILD FAILURE`. |
| **G-02** (MAJOR) | `-Dexec.outputFile='/tmp/floorhide${z}.txt'` → green, `verdict=PASS`, **zero** `TAMPERING` violations, `[floor]` absent from a 1463-line log. The set/unset test was `value.contains("${")`, so any value *containing* the opener read as *unset* — against the one exec parameter that cannot be pinned, for which detection is the whole defence. | The test is exact: unset means the value **is** the whole placeholder, `"${" + property + "}"`, end to end (`isSet`). The same run is now `verdict=FAIL`, one `TAMPERING` violation, `BUILD FAILURE`. |
| **G-04** (MINOR, the realistic accident) | `mvn -B test -Pupstream-oracle-typo` → **BUILD SUCCESS, exit 0, no floor check at all**, the typo one `[WARNING]` in a 1393-line log. The whole gate bound at `verify`, which a truncated lifecycle outruns. | The unactivatable-profile check is **also bound at `initialize`** — the second phase of the default lifecycle, which every truncatable lifecycle still reaches. Both poms' `upstream-oracle-floor-stamp` execution now passes `--reactor-root`, `--requested` and `--allow-profiles`; `checkWiring` and `UpstreamOracleGateWiringTest` each require those three tokens **twice** per pom, so removing the initialize-phase copy fails the build. |
| **G-05** (MINOR) | `-Duse.floor.allowProfiles` widens check B2 and was echoed **nowhere** — not the log, not the receipt — so a widened allow-set left no trace in a green run. | Echoed in the `initialize` line, in the check's own header, and recorded in the receipt as `allow-profiles=…`, with `<-- CHECK B2 WAS WIDENED BY THE COMMAND LINE` when non-empty. `scripts/upstream-oracle-gate.sh` now **requires** `allow-profiles=(none)` in each receipt, so forwarding that property through the wrapper fails the gate. |

**G-03's factual half** is corrected in the box at the head of §5.2.1. Its *mechanism* half —
`-Dexec.useMavenLogger=true` re-prefixing every line to `[INFO] [floor] …`, blinding the wrapper's
two anchored greps — is **out of scope and listed as residual**
([`gate-threat-model.md`](gate-threat-model.md) §3, R-3): the wrapper still fails such a run on its
announce-count and receipt checks, and defeating it requires a hand-typed `-D` that no honest
workflow produces.

### 5.2.2 F-02 — the gate is an invocation, not a command you type

The build cannot detect a request Maven never accepted, so the gate is now **defined as**
`scripts/upstream-oracle-gate.sh`, which holds the profile id on one line and, after the build, fails on
`could not be activated`, on a missing `[floor] PASS — <module> … in <MODE> mode.` for either module, and
on a missing or stale receipt. `harness-contract.md` §0.1 says plainly that hand-typing `-P` is not the
gate.

Round 10 §3.5 item 3 also said the porter's reason for not enforcing this *inside* the build was
refutable — and it was. Check **B2** now collects every profile `<id>` declared by any reactor pom (the
same text parse check A already ran) and fails on a requested id matching none. **So the bare, hand-typed
`mvn -Pupstream-oracle-typo` is red too.** The honest residual: a profile declared in a `settings.xml` or
an ancestor pom outside this reactor would not be found. This machine has neither
(`upstream-oracle-verification.md` §11), so the pom set is the complete authority here; a future machine
that has one declares the id or passes `-Duse.floor.allowProfiles=<id>`.

### 5.2.3 F-03 — a partial reactor is not a gate

The checker reads `${session.request.selectedProjects}` and `${session.request.resumeFrom}` and prints
`PARTIAL`, never `PASS`, when either is set, naming what was not checked. Exit stays `0` — `-pl` is a
deliberate developer flag — but the receipt records `verdict=PARTIAL` and the wrapper rejects it.

### 5.2.4 The measurement — round 10's two bypasses, its controls, and the residual hole

Every row run on the committed tree, `mvn -q clean` first, tree restored and `git status --porcelain`
checked after each. Pass/fail summary of each run is in §7.4.

| # | what was done | round 10 | round 11 | what the message names |
|---|---|---|---|---|
| B1 | `-Dexec.args=-version`, default command, tree intact | **PASS, exit 0, 0 `[floor]` lines** | **FAIL** exit 1, **16** `[floor]` lines | `TAMPERING: -Dexec.args=-version was set on the command line` |
| B2 | same, under the profile | **PASS, exit 0** | **FAIL** exit 1 | idem, in ORACLE mode |
| B3 | `-Dexec.outputFile=/dev/null` (unpinnable) | not tried | **FAIL** exit 1, 0 `[floor]` lines | Maven names the `upstream-oracle-floor` execution; the receipt on disk carries `verdict=FAIL` |
| B4 | `-Dexec.async=true` | not tried | **FAIL** exit 1 | `TAMPERING: -Dexec.async=true` |
| B5 | `-Dexec.skip=true`, tree intact | inert (green; needed a pom break to fail) | **FAIL** exit 1 | `TAMPERING: -Dexec.skip=true` |
| B6 | **bare** `-Pupstream-oracle-typo` | **PASS, exit 0** | **FAIL** exit 1 | `PROFILE: -Pupstream-oracle-typo … NO pom in this reactor declares a profile with that id … Declared profile ids: [upstream-oracle]` |
| B7 | `-pl use-core -Pupstream-oracle` | **unqualified `PASS`, exit 0** | **`PARTIAL`**, exit 0 | `THIS WAS A PARTIAL REACTOR (-pl/--projects [use-core]) … A partial reactor is NOT the acceptance gate` |
| B8 | `<profiles>` deleted from `use-gui/pom.xml`, default command *(control)* | FAIL exit 1 | **FAIL** exit 1, now at the `test` phase | `use-gui/pom.xml HAS NO <profiles> ELEMENT … (D-01's merge accident)` |
| B9 | `-DskipTests` under the profile *(control)* | FAIL exit 1, 7 violations | **FAIL** exit 1, 7 violations | `0 distinct test classes < floor 41`, `report directory does not exist`, SENTINEL |
| B10 | the Jupiter test deleted | n/a | **FAIL** exit 1 | the missing file **and** `7 < 8` classes, `79 < 80` methods |
| B11 | `<commandlineArgs>` unpinned (round-10 shape) **and** `-Dexec.args=-version` | **PASS, exit 0** | **FAIL** exit 1, 0 `[floor]` lines | mechanism 1 defeated; the test names `carries <commandlineArgs> 0 time(s), needs 2` and `RUNTIME: …stamp does not exist` |
| B12 | `exec-maven-plugin` deleted from **both** poms — §5.1.3 item 3's residual hole | **PASS, exit 0** | **FAIL** exit 1, 41 violations | every stripped pin, both poms, by name |

**B11 is the belt-and-braces result:** with the pin removed, `-Dexec.args=-version` still silences the
exec binding exactly as it did in round 10 — and the build is red anyway, from the other mechanism, which
names both the removed pin and the runtime consequence. **B12 is the closure of the documented residual
hole.** B8's message now arrives from surefire rather than from `verify`, i.e. earlier; the floor check
would have caught it too.

---

## 6. The diff — what this round touched

> **ROUND 11 adds three non-docs paths to the two below**, all of them permitted by ground rule 2
> (the two poms, plus `scripts/` and `use-core/src/test`): `scripts/upstream-oracle-gate.sh` (new),
> `use-core/src/test/java/org/tzi/use/uncertainty/gate/UpstreamOracleGateWiringTest.java` (new) and
> `scripts/UpstreamOracleFloor.java` (modified). Still **no** `*/src/main/*` path, no `module-info.java`,
> no `use-gui/src`, no `use-assembly/src`, and no upstream test edited.

Ground rule 2 permits exactly two non-docs, non-differential-harness paths this round:
`use-core/pom.xml` and `use-gui/pom.xml`. Verified:

```
$ git diff --name-status 30d480db..HEAD -- '*/src/main/*'
(empty)
```

`git diff --name-status 30d480db..HEAD` lists 84 paths in total: 44 new `docs/port2/**` files (this
round's own audit trail, including this document), `.gitattributes` (new) / `.gitignore` (modified,
S0 housekeeping, `stage-00-baseline.md` §1), the S1 differential harness — 17 new files under
`use-core/src/test/java/org/tzi/use/uncertainty/differential/` plus two vendored reference jars
under `use-core/src/test/resources/historical/` — and the two pom edits. Filtered to the non-`docs/`
paths, which is where the ground rule bites:

```
$ git diff --name-status 30d480db..HEAD | grep -v '\tdocs/'
A	.gitattributes
M	.gitignore
M	use-core/pom.xml
A	use-core/src/test/java/org/tzi/use/uncertainty/differential/   (17 .java files: AcceptedDegenerateOperations,
	AcceptedThrowPairs, Candidate, DiffReportWriter, DiffRow, DiffVerdict,
	DifferentialHarnessRegressionTest, DifferentialSweep, HarnessMarshallingException,
	HistoricalOracle, HistoricalOracleIsolationTest, InputGenerator, IsolatedJarClassLoader,
	PortedInfidelityDetectionPowerTest, StubCandidate, UOp, UValue,
	UncertaintyDifferentialSmokeTest, UnwrittenPortInvariantTest)
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
| profile counts reported as distinct classes/methods, deduplicated | met — §4.1, §4.2: 50 classes / 497 methods *(round-9 figures; round 11 re-pinned them to 51 / 498 — §5.2)* |
| profile deltas reconciled against both earlier probes | met — §4.3, every delta named |
| failures and errors reported honestly | met — §4.5: 0/0/0, and §4.6's three caveats on what the 287 is worth |
| no test file edited, migrated or renamed | met — §6; every `src/test` path in the diff is the port's own harness |
| `*/src/main/*` diff empty | met — §6 |
| the acceptance counts are **asserted by a machine**, per module and per tier | met — §5.1, and §7.3 shows the assertion FAILING five ways |
| the floors were pinned **before** the accepting run | met — §5.1.2; the literals are in `scripts/UpstreamOracleFloor.java` and the run that accepted them is §7.1 |
| requesting the profile without it being effective is an **error** | met — §7.3 breakage (b), violation 2 |

---

## 7.1 Round-9 acceptance run — the default build, from clean

`mvn -q clean && mvn -B verify -Djava.awt.headless=true`: `BUILD SUCCESS`, exit 0. Deduplicated
(§2's script): `surefire use-core 7/79`, `surefire use-gui 1/1`, `failsafe use-core 1/1`, `failsafe
use-gui 1/129` — **TOTAL 10 classes / 210 methods, 0 failures / 0 errors / 0 skipped**. The floor
check (`scripts/UpstreamOracleFloor.java`, phase `verify`) printed `[floor] PASS — use-core met
every pinned floor in DEFAULT mode.` and the same for `use-gui`, both against `stale-ignored=0`;
both vintage-only sentinels (`org.tzi.use.parser.USECompilerTest`,
`org.tzi.use.gui.views.diagrams.util.DirectedLineTest`) reported `absent`, as expected in DEFAULT
mode. `mvn -B dependency:list | grep -c vintage` → `0`.

**210 methods, 10 classes, 0 failures / 0 errors / 0 skipped, no vintage engine — unchanged by this
round**, and now every one of the four populations is asserted.

## 7.2 Round-9 acceptance run — under the profile, from clean

`mvn -q clean && mvn -B verify -Pupstream-oracle -Djava.awt.headless=true`: `BUILD SUCCESS`, exit
0. Deduplicated: `surefire use-core 40/350`, `surefire use-gui 8/17`, `failsafe use-core 1/1`,
`failsafe use-gui 1/129` — **TOTAL 50 classes / 497 methods, 0 failures / 0 errors / 0 skipped**
(1085 raw executions, the aggregator inflation of §4.1). The floor check printed `[floor] PASS` for
both modules in ORACLE mode, both sentinels `collected`, both floors met exactly. `mvn -B
dependency:list -Pupstream-oracle | grep vintage` confirms the engine is present.

**50 distinct classes / 497 distinct methods, 0 failures / 0 errors / 0 skipped, `BUILD SUCCESS`,
and all four floors met exactly** — the checker's figures and the independent script's figures
agree, and the 1085 executions against 497 methods are the aggregator inflation of §4.1.

## 7.3 The five deliberate breakages — pass/fail summary

Each breakage in §5.1.4's table was applied to the committed tree at `6702f06e`, run, and restored;
`git status --porcelain` was empty after every restore. **Every one fails, as the table states.**
What each one proves:

* **(a1)** is the D-01 scenario exactly — one module's profile block deleted — and it fails the
  **default** command, with no profile requested. The merge accident cannot reach a green build at all.
* **(b)** fires all four mechanisms at once, including `EFFECTIVENESS: -Pupstream-oracle WAS
  REQUESTED ON THE COMMAND LINE BUT IS NOT EFFECTIVE IN use-core`, which is the requirement that a
  requested profile collecting default counts is an error.
* **(c)** is the case only a count floor can see: the wiring is intact, the profile *is* effective,
  the dependency resolves — and the engine never reaches the test classpath. `7 < 40`, `79 < 350`,
  sentinel absent.
* **(d)** cannot even resolve, so the gate fails before any test runs. A gate that cannot run is not
  a gate that passed.
* **(e)** is the same defect attempted from the command line rather than the tree: `-Dtest=`
  narrowing under the profile collects 6 methods of 350 and fails.

## 7.4 Round-11 acceptance and break output — pass/fail summary

Every command below was run on `port-uncertainty-2` with the round-11 changes in the working tree,
`pgrep -f '[c]lassworlds.launcher.Launcher'` empty before each, `mvn -q clean` before each build, and
`git status --porcelain` clean before and after every run, with no foreign modification observed at
any point.

### 7.4.1 THE GATE — both modes, one invocation

`bash scripts/upstream-oracle-gate.sh` at HEAD (`cdcbea54`), clean tree, 3 m 14 s wall:
`[gate] PASS — mode 'both': every check above held.` Both floors met **exactly**, in both modes, for
both modules and both tiers (default 11/211, oracle 51/498 — the `+1/+1` of the wiring test);
`stale-ignored=0` everywhere; both vintage-only sentinels absent by default and collected under the
profile; both receipts written and verified on disk after Maven exited. Headline executions:
`939 + 17` surefire and `1 + 129` failsafe = **1086 executions for 498 distinct methods** under the
profile. `mvn -B -q dependency:list -Djava.awt.headless=true | grep -c vintage` → `0`.

### 7.4.2 F-01 — `-Dexec.args=-version`, the command that used to silence everything

Round 10: `BUILD SUCCESS`, 0 `[floor]` lines. Now: `mvn -B verify -Djava.awt.headless=true
-Dexec.args=-version` → **`BUILD FAILURE`, exit 1, 16 `[floor]` lines**, one `TAMPERING` violation
naming the property and citing this defect (F-01). Identical result under the profile (`b2`). The
three siblings — `-Dexec.async=true` (`b4`), `-Dexec.skip=true` (`b5`, inert in round 10, needed a
pom break to fail), `-Dexec.outputFile=/dev/null` (`b3`, the one unpinnable parameter) — all
`FAIL`, exit 1, each naming its own `TAMPERING` violation (`b3` has 0 `[floor]` lines in the log —
the words went to `/dev/null` — but Maven itself still fails).

### 7.4.3 F-01, belt and braces — mechanism 1 removed, mechanism 2 holds

With `<commandlineArgs>` reverted to round 10's unpinned `<arguments>` shape in
`use-core/pom.xml`, `exec.args` wins again and the exec binding really is silenced — **0**
`[floor]` lines, exactly as in round 10 — and the build is red anyway, from mechanism 2: the Jupiter
wiring test names both the removed pin (`carries <commandlineArgs> 0 time(s), needs 2`) and the
runtime consequence (`RUNTIME: … stamp does not exist`), 2 violations, exit 1.

### 7.4.4 F-01 — §5.1.3 item 3's residual hole, closed

With `exec-maven-plugin` deleted from **both** poms, round 10 (break `(l)`) was `BUILD SUCCESS`,
exit 0. Now: **`BUILD FAILURE`, exit 1, 41 violations** — the wiring test names every stripped pin
in both poms individually (missing floor executions, missing `--module=`, all seven pins at
`0` time(s) where 2 are required, and so on).

### 7.4.5 F-02 — the mistyped profile id, both ways

The **bare, hand-typed** `mvn -B verify -Pupstream-oracle-typo -Djava.awt.headless=true` — round
10 (break `(d)`) was `BUILD SUCCESS`, exit 0, floor `PASS` in DEFAULT mode. Now: **`BUILD
FAILURE`**, one `PROFILE` violation naming the typo, the declared ids, and the fact that the gate is
the wrapper script, not a hand-typed `-P`. Through the wrapper, nine independent checks fail together
(no `BUILD SUCCESS`, the `could not be activated` line, both modules' missing `PASS` lines, missing
announce count, missing receipts) — `GATE FAILED — 9 check(s) failed in mode 'default'.`

**What the bare command can and cannot be made to do — stated plainly.** `mvn -P<typo>` is now a
build failure because check B2 reads the reactor's declared profile ids. What *cannot* be fixed from
inside Maven is the class of causes B2 cannot see: a profile id that a `settings.xml` outside this
reactor declares, a `-P` list mangled by `MAVEN_ARGS`, or any other route by which the profile is
accepted by Maven and yet does nothing. For those, the only defence is that the acceptance gate is a
committed invocation whose post-conditions are checked outside Maven — which is why the wrapper
requires the receipt on disk and the exact `PASS` line per module, and why `harness-contract.md` §0.1
says hand-typing `-P` is not the gate. Nothing stops an operator from typing `mvn` by hand; what the
record can do is refuse to call that the gate, and make the common typo red anyway.

### 7.4.6 F-03 — `-pl` no longer says `PASS`

`mvn -B verify -pl use-core -Pupstream-oracle -Djava.awt.headless=true` → `BUILD SUCCESS`, exit 0
(on purpose — `-pl` is a deliberate developer flag, not a merge accident), but `[floor] PARTIAL —
use-core met its own pinned floors in ORACLE mode, but THIS WAS A PARTIAL REACTOR …`, never `PASS`.
The receipt records `verdict=PARTIAL`, and the wrapper requires `verdict=PASS` and
`partial-reactor=false`, so a partial reactor can be used for iteration and can never be quoted as
acceptance.

### 7.4.7 The controls — round 10's tree-borne breakages must still fail, and do

Three controls, all still `FAIL`, exit 1: **(b8)** `<profiles>` deleted from `use-gui/pom.xml`
under the default command — 2 violations, now caught at the `test` phase rather than `verify`, i.e.
earlier; the floor check would have caught it too. **(b9)** `-DskipTests` under the profile — 7
violations (report directory absent, both count floors, sentinel). **(b10)** the Jupiter wiring test
itself deleted — 3 violations: the missing file, and the count floor independently noticing the
`+1/+1` method it stopped contributing (`7 < 8` classes, `79 < 80` methods) — the two halves of the
fix guarding each other.
