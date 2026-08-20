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
verdicts               {AGREE=78130, INTENDED_DEPARTURE=1390, BOTH_THREW=50598,
                        UNMEASURABLE=808, HARNESS_ERROR=1163146}
pre-registered (B7)    11 declaration(s), 1390 row(s) adjudicated
diverging operations   0 (unintended)
====================================================
```

**0 unintended divergence, 0 unused declarations, 1390 rows adjudicated** across **11 declarations
over 12 operations**. The adjudication is co-extensive with the corrections and adds nothing to the
green count: with the corrections in and the declarations removed, the same sweep reports the same
`AGREE` figure and the same rows as `DIFFER`.

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

### 4.3a F-2 — the row the plan said had no observable effect

`b7-fix-plan.md` §2 F-2 says the fix "has no observable effect on any existing evidence" because
"no test and no corpus entry reaches that magnitude". **Both halves are wrong**, and the sweep is
what showed it.

`InputGenerator` ships `NaN`, both infinities and ±`Double.MAX_VALUE` — all far above the 9.2e8
ceiling. Six printing operations moved, on 144 rows. The pairs are the argument for the fix:

| the fork printed | the port prints |
|---|---|
| `UReal(9.223372036854776E8, 0.0)` | `UReal(1.7976931348623157E308, 0.0)` |
| `UReal(9.223372036854776E8, 0.0)` | `UReal(Infinity, 0.0)` |
| `UReal(0.0, 0.0)` | `UReal(NaN, 0.0)` |
| `UBoolean(true, 0.0)` | `UBoolean(true, NaN)` |

Read the first two rows together: **the fork printed the same text for `Double.MAX_VALUE` and for
`Infinity`.** And it printed a `NaN` probability as `0.0`, which reads as *certainly false* — the
strongest claim the type can make — for a value carrying no probability at all.

#### The rounding mode was nearly a second, undeclared change

`Math.round` rounds a half toward **positive infinity**: `Math.round(-0.5) == 0`. `BigDecimal`'s
`HALF_UP` rounds a half **away from zero**: it would send `-0.5` to `-1.0`. A plain `HALF_UP` would
therefore have moved every negative half — a population the corpora do reach — inside a commit whose
stated subject is saturation.

It was caught by an assertion, not by review: `MathUtilRoundSaturationTest.Preserved.agreesBelowTheCeiling`
sweeps both bodies over 36 values at four scales and demands they agree everywhere below the ceiling.
The mode is now `HALF_UP` above zero and `HALF_DOWN` below it, which together are exactly
half-toward-positive-infinity.

#### The declared limit

`NaN` and the infinities have no `BigDecimal` representation and are returned unchanged. The fork's
body mapped `NaN` to `0.0` and `±Infinity` to `±9.223372036854776E8` — neither of which is a rounding
of anything. They are reachable from OCL: `UReal(1,0) / UReal(0,0)` produces an infinity that then
flows into `toString` and `equals`.

#### The mechanism caught its own declaration going stale

F-3's declaration was written as **72 rows over 9 distinct pairs**. When F-2 landed it stopped
matching, clause 4 fired, and the gate refused. The cause was not a regression: 56 of those 72 rows
had been departing only because the *old* `round` mangled large values, and with the ceiling gone
they **agree with the fork again**. What survives is 16 rows over 2 pairs — `Double.hashCode(-0.0)`
becoming `Double.hashCode(0.0)`, and a subnormal rounding to zero — both cases the fork's own
`equals` already called equal.

So F-3's correction is now exactly co-extensive with the contract violation it was written for, and
that was discovered by the pre-registration lapsing rather than by anyone reading the code. This is
rule 1 doing the job it exists for.

### 4.3b The type and dispatch rows, and a porting omission found beside them

`harness-contract.md` §C3 says outright that the differential harness cannot see the type layer, so
these four rows were always going to need purpose-built evidence. `B7TypeAndDispatchTest`, 12 tests.

| Row | The defect | Evidence |
|---|---|---|
| **M-21** | `UBooleanType`, `UStringType` and `SBooleanType` put `TypeFactory.mkX()` in `allSupertypes()` where `URealType` and `UIntegerType` put `this`. For a factory singleton those are the same object — but `TypeTest` constructs types **directly**, and for such an instance `this != mkX()`, so **a directly-constructed type was not among its own supertypes** and did not conform to itself. | all five constructed directly, each asserted to contain itself and to `conformsTo` itself |
| **M-22** | three constructor visibilities across five sibling types: `UIntegerType` public, `URealType` protected, the rest package-private | see below |
| **M-37** | `Op_uInteger_value.matches` declared `mkUInteger()` while `eval` returns an `IntegerValue`. `ExpStdOp.create` stores `matches`'s answer as the **static** type, so every enclosing expression was type-checked against the wrong one | driven through `ExpStdOp.create`, the production path, not by reflecting into the registry |
| **M-38** | `Op_uBoolean_or` dereferenced the `null` that `UBooleanValue.valueOf` returns for an `UndefinedValue`. The sibling `Op_uBoolean_and` guards it; only `or` did not | see below |

#### M-22's evidence is a compile error

The first draft of the test sat in `org.tzi.use.uncertainty` and **did not compile** — five errors,
one per uncertain type, all reading *"`URealType()` is not public … cannot be accessed from outside
package"*. The file was moved into `org.tzi.use.uml.ocl.type`, and that refusal is recorded in its
class comment: a reflective modifier check is asserted too, but the compiler's refusal is the
stronger statement and a reader of the reflective assertion would otherwise never know it had been
tested against a real call site.

#### M-38 needed a witness that does not exist in the corpus

`b7-fix-plan.md` §0.2 established that `Undefined or Undefined` never reaches `Op_uBoolean_or`:
`Op_boolean_or` is registered first (`OpGeneric.java:90` against `:94`), matches `(OclVoid, OclVoid)`
under `INCLUDE_VOID`, and `ExpStdOp.create` stops at the first match. So the four corpus entries that
write it pass today and would pass with the NPE still in place.

The witness is a pair of `ExpUndefined(TypeFactory.mkUBoolean())` — expressions carrying a declared
`UBoolean` type that evaluate to `Undefined`. The test asserts **first** that `or.type()` is
`UBoolean`, because if `Op_boolean_or` had matched instead, the witness would have missed the code
under test entirely and the assertion about the result would have been meaningless.

#### The porting omission

`VoidType` was missing all five `isKindOfU*` overrides, which the fork has
(`FORK/…/VoidType.java:38, 58, 123, 128, 133`). `OclVoid` therefore answered `true` for
`isKindOfInteger(INCLUDE_VOID)` and `false` for `isKindOfUInteger(INCLUDE_VOID)`, so `Undefined`
could be passed where an `Integer` was expected and was refused where a `UInteger` was — and every
`matches()` in `StandardOperationsU*.java` guards on exactly those predicates.

This is **not** a B7 correction. It moves the port *towards* the fork, so no `IntendedDepartures`
entry adjudicates it, and the sweep is unchanged by it.

### 4.4 The gate

`bash scripts/upstream-oracle-gate.sh both` → **PASS**.

| mode | classes | methods | executions | failures |
|---|---|---|---|---|
| default, `use-core` surefire | 36 (floor 15 → **re-pinned 36**) | 182 (floor 107 → **re-pinned 182**) | 182 | 0 |
| oracle, `use-core` surefire | 69 (floor 48 → **re-pinned 69**) | 453 (floor 378 → **re-pinned 453**) | 1041 | 0 |

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
| M-11, M-8, M-9, M-10, M-12, M-18, F-2, F-3, F-4, F-10, bundle A | **done**, evidenced above |
| M-21, M-22, M-37, M-38 | **done**, evidenced in §4.3b |
| **M-29, M-30, M-32, M-33** (expression/parser layer) | not started |
| **M-26, M-27** (`ExpDefSBoolean`) | the class was ported although B10 decided "drop"; unreachable from any grammar rule. Needs a decision, not a fix |
| **M-6, M-28, M-31, M-43, M-48b, M-51** | "fix = do not change the code". Each still needs its written justification **at the site** |
| **CF-5, CF-7, CF-8, CF-9, M-44, M-45, M-49b** | the test-harness rows. None started |

**15 of 33 rows are discharged.** That is the honest count; the previous record said 1.

---

## 6. Reproduce every number in this file

```sh
cd /home/xoruser/msc-4/use-msc2026

# section 4.1
mvn -o -pl use-core test -Dtest=PortedFidelitySweepTest

# section 4.3
mvn -o -pl use-core test -Dtest=B7CorrectionsTest

# section 4.3a
mvn -o -pl use-core test -Dtest=MathUtilRoundSaturationTest

# section 4.3b
mvn -o -pl use-core test -Dtest=B7TypeAndDispatchTest

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
