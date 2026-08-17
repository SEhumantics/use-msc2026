# S1 round 6 — the four remaining defects, closed

**2026-08-17, branch `port-uncertainty-2`, behaviour commit `d13d4858`. Written by the porter.**
Test-scoped throughout; `git diff --name-status 30d480db..HEAD -- '*/src/main/*'` is empty.

Round 5 returned `SOUND_WITH_DOCUMENTED_LIMITS` from two independent reviewers and left four defects
open that this round closes: three named preconditions on S4 quoting any number (**D-34**, **D-35**,
**D-36**) and one detection hole that matters specifically for a port whose whole subject is the
U-types (**D-18**). Two further items were to be **recorded and not fixed**, with their measured
numbers, because they are properties of the method rather than bugs: **D-30** (a defect at an input
the corpora never reach is invisible) and the two concealment routes **P8 / P9** (**D-17**, narrowed
by **D-32**). Both are in §6 with an assessment of whether a cheap guard is worth building.

Every figure below is pasted from a run. The commands are named. Nothing in this file was written
from memory before the run that produced it finished — the round-5 verifier's disclosure is the
standard this file is held to.

---

## 1. D-18 — right content, wrong Java type

### 1.1 What was wrong, and what was already right

Two different claims were bundled under one defect id, and they had different starting points.

* **A `Kind` difference was always caught.** `UValue.canonical()` leads with the kind, so
  `UREAL(3.0,0.0)` never compared equal to `UINTEGER(3,0.0)`, and `INTEGER(3)` never compared equal
  to `UINTEGER(3,0.0)`. A port answering `URealValue` where the historical answers `UIntegerValue`,
  or `IntegerValue` where `UIntegerValue` is required, was already a `DIFFER`. It is now **pinned**
  as such, in `DifferentialHarnessRegressionTest.rightContentInTheWrongJavaTypeIsADifference` step (1),
  so it cannot quietly stop being true.
* **A runtime-class difference inside one kind was not caught.** `HistoricalOracle.fromHistorical`
  mapped a raw `Boolean`/`Integer`/`Double`/`CharSequence` to the same kind *and the same canonical
  string* as `BooleanValue`/`IntegerValue`/`RealValue`/`StringValue`. This is the defect, and it is
  the one the register sized at 193 of 285 operations.

### 1.2 The fix

`UValue` now carries `javaType()` — the fully-qualified class a value was **observed as** — and
`canonical()` appends its simple name:

```
BOOLEAN(true)@Boolean          a raw java.lang.Boolean
BOOLEAN(true)@BooleanValue     an org.tzi.use.uml.ocl.value.BooleanValue
```

`fromHistorical` attributes **every** branch from `result.getClass().getName()`, uniformly, so a
branch added later cannot fall back to an assumption by omission. The factories are unchanged and
still take content only; a value built by a factory is typed as *the `Value` class of its kind*,
which is the type a corpus entry marshals to and the type an adapter returning that kind is
claiming. `NULL` and `VOID` stand for the absence of a result, carry no class, and render exactly as
they always did.

There is deliberately **no "unattributed" state that matches everything**. A wildcard would let a
subject opt out of the check by not answering the question, which is D-17's shape.

A `DIFFER` row whose two sides disagree about the Java type carries a note naming both **fully
qualified** types and saying whether the content was identical — because "the port returned the
right number in the wrong class" and "the port returned the wrong number" are different findings and
the two columns look nearly the same:

```
0  URealValue.neg()  UREAL(1.0,0.0)@URealValue  INTEGER(7)@Integer  INTEGER(7)@IntegerValue  DIFFER
   java type mismatch: reference returned java.lang.Integer (INTEGER(7)@Integer) / subject returned
   org.tzi.use.uml.ocl.value.IntegerValue (INTEGER(7)@IntegerValue); the content is IDENTICAL --
   right content, wrong Java type (defect D-18); this row is a divergence because a port of these
   classes must reproduce the declared result type, not only the payload.
```

An ordinary content divergence keeps the empty note it always had: the columns already say it, and
filling every `DIFFER` row with prose would bury the rows where the note is load-bearing.

### 1.3 BEFORE and AFTER, measured on the same planted defect

The planted infidelity is the most ordinary mistake a re-implementation of this API can make:
wherever the historical operation returns a **raw** Java value, the port returns the corresponding
`org.tzi.use.uml.ocl.value.*` wrapper, with identical content — `IntegerValue.value()` declared to
return `IntegerValue` rather than `int`. It is planted by round-tripping the produced value through
`toHistorical`/`fromHistorical`, so the payload is *provably* unchanged and the only thing that
moves is the class. Subject and reference are both `HistoricalOracle` instances, so nothing else in
the sweep can drift. Test:
`PortedInfidelityDetectionPowerTest.aWrongJavaTypeWithRightContentIsADivergence`.

**BEFORE** — `mvn -B -pl use-core test -Dtest='PortedInfidelityDetectionPowerTest#aWrongJavaTypeWithRightContentIsADivergence'`
against the tree at `90404528`:

```
=== D-18: right content, wrong Java type =========================
operations           285
control  rows        19083, measured 17199, agreed 17199  {AGREE=17199, BOTH_THREW=910, HARNESS_ERROR=883, UNMEASURABLE=91}
boxed    rows        19083, measured 17199, agreed 17199  {AGREE=17199, BOTH_THREW=910, HARNESS_ERROR=883, UNMEASURABLE=91}
control DIFFER+MIXED 0   <- MUST be 0
boxed   DIFFER rows  0
DETECTED on          0 of 285 operations
stage passes         control 74 -> boxed 74; lost 0: []
=================================================================
[ERROR] ... a port returning the right content with the wrong Java type was scored as agreeing on
        every row of every operation (defect D-18) ==> expected: <false> but was: <true>
```

The two tallies are **byte-identical**. A port wrong about the Java type of 182 operations was
indistinguishable, in every aggregate the harness published, from a perfect one.

**AFTER** — same command, same seed, after `d13d4858`:

```
=== D-18: right content, wrong Java type =========================
operations           285
control  rows        19083, measured 17199, agreed 17199  {AGREE=17199, BOTH_THREW=910, HARNESS_ERROR=883, UNMEASURABLE=91}
boxed    rows        19083, measured 17199, agreed 13754  {AGREE=13754, BOTH_THREW=910, DIFFER=3445, HARNESS_ERROR=883, UNMEASURABLE=91}
control DIFFER+MIXED 0   <- MUST be 0
boxed   DIFFER rows  3445
DETECTED on          182 of 285 operations
stage passes         control 74 -> boxed 45; lost 29: [BooleanValue.compareTo(value),
  BooleanValue.hashCode(), BooleanValue.isFalse(), BooleanValue.isTrue(), BooleanValue.toString(),
  BooleanValue.toStringWithType(), BooleanValue.value(), IntegerValue.compareTo(value),
  IntegerValue.hashCode(), IntegerValue.toString(), IntegerValue.toStringWithType(),
  IntegerValue.value(), StringValue.compareTo(value), StringValue.hashCode(),
  StringValue.toString(), StringValue.toStringWithType(), StringValue.value(),
  UIntegerValue.compareTo(value), UIntegerValue.hashCode(), UIntegerValue.toString(),
  UIntegerValue.toStringWithType(), UIntegerValue.uncertainty(), UIntegerValue.value(),
  URealValue.compareTo(value), URealValue.hashCode(), URealValue.toString(),
  URealValue.toStringWithType(), URealValue.uncertainty(), URealValue.value()]
```

| | before | after |
|---|---|---|
| `DIFFER` rows | **0** | **3 445** |
| operations detected on | **0** of 285 | **182** of 285 |
| stage passes | 74, unchanged | **74 → 45**, 29 lost |
| verdict tally vs a perfect port | identical | differs by 3 445 rows |

**182, not 193.** The register's 193 is a *static* count of operations whose declared return type
shares a canonical form with another Java type. 182 is the *measured* count of those the shipped
corpora actually drive to a returned value. The eleven-operation gap is the same corpus-depth fact
as D-19/D-28/D-31: an operation that never returns cannot be detected on. Both numbers are true and
they answer different questions.

### 1.4 THE CONTROL — the fix is not over-strict

The brief's condition was explicit: if the perfect-port control breaks, the fix is over-strict and
must be said so. It does not break. From the same run:

```
control DIFFER+MIXED 0   <- MUST be 0
```

and, asserted in the same test, `assertEquals(Set.of(), control.divergingOperations())`. In the full
`subtleInfidelitiesAreDetectedOrNamed` run the control is unchanged from round 5 —

```
=== detection power: control (a perfect port) =====================
rows                 19083
measured rows        17199
agreement rows       17199
verdict tally        {AGREE=17199, BOTH_THREW=910, HARNESS_ERROR=883, UNMEASURABLE=91}
diverging operations 0   <- MUST be 0, or nothing below is attributable to a planted defect
stage passes         74 of 285  (isStagePass(1, none()))
```

— and the round-5 exact blind-spot set is still exactly
`{P11-negative-zero-collapse / URealValue.round()  [STAGE PASS]}`, so no probe gained or lost
detection as a side effect.

### 1.5 Equivalent representation, distinguished from wrong type — measured

The control is necessary and not sufficient: both of its sides are the same code, so they pick the
same class trivially. The real question is whether any operation in the shipped corpora has **two
legitimate representations**, because then a faithful port that picked the other one would be
reported as wrong on every row. So the reference was asked alone, over every operation and every
tuple of the stage-shaped domains. Test:
`PortedInfidelityDetectionPowerTest.noOperationAnswersWithTwoRuntimeClasses`.

```
=== representation census: what the reference actually returns =====
operations                 285  (274 ever returned a classed value)
--- every runtime class the reference returned, and on how many rows
  1562	java.lang.Boolean
  90	java.lang.Double
  1579	java.lang.Integer
  214	java.lang.String
  4	org.tzi.use.uml.ocl.type.BooleanType
  16	org.tzi.use.uml.ocl.type.IntegerType
  2	org.tzi.use.uml.ocl.type.RealType
  30	org.tzi.use.uml.ocl.type.StringType
  18	org.tzi.use.uml.ocl.type.UBooleanType
  30	org.tzi.use.uml.ocl.type.UIntegerType
  48	org.tzi.use.uml.ocl.type.URealType
  34	org.tzi.use.uml.ocl.type.UStringType
  269	org.tzi.use.uml.ocl.value.BooleanValue
  39	org.tzi.use.uml.ocl.value.IntegerValue
  39	org.tzi.use.uml.ocl.value.RealValue
  17	org.tzi.use.uml.ocl.value.SequenceValue
  54	org.tzi.use.uml.ocl.value.StringValue
  7422	org.tzi.use.uml.ocl.value.UBooleanValue
  1164	org.tzi.use.uml.ocl.value.UIntegerValue
  4176	org.tzi.use.uml.ocl.value.URealValue
  377	org.tzi.use.uml.ocl.value.UStringValue
  15	uDataTypes.UInteger
--- classes per UValue.Kind (two means the KIND is ambiguous: D-18) ---
  BOOLEAN  [java.lang.Boolean, org.tzi.use.uml.ocl.value.BooleanValue]   <== two representations of one kind
  INTEGER  [java.lang.Integer, org.tzi.use.uml.ocl.value.IntegerValue]   <== two representations of one kind
  OPAQUE   [ ...type.BooleanType, ...type.IntegerType, ...type.RealType, ...type.StringType,
             ...type.UBooleanType, ...type.UIntegerType, ...type.URealType, ...type.UStringType,
             uDataTypes.UInteger ]                                      <== two representations of one kind
  REAL     [java.lang.Double, org.tzi.use.uml.ocl.value.RealValue]      <== two representations of one kind
  SEQUENCE [org.tzi.use.uml.ocl.value.SequenceValue]
  STRING   [java.lang.String, org.tzi.use.uml.ocl.value.StringValue]    <== two representations of one kind
  UBOOLEAN [org.tzi.use.uml.ocl.value.UBooleanValue]
  UINTEGER [org.tzi.use.uml.ocl.value.UIntegerValue]
  UREAL    [org.tzi.use.uml.ocl.value.URealValue]
  USTRING  [org.tzi.use.uml.ocl.value.UStringValue]
--- operations whose OWN answers used more than one class -------------
  (none)
===================================================================
```

**How "wrong type" is distinguished from "equivalent representation": by measurement, not by
argument.** Five kinds are carried by more than one class — that *is* D-18 — but the last block is
the one that decides it: **no operation, 0 of 285, ever answers with more than one runtime class.**
A historical operation's declared return type is a single class, so for any one operation there is
exactly one right answer, and "the port used the other class" is a defect rather than a
representation choice. There is therefore no case in the shipped corpora where this fix can raise a
false divergence, and the assertion fails loudly if a widened corpus ever creates one — at which
point the justification above has to be re-read before the green is believed again.

**The one legitimately-different representation that does exist is the package.** The historical
classes are loaded from a vendored jar by an isolated class loader; a port that relocated
`URealValue` into another package would, under a fully-qualified comparison, show *every row of
every operation* as a divergence — a difference in where a file lives, not in what an operation
answered. So `canonical()` compares the **simple name**, and the fully-qualified name survives on
`javaType()` and is what the row note prints. The cost is stated rather than hidden: two distinct
classes sharing one simple name would compare equal, which is not a shape a port of this API takes.
Pinned by `DifferentialHarnessRegressionTest.theTypeTokenIsPackageInsensitiveOnPurpose`.

### 1.6 The goldens: what moved and why

Refreshed deliberately with `-Duse.differential.golden.refresh=true` on
`UncertaintyDifferentialSmokeTest`, as the contract requires, in the same commit as the behaviour
that changed them. Measured against the previous bytes:

| | `s1-smoke-ureal-add.tsv` | `s1-smoke-ureal-minus-faulty.tsv` |
|---|---|---|
| `#` header block | **byte-identical** | **byte-identical** |
| data rows changed | 784 of 784 | 784 of 784 |
| verdict column changed anywhere | no | no |
| note column changed anywhere | no | no |
| content with the `@` token stripped | **identical** | **identical** |

One row, before and after:

```
0  URealValue.add(value)  UREAL(0.0,0.0) | UREAL(0.0,0.0)  UREAL(0.0,0.0)  UREAL(0.0,0.0)  AGREE
0  URealValue.add(value)  UREAL(0.0,0.0)@URealValue | UREAL(0.0,0.0)@URealValue  UREAL(0.0,0.0)@URealValue  UREAL(0.0,0.0)@URealValue  AGREE
```

So the entire change is the appended type token, on 784 rows × 4 columns in each file, and only one
token appears in either file: `@URealValue`, 6 272 occurrences across the two. **The header being
byte-identical is the substantive point**: `distinctReferenceValues` is still 258 and 389, because
the type token is constant over these sweeps and the map from value to canonical form is still
injective. No count that S1 published moved.

### 1.7 What the fix cost the record, and it is not a loss

`UnwrittenPortInvariantTest.ECHO_SUBJECT_REVIEWED` held four operations —
`BooleanValue.value()`, `BooleanValue.isTrue()`, `IntegerValue.value()`, `StringValue.value()` — with
a Javadoc calling them "the same limit, stated four times". They were D-18's whole *visible* extent:
the part of a 193-operation blind spot that a subject doing nothing but handing back its receiver
happened to reach. That set is now **empty**, and `RealValue.value()` — the operation whose move
from an asserted list to a printed one made D-35 concrete — is detected for the same reason.

```
=== unwritten-port invariant: f-echoes-receiver =================
verdict tally        {AGREE=4567, DIFFER=63429, HARNESS_ERROR=618462, MIXED=39880}
fully agreed ops, DISCRIMINATING (a finding about the subject)  (none)
fully agreed ops, NOT DISCRIMINATING (a finding about the corpus)  (none)   [ASSERTED ...]
```

Both buckets empty, both asserted. The constant stays in the file, empty, with the whole story in
its Javadoc: a fifth operation becoming fully agreeable to a subject with no code in it still fails
this test and still has to be explained.

One test needed its subject corrected rather than its expectation:
`aDegenerateOperationNeedsAWrittenSignOff` fed `URealValue.isUReal()` the literal
`UValue.bool(true)`, and `isUReal()` is declared `public boolean`, so that literal is now a
detected D-18 defect (24 `DIFFER` rows). The literal is now
`UValue.bool(true).asJavaType("java.lang.Boolean")` and the sign-off key is `BOOLEAN(true)@Boolean`.
That is the fix working on its first contact with the tree's own code.

---

## 2. D-34 — a report could understate its own sign-offs

`DiffReportWriter.writeAll`'s 3-argument form substituted `AcceptedDegenerateOperations.none()`, and
all five call sites used it. So a stage could take a pass that exists **only** because a human signed
a degenerate operation off, and publish a report whose header asserts that no sign-off was in force.

**BEFORE** — a scratch test on the tree at `90404528`, sweeping `URealValue.isUReal()` against a
one-literal subject, granting the pass with a sign-off, and then writing the report with the
3-argument form:

```
=== D-34 BEFORE ==================================================
sign-off in force?   1  [URealValue.isUReal()|BOOLEAN(true) -> SCRATCH sign-off: the historical body is iconst_1/ireturn.]
stage pass WITHOUT the sign-off? false
stage pass WITH    the sign-off? true
statement            URealValue.isUReal(): 24 rows, 24 measured, 24 agreed, 0 disagreed, 1 distinct
                     reference value(s) [NOT DISCRIMINATING: always BOOLEAN(true); acknowledged: ...]
--- header of the report the pass was granted under -------------
# op.URealValue.isUReal().distinctReferenceValues	1
# op.URealValue.isUReal().discriminating	false
# op.URealValue.isUReal().soleReferenceValue	BOOLEAN(true)
# accepted.degenerateOperations	0
=================================================================
```

`stage pass WITH the sign-off? true` and `# accepted.degenerateOperations 0` in the same run. The
header does not omit the question; it answers it **wrongly**, which is worse.

**AFTER** — `DifferentialHarnessRegressionTest.aReportCannotUnderstateItsOwnSignOffs`, which writes
the *same* sweep twice, once under the sign-off that granted its pass and once under `none()`:

```
=== D-34: the same sweep, two sign-off sets =======================
  signed:  # accepted.degenerateOperations	1
  signed:  # accepted.degenerateOperation	URealValue.add(value)|UREAL(2.0,0.0)@URealValue -> reviewed: a one-point domain, kept as a reachability check only; nothing here is evidence about the addition rule
  none:    # accepted.degenerateOperations	0
===================================================================
```

The count is right, the rationale travels with it verbatim, and the two headers are asserted
**unequal**, so a run with a sign-off in force can never be byte-indistinguishable from one without.

The fix is the *absence of a default*: `write` and `writeAll` both take the set and there is no
overload that omits it. The same test asserts that reflectively —

```java
for (Method m : DiffReportWriter.class.getMethods()) {
    if (!m.getName().equals("write") && !m.getName().equals("writeAll")) continue;
    assertTrue(List.of(m.getParameterTypes()).contains(AcceptedDegenerateOperations.class), ...);
}
```

— because "all five call sites pass it today" is a fact about today, and a sixth call site is one
line of typing. `harness-contract.md` §4.3's mitigation ("always call the 4-argument form") is
withdrawn: there is only one form.

---

## 3. D-35 — the standing invariant asserts both halves again

`0a93ad4f` introduced the DISCRIMINATING / NOT-DISCRIMINATING split, which is right: an operation
fully agreed while the reference gave one answer throughout is a finding about the **corpus**, not
about the subject. What it also did was **stop asserting the second bucket**, leaving it printed. The
commit before, it was asserted, as part of one undivided `fullyAgreedOperations()` set:

```
0a93ad4f^   assertEquals(subject.reviewedFullyAgreed, tally.fullyAgreedOperations().keySet(), ...)
0a93ad4f    assertEquals(subject.reviewedFullyAgreed, tally.discriminatingFullyAgreedOperations().keySet(), ...)
            // The other half of the split is printed, not asserted on here, and deliberately so.
```

### 3.1 What each version catches — measured, not argued

The experiment: on the tree at `90404528`, **otherwise unmodified**, add the missing assertion and
nothing else, then run `UnwrittenPortInvariantTest#anUnwrittenPortAgreesWithNothing`.

```
[ERROR] Tests run: 7, Failures: 1, Errors: 0, Skipped: 0
[ERROR]   UnwrittenPortInvariantTest.anUnwrittenPortAgreesWithNothing:158
          D-35 EXPERIMENT: the degenerate fully-agreed bucket, asserted
          ==> expected: <[]> but was: <[RealValue.value()]>
```

The same run that HEAD's own test **passes**. So, precisely:

| version | what it asserts | what it catches on that run |
|---|---|---|
| `0a93ad4f^` | one undivided set | `RealValue.value()`, but cannot say whether the finding is about the subject or the corpus |
| `0a93ad4f` (HEAD before this round) | the discriminating half only | **nothing** — `RealValue.value()` is printed and the test is green |
| this round | both halves, separately, labelled | `RealValue.value()`, *and* says which kind of finding it is |

`RealValue.value()` is an operation that a subject containing no code — one that only hands back its
receiver — was scored agreement on for **every driven row**, and the tree's standing invariant had
stopped looking at it. That is the door that had reopened.

### 3.2 What is asserted now

A second `assertEquals` against a new per-subject `reviewedDegenerateFullyAgreed` set, keeping the
split. The D-20 objection offered for dropping it does not apply: D-20 is about a test that asserts
the predicate it branched on, and this assertion does not — the branch is
`referenceValues().size() < 2`, and what is pinned is **which operations land there**, an extensional
fact about the historical code and the shipped corpora that no predicate in the file computes. All
fourteen buckets (seven subjects × two halves) are empty and asserted after the D-18 fix, which is
the strongest state this invariant has ever been in.

---

## 4. D-36 — the tree's own S1 acceptance test now gates properly

`UncertaintyDifferentialSmokeTest.smokeURealAdd` asserted `isClean()`, whose own Javadoc says it is
not a pass predicate and which is `true` for 119 of 285 operations against a subject of one hardcoded
literal each. The problem was never that the test was wrong about *this* sweep; it is that this test
is the worked example S4 copies.

It now gates the way `harness-contract.md` §4.3 says a stage must: one call to `requireStagePass`,
with the floor **written above the run** and derived from the corpus rather than read off the output,
plus the two checks the gate deliberately does not make.

```java
/** 22 boundary values + 6 random draws = 28, and every cell of the 28x28 product returns. */
private static final int ADD_FLOOR = 784;
...
result.requireStagePass(ADD_FLOOR, acknowledged);              // clauses 1-3, or it throws
assertEquals(0, result.throwClassMismatchCount(), result.summary());   // clause 5
assertTrue(result.isDiscriminating(), result.summary());
DiffReportWriter.assertMatchesGolden(report, "s1-smoke-ureal-add.tsv"); // clause 4
```

From the run:

```
report               .../use-core/target/differential/s1-smoke-ureal-add.tsv
golden (matched)     .../docs/port2/differential/s1-smoke-ureal-add.tsv
isClean()            true   <- measured, NOT the pass criterion (D-36)
stage gate failures  []
STAGE STATEMENT      URealValue.add(value): 784 rows, 784 measured, 784 agreed, 0 disagreed, 258 distinct reference value(s) [DISCRIMINATING]
```

`isClean()` is still **measured and printed beside the gate's verdict**, because the gap between the
two is exactly what D-36 is about; it is no longer what the test passes on.

The negative direction is gated by the same predicate, and asserts *which clause* refuses:

```
refused              sweep of URealValue.minus(value) is not a stage pass: - 226 row(s) did not
                     agree. tally: URealValue.minus(value): 784 rows, 784 measured, 389 distinct
                     ref, AGREE=558, DIFFER=226
```

And the class comment now states, in prose, the fifth figure the harness does not compute (D-30):
28 `UReal` receivers × the same 28 as arguments; the 22 boundary values, their uncertainties, and 6
seeded draws rounded to six decimals in [-100, 100]; **no value in (2, 100) other than those draws,
no denormal other than `MIN_VALUE`, no receiver at 42.** The last clause is not decoration — it is
the exact reason P10 is invisible.

---

## 5. Acceptance

**`mvn -q clean && mvn -B verify -Djava.awt.headless=true` — `BUILD SUCCESS`, twice.**

| module / phase | before (`90404528`) | after (`d13d4858`) | delta |
|---|---|---|---|
| use-core surefire | 71 | **76** | **+5** |
| use-gui surefire | 1 | 1 | 0 |
| use-core failsafe (`OCLExpressionIT`) | 1 | 1 | 0 |
| use-gui failsafe (`ShellIT`) | 129 | 129 | 0 |
| **surefire total** | **72** | **77** | **+5** |
| **failsafe total** | **130** | **130** | **0** |

Per class, from the run:

```
Tests run: 11 -- org.tzi.use.architecture.MavenCyclicDependenciesCoreTest      (unchanged)
Tests run:  6 -- Detection power: subtle infidelities in a ported U-type       (4 -> 6, +2)
Tests run:  6 -- Uncertainty differential smoke                                (unchanged)
Tests run: 10 -- Unwritten-port invariant                                      (unchanged)
Tests run:  9 -- HistoricalOracle class-loader isolation                       (unchanged)
Tests run: 33 -- Differential harness regressions                              (30 -> 33, +3)
Tests run:  1 -- org.tzi.use.uml.mm.ModelAPITest                               (unchanged)
Tests run: 76 -- use-core surefire total
Tests run:  1 -- org.tzi.use.OCLExpressionIT
Tests run:  1 -- org.tzi.use.architecture.MavenLayeredArchitectureTest
Tests run: 129 -- org.tzi.use.main.shell.ShellIT
```

**Every one of the +5 accounted for**, and all five are new assertions rather than renamings:

| new method | closes |
|---|---|
| `PortedInfidelityDetectionPowerTest.aWrongJavaTypeWithRightContentIsADivergence` | D-18, with its control |
| `PortedInfidelityDetectionPowerTest.noOperationAnswersWithTwoRuntimeClasses` | D-18's premise: no equivalent representations |
| `DifferentialHarnessRegressionTest.aReportCannotUnderstateItsOwnSignOffs` | D-34, including the reflective no-overload pin |
| `DifferentialHarnessRegressionTest.rightContentInTheWrongJavaTypeIsADifference` | D-18 at unit resolution, both shapes |
| `DifferentialHarnessRegressionTest.theTypeTokenIsPackageInsensitiveOnPurpose` | the cost of the simple-name token, stated |

D-35's and D-36's fixes are *stronger assertions inside existing methods*, so they add no method
count — which is why the counts above are not the whole evidence and §3.1 and §4 exist.

**No pre-existing test broken.** The nine methods that failed mid-change were all updated for the
new canonical form and are listed in the behaviour commit; five were literal expectations, two were
the goldens, one was a subject that had to be corrected rather than an expectation
(`aDegenerateOperationNeedsAWrittenSignOff`, §1.7), and one was `ECHO_SUBJECT_REVIEWED` emptying,
which is the fix landing.

**Determinism.** Two consecutive full `verify` runs; the 900-line evidence block covering the
seven-subject invariant sweep and the whole detection-power section is byte-identical:

```
evidence block lines: 900
sha256 run1: cca6d4fa3482f951a15eb5a1a739372f85a69e8427c3988445330aba4e79a72f  -
sha256 run2: cca6d4fa3482f951a15eb5a1a739372f85a69e8427c3988445330aba4e79a72f  -
BYTE-IDENTICAL across the two runs
goldens unchanged by a non-refresh run: 0
```

**Scope.** `git diff --name-status 30d480db..HEAD -- '*/src/main/*'` → empty. No pom, no
`module-info.java`, no `use-gui`, no `use-assembly`, no pre-existing upstream test touched.

*(A note on the brief's stated baseline: it says "68 surefire + 130 failsafe = 198". 68 was the count
before round 5 added four methods in `f438a365`. The independently confirmed baseline at `90404528`
is 72 surefire + 130 failsafe = 202, which is what the table above measures against.)*

---

## 6. Recorded, not fixed — and one guard assessed

Both items were re-measured in this round's runs rather than copied forward.

### 6.1 D-30 — a defect at an unreached input is invisible

`P10-narrow-input-window` plants P2's real arithmetic defect (uncertainties combined with `+` where
the historical uses `sqrt(ua²+ub²)`) restricted to receiver value `42.0`, which no shipped corpus
contains. Still, after this round's fix:

* **0 `DIFFER` rows**, 0 diverging operations;
* `assertEquals(control.stagePasses, blind.stagePasses)` — **stage-pass-identical to a perfect
  port**, including a full pass on `URealValue.add(value)`, the operation carrying the defect;
* published as `[DISCRIMINATING]` beside its agreement figure, which is *true* and says nothing
  about the input never tried.

Unchanged by anything in this round, and unchangeable by anything inside the instrument: a
differential oracle can only compare what it was asked to compare. It stays **MAJOR, open**, and it
is the reason every stage document must state its input domain in prose — which §4 now demonstrates
on the one test S4 will copy.

### 6.2 D-17 / D-32 — a subject can suppress its own measurement (P8, P9)

Two concealment routes, re-measured this round:

| probe | route | `DIFFER` rows | stage passes on the 4 affected operations |
|---|---|---|---|
| P8 | the wrong rows raised `HarnessMarshallingException` — exactly what `Candidate`'s Javadoc invites | **0** | **0** (clause 2 refuses `HARNESS_ERROR`) |
| P9 | `supports()` answers `false` for the operations carrying the defect | **0** | **0** (clause 2 refuses `UNSUPPORTED`) |

Pasted from this round's second `verify` run:

```
=== detection power: P8-hides-behind-harness-error ============================
verdict tally        {AGREE=16731, BOTH_THREW=910, HARNESS_ERROR=1351, UNMEASURABLE=91}
DETECTED on          0 operation(s): []
stage passes         70   (control 74)
    stage pass? false   (control true)     x4, one per ADDITIVE operation

=== detection power: P9-hides-behind-unsupported ==============================
verdict tally        {AGREE=15597, BOTH_THREW=910, HARNESS_ERROR=883, UNMEASURABLE=91, UNSUPPORTED=1602}
DETECTED on          0 operation(s): []
stage passes         70   (control 74)
    stage pass? false   (control true)     x4, one per ADDITIVE operation

=== detection power: P10-narrow-input-window ==================================
verdict tally        {AGREE=17199, BOTH_THREW=910, HARNESS_ERROR=883, UNMEASURABLE=91}
DETECTED on          0 operation(s): []
stage passes         74   (control 74)
    stage pass? true    (control true)     x4  <- the D-30 blind spot, in one line
```

The contrast between the two blocks is the whole of D-32: P8 and P9 lose exactly the four operations
they lie about (74 → 70), while P10 loses nothing (74 → 74). Concealment costs the passes;
unreachability does not.

Both destroy the divergence; **neither buys a pass**, and the row note still names the subject as the
side that could not be driven. What the attacks cost is **attribution**: the reader is told the
harness could not drive 143 rows rather than that the port is wrong on them.

**Is a cheap guard possible, and is it worth building?** For P9, yes and yes-in-principle: the
harness already calls `supports()` on both sides, so comparing them costs one boolean per operation,
and "the reference can be driven and the subject declares it cannot" is a strictly stronger statement
than today's undifferentiated `UNSUPPORTED` — it is the difference between "out of the instrument's
reach" and "the port says it has not got this one". I judge it **worth building, but as reporting and
not as a verdict**: it should split `UNSUPPORTED` into a subject-declined bucket with its own count in
the `# op.<key>.*` block, so a stage document cannot quote 143 undriven rows without saying whose
choice that was. It must **not** become a `DIFFER`, because a partially-implemented port during S4 is
a legitimate and expected state, and a guard that turns "not ported yet" into "wrong" would push S4
toward exactly the blanket sign-offs §7 of the contract warns about. For P8 no cheap guard exists at
all: `HarnessMarshallingException` from a subject is indistinguishable, by construction and by
design, from a real adapter limitation, and the only real defence is that it costs no stage pass —
which is measured, and is D-32. Both remain **open and recorded**; the register carries the numbers.

---

## 7. What this round did not touch

D-29 (the gate is not satisfiable by fidelity, 92 of 285), D-30, D-17/D-32, D-20, D-21's writer
guard, and the twelve open MINORs are unchanged. D-29 in particular is a *decision* for the human —
`foundation-verdict.md` H13 — and this round makes it slightly more visible rather than less: the
D-18 fix moves 29 operations out of the perfect-port pass set for a *boxing* port, which is more
evidence that S4 must diff the clause list against a recorded perfect-port baseline rather than read
a boolean.
