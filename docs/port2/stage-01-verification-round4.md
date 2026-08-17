# S1 — Empirical verification, round 4 (differential harness, post six-fix)

**Verdict: DEFECTIVE. A fourth door exists and it is the largest of the four.**

**Role**: empirical refuter. I own Maven. I did not write the fixes under review.
**Tree**: branch `port-uncertainty-2`, HEAD `53ed152a` (`fa9bba2d` reviewability, `93e038ac`
behaviour, `53ed152a` documentation). Working tree clean at the start of this review.
**Everything below is pasted output.** Probes ran with `java` directly from
`/tmp/claude-1000/.../scratchpad/probe`, outside the repository, on the classpath
`use-core/target/classes : use-core/target/test-classes : use-core/src/test/resources/historical/use.jar :
use-core/src/test/resources/historical/atenearesearchgroup.uncertainty.jar`. No probe source is in the
repository and no repository file was modified by this review other than this document.

---

## 0. Headline

The six fixes do what they say. D-10 is closed: the do-nothing subject goes 444 → 0 and its 51 752
genuine divergences survive. D1 and D2 are not regressed. Nine planted defects are all reported. Two
full runs from clean are `BUILD SUCCESS` and byte-deterministic. `*/src/main/*` is untouched.

And the harness still scores a port containing no logic as fully agreed, with real measurements, on
**120 of its 285 reachable operations** — of which **76** produce a stage-shaped sweep whose
`isClean()` is `true`, whose report writes successfully, and whose header reads
`# rows.disagreement 0`.

The three previous doors were all *the absence of a measurement being scored as an agreement*. Every
fix, and the whole closure argument, is aimed at that family. This one is a different family: **two
real measurements, correctly compared, correctly equal — over an operation whose answer is the same
on every input the corpus can reach.** `UNMEASURABLE` cannot fire (both sides observed a value).
`measurementCount()` is large. `isClean()` is true. `everyKindIsEitherAnObservationOrUnmeasurable`
passes, and is *right* to pass. The harness makes no false statement at row level; the false
statement is at sweep level, and it is the one a stage will read.

The porter predicted the shape and named the wrong instance:

> "I expect them to try the enum-quantified test first and then look for a value-carrying kind whose
> canonical form is degenerate — which is exactly where (b) is."

(b) is the raw-`int`-vs-`IntegerValue` collision, worth 2 operations. The degenerate thing is not the
canonical form of a kind; it is the **codomain of an operation over the shipped corpora**, and that is
worth 120.

---

## 1. Acceptance — what I ran and what it said

```
mvn -q clean && mvn -B verify -Djava.awt.headless=true      (twice, from clean)
```

Run 1 and run 2, identical:

```
[INFO] Reactor Summary for use 7.5.0:
[INFO] use ................................................ SUCCESS [  0.003 s]
[INFO] use-core ........................................... SUCCESS [ 40.116 s]
[INFO] use-gui ............................................ SUCCESS [ 23.033 s]
[INFO] use-assembly ....................................... SUCCESS [  5.569 s]
[INFO] BUILD SUCCESS
```

Test counts, byte-identical between the two runs:

| module / phase | run 1 | run 2 |
|---|---|---|
| use-core surefire | `Tests run: 61, Failures: 0, Errors: 0, Skipped: 0` | same |
| use-core failsafe | `Tests run: 1, Failures: 0, Errors: 0, Skipped: 0` | same |
| use-gui surefire  | `Tests run: 1, Failures: 0, Errors: 0, Skipped: 0` | same |
| use-gui failsafe  | `Tests run: 129, Failures: 0, Errors: 0, Skipped: 0` | same |

The 61 decompose exactly:

```
Tests run: 11 -- org.tzi.use.architecture.MavenCyclicDependenciesCoreTest
Tests run:  6 -- Uncertainty differential smoke
Tests run:  8 -- Unwritten-port invariant
Tests run:  9 -- HistoricalOracle class-loader isolation
Tests run: 26 -- Differential harness regressions
Tests run:  1 -- org.tzi.use.uml.mm.ModelAPITest
```

**Delta accounted for.** Annotated test methods in the differential package:

```
  d58085a1  16  DifferentialHarnessRegressionTest.java
  d58085a1   9  HistoricalOracleIsolationTest.java
  d58085a1   6  UncertaintyDifferentialSmokeTest.java
  d58085a1   2  UnwrittenPortInvariantTest.java      (@Test x2, not parameterised)
  d58085a1 TOTAL 33
  HEAD      26  DifferentialHarnessRegressionTest.java
  HEAD       9  HistoricalOracleIsolationTest.java
  HEAD       6  UncertaintyDifferentialSmokeTest.java
  HEAD       2  UnwrittenPortInvariantTest.java      (@ParameterizedTest x7 + @Test x1 = 8 executed)
  HEAD  TOTAL 43 declared / 49 executed
```

+10 regression methods and +6 executed invariant cases. Every count is new; no pre-existing test
changed, and none broke.

**Ground rule 2 holds:**

```
$ git diff --name-status 30d480db..HEAD -- '*/src/main/*'
(no output)
```

**Determinism.** The invariant's printed output, stripped of Maven's own `[INFO]` lines (which carry
wall-clock elapsed times and are the only difference between the two logs):

```
$ cmp inv1.txt inv2.txt && echo identical
*** RUN1 == RUN2 byte-identical: 205 lines of unwritten-port-invariant output
482ddb2fef0e97f6915b6e3f44c5c3910cf574337ca0154345bf7ac153308530  inv1.txt
482ddb2fef0e97f6915b6e3f44c5c3910cf574337ca0154345bf7ac153308530  inv2.txt
```

`482ddb2f…` is the digest the porter reported, reproduced independently.

**Goldens:**

```
$ cmp docs/port2/differential/s1-smoke-ureal-add.tsv use-core/target/differential/s1-smoke-ureal-add.tsv
GOLDEN == RUN2  s1-smoke-ureal-add.tsv
f46ef6259d3822f465b7f0f55f4e62e59a9996dc7c9f8beeeccd721666e524d8  s1-smoke-ureal-add.tsv
GOLDEN == RUN2  s1-smoke-ureal-minus-faulty.tsv
2d76736e6c3669a4362974f0056c1295816206126a50ce72946867e9bd370c5c  s1-smoke-ureal-minus-faulty.tsv
```

Both digests match the porter's. Tree clean afterwards.

---

## 2. The family invariant, as shipped — verified, and the task's premise corrected

Task item 1 asks me to verify that subjects (a)–(f) "all score zero agreement AND that no single
operation is fully agreed". **That is not what the tree does, and it should not be.** Pasted from the
acceptance log (`Unwritten-port invariant`, 8/8 green):

```
subject              a-throws  (every method body: throw new RuntimeException("TODO: port " + op.key()))
rows                 471471   measured rows 0       agreement rows 0
verdict tally        {BOTH_THREW=30580, HARNESS_ERROR=388695, MIXED=52196}
fully agreed ops     (none)

subject              b-returns-java-null  (every method body: return null)
rows                 471471   measured rows 0       agreement rows 0
verdict tally        {HARNESS_ERROR=471471}
fully agreed ops     (none)

subject              c-empty-body  (every method body: { } -- i.e. return UValue.voidValue())
rows                 471471   measured rows 51752   agreement rows 0
verdict tally        {DIFFER=51752, HARNESS_ERROR=388695, MIXED=30580, UNMEASURABLE=444}
fully agreed ops     (none)

subject              d-returns-null-value  (every method body: return UValue.nullValue())
rows                 471471   measured rows 51752   agreement rows 0
verdict tally        {DIFFER=51752, HARNESS_ERROR=388695, MIXED=30580, UNMEASURABLE=444}
fully agreed ops     (none)

subject              e-fixed-constant  (every method body: return UValue.uBoolean(true, 1.0))
rows                 471471   measured rows 52196   agreement rows 6931
verdict tally        {AGREE=6931, DIFFER=45265, HARNESS_ERROR=388695, MIXED=30580}
fully agreed ops     (none)

subject              f-echoes-receiver  (every method body: return args.get(0))
rows                 471471   measured rows 52196   agreement rows 4278
verdict tally        {AGREE=4278, DIFFER=47918, HARNESS_ERROR=388695, MIXED=30580}
fully agreed ops
  *** IntegerValue.value()  (48/48 driven rows agreed, 462 rows total; reviewed and signed off)
  *** RealValue.value()  (6/6 driven rows agreed, 462 rows total; reviewed and signed off)

subject              g-throws-error
rows                 0        measured rows 0       agreement rows 0
verdict tally        {}
ESCAPED              java.lang.AssertionError: TODO: port BooleanValue.compareTo(value)  -> the sweep ABORTED
fully agreed ops     (none)
```

So: (a)–(d) and (g) score zero agreement. (e) and (f) do **not**, and the porter is right that
asserting zero for them would be asserting a falsehood — they produce values, and 6931 / 4278 of them
really do equal the reference's. (f) has two fully-agreed operations on a written allowlist. **The
D-10 headline reproduces exactly**: subject c, 444 → 0, six `setTypeToRuntimeType()` operations → none,
51 752 DIFFER rows untouched, 444 rows relabelled `UNMEASURABLE` rather than deleted.

---

## 3. **D-15 (CRITICAL) — the fourth door: a degenerate codomain is scored as fidelity**

### 3.1 The census

Probe A drives the historical oracle against a **second, independent historical oracle** — the
"delegating subject", i.e. a perfect port — over the shipped 285-operation inventory, the shipped six
corpora and seed `20260817`. Row counts reproduce the porter's exactly, which is the check that the
probe is measuring the shipped sweep and not a re-implementation of it:

```
operations           285
rows                 471471
measured rows        51752
agreement rows       51752
verdict tally        {AGREE=51752, BOTH_THREW=30580, HARNESS_ERROR=388695, UNMEASURABLE=444}
FULLY AGREED ops under a DELEGATING subject: 188 of 285
```

For every operation I then counted the **distinct canonical reference results over its measured rows**:

```
=== OPERATIONS WITH A SINGLE-VALUED CODOMAIN OVER THE SHIPPED CORPORA ===
operation                                      measured   driven  the one value
IntegerValue.getRuntimeType()                        48       48  OPAQUE("org.tzi.use.uml.ocl.type.IntegerType|org.tzi.use.uml.ocl.type.IntegerType{BasicType.fTypename=\"Integer\"}")
IntegerValue.isBag()                                 48       48  BOOLEAN(false)
IntegerValue.isDefined()                             48       48  BOOLEAN(true)
IntegerValue.isInteger()                             48       48  BOOLEAN(true)
IntegerValue.type()                                  48       48  OPAQUE("org.tzi.use.uml.ocl.type.IntegerType|…")
RealValue.hashCode()                                  6        6  INTEGER(0)
RealValue.toString()                                  6        6  STRING("0.0")
RealValue.toStringWithType()                          6        6  STRING("0.0 : Real")
RealValue.value()                                     6        6  REAL(0.0)
UBooleanValue.value()                                54       54  BOOLEAN(true)
UIntegerValue.isUInteger()                           90       90  BOOLEAN(true)
URealValue.getRuntimeType()                         144      144  OPAQUE("org.tzi.use.uml.ocl.type.URealType|…")
URealValue.isDefined()                              144      144  BOOLEAN(true)
URealValue.isUReal()                                144      144  BOOLEAN(true)
URealValue.type()                                   144      144  OPAQUE("org.tzi.use.uml.ocl.type.URealType|…")
UStringValue.toBoolean()                            102      102  BOOLEAN(false)
UStringValue.type()                                 102      102  OPAQUE("org.tzi.use.uml.ocl.type.UStringType|…")
…
TOTAL single-valued-codomain operations: 120
```

120 of 285, spread across every receiver the harness can marshal:

```
     19 IntegerValue      23 RealValue        20 UBooleanValue
     19 UIntegerValue     19 URealValue       20 UStringValue
```

Note the denominators. `URealValue.isDefined()` is **144 of 144 driven rows** — the identical
"144/144" that made D-10 critical, arrived at by a completely different route.

### 3.2 Driving it through the real harness

Probe B plugs six new no-logic subjects into `DifferentialSweep` and applies the *shipped*
per-operation predicate (`driven > 0 && agreed == driven`, driven = not HARNESS_ERROR and not
UNSUPPORTED — `UnwrittenPortInvariantTest.java:416-425`):

```
codomain census: 120 of 285 operations produce ONE canonical value on every measured row

--- subject h-const-BOOLEAN(false)   body: return UValue.bool(false)
    rows 471471 / measured 52196 / agreement 6888
    tally {AGREE=6888, DIFFER=45308, HARNESS_ERROR=388695, MIXED=30580}
    FULLY AGREED OPERATIONS: 92

--- subject i-const-BOOLEAN(true)   body: return UValue.bool(true)
    rows 471471 / measured 52196 / agreement 870
    tally {AGREE=870, DIFFER=51326, HARNESS_ERROR=388695, MIXED=30580}
    FULLY AGREED OPERATIONS: 12
      *** IntegerValue.isDefined()  (48/48 driven agreed, 48 measured)
      *** IntegerValue.isInteger()  (48/48 driven agreed, 48 measured)
      *** RealValue.isDefined()  (6/6 driven agreed, 6 measured)
      *** RealValue.isReal()  (6/6 driven agreed, 6 measured)
      *** UBooleanValue.isDefined()  (54/54 driven agreed, 54 measured)
      *** UBooleanValue.isUBoolean()  (54/54 driven agreed, 54 measured)
      *** UBooleanValue.value()  (54/54 driven agreed, 54 measured)
      *** UIntegerValue.isDefined()  (90/90 driven agreed, 90 measured)
      *** UIntegerValue.isUInteger()  (90/90 driven agreed, 90 measured)
      *** URealValue.isDefined()  (144/144 driven agreed, 144 measured)
      *** URealValue.isUReal()  (144/144 driven agreed, 144 measured)
      *** UStringValue.isDefined()  (102/102 driven agreed, 102 measured)

--- subject k-const-STRING("0.0")   body: return UValue.string("0.0")
    FULLY AGREED OPERATIONS: 1        *** RealValue.toString()  (6/6)

--- subject l-const-INTEGER(0)   body: return UValue.integer(0)
    FULLY AGREED OPERATIONS: 1        *** RealValue.hashCode()  (6/6)

--- subject j-constant-table   body: return CONST.get(op.key())  -- 120 hardcoded literals, no logic
    rows 471471 / measured 8616 / agreement 8616
    tally {AGREE=8616, BOTH_THREW=30580, HARNESS_ERROR=388695, MIXED=43580}
    FULLY AGREED OPERATIONS: 120
```

**Subject j is the whole finding in one line.** A class with 120 method bodies, each a single
hardcoded literal — no arithmetic, no branching, it never reads its receiver or its arguments — is
scored `AGREE` on 8616 rows and `DIFFER` on **zero** of them, and is fully agreed on 120 of the 285
operations the harness can reach.

### 3.3 What a stage actually sees

A stage runs one operation over the corpus for that operation's receiver type. Probe C:

```
STAGE URealValue.isDefined()   subject 'const-BOOLEAN(true)'
  summary                     URealValue.isDefined(): 30 rows, 30 measured, AGREE=30
  measurementCount            30
  disagreements().isEmpty()   true   <= a caller asserting this reads PASS
  isClean()                   true   <= *** THE DOCUMENTED PASS PREDICATE SAYS PASS ***
  requireMeasurements(1)      returned normally
  DiffReportWriter.writeAll   WROTE the report
      # rows	30
      # rows.measured	30
      # rows.agreement	30
      # rows.disagreement	0
      # rows.throwClassMismatch	0
      # verdict.AGREE	30
      first data row: 0	URealValue.isDefined()	UREAL(0.0,0.0)	BOOLEAN(true)	BOOLEAN(true)	AGREE	

STAGE URealValue.type()   subject 'const-OPAQUE("org.tzi.use.uml.ocl.type.URealType|…")'
  summary                     URealValue.type(): 30 rows, 30 measured, AGREE=30
  isClean()                   true   <= *** THE DOCUMENTED PASS PREDICATE SAYS PASS ***
      # rows.disagreement	0

STAGE UIntegerValue.isCollection()   subject 'const-BOOLEAN(false)'
  summary                     UIntegerValue.isCollection(): 21 rows, 21 measured, AGREE=21
  isClean()                   true   <= *** THE DOCUMENTED PASS PREDICATE SAYS PASS ***
```

Control, same subject, an operation with a real codomain:

```
STAGE URealValue.lt(value)   subject 'const-BOOLEAN(true)'
  summary                     URealValue.lt(value): 900 rows, 900 measured, DIFFER=900
  isClean()                   false
```

Probe F enumerates this exhaustively over every nullary operation with a single-valued codomain,
sweeping each against `return <its literal>;` over its own receiver corpus:

```
operation                                        rows measured isClean() the constant the port returns
IntegerValue.isDefined()                            8        8  *** TRUE BOOLEAN(true)
IntegerValue.type()                                 8        8  *** TRUE OPAQUE("org.tzi.use.uml.ocl.type.IntegerType…
RealValue.getRuntimeType()                          3        3  *** TRUE OPAQUE("org.tzi.use.uml.ocl.type.RealType|…
URealValue.isUReal()                               30       30  *** TRUE BOOLEAN(true)
URealValue.type()                                  30       30  *** TRUE OPAQUE("org.tzi.use.uml.ocl.type.URealType|…
UBooleanValue.isDefined()                          17       15     false BOOLEAN(true)
UStringValue.isDefined()                           24       23     false BOOLEAN(true)
…
OPERATIONS ON WHICH A NO-LOGIC SUBJECT PRODUCES A FULLY CLEAN,
MEASURED, REPORT-WRITABLE STAGE SWEEP: 76
```

**76 operations.** By receiver: `IntegerValue` 19, `RealValue` 19, `UIntegerValue` 19, `URealValue` 19.

The `UBooleanValue` and `UStringValue` rows read `false` **by accident, not by design**: their
boundary corpora contain `UBOOLEAN(false,-1.0)`, `UBOOLEAN(false,2.0)` and `USTRING("abc",-1.0)`,
whose historical constructors reject the out-of-range probability/confidence, so those rows land on
`HARNESS_ERROR` and drag `disagreements()` above zero. Delete three boundary values and 40 more
operations join the 76. The safeguard here is a corpus coincidence.

### 3.4 Why every one of the six fixes lets it through

| fix | why it does not fire |
|---|---|
| FIX 1 `UNMEASURABLE` | Requires **neither** side to carry an observation. Both sides carry `BOOLEAN(true)`. Correctly not raised. |
| FIX 2 `measurementCount` / `isClean` / writer refusal | `measurementCount() == rowCount() == 30`. `isClean()` is `true`. The writer writes. |
| FIX 3 per-operation invariant | The form is right; the **family is too narrow**. Its only constant subject is `return UValue.uBoolean(true, 1.0)` — a `UBOOLEAN`, and not one of the 120 operations returns a `UBOOLEAN`, which is why subject e reports `fully agreed ops (none)`. Change that one literal to `UValue.bool(false)` and the shipped assertion at `UnwrittenPortInvariantTest.java:147` fails with **92** unreviewed fully-agreed operations. |
| FIX 4 both-sided notes | `AGREE` rows carry an empty note by construction. Nothing to say. |
| FIX 5 `throwClassMismatchCount` | `0`. Nothing threw. |
| FIX 6 de-NUL | Unrelated. |
| `everyKindIsEitherAnObservationOrUnmeasurable` | Asserts that every **value-carrying kind paired with itself is `AGREE`** (`DifferentialHarnessRegressionTest.java:583-586`). `BOOLEAN`/`BOOLEAN` → `AGREE` is exactly what the test demands, and at row level it is correct. The property the test quantifies over is the **value vocabulary**; the degeneracy is in the **operation's codomain**, which no test in the tree examines. |

The closure argument's strongest claim — "the previous rounds each pinned an instance, and this pins
the closure condition" — is true of the vocabulary and false of the sweep. Quantifying over
`Kind.values()` closes "a value-less kind agreeing with itself". It says nothing about "a
value-carrying kind agreeing with itself on every row because there is only one row-value to be had".

### 3.5 Severity

This is not a corner. `isDefined()`, `isUReal()`, `isUInteger()`, `type()`, `getRuntimeType()` and the
sixteen `is*()` type predicates are exactly the operations an S4 adapter writes first and gets right by
accident, and they are exactly the operations whose sweep will read `# rows.disagreement 0` whether or
not anybody wrote them. A stage that reports "operation X: 30 rows, 30 measured, `isClean()`" for each
of the 76 has produced 76 green lines of evidence about a file that could be empty.

---

## 4. **D-16 (MAJOR) — `isClean()` has no coverage floor**

Task item 3. `isClean()` is `measurementCount() > 0 && disagreements().isEmpty()`. One is enough.

```
STAGE URealValue.add(value)   subject 'delegates-to-a-second-oracle'   (domain 1 x 1)
  summary                     URealValue.add(value): 1 rows, 1 measured, AGREE=1
  disagreements().isEmpty()   true   <= a caller asserting this reads PASS
  isClean()                   true   <= *** THE DOCUMENTED PASS PREDICATE SAYS PASS ***
  requireMeasurements(1)      returned normally
  DiffReportWriter.writeAll   WROTE the report
      # rows	1
      # rows.measured	1
      # rows.agreement	1
      # rows.disagreement	0
```

The other degenerate-coverage cases are handled correctly, and I record them because they are the
scenarios the porter's fixes were aimed at:

```
STAGE SBooleanValue.and(value)  (all UNSUPPORTED)
  summary                     1 rows, 0 measured, UNSUPPORTED=1
  disagreements().isEmpty()   false      isClean()   false
  requireMeasurements(1)      THREW IllegalStateException
  DiffReportWriter.writeAll   REFUSED: refusing to write a differential report … that contains no
      measurements: 1 row(s) across 1 sweep result(s), and not one of them compared two observed values.

STAGE URealValue.sqrt()  (receiver corpus of the wrong type -- all HARNESS_ERROR)
  summary                     2 rows, 0 measured, HARNESS_ERROR=2
  disagreements().isEmpty()   false      isClean()   false
  requireMeasurements(1)      THREW IllegalStateException
  DiffReportWriter.writeAll   REFUSED

STAGE URealValue.sqrt()  (EMPTY receiver domain)
  summary                     0 rows, 0 measured
  disagreements().isEmpty()   true   <= a caller asserting this reads PASS
  isClean()                   false
  requireMeasurements(1)      THREW IllegalStateException
  DiffReportWriter.writeAll   REFUSED: refusing to write an empty differential report … 0 rows in total.

STAGE URealValue.sqrt()  (1 marshallable receiver + 25 unmarshallable)
  summary                     26 rows, 1 measured, AGREE=1, HARNESS_ERROR=25
  isClean()                   false
```

So the "all `UNSUPPORTED` / all `HARNESS_ERROR` / all `UNMEASURABLE` / zero-row" doors are shut, and a
caller asserting `disagreements().isEmpty()` alone would still read the empty-domain sweep as a pass —
which is D-12, correctly documented as only partly fixed. What is **not** shut is
"one measurement out of a domain the caller chose to make tiny": `isClean()` cannot tell 1 row from
20 000, and `requireMeasurements(n)` puts the floor in the hands of the caller who is trying to pass.

---

## 5. **D-17 (MAJOR) — the subject controls its own denominator**

The per-operation predicate excludes `HARNESS_ERROR` and `UNSUPPORTED` from `driven`. Both are things
the **subject** raises about itself. A port that answers "the adapter could not marshal this" wherever
it would otherwise be wrong therefore has `agreed == driven` everywhere. Probe G, against a subject
that implements `isDefined()` and raises `HarnessMarshallingException` for every other operation:

```
subject: delegates-to-a-second-oracle
  rows 471471 / measured 51752 / agreement 51752
  tally {AGREE=51752, BOTH_THREW=30580, HARNESS_ERROR=388695, UNMEASURABLE=444}
  operations fully agreed under the shipped per-operation predicate: 188 of 285

subject: hides-behind-HARNESS_ERROR
  rows 471471 / measured 444 / agreement 444
  tally {AGREE=444, HARNESS_ERROR=471027}
  operations fully agreed under the shipped per-operation predicate: 6 of 285
```

**444 agreement rows, zero DIFFER rows anywhere in a 471 471-row sweep**, from a port that implements
one method. That is numerically the D-10 figure again, reached through the third distinct route in
three rounds. `isClean()` does protect a stage here (the `HARNESS_ERROR` rows are disagreements), so
this is a defect in the per-operation predicate rather than in the pass predicate — but the
per-operation predicate is the one the standing invariant is built on, and `HarnessMarshallingException`
is precisely what `Candidate`'s Javadoc instructs an S4 adapter author to throw.

Note also the first line: **a perfect port is "fully agreed" on only 188 of 285 operations.** The other
97 contain `BOTH_THREW` rows — shared error paths, which are non-agreements by design. S4 cannot sign
those off green without hand-authored `AcceptedThrowPairs` entries. That is a policy consequence, not a
defect, but it is a large planning fact that no document states.

---

## 6. **D-18 (MAJOR) — the canonical-form collision, quantified**

The porter reported this as an unfixed blind spot worth two operations. Probe E reads the declared
return type of all 285 reachable operations off the vendored jars:

```
reachable operations inspected: 285
declared return types:
     140  boolean
      18  int
      18  java.lang.String
       6  double
      16  org.tzi.use.uml.ocl.type.Type
       3  org.tzi.use.uml.ocl.value.BooleanValue
       3  org.tzi.use.uml.ocl.value.IntegerValue
       3  org.tzi.use.uml.ocl.value.RealValue
       2  org.tzi.use.uml.ocl.value.StringValue
       1  org.tzi.use.uml.ocl.value.SequenceValue
      19  org.tzi.use.uml.ocl.value.UBooleanValue
      21  org.tzi.use.uml.ocl.value.URealValue
      12  org.tzi.use.uml.ocl.value.UIntegerValue
       5  org.tzi.use.uml.ocl.value.UStringValue
       9  org.tzi.use.uml.ocl.value.UncertainBooleanValue
       1  uDataTypes.UInteger
       8  void

OPERATIONS WHOSE RETURN TYPE SHARES A CANONICAL FORM WITH ANOTHER JAVA TYPE: 193 of 285
```

`HistoricalOracle.fromHistorical` (lines 709-720) maps a raw `Boolean`/`Integer`/`Double`/`CharSequence`
to the same `UValue.Kind` as `BooleanValue`/`IntegerValue`/`RealValue`/`StringValue`. So on 193 of 285
operations — 68% — a port that returns the wrong member of the pair, right content and wrong Java type,
is scored `AGREE` on every row. The allowlist of two on subject f is the visible tip of that; the
number is 193.

---

## 7. **D-19 (MINOR) — coverage, quantified**

61 of 285 operations produce **zero measurements** across the entire 471 471-row sweep even against a
perfect port. 52 of them cannot be driven at all:

```
=== OPERATIONS WITH ZERO MEASUREMENTS ACROSS THE WHOLE SWEEP ===
count: 61 of 285
  BooleanValue.compareTo(value)  rows=6391 driven=0
  BooleanValue.isDefined()  rows=462 driven=0
  … 25 more BooleanValue.* …
  StringValue.compareTo(value)  rows=6391 driven=0
  StringValue.value()  rows=462 driven=0
  … 23 more StringValue.* …
  IntegerValue.setTypeToRuntimeType()  rows=462 driven=48
  URealValue.setTypeToRuntimeType()  rows=462 driven=144
  UBooleanValue.equalsC(value,double)  rows=19173 driven=1458
  UStringValue.toInteger()  rows=462 driven=102
  UStringValue.toReal()  rows=462 driven=102
```

`BooleanValue` and `StringValue` are in `MARSHALLABLE_RECEIVERS`, so `supports()` returns `true`, but no
corpus contains a `BOOLEAN` or `STRING` value, so every row is `HARNESS_ERROR`. Widening the corpora to
reach them widens the D-15 population by roughly 40 more single-valued-codomain operations, since every
one of those 52 is a type predicate or an accessor of a constant.

---

## 8. Planted defects — 9 of 9 reported

Base subject: a second historical oracle (a perfect port), with one named operation corrupted.

```
DEFECT NONE  on  URealValue.add(value)                     <- control
  URealValue.add(value): 784 rows, 784 measured, AGREE=784
  isClean() = true   <= control: the faithful port IS clean

DEFECT ARITHMETIC  on  URealValue.add(value)               value + 1.0
  784 rows, 784 measured, AGREE=253, DIFFER=531     isClean() = false   <= defect REPORTED
    0  URealValue.add(value)  UREAL(0.0,0.0) | UREAL(0.0,0.0)  UREAL(0.0,0.0)  UREAL(1.0,0.0)  DIFFER

DEFECT COMPARISON  on  URealValue.lt(value)                negated
  784 rows, 784 measured, DIFFER=784                isClean() = false   <= defect REPORTED
    0  URealValue.lt(value)  UREAL(0.0,0.0) | UREAL(0.0,0.0)  UBOOLEAN(true,0.0)  UBOOLEAN(false,0.0)  DIFFER

DEFECT CONVERSION  on  URealValue.toInteger()              Java (int) cast instead of floor
  28 rows, 28 measured, AGREE=24, DIFFER=4          isClean() = false   <= defect REPORTED
    9  URealValue.toInteger()  UREAL(-0.5,0.25)  INTEGER(-1)  INTEGER(0)  DIFFER
   22  URealValue.toInteger()  UREAL(-46.064505,0.782649)  INTEGER(-47)  INTEGER(-46)  DIFFER

DEFECT STRING  on  UStringValue.uToUpperCase()             returns the receiver unchanged
  22 rows, 21 measured, AGREE=8, DIFFER=13, HARNESS_ERROR=1   isClean() = false   <= defect REPORTED
    3  UStringValue.uToUpperCase()  USTRING("a",0.0)  USTRING("A",0.0)  USTRING("a",0.0)  DIFFER

DEFECT ERROR_CLASS  on  UStringValue.at(int)               right failure, wrong exception class
  176 rows, 48 measured, AGREE=48, BOTH_THREW=120, HARNESS_ERROR=8, throwClassMismatch=120
  isClean() = false   <= defect REPORTED
    0  …  THROWN:java.lang.IndexOutOfBoundsException  THROWN:java.lang.IllegalStateException  BOTH_THREW
       reference threw java.lang.IndexOutOfBoundsException: idx = -2147483648 /
       subject threw java.lang.IllegalStateException: idx = -2147483648

DEFECT ZERO_DIVISOR  on  URealValue.divideBy(value)        returns 0 instead of Infinity
  364 rows, 364 measured, AGREE=75, DIFFER=289      isClean() = false   <= defect REPORTED
    0  URealValue.divideBy(value)  UREAL(0.0,0.0) | UINTEGER(0,0.0)  UREAL(NaN,NaN)  UREAL(0.0,0.0)  DIFFER

DEFECT CONFIDENCE_EDGE  on  UStringValue.confidence()      wrong ONLY at exactly 0.0 and exactly 1.0
  22 rows, 21 measured, AGREE=17, DIFFER=4, HARNESS_ERROR=1   isClean() = false   <= defect REPORTED
    0  UStringValue.confidence()  USTRING("",0.0)  REAL(0.0)  REAL(0.5)  DIFFER
    1  UStringValue.confidence()  USTRING("",1.0)  REAL(1.0)  REAL(0.5)  DIFFER

DEFECT EMPTY_STRING  on  UStringValue.uSize()              returns 1 for the empty string
  22 rows, 21 measured, AGREE=18, DIFFER=3, HARNESS_ERROR=1   isClean() = false   <= defect REPORTED
    0  UStringValue.uSize()  USTRING("",0.0)  UINTEGER(0,0.0)  UINTEGER(1,0.0)  DIFFER

DEFECT INDEX_RANGE  on  UStringValue.uAt(int)              swallows the out-of-range failure
  176 rows, 48 measured, AGREE=48, MIXED=120, HARNESS_ERROR=8   isClean() = false   <= defect REPORTED
    0  …  THROWN:java.lang.IllegalArgumentException  USTRING("",0.0)  MIXED
       one side threw and the other returned. reference threw java.lang.IllegalArgumentException:
       lower should be greater than 0 / subject returned USTRING("",0.0)
```

Two of these fired only after I fixed my own probe: my first `CONVERSION` plant guarded on
`Kind.UINTEGER` when `URealValue.toInteger()` returns `Kind.INTEGER`, and my first `ZERO_DIVISOR` plant
assumed the historical `divideBy(0)` throws. Measured, it does not:

```
URealValue.toInteger() on UREAL(-2.5,0.5) -> INTEGER(-3)      (floor, not truncation)
URealValue.divideBy(4.0,0.5 / UREAL(0.0,0.0)) -> UREAL(Infinity,Infinity)   (no throw)
```

Both were probe bugs, not harness misses, and both are recorded here because the first run of Probe D
printed `*** DEFECT NOT DETECTED ***` for them and it would have been easy to report that.

**Also worth recording**: the control on `UStringValue.at(int)` is `isClean() = false` against a
*perfect* port, because 120 rows are `BOTH_THREW` with identical classes **and identical messages**:

```
DEFECT NONE  on  UStringValue.at(int)
  176 rows, 48 measured, AGREE=48, BOTH_THREW=120, HARNESS_ERROR=8   isClean() = false
    0  …  THROWN:java.lang.IndexOutOfBoundsException  THROWN:java.lang.IndexOutOfBoundsException  BOTH_THREW
       reference threw java.lang.IndexOutOfBoundsException: idx = -2147483648 /
       subject threw java.lang.IndexOutOfBoundsException: idx = -2147483648
```

That is the deliberate BOTH_THREW policy working, and the cost of it: every error path needs a written
sign-off before it can read green. Fine — but it means `isClean()` will be `false` for a correct port
on 97 of 285 operations, so a stage told "assert isClean()" will be forced towards
`AcceptedThrowPairs`, which is the one remaining agreement-without-two-values route.

---

## 9. Confirmed fixed / not regressed

* **D-10** — subject c: 444 → 0 agreement rows; six `setTypeToRuntimeType()` operations → none;
  51 752 DIFFER rows preserved; 444 rows now `UNMEASURABLE`. Reproduced independently (§2).
* **One-sided absence stays DIFFER** — subject c has `DIFFER=51752` and subject n
  (right kind, wrong contents) has `agreement 0 / DIFFER 51752`, i.e. the tempting over-broad version
  of FIX 1 was not taken.
* **D1 / D2 / zero-row / zero-measurement** — the writer refuses both shapes with the messages pasted
  in §4; `requireMeasurements` throws; `isClean()` is `false` for all of them.
* **FIX 4 both-sided notes** — verified on live rows in §8 (`MIXED`, `BOTH_THREW`) and in the
  acceptance log's D1 reproduction, where the two *different* `HarnessMarshallingException` messages
  both survive.
* **FIX 5** — `throwClassMismatch=120` separates the wrong-exception-class plant from the control,
  whose tally, row count and agreement count are otherwise bit-identical (48/120/8 both ways).
* **FIX 6** — `AcceptedThrowPairs.java` is plain ASCII; `.gitattributes` is `*.java diff`, no `text`.
* **`ACCEPTED_THROW` is unreachable in practice** — `AcceptedThrowPairs.builder()` appears only at
  `DifferentialHarnessRegressionTest.java:353` and `:399`. No sign-off exists in the tree.
* **`everyKindIsEitherAnObservationOrUnmeasurable`** — read at
  `DifferentialHarnessRegressionTest.java:553-601`; it does exactly what the porter claims.
* **Determinism, goldens, `src/main`, test deltas** — §1.

---

## 10. What I tried that did *not* find a door

So that "I could not find another" is not the claim.

1. **`NULL`/`NULL`.** Unreachable, as claimed: subject d's tally has `UNMEASURABLE=444` and no more,
   i.e. all 444 come from the eight `void` operations, none from a historical `null` return.
2. **Empty `SEQUENCE`.** `SEQUENCE[]` carries an observation, so a subject returning it could green an
   operation that always returns an empty sequence. The codomain census lists every single-valued
   operation and none of them returns a `SEQUENCE`.
3. **`OPAQUE` forgery.** Covered — and it *works*: the constant-table subject greens
   `URealValue.type()` and `getRuntimeType()` by returning a hand-typed
   `OPAQUE("org.tzi.use.uml.ocl.type.URealType|…{BasicType.fTypename=\"UReal\"}")` string. Folded into
   D-15 rather than reported separately.
4. **Right kind, arbitrary contents** (task item 1). Subject n: `agreement 0`, `DIFFER 51752`. Clean.
5. **Correct for exactly one operation** (task item 1). Subject m: 1296 AGREE, 0 fully-agreed
   operations — `URealValue.add(value)` is not fully agreed even when delegated, because its
   `BOTH_THREW` rows are non-agreements. The harness handles this one correctly.
6. **Delegating to the historical oracle** (task item 1). Greens everything, by construction; the
   harness cannot detect it and is not expected to. That is what `assertIsolated` and
   `HistoricalOracleIsolationTest` exist for, and they are green.
7. **`supports()` returning false to dodge disagreement.** All rows become `UNSUPPORTED`, which is a
   non-agreement, so `isClean()` is `false`. Shut. (`HarnessMarshallingException` is the version that
   works against the *per-operation* predicate — D-17.)
8. **`agreements()` / `disagreements()` partition drift.** Asserted per sweep inside the invariant
   itself (`UnwrittenPortInvariantTest.java:403`) and re-checked in Probe A across all 471 471 rows.
   No drift.
9. **`-Duse.differential.golden.refresh=true` as a silent-greening path.** Opt-in only, appears in no
   pom or CI file (`grep` over `*.xml` returns nothing). Not a door today; it is a door the day
   someone puts it in a script.
10. **`throwClassMismatchCount` over-counting.** It compares the two result columns, which are
    `THROWN:<class>` and carry no message, so it counts class mismatches and not message mismatches,
    as documented.

---

## 11. What has to change before S4 starts

1. **`isClean()` is not sufficient as a stage pass predicate, and must not be documented as one.**
   A stage must additionally assert that the operation's sweep **observed more than one distinct
   reference value** — i.e. that the sweep could have failed. The census in §3.1 is 40 lines of code;
   it belongs in `DifferentialSweep.Result` as something like
   `distinctReferenceResults()`, with `isClean()` requiring `>= 2` or the operation being on a written,
   reviewed allowlist of genuinely-constant operations exactly like `ECHO_SUBJECT_REVIEWED`.
   The 120 are enumerable and stable; making them a signed-off list costs one afternoon.
2. **Add a constant subject per canonical kind to the invariant family** — at minimum
   `UValue.bool(false)`, `UValue.bool(true)`, `UValue.integer(0)`, `UValue.string("")`, and an
   `OPAQUE`. Today the family's only constant is a `UBOOLEAN`, which no reachable operation returns,
   and that single choice is why the shipped test reports `(none)`.
3. **Exclude subject-raised `HARNESS_ERROR` from shrinking the denominator**, or assert a floor on
   `driven` per operation (D-17).
4. **Record the coverage facts in the report header**: `# operations.singleValuedCodomain`,
   `# accepted.*` (still missing, D-14 unfixed). A reader of a 30-row all-AGREE report has no way to
   know it could not have failed.

---

## 12. Verdict

**DEFECTIVE.** The six fixes are real, correct, well-evidenced, and they close the family they were
aimed at. They do not close the space. The fourth door is open on 120 of 285 operations, 76 of them
producing a fully clean, fully measured, report-writable stage sweep against a subject containing no
logic, and it is open through a mechanism no existing test looks at.

Three rounds ago the number was 21 816. Two rounds ago it was 444. This round it is 8616 agreement
rows over 120 operations — and unlike the previous three, every single one of those rows is a
*correct* row. That is what makes it worse, not better: there is no row to fix. The fix has to be at
the level of what a sweep is allowed to claim.

---

*Probe sources: `ProbeA` (codomain census), `ProbeB` (constant family through the real harness),
`ProbeC` (stage-shaped sweeps and degenerate coverage), `ProbeD` (nine planted defects), `ProbeE`
(return-type collision census), `ProbeF` (exact size of the door), `ProbeG` (denominator control).
All in `/tmp/claude-1000/.../scratchpad/probe/src/org/tzi/use/uncertainty/differential/`, outside the
repository, compiled against `use-core/target/{classes,test-classes}` and the two vendored jars.*
