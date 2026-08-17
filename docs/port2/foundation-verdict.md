# Foundation verdict — may S3 start?

**2026-08-17, branch `port-uncertainty-2`. Eight review rounds of S1 are complete; behaviour through
`066fe15c`, the independent refutation of it at `c91277ff`.** Written for the human who has to decide.
Three questions, answered plainly. **Nothing here is new measurement** — every figure is cited to the
report that produced it, and where a figure was refuted the refutation is cited instead.

The long records: [`stage-01.md`](stage-01.md) **§10** (narrative, the single canonical defect register
§10.4, the id re-keying map §10.5), [`harness-contract.md`](harness-contract.md) (the rules a stage
follows, **§8 the S4 checklist**), and the eight round reports listed in §4 below. Where any document
disagrees with `stage-01.md` §10.4, §10.4 wins.

> **State in six lines.**
> * S1 verdict: **`SOUND_WITH_DOCUMENTED_LIMITS`** — reached independently by the round-5, round-7 and
>   round-8 reviewers.
> * The perfect-port control diverges **nowhere**: 285 operations, 19 083 rows, 0 `DIFFER`, 0 `MIXED`,
>   0 diverging operations, 74 of 285 stage passes, `javaTypeMismatch` 0. Its printed block is
>   byte-identical across rounds 7 and 8 (`md5 c724bd19dbed9071ffc8762675584107`) and was reproduced by
>   the round-8 refuter's own sweep rig, not only by the suite.
> * Detection power is **measured, not argued**: 8 of 11 planted content infidelities caught on every
>   affected operation; the three misses are named and are properties of the corpus, not of the scorer.
> * **No scoring defect is open.** Nothing open scores a non-agreement as an agreement, and nothing open
>   scores a faithful port as diverging.
> * **One MAJOR is open against a rule that does not bind until S4**: D-52. `javaTypeMismatchCount() == 0`
>   is not yet a sound gate clause, because an adapter chooses the object whose class is read. It is
>   **inert at S1** (74 stage passes either way) and is fixed by mandating the adapter's *shape* — done in
>   `harness-contract.md` §7 and §8 by this commit.
> * The dominant risks to S4 are **not** the type question: they are **D-30** (no input-domain coverage
>   figure exists anywhere) and **D-29** (a perfect port passes only 74 of 285 gates).

---

## 1. May S3 start?

**Yes.** Nothing in the instrument blocks it. S3 is blocked only on decisions a human owes, and the list
is §3: **B1–B12** from `specification.md` §0 plus **H13–H22** added by the eight rounds. B3 in particular
decides whether S3–S7 have any automatic signal at all.

Two preconditions bear on **S4**, not on S3, and both are cheap enough to clear first:

1. **D-52 (MAJOR, open).** `harness-contract.md` §7 mandates `javaTypeMismatchCount() == 0` as an S4 gate
   clause. That clause is not sound as written: the round-8 refuter took a port with a **real 401-row / 9-operation
   wrong-class defect** to `javaTypeMismatch 0`, a verdict tally byte-identical to the perfect-port
   control, and the mandated disclosure in **0 rows**, by changing **one line** of the adapter —
   `observedFrom(anEmptyStandInClass)` (`stage-01-verification-round8.md` §2.1–§2.3). The fix is not a
   fourth API change; it is to mandate the **shape**: the observed object must be the invocation's return
   value, exactly as `PortedInfidelityDetectionPowerTest.observeWhatThePortReturned`
   (`PortedInfidelityDetectionPowerTest.java:1116-1126`) already does. **That sentence is now in
   `harness-contract.md` §7 and in the §8 checklist.** Nothing else about D-52 needs doing at S1: the
   clause is inert here (74 stage passes with the defect and without it).
2. **The provenance aggregate (H21) — CLOSED 2026-08-17, BUILT.** As written below, `OBSERVED` versus
   `ASSUMED` reached only the row note and had no header count (D-49 residue, D-48). It now has one, at
   both scopes: `# rows.subjectTypeObserved` / `# rows.subjectTypeAssumed` and
   `# op.<key>.subjectTypeObserved` / `.subjectTypeAssumed`, from
   `DifferentialSweep.Result.subjectTypeObservedCount()` / `.subjectTypeAssumedCount()`, also carried in
   `summary()` and printed unconditionally by `stageStatement()`. Original text, kept because it is the
   argument the decision rests on: the porter's own unbuilt recommendation —
   `# rows.subjectTypeObserved` / `.subjectTypeAssumed`, per file and per operation — is the cheapest item
   on the whole list, and the round-8 refuter independently agreed it should exist before S4 starts
   (`stage-01-verification-round8.md` §8, limit 3).

**Verdicts by stage.**

| Stage | What it is | Verdict | Where |
|---|---|---|---|
| **S0** | Baseline: branch from `30d480db`, Java 21 / Maven 3.9.16, clean tree, recorded test counts | sound | `stage-00-baseline.md` |
| **S1** | The differential harness — the measuring instrument for every fidelity claim in S4–S7 | **`SOUND_WITH_DOCUMENTED_LIMITS`**, three reviewers independently (rounds 5, 7, 8). No scoring defect open; no false-divergence mode open. One MAJOR open (D-52) against a rule dated to S4. | `stage-01.md` §10; `stage-01-verification-round8.md` |
| **S2** | The port specification, including the 12 blocking decisions | `SOUND_WITH_CAVEATS`; 175/185 citations adjudicated against source, **no blocking decision refuted**, four re-derived by a second party and all four `CONFIRMED` | `stage-02.md`, `audit-02-specification.md` |

**Acceptance, at `c91277ff`:** `mvn -q clean && mvn -B verify -Djava.awt.headless=true` → `BUILD SUCCESS`,
**79 surefire (78 use-core + 1 use-gui) + 130 failsafe (1 `OCLExpressionIT` + 129 `ShellIT`) = 209 methods,
0 failures / 0 errors / 0 skipped**, delta 0 against the stated baseline; two runs byte-identical outside
Maven's own logging (`md5 919997f36959cf8cc6f8af4a64030ecd` over the full stripped output);
`git diff --name-status 30d480db..HEAD -- '*/src/main/*'` **empty**; tree clean
(`stage-01-verification-round8.md` §6).

---

## 2. What can this oracle detect, and what can it not?

Study A is defined as agreement against this oracle, so **this section is the one that must survive a
hostile reviewer.** It therefore starts with what the oracle cannot see.

### 2.1 Invisible — the three classes measured across the rounds

1. **A real defect at an input the corpora never generate (D-30, MAJOR, open).** Round 5 planted a wrong
   uncertainty-combination rule restricted to receiver value `42.0`. Measured: **0 `DIFFER` rows**, a
   verdict tally **byte-identical to a perfect port's** on all 19 083 rows, and **a full stage pass on all
   four affected operations**, published as
   `URealValue.add(value): 576 rows, 576 measured, 576 agreed, 0 disagreed, 0 java-type mismatch(es), 164 distinct reference value(s) [DISCRIMINATING]`
   — for a port that computes the wrong answer. `[DISCRIMINATING]` is *true* and says nothing about the
   input never tried. The same in miniature: a `-0.0 → 0.0` collapse is caught on `floor()`, `neg()` and
   `mult(value)` and **missed on `URealValue.round()`**, because no shipped input drives the historical
   `round()` to a negative zero — the blind-spot set is exactly that one entry, asserted as an exact set so
   it cannot grow silently. **No input-domain coverage figure is computed, published or gated anywhere in
   the instrument.** (`stage-01-verification-round5.md` §3.1–3.2; re-measured unchanged in rounds 6–8.)
2. **A subject that suppresses its own measurement (D-17, MAJOR, open; narrowed by D-32).** A port may
   raise `HarnessMarshallingException` — which `Candidate`'s Javadoc invites — or lie in `supports()`.
   Both destroy the divergence: P8 `0 DIFFER`, P9 `0 DIFFER`, detected on **0** operations each.
   **Neither buys a stage pass** (`HARNESS_ERROR` and `UNSUPPORTED` are non-agreements, clause 2 refuses,
   and both probes lose exactly the four operations they lie about: 74 → 70). What is destroyed is
   **attribution**: the reader is told the harness could not drive 143 rows, not that the port is wrong on
   them.
3. **A Java-runtime-type-only difference — counted, not scored (D-43, demoted round 8; D-52 open).** Where
   the content matches and only the runtime class differs, the row is `AGREE` and the difference is counted
   in `javaTypeMismatchCount()` / `# rows.javaTypeMismatch` / `# op.<key>.javaTypeMismatch`, printed
   unconditionally by `stageStatement()`. It **does not fail a gate at S1**. Extent: **3 445 rows across
   182 of 285 operations** for a non-attributing adapter, and **29 stage passes** a wrong-class port keeps
   because of it — the 29 operations are named in `stage-01-verification-round8.md` §4.5 — plus **4** an
   otherwise do-nothing receiver-echoing subject keeps (D-57). The reason is dated and narrow: **at S1
   there is no ported implementation to observe** — no `org.tzi.use.uml.ocl.value.URealValue` exists in
   `use-core/src/main`, and writing it *is* S4 — so a type-only divergence measures the adapter, not the
   port. **The S4 obligation that reverses this is in `harness-contract.md` §7, and D-52 is the amendment
   it needed:** the gate clause binds only under the invocation-seam adapter shape.

   > **So the sentence a stage must print beside every agreement figure taken from this harness:**
   > **an `AGREE` row may be an agreement on the payload alone.**

**Also invisible, structurally, and none of it new:**

* **33 of 318 public operations cannot be named as a `UOp`** — no row, no verdict, not even `UNSUPPORTED`.
  `equals(Object)` and `compareTo(Object)` on all eight receivers are among them, plus
  `UStringValue.indexOf(StringValue)` and 16 `toString(StringBuilder)` / `toStringWithType(StringBuilder)`.
  **"285 operations" is not the ported surface; it is the surface this harness can name.** This is the
  sharpest edge of the type question: a type-confused port gets `equals` wrong first.
* **Post-state**: the receiver is never re-read, so all **8** `void` mutators are `UNMEASURABLE` — never
  agreement either, so a port is not *claimed* faithful on them. **A `void` operation cannot be shown
  faithful by this harness at all.**
* **`SBooleanValue` (39 operations), collection receivers, `ocl.type.*`, `uDataTypes.*`** — declared out
  of reach; reported `UNSUPPORTED`, never silently skipped.
* **Operations the corpora leave single-valued: 159 of 285.** The gate *refuses* these (D-15 fix); it
  still cannot measure them. 23 are `RealValue.*` purely because the corpora hold exactly one `RealValue`
  (D-28), and `uSubstring(int,int)` gets 17 measured rows of 432 (D-31).
* **Two latent holes the round-8 demotion created, both measured unreachable in today's corpus** and both
  therefore inheriting D-30: `OPAQUE` content is the non-injective concatenation `className|repr`, so two
  values differing in **both** could render equal content (measured: 0 of 197 `OPAQUE` reference rows carry
  a second `|`) — **D-55**; and a **nested** type-only difference is still scored `DIFFER`, because
  `SEQUENCE` content embeds each element's `canonical()` (measured: 0 `DIFFER` over the corpus's 17
  `SequenceValue` rows) — **D-56**.

### 2.2 Detected — measured, not argued

Control first, because nothing below is attributable without it. Two independently loaded
`HistoricalOracle` instances, seed 20260817, 285 operations over stage-shaped domains:

```
rows 19083   measured 17199   agreed 17199
verdict tally {AGREE=17199, BOTH_THREW=910, HARNESS_ERROR=883, UNMEASURABLE=91}
DIFFER 0   MIXED 0   diverging operations 0   javaTypeMismatch 0
stage passes 74 of 285  (isStagePass(1, none()))
```

`md5 c724bd19dbed9071ffc8762675584107` for that block — **identical in rounds 7 and 8**, and reproduced
independently by the round-8 refuter's own rig over the same operations and domains
(`stage-01-verification-round8.md` §1). No subject can exceed 74: clause 2 refuses any operation with a
`BOTH_THREW` / `HARNESS_ERROR` / `UNMEASURABLE` row, and clause 3 is computed from the **reference's**
distinct values, so every subject's pass set is a subset of one fixed by the reference alone.

| probe | planted infidelity | rows | measured | DIFFER | MIXED | ops detected | stage passes (control 74) |
|---|---|---|---|---|---|---|---|
| **P0 / control** | **identity — a perfect port** | 19 083 | 17 199 | **0** | **0** | **0** | **74** |
| P1 | 0-based port of the 1-based string index | 19 083 | 17 160 | 52 | 86 | 3 of 3 | 74 (D-29) |
| P2 | uncertainties combined with `+` instead of `sqrt(ua²+ub²)` | 19 083 | 17 199 | 468 | 0 | 4 of 4 | 70 |
| P3 | `Math.hypot(ua,ub)` for `sqrt(ua*ua+ub*ub)` | 19 083 | 17 199 | 24 | 0 | 4 of 4 | 70 |
| P4 | `<=` for `<`, `>=` for `>` | 19 083 | 17 199 | 280 | 0 | 6 of 6 | 70 |
| P5 | results rounded to 10 decimal places | 19 083 | 17 199 | 428 | 0 | 7 of 7 | 67 |
| P6 | `uEquals` ignores the uncertainty component | 19 083 | 17 199 | 1 119 | 0 | 4 of 4 | 72 |
| P7 | divide-by-zero returns `Undefined` where historical throws | 19 083 | 17 199 | 105 | 62 | 6 of 6 | 71 |
| P8 | P2's defect hidden behind `HarnessMarshallingException` | 19 083 | 16 731 | 0 | 0 | **0** | 70 |
| P9 | P2's defect hidden behind `supports() == false` | 19 083 | 15 597 | 0 | 0 | **0** | 70 |
| P10 | P2's defect only at receiver value `42.0` | 19 083 | 17 199 | **0** | 0 | **0** | **74** |
| P11 | `-0.0` collapsed to `0.0` | 19 083 | 17 199 | 59 | 0 | 3 of 4 | 71 |

**8 of 11 caught on every operation they touch.** The three misses are exactly the first two invisible
classes above: P8 and P9 (D-17), P10 (D-30). Every `DIFFER`, `MIXED`, detected-operation set and
stage-pass figure equals round 7's — **no content probe regressed under round 8's demotion** — and the
detected-operation sets are pasted verbatim in `stage-01-verification-round8.md` §3. Resolution: P3 was
separated at **one ULP** (`UINTEGER(696,0.3144000993956586)` vs `UINTEGER(696,0.31440009939565855)`),
seven significant digits finer than the brief's "10th decimal", because `canonical()` compares
`Double.toString` exactly.

**The type dimension, reported separately since round 8:**

```
subject                              DIFFER  divOps  passes  javaTypeMismatch  notes ASSUMED
P0-perfect                                0       0      74                 0             0
P12-boxed-primitive (real defect)         0       0      74              3445             0
P13-factory-typed adapter (no defect)     0       0      74              3445          3445
P14-observing adapter (no defect)         0       0      74                 0             0
```

Read it as one fact: **the same 3 445 is produced by a port with a real wrong-class defect and by a port
with no defect at all whose adapter never attributed** (D-48, reclassified as a named limit). The only
discriminator is the row note's provenance clause, which had no header aggregate — hence H21, **built
2026-08-17**: the split now has a header number at file and operation scope, so the two cases above are
distinguishable from the preamble alone. Before round
8 those rows were `DIFFER` and cost a **faithful** port 29 stage passes; that false-divergence mode is
gone, independently confirmed (P13 reaches the control's exact stage-pass set; P14 is row-for-row the
reference).

**The demotion did not swallow content differences**, constructed at sweep scale rather than asserted:
a content defect plus a type difference measures `DIFFER 468` where the content defect alone measures 468
(same 4 diverging operations, same 70 passes); and a content defect placed **on the very rows where the
type difference lives** takes `DIFFER` from 0 to **1 831 across 143 operations**, losing 3 stage passes
(`stage-01-verification-round8.md` §4.3). Structural reason, checked rather than assumed:
`DifferentialSweep.classify` demotes only when `ref.content().equals(sub.content())`.

**Two things the type figure is not.** It is **not a lower bound** on wrong-class rows: adding a content
defect moves it from 3 445 / 182 operations to **1 883 / 42**, because a row wrong in both dimensions is a
`DIFFER` and leaves the count (D-54 — nothing is hidden, the `DIFFER` rises and the notes still name the
mismatch, but the header must not be read as a floor). And it is **not yet gate-worthy**: D-52.

### 2.3 The oracle claim a thesis may make

> A `DIFFER` / `MIXED` / `BOTH_THREW` / `throwClassMismatch` count from this harness is **trustworthy**.
> Its **silence** is meaningful only inside the region the corpora reach, on operations it can name, and
> that region is **not measured**. An `AGREE` row may be an agreement on the payload alone. A stage pass
> certifies "no divergence over the inputs we tried" and nothing more.

First clause: eleven planted content infidelities plus fourteen probes, one ULP of resolution, and a
control that diverges nowhere over 19 083 rows. Second clause: D-30, D-17 and the 33 non-nameable
operations. Third: D-43 with its dated reversal. Fourth: D-29.

---

## 3. What must a human decide

One list. **B1–B12 are `specification.md` §0** — evidence, options and recommendation in place there.
**H13–H22 were added by the eight review rounds** and are not in that section. Struck rows are answered;
they are kept visible because each cost a round.

### 3.0 DECIDED — 2026-08-17 (four items)

The user decided four items in one round. All four are binding and none is re-litigable. **Three went
against the recommendation recorded above, and all three expand scope** — recorded here as the user's
decision, with the recommendation that was not taken named, so the record shows a considered choice
and not a drift. Nothing below deletes the original evidence or recommendation; the rows keep their
text and are marked in place.

| # | Chosen | Recommendation NOT taken | Status |
|---|---|---|---|
| **B3** | **(b)** a `-Pupstream-oracle` Maven profile: `junit-vintage-engine` 5.7.0, test scope, `use-core` + `use-gui`, **no test file touched**. Every stage from S3 runs both `mvn -B verify -Djava.awt.headless=true` and `mvn -B verify -Pupstream-oracle -Djava.awt.headless=true`. | — the recommendation **was** taken | **BUILT and MEASURED.** `upstream-oracle-profile.md`: default 10 classes / **210** distinct methods; profile **50 classes / 497 distinct methods, 0 failures, 0 errors**. No upstream test fails under it. |
| **B7** | **FIX** the historical defects, documenting each. | **bug-for-bug reproduction** — the recorded recommendation, **not taken** | Open work for S4–S7. Each of the 33 behaviour-changing rows in `specification.md` §7.2 needs a fix, a written justification, and the print-output delta where one exists. |
| **H14** | **BUILD an input-domain coverage measure** (D-30). | **prose-stated domains** in every stage document — the recorded position, **not taken** | Open work. This was "the one open question the instrument cannot answer for you, and the largest live risk"; the decision converts it from a caveat carried in prose into an instrument to be built. Everything that today reads "inherits D-30" — H17's corrected justification, D-45, D-55, D-56 — becomes measurable rather than argued. |
| **B2** | **FULL PORT of `SBoolean`**, all 39 operations. | **skeleton** (keep `SBooleanType` only) — the recorded recommendation, **not taken** | Open work. `StandardOperationsSBoolean`'s 1502 lines enter the build with **zero** fork-test coverage behind them, so the differential harness is the only instrument that can judge the 39 operations. |

**H21 is DONE** (see the struck row below): the provenance aggregate was built in this round —
`# rows.subjectTypeObserved` / `# rows.subjectTypeAssumed` and the per-operation equivalents, plus
`Result.subjectTypeObservedCount()` / `.subjectTypeAssumedCount()`, carried in `summary()` and
`stageStatement()`, pinned by a regression test, and the two S1 goldens refreshed by exactly four
added header lines each.

| # | Decision |
|---|---|
| **B1** | How `uDataTypes` reaches the product classpath — it is on no Maven repository under any coordinates. Recommendation **A2** (vendor the 2023 MIT source, relocated). |
| **B1a** | Whether to keep A2 on defence-in-depth grounds after its originally stated justification was refuted, or re-open A1. |
| **B2** | `SBoolean` scope: full omission, skeleton, or full port. Recommendation **skeleton**. — ✅ **DECIDED 2026-08-17: FULL PORT, all 39 operations. The skeleton recommendation was NOT taken; see §3.0.** |
| **B3** | ✅ **DECIDED 2026-08-17: option (b), the profile — BUILT, see `upstream-oracle-profile.md` (§3.0).** `junit-vintage-engine`: in the product build, or in a `-Pupstream-oracle` profile. Without it **38 of 41** `*Test.java` never execute and "full suite green" is a near-vacuous gate. Recommendation **(b)**, run as part of every stage's acceptance. **This one decides whether S3–S7 have any automatic signal at all.** |
| **B4** | The `'equals'` keyword collision (confirmed live against three upstream fixtures): drop `identicalExpression`, hide it behind a semantic predicate, or amend the fixtures. Recommendation **1, else 2 — not 3**. |
| **B5** | `TypeTest#testSupertype`: adopting the fork lattice breaks **exactly 10 of its 12 assertions** (independently re-derived). Recommendation **adopt and handle**. |
| **B6** | `UndefinedValue` printed form — 7.5.0 prints `null`, the fork prints `Undefined`, **79 corpus entries** expect the latter. Recommendation **normalise in the harness**. |
| **B7** | Bug-for-bug vs fix, as **one policy** first and then per row: **33 behaviour-changing ledger rows**. — ✅ **DECIDED 2026-08-17: FIX and document each row. The bug-for-bug recommendation was NOT taken; see §3.0.** |
| **B8** | `Op_number_sqrt` / `Op_number_pow` shadowing (chain confirmed, ends in `ClassCastException`). Recommendation **tighten `matches`**. |
| **B9** | `ExpQuery` items 7+8 — `exists`/`forAll` over uncertain predicates. **Take both or neither.** |
| **B10** | `ExpDefSBoolean` + `ASTSBooleanDefExpression`: drop the unreachable dead code, or port it with its three defects documented. Recommendation **drop**. |
| **B11** | `UnlimitedNatural` lattice inconsistency: reproduce bit-for-bit or fix. Recommendation **reproduce, plus a regression test pinning the deviation**. |
| **B12** | Corpus-harness placement and the process-global `Options.explicitVariableDeclarations` write that makes JUnit-3 suite ordering load-bearing. |
| **H13** | **How S4 gates, given D-29.** A perfect port passes 74 of 285. Choose: (a) diff `stageGateFailures` against a recorded perfect-port baseline — cheapest, recommended; (b) fund ~**273** hand-authored sign-offs (154 `AcceptedThrowPairs` + 119 degenerate); (c) change clause 2. **Do not choose "fall back on `isClean()`"** — that is a 119-operation gap. |
| **H14** | ✅ **DECIDED 2026-08-17: BUILD it. The prose-stated-domains position was NOT taken; see §3.0.** **Whether to build an input-domain coverage measure (D-30)**, or to accept corpus-bounded claims with the domain stated in prose in every stage document. The one open question the instrument cannot answer for you, and the largest live risk. |
| **H15** | **Whether the corpora are widened before S4** (D-28: one `RealValue`; D-31: `indexBoundaries()`; D-42: `boolean=4`), or the single-valued census is signed off operation by operation with written rationales. |
| **H16** | **How fidelity is established for the 33 non-nameable operations** — `equals(Object)` first: a second instrument, hand-written unit tests, or a recorded declaration that they are out of scope. |
| ~~**H17**~~ | **ANSWERED, round 6:** a type-aware canonical form was built; "right content, wrong Java type" is not fidelity. **With the D-45 correction:** the original justification ("a declared return type is one class") is **false for 84 of 285 operations**; the conclusion survives on a corpus measurement (0 of 285) and therefore **inherits D-30**. Do not repeat the false reason in a stage document. |
| **H18** | **Post-state** — the 8 `void` mutators cannot be shown faithful by this harness at all. Accept, or build a post-state probe. |
| ~~**H19**~~ | **ANSWERED, round 6:** D-34, D-35, D-36 fixed before any S4 number existed, all three independently confirmed. |
| ~~**H20**~~ | **ANSWERED, rounds 7 and 8, and the answer was corrected twice by refutation.** Round 7 made the ported token a measurement and guarded the stating route with a mandatory reason; the reason was measured to reach **0 rows** on a laundered sweep. Round 8 deleted the stating API and demoted a type-only difference to a counted `AGREE`, with a dated S4 obligation. Round 8's refuter then showed the obligation's clause is still author-influenceable through the **choice of object** (D-52) and that the fix is to mandate the adapter's **shape** — now written into `harness-contract.md` §7 and §8. **What remains yours:** hold every S4–S7 document to one sentence saying how its adapter obtained the class token, and reject a type-fidelity figure whose adapter does not observe the invocation's own return value. |
| ~~**H21**~~ | ✅ **ANSWERED / BUILT 2026-08-17** — `# rows.subjectTypeObserved` / `# rows.subjectTypeAssumed`, file total and per operation, plus `Result.subjectTypeObservedCount()` / `.subjectTypeAssumedCount()` in `summary()` and `stageStatement()`, the identity `observed + assumed == javaTypeMismatch` pinned by `DifferentialHarnessRegressionTest#theTypeMismatchTotalIsSplitBySubjectTypeProvenance`, and both S1 goldens refreshed (+4 header lines each, no data row moved). Original text: **Whether to build the provenance aggregate before S4 starts** — `# rows.subjectTypeObserved` / `.subjectTypeAssumed`, per file and per operation (D-48 / D-49 residue). Today `OBSERVED` vs `ASSUMED` is a per-row fact with no header number, so two reports with equal type-mismatch counts are told apart only by reading rows. **Recommended: build it.** Both the round-8 porter and the round-8 refuter call it the cheapest item on the list. |
| **H22** | **Whether D-55 and D-56 are closed now or carried as corpus-dependent latents.** Both are unreachable in today's corpus **by measurement, not by construction** (0 of 197 `OPAQUE` rows carry a second `\|`; 0 `DIFFER` over 17 `SequenceValue` rows), so both inherit D-30. Carrying them is defensible; a widened corpus (H15) can make either live without warning. |

---

## 4. How this was reviewed

Eight rounds, alternating build and refutation, each refutation run by a reviewer who owned Maven and
reproduced the *before* state rather than taking the previous round's word for it.

| Round | Verdict | What it found |
|---|---|---|
| 1 | **DEFECTIVE** | The harness scored **its own** marshalling failures as agreement — 21 816 rows green. Closed: `HARNESS_ERROR` as a distinct non-agreement. |
| 2 | **DEFECTIVE** | Two throws with matching class names were `AGREE_THROWN`, messages discarded. Closed by **deleting** throw-agreement. |
| 3 | **DEFECTIVE** | `VOID` vs `VOID` was `AGREE` — 444 rows, every driven row of every void operation. Closed: `UNMEASURABLE`. |
| 4 | **DEFECTIVE** | **No scoring bug at all**: two real values, correctly equal, over a single-point codomain — 159 of 285 operations. Closed: `distinctReferenceValues()`, published per operation and **gated**. |
| 5 | **`SOUND_WITH_DOCUMENTED_LIMITS`** | First direct measurement of **detection power** (11 probes). No new scoring defect. Found instead: the gate is not satisfiable by fidelity (D-29), detection is bounded by an unmeasured input domain (D-30), and **three of four MAJORs were in the documents, not the instrument**. |
| 6 | **defects closed, then DEFECTIVE on refutation** | Closed D-18 (right content in the wrong Java class was `AGREE` on 193 of 285), D-34, D-35, D-36. Its refuter confirmed all four, found the control intact and no false green — and found that the new check's ported-side token was **declared by the thing under test**, so a **content-perfect** port reproduced the headline detection figure exactly (3 445 / 182 / 29 passes lost). **D-43.** |
| 7 | **half closed, then DEFECTIVE on refutation** | Made the ported token observable (`observedFrom(Object)`), deleted `asJavaType(String)`, and guarded the remaining stating route with a mandatory written reason. Refuted: `declaredJavaType(referenceToken, "x")` took a wrong-class port to a sweep byte-identical to the control with the mandated reason in **0 rows**. |
| 8 | **`SOUND_WITH_DOCUMENTED_LIMITS`** | **Demoted** the check instead of patching it a third time: no author-chosen token exists, and a type-only difference is `AGREE` counted in `javaTypeMismatchCount()`, with a dated S4 obligation to promote it. Its refuter confirmed the control (byte-identical, and reproduced from an independent rig), confirmed every content probe unchanged, confirmed the demotion swallows no content difference — and found **D-52**: the escape hatch moved from a `String` parameter to an `Object` parameter, so the fix must mandate the adapter's **shape**, not its call. Plus D-53–D-57, all MINOR. |

**The honest summary.** **Rounds 1–5 fixed scoring defects** — four distinct ways the instrument called
something agreement that was not, each closed by a mechanism and pinned by a test with a control that
fails if the enforcement degenerates into blanket refusal. **Rounds 6–8 were all about one addition**, the
Java-runtime-type check: round 6 closed a real blind spot and created a false-*divergence* mode, round 7
closed that and left an escape hatch, round 8 removed the ability to state a token at all and demoted what
cannot yet be attributed. The convergence is visible in the numbers rather than asserted: the control
block is byte-identical across rounds 7 and 8, every content probe's figure is identical across rounds 7
and 8, and what each round of the last three found was **smaller** than the last — 3 445 `DIFFER` on a
faithful port (round 6R), then the same erasure via a guarded API (round 7R), then a MAJOR that is **inert
at S1** and needs one sentence of contract wording (round 8R). The transferable lesson, and it is why the
review did not thrash: **a fix is not assessed until someone measures what a *faithful* port does under
it**, and **if the thing under test can influence what a rule measures, no mandated disclosure repairs the
rule** — a disclosure fires only where the instrument already noticed, and laundering is exactly the case
where it did not.

---

## 5. Residual risk

* **The largest live risk is silent agreement inside an unmeasured domain (D-30).** A port wrong only
  where the corpora do not look is reported as a full stage pass with `[DISCRIMINATING]` beside it — true
  and irrelevant. Measured twice. Nothing in the harness will tell you; only a widened corpus (H15), a
  coverage metric (H14), or a stage document stating its domain in prose will.
* **The second-largest is a stage automating on a boolean (D-29).** On 92 of 285 operations a perfect port
  already fails clause 2, so the pass bit cannot move — and the most classic port bug of all, off-by-one
  on a 1-based string index, sits in exactly that region: its rows go
  `{AGREE=37, BOTH_THREW=99}` → `{DIFFER=26, MIXED=26, BOTH_THREW=84}` while `requireStagePass` says
  `false` before and after. Diff the **clause list** against a recorded perfect-port baseline (H13).
* **The type figure is not yet a gate (D-52).** Inert at S1, and it must be settled before S4 quotes the
  figure. **Its provenance now has an aggregate (D-48/D-49, H21, built 2026-08-17)** — the two
  `rows.subjectType*` lines plus the per-operation pair — so "the same 3 445 from a
  defective port and from a non-attributing adapter" is now separable from the header. That closes the
  reporting half of D-48; the gating half (D-52, the adapter's shape) is unchanged and still yours. The reviewer check that fails to
  separate an honest adapter from a laundering one — "state the attribution route", which both would state
  truthfully — has been replaced in `harness-contract.md` §7 by a check on the adapter's shape.
* **Attribution can be destroyed by the subject (D-17), though the verdict cannot (D-32).** A partially
  implemented port is a legitimate S4 state, so this cannot be turned into a `DIFFER`; the defence is that
  it buys no stage pass, and the recommended reporting fix (split `UNSUPPORTED` into a subject-declined
  bucket) is unbuilt.
* **The soft target is now the prose, and it has been for four rounds.** Three of round 5's four MAJORs
  were reports reading stronger than the runs behind them; the round-5 verifier disclosed having drafted a
  recomputation block from memory before the run finished; round 7's refutation killed a claim that three
  documents made in identical words; round 8's refutation killed a fourth ("no agreement figure can be
  rendered without the count" — `agreementCount()`, `agreements()` and `isClean()` are public and
  unaccompanied, D-53). **Hold S4–S7 documents to the harness's own standard: every claim names
  `file:line` or the command, and pastes real output.**
* **The specification's risk is decision risk, not evidence risk.** No blocking decision was refuted and
  four were independently re-derived. What remains is that **all twelve, plus H13–H16, H18, H21 and H22,
  are unmade.**

**Recommendation: S3 may start once B1–B12 and H13–H16 / H18 are answered.** (H21 is now built and no
longer deferrable-or-not; B2, B3, B7 and H14 were decided on 2026-08-17 — see §3.0.) H21 and H22 may be deferred
past S3 but not past S4. The foundation carries the weight. It does not carry an unqualified fidelity
claim, and the honest form of that claim is written into `harness-contract.md` §1, §4 and §8 so that a
stage cannot make the stronger one by accident.

**Only defects the refuter confirmed are marked closed here.** The canonical state of every defect — open,
closed, reclassified, and by which commit — is `stage-01.md` §10.4, with the id re-keying map in §10.5.
