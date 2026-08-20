# S9 — B7: the pre-registration mechanism, and the value-layer corrections it adjudicates

**Role: Record.** Written 2026-08-20 on branch `port-uncertainty-2`. Every number below comes from a
named command whose output is quoted verbatim. Nothing here is estimated.

---

## 0. What this stage did, in one paragraph

The user's decision **B7** (2026-08-17, binding) is that the port **fixes** the fork's defects rather
than reproducing them bug-for-bug. Until this stage that decision was recorded and not executed: 1 of
the 33 behaviour-changing ledger rows had landed, and the ported file headers still read *"Semantics
unchanged"*, which documented the opposite of what the user asked for. This stage built the mechanism
B7 requires, applied eight value-layer corrections, and evidenced every one of them.

---

## 1. The mechanism, and why it had to come first

`DifferentialSweep` measures **difference**. It has no access to **intent**. Given a red row it
cannot tell *"we corrected `UStringValue.equals`, which was the constant `false`"* from *"the port
dropped a conjunct"* — both are one operation, two values, not equal.

So under B7 every fix makes the instrument redder, and a divergence that is *discovered* is
indistinguishable from a porting error. `b7-fix-plan.md` §0.1 says this outright, and §4.3 designs
the answer: pre-registration.

### 1.1 What was built

| File | What it is |
|---|---|
| `use-core/src/test/java/org/tzi/use/uncertainty/differential/IntendedDepartures.java` | The pre-registration list. Three declaration forms, each with a written rationale, a ledger row, and a predicted direction. |
| `DiffVerdict.INTENDED_DEPARTURE` | New verdict. `isAgreement() == false`, `isMeasurement() == true`. |
| `DifferentialSweep` | Threads the list through `classify`, adds the population post-pass, adds gate clause 4, and exposes `tuplesOf`. |
| `DiffReportWriter` | `# rows.intendedDeparture` and `# intendedDeparture.<row>` in the header, **including when zero**. |
| `IntendedDeparturesTest` | 28 adversarial tests, organised by the escape route each closes. |

### 1.2 The verdict's exact standing

|  | `isAgreement()` | `isMeasurement()` | counts in gate clause 2 |
|---|---|---|---|
| `INTENDED_DEPARTURE` | **no** | **yes** | **no** |

* **Not an agreement.** The two sides did not agree. Saying they did would be the `AGREE_THROWN`
  mistake — 21 816 rows of false green — in a fourth costume. `agreementCount()` does not move.
* **Is a measurement.** Two values were observed and compared. It is evidence, of a difference
  predicted in writing before the run.
* **Excluded from clause 2 and nothing else.** Clause 1 (the measurement floor) and clause 3
  (discriminating power) are untouched; both have tests asserting a departure does not rescue them.

### 1.3 Clause 4 — the half that is easy to leave out

A list that only ever **permits** differences lets an unfixed defect through: declare the departure,
forget to write the fix, and the sweep is green because the port still agrees with the defective
reference. So the gate also **requires** them. `Result.unusedDeclarations()` is non-empty exactly
when a declaration never fired, and that is a stage-gate failure with the same standing as a residual
`DIFFER`.

`IntendedDeparturesTest.CannotLaunder.clause4CatchesTheUnfixedDefect` is that scenario built
deliberately: fork against fork, every row `AGREE`, clauses 1–3 all pass, and the gate still refuses.

### 1.4 The two-argument gate is refused, not defaulted

`isStagePass(int, AcceptedDegenerateOperations)` **throws** if the sweep ran with a non-empty
pre-registration. The 64 existing call sites all run with `none()` and are unaffected; a new B7 call
site cannot reach a pass without naming the mechanism that let it. And the three-argument form checks
that the list it is handed is the list the sweep ran under, by declaration identity — otherwise the
third parameter would be a label a caller writes next to a number it did not come from.

---

## 2. Three deviations from the plan, and why

### 2.1 The digest form was replaced by an enumeration — DESIGN CHANGE

`b7-fix-plan.md` §4.3 specifies `declareBounded(..., pairsSha256, ...)`: a SHA-256 over the departing
lines, capped at 64 rows, on the argument that a digest preserves the lapse property at one entry
instead of sixty-four.

**Measured, that design does not fit the data and its safeguard is not a safeguard.** The five
departing populations are 72, 112, 343, 343 and 432 **rows** — every one above the 64-row cap — over
2, 9, 2, 2 and 210 **distinct pairs**. The row count is a fact about the corpus; the distinct-pair
count is a fact about the correction, and it is the number a human can actually read.

So `declareBounded` became **`declarePopulation(..., List<String> distinctPairs, ...)`**: the pairs
are written out in full, the cap moved onto them, and the digest is gone. It is strictly stronger —
it fires only if the observed distinct set **equals** the declared set, neither subset nor superset —
and it is reviewable, which a digest is not.

### 2.2 A third, deliberately weaker form was added — `declareWideCodomain`

One correction does not fit either form. M-12 fixes `UStringValue.compareTo`, and `String.compareTo`
returns a character difference, so it moves **432 rows onto 210 distinct pairs**. Raising the cap to
210 would have turned the class into the blanket exemption it exists to prevent — the list would
exist and nobody would read it.

`declareWideCodomain` pins **two counts** (rows and distinct pairs, both equalities) and a **sample**
a reviewer has read. It refuses to be used when the population *would* fit `declarePopulation`. It is
named to be conspicuous, its javadoc says outright that it is the weakest of the three, and it is
still a *requirement*: a fix that did not land produces zero departing rows and fails clause 4.

### 2.3 §4.1 of the plan is wrong about which rows the sweep can see — CORRECTION TO THE RECORD

`b7-fix-plan.md` §4.1 lists **nine** rows as "visible to the sweep (`DIFFER` expected)", including
F-4, M-10, M-8 and M-11.

**They are not visible.** `UnwrittenPortInvariantTest.kindsOf` admits a method only when every
parameter is a `Value`, `int`, `double` or `float`. `equals(Object)` takes an `Object`, so **not one
`equals` override is in the 355-operation census.** Confirmed empirically: the sweep reports exactly
five diverging operations, and no `equals` among them.

| Ledger row | Plan said | Actually |
|---|---|---|
| F-3 (`URealValue.hashCode`) | visible | **visible** — 72 rows |
| F-10 (`UIntegerValue.hashCode`) | visible | **visible** — 112 rows |
| M-9 (`compareTo`, both sides) | visible | **visible** — 343 + 343 rows |
| M-12 (`UStringValue.compareTo`) | visible | **visible** — 432 rows |
| **F-4, M-10, M-8, M-11** (`equals`) | **visible** | **INVISIBLE** — no census row exists |
| F-2 (`MathUtil.round`) | visible "through every equals" | **invisible**, same reason |

A stage quoting the sweep as evidence for those four would be quoting a population that does not
exist. Their evidence is `B7CorrectionsTest`, §4 below.

---

## 3. The eight corrections applied

Each carries its justification in the source, at the site, as B7 requires — not in this document only.

| Row | Site | The defect | Δoutput |
|---|---|---|---|
| **M-11** | `UStringValue.equals` | `String.equals(UString)` is false for every argument, ANDed with a comparison of the receiver's confidence **to itself**. The method is the constant `false`; `a.equals(a)` is `false`. | `SET` + `VALUE` |
| **F-10** | `UIntegerValue.hashCode` | `hash *= 7 * Double.hashCode(uncertainty())` and `Double.hashCode(0.0) == 0`, so **every** `UInteger(n, 0)` hashes to `0`. | hash only |
| **M-9** | `UIntegerValue.compareTo` | delegates to the other operand **without negating**. | `SET` (order) |
| **bundle A** | `URealValue.compareTo` | the fourth arm is an unreachable duplicate of the first, whose *body* builds a `UIntegerValue` — so the `UIntegerValue` case has no implementation and M-9's negation would negate a constant `0`. | `SET` (order) |
| **F-3** | `URealValue.hashCode` | hashes **unrounded** what `equals` compares **rounded to 10 dp**. | `SET` (membership) |
| **F-4** | `URealValue.equals` | the cross-type arms use raw `==`, three lines below an arm that rounds. | `VALUE` (widening only) |
| **M-10** | `URealValue.equals` | no `UIntegerValue` arm, and `UIntegerValue.equals` **delegates here**, so cross-type equality was `false` in both directions. | `VALUE` + `SET` |
| **M-8** | `UBooleanValue.equals` | `&& !this.value()` is dead — `valueOf` normalises every value to `true`. | `VALUE` |
| **M-12** | `UStringValue.compareTo` | compares the receiver's **bare string** against the argument's **wrapper rendering** `UString('x', 1.0)`. | `SET` (order) |
| **M-18** | `SBooleanValue.compareTo` | the whole body is `return 0;`. | `SET` (order) + `ERR` |

### 3.1 M-18 does not delegate, and that is the point

The obvious fix — hand the job to `uDataTypes.SBoolean.compareTo`, as every sibling does — trades one
defect for a crash. That comparator returns `0` whenever the L1 distance of the four masses is below
`0.001`, and a tolerance-based "equal" is **not transitive**. `B7CorrectionsTest.M18.intransitivityWitness`
exhibits a concrete triple: `a ~ b`, `b ~ c`, `a < c`.

A first draft of that test asserted the consequence — that sorting 40 such opinions throws
`IllegalArgumentException: Comparison method violates its general contract`. **It does not,
reliably.** TimSort checks the contract only when a merge happens to expose the inconsistency, so
whether it throws depends on the permutation. That test was deleted rather than tuned until it went
green: a crash that may not happen is a test that passes for the wrong reason on the day it passes.
The intransitivity is deterministic, so that is what is asserted.

---

## 4. Evidence

### 4.1 The five sweep-visible corrections — pre-registered, and every declaration fired

`mvn -o -pl use-core test -Dtest=PortedFidelitySweepTest`:

```
=========== PORTED FIDELITY, FULL CENSUS ===========
operations enumerated  355
supported by the port  355  (100%)
rows                   1294072 total, 79520 measured
verdicts               {AGREE=78218, INTENDED_DEPARTURE=1302, BOTH_THREW=50598,
                        UNMEASURABLE=808, HARNESS_ERROR=1163146}
pre-registered (B7)    5 declaration(s), 1302 row(s) adjudicated
diverging operations   0 (unintended)
====================================================
```

**0 unintended divergence, 0 unused declarations, 1302 rows adjudicated.** Before the declarations
were written the same sweep reported `DIFFER=1302` across those same five operations, so the
adjudication is exactly co-extensive with the corrections and adds nothing to the green count:
`AGREE` is `78218` in both runs.

### 4.2 One result per operation, not one per corpus — a defect found and fixed mid-stage

The first run with declarations in force adjudicated only M-12 (432 rows) and left the other four
unused, with 870 rows still `DIFFER`. The cause was the sweep test, not the mechanism: it called
`sweep.sweep(op, domains)` **once per corpus**, producing eight results per operation, each holding a
slice. A population declaration keys on an exact row count over the operation, so against eight
slices it matched none of them.

This is worth recording because of its shape: the pre-registration would have silently failed to fire
while the fixes were demonstrably in place, and the only symptom was a red gate that looked like a
porting error. `DifferentialSweep.tuplesOf` was extracted so the test can build one result per
operation, and the reason is written at the call site.

### 4.3 The four invisible corrections — `B7CorrectionsTest`, 26 tests

Every test **drives the fork as well as the port**, through `HistoricalOracle.toHistorical`. A test
that only asserted what the port does now would be evidence of the port's behaviour, not of a
*correction* — it would pass identically if the fork had always been right and the ledger row were
fiction.

Selected measurements, each an assertion in that file:

| Claim | Fork | Port |
|---|---|---|
| `UString('x',1.0).equals(itself)` | `false` | `true` |
| the same value found in a `HashSet` | **not found** | found |
| `UBoolean(true,0).equals(Boolean false)` | `false` | `true` |
| `UInteger(2,0.5).equals(UReal(2,0.5))`, both directions | `false` | `true` |
| `UReal(0.1+0.2, 0).equals(Real(0.3))` | `false` | `true` |
| `hashCode` of `UInteger(n,0)` for six distinct `n` | `[0,0,0,0,0,0]` | six distinct |
| `hashCode(UReal(-0.0,0))` vs `hashCode(UReal(0.0,0))` | differ, though `equals` says equal | equal |
| `UInteger(2,0).compareTo(UReal(3,0))`, both directions | `0` and `0` | `<0` and `>0` |
| `UString('x',1).compareTo('x')` | `"x".compareTo("UString('x', 1.0)")` | `0` |

The file also asserts the **declared residuals**, so they are measured facts in the suite rather than
sentences in a document that can go stale: `StringValue.equals` still has no `UStringValue` arm,
`RealValue.equals` still has no `URealValue` arm, and `UString`-against-`UString` comparison still
routes through `toString()`.

And it asserts the **bundle** obligations directly: bundle B (`M-10` + `F-3`) by checking the new
cross-type equal pair hashes alike, and bundle A by checking that `URealValue.compareTo` — the side
that gained the arm — is non-zero, since if it still fell through, M-9's negation would be a no-op.

### 4.4 The gate

`bash scripts/upstream-oracle-gate.sh both` → **PASS**.

| mode | classes | methods | executions | failures |
|---|---|---|---|---|
| default, `use-core` surefire | 28 (floor 15 → **re-pinned 28**) | 161 (floor 107 → **re-pinned 161**) | 161 | 0 |
| oracle, `use-core` surefire | 61 (floor 48 → **re-pinned 61**) | 432 (floor 378 → **re-pinned 432**) | 1020 | 0 |

The jump is 13 classes, not 2: surefire counts each `@Nested` class as its own report, and the two
new files carry 6 and 7 nested classes. Floors may grow and may never shrink.

**Waivers: still one** (W-01). No upstream test was edited.

### 4.5 Two golden reports changed, by two lines each

`docs/port2/differential/s1-smoke-ureal-add.tsv` and `s1-smoke-ureal-minus-faulty.tsv` each gained
`# rows.intendedDeparture 0` and `# op.<key>.intendedDeparture 0`. **No data row changed.** The
golden-file guard caught the header change on the first run, which is what it is for; the diff was
read before the goldens were updated.

---

## 5. What B7 still owes

| Rows | Status |
|---|---|
| M-11, M-8, M-9, M-10, M-12, M-18, F-3, F-4, F-10, bundle A | **done**, evidenced above |
| **F-2** (`MathUtil.round` saturates above 9.2e8) | not started. Must be a **separate commit** after the byte-identical `round` is shown green — `b7-fix-plan.md` §7.1 bundle D |
| **M-21, M-22** (type layer) | not started |
| **M-37, M-38** (`OpGeneric` registries) | not started |
| **M-29, M-30, M-32, M-33** (expression/parser layer) | not started |
| **M-26, M-27** (`ExpDefSBoolean`) | the class was ported although B10 decided "drop"; unreachable from any grammar rule. Needs a decision, not a fix |
| **M-6, M-28, M-31, M-43, M-48b, M-51** | "fix = do not change the code". Each still needs its written justification **at the site** |
| **CF-5, CF-7, CF-8, CF-9, M-44, M-45, M-49b** | the test-harness rows. None started |

**10 of 33 rows are discharged.** That is the honest count; the previous record said 1.

---

## 6. Reproduce every number in this file

```sh
cd /home/xoruser/msc-4/use-msc2026

# section 4.1
mvn -o -pl use-core test -Dtest=PortedFidelitySweepTest

# section 4.3
mvn -o -pl use-core test -Dtest=B7CorrectionsTest

# section 1.1 / 1.3
mvn -o -pl use-core test -Dtest=IntendedDeparturesTest

# section 4.4
bash scripts/upstream-oracle-gate.sh both

# section 2.3 -- the census admits no equals(Object)
grep -n 'types\[i\] == valueClass' -A 8 \
  use-core/src/test/java/org/tzi/use/uncertainty/differential/UnwrittenPortInvariantTest.java

# section 4.4 -- waiver count
grep -c '^# W-' docs/port2/upstream-test-waivers.md
```
