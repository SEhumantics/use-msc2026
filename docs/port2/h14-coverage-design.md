# H14 — the input-domain coverage measure: design

**Status: DESIGN ONLY, 2026-08-17. Nothing here is implemented, and H14 remains unimplemented at project
end.** The user decided H14 on 2026-08-17: **BUILD an input-domain coverage measure**
(`foundation-verdict.md` §3.0, row H14). This document is the Spec role's answer: what to build, in what
order, what it will and will not tell you. Reading only — no Maven run this round. Every figure below is
either pasted from an existing record with citation, derived arithmetically from source literals
(labelled **hand derivation**), or labelled **ESTIMATE**.

**The defect this exists to close.** D-30, MAJOR, open (`stage-01.md` §10.4). Round 5 planted a wrong
uncertainty-combination rule restricted to receiver value exactly `42.0`: **0 `DIFFER` rows**, a verdict
tally byte-identical to a perfect port's across 19 083 rows, and a full stage pass on the operation
carrying the defect, printed `[DISCRIMINATING]` — true and irrelevant to the untried input. **No
input-domain coverage figure is computed, published or gated anywhere in the instrument.**

---

## 1. The core insight — a coverage ratio can't be rescued

**"Coverage" of this domain is not a ratio, and no amount of engineering makes it one.**

One operation's input is a tuple of `UValue`s; the state space per receiver kind runs from 2^65
(`UBOOLEAN`) to 2^128 (`UREAL`) to unbounded (`USTRING`). `URealValue.add(Value)` alone has a 2^256 input
space against the **784** tuples the S1 smoke sweep drives — a ratio true to 74 decimal places of zero
and useless to a reader.

Three reasons a ratio cannot be rescued:

1. **No canonical measure.** A ratio needs a measure on the domain, and every plausible choice (uniform
   over reals, over IEEE-754 bit patterns, over decimal literals a modeller might type) gives a different
   answer — choosing one is choosing the answer.
2. **The unbounded slot.** `USTRING` has no finite state space, so any total ratio is `finite / ∞ = 0` by
   construction.
3. **It answers the wrong question even if it existed.** D-30 asks *"would we have seen the defect?"*,
   not *"how much of the domain did we try?"* Round 5 measured that removing five of the 24 `uReal`
   corpus values (NaN and both infinities, in both positions — a 21% cut) cost no probe a single detected
   operation. Detection is dense inside a reached region and zero outside it; a volume measure cannot see
   that shape.

**The surrogate, in one sentence.** Replace the unanswerable ratio with **two finite, machine-enumerated
denominators**, and never blend them into one score:

* **A. A reach census.** Partition each slot into a fixed, globally shared, per-stage-immutable set of
  named cells; count which were witnessed on a measured row. A bound on the *meaning* of a fidelity
  figure — not a claim about defects.
* **B. A mutation-adequacy score.** Generate a population of defective ports; count how many the corpora
  would catch. An estimate of the *power* behind a figure — **the only one of the two that answers D-30's
  actual question.**

---

## 2. Candidate measures

Three candidates, each scored against the same decisive test: **how would it have scored the `42.0`
defect?**

**C1 — equivalence-class / boundary partitioning per operation.** Hand-declare a partition of each
operation's parameter space from `specification.md`'s stated semantic boundaries (e.g. `UString.at`:
index `<1` vs `>|s|`; `equalsC`: `k=0` vs `k=1`). Catches relational/semantic boundaries no numeric
binning sees, and would have caught D-19 and D-31 mechanically. **Disqualifying property: the denominator
is author-chosen, per operation, and coarsenable invisibly** — a wider interval leaves no diff to review,
the same shape as D-15/D-43 in a new dimension. **Scores `42.0`: FULL COVERAGE, NO SIGNAL** — `42.0`
falls in the same class as `2.0`/`100.0` under any partition anyone would actually write. Cost: highest,
recurring — 285 operations plus the 39 `SBoolean` operations B2 just made a port target.

**C2 — mutation adequacy.** A *mutant* is the perfect port with exactly one named infidelity; adequacy =
killed mutants / (generated − signed-off-equivalent). **The harness already has the machinery** — this is
a generalisation of a test that ships, not a proposal from nothing: the eleven hand-written probes in
`PortedInfidelityDetectionPowerTest` (`P0`…`P11`) **are** mutants, built on a perfect-port control, a
`MutantPort`/`Mutation`/`Probe` framework with per-operation targeting, and an exact-set assertion over
what the instrument cannot see ("so that blindness cannot grow or shrink silently"). What's missing is
*generation*, not measurement — today's probes were hand-picked by a reviewer, so the current score
measures that reviewer's imagination. The build replaces hand-picking with a schema × surface
enumeration: value perturbation, uncertainty perturbation, argument perturbation, relational, equality,
exceptional, representation, degenerate, concealment, and — the family that speaks to D-30 directly —
**windowed**: any content operator restricted to a declared input window, of which `P10`'s
`NARROW_WINDOW = 42.0` is one instance of one window. **Cannot catch:** a defect shape no operator
generates (a weaker hazard than C1's — dropping an operator lowers a printed integer, `# mutation.
generated`, rather than coarsening invisibly, and cannot remove an already-recorded survivor from the
exact set); **code-space defects** — `MutantPort` wraps a *perfect port* and transforms *results*, so it
cannot express wrong registration order ("the highest-blast-radius constraint in the whole port"), an
operation registered under the wrong name, or an arity guard checked after a dereference — real
historical shapes, and **the sharpest limit of C2** (§5.3); equivalent mutants (undecidable in general,
priced by a sign-off, same pressure as bulk `AcceptedThrowPairs`); and the 33 of 318 operations the
harness cannot name at all — a mutant there is unplantable, not merely undetected. **Scores `42.0`:
SURVIVOR, NAMED, WITH ITS WINDOW** — `P10` already measures `DETECTED on 0 operation(s)`.
**C2 is the only candidate that scores the headline defect as a miss.** Two adequacy readings are
derivable *today* from published round-5 measurements: by "a row diverged", 8 of 11 probes killed
(survivors P8, P9, P10); by "the per-operation verdict tally changed", 10 of 11 (survivor P10 only) — the
choice between them is made in §4. Cost: highest runtime of the three (≈19 083 rows per full-inventory
mutant), mitigated because `Probe.targets` lets most mutants sweep only their target operations rather
than all 285; machinery ~90% built already.

**C3 — fixed interval/grid lattice of the value × uncertainty plane, per receiver kind.** One shared
lattice, defined once and never per operation. A concrete proposal for the real-valued kinds: 11 value
cells × 9 uncertainty cells = **99**, with `-0.0` and `0.0` deliberately separate cells (the dimension of
the one defect round 5's harness half-missed). Coverage = cells witnessed on a measured row / cells
declared, reported per receiver kind, per operation, and per slot-tuple for multi-slot operations. **A
hand-derived illustration, not a harness measurement:** the 22 `uReal` boundary literals in
`InputGenerator.java:170-194` occupy **21 of 99 cells** (one collision), with two uncertainty cells empty
(nothing strictly between 0.5 and 1; nothing finite above 1). For `URealValue.add`'s **9 801**-cell joint
receiver × argument space, the smoke sweep's 784 driven tuples occupy **at most ≈625, roughly 6%**,
against a golden header reading `# rows.agreement 784` / `# rows.disagreement 0`. **That contrast is the
whole argument for building C3: the honest number is small, and a small number cannot be misquoted as
proof of fidelity.** Catches gross regional holes mechanically, including D-19 and D-31, and gives every
C2 survivor an *address* — survivor + unoccupied cell = a corpus gap someone can close. **Cannot catch**
in-cell blindness, anything relational (`a == b`, both strings empty, index `== length`), or anything
codomain-side. **Scores `42.0`: FULL COVERAGE, NO SIGNAL** (`42.0` occupies the cell already occupied by
`1.0`/`2.0`/`100.0`) — and scores `P11`/`round()` green too, since its input `-0.0` **is** in the corpus;
that blindness is codomain-side and no input census can see it. Cost: lowest of the three — one lattice
class, no per-operation input, plus one small structural change (§3, H14-1).

### 2.4 The comparison

| | C1 per-op partition | C2 mutation adequacy | C3 fixed lattice |
|---|---|---|---|
| Denominator from | the author, per operation | the machine, from a shared operator schema | the machine, from one shared lattice |
| Coarsenable invisibly? | **yes** — a wider interval leaves no diff | no — `# mutation.generated` falls, survivor set can't shrink | no — one lattice, asserted as an exact literal |
| Catches relational / semantic boundaries | **yes** (unique strength) | only where an operator encodes one | no |
| Catches a defect inside a covered region | no | **yes**, where a windowed operator names that window | no |
| Answers "would we see it?" | no (proxy) | **yes (direct)** | no (proxy) |
| Cost | highest, recurring, 285 + 39 ops | highest runtime; machinery ~90% built | lowest |
| Score on the `42.0` defect | green — no signal | **survivor, named, with its window** | green — no signal |
| Score on `P11`/`round()` | green | survivor (2nd member of the recorded blind set) | green |

### 2.5 Recommendation: C2 as the gated instrument, C3 + "C1-lite" as the reported reach census

**Reject C1 as specified** — highest cost, and its denominator is exactly the author-influenceable
quantity eight review rounds were spent removing from this harness (`harness-contract.md` §9's test:
*"can the thing under test influence what this rule measures?"* — here the influencer is the porter, not
the port). **Keep C1's catching power as "C1-lite"**: a fixed, globally shared list of ~20–30 named
relational predicates, computed from the typed tuple, applicable-by-arity-and-kind (machine-derived),
never per operation — drawn from `specification.md` §2.1–§2.5, which already names most of them
(`a == b`, `a == -b`, both operands zero, both strings empty, index `== 1`/`== |s|`/`< 1`, threshold
`k == 0`/`k == 1`, confidence at the 0.5 fold, value integral vs non-integral). Buys most of C1's unique
catch at none of its gameability, because applicability is derived and the predicate list is shared.

Why this pairing: **C2 is the only candidate that scores the defect this project actually measured as a
miss** — determinative, since both proxies report green on it. **C3 + C1-lite is nearly free and makes
C2's output actionable** — a survivor alone says "a defect of this shape would be missed"; a survivor
beside an unoccupied cell says where to widen the corpus. **C3's number is honestly small**, which
inverts the usual reporting hazard in this project (a number reading stronger than the run behind it —
D-15, D-43, D-53). **They fail differently and are both immune to D-43's failure mode**: C3 is a property
of the corpus alone, so the port cannot move it; C2's subject is `MutantPort(perfectPort)`, never the
real port, so a bad real port cannot raise the adequacy score. **Do not blend them into a single
"coverage score"** — one number mixing a measurement with a judgement gets quoted as the measurement.

---

## 3. Build plan

Five stages, all test-scoped under `use-core/src/test/java/org/tzi/use/uncertainty/differential/`. No
`*/src/main/*` file touched, no `pom.xml` change. **All line counts are ESTIMATEs.**

1. **H14-1 — the reach census, no gate** (~1 270 lines: `DomainCell`, `DomainLattice`,
   `RelationalPredicates` for C1-lite, `DomainReach`; small edits to `DiffRow`/`DifferentialSweep`). **The
   one unavoidable structural change:** the census must read **typed** `UValue`s, not `DiffRow`'s rendered
   `inputs` text, so `DiffRow` gains `inputValues()` — carried structurally, **not** a TSV column, exactly
   H21's pattern (a count derived from prose "silently becomes zero when the prose is reworded"). Small,
   verified edit surface: `new DiffRow(` appears 8 times in the tree, all inside `DifferentialSweep.run`,
   which already holds the typed tuple there. **The one discipline built in from the first line:** a cell
   counts as reached **only on a measured row** (`AGREE`/`DIFFER`) — never on
   `HARNESS_ERROR`/`UNSUPPORTED`/`UNMEASURABLE`, D-11/D-12's "rows are not measurements" defect in a new
   dimension. Also: declared cell count per kind is an exact literal; `-0.0`/`0.0` land in different
   cells; the census is seed-stable. **Buys:** the honest number, quotable immediately, gating nothing.
2. **H14-2 — the census reaches the artefact** (~255 lines across `DiffReportWriter`/`DifferentialSweep`,
   2 golden refreshes, header lines only — same cost shape as H21's "+4 header lines each, no data row
   moved"). New regression tests assert the figure reaches the header and that **no ratio or percentage is
   ever emitted**. **Buys:** the number reaches `stageStatement()`, unavoidably.
3. **H14-3 — the gate clause** (~700 lines, ~26 gate call sites: `DomainReachFloor`,
   `AcceptedUnreachedCells`, a fourth `stageGateFailures` clause). Every design choice is a precedent
   already proven in this harness: a mandatory floor parameter with no eliding overload, pinned
   reflectively (D-34's fix verbatim); the floor written into the header whether or not it fired, including
   `none` (D-34's argument again); `AcceptedUnreachedCells` keyed on `(operation, cellId)` with a mandatory
   rationale, `AcceptedDegenerateOperations`' self-lapsing shape. **Must anticipate D-29 rather than
   rediscover it:** some declared cells (e.g. `UBOOLEAN` value cells, exhausted by two inhabitants) are
   unreachable by any faithful port, so the floor is declared **per receiver kind**, derived from lattice
   arithmetic **before the run** — a floor chosen after seeing the run is not a floor.
4. **H14-4 — systematic mutant generation and the adequacy score** (~1 400 lines: promote today's
   `MutantPort`/`Mutation`/`Probe`/`measure` machinery out of `PortedInfidelityDetectionPowerTest` as a
   move, not a rewrite, leaving the eleven hand-authored probes in place; add `MutationOperator`,
   `MutantCatalogue`, `MutationAdequacy`; one new golden). Assertions that must ship: the control diverges
   nowhere (precondition — nothing is attributable otherwise); `# mutation.generated` is an exact literal
   so dropping an operator fails a named test; the survivor set is an exact recorded set (the
   `subtleInfidelitiesAreDetectedOrNamed` pattern); every "semantically equivalent" mutant carries a
   written rationale in a keyed sign-off list whose size is in the header; both kill criteria reported
   separately, with the gate reading the divergence one (not "the gate refused" — a perfect port is refused
   on 92 of 285 by clause 2 alone, D-29, so that criterion would score fidelity as detection on a third of
   the surface). **Buys:** the only number that answers D-30's question, and a survivor list that is a
   corpus work queue.
5. **H14-5 — close the loop, the only stage that reduces risk.** For each survivor: widen the corpus until
   it dies, recording the added inputs and re-measured adequacy, or sign it off with a written window
   saying what a reader must not conclude. **H14-1…4 measure the risk; only H14-5 lowers it** — the
   temptation after building an instrument is to treat the instrument as the fix.

**Ordering:** `1 → 2 → 3` is strict — a census with no artefact is unquotable, a gate on an unpublished
number is unreviewable. `4` is independent and could run first, but pairing a survivor with an unoccupied
cell only works after `1–3`. `5` is last by definition. **If the round is cut short, stop after H14-2**:
an honest published number with no gate beats prose, at the cost of two golden header refreshes. **Do not
stop after H14-3 without H14-4**: a gate on the reach census alone is a gate on the proxy that scored the
`42.0` defect green.

**Total ESTIMATE:** ~11 new files, ~9 edited, ~3 600 test-scoped lines, 2 golden refreshes + 1 new golden.
Runtime cost **MUST BE MEASURED** by whoever owns Maven; the planning arithmetic is 19 083 rows per
full-inventory mutant sweep, with `Probe.targets` as the lever.

---

## 4. How it reports — the design rules

* **Two integers, never a ratio.** `harness-contract.md` §4.5 already forbids a bare agreement
  percentage; the rule applies here more strongly, because a percentage travels and a cell list does not
  — `reached 21 of 99; unreached: UREAL/v9/u6, UREAL/v9/u8` survives being quoted out of context,
  `coverage 0.21` does not. **No `# domain.coverage` key exists in this design, deliberately.**
* **Per operation, and read per operation**, inheriting D-41's shape (`# op.<key>.*` keys are not unique
  if one report holds several results for one operation) — fix D-41 first (§6) or document the
  inheritance explicitly.
* **The unreached-cell list is enumerated, bounded and printed** — the gap is the finding, the count is
  the headline; cap the list and print the count of the remainder.
* **The floor is in the header whether or not it fired**, including `none`, so a floored run is never
  byte-identical to an unfloored one.
* **`stageStatement()` carries the reach figure unconditionally, including when complete** — the same
  reason the type-mismatch figure prints even at zero ("'0' is the claim a stage needs to be able to
  make").
* **The mutation-adequacy report is a separate artefact**, own file and golden — it is not a row-level
  property of a sweep. `killedByDivergence` and `killedOnlyByTallyChange` are reported **separately**:
  a divergence kill is detection **with attribution**, a tally-change kill is a detection the reader would
  naturally blame on the instrument instead (D-32's distinction). Summing them erases it.
* **Gate-or-report, decided item by item:** the reach census cell counts are a **gate clause** on a
  floor written before the run (the port cannot move the denominator at all, only the author-chosen floor
  is under review — the existing clause-1 precedent); the unreached-cell list is **reported**, not gated;
  **no coverage ratio is ever emitted, gated or not**; the mutation survivor **set** is a **gate clause**,
  as an exact set (adding an easy mutant can't remove a survivor; removing an operator lowers a printed
  integer); the adequacy **ratio** `killed/generated` is **reported, never gated, never quoted alone**
  (gameable upward by adding easy operators — gate the set, report the ratio); **"the gate refused" is
  forbidden by name as a kill criterion** (D-29); the equivalent-mutant bucket is **reported**, with a
  per-mutant written rationale, sized in the header, priced like `AcceptedThrowPairs`.
* **The sentence that must travel with every reach figure**, next to the number if possible: *"Coverage
  is not fidelity evidence. It is a bound on the meaning of fidelity evidence. A perfect port and a
  defective port produce the same reach census, because the census is a property of the corpus and not of
  the implementation."* Bind it on the **method**, not the class — a class-level version of this promise
  was already withdrawn as false (D-53: `agreementCount()`, `agreements()`, `isClean()` are public and
  unaccompanied despite a Javadoc claiming otherwise).

---

## 5. What it still will not tell you — the residual D-30

An over-promised coverage measure is worse than none. Nine residuals, most severe first:

1. **Measuring coverage adds no detection. None.** After H14-1…4 the `42.0` defect is *named*, not
   detected — a port carrying it still passes. Only **H14-5**, widening the corpus, changes detection. If
   H14 lands and its survivors are neither closed nor signed off, D-30 has been **relabelled, not
   reduced**, and any stage document must say so in those words.
2. **In-cell blindness is total and permanent.** Both proxies score both known D-30 instances green:
   `42.0`'s cell and `P11`/`round()`'s `-0.0` cell. Refining the lattice does not fix this, only moves the
   boundary — the domain is uncountable and any finite partition has interiors. **A stage may never write
   "the input domain is covered."** The strongest true sentence: "*N* of *M* declared cells were reached
   on a measured row; within a reached cell, nothing is claimed."
3. **The mutant population is result-space; a large part of the real defect population is code-space.**
   `MutantPort` wraps a perfect port and transforms its results — it cannot express wrong registration
   order ("the highest-blast-radius constraint in the whole port"), an operation registered under the
   wrong name, or a guard evaluated after a dereference. Not hypothetical: **B7** mandates fixing 33
   behaviour-changing historical defects and **B2** mandates a full hand port of 39 `SBoolean` operations
   with zero fork-test coverage behind them, and none of those defect shapes appear in any generated
   mutant population. **The adequacy score is an upper bound on power against a defect distribution that
   is not the one S4–S7 will actually produce.**
4. **`SBoolean` is at coverage zero and cannot be raised by this design.** Not marshallable, no
   `UValue.Kind` for it, every row `UNSUPPORTED` — so B2's 39 operations get no cells, no mutants, no
   adequacy figure. Any H14 number must be published with its surface denominator beside it ("*N* of *M*
   cells over 285 of 318 nameable operations, 0 of 39 `SBoolean`") or become the next D-15.
5. **The unmeasurable surface is unchanged.** 33 non-nameable operations (no row, no verdict, not even
   `UNSUPPORTED`), 8 `void` mutators whose post-state is never re-read, collection receivers, the type
   layer. A census over an operation with no rows is undefined, not zero, and must print `n/a`.
6. **The equivalent-mutant problem is undecidable**, and the sign-off is a judgement inside a measurement.
   The rationale requirement and header count price it; they do not solve it. Expect this to be the number
   a reviewer attacks first, and expect it to be right to.
7. **A survivor's attribution to a specific corpus gap is a heuristic, not a derivation.** Round 5
   measured detection is robust to thinning within a reached region (removing five `uReal` corpus values
   cost no probe an operation), so a mutant's *death* does not identify which input killed it, and pairing
   a *survivor* with an unoccupied cell is a plausible explanation rather than a proof.
8. **Interaction depth above 2 is unmeasured.** The design proposes 1-way and 2-way (slot-tuple) cells;
   `uSubstring(int,int)` has three slots and is already 17 measured rows of 432 (D-31) — its 3-way census
   reads near zero, the honest restatement of D-31, but 3-way-and-higher interactions get a number too
   small to carry discriminating information.
9. **Every figure is seed- and corpus-conditional**, inheriting the D-28/D-31 species — every H14 number
   is a joint fact about the lattice, the corpus and the seed, and must be quoted with `# seed`. Related:
   fix D-42 (`boolean=4` reported over a two-inhabitant type, because `booleanCorpus` appends random draws
   to an already-exhaustive domain) in H14-1, or the cell census and the row count will contradict each
   other in the same report.

---

## 6. What is left for the human

Three genuine decisions inside H14, each with a recommendation, none settled:

1. **Does `requireStagePass` gain a third mandatory parameter (H14-3), or does the reach gate live in a
   separate call?** Recommended: **the third parameter, with no eliding overload** — the precedent chain
   (D-34, D-36) says a separate call is forgettable and "the gate is still opt-in" is already a named
   weakness. Cost: ~26 gate call sites edited. Fallback if a smaller blast radius is preferred: a separate
   call plus a header key recording whether it was made, so a stage that skipped it is visible in the
   golden.
2. **Is D-41 (non-unique `# op.<key>.*` keys) fixed before H14-2, or inherited?** Recommended: **fix it
   first** — one header-block change, latent today, and H14 doubles the number of non-unique keys.
3. **Is the mutation-adequacy run part of the default build, or gated behind a profile like B3's
   `-Pupstream-oracle`?** Recommended: **measure the runtime first, then decide** — the arithmetic
   (19 083 rows per full-inventory mutant, targeted sweeps otherwise) is in §3; the measurement is not
   this document's to take.

---

**Two citation-hygiene findings, noticed while reading, not part of H14 itself.** `harness-contract.md`'s
line-number citations into `DifferentialSweep.java` are stale (H21 moved several methods by a few lines
each; prefer citing method names over line numbers, or re-derive the set in one pass). And
`DifferentialSweep.java:777-784`'s Javadoc still carries the D-53 over-claim ("there is deliberately no
way to render an agreement figure from this class without the discrimination figure beside it"), which
`harness-contract.md` §4.5 has already withdrawn as false. H14 adds accessors to the same class and must
not repeat either mistake.
