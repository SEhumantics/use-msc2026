# Upstream-test waivers

Ground rule 3: **never edit an upstream test to make ported code pass.** If an upstream test fails
after a change, the default conclusion is that the change is wrong. Editing the test requires an
individually written waiver naming the upstream behaviour, why the port legitimately alters it, and
why that alteration is correct. **Target: zero.**

## Waivers issued

**One.** W-01, below. Zero through S0, S1, S2 and S3(1/2).

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
