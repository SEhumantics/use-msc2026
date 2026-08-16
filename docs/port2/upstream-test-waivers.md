# Upstream-test waivers

Ground rule 3: **never edit an upstream test to make ported code pass.** If an upstream test fails
after a change, the default conclusion is that the change is wrong. Editing the test requires an
individually written waiver naming the upstream behaviour, why the port legitimately alters it, and
why that alteration is correct. **Target: zero.**

## Waivers issued

**None.** Zero waivers through S0, S1 and S2.

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
