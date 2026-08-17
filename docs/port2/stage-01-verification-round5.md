# S1 — empirical verification, round 5

**Subject.** Commits `0a93ad4f` (behaviour + refreshed goldens) and `10b8b4c2` (documentation) on
`port-uncertainty-2`: FIX A–E against **D-15** (a single-point codomain scored as fidelity) and
**D-19** (`BooleanValue` / `StringValue` receivers with no corpus).

**Method.** Empirical. I own Maven. Every number below is pasted from a run of
`mvn -B verify -Djava.awt.headless=true` or of `mvn -B -pl use-core test -Dtest=…`, after
`mvn -q clean`. Nothing is estimated and nothing is quoted from a previous report without being
re-measured here.

**The question this round asks is different from rounds 1–4.** Those asked *can the harness claim
agreement it did not measure?* That class is closed and invariant-pinned. This round asks:

> **Can stages S4–S7 rely on this harness to detect a real infidelity in a ported U-type?**

That is a question about **power**, and it is answered by planting infidelities and counting.

**Verdict: `SOUND_WITH_DOCUMENTED_LIMITS`.**

Eleven probes (`P1`–`P11`) planted a defect in a port that is otherwise bit-for-bit the historical
implementation. Counted exactly:

* **Eight probes produced divergence** — `P1`–`P7` and `P11`. Seven of the eight were detected on
  **every** operation they touched; `P11` on three of its four. Each one cost the affected operations
  their stage pass wherever a perfect port had one.
* **Two probes (`P8`, `P9`) were concealment attacks** carrying `P2`'s real arithmetic defect behind
  `HarnessMarshallingException` (D-17) and behind `supports() == false`. Both destroyed the
  divergence — zero `DIFFER` rows — and **neither bought a stage pass**: the gate refused all four
  affected operations in both cases. Detection is lost; the verdict is not.
* **One probe (`P10`) was not seen at all**, and its operations are stage-pass-identical to a perfect
  port's. That is the finding this review exists to deliver, and it is §3.1.

The instrument has real detection power and it is not a rubber stamp.

The limits are three, all measured here for the first time, and the first two are new:

1. **A perfect port reaches a stage pass on 74 of 285 operations.** The gate is not satisfiable by
   fidelity alone: 92 operations are refused because rows *disagreed* against a perfect port
   (`BOTH_THREW` / `HARNESS_ERROR` / `UNMEASURABLE` are non-agreements), and clearing them requires
   **154** hand-authored `AcceptedThrowPairs` entries. New defect **D-29**.
2. **On 10 (defect, operation) pairs a real infidelity changes the rows and the counts but not the
   pass/fail bit**, because a perfect port already fails the gate there. A stage that reads the
   boolean and not the numbers is blind on exactly those. Part of **D-29**.
3. **Detection is bounded by the corpus, and nothing in the instrument states the bound.** A genuine
   arithmetic defect confined to a receiver value the corpora do not contain is invisible on all
   four operations that carry it, and those operations are reported as a full stage pass, byte for
   byte identical to a perfect port's. New defect **D-30**.

---

## 0. Summary table

| id | severity | where | one line |
|---|---|---|---|
| D-29 | MAJOR | `DifferentialSweep.Result.stageGateFailures` clause 2 | The stage gate is not satisfiable by fidelity: a **perfect** port passes 74 of 285 operations; 92 are refused for disagreements it cannot avoid, needing 154 hand-written `AcceptedThrowPairs` entries. On 10 measured (defect, operation) pairs an infidelity therefore leaves the pass bit unchanged. |
| D-30 | MAJOR | the instrument as a whole | Detection is bounded by the input corpus and no domain-coverage figure is published anywhere. Measured: a real defect on receiver value `42.0` is invisible and its operations are a full stage pass; a `-0.0 → 0.0` collapse is invisible on `URealValue.round()` while being caught on `floor()`, `neg()` and `mult(value)`. |
| D-31 | MINOR | `InputGenerator.indexBoundaries()` | The string-index family has almost no measured yield: against a perfect port `UStringValue.uSubstring(int,int)` is **17 AGREE of 432 rows** (391 `BOTH_THREW`), and `at(int)` / `uAt(int)` are 37 of 144. The index corpus was drawn for `at(int)` and is mostly out of range for the rest. |
| D-32 | MINOR | round-4 register, D-17 | D-17's practical reach is smaller than the register implies, and that is now measured rather than argued: an adapter that hides its wrong rows behind `HarnessMarshallingException` reaches **zero** stage passes on the affected operations and the row note still names the subject as the failing side. What it destroys is *attribution*, not the verdict. |

Confirmed fixed / not regressed, all re-measured in this round: D-19 (61 → 11 zero-measurement
operations), D-15 (119 clean-but-degenerate sweeps, 119 refused, 0 stage passes), D1, D2, D-10 (the
seven non-port subjects), the round-4 census (11 / 159 / 115).

---

## 1. Method — how a "realistic port with one subtle bug" was built

Hand-writing a near-faithful port of 285 operations to plant one bug in it would measure the quality
of my hand-written port, not the power of the instrument: every place my re-implementation drifted
would show up as detection I did not plant. So the subject is built the other way round.

A **second, independent `HistoricalOracle`** — its own `IsolatedJarClassLoader`, its own copy of the
vendored jars — plays the part of a **perfect port**. A `MutantPort` wrapper applies exactly one
named infidelity to exactly the operations that infidelity would touch; every other operation is
bit-for-bit the historical behaviour. That gives the experiment a control it would otherwise lack.

Domains are **stage-shaped** — each operation over its own receiver type's corpus, via
`UnwrittenPortInvariantTest.stageDomains` — which is what S4 will do and makes every number here
directly comparable with the D-15 census in that class.

New file:
`use-core/src/test/java/org/tzi/use/uncertainty/differential/PortedInfidelityDetectionPowerTest.java`
(4 tests). One two-word change to `UnwrittenPortInvariantTest`: `stageDomains` and `tuples` went from
`private static` to package-private `static` so the new test reuses them rather than reimplementing
them. Nothing else in the tree was touched.

### 1.1 The control

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
distinct throw-pairs a PERFECT port produces  154   <- AcceptedThrowPairs entries a human would have
to author, one per (operation, both classes, both messages), before clause 2 could ever be met on the
operations that throw
    e.g. UIntegerValue.divideBy(value) || reference threw java.lang.ArithmeticException: / by zero / subject threw java.lang.ArithmeticException: / by zero
    e.g. UIntegerValue.inverse() || reference threw java.lang.ArithmeticException: / by zero / subject threw java.lang.ArithmeticException: / by zero
    e.g. UIntegerValue.mod(value) || reference threw java.lang.ArithmeticException: / by zero / subject threw java.lang.ArithmeticException: / by zero
    e.g. UIntegerValue.power(value) || reference threw java.lang.RuntimeException: UInteger.power() : expected Real or Integer exponent value / subject threw java.lang.RuntimeException: UInteger.power() : expected Real or Integer exponent value
===================================================================
```

`diverging operations 0` is the precondition for everything that follows: two independently loaded
copies of the historical jars reproduce each other exactly, on all 19 083 rows, so every divergence
below is attributable to the planted defect and to nothing else.

The last four lines are **D-29** and are discussed in §4.

---

## 2. POWER — the primary question

Eleven probes. `P0` is the control. Each probe was swept over the whole 285-operation inventory, so
"did it disturb anything it does not target" is answered as well as "was it seen".

| probe | the planted infidelity | detected on | detecting rows | stage passes lost |
|---|---|---|---|---|
| P1 | 0-based port of the 1-based string index (`at`, `uAt`, `uSubstring`) | **3 of 3** | 138 | 0 (see §4) |
| P2 | uncertainties combined with `+` where historical uses `sqrt(ua²+ub²)` | **4 of 4** | 468 | 4 |
| P3 | `Math.hypot(ua,ub)` instead of `sqrt(ua*ua+ub*ub)` | **4 of 4** | 24 | 4 |
| P4 | `<=` where historical writes `<` (and `>=` for `>`) | **6 of 6** | 280 | 4 |
| P5 | results rounded to 10 decimal places | **7 of 7** | 428 | 7 |
| P6 | `uEquals` compares values and returns certainty 1.0 | **4 of 4** | 1119 | 2 |
| P7 | divide-by-zero answers `UndefinedValue` where historical throws | **6 of 6** | 167 | 3 |
| P8 | P2's defect, hidden behind `HarnessMarshallingException` (D-17) | 0 | 0 | 4 |
| P9 | P2's defect, hidden behind `supports() == false` | 0 | 0 | 4 |
| P10 | P2's defect, only for receiver value `42.0` | **0** | **0** | **0** |
| P11 | `-0.0` normalised to `0.0` | **3 of 4** | 59 | 3 |

### 2.1 Off-by-one in a string index (P1) — detected

The historical `UStringValue.at(int)` is 1-based; a 0-based port shifts by one.

```
=== detection power: P1-off-by-one-index ============================
verdict tally        {AGREE=17108, BOTH_THREW=863, DIFFER=52, HARNESS_ERROR=883, MIXED=86, UNMEASURABLE=91}
DETECTED on          3 operation(s): [UStringValue.at(int), UStringValue.uAt(int), UStringValue.uSubstring(int,int)]
  target UStringValue.at(int)
    control  {AGREE=37, BOTH_THREW=99, HARNESS_ERROR=8}
    mutant   {DIFFER=26, BOTH_THREW=84, MIXED=26, HARNESS_ERROR=8}
    statement UStringValue.at(int): 144 rows, 26 measured, 0 agreed, 144 disagreed, 14 distinct reference value(s) [DISCRIMINATING]
```

Every one of the 37 rows the control agreed on is gone: **0 agreed, 144 disagreed.** The evidence is
attributed on both sides:

```
18  UStringValue.at(int)  USTRING(" ",0.5) | INTEGER(0)  THROWN:java.lang.IndexOutOfBoundsException  STRING(" ")  MIXED  the reference threw and the subject returned. reference threw java.lang.IndexOutOfBoundsException: idx = 0 / subject returned STRING(" ")
19  UStringValue.at(int)  USTRING(" ",0.5) | INTEGER(1)  STRING(" ")  THROWN:java.lang.IndexOutOfBoundsException  MIXED  the subject threw and the reference returned. reference returned STRING(" ") / subject threw java.lang.IndexOutOfBoundsException: idx = 2
```

That is FIX D(a) working on live data: the lead clause names the side, in both directions, on the
same operation.

### 2.2 The uncertainty combination rule (P2) — detected, 468 rows

```
  target URealValue.add(value)
    control  {AGREE=576}
    mutant   {AGREE=433, DIFFER=143}
    statement URealValue.add(value): 576 rows, 576 measured, 433 agreed, 143 disagreed, 164 distinct reference value(s) [DISCRIMINATING]
    stage pass? false   (control true)
    refused: 143 row(s) did not agree.
```

25 % of the rows. Note that **433 rows still agree** — a stage reading an agreement *count* would see
a large healthy number. What it cannot see is a pass, and the refusal names the reason.

### 2.3 `hypot` for `sqrt` (P3) — detected, and this is the sharpest result in the round

`Math.hypot(a,b)` and `sqrt(a*a+b*b)` are the same formula. Only **24 rows of 1 602** separate them,
and this is what they look like:

```
134  UIntegerValue.add(value)  UINTEGER(7,0.25) | UINTEGER(689,0.19065)  UINTEGER(696,0.3144000993956586)  UINTEGER(696,0.31440009939565855)  DIFFER
 47  URealValue.add(value)     UREAL(0.0,1.0) | UREAL(28.230986,0.91554)  UREAL(28.230986,1.355807320971531)  UREAL(28.230986,1.3558073209715311)  DIFFER
```

A **one-unit-in-the-last-place** difference, at the seventeenth significant digit. The harness sees
it because `UValue.canonical()` renders through `Double.toString` and compares exactly — the property
its class comment claims, here demonstrated on a defect nobody would find by inspection. The brief
asked for "a rounding difference at the 10th decimal"; the instrument resolves seven digits finer
than that.

I predicted this detection would come from the NaN / Infinity boundary values and **I was wrong**;
see §5.

### 2.4 `<=` for `<` (P4) — detected on all six

54 of 225 rows on `UIntegerValue.lt(value)`, 66 of 576 on `URealValue.lt(value)`, 20 of 289 on
`UStringValue.lt(value)`, and the same on the three `gt`/`ge` counterparts. The divergence is wider
than the equality diagonal — these operations return a `UBooleanValue` carrying a probability, so
`le` and `lt` differ on more than the `a == b` rows — which is why I am quoting the measured counts
rather than a predicted one.

### 2.5 Rounding to 10 decimals (P5) — detected on all seven

```
3  URealValue.cos()  UREAL(1.0,0.0)  UREAL(0.5403023058681398,0.0)  UREAL(0.5403023059,0.0)  DIFFER
```

`URealValue.sin()` and `tan()` lose 19 of 24 rows; `divideBy(value)` loses 202 of 576. Seven stage
passes lost, the largest of any probe.

### 2.6 An `equals` that ignores the uncertainty component (P6) — detected, 1 119 rows

```
1  UBooleanValue.uEquals(value)  UBOOLEAN(true,0.0) | UBOOLEAN(true,0.5)  UBOOLEAN(true,0.5)  UBOOLEAN(true,1.0)  DIFFER
3  UBooleanValue.uEquals(value)  UBOOLEAN(true,0.0) | UBOOLEAN(false,0.0)  UBOOLEAN(true,0.0)  UBOOLEAN(false,1.0)  DIFFER
```

`URealValue.uEquals(value)` goes from 576 AGREE to **26 AGREE, 550 DIFFER**. The uncertainty
component is exactly what this operation is *for*, and a port that drops it is destroyed by the
harness.

**But note the reachability limit.** `equals(java.lang.Object)` is **not in the inventory at all** —
see §3.3. This probe caught `uEquals(Value)`; a port whose `equals(Object)` ignores uncertainty is
invisible.

### 2.7 Divide-by-zero returning `Undefined` (P7) — detected on all six

```
0  UIntegerValue.divideBy(value)  UINTEGER(0,0.0) | UINTEGER(0,0.0)  THROWN:java.lang.ArithmeticException  OPAQUE("org.tzi.use.uml.ocl.value.UndefinedValue|UndefinedValue{}")  MIXED  the reference threw and the subject returned. reference threw java.lang.ArithmeticException: / by zero / subject returned OPAQUE(...)
```

The throw/return boundary is where `MIXED` earns its place: 62 `MIXED` rows on the three operations
where the historical code throws on a zero divisor (`UIntegerValue.divideBy`, `mod`, `inverse` —
their control tallies carry `BOTH_THREW=30, 30, 2`), plus 105 `DIFFER` rows on the three where it
does **not** throw (`URealValue.divideBy` 72, `UIntegerValue.divideByR` 30, `URealValue.inverse` 3 —
their control tallies are pure `AGREE`, so the historical side returns a value there and the
subject's `UndefinedValue` is compared against it). Both shapes of the defect are caught, by two
different verdicts, with the reason in the note.

### 2.8 `-0.0` collapsed to `0.0` (P11) — detected on 3 of 4

```
2  URealValue.floor()      UREAL(-0.0,0.0)                  UREAL(-0.0,0.0)  UREAL(0.0,0.0)  DIFFER
2  URealValue.mult(value)  UREAL(0.0,0.0) | UREAL(-0.0,0.0)  UREAL(-0.0,0.0)  UREAL(0.0,0.0)  DIFFER
```

`floor()` 1 row, `neg()` 2 rows, `mult(value)` 56 rows. **`URealValue.round()` is not detected**, and
reaches a full stage pass — the historical `round()` never produces a negative zero over the shipped
`uReal` corpus, so there is no row on which the two implementations can be told apart. See §3.2.

### 2.9 The two concealment attacks (P8, P9) — both fail to buy a pass

**P8 is the D-17 attack, made concrete.** The port has P2's real arithmetic defect, and its adapter
raises `HarnessMarshallingException` — *precisely what `Candidate`'s Javadoc instructs an adapter
author to throw* — on exactly the rows where the defect would have shown.

```
=== detection power: P8-hides-behind-harness-error ============================
verdict tally        {AGREE=16731, BOTH_THREW=910, HARNESS_ERROR=1351, UNMEASURABLE=91}
DETECTED on          0 operation(s): []
  target URealValue.add(value)
    control  {AGREE=576}
    mutant   {AGREE=433, HARNESS_ERROR=143}
    statement URealValue.add(value): 576 rows, 433 measured, 433 agreed, 143 disagreed, 110 distinct reference value(s) [DISCRIMINATING]
    stage pass? false   (control true)
```

**Zero DIFFER rows. Zero detected operations.** And **zero stage passes** on all four affected
operations, because `HARNESS_ERROR` is a non-agreement and clause 2 refuses. The gate holds. What the
attack does destroy is *attribution*: the reader is told the harness could not drive 143 rows, not
that the port is wrong on them. The row note still names the side —
`subject … could not be driven: HarnessMarshallingException` — so the evidence is recoverable, but a
reader skimming a verdict tally would blame the instrument. That is **D-32**, and it is a smaller
claim than the round-4 register's.

**P9** is the coarser version: `supports()` answers false. All 1 602 rows become `UNSUPPORTED`,
`measured 0`, and the gate refuses on both the floor and the disagreement clause. Closed.

### 2.10 `isClean()` against `requireStagePass`, on the same defects

The porter and the round-5 static reviewer both note that nothing mechanically stops an S4 stage
using `isClean()`. Measured, that hole is **entirely about degeneracy and not about detection**:

```
=== isClean() against requireStagePass, on the same defects =======
  probe                        detected  isClean lost  gate lost  divergence with NO change in the pass bit
  P1-off-by-one-index               3             0          0          3
  P2-linear-uncertainty             4             4          4          0
  P3-hypot-uncertainty              4             4          4          0
  P4-le-for-lt                      6             4          4          2
  P5-round-10dp                     7             7          7          0
  P6-equals-ignores-uncertainty      4             2          2          2
  P7-undefined-on-zero-divisor      6             3          3          3
  P8-hides-behind-harness-error      0             4          4          0
  P9-hides-behind-unsupported       0             4          4          0
  P10-narrow-input-window           0             0          0          0
  P11-negative-zero-collapse        3             3          3          0
```

Columns 2 and 3 are **identical on every probe**. On genuine infidelities the two predicates have the
same power; the D-15 gate's whole contribution is to degenerate operations, which is exactly what
round 4 said it was for. Column 4 is D-29 and is discussed next.

---

## 3. What the harness CANNOT see — the most valuable output of this review

### 3.1 A real defect on an input the corpus does not reach (P10) — **the headline**

P10 is P2's defect — the wrong uncertainty combination rule — restricted to receivers whose value is
exactly `42.0`. `uRealBoundaries()` holds `0, ±0, ±1, ±0.5, 2, ±100, MIN_VALUE, MAX_VALUE, NaN` and
both infinities, and the two random draws are rounded to six decimal places in `[-100, 100]`, so no
shipped corpus contains it.

```
=== detection power: P10-narrow-input-window ============================
verdict tally        {AGREE=17199, BOTH_THREW=910, HARNESS_ERROR=883, UNMEASURABLE=91}
DETECTED on          0 operation(s): []
stage passes         74   (control 74)
isClean() operations 193   (control 193)   the older predicate loses 0: []
  target URealValue.add(value)
    control  {AGREE=576}
    mutant   {AGREE=576}
    statement URealValue.add(value): 576 rows, 576 measured, 576 agreed, 0 disagreed, 164 distinct reference value(s) [DISCRIMINATING]
    stage pass? true   (control true)
```

The mutant's tally is **byte-identical to the perfect port's**, on every one of the 19 083 rows. The
stage statement S4 is instructed to publish reads

> `URealValue.add(value): 576 rows, 576 measured, 576 agreed, 0 disagreed, 164 distinct reference value(s) [DISCRIMINATING]`

for a port that computes the wrong answer. This is **not a bug in `DifferentialSweep`** — a
differential oracle can only compare what it was asked to compare — but it is the boundary of what a
fidelity claim from this instrument means, and **nothing in the instrument states that boundary.**

The asymmetry is the point. FIX A made the *codomain* a published, enforced quantity:
`distinctReferenceValues()` answers "could the reference have said anything else?". Its dual — "how
much of the input domain did we reach?" — is computed nowhere, published nowhere and gated nowhere.
`[DISCRIMINATING]` on the line above is *true*: the reference gave 164 different answers. It is also
completely uninformative about the 42.0 the sweep never tried. That is **D-30**.

The test asserts this blindness rather than describing it, so it cannot move silently:

```java
assertEquals(control.stagePasses, blind.stagePasses,
        "a port with a real arithmetic defect on an unreached input is stage-pass-identical "
                + "to a perfect one; that is the claim this test exists to make explicit");
```

### 3.2 The same finding in miniature: `URealValue.round()`

```
=== planted defects the harness did NOT see =======================
  ??? P11-negative-zero-collapse / URealValue.round()  [STAGE PASS]
===================================================================
```

This set is asserted **exactly**, so it cannot grow or shrink without a reader being told. Today it
has one member. The defect is real and is caught on `floor()`, `neg()` and `mult(value)`; on
`round()` there is no input in the shipped `uReal` corpus that makes the historical implementation
produce a negative zero, so the two are indistinguishable. Same cause as P10, different scale.

### 3.3 The inventory boundary: 33 public operations are not nameable at all

`reachableOperations` enumerates public instance methods whose every parameter is a `Value`, `int`,
`double` or `float`. Everything else is **not in the 285** — no row, no verdict, not even an
`UNSUPPORTED` marker.

```
=== the inventory boundary ========================================
public instance methods on the 8 marshallable receivers  318
expressible as a UOp (before de-duplication)             285
NOT nameable, therefore absent from every report         33
  BooleanValue
      compareTo[class java.lang.Object]
      equals[class java.lang.Object]
      toStringWithType[class java.lang.StringBuilder]
      toString[class java.lang.StringBuilder]
  IntegerValue
      compareTo[class java.lang.Object]
      equals[class java.lang.Object]
      toStringWithType[class java.lang.StringBuilder]
      toString[class java.lang.StringBuilder]
  RealValue
      compareTo[class java.lang.Object]
      equals[class java.lang.Object]
      toStringWithType[class java.lang.StringBuilder]
      toString[class java.lang.StringBuilder]
  StringValue
      compareTo[class java.lang.Object]
      equals[class java.lang.Object]
      toStringWithType[class java.lang.StringBuilder]
      toString[class java.lang.StringBuilder]
  UBooleanValue
      compareTo[class java.lang.Object]
      equals[class java.lang.Object]
      toStringWithType[class java.lang.StringBuilder]
      toString[class java.lang.StringBuilder]
  UIntegerValue
      compareTo[class java.lang.Object]
      equals[class java.lang.Object]
      toStringWithType[class java.lang.StringBuilder]
      toString[class java.lang.StringBuilder]
  URealValue
      compareTo[class java.lang.Object]
      equals[class java.lang.Object]
      toStringWithType[class java.lang.StringBuilder]
      toString[class java.lang.StringBuilder]
  UStringValue
      compareTo[class java.lang.Object]
      equals[class java.lang.Object]
      indexOf[class org.tzi.use.uml.ocl.value.StringValue]
      toStringWithType[class java.lang.StringBuilder]
      toString[class java.lang.StringBuilder]
distinct UOp keys in the inventory                       285
===================================================================
```

Consequences for S4, stated plainly:

* **`equals(Object)` is invisible on all eight receivers.** A port whose `equals` ignores the
  uncertainty component — one of the infidelities this round was asked to plant — cannot be detected
  by this harness through `equals`. P6 caught the same mistake through `uEquals(Value)` only because
  that method happens to take a `Value`.
* **`compareTo(Object)` is invisible** on all eight; `compareTo(Value)` is in the inventory, and the
  two are different methods with different bodies.
* **`UStringValue.indexOf(StringValue)` is invisible.**
* `toString(StringBuilder)` / `toStringWithType(StringBuilder)` are invisible — 16 of the 33.

"285 operations" is therefore not "the ported surface"; it is "the ported surface this harness can
name". The test pins the two most consequential absences by assertion.

### 3.4 Post-state, and D-18 — unchanged, restated

Two limits from earlier rounds were re-confirmed and not re-litigated:

* **Post-state is unobserved.** The 8 `setTypeToRuntimeType()` mutators measure nothing at all (§6),
  so a port that makes one of them a no-op is undetectable. This is a declared limit of
  `HistoricalOracle` and its rows are `UNMEASURABLE`, i.e. never an agreement — the port cannot be
  reported as faithful on them either.
* **D-18** (a port returning the right content with the wrong Java type) is untouched by this round
  and remains open at the 193-of-285 figure. It is a property of the canonical vocabulary, not
  something a mutation experiment can add to.

---

## 4. D-29 — the stage gate is not satisfiable by fidelity

This is the finding I did not expect and it comes straight out of the control.

```
stage passes         74 of 285  (isStagePass(1, none()))
why a PERFECT port is refused elsewhere:
    0 PASS   74
    2 refused: rows disagreed   41
    3 refused on more than one clause   51
    4 refused: not discriminating (D-15)   119
distinct throw-pairs a PERFECT port produces  154
```

A port that *is* the historical implementation reaches a stage pass on **74 of 285 operations
(26 %)**. Of the remaining 211:

* **119** are refused by clause 3 alone — D-15, as designed, requiring an
  `AcceptedDegenerateOperations` sign-off each.
* **92** (41 + 51) are refused by clause 2, *"N row(s) did not agree"*, **against a perfect port**.
  Those rows are `BOTH_THREW` (the historical code throws and so does the faithful port),
  `HARNESS_ERROR` (a cross-type receiver the instrument cannot marshal) and `UNMEASURABLE` (a void
  operation). None of them is a fidelity failure. The only route out is `AcceptedThrowPairs`, and
  `AcceptedThrowPairs` keys on **both messages verbatim** — its own Javadoc says "a message that
  varies with the input needs one entry per input… That is deliberate friction." Measured, that
  friction is **154 distinct entries**, e.g.

  ```
  UIntegerValue.divideBy(value) || reference threw java.lang.ArithmeticException: / by zero / subject threw java.lang.ArithmeticException: / by zero
  UIntegerValue.power(value) || reference threw java.lang.RuntimeException: UInteger.power() : expected Real or Integer exponent value / subject threw java.lang.RuntimeException: UInteger.power() : expected Real or Integer exponent value
  ```

So declaring the full surface passing would take on the order of **273 hand-authored sign-off
entries** (154 throw-pairs + 119 degenerate operations) before a single line of the port is in
question. That number has never been stated. It matters for two reasons.

**First, it creates exactly the pressure that produces D-11-shaped mistakes.** The contract tells S4
to use `requireStagePass`; `requireStagePass` refuses 74 % of the surface for a perfect port; the
un-deprecated, still-public `isClean()` says yes to 193 of 285 for the same port. A stage under
schedule pressure will find the second number. The porter disclosed the opt-in property and the
static reviewer filed it as R5-4; what neither states is *how strong the incentive is*, and the
incentive is a 119-operation gap.

**Second, and this is the part that is a real hole rather than an ergonomic complaint:** on the 92
operations a perfect port already fails, an infidelity produces **no change in the pass/fail bit**.
Measured, 10 (defect, operation) pairs:

```
  operations where a real infidelity leaves the pass bit unchanged, because a PERFECT port already
  fails the gate there:
    !!! P1-off-by-one-index / UStringValue.at(int)
    !!! P1-off-by-one-index / UStringValue.uAt(int)
    !!! P1-off-by-one-index / UStringValue.uSubstring(int,int)
    !!! P4-le-for-lt / UStringValue.gt(value)
    !!! P4-le-for-lt / UStringValue.lt(value)
    !!! P6-equals-ignores-uncertainty / UBooleanValue.uEquals(value)
    !!! P6-equals-ignores-uncertainty / UStringValue.uEquals(value)
    !!! P7-undefined-on-zero-divisor / UIntegerValue.divideBy(value)
    !!! P7-undefined-on-zero-divisor / UIntegerValue.inverse()
    !!! P7-undefined-on-zero-divisor / UIntegerValue.mod(value)
```

The whole off-by-one probe is in that list. Its rows are all there — `at(int)` goes from
`{AGREE=37, BOTH_THREW=99}` to `{DIFFER=26, MIXED=26, BOTH_THREW=84}` — and its stage verdict is
`false` before and `false` after. **A stage that automates on the boolean and reads the counts by
eye will not see the most classic port bug there is on the three operations that carry it.** The
evidence is in the report; the gate does not change state.

This is not a scoring bug and clause 2 is not wrong: those rows genuinely are non-agreements. It is
the same species as every previous round — an artefact whose headline reads stronger than the
measurement behind it — and its fix is a per-clause delta rather than a boolean, or a
`stageGateFailures` comparison against a recorded baseline. I have not attempted a fix; I am the
refuter.

---

## 5. Discriminating power — verified, including by a route that does not read a row

### 5.1 Recomputed by hand

The metric was recomputed without looking at a single `DiffRow`: drive the oracle directly over the
same domain, collect canonical forms into a fresh `TreeSet`, compare with `Result.referenceValues()`.
`URealValue.neg()` was chosen because its domain is one corpus of 24 receivers, small enough to print
and count.

```
=== the metric, recomputed by hand ================================
operation            URealValue.neg()
receivers            24  (the URealValue corpus)
rows                 24
    UREAL(0.0,0.0)  ->  UREAL(-0.0,0.0)
    UREAL(0.0,1.0)  ->  UREAL(-0.0,1.0)
    UREAL(-0.0,0.0)  ->  UREAL(0.0,0.0)
    UREAL(1.0,0.0)  ->  UREAL(-1.0,0.0)
    UREAL(1.0,1.0)  ->  UREAL(-1.0,1.0)
    UREAL(-1.0,0.0)  ->  UREAL(1.0,0.0)
    UREAL(-1.0,1.0)  ->  UREAL(1.0,1.0)
    UREAL(-1.0,0.5)  ->  UREAL(1.0,0.5)
    UREAL(0.5,0.5)  ->  UREAL(-0.5,0.5)
    UREAL(-0.5,0.25)  ->  UREAL(0.5,0.25)
    UREAL(2.0,0.0)  ->  UREAL(-2.0,0.0)
    UREAL(100.0,0.001)  ->  UREAL(-100.0,0.001)
    UREAL(-100.0,0.001)  ->  UREAL(100.0,0.001)
    UREAL(4.9E-324,0.0)  ->  UREAL(-4.9E-324,0.0)
    UREAL(1.7976931348623157E308,0.0)  ->  UREAL(-1.7976931348623157E308,0.0)
    UREAL(-1.7976931348623157E308,0.0)  ->  UREAL(1.7976931348623157E308,0.0)
    UREAL(NaN,0.0)  ->  UREAL(NaN,0.0)
    UREAL(Infinity,0.0)  ->  UREAL(-Infinity,0.0)
    UREAL(-Infinity,0.0)  ->  UREAL(Infinity,0.0)
    UREAL(1.0,NaN)  ->  UREAL(-1.0,NaN)
    UREAL(1.0,Infinity)  ->  UREAL(-1.0,Infinity)
    UREAL(1.0,-1.0)  ->  UREAL(-1.0,1.0)
    UREAL(-46.064505,0.782649)  ->  UREAL(46.064505,0.782649)
    UREAL(28.230986,0.91554)  ->  UREAL(-28.230986,0.91554)
by hand              23  [UREAL(-0.0,0.0), UREAL(-0.0,1.0), UREAL(-0.5,0.5), UREAL(-1.0,0.0), UREAL(-1.0,1.0), UREAL(-1.0,Infinity), UREAL(-1.0,NaN), UREAL(-1.7976931348623157E308,0.0), UREAL(-100.0,0.001), UREAL(-2.0,0.0), UREAL(-28.230986,0.91554), UREAL(-4.9E-324,0.0), UREAL(-Infinity,0.0), UREAL(0.0,0.0), UREAL(0.5,0.25), UREAL(1.0,0.0), UREAL(1.0,0.5), UREAL(1.0,1.0), UREAL(1.7976931348623157E308,0.0), UREAL(100.0,0.001), UREAL(46.064505,0.782649), UREAL(Infinity,0.0), UREAL(NaN,0.0)]
Result.referenceValues() 23  [UREAL(-0.0,0.0), UREAL(-0.0,1.0), UREAL(-0.5,0.5), UREAL(-1.0,0.0), UREAL(-1.0,1.0), UREAL(-1.0,Infinity), UREAL(-1.0,NaN), UREAL(-1.7976931348623157E308,0.0), UREAL(-100.0,0.001), UREAL(-2.0,0.0), UREAL(-28.230986,0.91554), UREAL(-4.9E-324,0.0), UREAL(-Infinity,0.0), UREAL(0.0,0.0), UREAL(0.5,0.25), UREAL(1.0,0.0), UREAL(1.0,0.5), UREAL(1.0,1.0), UREAL(1.7976931348623157E308,0.0), UREAL(100.0,0.001), UREAL(46.064505,0.782649), UREAL(Infinity,0.0), UREAL(NaN,0.0)]
summary              URealValue.neg(): 24 rows, 24 measured, 23 distinct ref, AGREE=24
===================================================================
```

24 rows in, 23 distinct out, and the duplicate is visible in the trace: **`UREAL(-1.0,1.0)` is
produced twice** — once from `UREAL(1.0,1.0)` and once from `UREAL(1.0,-1.0)`. The second line is
itself worth noting: the historical `neg()` maps `(1.0, -1.0)` to `(-1.0, +1.0)`, i.e. it does not
carry a negative uncertainty through unchanged, whereas it does carry `NaN` and `Infinity` through
unchanged. That is a real behaviour of the code being ported, sitting in an evidence file because the
enumeration was printed rather than summarised.

`by hand` and `Result.referenceValues()` are the same 23-element set, computed by two routes that
share no code: mine calls `HistoricalOracle.invoke` directly and never constructs a `DiffRow`; the
harness's reads the `historical` column of the rows it classified as measurements. The metric is
correct on the operation I checked.

*(A note on process: I drafted this section before the run finished and wrote a plausible trace into
it from memory of the corpus. The run then printed a different random draw — `-46.064505`, not the
`-56.914806` I had written — and a different image for `UREAL(1.0,-1.0)`. The fabricated block was
replaced with the pasted one above. I record the slip because a review whose own evidence is
reconstructed is worth nothing, and because it is exactly the failure mode this whole document exists
to catch in others.)*

### 5.2 The gate refuses a single-valued operation, and cannot be talked round

Re-measured from `mvn -B verify`, unchanged from the porter's report:

```
=== D-15: the constant-literal subject, stage-shaped ==============
seed                       20260817
corpora                    uReal=24, uInteger=15, uBoolean=11, uString=18, boolean=4, string=16, zeroDivisors=7, indexBoundaries=8; receivers=94
operations                 285
literals the subject holds 274  (one per operation the reference ever answered with a value)
codomain census            285 operations: 11 measured nothing, 159 single-valued (NOT DISCRIMINATING), 115 discriminating
isClean() AND degenerate   119   <- the size of the door: a stage asserting isClean() reads these as PASS
refused by the stage gate  119 of 119
stage passes (must be 0)   0
```

I tried to get a clean pass out of a single-valued operation without the written acknowledgement:

* **`AcceptedDegenerateOperations.none()`** — refused, 119 of 119, with the D-15 text.
* **A sign-off with the wrong value or the wrong operation key** — refused
  (`aDegenerateOperationNeedsAWrittenSignOff`, both directions).
* **A blank rationale** — `IllegalArgumentException`.
* **A measurement floor of 0** — `IllegalArgumentException`, "a floor of zero is not a floor".
* **Driving `referenceValues()` from the subject side** — impossible: it reads `row.historical()`,
  and my mutant controls only the ported column. Confirmed on all eleven probes, where the control's
  and every mutant's `distinct reference value(s)` are identical for every operation.
* **`isClean()`** — **succeeds**, on 193 of 285 operations for a perfect port and on 119 degenerate
  ones for a port of hardcoded literals. That is the known opt-in hole (D-11 / R5-4). Measured
  contribution: see §2.10 — it is a hole for degeneracy only, not for detection.

---

## 6. Corpus widening — confirmed

Re-measured from `mvn -B verify`, not quoted:

| quantity | round 4 | this round |
|---|---|---|
| operations that measure nothing (D-19) | 61 | **11** |
| single-valued (D-15 population) | 121 | **159** |
| discriminating | 103 | **115** |
| clean-but-degenerate against the literal subject | 81 | **119** |
| of those, refused by the gate | 81 of 81 | **119 of 119** |
| stage passes reached by a port with no logic | 0 | **0** |

The 52 zero-driven-row operations are gone: all 27 `BooleanValue.*` and all 25 `StringValue.*`
operations now measure. The 11 that remain are declared limits, pasted verbatim:

```
--- operations that measured NOTHING (11) -------------------------
  ... BooleanValue.setTypeToRuntimeType()  BooleanValue.setTypeToRuntimeType(): 2 rows, 0 measured, 0 distinct ref, MIXED=2
  ... IntegerValue.setTypeToRuntimeType()  IntegerValue.setTypeToRuntimeType(): 8 rows, 0 measured, 0 distinct ref, MIXED=8
  ... RealValue.setTypeToRuntimeType()  RealValue.setTypeToRuntimeType(): 1 rows, 0 measured, 0 distinct ref, MIXED=1
  ... StringValue.setTypeToRuntimeType()  StringValue.setTypeToRuntimeType(): 15 rows, 0 measured, 0 distinct ref, MIXED=15
  ... UBooleanValue.setTypeToRuntimeType()  UBooleanValue.setTypeToRuntimeType(): 11 rows, 0 measured, 0 distinct ref, MIXED=9, HARNESS_ERROR=2
  ... UIntegerValue.power(value)  UIntegerValue.power(value): 225 rows, 0 measured, 0 distinct ref, BOTH_THREW=225
  ... UIntegerValue.setTypeToRuntimeType()  UIntegerValue.setTypeToRuntimeType(): 15 rows, 0 measured, 0 distinct ref, MIXED=15
  ... URealValue.setTypeToRuntimeType()  URealValue.setTypeToRuntimeType(): 24 rows, 0 measured, 0 distinct ref, MIXED=24
  ... UStringValue.setTypeToRuntimeType()  UStringValue.setTypeToRuntimeType(): 18 rows, 0 measured, 0 distinct ref, MIXED=17, HARNESS_ERROR=1
  ... UStringValue.toInteger()  UStringValue.toInteger(): 18 rows, 0 measured, 0 distinct ref, BOTH_THREW=17, HARNESS_ERROR=1, throwClassMismatch=17
  ... UStringValue.toReal()  UStringValue.toReal(): 18 rows, 0 measured, 0 distinct ref, BOTH_THREW=17, HARNESS_ERROR=1, throwClassMismatch=17
```

8 declared-void mutators, plus three operations that throw on every input the corpora hold. Limits,
not gaps — confirmed.

**D-31, a residual coverage finding the widening did not touch.** Against a *perfect* port:

```
  UStringValue.uSubstring(int,int)   control {AGREE=17, BOTH_THREW=391, HARNESS_ERROR=24}
  UStringValue.at(int)              control {AGREE=37, BOTH_THREW=99, HARNESS_ERROR=8}
```

`indexBoundaries()` was drawn for `at(int)` — `MIN_VALUE, -1, 0, 1, 2, 3, 4, MAX_VALUE` — and is
almost entirely out of range for two-index substring extraction. 17 measured rows of 432 is a thin
basis for a fidelity claim on that operation, and it is why P1 costs it no stage pass (§4). The same
species as D-19 and D-28: the corpus, not the code.

### 6.1 Corpus sensitivity — and a prediction of mine that was wrong

Detection is a joint fact about the implementations and the inputs. Removing the non-finite boundary
values (NaN and both infinities, in the value and the uncertainty position) and re-running everything:

```
=== corpus sensitivity ============================================
full corpora         uReal=24, uInteger=15, uBoolean=11, uString=18, boolean=4, string=16, zeroDivisors=7, indexBoundaries=8
finite-only corpora  uReal=19, uInteger=14, uBoolean=10, uString=17, boolean=4, string=16, zeroDivisors=7, indexBoundaries=8
probe                          detecting rows   ops detected
                               full  finite     full  finite
  P0-perfect                       0      0         0      0
  P1-off-by-one-index            138    128         3      3
  P2-linear-uncertainty          468    456         4      4
  P3-hypot-uncertainty            24     20         4      4
  P4-le-for-lt                   280    274         6      6
  P5-round-10dp                  428    374         7      7
  P6-equals-ignores-uncertainty  1119    828         4      4
  P7-undefined-on-zero-divisor   167    146         6      6
  P8-hides-behind-harness-error     0      0         0      0
  P9-hides-behind-unsupported      0      0         0      0
  P10-narrow-input-window          0      0         0      0
  P11-negative-zero-collapse      59     55         3      3
===================================================================
```

**I predicted P3 would go to zero and it did not.** I expected `hypot` versus `sqrt` to be separable
only through NaN and Infinity; in fact 20 of its 24 detecting rows survive, because the two functions
differ by one ulp on ordinary finite inputs and `UValue.canonical()` compares `Double.toString`
output exactly. The exactness the harness documents is doing the work, not the boundary corpus. The
prediction is recorded here because a review that only prints its confirmed guesses is not a
measurement. No probe loses an operation, and none loses more than 26 % of its rows: **detection on
these defects is robust to thinning the corpus, and P10 shows it is not robust to a defect the corpus
never reaches.** Those two statements are not in tension — the first is about redundancy within a
reached region, the second about regions that are not reached at all.

---

## 7. No regression of rounds 1–3 — the seven non-port subjects

Pasted verbatim from the final `mvn -B verify` (`grep` of the seven per-subject blocks; only the
constant per-subject header lines `seed`, `observability`, `operations`, `corpora`, `codomain census`
are omitted, and they are identical for all seven):

```
subject              a-throws  (every method body: throw new RuntimeException("TODO: port " + op.key()))
rows                 726338
measured rows        0  (AGREE + DIFFER)
agreement rows       0
verdict tally        {BOTH_THREW=39880, HARNESS_ERROR=618462, MIXED=67996}
fully agreed ops, DISCRIMINATING (a finding about the subject)  (none)
fully agreed ops, NOT DISCRIMINATING (a finding about the corpus)  (none)
subject              b-returns-java-null  (every method body: return null)
rows                 726338
measured rows        0  (AGREE + DIFFER)
agreement rows       0
verdict tally        {HARNESS_ERROR=726338}
fully agreed ops, DISCRIMINATING (a finding about the subject)  (none)
fully agreed ops, NOT DISCRIMINATING (a finding about the corpus)  (none)
subject              c-empty-body  (every method body: { } -- i.e. return UValue.voidValue())
rows                 726338
measured rows        67268  (AGREE + DIFFER)
agreement rows       0
verdict tally        {DIFFER=67268, HARNESS_ERROR=618462, MIXED=39880, UNMEASURABLE=728}
fully agreed ops, DISCRIMINATING (a finding about the subject)  (none)
fully agreed ops, NOT DISCRIMINATING (a finding about the corpus)  (none)
subject              d-returns-null-value  (every method body: return UValue.nullValue())
rows                 726338
measured rows        67268  (AGREE + DIFFER)
agreement rows       0
verdict tally        {DIFFER=67268, HARNESS_ERROR=618462, MIXED=39880, UNMEASURABLE=728}
fully agreed ops, DISCRIMINATING (a finding about the subject)  (none)
fully agreed ops, NOT DISCRIMINATING (a finding about the corpus)  (none)
subject              e-fixed-constant  (every method body: return UValue.uBoolean(true, 1.0))
rows                 726338
measured rows        67996  (AGREE + DIFFER)
agreement rows       8240
verdict tally        {AGREE=8240, DIFFER=59756, HARNESS_ERROR=618462, MIXED=39880}
fully agreed ops, DISCRIMINATING (a finding about the subject)  (none)
fully agreed ops, NOT DISCRIMINATING (a finding about the corpus)  (none)
subject              f-echoes-receiver  (every method body: return args.get(0))
rows                 726338
measured rows        67996  (AGREE + DIFFER)
agreement rows       4951
verdict tally        {AGREE=4951, DIFFER=63045, HARNESS_ERROR=618462, MIXED=39880}
fully agreed ops, DISCRIMINATING (a finding about the subject)  
  *** BooleanValue.isTrue()  (16/16 driven rows agreed, 752 rows total, 2 distinct reference value(s); reviewed and signed off)
  *** BooleanValue.value()  (16/16 driven rows agreed, 752 rows total, 2 distinct reference value(s); reviewed and signed off)
  *** IntegerValue.value()  (64/64 driven rows agreed, 752 rows total, 8 distinct reference value(s); reviewed and signed off)
  *** StringValue.value()  (120/120 driven rows agreed, 752 rows total, 15 distinct reference value(s); reviewed and signed off)
fully agreed ops, NOT DISCRIMINATING (a finding about the corpus)  1 operations
subject              g-throws-error  (every method body: throw new AssertionError("TODO: port " + op.key()))
rows                 0
measured rows        0  (AGREE + DIFFER)
agreement rows       0
verdict tally        {}
ESCAPED              java.lang.AssertionError: TODO: port BooleanValue.compareTo(value)  -> the sweep ABORTED; rows above are only those completed before it
fully agreed ops, DISCRIMINATING (a finding about the subject)  (none)
fully agreed ops, NOT DISCRIMINATING (a finding about the corpus)  (none)
```

**a, b, c, d, g: zero agreement rows.** D1, D2 and D-10 are all still closed — subject c keeps its
67 268 `DIFFER` rows and its 728 `UNMEASURABLE` void rows. Subjects e and f produce agreement, which
is the correct verdict for a subject that returns wrong *values*, and neither has a discriminating
fully-agreed operation outside the four reviewed ones.

**One corroboration of the round-5 static review's R5-3, measured.** That review argues the standing
invariant now asserts less than it did, because the degenerate half of the fully-agreed split is
printed and not asserted, and says it could not quantify the unasserted bucket without running the
suite. Run: it is **1 operation** for subject `f` (`RealValue.value()`, per §11.6 of `stage-01.md`)
and **0** for subject `e`. The exposure is one operation today. The mechanism the reviewer objects to
is real; its current occupancy is small.

---

## 8. Acceptance

`mvn -q clean` immediately before. Final run against the committed branch state:

```
$ mvn -q clean && mvn -B verify -Djava.awt.headless=true
…
[INFO] Tests run: 11, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 3.018 s -- in org.tzi.use.architecture.MavenCyclicDependenciesCoreTest
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 3.656 s -- in Detection power: subtle infidelities in a ported U-type
[INFO] Tests run: 6, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.063 s -- in Uncertainty differential smoke
[INFO] Tests run: 10, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 42.17 s -- in Unwritten-port invariant
[INFO] Tests run: 9, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.039 s -- in HistoricalOracle class-loader isolation
[INFO] Tests run: 30, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.224 s -- in Differential harness regressions
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.173 s -- in org.tzi.use.uml.mm.ModelAPITest
[INFO] Tests run: 71, Failures: 0, Errors: 0, Skipped: 0
…
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.175 s - in org.tzi.use.OCLExpressionIT
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
…
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 3.795 s -- in org.tzi.use.architecture.MavenLayeredArchitectureTest
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
…
[INFO] Tests run: 129, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 7.443 s - in org.tzi.use.main.shell.ShellIT
[INFO] Tests run: 129, Failures: 0, Errors: 0, Skipped: 0
…
[INFO] BUILD SUCCESS
```

Both phases: surefire **and** failsafe. Four full `mvn -q clean && mvn -B verify` runs were made in
the course of this review (one baseline before any change, two for determinism, one final); all four
were `BUILD SUCCESS`.

### 8.1 Test counts and deltas

| module / phase | baseline (`10b8b4c2`) | this round | delta |
|---|---|---|---|
| use-core surefire | 67 | 71 | **+4** — the four tests of `PortedInfidelityDetectionPowerTest` |
| use-core failsafe (`OCLExpressionIT`) | 1 | 1 | — |
| use-gui surefire (`MavenLayeredArchitectureTest`) | 1 | 1 | — |
| use-gui failsafe (`ShellIT`) | 129 | 129 | — |

Every pre-existing test is unchanged and green. `Unwritten-port invariant` still runs 10 tests;
`Differential harness regressions` still runs 30; `HistoricalOracle class-loader isolation` still
runs 9; `Uncertainty differential smoke` still runs 6.

### 8.2 Scope

```
$ git diff --name-status 30d480db..HEAD -- '*/src/main/*'
(empty)
```

No `pom.xml`, no `module-info.java`, no `use-gui`, no `use-assembly`, no pre-existing upstream test
was modified. The only change to an existing file is two occurrences of `private static` →
`static` in `UnwrittenPortInvariantTest` (visibility of `stageDomains` and `tuples`), so that the new
test reuses the stage-shaped domain builder instead of writing a second one — a duplicate
implementation of the domain shape is exactly how two numbers that should be identical come to
differ.

### 8.3 Determinism

Two consecutive `mvn -q clean && mvn -B verify` runs, with the detection-power and invariant output
blocks extracted and diffed:

```
$ diff verify-r5b.diffblock verify-r5c.diffblock && echo IDENTICAL
IDENTICAL (557 lines)
$ diff verify-r5b.invblock verify-r5c.invblock && echo IDENTICAL
IDENTICAL (324 lines)
```

`git status --short` shows no modification to `docs/port2/differential/*.tsv` after any run: the
committed goldens were reproduced byte for byte by `assertMatchesGolden`, three times, without the
refresh flag.

### 8.4 Tree state

Clean apart from this round's two files. `docs/port2/stage-01-static-review-round5.md` was present
and untracked in the working tree when I started and is **not mine** — it is the concurrent static
review — and I have not committed it.

---

## 9. Register entries proposed for `stage-01.md` §11.2

| id | severity | state | one line |
|---|---|---|---|
| D-29 | MAJOR | **open, new** | The stage gate is not satisfiable by fidelity: a perfect port passes 74 of 285; 92 operations are refused by clause 2 for `BOTH_THREW` / `HARNESS_ERROR` / `UNMEASURABLE` rows a faithful port cannot avoid, needing 154 hand-authored `AcceptedThrowPairs` entries. Consequence: on 10 measured (defect, operation) pairs a real infidelity leaves the pass bit unchanged, including the whole off-by-one probe. |
| D-30 | MAJOR | **open, new** | Detection is bounded by the input corpus and no domain-coverage figure exists. `distinctReferenceValues()` measures the codomain; its dual is unmeasured. Demonstrated: a port with the wrong uncertainty rule at receiver value `42.0` is stage-pass-identical to a perfect port on all four affected operations, with `[DISCRIMINATING]` printed beside the number. |
| D-31 | MINOR | **open, new** | `indexBoundaries()` gives the string-index family almost no measured yield: `uSubstring(int,int)` is 17 AGREE of 432 rows against a perfect port. Same species as D-19 / D-28. |
| D-32 | MINOR | **new, narrows D-17** | D-17 cannot buy a stage pass. An adapter concealing its wrong rows behind `HarnessMarshallingException` scores 0 DIFFER and 0 stage passes on the affected operations; the row note still names the subject. The damage is to attribution, not to the verdict. |
| D-15 | — | **CLOSED, re-measured** | 119 clean-but-degenerate sweeps, 119 refused, 0 stage passes. Sign-off route exact in both key positions. |
| D-19 | — | **CLOSED, re-measured** | 61 → 11 zero-measurement operations; the 11 are 8 void mutators plus 3 that always throw. |
| D1, D2, D-10 | — | **not regressed** | Seven non-port subjects; a/b/c/d/g at zero agreement. |
| D-18 | — | open, untouched | Out of reach of a mutation experiment. |
| D-20 | — | open, untouched | Not in scope this round. |

---

## 10. Verdict

**`SOUND_WITH_DOCUMENTED_LIMITS`.**

The harness detects real, subtle infidelities in a ported U-type, on the operations that carry them,
with row counts and attributed evidence: eight of eleven planted defects diverged, seven of those on
every operation they touched, and each cost the affected operations their stage pass. Two of the
remaining three were deliberate concealment attacks, and although both destroyed the divergence,
neither bought a pass. A `DIFFER` / `MIXED` / `BOTH_THREW` count from this harness was always
trustworthy and this round is the first direct measurement that its *silence* means something too.

It is not a closed instrument and must not be quoted as one. Its silence means something **only
inside the region the corpora reach**, and that region is not measured, not published and not gated
(D-30). Its pass/fail bit is not a fidelity signal on the 92 operations a perfect port already fails
(D-29). And 33 public operations, `equals(Object)` among them, are not in the inventory at all.

The structural pattern from five rounds holds again, and I found this round's doors the same way my
predecessors found theirs — by pushing on the thing the last round named. Round 4 named the codomain;
FIX A made it a number. This round pushed on the **domain**, and there is no number there.
