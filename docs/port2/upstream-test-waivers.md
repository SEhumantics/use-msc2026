# Upstream-test waivers

Ground rule 3: **never edit an upstream test to make ported code pass.** If an upstream test fails
after a change, the default conclusion is that the change is wrong. Editing the test requires an
individually written waiver naming the upstream behaviour, why the port legitimately alters it, and
why that alteration is correct. **Target: zero.**

## Waivers issued

**Four.** W-01, W-02, W-03 and W-04, below. Zero through S0, S1, S2 and S3(1/2).

## Evidence, as of `8c410c98`

```bash
git diff --name-status 30d480db..HEAD -- '*/src/test/*' '*/src/main/*'
```

All 15 paths are `A` (added); there is no `M`, `D` or `R` line:

```
A	use-core/src/test/java/org/tzi/use/uncertainty/differential/Candidate.java
A	use-core/src/test/java/org/tzi/use/uncertainty/differential/DiffReportWriter.java
A	use-core/src/test/java/org/tzi/use/uncertainty/differential/DiffRow.java
A	use-core/src/test/java/org/tzi/use/uncertainty/differential/DiffVerdict.java
A	use-core/src/test/java/org/tzi/use/uncertainty/differential/DifferentialSweep.java
A	use-core/src/test/java/org/tzi/use/uncertainty/differential/HistoricalOracle.java
A	use-core/src/test/java/org/tzi/use/uncertainty/differential/HistoricalOracleIsolationTest.java
A	use-core/src/test/java/org/tzi/use/uncertainty/differential/InputGenerator.java
A	use-core/src/test/java/org/tzi/use/uncertainty/differential/IsolatedJarClassLoader.java
A	use-core/src/test/java/org/tzi/use/uncertainty/differential/StubCandidate.java
A	use-core/src/test/java/org/tzi/use/uncertainty/differential/UOp.java
A	use-core/src/test/java/org/tzi/use/uncertainty/differential/UValue.java
A	use-core/src/test/java/org/tzi/use/uncertainty/differential/UncertaintyDifferentialSmokeTest.java
A	use-core/src/test/resources/historical/atenearesearchgroup.uncertainty.jar
A	use-core/src/test/resources/historical/use.jar
```

`use-gui` and `use-assembly` carry no source change at all. `use-core/src/main` is untouched,
including `module-info.java`.

## Standing caution — rule 3 currently has no automatic signal

On this branch 38 of 41 `*Test.java` files never execute: there is no `junit-vintage-engine`, so
every JUnit 3 and JUnit 4 upstream test is silently uncollected (`stage-00-baseline.md` §3). An edit
to a dormant upstream test would therefore produce **no test failure at all**.

Until blocking decision **B3** is taken (`specification.md` §0), rule 3 is enforced by the
`git diff --name-status` check above, not by the suite. That check should be run at every stage
acceptance, and its output pasted into the stage report.

One upstream conflict is already known and will land in S3 — it is **not** a waiver yet, and must
not become one silently:

* **B5 — `TypeTest#testSupertype`.** Adopting the fork's lattice (`Real ≤ UReal`, `Boolean ≤
  UBoolean`, `String ≤ UString`, `Integer ≤ UInteger` in `allSupertypes()`) makes **10 of the 12
  assertions** in upstream's own untouched `testSupertype` false. This is a lattice *design*
  question, not a test-hygiene question, and it cannot be dissolved by moving assertions into a new
  test class. If S3 proceeds with the fork's lattice, the resolution must be recorded here as a
  written waiver naming the upstream behaviour being changed and why that is correct — or the
  lattice must change instead.

---

# W-01 — `TypeTest#testSupertype`

**Issued** 2026-08-18, at S3(2/2). **Anticipated** since S2 as decision B5; the standing caution at the
foot of this file predicted it by name and required that it "must not become one silently".

## 1. The upstream behaviour being changed

`use-core/src/test/java/org/tzi/use/uml/ocl/type/TypeTest.java#testSupertype` asserts **exact set
equality** on `allSupertypes()` for twelve types. Upstream, the four crisp basic types have no
uncertain supertypes, so e.g.

```java
assertEquals("Boolean.allSupertypes()",
             mkSet(new Object[] { TypeFactory.mkBoolean(), TypeFactory.mkOclAny() }),
             TypeFactory.mkBoolean().allSupertypes());
```

Because the assertion is **exact** rather than containment, adding any element makes it false. That is
why this test — and only this test — breaks.

## 2. What the port alters, and why it legitimately does so

S3(2/2) adopts the fork's lattice: `Real ≤ UReal`, `Integer ≤ UInteger`, `Boolean ≤ UBoolean`,
`String ≤ UString` (plus the uncertain-internal `UInteger ≤ UReal`, `UBoolean ≤ SBoolean`, which
landed in S3(1/2) and break nothing).

This is not an incidental consequence of adding types. It is the fork's **deliberate design**, and it
is load-bearing:

* The fork tests it directly and in the same direction — `FORK/src/test/.../type/TypeTest.java:138`
  asserts `TypeFactory.mkReal().conformsTo(TypeFactory.mkUReal())`, `:153`
  `mkInteger().conformsTo(mkUReal())`, `:156` `mkInteger().conformsTo(mkUInteger())`.
* It is what makes the mixed collection literal work. `Set{UReal(2,0.5), 1, 2.5}` has type
  `Set(UReal)` in the fork and is a compile error in plain USE 7.5.0 (measured on both sides,
  `adaptation-policy-refutation.md`). The element type is decided by
  `UniqueLeastCommonSupertypeDeterminator`, which reads `allSupertypes()`. Without the crisp→uncertain
  edges the fork's own worked example does not typecheck.
* Mixed **binary arithmetic** (`UReal(0,0) + 3`) does *not* depend on it — that goes through operation
  signatures (`StandardOperationsUReal.java:164-165`). So the lattice is not redundant scaffolding for
  arithmetic; collections are precisely what needs it.

Adopting the fork's semantics here is the whole point of the port. The alternative — keeping upstream's
lattice — would produce an evaluator that disagrees with the oracle on collection literals, which is
the one thing this port exists to prevent.

## 3. Why the alteration is correct, and bounded

**Measured, not argued.** A fingerprint of every `conformsTo` cell and every pairwise
`getLeastCommonSupertype` cell over a 12-type crisp universe (144 cells each), taken immediately
before and after the change on the same build:

| Metric | Before | After |
|---|---|---|
| `conformsTo` — 144 cells | `-1429835451` | `-1429835451` |
| pairwise LCS — 144 cells | `1606464704` | `1606464704` |

**Byte-identical.** Over crisp types, conformance and least-common-supertype do not move at all. The
independent round-8 refutation reached the same conclusion on a wider universe: 0 of 324 `conformsTo`
cells, 0 of 324 pairwise LCS cells, 0 of 1100 ULCS cells. **Only `allSupertypes()` itself changes** —
which is exactly, and only, what `testSupertype` asserts.

That is why the blast radius is one test method. Empirically confirmed: the full
`scripts/upstream-oracle-gate.sh` run produced **one** failing method across both modules —
`TypeTest#testSupertype`, 1 of 38 in its own class, and no other failing class anywhere in the reactor.

## 4. The edit made, and why it is the minimal one

The test's *character* is preserved: it still asserts **exact set equality**, not containment. Only the
expected values change, and each is **derived from the intended lattice**, not copied from the
implementation's output — otherwise the test would be a rubber stamp for whatever the code does.

Ten of twelve assertions are updated; `OclAny` and `Enum` are untouched because their supertype sets do
not move. The six collection assertions change only as a consequence of their element type's set
growing — `Collection(Integer)` gains `Collection(UReal)` and `Collection(UInteger)` because `Integer`
gained `UReal` and `UInteger`.

**What was NOT done, deliberately:** the assertions were not weakened to `assertTrue(...contains...)`,
and they were not moved to a new test class. Either would have destroyed the exactness that makes this
test the one that caught the change.

## 5. Residual

`TupleType.allSupertypes()` grows from `3ⁿ+1` to `5ⁿ+1` over `Integer` parts — measured directly:
**730 → 15,626** at arity 6. No upstream test exercises a tuple of that arity, so nothing fails today,
and this waiver does not cover it. It is tracked as a performance hazard in `stage-03-scope.md` §1 and
must be addressed on its own evidence, not folded in here.

---

# W-02 — 79 corpus entries expecting `Undefined : OclVoid`

**Issued** 2026-08-20, at S9 (porting the fork's test-harness files). Found while running the ported
`USECompilerUncertaintyTest` against the full 1427-entry corpus for the first time.

## 1. The upstream behaviour being changed

`UBooleanExpression.in`, `UIntegerExpression.in` and `URealExpression.in` (16 + 38 + 25 = 79 entries;
`UCollectionOperations.in` has none) contain lines of the exact form:

```
UBoolean(true and false, 3 / 0)
-> Undefined : OclVoid
```

Every one of these entries evaluates to a defined-shape expression whose value is
`UndefinedValue.instance`, and the expected string is that value's `toStringWithType()`: the value's
own rendering (`"Undefined"`), a literal `" : "`, and the runtime type's rendering (`"OclVoid"`).

## 2. What the port produces instead, and why it legitimately does so

This port's `UndefinedValue.toString(StringBuilder)` appends `"null"`, not `"Undefined"`, so every
one of these 79 entries fails with e.g. `expected: <Undefined : OclVoid> but was: <null : OclVoid>`.

**This is not a porting defect.** `UndefinedValue` carries no uncertainty semantics — it is core
OCL/USE machinery, unrelated to the extension this project ports — and the governing adaptation
policy is explicit about exactly this case: *"Uncertainty meaning comes from the fork. Everything
else comes from USE 7.5.0. Where they collide, keep the uncertainty behaviour but express it the
7.5.0 way."* `UndefinedValue`'s string form is "everything else."

Verified against the read-only reference tree, not assumed:

```
$ grep -n 'toString' -A 4 .git/reference-repositories/upstream-use/use-core/src/main/java/org/tzi/use/uml/ocl/value/UndefinedValue.java
    public StringBuilder toString(StringBuilder sb) {
        return sb.append("null");
    }
```

Stock USE 7.5.0 prints `"null"`. The fork's own copy of the same file
(`.git/reference-repositories/uncertainty/USE-Uncertainty/src/main/org/tzi/use/uml/ocl/value/UndefinedValue.java:45-47`)
prints `"Undefined"` — a difference between the fork's (older) USE base and 7.5.0, not something the
uncertainty extension introduced on purpose. `git log --follow` on this file in this repository's own
history even names the commit that made the change upstream: `72ab8fd7 changed Undefined to null`.

The port's `UndefinedValue` is untouched 7.5.0 code and correctly renders `"null"`. The alternative —
changing it to print `"Undefined"` — would mean editing core, non-uncertainty machinery to satisfy a
fork-vintage fixture, which is precisely backwards under the adaptation policy and would silently
change the printed form of *every* undefined value everywhere in the language, not just the 79
uncertainty-corpus entries that happen to exercise it.

## 3. Why the alteration is correct, and bounded

**Confirmed the discrepancy is exactly this text and nothing else.** `UndefinedValue.toString` is the
only site in the port that could produce the substring `"Undefined"` for a value's own rendering
(`grep` for the literal across `value/` and `type/` finds nothing else). Every one of the 79 entries
was re-run after the substitution below and all 79 pass — see the S9 stage record for the executed
count. No entry's expectation beyond the literal string `Undefined` → `null` was touched.

**Scope check.** All 79 entries have the shape `-> Undefined : OclVoid` exactly — none is
`-> Undefined` alone or `Undefined :` with different surrounding text — so a single, exact,
whole-line substitution is safe: `grep -c '^-> Undefined : OclVoid$'` on each of the three affected
files, before and after, accounts for every occurrence.

## 4. The edit made, and why it is the minimal one

In `use-core/src/test/resources/org/tzi/use/parser/uncertainty/{UBooleanExpression,UIntegerExpression,URealExpression}.in`
**only** (never in `.git/reference-repositories/`, which is read-only per ground rule on reference
repos): every line reading exactly `-> Undefined : OclVoid` becomes `-> null : OclVoid`. Nothing else
on any line changes — not the expression text above it, not any other expected string, not line
count or ordering. `UCollectionOperations.in` is untouched; it has no such line.

**What was NOT done, deliberately:** the fixture's other content was not otherwise "cleaned up" or
reformatted while this edit was made, and no entry with a *different* expected string was touched,
even ones that looked adjacent or related.

---

# W-03 — 8 corpus entries expecting a probability with more precision than any implementation prints

**Issued** 2026-08-20, at S9 (porting the fork's test-harness files). Found the same way as W-02: running
`USECompilerUncertaintyTest` against the full 1427-entry corpus.

## 1. The upstream behaviour being changed

Eight entries across `UBooleanExpression.in` (2) and `UCollectionOperations.in` (6) expect a `UBoolean`
probability with 4 to 10 significant decimal digits, e.g.:

```
UBoolean(false, 0.55) and UBoolean(true, 0.49)
-> UBoolean(true, 0.2205) : UBoolean
```

```
Set{1, 2, UReal(2,5)}->forAll(e | e >= 1)
-> UBoolean(true, 0.5792596878) : UBoolean
```

`UBooleanValue.toString(StringBuilder)` rounds the probability to 3 decimal places via
`MathUtil.round(probability(), 3)` — ported verbatim from the fork, unchanged by this port (see F-2,
S9(3/n), for the one fix that class needed, which is unrelated: saturation of `MathUtil.round` above
`9.2e8`, not its digit count). No entry in this corpus, run against a correctly functioning
implementation of the fork's own `UBooleanValue`, could ever print more than 3 decimal digits — the
fixture's own expected strings are asserting an output shape neither the fork nor the port produces.

## 2. What the port produces instead, and why it legitimately does so

Confirmed for all eight, not assumed, by reading each result's *unrounded* `probability()` field
(`UBooleanValue.probability()`, the value `toString` rounds from) before it is formatted:

| Expression | Fixture expected | Port's raw `probability()` | Port's printed (3dp) |
|---|---|---|---|
| `UBoolean(false,0.55) and UBoolean(true,0.49)` | `0.2205` | `0.22049999999999997` | `0.22` |
| `UBoolean(false,0.45) or UBoolean(true,0.37)` | `0.7165` | `0.7165` | `0.717` |
| `Set{1,2,UReal(2,5)}->forAll(e \| e >= 1)` | `0.5792596878` | `0.5792596877748737` | `0.579` |
| `Set{UReal(1,0.5),UReal(1,0.75),1.2}->forAll(e \| e >= 1.2)` | `0.1360612114` | `0.1360612114052354` | `0.136` |
| `Set{0,1,UReal(3,0.5)}->exists(e \| e >= 3)` | `0.4999999995` | `0.499999999474649` | `0.5` |
| `Set{UReal(2,0.5),1,2.5,3.2,UReal(3.5,0.25)}->includes(UReal(2,0.2))` | `0.5850213691` | `0.5850213691331607` | `0.585` |
| `Set{UReal(2,0.35),UReal(2,0.3)}->includes(UReal(2,0.29))` | `0.9835952315` | `0.9835952315410111` | `0.984` |
| `Set{UReal(2,0.5),1,2.5,3.2,UReal(3.5,0.25)}->includesAll(Set{2.5,UReal(3.5,0.15)})` | `0.758018702` | `0.7580187020081727` | `0.758` |

Every fixture value matches the port's raw, unrounded arithmetic to the precision the fixture itself
wrote (4 to 16 significant digits) — this is not a coincidence and not a near-miss: the port's
underlying computation is exactly correct, including for `uIncludes`/`uIncludesAll`/`->forAll`/
`->exists`, all of which are new code from this same S9 stage (see the uSelect/collection-membership
commit) and had no prior oracle-level evidence before this corpus run exercised them. **This is not a
porting defect** — it is confirmation, by a route independent of the differential sweep, that the new
collection-membership and quantifier code computes the right number. The only thing wrong is that the
fixture's expected string was authored (or hand-transcribed) at a precision no implementation of
`UBooleanValue.toString` — fork or port — ever emits.

## 3. Why the alteration is correct, and bounded

**Confirmed the discrepancy is exactly a precision mismatch and nothing else** for all eight: each raw
`probability()` value was read directly off the evaluated result before this waiver was written (not
inferred, not computed independently by hand — see the table above), and in every case it agrees with
the fixture's digits up to the fixture's own stated precision. No entry's *expected value* differs from
the port's computed value; only the number of digits the fixture chose to write differs from what
`toString` prints.

**Scope check.** Exactly eight entries were found to mismatch on first full-corpus run (of 1427 total,
`0` mismatches remained after this waiver and W-04 together — see the S9 stage record for the executed
count), and all eight fit this one pattern: a `UBoolean(true, …)` expected value whose digit count
exceeds 3. No entry with a 3-decimal-digit or coarser expected value was touched.

## 4. The edit made, and why it is the minimal one

In `use-core/src/test/resources/org/tzi/use/parser/uncertainty/{UBooleanExpression,UCollectionOperations}.in`
**only**: each of the eight `-> UBoolean(true, <n-digit>) : UBoolean` lines becomes
`-> UBoolean(true, <3dp-rounded-n>) : UBoolean`, where the 3dp value is exactly what
`MathUtil.round(probability(), 3)` produces from the same raw value the fixture's own digits already
agree with (shown in the table above). Nothing else on any line changes — not the expression, not the
`true`/`false` polarity, not any other entry.

**What was NOT done, deliberately:** `UBooleanValue.toString` was not changed to print more digits.
Doing so would mean changing the printed precision of every `UBoolean` value in the entire language to
satisfy eight fixture lines, which — like W-02 — is backwards: `toString`'s 3-decimal rounding is
existing, ported-verbatim, un-suspected-of-any-defect behaviour, ported from
`.git/reference-repositories/uncertainty/USE-Uncertainty/src/main/org/tzi/use/uml/ocl/value/UBooleanValue.java`
unchanged in this respect.

---

# W-04 — 5 corpus entries writing `.equals(...)` on a Collection where OCL requires `->equals(...)`

**Issued** 2026-08-20, at S9 (porting the fork's test-harness files). Found the same way as W-02/W-03.

## 1. The upstream behaviour being changed

Five entries in `UCollectionOperations.in` compare the result of an `iterate` (a `Set`/`Sequence`/`Bag`
of `UReal`, i.e. a Collection-typed value) against a `uSelect`/`uSelectC` result using dot notation:

```
let A = Set{2, 3, UReal(3, 0.5)} in (A->iterate(v; acc : Set(UReal) = Set {} | if (v > 2).toBoolean()
then acc->including(v) else acc endif) ).equals(A->uSelect(e|e>2))
-> true : Boolean
```

## 2. What the port produces instead, and why it legitimately does so

The port's compiler rejects all five with a compile error, e.g.:

```
UCollectionOperations.in:1:143: Undefined operation `UReal.equals' in shorthand notation for collect.
However, there is an operation `Set(UReal)->equals'. Maybe you wanted to use `->' instead of `.'?
```

**This is not a porting defect; it is correct, standard OCL.** In OCL, `.` on a Collection-typed
receiver is never "call this operation on the collection" — it is always shorthand for `collect`,
mapping the operation over each element (`ASTOperationExpression.java`, the
`SRC_COLLECTION_TYPE + DOT + PARENTHESES` branch, unconditionally routes to
`collectShorthandWithArgs`). `(iterate-result).equals(x)` therefore means "map `.equals(x)` over every
`UReal` element of the iterate result," which is nonsensical here (no `UReal.equals` operation exists)
and correctly rejected at compile time — the same rule this port relies on everywhere else a
Collection's own operations are invoked with `->`. Confirmed byte-identical between port and fork: both
route dot-notation-on-a-collection through the same collect-shorthand branch (verified by reading
`ASTOperationExpression.java` in both trees).

## 3. Why the alteration is correct, and bounded

**Confirmed the fix produces the fixture's already-correct expected value**, not a new one: changing
`.equals(` to `->equals(` in each of the five expressions and re-running gives `true : Boolean` in every
case — exactly the `-> true : Boolean` line already present in the fixture, untouched. So this waiver
changes five *expression* lines and zero *expected-result* lines; the corpus author's intent (the two
sides are equal collections) was correct, only the notation was not valid OCL.

**Scope check.** All five entries have the identical shape — a parenthesized `iterate` result followed
directly by `.equals(` — found by the compile-error location matching the fixture's own reported column
in each case; no other `.equals(` (or other dot-notation-on-collection) usage exists in any of the four
corpus files besides these five (confirmed by grep for `.equals(` across all four `.in` files).

## 4. The edit made, and why it is the minimal one

In `use-core/src/test/resources/org/tzi/use/parser/uncertainty/UCollectionOperations.in` **only**: each
of the five `).equals(` occurrences immediately following an `iterate` expression's closing parenthesis
becomes `)->equals(`. Nothing else on any line changes — not the `iterate` body, not the `uSelect`/
`uSelectC` argument, not the expected `true : Boolean` line, not any of the corpus's many other
legitimate uses of `.` for member/attribute access or non-collection method calls.

**What was NOT done, deliberately:** the collect-shorthand rule itself
(`ASTOperationExpression.SRC_COLLECTION_TYPE + DOT + PARENTHESES`) was not weakened or special-cased to
accept `.equals` on a collection. That rule is correct, ported-verbatim OCL semantics; the fixture's
notation was the error.
