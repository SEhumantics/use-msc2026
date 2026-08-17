# Stage S1 — post-fix empirical verification of the differential harness

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


**Role:** empirical refuter. I did not write the F1–F11 fixes.
**Branch:** `port-uncertainty-2`. **HEAD at verification:** `3959127f`.
**I owned Maven for this phase.** Two full `mvn -B verify` runs, no concurrent Maven.

---

## VERDICT: DEFECTIVE

The ten fixes do what they claim. D1 is genuinely dead, `SBooleanValue` is genuinely gated,
and the harness detects every arithmetic defect I planted. **But the instrument still reports
full agreement over a port that contains no code at all**, on a well-typed corpus, through a
door none of F1–F11 closes:

```
UIntegerValue.power(value): 169 rows, AGREE_THROWN=169
disagreements()            0
```

Those are the *same numbers as the original D1 defect* — `169 rows, AGREE_THROWN=169,
disagreements 0` — reached by a different route. Section 5 is the reproduction.

| Check | Result |
|---|---|
| 1. D1 reproduction | **FIXED** — `169 rows, HARNESS_ERROR=169`, disagreements 169 |
| 2. `supports(SBooleanValue.and)` | **FIXED** — `false`; sweep lands on 81 visible `UNSUPPORTED` |
| 3a. Planted defects detected | **PASS** — 4/4 operations reported as `DIFFER` |
| 3b. New false agreement | **FOUND — D2, critical.** See §5 |
| 4. Zero-row sweep | Refused **by the report writer**; `DifferentialSweep` itself still returns a clean-looking 0-row Result |
| 5. Determinism | **PASS** — two runs byte-identical, and identical to the committed goldens |
| 6. `mvn -B verify` | **PASS** — 39 surefire + 130 failsafe = 169, 0 failures |
| 7. Scope discipline | **PASS** — `*/src/main/*` diff empty; every `*/src/test/*` path is `A` |
| 8. Working tree | **CLEAN** |

---

## 0. How the direct runs were executed

All of §1–§5 were run with `java` directly, not through Maven, from a scratch directory outside
the repository. Classpath exactly as instructed:

```
CLASSPATH=/home/xoruser/msc-4/use-msc2026/use-core/target/classes
         :/home/xoruser/msc-4/use-msc2026/use-core/target/test-classes
         :/home/xoruser/msc-4/use-msc2026/use-core/src/test/resources/historical/use.jar
         :/home/xoruser/msc-4/use-msc2026/use-core/src/test/resources/historical/atenearesearchgroup.uncertainty.jar
         :<scratch>
```

Putting the two historical jars on the application classpath did **not** disturb the oracle:
`IsolatedJarClassLoader` is parent-last for `org.tzi.use.*` and `uDataTypes.*`, so the eager
`assertIsolated` sample in the `HistoricalOracle` constructor still passed and `open()` succeeded.

---

## 1. D1 reproduction — FIXED

`sweepBinary(UOp.binary("URealValue","add"), uIntegerBoundaries(), uIntegerBoundaries())`,
historical oracle versus `StubCandidate.faithful()`:

```
################ 1. D1 REPRODUCTION ################
--- sweepBinary(URealValue.add, uIntegerBoundaries, uIntegerBoundaries)
    tally         URealValue.add(value): 169 rows, HARNESS_ERROR=169
    rows          169
    disagreements 169
    | 0	URealValue.add(value)	UINTEGER(0,0.0) | UINTEGER(0,0.0)	HARNESS_ERROR:org.tzi.use.uncertainty.differential.HarnessMarshallingException	THROWN:java.lang.IllegalArgumentException	HARNESS_ERROR	the harness could not drive the reference; no comparison was made. URealValue.add(value) expects a receiver of org.tzi.use.uml.ocl.value.URealValue but the supplied UINTEGER(0,0.0) maps to org.tzi.use.uml.ocl.value.UIntegerValue
    | 1	URealValue.add(value)	UINTEGER(0,0.0) | UINTEGER(0,1.0)	HARNESS_ERROR:...	THROWN:java.lang.IllegalArgumentException	HARNESS_ERROR	...
    | 2	URealValue.add(value)	UINTEGER(0,0.0) | UINTEGER(1,0.0)	HARNESS_ERROR:...	THROWN:java.lang.IllegalArgumentException	HARNESS_ERROR	...
    AGREE_THROWN  0
    HARNESS_ERROR 169
```

Before the fix this sweep read `169 rows, AGREE_THROWN=169, disagreements 0`. It now reads
`AGREE_THROWN=0, HARNESS_ERROR=169, disagreements 169`. The porter's claim is **confirmed
independently**. Note the row also proves the two populations stay separated in the *columns*,
not only in the verdict: `HARNESS_ERROR:…` on the reference side, `THROWN:…` on the subject side.

## 2. `supports(UOp.binary("SBooleanValue","and"))` — FIXED

```
################ 2. SBooleanValue SUPPORT ################
    oracle.supports(UOp.binary("SBooleanValue","and")) = false
    oracle.supports(UOp.binary("URealValue","add"))     = true
--- sweepBinary(SBooleanValue.and, uBooleanBoundaries^2)
    tally         SBooleanValue.and(value): 81 rows, UNSUPPORTED=81
    rows          81
    disagreements 81
    | 0	SBooleanValue.and(value)	UBOOLEAN(true,0.0) | UBOOLEAN(true,0.0)	UNSUPPORTED	UNSUPPORTED	UNSUPPORTED	historical does not implement SBooleanValue.and(value); stub-faithful does not implement SBooleanValue.and(value)
```

`false`, as claimed, and the sweep lands on 81 visible `UNSUPPORTED` rows which
`DiffVerdict.isAgreement()` scores as **not** agreement, so they all appear in `disagreements()`.
A run cannot read as green while 39 `SBoolean` operations go unmeasured.

## 3. Planted defects — all four detected

Four operations, four different wrong implementations, against the real historical oracle over
the boundary corpora:

```
################ 3. PLANTED DEFECTS ################
--- DEFECT 1  URealValue.neg()          [forgets to negate]
    tally         URealValue.neg(): 22 rows, AGREE=1, DIFFER=21
    disagreements 21
    | 0	URealValue.neg()	UREAL(0.0,0.0)	UREAL(-0.0,0.0)	UREAL(0.0,0.0)	DIFFER	
--- DEFECT 2  URealValue.add(value)     [value off by one]
    tally         URealValue.add(value): 484 rows, AGREE=193, DIFFER=291
    disagreements 291
    | 0	URealValue.add(value)	UREAL(0.0,0.0) | UREAL(0.0,0.0)	UREAL(0.0,0.0)	UREAL(1.0,0.0)	DIFFER	
--- DEFECT 3  URealValue.minus(value)   [linear uncertainty]
    tally         URealValue.minus(value): 484 rows, AGREE=381, DIFFER=103
    disagreements 103
--- DEFECT 4  UIntegerValue.add(value)  [uncertainty dropped]
    tally         UIntegerValue.add(value): 169 rows, AGREE=25, DIFFER=144
    disagreements 144
```

Worth recording that DEFECT 1 row 0 is `UREAL(-0.0,0.0)` vs `UREAL(0.0,0.0)` → `DIFFER`. The
canonical form really is exact: it distinguishes `-0.0` from `0.0`, which `Double.equals`-free
rounding would have erased. The harness detects value defects, uncertainty-propagation defects
and sign defects on both `UReal` and `UInteger` receivers.

**The instrument can say no about arithmetic.** That part of the fix list holds.

## 4. Zero-row sweep — refused, but only at the writer

```
################ 4. ZERO-ROW SWEEP ################
    Result.rowCount()        0
    Result.disagreements()   0
    Result.summary()         URealValue.add(value): 0 rows
    -> DifferentialSweep itself raised NOTHING for the empty domain.
    DiffReportWriter.write REFUSED: refusing to write an empty differential report 'zero-row.tsv': 1 sweep result(s) contributing 0 rows in total. A report with no rows would read as agreement. The usual cause is an empty input domain, which makes the cartesian product empty.
```

The guard fires, and the message is accurate. But note precisely *where* it lives
(`DiffReportWriter.java:98`, on `rowTotal`, not on `results.isEmpty()` — that part of F3 is
correct). `DifferentialSweep.sweep()` / `run()` have no such guard, so a caller that asserts
`result.disagreements().isEmpty()` **without writing a report** gets a silent pass on an empty
domain. The porter's own `zeroRowSweepIsRefused` test says as much in a comment —
*"a zero-row sweep looks clean, which is the trap"* — so this is a conscious placement, not an
oversight. It is still an unguarded path. Logged as MINOR below.

---

## 5. THE DECISIVE CHECK — D2, a false agreement the fixes do not cover

### 5.1 The construction

The subject is a port that has not been written. Every method body is the single most likely
thing an unfinished Java port contains:

```java
public UValue invoke(UOp op, List<UValue> args) {
    throw new RuntimeException("TODO: port " + op.key());
}
```

This is **not** a contract violation. `Candidate.invoke` is documented as
*"@throws Throwable whatever the implementation under test throws"*, and this is the
implementation under test throwing. `HarnessMarshallingException` does not apply: nothing
failed to marshal.

The reference is the real historical oracle. `UIntegerValue.power(Value)` rejects its argument
with a **bare `java.lang.RuntimeException`**.

### 5.2 The reproduction

```
historical message on this operation:
   java.lang.RuntimeException: UInteger.power() : expected Real or Integer exponent value
ported message on this operation:
   java.lang.RuntimeException: TODO: port UIntegerValue.power(value)

SWEEP  UIntegerValue.power(value): 169 rows, AGREE_THROWN=169
rows                       169
disagreements()            0
count(AGREE_THROWN)        169
count(HARNESS_ERROR)       0
DiffVerdict.AGREE_THROWN.isAgreement() = true

index	operation	inputs	historical	ported	verdict	note
0	UIntegerValue.power(value)	UINTEGER(0,0.0) | UINTEGER(0,0.0)	THROWN:java.lang.RuntimeException	THROWN:java.lang.RuntimeException	AGREE_THROWN	
1	UIntegerValue.power(value)	UINTEGER(0,0.0) | UINTEGER(0,1.0)	THROWN:java.lang.RuntimeException	THROWN:java.lang.RuntimeException	AGREE_THROWN	
2	UIntegerValue.power(value)	UINTEGER(0,0.0) | UINTEGER(1,0.0)	THROWN:java.lang.RuntimeException	THROWN:java.lang.RuntimeException	AGREE_THROWN	
3	UIntegerValue.power(value)	UINTEGER(0,0.0) | UINTEGER(1,1.0)	THROWN:java.lang.RuntimeException	THROWN:java.lang.RuntimeException	AGREE_THROWN	

The assertion every stage of this port uses to decide pass/fail:
   result.disagreements().isEmpty() == true
A report writer would happily accept it too (rowTotal = 169 > 0).
```

### 5.3 Why this is the same defect as D1, not a lesser one

`DifferentialSweep.classify` (`DifferentialSweep.java:170–176`) decides throw-agreement on the
throwable **class name alone**:

```java
boolean same = ref.thrown.getClass().getName().equals(sub.thrown.getClass().getName());
String note = same ? "" : "reference message: " + ... + " / subject message: " + ...;
```

When the classes match, the messages are **discarded** — `note` is set to the empty string. The
harness holds the evidence that these two throws are unrelated (`"UInteger.power() : expected
Real or Integer exponent value"` versus `"TODO: port UIntegerValue.power(value)"`) and writes an
empty note instead. The report contains no trace of the difference. This is the same failure
mode F1 was written to kill — *agreement recorded where no comparison happened* — relocated from
the marshalling path to the throw-classification path.

It is worse than a theoretical concern because `java.lang.RuntimeException` is precisely what
the historical uncertainty code uses for its type errors. It is the least discriminating class in
Java, and the harness treats a match on it as a measurement.

### 5.4 Blast radius

Every operation the harness can reach, over its **natural well-typed corpus**, against the
unwritten port:

```
operations with >=1 AGREE_THROWN against an UNWRITTEN port:
   UIntegerValue.power(value)                169/ 169 rows AGREE_THROWN

throwable classes that scored as agreement:
   THROWN:java.lang.RuntimeException   x169

TOTAL rows                        13706
TOTAL AGREE (real values matched) 0
TOTAL AGREE_THROWN (FALSE)        169
TOTAL disagreements               13537
```

One operation is fully green against an empty port on its own well-typed corpus. Widen to the
cross-type sweeps the specification requires (the D1 sweep was itself cross-type — `UInteger`
inputs into `URealValue.add`), i.e. every reachable operation × every argument corpus:

```
ops that score AGREE_THROWN against a port with no code in it:
   URealValue.add(value)  (154/198 on one arg corpus)
   URealValue.add(value)  (330/352 on one arg corpus)
   URealValue.divideBy(value) ...
   URealValue.ge(value) / gt / le / lt / max / min / minus / mult ...
   UStringValue.ge(value) / gt / le / lt / uConcat / uEqualsIgnoreCase ...

rows 43136   AGREE_THROWN(false) 15081   disagreements 28055
```

**15 081 of 43 136 rows — 35 % — score as agreement against a subject containing no code**,
across 12 distinct operations on two receiver types.

### 5.5 What the test suite says about it

Nothing. `grep -n "AGREE_THROWN" DifferentialHarnessRegressionTest.java` returns only
assertions that `AGREE_THROWN` is **zero** on the D1 paths (lines 52 and 93). No test constructs
a genuine `AGREE_THROWN` and asks whether the two throws meant the same thing. The eleven new
regression methods all address the harness-error/throw separation; none addresses throw-semantics
collapse.

### 5.6 The other candidates I tried, which the fixes DO cover

* **Subject returns Java `null` on every row.** Covered — becomes `MIXED`, 484/484 disagreements,
  with the contract violation named in the note:
  ```
  0	URealValue.add(value)	UREAL(0.0,0.0) | UREAL(0.0,0.0)	UREAL(0.0,0.0)	THROWN:java.lang.NullPointerException	MIXED	one side threw: subject-always-null returned Java null from URealValue.add(value); a Candidate must return a UValue (use UValue.nullValue() for a genuine null result)
  ```
* **Harness-level refusal on one side only.** Covered — `HARNESS_ERROR` is checked before both
  throw branches in `classify`, so it never merges (§1 shows the mixed
  `HARNESS_ERROR` / `THROWN` row).
* **`UNSUPPORTED` masquerading as green.** Covered — `isAgreement()` is false, so all 81 rows
  in §2 appear in `disagreements()`.
* **Canonicalisation erasing a difference.** Not found. `UValue.canonical()` goes through
  `Double.toString`, distinguishes `-0.0` from `0.0` (§3 DEFECT 1), and the `OPAQUE` branch is
  rebuilt from declared fields with a cycle check, a depth limit, and a hard refusal for anything
  it cannot render exactly — it never falls back to a foreign `toString()`.
* **`void` collapsing into `NULL`.** Covered by `UValue.Kind.VOID`.
* **`Error` becoming comparable data.** Covered — `apply` re-throws `Error`.

---

## 6. `mvn -q clean && mvn -B verify -Djava.awt.headless=true`

Run 1 (`mvn -q clean` exit 0, then verify):

```
[INFO] Running org.tzi.use.architecture.MavenCyclicDependenciesCoreTest
[INFO] Tests run: 11, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 4.356 s -- in org.tzi.use.architecture.MavenCyclicDependenciesCoreTest
[INFO] Running Uncertainty differential smoke
[INFO] Tests run: 6, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.151 s -- in Uncertainty differential smoke
[INFO] Running HistoricalOracle class-loader isolation
[INFO] Tests run: 9, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.025 s -- in HistoricalOracle class-loader isolation
[INFO] Running Differential harness regressions
[INFO] Tests run: 11, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.071 s -- in Differential harness regressions
[INFO] Running org.tzi.use.uml.mm.ModelAPITest
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.090 s -- in org.tzi.use.uml.mm.ModelAPITest
[INFO] Results:
[INFO] Tests run: 38, Failures: 0, Errors: 0, Skipped: 0          <- use-core surefire
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.106 s - in org.tzi.use.OCLExpressionIT
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0           <- use-core failsafe
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 2.914 s -- in org.tzi.use.architecture.MavenLayeredArchitectureTest
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0           <- use-gui surefire
[INFO] Tests run: 129, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 6.44 s - in org.tzi.use.main.shell.ShellIT
[INFO] Tests run: 129, Failures: 0, Errors: 0, Skipped: 0         <- use-gui failsafe
```

```
[INFO] Reactor Summary for use 7.5.0:
[INFO] use ................................................ SUCCESS [  0.002 s]
[INFO] use-core ........................................... SUCCESS [ 20.700 s]
[INFO] use-gui ............................................ SUCCESS [ 25.517 s]
[INFO] use-assembly ....................................... SUCCESS [  5.828 s]
[INFO] BUILD SUCCESS
[INFO] Total time:  52.151 s
```

Run 2 (no clean, same command) gave identical counts:
`38 / 1 / 1 / 129`, `BUILD SUCCESS`.

### Accounting for every delta

| | surefire | failsafe | total |
|---|---|---|---|
| Pre-fix (given) | 28 | 130 | 158 |
| Measured now | **39** | **130** | **169** |
| Delta | **+11** | 0 | +11 |

surefire = 38 (use-core) + 1 (use-gui `MavenLayeredArchitectureTest`) = 39.
use-core 38 = 11 `MavenCyclicDependenciesCoreTest` + 6 smoke + 9 isolation + **11 regressions** + 1 `ModelAPITest`.
Pre-fix use-core was 11 + 6 + 9 + 1 = 27, plus 1 use-gui = 28. ✔

failsafe = 1 (`OCLExpressionIT`) + 129 (`ShellIT`) = 130, unchanged. ✔

**+11 is exactly `DifferentialHarnessRegressionTest`.** No pre-existing test broke, none was
removed, none was skipped (`Skipped: 0` throughout). The porter's count claim is confirmed.

The 11 new methods do assert (unlike the 12 assertion-free ArchUnit baseline methods): each one
carries at least one JUnit assertion. They are, however, all aimed at D1-class defects — see
§5.5.

## 7. Determinism

```
=== diff run1 vs run2 ===
add.tsv: IDENTICAL (diff exit 0)
minus-faulty.tsv: IDENTICAL (diff exit 0)

=== sha256: run1 / run2 / live target / COMMITTED GOLDEN ===
549d33e61eb219a47dabff673de5da0bda42caeb348de0df6ef0d583823e3050  <scratch>/run1/s1-smoke-ureal-add.tsv
21b3923027f3c12a31d48d5955ed7a1e0461123fe9c5dac2250993c686b4b220  <scratch>/run1/s1-smoke-ureal-minus-faulty.tsv
549d33e61eb219a47dabff673de5da0bda42caeb348de0df6ef0d583823e3050  <scratch>/run2/s1-smoke-ureal-add.tsv
21b3923027f3c12a31d48d5955ed7a1e0461123fe9c5dac2250993c686b4b220  <scratch>/run2/s1-smoke-ureal-minus-faulty.tsv
549d33e61eb219a47dabff673de5da0bda42caeb348de0df6ef0d583823e3050  use-core/target/differential/s1-smoke-ureal-add.tsv
21b3923027f3c12a31d48d5955ed7a1e0461123fe9c5dac2250993c686b4b220  use-core/target/differential/s1-smoke-ureal-minus-faulty.tsv
549d33e61eb219a47dabff673de5da0bda42caeb348de0df6ef0d583823e3050  docs/port2/differential/s1-smoke-ureal-add.tsv
21b3923027f3c12a31d48d5955ed7a1e0461123fe9c5dac2250993c686b4b220  docs/port2/differential/s1-smoke-ureal-minus-faulty.tsv
```

Two runs byte-identical, and identical to the committed goldens. Reports now go to
`use-core/target/differential/` and no longer overwrite tracked files: `git status --porcelain`
after both runs is empty. F3 and F10 confirmed. This run reproduces the S1 evidence rather than
replacing it.

**One correction to the porter's wording.** `assertMatchesGolden` is documented (and was reported
to me) as comparing *"byte for byte"*. It does not — it compares `Files.readAllLines`
(`DiffReportWriter.java:206, 242`). Measured:

```
byte lengths   lf=44  crlf=47  no-trailing-newline=43
bytes equal?   lf vs crlf    = false
bytes equal?   lf vs notrail = false
   readLines(lf).equals(readLines(crlf))    = true
   readLines(lf).equals(readLines(notrail)) = true
```

Three byte-distinct files compare equal. The sha256 identity above is my own measurement, not
something the test enforces. Line-level comparison is adequate for the verdict data, but the
claim is wrong and should be corrected or the comparison strengthened.

## 8. Scope discipline

```
$ git diff --name-status 30d480db..HEAD -- '*/src/main/*'
<<<end>>>                                                        # EMPTY
```

```
$ git diff --name-status 30d480db..HEAD -- '*/src/test/*'
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
A	use-core/src/test/java/org/tzi/use/uncertainty/differential/StubCandidate.java
A	use-core/src/test/java/org/tzi/use/uncertainty/differential/UOp.java
A	use-core/src/test/java/org/tzi/use/uncertainty/differential/UValue.java
A	use-core/src/test/java/org/tzi/use/uncertainty/differential/UncertaintyDifferentialSmokeTest.java
A	use-core/src/test/resources/historical/atenearesearchgroup.uncertainty.jar
A	use-core/src/test/resources/historical/use.jar
```

Every entry is `A`. All seventeen are under `.../uncertainty/differential/` or its
`resources/historical/` companion. **No upstream test was touched.**

```
$ git diff --name-status 30d480db..HEAD -- '*module-info.java'
<<<end>>>                                                        # EMPTY
```

The only non-`docs`, non-differential path in the whole range is `M .gitignore`, which belongs
to commit `d0bf18aa` *"chore(port2): ignore ArchUnit test-run artifacts"* — **not** to either of
the porter's commits — and adds three ignore lines for ArchUnit run artefacts. Confirmed
test-scoped:

```
cf9d2f45 S3 fix: stop the differential harness scoring its own failures as agreement
 9 files changed, 961 insertions(+), 44 deletions(-)   [all under .../uncertainty/differential/]
3959127f docs(port2): correct assertIsolated's overstated Javadoc (F9)
 1 file changed, 31 insertions(+), 2 deletions(-)      [HistoricalOracle.java]
```

Neither commit contains `docs/port2/audit-02-specification.md`, the file the porter flagged as
landing concurrently. Confirmed.

## 9. Working tree

```
$ git status --porcelain
<<<empty=clean>>>
```

Clean after both Maven runs and all direct experiments. All scratch code
(`refute/Probe.java`, `Exp.java`, `Blast.java`, `Blast2.java`, `D2.java`, `Golden.java`) lived
outside the repository and has been removed. No `zero-row.tsv` or `must-not-be-written.tsv`
exists anywhere — both writes were refused, as intended.

---

## 10. Findings

### D2 — CRITICAL — `AGREE_THROWN` collapses semantically unrelated throws
`use-core/src/test/java/org/tzi/use/uncertainty/differential/DifferentialSweep.java:170–176`

Throw-agreement is decided on `Throwable.getClass().getName()` alone, and when the classes match
the two messages are discarded into an empty `note`. The historical uncertainty code signals its
type errors with a bare `java.lang.RuntimeException`, so a port that throws
`new RuntimeException(...)` for *any* reason — including "not implemented" — is scored as
reproducing historical behaviour. Measured: `UIntegerValue.power(value)` over
`uIntegerBoundaries²` gives `169 rows, AGREE_THROWN=169, disagreements 0` against a subject with
no implementation; 15 081 / 43 136 rows (35 %) across 12 operations once cross-type argument
corpora are included. Not covered by any of F1–F11 and not covered by any of the 11 new
regression tests.

*Minimum fix:* `AGREE_THROWN` must require more than a class-name match. Either compare the
throwable message as well (and record both messages in the `note` regardless of verdict), or
introduce a `DIFFER_THROWN_MESSAGE` verdict that is not an agreement, or refuse to treat
`java.lang.RuntimeException`/`java.lang.Exception` — classes carrying no semantic content — as
agreeable at all. The `note` should never be empty on a throw row: the harness already has the
messages and is throwing them away.

### F-ZERO — MINOR — the zero-row guard lives only in the report writer
`DifferentialSweep.java:53, 87` (no guard) vs `DiffReportWriter.java:98` (guard)

`DifferentialSweep.sweep()` returns a 0-row `Result` whose `disagreements()` is empty for an
empty input domain. A caller asserting on `disagreements()` without writing a report is
unprotected. Consciously placed — the porter's own test comments on the trap — but still a live
path.

### F10-DOC — MINOR — `assertMatchesGolden` is not byte-for-byte
`DiffReportWriter.java:187` (Javadoc claim), `:206, :242` (implementation)

Compares `Files.readAllLines`. CRLF-vs-LF and trailing-newline differences compare equal
(measured, §7). Correct the claim or compare bytes.

### F5 — confirmed unreachable, as the porter self-reported
`HistoricalOracle.java:441–452`, constructor eager list at `:163–166`

All eight `MARSHALLABLE_RECEIVERS` (`URealValue`, `UIntegerValue`, `UBooleanValue`,
`UStringValue`, `RealValue`, `IntegerValue`, `BooleanValue`, `StringValue`) appear in the twelve
classes loaded eagerly in the constructor, so `load()` inside `supports()` cannot throw
`HistoricalOracleUnavailableException`. The narrowed catch is preventive only. The porter
declared this; I confirm it by inspection. **This is the right way to have reported it** — the
narrowing is still correct and should stay.

### Porter self-reports I checked and found accurate
The F4 `OPAQUE`-branch unreachability, the F5 weakness, the un-executed golden refresh path, the
`reportDir()` behaviour change, and the concurrency note about `docs/port2/audit-02-specification.md`
are all as described. I found no overstatement in the fix report other than the "byte for byte"
wording in §7.

---

## 11. Bottom line

The fix list is honest work and it killed the defect it was aimed at. D1 is dead; I reproduced
its death independently. The `SBoolean` gate is real. The instrument detects planted arithmetic
defects on four operations across two receiver types. Determinism and scope discipline hold, and
the acceptance suite is 169/169 green with every delta accounted for.

But the audit question was not "did F1–F11 get applied", it was **"can the harness still report
agreement over nothing"**. It can. A port containing nothing but
`throw new RuntimeException("TODO")` scores `169 rows, AGREE_THROWN=169, disagreements 0` on a
well-typed corpus — numerically indistinguishable from the D1 report that triggered this whole
fix round. The harness was taught to separate *its own* failures from the code under test's; it
was not taught that two throws of the most generic class in Java are not a measurement.

**Verdict: DEFECTIVE.** Fix D2 before anything in S4 relies on a green sweep.
