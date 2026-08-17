# S1 round-8 refutation — the Java-type check, demoted

**Refuter:** independent session, `port-uncertainty-2`.
**Under review:** commits `066fe15c` (behaviour) and `8d39041c` (documentation) — D-43 half (b) closed by
removing the declaration API and demoting a type-only difference to `AGREE` plus a counted dimension.
**Date:** 2026-08-17.
**Question asked of this round, verbatim from the brief:**

> With the Java-type check demoted to a reported dimension, is the harness's SCORING sound and its
> CONTENT detection intact — i.e. can S4 start?

**VERDICT: SOUND WITH DOCUMENTED LIMITS.** S1 scoring is sound and content detection is intact: the
perfect-port control is byte-identical to round 7's, all eleven content probes reproduce round 7's
figures exactly, no probe regressed, and a content difference still produces `DIFFER` even when a type
difference coincides with it — measured at sweep scale, not argued. **S4 can start.**

**But the escape-hatch class is not gone; it moved from a `String` parameter to an `Object` parameter,**
and I take a genuinely wrong-class port to a sweep **byte-identical to the perfect-port control** with the
disclosure clause in **0 rows** — the same construction shape that made round 7 `DEFECTIVE`. The reason
this is not a `DEFECTIVE` verdict is that the demotion has made the hatch **inert at S1**: laundering the
token changes no verdict and gains no stage pass (74 either way). Its target is `harness-contract.md` §7's
**dated obligation on S4**, which mandates `javaTypeMismatchCount() == 0` as a gate clause — and that
clause is satisfiable by one line with zero disclosure. That is **D-52**, a MAJOR against the S4
requirement as written, and it must be fixed *before* S4 relies on the clause, not after.

Contents: §1 control · §2 the escape hatch · §3 content detection · §4 is the demotion honest · §5
false-green attempts · §6 acceptance, determinism, hygiene · §7 defect register · §8 answer to the
question.

---

## 0. Maven contention (rule 5), stated before any number

The other session's loop shell was present throughout: PID `494057` at the start of my work, PID
`1380275`/`1380299` later. Its command line **literally contains the text `mvn -B clean test`**, so
`pgrep -f '[m]vn|[m]aven'` matches it while it is only sleeping in its 60-second quiet loop. Its output
log:

```
$ test -f /tmp/claude-1000/-home-xoruser-msc-4/66c28628-.../scratchpad/mvn-final.log && echo YES || echo NO
NO
```

**does not exist**, so its `mvn -B clean test` never ran during my work — my own runs kept resetting its
quiet counter. **No phantom counts were observed:** two consecutive `mvn -q clean && mvn -B verify`
runs gave identical aggregate counts *and* byte-identical evidence (§6), and no run produced
"module not found: use.core" or an impossible total. Every figure below is either pasted from a run in
this session or explicitly labelled as quoted from an earlier round's report.

---

## 1. Control first — INTACT, and byte-identical to round 7

Pasted from `verify1.log` (identical in `verify2.log`):

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
distinct throw-pairs a PERFECT port produces  154   <- AcceptedThrowPairs entries a human would have to author, one per (operation, both classes, both messages), before clause 2 could ever be met on the operations that throw
    e.g. UIntegerValue.divideBy(value) || reference threw java.lang.ArithmeticException: / by zero / subject threw java.lang.ArithmeticException: / by zero
    e.g. UIntegerValue.inverse() || reference threw java.lang.ArithmeticException: / by zero / subject threw java.lang.ArithmeticException: / by zero
    e.g. UIntegerValue.mod(value) || reference threw java.lang.ArithmeticException: / by zero / subject threw java.lang.ArithmeticException: / by zero
    e.g. UIntegerValue.power(value) || reference threw java.lang.RuntimeException: UInteger.power() : expected Real or Integer exponent value / subject threw java.lang.RuntimeException: UInteger.power() : expected Real or Integer exponent value
===================================================================
```

0 `DIFFER`, 0 `MIXED`, 0 diverging operations, 74 stage passes. In the new dimension:

```
control javaTypeMismatch 0   <- MUST be 0
```

**The single strongest statement that the control did not move.** md5 of the pasted control block:

```
$ md5sum control-block.txt
c724bd19dbed9071ffc8762675584107  control-block.txt
```

That is the same digest `stage-01-verification-round7.md` §1 published for round 7, and the porter's
report claims for round 8. Three independent extractions agree. **The control is intact.**

I also reproduced the control **from my own sweep code**, not the suite's, over the same 285 operations
and the same stage-shaped domains — because a control that only the suite can compute is a control the
suite could be wrong about (`Round8Refuter`, scratch, never committed):

```
  R0-control-perfect   rows=19083 measured=17199 agreed=17199 DIFFER=0 MIXED=0 typeMism=0 divOps=0 passes=74
      verdicts {AGREE=17199, BOTH_THREW=910, HARNESS_ERROR=883, UNMEASURABLE=91}
```

Same rows, same measured count, same tally, same 74. **My rig and the suite agree, so the numbers below
are attributable to my constructions and not to my scaffolding.**

---

## 2. Is the escape-hatch class actually gone? **NO — it moved. D-52, MAJOR.**

The claim under test, from the porter's report: *"a token is OBSERVED or ASSUMED and never
author-chosen"*, defended three ways — a grep with no call sites, a compile-level argument (`javaType`
is private final, the only setter is `private observed(String)` reachable only from
`observedFrom(Object)`), and a reflection test pinning `UValue`'s public String-taking members to
`{uString, string, opaque}`.

**All three defences are true as stated, and all three are about the wrong parameter type.**
`observedFrom` takes an `Object`. The token is `returned.getClass().getName()`. **An author who chooses
the object has chosen the token**, and the harness has no way to know whether the object it was handed is
the one the implementation produced.

### 2.1 The construction: ONE port with a REAL wrong-class defect, TWO adapters

Not a content-perfect port whose adapter was lazy — the case round 8 was built to exonerate — but a port
that genuinely answers the wrong Java class. It is content-perfect on every row, and where the historical
answers an `org.tzi.use.uml.ocl.value.*` object it answers a **raw boxed primitive**. That is exactly the
infidelity `harness-contract.md` §7 quotes from round 7 as *"401 rows / 9 operations"*. Adapter A observes
the object the port returned. Adapter B is the same port with one line changed: it observes an **empty
stand-in class of the name the reference used**. Pasted, `Round8RefuterD`:

```
=== R1d: ONE port with a REAL wrong-class defect, TWO adapters =====
  R0-control (perfect port)        DIFFER=0    MIXED=0    typeMism=0     ops=0    passes=74
      tally {AGREE=17199, BOTH_THREW=910, HARNESS_ERROR=883, UNMEASURABLE=91}
      rows carrying a type clause 0  (of which 'subject ASSUMED' 0, 'not checkable by this harness' 0)
  A: honest, observes its object   DIFFER=0    MIXED=0    typeMism=401   ops=9    passes=74
      tally {AGREE=17199, BOTH_THREW=910, HARNESS_ERROR=883, UNMEASURABLE=91}
      rows carrying a type clause 401  (of which 'subject ASSUMED' 0, 'not checkable by this harness' 401)
      operations reported wrong-class: [UBooleanValue.equalsC(value,double), UBooleanValue.toBoolean(), UIntegerValue.toInteger(), UIntegerValue.toReal(), URealValue.toInteger(), URealValue.toReal(), UStringValue.at(int), UStringValue.toBoolean(), UStringValue.uToString()]
  B: same port, stand-in laundering DIFFER=0    MIXED=0    typeMism=0     ops=0    passes=74
      tally {AGREE=17199, BOTH_THREW=910, HARNESS_ERROR=883, UNMEASURABLE=91}
      rows carrying a type clause 0  (of which 'subject ASSUMED' 0, 'not checkable by this harness' 0)
  B tally == control tally?      true
  B stage passes == control's?   true
===================================================================
```

**Read the three lines together.** The same port. Adapter A publishes a 401-row defect across nine named
operations and 401 rows of disclosure. Adapter B publishes **`typeMism=0`, `ops=0`, a verdict tally
byte-identical to the perfect-port control, and 0 rows carrying any type clause at all.** This is round
7's refutation, verbatim in shape:

| | round 7's `declaredJavaType(referenceToken, "x")` | round 8's `observedFrom(standIn)` |
|---|---|---|
| lines of adapter code | 1 | 1 |
| the wrong-class finding it erases | 3 445 `DIFFER` / 182 ops | 401 `javaTypeMismatch` / 9 ops |
| resulting sweep | byte-identical to the control | byte-identical to the control |
| rows carrying the mandated disclosure | **0** | **0** |
| provenance the harness reports | `DECLARED` | **`OBSERVED`** |

The last row is worse, not better. Round 7's laundered rows at least carried a provenance token that named
the act. Round 8's carry `OBSERVED` — the provenance the contract treats as the *trustworthy* one.

### 2.2 The whole dimension, erased with nineteen empty classes

The stand-in table needs no knowledge the author does not already have: **the harness publishes the list
of names itself**, in `=== representation census: what the reference actually returns ===`. Eighteen
nested empty classes plus four JDK objects cover every class the reference ever returns. Pasted,
`Round8RefuterB`:

```
=== R1c: nineteen empty stand-in classes erase the whole dimension ===
stand-in simple names the adapter can present: [BooleanValue, IntegerValue, RealValue, StringValue, SequenceValue, UBooleanValue, UIntegerValue, URealValue, UStringValue, BooleanType, IntegerType, RealType, StringType, UBooleanType, UIntegerType, URealType, UStringType, UInteger, Boolean, Integer, Double, String]
  R0-control-perfect                  rows=19083 ... typeMism=0    typeOps=0   divOps=0 passes=74 | notes: ASSUMED=0    NOTCHECKABLE=0 anyTypeClause=0
  R1a-factory-typed, honest omission  rows=19083 ... typeMism=3445 typeOps=182 divOps=0 passes=74 | notes: ASSUMED=3445 NOTCHECKABLE=0 anyTypeClause=3445
  R1c-stand-in laundering             rows=19083 ... typeMism=0    typeOps=0   divOps=0 passes=74 | notes: ASSUMED=0    NOTCHECKABLE=0 anyTypeClause=0
  R1c verdict tally == control?  true
  R1c stage-pass set == control? true
  R1c javaTypeMismatch 0  (honest omission: 3445 on 182 operations)
  R1c rows carrying ANY type clause 0   (the disclosure the contract relies on)
  R1c summary of one op: BooleanValue.value(): 2 rows, 2 measured, 2 agreed, 0 disagreed, 0 java-type mismatch(es), 2 distinct reference value(s) [DISCRIMINATING]
  R1a summary of one op: BooleanValue.value(): 2 rows, 2 measured, 2 agreed, 0 disagreed, 2 java-type mismatch(es), 2 distinct reference value(s) [DISCRIMINATING]
======================================================================
```

The last two lines are the finding in one place. **`stageStatement()` — the exact line §7's reviewer check
tells a reviewer to read verbatim — reads `0 java-type mismatch(es)` for the laundering adapter and
`2 java-type mismatch(es)` for the honest one, on the same operation, with the same content.** The
mandated reviewer check does not distinguish them.

### 2.3 A weaker laundering already gets most of the way, with no table at all

Before building the table I tried the naive version an author would write first — "observe the boxed
primitive rather than the object", four lines, no census needed:

```
  R1a-factory-typed (P13 shape)  ... typeMism=3445 divOps=0 passes=74 notesASSUMED=3445 notesNOTCHECKABLE=0
  R1b-FABRICATED-observation     ... typeMism=401  divOps=0 passes=74 notesASSUMED=0    notesNOTCHECKABLE=401
  R1b == R0 verdict tally?      true
  R1b typeMismatch              401   (R1a, same port, honest omission: 3445)
  ops with a mismatch: R1a 182   R1b 9
```

3 445 → **401**, 182 operations → **9**, with four lines and no knowledge of the reference at all. The
residual 401/9 is precisely the population the full table then clears. **The laundering gradient is smooth
and it starts shallow**, which matters for the incentive argument §7 makes: an author reducing 3 445 red
figures to 401 will not stop at 401.

### 2.4 Other routes, tested rather than accepted

Pasted, `Round8Refuter`:

```
=== R2: can an author write the token without an object? =========
  before                       INTEGER(7)@IntegerValue / ASSUMED
  Field.set                    SUCCEEDED -> INTEGER(7)@Integer
  VarHandle.set                REFUSED: java.lang.UnsupportedOperationException: set
  private observed(String)     SUCCEEDED -> INTEGER(7)@Integer / OBSERVED
  observedFrom(chosen object)  INTEGER(7)@Integer / OBSERVED   <- no reflection, one line, author-chosen token
  every public/protected UValue member taking an Object:
      public boolean org.tzi.use.uncertainty.differential.UValue.equals(java.lang.Object)
      public org.tzi.use.uncertainty.differential.UValue org.tzi.use.uncertainty.differential.UValue.observedFrom(java.lang.Object)
=================================================================
```

* **`Field.setAccessible(true)` + `Field.set` on the private final `javaType` SUCCEEDS.** Test code runs on
  the classpath, so no module barrier applies. Note what it produces: `canonical()` becomes
  `INTEGER(7)@Integer` while `typeProvenance()` stays **`ASSUMED`** — a token that renders as a match on a
  value that never observed anything. The row is `AGREE` with an empty note, so this route also discloses
  nothing.
* **`observed(String)` via `setAccessible` SUCCEEDS** and yields provenance `OBSERVED`. The deleted API is
  still there, one `setAccessible` away.
* `VarHandle.set` on the final field is refused, which is the only route that is closed.
* **Neither reflective route matters**, because `observedFrom(anyObject)` is a public, one-line,
  innocent-looking call that does the same job. I report them only because the porter's defence was
  stated as "there is no route".

**Subclassing `Candidate`** buys nothing extra: a `Candidate` returns a `UValue`, and the `UValue` it
returns is built by the same factories, so all a subclass gains is the `observedFrom` route it already had.
The `opaque(String, String)` factory does put a caller-chosen name into `javaType()`, and the regression
test's justification — the name also lands in `content()`, so an untruthful token is a content difference —
is correct as far as it goes; see §4.4 for the one case where it does not.

### 2.5 What is and is not already declared

`harness-contract.md` §5's D-43 row **does** say: *"The token is also only as honest as the adapter's
choice of object: `observedFrom` believes what it is handed, which is not checkable."* That is honest and
it is why this is not a `DEFECTIVE` verdict. **Three things are not stated anywhere, and all three are
load-bearing for §7's requirement:**

1. that the unchecked choice of object drives `javaTypeMismatchCount()` to **exactly 0** — not "may be
   optimistic", but *fully erased*, on all 182 operations, with the sweep byte-identical to the control;
2. that the note's own disclosure — *"Whether the object the subject observed is the one its
   implementation returned is not checkable by this harness"* — fires on **0 rows** of a laundered sweep
   (measured: `NOTCHECKABLE=0` for R1c and R1d/B), because like round 7's reason it fires only where the
   instrument already noticed a difference. **This is the identical failure mode round 7 was marked
   `DEFECTIVE` for, in a new location, and §7's retraction of round 7 does not notice that its
   replacement inherits it.**
3. that §7's dated obligation therefore rests on a clause an adapter can satisfy by laundering, while its
   own soundness argument reads *"once it is attributable a non-zero value is a port defect and must fail
   the gate"* — **attributability is asserted by the adapter, not enforced by the harness.** The reviewer
   check §7 offers, *"reject a type-fidelity figure from an adapter whose attribution route is not
   stated"*, does not separate A from B in §2.1: **both route through `observedFrom`, and both would state
   that truthfully.**

**D-52, MAJOR.** The fix is not a fourth API change. It is structural, and the suite already contains the
pattern: `PortedInfidelityDetectionPowerTest.observeWhatThePortReturned` obtains the object from
`invokeRaw` and observes *that*, so the observed object is provably the invocation's return value. The S4
requirement should mandate that **shape** — the harness or the adapter's single invocation seam supplies
the object, and no other object may be passed to `observedFrom` — and should say in §7 that
`javaTypeMismatchCount() == 0` is a gate clause **only** under that shape, with §2.1's two-adapter
measurement as the reason.

---

## 3. Is content detection intact? **YES. Every probe, every figure, no regression.**

All eleven planted content infidelities, pasted from `verify1.log` and equal in `verify2.log`. Rows are
**19 083 for every probe**, matching the control.

| probe | rows | measured | agreed | DIFFER | MIXED | ops detected | stage passes (control 74) |
|---|---|---|---|---|---|---|---|
| P0-perfect (control) | 19083 | 17199 | 17199 | 0 | 0 | 0 | 74 |
| P1-off-by-one-index | 19083 | 17160 | 17108 | 52 | 86 | 3 | 74 |
| P2-linear-uncertainty | 19083 | 17199 | 16731 | 468 | 0 | 4 | 70 |
| P3-hypot-uncertainty | 19083 | 17199 | 17175 | 24 | 0 | 4 | 70 |
| P4-le-for-lt | 19083 | 17199 | 16919 | 280 | 0 | 6 | 70 |
| P5-round-10dp | 19083 | 17199 | 16771 | 428 | 0 | 7 | 67 |
| P6-equals-ignores-uncertainty | 19083 | 17199 | 16080 | 1119 | 0 | 4 | 72 |
| P7-undefined-on-zero-divisor | 19083 | 17199 | 17094 | 105 | 62 | 6 | 71 |
| P8-hides-behind-harness-error | 19083 | 16731 | 16731 | 0 | 0 | **0** | 70 |
| P9-hides-behind-unsupported | 19083 | 15597 | 15597 | 0 | 0 | **0** | 70 |
| P10-narrow-input-window | 19083 | 17199 | 17199 | 0 | 0 | **0** | 74 |
| P11-negative-zero-collapse | 19083 | 17199 | 17140 | 59 | 0 | 3 | 71 |

**Every DIFFER count, every MIXED count and every stage-pass figure is equal to
`stage-01-verification-round7.md` §3's table.** Nothing regressed. The detected operation *sets* are
identical too, pasted:

```
DETECTED on          3 operation(s): [UStringValue.at(int), UStringValue.uAt(int), UStringValue.uSubstring(int,int)]
DETECTED on          4 operation(s): [UIntegerValue.add(value), UIntegerValue.minus(value), URealValue.add(value), URealValue.minus(value)]
DETECTED on          4 operation(s): [UIntegerValue.add(value), UIntegerValue.minus(value), URealValue.add(value), URealValue.minus(value)]
DETECTED on          6 operation(s): [UIntegerValue.gt(value), UIntegerValue.lt(value), URealValue.gt(value), URealValue.lt(value), UStringValue.gt(value), UStringValue.lt(value)]
DETECTED on          7 operation(s): [URealValue.cos(), URealValue.divideBy(value), URealValue.inverse(), URealValue.mult(value), URealValue.sin(), URealValue.sqrt(), URealValue.tan()]
DETECTED on          4 operation(s): [UBooleanValue.uEquals(value), UIntegerValue.uEquals(value), URealValue.uEquals(value), UStringValue.uEquals(value)]
DETECTED on          6 operation(s): [UIntegerValue.divideBy(value), UIntegerValue.divideByR(value), UIntegerValue.inverse(), UIntegerValue.mod(value), URealValue.divideBy(value), URealValue.inverse()]
DETECTED on          0 operation(s): []
DETECTED on          0 operation(s): []
DETECTED on          0 operation(s): []
DETECTED on          3 operation(s): [URealValue.floor(), URealValue.mult(value), URealValue.neg()]
```

Blind-spot set still exactly one entry:

```
=== planted defects the harness did NOT see =======================
  ??? P11-negative-zero-collapse / URealValue.round()  [STAGE PASS]
===================================================================
```

Detection power is therefore **8 of 11** planted content infidelities caught on every affected operation,
with the three misses named (P8/D-17 attribution loss, P9/D-17, P10/D-30) — unchanged from round 7.

**The one probe that moved, and it is the demotion itself, not a regression:** P12, the wrong-Java-type
probe, went from `DIFFER 3 445 / 182 diverging operations / 45 stage passes` to
`DIFFER 0 / 0 / 74 passes` with its 3 445 rows in `javaTypeMismatch`. Pasted:

```
=== D-43: two readings of the same measurement ====================
  subject                              DIFFER     divOps   passes   typeMism notes ASSUMED
  P0-perfect                                0          0       74          0            0
  P12-boxed-primitive                       0          0       74       3445            0
  P13-factory-typed-adapter                 0          0       74       3445         3445
  P14-observing-adapter                     0          0       74          0            0
  stage passes the port with a DEFECT loses    0
  stage passes the port with NO defect loses   0   <- was 29 before round 8; the false-divergence mode
  operations carrying a java-type mismatch:
      P12 182   P13 182   P14 0
===================================================================
```

**The false-divergence mode is gone, independently confirmed.** P13 — a content-perfect port with a
factory-typed adapter — reaches the control's exact stage-pass set with 0 `DIFFER`, and P14 — the same port
with an observing adapter — is row-for-row indistinguishable from the reference. Both were the point of the
round and both hold.

---

## 4. Is the demotion honest?

### 4.1 (a) Can a stage quote a pass without seeing the count? **Almost never — but "never" is an overclaim.**

`stageStatement()` carries the figure unconditionally, including zero. Verified on a laundered sweep and an
honest one in §2.2, and on every probe in the suite output, e.g.:

```
    statement UStringValue.at(int): 144 rows, 26 measured, 0 agreed, 144 disagreed, 0 java-type mismatch(es), 14 distinct reference value(s) [DISCRIMINATING]
```

`summary()` carries it when non-zero; the report header carries `# rows.javaTypeMismatch` per file and
`# op.<key>.javaTypeMismatch` per operation, and the two committed goldens gained exactly those two keys
and nothing else (§6.3). **That is a real mechanism and it is the right one.**

The claim that goes too far is the porter's *"no agreement figure can be rendered from `Result` without
it"*, and `DifferentialSweep.java:760`'s *"no way to render an agreement figure from this class without…"*.
Measured, `Round8Refuter` R6:

```
=== R6: can a stage render an agreement figure without the count? =
  agreementCount()   2        <- public, unaccompanied
  agreements().size()2
  measurementCount() 2
  isClean()          true
  isStagePass(1,none)false
  summary()          URealValue.neg(): 2 rows, 2 measured, 1 distinct ref, AGREE=2, javaTypeMismatch=2
  stageStatement()   URealValue.neg(): 2 rows, 2 measured, 2 agreed, 0 disagreed, 2 java-type mismatch(es), 1 distinct reference value(s) [NOT DISCRIMINATING: always INTEGER(1)@Integer]
  javaTypeMismatch   2
=================================================================
```

`agreementCount()` (line 520) and `agreements()` (line 505) are public and return the agreement population
with nothing beside it, and **`isClean()` returns `true` on a sweep carrying two java-type mismatches**.
The mechanism binds `stageStatement()` and `summary()`, not the class. This is the same shape as the
pre-existing D-15 overclaim and is **MINOR (D-53)**: the sentence should say "no way to render an agreement
figure *from `stageStatement()` or `summary()`*".

### 4.2 (b) Is the count accurate on cases verified by hand? **Yes.**

Three independent recounts agree with the harness on every sweep I ran:

* the harness's `javaTypeMismatchCount()`;
* my own recount over the rendered rows (`AGREE` && columns differ) in `Round8Refuter`
  — `agreeColsDiffer` equals `typeMism` on all six of my sweeps;
* the suite's two recounts (`ProbeResult.agreeRowsWhoseColumnsDiffer`,
  `OperationTally.javaTypeMismatch`), asserted per operation.

Hand-verified figures: the honest wrong-class port reports **401** across the **nine** named operations of
§2.1; the factory-typed adapter reports **3 445** across **182**; `BooleanValue.value()` reports **2** of
its 2 rows, and its two rows are `BOOLEAN(x)@Boolean` vs `BOOLEAN(x)@BooleanValue`, which is one mismatch
each. The pasted first mismatch row names both fully-qualified classes, both provenances, the defect ids,
and the call that would have made it a measurement:

```
0	BooleanValue.compareTo(value)	BOOLEAN(true)@BooleanValue | BOOLEAN(true)@BooleanValue	INTEGER(0)@Integer	INTEGER(0)@IntegerValue	AGREE	java type mismatch: reference returned java.lang.Integer (INTEGER(0)@Integer) / subject returned org.tzi.use.uml.ocl.value.IntegerValue (INTEGER(0)@IntegerValue); the content is IDENTICAL -- right content, wrong Java type (defect D-18). This row is scored AGREE and counted in rows.javaTypeMismatch, not scored as a divergence: at S1 the ported side's class cannot be authentically observed, because no ported value class exists to observe, so a type-only difference measures the adapter and not the port (D-43). Provenance: reference OBSERVED, subject ASSUMED (...). The subject's adapter never looked at what its implementation returned, so this difference is a finding about the ADAPTER and not about the port (D-43); an adapter must attribute through UValue.observedFrom(Object).
```

Round 7's laundering defect — the mandated text firing only where a difference already exists — is fixed
**for the provenance clause specifically**: both provenances now print on every type-mismatch row, and the
suite asserts it in both directions. It is *not* fixed for the population of rows where the clause prints
at all (§2.5, point 2).

**One accuracy limit, measured, and it is the porter's own attack (2).** The count is derived from the
rendered columns, so a row wrong in *both* dimensions is a `DIFFER` and leaves the count. Pasted,
`Round8RefuterB` R3c — a port with the wrong class everywhere *and* a negated boolean payload:

```
=== R3c: wrong in BOTH dimensions on the raw-returning operations =====
  R3c-type only ...                    DIFFER=0    typeMism=3445 typeOps=182 divOps=0   passes=74
  R3c-content AND type on the same rows DIFFER=1831 typeMism=1883 typeOps=42  divOps=143 passes=71
  javaTypeMismatch fell from 3445 to 1883; operations reporting one fell from 182 to 42
  DIFFER rose from 0 to 1831, diverging ops 143
======================================================================
```

**`javaTypeMismatchCount()` is not monotone in wrongness: adding a content defect *reduces* it from 3 445
to 1 883 and its operation count from 182 to 42.** Nothing is hidden — `DIFFER` rises to 1 831 across 143
operations and three stage passes are lost — and the *row notes* still name the type mismatch on all
3 445 rows (`anyTypeClause=3445` in both sweeps), so the evidence is intact and only the aggregate
under-reports. **MINOR (D-54)**, the D-21 shape applied to the new dimension: the header number must not be
read as a lower bound on wrong-class rows.

### 4.3 (c) Does a CONTENT difference still produce `DIFFER` when a type difference coincides? **YES — constructed and measured.**

This is the dangerous case the brief names, so I built it at sweep scale rather than in a unit assertion.
`Round8Refuter` R3: P2's arithmetic defect (uncertainties combined linearly) with an *honest* adapter, and
the same defect with a *wrong-class* adapter, over all 285 operations:

```
  R3a-content-defect only (P2 shape)  rows=19083 measured=17199 agreed=16731 DIFFER=468 MIXED=0 typeMism=0    divOps=4 passes=70
  R3b-content defect + wrong class    rows=19083 measured=17199 agreed=16731 DIFFER=468 MIXED=0 typeMism=3445 divOps=4 passes=70
  R3a DIFFER 468   R3b DIFFER 468   R3b typeMismatch 3445   (R1a typeMismatch 3445)
  R3b divergingOps 4   R3a divergingOps 4
```

**Identical `DIFFER` count, identical diverging-operation count, identical stage-pass count.** Adding a
type difference on top of a content difference removes nothing. R3c above is the stronger version: with the
content defect on the raw-returning operations themselves — the exact rows where the type difference lives —
`DIFFER` goes from 0 to **1 831** and 143 operations diverge. **The demotion did not swallow a single
content difference.**

The reason is structural and I checked it rather than assumed it. `classify` (`DifferentialSweep.java`
~243–270) demotes only when `ref.content().equals(sub.content())`, and `canonical() = content() + "@" +
typeToken()`, so content-equal-and-canonical-unequal is exactly and only a token difference. `content()`
(`UValue.java` ~547–585) is injective on payloads for every kind except one — see §4.4.

### 4.4 Two latent holes the demotion created, both measured unreachable today

**(i) `OPAQUE` content is a non-injective concatenation.** `opaque(className, repr)` renders
`OPAQUE("className|repr")`, so two values differing in *both* class name and representation can render
equal content and be demoted to `AGREE`:

```
=== R5: OPAQUE content, and whether the split point is unique ====
  OPAQUE reference rows                      197
  ... whose rendered content has >1 '|'      0
  operations                                 []
  hand-built collision: contents equal?      true
      OPAQUE("a.b.C|x=1|y=2")   javaType a.b.C|x=1
      OPAQUE("a.b.C|x=1|y=2")   javaType a.b.C
=================================================================
```

The collision is real and **before round 8 it was a `DIFFER`**. It is unreachable from the reference side:
all **197** `OPAQUE` reference rows have exactly one `|`, so there is only one split point and the class
name is forced. Exploiting it needs a hostile `opaque()` call *and* a reference representation containing
`|`. **MINOR (D-55), latent, corpus-dependent — the D-30 shape.**

**(ii) A nested type-only difference is still scored `DIFFER`.** `SEQUENCE` content embeds each element's
`canonical()`, token included, so the identical difference gets opposite verdicts depending on depth:

```
=== R4b: the SAME type-only difference, nested one level =========
  top level : INTEGER(1)@Integer  vs  INTEGER(1)@IntegerValue   content equal? true
  nested    : SEQUENCE[INTEGER(1)@Integer]@ArrayList  vs  SEQUENCE[INTEGER(1)@IntegerValue]@ArrayList   content equal? false
      verdict AGREE   typeMismatch 1
      verdict DIFFER  typeMismatch 0
=================================================================
```

That is the residual false-divergence mode round 8 set out to remove, one level down — and round 8's own
P13 probe cannot see it, because `asAFactoryTypedAdapterWouldReturnIt` (`PortedInfidelityDetectionPowerTest`
~1106) falls through `default: return produced;` for `SEQUENCE`, passing the reference's element
attribution straight through. I closed that gap in my own probe by rebuilding sequence elements through the
factories, and measured the corpus:

```
  R4-factory-typed, sequences rebuilt too  rows=19083 ... DIFFER=0 typeMism=3445 divOps=0 passes=74
  R4 DIFFER 0   R4 typeMismatch 3445   R4 divergingOps []
```

**0 `DIFFER`.** The census shows **17** `SequenceValue` rows and their elements are `StringValue`-shaped on
both sides, so the factory's assumption happens to match. **MINOR (D-56), latent:** unreachable in this
corpus, guaranteed by nothing, and one widened corpus away from re-creating the exact mode round 8 removed.

### 4.5 What the demotion costs at the gate — measured, and the record does not have this number

The porter measured the demotion's cost at *verdict* level and said so honestly. It is also measurable at
*gate* level, from the current tree, without a pre-round-8 harness: an operation passes now iff it measured
something, disagreed nowhere and is discriminating; under round 7's rule a type-only difference was a
disagreement; therefore **every operation that passes now and carries `javaTypeMismatch > 0` is a pass the
demotion created.** Pasted, `Round8RefuterC`:

```
=== R7: stage passes the demotion CREATES, by subject =============
  (stage-shaped domains, 285 operations, 19083 rows; control ceiling 74)
  R0-control-perfect                   passes=74   DIFFER=0      typeMism=0      passes OWED TO THE DEMOTION=0
  P12/P13-wrong-class or unattributed  passes=74   DIFFER=0      typeMism=3445   passes OWED TO THE DEMOTION=29
      [BooleanValue.compareTo(value), BooleanValue.hashCode(), BooleanValue.isFalse(), BooleanValue.isTrue(), BooleanValue.toString(), BooleanValue.toStringWithType(), BooleanValue.value(), IntegerValue.compareTo(value), IntegerValue.hashCode(), IntegerValue.toString(), IntegerValue.toStringWithType(), IntegerValue.value(), StringValue.compareTo(value), StringValue.hashCode(), StringValue.toString(), StringValue.toStringWithType(), StringValue.value(), UIntegerValue.compareTo(value), UIntegerValue.hashCode(), UIntegerValue.toString(), UIntegerValue.toStringWithType(), UIntegerValue.uncertainty(), UIntegerValue.value(), URealValue.compareTo(value), URealValue.hashCode(), URealValue.toString(), URealValue.toStringWithType(), URealValue.uncertainty(), URealValue.value()]
  f-echoes-receiver (return args.get(0)) passes=4    DIFFER=15805  typeMism=56     passes OWED TO THE DEMOTION=4
      [BooleanValue.isTrue(), BooleanValue.value(), IntegerValue.value(), StringValue.value()]
==================================================================
```

Two things worth having in the record:

* **The 29 is recovered and named.** The porter wrote that the tree no longer produces the pre-round-8
  figures and quoted 29 from round 7. It does produce it, by this construction, and the twenty-nine
  operations are now listed. The trade is exactly the one the round intended.
* **A subject that does nothing but echo its receiver gains 4 stage passes it did not have.** All four are
  *discriminating* accessors (`BooleanValue.isTrue()`, `BooleanValue.value()`, `IntegerValue.value()`,
  `StringValue.value()`), so they are not caught by the D-15 clause. This is not a *false* green — for those
  four accessors the receiver's payload genuinely is the answer, only the wrapper class differs — and it is
  reviewed, signed off, and the sign-off is now falsifiable: the suite asserts `per.agreed ==
  per.javaTypeMismatch` for every entry, and the report prints it:

```
  *** BooleanValue.value()  (16/16 driven rows agreed, 16 of them on the payload only (java type mismatch), 752 rows total, 2 distinct reference value(s); reviewed and signed off)
  --- RealValue.value()  (8/8 driven rows agreed, 8 of them on the payload only (java type mismatch), 752 rows total, 1 distinct reference value(s) -- always REAL(0.0)@Double [NOT DISCRIMINATING]; reviewed and signed off)
```

  **MINOR (D-57), record only:** the demotion's cost at gate level is 4 stage passes to a do-nothing
  subject and it is stated nowhere. It belongs beside the 29.

I also confirm the porter's `4951 = 4567 + 384` arithmetic is consistent with the current tree on its
measurable half:

```
=== unwritten-port invariant: f-echoes-receiver =================
javaTypeMismatch     384  <- agreement rows on which only the Java class differed (D-43)
agreement rows       4951
verdict tally        {AGREE=4951, DIFFER=63045, HARNESS_ERROR=618462, MIXED=39880}
```

4 951 and 384 are measured here; 4 567 is quoted from round 7 and I could not reproduce it either. The
porter labelled it as quoted rather than presenting the subtraction as a measurement, which is the correct
handling.

---

## 5. One more false-green attempt

Everything in §2 and §4 was an attempt. Consolidating, plus the constructions I tried and could not make
work:

| attempt | tool from | result |
|---|---|---|
| Erase a real 401-row wrong-class defect | rounds 6/7 laundering, new parameter | **SUCCEEDS as a reporting erasure**, gains **0** stage passes (§2.1) — no false green, D-52 |
| Erase the whole 3 445 / 182-op dimension | published census + 19 empty classes | **SUCCEEDS as a reporting erasure**, gains **0** stage passes (§2.2) |
| Hide a content defect behind a type difference | round 8's demotion | **FAILS** — `DIFFER` 468→468, 1 831 on the overlapping rows (§4.3) |
| Hide a content defect in `OPAQUE` content collision | round 6R's D-44 | **FAILS today** — 0 of 197 rows exploitable (§4.4 i) |
| Provoke a false divergence on a nested value | round 6's false-divergence mode | **FAILS today** — 0 `DIFFER` over the 17 `SequenceValue` rows (§4.4 ii) |
| Write the token with no object at all | reflection | **SUCCEEDS** via `Field.set` / `observed(String)`, discloses nothing, but buys nothing `observedFrom` did not already give (§2.4) |
| Exceed the control's 74 stage passes | all of the above, combined | **FAILS** |

The last row deserves its argument, because it is the one that would be a true false green. Clause 2
refuses any operation with a `BOTH_THREW`, `HARNESS_ERROR` or `UNMEASURABLE` row, and clause 3 is computed
from the **reference's** distinct values, so the pass set of *any* subject is a subset of
{operations the reference neither throws on nor is degenerate on} — **74**, fixed by the reference alone. No
subject can exceed it, and none of the eleven suite probes or six sweeps of mine did: every figure in this
document is ≤ 74. Making a subject *disagree less* than a perfect port is not reachable through the type
dimension, because the type dimension no longer feeds clause 2 at all — which is, exactly, why D-52 is a
reporting defect and not a false green.

**No false green found.** That is now five consecutive rounds with no false green in the scoring, and this
round is the first in which the type dimension cannot produce one either — because it cannot produce a
verdict.

---

## 6. Acceptance, determinism, hygiene

### 6.1 Counts

```
$ pgrep -af '[m]vn -B|[m]aven'     # only the other session's sleeping loop shell, log absent (§0)
$ mvn -q clean && mvn -B verify -Djava.awt.headless=true
```

Run 1 (`verify1.log`) and run 2 (`verify2.log`), aggregate `Tests run` lines, `(tests, failures, errors, skipped)`:

```
verify1: [('78','0','0','0'), ('1','0','0','0'), ('1','0','0','0'), ('129','0','0','0')]
verify2: [('78','0','0','0'), ('1','0','0','0'), ('1','0','0','0'), ('129','0','0','0')]
[INFO] BUILD SUCCESS
```

**surefire 78 (use-core) + 1 (use-gui) = 79; failsafe 1 (`OCLExpressionIT`) + 129 (`ShellIT`) = 130;
total 209; 0 failures, 0 errors, 0 skipped.** Exactly the stated acceptance baseline — **delta 0**. Nothing
pre-existing is broken: `MavenCyclicDependenciesCoreTest` 11, `MavenLayeredArchitectureTest` 1,
`ModelAPITest` 1, `HistoricalOracleIsolationTest` 9, `ShellIT` 129 all pass unchanged.

### 6.2 Determinism

Stripping only Maven's own `[INFO]/[WARNING]/[ERROR]/SLF4J/Download` lines, the **entire** remaining output
of both runs — every evidence block, every TSV row, every note — is byte-identical:

```
919997f36959cf8cc6f8af4a64030ecd  blocks1.txt
919997f36959cf8cc6f8af4a64030ecd  blocks2.txt
IDENTICAL EVIDENCE
```

### 6.3 Hygiene

```
$ git status --porcelain
(empty)
$ git diff --name-status 30d480db..HEAD -- '*/src/main/*'
(empty)
$ git diff --name-status 066fe15c~1..HEAD
M	docs/port2/differential/s1-smoke-ureal-add.tsv
M	docs/port2/differential/s1-smoke-ureal-minus-faulty.tsv
M	docs/port2/foundation-verdict.md
M	docs/port2/harness-contract.md
M	docs/port2/stage-01.md
M	use-core/src/test/java/org/tzi/use/uncertainty/differential/Candidate.java
M	use-core/src/test/java/org/tzi/use/uncertainty/differential/DiffReportWriter.java
M	use-core/src/test/java/org/tzi/use/uncertainty/differential/DifferentialHarnessRegressionTest.java
M	use-core/src/test/java/org/tzi/use/uncertainty/differential/DifferentialSweep.java
M	use-core/src/test/java/org/tzi/use/uncertainty/differential/PortedInfidelityDetectionPowerTest.java
M	use-core/src/test/java/org/tzi/use/uncertainty/differential/StubCandidate.java
M	use-core/src/test/java/org/tzi/use/uncertainty/differential/UValue.java
M	use-core/src/test/java/org/tzi/use/uncertainty/differential/UncertaintyDifferentialSmokeTest.java
M	use-core/src/test/java/org/tzi/use/uncertainty/differential/UnwrittenPortInvariantTest.java
```

Test-scoped and documentation only; `src/main` untouched; no pom, no `module-info.java`, no pre-existing
upstream test. Behaviour (`066fe15c`) and documentation (`8d39041c`) are separate commits.

**The golden refresh is exactly two header keys per file and nothing else:**

```
+# rows.javaTypeMismatch	0
+# op.URealValue.add(value).javaTypeMismatch	0
```

and, importantly, the faulty golden's **226** arithmetic `DIFFER` rows are unchanged and carry **0** type
clauses:

```
$ grep -c "java type mismatch" docs/port2/differential/s1-smoke-ureal-minus-faulty.tsv
0
$ grep "^# rows" docs/port2/differential/s1-smoke-ureal-minus-faulty.tsv
# rows	784
# rows.measured	784
# rows.agreement	558
# rows.disagreement	226
# rows.throwClassMismatch	0
# rows.javaTypeMismatch	0
```

The porter's argument against fabricating a stand-in inside the package — that it would re-caption all 226
arithmetic findings with a type clause — is therefore consistent with the committed evidence. My own
stand-in classes live in a scratch directory outside the repository and are not committed; `git status` is
clean.

### 6.4 The record's corrections, spot-checked

The four retractions the porter claims are present and specific: `stage-01.md:1457` carries a
**CORRECTION BOX** that quotes both withdrawn round-7 claims verbatim, names round 7's refuter, lists the
three renamed methods and declares §10.9 authoritative; `harness-contract.md` §7 quotes its own refuted
sentence and retracts it; `foundation-verdict.md:100` and its **H20** row quote-and-withdraw. §5's D-43 row
carries the new figures and, to its credit, the sentence that keeps this round from being `DEFECTIVE`:
*"the token is also only as honest as the adapter's choice of object: `observedFrom` believes what it is
handed, which is not checkable."*

---

## 7. Defect register from this round

| id | sev | statement | evidence |
|---|---|---|---|
| **D-52** | **MAJOR** | The escape-hatch class is not gone. `observedFrom(Object)` lets the author choose the token by choosing the object; one line takes a **real 401-row / 9-operation wrong-class port** to `javaTypeMismatch 0`, a verdict tally byte-identical to the control, and **0 rows** of disclosure — round 7's failure mode in a new location. `harness-contract.md` §7's dated obligation makes `javaTypeMismatchCount() == 0` an S4 gate clause whose soundness argument assumes attributability the harness does not enforce, and its reviewer check ("state the attribution route") does not separate an honest adapter from a laundering one, since both route through `observedFrom`. **Inert at S1** (0 stage passes gained); **must be fixed before S4 relies on the clause.** Fix: mandate the shape, not the call — the observed object must be the invocation's return value, as `observeWhatThePortReturned` already does — and say in §7 that the clause is a gate only under that shape. | §2.1, §2.2, §2.3, §2.5 |
| **D-53** | MINOR | "No agreement figure can be rendered from `Result` without the count" is an overclaim: `agreementCount()`, `agreements()` and `isClean()` are public and unaccompanied, and `isClean()` returns `true` on a sweep with 2 mismatches. The mechanism binds `stageStatement()` and `summary()`, which is enough — the sentence should say so. | §4.1 |
| **D-54** | MINOR | `javaTypeMismatchCount()` is not monotone in wrongness: a port wrong in both dimensions reports **1 883 / 42 ops** where the type-only port reports **3 445 / 182**. Nothing is hidden (`DIFFER` 1 831 / 143 ops, 3 stage passes lost; all 3 445 row notes still name the mismatch), but the header must not be read as a lower bound. D-21's shape in the new dimension. | §4.2 |
| **D-55** | MINOR | `OPAQUE` content is the non-injective concatenation `className\|repr`, so two values differing in **both** class and representation can render equal content and be demoted to `AGREE` — a case that was a `DIFFER` before round 8. Unreachable today: 0 of **197** `OPAQUE` reference rows have a second `\|`. Latent, corpus-dependent. | §4.4 (i) |
| **D-56** | MINOR | A **nested** type-only difference is still scored `DIFFER` (`SEQUENCE` content embeds element `canonical()`), so the identical difference gets opposite verdicts by depth — round 8's own false-divergence mode one level down. Round 8's P13 probe cannot see it because its helper passes `SEQUENCE` through unrebuilt. Unreachable today: rebuilding sequence elements through the factories yields **0 `DIFFER`** over the corpus's 17 `SequenceValue` rows. Latent. | §4.4 (ii) |
| **D-57** | MINOR | The demotion's cost **at gate level** is stated nowhere. Measured: it creates **29** stage passes for a wrong-class/unattributed port (the figure the record quotes from round 7, here recovered from the current tree and its 29 operations named) and **4** for a subject that only echoes its receiver, all four on *discriminating* accessors. The four are reviewed, signed off and asserted (`per.agreed == per.javaTypeMismatch`), so this is a record gap, not a false green. | §4.5 |

**Closed by this round, confirmed independently:** D-43 half (b) — `declaredJavaType`, `TypeProvenance.DECLARED`
and `typeDeclarationReason()` are gone, and no *compile-level* route accepts a type token (§2.4's member
listing shows `observedFrom(Object)` and `equals(Object)` as the only `Object`-taking members). D-43 half (a)
— the false-divergence mode: P13 reaches the control's exact stage-pass set with 0 `DIFFER`, P14 is row-for-row
the reference. D-51 — the `void` case is handled in both worked snippets. D-47 — the "Both classes were
OBSERVED…" certification is gone and the note now says what is not checkable.

**Unchanged and untouched, correctly recorded as open:** D-29 (a perfect port reaches 74 of 285; 92 refused
by clause 2; 154 hand-authored `AcceptedThrowPairs` entries the only way out), D-30 (no input-domain
coverage figure anywhere; P10 and `URealValue.round()` are its two measured instances), D-48 (a
non-attributing adapter absorbs a real wrong-class defect into the same figure), D-50 (D-44's 197 rows and
D-45's 84 declarations are stated in Javadoc and in an assertion *message* with no assertion computing
either).

---

## 8. The answer to the question

> With the Java-type check demoted to a reported dimension, is the harness's SCORING sound and its CONTENT
> detection intact — i.e. can S4 start?

**Yes, with five limits, and one of them is a MAJOR that S4 must fix before it leans on the type figure.**

**Sound.** The control diverges nowhere over 285 operations and 19 083 rows and is byte-identical to round
7's (`c724bd19dbed9071ffc8762675584107`), reproduced by my own rig as well as the suite's. No subject can
exceed the control's 74 stage passes, and none did. No false green was found in five constructions plus the
suite's eleven. **Content detection is intact and unchanged:** every `DIFFER`, `MIXED`, detected-operation
set and stage-pass figure for P1–P11 equals round 7's, with 19 083 rows on every probe and the blind-spot
set still the single `P11 / URealValue.round()` entry. **The demotion did not swallow content:** a content
difference coinciding with a type difference still produces `DIFFER` — 468 → 468 in one construction, 0 →
1 831 across 143 operations in the sharper one. **The false-divergence mode is gone**, so the pressure that
produced two laundering APIs is gone with it.

**The limits, in the order they bear on S4, and none of them padding:**

1. **D-52, MAJOR — `javaTypeMismatchCount() == 0` is not yet a sound gate clause.** One line
   (`observedFrom(aStandIn)`) takes a genuine 401-row / 9-operation wrong-class port to 0 in every published
   figure, with a sweep byte-identical to the control and the disclosure clause in 0 rows. The clause is inert
   at S1, so S1 is not wrong — but §7 *mandates* it at S4 and its soundness argument assumes an
   attributability nothing enforces. **S4 must adopt the invocation-seam shape** (the observed object *is*
   the invocation's return value, as `observeWhatThePortReturned` already demonstrates) **and §7 must say the
   clause is a gate only under that shape.** Doing this before writing the adapter costs nothing; doing it
   after means re-reading a figure.
2. **An `AGREE` row may be an agreement on the payload alone** — §7 already requires this be stated wherever
   an agreement figure from this harness is quoted, and it must be. Its measured extent: 3 445 rows across
   182 of 285 operations for a non-attributing adapter, and **29 stage passes** that a wrong-class port keeps
   because of it (D-57 adds the four an echoing subject keeps).
3. **D-48 is reclassified, not dissolved.** Through a non-attributing adapter a defect-free port and a
   really-wrong-class port report the same figure. The row note's provenance clause is the only
   discriminator, and it is a per-row fact with no header aggregate — which is why the porter's own
   recommendation (`# rows.subjectTypeObserved` / `.subjectTypeAssumed`, per file and per operation) should be
   built before S4 starts. **I agree with that recommendation and it is the cheapest item on this list.**
4. **D-29 and D-30 remain the dominant risks to S4 and are both bigger than the type question.** A perfect
   port reaches 74 of 285 stage passes, 92 refused by clause 2 with 154 hand-authored throw-pair entries the
   only escape; and there is no input-domain coverage figure anywhere, measured twice (P10's real arithmetic
   defect at receiver `42.0`: 0 `DIFFER`, byte-identical tally, all four operations at a full stage pass).
   Neither was touched this round and neither should be forgotten because this round was about types.
5. **D-54 to D-56 are reporting and latency limits, not scoring limits:** the count is not a lower bound on
   wrong-class rows; `OPAQUE` content is non-injective; a nested type-only difference is still a `DIFFER`.
   The last two are unreachable in today's corpus by measurement, not by construction, so they inherit D-30.

**On whether the demotion was the right call: yes, and for the porter's reason rather than the obvious one.**
The check read a property that does not exist yet. What round 8 establishes by removing the declaration API
is that the *class* of defect — an author-supplied token — cannot be closed by a better statement, and my §2
establishes that it cannot be closed by a better parameter type either. It can only be closed by making the
object non-choosable, which requires a real port to invoke. **That is exactly what S4 writes, so S4 is the
right place, and the requirement is correctly dated and reversible.** What must change before S4 uses the
clause is one sentence about the shape and one about the reviewer check — not a fourth API.

**Process.** I own Maven for this round; the other session's loop never ran (§0). Every figure is pasted
from `verify1.log`/`verify2.log` or from one of four scratch programs I wrote and ran against the compiled
test classes (`Round8Refuter`, `Round8RefuterB`, `Round8RefuterC`, `Round8RefuterD`), none of which is
committed — `git status` is clean and `src/main` is empty of changes. The one figure I could not reproduce
is round 7's pre-demotion echo-subject agreement count of 4 567; the porter labelled it as quoted and I do
the same. Nothing above is a sign-off on the S4 requirement's final wording, which is the next author's to
write.
