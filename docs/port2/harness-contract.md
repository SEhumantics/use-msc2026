# The differential harness — contract for S4–S7

**Status: 2026-08-17, after round 4. Binding on every stage that quotes a differential number.**
The harness is `use-core/src/test/java/org/tzi/use/uncertainty/differential/`. Its narrative record,
including the current verdict (**DEFECTIVE**, defect D-15 open) and the full open-defect register, is
[`stage-01.md`](stage-01.md) §10. This file is the short normative version: the rules, not the story.

Read this before writing a sweep. Four rounds of review each found a new way to make the harness
claim fidelity it had not measured; every rule below exists because one of them succeeded.

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
  operation with a single-point codomain are green by construction, not by measurement (**D-15,
  open**). This clause is the one the harness does **not** yet enforce; §5 says what you must do
  about it by hand.

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
  `RealValue`, `IntegerValue`, `BooleanValue`, `StringValue`. Of those, **no corpus contains a
  `BOOLEAN` or `STRING` value**, so all 27 `BooleanValue.*` and all 25 `StringValue.*` operations are
  100% `HARNESS_ERROR` (defect D-19). **61 of 285 operations produce zero measurements even against a
  perfect port.**

---

## 4. The standing invariants, and what each would catch

Run by `mvn -B verify -Djava.awt.headless=true`. If you change the harness, these are what must still
pass; if one starts failing, read it before you "fix" it.

| Invariant | What it would catch |
|---|---|
| `HistoricalOracleIsolationTest` (9 methods) | The harness comparing the port against **itself**. It caught a real one: a platform-parented `URLClassLoader` is not isolated in this reactor. Also pins both jar digests and that a missing or altered jar fails loudly. |
| `UnwrittenPortInvariantTest.anUnwrittenPortAgreesWithNothing` — 7 subjects (throws, Java `null`, empty body, `nullValue()`, fixed constant, echoes receiver, throws `Error`) | A port that does not exist scoring agreement. Two assertions: total agreement rows == 0 for the five non-observing subjects, and — the sharper one — **no operation is agreed on every row the harness could drive**. The second is what turns "444 of 471 471, looks like noise" into "144 of 144, every reachable row". **Blind spot (D-15): the one constant subject returns `UBOOLEAN(true,1.0)`, a canonical form no operation in the inventory returns.** |
| `DifferentialHarnessRegressionTest` (26 methods) | Each closed door, pinned individually: `d1TypeMismatchIsNotAgreement`, `twoThrowsAreNeverAgreementAndNeverLoseTheirMessages`, `d10VoidVersusVoidIsNotAgreement`, `oneSidedAbsenceIsADifferenceAndKeepsItsEvidence`, `twoNullValuesAreNotAgreementEither`, `zeroRowSweepIsRefused`, `aReportWithNoMeasurementsIsRefused`, `zeroMeasurementSweepsCannotReadAsSuccess`, `wrongThrowClassIsVisibleInAnAggregate`, `acceptedThrowPairsAreOptInAndExact`, plus the note-content pins (`harnessErrorNoteCarriesBothSides`, `mixedNoteNamesBothSides`, `unsupportedNoteAttributesEachSide`) that stop evidence being destroyed. |
| `UncertaintyDifferentialSmokeTest` (6 methods) + `DiffReportWriter.assertMatchesGolden` | Non-determinism, and any unannounced change in output. The goldens under `docs/port2/differential/` are compared **byte for byte**; a golden may only be refreshed with `-Duse.differential.golden.refresh=true`, deliberately, in a commit that says why. |
| `everyKindIsEitherAnObservationOrUnmeasurable` | *Intended* to catch a newly added value-less `UValue.Kind` becoming a route to `AGREE`. **Do not rely on it: it is tautological (defect D-20)** — it branches on `carriesAnObservation()` and asserts the verdict `classify` derives from that same predicate. Treat a new `Kind` as requiring manual review. |

---

## 5. **How a stage must gate on a sweep**

### 5.1 `disagreements().isEmpty()` is NOT a pass criterion

It is vacuously true of a sweep that compared nothing: an empty input domain, an all-`UNSUPPORTED`
sweep, an all-`UNMEASURABLE` sweep. "No row disagreed" says nothing when no row was a comparison.
The accessor's own Javadoc says so. Do not write it as an assertion.

`isClean()` — `measurementCount() > 0 && disagreements().isEmpty()` — is better and is still not
sufficient: **one** measured row passes it (defect D-16), and a 1×1 domain gives
`1 rows, 1 measured, AGREE=1`, `isClean() == true`, and a written report.

### 5.2 The criterion a stage must use

All five, per operation, not per file:

1. **`result.isClean()`.**
2. **`result.requireMeasurements(n)`** with `n` derived from the operation's corpus size and written
   down in the stage document — *not* chosen after seeing the run. A measurement floor picked to make
   the run pass is not a floor.
3. **At least two distinct canonical reference values** across the measured rows — i.e. the sweep
   *could* have failed. **This is the D-15 gate and the harness does not compute it for you.** Until
   it does, a stage must derive it and print it. An operation whose reference gave one answer on all
   144 rows is not evidence of fidelity whatever its agreement rate. The 120 operations known to be
   single-valued over the shipped corpora are enumerated in `stage-01-verification-round4.md` §3.1;
   a genuinely-constant operation may be signed off only on a **written, reviewed allowlist**, one
   line of rationale each, in the manner of `ECHO_SUBJECT_REVIEWED`.
4. **Byte-identical golden comparison** via `DiffReportWriter.assertMatchesGolden`, so that any change
   in the numbers is a diff someone has to read and approve.
5. **`throwClassMismatchCount() == 0`**, or an explanation. A port that fails on the right rows with
   the wrong exception type leaves every other aggregate bit-identical to a correct port's.

### 5.3 What a stage must publish alongside any fidelity figure

Rows, **measured rows**, agreement rows, disagreement rows, throw-class mismatches, and **the number
of distinct reference values** — per operation. Never a bare agreement percentage, and never a
file-level total: the report headers are sums over all results and hide an operation that measured
nothing (defect D-21).

---

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

If the answer is "nothing the corpora can produce", the green is an artefact of the instrument.
Two places nobody has looked yet: an operation whose codomain over the corpora is **two** values (a
subject echoing one bit of the receiver would green those), and the **`OPAQUE`** branch, where
`UncertainBooleanValue` (9 operations) and `uDataTypes.UInteger` (1) are rendered by field reflection
and a port need only reproduce a string.
