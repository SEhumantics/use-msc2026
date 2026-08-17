# The differential harness — contract for S4–S7

**Status: 2026-08-17, after the round-4 fixes (`0a93ad4f`). Binding on every stage that quotes a
differential number.** The harness is
`use-core/src/test/java/org/tzi/use/uncertainty/differential/`. Its narrative record, the canonical
defect register and the mapping from the colliding per-report defect ids are in
[`stage-01.md`](stage-01.md) **§11**, which supersedes §10. This file is the short normative version:
the rules, not the story.

Read this before writing a sweep. Four rounds of review each found a new way to make the harness
claim fidelity it had not measured; every rule below exists because one of them succeeded.

**What changed in this revision.** The rule that used to live in §5 as *discipline* — "never quote a
per-operation agreement figure without also quoting distinct reference values" — is now a
**mechanism**. `DifferentialSweep.Result` computes the number and
`Result.requireStagePass(int, AcceptedDegenerateOperations)` refuses a pass without it. Wherever this
document previously told you to derive something by hand, it now tells you which method to call, and
a stage that forgets fails rather than passes.

---

## 1. The principle

> **A differential oracle may report agreement only where it observed two comparable,
> non-degenerate values.**

Three clauses, each paid for:

* **two** — not one. A row where the harness failed to marshal, or where one side never ran, is the
  *absence* of a measurement, and the absence of a measurement is not a measurement the two sides
  happen to share. (D1: 21 816 rows of this were scored green.)
* **values** — not throws, and not the encodings of "no result". Two throws are not a shared value
  however well their class names match (D2). Two `VOID`s are not a shared value (D-10).
* **non-degenerate** — the operation must be *able* to answer differently. Two equal values over an
  operation with a single-point codomain are green by construction, not by measurement (D-15). This
  clause used to be the one the harness did not enforce. It now does:
  `Result.distinctReferenceValues()` counts the reference's answers over the measured rows, and
  `Result.requireStagePass` refuses when that count is below `DISCRIMINATING_MINIMUM` (2) unless the
  operation carries a written, value-keyed sign-off in `AcceptedDegenerateOperations`. Measured: a
  subject of one hardcoded literal per operation produces **119** fully measured, zero-disagreement,
  `isClean() == true` sweeps and **0** stage passes.

---

## 2. Verdict vocabulary (`DiffVerdict.java`)

| Verdict | `isAgreement()` | `isMeasurement()` | Meaning |
|---|---|---|---|
| `AGREE` | **yes** | **yes** | Both sides returned a value; canonical forms identical. |
| `ACCEPTED_THROW` | **yes** | no | Both threw, and a caller-supplied `AcceptedThrowPairs` allowlist names this exact pair — operation, both classes, both messages — with a written rationale. Opt-in; the default allowlist is empty; **no sign-off exists anywhere in the tree today.** |
| `DIFFER` | no | **yes** | Both returned a value; canonical forms differ. |
| `BOTH_THREW` | no | no | Both threw, unadjudicated — **whether or not the classes match.** Note always carries both classes *and* both messages. |
| `MIXED` | no | no | One side returned a value, the other threw. |
| `UNMEASURABLE` | no | no | Neither side carried an observation: a `void` operation, or both sides `NULL`. Never raised when only *one* side lacks an observation — that is `DIFFER`, and the note keeps both canonical forms. |
| `UNSUPPORTED` | no | no | At least one side could not be driven: the candidate does not declare the operation, or the harness cannot marshal the receiver type. |
| `HARNESS_ERROR` | no | no | The harness failed before any comparable value existed: marshalling, unwrapping, or a candidate returning Java `null`. |

`AGREE_THROWN` and `DIFFER_THROWN` **do not exist**; they were deleted in `e8b73e48`. Any document
naming them describes pre-`e8b73e48` code.

Two agreements, two measurements, and they are different pairs. `ACCEPTED_THROW` is an agreement a
human authored and the harness measured nothing. `DIFFER` is a measurement that is not an agreement.
**`measurementCount()` is the size of the evidence; `rowCount()` is not.** A 471 471-row sweep
against a subject whose every body throws contains *zero* measurements.

---

## 3. What the harness cannot measure — declared scope boundaries

These are limits of the instrument, not results. A stage that needs one of them needs a different
instrument, and must say so rather than report a number.

* **Post-state.** The harness never re-reads the receiver after a call. Any effect an operation has
  on its receiver is invisible. Consequently **all 8 `void` operations** (e.g.
  `setTypeToRuntimeType()`) are `UNMEASURABLE` on every row, by design. **A void operation cannot be
  shown faithful by this harness at all.**
* **`SBooleanValue` — all 39 operations.** Not in `MARSHALLABLE_RECEIVERS`; the harness has no
  `SBoolean` marshalling and no `UValue.Kind` for it. Reported `UNSUPPORTED`, never silently skipped.
* **Collection receivers.** Out of reach for the same reason.
* **`org.tzi.use.uml.ocl.type.*`** (the `Type` hierarchy) and **`uDataTypes.*`** (the underlying
  uncertainty library). Unreachable by design — they cannot be named as receivers or
  isolation-checked. They *are* observed indirectly, as `OPAQUE` canonical strings built by field
  reflection, when an operation returns one.
* **Primitive vs boxed results.** `fromHistorical` maps a raw `Boolean`/`Integer`/`Double`/
  `CharSequence` to the same `UValue.Kind` as `BooleanValue`/`IntegerValue`/`RealValue`/`StringValue`.
  On **193 of the 285** reachable operations a port returning the right content with the wrong Java
  type is scored `AGREE` (defect D-18). Two of these are pinned on `ECHO_SUBJECT_REVIEWED`; the other
  191 are not.
* **Reachable receivers are 8:** `URealValue`, `UIntegerValue`, `UBooleanValue`, `UStringValue`,
  `RealValue`, `IntegerValue`, `BooleanValue`, `StringValue`, and all eight now have a corpus.
  D-19 is closed: `InputGenerator.booleanBoundaries()` and `stringBoundaries()` were added, and
  **operations producing zero measurements against a perfect port went 61 → 11**. The 11 are the
  8 declared-`void` mutators (above) plus `UIntegerValue.power(value)`, `UStringValue.toInteger()`
  and `UStringValue.toReal()`, which throw on every input the corpora hold. Widening also enlarged
  the single-valued population 121 → 159, exactly as round 4 predicted; that is the correct trade
  only because every one of them is now labelled.
* **Corpus depth is uneven, and it decides the census (D-28, open).** The corpora contain exactly
  **one** `RealValue` — `REAL(0.0)`, from `zeroDivisors()` — so all 23 `RealValue.*` operations have
  a one-point codomain by arithmetic and nothing about any of them can be measured. `IntegerValue`
  has 8 receivers, all index boundaries drawn for another purpose. **"159 single-valued" is a joint
  fact about the implementation and the corpus, not a fact about the implementation.** Read
  `# op.<key>.distinctReferenceValues` per operation; do not quote the aggregate.

---

## 4. The standing invariants, and what each would catch

Run by `mvn -B verify -Djava.awt.headless=true`. If you change the harness, these are what must still
pass; if one starts failing, read it before you "fix" it.

| Invariant | What it would catch |
|---|---|
| `HistoricalOracleIsolationTest` (9 methods) | The harness comparing the port against **itself**. It caught a real one: a platform-parented `URLClassLoader` is not isolated in this reactor. Also pins both jar digests and that a missing or altered jar fails loudly. |
| `UnwrittenPortInvariantTest.anUnwrittenPortAgreesWithNothing` — 7 subjects (throws, Java `null`, empty body, `nullValue()`, fixed constant, echoes receiver, throws `Error`) | A port that does not exist scoring agreement. Two assertions: total agreement rows == 0 for the five non-observing subjects, and — the sharper one — **no operation is agreed on every row the harness could drive**. The second is what turns "444 of 471 471, looks like noise" into "144 of 144, every reachable row". The fully-agreed set is now **split**: *discriminating* operations are a finding about the **subject** and must be signed off one at a time in `reviewedFullyAgreed`; *non-discriminating* ones are a finding about the **corpus** and are labelled, not signed off. |
| `UnwrittenPortInvariantTest.aNoLogicPortCannotProduceAStagePass` | **The D-15 shape.** A subject whose every body is one hardcoded literal, driven over **stage-shaped** domains (each operation over its own receiver corpus — what S4 will do, unlike the union-domain sweep above). Four assertions: the attack still lands (`isClean()` and degenerate on 119 operations — if this ever reaches 0 the test has stopped testing anything); every one of those is refused by `requireStagePass`; **no** operation reaches a stage pass; and, the control that stops it being a blanket refusal, a faithful port on a discriminating operation passes `requireStagePass(100, none())`. |
| `UnwrittenPortInvariantTest.aDegenerateOperationNeedsAWrittenSignOff` | The sign-off route, both directions, and its exactness in both key positions: a rationale written against a different value, or a different operation, does not match; a blank rationale is rejected. |
| `DifferentialHarnessRegressionTest.distinctReferenceValuesCountsTheReferenceOverMeasuredRows` | The statistic itself, against the two mistakes that would make it useless: counting the **subject's** column, and counting over **all** rows (169 `HARNESS_ERROR` rows carry distinct marker strings and would look richly discriminating — measured as 0). |
| `DifferentialHarnessRegressionTest.theStageGateRefusesADegenerateOperation` | The gate's three clauses separately and together, a floor of zero being rejected, and the sign-off opening it. |
| `DifferentialHarnessRegressionTest.theReportHeaderCarriesDiscriminatingPowerPerOperation` | That the number reaches the artefact a human reads, per operation — asserting `# rows.disagreement 0` **first**, because that is what the file used to say and all it used to say. |
| `DifferentialHarnessRegressionTest.goldenComparisonIsBytesAndNotLines` | That `assertMatchesGolden` really compares bytes: a trailing-newline-only difference and a CRLF substitution both fail, with `Files.readAllLines` asserted **equal** first as the precondition. |
| `DifferentialHarnessRegressionTest` (26 methods) | Each closed door, pinned individually: `d1TypeMismatchIsNotAgreement`, `twoThrowsAreNeverAgreementAndNeverLoseTheirMessages`, `d10VoidVersusVoidIsNotAgreement`, `oneSidedAbsenceIsADifferenceAndKeepsItsEvidence`, `twoNullValuesAreNotAgreementEither`, `zeroRowSweepIsRefused`, `aReportWithNoMeasurementsIsRefused`, `zeroMeasurementSweepsCannotReadAsSuccess`, `wrongThrowClassIsVisibleInAnAggregate`, `acceptedThrowPairsAreOptInAndExact`, plus the note-content pins (`harnessErrorNoteCarriesBothSides`, `mixedNoteNamesBothSides`, `unsupportedNoteAttributesEachSide`) that stop evidence being destroyed. |
| `UncertaintyDifferentialSmokeTest` (6 methods) + `DiffReportWriter.assertMatchesGolden` | Non-determinism, and any unannounced change in output. The goldens under `docs/port2/differential/` are compared **byte for byte**; a golden may only be refreshed with `-Duse.differential.golden.refresh=true`, deliberately, in a commit that says why. |
| `everyKindIsEitherAnObservationOrUnmeasurable` | *Intended* to catch a newly added value-less `UValue.Kind` becoming a route to `AGREE`. **Do not rely on it: it is tautological (defect D-20)** — it branches on `carriesAnObservation()` and asserts the verdict `classify` derives from that same predicate. Treat a new `Kind` as requiring manual review. |

---

## 5. **How a stage must gate on a sweep**

### 5.1 Two predicates that are NOT pass criteria

**`disagreements().isEmpty()`** is vacuously true of a sweep that compared nothing: an empty input
domain, an all-`UNSUPPORTED` sweep, an all-`UNMEASURABLE` sweep. Do not write it as an assertion.

**`isClean()`** — `measurementCount() > 0 && disagreements().isEmpty()` — is better and is still not
a pass criterion, and its own Javadoc now says so. **One** measured row passes it (D-16), and, worse,
it is `true` for **119 of 285 operations** against a subject consisting of one hardcoded literal per
operation, because those operations answer the same thing on every input the corpora hold. Every row
of those 119 is individually correct. `isClean()` remains in the API because it is exactly the right
question for the harness's own regression tests, where the codomain of a synthetic sweep is known by
construction. It is not the question a stage is asking.

### 5.2 The criterion a stage must use — one call

```java
result.requireStagePass(minimumMeasurements, acknowledgedDegenerateOperations);
```

Three clauses, all of them, or it throws with **every** failing clause and the numbers behind it:

1. **`measurementCount() >= minimumMeasurements`**, with `minimumMeasurements` derived from the
   operation's corpus size and **written down in the stage document before the run**. A floor chosen
   after seeing the run is not a floor. `0` is rejected outright.
2. **No row disagreed.**
3. **`distinctReferenceValues() >= 2`** — the reference gave more than one answer, so the sweep
   *could* have failed — **or** the operation carries a sign-off in `AcceptedDegenerateOperations`,
   keyed on the operation **and** the exact single canonical value, with a mandatory written
   rationale that is copied into `stageStatement()` and into the report header.

Two further checks the gate does not make and a stage still must:

4. **Byte-identical golden comparison** via `DiffReportWriter.assertMatchesGolden`, so any change in
   the numbers is a diff someone has to read and approve.
5. **`throwClassMismatchCount() == 0`**, or an explanation. A port that fails on the right rows with
   the wrong exception type leaves every other aggregate bit-identical to a correct port's.

### 5.3 Signing off a genuinely-constant operation

Some of the 159 really are constant by specification — `isUReal()` compiles to `iconst_1; ireturn`.
They are legitimately part of the ported surface and must not be deleted from the inventory: that
hides the row instead of classifying it, which is the mistake round 1 made with `void`. Sign them off
one at a time:

```java
AcceptedDegenerateOperations.builder()
    .accept("URealValue.isUReal()", "BOOLEAN(true)",
            "type predicate; the historical body is iconst_1/ireturn, so BOOLEAN(true) is the whole "
          + "of its specification. Agreement shows the operation exists and is reachable; it is not "
          + "evidence about any computation.")
    .build();
```

The key includes the value, so the sign-off **lapses by itself** if the operation ever answers
something else — a widened corpus, a different jar, a different seed. That is deliberate friction, in
the manner of `AcceptedThrowPairs`, and a blanket "accept all type predicates" cannot be expressed.

**Do not sign one off to make a run pass.** The rationale has to say what a reader should *not*
conclude from the agreement figure, and it lands in the evidence file where they will read it.

### 5.4 What a stage must publish alongside any fidelity figure

Use `result.stageStatement(acknowledged)`; it is built so that an agreement figure cannot be rendered
without the discrimination figure beside it:

```
URealValue.add(value): 576 rows, 576 measured, 576 agreed, 0 disagreed, 164 distinct reference value(s) [DISCRIMINATING]
URealValue.isUReal(): 24 rows, 24 measured, 24 agreed, 0 disagreed, 1 distinct reference value(s) [NOT DISCRIMINATING: always BOOLEAN(true); acknowledged: ...]
```

Never a bare agreement percentage, and never a file-level total: `# rows.*` and `# verdict.*` are
sums over every result in the file and hide an operation that measured nothing (D-21). The
`# op.<key>.*` block is the per-operation one; read that.

## 6. Two traps specific to writing an S4 adapter

* **Do not reach for `HarnessMarshallingException` as a fallback.** `Candidate`'s Javadoc tells you to
  throw it when the adapter genuinely cannot marshal, and that is correct — but `HARNESS_ERROR` and
  `UNSUPPORTED` are both excluded from the per-operation `driven` denominator, so a port that answers
  "could not marshal" wherever it would otherwise be wrong is scored fully agreed everywhere
  (defect D-17). Measured: `{AGREE=444, HARNESS_ERROR=471027}` — 444 agreement rows, zero `DIFFER`
  rows, from a port implementing one method.
* **Do not sign off `AcceptedThrowPairs` in bulk.** A **correct** port is `isClean() == false` on
  **97 of 285** operations, because their shared error paths produce `BOTH_THREW` rows even when both
  sides throw the same class with the same message. That is the policy working as designed, and the
  pressure it creates — "just allowlist them" — is exactly how the deleted blanket throw-agreement
  rule would come back. Each entry needs the operation, both classes, both messages and a written
  rationale, reviewed one at a time.

---

## 7. The question to ask when you extend this

Every round, the harness was fixed to stop making one false claim, and the next reviewer found a
different construction producing the same false claim. Round 1: harness failure == agreement. Round 2:
two throws == agreement. Round 3: two `VOID`s == agreement. Round 4: two equal values over a
one-valued codomain == fidelity. They got *harder* to see, and the fourth needed no bug in
`DifferentialSweep` at all.

So the question is not "is this row correct?" — in round 4 every row was. It is:

> **Could this sweep have failed? What would have had to be different for it to report a
> divergence?**

That question now has an answer the harness computes and a gate that acts on it. **The gate is a
threshold, and a threshold is a place to stand, not a proof.** Three things it does not settle, in
descending order of how likely they are to be the fifth door:

* **The two-valued codomain.** An operation whose range over the corpora is exactly `{true, false}`
  reports `2 distinct reference values`, clears `DISCRIMINATING_MINIMUM`, and is still nearly free
  for a subject echoing one bit of its receiver. `BooleanValue.value()` and `BooleanValue.isTrue()`
  sit at exactly 2 today. The number is published per operation; **look at it, do not just compare it
  to 2.**
* **The `OPAQUE` branch.** `type()` and `getRuntimeType()` are greened by a subject that reproduces a
  string built by field reflection. They are now correctly labelled non-discriminating, which is not
  the same as having been measured. `UncertainBooleanValue` (9 operations) and `uDataTypes.UInteger`
  (1) are rendered the same way.
* **Corpus depth (D-28).** The census is a joint fact about the implementation and the corpus. One
  `RealValue` in the whole tree makes 23 operations degenerate for no reason connected to the code.
  Before concluding that an operation is constant, check whether it is the corpus that is.

And the two defects this round did **not** close, which are open and are not small: a subject can
still shrink its own `driven` denominator by raising `HarnessMarshallingException` (D-17), and
`everyKindIsEitherAnObservationOrUnmeasurable` still restates its own implementation (D-20).
