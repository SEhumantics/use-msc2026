# H14 — the input-domain coverage measure: design

**Status: DESIGN ONLY, 2026-08-17. Nothing here is implemented and nothing here is measured by a run
of this design.** The user decided H14 on 2026-08-17: **BUILD an input-domain coverage measure.** The
recorded recommendation was the cheaper one — *prose-stated domains in every stage document* — and it
was **NOT taken** (`foundation-verdict.md` §3.0, row **H14**; `specification.md:171-172` points at that
row). This document is the Spec role's answer: what to build, in what order, what it will and will not
tell you.

**What produced this document.** Reading only. I did not run Maven; I do not own it this round. Another
session shares the checkout. Every number below is either (a) pasted from an existing record with its
citation, (b) derived arithmetically from source literals I quote with file:line, and labelled as a hand
derivation, or (c) labelled **ESTIMATE**. There is no third kind. Where a figure would require a run,
it says **MUST BE MEASURED BY THE IMPLEMENTING STAGE**.

**The defect this exists to close.** **D-30**, MAJOR, open (`stage-01.md` §10.4; `harness-contract.md`
§5, last-but-five row). Measured in round 5: `P10-narrow-input-window`, a real arithmetic defect —
`P2`'s wrong uncertainty-combination rule — restricted to receiver value exactly `42.0`, produced

```
=== detection power: P10-narrow-input-window ============================
verdict tally        {AGREE=17199, BOTH_THREW=910, HARNESS_ERROR=883, UNMEASURABLE=91}
DETECTED on          0 operation(s): []
stage passes         74   (control 74)
isClean() operations 193   (control 193)   the older predicate loses 0: []
  target URealValue.add(value)
    control  {AGREE=576}
    mutant   {AGREE=576}
    statement URealValue.add(value): 576 rows, 576 measured, 576 agreed, 0 disagreed, 164 distinct reference value(s) [DISCRIMINATING]
    stage pass? true   (control true)
```

(`stage-01-verification-round5.md` §3.1, verbatim.) A verdict tally byte-identical to a perfect port's
across 19 083 rows, a full stage pass on the operation carrying the defect, and `[DISCRIMINATING]`
printed beside the agreement figure. Study A is defined as agreement against this oracle, so an
unmeasured coverage bound is the largest live risk in the port.

---

## 1. The definition problem, stated honestly

**"Coverage" of this domain is not a ratio, and no amount of engineering makes it one.**

The input to one operation is a tuple of `UValue`s. For the four U-types the payload is a pair:

| Receiver kind | value component | uncertainty component | state space |
|---|---|---|---|
| `UREAL` | `double` | `double` | 2^64 × 2^64 = **2^128** |
| `UINTEGER` | `int` | `double` | 2^32 × 2^64 = **2^96** |
| `UBOOLEAN` | `boolean` | `double` | 2 × 2^64 = **2^65** |
| `USTRING` | `String` | `double` | **unbounded** × 2^64 |

(Component types read off the factories, `UValue.java:272-290`: `uReal(double,double)`,
`uInteger(int,double)`, `uBoolean(boolean,double)`, `uString(String,double)`.)

`URealValue.add(Value)` is a two-slot operation, so its input space is 2^128 × 2^128 = **2^256**. The
S1 smoke sweep drives **784** tuples of it (`docs/port2/differential/s1-smoke-ureal-add.tsv`,
`# rows 784` — 28 receivers × 28 arguments, from `RANDOM_DRAWS = 6` in
`UncertaintyDifferentialSmokeTest.java:59` on top of the 22 literals of
`InputGenerator.java:170-194`). The literal coverage ratio is 784 / 2^256. That number is true, it is
zero to 74 decimal places, and it tells a reader nothing they did not already know.

Three separate reasons a ratio cannot be rescued:

1. **No canonical measure.** A ratio needs a measure on the domain. Uniform over the reals is not a
   probability measure; uniform over IEEE-754 bit patterns puts half its mass in `|x| < 1` and a
   quarter of it in the subnormals; uniform over decimal literals a modeller might type is a different
   measure again. Choosing one is choosing an answer.
2. **The unbounded slot.** `USTRING` has no finite state space at all, so any total ratio is
   `finite / ∞ = 0` by construction.
3. **It answers the wrong question even if it existed.** D-30's question is not *"how much of the
   domain did we try?"* but *"would we have seen the defect?"* Those come apart badly: round 5 measured
   that removing five of the 24 `uReal` corpus values (NaN and both infinities, in both positions) —
   a 21 % cut in the domain — cost **no probe a single detected operation** and at most 26 % of its
   detecting rows (`stage-01-verification-round5.md` §6.1, the corpus-sensitivity table). Detection is
   dense inside a reached region and zero outside it. A volume measure cannot see that shape.

**The surrogate I propose, in one sentence.** Replace the unanswerable ratio with **two finite,
machine-enumerated denominators**, and never blend them into one score:

* **A. A reach census.** Partition each slot into a **fixed, globally shared, hand-declared but
  per-stage-immutable** set of named cells, and count which cells were witnessed **on a measured row**.
  Denominator: an integer printed by the instrument. Enumerable, auditable, monotone in corpus width.
  Not a claim about defects.
* **B. A mutation-adequacy score.** Systematically generate a population of defective ports, and count
  how many the corpora would catch. Denominator: an integer, printed, and every member individually
  executable and reviewable. **This is the only one of the two that answers D-30's actual question.**

Both are surrogates and both must say so in the same breath as their number. A is a bound on the
*meaning* of a fidelity figure; B is an estimate of the *power* behind it. Neither is "coverage" in
any measure-theoretic sense, and the word should not appear in a stage document without one of the two
qualifiers attached.

---

## 2. Candidate measures

Three candidates, as the task specifies, each scored against the same four questions. Then a fourth
column that decides it: **how would it have scored the `42.0` defect?**

### 2.1 Candidate C1 — equivalence-class / boundary partitioning per operation

**Definition.** For each of the 285 nameable operations (`stage-01-verification-round5.md` §3.3:
318 public instance methods on the 8 marshallable receivers, 285 expressible as a `UOp`, 33 not
nameable at all), declare a partition of its declared parameter space, derived from
`specification.md` §2.1–§2.5. That section is unusually good source material for this — it already
states the semantic boundaries operation by operation:

* `setConfidence` — "Range `[0,1]` else Undefined; **NaN → Undefined**" (`specification.md:464`);
* `equalsC` — "`k=1` demands exact equality; `k=0` always true; NaN → Undefined"
  (`specification.md:466`);
* `UString.at` — "**1-based**, confidence carried. **`idx<1` throws `IllegalArgumentException`;
  `idx>|s|` throws `StringIndexOutOfBoundsException`**" (`specification.md:632`);
* `UString.+` — "**Both operands empty ⇒ `c = NaN`** (0/0)" (`specification.md:634`);
* `UString.size` — "`UInteger(|s|, |s|·(1−c))` — the one place the uncertainty changes representation"
  (`specification.md:638`);
* `UString.toUBoolean` — "at threshold 0.5, falling back to `(true, 0.5)`" (`specification.md:643`).

Coverage per operation = witnessed classes / declared classes, at 1-way (each slot independently) and
optionally 2-way.

**What it would catch.** The class of hole that is *semantic* and invisible to any numeric binning:
"no tuple in which both string operands are empty", "no `equalsC` at `k = 1`", "no index exactly equal
to the receiver's length". These are real, they are where the historical implementation's cliffs are,
and a value×uncertainty lattice (C3) cannot see any of them because they are **relations between
slots**, not regions of one slot.

It would also have caught, mechanically, the two corpus holes the project found by hand:
**D-19** (`BooleanValue` / `StringValue` marshallable but never marshalled — 52 operations at 100 %
`HARNESS_ERROR`, `InputGenerator.java:253-267`) and **D-31** (`indexBoundaries()` drawn for `at(int)`,
leaving `uSubstring(int,int)` at **17 measured rows of 432**, `stage-01.md` §10.4 D-31 row).

**What it would not catch.** A defect **inside** a declared class. That is not a marginal weakness; it
is the `42.0` case exactly.

**Cost.** The highest of the three, and recurring. 285 operations plus the 39 `SBoolean` operations that
**B2** has just made a port target (`foundation-verdict.md` §3.0, row B2) — each needing a hand-authored
partition, each needing re-review whenever the spec table is corrected.

**The disqualifying property: the denominator is author-chosen, per operation, and coarsenable
invisibly.** This is D-15's and D-43's shape in a new dimension. §9 of `harness-contract.md` states the
test every extension must pass: *"can the thing under test influence what this rule measures?"* Here the
influencer is not the port but the porter, and a wider declared interval reads identically to a narrower
one in every artefact — there is no diff to review, because the coarsening **is** the declaration. An
author who wants 100 % writes three classes per slot and gets it. `AcceptedDegenerateOperations`'
answer to the same pressure was to make the key **type-bearing and value-keyed** so a sign-off lapses by
itself (`AcceptedDegenerateOperations.java:31-42`); there is no analogous self-lapsing key for "the
interval I chose was too wide".

**How it would have scored the `42.0` defect: FULL COVERAGE, NO SIGNAL.** Under any partition anyone
would actually write, `42.0` falls in the same class as `2.0` and `100.0`, both of which are in
`uRealBoundaries()` (`InputGenerator.java:182-184`). The cell is occupied, the operation reports 100 %
of its declared classes witnessed, and the defect is invisible. **C1 scores the headline defect green.**

### 2.2 Candidate C2 — mutation adequacy

**Definition.** A *mutant* is the perfect port with exactly one named infidelity. Adequacy =
killed mutants / (generated mutants − mutants signed off as semantically equivalent).

**The harness already has the machinery, and this is the decisive practical fact.** It is not a
proposal from nothing; it is a generalisation of a test that ships:

| Piece | Where | What it is |
|---|---|---|
| The perfect-port control | `PortedInfidelityDetectionPowerTest.java:62-68` — two independently loaded `HistoricalOracle`s, each with its own `IsolatedJarClassLoader` | The baseline. Measured `diverging operations 0` over 19 083 rows (`stage-01-verification-round5.md` §1.1), **without which nothing is attributable to a planted defect** |
| `MutantPort` | `PortedInfidelityDetectionPowerTest.java:109-152` | A `Candidate` that delegates to the perfect port and applies one `Mutation`, with an optional `unsupported` set |
| `Mutation` | `:102-107` | `UValue apply(UOp, List<UValue> args, Candidate perfect)` — a functional interface. **Eleven mutants exist today** |
| `Probe` | `:156-173` | id + description + **target operation set** + mutation. The `targets` field is what keeps a mutant's sweep cheap |
| `probes()` | `:207-364` | `P0-perfect` … `P11-negative-zero-collapse` — **the eleven hand-written probes ARE mutants** |
| `measure(Probe)` | `:516-570` | Drives one mutant over the whole inventory, recording per-operation tallies, stage passes, refusal clause lists, type-mismatch counts, throw-pair keys |
| `ProbeResult` | `:436-511` | The per-mutant record, already carrying `perOperation` tallies so a mutant can be **diffed against the control** |
| The exact-set assertion | `:657-698` | The set of planted (defect, operation) pairs the instrument **cannot** see, asserted **as an exact set**, "so that blindness cannot grow or shrink silently" (`harness-contract.md` §6) |

**What has to be added is generation, not measurement.** Today's eleven probes were chosen by a
reviewer to be interesting, which makes the current score a measurement of that reviewer's imagination.
The build replaces hand-picking with a **schema × surface enumeration**: mutation operators crossed with
the operation families they apply to, so the denominator is produced by the machine and printed.
Operators that are expressible today, because a `Mutation` is a transform of the perfect port's result
and/or of its arguments:

* **value perturbation** — result value +1 ULP, +1.0, ×(1+1e-12), sign flip, truncate toward zero,
  round to N dp (`P5-round-10dp` is one instance, `:257`);
* **uncertainty perturbation** — set 0, copy the receiver's, sum (`P2`, `:224`), `Math.hypot` (`P3`,
  `:235`), product, `1−u`, clamp to `[0,1]`;
* **argument perturbation** — index ±1 (`P1`, `:212`), swap argument order, drop the argument's
  uncertainty;
* **relational** — `<`→`<=` (`P4`, `:247`), negate a boolean result, move a `0.5` threshold by ±ε;
* **equality** — ignore the uncertainty component (`P6`, `:270`);
* **exceptional** — `Undefined` for a zero divisor (`P7`, `:283`);
* **representation** — box a raw result into its `Value` class (`P12`, `:850`), and its inverse;
* **degenerate** — constant, echo-receiver, echo-argument (these already exist as the seven non-port
  subjects of `UnwrittenPortInvariantTest.anUnwrittenPortAgreesWithNothing`, `harness-contract.md` §6);
* **concealment** — `HarnessMarshallingException` (`P8`, `:297`), `supports() == false` (`P9`, `:317`);
* **windowed** — and this is the operator family that speaks to D-30 directly: **any content operator
  above, restricted to a declared input window.** `P10` (`:330`) is one instance of one window,
  `NARROW_WINDOW = 42.0` (`:366`).

**What it would catch.** The question that matters, and nothing else: *would a defect of this shape,
here, be seen?* Critically it catches a defect **inside a cell C1 and C3 both call covered**, because
the windowed operators enumerate windows rather than regions — a surviving windowed mutant is a
**named, addressed** statement that the corpora do not reach that window.

**What it would not catch.**

1. **A defect shape no operator generates.** The operator list is hand-authored, so the same
   author-influence hazard as C1 applies — but weaker, and the difference is structural, not a matter of
   good intentions: a coarsening of C1 is invisible in every artefact, whereas dropping a C2 operator
   **lowers a printed integer** (`# mutation.generated`) and cannot remove an already-recorded survivor
   from the asserted exact set.
2. **Code-space defects.** `MutantPort` wraps a *perfect port* and transforms *results*
   (`:132-135`). It therefore cannot express "the port registered the operation under the wrong name",
   "the arity guard is checked after the dereference" (a real historical shape —
   `specification.md:634`: "**`matches` dereferences `params[1]` before checking `params.length == 2`**")
   or "the registration order is wrong" (`specification.md:746`, called "the highest-blast-radius
   constraint in the whole port"). **The mutant population is result-space; a large part of S4–S7's real
   defect population is code-space.** This is the sharpest limit of C2 and it is in §5.
3. **Equivalent mutants.** A mutant semantically identical to the reference cannot be killed, and
   deciding equivalence is undecidable in general. Left unhandled, the score is capped below 1 for a
   reason that is not a coverage fact; handled by a sign-off, the bucket becomes a place to hide
   inconvenient survivors — the same pressure as bulk `AcceptedThrowPairs` sign-off
   (`harness-contract.md` §7, trap 2). §4 says how to price it.
4. **Operations the harness cannot name.** 33 of 318, `equals(Object)` and `compareTo(Object)` on all
   eight receivers among them (`stage-01-verification-round5.md` §3.3). A mutant there is not merely
   undetected, it is **unplantable** — adequacy over the nameable 285 says nothing about it.

**Cost.** The highest runtime of the three. Arithmetic, not a measurement: one mutant over the full
stage-shaped inventory is **19 083 rows** (`stage-01-verification-round5.md` §1.1), and today's twelve
probes therefore drive ≈ 229 000 rows inside a test class that already runs in the default build
(209 methods, 0 failures — the count in the task's context). At *N* generated mutants the naive figure
is *N* × 19 083. The mitigation is already in the design: `Probe.targets` means a mutant only needs its
**target** operations, plus **one** full-inventory control run to establish `diverging operations 0`. For
`P2` that is 4 operations (`ADDITIVE`, `:177-179`) rather than 285. **The real budget MUST BE MEASURED BY
THE IMPLEMENTING STAGE**; I did not run Maven and will not estimate a wall-clock figure.

**How it would have scored the `42.0` defect: SURVIVOR, NAMED, WITH ITS WINDOW.** `P10` is already in
the set and already measures `DETECTED on 0 operation(s)` with a byte-identical tally
(`stage-01-verification-round5.md` §3.1, quoted at the head of this document). Under an adequacy score
it is a survivor, the score falls below 1, and the printed survivor line names the operations and the
window. **C2 is the only candidate of the three that scores the headline defect as a miss.**

Two adequacy figures are derivable **today** from published measurements, and the choice between them
is a design decision I make explicitly in §4:

| kill criterion | killed | survived | survivors | source |
|---|---|---|---|---|
| **a row diverged** (`DIFFER` or `MIXED` on a target where the control had none) | **8 of 11** | 3 | `P8`, `P9`, `P10` | §2.1–§2.9 of round 5: "Eight probes produced divergence — `P1`–`P7` and `P11`"; `P8`/`P9` "**Zero DIFFER rows. Zero detected operations.**"; `P10` `DETECTED on 0` |
| **the per-operation verdict tally differs from the control's** | **10 of 11** | 1 | `P10` only | `P8` target tally `{AGREE=433, HARNESS_ERROR=143}` against control `{AGREE=576}` (§2.9); `P9` "All 1 602 rows become `UNSUPPORTED`" (§2.9); `P10` `control {AGREE=576} / mutant {AGREE=576}` (§3.1) |

Note what is **not** an acceptable kill criterion: *the gate refused*. A **perfect** port is refused on
**92 of 285** operations by clause 2 alone (**D-29**, `stage-01.md` §10.4), so gate refusal would score
fidelity as detection on a third of the surface. §4 rules it out by name.

### 2.3 Candidate C3 — interval / grid coverage of the value × uncertainty plane per receiver type

**Definition.** One fixed lattice per `UValue.Kind`, shared by every operation, defined in one place
and never per operation. Coverage = cells witnessed **on a measured row** / cells declared, reported
per receiver kind, per operation, and — the part that carries the information — per **slot tuple** for
multi-slot operations.

A concrete proposal for the real-valued kinds, 11 × 9 = **99 cells**:

| value axis (11) | uncertainty axis (9) |
|---|---|
| `v1` NaN · `v2` −∞ · `v3` finite ≤ −1e6 · `v4` (−1e6, −1] · `v5` (−1, −0) · `v6` −0.0 exactly · `v7` 0.0 exactly · `v8` (0, 1) · `v9` [1, 1e6) · `v10` ≥ 1e6 · `v11` +∞ | `u1` NaN · `u2` < 0 · `u3` 0.0 exactly · `u4` (0, 0.5) · `u5` 0.5 exactly · `u6` (0.5, 1) · `u7` 1.0 exactly · `u8` finite > 1 · `u9` +∞ |

`v6`/`v7` are separate cells on purpose: `-0.0` and `0.0` are the dimension of the one defect round 5
planted that the harness half-missed (`P11-negative-zero-collapse`, detected on `floor()`, `neg()`,
`mult(value)`, missed on `round()` — `stage-01-verification-round5.md` §3.2). Per-kind lattices matter:
`UINTEGER` gets no NaN or ±∞ value cell, because `uInteger` takes an `int` (`UValue.java:277`), and a
declared-but-unreachable cell is how a coverage floor becomes unsatisfiable — D-29's shape in the new
dimension (§4).

**A hand-derived illustration — labelled as such.** The following is arithmetic over the 22 literals at
`InputGenerator.java:170-194`, which I read, under the lattice above, which I invented in this document.
**It is not a harness measurement and no run produced it.** Cell by cell, the 22 `uReal` boundary values
occupy:

```
(v7,u3) (v7,u7) (v6,u3) (v9,u3) (v9,u7) (v4,u3) (v4,u7) (v4,u5) (v8,u5) (v5,u4)
(v9,u4) (v4,u4) (v8,u3) (v10,u3) (v3,u3) (v1,u3) (v11,u3) (v2,u3) (v9,u1) (v9,u9) (v9,u2)
```

**21 distinct cells of 99** — the 22 literals collide once, `uReal(2.0, 0.0)` and `uReal(1.0, 0.0)`
both landing in `(v9,u3)`. All 11 value cells are occupied; **two uncertainty cells are empty**:
`u6` = (0.5, 1) and `u8` = finite > 1. The boundary corpus has uncertainties
`{0.0, 1.0, 0.5, 0.25, 0.001, NaN, +∞, −1.0}` and nothing strictly between 0.5 and 1, and nothing
finite above 1. Whether the random draws fill `u6` depends on the seed —
`round6(random.nextDouble())` (`InputGenerator.java:73`) can land there — which is precisely why this
has to be *measured per run and printed*, not argued in prose.

The consequence for the smoke sweep is the number worth putting in front of a reader. For
`URealValue.add(value)` the joint receiver × argument space is 99 × 99 = **9 801** cells; 28 receivers
× 28 arguments can occupy **at most** 28 × 28 = 784, and since the 22 boundaries collapse to 21 cells,
at most about **625 of 9 801 ≈ 6 %** — against a golden header that today reads `# rows.agreement 784`
and `# rows.disagreement 0` (`docs/port2/differential/s1-smoke-ureal-add.tsv`). That contrast is the
whole argument for building C3: **the honest number is small, and a small number cannot be misquoted as
proof of fidelity.**

**What it would catch.** Gross regional holes, mechanically, with no per-operation declaration — and
therefore also the two the project found by hand (D-19, D-31). It gives every C2 survivor an *address*:
survivor + unoccupied cell = a corpus gap someone can close.

**What it would not catch.** In-cell blindness, at finer grain than C1 but the same in kind; anything
relational (a == b, both strings empty, index == length); and anything about the codomain.

**Cost.** The lowest. One lattice class plus one census reader, no per-operation input, and — see §3 —
one small structural change so the census reads **typed** values rather than rendered text.

**How it would have scored the `42.0` defect: FULL COVERAGE, NO SIGNAL.** `42.0` is in `v9` = [1, 1e6),
occupied by `1.0`, `2.0` and `100.0`. **C3 scores the headline defect green too.** It would also score
`P11`/`round()` green: the input `uReal(-0.0, 0.0)` **is** in the corpus (`InputGenerator.java:174`), so
`v6` is occupied; that blindness is codomain-side and no input census can see it.

### 2.4 The comparison, on one page

| | C1 per-operation partition | C2 mutation adequacy | C3 fixed lattice |
|---|---|---|---|
| Denominator produced by | the author, per operation | the machine, from an author-written operator schema | the machine, from one shared lattice |
| Coarsenable invisibly? | **yes** — a wider interval leaves no diff | no — `# mutation.generated` falls, and the asserted survivor set does not shrink | no — one lattice, asserted as an exact literal |
| Catches relational / semantic boundaries | **yes** (its unique strength) | only where an operator encodes one | no |
| Catches a defect inside a covered region | no | **yes, where a windowed operator names that window** | no |
| Answers "would we see it?" | no (proxy) | **yes (direct)** | no (proxy) |
| Cost | highest, recurring, 285 + 39 operations | highest runtime; machinery ~90 % built | lowest |
| Score on the `42.0` defect | **green — no signal** | **survivor, named, with its window** | **green — no signal** |
| Score on `P11`/`round()` | green | survivor (it is the second member of the recorded blind set, §3.2) | green |

### 2.5 Combination, and what I reject

**Reject C1 as specified — per-operation declared partitions.** Highest cost, and its denominator is
exactly the kind of author-influenceable quantity that eight review rounds were spent removing from this
harness (`harness-contract.md` §9). Keep its *catching power* by a route that has a machine-fixed
denominator, below.

**Keep C1's strength as "C1-lite": a fixed, globally shared set of named relational predicates.** One
list, ~20–30 predicates, computed from the typed tuple, applicable-by-arity-and-kind (machine-derived),
never per operation. Drawn from `specification.md` §2.1–§2.5, which already names them:
`a == b` · `a == -b` · `a < b` · `a > b` · `u_a == u_b` · `u_a == 0 ∧ u_b > 0` · both operands zero ·
both strings empty · exactly one string empty · strings differ only by case
(`specification.md:643`, the `toUBoolean` fold) · index == 1 (`specification.md:632`, 1-based) ·
index == |s| · index == |s|+1 · index < 1 · threshold `k == 0` and `k == 1`
(`specification.md:466`, `equalsC`) · confidence exactly at the 0.5 fold
(`specification.md:643`) · value integral vs non-integral · receiver and argument of different kinds.
Coverage = which **applicable** predicates were witnessed on a measured row. Cost: one class. It buys
most of C1's unique catch at none of C1's gameability, because applicability is derived and the
predicate list is shared.

**RECOMMENDED COMBINATION: C2 as the gated instrument, C3 + C1-lite as the reported reach census.**

Why this pairing and not another:

* **C2 is the only candidate that scores the defect this project actually measured as a miss.** That is
  determinative. Everything else is a proxy, and the two proxies both report green on it.
* **C3 + C1-lite is nearly free and makes C2's output actionable.** A survivor alone says "a defect of
  this shape would be missed"; a survivor **beside an unoccupied cell** says where to widen the corpus.
  The pair is a work queue, not a caveat.
* **C3's number is honestly small** (≈ 6 % for the smoke sweep's joint space, hand-derived above), which
  inverts the usual reporting hazard. The danger in this project is a number that reads stronger than
  the run behind it (D-15, D-43, D-53). A coverage figure of 6 % cannot do that.
* **They fail differently.** C3 is a property of the corpus alone and the port cannot move it. C2 is a
  property of (corpus, harness, operator set) and the **real port is not its subject at all** — the
  adequacy run compares `reference` against `MutantPort(perfectPort)`
  (`PortedInfidelityDetectionPowerTest.java:62-68, :516-520`), so a bad real port cannot raise the
  adequacy score. Both are therefore immune to D-43's failure mode, in which the thing under test
  supplied half of what the instrument compared.

**Do not blend them into a single "coverage score".** D-15's and D-53's lesson is that one number mixing
a measurement with a judgement gets quoted as the measurement. Two numbers, two names, two denominators,
never added.

---

## 3. Recommendation and build plan

Five stages, all **test-scoped**, all under
`use-core/src/test/java/org/tzi/use/uncertainty/differential/`. No `*/src/main/*` file is touched, so
`git diff --name-status 30d480db..HEAD -- '*/src/main/*'` stays empty (ground rule 2). No `pom.xml`
change: this needs no new dependency, and the only permitted pom edit this round is B3's profile. **All
line counts are ESTIMATES.**

### H14-1 — the reach census, no gate (foundation)

| | |
|---|---|
| New | `DomainCell.java` (~140), `DomainLattice.java` (~300), `RelationalPredicates.java` (~220, C1-lite), `DomainReach.java` (~220, the census) |
| Edited | `DiffRow.java` (+~30), `DifferentialSweep.java` (+~40) |
| Tests | `DomainReachTest.java` (~320) |
| ESTIMATE | 5 new files, 2 edited, ~1 270 lines |

**The one structural change, and why it is unavoidable.** The census must read **typed** `UValue`s, not
the rendered `inputs` column. `DiffRow` today carries `List<String> inputs` (`DiffRow.java:28, :69`) —
canonical text. Recovering `42.0` from `UREAL(42.0,0.5)@URealValue` means parsing prose, and `DiffRow`
itself already says why that is forbidden: a count derived from prose "silently becomes zero when the
prose is reworded" (`DiffRow.java:98-101`, the reason `subjectTypeProvenance` is carried structurally).
So `DiffRow` gains `List<UValue> inputValues()`, carried structurally and **not** a TSV column — exactly
H21's pattern and for exactly H21's reason (`DiffRow.java:103-107`: "Not a TSV column, on purpose.
`toTsv()` is unchanged by H21, so no data row in any golden moved"). The edit surface is small and
verified: **`new DiffRow(` appears 8 times in the whole tree, all inside `DifferentialSweep.run`**, which
already holds the typed `tuple` at every one of them (`DifferentialSweep.java:112-119`).

**The one discipline that must be built in from the first line.** A cell counts as reached **only on a
measured row** — `AGREE` or `DIFFER` (`harness-contract.md` §3). A cell "reached" on a `HARNESS_ERROR`,
`UNSUPPORTED` or `UNMEASURABLE` row was never exercised by either implementation, and counting it is
D-11/D-12's defect ("rows are not measurements") in the new dimension. This mirrors
`referenceValues()`, which counts "the **reference** column over **measured rows only**"
(`DifferentialSweep.java:624`; `harness-contract.md` §3). It is not an optimisation; it is the property.
The regression test must pin it directly: a sweep in which a cell appears **only** on `HARNESS_ERROR`
rows must report that cell **unreached**.

Other assertions this stage must carry: the declared cell count per kind is an **exact literal** (so
shrinking the lattice fails a test that names the number — the "all fourteen buckets asserted" pattern,
`harness-contract.md` §6); `-0.0` and `0.0` land in **different** cells; NaN, ±∞, `MIN_VALUE`,
`MAX_VALUE` each land in the intended cell; the census is seed-stable.

**Buys:** the honest number, quotable in a stage document immediately, gating nothing and risking
nothing. S1's own two sweeps can be re-described with it without touching a golden.

### H14-2 — the census reaches the artefact

| | |
|---|---|
| Edited | `DiffReportWriter.java` (+~90), `DifferentialSweep.java` (`stageStatement`, +~25) |
| Tests | `DifferentialHarnessRegressionTest.java` (+~140) |
| Goldens | both S1 goldens refreshed, **header lines only** |
| ESTIMATE | 3 edited files, ~255 lines, 2 golden refreshes |

Header keys in §4. The precedent for the cost is measured, not guessed: H21 added the provenance split
to the same two goldens at "+4 header lines each, no data row moved"
(`foundation-verdict.md` §3.0, H21 row). Because `inputValues()` stays off the TSV, the same holds here.
Refresh only under `-Duse.differential.golden.refresh=true`, in a commit that says why
(`harness-contract.md` §6, last-but-one row).

New regression tests: the per-operation coverage figure reaches the header (mirroring
`theReportHeaderCarriesDiscriminatingPowerPerOperation`, `harness-contract.md` §6); **no ratio or
percentage is emitted anywhere** (§4); and `stageStatement()` cannot render an agreement figure without
the reach figure beside it — with the caveat that this binds the **method**, not the class (**D-53**).

**Buys:** the number reaches the artefact a human reads, per operation, and a stage quoting
`stageStatement()` verbatim cannot avoid seeing it.

### H14-3 — the gate clause

| | |
|---|---|
| New | `DomainReachFloor.java` (~150), `AcceptedUnreachedCells.java` (~180) |
| Edited | `DifferentialSweep.java` (clause 4, +~70); call sites in `UncertaintyDifferentialSmokeTest`, `UnwrittenPortInvariantTest`, `DifferentialHarnessRegressionTest`, `PortedInfidelityDetectionPowerTest` |
| Tests | `DifferentialHarnessRegressionTest.java` (+~200) |
| ESTIMATE | 2 new files, ~6 edited, ~700 lines; **26 gate call sites** (`requireStagePass` / `stageGateFailures` / `isStagePass` — measured by grep: 13 + 8 + 1 + 2 + 4 + 7 across the six files) |

Design, and every clause of it is a precedent rather than an invention:

* A **fourth clause** in `stageGateFailures` (`DifferentialSweep.java:727`), so the existing "throws with
  **every** failing clause and the numbers behind it" behaviour extends to it.
* `requireStagePass` takes the floor as a **mandatory third parameter with no eliding overload**, and a
  reflective test asserts no parameterless overload ever comes back. That is D-34's fix verbatim — the
  3-argument `writeAll` that "silently substituted `AcceptedDegenerateOperations.none()`", closed by
  deleting the overload and pinning its absence reflectively "because 'all the call sites pass it today'
  is a fact about today" (`harness-contract.md` §4.3; `DiffReportWriter.java:112-131`).
* `DomainReachFloor.none()` exists, is explicit, and is **written into the report header**, so a run with
  no floor is never byte-indistinguishable from a run with one (`DiffReportWriter.java:151-155`, the same
  argument for `acknowledged`).
* `AcceptedUnreachedCells` is keyed on **(operation, cellId)** with a mandatory non-blank rationale,
  copied into `stageStatement()` and the header — `AcceptedDegenerateOperations`' shape
  (`AcceptedDegenerateOperations.java:31-49`), including its self-lapsing property: a sign-off keyed on a
  cell id lapses if the lattice changes.

**Anticipate D-29 rather than rediscover it.** A declared lattice **will** contain cells no faithful port
can reach — `UBOOLEAN` value cells are exhausted by two inhabitants
(`InputGenerator.java:268-272`: "a two-element corpus is the whole domain of the type, so 'boundary' and
'exhaustive' are the same list here"), and an absolute cell floor on `UStringValue.uSubstring(int,int)`
will refuse for the same reason it measures 17 rows of 432 (D-31). D-29 is what an unsatisfiable gate
produces: **154** hand-authored `AcceptedThrowPairs` entries as the only route out, and the standing
temptation to fall back on `isClean()` (`stage-01.md` §10.4, D-29 row). So: declare the floor **per
receiver kind**, derive it from the lattice arithmetic **before the run** (`harness-contract.md` §8
step 2: "a floor chosen after seeing the run is not a floor"), and record both the floor and the achieved
count in the header so a post-hoc floor is a diff someone has to approve.

**Buys:** the number is binding and a stage cannot forget it. This is the D-15 lesson applied — the gate
is what stopped 119 degenerate operations reading as passes.

### H14-4 — systematic mutant generation and the adequacy score

| | |
|---|---|
| New | `MutationOperator.java` (~200), `MutantCatalogue.java` (~450), `MutationAdequacy.java` (~250), `MutationAdequacyTest.java` (~500) |
| Refactor | promote `MutantPort`, `Mutation`, `Probe`, `ProbeResult`, `measure` out of `PortedInfidelityDetectionPowerTest` into their own files (a **move**, not a rewrite), leaving the eleven probes in place as the hand-authored subset that must stay green |
| Report | one new golden, `docs/port2/differential/<stage>-mutation-adequacy.tsv` |
| ESTIMATE | 4 new files + 1 refactor, ~1 400 lines, 1 new golden |

Assertions that must ship with it, each closing a door this project has already had opened:

1. **The control diverges nowhere.** Without `diverging operations 0` nothing is attributable
   (`stage-01-verification-round5.md` §1.1). This is a precondition, asserted first.
2. **`# mutation.generated` equals an exact literal**, so dropping an operator fails a test that names
   the number.
3. **The survivor set equals an exact recorded set** — the `subtleInfidelitiesAreDetectedOrNamed`
   pattern (`PortedInfidelityDetectionPowerTest.java:657-698`), so blindness cannot grow or shrink
   silently.
4. **Every "semantically equivalent" mutant carries a written rationale** in a keyed sign-off list whose
   size is in the header. Dumping survivors there costs a sentence in the evidence file, one at a time —
   `AcceptedThrowPairs`' friction, deliberately (`harness-contract.md` §7, trap 2).
5. **Both kill criteria are reported separately** (§4), and the gate reads the divergence one.

**Buys:** the only number that answers D-30's question, and a survivor list that is a corpus work queue.

### H14-5 — close the loop (the only stage that reduces risk)

For each survivor: widen the corpus until it dies, recording the added inputs and the re-measured
adequacy; **or** sign it off with a written window saying what a reader must not conclude. **ESTIMATE:**
no new files; `InputGenerator` additions (~60 lines) plus goldens for every affected sweep.

**Buys:** actual detection. H14-1…4 measure the risk; only H14-5 lowers it. Say that plainly in the
stage document, because the temptation after building an instrument is to treat the instrument as the
fix.

### Ordering, and what may be reordered

`1 → 2 → 3` is strict: a census with no artefact is unquotable, and a gate on an unpublished number is
unreviewable. `4` is independent of `1–3` and could run first, but **after** them each survivor can be
paired with an unoccupied cell, which is what makes it actionable. `5` is last by definition. If the
round has to be cut short, **stop after H14-2**: an honest published number with no gate is a real
improvement over prose, and it costs two golden header refreshes. **Do not stop after H14-3 without
H14-4**: a gate on the reach census alone is a gate on the proxy that scored the `42.0` defect green,
and D-15's history says a gate gets quoted as the thing it does not measure.

**Total ESTIMATE:** ~11 new files, ~9 edited, ~3 600 test-scoped lines, 2 golden refreshes + 1 new
golden. Runtime cost **MUST BE MEASURED** by whoever owns Maven; the arithmetic to plan with is
19 083 rows per full-inventory mutant sweep, and `Probe.targets` is the lever.

---

## 4. How it reports

### 4.1 Header keys — the reach census, per file and per operation

Composing with `DiffReportWriter`'s existing block (`DiffReportWriter.java:226-334`), after
`rows.subjectTypeAssumed` and inside the `op.<key>.*` block respectively:

```
# domain.lattice                        uvalue-lattice/1
# domain.cellsDeclared                  99
# domain.cellsReachedOnMeasuredRow      21
# domain.predicatesApplicable           14
# domain.predicatesWitnessed            9
# domain.reachFloor                     18            (or "none")
# domain.cell.unreached.1               UREAL/v9/u6
# domain.cell.unreached.2               UREAL/v9/u8
# ...
# accepted.unreachedCells               0
# accepted.unreachedCell                <op>|<cellId> -> <rationale verbatim>

# op.<key>.domain.cellsDeclared         9801
# op.<key>.domain.cellsReached          441
# op.<key>.domain.predicatesApplicable  11
# op.<key>.domain.predicatesWitnessed   7
```

Five rules, each earned by a recorded defect:

1. **Two integers, never a ratio.** `harness-contract.md` §4.5: "Never a bare agreement percentage."
   The same applies here and more strongly, because a percentage travels and a cell list does not.
   `reached 21 of 99; unreached: UREAL/v9/u6, UREAL/v9/u8` survives being quoted out of context;
   `coverage 0.21` does not. **No `# domain.coverage` key exists in this design, deliberately.**
2. **Per operation, and read per operation** — `# rows.*` and `# verdict.*` are file sums and hide an
   operation that measured nothing (**D-21**, `harness-contract.md` §4.5). The new keys inherit
   **D-41**: `# op.<key>.*` keys are **not unique** if one report holds several results for one
   operation (`DiffReportWriter.java:303-326`; `stage-01.md` §10.4, D-41 row). H14 must not repeat it —
   either state it in the new keys' documentation, or fix D-41 first. It is latent today and directly in
   the path of a stage sweeping one operation over several corpora.
3. **The unreached list is enumerated, bounded and printed.** The gap is the finding; the count is the
   headline. An unbounded list is not acceptable in a golden, so cap it and print the count of the
   remainder — a truncation that says it truncated.
4. **The floor is in the header whether or not it fired**, including `none`, so a floored run is never
   byte-identical to an unfloored one (D-34's argument, `DiffReportWriter.java:151-155`).
5. **`stageStatement()` carries the reach figure unconditionally, including when it is complete**, for
   the reason the type-mismatch figure is printed even at zero: "'0' is the claim a stage needs to be
   able to make" (`DifferentialSweep.java:780-784`).

Proposed `stageStatement()` line — extending the current format
(`DifferentialSweep.java:785-810`):

```
URealValue.add(value): 784 rows, 784 measured, 784 agreed, 0 disagreed,
  0 java-type mismatch(es) (subject token OBSERVED on 0, ASSUMED on 0),
  258 distinct reference value(s) [DISCRIMINATING],
  441 of 9801 input cells reached, 7 of 11 relational predicates witnessed
  [REACH FLOOR 400 met]
```

### 4.2 The mutation-adequacy report is a separate artefact

It is not a row-level property of a sweep, so it must not be squeezed into a sweep's header. Own file,
own golden:

```
# mutation.control.divergingOperations   0        <- precondition; nothing below is attributable if non-zero
# mutation.operators                     23
# mutation.generated                     186
# mutation.killedByDivergence            171
# mutation.killedOnlyByTallyChange        8
# mutation.survived                        7
# mutation.equivalent.signedOff            0
# mutation.survivor.1   P10-narrow-input-window | URealValue.add(value),URealValue.minus(value),UIntegerValue.add(value),UIntegerValue.minus(value) | window: receiver value == 42.0
```

`killedByDivergence` and `killedOnlyByTallyChange` are separate because they are different findings. A
divergence kill is a detection **with attribution**; a tally-change kill is a detection that the reader
would naturally blame on the instrument — that is **D-32**, measured: `P8` destroys the divergence and
the reader "is told the harness could not drive 143 rows, not that the port is wrong on them"
(`stage-01-verification-round5.md` §2.9). Summing them would erase the distinction the register was
amended to preserve.

### 4.3 Gate clause or reported dimension — the decision, with its reasoning

The task names the two failure modes to reason from, and they pull in opposite directions:

* **D-15's lesson:** a number that matters must be a **gate clause**, or it is ignored. Before the gate,
  119 of 285 single-valued operations read as fidelity; `requireStagePass` refuses them
  (`stage-01.md` §11.3.2, "a mechanism, not a convention").
* **D-43/D-52's lesson:** a check the author can influence measures the **author**, and no amount of
  mandated disclosure repairs it — "a disclosure fires only where the instrument already noticed, and
  laundering is exactly the case where it did not" (`harness-contract.md` §9). `observedFrom(aStandIn)`
  drove a real 401-row defect to `0` in one line.

Applied item by item:

| Quantity | Verdict | Why |
|---|---|---|
| **Reach census (C3 + C1-lite) cell counts** | **GATE CLAUSE**, on a floor written before the run | The denominator is one shared lattice, asserted as an exact literal, and **the port cannot move it at all** — coverage is a property of the corpus. What the author chooses is the *floor*, and clause 1 is the existing precedent for a pre-declared floor with the rule "a floor chosen after seeing the run is not a floor". The header records floor and achievement, so a lowered floor is a golden diff |
| **Unreached-cell list** | **REPORTED**, enumerated | It is the work queue, not a pass criterion |
| **Any coverage ratio** | **NEITHER — not emitted at all** | The one thing that would certainly be quoted as if enforced |
| **Mutation adequacy: survivor set** | **GATE CLAUSE, as an exact set** | Adding an easy mutant cannot remove a survivor; removing an operator lowers a printed integer. This is the shape already proven at `PortedInfidelityDetectionPowerTest.java:657-698` |
| **Mutation adequacy: the score `killed/generated`** | **REPORTED, never gated, never quoted alone** | A ratio over an author-authored operator population is gameable upward by adding easy operators. Gate the set; report the ratio |
| **"the gate refused" as a kill criterion** | **FORBIDDEN, by name** | A perfect port is refused on **92 of 285** by clause 2 alone (**D-29**). Gate refusal as detection would score fidelity as power on a third of the surface |
| **Equivalent-mutant bucket** | **REPORTED, with a per-mutant written rationale, size in the header** | It is a judgement inside a measurement; price it like `AcceptedThrowPairs` |

### 4.4 The three anti-gaming provisions, named

1. **The lattice cannot shrink quietly.** Declared cell count per kind is asserted as an exact literal;
   `DomainLattice` has no dependency on `InputGenerator`, so the corpus author cannot reach the
   denominator from the file where the corpus lives.
2. **The operator population cannot shrink quietly.** `# mutation.generated` and
   `# mutation.operators` are asserted as exact literals, and the survivor set is asserted as an exact
   set.
3. **No number in this design can be moved by the subject.** The reach census is computed from the
   inputs; the adequacy run's subject is `MutantPort(perfectPort)` against a second isolated
   `HistoricalOracle` (`PortedInfidelityDetectionPowerTest.java:62-68`), never the real port. This is
   the direct answer to `harness-contract.md` §9's standing question — *can the thing under test
   influence what this rule measures?* — and the answer here is **no**, which is also precisely why
   §5.1 is true.

### 4.5 The sentence that must travel with every reach figure

> **Coverage is not fidelity evidence. It is a bound on the meaning of fidelity evidence.** A perfect
> port and a defective port produce the **same** reach census, because the census is a property of the
> corpus and not of the implementation.

Put it next to the number, in `stageStatement()`'s own output if possible. And bind it on the
**method**, not on the class: `agreementCount()`, `agreements()` and `isClean()` are public and return
the agreement population unaccompanied, and the class-level claim to the contrary was withdrawn as
**D-53** (`harness-contract.md` §4.5; the false Javadoc is still at
`DifferentialSweep.java:777-784`). Any new accessor `DomainReach` exposes will have the same property
on day one. Do not write "there is no way to render X without Y" in a Javadoc; it has been false twice.

---

## 5. What it still will not tell you — the residual D-30

An over-promised coverage measure is worse than none. Nine residuals, in descending severity.

**5.1 Measuring coverage adds no detection. None.** This is the whole of the honest claim. After
H14-1…4 the `42.0` defect is *still not detected* — it is *named*. The survivor line says "a defect of
this shape in this window would be invisible"; the port carrying such a defect still passes. The only
stage that changes detection is **H14-5**, widening the corpus. If H14 lands and the survivors are
neither closed nor signed off, D-30 has been **relabelled, not reduced**, and any stage document must
say so in those words.

**5.2 In-cell blindness is total and permanent.** Both proxies score both known instances of D-30 green:
`42.0` falls in an occupied cell (`v9`, with `1.0`/`2.0`/`100.0`), and `P11`/`round()`'s input `-0.0`
**is** in the corpus. Refining the lattice does not fix this; it only moves the boundary, because the
domain is uncountable and any finite partition has interiors. **A stage may never write "the input
domain is covered."** The strongest true sentence is: "*N* of *M* declared cells were reached on a
measured row; within a reached cell, nothing is claimed."

**5.3 The mutant population is result-space; a large part of the real defect population is
code-space.** `MutantPort` wraps a perfect port and transforms its results
(`PortedInfidelityDetectionPowerTest.java:132-135`). It cannot express: wrong registration order
("the highest-blast-radius constraint in the whole port", `specification.md:746`); an operation
registered under the wrong name (`specification.md:464` — the class is `Op_uBoolean_setUncertainty`
while `name()` is `"setConfidence"`, and there is no `setUncertainty`); a guard evaluated after a
dereference (`specification.md:634`); a wrong arity or a wrong parameter-conformance mode
(`typeOf` vs `kindOf`, `specification.md:462-466`). And this is not hypothetical for this port: **B7**
mandates fixing 33 behaviour-changing historical defects, each with a written justification
(`foundation-verdict.md` §3.0, B7 row), and **B2** mandates a full hand port of 39 `SBoolean`
operations whose 1 502 lines have **zero** fork-test coverage behind them (same row). Those defect
shapes will not appear in any generated mutant population, so **the adequacy score is an upper bound on
power against a defect distribution that is not the one S4–S7 will actually produce.**

**5.4 `SBoolean` is at coverage zero and cannot be raised by this design.** `SBooleanValue`'s 39
operations are not in `MARSHALLABLE_RECEIVERS`; there is no `SBoolean` marshalling and no `UValue.Kind`
for it, and every row is `UNSUPPORTED` (`harness-contract.md` §5, second row). So the operations B2 just
promoted to a port target, with no upstream oracle behind them, get **no cells, no mutants and no
adequacy figure**. Any H14 number must be published with its surface denominator beside it — "*N* of *M*
cells over **285 of 318** nameable operations, **0 of 39** `SBoolean` operations" — or H14's own figure
becomes the next D-15: a true number read as a stronger claim.

**5.5 The unmeasurable surface is unchanged.** 33 non-nameable operations, `equals(Object)` and
`compareTo(Object)` on all eight receivers among them, with "no row, no verdict, not even an
`UNSUPPORTED` marker" (`stage-01-verification-round5.md` §3.3; `harness-contract.md` §5). 8 `void`
mutators whose post-state is never re-read, so "a void operation cannot be shown faithful by this
harness at all" (`harness-contract.md` §5, first row). Collection receivers and the whole type layer.
Coverage of the inputs of an operation that has no rows is not a small number — it is undefined, and a
census must print it as `n/a`, never as `0` and never omit the operation.

**5.6 The equivalent-mutant problem is undecidable, and the sign-off is a judgement inside a
measurement.** Every mutant classified "semantically equivalent" is a survivor removed from the
denominator by a human. The rationale requirement and the header count price it; they do not solve it.
Expect this to be the number a reviewer attacks first, and expect it to be right to.

**5.7 A survivor's attribution to a specific corpus gap is a heuristic, not a derivation.** Round 5
measured that detection is **robust to thinning within a reached region**: removing five `uReal`
corpus values cost no probe an operation and at most 26 % of its rows
(`stage-01-verification-round5.md` §6.1). Detection is therefore redundant inside a reached region,
which means a mutant's *death* does not identify which input killed it, and pairing a *survivor* with an
unoccupied cell is a plausible explanation rather than a proof. Useful; not a derivation. Round 5 also
recorded a prediction of its author's that was wrong in this exact area (`P3` was expected to go to zero
and did not), which is the calibration to keep.

**5.8 Interaction depth above 2 is unmeasured.** The design proposes 1-way and 2-way (slot-tuple) cells.
`uSubstring(int,int)` has three slots and is already **17 measured rows of 432** (D-31). Its 3-way cell
census will read near zero, which is the honest restatement of D-31 — but the residual is real: 3-way and
higher interactions get a number so small that it carries no discriminating information.

**5.9 Every figure is seed- and corpus-conditional, so it inherits the D-28/D-31 species.** "159
single-valued operations" is "a joint fact about the implementation and the corpus"
(`harness-contract.md` §5); every H14 number is a joint fact about the lattice, the corpus and the seed
(`InputGenerator.DEFAULT_SEED = 20260817L`, `InputGenerator.java:50`). It must be quoted with
`# seed`, and a re-seeded run legitimately moves it. Related and worth stating: the corpus census a run
already prints reads `boolean=4` for a type with two inhabitants (**D-42**), because
`booleanCorpus(RANDOM_DRAWS)` appends random draws to an already-exhaustive domain
(`UnwrittenPortInvariantTest.java:1150-1157`). A cell census over the same corpus will report
`UBOOLEAN` value cells as fully reached — correctly — while the row count overstates the domain. Fix
D-42 in H14-1 or the two artefacts will contradict each other in the same report.

---

## 6. Two citation-hygiene findings, noticed while reading (not part of H14)

Recorded because this project treats a stale citation as a defect, and because H14 will add citations to
the same file.

1. **`harness-contract.md`'s line numbers into `DifferentialSweep.java` are stale.** The contract cites
   `distinctReferenceValues()` at `:523` (§1), `requireStagePass` at `:588` (§1),
   `DISCRIMINATING_MINIMUM = 2` at `:332` (§1), `isClean()` at `:492` (§4.1) **and** at `:591` (§4.5) —
   two different numbers for one method in one document. Measured now by grep on the current file
   (1 007 lines): `DISCRIMINATING_MINIMUM` **:444**, `agreements()` **:518**, `agreementCount()`
   **:533**, `isClean()` **:604**, `referenceValues()` **:624**, `distinctReferenceValues()` **:635**,
   `requireStagePass` **:711**, `stageGateFailures` **:727**, `stageStatement` **:785**,
   `javaTypeMismatchCount` **:874**. H21 moved them. Anything H14 adds will move them again; prefer
   citing **method names** over line numbers in the contract, or re-derive the whole set in one pass.
2. **`DifferentialSweep.java:777-784` still carries the D-53 over-claim** — "there is deliberately no way
   to render an agreement figure from this class without the discrimination figure beside it" — which
   `harness-contract.md` §4.5 has withdrawn as false (`agreementCount()`, `agreements()` and
   `isClean()` are public and unaccompanied). D-53's register row says the same: "corrected in the
   contract by this commit; **the Javadoc sentence is still the old one**". H14 adds accessors to the
   same class and must not repeat the sentence.

---

## 7. What is left for the human, and what I am not deciding

Three items inside H14 that are genuine decisions rather than design detail. I state a recommendation
for each and do not treat any as settled.

1. **Does `requireStagePass` gain a third mandatory parameter (H14-3), or does the reach gate live in a
   separate call?** Recommendation: **the third parameter, with no eliding overload** — §4.1's
   precedent chain (D-34, D-36) says a separate call is forgettable and "the gate is still opt-in" is
   already a named weakness. Cost: 26 gate call sites edited and a re-review of a contract that has been
   through eight rounds. A reviewer may reasonably prefer the smaller blast radius; if so, the fallback
   is a separate call **plus** a header key that records whether it was made, so a stage that skipped it
   is visible in the golden.
2. **Is D-41 fixed before H14-2, or inherited?** Recommendation: **fix it first.** It is a
   one-header-block change (`DiffReportWriter.java:303-326`), it is latent today, and H14 doubles the
   number of non-unique `op.<key>.*` keys.
3. **Is the mutation-adequacy run part of the default build, or gated behind a profile like B3's
   `-Pupstream-oracle`?** Recommendation: **measure the runtime first, then decide**; the arithmetic
   (19 083 rows per full-inventory mutant, targeted sweeps otherwise) is in §3, and the measurement is
   not mine to take this round.
