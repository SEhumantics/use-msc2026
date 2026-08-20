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

### 4.3c Dead code removed: `ExpDefSBoolean` and `ASTSBooleanDefExpression`

The user's follow-up instruction (2026-08-20): dead code, including semantics code with no
grammatical binding, is removed rather than fixed or kept. `ExpDefSBoolean` and its constructing AST
node `ASTSBooleanDefExpression` are exactly that population, and they are also M-26 and M-27's site.

**Confirmed unreachable, mechanically.** `grep` for `ASTSBooleanDefExpression` across
`use-core/src/main/resources/grammars/` returns nothing — no grammar production constructs it, ever.
The only other reference besides the class's own definition was `ExpressionVisitor` (the visitor
method) and its two implementers. No corpus entry, no shell test, no `.soil` script can reach this
code, because nothing in the parser can build the node that would call it.

**Removed:**

| File | |
|---|---|
| `use-core/src/main/java/org/tzi/use/parser/ocl/ASTSBooleanDefExpression.java` | deleted |
| `use-core/src/main/java/org/tzi/use/uml/ocl/expr/ExpDefSBoolean.java` | deleted |
| `ExpressionVisitor.visitDefSBoolean(ExpDefSBoolean)` | interface method removed |
| `ExpressionPrintVisitor.visitDefSBoolean` | implementation removed |
| `AbstractCoverageVisitor.visitDefSBoolean` | implementation removed |

**M-26 and M-27 are moot, not fixed and not deferred.** M-26 was a missing `ctx.exit` before the
return in `ExpDefSBoolean.eval`; M-27 was an inverted guard in the constructor. Both die with the
class. `b7-fix-plan.md` §2 already anticipated this outcome under B10 ("MOOT — do not port the
class"); what changed today is that the class had in fact been ported anyway, and B10's "drop" is now
executed rather than merely decided.

**Verified no other dead uncertainty code exists on the same surface.** Every grammar-bound AST
literal (`ASTURealLiteral`, `ASTUIntegerLiteral`, `ASTUBooleanLiteral`, `ASTUStringLiteral`,
`ASTSBooleanLiteral`) maps one-to-one to a constructing `Exp*` class
(`ExpConstUReal`/`ExpConstUInteger`/`ExpConstUBoolean`/`ExpConstUString`/`ExpConstSBoolean`); with
`ExpDefSBoolean` gone, that is now an exact five-to-five correspondence and nothing is left over on
either side.

`mvn -o -pl use-core compile` and `mvn -o -pl use-gui -am compile` both succeed with these removed;
`use-gui` was checked because `VoidType` (§4.3b) and `ExpressionVisitor` are both touched by this
stage and `use-gui` is out of scope by ground rule 5 unless a compile break forces otherwise — it
does not.

### 4.3d M-29, M-30, M-32, M-33 — the parser and literal-constant layer

`b7-fix-plan.md` §3 marks every one of these as reaching a compile error before the corrected line,
or as having no corpus example at all — a different way of saying nothing that already exists
exercises the code. `B7ParserAndConstantsTest`, 12 tests, driven end to end through
`OCLCompiler.compileExpression`, the same production path `UncertainExpressionTypingTest` uses.

| Row | The defect | Witness |
|---|---|---|
| **M-29** | `ExpConstUBoolean` checked only `probability.isUndefined()`. An undefined **value** operand's `toString()` is the literal text `"Undefined"`; `Boolean.valueOf("Undefined")` is `false` (no exception); `UBooleanValue.valueOf(false, p)` normalises that to `(true, 1-p)` — a defined result manufactured from an operand that was not there. | `UBoolean((let b : Boolean = Undefined in b), 0.8)` |
| **M-30** | `ExpConstUString.eval` had an unguarded `(StringValue)` cast and an unguarded `Double.valueOf(confidence.toString())`. A **statically** well-typed operand can still evaluate to `Undefined` at runtime, and both raised uncaught exceptions when it did. | `UString((let s : String = Undefined in s), 0.9)` |
| **M-32** | `ASTURealLiteral.gen` called `eValue.gen(ctx)` and `eUncertainty.gen(ctx)` each **twice** — once for the type check, again at construction — building two distinct graphs and installing the second. | `UReal((let x : Integer = 3 in x), (let y : Real = 0.5 in y))` |
| **M-33** | `ASTUStringLiteral` had no `toString()` override at all, unlike its four siblings; it fell through to `Object.toString()`, an identity hash. | direct construction with a stub operand, asserting the rendered text |

#### Finding the right witness took two tries, for reasons worth recording

The first draft used `Undefined.oclAsType(Boolean)` to get an undefined operand statically typed
`Boolean`. It does not compile: `oclAsType` refuses to widen `OclVoid` to a declared subtype, so
every M-29/M-30 test failed at the `compile()` step rather than reaching `eval`. The working idiom is
a `let`-declaration with an explicit type ascription — `(let b : Boolean = Undefined in b)` — which
gives exactly "undefined value, declared type." M-32's witness needed parentheses around each `let`
for an unrelated grammar-precedence reason: an unparenthesized `let ... in ...` is not a valid
function-call argument in this grammar.

#### A stale classpath produced a false negative worth recording

Debugging the M-30 witness against a manually assembled classpath first reported the **old**
`ClassCastException` even after the source fix had landed, because `~/.m2`'s installed `use-core`
artifact was earlier on the classpath than the freshly compiled `target/classes`. Reordering the
classpath — `target/classes` first — reproduced the fix immediately. Recorded because it is exactly
the failure shape this whole project keeps finding: a signal that looks like a result but is actually
measuring the wrong artifact.

### 4.3e M-6, M-28, M-31 — three rows whose fix is a written reason, at the site

B7 says "fix the historical defects, documenting each", and for these three the documented finding
is that the ledger's proposed change is *worse* than what stands. That is not evasion of the
decision — it is the decision, applied. Each now carries a full javadoc at its own site rather than
only a line in this document, so a reader who arrives at the code (not this file) sees the reasoning.

| Row | Site(s) | What was considered and rejected |
|---|---|---|
| **M-6** | `URealValue.assertKindOfUReal`, `UIntegerValue.assertKindOfUInteger`, `UStringValue.assertKindOfUString`, `SBooleanValue.assertKindOfSBoolean` | Narrowing the bare `RuntimeException` to `IllegalArgumentException`. Rejected: two catch sites downstream (`ExpConstSBoolean.java:57`, `ASTSBooleanLiteral.java:35`) swallow `Exception` broadly, and the full downstream `catch` set could not be enumerated — narrowing is risk with no offsetting benefit |
| **M-28** | `ExpConstUBoolean.eval` | Replacing the `String`-round-trip (`Boolean.valueOf(value.toString())`, `Double.valueOf(probability.toString())`) with direct accessors. Rejected: the round-trip is two behaviours in one expression — it also produces the `NumberFormatException` that the surrounding `catch` converts to `Undefined`, and a direct accessor has no string to fail to parse |
| **M-31** | `ExpConstUReal`'s constructor | Moving `ASTURealLiteral`'s type check into the constructor, for consistency with its four siblings. Rejected: this constructor is called directly, with unvalidated arguments, at 300+ sites in the ported test suite; making the check a constructor-time failure breaks every one of them and is a compile-shape change none of the siblings' call sites make |

No test count moves — these are documentation-only changes at sites the existing suite already
exercises. Confirmed: the gate reports the same 40/194 (default) and 73/465 (oracle) as the previous
commit.

**M-43, M-48b and M-51 are not actionable yet.** All three apply to fork test files —
`UBooleanValueTest.java` and `USECompilerUncertaintyTest.java` — that have not been ported into this
repository (`find use-core/src/test -iname 'UBooleanValueTest.java' -o -iname
'USECompilerUncertaintyTest.java'` finds neither). Porting them *is* the remaining test-harness work
(CF-8 and neighbours); these three rows apply to that porting once it happens, not before.

### 4.3f `uEquals` — a question closed, not a fix

An earlier session left a standing worry: commit {@code d99ab9ef} routed {@code =}/{@code <>} on
uncertain operands to {@code uEquals}, and noted that {@code uEquals} was **not** in the differential
sweep at the time, so {@code UReal(2,0.5) = 2} giving probability {@code 0.0} "looks wrong to me and
may be a genuine fork defect; it cannot be judged until uEquals is swept." `UEqualsCoverageTest`, 6
tests, closes it.

**Half one: is `uEquals` actually swept?** Yes, and there was no separate step to add it. It is a
public, single-`Value`-argument instance method on all five uncertain value classes, in both the
historical jar and the port, so `UnwrittenPortInvariantTest.reachableOperations` finds it by
reflection — it has been one of the 355 operations `PortedFidelitySweepTest` drives since that test
was written (section 4.1). That sweep reports `AGREE` on every measured `uEquals` row.

**Half two: is the `0.0` correct?** Yes, and it is not a coincidence. `uDataTypes.UReal.calculate`
(the vendored library, `UReal.java:501-508`) branches on which side of a comparison is degenerate
(uncertainty exactly `0`), and whenever exactly one side is, it sets `eq = 0.0`
**unconditionally** — before ever looking at how close the two means are. That is the correct answer
for a continuous distribution compared to an exact point: a continuous random variable takes any
single value with probability zero, however close that value sits to the mean.
`UReal(2,0.5) = 2` is asking "what is the probability this Gaussian equals exactly 2", and the honest
answer is zero for any mean. It is not the constant `0.0` in general — two genuinely uncertain
operands (`UReal(2,0.5) = UReal(2,0.5)`) give `UBoolean(true, 1.0)`, and the Gaussian-overlap branch
of the same library method is what computes that.

Every test in the file drives the historical jar as well as the port, so it would fail if either half
of this answer stopped being true.

### 4.3g Two regressions found while porting the historical corpus

Porting the fork's own test-harness files (§4.3i) meant running 1427 corpus entries against the port
for the first time. Two of them exposed real regressions in `StandardOperationsNumber`, not corpus
problems: `Op_number_toString` and `Op_number_unaryplus` both wrongly refused an uncertain operand,
because an earlier session (S8) applied `UncertainOperand.present`'s guard to all four of
`unaryplus`/`pow`/`sqrt`/`toString`, when only `pow` and `sqrt` genuinely need it — the fork provides
dedicated `UReal`/`UInteger` replacement operations for those two only. `toString`'s `matches()` in the
fork carries no such guard, and `unaryplus` has no dedicated uncertain replacement at all. Fixed by
removing the guard from both, with javadoc at each site citing the exact fork line numbers that show
the asymmetry. Committed separately (`e37abb2b`) from the corpus port that found them, per the ground
rule that behaviour changes and modernization land in separate commits.

### 4.3h `uSelect`/`uSelectC`, uncertainty-aware collection membership, and a quantifier bug

Two features the port was missing entirely, both ported from the fork this stage:

- **`uSelect`/`uSelectC`** (`ExpUSelect`, `ExpUSelectC`, ported verbatim): `collection->uSelect(e | body)`
  keeps elements whose body evaluates to a crisp `true` or a `UBoolean` with probability ≥ 0.5;
  `uSelectC` takes an explicit confidence threshold instead of the 0.5 default. The grammar
  (`OCLBase.gpart`), `ParserHelper`, and `ASTQueryExpression` all needed a generic optional trailing
  `, confidence` clause on `queryExpression`, matching the fork's own grammar shape rather than a
  special case for just these two operations.
- **Uncertainty-aware collection membership** (`CollectionValue.uIncludes`/`uIncludesAll`/`uExcludes`/
  `uExcludesAll`, ported verbatim): `StandardOperationsCollection`'s `includes`/`excludes`/
  `includesAll`/`excludesAll` now branch `mkBoolean()` vs `mkUBoolean()` by element/argument
  uncertainty. One fork dead-code oddity was deliberately preserved byte-for-byte in `includes`'s
  `matches()` — `params[1] instanceof UncertainValue`, where `params[1]` is actually `Type[]`, so the
  check is always `false` since `Type` and `Value` are unrelated hierarchies. Not a B7 row, not
  silently fixed: left as-is, flagged in a code comment for a future finding.

Making `ExpForAll`/`ExpExists` accept a `UBoolean`-valued body (needed so `uSelect`'s body type-checks
against the same machinery) forced replacing `evalExistsOrForAll`/`evalExistsOrForAll0` — crisp-only,
with a short-circuiting `break` — with ported `evalExists0`/`evalForAll0` using `ExpStdOp.create`
dispatch. Building the regression tests for that replacement surfaced a genuine, **pre-existing** defect
in the fork's own `evalForAll0`/`evalExists0`: at nested recursion (2 or more element variables),
`res = evalForAll0(nesting+1, ...)` **overwrites** the outer accumulator instead of combining with it,
so only the last outer iteration's inner result survives. Universal/existential quantification over 2+
variables is silently wrong whenever the outer variable ranges over more than one element.

Confirmed byte-for-byte present in the fork's own shipped source before deciding to fix rather than
reproduce it — this is core crisp OCL semantics, not uncertainty-specific — and it broke two of
`use-gui`'s own inherited `ShellIT` fixtures, both passing on stock USE 7.5.0:

- `t049` (`Person::nameUnique`, a 2-variable invariant)
- `t022` (2-variable `exists` in `transitiveClosure`)

Per ground rule 3's spirit ("if an upstream test fails after a change, the change is wrong"), that rule
binds this port's own new code, not license to edit the test: `evalForAll0`/`evalExists0` now
AND/OR-combine the recursive result with the outer accumulator via `ExpStdOp.create` dispatch at every
nesting level, not just the leaf. No `use-gui` source file was touched to fix this — only
`use-core/ExpQuery.java` — so ground rule 5 is unaffected; this was a test failure, not a compile break.

```
$ mvn -o -pl use-gui verify -Dit.test=ShellIT
Tests run: 129, Failures: 0, Errors: 0, Skipped: 0
```

`UncertainQueryAndMembershipTest` (16+ tests, nested classes `USelect`/`USelectC`/`ForAllExists`/
`Membership`/`MultiVariableAccumulation`) covers both features; the last nested class regression-tests
the accumulation fix specifically, with `forAllAccumulatesAcrossOuterIterations`,
`existsAccumulatesAcrossOuterIterations`, and `twoVariableInvariantCatchesDuplicate` (mirroring `t049`).
Committed as `7da9de10`.

### 4.3i CF-8 and neighbours — the historical corpus test harness, ported

`USECompilerUncertaintyTest` (`org.tzi.use.parser.uncertainty`) replays all 1427 entries of the fork's
`UBooleanExpression.in`/`UCollectionOperations.in`/`UIntegerExpression.in`/`URealExpression.in` corpus
through the port's own OCL compiler and evaluator. The four `.in` files were copied byte-identically
from the fork (sha256-verified against `.git/reference-repositories/uncertainty/USE-Uncertainty`),
then adjusted only where W-01–W-04 (below) required it.

Porting the JUnit-3 `TestCase` to JUnit 5 closed six ledger rows in one file:

| Row | What changed | Evidence |
|---|---|---|
| **CF-8** | `File.listFiles` on a `user.dir`-relative path (fails or passes vacuously under Maven) → a fixed, sorted array of the four corpus filenames resolved by `Class.getResourceAsStream`. A literal directory listing via `Class.getResource(".")` was tried first and confirmed to return `null` under this project's JPMS module (`use.core`, patched by Surefire via `--patch-module`) — the fixed-array approach sidesteps that rather than fighting it, since the corpus is a known, unchanging set of four files, not something that grows between runs | test passes, reports exactly 1427 entries executed |
| **CF-9** | platform-default charset on the fixture read and on `expression.getBytes()` → `StandardCharsets.UTF_8` explicitly on both | source at `USECompilerUncertaintyTest.java` |
| **M-45** | `Options.explicitVariableDeclarations = false` set once, never restored | saved in `@BeforeEach`, restored in `@AfterEach` |
| **M-48b** | `ExpressionTest` was a non-static inner class with `Object.toString()` (an identity hash in failure messages) | `private static final class` with an explicit `toString()` returning the expression text |
| **M-49b** | `split("\n(\r\n)")` is not a line-separator alternation (parentheses are a capturing group, `\|` is missing) — it never matches, so the 5 error-path entries were adjudicated against the *entire* captured stderr, not the intended line | `split("\r?\n")` plus a `lastNonBlank` helper; all 5 error-path entries (`UBooleanExpression.in:8,11,14`, `URealExpression.in:62,65`) still pass under the corrected split, confirmed by the full 1427/1427 run below |
| **M-51** | `catch (IOException ex) { throw new RuntimeException(msg); }` dropped the cause | `new RuntimeException(msg, ex)` |

Two more rows close as a side effect of the JUnit 5 port itself, not a targeted fix:

- **CF-7** (2 of its 12 sites): the fork's `TestCase.assertEquals(message, expected, actual)` vs
  JUnit 5's `assertEquals(expected, actual, message)` — all-`String` arguments meant the fork's two
  calls at `:90,94` silently rebound under a naive migration. Writing the port's `assertEquals` calls
  with the JUnit 5 argument order from the start avoids the hazard entirely; there is nothing to
  regress. The other 10 CF-7 sites (`UIntegerExpOpsTest.java`) apply to a fork test file not yet
  ported, so CF-7 is only partially discharged.
- **CF-5**: the fork's three JUnit-3 `AllTests` suites, which pinned execution order to keep the
  `Options.explicitVariableDeclarations` leak survivable, were never ported — this port relies on
  Surefire's own classpath discovery, so there is no suite class to delete and no order dependency to
  remove. Combined with M-45's fix landing in the same commit (as the ledger itself recommends: *"Do
  not pin surefire order... fixing M-45 removes the leak"*), CF-5's underlying hazard does not exist in
  this port.

**M-43 and M-44 remain open as of this commit.** Both apply to fork test files not ported yet —
`UBooleanValueTest` (M-43, two commented-out `try/fail/catch` blocks the ledger recommended reviving as
`@Disabled`) and four files including `ExpQueryUncertaintyTest` (M-44, 40 JUnit-3 `try/fail/catch`
idiom sites). Neither blocked anything the corpus exercises, so both were left for their own commits.
**M-43 is closed two commits later, §4.3j — and not the way the ledger predicted.** M-44 remains open;
see §5.

W-02, W-03 and W-04 (`docs/port2/upstream-test-waivers.md`) record the three categories of fixture
correction the first full run found — 79 entries expecting `Undefined : OclVoid` where the port
correctly gives `null : OclVoid` (W-02, a pre-existing finding from earlier this stage, re-confirmed
against this freshly-ported corpus); 8 entries expecting a `UBoolean` probability with more decimal
digits than any implementation of `UBooleanValue.toString` ever prints, confirmed against each
expression's raw, unrounded `probability()` (W-03); and 5 entries writing `.equals(...)` directly on a
`Collection`-typed `iterate` result where OCL requires `->equals(...)` (W-04). After all three waivers,
the corpus is 1427/1427:

```
$ mvn -o -pl use-core test -Dtest=USECompilerUncertaintyTest
Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
```

### 4.3j B7 M-43 — `UBooleanValueTest`, and a ledger prediction the real historical jar refutes

The fork's `UBooleanValueTest.java` (`org.tzi.use.uml.ocl.value`) left two assertions commented out,
each guarded by `// FIXME: When It will be fixed in atenea library`:

```java
try {
    uBoolean = UBooleanValue.valueOf(true, -2);
    fail("Exception expected\n");
}
catch (Exception ex) { }
```

`b7-fix-plan.md`'s own recommendation for M-43 was to revive both as `@Disabled` Jupiter tests,
reasoning — from the FIXME text alone, not from running anything — that the vendored library's
constructor clamped out-of-range probabilities rather than throwing, so a live revival would fail and
turn the suite red.

**That reasoning does not hold, checked directly rather than assumed.** Probed against this port's own
`UBooleanValue.valueOf(true, -2)`/`(true, 2)`: both throw `IllegalArgumentException`. More importantly,
probed against the **real historical jar**, via `HistoricalOracle` reflection — invoked, not read from
source:

```
HISTORICAL valueOf(true,-2) THREW java.lang.IllegalArgumentException: Invalid parameters
HISTORICAL valueOf(true,2) THREW java.lang.IllegalArgumentException: Invalid parameters
```

The vendored `org.tzi.use.uncertainty.datatypes.UBoolean(boolean, double)` constructor validates
`c < 0 || c > 1` and throws before `UBooleanValue`'s own constructor guard ever runs — confirmed
byte-identical to the fork's own vendored source
(`.git/reference-repositories/uncertainty/uDataTypes/Libraries/Java/src/uDataTypes/UBoolean.java:35-36`),
unchanged by this port's vendoring (B1). Whatever prompted the fork author's FIXME either did not apply
to this code path or was already fixed by the `74acd0d` snapshot this port is built from. The two
blocks are therefore revived **live** — `valueOfRejectsProbabilityBelowZero()`,
`valueOfRejectsProbabilityAboveOne()` — not `@Disabled`. The remaining three test methods (`values`,
`isTypeOf`, `testEquals`) port unchanged; none of their assertions compare a `UBooleanValue` against a
`BooleanValue`, so none touch M-8's `equals()` fix, and nothing in the file was expected to move.

```
$ mvn -o -pl use-core test -Dtest=UBooleanValueTest
Tests run: 5, Failures: 0, Errors: 0, Skipped: 0
```

### 4.4 The gate

`bash scripts/upstream-oracle-gate.sh both` → **PASS**. Numbers below are the state at the end of this
stage, after four re-pins: the B7 corrections phase (§3–4.3f), the `uSelect`/collection-membership
commit (§4.3h), the CF-8 corpus-port commit (§4.3i), and the M-43 commit (§4.3j).

| mode | classes | methods | executions | failures |
|---|---|---|---|---|
| default, `use-core` surefire | 48 (floor 15 → **re-pinned 48**) | 222 (floor 107 → **re-pinned 222**) | 222 | 0 |
| oracle, `use-core` surefire | 81 (floor 48 → **re-pinned 81**) | 493 (floor 378 → **re-pinned 493**) | 1081 | 0 |
| default/oracle, `use-gui` | unchanged: 1/1 surefire, 1/129 failsafe | — | — | 0 |

The four re-pins, in order: **+13 classes / +54 methods** for the B7 pre-registration mechanism and
its two correction test files (`IntendedDeparturesTest`, `B7CorrectionsTest` — surefire counts each
`@Nested` class as its own report, and the two files carry 6 and 7 nested classes between them), then
**+5 classes / +16 methods** for `UncertainQueryAndMembershipTest`'s 5 `@Nested` classes, then
**+1 class / +1 method** for `USECompilerUncertaintyTest`, then **+1 class / +5 methods** for
`UBooleanValueTest`. `use-gui` is untouched by all four — no `use-gui` source file changed this stage,
and the `ExpQuery` accumulation fix (§4.3h) corrected two existing `ShellIT` fixtures rather than
adding new ones. Floors may grow and may never shrink.

**Waivers: four** (W-01–W-04). No `.java` test file was edited to make ported code pass — every waiver
this stage (W-02, W-03, W-04) alters only the ported `.in` fixture *data* the tests read, never the
test's own assertions.

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
| M-26, M-27 | **moot**: `ExpDefSBoolean` deleted as dead code, §4.3c |
| M-29, M-30, M-32, M-33 | **done**, evidenced in §4.3d |
| M-6, M-28, M-31 | **done**, evidenced in §4.3e |
| CF-8, CF-9, M-45, M-48b, M-49b, M-51 | **done**, evidenced in §4.3i |
| CF-5 | **discharged structurally** — the fork's order-pinning `AllTests` suites were never ported (this port relies on Surefire's own classpath discovery), and M-45 lands in the same stage as the ledger itself recommends. §4.3i |
| CF-7 | **partially discharged** — the 2 sites inside the ported `USECompilerUncertaintyTest` are correct by construction (JUnit 5 argument order from the start); the other 10 sites (`UIntegerExpOpsTest.java`) apply to a file not yet ported. §4.3i |
| M-43 | **done**, evidenced in §4.3j — and not the way the ledger predicted (revived live, not `@Disabled`; the real historical jar throws too) |
| **M-44** | applies to four fork test files not yet ported (`URealExpOpsTest.java`/`UIntegerExpOpsTest.java`/`UBooleanExpOpsTest.java`/`ExpQueryUncertaintyTest.java`, ~8,700 lines combined). Judged out of scope for this stage; the largest remaining unit of B7/CF work by a wide margin |
| **`uCount`/`uCountC`, metamorphic tests M-1..M-6** | outside the 33-row ledger; separate open items from the adversarial audit. `uSelect`/`uSelectC` and uncertainty-aware collection membership, previously in this category, are now done — §4.3h |

**31 of 33 rows are discharged, one more (CF-7) partially so** — counted directly by row name, not by
group arithmetic: M-8, M-9, M-10, M-11, M-12, M-18, F-2, F-3, F-4, F-10 (10, value layer, prior
sessions) + M-21, M-22, M-37, M-38 (4, type/dispatch layer, section 4.3b) + M-26, M-27 (2, moot by the
deletion of `ExpDefSBoolean`, section 4.3c) + M-29, M-30, M-32, M-33 (4, parser/literal-constant
layer, section 4.3d) + M-6, M-28, M-31 (3, written justification at the site, section 4.3e) + CF-8,
CF-9, M-45, M-48b, M-49b, M-51 (6, the historical corpus's own test harness, section 4.3i) + CF-5 (1,
discharged structurally, section 4.3i) + M-43 (1, section 4.3j) = 31. CF-7 is half discharged (2 of
its 12 sites; the other 10 await a file not yet ported). **Only M-44 has had no work done on it at
all** — it applies exclusively to four fork test files this stage did not port. The previous record
(before this session) said 1; it said 24 at the start of this commit sequence.

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

# section 4.3c -- confirm the two files are gone and nothing references them
git log --oneline -1 -- use-core/src/main/java/org/tzi/use/parser/ocl/ASTSBooleanDefExpression.java
grep -rn 'ExpDefSBoolean\|ASTSBooleanDefExpression' use-core/src/main use-gui/src/main 2>/dev/null || echo "none found"

# section 4.3d
mvn -o -pl use-core test -Dtest=B7ParserAndConstantsTest

# section 4.3e -- the javadoc is at the site; grep confirms it is there
grep -c 'ledger M-6\|ledger M-28\|ledger M-31' \
  use-core/src/main/java/org/tzi/use/uml/ocl/value/URealValue.java \
  use-core/src/main/java/org/tzi/use/uml/ocl/value/UIntegerValue.java \
  use-core/src/main/java/org/tzi/use/uml/ocl/value/UStringValue.java \
  use-core/src/main/java/org/tzi/use/uml/ocl/value/SBooleanValue.java \
  use-core/src/main/java/org/tzi/use/uml/ocl/expr/ExpConstUBoolean.java \
  use-core/src/main/java/org/tzi/use/uml/ocl/expr/ExpConstUReal.java

# section 4.3f
mvn -o -pl use-core test -Dtest=UEqualsCoverageTest

# section 4.3g -- the two StandardOperationsNumber regressions and their fix, at the site
grep -n 'Op_number_toString\|Op_number_unaryplus' -A 3 \
  use-core/src/main/java/org/tzi/use/uml/ocl/expr/operations/StandardOperationsNumber.java | head -40
git show --stat e37abb2b

# section 4.3h -- uSelect/uSelectC, uncertain collection membership, and the accumulation fix
mvn -o -pl use-core test -Dtest=UncertainQueryAndMembershipTest
mvn -o -pl use-gui verify -Dit.test=ShellIT -DfailIfNoTests=false

# section 4.3i -- the historical corpus test harness, and the three waivers it required
mvn -o -pl use-core test -Dtest=USECompilerUncertaintyTest

# section 4.3j -- M-43, and the two out-of-range-probability tests revived live
mvn -o -pl use-core test -Dtest=UBooleanValueTest

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
