# Foundation verdict — may S3 start?

**2026-08-17, branch `port-uncertainty-2`, after five review rounds of S1.** Written for the human
who has to decide. Three questions, answered plainly, each with the evidence named. The long records
are [`stage-01.md`](stage-01.md) §10 (verdict, story, defect register) and
[`harness-contract.md`](harness-contract.md) (the rules a stage follows). Nothing here is new
measurement; every figure is cited to the report that produced it.

---

## 1. Is the S0–S2 foundation sound enough to build S4–S7 on?

**Yes — with six documented limits, and with three fixes to make first.** Not "sound". Not "blocked".

| Stage | What it is | Verdict | Where |
|---|---|---|---|
| **S0** | Baseline: branch from `30d480db`, Java 21 / Maven 3.9.16, clean tree, recorded test counts | sound | `stage-00-baseline.md` |
| **S1** | The differential harness — the measuring instrument for every fidelity claim in S4–S7 | **`SOUND_WITH_DOCUMENTED_LIMITS`** (both round-5 reviewers, independently) | `stage-01.md` §10 |
| **S2** | The port specification, including the 12 blocking decisions | `SOUND_WITH_CAVEATS`, 175/185 citations adjudicated against source, **no blocking decision refuted**, four re-derived from source by a second party and all four `CONFIRMED` | `stage-02.md`, `audit-02-specification.md` |

**The six limits, stated as the constraints they are.** A stage that respects all six can quote
numbers from this instrument honestly; a stage that ignores any one of them will publish a figure
stronger than its evidence.

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
5. **Right content with the wrong Java type is scored `AGREE` on 193 of 285 operations** (D-18, open).
6. **The census is a joint fact about the code and the corpus.** 159 of 285 operations are
   single-valued; 23 of those are `RealValue.*` purely because the corpora hold exactly one
   `RealValue` (D-28), and `uSubstring(int,int)` gets 17 measured rows out of 432 (D-31).

**The three fixes to make before S4 quotes a number** — all test-scoped, all small, and the second
reviewer made two of them a precondition:

* **D-34** — `DiffReportWriter.writeAll`'s 3-argument form silently substitutes
  `AcceptedDegenerateOperations.none()`, so a report can assert `# accepted.degenerateOperations 0`
  while the pass it documents was granted under a sign-off. All five call sites use it.
* **D-35** — commit `0a93ad4f` weakened the standing invariant: the degenerate half of the
  fully-agreed set (159 of 285 operations) is now printed and unasserted. It should assert what it
  asserted the commit before.
* **D-36** — the gate is opt-in and the tree's own S1 acceptance test still gates on `isClean()`,
  which its own Javadoc says is not a pass predicate. Either gate it properly or label it loudly.

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
* **A wrong Java type carrying the right content**, on 193 of 285 operations (D-18).
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

Eight of eleven diverged; **seven of those on every operation they touched**, each costing the
affected operations their stage pass. The brief asked for detection at the 10th decimal: P3 was
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
is not measured.* The first clause is now backed by eleven planted defects; the second is exactly why
this verdict is qualified.

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
| **H17** | **D-18** — whether "right content, wrong Java type" counts as fidelity on 193 of 285 operations, or a type-aware canonical form is built. |
| **H18** | **Post-state** — the 8 `void` mutators cannot be shown faithful by this harness at all. Accept, or build a post-state probe. |
| **H19** | **Whether D-34, D-35 and D-36 are fixed before S4 quotes its first number.** The round-5 static reviewer made D-34 and D-35 preconditions. They are test-scoped and small. |

---

## Residual risk, stated plainly

* **The instrument is not the risk it was.** Every scoring defect found in five rounds is closed and
  pinned by an executing test with a control that fails if the enforcement becomes a blanket refusal.
  Round 5 attacked the scorer directly and found nothing; its four MAJORs are one in the gate's
  ergonomics (D-29) and three in the record and the artefacts (D-33, D-34, D-35).
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

**Recommendation: S3 may start once B1–B12 and H13–H19 are answered and D-34/D-35/D-36 are fixed.**
The foundation carries the weight. It does not carry an unqualified fidelity claim, and the honest
form of that claim is written into `harness-contract.md` §4 so that S4 cannot make it by accident.
