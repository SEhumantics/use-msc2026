# S9 — B7: the pre-registration mechanism, and the ledger it closes at 33/33

**Role: Record.** Written 2026-08-20 on branch `port-uncertainty-2`. Every number below comes from a
named command whose output is quoted verbatim or reproducible via §6.

---

## 0. What this stage did, in one paragraph

The user's decision **B7** (2026-08-17, binding) is that the port **fixes** the fork's defects rather
than reproducing them bug-for-bug. At the start of this stage that decision was recorded and not
executed: 1 of the 33 behaviour-changing ledger rows had landed, and the ported file headers still read
*"Semantics unchanged"* — documenting the opposite of what the user asked for. This stage built the
mechanism B7 requires (§1), applied it row by row across sixteen commits (§3–§4), ported the fork's own
historical test corpus and its four largest test files, found and fixed two real regressions and one
genuine pre-existing fork defect along the way (a multi-variable `forAll`/`exists` accumulation bug,
§4.3h), implemented the two features the port was missing entirely (`uSelect`/`uSelectC` and
uncertainty-aware collection membership, §4.3h), reached **all 33 ledger rows discharged** — evidenced,
in every case, by an independently fork-authored test passing against this port's own computation, not
merely the port agreeing with itself — and closed the two items left open outside the ledger's own
scope: `uCount`/`uCountC` (§4.3l) and six metamorphic relations M-1..M-6 (§4.3m).

---

## 1. The mechanism, and why it had to come first

`DifferentialSweep` measures **difference**. It has no access to **intent**: given a red row it cannot
tell "we corrected a genuine fork bug" from "the port dropped a conjunct" — both are one operation, two
values, not equal. So under B7 every fix makes the instrument redder, and a divergence that is
*discovered* is indistinguishable from a porting error (`b7-fix-plan.md` §0.1/§4.3 designs the answer:
pre-registration).

**What was built**, all under `use-core/src/test/java/org/tzi/use/uncertainty/differential/`:
`IntendedDepartures.java` (the pre-registration list — three declaration forms, each with a written
rationale, a ledger row, a predicted direction); `DiffVerdict.INTENDED_DEPARTURE`
(`isAgreement()==false`, `isMeasurement()==true` — **not** an agreement, so `agreementCount()` never
moves; **is** a measurement, evidence of a difference predicted in writing before the run; excluded from
gate clause 2 only, clauses 1 and 3 untouched); `DifferentialSweep` (threads the list through
`classify`, adds gate clause 4, exposes `tuplesOf`); `DiffReportWriter` (`# rows.intendedDeparture` and
`# intendedDeparture.<row>`, including when zero); `IntendedDeparturesTest` (28 adversarial tests).

**Clause 4 is the half easy to leave out.** A list that only *permits* differences lets an unfixed
defect through unnoticed (declare the departure, forget to write the fix, the sweep stays green because
the port still agrees with the defective reference) — so the gate also **requires** them:
`Result.unusedDeclarations()` is non-empty exactly when a declaration never fired, and that is a
stage-gate failure with the same standing as a residual `DIFFER`. `IntendedDeparturesTest.CannotLaunder
.clause4CatchesTheUnfixedDefect` builds that scenario deliberately (fork against fork, every row
`AGREE`, clauses 1–3 pass, gate still refuses).

`isStagePass(int, AcceptedDegenerateOperations)` **throws** if the sweep ran with a non-empty
pre-registration — the existing 64 call sites (all `none()`) are unaffected, but a B7 call site cannot
reach a pass without naming the mechanism that let it. The three-argument form additionally checks the
list it is handed is the list the sweep ran under, by declaration identity.

---

## 2. Three deviations from the plan, and why

`b7-fix-plan.md` §4.3 specified a SHA-256-digest declaration form (`declareBounded`, capped at 64 rows),
on the theory that a digest preserves the lapse property at one entry instead of many. **Measured, that
does not fit the data**: the five departing populations are 72, 112, 343, 343 and 432 rows — every one
above the cap — over 2, 9, 2, 2 and 210 distinct *pairs*. The pair count, not the row count, is the
number a human can read, so `declareBounded` became **`declarePopulation(..., List<String>
distinctPairs, ...)`**: pairs written out in full, the cap on them instead, digest gone. Strictly
stronger — it fires only on exact set equality — and reviewable, which a digest is not.

One correction (M-12, `UStringValue.compareTo`) fits neither form: `String.compareTo` returns a
character difference, moving 432 rows onto **210** distinct pairs. Raising the cap to 210 would make the
class the blanket exemption it exists to prevent, so a third, deliberately weaker form,
**`declareWideCodomain`**, pins two counts (rows and distinct pairs, both equalities) plus a sample a
reviewer has read; it refuses to be used where the population would fit `declarePopulation`, and its
Javadoc says outright it is the weakest of the three.

**`b7-fix-plan.md` §4.1 also mis-scoped which rows the sweep can see.** It listed nine rows as sweep-visible
including F-4, M-10, M-8, M-11 (all `equals` overrides). They are not visible: `UnwrittenPortInvariantTest
.kindsOf` admits a method only when every parameter is `Value`/`int`/`double`/`float`, and `equals(Object)`
takes an `Object` — not one `equals` override is in the 355-operation census, confirmed empirically (the
sweep reports exactly five diverging operations, no `equals` among them). F-3, F-10, M-9 and M-12 *are*
sweep-visible (72/112/343+343/432 rows respectively); F-4, M-10, M-8, M-11 and F-2 (`MathUtil.round`,
reached "through every equals" per the plan) are not, and their evidence is `B7CorrectionsTest`, §4.3.

---

## 3. The eight corrections applied

Each carries its justification in the source, at the site, as B7 requires — not in this document only.

| Row | Site | The defect | Δoutput |
|---|---|---|---|
| **M-11** | `UStringValue.equals` | `String.equals(UString)` is false for every argument, ANDed with the receiver's confidence compared **to itself**. The method was the constant `false`; `a.equals(a)` was `false`. | `SET` + `VALUE` |
| **F-10** | `UIntegerValue.hashCode` | `hash *= 7 * Double.hashCode(uncertainty())`, and `Double.hashCode(0.0)==0`, so **every** `UInteger(n,0)` hashed to `0`. | hash only |
| **M-9** | `UIntegerValue.compareTo` | delegates to the other operand **without negating**. | `SET` (order) |
| **bundle A** | `URealValue.compareTo` | the fourth arm was an unreachable duplicate of the first, whose *body* built a `UIntegerValue` — so the `UIntegerValue` case had no implementation, and M-9's negation would have negated a constant `0`. | `SET` (order) |
| **F-3** | `URealValue.hashCode` | hashed **unrounded** what `equals` compares **rounded to 10 dp**. | `SET` (membership) |
| **F-4** | `URealValue.equals` | cross-type arms used raw `==`, three lines below an arm that rounds. | `VALUE` (widening) |
| **M-10** | `URealValue.equals` | no `UIntegerValue` arm, and `UIntegerValue.equals` **delegates here**, so cross-type equality was `false` both directions. | `VALUE` + `SET` |
| **M-8** | `UBooleanValue.equals` | `&& !this.value()` is dead — `valueOf` normalises every value to `true`. | `VALUE` |
| **M-12** | `UStringValue.compareTo` | compared the receiver's **bare string** against the argument's **wrapper rendering** `UString('x', 1.0)`. | `SET` (order) |
| **M-18** | `SBooleanValue.compareTo` | the whole body was `return 0;`. | `SET` (order) + `ERR` |

### 3.1 M-18 does not delegate, and that is the point

The obvious fix — hand the job to `uDataTypes.SBoolean.compareTo`, as every sibling does — trades one
defect for a crash. That comparator returns `0` whenever the L1 distance of the four opinion masses is
below `0.001`, and a tolerance-based "equal" is **not transitive**.
`B7CorrectionsTest.M18.intransitivityWitness` exhibits a concrete triple: `a ~ b`, `b ~ c`, `a < c`.

A first draft asserted the consequence — sorting 40 such opinions throws `IllegalArgumentException:
Comparison method violates its general contract`. It does not, reliably: TimSort only checks the
contract when a merge happens to expose the inconsistency, so whether it throws depends on the
permutation. That test was deleted rather than tuned green, because a crash that may not happen is a
test that passes for the wrong reason on the day it passes. The intransitivity itself is deterministic,
so that is what is asserted instead — and it is why M-18's fix is a **hand-written, corrected**
comparator rather than a delegation.

---

## 4. Evidence

### 4.1 The five sweep-visible corrections — pre-registered, and every declaration fired

`mvn -o -pl use-core test -Dtest=PortedFidelitySweepTest`:

```
operations enumerated  355   supported by the port  355 (100%)
rows                   1294072 total, 79520 measured
verdicts               {AGREE=78130, INTENDED_DEPARTURE=1390, BOTH_THREW=50598,
                        UNMEASURABLE=808, HARNESS_ERROR=1163146}
pre-registered (B7)    11 declaration(s), 1390 row(s) adjudicated
diverging operations   0 (unintended)
```

**0 unintended divergence, 0 unused declarations, 1390 rows adjudicated** across 11 declarations over 12
operations. The adjudication adds nothing to the green count: with the corrections in and the
declarations removed, the same rows report as `DIFFER`.

### 4.2 One result per operation, not one per corpus

The first run with declarations in force adjudicated only M-12 and left the other four unused (870 rows
still `DIFFER`) — not a mechanism failure but a test-shape bug: `sweep.sweep(op, domains)` was called
once per corpus, producing eight slices per operation, and a population declaration keys on the exact
row count over the *whole* operation, so it matched none of the slices. `DifferentialSweep.tuplesOf` was
extracted so the test builds one result per operation. Worth recording for its shape: pre-registration
would have silently failed to fire while the fixes were demonstrably in place, and the only symptom was
a red gate indistinguishable from a porting error.

### 4.3 The four invisible corrections — `B7CorrectionsTest`, 26 tests

Every test **drives the fork as well as the port**, through `HistoricalOracle.toHistorical` — a test
that only asserted what the port does would be evidence of the port's behaviour, not of a *correction*.

| Claim | Fork | Port |
|---|---|---|
| `UString('x',1.0).equals(itself)` | `false` | `true` |
| the same value found in a `HashSet` | not found | found |
| `UBoolean(true,0).equals(Boolean false)` | `false` | `true` |
| `UInteger(2,0.5).equals(UReal(2,0.5))`, both directions | `false` | `true` |
| `UReal(0.1+0.2, 0).equals(Real(0.3))` | `false` | `true` |
| `hashCode` of `UInteger(n,0)` for six distinct `n` | `[0,0,0,0,0,0]` | six distinct |
| `hashCode(UReal(-0.0,0))` vs `hashCode(UReal(0.0,0))` | differ, though `equals` says equal | equal |
| `UInteger(2,0).compareTo(UReal(3,0))`, both directions | `0` and `0` | `<0` and `>0` |
| `UString('x',1).compareTo('x')` | `"x".compareTo("UString('x', 1.0)")` | `0` |

The file also asserts the **declared residuals** as measured facts, not prose that can go stale:
`StringValue.equals` still has no `UStringValue` arm, `RealValue.equals` still has no `URealValue` arm,
`UString`-against-`UString` still routes through `toString()`. It also asserts the **bundle**
obligations directly (bundle B, `M-10`+`F-3`: the new cross-type equal pair hashes alike; bundle A: the
gained `URealValue.compareTo` arm is non-zero, since a fall-through would make M-9's negation a no-op).

**F-2 — the row the plan said had no observable effect. Both halves of that claim were wrong.**
`InputGenerator` ships `NaN`, both infinities and `±Double.MAX_VALUE`, all above `MathUtil.round`'s old
9.2e8 saturation ceiling; six printing operations moved on 144 rows:

| the fork printed | the port prints |
|---|---|
| `UReal(9.223372036854776E8, 0.0)` | `UReal(1.7976931348623157E308, 0.0)` |
| `UReal(9.223372036854776E8, 0.0)` | `UReal(Infinity, 0.0)` |
| `UReal(0.0, 0.0)` | `UReal(NaN, 0.0)` |
| `UBoolean(true, 0.0)` | `UBoolean(true, NaN)` |

The fork printed the same text for `Double.MAX_VALUE` and for `Infinity`, and printed a `NaN`
probability as `0.0` — the strongest claim the type can make — for a value carrying no probability at
all.

**The rounding mode was nearly a second, undeclared change, and this is genuinely load-bearing.**
`Math.round` rounds a half toward **positive infinity** (`Math.round(-0.5)==0`); `BigDecimal`'s
`HALF_UP` rounds a half **away from zero** and would have sent `-0.5` to `-1.0` — moving every negative
half, a population the corpora do reach, inside a commit whose stated subject was saturation, not
rounding. Caught by an assertion, not by review:
`MathUtilRoundSaturationTest.Preserved.agreesBelowTheCeiling` sweeps both bodies over 36 values at four
scales and demands agreement everywhere below the ceiling. The mode is now `HALF_UP` above zero and
`HALF_DOWN` below it — together exactly half-toward-positive-infinity, i.e. `Math.round`'s own rule,
preserved outside the new ceiling. `NaN` and the infinities have no `BigDecimal` representation and are
returned unchanged; the fork's old body mapped `NaN`→`0.0` and `±Infinity`→`±9.223372036854776E8`,
neither of which is a rounding of anything, and both are reachable from OCL (`UReal(1,0)/UReal(0,0)`
produces an infinity that flows into `toString`/`equals`).

**The mechanism caught its own declaration going stale.** F-3's declaration was written as 72 rows over
9 pairs; when F-2 landed it stopped matching and clause 4 refused the gate. Not a regression: 56 of
those 72 rows had been departing only because the *old* `round` mangled large values, and with the
ceiling gone they agree with the fork again. What survives is 16 rows over 2 pairs
(`Double.hashCode(-0.0)` becoming `Double.hashCode(0.0)`, and a subnormal rounding to zero — both cases
the fork's own `equals` already called equal), exactly co-extensive with the contract violation F-3 was
written for. Discovered by the pre-registration lapsing, not by anyone reading the code — rule 1 doing
the job it exists for.

**Type and dispatch rows (M-21, M-22, M-37, M-38 — `B7TypeAndDispatchTest`, 12 tests),** needed because
`harness-contract.md` §C3 says the differential harness cannot see the type layer. M-21:
`UBooleanType`/`UStringType`/`SBooleanType` put `TypeFactory.mkX()` in `allSupertypes()` where
`URealType`/`UIntegerType` put `this` — same object for the factory singleton, but a
**directly-constructed** type was not among its own supertypes. M-22: constructor visibility differs
across the five sibling types — caught as a **compile error** on the first test draft, the stronger
statement. M-37: `Op_uInteger_value.matches` declared `mkUInteger()` while `eval` returns
`IntegerValue`, mistyping every enclosing expression. M-38: `Op_uBoolean_or` dereferenced the `null`
`UBooleanValue.valueOf` returns for `Undefined`; its sibling `and` guards it, `or` did not (witness:
`ExpUndefined(TypeFactory.mkUBoolean())`, since the ordinary corpus routes around `Op_uBoolean_or`
entirely). **Porting omission found beside it, not a B7 row:** `VoidType` was missing all five
`isKindOfU*` overrides the fork has; fixed — moves the port *towards* the fork, so no
`IntendedDepartures` entry adjudicates it.

### 4.3c Dead code removed: `ExpDefSBoolean` and `ASTSBooleanDefExpression`

The user's follow-up instruction (2026-08-20): dead code, including semantics code with no grammatical
binding, is removed rather than fixed or kept. `ExpDefSBoolean` and its constructing AST node
`ASTSBooleanDefExpression` are exactly that population, and are also M-26 and M-27's site.
**Confirmed unreachable, mechanically**: no grammar production in `use-core/src/main/resources/grammars/`
constructs `ASTSBooleanDefExpression`; the only other references were `ExpressionVisitor`'s visitor
method and its two implementers.

**Removed:** `ASTSBooleanDefExpression.java`, `ExpDefSBoolean.java`,
`ExpressionVisitor.visitDefSBoolean`, and its two implementations
(`ExpressionPrintVisitor`, `AbstractCoverageVisitor`).

**M-26 and M-27 are moot, not fixed and not deferred** — M-26 (a missing `ctx.exit` in
`ExpDefSBoolean.eval`) and M-27 (an inverted constructor guard) die with the class. `b7-fix-plan.md` §2
had already anticipated this outcome (B10, "MOOT — do not port the class"); what changed here is that
B10's "drop" is executed, because the class had in fact been ported anyway. Verified no other dead
uncertainty code exists on the same surface: every grammar-bound AST literal now maps one-to-one to a
constructing `Exp*` class, an exact five-to-five correspondence. `mvn -o -pl use-core compile` and
`mvn -o -pl use-gui -am compile` both succeed with these removed.

### 4.3d M-29, M-30, M-32, M-33 — the parser and literal-constant layer

`b7-fix-plan.md` §3 marked these as reaching a compile error before the corrected line, or having no
corpus example at all. `B7ParserAndConstantsTest`, 12 tests, driven end to end through
`OCLCompiler.compileExpression`:

| Row | Defect | Witness |
|---|---|---|
| M-29 | `ExpConstUBoolean` checked only `probability.isUndefined()`; an undefined value operand's `toString()` is the text `"Undefined"`, `Boolean.valueOf("Undefined")` is `false` with no exception, and `valueOf` normalises that to a defined `(true, 1-p)` manufactured from an operand never there. | `UBoolean((let b:Boolean=Undefined in b), 0.8)` |
| M-30 | `ExpConstUString.eval` had an unguarded `(StringValue)` cast and an unguarded `Double.valueOf`, both uncaught when a statically well-typed operand evaluates to `Undefined` at runtime. | `UString((let s:String=Undefined in s), 0.9)` |
| M-32 | `ASTURealLiteral.gen` called each child's `.gen(ctx)` **twice** (type check, then construction), building and installing two distinct graphs. | nested `let`s in both positions |
| M-33 | `ASTUStringLiteral` had no `toString()` override, unlike its four siblings, and fell through to `Object.toString()`'s identity hash. | direct construction |

Witnesses use a `let`-declaration with an explicit type ascription for "undefined value, statically
typed" — `oclAsType` refuses to widen `OclVoid` to a declared subtype, so that alternative route does
not compile.

### 4.3e M-6, M-28, M-31 — a written reason, at the site, not a code change

B7 says "fix the historical defects, documenting each," and for these three the documented finding is
that the ledger's proposed change is *worse* than what stands — the decision, applied, not evaded. Each
now carries its full reasoning as Javadoc **at its own call site** rather than only here (`grep -c
'ledger M-6\|ledger M-28\|ledger M-31'` in §6 confirms all six sites), so a reader who arrives at the
code sees it directly: **M-6** (`URealValue.assertKindOfUReal` + 3 siblings) — narrowing the bare
`RuntimeException` rejected, because two downstream `catch` sites swallow `Exception` broadly and the
full downstream set could not be enumerated. **M-28** (`ExpConstUBoolean.eval`) — replacing the
`String`-round-trip with direct accessors rejected, because the round-trip is two behaviours in one
expression (it also produces the `NumberFormatException` the surrounding `catch` converts to
`Undefined`). **M-31** (`ExpConstUReal`'s constructor) — moving `ASTURealLiteral`'s type check into the
constructor rejected, because that constructor is called directly, unvalidated, at 300+ sites in the
ported test suite. No test count moves. **M-43, M-48b, M-51 were not yet actionable** — fork test files
not yet ported (closed later: M-43 in §4.3j, M-48b/M-51 in §4.3i).

### 4.3f `uEquals` — a question closed, not a fix

An earlier session left a standing worry: commit `d99ab9ef` routed `=`/`<>` on uncertain operands to
`uEquals`, noting `UReal(2,0.5) = 2` giving probability `0.0` "may be a genuine fork defect; cannot be
judged until `uEquals` is swept." `UEqualsCoverageTest`, 6 tests, closes it two ways. **Is it swept?**
Yes, automatically — `uEquals` is a public, single-`Value`-argument method on all five uncertain classes
in both the jar and the port, so it has been one of the 355 operations `PortedFidelitySweepTest` drives
since that test existed, and reports `AGREE` on every measured row. **Is the `0.0` correct?** Yes, and
not by coincidence: `uDataTypes.UReal.calculate` sets `eq=0.0` **unconditionally** whenever exactly one
side of a comparison is degenerate (uncertainty exactly 0), before looking at how close the means are —
correct for a continuous distribution compared to an exact point, which has probability zero of exact
equality for any mean. Two genuinely uncertain operands (`UReal(2,0.5) = UReal(2,0.5)`) give
`UBoolean(true,1.0)` via the same library method's Gaussian-overlap branch.

### 4.3g Two regressions found while porting the historical corpus

Running 1427 fork corpus entries (§4.3i) against the port for the first time exposed two real
regressions in `StandardOperationsNumber`: `Op_number_toString` and `Op_number_unaryplus` both wrongly
refused an uncertain operand, because an earlier session (S8) applied `UncertainOperand.present`'s guard
to all four of `unaryplus`/`pow`/`sqrt`/`toString` when only `pow` and `sqrt` need it (the fork provides
dedicated `UReal`/`UInteger` replacements for those two only). Fixed by removing the guard from both,
with Javadoc citing the fork line numbers that show the asymmetry; committed separately (`e37abb2b`)
from the corpus port that found them.

### 4.3h `uSelect`/`uSelectC`, uncertainty-aware collection membership, and a quantifier bug

Two features the port was missing entirely, both ported from the fork:

- **`uSelect`/`uSelectC`** (`ExpUSelect`, `ExpUSelectC`, ported verbatim): `collection->uSelect(e|body)`
  keeps elements whose body evaluates to crisp `true` or a `UBoolean` with probability ≥ 0.5; `uSelectC`
  takes an explicit threshold. Needed a generic optional trailing `, confidence` clause on
  `queryExpression` in the grammar (`OCLBase.gpart`, `ParserHelper`, `ASTQueryExpression`), matching the
  fork's own grammar shape rather than special-casing these two operations.
- **Uncertainty-aware collection membership** (`CollectionValue.uIncludes`/`uIncludesAll`/`uExcludes`/
  `uExcludesAll`, ported verbatim): `StandardOperationsCollection`'s `includes`/`excludes`/`includesAll`/
  `excludesAll` now branch `mkBoolean()` vs `mkUBoolean()` by element/argument uncertainty. One fork
  dead-code oddity preserved byte-for-byte on purpose: `includes`'s `matches()` checks `params[1]
  instanceof UncertainValue` where `params[1]` is actually `Type[]` — always `false`, since `Type` and
  `Value` are unrelated hierarchies. Not a B7 row; left as-is with a code comment flagging it for a
  future finding.

**The multi-variable `forAll`/`exists` accumulation bug — a genuine, pre-existing fork defect, found
incidentally and fixed.** Making `ExpForAll`/`ExpExists` accept a `UBoolean`-valued body (needed so
`uSelect`'s body type-checks against the same machinery) required replacing the crisp-only,
short-circuiting `evalExistsOrForAll`/`evalExistsOrForAll0` with ported `evalExists0`/`evalForAll0` using
`ExpStdOp.create` dispatch. Building regression tests for that replacement surfaced a defect in the
fork's own `evalForAll0`/`evalExists0`, confirmed byte-for-byte present in the fork's shipped source
before deciding to fix it (core crisp OCL semantics, not uncertainty-specific): at nested recursion (2+
element variables), `res = evalForAll0(nesting+1, ...)` **overwrites** the outer accumulator instead of
combining with it, so only the last outer iteration's inner result survives — universal/existential
quantification over 2+ variables is silently wrong whenever the outer variable ranges over more than one
element. It broke two of `use-gui`'s own inherited `ShellIT` fixtures, both passing on stock USE 7.5.0:
`t049` (`Person::nameUnique`, a 2-variable invariant) and `t022` (2-variable `exists` in
`transitiveClosure`). Per ground rule 3's spirit ("if an upstream test fails after a change, the change
is wrong") — this binds the port's own new code, not license to edit the test — `evalForAll0`/
`evalExists0` now AND/OR-combine the recursive result with the outer accumulator via `ExpStdOp.create`
dispatch **at every nesting level**, not just the leaf. Only `use-core/ExpQuery.java` changed; no
`use-gui` source was touched, so ground rule 5 is unaffected. `mvn -o -pl use-gui verify -Dit.test=ShellIT`
→ 129 tests, 0 failures. `UncertainQueryAndMembershipTest` (16+ tests, nested `USelect`/`USelectC`/
`ForAllExists`/`Membership`/`MultiVariableAccumulation`) covers both features; the last nested class
regression-tests the accumulation fix specifically (`forAllAccumulatesAcrossOuterIterations`,
`existsAccumulatesAcrossOuterIterations`, `twoVariableInvariantCatchesDuplicate`, mirroring `t049`).
Committed as `7da9de10`.

### 4.3i CF-8 and neighbours — the historical corpus test harness, ported

`USECompilerUncertaintyTest` (`org.tzi.use.parser.uncertainty`) replays all 1427 entries of the fork's
`UBooleanExpression.in`/`UCollectionOperations.in`/`UIntegerExpression.in`/`URealExpression.in` corpus
through the port's own compiler and evaluator, copied byte-identically from the fork (sha256-verified),
adjusted only where three fixture-data waivers required it. Porting the JUnit-3 harness to JUnit 5
closed six rows:

| Row | Fix |
|---|---|
| CF-8 | `user.dir`-relative `File.listFiles` → a fixed, sorted array of the four corpus filenames via `Class.getResourceAsStream` (`Class.getResource(".")` returns `null` under this project's JPMS module) |
| CF-9 | platform-default charset → `StandardCharsets.UTF_8` explicitly |
| M-45 | `Options.explicitVariableDeclarations=false` set once, never restored → saved/restored in `@BeforeEach`/`@AfterEach` |
| M-48b | non-static inner class, identity-hash `toString()` → static final class, explicit `toString()` |
| M-49b | `split("\n(\r\n)")` is not a valid alternation and never matched, so 5 error-path entries were checked against all of stderr → `split("\r?\n")` + a `lastNonBlank` helper, all 5 still pass |
| M-51 | a caught `IOException` was rethrown without its cause → `new RuntimeException(msg, ex)` |

Two more close as a side effect of the JUnit 5 port itself: **CF-7** (2 of 12 sites — writing the port's
`assertEquals` calls in JUnit 5's argument order from the start avoids the fork's silent-rebind hazard;
the other 10 close in §4.3k) and **CF-5** (the fork's order-pinning JUnit-3 `AllTests` suites were never
ported — Surefire's own discovery replaces them, and M-45's fix removes the leak those suites existed to
survive, per the ledger's own recommendation).

Three fixture-data waivers (`docs/port2/upstream-test-waivers.md` W-02/W-03/W-04: 79 entries expecting
`Undefined : OclVoid` where the port correctly gives `null : OclVoid`; 8 expecting more decimal digits
than `UBooleanValue.toString()` ever prints; 5 writing `.equals(...)` directly on a `Collection`-typed
`iterate` result where OCL requires `->equals(...)`) — no `.java` test file edited, only fixture *data*.
`mvn -o -pl use-core test -Dtest=USECompilerUncertaintyTest` → 1427/1427.

### 4.3j B7 M-43 — `UBooleanValueTest`, and a ledger prediction the real historical jar refutes

The fork's `UBooleanValueTest.java` left two assertions commented out (`// FIXME: When It will be fixed
in atenea library`), each expecting `UBooleanValue.valueOf(true, ±2)` (an out-of-range probability) to
throw. `b7-fix-plan.md`'s own recommendation was to revive both as `@Disabled`, reasoning from the FIXME
text alone that the vendored library clamps rather than throws. **That reasoning does not hold, checked
directly**: this port's own `valueOf` throws `IllegalArgumentException` for both, and probed against the
**real historical jar** via `HistoricalOracle` reflection, so does the vendored constructor —
`uDataTypes.SBoolean`... `UBoolean(boolean,double)` validates `c<0 || c>1` and throws before
`UBooleanValue`'s own guard runs, confirmed byte-identical to the fork's own vendored source, unchanged
by this port's vendoring (B1). Whatever prompted the FIXME either did not apply to this code path or was
already fixed upstream of this port's snapshot. Both blocks are revived **live**, not `@Disabled`:
`valueOfRejectsProbabilityBelowZero()`, `valueOfRejectsProbabilityAboveOne()`. The remaining three test
methods port unchanged (none touch M-8's `equals()` fix). `mvn -o -pl use-core test -Dtest=UBooleanValueTest`
→ 5/5.

### 4.3k B7 M-44 — the last four fork test files, and the ledger closes at 33/33

M-44 covers 40 JUnit-3 `try{...;fail(...);}catch(X){}catch(Exception){fail(...);}` sites across four
fork test files, none of which existed in this port before this stage: `ExpQueryUncertaintyTest`
(already ported in §4.3i, 12 methods/2 sites), `UBooleanExpOpsTest` (27/3), `URealExpOpsTest` (32/4),
`UIntegerExpOpsTest` (39/8, plus the 10 remaining CF-7 sites). Total 110 test methods, 40 M-44 sites,
ported across four commits. **Every one of the 110 methods passes with zero semantic corrections** — the
strongest evidence this stage produced that the port's semantics are right, not merely internally
consistent: §4.1 and §4.3i both check the port against the *historical oracle's own arithmetic*; these
four files check it against a *human's independent expectations*, written for a codebase this port only
partially resembles syntactically. CF-7's JUnit-3→5 argument reorder (`assertEquals(message,expected,
actual)`→`assertEquals(expected,actual,message)`) applies at 898 `assertEquals` + 23 `assertTrue` call
sites across three files — done by a small script that parses each call's balanced-paren argument list
(kept only as a session scratch artefact, not committed) rather than by hand or by a line-based regex;
the 40 `try/fail/catch` blocks were converted by hand, each verified by an actual run before its
exception message was written into an assertion.

```
$ mvn -o -pl use-core test -Dtest=UBooleanExpOpsTest,URealExpOpsTest,UIntegerExpOpsTest
Tests run: 98, Failures: 0, Errors: 0, Skipped: 0
```

**CF-7 closes alongside M-44** — its remaining 10 sites (`UIntegerExpOpsTest`) reorder with the same
script as the file's other 434 `assertEquals` calls; the 2 in `ExpQueryUncertaintyTest` needed no reorder
(written JUnit-5-first). **The B7 ledger is now 33 of 33 rows discharged.** M-44 and CF-7 were the only
two rows with any work outstanding at the start of this stage's final push.

### 4.3l `uCount`/`uCountC` — a real fork feature, absent from the port until now

Neither operation was ever a B7 ledger row — B7 tracks behaviour that *changed* during porting, and
`uCount`/`uCountC` simply did not exist in the port at all. Surfaced by an earlier session's adversarial
audit, carried in §5 as an open item once the ledger itself closed. Ported from
`StandardOperationsCollection.Op_collection_uCount`/`Op_collection_uCountC` and
`CollectionValue.uCountC`, semantics unchanged: an ordinary `OpGeneric` operation registered like `count`
itself, the uncertain-equality analogue (an element counts if `uEquals` against the target meets a
confidence threshold — fixed `0.5` for `uCount`, explicit for `uCountC`). No grammar change needed. The
fork's own coverage (`UCollectionExpOpTest.testUCount`) evaluates one expression and asserts nothing —
`UCountCoverageTest`, 6 methods, replaces it with assertions on the actual returned count.
`mvn -o -pl use-core test -Dtest=UCountCoverageTest` → 6/6.

### 4.3m M-1..M-6 — the metamorphic relations, closing the SBoolean/UString blindspot

`docs/port2/stage-03-scope.md` §8.5 proposed six relations specifically because `SBoolean` and `UString`
carry the weakest independent evidence of the five uncertain types — each relation is a property of the
ported code checked against itself, yielding evidence where there is no fork test and no ported
counterpart to compare against. Confirmed before writing any of it: the fork's own suite has zero
property-based or metamorphic-test infrastructure anywhere, so this is new test design from a written
spec, not a port. `MetamorphicRelationsTest`, 14 methods across 6 `@Nested` classes — every relation
holds:

| # | Relation | Result |
|---|---|---|
| M-1 | Crisp embedding (`UReal`, `UInteger`, `UBoolean`, `UString`) | holds |
| M-2 | Degree monotonicity (`UReal`, `UInteger` addition) | holds |
| M-3 | `UBoolean` canonicalisation (`and`, `or`) | holds |
| M-4 | `UInteger`/`UReal` widening agreement (`+`, `-`) | holds |
| M-5 | `SBoolean` interning independence | holds |
| M-6 | `SBoolean` simplex closure (21 of 23 SBoolean-returning operations) | holds |

M-5 needed the package-private `SBooleanValue(double,double,double,double)` constructor, since the OCL
literal `SBoolean(1,0,0,1)` itself interns to `SBooleanValue.TRUE` (confirmed
`run("SBoolean(1,0,0,1)")==SBooleanValue.TRUE`), so a genuinely distinct-but-equal instance can only be
built by calling the constructor directly — which forced this file into `org.tzi.use.uml.ocl.value`
(alongside `UBooleanValueTest`) rather than the `org.tzi.use.uncertainty` package this stage's other new
tests use.

**M-6 found a real, pre-existing fork defect, incidentally — not fixed here.** Every `matches()` in
`StandardOperationsSBoolean.java` was read to enumerate all `SBoolean`-returning operations, not guessed
— 23 declare an `SBoolean` return type. Two of them, `conjunctiveCertainty` and `degreeOfConflict`, throw
a `NullPointerException` when treated as `SBoolean` (as their declared type promises): both delegate to
`SBooleanValue.conjunctiveCertainty`/`degreeOfConflict`, which actually return `RealValue`. Confirmed
byte-identical in the fork's own source — a genuine static-declared-type-vs-runtime-value mismatch in
the fork itself, not a portation artefact. Documented in `MetamorphicRelationsTest`'s own Javadoc and
excluded from the M-6 table (closure does not apply to an operation that doesn't return `SBoolean`), but
**not fixed**: changing a declared return type is its own independently-scoped decision (it could affect
OCL-level type-checking of any expression chaining off these two operations), and this file's job was to
measure the port against a written spec, not to fix whatever it found along the way.
`mvn -o -pl use-core test -Dtest=MetamorphicRelationsTest` → 14/14.

### 4.4 The gate

`bash scripts/upstream-oracle-gate.sh both` → **PASS**. Eleven re-pins across this stage's commits
(§3–§4.3f's corrections, §4.3h's `uSelect`/membership, §4.3i's corpus port, §4.3j's M-43, §4.3k's four
M-44 commits, §4.3l's `uCount`/`uCountC`, §4.3m's metamorphic relations):

| mode | classes | methods | executions | failures |
|---|---|---|---|---|
| default, `use-core` surefire | **59** (floor 15 → re-pinned) | **352** (floor 107 → re-pinned) | 352 | 0 |
| oracle, `use-core` surefire | **92** (floor 48 → re-pinned) | **623** (floor 378 → re-pinned) | 1211 | 0 |
| default/oracle, `use-gui` | unchanged: 1/1 surefire, 1/129 failsafe | — | — | 0 |

Four waivers total (W-01–W-04); no `.java` test file was ever edited to make ported code pass. Two
golden reports (`s1-smoke-ureal-add.tsv`, `s1-smoke-ureal-minus-faulty.tsv`) each gained two header lines
(`# rows.intendedDeparture 0`, `# op.<key>.intendedDeparture 0`) — no data row changed.

---

## 5. The B7 ledger closes: 33 of 33

| Rows | Status |
|---|---|
| M-11, M-8, M-9, M-10, M-12, M-18, F-2, F-3, F-4, F-10, bundle A | done, §3–§4.3 |
| M-21, M-22, M-37, M-38 | done, §4.3 |
| M-26, M-27 | moot: `ExpDefSBoolean` deleted as dead code, §4.3c |
| M-29, M-30, M-32, M-33 | done, §4.3d |
| M-6, M-28, M-31 | done — written justification at the site, §4.3e |
| CF-8, CF-9, M-45, M-48b, M-49b, M-51 | done, §4.3i |
| CF-5 | discharged structurally — the fork's order-pinning suites were never ported; M-45 removed the leak they existed to survive, §4.3i |
| M-43 | done, §4.3j — revived live, not `@Disabled`; the real historical jar throws too |
| M-44 | done, §4.3k — all 40 sites, 110 test methods, 0 semantic corrections |
| CF-7 | done, §4.3k — the last 10 of 12 sites ported alongside M-44 |

**33 of 33 rows are discharged. Nothing in the B7 ledger is open.** Counted directly by row name: 10
(value layer) + 4 (type/dispatch) + 2 (moot) + 4 (parser/constants) + 3 (written justification) + 6
(corpus harness) + 1 (CF-5, structural) + 1 (M-43) + 1 (M-44) + 1 (CF-7) = 33. Before this session: 1;
partway through this commit sequence: 24, then 31 (after M-43), now 33, complete.

**What "closed" does and does not mean.** Every row in the 33-row ledger has a commit, a test and
written evidence behind it. It does not mean every conceivable defect in the uncertainty extension has
been found — `b7-fix-plan.md` §1 scoped itself to what its own audit could find. Two categories of work
were genuinely open, *outside* this ledger's scope, when the 33/33 milestone landed: `uCount`/`uCountC`
(never implemented at all, so its absence was never forced into view) and the six metamorphic relations
(property-based tests the fork's own suite does not contain — they needed designing, not porting). Both
closed in this same stage, immediately after (`uCount`/`uCountC` in §4.3l, M-1..M-6 in §4.3m);
`uSelect`/`uSelectC` and uncertainty-aware collection membership, previously in the same "missing
feature" category, closed earlier still, in §4.3h.

As of this stage's last commit, **nothing from the adversarial audit's original findings remains open.**
That is not a claim the port is defect-free — see §4.3m's own finding
(`conjunctiveCertainty`/`degreeOfConflict`'s return-type mismatch), found in the course of this very
work and left open on its own terms. It is a claim about the specific, named backlog this stage
inherited: it is now empty.

---

## 6. Reproduce every number in this file

```sh
cd /home/xoruser/msc-4/use-msc2026
mvn -o -pl use-core test -Dtest=PortedFidelitySweepTest,B7CorrectionsTest,MathUtilRoundSaturationTest,B7TypeAndDispatchTest,B7ParserAndConstantsTest,UEqualsCoverageTest,UncertainQueryAndMembershipTest,USECompilerUncertaintyTest,UBooleanValueTest,ExpQueryUncertaintyTest,UBooleanExpOpsTest,URealExpOpsTest,UIntegerExpOpsTest,UCountCoverageTest,MetamorphicRelationsTest,IntendedDeparturesTest
mvn -o -pl use-gui verify -Dit.test=ShellIT -DfailIfNoTests=false       # §4.3h regression check
bash scripts/upstream-oracle-gate.sh both                               # §4.4

git log --oneline -1 -- use-core/src/main/java/org/tzi/use/parser/ocl/ASTSBooleanDefExpression.java   # §4.3c
grep -rn 'ExpDefSBoolean\|ASTSBooleanDefExpression' use-core/src/main use-gui/src/main 2>/dev/null || echo "none found"
grep -c 'ledger M-6\|ledger M-28\|ledger M-31' use-core/src/main/java/org/tzi/use/uml/ocl/value/{URealValue,UIntegerValue,UStringValue,SBooleanValue}.java use-core/src/main/java/org/tzi/use/uml/ocl/expr/{ExpConstUBoolean,ExpConstUReal}.java  # §4.3e
grep -n 'types\[i\] == valueClass' -A 8 use-core/src/test/java/org/tzi/use/uncertainty/differential/UnwrittenPortInvariantTest.java  # §2
grep -c '^# W-' docs/port2/upstream-test-waivers.md                     # §4.4 waiver count
```

---

**Addendum, 2026-08-21:** §5's "nothing from the adversarial audit's original findings remains open" is
a claim about this stage's specific 33-row backlog, not a claim that the five uncertain types were
exhaustively verified — §5 says as much itself, citing the `conjunctiveCertainty`/`degreeOfConflict`
finding as a named counter-example. A follow-up independent audit (fresh subagents, no memory of this
session) found six more real defects and 24 more untested operations that this stage's methodology did
not surface. See `docs/port2/stage-10.md`. This file's own content above is left as originally written —
a record of what this stage did, not a continuously-updated status page.
