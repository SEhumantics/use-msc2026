# S1 — static review, round 5

**Subject.** Commits `0a93ad4f` (behaviour + refreshed goldens) and `10b8b4c2` (documentation) on
`port-uncertainty-2`, i.e. FIX A–E against defect **D-15** (a degenerate codomain scored as
fidelity) and **D-19** (BOOLEAN/STRING receivers with no corpus).

**Method.** Static only. `git`, `grep`, `sed`, `awk`, `javap` against the vendored jars. **No Maven
was run.** Every number below is either pasted from a committed file, recomputed from committed data
rows with `awk`, or read out of the vendored bytecode. Where I could not measure something without
running the suite I say so and do not guess.

**Verdict: SOUND_WITH_DOCUMENTED_LIMITS.** The metric is correct — I recomputed it independently
from the golden data rows and it matches, including against the two wrong ways of computing it. The
gate is real, bidirectional and pinned. But I found **four MAJORs**, one of which is a *reduction in
what the standing invariant asserts* that shipped inside the same commit as the fix, and one of which
is an evidence-file paste that attributes output from one test to another. Neither is a scoring bug;
both are the same species as every previous round — an artefact that reads stronger than the
measurement behind it.

Defect ids in this report are **round-5-local** and prefixed `R5-` to comply with the naming rule in
`stage-01.md` §11.1. Canonical ids (`D-nn`) refer to the register in `stage-01.md` §11.2.

---

## 0. Summary table

| id | sev | where | one line |
|---|---|---|---|
| **R5-1** | **MAJOR** | `stage-01.md` §11.4.1 | The refusal block pasted as the AFTER evidence of the 119-operation attack was produced by a *different test*, on a *different subject*, over a *different domain*; the operation it names is discriminating in the run being described and cannot be one of the 119. The same document contradicts it three lines later. |
| **R5-2** | **MAJOR** | `DiffReportWriter.java:123-126`, `harness-contract.md` §5 | The 3-argument `writeAll` silently substitutes `AcceptedDegenerateOperations.none()`, so a report can assert `# accepted.degenerateOperations 0` while the pass it documents was granted under a sign-off. The contract's stage recipe never tells a stage to use the 4-argument form. D-14's hole *was* dug twice. |
| **R5-3** | **MAJOR** | `UnwrittenPortInvariantTest.java:147-153` | The standing per-subject invariant was **weakened** by this commit: it used to assert the reviewed set against **all** fully-agreed operations, now only against the discriminating half. For the two value-producing subjects, an operation that newly agrees on every driven row is asserted by nothing whenever it is single-valued — that is 159 of 285 operations. One instance already exists. |
| **R5-4** | **MAJOR** | `UncertaintyDifferentialSmokeTest.java:73-79`, `harness-contract.md` line 18 | The gate is opt-in and the tree's own stage-facing acceptance test still gates on `isClean()`. The contract's claim "a stage that forgets fails rather than passes" is false: a stage that forgets does not fail, it simply is not gated. |
| R5-5 | MINOR | `DifferentialSweep.java:626-640` | Clause 3's refusal message conflates "measured nothing" with "could not have failed", asserts the latter when `distinct == 0`, and offers a sign-off remedy that can never work in that case. |
| R5-6 | MINOR | `UnwrittenPortInvariantTest.java:726` | `assertEquals(cleanAndDegenerate, refusedByTheGate)` is tautological — both counters are incremented in the same branch with no path between them. D-20's shape, in the test written to close D-15. |
| R5-7 | MINOR | `DifferentialHarnessRegressionTest.java:671-677, 710`, `harness-contract.md` §4 | "169 `HARNESS_ERROR` rows carry distinct marker strings" is false — all 169 carry one identical string. The pin holds, but at 1-vs-0, not 169-vs-0, and the stated strength is repeated in three places. |
| R5-8 | MINOR | `UnwrittenPortInvariantTest.java:466` (`per.agreed == per.driven`) | `fullyAgreedOperations()` tests `agreed == driven`. An operation with `agreed > driven` — the shape a regression that made a non-driven verdict an agreement would take — falls out of **both** buckets silently. |
| R5-9 | MINOR | `DiffReportWriter.java:246-263` | `# op.<key>.*` keys are not unique. A report holding several `Result`s for one operation (which is exactly how the invariant sweeps: op × corpus) emits duplicate header keys with different values and no aggregation. |
| R5-10 | MINOR | `InputGenerator.java:97-100, 145-147` | `booleanCorpus(2)` appends two random draws to an already-exhaustive two-element domain. The printed corpus census reads `boolean=4` for a type with two inhabitants. |

Nothing found at CRITICAL. The three questions the brief asked first — is the metric correct, is the
gate real, is the acknowledgement mechanism narrow — all came back clean, and §1–§3 give the
evidence.

---

## 1. Is the discriminating-power metric CORRECT? — **yes, and I recomputed it**

### 1.1 It uses the same canonicalisation as the comparison, on the same rows

The verdict is decided at `DifferentialSweep.java:242`:

```java
boolean agree = ref.value.canonical().equals(sub.value.canonical());
```

and the metric at `DifferentialSweep.java:512-521`:

```java
public java.util.SortedSet<String> referenceValues() {
    java.util.SortedSet<String> out = new java.util.TreeSet<>();
    for (DiffRow row : rows) {
        if (row.verdict().isMeasurement()) {
            out.add(row.historical());
        }
    }
    return java.util.Collections.unmodifiableSortedSet(out);
}
```

`row.historical()` on an `AGREE`/`DIFFER` row is `ref.value.canonical()` (set at
`DifferentialSweep.java:243`), and `DiffVerdict.isMeasurement()` is `AGREE || DIFFER` and nothing
else. So the metric counts **the reference's canonical forms, over exactly the rows the comparison
was made on, under exactly the comparison's equality**. That is the right definition and it is not
a second implementation of it.

`ACCEPTED_THROW` is correctly excluded: it is an agreement but not a measurement, and its
`historical` column is a `THROWN:<class>` marker, not a value. Had `isMeasurement()` included it, a
sweep of adjudicated throw-pairs would have looked discriminating on the throwable class names.

### 1.2 Recomputed from the committed goldens, three ways

The goldens carry every data row, so the header number is checkable without running anything:

```
$ awk -F'\t' '!/^#/ && $1!="index" && ($6=="AGREE"||$6=="DIFFER"){print $4}' s1-smoke-ureal-add.tsv | sort -u | wc -l
258
$ awk -F'\t' '!/^#/ && $1!="index" && ($6=="AGREE"||$6=="DIFFER"){print $4}' s1-smoke-ureal-minus-faulty.tsv | sort -u | wc -l
389
```

against the committed headers:

```
# op.URealValue.add(value).distinctReferenceValues	258
# op.URealValue.minus(value).distinctReferenceValues	389
```

Exact match on both. And the faulty golden separates the two columns, which is the check that
matters:

```
$ awk -F'\t' '... {print $5}' s1-smoke-ureal-minus-faulty.tsv | sort -u | wc -l   # the PORTED column
391
```

391 ≠ 389, so the published number demonstrably comes from the historical column and not the ported
one. No overcounting: the metric is a set of canonical strings, so two rows that agreed on the same
value contribute once.

### 1.3 The two wrong implementations are pinned — but see R5-7

`distinctReferenceValuesCountsTheReferenceOverMeasuredRows` pins both:

* counting the **subject's** column — `varied` has 3 reference values and 1 subject value, asserted
  as 3, so a ported-column implementation fails;
* counting over **all** rows — the 169-row all-`HARNESS_ERROR` sweep is asserted 0, so a
  count-everything implementation returns 1 and fails.

Both pins are genuine. The *reason given* for the second is not; see R5-7.

### 1.4 One judgement call, stated rather than filed

On a `DIFFER` row where the reference produced `NULL` and the subject a value, the string `NULL`
counts as a distinct reference value. I think that is right — a reference that answers `NULL` on some
inputs and a value on others genuinely *can* answer differently, and a port must reproduce that — but
it is a judgement, it is not written down anywhere, and a reader computing the metric by hand from a
report would not necessarily make the same one. Worth a sentence in the contract.

---

## 2. Is the gate real or ornamental?

### 2.1 The gate itself is real

`stageGateFailures` (`DifferentialSweep.java:604-641`) is the single implementation;
`isStagePass` and `requireStagePass` both delegate to it, so there is no second copy to drift.
The three clauses:

1. `measurementCount() >= minimumMeasurements`, with `minimumMeasurements < 1` throwing
   `IllegalArgumentException` — a floor of zero is not expressible;
2. `disagreements().isEmpty()` — and `disagreements()` is the complement of `agreements()` through
   the single `partition(boolean)` predicate, so `HARNESS_ERROR`, `UNSUPPORTED`, `BOTH_THREW`,
   `MIXED` and `UNMEASURABLE` all fail it;
3. `distinctReferenceValues() >= 2` unless signed off.

Clause 2 is stricter than I expected and it closes one thing the register lists as open:
**D-17 cannot reach a stage pass.** A subject that hides behind `HarnessMarshallingException` turns
those rows into `HARNESS_ERROR`, which is a non-agreement, which fails clause 2 outright — quite
apart from the mitigation the register claims via clause 3. D-17 remains live for the *per-operation
invariant predicate*, which is a different predicate; see R5-8.

`requireStagePass` returns `this` and reports **every** failing clause, not the first. Both are
asserted (`assertSame`, and `assertEquals(2, tooFew.size())`).

### 2.2 …and it is opt-in, which is R5-4

The brief asks: *can a caller get a clean-looking pass on a single-valued operation by using a
different accessor?* **Yes**, and the tree ships an example. Every one of these is still public,
still un-deprecated, and still returns a green-looking answer on a degenerate sweep:

* `isClean()` — true on 119 operations against the constant-literal subject, by the porter's own
  measurement;
* `requireMeasurements(int)` — `assertSame(degenerate, degenerate.requireMeasurements(1))` is
  asserted in the new regression test itself;
* `disagreements().isEmpty()`;
* `DiffReportWriter.writeAll` — no discrimination guard at all; the file-level `# rows.disagreement 0`
  is written for a fully degenerate result set.

And `UncertaintyDifferentialSmokeTest.java:73-79`, the S1 acceptance demonstration whose goldens are
the committed evidence for this whole stage, still reads:

```java
// isClean(), not disagreements().isEmpty(): the second is also true of a sweep that
// compared nothing at all, and this test's whole job is to show the plumbing compares.
assertTrue(result.isClean(), ...);
```

That comment argues *for* `isClean()` as the pass predicate, and it now directly contradicts
`isClean()`'s own Javadoc ("This predicate is not a pass predicate either, and a stage must not use
it as one"). For this particular sweep the operation is discriminating (258 distinct, §1.2), so the
claim is not false — but the test does not assert that, so nothing stops the corpus being narrowed
under it. The harness's own flagship green is ungated.

The porter disclosed the opt-in property. What is not disclosed is the contract sentence built on
top of it (`harness-contract.md`, line 18):

> a stage that forgets fails rather than passes

That is not true. A stage that forgets does not fail; it is simply not gated, exactly as before.
`requireStagePass` fails a stage that *calls it*. The distinction is the whole of D-11, and the
contract states the stronger claim.

**This is the one place where I disagree with the porter's own closure argument.** The argument says
the previous state was "a sentence in a document; a reader who did not open the document was not
stopped by it". The current state is a *method* a reader who did not open the document will not call.
That is better — the number is now unavoidable in `summary()`, `stageStatement()` and the header —
but the *refusal* is not.

---

## 3. The acknowledgement mechanism — clean, to the `AcceptedThrowPairs` standard

Checked against every over-broadening route the round-2 review used on `AcceptedThrowPairs`:

| attack | result |
|---|---|
| wildcard / "accept all type predicates" | not expressible — `rationaleFor` is a single `Map.get` on an exact two-part key (`AcceptedDegenerateOperations.java:105-114`) |
| prefix or `startsWith` matching | none; exact `equals` via the map |
| empty or blank rationale | `require()` rejects `null`, `""` and whitespace-only (`:151-157`); asserted with `"  "` in both test classes |
| rationale not reaching the output | reaches `stageStatement()` (`DifferentialSweep.java:663-666`) **and** `# op.<key>.degenerate.acknowledged` (`DiffReportWriter.java:258-262`); both asserted |
| silent default | `Objects.requireNonNull(acknowledged, "acknowledged (use AcceptedDegenerateOperations.none())")` in `stageGateFailures` and `stageStatement`; `assertThrows(NullPointerException.class, () -> degenerate.isStagePass(1, null))` |
| sign-off surviving a behaviour change | key includes the sole canonical value; a wrong-value and a wrong-operation entry are both asserted not to match, in both test classes |
| duplicate entries with conflicting rationales | rejected (`:145-148`) |
| separator injection | `SEP` is U+001F; `UValue.quote()` escapes every char `< 0x20` (`UValue.java:302-325` (the `c < 0x20` branch at :316)), so the value half can never contain it, and `UOp.key()` is `Type.method(params)`. Not reachable. |

No sign-off exists anywhere in the tree outside the tests that exercise the mechanism —
`grep -rn "AcceptedDegenerateOperations.builder()"` returns only the two test classes.

**The one hole is not in this class, it is in the writer: R5-2.**

---

## 4. Would the new invariant subject have failed BEFORE the change?

Strictly, it could not have compiled: `requireStagePass`, `isStagePass`, `stageStatement` and
`AcceptedDegenerateOperations` are all new in `0a93ad4f`. So the honest answer to the D-4 question is
that `aNoLogicPortCannotProduceAStagePass` is a test **of** the fix, not a detector that would have
found the defect independently. That is unavoidable for a fix that consists of adding a predicate.

What matters instead is whether it *pins* the fix, and it does:

* delete or weaken clause 3 → `assertThrows(IllegalStateException.class, ... requireStagePass ...)`
  fails on 119 operations;
* compute `referenceValues()` over the ported column or over all rows → the two separate regression
  assertions fail (§1.3);
* widen the corpora until no operation is degenerate → `assertTrue(cleanAndDegenerate > 0)` fails
  loudly, with a message telling the reader to find out why. That is the right shape: the attack
  going away is a *finding*, not a pass.
* the control (`requireStagePass(100, none())` on a 164-distinct sweep) fails if the gate degenerates
  into a blanket refusal.

The one assertion that pins nothing is R5-6.

The `observed` cross-check inside that test is worth calling out as genuinely good: it collects the
oracle's canonical forms in a separate loop, with its own `try/catch`, and asserts set equality
against `Result.referenceValues()`. I traced the two populations against each other (rows the oracle
threw on → `catch continue` there, `MIXED`/`BOTH_THREW` here, excluded by both; rows the harness
could not marshal → excluded by both; one-sided `NULL` → `DIFFER` here and recorded there) and they
agree. That is a real independent check, not a restatement.

---

## 5. Corpus additions — reaching the operations, and deterministic

**They reach.** `receiverTypeOf` maps `BOOLEAN → BooleanValue` and `STRING → StringValue`
(`UnwrittenPortInvariantTest.java:878-890`); both are in `MARSHALLABLE_RECEIVERS`
(`HistoricalOracle.java:134-136`); and the marshalling exists —
`load("BooleanValue").getMethod("get", boolean.class)` and `ctor("StringValue", String.class)`
(`HistoricalOracle.java:649-653`). Confirmed against the jar:

```
$ javap -p -cp use-core/src/test/resources/historical/use.jar org.tzi.use.uml.ocl.value.BooleanValue
  public static org.tzi.use.uml.ocl.value.BooleanValue get(boolean);
  public boolean value();
  public boolean isTrue();
$ javap -p -cp ... org.tzi.use.uml.ocl.value.StringValue
  public org.tzi.use.uml.ocl.value.StringValue(java.lang.String);
  public java.lang.String value();
```

`StringValue.value()` returning a raw `java.lang.String` is exactly the D-18 collision the porter
added to `ECHO_SUBJECT_REVIEWED`; the three additions are justified by the bytecode.

**Determinism.** Checked every route I could think of:

* *ordering* — `corpora()` is a `LinkedHashMap` in fixed order; `referenceValues()` is a `TreeSet`
  over `String`, i.e. `String.compareTo`, which is not locale-sensitive; `allValues()` is a
  `LinkedHashSet`.
* *locale* — nothing new goes through `String.format`, `toUpperCase`, `Collator` or a `DateFormat`.
  `UValue.quote()` hand-rolls its hex escape with `Character.forDigit` precisely to avoid this.
* *encoding* — `InputGenerator.java` is UTF-8 (`file` says so), `project.build.sourceEncoding` is
  UTF-8 in the root pom, reports are written and read as `StandardCharsets.UTF_8`. The `é中` and
  U+1F600 literals do not appear in either committed golden, so no golden depends on it.
* *hash iteration* — none of the new code iterates a `HashMap`/`HashSet`.
* *the random stream* — this is the one that could have invalidated the BEFORE/AFTER, and it does
  not. `corpora()` draws `uReal, uInteger, uBoolean, uString` **before** `boolean, string`, and the
  two corpora after them (`zeroDivisors`, `indexBoundaries`) are static. So deleting the two
  `out.put` lines to take the BEFORE census leaves the four uncertain corpora bit-identical. The
  single-variable claim holds. Good method.
* *TSV hostility* — `"\t"`, `"\n"`, `"\""`, `"back\\slash"` as `StringValue` payloads all pass
  through `UValue.quote()`, which escapes tab, newline, CR, quote and backslash, and `DiffRow.scrub`
  is a second line of defence. Safe.

Arithmetic cross-check on the published census: `11 + 159 + 115 = 285` and `61 + 121 + 103 = 285`.
Both close.

---

## 6. The MAJORs in full

### R5-1 (MAJOR) — §11.4.1 attributes one test's output to another test

`stage-01.md` §11.4.1 says, of the constant-literal attack:

> **AFTER**: the same subject, the same operations, more of them (119). Every one of the 119 is
> refused, and the refusal names the reason:
> ```
> sweep of URealValue.add(value) is not a stage pass:
>   - the reference side produced 1 distinct value(s) across 1 measured row(s), always UREAL(2.0,0.0).
>   ...
>   tally: URealValue.add(value): 1 rows, 1 measured, 1 distinct ref, AGREE=1
> ```

That block cannot have come from `aNoLogicPortCannotProduceAStagePass`, for three independent
reasons:

1. **`1 rows`.** In that test every operation is driven over `stageDomains`, and
   `URealValue.add(value)` takes a `VALUE` parameter, so its domain is the 24-element uReal receiver
   corpus squared — 576 rows. The document itself says so nine lines later:
   `CONTROL, faithful port URealValue.add(value): 576 rows, 576 measured, ... 164 distinct
   reference value(s) [DISCRIMINATING]`.
2. **It is discriminating.** With 164 distinct reference values, `URealValue.add(value)` fails
   `result.isClean() && !result.isDiscriminating()` and is therefore **not one of the 119** by
   construction.
3. **That test never prints a refusal.** It calls `assertThrows` and asserts on the message; the only
   `System.out.println(refusal.getMessage())` in the tree is in
   `DifferentialHarnessRegressionTest.theStageGateRefusesADegenerateOperation`, whose `degenerate`
   result is `sweepBinary(add, one, one)` — one receiver, one argument, **1 row**, sole value
   `UREAL(2.0,0.0)`. That is the block, verbatim.

So the flagship illustration of the fix is a synthetic one-row sweep between two faithful stubs,
presented as an instance of a 119-operation attack by a no-logic port. The refusal message is real
and the mechanism is real — this is a provenance defect, not a fabrication — but it is precisely the
kind of paste that the round-4 review filed as D-24 (`stage-01-static-review-round4.md` local D-20:
"§5.2 pasted a superseded header"), and it recurs in the amendment that closes the round-4 CRITICAL.
The same paste was carried into the porter's hand-off summary with an "e.g." in front of it.

**Fix**: paste a refusal from the 119 (any single-valued operation over its own receiver corpus, e.g.
`URealValue.isUReal()` at 24 rows), or label the block as what it is.

### R5-2 (MAJOR) — a report can assert that no sign-off was in force when one was

`DiffReportWriter.java:123-126`:

```java
public static Path writeAll(String fileName, List<DifferentialSweep.Result> results,
                            Map<String, String> jarDigests) {
    return writeAll(fileName, results, jarDigests, AcceptedDegenerateOperations.none());
}
```

and, at the end of the header block (`:265-267`):

```java
// Stated even when empty, so that "no sign-off was in force" is an assertion this file
// makes rather than an absence the reader has to infer.
header(out, "accepted.degenerateOperations", Integer.toString(acknowledged.size()));
```

Put together: a stage that follows `harness-contract.md` §5.2 exactly — `requireStagePass(min,
acknowledged)` for the gate, `assertMatchesGolden` for the evidence — and writes its report with the
3-argument `writeAll` (which is what §5 shows, and what all five existing call sites use) produces a
file whose header **asserts** `# accepted.degenerateOperations 0` while the pass it documents was
granted under a sign-off. The comment promises the line is an assertion; the default makes it a
false one.

This matters more than a missing line, which is what D-14 was against `AcceptedThrowPairs`. A missing
header leaves the reader to ask; a header reading `0` answers the question wrongly. And the register
entry (`stage-01.md` §11.2, D-14) says:

> The new `AcceptedDegenerateOperations` does emit `# accepted.degenerateOperations` **even when
> zero**, so the same hole was not dug twice

which is half true: the line exists, and it can lie.

The design principle the porter states everywhere else — "`none()` is the default and must be passed
explicitly, so a caller cannot reach a pass … without naming the mechanism" — is enforced in
`stageGateFailures` and `stageStatement` (both `requireNonNull`, no defaulting overload) and is
abandoned in exactly the place where the value is a *claim about someone else's state*.

**Fix**: delete the 3-argument overload, or make the header line distinguish "no allowlist was
supplied to the writer" from "an empty allowlist was supplied".

### R5-3 (MAJOR) — the standing invariant now asserts less than it did

Before `0a93ad4f` (`git show 0a93ad4f^:…UnwrittenPortInvariantTest.java`, line 147):

```java
assertEquals(subject.reviewedFullyAgreed, tally.fullyAgreedOperations().keySet(), …);
```

After (line 147):

```java
assertEquals(subject.reviewedFullyAgreed, tally.discriminatingFullyAgreedOperations().keySet(), …);
```

and the other half is explicitly not asserted (lines 156-167): *"The other half of the split is
printed, not asserted on here, and deliberately so."*

The stated reason is D-20 avoidance — "asserting 'the degenerate bucket is degenerate' would restate
the predicate that sorted it". **That reason does not support this change.** Asserting the *membership*
of the degenerate bucket against a reviewed set is not a restatement of the sorting predicate, any
more than asserting the membership of the discriminating bucket is; the tautology would be asserting
that every member of the degenerate bucket has one reference value. What was removed is the
membership assertion, not the tautology.

The consequence, concretely: for the two `WRONG_VALUES` subjects (`e-fixed-constant`,
`f-echoes-receiver`), an operation that comes to agree on **every driven row** is now caught by
nothing at all whenever its reference side is single-valued — and single-valued is **159 of 285**
operations, the majority of the measured surface. The `NOTHING` subjects are still covered by the
global `agreementRows == 0`, so the loss is confined to e and f, but e and f are the only subjects
that can produce agreement at all.

An instance already exists and is documented as a side effect rather than as a loss:
`RealValue.value()` was in `ECHO_SUBJECT_REVIEWED` before this commit and is not now. It is still
fully agreed against the echoing subject; it has simply moved from an asserted list to a printed one.
The register calls this "moves to the labelled degenerate population rather than staying on a list
that reads as a sign-off". Labelling is not asserting: if a second, fifth or twentieth operation joins
it, the suite stays green and the line scrolls past in the build log.

I cannot quantify how many operations are in that unasserted bucket for subject `f` without running
the suite; the porter's output reports only the discriminating half (2 → 5). **That number should be
printed and asserted.** A `assertEquals(expectedCount, degenerateFullyAgreedOperations().size())`
with the count written down is not tautological and would restore the property.

### R5-4 (MAJOR) — see §2.2.

---

## 7. The MINORs

**R5-5 — the refusal message states something the harness does not know.**
`DifferentialSweep.java:626-640` builds one message for both causes of `distinct < 2`, although
`isDiscriminating()`'s own Javadoc says outright that *"False has two causes and they are not the
same"*. With `distinct == 0` the reader is told:

> the reference side produced 0 distinct value(s) across 0 measured row(s). This operation could not
> have failed over this domain, so agreement on it is decided before either implementation runs …
> Either widen the domain until the reference answers differently, or sign the operation off in
> AcceptedDegenerateOperations with a written rationale

Three problems. The operation was not measured, so "could not have failed" is not what was observed.
"Agreement on it is decided before either implementation runs" describes a sweep with agreements;
this one has none. And the sign-off remedy **cannot work**: `soleReferenceValue()` returns `null` when
the set is empty, and `rationaleFor(key, null)` returns `null` unconditionally, so no entry can ever
open the gate for a zero-measurement operation. The reader is instructed to do something impossible.
Clause 1 does fire alongside, so the reader is not left without the true reason — but the false one is
printed next to it. `stageStatement()` has the milder version of the same conflation: `distinct == 0`
renders `[NOT DISCRIMINATING]` with no "always", which reads as degenerate rather than unmeasured.

**R5-6 — a tautological assertion in the test that closes D-15.**
`UnwrittenPortInvariantTest.java:679-694` increments `cleanAndDegenerate` and `refusedByTheGate`
inside the *same* `if` body, with only an `assertThrows` between them. There is no execution path
that increments one and not the other: if the gate failed to refuse, `assertThrows` aborts the test
before `refusedByTheGate++`. Therefore

```java
assertEquals(cleanAndDegenerate, refusedByTheGate,
        "every clean-but-degenerate sweep must be refused by the stage gate");
```

can never fail. It is harmless (the `assertThrows` does the real work) but it is D-20's exact shape —
a test restating its own implementation — in the very method whose comment explains that a
tautological assertion was deleted from the sibling test for that reason. The hand-off summary
describes it as "all 119 are refused, in the same test, with the count asserted equal", which reads as
a second, independent check. It is not one.

**R5-7 — "distinct marker strings" is false.**
`DifferentialHarnessRegressionTest.java:671-677` (Javadoc), `:710` (inline comment) and
`harness-contract.md` §4 all say some version of: *169 `HARNESS_ERROR` rows carry distinct marker
strings, so counting over all rows would look richly discriminating.* `DiffRow.harnessError(t)`
returns `"HARNESS_ERROR:" + t.getClass().getName()`, and every one of those 169 rows failed
marshalling with the same class, `HarnessMarshallingException`. All 169 carry **one** identical
string. Counting over all rows would return 1, not 169. The assertion message "169 rows of absence
are not 169 observations of a range" describes an outcome no implementation would have produced.
The pin still holds (1 ≠ 0), and the inline comment's "two different harness failures and two
different throws are still zero" describes a scenario this sweep does not contain — there are no
throws in it at all. Worth constructing the scenario the comment describes, since that is the case
where the wrong metric would look impressive.

**R5-8 — `agreed == driven` should be `agreed >= driven`.**
`UnwrittenPortInvariantTest.java:466` (`per.agreed == per.driven`). `driven` excludes `HARNESS_ERROR` and `UNSUPPORTED`; `agreed`
counts every `isAgreement()` row. If a future regression made either of those verdicts an agreement —
which is the exact defect class of D1 and D2 — an affected operation would have `agreed > driven` and
would silently drop out of **both** buckets, so neither the discriminating assertion nor the printed
degenerate list would show it. The `NOTHING` subjects would still catch it globally; for `e` and `f`
it would be invisible. One-character fix, and it removes an assumption the predicate is currently
making about a property the harness elsewhere refuses to assume.

**R5-9 — `# op.<key>.*` keys are not unique.** `DiffReportWriter.java:246-263` iterates `results` and
emits a block per `Result`, not per operation. Two `Result`s for one operation — which is exactly the
shape the invariant produces (`for op { for corpus { sweep } }`) — yield two `# op.X.rows` lines with
different values and no way to attribute either. Latent today (no call site does it) and directly in
the path of an S4 stage that sweeps one operation over several corpora and writes one report.

**R5-10 — `boolean=4` over a two-inhabitant type.** `booleanCorpus(RANDOM_DRAWS)` appends two
`randomBoolean()` draws to `booleanBoundaries()`, which the Javadoc correctly calls exhaustive. The
draws can only duplicate. Harmless for correctness (`allValues()` dedups for receivers), but the
corpus census line printed in every run and pasted into `stage-01.md` §11.4 reads `boolean=4`, which
overstates the domain, and the two wasted draws perturb nothing but are noise in the row counts of
argument positions. `booleanCorpus` should take no random tail.

---

## 8. Jupiter, scope, commits, documentation

* **Jupiter only.** Every `import org.junit` in the package is `org.junit.jupiter.api.*` or
  `org.junit.jupiter.params.*`. No `org.junit.Assert`, no `junit.framework`, no JUnit-4 `@Test`.
* **Scope.** `git diff --name-status 30d480db..HEAD -- '*/src/main/*'` → empty. Both commits touch
  only `use-core/src/test/java/org/tzi/use/uncertainty/differential/**` and `docs/port2/**`. No pom,
  no `module-info.java`, no pre-existing upstream test. `git status --short` clean.
* **Commit separation.** `0a93ad4f` = behaviour + the two refreshed goldens; `10b8b4c2` = docs. The
  goldens belong in the behaviour commit and not in the doc commit: the header change and the golden
  are the same fact, and splitting them would leave `0a93ad4f` failing its own
  `assertMatchesGolden`. Correct as done.
* **Golden refresh, verified rather than trusted.** `git diff 0a93ad4f^ 0a93ad4f --
  docs/port2/differential` is exactly `+7` lines in each file, all `# op.*` / `# accepted.*`, **zero**
  data rows changed and zero data rows moved. The overwrite is what the commit message says it is.
* **Defect-ID re-keying.** The mapping table (`stage-01.md` §11.1) covers every historical id I could
  find. `stage-01.md` §8.2's own round-3 `D-15` row (line 786) still reads `D-15` and now collides
  with the canonical `D-15` **inside the same document** — but §11.1 maps it explicitly
  (`stage-01.md §8.2 (round 3) | D-15 | D-26`) and the stated rule is that a bare `D-nn` means §11.2.
  Acceptable; a one-line "(now D-26)" annotation at line 786 would make it readable without a
  cross-reference. No orphaned evidence found: `git grep -n "D-15" -- docs/port2/` returns 30 hits and
  every one resolves through the table.
* **`AGREE_THROWN` / `DIFFER_THROWN`** appear nowhere in `docs/port2/` outside the deliberate
  historical narrative — no stale verdict names.
* **The temporary file in a tracked directory** (`d-byte-probe.tsv` under `docs/port2/differential/`)
  is disclosed by the porter, deleted in a `finally`, and the working tree is clean. I agree it is a
  design smell rather than a defect, and I agree with the porter's judgement that parameterising
  `goldenDir()` was out of scope.

---

## 9. Fit for purpose as the S4–S7 oracle

**For a `DIFFER` / `BOTH_THREW` / `MIXED` / `throwClassMismatch` count: yes, unchanged, and this
round found nothing against it.**

**For an `AGREE` count: yes, conditionally, and the condition is now computable rather than
remembered.** The condition is `# op.<key>.distinctReferenceValues >= 2` — the number is correct
(§1.2, recomputed), it cannot be omitted from `summary()`, `stageStatement()` or the per-operation
header, and `requireStagePass` refuses without it. That is a real change in kind from a documented
convention, and the two ways of computing it wrongly are pinned by tests I traced.

**The three things a stage must not do with it.**

1. **Do not read `isClean()` as a pass** (R5-4). The gate protects callers who opt in. The tree's own
   smoke test does not opt in.
2. **Do not quote an aggregate.** `# rows.*` and `# verdict.*` are file-level sums (D-21, open for the
   guard); the census is a joint fact about the jars *and* the corpus, and D-28 is the proof — 23
   `RealValue.*` operations are single-valued because the corpora hold exactly one `RealValue`, which
   says nothing about the historical code.
3. **Do not read `>= 2` as sufficient.** `DISCRIMINATING_MINIMUM = 2` is a chosen threshold. An
   operation whose range is `{true, false}` clears it and is nearly free for a subject echoing one
   bit — `BooleanValue.value()` and `BooleanValue.isTrue()` sit at exactly 2 and are on the reviewed
   echo list. The porter says this outright and I agree it is measurable-but-not-closed.

**What I would require before S4 quotes a single number**: R5-2 (a report must not be able to assert
`0` sign-offs while a sign-off was in force) and R5-3 (the standing invariant must assert what it
asserted before this commit). R5-1 is documentation, but it is the paragraph a reader of the closure
argument will read first.

**On the five-round pattern.** The porter states that they found the fifth-door candidate the same
way the previous four were found — by looking where the last reviewer pointed — and that the person
who wrote the fix is worst placed to find the next door. I agree, and I will add the observation this
round supports: **three of my four MAJORs are about the artefacts rather than the instrument.** The
scoring logic came back clean under every attack I could construct statically; what did not come back
clean was a pasted example, a defaulted argument in the writer, and an assertion that was quietly
narrowed in the same commit that added the gate. The instrument is getting harder to fool. The
*record* of the instrument is now the softer target, and R5-1 and R5-3 are both cases where the
document and the summary read stronger than the code that backs them.

---

*Static review, round 5. No Maven was run; every claim above is traceable to a committed file, a
pasted command, or the vendored bytecode.*

---

## Appendix — working-tree state at review time

`git status --short` was **empty** when this review began. It was not when it ended:

```
 M use-core/src/test/java/org/tzi/use/uncertainty/differential/UnwrittenPortInvariantTest.java
?? use-core/src/test/java/org/tzi/use/uncertainty/differential/PortedInfidelityDetectionPowerTest.java
```

Those are uncommitted changes made by a concurrent session, not by this review, which wrote nothing
outside `docs/port2/stage-01-static-review-round5.md`. The modification is two `private static` →
package-private widenings (`stageDomains`, `tuples`), 2 insertions and 2 deletions with no change in
line count, so every line citation above still resolves against both the committed state
(`0a93ad4f`) and the current working tree. **This review is of the committed state and of nothing
else**; `PortedInfidelityDetectionPowerTest.java` was not read and is not assessed here.
