# S1 — Empirical refutation of the differential harness

> **⚠ FORWARD POINTER (added 2026-08-17, after round 4) — the verdict vocabulary has changed since
> this document was written.** This report is left exactly as it was written; it was true on its
> date. Two changes to `DiffVerdict` have happened since:
>
> * **`AGREE_THROWN` and `DIFFER_THROWN` were deleted** in commit `e8b73e48`. Two throws are never
>   an agreement, whatever their classes. Wherever this document names either constant as a live
>   verdict it is describing the code as it stood before `e8b73e48`. They are replaced by the single
>   non-agreement **`BOTH_THREW`** (whose note always carries both classes *and* both messages), plus
>   the opt-in, human-authored **`ACCEPTED_THROW`**.
> * **`UNMEASURABLE` was added** in commit `93e038ac`, for rows where neither side carried an
>   observation (`VOID`/`VOID`, `NULL`/`NULL`). Those rows used to be scored `AGREE`.
>
> The current vocabulary is `AGREE, ACCEPTED_THROW, DIFFER, BOTH_THREW, MIXED, UNMEASURABLE,
> UNSUPPORTED, HARNESS_ERROR`, defined in
> `use-core/src/test/java/org/tzi/use/uncertainty/differential/DiffVerdict.java` and summarised in
> [`harness-contract.md`](harness-contract.md). **The current S1 verdict is `stage-01.md` §10 — not
> this file.**


Adversarial re-run of stage S1 by an agent who did not write the code and was not shown the
author's reasoning. Everything below was executed on branch `port-uncertainty-2` at
`/home/xoruser/msc-4/use-msc2026`, Java 21.0.11, Maven 3.9.16. All output is pasted verbatim.

**Verdict: DEFECTIVE — but not where it matters most.**

Every specific claim in `stage-01.md` that I tested reproduced exactly. The isolation is real and
survives a deliberate poisoning attack that is *stronger* than the one the harness's own tests run.
All seven defects I planted were detected. However, I confirmed a reachable channel by which a
genuine mismatch is scored as agreement — including a 169-row sweep that reports **100 % agreement
while neither side ever invoked the operation under test**. That is filed as `D1` and it is the
reason for the verdict.

---

## Summary table

| # | Check | Result |
|---|---|---|
| 1 | Acceptance command runs as claimed | **Reproduced exactly** |
| 2 | Two runs byte-identical | **Reproduced** (also identical to the committed reports) |
| 3 | Deliberate isolation break | **Isolation held** under poisoning of both namespaces |
| 4 | Missing jar / mis-pointed override | **Loud.** BUILD FAILURE, errors not skips |
| 5 | Planted defects detected | **7 of 7 detected** |
| 5b | Can a mismatch be swallowed? | **YES — see D1, D2** |
| 6 | Test counts | 4 classes / 27 methods, delta +2 / +15, fully accounted |
| 7 | No pre-existing file modified | **Confirmed** — 15 paths, all `A` |
| 8a | Platform loader insufficient under JPMS | **Confirmed independently** |
| 8b | `UStringValue.at` is 1-based, `at(0)` throws | **Confirmed independently** |

---

## 1. The acceptance command

```bash
mvn -q clean
mvn -B -pl use-core test -Dtest=UncertaintyDifferentialSmokeTest
```
```
EXIT=0
...
report               /home/xoruser/msc-4/use-msc2026/docs/port2/differential/s1-smoke-ureal-add.tsv
===================================================================
=== S1 fault-injection check ======================================
seed                 20260817
subject              stub-faulty-minus  (minus uses |ua-ub|)
rows                 784
tally                URealValue.minus(value): 784 rows, AGREE=558, DIFFER=226
--- first 5 disagreements -----------------------------------------
index	operation	inputs	historical	ported	verdict	note
29	URealValue.minus(value)	UREAL(0.0,1.0) | UREAL(0.0,1.0)	UREAL(0.0,1.4142135623730951)	UREAL(0.0,0.0)	DIFFER	
32	URealValue.minus(value)	UREAL(0.0,1.0) | UREAL(1.0,1.0)	UREAL(-1.0,1.4142135623730951)	UREAL(-1.0,0.0)	DIFFER	
34	URealValue.minus(value)	UREAL(0.0,1.0) | UREAL(-1.0,1.0)	UREAL(1.0,1.4142135623730951)	UREAL(1.0,0.0)	DIFFER	
35	URealValue.minus(value)	UREAL(0.0,1.0) | UREAL(-1.0,0.5)	UREAL(1.0,1.118033988749895)	UREAL(1.0,0.5)	DIFFER	
36	URealValue.minus(value)	UREAL(0.0,1.0) | UREAL(0.5,0.5)	UREAL(-0.5,1.118033988749895)	UREAL(-0.5,0.5)	DIFFER	
report               /home/xoruser/msc-4/use-msc2026/docs/port2/differential/s1-smoke-ureal-minus-faulty.tsv
===================================================================
[INFO] Tests run: 6, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.292 s -- in Uncertainty differential smoke
[INFO] Tests run: 6, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
[INFO] Total time:  11.284 s
```

Row counts, tallies and the first rows match `stage-01.md` §5.1 character for character.

---

## 2. Determinism

Run 1 output was first compared against the **committed** reports, then a second run was compared
against run 1.

```
=== git status after run1 ===
(empty = run1 output identical to committed)
=== run1 vs committed ===
add: IDENTICAL to committed
minus: IDENTICAL to committed
```
```
EXIT=0
[INFO] Tests run: 6, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.378 s -- in Uncertainty differential smoke
[INFO] BUILD SUCCESS
=== diff run1 vs run2 (add) ===
diff exit=0
=== diff run1 vs run2 (minus) ===
diff exit=0
=== cmp bytes ===
add BYTE-IDENTICAL
minus BYTE-IDENTICAL
=== sha256 all four ===
549d33e61eb219a47dabff673de5da0bda42caeb348de0df6ef0d583823e3050  run1-add.tsv
549d33e61eb219a47dabff673de5da0bda42caeb348de0df6ef0d583823e3050  run2-add.tsv
21b3923027f3c12a31d48d5955ed7a1e0461123fe9c5dac2250993c686b4b220  run1-minus.tsv
21b3923027f3c12a31d48d5955ed7a1e0461123fe9c5dac2250993c686b4b220  run2-minus.tsv
```

The diff is empty. Determinism holds. No defect here.

---

## 3. Breaking the isolation on purpose

The harness's own negative controls only compare **class identity** for `RealValue`. I attacked
harder, on two fronts, by adding real classes to `use-core/src/test/java`:

* `org/tzi/use/uml/ocl/value/URealValue.java` — same FQN as the class in `use.jar`, i.e. exactly
  what S4 will look like when the port lands. Every accessor returns `999.0`.
* `uDataTypes/UReal.java` — same FQN as the class inside the uncertainty jar, with `getX()`,
  `getU()`, `add()` all returning `999.0`. **This is the sharper attack:**
  `HistoricalOracle.assertIsolated()` checks a hard-coded list of twelve
  `org.tzi.use.uml.ocl.value.*` simple names and **never checks any `uDataTypes.*` class at all**,
  so nothing but `IsolatedJarClassLoader` itself stands between this class and a silently corrupted
  oracle. The historical `URealValue(double,double)` constructs `new uDataTypes.UReal(d,d)` and
  `value()`/`uncertainty()` delegate to `UReal.getX()`/`getU()` — verified by `javap -c` — so a leak
  here would produce wrong numbers, not an exception.

Both poison classes compiled into `target/test-classes`:

```
use-core/target/test-classes/org/tzi/use/uml/ocl/value/:
-rw-r--r-- 1 xoruser xoruser  936 Aug 17 04:26 URealValue.class
use-core/target/test-classes/uDataTypes/:
-rw-r--r-- 1 xoruser xoruser 1718 Aug 17 04:26 UReal.class
```

Result:

```
EXIT=0
[INFO] Tests run: 6, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.217 s -- in Uncertainty differential smoke
PROBE3 under poison: URealValue.add(value): 784 rows, AGREE=784
PROBE2 oracle uDataTypes.UReal   = class uDataTypes.UReal  loader=IsolatedJarClassLoader[historical-oracle, parent-last for org.tzi.use., uDataTypes., urls=[file:/home/xoruser/msc-4/use-msc2026/use-core/target/test-classes/historical/use.jar, file:/home/xoruser/msc-4/use-msc2026/use-core/target/test-classes/historical/atenearesearchgroup.uncertainty.jar]]
PROBE2 oracle (1.5,0.25) add (2.5,0.5) = UREAL(4.0,0.5590169943749475)
PROBE1 historical URealValue     = class org.tzi.use.uml.ocl.value.URealValue  loader=IsolatedJarClassLoader[historical-oracle, parent-last for org.tzi.use., uDataTypes., ...]
PROBE1 historical URealValue has POISON_MARKER = false
PROBE0 app-loader URealValue    = class org.tzi.use.uml.ocl.value.URealValue  loader=jdk.internal.loader.ClassLoaders$AppClassLoader@4b9e13df
PROBE0 app-loader uDataTypes.UReal = class uDataTypes.UReal  loader=jdk.internal.loader.ClassLoaders$AppClassLoader@4b9e13df
PROBE0 poison URealValue(1.5,0.25).value() = 999.0
[INFO] Tests run: 10, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

The poison is genuinely on the application class loader and genuinely returns `999.0`. The oracle
resolved both classes from its own loader, and the arithmetic
`(1.5,0.25) add (2.5,0.5) = UREAL(4.0,0.5590169943749475)` is the jar's answer, not `999.0`. The
full 784-row sweep still agreed. **The isolation is real, including for the namespace the built-in
guard does not police.**

I also checked the isolation test is not vacuous under the simulated port landing:

```
[ERROR] HistoricalOracleIsolationTest.uTypesResolveOnlyThroughTheOracle:160 URealValue unexpectedly
resolves on the application loader. If the port has landed, this test must be updated to assert
distinctness instead of absence -- do NOT delete it. ==> Expected java.lang.ClassNotFoundException
to be thrown, but nothing was thrown.
[ERROR] Tests run: 9, Failures: 1, Errors: 0, Skipped: 0
[INFO] BUILD FAILURE
```

It fires, with the message the author documented. Good.

Scratch classes removed, `mvn -q clean`, tree clean:

```
=== git status ===
(empty above = CLEAN)
=== find any scratch leftovers ===
(none outside target/, which clean removes)
```

---

## 4. The missing-jar path

```bash
mv use-core/src/test/resources/historical/use.jar <scratch>/use.jar.parked
mvn -q clean && mvn -B -pl use-core test -Dtest=UncertaintyDifferentialSmokeTest
```
```
EXIT=1
[ERROR] Tests run: 6, Failures: 0, Errors: 4, Skipped: 0, Time elapsed: 0.125 s -- in Uncertainty differential smoke
[ERROR] Tests run: 6, Failures: 0, Errors: 4, Skipped: 0
[INFO] BUILD FAILURE
```
```
org.tzi.use.uncertainty.differential.HistoricalOracle$HistoricalOracleUnavailableException: 
historical oracle jar 'use.jar' was not found. It must be committed at use-core/src/test/resources/historical/use.jar so that Maven copies it to target/test-classes/historical/. Paths tried, in order:
  code source of HistoricalOracle -> /home/xoruser/msc-4/use-msc2026/use-core/target/test-classes/historical/use.jar
  relative to cwd -> /home/xoruser/msc-4/use-msc2026/use-core/use-core/target/test-classes/historical/use.jar
  relative to cwd -> /home/xoruser/msc-4/use-msc2026/use-core/target/test-classes/historical/use.jar
  relative to cwd -> /home/xoruser/msc-4/use-msc2026/use-core/use-core/src/test/resources/historical/use.jar
  relative to cwd -> /home/xoruser/msc-4/use-msc2026/use-core/src/test/resources/historical/use.jar
  classloader resource historical/use.jar
	at use.core@7.5.0/org.tzi.use.uncertainty.differential.HistoricalOracle.materialise(HistoricalOracle.java:199)
	at use.core@7.5.0/org.tzi.use.uncertainty.differential.HistoricalOracle.open(HistoricalOracle.java:129)
	at use.core@7.5.0/org.tzi.use.uncertainty.differential.UncertaintyDifferentialSmokeTest.smokeURealAdd(UncertaintyDifferentialSmokeTest.java:41)
```

**Judgement: genuinely loud.** `Errors: 4, Skipped: 0` and `BUILD FAILURE`. There is no skip path —
`HistoricalOracleUnavailableException` is an unchecked `IllegalStateException` and nothing catches
it. The two tests that still pass (`seededGenerationIsReplayable`, `boundaryCoverage`) do not touch
the oracle, which is correct.

### Mis-pointed override

```bash
mvn -B -pl use-core test -Dtest=UncertaintyDifferentialSmokeTest -Duse.historical.jars.dir=<empty dir>
```
```
MISPOINTED-EMPTY EXIT=1
[ERROR] Tests run: 6, Failures: 0, Errors: 4, Skipped: 0
[INFO] BUILD FAILURE
--- error text ---
org.tzi.use.uncertainty.differential.HistoricalOracle$HistoricalOracleUnavailableException: 
historical oracle jar 'use.jar' was not found and -Duse.historical.jars.dir is set, so no other location was consulted. Paths tried:
  -Duse.historical.jars.dir (authoritative) -> /tmp/.../scratchpad/emptydir/use.jar
	at use.core@7.5.0/org.tzi.use.uncertainty.differential.HistoricalOracle.materialise(HistoricalOracle.java:178)
```

The override is authoritative with no silent fallback, as claimed. Restored and re-verified:

```
53b2a43feb0a0a39844a60278dd80a7d4b975ef324fb05c6db28831e835e59d0  atenearesearchgroup.uncertainty.jar
80ac8ae433b8345677472019991356950f094f4a104cfbce1f75783a7308788d  use.jar
```

### D4 — the stale-`target` false pass (documented, but confirmed)

`stage-01.md` §5.3 notes in passing that `mvn -q clean` is required. I confirmed the consequence is
a **green build with the source jar deleted**:

```
--- step5: delete SOURCE jar only ---
source dir: atenearesearchgroup.uncertainty.jar
target dir: atenearesearchgroup.uncertainty.jar
use.jar
--- step6: surefire only, no resources phase ---
SUREFIRE-ONLY EXIT=0
[INFO] Tests run: 6, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.225 s -- in Uncertainty differential smoke
[INFO] BUILD SUCCESS
```

Damage is bounded: the digest check means the stale copy is still the *correct* bytes, so this can
mask the jar's absence but cannot produce wrong results. Filed MINOR.

---

## 5. THE DECISIVE CHECK — planted defects

Seven candidates, none of them `StubCandidate.faultyMinus`, swept against `URealValue.add` over the
784-row corpus.

```
DEFECT1 one-ULP value error      : URealValue.add(value): 784 rows, AGREE=109, DIFFER=675
DEFECT2 one-ULP uncertainty error: URealValue.add(value): 784 rows, AGREE=108, DIFFER=676
DEFECT3 Math.hypot substitution  : URealValue.add(value): 784 rows, AGREE=770, DIFFER=14
DEFECT4 -0.0 for 0.0             : URealValue.add(value): 784 rows, AGREE=740, DIFFER=44
DEFECT5 REAL instead of UREAL    : URealValue.add(value): 784 rows, DIFFER=784
DEFECT6 always throws            : URealValue.add(value): 784 rows, MIXED=784
DEFECT7 unsupported              : URealValue.add(value): 784 rows, UNSUPPORTED=784
[INFO] Tests run: 10, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

**7 of 7 detected.** A single-ULP error in either field is reported. `Math.hypot` — the exact trap
the author documented — is caught on 14 rows. `-0.0` vs `0.0` is caught, as the exact-comparison
design intends. A wrong result *kind* is caught on every row. A candidate that only throws is
`MIXED` (not agreement) and one that does not implement the operation is `UNSUPPORTED` (not
agreement). The instrument can say no.

### The converse — D1: a genuine mismatch IS swallowed

`DiffVerdict.AGREE_THROWN.isAgreement()` returns `true`, and `classify()` scores two throws as
agreement whenever the **throwable class names match** — regardless of cause, message, or which
code actually threw. `HistoricalOracle.invoke()` and `toHistorical()` throw
`IllegalArgumentException` / `IllegalStateException` **from the harness's own marshalling, before
the historical method is ever called**, and those are indistinguishable in the report from
exceptions thrown by the implementations.

**SWALLOW B — an entire sweep reads as agreement while neither side ran the operation.**
`URealValue.add` swept over `InputGenerator.uIntegerBoundaries()` as receivers:

```
SWALLOW-B URealValue.add(value): 169 rows, AGREE_THROWN=169
SWALLOW-B disagreements = 0
SWALLOW-B first row: 0	URealValue.add(value)	UINTEGER(0,0.0) | UINTEGER(0,0.0)	THROWN:java.lang.IllegalArgumentException	THROWN:java.lang.IllegalArgumentException	AGREE_THROWN	
SWALLOW-B AGREE_THROWN counts as agreement: true
```

**SWALLOW C — this passes the smoke test's own pass criterion.**
`UncertaintyDifferentialSmokeTest.smokeURealAdd` asserts
`assertEquals(List.of(), result.disagreements())`. On the no-op sweep above:

```
SWALLOW-C smoke-test-style assertion on a no-op sweep:
SWALLOW-C   result.disagreements() = []
SWALLOW-C   rowCount=169 AGREE=0 AGREE_THROWN=169
```

The note column is empty (classes matched), so the report carries no signal at all that the
harness, rather than the implementation, failed 169 times.

**Reachability with the harness's OWN shipped corpora.** This is not a contrived receiver-type
mistake. Three values in the documented boundary corpora cannot even be constructed:

```
REACH uRealBoundaries: 22/22 constructible
REACH uIntegerBoundaries: 13/13 constructible
REACH uBooleanBoundaries CONSTRUCTION FAILED UBOOLEAN(false,-1.0) -> java.lang.IllegalStateException: historical constructor threw for UBOOLEAN(false,-1.0)
REACH uBooleanBoundaries CONSTRUCTION FAILED UBOOLEAN(false,2.0) -> java.lang.IllegalStateException: historical constructor threw for UBOOLEAN(false,2.0)
REACH uBooleanBoundaries: 7/9 constructible
REACH uStringBoundaries CONSTRUCTION FAILED USTRING("abc",-1.0) -> java.lang.IllegalStateException: historical constructor threw for USTRING("abc",-1.0)
REACH uStringBoundaries: 15/16 constructible
REACH zeroDivisors: 7/7 constructible
```

So a candidate that does nothing but `throw new IllegalStateException(...)` collects free
"agreements" on the shipped `uBooleanBoundaries()` corpus — **32 of 81 rows, 39.5 %**:

```
REACH uBoolean.and vs always-ISE candidate: UBooleanValue.and(value): 81 rows, AGREE_THROWN=32, MIXED=49
REACH   disagreements = 49 / 81
REACH   swallowed row: 7	UBooleanValue.and(value)	UBOOLEAN(true,0.0) | UBOOLEAN(false,-1.0)	THROWN:java.lang.IllegalStateException	THROWN:java.lang.IllegalStateException	AGREE_THROWN	
REACH   swallowed row: 63	UBooleanValue.and(value)	UBOOLEAN(false,-1.0) | UBOOLEAN(true,0.0)	THROWN:java.lang.IllegalStateException	THROWN:java.lang.IllegalStateException	AGREE_THROWN	
   (…30 more identical-shaped rows…)
```

The moment S2/S3 widen coverage to `UBooleanValue` and `UStringValue` — which the stage report
lists as required work — a fraction of every sweep becomes uninformative but *scored as passing*.

### D2 — exception comparison is class-name only

```
SWALLOW-A UStringValue.at(int): 1 rows, AGREE_THROWN=1
SWALLOW-A row: 0	UStringValue.at(int)	USTRING("abc",0.5) | INTEGER(0)	THROWN:java.lang.IndexOutOfBoundsException	THROWN:java.lang.IndexOutOfBoundsException	AGREE_THROWN	
SWALLOW-A disagreements = 0
```

Here the historical side threw `IndexOutOfBoundsException: idx = 0` from `uDataTypes.UString.at`,
and the candidate threw `IndexOutOfBoundsException: PLANTED: this candidate never implemented
UStringValue.at(int)`. Scored as agreement, empty note. The `Candidate` javadoc does state that
class names are what is compared, so this is a documented design choice — but it erases message and
cause, and it is the mechanism that makes D1 possible.

Worth noting how thin the margin is: the historical `at(0)` throws `IndexOutOfBoundsException` while
the sibling `uAt(0)` throws `IllegalArgumentException: lower should be greater than 0`. A port that
gets these the wrong way round *would* be caught; a port that throws the right class for the wrong
reason would not.

### D3 — the empty-report guard does not do what its message says

`DiffReportWriter.writeAll` refuses an empty *results list* with the message "refusing to write an
empty differential report: a report with no rows would read as agreement". It does not check the
*row count*. A zero-row sweep is written and reads as clean:

```
EMPTY rowCount        = 0
EMPTY summary         = URealValue.add(value): 0 rows
EMPTY disagreements   = []
EMPTY reads as clean? = true
EMPTY report body:
EMPTY | # harness	differential-sweep/1
EMPTY | # seed	20260817
EMPTY | # rows	0
EMPTY | index	operation	inputs	historical	ported	verdict	note
```

---

## 6. Test counts

```bash
mvn -q clean && mvn -B -pl use-core test
```
```
EXIT=0
=== surefire XML (authoritative) ===
name="org.tzi.use.architecture.MavenCyclicDependenciesCoreTest" tests="11"
name="org.tzi.use.uml.mm.ModelAPITest" tests="1"
name="org.tzi.use.uncertainty.differential.HistoricalOracleIsolationTest" tests="9"
name="org.tzi.use.uncertainty.differential.UncertaintyDifferentialSmokeTest" tests="6"
classes=4  methods=27
[INFO] Tests run: 27, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

I did not take the 2 / 12 baseline on faith. I parked the S1 sources and resources aside and
measured it:

```
BASELINE EXIT=0
[INFO] Tests run: 11, ... -- in org.tzi.use.architecture.MavenCyclicDependenciesCoreTest
[INFO] Tests run: 1,  ... -- in org.tzi.use.uml.mm.ModelAPITest
[INFO] Tests run: 12, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
=== baseline surefire classes ===
TEST-org.tzi.use.architecture.MavenCyclicDependenciesCoreTest.xml
TEST-org.tzi.use.uml.mm.ModelAPITest.xml
```

**Baseline 2 classes / 12 methods → 4 / 27. Delta exactly +2 classes / +15 methods**
(`HistoricalOracleIsolationTest` 9 + `UncertaintyDifferentialSmokeTest` 6). Every added method is
accounted for and the two pre-existing classes are unchanged at 11 and 1.

Full reactor after `mvn -q clean`: `use-core` 27, `use-gui` 1 (`MavenLayeredArchitectureTest`),
`BUILD SUCCESS`. That is 5 classes / 28 methods against the stated whole-reactor baseline of
3 / 13 — the same +2 / +15.

Both added classes are JUnit 5 Jupiter, and the engine situation is as the report describes:

```
[INFO] |  \- junit:junit:jar:4.13.2:test
[INFO] \- org.junit.jupiter:junit-jupiter:jar:5.7.0:test
[INFO]    \- org.junit.jupiter:junit-jupiter-engine:jar:5.7.0:test
```

JUnit 4 is on the test classpath (so a JUnit 4 test compiles) but there is **no
`junit-vintage-engine`**, so it would never execute. Both S1 classes appear in the surefire XML,
so they genuinely ran.

---

## 7. No pre-existing file modified

```bash
git -C /home/xoruser/msc-4/use-msc2026 diff --stat 30d480db..HEAD -- '*/src/test/*' '*/src/main/*'
```
```
 .../use/uncertainty/differential/Candidate.java    |  38 ++
 .../uncertainty/differential/DiffReportWriter.java | 139 +++++
 .../tzi/use/uncertainty/differential/DiffRow.java  | 102 ++++
 .../use/uncertainty/differential/DiffVerdict.java  |  35 ++
 .../differential/DifferentialSweep.java            | 252 ++++++++
 .../uncertainty/differential/HistoricalOracle.java | 639 +++++++++++++++++++++
 .../HistoricalOracleIsolationTest.java             | 277 +++++++++
 .../uncertainty/differential/InputGenerator.java   | 246 ++++++++
 .../differential/IsolatedJarClassLoader.java       | 100 ++++
 .../uncertainty/differential/StubCandidate.java    | 138 +++++
 .../org/tzi/use/uncertainty/differential/UOp.java  | 100 ++++
 .../tzi/use/uncertainty/differential/UValue.java   | 277 +++++++++
 .../UncertaintyDifferentialSmokeTest.java          | 197 +++++++
 .../historical/atenearesearchgroup.uncertainty.jar | Bin 0 -> 77674 bytes
 use-core/src/test/resources/historical/use.jar     | Bin 0 -> 1440303 bytes
 15 files changed, 2540 insertions(+)
```

Filtering for anything that is not an addition:

```bash
git diff --name-status --diff-filter=MDRCT 30d480db..HEAD -- '*/src/test/*' '*/src/main/*'
```
```
(empty)
```

All 15 paths are `A`, all under
`use-core/src/test/java/org/tzi/use/uncertainty/differential/` and
`use-core/src/test/resources/historical/`. **No rule violation.** `module-info.java` untouched.

---

## 8. The two load-bearing claims

### 8a — `getPlatformClassLoader()` is genuinely insufficient here

Reproduced independently, printing raw loader and module identities rather than asserting:

```
JPMS app loader        = jdk.internal.loader.ClassLoaders$AppClassLoader@4b9e13df
JPMS platform loader   = jdk.internal.loader.ClassLoaders$PlatformClassLoader@275bf9b3
JPMS this class module = module use.core
JPMS boot layer has use.core = true
JPMS use.core classloader   = jdk.internal.loader.ClassLoaders$AppClassLoader@4b9e13df
JPMS via app loader      -> class org.tzi.use.uml.ocl.value.RealValue defined by jdk.internal.loader.ClassLoaders$AppClassLoader@4b9e13df module module use.core
JPMS via PLATFORM loader -> class org.tzi.use.uml.ocl.value.RealValue defined by jdk.internal.loader.ClassLoaders$AppClassLoader@4b9e13df module module use.core
JPMS platform == app class ? true   <-- if true, getPlatformClassLoader() is NOT a clean parent
JPMS platform-parented URLClassLoader -> class org.tzi.use.uml.ocl.value.RealValue defined by jdk.internal.loader.ClassLoaders$AppClassLoader@4b9e13df
JPMS   == app class ? true   <-- if true, the jar's copy was SHADOWED by the app class
JPMS system-parented URLClassLoader   -> defined by jdk.internal.loader.ClassLoaders$AppClassLoader@4b9e13df  == app class ? true
JPMS IsolatedJarClassLoader           -> defined by IsolatedJarClassLoader[historical-oracle, parent-last for org.tzi.use., uDataTypes., ...]  == app class ? false
```

**Claim CONFIRMED.** `use.core` is in the boot layer, defined by the *application* loader. The
platform loader returns that application class. A platform-parented `URLClassLoader` shadows the
jar's copy. Only `IsolatedJarClassLoader` returns a distinct class. The author's diagnosis is
correct and the fix is the right one.

### 8b — `UStringValue.at(int)` is 1-based and `at(0)` throws

```
AT   "abc".at(-1) -> THROWN java.lang.IndexOutOfBoundsException: idx = -1
AT   "abc".at(0) -> THROWN java.lang.IndexOutOfBoundsException: idx = 0
AT   "abc".at(1) -> STRING("a")
AT   "abc".at(2) -> STRING("b")
AT   "abc".at(3) -> STRING("c")
AT   "abc".at(4) -> THROWN java.lang.IndexOutOfBoundsException: idx = 4
UAT  "abc".uAt(0) -> THROWN java.lang.IllegalArgumentException: lower should be greater than 0
UAT  "abc".uAt(1) -> USTRING("a",0.5)
```

**Claim CONFIRMED**, and sharpened: indices 1..3 map to `a`,`b`,`c`; 0 and 4 both throw. The
sibling `uAt(0)` throws a *different* exception class (`IllegalArgumentException`), which the stage
report does not mention and which S2+ should record.

---

## 9. Other observations

### D5 — tests write into a tracked directory

`DiffReportWriter.REPORT_DIR = "docs/port2/differential"` and both reports are tracked in git. Every
`mvn test` rewrites tracked files. It is clean today only because the output is byte-stable; the
first time a candidate changes, `mvn test` will dirty the working tree as a side effect. Also,
`reportDir()` falls back to "create under the parent anyway" when it cannot locate `docs/port2`,
so an unexpected working directory writes outside the repository rather than failing.

### D6 — an intermittent JPMS incremental-compile failure I could not attribute

Twice in roughly twenty Maven invocations, a rebuild without `clean` failed at `testCompile`:

```
[ERROR] COMPILATION ERROR : 
[ERROR] .../use-core/src/it/java/org/tzi/use/OCLExpressionIT.java:[8,23] package org.tzi.use.api does not exist
[ERROR] .../use-core/src/test/java/org/tzi/use/TestSystem.java:[26,26] cannot find symbol
```

In both failures the test-compile line had lost module-path mode
(`Compiling 65 source files with javac [debug target 21] to target/test-classes` — note the missing
`module-path`), which explains why an exported package became invisible. **I could not reproduce it
deterministically**: four consecutive no-clean re-runs with S1 present all succeeded, as did a
full-reactor build after a `-pl use-core` build, and a no-clean re-run at the parked baseline also
succeeded. The affected files (`TestSystem.java`, `OCLExpressionIT.java`) are pre-existing and
untouched by S1. **I can neither attribute this to S1 nor rule it out** — the same honest position
`stage-01.md` §6.5 takes about its own transient, and my two sightings corroborate that something
real is there. Always recoverable with `mvn clean`.

### Defensive gap (no defect demonstrated)

`HistoricalOracle.assertIsolated()` is applied only to twelve hard-coded simple names in
`org.tzi.use.uml.ocl.value`. No `uDataTypes.*` class is ever checked, even though the historical
arithmetic lives there. I demonstrated in §3 that the loader *does* isolate that namespace, so
there is no live bug — but the guard that is advertised as catching self-comparison would not catch
a regression in `IsolatedJarClassLoader.ISOLATED_PREFIXES` affecting `uDataTypes.`.

---

## 10. Working tree

Every scratch artefact was removed.

```
$ mvn -q clean
$ git status --porcelain
(empty)
$ git status --porcelain --untracked-files=all
(empty)
```

Jars re-verified after all the moving about:

```
53b2a43feb0a0a39844a60278dd80a7d4b975ef324fb05c6db28831e835e59d0  atenearesearchgroup.uncertainty.jar
80ac8ae433b8345677472019991356950f094f4a104cfbce1f75783a7308788d  use.jar
```

---

## 11. Defects filed

| ID | Severity | Where | What |
|---|---|---|---|
| D1 | **MAJOR** | `DifferentialSweep.classify`, `DiffVerdict.isAgreement` | Harness-internal marshalling failures on both sides score as `AGREE_THROWN`, so `disagreements()` — the pass criterion the smoke test uses — returns empty for a 169-row sweep in which the operation was never invoked. Reachable with the shipped `uBooleanBoundaries()` corpus: 32/81 rows free "agreement" for a candidate that only throws. |
| D2 | MINOR | `DifferentialSweep.classify` | Two throws agree on throwable **class name** alone; message and cause are discarded and the note is left empty. Documented, but it is the mechanism behind D1. |
| D3 | MINOR | `DiffReportWriter.writeAll` | The "refusing to write an empty differential report" guard checks the results list, not the row count. A zero-row sweep is written and reads as clean. |
| D4 | MINOR | `HistoricalOracle.candidateLocations` | A stale `target/test-classes/historical/` copy makes a deleted source jar pass green. Disclosed in `stage-01.md` §5.3; digest check bounds the damage. |
| D5 | MINOR | `DiffReportWriter.REPORT_DIR`, `reportDir()` | Tests write into a git-tracked directory, so a future run dirties the tree as a side effect; the cwd fallback can write outside the repository instead of failing. |
| D6 | MINOR | build | Intermittent no-clean `testCompile` failure with module-path dropped. Two sightings, not reproducible, **not attributed** to S1. |

## 12. Recommended fixes (not applied — I file, I do not fix)

1. **D1 is the one that matters.** Separate harness failures from implementation failures: have
   `HistoricalOracle` wrap its own marshalling/dispatch errors in a dedicated
   `HarnessMarshallingException` and have `DifferentialSweep.classify` give any row involving one a
   non-agreement verdict (say `HARNESS_ERROR`). A row where the harness could not run the operation
   must never be scored as agreement.
2. Record the throwable **message** in the note column for `AGREE_THROWN` rows too, so the report
   shows what was thrown, not merely that classes matched.
3. Make `DiffReportWriter` reject a zero-row report, matching what its error message already claims.
4. Assert a minimum expected row count, and assert `count(AGREE) == rowCount()` rather than
   `disagreements().isEmpty()`, wherever a sweep is expected to be fully computed.
5. Extend `assertIsolated` to a `uDataTypes.*` class so the guard covers both isolated namespaces.
