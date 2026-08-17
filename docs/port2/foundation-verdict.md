# Foundation verdict — may S3 start?

**2026-08-17, branch `port-uncertainty-2`, after six review rounds of S1.** Written for the human
who has to decide. Three questions, answered plainly, each with the evidence named. The long records
are [`stage-01.md`](stage-01.md) §10 (verdict, story, defect register),
[`harness-contract.md`](harness-contract.md) (the rules a stage follows) and
[`stage-01-round6-fixes.md`](stage-01-round6-fixes.md) (round 6's four fixes, with pasted output).
Nothing here is new measurement; every figure is cited to the report that produced it.

> **Round 6 changed this document's answer in one place and one place only.** The three fixes it
> demanded before S4 quotes a number — **D-34**, **D-35**, **D-36** — are **done**, and so is
> **D-18**, which was limit 5 below. `mvn -q clean && mvn -B verify -Djava.awt.headless=true` is
> `BUILD SUCCESS` at `d13d4858` with 77 surefire and 130 failsafe methods, 0 failures. Six documented
> limits are now five, and **H17 and H19 are answered**; H13–H16 and H18 are still open questions for
> you.

---

## 1. Is the S0–S2 foundation sound enough to build S4–S7 on?

**Yes — with five documented limits. The three fixes this section used to demand are made**
(round 6, `d13d4858`). Not "sound". Not "blocked".

| Stage | What it is | Verdict | Where |
|---|---|---|---|
| **S0** | Baseline: branch from `30d480db`, Java 21 / Maven 3.9.16, clean tree, recorded test counts | sound | `stage-00-baseline.md` |
| **S1** | The differential harness — the measuring instrument for every fidelity claim in S4–S7 | **`SOUND_WITH_DOCUMENTED_LIMITS`** (both round-5 reviewers, independently); round 6 then closed all four remaining defects, D-18 among them, **and has not itself been reviewed by a second party** | `stage-01.md` §10, `stage-01-round6-fixes.md` |
| **S2** | The port specification, including the 12 blocking decisions | `SOUND_WITH_CAVEATS`, 175/185 citations adjudicated against source, **no blocking decision refuted**, four re-derived from source by a second party and all four `CONFIRMED` | `stage-02.md`, `audit-02-specification.md` |

**The five limits, stated as the constraints they are.** A stage that respects all five can quote
numbers from this instrument honestly; a stage that ignores any one of them will publish a figure
stronger than its evidence. (There were six. The fifth — right content in the wrong Java type — is
closed; see the struck row below.)

1. **A stage pass is not a fidelity certificate.** It certifies "no divergence over the inputs we
   tried". Quote measured rows, distinct reference values, and the input domain covered — the last in
   prose, because the harness does not compute it (`stage-01.md` §10.6).
2. **The gate is not satisfiable by fidelity.** A *perfect* port reaches `isStagePass(1, none())` on
   **74 of 285** operations; 119 are refused by design (D-15) and **92 by clause 2** for
   `BOTH_THREW` / `HARNESS_ERROR` / `UNMEASURABLE` rows a faithful port cannot avoid. On those 92 an
   infidelity leaves the pass/fail bit **unchanged** — measured on 10 (defect, operation) pairs.
   S4 must diff the *clause list* against a recorded perfect-port baseline, not read a boolean (D-29).
3. **Detection is bounded by the corpus, and the bound is published nowhere** (D-30). See §2.
4. **285 is not the ported surface.** 33 public operations cannot be named as a `UOp` and appear in
   no report at all, `equals(Object)` and `compareTo(Object)` on all eight receivers among them.
5. ~~**Right content with the wrong Java type is scored `AGREE` on 193 of 285 operations**~~
   — **closed in round 6.** `UValue.canonical()` is type-bearing, so a `Kind` or runtime-class
   difference is a `DIFFER` carrying a note that names both fully-qualified types. Measured on a
   perfect port that boxes every raw result into its `Value` class: **0 → 3 445 `DIFFER` rows**,
   **0 → 182 of 285** operations detected on, **74 → 45** stage passes. The perfect-port control still
   diverges nowhere, and no operation in the corpora answers with two different runtime classes
   (0 of 285, measured), so the fix has no false-divergence mode.
6. **The census is a joint fact about the code and the corpus.** 159 of 285 operations are
   single-valued; 23 of those are `RealValue.*` purely because the corpora hold exactly one
   `RealValue` (D-28), and `uSubstring(int,int)` gets 17 measured rows out of 432 (D-31).

**The three fixes this section demanded before S4 quotes a number are made** — all test-scoped,
commit `d13d4858`, evidence in `stage-01-round6-fixes.md` §2–§4:

* **D-34 — done.** `write` and `writeAll` both require the sign-off set and no eliding overload
  exists; a reflective assertion fails if one comes back. Before: `stage pass WITH the sign-off?
  true` printed beside `# accepted.degenerateOperations 0`. After: the two headers of one sweep,
  written under `signed` and under `none()`, are asserted **unequal**.
* **D-35 — done.** Both halves of the fully-agreed split are asserted again, keeping the split.
  Verified against unmodified HEAD behaviour: restoring the assertion catches `RealValue.value()` on a
  run the unrestored test passes.
* **D-36 — done for the acceptance test.** `UncertaintyDifferentialSmokeTest` gates through
  `requireStagePass(784, none())` with the floor written above the run, prints `isClean()` beside the
  gate rather than passing on it, asserts *which clause* refuses in the negative direction, and states
  its input domain in prose. It is now the template S4 should copy. The gate itself is still opt-in by
  design — nothing in the harness can force a caller through it, and `harness-contract.md` §4.1's
  withdrawn claim to the contrary stays withdrawn.

---

## 2. What can the harness detect, and what can it not?

**Start with what it cannot, because that is what a thesis oracle claim has to survive.**

### Invisible

* **A real defect on an input the corpora do not reach.** Round 5 planted P2's wrong
  uncertainty-combination rule restricted to receiver value `42.0`. Result: **0 `DIFFER` rows**, a
  tally byte-identical to a perfect port's on all **19 083** rows, and **a full stage pass on all
  four affected operations**, published as
  `URealValue.add(value): 576 rows, 576 measured, 576 agreed, 0 disagreed, 164 distinct reference value(s) [DISCRIMINATING]`
  for a port that computes the wrong answer. `[DISCRIMINATING]` is *true* and says nothing about the
  input never tried. Same finding in miniature: a `-0.0 → 0.0` collapse is caught on `floor()`,
  `neg()` and `mult(value)` and **missed on `URealValue.round()`**, because no shipped input makes the
  historical `round()` produce a negative zero. (D-30; `stage-01-verification-round5.md` §3.1–3.2.)
* **`equals(Object)`, `compareTo(Object)`, `UStringValue.indexOf(StringValue)` and the
  `toString(StringBuilder)` family** — 33 operations of 318, not nameable as a `UOp`. A port whose
  `equals` ignores the uncertainty component is invisible through `equals`; round 5 caught that
  mistake only because `uEquals(Value)` happens to take a `Value`.
* **Any effect on the receiver.** The harness never re-reads it, so the 8 `void` mutators measure
  nothing. They are `UNMEASURABLE` — never scored as agreement either, so the port is not *claimed*
  faithful on them.
* ~~A wrong Java type carrying the right content, on 193 of 285 operations~~ — **no longer
  invisible** (D-18, closed in round 6). It moves to the *Detected* table below as P12.
  `equals(Object)` remains the sharp edge here: a type-confused port gets `equals` wrong first, and
  `equals` is one of the 33 operations that cannot be named at all.
* **`SBooleanValue` (39 operations), collection receivers, the `ocl.type.*` hierarchy and
  `uDataTypes.*`** — declared out of reach; reported `UNSUPPORTED`, never silently skipped.
* **An operation the corpora leave single-valued** — 159 of 285. The harness now *refuses* these
  rather than greening them, which is the D-15 fix; it still cannot measure them.

### Detected — measured, not argued

Round 5 is the first direct measurement of detection power. A second, independently loaded
`HistoricalOracle` plays a perfect port; a wrapper applies exactly one named infidelity. **Control:
0 `DIFFER`, 0 `MIXED`, 0 diverging operations over 285 operations and 19 083 rows**, so every
divergence is attributable to the planted defect.

| probe | planted infidelity | detected on | rows | stage passes lost |
|---|---|---|---|---|
| P1 | 0-based port of the 1-based string index | 3 of 3 | 138 | 0 (D-29) |
| P2 | uncertainties combined with `+` instead of `sqrt(ua²+ub²)` | 4 of 4 | 468 | 4 |
| P3 | `Math.hypot(ua,ub)` for `sqrt(ua*ua+ub*ub)` | 4 of 4 | 24 | 4 |
| P4 | `<=` for `<`, `>=` for `>` | 6 of 6 | 280 | 4 |
| P5 | results rounded to 10 decimal places | 7 of 7 | 428 | 7 |
| P6 | `uEquals` ignores the uncertainty component | 4 of 4 | 1 119 | 2 |
| P7 | divide-by-zero returns `Undefined` where historical throws | 6 of 6 | 167 | 3 |
| P8 | P2's defect hidden behind `HarnessMarshallingException` | **0** | 0 | 4 |
| P9 | P2's defect hidden behind `supports() == false` | **0** | 0 | 4 |
| P10 | P2's defect only at receiver value `42.0` | **0** | **0** | **0** |
| P11 | `-0.0` collapsed to `0.0` | 3 of 4 | 59 | 3 |
| **P12** *(round 6)* | every raw result boxed into its `Value` class — right content, wrong Java type | **182 of 285** | **3 445** | **29** |

Nine of twelve diverged; **eight of those on every operation they touched**, each costing the
affected operations their stage pass. P12 is round 6's addition and was the largest blind spot on the
list: before the D-18 fix it produced **0** diverging rows and a verdict tally byte-identical to a
perfect port's. The control was re-run beside it and is unchanged (0 `DIFFER`, 0 `MIXED`, 0 diverging
operations over 19 083 rows), and the exact set of planted defects the instrument cannot see is still
`{P11 / URealValue.round()}`, so no other probe's number moved. The brief asked for detection at the 10th decimal: P3 was
caught at **one ULP** — `UINTEGER(696,0.3144000993956586)` against `UINTEGER(696,0.31440009939565855)`
— seven significant digits finer, because `UValue.canonical()` compares `Double.toString` exactly.

**The two concealment attacks are the reassuring result.** P8 (hide the wrong rows behind
`HarnessMarshallingException`, exactly what `Candidate`'s Javadoc invites) and P9 (lie in
`supports()`) both destroy the divergence — and **neither buys a stage pass**: `HARNESS_ERROR` and
`UNSUPPORTED` are non-agreements, the gate refuses, and the row note still names the subject as the
side that could not be driven. What they cost is **attribution** — the reader is told the harness
could not drive 143 rows rather than that the port is wrong on them (D-32, which *narrows* the
round-4 register).

**So the oracle claim the thesis may make is:** *a `DIFFER` / `MIXED` / `BOTH_THREW` count from this
harness is trustworthy; its silence is meaningful inside the region the corpora reach, and that region
is not measured.* The first clause is now backed by twelve planted defects; the second is exactly why
this verdict is qualified. Round 6 adds one clause to the first: *and a value means its content
together with its Java class*, which for a port of four new value classes is not a detail.

---

## 3. What must a human decide before S3 starts

One list. **B1–B12 are `specification.md` §0** (evidence, options and recommendation in place there,
one row each). **H13–H19 were added by the five review rounds and are not in that section.**

| # | Decision |
|---|---|
| **B1** | How `uDataTypes` reaches the product classpath — it is on no Maven repository under any coordinates. Recommendation **A2** (vendor the 2023 MIT source, relocated). |
| **B1a** | Whether to keep A2 on defence-in-depth grounds after its originally stated justification was refuted, or re-open A1. |
| **B2** | `SBoolean` scope: full omission, skeleton, or full port. Recommendation **skeleton** (keeps the type system bit-identical, pays none of the 1502-line registry cost). |
| **B3** | `junit-vintage-engine`: in the product build, or in a `-Pupstream-oracle` profile. Without it **38 of 41** `*Test.java` never execute and "full suite green" is a near-vacuous gate. Recommendation **(b)**, run as part of every stage's acceptance. |
| **B4** | The `'equals'` keyword collision (confirmed live against three upstream fixtures): drop `identicalExpression`, hide it behind a semantic predicate, or amend the fixtures. Recommendation **1, else 2 — not 3**. |
| **B5** | `TypeTest#testSupertype`: adopting the fork lattice breaks **exactly 10 of its 12 assertions** (independently re-derived). Adopt and handle, or keep uncertain types out of the crisp types' supertype closure. Recommendation **adopt**. |
| **B6** | `UndefinedValue` printed form — 7.5.0 prints `null`, the fork prints `Undefined`, **79 corpus entries** expect the latter. Normalise in the harness, rewrite the corpus, or revert. Recommendation **normalise**. |
| **B7** | Bug-for-bug vs fix, as **one policy** first and then per row: **33 behaviour-changing ledger rows** (`UStringValue.equals` constant `false`, `SBooleanValue.compareTo` returning 0, `UIntegerValue.hashCode` collapsing). |
| **B8** | `Op_number_sqrt` / `Op_number_pow` shadowing (whole chain confirmed, ends in `ClassCastException`): tighten `matches`, re-order registration, or teach the ops about `UReal`. Recommendation **tighten `matches`**. |
| **B9** | `ExpQuery` items 7+8 — `exists`/`forAll` over uncertain predicates. **Take both or neither**; do not ship the "additive" middle. |
| **B10** | `ExpDefSBoolean` + `ASTSBooleanDefExpression`: drop the unreachable dead code, or port it with its three defects documented. Recommendation **drop**. |
| **B11** | `UnlimitedNatural` lattice inconsistency: reproduce bit-for-bit (the same shape pre-exists upstream) or fix. Recommendation **reproduce, plus a regression test pinning the deviation**. |
| **B12** | Corpus-harness placement and the process-global `Options.explicitVariableDeclarations` write that makes JUnit-3 suite ordering load-bearing. Note that adding the non-empty assertion converts a vacuous pass into a failure — itself a behaviour change. |
| **H13** | **How S4 gates, given D-29.** A perfect port passes 74 of 285. Choose one: (a) diff `stageGateFailures` against a recorded perfect-port baseline — cheapest, recommended; (b) fund ~**273** hand-authored sign-offs (154 `AcceptedThrowPairs` + 119 degenerate) before a line of the port is in question; (c) change clause 2. **Do not choose "fall back on `isClean()`"** — that is the 119-operation gap this round measured. |
| **H14** | **Whether to build an input-domain coverage measure (D-30)**, or to accept corpus-bounded claims with the domain stated in prose in every stage document. This is the fifth door and the one open question the instrument cannot answer for you. |
| **H15** | **Whether the corpora are widened before S4** (D-28: one `RealValue`; D-31: `indexBoundaries()`; D-42: `boolean=4`), or the resulting single-valued census is signed off operation by operation with written rationales. |
| **H16** | **How fidelity is established for the 33 non-nameable operations** — `equals(Object)` first among them: a second instrument, hand-written unit tests, or a recorded declaration that they are out of scope. |
| ~~**H17**~~ | **ANSWERED in round 6: a type-aware canonical form was built.** "Right content, wrong Java type" does not count as fidelity. The decision was forced by measurement rather than taken on taste: for any single operation the historical return type is one class (0 of 285 operations answer with two), so there was no equivalent-representation case on the other side of the trade. The residual judgement, and it is small, is that the compared token is the class's *simple name* and not its package — see the struck limit 5 in §1. |
| **H18** | **Post-state** — the 8 `void` mutators cannot be shown faithful by this harness at all. Accept, or build a post-state probe. |
| ~~**H19**~~ | **ANSWERED in round 6: all three are fixed** (`d13d4858`), before any S4 number exists. |

---

## Residual risk, stated plainly

* **The instrument is not the risk it was.** Every scoring defect found in six rounds is closed and
  pinned by an executing test with a control that fails if the enforcement becomes a blanket refusal.
  Round 5 attacked the scorer directly and found nothing; its four MAJORs are one in the gate's
  ergonomics (D-29) and three in the record and the artefacts (D-33, D-34, D-35). Round 6 found the
  scoring defect round 5 had walked past: **D-18 had been sized at 193 of 285 since round 4 and never
  reproduced**, and the register called it "out of reach of a mutation experiment" when the experiment
  is four lines. The transferable lesson is not about types. It is that **a defect that has been
  measured for its size but never reproduced has not been assessed**, and a number in a limits table
  is a comfortable place for one to sit.
* **The largest live risk is silent agreement inside an unmeasured domain (D-30).** A port that is
  wrong only where the corpora do not look is reported as a full stage pass, with a
  `[DISCRIMINATING]` label that is true and irrelevant. Nothing in the harness will tell you. Only
  a widened corpus, a coverage metric, or a stage document that states its domain in prose will.
* **The second-largest is a stage automating on a boolean (D-29).** On 92 of 285 operations the pass
  bit cannot move, and the most classic port bug there is — off-by-one on a 1-based string index —
  sits in exactly that region. The rows change; the boolean does not.
* **The third is documentary.** Three of round 5's four MAJORs were reports reading stronger than the
  runs behind them, and the round-5 verifier disclosed drafting a recomputation block from memory
  before the run finished and replacing it with pasted output. Five rounds of hardening the code have
  moved the soft target to the prose. Hold S4–S7 documents to the same standard as the harness:
  every claim names `file:line` or the command, and pastes real output.
* **The specification's own risk is decision risk, not evidence risk.** No blocking decision was
  refuted and four were independently re-derived; what remains is that **all twelve blocking decisions
  — and the seven the review rounds added — are still unmade**, and B3 in particular determines
  whether S3–S7 have any automatic signal at all.

**Recommendation: S3 may start once B1–B12 and H13–H16 / H18 are answered.** D-34, D-35, D-36 and
D-18 are fixed, and H17 and H19 are struck above. The foundation carries the weight. It does not carry
an unqualified fidelity claim, and the honest form of that claim is written into
`harness-contract.md` §4 so that S4 cannot make it by accident.

**One caveat about round 6 itself, stated by its author.** The porter wrote these four fixes and the
porter does not sign off on the porter's own work. Everything above is pasted from runs and every
claim names a test method or a command, but no second party has attacked the type-bearing canonical
form the way round 5 attacked the scorer. The three things a reviewer should go at first: whether
comparing the **simple** class name rather than the fully-qualified one hides anything a port could
plausibly do; whether `noOperationAnswersWithTwoRuntimeClasses` really is the right premise for the
fix, or whether an operation could have two legitimate return classes on an input the corpora do not
reach — **D-30 applied to round 6's own justification**, which is the obvious next attack; and whether
the fourteen now-empty buckets of the restored D-35 assertion are empty because the invariant is
strong or because the D-18 fix made every subject diverge everywhere, which would make the assertion
cheap rather than sharp.
