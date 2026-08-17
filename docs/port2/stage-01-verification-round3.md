# S1 empirical verification, round 3 — the third door

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


**Verdict: DEFECTIVE.**

Two rounds of fixes closed two doors. A third is open. It is not a variant of the first two: it does
not go through a throw, a marshalling failure or a `null`. It goes through `AGREE` itself.

**A `Candidate` whose every method body is empty scores 444 agreement rows.** The standing invariant
does not catch it, because the invariant tests one instance — a subject that *throws* — of the
property it claims to test.

Everything below was measured on `port-uncertainty-2` at `e8b73e48`, on this machine, today. Every
number is followed by the command that produced it and the unedited output. Where I could not
establish something I say UNVERIFIED rather than guess.

---

## 0. How this was measured

Probes were compiled and run with `java` directly, from a scratch directory outside the repository,
against the built classes and the vendored jars. Nothing under `use-core/src` was modified.

```
SCRATCH=/tmp/claude-1000/-home-xoruser-msc-4/5a883e17-9055-4019-8f36-a743005556fa/scratchpad
R=/home/xoruser/msc-4/use-msc2026
CP="$SCRATCH/probe\
:$R/use-core/target/classes\
:$R/use-core/target/test-classes\
:$R/use-core/src/test/resources/historical/use.jar\
:$R/use-core/src/test/resources/historical/atenearesearchgroup.uncertainty.jar"

javac -cp "$CP" -d "$SCRATCH/probe" Probe1.java Probe2.java Probe3.java Probe4.java Probe5.java
cd "$SCRATCH/run2" && java -cp "$CP" Probe1     # adversarial subjects
cd "$SCRATCH/run2" && java -cp "$CP" Probe2     # empty / silent coverage
cd "$SCRATCH/run2" && java -cp "$CP" Probe3     # allowlist abuse
cd "$SCRATCH/run2" && java -cp "$CP" Probe4     # planted defects
cd "$SCRATCH/run2" && java -cp "$CP" Probe5     # wrong-exception-class visibility
```

`Probe1` re-derives the operation inventory and the corpora with the same code as
`UnwrittenPortInvariantTest` (`reachableOperations`, `corpora`, `domains`) and reproduces the
committed figures exactly for the committed subject, which is how I know the probe is measuring the
same thing the test measures:

```
=== PROBE 1: adversarial subjects vs the unwritten-port invariant ===
operations 285, corpora [uReal, uInteger, uBoolean, uString, zeroDivisors, indexBoundaries], receivers 77

### SUBJECT: A. every body throws (the porter's subject)  (unwritten-port)
    rows            471471
    agreement rows  0
    verdict tally   {BOTH_THREW=30580, HARNESS_ERROR=388695, MIXED=52196}
    INVARIANT (agreement==0): HOLDS
```

471471 rows, `{BOTH_THREW=30580, HARNESS_ERROR=388695, MIXED=52196}`, agreement 0 — identical to the
porter's AFTER figures and to the `mvn -B verify` log below. The instrument is the same instrument.

---

## 1. The unwritten-port invariant, and six attempts to defeat it

`Probe1` swaps the subject and changes nothing else. Full results:

| # | subject | rows | agreement | invariant |
|---|---------|------|-----------|-----------|
| A | every body `throw new RuntimeException("TODO: port …")` | 471471 | **0** | holds |
| B | every body `return null` (Java null) | 471471 | **0** | holds |
| C | **every body EMPTY → `UValue.voidValue()`** | 471471 | **444** | **VIOLATED** |
| D | every body `return UValue.nullValue()` | 471471 | **0** | holds |
| E | every body returns constant `UREAL(0.0,0.0)` | 471471 | 644 | (see §1.2) |
| F | every body returns constant `UBOOLEAN(true,1.0)` | 471471 | 6931 | (see §1.2) |
| G | every body returns the argument unchanged | 471471 | 1907 | (see §1.2) |
| H | every body returns a `UValue` of the WRONG `Kind` | 471471 | 983 | (see §1.2) |
| I | every body `throw new AssertionError(…)` | 0 | 0 | (see §1.3) |

### 1.1 D-10 — CRITICAL: `VOID` vs `VOID` is scored `AGREE`

```
### SUBJECT: C. every body EMPTY -> UValue.voidValue()  (do-nothing-port)
    rows            471471
    agreement rows  444
    verdict tally   {AGREE=444, DIFFER=51752, HARNESS_ERROR=388695, MIXED=30580}
    --- sample agreement rows ---
    index	operation	inputs	historical	ported	verdict	note
    68	IntegerValue.setTypeToRuntimeType()	INTEGER(0)	VOID	VOID	AGREE	
    70	IntegerValue.setTypeToRuntimeType()	INTEGER(-2147483648)	VOID	VOID	AGREE	
    71	IntegerValue.setTypeToRuntimeType()	INTEGER(-1)	VOID	VOID	AGREE	
    72	IntegerValue.setTypeToRuntimeType()	INTEGER(1)	VOID	VOID	AGREE	
    73	IntegerValue.setTypeToRuntimeType()	INTEGER(2)	VOID	VOID	AGREE	
    74	IntegerValue.setTypeToRuntimeType()	INTEGER(3)	VOID	VOID	AGREE	
    INVARIANT (agreement==0): *** VIOLATED ***
```

All 444 rows are void operations, and they are *every* row of every void operation the harness can
reach with a receiver it has in its corpora:

```
   144  URealValue.setTypeToRuntimeType()
   102  UStringValue.setTypeToRuntimeType()
    90  UIntegerValue.setTypeToRuntimeType()
    54  UBooleanValue.setTypeToRuntimeType()
    48  IntegerValue.setTypeToRuntimeType()
     6  RealValue.setTypeToRuntimeType()
  ---- total 444
```

(`BooleanValue`/`StringValue.setTypeToRuntimeType()` are also reachable — `Probe1` enumerated 8 void
operations — but the shipped corpora contain no `BOOLEAN` or `STRING` receiver, so those rows are
`HARNESS_ERROR`. Widening the corpora *widens this defect*.)

**Why this is a defect and not a declared limit.**

`DiffVerdict`'s own class comment, `DiffVerdict.java:7-10`, states the rule the whole enum exists to
enforce:

> **A differential oracle may report agreement only where it observed two comparable values.**
> Everything else — a throw on both sides, a harness failure, an operation one side does not have —
> is the *absence* of a measurement, and the absence of a measurement is not a measurement that the
> two sides happen to share.

`UValue.Kind.VOID`, `UValue.java:59-67`, defines itself as exactly that absence:

> The operation is declared `void`, so **there is no result to compare**.

So the harness takes two objects each of which means "there is no result to compare", compares them,
finds them equal, and emits `AGREE` — `isAgreement() == true`. That is verbatim the rule the enum
was rewritten to forbid. `UNSUPPORTED` and `HARNESS_ERROR` were both carved out as distinct
non-agreements for precisely this reason; `VOID` was not.

**Worse: the fix that was supposed to close this names the defect and does not close it.** The same
`UValue.Kind.VOID` Javadoc, `UValue.java:62-66`:

> Distinct from `NULL` on purpose. `Method.invoke` returns `null` for a `void` method, so before this
> constant existed every `void` operation unwrapped to `NULL` — and **an empty-bodied ported mutator
> therefore agreed with the historical one on every row, forever.**

And `DifferentialHarnessRegressionTest.java:422-423`:

```java
@DisplayName("a void historical operation unwraps to VOID, never to NULL")
void voidIsDistinctFromNull() throws Throwable {
    // Value.setTypeToRuntimeType() is public and void, and is inherited by URealValue: the
    // exact shape of the "empty-bodied mutator agrees forever" defect.
```

The test asserts `VOID != NULL`. It never asserts that `VOID` vs `VOID` is not an agreement. The
stated consequence — "an empty-bodied ported mutator agreed with the historical one on every row,
forever" — **is still true**, measured: 22 of 22 rows for `URealValue.setTypeToRuntimeType()`, 444 of
444 across the reachable void operations. The `NULL`/`VOID` separation fixed the conflation of void
results with genuine `null` results. It did not fix the thing its own comment says it fixed.

**And the invariant does not catch it.** `UnwrittenPortInvariantTest`'s headline claim,
`UnwrittenPortInvariantTest.java:23-25`:

> **A `Candidate` that implements nothing must produce zero agreement rows, over every operation the
> harness can reach and every input corpus it ships.**

That statement is false as written. Subject C implements nothing — every body is empty — and produces
444 agreement rows over the operations the harness can reach and the corpora it ships. The test holds
only for the one subject it instantiates, `UnwrittenPortInvariantTest.UnwrittenPort`, whose bodies
throw. Its own Javadoc, lines 28-34, diagnoses this failure mode:

> Pinning each route with its own regression test is chasing instances; the instances kept coming
> back through a route nobody had pinned yet. The invariant closes the whole family…

It does not close the family. It pins one more instance — a very large and valuable one, but an
instance. "Implements nothing" has at least two encodings and the test covers one.

### 1.2 Subjects E, F, G, H are NOT defects — reported for completeness

E/F/G/H produce agreement rows, and I want to be explicit that these are *correct*. A constant
subject returning `UBOOLEAN(true,1.0)` genuinely agrees on rows where the historical code genuinely
returned `UBOOLEAN(true,1.0)`:

```
### SUBJECT: F. every body returns a fixed constant UBOOLEAN(true,1.0)  (const-ubool-true)
    rows            471471
    agreement rows  6931
    verdict tally   {AGREE=6931, DIFFER=45265, HARNESS_ERROR=388695, MIXED=30580}
    index	operation	inputs	historical	ported	verdict	note
    453	UBooleanValue.and(value)	UBOOLEAN(true,1.0) | UBOOLEAN(true,1.0)	UBOOLEAN(true,1.0)	UBOOLEAN(true,1.0)	AGREE	
```

Two values were observed and they were equal. That is a measurement, and it is the right verdict for
that row. The sweep reports **45265 `DIFFER` rows** for the same subject; any caller looking at
disagreements sees the subject is wrong. Same for E (644 agree / 51552 differ), G (1907 / 50289) and
H (983 / 51213).

Specifically on the wrong-`Kind` subject H: `UValue.canonical()` prefixes the kind tag
(`UValue.java:230-266`), so `UREAL(2.0,0.0)` and `UINTEGER(2,0.0)` are never equal strings, and H's
983 agreements are rows where the "wrong" kind happened to be the right one (e.g.
`UBooleanValue.isBag()` really does return a plain `BOOLEAN`). **A wrong-`Kind` return is reported as
divergence.** No defect.

### 1.3 Subject I — `Error` aborts the sweep. Loud, so acceptable.

```
### SUBJECT: I. every body throws java.lang.Error (AssertionError)  (throws-Error)
    rows            0
    agreement rows  0
    verdict tally   {}
    ESCAPED         java.lang.AssertionError: TODO: port BooleanValue.compareTo(value)
    -> the sweep ABORTED; rows above are only those completed before the throw
```

`DifferentialSweep.apply` re-throws `Error` (`DifferentialSweep.java:174-175`) by design. The sweep
produces no rows and the `Error` escapes to the caller, so a JUnit stage fails. It cannot produce a
false agreement. **Not a defect**, but note the failure mode is "zero rows plus an exception", and
per D-12 below, zero rows on its own is indistinguishable from a clean pass.

---

## 2. Empty / silent coverage — D-11 and D-12

`Probe2` judges each scenario exactly as a stage would: `result.disagreements().isEmpty()`.

```
--- (1) EMPTY RECEIVER DOMAIN: sweepBinary(add, [], uRealBoundaries())
    rowCount()               0
    agreementCount()         0
    disagreements().size()   0
    tally                    URealValue.add(value): 0 rows
    a stage asserting disagreements().isEmpty() => *** PASS ***
    DiffReportWriter.writeAll => REFUSED: refusing to write an empty differential report 'probe-empty.tsv': 1 sweep result(s) contributing 0 rows in total. A report with no rows would read as agreement. The usual cause is an empty input domain, which makes the cartesian product empty.

--- (2) EMPTY ARGUMENT DOMAIN: sweepBinary(add, uRealBoundaries(), [])
    rowCount()               0
    agreementCount()         0
    disagreements().size()   0
    tally                    URealValue.add(value): 0 rows
    a stage asserting disagreements().isEmpty() => *** PASS ***

--- (3) ALL UNSUPPORTED: SBooleanValue.and over uBoolean boundaries
    rowCount()               81
    disagreements().size()   81
    tally                    SBooleanValue.and(value): 81 rows, UNSUPPORTED=81
    a stage asserting disagreements().isEmpty() => FAIL

--- (4) ALL HARNESS_ERROR: URealValue.add driven with UINTEGER receivers
    rowCount()               169
    disagreements().size()   169
    tally                    URealValue.add(value): 169 rows, HARNESS_ERROR=169
    a stage asserting disagreements().isEmpty() => FAIL

--- (5) ALL BOTH_THREW: UIntegerValue.power(UString) vs unwritten port
    rowCount()               208
    disagreements().size()   208
    tally                    UIntegerValue.power(value): 208 rows, BOTH_THREW=195, HARNESS_ERROR=13
    a stage asserting disagreements().isEmpty() => FAIL
```

**All-`UNSUPPORTED`, all-`HARNESS_ERROR` and all-`BOTH_THREW` are correctly caught.** Those three are
genuinely fixed and I confirm them. Two holes remain.

### D-12 — MAJOR: a zero-row `Result` still reads as clean at the `Result` level

Scenarios (1) and (2): `disagreements().isEmpty() == true` on a sweep that never ran. The guard added
in round 2 is on `DiffReportWriter.writeAll` (`DiffReportWriter.java:96-105`), not on
`DifferentialSweep.Result`. A stage that sweeps and asserts without writing a report is unprotected.

This is *known* and left open. `DifferentialHarnessRegressionTest.zeroRowSweepIsRefused`,
lines 106-121, asserts the trap and then guards only the writer:

```java
assertEquals(0, empty.rowCount());
assertEquals(List.of(), empty.disagreements(),
        "a zero-row sweep looks clean, which is the trap");
```

Nothing on `Result` forces a minimum of compared values. There is no `assertMeasured()`, no
`requireRows(n)`, no minimum-agreement accessor. `agreementCount()` returning 0 is equally consistent
with "nothing ran" and with "everything diverged". A stage cannot distinguish them from the `Result`
API alone; it must remember to write a report.

### D-11 — MAJOR: the writer's guard counts rows, not measurements

D-10 plus the writer's row-count guard produces a report that is maximally green and contains zero
comparisons. `Probe2` scenario (6) sweeps only void operations against the do-nothing port:

```
--- (6.URealValue) URealValue.setTypeToRuntimeType()
    rowCount()               22
    agreementCount()         22
    disagreements().size()   0
    tally                    URealValue.setTypeToRuntimeType(): 22 rows, AGREE=22
    a stage asserting disagreements().isEmpty() => *** PASS ***
--- (6.IntegerValue) IntegerValue.setTypeToRuntimeType()
    rowCount()               8
    agreementCount()         8
    disagreements().size()   0
    tally                    IntegerValue.setTypeToRuntimeType(): 8 rows, AGREE=8
    a stage asserting disagreements().isEmpty() => *** PASS ***

    TOTAL rows 75, agreement 72, disagreements 3
    DiffReportWriter.writeAll => ACCEPTED, wrote .../target/differential/probe-void-green.tsv
    --- the report header the writer produced ---
    # harness	differential-sweep/1
    # seed	20260817
    # reference	historical
    # subject	do-nothing-port
    # operations	URealValue.setTypeToRuntimeType(),UIntegerValue.setTypeToRuntimeType(),...
    # rows	75
    # rows.agreement	72
    # rows.disagreement	3
    # verdict.AGREE	72
    # verdict.HARNESS_ERROR	3
    --- first 3 data rows ---
    index	operation	inputs	historical	ported	verdict	note
    0	URealValue.setTypeToRuntimeType()	UREAL(0.0,0.0)	VOID	VOID	AGREE	
    1	URealValue.setTypeToRuntimeType()	UREAL(0.0,1.0)	VOID	VOID	AGREE	
    2	URealValue.setTypeToRuntimeType()	UREAL(-0.0,0.0)	VOID	VOID	AGREE	
```

The round-2 fix added `# rows.agreement` / `# rows.disagreement` headers specifically so that "a
reader never has to infer greenness from verdict names again". Here the headers state
`# rows.agreement 72` and the reader is misled *by the explicit number*, not by an inference. The
guard rejects a report with no rows; it accepts a report with 75 rows and no measurements. The
property it needs to enforce is "this report contains comparisons", and row count is not that
property — the same category of error as the pre-round-2 guard that tested `results.isEmpty()`
instead of the row total.

### What is *not* a hole

- **Corpora cannot silently empty themselves.** `InputGenerator.corpus(...)` (lines 110-120) rejects
  a negative `randomCount` and every `*Boundaries()` list is a non-empty literal. There is no
  reachable path today by which a shipped corpus yields no tuples. The empty-domain hole (D-12)
  requires a caller to pass `List.of()` explicitly.

---

## 3. Adjudication allowlist — NO DEFECT FOUND

I tried to write an over-broad entry. I could not. `Probe3`:

```
--- (A) attempts to express a blanket rule
    accept(null, ...) => rejected: IllegalArgumentException: operationKey must not be blank: ...
    accept("", ...) blank op => rejected: IllegalArgumentException: operationKey must not be blank: ...
    blank rationale => rejected: IllegalArgumentException: rationale must not be blank: ...
    null reference message => rejected: NullPointerException: referenceMessage (use "" for none)
    same pair, two rationales => rejected: IllegalArgumentException: the same throw-pair is signed off twice with different rationales: ...

--- (B) wildcard-shaped entries against a real sweep
    op=*                          refMsg=''  => 208 rows, BOTH_THREW=195, HARNESS_ERROR=13  | ACCEPTED_THROW=0
    op=UIntegerValue.*            refMsg=''  => 208 rows, BOTH_THREW=195, HARNESS_ERROR=13  | ACCEPTED_THROW=0
    op=UIntegerValue.power(value) refMsg=''  => 208 rows, BOTH_THREW=195, HARNESS_ERROR=13  | ACCEPTED_THROW=0
    op=UIntegerValue.power(value) refMsg='*' => 208 rows, BOTH_THREW=195, HARNESS_ERROR=13  | ACCEPTED_THROW=0
```

Matching is exact string equality on the concatenation of five discriminators
(`AcceptedThrowPairs.java:90-94`). `*` is a literal. There is no class-only match, no prefix match,
no null match. Matching on class alone is impossible because the messages are part of the key and
cannot be omitted — `""` is not a wildcard, it matches only a genuinely absent message.

**Blast radius, measured.** How much friction is the friction actually worth?

```
--- (C) how many hand-written entries would it take to re-green the ENTIRE unwritten port?
    BOTH_THREW rows                                          30580
    distinct (op, refClass, refMsg, subClass, subMsg) keys      291
    operations involved                                          39
```

291 entries, each with its own written rationale that lands in the note column of every row it
adjudicates. 30580 rows of false green are therefore reachable — but only via 291 separately
authored, separately reviewable sign-offs. That is a real, working safeguard. I record the number so
a future reviewer knows the order of magnitude they are being asked to approve, and I repeat the
porter's own warning: **no sign-off document exists anywhere in the tree today, and
`ACCEPTED_THROW.isAgreement()` is `true`.** Anyone auditing a future sweep must read
`# verdict.ACCEPTED_THROW` in the report header and demand the rationale.

---

## 4. Planted defects — 5 planted, 5 reported

`Probe4`'s subject is a **second `HistoricalOracle` instance** — a byte-perfect port — with five
operations corrupted. Run one is the uncorrupted mirror (the control), run two the corrupted one.
Both sweep all 285 operations × 6 corpora.

```
=== PROBE 4: planted defects ===
operations 285, planted defects [URealValue.add(value), UIntegerValue.lt(value),
                                 URealValue.toUInteger(), UIntegerValue.divideBy(value),
                                 UStringValue.at(int)]

--- CONTROL: a perfect mirror (oracle vs oracle) -----------------
    rows 471471  {AGREE=52196, BOTH_THREW=30580, HARNESS_ERROR=388695}
--- PLANTED: the same mirror with 5 corrupted operations ---------
    rows 471471  {AGREE=51486, DIFFER=710, BOTH_THREW=29680, MIXED=900, HARNESS_ERROR=388695}

--- per-operation, the five planted operations -------------------
operation                        CONTROL (perfect mirror)                          PLANTED
URealValue.add(value)            {AGREE=1296, BOTH_THREW=624, HARNESS_ERROR=4471}  {AGREE=1054, DIFFER=242, BOTH_THREW=624, HARNESS_ERROR=4471}
UIntegerValue.lt(value)          {AGREE=390, BOTH_THREW=810, HARNESS_ERROR=5191}   {DIFFER=390, BOTH_THREW=810, HARNESS_ERROR=5191}
URealValue.toUInteger()          {AGREE=144, HARNESS_ERROR=318}                    {AGREE=66, DIFFER=78, HARNESS_ERROR=318}
UIntegerValue.divideBy(value)    {AGREE=300, BOTH_THREW=900, HARNESS_ERROR=5191}   {AGREE=300, MIXED=900, HARNESS_ERROR=5191}
UStringValue.at(int)             {AGREE=196, BOTH_THREW=722, HARNESS_ERROR=5473}   {AGREE=196, BOTH_THREW=722, HARNESS_ERROR=5473}
```

| # | category | operation | defect | result |
|---|----------|-----------|--------|--------|
| 1 | arithmetic | `URealValue.add(value)` | uncertainty combined linearly, not in quadrature | `AGREE 1296 → 1054`, **`DIFFER 242`** |
| 2 | comparison | `UIntegerValue.lt(value)` | sense of the comparison inverted | `AGREE 390 → 0`, **`DIFFER 390`** |
| 3 | conversion | `URealValue.toUInteger()` | uncertainty dropped during conversion | `AGREE 144 → 66`, **`DIFFER 78`** |
| 4 | error path | `UIntegerValue.divideBy(value)` | swallows divide-by-zero, returns `+Inf` | `BOTH_THREW 900 → ` **`MIXED 900`** |
| 5 | error path | `UStringValue.at(int)` | right failure, **wrong exception class** | tally **UNCHANGED** — see D-13 |

Defect 1 is only caught on 242 of 1296 rows because linear sum and quadrature coincide whenever one
uncertainty is zero; that is a property of the planted defect, not a limitation of the harness.

**Two control results worth recording:**

1. **The perfect mirror produces `DIFFER=0` over 471471 rows.** The harness raises no false
   disagreement. `UValue.canonical()`'s exact-`Double.toString` comparison is not producing spurious
   noise.
2. **The perfect mirror produces 419275 non-agreement rows — 88.9%** (`BOTH_THREW=30580`,
   `HARNESS_ERROR=388695`). **`disagreements().isEmpty()` is unreachable even for a byte-identical
   port.** So the idiom D-12 is dangerous under is not, and cannot be, the acceptance idiom for a
   real sweep. The usable gates are the byte-compared golden and a per-operation agreement floor.
   Neither is currently expressed as an API on `Result`.

### D-13 — MINOR: a wrong-exception-class defect is invisible in every aggregate

Defect 5 changed `IndexOutOfBoundsException` to `IllegalStateException` on 89 rows. `Probe5`:

```
CONTROL (perfect mirror) tally : UStringValue.at(int): 128 rows, AGREE=31, BOTH_THREW=89, HARNESS_ERROR=8
PLANTED (wrong throw class)    : UStringValue.at(int): 128 rows, AGREE=31, BOTH_THREW=89, HARNESS_ERROR=8

identical verdict tally?          true
identical rowCount?               true
identical agreementCount?         true
identical disagreements().size()? true

rows whose TSV text differs:      89 of 128

first differing row:
  CONTROL 0	UStringValue.at(int)	USTRING("",0.0) | INTEGER(-2147483648)	THROWN:java.lang.IndexOutOfBoundsException	THROWN:java.lang.IndexOutOfBoundsException	BOTH_THREW	reference threw java.lang.IndexOutOfBoundsException: idx = -2147483648 / subject threw java.lang.IndexOutOfBoundsException: idx = -2147483648
  PLANTED 0	UStringValue.at(int)	USTRING("",0.0) | INTEGER(-2147483648)	THROWN:java.lang.IndexOutOfBoundsException	THROWN:java.lang.IllegalStateException	BOTH_THREW	reference threw java.lang.IndexOutOfBoundsException: idx = -2147483648 / subject threw java.lang.IllegalStateException: index out of range: idx = -2147483648
```

The defect **is** in the report — the columns and the note carry it, exactly as the round-2 rationale
promised ("the two result columns already hold `THROWN:<class>` per side"). But `tally()`,
`count()`, `agreementCount()`, `disagreements()`, `rowCount()` and every `# verdict.*` / `# rows.*`
header are bit-identical between a correct port and a port that throws the wrong exception on 89
rows. Deleting `DIFFER_THROWN` removed the only *aggregated* signal for this defect class.

This produces **no false green** — every affected row was already a non-agreement — so it is MINOR,
and for golden-backed sweeps the byte-level golden diff catches it. But sweeps without goldens (the
invariant sweep, and any stage that tallies rather than diffs) cannot see it. If S4+ intends to hold
the port to the historical exception *types*, that requirement currently has no aggregate to assert
against.

---

## 5. Acceptance run — `mvn -q clean && mvn -B verify -Djava.awt.headless=true`

Two full runs, both from `mvn -q clean`. Both `BUILD SUCCESS`, exit 0.

```
[INFO] Tests run: 11, Failures: 0, Errors: 0, Skipped: 0 -- in org.tzi.use.architecture.MavenCyclicDependenciesCoreTest
[INFO] Tests run: 6,  Failures: 0, Errors: 0, Skipped: 0 -- in Uncertainty differential smoke
[INFO] Tests run: 2,  Failures: 0, Errors: 0, Skipped: 0 -- in Unwritten-port invariant
[INFO] Tests run: 9,  Failures: 0, Errors: 0, Skipped: 0 -- in HistoricalOracle class-loader isolation
[INFO] Tests run: 16, Failures: 0, Errors: 0, Skipped: 0 -- in Differential harness regressions
[INFO] Tests run: 1,  Failures: 0, Errors: 0, Skipped: 0 -- in org.tzi.use.uml.mm.ModelAPITest
[INFO] Results:
[INFO] Tests run: 45, Failures: 0, Errors: 0, Skipped: 0        <- surefire, use-core
[INFO] Tests run: 1,  Failures: 0, Errors: 0, Skipped: 0 - in org.tzi.use.OCLExpressionIT
[INFO] Results:
[INFO] Tests run: 1,  Failures: 0, Errors: 0, Skipped: 0        <- failsafe, use-core
[INFO] Tests run: 1,  Failures: 0, Errors: 0, Skipped: 0 -- in org.tzi.use.architecture.MavenLayeredArchitectureTest
[INFO] Results:
[INFO] Tests run: 1,  Failures: 0, Errors: 0, Skipped: 0        <- surefire, use-gui
[INFO] Tests run: 129, Failures: 0, Errors: 0, Skipped: 0 - in org.tzi.use.main.shell.ShellIT
[INFO] Results:
[INFO] Tests run: 129, Failures: 0, Errors: 0, Skipped: 0       <- failsafe, use-gui
```

| | use-core | use-gui | total |
|---|---|---|---|
| surefire | 45 | 1 | **46** |
| failsafe | 1 | 129 | **130** |
| | | | **176** |

**Delta accounted: 169 → 176, +7, all surefire, all in the two files `e8b73e48` touched.**

```
$ for f in DifferentialHarnessRegressionTest UncertaintyDifferentialSmokeTest \
           HistoricalOracleIsolationTest UnwrittenPortInvariantTest; do ...; done
DifferentialHarnessRegressionTest  9443d4b4=11  HEAD=16     (+5)
UncertaintyDifferentialSmokeTest   9443d4b4=6   HEAD=6      ( 0)
HistoricalOracleIsolationTest      9443d4b4=9   HEAD=9      ( 0)
UnwrittenPortInvariantTest         9443d4b4=0   HEAD=2      (+2)
```

`11+6+9 = 26` differential + `11` `MavenCyclicDependenciesCoreTest` + `1` `ModelAPITest` = 38 in
use-core, `+1` use-gui = **39**, the recorded baseline. Now `16+6+9 = 31` + `11` + `1` = 45, `+1` =
**46**. **No pre-existing test was broken; no upstream test changed count.** Failsafe is unchanged at
130.

---

## 6. Determinism and goldens

```
$ sha256sum use-core/target/differential/*.tsv          # run 1
2016948f47fdeaf74ce87140031c1c58156af9fd7fd7a1b66157b0742bc84867  s1-smoke-ureal-add.tsv
fb93bdbd41f18154ba8335f6a4a7d92caee9244cfd0d65ee3e1426ce9601863d  s1-smoke-ureal-minus-faulty.tsv

$ mvn -q clean && mvn -B verify -Djava.awt.headless=true
$ sha256sum use-core/target/differential/*.tsv          # run 2
2016948f47fdeaf74ce87140031c1c58156af9fd7fd7a1b66157b0742bc84867  s1-smoke-ureal-add.tsv
fb93bdbd41f18154ba8335f6a4a7d92caee9244cfd0d65ee3e1426ce9601863d  s1-smoke-ureal-minus-faulty.tsv

$ cmp run1/s1-smoke-ureal-add.tsv run2/s1-smoke-ureal-add.tsv
IDENTICAL s1-smoke-ureal-add.tsv
IDENTICAL s1-smoke-ureal-minus-faulty.tsv

$ cmp use-core/target/differential/<f> docs/port2/differential/<f>
GOLDEN MATCHES BYTE-FOR-BYTE: s1-smoke-ureal-add.tsv
GOLDEN MATCHES BYTE-FOR-BYTE: s1-smoke-ureal-minus-faulty.tsv
```

Note I used `cmp`, which is a genuine byte comparison, **not** `DiffReportWriter.assertMatchesGolden`,
which still uses `Files.readAllLines` (D-5, still open, and still described in its own Javadoc as
"byte for byte"). The regenerated reports match the committed goldens at the byte level; the
in-harness assertion still does not establish what its comment claims.

The invariant sweep output was also identical between the two runs:

```
$ diff <(run1 invariant block) <(run2 invariant block)
IDENTICAL (invariant sweep output)

=== unwritten-port invariant ======================================
seed                 20260817
subject              unwritten-port  (every method body: throw new RuntimeException("TODO: port " + op.key()))
operations           285  (enumerated from use.jar + atenearesearchgroup.uncertainty.jar)
corpora              uReal=24, uInteger=15, uBoolean=11, uString=18, zeroDivisors=7, indexBoundaries=8; receivers=77
rows                 471471
agreement rows       0
verdict tally        {BOTH_THREW=30580, HARNESS_ERROR=388695, MIXED=52196}
===================================================================
```

---

## 7. Scope

```
$ git diff --name-status 30d480db..HEAD -- '*/src/main/*'
$
```

Empty. Confirmed. Nothing under any `src/main`, no pom, no `module-info.java`, no upstream test was
modified by `e8b73e48` or by this verification. My probes live entirely outside the repository.

---

## 8. Confirmed fixed

I re-measured the round-2 claims rather than taking them on trust. These hold:

- **`AGREE_THROWN` / `DIFFER_THROWN` are gone.** `BOTH_THREW` is a non-agreement. Subject A: 30580
  `BOTH_THREW`, 0 agreement, against a subject containing no code. `21816 + 8764 == 30580` — the
  populations are the same rows, reclassified.
- **The note is never empty on a throw-pair** and always carries both classes and both messages
  (Probe5 row text, §4).
- **D-1 (null vs null):** subject B, `{HARNESS_ERROR=471471}`, agreement 0.
- **D-2 (two stubs over an unmarshallable receiver):** Probe2 (4), `169 rows, HARNESS_ERROR=169`,
  disagreements 169.
- **D-3 (`UNSUPPORTED` note):** Probe2 (3), 81 rows `UNSUPPORTED`, all disagreements.
- **`agreements()` / `disagreements()` partition:** `agreementCount()` and the tally agree on every
  probe run; `agreements().size() + disagreements().size() == rowCount()` throughout.
- **The allowlist cannot express a blanket rule** (§3). Nothing I tried got past exact matching.
- **Planted defects in arithmetic, comparison and conversion are reported as `DIFFER`; a swallowed
  error path is reported as `MIXED`** (§4).
- **A perfect port yields `DIFFER=0`** over 471471 rows — no false disagreements.
- **Determinism and goldens** (§6). **Scope** (§7). **Counts** (§5).

## 9. Still open from earlier rounds, re-confirmed

- **D-5**: `assertMatchesGolden` is line-based while its Javadoc says "byte for byte". Still true.
  My byte-level determinism claim in §6 uses `cmp`, not this method.
- **D-6**: `HistoricalOracle.supports()` validates the receiver only, not the argument kinds. Still
  true; visible as `HARNESS_ERROR`, so not a correctness hole.
- **D-7**: partly fixed. `checkOpen()` is in `resolve()`; `NoSuchHistoricalMethodException` is still
  an `IllegalArgumentException` on the public `invoke`/`call` path.
- **D-9 (coverage)**: 8 marshallable receivers; `SBooleanValue` and all collection receivers are
  `UNSUPPORTED`. The invariant inherits this limit. Note it interacts with D-10: widening the corpora
  to include `BOOLEAN`/`STRING` receivers will *increase* the false-agreement count from 444.
- **Documentation drift**: `stage-01.md` and the earlier refutation/audit files still describe
  `AGREE_THROWN`/`DIFFER_THROWN` as live verdicts. Unchanged.

## 10. What has to happen before S4

1. **`VOID` must stop being an agreement.** Either a distinct non-agreement verdict — the shape
   already used for `UNSUPPORTED` and `HARNESS_ERROR`, e.g. `NO_OBSERVABLE_RESULT` — or an explicit
   `AcceptedVoidOperations` sign-off mirroring `AcceptedThrowPairs`. Do not "fix" it by excluding
   void operations from the inventory: that hides the row instead of classifying it, which is the
   mistake round 1 made.
2. **The invariant must quantify over subjects, not name one.** Run
   `anUnwrittenPortAgreesWithNothing` over a list of degenerate candidates — throws, returns Java
   `null`, returns `nullValue()`, **returns `voidValue()`** — and assert zero agreement for each. The
   throwing subject alone is an instance, and the test's own Javadoc explains why instances are not
   enough.
3. **`Result` needs a measurement floor.** Something on `Result` that distinguishes "nothing ran"
   from "everything agreed" — a `measuredRowCount()` counting only rows where two values were
   observed, and a guard that a stage can assert. Move the writer's guard onto that quantity so
   §2's 75-row all-`VOID` green report is refused.
4. **Fix `voidIsDistinctFromNull`'s comment or its assertion.** It currently claims to pin "the exact
   shape of the 'empty-bodied mutator agrees forever' defect" and does not pin it.
5. D-5's Javadoc, and the documentation forward-pointer the porter flagged.

---

**Verdict: DEFECTIVE.** The third door exists and it is D-10: a `Candidate` that implements nothing
scores 444 agreement rows, because the harness treats two declared absences of a result as two equal
results. D-11 turns that into a report whose own headers read `# rows.agreement 72 /
# rows.disagreement 0` over zero comparisons. D-12 leaves the zero-row trap open at the `Result`
level where the round-2 fix guarded only the writer. D-13 is minor. The round-2 fixes themselves are
sound and I confirm all of them; the build is green at 46 + 130 = 176 with the delta fully accounted,
the reports are byte-deterministic, the goldens match, and `src/main` is untouched.
