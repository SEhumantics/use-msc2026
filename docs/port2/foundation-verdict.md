# Foundation verdict — may S3 start?

**2026-08-17, branch `port-uncertainty-2`. Eight review rounds of S1 are complete; behaviour through
`066fe15c`, the independent refutation of it at `c91277ff`.** Written for the human who has to decide.
Three questions, answered plainly. **Nothing here is new measurement** — every figure is cited to the
report that produced it, and where a figure was refuted the refutation is cited instead.

**Provenance note (2026-08-21).** `audit-02-specification.md` and `stage-02.md`, cited in the table
below, were consolidated during a documentation cleanup and no longer exist as separate files; the
S2 verdict they support is already stated in this table's own S2 row. Full original content in git
history.

The long records: [`stage-01.md`](stage-01.md) **§10** (narrative, the single canonical defect register
§10.4, the id re-keying map §10.5, the round-5 detection table §10.3, round-7 in §10.8, round-8's build
in §10.9, round-8's refutation in §10.10) and [`harness-contract.md`](harness-contract.md) (the rules a
stage follows, **§8 the S4 checklist**). Where any document disagrees with `stage-01.md` §10.4, §10.4
wins.

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
>   `harness-contract.md` §7 and §8.
> * The dominant risks to S4 are **not** the type question: they are **D-30** (no input-domain coverage
>   figure exists anywhere) and **D-29** (a perfect port passes only 74 of 285 gates).

---

## 1. May S3 start?

**Yes.** Nothing in the instrument blocks it. S3 is blocked only on decisions a human owes, and the list
is §3: **B1–B12** from `specification.md` §0 plus **H13–H22** added by the eight rounds. B3 in particular
decides whether S3–S7 have any automatic signal at all.

Two preconditions bear on **S4**, not on S3, and both are cheap enough to clear first:

1. **D-52 (MAJOR, open).** `harness-contract.md` §7 mandates `javaTypeMismatchCount() == 0` as an S4 gate
   clause. Not sound as written: the round-8 refuter took a port with a **real 401-row / 9-operation
   wrong-class defect** to `javaTypeMismatch 0`, byte-identical to the perfect-port control, disclosure in
   **0 rows**, by changing **one line** of the adapter — `observedFrom(anEmptyStandInClass)`
   (`stage-01.md` §10.10.2). Fix: mandate the adapter's **shape**, not a fourth API — the observed object
   must be the invocation's own return value, as `PortedInfidelityDetectionPowerTest.
   observeWhatThePortReturned` (`:1116-1126`) already does. **Now written into `harness-contract.md` §7
   and §8.** Inert at S1 either way (74 stage passes with the defect and without it).
2. **The provenance aggregate (H21) — CLOSED 2026-08-17, BUILT.** `OBSERVED`/`ASSUMED` reached only the
   row note with no header count (D-49 residue, D-48). Now has one at both scopes —
   `# rows.subjectTypeObserved`/`.subjectTypeAssumed` and the per-operation equivalents, from
   `DifferentialSweep.Result.subjectTypeObservedCount()`/`.subjectTypeAssumedCount()`, carried in
   `summary()` and `stageStatement()`. Both the round-8 porter and refuter independently called it the
   cheapest item on the list (`stage-01.md` §10.10.1).

**Verdicts by stage.**

| Stage | What it is | Verdict | Where |
|---|---|---|---|
| **S0** | Baseline: branch from `30d480db`, Java 21 / Maven 3.9.16, clean tree, recorded test counts | sound | `stage-00-baseline.md` |
| **S1** | The differential harness — the measuring instrument for every fidelity claim in S4–S7 | **`SOUND_WITH_DOCUMENTED_LIMITS`**, three reviewers independently (rounds 5, 7, 8). No scoring defect open; no false-divergence mode open. One MAJOR open (D-52) against a rule dated to S4. | `stage-01.md` §10 |
| **S2** | The port specification, including the 12 blocking decisions | `SOUND_WITH_CAVEATS`; 175/185 citations adjudicated against source, **no blocking decision refuted**, four re-derived by a second party and all four `CONFIRMED` | `stage-02.md`, `audit-02-specification.md` |

**Acceptance, at `c91277ff`:** `mvn -q clean && mvn -B verify -Djava.awt.headless=true` → `BUILD SUCCESS`,
**79 surefire (78 use-core + 1 use-gui) + 130 failsafe (1 `OCLExpressionIT` + 129 `ShellIT`) = 209 methods,
0 failures / 0 errors / 0 skipped**, delta 0 against the stated baseline; two runs byte-identical outside
Maven's own logging (`md5 919997f36959cf8cc6f8af4a64030ecd` over the full stripped output);
`git diff --name-status 30d480db..HEAD -- '*/src/main/*'` **empty**; tree clean (`stage-01.md` §10.10.2).

---

## 2. What can this oracle detect, and what can it not?

Study A is defined as agreement against this oracle, so this section must survive a hostile reviewer.
Full measurement provenance: `stage-01.md` §10.3 (round-5 detection table), §10.9 (round-8 build), §10.10
(round-8 refutation).

### 2.1 Invisible

1. **A real defect at an input the corpora never generate (D-30, MAJOR, open).** Round 5 planted a wrong
   uncertainty-combination rule restricted to receiver value `42.0`. Measured: **0 `DIFFER` rows**, a
   verdict tally **byte-identical to a perfect port's** on all 19 083 rows, and a full stage pass on the
   operation carrying the defect, printed with `[DISCRIMINATING]` beside it — true and irrelevant to the
   one input never tried. Same in miniature: a `-0.0 → 0.0` collapse is caught on `floor()`, `neg()` and
   `mult(value)` and **missed on `URealValue.round()`**, because no shipped input drives the historical
   `round()` to a negative zero. **No input-domain coverage figure is computed, published or gated
   anywhere in the instrument** — this is what H14 (§3.0) was decided to build.
2. **A subject that suppresses its own measurement (D-17, MAJOR, open; narrowed by D-32).** A port may
   raise `HarnessMarshallingException` or lie in `supports()`. Both destroy the divergence and detect
   **0** operations, but **neither buys a stage pass** (`HARNESS_ERROR` and `UNSUPPORTED` are
   non-agreements). What is destroyed is **attribution**: the reader is told the harness could not drive
   the rows, not that the port is wrong on them.
3. **A Java-runtime-type-only difference — counted, not scored (D-43, demoted round 8; D-52 open).** Where
   content matches and only the runtime class differs, the row is `AGREE`, counted in
   `javaTypeMismatchCount()` / `# rows.javaTypeMismatch` / `# op.<key>.javaTypeMismatch`, printed
   unconditionally by `stageStatement()`, and **does not fail a gate at S1**. Extent: **3 445 rows across
   182 of 285 operations** for a non-attributing adapter, costing a faithful port **29** stage passes it
   would otherwise keep plus **4** an otherwise do-nothing receiver-echoing subject would keep (D-57,
   `stage-01.md` §10.4). Dated and narrow: **at S1 there is no
   ported implementation to observe** — writing one *is* S4 — so a type-only divergence today measures the
   adapter, not the port. The S4 obligation that reverses this is `harness-contract.md` §7; D-52 is the
   amendment it needed.

   > **The sentence a stage must print beside every agreement figure taken from this harness: an `AGREE`
   > row may be an agreement on the payload alone.**

**Also invisible, structurally, and none of it new:** 33 of 318 public operations cannot be named as a
`UOp` at all — no row, no verdict, not even `UNSUPPORTED` (`equals(Object)`/`compareTo(Object)` on all
eight receivers among them; **"285 operations" is not the ported surface, it is the surface this harness
can name**). All **8** `void` mutators are `UNMEASURABLE` since the receiver is never re-read — a void
operation cannot be shown faithful by this harness at all. `SBooleanValue` (39 ops), collection
receivers, `ocl.type.*`, `uDataTypes.*` are declared out of reach and reported `UNSUPPORTED`, never
silently skipped. **159 of 285 operations** the corpora leave single-valued: the gate *refuses* these
(D-15 fix) but still cannot measure them. Two latent holes the round-8 demotion created, both measured
unreachable in today's corpus and both inheriting D-30: `OPAQUE` content's non-injective
`className|repr` concatenation (**D-55**), and a **nested** type-only difference still scored `DIFFER`
because `SEQUENCE` content embeds each element's `canonical()` (**D-56**) — `stage-01.md` §10.4.

### 2.2 Detected — measured, not argued

Control: two independently loaded `HistoricalOracle` instances, seed 20260817, 285 operations over
stage-shaped domains:

```
rows 19083   measured 17199   agreed 17199
verdict tally {AGREE=17199, BOTH_THREW=910, HARNESS_ERROR=883, UNMEASURABLE=91}
DIFFER 0   MIXED 0   diverging operations 0   javaTypeMismatch 0
stage passes 74 of 285  (isStagePass(1, none()))
```

`md5 c724bd19dbed9071ffc8762675584107` — **identical in rounds 7 and 8**, and reproduced independently by
the round-8 refuter's own rig over the same operations and domains (`stage-01.md` §10.10.1). No subject
can exceed 74: clause 2 refuses any operation with a `BOTH_THREW` / `HARNESS_ERROR` / `UNMEASURABLE` row,
and clause 3 is computed from the **reference's** distinct values, so every subject's pass set is a
subset of one fixed by the reference alone.

| probe | planted infidelity | ops detected | stage passes (control 74) |
|---|---|---|---|
| **P0 / control** | **identity — a perfect port** | **0** | **74** |
| P1 | 0-based port of the 1-based string index | 3 of 3 | 74 (D-29) |
| P2 | uncertainties combined with `+` instead of `sqrt(ua²+ub²)` | 4 of 4 | 70 |
| P3 | `Math.hypot(ua,ub)` for `sqrt(ua*ua+ub*ub)` | 4 of 4 | 70 |
| P4 | `<=` for `<`, `>=` for `>` | 6 of 6 | 70 |
| P5 | results rounded to 10 decimal places | 7 of 7 | 67 |
| P6 | `uEquals` ignores the uncertainty component | 4 of 4 | 72 |
| P7 | divide-by-zero returns `Undefined` where historical throws | 6 of 6 | 71 |
| P8 | P2's defect hidden behind `HarnessMarshallingException` | **0** | 70 |
| P9 | P2's defect hidden behind `supports() == false` | **0** | 70 |
| P10 | P2's defect only at receiver value `42.0` | **0** | **74** |
| P11 | `-0.0` collapsed to `0.0` | 3 of 4 | 71 |

**8 of 11 caught on every operation they touch.** The three misses are exactly the invisible classes
above: P8 and P9 (D-17), P10 (D-30). Every figure is unchanged between rounds 7 and 8 — no content probe
regressed under the demotion, independently confirmed by the round-8 refuter's own rig (`stage-01.md`
§10.10.1). Resolution: P3 was separated at **one ULP**, seven significant digits finer than the brief's
"10th decimal", because `canonical()` compares `Double.toString` exactly.

**The type dimension, reported separately since round 8:** the same **3 445** `javaTypeMismatch` rows are
produced both by a port with a real wrong-class defect and by a defect-free port whose adapter never
attributed — the only discriminator is the row note's provenance clause, which now has a header
aggregate (H21, above). Before round 8 those rows were `DIFFER` and cost a **faithful** port 29 stage
passes; that false-divergence mode is gone, independently confirmed. The demotion did not swallow content
differences: a content defect plus a type difference still measures `DIFFER 468` (same as the content
defect alone); placed **on the very rows the type difference lives on**, `DIFFER` goes 0 → **1 831 across
143 operations**, losing 3 stage passes. Nor is the type figure a lower bound — adding a content defect
moves it from 3 445/182 to **1 883/42**, because a doubly-wrong row is a `DIFFER` and leaves the count
(D-54). And it is **not yet gate-worthy**: D-52. (`stage-01.md` §10.10.1 for all figures in this
paragraph.)

### 2.3 The oracle claim a thesis may make

> A `DIFFER` / `MIXED` / `BOTH_THREW` / `throwClassMismatch` count from this harness is **trustworthy**.
> Its **silence** is meaningful only inside the region the corpora reach, on operations it can name, and
> that region is **not measured**. An `AGREE` row may be an agreement on the payload alone. A stage pass
> certifies "no divergence over the inputs we tried" and nothing more.

---

## 3. What must a human decide

One list. **B1–B12 are `specification.md` §0** — evidence, options and recommendation in place there.
**H13–H22 were added by the eight review rounds** and are not in that section.

### 3.0 DECIDED — 2026-08-17 (four items)

The user decided four items in one round. All four are binding and none is re-litigable. **Three went
against the recommendation recorded above, and all three expand scope** — recorded here as the user's
decision, with the recommendation that was not taken named, so the record shows a considered choice and
not a drift.

| # | Chosen | Recommendation NOT taken | Status |
|---|---|---|---|
| **B3** | **(b)** a `-Pupstream-oracle` Maven profile: `junit-vintage-engine` 5.7.0, test scope, `use-core` + `use-gui`, **no test file touched**. Every stage from S3 runs both `mvn -B verify -Djava.awt.headless=true` and `mvn -B verify -Pupstream-oracle -Djava.awt.headless=true`. | — the recommendation **was** taken | **BUILT and MEASURED, and the gate now ASSERTS its own floor** via `scripts/upstream-oracle-gate.sh` / `scripts/UpstreamOracleFloor.java`. `upstream-oracle-profile.md`: default 11 classes / **211** distinct methods (199 asserting); profile **51 classes / 498 distinct methods (465 asserting), 0 failures, 0 errors**. |
| **B7** | **FIX** the historical defects, documenting each. | **bug-for-bug reproduction** — the recorded recommendation, **not taken** | Open work for S4–S7. Each of the 33 behaviour-changing rows in `specification.md` §7.2 needs a fix, a written justification, and the print-output delta where one exists. **The per-row plan is [`b7-fix-plan.md`](b7-fix-plan.md)**, which supersedes every `DEFER` in `spec-parts/16-modernization-ledger.md`. |
| **H14** | **BUILD an input-domain coverage measure** (D-30). | **prose-stated domains** in every stage document — the recorded position, **not taken** | Open work. Converts D-30 from a caveat carried in prose into an instrument to be built. **The design is [`h14-coverage-design.md`](h14-coverage-design.md)** (design only, unimplemented); `harness-contract.md` §5 and §8 step 5 no longer mandate the prose-only position. |
| **B2** | **FULL PORT of `SBoolean`**, all 39 operations. | **skeleton** (keep `SBooleanType` only) — the recorded recommendation, **not taken** | Open work. `StandardOperationsSBoolean`'s 1502 lines enter the build with **zero** fork-test coverage behind them, so the differential harness is the only instrument that can judge the 39 operations — **and it declines `SBooleanValue` by name today, so marshalling it is a hard prerequisite** ([`b7-fix-plan.md`](b7-fix-plan.md) §6). |

**H21 is DONE**: the provenance aggregate was built in this round — header keys at both file and
per-operation scope, pinned by a regression test, both S1 goldens refreshed by exactly four added header
lines each.

**B1–B12, evidence and recommendation in `specification.md` §0; open items only summarised here:**

| # | Decision |
|---|---|
| **B1 / B1a** | How `uDataTypes` reaches the product classpath (on no Maven repository under any coordinates). Recommendation **A2**, vendor the 2023 MIT source relocated; B1a is whether to keep A2 on defence-in-depth grounds or re-open A1. |
| **B2** | `SBoolean` scope — ✅ **DECIDED 2026-08-17: FULL PORT, all 39 operations; see §3.0.** |
| **B3** | ✅ **DECIDED 2026-08-17: option (b), the upstream-oracle profile — BUILT; see §3.0.** Decides whether S3–S7 have any automatic signal at all. |
| **B4** | The `'equals'` keyword collision. Recommendation **drop `identicalExpression`, else hide it behind a semantic predicate — not fixture amendment**. |
| **B5** | `TypeTest#testSupertype`: the fork lattice breaks **10 of 12** assertions. Recommendation **adopt and handle**. |
| **B6** | `UndefinedValue` prints `null` upstream / `Undefined` on the fork; **79 corpus entries** expect the latter. Recommendation **normalise in the harness**. |
| **B7** | Bug-for-bug vs fix — ✅ **DECIDED 2026-08-17: FIX and document each row; see §3.0.** |
| **B8** | `Op_number_sqrt` / `Op_number_pow` shadowing. Recommendation **tighten `matches`**. |
| **B9** | `ExpQuery` items 7+8, uncertain-predicate `exists`/`forAll`. **Take both or neither.** |
| **B10** | `ExpDefSBoolean` + `ASTSBooleanDefExpression` dead code. Recommendation **drop**. |
| **B11** | `UnlimitedNatural` lattice inconsistency. Recommendation **reproduce bit-for-bit, plus a pinning regression test**. |
| **B12** | Corpus-harness placement and the process-global `Options.explicitVariableDeclarations` write that makes JUnit-3 suite ordering load-bearing. |

**H13–H22, added by the eight review rounds, not in `specification.md`:**

| # | Decision |
|---|---|
| **H13** | **How S4 gates, given D-29.** A perfect port passes 74 of 285. Choose: (a) diff `stageGateFailures` against a recorded perfect-port baseline — cheapest, recommended; (b) fund ~**273** hand-authored sign-offs; (c) change clause 2. **Do not** fall back on `isClean()` — a 119-operation gap. |
| **H14** | ✅ **DECIDED 2026-08-17: BUILD it; see §3.0.** |
| **H15** | **Whether the corpora are widened before S4** (D-28: one `RealValue`; D-31: `indexBoundaries()`; D-42: `boolean=4`), or the single-valued census is signed off operation by operation. |
| **H16** | **How fidelity is established for the 33 non-nameable operations** — `equals(Object)` first: a second instrument, hand-written unit tests, or a declaration that they are out of scope. |
| ~~**H17**~~ | **ANSWERED, round 6:** a type-aware canonical form was built; "right content, wrong Java type" is not fidelity. Survives on a corpus measurement and inherits D-30. |
| **H18** | **Post-state** — the 8 `void` mutators cannot be shown faithful by this harness at all. Accept, or build a post-state probe. |
| ~~**H19**~~ | **ANSWERED, round 6:** D-34, D-35, D-36 fixed and confirmed. |
| ~~**H20**~~ | **ANSWERED, rounds 7–8.** The token-stating API is deleted; a type-only difference is a counted, non-gating `AGREE`. Round 8's refuter found the obligation still author-influenceable through the **choice of object** (D-52), fixed by mandating the adapter's **shape** (`harness-contract.md` §7–§8). **What remains yours:** reject a type-fidelity figure whose adapter does not observe the invocation's own return value. |
| ~~**H21**~~ | ✅ **ANSWERED / BUILT 2026-08-17** — see above. |
| **H22** | **Whether D-55 and D-56 are closed now or carried as corpus-dependent latents.** Both unreachable in today's corpus by measurement, not by construction — both inherit D-30. |

---

## 4. How this was reviewed

Eight rounds, alternating build and refutation, each refutation run by a reviewer who owned Maven and
reproduced the *before* state rather than taking the previous round's word for it. Full round-by-round
detail: `stage-01.md` §10 (rounds 1–4 in §7–§9, round 5 in §10.2–§10.3, round 7 in §10.8, round 8's build
in §10.9, round 8's refutation in §10.10) and `harness-contract.md` (the rules those rounds produced).

**Rounds 1–5 fixed scoring defects** — four distinct ways the instrument called something agreement that
was not (harness-own-error scored as agreement, throw-agreement on message-discarding, `VOID` vs `VOID`
scored `AGREE`, single-valued codomains scored as fidelity), each closed and pinned by a regression test.
Round 5 then made detection power a direct measurement (11 probes) and found the remaining risks are not
scoring bugs: the gate is not satisfiable by fidelity (D-29), detection is bounded by an unmeasured input
domain (D-30). **Rounds 6–8 were all about one addition**, the Java-runtime-type check: round 6 closed a
real blind spot (right content, wrong Java class scored `AGREE`) and created a false-*divergence* mode (a
faithful port lost 29 stage passes to its own adapter); round 7 closed that and left an escape hatch (a
mandated disclosure that fired in 0 rows of a laundered sweep); round 8 removed the ability to state a
token at all and demoted what cannot yet be attributed to a counted, non-gating `AGREE`, then its refuter
found **D-52**: the escape hatch moved from a `String` parameter to an `Object` parameter, so the fix
must mandate the adapter's **shape**, not its call.

**The transferable lesson:** a fix is not assessed until someone measures what a *faithful* port does
under it, and if the thing under test can influence what a rule measures, no mandated disclosure repairs
the rule — a disclosure fires only where the instrument already noticed, and laundering is exactly the
case where it did not.

---

## 5. Residual risk

* **Largest: silent agreement inside an unmeasured domain (D-30).** A port wrong only where the corpora
  do not look is a full stage pass with `[DISCRIMINATING]` beside it — true and irrelevant. Only a
  widened corpus (H15), a coverage metric (H14), or prose stating the domain will surface it.
* **Second: a stage automating on a boolean (D-29).** On 92 of 285 operations a perfect port already
  fails clause 2, so the pass bit cannot move — and the most classic port bug of all, off-by-one on a
  1-based string index, sits in exactly that region: its verdict tally changes completely while
  `requireStagePass` says `false` before and after. Diff the **clause list** against a recorded
  perfect-port baseline (H13).
* **The type figure is not yet a gate (D-52).** Inert at S1, must be settled before S4 quotes it. Its
  provenance now has an aggregate (D-48/D-49, H21, built), closing the reporting half of D-48; the gating
  half (D-52, the adapter's shape) is unchanged and still yours.
* **Attribution can be destroyed by the subject (D-17), though the verdict cannot (D-32).** A partially
  implemented port is a legitimate S4 state, so this cannot be turned into a `DIFFER`; the defence is
  that it buys no stage pass. The recommended reporting fix (split `UNSUPPORTED` into a subject-declined
  bucket) is unbuilt.
* **The soft target is now the prose.** Three of round 5's four MAJORs were reports reading stronger than
  the runs behind them; round 7's and round 8's refutations each killed an overclaim made in identical
  words across documents (D-53: `agreementCount()`, `agreements()`, `isClean()` are public and
  unaccompanied, despite a Javadoc claiming otherwise). **Hold S4–S7 documents to the harness's own
  standard: every claim names `file:line` or the command, and pastes real output.**
* **Decision risk, not evidence risk.** No blocking decision was refuted and four were independently
  re-derived. Eight of the twelve B-items plus H13, H15, H16, H18, H22 remain unmade; B2/B3/B7/H14 were
  decided 2026-08-17 (§3.0), three reversing the recorded recommendation; H21 is built (`1ec7d59f`); B3
  is built with its gate asserting a per-module floor (`6702f06e`, `upstream-oracle-profile.md` §5.1).

**Recommendation: S3 may start once B1–B12 and H13–H16 / H18 are answered.** (H21 is now built and no
longer deferrable-or-not; B2, B3, B7 and H14 were decided on 2026-08-17 — see §3.0. Only H22 remains
deferrable past S3 and not past S4.) The foundation carries the weight. It does not carry an unqualified
fidelity claim, and the honest form of that claim is written into `harness-contract.md` §1, §4 and §8 so
that a stage cannot make the stronger one by accident.

**Only defects the refuter confirmed are marked closed here.** The canonical state of every defect — open,
closed, reclassified, and by which commit — is `stage-01.md` §10.4, with the id re-keying map in §10.5.
