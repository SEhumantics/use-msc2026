# Refutation: the `-Pupstream-oracle` profile and H21

**Round:** S2 post-hoc refutation, round 9 of the port-2 review chain
**Refuter owns Maven this round.** The porter did not build anything reported here; every number
below was produced by the refuter on a tree freshly `mvn -B clean`ed.
**Date:** 2026-08-17
**Branch:** `port-uncertainty-2`
**Verdict:** **SOUND WITH DOCUMENTED LIMITS** — the profile is fit to serve as the S3–S10
acceptance gate. Six defects, all in *documentation and analysis prose*, none in the profile
mechanism or the H21 code.

**Toolchain that produced these numbers** (record it; a different Maven picks a different surefire):

```
Apache Maven 3.9.16 (2bdd9fddda4b155ebf8000e807eb73fd829a51d5)
Java version: 21.0.11, vendor: Ubuntu, runtime: /usr/lib/jvm/java-21-openjdk-amd64
```

Neither pom pins surefire, so both runs used Maven's default **surefire 3.5.4** while **failsafe is
pinned at 2.22.2**:

```
[INFO] --- failsafe:2.22.2:integration-test (default)
[INFO] --- failsafe:2.22.2:verify (default)
[INFO] --- surefire:3.5.4:test (default-test)
```

That mismatch pre-exists this round and was correctly left alone (ground rule 2 permits only the
profile block).

---

## 0. The commit the porter reported is no longer HEAD

The handover states the final hash as `e73054e2`. It is not HEAD, and HEAD moved **twice more while
this verification was running**. Another Claude session shares this checkout and is committing.

```
$ git log --oneline -5
b15b5a94 docs(port2): H14 coverage-measure design (agent errored on return, file was complete)
7696091e docs(port2): B7 fix plan — all 33 behaviour-changing rows triaged
e73054e2 docs: record decisions B2, B3, B7, H14; write upstream-oracle-profile.md (H21 too)
1ec7d59f harness: split rows.javaTypeMismatch by the subject's type provenance (H21)
e3668a04 build: add the -Pupstream-oracle Maven profile (decision B3)
```

Both post-`e73054e2` commits are documentation-only, so **every build number in this document
stands**:

```
$ git diff --name-status 7696091e..b15b5a94
M	.gitignore
A	docs/port2/h14-coverage-design.md
```

The `.gitignore` delta adds `/target` (reactor-root build output) and nothing else — no source path
is hidden:

```
$ git diff 7696091e..b15b5a94 -- .gitignore
+
+# Reactor-root build output
+/target
```

The measurements in sections 2 and 3 were taken at `7696091e`; the static re-checks in section 1 were
re-run at `b15b5a94` and are unchanged. Untracked files belonging to the other session appeared
during the round (`docs/port2/h14-coverage-design.md`, later
`docs/port2/upstream-oracle-static-review.md`); the refuter did not touch either.

### Competing Maven

Ground rule 5 check — `pgrep -f '[m]vn -B'` **does** match:

```
494057 ... while [ $quiet -lt 20 ]; do if pgrep -f '[m]aven|[m]vn'; then quiet=0; else
       quiet=$((quiet+1)); fi; sleep 3; done; ... mvn -B clean test > .../mvn-final.log
```

The porter's diagnosis is confirmed correct: the watcher's own command line contains the string
`mvn -B clean test`, so its guard `pgrep -f '[m]aven|[m]vn'` matches **itself**, `quiet` can never
reach 20, and it will never fire. No `java` build process or forked-booter existed at any point
during these measurements. **Risk restated:** if that session ever kills or restarts the watcher it
fires `mvn -B clean test` in this shared checkout and wipes `target/`.

---

## 1. Constraint gates (ground rules 2 and 3)

All five source gates are **empty** at HEAD `b15b5a94`:

```
$ for P in '*/src/main/*' '*module-info.java' 'use-gui/src/*' 'use-assembly/src/*'; do ... done
  */src/main/* -> EMPTY
  *module-info.java -> EMPTY
  use-gui/src/* -> EMPTY
  use-assembly/src/* -> EMPTY
```

Non-docs, non-harness, non-jar paths in the whole branch diff are **exactly four**, as claimed:

```
$ git diff --name-status 30d480db..HEAD | grep -vE 'docs/|src/test/java/org/tzi/use/uncertainty/differential/|\.jar$'
A	.gitattributes
M	.gitignore
M	use-core/pom.xml
M	use-gui/pom.xml
```

### No upstream test was modified — rule-3 check for task 2

```
$ git diff --name-status 30d480db..HEAD -- '*/src/test/*' | awk '$1!="A"'
[end non-A]
```

**Every** `src/test` path on the branch is `A`. There is no `M` on any pre-existing upstream test,
so no rule-3 violation. The full listing is 19 `A` harness sources under
`org/tzi/use/uncertainty/differential/` plus two `A` fixture jars — see defect **R-5** for a small
inaccuracy in how the handover described this.

---

## 2. Task 1 — the default build stays clean of vintage

### 2.1 The engine is absent from the resolved test classpath, in both modules

Counts alone would not prove this, so the classpath itself was dumped and diffed.

```
$ mvn -B -o dependency:tree -Dincludes='org.junit.vintage:*'
[INFO] BUILD SUCCESS
vintage-line-count=0

$ mvn -B -o dependency:tree -Pupstream-oracle -Dincludes='org.junit.vintage:*'
[INFO] \- org.junit.vintage:junit-vintage-engine:jar:5.7.0:test
[INFO] \- org.junit.vintage:junit-vintage-engine:jar:5.7.0:test
vintage-line-count=2
```

Stronger — the **fully resolved test classpath** via `dependency:build-classpath`
(`-Dmdep.includeScope=test`):

```
MODE=default
  use-core: entries=29  vintage=0  junit4=1  jupiter=1
  use-gui:  entries=73  vintage=0  junit4=1  jupiter=1
MODE=profile
  use-core: entries=30  vintage=1  junit4=1  jupiter=1
  use-gui:  entries=74  vintage=1  junit4=1  jupiter=1
```

And the decisive check — the **only** entry that differs, in either module, is the vintage jar:

```
$ diff <(sorted default cp) <(sorted profile cp)
=== use-core ===
27a28
> org/junit/vintage/junit-vintage-engine/5.7.0/junit-vintage-engine-5.7.0.jar
=== use-gui ===
49a50
> org/junit/vintage/junit-vintage-engine/5.7.0/junit-vintage-engine-5.7.0.jar
```

Nothing is removed, and **no transitive version is downgraded** —
`junit-platform-engine` and `junit-platform-commons` stay at `1.7.0` in both modes. This is worth
stating explicitly because it is the failure mode the profile could plausibly have had: vintage
`5.7.0` matches the declared jupiter `5.7.0` exactly in both modules, so there is no platform skew.

The word `vintage` appears **0 times** in the entire default build log.

The porter's claim that `junit:junit` was already present by default is confirmed, including its
provenance:

```
$ mvn -B -o -pl use-core dependency:tree -Dincludes='junit:junit'
[INFO] org.tzi.use:use-core:jar:7.5.0
[INFO] \- com.google.guava:guava-testlib:jar:33.5.0-jre:test
[INFO]    \- junit:junit:jar:4.13.2:test
```

`use-gui` declares `junit:junit:4.13.1` directly (`use-gui/pom.xml:152-154`). This is why the 59
JUnit 3/4 sources compile today despite never running.

### 2.2 The default counts

```
$ mvn -B clean && mvn -B verify -Djava.awt.headless=true
VERIFY_DEFAULT_EXIT=0
[INFO] BUILD SUCCESS
[INFO] Total time:  01:36 min
```

Deduplicated XML scan (class = XML root `@name`; methods = distinct `<testcase> @name`):

```
surefire  use-core  classes=  7 methods=  79 executions=  79 failures=0 errors=0 skipped=0
surefire  use-gui   classes=  1 methods=   1 executions=   1 failures=0 errors=0 skipped=0
failsafe  use-core  classes=  1 methods=   1 executions=   1 failures=0 errors=0 skipped=0
failsafe  use-gui   classes=  1 methods= 129 executions= 129 failures=0 errors=0 skipped=0
TOTAL               classes= 10 methods= 210 executions= 210 failures=0 errors=0 skipped=0

inflated_class_count=0
zero_testcase_file_count=0
```

`executions == methods` exactly, and there are no aggregator files — the default build contains no
JUnit-3 suites, so its headline cannot overcount.

### 2.3 The 209 → 210 drift, independently accounted

The task text asks for 209. The measured figure at HEAD is **210** (80 surefire + 130 failsafe). The
porter flagged this and the accounting is exact. It was verified *statically*, without trusting
either build:

```
$ for REF in e3668a04 1ec7d59f HEAD; do git show $REF:...DifferentialHarnessRegressionTest.java \
    | grep -cE '^\s*@(Test|ParameterizedTest|RepeatedTest)'; done
  e3668a04 : 34
  1ec7d59f : 35
  HEAD     : 35

$ git show e3668a04:...DifferentialHarnessRegressionTest.java | grep -c 'theTypeMismatchTotalIsSplitBySubjectTypeProvenance'
0
$ git show HEAD:...DifferentialHarnessRegressionTest.java | grep -c 'theTypeMismatchTotalIsSplitBySubjectTypeProvenance'
1
```

**209 belongs to `e3668a04` (the pom-only leak test); 210 belongs to HEAD.** The delta is exactly the
one H21 regression method and nothing else. Future acceptance text should quote 210.

---

## 3. Task 2 — the profile executes upstream's tests, unmodified

```
$ mvn -B clean && mvn -B verify -Pupstream-oracle -Djava.awt.headless=true
VERIFY_PROFILE_EXIT=0
[INFO] BUILD SUCCESS
[INFO] Total time:  02:14 min

[INFO] Reactor Summary for use 7.5.0:
[INFO] use ................................................ SUCCESS [  0.002 s]
[INFO] use-core ........................................... SUCCESS [01:24 min]
[INFO] use-gui ............................................ SUCCESS [ 42.981 s]
[INFO] use-assembly ....................................... SUCCESS [  6.537 s]
```

### 3.1 Deduplicated counts

```
surefire  use-core  classes= 54 methods= 350 executions= 938 failures=0 errors=0 skipped=0
surefire  use-gui   classes=  8 methods=  17 executions=  17 failures=0 errors=0 skipped=0
failsafe  use-core  classes=  1 methods=   1 executions=   1 failures=0 errors=0 skipped=0
failsafe  use-gui   classes=  1 methods= 129 executions= 129 failures=0 errors=0 skipped=0
TOTAL               classes= 64 methods= 497 executions=1085 failures=0 errors=0 skipped=0
```

**Distinct methods: 497 — the handover's figure, confirmed exactly.**
**Distinct classes: 64 under the counting rule the handover states, 50 under the rule it actually
used.** See defect **R-1**. The 14-file difference is exactly the aggregators:

```
--- zero-testcase files (aggregators) ---
  org.tzi.use.AllTests                 org.tzi.use.uml.AllTests
  org.tzi.use.graph.AllTests           org.tzi.use.uml.mm.AllTests
  org.tzi.use.parser.AllTests          org.tzi.use.uml.ocl.expr.AllTests
  org.tzi.use.parser.shell.AllTests    org.tzi.use.uml.ocl.type.AllTests
  org.tzi.use.parser.soil.AllTests     org.tzi.use.uml.ocl.value.AllTests
  org.tzi.use.utilcore.AllTests        org.tzi.use.uml.sys.AllTests
  org.tzi.use.utilcore.soil.AllTests   org.tzi.use.uml.sys.soil.AllTests
  zero_testcase_file_count=14
```

Excluding them reproduces the handover's numbers precisely:

```
surefire  use-core  nonempty_classes= 40 distinct_methods= 350  zero_testcase_files_excluded=14
surefire  use-gui   nonempty_classes=  8 distinct_methods=  17  zero_testcase_files_excluded=0
failsafe  use-core  nonempty_classes=  1 distinct_methods=   1
failsafe  use-gui   nonempty_classes=  1 distinct_methods= 129
TOTAL nonempty_classes=50 distinct_methods=497
```

### 3.2 The overcount is real and correctly diagnosed

`Tests run: 938` for use-core surefire is a count of method **executions**. 28 of the 40
method-bearing classes are inflated by the nested suites, e.g.

```
org.tzi.use.uml.ocl.type.TypeTest    methods= 38 executions= 152 factor=4.00
org.tzi.use.uml.sys.soil.StatementEffectTest  methods= 11 executions=  55 factor=5.00
inflated_class_count=28
```

and for `TypeTest` the XML root's own attribute disagrees with its body, which is the mechanism:

```
root@tests = 38   testcase elements = 152   distinct names = 38   factor = 4.0
testSupertype present: True
```

`testSupertype` — the class decision B5 predicts will break at S3 — is present and executing.

### 3.3 Nothing is left dormant, and nothing is silently skipped

`skipped=0` everywhere. A source census confirms the profile revives **everything** test-shaped:

```
test source files: 78   (*Test.java 46, Test*.java 3, AllTests.java 14)
test-ish sources (any of @Test / extends TestCase / @ArchTest / junit.framework / @ParameterizedTest): 62
executed classes (surefire+failsafe, profile): 64
DORMANT test-ish classes even under the profile: 0
```

The census reconciles exactly: 62 test-ish sources in `src/test/java` = 14 aggregators + 48
method-bearing classes; the two ITs live in a separate source root
(`use-core/src/it/java/org/tzi/use/OCLExpressionIT.java`,
`use-gui/src/it/java/org/tzi/use/main/shell/ShellIT.java`), giving 48 + 2 = 50 method-bearing
classes and 64 executed files.

### 3.4 Revived inventory: exactly 40 classes / 287 methods

```
REVIVED classes (present under profile, absent from default): 40
REVIVED distinct methods: 287
  use-core org.tzi.use.uml.mm.MImportedModelTest            methods= 55 executions=  55
  use-core org.tzi.use.uml.ocl.type.TypeTest                methods= 38 executions= 152
  use-core org.tzi.use.uml.ocl.expr.ExpQueryTest            methods= 13 executions=  52
  use-core org.tzi.use.parser.soil.ASTConstructionTest      methods= 12 executions=  48
  use-core org.tzi.use.parser.soil.StatementGenerationTest  methods= 12 executions=  48
  use-core org.tzi.use.uml.ocl.expr.ExprNavigationTest      methods= 12 executions=  48
  use-core org.tzi.use.uml.ocl.expr.ExpStdOpTest            methods= 11 executions=  44
  use-core org.tzi.use.uml.ocl.value.ValueTest              methods= 11 executions=  44
  use-core org.tzi.use.uml.sys.soil.StatementEffectTest     methods= 11 executions=  55
  use-core org.tzi.use.architecture.AntCyclicDependenciesCoreTest methods= 10 executions=  10
  ... (30 more; full list in the run log)
```

Gain over the default build: **+40 classes, +287 methods, +38 s wall clock** (01:36 → 02:14).

### 3.5 The profile does not disturb the default tier

Every class that runs by default runs identically under the profile — the vintage engine does not
displace Jupiter:

```
org.tzi.use.architecture.MavenCyclicDependenciesCoreTest   default= 11 profile=11  OK
org.tzi.use.architecture.MavenLayeredArchitectureTest      default=  1 profile=1   OK
org.tzi.use.uml.mm.ModelAPITest                            default=  1 profile=1   OK
...DifferentialHarnessRegressionTest                       default= 35 profile=35  OK
...HistoricalOracleIsolationTest                           default=  9 profile=9   OK
...PortedInfidelityDetectionPowerTest                       default=  7 profile=7   OK
...UncertaintyDifferentialSmokeTest                        default=  6 profile=6   OK
...UnwrittenPortInvariantTest                              default= 10 profile=10  OK
  mismatches = 0
```

Both failsafe tiers are byte-identical across modes (1 and 129 methods).

---

## 4. Task 3 — do any upstream tests fail under the profile?

**No. Zero upstream tests fail.**

```
$ grep -inE '<<< FAILURE|<<< ERROR|BUILD FAILURE|Tests in error|Failed tests|There are test failures' verify-profile.log
  [grep exit=1 : no matches]
```

Every module summary reads `Failures: 0, Errors: 0, Skipped: 0`, and the XML scan agrees across all
four tier/module combinations. There is no failure output to paste, and nothing was fixed, waived or
touched.

This is the expected result and it is *not* evidence that the gate works — see section 5, which
tests that separately.

---

## 5. The gate can actually fail — the experiment the handover did not run

A green gate that cannot go red is worthless, and "zero upstream failures" is exactly the
observation a broken gate would also produce. This was tested directly.

In a **throwaway detached worktree** (`git worktree add --detach`, outside the repo, removed
afterwards; the main checkout was never modified) two probe classes were added — one JUnit 3, one
JUnit 4, each with one passing and one guaranteed-failing assertion — with no other change.

**A. Default build, same two classes:**

```
$ mvn -B -o -pl use-core test -Dtest='Vintage*' -DfailIfNoSpecifiedTests=false
  EXIT=0
[INFO] Tests run: 0, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

A guaranteed-failing JUnit 3 class **and** a guaranteed-failing JUnit 4 class produce
`Tests run: 0` and a green build. This is the dormancy defect demonstrated directly rather than
inferred, and it is the strongest single justification for decision B3 in the record.

**B. Profile, same two classes, unchanged:**

```
$ mvn -B -o -pl use-core test -Pupstream-oracle -Dtest='Vintage*' -DfailIfNoSpecifiedTests=false
  EXIT=1
[INFO] Running org.tzi.use.refuterprobe.VintageFourFailureProbeTest
[ERROR] Tests run: 2, Failures: 1, Errors: 0, Skipped: 0 <<< FAILURE!
[ERROR] ...junit4FailureTurnsTheBuildRed <<< FAILURE!
java.lang.AssertionError: expected:<99> but was:<2>
[INFO] Running org.tzi.use.refuterprobe.VintageFailureProbeTest
[ERROR] Tests run: 2, Failures: 1, Errors: 0, Skipped: 0 <<< FAILURE!
[ERROR] ...testJUnit3FailureTurnsTheBuildRed <<< FAILURE!
junit.framework.AssertionFailedError: a JUnit-3 failure must fail the build expected:<99> but was:<2>
[ERROR] Tests run: 4, Failures: 2, Errors: 0, Skipped: 0
[INFO] BUILD FAILURE
```

**Both dialects are collected, both failures are reported in the framework's own words, and the
build exits 1.** The gate can fail. The worktree was then removed:

```
$ git worktree remove --force .../wt-failprobe && git worktree list
/home/xoruser/msc-4/use-msc2026  b15b5a94 [port-uncertainty-2]
```

---

## 6. Task 4 — the profile's scope

`git log` confirms the poms were touched by **one** commit only (`e3668a04`), `+78 / -0` across both
files, and every added line was read. Each module gained an identical comment block plus:

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

Checked and **absent**: any `<activation>` or `activeByDefault`, any surefire or failsafe
configuration, `includes`/`excludes`, `forkCount`, `reuseForks`, `argLine`, `systemProperty`,
`parallel`, any `<properties>` change, any plugin version change, any non-test scope.

```
$ grep -rn 'activeByDefault\|<activation>' --include=pom.xml .
[end activation]
$ ls ~/.m2/settings.xml
ls: cannot access '/home/xoruser/.m2/settings.xml': No such file or directory
```

With no activation block anywhere and no settings file to carry an `<activeProfiles>`, "off by
default" is **structurally guaranteed**, not merely observed.

`grep -nE 'forkCount|reuseForks|argLine|<systemProperty|parallel'` matches nothing in either pom, so
both runs used surefire defaults — `forkCount=1`, `reuseForks=true`, i.e. **one JVM per module**.
That matters for defect **R-4**.

**No leak into the product.** Test scope cannot reach the artifacts, and this was confirmed rather
than assumed:

```
use-core/target/use-core-7.5.0.jar : vintage entries = 0
use-gui/target/use-gui.jar         : vintage entries = 0
use-gui/target/use-gui-7.5.0.jar   : vintage entries = 0
use-assembly/target/use-7.5.0-use-bin.zip : junit|vintage entries = 0
```

---

## 7. Task 5 — H21, verified against an independent recount

### 7.1 The identity is airtight by construction, not by luck

Every `new DiffRow(...)` site in the package was enumerated. The three that use the 7-argument
(`null`-provenance) constructor produce `UNSUPPORTED` (`DifferentialSweep.java:138`),
`HARNESS_ERROR` (`:202`) and the two throw verdicts (`:212`) — **never `AGREE`**. Both `AGREE` sites
(`:244` exact-equal, `:267` type-only) pass `subjectTypeProvenance(sub)`.

The mismatch population is therefore exactly the `:267` rows. `NONE` requires `javaType == null`,
which `UValue` sets only for the two absence kinds (`nullValue()`, `voidValue()`, `UValue.java:317`,
`:322`), whose `content()` strings (`"NULL(...)"`, `"VOID..."`) can never equal a value kind's
content — so an absence value cannot reach `AGREE` against a non-absence, and if both sides are
absences the columns are equal and the row falls to `:244`, outside the population. The Javadoc's
claim holds.

### 7.2 Independent empirical recount, including a case the test does not cover

A probe (`H21Probe.java`, written by the refuter, compiled against `use-core/target/test-classes`
and run outside the repo) implements `Candidate` directly, builds sweeps, and **recounts the
aggregates from `rows()` itself** rather than trusting the accessors. It includes a **mixed**
provenance sweep — the regression test only exercises homogeneous 0/4 and 4/0.

```
ALL-ASSUMED  rows=6
   my recount   mismatch=6 OBSERVED=0 ASSUMED=6 NONE=0 null=0
   API          mismatch=6 OBSERVED=0 ASSUMED=6
   summary(): URealValue.add(value): 6 rows, 6 measured, 1 distinct ref, AGREE=6,
              javaTypeMismatch=6 (subjectType OBSERVED=0 ASSUMED=6)
   stage()  : ... 6 java-type mismatch(es) (subject token OBSERVED on 0, ASSUMED on 6), ...

MIXED   rows=6
   my recount   mismatch=6 OBSERVED=3 ASSUMED=3 NONE=0 null=0
   API          mismatch=6 OBSERVED=3 ASSUMED=3
   summary(): ... javaTypeMismatch=6 (subjectType OBSERVED=3 ASSUMED=3)
   stage()  : ... 6 java-type mismatch(es) (subject token OBSERVED on 3, ASSUMED on 3), ...

PROBE RESULT: all independent recounts AGREE with the API
```

The identity held, no mismatch row carried `NONE` or `null`, and the **3/3 mixed split renders
correctly in both one-line forms** — a property nothing in the committed suite tests.

### 7.3 The regression test's arithmetic checks out

The fixture's numbers were re-derived from `UValue` rather than taken on trust.
`UValue.integer(7)` defaults to `javaType = "…IntegerValue"` (`UValue.java:297-300`), so
`typeToken()` is `IntegerValue` with provenance `ASSUMED`; `.observedFrom(Integer.valueOf(7))` gives
token `Integer`, `OBSERVED`; `.observedFrom(new AtomicInteger(7))` gives `AtomicInteger`. `content()`
is `INTEGER(7)` in all three cases. So `canonical()` differs while content matches — exactly the
type-only `AGREE` — on all 4 rows of the 2×2 domain, for both sweeps. The asserted 4 / 0-4 / 4-0 are
right.

### 7.4 Both renderings, and the header, carry the split

`stageStatement()` prints it **unconditionally**, including zeros — confirmed 77 times in each
build log:

```
$ grep -c 'java-type mismatch(es) (subject token OBSERVED on' verify-default.log   -> 77
$ grep -c 'java-type mismatch(es) (subject token OBSERVED on' verify-profile.log   -> 77
```

Real output from the perfect-port control:

```
statement URealValue.divideBy(value): 576 rows, 576 measured, 374 agreed, 202 disagreed,
          0 java-type mismatch(es) (subject token OBSERVED on 0, ASSUMED on 0),
          241 distinct reference value(s) [DISCRIMINATING]
```

### 7.5 The goldens moved by exactly 8 insertions, 0 deletions

```
$ git diff --numstat 1ec7d59f^..1ec7d59f -- docs/port2/differential/
4	0	docs/port2/differential/s1-smoke-ureal-add.tsv
4	0	docs/port2/differential/s1-smoke-ureal-minus-faulty.tsv

+# rows.subjectTypeObserved	0
+# rows.subjectTypeAssumed	0
+# op.URealValue.add(value).subjectTypeObserved	0
+# op.URealValue.add(value).subjectTypeAssumed	0
+# rows.subjectTypeObserved	0
+# rows.subjectTypeAssumed	0
+# op.URealValue.minus(value).subjectTypeObserved	0
+# op.URealValue.minus(value).subjectTypeAssumed	0
```

No data row changed, and the new header lines sit adjacent to the `javaTypeMismatch` they split.

Both goldens were re-parsed and **recounted from the data rows**:

```
--- docs/port2/differential/s1-smoke-ureal-add.tsv
    data rows 784 = header rows 784
    AGREE recount 784 = header 784 ; DIFFER recount 0 = header 0
    type-mismatch recount (AGREE & historical!=ported) : 0 = header rows.javaTypeMismatch 0
    IDENTITY 0+0==0 -> True        recount==header on all four : True
--- docs/port2/differential/s1-smoke-ureal-minus-faulty.tsv
    data rows 784 = header rows 784
    AGREE recount 558 = header 558 ; DIFFER recount 226 = header 226
    type-mismatch recount : 0 = header 0
    IDENTITY 0+0==0 -> True        recount==header on all four : True
```

**Documented limit:** the mismatch population in both goldens is **empty**, so the split is
`0 + 0 == 0` — vacuously true. The goldens prove only that H21 broke nothing; **all** positive H21
evidence rests on the regression test and on the probe in 7.2. The porter stated this
("both S1 subjects are type-perfect"); it is recorded here as a limit of the golden artefact, not a
defect.

### 7.6 Determinism

Two consecutive golden refreshes reproduce the committed bytes exactly:

```
committed (git show HEAD:)
  f66af22251fe3a0ebaa1f55e42c019a138e13b8beb23f660361e8b781d671059  s1-smoke-ureal-add.tsv
  a4c3eb7ea17c9ce066069d86bee6c891712364d6a6fc56f4aa4f4e09e354e68b  s1-smoke-ureal-minus-faulty.tsv

REFRESH RUN 1  exit=0 BUILD SUCCESS   f66af222… / a4c3eb7e…   git status of goldens: []
REFRESH RUN 2  exit=0 BUILD SUCCESS   f66af222… / a4c3eb7e…   git status of goldens: []
```

Both hashes match the handover's. The tree stayed clean through both.

---

## 8. Task 6 — no regression in the harness's own instruments

The perfect-port control is intact, in **both** builds:

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
why a PERFECT port is refused elsewhere:
    0 PASS   74
    2 refused: rows disagreed   41
    3 refused on more than one clause   51
    4 refused: not discriminating (D-15)   119
distinct throw-pairs a PERFECT port produces  154
```

**0 divergence, 74 stage passes — confirmed.** The whole control block is byte-identical between the
default and profile runs (`diff` exit 0), and every numeric harness line in the two logs hashes the
same:

```
  default md5: e47e9d3af81c701df4670f86eb8aa6d8
  profile md5: e47e9d3af81c701df4670f86eb8aa6d8
```

The detection-power probes still fire (`P1-off-by-one-index` → `DETECTED on 3 operation(s)`, stage
passes 74 → 70 on the mutants, 67 on the divideBy mutant), and `isClean()` loses nothing
(`193 (control 193) the older predicate loses 0: []`).

---

## 9. Defects

### R-1 (MINOR) — the stated counting rule does not produce the reported class count

`upstream-oracle-profile.md` §2 gives the rule as "class = the XML root's `@name`". Under that rule
the profile run has **64** classes, not 50: the 14 `AllTests` files each have a root `@name` and are
counted. The reported 50 requires the *different* rule "classes contributing at least one
`<testcase>`". Both scans are pasted in §3.1 above. The **method** count (497) is exact under either
rule. Fix: state the rule as "classes contributing ≥ 1 test method (the 14 aggregator files hold
none and are excluded)". The same correction applies to the reconciliation figures 43 / 45 / 48.

### R-2 (MAJOR) — B12 half (a) asserts a class runs that does not exist in the build tree

The handover states: "the JUnit-3 aggregators execute, so `USECompilerUncertaintyTest`'s
process-global write to `Options.explicitVariableDeclarations` runs and suite ORDERING becomes
load-bearing". That class is **not in the port**:

```
$ find use-core use-gui use-assembly -name '*USECompilerUncertaintyTest*' | grep -v target
  [empty]
$ find .git/reference-repositories -name '*USECompilerUncertaintyTest*'
.git/reference-repositories/uncertainty/USE-Uncertainty/src/test/org/tzi/use/parser/uncertainty/USECompilerUncertaintyTest.java
```

It exists only in the read-only reference repository, which is not a build input (ground rule 3). It
did not run and cannot have run. The *mechanism* is nonetheless real, and broader than described —
the actual mutators are two classes that both revive under the profile:

```
$ grep -rn 'explicitVariableDeclarations' use-core/src use-gui/src
use-core/src/main/java/org/tzi/use/config/Options.java:138:    public static boolean explicitVariableDeclarations = true;
use-core/src/test/java/org/tzi/use/parser/soil/StatementGenerationTest.java:64:      Options.explicitVariableDeclarations = false;
use-core/src/test/java/org/tzi/use/parser/USECompilerTest.java:111:        Options.explicitVariableDeclarations = false;
```

Fix: name `USECompilerTest` and `soil/StatementGenerationTest`, and delete the claim about a class
the port does not contain. B12 must be decided on the real population.

### R-3 (MAJOR) — B12 half (b) misstates the mechanism and its conclusion is false

The handover says `USECompilerTest`'s "fixtures resolved from `user.dir + "/src/test/..."`, which
under Maven can list empty and run the loop zero times — so 'it passes' is not evidence that
anything was compiled."

At HEAD the file does not use `user.dir`. It resolves fixtures from the classpath
(`USECompilerTest.java:77-79`) and fails loudly if they are missing:

```java
TEST_PATH     = new File(ClassLoader.getSystemResource("org/tzi/use/parser").toURI());
EXAMPLES_PATH = new File(ClassLoader.getSystemResource("examples").toURI());
TEST_EXPR_FILE= new File(ClassLoader.getSystemResource("org/tzi/use/parser/test_expr.in").toURI());
} catch (NullPointerException | URISyntaxException e) { ... fail("Folders including tests are missing!"); }
```

Resolved at runtime by probe:

```
resource "examples"           -> file:.../use-core/target/classes/examples        .use count=0
resource "org/tzi/use/parser" -> file:.../use-core/target/test-classes/org/tzi/use/parser  .use count=49
```

So `testSpecification` compiles **49 real `.use` specifications**, and "not evidence that anything
was compiled" is false in the load-bearing direction.

A narrower vacuity *is* real and is newly measured here: `EXAMPLES_PATH` contributes **0** files
because the corpus is tree-structured and `listFiles` is not recursive —

```
$ ls use-core/target/classes/examples
Documentation  Makefile  Metamodels  Others  Papers  README.examples  StateMachines  generator  monitoring  soil
$ ls use-core/target/classes/examples/*.use | wc -l        -> 0
$ find use-core/target/classes/examples -name '*.use' | wc -l -> 85
```

All 85 example specs are one level down and none is compiled. That is an upstream defect affecting
upstream identically, and it is what B12 should actually record.

### R-4 (MINOR) — a JVM-global static becomes order-dependent only under the profile

`Options.explicitVariableDeclarations` defaults to `true` (`Options.java:138`). `USECompilerTest:111`
sets it `false` inside a test method; `StatementGenerationTest:64` sets it `false` in `@Before`, so
before each of its 12 methods (48 executions). **Neither restores it**, there is no `@After`, and
both modules run in a single JVM (`forkCount=1`, `reuseForks=true`, plugin defaults — no fork
configuration in either pom). Neither class runs in the default build; both run under the profile.
Consumers exist in main (`ASTStatement.java:287`, `:292`), and other soil tests read the flag
indirectly without setting it.

Probed rather than asserted:

```
$ mvn -B -pl use-core test -Pupstream-oracle -Dsurefire.runOrder=reversealphabetical
EXIT=0
[INFO] Tests run: 938, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

**Latent, not active.** Reversing the order changes nothing today. Recorded because it is a real
stability property of the gate the document should carry, and because it is the correct version of
what R-2 was reaching for.

### R-5 (MINOR) — the non-asserting population is understated, and 12 such methods are in the *default* gate

The handover says "21 of them assert nothing: four ArchUnit classes". All **six** architecture
classes have zero real assertions:

```
AntCyclicDependenciesCoreTest.java     realAssert=0  check=0  fail(=0
MavenCyclicDependenciesCoreTest.java   realAssert=0  check=0  fail(=0
AntCyclicDependenciesGUITest.java      realAssert=0  check=0  fail(=0
AntLayeredArchitectureTest.java        realAssert=0  check=0  fail(=0
MavenCyclicDependenciesGUITest.java    realAssert=0  check=0  fail(=0
MavenLayeredArchitectureTest.java      realAssert=0  check=0  fail(=0
```

They compute a count and `System.out.println` it — e.g. `AntCyclicDependenciesCoreTest`'s ten
methods each call `countCyclesForPackage(...)` and print; `MavenLayeredArchitectureTest` prints a
violation count and writes a report file. Correct figures: **21 of the 287 revived**, **33 of the
497 profile total**, and — not previously stated — **12 of the default build's 210**
(`MavenCyclicDependenciesCoreTest` 11 + `MavenLayeredArchitectureTest` 1). The default gate is
therefore 198 asserting methods, not 210. (Note: a naive
`grep -cE 'assert…'` returns 1 for four of these files; that is a false positive on
`subPackage.isEmpty()` inside the slice assignment. The handover's `assert=0` was right.)

### R-6 (MINOR) — "every `src/test` path is inside `org/tzi/use/uncertainty/differential/`" is false

Two are not:

```
$ git diff --name-status 30d480db..HEAD -- '*/src/test/*' | grep -v 'org/tzi/use/uncertainty/differential/'
A	use-core/src/test/resources/historical/atenearesearchgroup.uncertainty.jar
A	use-core/src/test/resources/historical/use.jar
```

Both are `A`, so no rule is broken, and the handover's *other* formulation ("non-docs, non-harness,
non-jar paths … are exactly four") is correct. Only the sentence quoted above is wrong.

---

## 10. Non-defects worth recording

* **The build writes into the tracked `docs/` tree.** `MavenLayeredArchitectureTest` and the cyclic
  tests write `docs/archunit-results/*-current-failure-report.txt` (775 KB this run). "Tree clean
  after build" holds because `.gitignore:122` covers them, not because the build is side-effect
  free. Pre-existing upstream behaviour; the ignore rule is the right handling.
* **`.gitignore` is doing load-bearing work** for three generated-output families. Correct, and
  documented in the file itself.
* **The H21 blind spot the porter self-declared is real** — a uniformly non-attributing adapter that
  produces zero mismatch rows reports 0/0 and is indistinguishable from a fully observing one. The
  population choice (scoped to `javaTypeMismatchCount()`) is nonetheless the right one: it buys the
  exact partition and it answers the question the brief posed. Accepting the scoping and the blind
  spot as stated.
* **Scope taken beyond literal task 3** (spec §C1, `harness-contract.md` §§1/7/8, three stale "H21
  has no header aggregate" assertions in `foundation-verdict.md`) is **endorsed**. Leaving a
  normative document asserting something the code had just falsified would have been worse. Nothing
  was deleted.

---

## 11. Fitness as the S3–S10 acceptance gate

**Yes — the profile can serve as the gate.** Grounds, in order of weight:

1. **It can fail.** §5: an unmodified JUnit 3 class and an unmodified JUnit 4 class each go
   `Tests run: 0` / green by default and `BUILD FAILURE` / exit 1 under the profile, reporting the
   framework's own assertion text. This is the property that makes every later non-regression claim
   meaningful, and it was the one property the handover had not tested.
2. **It executes upstream's tests verbatim.** 497 distinct methods over 50 method-bearing classes,
   `skipped=0`, **zero** dormant test-shaped classes remaining, and no `M` on any pre-existing
   upstream test on the whole branch.
3. **Its scope is exactly one test-scope dependency.** The resolved test classpath differs by
   precisely one jar in each module, with no transitive downgrade; no surefire/failsafe/fork/argLine
   setting is touched; no activation block and no `settings.xml` exist, so off-by-default is
   structural. No vintage or junit artefact reaches any jar or the assembly zip.
4. **It does not perturb what it must not.** All 8 default surefire classes keep their exact method
   counts, both failsafe tiers are unchanged, and every numeric line of harness output is
   md5-identical between the two builds.
5. **H21 is correct.** An independent recount from `rows()` agrees with the accessors on every
   figure, including a 3/3 mixed split the committed suite does not exercise; the partition identity
   holds by construction, not coincidence; goldens are deterministic to the byte across two
   refreshes.

Conditions the gate should be operated under:

* Quote **210**, not 209, as the default-build figure at HEAD; 209 is `e3668a04`.
* Quote **497 methods / 50 method-bearing classes** and state the counting rule as "classes
  contributing ≥ 1 test method", or quote 64 and say aggregators are included. Never mix the two.
* Never quote the raw `Tests run: 938` headline without the word *executions*.
* Treat **198** (not 210) and **464** (not 497) as the asserting-method figures if the gate is ever
  used to argue coverage; 12 and 33 methods respectively cannot fail (R-5).
* Re-run `-Dsurefire.runOrder=reversealphabetical` under the profile whenever a soil or parser test
  is added, until R-4's unrestored global is fixed or B12 is decided.
* Both commands, every stage, S3 onward — and when `TypeTest#testSupertype` breaks at S3 (B5
  predicts 10 of 12 assertions), that is a decision to record, never a test to edit.

---

## 12. Reproduction

```bash
cd /home/xoruser/msc-4/use-msc2026
mvn -B clean && mvn -B verify -Djava.awt.headless=true                        # 210 methods
mvn -B clean && mvn -B verify -Pupstream-oracle -Djava.awt.headless=true      # 497 methods
```

Deduplicating counter (the figure that matters; the console headline overcounts):

```python
import glob, xml.etree.ElementTree as ET
for tier, d in (("surefire","surefire-reports"), ("failsafe","failsafe-reports")):
    for mod in ("use-core","use-gui"):
        cls=set(); meths=set(); ex=f=e=s=0
        for p in glob.glob(f"{mod}/target/{d}/TEST-*.xml"):
            r=ET.parse(p).getroot(); tcs=r.findall(".//testcase")
            if not tcs: continue                      # the 14 AllTests aggregators
            cls.add(r.get("name"))
            meths |= {(r.get("name"), t.get("name")) for t in tcs}
            ex+=len(tcs); f+=len(r.findall(".//failure"))
            e+=len(r.findall(".//error")); s+=len(r.findall(".//skipped"))
        print(f"{tier:9} {mod:9} classes={len(cls):3} methods={len(meths):4} "
              f"executions={ex:4} failures={f} errors={e} skipped={s}")
```

Classpath non-leak:

```bash
mvn -B dependency:tree -Dincludes='org.junit.vintage:*'                    # 0 vintage lines
mvn -B dependency:tree -Pupstream-oracle -Dincludes='org.junit.vintage:*'  # 2 vintage lines
mvn -B dependency:build-classpath -Dmdep.includeScope=test \
    -Dmdep.outputFile=target/testcp.txt -Dmdep.regenerateFile=true         # diff the two sorted
```

Gate-can-fail probe: `git worktree add --detach <scratch> HEAD`, add a JUnit-3 and a JUnit-4 class
each containing one failing assertion, run `-pl use-core test` with and without the profile, then
`git worktree remove --force <scratch>`. Never in the main checkout.

---

## 13. Final state

```
$ git diff --name-status 30d480db..HEAD -- '*/src/main/*'        -> EMPTY
$ git diff --name-status 30d480db..HEAD -- '*module-info.java'   -> EMPTY
$ git diff --name-status 30d480db..HEAD -- 'use-gui/src/*'       -> EMPTY
$ git diff --name-status 30d480db..HEAD -- 'use-assembly/src/*'  -> EMPTY
$ git diff --name-status 30d480db..HEAD -- '*/src/test/*' | awk '$1!="A"'  -> EMPTY
```

Both full verifies exited 0 with `BUILD SUCCESS`; the tree was clean after each (modulo untracked
files created by the other session, which the refuter did not touch). One `git worktree` was created
in the scratchpad and removed; `git worktree list` shows only the main checkout.
