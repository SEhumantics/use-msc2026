# S11 — closing out the U-types/SBoolean completeness review

**Role: Record.** Written 2026-08-21 on branch `port-uncertainty-2`. This document closes out a
completeness review of the Uncertainty Types (`UReal`/`UInteger`/`UBoolean`/`UString`) and Subjective
Boolean (`SBoolean`) port that the user commissioned via `/goal` on 2026-08-21. The review compared
the current implementation against `.git/reference-repositories/uncertainty/` (the original fork) and
against this project's own `docs/port2/` audit trail — the accumulated record of every prior stage's
findings and fixes. It ran as seven prior tasks plus this closing one; every fix and test it produced
is already committed, in order, on this branch. This document adds nothing new to the code — it runs
the acceptance gate one final time and records what the review did and did not establish.

---

## 1. Findings

### Finding 1 — the "delete if dangling" cuts were all correct

An independent method-level diff of all five vendored datatype classes (`UBoolean`, `UInteger`,
`UReal`, `UString`, `SBoolean`) against the old fork found that every method cut during porting was
already unreachable from the OCL grammar in the old fork too — none of the cuts removed anything that
had a live grammar path this project's standing "delete if dangling" rule would have required keeping.
Nothing needed restoring. `UUnlimitedNatural` (what the user refers to as "UUnlimitedInteger") stays
excluded from the port per the user's 2026-08-21 confirmation, consistent with the "do not add"
determination already recorded in `docs/port2/stage-03-scope.md` §3/§5.

No commit — this is a negative finding (nothing to restore), stated here for the record.

### Finding 2 — broken `Cloneable` contract on three classes, fixed

`UBoolean`, `UInteger` and `UReal` all declared `implements Cloneable` with no working `clone()`
method backing it — the `clone()` implementation was dropped during porting, correctly, since it was
itself dangling (no grammar path called it), but the `Cloneable` interface declaration on the class
should have been dropped in the same pass and was not. A class that implements `Cloneable` without
overriding `clone()` inherits `Object.clone()`'s protected, `CloneNotSupportedException`-throwing
behaviour behind a public marker interface that promises the opposite.

Fixed by commit `d50a4a9c`.

### Finding 3 — an undocumented (but legitimate) SBoolean cut, now documented

`SBoolean.union`, both `weightedUnion` overloads, and the binary `ccFusion(SBoolean)` overload were
dropped during porting without an audit-trail note recording the cut, unlike their six sibling fusion
wrappers (`minimumFusion`, `majorityFusion`, `averageFusion`, `cumulativeFusion`,
`epistemicCumulativeFusion`, `weightedFusion`), which were removed with a documented rationale. The
cut itself was legitimate — same "ungrammared, unreachable" class as the documented six — the gap was
purely in the audit trail.

Documented by commits `7bc15154` (the original note) and `09dea22d` (a numbering-consistency fix
caught by task review: the note's bullet numbering and intro count in `SBoolean.java`'s header comment
had drifted out of sync with the sibling notes already present).

### Finding 4 — the 8 SBoolean fusion operators (+ discount) had shape coverage but no value-correctness coverage; now closed, and it found a real defect

Prior to this review, `SBoolean`'s fusion operators (`minimumBeliefFusion`, `majorityBeliefFusion`,
`averageBeliefFusion`, `aleatoryCumulativeBeliefFusion`, `epistemicCumulativeBeliefFusion`,
`weightedBeliefFusion`, `beliefConstraintFusion`, `consensusAndCompromiseFusion`) and `discount` were
exercised only for shape — that a call type-checks, dispatches, and returns something of the right
declared type — never for whether the returned belief/disbelief/uncertainty/base-rate values were
numerically correct against the operators' own documented formulas. Closed by 20 new tests across five
commits, all in the new `SBooleanFusionValueTest.java`:

- `7d071d37` — value-correctness coverage for `minimumBeliefFusion`/`majorityBeliefFusion`
- `adf260e6` — value-correctness coverage for `discount`
- `f88ccdb6` — value-correctness coverage for `beliefConstraintFusion`
- `904e4576` — value-correctness coverage for `averageBeliefFusion`/`aleatoryCumulativeBeliefFusion`/
  `epistemicCumulativeBeliefFusion`/`weightedBeliefFusion`, **and a real production defect fix**
- `62b3ea7c` — hazard and degenerate-case coverage for `consensusAndCompromiseFusion`

Commit `904e4576` also fixes a real production defect this test-writing found, in
`weightedFusion`'s all-vacuous branch: the method's own comment documents that branch as falling back
to a "plain average" of the receiver's base rate against every other opinion's base rate, but the
shipped code only ever averaged the receiver's own base rate, dividing by the full opinion count
regardless of how many opinions actually contributed — silently dropping every other opinion's base
rate from the average. For three opinions with base rates `{0.5, 0.3, 0.4}`, the mathematically
correct plain average is `0.4`; the shipped (pre-fix) behaviour computed `0.1667`. The fix and its
regression test are both in `904e4576`.

---

## 2. The acceptance gate — both profiles, real output

Run from the repository root, per this review's own instructions:

```
cd use-core && mvn verify
```

and separately:

```
cd use-core && mvn verify -Pupstream-oracle
```

Both completed with `BUILD SUCCESS`. The floor check embedded in the build (`UpstreamOracleFloor`,
run at the Maven `verify` phase) printed an unqualified `PASS` for the correct mode in both runs, on
disk in each module's `target/upstream-oracle-floor.receipt`. Full, unedited console output for both
runs follows — nothing below is paraphrased, summarized, or trimmed.

A note on terminology, for anyone comparing this against `docs/port2/harness-contract.md` §0.1: that
section documents `scripts/upstream-oracle-gate.sh` as *the* committed acceptance gate (which prints a
`[gate]`-prefixed banner) and treats a hand-typed `mvn verify -Pupstream-oracle` as a thing to be
wary of automating around. This review's own Step 1 instructions specified the two hand-typed `mvn
verify` invocations shown above, run directly inside `use-core`, not the wrapper script — so what is
pasted below is genuinely `[floor] PASS` (the in-build floor check's own banner), not `[gate] PASS`
(the wrapper script's banner). The two are not the same invocation; see §3 below for what that means
this review did not attempt.

### 2.1 `mvn verify` (default profile)

```
[INFO] Scanning for projects...
[INFO] 
[INFO] ------------------------< org.tzi.use:use-core >------------------------
[INFO] Building use-core 7.5.0
[INFO]   from pom.xml
[INFO] --------------------------------[ jar ]---------------------------------
[INFO] 
[INFO] --- exec:3.5.0:exec (upstream-oracle-floor-stamp) @ use-core ---
[floor] initialize: requested profiles (none), declared in this reactor [upstream-oracle], allow-profiles (-Duse.floor.allowProfiles) (none)
[floor] wrote freshness stamp /home/xoruser/msc-4/use-msc2026/use-core/target/upstream-oracle-floor.stamp
[INFO] 
[INFO] --- merge:1.2.0:merge (merge-grammar-files) @ use-core ---
[INFO] 
[INFO] --- antlr3:3.5.3:antlr (antlr) @ use-core ---
[INFO] ANTLR: Processing source directory /home/xoruser/msc-4/use-msc2026/use-core/target/grammars
[INFO] 
[INFO] --- copy-rename:1.0:rename (move-antlr-parser-use) @ use-core ---
[INFO] Renamed /home/xoruser/msc-4/use-msc2026/use-core/target/generated-sources/antlr3/USELexer.java to /home/xoruser/msc-4/use-msc2026/use-core/target/generated-sources/antlr3/org/tzi/use/parser/use/USELexer.java
[INFO] Renamed /home/xoruser/msc-4/use-msc2026/use-core/target/generated-sources/antlr3/USEParser.java to /home/xoruser/msc-4/use-msc2026/use-core/target/generated-sources/antlr3/org/tzi/use/parser/use/USEParser.java
[INFO] Renamed /home/xoruser/msc-4/use-msc2026/use-core/target/generated-sources/antlr3/GeneratorLexer.java to /home/xoruser/msc-4/use-msc2026/use-core/target/generated-sources/antlr3/org/tzi/use/parser/generator/GeneratorLexer.java
[INFO] Renamed /home/xoruser/msc-4/use-msc2026/use-core/target/generated-sources/antlr3/GeneratorParser.java to /home/xoruser/msc-4/use-msc2026/use-core/target/generated-sources/antlr3/org/tzi/use/parser/generator/GeneratorParser.java
[INFO] Renamed /home/xoruser/msc-4/use-msc2026/use-core/target/generated-sources/antlr3/OCLLexer.java to /home/xoruser/msc-4/use-msc2026/use-core/target/generated-sources/antlr3/org/tzi/use/parser/ocl/OCLLexer.java
[INFO] Renamed /home/xoruser/msc-4/use-msc2026/use-core/target/generated-sources/antlr3/OCLParser.java to /home/xoruser/msc-4/use-msc2026/use-core/target/generated-sources/antlr3/org/tzi/use/parser/ocl/OCLParser.java
[INFO] Renamed /home/xoruser/msc-4/use-msc2026/use-core/target/generated-sources/antlr3/ShellCommandLexer.java to /home/xoruser/msc-4/use-msc2026/use-core/target/generated-sources/antlr3/org/tzi/use/parser/shell/ShellCommandLexer.java
[INFO] Renamed /home/xoruser/msc-4/use-msc2026/use-core/target/generated-sources/antlr3/ShellCommandParser.java to /home/xoruser/msc-4/use-msc2026/use-core/target/generated-sources/antlr3/org/tzi/use/parser/shell/ShellCommandParser.java
[INFO] Renamed /home/xoruser/msc-4/use-msc2026/use-core/target/generated-sources/antlr3/SoilLexer.java to /home/xoruser/msc-4/use-msc2026/use-core/target/generated-sources/antlr3/org/tzi/use/parser/soil/SoilLexer.java
[INFO] Renamed /home/xoruser/msc-4/use-msc2026/use-core/target/generated-sources/antlr3/SoilParser.java to /home/xoruser/msc-4/use-msc2026/use-core/target/generated-sources/antlr3/org/tzi/use/parser/soil/SoilParser.java
[INFO] Renamed /home/xoruser/msc-4/use-msc2026/use-core/target/generated-sources/antlr3/TestSuiteLexer.java to /home/xoruser/msc-4/use-msc2026/use-core/target/generated-sources/antlr3/org/tzi/use/parser/testsuite/TestSuiteLexer.java
[INFO] Renamed /home/xoruser/msc-4/use-msc2026/use-core/target/generated-sources/antlr3/TestSuiteParser.java to /home/xoruser/msc-4/use-msc2026/use-core/target/generated-sources/antlr3/org/tzi/use/parser/testsuite/TestSuiteParser.java
[INFO] 
[INFO] --- build-helper:3.6.0:add-source (add-antlr3-generated-source) @ use-core ---
[INFO] Source directory: /home/xoruser/msc-4/use-msc2026/use-core/target/generated-sources/antlr3 added.
[INFO] 
[INFO] --- resources:3.4.0:resources (default-resources) @ use-core ---
[INFO] Copying 512 resources from src/main/resources to target/classes
[INFO] 
[INFO] --- build-helper:3.6.0:add-test-source (add-test-source) @ use-core ---
[INFO] Test Source directory: /home/xoruser/msc-4/use-msc2026/use-core/src/it/java added.
[INFO] 
[INFO] --- compiler:3.15.0:compile (default-compile) @ use-core ---
[WARNING] *********************************************************************************************************************************************************************************************
[WARNING] * Required filename-based automodules detected: [antlr-runtime-3.5.3.jar, combinatoricslib-2.3.jar, vtd-xml-2.13.4.jar]. Please don't publish this project to a public artifact repository! *
[WARNING] *********************************************************************************************************************************************************************************************
[INFO] Recompiling the module because of changed source code.
[INFO] Compiling 605 source files with javac [debug target 21 module-path] to target/classes
[INFO] /home/xoruser/msc-4/use-msc2026/use-core/src/main/java/org/tzi/use/uml/ocl/type/MessageType.java: Some input files use or override a deprecated API.
[INFO] /home/xoruser/msc-4/use-msc2026/use-core/src/main/java/org/tzi/use/uml/ocl/type/MessageType.java: Recompile with -Xlint:deprecation for details.
[INFO] /home/xoruser/msc-4/use-msc2026/use-core/target/generated-sources/antlr3/org/tzi/use/parser/generator/GeneratorParser.java: Some input files use unchecked or unsafe operations.
[INFO] /home/xoruser/msc-4/use-msc2026/use-core/target/generated-sources/antlr3/org/tzi/use/parser/generator/GeneratorParser.java: Recompile with -Xlint:unchecked for details.
[INFO] 
[INFO] --- resources:3.4.0:testResources (default-testResources) @ use-core ---
[INFO] Copying 97 resources from src/test/resources to target/test-classes
[INFO] 
[INFO] --- compiler:3.15.0:testCompile (default-testCompile) @ use-core ---
[INFO] Recompiling the module because of changed dependency.
[INFO] Compiling 100 source files with javac [debug target 21 module-path] to target/test-classes
[INFO] /home/xoruser/msc-4/use-msc2026/use-core/src/test/java/org/tzi/use/uncertainty/differential/B7CorrectionsTest.java: /home/xoruser/msc-4/use-msc2026/use-core/src/test/java/org/tzi/use/uncertainty/differential/B7CorrectionsTest.java uses or overrides a deprecated API.
[INFO] /home/xoruser/msc-4/use-msc2026/use-core/src/test/java/org/tzi/use/uncertainty/differential/B7CorrectionsTest.java: Recompile with -Xlint:deprecation for details.
[INFO] 
[INFO] --- surefire:3.5.4:test (default-test) @ use-core ---
[INFO] Using auto detected provider org.apache.maven.surefire.junitplatform.JUnitPlatformProvider
[INFO] 
[INFO] -------------------------------------------------------
[INFO]  T E S T S
[INFO] -------------------------------------------------------
SLF4J(W): No SLF4J providers were found.
SLF4J(W): Defaulting to no-operation (NOP) logger implementation
SLF4J(W): See https://www.slf4j.org/codes.html#noProviders for further details.
[INFO] Running F-2: MathUtil.round saturated above 9.2e8
[INFO] Running the declared limit: NaN and the infinities
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.032 s -- in the declared limit: NaN and the infinities
[INFO] Running what the fix must not change
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.028 s -- in what the fix must not change
[INFO] Running the defect the fix removes
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.018 s -- in the defect the fix removes
[INFO] Tests run: 0, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.092 s -- in F-2: MathUtil.round saturated above 9.2e8
[INFO] Running B7 at the parser and literal-constant layer
[INFO] Running M-33: ASTUStringLiteral fell through to Object.toString()
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.038 s -- in M-33: ASTUStringLiteral fell through to Object.toString()
[INFO] Running M-32: ASTURealLiteral built two Expression graphs and installed the second
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.011 s -- in M-32: ASTURealLiteral built two Expression graphs and installed the second
[INFO] Running M-30: ExpConstUString had two unguarded operations
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.010 s -- in M-30: ExpConstUString had two unguarded operations
[INFO] Running M-29: an undefined UBoolean VALUE operand was silently accepted
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.007 s -- in M-29: an undefined UBoolean VALUE operand was silently accepted
[INFO] Tests run: 0, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.071 s -- in B7 at the parser and literal-constant layer
[INFO] Running org.tzi.use.parser.ocl.UncertainExpressionTypingTest
[INFO] Tests run: 8, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.120 s -- in org.tzi.use.parser.ocl.UncertainExpressionTypingTest
[INFO] Running org.tzi.use.parser.soil.IterationWarningTokenRotTest
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.047 s -- in org.tzi.use.parser.soil.IterationWarningTokenRotTest
[INFO] Running org.tzi.use.parser.uncertainty.USECompilerUncertaintyTest
-----------------------------------------------------------------
It's going to be executed 4 test files.
-----------------------------------------------------------------
File : UBooleanExpression.in
	Expression : 
		UBoolean(3 + 2, 1)
		-> Value must be Boolean

	Expression : 
		UBoolean(3 / 0, 1)
		-> Value must be Boolean

	Expression : 
		UBoolean(true or false, UReal(2, 3))
		-> Probability must be a Integer or Real

	Expression : 
		UBoolean(true and false, 3 / 0)
		-> null : OclVoid

	Expression : 
		UBoolean(true or false, 3 - 5)
		-> null : OclVoid

	Expression : 
		UBoolean(true or false, 23 * 3)
		-> null : OclVoid

	Expression : 
		UBoolean(true and false, 1 - 0.2)
		-> UBoolean(true, 0.2) : UBoolean

	Expression : 
		UBoolean(true or false, 1 - 0.4)
		-> UBoolean(true, 0.6) : UBoolean

	Expression : 
		UBoolean(false, 1 - 0.1)
		-> UBoolean(true, 0.1) : UBoolean

	Expression : 
		UBoolean(true or false, 0.65)
		-> UBoolean(true, 0.65) : UBoolean

	Expression : 
		UBoolean(false, 0.42)
		-> UBoolean(true, 0.58) : UBoolean

	Expression : 
		UBoolean(false, 0.5) and UBoolean(false, 0.2)
		-> UBoolean(true, 0.4) : UBoolean

	Expression : 
		UBoolean(false, 0.9) and UBoolean(true, 0.8)
		-> UBoolean(true, 0.08) : UBoolean

	Expression : 
		UBoolean(false, 0.55) and UBoolean(true, 0.49)
		-> UBoolean(true, 0.22) : UBoolean

	Expression : 
		true and false
		-> false : Boolean

	Expression : 
		false and UBoolean(false, 0.49)
		-> UBoolean(true, 0.0) : UBoolean

	Expression : 
		UBoolean(false, 0.79) and false
		-> UBoolean(true, 0.0) : UBoolean

	Expression : 
		UBoolean(false, 0.79) and true
		-> UBoolean(true, 0.21) : UBoolean

	Expression : 
		UBoolean(true, 0.79) and true
		-> UBoolean(true, 0.79) : UBoolean

	Expression : 
		Undefined and Undefined
		-> null : OclVoid

	Expression : 
		UBoolean(true, 0.9) and Undefined
		-> null : OclVoid

	Expression : 
		true and Undefined
		-> null : OclVoid

	Expression : 
		Undefined and UBoolean(false, 0.9)
		-> null : OclVoid

	Expression : 
		Undefined and false
		-> false : Boolean

	Expression : 
		Undefined and UBoolean(true, 0)
		-> UBoolean(true, 0.0) : UBoolean

	Expression : 
		UBoolean(false, 1) and Undefined
		-> UBoolean(true, 0.0) : UBoolean

	Expression : 
		( UBoolean(true, 0.3) and UBoolean(true, 0.8) ).equals( (UBoolean(true, 0.8) and UBoolean(true, 0.3)) )
		-> true : Boolean

	Expression : 
		( true and UBoolean(true, 0.8) ).equals( (UBoolean(true, 0.8) and true) )
		-> true : Boolean

	Expression : 
		( Undefined and UBoolean(true, 0.8) ).equals( (UBoolean(true, 0.8) and Undefined) )
		-> true : Boolean

	Expression : 
		( UBoolean(true, 0.4) and (UBoolean(false, 0.55) and UBoolean(true, 0.8)) ).equals(( (UBoolean(true, 0.4) and UBoolean(false, 0.55)) and UBoolean(true, 0.8) ))
		-> true : Boolean

	Expression : 
		( false and (UBoolean(false, 0.55) and UBoolean(true, 0.8)) ).equals(( (false and UBoolean(false, 0.55)) and UBoolean(true, 0.8) ))
		-> true : Boolean

	Expression : 
		( true and (Undefined and UBoolean(true, 0.8)) ).equals(( (true and Undefined) and UBoolean(true, 0.8) ))
		-> true : Boolean

	Expression : 
		( UBoolean(false, 0.4) and UBoolean(true, 1) ).equals( UBoolean(false, 0.4) )
		-> true : Boolean

	Expression : 
		( UBoolean(false, 0.4) and true ).equals( UBoolean(false, 0.4) )
		-> true : Boolean

	Expression : 
		UBoolean(false, 0.45) or UBoolean(false, 0.76)
		-> UBoolean(true, 0.658) : UBoolean

	Expression : 
		UBoolean(false, 0.45) or UBoolean(true, 0.37)
		-> UBoolean(true, 0.717) : UBoolean

	Expression : 
		UBoolean(true, 0.45) or UBoolean(true, 0.76)
		-> UBoolean(true, 0.868) : UBoolean

	Expression : 
		true or false
		-> true : Boolean

	Expression : 
		false or UBoolean(false, 0.49)
		-> UBoolean(true, 0.51) : UBoolean

	Expression : 
		UBoolean(false, 0.79) or false
		-> UBoolean(true, 0.21) : UBoolean

	Expression : 
		UBoolean(true, 0.79) or true
		-> UBoolean(true, 1.0) : UBoolean

	Expression : 
		Undefined or Undefined
		-> null : OclVoid

	Expression : 
		UBoolean(true, 0.9) or Undefined
		-> null : OclVoid

	Expression : 
		true or Undefined
		-> true : Boolean

	Expression : 
		Undefined or UBoolean(false, 0.9)
		-> null : OclVoid

	Expression : 
		Undefined or false
		-> null : OclVoid

	Expression : 
		Undefined or UBoolean(true, 1)
		-> UBoolean(true, 1.0) : UBoolean

	Expression : 
		UBoolean(false, 1) or Undefined
		-> null : OclVoid

	Expression : 
		( UBoolean(true, 0.5) or UBoolean(true, 0.16) ).equals( UBoolean(true, 0.16) or UBoolean(true, 0.5) )
		-> true : Boolean

	Expression : 
		( false or UBoolean(true, 0.16) ).equals( UBoolean(true, 0.16) or false )
		-> true : Boolean

	Expression : 
		( Undefined or UBoolean(true, 0.16) ).equals( UBoolean(true, 0.16) or Undefined )
		-> true : Boolean

	Expression : 
		( UBoolean(false, 0.1) or (UBoolean(false, 0.4) or UBoolean(true, 0.6)) ).equals ( (UBoolean(false, 0.1) or UBoolean(false, 0.4)) or UBoolean(true, 0.6) )
		-> true : Boolean

	Expression : 
		( true or (UBoolean(false, 0.4) or UBoolean(true, 0.6)) ).equals ( (true or UBoolean(false, 0.4)) or UBoolean(true, 0.6) )
		-> true : Boolean

	Expression : 
		( true or (Undefined or UBoolean(true, 0.6)) ).equals ( (true or Undefined) or UBoolean(true, 0.6) )
		-> true : Boolean

	Expression : 
		( UBoolean(true, 0.4) or UBoolean(true, 0) ).equals( UBoolean(true, 0.4) )
		-> true : Boolean

	Expression : 
		( UBoolean(false, 0.4) or UBoolean(true, 0) ).equals( UBoolean(false, 0.4) )
		-> true : Boolean

	Expression : 
		( true or UBoolean(true, 0) ).equals( UBoolean(true, 1) )
		-> true : Boolean

	Expression : 
		( Undefined or UBoolean(true, 0) ).equals( Undefined )
		-> true : Boolean

	Expression : 
		not Undefined
		-> null : OclVoid

	Expression : 
		not UBoolean(true, 0)
		-> UBoolean(true, 1.0) : UBoolean

	Expression : 
		not UBoolean(true, 1)
		-> UBoolean(true, 0.0) : UBoolean

	Expression : 
		not UBoolean(true, 0.2)
		-> UBoolean(true, 0.8) : UBoolean

	Expression : 
		not UBoolean(true, 0.5)
		-> UBoolean(true, 0.5) : UBoolean

	Expression : 
		not UBoolean(true, 0.8)
		-> UBoolean(true, 0.2) : UBoolean

	Expression : 
		not UBoolean(false, 0)
		-> UBoolean(true, 0.0) : UBoolean

	Expression : 
		not UBoolean(false, 1)
		-> UBoolean(true, 1.0) : UBoolean

	Expression : 
		not UBoolean(false, 0.2)
		-> UBoolean(true, 0.2) : UBoolean

	Expression : 
		not UBoolean(false, 0.5)
		-> UBoolean(true, 0.5) : UBoolean

	Expression : 
		not UBoolean(false, 0.8)
		-> UBoolean(true, 0.8) : UBoolean

	Expression : 
		( not ( not UBoolean(false, 0.2)) ).equals( UBoolean(false, 0.2) )
		-> true : Boolean

	Expression : 
		( not ( not UBoolean(true, 0.2)) ).equals( UBoolean(true, 0.2) )
		-> true : Boolean

	Expression : 
		( not (UBoolean(true, 0.36) or UBoolean(true, 0.39)) ).equals( (not UBoolean(true, 0.36)) and (not UBoolean(true, 0.39)) )
		-> true : Boolean

	Expression : 
		( not (UBoolean(false, 0.8) or UBoolean(true, 0.39)) ).equals( (not UBoolean(false, 0.8)) and (not UBoolean(true, 0.39)) )
		-> true : Boolean

	Expression : 
		( not (UBoolean(false, 0.04) or UBoolean(false, 0.9)) ).equals( (not UBoolean(false, 0.04)) and (not UBoolean(false, 0.9)) )
		-> true : Boolean

	Expression : 
		( not (UBoolean(true, 0.36) and UBoolean(true, 0.39)) ).equals( (not UBoolean(true, 0.36)) or (not UBoolean(true, 0.39)) )
		-> true : Boolean

	Expression : 
		( not (UBoolean(false, 0.8) and UBoolean(true, 0.39)) ).equals( (not UBoolean(false, 0.8)) or (not UBoolean(true, 0.39)) )
		-> true : Boolean

	Expression : 
		( not (UBoolean(false, 0.04) and UBoolean(false, 0.9)) ).equals( (not UBoolean(false, 0.04)) or (not UBoolean(false, 0.9)) )
		-> true : Boolean

	Expression : 
		UBoolean(false, 0.4) xor UBoolean(false, 0.2)
		-> UBoolean(true, 0.2) : UBoolean

	Expression : 
		UBoolean(false, 0.2) xor UBoolean(true, 0.3)
		-> UBoolean(true, 0.5) : UBoolean

	Expression : 
		UBoolean(true, 0.1) xor UBoolean(true, 0.1)
		-> UBoolean(true, 0.0) : UBoolean

	Expression : 
		UBoolean(false, 0) xor UBoolean(false, 1)
		-> UBoolean(true, 1.0) : UBoolean

	Expression : 
		true xor false
		-> true : Boolean

	Expression : 
		false xor UBoolean(false, 0.5)
		-> UBoolean(true, 0.5) : UBoolean

	Expression : 
		UBoolean(false, 0.2) xor false
		-> UBoolean(true, 0.8) : UBoolean

	Expression : 
		UBoolean(false, 0.6) xor true
		-> UBoolean(true, 0.6) : UBoolean

	Expression : 
		UBoolean(true,  0.3) xor true
		-> UBoolean(true, 0.7) : UBoolean

	Expression : 
		UBoolean(true,  0.0) xor true
		-> UBoolean(true, 1.0) : UBoolean

	Expression : 
		Undefined xor Undefined
		-> null : OclVoid

	Expression : 
		UBoolean(true, 0.5) xor Undefined
		-> null : OclVoid

	Expression : 
		Undefined xor UBoolean(false, 0.4)
		-> null : OclVoid

	Expression : 
		UBoolean(false, 0.2).equivalent(UBoolean(false, 0.4))
		-> UBoolean(true, 0.8) : UBoolean

	Expression : 
		UBoolean(false, 0.8).equivalent(UBoolean(true, 0.5))
		-> UBoolean(true, 0.7) : UBoolean

	Expression : 
		UBoolean(true, 0.34).equivalent(UBoolean(true, 0.56))
		-> UBoolean(true, 0.78) : UBoolean

	Expression : 
		true.equivalent(false)
		-> false : Boolean

	Expression : 
		true.equivalent(true)
		-> true : Boolean

	Expression : 
		false.equivalent(true)
		-> false : Boolean

	Expression : 
		false.equivalent(false)
		-> true : Boolean

	Expression : 
		false.equivalent(UBoolean(false, 0.49))
		-> UBoolean(true, 0.49) : UBoolean

	Expression : 
		UBoolean(false, 0.79).equivalent(false)
		-> UBoolean(true, 0.79) : UBoolean

	Expression : 
		UBoolean(true, 0.79).equivalent( true )
		-> UBoolean(true, 0.79) : UBoolean

	Expression : 
		UBoolean(true, 0.2).value()
		-> true : Boolean

	Expression : 
		UBoolean(true, 0.55).value()
		-> true : Boolean

	Expression : 
		UBoolean(true, 0.9).value()
		-> true : Boolean

	Expression : 
		UBoolean(true, 0).confidence()
		-> 0.0 : Real

	Expression : 
		UBoolean(true, 0.5).confidence()
		-> 0.5 : Real

	Expression : 
		UBoolean(true, 1).confidence()
		-> 1.0 : Real

	Expression : 
		UBoolean(false, 0.2) = UBoolean(false, 0.4)
		-> UBoolean(true, 0.8) : UBoolean

	Expression : 
		UBoolean(false, 0.8) = UBoolean(true, 0.5)
		-> UBoolean(true, 0.7) : UBoolean

	Expression : 
		UBoolean(true, 0.34) = UBoolean(true, 0.56)
		-> UBoolean(true, 0.78) : UBoolean

	Expression : 
		false = UBoolean(false, 0.49)
		-> UBoolean(true, 0.49) : UBoolean

	Expression : 
		UBoolean(false, 0.79) = false
		-> UBoolean(true, 0.79) : UBoolean

	Expression : 
		UBoolean(true, 0.79) = true
		-> UBoolean(true, 0.79) : UBoolean

	Expression : 
		UBoolean(true, 0.2) = Undefined
		-> false : Boolean

	Expression : 
		UBoolean(true, 0.2) = null
		-> false : Boolean

	Expression : 
		UBoolean(true, 0).toBoolean()
		-> false : Boolean

	Expression : 
		UBoolean(true, 0.49).toBoolean()
		-> false : Boolean

	Expression : 
		UBoolean(true, 0.5).toBoolean()
		-> true : Boolean

	Expression : 
		UBoolean(true, 1).toBoolean()
		-> true : Boolean

-----------------------------------------------------------------
File : UCollectionOperations.in
	Expression : 
		Set{UReal(2, 0.5), 1, 2.5, 3.2, UReal(3.5, 0.25)}->sum()
		-> UReal(12.2, 0.5590169944) : UReal

	Expression : 
		Sequence{UReal(52, 0.5), 3.2, 2, UReal(-53, 20), UReal(20, 5)}->sum()
		-> UReal(24.2, 20.6215906273) : UReal

	Expression : 
		Set{1, 2, UReal(2,5)}->forAll(e | e >= 1)
		-> UBoolean(true, 0.579) : UBoolean

	Expression : 
		Set{UReal(1, 0.5),UReal(1,0.75), 1.2}->forAll(e | e >= 1.2)
		-> UBoolean(true, 0.136) : UBoolean

	Expression : 
		Set{UReal(1, 0.5), 3}->forAll(e | e < 0)
		-> UBoolean(true, 0.0) : UBoolean

	Expression : 
		( Set{1, UReal(1,0.78)}->forAll(e | e > 0) ).equals( (1 > 0) and (UReal(1, 0.78) > 0) )
		-> true : Boolean

	Expression : 
		( Set{1, UReal(1,0.78)}->forAll(e | e < 0) ).equals( (1 < 0) and (UReal(1, 0.78) < 0) )
		-> true : Boolean

	Expression : 
		Set{0, 1, UReal(3,0.5)}->exists(e | e = 0)
		-> UBoolean(true, 1.0) : UBoolean

	Expression : 
		Set{0, 1, UReal(3, 0.5)}->exists(e | e >= 3)
		-> UBoolean(true, 0.5) : UBoolean

	Expression : 
		( Set{1, UReal(1,0.2)}->exists(e | e >= 1.1)).equals( (1 >= 1.1) or (UReal(1,0.2) >= 1.1))
		-> true : Boolean

	Expression : 
		( Set{1, UReal(1,0.1)}->exists(a,b| a <> b and a = b) ).equals( (1 <> 1 and 1 = 1) or (1 <> UReal(1,0.1) and 1 = UReal(1,0.1)))
		-> true : Boolean

	Expression : 
		Set{UReal(2, 0.5), 1, 2.5, 3.2, UReal(3.5, 0.25)}->includes(2.5)
		-> UBoolean(true, 1.0) : UBoolean

	Expression : 
		Set{UReal(2, 0.5), 1, 2.5, 3.2, UReal(3.5, 0.25)}->includes(UReal(2, 0.2))
		-> UBoolean(true, 0.585) : UBoolean

	Expression : 
		Set{UReal(2, 0.35), UReal(2, 0.3)}->includes(UReal(2, 0.29))
		-> UBoolean(true, 0.984) : UBoolean

	Expression : 
		Set{}->includes(UReal(2, 3))
		-> UBoolean(true, 0.0) : UBoolean

	Expression : 
		Set{Undefined}->includes(UReal(2, 3))
		-> UBoolean(true, 0.0) : UBoolean

	Expression : 
		Set{}->includesAll(Set{UReal(2, 3)})
		-> UBoolean(true, 0.0) : UBoolean

	Expression : 
		Set{UReal(2, 0.5), 1, 2.5, 3.2, UReal(3.5, 0.25)}->includesAll(Set{2.5, UReal(3.5, 0.15)})
		-> UBoolean(true, 0.758) : UBoolean

	Expression : 
		Set{UReal(2, 0.5), 1, 2.5, 3.2, UReal(3.5, 0.25)}->includesAll(Set{UReal(2, 0.5), 1, 2.5, 3.2, UReal(3.5, 0.25)})
		-> UBoolean(true, 1.0) : UBoolean

	Expression : 
		Set{UReal(2, 0.3)}->includesAll(Set{1, 2, 3})
		-> UBoolean(true, 0.0) : UBoolean

	Expression : 
		let A = Set{UReal(2, 0.5), 1, 2.5, 3.2, UReal(3.5, 0.25)} in 
let B = Set{UReal(2, 0.5), 1, 3.2} in (B->forAll(e | A->includes(e))).equals(A->includesAll(B))
		-> true : Boolean

	Expression : 
		let A = Set{UReal(2, 0.5), 1, 2.5, 5.3, UReal(3.5, 0.25)} in 
let B = Set{UReal(2, 0.5), 1, 3.2} in (B->forAll(e | A->includes(e))).equals(A->includesAll(B))
		-> true : Boolean

	Expression : 
		let A = Set{UReal(2, 0.5), 1, 2.5, 3.2, UReal(3.5, 0.25)} in 
let B = Set{UReal(2, 0.15), UReal(3.4, 0.25)} in (B->forAll(e | A->includes(e))).equals(A->includesAll(B))
		-> true : Boolean

	Expression : 
		Set{}->excludes(UReal(1, 2))
		-> UBoolean(true, 1.0) : UBoolean

	Expression : 
		Set{UReal(2, 0.5), 1, 2.5, 3.2, UReal(3.5, 0.25)}->excludes(UReal(59,2))
		-> UBoolean(true, 1.0) : UBoolean

	Expression : 
		Set{UReal(2, 0.5), 1, 2.5, 3.2, UReal(3.5, 0.25)}->excludes(UReal(3.5, 0.25))
		-> UBoolean(true, 0.0) : UBoolean

	Expression : 
		let A = Set{UReal(2, 0.5), 1, 2.5, 3.2, UReal(3.5, 0.25)} in 
let B = UReal(3, 2) in ( A->forAll(e | e <> B) ).equals( A->excludes(B))
		-> true : Boolean

	Expression : 
		let A = Set{UReal(2, 0.5), 1, 2.5, 3.2, UReal(3.5, 0.25)} in 
let B = UReal(0, 2) in ( A->forAll(e | e <> B) ).equals( A->excludes(B))
		-> true : Boolean

	Expression : 
		let A = Set{UReal(2, 0.5), 1, 2.5, 3.2, UReal(3.5, 0.25)} in 
let B = UReal(59, 2) in ( A->forAll(e | e <> B) ).equals( A->excludes(B))
		-> true : Boolean

	Expression : 
		Set{}->excludesAll(Set{UReal(2, 3)})
		-> UBoolean(true, 1.0) : UBoolean

	Expression : 
		Set{UReal(2, 0.5), 1, 2.5, 3.2, UReal(3.5, 0.25)}->excludesAll(Set{UReal(59,3),UReal(-310,9)})
		-> UBoolean(true, 1.0) : UBoolean

	Expression : 
		Set{UReal(2, 0.5), 1, 2.5, 3.2, UReal(3.5, 0.25)}->excludesAll(Set{UReal(3.5, 0.25)})
		-> UBoolean(true, 0.0) : UBoolean

	Expression : 
		let A = Set{UReal(2, 0.5), 1, 2.5, 3.2, UReal(3.5, 0.25)} in 
let B = Set{UReal(2.75, 1), 1} in 
( A->excludesAll(B) ).equals( B->forAll(b | A->excludes(b)) )
		-> true : Boolean

	Expression : 
		let A = Set{UReal(2, 0.5), 1, 2.5, 3.2, UReal(3.5, 0.25)} in 
let B = Set{UReal(1, 3), UReal(5, 2)} in 
( A->excludesAll(B) ).equals( B->forAll(b | A->excludes(b)) )
		-> true : Boolean

	Expression : 
		let A = Set{UReal(2, 0.5), 1, 2.5, 3.2, UReal(3.5, 0.25)} in let B = Set{UReal(-11, 3), UReal(55, 2)} in ( A->excludesAll(B) ).equals( B->forAll(b | A->excludes(b)) )
		-> true : Boolean

	Expression : 
		Set{UReal(2, 0.5), 2.5, 3.2, 1, UReal(3, 0.25)}->uSelect(e | e >= 2)
		-> Set{2.5,UReal(3.0, 0.25),3.2} : Set(UReal)

	Expression : 
		Set{UReal(2, 0.5), 2.5, 3.2, 1, UReal(3, 0.25)}->uSelect(e | e <= 2)
		-> Set{1,UReal(2.0, 0.5)} : Set(UReal)

	Expression : 
		let A = Set{2, 3, UReal(3, 0.5)} in (A->iterate(v; acc : Set(UReal) = Set {} | if (v > 2).toBoolean() then acc->including(v) else acc endif) )->equals(A->uSelect(e|e>2))
		-> true : Boolean

	Expression : 
		let A = Sequence{UReal(-3,5), 2.3, UReal(2,3), UReal(67,3), -50} in 
(A->iterate(v; acc : Sequence(UReal) = Sequence {} | if (v > 2).toBoolean() then acc->including(v) else acc endif) )->equals(A->uSelect(e|e>2))
		-> true : Boolean

	Expression : 
		let A = Bag{2.3, UReal(2,3), UReal(67,3)} in 
(A->iterate(v; acc : Bag(UReal) = Bag {} | if (v > 2).toBoolean() then acc->including(v) else acc endif) )->equals(A->uSelect(e|e>2))
		-> true : Boolean

	Expression : 
		Set{UReal(2, 0.5), 2.5, 3.2, 1, UReal(3, 0.25)}->uSelectC(e | e >= 2, 0.49)
		-> Set{2.5,UReal(3.0, 0.25),3.2,UReal(2.0, 0.5)} : Set(UReal)

	Expression : 
		Set{UReal(2, 0.5), 2.5, 3.2, 1, UReal(3, 0.25)}->uSelectC(e | e <= 2, 0.49)
		-> Set{1,UReal(2.0, 0.5)} : Set(UReal)

	Expression : 
		let A = Set{UReal(2, 0.5), 2.5, 3.2, 1, UReal(3, 0.25)} in let C = 0.7 in 
(A->iterate (v ; acc : Set(UReal) = Set {} | if (v >= 2). toBooleanC (C) then acc -> including (v) else acc endif ) )->equals( A->uSelectC(e | e >= 2, C) )
		-> true : Boolean

	Expression : 
		let A = Set{UReal(52, 0.5), 3.2, 2, UReal(-53, 20), UReal(20, 5)} in let C = 0.45 in 
(A->iterate (v ; acc : Set(UReal) = Set {} | if (v >= 2). toBooleanC (C) then acc -> including (v) else acc endif ) )->equals( A->uSelectC(e | e >= 2, C) )
		-> true : Boolean

-----------------------------------------------------------------
File : UIntegerExpression.in
	Expression : 
		UInteger(-5, 0.0)
		-> UInteger(-5, 0.0) : UInteger

	Expression : 
		UInteger(-5, 0.5)
		-> UInteger(-5, 0.5) : UInteger

	Expression : 
		UInteger(-5, -0.5)
		-> UInteger(-5, 0.5) : UInteger

	Expression : 
		UInteger(-5, 2)
		-> UInteger(-5, 2.0) : UInteger

	Expression : 
		UInteger(-5, -5)
		-> UInteger(-5, 5.0) : UInteger

	Expression : 
		UInteger(3, 39)
		-> UInteger(3, 39.0) : UInteger

	Expression : 
		UInteger(0, 0)
		-> UInteger(0, 0.0) : UInteger

	Expression : 
		UInteger(Undefined, Undefined)
		-> null : OclVoid

	Expression : 
		UInteger(Undefined, 0.34)
		-> null : OclVoid

	Expression : 
		UInteger(5, Undefined)
		-> null : OclVoid

	Expression : 
		UInteger(3 + 4*2-3, UReal(4, 3.3).value() + 1)
		-> UInteger(8, 5.0) : UInteger

	Expression : 
		UInteger(3, 3.5).value()
		-> 3 : Integer

	Expression : 
		UInteger(0, 2.3).value()
		-> 0 : Integer

	Expression : 
		UInteger(-5, 0.2).value()
		-> -5 : Integer

	Expression : 
		UInteger(Undefined, Undefined).value()
		-> null : OclVoid

	Expression : 
		UInteger(3, Undefined).value()
		-> null : OclVoid

	Expression : 
		UInteger(Undefined, 3).value()
		-> null : OclVoid

	Expression : 
		UInteger(3, 5).setValue(2)
		-> UInteger(2, 5.0) : UInteger

	Expression : 
		UInteger(-2, 4).setValue(0)
		-> UInteger(0, 4.0) : UInteger

	Expression : 
		UInteger(0, 3).setValue(-55)
		-> UInteger(-55, 3.0) : UInteger

	Expression : 
		UInteger(3, 3.5).uncertainty()
		-> 3.5 : Real

	Expression : 
		UInteger(0, 0).uncertainty()
		-> 0.0 : Real

	Expression : 
		UInteger(-5, 0.2).uncertainty()
		-> 0.2 : Real

	Expression : 
		UInteger(Undefined, Undefined).uncertainty()
		-> null : OclVoid

	Expression : 
		UInteger(3, Undefined).uncertainty()
		-> null : OclVoid

	Expression : 
		UInteger(Undefined, 3).uncertainty()
		-> null : OclVoid

	Expression : 
		UInteger(0, 3).setUncertainty(-5)
		-> UInteger(0, 5.0) : UInteger

	Expression : 
		UInteger(5, 2).setUncertainty(0)
		-> UInteger(5, 0.0) : UInteger

	Expression : 
		UInteger(0, 3).setUncertainty(5)
		-> UInteger(0, 5.0) : UInteger

	Expression : 
		UInteger(0, 3).setUncertainty(5.3)
		-> UInteger(0, 5.3) : UInteger

	Expression : 
		UInteger(0, 3).setUncertainty(0.2)
		-> UInteger(0, 0.2) : UInteger

	Expression : 
		UInteger(0, 3).setUncertainty(-0.3)
		-> UInteger(0, 0.3) : UInteger

	Expression : 
		UInteger(0, 3).setUncertainty(0.0)
		-> UInteger(0, 0.0) : UInteger

	Expression : 
		UInteger(3, 0.5).toUReal()
		-> UReal(3.0, 0.5) : UReal

	Expression : 
		UInteger(3, -0.5).toUReal()
		-> UReal(3.0, 0.5) : UReal

	Expression : 
		UInteger(0, 0).toUReal()
		-> UReal(0.0, 0.0) : UReal

	Expression : 
		UInteger(-53, 5).toUReal()
		-> UReal(-53.0, 5.0) : UReal

	Expression : 
		UInteger(3, 0.3).toInteger()
		-> 3 : Integer

	Expression : 
		UInteger(0, 4).toInteger()
		-> 0 : Integer

	Expression : 
		UInteger(-5, 5).toInteger()
		-> -5 : Integer

	Expression : 
		UInteger(3, 0.3).toReal()
		-> 3.0 : Real

	Expression : 
		UInteger(0, 0.5).toReal()
		-> 0.0 : Real

	Expression : 
		UInteger(-3, -0.5).toReal()
		-> -3.0 : Real

	Expression : 
		UInteger(5, 0.3).toString()
		-> 'UInteger(5, 0.3)' : String

	Expression : 
		UInteger(5, -0.3).toString()
		-> 'UInteger(5, 0.3)' : String

	Expression : 
		UInteger(-5, 0.3).toString()
		-> 'UInteger(-5, 0.3)' : String

	Expression : 
		UInteger(2, 3).abs()
		-> UInteger(2, 3.0) : UInteger

	Expression : 
		UInteger(0, 3).abs()
		-> UInteger(0, 3.0) : UInteger

	Expression : 
		UInteger(-2, 3).abs()
		-> UInteger(2, 3.0) : UInteger

	Expression : 
		UInteger(-3, 2.3).sqrt()
		-> null : OclVoid

	Expression : 
		UInteger(0, 0.0).sqrt()
		-> UInteger(0, 0.0) : UInteger

	Expression : 
		UInteger(4, 0.0).sqrt()
		-> UInteger(2, 0.0) : UInteger

	Expression : 
		UInteger(4, 2).sqrt()
		-> UInteger(2, 0.5) : UInteger

	Expression : 
		UInteger(0, 0).power(0)
		-> null : OclVoid

	Expression : 
		UInteger(0, 0).power(3)
		-> UInteger(0, 0.0) : UInteger

	Expression : 
		UInteger(0, 0).power(-2)
		-> null : OclVoid

	Expression : 
		UInteger(0, 0).power(3.5)
		-> UInteger(0, 0.0) : UInteger

	Expression : 
		UInteger(0, 2).power(0)
		-> null : OclVoid

	Expression : 
		UInteger(0, 4).power(3)
		-> UInteger(0, 0.0) : UInteger

	Expression : 
		UInteger(0, 3).power(-3)
		-> null : OclVoid

	Expression : 
		UInteger(0, 1).power(3.5)
		-> UInteger(0, 0.0) : UInteger

	Expression : 
		UInteger(3, 0).power(0)
		-> UInteger(1, 0.0) : UInteger

	Expression : 
		UInteger(2, 0).power(3)
		-> UInteger(8, 0.0) : UInteger

	Expression : 
		UInteger(4, 0).power(-2)
		-> UInteger(0, 0.0625) : UInteger

	Expression : 
		UInteger(4, 0).power(1.5)
		-> UInteger(8, 0.0) : UInteger

	Expression : 
		UInteger(2, 4).power(4)
		-> UInteger(16, 128.0) : UInteger

	Expression : 
		UInteger(1, 3).power(-2)
		-> UInteger(1, 6.0) : UInteger

	Expression : 
		UInteger(1, 2).power(0.25)
		-> UInteger(1, 0.5) : UInteger

	Expression : 
		UInteger(3, 2.3).neg()
		-> UInteger(-3, 2.3) : UInteger

	Expression : 
		UInteger(0, 2.3).neg()
		-> UInteger(0, 2.3) : UInteger

	Expression : 
		UInteger(-3, 2.3).neg()
		-> UInteger(3, 2.3) : UInteger

	Expression : 
		UInteger(-9, 0) + UInteger(-9, 0)
		-> UInteger(-18, 0.0) : UInteger

	Expression : 
		UInteger(-7, 0) + UInteger(-7, 8)
		-> UInteger(-14, 8.0) : UInteger

	Expression : 
		UInteger(-10, 0) + UInteger(0, 0)
		-> UInteger(-10, 0.0) : UInteger

	Expression : 
		UInteger(-8, 0) + UInteger(3, 5)
		-> UInteger(-5, 5.0) : UInteger

	Expression : 
		UInteger(-6, 8) + UInteger(-6, 0)
		-> UInteger(-12, 8.0) : UInteger

	Expression : 
		UInteger(-9, 3) + UInteger(-9, 4)
		-> UInteger(-18, 5.0) : UInteger

	Expression : 
		UInteger(-9, 8) + UInteger(4, 0)
		-> UInteger(-5, 8.0) : UInteger

	Expression : 
		UInteger(-3, 3) + UInteger(4, 4)
		-> UInteger(1, 5.0) : UInteger

	Expression : 
		UInteger(0, 0) + UInteger(0, 0)
		-> UInteger(0, 0.0) : UInteger

	Expression : 
		UInteger(0, 0) + UInteger(0, 0)
		-> UInteger(0, 0.0) : UInteger

	Expression : 
		UInteger(0, 0) + UInteger(9, 0)
		-> UInteger(9, 0.0) : UInteger

	Expression : 
		UInteger(0, 0) + UInteger(8, 4)
		-> UInteger(8, 4.0) : UInteger

	Expression : 
		UInteger(0, 8) + UInteger(0, 0)
		-> UInteger(0, 8.0) : UInteger

	Expression : 
		UInteger(0, 3) + UInteger(0, 4)
		-> UInteger(0, 5.0) : UInteger

	Expression : 
		UInteger(0, 6) + UInteger(8, 0)
		-> UInteger(8, 6.0) : UInteger

	Expression : 
		UInteger(0, 3) + UInteger(5, 4)
		-> UInteger(5, 5.0) : UInteger

	Expression : 
		UInteger(9, 0) + UInteger(9, 0)
		-> UInteger(18, 0.0) : UInteger

	Expression : 
		UInteger(7, 0) + UInteger(7, 0)
		-> UInteger(14, 0.0) : UInteger

	Expression : 
		UInteger(10, 0) + UInteger(8, 0)
		-> UInteger(18, 0.0) : UInteger

	Expression : 
		UInteger(8, 0) + UInteger(8, 7)
		-> UInteger(16, 7.0) : UInteger

	Expression : 
		UInteger(6, 5) + UInteger(6, 0)
		-> UInteger(12, 5.0) : UInteger

	Expression : 
		UInteger(9, 3) + UInteger(9, 4)
		-> UInteger(18, 5.0) : UInteger

	Expression : 
		UInteger(9, 1) + UInteger(8, 0)
		-> UInteger(17, 1.0) : UInteger

	Expression : 
		UInteger(3, 3) + UInteger(4, 4)
		-> UInteger(7, 5.0) : UInteger

	Expression : 
		UInteger(-9, 0) + UReal(-9, 0)
		-> UReal(-18.0, 0.0) : UReal

	Expression : 
		UInteger(-7, 0) + UReal(-7, 8)
		-> UReal(-14.0, 8.0) : UReal

	Expression : 
		UInteger(-10, 0) + UReal(0, 0)
		-> UReal(-10.0, 0.0) : UReal

	Expression : 
		UInteger(-8, 0) + UReal(3, 5)
		-> UReal(-5.0, 5.0) : UReal

	Expression : 
		UInteger(-6, 8) + UReal(-6, 0)
		-> UReal(-12.0, 8.0) : UReal

	Expression : 
		UInteger(-9, 3) + UReal(-9, 4)
		-> UReal(-18.0, 5.0) : UReal

	Expression : 
		UInteger(-9, 8) + UReal(4, 0)
		-> UReal(-5.0, 8.0) : UReal

	Expression : 
		UInteger(-3, 3) + UReal(4, 4)
		-> UReal(1.0, 5.0) : UReal

	Expression : 
		UInteger(0, 0) + UReal(0, 0)
		-> UReal(0.0, 0.0) : UReal

	Expression : 
		UInteger(0, 0) + UReal(0, 0)
		-> UReal(0.0, 0.0) : UReal

	Expression : 
		UInteger(0, 0) + UReal(9, 0)
		-> UReal(9.0, 0.0) : UReal

	Expression : 
		UInteger(0, 0) + UReal(8, 4)
		-> UReal(8.0, 4.0) : UReal

	Expression : 
		UInteger(0, 8) + UReal(0, 0)
		-> UReal(0.0, 8.0) : UReal

	Expression : 
		UInteger(0, 3) + UReal(0, 4)
		-> UReal(0.0, 5.0) : UReal

	Expression : 
		UInteger(0, 6) + UReal(8, 0)
		-> UReal(8.0, 6.0) : UReal

	Expression : 
		UInteger(0, 3) + UReal(5, 4)
		-> UReal(5.0, 5.0) : UReal

	Expression : 
		UInteger(9, 0) + UReal(9, 0)
		-> UReal(18.0, 0.0) : UReal

	Expression : 
		UInteger(7, 0) + UReal(7, 0)
		-> UReal(14.0, 0.0) : UReal

	Expression : 
		UInteger(10, 0) + UReal(8, 0)
		-> UReal(18.0, 0.0) : UReal

	Expression : 
		UInteger(8, 0) + UReal(8, 7)
		-> UReal(16.0, 7.0) : UReal

	Expression : 
		UInteger(6, 5) + UReal(6, 0)
		-> UReal(12.0, 5.0) : UReal

	Expression : 
		UInteger(9, 3) + UReal(9, 4)
		-> UReal(18.0, 5.0) : UReal

	Expression : 
		UInteger(9, 1) + UReal(8, 0)
		-> UReal(17.0, 1.0) : UReal

	Expression : 
		UInteger(3, 3) + UReal(4, 4)
		-> UReal(7.0, 5.0) : UReal

	Expression : 
		UInteger(-3, 0) + -3.0
		-> UReal(-6.0, 0.0) : UReal

	Expression : 
		UInteger(-6, 0) + -1.2
		-> UReal(-7.2, 0.0) : UReal

	Expression : 
		UInteger(-5, 3) + -5.0
		-> UReal(-10.0, 3.0) : UReal

	Expression : 
		UInteger(-8, 5) + -2.0
		-> UReal(-10.0, 5.0) : UReal

	Expression : 
		UInteger(0, 0) + 0.0
		-> UReal(0.0, 0.0) : UReal

	Expression : 
		UInteger(0, 0) + 3.0
		-> UReal(3.0, 0.0) : UReal

	Expression : 
		UInteger(0, 3) + 0.0
		-> UReal(0.0, 3.0) : UReal

	Expression : 
		UInteger(0, 5) + -5.0
		-> UReal(-5.0, 5.0) : UReal

	Expression : 
		UInteger(5, 0) + 5.0
		-> UReal(10.0, 0.0) : UReal

	Expression : 
		UInteger(3, 0) + 0.6
		-> UReal(3.6, 0.0) : UReal

	Expression : 
		UInteger(7, 3) + 7.0
		-> UReal(14.0, 3.0) : UReal

	Expression : 
		UInteger(2, 5) + 0.5
		-> UReal(2.5, 5.0) : UReal

	Expression : 
		UInteger(-3, 0) + -3
		-> UInteger(-6, 0.0) : UInteger

	Expression : 
		UInteger(-6, 0) + -12
		-> UInteger(-18, 0.0) : UInteger

	Expression : 
		UInteger(-5, 3) + -5
		-> UInteger(-10, 3.0) : UInteger

	Expression : 
		UInteger(-8, 5) + -2
		-> UInteger(-10, 5.0) : UInteger

	Expression : 
		UInteger(0, 0) + 0
		-> UInteger(0, 0.0) : UInteger

	Expression : 
		UInteger(0, 0) + 3
		-> UInteger(3, 0.0) : UInteger

	Expression : 
		UInteger(0, 3) + 0
		-> UInteger(0, 3.0) : UInteger

	Expression : 
		UInteger(0, 5) + -5
		-> UInteger(-5, 5.0) : UInteger

	Expression : 
		UInteger(5, 0) + 5
		-> UInteger(10, 0.0) : UInteger

	Expression : 
		UInteger(3, 0) + 56
		-> UInteger(59, 0.0) : UInteger

	Expression : 
		UInteger(7, 3) + 7
		-> UInteger(14, 3.0) : UInteger

	Expression : 
		UInteger(2, 5) + 65
		-> UInteger(67, 5.0) : UInteger

	Expression : 
		( UInteger(2, 5) + UInteger(0, 0) ).equals( UInteger(2, 5) )
		-> true : Boolean

	Expression : 
		( UInteger(2, 5) + 0 ).equals( UInteger(2, 5) )
		-> true : Boolean

	Expression : 
		( UInteger(2, 5) + 0.0 ).equals( UReal(2, 5) )
		-> true : Boolean

	Expression : 
		( UInteger(2, 5) + UReal(0, 0) ).equals( UReal(2, 5) )
		-> true : Boolean

	Expression : 
		( UInteger(6, 3) + UInteger(5, 0.3) ).equals( UInteger(5, 0.3) + UInteger(6, 3) )
		-> true : Boolean

	Expression : 
		( UInteger(9, 32) + 0.53 ).equals( 0.53 + UInteger(9, 32) )
		-> true : Boolean

	Expression : 
		( UInteger(2, 3) + 5 ).equals( 5 + UInteger(2, 3) )
		-> true : Boolean

	Expression : 
		( UInteger(9, 32) + UReal(0.53, 3) ).equals( UReal(0.53, 3) + UInteger(9, 32) )
		-> true : Boolean

	Expression : 
		( UInteger(6, 3) + (UInteger(5, 3) + UInteger(9,2)) ).equals( (UInteger(6, 3) + UInteger(5, 3)) + UInteger(9,2) )
		-> true : Boolean

	Expression : 
		( UReal(6, 3) + (5.3 + UInteger(9,2)) ).equals( (UReal(6, 3) + 5.3) + UInteger(9,2) )
		-> true : Boolean

	Expression : 
		( UReal(6, 3) + (5 + UInteger(9,2)) ).equals( (UReal(6, 3) + 5) + UInteger(9,2) )
		-> true : Boolean

	Expression : 
		( UInteger(6, 3) + (5 + 2) ).equals( (UInteger(6, 3) + 5) + 2 )
		-> true : Boolean

	Expression : 
		( 3.5 + (5 + UInteger(9,2)) ).equals( (3.5 + 5) + UInteger(9,2) )
		-> true : Boolean

	Expression : 
		UInteger(-9, 0) - UInteger(-9, 0)
		-> UInteger(0, 0.0) : UInteger

	Expression : 
		UInteger(-5, 0) - UInteger(-5, 3)
		-> UInteger(0, 3.0) : UInteger

	Expression : 
		UInteger(-4, 0) - UInteger(2, 0)
		-> UInteger(-6, 0.0) : UInteger

	Expression : 
		UInteger(-10, 0) - UInteger(4, 1)
		-> UInteger(-14, 1.0) : UInteger

	Expression : 
		UInteger(-9, 9) - UInteger(-9, 0)
		-> UInteger(0, 9.0) : UInteger

	Expression : 
		UInteger(-2, 3) - UInteger(-2, 4)
		-> UInteger(0, 5.0) : UInteger

	Expression : 
		UInteger(-6, 2) - UInteger(5, 0)
		-> UInteger(-11, 2.0) : UInteger

	Expression : 
		UInteger(-2, 3) - UInteger(4, 4)
		-> UInteger(-6, 5.0) : UInteger

	Expression : 
		UInteger(0, 0) - UInteger(0, 0)
		-> UInteger(0, 0.0) : UInteger

	Expression : 
		UInteger(0, 0) - UInteger(0, 4)
		-> UInteger(0, 4.0) : UInteger

	Expression : 
		UInteger(0, 0) - UInteger(6, 0)
		-> UInteger(-6, 0.0) : UInteger

	Expression : 
		UInteger(0, 0) - UInteger(7, 3)
		-> UInteger(-7, 3.0) : UInteger

	Expression : 
		UInteger(0, 4) - UInteger(0, 0)
		-> UInteger(0, 4.0) : UInteger

	Expression : 
		UInteger(0, 4) - UInteger(0, 3)
		-> UInteger(0, 5.0) : UInteger

	Expression : 
		UInteger(0, 4) - UInteger(1, 0)
		-> UInteger(-1, 4.0) : UInteger

	Expression : 
		UInteger(0, 4) - UInteger(2, 3)
		-> UInteger(-2, 5.0) : UInteger

	Expression : 
		UInteger(9, 0) - UInteger(9, 0)
		-> UInteger(0, 0.0) : UInteger

	Expression : 
		UInteger(5, 0) - UInteger(5, 3)
		-> UInteger(0, 3.0) : UInteger

	Expression : 
		UInteger(4, 0) - UInteger(8, 0)
		-> UInteger(-4, 0.0) : UInteger

	Expression : 
		UInteger(10, 0) - UInteger(10, 12)
		-> UInteger(0, 12.0) : UInteger

	Expression : 
		UInteger(9, 5) - UInteger(9, 0)
		-> UInteger(0, 5.0) : UInteger

	Expression : 
		UInteger(2, 3) - UInteger(2, 4)
		-> UInteger(0, 5.0) : UInteger

	Expression : 
		UInteger(6, 1) - UInteger(4, 0)
		-> UInteger(2, 1.0) : UInteger

	Expression : 
		UInteger(2, 3) - UInteger(5, 4)
		-> UInteger(-3, 5.0) : UInteger

	Expression : 
		UInteger(-9, 0) - UReal(-9, 0)
		-> UReal(0.0, 0.0) : UReal

	Expression : 
		UInteger(-5, 0) - UReal(-5, 3)
		-> UReal(0.0, 3.0) : UReal

	Expression : 
		UInteger(-4, 0) - UReal(2, 0)
		-> UReal(-6.0, 0.0) : UReal

	Expression : 
		UInteger(-10, 0) - UReal(4, 1)
		-> UReal(-14.0, 1.0) : UReal

	Expression : 
		UInteger(-9, 9) - UReal(-9, 0)
		-> UReal(0.0, 9.0) : UReal

	Expression : 
		UInteger(-2, 3) - UReal(-2, 4)
		-> UReal(0.0, 5.0) : UReal

	Expression : 
		UInteger(-6, 2) - UReal(5, 0)
		-> UReal(-11.0, 2.0) : UReal

	Expression : 
		UInteger(-2, 3) - UReal(4, 4)
		-> UReal(-6.0, 5.0) : UReal

	Expression : 
		UInteger(0, 0) - UReal(0, 0)
		-> UReal(0.0, 0.0) : UReal

	Expression : 
		UInteger(0, 0) - UReal(0, 4)
		-> UReal(0.0, 4.0) : UReal

	Expression : 
		UInteger(0, 0) - UReal(6, 0)
		-> UReal(-6.0, 0.0) : UReal

	Expression : 
		UInteger(0, 0) - UReal(7, 3)
		-> UReal(-7.0, 3.0) : UReal

	Expression : 
		UInteger(0, 4) - UReal(0, 0)
		-> UReal(0.0, 4.0) : UReal

	Expression : 
		UInteger(0, 4) - UReal(0, 3)
		-> UReal(0.0, 5.0) : UReal

	Expression : 
		UInteger(0, 4) - UReal(1, 0)
		-> UReal(-1.0, 4.0) : UReal

	Expression : 
		UInteger(0, 4) - UReal(2, 3)
		-> UReal(-2.0, 5.0) : UReal

	Expression : 
		UInteger(9, 0) - UReal(9, 0)
		-> UReal(0.0, 0.0) : UReal

	Expression : 
		UInteger(5, 0) - UReal(5, 3)
		-> UReal(0.0, 3.0) : UReal

	Expression : 
		UInteger(4, 0) - UReal(8, 0)
		-> UReal(-4.0, 0.0) : UReal

	Expression : 
		UInteger(10, 0) - UReal(10, 12)
		-> UReal(0.0, 12.0) : UReal

	Expression : 
		UInteger(9, 5) - UReal(9, 0)
		-> UReal(0.0, 5.0) : UReal

	Expression : 
		UInteger(2, 3) - UReal(2, 4)
		-> UReal(0.0, 5.0) : UReal

	Expression : 
		UInteger(6, 1) - UReal(4, 0)
		-> UReal(2.0, 1.0) : UReal

	Expression : 
		UInteger(2, 3) - UReal(5, 4)
		-> UReal(-3.0, 5.0) : UReal

	Expression : 
		UInteger(-3, 0) - -3.0
		-> UReal(0.0, 0.0) : UReal

	Expression : 
		UInteger(-6, 0) - -1.2
		-> UReal(-4.8, 0.0) : UReal

	Expression : 
		UInteger(-5, 3) - -5.0
		-> UReal(0.0, 3.0) : UReal

	Expression : 
		UInteger(-8, 5) - -2.0
		-> UReal(-6.0, 5.0) : UReal

	Expression : 
		UInteger(0, 0) - 0.0
		-> UReal(0.0, 0.0) : UReal

	Expression : 
		UInteger(0, 0) - 3.0
		-> UReal(-3.0, 0.0) : UReal

	Expression : 
		UInteger(0, 3) - 0.0
		-> UReal(0.0, 3.0) : UReal

	Expression : 
		UInteger(0, 5) - -5.0
		-> UReal(5.0, 5.0) : UReal

	Expression : 
		UInteger(5, 0) - 5.0
		-> UReal(0.0, 0.0) : UReal

	Expression : 
		UInteger(3, 0) - 0.6
		-> UReal(2.4, 0.0) : UReal

	Expression : 
		UInteger(7, 3) - 7.0
		-> UReal(0.0, 3.0) : UReal

	Expression : 
		UInteger(2, 5) - 0.5
		-> UReal(1.5, 5.0) : UReal

	Expression : 
		UInteger(-3, 0) - -3
		-> UInteger(0, 0.0) : UInteger

	Expression : 
		UInteger(-6, 0) - -12
		-> UInteger(6, 0.0) : UInteger

	Expression : 
		UInteger(-5, 3) - -5
		-> UInteger(0, 3.0) : UInteger

	Expression : 
		UInteger(-8, 5) - -2
		-> UInteger(-6, 5.0) : UInteger

	Expression : 
		UInteger(0, 0) - 0
		-> UInteger(0, 0.0) : UInteger

	Expression : 
		UInteger(0, 0) - 3
		-> UInteger(-3, 0.0) : UInteger

	Expression : 
		UInteger(0, 3) - 0
		-> UInteger(0, 3.0) : UInteger

	Expression : 
		UInteger(0, 5) - -5
		-> UInteger(5, 5.0) : UInteger

	Expression : 
		UInteger(5, 0) - 5
		-> UInteger(0, 0.0) : UInteger

	Expression : 
		UInteger(3, 0) - 56
		-> UInteger(-53, 0.0) : UInteger

	Expression : 
		UInteger(7, 3) - 7
		-> UInteger(0, 3.0) : UInteger

	Expression : 
		UInteger(2, 5) - 65
		-> UInteger(-63, 5.0) : UInteger

	Expression : 
		( UInteger(3, 4) - UInteger(5, 2) ).equals( -(UInteger(5, 2) - UInteger(3, 4)) )
		-> true : Boolean

	Expression : 
		( UInteger(3, 4) - 5 ).equals( -(5 - UInteger(3, 4)) )
		-> true : Boolean

	Expression : 
		( 4.3 - UInteger(5, 2) ).equals( -(UInteger(5, 2) - 4.3) )
		-> true : Boolean

	Expression : 
		( UInteger(3, 4) - UReal(5, 2) ).equals( -(UInteger(5, 2) - UReal(3, 4)) )
		-> true : Boolean

	Expression : 
		( UInteger(3, 4) - (UInteger(5, 2) - UInteger(2, 0.53)) ).equals( (UInteger(3, 4) - UInteger(5, 2)) - UInteger(2, 0.53) )
		-> false : Boolean

	Expression : 
		( UInteger(3, 0) - (UInteger(5, 0) - UReal(2, 0)) ).equals( (UInteger(3, 0) - UInteger(5, 0)) - UReal(2, 0) )
		-> false : Boolean

	Expression : 
		( UInteger(3, 0) - (5 - UReal(2, 0)) ).equals( (UInteger(3, 0) - 5) - UReal(2, 0) )
		-> false : Boolean

	Expression : 
		( UInteger(3, 0) - (5 - 2.2) ).equals( (UInteger(3, 0) - 5) - 2.2 )
		-> false : Boolean

	Expression : 
		UInteger(-9, 0) * UInteger(-9, 0)
		-> UInteger(81, 0.0) : UInteger

	Expression : 
		UInteger(-5, 0) * UInteger(-5, 3)
		-> UInteger(25, 15.0) : UInteger

	Expression : 
		UInteger(-4, 0) * UInteger(2, 0)
		-> UInteger(-8, 0.0) : UInteger

	Expression : 
		UInteger(-10, 0) * UInteger(4, 1)
		-> UInteger(-40, 10.0) : UInteger

	Expression : 
		UInteger(-9, 9) * UInteger(-9, 0)
		-> UInteger(81, 81.0) : UInteger

	Expression : 
		UInteger(-2, 3) * UInteger(-2, 4)
		-> UInteger(4, 10.0) : UInteger

	Expression : 
		UInteger(-6, 2) * UInteger(5, 0)
		-> UInteger(-30, 10.0) : UInteger

	Expression : 
		UInteger(-2, 3) * UInteger(2, 4)
		-> UInteger(-4, 10.0) : UInteger

	Expression : 
		UInteger(0, 0) * UInteger(0, 0)
		-> UInteger(0, 0.0) : UInteger

	Expression : 
		UInteger(0, 0) * UInteger(0, 4)
		-> UInteger(0, 0.0) : UInteger

	Expression : 
		UInteger(0, 0) * UInteger(6, 0)
		-> UInteger(0, 0.0) : UInteger

	Expression : 
		UInteger(0, 0) * UInteger(7, 3)
		-> UInteger(0, 0.0) : UInteger

	Expression : 
		UInteger(0, 4) * UInteger(0, 0)
		-> UInteger(0, 0.0) : UInteger

	Expression : 
		UInteger(0, 4) * UInteger(0, 3)
		-> UInteger(0, 0.0) : UInteger

	Expression : 
		UInteger(0, 4) * UInteger(1, 0)
		-> UInteger(0, 4.0) : UInteger

	Expression : 
		UInteger(0, 4) * UInteger(2, 3)
		-> UInteger(0, 8.0) : UInteger

	Expression : 
		UInteger(9, 0) * UInteger(9, 0)
		-> UInteger(81, 0.0) : UInteger

	Expression : 
		UInteger(5, 0) * UInteger(5, 3)
		-> UInteger(25, 15.0) : UInteger

	Expression : 
		UInteger(4, 0) * UInteger(8, 0)
		-> UInteger(32, 0.0) : UInteger

	Expression : 
		UInteger(10, 0) * UInteger(10, 12)
		-> UInteger(100, 120.0) : UInteger

	Expression : 
		UInteger(9, 5) * UInteger(9, 0)
		-> UInteger(81, 45.0) : UInteger

	Expression : 
		UInteger(2, 3) * UInteger(2, 4)
		-> UInteger(4, 10.0) : UInteger

	Expression : 
		UInteger(6, 1) * UInteger(4, 0)
		-> UInteger(24, 4.0) : UInteger

	Expression : 
		UInteger(2, 3) * UInteger(5, 4)
		-> UInteger(10, 17.0) : UInteger

	Expression : 
		UInteger(-9, 0) * UReal(-9, 0)
		-> UReal(81.0, 0.0) : UReal

	Expression : 
		UInteger(-5, 0) * UReal(-5, 3)
		-> UReal(25.0, 15.0) : UReal

	Expression : 
		UInteger(-4, 0) * UReal(2, 0)
		-> UReal(-8.0, 0.0) : UReal

	Expression : 
		UInteger(-10, 0) * UReal(4, 1)
		-> UReal(-40.0, 10.0) : UReal

	Expression : 
		UInteger(-9, 9) * UReal(-9, 0)
		-> UReal(81.0, 81.0) : UReal

	Expression : 
		UInteger(-2, 3) * UReal(-2, 4)
		-> UReal(4.0, 10.0) : UReal

	Expression : 
		UInteger(-6, 2) * UReal(5, 0)
		-> UReal(-30.0, 10.0) : UReal

	Expression : 
		UInteger(-2, 3) * UReal(2, 4)
		-> UReal(-4.0, 10.0) : UReal

	Expression : 
		UInteger(0, 0) * UReal(0, 0)
		-> UReal(0.0, 0.0) : UReal

	Expression : 
		UInteger(0, 0) * UReal(0, 4)
		-> UReal(0.0, 0.0) : UReal

	Expression : 
		UInteger(0, 0) * UReal(6, 0)
		-> UReal(0.0, 0.0) : UReal

	Expression : 
		UInteger(0, 0) * UReal(7, 3)
		-> UReal(0.0, 0.0) : UReal

	Expression : 
		UInteger(0, 4) * UReal(0, 0)
		-> UReal(0.0, 0.0) : UReal

	Expression : 
		UInteger(0, 4) * UReal(0, 3)
		-> UReal(0.0, 0.0) : UReal

	Expression : 
		UInteger(0, 4) * UReal(1, 0)
		-> UReal(0.0, 4.0) : UReal

	Expression : 
		UInteger(0, 4) * UReal(2, 3)
		-> UReal(0.0, 8.0) : UReal

	Expression : 
		UInteger(9, 0) * UReal(9, 0)
		-> UReal(81.0, 0.0) : UReal

	Expression : 
		UInteger(5, 0) * UReal(5, 3)
		-> UReal(25.0, 15.0) : UReal

	Expression : 
		UInteger(4, 0) * UReal(8, 0)
		-> UReal(32.0, 0.0) : UReal

	Expression : 
		UInteger(10, 0) * UReal(10, 12)
		-> UReal(100.0, 120.0) : UReal

	Expression : 
		UInteger(9, 5) * UReal(9, 0)
		-> UReal(81.0, 45.0) : UReal

	Expression : 
		UInteger(2, 3) * UReal(2, 4)
		-> UReal(4.0, 10.0) : UReal

	Expression : 
		UInteger(6, 1) * UReal(4, 0)
		-> UReal(24.0, 4.0) : UReal

	Expression : 
		UInteger(2, 3) * UReal(5, 4)
		-> UReal(10.0, 17.0) : UReal

	Expression : 
		UInteger(-3, 0) * -3.0
		-> UReal(9.0, 0.0) : UReal

	Expression : 
		UInteger(-6, 0) * -1.2
		-> UReal(7.2, 0.0) : UReal

	Expression : 
		UInteger(-5, 3) * -5.0
		-> UReal(25.0, 15.0) : UReal

	Expression : 
		UInteger(-8, 5) * -2.0
		-> UReal(16.0, 10.0) : UReal

	Expression : 
		UInteger(0, 0) * 0.0
		-> UReal(0.0, 0.0) : UReal

	Expression : 
		UInteger(0, 0) * 3.0
		-> UReal(0.0, 0.0) : UReal

	Expression : 
		UInteger(0, 3) * 0.0
		-> UReal(0.0, 0.0) : UReal

	Expression : 
		UInteger(0, 5) * -5.0
		-> UReal(0.0, 25.0) : UReal

	Expression : 
		UInteger(5, 0) * 5.0
		-> UReal(25.0, 0.0) : UReal

	Expression : 
		UInteger(3, 0) * 0.6
		-> UReal(1.8, 0.0) : UReal

	Expression : 
		UInteger(7, 3) * 7.0
		-> UReal(49.0, 21.0) : UReal

	Expression : 
		UInteger(2, 5) * 0.5
		-> UReal(1.0, 2.5) : UReal

	Expression : 
		UInteger(-3, 0) * -3
		-> UInteger(9, 0.0) : UInteger

	Expression : 
		UInteger(-6, 0) * -12
		-> UInteger(72, 0.0) : UInteger

	Expression : 
		UInteger(-5, 3) * -5
		-> UInteger(25, 15.0) : UInteger

	Expression : 
		UInteger(-8, 5) * -2
		-> UInteger(16, 10.0) : UInteger

	Expression : 
		UInteger(0, 0) * 0
		-> UInteger(0, 0.0) : UInteger

	Expression : 
		UInteger(0, 0) * 3
		-> UInteger(0, 0.0) : UInteger

	Expression : 
		UInteger(0, 3) * 0
		-> UInteger(0, 0.0) : UInteger

	Expression : 
		UInteger(0, 5) * -5
		-> UInteger(0, 25.0) : UInteger

	Expression : 
		UInteger(5, 0) * 5
		-> UInteger(25, 0.0) : UInteger

	Expression : 
		UInteger(3, 0) * 56
		-> UInteger(168, 0.0) : UInteger

	Expression : 
		UInteger(7, 3) * 7
		-> UInteger(49, 21.0) : UInteger

	Expression : 
		UInteger(2, 5) * 65
		-> UInteger(130, 325.0) : UInteger

	Expression : 
		( UInteger(3, 2) * UInteger(5, 2) ).equals( UInteger(5, 2) * UInteger(3, 2) )
		-> true : Boolean

	Expression : 
		( UInteger(3, 2) * UReal(5, 0) ).equals( UInteger(5, 0) * UReal(3, 2) )
		-> true : Boolean

	Expression : 
		( UInteger(3, 2) * 5 ).equals( 5 * UInteger(3, 2) )
		-> true : Boolean

	Expression : 
		( UInteger(3, 2) * -5.53 ).equals( -5.53 * UInteger(3, 2) )
		-> true : Boolean

	Expression : 
		( UInteger(3, 5) * (UInteger(5, 1) * UInteger(1, 2)) ).equals( (UInteger(3, 5) * UInteger(5, 1)) * UInteger(1, 2) )
		-> true : Boolean

	Expression : 
		( UInteger(3, 5) * (5.1 * UReal(1, 2)) ).equals( (UInteger(3, 5) * 5.1) * UReal(1, 2) )
		-> true : Boolean

	Expression : 
		( UInteger(3, 5) * (5.1 * 1.2) ).equals( (UInteger(3, 5) * 5.1) * 1.2 )
		-> true : Boolean

	Expression : 
		( UInteger(3, 5) * (5 * UInteger(1, 2)) ).equals( (UInteger(3, 5) * 5) * UInteger(1, 2) )
		-> true : Boolean

	Expression : 
		( UInteger(3, 5) * (5 * 1.2) ).equals( (UInteger(3, 5) * 5) * 1.2 )
		-> true : Boolean

	Expression : 
		( UInteger(3, 5) * (5 * 2) ).equals( (UInteger(3, 5) * 5) * 2 )
		-> true : Boolean

	Expression : 
		( UInteger(2,1) * (UInteger(3,1) + UInteger(5, 0.2)) ).equals( UInteger(2,1) * UInteger(3,1) +  UInteger(2,1) * UInteger(5, 0.2) )
		-> false : Boolean

	Expression : 
		( 5.1 * (UInteger(3, 2) + UInteger(1, 2)) ).equals( (5.1 * UInteger(3, 2)) + (5.1 * UInteger(1, 2)) )
		-> true : Boolean

	Expression : 
		( 2 * (UInteger(3, 2) + UInteger(1, 2)) ).equals( (2 * UInteger(3, 2)) + (2 * UInteger(1, 2)) )
		-> true : Boolean

	Expression : 
		( UInteger(3, 2) * UInteger(1, 0) ).equals( UInteger(3, 2) )
		-> true : Boolean

	Expression : 
		( UInteger(3, 2) * 1 ).equals( UInteger(3, 2) )
		-> true : Boolean

	Expression : 
		( UInteger(3, 2) * 1.0 ).equals( UReal(3, 2) )
		-> true : Boolean

	Expression : 
		UInteger(-9, 0) / UInteger(-9, 0)
		-> UReal(1.0, 0.0) : UReal

	Expression : 
		UInteger(-5, 0) / UInteger(-5, 3)
		-> UReal(1.0, 0.12) : UReal

	Expression : 
		UInteger(-4, 0) / UInteger(2, 0)
		-> UReal(-2.0, 0.0) : UReal

	Expression : 
		UInteger(-10, 0) / UInteger(4, 1)
		-> UReal(-2.5, 0.0625) : UReal

	Expression : 
		UInteger(-9, 9) / UInteger(-9, 0)
		-> UReal(1.0, 1.0) : UReal

	Expression : 
		UInteger(-2, 3) / UInteger(-2, 4)
		-> UReal(1.0, 2.9154759474) : UReal

	Expression : 
		UInteger(-6, 2) / UInteger(5, 0)
		-> UReal(-1.2, 0.4) : UReal

	Expression : 
		UInteger(-2, 3) / UInteger(2, 4)
		-> UReal(-1.0, 2.9154759474) : UReal

	Expression : 
		UInteger(0, 0) / UInteger(0, 0)
		-> null : OclVoid

	Expression : 
		UInteger(0, 0) / UInteger(0, 4)
		-> null : OclVoid

	Expression : 
		UInteger(0, 0) / UInteger(6, 0)
		-> UReal(0.0, 0.0) : UReal

	Expression : 
		UInteger(0, 0) / UInteger(7, 3)
		-> UReal(0.0, 0.0612244898) : UReal

	Expression : 
		UInteger(0, 4) / UInteger(0, 0)
		-> null : OclVoid

	Expression : 
		UInteger(0, 4) / UInteger(0, 3)
		-> null : OclVoid

	Expression : 
		UInteger(0, 4) / UInteger(1, 0)
		-> UReal(0.0, 4.0) : UReal

	Expression : 
		UInteger(0, 4) / UInteger(2, 3)
		-> UReal(0.0, 2.8284271247) : UReal

	Expression : 
		UInteger(9, 0) / UInteger(9, 0)
		-> UReal(1.0, 0.0) : UReal

	Expression : 
		UInteger(5, 0) / UInteger(5, 3)
		-> UReal(1.0, 0.12) : UReal

	Expression : 
		UInteger(4, 0) / UInteger(8, 0)
		-> UReal(0.5, 0.0) : UReal

	Expression : 
		UInteger(10, 0) / UInteger(10, 12)
		-> UReal(1.0, 0.12) : UReal

	Expression : 
		UInteger(9, 5) / UInteger(9, 0)
		-> UReal(1.0, 0.5555555556) : UReal

	Expression : 
		UInteger(2, 3) / UInteger(2, 4)
		-> UReal(1.0, 2.9154759474) : UReal

	Expression : 
		UInteger(6, 1) / UInteger(4, 0)
		-> UReal(1.5, 0.25) : UReal

	Expression : 
		UInteger(2, 3) / UInteger(5, 4)
		-> UReal(0.4, 1.379275172) : UReal

	Expression : 
		UInteger(-9, 0) / UReal(-9, 0)
		-> UReal(1.0, 0.0) : UReal

	Expression : 
		UInteger(-5, 0) / UReal(-5, 3)
		-> UReal(1.0, 0.12) : UReal

	Expression : 
		UInteger(-4, 0) / UReal(2, 0)
		-> UReal(-2.0, 0.0) : UReal

	Expression : 
		UInteger(-10, 0) / UReal(4, 1)
		-> UReal(-2.5, 0.0625) : UReal

	Expression : 
		UInteger(-9, 9) / UReal(-9, 0)
		-> UReal(1.0, 1.0) : UReal

	Expression : 
		UInteger(-2, 3) / UReal(-2, 4)
		-> UReal(1.0, 2.9154759474) : UReal

	Expression : 
		UInteger(-6, 2) / UReal(5, 0)
		-> UReal(-1.2, 0.4) : UReal

	Expression : 
		UInteger(-2, 3) / UReal(2, 4)
		-> UReal(-1.0, 2.9154759474) : UReal

	Expression : 
		UInteger(0, 0) / UReal(0, 0)
		-> null : OclVoid

	Expression : 
		UInteger(0, 0) / UReal(0, 4)
		-> null : OclVoid

	Expression : 
		UInteger(0, 0) / UReal(6, 0)
		-> UReal(0.0, 0.0) : UReal

	Expression : 
		UInteger(0, 0) / UReal(7, 3)
		-> UReal(0.0, 0.0612244898) : UReal

	Expression : 
		UInteger(0, 4) / UReal(0, 0)
		-> null : OclVoid

	Expression : 
		UInteger(0, 4) / UReal(0, 3)
		-> null : OclVoid

	Expression : 
		UInteger(0, 4) / UReal(1, 0)
		-> UReal(0.0, 4.0) : UReal

	Expression : 
		UInteger(0, 4) / UReal(2, 3)
		-> UReal(0.0, 2.8284271247) : UReal

	Expression : 
		UInteger(9, 0) / UReal(9, 0)
		-> UReal(1.0, 0.0) : UReal

	Expression : 
		UInteger(5, 0) / UReal(5, 3)
		-> UReal(1.0, 0.12) : UReal

	Expression : 
		UInteger(4, 0) / UReal(8, 0)
		-> UReal(0.5, 0.0) : UReal

	Expression : 
		UInteger(10, 0) / UReal(10, 12)
		-> UReal(1.0, 0.12) : UReal

	Expression : 
		UInteger(9, 5) / UReal(9, 0)
		-> UReal(1.0, 0.5555555556) : UReal

	Expression : 
		UInteger(2, 3) / UReal(2, 4)
		-> UReal(1.0, 2.9154759474) : UReal

	Expression : 
		UInteger(6, 1) / UReal(4, 0)
		-> UReal(1.5, 0.25) : UReal

	Expression : 
		UInteger(2, 3) / UReal(5, 4)
		-> UReal(0.4, 1.379275172) : UReal

	Expression : 
		UInteger(-3, 0) / -3.0
		-> UReal(1.0, 0.0) : UReal

	Expression : 
		UInteger(-6, 0) / -1.2
		-> UReal(5.0, 0.0) : UReal

	Expression : 
		UInteger(-5, 3) / -5.0
		-> UReal(1.0, 0.6) : UReal

	Expression : 
		UInteger(-8, 5) / -2.0
		-> UReal(4.0, 2.5) : UReal

	Expression : 
		UInteger(0, 0) / 0.0
		-> null : OclVoid

	Expression : 
		UInteger(0, 0) / 3.0
		-> UReal(0.0, 0.0) : UReal

	Expression : 
		UInteger(0, 3) / 0.0
		-> null : OclVoid

	Expression : 
		UInteger(0, 5) / -5.0
		-> UReal(0.0, 1.0) : UReal

	Expression : 
		UInteger(5, 0) / 5.0
		-> UReal(1.0, 0.0) : UReal

	Expression : 
		UInteger(3, 0) / 0.6
		-> UReal(5.0, 0.0) : UReal

	Expression : 
		UInteger(7, 3) / 7.0
		-> UReal(1.0, 0.4285714286) : UReal

	Expression : 
		UInteger(2, 5) / 0.5
		-> UReal(4.0, 10.0) : UReal

	Expression : 
		UInteger(-3, 0) / -3
		-> UReal(1.0, 0.0) : UReal

	Expression : 
		UInteger(-6, 0) / -12
		-> UReal(0.5, 0.0) : UReal

	Expression : 
		UInteger(-5, 3) / -5
		-> UReal(1.0, 0.6) : UReal

	Expression : 
		UInteger(-8, 5) / -2
		-> UReal(4.0, 2.5) : UReal

	Expression : 
		UInteger(0, 0) / 0
		-> null : OclVoid

	Expression : 
		UInteger(0, 0) / 3
		-> UReal(0.0, 0.0) : UReal

	Expression : 
		UInteger(0, 3) / 0
		-> null : OclVoid

	Expression : 
		UInteger(0, 5) / -5
		-> UReal(0.0, 1.0) : UReal

	Expression : 
		UInteger(5, 0) / 5
		-> UReal(1.0, 0.0) : UReal

	Expression : 
		UInteger(3, 0) / 56
		-> UReal(0.0535714286, 0.0) : UReal

	Expression : 
		UInteger(7, 3) / 7
		-> UReal(1.0, 0.4285714286) : UReal

	Expression : 
		UInteger(2, 5) / 65
		-> UReal(0.0307692308, 0.0769230769) : UReal

	Expression : 
		( UInteger(2, 3).toUReal().inv() ).equals( 1 / UInteger(2, 3) )
		-> true : Boolean

	Expression : 
		( UInteger(0, 3).toUReal().inv() ).equals( 1 / UInteger(0, 3) )
		-> true : Boolean

	Expression : 
		( UInteger(2, 3) / UInteger(1, 0.5) ).equals( UInteger(1, 0.5) / UInteger(2, 3) )
		-> false : Boolean

	Expression : 
		( 2.3 / UInteger(1, 0.5) ).equals( UInteger(1, 0.5) / 2.3 )
		-> false : Boolean

	Expression : 
		( 2 / UInteger(1, 0.5) ).equals( UInteger(1, 0.5) / 2 )
		-> false : Boolean

	Expression : 
		( UInteger(2, 3) / (UInteger(1, 0.5) / UInteger(4, 0.25)) ).equals( (UInteger(2, 3) / UInteger(1, 0.5)) / UInteger(4, 0.25) )
		-> false : Boolean

	Expression : 
		( UInteger(2, 3) / (12.59 / UInteger(3, 0.25)) ).equals( (UInteger(2, 3) / 12.59) / UInteger(3, 0.25) )
		-> false : Boolean

	Expression : 
		( UInteger(2, 3) / (12 / UInteger(3, 0.25)) ).equals( (UInteger(2, 3) / 12) / UInteger(3, 0.25) )
		-> false : Boolean

	Expression : 
		( UInteger(2, 3) / 1 ).equals( UInteger(2, 3).toUReal() )
		-> true : Boolean

	Expression : 
		UInteger(-9, 0) div UInteger(-9, 0)
		-> UInteger(1, 0.0) : UInteger

	Expression : 
		UInteger(-5, 0) div UInteger(-5, 3)
		-> UInteger(1, 0.12) : UInteger

	Expression : 
		UInteger(-4, 0) div UInteger(2, 0)
		-> UInteger(-2, 0.0) : UInteger

	Expression : 
		UInteger(-10, 0) div UInteger(4, 1)
		-> UInteger(-2, 0.0625) : UInteger

	Expression : 
		UInteger(-9, 9) div UInteger(-9, 0)
		-> UInteger(1, 1.0) : UInteger

	Expression : 
		UInteger(-2, 3) div UInteger(-2, 4)
		-> UInteger(1, 2.9154759474) : UInteger

	Expression : 
		UInteger(-6, 2) div UInteger(5, 0)
		-> UInteger(-1, 0.4) : UInteger

	Expression : 
		UInteger(-2, 3) div UInteger(2, 4)
		-> UInteger(-1, 2.9154759474) : UInteger

	Expression : 
		UInteger(0, 0) div UInteger(0, 0)
		-> null : OclVoid

	Expression : 
		UInteger(0, 0) div UInteger(0, 4)
		-> null : OclVoid

	Expression : 
		UInteger(0, 0) div UInteger(6, 0)
		-> UInteger(0, 0.0) : UInteger

	Expression : 
		UInteger(0, 0) div UInteger(7, 3)
		-> UInteger(0, 0.0612244898) : UInteger

	Expression : 
		UInteger(0, 4) div UInteger(0, 0)
		-> null : OclVoid

	Expression : 
		UInteger(0, 4) div UInteger(0, 3)
		-> null : OclVoid

	Expression : 
		UInteger(0, 4) div UInteger(1, 0)
		-> UInteger(0, 4.0) : UInteger

	Expression : 
		UInteger(0, 4) div UInteger(2, 3)
		-> UInteger(0, 2.8284271247) : UInteger

	Expression : 
		UInteger(9, 0) div UInteger(9, 0)
		-> UInteger(1, 0.0) : UInteger

	Expression : 
		UInteger(5, 0) div UInteger(5, 3)
		-> UInteger(1, 0.12) : UInteger

	Expression : 
		UInteger(4, 0) div UInteger(8, 0)
		-> UInteger(0, 0.0) : UInteger

	Expression : 
		UInteger(10, 0) div UInteger(10, 12)
		-> UInteger(1, 0.12) : UInteger

	Expression : 
		UInteger(9, 5) div UInteger(9, 0)
		-> UInteger(1, 0.5555555556) : UInteger

	Expression : 
		UInteger(2, 3) div UInteger(2, 4)
		-> UInteger(1, 2.9154759474) : UInteger

	Expression : 
		UInteger(6, 1) div UInteger(4, 0)
		-> UInteger(1, 0.25) : UInteger

	Expression : 
		UInteger(2, 3) div UInteger(5, 4)
		-> UInteger(0, 1.379275172) : UInteger

	Expression : 
		UInteger(-3, 0) div -3
		-> UInteger(1, 0.0) : UInteger

	Expression : 
		UInteger(-6, 0) div -12
		-> UInteger(0, 0.0) : UInteger

	Expression : 
		UInteger(-5, 3) div -5
		-> UInteger(1, 0.6) : UInteger

	Expression : 
		UInteger(-8, 5) div -2
		-> UInteger(4, 2.5) : UInteger

	Expression : 
		UInteger(0, 0) div 0
		-> null : OclVoid

	Expression : 
		UInteger(0, 0) div 3
		-> UInteger(0, 0.0) : UInteger

	Expression : 
		UInteger(0, 3) div 0
		-> null : OclVoid

	Expression : 
		UInteger(0, 5) div -5
		-> UInteger(0, 1.0) : UInteger

	Expression : 
		UInteger(5, 0) div 5
		-> UInteger(1, 0.0) : UInteger

	Expression : 
		UInteger(3, 0) div 56
		-> UInteger(0, 0.0) : UInteger

	Expression : 
		UInteger(7, 3) div 7
		-> UInteger(1, 0.4285714286) : UInteger

	Expression : 
		UInteger(2, 5) div 65
		-> UInteger(0, 0.0769230769) : UInteger

	Expression : 
		UInteger(-9, 0).mod(UInteger(-9, 0))
		-> UInteger(0, 0.0) : UInteger

	Expression : 
		UInteger(-5, 0).mod(UInteger(-5, 3))
		-> UInteger(0, 0.12) : UInteger

	Expression : 
		UInteger(-4, 0).mod(UInteger(2, 0))
		-> UInteger(0, 0.0) : UInteger

	Expression : 
		UInteger(-10, 0).mod(UInteger(4, 1))
		-> UInteger(-2, 0.0625) : UInteger

	Expression : 
		UInteger(-9, 9).mod(UInteger(-9, 0))
		-> UInteger(0, 1.0) : UInteger

	Expression : 
		UInteger(-2, 3).mod(UInteger(-2, 4))
		-> UInteger(0, 2.9154759474) : UInteger

	Expression : 
		UInteger(-6, 2).mod(UInteger(5, 0))
		-> UInteger(-1, 0.4) : UInteger

	Expression : 
		UInteger(-2, 3).mod(UInteger(2, 4))
		-> UInteger(0, 2.9154759474) : UInteger

	Expression : 
		UInteger(0, 0).mod(UInteger(0, 0))
		-> null : OclVoid

	Expression : 
		UInteger(0, 0).mod(UInteger(0, 4))
		-> null : OclVoid

	Expression : 
		UInteger(0, 0).mod(UInteger(6, 0))
		-> UInteger(0, 0.0) : UInteger

	Expression : 
		UInteger(0, 0).mod(UInteger(7, 3))
		-> UInteger(0, 0.0612244898) : UInteger

	Expression : 
		UInteger(0, 4).mod(UInteger(0, 0))
		-> null : OclVoid

	Expression : 
		UInteger(0, 4).mod(UInteger(0, 3))
		-> null : OclVoid

	Expression : 
		UInteger(0, 4).mod(UInteger(1, 0))
		-> UInteger(0, 4.0) : UInteger

	Expression : 
		UInteger(0, 4).mod(UInteger(2, 3))
		-> UInteger(0, 2.8284271247) : UInteger

	Expression : 
		UInteger(9, 0).mod(UInteger(9, 0))
		-> UInteger(0, 0.0) : UInteger

	Expression : 
		UInteger(5, 0).mod(UInteger(5, 3))
		-> UInteger(0, 0.12) : UInteger

	Expression : 
		UInteger(4, 0).mod(UInteger(8, 0))
		-> UInteger(4, 0.0) : UInteger

	Expression : 
		UInteger(10, 0).mod(UInteger(10, 12))
		-> UInteger(0, 0.12) : UInteger

	Expression : 
		UInteger(9, 5).mod(UInteger(9, 0))
		-> UInteger(0, 0.5555555556) : UInteger

	Expression : 
		UInteger(2, 3).mod(UInteger(2, 4))
		-> UInteger(0, 2.9154759474) : UInteger

	Expression : 
		UInteger(6, 1).mod(UInteger(4, 0))
		-> UInteger(2, 0.25) : UInteger

	Expression : 
		UInteger(2, 3).mod(UInteger(5, 4))
		-> UInteger(2, 1.379275172) : UInteger

	Expression : 
		UInteger(-3, 0).mod(-3)
		-> UInteger(0, 0.0) : UInteger

	Expression : 
		UInteger(-6, 0).mod(-12)
		-> UInteger(-6, 0.0) : UInteger

	Expression : 
		UInteger(-5, 3).mod(-5)
		-> UInteger(0, 0.6) : UInteger

	Expression : 
		UInteger(-8, 5).mod(-2)
		-> UInteger(0, 2.5) : UInteger

	Expression : 
		UInteger(0, 0).mod(0)
		-> null : OclVoid

	Expression : 
		UInteger(0, 0).mod(3)
		-> UInteger(0, 0.0) : UInteger

	Expression : 
		UInteger(0, 3).mod(0)
		-> null : OclVoid

	Expression : 
		UInteger(0, 5).mod(-5)
		-> UInteger(0, 1.0) : UInteger

	Expression : 
		UInteger(5, 0).mod(5)
		-> UInteger(0, 0.0) : UInteger

	Expression : 
		UInteger(3, 0).mod(56)
		-> UInteger(3, 0.0) : UInteger

	Expression : 
		UInteger(7, 3).mod(7)
		-> UInteger(0, 0.4285714286) : UInteger

	Expression : 
		UInteger(2, 5).mod(65)
		-> UInteger(2, 0.0769230769) : UInteger

	Expression : 
		(UInteger(0, 0) < UInteger(0, 0)).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(0, 0) < UInteger(1, 0)).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(3, 0) < UInteger(0, 0)).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(0, 0) < UInteger(3, 2)).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(3, 0) < UInteger(0, 2)).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(0, 2) < UInteger(3, 0)).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(3, 2) < UInteger(0, 0)).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(0, 2) < UInteger(0, 2)).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(0, 2) < UInteger(0, 1)).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(0, 2) < UInteger(1, 0.25)).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(0, 2) < UInteger(-1, 0.25)).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(0, 2) < UInteger(5, 2)).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(5, 2) < UInteger(0, 2)).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(0, 0) < UReal(0, 0)).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(0, 0) < UReal(1, 0)).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(3, 0) < UReal(0, 0)).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(0, 0) < UReal(3, 2)).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(3, 0) < UReal(0, 2)).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(0, 2) < UReal(3, 0)).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(3, 2) < UReal(0, 0)).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(0, 2) < UReal(0, 2)).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(0, 2) < UReal(0, 1)).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(0, 2) < UReal(1, 0.25)).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(0, 2) < UReal(-1, 0.25)).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(0, 2) < UReal(5, 2)).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(5, 2) < UReal(0, 2)).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(0, 0) < 0).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(0, 0) < 1).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(1, 0) < 0).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(0, 2) < 3).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(3, 2) < 0).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(0, 0) <= UInteger(0, 0)).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(0, 0) <= UInteger(1, 0)).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(3, 0) <= UInteger(0, 0)).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(0, 0) <= UInteger(3, 2)).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(3, 0) <= UInteger(0, 2)).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(0, 2) <= UInteger(3, 0)).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(3, 2) <= UInteger(0, 0)).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(0, 2) <= UInteger(0, 2)).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(0, 2) <= UInteger(0, 1)).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(0, 2) <= UInteger(1, 0.25)).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(0, 2) <= UInteger(-1, 0.25)).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(0, 2) <= UInteger(5, 2)).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(5, 2) <= UInteger(0, 2)).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(0, 0) <= UReal(0, 0)).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(0, 0) <= UReal(1, 0)).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(3, 0) <= UReal(0, 0)).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(0, 0) <= UReal(3, 2)).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(3, 0) <= UReal(0, 2)).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(0, 2) <= UReal(3, 0)).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(3, 2) <= UReal(0, 0)).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(0, 2) <= UReal(0, 2)).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(0, 2) <= UReal(0, 1)).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(0, 2) <= UReal(1, 0.25)).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(0, 2) <= UReal(-1, 0.25)).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(0, 2) <= UReal(5, 2)).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(5, 2) <= UReal(0, 2)).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(0, 0) <= 0).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(0, 0) <= 1).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(1, 0) <= 0).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(0, 2) <= 3).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(3, 2) <= 0).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(0, 0) <= 0.0).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(0, 0) <= 1.0).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(1, 0) <= 0.0).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(0, 2) <= 3.0).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(3, 2) <= 0.0).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(0, 0) > UInteger(0, 0)).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(0, 0) > UInteger(1, 0)).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(3, 0) > UInteger(0, 0)).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(0, 0) > UInteger(3, 2)).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(3, 0) > UInteger(0, 2)).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(0, 2) > UInteger(3, 0)).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(3, 2) > UInteger(0, 0)).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(0, 2) > UInteger(0, 2)).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(0, 2) > UInteger(0, 1)).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(0, 2) > UInteger(1, 0.25)).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(0, 2) > UInteger(-1, 0.25)).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(0, 2) > UInteger(5, 2)).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(5, 2) > UInteger(0, 2)).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(0, 0) > UReal(0, 0)).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(0, 0) > UReal(1, 0)).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(3, 0) > UReal(0, 0)).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(0, 0) > UReal(3, 2)).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(3, 0) > UReal(0, 2)).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(0, 2) > UReal(3, 0)).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(3, 2) > UReal(0, 0)).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(0, 2) > UReal(0, 2)).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(0, 2) > UReal(0, 1)).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(0, 2) > UReal(1, 0.25)).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(0, 2) > UReal(-1, 0.25)).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(0, 2) > UReal(5, 2)).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(5, 2) > UReal(0, 2)).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(0, 0) > 0).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(0, 0) > 1).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(1, 0) > 0).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(0, 2) > 3).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(3, 2) > 0).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(0, 0) > 0.0).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(0, 0) > 1.0).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(1, 0) > 0.0).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(0, 2) > 3.0).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(3, 2) > 0.0).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(0, 0) >= UInteger(0, 0)).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(0, 0) >= UInteger(1, 0)).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(3, 0) >= UInteger(0, 0)).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(0, 0) >= UInteger(3, 2)).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(3, 0) >= UInteger(0, 2)).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(0, 2) >= UInteger(3, 0)).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(3, 2) >= UInteger(0, 0)).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(0, 2) >= UInteger(0, 2)).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(0, 2) >= UInteger(0, 1)).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(0, 2) >= UInteger(1, 0.25)).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(0, 2) >= UInteger(-1, 0.25)).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(0, 2) >= UInteger(5, 2)).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(5, 2) >= UInteger(0, 2)).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(0, 0) >= UReal(0, 0)).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(0, 0) >= UReal(1, 0)).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(3, 0) >= UReal(0, 0)).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(0, 0) >= UReal(3, 2)).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(3, 0) >= UReal(0, 2)).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(0, 2) >= UReal(3, 0)).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(3, 2) >= UReal(0, 0)).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(0, 2) >= UReal(0, 2)).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(0, 2) >= UReal(0, 1)).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(0, 2) >= UReal(1, 0.25)).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(0, 2) >= UReal(-1, 0.25)).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(0, 2) >= UReal(5, 2)).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(5, 2) >= UReal(0, 2)).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(0, 0) >= 0).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(0, 0) >= 1).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(1, 0) >= 0).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(0, 2) >= 3).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(3, 2) >= 0).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(0, 0) >= 0.0).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(0, 0) >= 1.0).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(1, 0) >= 0.0).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(0, 2) >= 3.0).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(3, 2) >= 0.0).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(0, 0) = UInteger(0, 0)).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(0, 0) = UInteger(1, 0)).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(3, 0) = UInteger(0, 0)).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(0, 0) = UInteger(3, 2)).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(3, 0) = UInteger(0, 2)).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(0, 2) = UInteger(3, 0)).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(3, 2) = UInteger(0, 0)).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(0, 2) = UInteger(0, 2)).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(0, 2) = UInteger(0, 1)).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(0, 2) = UInteger(1, 0.25)).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(0, 2) = UInteger(-1, 0.25)).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(0, 2) = UInteger(5, 2)).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(5, 2) = UInteger(0, 2)).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(0, 0) = UReal(0, 0)).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(0, 0) = UReal(1, 0)).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(3, 0) = UReal(0, 0)).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(0, 0) = UReal(3, 2)).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(3, 0) = UReal(0, 2)).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(0, 2) = UReal(3, 0)).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(3, 2) = UReal(0, 0)).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(0, 2) = UReal(0, 2)).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(0, 2) = UReal(0, 1)).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(0, 2) = UReal(1, 0.25)).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(0, 2) = UReal(-1, 0.25)).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(0, 2) = UReal(5, 2)).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(5, 2) = UReal(0, 2)).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(0, 0) = 0).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(0, 0) = 1).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(1, 0) = 0).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(0, 2) = 3).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(3, 2) = 0).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(0, 0) = 0.0).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(0, 0) = 1.0).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(1, 0) = 0.0).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(0, 2) = 3.0).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(3, 2) = 0.0).toBoolean()
		-> false : Boolean

	Expression : 
		UInteger(2, 3) = Undefined
		-> false : Boolean

	Expression : 
		UInteger(2, 3) = null
		-> false : Boolean

	Expression : 
		(UInteger(0, 0) <> UInteger(0, 0)).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(0, 0) <> UInteger(1, 0)).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(3, 0) <> UInteger(0, 0)).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(0, 0) <> UInteger(3, 2)).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(3, 0) <> UInteger(0, 2)).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(0, 2) <> UInteger(3, 0)).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(3, 2) <> UInteger(0, 0)).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(0, 2) <> UInteger(0, 2)).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(0, 2) <> UInteger(0, 1)).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(0, 2) <> UInteger(1, 0.25)).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(0, 2) <> UInteger(-1, 0.25)).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(0, 2) <> UInteger(5, 2)).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(5, 2) <> UInteger(0, 2)).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(0, 0) <> UReal(0, 0)).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(0, 0) <> UReal(1, 0)).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(3, 0) <> UReal(0, 0)).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(0, 0) <> UReal(3, 2)).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(3, 0) <> UReal(0, 2)).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(0, 2) <> UReal(3, 0)).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(3, 2) <> UReal(0, 0)).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(0, 2) <> UReal(0, 2)).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(0, 2) <> UReal(0, 1)).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(0, 2) <> UReal(1, 0.25)).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(0, 2) <> UReal(-1, 0.25)).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(0, 2) <> UReal(5, 2)).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(5, 2) <> UReal(0, 2)).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(0, 0) <> 0).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(0, 0) <> 1).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(1, 0) <> 0).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(0, 2) <> 3).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(3, 2) <> 0).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(0, 0) <> 0.0).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(0, 0) <> 1.0).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(1, 0) <> 0.0).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(0, 2) <> 3.0).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(3, 2) <> 0.0).toBoolean()
		-> true : Boolean

	Expression : 
		UInteger(2, 3) <> Undefined
		-> true : Boolean

	Expression : 
		UInteger(2, 3) <> null
		-> true : Boolean

-----------------------------------------------------------------
File : URealExpression.in
	Expression : 
		UReal(2, 0)
		-> UReal(2.0, 0.0) : UReal

	Expression : 
		UReal(2, 2)
		-> UReal(2.0, 2.0) : UReal

	Expression : 
		UReal(2, -2)
		-> UReal(2.0, 2.0) : UReal

	Expression : 
		UReal(0, 0)
		-> UReal(0.0, 0.0) : UReal

	Expression : 
		UReal(0, 2)
		-> UReal(0.0, 2.0) : UReal

	Expression : 
		UReal(0, -2)
		-> UReal(0.0, 2.0) : UReal

	Expression : 
		UReal(2+2, 3)
		-> UReal(4.0, 3.0) : UReal

	Expression : 
		UReal(55.23, 9.34)
		-> UReal(55.23, 9.34) : UReal

	Expression : 
		UReal(55.23, 0.34)
		-> UReal(55.23, 0.34) : UReal

	Expression : 
		UReal(55.23, -66.34)
		-> UReal(55.23, 66.34) : UReal

	Expression : 
		UReal(0.34, 55.23)
		-> UReal(0.34, 55.23) : UReal

	Expression : 
		UReal(0.34, 0.34)
		-> UReal(0.34, 0.34) : UReal

	Expression : 
		UReal(0.34, -66.34)
		-> UReal(0.34, 66.34) : UReal

	Expression : 
		UReal(-66.34, 55.23)
		-> UReal(-66.34, 55.23) : UReal

	Expression : 
		UReal(-66.34, 0.34)
		-> UReal(-66.34, 0.34) : UReal

	Expression : 
		UReal(-66.34, -66.34)
		-> UReal(-66.34, 66.34) : UReal

	Expression : 
		UReal(2.3, 5)
		-> UReal(2.3, 5.0) : UReal

	Expression : 
		UReal(3*3/5, 9*(3-4))
		-> UReal(1.8, 9.0) : UReal

	Expression : 
		UReal('Hola', 9.3)
		-> Value must be Integer or Real

	Expression : 
		UReal(9.3, 'Hola')
		-> Uncertainty must be Integer or Real

	Expression : 
		UReal(2, 2 + 3/0)
		-> null : OclVoid

	Expression : 
		UReal(2 / 0, 3)
		-> null : OclVoid

	Expression : 
		UReal(3 / 0, 2 / 0)
		-> null : OclVoid

	Expression : 
		UReal(2.3, 5).oclIsTypeOf(UReal)
		-> true : Boolean

	Expression : 
		(3.2).oclIsKindOf(UReal)
		-> true : Boolean

	Expression : 
		2.oclIsKindOf(UReal)
		-> true : Boolean

	Expression : 
		UReal(2, 3).abs()
		-> UReal(2.0, 3.0) : UReal

	Expression : 
		UReal(0, 3).abs()
		-> UReal(0.0, 3.0) : UReal

	Expression : 
		UReal(-2, 3).abs()
		-> UReal(2.0, 3.0) : UReal

	Expression : 
		UReal(-3, 2.3).value()
		-> -3.0 : Real

	Expression : 
		UReal(0, 2.3).value()
		-> 0.0 : Real

	Expression : 
		UReal(3, 2.3).value()
		-> 3.0 : Real

	Expression : 
		UReal(-2, 3).setValue(0.0)
		-> UReal(0.0, 3.0) : UReal

	Expression : 
		UReal(-2, 3).setValue(-2.0)
		-> UReal(-2.0, 3.0) : UReal

	Expression : 
		UReal(-2, 3).setValue(-2)
		-> UReal(-2.0, 3.0) : UReal

	Expression : 
		UReal(-2, 3).setValue(3 / 0)
		-> null : OclVoid

	Expression : 
		UReal(-3, -2.3).uncertainty()
		-> 2.3 : Real

	Expression : 
		UReal(-3, 0).uncertainty()
		-> 0.0 : Real

	Expression : 
		UReal(-3, 0).setUncertainty(-3)
		-> UReal(-3.0, 3.0) : UReal

	Expression : 
		UReal(-3, 0).setUncertainty(3)
		-> UReal(-3.0, 3.0) : UReal

	Expression : 
		UReal(-3, 0).setUncertainty(3.0)
		-> UReal(-3.0, 3.0) : UReal

	Expression : 
		UReal(-3, 0).setUncertainty(3 / 0)
		-> null : OclVoid

	Expression : 
		UReal(-3, 2.3).sqrt()
		-> null : OclVoid

	Expression : 
		UReal(0, 2).sqrt()
		-> null : OclVoid

	Expression : 
		UReal(4, 0).sqrt()
		-> UReal(2.0, 0.0) : UReal

	Expression : 
		UReal(4, 2).sqrt()
		-> UReal(2.0, 0.5) : UReal

	Expression : 
		UReal(0, 0).power(0)
		-> null : OclVoid

	Expression : 
		UReal(0, 0).power(3)
		-> UReal(0.0, 0.0) : UReal

	Expression : 
		UReal(0, 0).power(-2)
		-> null : OclVoid

	Expression : 
		UReal(0, 0).power(3.5)
		-> UReal(0.0, 0.0) : UReal

	Expression : 
		UReal(0, 2).power(0)
		-> null : OclVoid

	Expression : 
		UReal(0, 4).power(3)
		-> UReal(0.0, 0.0) : UReal

	Expression : 
		UReal(0, 3).power(-3)
		-> null : OclVoid

	Expression : 
		UReal(0, 1).power(3.5)
		-> UReal(0.0, 0.0) : UReal

	Expression : 
		UReal(3, 0).power(0)
		-> UReal(1.0, 0.0) : UReal

	Expression : 
		UReal(2, 0).power(3)
		-> UReal(8.0, 0.0) : UReal

	Expression : 
		UReal(4, 0).power(-2)
		-> UReal(0.0625, 0.0) : UReal

	Expression : 
		UReal(4, 0).power(1.5)
		-> UReal(8.0, 0.0) : UReal

	Expression : 
		UReal(1.5, 3.2).power(0)
		-> UReal(1.0, 0.0) : UReal

	Expression : 
		UReal(2, 4).power(4)
		-> UReal(16.0, 128.0) : UReal

	Expression : 
		UReal(1, 3).power(-2)
		-> UReal(1.0, 6.0) : UReal

	Expression : 
		UReal(1, 2).power(0.25)
		-> UReal(1.0, 0.5) : UReal

	Expression : 
		UReal(-2, 5).power(1/2).equals( UReal(-2, 5).sqrt() )
		-> true : Boolean

	Expression : 
		UReal(0, 5).power(1/2).equals( UReal(0, 5).sqrt() )
		-> true : Boolean

	Expression : 
		UReal(3.0, 2.3).neg()
		-> UReal(-3.0, 2.3) : UReal

	Expression : 
		UReal(0.0, 2.3).neg()
		-> UReal(0.0, 2.3) : UReal

	Expression : 
		UReal(-3.0, 2.3).neg()
		-> UReal(3.0, 2.3) : UReal

	Expression : 
		UReal(3.7, 3.2).floor()
		-> UReal(3.0, 3.2) : UReal

	Expression : 
		UReal(3.2, 3.2).floor()
		-> UReal(3.0, 3.2) : UReal

	Expression : 
		UReal(3.5, 3.2).floor()
		-> UReal(3.0, 3.2) : UReal

	Expression : 
		UReal(2, 3).round()
		-> UReal(2.0, 3.0) : UReal

	Expression : 
		UReal(2.7, 3).round()
		-> UReal(3.0, 3.0) : UReal

	Expression : 
		UReal(2.5, 3).round()
		-> UReal(3.0, 3.0) : UReal

	Expression : 
		UReal(2.2, 3).round()
		-> UReal(2.0, 3.0) : UReal

	Expression : 
		UReal(-0.5, 3).round()
		-> UReal(0.0, 3.0) : UReal

	Expression : 
		UReal(-0.8, 3).round()
		-> UReal(-1.0, 3.0) : UReal

	Expression : 
		(UReal(3.2, 3).floor()).equals( UReal(3.2, 3.0).round() )
		-> true : Boolean

	Expression : 
		(UReal(3, 3).floor()).equals( UReal(3, 3.0).round() )
		-> true : Boolean

	Expression : 
		(UReal(3.5, 3).floor()).equals( UReal(3.5, 3.0).round() )
		-> false : Boolean

	Expression : 
		(UReal(3.9, 3).floor()).equals( UReal(3.9, 3.0).round() )
		-> false : Boolean

	Expression : 
		UReal(8, 0.75).inv()
		-> UReal(0.125, 0.01171875) : UReal

	Expression : 
		UReal(0, 0.5).inv()
		-> null : OclVoid

	Expression : 
		UReal(0.0, 0.0).min(UReal(0.0, 0.0))
		-> UReal(0.0, 0.0) : UReal

	Expression : 
		UReal(0.0, 0.0).min(UReal(1.0, 0.0))
		-> UReal(0.0, 0.0) : UReal

	Expression : 
		UReal(1.0, 0.0).min(UReal(0.0, 0.0))
		-> UReal(0.0, 0.0) : UReal

	Expression : 
		UReal(3.0, 0.0).min(UReal(0.0, 0.0))
		-> UReal(0.0, 0.0) : UReal

	Expression : 
		UReal(0.0, 0.0).min(UReal(3.0, 0.0))
		-> UReal(0.0, 0.0) : UReal

	Expression : 
		UReal(0.0, 0.0).min(UReal(3.0, 2.0))
		-> UReal(0.0, 0.0) : UReal

	Expression : 
		UReal(3.0, 2.0).min(UReal(0.0, 0.0))
		-> UReal(0.0, 0.0) : UReal

	Expression : 
		UReal(3.0, 0.0).min(UReal(0.0, 2.0))
		-> UReal(0.0, 2.0) : UReal

	Expression : 
		UReal(0.0, 2.0).min(UReal(3.0, 0.0))
		-> UReal(0.0, 2.0) : UReal

	Expression : 
		UReal(3.0, 2.0).min(UReal(0.0, 0.0))
		-> UReal(0.0, 0.0) : UReal

	Expression : 
		UReal(0.0, 0.0).min(UReal(3.0, 2.0))
		-> UReal(0.0, 0.0) : UReal

	Expression : 
		UReal(0.0, 2.0).min(UReal(0.0, 0.2))
		-> UReal(0.0, 2.0) : UReal

	Expression : 
		UReal(0.0, 2.0).min(UReal(1.0, 0.0))
		-> UReal(0.0, 2.0) : UReal

	Expression : 
		UReal(1.0, 0.0).min(UReal(0.0, 2.0))
		-> UReal(0.0, 2.0) : UReal

	Expression : 
		UReal(0.0, 2.0).min(UReal(-1.0, 0.25))
		-> UReal(-1.0, 0.25) : UReal

	Expression : 
		UReal(-1.0, 0.25).min(UReal(0.0, 2.0))
		-> UReal(-1.0, 0.25) : UReal

	Expression : 
		UReal(0.0, 2.0).min(UReal(5.0, 2.0))
		-> UReal(0.0, 2.0) : UReal

	Expression : 
		UReal(5.0, 2.0).min(UReal(0.0, 2.0))
		-> UReal(0.0, 2.0) : UReal

	Expression : 
		UReal(0.0, 0.0).min(0)
		-> UReal(0.0, 0.0) : UReal

	Expression : 
		0.min(UReal(0.0, 0.0))
		-> UReal(0.0, 0.0) : UReal

	Expression : 
		UReal(0.0, 0.0).min(1)
		-> UReal(0.0, 0.0) : UReal

	Expression : 
		1.min(UReal(0.0, 0.0))
		-> UReal(0.0, 0.0) : UReal

	Expression : 
		UReal(1.0, 0.0).min(0)
		-> UReal(0.0, 0.0) : UReal

	Expression : 
		0.min(UReal(1.0, 0.0))
		-> UReal(0.0, 0.0) : UReal

	Expression : 
		UReal(0.0, 2.0).min(3)
		-> UReal(0.0, 2.0) : UReal

	Expression : 
		3.min(UReal(0.0, 2.0))
		-> UReal(0.0, 2.0) : UReal

	Expression : 
		UReal(3.0, 2.0).min(0)
		-> UReal(0.0, 0.0) : UReal

	Expression : 
		0.min(UReal(3.0, 2.0))
		-> UReal(0.0, 0.0) : UReal

	Expression : 
		UReal(0.0, 0.0).min(0.0)
		-> UReal(0.0, 0.0) : UReal

	Expression : 
		0.0.min(UReal(0.0, 0.0))
		-> UReal(0.0, 0.0) : UReal

	Expression : 
		UReal(0.0, 0.0).min(1.5)
		-> UReal(0.0, 0.0) : UReal

	Expression : 
		1.5.min(UReal(0.0, 0.0))
		-> UReal(0.0, 0.0) : UReal

	Expression : 
		UReal(1.0, 0.0).min(0)
		-> UReal(0.0, 0.0) : UReal

	Expression : 
		0.min(UReal(1.0, 0.0))
		-> UReal(0.0, 0.0) : UReal

	Expression : 
		UReal(0.0, 2.0).min(2.5)
		-> UReal(0.0, 2.0) : UReal

	Expression : 
		2.5.min(UReal(0.0, 2.0))
		-> UReal(0.0, 2.0) : UReal

	Expression : 
		0.min(UReal(3.0, 2.0))
		-> UReal(0.0, 0.0) : UReal

	Expression : 
		UReal(0.0, 0.0).min(3 / 0)
		-> null : OclVoid

	Expression : 
		(3 / 0).min(UReal(0.0, 0.0))
		-> null : OclVoid

	Expression : 
		UReal(0.0, 0.0).max(UReal(0.0, 0.0))
		-> UReal(0.0, 0.0) : UReal

	Expression : 
		UReal(0.0, 0.0).max(UReal(1.0, 0.0))
		-> UReal(1.0, 0.0) : UReal

	Expression : 
		UReal(1.0, 0.0).max(UReal(0.0, 0.0))
		-> UReal(1.0, 0.0) : UReal

	Expression : 
		UReal(3.0, 0.0).max(UReal(0.0, 0.0))
		-> UReal(3.0, 0.0) : UReal

	Expression : 
		UReal(0.0, 0.0).max(UReal(3.0, 0.0))
		-> UReal(3.0, 0.0) : UReal

	Expression : 
		UReal(0.0, 0.0).max(UReal(3.0, 2.0))
		-> UReal(3.0, 2.0) : UReal

	Expression : 
		UReal(3.0, 2.0).max(UReal(0.0, 0.0))
		-> UReal(3.0, 2.0) : UReal

	Expression : 
		UReal(3.0, 0.0).max(UReal(0.0, 2.0))
		-> UReal(3.0, 0.0) : UReal

	Expression : 
		UReal(0.0, 2.0).max(UReal(3.0, 0.0))
		-> UReal(3.0, 0.0) : UReal

	Expression : 
		UReal(3.0, 2.0).max(UReal(0.0, 0.0))
		-> UReal(3.0, 2.0) : UReal

	Expression : 
		UReal(0.0, 0.0).max(UReal(3.0, 2.0))
		-> UReal(3.0, 2.0) : UReal

	Expression : 
		UReal(0.0, 2.0).max(UReal(0.0, 0.2))
		-> UReal(0.0, 2.0) : UReal

	Expression : 
		UReal(0.0, 2.0).max(UReal(1.0, 0.0))
		-> UReal(1.0, 0.0) : UReal

	Expression : 
		UReal(1.0, 0.0).max(UReal(0.0, 2.0))
		-> UReal(1.0, 0.0) : UReal

	Expression : 
		UReal(0.0, 2.0).max(UReal(-1.0, 0.25))
		-> UReal(0.0, 2.0) : UReal

	Expression : 
		UReal(-1.0, 0.25).max(UReal(0.0, 2.0))
		-> UReal(0.0, 2.0) : UReal

	Expression : 
		UReal(0.0, 2.0).max(UReal(5.0, 2.0))
		-> UReal(5.0, 2.0) : UReal

	Expression : 
		UReal(0.0, 0.0).max(0)
		-> UReal(0.0, 0.0) : UReal

	Expression : 
		0.max(UReal(0.0, 0.0))
		-> UReal(0.0, 0.0) : UReal

	Expression : 
		UReal(0.0, 0.0).max(1)
		-> UReal(1.0, 0.0) : UReal

	Expression : 
		1.max(UReal(0.0, 0.0))
		-> UReal(1.0, 0.0) : UReal

	Expression : 
		UReal(1.0, 0.0).max(0)
		-> UReal(1.0, 0.0) : UReal

	Expression : 
		0.max(UReal(1.0, 0.0))
		-> UReal(1.0, 0.0) : UReal

	Expression : 
		UReal(0.0, 2.0).max(3)
		-> UReal(3.0, 0.0) : UReal

	Expression : 
		3.max(UReal(0.0, 2.0))
		-> UReal(3.0, 0.0) : UReal

	Expression : 
		UReal(3.0, 2.0).max(0)
		-> UReal(3.0, 2.0) : UReal

	Expression : 
		0.max(UReal(3.0, 2.0))
		-> UReal(3.0, 2.0) : UReal

	Expression : 
		UReal(0.0, 0.0).max(0.0)
		-> UReal(0.0, 0.0) : UReal

	Expression : 
		0.0.max(UReal(0.0, 0.0))
		-> UReal(0.0, 0.0) : UReal

	Expression : 
		UReal(0.0, 0.0).max(1.5)
		-> UReal(1.5, 0.0) : UReal

	Expression : 
		1.5.max(UReal(0.0, 0.0))
		-> UReal(1.5, 0.0) : UReal

	Expression : 
		UReal(1.0, 0.0).max(0)
		-> UReal(1.0, 0.0) : UReal

	Expression : 
		0.max(UReal(1.0, 0.0))
		-> UReal(1.0, 0.0) : UReal

	Expression : 
		UReal(0.0, 2.0).max(2.5)
		-> UReal(2.5, 0.0) : UReal

	Expression : 
		2.5.max(UReal(0.0, 2.0))
		-> UReal(2.5, 0.0) : UReal

	Expression : 
		UReal(3.0, 2.0).max(0)
		-> UReal(3.0, 2.0) : UReal

	Expression : 
		0.max(UReal(3.0, 2.0))
		-> UReal(3.0, 2.0) : UReal

	Expression : 
		UReal(3.0, 2.0).max(3 / 0)
		-> null : OclVoid

	Expression : 
		(3 / 0).max(UReal(3.0, 2.0))
		-> null : OclVoid

	Expression : 
		UReal(-2, 0).toReal()
		-> -2.0 : Real

	Expression : 
		UReal(-2, 2).toReal()
		-> -2.0 : Real

	Expression : 
		UReal(0, 0).toReal()
		-> 0.0 : Real

	Expression : 
		UReal(0, 3).toReal()
		-> 0.0 : Real

	Expression : 
		UReal(3, 0).toReal()
		-> 3.0 : Real

	Expression : 
		UReal(3, 5).toReal()
		-> 3.0 : Real

	Expression : 
		UReal(0.5, 3.2).toReal()
		-> 0.5 : Real

	Expression : 
		UReal(-2, 0).toInteger()
		-> -2 : Integer

	Expression : 
		UReal(-2, 2).toInteger()
		-> -2 : Integer

	Expression : 
		UReal(0, 0).toInteger()
		-> 0 : Integer

	Expression : 
		UReal(0, 3).toInteger()
		-> 0 : Integer

	Expression : 
		UReal(3, 0).toInteger()
		-> 3 : Integer

	Expression : 
		UReal(3, 5).toInteger()
		-> 3 : Integer

	Expression : 
		UReal(0.5, 3.2).toInteger()
		-> 0 : Integer

	Expression : 
		UReal(5.0, 0.3).toUInteger()
		-> UInteger(5, 0.3) : UInteger

	Expression : 
		UReal(5.5, 5).toUInteger()
		-> UInteger(5, 5.0) : UInteger

	Expression : 
		UReal(0, -5).toUInteger()
		-> UInteger(0, 5.0) : UInteger

	Expression : 
		UReal(-5.3, 3.75).toUInteger()
		-> UInteger(-5, 3.75) : UInteger

	Expression : 
		UReal(-2, 0).toString()
		-> 'UReal(-2.0, 0.0)' : String

	Expression : 
		UReal(-2, 2).toString()
		-> 'UReal(-2.0, 2.0)' : String

	Expression : 
		UReal(0, 0).toString()
		-> 'UReal(0.0, 0.0)' : String

	Expression : 
		UReal(0, 3).toString()
		-> 'UReal(0.0, 3.0)' : String

	Expression : 
		UReal(3, 0).toString()
		-> 'UReal(3.0, 0.0)' : String

	Expression : 
		UReal(3, 5).toString()
		-> 'UReal(3.0, 5.0)' : String

	Expression : 
		UReal(0.5, 3.2).toString()
		-> 'UReal(0.5, 3.2)' : String

	Expression : 
		UReal(-9, 0) + UReal(-9, 0)
		-> UReal(-18.0, 0.0) : UReal

	Expression : 
		UReal(-3, 0) + UReal(-3, 9)
		-> UReal(-6.0, 9.0) : UReal

	Expression : 
		UReal(-7, 0) + UReal(3, 0)
		-> UReal(-4.0, 0.0) : UReal

	Expression : 
		UReal(-2, 0) + UReal(10, 7)
		-> UReal(8.0, 7.0) : UReal

	Expression : 
		UReal(-9, 7) + UReal(-9, 0)
		-> UReal(-18.0, 7.0) : UReal

	Expression : 
		UReal(-3, 3) + UReal(-3, 4)
		-> UReal(-6.0, 5.0) : UReal

	Expression : 
		UReal(-9, 3) + UReal(7, 0)
		-> UReal(-2.0, 3.0) : UReal

	Expression : 
		UReal(-6, 3) + UReal(10, 4)
		-> UReal(4.0, 5.0) : UReal

	Expression : 
		UReal(0, 0) + UReal(0, 0)
		-> UReal(0.0, 0.0) : UReal

	Expression : 
		UReal(0, 0) + UReal(0, 1)
		-> UReal(0.0, 1.0) : UReal

	Expression : 
		UReal(0, 0) + UReal(6, 0)
		-> UReal(6.0, 0.0) : UReal

	Expression : 
		UReal(0, 0) + UReal(9, 4)
		-> UReal(9.0, 4.0) : UReal

	Expression : 
		UReal(0, 2) + UReal(0, 0)
		-> UReal(0.0, 2.0) : UReal

	Expression : 
		UReal(0, 3) + UReal(0, 4)
		-> UReal(0.0, 5.0) : UReal

	Expression : 
		UReal(0, 4) + UReal(2, 0)
		-> UReal(2.0, 4.0) : UReal

	Expression : 
		UReal(0, 3) + UReal(8, 4)
		-> UReal(8.0, 5.0) : UReal

	Expression : 
		UReal(9, 0) + UReal(9, 0)
		-> UReal(18.0, 0.0) : UReal

	Expression : 
		UReal(3, 0) + UReal(3, 1)
		-> UReal(6.0, 1.0) : UReal

	Expression : 
		UReal(7, 0) + UReal(8, 0)
		-> UReal(15.0, 0.0) : UReal

	Expression : 
		UReal(2, 0) + UReal(7, 8)
		-> UReal(9.0, 8.0) : UReal

	Expression : 
		UReal(9, 9) + UReal(9, 0)
		-> UReal(18.0, 9.0) : UReal

	Expression : 
		UReal(3, 3) + UReal(3, 4)
		-> UReal(6.0, 5.0) : UReal

	Expression : 
		UReal(9, 2) + UReal(10, 0)
		-> UReal(19.0, 2.0) : UReal

	Expression : 
		UReal(6, 3) + UReal(1, 4)
		-> UReal(7.0, 5.0) : UReal

	Expression : 
		UReal(-3, 0) + -3.0
		-> UReal(-6.0, 0.0) : UReal

	Expression : 
		UReal(-6, 0) + -1.2
		-> UReal(-7.2, 0.0) : UReal

	Expression : 
		UReal(-5, 3) + -5.0
		-> UReal(-10.0, 3.0) : UReal

	Expression : 
		UReal(-8, 5) + -2.0
		-> UReal(-10.0, 5.0) : UReal

	Expression : 
		UReal(0, 0) + 0.0
		-> UReal(0.0, 0.0) : UReal

	Expression : 
		UReal(0, 0) + 3.0
		-> UReal(3.0, 0.0) : UReal

	Expression : 
		UReal(0, 3) + 0.0
		-> UReal(0.0, 3.0) : UReal

	Expression : 
		UReal(0, 5) + -5.0
		-> UReal(-5.0, 5.0) : UReal

	Expression : 
		UReal(5, 0) + 5.0
		-> UReal(10.0, 0.0) : UReal

	Expression : 
		UReal(3, 0) + 0.6
		-> UReal(3.6, 0.0) : UReal

	Expression : 
		UReal(7, 3) + 7.0
		-> UReal(14.0, 3.0) : UReal

	Expression : 
		UReal(2, 5) + 0.5
		-> UReal(2.5, 5.0) : UReal

	Expression : 
		UReal(-3, 0) + -3
		-> UReal(-6.0, 0.0) : UReal

	Expression : 
		UReal(-6, 0) + -12
		-> UReal(-18.0, 0.0) : UReal

	Expression : 
		UReal(-5, 3) + -5
		-> UReal(-10.0, 3.0) : UReal

	Expression : 
		UReal(-8, 5) + -2
		-> UReal(-10.0, 5.0) : UReal

	Expression : 
		UReal(0, 0) + 0
		-> UReal(0.0, 0.0) : UReal

	Expression : 
		UReal(0, 0) + 3
		-> UReal(3.0, 0.0) : UReal

	Expression : 
		UReal(0, 3) + 0
		-> UReal(0.0, 3.0) : UReal

	Expression : 
		UReal(0, 5) + -5
		-> UReal(-5.0, 5.0) : UReal

	Expression : 
		UReal(5, 0) + 5
		-> UReal(10.0, 0.0) : UReal

	Expression : 
		UReal(3, 0) + 56
		-> UReal(59.0, 0.0) : UReal

	Expression : 
		UReal(7, 3) + 7
		-> UReal(14.0, 3.0) : UReal

	Expression : 
		UReal(2, 5) + 65
		-> UReal(67.0, 5.0) : UReal

	Expression : 
		UReal(2, 5) + 3 / 0
		-> null : OclVoid

	Expression : 
		( UReal(2, 5) + UReal(0, 0) ).equals( UReal(2, 5) )
		-> true : Boolean

	Expression : 
		( UReal(2, 5) + 0.0 ).equals( UReal(2, 5) )
		-> true : Boolean

	Expression : 
		( UReal(2, 5) + 0 ).equals( UReal(2, 5) )
		-> true : Boolean

	Expression : 
		( UReal(6, 3) + UReal(5, 0.3) ).equals( UReal(5, 0.3) + UReal(6, 3) )
		-> true : Boolean

	Expression : 
		( UReal(9, 32) + 0.53 ).equals( 0.53 + UReal(9, 32) )
		-> true : Boolean

	Expression : 
		( UReal(2, 3) + 5 ).equals( 5 + UReal(2, 3) )
		-> true : Boolean

	Expression : 
		( UReal(6, 3) + (UReal(5, 3) + UReal(9,2)) ).equals( (UReal(6, 3) + UReal(5, 3)) + UReal(9,2) )
		-> true : Boolean

	Expression : 
		( UReal(6, 3) + (5.3 + UReal(9,2)) ).equals( (UReal(6, 3) + 5.3) + UReal(9,2) )
		-> true : Boolean

	Expression : 
		( UReal(6, 3) + (5 + UReal(9,2)) ).equals( (UReal(6, 3) + 5) + UReal(9,2) )
		-> true : Boolean

	Expression : 
		( UReal(6, 3) + (5 + 2) ).equals( (UReal(6, 3) + 5) + 2 )
		-> true : Boolean

	Expression : 
		( 3.5 + (5 + UReal(9,2)) ).equals( (3.5 + 5) + UReal(9,2) )
		-> true : Boolean

	Expression : 
		UReal(-9, 0) - UReal(-9, 0)
		-> UReal(0.0, 0.0) : UReal

	Expression : 
		UReal(-5, 0) - UReal(-5, 3)
		-> UReal(0.0, 3.0) : UReal

	Expression : 
		UReal(-4, 0) - UReal(2, 0)
		-> UReal(-6.0, 0.0) : UReal

	Expression : 
		UReal(-10, 0) - UReal(4, 1)
		-> UReal(-14.0, 1.0) : UReal

	Expression : 
		UReal(-9, 9) - UReal(-9, 0)
		-> UReal(0.0, 9.0) : UReal

	Expression : 
		UReal(-2, 3) - UReal(-2, 4)
		-> UReal(0.0, 5.0) : UReal

	Expression : 
		UReal(-6, 2) - UReal(5, 0)
		-> UReal(-11.0, 2.0) : UReal

	Expression : 
		UReal(-2, 3) - UReal(4, 4)
		-> UReal(-6.0, 5.0) : UReal

	Expression : 
		UReal(0, 0) - UReal(0, 0)
		-> UReal(0.0, 0.0) : UReal

	Expression : 
		UReal(0, 0) - UReal(0, 4)
		-> UReal(0.0, 4.0) : UReal

	Expression : 
		UReal(0, 0) - UReal(6, 0)
		-> UReal(-6.0, 0.0) : UReal

	Expression : 
		UReal(0, 0) - UReal(7, 3)
		-> UReal(-7.0, 3.0) : UReal

	Expression : 
		UReal(0, 4) - UReal(0, 0)
		-> UReal(0.0, 4.0) : UReal

	Expression : 
		UReal(0, 4) - UReal(0, 3)
		-> UReal(0.0, 5.0) : UReal

	Expression : 
		UReal(0, 4) - UReal(1, 0)
		-> UReal(-1.0, 4.0) : UReal

	Expression : 
		UReal(0, 4) - UReal(2, 3)
		-> UReal(-2.0, 5.0) : UReal

	Expression : 
		UReal(9, 0) - UReal(9, 0)
		-> UReal(0.0, 0.0) : UReal

	Expression : 
		UReal(5, 0) - UReal(5, 3)
		-> UReal(0.0, 3.0) : UReal

	Expression : 
		UReal(4, 0) - UReal(8, 0)
		-> UReal(-4.0, 0.0) : UReal

	Expression : 
		UReal(10, 0) - UReal(10, 12)
		-> UReal(0.0, 12.0) : UReal

	Expression : 
		UReal(9, 5) - UReal(9, 0)
		-> UReal(0.0, 5.0) : UReal

	Expression : 
		UReal(2, 3) - UReal(2, 4)
		-> UReal(0.0, 5.0) : UReal

	Expression : 
		UReal(6, 1) - UReal(4, 0)
		-> UReal(2.0, 1.0) : UReal

	Expression : 
		UReal(2, 3) - UReal(5, 4)
		-> UReal(-3.0, 5.0) : UReal

	Expression : 
		UReal(-3, 0) - -3.0
		-> UReal(0.0, 0.0) : UReal

	Expression : 
		UReal(-6, 0) - -1.2
		-> UReal(-4.8, 0.0) : UReal

	Expression : 
		UReal(-5, 3) - -5.0
		-> UReal(0.0, 3.0) : UReal

	Expression : 
		UReal(-8, 5) - -2.0
		-> UReal(-6.0, 5.0) : UReal

	Expression : 
		UReal(0, 0) - 0.0
		-> UReal(0.0, 0.0) : UReal

	Expression : 
		UReal(0, 0) - 3.0
		-> UReal(-3.0, 0.0) : UReal

	Expression : 
		UReal(0, 3) - 0.0
		-> UReal(0.0, 3.0) : UReal

	Expression : 
		UReal(0, 5) - -5.0
		-> UReal(5.0, 5.0) : UReal

	Expression : 
		UReal(5, 0) - 5.0
		-> UReal(0.0, 0.0) : UReal

	Expression : 
		UReal(3, 0) - 0.6
		-> UReal(2.4, 0.0) : UReal

	Expression : 
		UReal(7, 3) - 7.0
		-> UReal(0.0, 3.0) : UReal

	Expression : 
		UReal(2, 5) - 0.5
		-> UReal(1.5, 5.0) : UReal

	Expression : 
		UReal(-3, 0) - -3
		-> UReal(0.0, 0.0) : UReal

	Expression : 
		UReal(-6, 0) - -12
		-> UReal(6.0, 0.0) : UReal

	Expression : 
		UReal(-5, 3) - -5
		-> UReal(0.0, 3.0) : UReal

	Expression : 
		UReal(-8, 5) - -2
		-> UReal(-6.0, 5.0) : UReal

	Expression : 
		UReal(0, 0) - 0
		-> UReal(0.0, 0.0) : UReal

	Expression : 
		UReal(0, 0) - 3
		-> UReal(-3.0, 0.0) : UReal

	Expression : 
		UReal(0, 3) - 0
		-> UReal(0.0, 3.0) : UReal

	Expression : 
		UReal(0, 5) - -5
		-> UReal(5.0, 5.0) : UReal

	Expression : 
		UReal(5, 0) - 5
		-> UReal(0.0, 0.0) : UReal

	Expression : 
		UReal(3, 0) - 56
		-> UReal(-53.0, 0.0) : UReal

	Expression : 
		UReal(7, 3) - 7
		-> UReal(0.0, 3.0) : UReal

	Expression : 
		UReal(2, 5) - 65
		-> UReal(-63.0, 5.0) : UReal

	Expression : 
		( UReal(3, 0) - 3.0 ).equals( 0 )
		-> true : Boolean

	Expression : 
		( UReal(3, 0) - 3 ).equals( 0 )
		-> true : Boolean

	Expression : 
		( 3.0 - UReal(3, 0) ).equals( 0 )
		-> true : Boolean

	Expression : 
		( UReal(3, 4) - UReal(5, 2) ).equals( -(UReal(5, 2) - UReal(3, 4)) )
		-> true : Boolean

	Expression : 
		( UReal(3, 4) - 5 ).equals( -(5 - UReal(3, 4)) )
		-> true : Boolean

	Expression : 
		( 4.3 - UReal(5, 2) ).equals( -(UReal(5, 2) - 4.3) )
		-> true : Boolean

	Expression : 
		( UReal(3, 4) - (UReal(5, 2) - UReal(2, 0.53)) ).equals( (UReal(3, 4) - UReal(5, 2)) - UReal(2, 0.53) )
		-> false : Boolean

	Expression : 
		( UReal(3, 0) - (UReal(5, 0) - UReal(2, 0)) ).equals( (UReal(3, 0) - UReal(5, 0)) - UReal(2, 0) )
		-> false : Boolean

	Expression : 
		( -UReal(3, 4) ).equals( UReal(3, 4).neg() )
		-> true : Boolean

	Expression : 
		( - UReal(-3, 0) ).equals( UReal(-3, 0).neg() )
		-> true : Boolean

	Expression : 
		( 0 - UReal(3, 4) ).equals( UReal(3, 4).neg() )
		-> true : Boolean

	Expression : 
		( (UReal(1, 0) - 1) - UReal(3, 4) ).equals( UReal(3, 4).neg() )
		-> true : Boolean

	Expression : 
		UReal(-9, 0) * UReal(-9, 0)
		-> UReal(81.0, 0.0) : UReal

	Expression : 
		UReal(-5, 0) * UReal(-5, 3)
		-> UReal(25.0, 15.0) : UReal

	Expression : 
		UReal(-4, 0) * UReal(2, 0)
		-> UReal(-8.0, 0.0) : UReal

	Expression : 
		UReal(-10, 0) * UReal(4, 1)
		-> UReal(-40.0, 10.0) : UReal

	Expression : 
		UReal(-9, 9) * UReal(-9, 0)
		-> UReal(81.0, 81.0) : UReal

	Expression : 
		UReal(-2, 3) * UReal(-2, 4)
		-> UReal(4.0, 10.0) : UReal

	Expression : 
		UReal(-6, 2) * UReal(5, 0)
		-> UReal(-30.0, 10.0) : UReal

	Expression : 
		UReal(-2, 3) * UReal(2, 4)
		-> UReal(-4.0, 10.0) : UReal

	Expression : 
		UReal(0, 0) * UReal(0, 0)
		-> UReal(0.0, 0.0) : UReal

	Expression : 
		UReal(0, 0) * UReal(0, 4)
		-> UReal(0.0, 0.0) : UReal

	Expression : 
		UReal(0, 0) * UReal(6, 0)
		-> UReal(0.0, 0.0) : UReal

	Expression : 
		UReal(0, 0) * UReal(7, 3)
		-> UReal(0.0, 0.0) : UReal

	Expression : 
		UReal(0, 4) * UReal(0, 0)
		-> UReal(0.0, 0.0) : UReal

	Expression : 
		UReal(0, 4) * UReal(0, 3)
		-> UReal(0.0, 0.0) : UReal

	Expression : 
		UReal(0, 4) * UReal(1, 0)
		-> UReal(0.0, 4.0) : UReal

	Expression : 
		UReal(0, 4) * UReal(2, 3)
		-> UReal(0.0, 8.0) : UReal

	Expression : 
		UReal(9, 0) * UReal(9, 0)
		-> UReal(81.0, 0.0) : UReal

	Expression : 
		UReal(5, 0) * UReal(5, 3)
		-> UReal(25.0, 15.0) : UReal

	Expression : 
		UReal(4, 0) * UReal(8, 0)
		-> UReal(32.0, 0.0) : UReal

	Expression : 
		UReal(10, 0) * UReal(10, 12)
		-> UReal(100.0, 120.0) : UReal

	Expression : 
		UReal(9, 5) * UReal(9, 0)
		-> UReal(81.0, 45.0) : UReal

	Expression : 
		UReal(2, 3) * UReal(2, 4)
		-> UReal(4.0, 10.0) : UReal

	Expression : 
		UReal(6, 1) * UReal(4, 0)
		-> UReal(24.0, 4.0) : UReal

	Expression : 
		UReal(2, 3) * UReal(5, 4)
		-> UReal(10.0, 17.0) : UReal

	Expression : 
		UReal(-3, 0) * -3.0
		-> UReal(9.0, 0.0) : UReal

	Expression : 
		UReal(-6, 0) * -1.2
		-> UReal(7.2, 0.0) : UReal

	Expression : 
		UReal(-5, 3) * -5.0
		-> UReal(25.0, 15.0) : UReal

	Expression : 
		UReal(-8, 5) * -2.0
		-> UReal(16.0, 10.0) : UReal

	Expression : 
		UReal(0, 0) * 0.0
		-> UReal(0.0, 0.0) : UReal

	Expression : 
		UReal(0, 0) * 3.0
		-> UReal(0.0, 0.0) : UReal

	Expression : 
		UReal(0, 3) * 0.0
		-> UReal(0.0, 0.0) : UReal

	Expression : 
		UReal(0, 5) * -5.0
		-> UReal(0.0, 25.0) : UReal

	Expression : 
		UReal(5, 0) * 5.0
		-> UReal(25.0, 0.0) : UReal

	Expression : 
		UReal(3, 0) * 0.6
		-> UReal(1.8, 0.0) : UReal

	Expression : 
		UReal(7, 3) * 7.0
		-> UReal(49.0, 21.0) : UReal

	Expression : 
		UReal(2, 5) * 0.5
		-> UReal(1.0, 2.5) : UReal

	Expression : 
		UReal(-3, 0) * -3
		-> UReal(9.0, 0.0) : UReal

	Expression : 
		UReal(-6, 0) * -12
		-> UReal(72.0, 0.0) : UReal

	Expression : 
		UReal(-5, 3) * -5
		-> UReal(25.0, 15.0) : UReal

	Expression : 
		UReal(-8, 5) * -2
		-> UReal(16.0, 10.0) : UReal

	Expression : 
		UReal(0, 0) * 0
		-> UReal(0.0, 0.0) : UReal

	Expression : 
		UReal(0, 0) * 3
		-> UReal(0.0, 0.0) : UReal

	Expression : 
		UReal(0, 3) * 0
		-> UReal(0.0, 0.0) : UReal

	Expression : 
		UReal(0, 5) * -5
		-> UReal(0.0, 25.0) : UReal

	Expression : 
		UReal(5, 0) * 5
		-> UReal(25.0, 0.0) : UReal

	Expression : 
		UReal(3, 0) * 56
		-> UReal(168.0, 0.0) : UReal

	Expression : 
		UReal(7, 3) * 7
		-> UReal(49.0, 21.0) : UReal

	Expression : 
		UReal(2, 5) * 65
		-> UReal(130.0, 325.0) : UReal

	Expression : 
		( UReal(3, 2) * UReal(5, 2) ).equals( UReal(5, 2) * UReal(3, 2) )
		-> true : Boolean

	Expression : 
		( UReal(3, 2) * UReal(5, 0) ).equals( UReal(5, 0) * UReal(3, 2) )
		-> true : Boolean

	Expression : 
		( UReal(3, 2) * 5 ).equals( 5 * UReal(3, 2) )
		-> true : Boolean

	Expression : 
		( UReal(3, 2) * -5.53 ).equals( -5.53 * UReal(3, 2) )
		-> true : Boolean

	Expression : 
		( UReal(3, 5) * (UReal(5, 1) * UReal(1, 2)) ).equals( (UReal(3, 5) * UReal(5, 1)) * UReal(1, 2) )
		-> true : Boolean

	Expression : 
		( UReal(3, 5) * (5.1 * UReal(1, 2)) ).equals( (UReal(3, 5) * 5.1) * UReal(1, 2) )
		-> true : Boolean

	Expression : 
		( UReal(3, 5) * (5.1 * 1.2) ).equals( (UReal(3, 5) * 5.1) * 1.2 )
		-> true : Boolean

	Expression : 
		( UReal(3, 5) * (5 * UReal(1, 2)) ).equals( (UReal(3, 5) * 5) * UReal(1, 2) )
		-> true : Boolean

	Expression : 
		( UReal(3, 5) * (5 * 1.2) ).equals( (UReal(3, 5) * 5) * 1.2 )
		-> true : Boolean

	Expression : 
		( UReal(3, 5) * (5 * 2) ).equals( (UReal(3, 5) * 5) * 2 )
		-> true : Boolean

	Expression : 
		( UReal(2,1) * (UReal(3,1) + UReal(5, 0.2)) ).equals( UReal(2,1) * UReal(3,1) +  UReal(2,1) * UReal(5, 0.2) )
		-> false : Boolean

	Expression : 
		( 5.1 * (UReal(3, 2) + UReal(1, 2)) ).equals( (5.1 * UReal(3, 2)) + (5.1 * UReal(1, 2)) )
		-> true : Boolean

	Expression : 
		( 2 * (UReal(3, 2) + UReal(1, 2)) ).equals( (2 * UReal(3, 2)) + (2 * UReal(1, 2)) )
		-> true : Boolean

	Expression : 
		( UReal(3, 2) * UReal(1, 0) ).equals( UReal(3, 2) )
		-> true : Boolean

	Expression : 
		( UReal(3, 2) * 1 ).equals( UReal(3, 2) )
		-> true : Boolean

	Expression : 
		UReal(-9, 0) / UReal(-9, 0)
		-> UReal(1.0, 0.0) : UReal

	Expression : 
		UReal(-5, 0) / UReal(-5, 3)
		-> UReal(1.0, 0.12) : UReal

	Expression : 
		UReal(-4, 0) / UReal(2, 0)
		-> UReal(-2.0, 0.0) : UReal

	Expression : 
		UReal(-10, 0) / UReal(4, 1)
		-> UReal(-2.5, 0.0625) : UReal

	Expression : 
		UReal(-9, 9) / UReal(-9, 0)
		-> UReal(1.0, 1.0) : UReal

	Expression : 
		UReal(-2, 3) / UReal(-2, 4)
		-> UReal(1.0, 2.9154759474) : UReal

	Expression : 
		UReal(-6, 2) / UReal(5, 0)
		-> UReal(-1.2, 0.4) : UReal

	Expression : 
		UReal(-2, 3) / UReal(2, 4)
		-> UReal(-1.0, 2.9154759474) : UReal

	Expression : 
		UReal(0, 0) / UReal(0, 0)
		-> null : OclVoid

	Expression : 
		UReal(0, 0) / UReal(0, 4)
		-> null : OclVoid

	Expression : 
		UReal(0, 0) / UReal(6, 0)
		-> UReal(0.0, 0.0) : UReal

	Expression : 
		UReal(0, 0) / UReal(7, 3)
		-> UReal(0.0, 0.0612244898) : UReal

	Expression : 
		UReal(0, 4) / UReal(0, 0)
		-> null : OclVoid

	Expression : 
		UReal(0, 4) / UReal(0, 3)
		-> null : OclVoid

	Expression : 
		UReal(0, 4) / UReal(1, 0)
		-> UReal(0.0, 4.0) : UReal

	Expression : 
		UReal(0, 4) / UReal(2, 3)
		-> UReal(0.0, 2.8284271247) : UReal

	Expression : 
		UReal(9, 0) / UReal(9, 0)
		-> UReal(1.0, 0.0) : UReal

	Expression : 
		UReal(5, 0) / UReal(5, 3)
		-> UReal(1.0, 0.12) : UReal

	Expression : 
		UReal(4, 0) / UReal(8, 0)
		-> UReal(0.5, 0.0) : UReal

	Expression : 
		UReal(10, 0) / UReal(10, 12)
		-> UReal(1.0, 0.12) : UReal

	Expression : 
		UReal(9, 5) / UReal(9, 0)
		-> UReal(1.0, 0.5555555556) : UReal

	Expression : 
		UReal(2, 3) / UReal(2, 4)
		-> UReal(1.0, 2.9154759474) : UReal

	Expression : 
		UReal(6, 1) / UReal(4, 0)
		-> UReal(1.5, 0.25) : UReal

	Expression : 
		UReal(2, 3) / UReal(5, 4)
		-> UReal(0.4, 1.379275172) : UReal

	Expression : 
		UReal(-3, 0) / -3.0
		-> UReal(1.0, 0.0) : UReal

	Expression : 
		UReal(-6, 0) / -1.2
		-> UReal(5.0, 0.0) : UReal

	Expression : 
		UReal(-5, 3) / -5.0
		-> UReal(1.0, 0.6) : UReal

	Expression : 
		UReal(-8, 5) / -2.0
		-> UReal(4.0, 2.5) : UReal

	Expression : 
		UReal(0, 0) / 0.0
		-> null : OclVoid

	Expression : 
		UReal(0, 0) / 3.0
		-> UReal(0.0, 0.0) : UReal

	Expression : 
		UReal(0, 3) / 0.0
		-> null : OclVoid

	Expression : 
		UReal(0, 5) / -5.0
		-> UReal(0.0, 1.0) : UReal

	Expression : 
		UReal(5, 0) / 5.0
		-> UReal(1.0, 0.0) : UReal

	Expression : 
		UReal(3, 0) / 0.6
		-> UReal(5.0, 0.0) : UReal

	Expression : 
		UReal(7, 3) / 7.0
		-> UReal(1.0, 0.4285714286) : UReal

	Expression : 
		UReal(2, 5) / 0.5
		-> UReal(4.0, 10.0) : UReal

	Expression : 
		UReal(-3, 0) / -3
		-> UReal(1.0, 0.0) : UReal

	Expression : 
		UReal(-6, 0) / -12
		-> UReal(0.5, 0.0) : UReal

	Expression : 
		UReal(-5, 3) / -5
		-> UReal(1.0, 0.6) : UReal

	Expression : 
		UReal(-8, 5) / -2
		-> UReal(4.0, 2.5) : UReal

	Expression : 
		UReal(0, 0) / 0
		-> null : OclVoid

	Expression : 
		UReal(0, 0) / 3
		-> UReal(0.0, 0.0) : UReal

	Expression : 
		UReal(0, 3) / 0
		-> null : OclVoid

	Expression : 
		UReal(0, 5) / -5
		-> UReal(0.0, 1.0) : UReal

	Expression : 
		UReal(5, 0) / 5
		-> UReal(1.0, 0.0) : UReal

	Expression : 
		UReal(3, 0) / 56
		-> UReal(0.0535714286, 0.0) : UReal

	Expression : 
		UReal(7, 3) / 7
		-> UReal(1.0, 0.4285714286) : UReal

	Expression : 
		UReal(2, 5) / 65
		-> UReal(0.0307692308, 0.0769230769) : UReal

	Expression : 
		( UReal(2, 3).inv() ).equals( 1 / UReal(2, 3) )
		-> true : Boolean

	Expression : 
		( UReal(0, 3).inv() ).equals( 1 / UReal(0, 3) )
		-> true : Boolean

	Expression : 
		( UReal(2, 3) / UReal(1, 0.5) ).equals( UReal(1, 0.5) / UReal(2, 3) )
		-> false : Boolean

	Expression : 
		( 2.3 / UReal(1, 0.5) ).equals( UReal(1, 0.5) / 2.3 )
		-> false : Boolean

	Expression : 
		( 2 / UReal(1, 0.5) ).equals( UReal(1, 0.5) / 2 )
		-> false : Boolean

	Expression : 
		( UReal(2, 3) / (UReal(1, 0.5) / UReal(-0.5, 0.25)) ).equals( (UReal(2, 3) / UReal(1, 0.5)) / UReal(-0.5, 0.25) )
		-> false : Boolean

	Expression : 
		( UReal(2, 3) / (12.59 / UReal(-0.5, 0.25)) ).equals( (UReal(2, 3) / 12.59) / UReal(-0.5, 0.25) )
		-> false : Boolean

	Expression : 
		( UReal(2, 3) / (12 / UReal(-0.5, 0.25)) ).equals( (UReal(2, 3) / 12) / UReal(-0.5, 0.25) )
		-> false : Boolean

	Expression : 
		( UReal(2, 3) / 1 ).equals( UReal(2, 3) )
		-> true : Boolean

	Expression : 
		(UReal(0,0) < UReal(0,0)).toBoolean()
		-> false : Boolean

	Expression : 
		(UReal(0,0) < UReal(1,0)).toBoolean()
		-> true : Boolean

	Expression : 
		(UReal(3,0) < UReal(0,0)).toBoolean()
		-> false : Boolean

	Expression : 
		(UReal(0,0) < UReal(3,2)).toBoolean()
		-> true : Boolean

	Expression : 
		(UReal(3,0) < UReal(0,2)).toBoolean()
		-> false : Boolean

	Expression : 
		(UReal(0,2) < UReal(3,0)).toBoolean()
		-> true : Boolean

	Expression : 
		(UReal(3,2) < UReal(0,0)).toBoolean()
		-> false : Boolean

	Expression : 
		(UReal(0,2) < UReal(0,2)).toBoolean()
		-> false : Boolean

	Expression : 
		(UReal(0,2) < UReal(0,1)).toBoolean()
		-> false : Boolean

	Expression : 
		(UReal(0,2) < UReal(1,0.25)).toBoolean()
		-> true : Boolean

	Expression : 
		(UReal(0,2) < UReal(-1,0.25)).toBoolean()
		-> false : Boolean

	Expression : 
		(UReal(0,2) < UReal(5,2)).toBoolean()
		-> true : Boolean

	Expression : 
		(UReal(5,2) < UReal(0,2)).toBoolean()
		-> false : Boolean

	Expression : 
		(UReal(0,0) < 0).toBoolean()
		-> false : Boolean

	Expression : 
		(UReal(0,0) < 1).toBoolean()
		-> true : Boolean

	Expression : 
		(UReal(1,0) < 0).toBoolean()
		-> false : Boolean

	Expression : 
		(UReal(0,2) < 3).toBoolean()
		-> true : Boolean

	Expression : 
		(UReal(3,2) < 0).toBoolean()
		-> false : Boolean

	Expression : 
		(UReal(0,0) < 0.0).toBoolean()
		-> false : Boolean

	Expression : 
		(UReal(0,0) < 1.0).toBoolean()
		-> true : Boolean

	Expression : 
		(UReal(1,0) < 0.0).toBoolean()
		-> false : Boolean

	Expression : 
		(UReal(0,2) < 3.0).toBoolean()
		-> true : Boolean

	Expression : 
		(UReal(3,2) < 0.0).toBoolean()
		-> false : Boolean

	Expression : 
		( UReal(3,2) < UReal(0, 0) <> UReal(0, 0) < UReal(3, 2) ).toBoolean()
		-> true : Boolean

	Expression : 
		( UReal(3,2) < 0 <> 0 < UReal(3, 2) ).toBoolean()
		-> true : Boolean

	Expression : 
		( UReal(3,2) < 0.5 <> 0.5 < UReal(3, 2) ).toBoolean()
		-> true : Boolean

	Expression : 
		(UReal(0,0) >= UReal(0, 0)).toBoolean()
		-> true : Boolean

	Expression : 
		(UReal(0,0) >= UReal(1, 0)).toBoolean()
		-> false : Boolean

	Expression : 
		(UReal(3,0) >= UReal(0, 0)).toBoolean()
		-> true : Boolean

	Expression : 
		(UReal(0,0) >= UReal(3, 2)).toBoolean()
		-> false : Boolean

	Expression : 
		(UReal(3,0) >= UReal(0, 2)).toBoolean()
		-> true : Boolean

	Expression : 
		(UReal(0,2) >= UReal(3, 0)).toBoolean()
		-> false : Boolean

	Expression : 
		(UReal(3,2) >= UReal(0, 0)).toBoolean()
		-> true : Boolean

	Expression : 
		(UReal(0,2) >= UReal(0, 2)).toBoolean()
		-> true : Boolean

	Expression : 
		(UReal(0,2) >= UReal(0, 1)).toBoolean()
		-> true : Boolean

	Expression : 
		(UReal(0,2) >= UReal(1, 0.25)).toBoolean()
		-> false : Boolean

	Expression : 
		(UReal(0,2) >= UReal(-1, 0.25)).toBoolean()
		-> true : Boolean

	Expression : 
		(UReal(0,2) >= UReal(5, 2)).toBoolean()
		-> false : Boolean

	Expression : 
		(UReal(5,2) >= UReal(0, 2)).toBoolean()
		-> true : Boolean

	Expression : 
		(UReal(0,0) >= 0).toBoolean()
		-> true : Boolean

	Expression : 
		(UReal(0,0) >= 1).toBoolean()
		-> false : Boolean

	Expression : 
		(UReal(1,0) >= 0).toBoolean()
		-> true : Boolean

	Expression : 
		(UReal(0,2) >= 3).toBoolean()
		-> false : Boolean

	Expression : 
		(UReal(3,2) >= 0).toBoolean()
		-> true : Boolean

	Expression : 
		(UReal(0,0) >= 0.0).toBoolean()
		-> true : Boolean

	Expression : 
		(UReal(0,0) >= 1.0).toBoolean()
		-> false : Boolean

	Expression : 
		(UReal(1,0) >= 0.0).toBoolean()
		-> true : Boolean

	Expression : 
		(UReal(0,2) >= 3.0).toBoolean()
		-> false : Boolean

	Expression : 
		(UReal(3,2) >= 0.0).toBoolean()
		-> true : Boolean

	Expression : 
		(UReal(0,0) <= UReal(0, 0)).toBoolean()
		-> true : Boolean

	Expression : 
		(UReal(0,0) <= UReal(1, 0)).toBoolean()
		-> true : Boolean

	Expression : 
		(UReal(3,0) <= UReal(0, 0)).toBoolean()
		-> false : Boolean

	Expression : 
		(UReal(0,0) <= UReal(3, 2)).toBoolean()
		-> true : Boolean

	Expression : 
		(UReal(3,0) <= UReal(0, 2)).toBoolean()
		-> false : Boolean

	Expression : 
		(UReal(0,2) <= UReal(3, 0)).toBoolean()
		-> true : Boolean

	Expression : 
		(UReal(3,2) <= UReal(0, 0)).toBoolean()
		-> false : Boolean

	Expression : 
		(UReal(0,2) <= UReal(0, 2)).toBoolean()
		-> true : Boolean

	Expression : 
		(UReal(0,2) <= UReal(0, 1)).toBoolean()
		-> true : Boolean

	Expression : 
		(UReal(0,2) <= UReal(1, 0.25)).toBoolean()
		-> true : Boolean

	Expression : 
		(UReal(0,2) <= UReal(-1, 0.25)).toBoolean()
		-> false : Boolean

	Expression : 
		(UReal(0,2) <= UReal(5, 2)).toBoolean()
		-> true : Boolean

	Expression : 
		(UReal(5,2) <= UReal(0, 2)).toBoolean()
		-> false : Boolean

	Expression : 
		(UReal(0,0) <= 0).toBoolean()
		-> true : Boolean

	Expression : 
		(UReal(0,0) <= 1).toBoolean()
		-> true : Boolean

	Expression : 
		(UReal(1,0) <= 0).toBoolean()
		-> false : Boolean

	Expression : 
		(UReal(0,2) <= 3).toBoolean()
		-> true : Boolean

	Expression : 
		(UReal(3,2) <= 0).toBoolean()
		-> false : Boolean

	Expression : 
		(UReal(0,0) <= 0.0).toBoolean()
		-> true : Boolean

	Expression : 
		(UReal(0,0) <= 1.0).toBoolean()
		-> true : Boolean

	Expression : 
		(UReal(1,0) <= 0.0).toBoolean()
		-> false : Boolean

	Expression : 
		(UReal(0,2) <= 3.0).toBoolean()
		-> true : Boolean

	Expression : 
		(UReal(3,2) <= 0.0).toBoolean()
		-> false : Boolean

	Expression : 
		(UReal(0,0) > UReal(0, 0)).toBoolean()
		-> false : Boolean

	Expression : 
		(UReal(0,0) > UReal(1, 0)).toBoolean()
		-> false : Boolean

	Expression : 
		(UReal(3,0) > UReal(0, 0)).toBoolean()
		-> true : Boolean

	Expression : 
		(UReal(0,0) > UReal(3, 2)).toBoolean()
		-> false : Boolean

	Expression : 
		(UReal(3,0) > UReal(0, 2)).toBoolean()
		-> true : Boolean

	Expression : 
		(UReal(0,2) > UReal(3, 0)).toBoolean()
		-> false : Boolean

	Expression : 
		(UReal(3,2) > UReal(0, 0)).toBoolean()
		-> true : Boolean

	Expression : 
		(UReal(0,2) > UReal(0, 2)).toBoolean()
		-> false : Boolean

	Expression : 
		(UReal(0,2) > UReal(0, 1)).toBoolean()
		-> false : Boolean

	Expression : 
		(UReal(0,2) > UReal(1, 0.25)).toBoolean()
		-> false : Boolean

	Expression : 
		(UReal(0,2) > UReal(-1, 0.25)).toBoolean()
		-> true : Boolean

	Expression : 
		(UReal(0,2) > UReal(5, 2)).toBoolean()
		-> false : Boolean

	Expression : 
		(UReal(5,2) > UReal(0, 2)).toBoolean()
		-> true : Boolean

	Expression : 
		(UReal(0,0) > 0).toBoolean()
		-> false : Boolean

	Expression : 
		(UReal(0,0) > 1).toBoolean()
		-> false : Boolean

	Expression : 
		(UReal(1,0) > 0).toBoolean()
		-> true : Boolean

	Expression : 
		(UReal(0,2) > 3).toBoolean()
		-> false : Boolean

	Expression : 
		(UReal(3,2) > 0).toBoolean()
		-> true : Boolean

	Expression : 
		(UReal(0,0) > 0.0).toBoolean()
		-> false : Boolean

	Expression : 
		(UReal(0,0) > 1.0).toBoolean()
		-> false : Boolean

	Expression : 
		(UReal(1,0) > 0.0).toBoolean()
		-> true : Boolean

	Expression : 
		(UReal(0,2) > 3.0).toBoolean()
		-> false : Boolean

	Expression : 
		(UReal(3,2) > 0.0).toBoolean()
		-> true : Boolean

	Expression : 
		( UReal(3,2) > UReal(0, 0) ).equals( UReal(0, 0) > UReal(3, 2) )
		-> false : Boolean

	Expression : 
		( UReal(3,2) > 0 ).equals( 0 > UReal(3, 2) )
		-> false : Boolean

	Expression : 
		( UReal(3,2) > 0.5 ).equals( 0.5 > UReal(3, 2) )
		-> false : Boolean

	Expression : 
		( UReal(0, 0) = UReal(0, 0) ).toBoolean()
		-> true : Boolean

	Expression : 
		( UReal(0, 0) = UReal(1, 0) ).toBoolean()
		-> false : Boolean

	Expression : 
		( UReal(3, 0) = UReal(0, 0) ).toBoolean()
		-> false : Boolean

	Expression : 
		( UReal(0, 0) = UReal(3, 2) ).toBoolean()
		-> false : Boolean

	Expression : 
		( UReal(3, 0) = UReal(0, 2) ).toBoolean()
		-> false : Boolean

	Expression : 
		( UReal(0, 2) = UReal(3, 0) ).toBoolean()
		-> false : Boolean

	Expression : 
		( UReal(3, 2) = UReal(0, 0) ).toBoolean()
		-> false : Boolean

	Expression : 
		( UReal(0, 2) = UReal(0, 2) ).toBoolean()
		-> true : Boolean

	Expression : 
		( UReal(0, 2) = UReal(0, 1) ).toBoolean()
		-> true : Boolean

	Expression : 
		( UReal(0, 2) = UReal(1, 0.25) ).toBoolean()
		-> false : Boolean

	Expression : 
		( UReal(0, 2) = UReal(-1, 0.25) ).toBoolean()
		-> false : Boolean

	Expression : 
		( UReal(0, 2) = UReal(5, 2) ).toBoolean()
		-> false : Boolean

	Expression : 
		( UReal(5, 2) = UReal(0, 2) ).toBoolean()
		-> false : Boolean

	Expression : 
		( UReal(0, 0) = 0 ).toBoolean()
		-> true : Boolean

	Expression : 
		( UReal(0, 0) = 1 ).toBoolean()
		-> false : Boolean

	Expression : 
		( UReal(1, 0) = 0 ).toBoolean()
		-> false : Boolean

	Expression : 
		( UReal(0, 2) = 3 ).toBoolean()
		-> false : Boolean

	Expression : 
		( UReal(3, 2) = 0 ).toBoolean()
		-> false : Boolean

	Expression : 
		( UReal(0, 0) = 0.0 ).toBoolean()
		-> true : Boolean

	Expression : 
		( UReal(0, 0) = 1.0 ).toBoolean()
		-> false : Boolean

	Expression : 
		( UReal(1, 0) = 0.0 ).toBoolean()
		-> false : Boolean

	Expression : 
		( UReal(0, 2) = 3.0 ).toBoolean()
		-> false : Boolean

	Expression : 
		( UReal(3, 2) = 0.0 ).toBoolean()
		-> false : Boolean

	Expression : 
		UReal(2, 3) = Undefined
		-> false : Boolean

	Expression : 
		UReal(2, 3) = null
		-> false : Boolean

	Expression : 
		( UReal(0, 0) <> UReal(0, 0) ).toBoolean()
		-> false : Boolean

	Expression : 
		( UReal(0, 0) <> UReal(1, 0) ).toBoolean()
		-> true : Boolean

	Expression : 
		( UReal(3, 0) <> UReal(0, 0) ).toBoolean()
		-> true : Boolean

	Expression : 
		( UReal(0, 0) <> UReal(3, 2) ).toBoolean()
		-> true : Boolean

	Expression : 
		( UReal(3, 0) <> UReal(0, 2) ).toBoolean()
		-> true : Boolean

	Expression : 
		( UReal(0, 2) <> UReal(3, 0) ).toBoolean()
		-> true : Boolean

	Expression : 
		( UReal(3, 2) <> UReal(0, 0) ).toBoolean()
		-> true : Boolean

	Expression : 
		( UReal(0, 2) <> UReal(0, 2) ).toBoolean()
		-> false : Boolean

	Expression : 
		( UReal(0, 2) <> UReal(0, 1) ).toBoolean()
		-> false : Boolean

	Expression : 
		( UReal(0, 2) <> UReal(1, 0.25) ).toBoolean()
		-> true : Boolean

	Expression : 
		( UReal(0, 2) <> UReal(-1, 0.25) ).toBoolean()
		-> true : Boolean

	Expression : 
		( UReal(0, 2) <> UReal(5, 2) ).toBoolean()
		-> true : Boolean

	Expression : 
		( UReal(5, 2) <> UReal(0, 2) ).toBoolean()
		-> true : Boolean

	Expression : 
		( UReal(0, 0) <> 0 ).toBoolean()
		-> false : Boolean

	Expression : 
		( UReal(0, 0) <> 1 ).toBoolean()
		-> true : Boolean

	Expression : 
		( UReal(1, 0) <> 0 ).toBoolean()
		-> true : Boolean

	Expression : 
		( UReal(0, 2) <> 3 ).toBoolean()
		-> true : Boolean

	Expression : 
		( UReal(3, 2) <> 0 ).toBoolean()
		-> true : Boolean

	Expression : 
		( UReal(0, 0) <> 0.0 ).toBoolean()
		-> false : Boolean

	Expression : 
		( UReal(0, 0) <> 1.0 ).toBoolean()
		-> true : Boolean

	Expression : 
		( UReal(1, 0) <> 0.0 ).toBoolean()
		-> true : Boolean

	Expression : 
		( UReal(0, 2) <> 3.0 ).toBoolean()
		-> true : Boolean

	Expression : 
		( UReal(3, 2) <> 0.0 ).toBoolean()
		-> true : Boolean

	Expression : 
		UReal(2, 3) <> Undefined
		-> true : Boolean

	Expression : 
		UReal(2, 3) <> null
		-> true : Boolean

[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.197 s -- in org.tzi.use.parser.uncertainty.USECompilerUncertaintyTest
[INFO] Running org.tzi.use.architecture.MavenCyclicDependenciesCoreTest
Number of cycles in org.tzi.use.main without tests: 0
Number of cycles in org.tzi.use.main with tests: 0
Number of cycles in org.tzi.use.analysis without tests: 0
Number of cycles in org.tzi.use.analysis with tests: 0
Number of cycles in org.tzi.use.util without tests: 0
Number of cycles in org.tzi.use.util with tests: 0
Number of cycles in org.tzi.use.gen without tests: 1
Number of cycles in org.tzi.use.gen with tests: 1
Number of cycles in org.tzi.use.parser without tests: 2
Number of cycles in org.tzi.use.parser with tests: 36
Number of cycles in org.tzi.use.api without tests: 1
Number of cycles in org.tzi.use.api with tests: 1
Cycles in core module with tests : 233
Number of cycles in org.tzi.use.graph without tests: 0
Number of cycles in org.tzi.use.graph with tests: 0
Number of cycles in org.tzi.use.config without tests: 0
Number of cycles in org.tzi.use.config with tests: 0
Cycles in core module without tests : 55
Number of cycles in org.tzi.use.uml without tests: 5
Number of cycles in org.tzi.use.uml with tests: 5
[INFO] Tests run: 11, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 4.169 s -- in org.tzi.use.architecture.MavenCyclicDependenciesCoreTest
[INFO] Running uCount/uCountC
[INFO] Tests run: 6, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.010 s -- in uCount/uCountC
[INFO] Running org.tzi.use.uncertainty.differential.SBooleanMarshallingTest
=== SBooleanValue.and over 3x3 opinions ===
  OPAQUE("org.tzi.use.uml.ocl.value.SBooleanValue|SBooleanValue{Value.fType=SBooleanType{BasicType.fTypename=\"SBoolean\"},SBooleanValue.sBoolean=SBoolean{SBoolean.a=0.25,SBoolean.b=0.19,SBoolean.d=0.36,SBoolean.relativeWeight=0.0,SBoolean.u=0.45}}")@SBooleanValue
  OPAQUE("org.tzi.use.uml.ocl.value.SBooleanValue|SBooleanValue{Value.fType=SBooleanType{BasicType.fTypename=\"SBoolean\"},SBooleanValue.sBoolean=SBoolean{SBoolean.a=0.5,SBoolean.b=0.3,SBoolean.d=0.2,SBoolean.relativeWeight=1.0,SBoolean.u=0.5}}")@SBooleanValue
  OPAQUE("org.tzi.use.uml.ocl.value.SBooleanValue|SBooleanValue{Value.fType=SBooleanType{BasicType.fTypename=\"SBoolean\"},SBooleanValue.sBoolean=SBoolean{SBoolean.a=0.25,SBoolean.b=0.1,SBoolean.d=0.2,SBoolean.relativeWeight=0.0,SBoolean.u=0.7}}")@SBooleanValue
  OPAQUE("org.tzi.use.uml.ocl.value.SBooleanValue|SBooleanValue{Value.fType=SBooleanType{BasicType.fTypename=\"SBoolean\"},SBooleanValue.sBoolean=SBoolean{SBoolean.a=1.0,SBoolean.b=1.0,SBoolean.d=0.0,SBoolean.relativeWeight=1.0,SBoolean.u=0.0}}")@SBooleanValue
  OPAQUE("org.tzi.use.uml.ocl.value.SBooleanValue|SBooleanValue{Value.fType=SBooleanType{BasicType.fTypename=\"SBoolean\"},SBooleanValue.sBoolean=SBoolean{SBoolean.a=0.5,SBoolean.b=0.0,SBoolean.d=0.0,SBoolean.relativeWeight=1.0,SBoolean.u=1.0}}")@SBooleanValue
  OPAQUE("org.tzi.use.uml.ocl.value.SBooleanValue|SBooleanValue{Value.fType=SBooleanType{BasicType.fTypename=\"SBoolean\"},SBooleanValue.sBoolean=SBoolean{SBoolean.a=0.25,SBoolean.b=0.0,SBoolean.d=0.0,SBoolean.relativeWeight=0.0,SBoolean.u=1.0}}")@SBooleanValue
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.078 s -- in org.tzi.use.uncertainty.differential.SBooleanMarshallingTest
[INFO] Running org.tzi.use.uncertainty.differential.FirstRealDifferentialTest
================ FIRST REAL DIFFERENTIAL ================
URealValue.add(value)         784 rows  distinctRef=258  {AGREE=784}
URealValue.minus(value)       784 rows  distinctRef=389  {AGREE=784}
URealValue.mult(value)        784 rows  distinctRef=195  {AGREE=784}
URealValue.divideBy(value)    784 rows  distinctRef=409  {AGREE=784}
URealValue.min(value)         784 rows  distinctRef=27   {AGREE=784}
URealValue.max(value)         784 rows  distinctRef=27   {AGREE=784}
URealValue.neg()               28 rows  distinctRef=27   {AGREE=28}
URealValue.abs()               28 rows  distinctRef=21   {AGREE=28}
URealValue.floor()             28 rows  distinctRef=26   {AGREE=28}
URealValue.round()             28 rows  distinctRef=22   {AGREE=28}
URealValue.sqrt()              28 rows  distinctRef=16   {AGREE=28}
URealValue.inverse()           28 rows  distinctRef=27   {AGREE=28}
URealValue.toReal()            28 rows  distinctRef=21   {AGREE=28}
URealValue.toInteger()         28 rows  distinctRef=14   {AGREE=28}
URealValue.toUInteger()        28 rows  distinctRef=22   {AGREE=28}
URealValue.lt(value)          784 rows  distinctRef=37   {AGREE=784}
URealValue.gt(value)          784 rows  distinctRef=37   {AGREE=784}
URealValue.le(value)          784 rows  distinctRef=37   {AGREE=784}
URealValue.ge(value)          784 rows  distinctRef=37   {AGREE=784}
UIntegerValue.add(value)      361 rows  distinctRef=154  {AGREE=361}
UIntegerValue.minus(value)    361 rows  distinctRef=260  {AGREE=361}
UIntegerValue.mult(value)     361 rows  distinctRef=115  {AGREE=361}
URealValue.value()             28 rows  distinctRef=21   {AGREE=28}
UIntegerValue.value()          19 rows  distinctRef=14   {AGREE=19}
URealValue.uncertainty()       28 rows  distinctRef=13   {AGREE=28}
UIntegerValue.uncertainty()    19 rows  distinctRef=11   {AGREE=19}
UBooleanValue.value()          15 rows  distinctRef=1    {AGREE=13, HARNESS_ERROR=2}   <== DEGENERATE, agreement is free
UBooleanValue.probability()    15 rows  distinctRef=10   {AGREE=13, HARNESS_ERROR=2}
UStringValue.value()           32 rows  distinctRef=27   {AGREE=31, HARNESS_ERROR=1}
UStringValue.confidence()      32 rows  distinctRef=11   {AGREE=31, HARNESS_ERROR=1}
UBooleanValue.and(value)      225 rows  distinctRef=38   {AGREE=169, HARNESS_ERROR=56}
UBooleanValue.or(value)       225 rows  distinctRef=39   {AGREE=169, HARNESS_ERROR=56}
UBooleanValue.not()            15 rows  distinctRef=10   {AGREE=13, HARNESS_ERROR=2}
UStringValue.uConcat(value)  1024 rows  distinctRef=863  {AGREE=961, HARNESS_ERROR=63}
UStringValue.lt(value)       1024 rows  distinctRef=81   {AGREE=961, HARNESS_ERROR=63}
UStringValue.gt(value)       1024 rows  distinctRef=81   {AGREE=961, HARNESS_ERROR=63}
UStringValue.le(value)       1024 rows  distinctRef=81   {AGREE=961, HARNESS_ERROR=63}
UStringValue.ge(value)       1024 rows  distinctRef=81   {AGREE=961, HARNESS_ERROR=63}
UStringValue.toBoolean()       32 rows  distinctRef=2    {AGREE=31, HARNESS_ERROR=1}
UStringValue.toInteger()       32 rows  distinctRef=5    {AGREE=5, BOTH_THREW=26, HARNESS_ERROR=1}
UStringValue.toReal()          32 rows  distinctRef=8    {AGREE=8, BOTH_THREW=23, HARNESS_ERROR=1}
UStringValue.uToString()       32 rows  distinctRef=27   {AGREE=31, HARNESS_ERROR=1}
UStringValue.toUBoolean()      32 rows  distinctRef=6    {AGREE=31, HARNESS_ERROR=1}
UStringValue.uCharacters()     32 rows  distinctRef=30   {AGREE=31, HARNESS_ERROR=1}
SBooleanValue.and(value)      529 rows  distinctRef=160  {AGREE=361, HARNESS_ERROR=168}
SBooleanValue.not()            23 rows  distinctRef=19   {AGREE=19, HARNESS_ERROR=4}
=========================================================
java-type mismatches: NONE
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 1.090 s -- in org.tzi.use.uncertainty.differential.FirstRealDifferentialTest
[INFO] Running Ported fidelity over the full operation census
=========== PORTED FIDELITY, FULL CENSUS ===========
operations enumerated  355
supported by the port  349  (98%)
rows                   1223560 total, 79520 measured
verdicts               {AGREE=78130, INTENDED_DEPARTURE=1390, BOTH_THREW=50598, UNMEASURABLE=808, HARNESS_ERROR=1092634}
pre-registered (B7)    11 declaration(s), 1390 row(s) adjudicated
diverging operations   0 (unintended)
unsupported (6): [SBooleanValue.averageFusion(value), SBooleanValue.cumulativeFusion(value), SBooleanValue.epistemicCumulativeFusion(value), SBooleanValue.majorityFusion(value), SBooleanValue.minimumFusion(value), SBooleanValue.weightedFusion(value)]
====================================================
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 12.51 s -- in Ported fidelity over the full operation census
[INFO] Running uEquals: swept, and 0.0 for uncertain-vs-crisp equality is correct, not a defect
[INFO] Tests run: 6, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.036 s -- in uEquals: swept, and 0.0 for uncertain-vs-crisp equality is correct, not a defect
[INFO] Running Detection power: subtle infidelities in a ported U-type
=== detection power: control (a perfect port) =====================
seed                 20260817
operations           355  (stage-shaped domains)
rows                 23963
measured rows        21556
agreement rows       21556
verdict tally        {AGREE=21556, BOTH_THREW=1243, HARNESS_ERROR=1063, UNMEASURABLE=101}
diverging operations 0   <- MUST be 0, or nothing below is attributable to a planted defect
stage passes         74 of 355  (isStagePass(1, none()))
why a PERFECT port is refused elsewhere:
    0 PASS   74
    2 refused: rows disagreed   44
    3 refused on more than one clause   118
    4 refused: not discriminating (D-15)   119
distinct throw-pairs a PERFECT port produces  193   <- AcceptedThrowPairs entries a human would have to author, one per (operation, both classes, both messages), before clause 2 could ever be met on the operations that throw
    e.g. UIntegerValue.divideBy(value) || reference threw java.lang.ArithmeticException: / by zero / subject threw java.lang.ArithmeticException: / by zero
    e.g. UIntegerValue.inverse() || reference threw java.lang.ArithmeticException: / by zero / subject threw java.lang.ArithmeticException: / by zero
    e.g. UIntegerValue.mod(value) || reference threw java.lang.ArithmeticException: / by zero / subject threw java.lang.ArithmeticException: / by zero
    e.g. UIntegerValue.power(value) || reference threw java.lang.RuntimeException: UInteger.power() : expected Real or Integer exponent value / subject threw java.lang.RuntimeException: UInteger.power() : expected Real or Integer exponent value
===================================================================
=== detection power: P1-off-by-one-index ============================
defect               a 0-based port of a 1-based string index: at/uAt/uSubstring shift by one
aimed at             [UStringValue.at(int), UStringValue.uAt(int), UStringValue.uSubstring(int,int)]
rows                 23963   (control 23963)
measured rows        21491   (control 21556)
agreement rows       21389   (control 21556)
verdict tally        {AGREE=21389, BOTH_THREW=1166, DIFFER=102, HARNESS_ERROR=1063, MIXED=142, UNMEASURABLE=101}
DETECTED on          3 operation(s): [UStringValue.at(int), UStringValue.uAt(int), UStringValue.uSubstring(int,int)]
stage passes         74   (control 74)
isClean() operations 193   (control 193)   the older predicate loses 0: []
  target UStringValue.at(int)
    control  {AGREE=70, BOTH_THREW=146, HARNESS_ERROR=8}
    mutant   {DIFFER=51, BOTH_THREW=121, MIXED=44, HARNESS_ERROR=8}
    statement UStringValue.at(int): 224 rows, 51 measured, 0 agreed, 224 disagreed, 0 intended departure(s), 0 java-type mismatch(es) (subject token OBSERVED on 0, ASSUMED on 0), 31 distinct reference value(s) [DISCRIMINATING]
    stage pass? false   (control false)
    refused: 224 row(s) did not agree.
  target UStringValue.uAt(int)
    control  {AGREE=70, BOTH_THREW=146, HARNESS_ERROR=8}
    mutant   {DIFFER=51, BOTH_THREW=121, MIXED=44, HARNESS_ERROR=8}
    statement UStringValue.uAt(int): 224 rows, 51 measured, 0 agreed, 224 disagreed, 0 intended departure(s), 0 java-type mismatch(es) (subject token OBSERVED on 0, ASSUMED on 0), 39 distinct reference value(s) [DISCRIMINATING]
    stage pass? false   (control false)
    refused: 224 row(s) did not agree.
  target UStringValue.uSubstring(int,int)
    control  {AGREE=27, BOTH_THREW=621, HARNESS_ERROR=24}
    mutant   {BOTH_THREW=594, MIXED=54, HARNESS_ERROR=24}
    statement UStringValue.uSubstring(int,int): 672 rows, 0 measured, 0 agreed, 672 disagreed, 0 intended departure(s), 0 java-type mismatch(es) (subject token OBSERVED on 0, ASSUMED on 0), 0 distinct reference value(s) [NOT DISCRIMINATING]
    stage pass? false   (control false)
    refused: measured 0 row(s) of 672, needed at least 1. A result with too little evidence is not evidence.
    refused: 672 row(s) did not agree.
    refused: the reference side produced 0 distinct value(s) across 0 measured row(s). This operation could not have failed over this domain, so agreement on it is decided before either implementation runs and is not evidence of fidelity (defect D-15). Either widen the domain until the reference answers differently, or sign the operation off in AcceptedDegenerateOperations with a written rationale — which is copied into the report, so the weakness travels with the number.
  first 6 diverging row(s):
  index	operation	inputs	historical	ported	verdict	note
  18	UStringValue.at(int)	USTRING(" ",0.5)@UStringValue | INTEGER(0)@IntegerValue	THROWN:java.lang.IndexOutOfBoundsException	STRING(" ")@StringValue	MIXED	the reference threw and the subject returned. reference threw java.lang.IndexOutOfBoundsException: idx = 0 / subject returned STRING(" ")@StringValue
  19	UStringValue.at(int)	USTRING(" ",0.5)@UStringValue | INTEGER(1)@IntegerValue	STRING(" ")@StringValue	THROWN:java.lang.IndexOutOfBoundsException	MIXED	the subject threw and the reference returned. reference returned STRING(" ")@StringValue / subject threw java.lang.IndexOutOfBoundsException: idx = 2
  26	UStringValue.at(int)	USTRING("a",0.0)@UStringValue | INTEGER(0)@IntegerValue	THROWN:java.lang.IndexOutOfBoundsException	STRING("a")@StringValue	MIXED	the reference threw and the subject returned. reference threw java.lang.IndexOutOfBoundsException: idx = 0 / subject returned STRING("a")@StringValue
  27	UStringValue.at(int)	USTRING("a",0.0)@UStringValue | INTEGER(1)@IntegerValue	STRING("a")@StringValue	THROWN:java.lang.IndexOutOfBoundsException	MIXED	the subject threw and the reference returned. reference returned STRING("a")@StringValue / subject threw java.lang.IndexOutOfBoundsException: idx = 2
  34	UStringValue.at(int)	USTRING("a",1.0)@UStringValue | INTEGER(0)@IntegerValue	THROWN:java.lang.IndexOutOfBoundsException	STRING("a")@StringValue	MIXED	the reference threw and the subject returned. reference threw java.lang.IndexOutOfBoundsException: idx = 0 / subject returned STRING("a")@StringValue
  35	UStringValue.at(int)	USTRING("a",1.0)@UStringValue | INTEGER(1)@IntegerValue	STRING("a")@StringValue	THROWN:java.lang.IndexOutOfBoundsException	MIXED	the subject threw and the reference returned. reference returned STRING("a")@StringValue / subject threw java.lang.IndexOutOfBoundsException: idx = 2
===================================================================
=== detection power: P2-linear-uncertainty ============================
defect               uncertainties combined with + where the historical uses sqrt(ua^2+ub^2)
aimed at             [UIntegerValue.add(value), UIntegerValue.minus(value), URealValue.add(value), URealValue.minus(value)]
rows                 23963   (control 23963)
measured rows        21556   (control 21556)
agreement rows       21088   (control 21556)
verdict tally        {AGREE=21088, BOTH_THREW=1243, DIFFER=468, HARNESS_ERROR=1063, UNMEASURABLE=101}
DETECTED on          4 operation(s): [UIntegerValue.add(value), UIntegerValue.minus(value), URealValue.add(value), URealValue.minus(value)]
stage passes         70   (control 74)
isClean() operations 189   (control 193)   the older predicate loses 4: [UIntegerValue.add(value), UIntegerValue.minus(value), URealValue.add(value), URealValue.minus(value)]
  target UIntegerValue.add(value)
    control  {AGREE=225}
    mutant   {AGREE=134, DIFFER=91}
    statement UIntegerValue.add(value): 225 rows, 225 measured, 134 agreed, 91 disagreed, 0 intended departure(s), 0 java-type mismatch(es) (subject token OBSERVED on 0, ASSUMED on 0), 88 distinct reference value(s) [DISCRIMINATING]
    stage pass? false   (control true)
    refused: 91 row(s) did not agree.
  target UIntegerValue.minus(value)
    control  {AGREE=225}
    mutant   {AGREE=134, DIFFER=91}
    statement UIntegerValue.minus(value): 225 rows, 225 measured, 134 agreed, 91 disagreed, 0 intended departure(s), 0 java-type mismatch(es) (subject token OBSERVED on 0, ASSUMED on 0), 132 distinct reference value(s) [DISCRIMINATING]
    stage pass? false   (control true)
    refused: 91 row(s) did not agree.
  target URealValue.add(value)
    control  {AGREE=576}
    mutant   {AGREE=433, DIFFER=143}
    statement URealValue.add(value): 576 rows, 576 measured, 433 agreed, 143 disagreed, 0 intended departure(s), 0 java-type mismatch(es) (subject token OBSERVED on 0, ASSUMED on 0), 164 distinct reference value(s) [DISCRIMINATING]
    stage pass? false   (control true)
    refused: 143 row(s) did not agree.
  target URealValue.minus(value)
    control  {AGREE=576}
    mutant   {AGREE=433, DIFFER=143}
    statement URealValue.minus(value): 576 rows, 576 measured, 433 agreed, 143 disagreed, 0 intended departure(s), 0 java-type mismatch(es) (subject token OBSERVED on 0, ASSUMED on 0), 225 distinct reference value(s) [DISCRIMINATING]
    stage pass? false   (control true)
    refused: 143 row(s) did not agree.
  first 6 diverging row(s):
  index	operation	inputs	historical	ported	verdict	note
  12	UIntegerValue.add(value)	UINTEGER(0,0.0)@UIntegerValue | UINTEGER(1,-1.0)@UIntegerValue	UINTEGER(1,1.0)@UIntegerValue	UINTEGER(1,-1.0)@UIntegerValue	DIFFER	
  16	UIntegerValue.add(value)	UINTEGER(0,1.0)@UIntegerValue | UINTEGER(0,1.0)@UIntegerValue	UINTEGER(0,1.4142135623730951)@UIntegerValue	UINTEGER(0,2.0)@UIntegerValue	DIFFER	
  18	UIntegerValue.add(value)	UINTEGER(0,1.0)@UIntegerValue | UINTEGER(1,1.0)@UIntegerValue	UINTEGER(1,1.4142135623730951)@UIntegerValue	UINTEGER(1,2.0)@UIntegerValue	DIFFER	
  20	UIntegerValue.add(value)	UINTEGER(0,1.0)@UIntegerValue | UINTEGER(-1,1.0)@UIntegerValue	UINTEGER(-1,1.4142135623730951)@UIntegerValue	UINTEGER(-1,2.0)@UIntegerValue	DIFFER	
  21	UIntegerValue.add(value)	UINTEGER(0,1.0)@UIntegerValue | UINTEGER(2,0.5)@UIntegerValue	UINTEGER(2,1.118033988749895)@UIntegerValue	UINTEGER(2,1.5)@UIntegerValue	DIFFER	
  22	UIntegerValue.add(value)	UINTEGER(0,1.0)@UIntegerValue | UINTEGER(-2,0.5)@UIntegerValue	UINTEGER(-2,1.118033988749895)@UIntegerValue	UINTEGER(-2,1.5)@UIntegerValue	DIFFER	
===================================================================
=== detection power: P3-hypot-uncertainty ============================
defect               Math.hypot(ua,ub) instead of sqrt(ua*ua+ub*ub) -- algebraically the same rule, a different function
aimed at             [UIntegerValue.add(value), UIntegerValue.minus(value), URealValue.add(value), URealValue.minus(value)]
rows                 23963   (control 23963)
measured rows        21556   (control 21556)
agreement rows       21532   (control 21556)
verdict tally        {AGREE=21532, BOTH_THREW=1243, DIFFER=24, HARNESS_ERROR=1063, UNMEASURABLE=101}
DETECTED on          4 operation(s): [UIntegerValue.add(value), UIntegerValue.minus(value), URealValue.add(value), URealValue.minus(value)]
stage passes         70   (control 74)
isClean() operations 189   (control 193)   the older predicate loses 4: [UIntegerValue.add(value), UIntegerValue.minus(value), URealValue.add(value), URealValue.minus(value)]
  target UIntegerValue.add(value)
    control  {AGREE=225}
    mutant   {AGREE=223, DIFFER=2}
    statement UIntegerValue.add(value): 225 rows, 225 measured, 223 agreed, 2 disagreed, 0 intended departure(s), 0 java-type mismatch(es) (subject token OBSERVED on 0, ASSUMED on 0), 88 distinct reference value(s) [DISCRIMINATING]
    stage pass? false   (control true)
    refused: 2 row(s) did not agree.
  target UIntegerValue.minus(value)
    control  {AGREE=225}
    mutant   {AGREE=223, DIFFER=2}
    statement UIntegerValue.minus(value): 225 rows, 225 measured, 223 agreed, 2 disagreed, 0 intended departure(s), 0 java-type mismatch(es) (subject token OBSERVED on 0, ASSUMED on 0), 132 distinct reference value(s) [DISCRIMINATING]
    stage pass? false   (control true)
    refused: 2 row(s) did not agree.
  target URealValue.add(value)
    control  {AGREE=576}
    mutant   {AGREE=566, DIFFER=10}
    statement URealValue.add(value): 576 rows, 576 measured, 566 agreed, 10 disagreed, 0 intended departure(s), 0 java-type mismatch(es) (subject token OBSERVED on 0, ASSUMED on 0), 164 distinct reference value(s) [DISCRIMINATING]
    stage pass? false   (control true)
    refused: 10 row(s) did not agree.
  target URealValue.minus(value)
    control  {AGREE=576}
    mutant   {AGREE=566, DIFFER=10}
    statement URealValue.minus(value): 576 rows, 576 measured, 566 agreed, 10 disagreed, 0 intended departure(s), 0 java-type mismatch(es) (subject token OBSERVED on 0, ASSUMED on 0), 225 distinct reference value(s) [DISCRIMINATING]
    stage pass? false   (control true)
    refused: 10 row(s) did not agree.
  first 6 diverging row(s):
  index	operation	inputs	historical	ported	verdict	note
  134	UIntegerValue.add(value)	UINTEGER(7,0.25)@UIntegerValue | UINTEGER(689,0.19065)@UIntegerValue	UINTEGER(696,0.3144000993956586)@UIntegerValue	UINTEGER(696,0.31440009939565855)@UIntegerValue	DIFFER	
  218	UIntegerValue.add(value)	UINTEGER(689,0.19065)@UIntegerValue | UINTEGER(7,0.25)@UIntegerValue	UINTEGER(696,0.3144000993956586)@UIntegerValue	UINTEGER(696,0.31440009939565855)@UIntegerValue	DIFFER	
  134	UIntegerValue.minus(value)	UINTEGER(7,0.25)@UIntegerValue | UINTEGER(689,0.19065)@UIntegerValue	UINTEGER(-682,0.3144000993956586)@UIntegerValue	UINTEGER(-682,0.31440009939565855)@UIntegerValue	DIFFER	
  218	UIntegerValue.minus(value)	UINTEGER(689,0.19065)@UIntegerValue | UINTEGER(7,0.25)@UIntegerValue	UINTEGER(682,0.3144000993956586)@UIntegerValue	UINTEGER(682,0.31440009939565855)@UIntegerValue	DIFFER	
  47	URealValue.add(value)	UREAL(0.0,1.0)@URealValue | UREAL(28.230986,0.91554)@URealValue	UREAL(28.230986,1.355807320971531)@URealValue	UREAL(28.230986,1.3558073209715311)@URealValue	DIFFER	
  119	URealValue.add(value)	UREAL(1.0,1.0)@URealValue | UREAL(28.230986,0.91554)@URealValue	UREAL(29.230986,1.355807320971531)@URealValue	UREAL(29.230986,1.3558073209715311)@URealValue	DIFFER	
===================================================================
=== detection power: P4-le-for-lt ============================
defect               an order comparison written <= where the historical writes < (and >= for >)
aimed at             [UIntegerValue.gt(value), UIntegerValue.lt(value), URealValue.gt(value), URealValue.lt(value), UStringValue.gt(value), UStringValue.lt(value)]
rows                 23963   (control 23963)
measured rows        21556   (control 21556)
agreement rows       21256   (control 21556)
verdict tally        {AGREE=21256, BOTH_THREW=1243, DIFFER=300, HARNESS_ERROR=1063, UNMEASURABLE=101}
DETECTED on          6 operation(s): [UIntegerValue.gt(value), UIntegerValue.lt(value), URealValue.gt(value), URealValue.lt(value), UStringValue.gt(value), UStringValue.lt(value)]
stage passes         70   (control 74)
isClean() operations 189   (control 193)   the older predicate loses 4: [UIntegerValue.gt(value), UIntegerValue.lt(value), URealValue.gt(value), URealValue.lt(value)]
  target UIntegerValue.gt(value)
    control  {AGREE=225}
    mutant   {AGREE=171, DIFFER=54}
    statement UIntegerValue.gt(value): 225 rows, 225 measured, 171 agreed, 54 disagreed, 0 intended departure(s), 0 java-type mismatch(es) (subject token OBSERVED on 0, ASSUMED on 0), 27 distinct reference value(s) [DISCRIMINATING]
    stage pass? false   (control true)
    refused: 54 row(s) did not agree.
  target UIntegerValue.lt(value)
    control  {AGREE=225}
    mutant   {AGREE=171, DIFFER=54}
    statement UIntegerValue.lt(value): 225 rows, 225 measured, 171 agreed, 54 disagreed, 0 intended departure(s), 0 java-type mismatch(es) (subject token OBSERVED on 0, ASSUMED on 0), 27 distinct reference value(s) [DISCRIMINATING]
    stage pass? false   (control true)
    refused: 54 row(s) did not agree.
  target URealValue.gt(value)
    control  {AGREE=576}
    mutant   {AGREE=510, DIFFER=66}
    statement URealValue.gt(value): 576 rows, 576 measured, 510 agreed, 66 disagreed, 0 intended departure(s), 0 java-type mismatch(es) (subject token OBSERVED on 0, ASSUMED on 0), 33 distinct reference value(s) [DISCRIMINATING]
    stage pass? false   (control true)
    refused: 66 row(s) did not agree.
  target URealValue.lt(value)
    control  {AGREE=576}
    mutant   {AGREE=510, DIFFER=66}
    statement URealValue.lt(value): 576 rows, 576 measured, 510 agreed, 66 disagreed, 0 intended departure(s), 0 java-type mismatch(es) (subject token OBSERVED on 0, ASSUMED on 0), 33 distinct reference value(s) [DISCRIMINATING]
    stage pass? false   (control true)
    refused: 66 row(s) did not agree.
  target UStringValue.gt(value)
    control  {AGREE=729, HARNESS_ERROR=55}
    mutant   {AGREE=699, DIFFER=30, HARNESS_ERROR=55}
    statement UStringValue.gt(value): 784 rows, 729 measured, 699 agreed, 85 disagreed, 0 intended departure(s), 0 java-type mismatch(es) (subject token OBSERVED on 0, ASSUMED on 0), 25 distinct reference value(s) [DISCRIMINATING]
    stage pass? false   (control false)
    refused: 85 row(s) did not agree.
  target UStringValue.lt(value)
    control  {AGREE=729, HARNESS_ERROR=55}
    mutant   {AGREE=699, DIFFER=30, HARNESS_ERROR=55}
    statement UStringValue.lt(value): 784 rows, 729 measured, 699 agreed, 85 disagreed, 0 intended departure(s), 0 java-type mismatch(es) (subject token OBSERVED on 0, ASSUMED on 0), 25 distinct reference value(s) [DISCRIMINATING]
    stage pass? false   (control false)
    refused: 85 row(s) did not agree.
  first 6 diverging row(s):
  index	operation	inputs	historical	ported	verdict	note
  0	UIntegerValue.gt(value)	UINTEGER(0,0.0)@UIntegerValue | UINTEGER(0,0.0)@UIntegerValue	UBOOLEAN(true,0.0)@UBooleanValue	UBOOLEAN(true,1.0)@UBooleanValue	DIFFER	
  16	UIntegerValue.gt(value)	UINTEGER(0,1.0)@UIntegerValue | UINTEGER(0,1.0)@UIntegerValue	UBOOLEAN(true,0.0)@UBooleanValue	UBOOLEAN(true,1.0)@UBooleanValue	DIFFER	
  18	UIntegerValue.gt(value)	UINTEGER(0,1.0)@UIntegerValue | UINTEGER(1,1.0)@UIntegerValue	UBOOLEAN(true,0.0)@UBooleanValue	UBOOLEAN(true,0.617075064424681)@UBooleanValue	DIFFER	
  20	UIntegerValue.gt(value)	UINTEGER(0,1.0)@UIntegerValue | UINTEGER(-1,1.0)@UIntegerValue	UBOOLEAN(true,0.38292493557531904)@UBooleanValue	UBOOLEAN(true,1.0)@UBooleanValue	DIFFER	
  21	UIntegerValue.gt(value)	UINTEGER(0,1.0)@UIntegerValue | UINTEGER(2,0.5)@UIntegerValue	UBOOLEAN(true,8.116315292738818E-6)@UBooleanValue	UBOOLEAN(true,0.16945774662466784)@UBooleanValue	DIFFER	
  22	UIntegerValue.gt(value)	UINTEGER(0,1.0)@UIntegerValue | UINTEGER(-2,0.5)@UIntegerValue	UBOOLEAN(true,0.830542253375332)@UBooleanValue	UBOOLEAN(true,0.9999918836847073)@UBooleanValue	DIFFER	
===================================================================
=== detection power: P5-round-10dp ============================
defect               results rounded to ten decimal places -- the classic 'it looked the same when I printed it' port
aimed at             [URealValue.cos(), URealValue.divideBy(value), URealValue.inverse(), URealValue.mult(value), URealValue.sin(), URealValue.sqrt(), URealValue.tan()]
rows                 23963   (control 23963)
measured rows        21556   (control 21556)
agreement rows       21128   (control 21556)
verdict tally        {AGREE=21128, BOTH_THREW=1243, DIFFER=428, HARNESS_ERROR=1063, UNMEASURABLE=101}
DETECTED on          7 operation(s): [URealValue.cos(), URealValue.divideBy(value), URealValue.inverse(), URealValue.mult(value), URealValue.sin(), URealValue.sqrt(), URealValue.tan()]
stage passes         67   (control 74)
isClean() operations 186   (control 193)   the older predicate loses 7: [URealValue.cos(), URealValue.divideBy(value), URealValue.inverse(), URealValue.mult(value), URealValue.sin(), URealValue.sqrt(), URealValue.tan()]
  target URealValue.cos()
    control  {AGREE=24}
    mutant   {AGREE=7, DIFFER=17}
    statement URealValue.cos(): 24 rows, 24 measured, 7 agreed, 17 disagreed, 0 intended departure(s), 0 java-type mismatch(es) (subject token OBSERVED on 0, ASSUMED on 0), 14 distinct reference value(s) [DISCRIMINATING]
    stage pass? false   (control true)
    refused: 17 row(s) did not agree.
  target URealValue.divideBy(value)
    control  {AGREE=576}
    mutant   {AGREE=374, DIFFER=202}
    statement URealValue.divideBy(value): 576 rows, 576 measured, 374 agreed, 202 disagreed, 0 intended departure(s), 0 java-type mismatch(es) (subject token OBSERVED on 0, ASSUMED on 0), 241 distinct reference value(s) [DISCRIMINATING]
    stage pass? false   (control true)
    refused: 202 row(s) did not agree.
  target URealValue.inverse()
    control  {AGREE=24}
    mutant   {AGREE=19, DIFFER=5}
    statement URealValue.inverse(): 24 rows, 24 measured, 19 agreed, 5 disagreed, 0 intended departure(s), 0 java-type mismatch(es) (subject token OBSERVED on 0, ASSUMED on 0), 23 distinct reference value(s) [DISCRIMINATING]
    stage pass? false   (control true)
    refused: 5 row(s) did not agree.
  target URealValue.mult(value)
    control  {AGREE=576}
    mutant   {AGREE=414, DIFFER=162}
    statement URealValue.mult(value): 576 rows, 576 measured, 414 agreed, 162 disagreed, 0 intended departure(s), 0 java-type mismatch(es) (subject token OBSERVED on 0, ASSUMED on 0), 121 distinct reference value(s) [DISCRIMINATING]
    stage pass? false   (control true)
    refused: 162 row(s) did not agree.
  target URealValue.sin()
    control  {AGREE=24}
    mutant   {AGREE=5, DIFFER=19}
    statement URealValue.sin(): 24 rows, 24 measured, 5 agreed, 19 disagreed, 0 intended departure(s), 0 java-type mismatch(es) (subject token OBSERVED on 0, ASSUMED on 0), 21 distinct reference value(s) [DISCRIMINATING]
    stage pass? false   (control true)
    refused: 19 row(s) did not agree.
  target URealValue.sqrt()
    control  {AGREE=24}
    mutant   {AGREE=20, DIFFER=4}
    statement URealValue.sqrt(): 24 rows, 24 measured, 20 agreed, 4 disagreed, 0 intended departure(s), 0 java-type mismatch(es) (subject token OBSERVED on 0, ASSUMED on 0), 14 distinct reference value(s) [DISCRIMINATING]
    stage pass? false   (control true)
    refused: 4 row(s) did not agree.
  target URealValue.tan()
    control  {AGREE=24}
    mutant   {AGREE=5, DIFFER=19}
    statement URealValue.tan(): 24 rows, 24 measured, 5 agreed, 19 disagreed, 0 intended departure(s), 0 java-type mismatch(es) (subject token OBSERVED on 0, ASSUMED on 0), 21 distinct reference value(s) [DISCRIMINATING]
    stage pass? false   (control true)
    refused: 19 row(s) did not agree.
  first 6 diverging row(s):
  index	operation	inputs	historical	ported	verdict	note
  3	URealValue.cos()	UREAL(1.0,0.0)@URealValue	UREAL(0.5403023058681398,0.0)@URealValue	UREAL(0.5403023059,0.0)@URealValue	DIFFER	
  4	URealValue.cos()	UREAL(1.0,1.0)@URealValue	UREAL(0.5403023058681398,0.8414709848078965)@URealValue	UREAL(0.5403023059,0.8414709848)@URealValue	DIFFER	
  5	URealValue.cos()	UREAL(-1.0,0.0)@URealValue	UREAL(0.5403023058681398,0.0)@URealValue	UREAL(0.5403023059,0.0)@URealValue	DIFFER	
  6	URealValue.cos()	UREAL(-1.0,1.0)@URealValue	UREAL(0.5403023058681398,0.8414709848078965)@URealValue	UREAL(0.5403023059,0.8414709848)@URealValue	DIFFER	
  7	URealValue.cos()	UREAL(-1.0,0.5)@URealValue	UREAL(0.5403023058681398,0.42073549240394825)@URealValue	UREAL(0.5403023059,0.4207354924)@URealValue	DIFFER	
  8	URealValue.cos()	UREAL(0.5,0.5)@URealValue	UREAL(0.8775825618903728,0.2397127693021015)@URealValue	UREAL(0.8775825619,0.2397127693)@URealValue	DIFFER	
===================================================================
=== detection power: P6-equals-ignores-uncertainty ============================
defect               uEquals compares the values and returns certainty 1.0, ignoring the uncertainty component entirely
aimed at             [UBooleanValue.uEquals(value), UIntegerValue.uEquals(value), URealValue.uEquals(value), UStringValue.uEquals(value)]
rows                 23963   (control 23963)
measured rows        21556   (control 21556)
agreement rows       20000   (control 21556)
verdict tally        {AGREE=20000, BOTH_THREW=1243, DIFFER=1556, HARNESS_ERROR=1063, UNMEASURABLE=101}
DETECTED on          4 operation(s): [UBooleanValue.uEquals(value), UIntegerValue.uEquals(value), URealValue.uEquals(value), UStringValue.uEquals(value)]
stage passes         72   (control 74)
isClean() operations 191   (control 193)   the older predicate loses 2: [UIntegerValue.uEquals(value), URealValue.uEquals(value)]
  target UBooleanValue.uEquals(value)
    control  {AGREE=81, HARNESS_ERROR=40}
    mutant   {AGREE=8, DIFFER=73, HARNESS_ERROR=40}
    statement UBooleanValue.uEquals(value): 121 rows, 81 measured, 8 agreed, 113 disagreed, 0 intended departure(s), 0 java-type mismatch(es) (subject token OBSERVED on 0, ASSUMED on 0), 11 distinct reference value(s) [DISCRIMINATING]
    stage pass? false   (control false)
    refused: 113 row(s) did not agree.
  target UIntegerValue.uEquals(value)
    control  {AGREE=225}
    mutant   {AGREE=16, DIFFER=209}
    statement UIntegerValue.uEquals(value): 225 rows, 225 measured, 16 agreed, 209 disagreed, 0 intended departure(s), 0 java-type mismatch(es) (subject token OBSERVED on 0, ASSUMED on 0), 15 distinct reference value(s) [DISCRIMINATING]
    stage pass? false   (control true)
    refused: 209 row(s) did not agree.
  target URealValue.uEquals(value)
    control  {AGREE=576}
    mutant   {AGREE=26, DIFFER=550}
    statement URealValue.uEquals(value): 576 rows, 576 measured, 26 agreed, 550 disagreed, 0 intended departure(s), 0 java-type mismatch(es) (subject token OBSERVED on 0, ASSUMED on 0), 14 distinct reference value(s) [DISCRIMINATING]
    stage pass? false   (control true)
    refused: 550 row(s) did not agree.
  target UStringValue.uEquals(value)
    control  {AGREE=729, HARNESS_ERROR=55}
    mutant   {AGREE=5, DIFFER=724, HARNESS_ERROR=55}
    statement UStringValue.uEquals(value): 784 rows, 729 measured, 5 agreed, 779 disagreed, 0 intended departure(s), 0 java-type mismatch(es) (subject token OBSERVED on 0, ASSUMED on 0), 17 distinct reference value(s) [DISCRIMINATING]
    stage pass? false   (control false)
    refused: 779 row(s) did not agree.
  first 6 diverging row(s):
  index	operation	inputs	historical	ported	verdict	note
  1	UBooleanValue.uEquals(value)	UBOOLEAN(true,0.0)@UBooleanValue | UBOOLEAN(true,0.5)@UBooleanValue	UBOOLEAN(true,0.5)@UBooleanValue	UBOOLEAN(true,1.0)@UBooleanValue	DIFFER	
  2	UBooleanValue.uEquals(value)	UBOOLEAN(true,0.0)@UBooleanValue | UBOOLEAN(true,1.0)@UBooleanValue	UBOOLEAN(true,0.0)@UBooleanValue	UBOOLEAN(true,1.0)@UBooleanValue	DIFFER	
  3	UBooleanValue.uEquals(value)	UBOOLEAN(true,0.0)@UBooleanValue | UBOOLEAN(false,0.0)@UBooleanValue	UBOOLEAN(true,0.0)@UBooleanValue	UBOOLEAN(false,1.0)@UBooleanValue	DIFFER	
  4	UBooleanValue.uEquals(value)	UBOOLEAN(true,0.0)@UBooleanValue | UBOOLEAN(false,0.5)@UBooleanValue	UBOOLEAN(true,0.5)@UBooleanValue	UBOOLEAN(false,1.0)@UBooleanValue	DIFFER	
  5	UBooleanValue.uEquals(value)	UBOOLEAN(true,0.0)@UBooleanValue | UBOOLEAN(false,1.0)@UBooleanValue	UBOOLEAN(true,1.0)@UBooleanValue	UBOOLEAN(false,1.0)@UBooleanValue	DIFFER	
  6	UBooleanValue.uEquals(value)	UBOOLEAN(true,0.0)@UBooleanValue | UBOOLEAN(true,NaN)@UBooleanValue	UBOOLEAN(true,NaN)@UBooleanValue	UBOOLEAN(true,1.0)@UBooleanValue	DIFFER	
===================================================================
=== detection power: P7-undefined-on-zero-divisor ============================
defect               division by zero answers UndefinedValue where the historical throws
aimed at             [UIntegerValue.divideBy(value), UIntegerValue.divideByR(value), UIntegerValue.inverse(), UIntegerValue.mod(value), URealValue.divideBy(value), URealValue.inverse()]
rows                 23963   (control 23963)
measured rows        21556   (control 21556)
agreement rows       21451   (control 21556)
verdict tally        {AGREE=21451, BOTH_THREW=1181, DIFFER=105, HARNESS_ERROR=1063, MIXED=62, UNMEASURABLE=101}
DETECTED on          6 operation(s): [UIntegerValue.divideBy(value), UIntegerValue.divideByR(value), UIntegerValue.inverse(), UIntegerValue.mod(value), URealValue.divideBy(value), URealValue.inverse()]
stage passes         71   (control 74)
isClean() operations 190   (control 193)   the older predicate loses 3: [UIntegerValue.divideByR(value), URealValue.divideBy(value), URealValue.inverse()]
  target UIntegerValue.divideBy(value)
    control  {AGREE=195, BOTH_THREW=30}
    mutant   {AGREE=195, MIXED=30}
    statement UIntegerValue.divideBy(value): 225 rows, 195 measured, 195 agreed, 30 disagreed, 0 intended departure(s), 0 java-type mismatch(es) (subject token OBSERVED on 0, ASSUMED on 0), 92 distinct reference value(s) [DISCRIMINATING]
    stage pass? false   (control false)
    refused: 30 row(s) did not agree.
  target UIntegerValue.divideByR(value)
    control  {AGREE=225}
    mutant   {AGREE=195, DIFFER=30}
    statement UIntegerValue.divideByR(value): 225 rows, 225 measured, 195 agreed, 30 disagreed, 0 intended departure(s), 0 java-type mismatch(es) (subject token OBSERVED on 0, ASSUMED on 0), 137 distinct reference value(s) [DISCRIMINATING]
    stage pass? false   (control true)
    refused: 30 row(s) did not agree.
  target UIntegerValue.inverse()
    control  {AGREE=13, BOTH_THREW=2}
    mutant   {AGREE=13, MIXED=2}
    statement UIntegerValue.inverse(): 15 rows, 13 measured, 13 agreed, 2 disagreed, 0 intended departure(s), 0 java-type mismatch(es) (subject token OBSERVED on 0, ASSUMED on 0), 10 distinct reference value(s) [DISCRIMINATING]
    stage pass? false   (control false)
    refused: 2 row(s) did not agree.
  target UIntegerValue.mod(value)
    control  {AGREE=195, BOTH_THREW=30}
    mutant   {AGREE=195, MIXED=30}
    statement UIntegerValue.mod(value): 225 rows, 195 measured, 195 agreed, 30 disagreed, 0 intended departure(s), 0 java-type mismatch(es) (subject token OBSERVED on 0, ASSUMED on 0), 80 distinct reference value(s) [DISCRIMINATING]
    stage pass? false   (control false)
    refused: 30 row(s) did not agree.
  target URealValue.divideBy(value)
    control  {AGREE=576}
    mutant   {AGREE=504, DIFFER=72}
    statement URealValue.divideBy(value): 576 rows, 576 measured, 504 agreed, 72 disagreed, 0 intended departure(s), 0 java-type mismatch(es) (subject token OBSERVED on 0, ASSUMED on 0), 241 distinct reference value(s) [DISCRIMINATING]
    stage pass? false   (control true)
    refused: 72 row(s) did not agree.
  target URealValue.inverse()
    control  {AGREE=24}
    mutant   {AGREE=21, DIFFER=3}
    statement URealValue.inverse(): 24 rows, 24 measured, 21 agreed, 3 disagreed, 0 intended departure(s), 0 java-type mismatch(es) (subject token OBSERVED on 0, ASSUMED on 0), 23 distinct reference value(s) [DISCRIMINATING]
    stage pass? false   (control true)
    refused: 3 row(s) did not agree.
  first 6 diverging row(s):
  index	operation	inputs	historical	ported	verdict	note
  0	UIntegerValue.divideBy(value)	UINTEGER(0,0.0)@UIntegerValue | UINTEGER(0,0.0)@UIntegerValue	THROWN:java.lang.ArithmeticException	OPAQUE("org.tzi.use.uml.ocl.value.UndefinedValue|UndefinedValue{}")@UndefinedValue	MIXED	the reference threw and the subject returned. reference threw java.lang.ArithmeticException: / by zero / subject returned OPAQUE("org.tzi.use.uml.ocl.value.UndefinedValue|UndefinedValue{}")@UndefinedValue
  1	UIntegerValue.divideBy(value)	UINTEGER(0,0.0)@UIntegerValue | UINTEGER(0,1.0)@UIntegerValue	THROWN:java.lang.ArithmeticException	OPAQUE("org.tzi.use.uml.ocl.value.UndefinedValue|UndefinedValue{}")@UndefinedValue	MIXED	the reference threw and the subject returned. reference threw java.lang.ArithmeticException: / by zero / subject returned OPAQUE("org.tzi.use.uml.ocl.value.UndefinedValue|UndefinedValue{}")@UndefinedValue
  15	UIntegerValue.divideBy(value)	UINTEGER(0,1.0)@UIntegerValue | UINTEGER(0,0.0)@UIntegerValue	THROWN:java.lang.ArithmeticException	OPAQUE("org.tzi.use.uml.ocl.value.UndefinedValue|UndefinedValue{}")@UndefinedValue	MIXED	the reference threw and the subject returned. reference threw java.lang.ArithmeticException: / by zero / subject returned OPAQUE("org.tzi.use.uml.ocl.value.UndefinedValue|UndefinedValue{}")@UndefinedValue
  16	UIntegerValue.divideBy(value)	UINTEGER(0,1.0)@UIntegerValue | UINTEGER(0,1.0)@UIntegerValue	THROWN:java.lang.ArithmeticException	OPAQUE("org.tzi.use.uml.ocl.value.UndefinedValue|UndefinedValue{}")@UndefinedValue	MIXED	the reference threw and the subject returned. reference threw java.lang.ArithmeticException: / by zero / subject returned OPAQUE("org.tzi.use.uml.ocl.value.UndefinedValue|UndefinedValue{}")@UndefinedValue
  30	UIntegerValue.divideBy(value)	UINTEGER(1,0.0)@UIntegerValue | UINTEGER(0,0.0)@UIntegerValue	THROWN:java.lang.ArithmeticException	OPAQUE("org.tzi.use.uml.ocl.value.UndefinedValue|UndefinedValue{}")@UndefinedValue	MIXED	the reference threw and the subject returned. reference threw java.lang.ArithmeticException: / by zero / subject returned OPAQUE("org.tzi.use.uml.ocl.value.UndefinedValue|UndefinedValue{}")@UndefinedValue
  31	UIntegerValue.divideBy(value)	UINTEGER(1,0.0)@UIntegerValue | UINTEGER(0,1.0)@UIntegerValue	THROWN:java.lang.ArithmeticException	OPAQUE("org.tzi.use.uml.ocl.value.UndefinedValue|UndefinedValue{}")@UndefinedValue	MIXED	the reference threw and the subject returned. reference threw java.lang.ArithmeticException: / by zero / subject returned OPAQUE("org.tzi.use.uml.ocl.value.UndefinedValue|UndefinedValue{}")@UndefinedValue
===================================================================
=== detection power: P8-hides-behind-harness-error ============================
defect               P2's defect, plus an adapter that raises HarnessMarshallingException on exactly the rows where it would have shown (defect D-17)
aimed at             [UIntegerValue.add(value), UIntegerValue.minus(value), URealValue.add(value), URealValue.minus(value)]
rows                 23963   (control 23963)
measured rows        21088   (control 21556)
agreement rows       21088   (control 21556)
verdict tally        {AGREE=21088, BOTH_THREW=1243, HARNESS_ERROR=1531, UNMEASURABLE=101}
DETECTED on          0 operation(s): []
stage passes         70   (control 74)
isClean() operations 189   (control 193)   the older predicate loses 4: [UIntegerValue.add(value), UIntegerValue.minus(value), URealValue.add(value), URealValue.minus(value)]
  target UIntegerValue.add(value)
    control  {AGREE=225}
    mutant   {AGREE=134, HARNESS_ERROR=91}
    statement UIntegerValue.add(value): 225 rows, 134 measured, 134 agreed, 91 disagreed, 0 intended departure(s), 0 java-type mismatch(es) (subject token OBSERVED on 0, ASSUMED on 0), 53 distinct reference value(s) [DISCRIMINATING]
    stage pass? false   (control true)
    refused: 91 row(s) did not agree.
  target UIntegerValue.minus(value)
    control  {AGREE=225}
    mutant   {AGREE=134, HARNESS_ERROR=91}
    statement UIntegerValue.minus(value): 225 rows, 134 measured, 134 agreed, 91 disagreed, 0 intended departure(s), 0 java-type mismatch(es) (subject token OBSERVED on 0, ASSUMED on 0), 79 distinct reference value(s) [DISCRIMINATING]
    stage pass? false   (control true)
    refused: 91 row(s) did not agree.
  target URealValue.add(value)
    control  {AGREE=576}
    mutant   {AGREE=433, HARNESS_ERROR=143}
    statement URealValue.add(value): 576 rows, 433 measured, 433 agreed, 143 disagreed, 0 intended departure(s), 0 java-type mismatch(es) (subject token OBSERVED on 0, ASSUMED on 0), 110 distinct reference value(s) [DISCRIMINATING]
    stage pass? false   (control true)
    refused: 143 row(s) did not agree.
  target URealValue.minus(value)
    control  {AGREE=576}
    mutant   {AGREE=433, HARNESS_ERROR=143}
    statement URealValue.minus(value): 576 rows, 433 measured, 433 agreed, 143 disagreed, 0 intended departure(s), 0 java-type mismatch(es) (subject token OBSERVED on 0, ASSUMED on 0), 142 distinct reference value(s) [DISCRIMINATING]
    stage pass? false   (control true)
    refused: 143 row(s) did not agree.
===================================================================
=== detection power: P9-hides-behind-unsupported ============================
defect               P2's defect, plus supports() answering false for the operations that carry it
aimed at             [UIntegerValue.add(value), UIntegerValue.minus(value), URealValue.add(value), URealValue.minus(value)]
rows                 23963   (control 23963)
measured rows        19954   (control 21556)
agreement rows       19954   (control 21556)
verdict tally        {AGREE=19954, BOTH_THREW=1243, HARNESS_ERROR=1063, UNMEASURABLE=101, UNSUPPORTED=1602}
DETECTED on          0 operation(s): []
stage passes         70   (control 74)
isClean() operations 189   (control 193)   the older predicate loses 4: [UIntegerValue.add(value), UIntegerValue.minus(value), URealValue.add(value), URealValue.minus(value)]
  target UIntegerValue.add(value)
    control  {AGREE=225}
    mutant   {UNSUPPORTED=225}
    statement UIntegerValue.add(value): 225 rows, 0 measured, 0 agreed, 225 disagreed, 0 intended departure(s), 0 java-type mismatch(es) (subject token OBSERVED on 0, ASSUMED on 0), 0 distinct reference value(s) [NOT DISCRIMINATING]
    stage pass? false   (control true)
    refused: measured 0 row(s) of 225, needed at least 1. A result with too little evidence is not evidence.
    refused: 225 row(s) did not agree.
    refused: the reference side produced 0 distinct value(s) across 0 measured row(s). This operation could not have failed over this domain, so agreement on it is decided before either implementation runs and is not evidence of fidelity (defect D-15). Either widen the domain until the reference answers differently, or sign the operation off in AcceptedDegenerateOperations with a written rationale — which is copied into the report, so the weakness travels with the number.
  target UIntegerValue.minus(value)
    control  {AGREE=225}
    mutant   {UNSUPPORTED=225}
    statement UIntegerValue.minus(value): 225 rows, 0 measured, 0 agreed, 225 disagreed, 0 intended departure(s), 0 java-type mismatch(es) (subject token OBSERVED on 0, ASSUMED on 0), 0 distinct reference value(s) [NOT DISCRIMINATING]
    stage pass? false   (control true)
    refused: measured 0 row(s) of 225, needed at least 1. A result with too little evidence is not evidence.
    refused: 225 row(s) did not agree.
    refused: the reference side produced 0 distinct value(s) across 0 measured row(s). This operation could not have failed over this domain, so agreement on it is decided before either implementation runs and is not evidence of fidelity (defect D-15). Either widen the domain until the reference answers differently, or sign the operation off in AcceptedDegenerateOperations with a written rationale — which is copied into the report, so the weakness travels with the number.
  target URealValue.add(value)
    control  {AGREE=576}
    mutant   {UNSUPPORTED=576}
    statement URealValue.add(value): 576 rows, 0 measured, 0 agreed, 576 disagreed, 0 intended departure(s), 0 java-type mismatch(es) (subject token OBSERVED on 0, ASSUMED on 0), 0 distinct reference value(s) [NOT DISCRIMINATING]
    stage pass? false   (control true)
    refused: measured 0 row(s) of 576, needed at least 1. A result with too little evidence is not evidence.
    refused: 576 row(s) did not agree.
    refused: the reference side produced 0 distinct value(s) across 0 measured row(s). This operation could not have failed over this domain, so agreement on it is decided before either implementation runs and is not evidence of fidelity (defect D-15). Either widen the domain until the reference answers differently, or sign the operation off in AcceptedDegenerateOperations with a written rationale — which is copied into the report, so the weakness travels with the number.
  target URealValue.minus(value)
    control  {AGREE=576}
    mutant   {UNSUPPORTED=576}
    statement URealValue.minus(value): 576 rows, 0 measured, 0 agreed, 576 disagreed, 0 intended departure(s), 0 java-type mismatch(es) (subject token OBSERVED on 0, ASSUMED on 0), 0 distinct reference value(s) [NOT DISCRIMINATING]
    stage pass? false   (control true)
    refused: measured 0 row(s) of 576, needed at least 1. A result with too little evidence is not evidence.
    refused: 576 row(s) did not agree.
    refused: the reference side produced 0 distinct value(s) across 0 measured row(s). This operation could not have failed over this domain, so agreement on it is decided before either implementation runs and is not evidence of fidelity (defect D-15). Either widen the domain until the reference answers differently, or sign the operation off in AcceptedDegenerateOperations with a written rationale — which is copied into the report, so the weakness travels with the number.
===================================================================
=== detection power: P10-narrow-input-window ============================
defect               P2's defect, restricted to receivers whose value is exactly 42.0 -- a real arithmetic bug on an input no shipped corpus contains
aimed at             [UIntegerValue.add(value), UIntegerValue.minus(value), URealValue.add(value), URealValue.minus(value)]
rows                 23963   (control 23963)
measured rows        21556   (control 21556)
agreement rows       21556   (control 21556)
verdict tally        {AGREE=21556, BOTH_THREW=1243, HARNESS_ERROR=1063, UNMEASURABLE=101}
DETECTED on          0 operation(s): []
stage passes         74   (control 74)
isClean() operations 193   (control 193)   the older predicate loses 0: []
  target UIntegerValue.add(value)
    control  {AGREE=225}
    mutant   {AGREE=225}
    statement UIntegerValue.add(value): 225 rows, 225 measured, 225 agreed, 0 disagreed, 0 intended departure(s), 0 java-type mismatch(es) (subject token OBSERVED on 0, ASSUMED on 0), 88 distinct reference value(s) [DISCRIMINATING]
    stage pass? true   (control true)
  target UIntegerValue.minus(value)
    control  {AGREE=225}
    mutant   {AGREE=225}
    statement UIntegerValue.minus(value): 225 rows, 225 measured, 225 agreed, 0 disagreed, 0 intended departure(s), 0 java-type mismatch(es) (subject token OBSERVED on 0, ASSUMED on 0), 132 distinct reference value(s) [DISCRIMINATING]
    stage pass? true   (control true)
  target URealValue.add(value)
    control  {AGREE=576}
    mutant   {AGREE=576}
    statement URealValue.add(value): 576 rows, 576 measured, 576 agreed, 0 disagreed, 0 intended departure(s), 0 java-type mismatch(es) (subject token OBSERVED on 0, ASSUMED on 0), 164 distinct reference value(s) [DISCRIMINATING]
    stage pass? true   (control true)
  target URealValue.minus(value)
    control  {AGREE=576}
    mutant   {AGREE=576}
    statement URealValue.minus(value): 576 rows, 576 measured, 576 agreed, 0 disagreed, 0 intended departure(s), 0 java-type mismatch(es) (subject token OBSERVED on 0, ASSUMED on 0), 225 distinct reference value(s) [DISCRIMINATING]
    stage pass? true   (control true)
===================================================================
=== detection power: P11-negative-zero-collapse ============================
defect               a port that normalises -0.0 to 0.0 -- invisible to every printf and to Double.equals, visible only to an exact comparison
aimed at             [URealValue.floor(), URealValue.mult(value), URealValue.neg(), URealValue.round()]
rows                 23963   (control 23963)
measured rows        21556   (control 21556)
agreement rows       21497   (control 21556)
verdict tally        {AGREE=21497, BOTH_THREW=1243, DIFFER=59, HARNESS_ERROR=1063, UNMEASURABLE=101}
DETECTED on          3 operation(s): [URealValue.floor(), URealValue.mult(value), URealValue.neg()]
stage passes         71   (control 74)
isClean() operations 190   (control 193)   the older predicate loses 3: [URealValue.floor(), URealValue.mult(value), URealValue.neg()]
  target URealValue.floor()
    control  {AGREE=24}
    mutant   {AGREE=23, DIFFER=1}
    statement URealValue.floor(): 24 rows, 24 measured, 23 agreed, 1 disagreed, 0 intended departure(s), 0 java-type mismatch(es) (subject token OBSERVED on 0, ASSUMED on 0), 22 distinct reference value(s) [DISCRIMINATING]
    stage pass? false   (control true)
    refused: 1 row(s) did not agree.
  target URealValue.mult(value)
    control  {AGREE=576}
    mutant   {AGREE=520, DIFFER=56}
    statement URealValue.mult(value): 576 rows, 576 measured, 520 agreed, 56 disagreed, 0 intended departure(s), 0 java-type mismatch(es) (subject token OBSERVED on 0, ASSUMED on 0), 121 distinct reference value(s) [DISCRIMINATING]
    stage pass? false   (control true)
    refused: 56 row(s) did not agree.
  target URealValue.neg()
    control  {AGREE=24}
    mutant   {AGREE=22, DIFFER=2}
    statement URealValue.neg(): 24 rows, 24 measured, 22 agreed, 2 disagreed, 0 intended departure(s), 0 java-type mismatch(es) (subject token OBSERVED on 0, ASSUMED on 0), 23 distinct reference value(s) [DISCRIMINATING]
    stage pass? false   (control true)
    refused: 2 row(s) did not agree.
  target URealValue.round()
    control  {AGREE=24}
    mutant   {AGREE=24}
    statement URealValue.round(): 24 rows, 24 measured, 24 agreed, 0 disagreed, 0 intended departure(s), 0 java-type mismatch(es) (subject token OBSERVED on 0, ASSUMED on 0), 18 distinct reference value(s) [DISCRIMINATING]
    stage pass? true   (control true)
  first 6 diverging row(s):
  index	operation	inputs	historical	ported	verdict	note
  2	URealValue.floor()	UREAL(-0.0,0.0)@URealValue	UREAL(-0.0,0.0)@URealValue	UREAL(0.0,0.0)@URealValue	DIFFER	
  2	URealValue.mult(value)	UREAL(0.0,0.0)@URealValue | UREAL(-0.0,0.0)@URealValue	UREAL(-0.0,0.0)@URealValue	UREAL(0.0,0.0)@URealValue	DIFFER	
  5	URealValue.mult(value)	UREAL(0.0,0.0)@URealValue | UREAL(-1.0,0.0)@URealValue	UREAL(-0.0,0.0)@URealValue	UREAL(0.0,0.0)@URealValue	DIFFER	
  6	URealValue.mult(value)	UREAL(0.0,0.0)@URealValue | UREAL(-1.0,1.0)@URealValue	UREAL(-0.0,0.0)@URealValue	UREAL(0.0,0.0)@URealValue	DIFFER	
  7	URealValue.mult(value)	UREAL(0.0,0.0)@URealValue | UREAL(-1.0,0.5)@URealValue	UREAL(-0.0,0.0)@URealValue	UREAL(0.0,0.0)@URealValue	DIFFER	
  9	URealValue.mult(value)	UREAL(0.0,0.0)@URealValue | UREAL(-0.5,0.25)@URealValue	UREAL(-0.0,0.0)@URealValue	UREAL(0.0,0.0)@URealValue	DIFFER	
===================================================================
=== planted defects the harness did NOT see =======================
  ??? P11-negative-zero-collapse / URealValue.round()  [STAGE PASS]
===================================================================
=== isClean() against requireStagePass, on the same defects =======
  probe                        detected  isClean lost  gate lost  divergence with NO change in the pass bit
  P1-off-by-one-index               3             0          0          3
  P2-linear-uncertainty             4             4          4          0
  P3-hypot-uncertainty              4             4          4          0
  P4-le-for-lt                      6             4          4          2
  P5-round-10dp                     7             7          7          0
  P6-equals-ignores-uncertainty      4             2          2          2
  P7-undefined-on-zero-divisor      6             3          3          3
  P8-hides-behind-harness-error      0             4          4          0
  P9-hides-behind-unsupported       0             4          4          0
  P10-narrow-input-window           0             0          0          0
  P11-negative-zero-collapse        3             3          3          0
  operations where a real infidelity leaves the pass bit unchanged, because a PERFECT port already fails the gate there:
    !!! P1-off-by-one-index / UStringValue.at(int)
    !!! P1-off-by-one-index / UStringValue.uAt(int)
    !!! P1-off-by-one-index / UStringValue.uSubstring(int,int)
    !!! P4-le-for-lt / UStringValue.gt(value)
    !!! P4-le-for-lt / UStringValue.lt(value)
    !!! P6-equals-ignores-uncertainty / UBooleanValue.uEquals(value)
    !!! P6-equals-ignores-uncertainty / UStringValue.uEquals(value)
    !!! P7-undefined-on-zero-divisor / UIntegerValue.divideBy(value)
    !!! P7-undefined-on-zero-divisor / UIntegerValue.inverse()
    !!! P7-undefined-on-zero-divisor / UIntegerValue.mod(value)
===================================================================
=== representation census: what the reference actually returns =====
operations                 355  (276 ever returned a classed value)
--- every runtime class the reference returned, and on how many rows
  1732	java.lang.Boolean
  100	java.lang.Double
  2029	java.lang.Integer
  244	java.lang.String
  4	org.tzi.use.uml.ocl.type.BooleanType
  16	org.tzi.use.uml.ocl.type.IntegerType
  2	org.tzi.use.uml.ocl.type.RealType
  30	org.tzi.use.uml.ocl.type.StringType
  18	org.tzi.use.uml.ocl.type.UBooleanType
  30	org.tzi.use.uml.ocl.type.UIntegerType
  48	org.tzi.use.uml.ocl.type.URealType
  54	org.tzi.use.uml.ocl.type.UStringType
  279	org.tzi.use.uml.ocl.value.BooleanValue
  43	org.tzi.use.uml.ocl.value.IntegerValue
  46	org.tzi.use.uml.ocl.value.RealValue
  27	org.tzi.use.uml.ocl.value.SequenceValue
  97	org.tzi.use.uml.ocl.value.StringValue
  10512	org.tzi.use.uml.ocl.value.UBooleanValue
  1174	org.tzi.use.uml.ocl.value.UIntegerValue
  4176	org.tzi.use.uml.ocl.value.URealValue
  880	org.tzi.use.uml.ocl.value.UStringValue
  15	uDataTypes.UInteger
--- classes per UValue.Kind (two means the KIND is ambiguous: D-18) ---
  BOOLEAN  [java.lang.Boolean, org.tzi.use.uml.ocl.value.BooleanValue]   <== two representations of one kind
  INTEGER  [java.lang.Integer, org.tzi.use.uml.ocl.value.IntegerValue]   <== two representations of one kind
  OPAQUE  [org.tzi.use.uml.ocl.type.BooleanType, org.tzi.use.uml.ocl.type.IntegerType, org.tzi.use.uml.ocl.type.RealType, org.tzi.use.uml.ocl.type.StringType, org.tzi.use.uml.ocl.type.UBooleanType, org.tzi.use.uml.ocl.type.UIntegerType, org.tzi.use.uml.ocl.type.URealType, org.tzi.use.uml.ocl.type.UStringType, uDataTypes.UInteger]   <== two representations of one kind
  REAL  [java.lang.Double, org.tzi.use.uml.ocl.value.RealValue]   <== two representations of one kind
  SEQUENCE  [org.tzi.use.uml.ocl.value.SequenceValue]
  STRING  [java.lang.String, org.tzi.use.uml.ocl.value.StringValue]   <== two representations of one kind
  UBOOLEAN  [org.tzi.use.uml.ocl.value.UBooleanValue]
  UINTEGER  [org.tzi.use.uml.ocl.value.UIntegerValue]
  UREAL  [org.tzi.use.uml.ocl.value.URealValue]
  USTRING  [org.tzi.use.uml.ocl.value.UStringValue]
--- operations whose OWN answers used more than one class -------------
  (none)
===================================================================
=== corpus sensitivity ============================================
full corpora         uReal=24, uInteger=15, uBoolean=11, uString=28, boolean=4, string=16, zeroDivisors=7, indexBoundaries=8
finite-only corpora  uReal=19, uInteger=14, uBoolean=10, uString=27, boolean=4, string=16, zeroDivisors=7, indexBoundaries=8
probe                          detecting rows   ops detected
                               full  finite     full  finite
  P0-perfect                       0      0         0      0
  P1-off-by-one-index            244    234         3      3
  P2-linear-uncertainty          468    456         4      4
  P3-hypot-uncertainty            24     20         4      4
  P4-le-for-lt                   300    294         6      6
  P5-round-10dp                  428    374         7      7
  P6-equals-ignores-uncertainty  1556   1245         4      4
  P7-undefined-on-zero-divisor   167    146         6      6
  P8-hides-behind-harness-error     0      0         0      0
  P9-hides-behind-unsupported      0      0         0      0
  P10-narrow-input-window          0      0         0      0
  P11-negative-zero-collapse      59     55         3      3
===================================================================
=== the inventory boundary ========================================
public instance methods on the 8 marshallable receivers  392
expressible as a UOp (before de-duplication)             355
NOT nameable, therefore absent from every report         37
  BooleanValue
      compareTo[class java.lang.Object]
      equals[class java.lang.Object]
      toStringWithType[class java.lang.StringBuilder]
      toString[class java.lang.StringBuilder]
  IntegerValue
      compareTo[class java.lang.Object]
      equals[class java.lang.Object]
      toStringWithType[class java.lang.StringBuilder]
      toString[class java.lang.StringBuilder]
  RealValue
      compareTo[class java.lang.Object]
      equals[class java.lang.Object]
      toStringWithType[class java.lang.StringBuilder]
      toString[class java.lang.StringBuilder]
  SBooleanValue
      compareTo[class java.lang.Object]
      equals[class java.lang.Object]
      toStringWithType[class java.lang.StringBuilder]
      toString[class java.lang.StringBuilder]
  StringValue
      compareTo[class java.lang.Object]
      equals[class java.lang.Object]
      toStringWithType[class java.lang.StringBuilder]
      toString[class java.lang.StringBuilder]
  UBooleanValue
      compareTo[class java.lang.Object]
      equals[class java.lang.Object]
      toStringWithType[class java.lang.StringBuilder]
      toString[class java.lang.StringBuilder]
  UIntegerValue
      compareTo[class java.lang.Object]
      equals[class java.lang.Object]
      toStringWithType[class java.lang.StringBuilder]
      toString[class java.lang.StringBuilder]
  URealValue
      compareTo[class java.lang.Object]
      equals[class java.lang.Object]
      toStringWithType[class java.lang.StringBuilder]
      toString[class java.lang.StringBuilder]
  UStringValue
      compareTo[class java.lang.Object]
      equals[class java.lang.Object]
      indexOf[class org.tzi.use.uml.ocl.value.StringValue]
      toStringWithType[class java.lang.StringBuilder]
      toString[class java.lang.StringBuilder]
distinct UOp keys in the inventory                       355
===================================================================
=== the metric, recomputed by hand ================================
operation            URealValue.neg()
receivers            24  (the URealValue corpus)
rows                 24
    UREAL(0.0,0.0)@URealValue  ->  UREAL(-0.0,0.0)@URealValue
    UREAL(0.0,1.0)@URealValue  ->  UREAL(-0.0,1.0)@URealValue
    UREAL(-0.0,0.0)@URealValue  ->  UREAL(0.0,0.0)@URealValue
    UREAL(1.0,0.0)@URealValue  ->  UREAL(-1.0,0.0)@URealValue
    UREAL(1.0,1.0)@URealValue  ->  UREAL(-1.0,1.0)@URealValue
    UREAL(-1.0,0.0)@URealValue  ->  UREAL(1.0,0.0)@URealValue
    UREAL(-1.0,1.0)@URealValue  ->  UREAL(1.0,1.0)@URealValue
    UREAL(-1.0,0.5)@URealValue  ->  UREAL(1.0,0.5)@URealValue
    UREAL(0.5,0.5)@URealValue  ->  UREAL(-0.5,0.5)@URealValue
    UREAL(-0.5,0.25)@URealValue  ->  UREAL(0.5,0.25)@URealValue
    UREAL(2.0,0.0)@URealValue  ->  UREAL(-2.0,0.0)@URealValue
    UREAL(100.0,0.001)@URealValue  ->  UREAL(-100.0,0.001)@URealValue
    UREAL(-100.0,0.001)@URealValue  ->  UREAL(100.0,0.001)@URealValue
    UREAL(4.9E-324,0.0)@URealValue  ->  UREAL(-4.9E-324,0.0)@URealValue
    UREAL(1.7976931348623157E308,0.0)@URealValue  ->  UREAL(-1.7976931348623157E308,0.0)@URealValue
    UREAL(-1.7976931348623157E308,0.0)@URealValue  ->  UREAL(1.7976931348623157E308,0.0)@URealValue
    UREAL(NaN,0.0)@URealValue  ->  UREAL(NaN,0.0)@URealValue
    UREAL(Infinity,0.0)@URealValue  ->  UREAL(-Infinity,0.0)@URealValue
    UREAL(-Infinity,0.0)@URealValue  ->  UREAL(Infinity,0.0)@URealValue
    UREAL(1.0,NaN)@URealValue  ->  UREAL(-1.0,NaN)@URealValue
    UREAL(1.0,Infinity)@URealValue  ->  UREAL(-1.0,Infinity)@URealValue
    UREAL(1.0,-1.0)@URealValue  ->  UREAL(-1.0,1.0)@URealValue
    UREAL(-46.064505,0.782649)@URealValue  ->  UREAL(46.064505,0.782649)@URealValue
    UREAL(28.230986,0.91554)@URealValue  ->  UREAL(-28.230986,0.91554)@URealValue
by hand              23  [UREAL(-0.0,0.0)@URealValue, UREAL(-0.0,1.0)@URealValue, UREAL(-0.5,0.5)@URealValue, UREAL(-1.0,0.0)@URealValue, UREAL(-1.0,1.0)@URealValue, UREAL(-1.0,Infinity)@URealValue, UREAL(-1.0,NaN)@URealValue, UREAL(-1.7976931348623157E308,0.0)@URealValue, UREAL(-100.0,0.001)@URealValue, UREAL(-2.0,0.0)@URealValue, UREAL(-28.230986,0.91554)@URealValue, UREAL(-4.9E-324,0.0)@URealValue, UREAL(-Infinity,0.0)@URealValue, UREAL(0.0,0.0)@URealValue, UREAL(0.5,0.25)@URealValue, UREAL(1.0,0.0)@URealValue, UREAL(1.0,0.5)@URealValue, UREAL(1.0,1.0)@URealValue, UREAL(1.7976931348623157E308,0.0)@URealValue, UREAL(100.0,0.001)@URealValue, UREAL(46.064505,0.782649)@URealValue, UREAL(Infinity,0.0)@URealValue, UREAL(NaN,0.0)@URealValue]
Result.referenceValues() 23  [UREAL(-0.0,0.0)@URealValue, UREAL(-0.0,1.0)@URealValue, UREAL(-0.5,0.5)@URealValue, UREAL(-1.0,0.0)@URealValue, UREAL(-1.0,1.0)@URealValue, UREAL(-1.0,Infinity)@URealValue, UREAL(-1.0,NaN)@URealValue, UREAL(-1.7976931348623157E308,0.0)@URealValue, UREAL(-100.0,0.001)@URealValue, UREAL(-2.0,0.0)@URealValue, UREAL(-28.230986,0.91554)@URealValue, UREAL(-4.9E-324,0.0)@URealValue, UREAL(-Infinity,0.0)@URealValue, UREAL(0.0,0.0)@URealValue, UREAL(0.5,0.25)@URealValue, UREAL(1.0,0.0)@URealValue, UREAL(1.0,0.5)@URealValue, UREAL(1.0,1.0)@URealValue, UREAL(1.7976931348623157E308,0.0)@URealValue, UREAL(100.0,0.001)@URealValue, UREAL(46.064505,0.782649)@URealValue, UREAL(Infinity,0.0)@URealValue, UREAL(NaN,0.0)@URealValue]
summary              URealValue.neg(): 24 rows, 24 measured, 23 distinct ref, AGREE=24
===================================================================
=== D-43: two readings of the same measurement ====================
  subject                              DIFFER     divOps   passes   typeMism notes ASSUMED
  P0-perfect                                0          0       74          0            0
  P12-boxed-primitive                       0          0       74       4105            0
  P13-factory-typed-adapter                 0          0       74       4105         4105
  P14-observing-adapter                     0          0       74          0            0
  stage passes the port with a DEFECT loses    0
  stage passes the port with NO defect loses   0   <- was 29 before round 8; the false-divergence mode
  operations carrying a java-type mismatch:
      P12 182   P13 182   P14 0
  first row of the ADAPTER's omission, which is AGREE and says so:
      0	BooleanValue.compareTo(value)	BOOLEAN(true)@BooleanValue | BOOLEAN(true)@BooleanValue	INTEGER(0)@Integer	INTEGER(0)@IntegerValue	AGREE	java type mismatch: reference returned java.lang.Integer (INTEGER(0)@Integer) / subject returned org.tzi.use.uml.ocl.value.IntegerValue (INTEGER(0)@IntegerValue); the content is IDENTICAL -- right content, wrong Java type (defect D-18). This row is scored AGREE and counted in rows.javaTypeMismatch, not scored as a divergence: at S1 the ported side's class cannot be authentically observed, because no ported value class exists to observe, so a type-only difference measures the adapter and not the port (D-43). Provenance: reference OBSERVED, subject ASSUMED (OBSERVED = read off the object that side returned; ASSUMED = the factory default for the kind, which is wrong for 182 of 285 operations). The subject's adapter never looked at what its implementation returned, so this difference is a finding about the ADAPTER and not about the port (D-43); an adapter must attribute through UValue.observedFrom(Object).
  first row of the PORT's real wrong class, same figure, different note:
      0	BooleanValue.compareTo(value)	BOOLEAN(true)@BooleanValue | BOOLEAN(true)@BooleanValue	INTEGER(0)@Integer	INTEGER(0)@IntegerValue	AGREE	java type mismatch: reference returned java.lang.Integer (INTEGER(0)@Integer) / subject returned org.tzi.use.uml.ocl.value.IntegerValue (INTEGER(0)@IntegerValue); the content is IDENTICAL -- right content, wrong Java type (defect D-18). This row is scored AGREE and counted in rows.javaTypeMismatch, not scored as a divergence: at S1 the ported side's class cannot be authentically observed, because no ported value class exists to observe, so a type-only difference measures the adapter and not the port (D-43). Provenance: reference OBSERVED, subject OBSERVED (OBSERVED = read off the object that side returned; ASSUMED = the factory default for the kind, which is wrong for 182 of 285 operations). Whether the object the subject observed is the one its implementation returned is not checkable by this harness.
===================================================================
=== D-18: right content, wrong Java type =========================
operations           355
control  rows        23963, measured 21556, agreed 21556  {AGREE=21556, BOTH_THREW=1243, HARNESS_ERROR=1063, UNMEASURABLE=101}
boxed    rows        23963, measured 21556, agreed 21556  {AGREE=21556, BOTH_THREW=1243, HARNESS_ERROR=1063, UNMEASURABLE=101}
control DIFFER+MIXED 0   <- MUST be 0
control javaTypeMismatch 0   <- MUST be 0
boxed   DIFFER rows  0   <- 0 since round 8: a type-only difference is not a divergence
boxed   javaTypeMismatch rows 4105   <- where the finding lives now
MEASURED on          182 of 355 operations
stage passes         control 74 -> boxed 74; lost 0: []
  a sample of the rows, which still SHOW both classes:
  index	operation	inputs	historical	ported	verdict	note
  0	BooleanValue.compareTo(value)	BOOLEAN(true)@BooleanValue | BOOLEAN(true)@BooleanValue	INTEGER(0)@Integer	INTEGER(0)@IntegerValue	AGREE	java type mismatch: reference returned java.lang.Integer (INTEGER(0)@Integer) / subject returned org.tzi.use.uml.ocl.value.IntegerValue (INTEGER(0)@IntegerValue); the content is IDENTICAL -- right content, wrong Java type (defect D-18). This row is scored AGREE and counted in rows.javaTypeMismatch, not scored as a divergence: at S1 the ported side's class cannot be authentically observed, because no ported value class exists to observe, so a type-only difference measures the adapter and not the port (D-43). Provenance: reference OBSERVED, subject OBSERVED (OBSERVED = read off the object that side returned; ASSUMED = the factory default for the kind, which is wrong for 182 of 285 operations). Whether the object the subject observed is the one its implementation returned is not checkable by this harness.
  1	BooleanValue.compareTo(value)	BOOLEAN(true)@BooleanValue | BOOLEAN(false)@BooleanValue	INTEGER(1)@Integer	INTEGER(1)@IntegerValue	AGREE	java type mismatch: reference returned java.lang.Integer (INTEGER(1)@Integer) / subject returned org.tzi.use.uml.ocl.value.IntegerValue (INTEGER(1)@IntegerValue); the content is IDENTICAL -- right content, wrong Java type (defect D-18). This row is scored AGREE and counted in rows.javaTypeMismatch, not scored as a divergence: at S1 the ported side's class cannot be authentically observed, because no ported value class exists to observe, so a type-only difference measures the adapter and not the port (D-43). Provenance: reference OBSERVED, subject OBSERVED (OBSERVED = read off the object that side returned; ASSUMED = the factory default for the kind, which is wrong for 182 of 285 operations). Whether the object the subject observed is the one its implementation returned is not checkable by this harness.
  2	BooleanValue.compareTo(value)	BOOLEAN(false)@BooleanValue | BOOLEAN(true)@BooleanValue	INTEGER(-1)@Integer	INTEGER(-1)@IntegerValue	AGREE	java type mismatch: reference returned java.lang.Integer (INTEGER(-1)@Integer) / subject returned org.tzi.use.uml.ocl.value.IntegerValue (INTEGER(-1)@IntegerValue); the content is IDENTICAL -- right content, wrong Java type (defect D-18). This row is scored AGREE and counted in rows.javaTypeMismatch, not scored as a divergence: at S1 the ported side's class cannot be authentically observed, because no ported value class exists to observe, so a type-only difference measures the adapter and not the port (D-43). Provenance: reference OBSERVED, subject OBSERVED (OBSERVED = read off the object that side returned; ASSUMED = the factory default for the kind, which is wrong for 182 of 285 operations). Whether the object the subject observed is the one its implementation returned is not checkable by this harness.
  3	BooleanValue.compareTo(value)	BOOLEAN(false)@BooleanValue | BOOLEAN(false)@BooleanValue	INTEGER(0)@Integer	INTEGER(0)@IntegerValue	AGREE	java type mismatch: reference returned java.lang.Integer (INTEGER(0)@Integer) / subject returned org.tzi.use.uml.ocl.value.IntegerValue (INTEGER(0)@IntegerValue); the content is IDENTICAL -- right content, wrong Java type (defect D-18). This row is scored AGREE and counted in rows.javaTypeMismatch, not scored as a divergence: at S1 the ported side's class cannot be authentically observed, because no ported value class exists to observe, so a type-only difference measures the adapter and not the port (D-43). Provenance: reference OBSERVED, subject OBSERVED (OBSERVED = read off the object that side returned; ASSUMED = the factory default for the kind, which is wrong for 182 of 285 operations). Whether the object the subject observed is the one its implementation returned is not checkable by this harness.
  0	BooleanValue.hashCode()	BOOLEAN(true)@BooleanValue	INTEGER(1231)@Integer	INTEGER(1231)@IntegerValue	AGREE	java type mismatch: reference returned java.lang.Integer (INTEGER(1231)@Integer) / subject returned org.tzi.use.uml.ocl.value.IntegerValue (INTEGER(1231)@IntegerValue); the content is IDENTICAL -- right content, wrong Java type (defect D-18). This row is scored AGREE and counted in rows.javaTypeMismatch, not scored as a divergence: at S1 the ported side's class cannot be authentically observed, because no ported value class exists to observe, so a type-only difference measures the adapter and not the port (D-43). Provenance: reference OBSERVED, subject OBSERVED (OBSERVED = read off the object that side returned; ASSUMED = the factory default for the kind, which is wrong for 182 of 285 operations). Whether the object the subject observed is the one its implementation returned is not checkable by this harness.
  1	BooleanValue.hashCode()	BOOLEAN(false)@BooleanValue	INTEGER(1237)@Integer	INTEGER(1237)@IntegerValue	AGREE	java type mismatch: reference returned java.lang.Integer (INTEGER(1237)@Integer) / subject returned org.tzi.use.uml.ocl.value.IntegerValue (INTEGER(1237)@IntegerValue); the content is IDENTICAL -- right content, wrong Java type (defect D-18). This row is scored AGREE and counted in rows.javaTypeMismatch, not scored as a divergence: at S1 the ported side's class cannot be authentically observed, because no ported value class exists to observe, so a type-only difference measures the adapter and not the port (D-43). Provenance: reference OBSERVED, subject OBSERVED (OBSERVED = read off the object that side returned; ASSUMED = the factory default for the kind, which is wrong for 182 of 285 operations). Whether the object the subject observed is the one its implementation returned is not checkable by this harness.
=================================================================
[INFO] Tests run: 7, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 4.631 s -- in Detection power: subtle infidelities in a ported U-type
[INFO] Running Uncertainty differential smoke
=== S1 differential smoke =========================================
seed                 20260817
reference            historical  /home/xoruser/msc-4/use-msc2026/use-core/target/test-classes/historical/use.jar
subject              stub-faithful
sha256 use.jar  80ac8ae433b8345677472019991356950f094f4a104cfbce1f75783a7308788d
sha256 atenearesearchgroup.uncertainty.jar  53b2a43feb0a0a39844a60278dd80a7d4b975ef324fb05c6db28831e835e59d0
corpus size          28  (22 boundary + 6 random)
rows                 784
measured             784
tally                URealValue.add(value): 784 rows, 784 measured, 258 distinct ref, AGREE=784
--- first 12 rows -------------------------------------------------
index	operation	inputs	historical	ported	verdict	note
0	URealValue.add(value)	UREAL(0.0,0.0)@URealValue | UREAL(0.0,0.0)@URealValue	UREAL(0.0,0.0)@URealValue	UREAL(0.0,0.0)@URealValue	AGREE	
1	URealValue.add(value)	UREAL(0.0,0.0)@URealValue | UREAL(0.0,1.0)@URealValue	UREAL(0.0,1.0)@URealValue	UREAL(0.0,1.0)@URealValue	AGREE	
2	URealValue.add(value)	UREAL(0.0,0.0)@URealValue | UREAL(-0.0,0.0)@URealValue	UREAL(0.0,0.0)@URealValue	UREAL(0.0,0.0)@URealValue	AGREE	
3	URealValue.add(value)	UREAL(0.0,0.0)@URealValue | UREAL(1.0,0.0)@URealValue	UREAL(1.0,0.0)@URealValue	UREAL(1.0,0.0)@URealValue	AGREE	
4	URealValue.add(value)	UREAL(0.0,0.0)@URealValue | UREAL(1.0,1.0)@URealValue	UREAL(1.0,1.0)@URealValue	UREAL(1.0,1.0)@URealValue	AGREE	
5	URealValue.add(value)	UREAL(0.0,0.0)@URealValue | UREAL(-1.0,0.0)@URealValue	UREAL(-1.0,0.0)@URealValue	UREAL(-1.0,0.0)@URealValue	AGREE	
6	URealValue.add(value)	UREAL(0.0,0.0)@URealValue | UREAL(-1.0,1.0)@URealValue	UREAL(-1.0,1.0)@URealValue	UREAL(-1.0,1.0)@URealValue	AGREE	
7	URealValue.add(value)	UREAL(0.0,0.0)@URealValue | UREAL(-1.0,0.5)@URealValue	UREAL(-1.0,0.5)@URealValue	UREAL(-1.0,0.5)@URealValue	AGREE	
8	URealValue.add(value)	UREAL(0.0,0.0)@URealValue | UREAL(0.5,0.5)@URealValue	UREAL(0.5,0.5)@URealValue	UREAL(0.5,0.5)@URealValue	AGREE	
9	URealValue.add(value)	UREAL(0.0,0.0)@URealValue | UREAL(-0.5,0.25)@URealValue	UREAL(-0.5,0.25)@URealValue	UREAL(-0.5,0.25)@URealValue	AGREE	
10	URealValue.add(value)	UREAL(0.0,0.0)@URealValue | UREAL(2.0,0.0)@URealValue	UREAL(2.0,0.0)@URealValue	UREAL(2.0,0.0)@URealValue	AGREE	
11	URealValue.add(value)	UREAL(0.0,0.0)@URealValue | UREAL(100.0,0.001)@URealValue	UREAL(100.0,0.001)@URealValue	UREAL(100.0,0.001)@URealValue	AGREE	
report               /home/xoruser/msc-4/use-msc2026/use-core/target/differential/s1-smoke-ureal-add.tsv
golden (matched)     /home/xoruser/msc-4/use-msc2026/docs/port2/differential/s1-smoke-ureal-add.tsv
isClean()            true   <- measured, NOT the pass criterion (D-36)
stage gate failures  []
javaTypeMismatch     0   <- clause 6 (D-43); the gate does not make it, a stage must
STAGE STATEMENT      URealValue.add(value): 784 rows, 784 measured, 784 agreed, 0 disagreed, 0 intended departure(s), 0 java-type mismatch(es) (subject token OBSERVED on 0, ASSUMED on 0), 258 distinct reference value(s) [DISCRIMINATING]
===================================================================
=== S1 fault-injection check ======================================
seed                 20260817
subject              stub-faulty-minus  (minus uses |ua-ub|)
rows                 784
measured             784
tally                URealValue.minus(value): 784 rows, 784 measured, 389 distinct ref, AGREE=558, DIFFER=226
--- first 5 disagreements -----------------------------------------
index	operation	inputs	historical	ported	verdict	note
29	URealValue.minus(value)	UREAL(0.0,1.0)@URealValue | UREAL(0.0,1.0)@URealValue	UREAL(0.0,1.4142135623730951)@URealValue	UREAL(0.0,0.0)@URealValue	DIFFER	
32	URealValue.minus(value)	UREAL(0.0,1.0)@URealValue | UREAL(1.0,1.0)@URealValue	UREAL(-1.0,1.4142135623730951)@URealValue	UREAL(-1.0,0.0)@URealValue	DIFFER	
34	URealValue.minus(value)	UREAL(0.0,1.0)@URealValue | UREAL(-1.0,1.0)@URealValue	UREAL(1.0,1.4142135623730951)@URealValue	UREAL(1.0,0.0)@URealValue	DIFFER	
35	URealValue.minus(value)	UREAL(0.0,1.0)@URealValue | UREAL(-1.0,0.5)@URealValue	UREAL(1.0,1.118033988749895)@URealValue	UREAL(1.0,0.5)@URealValue	DIFFER	
36	URealValue.minus(value)	UREAL(0.0,1.0)@URealValue | UREAL(0.5,0.5)@URealValue	UREAL(-0.5,1.118033988749895)@URealValue	UREAL(-0.5,0.5)@URealValue	DIFFER	
report               /home/xoruser/msc-4/use-msc2026/use-core/target/differential/s1-smoke-ureal-minus-faulty.tsv
golden (matched)     /home/xoruser/msc-4/use-msc2026/docs/port2/differential/s1-smoke-ureal-minus-faulty.tsv
===================================================================
refused              sweep of URealValue.minus(value) is not a stage pass: - 226 row(s) did not agree. tally: URealValue.minus(value): 784 rows, 784 measured, 389 distinct ref, AGREE=558, DIFFER=226
[INFO] Tests run: 6, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.120 s -- in Uncertainty differential smoke
[INFO] Running Unwritten-port invariant
=== D-15: the constant-literal subject, stage-shaped ==============
seed                       20260817
corpora                    uReal=24, uInteger=15, uBoolean=11, uString=28, boolean=4, string=16, zeroDivisors=7, indexBoundaries=8; receivers=104
operations                 355
literals the subject holds 276  (one per operation the reference ever answered with a value)
codomain census            355 operations: 79 measured nothing, 158 single-valued (NOT DISCRIMINATING), 118 discriminating
isClean() AND degenerate   119   <- the size of the door: a stage asserting isClean() reads these as PASS
refused by the stage gate  119 of 119
stage passes (must be 0)   0
--- operations that measured NOTHING (79) -------------------------
  ... BooleanValue.setTypeToRuntimeType()  BooleanValue.setTypeToRuntimeType(): 2 rows, 0 measured, 0 distinct ref, MIXED=2
  ... IntegerValue.setTypeToRuntimeType()  IntegerValue.setTypeToRuntimeType(): 8 rows, 0 measured, 0 distinct ref, MIXED=8
  ... RealValue.setTypeToRuntimeType()  RealValue.setTypeToRuntimeType(): 1 rows, 0 measured, 0 distinct ref, MIXED=1
  ... SBooleanValue.aleatoryCumulativeBeliefFusion(value)  SBooleanValue.aleatoryCumulativeBeliefFusion(value): 0 rows, 0 measured, 0 distinct ref
  ... SBooleanValue.and(value)  SBooleanValue.and(value): 0 rows, 0 measured, 0 distinct ref
  ... SBooleanValue.applyOn(value)  SBooleanValue.applyOn(value): 0 rows, 0 measured, 0 distinct ref
  ... SBooleanValue.averageBeliefFusion(value)  SBooleanValue.averageBeliefFusion(value): 0 rows, 0 measured, 0 distinct ref
  ... SBooleanValue.averageFusion(value)  SBooleanValue.averageFusion(value): 0 rows, 0 measured, 0 distinct ref
  ... SBooleanValue.baseRate()  SBooleanValue.baseRate(): 0 rows, 0 measured, 0 distinct ref
  ... SBooleanValue.belief()  SBooleanValue.belief(): 0 rows, 0 measured, 0 distinct ref
  ... SBooleanValue.beliefConstraintFusion(value)  SBooleanValue.beliefConstraintFusion(value): 0 rows, 0 measured, 0 distinct ref
  ... SBooleanValue.certainty()  SBooleanValue.certainty(): 0 rows, 0 measured, 0 distinct ref
  ... SBooleanValue.compareTo(value)  SBooleanValue.compareTo(value): 0 rows, 0 measured, 0 distinct ref
  ... SBooleanValue.conjunctiveCertainty(value)  SBooleanValue.conjunctiveCertainty(value): 0 rows, 0 measured, 0 distinct ref
  ... SBooleanValue.consensusAndCompromiseFusion(value)  SBooleanValue.consensusAndCompromiseFusion(value): 0 rows, 0 measured, 0 distinct ref
  ... SBooleanValue.cumulativeFusion(value)  SBooleanValue.cumulativeFusion(value): 0 rows, 0 measured, 0 distinct ref
  ... SBooleanValue.deduceY(value,value)  SBooleanValue.deduceY(value,value): 0 rows, 0 measured, 0 distinct ref
  ... SBooleanValue.degreeOfConflict(value)  SBooleanValue.degreeOfConflict(value): 0 rows, 0 measured, 0 distinct ref
  ... SBooleanValue.disbelief()  SBooleanValue.disbelief(): 0 rows, 0 measured, 0 distinct ref
  ... SBooleanValue.discount(value)  SBooleanValue.discount(value): 0 rows, 0 measured, 0 distinct ref
  ... SBooleanValue.epistemicCumulativeBeliefFusion(value)  SBooleanValue.epistemicCumulativeBeliefFusion(value): 0 rows, 0 measured, 0 distinct ref
  ... SBooleanValue.epistemicCumulativeFusion(value)  SBooleanValue.epistemicCumulativeFusion(value): 0 rows, 0 measured, 0 distinct ref
  ... SBooleanValue.equivalent(value)  SBooleanValue.equivalent(value): 0 rows, 0 measured, 0 distinct ref
  ... SBooleanValue.getRelativeWeight()  SBooleanValue.getRelativeWeight(): 0 rows, 0 measured, 0 distinct ref
  ... SBooleanValue.getRuntimeType()  SBooleanValue.getRuntimeType(): 0 rows, 0 measured, 0 distinct ref
  ... SBooleanValue.hashCode()  SBooleanValue.hashCode(): 0 rows, 0 measured, 0 distinct ref
  ... SBooleanValue.implies(value)  SBooleanValue.implies(value): 0 rows, 0 measured, 0 distinct ref
  ... SBooleanValue.isAbsolute()  SBooleanValue.isAbsolute(): 0 rows, 0 measured, 0 distinct ref
  ... SBooleanValue.isBag()  SBooleanValue.isBag(): 0 rows, 0 measured, 0 distinct ref
  ... SBooleanValue.isBoolean()  SBooleanValue.isBoolean(): 0 rows, 0 measured, 0 distinct ref
  ... SBooleanValue.isCertain(value)  SBooleanValue.isCertain(value): 0 rows, 0 measured, 0 distinct ref
  ... SBooleanValue.isCollection()  SBooleanValue.isCollection(): 0 rows, 0 measured, 0 distinct ref
  ... SBooleanValue.isDefined()  SBooleanValue.isDefined(): 0 rows, 0 measured, 0 distinct ref
  ... SBooleanValue.isDogmatic()  SBooleanValue.isDogmatic(): 0 rows, 0 measured, 0 distinct ref
  ... SBooleanValue.isInteger()  SBooleanValue.isInteger(): 0 rows, 0 measured, 0 distinct ref
  ... SBooleanValue.isLink()  SBooleanValue.isLink(): 0 rows, 0 measured, 0 distinct ref
  ... SBooleanValue.isMaximizedUncertainty()  SBooleanValue.isMaximizedUncertainty(): 0 rows, 0 measured, 0 distinct ref
  ... SBooleanValue.isObject()  SBooleanValue.isObject(): 0 rows, 0 measured, 0 distinct ref
  ... SBooleanValue.isOrderedSet()  SBooleanValue.isOrderedSet(): 0 rows, 0 measured, 0 distinct ref
  ... SBooleanValue.isReal()  SBooleanValue.isReal(): 0 rows, 0 measured, 0 distinct ref
  ... SBooleanValue.isSBoolean()  SBooleanValue.isSBoolean(): 0 rows, 0 measured, 0 distinct ref
  ... SBooleanValue.isSequence()  SBooleanValue.isSequence(): 0 rows, 0 measured, 0 distinct ref
  ... SBooleanValue.isSet()  SBooleanValue.isSet(): 0 rows, 0 measured, 0 distinct ref
  ... SBooleanValue.isUBoolean()  SBooleanValue.isUBoolean(): 0 rows, 0 measured, 0 distinct ref
  ... SBooleanValue.isUInteger()  SBooleanValue.isUInteger(): 0 rows, 0 measured, 0 distinct ref
  ... SBooleanValue.isUReal()  SBooleanValue.isUReal(): 0 rows, 0 measured, 0 distinct ref
  ... SBooleanValue.isUncertain(value)  SBooleanValue.isUncertain(value): 0 rows, 0 measured, 0 distinct ref
  ... SBooleanValue.isUndefined()  SBooleanValue.isUndefined(): 0 rows, 0 measured, 0 distinct ref
  ... SBooleanValue.isUnlimitedNatural()  SBooleanValue.isUnlimitedNatural(): 0 rows, 0 measured, 0 distinct ref
  ... SBooleanValue.isVacuous()  SBooleanValue.isVacuous(): 0 rows, 0 measured, 0 distinct ref
  ... SBooleanValue.majorityBeliefFusion(value)  SBooleanValue.majorityBeliefFusion(value): 0 rows, 0 measured, 0 distinct ref
  ... SBooleanValue.majorityFusion(value)  SBooleanValue.majorityFusion(value): 0 rows, 0 measured, 0 distinct ref
  ... SBooleanValue.max(value)  SBooleanValue.max(value): 0 rows, 0 measured, 0 distinct ref
  ... SBooleanValue.min(value)  SBooleanValue.min(value): 0 rows, 0 measured, 0 distinct ref
  ... SBooleanValue.minimumBeliefFusion(value)  SBooleanValue.minimumBeliefFusion(value): 0 rows, 0 measured, 0 distinct ref
  ... SBooleanValue.minimumFusion(value)  SBooleanValue.minimumFusion(value): 0 rows, 0 measured, 0 distinct ref
  ... SBooleanValue.not()  SBooleanValue.not(): 0 rows, 0 measured, 0 distinct ref
  ... SBooleanValue.or(value)  SBooleanValue.or(value): 0 rows, 0 measured, 0 distinct ref
  ... SBooleanValue.projection()  SBooleanValue.projection(): 0 rows, 0 measured, 0 distinct ref
  ... SBooleanValue.projectiveDistance(value)  SBooleanValue.projectiveDistance(value): 0 rows, 0 measured, 0 distinct ref
  ... SBooleanValue.setTypeToRuntimeType()  SBooleanValue.setTypeToRuntimeType(): 0 rows, 0 measured, 0 distinct ref
  ... SBooleanValue.toString()  SBooleanValue.toString(): 0 rows, 0 measured, 0 distinct ref
  ... SBooleanValue.toStringWithType()  SBooleanValue.toStringWithType(): 0 rows, 0 measured, 0 distinct ref
  ... SBooleanValue.toUBoolean()  SBooleanValue.toUBoolean(): 0 rows, 0 measured, 0 distinct ref
  ... SBooleanValue.type()  SBooleanValue.type(): 0 rows, 0 measured, 0 distinct ref
  ... SBooleanValue.uDistinct(value)  SBooleanValue.uDistinct(value): 0 rows, 0 measured, 0 distinct ref
  ... SBooleanValue.uEquals(value)  SBooleanValue.uEquals(value): 0 rows, 0 measured, 0 distinct ref
  ... SBooleanValue.uncertainOpinion()  SBooleanValue.uncertainOpinion(): 0 rows, 0 measured, 0 distinct ref
  ... SBooleanValue.uncertainty()  SBooleanValue.uncertainty(): 0 rows, 0 measured, 0 distinct ref
  ... SBooleanValue.uncertaintyMaximized()  SBooleanValue.uncertaintyMaximized(): 0 rows, 0 measured, 0 distinct ref
  ... SBooleanValue.weightedBeliefFusion(value)  SBooleanValue.weightedBeliefFusion(value): 0 rows, 0 measured, 0 distinct ref
  ... SBooleanValue.weightedFusion(value)  SBooleanValue.weightedFusion(value): 0 rows, 0 measured, 0 distinct ref
  ... SBooleanValue.xor(value)  SBooleanValue.xor(value): 0 rows, 0 measured, 0 distinct ref
  ... StringValue.setTypeToRuntimeType()  StringValue.setTypeToRuntimeType(): 15 rows, 0 measured, 0 distinct ref, MIXED=15
  ... UBooleanValue.setTypeToRuntimeType()  UBooleanValue.setTypeToRuntimeType(): 11 rows, 0 measured, 0 distinct ref, MIXED=9, HARNESS_ERROR=2
  ... UIntegerValue.power(value)  UIntegerValue.power(value): 225 rows, 0 measured, 0 distinct ref, BOTH_THREW=225
  ... UIntegerValue.setTypeToRuntimeType()  UIntegerValue.setTypeToRuntimeType(): 15 rows, 0 measured, 0 distinct ref, MIXED=15
  ... URealValue.setTypeToRuntimeType()  URealValue.setTypeToRuntimeType(): 24 rows, 0 measured, 0 distinct ref, MIXED=24
  ... UStringValue.setTypeToRuntimeType()  UStringValue.setTypeToRuntimeType(): 28 rows, 0 measured, 0 distinct ref, MIXED=27, HARNESS_ERROR=1
--- first 20 clean-but-degenerate operations ------------------
  --- BooleanValue.getRuntimeType(): 2 rows, 2 measured, 2 agreed, 0 disagreed, 0 intended departure(s), 0 java-type mismatch(es) (subject token OBSERVED on 0, ASSUMED on 0), 1 distinct reference value(s) [NOT DISCRIMINATING: always OPAQUE("org.tzi.use.uml.ocl.type.BooleanType|BooleanType{BasicType.fTypename=\"Boolean\"}")@BooleanType]
  --- BooleanValue.isBag(): 2 rows, 2 measured, 2 agreed, 0 disagreed, 0 intended departure(s), 0 java-type mismatch(es) (subject token OBSERVED on 0, ASSUMED on 0), 1 distinct reference value(s) [NOT DISCRIMINATING: always BOOLEAN(false)@Boolean]
  --- BooleanValue.isBoolean(): 2 rows, 2 measured, 2 agreed, 0 disagreed, 0 intended departure(s), 0 java-type mismatch(es) (subject token OBSERVED on 0, ASSUMED on 0), 1 distinct reference value(s) [NOT DISCRIMINATING: always BOOLEAN(true)@Boolean]
  --- BooleanValue.isCollection(): 2 rows, 2 measured, 2 agreed, 0 disagreed, 0 intended departure(s), 0 java-type mismatch(es) (subject token OBSERVED on 0, ASSUMED on 0), 1 distinct reference value(s) [NOT DISCRIMINATING: always BOOLEAN(false)@Boolean]
  --- BooleanValue.isDefined(): 2 rows, 2 measured, 2 agreed, 0 disagreed, 0 intended departure(s), 0 java-type mismatch(es) (subject token OBSERVED on 0, ASSUMED on 0), 1 distinct reference value(s) [NOT DISCRIMINATING: always BOOLEAN(true)@Boolean]
  --- BooleanValue.isInteger(): 2 rows, 2 measured, 2 agreed, 0 disagreed, 0 intended departure(s), 0 java-type mismatch(es) (subject token OBSERVED on 0, ASSUMED on 0), 1 distinct reference value(s) [NOT DISCRIMINATING: always BOOLEAN(false)@Boolean]
  --- BooleanValue.isLink(): 2 rows, 2 measured, 2 agreed, 0 disagreed, 0 intended departure(s), 0 java-type mismatch(es) (subject token OBSERVED on 0, ASSUMED on 0), 1 distinct reference value(s) [NOT DISCRIMINATING: always BOOLEAN(false)@Boolean]
  --- BooleanValue.isObject(): 2 rows, 2 measured, 2 agreed, 0 disagreed, 0 intended departure(s), 0 java-type mismatch(es) (subject token OBSERVED on 0, ASSUMED on 0), 1 distinct reference value(s) [NOT DISCRIMINATING: always BOOLEAN(false)@Boolean]
  --- BooleanValue.isOrderedSet(): 2 rows, 2 measured, 2 agreed, 0 disagreed, 0 intended departure(s), 0 java-type mismatch(es) (subject token OBSERVED on 0, ASSUMED on 0), 1 distinct reference value(s) [NOT DISCRIMINATING: always BOOLEAN(false)@Boolean]
  --- BooleanValue.isReal(): 2 rows, 2 measured, 2 agreed, 0 disagreed, 0 intended departure(s), 0 java-type mismatch(es) (subject token OBSERVED on 0, ASSUMED on 0), 1 distinct reference value(s) [NOT DISCRIMINATING: always BOOLEAN(false)@Boolean]
  --- BooleanValue.isSBoolean(): 2 rows, 2 measured, 2 agreed, 0 disagreed, 0 intended departure(s), 0 java-type mismatch(es) (subject token OBSERVED on 0, ASSUMED on 0), 1 distinct reference value(s) [NOT DISCRIMINATING: always BOOLEAN(false)@Boolean]
  --- BooleanValue.isSequence(): 2 rows, 2 measured, 2 agreed, 0 disagreed, 0 intended departure(s), 0 java-type mismatch(es) (subject token OBSERVED on 0, ASSUMED on 0), 1 distinct reference value(s) [NOT DISCRIMINATING: always BOOLEAN(false)@Boolean]
  --- BooleanValue.isSet(): 2 rows, 2 measured, 2 agreed, 0 disagreed, 0 intended departure(s), 0 java-type mismatch(es) (subject token OBSERVED on 0, ASSUMED on 0), 1 distinct reference value(s) [NOT DISCRIMINATING: always BOOLEAN(false)@Boolean]
  --- BooleanValue.isUBoolean(): 2 rows, 2 measured, 2 agreed, 0 disagreed, 0 intended departure(s), 0 java-type mismatch(es) (subject token OBSERVED on 0, ASSUMED on 0), 1 distinct reference value(s) [NOT DISCRIMINATING: always BOOLEAN(false)@Boolean]
  --- BooleanValue.isUInteger(): 2 rows, 2 measured, 2 agreed, 0 disagreed, 0 intended departure(s), 0 java-type mismatch(es) (subject token OBSERVED on 0, ASSUMED on 0), 1 distinct reference value(s) [NOT DISCRIMINATING: always BOOLEAN(false)@Boolean]
  --- BooleanValue.isUReal(): 2 rows, 2 measured, 2 agreed, 0 disagreed, 0 intended departure(s), 0 java-type mismatch(es) (subject token OBSERVED on 0, ASSUMED on 0), 1 distinct reference value(s) [NOT DISCRIMINATING: always BOOLEAN(false)@Boolean]
  --- BooleanValue.isUndefined(): 2 rows, 2 measured, 2 agreed, 0 disagreed, 0 intended departure(s), 0 java-type mismatch(es) (subject token OBSERVED on 0, ASSUMED on 0), 1 distinct reference value(s) [NOT DISCRIMINATING: always BOOLEAN(false)@Boolean]
  --- BooleanValue.isUnlimitedNatural(): 2 rows, 2 measured, 2 agreed, 0 disagreed, 0 intended departure(s), 0 java-type mismatch(es) (subject token OBSERVED on 0, ASSUMED on 0), 1 distinct reference value(s) [NOT DISCRIMINATING: always BOOLEAN(false)@Boolean]
  --- BooleanValue.type(): 2 rows, 2 measured, 2 agreed, 0 disagreed, 0 intended departure(s), 0 java-type mismatch(es) (subject token OBSERVED on 0, ASSUMED on 0), 1 distinct reference value(s) [NOT DISCRIMINATING: always OPAQUE("org.tzi.use.uml.ocl.type.BooleanType|BooleanType{BasicType.fTypename=\"Boolean\"}")@BooleanType]
  --- IntegerValue.getRuntimeType(): 8 rows, 8 measured, 8 agreed, 0 disagreed, 0 intended departure(s), 0 java-type mismatch(es) (subject token OBSERVED on 0, ASSUMED on 0), 1 distinct reference value(s) [NOT DISCRIMINATING: always OPAQUE("org.tzi.use.uml.ocl.type.IntegerType|IntegerType{BasicType.fTypename=\"Integer\"}")@IntegerType]
===================================================================
CONTROL, faithful port     URealValue.add(value): 576 rows, 576 measured, 576 agreed, 0 disagreed, 0 intended departure(s), 0 java-type mismatch(es) (subject token OBSERVED on 0, ASSUMED on 0), 164 distinct reference value(s) [DISCRIMINATING]
=== unwritten-port invariant: a-throws =================
seed                 20260817
subject              a-throws  (every method body: throw new RuntimeException("TODO: port " + op.key()))
observability        NOTHING
javaTypeMismatch     0  <- agreement rows on which only the Java class differed (D-43)
operations           355  (enumerated from use.jar + atenearesearchgroup.uncertainty.jar)
corpora              uReal=24, uInteger=15, uBoolean=11, uString=28, boolean=4, string=16, zeroDivisors=7, indexBoundaries=8; receivers=104
rows                 1294072
measured rows        0  (AGREE + DIFFER)
agreement rows       0
verdict tally        {BOTH_THREW=50598, HARNESS_ERROR=1163146, MIXED=80328}
codomain census      355 operations: 355 measured nothing, 0 single-valued (NOT DISCRIMINATING), 0 discriminating
fully agreed ops, DISCRIMINATING (a finding about the subject)  (none)
fully agreed ops, NOT DISCRIMINATING (a finding about the corpus)  (none)   [ASSERTED against reviewedDegenerateFullyAgreed since the D-35 fix]
===================================================================
=== unwritten-port invariant: b-returns-java-null =================
seed                 20260817
subject              b-returns-java-null  (every method body: return null)
observability        NOTHING
javaTypeMismatch     0  <- agreement rows on which only the Java class differed (D-43)
operations           355  (enumerated from use.jar + atenearesearchgroup.uncertainty.jar)
corpora              uReal=24, uInteger=15, uBoolean=11, uString=28, boolean=4, string=16, zeroDivisors=7, indexBoundaries=8; receivers=104
rows                 1294072
measured rows        0  (AGREE + DIFFER)
agreement rows       0
verdict tally        {HARNESS_ERROR=1294072}
codomain census      355 operations: 355 measured nothing, 0 single-valued (NOT DISCRIMINATING), 0 discriminating
fully agreed ops, DISCRIMINATING (a finding about the subject)  (none)
fully agreed ops, NOT DISCRIMINATING (a finding about the corpus)  (none)   [ASSERTED against reviewedDegenerateFullyAgreed since the D-35 fix]
===================================================================
=== unwritten-port invariant: c-empty-body =================
seed                 20260817
subject              c-empty-body  (every method body: { } -- i.e. return UValue.voidValue())
observability        NOTHING
javaTypeMismatch     0  <- agreement rows on which only the Java class differed (D-43)
operations           355  (enumerated from use.jar + atenearesearchgroup.uncertainty.jar)
corpora              uReal=24, uInteger=15, uBoolean=11, uString=28, boolean=4, string=16, zeroDivisors=7, indexBoundaries=8; receivers=104
rows                 1294072
measured rows        79520  (AGREE + DIFFER)
agreement rows       0
verdict tally        {DIFFER=79520, HARNESS_ERROR=1163146, MIXED=50598, UNMEASURABLE=808}
codomain census      355 operations: 79 measured nothing, 157 single-valued (NOT DISCRIMINATING), 119 discriminating
fully agreed ops, DISCRIMINATING (a finding about the subject)  (none)
fully agreed ops, NOT DISCRIMINATING (a finding about the corpus)  (none)   [ASSERTED against reviewedDegenerateFullyAgreed since the D-35 fix]
===================================================================
=== unwritten-port invariant: d-returns-null-value =================
seed                 20260817
subject              d-returns-null-value  (every method body: return UValue.nullValue())
observability        NOTHING
javaTypeMismatch     0  <- agreement rows on which only the Java class differed (D-43)
operations           355  (enumerated from use.jar + atenearesearchgroup.uncertainty.jar)
corpora              uReal=24, uInteger=15, uBoolean=11, uString=28, boolean=4, string=16, zeroDivisors=7, indexBoundaries=8; receivers=104
rows                 1294072
measured rows        79520  (AGREE + DIFFER)
agreement rows       0
verdict tally        {DIFFER=79520, HARNESS_ERROR=1163146, MIXED=50598, UNMEASURABLE=808}
codomain census      355 operations: 79 measured nothing, 157 single-valued (NOT DISCRIMINATING), 119 discriminating
fully agreed ops, DISCRIMINATING (a finding about the subject)  (none)
fully agreed ops, NOT DISCRIMINATING (a finding about the corpus)  (none)   [ASSERTED against reviewedDegenerateFullyAgreed since the D-35 fix]
===================================================================
=== unwritten-port invariant: e-fixed-constant =================
seed                 20260817
subject              e-fixed-constant  (every method body: return UValue.uBoolean(true, 1.0))
observability        WRONG_VALUES
javaTypeMismatch     0  <- agreement rows on which only the Java class differed (D-43)
operations           355  (enumerated from use.jar + atenearesearchgroup.uncertainty.jar)
corpora              uReal=24, uInteger=15, uBoolean=11, uString=28, boolean=4, string=16, zeroDivisors=7, indexBoundaries=8; receivers=104
rows                 1294072
measured rows        80328  (AGREE + DIFFER)
agreement rows       9768
verdict tally        {AGREE=9768, DIFFER=70560, HARNESS_ERROR=1163146, MIXED=50598}
codomain census      355 operations: 71 measured nothing, 165 single-valued (NOT DISCRIMINATING), 119 discriminating
fully agreed ops, DISCRIMINATING (a finding about the subject)  (none)
fully agreed ops, NOT DISCRIMINATING (a finding about the corpus)  (none)   [ASSERTED against reviewedDegenerateFullyAgreed since the D-35 fix]
--- per-operation agreement tally (agreed/driven/rows) ------------
  10/990/11752	UBooleanValue.and(value)
  22/990/11752	UBooleanValue.equivalent(value)
  43/990/11752	UBooleanValue.implies(value)
  16/72/832	UBooleanValue.not()
  54/990/11752	UBooleanValue.or(value)
  889/990/11752	UBooleanValue.uDistinct(value)
  22/990/11752	UBooleanValue.uEquals(value)
  16/990/11752	UBooleanValue.xor(value)
  142/1650/11752	UIntegerValue.ge(value)
  106/1650/11752	UIntegerValue.gt(value)
  125/1650/11752	UIntegerValue.le(value)
  91/1650/11752	UIntegerValue.lt(value)
  1470/1650/11752	UIntegerValue.uDistinct(value)
  38/1650/11752	UIntegerValue.uEquals(value)
  388/2640/11752	URealValue.ge(value)
  318/2640/11752	URealValue.gt(value)
  474/2640/11752	URealValue.le(value)
  403/2640/11752	URealValue.lt(value)
  2413/2640/11752	URealValue.uDistinct(value)
  53/2640/11752	URealValue.uEquals(value)
  128/2970/11752	UStringValue.ge(value)
  129/2970/11752	UStringValue.gt(value)
  108/2970/11752	UStringValue.le(value)
  109/2970/11752	UStringValue.lt(value)
  24/216/832	UStringValue.toUBoolean()
  1915/2970/11752	UStringValue.uDistinct(value)
  131/2970/11752	UStringValue.uEquals(value)
  131/2970/11752	UStringValue.uEqualsIgnoreCase(value)
--- first 20 agreement rows -----------------------------------
index	operation	inputs	historical	ported	verdict	note
[corpus uBoolean] 453	UBooleanValue.and(value)	UBOOLEAN(true,1.0)@UBooleanValue | UBOOLEAN(true,1.0)@UBooleanValue	UBOOLEAN(true,1.0)@UBooleanValue	UBOOLEAN(true,1.0)@UBooleanValue	AGREE	
[corpus uBoolean] 454	UBooleanValue.and(value)	UBOOLEAN(true,1.0)@UBooleanValue | UBOOLEAN(false,0.0)@UBooleanValue	UBOOLEAN(true,1.0)@UBooleanValue	UBOOLEAN(true,1.0)@UBooleanValue	AGREE	
[corpus uBoolean] 464	UBooleanValue.and(value)	UBOOLEAN(false,0.0)@UBooleanValue | UBOOLEAN(true,1.0)@UBooleanValue	UBOOLEAN(true,1.0)@UBooleanValue	UBOOLEAN(true,1.0)@UBooleanValue	AGREE	
[corpus uBoolean] 465	UBooleanValue.and(value)	UBOOLEAN(false,0.0)@UBooleanValue | UBOOLEAN(false,0.0)@UBooleanValue	UBOOLEAN(true,1.0)@UBooleanValue	UBOOLEAN(true,1.0)@UBooleanValue	AGREE	
[corpus boolean] 164	UBooleanValue.and(value)	UBOOLEAN(true,1.0)@UBooleanValue | BOOLEAN(true)@BooleanValue	UBOOLEAN(true,1.0)@UBooleanValue	UBOOLEAN(true,1.0)@UBooleanValue	AGREE	
[corpus boolean] 166	UBooleanValue.and(value)	UBOOLEAN(true,1.0)@UBooleanValue | BOOLEAN(true)@BooleanValue	UBOOLEAN(true,1.0)@UBooleanValue	UBOOLEAN(true,1.0)@UBooleanValue	AGREE	
[corpus boolean] 167	UBooleanValue.and(value)	UBOOLEAN(true,1.0)@UBooleanValue | BOOLEAN(true)@BooleanValue	UBOOLEAN(true,1.0)@UBooleanValue	UBOOLEAN(true,1.0)@UBooleanValue	AGREE	
[corpus boolean] 168	UBooleanValue.and(value)	UBOOLEAN(false,0.0)@UBooleanValue | BOOLEAN(true)@BooleanValue	UBOOLEAN(true,1.0)@UBooleanValue	UBOOLEAN(true,1.0)@UBooleanValue	AGREE	
[corpus boolean] 170	UBooleanValue.and(value)	UBOOLEAN(false,0.0)@UBooleanValue | BOOLEAN(true)@BooleanValue	UBOOLEAN(true,1.0)@UBooleanValue	UBOOLEAN(true,1.0)@UBooleanValue	AGREE	
[corpus boolean] 171	UBooleanValue.and(value)	UBOOLEAN(false,0.0)@UBooleanValue | BOOLEAN(true)@BooleanValue	UBOOLEAN(true,1.0)@UBooleanValue	UBOOLEAN(true,1.0)@UBooleanValue	AGREE	
[corpus uBoolean] 429	UBooleanValue.equivalent(value)	UBOOLEAN(true,0.0)@UBooleanValue | UBOOLEAN(true,0.0)@UBooleanValue	UBOOLEAN(true,1.0)@UBooleanValue	UBOOLEAN(true,1.0)@UBooleanValue	AGREE	
[corpus uBoolean] 434	UBooleanValue.equivalent(value)	UBOOLEAN(true,0.0)@UBooleanValue | UBOOLEAN(false,1.0)@UBooleanValue	UBOOLEAN(true,1.0)@UBooleanValue	UBOOLEAN(true,1.0)@UBooleanValue	AGREE	
[corpus uBoolean] 441	UBooleanValue.equivalent(value)	UBOOLEAN(true,0.5)@UBooleanValue | UBOOLEAN(true,0.5)@UBooleanValue	UBOOLEAN(true,1.0)@UBooleanValue	UBOOLEAN(true,1.0)@UBooleanValue	AGREE	
[corpus uBoolean] 444	UBooleanValue.equivalent(value)	UBOOLEAN(true,0.5)@UBooleanValue | UBOOLEAN(false,0.5)@UBooleanValue	UBOOLEAN(true,1.0)@UBooleanValue	UBOOLEAN(true,1.0)@UBooleanValue	AGREE	
[corpus uBoolean] 453	UBooleanValue.equivalent(value)	UBOOLEAN(true,1.0)@UBooleanValue | UBOOLEAN(true,1.0)@UBooleanValue	UBOOLEAN(true,1.0)@UBooleanValue	UBOOLEAN(true,1.0)@UBooleanValue	AGREE	
[corpus uBoolean] 454	UBooleanValue.equivalent(value)	UBOOLEAN(true,1.0)@UBooleanValue | UBOOLEAN(false,0.0)@UBooleanValue	UBOOLEAN(true,1.0)@UBooleanValue	UBOOLEAN(true,1.0)@UBooleanValue	AGREE	
[corpus uBoolean] 464	UBooleanValue.equivalent(value)	UBOOLEAN(false,0.0)@UBooleanValue | UBOOLEAN(true,1.0)@UBooleanValue	UBOOLEAN(true,1.0)@UBooleanValue	UBOOLEAN(true,1.0)@UBooleanValue	AGREE	
[corpus uBoolean] 465	UBooleanValue.equivalent(value)	UBOOLEAN(false,0.0)@UBooleanValue | UBOOLEAN(false,0.0)@UBooleanValue	UBOOLEAN(true,1.0)@UBooleanValue	UBOOLEAN(true,1.0)@UBooleanValue	AGREE	
[corpus uBoolean] 474	UBooleanValue.equivalent(value)	UBOOLEAN(false,0.5)@UBooleanValue | UBOOLEAN(true,0.5)@UBooleanValue	UBOOLEAN(true,1.0)@UBooleanValue	UBOOLEAN(true,1.0)@UBooleanValue	AGREE	
[corpus uBoolean] 477	UBooleanValue.equivalent(value)	UBOOLEAN(false,0.5)@UBooleanValue | UBOOLEAN(false,0.5)@UBooleanValue	UBOOLEAN(true,1.0)@UBooleanValue	UBOOLEAN(true,1.0)@UBooleanValue	AGREE	
===================================================================
=== unwritten-port invariant: f-echoes-receiver =================
seed                 20260817
subject              f-echoes-receiver  (every method body: return args.get(0))
observability        WRONG_VALUES
javaTypeMismatch     384  <- agreement rows on which only the Java class differed (D-43)
operations           355  (enumerated from use.jar + atenearesearchgroup.uncertainty.jar)
corpora              uReal=24, uInteger=15, uBoolean=11, uString=28, boolean=4, string=16, zeroDivisors=7, indexBoundaries=8; receivers=104
rows                 1294072
measured rows        80328  (AGREE + DIFFER)
agreement rows       5143
verdict tally        {AGREE=5143, DIFFER=75185, HARNESS_ERROR=1163146, MIXED=50598}
codomain census      355 operations: 71 measured nothing, 165 single-valued (NOT DISCRIMINATING), 119 discriminating
fully agreed ops, DISCRIMINATING (a finding about the subject)  
  *** BooleanValue.isTrue()  (16/16 driven rows agreed, 16 of them on the payload only (java type mismatch), 832 rows total, 2 distinct reference value(s); reviewed and signed off)
  *** BooleanValue.value()  (16/16 driven rows agreed, 16 of them on the payload only (java type mismatch), 832 rows total, 2 distinct reference value(s); reviewed and signed off)
  *** IntegerValue.value()  (64/64 driven rows agreed, 64 of them on the payload only (java type mismatch), 832 rows total, 8 distinct reference value(s); reviewed and signed off)
  *** StringValue.value()  (120/120 driven rows agreed, 120 of them on the payload only (java type mismatch), 832 rows total, 15 distinct reference value(s); reviewed and signed off)
fully agreed ops, NOT DISCRIMINATING (a finding about the corpus)  1 operations   [ASSERTED against reviewedDegenerateFullyAgreed since the D-35 fix]
  --- RealValue.value()  (8/8 driven rows agreed, 8 of them on the payload only (java type mismatch), 832 rows total, 1 distinct reference value(s) -- always REAL(0.0)@Double [NOT DISCRIMINATING]; reviewed and signed off)
--- per-operation agreement tally (agreed/driven/rows) ------------
  8/16/832	BooleanValue.isBag()
  8/16/832	BooleanValue.isBoolean()
  8/16/832	BooleanValue.isCollection()
  8/16/832	BooleanValue.isDefined()
  8/16/832	BooleanValue.isInteger()
  8/16/832	BooleanValue.isLink()
  8/16/832	BooleanValue.isObject()
  8/16/832	BooleanValue.isOrderedSet()
  8/16/832	BooleanValue.isReal()
  8/16/832	BooleanValue.isSBoolean()
  8/16/832	BooleanValue.isSequence()
  8/16/832	BooleanValue.isSet()
  16/16/832	BooleanValue.isTrue()
  8/16/832	BooleanValue.isUBoolean()
  8/16/832	BooleanValue.isUInteger()
  8/16/832	BooleanValue.isUReal()
  8/16/832	BooleanValue.isUndefined()
  8/16/832	BooleanValue.isUnlimitedNatural()
  16/16/832	BooleanValue.value()
  16/880/11752	IntegerValue.compareTo(value)
  8/64/832	IntegerValue.hashCode()
  64/64/832	IntegerValue.value()
  8/8/832	RealValue.value()
  120/120/832	StringValue.value()
  40/990/11752	UBooleanValue.and(value)
  31/990/11752	UBooleanValue.equivalent(value)
  24/990/11752	UBooleanValue.implies(value)
  16/72/832	UBooleanValue.not()
  34/990/11752	UBooleanValue.or(value)
  124/990/11752	UBooleanValue.uDistinct(value)
  128/990/11752	UBooleanValue.uEquals(value)
  30/990/11752	UBooleanValue.xor(value)
  88/120/832	UIntegerValue.abs()
  58/1650/11752	UIntegerValue.add(value)
  47/1650/11752	UIntegerValue.divideBy(value)
  40/120/832	UIntegerValue.inverse()
  58/1650/11752	UIntegerValue.minus(value)
  46/1650/11752	UIntegerValue.mod(value)
  72/1650/11752	UIntegerValue.mult(value)
  24/120/832	UIntegerValue.neg()
  40/1650/11752	UIntegerValue.power(value)
  24/120/832	UIntegerValue.sqrt()
  112/192/832	URealValue.abs()
  308/2640/11752	URealValue.add(value)
  32/192/832	URealValue.asin()
  32/192/832	URealValue.atan()
  138/2640/11752	URealValue.divideBy(value)
  144/192/832	URealValue.floor()
  56/192/832	URealValue.inverse()
  718/2640/11752	URealValue.max(value)
  777/2640/11752	URealValue.min(value)
  313/2640/11752	URealValue.minus(value)
  132/2640/11752	URealValue.mult(value)
  8/192/832	URealValue.neg()
  378/1296/11752	URealValue.power(float)
  96/192/832	URealValue.round()
  32/192/832	URealValue.sin()
  40/192/832	URealValue.sqrt()
  32/192/832	URealValue.tan()
  60/1458/11752	UStringValue.uAt(int)
  75/2970/11752	UStringValue.uConcat(value)
  72/4374/35256	UStringValue.uSubstring(int,int)
  176/216/832	UStringValue.uToLowerCase()
  104/216/832	UStringValue.uToUpperCase()
--- first 20 agreement rows -----------------------------------
index	operation	inputs	historical	ported	verdict	note
[corpus uReal] 79	BooleanValue.isBag()	BOOLEAN(false)@BooleanValue	BOOLEAN(false)@Boolean	BOOLEAN(false)@BooleanValue	AGREE	java type mismatch: reference returned java.lang.Boolean (BOOLEAN(false)@Boolean) / subject returned org.tzi.use.uml.ocl.value.BooleanValue (BOOLEAN(false)@BooleanValue); the content is IDENTICAL -- right content, wrong Java type (defect D-18). This row is scored AGREE and counted in rows.javaTypeMismatch, not scored as a divergence: at S1 the ported side's class cannot be authentically observed, because no ported value class exists to observe, so a type-only difference measures the adapter and not the port (D-43). Provenance: reference OBSERVED, subject ASSUMED (OBSERVED = read off the object that side returned; ASSUMED = the factory default for the kind, which is wrong for 182 of 285 operations). The subject's adapter never looked at what its implementation returned, so this difference is a finding about the ADAPTER and not about the port (D-43); an adapter must attribute through UValue.observedFrom(Object).
[corpus uInteger] 79	BooleanValue.isBag()	BOOLEAN(false)@BooleanValue	BOOLEAN(false)@Boolean	BOOLEAN(false)@BooleanValue	AGREE	java type mismatch: reference returned java.lang.Boolean (BOOLEAN(false)@Boolean) / subject returned org.tzi.use.uml.ocl.value.BooleanValue (BOOLEAN(false)@BooleanValue); the content is IDENTICAL -- right content, wrong Java type (defect D-18). This row is scored AGREE and counted in rows.javaTypeMismatch, not scored as a divergence: at S1 the ported side's class cannot be authentically observed, because no ported value class exists to observe, so a type-only difference measures the adapter and not the port (D-43). Provenance: reference OBSERVED, subject ASSUMED (OBSERVED = read off the object that side returned; ASSUMED = the factory default for the kind, which is wrong for 182 of 285 operations). The subject's adapter never looked at what its implementation returned, so this difference is a finding about the ADAPTER and not about the port (D-43); an adapter must attribute through UValue.observedFrom(Object).
[corpus uBoolean] 79	BooleanValue.isBag()	BOOLEAN(false)@BooleanValue	BOOLEAN(false)@Boolean	BOOLEAN(false)@BooleanValue	AGREE	java type mismatch: reference returned java.lang.Boolean (BOOLEAN(false)@Boolean) / subject returned org.tzi.use.uml.ocl.value.BooleanValue (BOOLEAN(false)@BooleanValue); the content is IDENTICAL -- right content, wrong Java type (defect D-18). This row is scored AGREE and counted in rows.javaTypeMismatch, not scored as a divergence: at S1 the ported side's class cannot be authentically observed, because no ported value class exists to observe, so a type-only difference measures the adapter and not the port (D-43). Provenance: reference OBSERVED, subject ASSUMED (OBSERVED = read off the object that side returned; ASSUMED = the factory default for the kind, which is wrong for 182 of 285 operations). The subject's adapter never looked at what its implementation returned, so this difference is a finding about the ADAPTER and not about the port (D-43); an adapter must attribute through UValue.observedFrom(Object).
[corpus uString] 79	BooleanValue.isBag()	BOOLEAN(false)@BooleanValue	BOOLEAN(false)@Boolean	BOOLEAN(false)@BooleanValue	AGREE	java type mismatch: reference returned java.lang.Boolean (BOOLEAN(false)@Boolean) / subject returned org.tzi.use.uml.ocl.value.BooleanValue (BOOLEAN(false)@BooleanValue); the content is IDENTICAL -- right content, wrong Java type (defect D-18). This row is scored AGREE and counted in rows.javaTypeMismatch, not scored as a divergence: at S1 the ported side's class cannot be authentically observed, because no ported value class exists to observe, so a type-only difference measures the adapter and not the port (D-43). Provenance: reference OBSERVED, subject ASSUMED (OBSERVED = read off the object that side returned; ASSUMED = the factory default for the kind, which is wrong for 182 of 285 operations). The subject's adapter never looked at what its implementation returned, so this difference is a finding about the ADAPTER and not about the port (D-43); an adapter must attribute through UValue.observedFrom(Object).
[corpus boolean] 79	BooleanValue.isBag()	BOOLEAN(false)@BooleanValue	BOOLEAN(false)@Boolean	BOOLEAN(false)@BooleanValue	AGREE	java type mismatch: reference returned java.lang.Boolean (BOOLEAN(false)@Boolean) / subject returned org.tzi.use.uml.ocl.value.BooleanValue (BOOLEAN(false)@BooleanValue); the content is IDENTICAL -- right content, wrong Java type (defect D-18). This row is scored AGREE and counted in rows.javaTypeMismatch, not scored as a divergence: at S1 the ported side's class cannot be authentically observed, because no ported value class exists to observe, so a type-only difference measures the adapter and not the port (D-43). Provenance: reference OBSERVED, subject ASSUMED (OBSERVED = read off the object that side returned; ASSUMED = the factory default for the kind, which is wrong for 182 of 285 operations). The subject's adapter never looked at what its implementation returned, so this difference is a finding about the ADAPTER and not about the port (D-43); an adapter must attribute through UValue.observedFrom(Object).
[corpus string] 79	BooleanValue.isBag()	BOOLEAN(false)@BooleanValue	BOOLEAN(false)@Boolean	BOOLEAN(false)@BooleanValue	AGREE	java type mismatch: reference returned java.lang.Boolean (BOOLEAN(false)@Boolean) / subject returned org.tzi.use.uml.ocl.value.BooleanValue (BOOLEAN(false)@BooleanValue); the content is IDENTICAL -- right content, wrong Java type (defect D-18). This row is scored AGREE and counted in rows.javaTypeMismatch, not scored as a divergence: at S1 the ported side's class cannot be authentically observed, because no ported value class exists to observe, so a type-only difference measures the adapter and not the port (D-43). Provenance: reference OBSERVED, subject ASSUMED (OBSERVED = read off the object that side returned; ASSUMED = the factory default for the kind, which is wrong for 182 of 285 operations). The subject's adapter never looked at what its implementation returned, so this difference is a finding about the ADAPTER and not about the port (D-43); an adapter must attribute through UValue.observedFrom(Object).
[corpus zeroDivisors] 79	BooleanValue.isBag()	BOOLEAN(false)@BooleanValue	BOOLEAN(false)@Boolean	BOOLEAN(false)@BooleanValue	AGREE	java type mismatch: reference returned java.lang.Boolean (BOOLEAN(false)@Boolean) / subject returned org.tzi.use.uml.ocl.value.BooleanValue (BOOLEAN(false)@BooleanValue); the content is IDENTICAL -- right content, wrong Java type (defect D-18). This row is scored AGREE and counted in rows.javaTypeMismatch, not scored as a divergence: at S1 the ported side's class cannot be authentically observed, because no ported value class exists to observe, so a type-only difference measures the adapter and not the port (D-43). Provenance: reference OBSERVED, subject ASSUMED (OBSERVED = read off the object that side returned; ASSUMED = the factory default for the kind, which is wrong for 182 of 285 operations). The subject's adapter never looked at what its implementation returned, so this difference is a finding about the ADAPTER and not about the port (D-43); an adapter must attribute through UValue.observedFrom(Object).
[corpus indexBoundaries] 79	BooleanValue.isBag()	BOOLEAN(false)@BooleanValue	BOOLEAN(false)@Boolean	BOOLEAN(false)@BooleanValue	AGREE	java type mismatch: reference returned java.lang.Boolean (BOOLEAN(false)@Boolean) / subject returned org.tzi.use.uml.ocl.value.BooleanValue (BOOLEAN(false)@BooleanValue); the content is IDENTICAL -- right content, wrong Java type (defect D-18). This row is scored AGREE and counted in rows.javaTypeMismatch, not scored as a divergence: at S1 the ported side's class cannot be authentically observed, because no ported value class exists to observe, so a type-only difference measures the adapter and not the port (D-43). Provenance: reference OBSERVED, subject ASSUMED (OBSERVED = read off the object that side returned; ASSUMED = the factory default for the kind, which is wrong for 182 of 285 operations). The subject's adapter never looked at what its implementation returned, so this difference is a finding about the ADAPTER and not about the port (D-43); an adapter must attribute through UValue.observedFrom(Object).
[corpus uReal] 78	BooleanValue.isBoolean()	BOOLEAN(true)@BooleanValue	BOOLEAN(true)@Boolean	BOOLEAN(true)@BooleanValue	AGREE	java type mismatch: reference returned java.lang.Boolean (BOOLEAN(true)@Boolean) / subject returned org.tzi.use.uml.ocl.value.BooleanValue (BOOLEAN(true)@BooleanValue); the content is IDENTICAL -- right content, wrong Java type (defect D-18). This row is scored AGREE and counted in rows.javaTypeMismatch, not scored as a divergence: at S1 the ported side's class cannot be authentically observed, because no ported value class exists to observe, so a type-only difference measures the adapter and not the port (D-43). Provenance: reference OBSERVED, subject ASSUMED (OBSERVED = read off the object that side returned; ASSUMED = the factory default for the kind, which is wrong for 182 of 285 operations). The subject's adapter never looked at what its implementation returned, so this difference is a finding about the ADAPTER and not about the port (D-43); an adapter must attribute through UValue.observedFrom(Object).
[corpus uInteger] 78	BooleanValue.isBoolean()	BOOLEAN(true)@BooleanValue	BOOLEAN(true)@Boolean	BOOLEAN(true)@BooleanValue	AGREE	java type mismatch: reference returned java.lang.Boolean (BOOLEAN(true)@Boolean) / subject returned org.tzi.use.uml.ocl.value.BooleanValue (BOOLEAN(true)@BooleanValue); the content is IDENTICAL -- right content, wrong Java type (defect D-18). This row is scored AGREE and counted in rows.javaTypeMismatch, not scored as a divergence: at S1 the ported side's class cannot be authentically observed, because no ported value class exists to observe, so a type-only difference measures the adapter and not the port (D-43). Provenance: reference OBSERVED, subject ASSUMED (OBSERVED = read off the object that side returned; ASSUMED = the factory default for the kind, which is wrong for 182 of 285 operations). The subject's adapter never looked at what its implementation returned, so this difference is a finding about the ADAPTER and not about the port (D-43); an adapter must attribute through UValue.observedFrom(Object).
[corpus uBoolean] 78	BooleanValue.isBoolean()	BOOLEAN(true)@BooleanValue	BOOLEAN(true)@Boolean	BOOLEAN(true)@BooleanValue	AGREE	java type mismatch: reference returned java.lang.Boolean (BOOLEAN(true)@Boolean) / subject returned org.tzi.use.uml.ocl.value.BooleanValue (BOOLEAN(true)@BooleanValue); the content is IDENTICAL -- right content, wrong Java type (defect D-18). This row is scored AGREE and counted in rows.javaTypeMismatch, not scored as a divergence: at S1 the ported side's class cannot be authentically observed, because no ported value class exists to observe, so a type-only difference measures the adapter and not the port (D-43). Provenance: reference OBSERVED, subject ASSUMED (OBSERVED = read off the object that side returned; ASSUMED = the factory default for the kind, which is wrong for 182 of 285 operations). The subject's adapter never looked at what its implementation returned, so this difference is a finding about the ADAPTER and not about the port (D-43); an adapter must attribute through UValue.observedFrom(Object).
[corpus uString] 78	BooleanValue.isBoolean()	BOOLEAN(true)@BooleanValue	BOOLEAN(true)@Boolean	BOOLEAN(true)@BooleanValue	AGREE	java type mismatch: reference returned java.lang.Boolean (BOOLEAN(true)@Boolean) / subject returned org.tzi.use.uml.ocl.value.BooleanValue (BOOLEAN(true)@BooleanValue); the content is IDENTICAL -- right content, wrong Java type (defect D-18). This row is scored AGREE and counted in rows.javaTypeMismatch, not scored as a divergence: at S1 the ported side's class cannot be authentically observed, because no ported value class exists to observe, so a type-only difference measures the adapter and not the port (D-43). Provenance: reference OBSERVED, subject ASSUMED (OBSERVED = read off the object that side returned; ASSUMED = the factory default for the kind, which is wrong for 182 of 285 operations). The subject's adapter never looked at what its implementation returned, so this difference is a finding about the ADAPTER and not about the port (D-43); an adapter must attribute through UValue.observedFrom(Object).
[corpus boolean] 78	BooleanValue.isBoolean()	BOOLEAN(true)@BooleanValue	BOOLEAN(true)@Boolean	BOOLEAN(true)@BooleanValue	AGREE	java type mismatch: reference returned java.lang.Boolean (BOOLEAN(true)@Boolean) / subject returned org.tzi.use.uml.ocl.value.BooleanValue (BOOLEAN(true)@BooleanValue); the content is IDENTICAL -- right content, wrong Java type (defect D-18). This row is scored AGREE and counted in rows.javaTypeMismatch, not scored as a divergence: at S1 the ported side's class cannot be authentically observed, because no ported value class exists to observe, so a type-only difference measures the adapter and not the port (D-43). Provenance: reference OBSERVED, subject ASSUMED (OBSERVED = read off the object that side returned; ASSUMED = the factory default for the kind, which is wrong for 182 of 285 operations). The subject's adapter never looked at what its implementation returned, so this difference is a finding about the ADAPTER and not about the port (D-43); an adapter must attribute through UValue.observedFrom(Object).
[corpus string] 78	BooleanValue.isBoolean()	BOOLEAN(true)@BooleanValue	BOOLEAN(true)@Boolean	BOOLEAN(true)@BooleanValue	AGREE	java type mismatch: reference returned java.lang.Boolean (BOOLEAN(true)@Boolean) / subject returned org.tzi.use.uml.ocl.value.BooleanValue (BOOLEAN(true)@BooleanValue); the content is IDENTICAL -- right content, wrong Java type (defect D-18). This row is scored AGREE and counted in rows.javaTypeMismatch, not scored as a divergence: at S1 the ported side's class cannot be authentically observed, because no ported value class exists to observe, so a type-only difference measures the adapter and not the port (D-43). Provenance: reference OBSERVED, subject ASSUMED (OBSERVED = read off the object that side returned; ASSUMED = the factory default for the kind, which is wrong for 182 of 285 operations). The subject's adapter never looked at what its implementation returned, so this difference is a finding about the ADAPTER and not about the port (D-43); an adapter must attribute through UValue.observedFrom(Object).
[corpus zeroDivisors] 78	BooleanValue.isBoolean()	BOOLEAN(true)@BooleanValue	BOOLEAN(true)@Boolean	BOOLEAN(true)@BooleanValue	AGREE	java type mismatch: reference returned java.lang.Boolean (BOOLEAN(true)@Boolean) / subject returned org.tzi.use.uml.ocl.value.BooleanValue (BOOLEAN(true)@BooleanValue); the content is IDENTICAL -- right content, wrong Java type (defect D-18). This row is scored AGREE and counted in rows.javaTypeMismatch, not scored as a divergence: at S1 the ported side's class cannot be authentically observed, because no ported value class exists to observe, so a type-only difference measures the adapter and not the port (D-43). Provenance: reference OBSERVED, subject ASSUMED (OBSERVED = read off the object that side returned; ASSUMED = the factory default for the kind, which is wrong for 182 of 285 operations). The subject's adapter never looked at what its implementation returned, so this difference is a finding about the ADAPTER and not about the port (D-43); an adapter must attribute through UValue.observedFrom(Object).
[corpus indexBoundaries] 78	BooleanValue.isBoolean()	BOOLEAN(true)@BooleanValue	BOOLEAN(true)@Boolean	BOOLEAN(true)@BooleanValue	AGREE	java type mismatch: reference returned java.lang.Boolean (BOOLEAN(true)@Boolean) / subject returned org.tzi.use.uml.ocl.value.BooleanValue (BOOLEAN(true)@BooleanValue); the content is IDENTICAL -- right content, wrong Java type (defect D-18). This row is scored AGREE and counted in rows.javaTypeMismatch, not scored as a divergence: at S1 the ported side's class cannot be authentically observed, because no ported value class exists to observe, so a type-only difference measures the adapter and not the port (D-43). Provenance: reference OBSERVED, subject ASSUMED (OBSERVED = read off the object that side returned; ASSUMED = the factory default for the kind, which is wrong for 182 of 285 operations). The subject's adapter never looked at what its implementation returned, so this difference is a finding about the ADAPTER and not about the port (D-43); an adapter must attribute through UValue.observedFrom(Object).
[corpus uReal] 79	BooleanValue.isCollection()	BOOLEAN(false)@BooleanValue	BOOLEAN(false)@Boolean	BOOLEAN(false)@BooleanValue	AGREE	java type mismatch: reference returned java.lang.Boolean (BOOLEAN(false)@Boolean) / subject returned org.tzi.use.uml.ocl.value.BooleanValue (BOOLEAN(false)@BooleanValue); the content is IDENTICAL -- right content, wrong Java type (defect D-18). This row is scored AGREE and counted in rows.javaTypeMismatch, not scored as a divergence: at S1 the ported side's class cannot be authentically observed, because no ported value class exists to observe, so a type-only difference measures the adapter and not the port (D-43). Provenance: reference OBSERVED, subject ASSUMED (OBSERVED = read off the object that side returned; ASSUMED = the factory default for the kind, which is wrong for 182 of 285 operations). The subject's adapter never looked at what its implementation returned, so this difference is a finding about the ADAPTER and not about the port (D-43); an adapter must attribute through UValue.observedFrom(Object).
[corpus uInteger] 79	BooleanValue.isCollection()	BOOLEAN(false)@BooleanValue	BOOLEAN(false)@Boolean	BOOLEAN(false)@BooleanValue	AGREE	java type mismatch: reference returned java.lang.Boolean (BOOLEAN(false)@Boolean) / subject returned org.tzi.use.uml.ocl.value.BooleanValue (BOOLEAN(false)@BooleanValue); the content is IDENTICAL -- right content, wrong Java type (defect D-18). This row is scored AGREE and counted in rows.javaTypeMismatch, not scored as a divergence: at S1 the ported side's class cannot be authentically observed, because no ported value class exists to observe, so a type-only difference measures the adapter and not the port (D-43). Provenance: reference OBSERVED, subject ASSUMED (OBSERVED = read off the object that side returned; ASSUMED = the factory default for the kind, which is wrong for 182 of 285 operations). The subject's adapter never looked at what its implementation returned, so this difference is a finding about the ADAPTER and not about the port (D-43); an adapter must attribute through UValue.observedFrom(Object).
[corpus uBoolean] 79	BooleanValue.isCollection()	BOOLEAN(false)@BooleanValue	BOOLEAN(false)@Boolean	BOOLEAN(false)@BooleanValue	AGREE	java type mismatch: reference returned java.lang.Boolean (BOOLEAN(false)@Boolean) / subject returned org.tzi.use.uml.ocl.value.BooleanValue (BOOLEAN(false)@BooleanValue); the content is IDENTICAL -- right content, wrong Java type (defect D-18). This row is scored AGREE and counted in rows.javaTypeMismatch, not scored as a divergence: at S1 the ported side's class cannot be authentically observed, because no ported value class exists to observe, so a type-only difference measures the adapter and not the port (D-43). Provenance: reference OBSERVED, subject ASSUMED (OBSERVED = read off the object that side returned; ASSUMED = the factory default for the kind, which is wrong for 182 of 285 operations). The subject's adapter never looked at what its implementation returned, so this difference is a finding about the ADAPTER and not about the port (D-43); an adapter must attribute through UValue.observedFrom(Object).
[corpus uString] 79	BooleanValue.isCollection()	BOOLEAN(false)@BooleanValue	BOOLEAN(false)@Boolean	BOOLEAN(false)@BooleanValue	AGREE	java type mismatch: reference returned java.lang.Boolean (BOOLEAN(false)@Boolean) / subject returned org.tzi.use.uml.ocl.value.BooleanValue (BOOLEAN(false)@BooleanValue); the content is IDENTICAL -- right content, wrong Java type (defect D-18). This row is scored AGREE and counted in rows.javaTypeMismatch, not scored as a divergence: at S1 the ported side's class cannot be authentically observed, because no ported value class exists to observe, so a type-only difference measures the adapter and not the port (D-43). Provenance: reference OBSERVED, subject ASSUMED (OBSERVED = read off the object that side returned; ASSUMED = the factory default for the kind, which is wrong for 182 of 285 operations). The subject's adapter never looked at what its implementation returned, so this difference is a finding about the ADAPTER and not about the port (D-43); an adapter must attribute through UValue.observedFrom(Object).
===================================================================
=== unwritten-port invariant: g-throws-error =================
seed                 20260817
subject              g-throws-error  (every method body: throw new AssertionError("TODO: port " + op.key()))
observability        NOTHING
javaTypeMismatch     0  <- agreement rows on which only the Java class differed (D-43)
operations           355  (enumerated from use.jar + atenearesearchgroup.uncertainty.jar)
corpora              uReal=24, uInteger=15, uBoolean=11, uString=28, boolean=4, string=16, zeroDivisors=7, indexBoundaries=8; receivers=104
rows                 0
measured rows        0  (AGREE + DIFFER)
agreement rows       0
verdict tally        {}
codomain census      0 operations: 0 measured nothing, 0 single-valued (NOT DISCRIMINATING), 0 discriminating
ESCAPED              java.lang.AssertionError: TODO: port BooleanValue.compareTo(value)  -> the sweep ABORTED; rows above are only those completed before it
fully agreed ops, DISCRIMINATING (a finding about the subject)  (none)
fully agreed ops, NOT DISCRIMINATING (a finding about the corpus)  (none)   [ASSERTED against reviewedDegenerateFullyAgreed since the D-35 fix]
===================================================================
=== D-15: the sign-off route ======================================
no sign-off                URealValue.isUReal(): 24 rows, 24 measured, 24 agreed, 0 disagreed, 0 intended departure(s), 0 java-type mismatch(es) (subject token OBSERVED on 0, ASSUMED on 0), 1 distinct reference value(s) [NOT DISCRIMINATING: always BOOLEAN(true)@Boolean]
signed off                 URealValue.isUReal(): 24 rows, 24 measured, 24 agreed, 0 disagreed, 0 intended departure(s), 0 java-type mismatch(es) (subject token OBSERVED on 0, ASSUMED on 0), 1 distinct reference value(s) [NOT DISCRIMINATING: always BOOLEAN(true)@Boolean; acknowledged: URealValue.isUReal() is a type predicate: the historical body is iconst_1/ireturn, so BOOLEAN(true) is the whole of its specification and no corpus can make it answer otherwise. Agreement here shows the operation exists and is reachable; it is not evidence about any computation.]
===================================================================
[INFO] Tests run: 10, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 74.67 s -- in Unwritten-port invariant
[INFO] Running B7 corrections the differential sweep cannot see
[INFO] Running M-18: SBooleanValue.compareTo was 'return 0'
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.006 s -- in M-18: SBooleanValue.compareTo was 'return 0'
[INFO] Running M-12: UStringValue.compareTo compared a bare string against a wrapper rendering
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.005 s -- in M-12: UStringValue.compareTo compared a bare string against a wrapper rendering
[INFO] Running M-9 and bundle A: UInteger against UReal compared equal in both directions
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.005 s -- in M-9 and bundle A: UInteger against UReal compared equal in both directions
[INFO] Running F-3 and F-10: the hashCode/equals contract
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.006 s -- in F-3 and F-10: the hashCode/equals contract
[INFO] Running M-10 and F-4: URealValue.equals had no UIntegerValue arm and did not round
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.006 s -- in M-10 and F-4: URealValue.equals had no UIntegerValue arm and did not round
[INFO] Running M-8: UBooleanValue.equals had a dead conjunct
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.004 s -- in M-8: UBooleanValue.equals had a dead conjunct
[INFO] Running M-11: UStringValue.equals was the constant false
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.006 s -- in M-11: UStringValue.equals was the constant false
[INFO] Tests run: 0, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.048 s -- in B7 corrections the differential sweep cannot see
[INFO] Running HistoricalOracle class-loader isolation
[INFO] Tests run: 11, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.042 s -- in HistoricalOracle class-loader isolation
[INFO] Running Differential harness regressions
=== D1 reproduction ===============================================
tally                URealValue.add(value): 169 rows, 0 measured, 0 distinct ref, HARNESS_ERROR=169
disagreements        169
row 0                0	URealValue.add(value)	UINTEGER(0,0.0)@UIntegerValue | UINTEGER(0,0.0)@UIntegerValue	HARNESS_ERROR:org.tzi.use.uncertainty.differential.HarnessMarshallingException	HARNESS_ERROR:org.tzi.use.uncertainty.differential.HarnessMarshallingException	HARNESS_ERROR	no measurement on either side; no comparison was made. reference could not be driven: org.tzi.use.uncertainty.differential.HarnessMarshallingException: URealValue.add(value) expects a receiver of org.tzi.use.uml.ocl.value.URealValue but the supplied UINTEGER(0,0.0)@UIntegerValue maps to org.tzi.use.uml.ocl.value.UIntegerValue / subject could not be driven: org.tzi.use.uncertainty.differential.HarnessMarshallingException: URealValue.add(value) needs a UREAL receiver, got UINTEGER(0,0.0)@UIntegerValue
===================================================================
=== D-2 reproduction (two stubs) ==================================
tally                URealValue.add(value): 169 rows, 0 measured, 0 distinct ref, HARNESS_ERROR=169
disagreements        169
row 0                0	URealValue.add(value)	UINTEGER(0,0.0)@UIntegerValue | UINTEGER(0,0.0)@UIntegerValue	HARNESS_ERROR:org.tzi.use.uncertainty.differential.HarnessMarshallingException	HARNESS_ERROR:org.tzi.use.uncertainty.differential.HarnessMarshallingException	HARNESS_ERROR	no measurement on either side; no comparison was made. reference could not be driven: org.tzi.use.uncertainty.differential.HarnessMarshallingException: URealValue.add(value) needs a UREAL receiver, got UINTEGER(0,0.0)@UIntegerValue / subject could not be driven: org.tzi.use.uncertainty.differential.HarnessMarshallingException: URealValue.add(value) needs a UREAL receiver, got UINTEGER(0,0.0)@UIntegerValue
===================================================================
=== D-13: wrong exception class ===================================
same class  UStringValue.at(int): 2 rows, 0 measured, 0 distinct ref, BOTH_THREW=2
wrong class UStringValue.at(int): 2 rows, 0 measured, 0 distinct ref, BOTH_THREW=2, throwClassMismatch=2
===================================================================
=== D-43: every public UValue member that accepts a String ========
  opaque[class java.lang.String, class java.lang.String]
  string[class java.lang.String]
  uString[class java.lang.String, double]
===================================================================
=== D-43: the same type-mismatch row, two attributions of the subject ===
  ASSUMED (factory default)
      java type mismatch: reference returned java.lang.Integer (INTEGER(7)@Integer) / subject returned org.tzi.use.uml.ocl.value.IntegerValue (INTEGER(7)@IntegerValue); the content is IDENTICAL -- right content, wrong Java type (defect D-18). This row is scored AGREE and counted in rows.javaTypeMismatch, not scored as a divergence: at S1 the ported side's class cannot be authentically observed, because no ported value class exists to observe, so a type-only difference measures the adapter and not the port (D-43). Provenance: reference OBSERVED, subject ASSUMED (OBSERVED = read off the object that side returned; ASSUMED = the factory default for the kind, which is wrong for 182 of 285 operations). The subject's adapter never looked at what its implementation returned, so this difference is a finding about the ADAPTER and not about the port (D-43); an adapter must attribute through UValue.observedFrom(Object).
  OBSERVED (off a real object of another class)
      java type mismatch: reference returned java.lang.Integer (INTEGER(7)@Integer) / subject returned java.util.concurrent.atomic.AtomicInteger (INTEGER(7)@AtomicInteger); the content is IDENTICAL -- right content, wrong Java type (defect D-18). This row is scored AGREE and counted in rows.javaTypeMismatch, not scored as a divergence: at S1 the ported side's class cannot be authentically observed, because no ported value class exists to observe, so a type-only difference measures the adapter and not the port (D-43). Provenance: reference OBSERVED, subject OBSERVED (OBSERVED = read off the object that side returned; ASSUMED = the factory default for the kind, which is wrong for 182 of 285 operations). Whether the object the subject observed is the one its implementation returned is not checkable by this harness.
===================================================================
=== D-3: UNSUPPORTED note =========================================
0	SetValue.includes(value)	UBOOLEAN(true,0.5)@UBooleanValue | UBOOLEAN(false,0.5)@UBooleanValue	UNSUPPORTED	UNSUPPORTED	UNSUPPORTED	no measurement. reference: this harness cannot marshal a SetValue receiver, so it cannot drive SetValue.includes(value); this is a limit of the instrument and says nothing about whether the historical implementation declares the operation / subject: stub-faithful implements only [URealValue.add(value), URealValue.minus(value), URealValue.neg()], not SetValue.includes(value)
===================================================================
=== MIXED note, both directions ===================================
the reference threw and the subject returned. reference threw java.lang.RuntimeException: historical blew up / subject returned UREAL(2.0,0.7071067811865476)@URealValue
===================================================================
=== both-sided HARNESS_ERROR note =================================
no measurement on either side; no comparison was made. reference could not be driven: org.tzi.use.uncertainty.differential.HarnessMarshallingException: cannot marshal UREAL(1.0,0.5)@URealValue for URealValue.add(value) [ref] / subject could not be driven: org.tzi.use.uncertainty.differential.HarnessMarshallingException: cannot marshal UREAL(1.0,0.5)@URealValue for URealValue.add(value) [sub]
===================================================================
=== D-15: the stage gate ==========================================
sweep of URealValue.add(value) is not a stage pass:
  - the reference side produced 1 distinct value(s) across 1 measured row(s), always UREAL(2.0,0.0)@URealValue. This operation could not have failed over this domain, so agreement on it is decided before either implementation runs and is not evidence of fidelity (defect D-15). Either widen the domain until the reference answers differently, or sign the operation off in AcceptedDegenerateOperations with a written rationale — which is copied into the report, so the weakness travels with the number.
  tally: URealValue.add(value): 1 rows, 1 measured, 1 distinct ref, AGREE=1
PASSES: URealValue.add(value): 16 rows, 16 measured, 16 agreed, 0 disagreed, 0 intended departure(s), 0 java-type mismatch(es) (subject token OBSERVED on 0, ASSUMED on 0), 10 distinct reference value(s) [DISCRIMINATING]
===================================================================
=== D-18: the type-mismatch note =================================
0	URealValue.neg()	UREAL(1.0,0.0)@URealValue	INTEGER(7)@Integer	INTEGER(7)@IntegerValue	AGREE	java type mismatch: reference returned java.lang.Integer (INTEGER(7)@Integer) / subject returned org.tzi.use.uml.ocl.value.IntegerValue (INTEGER(7)@IntegerValue); the content is IDENTICAL -- right content, wrong Java type (defect D-18). This row is scored AGREE and counted in rows.javaTypeMismatch, not scored as a divergence: at S1 the ported side's class cannot be authentically observed, because no ported value class exists to observe, so a type-only difference measures the adapter and not the port (D-43). Provenance: reference OBSERVED, subject ASSUMED (OBSERVED = read off the object that side returned; ASSUMED = the factory default for the kind, which is wrong for 182 of 285 operations). The subject's adapter never looked at what its implementation returned, so this difference is a finding about the ADAPTER and not about the port (D-43); an adapter must attribute through UValue.observedFrom(Object).
===================================================================
=== FIX 1: two unrelated RuntimeExceptions ========================
0	UIntegerValue.power(value)	UINTEGER(2,0.5)@UIntegerValue | UINTEGER(3,0.5)@UIntegerValue	THROWN:java.lang.RuntimeException	THROWN:java.lang.RuntimeException	BOTH_THREW	reference threw java.lang.RuntimeException: UInteger.power() : expected Real or Integer exponent value / subject threw java.lang.RuntimeException: TODO: port UIntegerValue.power(value)
===================================================================
=== D-15: the report header =======================================
# harness	differential-sweep/1
# seed	1
# reference	stub-faithful
# subject	stub-faithful
# operations	URealValue.add(value),URealValue.minus(value)
# rows	10
# rows.measured	10
# rows.agreement	10
# rows.disagreement	0
# rows.throwClassMismatch	0
# rows.javaTypeMismatch	0
# rows.subjectTypeObserved	0
# rows.subjectTypeAssumed	0
# rows.intendedDeparture	0
# verdict.AGREE	10
# op.URealValue.add(value).rows	1
# op.URealValue.add(value).measured	1
# op.URealValue.add(value).agreement	1
# op.URealValue.add(value).disagreement	0
# op.URealValue.add(value).intendedDeparture	0
# op.URealValue.add(value).javaTypeMismatch	0
# op.URealValue.add(value).subjectTypeObserved	0
# op.URealValue.add(value).subjectTypeAssumed	0
# op.URealValue.add(value).distinctReferenceValues	1
# op.URealValue.add(value).discriminating	false
# op.URealValue.add(value).soleReferenceValue	UREAL(2.0,0.0)@URealValue
# op.URealValue.add(value).degenerate.acknowledged	reviewed: one-point domain
# op.URealValue.minus(value).rows	9
# op.URealValue.minus(value).measured	9
# op.URealValue.minus(value).agreement	9
# op.URealValue.minus(value).disagreement	0
# op.URealValue.minus(value).intendedDeparture	0
# op.URealValue.minus(value).javaTypeMismatch	0
# op.URealValue.minus(value).subjectTypeObserved	0
# op.URealValue.minus(value).subjectTypeAssumed	0
# op.URealValue.minus(value).distinctReferenceValues	7
# op.URealValue.minus(value).discriminating	true
# accepted.degenerateOperations	1
# accepted.degenerateOperation	URealValue.add(value)|UREAL(2.0,0.0)@URealValue -> reviewed: one-point domain
===================================================================
=== H21: the same mismatch total, two causes =======================
  summary  ASSUMED  URealValue.add(value): 4 rows, 4 measured, 1 distinct ref, AGREE=4, javaTypeMismatch=4 (subjectType OBSERVED=0 ASSUMED=4)
  summary  OBSERVED URealValue.minus(value): 4 rows, 4 measured, 1 distinct ref, AGREE=4, javaTypeMismatch=4 (subjectType OBSERVED=4 ASSUMED=0)
  stage    ASSUMED  URealValue.add(value): 4 rows, 4 measured, 4 agreed, 0 disagreed, 0 intended departure(s), 4 java-type mismatch(es) (subject token OBSERVED on 0, ASSUMED on 4), 1 distinct reference value(s) [NOT DISCRIMINATING: always INTEGER(7)@Integer]
  stage    OBSERVED URealValue.minus(value): 4 rows, 4 measured, 4 agreed, 0 disagreed, 0 intended departure(s), 4 java-type mismatch(es) (subject token OBSERVED on 4, ASSUMED on 0), 1 distinct reference value(s) [NOT DISCRIMINATING: always INTEGER(7)@Integer]
===================================================================
=== H21: the report header ========================================
# harness	differential-sweep/1
# seed	1
# reference	ref
# subject	sub-assumed
# operations	URealValue.add(value),URealValue.minus(value)
# rows	8
# rows.measured	8
# rows.agreement	8
# rows.disagreement	0
# rows.throwClassMismatch	0
# rows.javaTypeMismatch	8
# rows.subjectTypeObserved	4
# rows.subjectTypeAssumed	4
# rows.intendedDeparture	0
# verdict.AGREE	8
# op.URealValue.add(value).rows	4
# op.URealValue.add(value).measured	4
# op.URealValue.add(value).agreement	4
# op.URealValue.add(value).disagreement	0
# op.URealValue.add(value).intendedDeparture	0
# op.URealValue.add(value).javaTypeMismatch	4
# op.URealValue.add(value).subjectTypeObserved	0
# op.URealValue.add(value).subjectTypeAssumed	4
# op.URealValue.add(value).distinctReferenceValues	1
# op.URealValue.add(value).discriminating	false
# op.URealValue.add(value).soleReferenceValue	INTEGER(7)@Integer
# op.URealValue.minus(value).rows	4
# op.URealValue.minus(value).measured	4
# op.URealValue.minus(value).agreement	4
# op.URealValue.minus(value).disagreement	0
# op.URealValue.minus(value).intendedDeparture	0
# op.URealValue.minus(value).javaTypeMismatch	4
# op.URealValue.minus(value).subjectTypeObserved	4
# op.URealValue.minus(value).subjectTypeAssumed	0
# op.URealValue.minus(value).distinctReferenceValues	1
# op.URealValue.minus(value).discriminating	false
# op.URealValue.minus(value).soleReferenceValue	INTEGER(7)@Integer
# accepted.degenerateOperations	0
===================================================================
=== D-11: writer refusal ==========================================
refusing to write a differential report 'no-measurements.tsv' that contains no measurements: 22 row(s) across 1 sweep result(s), and not one of them compared two observed values. Every number this file would carry would describe an absence, and a reader would see '# rows 22' and a green-looking verdict tally. The usual causes are a subject that throws on every row, a receiver type the harness cannot marshal, and an operation that returns void.
    URealValue.setTypeToRuntimeType(): 22 rows, 0 measured, 0 distinct ref, UNMEASURABLE=22
===================================================================
=== OPAQUE representation =========================================
foreign toString()   UReal(0.3333333333, 0.6666666667)
field-derived        URealValue{Value.fType=URealType{BasicType.fTypename="UReal"},URealValue.uReal=UReal{UReal.u=0.6666666666666666,UReal.x=0.3333333333333333}}
SBooleanValue.TRUE   toString  SBoolean(1.0, 0.0, 0.0, 1.0)
SBooleanValue.TRUE   canonical OPAQUE("org.tzi.use.uml.ocl.value.SBooleanValue|SBooleanValue{Value.fType=SBooleanType{BasicType.fTypename=\"SBoolean\"},SBooleanValue.sBoolean=SBoolean{SBoolean.a=1.0,SBoolean.b=1.0,SBoolean.d=0.0,SBoolean.relativeWeight=1.0,SBoolean.u=0.0}}")@SBooleanValue
===================================================================
=== golden byte comparison ========================================
differential report /home/xoruser/msc-4/use-msc2026/use-core/target/differential/d-byte-probe.tsv differs from the committed golden /home/xoruser/msc-4/use-msc2026/docs/port2/differential/d-byte-probe.tsv in bytes but not in any line: the files disagree only about line terminators or a trailing newline. A line-based comparison would have called these two files equal. Re-record with -Duse.differential.golden.refresh=true once the change is understood.
===================================================================
=== D-10 reproduction (VOID vs VOID) ==============================
tally                URealValue.setTypeToRuntimeType(): 22 rows, 0 measured, 0 distinct ref, UNMEASURABLE=22
measurements         0
agreements           0
row 0                0	URealValue.setTypeToRuntimeType()	UREAL(0.0,0.0)@URealValue	VOID	VOID	UNMEASURABLE	no measurement: the operation is declared void, so it has no result, and this harness does not re-read the receiver after a call -- no post-state was observed on either side, so nothing about either implementation was measured here. reference returned VOID / subject returned VOID
===================================================================
=== D-1 reproduction (null vs null) ===============================
tally                URealValue.add(value): 4 rows, 0 measured, 0 distinct ref, HARNESS_ERROR=4
row 0                0	URealValue.add(value)	UREAL(1.0,0.5)@URealValue | UREAL(1.0,0.5)@URealValue	HARNESS_ERROR:org.tzi.use.uncertainty.differential.HarnessMarshallingException	HARNESS_ERROR:org.tzi.use.uncertainty.differential.HarnessMarshallingException	HARNESS_ERROR	no measurement on either side; no comparison was made. reference could not be driven: org.tzi.use.uncertainty.differential.HarnessMarshallingException: returns-null returned Java null from URealValue.add(value); a Candidate must return a UValue (use UValue.nullValue() for a genuine null result). No comparable value exists, so this row is not a measurement. / subject could not be driven: org.tzi.use.uncertainty.differential.HarnessMarshallingException: returns-null returned Java null from URealValue.add(value); a Candidate must return a UValue (use UValue.nullValue() for a genuine null result). No comparable value exists, so this row is not a measurement.
===================================================================
=== D-34: the same sweep, two sign-off sets =======================
  signed:  # accepted.degenerateOperations	1
  signed:  # accepted.degenerateOperation	URealValue.add(value)|UREAL(2.0,0.0)@URealValue -> reviewed: a one-point domain, kept as a reachability check only; nothing here is evidence about the addition rule
  none:    # accepted.degenerateOperations	0
===================================================================
[INFO] Tests run: 35, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.177 s -- in Differential harness regressions
[INFO] Running org.tzi.use.uncertainty.differential.IntendedDeparturesTest
[INFO] Running content is split off the type token without guessing
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.003 s -- in content is split off the type token without guessing
[INFO] Running the builder refuses what would become a blanket exemption
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.010 s -- in the builder refuses what would become a blanket exemption
[INFO] Running the population form names an exact set, written out
[INFO] Tests run: 7, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.010 s -- in the population form names an exact set, written out
[INFO] Running the gate cannot be reached without naming the mechanism
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.005 s -- in the gate cannot be reached without naming the mechanism
[INFO] Running it cannot be used to make a wrong port look right
[INFO] Tests run: 6, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.006 s -- in it cannot be used to make a wrong port look right
[INFO] Running the verdict is a measurement and is not an agreement
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.004 s -- in the verdict is a measurement and is not an agreement
[INFO] Tests run: 0, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.040 s -- in org.tzi.use.uncertainty.differential.IntendedDeparturesTest
[INFO] Running org.tzi.use.uncertainty.datatypes.DatatypeCloneableContractTest
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.001 s -- in org.tzi.use.uncertainty.datatypes.DatatypeCloneableContractTest
[INFO] Running uSelect/uSelectC, UBoolean forAll/exists, uncertain collection membership
[INFO] Running multi-variable forAll/exists: a pre-existing fork defect, found and fixed
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.006 s -- in multi-variable forAll/exists: a pre-existing fork defect, found and fixed
[INFO] Running collection membership answers a degree when the comparison is uncertain
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.006 s -- in collection membership answers a degree when the comparison is uncertain
[INFO] Running forAll/exists accept a UBoolean body and combine via uncertain and/or
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.005 s -- in forAll/exists accept a UBoolean body and combine via uncertain and/or
[INFO] Running uSelectC: explicit confidence threshold
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.011 s -- in uSelectC: explicit confidence threshold
[INFO] Running uSelect: default 0.5 confidence threshold
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.002 s -- in uSelect: default 0.5 confidence threshold
[INFO] Tests run: 0, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.032 s -- in uSelect/uSelectC, UBoolean forAll/exists, uncertain collection membership
[INFO] Running SBoolean: the 13 operations found untested
[INFO] Running conjunctiveCertainty/degreeOfConflict: declared-type fix and its regression
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.002 s -- in conjunctiveCertainty/degreeOfConflict: declared-type fix and its regression
[INFO] Running conversions and pairwise operations
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.003 s -- in conversions and pairwise operations
[INFO] Running classification predicates
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.005 s -- in classification predicates
[INFO] Running scalar accessors: projection, getRelativeWeight, certainty
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.002 s -- in scalar accessors: projection, getRelativeWeight, certainty
[INFO] Tests run: 0, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.014 s -- in SBoolean: the 13 operations found untested
[INFO] Running UString: all 20 operations
[INFO] Running ordering: <, <=, >, >=
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.001 s -- in ordering: <, <=, >, >=
[INFO] Running conversions: toString, toInteger, toReal, toBoolean, toUBoolean
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.003 s -- in conversions: toString, toInteger, toReal, toBoolean, toUBoolean
[INFO] Running case conversion
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.001 s -- in case conversion
[INFO] Running concatenation, indexOf, substring
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.001 s -- in concatenation, indexOf, substring
[INFO] Running character access: at, character, size
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.002 s -- in character access: at, character, size
[INFO] Running mutators: setValue, setConfidence
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.001 s -- in mutators: setValue, setConfidence
[INFO] Running accessors: value, confidence
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.001 s -- in accessors: value, confidence
[INFO] Tests run: 0, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.014 s -- in UString: all 20 operations
[INFO] Running org.tzi.use.uncertainty.SBooleanFusionValueTest
[INFO] Running consensusAndCompromiseFusion: O(4^n) hazard and degenerate cases
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.015 s -- in consensusAndCompromiseFusion: O(4^n) hazard and degenerate cases
[INFO] Running weightedBeliefFusion: receiver-prepended, confidence-weighted averaging (FUSION-2018 van der Heijden et al.)
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.003 s -- in weightedBeliefFusion: receiver-prepended, confidence-weighted averaging (FUSION-2018 van der Heijden et al.)
[INFO] Running aleatoryCumulativeBeliefFusion vs epistemicCumulativeBeliefFusion: same input must diverge
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.002 s -- in aleatoryCumulativeBeliefFusion vs epistemicCumulativeBeliefFusion: same input must diverge
[INFO] Running epistemicCumulativeBeliefFusion: same accumulation as aleatory, then projected onto the uncertainty-maximized boundary
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.003 s -- in epistemicCumulativeBeliefFusion: same accumulation as aleatory, then projected onto the uncertainty-maximized boundary
[INFO] Running aleatoryCumulativeBeliefFusion: receiver-prepended, Josang's cumulative fusion for i.i.d. sources
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.002 s -- in aleatoryCumulativeBeliefFusion: receiver-prepended, Josang's cumulative fusion for i.i.d. sources
[INFO] Running averageBeliefFusion: receiver-prepended, equation (32) of JWZ2017-FUSION (not the book formula)
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.002 s -- in averageBeliefFusion: receiver-prepended, equation (32) of JWZ2017-FUSION (not the book formula)
[INFO] Running beliefConstraintFusion: receiver-prepended, Dempster's-rule belief-constraint combination
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.003 s -- in beliefConstraintFusion: receiver-prepended, Dempster's-rule belief-constraint combination
[INFO] Running discount: multi-edge trust discounting, receiver NOT prepended to the collection
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.003 s -- in discount: multi-edge trust discounting, receiver NOT prepended to the collection
[INFO] Running majorityBeliefFusion: dogmatic vote by projection-vs-baseRate
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.002 s -- in majorityBeliefFusion: dogmatic vote by projection-vs-baseRate
[INFO] Running minimumBeliefFusion: receiver-prepended, picks the lowest-projection opinion
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.003 s -- in minimumBeliefFusion: receiver-prepended, picks the lowest-projection opinion
[INFO] Tests run: 0, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.040 s -- in org.tzi.use.uncertainty.SBooleanFusionValueTest
[INFO] Running org.tzi.use.uncertainty.gate.UpstreamOracleGateWiringTest
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.007 s -- in org.tzi.use.uncertainty.gate.UpstreamOracleGateWiringTest
[INFO] Running org.tzi.use.uml.mm.ModelAPITest
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.026 s -- in org.tzi.use.uml.mm.ModelAPITest
[INFO] Running org.tzi.use.uml.ocl.value.MetamorphicRelationsTest
[INFO] Running M-6: simplex closure — every SBoolean-returning operation satisfies |b+d+u-1| <= 0.001
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.004 s -- in M-6: simplex closure — every SBoolean-returning operation satisfies |b+d+u-1| <= 0.001
[INFO] Running M-5: interning independence — a value equal to TRUE/FALSE but not the interned instance behaves identically
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.001 s -- in M-5: interning independence — a value equal to TRUE/FALSE but not the interned instance behaves identically
[INFO] Running M-4: widening agreement — a UInteger operation and its UReal widening agree where both are defined
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.001 s -- in M-4: widening agreement — a UInteger operation and its UReal widening agree where both are defined
[INFO] Running M-3: canonicalisation — UBoolean(false,p) is UBoolean(true,1-p) on every operation
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.002 s -- in M-3: canonicalisation — UBoolean(false,p) is UBoolean(true,1-p) on every operation
[INFO] Running M-2: degree monotonicity — raising an input's uncertainty must not lower the result's
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.001 s -- in M-2: degree monotonicity — raising an input's uncertainty must not lower the result's
[INFO] Running M-1: crisp embedding — op(U(x,0), U(y,0)) carries the same representative as crisp op(x,y), degree 0
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.002 s -- in M-1: crisp embedding — op(U(x,0), U(y,0)) carries the same representative as crisp op(x,y), degree 0
[INFO] Tests run: 0, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.012 s -- in org.tzi.use.uml.ocl.value.MetamorphicRelationsTest
[INFO] Running org.tzi.use.uml.ocl.value.UBooleanValueTest
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.002 s -- in org.tzi.use.uml.ocl.value.UBooleanValueTest
[INFO] Running org.tzi.use.uml.ocl.expr.UBooleanExpOpsTest
[INFO] Tests run: 27, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.015 s -- in org.tzi.use.uml.ocl.expr.UBooleanExpOpsTest
[INFO] Running org.tzi.use.uml.ocl.expr.UIntegerExpOpsTest
[INFO] Tests run: 39, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.019 s -- in org.tzi.use.uml.ocl.expr.UIntegerExpOpsTest
[INFO] Running org.tzi.use.uml.ocl.expr.URealExpOpsTest
[INFO] Tests run: 38, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.022 s -- in org.tzi.use.uml.ocl.expr.URealExpOpsTest
[INFO] Running org.tzi.use.uml.ocl.expr.ExpQueryUncertaintyTest
[INFO] Tests run: 12, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.007 s -- in org.tzi.use.uml.ocl.expr.ExpQueryUncertaintyTest
[INFO] Running B7 at the type and dispatch layers
[INFO] Running M-37: UInteger.value() declared a static type its eval never returns
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.001 s -- in M-37: UInteger.value() declared a static type its eval never returns
[INFO] Running M-38: `or` on two undefined UBooleans threw NullPointerException
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.001 s -- in M-38: `or` on two undefined UBooleans threw NullPointerException
[INFO] Running porting omission: VoidType had no isKindOfU* overrides
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.002 s -- in porting omission: VoidType had no isKindOfU* overrides
[INFO] Running M-22: every uncertain type's constructor is package-private
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.001 s -- in M-22: every uncertain type's constructor is package-private
[INFO] Running M-21: a directly-constructed type was missing from its own supertype set
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.002 s -- in M-21: a directly-constructed type was missing from its own supertype set
[INFO] Tests run: 0, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.008 s -- in B7 at the type and dispatch layers
[INFO] Running org.tzi.use.uml.ocl.type.UncertainTypeLatticeTest
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.002 s -- in org.tzi.use.uml.ocl.type.UncertainTypeLatticeTest
[INFO] Running org.tzi.use.uml.ocl.type.TupleTypeSupertypeCostTest
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.027 s -- in org.tzi.use.uml.ocl.type.TupleTypeSupertypeCostTest
[INFO] 
[INFO] Results:
[INFO] 
[INFO] Tests run: 414, Failures: 0, Errors: 0, Skipped: 0
[INFO] 
[INFO] 
[INFO] --- jar:3.5.0:jar (default-jar) @ use-core ---
[INFO] Building jar: /home/xoruser/msc-4/use-msc2026/use-core/target/use-core-7.5.0.jar
[INFO] 
[INFO] --- failsafe:2.22.2:integration-test (default) @ use-core ---
[INFO] 
[INFO] -------------------------------------------------------
[INFO]  T E S T S
[INFO] -------------------------------------------------------
SLF4J(W): No SLF4J providers were found.
SLF4J(W): Defaulting to no-operation (NOP) logger implementation
SLF4J(W): See https://www.slf4j.org/codes.html#noProviders for further details.
[INFO] Running org.tzi.use.OCLExpressionIT
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.261 s - in org.tzi.use.OCLExpressionIT
[INFO] 
[INFO] Results:
[INFO] 
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
[INFO] 
[INFO] 
[INFO] --- failsafe:2.22.2:verify (default) @ use-core ---
[INFO] 
[INFO] --- exec:3.5.0:exec (upstream-oracle-floor) @ use-core ---
[floor] ===== upstream-oracle floor check: use-core =====
[floor] requested profiles (reactor-wide, from the command line): (none)
[floor] this module's upstream-oracle profile effective: false
[floor] mode: DEFAULT
[floor] allow-profiles (-Duse.floor.allowProfiles): (none)
[floor] reactor: FULL (no -pl/--projects, no -rf/--resume-from)
[floor] freshness stamp: 2026-08-20T22:57:28.531Z — reports older than this are stale and are NOT counted
[floor] surefire  use-core  classes=81  (floor 70 )  methods=414  (floor 393 )  executions=414  failures=0 errors=0 skipped=0 stale-ignored=47
[floor] failsafe  use-core  classes=1   (floor 1  )  methods=1    (floor 1   )  executions=1    failures=0 errors=0 skipped=0 stale-ignored=0
[floor] vintage-only sentinel org.tzi.use.parser.USECompilerTest: absent
[floor] wrote receipt /home/xoruser/msc-4/use-msc2026/use-core/target/upstream-oracle-floor.receipt (verdict=PASS)
[floor] PASS — use-core met every pinned floor in DEFAULT mode.
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time:  01:56 min
[INFO] Finished at: 2026-08-21T05:59:23+07:00
[INFO] ------------------------------------------------------------------------
```

### 2.2 `mvn verify -Pupstream-oracle` (oracle profile)

```
[INFO] Scanning for projects...
[INFO] 
[INFO] ------------------------< org.tzi.use:use-core >------------------------
[INFO] Building use-core 7.5.0
[INFO]   from pom.xml
[INFO] --------------------------------[ jar ]---------------------------------
[INFO] 
[INFO] --- exec:3.5.0:exec (upstream-oracle-floor-stamp) @ use-core ---
[floor] initialize: requested profiles [upstream-oracle], declared in this reactor [upstream-oracle], allow-profiles (-Duse.floor.allowProfiles) (none)
[floor] wrote freshness stamp /home/xoruser/msc-4/use-msc2026/use-core/target/upstream-oracle-floor.stamp
[INFO] 
[INFO] --- merge:1.2.0:merge (merge-grammar-files) @ use-core ---
[INFO] 
[INFO] --- antlr3:3.5.3:antlr (antlr) @ use-core ---
[INFO] ANTLR: Processing source directory /home/xoruser/msc-4/use-msc2026/use-core/target/grammars
[INFO] 
[INFO] --- copy-rename:1.0:rename (move-antlr-parser-use) @ use-core ---
[INFO] Renamed /home/xoruser/msc-4/use-msc2026/use-core/target/generated-sources/antlr3/USELexer.java to /home/xoruser/msc-4/use-msc2026/use-core/target/generated-sources/antlr3/org/tzi/use/parser/use/USELexer.java
[INFO] Renamed /home/xoruser/msc-4/use-msc2026/use-core/target/generated-sources/antlr3/USEParser.java to /home/xoruser/msc-4/use-msc2026/use-core/target/generated-sources/antlr3/org/tzi/use/parser/use/USEParser.java
[INFO] Renamed /home/xoruser/msc-4/use-msc2026/use-core/target/generated-sources/antlr3/GeneratorLexer.java to /home/xoruser/msc-4/use-msc2026/use-core/target/generated-sources/antlr3/org/tzi/use/parser/generator/GeneratorLexer.java
[INFO] Renamed /home/xoruser/msc-4/use-msc2026/use-core/target/generated-sources/antlr3/GeneratorParser.java to /home/xoruser/msc-4/use-msc2026/use-core/target/generated-sources/antlr3/org/tzi/use/parser/generator/GeneratorParser.java
[INFO] Renamed /home/xoruser/msc-4/use-msc2026/use-core/target/generated-sources/antlr3/OCLLexer.java to /home/xoruser/msc-4/use-msc2026/use-core/target/generated-sources/antlr3/org/tzi/use/parser/ocl/OCLLexer.java
[INFO] Renamed /home/xoruser/msc-4/use-msc2026/use-core/target/generated-sources/antlr3/OCLParser.java to /home/xoruser/msc-4/use-msc2026/use-core/target/generated-sources/antlr3/org/tzi/use/parser/ocl/OCLParser.java
[INFO] Renamed /home/xoruser/msc-4/use-msc2026/use-core/target/generated-sources/antlr3/ShellCommandLexer.java to /home/xoruser/msc-4/use-msc2026/use-core/target/generated-sources/antlr3/org/tzi/use/parser/shell/ShellCommandLexer.java
[INFO] Renamed /home/xoruser/msc-4/use-msc2026/use-core/target/generated-sources/antlr3/ShellCommandParser.java to /home/xoruser/msc-4/use-msc2026/use-core/target/generated-sources/antlr3/org/tzi/use/parser/shell/ShellCommandParser.java
[INFO] Renamed /home/xoruser/msc-4/use-msc2026/use-core/target/generated-sources/antlr3/SoilLexer.java to /home/xoruser/msc-4/use-msc2026/use-core/target/generated-sources/antlr3/org/tzi/use/parser/soil/SoilLexer.java
[INFO] Renamed /home/xoruser/msc-4/use-msc2026/use-core/target/generated-sources/antlr3/SoilParser.java to /home/xoruser/msc-4/use-msc2026/use-core/target/generated-sources/antlr3/org/tzi/use/parser/soil/SoilParser.java
[INFO] Renamed /home/xoruser/msc-4/use-msc2026/use-core/target/generated-sources/antlr3/TestSuiteLexer.java to /home/xoruser/msc-4/use-msc2026/use-core/target/generated-sources/antlr3/org/tzi/use/parser/testsuite/TestSuiteLexer.java
[INFO] Renamed /home/xoruser/msc-4/use-msc2026/use-core/target/generated-sources/antlr3/TestSuiteParser.java to /home/xoruser/msc-4/use-msc2026/use-core/target/generated-sources/antlr3/org/tzi/use/parser/testsuite/TestSuiteParser.java
[INFO] 
[INFO] --- build-helper:3.6.0:add-source (add-antlr3-generated-source) @ use-core ---
[INFO] Source directory: /home/xoruser/msc-4/use-msc2026/use-core/target/generated-sources/antlr3 added.
[INFO] 
[INFO] --- resources:3.4.0:resources (default-resources) @ use-core ---
[INFO] Copying 512 resources from src/main/resources to target/classes
[INFO] 
[INFO] --- build-helper:3.6.0:add-test-source (add-test-source) @ use-core ---
[INFO] Test Source directory: /home/xoruser/msc-4/use-msc2026/use-core/src/it/java added.
[INFO] 
[INFO] --- compiler:3.15.0:compile (default-compile) @ use-core ---
[WARNING] *********************************************************************************************************************************************************************************************
[WARNING] * Required filename-based automodules detected: [antlr-runtime-3.5.3.jar, combinatoricslib-2.3.jar, vtd-xml-2.13.4.jar]. Please don't publish this project to a public artifact repository! *
[WARNING] *********************************************************************************************************************************************************************************************
[INFO] Recompiling the module because of changed source code.
[INFO] Compiling 605 source files with javac [debug target 21 module-path] to target/classes
[INFO] /home/xoruser/msc-4/use-msc2026/use-core/src/main/java/org/tzi/use/uml/ocl/type/MessageType.java: Some input files use or override a deprecated API.
[INFO] /home/xoruser/msc-4/use-msc2026/use-core/src/main/java/org/tzi/use/uml/ocl/type/MessageType.java: Recompile with -Xlint:deprecation for details.
[INFO] /home/xoruser/msc-4/use-msc2026/use-core/target/generated-sources/antlr3/org/tzi/use/parser/generator/GeneratorParser.java: Some input files use unchecked or unsafe operations.
[INFO] /home/xoruser/msc-4/use-msc2026/use-core/target/generated-sources/antlr3/org/tzi/use/parser/generator/GeneratorParser.java: Recompile with -Xlint:unchecked for details.
[INFO] 
[INFO] --- resources:3.4.0:testResources (default-testResources) @ use-core ---
[INFO] Copying 97 resources from src/test/resources to target/test-classes
[INFO] 
[INFO] --- compiler:3.15.0:testCompile (default-testCompile) @ use-core ---
[INFO] Recompiling the module because of changed dependency.
[INFO] Compiling 100 source files with javac [debug target 21 module-path] to target/test-classes
[INFO] /home/xoruser/msc-4/use-msc2026/use-core/src/test/java/org/tzi/use/uncertainty/differential/B7CorrectionsTest.java: /home/xoruser/msc-4/use-msc2026/use-core/src/test/java/org/tzi/use/uncertainty/differential/B7CorrectionsTest.java uses or overrides a deprecated API.
[INFO] /home/xoruser/msc-4/use-msc2026/use-core/src/test/java/org/tzi/use/uncertainty/differential/B7CorrectionsTest.java: Recompile with -Xlint:deprecation for details.
[INFO] 
[INFO] --- surefire:3.5.4:test (default-test) @ use-core ---
[INFO] Using auto detected provider org.apache.maven.surefire.junitplatform.JUnitPlatformProvider
[INFO] 
[INFO] -------------------------------------------------------
[INFO]  T E S T S
[INFO] -------------------------------------------------------
SLF4J(W): No SLF4J providers were found.
SLF4J(W): Defaulting to no-operation (NOP) logger implementation
SLF4J(W): See https://www.slf4j.org/codes.html#noProviders for further details.
[INFO] Running F-2: MathUtil.round saturated above 9.2e8
[INFO] Running the declared limit: NaN and the infinities
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.028 s -- in the declared limit: NaN and the infinities
[INFO] Running what the fix must not change
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.024 s -- in what the fix must not change
[INFO] Running the defect the fix removes
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.017 s -- in the defect the fix removes
[INFO] Tests run: 0, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.082 s -- in F-2: MathUtil.round saturated above 9.2e8
[INFO] Running B7 at the parser and literal-constant layer
[INFO] Running M-33: ASTUStringLiteral fell through to Object.toString()
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.042 s -- in M-33: ASTUStringLiteral fell through to Object.toString()
[INFO] Running M-32: ASTURealLiteral built two Expression graphs and installed the second
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.013 s -- in M-32: ASTURealLiteral built two Expression graphs and installed the second
[INFO] Running M-30: ExpConstUString had two unguarded operations
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.012 s -- in M-30: ExpConstUString had two unguarded operations
[INFO] Running M-29: an undefined UBoolean VALUE operand was silently accepted
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.007 s -- in M-29: an undefined UBoolean VALUE operand was silently accepted
[INFO] Tests run: 0, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.080 s -- in B7 at the parser and literal-constant layer
[INFO] Running org.tzi.use.parser.ocl.UncertainExpressionTypingTest
[INFO] Tests run: 8, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.123 s -- in org.tzi.use.parser.ocl.UncertainExpressionTypingTest
[INFO] Running org.tzi.use.parser.soil.IterationWarningTokenRotTest
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.042 s -- in org.tzi.use.parser.soil.IterationWarningTokenRotTest
[INFO] Running org.tzi.use.parser.uncertainty.USECompilerUncertaintyTest
-----------------------------------------------------------------
It's going to be executed 4 test files.
-----------------------------------------------------------------
File : UBooleanExpression.in
	Expression : 
		UBoolean(3 + 2, 1)
		-> Value must be Boolean

	Expression : 
		UBoolean(3 / 0, 1)
		-> Value must be Boolean

	Expression : 
		UBoolean(true or false, UReal(2, 3))
		-> Probability must be a Integer or Real

	Expression : 
		UBoolean(true and false, 3 / 0)
		-> null : OclVoid

	Expression : 
		UBoolean(true or false, 3 - 5)
		-> null : OclVoid

	Expression : 
		UBoolean(true or false, 23 * 3)
		-> null : OclVoid

	Expression : 
		UBoolean(true and false, 1 - 0.2)
		-> UBoolean(true, 0.2) : UBoolean

	Expression : 
		UBoolean(true or false, 1 - 0.4)
		-> UBoolean(true, 0.6) : UBoolean

	Expression : 
		UBoolean(false, 1 - 0.1)
		-> UBoolean(true, 0.1) : UBoolean

	Expression : 
		UBoolean(true or false, 0.65)
		-> UBoolean(true, 0.65) : UBoolean

	Expression : 
		UBoolean(false, 0.42)
		-> UBoolean(true, 0.58) : UBoolean

	Expression : 
		UBoolean(false, 0.5) and UBoolean(false, 0.2)
		-> UBoolean(true, 0.4) : UBoolean

	Expression : 
		UBoolean(false, 0.9) and UBoolean(true, 0.8)
		-> UBoolean(true, 0.08) : UBoolean

	Expression : 
		UBoolean(false, 0.55) and UBoolean(true, 0.49)
		-> UBoolean(true, 0.22) : UBoolean

	Expression : 
		true and false
		-> false : Boolean

	Expression : 
		false and UBoolean(false, 0.49)
		-> UBoolean(true, 0.0) : UBoolean

	Expression : 
		UBoolean(false, 0.79) and false
		-> UBoolean(true, 0.0) : UBoolean

	Expression : 
		UBoolean(false, 0.79) and true
		-> UBoolean(true, 0.21) : UBoolean

	Expression : 
		UBoolean(true, 0.79) and true
		-> UBoolean(true, 0.79) : UBoolean

	Expression : 
		Undefined and Undefined
		-> null : OclVoid

	Expression : 
		UBoolean(true, 0.9) and Undefined
		-> null : OclVoid

	Expression : 
		true and Undefined
		-> null : OclVoid

	Expression : 
		Undefined and UBoolean(false, 0.9)
		-> null : OclVoid

	Expression : 
		Undefined and false
		-> false : Boolean

	Expression : 
		Undefined and UBoolean(true, 0)
		-> UBoolean(true, 0.0) : UBoolean

	Expression : 
		UBoolean(false, 1) and Undefined
		-> UBoolean(true, 0.0) : UBoolean

	Expression : 
		( UBoolean(true, 0.3) and UBoolean(true, 0.8) ).equals( (UBoolean(true, 0.8) and UBoolean(true, 0.3)) )
		-> true : Boolean

	Expression : 
		( true and UBoolean(true, 0.8) ).equals( (UBoolean(true, 0.8) and true) )
		-> true : Boolean

	Expression : 
		( Undefined and UBoolean(true, 0.8) ).equals( (UBoolean(true, 0.8) and Undefined) )
		-> true : Boolean

	Expression : 
		( UBoolean(true, 0.4) and (UBoolean(false, 0.55) and UBoolean(true, 0.8)) ).equals(( (UBoolean(true, 0.4) and UBoolean(false, 0.55)) and UBoolean(true, 0.8) ))
		-> true : Boolean

	Expression : 
		( false and (UBoolean(false, 0.55) and UBoolean(true, 0.8)) ).equals(( (false and UBoolean(false, 0.55)) and UBoolean(true, 0.8) ))
		-> true : Boolean

	Expression : 
		( true and (Undefined and UBoolean(true, 0.8)) ).equals(( (true and Undefined) and UBoolean(true, 0.8) ))
		-> true : Boolean

	Expression : 
		( UBoolean(false, 0.4) and UBoolean(true, 1) ).equals( UBoolean(false, 0.4) )
		-> true : Boolean

	Expression : 
		( UBoolean(false, 0.4) and true ).equals( UBoolean(false, 0.4) )
		-> true : Boolean

	Expression : 
		UBoolean(false, 0.45) or UBoolean(false, 0.76)
		-> UBoolean(true, 0.658) : UBoolean

	Expression : 
		UBoolean(false, 0.45) or UBoolean(true, 0.37)
		-> UBoolean(true, 0.717) : UBoolean

	Expression : 
		UBoolean(true, 0.45) or UBoolean(true, 0.76)
		-> UBoolean(true, 0.868) : UBoolean

	Expression : 
		true or false
		-> true : Boolean

	Expression : 
		false or UBoolean(false, 0.49)
		-> UBoolean(true, 0.51) : UBoolean

	Expression : 
		UBoolean(false, 0.79) or false
		-> UBoolean(true, 0.21) : UBoolean

	Expression : 
		UBoolean(true, 0.79) or true
		-> UBoolean(true, 1.0) : UBoolean

	Expression : 
		Undefined or Undefined
		-> null : OclVoid

	Expression : 
		UBoolean(true, 0.9) or Undefined
		-> null : OclVoid

	Expression : 
		true or Undefined
		-> true : Boolean

	Expression : 
		Undefined or UBoolean(false, 0.9)
		-> null : OclVoid

	Expression : 
		Undefined or false
		-> null : OclVoid

	Expression : 
		Undefined or UBoolean(true, 1)
		-> UBoolean(true, 1.0) : UBoolean

	Expression : 
		UBoolean(false, 1) or Undefined
		-> null : OclVoid

	Expression : 
		( UBoolean(true, 0.5) or UBoolean(true, 0.16) ).equals( UBoolean(true, 0.16) or UBoolean(true, 0.5) )
		-> true : Boolean

	Expression : 
		( false or UBoolean(true, 0.16) ).equals( UBoolean(true, 0.16) or false )
		-> true : Boolean

	Expression : 
		( Undefined or UBoolean(true, 0.16) ).equals( UBoolean(true, 0.16) or Undefined )
		-> true : Boolean

	Expression : 
		( UBoolean(false, 0.1) or (UBoolean(false, 0.4) or UBoolean(true, 0.6)) ).equals ( (UBoolean(false, 0.1) or UBoolean(false, 0.4)) or UBoolean(true, 0.6) )
		-> true : Boolean

	Expression : 
		( true or (UBoolean(false, 0.4) or UBoolean(true, 0.6)) ).equals ( (true or UBoolean(false, 0.4)) or UBoolean(true, 0.6) )
		-> true : Boolean

	Expression : 
		( true or (Undefined or UBoolean(true, 0.6)) ).equals ( (true or Undefined) or UBoolean(true, 0.6) )
		-> true : Boolean

	Expression : 
		( UBoolean(true, 0.4) or UBoolean(true, 0) ).equals( UBoolean(true, 0.4) )
		-> true : Boolean

	Expression : 
		( UBoolean(false, 0.4) or UBoolean(true, 0) ).equals( UBoolean(false, 0.4) )
		-> true : Boolean

	Expression : 
		( true or UBoolean(true, 0) ).equals( UBoolean(true, 1) )
		-> true : Boolean

	Expression : 
		( Undefined or UBoolean(true, 0) ).equals( Undefined )
		-> true : Boolean

	Expression : 
		not Undefined
		-> null : OclVoid

	Expression : 
		not UBoolean(true, 0)
		-> UBoolean(true, 1.0) : UBoolean

	Expression : 
		not UBoolean(true, 1)
		-> UBoolean(true, 0.0) : UBoolean

	Expression : 
		not UBoolean(true, 0.2)
		-> UBoolean(true, 0.8) : UBoolean

	Expression : 
		not UBoolean(true, 0.5)
		-> UBoolean(true, 0.5) : UBoolean

	Expression : 
		not UBoolean(true, 0.8)
		-> UBoolean(true, 0.2) : UBoolean

	Expression : 
		not UBoolean(false, 0)
		-> UBoolean(true, 0.0) : UBoolean

	Expression : 
		not UBoolean(false, 1)
		-> UBoolean(true, 1.0) : UBoolean

	Expression : 
		not UBoolean(false, 0.2)
		-> UBoolean(true, 0.2) : UBoolean

	Expression : 
		not UBoolean(false, 0.5)
		-> UBoolean(true, 0.5) : UBoolean

	Expression : 
		not UBoolean(false, 0.8)
		-> UBoolean(true, 0.8) : UBoolean

	Expression : 
		( not ( not UBoolean(false, 0.2)) ).equals( UBoolean(false, 0.2) )
		-> true : Boolean

	Expression : 
		( not ( not UBoolean(true, 0.2)) ).equals( UBoolean(true, 0.2) )
		-> true : Boolean

	Expression : 
		( not (UBoolean(true, 0.36) or UBoolean(true, 0.39)) ).equals( (not UBoolean(true, 0.36)) and (not UBoolean(true, 0.39)) )
		-> true : Boolean

	Expression : 
		( not (UBoolean(false, 0.8) or UBoolean(true, 0.39)) ).equals( (not UBoolean(false, 0.8)) and (not UBoolean(true, 0.39)) )
		-> true : Boolean

	Expression : 
		( not (UBoolean(false, 0.04) or UBoolean(false, 0.9)) ).equals( (not UBoolean(false, 0.04)) and (not UBoolean(false, 0.9)) )
		-> true : Boolean

	Expression : 
		( not (UBoolean(true, 0.36) and UBoolean(true, 0.39)) ).equals( (not UBoolean(true, 0.36)) or (not UBoolean(true, 0.39)) )
		-> true : Boolean

	Expression : 
		( not (UBoolean(false, 0.8) and UBoolean(true, 0.39)) ).equals( (not UBoolean(false, 0.8)) or (not UBoolean(true, 0.39)) )
		-> true : Boolean

	Expression : 
		( not (UBoolean(false, 0.04) and UBoolean(false, 0.9)) ).equals( (not UBoolean(false, 0.04)) or (not UBoolean(false, 0.9)) )
		-> true : Boolean

	Expression : 
		UBoolean(false, 0.4) xor UBoolean(false, 0.2)
		-> UBoolean(true, 0.2) : UBoolean

	Expression : 
		UBoolean(false, 0.2) xor UBoolean(true, 0.3)
		-> UBoolean(true, 0.5) : UBoolean

	Expression : 
		UBoolean(true, 0.1) xor UBoolean(true, 0.1)
		-> UBoolean(true, 0.0) : UBoolean

	Expression : 
		UBoolean(false, 0) xor UBoolean(false, 1)
		-> UBoolean(true, 1.0) : UBoolean

	Expression : 
		true xor false
		-> true : Boolean

	Expression : 
		false xor UBoolean(false, 0.5)
		-> UBoolean(true, 0.5) : UBoolean

	Expression : 
		UBoolean(false, 0.2) xor false
		-> UBoolean(true, 0.8) : UBoolean

	Expression : 
		UBoolean(false, 0.6) xor true
		-> UBoolean(true, 0.6) : UBoolean

	Expression : 
		UBoolean(true,  0.3) xor true
		-> UBoolean(true, 0.7) : UBoolean

	Expression : 
		UBoolean(true,  0.0) xor true
		-> UBoolean(true, 1.0) : UBoolean

	Expression : 
		Undefined xor Undefined
		-> null : OclVoid

	Expression : 
		UBoolean(true, 0.5) xor Undefined
		-> null : OclVoid

	Expression : 
		Undefined xor UBoolean(false, 0.4)
		-> null : OclVoid

	Expression : 
		UBoolean(false, 0.2).equivalent(UBoolean(false, 0.4))
		-> UBoolean(true, 0.8) : UBoolean

	Expression : 
		UBoolean(false, 0.8).equivalent(UBoolean(true, 0.5))
		-> UBoolean(true, 0.7) : UBoolean

	Expression : 
		UBoolean(true, 0.34).equivalent(UBoolean(true, 0.56))
		-> UBoolean(true, 0.78) : UBoolean

	Expression : 
		true.equivalent(false)
		-> false : Boolean

	Expression : 
		true.equivalent(true)
		-> true : Boolean

	Expression : 
		false.equivalent(true)
		-> false : Boolean

	Expression : 
		false.equivalent(false)
		-> true : Boolean

	Expression : 
		false.equivalent(UBoolean(false, 0.49))
		-> UBoolean(true, 0.49) : UBoolean

	Expression : 
		UBoolean(false, 0.79).equivalent(false)
		-> UBoolean(true, 0.79) : UBoolean

	Expression : 
		UBoolean(true, 0.79).equivalent( true )
		-> UBoolean(true, 0.79) : UBoolean

	Expression : 
		UBoolean(true, 0.2).value()
		-> true : Boolean

	Expression : 
		UBoolean(true, 0.55).value()
		-> true : Boolean

	Expression : 
		UBoolean(true, 0.9).value()
		-> true : Boolean

	Expression : 
		UBoolean(true, 0).confidence()
		-> 0.0 : Real

	Expression : 
		UBoolean(true, 0.5).confidence()
		-> 0.5 : Real

	Expression : 
		UBoolean(true, 1).confidence()
		-> 1.0 : Real

	Expression : 
		UBoolean(false, 0.2) = UBoolean(false, 0.4)
		-> UBoolean(true, 0.8) : UBoolean

	Expression : 
		UBoolean(false, 0.8) = UBoolean(true, 0.5)
		-> UBoolean(true, 0.7) : UBoolean

	Expression : 
		UBoolean(true, 0.34) = UBoolean(true, 0.56)
		-> UBoolean(true, 0.78) : UBoolean

	Expression : 
		false = UBoolean(false, 0.49)
		-> UBoolean(true, 0.49) : UBoolean

	Expression : 
		UBoolean(false, 0.79) = false
		-> UBoolean(true, 0.79) : UBoolean

	Expression : 
		UBoolean(true, 0.79) = true
		-> UBoolean(true, 0.79) : UBoolean

	Expression : 
		UBoolean(true, 0.2) = Undefined
		-> false : Boolean

	Expression : 
		UBoolean(true, 0.2) = null
		-> false : Boolean

	Expression : 
		UBoolean(true, 0).toBoolean()
		-> false : Boolean

	Expression : 
		UBoolean(true, 0.49).toBoolean()
		-> false : Boolean

	Expression : 
		UBoolean(true, 0.5).toBoolean()
		-> true : Boolean

	Expression : 
		UBoolean(true, 1).toBoolean()
		-> true : Boolean

-----------------------------------------------------------------
File : UCollectionOperations.in
	Expression : 
		Set{UReal(2, 0.5), 1, 2.5, 3.2, UReal(3.5, 0.25)}->sum()
		-> UReal(12.2, 0.5590169944) : UReal

	Expression : 
		Sequence{UReal(52, 0.5), 3.2, 2, UReal(-53, 20), UReal(20, 5)}->sum()
		-> UReal(24.2, 20.6215906273) : UReal

	Expression : 
		Set{1, 2, UReal(2,5)}->forAll(e | e >= 1)
		-> UBoolean(true, 0.579) : UBoolean

	Expression : 
		Set{UReal(1, 0.5),UReal(1,0.75), 1.2}->forAll(e | e >= 1.2)
		-> UBoolean(true, 0.136) : UBoolean

	Expression : 
		Set{UReal(1, 0.5), 3}->forAll(e | e < 0)
		-> UBoolean(true, 0.0) : UBoolean

	Expression : 
		( Set{1, UReal(1,0.78)}->forAll(e | e > 0) ).equals( (1 > 0) and (UReal(1, 0.78) > 0) )
		-> true : Boolean

	Expression : 
		( Set{1, UReal(1,0.78)}->forAll(e | e < 0) ).equals( (1 < 0) and (UReal(1, 0.78) < 0) )
		-> true : Boolean

	Expression : 
		Set{0, 1, UReal(3,0.5)}->exists(e | e = 0)
		-> UBoolean(true, 1.0) : UBoolean

	Expression : 
		Set{0, 1, UReal(3, 0.5)}->exists(e | e >= 3)
		-> UBoolean(true, 0.5) : UBoolean

	Expression : 
		( Set{1, UReal(1,0.2)}->exists(e | e >= 1.1)).equals( (1 >= 1.1) or (UReal(1,0.2) >= 1.1))
		-> true : Boolean

	Expression : 
		( Set{1, UReal(1,0.1)}->exists(a,b| a <> b and a = b) ).equals( (1 <> 1 and 1 = 1) or (1 <> UReal(1,0.1) and 1 = UReal(1,0.1)))
		-> true : Boolean

	Expression : 
		Set{UReal(2, 0.5), 1, 2.5, 3.2, UReal(3.5, 0.25)}->includes(2.5)
		-> UBoolean(true, 1.0) : UBoolean

	Expression : 
		Set{UReal(2, 0.5), 1, 2.5, 3.2, UReal(3.5, 0.25)}->includes(UReal(2, 0.2))
		-> UBoolean(true, 0.585) : UBoolean

	Expression : 
		Set{UReal(2, 0.35), UReal(2, 0.3)}->includes(UReal(2, 0.29))
		-> UBoolean(true, 0.984) : UBoolean

	Expression : 
		Set{}->includes(UReal(2, 3))
		-> UBoolean(true, 0.0) : UBoolean

	Expression : 
		Set{Undefined}->includes(UReal(2, 3))
		-> UBoolean(true, 0.0) : UBoolean

	Expression : 
		Set{}->includesAll(Set{UReal(2, 3)})
		-> UBoolean(true, 0.0) : UBoolean

	Expression : 
		Set{UReal(2, 0.5), 1, 2.5, 3.2, UReal(3.5, 0.25)}->includesAll(Set{2.5, UReal(3.5, 0.15)})
		-> UBoolean(true, 0.758) : UBoolean

	Expression : 
		Set{UReal(2, 0.5), 1, 2.5, 3.2, UReal(3.5, 0.25)}->includesAll(Set{UReal(2, 0.5), 1, 2.5, 3.2, UReal(3.5, 0.25)})
		-> UBoolean(true, 1.0) : UBoolean

	Expression : 
		Set{UReal(2, 0.3)}->includesAll(Set{1, 2, 3})
		-> UBoolean(true, 0.0) : UBoolean

	Expression : 
		let A = Set{UReal(2, 0.5), 1, 2.5, 3.2, UReal(3.5, 0.25)} in 
let B = Set{UReal(2, 0.5), 1, 3.2} in (B->forAll(e | A->includes(e))).equals(A->includesAll(B))
		-> true : Boolean

	Expression : 
		let A = Set{UReal(2, 0.5), 1, 2.5, 5.3, UReal(3.5, 0.25)} in 
let B = Set{UReal(2, 0.5), 1, 3.2} in (B->forAll(e | A->includes(e))).equals(A->includesAll(B))
		-> true : Boolean

	Expression : 
		let A = Set{UReal(2, 0.5), 1, 2.5, 3.2, UReal(3.5, 0.25)} in 
let B = Set{UReal(2, 0.15), UReal(3.4, 0.25)} in (B->forAll(e | A->includes(e))).equals(A->includesAll(B))
		-> true : Boolean

	Expression : 
		Set{}->excludes(UReal(1, 2))
		-> UBoolean(true, 1.0) : UBoolean

	Expression : 
		Set{UReal(2, 0.5), 1, 2.5, 3.2, UReal(3.5, 0.25)}->excludes(UReal(59,2))
		-> UBoolean(true, 1.0) : UBoolean

	Expression : 
		Set{UReal(2, 0.5), 1, 2.5, 3.2, UReal(3.5, 0.25)}->excludes(UReal(3.5, 0.25))
		-> UBoolean(true, 0.0) : UBoolean

	Expression : 
		let A = Set{UReal(2, 0.5), 1, 2.5, 3.2, UReal(3.5, 0.25)} in 
let B = UReal(3, 2) in ( A->forAll(e | e <> B) ).equals( A->excludes(B))
		-> true : Boolean

	Expression : 
		let A = Set{UReal(2, 0.5), 1, 2.5, 3.2, UReal(3.5, 0.25)} in 
let B = UReal(0, 2) in ( A->forAll(e | e <> B) ).equals( A->excludes(B))
		-> true : Boolean

	Expression : 
		let A = Set{UReal(2, 0.5), 1, 2.5, 3.2, UReal(3.5, 0.25)} in 
let B = UReal(59, 2) in ( A->forAll(e | e <> B) ).equals( A->excludes(B))
		-> true : Boolean

	Expression : 
		Set{}->excludesAll(Set{UReal(2, 3)})
		-> UBoolean(true, 1.0) : UBoolean

	Expression : 
		Set{UReal(2, 0.5), 1, 2.5, 3.2, UReal(3.5, 0.25)}->excludesAll(Set{UReal(59,3),UReal(-310,9)})
		-> UBoolean(true, 1.0) : UBoolean

	Expression : 
		Set{UReal(2, 0.5), 1, 2.5, 3.2, UReal(3.5, 0.25)}->excludesAll(Set{UReal(3.5, 0.25)})
		-> UBoolean(true, 0.0) : UBoolean

	Expression : 
		let A = Set{UReal(2, 0.5), 1, 2.5, 3.2, UReal(3.5, 0.25)} in 
let B = Set{UReal(2.75, 1), 1} in 
( A->excludesAll(B) ).equals( B->forAll(b | A->excludes(b)) )
		-> true : Boolean

	Expression : 
		let A = Set{UReal(2, 0.5), 1, 2.5, 3.2, UReal(3.5, 0.25)} in 
let B = Set{UReal(1, 3), UReal(5, 2)} in 
( A->excludesAll(B) ).equals( B->forAll(b | A->excludes(b)) )
		-> true : Boolean

	Expression : 
		let A = Set{UReal(2, 0.5), 1, 2.5, 3.2, UReal(3.5, 0.25)} in let B = Set{UReal(-11, 3), UReal(55, 2)} in ( A->excludesAll(B) ).equals( B->forAll(b | A->excludes(b)) )
		-> true : Boolean

	Expression : 
		Set{UReal(2, 0.5), 2.5, 3.2, 1, UReal(3, 0.25)}->uSelect(e | e >= 2)
		-> Set{2.5,UReal(3.0, 0.25),3.2} : Set(UReal)

	Expression : 
		Set{UReal(2, 0.5), 2.5, 3.2, 1, UReal(3, 0.25)}->uSelect(e | e <= 2)
		-> Set{1,UReal(2.0, 0.5)} : Set(UReal)

	Expression : 
		let A = Set{2, 3, UReal(3, 0.5)} in (A->iterate(v; acc : Set(UReal) = Set {} | if (v > 2).toBoolean() then acc->including(v) else acc endif) )->equals(A->uSelect(e|e>2))
		-> true : Boolean

	Expression : 
		let A = Sequence{UReal(-3,5), 2.3, UReal(2,3), UReal(67,3), -50} in 
(A->iterate(v; acc : Sequence(UReal) = Sequence {} | if (v > 2).toBoolean() then acc->including(v) else acc endif) )->equals(A->uSelect(e|e>2))
		-> true : Boolean

	Expression : 
		let A = Bag{2.3, UReal(2,3), UReal(67,3)} in 
(A->iterate(v; acc : Bag(UReal) = Bag {} | if (v > 2).toBoolean() then acc->including(v) else acc endif) )->equals(A->uSelect(e|e>2))
		-> true : Boolean

	Expression : 
		Set{UReal(2, 0.5), 2.5, 3.2, 1, UReal(3, 0.25)}->uSelectC(e | e >= 2, 0.49)
		-> Set{2.5,UReal(3.0, 0.25),3.2,UReal(2.0, 0.5)} : Set(UReal)

	Expression : 
		Set{UReal(2, 0.5), 2.5, 3.2, 1, UReal(3, 0.25)}->uSelectC(e | e <= 2, 0.49)
		-> Set{1,UReal(2.0, 0.5)} : Set(UReal)

	Expression : 
		let A = Set{UReal(2, 0.5), 2.5, 3.2, 1, UReal(3, 0.25)} in let C = 0.7 in 
(A->iterate (v ; acc : Set(UReal) = Set {} | if (v >= 2). toBooleanC (C) then acc -> including (v) else acc endif ) )->equals( A->uSelectC(e | e >= 2, C) )
		-> true : Boolean

	Expression : 
		let A = Set{UReal(52, 0.5), 3.2, 2, UReal(-53, 20), UReal(20, 5)} in let C = 0.45 in 
(A->iterate (v ; acc : Set(UReal) = Set {} | if (v >= 2). toBooleanC (C) then acc -> including (v) else acc endif ) )->equals( A->uSelectC(e | e >= 2, C) )
		-> true : Boolean

-----------------------------------------------------------------
File : UIntegerExpression.in
	Expression : 
		UInteger(-5, 0.0)
		-> UInteger(-5, 0.0) : UInteger

	Expression : 
		UInteger(-5, 0.5)
		-> UInteger(-5, 0.5) : UInteger

	Expression : 
		UInteger(-5, -0.5)
		-> UInteger(-5, 0.5) : UInteger

	Expression : 
		UInteger(-5, 2)
		-> UInteger(-5, 2.0) : UInteger

	Expression : 
		UInteger(-5, -5)
		-> UInteger(-5, 5.0) : UInteger

	Expression : 
		UInteger(3, 39)
		-> UInteger(3, 39.0) : UInteger

	Expression : 
		UInteger(0, 0)
		-> UInteger(0, 0.0) : UInteger

	Expression : 
		UInteger(Undefined, Undefined)
		-> null : OclVoid

	Expression : 
		UInteger(Undefined, 0.34)
		-> null : OclVoid

	Expression : 
		UInteger(5, Undefined)
		-> null : OclVoid

	Expression : 
		UInteger(3 + 4*2-3, UReal(4, 3.3).value() + 1)
		-> UInteger(8, 5.0) : UInteger

	Expression : 
		UInteger(3, 3.5).value()
		-> 3 : Integer

	Expression : 
		UInteger(0, 2.3).value()
		-> 0 : Integer

	Expression : 
		UInteger(-5, 0.2).value()
		-> -5 : Integer

	Expression : 
		UInteger(Undefined, Undefined).value()
		-> null : OclVoid

	Expression : 
		UInteger(3, Undefined).value()
		-> null : OclVoid

	Expression : 
		UInteger(Undefined, 3).value()
		-> null : OclVoid

	Expression : 
		UInteger(3, 5).setValue(2)
		-> UInteger(2, 5.0) : UInteger

	Expression : 
		UInteger(-2, 4).setValue(0)
		-> UInteger(0, 4.0) : UInteger

	Expression : 
		UInteger(0, 3).setValue(-55)
		-> UInteger(-55, 3.0) : UInteger

	Expression : 
		UInteger(3, 3.5).uncertainty()
		-> 3.5 : Real

	Expression : 
		UInteger(0, 0).uncertainty()
		-> 0.0 : Real

	Expression : 
		UInteger(-5, 0.2).uncertainty()
		-> 0.2 : Real

	Expression : 
		UInteger(Undefined, Undefined).uncertainty()
		-> null : OclVoid

	Expression : 
		UInteger(3, Undefined).uncertainty()
		-> null : OclVoid

	Expression : 
		UInteger(Undefined, 3).uncertainty()
		-> null : OclVoid

	Expression : 
		UInteger(0, 3).setUncertainty(-5)
		-> UInteger(0, 5.0) : UInteger

	Expression : 
		UInteger(5, 2).setUncertainty(0)
		-> UInteger(5, 0.0) : UInteger

	Expression : 
		UInteger(0, 3).setUncertainty(5)
		-> UInteger(0, 5.0) : UInteger

	Expression : 
		UInteger(0, 3).setUncertainty(5.3)
		-> UInteger(0, 5.3) : UInteger

	Expression : 
		UInteger(0, 3).setUncertainty(0.2)
		-> UInteger(0, 0.2) : UInteger

	Expression : 
		UInteger(0, 3).setUncertainty(-0.3)
		-> UInteger(0, 0.3) : UInteger

	Expression : 
		UInteger(0, 3).setUncertainty(0.0)
		-> UInteger(0, 0.0) : UInteger

	Expression : 
		UInteger(3, 0.5).toUReal()
		-> UReal(3.0, 0.5) : UReal

	Expression : 
		UInteger(3, -0.5).toUReal()
		-> UReal(3.0, 0.5) : UReal

	Expression : 
		UInteger(0, 0).toUReal()
		-> UReal(0.0, 0.0) : UReal

	Expression : 
		UInteger(-53, 5).toUReal()
		-> UReal(-53.0, 5.0) : UReal

	Expression : 
		UInteger(3, 0.3).toInteger()
		-> 3 : Integer

	Expression : 
		UInteger(0, 4).toInteger()
		-> 0 : Integer

	Expression : 
		UInteger(-5, 5).toInteger()
		-> -5 : Integer

	Expression : 
		UInteger(3, 0.3).toReal()
		-> 3.0 : Real

	Expression : 
		UInteger(0, 0.5).toReal()
		-> 0.0 : Real

	Expression : 
		UInteger(-3, -0.5).toReal()
		-> -3.0 : Real

	Expression : 
		UInteger(5, 0.3).toString()
		-> 'UInteger(5, 0.3)' : String

	Expression : 
		UInteger(5, -0.3).toString()
		-> 'UInteger(5, 0.3)' : String

	Expression : 
		UInteger(-5, 0.3).toString()
		-> 'UInteger(-5, 0.3)' : String

	Expression : 
		UInteger(2, 3).abs()
		-> UInteger(2, 3.0) : UInteger

	Expression : 
		UInteger(0, 3).abs()
		-> UInteger(0, 3.0) : UInteger

	Expression : 
		UInteger(-2, 3).abs()
		-> UInteger(2, 3.0) : UInteger

	Expression : 
		UInteger(-3, 2.3).sqrt()
		-> null : OclVoid

	Expression : 
		UInteger(0, 0.0).sqrt()
		-> UInteger(0, 0.0) : UInteger

	Expression : 
		UInteger(4, 0.0).sqrt()
		-> UInteger(2, 0.0) : UInteger

	Expression : 
		UInteger(4, 2).sqrt()
		-> UInteger(2, 0.5) : UInteger

	Expression : 
		UInteger(0, 0).power(0)
		-> null : OclVoid

	Expression : 
		UInteger(0, 0).power(3)
		-> UInteger(0, 0.0) : UInteger

	Expression : 
		UInteger(0, 0).power(-2)
		-> null : OclVoid

	Expression : 
		UInteger(0, 0).power(3.5)
		-> UInteger(0, 0.0) : UInteger

	Expression : 
		UInteger(0, 2).power(0)
		-> null : OclVoid

	Expression : 
		UInteger(0, 4).power(3)
		-> UInteger(0, 0.0) : UInteger

	Expression : 
		UInteger(0, 3).power(-3)
		-> null : OclVoid

	Expression : 
		UInteger(0, 1).power(3.5)
		-> UInteger(0, 0.0) : UInteger

	Expression : 
		UInteger(3, 0).power(0)
		-> UInteger(1, 0.0) : UInteger

	Expression : 
		UInteger(2, 0).power(3)
		-> UInteger(8, 0.0) : UInteger

	Expression : 
		UInteger(4, 0).power(-2)
		-> UInteger(0, 0.0625) : UInteger

	Expression : 
		UInteger(4, 0).power(1.5)
		-> UInteger(8, 0.0) : UInteger

	Expression : 
		UInteger(2, 4).power(4)
		-> UInteger(16, 128.0) : UInteger

	Expression : 
		UInteger(1, 3).power(-2)
		-> UInteger(1, 6.0) : UInteger

	Expression : 
		UInteger(1, 2).power(0.25)
		-> UInteger(1, 0.5) : UInteger

	Expression : 
		UInteger(3, 2.3).neg()
		-> UInteger(-3, 2.3) : UInteger

	Expression : 
		UInteger(0, 2.3).neg()
		-> UInteger(0, 2.3) : UInteger

	Expression : 
		UInteger(-3, 2.3).neg()
		-> UInteger(3, 2.3) : UInteger

	Expression : 
		UInteger(-9, 0) + UInteger(-9, 0)
		-> UInteger(-18, 0.0) : UInteger

	Expression : 
		UInteger(-7, 0) + UInteger(-7, 8)
		-> UInteger(-14, 8.0) : UInteger

	Expression : 
		UInteger(-10, 0) + UInteger(0, 0)
		-> UInteger(-10, 0.0) : UInteger

	Expression : 
		UInteger(-8, 0) + UInteger(3, 5)
		-> UInteger(-5, 5.0) : UInteger

	Expression : 
		UInteger(-6, 8) + UInteger(-6, 0)
		-> UInteger(-12, 8.0) : UInteger

	Expression : 
		UInteger(-9, 3) + UInteger(-9, 4)
		-> UInteger(-18, 5.0) : UInteger

	Expression : 
		UInteger(-9, 8) + UInteger(4, 0)
		-> UInteger(-5, 8.0) : UInteger

	Expression : 
		UInteger(-3, 3) + UInteger(4, 4)
		-> UInteger(1, 5.0) : UInteger

	Expression : 
		UInteger(0, 0) + UInteger(0, 0)
		-> UInteger(0, 0.0) : UInteger

	Expression : 
		UInteger(0, 0) + UInteger(0, 0)
		-> UInteger(0, 0.0) : UInteger

	Expression : 
		UInteger(0, 0) + UInteger(9, 0)
		-> UInteger(9, 0.0) : UInteger

	Expression : 
		UInteger(0, 0) + UInteger(8, 4)
		-> UInteger(8, 4.0) : UInteger

	Expression : 
		UInteger(0, 8) + UInteger(0, 0)
		-> UInteger(0, 8.0) : UInteger

	Expression : 
		UInteger(0, 3) + UInteger(0, 4)
		-> UInteger(0, 5.0) : UInteger

	Expression : 
		UInteger(0, 6) + UInteger(8, 0)
		-> UInteger(8, 6.0) : UInteger

	Expression : 
		UInteger(0, 3) + UInteger(5, 4)
		-> UInteger(5, 5.0) : UInteger

	Expression : 
		UInteger(9, 0) + UInteger(9, 0)
		-> UInteger(18, 0.0) : UInteger

	Expression : 
		UInteger(7, 0) + UInteger(7, 0)
		-> UInteger(14, 0.0) : UInteger

	Expression : 
		UInteger(10, 0) + UInteger(8, 0)
		-> UInteger(18, 0.0) : UInteger

	Expression : 
		UInteger(8, 0) + UInteger(8, 7)
		-> UInteger(16, 7.0) : UInteger

	Expression : 
		UInteger(6, 5) + UInteger(6, 0)
		-> UInteger(12, 5.0) : UInteger

	Expression : 
		UInteger(9, 3) + UInteger(9, 4)
		-> UInteger(18, 5.0) : UInteger

	Expression : 
		UInteger(9, 1) + UInteger(8, 0)
		-> UInteger(17, 1.0) : UInteger

	Expression : 
		UInteger(3, 3) + UInteger(4, 4)
		-> UInteger(7, 5.0) : UInteger

	Expression : 
		UInteger(-9, 0) + UReal(-9, 0)
		-> UReal(-18.0, 0.0) : UReal

	Expression : 
		UInteger(-7, 0) + UReal(-7, 8)
		-> UReal(-14.0, 8.0) : UReal

	Expression : 
		UInteger(-10, 0) + UReal(0, 0)
		-> UReal(-10.0, 0.0) : UReal

	Expression : 
		UInteger(-8, 0) + UReal(3, 5)
		-> UReal(-5.0, 5.0) : UReal

	Expression : 
		UInteger(-6, 8) + UReal(-6, 0)
		-> UReal(-12.0, 8.0) : UReal

	Expression : 
		UInteger(-9, 3) + UReal(-9, 4)
		-> UReal(-18.0, 5.0) : UReal

	Expression : 
		UInteger(-9, 8) + UReal(4, 0)
		-> UReal(-5.0, 8.0) : UReal

	Expression : 
		UInteger(-3, 3) + UReal(4, 4)
		-> UReal(1.0, 5.0) : UReal

	Expression : 
		UInteger(0, 0) + UReal(0, 0)
		-> UReal(0.0, 0.0) : UReal

	Expression : 
		UInteger(0, 0) + UReal(0, 0)
		-> UReal(0.0, 0.0) : UReal

	Expression : 
		UInteger(0, 0) + UReal(9, 0)
		-> UReal(9.0, 0.0) : UReal

	Expression : 
		UInteger(0, 0) + UReal(8, 4)
		-> UReal(8.0, 4.0) : UReal

	Expression : 
		UInteger(0, 8) + UReal(0, 0)
		-> UReal(0.0, 8.0) : UReal

	Expression : 
		UInteger(0, 3) + UReal(0, 4)
		-> UReal(0.0, 5.0) : UReal

	Expression : 
		UInteger(0, 6) + UReal(8, 0)
		-> UReal(8.0, 6.0) : UReal

	Expression : 
		UInteger(0, 3) + UReal(5, 4)
		-> UReal(5.0, 5.0) : UReal

	Expression : 
		UInteger(9, 0) + UReal(9, 0)
		-> UReal(18.0, 0.0) : UReal

	Expression : 
		UInteger(7, 0) + UReal(7, 0)
		-> UReal(14.0, 0.0) : UReal

	Expression : 
		UInteger(10, 0) + UReal(8, 0)
		-> UReal(18.0, 0.0) : UReal

	Expression : 
		UInteger(8, 0) + UReal(8, 7)
		-> UReal(16.0, 7.0) : UReal

	Expression : 
		UInteger(6, 5) + UReal(6, 0)
		-> UReal(12.0, 5.0) : UReal

	Expression : 
		UInteger(9, 3) + UReal(9, 4)
		-> UReal(18.0, 5.0) : UReal

	Expression : 
		UInteger(9, 1) + UReal(8, 0)
		-> UReal(17.0, 1.0) : UReal

	Expression : 
		UInteger(3, 3) + UReal(4, 4)
		-> UReal(7.0, 5.0) : UReal

	Expression : 
		UInteger(-3, 0) + -3.0
		-> UReal(-6.0, 0.0) : UReal

	Expression : 
		UInteger(-6, 0) + -1.2
		-> UReal(-7.2, 0.0) : UReal

	Expression : 
		UInteger(-5, 3) + -5.0
		-> UReal(-10.0, 3.0) : UReal

	Expression : 
		UInteger(-8, 5) + -2.0
		-> UReal(-10.0, 5.0) : UReal

	Expression : 
		UInteger(0, 0) + 0.0
		-> UReal(0.0, 0.0) : UReal

	Expression : 
		UInteger(0, 0) + 3.0
		-> UReal(3.0, 0.0) : UReal

	Expression : 
		UInteger(0, 3) + 0.0
		-> UReal(0.0, 3.0) : UReal

	Expression : 
		UInteger(0, 5) + -5.0
		-> UReal(-5.0, 5.0) : UReal

	Expression : 
		UInteger(5, 0) + 5.0
		-> UReal(10.0, 0.0) : UReal

	Expression : 
		UInteger(3, 0) + 0.6
		-> UReal(3.6, 0.0) : UReal

	Expression : 
		UInteger(7, 3) + 7.0
		-> UReal(14.0, 3.0) : UReal

	Expression : 
		UInteger(2, 5) + 0.5
		-> UReal(2.5, 5.0) : UReal

	Expression : 
		UInteger(-3, 0) + -3
		-> UInteger(-6, 0.0) : UInteger

	Expression : 
		UInteger(-6, 0) + -12
		-> UInteger(-18, 0.0) : UInteger

	Expression : 
		UInteger(-5, 3) + -5
		-> UInteger(-10, 3.0) : UInteger

	Expression : 
		UInteger(-8, 5) + -2
		-> UInteger(-10, 5.0) : UInteger

	Expression : 
		UInteger(0, 0) + 0
		-> UInteger(0, 0.0) : UInteger

	Expression : 
		UInteger(0, 0) + 3
		-> UInteger(3, 0.0) : UInteger

	Expression : 
		UInteger(0, 3) + 0
		-> UInteger(0, 3.0) : UInteger

	Expression : 
		UInteger(0, 5) + -5
		-> UInteger(-5, 5.0) : UInteger

	Expression : 
		UInteger(5, 0) + 5
		-> UInteger(10, 0.0) : UInteger

	Expression : 
		UInteger(3, 0) + 56
		-> UInteger(59, 0.0) : UInteger

	Expression : 
		UInteger(7, 3) + 7
		-> UInteger(14, 3.0) : UInteger

	Expression : 
		UInteger(2, 5) + 65
		-> UInteger(67, 5.0) : UInteger

	Expression : 
		( UInteger(2, 5) + UInteger(0, 0) ).equals( UInteger(2, 5) )
		-> true : Boolean

	Expression : 
		( UInteger(2, 5) + 0 ).equals( UInteger(2, 5) )
		-> true : Boolean

	Expression : 
		( UInteger(2, 5) + 0.0 ).equals( UReal(2, 5) )
		-> true : Boolean

	Expression : 
		( UInteger(2, 5) + UReal(0, 0) ).equals( UReal(2, 5) )
		-> true : Boolean

	Expression : 
		( UInteger(6, 3) + UInteger(5, 0.3) ).equals( UInteger(5, 0.3) + UInteger(6, 3) )
		-> true : Boolean

	Expression : 
		( UInteger(9, 32) + 0.53 ).equals( 0.53 + UInteger(9, 32) )
		-> true : Boolean

	Expression : 
		( UInteger(2, 3) + 5 ).equals( 5 + UInteger(2, 3) )
		-> true : Boolean

	Expression : 
		( UInteger(9, 32) + UReal(0.53, 3) ).equals( UReal(0.53, 3) + UInteger(9, 32) )
		-> true : Boolean

	Expression : 
		( UInteger(6, 3) + (UInteger(5, 3) + UInteger(9,2)) ).equals( (UInteger(6, 3) + UInteger(5, 3)) + UInteger(9,2) )
		-> true : Boolean

	Expression : 
		( UReal(6, 3) + (5.3 + UInteger(9,2)) ).equals( (UReal(6, 3) + 5.3) + UInteger(9,2) )
		-> true : Boolean

	Expression : 
		( UReal(6, 3) + (5 + UInteger(9,2)) ).equals( (UReal(6, 3) + 5) + UInteger(9,2) )
		-> true : Boolean

	Expression : 
		( UInteger(6, 3) + (5 + 2) ).equals( (UInteger(6, 3) + 5) + 2 )
		-> true : Boolean

	Expression : 
		( 3.5 + (5 + UInteger(9,2)) ).equals( (3.5 + 5) + UInteger(9,2) )
		-> true : Boolean

	Expression : 
		UInteger(-9, 0) - UInteger(-9, 0)
		-> UInteger(0, 0.0) : UInteger

	Expression : 
		UInteger(-5, 0) - UInteger(-5, 3)
		-> UInteger(0, 3.0) : UInteger

	Expression : 
		UInteger(-4, 0) - UInteger(2, 0)
		-> UInteger(-6, 0.0) : UInteger

	Expression : 
		UInteger(-10, 0) - UInteger(4, 1)
		-> UInteger(-14, 1.0) : UInteger

	Expression : 
		UInteger(-9, 9) - UInteger(-9, 0)
		-> UInteger(0, 9.0) : UInteger

	Expression : 
		UInteger(-2, 3) - UInteger(-2, 4)
		-> UInteger(0, 5.0) : UInteger

	Expression : 
		UInteger(-6, 2) - UInteger(5, 0)
		-> UInteger(-11, 2.0) : UInteger

	Expression : 
		UInteger(-2, 3) - UInteger(4, 4)
		-> UInteger(-6, 5.0) : UInteger

	Expression : 
		UInteger(0, 0) - UInteger(0, 0)
		-> UInteger(0, 0.0) : UInteger

	Expression : 
		UInteger(0, 0) - UInteger(0, 4)
		-> UInteger(0, 4.0) : UInteger

	Expression : 
		UInteger(0, 0) - UInteger(6, 0)
		-> UInteger(-6, 0.0) : UInteger

	Expression : 
		UInteger(0, 0) - UInteger(7, 3)
		-> UInteger(-7, 3.0) : UInteger

	Expression : 
		UInteger(0, 4) - UInteger(0, 0)
		-> UInteger(0, 4.0) : UInteger

	Expression : 
		UInteger(0, 4) - UInteger(0, 3)
		-> UInteger(0, 5.0) : UInteger

	Expression : 
		UInteger(0, 4) - UInteger(1, 0)
		-> UInteger(-1, 4.0) : UInteger

	Expression : 
		UInteger(0, 4) - UInteger(2, 3)
		-> UInteger(-2, 5.0) : UInteger

	Expression : 
		UInteger(9, 0) - UInteger(9, 0)
		-> UInteger(0, 0.0) : UInteger

	Expression : 
		UInteger(5, 0) - UInteger(5, 3)
		-> UInteger(0, 3.0) : UInteger

	Expression : 
		UInteger(4, 0) - UInteger(8, 0)
		-> UInteger(-4, 0.0) : UInteger

	Expression : 
		UInteger(10, 0) - UInteger(10, 12)
		-> UInteger(0, 12.0) : UInteger

	Expression : 
		UInteger(9, 5) - UInteger(9, 0)
		-> UInteger(0, 5.0) : UInteger

	Expression : 
		UInteger(2, 3) - UInteger(2, 4)
		-> UInteger(0, 5.0) : UInteger

	Expression : 
		UInteger(6, 1) - UInteger(4, 0)
		-> UInteger(2, 1.0) : UInteger

	Expression : 
		UInteger(2, 3) - UInteger(5, 4)
		-> UInteger(-3, 5.0) : UInteger

	Expression : 
		UInteger(-9, 0) - UReal(-9, 0)
		-> UReal(0.0, 0.0) : UReal

	Expression : 
		UInteger(-5, 0) - UReal(-5, 3)
		-> UReal(0.0, 3.0) : UReal

	Expression : 
		UInteger(-4, 0) - UReal(2, 0)
		-> UReal(-6.0, 0.0) : UReal

	Expression : 
		UInteger(-10, 0) - UReal(4, 1)
		-> UReal(-14.0, 1.0) : UReal

	Expression : 
		UInteger(-9, 9) - UReal(-9, 0)
		-> UReal(0.0, 9.0) : UReal

	Expression : 
		UInteger(-2, 3) - UReal(-2, 4)
		-> UReal(0.0, 5.0) : UReal

	Expression : 
		UInteger(-6, 2) - UReal(5, 0)
		-> UReal(-11.0, 2.0) : UReal

	Expression : 
		UInteger(-2, 3) - UReal(4, 4)
		-> UReal(-6.0, 5.0) : UReal

	Expression : 
		UInteger(0, 0) - UReal(0, 0)
		-> UReal(0.0, 0.0) : UReal

	Expression : 
		UInteger(0, 0) - UReal(0, 4)
		-> UReal(0.0, 4.0) : UReal

	Expression : 
		UInteger(0, 0) - UReal(6, 0)
		-> UReal(-6.0, 0.0) : UReal

	Expression : 
		UInteger(0, 0) - UReal(7, 3)
		-> UReal(-7.0, 3.0) : UReal

	Expression : 
		UInteger(0, 4) - UReal(0, 0)
		-> UReal(0.0, 4.0) : UReal

	Expression : 
		UInteger(0, 4) - UReal(0, 3)
		-> UReal(0.0, 5.0) : UReal

	Expression : 
		UInteger(0, 4) - UReal(1, 0)
		-> UReal(-1.0, 4.0) : UReal

	Expression : 
		UInteger(0, 4) - UReal(2, 3)
		-> UReal(-2.0, 5.0) : UReal

	Expression : 
		UInteger(9, 0) - UReal(9, 0)
		-> UReal(0.0, 0.0) : UReal

	Expression : 
		UInteger(5, 0) - UReal(5, 3)
		-> UReal(0.0, 3.0) : UReal

	Expression : 
		UInteger(4, 0) - UReal(8, 0)
		-> UReal(-4.0, 0.0) : UReal

	Expression : 
		UInteger(10, 0) - UReal(10, 12)
		-> UReal(0.0, 12.0) : UReal

	Expression : 
		UInteger(9, 5) - UReal(9, 0)
		-> UReal(0.0, 5.0) : UReal

	Expression : 
		UInteger(2, 3) - UReal(2, 4)
		-> UReal(0.0, 5.0) : UReal

	Expression : 
		UInteger(6, 1) - UReal(4, 0)
		-> UReal(2.0, 1.0) : UReal

	Expression : 
		UInteger(2, 3) - UReal(5, 4)
		-> UReal(-3.0, 5.0) : UReal

	Expression : 
		UInteger(-3, 0) - -3.0
		-> UReal(0.0, 0.0) : UReal

	Expression : 
		UInteger(-6, 0) - -1.2
		-> UReal(-4.8, 0.0) : UReal

	Expression : 
		UInteger(-5, 3) - -5.0
		-> UReal(0.0, 3.0) : UReal

	Expression : 
		UInteger(-8, 5) - -2.0
		-> UReal(-6.0, 5.0) : UReal

	Expression : 
		UInteger(0, 0) - 0.0
		-> UReal(0.0, 0.0) : UReal

	Expression : 
		UInteger(0, 0) - 3.0
		-> UReal(-3.0, 0.0) : UReal

	Expression : 
		UInteger(0, 3) - 0.0
		-> UReal(0.0, 3.0) : UReal

	Expression : 
		UInteger(0, 5) - -5.0
		-> UReal(5.0, 5.0) : UReal

	Expression : 
		UInteger(5, 0) - 5.0
		-> UReal(0.0, 0.0) : UReal

	Expression : 
		UInteger(3, 0) - 0.6
		-> UReal(2.4, 0.0) : UReal

	Expression : 
		UInteger(7, 3) - 7.0
		-> UReal(0.0, 3.0) : UReal

	Expression : 
		UInteger(2, 5) - 0.5
		-> UReal(1.5, 5.0) : UReal

	Expression : 
		UInteger(-3, 0) - -3
		-> UInteger(0, 0.0) : UInteger

	Expression : 
		UInteger(-6, 0) - -12
		-> UInteger(6, 0.0) : UInteger

	Expression : 
		UInteger(-5, 3) - -5
		-> UInteger(0, 3.0) : UInteger

	Expression : 
		UInteger(-8, 5) - -2
		-> UInteger(-6, 5.0) : UInteger

	Expression : 
		UInteger(0, 0) - 0
		-> UInteger(0, 0.0) : UInteger

	Expression : 
		UInteger(0, 0) - 3
		-> UInteger(-3, 0.0) : UInteger

	Expression : 
		UInteger(0, 3) - 0
		-> UInteger(0, 3.0) : UInteger

	Expression : 
		UInteger(0, 5) - -5
		-> UInteger(5, 5.0) : UInteger

	Expression : 
		UInteger(5, 0) - 5
		-> UInteger(0, 0.0) : UInteger

	Expression : 
		UInteger(3, 0) - 56
		-> UInteger(-53, 0.0) : UInteger

	Expression : 
		UInteger(7, 3) - 7
		-> UInteger(0, 3.0) : UInteger

	Expression : 
		UInteger(2, 5) - 65
		-> UInteger(-63, 5.0) : UInteger

	Expression : 
		( UInteger(3, 4) - UInteger(5, 2) ).equals( -(UInteger(5, 2) - UInteger(3, 4)) )
		-> true : Boolean

	Expression : 
		( UInteger(3, 4) - 5 ).equals( -(5 - UInteger(3, 4)) )
		-> true : Boolean

	Expression : 
		( 4.3 - UInteger(5, 2) ).equals( -(UInteger(5, 2) - 4.3) )
		-> true : Boolean

	Expression : 
		( UInteger(3, 4) - UReal(5, 2) ).equals( -(UInteger(5, 2) - UReal(3, 4)) )
		-> true : Boolean

	Expression : 
		( UInteger(3, 4) - (UInteger(5, 2) - UInteger(2, 0.53)) ).equals( (UInteger(3, 4) - UInteger(5, 2)) - UInteger(2, 0.53) )
		-> false : Boolean

	Expression : 
		( UInteger(3, 0) - (UInteger(5, 0) - UReal(2, 0)) ).equals( (UInteger(3, 0) - UInteger(5, 0)) - UReal(2, 0) )
		-> false : Boolean

	Expression : 
		( UInteger(3, 0) - (5 - UReal(2, 0)) ).equals( (UInteger(3, 0) - 5) - UReal(2, 0) )
		-> false : Boolean

	Expression : 
		( UInteger(3, 0) - (5 - 2.2) ).equals( (UInteger(3, 0) - 5) - 2.2 )
		-> false : Boolean

	Expression : 
		UInteger(-9, 0) * UInteger(-9, 0)
		-> UInteger(81, 0.0) : UInteger

	Expression : 
		UInteger(-5, 0) * UInteger(-5, 3)
		-> UInteger(25, 15.0) : UInteger

	Expression : 
		UInteger(-4, 0) * UInteger(2, 0)
		-> UInteger(-8, 0.0) : UInteger

	Expression : 
		UInteger(-10, 0) * UInteger(4, 1)
		-> UInteger(-40, 10.0) : UInteger

	Expression : 
		UInteger(-9, 9) * UInteger(-9, 0)
		-> UInteger(81, 81.0) : UInteger

	Expression : 
		UInteger(-2, 3) * UInteger(-2, 4)
		-> UInteger(4, 10.0) : UInteger

	Expression : 
		UInteger(-6, 2) * UInteger(5, 0)
		-> UInteger(-30, 10.0) : UInteger

	Expression : 
		UInteger(-2, 3) * UInteger(2, 4)
		-> UInteger(-4, 10.0) : UInteger

	Expression : 
		UInteger(0, 0) * UInteger(0, 0)
		-> UInteger(0, 0.0) : UInteger

	Expression : 
		UInteger(0, 0) * UInteger(0, 4)
		-> UInteger(0, 0.0) : UInteger

	Expression : 
		UInteger(0, 0) * UInteger(6, 0)
		-> UInteger(0, 0.0) : UInteger

	Expression : 
		UInteger(0, 0) * UInteger(7, 3)
		-> UInteger(0, 0.0) : UInteger

	Expression : 
		UInteger(0, 4) * UInteger(0, 0)
		-> UInteger(0, 0.0) : UInteger

	Expression : 
		UInteger(0, 4) * UInteger(0, 3)
		-> UInteger(0, 0.0) : UInteger

	Expression : 
		UInteger(0, 4) * UInteger(1, 0)
		-> UInteger(0, 4.0) : UInteger

	Expression : 
		UInteger(0, 4) * UInteger(2, 3)
		-> UInteger(0, 8.0) : UInteger

	Expression : 
		UInteger(9, 0) * UInteger(9, 0)
		-> UInteger(81, 0.0) : UInteger

	Expression : 
		UInteger(5, 0) * UInteger(5, 3)
		-> UInteger(25, 15.0) : UInteger

	Expression : 
		UInteger(4, 0) * UInteger(8, 0)
		-> UInteger(32, 0.0) : UInteger

	Expression : 
		UInteger(10, 0) * UInteger(10, 12)
		-> UInteger(100, 120.0) : UInteger

	Expression : 
		UInteger(9, 5) * UInteger(9, 0)
		-> UInteger(81, 45.0) : UInteger

	Expression : 
		UInteger(2, 3) * UInteger(2, 4)
		-> UInteger(4, 10.0) : UInteger

	Expression : 
		UInteger(6, 1) * UInteger(4, 0)
		-> UInteger(24, 4.0) : UInteger

	Expression : 
		UInteger(2, 3) * UInteger(5, 4)
		-> UInteger(10, 17.0) : UInteger

	Expression : 
		UInteger(-9, 0) * UReal(-9, 0)
		-> UReal(81.0, 0.0) : UReal

	Expression : 
		UInteger(-5, 0) * UReal(-5, 3)
		-> UReal(25.0, 15.0) : UReal

	Expression : 
		UInteger(-4, 0) * UReal(2, 0)
		-> UReal(-8.0, 0.0) : UReal

	Expression : 
		UInteger(-10, 0) * UReal(4, 1)
		-> UReal(-40.0, 10.0) : UReal

	Expression : 
		UInteger(-9, 9) * UReal(-9, 0)
		-> UReal(81.0, 81.0) : UReal

	Expression : 
		UInteger(-2, 3) * UReal(-2, 4)
		-> UReal(4.0, 10.0) : UReal

	Expression : 
		UInteger(-6, 2) * UReal(5, 0)
		-> UReal(-30.0, 10.0) : UReal

	Expression : 
		UInteger(-2, 3) * UReal(2, 4)
		-> UReal(-4.0, 10.0) : UReal

	Expression : 
		UInteger(0, 0) * UReal(0, 0)
		-> UReal(0.0, 0.0) : UReal

	Expression : 
		UInteger(0, 0) * UReal(0, 4)
		-> UReal(0.0, 0.0) : UReal

	Expression : 
		UInteger(0, 0) * UReal(6, 0)
		-> UReal(0.0, 0.0) : UReal

	Expression : 
		UInteger(0, 0) * UReal(7, 3)
		-> UReal(0.0, 0.0) : UReal

	Expression : 
		UInteger(0, 4) * UReal(0, 0)
		-> UReal(0.0, 0.0) : UReal

	Expression : 
		UInteger(0, 4) * UReal(0, 3)
		-> UReal(0.0, 0.0) : UReal

	Expression : 
		UInteger(0, 4) * UReal(1, 0)
		-> UReal(0.0, 4.0) : UReal

	Expression : 
		UInteger(0, 4) * UReal(2, 3)
		-> UReal(0.0, 8.0) : UReal

	Expression : 
		UInteger(9, 0) * UReal(9, 0)
		-> UReal(81.0, 0.0) : UReal

	Expression : 
		UInteger(5, 0) * UReal(5, 3)
		-> UReal(25.0, 15.0) : UReal

	Expression : 
		UInteger(4, 0) * UReal(8, 0)
		-> UReal(32.0, 0.0) : UReal

	Expression : 
		UInteger(10, 0) * UReal(10, 12)
		-> UReal(100.0, 120.0) : UReal

	Expression : 
		UInteger(9, 5) * UReal(9, 0)
		-> UReal(81.0, 45.0) : UReal

	Expression : 
		UInteger(2, 3) * UReal(2, 4)
		-> UReal(4.0, 10.0) : UReal

	Expression : 
		UInteger(6, 1) * UReal(4, 0)
		-> UReal(24.0, 4.0) : UReal

	Expression : 
		UInteger(2, 3) * UReal(5, 4)
		-> UReal(10.0, 17.0) : UReal

	Expression : 
		UInteger(-3, 0) * -3.0
		-> UReal(9.0, 0.0) : UReal

	Expression : 
		UInteger(-6, 0) * -1.2
		-> UReal(7.2, 0.0) : UReal

	Expression : 
		UInteger(-5, 3) * -5.0
		-> UReal(25.0, 15.0) : UReal

	Expression : 
		UInteger(-8, 5) * -2.0
		-> UReal(16.0, 10.0) : UReal

	Expression : 
		UInteger(0, 0) * 0.0
		-> UReal(0.0, 0.0) : UReal

	Expression : 
		UInteger(0, 0) * 3.0
		-> UReal(0.0, 0.0) : UReal

	Expression : 
		UInteger(0, 3) * 0.0
		-> UReal(0.0, 0.0) : UReal

	Expression : 
		UInteger(0, 5) * -5.0
		-> UReal(0.0, 25.0) : UReal

	Expression : 
		UInteger(5, 0) * 5.0
		-> UReal(25.0, 0.0) : UReal

	Expression : 
		UInteger(3, 0) * 0.6
		-> UReal(1.8, 0.0) : UReal

	Expression : 
		UInteger(7, 3) * 7.0
		-> UReal(49.0, 21.0) : UReal

	Expression : 
		UInteger(2, 5) * 0.5
		-> UReal(1.0, 2.5) : UReal

	Expression : 
		UInteger(-3, 0) * -3
		-> UInteger(9, 0.0) : UInteger

	Expression : 
		UInteger(-6, 0) * -12
		-> UInteger(72, 0.0) : UInteger

	Expression : 
		UInteger(-5, 3) * -5
		-> UInteger(25, 15.0) : UInteger

	Expression : 
		UInteger(-8, 5) * -2
		-> UInteger(16, 10.0) : UInteger

	Expression : 
		UInteger(0, 0) * 0
		-> UInteger(0, 0.0) : UInteger

	Expression : 
		UInteger(0, 0) * 3
		-> UInteger(0, 0.0) : UInteger

	Expression : 
		UInteger(0, 3) * 0
		-> UInteger(0, 0.0) : UInteger

	Expression : 
		UInteger(0, 5) * -5
		-> UInteger(0, 25.0) : UInteger

	Expression : 
		UInteger(5, 0) * 5
		-> UInteger(25, 0.0) : UInteger

	Expression : 
		UInteger(3, 0) * 56
		-> UInteger(168, 0.0) : UInteger

	Expression : 
		UInteger(7, 3) * 7
		-> UInteger(49, 21.0) : UInteger

	Expression : 
		UInteger(2, 5) * 65
		-> UInteger(130, 325.0) : UInteger

	Expression : 
		( UInteger(3, 2) * UInteger(5, 2) ).equals( UInteger(5, 2) * UInteger(3, 2) )
		-> true : Boolean

	Expression : 
		( UInteger(3, 2) * UReal(5, 0) ).equals( UInteger(5, 0) * UReal(3, 2) )
		-> true : Boolean

	Expression : 
		( UInteger(3, 2) * 5 ).equals( 5 * UInteger(3, 2) )
		-> true : Boolean

	Expression : 
		( UInteger(3, 2) * -5.53 ).equals( -5.53 * UInteger(3, 2) )
		-> true : Boolean

	Expression : 
		( UInteger(3, 5) * (UInteger(5, 1) * UInteger(1, 2)) ).equals( (UInteger(3, 5) * UInteger(5, 1)) * UInteger(1, 2) )
		-> true : Boolean

	Expression : 
		( UInteger(3, 5) * (5.1 * UReal(1, 2)) ).equals( (UInteger(3, 5) * 5.1) * UReal(1, 2) )
		-> true : Boolean

	Expression : 
		( UInteger(3, 5) * (5.1 * 1.2) ).equals( (UInteger(3, 5) * 5.1) * 1.2 )
		-> true : Boolean

	Expression : 
		( UInteger(3, 5) * (5 * UInteger(1, 2)) ).equals( (UInteger(3, 5) * 5) * UInteger(1, 2) )
		-> true : Boolean

	Expression : 
		( UInteger(3, 5) * (5 * 1.2) ).equals( (UInteger(3, 5) * 5) * 1.2 )
		-> true : Boolean

	Expression : 
		( UInteger(3, 5) * (5 * 2) ).equals( (UInteger(3, 5) * 5) * 2 )
		-> true : Boolean

	Expression : 
		( UInteger(2,1) * (UInteger(3,1) + UInteger(5, 0.2)) ).equals( UInteger(2,1) * UInteger(3,1) +  UInteger(2,1) * UInteger(5, 0.2) )
		-> false : Boolean

	Expression : 
		( 5.1 * (UInteger(3, 2) + UInteger(1, 2)) ).equals( (5.1 * UInteger(3, 2)) + (5.1 * UInteger(1, 2)) )
		-> true : Boolean

	Expression : 
		( 2 * (UInteger(3, 2) + UInteger(1, 2)) ).equals( (2 * UInteger(3, 2)) + (2 * UInteger(1, 2)) )
		-> true : Boolean

	Expression : 
		( UInteger(3, 2) * UInteger(1, 0) ).equals( UInteger(3, 2) )
		-> true : Boolean

	Expression : 
		( UInteger(3, 2) * 1 ).equals( UInteger(3, 2) )
		-> true : Boolean

	Expression : 
		( UInteger(3, 2) * 1.0 ).equals( UReal(3, 2) )
		-> true : Boolean

	Expression : 
		UInteger(-9, 0) / UInteger(-9, 0)
		-> UReal(1.0, 0.0) : UReal

	Expression : 
		UInteger(-5, 0) / UInteger(-5, 3)
		-> UReal(1.0, 0.12) : UReal

	Expression : 
		UInteger(-4, 0) / UInteger(2, 0)
		-> UReal(-2.0, 0.0) : UReal

	Expression : 
		UInteger(-10, 0) / UInteger(4, 1)
		-> UReal(-2.5, 0.0625) : UReal

	Expression : 
		UInteger(-9, 9) / UInteger(-9, 0)
		-> UReal(1.0, 1.0) : UReal

	Expression : 
		UInteger(-2, 3) / UInteger(-2, 4)
		-> UReal(1.0, 2.9154759474) : UReal

	Expression : 
		UInteger(-6, 2) / UInteger(5, 0)
		-> UReal(-1.2, 0.4) : UReal

	Expression : 
		UInteger(-2, 3) / UInteger(2, 4)
		-> UReal(-1.0, 2.9154759474) : UReal

	Expression : 
		UInteger(0, 0) / UInteger(0, 0)
		-> null : OclVoid

	Expression : 
		UInteger(0, 0) / UInteger(0, 4)
		-> null : OclVoid

	Expression : 
		UInteger(0, 0) / UInteger(6, 0)
		-> UReal(0.0, 0.0) : UReal

	Expression : 
		UInteger(0, 0) / UInteger(7, 3)
		-> UReal(0.0, 0.0612244898) : UReal

	Expression : 
		UInteger(0, 4) / UInteger(0, 0)
		-> null : OclVoid

	Expression : 
		UInteger(0, 4) / UInteger(0, 3)
		-> null : OclVoid

	Expression : 
		UInteger(0, 4) / UInteger(1, 0)
		-> UReal(0.0, 4.0) : UReal

	Expression : 
		UInteger(0, 4) / UInteger(2, 3)
		-> UReal(0.0, 2.8284271247) : UReal

	Expression : 
		UInteger(9, 0) / UInteger(9, 0)
		-> UReal(1.0, 0.0) : UReal

	Expression : 
		UInteger(5, 0) / UInteger(5, 3)
		-> UReal(1.0, 0.12) : UReal

	Expression : 
		UInteger(4, 0) / UInteger(8, 0)
		-> UReal(0.5, 0.0) : UReal

	Expression : 
		UInteger(10, 0) / UInteger(10, 12)
		-> UReal(1.0, 0.12) : UReal

	Expression : 
		UInteger(9, 5) / UInteger(9, 0)
		-> UReal(1.0, 0.5555555556) : UReal

	Expression : 
		UInteger(2, 3) / UInteger(2, 4)
		-> UReal(1.0, 2.9154759474) : UReal

	Expression : 
		UInteger(6, 1) / UInteger(4, 0)
		-> UReal(1.5, 0.25) : UReal

	Expression : 
		UInteger(2, 3) / UInteger(5, 4)
		-> UReal(0.4, 1.379275172) : UReal

	Expression : 
		UInteger(-9, 0) / UReal(-9, 0)
		-> UReal(1.0, 0.0) : UReal

	Expression : 
		UInteger(-5, 0) / UReal(-5, 3)
		-> UReal(1.0, 0.12) : UReal

	Expression : 
		UInteger(-4, 0) / UReal(2, 0)
		-> UReal(-2.0, 0.0) : UReal

	Expression : 
		UInteger(-10, 0) / UReal(4, 1)
		-> UReal(-2.5, 0.0625) : UReal

	Expression : 
		UInteger(-9, 9) / UReal(-9, 0)
		-> UReal(1.0, 1.0) : UReal

	Expression : 
		UInteger(-2, 3) / UReal(-2, 4)
		-> UReal(1.0, 2.9154759474) : UReal

	Expression : 
		UInteger(-6, 2) / UReal(5, 0)
		-> UReal(-1.2, 0.4) : UReal

	Expression : 
		UInteger(-2, 3) / UReal(2, 4)
		-> UReal(-1.0, 2.9154759474) : UReal

	Expression : 
		UInteger(0, 0) / UReal(0, 0)
		-> null : OclVoid

	Expression : 
		UInteger(0, 0) / UReal(0, 4)
		-> null : OclVoid

	Expression : 
		UInteger(0, 0) / UReal(6, 0)
		-> UReal(0.0, 0.0) : UReal

	Expression : 
		UInteger(0, 0) / UReal(7, 3)
		-> UReal(0.0, 0.0612244898) : UReal

	Expression : 
		UInteger(0, 4) / UReal(0, 0)
		-> null : OclVoid

	Expression : 
		UInteger(0, 4) / UReal(0, 3)
		-> null : OclVoid

	Expression : 
		UInteger(0, 4) / UReal(1, 0)
		-> UReal(0.0, 4.0) : UReal

	Expression : 
		UInteger(0, 4) / UReal(2, 3)
		-> UReal(0.0, 2.8284271247) : UReal

	Expression : 
		UInteger(9, 0) / UReal(9, 0)
		-> UReal(1.0, 0.0) : UReal

	Expression : 
		UInteger(5, 0) / UReal(5, 3)
		-> UReal(1.0, 0.12) : UReal

	Expression : 
		UInteger(4, 0) / UReal(8, 0)
		-> UReal(0.5, 0.0) : UReal

	Expression : 
		UInteger(10, 0) / UReal(10, 12)
		-> UReal(1.0, 0.12) : UReal

	Expression : 
		UInteger(9, 5) / UReal(9, 0)
		-> UReal(1.0, 0.5555555556) : UReal

	Expression : 
		UInteger(2, 3) / UReal(2, 4)
		-> UReal(1.0, 2.9154759474) : UReal

	Expression : 
		UInteger(6, 1) / UReal(4, 0)
		-> UReal(1.5, 0.25) : UReal

	Expression : 
		UInteger(2, 3) / UReal(5, 4)
		-> UReal(0.4, 1.379275172) : UReal

	Expression : 
		UInteger(-3, 0) / -3.0
		-> UReal(1.0, 0.0) : UReal

	Expression : 
		UInteger(-6, 0) / -1.2
		-> UReal(5.0, 0.0) : UReal

	Expression : 
		UInteger(-5, 3) / -5.0
		-> UReal(1.0, 0.6) : UReal

	Expression : 
		UInteger(-8, 5) / -2.0
		-> UReal(4.0, 2.5) : UReal

	Expression : 
		UInteger(0, 0) / 0.0
		-> null : OclVoid

	Expression : 
		UInteger(0, 0) / 3.0
		-> UReal(0.0, 0.0) : UReal

	Expression : 
		UInteger(0, 3) / 0.0
		-> null : OclVoid

	Expression : 
		UInteger(0, 5) / -5.0
		-> UReal(0.0, 1.0) : UReal

	Expression : 
		UInteger(5, 0) / 5.0
		-> UReal(1.0, 0.0) : UReal

	Expression : 
		UInteger(3, 0) / 0.6
		-> UReal(5.0, 0.0) : UReal

	Expression : 
		UInteger(7, 3) / 7.0
		-> UReal(1.0, 0.4285714286) : UReal

	Expression : 
		UInteger(2, 5) / 0.5
		-> UReal(4.0, 10.0) : UReal

	Expression : 
		UInteger(-3, 0) / -3
		-> UReal(1.0, 0.0) : UReal

	Expression : 
		UInteger(-6, 0) / -12
		-> UReal(0.5, 0.0) : UReal

	Expression : 
		UInteger(-5, 3) / -5
		-> UReal(1.0, 0.6) : UReal

	Expression : 
		UInteger(-8, 5) / -2
		-> UReal(4.0, 2.5) : UReal

	Expression : 
		UInteger(0, 0) / 0
		-> null : OclVoid

	Expression : 
		UInteger(0, 0) / 3
		-> UReal(0.0, 0.0) : UReal

	Expression : 
		UInteger(0, 3) / 0
		-> null : OclVoid

	Expression : 
		UInteger(0, 5) / -5
		-> UReal(0.0, 1.0) : UReal

	Expression : 
		UInteger(5, 0) / 5
		-> UReal(1.0, 0.0) : UReal

	Expression : 
		UInteger(3, 0) / 56
		-> UReal(0.0535714286, 0.0) : UReal

	Expression : 
		UInteger(7, 3) / 7
		-> UReal(1.0, 0.4285714286) : UReal

	Expression : 
		UInteger(2, 5) / 65
		-> UReal(0.0307692308, 0.0769230769) : UReal

	Expression : 
		( UInteger(2, 3).toUReal().inv() ).equals( 1 / UInteger(2, 3) )
		-> true : Boolean

	Expression : 
		( UInteger(0, 3).toUReal().inv() ).equals( 1 / UInteger(0, 3) )
		-> true : Boolean

	Expression : 
		( UInteger(2, 3) / UInteger(1, 0.5) ).equals( UInteger(1, 0.5) / UInteger(2, 3) )
		-> false : Boolean

	Expression : 
		( 2.3 / UInteger(1, 0.5) ).equals( UInteger(1, 0.5) / 2.3 )
		-> false : Boolean

	Expression : 
		( 2 / UInteger(1, 0.5) ).equals( UInteger(1, 0.5) / 2 )
		-> false : Boolean

	Expression : 
		( UInteger(2, 3) / (UInteger(1, 0.5) / UInteger(4, 0.25)) ).equals( (UInteger(2, 3) / UInteger(1, 0.5)) / UInteger(4, 0.25) )
		-> false : Boolean

	Expression : 
		( UInteger(2, 3) / (12.59 / UInteger(3, 0.25)) ).equals( (UInteger(2, 3) / 12.59) / UInteger(3, 0.25) )
		-> false : Boolean

	Expression : 
		( UInteger(2, 3) / (12 / UInteger(3, 0.25)) ).equals( (UInteger(2, 3) / 12) / UInteger(3, 0.25) )
		-> false : Boolean

	Expression : 
		( UInteger(2, 3) / 1 ).equals( UInteger(2, 3).toUReal() )
		-> true : Boolean

	Expression : 
		UInteger(-9, 0) div UInteger(-9, 0)
		-> UInteger(1, 0.0) : UInteger

	Expression : 
		UInteger(-5, 0) div UInteger(-5, 3)
		-> UInteger(1, 0.12) : UInteger

	Expression : 
		UInteger(-4, 0) div UInteger(2, 0)
		-> UInteger(-2, 0.0) : UInteger

	Expression : 
		UInteger(-10, 0) div UInteger(4, 1)
		-> UInteger(-2, 0.0625) : UInteger

	Expression : 
		UInteger(-9, 9) div UInteger(-9, 0)
		-> UInteger(1, 1.0) : UInteger

	Expression : 
		UInteger(-2, 3) div UInteger(-2, 4)
		-> UInteger(1, 2.9154759474) : UInteger

	Expression : 
		UInteger(-6, 2) div UInteger(5, 0)
		-> UInteger(-1, 0.4) : UInteger

	Expression : 
		UInteger(-2, 3) div UInteger(2, 4)
		-> UInteger(-1, 2.9154759474) : UInteger

	Expression : 
		UInteger(0, 0) div UInteger(0, 0)
		-> null : OclVoid

	Expression : 
		UInteger(0, 0) div UInteger(0, 4)
		-> null : OclVoid

	Expression : 
		UInteger(0, 0) div UInteger(6, 0)
		-> UInteger(0, 0.0) : UInteger

	Expression : 
		UInteger(0, 0) div UInteger(7, 3)
		-> UInteger(0, 0.0612244898) : UInteger

	Expression : 
		UInteger(0, 4) div UInteger(0, 0)
		-> null : OclVoid

	Expression : 
		UInteger(0, 4) div UInteger(0, 3)
		-> null : OclVoid

	Expression : 
		UInteger(0, 4) div UInteger(1, 0)
		-> UInteger(0, 4.0) : UInteger

	Expression : 
		UInteger(0, 4) div UInteger(2, 3)
		-> UInteger(0, 2.8284271247) : UInteger

	Expression : 
		UInteger(9, 0) div UInteger(9, 0)
		-> UInteger(1, 0.0) : UInteger

	Expression : 
		UInteger(5, 0) div UInteger(5, 3)
		-> UInteger(1, 0.12) : UInteger

	Expression : 
		UInteger(4, 0) div UInteger(8, 0)
		-> UInteger(0, 0.0) : UInteger

	Expression : 
		UInteger(10, 0) div UInteger(10, 12)
		-> UInteger(1, 0.12) : UInteger

	Expression : 
		UInteger(9, 5) div UInteger(9, 0)
		-> UInteger(1, 0.5555555556) : UInteger

	Expression : 
		UInteger(2, 3) div UInteger(2, 4)
		-> UInteger(1, 2.9154759474) : UInteger

	Expression : 
		UInteger(6, 1) div UInteger(4, 0)
		-> UInteger(1, 0.25) : UInteger

	Expression : 
		UInteger(2, 3) div UInteger(5, 4)
		-> UInteger(0, 1.379275172) : UInteger

	Expression : 
		UInteger(-3, 0) div -3
		-> UInteger(1, 0.0) : UInteger

	Expression : 
		UInteger(-6, 0) div -12
		-> UInteger(0, 0.0) : UInteger

	Expression : 
		UInteger(-5, 3) div -5
		-> UInteger(1, 0.6) : UInteger

	Expression : 
		UInteger(-8, 5) div -2
		-> UInteger(4, 2.5) : UInteger

	Expression : 
		UInteger(0, 0) div 0
		-> null : OclVoid

	Expression : 
		UInteger(0, 0) div 3
		-> UInteger(0, 0.0) : UInteger

	Expression : 
		UInteger(0, 3) div 0
		-> null : OclVoid

	Expression : 
		UInteger(0, 5) div -5
		-> UInteger(0, 1.0) : UInteger

	Expression : 
		UInteger(5, 0) div 5
		-> UInteger(1, 0.0) : UInteger

	Expression : 
		UInteger(3, 0) div 56
		-> UInteger(0, 0.0) : UInteger

	Expression : 
		UInteger(7, 3) div 7
		-> UInteger(1, 0.4285714286) : UInteger

	Expression : 
		UInteger(2, 5) div 65
		-> UInteger(0, 0.0769230769) : UInteger

	Expression : 
		UInteger(-9, 0).mod(UInteger(-9, 0))
		-> UInteger(0, 0.0) : UInteger

	Expression : 
		UInteger(-5, 0).mod(UInteger(-5, 3))
		-> UInteger(0, 0.12) : UInteger

	Expression : 
		UInteger(-4, 0).mod(UInteger(2, 0))
		-> UInteger(0, 0.0) : UInteger

	Expression : 
		UInteger(-10, 0).mod(UInteger(4, 1))
		-> UInteger(-2, 0.0625) : UInteger

	Expression : 
		UInteger(-9, 9).mod(UInteger(-9, 0))
		-> UInteger(0, 1.0) : UInteger

	Expression : 
		UInteger(-2, 3).mod(UInteger(-2, 4))
		-> UInteger(0, 2.9154759474) : UInteger

	Expression : 
		UInteger(-6, 2).mod(UInteger(5, 0))
		-> UInteger(-1, 0.4) : UInteger

	Expression : 
		UInteger(-2, 3).mod(UInteger(2, 4))
		-> UInteger(0, 2.9154759474) : UInteger

	Expression : 
		UInteger(0, 0).mod(UInteger(0, 0))
		-> null : OclVoid

	Expression : 
		UInteger(0, 0).mod(UInteger(0, 4))
		-> null : OclVoid

	Expression : 
		UInteger(0, 0).mod(UInteger(6, 0))
		-> UInteger(0, 0.0) : UInteger

	Expression : 
		UInteger(0, 0).mod(UInteger(7, 3))
		-> UInteger(0, 0.0612244898) : UInteger

	Expression : 
		UInteger(0, 4).mod(UInteger(0, 0))
		-> null : OclVoid

	Expression : 
		UInteger(0, 4).mod(UInteger(0, 3))
		-> null : OclVoid

	Expression : 
		UInteger(0, 4).mod(UInteger(1, 0))
		-> UInteger(0, 4.0) : UInteger

	Expression : 
		UInteger(0, 4).mod(UInteger(2, 3))
		-> UInteger(0, 2.8284271247) : UInteger

	Expression : 
		UInteger(9, 0).mod(UInteger(9, 0))
		-> UInteger(0, 0.0) : UInteger

	Expression : 
		UInteger(5, 0).mod(UInteger(5, 3))
		-> UInteger(0, 0.12) : UInteger

	Expression : 
		UInteger(4, 0).mod(UInteger(8, 0))
		-> UInteger(4, 0.0) : UInteger

	Expression : 
		UInteger(10, 0).mod(UInteger(10, 12))
		-> UInteger(0, 0.12) : UInteger

	Expression : 
		UInteger(9, 5).mod(UInteger(9, 0))
		-> UInteger(0, 0.5555555556) : UInteger

	Expression : 
		UInteger(2, 3).mod(UInteger(2, 4))
		-> UInteger(0, 2.9154759474) : UInteger

	Expression : 
		UInteger(6, 1).mod(UInteger(4, 0))
		-> UInteger(2, 0.25) : UInteger

	Expression : 
		UInteger(2, 3).mod(UInteger(5, 4))
		-> UInteger(2, 1.379275172) : UInteger

	Expression : 
		UInteger(-3, 0).mod(-3)
		-> UInteger(0, 0.0) : UInteger

	Expression : 
		UInteger(-6, 0).mod(-12)
		-> UInteger(-6, 0.0) : UInteger

	Expression : 
		UInteger(-5, 3).mod(-5)
		-> UInteger(0, 0.6) : UInteger

	Expression : 
		UInteger(-8, 5).mod(-2)
		-> UInteger(0, 2.5) : UInteger

	Expression : 
		UInteger(0, 0).mod(0)
		-> null : OclVoid

	Expression : 
		UInteger(0, 0).mod(3)
		-> UInteger(0, 0.0) : UInteger

	Expression : 
		UInteger(0, 3).mod(0)
		-> null : OclVoid

	Expression : 
		UInteger(0, 5).mod(-5)
		-> UInteger(0, 1.0) : UInteger

	Expression : 
		UInteger(5, 0).mod(5)
		-> UInteger(0, 0.0) : UInteger

	Expression : 
		UInteger(3, 0).mod(56)
		-> UInteger(3, 0.0) : UInteger

	Expression : 
		UInteger(7, 3).mod(7)
		-> UInteger(0, 0.4285714286) : UInteger

	Expression : 
		UInteger(2, 5).mod(65)
		-> UInteger(2, 0.0769230769) : UInteger

	Expression : 
		(UInteger(0, 0) < UInteger(0, 0)).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(0, 0) < UInteger(1, 0)).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(3, 0) < UInteger(0, 0)).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(0, 0) < UInteger(3, 2)).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(3, 0) < UInteger(0, 2)).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(0, 2) < UInteger(3, 0)).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(3, 2) < UInteger(0, 0)).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(0, 2) < UInteger(0, 2)).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(0, 2) < UInteger(0, 1)).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(0, 2) < UInteger(1, 0.25)).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(0, 2) < UInteger(-1, 0.25)).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(0, 2) < UInteger(5, 2)).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(5, 2) < UInteger(0, 2)).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(0, 0) < UReal(0, 0)).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(0, 0) < UReal(1, 0)).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(3, 0) < UReal(0, 0)).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(0, 0) < UReal(3, 2)).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(3, 0) < UReal(0, 2)).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(0, 2) < UReal(3, 0)).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(3, 2) < UReal(0, 0)).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(0, 2) < UReal(0, 2)).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(0, 2) < UReal(0, 1)).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(0, 2) < UReal(1, 0.25)).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(0, 2) < UReal(-1, 0.25)).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(0, 2) < UReal(5, 2)).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(5, 2) < UReal(0, 2)).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(0, 0) < 0).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(0, 0) < 1).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(1, 0) < 0).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(0, 2) < 3).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(3, 2) < 0).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(0, 0) <= UInteger(0, 0)).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(0, 0) <= UInteger(1, 0)).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(3, 0) <= UInteger(0, 0)).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(0, 0) <= UInteger(3, 2)).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(3, 0) <= UInteger(0, 2)).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(0, 2) <= UInteger(3, 0)).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(3, 2) <= UInteger(0, 0)).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(0, 2) <= UInteger(0, 2)).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(0, 2) <= UInteger(0, 1)).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(0, 2) <= UInteger(1, 0.25)).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(0, 2) <= UInteger(-1, 0.25)).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(0, 2) <= UInteger(5, 2)).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(5, 2) <= UInteger(0, 2)).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(0, 0) <= UReal(0, 0)).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(0, 0) <= UReal(1, 0)).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(3, 0) <= UReal(0, 0)).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(0, 0) <= UReal(3, 2)).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(3, 0) <= UReal(0, 2)).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(0, 2) <= UReal(3, 0)).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(3, 2) <= UReal(0, 0)).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(0, 2) <= UReal(0, 2)).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(0, 2) <= UReal(0, 1)).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(0, 2) <= UReal(1, 0.25)).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(0, 2) <= UReal(-1, 0.25)).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(0, 2) <= UReal(5, 2)).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(5, 2) <= UReal(0, 2)).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(0, 0) <= 0).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(0, 0) <= 1).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(1, 0) <= 0).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(0, 2) <= 3).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(3, 2) <= 0).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(0, 0) <= 0.0).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(0, 0) <= 1.0).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(1, 0) <= 0.0).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(0, 2) <= 3.0).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(3, 2) <= 0.0).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(0, 0) > UInteger(0, 0)).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(0, 0) > UInteger(1, 0)).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(3, 0) > UInteger(0, 0)).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(0, 0) > UInteger(3, 2)).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(3, 0) > UInteger(0, 2)).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(0, 2) > UInteger(3, 0)).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(3, 2) > UInteger(0, 0)).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(0, 2) > UInteger(0, 2)).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(0, 2) > UInteger(0, 1)).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(0, 2) > UInteger(1, 0.25)).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(0, 2) > UInteger(-1, 0.25)).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(0, 2) > UInteger(5, 2)).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(5, 2) > UInteger(0, 2)).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(0, 0) > UReal(0, 0)).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(0, 0) > UReal(1, 0)).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(3, 0) > UReal(0, 0)).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(0, 0) > UReal(3, 2)).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(3, 0) > UReal(0, 2)).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(0, 2) > UReal(3, 0)).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(3, 2) > UReal(0, 0)).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(0, 2) > UReal(0, 2)).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(0, 2) > UReal(0, 1)).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(0, 2) > UReal(1, 0.25)).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(0, 2) > UReal(-1, 0.25)).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(0, 2) > UReal(5, 2)).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(5, 2) > UReal(0, 2)).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(0, 0) > 0).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(0, 0) > 1).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(1, 0) > 0).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(0, 2) > 3).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(3, 2) > 0).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(0, 0) > 0.0).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(0, 0) > 1.0).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(1, 0) > 0.0).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(0, 2) > 3.0).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(3, 2) > 0.0).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(0, 0) >= UInteger(0, 0)).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(0, 0) >= UInteger(1, 0)).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(3, 0) >= UInteger(0, 0)).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(0, 0) >= UInteger(3, 2)).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(3, 0) >= UInteger(0, 2)).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(0, 2) >= UInteger(3, 0)).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(3, 2) >= UInteger(0, 0)).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(0, 2) >= UInteger(0, 2)).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(0, 2) >= UInteger(0, 1)).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(0, 2) >= UInteger(1, 0.25)).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(0, 2) >= UInteger(-1, 0.25)).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(0, 2) >= UInteger(5, 2)).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(5, 2) >= UInteger(0, 2)).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(0, 0) >= UReal(0, 0)).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(0, 0) >= UReal(1, 0)).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(3, 0) >= UReal(0, 0)).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(0, 0) >= UReal(3, 2)).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(3, 0) >= UReal(0, 2)).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(0, 2) >= UReal(3, 0)).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(3, 2) >= UReal(0, 0)).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(0, 2) >= UReal(0, 2)).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(0, 2) >= UReal(0, 1)).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(0, 2) >= UReal(1, 0.25)).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(0, 2) >= UReal(-1, 0.25)).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(0, 2) >= UReal(5, 2)).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(5, 2) >= UReal(0, 2)).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(0, 0) >= 0).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(0, 0) >= 1).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(1, 0) >= 0).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(0, 2) >= 3).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(3, 2) >= 0).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(0, 0) >= 0.0).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(0, 0) >= 1.0).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(1, 0) >= 0.0).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(0, 2) >= 3.0).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(3, 2) >= 0.0).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(0, 0) = UInteger(0, 0)).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(0, 0) = UInteger(1, 0)).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(3, 0) = UInteger(0, 0)).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(0, 0) = UInteger(3, 2)).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(3, 0) = UInteger(0, 2)).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(0, 2) = UInteger(3, 0)).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(3, 2) = UInteger(0, 0)).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(0, 2) = UInteger(0, 2)).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(0, 2) = UInteger(0, 1)).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(0, 2) = UInteger(1, 0.25)).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(0, 2) = UInteger(-1, 0.25)).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(0, 2) = UInteger(5, 2)).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(5, 2) = UInteger(0, 2)).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(0, 0) = UReal(0, 0)).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(0, 0) = UReal(1, 0)).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(3, 0) = UReal(0, 0)).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(0, 0) = UReal(3, 2)).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(3, 0) = UReal(0, 2)).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(0, 2) = UReal(3, 0)).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(3, 2) = UReal(0, 0)).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(0, 2) = UReal(0, 2)).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(0, 2) = UReal(0, 1)).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(0, 2) = UReal(1, 0.25)).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(0, 2) = UReal(-1, 0.25)).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(0, 2) = UReal(5, 2)).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(5, 2) = UReal(0, 2)).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(0, 0) = 0).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(0, 0) = 1).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(1, 0) = 0).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(0, 2) = 3).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(3, 2) = 0).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(0, 0) = 0.0).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(0, 0) = 1.0).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(1, 0) = 0.0).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(0, 2) = 3.0).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(3, 2) = 0.0).toBoolean()
		-> false : Boolean

	Expression : 
		UInteger(2, 3) = Undefined
		-> false : Boolean

	Expression : 
		UInteger(2, 3) = null
		-> false : Boolean

	Expression : 
		(UInteger(0, 0) <> UInteger(0, 0)).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(0, 0) <> UInteger(1, 0)).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(3, 0) <> UInteger(0, 0)).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(0, 0) <> UInteger(3, 2)).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(3, 0) <> UInteger(0, 2)).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(0, 2) <> UInteger(3, 0)).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(3, 2) <> UInteger(0, 0)).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(0, 2) <> UInteger(0, 2)).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(0, 2) <> UInteger(0, 1)).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(0, 2) <> UInteger(1, 0.25)).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(0, 2) <> UInteger(-1, 0.25)).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(0, 2) <> UInteger(5, 2)).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(5, 2) <> UInteger(0, 2)).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(0, 0) <> UReal(0, 0)).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(0, 0) <> UReal(1, 0)).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(3, 0) <> UReal(0, 0)).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(0, 0) <> UReal(3, 2)).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(3, 0) <> UReal(0, 2)).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(0, 2) <> UReal(3, 0)).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(3, 2) <> UReal(0, 0)).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(0, 2) <> UReal(0, 2)).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(0, 2) <> UReal(0, 1)).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(0, 2) <> UReal(1, 0.25)).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(0, 2) <> UReal(-1, 0.25)).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(0, 2) <> UReal(5, 2)).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(5, 2) <> UReal(0, 2)).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(0, 0) <> 0).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(0, 0) <> 1).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(1, 0) <> 0).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(0, 2) <> 3).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(3, 2) <> 0).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(0, 0) <> 0.0).toBoolean()
		-> false : Boolean

	Expression : 
		(UInteger(0, 0) <> 1.0).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(1, 0) <> 0.0).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(0, 2) <> 3.0).toBoolean()
		-> true : Boolean

	Expression : 
		(UInteger(3, 2) <> 0.0).toBoolean()
		-> true : Boolean

	Expression : 
		UInteger(2, 3) <> Undefined
		-> true : Boolean

	Expression : 
		UInteger(2, 3) <> null
		-> true : Boolean

-----------------------------------------------------------------
File : URealExpression.in
	Expression : 
		UReal(2, 0)
		-> UReal(2.0, 0.0) : UReal

	Expression : 
		UReal(2, 2)
		-> UReal(2.0, 2.0) : UReal

	Expression : 
		UReal(2, -2)
		-> UReal(2.0, 2.0) : UReal

	Expression : 
		UReal(0, 0)
		-> UReal(0.0, 0.0) : UReal

	Expression : 
		UReal(0, 2)
		-> UReal(0.0, 2.0) : UReal

	Expression : 
		UReal(0, -2)
		-> UReal(0.0, 2.0) : UReal

	Expression : 
		UReal(2+2, 3)
		-> UReal(4.0, 3.0) : UReal

	Expression : 
		UReal(55.23, 9.34)
		-> UReal(55.23, 9.34) : UReal

	Expression : 
		UReal(55.23, 0.34)
		-> UReal(55.23, 0.34) : UReal

	Expression : 
		UReal(55.23, -66.34)
		-> UReal(55.23, 66.34) : UReal

	Expression : 
		UReal(0.34, 55.23)
		-> UReal(0.34, 55.23) : UReal

	Expression : 
		UReal(0.34, 0.34)
		-> UReal(0.34, 0.34) : UReal

	Expression : 
		UReal(0.34, -66.34)
		-> UReal(0.34, 66.34) : UReal

	Expression : 
		UReal(-66.34, 55.23)
		-> UReal(-66.34, 55.23) : UReal

	Expression : 
		UReal(-66.34, 0.34)
		-> UReal(-66.34, 0.34) : UReal

	Expression : 
		UReal(-66.34, -66.34)
		-> UReal(-66.34, 66.34) : UReal

	Expression : 
		UReal(2.3, 5)
		-> UReal(2.3, 5.0) : UReal

	Expression : 
		UReal(3*3/5, 9*(3-4))
		-> UReal(1.8, 9.0) : UReal

	Expression : 
		UReal('Hola', 9.3)
		-> Value must be Integer or Real

	Expression : 
		UReal(9.3, 'Hola')
		-> Uncertainty must be Integer or Real

	Expression : 
		UReal(2, 2 + 3/0)
		-> null : OclVoid

	Expression : 
		UReal(2 / 0, 3)
		-> null : OclVoid

	Expression : 
		UReal(3 / 0, 2 / 0)
		-> null : OclVoid

	Expression : 
		UReal(2.3, 5).oclIsTypeOf(UReal)
		-> true : Boolean

	Expression : 
		(3.2).oclIsKindOf(UReal)
		-> true : Boolean

	Expression : 
		2.oclIsKindOf(UReal)
		-> true : Boolean

	Expression : 
		UReal(2, 3).abs()
		-> UReal(2.0, 3.0) : UReal

	Expression : 
		UReal(0, 3).abs()
		-> UReal(0.0, 3.0) : UReal

	Expression : 
		UReal(-2, 3).abs()
		-> UReal(2.0, 3.0) : UReal

	Expression : 
		UReal(-3, 2.3).value()
		-> -3.0 : Real

	Expression : 
		UReal(0, 2.3).value()
		-> 0.0 : Real

	Expression : 
		UReal(3, 2.3).value()
		-> 3.0 : Real

	Expression : 
		UReal(-2, 3).setValue(0.0)
		-> UReal(0.0, 3.0) : UReal

	Expression : 
		UReal(-2, 3).setValue(-2.0)
		-> UReal(-2.0, 3.0) : UReal

	Expression : 
		UReal(-2, 3).setValue(-2)
		-> UReal(-2.0, 3.0) : UReal

	Expression : 
		UReal(-2, 3).setValue(3 / 0)
		-> null : OclVoid

	Expression : 
		UReal(-3, -2.3).uncertainty()
		-> 2.3 : Real

	Expression : 
		UReal(-3, 0).uncertainty()
		-> 0.0 : Real

	Expression : 
		UReal(-3, 0).setUncertainty(-3)
		-> UReal(-3.0, 3.0) : UReal

	Expression : 
		UReal(-3, 0).setUncertainty(3)
		-> UReal(-3.0, 3.0) : UReal

	Expression : 
		UReal(-3, 0).setUncertainty(3.0)
		-> UReal(-3.0, 3.0) : UReal

	Expression : 
		UReal(-3, 0).setUncertainty(3 / 0)
		-> null : OclVoid

	Expression : 
		UReal(-3, 2.3).sqrt()
		-> null : OclVoid

	Expression : 
		UReal(0, 2).sqrt()
		-> null : OclVoid

	Expression : 
		UReal(4, 0).sqrt()
		-> UReal(2.0, 0.0) : UReal

	Expression : 
		UReal(4, 2).sqrt()
		-> UReal(2.0, 0.5) : UReal

	Expression : 
		UReal(0, 0).power(0)
		-> null : OclVoid

	Expression : 
		UReal(0, 0).power(3)
		-> UReal(0.0, 0.0) : UReal

	Expression : 
		UReal(0, 0).power(-2)
		-> null : OclVoid

	Expression : 
		UReal(0, 0).power(3.5)
		-> UReal(0.0, 0.0) : UReal

	Expression : 
		UReal(0, 2).power(0)
		-> null : OclVoid

	Expression : 
		UReal(0, 4).power(3)
		-> UReal(0.0, 0.0) : UReal

	Expression : 
		UReal(0, 3).power(-3)
		-> null : OclVoid

	Expression : 
		UReal(0, 1).power(3.5)
		-> UReal(0.0, 0.0) : UReal

	Expression : 
		UReal(3, 0).power(0)
		-> UReal(1.0, 0.0) : UReal

	Expression : 
		UReal(2, 0).power(3)
		-> UReal(8.0, 0.0) : UReal

	Expression : 
		UReal(4, 0).power(-2)
		-> UReal(0.0625, 0.0) : UReal

	Expression : 
		UReal(4, 0).power(1.5)
		-> UReal(8.0, 0.0) : UReal

	Expression : 
		UReal(1.5, 3.2).power(0)
		-> UReal(1.0, 0.0) : UReal

	Expression : 
		UReal(2, 4).power(4)
		-> UReal(16.0, 128.0) : UReal

	Expression : 
		UReal(1, 3).power(-2)
		-> UReal(1.0, 6.0) : UReal

	Expression : 
		UReal(1, 2).power(0.25)
		-> UReal(1.0, 0.5) : UReal

	Expression : 
		UReal(-2, 5).power(1/2).equals( UReal(-2, 5).sqrt() )
		-> true : Boolean

	Expression : 
		UReal(0, 5).power(1/2).equals( UReal(0, 5).sqrt() )
		-> true : Boolean

	Expression : 
		UReal(3.0, 2.3).neg()
		-> UReal(-3.0, 2.3) : UReal

	Expression : 
		UReal(0.0, 2.3).neg()
		-> UReal(0.0, 2.3) : UReal

	Expression : 
		UReal(-3.0, 2.3).neg()
		-> UReal(3.0, 2.3) : UReal

	Expression : 
		UReal(3.7, 3.2).floor()
		-> UReal(3.0, 3.2) : UReal

	Expression : 
		UReal(3.2, 3.2).floor()
		-> UReal(3.0, 3.2) : UReal

	Expression : 
		UReal(3.5, 3.2).floor()
		-> UReal(3.0, 3.2) : UReal

	Expression : 
		UReal(2, 3).round()
		-> UReal(2.0, 3.0) : UReal

	Expression : 
		UReal(2.7, 3).round()
		-> UReal(3.0, 3.0) : UReal

	Expression : 
		UReal(2.5, 3).round()
		-> UReal(3.0, 3.0) : UReal

	Expression : 
		UReal(2.2, 3).round()
		-> UReal(2.0, 3.0) : UReal

	Expression : 
		UReal(-0.5, 3).round()
		-> UReal(0.0, 3.0) : UReal

	Expression : 
		UReal(-0.8, 3).round()
		-> UReal(-1.0, 3.0) : UReal

	Expression : 
		(UReal(3.2, 3).floor()).equals( UReal(3.2, 3.0).round() )
		-> true : Boolean

	Expression : 
		(UReal(3, 3).floor()).equals( UReal(3, 3.0).round() )
		-> true : Boolean

	Expression : 
		(UReal(3.5, 3).floor()).equals( UReal(3.5, 3.0).round() )
		-> false : Boolean

	Expression : 
		(UReal(3.9, 3).floor()).equals( UReal(3.9, 3.0).round() )
		-> false : Boolean

	Expression : 
		UReal(8, 0.75).inv()
		-> UReal(0.125, 0.01171875) : UReal

	Expression : 
		UReal(0, 0.5).inv()
		-> null : OclVoid

	Expression : 
		UReal(0.0, 0.0).min(UReal(0.0, 0.0))
		-> UReal(0.0, 0.0) : UReal

	Expression : 
		UReal(0.0, 0.0).min(UReal(1.0, 0.0))
		-> UReal(0.0, 0.0) : UReal

	Expression : 
		UReal(1.0, 0.0).min(UReal(0.0, 0.0))
		-> UReal(0.0, 0.0) : UReal

	Expression : 
		UReal(3.0, 0.0).min(UReal(0.0, 0.0))
		-> UReal(0.0, 0.0) : UReal

	Expression : 
		UReal(0.0, 0.0).min(UReal(3.0, 0.0))
		-> UReal(0.0, 0.0) : UReal

	Expression : 
		UReal(0.0, 0.0).min(UReal(3.0, 2.0))
		-> UReal(0.0, 0.0) : UReal

	Expression : 
		UReal(3.0, 2.0).min(UReal(0.0, 0.0))
		-> UReal(0.0, 0.0) : UReal

	Expression : 
		UReal(3.0, 0.0).min(UReal(0.0, 2.0))
		-> UReal(0.0, 2.0) : UReal

	Expression : 
		UReal(0.0, 2.0).min(UReal(3.0, 0.0))
		-> UReal(0.0, 2.0) : UReal

	Expression : 
		UReal(3.0, 2.0).min(UReal(0.0, 0.0))
		-> UReal(0.0, 0.0) : UReal

	Expression : 
		UReal(0.0, 0.0).min(UReal(3.0, 2.0))
		-> UReal(0.0, 0.0) : UReal

	Expression : 
		UReal(0.0, 2.0).min(UReal(0.0, 0.2))
		-> UReal(0.0, 2.0) : UReal

	Expression : 
		UReal(0.0, 2.0).min(UReal(1.0, 0.0))
		-> UReal(0.0, 2.0) : UReal

	Expression : 
		UReal(1.0, 0.0).min(UReal(0.0, 2.0))
		-> UReal(0.0, 2.0) : UReal

	Expression : 
		UReal(0.0, 2.0).min(UReal(-1.0, 0.25))
		-> UReal(-1.0, 0.25) : UReal

	Expression : 
		UReal(-1.0, 0.25).min(UReal(0.0, 2.0))
		-> UReal(-1.0, 0.25) : UReal

	Expression : 
		UReal(0.0, 2.0).min(UReal(5.0, 2.0))
		-> UReal(0.0, 2.0) : UReal

	Expression : 
		UReal(5.0, 2.0).min(UReal(0.0, 2.0))
		-> UReal(0.0, 2.0) : UReal

	Expression : 
		UReal(0.0, 0.0).min(0)
		-> UReal(0.0, 0.0) : UReal

	Expression : 
		0.min(UReal(0.0, 0.0))
		-> UReal(0.0, 0.0) : UReal

	Expression : 
		UReal(0.0, 0.0).min(1)
		-> UReal(0.0, 0.0) : UReal

	Expression : 
		1.min(UReal(0.0, 0.0))
		-> UReal(0.0, 0.0) : UReal

	Expression : 
		UReal(1.0, 0.0).min(0)
		-> UReal(0.0, 0.0) : UReal

	Expression : 
		0.min(UReal(1.0, 0.0))
		-> UReal(0.0, 0.0) : UReal

	Expression : 
		UReal(0.0, 2.0).min(3)
		-> UReal(0.0, 2.0) : UReal

	Expression : 
		3.min(UReal(0.0, 2.0))
		-> UReal(0.0, 2.0) : UReal

	Expression : 
		UReal(3.0, 2.0).min(0)
		-> UReal(0.0, 0.0) : UReal

	Expression : 
		0.min(UReal(3.0, 2.0))
		-> UReal(0.0, 0.0) : UReal

	Expression : 
		UReal(0.0, 0.0).min(0.0)
		-> UReal(0.0, 0.0) : UReal

	Expression : 
		0.0.min(UReal(0.0, 0.0))
		-> UReal(0.0, 0.0) : UReal

	Expression : 
		UReal(0.0, 0.0).min(1.5)
		-> UReal(0.0, 0.0) : UReal

	Expression : 
		1.5.min(UReal(0.0, 0.0))
		-> UReal(0.0, 0.0) : UReal

	Expression : 
		UReal(1.0, 0.0).min(0)
		-> UReal(0.0, 0.0) : UReal

	Expression : 
		0.min(UReal(1.0, 0.0))
		-> UReal(0.0, 0.0) : UReal

	Expression : 
		UReal(0.0, 2.0).min(2.5)
		-> UReal(0.0, 2.0) : UReal

	Expression : 
		2.5.min(UReal(0.0, 2.0))
		-> UReal(0.0, 2.0) : UReal

	Expression : 
		0.min(UReal(3.0, 2.0))
		-> UReal(0.0, 0.0) : UReal

	Expression : 
		UReal(0.0, 0.0).min(3 / 0)
		-> null : OclVoid

	Expression : 
		(3 / 0).min(UReal(0.0, 0.0))
		-> null : OclVoid

	Expression : 
		UReal(0.0, 0.0).max(UReal(0.0, 0.0))
		-> UReal(0.0, 0.0) : UReal

	Expression : 
		UReal(0.0, 0.0).max(UReal(1.0, 0.0))
		-> UReal(1.0, 0.0) : UReal

	Expression : 
		UReal(1.0, 0.0).max(UReal(0.0, 0.0))
		-> UReal(1.0, 0.0) : UReal

	Expression : 
		UReal(3.0, 0.0).max(UReal(0.0, 0.0))
		-> UReal(3.0, 0.0) : UReal

	Expression : 
		UReal(0.0, 0.0).max(UReal(3.0, 0.0))
		-> UReal(3.0, 0.0) : UReal

	Expression : 
		UReal(0.0, 0.0).max(UReal(3.0, 2.0))
		-> UReal(3.0, 2.0) : UReal

	Expression : 
		UReal(3.0, 2.0).max(UReal(0.0, 0.0))
		-> UReal(3.0, 2.0) : UReal

	Expression : 
		UReal(3.0, 0.0).max(UReal(0.0, 2.0))
		-> UReal(3.0, 0.0) : UReal

	Expression : 
		UReal(0.0, 2.0).max(UReal(3.0, 0.0))
		-> UReal(3.0, 0.0) : UReal

	Expression : 
		UReal(3.0, 2.0).max(UReal(0.0, 0.0))
		-> UReal(3.0, 2.0) : UReal

	Expression : 
		UReal(0.0, 0.0).max(UReal(3.0, 2.0))
		-> UReal(3.0, 2.0) : UReal

	Expression : 
		UReal(0.0, 2.0).max(UReal(0.0, 0.2))
		-> UReal(0.0, 2.0) : UReal

	Expression : 
		UReal(0.0, 2.0).max(UReal(1.0, 0.0))
		-> UReal(1.0, 0.0) : UReal

	Expression : 
		UReal(1.0, 0.0).max(UReal(0.0, 2.0))
		-> UReal(1.0, 0.0) : UReal

	Expression : 
		UReal(0.0, 2.0).max(UReal(-1.0, 0.25))
		-> UReal(0.0, 2.0) : UReal

	Expression : 
		UReal(-1.0, 0.25).max(UReal(0.0, 2.0))
		-> UReal(0.0, 2.0) : UReal

	Expression : 
		UReal(0.0, 2.0).max(UReal(5.0, 2.0))
		-> UReal(5.0, 2.0) : UReal

	Expression : 
		UReal(0.0, 0.0).max(0)
		-> UReal(0.0, 0.0) : UReal

	Expression : 
		0.max(UReal(0.0, 0.0))
		-> UReal(0.0, 0.0) : UReal

	Expression : 
		UReal(0.0, 0.0).max(1)
		-> UReal(1.0, 0.0) : UReal

	Expression : 
		1.max(UReal(0.0, 0.0))
		-> UReal(1.0, 0.0) : UReal

	Expression : 
		UReal(1.0, 0.0).max(0)
		-> UReal(1.0, 0.0) : UReal

	Expression : 
		0.max(UReal(1.0, 0.0))
		-> UReal(1.0, 0.0) : UReal

	Expression : 
		UReal(0.0, 2.0).max(3)
		-> UReal(3.0, 0.0) : UReal

	Expression : 
		3.max(UReal(0.0, 2.0))
		-> UReal(3.0, 0.0) : UReal

	Expression : 
		UReal(3.0, 2.0).max(0)
		-> UReal(3.0, 2.0) : UReal

	Expression : 
		0.max(UReal(3.0, 2.0))
		-> UReal(3.0, 2.0) : UReal

	Expression : 
		UReal(0.0, 0.0).max(0.0)
		-> UReal(0.0, 0.0) : UReal

	Expression : 
		0.0.max(UReal(0.0, 0.0))
		-> UReal(0.0, 0.0) : UReal

	Expression : 
		UReal(0.0, 0.0).max(1.5)
		-> UReal(1.5, 0.0) : UReal

	Expression : 
		1.5.max(UReal(0.0, 0.0))
		-> UReal(1.5, 0.0) : UReal

	Expression : 
		UReal(1.0, 0.0).max(0)
		-> UReal(1.0, 0.0) : UReal

	Expression : 
		0.max(UReal(1.0, 0.0))
		-> UReal(1.0, 0.0) : UReal

	Expression : 
		UReal(0.0, 2.0).max(2.5)
		-> UReal(2.5, 0.0) : UReal

	Expression : 
		2.5.max(UReal(0.0, 2.0))
		-> UReal(2.5, 0.0) : UReal

	Expression : 
		UReal(3.0, 2.0).max(0)
		-> UReal(3.0, 2.0) : UReal

	Expression : 
		0.max(UReal(3.0, 2.0))
		-> UReal(3.0, 2.0) : UReal

	Expression : 
		UReal(3.0, 2.0).max(3 / 0)
		-> null : OclVoid

	Expression : 
		(3 / 0).max(UReal(3.0, 2.0))
		-> null : OclVoid

	Expression : 
		UReal(-2, 0).toReal()
		-> -2.0 : Real

	Expression : 
		UReal(-2, 2).toReal()
		-> -2.0 : Real

	Expression : 
		UReal(0, 0).toReal()
		-> 0.0 : Real

	Expression : 
		UReal(0, 3).toReal()
		-> 0.0 : Real

	Expression : 
		UReal(3, 0).toReal()
		-> 3.0 : Real

	Expression : 
		UReal(3, 5).toReal()
		-> 3.0 : Real

	Expression : 
		UReal(0.5, 3.2).toReal()
		-> 0.5 : Real

	Expression : 
		UReal(-2, 0).toInteger()
		-> -2 : Integer

	Expression : 
		UReal(-2, 2).toInteger()
		-> -2 : Integer

	Expression : 
		UReal(0, 0).toInteger()
		-> 0 : Integer

	Expression : 
		UReal(0, 3).toInteger()
		-> 0 : Integer

	Expression : 
		UReal(3, 0).toInteger()
		-> 3 : Integer

	Expression : 
		UReal(3, 5).toInteger()
		-> 3 : Integer

	Expression : 
		UReal(0.5, 3.2).toInteger()
		-> 0 : Integer

	Expression : 
		UReal(5.0, 0.3).toUInteger()
		-> UInteger(5, 0.3) : UInteger

	Expression : 
		UReal(5.5, 5).toUInteger()
		-> UInteger(5, 5.0) : UInteger

	Expression : 
		UReal(0, -5).toUInteger()
		-> UInteger(0, 5.0) : UInteger

	Expression : 
		UReal(-5.3, 3.75).toUInteger()
		-> UInteger(-5, 3.75) : UInteger

	Expression : 
		UReal(-2, 0).toString()
		-> 'UReal(-2.0, 0.0)' : String

	Expression : 
		UReal(-2, 2).toString()
		-> 'UReal(-2.0, 2.0)' : String

	Expression : 
		UReal(0, 0).toString()
		-> 'UReal(0.0, 0.0)' : String

	Expression : 
		UReal(0, 3).toString()
		-> 'UReal(0.0, 3.0)' : String

	Expression : 
		UReal(3, 0).toString()
		-> 'UReal(3.0, 0.0)' : String

	Expression : 
		UReal(3, 5).toString()
		-> 'UReal(3.0, 5.0)' : String

	Expression : 
		UReal(0.5, 3.2).toString()
		-> 'UReal(0.5, 3.2)' : String

	Expression : 
		UReal(-9, 0) + UReal(-9, 0)
		-> UReal(-18.0, 0.0) : UReal

	Expression : 
		UReal(-3, 0) + UReal(-3, 9)
		-> UReal(-6.0, 9.0) : UReal

	Expression : 
		UReal(-7, 0) + UReal(3, 0)
		-> UReal(-4.0, 0.0) : UReal

	Expression : 
		UReal(-2, 0) + UReal(10, 7)
		-> UReal(8.0, 7.0) : UReal

	Expression : 
		UReal(-9, 7) + UReal(-9, 0)
		-> UReal(-18.0, 7.0) : UReal

	Expression : 
		UReal(-3, 3) + UReal(-3, 4)
		-> UReal(-6.0, 5.0) : UReal

	Expression : 
		UReal(-9, 3) + UReal(7, 0)
		-> UReal(-2.0, 3.0) : UReal

	Expression : 
		UReal(-6, 3) + UReal(10, 4)
		-> UReal(4.0, 5.0) : UReal

	Expression : 
		UReal(0, 0) + UReal(0, 0)
		-> UReal(0.0, 0.0) : UReal

	Expression : 
		UReal(0, 0) + UReal(0, 1)
		-> UReal(0.0, 1.0) : UReal

	Expression : 
		UReal(0, 0) + UReal(6, 0)
		-> UReal(6.0, 0.0) : UReal

	Expression : 
		UReal(0, 0) + UReal(9, 4)
		-> UReal(9.0, 4.0) : UReal

	Expression : 
		UReal(0, 2) + UReal(0, 0)
		-> UReal(0.0, 2.0) : UReal

	Expression : 
		UReal(0, 3) + UReal(0, 4)
		-> UReal(0.0, 5.0) : UReal

	Expression : 
		UReal(0, 4) + UReal(2, 0)
		-> UReal(2.0, 4.0) : UReal

	Expression : 
		UReal(0, 3) + UReal(8, 4)
		-> UReal(8.0, 5.0) : UReal

	Expression : 
		UReal(9, 0) + UReal(9, 0)
		-> UReal(18.0, 0.0) : UReal

	Expression : 
		UReal(3, 0) + UReal(3, 1)
		-> UReal(6.0, 1.0) : UReal

	Expression : 
		UReal(7, 0) + UReal(8, 0)
		-> UReal(15.0, 0.0) : UReal

	Expression : 
		UReal(2, 0) + UReal(7, 8)
		-> UReal(9.0, 8.0) : UReal

	Expression : 
		UReal(9, 9) + UReal(9, 0)
		-> UReal(18.0, 9.0) : UReal

	Expression : 
		UReal(3, 3) + UReal(3, 4)
		-> UReal(6.0, 5.0) : UReal

	Expression : 
		UReal(9, 2) + UReal(10, 0)
		-> UReal(19.0, 2.0) : UReal

	Expression : 
		UReal(6, 3) + UReal(1, 4)
		-> UReal(7.0, 5.0) : UReal

	Expression : 
		UReal(-3, 0) + -3.0
		-> UReal(-6.0, 0.0) : UReal

	Expression : 
		UReal(-6, 0) + -1.2
		-> UReal(-7.2, 0.0) : UReal

	Expression : 
		UReal(-5, 3) + -5.0
		-> UReal(-10.0, 3.0) : UReal

	Expression : 
		UReal(-8, 5) + -2.0
		-> UReal(-10.0, 5.0) : UReal

	Expression : 
		UReal(0, 0) + 0.0
		-> UReal(0.0, 0.0) : UReal

	Expression : 
		UReal(0, 0) + 3.0
		-> UReal(3.0, 0.0) : UReal

	Expression : 
		UReal(0, 3) + 0.0
		-> UReal(0.0, 3.0) : UReal

	Expression : 
		UReal(0, 5) + -5.0
		-> UReal(-5.0, 5.0) : UReal

	Expression : 
		UReal(5, 0) + 5.0
		-> UReal(10.0, 0.0) : UReal

	Expression : 
		UReal(3, 0) + 0.6
		-> UReal(3.6, 0.0) : UReal

	Expression : 
		UReal(7, 3) + 7.0
		-> UReal(14.0, 3.0) : UReal

	Expression : 
		UReal(2, 5) + 0.5
		-> UReal(2.5, 5.0) : UReal

	Expression : 
		UReal(-3, 0) + -3
		-> UReal(-6.0, 0.0) : UReal

	Expression : 
		UReal(-6, 0) + -12
		-> UReal(-18.0, 0.0) : UReal

	Expression : 
		UReal(-5, 3) + -5
		-> UReal(-10.0, 3.0) : UReal

	Expression : 
		UReal(-8, 5) + -2
		-> UReal(-10.0, 5.0) : UReal

	Expression : 
		UReal(0, 0) + 0
		-> UReal(0.0, 0.0) : UReal

	Expression : 
		UReal(0, 0) + 3
		-> UReal(3.0, 0.0) : UReal

	Expression : 
		UReal(0, 3) + 0
		-> UReal(0.0, 3.0) : UReal

	Expression : 
		UReal(0, 5) + -5
		-> UReal(-5.0, 5.0) : UReal

	Expression : 
		UReal(5, 0) + 5
		-> UReal(10.0, 0.0) : UReal

	Expression : 
		UReal(3, 0) + 56
		-> UReal(59.0, 0.0) : UReal

	Expression : 
		UReal(7, 3) + 7
		-> UReal(14.0, 3.0) : UReal

	Expression : 
		UReal(2, 5) + 65
		-> UReal(67.0, 5.0) : UReal

	Expression : 
		UReal(2, 5) + 3 / 0
		-> null : OclVoid

	Expression : 
		( UReal(2, 5) + UReal(0, 0) ).equals( UReal(2, 5) )
		-> true : Boolean

	Expression : 
		( UReal(2, 5) + 0.0 ).equals( UReal(2, 5) )
		-> true : Boolean

	Expression : 
		( UReal(2, 5) + 0 ).equals( UReal(2, 5) )
		-> true : Boolean

	Expression : 
		( UReal(6, 3) + UReal(5, 0.3) ).equals( UReal(5, 0.3) + UReal(6, 3) )
		-> true : Boolean

	Expression : 
		( UReal(9, 32) + 0.53 ).equals( 0.53 + UReal(9, 32) )
		-> true : Boolean

	Expression : 
		( UReal(2, 3) + 5 ).equals( 5 + UReal(2, 3) )
		-> true : Boolean

	Expression : 
		( UReal(6, 3) + (UReal(5, 3) + UReal(9,2)) ).equals( (UReal(6, 3) + UReal(5, 3)) + UReal(9,2) )
		-> true : Boolean

	Expression : 
		( UReal(6, 3) + (5.3 + UReal(9,2)) ).equals( (UReal(6, 3) + 5.3) + UReal(9,2) )
		-> true : Boolean

	Expression : 
		( UReal(6, 3) + (5 + UReal(9,2)) ).equals( (UReal(6, 3) + 5) + UReal(9,2) )
		-> true : Boolean

	Expression : 
		( UReal(6, 3) + (5 + 2) ).equals( (UReal(6, 3) + 5) + 2 )
		-> true : Boolean

	Expression : 
		( 3.5 + (5 + UReal(9,2)) ).equals( (3.5 + 5) + UReal(9,2) )
		-> true : Boolean

	Expression : 
		UReal(-9, 0) - UReal(-9, 0)
		-> UReal(0.0, 0.0) : UReal

	Expression : 
		UReal(-5, 0) - UReal(-5, 3)
		-> UReal(0.0, 3.0) : UReal

	Expression : 
		UReal(-4, 0) - UReal(2, 0)
		-> UReal(-6.0, 0.0) : UReal

	Expression : 
		UReal(-10, 0) - UReal(4, 1)
		-> UReal(-14.0, 1.0) : UReal

	Expression : 
		UReal(-9, 9) - UReal(-9, 0)
		-> UReal(0.0, 9.0) : UReal

	Expression : 
		UReal(-2, 3) - UReal(-2, 4)
		-> UReal(0.0, 5.0) : UReal

	Expression : 
		UReal(-6, 2) - UReal(5, 0)
		-> UReal(-11.0, 2.0) : UReal

	Expression : 
		UReal(-2, 3) - UReal(4, 4)
		-> UReal(-6.0, 5.0) : UReal

	Expression : 
		UReal(0, 0) - UReal(0, 0)
		-> UReal(0.0, 0.0) : UReal

	Expression : 
		UReal(0, 0) - UReal(0, 4)
		-> UReal(0.0, 4.0) : UReal

	Expression : 
		UReal(0, 0) - UReal(6, 0)
		-> UReal(-6.0, 0.0) : UReal

	Expression : 
		UReal(0, 0) - UReal(7, 3)
		-> UReal(-7.0, 3.0) : UReal

	Expression : 
		UReal(0, 4) - UReal(0, 0)
		-> UReal(0.0, 4.0) : UReal

	Expression : 
		UReal(0, 4) - UReal(0, 3)
		-> UReal(0.0, 5.0) : UReal

	Expression : 
		UReal(0, 4) - UReal(1, 0)
		-> UReal(-1.0, 4.0) : UReal

	Expression : 
		UReal(0, 4) - UReal(2, 3)
		-> UReal(-2.0, 5.0) : UReal

	Expression : 
		UReal(9, 0) - UReal(9, 0)
		-> UReal(0.0, 0.0) : UReal

	Expression : 
		UReal(5, 0) - UReal(5, 3)
		-> UReal(0.0, 3.0) : UReal

	Expression : 
		UReal(4, 0) - UReal(8, 0)
		-> UReal(-4.0, 0.0) : UReal

	Expression : 
		UReal(10, 0) - UReal(10, 12)
		-> UReal(0.0, 12.0) : UReal

	Expression : 
		UReal(9, 5) - UReal(9, 0)
		-> UReal(0.0, 5.0) : UReal

	Expression : 
		UReal(2, 3) - UReal(2, 4)
		-> UReal(0.0, 5.0) : UReal

	Expression : 
		UReal(6, 1) - UReal(4, 0)
		-> UReal(2.0, 1.0) : UReal

	Expression : 
		UReal(2, 3) - UReal(5, 4)
		-> UReal(-3.0, 5.0) : UReal

	Expression : 
		UReal(-3, 0) - -3.0
		-> UReal(0.0, 0.0) : UReal

	Expression : 
		UReal(-6, 0) - -1.2
		-> UReal(-4.8, 0.0) : UReal

	Expression : 
		UReal(-5, 3) - -5.0
		-> UReal(0.0, 3.0) : UReal

	Expression : 
		UReal(-8, 5) - -2.0
		-> UReal(-6.0, 5.0) : UReal

	Expression : 
		UReal(0, 0) - 0.0
		-> UReal(0.0, 0.0) : UReal

	Expression : 
		UReal(0, 0) - 3.0
		-> UReal(-3.0, 0.0) : UReal

	Expression : 
		UReal(0, 3) - 0.0
		-> UReal(0.0, 3.0) : UReal

	Expression : 
		UReal(0, 5) - -5.0
		-> UReal(5.0, 5.0) : UReal

	Expression : 
		UReal(5, 0) - 5.0
		-> UReal(0.0, 0.0) : UReal

	Expression : 
		UReal(3, 0) - 0.6
		-> UReal(2.4, 0.0) : UReal

	Expression : 
		UReal(7, 3) - 7.0
		-> UReal(0.0, 3.0) : UReal

	Expression : 
		UReal(2, 5) - 0.5
		-> UReal(1.5, 5.0) : UReal

	Expression : 
		UReal(-3, 0) - -3
		-> UReal(0.0, 0.0) : UReal

	Expression : 
		UReal(-6, 0) - -12
		-> UReal(6.0, 0.0) : UReal

	Expression : 
		UReal(-5, 3) - -5
		-> UReal(0.0, 3.0) : UReal

	Expression : 
		UReal(-8, 5) - -2
		-> UReal(-6.0, 5.0) : UReal

	Expression : 
		UReal(0, 0) - 0
		-> UReal(0.0, 0.0) : UReal

	Expression : 
		UReal(0, 0) - 3
		-> UReal(-3.0, 0.0) : UReal

	Expression : 
		UReal(0, 3) - 0
		-> UReal(0.0, 3.0) : UReal

	Expression : 
		UReal(0, 5) - -5
		-> UReal(5.0, 5.0) : UReal

	Expression : 
		UReal(5, 0) - 5
		-> UReal(0.0, 0.0) : UReal

	Expression : 
		UReal(3, 0) - 56
		-> UReal(-53.0, 0.0) : UReal

	Expression : 
		UReal(7, 3) - 7
		-> UReal(0.0, 3.0) : UReal

	Expression : 
		UReal(2, 5) - 65
		-> UReal(-63.0, 5.0) : UReal

	Expression : 
		( UReal(3, 0) - 3.0 ).equals( 0 )
		-> true : Boolean

	Expression : 
		( UReal(3, 0) - 3 ).equals( 0 )
		-> true : Boolean

	Expression : 
		( 3.0 - UReal(3, 0) ).equals( 0 )
		-> true : Boolean

	Expression : 
		( UReal(3, 4) - UReal(5, 2) ).equals( -(UReal(5, 2) - UReal(3, 4)) )
		-> true : Boolean

	Expression : 
		( UReal(3, 4) - 5 ).equals( -(5 - UReal(3, 4)) )
		-> true : Boolean

	Expression : 
		( 4.3 - UReal(5, 2) ).equals( -(UReal(5, 2) - 4.3) )
		-> true : Boolean

	Expression : 
		( UReal(3, 4) - (UReal(5, 2) - UReal(2, 0.53)) ).equals( (UReal(3, 4) - UReal(5, 2)) - UReal(2, 0.53) )
		-> false : Boolean

	Expression : 
		( UReal(3, 0) - (UReal(5, 0) - UReal(2, 0)) ).equals( (UReal(3, 0) - UReal(5, 0)) - UReal(2, 0) )
		-> false : Boolean

	Expression : 
		( -UReal(3, 4) ).equals( UReal(3, 4).neg() )
		-> true : Boolean

	Expression : 
		( - UReal(-3, 0) ).equals( UReal(-3, 0).neg() )
		-> true : Boolean

	Expression : 
		( 0 - UReal(3, 4) ).equals( UReal(3, 4).neg() )
		-> true : Boolean

	Expression : 
		( (UReal(1, 0) - 1) - UReal(3, 4) ).equals( UReal(3, 4).neg() )
		-> true : Boolean

	Expression : 
		UReal(-9, 0) * UReal(-9, 0)
		-> UReal(81.0, 0.0) : UReal

	Expression : 
		UReal(-5, 0) * UReal(-5, 3)
		-> UReal(25.0, 15.0) : UReal

	Expression : 
		UReal(-4, 0) * UReal(2, 0)
		-> UReal(-8.0, 0.0) : UReal

	Expression : 
		UReal(-10, 0) * UReal(4, 1)
		-> UReal(-40.0, 10.0) : UReal

	Expression : 
		UReal(-9, 9) * UReal(-9, 0)
		-> UReal(81.0, 81.0) : UReal

	Expression : 
		UReal(-2, 3) * UReal(-2, 4)
		-> UReal(4.0, 10.0) : UReal

	Expression : 
		UReal(-6, 2) * UReal(5, 0)
		-> UReal(-30.0, 10.0) : UReal

	Expression : 
		UReal(-2, 3) * UReal(2, 4)
		-> UReal(-4.0, 10.0) : UReal

	Expression : 
		UReal(0, 0) * UReal(0, 0)
		-> UReal(0.0, 0.0) : UReal

	Expression : 
		UReal(0, 0) * UReal(0, 4)
		-> UReal(0.0, 0.0) : UReal

	Expression : 
		UReal(0, 0) * UReal(6, 0)
		-> UReal(0.0, 0.0) : UReal

	Expression : 
		UReal(0, 0) * UReal(7, 3)
		-> UReal(0.0, 0.0) : UReal

	Expression : 
		UReal(0, 4) * UReal(0, 0)
		-> UReal(0.0, 0.0) : UReal

	Expression : 
		UReal(0, 4) * UReal(0, 3)
		-> UReal(0.0, 0.0) : UReal

	Expression : 
		UReal(0, 4) * UReal(1, 0)
		-> UReal(0.0, 4.0) : UReal

	Expression : 
		UReal(0, 4) * UReal(2, 3)
		-> UReal(0.0, 8.0) : UReal

	Expression : 
		UReal(9, 0) * UReal(9, 0)
		-> UReal(81.0, 0.0) : UReal

	Expression : 
		UReal(5, 0) * UReal(5, 3)
		-> UReal(25.0, 15.0) : UReal

	Expression : 
		UReal(4, 0) * UReal(8, 0)
		-> UReal(32.0, 0.0) : UReal

	Expression : 
		UReal(10, 0) * UReal(10, 12)
		-> UReal(100.0, 120.0) : UReal

	Expression : 
		UReal(9, 5) * UReal(9, 0)
		-> UReal(81.0, 45.0) : UReal

	Expression : 
		UReal(2, 3) * UReal(2, 4)
		-> UReal(4.0, 10.0) : UReal

	Expression : 
		UReal(6, 1) * UReal(4, 0)
		-> UReal(24.0, 4.0) : UReal

	Expression : 
		UReal(2, 3) * UReal(5, 4)
		-> UReal(10.0, 17.0) : UReal

	Expression : 
		UReal(-3, 0) * -3.0
		-> UReal(9.0, 0.0) : UReal

	Expression : 
		UReal(-6, 0) * -1.2
		-> UReal(7.2, 0.0) : UReal

	Expression : 
		UReal(-5, 3) * -5.0
		-> UReal(25.0, 15.0) : UReal

	Expression : 
		UReal(-8, 5) * -2.0
		-> UReal(16.0, 10.0) : UReal

	Expression : 
		UReal(0, 0) * 0.0
		-> UReal(0.0, 0.0) : UReal

	Expression : 
		UReal(0, 0) * 3.0
		-> UReal(0.0, 0.0) : UReal

	Expression : 
		UReal(0, 3) * 0.0
		-> UReal(0.0, 0.0) : UReal

	Expression : 
		UReal(0, 5) * -5.0
		-> UReal(0.0, 25.0) : UReal

	Expression : 
		UReal(5, 0) * 5.0
		-> UReal(25.0, 0.0) : UReal

	Expression : 
		UReal(3, 0) * 0.6
		-> UReal(1.8, 0.0) : UReal

	Expression : 
		UReal(7, 3) * 7.0
		-> UReal(49.0, 21.0) : UReal

	Expression : 
		UReal(2, 5) * 0.5
		-> UReal(1.0, 2.5) : UReal

	Expression : 
		UReal(-3, 0) * -3
		-> UReal(9.0, 0.0) : UReal

	Expression : 
		UReal(-6, 0) * -12
		-> UReal(72.0, 0.0) : UReal

	Expression : 
		UReal(-5, 3) * -5
		-> UReal(25.0, 15.0) : UReal

	Expression : 
		UReal(-8, 5) * -2
		-> UReal(16.0, 10.0) : UReal

	Expression : 
		UReal(0, 0) * 0
		-> UReal(0.0, 0.0) : UReal

	Expression : 
		UReal(0, 0) * 3
		-> UReal(0.0, 0.0) : UReal

	Expression : 
		UReal(0, 3) * 0
		-> UReal(0.0, 0.0) : UReal

	Expression : 
		UReal(0, 5) * -5
		-> UReal(0.0, 25.0) : UReal

	Expression : 
		UReal(5, 0) * 5
		-> UReal(25.0, 0.0) : UReal

	Expression : 
		UReal(3, 0) * 56
		-> UReal(168.0, 0.0) : UReal

	Expression : 
		UReal(7, 3) * 7
		-> UReal(49.0, 21.0) : UReal

	Expression : 
		UReal(2, 5) * 65
		-> UReal(130.0, 325.0) : UReal

	Expression : 
		( UReal(3, 2) * UReal(5, 2) ).equals( UReal(5, 2) * UReal(3, 2) )
		-> true : Boolean

	Expression : 
		( UReal(3, 2) * UReal(5, 0) ).equals( UReal(5, 0) * UReal(3, 2) )
		-> true : Boolean

	Expression : 
		( UReal(3, 2) * 5 ).equals( 5 * UReal(3, 2) )
		-> true : Boolean

	Expression : 
		( UReal(3, 2) * -5.53 ).equals( -5.53 * UReal(3, 2) )
		-> true : Boolean

	Expression : 
		( UReal(3, 5) * (UReal(5, 1) * UReal(1, 2)) ).equals( (UReal(3, 5) * UReal(5, 1)) * UReal(1, 2) )
		-> true : Boolean

	Expression : 
		( UReal(3, 5) * (5.1 * UReal(1, 2)) ).equals( (UReal(3, 5) * 5.1) * UReal(1, 2) )
		-> true : Boolean

	Expression : 
		( UReal(3, 5) * (5.1 * 1.2) ).equals( (UReal(3, 5) * 5.1) * 1.2 )
		-> true : Boolean

	Expression : 
		( UReal(3, 5) * (5 * UReal(1, 2)) ).equals( (UReal(3, 5) * 5) * UReal(1, 2) )
		-> true : Boolean

	Expression : 
		( UReal(3, 5) * (5 * 1.2) ).equals( (UReal(3, 5) * 5) * 1.2 )
		-> true : Boolean

	Expression : 
		( UReal(3, 5) * (5 * 2) ).equals( (UReal(3, 5) * 5) * 2 )
		-> true : Boolean

	Expression : 
		( UReal(2,1) * (UReal(3,1) + UReal(5, 0.2)) ).equals( UReal(2,1) * UReal(3,1) +  UReal(2,1) * UReal(5, 0.2) )
		-> false : Boolean

	Expression : 
		( 5.1 * (UReal(3, 2) + UReal(1, 2)) ).equals( (5.1 * UReal(3, 2)) + (5.1 * UReal(1, 2)) )
		-> true : Boolean

	Expression : 
		( 2 * (UReal(3, 2) + UReal(1, 2)) ).equals( (2 * UReal(3, 2)) + (2 * UReal(1, 2)) )
		-> true : Boolean

	Expression : 
		( UReal(3, 2) * UReal(1, 0) ).equals( UReal(3, 2) )
		-> true : Boolean

	Expression : 
		( UReal(3, 2) * 1 ).equals( UReal(3, 2) )
		-> true : Boolean

	Expression : 
		UReal(-9, 0) / UReal(-9, 0)
		-> UReal(1.0, 0.0) : UReal

	Expression : 
		UReal(-5, 0) / UReal(-5, 3)
		-> UReal(1.0, 0.12) : UReal

	Expression : 
		UReal(-4, 0) / UReal(2, 0)
		-> UReal(-2.0, 0.0) : UReal

	Expression : 
		UReal(-10, 0) / UReal(4, 1)
		-> UReal(-2.5, 0.0625) : UReal

	Expression : 
		UReal(-9, 9) / UReal(-9, 0)
		-> UReal(1.0, 1.0) : UReal

	Expression : 
		UReal(-2, 3) / UReal(-2, 4)
		-> UReal(1.0, 2.9154759474) : UReal

	Expression : 
		UReal(-6, 2) / UReal(5, 0)
		-> UReal(-1.2, 0.4) : UReal

	Expression : 
		UReal(-2, 3) / UReal(2, 4)
		-> UReal(-1.0, 2.9154759474) : UReal

	Expression : 
		UReal(0, 0) / UReal(0, 0)
		-> null : OclVoid

	Expression : 
		UReal(0, 0) / UReal(0, 4)
		-> null : OclVoid

	Expression : 
		UReal(0, 0) / UReal(6, 0)
		-> UReal(0.0, 0.0) : UReal

	Expression : 
		UReal(0, 0) / UReal(7, 3)
		-> UReal(0.0, 0.0612244898) : UReal

	Expression : 
		UReal(0, 4) / UReal(0, 0)
		-> null : OclVoid

	Expression : 
		UReal(0, 4) / UReal(0, 3)
		-> null : OclVoid

	Expression : 
		UReal(0, 4) / UReal(1, 0)
		-> UReal(0.0, 4.0) : UReal

	Expression : 
		UReal(0, 4) / UReal(2, 3)
		-> UReal(0.0, 2.8284271247) : UReal

	Expression : 
		UReal(9, 0) / UReal(9, 0)
		-> UReal(1.0, 0.0) : UReal

	Expression : 
		UReal(5, 0) / UReal(5, 3)
		-> UReal(1.0, 0.12) : UReal

	Expression : 
		UReal(4, 0) / UReal(8, 0)
		-> UReal(0.5, 0.0) : UReal

	Expression : 
		UReal(10, 0) / UReal(10, 12)
		-> UReal(1.0, 0.12) : UReal

	Expression : 
		UReal(9, 5) / UReal(9, 0)
		-> UReal(1.0, 0.5555555556) : UReal

	Expression : 
		UReal(2, 3) / UReal(2, 4)
		-> UReal(1.0, 2.9154759474) : UReal

	Expression : 
		UReal(6, 1) / UReal(4, 0)
		-> UReal(1.5, 0.25) : UReal

	Expression : 
		UReal(2, 3) / UReal(5, 4)
		-> UReal(0.4, 1.379275172) : UReal

	Expression : 
		UReal(-3, 0) / -3.0
		-> UReal(1.0, 0.0) : UReal

	Expression : 
		UReal(-6, 0) / -1.2
		-> UReal(5.0, 0.0) : UReal

	Expression : 
		UReal(-5, 3) / -5.0
		-> UReal(1.0, 0.6) : UReal

	Expression : 
		UReal(-8, 5) / -2.0
		-> UReal(4.0, 2.5) : UReal

	Expression : 
		UReal(0, 0) / 0.0
		-> null : OclVoid

	Expression : 
		UReal(0, 0) / 3.0
		-> UReal(0.0, 0.0) : UReal

	Expression : 
		UReal(0, 3) / 0.0
		-> null : OclVoid

	Expression : 
		UReal(0, 5) / -5.0
		-> UReal(0.0, 1.0) : UReal

	Expression : 
		UReal(5, 0) / 5.0
		-> UReal(1.0, 0.0) : UReal

	Expression : 
		UReal(3, 0) / 0.6
		-> UReal(5.0, 0.0) : UReal

	Expression : 
		UReal(7, 3) / 7.0
		-> UReal(1.0, 0.4285714286) : UReal

	Expression : 
		UReal(2, 5) / 0.5
		-> UReal(4.0, 10.0) : UReal

	Expression : 
		UReal(-3, 0) / -3
		-> UReal(1.0, 0.0) : UReal

	Expression : 
		UReal(-6, 0) / -12
		-> UReal(0.5, 0.0) : UReal

	Expression : 
		UReal(-5, 3) / -5
		-> UReal(1.0, 0.6) : UReal

	Expression : 
		UReal(-8, 5) / -2
		-> UReal(4.0, 2.5) : UReal

	Expression : 
		UReal(0, 0) / 0
		-> null : OclVoid

	Expression : 
		UReal(0, 0) / 3
		-> UReal(0.0, 0.0) : UReal

	Expression : 
		UReal(0, 3) / 0
		-> null : OclVoid

	Expression : 
		UReal(0, 5) / -5
		-> UReal(0.0, 1.0) : UReal

	Expression : 
		UReal(5, 0) / 5
		-> UReal(1.0, 0.0) : UReal

	Expression : 
		UReal(3, 0) / 56
		-> UReal(0.0535714286, 0.0) : UReal

	Expression : 
		UReal(7, 3) / 7
		-> UReal(1.0, 0.4285714286) : UReal

	Expression : 
		UReal(2, 5) / 65
		-> UReal(0.0307692308, 0.0769230769) : UReal

	Expression : 
		( UReal(2, 3).inv() ).equals( 1 / UReal(2, 3) )
		-> true : Boolean

	Expression : 
		( UReal(0, 3).inv() ).equals( 1 / UReal(0, 3) )
		-> true : Boolean

	Expression : 
		( UReal(2, 3) / UReal(1, 0.5) ).equals( UReal(1, 0.5) / UReal(2, 3) )
		-> false : Boolean

	Expression : 
		( 2.3 / UReal(1, 0.5) ).equals( UReal(1, 0.5) / 2.3 )
		-> false : Boolean

	Expression : 
		( 2 / UReal(1, 0.5) ).equals( UReal(1, 0.5) / 2 )
		-> false : Boolean

	Expression : 
		( UReal(2, 3) / (UReal(1, 0.5) / UReal(-0.5, 0.25)) ).equals( (UReal(2, 3) / UReal(1, 0.5)) / UReal(-0.5, 0.25) )
		-> false : Boolean

	Expression : 
		( UReal(2, 3) / (12.59 / UReal(-0.5, 0.25)) ).equals( (UReal(2, 3) / 12.59) / UReal(-0.5, 0.25) )
		-> false : Boolean

	Expression : 
		( UReal(2, 3) / (12 / UReal(-0.5, 0.25)) ).equals( (UReal(2, 3) / 12) / UReal(-0.5, 0.25) )
		-> false : Boolean

	Expression : 
		( UReal(2, 3) / 1 ).equals( UReal(2, 3) )
		-> true : Boolean

	Expression : 
		(UReal(0,0) < UReal(0,0)).toBoolean()
		-> false : Boolean

	Expression : 
		(UReal(0,0) < UReal(1,0)).toBoolean()
		-> true : Boolean

	Expression : 
		(UReal(3,0) < UReal(0,0)).toBoolean()
		-> false : Boolean

	Expression : 
		(UReal(0,0) < UReal(3,2)).toBoolean()
		-> true : Boolean

	Expression : 
		(UReal(3,0) < UReal(0,2)).toBoolean()
		-> false : Boolean

	Expression : 
		(UReal(0,2) < UReal(3,0)).toBoolean()
		-> true : Boolean

	Expression : 
		(UReal(3,2) < UReal(0,0)).toBoolean()
		-> false : Boolean

	Expression : 
		(UReal(0,2) < UReal(0,2)).toBoolean()
		-> false : Boolean

	Expression : 
		(UReal(0,2) < UReal(0,1)).toBoolean()
		-> false : Boolean

	Expression : 
		(UReal(0,2) < UReal(1,0.25)).toBoolean()
		-> true : Boolean

	Expression : 
		(UReal(0,2) < UReal(-1,0.25)).toBoolean()
		-> false : Boolean

	Expression : 
		(UReal(0,2) < UReal(5,2)).toBoolean()
		-> true : Boolean

	Expression : 
		(UReal(5,2) < UReal(0,2)).toBoolean()
		-> false : Boolean

	Expression : 
		(UReal(0,0) < 0).toBoolean()
		-> false : Boolean

	Expression : 
		(UReal(0,0) < 1).toBoolean()
		-> true : Boolean

	Expression : 
		(UReal(1,0) < 0).toBoolean()
		-> false : Boolean

	Expression : 
		(UReal(0,2) < 3).toBoolean()
		-> true : Boolean

	Expression : 
		(UReal(3,2) < 0).toBoolean()
		-> false : Boolean

	Expression : 
		(UReal(0,0) < 0.0).toBoolean()
		-> false : Boolean

	Expression : 
		(UReal(0,0) < 1.0).toBoolean()
		-> true : Boolean

	Expression : 
		(UReal(1,0) < 0.0).toBoolean()
		-> false : Boolean

	Expression : 
		(UReal(0,2) < 3.0).toBoolean()
		-> true : Boolean

	Expression : 
		(UReal(3,2) < 0.0).toBoolean()
		-> false : Boolean

	Expression : 
		( UReal(3,2) < UReal(0, 0) <> UReal(0, 0) < UReal(3, 2) ).toBoolean()
		-> true : Boolean

	Expression : 
		( UReal(3,2) < 0 <> 0 < UReal(3, 2) ).toBoolean()
		-> true : Boolean

	Expression : 
		( UReal(3,2) < 0.5 <> 0.5 < UReal(3, 2) ).toBoolean()
		-> true : Boolean

	Expression : 
		(UReal(0,0) >= UReal(0, 0)).toBoolean()
		-> true : Boolean

	Expression : 
		(UReal(0,0) >= UReal(1, 0)).toBoolean()
		-> false : Boolean

	Expression : 
		(UReal(3,0) >= UReal(0, 0)).toBoolean()
		-> true : Boolean

	Expression : 
		(UReal(0,0) >= UReal(3, 2)).toBoolean()
		-> false : Boolean

	Expression : 
		(UReal(3,0) >= UReal(0, 2)).toBoolean()
		-> true : Boolean

	Expression : 
		(UReal(0,2) >= UReal(3, 0)).toBoolean()
		-> false : Boolean

	Expression : 
		(UReal(3,2) >= UReal(0, 0)).toBoolean()
		-> true : Boolean

	Expression : 
		(UReal(0,2) >= UReal(0, 2)).toBoolean()
		-> true : Boolean

	Expression : 
		(UReal(0,2) >= UReal(0, 1)).toBoolean()
		-> true : Boolean

	Expression : 
		(UReal(0,2) >= UReal(1, 0.25)).toBoolean()
		-> false : Boolean

	Expression : 
		(UReal(0,2) >= UReal(-1, 0.25)).toBoolean()
		-> true : Boolean

	Expression : 
		(UReal(0,2) >= UReal(5, 2)).toBoolean()
		-> false : Boolean

	Expression : 
		(UReal(5,2) >= UReal(0, 2)).toBoolean()
		-> true : Boolean

	Expression : 
		(UReal(0,0) >= 0).toBoolean()
		-> true : Boolean

	Expression : 
		(UReal(0,0) >= 1).toBoolean()
		-> false : Boolean

	Expression : 
		(UReal(1,0) >= 0).toBoolean()
		-> true : Boolean

	Expression : 
		(UReal(0,2) >= 3).toBoolean()
		-> false : Boolean

	Expression : 
		(UReal(3,2) >= 0).toBoolean()
		-> true : Boolean

	Expression : 
		(UReal(0,0) >= 0.0).toBoolean()
		-> true : Boolean

	Expression : 
		(UReal(0,0) >= 1.0).toBoolean()
		-> false : Boolean

	Expression : 
		(UReal(1,0) >= 0.0).toBoolean()
		-> true : Boolean

	Expression : 
		(UReal(0,2) >= 3.0).toBoolean()
		-> false : Boolean

	Expression : 
		(UReal(3,2) >= 0.0).toBoolean()
		-> true : Boolean

	Expression : 
		(UReal(0,0) <= UReal(0, 0)).toBoolean()
		-> true : Boolean

	Expression : 
		(UReal(0,0) <= UReal(1, 0)).toBoolean()
		-> true : Boolean

	Expression : 
		(UReal(3,0) <= UReal(0, 0)).toBoolean()
		-> false : Boolean

	Expression : 
		(UReal(0,0) <= UReal(3, 2)).toBoolean()
		-> true : Boolean

	Expression : 
		(UReal(3,0) <= UReal(0, 2)).toBoolean()
		-> false : Boolean

	Expression : 
		(UReal(0,2) <= UReal(3, 0)).toBoolean()
		-> true : Boolean

	Expression : 
		(UReal(3,2) <= UReal(0, 0)).toBoolean()
		-> false : Boolean

	Expression : 
		(UReal(0,2) <= UReal(0, 2)).toBoolean()
		-> true : Boolean

	Expression : 
		(UReal(0,2) <= UReal(0, 1)).toBoolean()
		-> true : Boolean

	Expression : 
		(UReal(0,2) <= UReal(1, 0.25)).toBoolean()
		-> true : Boolean

	Expression : 
		(UReal(0,2) <= UReal(-1, 0.25)).toBoolean()
		-> false : Boolean

	Expression : 
		(UReal(0,2) <= UReal(5, 2)).toBoolean()
		-> true : Boolean

	Expression : 
		(UReal(5,2) <= UReal(0, 2)).toBoolean()
		-> false : Boolean

	Expression : 
		(UReal(0,0) <= 0).toBoolean()
		-> true : Boolean

	Expression : 
		(UReal(0,0) <= 1).toBoolean()
		-> true : Boolean

	Expression : 
		(UReal(1,0) <= 0).toBoolean()
		-> false : Boolean

	Expression : 
		(UReal(0,2) <= 3).toBoolean()
		-> true : Boolean

	Expression : 
		(UReal(3,2) <= 0).toBoolean()
		-> false : Boolean

	Expression : 
		(UReal(0,0) <= 0.0).toBoolean()
		-> true : Boolean

	Expression : 
		(UReal(0,0) <= 1.0).toBoolean()
		-> true : Boolean

	Expression : 
		(UReal(1,0) <= 0.0).toBoolean()
		-> false : Boolean

	Expression : 
		(UReal(0,2) <= 3.0).toBoolean()
		-> true : Boolean

	Expression : 
		(UReal(3,2) <= 0.0).toBoolean()
		-> false : Boolean

	Expression : 
		(UReal(0,0) > UReal(0, 0)).toBoolean()
		-> false : Boolean

	Expression : 
		(UReal(0,0) > UReal(1, 0)).toBoolean()
		-> false : Boolean

	Expression : 
		(UReal(3,0) > UReal(0, 0)).toBoolean()
		-> true : Boolean

	Expression : 
		(UReal(0,0) > UReal(3, 2)).toBoolean()
		-> false : Boolean

	Expression : 
		(UReal(3,0) > UReal(0, 2)).toBoolean()
		-> true : Boolean

	Expression : 
		(UReal(0,2) > UReal(3, 0)).toBoolean()
		-> false : Boolean

	Expression : 
		(UReal(3,2) > UReal(0, 0)).toBoolean()
		-> true : Boolean

	Expression : 
		(UReal(0,2) > UReal(0, 2)).toBoolean()
		-> false : Boolean

	Expression : 
		(UReal(0,2) > UReal(0, 1)).toBoolean()
		-> false : Boolean

	Expression : 
		(UReal(0,2) > UReal(1, 0.25)).toBoolean()
		-> false : Boolean

	Expression : 
		(UReal(0,2) > UReal(-1, 0.25)).toBoolean()
		-> true : Boolean

	Expression : 
		(UReal(0,2) > UReal(5, 2)).toBoolean()
		-> false : Boolean

	Expression : 
		(UReal(5,2) > UReal(0, 2)).toBoolean()
		-> true : Boolean

	Expression : 
		(UReal(0,0) > 0).toBoolean()
		-> false : Boolean

	Expression : 
		(UReal(0,0) > 1).toBoolean()
		-> false : Boolean

	Expression : 
		(UReal(1,0) > 0).toBoolean()
		-> true : Boolean

	Expression : 
		(UReal(0,2) > 3).toBoolean()
		-> false : Boolean

	Expression : 
		(UReal(3,2) > 0).toBoolean()
		-> true : Boolean

	Expression : 
		(UReal(0,0) > 0.0).toBoolean()
		-> false : Boolean

	Expression : 
		(UReal(0,0) > 1.0).toBoolean()
		-> false : Boolean

	Expression : 
		(UReal(1,0) > 0.0).toBoolean()
		-> true : Boolean

	Expression : 
		(UReal(0,2) > 3.0).toBoolean()
		-> false : Boolean

	Expression : 
		(UReal(3,2) > 0.0).toBoolean()
		-> true : Boolean

	Expression : 
		( UReal(3,2) > UReal(0, 0) ).equals( UReal(0, 0) > UReal(3, 2) )
		-> false : Boolean

	Expression : 
		( UReal(3,2) > 0 ).equals( 0 > UReal(3, 2) )
		-> false : Boolean

	Expression : 
		( UReal(3,2) > 0.5 ).equals( 0.5 > UReal(3, 2) )
		-> false : Boolean

	Expression : 
		( UReal(0, 0) = UReal(0, 0) ).toBoolean()
		-> true : Boolean

	Expression : 
		( UReal(0, 0) = UReal(1, 0) ).toBoolean()
		-> false : Boolean

	Expression : 
		( UReal(3, 0) = UReal(0, 0) ).toBoolean()
		-> false : Boolean

	Expression : 
		( UReal(0, 0) = UReal(3, 2) ).toBoolean()
		-> false : Boolean

	Expression : 
		( UReal(3, 0) = UReal(0, 2) ).toBoolean()
		-> false : Boolean

	Expression : 
		( UReal(0, 2) = UReal(3, 0) ).toBoolean()
		-> false : Boolean

	Expression : 
		( UReal(3, 2) = UReal(0, 0) ).toBoolean()
		-> false : Boolean

	Expression : 
		( UReal(0, 2) = UReal(0, 2) ).toBoolean()
		-> true : Boolean

	Expression : 
		( UReal(0, 2) = UReal(0, 1) ).toBoolean()
		-> true : Boolean

	Expression : 
		( UReal(0, 2) = UReal(1, 0.25) ).toBoolean()
		-> false : Boolean

	Expression : 
		( UReal(0, 2) = UReal(-1, 0.25) ).toBoolean()
		-> false : Boolean

	Expression : 
		( UReal(0, 2) = UReal(5, 2) ).toBoolean()
		-> false : Boolean

	Expression : 
		( UReal(5, 2) = UReal(0, 2) ).toBoolean()
		-> false : Boolean

	Expression : 
		( UReal(0, 0) = 0 ).toBoolean()
		-> true : Boolean

	Expression : 
		( UReal(0, 0) = 1 ).toBoolean()
		-> false : Boolean

	Expression : 
		( UReal(1, 0) = 0 ).toBoolean()
		-> false : Boolean

	Expression : 
		( UReal(0, 2) = 3 ).toBoolean()
		-> false : Boolean

	Expression : 
		( UReal(3, 2) = 0 ).toBoolean()
		-> false : Boolean

	Expression : 
		( UReal(0, 0) = 0.0 ).toBoolean()
		-> true : Boolean

	Expression : 
		( UReal(0, 0) = 1.0 ).toBoolean()
		-> false : Boolean

	Expression : 
		( UReal(1, 0) = 0.0 ).toBoolean()
		-> false : Boolean

	Expression : 
		( UReal(0, 2) = 3.0 ).toBoolean()
		-> false : Boolean

	Expression : 
		( UReal(3, 2) = 0.0 ).toBoolean()
		-> false : Boolean

	Expression : 
		UReal(2, 3) = Undefined
		-> false : Boolean

	Expression : 
		UReal(2, 3) = null
		-> false : Boolean

	Expression : 
		( UReal(0, 0) <> UReal(0, 0) ).toBoolean()
		-> false : Boolean

	Expression : 
		( UReal(0, 0) <> UReal(1, 0) ).toBoolean()
		-> true : Boolean

	Expression : 
		( UReal(3, 0) <> UReal(0, 0) ).toBoolean()
		-> true : Boolean

	Expression : 
		( UReal(0, 0) <> UReal(3, 2) ).toBoolean()
		-> true : Boolean

	Expression : 
		( UReal(3, 0) <> UReal(0, 2) ).toBoolean()
		-> true : Boolean

	Expression : 
		( UReal(0, 2) <> UReal(3, 0) ).toBoolean()
		-> true : Boolean

	Expression : 
		( UReal(3, 2) <> UReal(0, 0) ).toBoolean()
		-> true : Boolean

	Expression : 
		( UReal(0, 2) <> UReal(0, 2) ).toBoolean()
		-> false : Boolean

	Expression : 
		( UReal(0, 2) <> UReal(0, 1) ).toBoolean()
		-> false : Boolean

	Expression : 
		( UReal(0, 2) <> UReal(1, 0.25) ).toBoolean()
		-> true : Boolean

	Expression : 
		( UReal(0, 2) <> UReal(-1, 0.25) ).toBoolean()
		-> true : Boolean

	Expression : 
		( UReal(0, 2) <> UReal(5, 2) ).toBoolean()
		-> true : Boolean

	Expression : 
		( UReal(5, 2) <> UReal(0, 2) ).toBoolean()
		-> true : Boolean

	Expression : 
		( UReal(0, 0) <> 0 ).toBoolean()
		-> false : Boolean

	Expression : 
		( UReal(0, 0) <> 1 ).toBoolean()
		-> true : Boolean

	Expression : 
		( UReal(1, 0) <> 0 ).toBoolean()
		-> true : Boolean

	Expression : 
		( UReal(0, 2) <> 3 ).toBoolean()
		-> true : Boolean

	Expression : 
		( UReal(3, 2) <> 0 ).toBoolean()
		-> true : Boolean

	Expression : 
		( UReal(0, 0) <> 0.0 ).toBoolean()
		-> false : Boolean

	Expression : 
		( UReal(0, 0) <> 1.0 ).toBoolean()
		-> true : Boolean

	Expression : 
		( UReal(1, 0) <> 0.0 ).toBoolean()
		-> true : Boolean

	Expression : 
		( UReal(0, 2) <> 3.0 ).toBoolean()
		-> true : Boolean

	Expression : 
		( UReal(3, 2) <> 0.0 ).toBoolean()
		-> true : Boolean

	Expression : 
		UReal(2, 3) <> Undefined
		-> true : Boolean

	Expression : 
		UReal(2, 3) <> null
		-> true : Boolean

[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.245 s -- in org.tzi.use.parser.uncertainty.USECompilerUncertaintyTest
[INFO] Running org.tzi.use.architecture.MavenCyclicDependenciesCoreTest
Number of cycles in org.tzi.use.main without tests: 0
Number of cycles in org.tzi.use.main with tests: 0
Number of cycles in org.tzi.use.analysis without tests: 0
Number of cycles in org.tzi.use.analysis with tests: 0
Number of cycles in org.tzi.use.util without tests: 0
Number of cycles in org.tzi.use.util with tests: 0
Number of cycles in org.tzi.use.gen without tests: 1
Number of cycles in org.tzi.use.gen with tests: 1
Number of cycles in org.tzi.use.parser without tests: 2
Number of cycles in org.tzi.use.parser with tests: 36
Number of cycles in org.tzi.use.api without tests: 1
Number of cycles in org.tzi.use.api with tests: 1
Cycles in core module with tests : 233
Number of cycles in org.tzi.use.graph without tests: 0
Number of cycles in org.tzi.use.graph with tests: 0
Number of cycles in org.tzi.use.config without tests: 0
Number of cycles in org.tzi.use.config with tests: 0
Cycles in core module without tests : 55
Number of cycles in org.tzi.use.uml without tests: 5
Number of cycles in org.tzi.use.uml with tests: 5
[INFO] Tests run: 11, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 4.842 s -- in org.tzi.use.architecture.MavenCyclicDependenciesCoreTest
[INFO] Running uCount/uCountC
[INFO] Tests run: 6, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.010 s -- in uCount/uCountC
[INFO] Running org.tzi.use.uncertainty.differential.SBooleanMarshallingTest
=== SBooleanValue.and over 3x3 opinions ===
  OPAQUE("org.tzi.use.uml.ocl.value.SBooleanValue|SBooleanValue{Value.fType=SBooleanType{BasicType.fTypename=\"SBoolean\"},SBooleanValue.sBoolean=SBoolean{SBoolean.a=0.25,SBoolean.b=0.19,SBoolean.d=0.36,SBoolean.relativeWeight=0.0,SBoolean.u=0.45}}")@SBooleanValue
  OPAQUE("org.tzi.use.uml.ocl.value.SBooleanValue|SBooleanValue{Value.fType=SBooleanType{BasicType.fTypename=\"SBoolean\"},SBooleanValue.sBoolean=SBoolean{SBoolean.a=0.5,SBoolean.b=0.3,SBoolean.d=0.2,SBoolean.relativeWeight=1.0,SBoolean.u=0.5}}")@SBooleanValue
  OPAQUE("org.tzi.use.uml.ocl.value.SBooleanValue|SBooleanValue{Value.fType=SBooleanType{BasicType.fTypename=\"SBoolean\"},SBooleanValue.sBoolean=SBoolean{SBoolean.a=0.25,SBoolean.b=0.1,SBoolean.d=0.2,SBoolean.relativeWeight=0.0,SBoolean.u=0.7}}")@SBooleanValue
  OPAQUE("org.tzi.use.uml.ocl.value.SBooleanValue|SBooleanValue{Value.fType=SBooleanType{BasicType.fTypename=\"SBoolean\"},SBooleanValue.sBoolean=SBoolean{SBoolean.a=1.0,SBoolean.b=1.0,SBoolean.d=0.0,SBoolean.relativeWeight=1.0,SBoolean.u=0.0}}")@SBooleanValue
  OPAQUE("org.tzi.use.uml.ocl.value.SBooleanValue|SBooleanValue{Value.fType=SBooleanType{BasicType.fTypename=\"SBoolean\"},SBooleanValue.sBoolean=SBoolean{SBoolean.a=0.5,SBoolean.b=0.0,SBoolean.d=0.0,SBoolean.relativeWeight=1.0,SBoolean.u=1.0}}")@SBooleanValue
  OPAQUE("org.tzi.use.uml.ocl.value.SBooleanValue|SBooleanValue{Value.fType=SBooleanType{BasicType.fTypename=\"SBoolean\"},SBooleanValue.sBoolean=SBoolean{SBoolean.a=0.25,SBoolean.b=0.0,SBoolean.d=0.0,SBoolean.relativeWeight=0.0,SBoolean.u=1.0}}")@SBooleanValue
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.069 s -- in org.tzi.use.uncertainty.differential.SBooleanMarshallingTest
[INFO] Running org.tzi.use.uncertainty.differential.FirstRealDifferentialTest
================ FIRST REAL DIFFERENTIAL ================
URealValue.add(value)         784 rows  distinctRef=258  {AGREE=784}
URealValue.minus(value)       784 rows  distinctRef=389  {AGREE=784}
URealValue.mult(value)        784 rows  distinctRef=195  {AGREE=784}
URealValue.divideBy(value)    784 rows  distinctRef=409  {AGREE=784}
URealValue.min(value)         784 rows  distinctRef=27   {AGREE=784}
URealValue.max(value)         784 rows  distinctRef=27   {AGREE=784}
URealValue.neg()               28 rows  distinctRef=27   {AGREE=28}
URealValue.abs()               28 rows  distinctRef=21   {AGREE=28}
URealValue.floor()             28 rows  distinctRef=26   {AGREE=28}
URealValue.round()             28 rows  distinctRef=22   {AGREE=28}
URealValue.sqrt()              28 rows  distinctRef=16   {AGREE=28}
URealValue.inverse()           28 rows  distinctRef=27   {AGREE=28}
URealValue.toReal()            28 rows  distinctRef=21   {AGREE=28}
URealValue.toInteger()         28 rows  distinctRef=14   {AGREE=28}
URealValue.toUInteger()        28 rows  distinctRef=22   {AGREE=28}
URealValue.lt(value)          784 rows  distinctRef=37   {AGREE=784}
URealValue.gt(value)          784 rows  distinctRef=37   {AGREE=784}
URealValue.le(value)          784 rows  distinctRef=37   {AGREE=784}
URealValue.ge(value)          784 rows  distinctRef=37   {AGREE=784}
UIntegerValue.add(value)      361 rows  distinctRef=154  {AGREE=361}
UIntegerValue.minus(value)    361 rows  distinctRef=260  {AGREE=361}
UIntegerValue.mult(value)     361 rows  distinctRef=115  {AGREE=361}
URealValue.value()             28 rows  distinctRef=21   {AGREE=28}
UIntegerValue.value()          19 rows  distinctRef=14   {AGREE=19}
URealValue.uncertainty()       28 rows  distinctRef=13   {AGREE=28}
UIntegerValue.uncertainty()    19 rows  distinctRef=11   {AGREE=19}
UBooleanValue.value()          15 rows  distinctRef=1    {AGREE=13, HARNESS_ERROR=2}   <== DEGENERATE, agreement is free
UBooleanValue.probability()    15 rows  distinctRef=10   {AGREE=13, HARNESS_ERROR=2}
UStringValue.value()           32 rows  distinctRef=27   {AGREE=31, HARNESS_ERROR=1}
UStringValue.confidence()      32 rows  distinctRef=11   {AGREE=31, HARNESS_ERROR=1}
UBooleanValue.and(value)      225 rows  distinctRef=38   {AGREE=169, HARNESS_ERROR=56}
UBooleanValue.or(value)       225 rows  distinctRef=39   {AGREE=169, HARNESS_ERROR=56}
UBooleanValue.not()            15 rows  distinctRef=10   {AGREE=13, HARNESS_ERROR=2}
UStringValue.uConcat(value)  1024 rows  distinctRef=863  {AGREE=961, HARNESS_ERROR=63}
UStringValue.lt(value)       1024 rows  distinctRef=81   {AGREE=961, HARNESS_ERROR=63}
UStringValue.gt(value)       1024 rows  distinctRef=81   {AGREE=961, HARNESS_ERROR=63}
UStringValue.le(value)       1024 rows  distinctRef=81   {AGREE=961, HARNESS_ERROR=63}
UStringValue.ge(value)       1024 rows  distinctRef=81   {AGREE=961, HARNESS_ERROR=63}
UStringValue.toBoolean()       32 rows  distinctRef=2    {AGREE=31, HARNESS_ERROR=1}
UStringValue.toInteger()       32 rows  distinctRef=5    {AGREE=5, BOTH_THREW=26, HARNESS_ERROR=1}
UStringValue.toReal()          32 rows  distinctRef=8    {AGREE=8, BOTH_THREW=23, HARNESS_ERROR=1}
UStringValue.uToString()       32 rows  distinctRef=27   {AGREE=31, HARNESS_ERROR=1}
UStringValue.toUBoolean()      32 rows  distinctRef=6    {AGREE=31, HARNESS_ERROR=1}
UStringValue.uCharacters()     32 rows  distinctRef=30   {AGREE=31, HARNESS_ERROR=1}
SBooleanValue.and(value)      529 rows  distinctRef=160  {AGREE=361, HARNESS_ERROR=168}
SBooleanValue.not()            23 rows  distinctRef=19   {AGREE=19, HARNESS_ERROR=4}
=========================================================
java-type mismatches: NONE
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 1.001 s -- in org.tzi.use.uncertainty.differential.FirstRealDifferentialTest
[INFO] Running Ported fidelity over the full operation census
=========== PORTED FIDELITY, FULL CENSUS ===========
operations enumerated  355
supported by the port  349  (98%)
rows                   1223560 total, 79520 measured
verdicts               {AGREE=78130, INTENDED_DEPARTURE=1390, BOTH_THREW=50598, UNMEASURABLE=808, HARNESS_ERROR=1092634}
pre-registered (B7)    11 declaration(s), 1390 row(s) adjudicated
diverging operations   0 (unintended)
unsupported (6): [SBooleanValue.averageFusion(value), SBooleanValue.cumulativeFusion(value), SBooleanValue.epistemicCumulativeFusion(value), SBooleanValue.majorityFusion(value), SBooleanValue.minimumFusion(value), SBooleanValue.weightedFusion(value)]
====================================================
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 12.65 s -- in Ported fidelity over the full operation census
[INFO] Running uEquals: swept, and 0.0 for uncertain-vs-crisp equality is correct, not a defect
[INFO] Tests run: 6, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.036 s -- in uEquals: swept, and 0.0 for uncertain-vs-crisp equality is correct, not a defect
[INFO] Running Detection power: subtle infidelities in a ported U-type
=== detection power: control (a perfect port) =====================
seed                 20260817
operations           355  (stage-shaped domains)
rows                 23963
measured rows        21556
agreement rows       21556
verdict tally        {AGREE=21556, BOTH_THREW=1243, HARNESS_ERROR=1063, UNMEASURABLE=101}
diverging operations 0   <- MUST be 0, or nothing below is attributable to a planted defect
stage passes         74 of 355  (isStagePass(1, none()))
why a PERFECT port is refused elsewhere:
    0 PASS   74
    2 refused: rows disagreed   44
    3 refused on more than one clause   118
    4 refused: not discriminating (D-15)   119
distinct throw-pairs a PERFECT port produces  193   <- AcceptedThrowPairs entries a human would have to author, one per (operation, both classes, both messages), before clause 2 could ever be met on the operations that throw
    e.g. UIntegerValue.divideBy(value) || reference threw java.lang.ArithmeticException: / by zero / subject threw java.lang.ArithmeticException: / by zero
    e.g. UIntegerValue.inverse() || reference threw java.lang.ArithmeticException: / by zero / subject threw java.lang.ArithmeticException: / by zero
    e.g. UIntegerValue.mod(value) || reference threw java.lang.ArithmeticException: / by zero / subject threw java.lang.ArithmeticException: / by zero
    e.g. UIntegerValue.power(value) || reference threw java.lang.RuntimeException: UInteger.power() : expected Real or Integer exponent value / subject threw java.lang.RuntimeException: UInteger.power() : expected Real or Integer exponent value
===================================================================
=== detection power: P1-off-by-one-index ============================
defect               a 0-based port of a 1-based string index: at/uAt/uSubstring shift by one
aimed at             [UStringValue.at(int), UStringValue.uAt(int), UStringValue.uSubstring(int,int)]
rows                 23963   (control 23963)
measured rows        21491   (control 21556)
agreement rows       21389   (control 21556)
verdict tally        {AGREE=21389, BOTH_THREW=1166, DIFFER=102, HARNESS_ERROR=1063, MIXED=142, UNMEASURABLE=101}
DETECTED on          3 operation(s): [UStringValue.at(int), UStringValue.uAt(int), UStringValue.uSubstring(int,int)]
stage passes         74   (control 74)
isClean() operations 193   (control 193)   the older predicate loses 0: []
  target UStringValue.at(int)
    control  {AGREE=70, BOTH_THREW=146, HARNESS_ERROR=8}
    mutant   {DIFFER=51, BOTH_THREW=121, MIXED=44, HARNESS_ERROR=8}
    statement UStringValue.at(int): 224 rows, 51 measured, 0 agreed, 224 disagreed, 0 intended departure(s), 0 java-type mismatch(es) (subject token OBSERVED on 0, ASSUMED on 0), 31 distinct reference value(s) [DISCRIMINATING]
    stage pass? false   (control false)
    refused: 224 row(s) did not agree.
  target UStringValue.uAt(int)
    control  {AGREE=70, BOTH_THREW=146, HARNESS_ERROR=8}
    mutant   {DIFFER=51, BOTH_THREW=121, MIXED=44, HARNESS_ERROR=8}
    statement UStringValue.uAt(int): 224 rows, 51 measured, 0 agreed, 224 disagreed, 0 intended departure(s), 0 java-type mismatch(es) (subject token OBSERVED on 0, ASSUMED on 0), 39 distinct reference value(s) [DISCRIMINATING]
    stage pass? false   (control false)
    refused: 224 row(s) did not agree.
  target UStringValue.uSubstring(int,int)
    control  {AGREE=27, BOTH_THREW=621, HARNESS_ERROR=24}
    mutant   {BOTH_THREW=594, MIXED=54, HARNESS_ERROR=24}
    statement UStringValue.uSubstring(int,int): 672 rows, 0 measured, 0 agreed, 672 disagreed, 0 intended departure(s), 0 java-type mismatch(es) (subject token OBSERVED on 0, ASSUMED on 0), 0 distinct reference value(s) [NOT DISCRIMINATING]
    stage pass? false   (control false)
    refused: measured 0 row(s) of 672, needed at least 1. A result with too little evidence is not evidence.
    refused: 672 row(s) did not agree.
    refused: the reference side produced 0 distinct value(s) across 0 measured row(s). This operation could not have failed over this domain, so agreement on it is decided before either implementation runs and is not evidence of fidelity (defect D-15). Either widen the domain until the reference answers differently, or sign the operation off in AcceptedDegenerateOperations with a written rationale — which is copied into the report, so the weakness travels with the number.
  first 6 diverging row(s):
  index	operation	inputs	historical	ported	verdict	note
  18	UStringValue.at(int)	USTRING(" ",0.5)@UStringValue | INTEGER(0)@IntegerValue	THROWN:java.lang.IndexOutOfBoundsException	STRING(" ")@StringValue	MIXED	the reference threw and the subject returned. reference threw java.lang.IndexOutOfBoundsException: idx = 0 / subject returned STRING(" ")@StringValue
  19	UStringValue.at(int)	USTRING(" ",0.5)@UStringValue | INTEGER(1)@IntegerValue	STRING(" ")@StringValue	THROWN:java.lang.IndexOutOfBoundsException	MIXED	the subject threw and the reference returned. reference returned STRING(" ")@StringValue / subject threw java.lang.IndexOutOfBoundsException: idx = 2
  26	UStringValue.at(int)	USTRING("a",0.0)@UStringValue | INTEGER(0)@IntegerValue	THROWN:java.lang.IndexOutOfBoundsException	STRING("a")@StringValue	MIXED	the reference threw and the subject returned. reference threw java.lang.IndexOutOfBoundsException: idx = 0 / subject returned STRING("a")@StringValue
  27	UStringValue.at(int)	USTRING("a",0.0)@UStringValue | INTEGER(1)@IntegerValue	STRING("a")@StringValue	THROWN:java.lang.IndexOutOfBoundsException	MIXED	the subject threw and the reference returned. reference returned STRING("a")@StringValue / subject threw java.lang.IndexOutOfBoundsException: idx = 2
  34	UStringValue.at(int)	USTRING("a",1.0)@UStringValue | INTEGER(0)@IntegerValue	THROWN:java.lang.IndexOutOfBoundsException	STRING("a")@StringValue	MIXED	the reference threw and the subject returned. reference threw java.lang.IndexOutOfBoundsException: idx = 0 / subject returned STRING("a")@StringValue
  35	UStringValue.at(int)	USTRING("a",1.0)@UStringValue | INTEGER(1)@IntegerValue	STRING("a")@StringValue	THROWN:java.lang.IndexOutOfBoundsException	MIXED	the subject threw and the reference returned. reference returned STRING("a")@StringValue / subject threw java.lang.IndexOutOfBoundsException: idx = 2
===================================================================
=== detection power: P2-linear-uncertainty ============================
defect               uncertainties combined with + where the historical uses sqrt(ua^2+ub^2)
aimed at             [UIntegerValue.add(value), UIntegerValue.minus(value), URealValue.add(value), URealValue.minus(value)]
rows                 23963   (control 23963)
measured rows        21556   (control 21556)
agreement rows       21088   (control 21556)
verdict tally        {AGREE=21088, BOTH_THREW=1243, DIFFER=468, HARNESS_ERROR=1063, UNMEASURABLE=101}
DETECTED on          4 operation(s): [UIntegerValue.add(value), UIntegerValue.minus(value), URealValue.add(value), URealValue.minus(value)]
stage passes         70   (control 74)
isClean() operations 189   (control 193)   the older predicate loses 4: [UIntegerValue.add(value), UIntegerValue.minus(value), URealValue.add(value), URealValue.minus(value)]
  target UIntegerValue.add(value)
    control  {AGREE=225}
    mutant   {AGREE=134, DIFFER=91}
    statement UIntegerValue.add(value): 225 rows, 225 measured, 134 agreed, 91 disagreed, 0 intended departure(s), 0 java-type mismatch(es) (subject token OBSERVED on 0, ASSUMED on 0), 88 distinct reference value(s) [DISCRIMINATING]
    stage pass? false   (control true)
    refused: 91 row(s) did not agree.
  target UIntegerValue.minus(value)
    control  {AGREE=225}
    mutant   {AGREE=134, DIFFER=91}
    statement UIntegerValue.minus(value): 225 rows, 225 measured, 134 agreed, 91 disagreed, 0 intended departure(s), 0 java-type mismatch(es) (subject token OBSERVED on 0, ASSUMED on 0), 132 distinct reference value(s) [DISCRIMINATING]
    stage pass? false   (control true)
    refused: 91 row(s) did not agree.
  target URealValue.add(value)
    control  {AGREE=576}
    mutant   {AGREE=433, DIFFER=143}
    statement URealValue.add(value): 576 rows, 576 measured, 433 agreed, 143 disagreed, 0 intended departure(s), 0 java-type mismatch(es) (subject token OBSERVED on 0, ASSUMED on 0), 164 distinct reference value(s) [DISCRIMINATING]
    stage pass? false   (control true)
    refused: 143 row(s) did not agree.
  target URealValue.minus(value)
    control  {AGREE=576}
    mutant   {AGREE=433, DIFFER=143}
    statement URealValue.minus(value): 576 rows, 576 measured, 433 agreed, 143 disagreed, 0 intended departure(s), 0 java-type mismatch(es) (subject token OBSERVED on 0, ASSUMED on 0), 225 distinct reference value(s) [DISCRIMINATING]
    stage pass? false   (control true)
    refused: 143 row(s) did not agree.
  first 6 diverging row(s):
  index	operation	inputs	historical	ported	verdict	note
  12	UIntegerValue.add(value)	UINTEGER(0,0.0)@UIntegerValue | UINTEGER(1,-1.0)@UIntegerValue	UINTEGER(1,1.0)@UIntegerValue	UINTEGER(1,-1.0)@UIntegerValue	DIFFER	
  16	UIntegerValue.add(value)	UINTEGER(0,1.0)@UIntegerValue | UINTEGER(0,1.0)@UIntegerValue	UINTEGER(0,1.4142135623730951)@UIntegerValue	UINTEGER(0,2.0)@UIntegerValue	DIFFER	
  18	UIntegerValue.add(value)	UINTEGER(0,1.0)@UIntegerValue | UINTEGER(1,1.0)@UIntegerValue	UINTEGER(1,1.4142135623730951)@UIntegerValue	UINTEGER(1,2.0)@UIntegerValue	DIFFER	
  20	UIntegerValue.add(value)	UINTEGER(0,1.0)@UIntegerValue | UINTEGER(-1,1.0)@UIntegerValue	UINTEGER(-1,1.4142135623730951)@UIntegerValue	UINTEGER(-1,2.0)@UIntegerValue	DIFFER	
  21	UIntegerValue.add(value)	UINTEGER(0,1.0)@UIntegerValue | UINTEGER(2,0.5)@UIntegerValue	UINTEGER(2,1.118033988749895)@UIntegerValue	UINTEGER(2,1.5)@UIntegerValue	DIFFER	
  22	UIntegerValue.add(value)	UINTEGER(0,1.0)@UIntegerValue | UINTEGER(-2,0.5)@UIntegerValue	UINTEGER(-2,1.118033988749895)@UIntegerValue	UINTEGER(-2,1.5)@UIntegerValue	DIFFER	
===================================================================
=== detection power: P3-hypot-uncertainty ============================
defect               Math.hypot(ua,ub) instead of sqrt(ua*ua+ub*ub) -- algebraically the same rule, a different function
aimed at             [UIntegerValue.add(value), UIntegerValue.minus(value), URealValue.add(value), URealValue.minus(value)]
rows                 23963   (control 23963)
measured rows        21556   (control 21556)
agreement rows       21532   (control 21556)
verdict tally        {AGREE=21532, BOTH_THREW=1243, DIFFER=24, HARNESS_ERROR=1063, UNMEASURABLE=101}
DETECTED on          4 operation(s): [UIntegerValue.add(value), UIntegerValue.minus(value), URealValue.add(value), URealValue.minus(value)]
stage passes         70   (control 74)
isClean() operations 189   (control 193)   the older predicate loses 4: [UIntegerValue.add(value), UIntegerValue.minus(value), URealValue.add(value), URealValue.minus(value)]
  target UIntegerValue.add(value)
    control  {AGREE=225}
    mutant   {AGREE=223, DIFFER=2}
    statement UIntegerValue.add(value): 225 rows, 225 measured, 223 agreed, 2 disagreed, 0 intended departure(s), 0 java-type mismatch(es) (subject token OBSERVED on 0, ASSUMED on 0), 88 distinct reference value(s) [DISCRIMINATING]
    stage pass? false   (control true)
    refused: 2 row(s) did not agree.
  target UIntegerValue.minus(value)
    control  {AGREE=225}
    mutant   {AGREE=223, DIFFER=2}
    statement UIntegerValue.minus(value): 225 rows, 225 measured, 223 agreed, 2 disagreed, 0 intended departure(s), 0 java-type mismatch(es) (subject token OBSERVED on 0, ASSUMED on 0), 132 distinct reference value(s) [DISCRIMINATING]
    stage pass? false   (control true)
    refused: 2 row(s) did not agree.
  target URealValue.add(value)
    control  {AGREE=576}
    mutant   {AGREE=566, DIFFER=10}
    statement URealValue.add(value): 576 rows, 576 measured, 566 agreed, 10 disagreed, 0 intended departure(s), 0 java-type mismatch(es) (subject token OBSERVED on 0, ASSUMED on 0), 164 distinct reference value(s) [DISCRIMINATING]
    stage pass? false   (control true)
    refused: 10 row(s) did not agree.
  target URealValue.minus(value)
    control  {AGREE=576}
    mutant   {AGREE=566, DIFFER=10}
    statement URealValue.minus(value): 576 rows, 576 measured, 566 agreed, 10 disagreed, 0 intended departure(s), 0 java-type mismatch(es) (subject token OBSERVED on 0, ASSUMED on 0), 225 distinct reference value(s) [DISCRIMINATING]
    stage pass? false   (control true)
    refused: 10 row(s) did not agree.
  first 6 diverging row(s):
  index	operation	inputs	historical	ported	verdict	note
  134	UIntegerValue.add(value)	UINTEGER(7,0.25)@UIntegerValue | UINTEGER(689,0.19065)@UIntegerValue	UINTEGER(696,0.3144000993956586)@UIntegerValue	UINTEGER(696,0.31440009939565855)@UIntegerValue	DIFFER	
  218	UIntegerValue.add(value)	UINTEGER(689,0.19065)@UIntegerValue | UINTEGER(7,0.25)@UIntegerValue	UINTEGER(696,0.3144000993956586)@UIntegerValue	UINTEGER(696,0.31440009939565855)@UIntegerValue	DIFFER	
  134	UIntegerValue.minus(value)	UINTEGER(7,0.25)@UIntegerValue | UINTEGER(689,0.19065)@UIntegerValue	UINTEGER(-682,0.3144000993956586)@UIntegerValue	UINTEGER(-682,0.31440009939565855)@UIntegerValue	DIFFER	
  218	UIntegerValue.minus(value)	UINTEGER(689,0.19065)@UIntegerValue | UINTEGER(7,0.25)@UIntegerValue	UINTEGER(682,0.3144000993956586)@UIntegerValue	UINTEGER(682,0.31440009939565855)@UIntegerValue	DIFFER	
  47	URealValue.add(value)	UREAL(0.0,1.0)@URealValue | UREAL(28.230986,0.91554)@URealValue	UREAL(28.230986,1.355807320971531)@URealValue	UREAL(28.230986,1.3558073209715311)@URealValue	DIFFER	
  119	URealValue.add(value)	UREAL(1.0,1.0)@URealValue | UREAL(28.230986,0.91554)@URealValue	UREAL(29.230986,1.355807320971531)@URealValue	UREAL(29.230986,1.3558073209715311)@URealValue	DIFFER	
===================================================================
=== detection power: P4-le-for-lt ============================
defect               an order comparison written <= where the historical writes < (and >= for >)
aimed at             [UIntegerValue.gt(value), UIntegerValue.lt(value), URealValue.gt(value), URealValue.lt(value), UStringValue.gt(value), UStringValue.lt(value)]
rows                 23963   (control 23963)
measured rows        21556   (control 21556)
agreement rows       21256   (control 21556)
verdict tally        {AGREE=21256, BOTH_THREW=1243, DIFFER=300, HARNESS_ERROR=1063, UNMEASURABLE=101}
DETECTED on          6 operation(s): [UIntegerValue.gt(value), UIntegerValue.lt(value), URealValue.gt(value), URealValue.lt(value), UStringValue.gt(value), UStringValue.lt(value)]
stage passes         70   (control 74)
isClean() operations 189   (control 193)   the older predicate loses 4: [UIntegerValue.gt(value), UIntegerValue.lt(value), URealValue.gt(value), URealValue.lt(value)]
  target UIntegerValue.gt(value)
    control  {AGREE=225}
    mutant   {AGREE=171, DIFFER=54}
    statement UIntegerValue.gt(value): 225 rows, 225 measured, 171 agreed, 54 disagreed, 0 intended departure(s), 0 java-type mismatch(es) (subject token OBSERVED on 0, ASSUMED on 0), 27 distinct reference value(s) [DISCRIMINATING]
    stage pass? false   (control true)
    refused: 54 row(s) did not agree.
  target UIntegerValue.lt(value)
    control  {AGREE=225}
    mutant   {AGREE=171, DIFFER=54}
    statement UIntegerValue.lt(value): 225 rows, 225 measured, 171 agreed, 54 disagreed, 0 intended departure(s), 0 java-type mismatch(es) (subject token OBSERVED on 0, ASSUMED on 0), 27 distinct reference value(s) [DISCRIMINATING]
    stage pass? false   (control true)
    refused: 54 row(s) did not agree.
  target URealValue.gt(value)
    control  {AGREE=576}
    mutant   {AGREE=510, DIFFER=66}
    statement URealValue.gt(value): 576 rows, 576 measured, 510 agreed, 66 disagreed, 0 intended departure(s), 0 java-type mismatch(es) (subject token OBSERVED on 0, ASSUMED on 0), 33 distinct reference value(s) [DISCRIMINATING]
    stage pass? false   (control true)
    refused: 66 row(s) did not agree.
  target URealValue.lt(value)
    control  {AGREE=576}
    mutant   {AGREE=510, DIFFER=66}
    statement URealValue.lt(value): 576 rows, 576 measured, 510 agreed, 66 disagreed, 0 intended departure(s), 0 java-type mismatch(es) (subject token OBSERVED on 0, ASSUMED on 0), 33 distinct reference value(s) [DISCRIMINATING]
    stage pass? false   (control true)
    refused: 66 row(s) did not agree.
  target UStringValue.gt(value)
    control  {AGREE=729, HARNESS_ERROR=55}
    mutant   {AGREE=699, DIFFER=30, HARNESS_ERROR=55}
    statement UStringValue.gt(value): 784 rows, 729 measured, 699 agreed, 85 disagreed, 0 intended departure(s), 0 java-type mismatch(es) (subject token OBSERVED on 0, ASSUMED on 0), 25 distinct reference value(s) [DISCRIMINATING]
    stage pass? false   (control false)
    refused: 85 row(s) did not agree.
  target UStringValue.lt(value)
    control  {AGREE=729, HARNESS_ERROR=55}
    mutant   {AGREE=699, DIFFER=30, HARNESS_ERROR=55}
    statement UStringValue.lt(value): 784 rows, 729 measured, 699 agreed, 85 disagreed, 0 intended departure(s), 0 java-type mismatch(es) (subject token OBSERVED on 0, ASSUMED on 0), 25 distinct reference value(s) [DISCRIMINATING]
    stage pass? false   (control false)
    refused: 85 row(s) did not agree.
  first 6 diverging row(s):
  index	operation	inputs	historical	ported	verdict	note
  0	UIntegerValue.gt(value)	UINTEGER(0,0.0)@UIntegerValue | UINTEGER(0,0.0)@UIntegerValue	UBOOLEAN(true,0.0)@UBooleanValue	UBOOLEAN(true,1.0)@UBooleanValue	DIFFER	
  16	UIntegerValue.gt(value)	UINTEGER(0,1.0)@UIntegerValue | UINTEGER(0,1.0)@UIntegerValue	UBOOLEAN(true,0.0)@UBooleanValue	UBOOLEAN(true,1.0)@UBooleanValue	DIFFER	
  18	UIntegerValue.gt(value)	UINTEGER(0,1.0)@UIntegerValue | UINTEGER(1,1.0)@UIntegerValue	UBOOLEAN(true,0.0)@UBooleanValue	UBOOLEAN(true,0.617075064424681)@UBooleanValue	DIFFER	
  20	UIntegerValue.gt(value)	UINTEGER(0,1.0)@UIntegerValue | UINTEGER(-1,1.0)@UIntegerValue	UBOOLEAN(true,0.38292493557531904)@UBooleanValue	UBOOLEAN(true,1.0)@UBooleanValue	DIFFER	
  21	UIntegerValue.gt(value)	UINTEGER(0,1.0)@UIntegerValue | UINTEGER(2,0.5)@UIntegerValue	UBOOLEAN(true,8.116315292738818E-6)@UBooleanValue	UBOOLEAN(true,0.16945774662466784)@UBooleanValue	DIFFER	
  22	UIntegerValue.gt(value)	UINTEGER(0,1.0)@UIntegerValue | UINTEGER(-2,0.5)@UIntegerValue	UBOOLEAN(true,0.830542253375332)@UBooleanValue	UBOOLEAN(true,0.9999918836847073)@UBooleanValue	DIFFER	
===================================================================
=== detection power: P5-round-10dp ============================
defect               results rounded to ten decimal places -- the classic 'it looked the same when I printed it' port
aimed at             [URealValue.cos(), URealValue.divideBy(value), URealValue.inverse(), URealValue.mult(value), URealValue.sin(), URealValue.sqrt(), URealValue.tan()]
rows                 23963   (control 23963)
measured rows        21556   (control 21556)
agreement rows       21128   (control 21556)
verdict tally        {AGREE=21128, BOTH_THREW=1243, DIFFER=428, HARNESS_ERROR=1063, UNMEASURABLE=101}
DETECTED on          7 operation(s): [URealValue.cos(), URealValue.divideBy(value), URealValue.inverse(), URealValue.mult(value), URealValue.sin(), URealValue.sqrt(), URealValue.tan()]
stage passes         67   (control 74)
isClean() operations 186   (control 193)   the older predicate loses 7: [URealValue.cos(), URealValue.divideBy(value), URealValue.inverse(), URealValue.mult(value), URealValue.sin(), URealValue.sqrt(), URealValue.tan()]
  target URealValue.cos()
    control  {AGREE=24}
    mutant   {AGREE=7, DIFFER=17}
    statement URealValue.cos(): 24 rows, 24 measured, 7 agreed, 17 disagreed, 0 intended departure(s), 0 java-type mismatch(es) (subject token OBSERVED on 0, ASSUMED on 0), 14 distinct reference value(s) [DISCRIMINATING]
    stage pass? false   (control true)
    refused: 17 row(s) did not agree.
  target URealValue.divideBy(value)
    control  {AGREE=576}
    mutant   {AGREE=374, DIFFER=202}
    statement URealValue.divideBy(value): 576 rows, 576 measured, 374 agreed, 202 disagreed, 0 intended departure(s), 0 java-type mismatch(es) (subject token OBSERVED on 0, ASSUMED on 0), 241 distinct reference value(s) [DISCRIMINATING]
    stage pass? false   (control true)
    refused: 202 row(s) did not agree.
  target URealValue.inverse()
    control  {AGREE=24}
    mutant   {AGREE=19, DIFFER=5}
    statement URealValue.inverse(): 24 rows, 24 measured, 19 agreed, 5 disagreed, 0 intended departure(s), 0 java-type mismatch(es) (subject token OBSERVED on 0, ASSUMED on 0), 23 distinct reference value(s) [DISCRIMINATING]
    stage pass? false   (control true)
    refused: 5 row(s) did not agree.
  target URealValue.mult(value)
    control  {AGREE=576}
    mutant   {AGREE=414, DIFFER=162}
    statement URealValue.mult(value): 576 rows, 576 measured, 414 agreed, 162 disagreed, 0 intended departure(s), 0 java-type mismatch(es) (subject token OBSERVED on 0, ASSUMED on 0), 121 distinct reference value(s) [DISCRIMINATING]
    stage pass? false   (control true)
    refused: 162 row(s) did not agree.
  target URealValue.sin()
    control  {AGREE=24}
    mutant   {AGREE=5, DIFFER=19}
    statement URealValue.sin(): 24 rows, 24 measured, 5 agreed, 19 disagreed, 0 intended departure(s), 0 java-type mismatch(es) (subject token OBSERVED on 0, ASSUMED on 0), 21 distinct reference value(s) [DISCRIMINATING]
    stage pass? false   (control true)
    refused: 19 row(s) did not agree.
  target URealValue.sqrt()
    control  {AGREE=24}
    mutant   {AGREE=20, DIFFER=4}
    statement URealValue.sqrt(): 24 rows, 24 measured, 20 agreed, 4 disagreed, 0 intended departure(s), 0 java-type mismatch(es) (subject token OBSERVED on 0, ASSUMED on 0), 14 distinct reference value(s) [DISCRIMINATING]
    stage pass? false   (control true)
    refused: 4 row(s) did not agree.
  target URealValue.tan()
    control  {AGREE=24}
    mutant   {AGREE=5, DIFFER=19}
    statement URealValue.tan(): 24 rows, 24 measured, 5 agreed, 19 disagreed, 0 intended departure(s), 0 java-type mismatch(es) (subject token OBSERVED on 0, ASSUMED on 0), 21 distinct reference value(s) [DISCRIMINATING]
    stage pass? false   (control true)
    refused: 19 row(s) did not agree.
  first 6 diverging row(s):
  index	operation	inputs	historical	ported	verdict	note
  3	URealValue.cos()	UREAL(1.0,0.0)@URealValue	UREAL(0.5403023058681398,0.0)@URealValue	UREAL(0.5403023059,0.0)@URealValue	DIFFER	
  4	URealValue.cos()	UREAL(1.0,1.0)@URealValue	UREAL(0.5403023058681398,0.8414709848078965)@URealValue	UREAL(0.5403023059,0.8414709848)@URealValue	DIFFER	
  5	URealValue.cos()	UREAL(-1.0,0.0)@URealValue	UREAL(0.5403023058681398,0.0)@URealValue	UREAL(0.5403023059,0.0)@URealValue	DIFFER	
  6	URealValue.cos()	UREAL(-1.0,1.0)@URealValue	UREAL(0.5403023058681398,0.8414709848078965)@URealValue	UREAL(0.5403023059,0.8414709848)@URealValue	DIFFER	
  7	URealValue.cos()	UREAL(-1.0,0.5)@URealValue	UREAL(0.5403023058681398,0.42073549240394825)@URealValue	UREAL(0.5403023059,0.4207354924)@URealValue	DIFFER	
  8	URealValue.cos()	UREAL(0.5,0.5)@URealValue	UREAL(0.8775825618903728,0.2397127693021015)@URealValue	UREAL(0.8775825619,0.2397127693)@URealValue	DIFFER	
===================================================================
=== detection power: P6-equals-ignores-uncertainty ============================
defect               uEquals compares the values and returns certainty 1.0, ignoring the uncertainty component entirely
aimed at             [UBooleanValue.uEquals(value), UIntegerValue.uEquals(value), URealValue.uEquals(value), UStringValue.uEquals(value)]
rows                 23963   (control 23963)
measured rows        21556   (control 21556)
agreement rows       20000   (control 21556)
verdict tally        {AGREE=20000, BOTH_THREW=1243, DIFFER=1556, HARNESS_ERROR=1063, UNMEASURABLE=101}
DETECTED on          4 operation(s): [UBooleanValue.uEquals(value), UIntegerValue.uEquals(value), URealValue.uEquals(value), UStringValue.uEquals(value)]
stage passes         72   (control 74)
isClean() operations 191   (control 193)   the older predicate loses 2: [UIntegerValue.uEquals(value), URealValue.uEquals(value)]
  target UBooleanValue.uEquals(value)
    control  {AGREE=81, HARNESS_ERROR=40}
    mutant   {AGREE=8, DIFFER=73, HARNESS_ERROR=40}
    statement UBooleanValue.uEquals(value): 121 rows, 81 measured, 8 agreed, 113 disagreed, 0 intended departure(s), 0 java-type mismatch(es) (subject token OBSERVED on 0, ASSUMED on 0), 11 distinct reference value(s) [DISCRIMINATING]
    stage pass? false   (control false)
    refused: 113 row(s) did not agree.
  target UIntegerValue.uEquals(value)
    control  {AGREE=225}
    mutant   {AGREE=16, DIFFER=209}
    statement UIntegerValue.uEquals(value): 225 rows, 225 measured, 16 agreed, 209 disagreed, 0 intended departure(s), 0 java-type mismatch(es) (subject token OBSERVED on 0, ASSUMED on 0), 15 distinct reference value(s) [DISCRIMINATING]
    stage pass? false   (control true)
    refused: 209 row(s) did not agree.
  target URealValue.uEquals(value)
    control  {AGREE=576}
    mutant   {AGREE=26, DIFFER=550}
    statement URealValue.uEquals(value): 576 rows, 576 measured, 26 agreed, 550 disagreed, 0 intended departure(s), 0 java-type mismatch(es) (subject token OBSERVED on 0, ASSUMED on 0), 14 distinct reference value(s) [DISCRIMINATING]
    stage pass? false   (control true)
    refused: 550 row(s) did not agree.
  target UStringValue.uEquals(value)
    control  {AGREE=729, HARNESS_ERROR=55}
    mutant   {AGREE=5, DIFFER=724, HARNESS_ERROR=55}
    statement UStringValue.uEquals(value): 784 rows, 729 measured, 5 agreed, 779 disagreed, 0 intended departure(s), 0 java-type mismatch(es) (subject token OBSERVED on 0, ASSUMED on 0), 17 distinct reference value(s) [DISCRIMINATING]
    stage pass? false   (control false)
    refused: 779 row(s) did not agree.
  first 6 diverging row(s):
  index	operation	inputs	historical	ported	verdict	note
  1	UBooleanValue.uEquals(value)	UBOOLEAN(true,0.0)@UBooleanValue | UBOOLEAN(true,0.5)@UBooleanValue	UBOOLEAN(true,0.5)@UBooleanValue	UBOOLEAN(true,1.0)@UBooleanValue	DIFFER	
  2	UBooleanValue.uEquals(value)	UBOOLEAN(true,0.0)@UBooleanValue | UBOOLEAN(true,1.0)@UBooleanValue	UBOOLEAN(true,0.0)@UBooleanValue	UBOOLEAN(true,1.0)@UBooleanValue	DIFFER	
  3	UBooleanValue.uEquals(value)	UBOOLEAN(true,0.0)@UBooleanValue | UBOOLEAN(false,0.0)@UBooleanValue	UBOOLEAN(true,0.0)@UBooleanValue	UBOOLEAN(false,1.0)@UBooleanValue	DIFFER	
  4	UBooleanValue.uEquals(value)	UBOOLEAN(true,0.0)@UBooleanValue | UBOOLEAN(false,0.5)@UBooleanValue	UBOOLEAN(true,0.5)@UBooleanValue	UBOOLEAN(false,1.0)@UBooleanValue	DIFFER	
  5	UBooleanValue.uEquals(value)	UBOOLEAN(true,0.0)@UBooleanValue | UBOOLEAN(false,1.0)@UBooleanValue	UBOOLEAN(true,1.0)@UBooleanValue	UBOOLEAN(false,1.0)@UBooleanValue	DIFFER	
  6	UBooleanValue.uEquals(value)	UBOOLEAN(true,0.0)@UBooleanValue | UBOOLEAN(true,NaN)@UBooleanValue	UBOOLEAN(true,NaN)@UBooleanValue	UBOOLEAN(true,1.0)@UBooleanValue	DIFFER	
===================================================================
=== detection power: P7-undefined-on-zero-divisor ============================
defect               division by zero answers UndefinedValue where the historical throws
aimed at             [UIntegerValue.divideBy(value), UIntegerValue.divideByR(value), UIntegerValue.inverse(), UIntegerValue.mod(value), URealValue.divideBy(value), URealValue.inverse()]
rows                 23963   (control 23963)
measured rows        21556   (control 21556)
agreement rows       21451   (control 21556)
verdict tally        {AGREE=21451, BOTH_THREW=1181, DIFFER=105, HARNESS_ERROR=1063, MIXED=62, UNMEASURABLE=101}
DETECTED on          6 operation(s): [UIntegerValue.divideBy(value), UIntegerValue.divideByR(value), UIntegerValue.inverse(), UIntegerValue.mod(value), URealValue.divideBy(value), URealValue.inverse()]
stage passes         71   (control 74)
isClean() operations 190   (control 193)   the older predicate loses 3: [UIntegerValue.divideByR(value), URealValue.divideBy(value), URealValue.inverse()]
  target UIntegerValue.divideBy(value)
    control  {AGREE=195, BOTH_THREW=30}
    mutant   {AGREE=195, MIXED=30}
    statement UIntegerValue.divideBy(value): 225 rows, 195 measured, 195 agreed, 30 disagreed, 0 intended departure(s), 0 java-type mismatch(es) (subject token OBSERVED on 0, ASSUMED on 0), 92 distinct reference value(s) [DISCRIMINATING]
    stage pass? false   (control false)
    refused: 30 row(s) did not agree.
  target UIntegerValue.divideByR(value)
    control  {AGREE=225}
    mutant   {AGREE=195, DIFFER=30}
    statement UIntegerValue.divideByR(value): 225 rows, 225 measured, 195 agreed, 30 disagreed, 0 intended departure(s), 0 java-type mismatch(es) (subject token OBSERVED on 0, ASSUMED on 0), 137 distinct reference value(s) [DISCRIMINATING]
    stage pass? false   (control true)
    refused: 30 row(s) did not agree.
  target UIntegerValue.inverse()
    control  {AGREE=13, BOTH_THREW=2}
    mutant   {AGREE=13, MIXED=2}
    statement UIntegerValue.inverse(): 15 rows, 13 measured, 13 agreed, 2 disagreed, 0 intended departure(s), 0 java-type mismatch(es) (subject token OBSERVED on 0, ASSUMED on 0), 10 distinct reference value(s) [DISCRIMINATING]
    stage pass? false   (control false)
    refused: 2 row(s) did not agree.
  target UIntegerValue.mod(value)
    control  {AGREE=195, BOTH_THREW=30}
    mutant   {AGREE=195, MIXED=30}
    statement UIntegerValue.mod(value): 225 rows, 195 measured, 195 agreed, 30 disagreed, 0 intended departure(s), 0 java-type mismatch(es) (subject token OBSERVED on 0, ASSUMED on 0), 80 distinct reference value(s) [DISCRIMINATING]
    stage pass? false   (control false)
    refused: 30 row(s) did not agree.
  target URealValue.divideBy(value)
    control  {AGREE=576}
    mutant   {AGREE=504, DIFFER=72}
    statement URealValue.divideBy(value): 576 rows, 576 measured, 504 agreed, 72 disagreed, 0 intended departure(s), 0 java-type mismatch(es) (subject token OBSERVED on 0, ASSUMED on 0), 241 distinct reference value(s) [DISCRIMINATING]
    stage pass? false   (control true)
    refused: 72 row(s) did not agree.
  target URealValue.inverse()
    control  {AGREE=24}
    mutant   {AGREE=21, DIFFER=3}
    statement URealValue.inverse(): 24 rows, 24 measured, 21 agreed, 3 disagreed, 0 intended departure(s), 0 java-type mismatch(es) (subject token OBSERVED on 0, ASSUMED on 0), 23 distinct reference value(s) [DISCRIMINATING]
    stage pass? false   (control true)
    refused: 3 row(s) did not agree.
  first 6 diverging row(s):
  index	operation	inputs	historical	ported	verdict	note
  0	UIntegerValue.divideBy(value)	UINTEGER(0,0.0)@UIntegerValue | UINTEGER(0,0.0)@UIntegerValue	THROWN:java.lang.ArithmeticException	OPAQUE("org.tzi.use.uml.ocl.value.UndefinedValue|UndefinedValue{}")@UndefinedValue	MIXED	the reference threw and the subject returned. reference threw java.lang.ArithmeticException: / by zero / subject returned OPAQUE("org.tzi.use.uml.ocl.value.UndefinedValue|UndefinedValue{}")@UndefinedValue
  1	UIntegerValue.divideBy(value)	UINTEGER(0,0.0)@UIntegerValue | UINTEGER(0,1.0)@UIntegerValue	THROWN:java.lang.ArithmeticException	OPAQUE("org.tzi.use.uml.ocl.value.UndefinedValue|UndefinedValue{}")@UndefinedValue	MIXED	the reference threw and the subject returned. reference threw java.lang.ArithmeticException: / by zero / subject returned OPAQUE("org.tzi.use.uml.ocl.value.UndefinedValue|UndefinedValue{}")@UndefinedValue
  15	UIntegerValue.divideBy(value)	UINTEGER(0,1.0)@UIntegerValue | UINTEGER(0,0.0)@UIntegerValue	THROWN:java.lang.ArithmeticException	OPAQUE("org.tzi.use.uml.ocl.value.UndefinedValue|UndefinedValue{}")@UndefinedValue	MIXED	the reference threw and the subject returned. reference threw java.lang.ArithmeticException: / by zero / subject returned OPAQUE("org.tzi.use.uml.ocl.value.UndefinedValue|UndefinedValue{}")@UndefinedValue
  16	UIntegerValue.divideBy(value)	UINTEGER(0,1.0)@UIntegerValue | UINTEGER(0,1.0)@UIntegerValue	THROWN:java.lang.ArithmeticException	OPAQUE("org.tzi.use.uml.ocl.value.UndefinedValue|UndefinedValue{}")@UndefinedValue	MIXED	the reference threw and the subject returned. reference threw java.lang.ArithmeticException: / by zero / subject returned OPAQUE("org.tzi.use.uml.ocl.value.UndefinedValue|UndefinedValue{}")@UndefinedValue
  30	UIntegerValue.divideBy(value)	UINTEGER(1,0.0)@UIntegerValue | UINTEGER(0,0.0)@UIntegerValue	THROWN:java.lang.ArithmeticException	OPAQUE("org.tzi.use.uml.ocl.value.UndefinedValue|UndefinedValue{}")@UndefinedValue	MIXED	the reference threw and the subject returned. reference threw java.lang.ArithmeticException: / by zero / subject returned OPAQUE("org.tzi.use.uml.ocl.value.UndefinedValue|UndefinedValue{}")@UndefinedValue
  31	UIntegerValue.divideBy(value)	UINTEGER(1,0.0)@UIntegerValue | UINTEGER(0,1.0)@UIntegerValue	THROWN:java.lang.ArithmeticException	OPAQUE("org.tzi.use.uml.ocl.value.UndefinedValue|UndefinedValue{}")@UndefinedValue	MIXED	the reference threw and the subject returned. reference threw java.lang.ArithmeticException: / by zero / subject returned OPAQUE("org.tzi.use.uml.ocl.value.UndefinedValue|UndefinedValue{}")@UndefinedValue
===================================================================
=== detection power: P8-hides-behind-harness-error ============================
defect               P2's defect, plus an adapter that raises HarnessMarshallingException on exactly the rows where it would have shown (defect D-17)
aimed at             [UIntegerValue.add(value), UIntegerValue.minus(value), URealValue.add(value), URealValue.minus(value)]
rows                 23963   (control 23963)
measured rows        21088   (control 21556)
agreement rows       21088   (control 21556)
verdict tally        {AGREE=21088, BOTH_THREW=1243, HARNESS_ERROR=1531, UNMEASURABLE=101}
DETECTED on          0 operation(s): []
stage passes         70   (control 74)
isClean() operations 189   (control 193)   the older predicate loses 4: [UIntegerValue.add(value), UIntegerValue.minus(value), URealValue.add(value), URealValue.minus(value)]
  target UIntegerValue.add(value)
    control  {AGREE=225}
    mutant   {AGREE=134, HARNESS_ERROR=91}
    statement UIntegerValue.add(value): 225 rows, 134 measured, 134 agreed, 91 disagreed, 0 intended departure(s), 0 java-type mismatch(es) (subject token OBSERVED on 0, ASSUMED on 0), 53 distinct reference value(s) [DISCRIMINATING]
    stage pass? false   (control true)
    refused: 91 row(s) did not agree.
  target UIntegerValue.minus(value)
    control  {AGREE=225}
    mutant   {AGREE=134, HARNESS_ERROR=91}
    statement UIntegerValue.minus(value): 225 rows, 134 measured, 134 agreed, 91 disagreed, 0 intended departure(s), 0 java-type mismatch(es) (subject token OBSERVED on 0, ASSUMED on 0), 79 distinct reference value(s) [DISCRIMINATING]
    stage pass? false   (control true)
    refused: 91 row(s) did not agree.
  target URealValue.add(value)
    control  {AGREE=576}
    mutant   {AGREE=433, HARNESS_ERROR=143}
    statement URealValue.add(value): 576 rows, 433 measured, 433 agreed, 143 disagreed, 0 intended departure(s), 0 java-type mismatch(es) (subject token OBSERVED on 0, ASSUMED on 0), 110 distinct reference value(s) [DISCRIMINATING]
    stage pass? false   (control true)
    refused: 143 row(s) did not agree.
  target URealValue.minus(value)
    control  {AGREE=576}
    mutant   {AGREE=433, HARNESS_ERROR=143}
    statement URealValue.minus(value): 576 rows, 433 measured, 433 agreed, 143 disagreed, 0 intended departure(s), 0 java-type mismatch(es) (subject token OBSERVED on 0, ASSUMED on 0), 142 distinct reference value(s) [DISCRIMINATING]
    stage pass? false   (control true)
    refused: 143 row(s) did not agree.
===================================================================
=== detection power: P9-hides-behind-unsupported ============================
defect               P2's defect, plus supports() answering false for the operations that carry it
aimed at             [UIntegerValue.add(value), UIntegerValue.minus(value), URealValue.add(value), URealValue.minus(value)]
rows                 23963   (control 23963)
measured rows        19954   (control 21556)
agreement rows       19954   (control 21556)
verdict tally        {AGREE=19954, BOTH_THREW=1243, HARNESS_ERROR=1063, UNMEASURABLE=101, UNSUPPORTED=1602}
DETECTED on          0 operation(s): []
stage passes         70   (control 74)
isClean() operations 189   (control 193)   the older predicate loses 4: [UIntegerValue.add(value), UIntegerValue.minus(value), URealValue.add(value), URealValue.minus(value)]
  target UIntegerValue.add(value)
    control  {AGREE=225}
    mutant   {UNSUPPORTED=225}
    statement UIntegerValue.add(value): 225 rows, 0 measured, 0 agreed, 225 disagreed, 0 intended departure(s), 0 java-type mismatch(es) (subject token OBSERVED on 0, ASSUMED on 0), 0 distinct reference value(s) [NOT DISCRIMINATING]
    stage pass? false   (control true)
    refused: measured 0 row(s) of 225, needed at least 1. A result with too little evidence is not evidence.
    refused: 225 row(s) did not agree.
    refused: the reference side produced 0 distinct value(s) across 0 measured row(s). This operation could not have failed over this domain, so agreement on it is decided before either implementation runs and is not evidence of fidelity (defect D-15). Either widen the domain until the reference answers differently, or sign the operation off in AcceptedDegenerateOperations with a written rationale — which is copied into the report, so the weakness travels with the number.
  target UIntegerValue.minus(value)
    control  {AGREE=225}
    mutant   {UNSUPPORTED=225}
    statement UIntegerValue.minus(value): 225 rows, 0 measured, 0 agreed, 225 disagreed, 0 intended departure(s), 0 java-type mismatch(es) (subject token OBSERVED on 0, ASSUMED on 0), 0 distinct reference value(s) [NOT DISCRIMINATING]
    stage pass? false   (control true)
    refused: measured 0 row(s) of 225, needed at least 1. A result with too little evidence is not evidence.
    refused: 225 row(s) did not agree.
    refused: the reference side produced 0 distinct value(s) across 0 measured row(s). This operation could not have failed over this domain, so agreement on it is decided before either implementation runs and is not evidence of fidelity (defect D-15). Either widen the domain until the reference answers differently, or sign the operation off in AcceptedDegenerateOperations with a written rationale — which is copied into the report, so the weakness travels with the number.
  target URealValue.add(value)
    control  {AGREE=576}
    mutant   {UNSUPPORTED=576}
    statement URealValue.add(value): 576 rows, 0 measured, 0 agreed, 576 disagreed, 0 intended departure(s), 0 java-type mismatch(es) (subject token OBSERVED on 0, ASSUMED on 0), 0 distinct reference value(s) [NOT DISCRIMINATING]
    stage pass? false   (control true)
    refused: measured 0 row(s) of 576, needed at least 1. A result with too little evidence is not evidence.
    refused: 576 row(s) did not agree.
    refused: the reference side produced 0 distinct value(s) across 0 measured row(s). This operation could not have failed over this domain, so agreement on it is decided before either implementation runs and is not evidence of fidelity (defect D-15). Either widen the domain until the reference answers differently, or sign the operation off in AcceptedDegenerateOperations with a written rationale — which is copied into the report, so the weakness travels with the number.
  target URealValue.minus(value)
    control  {AGREE=576}
    mutant   {UNSUPPORTED=576}
    statement URealValue.minus(value): 576 rows, 0 measured, 0 agreed, 576 disagreed, 0 intended departure(s), 0 java-type mismatch(es) (subject token OBSERVED on 0, ASSUMED on 0), 0 distinct reference value(s) [NOT DISCRIMINATING]
    stage pass? false   (control true)
    refused: measured 0 row(s) of 576, needed at least 1. A result with too little evidence is not evidence.
    refused: 576 row(s) did not agree.
    refused: the reference side produced 0 distinct value(s) across 0 measured row(s). This operation could not have failed over this domain, so agreement on it is decided before either implementation runs and is not evidence of fidelity (defect D-15). Either widen the domain until the reference answers differently, or sign the operation off in AcceptedDegenerateOperations with a written rationale — which is copied into the report, so the weakness travels with the number.
===================================================================
=== detection power: P10-narrow-input-window ============================
defect               P2's defect, restricted to receivers whose value is exactly 42.0 -- a real arithmetic bug on an input no shipped corpus contains
aimed at             [UIntegerValue.add(value), UIntegerValue.minus(value), URealValue.add(value), URealValue.minus(value)]
rows                 23963   (control 23963)
measured rows        21556   (control 21556)
agreement rows       21556   (control 21556)
verdict tally        {AGREE=21556, BOTH_THREW=1243, HARNESS_ERROR=1063, UNMEASURABLE=101}
DETECTED on          0 operation(s): []
stage passes         74   (control 74)
isClean() operations 193   (control 193)   the older predicate loses 0: []
  target UIntegerValue.add(value)
    control  {AGREE=225}
    mutant   {AGREE=225}
    statement UIntegerValue.add(value): 225 rows, 225 measured, 225 agreed, 0 disagreed, 0 intended departure(s), 0 java-type mismatch(es) (subject token OBSERVED on 0, ASSUMED on 0), 88 distinct reference value(s) [DISCRIMINATING]
    stage pass? true   (control true)
  target UIntegerValue.minus(value)
    control  {AGREE=225}
    mutant   {AGREE=225}
    statement UIntegerValue.minus(value): 225 rows, 225 measured, 225 agreed, 0 disagreed, 0 intended departure(s), 0 java-type mismatch(es) (subject token OBSERVED on 0, ASSUMED on 0), 132 distinct reference value(s) [DISCRIMINATING]
    stage pass? true   (control true)
  target URealValue.add(value)
    control  {AGREE=576}
    mutant   {AGREE=576}
    statement URealValue.add(value): 576 rows, 576 measured, 576 agreed, 0 disagreed, 0 intended departure(s), 0 java-type mismatch(es) (subject token OBSERVED on 0, ASSUMED on 0), 164 distinct reference value(s) [DISCRIMINATING]
    stage pass? true   (control true)
  target URealValue.minus(value)
    control  {AGREE=576}
    mutant   {AGREE=576}
    statement URealValue.minus(value): 576 rows, 576 measured, 576 agreed, 0 disagreed, 0 intended departure(s), 0 java-type mismatch(es) (subject token OBSERVED on 0, ASSUMED on 0), 225 distinct reference value(s) [DISCRIMINATING]
    stage pass? true   (control true)
===================================================================
=== detection power: P11-negative-zero-collapse ============================
defect               a port that normalises -0.0 to 0.0 -- invisible to every printf and to Double.equals, visible only to an exact comparison
aimed at             [URealValue.floor(), URealValue.mult(value), URealValue.neg(), URealValue.round()]
rows                 23963   (control 23963)
measured rows        21556   (control 21556)
agreement rows       21497   (control 21556)
verdict tally        {AGREE=21497, BOTH_THREW=1243, DIFFER=59, HARNESS_ERROR=1063, UNMEASURABLE=101}
DETECTED on          3 operation(s): [URealValue.floor(), URealValue.mult(value), URealValue.neg()]
stage passes         71   (control 74)
isClean() operations 190   (control 193)   the older predicate loses 3: [URealValue.floor(), URealValue.mult(value), URealValue.neg()]
  target URealValue.floor()
    control  {AGREE=24}
    mutant   {AGREE=23, DIFFER=1}
    statement URealValue.floor(): 24 rows, 24 measured, 23 agreed, 1 disagreed, 0 intended departure(s), 0 java-type mismatch(es) (subject token OBSERVED on 0, ASSUMED on 0), 22 distinct reference value(s) [DISCRIMINATING]
    stage pass? false   (control true)
    refused: 1 row(s) did not agree.
  target URealValue.mult(value)
    control  {AGREE=576}
    mutant   {AGREE=520, DIFFER=56}
    statement URealValue.mult(value): 576 rows, 576 measured, 520 agreed, 56 disagreed, 0 intended departure(s), 0 java-type mismatch(es) (subject token OBSERVED on 0, ASSUMED on 0), 121 distinct reference value(s) [DISCRIMINATING]
    stage pass? false   (control true)
    refused: 56 row(s) did not agree.
  target URealValue.neg()
    control  {AGREE=24}
    mutant   {AGREE=22, DIFFER=2}
    statement URealValue.neg(): 24 rows, 24 measured, 22 agreed, 2 disagreed, 0 intended departure(s), 0 java-type mismatch(es) (subject token OBSERVED on 0, ASSUMED on 0), 23 distinct reference value(s) [DISCRIMINATING]
    stage pass? false   (control true)
    refused: 2 row(s) did not agree.
  target URealValue.round()
    control  {AGREE=24}
    mutant   {AGREE=24}
    statement URealValue.round(): 24 rows, 24 measured, 24 agreed, 0 disagreed, 0 intended departure(s), 0 java-type mismatch(es) (subject token OBSERVED on 0, ASSUMED on 0), 18 distinct reference value(s) [DISCRIMINATING]
    stage pass? true   (control true)
  first 6 diverging row(s):
  index	operation	inputs	historical	ported	verdict	note
  2	URealValue.floor()	UREAL(-0.0,0.0)@URealValue	UREAL(-0.0,0.0)@URealValue	UREAL(0.0,0.0)@URealValue	DIFFER	
  2	URealValue.mult(value)	UREAL(0.0,0.0)@URealValue | UREAL(-0.0,0.0)@URealValue	UREAL(-0.0,0.0)@URealValue	UREAL(0.0,0.0)@URealValue	DIFFER	
  5	URealValue.mult(value)	UREAL(0.0,0.0)@URealValue | UREAL(-1.0,0.0)@URealValue	UREAL(-0.0,0.0)@URealValue	UREAL(0.0,0.0)@URealValue	DIFFER	
  6	URealValue.mult(value)	UREAL(0.0,0.0)@URealValue | UREAL(-1.0,1.0)@URealValue	UREAL(-0.0,0.0)@URealValue	UREAL(0.0,0.0)@URealValue	DIFFER	
  7	URealValue.mult(value)	UREAL(0.0,0.0)@URealValue | UREAL(-1.0,0.5)@URealValue	UREAL(-0.0,0.0)@URealValue	UREAL(0.0,0.0)@URealValue	DIFFER	
  9	URealValue.mult(value)	UREAL(0.0,0.0)@URealValue | UREAL(-0.5,0.25)@URealValue	UREAL(-0.0,0.0)@URealValue	UREAL(0.0,0.0)@URealValue	DIFFER	
===================================================================
=== planted defects the harness did NOT see =======================
  ??? P11-negative-zero-collapse / URealValue.round()  [STAGE PASS]
===================================================================
=== isClean() against requireStagePass, on the same defects =======
  probe                        detected  isClean lost  gate lost  divergence with NO change in the pass bit
  P1-off-by-one-index               3             0          0          3
  P2-linear-uncertainty             4             4          4          0
  P3-hypot-uncertainty              4             4          4          0
  P4-le-for-lt                      6             4          4          2
  P5-round-10dp                     7             7          7          0
  P6-equals-ignores-uncertainty      4             2          2          2
  P7-undefined-on-zero-divisor      6             3          3          3
  P8-hides-behind-harness-error      0             4          4          0
  P9-hides-behind-unsupported       0             4          4          0
  P10-narrow-input-window           0             0          0          0
  P11-negative-zero-collapse        3             3          3          0
  operations where a real infidelity leaves the pass bit unchanged, because a PERFECT port already fails the gate there:
    !!! P1-off-by-one-index / UStringValue.at(int)
    !!! P1-off-by-one-index / UStringValue.uAt(int)
    !!! P1-off-by-one-index / UStringValue.uSubstring(int,int)
    !!! P4-le-for-lt / UStringValue.gt(value)
    !!! P4-le-for-lt / UStringValue.lt(value)
    !!! P6-equals-ignores-uncertainty / UBooleanValue.uEquals(value)
    !!! P6-equals-ignores-uncertainty / UStringValue.uEquals(value)
    !!! P7-undefined-on-zero-divisor / UIntegerValue.divideBy(value)
    !!! P7-undefined-on-zero-divisor / UIntegerValue.inverse()
    !!! P7-undefined-on-zero-divisor / UIntegerValue.mod(value)
===================================================================
=== representation census: what the reference actually returns =====
operations                 355  (276 ever returned a classed value)
--- every runtime class the reference returned, and on how many rows
  1732	java.lang.Boolean
  100	java.lang.Double
  2029	java.lang.Integer
  244	java.lang.String
  4	org.tzi.use.uml.ocl.type.BooleanType
  16	org.tzi.use.uml.ocl.type.IntegerType
  2	org.tzi.use.uml.ocl.type.RealType
  30	org.tzi.use.uml.ocl.type.StringType
  18	org.tzi.use.uml.ocl.type.UBooleanType
  30	org.tzi.use.uml.ocl.type.UIntegerType
  48	org.tzi.use.uml.ocl.type.URealType
  54	org.tzi.use.uml.ocl.type.UStringType
  279	org.tzi.use.uml.ocl.value.BooleanValue
  43	org.tzi.use.uml.ocl.value.IntegerValue
  46	org.tzi.use.uml.ocl.value.RealValue
  27	org.tzi.use.uml.ocl.value.SequenceValue
  97	org.tzi.use.uml.ocl.value.StringValue
  10512	org.tzi.use.uml.ocl.value.UBooleanValue
  1174	org.tzi.use.uml.ocl.value.UIntegerValue
  4176	org.tzi.use.uml.ocl.value.URealValue
  880	org.tzi.use.uml.ocl.value.UStringValue
  15	uDataTypes.UInteger
--- classes per UValue.Kind (two means the KIND is ambiguous: D-18) ---
  BOOLEAN  [java.lang.Boolean, org.tzi.use.uml.ocl.value.BooleanValue]   <== two representations of one kind
  INTEGER  [java.lang.Integer, org.tzi.use.uml.ocl.value.IntegerValue]   <== two representations of one kind
  OPAQUE  [org.tzi.use.uml.ocl.type.BooleanType, org.tzi.use.uml.ocl.type.IntegerType, org.tzi.use.uml.ocl.type.RealType, org.tzi.use.uml.ocl.type.StringType, org.tzi.use.uml.ocl.type.UBooleanType, org.tzi.use.uml.ocl.type.UIntegerType, org.tzi.use.uml.ocl.type.URealType, org.tzi.use.uml.ocl.type.UStringType, uDataTypes.UInteger]   <== two representations of one kind
  REAL  [java.lang.Double, org.tzi.use.uml.ocl.value.RealValue]   <== two representations of one kind
  SEQUENCE  [org.tzi.use.uml.ocl.value.SequenceValue]
  STRING  [java.lang.String, org.tzi.use.uml.ocl.value.StringValue]   <== two representations of one kind
  UBOOLEAN  [org.tzi.use.uml.ocl.value.UBooleanValue]
  UINTEGER  [org.tzi.use.uml.ocl.value.UIntegerValue]
  UREAL  [org.tzi.use.uml.ocl.value.URealValue]
  USTRING  [org.tzi.use.uml.ocl.value.UStringValue]
--- operations whose OWN answers used more than one class -------------
  (none)
===================================================================
=== corpus sensitivity ============================================
full corpora         uReal=24, uInteger=15, uBoolean=11, uString=28, boolean=4, string=16, zeroDivisors=7, indexBoundaries=8
finite-only corpora  uReal=19, uInteger=14, uBoolean=10, uString=27, boolean=4, string=16, zeroDivisors=7, indexBoundaries=8
probe                          detecting rows   ops detected
                               full  finite     full  finite
  P0-perfect                       0      0         0      0
  P1-off-by-one-index            244    234         3      3
  P2-linear-uncertainty          468    456         4      4
  P3-hypot-uncertainty            24     20         4      4
  P4-le-for-lt                   300    294         6      6
  P5-round-10dp                  428    374         7      7
  P6-equals-ignores-uncertainty  1556   1245         4      4
  P7-undefined-on-zero-divisor   167    146         6      6
  P8-hides-behind-harness-error     0      0         0      0
  P9-hides-behind-unsupported      0      0         0      0
  P10-narrow-input-window          0      0         0      0
  P11-negative-zero-collapse      59     55         3      3
===================================================================
=== the inventory boundary ========================================
public instance methods on the 8 marshallable receivers  392
expressible as a UOp (before de-duplication)             355
NOT nameable, therefore absent from every report         37
  BooleanValue
      compareTo[class java.lang.Object]
      equals[class java.lang.Object]
      toStringWithType[class java.lang.StringBuilder]
      toString[class java.lang.StringBuilder]
  IntegerValue
      compareTo[class java.lang.Object]
      equals[class java.lang.Object]
      toStringWithType[class java.lang.StringBuilder]
      toString[class java.lang.StringBuilder]
  RealValue
      compareTo[class java.lang.Object]
      equals[class java.lang.Object]
      toStringWithType[class java.lang.StringBuilder]
      toString[class java.lang.StringBuilder]
  SBooleanValue
      compareTo[class java.lang.Object]
      equals[class java.lang.Object]
      toStringWithType[class java.lang.StringBuilder]
      toString[class java.lang.StringBuilder]
  StringValue
      compareTo[class java.lang.Object]
      equals[class java.lang.Object]
      toStringWithType[class java.lang.StringBuilder]
      toString[class java.lang.StringBuilder]
  UBooleanValue
      compareTo[class java.lang.Object]
      equals[class java.lang.Object]
      toStringWithType[class java.lang.StringBuilder]
      toString[class java.lang.StringBuilder]
  UIntegerValue
      compareTo[class java.lang.Object]
      equals[class java.lang.Object]
      toStringWithType[class java.lang.StringBuilder]
      toString[class java.lang.StringBuilder]
  URealValue
      compareTo[class java.lang.Object]
      equals[class java.lang.Object]
      toStringWithType[class java.lang.StringBuilder]
      toString[class java.lang.StringBuilder]
  UStringValue
      compareTo[class java.lang.Object]
      equals[class java.lang.Object]
      indexOf[class org.tzi.use.uml.ocl.value.StringValue]
      toStringWithType[class java.lang.StringBuilder]
      toString[class java.lang.StringBuilder]
distinct UOp keys in the inventory                       355
===================================================================
=== the metric, recomputed by hand ================================
operation            URealValue.neg()
receivers            24  (the URealValue corpus)
rows                 24
    UREAL(0.0,0.0)@URealValue  ->  UREAL(-0.0,0.0)@URealValue
    UREAL(0.0,1.0)@URealValue  ->  UREAL(-0.0,1.0)@URealValue
    UREAL(-0.0,0.0)@URealValue  ->  UREAL(0.0,0.0)@URealValue
    UREAL(1.0,0.0)@URealValue  ->  UREAL(-1.0,0.0)@URealValue
    UREAL(1.0,1.0)@URealValue  ->  UREAL(-1.0,1.0)@URealValue
    UREAL(-1.0,0.0)@URealValue  ->  UREAL(1.0,0.0)@URealValue
    UREAL(-1.0,1.0)@URealValue  ->  UREAL(1.0,1.0)@URealValue
    UREAL(-1.0,0.5)@URealValue  ->  UREAL(1.0,0.5)@URealValue
    UREAL(0.5,0.5)@URealValue  ->  UREAL(-0.5,0.5)@URealValue
    UREAL(-0.5,0.25)@URealValue  ->  UREAL(0.5,0.25)@URealValue
    UREAL(2.0,0.0)@URealValue  ->  UREAL(-2.0,0.0)@URealValue
    UREAL(100.0,0.001)@URealValue  ->  UREAL(-100.0,0.001)@URealValue
    UREAL(-100.0,0.001)@URealValue  ->  UREAL(100.0,0.001)@URealValue
    UREAL(4.9E-324,0.0)@URealValue  ->  UREAL(-4.9E-324,0.0)@URealValue
    UREAL(1.7976931348623157E308,0.0)@URealValue  ->  UREAL(-1.7976931348623157E308,0.0)@URealValue
    UREAL(-1.7976931348623157E308,0.0)@URealValue  ->  UREAL(1.7976931348623157E308,0.0)@URealValue
    UREAL(NaN,0.0)@URealValue  ->  UREAL(NaN,0.0)@URealValue
    UREAL(Infinity,0.0)@URealValue  ->  UREAL(-Infinity,0.0)@URealValue
    UREAL(-Infinity,0.0)@URealValue  ->  UREAL(Infinity,0.0)@URealValue
    UREAL(1.0,NaN)@URealValue  ->  UREAL(-1.0,NaN)@URealValue
    UREAL(1.0,Infinity)@URealValue  ->  UREAL(-1.0,Infinity)@URealValue
    UREAL(1.0,-1.0)@URealValue  ->  UREAL(-1.0,1.0)@URealValue
    UREAL(-46.064505,0.782649)@URealValue  ->  UREAL(46.064505,0.782649)@URealValue
    UREAL(28.230986,0.91554)@URealValue  ->  UREAL(-28.230986,0.91554)@URealValue
by hand              23  [UREAL(-0.0,0.0)@URealValue, UREAL(-0.0,1.0)@URealValue, UREAL(-0.5,0.5)@URealValue, UREAL(-1.0,0.0)@URealValue, UREAL(-1.0,1.0)@URealValue, UREAL(-1.0,Infinity)@URealValue, UREAL(-1.0,NaN)@URealValue, UREAL(-1.7976931348623157E308,0.0)@URealValue, UREAL(-100.0,0.001)@URealValue, UREAL(-2.0,0.0)@URealValue, UREAL(-28.230986,0.91554)@URealValue, UREAL(-4.9E-324,0.0)@URealValue, UREAL(-Infinity,0.0)@URealValue, UREAL(0.0,0.0)@URealValue, UREAL(0.5,0.25)@URealValue, UREAL(1.0,0.0)@URealValue, UREAL(1.0,0.5)@URealValue, UREAL(1.0,1.0)@URealValue, UREAL(1.7976931348623157E308,0.0)@URealValue, UREAL(100.0,0.001)@URealValue, UREAL(46.064505,0.782649)@URealValue, UREAL(Infinity,0.0)@URealValue, UREAL(NaN,0.0)@URealValue]
Result.referenceValues() 23  [UREAL(-0.0,0.0)@URealValue, UREAL(-0.0,1.0)@URealValue, UREAL(-0.5,0.5)@URealValue, UREAL(-1.0,0.0)@URealValue, UREAL(-1.0,1.0)@URealValue, UREAL(-1.0,Infinity)@URealValue, UREAL(-1.0,NaN)@URealValue, UREAL(-1.7976931348623157E308,0.0)@URealValue, UREAL(-100.0,0.001)@URealValue, UREAL(-2.0,0.0)@URealValue, UREAL(-28.230986,0.91554)@URealValue, UREAL(-4.9E-324,0.0)@URealValue, UREAL(-Infinity,0.0)@URealValue, UREAL(0.0,0.0)@URealValue, UREAL(0.5,0.25)@URealValue, UREAL(1.0,0.0)@URealValue, UREAL(1.0,0.5)@URealValue, UREAL(1.0,1.0)@URealValue, UREAL(1.7976931348623157E308,0.0)@URealValue, UREAL(100.0,0.001)@URealValue, UREAL(46.064505,0.782649)@URealValue, UREAL(Infinity,0.0)@URealValue, UREAL(NaN,0.0)@URealValue]
summary              URealValue.neg(): 24 rows, 24 measured, 23 distinct ref, AGREE=24
===================================================================
=== D-43: two readings of the same measurement ====================
  subject                              DIFFER     divOps   passes   typeMism notes ASSUMED
  P0-perfect                                0          0       74          0            0
  P12-boxed-primitive                       0          0       74       4105            0
  P13-factory-typed-adapter                 0          0       74       4105         4105
  P14-observing-adapter                     0          0       74          0            0
  stage passes the port with a DEFECT loses    0
  stage passes the port with NO defect loses   0   <- was 29 before round 8; the false-divergence mode
  operations carrying a java-type mismatch:
      P12 182   P13 182   P14 0
  first row of the ADAPTER's omission, which is AGREE and says so:
      0	BooleanValue.compareTo(value)	BOOLEAN(true)@BooleanValue | BOOLEAN(true)@BooleanValue	INTEGER(0)@Integer	INTEGER(0)@IntegerValue	AGREE	java type mismatch: reference returned java.lang.Integer (INTEGER(0)@Integer) / subject returned org.tzi.use.uml.ocl.value.IntegerValue (INTEGER(0)@IntegerValue); the content is IDENTICAL -- right content, wrong Java type (defect D-18). This row is scored AGREE and counted in rows.javaTypeMismatch, not scored as a divergence: at S1 the ported side's class cannot be authentically observed, because no ported value class exists to observe, so a type-only difference measures the adapter and not the port (D-43). Provenance: reference OBSERVED, subject ASSUMED (OBSERVED = read off the object that side returned; ASSUMED = the factory default for the kind, which is wrong for 182 of 285 operations). The subject's adapter never looked at what its implementation returned, so this difference is a finding about the ADAPTER and not about the port (D-43); an adapter must attribute through UValue.observedFrom(Object).
  first row of the PORT's real wrong class, same figure, different note:
      0	BooleanValue.compareTo(value)	BOOLEAN(true)@BooleanValue | BOOLEAN(true)@BooleanValue	INTEGER(0)@Integer	INTEGER(0)@IntegerValue	AGREE	java type mismatch: reference returned java.lang.Integer (INTEGER(0)@Integer) / subject returned org.tzi.use.uml.ocl.value.IntegerValue (INTEGER(0)@IntegerValue); the content is IDENTICAL -- right content, wrong Java type (defect D-18). This row is scored AGREE and counted in rows.javaTypeMismatch, not scored as a divergence: at S1 the ported side's class cannot be authentically observed, because no ported value class exists to observe, so a type-only difference measures the adapter and not the port (D-43). Provenance: reference OBSERVED, subject OBSERVED (OBSERVED = read off the object that side returned; ASSUMED = the factory default for the kind, which is wrong for 182 of 285 operations). Whether the object the subject observed is the one its implementation returned is not checkable by this harness.
===================================================================
=== D-18: right content, wrong Java type =========================
operations           355
control  rows        23963, measured 21556, agreed 21556  {AGREE=21556, BOTH_THREW=1243, HARNESS_ERROR=1063, UNMEASURABLE=101}
boxed    rows        23963, measured 21556, agreed 21556  {AGREE=21556, BOTH_THREW=1243, HARNESS_ERROR=1063, UNMEASURABLE=101}
control DIFFER+MIXED 0   <- MUST be 0
control javaTypeMismatch 0   <- MUST be 0
boxed   DIFFER rows  0   <- 0 since round 8: a type-only difference is not a divergence
boxed   javaTypeMismatch rows 4105   <- where the finding lives now
MEASURED on          182 of 355 operations
stage passes         control 74 -> boxed 74; lost 0: []
  a sample of the rows, which still SHOW both classes:
  index	operation	inputs	historical	ported	verdict	note
  0	BooleanValue.compareTo(value)	BOOLEAN(true)@BooleanValue | BOOLEAN(true)@BooleanValue	INTEGER(0)@Integer	INTEGER(0)@IntegerValue	AGREE	java type mismatch: reference returned java.lang.Integer (INTEGER(0)@Integer) / subject returned org.tzi.use.uml.ocl.value.IntegerValue (INTEGER(0)@IntegerValue); the content is IDENTICAL -- right content, wrong Java type (defect D-18). This row is scored AGREE and counted in rows.javaTypeMismatch, not scored as a divergence: at S1 the ported side's class cannot be authentically observed, because no ported value class exists to observe, so a type-only difference measures the adapter and not the port (D-43). Provenance: reference OBSERVED, subject OBSERVED (OBSERVED = read off the object that side returned; ASSUMED = the factory default for the kind, which is wrong for 182 of 285 operations). Whether the object the subject observed is the one its implementation returned is not checkable by this harness.
  1	BooleanValue.compareTo(value)	BOOLEAN(true)@BooleanValue | BOOLEAN(false)@BooleanValue	INTEGER(1)@Integer	INTEGER(1)@IntegerValue	AGREE	java type mismatch: reference returned java.lang.Integer (INTEGER(1)@Integer) / subject returned org.tzi.use.uml.ocl.value.IntegerValue (INTEGER(1)@IntegerValue); the content is IDENTICAL -- right content, wrong Java type (defect D-18). This row is scored AGREE and counted in rows.javaTypeMismatch, not scored as a divergence: at S1 the ported side's class cannot be authentically observed, because no ported value class exists to observe, so a type-only difference measures the adapter and not the port (D-43). Provenance: reference OBSERVED, subject OBSERVED (OBSERVED = read off the object that side returned; ASSUMED = the factory default for the kind, which is wrong for 182 of 285 operations). Whether the object the subject observed is the one its implementation returned is not checkable by this harness.
  2	BooleanValue.compareTo(value)	BOOLEAN(false)@BooleanValue | BOOLEAN(true)@BooleanValue	INTEGER(-1)@Integer	INTEGER(-1)@IntegerValue	AGREE	java type mismatch: reference returned java.lang.Integer (INTEGER(-1)@Integer) / subject returned org.tzi.use.uml.ocl.value.IntegerValue (INTEGER(-1)@IntegerValue); the content is IDENTICAL -- right content, wrong Java type (defect D-18). This row is scored AGREE and counted in rows.javaTypeMismatch, not scored as a divergence: at S1 the ported side's class cannot be authentically observed, because no ported value class exists to observe, so a type-only difference measures the adapter and not the port (D-43). Provenance: reference OBSERVED, subject OBSERVED (OBSERVED = read off the object that side returned; ASSUMED = the factory default for the kind, which is wrong for 182 of 285 operations). Whether the object the subject observed is the one its implementation returned is not checkable by this harness.
  3	BooleanValue.compareTo(value)	BOOLEAN(false)@BooleanValue | BOOLEAN(false)@BooleanValue	INTEGER(0)@Integer	INTEGER(0)@IntegerValue	AGREE	java type mismatch: reference returned java.lang.Integer (INTEGER(0)@Integer) / subject returned org.tzi.use.uml.ocl.value.IntegerValue (INTEGER(0)@IntegerValue); the content is IDENTICAL -- right content, wrong Java type (defect D-18). This row is scored AGREE and counted in rows.javaTypeMismatch, not scored as a divergence: at S1 the ported side's class cannot be authentically observed, because no ported value class exists to observe, so a type-only difference measures the adapter and not the port (D-43). Provenance: reference OBSERVED, subject OBSERVED (OBSERVED = read off the object that side returned; ASSUMED = the factory default for the kind, which is wrong for 182 of 285 operations). Whether the object the subject observed is the one its implementation returned is not checkable by this harness.
  0	BooleanValue.hashCode()	BOOLEAN(true)@BooleanValue	INTEGER(1231)@Integer	INTEGER(1231)@IntegerValue	AGREE	java type mismatch: reference returned java.lang.Integer (INTEGER(1231)@Integer) / subject returned org.tzi.use.uml.ocl.value.IntegerValue (INTEGER(1231)@IntegerValue); the content is IDENTICAL -- right content, wrong Java type (defect D-18). This row is scored AGREE and counted in rows.javaTypeMismatch, not scored as a divergence: at S1 the ported side's class cannot be authentically observed, because no ported value class exists to observe, so a type-only difference measures the adapter and not the port (D-43). Provenance: reference OBSERVED, subject OBSERVED (OBSERVED = read off the object that side returned; ASSUMED = the factory default for the kind, which is wrong for 182 of 285 operations). Whether the object the subject observed is the one its implementation returned is not checkable by this harness.
  1	BooleanValue.hashCode()	BOOLEAN(false)@BooleanValue	INTEGER(1237)@Integer	INTEGER(1237)@IntegerValue	AGREE	java type mismatch: reference returned java.lang.Integer (INTEGER(1237)@Integer) / subject returned org.tzi.use.uml.ocl.value.IntegerValue (INTEGER(1237)@IntegerValue); the content is IDENTICAL -- right content, wrong Java type (defect D-18). This row is scored AGREE and counted in rows.javaTypeMismatch, not scored as a divergence: at S1 the ported side's class cannot be authentically observed, because no ported value class exists to observe, so a type-only difference measures the adapter and not the port (D-43). Provenance: reference OBSERVED, subject OBSERVED (OBSERVED = read off the object that side returned; ASSUMED = the factory default for the kind, which is wrong for 182 of 285 operations). Whether the object the subject observed is the one its implementation returned is not checkable by this harness.
=================================================================
[INFO] Tests run: 7, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 4.761 s -- in Detection power: subtle infidelities in a ported U-type
[INFO] Running Uncertainty differential smoke
=== S1 differential smoke =========================================
seed                 20260817
reference            historical  /home/xoruser/msc-4/use-msc2026/use-core/target/test-classes/historical/use.jar
subject              stub-faithful
sha256 use.jar  80ac8ae433b8345677472019991356950f094f4a104cfbce1f75783a7308788d
sha256 atenearesearchgroup.uncertainty.jar  53b2a43feb0a0a39844a60278dd80a7d4b975ef324fb05c6db28831e835e59d0
corpus size          28  (22 boundary + 6 random)
rows                 784
measured             784
tally                URealValue.add(value): 784 rows, 784 measured, 258 distinct ref, AGREE=784
--- first 12 rows -------------------------------------------------
index	operation	inputs	historical	ported	verdict	note
0	URealValue.add(value)	UREAL(0.0,0.0)@URealValue | UREAL(0.0,0.0)@URealValue	UREAL(0.0,0.0)@URealValue	UREAL(0.0,0.0)@URealValue	AGREE	
1	URealValue.add(value)	UREAL(0.0,0.0)@URealValue | UREAL(0.0,1.0)@URealValue	UREAL(0.0,1.0)@URealValue	UREAL(0.0,1.0)@URealValue	AGREE	
2	URealValue.add(value)	UREAL(0.0,0.0)@URealValue | UREAL(-0.0,0.0)@URealValue	UREAL(0.0,0.0)@URealValue	UREAL(0.0,0.0)@URealValue	AGREE	
3	URealValue.add(value)	UREAL(0.0,0.0)@URealValue | UREAL(1.0,0.0)@URealValue	UREAL(1.0,0.0)@URealValue	UREAL(1.0,0.0)@URealValue	AGREE	
4	URealValue.add(value)	UREAL(0.0,0.0)@URealValue | UREAL(1.0,1.0)@URealValue	UREAL(1.0,1.0)@URealValue	UREAL(1.0,1.0)@URealValue	AGREE	
5	URealValue.add(value)	UREAL(0.0,0.0)@URealValue | UREAL(-1.0,0.0)@URealValue	UREAL(-1.0,0.0)@URealValue	UREAL(-1.0,0.0)@URealValue	AGREE	
6	URealValue.add(value)	UREAL(0.0,0.0)@URealValue | UREAL(-1.0,1.0)@URealValue	UREAL(-1.0,1.0)@URealValue	UREAL(-1.0,1.0)@URealValue	AGREE	
7	URealValue.add(value)	UREAL(0.0,0.0)@URealValue | UREAL(-1.0,0.5)@URealValue	UREAL(-1.0,0.5)@URealValue	UREAL(-1.0,0.5)@URealValue	AGREE	
8	URealValue.add(value)	UREAL(0.0,0.0)@URealValue | UREAL(0.5,0.5)@URealValue	UREAL(0.5,0.5)@URealValue	UREAL(0.5,0.5)@URealValue	AGREE	
9	URealValue.add(value)	UREAL(0.0,0.0)@URealValue | UREAL(-0.5,0.25)@URealValue	UREAL(-0.5,0.25)@URealValue	UREAL(-0.5,0.25)@URealValue	AGREE	
10	URealValue.add(value)	UREAL(0.0,0.0)@URealValue | UREAL(2.0,0.0)@URealValue	UREAL(2.0,0.0)@URealValue	UREAL(2.0,0.0)@URealValue	AGREE	
11	URealValue.add(value)	UREAL(0.0,0.0)@URealValue | UREAL(100.0,0.001)@URealValue	UREAL(100.0,0.001)@URealValue	UREAL(100.0,0.001)@URealValue	AGREE	
report               /home/xoruser/msc-4/use-msc2026/use-core/target/differential/s1-smoke-ureal-add.tsv
golden (matched)     /home/xoruser/msc-4/use-msc2026/docs/port2/differential/s1-smoke-ureal-add.tsv
isClean()            true   <- measured, NOT the pass criterion (D-36)
stage gate failures  []
javaTypeMismatch     0   <- clause 6 (D-43); the gate does not make it, a stage must
STAGE STATEMENT      URealValue.add(value): 784 rows, 784 measured, 784 agreed, 0 disagreed, 0 intended departure(s), 0 java-type mismatch(es) (subject token OBSERVED on 0, ASSUMED on 0), 258 distinct reference value(s) [DISCRIMINATING]
===================================================================
=== S1 fault-injection check ======================================
seed                 20260817
subject              stub-faulty-minus  (minus uses |ua-ub|)
rows                 784
measured             784
tally                URealValue.minus(value): 784 rows, 784 measured, 389 distinct ref, AGREE=558, DIFFER=226
--- first 5 disagreements -----------------------------------------
index	operation	inputs	historical	ported	verdict	note
29	URealValue.minus(value)	UREAL(0.0,1.0)@URealValue | UREAL(0.0,1.0)@URealValue	UREAL(0.0,1.4142135623730951)@URealValue	UREAL(0.0,0.0)@URealValue	DIFFER	
32	URealValue.minus(value)	UREAL(0.0,1.0)@URealValue | UREAL(1.0,1.0)@URealValue	UREAL(-1.0,1.4142135623730951)@URealValue	UREAL(-1.0,0.0)@URealValue	DIFFER	
34	URealValue.minus(value)	UREAL(0.0,1.0)@URealValue | UREAL(-1.0,1.0)@URealValue	UREAL(1.0,1.4142135623730951)@URealValue	UREAL(1.0,0.0)@URealValue	DIFFER	
35	URealValue.minus(value)	UREAL(0.0,1.0)@URealValue | UREAL(-1.0,0.5)@URealValue	UREAL(1.0,1.118033988749895)@URealValue	UREAL(1.0,0.5)@URealValue	DIFFER	
36	URealValue.minus(value)	UREAL(0.0,1.0)@URealValue | UREAL(0.5,0.5)@URealValue	UREAL(-0.5,1.118033988749895)@URealValue	UREAL(-0.5,0.5)@URealValue	DIFFER	
report               /home/xoruser/msc-4/use-msc2026/use-core/target/differential/s1-smoke-ureal-minus-faulty.tsv
golden (matched)     /home/xoruser/msc-4/use-msc2026/docs/port2/differential/s1-smoke-ureal-minus-faulty.tsv
===================================================================
refused              sweep of URealValue.minus(value) is not a stage pass: - 226 row(s) did not agree. tally: URealValue.minus(value): 784 rows, 784 measured, 389 distinct ref, AGREE=558, DIFFER=226
[INFO] Tests run: 6, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.070 s -- in Uncertainty differential smoke
[INFO] Running Unwritten-port invariant
=== D-15: the constant-literal subject, stage-shaped ==============
seed                       20260817
corpora                    uReal=24, uInteger=15, uBoolean=11, uString=28, boolean=4, string=16, zeroDivisors=7, indexBoundaries=8; receivers=104
operations                 355
literals the subject holds 276  (one per operation the reference ever answered with a value)
codomain census            355 operations: 79 measured nothing, 158 single-valued (NOT DISCRIMINATING), 118 discriminating
isClean() AND degenerate   119   <- the size of the door: a stage asserting isClean() reads these as PASS
refused by the stage gate  119 of 119
stage passes (must be 0)   0
--- operations that measured NOTHING (79) -------------------------
  ... BooleanValue.setTypeToRuntimeType()  BooleanValue.setTypeToRuntimeType(): 2 rows, 0 measured, 0 distinct ref, MIXED=2
  ... IntegerValue.setTypeToRuntimeType()  IntegerValue.setTypeToRuntimeType(): 8 rows, 0 measured, 0 distinct ref, MIXED=8
  ... RealValue.setTypeToRuntimeType()  RealValue.setTypeToRuntimeType(): 1 rows, 0 measured, 0 distinct ref, MIXED=1
  ... SBooleanValue.aleatoryCumulativeBeliefFusion(value)  SBooleanValue.aleatoryCumulativeBeliefFusion(value): 0 rows, 0 measured, 0 distinct ref
  ... SBooleanValue.and(value)  SBooleanValue.and(value): 0 rows, 0 measured, 0 distinct ref
  ... SBooleanValue.applyOn(value)  SBooleanValue.applyOn(value): 0 rows, 0 measured, 0 distinct ref
  ... SBooleanValue.averageBeliefFusion(value)  SBooleanValue.averageBeliefFusion(value): 0 rows, 0 measured, 0 distinct ref
  ... SBooleanValue.averageFusion(value)  SBooleanValue.averageFusion(value): 0 rows, 0 measured, 0 distinct ref
  ... SBooleanValue.baseRate()  SBooleanValue.baseRate(): 0 rows, 0 measured, 0 distinct ref
  ... SBooleanValue.belief()  SBooleanValue.belief(): 0 rows, 0 measured, 0 distinct ref
  ... SBooleanValue.beliefConstraintFusion(value)  SBooleanValue.beliefConstraintFusion(value): 0 rows, 0 measured, 0 distinct ref
  ... SBooleanValue.certainty()  SBooleanValue.certainty(): 0 rows, 0 measured, 0 distinct ref
  ... SBooleanValue.compareTo(value)  SBooleanValue.compareTo(value): 0 rows, 0 measured, 0 distinct ref
  ... SBooleanValue.conjunctiveCertainty(value)  SBooleanValue.conjunctiveCertainty(value): 0 rows, 0 measured, 0 distinct ref
  ... SBooleanValue.consensusAndCompromiseFusion(value)  SBooleanValue.consensusAndCompromiseFusion(value): 0 rows, 0 measured, 0 distinct ref
  ... SBooleanValue.cumulativeFusion(value)  SBooleanValue.cumulativeFusion(value): 0 rows, 0 measured, 0 distinct ref
  ... SBooleanValue.deduceY(value,value)  SBooleanValue.deduceY(value,value): 0 rows, 0 measured, 0 distinct ref
  ... SBooleanValue.degreeOfConflict(value)  SBooleanValue.degreeOfConflict(value): 0 rows, 0 measured, 0 distinct ref
  ... SBooleanValue.disbelief()  SBooleanValue.disbelief(): 0 rows, 0 measured, 0 distinct ref
  ... SBooleanValue.discount(value)  SBooleanValue.discount(value): 0 rows, 0 measured, 0 distinct ref
  ... SBooleanValue.epistemicCumulativeBeliefFusion(value)  SBooleanValue.epistemicCumulativeBeliefFusion(value): 0 rows, 0 measured, 0 distinct ref
  ... SBooleanValue.epistemicCumulativeFusion(value)  SBooleanValue.epistemicCumulativeFusion(value): 0 rows, 0 measured, 0 distinct ref
  ... SBooleanValue.equivalent(value)  SBooleanValue.equivalent(value): 0 rows, 0 measured, 0 distinct ref
  ... SBooleanValue.getRelativeWeight()  SBooleanValue.getRelativeWeight(): 0 rows, 0 measured, 0 distinct ref
  ... SBooleanValue.getRuntimeType()  SBooleanValue.getRuntimeType(): 0 rows, 0 measured, 0 distinct ref
  ... SBooleanValue.hashCode()  SBooleanValue.hashCode(): 0 rows, 0 measured, 0 distinct ref
  ... SBooleanValue.implies(value)  SBooleanValue.implies(value): 0 rows, 0 measured, 0 distinct ref
  ... SBooleanValue.isAbsolute()  SBooleanValue.isAbsolute(): 0 rows, 0 measured, 0 distinct ref
  ... SBooleanValue.isBag()  SBooleanValue.isBag(): 0 rows, 0 measured, 0 distinct ref
  ... SBooleanValue.isBoolean()  SBooleanValue.isBoolean(): 0 rows, 0 measured, 0 distinct ref
  ... SBooleanValue.isCertain(value)  SBooleanValue.isCertain(value): 0 rows, 0 measured, 0 distinct ref
  ... SBooleanValue.isCollection()  SBooleanValue.isCollection(): 0 rows, 0 measured, 0 distinct ref
  ... SBooleanValue.isDefined()  SBooleanValue.isDefined(): 0 rows, 0 measured, 0 distinct ref
  ... SBooleanValue.isDogmatic()  SBooleanValue.isDogmatic(): 0 rows, 0 measured, 0 distinct ref
  ... SBooleanValue.isInteger()  SBooleanValue.isInteger(): 0 rows, 0 measured, 0 distinct ref
  ... SBooleanValue.isLink()  SBooleanValue.isLink(): 0 rows, 0 measured, 0 distinct ref
  ... SBooleanValue.isMaximizedUncertainty()  SBooleanValue.isMaximizedUncertainty(): 0 rows, 0 measured, 0 distinct ref
  ... SBooleanValue.isObject()  SBooleanValue.isObject(): 0 rows, 0 measured, 0 distinct ref
  ... SBooleanValue.isOrderedSet()  SBooleanValue.isOrderedSet(): 0 rows, 0 measured, 0 distinct ref
  ... SBooleanValue.isReal()  SBooleanValue.isReal(): 0 rows, 0 measured, 0 distinct ref
  ... SBooleanValue.isSBoolean()  SBooleanValue.isSBoolean(): 0 rows, 0 measured, 0 distinct ref
  ... SBooleanValue.isSequence()  SBooleanValue.isSequence(): 0 rows, 0 measured, 0 distinct ref
  ... SBooleanValue.isSet()  SBooleanValue.isSet(): 0 rows, 0 measured, 0 distinct ref
  ... SBooleanValue.isUBoolean()  SBooleanValue.isUBoolean(): 0 rows, 0 measured, 0 distinct ref
  ... SBooleanValue.isUInteger()  SBooleanValue.isUInteger(): 0 rows, 0 measured, 0 distinct ref
  ... SBooleanValue.isUReal()  SBooleanValue.isUReal(): 0 rows, 0 measured, 0 distinct ref
  ... SBooleanValue.isUncertain(value)  SBooleanValue.isUncertain(value): 0 rows, 0 measured, 0 distinct ref
  ... SBooleanValue.isUndefined()  SBooleanValue.isUndefined(): 0 rows, 0 measured, 0 distinct ref
  ... SBooleanValue.isUnlimitedNatural()  SBooleanValue.isUnlimitedNatural(): 0 rows, 0 measured, 0 distinct ref
  ... SBooleanValue.isVacuous()  SBooleanValue.isVacuous(): 0 rows, 0 measured, 0 distinct ref
  ... SBooleanValue.majorityBeliefFusion(value)  SBooleanValue.majorityBeliefFusion(value): 0 rows, 0 measured, 0 distinct ref
  ... SBooleanValue.majorityFusion(value)  SBooleanValue.majorityFusion(value): 0 rows, 0 measured, 0 distinct ref
  ... SBooleanValue.max(value)  SBooleanValue.max(value): 0 rows, 0 measured, 0 distinct ref
  ... SBooleanValue.min(value)  SBooleanValue.min(value): 0 rows, 0 measured, 0 distinct ref
  ... SBooleanValue.minimumBeliefFusion(value)  SBooleanValue.minimumBeliefFusion(value): 0 rows, 0 measured, 0 distinct ref
  ... SBooleanValue.minimumFusion(value)  SBooleanValue.minimumFusion(value): 0 rows, 0 measured, 0 distinct ref
  ... SBooleanValue.not()  SBooleanValue.not(): 0 rows, 0 measured, 0 distinct ref
  ... SBooleanValue.or(value)  SBooleanValue.or(value): 0 rows, 0 measured, 0 distinct ref
  ... SBooleanValue.projection()  SBooleanValue.projection(): 0 rows, 0 measured, 0 distinct ref
  ... SBooleanValue.projectiveDistance(value)  SBooleanValue.projectiveDistance(value): 0 rows, 0 measured, 0 distinct ref
  ... SBooleanValue.setTypeToRuntimeType()  SBooleanValue.setTypeToRuntimeType(): 0 rows, 0 measured, 0 distinct ref
  ... SBooleanValue.toString()  SBooleanValue.toString(): 0 rows, 0 measured, 0 distinct ref
  ... SBooleanValue.toStringWithType()  SBooleanValue.toStringWithType(): 0 rows, 0 measured, 0 distinct ref
  ... SBooleanValue.toUBoolean()  SBooleanValue.toUBoolean(): 0 rows, 0 measured, 0 distinct ref
  ... SBooleanValue.type()  SBooleanValue.type(): 0 rows, 0 measured, 0 distinct ref
  ... SBooleanValue.uDistinct(value)  SBooleanValue.uDistinct(value): 0 rows, 0 measured, 0 distinct ref
  ... SBooleanValue.uEquals(value)  SBooleanValue.uEquals(value): 0 rows, 0 measured, 0 distinct ref
  ... SBooleanValue.uncertainOpinion()  SBooleanValue.uncertainOpinion(): 0 rows, 0 measured, 0 distinct ref
  ... SBooleanValue.uncertainty()  SBooleanValue.uncertainty(): 0 rows, 0 measured, 0 distinct ref
  ... SBooleanValue.uncertaintyMaximized()  SBooleanValue.uncertaintyMaximized(): 0 rows, 0 measured, 0 distinct ref
  ... SBooleanValue.weightedBeliefFusion(value)  SBooleanValue.weightedBeliefFusion(value): 0 rows, 0 measured, 0 distinct ref
  ... SBooleanValue.weightedFusion(value)  SBooleanValue.weightedFusion(value): 0 rows, 0 measured, 0 distinct ref
  ... SBooleanValue.xor(value)  SBooleanValue.xor(value): 0 rows, 0 measured, 0 distinct ref
  ... StringValue.setTypeToRuntimeType()  StringValue.setTypeToRuntimeType(): 15 rows, 0 measured, 0 distinct ref, MIXED=15
  ... UBooleanValue.setTypeToRuntimeType()  UBooleanValue.setTypeToRuntimeType(): 11 rows, 0 measured, 0 distinct ref, MIXED=9, HARNESS_ERROR=2
  ... UIntegerValue.power(value)  UIntegerValue.power(value): 225 rows, 0 measured, 0 distinct ref, BOTH_THREW=225
  ... UIntegerValue.setTypeToRuntimeType()  UIntegerValue.setTypeToRuntimeType(): 15 rows, 0 measured, 0 distinct ref, MIXED=15
  ... URealValue.setTypeToRuntimeType()  URealValue.setTypeToRuntimeType(): 24 rows, 0 measured, 0 distinct ref, MIXED=24
  ... UStringValue.setTypeToRuntimeType()  UStringValue.setTypeToRuntimeType(): 28 rows, 0 measured, 0 distinct ref, MIXED=27, HARNESS_ERROR=1
--- first 20 clean-but-degenerate operations ------------------
  --- BooleanValue.getRuntimeType(): 2 rows, 2 measured, 2 agreed, 0 disagreed, 0 intended departure(s), 0 java-type mismatch(es) (subject token OBSERVED on 0, ASSUMED on 0), 1 distinct reference value(s) [NOT DISCRIMINATING: always OPAQUE("org.tzi.use.uml.ocl.type.BooleanType|BooleanType{BasicType.fTypename=\"Boolean\"}")@BooleanType]
  --- BooleanValue.isBag(): 2 rows, 2 measured, 2 agreed, 0 disagreed, 0 intended departure(s), 0 java-type mismatch(es) (subject token OBSERVED on 0, ASSUMED on 0), 1 distinct reference value(s) [NOT DISCRIMINATING: always BOOLEAN(false)@Boolean]
  --- BooleanValue.isBoolean(): 2 rows, 2 measured, 2 agreed, 0 disagreed, 0 intended departure(s), 0 java-type mismatch(es) (subject token OBSERVED on 0, ASSUMED on 0), 1 distinct reference value(s) [NOT DISCRIMINATING: always BOOLEAN(true)@Boolean]
  --- BooleanValue.isCollection(): 2 rows, 2 measured, 2 agreed, 0 disagreed, 0 intended departure(s), 0 java-type mismatch(es) (subject token OBSERVED on 0, ASSUMED on 0), 1 distinct reference value(s) [NOT DISCRIMINATING: always BOOLEAN(false)@Boolean]
  --- BooleanValue.isDefined(): 2 rows, 2 measured, 2 agreed, 0 disagreed, 0 intended departure(s), 0 java-type mismatch(es) (subject token OBSERVED on 0, ASSUMED on 0), 1 distinct reference value(s) [NOT DISCRIMINATING: always BOOLEAN(true)@Boolean]
  --- BooleanValue.isInteger(): 2 rows, 2 measured, 2 agreed, 0 disagreed, 0 intended departure(s), 0 java-type mismatch(es) (subject token OBSERVED on 0, ASSUMED on 0), 1 distinct reference value(s) [NOT DISCRIMINATING: always BOOLEAN(false)@Boolean]
  --- BooleanValue.isLink(): 2 rows, 2 measured, 2 agreed, 0 disagreed, 0 intended departure(s), 0 java-type mismatch(es) (subject token OBSERVED on 0, ASSUMED on 0), 1 distinct reference value(s) [NOT DISCRIMINATING: always BOOLEAN(false)@Boolean]
  --- BooleanValue.isObject(): 2 rows, 2 measured, 2 agreed, 0 disagreed, 0 intended departure(s), 0 java-type mismatch(es) (subject token OBSERVED on 0, ASSUMED on 0), 1 distinct reference value(s) [NOT DISCRIMINATING: always BOOLEAN(false)@Boolean]
  --- BooleanValue.isOrderedSet(): 2 rows, 2 measured, 2 agreed, 0 disagreed, 0 intended departure(s), 0 java-type mismatch(es) (subject token OBSERVED on 0, ASSUMED on 0), 1 distinct reference value(s) [NOT DISCRIMINATING: always BOOLEAN(false)@Boolean]
  --- BooleanValue.isReal(): 2 rows, 2 measured, 2 agreed, 0 disagreed, 0 intended departure(s), 0 java-type mismatch(es) (subject token OBSERVED on 0, ASSUMED on 0), 1 distinct reference value(s) [NOT DISCRIMINATING: always BOOLEAN(false)@Boolean]
  --- BooleanValue.isSBoolean(): 2 rows, 2 measured, 2 agreed, 0 disagreed, 0 intended departure(s), 0 java-type mismatch(es) (subject token OBSERVED on 0, ASSUMED on 0), 1 distinct reference value(s) [NOT DISCRIMINATING: always BOOLEAN(false)@Boolean]
  --- BooleanValue.isSequence(): 2 rows, 2 measured, 2 agreed, 0 disagreed, 0 intended departure(s), 0 java-type mismatch(es) (subject token OBSERVED on 0, ASSUMED on 0), 1 distinct reference value(s) [NOT DISCRIMINATING: always BOOLEAN(false)@Boolean]
  --- BooleanValue.isSet(): 2 rows, 2 measured, 2 agreed, 0 disagreed, 0 intended departure(s), 0 java-type mismatch(es) (subject token OBSERVED on 0, ASSUMED on 0), 1 distinct reference value(s) [NOT DISCRIMINATING: always BOOLEAN(false)@Boolean]
  --- BooleanValue.isUBoolean(): 2 rows, 2 measured, 2 agreed, 0 disagreed, 0 intended departure(s), 0 java-type mismatch(es) (subject token OBSERVED on 0, ASSUMED on 0), 1 distinct reference value(s) [NOT DISCRIMINATING: always BOOLEAN(false)@Boolean]
  --- BooleanValue.isUInteger(): 2 rows, 2 measured, 2 agreed, 0 disagreed, 0 intended departure(s), 0 java-type mismatch(es) (subject token OBSERVED on 0, ASSUMED on 0), 1 distinct reference value(s) [NOT DISCRIMINATING: always BOOLEAN(false)@Boolean]
  --- BooleanValue.isUReal(): 2 rows, 2 measured, 2 agreed, 0 disagreed, 0 intended departure(s), 0 java-type mismatch(es) (subject token OBSERVED on 0, ASSUMED on 0), 1 distinct reference value(s) [NOT DISCRIMINATING: always BOOLEAN(false)@Boolean]
  --- BooleanValue.isUndefined(): 2 rows, 2 measured, 2 agreed, 0 disagreed, 0 intended departure(s), 0 java-type mismatch(es) (subject token OBSERVED on 0, ASSUMED on 0), 1 distinct reference value(s) [NOT DISCRIMINATING: always BOOLEAN(false)@Boolean]
  --- BooleanValue.isUnlimitedNatural(): 2 rows, 2 measured, 2 agreed, 0 disagreed, 0 intended departure(s), 0 java-type mismatch(es) (subject token OBSERVED on 0, ASSUMED on 0), 1 distinct reference value(s) [NOT DISCRIMINATING: always BOOLEAN(false)@Boolean]
  --- BooleanValue.type(): 2 rows, 2 measured, 2 agreed, 0 disagreed, 0 intended departure(s), 0 java-type mismatch(es) (subject token OBSERVED on 0, ASSUMED on 0), 1 distinct reference value(s) [NOT DISCRIMINATING: always OPAQUE("org.tzi.use.uml.ocl.type.BooleanType|BooleanType{BasicType.fTypename=\"Boolean\"}")@BooleanType]
  --- IntegerValue.getRuntimeType(): 8 rows, 8 measured, 8 agreed, 0 disagreed, 0 intended departure(s), 0 java-type mismatch(es) (subject token OBSERVED on 0, ASSUMED on 0), 1 distinct reference value(s) [NOT DISCRIMINATING: always OPAQUE("org.tzi.use.uml.ocl.type.IntegerType|IntegerType{BasicType.fTypename=\"Integer\"}")@IntegerType]
===================================================================
CONTROL, faithful port     URealValue.add(value): 576 rows, 576 measured, 576 agreed, 0 disagreed, 0 intended departure(s), 0 java-type mismatch(es) (subject token OBSERVED on 0, ASSUMED on 0), 164 distinct reference value(s) [DISCRIMINATING]
=== unwritten-port invariant: a-throws =================
seed                 20260817
subject              a-throws  (every method body: throw new RuntimeException("TODO: port " + op.key()))
observability        NOTHING
javaTypeMismatch     0  <- agreement rows on which only the Java class differed (D-43)
operations           355  (enumerated from use.jar + atenearesearchgroup.uncertainty.jar)
corpora              uReal=24, uInteger=15, uBoolean=11, uString=28, boolean=4, string=16, zeroDivisors=7, indexBoundaries=8; receivers=104
rows                 1294072
measured rows        0  (AGREE + DIFFER)
agreement rows       0
verdict tally        {BOTH_THREW=50598, HARNESS_ERROR=1163146, MIXED=80328}
codomain census      355 operations: 355 measured nothing, 0 single-valued (NOT DISCRIMINATING), 0 discriminating
fully agreed ops, DISCRIMINATING (a finding about the subject)  (none)
fully agreed ops, NOT DISCRIMINATING (a finding about the corpus)  (none)   [ASSERTED against reviewedDegenerateFullyAgreed since the D-35 fix]
===================================================================
=== unwritten-port invariant: b-returns-java-null =================
seed                 20260817
subject              b-returns-java-null  (every method body: return null)
observability        NOTHING
javaTypeMismatch     0  <- agreement rows on which only the Java class differed (D-43)
operations           355  (enumerated from use.jar + atenearesearchgroup.uncertainty.jar)
corpora              uReal=24, uInteger=15, uBoolean=11, uString=28, boolean=4, string=16, zeroDivisors=7, indexBoundaries=8; receivers=104
rows                 1294072
measured rows        0  (AGREE + DIFFER)
agreement rows       0
verdict tally        {HARNESS_ERROR=1294072}
codomain census      355 operations: 355 measured nothing, 0 single-valued (NOT DISCRIMINATING), 0 discriminating
fully agreed ops, DISCRIMINATING (a finding about the subject)  (none)
fully agreed ops, NOT DISCRIMINATING (a finding about the corpus)  (none)   [ASSERTED against reviewedDegenerateFullyAgreed since the D-35 fix]
===================================================================
=== unwritten-port invariant: c-empty-body =================
seed                 20260817
subject              c-empty-body  (every method body: { } -- i.e. return UValue.voidValue())
observability        NOTHING
javaTypeMismatch     0  <- agreement rows on which only the Java class differed (D-43)
operations           355  (enumerated from use.jar + atenearesearchgroup.uncertainty.jar)
corpora              uReal=24, uInteger=15, uBoolean=11, uString=28, boolean=4, string=16, zeroDivisors=7, indexBoundaries=8; receivers=104
rows                 1294072
measured rows        79520  (AGREE + DIFFER)
agreement rows       0
verdict tally        {DIFFER=79520, HARNESS_ERROR=1163146, MIXED=50598, UNMEASURABLE=808}
codomain census      355 operations: 79 measured nothing, 157 single-valued (NOT DISCRIMINATING), 119 discriminating
fully agreed ops, DISCRIMINATING (a finding about the subject)  (none)
fully agreed ops, NOT DISCRIMINATING (a finding about the corpus)  (none)   [ASSERTED against reviewedDegenerateFullyAgreed since the D-35 fix]
===================================================================
=== unwritten-port invariant: d-returns-null-value =================
seed                 20260817
subject              d-returns-null-value  (every method body: return UValue.nullValue())
observability        NOTHING
javaTypeMismatch     0  <- agreement rows on which only the Java class differed (D-43)
operations           355  (enumerated from use.jar + atenearesearchgroup.uncertainty.jar)
corpora              uReal=24, uInteger=15, uBoolean=11, uString=28, boolean=4, string=16, zeroDivisors=7, indexBoundaries=8; receivers=104
rows                 1294072
measured rows        79520  (AGREE + DIFFER)
agreement rows       0
verdict tally        {DIFFER=79520, HARNESS_ERROR=1163146, MIXED=50598, UNMEASURABLE=808}
codomain census      355 operations: 79 measured nothing, 157 single-valued (NOT DISCRIMINATING), 119 discriminating
fully agreed ops, DISCRIMINATING (a finding about the subject)  (none)
fully agreed ops, NOT DISCRIMINATING (a finding about the corpus)  (none)   [ASSERTED against reviewedDegenerateFullyAgreed since the D-35 fix]
===================================================================
=== unwritten-port invariant: e-fixed-constant =================
seed                 20260817
subject              e-fixed-constant  (every method body: return UValue.uBoolean(true, 1.0))
observability        WRONG_VALUES
javaTypeMismatch     0  <- agreement rows on which only the Java class differed (D-43)
operations           355  (enumerated from use.jar + atenearesearchgroup.uncertainty.jar)
corpora              uReal=24, uInteger=15, uBoolean=11, uString=28, boolean=4, string=16, zeroDivisors=7, indexBoundaries=8; receivers=104
rows                 1294072
measured rows        80328  (AGREE + DIFFER)
agreement rows       9768
verdict tally        {AGREE=9768, DIFFER=70560, HARNESS_ERROR=1163146, MIXED=50598}
codomain census      355 operations: 71 measured nothing, 165 single-valued (NOT DISCRIMINATING), 119 discriminating
fully agreed ops, DISCRIMINATING (a finding about the subject)  (none)
fully agreed ops, NOT DISCRIMINATING (a finding about the corpus)  (none)   [ASSERTED against reviewedDegenerateFullyAgreed since the D-35 fix]
--- per-operation agreement tally (agreed/driven/rows) ------------
  10/990/11752	UBooleanValue.and(value)
  22/990/11752	UBooleanValue.equivalent(value)
  43/990/11752	UBooleanValue.implies(value)
  16/72/832	UBooleanValue.not()
  54/990/11752	UBooleanValue.or(value)
  889/990/11752	UBooleanValue.uDistinct(value)
  22/990/11752	UBooleanValue.uEquals(value)
  16/990/11752	UBooleanValue.xor(value)
  142/1650/11752	UIntegerValue.ge(value)
  106/1650/11752	UIntegerValue.gt(value)
  125/1650/11752	UIntegerValue.le(value)
  91/1650/11752	UIntegerValue.lt(value)
  1470/1650/11752	UIntegerValue.uDistinct(value)
  38/1650/11752	UIntegerValue.uEquals(value)
  388/2640/11752	URealValue.ge(value)
  318/2640/11752	URealValue.gt(value)
  474/2640/11752	URealValue.le(value)
  403/2640/11752	URealValue.lt(value)
  2413/2640/11752	URealValue.uDistinct(value)
  53/2640/11752	URealValue.uEquals(value)
  128/2970/11752	UStringValue.ge(value)
  129/2970/11752	UStringValue.gt(value)
  108/2970/11752	UStringValue.le(value)
  109/2970/11752	UStringValue.lt(value)
  24/216/832	UStringValue.toUBoolean()
  1915/2970/11752	UStringValue.uDistinct(value)
  131/2970/11752	UStringValue.uEquals(value)
  131/2970/11752	UStringValue.uEqualsIgnoreCase(value)
--- first 20 agreement rows -----------------------------------
index	operation	inputs	historical	ported	verdict	note
[corpus uBoolean] 453	UBooleanValue.and(value)	UBOOLEAN(true,1.0)@UBooleanValue | UBOOLEAN(true,1.0)@UBooleanValue	UBOOLEAN(true,1.0)@UBooleanValue	UBOOLEAN(true,1.0)@UBooleanValue	AGREE	
[corpus uBoolean] 454	UBooleanValue.and(value)	UBOOLEAN(true,1.0)@UBooleanValue | UBOOLEAN(false,0.0)@UBooleanValue	UBOOLEAN(true,1.0)@UBooleanValue	UBOOLEAN(true,1.0)@UBooleanValue	AGREE	
[corpus uBoolean] 464	UBooleanValue.and(value)	UBOOLEAN(false,0.0)@UBooleanValue | UBOOLEAN(true,1.0)@UBooleanValue	UBOOLEAN(true,1.0)@UBooleanValue	UBOOLEAN(true,1.0)@UBooleanValue	AGREE	
[corpus uBoolean] 465	UBooleanValue.and(value)	UBOOLEAN(false,0.0)@UBooleanValue | UBOOLEAN(false,0.0)@UBooleanValue	UBOOLEAN(true,1.0)@UBooleanValue	UBOOLEAN(true,1.0)@UBooleanValue	AGREE	
[corpus boolean] 164	UBooleanValue.and(value)	UBOOLEAN(true,1.0)@UBooleanValue | BOOLEAN(true)@BooleanValue	UBOOLEAN(true,1.0)@UBooleanValue	UBOOLEAN(true,1.0)@UBooleanValue	AGREE	
[corpus boolean] 166	UBooleanValue.and(value)	UBOOLEAN(true,1.0)@UBooleanValue | BOOLEAN(true)@BooleanValue	UBOOLEAN(true,1.0)@UBooleanValue	UBOOLEAN(true,1.0)@UBooleanValue	AGREE	
[corpus boolean] 167	UBooleanValue.and(value)	UBOOLEAN(true,1.0)@UBooleanValue | BOOLEAN(true)@BooleanValue	UBOOLEAN(true,1.0)@UBooleanValue	UBOOLEAN(true,1.0)@UBooleanValue	AGREE	
[corpus boolean] 168	UBooleanValue.and(value)	UBOOLEAN(false,0.0)@UBooleanValue | BOOLEAN(true)@BooleanValue	UBOOLEAN(true,1.0)@UBooleanValue	UBOOLEAN(true,1.0)@UBooleanValue	AGREE	
[corpus boolean] 170	UBooleanValue.and(value)	UBOOLEAN(false,0.0)@UBooleanValue | BOOLEAN(true)@BooleanValue	UBOOLEAN(true,1.0)@UBooleanValue	UBOOLEAN(true,1.0)@UBooleanValue	AGREE	
[corpus boolean] 171	UBooleanValue.and(value)	UBOOLEAN(false,0.0)@UBooleanValue | BOOLEAN(true)@BooleanValue	UBOOLEAN(true,1.0)@UBooleanValue	UBOOLEAN(true,1.0)@UBooleanValue	AGREE	
[corpus uBoolean] 429	UBooleanValue.equivalent(value)	UBOOLEAN(true,0.0)@UBooleanValue | UBOOLEAN(true,0.0)@UBooleanValue	UBOOLEAN(true,1.0)@UBooleanValue	UBOOLEAN(true,1.0)@UBooleanValue	AGREE	
[corpus uBoolean] 434	UBooleanValue.equivalent(value)	UBOOLEAN(true,0.0)@UBooleanValue | UBOOLEAN(false,1.0)@UBooleanValue	UBOOLEAN(true,1.0)@UBooleanValue	UBOOLEAN(true,1.0)@UBooleanValue	AGREE	
[corpus uBoolean] 441	UBooleanValue.equivalent(value)	UBOOLEAN(true,0.5)@UBooleanValue | UBOOLEAN(true,0.5)@UBooleanValue	UBOOLEAN(true,1.0)@UBooleanValue	UBOOLEAN(true,1.0)@UBooleanValue	AGREE	
[corpus uBoolean] 444	UBooleanValue.equivalent(value)	UBOOLEAN(true,0.5)@UBooleanValue | UBOOLEAN(false,0.5)@UBooleanValue	UBOOLEAN(true,1.0)@UBooleanValue	UBOOLEAN(true,1.0)@UBooleanValue	AGREE	
[corpus uBoolean] 453	UBooleanValue.equivalent(value)	UBOOLEAN(true,1.0)@UBooleanValue | UBOOLEAN(true,1.0)@UBooleanValue	UBOOLEAN(true,1.0)@UBooleanValue	UBOOLEAN(true,1.0)@UBooleanValue	AGREE	
[corpus uBoolean] 454	UBooleanValue.equivalent(value)	UBOOLEAN(true,1.0)@UBooleanValue | UBOOLEAN(false,0.0)@UBooleanValue	UBOOLEAN(true,1.0)@UBooleanValue	UBOOLEAN(true,1.0)@UBooleanValue	AGREE	
[corpus uBoolean] 464	UBooleanValue.equivalent(value)	UBOOLEAN(false,0.0)@UBooleanValue | UBOOLEAN(true,1.0)@UBooleanValue	UBOOLEAN(true,1.0)@UBooleanValue	UBOOLEAN(true,1.0)@UBooleanValue	AGREE	
[corpus uBoolean] 465	UBooleanValue.equivalent(value)	UBOOLEAN(false,0.0)@UBooleanValue | UBOOLEAN(false,0.0)@UBooleanValue	UBOOLEAN(true,1.0)@UBooleanValue	UBOOLEAN(true,1.0)@UBooleanValue	AGREE	
[corpus uBoolean] 474	UBooleanValue.equivalent(value)	UBOOLEAN(false,0.5)@UBooleanValue | UBOOLEAN(true,0.5)@UBooleanValue	UBOOLEAN(true,1.0)@UBooleanValue	UBOOLEAN(true,1.0)@UBooleanValue	AGREE	
[corpus uBoolean] 477	UBooleanValue.equivalent(value)	UBOOLEAN(false,0.5)@UBooleanValue | UBOOLEAN(false,0.5)@UBooleanValue	UBOOLEAN(true,1.0)@UBooleanValue	UBOOLEAN(true,1.0)@UBooleanValue	AGREE	
===================================================================
=== unwritten-port invariant: f-echoes-receiver =================
seed                 20260817
subject              f-echoes-receiver  (every method body: return args.get(0))
observability        WRONG_VALUES
javaTypeMismatch     384  <- agreement rows on which only the Java class differed (D-43)
operations           355  (enumerated from use.jar + atenearesearchgroup.uncertainty.jar)
corpora              uReal=24, uInteger=15, uBoolean=11, uString=28, boolean=4, string=16, zeroDivisors=7, indexBoundaries=8; receivers=104
rows                 1294072
measured rows        80328  (AGREE + DIFFER)
agreement rows       5143
verdict tally        {AGREE=5143, DIFFER=75185, HARNESS_ERROR=1163146, MIXED=50598}
codomain census      355 operations: 71 measured nothing, 165 single-valued (NOT DISCRIMINATING), 119 discriminating
fully agreed ops, DISCRIMINATING (a finding about the subject)  
  *** BooleanValue.isTrue()  (16/16 driven rows agreed, 16 of them on the payload only (java type mismatch), 832 rows total, 2 distinct reference value(s); reviewed and signed off)
  *** BooleanValue.value()  (16/16 driven rows agreed, 16 of them on the payload only (java type mismatch), 832 rows total, 2 distinct reference value(s); reviewed and signed off)
  *** IntegerValue.value()  (64/64 driven rows agreed, 64 of them on the payload only (java type mismatch), 832 rows total, 8 distinct reference value(s); reviewed and signed off)
  *** StringValue.value()  (120/120 driven rows agreed, 120 of them on the payload only (java type mismatch), 832 rows total, 15 distinct reference value(s); reviewed and signed off)
fully agreed ops, NOT DISCRIMINATING (a finding about the corpus)  1 operations   [ASSERTED against reviewedDegenerateFullyAgreed since the D-35 fix]
  --- RealValue.value()  (8/8 driven rows agreed, 8 of them on the payload only (java type mismatch), 832 rows total, 1 distinct reference value(s) -- always REAL(0.0)@Double [NOT DISCRIMINATING]; reviewed and signed off)
--- per-operation agreement tally (agreed/driven/rows) ------------
  8/16/832	BooleanValue.isBag()
  8/16/832	BooleanValue.isBoolean()
  8/16/832	BooleanValue.isCollection()
  8/16/832	BooleanValue.isDefined()
  8/16/832	BooleanValue.isInteger()
  8/16/832	BooleanValue.isLink()
  8/16/832	BooleanValue.isObject()
  8/16/832	BooleanValue.isOrderedSet()
  8/16/832	BooleanValue.isReal()
  8/16/832	BooleanValue.isSBoolean()
  8/16/832	BooleanValue.isSequence()
  8/16/832	BooleanValue.isSet()
  16/16/832	BooleanValue.isTrue()
  8/16/832	BooleanValue.isUBoolean()
  8/16/832	BooleanValue.isUInteger()
  8/16/832	BooleanValue.isUReal()
  8/16/832	BooleanValue.isUndefined()
  8/16/832	BooleanValue.isUnlimitedNatural()
  16/16/832	BooleanValue.value()
  16/880/11752	IntegerValue.compareTo(value)
  8/64/832	IntegerValue.hashCode()
  64/64/832	IntegerValue.value()
  8/8/832	RealValue.value()
  120/120/832	StringValue.value()
  40/990/11752	UBooleanValue.and(value)
  31/990/11752	UBooleanValue.equivalent(value)
  24/990/11752	UBooleanValue.implies(value)
  16/72/832	UBooleanValue.not()
  34/990/11752	UBooleanValue.or(value)
  124/990/11752	UBooleanValue.uDistinct(value)
  128/990/11752	UBooleanValue.uEquals(value)
  30/990/11752	UBooleanValue.xor(value)
  88/120/832	UIntegerValue.abs()
  58/1650/11752	UIntegerValue.add(value)
  47/1650/11752	UIntegerValue.divideBy(value)
  40/120/832	UIntegerValue.inverse()
  58/1650/11752	UIntegerValue.minus(value)
  46/1650/11752	UIntegerValue.mod(value)
  72/1650/11752	UIntegerValue.mult(value)
  24/120/832	UIntegerValue.neg()
  40/1650/11752	UIntegerValue.power(value)
  24/120/832	UIntegerValue.sqrt()
  112/192/832	URealValue.abs()
  308/2640/11752	URealValue.add(value)
  32/192/832	URealValue.asin()
  32/192/832	URealValue.atan()
  138/2640/11752	URealValue.divideBy(value)
  144/192/832	URealValue.floor()
  56/192/832	URealValue.inverse()
  718/2640/11752	URealValue.max(value)
  777/2640/11752	URealValue.min(value)
  313/2640/11752	URealValue.minus(value)
  132/2640/11752	URealValue.mult(value)
  8/192/832	URealValue.neg()
  378/1296/11752	URealValue.power(float)
  96/192/832	URealValue.round()
  32/192/832	URealValue.sin()
  40/192/832	URealValue.sqrt()
  32/192/832	URealValue.tan()
  60/1458/11752	UStringValue.uAt(int)
  75/2970/11752	UStringValue.uConcat(value)
  72/4374/35256	UStringValue.uSubstring(int,int)
  176/216/832	UStringValue.uToLowerCase()
  104/216/832	UStringValue.uToUpperCase()
--- first 20 agreement rows -----------------------------------
index	operation	inputs	historical	ported	verdict	note
[corpus uReal] 79	BooleanValue.isBag()	BOOLEAN(false)@BooleanValue	BOOLEAN(false)@Boolean	BOOLEAN(false)@BooleanValue	AGREE	java type mismatch: reference returned java.lang.Boolean (BOOLEAN(false)@Boolean) / subject returned org.tzi.use.uml.ocl.value.BooleanValue (BOOLEAN(false)@BooleanValue); the content is IDENTICAL -- right content, wrong Java type (defect D-18). This row is scored AGREE and counted in rows.javaTypeMismatch, not scored as a divergence: at S1 the ported side's class cannot be authentically observed, because no ported value class exists to observe, so a type-only difference measures the adapter and not the port (D-43). Provenance: reference OBSERVED, subject ASSUMED (OBSERVED = read off the object that side returned; ASSUMED = the factory default for the kind, which is wrong for 182 of 285 operations). The subject's adapter never looked at what its implementation returned, so this difference is a finding about the ADAPTER and not about the port (D-43); an adapter must attribute through UValue.observedFrom(Object).
[corpus uInteger] 79	BooleanValue.isBag()	BOOLEAN(false)@BooleanValue	BOOLEAN(false)@Boolean	BOOLEAN(false)@BooleanValue	AGREE	java type mismatch: reference returned java.lang.Boolean (BOOLEAN(false)@Boolean) / subject returned org.tzi.use.uml.ocl.value.BooleanValue (BOOLEAN(false)@BooleanValue); the content is IDENTICAL -- right content, wrong Java type (defect D-18). This row is scored AGREE and counted in rows.javaTypeMismatch, not scored as a divergence: at S1 the ported side's class cannot be authentically observed, because no ported value class exists to observe, so a type-only difference measures the adapter and not the port (D-43). Provenance: reference OBSERVED, subject ASSUMED (OBSERVED = read off the object that side returned; ASSUMED = the factory default for the kind, which is wrong for 182 of 285 operations). The subject's adapter never looked at what its implementation returned, so this difference is a finding about the ADAPTER and not about the port (D-43); an adapter must attribute through UValue.observedFrom(Object).
[corpus uBoolean] 79	BooleanValue.isBag()	BOOLEAN(false)@BooleanValue	BOOLEAN(false)@Boolean	BOOLEAN(false)@BooleanValue	AGREE	java type mismatch: reference returned java.lang.Boolean (BOOLEAN(false)@Boolean) / subject returned org.tzi.use.uml.ocl.value.BooleanValue (BOOLEAN(false)@BooleanValue); the content is IDENTICAL -- right content, wrong Java type (defect D-18). This row is scored AGREE and counted in rows.javaTypeMismatch, not scored as a divergence: at S1 the ported side's class cannot be authentically observed, because no ported value class exists to observe, so a type-only difference measures the adapter and not the port (D-43). Provenance: reference OBSERVED, subject ASSUMED (OBSERVED = read off the object that side returned; ASSUMED = the factory default for the kind, which is wrong for 182 of 285 operations). The subject's adapter never looked at what its implementation returned, so this difference is a finding about the ADAPTER and not about the port (D-43); an adapter must attribute through UValue.observedFrom(Object).
[corpus uString] 79	BooleanValue.isBag()	BOOLEAN(false)@BooleanValue	BOOLEAN(false)@Boolean	BOOLEAN(false)@BooleanValue	AGREE	java type mismatch: reference returned java.lang.Boolean (BOOLEAN(false)@Boolean) / subject returned org.tzi.use.uml.ocl.value.BooleanValue (BOOLEAN(false)@BooleanValue); the content is IDENTICAL -- right content, wrong Java type (defect D-18). This row is scored AGREE and counted in rows.javaTypeMismatch, not scored as a divergence: at S1 the ported side's class cannot be authentically observed, because no ported value class exists to observe, so a type-only difference measures the adapter and not the port (D-43). Provenance: reference OBSERVED, subject ASSUMED (OBSERVED = read off the object that side returned; ASSUMED = the factory default for the kind, which is wrong for 182 of 285 operations). The subject's adapter never looked at what its implementation returned, so this difference is a finding about the ADAPTER and not about the port (D-43); an adapter must attribute through UValue.observedFrom(Object).
[corpus boolean] 79	BooleanValue.isBag()	BOOLEAN(false)@BooleanValue	BOOLEAN(false)@Boolean	BOOLEAN(false)@BooleanValue	AGREE	java type mismatch: reference returned java.lang.Boolean (BOOLEAN(false)@Boolean) / subject returned org.tzi.use.uml.ocl.value.BooleanValue (BOOLEAN(false)@BooleanValue); the content is IDENTICAL -- right content, wrong Java type (defect D-18). This row is scored AGREE and counted in rows.javaTypeMismatch, not scored as a divergence: at S1 the ported side's class cannot be authentically observed, because no ported value class exists to observe, so a type-only difference measures the adapter and not the port (D-43). Provenance: reference OBSERVED, subject ASSUMED (OBSERVED = read off the object that side returned; ASSUMED = the factory default for the kind, which is wrong for 182 of 285 operations). The subject's adapter never looked at what its implementation returned, so this difference is a finding about the ADAPTER and not about the port (D-43); an adapter must attribute through UValue.observedFrom(Object).
[corpus string] 79	BooleanValue.isBag()	BOOLEAN(false)@BooleanValue	BOOLEAN(false)@Boolean	BOOLEAN(false)@BooleanValue	AGREE	java type mismatch: reference returned java.lang.Boolean (BOOLEAN(false)@Boolean) / subject returned org.tzi.use.uml.ocl.value.BooleanValue (BOOLEAN(false)@BooleanValue); the content is IDENTICAL -- right content, wrong Java type (defect D-18). This row is scored AGREE and counted in rows.javaTypeMismatch, not scored as a divergence: at S1 the ported side's class cannot be authentically observed, because no ported value class exists to observe, so a type-only difference measures the adapter and not the port (D-43). Provenance: reference OBSERVED, subject ASSUMED (OBSERVED = read off the object that side returned; ASSUMED = the factory default for the kind, which is wrong for 182 of 285 operations). The subject's adapter never looked at what its implementation returned, so this difference is a finding about the ADAPTER and not about the port (D-43); an adapter must attribute through UValue.observedFrom(Object).
[corpus zeroDivisors] 79	BooleanValue.isBag()	BOOLEAN(false)@BooleanValue	BOOLEAN(false)@Boolean	BOOLEAN(false)@BooleanValue	AGREE	java type mismatch: reference returned java.lang.Boolean (BOOLEAN(false)@Boolean) / subject returned org.tzi.use.uml.ocl.value.BooleanValue (BOOLEAN(false)@BooleanValue); the content is IDENTICAL -- right content, wrong Java type (defect D-18). This row is scored AGREE and counted in rows.javaTypeMismatch, not scored as a divergence: at S1 the ported side's class cannot be authentically observed, because no ported value class exists to observe, so a type-only difference measures the adapter and not the port (D-43). Provenance: reference OBSERVED, subject ASSUMED (OBSERVED = read off the object that side returned; ASSUMED = the factory default for the kind, which is wrong for 182 of 285 operations). The subject's adapter never looked at what its implementation returned, so this difference is a finding about the ADAPTER and not about the port (D-43); an adapter must attribute through UValue.observedFrom(Object).
[corpus indexBoundaries] 79	BooleanValue.isBag()	BOOLEAN(false)@BooleanValue	BOOLEAN(false)@Boolean	BOOLEAN(false)@BooleanValue	AGREE	java type mismatch: reference returned java.lang.Boolean (BOOLEAN(false)@Boolean) / subject returned org.tzi.use.uml.ocl.value.BooleanValue (BOOLEAN(false)@BooleanValue); the content is IDENTICAL -- right content, wrong Java type (defect D-18). This row is scored AGREE and counted in rows.javaTypeMismatch, not scored as a divergence: at S1 the ported side's class cannot be authentically observed, because no ported value class exists to observe, so a type-only difference measures the adapter and not the port (D-43). Provenance: reference OBSERVED, subject ASSUMED (OBSERVED = read off the object that side returned; ASSUMED = the factory default for the kind, which is wrong for 182 of 285 operations). The subject's adapter never looked at what its implementation returned, so this difference is a finding about the ADAPTER and not about the port (D-43); an adapter must attribute through UValue.observedFrom(Object).
[corpus uReal] 78	BooleanValue.isBoolean()	BOOLEAN(true)@BooleanValue	BOOLEAN(true)@Boolean	BOOLEAN(true)@BooleanValue	AGREE	java type mismatch: reference returned java.lang.Boolean (BOOLEAN(true)@Boolean) / subject returned org.tzi.use.uml.ocl.value.BooleanValue (BOOLEAN(true)@BooleanValue); the content is IDENTICAL -- right content, wrong Java type (defect D-18). This row is scored AGREE and counted in rows.javaTypeMismatch, not scored as a divergence: at S1 the ported side's class cannot be authentically observed, because no ported value class exists to observe, so a type-only difference measures the adapter and not the port (D-43). Provenance: reference OBSERVED, subject ASSUMED (OBSERVED = read off the object that side returned; ASSUMED = the factory default for the kind, which is wrong for 182 of 285 operations). The subject's adapter never looked at what its implementation returned, so this difference is a finding about the ADAPTER and not about the port (D-43); an adapter must attribute through UValue.observedFrom(Object).
[corpus uInteger] 78	BooleanValue.isBoolean()	BOOLEAN(true)@BooleanValue	BOOLEAN(true)@Boolean	BOOLEAN(true)@BooleanValue	AGREE	java type mismatch: reference returned java.lang.Boolean (BOOLEAN(true)@Boolean) / subject returned org.tzi.use.uml.ocl.value.BooleanValue (BOOLEAN(true)@BooleanValue); the content is IDENTICAL -- right content, wrong Java type (defect D-18). This row is scored AGREE and counted in rows.javaTypeMismatch, not scored as a divergence: at S1 the ported side's class cannot be authentically observed, because no ported value class exists to observe, so a type-only difference measures the adapter and not the port (D-43). Provenance: reference OBSERVED, subject ASSUMED (OBSERVED = read off the object that side returned; ASSUMED = the factory default for the kind, which is wrong for 182 of 285 operations). The subject's adapter never looked at what its implementation returned, so this difference is a finding about the ADAPTER and not about the port (D-43); an adapter must attribute through UValue.observedFrom(Object).
[corpus uBoolean] 78	BooleanValue.isBoolean()	BOOLEAN(true)@BooleanValue	BOOLEAN(true)@Boolean	BOOLEAN(true)@BooleanValue	AGREE	java type mismatch: reference returned java.lang.Boolean (BOOLEAN(true)@Boolean) / subject returned org.tzi.use.uml.ocl.value.BooleanValue (BOOLEAN(true)@BooleanValue); the content is IDENTICAL -- right content, wrong Java type (defect D-18). This row is scored AGREE and counted in rows.javaTypeMismatch, not scored as a divergence: at S1 the ported side's class cannot be authentically observed, because no ported value class exists to observe, so a type-only difference measures the adapter and not the port (D-43). Provenance: reference OBSERVED, subject ASSUMED (OBSERVED = read off the object that side returned; ASSUMED = the factory default for the kind, which is wrong for 182 of 285 operations). The subject's adapter never looked at what its implementation returned, so this difference is a finding about the ADAPTER and not about the port (D-43); an adapter must attribute through UValue.observedFrom(Object).
[corpus uString] 78	BooleanValue.isBoolean()	BOOLEAN(true)@BooleanValue	BOOLEAN(true)@Boolean	BOOLEAN(true)@BooleanValue	AGREE	java type mismatch: reference returned java.lang.Boolean (BOOLEAN(true)@Boolean) / subject returned org.tzi.use.uml.ocl.value.BooleanValue (BOOLEAN(true)@BooleanValue); the content is IDENTICAL -- right content, wrong Java type (defect D-18). This row is scored AGREE and counted in rows.javaTypeMismatch, not scored as a divergence: at S1 the ported side's class cannot be authentically observed, because no ported value class exists to observe, so a type-only difference measures the adapter and not the port (D-43). Provenance: reference OBSERVED, subject ASSUMED (OBSERVED = read off the object that side returned; ASSUMED = the factory default for the kind, which is wrong for 182 of 285 operations). The subject's adapter never looked at what its implementation returned, so this difference is a finding about the ADAPTER and not about the port (D-43); an adapter must attribute through UValue.observedFrom(Object).
[corpus boolean] 78	BooleanValue.isBoolean()	BOOLEAN(true)@BooleanValue	BOOLEAN(true)@Boolean	BOOLEAN(true)@BooleanValue	AGREE	java type mismatch: reference returned java.lang.Boolean (BOOLEAN(true)@Boolean) / subject returned org.tzi.use.uml.ocl.value.BooleanValue (BOOLEAN(true)@BooleanValue); the content is IDENTICAL -- right content, wrong Java type (defect D-18). This row is scored AGREE and counted in rows.javaTypeMismatch, not scored as a divergence: at S1 the ported side's class cannot be authentically observed, because no ported value class exists to observe, so a type-only difference measures the adapter and not the port (D-43). Provenance: reference OBSERVED, subject ASSUMED (OBSERVED = read off the object that side returned; ASSUMED = the factory default for the kind, which is wrong for 182 of 285 operations). The subject's adapter never looked at what its implementation returned, so this difference is a finding about the ADAPTER and not about the port (D-43); an adapter must attribute through UValue.observedFrom(Object).
[corpus string] 78	BooleanValue.isBoolean()	BOOLEAN(true)@BooleanValue	BOOLEAN(true)@Boolean	BOOLEAN(true)@BooleanValue	AGREE	java type mismatch: reference returned java.lang.Boolean (BOOLEAN(true)@Boolean) / subject returned org.tzi.use.uml.ocl.value.BooleanValue (BOOLEAN(true)@BooleanValue); the content is IDENTICAL -- right content, wrong Java type (defect D-18). This row is scored AGREE and counted in rows.javaTypeMismatch, not scored as a divergence: at S1 the ported side's class cannot be authentically observed, because no ported value class exists to observe, so a type-only difference measures the adapter and not the port (D-43). Provenance: reference OBSERVED, subject ASSUMED (OBSERVED = read off the object that side returned; ASSUMED = the factory default for the kind, which is wrong for 182 of 285 operations). The subject's adapter never looked at what its implementation returned, so this difference is a finding about the ADAPTER and not about the port (D-43); an adapter must attribute through UValue.observedFrom(Object).
[corpus zeroDivisors] 78	BooleanValue.isBoolean()	BOOLEAN(true)@BooleanValue	BOOLEAN(true)@Boolean	BOOLEAN(true)@BooleanValue	AGREE	java type mismatch: reference returned java.lang.Boolean (BOOLEAN(true)@Boolean) / subject returned org.tzi.use.uml.ocl.value.BooleanValue (BOOLEAN(true)@BooleanValue); the content is IDENTICAL -- right content, wrong Java type (defect D-18). This row is scored AGREE and counted in rows.javaTypeMismatch, not scored as a divergence: at S1 the ported side's class cannot be authentically observed, because no ported value class exists to observe, so a type-only difference measures the adapter and not the port (D-43). Provenance: reference OBSERVED, subject ASSUMED (OBSERVED = read off the object that side returned; ASSUMED = the factory default for the kind, which is wrong for 182 of 285 operations). The subject's adapter never looked at what its implementation returned, so this difference is a finding about the ADAPTER and not about the port (D-43); an adapter must attribute through UValue.observedFrom(Object).
[corpus indexBoundaries] 78	BooleanValue.isBoolean()	BOOLEAN(true)@BooleanValue	BOOLEAN(true)@Boolean	BOOLEAN(true)@BooleanValue	AGREE	java type mismatch: reference returned java.lang.Boolean (BOOLEAN(true)@Boolean) / subject returned org.tzi.use.uml.ocl.value.BooleanValue (BOOLEAN(true)@BooleanValue); the content is IDENTICAL -- right content, wrong Java type (defect D-18). This row is scored AGREE and counted in rows.javaTypeMismatch, not scored as a divergence: at S1 the ported side's class cannot be authentically observed, because no ported value class exists to observe, so a type-only difference measures the adapter and not the port (D-43). Provenance: reference OBSERVED, subject ASSUMED (OBSERVED = read off the object that side returned; ASSUMED = the factory default for the kind, which is wrong for 182 of 285 operations). The subject's adapter never looked at what its implementation returned, so this difference is a finding about the ADAPTER and not about the port (D-43); an adapter must attribute through UValue.observedFrom(Object).
[corpus uReal] 79	BooleanValue.isCollection()	BOOLEAN(false)@BooleanValue	BOOLEAN(false)@Boolean	BOOLEAN(false)@BooleanValue	AGREE	java type mismatch: reference returned java.lang.Boolean (BOOLEAN(false)@Boolean) / subject returned org.tzi.use.uml.ocl.value.BooleanValue (BOOLEAN(false)@BooleanValue); the content is IDENTICAL -- right content, wrong Java type (defect D-18). This row is scored AGREE and counted in rows.javaTypeMismatch, not scored as a divergence: at S1 the ported side's class cannot be authentically observed, because no ported value class exists to observe, so a type-only difference measures the adapter and not the port (D-43). Provenance: reference OBSERVED, subject ASSUMED (OBSERVED = read off the object that side returned; ASSUMED = the factory default for the kind, which is wrong for 182 of 285 operations). The subject's adapter never looked at what its implementation returned, so this difference is a finding about the ADAPTER and not about the port (D-43); an adapter must attribute through UValue.observedFrom(Object).
[corpus uInteger] 79	BooleanValue.isCollection()	BOOLEAN(false)@BooleanValue	BOOLEAN(false)@Boolean	BOOLEAN(false)@BooleanValue	AGREE	java type mismatch: reference returned java.lang.Boolean (BOOLEAN(false)@Boolean) / subject returned org.tzi.use.uml.ocl.value.BooleanValue (BOOLEAN(false)@BooleanValue); the content is IDENTICAL -- right content, wrong Java type (defect D-18). This row is scored AGREE and counted in rows.javaTypeMismatch, not scored as a divergence: at S1 the ported side's class cannot be authentically observed, because no ported value class exists to observe, so a type-only difference measures the adapter and not the port (D-43). Provenance: reference OBSERVED, subject ASSUMED (OBSERVED = read off the object that side returned; ASSUMED = the factory default for the kind, which is wrong for 182 of 285 operations). The subject's adapter never looked at what its implementation returned, so this difference is a finding about the ADAPTER and not about the port (D-43); an adapter must attribute through UValue.observedFrom(Object).
[corpus uBoolean] 79	BooleanValue.isCollection()	BOOLEAN(false)@BooleanValue	BOOLEAN(false)@Boolean	BOOLEAN(false)@BooleanValue	AGREE	java type mismatch: reference returned java.lang.Boolean (BOOLEAN(false)@Boolean) / subject returned org.tzi.use.uml.ocl.value.BooleanValue (BOOLEAN(false)@BooleanValue); the content is IDENTICAL -- right content, wrong Java type (defect D-18). This row is scored AGREE and counted in rows.javaTypeMismatch, not scored as a divergence: at S1 the ported side's class cannot be authentically observed, because no ported value class exists to observe, so a type-only difference measures the adapter and not the port (D-43). Provenance: reference OBSERVED, subject ASSUMED (OBSERVED = read off the object that side returned; ASSUMED = the factory default for the kind, which is wrong for 182 of 285 operations). The subject's adapter never looked at what its implementation returned, so this difference is a finding about the ADAPTER and not about the port (D-43); an adapter must attribute through UValue.observedFrom(Object).
[corpus uString] 79	BooleanValue.isCollection()	BOOLEAN(false)@BooleanValue	BOOLEAN(false)@Boolean	BOOLEAN(false)@BooleanValue	AGREE	java type mismatch: reference returned java.lang.Boolean (BOOLEAN(false)@Boolean) / subject returned org.tzi.use.uml.ocl.value.BooleanValue (BOOLEAN(false)@BooleanValue); the content is IDENTICAL -- right content, wrong Java type (defect D-18). This row is scored AGREE and counted in rows.javaTypeMismatch, not scored as a divergence: at S1 the ported side's class cannot be authentically observed, because no ported value class exists to observe, so a type-only difference measures the adapter and not the port (D-43). Provenance: reference OBSERVED, subject ASSUMED (OBSERVED = read off the object that side returned; ASSUMED = the factory default for the kind, which is wrong for 182 of 285 operations). The subject's adapter never looked at what its implementation returned, so this difference is a finding about the ADAPTER and not about the port (D-43); an adapter must attribute through UValue.observedFrom(Object).
===================================================================
=== unwritten-port invariant: g-throws-error =================
seed                 20260817
subject              g-throws-error  (every method body: throw new AssertionError("TODO: port " + op.key()))
observability        NOTHING
javaTypeMismatch     0  <- agreement rows on which only the Java class differed (D-43)
operations           355  (enumerated from use.jar + atenearesearchgroup.uncertainty.jar)
corpora              uReal=24, uInteger=15, uBoolean=11, uString=28, boolean=4, string=16, zeroDivisors=7, indexBoundaries=8; receivers=104
rows                 0
measured rows        0  (AGREE + DIFFER)
agreement rows       0
verdict tally        {}
codomain census      0 operations: 0 measured nothing, 0 single-valued (NOT DISCRIMINATING), 0 discriminating
ESCAPED              java.lang.AssertionError: TODO: port BooleanValue.compareTo(value)  -> the sweep ABORTED; rows above are only those completed before it
fully agreed ops, DISCRIMINATING (a finding about the subject)  (none)
fully agreed ops, NOT DISCRIMINATING (a finding about the corpus)  (none)   [ASSERTED against reviewedDegenerateFullyAgreed since the D-35 fix]
===================================================================
=== D-15: the sign-off route ======================================
no sign-off                URealValue.isUReal(): 24 rows, 24 measured, 24 agreed, 0 disagreed, 0 intended departure(s), 0 java-type mismatch(es) (subject token OBSERVED on 0, ASSUMED on 0), 1 distinct reference value(s) [NOT DISCRIMINATING: always BOOLEAN(true)@Boolean]
signed off                 URealValue.isUReal(): 24 rows, 24 measured, 24 agreed, 0 disagreed, 0 intended departure(s), 0 java-type mismatch(es) (subject token OBSERVED on 0, ASSUMED on 0), 1 distinct reference value(s) [NOT DISCRIMINATING: always BOOLEAN(true)@Boolean; acknowledged: URealValue.isUReal() is a type predicate: the historical body is iconst_1/ireturn, so BOOLEAN(true) is the whole of its specification and no corpus can make it answer otherwise. Agreement here shows the operation exists and is reachable; it is not evidence about any computation.]
===================================================================
[INFO] Tests run: 10, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 72.36 s -- in Unwritten-port invariant
[INFO] Running B7 corrections the differential sweep cannot see
[INFO] Running M-18: SBooleanValue.compareTo was 'return 0'
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.004 s -- in M-18: SBooleanValue.compareTo was 'return 0'
[INFO] Running M-12: UStringValue.compareTo compared a bare string against a wrapper rendering
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.002 s -- in M-12: UStringValue.compareTo compared a bare string against a wrapper rendering
[INFO] Running M-9 and bundle A: UInteger against UReal compared equal in both directions
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.002 s -- in M-9 and bundle A: UInteger against UReal compared equal in both directions
[INFO] Running F-3 and F-10: the hashCode/equals contract
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.003 s -- in F-3 and F-10: the hashCode/equals contract
[INFO] Running M-10 and F-4: URealValue.equals had no UIntegerValue arm and did not round
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.004 s -- in M-10 and F-4: URealValue.equals had no UIntegerValue arm and did not round
[INFO] Running M-8: UBooleanValue.equals had a dead conjunct
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.002 s -- in M-8: UBooleanValue.equals had a dead conjunct
[INFO] Running M-11: UStringValue.equals was the constant false
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.002 s -- in M-11: UStringValue.equals was the constant false
[INFO] Tests run: 0, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.031 s -- in B7 corrections the differential sweep cannot see
[INFO] Running HistoricalOracle class-loader isolation
[INFO] Tests run: 11, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.026 s -- in HistoricalOracle class-loader isolation
[INFO] Running Differential harness regressions
=== D1 reproduction ===============================================
tally                URealValue.add(value): 169 rows, 0 measured, 0 distinct ref, HARNESS_ERROR=169
disagreements        169
row 0                0	URealValue.add(value)	UINTEGER(0,0.0)@UIntegerValue | UINTEGER(0,0.0)@UIntegerValue	HARNESS_ERROR:org.tzi.use.uncertainty.differential.HarnessMarshallingException	HARNESS_ERROR:org.tzi.use.uncertainty.differential.HarnessMarshallingException	HARNESS_ERROR	no measurement on either side; no comparison was made. reference could not be driven: org.tzi.use.uncertainty.differential.HarnessMarshallingException: URealValue.add(value) expects a receiver of org.tzi.use.uml.ocl.value.URealValue but the supplied UINTEGER(0,0.0)@UIntegerValue maps to org.tzi.use.uml.ocl.value.UIntegerValue / subject could not be driven: org.tzi.use.uncertainty.differential.HarnessMarshallingException: URealValue.add(value) needs a UREAL receiver, got UINTEGER(0,0.0)@UIntegerValue
===================================================================
=== D-2 reproduction (two stubs) ==================================
tally                URealValue.add(value): 169 rows, 0 measured, 0 distinct ref, HARNESS_ERROR=169
disagreements        169
row 0                0	URealValue.add(value)	UINTEGER(0,0.0)@UIntegerValue | UINTEGER(0,0.0)@UIntegerValue	HARNESS_ERROR:org.tzi.use.uncertainty.differential.HarnessMarshallingException	HARNESS_ERROR:org.tzi.use.uncertainty.differential.HarnessMarshallingException	HARNESS_ERROR	no measurement on either side; no comparison was made. reference could not be driven: org.tzi.use.uncertainty.differential.HarnessMarshallingException: URealValue.add(value) needs a UREAL receiver, got UINTEGER(0,0.0)@UIntegerValue / subject could not be driven: org.tzi.use.uncertainty.differential.HarnessMarshallingException: URealValue.add(value) needs a UREAL receiver, got UINTEGER(0,0.0)@UIntegerValue
===================================================================
=== D-13: wrong exception class ===================================
same class  UStringValue.at(int): 2 rows, 0 measured, 0 distinct ref, BOTH_THREW=2
wrong class UStringValue.at(int): 2 rows, 0 measured, 0 distinct ref, BOTH_THREW=2, throwClassMismatch=2
===================================================================
=== D-43: every public UValue member that accepts a String ========
  opaque[class java.lang.String, class java.lang.String]
  string[class java.lang.String]
  uString[class java.lang.String, double]
===================================================================
=== D-43: the same type-mismatch row, two attributions of the subject ===
  ASSUMED (factory default)
      java type mismatch: reference returned java.lang.Integer (INTEGER(7)@Integer) / subject returned org.tzi.use.uml.ocl.value.IntegerValue (INTEGER(7)@IntegerValue); the content is IDENTICAL -- right content, wrong Java type (defect D-18). This row is scored AGREE and counted in rows.javaTypeMismatch, not scored as a divergence: at S1 the ported side's class cannot be authentically observed, because no ported value class exists to observe, so a type-only difference measures the adapter and not the port (D-43). Provenance: reference OBSERVED, subject ASSUMED (OBSERVED = read off the object that side returned; ASSUMED = the factory default for the kind, which is wrong for 182 of 285 operations). The subject's adapter never looked at what its implementation returned, so this difference is a finding about the ADAPTER and not about the port (D-43); an adapter must attribute through UValue.observedFrom(Object).
  OBSERVED (off a real object of another class)
      java type mismatch: reference returned java.lang.Integer (INTEGER(7)@Integer) / subject returned java.util.concurrent.atomic.AtomicInteger (INTEGER(7)@AtomicInteger); the content is IDENTICAL -- right content, wrong Java type (defect D-18). This row is scored AGREE and counted in rows.javaTypeMismatch, not scored as a divergence: at S1 the ported side's class cannot be authentically observed, because no ported value class exists to observe, so a type-only difference measures the adapter and not the port (D-43). Provenance: reference OBSERVED, subject OBSERVED (OBSERVED = read off the object that side returned; ASSUMED = the factory default for the kind, which is wrong for 182 of 285 operations). Whether the object the subject observed is the one its implementation returned is not checkable by this harness.
===================================================================
=== D-3: UNSUPPORTED note =========================================
0	SetValue.includes(value)	UBOOLEAN(true,0.5)@UBooleanValue | UBOOLEAN(false,0.5)@UBooleanValue	UNSUPPORTED	UNSUPPORTED	UNSUPPORTED	no measurement. reference: this harness cannot marshal a SetValue receiver, so it cannot drive SetValue.includes(value); this is a limit of the instrument and says nothing about whether the historical implementation declares the operation / subject: stub-faithful implements only [URealValue.add(value), URealValue.minus(value), URealValue.neg()], not SetValue.includes(value)
===================================================================
=== MIXED note, both directions ===================================
the reference threw and the subject returned. reference threw java.lang.RuntimeException: historical blew up / subject returned UREAL(2.0,0.7071067811865476)@URealValue
===================================================================
=== both-sided HARNESS_ERROR note =================================
no measurement on either side; no comparison was made. reference could not be driven: org.tzi.use.uncertainty.differential.HarnessMarshallingException: cannot marshal UREAL(1.0,0.5)@URealValue for URealValue.add(value) [ref] / subject could not be driven: org.tzi.use.uncertainty.differential.HarnessMarshallingException: cannot marshal UREAL(1.0,0.5)@URealValue for URealValue.add(value) [sub]
===================================================================
=== D-15: the stage gate ==========================================
sweep of URealValue.add(value) is not a stage pass:
  - the reference side produced 1 distinct value(s) across 1 measured row(s), always UREAL(2.0,0.0)@URealValue. This operation could not have failed over this domain, so agreement on it is decided before either implementation runs and is not evidence of fidelity (defect D-15). Either widen the domain until the reference answers differently, or sign the operation off in AcceptedDegenerateOperations with a written rationale — which is copied into the report, so the weakness travels with the number.
  tally: URealValue.add(value): 1 rows, 1 measured, 1 distinct ref, AGREE=1
PASSES: URealValue.add(value): 16 rows, 16 measured, 16 agreed, 0 disagreed, 0 intended departure(s), 0 java-type mismatch(es) (subject token OBSERVED on 0, ASSUMED on 0), 10 distinct reference value(s) [DISCRIMINATING]
===================================================================
=== D-18: the type-mismatch note =================================
0	URealValue.neg()	UREAL(1.0,0.0)@URealValue	INTEGER(7)@Integer	INTEGER(7)@IntegerValue	AGREE	java type mismatch: reference returned java.lang.Integer (INTEGER(7)@Integer) / subject returned org.tzi.use.uml.ocl.value.IntegerValue (INTEGER(7)@IntegerValue); the content is IDENTICAL -- right content, wrong Java type (defect D-18). This row is scored AGREE and counted in rows.javaTypeMismatch, not scored as a divergence: at S1 the ported side's class cannot be authentically observed, because no ported value class exists to observe, so a type-only difference measures the adapter and not the port (D-43). Provenance: reference OBSERVED, subject ASSUMED (OBSERVED = read off the object that side returned; ASSUMED = the factory default for the kind, which is wrong for 182 of 285 operations). The subject's adapter never looked at what its implementation returned, so this difference is a finding about the ADAPTER and not about the port (D-43); an adapter must attribute through UValue.observedFrom(Object).
===================================================================
=== FIX 1: two unrelated RuntimeExceptions ========================
0	UIntegerValue.power(value)	UINTEGER(2,0.5)@UIntegerValue | UINTEGER(3,0.5)@UIntegerValue	THROWN:java.lang.RuntimeException	THROWN:java.lang.RuntimeException	BOTH_THREW	reference threw java.lang.RuntimeException: UInteger.power() : expected Real or Integer exponent value / subject threw java.lang.RuntimeException: TODO: port UIntegerValue.power(value)
===================================================================
=== D-15: the report header =======================================
# harness	differential-sweep/1
# seed	1
# reference	stub-faithful
# subject	stub-faithful
# operations	URealValue.add(value),URealValue.minus(value)
# rows	10
# rows.measured	10
# rows.agreement	10
# rows.disagreement	0
# rows.throwClassMismatch	0
# rows.javaTypeMismatch	0
# rows.subjectTypeObserved	0
# rows.subjectTypeAssumed	0
# rows.intendedDeparture	0
# verdict.AGREE	10
# op.URealValue.add(value).rows	1
# op.URealValue.add(value).measured	1
# op.URealValue.add(value).agreement	1
# op.URealValue.add(value).disagreement	0
# op.URealValue.add(value).intendedDeparture	0
# op.URealValue.add(value).javaTypeMismatch	0
# op.URealValue.add(value).subjectTypeObserved	0
# op.URealValue.add(value).subjectTypeAssumed	0
# op.URealValue.add(value).distinctReferenceValues	1
# op.URealValue.add(value).discriminating	false
# op.URealValue.add(value).soleReferenceValue	UREAL(2.0,0.0)@URealValue
# op.URealValue.add(value).degenerate.acknowledged	reviewed: one-point domain
# op.URealValue.minus(value).rows	9
# op.URealValue.minus(value).measured	9
# op.URealValue.minus(value).agreement	9
# op.URealValue.minus(value).disagreement	0
# op.URealValue.minus(value).intendedDeparture	0
# op.URealValue.minus(value).javaTypeMismatch	0
# op.URealValue.minus(value).subjectTypeObserved	0
# op.URealValue.minus(value).subjectTypeAssumed	0
# op.URealValue.minus(value).distinctReferenceValues	7
# op.URealValue.minus(value).discriminating	true
# accepted.degenerateOperations	1
# accepted.degenerateOperation	URealValue.add(value)|UREAL(2.0,0.0)@URealValue -> reviewed: one-point domain
===================================================================
=== H21: the same mismatch total, two causes =======================
  summary  ASSUMED  URealValue.add(value): 4 rows, 4 measured, 1 distinct ref, AGREE=4, javaTypeMismatch=4 (subjectType OBSERVED=0 ASSUMED=4)
  summary  OBSERVED URealValue.minus(value): 4 rows, 4 measured, 1 distinct ref, AGREE=4, javaTypeMismatch=4 (subjectType OBSERVED=4 ASSUMED=0)
  stage    ASSUMED  URealValue.add(value): 4 rows, 4 measured, 4 agreed, 0 disagreed, 0 intended departure(s), 4 java-type mismatch(es) (subject token OBSERVED on 0, ASSUMED on 4), 1 distinct reference value(s) [NOT DISCRIMINATING: always INTEGER(7)@Integer]
  stage    OBSERVED URealValue.minus(value): 4 rows, 4 measured, 4 agreed, 0 disagreed, 0 intended departure(s), 4 java-type mismatch(es) (subject token OBSERVED on 4, ASSUMED on 0), 1 distinct reference value(s) [NOT DISCRIMINATING: always INTEGER(7)@Integer]
===================================================================
=== H21: the report header ========================================
# harness	differential-sweep/1
# seed	1
# reference	ref
# subject	sub-assumed
# operations	URealValue.add(value),URealValue.minus(value)
# rows	8
# rows.measured	8
# rows.agreement	8
# rows.disagreement	0
# rows.throwClassMismatch	0
# rows.javaTypeMismatch	8
# rows.subjectTypeObserved	4
# rows.subjectTypeAssumed	4
# rows.intendedDeparture	0
# verdict.AGREE	8
# op.URealValue.add(value).rows	4
# op.URealValue.add(value).measured	4
# op.URealValue.add(value).agreement	4
# op.URealValue.add(value).disagreement	0
# op.URealValue.add(value).intendedDeparture	0
# op.URealValue.add(value).javaTypeMismatch	4
# op.URealValue.add(value).subjectTypeObserved	0
# op.URealValue.add(value).subjectTypeAssumed	4
# op.URealValue.add(value).distinctReferenceValues	1
# op.URealValue.add(value).discriminating	false
# op.URealValue.add(value).soleReferenceValue	INTEGER(7)@Integer
# op.URealValue.minus(value).rows	4
# op.URealValue.minus(value).measured	4
# op.URealValue.minus(value).agreement	4
# op.URealValue.minus(value).disagreement	0
# op.URealValue.minus(value).intendedDeparture	0
# op.URealValue.minus(value).javaTypeMismatch	4
# op.URealValue.minus(value).subjectTypeObserved	4
# op.URealValue.minus(value).subjectTypeAssumed	0
# op.URealValue.minus(value).distinctReferenceValues	1
# op.URealValue.minus(value).discriminating	false
# op.URealValue.minus(value).soleReferenceValue	INTEGER(7)@Integer
# accepted.degenerateOperations	0
===================================================================
=== D-11: writer refusal ==========================================
refusing to write a differential report 'no-measurements.tsv' that contains no measurements: 22 row(s) across 1 sweep result(s), and not one of them compared two observed values. Every number this file would carry would describe an absence, and a reader would see '# rows 22' and a green-looking verdict tally. The usual causes are a subject that throws on every row, a receiver type the harness cannot marshal, and an operation that returns void.
    URealValue.setTypeToRuntimeType(): 22 rows, 0 measured, 0 distinct ref, UNMEASURABLE=22
===================================================================
=== OPAQUE representation =========================================
foreign toString()   UReal(0.3333333333, 0.6666666667)
field-derived        URealValue{Value.fType=URealType{BasicType.fTypename="UReal"},URealValue.uReal=UReal{UReal.u=0.6666666666666666,UReal.x=0.3333333333333333}}
SBooleanValue.TRUE   toString  SBoolean(1.0, 0.0, 0.0, 1.0)
SBooleanValue.TRUE   canonical OPAQUE("org.tzi.use.uml.ocl.value.SBooleanValue|SBooleanValue{Value.fType=SBooleanType{BasicType.fTypename=\"SBoolean\"},SBooleanValue.sBoolean=SBoolean{SBoolean.a=1.0,SBoolean.b=1.0,SBoolean.d=0.0,SBoolean.relativeWeight=1.0,SBoolean.u=0.0}}")@SBooleanValue
===================================================================
=== golden byte comparison ========================================
differential report /home/xoruser/msc-4/use-msc2026/use-core/target/differential/d-byte-probe.tsv differs from the committed golden /home/xoruser/msc-4/use-msc2026/docs/port2/differential/d-byte-probe.tsv in bytes but not in any line: the files disagree only about line terminators or a trailing newline. A line-based comparison would have called these two files equal. Re-record with -Duse.differential.golden.refresh=true once the change is understood.
===================================================================
=== D-10 reproduction (VOID vs VOID) ==============================
tally                URealValue.setTypeToRuntimeType(): 22 rows, 0 measured, 0 distinct ref, UNMEASURABLE=22
measurements         0
agreements           0
row 0                0	URealValue.setTypeToRuntimeType()	UREAL(0.0,0.0)@URealValue	VOID	VOID	UNMEASURABLE	no measurement: the operation is declared void, so it has no result, and this harness does not re-read the receiver after a call -- no post-state was observed on either side, so nothing about either implementation was measured here. reference returned VOID / subject returned VOID
===================================================================
=== D-1 reproduction (null vs null) ===============================
tally                URealValue.add(value): 4 rows, 0 measured, 0 distinct ref, HARNESS_ERROR=4
row 0                0	URealValue.add(value)	UREAL(1.0,0.5)@URealValue | UREAL(1.0,0.5)@URealValue	HARNESS_ERROR:org.tzi.use.uncertainty.differential.HarnessMarshallingException	HARNESS_ERROR:org.tzi.use.uncertainty.differential.HarnessMarshallingException	HARNESS_ERROR	no measurement on either side; no comparison was made. reference could not be driven: org.tzi.use.uncertainty.differential.HarnessMarshallingException: returns-null returned Java null from URealValue.add(value); a Candidate must return a UValue (use UValue.nullValue() for a genuine null result). No comparable value exists, so this row is not a measurement. / subject could not be driven: org.tzi.use.uncertainty.differential.HarnessMarshallingException: returns-null returned Java null from URealValue.add(value); a Candidate must return a UValue (use UValue.nullValue() for a genuine null result). No comparable value exists, so this row is not a measurement.
===================================================================
=== D-34: the same sweep, two sign-off sets =======================
  signed:  # accepted.degenerateOperations	1
  signed:  # accepted.degenerateOperation	URealValue.add(value)|UREAL(2.0,0.0)@URealValue -> reviewed: a one-point domain, kept as a reachability check only; nothing here is evidence about the addition rule
  none:    # accepted.degenerateOperations	0
===================================================================
[INFO] Tests run: 35, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.135 s -- in Differential harness regressions
[INFO] Running org.tzi.use.uncertainty.differential.IntendedDeparturesTest
[INFO] Running content is split off the type token without guessing
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.001 s -- in content is split off the type token without guessing
[INFO] Running the builder refuses what would become a blanket exemption
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.006 s -- in the builder refuses what would become a blanket exemption
[INFO] Running the population form names an exact set, written out
[INFO] Tests run: 7, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.006 s -- in the population form names an exact set, written out
[INFO] Running the gate cannot be reached without naming the mechanism
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.003 s -- in the gate cannot be reached without naming the mechanism
[INFO] Running it cannot be used to make a wrong port look right
[INFO] Tests run: 6, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.005 s -- in it cannot be used to make a wrong port look right
[INFO] Running the verdict is a measurement and is not an agreement
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.002 s -- in the verdict is a measurement and is not an agreement
[INFO] Tests run: 0, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.026 s -- in org.tzi.use.uncertainty.differential.IntendedDeparturesTest
[INFO] Running org.tzi.use.uncertainty.datatypes.DatatypeCloneableContractTest
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.001 s -- in org.tzi.use.uncertainty.datatypes.DatatypeCloneableContractTest
[INFO] Running uSelect/uSelectC, UBoolean forAll/exists, uncertain collection membership
[INFO] Running multi-variable forAll/exists: a pre-existing fork defect, found and fixed
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.004 s -- in multi-variable forAll/exists: a pre-existing fork defect, found and fixed
[INFO] Running collection membership answers a degree when the comparison is uncertain
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.004 s -- in collection membership answers a degree when the comparison is uncertain
[INFO] Running forAll/exists accept a UBoolean body and combine via uncertain and/or
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.003 s -- in forAll/exists accept a UBoolean body and combine via uncertain and/or
[INFO] Running uSelectC: explicit confidence threshold
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.008 s -- in uSelectC: explicit confidence threshold
[INFO] Running uSelect: default 0.5 confidence threshold
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.002 s -- in uSelect: default 0.5 confidence threshold
[INFO] Tests run: 0, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.022 s -- in uSelect/uSelectC, UBoolean forAll/exists, uncertain collection membership
[INFO] Running SBoolean: the 13 operations found untested
[INFO] Running conjunctiveCertainty/degreeOfConflict: declared-type fix and its regression
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.001 s -- in conjunctiveCertainty/degreeOfConflict: declared-type fix and its regression
[INFO] Running conversions and pairwise operations
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.002 s -- in conversions and pairwise operations
[INFO] Running classification predicates
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.004 s -- in classification predicates
[INFO] Running scalar accessors: projection, getRelativeWeight, certainty
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.001 s -- in scalar accessors: projection, getRelativeWeight, certainty
[INFO] Tests run: 0, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.010 s -- in SBoolean: the 13 operations found untested
[INFO] Running UString: all 20 operations
[INFO] Running ordering: <, <=, >, >=
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.001 s -- in ordering: <, <=, >, >=
[INFO] Running conversions: toString, toInteger, toReal, toBoolean, toUBoolean
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.002 s -- in conversions: toString, toInteger, toReal, toBoolean, toUBoolean
[INFO] Running case conversion
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.001 s -- in case conversion
[INFO] Running concatenation, indexOf, substring
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.001 s -- in concatenation, indexOf, substring
[INFO] Running character access: at, character, size
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.001 s -- in character access: at, character, size
[INFO] Running mutators: setValue, setConfidence
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.001 s -- in mutators: setValue, setConfidence
[INFO] Running accessors: value, confidence
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.001 s -- in accessors: value, confidence
[INFO] Tests run: 0, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.010 s -- in UString: all 20 operations
[INFO] Running org.tzi.use.uncertainty.SBooleanFusionValueTest
[INFO] Running consensusAndCompromiseFusion: O(4^n) hazard and degenerate cases
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.014 s -- in consensusAndCompromiseFusion: O(4^n) hazard and degenerate cases
[INFO] Running weightedBeliefFusion: receiver-prepended, confidence-weighted averaging (FUSION-2018 van der Heijden et al.)
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.002 s -- in weightedBeliefFusion: receiver-prepended, confidence-weighted averaging (FUSION-2018 van der Heijden et al.)
[INFO] Running aleatoryCumulativeBeliefFusion vs epistemicCumulativeBeliefFusion: same input must diverge
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.001 s -- in aleatoryCumulativeBeliefFusion vs epistemicCumulativeBeliefFusion: same input must diverge
[INFO] Running epistemicCumulativeBeliefFusion: same accumulation as aleatory, then projected onto the uncertainty-maximized boundary
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.003 s -- in epistemicCumulativeBeliefFusion: same accumulation as aleatory, then projected onto the uncertainty-maximized boundary
[INFO] Running aleatoryCumulativeBeliefFusion: receiver-prepended, Josang's cumulative fusion for i.i.d. sources
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.002 s -- in aleatoryCumulativeBeliefFusion: receiver-prepended, Josang's cumulative fusion for i.i.d. sources
[INFO] Running averageBeliefFusion: receiver-prepended, equation (32) of JWZ2017-FUSION (not the book formula)
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.002 s -- in averageBeliefFusion: receiver-prepended, equation (32) of JWZ2017-FUSION (not the book formula)
[INFO] Running beliefConstraintFusion: receiver-prepended, Dempster's-rule belief-constraint combination
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.003 s -- in beliefConstraintFusion: receiver-prepended, Dempster's-rule belief-constraint combination
[INFO] Running discount: multi-edge trust discounting, receiver NOT prepended to the collection
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.003 s -- in discount: multi-edge trust discounting, receiver NOT prepended to the collection
[INFO] Running majorityBeliefFusion: dogmatic vote by projection-vs-baseRate
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.002 s -- in majorityBeliefFusion: dogmatic vote by projection-vs-baseRate
[INFO] Running minimumBeliefFusion: receiver-prepended, picks the lowest-projection opinion
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.003 s -- in minimumBeliefFusion: receiver-prepended, picks the lowest-projection opinion
[INFO] Tests run: 0, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.038 s -- in org.tzi.use.uncertainty.SBooleanFusionValueTest
[INFO] Running org.tzi.use.uncertainty.gate.UpstreamOracleGateWiringTest
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.006 s -- in org.tzi.use.uncertainty.gate.UpstreamOracleGateWiringTest
[INFO] Running org.tzi.use.uml.mm.ModelAPITest
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.026 s -- in org.tzi.use.uml.mm.ModelAPITest
[INFO] Running org.tzi.use.uml.ocl.value.MetamorphicRelationsTest
[INFO] Running M-6: simplex closure — every SBoolean-returning operation satisfies |b+d+u-1| <= 0.001
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.004 s -- in M-6: simplex closure — every SBoolean-returning operation satisfies |b+d+u-1| <= 0.001
[INFO] Running M-5: interning independence — a value equal to TRUE/FALSE but not the interned instance behaves identically
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.001 s -- in M-5: interning independence — a value equal to TRUE/FALSE but not the interned instance behaves identically
[INFO] Running M-4: widening agreement — a UInteger operation and its UReal widening agree where both are defined
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.001 s -- in M-4: widening agreement — a UInteger operation and its UReal widening agree where both are defined
[INFO] Running M-3: canonicalisation — UBoolean(false,p) is UBoolean(true,1-p) on every operation
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.001 s -- in M-3: canonicalisation — UBoolean(false,p) is UBoolean(true,1-p) on every operation
[INFO] Running M-2: degree monotonicity — raising an input's uncertainty must not lower the result's
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.001 s -- in M-2: degree monotonicity — raising an input's uncertainty must not lower the result's
[INFO] Running M-1: crisp embedding — op(U(x,0), U(y,0)) carries the same representative as crisp op(x,y), degree 0
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.002 s -- in M-1: crisp embedding — op(U(x,0), U(y,0)) carries the same representative as crisp op(x,y), degree 0
[INFO] Tests run: 0, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.013 s -- in org.tzi.use.uml.ocl.value.MetamorphicRelationsTest
[INFO] Running org.tzi.use.uml.ocl.value.UBooleanValueTest
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.002 s -- in org.tzi.use.uml.ocl.value.UBooleanValueTest
[INFO] Running org.tzi.use.uml.ocl.expr.UBooleanExpOpsTest
[INFO] Tests run: 27, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.017 s -- in org.tzi.use.uml.ocl.expr.UBooleanExpOpsTest
[INFO] Running org.tzi.use.uml.ocl.expr.UIntegerExpOpsTest
[INFO] Tests run: 39, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.017 s -- in org.tzi.use.uml.ocl.expr.UIntegerExpOpsTest
[INFO] Running org.tzi.use.uml.ocl.expr.URealExpOpsTest
[INFO] Tests run: 38, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.017 s -- in org.tzi.use.uml.ocl.expr.URealExpOpsTest
[INFO] Running org.tzi.use.uml.ocl.expr.ExpQueryUncertaintyTest
[INFO] Tests run: 12, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.007 s -- in org.tzi.use.uml.ocl.expr.ExpQueryUncertaintyTest
[INFO] Running B7 at the type and dispatch layers
[INFO] Running M-37: UInteger.value() declared a static type its eval never returns
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.001 s -- in M-37: UInteger.value() declared a static type its eval never returns
[INFO] Running M-38: `or` on two undefined UBooleans threw NullPointerException
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.002 s -- in M-38: `or` on two undefined UBooleans threw NullPointerException
[INFO] Running porting omission: VoidType had no isKindOfU* overrides
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.003 s -- in porting omission: VoidType had no isKindOfU* overrides
[INFO] Running M-22: every uncertain type's constructor is package-private
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0 s -- in M-22: every uncertain type's constructor is package-private
[INFO] Running M-21: a directly-constructed type was missing from its own supertype set
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.002 s -- in M-21: a directly-constructed type was missing from its own supertype set
[INFO] Tests run: 0, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.008 s -- in B7 at the type and dispatch layers
[INFO] Running org.tzi.use.uml.ocl.type.UncertainTypeLatticeTest
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.002 s -- in org.tzi.use.uml.ocl.type.UncertainTypeLatticeTest
[INFO] Running org.tzi.use.uml.ocl.type.TupleTypeSupertypeCostTest
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.028 s -- in org.tzi.use.uml.ocl.type.TupleTypeSupertypeCostTest
[INFO] Running org.tzi.use.parser.AllTests
[INFO] Running org.tzi.use.parser.USECompilerTest
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.152 s -- in org.tzi.use.parser.USECompilerTest
[INFO] Running org.tzi.use.parser.soil.ASTConstructionTest
[INFO] Tests run: 12, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.070 s -- in org.tzi.use.parser.soil.ASTConstructionTest
[INFO] Running org.tzi.use.parser.soil.StatementGenerationTest
Warning: Iteration over a non-ordered collection. Order of the result might not be as expected. (for x in Set{1,2,3}do ... end)
Warning: Iteration over a non-ordered collection. Order of the result might not be as expected. (for x in Set{1,2,3}do ... end)
[INFO] Tests run: 12, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.058 s -- in org.tzi.use.parser.soil.StatementGenerationTest
[INFO] Running org.tzi.use.parser.shell.ASTConstructionTest
[INFO] Tests run: 9, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.044 s -- in org.tzi.use.parser.shell.ASTConstructionTest
[INFO] Running org.tzi.use.parser.shell.StatementGenerationTest
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.010 s -- in org.tzi.use.parser.shell.StatementGenerationTest
[INFO] Tests run: 0, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.339 s -- in org.tzi.use.parser.AllTests
[INFO] Running org.tzi.use.parser.soil.AllTests
[INFO] Running org.tzi.use.parser.soil.ASTConstructionTest
[INFO] Tests run: 12, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.030 s -- in org.tzi.use.parser.soil.ASTConstructionTest
[INFO] Running org.tzi.use.parser.soil.StatementGenerationTest
Warning: Iteration over a non-ordered collection. Order of the result might not be as expected. (for x in Set{1,2,3}do ... end)
Warning: Iteration over a non-ordered collection. Order of the result might not be as expected. (for x in Set{1,2,3}do ... end)
[INFO] Tests run: 12, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.047 s -- in org.tzi.use.parser.soil.StatementGenerationTest
[INFO] Tests run: 0, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.078 s -- in org.tzi.use.parser.soil.AllTests
[INFO] Running org.tzi.use.parser.soil.ASTConstructionTest
[INFO] Tests run: 12, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.027 s -- in org.tzi.use.parser.soil.ASTConstructionTest
[INFO] Running org.tzi.use.parser.soil.StatementGenerationTest
Warning: Iteration over a non-ordered collection. Order of the result might not be as expected. (for x in Set{1,2,3}do ... end)
Warning: Iteration over a non-ordered collection. Order of the result might not be as expected. (for x in Set{1,2,3}do ... end)
[INFO] Tests run: 12, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.032 s -- in org.tzi.use.parser.soil.StatementGenerationTest
[INFO] Running org.tzi.use.parser.shell.AllTests
[INFO] Running org.tzi.use.parser.shell.ASTConstructionTest
[INFO] Tests run: 9, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.018 s -- in org.tzi.use.parser.shell.ASTConstructionTest
[INFO] Running org.tzi.use.parser.shell.StatementGenerationTest
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.003 s -- in org.tzi.use.parser.shell.StatementGenerationTest
[INFO] Tests run: 0, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.021 s -- in org.tzi.use.parser.shell.AllTests
[INFO] Running org.tzi.use.parser.shell.ASTConstructionTest
[INFO] Tests run: 9, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.010 s -- in org.tzi.use.parser.shell.ASTConstructionTest
[INFO] Running org.tzi.use.parser.shell.StatementGenerationTest
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.007 s -- in org.tzi.use.parser.shell.StatementGenerationTest
[INFO] Running org.tzi.use.parser.USECompilerTest
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.059 s -- in org.tzi.use.parser.USECompilerTest
[INFO] Running org.tzi.use.architecture.AntCyclicDependenciesCoreTest
No of classes incl.: 976
Number of cycles in org.tzi.use.main: 0
No of classes incl.: 976
Number of cycles in org.tzi.use.analysis: 0
No of classes incl.: 976
Cycles in core module: 34
No of classes incl.: 976
Number of cycles in org.tzi.use.util: 0
No of classes incl.: 976
Number of cycles in org.tzi.use.gen: 1
No of classes incl.: 976
Number of cycles in org.tzi.use.parser: 2
No of classes incl.: 976
Number of cycles in org.tzi.use.api: 1
No of classes incl.: 976
Number of cycles in org.tzi.use.graph: 0
No of classes incl.: 976
Number of cycles in org.tzi.use.config: 0
No of classes incl.: 976
Number of cycles in org.tzi.use.uml: 5
[INFO] Tests run: 10, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 6.042 s -- in org.tzi.use.architecture.AntCyclicDependenciesCoreTest
[INFO] Running org.tzi.use.AllTests
[INFO] Running org.tzi.use.graph.GraphTest
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.003 s -- in org.tzi.use.graph.GraphTest
[INFO] Running org.tzi.use.parser.USECompilerTest
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.039 s -- in org.tzi.use.parser.USECompilerTest
[INFO] Running org.tzi.use.parser.soil.ASTConstructionTest
[INFO] Tests run: 12, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.035 s -- in org.tzi.use.parser.soil.ASTConstructionTest
[INFO] Running org.tzi.use.parser.soil.StatementGenerationTest
Warning: Iteration over a non-ordered collection. Order of the result might not be as expected. (for x in Set{1,2,3}do ... end)
Warning: Iteration over a non-ordered collection. Order of the result might not be as expected. (for x in Set{1,2,3}do ... end)
[INFO] Tests run: 12, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.015 s -- in org.tzi.use.parser.soil.StatementGenerationTest
[INFO] Running org.tzi.use.parser.shell.ASTConstructionTest
[INFO] Tests run: 9, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.014 s -- in org.tzi.use.parser.shell.ASTConstructionTest
[INFO] Running org.tzi.use.parser.shell.StatementGenerationTest
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.002 s -- in org.tzi.use.parser.shell.StatementGenerationTest
[INFO] Running org.tzi.use.uml.mm.MAssociationClassTest
[INFO] Tests run: 6, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.005 s -- in org.tzi.use.uml.mm.MAssociationClassTest
[INFO] Running org.tzi.use.uml.mm.MMultiplicityTest
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0 s -- in org.tzi.use.uml.mm.MMultiplicityTest
[INFO] Running org.tzi.use.uml.mm.ModelCreationTest
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.024 s -- in org.tzi.use.uml.mm.ModelCreationTest
[INFO] Running org.tzi.use.uml.ocl.expr.EvaluatorTest
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.020 s -- in org.tzi.use.uml.ocl.expr.EvaluatorTest
[INFO] Running org.tzi.use.uml.ocl.expr.ExpQueryTest
[INFO] Tests run: 13, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.005 s -- in org.tzi.use.uml.ocl.expr.ExpQueryTest
[INFO] Running org.tzi.use.uml.ocl.expr.ExprNavigationTest
[INFO] Tests run: 12, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.012 s -- in org.tzi.use.uml.ocl.expr.ExprNavigationTest
[INFO] Running org.tzi.use.uml.ocl.expr.ExpStdOpTest
[INFO] Tests run: 11, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.006 s -- in org.tzi.use.uml.ocl.expr.ExpStdOpTest
[INFO] Running org.tzi.use.uml.ocl.expr.NavigationTest
[INFO] Tests run: 6, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.006 s -- in org.tzi.use.uml.ocl.expr.NavigationTest
[INFO] Running org.tzi.use.uml.ocl.type.TypeTest
[INFO] Tests run: 38, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.019 s -- in org.tzi.use.uml.ocl.type.TypeTest
[INFO] Running org.tzi.use.uml.ocl.value.ValueTest
[INFO] Tests run: 11, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.006 s -- in org.tzi.use.uml.ocl.value.ValueTest
[INFO] Running org.tzi.use.uml.sys.DeletionTest
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.005 s -- in org.tzi.use.uml.sys.DeletionTest
[INFO] Running org.tzi.use.uml.sys.LinkTest
[INFO] Tests run: 8, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.011 s -- in org.tzi.use.uml.sys.LinkTest
[INFO] Running org.tzi.use.uml.sys.MCmdDestroyObjectsTest
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.001 s -- in org.tzi.use.uml.sys.MCmdDestroyObjectsTest
[INFO] Running org.tzi.use.uml.sys.soil.StatementEffectTest
Warning: Iteration over a non-ordered collection. Order of the result might not be as expected. (for x in Set{0}do ... end)
Warning: Iteration over a non-ordered collection. Order of the result might not be as expected. (for x in Set{0}do ... end)
[INFO] Tests run: 11, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.080 s -- in org.tzi.use.uml.sys.soil.StatementEffectTest
[INFO] Running org.tzi.use.utilcore.AbstractBagTest
[INFO] Tests run: 6, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.002 s -- in org.tzi.use.utilcore.AbstractBagTest
[INFO] Running org.tzi.use.utilcore.ReportTest
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.001 s -- in org.tzi.use.utilcore.ReportTest
[INFO] Running org.tzi.use.utilcore.StringUtilTest
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.001 s -- in org.tzi.use.utilcore.StringUtilTest
[INFO] Running org.tzi.use.utilcore.CombinationTest
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.002 s -- in org.tzi.use.utilcore.CombinationTest
[INFO] Running org.tzi.use.utilcore.soil.VariableEnvironmentTest
[INFO] Tests run: 8, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.005 s -- in org.tzi.use.utilcore.soil.VariableEnvironmentTest
[INFO] Running org.tzi.use.utilcore.soil.StateChangesTest
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.003 s -- in org.tzi.use.utilcore.soil.StateChangesTest
[INFO] Running org.tzi.use.utilcore.soil.VariableSetTest
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.002 s -- in org.tzi.use.utilcore.soil.VariableSetTest
[INFO] Running org.tzi.use.utilcore.soil.SymbolTableTest
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.001 s -- in org.tzi.use.utilcore.soil.SymbolTableTest
[INFO] Tests run: 0, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.328 s -- in org.tzi.use.AllTests
[INFO] Running org.tzi.use.utilcore.AllTests
[INFO] Running org.tzi.use.utilcore.AbstractBagTest
[INFO] Tests run: 6, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.002 s -- in org.tzi.use.utilcore.AbstractBagTest
[INFO] Running org.tzi.use.utilcore.ReportTest
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.001 s -- in org.tzi.use.utilcore.ReportTest
[INFO] Running org.tzi.use.utilcore.StringUtilTest
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.001 s -- in org.tzi.use.utilcore.StringUtilTest
[INFO] Running org.tzi.use.utilcore.CombinationTest
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.001 s -- in org.tzi.use.utilcore.CombinationTest
[INFO] Running org.tzi.use.utilcore.soil.VariableEnvironmentTest
[INFO] Tests run: 8, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.004 s -- in org.tzi.use.utilcore.soil.VariableEnvironmentTest
[INFO] Running org.tzi.use.utilcore.soil.StateChangesTest
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.003 s -- in org.tzi.use.utilcore.soil.StateChangesTest
[INFO] Running org.tzi.use.utilcore.soil.VariableSetTest
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.001 s -- in org.tzi.use.utilcore.soil.VariableSetTest
[INFO] Running org.tzi.use.utilcore.soil.SymbolTableTest
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0 s -- in org.tzi.use.utilcore.soil.SymbolTableTest
[INFO] Tests run: 0, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.014 s -- in org.tzi.use.utilcore.AllTests
[INFO] Running org.tzi.use.utilcore.soil.StateChangesTest
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.003 s -- in org.tzi.use.utilcore.soil.StateChangesTest
[INFO] Running org.tzi.use.utilcore.soil.AllTests
[INFO] Running org.tzi.use.utilcore.soil.VariableEnvironmentTest
[INFO] Tests run: 8, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.003 s -- in org.tzi.use.utilcore.soil.VariableEnvironmentTest
[INFO] Running org.tzi.use.utilcore.soil.StateChangesTest
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.002 s -- in org.tzi.use.utilcore.soil.StateChangesTest
[INFO] Running org.tzi.use.utilcore.soil.VariableSetTest
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.001 s -- in org.tzi.use.utilcore.soil.VariableSetTest
[INFO] Running org.tzi.use.utilcore.soil.SymbolTableTest
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0 s -- in org.tzi.use.utilcore.soil.SymbolTableTest
[INFO] Tests run: 0, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.007 s -- in org.tzi.use.utilcore.soil.AllTests
[INFO] Running org.tzi.use.utilcore.soil.SymbolTableTest
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0 s -- in org.tzi.use.utilcore.soil.SymbolTableTest
[INFO] Running org.tzi.use.utilcore.soil.VariableEnvironmentTest
[INFO] Tests run: 8, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.003 s -- in org.tzi.use.utilcore.soil.VariableEnvironmentTest
[INFO] Running org.tzi.use.utilcore.soil.VariableSetTest
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.001 s -- in org.tzi.use.utilcore.soil.VariableSetTest
[INFO] Running org.tzi.use.utilcore.StringUtilTest
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.001 s -- in org.tzi.use.utilcore.StringUtilTest
[INFO] Running org.tzi.use.utilcore.CombinationTest
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.001 s -- in org.tzi.use.utilcore.CombinationTest
[INFO] Running org.tzi.use.utilcore.AbstractBagTest
[INFO] Tests run: 6, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.002 s -- in org.tzi.use.utilcore.AbstractBagTest
[INFO] Running org.tzi.use.utilcore.ReportTest
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0 s -- in org.tzi.use.utilcore.ReportTest
[INFO] Running org.tzi.use.uml.sys.DeletionTest
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.003 s -- in org.tzi.use.uml.sys.DeletionTest
[INFO] Running org.tzi.use.uml.sys.AllTests
[INFO] Running org.tzi.use.uml.sys.DeletionTest
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.002 s -- in org.tzi.use.uml.sys.DeletionTest
[INFO] Running org.tzi.use.uml.sys.LinkTest
[INFO] Tests run: 8, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.005 s -- in org.tzi.use.uml.sys.LinkTest
[INFO] Running org.tzi.use.uml.sys.MCmdDestroyObjectsTest
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.001 s -- in org.tzi.use.uml.sys.MCmdDestroyObjectsTest
[INFO] Running org.tzi.use.uml.sys.soil.StatementEffectTest
Warning: Iteration over a non-ordered collection. Order of the result might not be as expected. (for x in Set{0}do ... end)
Warning: Iteration over a non-ordered collection. Order of the result might not be as expected. (for x in Set{0}do ... end)
[INFO] Tests run: 11, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.018 s -- in org.tzi.use.uml.sys.soil.StatementEffectTest
[INFO] Tests run: 0, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.027 s -- in org.tzi.use.uml.sys.AllTests
[INFO] Running org.tzi.use.uml.sys.soil.AllTests
[INFO] Running org.tzi.use.uml.sys.soil.StatementEffectTest
Warning: Iteration over a non-ordered collection. Order of the result might not be as expected. (for x in Set{0}do ... end)
Warning: Iteration over a non-ordered collection. Order of the result might not be as expected. (for x in Set{0}do ... end)
[INFO] Tests run: 11, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.018 s -- in org.tzi.use.uml.sys.soil.StatementEffectTest
[INFO] Tests run: 0, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.018 s -- in org.tzi.use.uml.sys.soil.AllTests
[INFO] Running org.tzi.use.uml.sys.soil.StatementEffectTest
Warning: Iteration over a non-ordered collection. Order of the result might not be as expected. (for x in Set{0}do ... end)
Warning: Iteration over a non-ordered collection. Order of the result might not be as expected. (for x in Set{0}do ... end)
[INFO] Tests run: 11, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.016 s -- in org.tzi.use.uml.sys.soil.StatementEffectTest
[INFO] Running org.tzi.use.uml.sys.LinkTest
[INFO] Tests run: 8, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.004 s -- in org.tzi.use.uml.sys.LinkTest
[INFO] Running org.tzi.use.uml.sys.MCmdDestroyObjectsTest
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.001 s -- in org.tzi.use.uml.sys.MCmdDestroyObjectsTest
[INFO] Running org.tzi.use.uml.sys.MSystemStateTest
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.284 s -- in org.tzi.use.uml.sys.MSystemStateTest
[INFO] Running org.tzi.use.uml.mm.ModelCreationTest
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.004 s -- in org.tzi.use.uml.mm.ModelCreationTest
[INFO] Running org.tzi.use.uml.mm.AllTests
[INFO] Running org.tzi.use.uml.mm.MAssociationClassTest
[INFO] Tests run: 6, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.004 s -- in org.tzi.use.uml.mm.MAssociationClassTest
[INFO] Running org.tzi.use.uml.mm.MMultiplicityTest
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0 s -- in org.tzi.use.uml.mm.MMultiplicityTest
[INFO] Running org.tzi.use.uml.mm.ModelCreationTest
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.003 s -- in org.tzi.use.uml.mm.ModelCreationTest
[INFO] Tests run: 0, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.007 s -- in org.tzi.use.uml.mm.AllTests
[INFO] Running org.tzi.use.uml.mm.MImportedModelTest
[INFO] Tests run: 55, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.060 s -- in org.tzi.use.uml.mm.MImportedModelTest
[INFO] Running org.tzi.use.uml.mm.MAssociationClassTest
[INFO] Tests run: 6, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.003 s -- in org.tzi.use.uml.mm.MAssociationClassTest
[INFO] Running org.tzi.use.uml.mm.MMultiplicityTest
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.001 s -- in org.tzi.use.uml.mm.MMultiplicityTest
[INFO] Running org.tzi.use.uml.mm.statemachines.TestProtocolStateMachine
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.004 s -- in org.tzi.use.uml.mm.statemachines.TestProtocolStateMachine
[INFO] Running org.tzi.use.uml.mm.statemachines.TestSignals
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.001 s -- in org.tzi.use.uml.mm.statemachines.TestSignals
[INFO] Running org.tzi.use.uml.AllTests
[INFO] Running org.tzi.use.uml.mm.MAssociationClassTest
[INFO] Tests run: 6, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.004 s -- in org.tzi.use.uml.mm.MAssociationClassTest
[INFO] Running org.tzi.use.uml.mm.MMultiplicityTest
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.001 s -- in org.tzi.use.uml.mm.MMultiplicityTest
[INFO] Running org.tzi.use.uml.mm.ModelCreationTest
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.003 s -- in org.tzi.use.uml.mm.ModelCreationTest
[INFO] Running org.tzi.use.uml.ocl.expr.EvaluatorTest
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.024 s -- in org.tzi.use.uml.ocl.expr.EvaluatorTest
[INFO] Running org.tzi.use.uml.ocl.expr.ExpQueryTest
[INFO] Tests run: 13, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.008 s -- in org.tzi.use.uml.ocl.expr.ExpQueryTest
[INFO] Running org.tzi.use.uml.ocl.expr.ExprNavigationTest
[INFO] Tests run: 12, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.013 s -- in org.tzi.use.uml.ocl.expr.ExprNavigationTest
[INFO] Running org.tzi.use.uml.ocl.expr.ExpStdOpTest
[INFO] Tests run: 11, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.007 s -- in org.tzi.use.uml.ocl.expr.ExpStdOpTest
[INFO] Running org.tzi.use.uml.ocl.expr.NavigationTest
[INFO] Tests run: 6, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.007 s -- in org.tzi.use.uml.ocl.expr.NavigationTest
[INFO] Running org.tzi.use.uml.ocl.type.TypeTest
[INFO] Tests run: 38, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.022 s -- in org.tzi.use.uml.ocl.type.TypeTest
[INFO] Running org.tzi.use.uml.ocl.value.ValueTest
[INFO] Tests run: 11, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.004 s -- in org.tzi.use.uml.ocl.value.ValueTest
[INFO] Running org.tzi.use.uml.sys.DeletionTest
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.006 s -- in org.tzi.use.uml.sys.DeletionTest
[INFO] Running org.tzi.use.uml.sys.LinkTest
[INFO] Tests run: 8, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.007 s -- in org.tzi.use.uml.sys.LinkTest
[INFO] Running org.tzi.use.uml.sys.MCmdDestroyObjectsTest
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.001 s -- in org.tzi.use.uml.sys.MCmdDestroyObjectsTest
[INFO] Running org.tzi.use.uml.sys.soil.StatementEffectTest
Warning: Iteration over a non-ordered collection. Order of the result might not be as expected. (for x in Set{0}do ... end)
Warning: Iteration over a non-ordered collection. Order of the result might not be as expected. (for x in Set{0}do ... end)
[INFO] Tests run: 11, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.023 s -- in org.tzi.use.uml.sys.soil.StatementEffectTest
[INFO] Tests run: 0, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.134 s -- in org.tzi.use.uml.AllTests
[INFO] Running org.tzi.use.uml.ocl.value.AllTests
[INFO] Running org.tzi.use.uml.ocl.value.ValueTest
[INFO] Tests run: 11, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.005 s -- in org.tzi.use.uml.ocl.value.ValueTest
[INFO] Tests run: 0, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.005 s -- in org.tzi.use.uml.ocl.value.AllTests
[INFO] Running org.tzi.use.uml.ocl.value.ValueTest
[INFO] Tests run: 11, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.003 s -- in org.tzi.use.uml.ocl.value.ValueTest
[INFO] Running org.tzi.use.uml.ocl.expr.AllTests
[INFO] Running org.tzi.use.uml.ocl.expr.EvaluatorTest
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.022 s -- in org.tzi.use.uml.ocl.expr.EvaluatorTest
[INFO] Running org.tzi.use.uml.ocl.expr.ExpQueryTest
[INFO] Tests run: 13, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.005 s -- in org.tzi.use.uml.ocl.expr.ExpQueryTest
[INFO] Running org.tzi.use.uml.ocl.expr.ExprNavigationTest
[INFO] Tests run: 12, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.012 s -- in org.tzi.use.uml.ocl.expr.ExprNavigationTest
[INFO] Running org.tzi.use.uml.ocl.expr.ExpStdOpTest
[INFO] Tests run: 11, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.005 s -- in org.tzi.use.uml.ocl.expr.ExpStdOpTest
[INFO] Running org.tzi.use.uml.ocl.expr.NavigationTest
[INFO] Tests run: 6, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.008 s -- in org.tzi.use.uml.ocl.expr.NavigationTest
[INFO] Tests run: 0, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.055 s -- in org.tzi.use.uml.ocl.expr.AllTests
[INFO] Running org.tzi.use.uml.ocl.expr.EvaluatorTest
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.017 s -- in org.tzi.use.uml.ocl.expr.EvaluatorTest
[INFO] Running org.tzi.use.uml.ocl.expr.ExpQueryTest
[INFO] Tests run: 13, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.007 s -- in org.tzi.use.uml.ocl.expr.ExpQueryTest
[INFO] Running org.tzi.use.uml.ocl.expr.ExpStdOpTest
[INFO] Tests run: 11, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.008 s -- in org.tzi.use.uml.ocl.expr.ExpStdOpTest
[INFO] Running org.tzi.use.uml.ocl.expr.ExprNavigationTest
[INFO] Tests run: 12, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.017 s -- in org.tzi.use.uml.ocl.expr.ExprNavigationTest
[INFO] Running org.tzi.use.uml.ocl.expr.NavigationTest
[INFO] Tests run: 6, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.012 s -- in org.tzi.use.uml.ocl.expr.NavigationTest
[INFO] Running org.tzi.use.uml.ocl.type.AllTests
[INFO] Running org.tzi.use.uml.ocl.type.TypeTest
[INFO] Tests run: 38, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.021 s -- in org.tzi.use.uml.ocl.type.TypeTest
[INFO] Tests run: 0, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.022 s -- in org.tzi.use.uml.ocl.type.AllTests
[INFO] Running org.tzi.use.uml.ocl.type.TypeTest
[INFO] Tests run: 38, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.020 s -- in org.tzi.use.uml.ocl.type.TypeTest
[INFO] Running org.tzi.use.graph.AllTests
[INFO] Running org.tzi.use.graph.GraphTest
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.001 s -- in org.tzi.use.graph.GraphTest
[INFO] Tests run: 0, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.002 s -- in org.tzi.use.graph.AllTests
[INFO] Running org.tzi.use.graph.GraphTest
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.002 s -- in org.tzi.use.graph.GraphTest
[INFO] 
[INFO] Results:
[INFO] 
[INFO] Tests run: 1273, Failures: 0, Errors: 0, Skipped: 0
[INFO] 
[INFO] 
[INFO] --- jar:3.5.0:jar (default-jar) @ use-core ---
[INFO] Building jar: /home/xoruser/msc-4/use-msc2026/use-core/target/use-core-7.5.0.jar
[INFO] 
[INFO] --- failsafe:2.22.2:integration-test (default) @ use-core ---
[INFO] 
[INFO] -------------------------------------------------------
[INFO]  T E S T S
[INFO] -------------------------------------------------------
SLF4J(W): No SLF4J providers were found.
SLF4J(W): Defaulting to no-operation (NOP) logger implementation
SLF4J(W): See https://www.slf4j.org/codes.html#noProviders for further details.
[INFO] Running org.tzi.use.OCLExpressionIT
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.129 s - in org.tzi.use.OCLExpressionIT
[INFO] 
[INFO] Results:
[INFO] 
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
[INFO] 
[INFO] 
[INFO] --- failsafe:2.22.2:verify (default) @ use-core ---
[INFO] 
[INFO] --- exec:3.5.0:exec (upstream-oracle-floor) @ use-core ---
[floor] ===== upstream-oracle floor check: use-core =====
[floor] requested profiles (reactor-wide, from the command line): [upstream-oracle]
[floor] this module's upstream-oracle profile effective: true
[floor] mode: ORACLE
[floor] allow-profiles (-Duse.floor.allowProfiles): (none)
[floor] reactor: FULL (no -pl/--projects, no -rf/--resume-from)
[floor] freshness stamp: 2026-08-20T22:59:31.181Z — reports older than this are stale and are NOT counted
[floor] surefire  use-core  classes=114 (floor 103)  methods=685  (floor 664 )  executions=1273 failures=0 errors=0 skipped=0 stale-ignored=0
[floor] failsafe  use-core  classes=1   (floor 1  )  methods=1    (floor 1   )  executions=1    failures=0 errors=0 skipped=0 stale-ignored=0
[floor] vintage-only sentinel org.tzi.use.parser.USECompilerTest: collected
[floor] wrote receipt /home/xoruser/msc-4/use-msc2026/use-core/target/upstream-oracle-floor.receipt (verdict=PASS)
[floor] PASS — use-core met every pinned floor in ORACLE mode.
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time:  02:01 min
[INFO] Finished at: 2026-08-21T06:01:30+07:00
[INFO] ------------------------------------------------------------------------
```

### 2.3 Gate script confirmation

Section 2.1/2.2 above quote raw, hand-typed `mvn verify` output. Per
`docs/port2/harness-contract.md` §0.1, "THE GATE IS A SCRIPT" — the
project's actual acceptance evidence is `scripts/upstream-oracle-gate.sh`'s
own `[gate] PASS` banner, not a hand-typed `mvn` invocation's `[floor] PASS`
alone. A task review of this closeout caught that gap; this subsection
closes it without removing the evidence above, which stays as real,
useful context (and the `[floor] PASS` lines it captured are exactly what
the gate script's own run reproduces below, just wrapped in the script's
additional freshness/tamper/partial-reactor checks).

```
$ bash scripts/upstream-oracle-gate.sh both
[gate] =================================================================
[gate] upstream-oracle acceptance gate — mode: both
[gate] reactor root: /home/xoruser/msc-4/use-msc2026
[gate] profile id (hard-coded here, not typed): upstream-oracle
[gate] git status --porcelain BEFORE:
[gate]   ?? docs/superpowers/
[gate]   (nothing above == clean)
[gate] =================================================================

[gate] ----- default : expecting mode DEFAULT in every module -----
[gate] mvn -q clean
[gate] mvn -B verify -Djava.awt.headless=true
[gate] mvn EXIT=0, log: /tmp/use-upstream-oracle-gate/default.log (7539 lines)
[gate] the floor's own words for default:
[gate]   [floor] surefire  use-core  classes=81  (floor 70 )  methods=414  (floor 393 )  executions=414  failures=0 errors=0 skipped=0 stale-ignored=0
[gate]   [floor] failsafe  use-core  classes=1   (floor 1  )  methods=1    (floor 1   )  executions=1    failures=0 errors=0 skipped=0 stale-ignored=0
[gate]   [floor] PASS — use-core met every pinned floor in DEFAULT mode.
[gate]   [floor] surefire  use-gui   classes=1   (floor 1  )  methods=1    (floor 1   )  executions=1    failures=0 errors=0 skipped=0 stale-ignored=0
[gate]   [floor] failsafe  use-gui   classes=1   (floor 1  )  methods=129  (floor 129 )  executions=129  failures=0 errors=0 skipped=0 stale-ignored=0
[gate]   [floor] PASS — use-gui met every pinned floor in DEFAULT mode.

[gate] ----- oracle : expecting mode ORACLE in every module -----
[gate] mvn -q clean
[gate] mvn -B verify -Djava.awt.headless=true -Pupstream-oracle
[gate] mvn EXIT=0, log: /tmp/use-upstream-oracle-gate/oracle.log (7867 lines)
[gate] the floor's own words for oracle:
[gate]   [floor] surefire  use-core  classes=114 (floor 103)  methods=685  (floor 664 )  executions=1273 failures=0 errors=0 skipped=0 stale-ignored=0
[gate]   [floor] failsafe  use-core  classes=1   (floor 1  )  methods=1    (floor 1   )  executions=1    failures=0 errors=0 skipped=0 stale-ignored=0
[gate]   [floor] vintage-only sentinel org.tzi.use.parser.USECompilerTest: collected
[gate]   [floor] PASS — use-core met every pinned floor in ORACLE mode.
[gate]   [floor] surefire  use-gui   classes=8   (floor 8  )  methods=17   (floor 17  )  executions=17   failures=0 errors=0 skipped=0 stale-ignored=0
[gate]   [floor] failsafe  use-gui   classes=1   (floor 1  )  methods=129  (floor 129 )  executions=129  failures=0 errors=0 skipped=0 stale-ignored=0
[gate]   [floor] vintage-only sentinel org.tzi.use.gui.views.diagrams.util.DirectedLineTest: collected
[gate]   [floor] PASS — use-gui met every pinned floor in ORACLE mode.

[gate] =================================================================
[gate] git status --porcelain AFTER:
[gate]   ?? docs/superpowers/
[gate]   (nothing above == clean; report anything you did not write, never commit it)
[gate] PASS — mode 'both': every check above held.
[gate] =================================================================
```

This is the project's canonical acceptance evidence for this review:
`[gate] PASS — mode 'both': every check above held.`

---

## 3. What this review did not re-verify

This review's scope was restoring and fixing the *ported types themselves* — `UReal`, `UInteger`,
`UBoolean`, `UString`, `SBoolean` — against the fork and the project's own audit trail. It was not a
re-examination of the differential test harness's own measurement fidelity or of the Maven build
infrastructure that runs the acceptance gate. Both of those are pre-existing, previously-identified,
harness/build-infrastructure-level concerns, and this review left them exactly as it found them:

- **The differential harness's own known-open items**, as recorded in
  `docs/port2/harness-contract.md`:
  - **D-29** (open, MAJOR) — the gate is not satisfiable by fidelity alone: a perfect port reaches
    `isStagePass(1, none())` on only 74 of 285 operations; the remaining 211 are refused by clause 3
    (119, by design) or clause 2 (92, for `BOTH_THREW`/`HARNESS_ERROR`/`UNMEASURABLE` rows a faithful
    port cannot avoid), so on those 92 an infidelity can change the rows and counts without changing
    the pass bit.
  - **D-30** (open, MAJOR) — input-domain coverage is unmeasured: `distinctReferenceValues()` measures
    the codomain reached, but its dual (how much of the input domain was reached) is computed nowhere,
    published nowhere, and gated nowhere. Decision H14 (2026-08-17) called for building this measure;
    it remains unimplemented (design in `docs/port2/h14-coverage-design.md`).
  - **D-52** (open, MAJOR) — the "escape hatch" for stating a ported value's observed Java type moved
    from a `String` parameter to an `Object` whose class the harness reads via
    `getClass().getName()`; the harness believes whatever object it is handed, which is not checkable
    by the harness itself, and drives the type-mismatch count to exactly 0 in a case measured to
    contain a real 401-row, 9-operation wrong-class defect.

  These three are named in the review's brief as the harness's own open items; they are not
  re-examined here.

- **The Maven-gate hardening items**, as recorded in `docs/port2/upstream-oracle-gate-round12.md`:
  - **H-01** (MINOR) — `scripts/upstream-oracle-gate.sh:189` contains unescaped backticks inside a
    double-quoted string, so bash *executes* `mvn test -Pupstream-oracle-typo` every time the G-04
    announce-count check fails; it cannot turn a red gate green, but it launches an unrequested Maven
    build mid-gate-run and replaces the intended diagnostic with a nested build log.
  - **H-02** (MINOR) — `docs/port2/gate-threat-model.md` §3's residual R-4 describes a route
    (`-Dexec.outputFile='${exec.outputFile}'`) that Maven 3.9.16 itself refuses outright with a
    recursive-expression-cycle error before any plugin runs; the route is documented as open but is in
    fact already closed.
  - **H-03** (MINOR) — an accident route in neither the gate's threat list nor its residual list: a
    background IDE Java language server sharing the same checkout can write into
    `use-core/target/classes` mid-build (observed once, produced a truncated `.class` file); `target/`
    is git-ignored, so the wrapper's `git status` check cannot see this class of interference.

  All three are pre-existing MINOR findings against the gate script and its threat model, not against
  the ported types. They are named here, per this review's brief, and not re-examined.

This review's own gate run (§2 above) did not attempt to re-measure D-29/D-30/D-52's open figures, and
did not re-run or re-audit `scripts/upstream-oracle-gate.sh` itself (§2.1's `mvn verify` /
`mvn verify -Pupstream-oracle` were run directly, per this review's own Step 1 instructions, rather
than through the wrapper script) — so H-01/H-02/H-03, which are specifically about that script, are
unconfirmed and unrefuted by anything run here.
