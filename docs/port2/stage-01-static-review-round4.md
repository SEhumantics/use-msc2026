# S1 — Static review, round 4 (differential harness, post-D-10 fixes)

> **Pointer (2026-08-17):** this is a round-4 report and is left as written. The consolidated
> current verdict and the single open-defect register are in [`stage-01.md`](stage-01.md) §10; the
> rules a stage must follow when it uses the harness are in
> [`harness-contract.md`](harness-contract.md). Defect IDs D-15..D-25 are re-keyed there, because the
> two round-4 reports independently reused D-16..D-19 for different defects.

**Reviewer role**: static refuter. No Maven was run. Evidence below is `git`, `grep`, `sed`, `javap`,
and reflection-only probes (`javac`/`java`) executed against the vendored jars under
`use-core/src/test/resources/historical/`. Scratch probes live in `/tmp/probe4/` and are outside the
repository.

**Tree reviewed**: branch `port-uncertainty-2`, HEAD `53ed152a`; behaviour commit `93e038ac`,
reviewability commit `fa9bba2d`, documentation commit `53ed152a`.

---

## 0. VERDICT — **DEFECTIVE**

A fourth door is open, it is the same species as D1 / D2 / D-10, and it is **fifteen times larger
than D-10 measured the way the round itself chose to measure**.

> **D-15 (CRITICAL).** 91 of the 285 reachable operations return a value that is a *compile-time
> constant* — `iconst_0; ireturn` — independent of every input the harness can supply. A subject
> whose every body is `return UValue.bool(false)` therefore scores `AGREE` on **every driven row of
> 91 operations**, e.g. `URealValue.isBag()` at **144/144** — numerically identical to the D-10
> headline `URealValue.setTypeToRuntimeType()` 144/144. The seven-subject family added this round
> does not contain that subject, and the family's one constant-returning subject returns
> `UValue.uBoolean(true,1.0)`, a canonical form that no operation in the inventory can ever produce.
> The round's headline safeguard — "no operation is fully agreed" — is therefore untested against
> the largest instance of the class of defect it was built to catch.

The six delivered fixes are, on their own terms, correct: I traced every path to `AGREE`, checked
every consumer of the verdict vocabulary, and confirmed that each new test fails on the pre-fix
code. §1 records what is confirmed fixed. §2 records what is not.

| # | Severity | Summary |
|---|----------|---------|
| D-15 | **CRITICAL** | 91 operations are constant-valued; a one-line `bool(false)` subject fully agrees with all of them, and no subject in the family produces `BOOLEAN(false)` |
| D-16 | **MAJOR** | `everyKindIsEitherAnObservationOrUnmeasurable` is tautological — it re-asserts `classify`'s own branch condition and cannot detect a newly added value-less `Kind`. Its Javadoc and §9.2 both claim it can |
| D-17 | **MAJOR** | `DiffReportWriter.writeAll`'s measurement guard and every `# rows.*` header are **file-level totals**. An operation that measured nothing is invisible in the header — the D-10 aggregate blindness, one level up |
| D-18 | **MINOR** | An `UNSUPPORTED` note asserts `reference: could be driven` about rows where the reference demonstrably could not be driven. Latent today; live from S4 |
| D-19 | **MINOR** | `unmeasurableNote` asserts "the operation is declared void" whenever *either* side is `VOID`. False when the reference returned `NULL` and the subject `VOID`. Not reachable with the shipped corpora |
| D-20 | **MINOR** | `stage-01.md` §5.2 pastes a report header that is missing four of the twelve header lines the committed golden now carries, and states line counts 794/795 for files that are 798/799 |
| D-21 | **MINOR** | `AcceptedThrowPairs` line 49 states "the source file is plain ASCII"; line 48 of the same Javadoc paragraph contains U+2014 |

Scope, commit hygiene and Jupiter discipline are **clean** — see §3.

---

## 1. CONFIRMED FIXED

### 1.1 Every path to `AGREE` — enumerated exhaustively

`DiffRow` is constructed in exactly six places, all in `DifferentialSweep`:

```
$ grep -rn "new DiffRow(" --include=*.java use-core/src/test
DifferentialSweep.java:138   -> UNSUPPORTED
DifferentialSweep.java:202   -> HARNESS_ERROR
DifferentialSweep.java:212   -> BOTH_THREW | ACCEPTED_THROW
DifferentialSweep.java:219   -> MIXED
DifferentialSweep.java:232   -> UNMEASURABLE
DifferentialSweep.java:236   -> AGREE | DIFFER
```

`DiffVerdict.isAgreement()` is true for exactly two constants, so there are exactly **two** paths to
an agreement row:

1. **`DifferentialSweep.java:235-237`.** Reached only after four guards have been passed:
   `ref.harnessError == null && sub.harnessError == null` (197), `!(ref.thrown != null && sub.thrown
   != null)` (206), `!(ref.thrown != null || sub.thrown != null)` (218), and
   `!(!ref.value.carriesAnObservation() && !sub.value.carriesAnObservation())` (225). It then
   requires `ref.value.canonical().equals(sub.value.canonical())`.

   I checked the residual one-sided case by hand: if exactly one side lacks an observation, the
   canonical forms are `"VOID"` or `"NULL"` on that side against any other kind's form on the other.
   No value-carrying kind can render as `VOID` or `NULL` — `OPAQUE` always renders
   `OPAQUE("…")`, `STRING` always `STRING("…")` (`UValue.java:263-298`) — so the one-sided case
   always falls to `DIFFER`. This is pinned by `oneSidedAbsenceIsADifferenceAndKeepsItsEvidence`
   (`DifferentialHarnessRegressionTest.java:497`).

2. **`ACCEPTED_THROW`** (`DifferentialSweep.java:211-216`), unreachable unless a caller passed a
   non-empty `AcceptedThrowPairs`. `rationaleFor` short-circuits on the empty map
   (`AcceptedThrowPairs.java:93-95`); `Builder.accept` rejects a blank rationale
   (`:153-159`); `DifferentialSweep`'s ordinary constructor supplies `none()` (`:41`). `git grep`
   finds no `AcceptedThrowPairs.builder()` call anywhere outside its own test. **No sign-off exists
   in the tree.**

The two-observed-values property therefore holds for path 1 as stated. What is *not* established is
that those two values are **non-degenerate** — see D-15.

### 1.2 `UNMEASURABLE` is a non-agreement in every consumer

Checked each consumer named in the brief:

| consumer | file:line | UNMEASURABLE treated as |
|---|---|---|
| `isAgreement()` | `DiffVerdict.java:145-147` | not an agreement |
| `isMeasurement()` | `DiffVerdict.java:163-165` | not a measurement |
| `agreements()` / `disagreements()` | `DifferentialSweep.java:383-404` | single `partition(boolean)` on one predicate — cannot diverge by construction |
| `agreementCount()` | `:407-415` | excluded |
| `measurementCount()` / `measurements()` | `:432-451` | excluded |
| `isClean()` | `:462-464` | fails (`measurementCount() > 0` is false for an all-UNMEASURABLE sweep) |
| `count()` / `tally()` | `:333-339` | every enum constant pre-seeded to 0, then merged — no constant can be invisible |
| report header | `DiffReportWriter.java:156-161, 185` | emitted as `# verdict.UNMEASURABLE`; counted into `# rows.disagreement` (`totalRows - agreementRows`) |
| golden files | `docs/port2/differential/*.tsv` | no UNMEASURABLE rows present; both goldens are 100 % measured |
| smoke assertions | `UncertaintyDifferentialSmokeTest.java:75, 79-81, 120-123` | uses `isClean()`, not `disagreements().isEmpty()` |
| invariant tally | `UnwrittenPortInvariantTest.java:384-391` | counted as *driven* (correct — both sides ran) but not as *measured* and not as *agreed* |

Header order is deterministic: `writeAll` merges into a `LinkedHashMap` while iterating
`DiffVerdict.values()` per result (`:156-161`), and `summary()` iterates an `EnumMap`
(`:333, 522`). Inserting `UNMEASURABLE` between `MIXED` and `UNSUPPORTED` changes nothing
non-deterministic.

**Verified in the committed goldens**, which is the only report evidence in the tree:

```
$ head -13 docs/port2/differential/s1-smoke-ureal-add.tsv
# harness	differential-sweep/1
# seed	20260817
# reference	historical
# subject	stub-faithful
# sha256.use.jar	80ac8ae433b8345677472019991356950f094f4a104cfbce1f75783a7308788d
# sha256.atenearesearchgroup.uncertainty.jar	53b2a43feb0a0a39844a60278dd80a7d4b975ef324fb05c6db28831e835e59d0
# operations	URealValue.add(value)
# rows	784
# rows.measured	784
# rows.agreement	784
# rows.disagreement	0
# rows.throwClassMismatch	0
# verdict.AGREE	784
```

and the entire golden diff in the behaviour commit is the two claimed header lines per file, no data
row changed:

```
$ git show 93e038ac -- docs/port2/differential/
+# rows.measured	784
+# rows.throwClassMismatch	0        (both files)
```

### 1.3 The new tests pin the properties — no D-4 recurrence

Reasoned through each, asking "would this pass against the pre-fix harness?":

| test | pins? | why |
|---|---|---|
| `d10VoidVersusVoidIsNotAgreement` | **yes** | pre-fix `VOID`/`VOID` was `AGREE`; asserts `count(UNMEASURABLE) == receivers.size()` and `agreementCount() == 0` |
| `twoNullValuesAreNotAgreementEither` | **yes** | pre-fix `NULL`/`NULL` compared equal → `AGREE`; now asserts 4 `UNMEASURABLE` |
| `everyKindIsEitherAnObservationOrUnmeasurable` | **yes, weakly** | fails pre-fix (the constant did not exist), but is tautological going forward — **D-16** |
| `aReportWithNoMeasurementsIsRefused` | **yes** | the old guard counted rows; asserts `IllegalArgumentException` and that the file was not created |
| `zeroMeasurementSweepsCannotReadAsSuccess` | **yes** | `isClean()` / `requireMeasurements` did not exist; scenario (1) explicitly asserts `disagreements()` *is* empty, i.e. it pins the trap as well as the fix |
| `wrongThrowClassIsVisibleInAnAggregate` | **yes** | first asserts tally/rowCount/agreementCount/disagreements are identical between correct and wrong-class subjects, then 0 vs 2 |
| `harnessErrorNoteCarriesBothSides` | **yes** | asserts `"[sub]"` appears in the note; pre-fix only the reference's message was quoted, and `assertEquals(row.historical(), row.ported())` proves the columns cannot recover it |
| `mixedNoteNamesBothSides`, `unsupportedNoteAttributesEachSide` | **yes** | assert both-sided phrasing that did not exist |
| `oneSidedAbsenceIsADifferenceAndKeepsItsEvidence` | no (by design) | passes pre-fix; it guards against the *over-broad* fix, which is a real future risk |
| `agreementIsOnlyEverAnObservedValue` | no | the `isAgreement()` loop and the partition assertion both hold pre-fix |

**Would the per-operation assertion have failed on the D-10 code?** Yes, twice over. For subject
`c-empty-body`, `fullyAgreedOperations()` (`UnwrittenPortInvariantTest.java:416-425`) keeps an
operation when `per.driven > 0 && per.agreed == per.driven`, and `per.driven` excludes only
`HARNESS_ERROR` and `UNSUPPORTED` (`:384-386`). Pre-fix the six `setTypeToRuntimeType()` operations
had `agreed == driven` (48/48, 6/6, 54/54, 90/90, 144/144, 102/102), so
`assertEquals(subject.reviewedFullyAgreed /* = Set.of() */, …)` at `:147` fails, and the total-agreement
assertion at `:136` fails first with 444 ≠ 0. The `driven` denominator is what turns 144/462 into
144/144, exactly as claimed.

I independently confirm the denominators from the jars. `462 = 77 receivers × 6 corpora`; the
per-receiver-type driven counts read off the porter's own `setTypeToRuntimeType()` figures
(URealValue 144, UIntegerValue 90, UStringValue 102, UBooleanValue 54, IntegerValue 48, RealValue 6)
and the absence of `BooleanValue.setTypeToRuntimeType()` and `StringValue.setTypeToRuntimeType()`
from that list are consistent with `InputGenerator`'s corpora containing **no** plain `BOOLEAN` and
**no** plain `STRING` receivers (`InputGenerator.java:216-245` — `zeroDivisors()` contributes
`integer(0)` and `real(0.0)`, `indexBoundaries()` contributes eight `INTEGER`s, and nothing
contributes a `BOOLEAN` or `STRING`). These numbers are self-consistent and I could not falsify them.

### 1.4 Inventory size and the two allowlisted operations

```
$ java Probe /…/use.jar /…/atenearesearchgroup.uncertainty.jar
INVENTORY SIZE = 285
```

reproducing `UnwrittenPortInvariantTest.reachableOperations` exactly (8 marshallable receivers,
public non-static non-`Object` methods whose parameters are all `Value`/`int`/`double`/`float`).
The allowlist claim checks out:

```
IntegerValue.value()  ->  int
RealValue.value()     ->  double
```

so `HistoricalOracle.fromHistorical` takes the `result instanceof Integer` / `instanceof Double`
branches (`HistoricalOracle.java:712-717`) and renders `INTEGER(n)` / `REAL(d)` — identical to the
receiver's own canonical form. The limit is real, is correctly described, and is correctly pinned.

### 1.5 Reviewability, determinism, evidence-preservation

* `AcceptedThrowPairs.java` contains **0 NUL bytes** (verified by byte count, not by `grep`, which
  cannot take a NUL pattern). `git show fa9bba2d -- '*AcceptedThrowPairs.java'` renders as a normal
  textual diff. `.gitattributes` requests `diff` and not `text`, so no end-of-line normalisation is
  requested; I confirm no other file changed in that commit.
* The `evidence(ref, sub)` helper (`DifferentialSweep.java:261-274`) is used by `HARNESS_ERROR`,
  `BOTH_THREW`, `ACCEPTED_THROW`, `MIXED` and `UNMEASURABLE`. Both sides are always described. The
  `UNSUPPORTED` branch (`:132-137`) builds its own equivalent clause — see D-18 for the consequence.
* `assertMatchesGolden` now compares `Arrays.equals(readBytes, readBytes)` (`:281`) and only walks
  lines to phrase a known failure (`:286-309`), matching its Javadoc.

---

## 2. DEFECTS

### D-15 (CRITICAL) — 91 constant-valued operations, and the subject family misses them

**The rule the harness states about itself** (`DiffVerdict.java:56-59`): a verdict is defective when
it is *"green by construction rather than by measurement, since the reference side's `VOID` follows
from `method.getReturnType() == void.class`"* — i.e. when the reference's answer is fixed before the
implementation runs. The brief restates it as "two observed, **comparable, non-degenerate** values".

**Measured.** Half the reachable inventory returns `boolean`:

```
$ java Probe …/use.jar …/atenearesearchgroup.uncertainty.jar
INVENTORY SIZE = 285
RETURN TYPE CENSUS
  140	boolean
   18	int
   18	java.lang.String
   16	org.tzi.use.uml.ocl.type.Type
    8	void
    …
```

and almost all of those predicates are compile-time constants. At bytecode level:

```
$ javap -c -p org/tzi/use/uml/ocl/value/Value.class
  public boolean isUndefined();
    Code:
       0: iconst_0
       1: ireturn
  public boolean isCollection();
       0: iconst_0
       1: ireturn
  public boolean isBag();
       0: iconst_0
       1: ireturn
  public boolean isLink();
       0: iconst_0
       1: ireturn
```

Driving every zero-argument `boolean` method on two structurally different receivers of each type
(`Probe2`):

```
zero-arg boolean ops that returned FALSE for both representative receivers: 122
returned TRUE for both: 15
varied / threw: 3      (BooleanValue.value, BooleanValue.isTrue, BooleanValue.isFalse)
```

Restricting to the six receiver kinds the shipped corpora actually contain
(`UREAL, UINTEGER, UBOOLEAN, USTRING, REAL, INTEGER` — see §1.3) leaves **91 constant-`false`
operations**: URealValue 15, UIntegerValue 15, UBooleanValue 15, UStringValue 16, RealValue 15,
IntegerValue 15.

**The consequence.** Trace `URealValue.isBag()` against a subject whose every body is
`return UValue.bool(false)`:

* `HistoricalOracle.supports` → true (`URealValue` is marshallable, the method resolves).
* `invoke` → `method.invoke` returns `Boolean.FALSE`; return type is not `void`;
  `fromHistorical(Boolean.FALSE)` hits `result instanceof Boolean` (`HistoricalOracle.java:709-711`)
  → `UValue.bool(false)` → canonical `BOOLEAN(false)`.
* subject → `BOOLEAN(false)`.
* `classify`: no harness error, no throw, both carry an observation, canonical forms equal →
  **`AGREE`** (`DifferentialSweep.java:235-237`).

Driven rows for any zero-argument operation on `URealValue` = 144, the same denominator as
`URealValue.setTypeToRuntimeType()`. So `URealValue.isBag()` is **144/144 agreed against a port
containing no code** — bit-for-bit the number this round's report puts in bold as the D-10 headline.

Summed over the 91 operations, using the per-receiver driven counts established in §1.3:

```
URealValue     15 ops × 144 = 2160
UIntegerValue  15 ops ×  90 = 1350
UBooleanValue  15 ops ×  54 =  810
UStringValue   16 ops × 102 = 1632
RealValue      15 ops ×   6 =   90
IntegerValue   15 ops ×  48 =  720
                              -----
                              6762 agreement rows, 91 fully-agreed operations
```

(The 6762 is arithmetic on measured denominators, not a run; the 91 is a direct measurement. A
`bool(true)` subject fully agrees on a further 12: the `isDefined()` / `isUReal()` / `isUInteger()` /
`isUBoolean()` / `isReal()` / `isInteger()` / `UBooleanValue.value()` family.)

**Against D-10: 6 operations, 444 rows → 91 operations, ~6762 rows.**

**Why the family does not catch it.** `degenerateSubjects()`
(`UnwrittenPortInvariantTest.java:229-252`) does contain a "fixed constant" subject — but the
constant is `UValue.uBoolean(true, 1.0)`, canonical `UBOOLEAN(true,1.0)`, a kind that **no operation
in the 285 can produce as a `boolean` result**. The one subject shape aimed at this defect was
instantiated with the one constant that cannot collide. `f-echoes-receiver` returns the receiver, so
it produces `UREAL(…)` etc., never `BOOLEAN(false)`. Neither reaches it.

Add subject `h-constant-false` returning `UValue.bool(false)` and the assertion at `:147` fails with
91 unreviewed entries. The test machinery is correct; the family is not general.

**Why this is the same species and not a new complaint.** For `isBag()` the reference's `false`
follows from `iconst_0` in `Value.class`, exactly as `VOID` followed from `getReturnType()`. Neither
number is a statement about the uncertainty implementation. The row is not a lie — two values were
observed and were equal — but "this operation is fully agreed" is worth nothing for a third of the
inventory, and *the harness publishes no statistic that would let a reader tell which third*.

**Consequence for S4–S7.** 91 of 285 (32 %) of the operations any future fidelity claim will quote
can be turned green by one line of adapter code. Add the 8 `void` operations (now `UNMEASURABLE`) and
the 12 `type()` / `getRuntimeType()` operations whose result is a `Type` whose sole instance field is
a constant type name (`Probe3`: `URealValue.type() -> …URealType instanceFields=[BasicType.fTypename]`),
and **111 of 285 operations have a reference result that does not vary with any input the harness can
supply.**

### D-16 (MAJOR) — the enum-quantified audit is tautological

`everyKindIsEitherAnObservationOrUnmeasurable`
(`DifferentialHarnessRegressionTest.java:553-601`) does:

```java
if (sample.carriesAnObservation()) {
    assertEquals(DiffVerdict.AGREE, row.verdict(), …);
} else {
    carriesNothing.add(e.getKey());
    assertEquals(DiffVerdict.UNMEASURABLE, row.verdict(), …);
}
…
assertEquals(Set.of(UValue.Kind.NULL, UValue.Kind.VOID), carriesNothing, …);
```

`classify` (`DifferentialSweep.java:225`) branches on **exactly** `carriesAnObservation()`. The test
therefore asserts `carriesAnObservation() == X ⟹ classify branches on X`, which is the
implementation restated. It cannot fail for any kind whose classification is *wrong*, only for a kind
whose classification is *inconsistent with itself* — which is impossible.

Concretely: add `Kind.UNDEFINED` (canonical `"UNDEFINED"`) for OCL `UndefinedValue`, forget to widen
`carriesAnObservation()` (`UValue.java:191-193`, hard-coded `kind != VOID && kind != NULL`), and add
the required sample. Then `carriesAnObservation()` is `true`, the verdict is `AGREE`, the first
assertion passes, `carriesNothing` is untouched, and the final `Set.of(NULL, VOID)` assertion passes.
**Test green, D-10 reintroduced.** The `samples.size()` assertion (`:569`) forces a *representative*
for a new kind; nothing forces a correct *classification*.

The Javadoc (`:547-551`) states the opposite — *"a kind added later that carries no value cannot
quietly become a route to `AGREE`"* — as does `stage-01.md` §9.2 and item 1 of the porter's closure
argument, which calls this "the strongest part of the argument". It is the weakest: it is the only
one of the round's assertions that is circular.

A non-circular criterion exists and is independent of `carriesAnObservation()`: a value-carrying kind
has **at least two distinguishable inhabitants** (`UValue.integer(1).canonical() !=
UValue.integer(2).canonical()`), whereas `NULL` and `VOID` each have exactly one. Asserting that
would actually pin the property. This is filed, not fixed.

### D-17 (MAJOR) — the measurement guard and every header are file-level totals

`DiffReportWriter.writeAll` sums across results before checking:

```java
for (DifferentialSweep.Result r : results) { rowTotal += r.rowCount(); measuredTotal += r.measurementCount(); }
…
if (measuredTotal == 0) { throw …; }
```

(`DiffReportWriter.java:109-133`) and every emitted header — `# rows`, `# rows.measured`,
`# rows.agreement`, `# rows.disagreement`, `# rows.throwClassMismatch`, `# verdict.*` — is likewise a
sum over all results (`:144-191`). `# operations` is a comma-joined list with no per-operation
counts.

So a multi-sweep report over 40 operations in which 39 measured **nothing** and one measured a single
row is written happily, and its header is indistinguishable in shape from a fully-measured report.
The reader cannot attribute any number to any operation without re-deriving it from 20 000 data rows.

This is precisely the lesson of D-10 — *"444 rows out of 471471 is 0.09 % and looks like noise in an
aggregate, but per operation it was 144 of the 144 driven rows"* — applied to the invariant test and
**not** applied to the writer, which is the artefact a human actually reads. It is also the third
iteration of the same guard error: counting `Result` objects → counting rows → counting measurements,
each time at file granularity only.

Minimum fix: refuse any *individual* result with `measurementCount() == 0`, or emit a per-operation
`# op.<key>.measured` block.

### D-18 (MINOR, becomes live at S4) — `reference: could be driven` is not something the harness knows

```java
String note = "no measurement. "
        + (refSupports ? "reference: could be driven"
                       : "reference: " + reference.unsupportedReason(op))
        + " / " + (subSupports ? "subject: could be driven" : …);
```

(`DifferentialSweep.java:132-137`). This row is emitted *without driving either side*, and
`supports()` is a per-operation predicate while the note is written per row, with the inputs in hand.
`HistoricalOracle.supports` checks only receiver-type marshallability and method existence
(`HistoricalOracle.java:464-474`); the per-row receiver check that produces D1's
`HarnessMarshallingException` happens later, at `:507-514`. So whenever `refSupports && !subSupports`
and the row's receiver is of the wrong kind for the operation, the note asserts "reference: could be
driven" about a row on which the reference provably could **not** have been driven — 388 695 of
471 471 rows in the shipped invariant sweep fail for exactly that reason.

Not reachable in any sweep in the tree today (`StubCandidate` and `HistoricalOracle` disagree about
support only on `SBooleanValue`, where *both* say no, and `DegeneratePort.supports()` always returns
true). It becomes reachable the moment S4 plugs in a port with partial `supports()` coverage. This is
the same category as the corrected *"historical does not implement SBooleanValue.and(value)"* — a
false sentence in a file whose purpose is to be evidence.

Note also that this clause is a **second, independently written** rendering of the both-sides
evidence, parallel to `evidence(Outcome, Outcome)` (`:261-274`). The codebase argues elsewhere
(`DifferentialSweep.java:378-381`) that two independently written implementations of one property is
how they come to diverge.

### D-19 (MINOR, latent) — `unmeasurableNote` states the operation is void on evidence that does not support it

```java
boolean voidOperation = ref.value.kind() == UValue.Kind.VOID || sub.value.kind() == UValue.Kind.VOID;
String why = voidOperation ? "the operation is declared void, so it has no result, …" : …;
```

(`DifferentialSweep.java:240-249`). The disjunction means the *subject* alone returning `VOID` is
enough to assert a fact about the *historical method's declaration*. If the reference returned `NULL`
(a non-`void` method that returned Java `null`, `HistoricalOracle.java:676-679`) and the subject
returned `VOID`, the note asserts "the operation is declared void" and "no post-state was observed on
either side" about an operation that is not declared void.

I could not reach it: driving every parameter-compatible operation on a representative receiver of
each of the six corpus kinds found **zero** null-returning invocations (`Probe4`:
`total null-returning invocations found: 0`). Filed as latent. The correct predicate is
`ref.value.kind() == VOID` (the reference is the only side whose `VOID` follows from
`getReturnType()`); the trailing `evidence()` clause already carries the rest.

### D-20 (MINOR) — the port's own record contradicts the committed golden

`stage-01.md` §5.2 introduces a pasted report header with *"The same figures are written into the
report header"* and then shows (lines 271–279):

```
# operations	URealValue.add(value)
# rows	784
# verdict.AGREE	784
```

The committed golden carries four more header lines between those two — `# rows.measured`,
`# rows.agreement`, `# rows.disagreement`, `# rows.throwClassMismatch`. And:

```
$ wc -l docs/port2/differential/*.tsv
   798 docs/port2/differential/s1-smoke-ureal-add.tsv
   799 docs/port2/differential/s1-smoke-ureal-minus-faulty.tsv
$ grep -n "794 lines\|795 lines" docs/port2/stage-01.md
283:Reports: `docs/port2/differential/s1-smoke-ureal-add.tsv` (794 lines),
284:`docs/port2/differential/s1-smoke-ureal-minus-faulty.tsv` (795 lines).
```

§5 is the "Acceptance — commands and pasted output" section: a reader checking the record against the
artefact finds a mismatch in the one place the document invites them to check. §9.7's
documentation-drift note covers §7, §8 and the `audit-*.md` files but not §5.

Separately, and as instructed, the stale-vocabulary sweep:

```
$ git grep -ln 'AGREE_THROWN\|DIFFER_THROWN' -- docs/port2/
docs/port2/audit-00-verdict.md
docs/port2/audit-01-harness.md
docs/port2/audit-03-acceptance.md
docs/port2/stage-01-refutation-empirical.md
docs/port2/stage-01-refutation-fidelity.md
docs/port2/stage-01-refutation-isolation.md
docs/port2/stage-01-static-review-post-fix.md
docs/port2/stage-01-static-review-round3.md
docs/port2/stage-01-verification-post-fix.md
docs/port2/stage-01-verification-round3.md
docs/port2/stage-01.md
```

Eleven files. Ten of them are dated round reports and are legitimately historical; I agree with the
decision not to rewrite them. `stage-01.md` is not — it is the living stage document, and §9 is an
appendix a reader reaches last. The correct minimal fix is a forward pointer at the head of §7 and
§8, not a rewrite.

### D-21 (MINOR) — a false sentence inside the sentence that claims accuracy

`AcceptedThrowPairs.java:48-49`:

```
     * sign-off — so it was committed in the single encoding that defeats human review. Written as an
     * escape, the source file is plain ASCII and reviews like any other.
```

```
$ python3 -c "b=open('…/AcceptedThrowPairs.java','rb').read(); print('NUL',b.count(b'\x00'),'non-ascii',sum(1 for c in b if c>127))"
NUL 0 non-ascii 15
```

Fifteen non-ASCII bytes — five U+2014 em dashes in the Javadoc, one of them on line 48, immediately
before the claim on line 49. The commit message of `fa9bba2d` repeats it. Harmless in effect; it is
listed because this round's own standard is that a comment in evidence-producing code must not state
something the file falsifies, and four such comments were corrected in `93e038ac` on exactly that
ground. `git`'s binary heuristic keys on NUL, so the substantive claim (reviewability) holds.

---

## 3. PROCESS CHECKS — all clean

**Scope discipline.**

```
$ git diff --name-status 30d480db..HEAD -- '*/src/main/*'
(empty)
$ git diff --name-status 30d480db..HEAD -- '*/src/test/*' | grep -v uncertainty
A	use-core/src/test/resources/historical/use.jar
```

No `use-gui`, no `use-assembly`, no `module-info.java`, no pom, no pre-existing upstream test
touched. The only repository-wide addition is `.gitattributes` (`*.java diff`), which the porter
flagged; I confirm it requests `diff` and **not** `text`, so no working-tree or index normalisation
is implied, and I confirm no other file changed in `fa9bba2d`.

**Separate commits.** `fa9bba2d` (2 files: `.gitattributes` + `AcceptedThrowPairs.java`, first line
"No behaviour change") → `93e038ac` (behaviour, 12 files) → `53ed152a` (docs, 1 file). Correct
ordering and separation. One nit for the record: `fa9bba2d` changes the map-key separator from U+0000
to U+001F, so the *encoding* of `AcceptedThrowPairs`' internal keys changes. Nothing observable
depends on it (the key is private and both characters are replaced by `|` before reaching any
message), so "No behaviour change" is fair.

**Jupiter only.**

```
$ grep -rn "import org.junit.Test\|import junit\|org.junit.Assert" use-core/src/test/java/org/tzi/use/uncertainty/
(none)
$ grep -rn "vintage" --include=pom.xml .
(none)
```

All 17 harness classes use `org.junit.jupiter.*` exclusively; the reactor has no
`junit-vintage-engine`, so a JUnit 3/4 test would silently not run — none exists.

**Determinism.** I could not re-run the build, so the byte-identity claim is taken on the porter's
evidence. Statically, the sources of non-determinism are closed: no `Math.random`, no
`System.nanoTime`, no `String.format` on any canonical path, `EnumMap`/`LinkedHashMap`/`TreeMap`
everywhere an ordered aggregate is built, `opaqueFields` sorts by name within each declaring class
(`HistoricalOracle.java:875-892`), and `HashSet` iteration is explicitly refused in
`appendOpaque` (`:831-837`).

---

## 4. IS THE SPACE CLOSED?

No. It was closed for the *one* shape the round was pointed at, and the fourth door is in the place
the porter's own issues list predicted a reviewer would look — "a value-carrying kind whose canonical
form is degenerate" — but one step past where they looked. They found the primitive/boxed collision
(`INTEGER(n)` from both `int` and `IntegerValue`), which affects 2 operations under the current
corpora. They did not ask the prior question: **for how many operations is the reference's answer
fixed before the reference runs?** The answer is 111 of 285, of which 91 are reachable by a one-line
subject.

Three rounds produced three doors of the form "*no* value was observed on either side, and the
harness called it agreement". This round closed that form properly — I could not find another
instance of it, and the classification in `classify` is now airtight for absence. The fourth door has
the adjacent form: "**a** value was observed on both sides, and it was a constant". The harness's own
vocabulary (`DiffVerdict.java:56-59`) already names the property that distinguishes them — *green by
construction rather than by measurement* — but that property is asserted only about `VOID`, never
computed.

What would actually close it, in order of cost:

1. **Add the missing subject.** `h-constant-false` returning `UValue.bool(false)` (and, cheaply,
   `i-constant-true`). One line each, and it converts D-15 from an argument into a failing assertion
   that someone must sign off, operation by operation, exactly as `ECHO_SUBJECT_REVIEWED` does.
2. **Publish variability, not just agreement.** For each operation, the number of *distinct* canonical
   forms the reference produced across its driven rows. An operation with one distinct reference value
   over 144 rows is not evidence of fidelity whatever its agreement rate, and today nothing in the
   report or the `Result` API says so. This is the statistic that would have made D-10, D-15 and the
   primitive/boxed collision all visible as one family before anyone went looking.
3. **De-tautologise D-16** with the "at least two distinguishable inhabitants" criterion.
4. **Make the writer's guard per-result** (D-17).

Items 1 and 2 are the ones I would insist on before S4 quotes any per-operation agreement figure.

Finally, on the porter's own honesty note — *"I fixed what three reports told me to fix plus what I
found while fixing it; that is not a search of the space"* — that is the correct description of this
round, and it is why the door is still open. The subject family is the right instrument. It was built
from the encodings of "no code here" that had already caused a failure, not from an enumeration of
what the *reference* can return. Enumerating the reference's return shapes takes one reflection probe
over the vendored jars, costs about ten minutes, and is what produced D-15.

---

*Static review only. No Maven was run; no fix was applied. Probes: `/tmp/probe4/Probe{,2,3,4}.java`.*
