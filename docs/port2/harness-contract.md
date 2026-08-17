# The differential harness — contract for S4–S7

**Status: 2026-08-17, after eight review rounds (behaviour through `066fe15c`). Binding on every stage
that quotes a differential number.** Round 6 closed **D-18**, **D-34**, **D-35** and **D-36**
([`stage-01-round6-fixes.md`](stage-01-round6-fixes.md)); changes to the rules below from those fixes
are marked *(round 6)*. An independent refuter then confirmed all four, found the control intact and no
false green, and returned **`DEFECTIVE`** on one new MAJOR — **D-43**, the ported side's Java type is
*declared* by the adapter and never *observed* by the harness — plus two MINORs, **D-44** and **D-45**
([`stage-01-verification-round6.md`](stage-01-verification-round6.md), `3de8203e`; that report's local
ids `D-37`/`D-38`/`D-39` are canonically D-43/D-44/D-45 — see `stage-01.md` §10.5). Changes from the
refutation are marked *(round 6R)*. **Round 7 (`4bb5b6fe`) closed all three** — `observedFrom(Object)`
for the ported side, `asJavaType(String)` deleted, provenance in the row note, both readings of the
3 445 pinned as adjacent tests — and its changes are marked *(round 7)*; the evidence is
[`stage-01.md`](stage-01.md) **§10.8**. **An independent refuter then returned `DEFECTIVE` on round 7:
half (b) of D-43 was still open — `declaredJavaType(referenceToken, "x")` took a wrong-class port to a
sweep byte-identical to the perfect-port control, and the reason this file said was "printed into the
note" reached 0 rows** ([`stage-01-verification-round7.md`](stage-01-verification-round7.md)).
**Round 8 (`066fe15c`) demoted the check rather than patching it a third time:** `declaredJavaType` is
deleted, a class token is `OBSERVED` or `ASSUMED` and an author chooses neither, and a type-only
difference is `AGREE` counted in `javaTypeMismatchCount()` — with a **dated REQUIREMENT on S4** to
observe and to gate on it. Round 8's changes are marked *(round 8)* and **§7 carries the requirement**.
**§7 has three traps, not two.** The harness is
`use-core/src/test/java/org/tzi/use/uncertainty/differential/`. The narrative record, the single
open-defect register and the id re-keying map are in [`stage-01.md`](stage-01.md) **§10**, which is
authoritative over every other section of that file. The short human answer to "may S3 start?" is
[`foundation-verdict.md`](foundation-verdict.md). **This file is the rules, not the story.**

Read it before writing a sweep. Six rounds each found a new way for the harness — or for the
document reporting it — to claim fidelity that had not been measured; the seventh found a way for it
to claim *detection* that had not been measured; the eighth found that the fix for *that* had shipped
the same escape hatch twice under two names, and closed the class of defect instead of the instance.
Every rule below exists because one of them succeeded.

---

## 1. The principle

> **A differential oracle may report agreement only where it observed two comparable,
> non-degenerate values — and the claim reaches no further than the inputs it tried.**

Four clauses, each paid for:

* **two** — not one. A row where the harness failed to marshal, or where one side never ran, is the
  *absence* of a measurement, and an absence is not a measurement the two sides happen to share.
  (D1: 21 816 rows of this were scored green.)
* **values** — not throws, and not the encodings of "no result". Two throws are not a shared value
  however well their class names match (D2). Two `VOID`s are not a shared value (D-10). And a value
  is its **content together with its Java type**: right content in the wrong class is not a shared
  value either *(round 6, D-18)*. `UValue.canonical()` ends in `@<simple class name>` for every kind
  that carries an observation, so `BOOLEAN(true)@Boolean` — a raw `boolean` — is not
  `BOOLEAN(true)@BooleanValue`. Measured before the fix: a port that boxed every raw result into its
  `Value` class was byte-identical to a perfect port in every aggregate the harness published
  (0 `DIFFER` over 19 083 rows, the same 74 stage passes); after it, 3 445 `DIFFER` rows on 182 of
  285 operations and 29 stage passes lost. The perfect-port control still diverges nowhere, and no
  operation in the shipped corpora answers with two different runtime classes (0 of 285) — though the
  *reason* the census test gives for that is false for 84 of 285 declared return types (**D-45**), so
  the premise is a corpus fact and inherits D-30.
  **The sentence that stood here — "so the fix has no false-divergence mode" — was REFUTED in round 6
  and is not restored** *(round 6R, D-43; the mechanism it asked for landed in round 7)*. What is true
  is narrower and is worth stating exactly, because it is what a stage may lean on:
  > No operation in the shipped corpora answers with two different runtime classes (**0 of 285**,
  > measured against the reference alone by `noOperationAnswersWithTwoRuntimeClasses`), so for any
  > single operation there **is** one correct class. But whether a *difference* between the two sides'
  > classes is a statement about the two implementations depends on how each side came by its token, and
  > **at S1 the ported side cannot have observed one**, because no ported value class exists to observe.

  **So since round 8 a type-only difference is measured and not scored.** Where the content matches and
  only the class differs, the row is `AGREE` and the difference is counted in
  `Result.javaTypeMismatchCount()` / `# rows.javaTypeMismatch` / `# op.<key>.javaTypeMismatch` and printed
  unconditionally by `stageStatement()`. **Content differences are unaffected and remain `DIFFER`.**

  The history, because it is the argument. Round 6 left the ported side's class *declared* and the
  reference's *observed*, which made two different findings numerically identical: a **content-perfect**
  port with a factory-typed adapter measured `DIFFER 3 445, 182 of 285 operations, stage passes 74 → 45,
  lost 29` — *the same four numbers as the planted defect* — and a genuinely wrong-class port plus one
  `.asJavaType(v.javaType())` measured `DIFFER 0`. Round 7 kept the check and tried to make the stating
  route cost a written reason; that was refuted too — `declaredJavaType(referenceToken, "x")` reproduced
  the `3 445 → 0` erasure and the reason reached **0 rows** (§7). Round 8 stopped patching the newest
  instance and removed the class of defect: **`declaredJavaType` is deleted**, the token is `OBSERVED`
  (`UValue.observedFrom(Object)`, which is what `fromHistorical` itself calls on every branch) or
  `ASSUMED` (the factory default), and an adapter author chooses neither. Measured after the demotion,
  285 operations / 19 083 rows: the content-perfect port with a factory-typed adapter is `DIFFER 0`, `0`
  diverging operations, the control's exact **74** stage passes and verdict tally, with **3 445** rows in
  `javaTypeMismatch`; the planted wrong-class port measures the same **3 445** there; the same
  content-perfect port with an **observing** adapter measures **0**. Provenance never touches
  `canonical()` or a verdict in either direction: a subject must not be able to move a row by how it came
  by its token. **The two 3 445s are told apart only by the row note's provenance clause** — which is
  exactly why §7 carries a dated REQUIREMENT that S4 observes and gates on
  `javaTypeMismatchCount() == 0`. **Read the third trap in §7 before writing an adapter, and state in the
  stage document how the token was obtained.**
* **non-degenerate** — the operation must be *able* to answer differently. Two equal values over a
  single-point codomain are green by construction (D-15). This clause is now enforced:
  `Result.distinctReferenceValues()` (`DifferentialSweep.java:523`) counts the reference's answers
  over the measured rows and `Result.requireStagePass` (`:588`) refuses below
  `DISCRIMINATING_MINIMUM = 2` (`:332`) without a written, value-keyed sign-off.
* **no further than the inputs it tried** — **not enforced, not measured, not published** (D-30).
  Measured in round 5: a port carrying a real arithmetic defect confined to receiver value `42.0`,
  which no shipped corpus contains, is stage-pass-identical to a perfect port and prints
  `[DISCRIMINATING]` beside its agreement figure. This clause is discipline, and it is the one the
  instrument cannot keep for you.

---

## 2. Verdict vocabulary (`DiffVerdict.java`)

| Verdict | `isAgreement()` | `isMeasurement()` | Meaning |
|---|---|---|---|
| `AGREE` | **yes** | **yes** | Both sides returned a value; canonical forms identical — **including the Java type they were observed as** *(round 6, D-18)*. "Observed as" is now literal on both sides: `UValue.observedFrom(Object)` *(round 7, D-43)*. A subject whose adapter merely assumed its class can still agree by luck; that is why §7's third trap exists. |
| `ACCEPTED_THROW` | **yes** | no | Both threw, and a caller-supplied `AcceptedThrowPairs` allowlist names this exact pair — operation, both classes, both messages — with a written rationale. Opt-in; the default is empty; **no sign-off exists anywhere in the tree today.** |
| `DIFFER` | no | **yes** | Both returned a value; canonical forms differ. |
| `BOTH_THREW` | no | no | Both threw, unadjudicated — **whether or not the classes match.** Note carries both classes *and* both messages. |
| `MIXED` | no | no | One side returned a value, the other threw. The note names **which** side. |
| `UNMEASURABLE` | no | no | Neither side carried an observation: a `void` operation, or both sides `NULL`. Never raised when only *one* side lacks an observation — that is `DIFFER`. |
| `UNSUPPORTED` | no | no | At least one side could not be driven: the candidate does not declare the operation, or the receiver type is unmarshallable. |
| `HARNESS_ERROR` | no | no | The harness failed before any comparable value existed: marshalling, unwrapping, or a candidate returning Java `null`. |

`AGREE_THROWN` and `DIFFER_THROWN` **do not exist** (deleted in `e8b73e48`). Any document naming
them describes pre-`e8b73e48` code.

Two agreements, two measurements, and they are **different pairs**. `ACCEPTED_THROW` is an agreement
a human authored and the harness measured nothing. `DIFFER` is a measurement that is not an
agreement. **`measurementCount()` is the size of the evidence; `rowCount()` is not.** A 471 471-row
sweep against a subject whose every body throws contains *zero* measurements.

---

## 3. The two metrics

| Metric | Method | What it answers |
|---|---|---|
| **Evidence size** | `measurementCount()` — `AGREE` + `DIFFER` rows | How many times were two values actually compared? |
| **Discriminating power** | `distinctReferenceValues()` (`:523`), from `referenceValues()` (`:512`); `isDiscriminating()` (`:548`); `soleReferenceValue()` | Could the reference have said anything else? |

`referenceValues()` counts the **reference** column over **measured rows only**, under **exactly**
the equality the verdict uses (`:242`). Both ways of getting it wrong — counting the subject's
column, counting over all rows — are pinned by
`DifferentialHarnessRegressionTest.distinctReferenceValuesCountsTheReferenceOverMeasuredRows`. It
was recomputed independently from the committed golden data rows with `awk` in the round-5 static
review (258 for `s1-smoke-ureal-add`, 389 for `s1-smoke-ureal-minus-faulty`, both exact).

There is **no third metric for the input domain**, and there should be. See §5, D-30.

---

## 4. How a stage must gate on a sweep

### 4.1 Predicates that are NOT pass criteria

**`disagreements().isEmpty()`** is vacuously true of a sweep that compared nothing — an empty
domain, an all-`UNSUPPORTED` sweep, an all-`UNMEASURABLE` sweep. Never assert it.

**`isClean()`** (`:492`, `measurementCount() > 0 && disagreements().isEmpty()`) is better and is
still not a pass criterion; its own Javadoc says so. **One** measured row passes it (D-16), and it is
`true` for **119 of 285** operations against a subject consisting of one hardcoded literal per
operation. It stays in the API because it is the right question for the harness's own regression
tests, where the codomain is known by construction. It is **not** the question a stage asks.

**`requireMeasurements(int)`** is a floor, not a gate: it says nothing about degeneracy.

> **The gate is still opt-in, and the tree's own example now uses it** *(round 6, D-36)*. Nothing in
> the harness *forces* a stage through the gate: `isClean()`, `requireMeasurements(int)` and
> `disagreements().isEmpty()` all still return a clean-looking answer on a degenerate sweep. An
> earlier revision of this file claimed "a stage that forgets fails rather than passes".
> **That sentence was false and is withdrawn.** What is unavoidable is the *number*: `summary()`,
> `stageStatement()` and the `# op.<key>.*` header block carry it whether or not the caller gates on
> it. What changed in round 6 is the **worked example**: `UncertaintyDifferentialSmokeTest`, whose
> goldens are S1's committed evidence, gated on `isClean()` and now gates through
> `requireStagePass(ADD_FLOOR, none())` with the floor written above the run, plus the golden
> comparison, `throwClassMismatchCount() == 0` and `javaTypeMismatchCount() == 0`. It prints
> `isClean()` beside the gate's verdict
> rather than passing on it. **Copy that test, not this warning.**

### 4.2 The criterion a stage must use — one call

```java
result.requireStagePass(minimumMeasurements, acknowledgedDegenerateOperations);
```

Three clauses (`stageGateFailures`, `:604`); it throws with **every** failing clause and the numbers
behind it:

1. **`measurementCount() >= minimumMeasurements`**, the floor derived from the corpus and **written
   into the stage document before the run**. A floor chosen after seeing the run is not a floor. `0`
   is rejected outright.
2. **No row disagreed** — and every non-agreement verdict counts as a disagreement, `BOTH_THREW`,
   `HARNESS_ERROR`, `UNSUPPORTED` and `UNMEASURABLE` included. **Read D-29 in §5 before you write a
   stage around this clause: a perfect port fails it on 92 of 285 operations.**
3. **`distinctReferenceValues() >= 2`** — the sweep *could* have failed — **or** the operation
   carries a sign-off in `AcceptedDegenerateOperations`, keyed on the operation **and** the exact
   single canonical value, with a mandatory non-blank rationale copied into `stageStatement()` and
   into the report header. Since round 6 the canonical value in that key is **type-bearing**
   (`BOOLEAN(true)@Boolean`, not `BOOLEAN(true)`), so a sign-off also lapses if the operation starts
   answering with the right content in a different class.

Two checks the gate does not make and a stage still must:

4. **Byte-identical golden comparison** via `DiffReportWriter.assertMatchesGolden`, so any change in
   the numbers is a diff someone has to read and approve.
5. **`throwClassMismatchCount() == 0`**, or an explanation. A port that fails on the right rows with
   the wrong exception type leaves every other aggregate bit-identical to a correct port's.
6. **`javaTypeMismatchCount() == 0`** — rows on which the content matched and the Java class did not.
   Scored `AGREE` since round 8 (see §1 and §7), so they are invisible in `rows.agreement`,
   `rows.disagreement` and every `verdict.*` line, and this is the only number that carries them.
   **At S1 this is a figure to publish; from S4 it is a REQUIREMENT and a gate clause** — the dated
   obligation is in §7. `stageStatement()` prints it unconditionally, including when it is zero, so a
   stage cannot quote a pass without seeing it.

### 4.3 A worked stage gate, and the naive one that is not a gate

```java
// ---------- NOT a stage gate. Every line of this is satisfied by a port with no logic. ----------
DifferentialSweep.Result r = DifferentialSweep.sweep(op, domain, oracle, port);
assertTrue(r.disagreements().isEmpty());          // vacuous if nothing was compared
assertTrue(r.isClean());                          // true for 119 of 285 ops against 119 literals
DiffReportWriter.writeAll("s4-ureal-add.tsv", List.of(r), digests);   // no longer compiles: D-34
```

```java
// ---------- A stage gate. ----------
// Written in the stage document BEFORE the run:
//   floor = 500  (URealValue.add(value) draws 24x24 = 576 rows from uRealBoundaries())
//   sign-offs = none; add(value) is expected to be discriminating
static final int ADD_FLOOR = 500;

DifferentialSweep.Result r = DifferentialSweep.sweep(op, domain, oracle, port);

r.requireStagePass(ADD_FLOOR, AcceptedDegenerateOperations.none());   // clauses 1-3, or throws
assertEquals(0, r.throwClassMismatchCount(), r.summary());            // clause 5
assertEquals(0, r.javaTypeMismatchCount(), r.summary());              // clause 6 -- REQUIRED from S4

// The four figures that must appear in the stage document, from the harness, not by hand:
//   URealValue.add(value): 576 rows, 576 measured, 576 agreed, 0 disagreed,
//                          0 java-type mismatch(es), 164 distinct reference value(s) [DISCRIMINATING]
System.out.println(r.stageStatement(AcceptedDegenerateOperations.none()));

// ...plus the fifth figure the harness does NOT compute (D-30): state, in prose, which inputs the
// domain covered and which it did not. "24 uReal boundary receivers x 24 arguments; no value in
// [1,100] other than 2 and 100; no denormals" is a sentence a reader can check. "576 agreed" is not.

DiffReportWriter.assertMatchesGolden(
        DiffReportWriter.writeAll("s4-ureal-add.tsv", List.of(r), digests,
                                  AcceptedDegenerateOperations.none()),   // the only form there is
        Path.of("docs/port2/differential/s4-ureal-add.tsv"));             // clause 4
```

Two rules the example encodes:

* **`writeAll` and `write` both require the sign-off set, and there is no other form**
  *(round 6, D-34, closed)*. The 3-argument `writeAll` silently substituted
  `AcceptedDegenerateOperations.none()`, so a report could assert
  `# accepted.degenerateOperations 0` while the pass it documents was granted under a sign-off —
  measured: `stage pass WITH the sign-off? true` printed beside
  `# accepted.degenerateOperations 0`. The eliding overloads are deleted, and
  `DifferentialHarnessRegressionTest.aReportCannotUnderstateItsOwnSignOffs` asserts **reflectively**
  that no `write`/`writeAll` overload without the parameter ever comes back, because "all the call
  sites pass it today" is a fact about today.
* **Compare against a recorded baseline, not against `true`.** On the 92 operations where a perfect
  port already fails clause 2, `isStagePass` is `false` before and after a real infidelity (D-29).
  Record `stageGateFailures(...)` for the perfect-port baseline and diff against it; the *clause
  list* changes even when the boolean does not.

### 4.4 Signing off a genuinely-constant operation

Some operations really are constant by specification — `isUReal()` compiles to `iconst_1; ireturn`.
They are part of the ported surface and must not be deleted from the inventory (that hides the row
instead of classifying it, which is the mistake round 1 made with `void`):

```java
AcceptedDegenerateOperations.builder()
    .accept("URealValue.isUReal()", "BOOLEAN(true)@Boolean",   // the key is type-bearing: D-18
            "type predicate; the historical body is iconst_1/ireturn, so BOOLEAN(true) is the whole "
          + "of its specification. Agreement shows the operation exists and is reachable; it is not "
          + "evidence about any computation.")
    .build();
```

The key includes the value, so the sign-off **lapses by itself** if the operation ever answers
otherwise. A blanket "accept all type predicates" cannot be expressed. **Do not sign one off to make
a run pass:** the rationale has to say what a reader should *not* conclude, and it lands in the
evidence file.

### 4.5 What a stage must publish alongside any fidelity figure

Use `result.stageStatement(acknowledged)` (`:650`) — it cannot render an agreement figure without
the discrimination figure beside it.

```
URealValue.add(value): 576 rows, 576 measured, 576 agreed, 0 disagreed, 164 distinct reference value(s) [DISCRIMINATING]
URealValue.isUReal(): 24 rows, 24 measured, 24 agreed, 0 disagreed, 1 distinct reference value(s) [NOT DISCRIMINATING: always BOOLEAN(true)@Boolean; acknowledged: ...]
```

Never a bare agreement percentage. Never a file-level total: `# rows.*` and `# verdict.*` are sums
over every result in the file and hide an operation that measured nothing (D-21). Read the
`# op.<key>.*` block — and note that those keys are **not unique** if one report holds several
results for one operation (**D-41**, open, latent).

---

## 5. What the harness cannot measure — declared limits

These are properties of the instrument, not results. A stage needing one of them needs a different
instrument and must say so rather than report a number.

| Limit | Extent | Consequence |
|---|---|---|
| **Post-state** | all 8 `void` mutators, e.g. `setTypeToRuntimeType()` | The receiver is never re-read. Every row is `UNMEASURABLE` — never an agreement, so a port cannot be called faithful on them either. **A void operation cannot be shown faithful by this harness at all.** |
| **`SBooleanValue`** | 39 operations | Not in `MARSHALLABLE_RECEIVERS`; no `SBoolean` marshalling, no `UValue.Kind`. Reported `UNSUPPORTED`, never silently skipped. |
| **Collection receivers** | — | Out of reach for the same reason. |
| **`org.tzi.use.uml.ocl.type.*`, `uDataTypes.*`** | the whole type layer and the uncertainty library | Cannot be named as receivers. Observed only indirectly, as `OPAQUE` canonical strings built by field reflection when an operation returns one. **That string is not a free choice** *(round 6R, **D-44**)*: `UValue.opaque(className, repr)` puts the **fully-qualified** class name into the compared content and `HistoricalOracle.opaqueRepresentation` adds the FQNs of every field's declaring class and every field name, so on the `OPAQUE` branch a port that **relocated the package is a divergence** — **197 rows across 17 operations** (`type()` / `getRuntimeType()` × 16, `UIntegerValue.getuInteger()` × 1). The earlier wording here, "a port need only reproduce a string", understated it. This is the pre-existing `OPAQUE` limit, and it contradicts the rationale given for comparing the type token's *simple* name (next row). **Asserted since round 7** in `theTypeTokenIsPackageInsensitiveOnPurpose`: two `opaque()` values differing only in package have the *same* `typeToken()` and *different* `canonical()`, so package-insensitivity is a property of the **token**, never of the row. |
| **Operations not nameable as a `UOp`** | **33 of 318** public instance methods on the 8 marshallable receivers | No row, no verdict, not even an `UNSUPPORTED` marker. Includes **`equals(Object)` and `compareTo(Object)` on all eight receivers**, `UStringValue.indexOf(StringValue)`, and 16 `toString(StringBuilder)` / `toStringWithType(StringBuilder)`. **"285 operations" is not the ported surface; it is the surface this harness can name.** A port with a broken `equals` is invisible here. |
| **Primitive vs boxed results (D-18)** — **blindness closed in round 6; DEMOTED from a verdict to a counted dimension in round 8** | was 193 of 285 scoring `AGREE` invisibly; **182 of 285 / 3 445 rows** now measured | `canonical()` is type-bearing, so the two sides' classes are compared on every row and every difference is reported with both fully-qualified names in the note. **What changed in round 8:** where the content is identical and only the class differs, the row is `AGREE` and the difference is counted in `Result.javaTypeMismatchCount()` / `# rows.javaTypeMismatch` / `# op.<key>.javaTypeMismatch` / `stageStatement()`, because at S1 the ported token cannot be authentically observed and a type-only divergence therefore measures the adapter (**D-43**, next row). Measured: a port that boxes every raw result into its `Value` class produces **0 `DIFFER`, 74 stage passes and 3 445 `javaTypeMismatch` rows across 182 of 285 operations**; before D-18 it produced **no signal at all** in any figure the harness published, and between rounds 6 and 8 it produced 3 445 `DIFFER` and cost 29 stage passes. **So this is a live limit again, and a narrower one:** a type-only infidelity does not fail a stage gate at S1. **§7's dated REQUIREMENT closes it at S4**, where the adapter can observe. Two further residual **costs**. (a) The compared token is the class's *simple name*, so two distinct classes sharing one simple name compare equal — deliberate, because the historical side is loaded from a vendored jar by an isolated loader and a relocated port must not read as 100 % divergence; note that the `OPAQUE` branch does not honour that rationale (**D-44**, row above). (b) `UnwrittenPortInvariantTest`'s receiver-echoing subject regains five payload-only fully-agreed operations as a direct consequence — signed off with the reason, and each sign-off asserts that *all* of its agreement rows are java-type mismatches. |
| **The ported side's Java type cannot be authentically observed at S1 (D-43)** — half (a) **CLOSED round 7**, half (b) **CLOSED round 8 by removing the API** | 182 of 285 operations / 3 445 rows for a non-attributing adapter; the ambiguity is total until S4 observes | `fromHistorical` observes the reference's class on every branch; **the ported side has the same call** — `UValue.observedFrom(Object)` reads `getClass().getName()` off the object the port returned. **Both stating routes are now deleted:** round 6's `asJavaType(String)` and round 7's `declaredJavaType(String, String)`. There are **two** provenances, `OBSERVED` and `ASSUMED` (plus `NONE` for the absence of a result), and an adapter author chooses neither; a reflection test over `UValue`'s public surface pins that the only String-taking members are `uString` / `string` / `opaque`, whose String is **content**. Round 7's compensating disclosure was measured false and both it and its claim are withdrawn (§7). Measured after the demotion, four subjects over the same 285 operations: content-perfect port + **factory-typed** adapter `DIFFER 0 / 0 divOps / 74 passes / 3 445 javaTypeMismatch / 3 445 notes say ASSUMED`; the **same** port + **observing** adapter `DIFFER 0 / 0 / 74 / 0`; planted **wrong-class** port `DIFFER 0 / 0 / 74 / 3 445 javaTypeMismatch / 0 notes say ASSUMED`. **What remains a limit, and it is the reason for §7's requirement:** the 3 445 is identical for a port with no defect and a port with a real wrong-class infidelity whenever the adapter does not attribute — round 7 measured the pre-demotion form of that as 19 083 of 19 083 rows byte-identical — so the *only* discriminator is the row note's provenance clause. The token is also only as honest as the adapter's *choice of object*: `observedFrom` believes what it is handed, which is not checkable and which the note now says outright instead of certifying the opposite. **Read the third trap in §7 before writing an adapter, and state in the stage document how the token was obtained.** |
| **Single-valued operations (D-15, enforced)** | **159 of 285** against a perfect port (census 11 measured-nothing / 159 single-valued / 115 discriminating) | Agreement is decided before either implementation runs. The gate refuses them; a sign-off is per operation and per value. |
| **Zero-measurement operations (D-19, closed as a gap, standing as a limit)** | **11 of 285** | The 8 void mutators plus `UIntegerValue.power(value)`, `UStringValue.toInteger()` and `UStringValue.toReal()`, which throw on every input the corpora hold. |
| **Input-domain coverage (D-30, open, MAJOR)** | unmeasured everywhere | `distinctReferenceValues()` measures the **codomain**; its dual — how much of the input domain was reached — is computed nowhere, published nowhere, gated nowhere. Measured: P2's real arithmetic defect restricted to receiver `42.0` produced **0 `DIFFER` rows** and a byte-identical tally to a perfect port on all 19 083 rows, and all four affected operations reached a full stage pass. The same in miniature at `URealValue.round()` for a `-0.0 → 0.0` collapse that *is* caught on `floor()`, `neg()` and `mult(value)`. |
| **Corpus depth decides the census (D-28 / D-31, open, MINOR)** | 23 `RealValue.*` operations; the string-index family | The corpora hold exactly one `RealValue` (`REAL(0.0)`), so all 23 of its operations are single-valued *by arithmetic*. `indexBoundaries()` was drawn for `at(int)`: `uSubstring(int,int)` is **17 measured rows of 432** against a perfect port. **"159 single-valued" is a joint fact about the implementation and the corpus.** |
| **The gate is not satisfiable by fidelity (D-29, open, MAJOR)** | 92 of 285 | A perfect port reaches `isStagePass(1, none())` on **74 of 285**; 119 are refused by clause 3 (D-15, as designed) and **92 by clause 2**, for `BOTH_THREW` / `HARNESS_ERROR` / `UNMEASURABLE` rows a faithful port cannot avoid. The only route out is **154** hand-authored `AcceptedThrowPairs` entries keyed on both messages verbatim. On those 92, an infidelity changes the rows and the counts **but not the pass bit** — measured on 10 (defect, operation) pairs, the entire off-by-one probe among them. |

---

## 6. The standing invariants, and which door each closes

Run by `mvn -B verify -Djava.awt.headless=true`. If you change the harness these are what must still
pass; if one starts failing, read it before you "fix" it.

| Invariant | The door it closes |
|---|---|
| `HistoricalOracleIsolationTest` (9 methods) | The harness comparing the port against **itself**. It caught a real one: a platform-parented `URLClassLoader` is not isolated in this reactor. Pins both jar digests; a missing or altered jar fails loudly. |
| `UnwrittenPortInvariantTest.anUnwrittenPortAgreesWithNothing` — 7 subjects (throws, Java `null`, empty body, `nullValue()`, fixed constant, echoes receiver, throws `Error`) | **D1 / D2 / D-10.** A port that does not exist scoring agreement. Total agreement rows == 0 for the five non-observing subjects, and — the sharper one — no operation is agreed on every driven row without a written review entry, asserted **for both halves of the split** since round 6: `reviewedFullyAgreed` for the discriminating half (a finding about the subject) and `reviewedDegenerateFullyAgreed` for the single-valued half (a finding about the corpus). `0a93ad4f` had left the second printed and unasserted (**D-35**, closed); measured **on the tree at `90404528`** — not "unmodified HEAD", which carries the assertion and passes; corrected on the refuter's finding — restoring it catches `RealValue.value()`, an operation a receiver-echoing subject was fully agreed with, on a run that tree's own test passes. All fourteen buckets are empty today and all fourteen are asserted: a live guard against future growth that proves nothing about today. |
| `UnwrittenPortInvariantTest.aNoLogicPortCannotProduceAStagePass` | **D-15.** A subject of one hardcoded literal per operation, over **stage-shaped** domains: the attack still lands (119 clean-and-degenerate), all 119 are refused, **no** operation reaches a stage pass, and a faithful port on a discriminating operation still passes `requireStagePass(100, none())` — the control that stops the gate degenerating into blanket refusal. |
| `UnwrittenPortInvariantTest.aDegenerateOperationNeedsAWrittenSignOff` | The sign-off route in both directions and its exactness in both key positions: a rationale against a different value, or a different operation, does not match; a blank rationale is rejected. |
| `PortedInfidelityDetectionPowerTest.aWrongJavaTypeWithRightContentIsCountedNotScored` (round 6, demoted round 8) | **D-18.** A perfect port that boxes every raw result into its `Value` class must show **3 445 java-type mismatches across 182 of 285 operations** — and **0 `DIFFER`**, **0** stage passes lost, because at S1 that difference is not attributable to the port (D-43). The identity control over the same inventory must show `0` in both dimensions, so the type-bearing form cannot have become a false-alarm generator unnoticed. Also asserts the rows still *show* both fully-qualified classes, so the demotion did not discard the observation. **Never quote it without the method next door** — its Javadoc says so, and the two are adjacent for that reason. |
| `PortedInfidelityDetectionPowerTest.aFactoryTypedAdapterCostsNoPassAndIsCountedNotScored` *(round 7, rewritten round 8)* | **D-43, both readings of the same number, and the demotion.** Four subjects, one run: the control; the planted wrong-class port; a **content-perfect** port with a **factory-typed** adapter; and the same port with an **observing** adapter. Asserts that the adapter defect produces **0 `DIFFER`**, **0** diverging operations and the control's **exact** stage-pass set — it loses none, where before round 8 it lost 29 — and that its **3 445** rows appear in `javaTypeMismatchCount()` instead, on **exactly** the same operations as the planted defect's 3 445, which is the identity that makes the figure a measurement and not an attribution; that the observing adapter measures **0** in that dimension and the control's exact verdict tally; that the notes are the only thing separating the two (3 445 rows say `subject ASSUMED`, 0 rows of the real defect do); and it **recounts** the population independently from the rows and asserts the harness's own count equals the recount. If the two 3 445s ever stop being equal, the record's account of rounds 6–8 is stale and must be re-read. |
| `DifferentialHarnessRegressionTest.theTypeTokenIsObservedOrAssumedAndNoApiTakesOne` *(round 7, rewritten round 8)* | **D-43 at unit resolution, and it pins the shape of the API and not only the values.** `observedFrom` derives the token from `getClass().getName()` and a boxed primitive gives exactly what the reference observes; the factory default is `ASSUMED`; `TypeProvenance` has **exactly** `OBSERVED` / `ASSUMED` / `NONE`; **no public member of `UValue` accepts a class name** — checked by reflection over the whole public surface, because both deleted escape hatches were ordinary-looking public methods and a grep is not a mechanism, with `uString` / `string` / `opaque` enumerated and justified as content-taking; both provenances reach the note on every type-mismatch row whatever they are, and neither reaches `canonical()` or the verdict. Also pins that the harness does **not** certify a fabricated observation (D-47). |
| `PortedInfidelityDetectionPowerTest.noOperationAnswersWithTwoRuntimeClasses` (round 6) | **D-18's premise.** No operation answers with two different runtime classes (0 of 285, measured against the reference alone), which is what makes "the port used the other class" a defect rather than a representation choice over the shipped corpora. Fails if a widened corpus ever creates a genuine ambiguity. **The Javadoc's *reason* was false and the measurement was not** *(round 6R, **D-45**; the Javadoc was corrected in round 7)*: 84 of 285 operations declare an interface or a non-final class, so more than one runtime class is legal by the API — the nine `UncertainBooleanValue`-declared operations return a subclass through a superclass-declared signature, and a port returning the *declared* class would read as divergence on every driven row while breaking no contract. The premise is a **corpus fact, not a language fact**, it inherits D-30, and the corrected Javadoc says so; do not repeat "a declared return type is one class" in a stage document. |
| `DifferentialHarnessRegressionTest.aReportCannotUnderstateItsOwnSignOffs` (round 6) | **D-34.** The header carries the sign-off count and the rationale verbatim, the two headers of one sweep under `signed` and under `none()` are asserted unequal, and no `write`/`writeAll` overload omitting the set may exist — checked reflectively. |
| `DifferentialHarnessRegressionTest.rightContentInTheWrongJavaTypeIsADifference` / `theTypeTokenIsPackageInsensitiveOnPurpose` (round 6) | **D-18 at unit resolution.** Both shapes of "wrong type" — a `Kind` difference (always caught) and a runtime-class difference (the defect) — plus the note naming both FQNs, `NULL`/`VOID` staying bare, and the stated cost of comparing the simple name. |
| `PortedInfidelityDetectionPowerTest` (4 methods, `f438a365`) | **Detection power itself.** A second, independently loaded `HistoricalOracle` plays a perfect port; a `MutantPort` applies one named infidelity. Asserts the control diverges nowhere, and asserts the set of planted (defect, operation) pairs the instrument **cannot** see **as an exact set**, so that blindness cannot grow or shrink silently. |
| `DifferentialHarnessRegressionTest.distinctReferenceValuesCountsTheReferenceOverMeasuredRows` | The metric, against the two mistakes that would make it useless: counting the **subject's** column, and counting over **all** rows. *(The Javadoc's "169 distinct marker strings" is wrong — all 169 rows carry one identical string, so the pin holds at 1-vs-0, not 169-vs-0. **D-39**, open.)* |
| `DifferentialHarnessRegressionTest.theStageGateRefusesADegenerateOperation` | The gate's three clauses separately and together, a floor of zero rejected, and the sign-off opening it. |
| `DifferentialHarnessRegressionTest.theReportHeaderCarriesDiscriminatingPowerPerOperation` | That the number reaches the artefact a human reads, per operation. |
| `DifferentialHarnessRegressionTest.goldenComparisonIsBytesAndNotLines` | That `assertMatchesGolden` compares **bytes**: a trailing-newline difference and a CRLF substitution both fail, with `Files.readAllLines` asserted equal first as the precondition. |
| `DifferentialHarnessRegressionTest` (26 methods) | Each closed door pinned individually: `d1TypeMismatchIsNotAgreement`, `twoThrowsAreNeverAgreementAndNeverLoseTheirMessages`, `d10VoidVersusVoidIsNotAgreement`, `oneSidedAbsenceIsADifferenceAndKeepsItsEvidence`, `zeroRowSweepIsRefused`, `aReportWithNoMeasurementsIsRefused`, `wrongThrowClassIsVisibleInAnAggregate`, `acceptedThrowPairsAreOptInAndExact`, plus the note-content pins that stop evidence being destroyed. |
| `UncertaintyDifferentialSmokeTest` (6 methods) + `assertMatchesGolden` | Non-determinism and any unannounced output change. Goldens under `docs/port2/differential/` compare byte for byte; refresh only with `-Duse.differential.golden.refresh=true`, deliberately, in a commit that says why. **Since round 6 this test gates through `requireStagePass` (D-36, closed) and IS the stage template** — floor written above the run, `isClean()` printed but not asserted, the negative direction asserting which clause refuses, and the input domain stated in prose in the class comment. |
| `everyKindIsEitherAnObservationOrUnmeasurable` | *Intended* to catch a new value-less `UValue.Kind` becoming a route to `AGREE`. **Do not rely on it: it is tautological (D-20, open).** Treat a new `Kind` as requiring manual review. |

---

## 7. Three traps specific to writing an S4 adapter

* **Do not reach for `HarnessMarshallingException` as a fallback.** `Candidate`'s Javadoc tells you
  to throw it when the adapter genuinely cannot marshal, and that is correct — but `HARNESS_ERROR`
  and `UNSUPPORTED` are excluded from the per-operation `driven` denominator, so a port answering
  "could not marshal" wherever it would be wrong is scored fully agreed by the *invariant predicate*
  (D-17). It buys **no stage pass**: clause 2 refuses `HARNESS_ERROR`, and round 5 measured zero
  stage passes on all four operations of such a subject (D-32). What it destroys is **attribution** —
  the reader is told the harness could not drive 143 rows, not that the port is wrong on them.
* **Do not sign off `AcceptedThrowPairs` in bulk.** A perfect port needs **154** distinct entries
  before clause 2 could be met on the operations that throw (D-29). That pressure is exactly how the
  deleted blanket throw-agreement rule would come back. Each entry needs the operation, both classes,
  both messages and a written rationale, reviewed one at a time.
* **Attribute the Java class your port actually returned: `UValue.observedFrom(theObjectItReturned)`.
  The factories do not do it for you** *(round 6R **D-43**, mechanism landed round 7)*. The harness
  observes the reference's class (`fromHistorical` reads `result.getClass().getName()`); your adapter
  must make the same kind of statement about its side, or the two halves of the comparison are not the
  same question. `UValue.uReal(...)`, `UValue.bool(...)` and the other factories type the value as the
  `Value` class of its kind — provenance `ASSUMED` — which is **wrong for 182 of 285 operations**,
  because most of the enumerated surface returns a raw `boolean`, `int`, `double` or `String`
  (140 / 18 / 6 / 18 declarations respectively, measured through the isolated loader). One line fixes
  it:

  ```java
  Object returned = portMethod.invoke(receiver, marshalledArgs);   // or a direct call
  if (returned == null) {
      return UValue.nullValue();                                    // no class to observe
  }
  return UValue.uReal(v, u).observedFrom(returned);                 // OBSERVED, not declared
  ```

  A primitive needs nothing special: reflection and autoboxing both hand you a `java.lang.Boolean` for
  a `boolean`, which is exactly what the reference observes. The two measured consequences of getting
  this wrong, and note that `StubCandidate` — the only worked example — leaves its class at the factory's
  **assumption**, because S1 has no ported object in existence, and says so at the call site:
  * a **content-perfect** port with a factory-typed adapter measures **3 445 rows on which the two
    sides name different classes for identical content, across 182 of 285 operations**,
    `URealValue.value()`, `URealValue.uncertainty()`, `UIntegerValue.value()` and
    `UIntegerValue.uncertainty()` among them — the *same figure* as the planted wrong-class defect, from
    a port with no defect in it. Before round 8 those were `DIFFER` rows and cost it 29 stage passes;
    they are now counted in `javaTypeMismatch` and cost none;
  * **the incentive hazard, which is how this defect did its damage twice: an S4 author who clears
    those spurious rows by naming a type destroys the check** — measured, the same wrong-class port plus
    one line stating the reference's token went to **0 `DIFFER`** under round 6's API *and* under round
    7's. That is the same pressure as bulk throw-pair sign-off, one bullet up, and it is why the API to
    name a type no longer exists.

  **Round 7's compensating disclosure was refuted, and both the mechanism and the claim are gone.**
  This paragraph used to read: *"the only way to state a class is `declaredJavaType(String javaType,
  String why)`, a blank `why` is rejected, and the reason is printed into the note of any row the
  declaration moved."* Round 7's refutation measured the last clause **false in the only direction that
  matters**: `declaredJavaType(referenceToken, "x")` on a genuinely wrong-class port produced a sweep
  **byte-identical to the perfect-port control**, and the mandated reason appeared in **0 rows** —
  because the type note fires only when the two class names *differ*, which a laundering declaration
  makes false by construction. The disclosure spoke when a declaration *created* a difference and was
  silent when it *erased* one. **Round 8 deleted `declaredJavaType` rather than patching it a third
  time** (`asJavaType(String)` was the first). There are now exactly **two** states and an adapter
  author chooses neither: `OBSERVED`, if you route through `UValue.observedFrom(Object)`, or `ASSUMED`,
  the factory default, if you do not. With nothing to declare there is nothing to declare falsely.

  **And a type-only difference is no longer scored.** Where the content matches and only the class
  differs, the row is `AGREE` and is counted in `Result.javaTypeMismatchCount()`, published as
  `# rows.javaTypeMismatch`, `# op.<key>.javaTypeMismatch` and in `stageStatement()`. The reason is
  dated and narrow: **at S1 the ported side's token cannot be authentically observed**, because no
  ported value class exists in `use-core/src/main` to observe — writing them *is* S4 — so a type-only
  divergence measures the adapter and not the port. Measured, 285 operations / 19 083 rows: a
  **content-perfect** port with a factory-typed adapter now scores `DIFFER 0`, `0` diverging operations
  and the control's **74** stage passes, with its **3 445** rows in `javaTypeMismatch`; before round 8 it
  scored `DIFFER 3 445 / 182 ops / 45 passes`. Content differences are untouched.

  So: **derive the token from the object your port returned, in one place in the adapter; and state in
  the stage document how the token was obtained** before quoting any figure a type difference could
  move. A row whose note says `subject ASSUMED` is a finding about your adapter, and the harness says so
  on every such row.

### REQUIREMENT on S4 — dated obligation, 2026-08-17

> **Once real ported value classes exist in `use-core/src/main` — which is what stage S4 writes — S4
> MUST (a) route its adapter's every result through `UValue.observedFrom(theObjectItsPortReturned)`, and
> (b) add `assertEquals(0, result.javaTypeMismatchCount(), result.summary())` as a gate clause beside
> `throwClassMismatchCount() == 0`.**
>
> **Why this is a requirement and not advice.** The demotion above is not a judgement that the Java type
> does not matter; it is a statement that at S1 the harness cannot attribute a type difference. The
> measurement that forces the obligation: a port with **no defect at all** and a non-attributing adapter,
> and a port carrying a **real** 401-row wrong-class infidelity seen through the same non-attributing
> adapter, produce **the same 3 445** in `javaTypeMismatch` — round 7 measured the pre-demotion form of
> this as 19 083 of 19 083 rows byte-identical, notes included. The **only** discriminator is the row
> note's provenance clause. That ambiguity exists *because* the adapter does not observe, and it
> disappears the moment it does: the same content-perfect port with an observing adapter measures **0**,
> and a real wrong-class port measures the operations it is actually wrong on (round 7: 401 rows / 9
> operations, against 3 445 / 182 by omission). So `observedFrom` is what makes the number attributable,
> and once it is attributable a non-zero value is a port defect and must fail the gate.
>
> **How a reviewer checks this was done.** The S4 document must quote `stageStatement()` verbatim — it
> carries `N java-type mismatch(es)` unconditionally — and the report header's `# rows.javaTypeMismatch`.
> A stage that quotes a pass without that figure has not gated on it. **Reject a type-fidelity figure
> from an adapter whose attribution route is not stated.**
>
> Until that is done the demotion stands and must be stated as a limit wherever an agreement figure from
> this harness is quoted: **an `AGREE` row may be an agreement on the payload alone.**

---

## 8. The question to ask when you extend this

Every round the harness was fixed to stop making one false claim, and the next reviewer found a
different construction producing the same false claim. Round 1: harness failure == agreement.
Round 2: two throws == agreement. Round 3: two `VOID`s == agreement. Round 4: two equal values over a
one-valued codomain == fidelity. Round 5: no scoring defect at all — the limits are the **domain**
that was never swept, the **operations that cannot be named**, and the **reports** that read stronger
than the runs behind them. Round 6: the same content in a **different Java class** == agreement, on
193 of 285 operations of a port whose entire subject is four new value classes — the harness compared
the payload and called it the value. Round 6's refutation: the fix for that produced a number a
**faithful** port reproduces exactly, because half of what it compares is **declared by the thing under
test** instead of observed by the instrument (D-43). Round 7's answer was not to soften the check but to
make the other half a measurement too — and then to pin *both* readings of the number as adjacent tests,
because the failure was that one reading had been written down and the other had not. So when you extend
this, ask the question of the fix as well as of the row: *what does a perfect port measure under this
rule, and is that different from what a defective one measures?* — and if it is not, say so in a test
next to the one that quotes the number.

So the question is not "is this row correct?" — in rounds 4 and 5 every row was. It is:

> **Could this sweep have failed? What would have had to be different for it to report a
> divergence — in the reference's answers, and in the inputs we chose?**

The first half is a number the harness computes and the gate acts on. **The second half is yours.**
`DISCRIMINATING_MINIMUM = 2` is a threshold, and a threshold is a place to stand, not a proof: an
operation whose range is exactly `{true, false}` clears it and is still nearly free for a subject
echoing one bit (`BooleanValue.value()` and `BooleanValue.isTrue()` sit at exactly 2 today). Look at
the number; do not just compare it to 2.
