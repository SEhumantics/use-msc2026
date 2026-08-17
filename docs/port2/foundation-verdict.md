# Foundation verdict — may S3 start?

**2026-08-17, branch `port-uncertainty-2`, after seven review rounds of S1 — round 6's refutation and
the round-7 closure of everything it found.** Written for the human who has to decide. Three questions, answered plainly, each with
the evidence named. The long records are [`stage-01.md`](stage-01.md) §10 (verdict, story, defect
register), [`harness-contract.md`](harness-contract.md) (the rules a stage follows),
[`stage-01-round6-fixes.md`](stage-01-round6-fixes.md) (round 6's four fixes) and
[`stage-01-verification-round6.md`](stage-01-verification-round6.md) (the independent refutation of
those fixes, `3de8203e`) and [`stage-01.md`](stage-01.md) **§10.8** (round 7's closure of D-43, D-44 and
D-45, behaviour `4bb5b6fe`). Nothing here is new measurement; every figure is cited to the report that
produced it.

> **What round 6 settled, and what it did not.** The three fixes it demanded before S4 quotes a
> number — **D-34**, **D-35**, **D-36** — are **done**, and so is **D-18**, which was limit 5 below.
> All four were then **independently re-measured by a refuter who owned Maven, reproduced the *before*
> state from scratch in a detached worktree at `90404528`, and confirmed every one of them**; it
> found **no false green** in five constructions and the perfect-port control **intact**
> (0 `DIFFER`, 0 `MIXED`, 0 diverging operations over 285 operations / 19 083 rows, the same 74 stage
> passes). Its verdict is nevertheless **`DEFECTIVE`**, on one new MAJOR: **the Java type is
> *observed* on the reference side and merely *declared* on the ported side**, so round 6's headline
> detection figure is reproduced exactly — 3 445 `DIFFER`, 182 of 285, 74 → 45 — by a
> **content-perfect** port whose adapter follows the documented worked example, and is erased by one
> line of adapter code. That is canonical **D-43**.
>
> **Round 7 (`4bb5b6fe`) closed D-43, D-44 and D-45, so documented limits go six → five.** The ported
> side's class is now *measured*: `UValue.observedFrom(Object)` reads it off the object a side returned
> and is what `fromHistorical` itself calls, the one-argument `asJavaType(String)` is **deleted** (the
> only stating route, `declaredJavaType(name, why)`, refuses a blank reason), and the token's provenance
> — `OBSERVED` / `DECLARED` / `ASSUMED` — is written into the note of every type-mismatch row. Measured
> in one run over the same 285 operations: the content-perfect port with an **observing** adapter is
> `DIFFER 0`, 74 stage passes, verdict tally identical to the control's; with a **factory-typed** adapter
> it is still `DIFFER 3 445` / 182 ops / 45 passes, and **3 445 of those rows now say "the subject's class
> was ASSUMED, not observed"** against **0** of the planted wrong-class port's — the two readings of the
> number are separated in the rows. D-18 is not regressed (the planted defect still measures 3 445 on
> 182 operations). Both readings are pinned as adjacent tests. **H17, H19 and H20 are answered**;
> H13–H16 and H18 are open questions for you. **Round 7 has not been independently refuted.**
>
> Acceptance, round 7: `mvn -q clean && mvn -B verify -Djava.awt.headless=true` → `BUILD SUCCESS`,
> **79 surefire + 130 failsafe = 209 methods, 0 failures** at `4bb5b6fe` (+2 on round 6's 207, both new
> tests named in §10.8.5), two byte-identical runs, goldens unchanged and **not** refreshed — their
> `sha256` digests still equal the round-6 ones. Round 6's own acceptance, re-run by the refuter:
> `BUILD SUCCESS`, **77 + 130 = 207** at `854abb83`;
> `git diff --name-status 30d480db..HEAD -- '*/src/main/*'` empty; tree clean; goldens
> byte-identical over two runs. The baseline at `90404528` was re-measured as **202**, so the +5 is
> accounted for method by method. **The 68/198 figure some briefs still carry is stale.**
>
> **Id collision, so no evidence is orphaned.** `stage-01-verification-round6.md` numbered its three
> findings `D-37`/`D-38`/`D-39`, keys the canonical register had already spent on round-5 MINORs.
> Their canonical keys are **D-43** (that report's `D-37`), **D-44** (`D-38`) and **D-45** (`D-39`);
> `stage-01.md` §10.5 carries the mapping.

---

## 1. Is the S0–S2 foundation sound enough to build S4–S7 on?

**Yes for S3 — with five documented limits. The three fixes this section used to demand are made and
independently confirmed** (round 6, `d13d4858`; refutation `3de8203e`), **and the one MAJOR the
refutation opened is closed** (round 7, `4bb5b6fe`; §10.8). An S4 type-fidelity figure is now
attributable — provided the stage states how its adapter obtained the class token, which is still the
stage's job. Not "sound". Not "blocked".

| Stage | What it is | Verdict | Where |
|---|---|---|---|
| **S0** | Baseline: branch from `30d480db`, Java 21 / Maven 3.9.16, clean tree, recorded test counts | sound | `stage-00-baseline.md` |
| **S1** | The differential harness — the measuring instrument for every fidelity claim in S4–S7 | **`SOUND_WITH_DOCUMENTED_LIMITS`** (both round-5 reviewers, independently). Round 6 closed all four remaining defects, D-18 among them; its refuter **confirmed all four, found no false green and an intact control, and returned `DEFECTIVE`** on one new MAJOR against the D-18 fix's *attribution* (**D-43**). **Round 7 closed D-43 and both of its MINORs and has not itself been refuted.** No scoring defect is open, and no false-divergence mode is open either. | `stage-01.md` §10 (§10.8 for round 7), `stage-01-round6-fixes.md`, `stage-01-verification-round6.md` |
| **S2** | The port specification, including the 12 blocking decisions | `SOUND_WITH_CAVEATS`, 175/185 citations adjudicated against source, **no blocking decision refuted**, four re-derived from source by a second party and all four `CONFIRMED` | `stage-02.md`, `audit-02-specification.md` |

**The five limits, stated as the constraints they are.** A stage that respects all five can quote
numbers from this instrument honestly; a stage that ignores any one of them will publish a figure
stronger than its evidence. **The count fell from six in round 7.** Limit 5 was "right content in the
wrong Java type is scored `AGREE`" (D-18, closed in round 6); its successor was "the ported side's type
is declared, not observed" (D-43, closed in round 7). Both are struck below, and both are kept visible,
because the second one is the sharpest lesson these seven rounds produced.

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
5. ~~**The Java type is observed on the reference side and only *declared* on the ported side**~~ —
   **CLOSED in round 7** (D-43). The obligation it left on a stage has not vanished, it has just become
   checkable: **call `UValue.observedFrom(theObjectYourPortReturned)`**, and state in the stage document
   how the token was obtained. `fromHistorical` observes the reference's class on every branch and the
   ported side now uses the same call; the one-argument `asJavaType(String)` is deleted, so the only way
   to state a class is `declaredJavaType(name, why)` with a non-blank reason that lands in the row note;
   and `UValue.typeProvenance()` (`OBSERVED` / `DECLARED` / `ASSUMED`) is printed into every type-mismatch
   row. **What the round measured, in one run over the same 285 operations:** a content-perfect port with
   a factory-typed adapter still measures `DIFFER 3 445 / 182 of 285 / 45 passes` — the four numbers this
   document publishes as P12's detection power — but **3 445 of its rows now say "the subject's class was
   ASSUMED, not observed"**, while the planted wrong-class port's 3 445 rows say that on **0**; and the
   *same* content-perfect port with an observing adapter measures `DIFFER 0` and the control's exact 74
   stage passes and verdict tally. So the figure is attributable, the check is not weakened (D-18 still
   detected on 182 operations), and the residual is a limit rather than a defect: the token is only as
   honest as the adapter's *choice of object*, and `declaredJavaType` believes what it is told — it just
   costs a sentence a reviewer reads. Evidence: `stage-01.md` §10.8.
6. **The census is a joint fact about the code and the corpus.** 159 of 285 operations are
   single-valued; 23 of those are `RealValue.*` purely because the corpora hold exactly one
   `RealValue` (D-28), and `uSubstring(int,int)` gets 17 measured rows out of 432 (D-31).

*(The closed limit, kept visible: ~~right content with the wrong Java type is scored `AGREE` on 193 of
285 operations~~ — **closed in round 6**. `UValue.canonical()` is type-bearing, so a `Kind` or
runtime-class difference is a `DIFFER` naming both fully-qualified types. The refuter reproduced the
*before* state independently at `90404528` — identity, boxed and factory-typed subjects gave three
byte-identical tallies, **0 `DIFFER`, 74 stage passes** — so D-18 was real, exactly as large as the
register said, and the blind spot is genuinely closed. What the earlier revision of this line claimed —
"the fix has no false-divergence mode" — **is withdrawn**: §4.1 of the refutation is one.)*

**The three fixes this section demanded before S4 quotes a number are made** — all test-scoped,
commit `d13d4858`, evidence in `stage-01-round6-fixes.md` §2–§4, **each independently verified in
`stage-01-verification-round6.md` §3**:

* **D-34 — done, confirmed.** `write` and `writeAll` both require the sign-off set and no eliding
  overload exists; a reflective assertion fails if one comes back. Before: `stage pass WITH the
  sign-off? true` printed beside `# accepted.degenerateOperations 0`. After: the two headers of one
  sweep, written under `signed` and under `none()`, are asserted **unequal**. The refuter checked the
  two surviving writers and the reflective guard from source and from `git diff`, and saw the header
  move with the sign-off in a live run.
* **D-35 — done, confirmed, including the experiment.** Both halves of the fully-agreed split are
  asserted again, keeping the split. The refuter re-ran the experiment the round demanded: on the tree
  at **`90404528`** the restored assertion fails with `expected: <[]> but was: <[RealValue.value()]>`
  while the nine other cases and the pre-existing assertion pass. **Correction to the record:** commit
  `d13d4858`'s message says "Verified against unmodified HEAD behaviour" and
  `stage-01-round6-fixes.md` §3.1 says "the same run that HEAD's own test passes"; in both, "HEAD" means
  the tree at `90404528`, which was HEAD when the work was done — today's HEAD carries the assertion and
  passes. Substance right, referent misleading, and corrected in place in both records. These records
  exist to be re-run.
* **D-36 — done for the acceptance test, confirmed at that scope.** `UncertaintyDifferentialSmokeTest` gates through
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
  invisible** (D-18, closed in round 6). It moves to the *Detected* table below as P12. Round 6's
  refutation showed the same port could be made invisible again with one line of adapter code
  (`.asJavaType(v.javaType())`, measured `DIFFER 3 445 → 0`); **round 7 deleted that route** — the only
  stating call now demands a written reason and marks the row — so what remains is that an adapter can
  still observe the *wrong object*, which no round has yet measured. `equals(Object)` remains the sharp
  edge here: a type-confused port gets `equals` wrong first, and `equals` is one of the 33 operations
  that cannot be named at all.
* **A port that relocated the package, on the `OPAQUE` branch — where the rationale for
  package-insensitivity does not hold** (**D-44**, MINOR, new). The compared type token is the class's
  *simple* name precisely so a relocated port is not 100 % divergence, and
  `theTypeTokenIsPackageInsensitiveOnPurpose` pins that — but only for `Kind.UREAL`, whose content
  carries no class name. `UValue.opaque(className, repr)` puts the **fully-qualified** name into the
  compared content and `HistoricalOracle.opaqueRepresentation` adds the FQNs of every field's declaring
  class, so a relocated port **is** a divergence on every `OPAQUE` row: **197 rows across 17
  operations** (`type()` / `getRuntimeType()` × 16, `UIntegerValue.getuInteger()` × 1). This is the
  pre-existing `OPAQUE` limit, not a scoring error; what round 6's refutation found is that round 6
  rested a design decision on a rationale its own numbers contradict on 197 rows. **Closed as a
  documentation defect in round 7:** `UValue`'s class comment states both costs of comparing the simple
  name, and `theTypeTokenIsPackageInsensitiveOnPurpose` now asserts that two `opaque()` values differing
  only in package have the *same* token and *different* canonical forms — so package-insensitivity is a
  property of the **token**, never of the row. The limit itself stands; only the false rationale is gone.
* **`SBooleanValue` (39 operations), collection receivers, the `ocl.type.*` hierarchy and
  `uDataTypes.*`** — declared out of reach; reported `UNSUPPORTED`, never silently skipped.
* **An operation the corpora leave single-valued** — 159 of 285. The harness now *refuses* these
  rather than greening them, which is the D-15 fix; it still cannot measure them.

### Detected — measured, not argued

Round 5 is the first direct measurement of detection power; **round 6's refuter re-measured all of it
end to end from its own `mvn -B verify` run** (seed 20260817, 285 operations, stage-shaped domains,
19 083 rows each). A second, independently loaded `HistoricalOracle` plays a perfect port; a wrapper
applies exactly one named infidelity. **Control, re-measured and unchanged: 17 199 measured, 17 199
agreed, `{AGREE=17199, BOTH_THREW=910, HARNESS_ERROR=883, UNMEASURABLE=91}`, 0 `DIFFER`, 0 `MIXED`,
0 diverging operations, 74 of 285 stage passes** — so every divergence below is attributable to the
planted defect.

| probe | planted infidelity | detected on | detecting rows | stage passes | lost |
|---|---|---|---|---|---|
| **P0 / control** | **identity — a perfect port** | **0** | **0** | **74** | **0** |
| P1 | 0-based port of the 1-based string index | 3 of 3 | 138 | 74 | 0 (D-29) |
| P2 | uncertainties combined with `+` instead of `sqrt(ua²+ub²)` | 4 of 4 | 468 | 70 | 4 |
| P3 | `Math.hypot(ua,ub)` for `sqrt(ua*ua+ub*ub)` | 4 of 4 | 24 | 70 | 4 |
| P4 | `<=` for `<`, `>=` for `>` | 6 of 6 | 280 | 70 | 4 |
| P5 | results rounded to 10 decimal places | 7 of 7 | 428 | 67 | 7 |
| P6 | `uEquals` ignores the uncertainty component | 4 of 4 | 1 119 | 72 | 2 |
| P7 | divide-by-zero returns `Undefined` where historical throws | 6 of 6 | 167 | 71 | 3 |
| P8 | P2's defect hidden behind `HarnessMarshallingException` | **0** | 0 | 70 | 4 |
| P9 | P2's defect hidden behind `supports() == false` | **0** | 0 | 70 | 4 |
| P10 | P2's defect only at receiver value `42.0` | **0** | **0** | **74** | **0** |
| P11 | `-0.0` collapsed to `0.0` | 3 of 4 | 59 | 71 | 3 |
| **P12** *(round 6)* | every raw result boxed into its `Value` class — right content, wrong Java type | **182 of 285** | **3 445** | **45** | **29** |

Nine of twelve diverged; **eight of those on every operation they touched**, each costing the
affected operations their stage pass. Corpus sensitivity (full-vs-finite detecting rows) is unchanged
from round 5 on every probe, and the exact set of planted defects the instrument cannot see is still
`{P11 / URealValue.round()}`, so no other probe's number moved. The brief asked for detection at the
10th decimal: P3 was caught at **one ULP** — `UINTEGER(696,0.3144000993956586)` against
`UINTEGER(696,0.31440009939565855)` — seven significant digits finer, because `UValue.canonical()`
compares `Double.toString` exactly.

> **Read P12's row with the two probes round 7 added beside it.** Before the D-18 fix P12 produced **0**
> diverging rows and a tally byte-identical to a perfect port's — the refuter reproduced that at
> `90404528` — so the blind spot was real and is closed. The refuter then measured a **content-perfect**
> port whose adapter is factory-typed the way the documented worked example was, and got
> `DIFFER 3 445, 182 of 285, 74 → 45, lost 29`: **the identical four numbers** (D-43). Round 7 pins both
> readings as adjacent tests and separates them in the evidence: `P13-factory-typed-adapter` (a port with
> **no defect**) measures `3 445 / 182 / 45` with **3 445** rows saying "the subject's class was ASSUMED,
> not observed"; `P12-boxed-primitive` (the real defect) measures `3 445 / 182 / 45` with **0**; and
> `P14-observing-adapter` — the same faithful port, attributing through `UValue.observedFrom(Object)` —
> measures `0 / 0 / 74`, tally-identical to the control. **So "182 of 285" is attributable now, provided
> the stage says how its adapter got the token.**

**The two concealment attacks are the reassuring result.** P8 (hide the wrong rows behind
`HarnessMarshallingException`, exactly what `Candidate`'s Javadoc invites) and P9 (lie in
`supports()`) both destroy the divergence — and **neither buys a stage pass**: `HARNESS_ERROR` and
`UNSUPPORTED` are non-agreements, the gate refuses, and the row note still names the subject as the
side that could not be driven. What they cost is **attribution** — the reader is told the harness
could not drive 143 rows rather than that the port is wrong on them (D-32, which *narrows* the
round-4 register).

**So the oracle claim the thesis may make is:** *a `DIFFER` / `MIXED` / `BOTH_THREW` count from this
harness is trustworthy; its silence is meaningful inside the region the corpora reach, and that region is
not measured.* The first clause is backed by twelve planted defects and fourteen probes; the second is
exactly why this verdict is qualified. Round 6 adds one clause to the first — *and a value means its
content together with its Java class*, which for a port of four new value classes is not a detail — and
round 7 makes that clause symmetrical: **both sides' classes are observed off the objects they returned**,
a row whose token was merely assumed says so, and the only route that states a class costs a written
reason. What a stage still owes the reader is one sentence saying how its adapter obtained the token.

---

## 3. What must a human decide before S3 starts

One list. **B1–B12 are `specification.md` §0** (evidence, options and recommendation in place there,
one row each). **H13–H20 were added by the seven review rounds and are not in that section.** Three of
the eight are struck: H17 and H19 in round 6, H20 in round 7.

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
| ~~**H17**~~ | **ANSWERED in round 6: a type-aware canonical form was built.** "Right content, wrong Java type" does not count as fidelity. **With one correction from the refutation (D-45):** the justification as written — "a historical operation's declared return type is one class, so there is exactly one right answer" — is **false for 84 of 285 operations**, which declare an interface or a non-final class; the sharpest case is the nine `UncertainBooleanValue`-declared operations, which return the `UBooleanValue` subclass through a superclass-declared signature, so a port returning the *declared* type reads as divergence on every driven row. The **conclusion** survives, because `noOperationAnswersWithTwoRuntimeClasses` measures it at **0 of 285** over the shipped corpora — but it now rests on a corpus measurement and therefore **inherits D-30**. Two smaller residual judgements, both now handled in round 7: the compared token is the class's *simple name* (D-44 — its two costs are stated and the OPAQUE half is asserted), and on the ported side the token used to be declared rather than observed (D-43, closed; see H20). |
| **H18** | **Post-state** — the 8 `void` mutators cannot be shown faithful by this harness at all. Accept, or build a post-state probe. |
| ~~**H19**~~ | **ANSWERED in round 6: all three are fixed** (`d13d4858`), before any S4 number exists, and all three independently confirmed (`3de8203e`). |
| ~~**H20**~~ | **ANSWERED in round 7 (`4bb5b6fe`): D-43's four bullets are funded and done, and one of them goes further than asked.** (1) `UValue.observedFrom(Object)` exists, `fromHistorical` calls it on all fourteen unwrapping branches, and `Candidate`'s Javadoc states as a second invariant that an adapter not routing through it is **declaring, not observing**; (2) `harness-contract.md` §7's third trap carries the 182-of-285 / 29-stage-pass figure **and the incentive hazard in one sentence**; (3) `StubCandidate` attributes every result through one named method — by **declaring with a written reason**, because S1 has no ported object to observe, which is measured rather than asserted: fabricating a stand-in to observe re-captions all 226 disagreeing golden rows as a "java type mismatch" (§10.8.4); (4) both readings of the 3 445 are pinned as adjacent tests. **Beyond the bullets:** the one-argument `asJavaType(String)` is deleted — the refuter's own §4.2 is the argument for removing it — so the only stating route demands a non-blank reason, and `typeProvenance()` carries `OBSERVED`/`DECLARED`/`ASSUMED` into the note of every type-mismatch row, which is what separates the two 3 445s in the evidence rather than in a footnote. **What is still yours to decide is smaller and belongs in the stage template:** every S4–S7 document must say in one sentence how its adapter obtained the class token, and a reviewer should reject a type-fidelity figure that does not. **Not independently refuted yet** — the obvious next attacks are an adapter that observes the *wrong* object, a non-blank but false `declaredJavaType` reason, and whether provenance should reach a report **header aggregate** and not only the row note. |

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
* **The risk that a number reads two ways is closed in the instrument and stays open in the prose
  (D-43, closed round 7).** Round 6's own refutation is the case: the fix that closed a blind spot
  produced a figure — 3 445 `DIFFER`, 182 of 285, 29 stage passes — that a **content-perfect** port
  reproduces exactly when its adapter follows the documented worked example, and that one line of adapter
  code took to zero. The scoring code was right; the *attribution* was not. Round 7 made the ported
  side's class a measurement (`observedFrom`), deleted the one-line declaring route, marked every
  type-mismatch row with how its token was obtained, and pinned **both** readings of the 3 445 as adjacent
  tests that assert their own equality — so if the ambiguity ever returns, a test says so. **What no code
  change can close is the reporting half:** a stage that does not say how its adapter obtained the token
  is publishing a figure a reader cannot attribute, and the same author facing spurious rows is still the
  person most tempted to name a type instead of finding one out. Hold S4 to the one sentence.
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

**Recommendation: S3 may start once B1–B12 and H13–H16 / H18 are answered.** The bar this section used
to set for S4 — do not quote a type-fidelity number until H20 is done — **is met**: D-43's bullets are
done (round 7, `4bb5b6fe`, §10.8), and what replaces the bar is a reporting rule, that a stage quoting
such a number states in one sentence how its adapter obtained the class token. D-34, D-35, D-36 and D-18
are fixed and independently confirmed; D-43, D-44 and D-45 are fixed and **not yet** independently
confirmed; H17, H19 and H20 are struck above, H17 with the D-45 correction. The
foundation carries the weight. It does not carry an unqualified fidelity claim, and the honest form of
that claim is written into `harness-contract.md` §4 so that S4 cannot make it by accident.

**Round 6 has now been reviewed by a second party, and here is what it went at.** The porter's own
caveat named three attacks; the refuter ran all three and its answers are the reason limits stayed at
six through round 6 (they fell to five in round 7). (i) *Does comparing the **simple** class name hide anything?* — the hole is not the one expected:
on the `OPAQUE` branch the compared content carries fully-qualified names, so the stated rationale
fails on 197 rows (**D-44**, MINOR). (ii) *Is `noOperationAnswersWithTwoRuntimeClasses` the right
premise?* — the measurement holds at 0 of 285, but the **reason** given for it is false for 84 of 285
declared return types (**D-45**, MINOR), so the premise is a corpus fact and inherits D-30, exactly as
this document predicted. (iii) *Are the fourteen empty D-35 buckets strong or cheap?* — confirmed as a
live guard against future growth, extensional rather than predicate-derived, and proving nothing about
today; the porter had already flagged it. Beyond those, the refuter attacked and could **not** break
five false-green constructions, including using the type token to satisfy the D-15 discrimination
clause on a representation difference (measured 0 of 274 operations, and guarded indirectly but soundly
by `noOperationAnswersWithTwoRuntimeClasses`). **What it did break is the attribution of the headline
number: D-43** — which round 7 then closed, and which is now the seventh round's own headline: *a fix is
not assessed until someone measures what a **faithful** port does under it, and if a defect's signature
and a faithful port's signature are the same number, the number is not evidence.* Round 7's work is
therefore itself unverified by a second party, and the three attacks it invites are named in H20.
