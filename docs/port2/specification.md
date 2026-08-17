# Port specification — uncertainty extension onto USE 7.5.0

Assembled from `docs/port2/spec-parts/10..20-*`. This is a **checklist that S3–S8 are measured
against**, not a narrative. Every row cites a historical file+symbol or the shell command that
reproduces it. `UNVERIFIABLE` means exactly that — it is never smoothed into a claim.

Path aliases used throughout:

| Alias | Absolute path |
|---|---|
| `F/` | `/home/xoruser/msc-4/use-msc2026/.git/reference-repositories/uncertainty/USE-Uncertainty/src/main/org/tzi/use/` |
| `FT/` | `…/USE-Uncertainty/src/test/org/tzi/use/` |
| `T/` | `/home/xoruser/msc-4/use-msc2026/use-core/src/main/java/org/tzi/use/` |
| `TT/` | `/home/xoruser/msc-4/use-msc2026/use-core/src/test/java/org/tzi/use/` |
| `UDT/` | `…/reference-repositories/uncertainty/uDataTypes/Libraries/Java/src/uDataTypes/` |
| `G/` | `/home/xoruser/msc-4/use-msc2026/use-core/src/main/resources/grammars/` |

Reference repositories are **read-only references, never build inputs**. Nothing in this document
derives from `origin/main`.

> **Citation convention (corrected 2026-08-17; ambiguity closed 2026-08-17, second pass).**
> A citation prefixed with an alias resolves in that tree and nowhere else.
>
> `audit-02-specification.md` §1.4 raised this as the document's one MAJOR editorial defect: **163
> of 218 `File.ext:NN` citations were bare basenames with no alias**, and 40+ of them name a file
> that exists in *both* the historical fork and the 7.5.0 tree with **different content at the cited
> line** — including `TypeTest.java`, on which blocking decision **B5** turns.
>
> **State now, re-measured mechanically over this file** (walk `F/ FT/ T/ TT/ UDT/ G/`, index every
> basename, then classify every `File.ext:NN` token):
>
> ```
> form-A tokens: 222 | alias-or-full-path: 114 | bare, resolves in exactly one tree: 91
>                    | bare AND ambiguous: 10 | bare, outside every alias tree: 7
> ```
>
> * **Every ambiguous citation now carries an alias, except ten that adjacent prose already pins
>   explicitly** — the eleven `conformsTo` bodies at §3.1 (the line above the block reads "All eleven
>   files are in the **`F/`** tree"), §3.5's `7.5.0 Type.java:31 — fork Type.java:33` (each side
>   labelled inline), §2.1's `StandardOperationsBoolean.java:87-88` (stated as "both trees", and
>   verified identical at those lines in both), and one `grep -rn … F/` line in §8.2 whose scope is
>   in the command.
> * **The 91 remaining bare basenames were each checked, not assumed: every one names a file that
>   exists in exactly one of the six alias trees**, so it cannot resolve wrongly. Most are the
>   fork-only `U*`/`SBoolean*` classes and the `FT/` uncertainty tests.
> * **The 7 outside every alias tree** are `MavenLayeredArchitectureTest.java` (`use-gui`),
>   `build.xml` / `pom.xml`, and the three `use-gui/src/it/resources/testfiles/shell/…` fixtures of
>   **B4** — each of which is given as a full repository-relative path at its first mention in the
>   same section.
>
> **Rule for a reader:** an aliased citation is authoritative. A bare basename is safe by
> construction (one tree only) *unless* it is one of the ten above, where the surrounding sentence
> names the tree. Nothing in this document now requires guessing which tree a citation means.

---

# STANDING CONSTRAINTS — read before §0; these bind every stage S3–S8

*(Deliberately unnumbered so that every existing "§0 / §1 / §9" cross-reference in this document
still resolves.)*

Three facts that are not decisions, are not negotiable, and were each recorded after a document or
a claim in this file was found to be wrong. They are placed ahead of §0 because S3–S8 are executed
from here and every one of the three has already misdirected an earlier reading.

### C1. There are TWO acceptance commands, both `mvn verify` and both floor-checked; the S0 baseline was 143

*(Heading corrected 2026-08-17. It read "~~The acceptance command is `mvn -B verify -Djava.awt.headless=true`,
and the baseline is 143~~" — one command, and a baseline superseded twice. The amendment below was
already here; the heading contradicted it, which is how §9 came to restate the single-command rule
2 500 lines later — static-review defect D-04.)*

> **AMENDED 2026-08-17 by decision B3 — from S3 onward there are TWO acceptance commands, and a
> stage is not accepted until both are green:**
>
> ```bash
> scripts/upstream-oracle-gate.sh   # THE gate: runs both of the commands below and checks the floor
> ```
>
> ```bash
> mvn -B verify -Djava.awt.headless=true                     # default: 11 classes / 211 methods today
> mvn -B verify -Pupstream-oracle -Djava.awt.headless=true    # + upstream's own JUnit 3/4 tree, unedited
> ```
>
> *(Figures and wrapper updated 2026-08-17, round 11 — defects F-01/F-02/F-04; `harness-contract.md`
> §0.1 is normative. Hand-typing `-P` is not the gate: Maven only warns on a mistyped id.)*
>
> The second is the one that makes ground rule 4 ("never edit an upstream test") enforceable by the
> suite instead of by diff review, and it turns S10's non-regression step from a formality into a
> **498**-method check against assertions upstream authored (**465** of which can fail — F-04). Measured both ways, with the counting
> command and the raw output, in `upstream-oracle-profile.md`. Everything C1 says below about
> `mvn test` versus `mvn verify` is unchanged and still applies to both invocations.

`mvn test` is **not** the gate. The reactor also configures `maven-failsafe-plugin`, which
contributes 130 integration tests that `mvn test` never touches:

| Tier | `use-core` | `use-gui` | total |
|---|---|---|---|
| surefire (`mvn test`) | 12 | 1 | **13** |
| failsafe (`mvn verify` only) — `OCLExpressionIT` 1, `ShellIT` 129 | 1 | 129 | **130** |
| **true baseline at `30d480db`** | 13 | 130 | **143** |

Established and pasted in `stage-00-baseline.md` §2 ("Correction — `mvn test` is the wrong gate"),
commit `8789e035`. Earlier drafts of this document stated the baseline as "13 methods / 3 classes"
throughout; every such figure has been corrected in place below. The 130 failsafe tests are the
port's **most relevant** oracle — `OCLExpressionIT` is an OCL-expression test, and `ShellIT` drives
the `.use` fixtures that make **B4** a live risk — and they were outside every acceptance gate as
originally written.

Post-S1 reference points, for delta arithmetic: `mvn -B verify` = 28 + 130 = **158**; after the S1
post-fix work (commit `cf9d2f45`) = 39 + 130 = **169**.

### C2. "Full suite green" is close to vacuous — **12 of the 13 baseline methods contain no assertion**

Not "11 of them are ArchUnit cycle checks". They contain **no assertion at all**:

```bash
grep -n "\.evaluate(\|\.check(" use-core/src/test/java/org/tzi/use/architecture/MavenCyclicDependenciesCoreTest.java \
                               use-gui/src/test/java/org/tzi/use/architecture/MavenLayeredArchitectureTest.java
grep -c "assert" use-core/src/test/java/org/tzi/use/architecture/MavenCyclicDependenciesCoreTest.java \
                 use-gui/src/test/java/org/tzi/use/architecture/MavenLayeredArchitectureTest.java
```
```
MavenCyclicDependenciesCoreTest.java:175:                .evaluate(classes);
MavenLayeredArchitectureTest.java:50:        EvaluationResult result = rule.evaluate(classes);
MavenCyclicDependenciesCoreTest.java:0
MavenLayeredArchitectureTest.java:0
```

ArchUnit's `.evaluate(classes)` returns an `EvaluationResult`; only `.check(classes)` throws. Both
classes call `.evaluate`, print the count, and write a report file. Proof that this is not
theoretical: **11 of those tests pass while the report they write reads `Cycle count: 55`**
(`tail -3 docs/archunit-results/cycles-current-failure-report.txt`).

Of the 13 surefire baseline methods (11 `MavenCyclicDependenciesCoreTest` + 1
`MavenLayeredArchitectureTest` + 1 `ModelAPITest`), exactly **one — `ModelAPITest`, 6 assertions —
can fail**, and it touches nothing in the uncertainty subsystem. This is the true premise of **B3**
and it makes B3 *stronger*, not weaker.

### C3. The differential harness cannot see the type layer or `uDataTypes` — by design

`HistoricalOracle` resolves every historical class as `VALUE_PKG + simpleName` where
`VALUE_PKG = "org.tzi.use.uml.ocl.value."`
(`TT/uncertainty/differential/HistoricalOracle.java:123`, declared as a scope limit at `:71-73` and
`:113-122`). Therefore:

* **`org.tzi.use.uml.ocl.type.*` is unreachable.** The entire type-lattice port — §3's conformance
  grid, `allSupertypes()`, the predicate battery, `TypeFactory`, and blocking decisions **B5**,
  **B8** and **B11** — is **outside the instrument**.
* **`uDataTypes.*` is unreachable.** It is isolated so that the value classes resolve against it,
  but no operation can be named on it and none is isolation-checked. **B1** is outside the
  instrument.
* Only the eight `MARSHALLABLE_RECEIVERS` (`:127-129`) can be receivers; `SBooleanValue` is not
  among them, so all **39** SBoolean operations report `UNSUPPORTED`. **B2** is outside the
  instrument.

**No stage S3–S7 may describe type-registration work, `uDataTypes` vendoring, or SBoolean as
"differentially verified".** Widening the harness is a change to `HistoricalOracle.load(String)`,
not a configuration knob. Whatever evidence those areas get must come from somewhere else — the
revived upstream oracle under **B3**, or new tests written for the purpose — and must say so.

---

# 0. BLOCKING DECISIONS — read this section first

Twelve decisions a human must make **before S3 starts**. Each is repeated in place with full
evidence; this list is written to stand alone. Nothing here is a judgement call the port may take
unilaterally.

## 0.0 DECIDED — 2026-08-17

The user has decided **B2, B3 and B7**. All three are binding and none is re-litigable. The rows
below are also marked in place in the table; the original options, recommendations and evidence are
left standing untouched, because the point of this record is that these were *considered* choices and
a reader must be able to see what was weighed.

| # | Decided | Chosen | Recommendation that was NOT taken | Effect on scope |
|---|---|---|---|---|
| **B3** | 2026-08-17 | **(b)** — a `-Pupstream-oracle` Maven profile adding `junit-vintage-engine` 5.7.0 at test scope to `use-core` and `use-gui`, no test file touched. From S3 onward **every** stage's acceptance runs `mvn -B verify -Djava.awt.headless=true` **and** `mvn -B verify -Pupstream-oracle -Djava.awt.headless=true`. | — (the recommendation **was** taken) | Built and measured: `docs/port2/upstream-oracle-profile.md`. Default build 11 classes / **211** methods; under the profile 51 classes / **498** distinct methods, **0 failures, 0 errors**. Since round 11 the gate is the committed invocation `scripts/upstream-oracle-gate.sh`, not a hand-typed `-P` (defect F-02). |
| **B7** | 2026-08-17 | **FIX** the historical defects, documenting each one. | **Bug-for-bug reproduction was the recorded recommendation and was NOT taken.** | Expands scope. Each of the 33 behaviour-changing ledger rows in §7.2 now needs a fix, a written justification, and a recorded print-output delta where one exists. The differential harness's `AcceptedDegenerateOperations`/`AcceptedThrowPairs` sign-off mechanisms are how a deliberate deviation from the reference stays visible in the report. |
| **B2** | 2026-08-17 | **3** — **FULL PORT of `SBoolean`, all 39 operations.** | **Option 2 (skeleton — keep `SBooleanType` only) was the recorded recommendation and was NOT taken.** | Expands scope. §2.5's 39-operation table becomes a port target rather than a reference, and `StandardOperationsSBoolean`'s 1502 lines enter the build. Note the standing fact that motivated the skeleton recommendation is unchanged and is now a *risk to be managed rather than avoided*: **zero** of those 1502 lines is covered by any fork test, so the 39 operations arrive with no upstream oracle behind them and the differential harness is the only instrument that can judge them. |

**H14** (build an input-domain coverage measure) was decided in the same round; it is recorded in
`foundation-verdict.md` §3, which is where the H-numbered items live.

| # | Decision | Options | Recommendation | Evidence | Blocks |
|---|---|---|---|---|---|
| **B1** | **How `uDataTypes` reaches the product classpath.** It is on **no** Maven repository under any coordinates (`fc:uDataTypes.UReal` → 0 hits on Central; `repo1/{es/uma/lcc/atenea,uDataTypes,atenearesearchgroup}/` → 404,404,404), has no `pom.xml`/`build.gradle` (so JitPack is out), and the 2021 jar has **no `META-INF`** hence no `Automatic-Module-Name`. | **A1** vendor 2023 MIT source keeping package `uDataTypes`; **A2** vendor relocated to `org.tzi.use.uncertainty.udatatypes`; **B** `mvn install:install-file`; **C** shade the jar; **D** reimplement | **A2**, but **on re-argued grounds** — see the correction in B1a below | §4.6; `15-upstream-delta.md` §7; `18-refutation-delta.md` F2, F4 | every `use-core` main-source compile of the 7 files that `import uDataTypes.*` |
| **B1a** | **Correction to B1's stated justification.** §15 selects A2 over A1 because "the harness uses a plain parent-first `URLClassLoader` (`TT/uncertainty/differential/UValue.java:13-16`)". That citation is **empty prose** (277 lines, no `loadClass` anywhere), and the real loader `TT/uncertainty/differential/IsolatedJarClassLoader.java:51-52,80-83` already isolates the `uDataTypes.` prefix **parent-last**, asserted by `TT/uncertainty/differential/HistoricalOracleIsolationTest.java:70-71` (**corrected** from `:69-70`; `:71` is the `uDataTypes.UReal` assertion, `:70` the `org.tzi.use.` one, `:69` a message string — audit-02 F4/M7). The repository has also **measured** that §15's proposed alternative remedy (platform-parented `URLClassLoader`) does **not** work under JPMS here (`TT/uncertainty/differential/IsolatedJarClassLoader.java:16-26`; `stage-01.md` §3). | keep A2 on defence-in-depth grounds; or re-open A1 | keep **A2**, and delete the refuted premise from the record | `18-refutation-delta.md` F2; `stage-01.md` §3 | B1 |
| **B2** ✅ **DECIDED 2026-08-17: option 3, FULL PORT (recommendation 2 not taken)** | **SBoolean scope.** No U-type *behaviour* touches SBoolean, but `UBooleanType` and `BooleanType` declare it a **supertype** and answer `isKindOfSBoolean() == true`, which drags **21 unshadowed** SBoolean operations into reach on `UBoolean`/`Boolean` receivers (`UBoolean(true,0.7).min(UBoolean(true,0.3))` is legal OCL returning an `SBoolean`). Zero of `StandardOperationsSBoolean`'s 1502 lines is covered by any fork test. | **1** full omission (also strip `isKindOfSBoolean` from `Type`/`TypeImpl`/`MClassifierImpl`/`VoidType`, the SBoolean clauses in `UBooleanType`/`BooleanType`, and 4 lines of `StandardOperationsAny`); **2** skeleton — keep `SBooleanType` only; **3** full port | **2 (skeleton)** — cheapest way to keep the fork's *type system* bit-identical (`FT/uml/ocl/type/TypeTest.java:111-112,123-124` assert it) while paying none of the 1502-line registry cost; the operation leak lives entirely in the registry | §8.2; `19-open-questions.md` Q2 | §1 rows for `SBoolean*`, §2.5, §3, §4.4 |
| **B3** ✅ **DECIDED 2026-08-17: option (b), the profile — built, see `upstream-oracle-profile.md`** | **`junit-vintage-engine`.** The 7.5.0 reactor has none, so **38 of 41** `*Test.java` never execute. The surefire baseline is 13 methods / 3 classes — and **12 of those 13 contain no assertion at all** (C2): the ArchUnit tests call `.evaluate()`, not `.check()`, so 11 pass while the report they write reads `Cycle count: 55`. **One** assertion-bearing method (`ModelAPITest`) survives, touching nothing uncertain. The full gate is 143 (C1), but the extra 130 are failsafe integration tests, not the unit oracles the port needs. A probe adding vintage 5.7.0 test-scope to `use-core`+`use-gui` — **no test file touched** — produced **43 classes / 300 methods, 0 failures** at `b7aaa99c`, re-measured **45 classes / 315 methods, 0 failures** at `8789e035` (the +2/+15 is S1's own tests). Without it, "full suite green" is a **near-vacuous** S3–S7 gate and ground rule 4 has no automatic signal. | **(a)** in the product build; **(b)** in a `-Pupstream-oracle` profile | **(b)**, run as part of every stage's acceptance, **in addition to** `mvn -B verify -Djava.awt.headless=true` | `stage-00-baseline.md` §3–§4 (both probes); C1, C2 above | the acceptance gate of S3, S4, S5, S6, S7, S10 |
| **B4** | **The `'equals'` keyword.** `identicalExpression` (`F/parser/base/OCLBase.gpart:124-135`) makes `equals` an **implicit ANTLR token**, reserved across OCL, USE, SOIL, ASSL, TestSuite and the shell. This is a **confirmed live collision** with three upstream fixtures. | **1** drop `identicalExpression`, register `Op_identical` under a non-colliding name or reuse `=`; **2** keep the rule behind a semantic predicate on token *text* so `equals` stays `IDENT`; **3** accept the break and amend the three fixtures | **1**, else **2**. **Not 3.** | §5.5; `13-grammar.md` §13.5.2 — `use-gui/src/it/resources/testfiles/shell/t098.use:11`, `use-gui/src/it/resources/testfiles/shell/imports/t133_import_date.use:29`, `use-gui/src/it/resources/testfiles/shell/imports/t133_import_datetime.use:12` (paths **corrected** — the `shell/` segment was missing; audit-02 F1/M1-M2) | grammar port, `StandardOperationsAny` port |
| **B5** | **`TypeTest#testSupertype` conflict.** The moment `Real ≤ UReal`, `Boolean ≤ UBoolean`, `String ≤ UString`, `Integer ≤ UInteger` enter `allSupertypes()`, **10 of the 12 assertions in upstream's own untouched `testSupertype`** become false. This cannot be fixed by moving tests to a new class — it is a lattice design question. | **1** adopt the fork's lattice and handle the upstream breakage explicitly; **2** keep uncertain types out of the crisp types' supertype closure (conformance one-way only) | **1** — option 2 breaks `getLeastCommonSupertype`, which is what drives overload resolution | **Independently re-derived by audit-02 §2 (commit `3cb92468`): CONFIRMED exactly — `TT/uml/ocl/type/TypeTest.java:135-228` holds exactly 12 `assertEquals`; under the fork lattice OclAny and Enum survive, 4 crisp-type and 6 collection assertions break. 10 break, 2 survive.** §3.1, §6.3; `14-historical-tests.md` §3.3, G7 | S3 type-lattice landing, and interacts with **B3** (under (b), this failure becomes visible) |
| **B6** | **`UndefinedValue` printed form.** 7.5.0 prints `null`; the fork prints `Undefined` (upstream commit `72ab8fd7`, 2019-06-27 "changed Undefined to null"). The historical corpus contains **79 entries** expecting `-> Undefined : OclVoid`. This is a whole-suite systematic offset, not a one-off. | **1** normalise in the harness; **2** rewrite the 79 corpus lines; **3** revert `UndefinedValue` | **1** — "the port prints `null` where the oracle prints `Undefined`" is a *correct* port, not a regression | **audit-02: CONFIRMED — exactly 79 (`UBooleanExpression.in` 16 + `UIntegerExpression.in` 38 + `URealExpression.in` 25 + `UCollectionOperations.in` 0), stable with or without the `-> ` prefix.** §4.1; `15-upstream-delta.md` §1(d); `14-historical-tests.md` §5 | every corpus-driven S4–S7 comparison |
| **B7** ✅ **DECIDED 2026-08-17: FIX and document each row (bug-for-bug recommendation not taken). THE PER-ROW PLAN IS [`b7-fix-plan.md`](b7-fix-plan.md), WHICH SUPERSEDES EVERY `DEFER` IN `16-modernization-ledger.md`** | **Bug-for-bug vs. fix: 33 BEHAVIOUR-CHANGING ledger rows.** The fork carries defects that are *unobserved* by its own tests (`UStringValue.equals` is constant `false`, breaking reflexivity; `SBooleanValue.compareTo` returns `0`; `UIntegerValue.hashCode` collapses to `0` whenever `u == 0`). Fixing any of them changes `Set`/`Bag` membership and therefore the **printed output** the `.in` fixtures assert on. | per-row: reproduce, or fix and record | decide **as one policy** first, then per-row; §7.2 lists all 33 | §7; `16-modernization-ledger.md` Tables A+B | S4–S7 fidelity verdicts |
| **B8** | **`Op_number_sqrt` / `Op_number_pow` shadowing.** 7.5.0 **added** these (`T/uml/ocl/expr/operations/StandardOperationsNumber.java:848`, `:802`, registered `:32`, `:31`); the fork has neither. Their `matches` is `isKindOfNumber(EXCLUDE_VOID)`, which `URealType`/`UIntegerType` answer `true`. Registered **before** the uncertainty registries ⇒ `UReal(4,2).sqrt()` resolves to `Op_number_sqrt`, types as `Integer`, then `ClassCastException`. | **1** tighten `Op_number_sqrt.matches` to exclude `UncertainType`; **2** register uncertainty ops first (**changes `Integer+Integer` typing — see §2.6**); **3** teach `Op_number_sqrt` about `UReal` | **1** | **audit-02: CONFIRMED, every link in the chain — the two ops exist in 7.5.0 and not in the fork; `matches` is `isKindOfNumber(EXCLUDE_VOID)`; `URealType`/`UIntegerType` return `true`; `F/uml/ocl/expr/operations/OpGeneric.java:88` registers Number before the five uncertainty registries at `:93-97`; `F/uml/ocl/expr/ExpStdOp.java:129-134` takes the first match over an insertion-ordered `ArrayListMultimap`; `eval` casts to `RealValue` and `URealValue extends UncertainValue`, so ClassCastException.** §2.6; `20-ops-UReal.md` §4.4 | any `sqrt`/`pow` result being trusted |
| **B9** | **`ExpQuery` items 7+8 — `exists`/`forAll` over uncertain predicates.** §12 declares its `ExpQuery` edit "purely additive" while keeping 7.5.0's `evalExistsOrForAll`. The refuter shows that then `assertKindOfUBoolean()` is **added and never called** and `exists`/`forAll` over a `UBoolean` predicate is **silently unported** — yet `ExpQueryUncertaintyTest#testForAllColA` pins `UBoolean(true, 0.999968314)`. Items 7+8 and the `ExpExists`/`ExpForAll` assertion swap are **one atomic unit: take both or neither**. | **1** take both (loses short-circuiting and the `isEnableEvalTree` fast path); **2** take neither and record `exists`/`forAll` as out of scope | decide explicitly; do **not** ship the "additive" middle | §2.4; `17-refutation-classification.md` R6; `12-expressions.md` §3.3.2 | `ExpQueryUncertaintyTest` parity |
| **B10** | **`ExpDefSBoolean` + `ASTSBooleanDefExpression`.** Unreachable dead code (sole `new ExpDefSBoolean` is at `F/parser/ocl/ASTSBooleanDefExpression.java:25`; that AST class is **never instantiated** anywhere, and no grammar produces it), with an **inverted** type guard, a missing `ctx.exit`, and an `eval` that can return Java `null`. | drop, or port with the three defects documented | **drop** — it also saves `visitDefSBoolean` in `ExpressionVisitor` and both visitors | §8.1; `12-expressions.md` §2; `19-open-questions.md` Q1 | `ExpressionVisitor` method count (7 vs 8) |
| **B11** | **`UnlimitedNatural` lattice inconsistency.** `UnlimitedNatural.conformsTo(UInteger)` and `…(UReal)` are `true` (predicate-driven, `F/uml/ocl/type/UnlimitedNaturalType.java:61-63`, identical in 7.5.0), yet `UnlimitedNatural.allSupertypes()` was **not** extended ⇒ `UnlimitedNatural.getLeastCommonSupertype(UInteger)` returns `OclAny`. The same shape of defect **pre-exists upstream** for `Integer`/`UnlimitedNatural`. | reproduce bit-for-bit, or fix | **reproduce**, plus a regression test pinning `LCS(UnlimitedNatural, UInteger) == OclAny` so the deviation is visible | **audit-02: CONFIRMED including the pre-exists-upstream clause — the fork's `UnlimitedNaturalType.java` differs from 7.5.0's by a two-line `$Id$` comment only, and upstream `IntegerType` already has the identical shape.** §3.4; `11-types.md` §1.8-1 | S3 lattice |
| **B12** | **Corpus harness placement and global state.** `USECompilerUncertaintyTest` resolves its four `.in` files from `System.getProperty("user.dir") + "/src/test/org/tzi/use/parser/uncertainty"` — under Maven the module root is `use-core/`, so `listFiles` returns `null`; **or**, if an empty directory exists, the loop runs zero times and the test **passes vacuously**. It also sets the process-global `Options.explicitVariableDeclarations = false` and never restores it, which is why the JUnit-3 `AllTests` **suite ordering is load-bearing**. | move fixtures to `use-core/src/test/resources/…` + classpath lookup + `assertTrue(files.length > 0)`; and either pin ordering or make the global write self-restoring | do both; note the non-empty assertion converts a previously-vacuous pass into a failure — that is itself a behaviour change and must be recorded as one | §7.2 (CF-5, CF-8, M-45); `16-modernization-ledger.md` | corpus-driven S4–S7 |

### Audit of this document — what an independent refuter did to §0

`docs/port2/audit-02-specification.md` (commit `3cb92468`) adjudicated **185 distinct citations**
against source, covering **100 % of §0, §1 and §7** plus §3.1/§3.2, §3.5, §5.5/§5.6, §8.1 and a
sweep of §2/§4/§6/§10. Verdict **SOUND_WITH_CAVEATS**, hit rate **175/185 = 94.6 %** (88.6 % under
the strict reading that counts §3.1's cite-the-method convention as 11 misses — now documented in
place at §3.1).

**No blocking decision was refuted.** Four of the twelve were re-derived **from source, by a second
party, without reusing this document's method**, and all four came back CONFIRMED. That is not a
review comment on this document; it is an independent measurement, and it is why these four rows may
be executed without re-deriving them:

| Decision | The claim that was checked | Outcome |
|---|---|---|
| **B5** | "10 of the 12 assertions in upstream's untouched `testSupertype` become false" | **CONFIRMED, exactly 10 of 12.** `TT/uml/ocl/type/TypeTest.java:135-228` holds exactly 12 `assertEquals`; under the fork lattice `OclAny` and `Enum` survive, 4 crisp-type and 6 collection assertions break — the collection ones because `CollectionType`/`SetType.allSupertypes()` *derive* from the element type |
| **B6** | "the historical corpus contains **79** entries expecting `-> Undefined : OclVoid`" | **CONFIRMED, exactly 79** — `UBooleanExpression.in` 16 + `UIntegerExpression.in` 38 + `URealExpression.in` 25 + `UCollectionOperations.in` 0, stable with or without the `-> ` prefix |
| **B8** | the `Op_number_sqrt`/`Op_number_pow` shadowing chain | **CONFIRMED, every link** — the two ops exist in 7.5.0 and not in the fork; `matches` is `isKindOfNumber(EXCLUDE_VOID)`; `URealType`/`UIntegerType` answer `true`; Number is registered before the five uncertainty registries; `ExpStdOp` takes the **first** match over an insertion-ordered `ArrayListMultimap`; `eval` casts to `RealValue` while `URealValue extends UncertainValue` ⇒ `ClassCastException` |
| **B11** | the `UnlimitedNatural` lattice inconsistency, **including** "the same shape pre-exists upstream" | **CONFIRMED including the upstream clause** — the fork's `UnlimitedNaturalType.java` differs from 7.5.0's by a two-line `$Id$` comment only, and upstream `IntegerType` already carries the identical defect. So reproducing it is not importing a fork bug; it is leaving an upstream one alone |

Each row above also carries this annotation inline in the §0 table, so a reader who arrives at a
decision does not have to find this paragraph first.

The inventory was reconstructed twice by methods
that do not reuse this document's keyword filter — a reference graph over the 33 new fork classes,
and an API-gap test over every static call those classes make into upstream — and came back
**complete**, not merely correct.

All ten misses are corrected in place above and are marked "**corrected**" where they occur. Four
were one wrong path stated twice (**B4**'s missing `shell/` segment), five were one-line offsets
(**B1a**, `11-types.md:747`, `URealType:9`, `ExpDefSBoolean:16-17`), one was an over-tight adjective
(§4.3's `GenerateHTMLExpressionVisitor` list). §7 — the 72-token ledger a human executes row by row
— scored **72/72 on file:line**; its only two defects were subsection row counts, also corrected.

**The one MAJOR finding was editorial and is now closed**: 75 % of citations were bare basenames
with no tree alias, 40+ of them resolving in *both* trees with different content at the cited line.
A second pass aliased every ambiguous one and re-measured the whole file mechanically — see the
citation convention note at the head of this document for the classification, and **R0b** in §10.1
for the closure record. Ten ambiguous citations are deliberately left bare because the adjacent
sentence names the tree outright; nothing in this document now requires guessing.

**Decisions deliberately NOT escalated** (recorded, port may take them): `mkUReal()` return type
(`Type` vs `URealType` — narrowing is source-compatible with all 27 fork call sites);
`UIntegerType()` constructor visibility (fork `public`, siblings not — note `URealType()` is
`protected`, not package-private, correcting `11-types.md:711` — **corrected** from `:747`, which is
a blank line; the fuller and already-correct treatment is `11-types.md:70-76`, audit-02 M5); the `allSupertypes` `this` vs
`TypeFactory.mkX()` idiom; the fork's `testIsTypeOfUBooloean`/`testIsTypeOfSBooloean` typos.

---

# 1. Inventory — every uncertainty-touching class

Ground truth for the "edit" set was rebuilt independently, not taken from any section's list:

```bash
cd /home/xoruser/msc-4/use-msc2026
FORK=.git/reference-repositories/uncertainty/USE-Uncertainty/src/main/org/tzi/use
TGT=use-core/src/main/java/org/tzi/use
(cd $FORK && find . -name '*.java' | sed 's|^\./||' | sort) > /tmp/fork.txt
(cd $TGT  && find . -name '*.java' | sed 's|^\./||' | sort) > /tmp/tgt.txt
comm -12 /tmp/fork.txt /tmp/tgt.txt > /tmp/both.txt      # 539 common files
while read f; do
  n=$(diff --strip-trailing-cr -u "$TGT/$f" "$FORK/$f" | grep -E '^\+' | grep -vE '^\+\+\+' \
      | grep -cE 'UBoolean|UReal|UInteger|UString|SBoolean|Uncertain|uEquals|uDistinct|uSelect|uIncludes|uExcludes|uCountC|uncertainty|Uncertainty|isUReal|isUInteger|toBooleanC|uDataTypes')
  [ "$n" -gt 0 ] && echo "$n  $f"
done < /tmp/both.txt | sort -rn
# 24 files.  Two more are invisible to the keyword filter and were checked by hand:
diff -u --strip-trailing-cr $TGT/util/MathUtil.java        $FORK/util/MathUtil.java
diff -u --strip-trailing-cr $TGT/uml/ocl/value/RealValue.java $FORK/uml/ocl/value/RealValue.java
# new files:
comm -23 /tmp/fork.txt /tmp/tgt.txt
```

**Totals: 33 new files (31 if B10 is taken), 26 upstream `.java` edits, 1 grammar resource edit,
1 upstream test-file decision.**

## 1.1 New files — `org/tzi/use/uml/ocl/value/` (7)

Verified absent from 7.5.0 by the `comm -23` above. (`10-values.md:37-38` says "`ls T/uml/ocl/value/`
lists no `U*Value.java`" — **false as literally written**, `UndefinedValue.java` and
`UnlimitedNaturalValue.java` match that glob; the substantive claim is correct. Refuter R10a
adopted.)

| Target path | Historical source | Size | Contract / behaviour |
|---|---|---|---|
| `T/uml/ocl/value/UncertainValue.java` | `F/uml/ocl/value/UncertainValue.java` | 47 L | **new file.** `public abstract class UncertainValue extends Value`. Adds `abstract UncertainBooleanValue uEquals(Value)` (`:28`) and `uDistinct(Value)` = `uEquals(other).not()` (`:37`). Supplies C1 (ctor chain) only; C2–C5 stay abstract. No delegate. |
| `T/uml/ocl/value/UncertainBooleanValue.java` | same | 13 L | **new file.** `abstract … extends UncertainValue`; single member `abstract UncertainBooleanValue not()` (`:11`) — the member that makes `uDistinct` work for both `UBooleanValue` and `SBooleanValue`. |
| `T/uml/ocl/value/UBooleanValue.java` | same | 351 L | **new file.** Wraps `uDataTypes.UBoolean`. Canonical form `(b=true, c=P(true))` enforced by the delegate on **every getter** (`UDT/UBoolean.java:11-13,59-67`) ⇒ `value()` is *always* `true`. `TRUE`=(true,1) `:27`, `FALSE`=(true,0) `:31`. **Package-private** ctor `:42`, `valueOf(UBoolean)` `:74` and `getuBoolean()` `:148` — used from `SBooleanValue` `:78,:192,:463`, so both classes **must** land in the same package. |
| `T/uml/ocl/value/UIntegerValue.java` | same | 223 L | **new file.** Wraps `uDataTypes.UInteger` (`private`, **not final**, `:10`). `uncertainty` absolutised by the delegate (`UDT/UInteger.java:19-21`). `getuInteger()` `:34` is **public**, publishing a mutable delegate — the "immutable value" invariant of the package is not actually enforced. |
| `T/uml/ocl/value/URealValue.java` | same | 281 L | **new file.** Wraps `uDataTypes.UReal`. `hashCode` `:56` skips the uncertainty term when `u == 0`, preserving the `1 == 1.0 == UReal(1,0)` hash bridge that `UIntegerValue` breaks. `toString` `:45` corrects `-0.0` → `0`. |
| `T/uml/ocl/value/UStringValue.java` | same | 206 L | **new file.** Wraps `uDataTypes.UString` (the only genuinely immutable delegate, `UDT/UString.java:10-11`). **No `isUString()` predicate exists anywhere** — discrimination is by `instanceof` in `valueOf` `:30-33`. `equals` `:79` is a constant `false` (B7 / §7.2 M-11). |
| `T/uml/ocl/value/SBooleanValue.java` | same | 476 L | **new file, the only `final` one.** Wraps `uDataTypes.SBoolean`. Both ctors **package-private**; the public cross-package entry point is the nested `Builder` (`:28-69`), used by `F/uml/ocl/expr/ExpConstSBoolean.java:49`. `Builder().build()` with no setters **throws** (`0+0+0 ≠ 1`). Note `FALSE` is `(0,1,0,a=1)` while the delegate's own `new SBoolean(false)` is `(0,1,0,a=0)` — the two "false" opinions are **not** `equals` (base rates differ by 1.0, tolerance 0.001, `UDT/SBoolean.java:1530`). Gated by **B2**. |

## 1.2 New files — `org/tzi/use/uml/ocl/type/` (7)

| Target path | Size | Contract / behaviour |
|---|---|---|
| `T/uml/ocl/type/UncertainType.java` | 16 L | **new file.** `abstract … extends BasicType`, one `protected UncertainType(String)`. **Pure `instanceof` tag** — used at 11 sites in 3 files: `F/uml/ocl/expr/operations/StandardOperationsCollection.java:104,169,401,474`, `F/uml/ocl/expr/operations/StandardOperationsNumber.java:351,946,1024,1101,1179`, `F/uml/ocl/expr/operations/StandardOperationsAny.java:49,199`. There is **no** `isKindOfUncertain…()` predicate on `Type`. |
| `T/uml/ocl/type/UncertainBooleanType.java` | 10 L | **new file.** Two-line body. **Zero `instanceof` sites** — exists solely to give `UBooleanType` and `SBooleanType` a shared Java parent. Collapses if B2 = option 1. |
| `T/uml/ocl/type/UIntegerType.java` | — | **new file.** `isKindOfNumber`, `isTypeOfUInteger`, `isKindOfUReal`, `isKindOfUInteger` → `true`; `allSupertypes` = `{this, UReal, OclAny}`; `conformsTo` = `isTypeOfUInteger \|\| isTypeOfUReal \|\| isTypeOfOclAny`. Fork ctor is **`public`** (`:14`) — the only one. |
| `T/uml/ocl/type/URealType.java` | — | **new file.** `isTypeOfUReal`, `isKindOfUReal`, `isKindOfNumber` → `true`; `allSupertypes` = `{this, OclAny}`; `conformsTo` = `equals(t) \|\| t.isTypeOfOclAny()`. Ctor is **`protected`** (`F/uml/ocl/type/URealType.java:8` — **corrected** from `:9`; `grep -n 'protected URealType'` → `8`, and §7.2 M-22 already had it right, audit-02 M6) — correcting `11-types.md:711`'s "all siblings package-private" (**corrected** from `:747`, a blank line; refuter R10b adopted). |
| `T/uml/ocl/type/UStringType.java` | — | **new file.** `isTypeOfUString`, `isKindOfUString` → `true`; `allSupertypes` = `{mkUString(), OclAny}`. |
| `T/uml/ocl/type/UBooleanType.java` | — | **new file.** `isKindOfOclAny`, `isKindOfUBoolean`, `isKindOfSBoolean`, `isTypeOfUBoolean` → `true`; `allSupertypes` = `{mkUBoolean(), SBoolean, OclAny}`; `conformsTo` accepts self, `OclAny`, `SBoolean`. **Must NOT gain an `isKindOfUBoolean` override on `SBooleanType`** — see §2.5 note. |
| `T/uml/ocl/type/SBooleanType.java` | — | **new file.** `isTypeOfSBoolean`, `isKindOfSBoolean` → `true`; `allSupertypes` = `{SBoolean, OclAny}`. Does **not** override `isKindOfUBoolean` — that asymmetry is load-bearing (§2.5). Gated by **B2**. |

> **MANDATORY for all seven (and the highest-consequence single finding in the refutation passes).**
> `TypeImpl.conformsTo` is **not** a `false` default — it is `return this.conformsTo(other);`
> (`T/uml/ocl/type/TypeImpl.java:78-81`; fork `:76-78`, identical). A new `*Type` that inherits it
> **compiles clean and then `StackOverflowError`s at first use.** `11-types.md` §1.8-2 states this;
> `15-upstream-delta.md` §2.1/§2.2 contradicts it ("supplies a `false`-returning default for every
> predicate"). **Verdict adopted: the refuter (`18-refutation-delta.md` F1).** Treat `conformsTo` as
> a de-facto abstract member of `TypeImpl`; override it in all seven new classes (the five leaves do,
> the two abstract tags need not) and add a test that calls `conformsTo` on each.

## 1.3 New files — `org/tzi/use/uml/ocl/expr/` (8, or 7 under B10)

| Target path | L | Ctor arity / `throws` | Guards | `eval` failure mode | `toString` prefix / separator | Visitor member |
|---|---|---|---|---|---|---|
| `ExpConstUBoolean.java` | 78 | 2 / yes | `isTypeOfBoolean`; `isTypeOfInteger \|\| isTypeOfReal`. **`VoidType` not accepted** | catches `RuntimeException` → Undefined. **`value.isUndefined()` is never checked** — undefined value + defined probability yields `UBoolean(false,p)`, a *defined* result | `UBoolean(` / `,` | `visitConstUBoolean` |
| `ExpConstUInteger.java` | 68 | 2 / yes | `isTypeOfInteger \|\| isTypeOfVoidType`; `Integer\|Real\|Void`. **Void tolerated on both** | defined-check → Undefined. Uses real casts, not `toString()` parsing | `mkUInteger().toString()` + `(` / **`, `** (the only spaced one) | `visitConstUInteger` |
| `ExpConstUReal.java` | 69 | 2 / **no** | **none** — the only one with no guard | propagates `NumberFormatException` (no `try`) | `UReal(` / `,` | `visitConstUReal` |
| `ExpConstUString.java` | 75 | 2 / yes | `isKindOfReal(EXCLUDE_VOID)` on conf (msg misspells "confidance"); `isTypeOfString` | propagates `ClassCastException` (unguarded `(StringValue)` cast); range → Undefined | `UString(` / `,` | `visitConstUString` |
| `ExpConstSBoolean.java` | 88 | **4** / yes | 4× `isKindOfReal(EXCLUDE_VOID)` (each message has a double space) | catches `Exception` → Undefined (broadest) | `SBoolean(` / `,` | `visitConstSBoolean` |
| `ExpUSelect.java` | 49 | 3 / yes | `assertKindOfUBoolean()` instead of `assertBooleanQuery()`; result type = **range type** | — | inherited | `visitUSelect` |
| `ExpUSelectC.java` | 37 | 4 / yes | calls the **5-arg** `ExpQuery` ctor, then `assertKindOfUBoolean()` | — | inherited | `visitUSelectC` |
| `ExpDefSBoolean.java` | 48 | 1 / no (throws unchecked) | **inverted** | **no `ctx.exit`**; can return Java `null` | `SBoolean(` | `visitDefSBoolean` |

All five `ExpConst*` extend `Expression` **directly** and store `Expression` children (unlike 7.5.0's
`ExpConstReal`, which stores a primitive `double`) — they are literals only syntactically. None is
`final`; none defines `equals`/`hashCode`; all return `false` from `childExpressionRequiresPreState()`,
so **an `@pre` inside a `UReal(...)` argument is silently ignored**.

**`ExpDefSBoolean.java` — DROP (B10).**

## 1.4 New files — `org/tzi/use/uml/ocl/expr/operations/` (5)

| Target path | Registered ops | Classes | Distinct OCL names | Shape |
|---|---|---|---|---|
| `StandardOperationsUBoolean.java` | 14 | 14 | 14 | `public class` + top-level `final class Op_uBoolean_*`; 6 of 14 extend `BooleanOperation` (`kind()==SPECIAL`) |
| `StandardOperationsUReal.java` | 18 | 18 | 18 | `public class` + `final class Op_ureal_*`; all `OPERATION`, all non-infix |
| `StandardOperationsUInteger.java` | **13** | **12** | **13** | `Op_uInteger_value` registered **twice** — under `value` and under the alias `toInteger` |
| `StandardOperationsUString.java` | **22** | **21** | **21** | `Op_uString_uConcat` registered **twice** (lines 19, 21) — a copy/paste defect; the second is dead but harmless |
| `StandardOperationsSBoolean.java` | 39 | 39 (anonymous) | 39 | **a Java `enum` of anonymous `OpGeneric`s** — a different idiom from every sibling. Gated by **B2** |
| | **106** | **104** | **105** | |

## 1.5 New files — `org/tzi/use/parser/ocl/` (6, or 5 under B10)

`ASTUBooleanLiteral`, `ASTUIntegerLiteral`, `ASTURealLiteral`, `ASTUStringLiteral`,
`ASTSBooleanLiteral` — **five per-type classes; do NOT create a single `ASTUncertainLiteral`.**
The split is load-bearing: arities differ (2 vs **4** for SBoolean); `gen` enforces different checks
(`F/parser/ocl/ASTURealLiteral.java:27-31` pre-checks with bespoke messages, the others translate
`ExpInvalidException`); they construct five distinct `ExpConst*`; their `toString()` renderings
differ, and `ASTUStringLiteral` **has none** (add one for parity). `ASTSBooleanDefExpression.java` —
**DROP (B10)**, referenced only by its own declaration and ctor.

## 1.6 Edits to upstream files (26 `.java`)

Ordered by uncertainty-attributable added lines (the reproduction command at the head of §1).
"Minimal change" is stated **as a behaviour**, per the brief.

| # | Target path | Δ | New file / **edit** | Minimal change, stated as behaviour | Refuter disagreement — verdict adopted |
|---|---|---|---|---|---|
| E1 | `T/uml/ocl/expr/operations/StandardOperationsNumber.java` | 133 | **edit** | Arithmetic and relational operations must now also accept operands that carry uncertainty, widening the result to `UInteger`/`UReal` when an operand is uncertain and to `UBoolean` for the four comparisons. `ArithOperation.matches` gains `getLeastCommonSupertype(...).isTypeOfUInteger()` **before** the `UReal` fallback — that ordering decides whether `UInteger+UInteger` stays `UInteger` | `12-expressions.md` **omits this file entirely** (its §6.2 lists 4 files, delivers 8). **Refuter R1 adopted.** `15-upstream-delta.md` and `20-ops-*.md` do cover it. **Three-way merge required**: 7.5.0 independently added `Op_number_pow`/`Op_number_sqrt` (B8) — taking the fork's file wholesale **deletes `pow` and `sqrt` from OCL** |
| E2 | `T/uml/ocl/expr/operations/StandardOperationsCollection.java` | 34 | **edit** | Membership tests on collections must answer **with a degree of confidence** rather than yes/no when either the element or the probe carries uncertainty. **`Op_includes`, `Op_excludes`, `Op_includesAll`, `Op_excludesAll` are rewritten**, not merely added to; plus `Op_collection_uCount` / `Op_collection_uCountC` are registered (`:42`, `:311`) | `10-values.md:895` defers this file to "the expression spec part", which never picks it up. **Refuter R1 adopted** |
| E3 | `T/uml/ocl/expr/operations/StandardOperationsAny.java` | 30 | **edit** | `=` and `<>` must return a **degree of equality** (`UBoolean`, or `SBoolean` when either operand is statically `SBooleanType`) instead of `Boolean` when either operand is uncertain; and a new `equals` operation must exist. **`Op_equal` and `Op_notequal` `matches`/`eval` bodies are replaced** (`T:32`, `T:86`); `Op_identical` is added (`F:130-179`) | Missing from **both** `12-expressions.md` §6.2 **and** `13-grammar.md` §13.6 — yet §13's Change A depends on `Op_identical` (7.5.0 has no `"equals"`: `grep -n '"equals"' T/…/StandardOperationsAny.java` → nothing). **Refuters R1 + R4 adopted.** A porter following only §13.6 ships a grammar level whose operator resolves to nothing |
| E4 | `T/uml/ocl/value/CollectionValue.java` | 29 | **edit** | Every collection must additionally answer membership/counting **with a confidence**: `uIncludes` (`F:112`, max-fold, early-exit at 1), `uIncludesAll` (`:134`, size guard then `and`-fold, early-exit at 0), `uExcludes` (`:154`, `and`-fold of `uDistinct`), `uExcludesAll` (`:177`), `uCountC(Value,double) : int` (`:190`). All **non-abstract** ⇒ `SetValue`/`BagValue`/`SequenceValue`/`OrderedSetValue` need **no change** | **Refuter R8: minimality is asserted, not argued.** All five bodies use only `iterator()`/`size()`/public `Value` API, so they could be `static` helpers on a new class, touching one fewer upstream file. **Verdict: keep them on `CollectionValue`** — it matches the fork, and `StandardOperationsCollection` must be edited anyway (E2) — but record that this is *preferred*, not *forced* |
| E5 | `T/uml/ocl/expr/ExpQuery.java` | 20 | **edit** | Query expressions must be able to carry an optional **confidence threshold**, and to accept a `UBoolean`-kind predicate. Add: field `fUncertaintyExp`; 5-arg ctor (requires `uncertaintyExp.type().isKindOfReal(EXCLUDE_VOID)`); `assertKindOfUBoolean()`; `evalUSelect()`; `private evalAndAsertConfident()` (default `0.5`; `RuntimeException` outside `[0,1]`); `getUncertaintyExpression()` | **Refuter R5:** `fElemVarDecls`/`fRangeExp`/`fQueryExp` are `protected` (`T:43,48,53`) and the only consumers are `ExpUSelect`/`ExpUSelectC`, **new files in the same package** — so 4 of the 5 members could live in a new `abstract class ExpUQuery extends ExpQuery` with **zero** upstream edit. Only `getUncertaintyExpression()` must sit on `ExpQuery`, because the print and coverage visitors call it through an `ExpQuery`-typed reference. **Verdict: keep all five on `ExpQuery`** (fidelity + one call path), record that minimality was asserted, not argued. **Also see B9** |
| E6 | `T/uml/ocl/type/TypeFactory.java` | 15 | **edit** | Five new built-in type names must resolve, and five interned singletons must exist. 5 `private static final` fields, 5 `mk*()` accessors, 5 `buildInTypesMap` entries. Nothing else touched (`mkEnum`, `mkCollection`, …, `mkSimpleType` byte-identical) | none |
| E7 | `T/uml/ocl/value/Value.java` | 12 | **edit** | `Value` must answer four additional type-discrimination questions — "are you a UInteger / UReal / UBoolean / SBoolean?" — defaulting to **no**, exactly as for `isInteger`/`isReal`/`isBoolean`. **Four `public boolean`, non-abstract, `return false`.** No field, no signature, no abstract member added ⇒ **no existing 7.5.0 value class needs touching** | **Do NOT add `isUString()` "for symmetry"** — the fork did not, `UStringValue` does not need one, and it would leave a predicate no class ever answers `true` to |
| E8 | `T/parser/ocl/ASTQueryExpression.java` | 11 | **edit** | A query expression must additionally carry an optional confidence sub-expression through to `gen`, and `uSelectC` without one must be a semantic error | **Refuter R7 adopted — `13-grammar.md:756` understates this badly.** Required: new field `fUncertainty` (`F:47`); in `gen`, `Expression uncertainty = null;` + `if (fUncertainty != null) uncertainty = fUncertainty.gen(ctx);` (`F:112-115`); **two new outer-`switch` labels** `Q_USELECT_ID`/`Q_USELECTC_ID` in the single-element-variable group (`F:124-125`); **two new inner-`switch` arms** (`F:139`, and `F:178-184` with the null-confidence `SemanticException`); `toString()` rewritten to append `", " + fUncertainty` (`F:224-229`). As specified in §13.6 ("ctor overload plus a guard") it would compile, parse, and then throw *"Internal error: unknown query operation"* at `gen` time |
| E9 | `T/uml/ocl/type/TypeImpl.java` | 10 | **edit** | Ten `return false;` no-ops for the ten new predicates. `conformsTo`, `getLeastCommonSupertype`, `shortName`, `qualifiedName`, `toString` and the 7.5.0 DataType no-ops untouched | **Refuter R9:** a static helper `UncertainTypes.isKindOfUBoolean(Type,VoidHandling)` would reproduce every answer with **zero** edits here, in `Type`, `VoidType` and `MClassifierImpl`. Refuter does **not** recommend it. **Verdict: keep the interface approach** (more faithful, far more readable), record that "minimal" was asserted |
| E10 | `T/uml/ocl/type/Type.java` | 10 | **edit** | Declare the ten predicates `isTypeOf/isKindOf × {UInteger, UReal, UString, UBoolean, SBoolean}`. `qualifiedName` (`:48`) and the DataType pair (`:136-138`) **stay** | **File-level copying from the fork is forbidden** — it would silently delete `qualifiedName`, `isKindOfDataType`, `isTypeOfDataType` |
| E11 | `T/uml/mm/MClassifierImpl.java` | 10 | **edit** | `MClass`/`MDataType`/`MAssociation` must answer "no" to every uncertainty query. Ten `return false;` no-ops. **`conformsTo` (`:121-130`), `allSupertypes` (`:132-137`), `getLeastCommonSupertype` (`:139-…`) untouched** | **Load-bearing:** `Type` has exactly **two** implementation roots in 7.5.0 — `TypeImpl` and `MClassifierImpl` — proved by `grep -rn "public boolean isTypeOfOclAny()" --include=*.java use-core use-gui` → 3 hits (`TypeImpl:313`, `MClassifierImpl:355`, `OclAnyType:33`). Adding to `Type` without adding here **breaks the build**. **File-level copying is forbidden** — 7.5.0's `MClassifierImpl` is 588 L vs the fork's 511 L, pulling up the whole attribute/operation table (`:54-61`, `:491-573`) |
| E12 | `T/uml/ocl/expr/ExpressionPrintVisitor.java` | 8 | **edit** | Must render the seven (eight under B10=port) new expression forms, **and must print the confidence argument of a `uSelectC`** | **Refuter R2 adopted — `12-expressions.md` says "add 8 methods" and misses an existing-body change.** `visitQuery(ExpQuery, VarInitializer)` gains, after `exp.getQueryExpression().processWithVisitor(this)`: `if (exp.getUncertaintyExpression() != null) { writer.write(","); writer.write(ws()); exp.getUncertaintyExpression().processWithVisitor(this); }` (`F:421-425`). **Refuter R3 adopted:** §12 §3.3.4's "the confidence argument is lost on print / will break any print-then-reparse test" is **false for the print visitor** — `visitUSelectC` delegates to `visitQuery`, which does print it. Only `ExpQuery.toString(StringBuilder)` (`F:680-694`, unchanged from `T:486-500`) drops it |
| E13 | `T/analysis/coverage/AbstractCoverageVisitor.java` | 7 | **edit** | Must traverse the new expression forms (empty bodies for the literals, `visitQuery(exp)` for the two uSelects) **and traverse a query's confidence sub-expression** | **Refuter R2 adopted.** `visitQuery(ExpQuery)` gains `if (exp.getUncertaintyExpression() != null) exp.getQueryExpression().processWithVisitor(this);` (`F:257-259`) — note the fork's **own bug**: it re-visits `getQueryExpression()`, not `getUncertaintyExpression()`. Neither the edit nor the bug is recorded in §12. Technically abstract, but it implements all 49 concretely (`grep -c "public void visit"` → 49) and its two concrete subclasses would break |
| E14 | `T/uml/ocl/expr/operations/OpGeneric.java` | 6 | **edit** | Five uncertainty registries must be registered, **after** `StandardOperationsBoolean` and **before** the collections | Missing from `12-expressions.md`. **Refuter R1 adopted.** The complete fork↔7.5.0 diff of this file is **one hunk** (`@@ -88,6 +88,13 @@`, 7 added lines incl. a blank and a comment — `15-upstream-delta.md`'s "six lines" and `20-ops-UInteger.md` §7's "88..97" are both loose; refuter F7/R.7 adopted). **No `OpGeneric` member signature differs** — all five ops sections independently confirm this |
| E15 | `T/uml/ocl/expr/ExpressionVisitor.java` | 6 | **edit** | Seven new `void visit…` declarations (eight if B10 = port). Insert to keep the fork's ordering: `visitConstUBoolean`/`visitConstSBoolean`(`/visitDefSBoolean`) after `visitConstBoolean` (`T:35`), `visitConstUInteger` after `:37`, `visitConstUReal` after `:38`, `visitConstUString` after `:39`, `visitUSelect`/`visitUSelectC` after `:76` | **Do NOT copy the fork's file** — it is 7.0-era and declares `visitObjOp(ExpObjOp)`; 7.5.0 uses `visitInstanceOp(ExpInstanceOp)` (upstream `46c277e7`, 2024-11-24). Counts: 7.5.0 declares **49**, fork **57**; the set difference is exactly the 8 new methods **plus** the `visitObjOp ↔ visitInstanceOp` rename |
| E16 | `T/uml/ocl/type/VoidType.java` | 5 | **edit** | `OclVoid` answers `true` to the five new `isKindOf*` **only under `INCLUDE_VOID`**. Five overrides, each `return h == VoidHandling.INCLUDE_VOID;`. `conformsTo` stays `return true`; `allSupertypes()` keeps throwing; 7.5.0's `isKindOfDataType` (`:92-95`) stays | none |
| E17 | `T/uml/ocl/type/BooleanType.java` | 5 | **edit** | `Boolean ≤ UBoolean` and `Boolean ≤ SBoolean`. Add `isKindOfUBoolean`/`isKindOfSBoolean` → `true`; `conformsTo` gains 2 disjuncts; `allSupertypes` gains 2 entries | The fork leaves the `new HashSet<Type>(2)` capacity hint at 2 while inserting four elements — harmless; do not copy it as if it were meaningful |
| E18 | `T/uml/ocl/type/IntegerType.java` | 4 | **edit** | `Integer ≤ UInteger` and `Integer ≤ UReal`. Add `isKindOfUReal`/`isKindOfUInteger` → `true`; `allSupertypes` gains `mkUReal()`+`mkUInteger()` (capacity 3→5). **`conformsTo` is NOT edited** | **The single most important mechanical fact in the type section.** `IntegerType.conformsTo` is *predicate-driven* (`!t.isTypeOfVoidType() && (t.isKindOfNumber(EXCLUDE_VOID) \|\| t.isTypeOfOclAny())`); the new edges arise entirely from `UIntegerType.isKindOfNumber`/`URealType.isKindOfNumber` returning `true`. It is **byte-identical** in fork and 7.5.0. A reviewer looking only at `conformsTo` will see no change and must not "fix" it |
| E19 | `T/uml/ocl/type/StringType.java` | 3 | **edit** | `String ≤ UString`. `isKindOfUString` → `true`; `conformsTo` gains `\|\| t.isTypeOfUString()`; `allSupertypes` gains `mkUString()` | none |
| E20 | `T/uml/ocl/type/RealType.java` | 3 | **edit** | `Real ≤ UReal`. `isKindOfUReal` → `true`; `conformsTo` gains `\|\| t.isTypeOfUReal()`; `allSupertypes` gains `mkUReal()` | none |
| E21 | `T/uml/ocl/expr/ExpForAll.java` | 2 | **edit, CONDITIONAL** | `assertBooleanQuery()` → `assertKindOfUBoolean()` | **Only together with E5's items 7+8 — see B9.** Under 7.5.0's `evalExistsOrForAll` the query value is cast `(BooleanValue) queryVal` (`T:206,208`), so relaxing the ctor assertion alone gives a `ClassCastException` at eval time |
| E22 | `T/uml/ocl/expr/ExpExists.java` | 2 | **edit, CONDITIONAL** | same | same |
| E23 | `T/parser/base/ParserHelper.java` | 2 | **edit** | `uSelect` and `uSelectC` must be recognised as query identifiers. **Exactly 6 added lines in 3 hunks, nothing removed**: `Q_USELECT`/`Q_USELECTC` strings, `Q_USELECT_ID = 12`/`Q_USELECTC_ID = 13`, two `queryIdentMap.put`. They are **`IDENT`s in a map, not keyword tokens** — a materially lower-risk mechanism than the one used for the literals, and the pattern to prefer | none |
| E24 | `T/analysis/coverage/BasicExpressionCoverageCalulator.java` | 1 | **edit / none** | Inherits from `AbstractCoverageVisitor`; needs nothing once E13 lands | The fork's version `import`s `ExpConstUReal` (`:29`) but declares **no** `visitConstUReal` — a stray unused import. **Do not port the import** |
| E25 | `T/util/MathUtil.java` | (invisible to the keyword grep) | **edit** | A rounding helper `round(double value, int digits)` must exist. Body from `F/util/MathUtil.java:96-109`, `Math.round(value * 10^digits) / 10^digits`, `@author Víctor Manuel Ortiz`. **Absent in 7.5.0** — `T/util/MathUtil.java` has only `max`/`min`. Required by `UBooleanValue:197,240`, `UIntegerValue:51,75-76`, `URealValue:48-50,77-80`, `SBooleanValue:125-128` — 15 call sites | Must be copied **byte-identically** — 101 assertions in the ported tests spell expected uncertainties to exactly 10 decimals and pass *only* because `equals` truncates at the 10th. The fork's file also carries two `<br/>` → `</br>` javadoc regressions; **do not port those** |
| E26 | `T/uml/ocl/value/RealValue.java` | (invisible to the keyword grep) | **edit** | A static widening lift `valueOf(Value)` must exist, answering `null` when the argument is neither Real nor Integer. Called from `SBooleanValue:258,271,284,285,290`. It is the **sole** behavioural difference between the two `RealValue.java` files | **Alternative that avoids the edit:** inline the two-branch coercion inside `SBooleanValue`. Recommend the edit (additive, matches the historical shape). Under **B2 = option 1 or 2** this edit disappears entirely with `SBooleanValue` |

## 1.7 Non-Java edits

| # | Target | Kind | Minimal change |
|---|---|---|---|
| E27 | `G/base/OCLBase.gpart` | **edit** | 8 hunks, **+35 −8** (31 executable grammar lines added), 677 → 707 lines. See §5. **Note the path**: 7.5.0 keeps grammar fragments under `use-core/src/main/resources/grammars/`, **not** under `parser/`. All 7.5.0 `.gpart` files are **CRLF**, the fork's are LF — every naive diff shows 100 % change; normalise with `sed 's/\r$//'` or `tr -d '\r'` before merging (refuter F3 adopted) |
| E28 | `G/base/OCLLexerRules.gpart` | **no edit — 0 lines** | Identical after CRLF normalisation. §15 §6.3's "empty diff" is **not reproducible as written** (`diff` reports `1,127c1,127`); the conclusion — no new lexer token — stands |
| E29 | `TT/uml/ocl/type/TypeTest.java` | **decision, not a mechanical edit** | See **B5** and §6.3. The recommended shape is a **new** `TT/uml/ocl/type/UncertaintyTypeTest.java` (598 assertions) with upstream's `TypeTest.java` receiving **zero** edits — but that only works for the additive material; the 10 mutated `testSupertype` assertions are a design question, not a test-placement one |

## 1.8 Explicitly NOT changed — verified

`BasicType`, `OclAnyType`, `UnlimitedNaturalType`, `UniqueLeastCommonSupertypeDeterminator`,
`EnumType`, `CollectionType`, `SetType`, `SequenceType`, `BagType`, `OrderedSetType`, `TupleType`,
`MessageType`, `MClassifier`, `Expression`, `ExpSelect`, `ExpReject`, `StandardOperationsBoolean`,
`StandardOperationsString`, `BooleanOperation`, `IntegerValue`, `BooleanValue`, `StringValue`.

Under `diff -u --strip-trailing-cr` the only hunks are removed `$Id$`/`$ProjectVersion$` tags,
import reordering, and one javadoc `<` → `&lt;` escape. `MessageType.java` produces **no** hunks at
all (it differs only in line endings). **Collection conformance for uncertain element types
therefore requires zero collection-type edits** — `CollectionType.allSupertypes` maps over the
element type's supertypes, so `Collection(Boolean).allSupertypes()` automatically contains
`Collection(UBoolean)` and `Collection(SBoolean)` (asserted at `FT/uml/ocl/type/TypeTest.java:280-360`).

**No visitor dispatches over `Type` or `Value` subclasses in either tree**
(`grep -rn "TypeVisitor\|ValueVisitor" --include=*.java use-core/src use-gui/src` → 0 hits), so no
visitor needs a new case for the type or value layer. The `ExpressionVisitor` family is the only
one affected (§1.6 E12/E13/E15).

---

# 2. Per-type operation tables

## 2.0 Conventions that apply to every table below

* **Arity counts the receiver as argument 0.** This is not a documentation convenience — it is how
  the registry works: `OpGeneric.matches(Type[] params)` receives the receiver as `params[0]` and
  `eval(EvalContext, Value[] args, Type)` receives it as `args[0]`. So `ub.equalsC(other, 0.9)` is
  **arity 3**.
* **`kind()` governs undefined arguments, and the *caller* enforces it.** `ExpStdOp.eval` evaluates
  arguments left to right; on the **first** `v.isUndefined()`: `OPERATION` ⇒ result is
  `UndefinedValue.instance` and **`eval` is never called**; `SPECIAL` ⇒ the operation handles it;
  `isBooleanOperation()` ⇒ `evalWithArgs(ctx, Expression[])` is called with **unevaluated** operands.
  The `eval` call is wrapped in `catch (ArithmeticException) → Undefined` and **nothing else** — an
  NPE or CCE escapes the evaluator. Verified identical in both trees (fork `F/uml/ocl/expr/ExpStdOp.java:293-318`;
  7.5.0 `:286-311`; note `20-ops-UInteger.md` §2's "299-308 / 308-315" matches neither tree exactly —
  refuter R.5 adopted).
* **Overload resolution is first-match-wins in registration order.** `ExpStdOp.opmap` is an
  `ArrayListMultimap`; `create` returns the first `op` whose `matches(params)` is non-null.
* **`OpGeneric`'s contract is byte-identical between fork and 7.5.0.** All five ops sections
  verified this independently. **No operation needs a signature adaptation**; only the *vocabulary*
  they call (`TypeFactory.mkU*`, `Type.isTypeOfU*`, `Value.isU*`, the `U*Value` classes,
  `MathUtil.round`, `RealValue.valueOf`, `uDataTypes.*`) is missing from 7.5.0.
* `IoR` = the set admitted by `isKindOfReal(EXCLUDE_VOID)` = **exactly `{Integer, Real}`**.
  Verified in the fork's lattice: overridden only in `RealType:50`, `IntegerType:53`,
  `VoidType:48`; `UnlimitedNaturalType extends BasicType` and does **not** override it; neither do
  `URealType`/`UIntegerType`. This is what makes the unguarded `(RealValue)` casts safe.

**Extractor vs refuter on the counts: all five types agree.** No count is disputed. The
disagreements are in the semantic prose and the cross-check apparatus, and are recorded in place.

| Type | Registrations | Classes | Distinct OCL names | Extractor | Refuter | Adopted |
|---|---|---|---|---|---|---|
| `UBoolean` | 14 | 14 | 14 | 14 | 14 | **14** |
| `UReal` | 18 | 18 | 18 | 18 | 18 | **18** |
| `UInteger` | 13 | **12** | 13 | 12 cls / 13 reg | 12 cls / 13 reg | **12 / 13** |
| `UString` | **22** | **21** | 21 | 21 cls / 22 reg | 21 cls / 22 reg | **21 / 22** |
| `SBoolean` | 39 | 39 | 39 | 39 | 39 | **39** |

## 2.1 `UBoolean` — 14 operations

```bash
F=.git/reference-repositories/uncertainty/USE-Uncertainty/src/main/org/tzi/use/uml/ocl/expr/operations/StandardOperationsUBoolean.java
grep -c 'OpGeneric\.registerOperation(new Op_uBoolean_' $F   # 14
grep -c '^final class Op_uBoolean_' $F                        # 14
grep -o 'return "[a-zA-Z]*";' $F | sort -u | wc -l            # 14  (all distinct)
```
Names: `and confidence equalsC equivalent implies not or setConfidence setValue toBoolean toBooleanC toString value xor`.

| # | Name | Arity | Argument types (arg0 = receiver) | Result | Notation | Base / `kind()` | Semantics |
|---|---|---|---|---|---|---|---|
| 1 | `toBoolean` | 1 | `UBoolean` (typeOf) | `Boolean` | dot | `OpGeneric`/OPERATION | Threshold collapse at **`c ≥ 0.5`**; uncertainty **discarded**. `c==0.5` → `true`. |
| 2 | `toString` | 1 | `UBoolean` (typeOf) | `String` | dot | OPERATION | **Display form**: flips out of canonical form below 0.5, so it prints `(most-likely-label, conf ≥ 0.5)`. `c=0.3` prints `UBoolean(false, 0.7)`. **No rounding** — `0.8 or 0.6` prints `0.9199999999999999` |
| 3 | `toBooleanC` | 2 | `UBoolean` (typeOf), `IoR` | `Boolean` | dot | OPERATION | Parameterised collapse: `true` iff `c ≥ threshold`. `t=0` always true; `t=1` iff `c==1`; **`t=NaN` → `FALSE`, not Undefined** |
| 4 | `value` | 1 | `UBoolean` (typeOf) | `Boolean` | dot | OPERATION | **`return BooleanValue.TRUE;` — the entire body.** Vacuously right (canonical `b` is always `true`) and informationless. Header comment says `-> Real`; code says `Boolean` — code wins |
| 5 | `setValue` | 2 | `UBoolean` (typeOf), `Boolean` (typeOf) | `UBoolean` | dot | OPERATION | Replaces the label, carrying the confidence over **as confidence in the new label**; `setValue(false)` therefore inverts. `FALSE.setValue(false)` → **`TRUE`**. Unguarded `(UBooleanValue)` cast |
| 6 | `confidence` | 1 | `UBoolean` (typeOf) | `Real` | dot | OPERATION | Returns the **canonical** `P(true)`, **not** the confidence `toString` displays. A value printing as `UBoolean(false,0.7)` returns **`0.3`**. `confidence` and `toString` disagree on every value with `c < 0.5` |
| 7 | `setConfidence` | 2 | `UBoolean` (typeOf), `IoR` | `UBoolean` | dot | OPERATION | **Class is `Op_uBoolean_setUncertainty`, `name()` is `"setConfidence"`.** There is no `setUncertainty` operation. Range `[0,1]` else Undefined; **NaN → Undefined** |
| 8 | `equalsC` | **3** | `UBoolean` (typeOf), `UBoolean` (**kindOf**, EXCLUDE_VOID), `IoR` | `Boolean` | dot | OPERATION | `\|c₁−c₂\| ≤ 1−k`. **Not** "`equivalent` has confidence ≥ k" (that intent is commented out at `UDT/UBoolean.java:155-156`) — do not "fix". `k=1` demands exact equality; `k=0` always true; NaN → Undefined |
| 9 | `and` | 2 | `UBoolean` (kindOf, **INCLUDE_VOID**) ×2 | `UBoolean` **unconditionally** | **infix** | `BooleanOperation`/SPECIAL | `c₁·c₂`. Short-circuits when `ub1.probability()==0`, **right operand never evaluated** — unless `ctx.isEnableEvalTree()`, which suppresses short-circuiting so the tree can show both. Guards `v2.isDefined()` |
| 10 | `or` | 2 | same | `UBoolean` | **infix** | SPECIAL | `c₁+c₂−c₁c₂`. Short-circuits at `c₁==1`. **Omits the `v2.isDefined()` guard** ⇒ `Undefined or Undefined` NPEs (§7.2 / defect) |
| 11 | `not` | 1 | `UBoolean` (kindOf, INCLUDE_VOID) | `UBoolean` | **prefix** | SPECIAL | `1−c`. Correctly guarded. `not(0.8)` = `0.19999999999999996`, not `0.2` |
| 12 | `implies` | 2 | same | `UBoolean` | **infix** | SPECIAL | `(1−c₁)+c₂−(1−c₁)c₂`. `c₁==0` short-circuits to the hard-coded `TRUE` (discarding the operand, unlike `and`/`or`). Guards `v2.isDefined()` |
| 13 | `xor` | 2 | same | `UBoolean` | **dot-call** | SPECIAL | `\|c₁−c₂\|`, value hard-coded `true`. `isInfixOrPrefix()` → **`false`**, contradicting core `xor` (both trees `StandardOperationsBoolean.java:87-88` → `true`). Affects **printing only**. No short-circuit, no eval-tree branch |
| 14 | `equivalent` | 2 | same | **`Boolean` if both args `isTypeOfBoolean`, else `UBoolean`** | dot | SPECIAL | `1−\|c₁−c₂\|` = `xor().not()`. **Evaluates both operands twice** (`:636-641`) |

**Confidence algebra, verified by executing the live oracle jar** (`c₁=0.8, c₂=0.6`), and
independently re-derived in exact IEEE-754:

| Op | Rule | Live-jar result |
|---|---|---|
| `not` | `1−c` | `0.19999999999999996` |
| `and` | `c₁·c₂` | `0.48` |
| `or` | `c₁+c₂−c₁c₂` | `0.9199999999999999` |
| `implies` | `(1−c₁)+c₂−(1−c₁)c₂` | `0.6799999999999999` |
| `xor` | `\|c₁−c₂\|` | `0.20000000000000007` |
| `equivalent` | `1−\|c₁−c₂\|` | `0.7999999999999999` |
| `equalsC(b,k)` | `\|c₁−c₂\| ≤ 1−k` | `k=0.7` → true; `k=0.9` → false |
| `toBoolean` | `c ≥ 0.5` | `0.5` → true; `0.4999` → false |

**Asserting `0.92`, `0.2` or `0.68` will fail.** Ported tests must use these exact doubles or an
explicit epsilon.

**Reference-identity aliasing.** `UDT/UBoolean.and/or/implies` each open with `if (this == b)`,
applying an idempotence shortcut on **object identity**: `p.and(p)` → `0.8` not `0.64`;
`p.or(p)` → `0.8` not `0.96`; `p.implies(p)` → `0.8` not `1.0`. `xor` has **no** self-branch and so
gets idempotence right (`p.xor(p)` → `0.0`). Because `TRUE`/`FALSE` are singletons this branch **is**
hit for `true and true` etc., where it is harmless. Note the identity compared is the inner
`uDataTypes.UBoolean`, not the wrapper — two distinct `UBooleanValue`s over the same `UBoolean`
would also trip it.

### Extractor / refuter disagreements — UBoolean

| Claim | Extractor | Refuter | **Adopted** |
|---|---|---|---|
| `equivalent` has no competitor; §4.3 "the deliberate exception" | sole provider for both `Boolean` and `UBoolean` | **WRONG.** `StandardOperationsSBoolean` also registers `equivalent` (`:534`/`:538`), and its `matches` is `isKindOfSBoolean(EXCLUDE_VOID)` ×2 — **true for both `BooleanType` and `UBooleanType`**. The UBoolean overload wins **only** by registration order (`F/uml/ocl/expr/operations/OpGeneric.java:94` before `:97`). The same applies to `and`, `or`, `not`, `implies`, `xor`, `toString` | **Refuter.** The precedence chain is **Boolean → UBoolean → SBoolean**, a three-element chain, not two. Registration order is load-bearing for `equivalent` *exactly as much as* for the rest |
| "a plain `Boolean` receiver of `toBoolean` is taken by the core `Boolean` registry" | as stated | **WRONG.** There is no `toBoolean` for a `Boolean` receiver anywhere. The only core one is `Op_string_toBoolean` (`isTypeOfString`). `someBoolean.toBoolean()` matches **nothing** and `ExpStdOp.create` throws `ExpInvalidException("Undefined operation …")`. §4.3's own table (which lists `T/uml/ocl/expr/operations/StandardOperationsString.java:547`) contradicts the parenthetical | **Refuter** |
| `toBooleanC` NPE mechanism | "`left` is assigned before the range check, so a non-Boolean receiver would NPE" | **WRONG mechanism.** The only dereference `left.probability()` is at `:157`, inside the `else if`, reached **only when the threshold is in `[0,1]``. The null-deref is guarded, accidentally, by the range check | **Refuter** |
| `and`: "returns the receiver object, preserving identity" | as stated | `ub1` is `valueOf(v1)`, which for a `BooleanValue` receiver returns the interned singleton, not the receiver | **Refuter** (minor) |
| "`setNormalForm()` runs in every constructor" | as stated | the no-arg `UBoolean()` (`UDT:18-20`) does not — behaviourally irrelevant (`b=true,c=0.0` is already canonical) but the invariant statement is false | **Refuter** (minor) |
| §8 item 5 (which `toString`/`toBoolean` wins for a `UBoolean` receiver) marked UNVERIFIABLE | open | **closed.** `Op_boolean_toString.matches` needs `isKindOfBoolean(INCLUDE_VOID)`, and **`UBooleanType` never overrides `isKindOfBoolean`** — the only definition on its chain is `TypeImpl:233-236` → `false`. `Op_toString`(Enum) needs `isTypeOfEnum`; `Op_string_toBoolean` needs `isTypeOfString`. So the UBoolean overloads are reached | **Refuter** — gap closed |

## 2.2 `UReal` — 18 operations

```bash
F=…/StandardOperationsUReal.java
echo $(grep -cE '^[[:space:]]*OpGeneric\.registerOperation\(new Op_ureal_[A-Za-z]+\(\), opmap\);$' $F) \
     $(grep -cE '^final class Op_ureal_[A-Za-z]+ extends OpGeneric \{$' $F) \
     $(grep -oP '(?<=return ")[^"]+(?=";)' $F | sort -u | wc -l)     # 18 18 18
grep -c 'return OPERATION;' $F   # 18   -- all OPERATION
grep -c 'return false;'     $F   # 18   -- none infix/prefix
```
Registration order (load-bearing): `abs, sin, cos, tan, asin, acos, atan, uncertainty,
setUncertainty, value, setValue, neg, power, sqrt, inv, toReal, toInteger, toUInteger`.

| # | Name | Arity | Argument types | Result | Semantics (uncertainty propagation) |
|---|---|---|---|---|---|
| 1 | `abs` | 1 | `UReal` (typeOf) | `UReal` | `(\|x\|, u)` — `u` unchanged (rigid reflection). No guards |
| 2 | `inv` | 1 | `UReal` | `UReal` | `1/x` with `u' = u/x²` (amplified for `\|x\|<1`). Guards the **value** only ⇒ `x==0` → Undefined |
| 3 | `uncertainty` | 1 | `UReal` | `Real` | Projects `u`, always ≥ 0 (`UReal(-3,-2.3).uncertainty()` = `2.3`) |
| 4 | `setUncertainty` | 2 | `UReal`, `IoR` | `UReal` | Value kept, `u` **replaced wholesale** and absolutised. `setUncertainty(-3)` ≡ `(3)` |
| 5 | `neg` | 1 | `UReal` | `UReal` | `(−x, u)`. **OCL name is `"neg"`, a dot-call** — the header comment `/* - : UReal -> UReal */` is **stale**; the infix unary minus is `Op_number_unaryminus` |
| 6 | `value` | 1 | `UReal` | `Real` | Projects `x`, **discards `u`**. Functionally identical to `toReal` |
| 7 | `setValue` | 2 | `UReal`, `IoR` | `UReal` | `u` **preserved**, value replaced. Dual of #4 |
| 8 | `toReal` | 1 | `UReal` | `Real` | `x`, `u` dropped |
| 9 | `toInteger` | 1 | `UReal` | `Integer` | **Floors toward −∞** (`(int) Math.floor(x)`): `toInteger(-3.2,0.2) = -4`. No range check |
| 10 | `toUInteger` | 1 | `UReal` | `UInteger` | **The fork deliberately bypasses the library.** `URealValue.toUInteger()` = `new UIntegerValue((int) value(), uncertainty())` — **C-truncation toward zero**, `u` verbatim. The library's `UReal.toUInteger()` **floors and inflates** `u` by the residue. `UReal(-5.3,3.75)` → fork `UInteger(-5, 3.75)`; library `(-6, …)`. **Do not "fix" — it breaks the historical oracle** |
| 11 | `power` | 2 | `UReal`, `Integer\|Real` (typeOf) | `UReal` | `(xˢ, \|s·u·x^(s−1)\|)`. The second-order term is computed at `UDT/UReal.java:155` and **discarded** (`setX(a); //setX(a+b);`). Exponent narrowed to **`float`**. Two guards (value, uncertainty) → Undefined |
| 12 | `sqrt` | 1 | `UReal` | `UReal` | `(√x, u/(2√x))`, with an explicit `x==0 && u==0` → `(0,0)` special case. **Guard slip at `:488`**: the second disjunct tests `isInfinite(result.uncertainty())` where `value()` was meant |
| 13 | `atan` | 1 | `UReal` | `UReal` | `u/(1+x²)` — always contracted. Guards unreachable |
| 14 | `sin` | 1 | `UReal` | `UReal` | `\|u·cos x\|`. **No guards at all** |
| 15 | `cos` | 1 | `UReal` | `UReal` | `\|u·sin x\|` (the library drops the minus sign; `setU`'s `Math.abs` repairs it). No guards |
| 16 | `tan` | 1 | `UReal` | `UReal` | **`sin().divideBy(cos())`, not a closed formula.** The both-uncertain branch is `sqrt((u·cos x)²/\|cos x\| + sin²x·(u·sin x)²/cos⁴x)` — **not** the textbook `u/cos²x`. Probe: `tan(0.5,0.1)` → `u=0.09831850394390179`, textbook would be `0.1685`. **A direct `u/cos²x` port silently disagrees.** `Math.tan(π/2)` is finite-but-huge, so the guards do **not** fire at the poles |
| 17 | `asin` | 1 | `UReal` | `UReal` | `u/√(1−x²)`, **except** `\|x\|==1` exactly, where `u' = u` (library sidestep). `\|x\|>1` → NaN → Undefined |
| 18 | `acos` | 1 | `UReal` | `UReal` | identical shape to `asin` |

**All 18 are dot-calls.** Argument-type note: `matches` uses `isTypeOfUReal()` (exact) for arg0, so
`Real`/`Integer` receivers fall through to `Op_real_*`/`Op_integer_*` in the same bucket.

**These 18 are NOT the full `UReal` OCL surface.** `+ - * /`, unary `±`, `floor`, `round`,
`max`, `min`, `< > <= >=` (→ **`UBoolean`**) and `toString` live in `StandardOperationsNumber.java`
(E1) and must be ported with it. `toString` works **unmodified** there because
`URealType.isKindOfNumber()` is `true`.

### Extractor / refuter disagreements — UReal

| Claim | Extractor | Refuter | **Adopted** |
|---|---|---|---|
| `power` with `x=0`, `s>0` | one row: `u' = s·u·0^(s−1) = 0` ⇒ `UReal(0.0, 0.0)` | **REFUTED — an over-generalisation from three `s>1` data points.** Three rows are needed: **`s>1`** → `(0.0, 0.0)`; **`s=1`** → `Math.pow(0,0)=1.0` ⇒ `u'=u` ⇒ **`UReal(0.0, u)`**; **`0<s<1`** → `Math.pow(0,s−1)=+∞` ⇒ `u'=∞` (`u>0`) or `NaN` (`u=0`) ⇒ **`Undefined`**. Probe: `power(0,4)^1 → u=4.0`; `power(0,4)^0.5 → u=Infinity`. The fork's **own** golden file agrees: `URealExpression.in:227-228` `UReal(0,5).power(1/2).equals(UReal(0,5).sqrt()) -> true`, both sides Undefined | **Refuter.** Replace the single row with the three. **`s=1, x=0` is pinned by no oracle at all** — a previously unrecorded gap in this file's highest-traffic operation |
| The `power`/`sqrt` FIXME at `.in:229-234` | attributed to the **`(float)` narrowing**, quoting `1.016465997955662` vs `1.4142135623730951` | **REFUTED on all three counts.** (a) `1/2 = 0.5` is *exactly* representable in `float`, as is every other exponent in the corpus (`0, 3, −2, 3.5, 1.5, 4, 0.25, −3`) — the narrowing has **zero observable effect anywhere in the fork's own oracle**. (b) The real cause is the **discarded second-order term**: `1.016465997955662` is exactly `a+b` for `x=2,u=3,s=0.5`. (c) Against the binding jar, `power(2,3)^0.5` and `sqrt(2,3)` differ only in the *uncertainty*, by **1 ULP** (`…8214` vs `…8212`), and `URealValue.equals` rounds both to `1.0606601718` ⇒ the disabled assertion would evaluate **`true`** | **Refuter.** It is a **second obsolete skip**, like the `UReal(0,0).sqrt()` one — not "a case a port will trip over" |
| "the oracle jar is not vendored in the target repo" | `md5sum use-core/src/test/resources/historical/…` reported *No such file* | **REFUTED — it is there**, md5 `a3055f54205babaa27484fa94efdda1c`, byte-identical to the fork's `lib/` copy, alongside `use.jar` | **Refuter**, corroborated independently by `stage-01.md` §2 (sha256 `53b2a43f…` / `80ac8ae4…`). The port needs **no** "obtain the jar" step |
| `setUncertainty` "is the dual of `setValue`" | as stated | `setUncertainty.eval:181-184` uses an **unconditional** `(RealValue)` cast where `setValue.eval:291-294` uses `else if (args[1].isReal())` with a `double newValue = 0` fallback. Both unreachable given `matches`; a port that "harmonises" them is making a change, not a cleanup | **Refuter** (minor) |
| §0 wording | "the single static block" | `registerTypeOperations` is a static **method** | **Refuter** (cosmetic) |

**Coverage gap (UReal).** `grep -rn "sin()\|cos()\|tan()" FT/` returns **nothing**. Six of the 18
(`sin`, `cos`, `tan`, `asin`, `acos`, `atan` — **33 %**) have **no test and no `.in` case**. Their
semantics above come from the library source plus a jar probe, **not** from any fork-authored
expectation. This is the highest-risk part of the UReal registry.

## 2.3 `UInteger` — 12 classes, 13 registrations, 13 names

```bash
F=…/StandardOperationsUInteger.java
grep -c '^final class Op_uInteger_[A-Za-z]* extends OpGeneric {$' $F   # 12
grep -c 'OpGeneric.registerOperation('                            $F   # 13
grep -c 'return OPERATION;'                                       $F   # 12
```
`Op_uInteger_value` is registered **twice** — under `value` (line 13) and, as a **second instance**,
under the alias `toInteger` (line 17).

| # | Name | Arity | Argument types | Result (`matches` declares) | Semantics |
|---|---|---|---|---|---|
| 1/5 | `value` / `toInteger` | 1 | `UInteger` (typeOf) | **`UInteger`** (declared) — `eval` returns `IntegerValue` | Projects `x`; `u` **discarded**. **DEFECT** — declared ≠ produced; the sibling `Op_ureal_value` gets it right (`mkReal()`) |
| 2 | `setUncertainty` | 2 | `UInteger`, `IoR` | `UInteger` | `(x, \|newU\|)` — `u` replaced wholesale, absolutised by the delegate. Negative → abs; zero accepted. `setUncertainty(null)` fails **at compile time** |
| 3 | `uncertainty` | 1 | `UInteger` | `Real` | Projects `u`, always ≥ 0 |
| 4 | `setValue` | 2 | `UInteger`, `Integer` (**typeOf**, not kindOf) | `UInteger` | Value replaced, `u` carried across unchanged. A `Real` argument does **not** match |
| 6 | `toUReal` | 1 | `UInteger` | `UReal` | Lossless widening `(x:int,u) ↦ (x:double,u)` |
| 7 | `toReal` | 1 | `UInteger` | `Real` | Projects `x`, **destroys `u`** (contrast #6) |
| 8 | `abs` | 1 | `UInteger` | `UInteger` | `(\|x\|, u)`. `Math.abs(Integer.MIN_VALUE)` overflows silently |
| 9 | `div` | 2 | `{UInteger×UInteger, UInteger×Integer, Integer×UInteger}` | `UInteger` | Integer-truncating quotient. Four delegate branches: **A** `r==this` (reference identity) → `(1, 0)`; **B** exact divisor → `(x₁/x₂, u₁/x₂)`; **C** exact dividend → `(x₁/x₂, u₂/x₂²)`; **D** both uncertain → value `(int)floor(int-division)`, `u' = sqrt(\|u₁²/x₂\| + x₁²u₂²/x₂⁴)`. **Zero divisor → int `/ by zero` → `ArithmeticException` → Undefined** |
| 10 | `mod` | 2 | same | `UInteger` | Java `%` (sign of the **dividend**), with **exactly `div`'s uncertainty formula** in branch D. Zero divisor → Undefined |
| 11 | `sqrt` | 1 | `UInteger` | `UInteger` | `toUReal().sqrt().toUInteger()` — floors and **folds the lost fraction into `u`**: `u' = sqrt((u/(2√x))² + residue²)`. Negative → Undefined; `(0,0)` → `(0, 0.0)`; `(0, u>0)` → Undefined |
| 12 | `power` | 2 | `UInteger`, `Integer\|Real` (typeOf) | `UInteger` | `toUReal().power(s).toUInteger()`; exponent narrowed to **`float`**. `x=0,s=0` → Undefined; `x=0,s<0` → Undefined; `x=0,s>0` → `(0, 0.0)`. `UInteger(4,0).power(-2)` → **`UInteger(0, 0.0625)`** — the clearest demonstration that truncation loss becomes uncertainty |
| 13 | `neg` | 1 | `UInteger` | `UInteger` | `(−x, u)`. **Redundant** with prefix `-` (`Op_number_unaryminus` dispatches `isUInteger()` to the same call) — but **required for oracle parity**, since `UIntegerExpression.in:244-253` writes `.neg()` |

**Not in this file** (must be ported with E1): `+`, `-` (binary and unary), `*`, `/` (→ **`UReal`**
via `divideByR`), `< > <= >=` (→ **`UBoolean`**), `toString`, `max`/`min` (→ **`UReal`**, both
operands widened). The lattice hook that keeps `+ - *` returning `UInteger` rather than `UReal` is
`ArithOperation.matches`'s `getLeastCommonSupertype(...).isTypeOfUInteger()` clause, which sits
**before** the `UReal` fallback.

**Grammar parity.** `div` is an infix multiplicative operator in **both** trees (`OCL.g:302`,
`G/base/OCLBase.gpart:224`, both `(STAR | SLASH | 'div')`); `mod` is **not** an infix keyword in
either. **No grammar change is needed** for `UInteger div UInteger` — only the registry entry.

### Extractor / refuter disagreements — UInteger

| Claim | Extractor | Refuter | **Adopted** |
|---|---|---|---|
| `sqrt` guards | "line 382 contributes nothing that 385 does not already cover; **385 is the only guard that ever fires**"; §6.3 "both first guards are dead code" | **REFUTED.** Line 382 is `isNaN(result.value()) \|\| isInfinite(result.**uncertainty()**)`. Only the **first** disjunct is dead (`value()` returns `int`). The second tests the *uncertainty* and runs **before** 385, so for `(0, u>0)` it is **382–383 that throws**. 385 fires only for the NaN case. The claim is true for `power` (427 tests `value()` in both disjuncts), false for `sqrt` | **Refuter — and this is not cosmetic.** The extractor's stated intent invites a porter to "repair" 382 to `isInfinite(result.value())`, which **deletes the only live infinite-uncertainty trap**; if 385 is then dropped as "redundant", `UInteger(0,u>0).sqrt()` silently changes from `Undefined` to `UInteger(0, Infinity)`. **Port instruction: transcribe 382–386 verbatim.** |
| `[in:N]` oracle citations | 18 citations in the `div`/`mod` sections and 4 earlier | **REFUTED — all 18 point at the wrong lines** (e.g. `(2,3).mod((5,4))` is at **1491-1492**, cited as 1500-1501). Every claimed expression+result pair **does exist with exactly the stated result** — the semantics are not fabricated — but a reviewer regenerating expectations from the numbers gets the wrong `mod` cases entirely. 29 other citations verified correct | **Refuter.** Re-derive every `[in:N]` with `grep -nF '<expr>' UIntegerExpression.in` before use |
| `div` call form | "**infix** (grammar) **and dot**" | The "and dot" half has no evidence and the evidence points the other way: `'div'` is an implicit literal token in `OCL.g:302` while dot-calls bind `name=IDENT` (`OCL.g:449`), so the literal wins for the exact text `div` and `u.div(v)` should **fail to parse**. `grep -rn '\.div('` over both test trees → nothing | **Refuter**, with the negative marked **UNVERIFIABLE** (the parser was not run). **Stronger consequence the extractor missed:** since `isInfixOrPrefix()` is `false`, `stringRep` prints `a.div(b)` — so the fork's pretty-printed output for `UInteger div` is **not re-parseable**. A round-tripping bug, not the "cosmetic" divergence §4.9 calls it |
| `matches` signature-style split | "ops #1–#7 use `Type[] params`, #8–#13 use `Type params[]` (lines 261, 293, 333, 372, 413, 457)" | **WRONG for `div` and `mod`** — 293 and 333 are `Type[] params`. Actual C-style set: `abs`, `sqrt`, `power`, `neg` only | **Refuter** (cosmetic) |
| `ExpStdOp` line citations | fork "299-308" / "308-315" | matches **neither** tree: fork is 298 / 317 / 318; 7.5.0 is 291 / 310 / 311. The *policy* described is correct in both | **Refuter** (cosmetic) |
| "all exponents in the oracle (`0, 3, −2, 3.5, 1.5, 4, 0.25`)" | as stated | omits **`−3`** (`.in:216`), which the same section cites two bullets earlier. 15 `.power(` cases in total. Conclusion survives (`−3` is exact in `float`) | **Refuter** (cosmetic) |

## 2.4 `UString` — 21 classes, 22 registrations, 21 names

```bash
F=…/StandardOperationsUString.java
grep -cE '^final class Op_uString_[A-Za-z_]+ extends OpGeneric \{$' $F   # 21
grep -cE 'OpGeneric\.registerOperation\(new Op_uString_'            $F   # 22
grep -oE 'new Op_uString_[A-Za-z_]+' $F | sort | uniq -c | sort -rn | head -1   # 2 new Op_uString_uConcat
grep -c 'return OPERATION;' $F                                            # 21 -- all OPERATION, all dot-calls
```
`Op_uString_uConcat` is registered at lines **19 and 21**; line 21 sits exactly where a distinct
third registration was presumably intended. The duplicate is dead but harmless
(`ArrayListMultimap` + first-match `create`).

| # | Name | Arity | Argument types | Result | Semantics |
|---|---|---|---|---|---|
| 1 | `value` | 1 | `UString` (typeOf) | `String` | Projects the string; **confidence discarded** |
| 2 | `confidence` | 1 | `UString` | `Real` | Projects `sConf`. A **NaN confidence is observable here** (see #7) |
| 3 | `setValue` | 2 | `UString`, `String` (**typeOf**) | `UString` | Replaces the string, **keeps** the confidence |
| 4 | `setConfidence` | 2 | `UString`, `IoR` | `UString` | Replaces the confidence. **`c ∉ [0,1]` throws `IllegalArgumentException` and escapes the evaluator** (not `ArithmeticException`). **NaN silently accepted** |
| 5 | `at` | 2 | `UString`, `Integer` | **`UString`** (the `-> String` comment is wrong) | `uAt(idx)` = `uSubstring(idx,idx)`, **1-based**, confidence carried. **`idx<1` throws `IllegalArgumentException`; `idx>\|s\|` throws `StringIndexOutOfBoundsException`** — both escape. Upstream `Op_string_at` returns `Undefined` for both |
| 6 | `character` | 1 | `UString` | `Sequence(UString)` | **Singular name** (upstream is `characters`). One element per char, **each carrying the receiver's confidence unchanged** — replicated, not divided. Empty string → empty sequence |
| 7 | `+` | 2 | `UString\|String` ×2, at least one **exactly** `UString` | `UString` | value concatenated; `c = max(1 − (\|a\|(1−c_a)+\|b\|(1−c_b))/(\|a\|+\|b\|), 0)` — the **character-length-weighted mean**, clamped at 0. `('abc',0.9)+('de',0.5)` → `0.740`. **Both operands empty ⇒ `c = NaN`** (0/0), which then leaks through `confidence`, comparisons and `setValue`. `isInfixOrPrefix()` → `false`, so it prints `a.+(b)`. **`matches` dereferences `params[1]` before checking `params.length == 2`** ⇒ AIOOBE on a 1-argument `+` that earlier candidates decline |
| 8 | `indexOf` | 2 | `UString`, `String` (typeOf) | **declared `UString`, returns `IntegerValue`** | Raw `String.indexOf`, **0-based**, `-1` absent, empty needle → `0`. **Confidence discarded.** Upstream `Op_string_indexOf` is **1-based** with different sentinels — the two families disagree by one |
| 9 | `substring` | **3** | `UString`, `Integer`, `Integer` | `UString` | **1-based inclusive on both ends**: `substring(1,2)` on `"abc"` = `"ab"`. **Confidence copied unchanged regardless of how much survives** (contrast `+`). `eval` wraps everything in `catch (Exception) → new UStringValue("", 1)` — **confidence 1.0**, not the receiver's, not `Undefined`. The inline comment claiming this matches `String.substring` is **false** |
| 10/11 | `toLowerCase` / `toUpperCase` | 1 | `UString` | `UString` | Confidence unchanged. **Default-locale sensitive** (Turkish-I). No `toLower`/`toUpper` aliases, unlike upstream |
| 12 | `size` | 1 | `UString` | **`UInteger`** (not `Integer`) | `UInteger(\|s\|, \|s\|·(1−c))` — **the one place the uncertainty changes representation**: an absolute edit-distance budget in characters, not a probability. `('abc',0.9)` → `u = 0.29999999999999993` |
| 13 | `toString` | 1 | `UString` | **declared `UString`, returns `StringValue`** | Bare value component; the `UString('…', c)` rendering is **not** produced. Functionally identical to `value` |
| 14 | `toInteger` | 1 | `UString` | `Integer` | `Integer.parseInt`; confidence discarded. **Unparseable → Java `null`**, which escapes the evaluator |
| 15 | `toReal` | 1 | `UString` | `Real` | `Double.parseDouble`; **`"NaN"` and `"Infinity"` parse successfully** ⇒ a non-finite `Real` is reachable from OCL. Unparseable → **`null`** |
| 16 | `toBoolean` | 1 | `UString` | `Boolean` | `Boolean.parseBoolean` — **never throws**, so the `catch`/`null` is dead code. Every input other than case-insensitive `"true"` → `false` |
| 17 | `toUBoolean` | 1 | `UString` | `UBoolean` | **The only conversion that preserves uncertainty.** Compares case-insensitively against `"TRUE"` then `"FALSE"` at threshold 0.5, falling back to `(true, 0.5)` (maximal ignorance). `("false",0.4)` → **`(true, 0.6)`** — a low-confidence `"false"` reads as probably TRUE |
| 18–21 | `<` `<=` `>` `>=` | 2 | `UString\|String` ×2 (**no `someOfThemIsUString` guard**) | `UBoolean` | Value: `String.compareTo`, **case-sensitive UTF-16 order** (`'Z' < 'a'`). Confidence: the **product** `c_a·c_b`. Then `UBoolean` normal form: false comparisons report `(true, 1−conf)`. **`('de',0.0) < ('abc',1.0)` → `(true, 1.0)`** — a FALSE comparison at confidence 0 becomes certain-TRUE (the library's own Spanish comment warns of this "conmuta" effect below 0.7) |

**The single most dangerous porting constraint in this file**: `<`, `<=`, `>`, `>=` use
`isKindOfUString`, which `StringType` answers `true`, so they **also match `String × String`**.
Only registration order (`F/uml/ocl/expr/operations/OpGeneric.java:89` String, `:96` UString) keeps every existing string
comparison typed `Boolean` rather than `UBoolean`.

**Never registered though implemented**: `UStringValue.at(int) : StringValue` (the *guarded* variant)
and `UStringValue.uEqualsIgnoreCase`. Relative to 7.5.0's `String` family there is no UString
`concat`, `split`, `equalsIgnoreCase`, `characters` (plural), `toLower` or `toUpper`.

### Extractor / refuter disagreements — UString

| Claim | Extractor | Refuter | **Adopted** |
|---|---|---|---|
| `toString` collision list | names `Op_enum_toString` and `Op_sBoolean_toString` | **Both are fabricated identifiers that grep to nothing.** The Enum one is `final class Op_toString` (`F/uml/ocl/expr/operations/StandardOperationsEnum.java:46`); the SBoolean one is an **anonymous `OpGeneric`** in the enum constant `TO_STRING` (`F/uml/ocl/expr/operations/StandardOperationsSBoolean.java:373-380`) — that file contains **zero** `final class Op_` declarations. The *collision* (six providers) is real | **Refuter** |
| "`UReal`/`UInteger`/`UBoolean` register the same names" for `value, confidence, setValue, setConfidence` | as stated | **False for two of the four.** `UReal` and `UInteger` register **`uncertainty`/`setUncertainty`**, not `confidence`/`setConfidence`; only `UBoolean` shares those two. **The naming convention across the uncertain types is inconsistent** — `confidence` for UString/UBoolean vs `uncertainty` for UReal/UInteger — which the table as written hides | **Refuter.** This matters for the port's API surface |
| collision inventory for `toInteger`/`toReal` | lists only `Op_string_*` | incomplete: also `Op_ureal_toInteger`/`toReal`, `Op_uInteger_toReal`, and — easily skipped — the **alias registration** `registerOperation("toInteger", new Op_uInteger_value(), opmap)`. Plus `toUBoolean` collides with SBoolean's `TO_U_BOOLEAN`. None changes resolution (each `matches` demands its own `isTypeOf*`) | **Refuter**; conclusion unchanged, inventory is not exhaustive |
| `StandardOperationsString` "byte-identical" between trees | as stated | identical only **after CR stripping** (the fork's copy is CRLF-free, 7.5.0's is CRLF). The evidence command already strips; only the adjective overstates | **Refuter** (cosmetic) |
| `UString.uEquals` = `c_this · c_other` | as stated | omits the identity shortcut `(this==u) ? 1.0 : calculateConf(u)` (`UDT/UString.java:102`). Unreachable from `uToUBoolean` (which builds a fresh `UString`), so no reported value changes — but a reimplementation from that description would drop it | **Refuter** |
| `ArithOperation` at `F/uml/ocl/expr/operations/StandardOperationsNumber.java:56`; length guard at `:63` | as stated | **54** and **62** — both load-bearing for the AIOOBE argument, which is otherwise confirmed | **Refuter** (cosmetic) |
| NaN propagation through `< <= > >=` | inferred from the `[0,1]` guard, marked UNVERIFIED | **measured**: `new UBoolean(true, NaN)` is accepted and prints `UBoolean(true,   NaN)` | **Refuter** — gap closed |

**Coverage: `UString` has no behavioural oracle at all.**
`grep -rln "UString\|uString"` over `FT/` matches **only** `uml/ocl/type/TypeTest.java`. See §6.4.

## 2.5 `SBoolean` — 39 operations *(gated by B2)*

```bash
cd …/uml/ocl/expr/operations
grep -cE '^\s+[A-Z][A-Z_0-9]*\(new OpGeneric\(\) \{' StandardOperationsSBoolean.java   # 39
grep -cE '^ {8}public String name\(\) \{'             StandardOperationsSBoolean.java   # 39
grep -oE '^ {12}return "[a-zA-Z]+";' StandardOperationsSBoolean.java | sort -u | wc -l # 39 DISTINCT
# TRAP: a naive `grep -c 'new OpGeneric()'` returns 45 -- it counts the six commented-out constants.
```

**Registry idiom differs from every sibling**: a Java `enum` whose constants each wrap one
anonymous `OpGeneric`; `registerTypeOperations` loops over `values()`.

**Receiver predicates are inconsistent, and the inconsistency is load-bearing:**

* **`isTypeOfSBoolean()`** (exact) — all unary ops plus `isCertain`/`isUncertain`. A `UBoolean`
  receiver **cannot** call `.belief()`.
* **`isKindOfSBoolean(EXCLUDE_VOID)`** — all binary/ternary ops. `true` for `SBooleanType` (`:17-20`),
  **`UBooleanType` (`:22-25`) and `BooleanType` (`:54-57`)** — so **every binary SBoolean operation
  also accepts plain `Boolean` and `UBoolean` in either position**. Complete: `grep -rn 'public
  boolean isKindOfSBoolean' type/*.java` → exactly SBooleanType:18, UBooleanType:23, BooleanType:55,
  VoidType:128, TypeImpl:375. No sixth override.

| # | Name | Arity | Argument types | Result | Semantics (uncertainty handling) |
|---|---|---|---|---|---|
| 1 | `projection` | 1 | typeOf SBoolean | `Real` | `P = adjust(b + a·u)` — collapses the belief/uncertainty split into a probability by redistributing `u` per the base rate |
| 2–5 | `belief` `disbelief` `uncertainty` `baseRate` | 1 | typeOf | `Real` | Project `b`, `d`, `u`, `a` respectively |
| 6 | `uncertaintyMaximized` | 1 | typeOf | `SBoolean` | Same `P`, same `a`, `u` pushed as high as constraints allow (one of `b`/`d` → 0). Three explicit vacuous guards prevent division by `a` and `1−a` |
| 7 | `projectiveDistance` | 2 | kindOf ×2 | `Real` | `\|P₁ − P₂\|`. **The `//` comment says `-> SBoolean` and is wrong**; the code is right |
| 8 | `conjunctiveCertainty` | 2 | kindOf ×2 | **declared `SBoolean`, returns `Real`** | `(1−u₁)(1−u₂)` — pure confidence product, ignoring b/d. **Defect D1** |
| 9 | `degreeOfConflict` | 2 | kindOf ×2 | **declared `SBoolean`, returns `Real`** | `projectiveDistance · conjunctiveCertainty` — disagreement weighted by joint confidence; two vacuous opinions have zero conflict. **Defect D1** |
| 10 | `deduceY` | **3** | kindOf ×3 | `SBoolean` | Binomial deduction. **The highest-risk operation to port** — see the refutation below |
| 11 | `toUBoolean` | 1 | typeOf | `UBoolean` | `new UBoolean(true, projection())` — uncertainty mass fully absorbed into the confidence; `u` is gone |
| 12 | `toString` | 1 | typeOf | `String` | Goes through **`SBooleanValue.toString(StringBuilder)`** = `"SBoolean(" + round(b,3) + ", " + …` — **not** the delegate's `String.format("SBoolean(%5.3f, …)")`. A port routing through the library would emit space-padded fields and differ |
| 13 | `not` | 1 | typeOf | `SBoolean` | `(d, b, u, 1−a)` — **`u` untouched**. **The only op with `isInfixOrPrefix() == true`** |
| 14 | `and` | 2 | kindOf ×2 | `SBoolean` | Binomial multiplication; `u` computed as the **residual** `1 − d − b`, not by the textbook formula. `this == s` (reference identity) → `clone()`. `a₁a₂ == 1` guards the division |
| 15 | `or` | 2 | kindOf ×2 | `SBoolean` | Binomial comultiplication; `u` residual. Same identity shortcut; `a₁+a₂ == a₁a₂` guards |
| 16 | `xor` | 2 | kindOf ×2 | `SBoolean` | `b = \|b₁−b₂\|`, **`u = u₁·u₂`**, `d` residual, `a = \|a₁−a₂\|`. No branch, no division. **Not** the classical form (commented out) ⇒ not consistent with Boolean `xor` |
| 17 | `equivalent` | 2 | kindOf ×2 | `SBoolean` | `xor().not()`. Also backs `SBooleanValue.uEquals`, i.e. `=` via `StandardOperationsAny` |
| 18 | `implies` | 2 | kindOf ×2 | `SBoolean` | `not().or(s)` — **explicitly chosen "to be consistent with UBoolean, because in Subjective Logic this is not the case"** |
| 19 | `getRelativeWeight` | 1 | typeOf | `Real` | `isDogmatic() ? relativeWeight : 0.0` — **collapses to 0 for any `u != 0`** |
| 20–21, 23–24 | `isAbsolute` `isVacuous` `isDogmatic` `isMaximizedUncertainty` | 1 | typeOf | `Boolean` | `b==1\|\|d==1` / `u==1` / `u==0` / `d==0\|\|b==0`. Exact `double` equality (safe-ish: `adjust()` rounds to 6 dp, but `0.9999996` rounds to `1.0`) |
| 22, 25 | `isCertain` / `isUncertain` | 2 | typeOf, `IoR` | `Boolean` | `1−u ≥ t` / `1−u < t`. **No threshold range validation anywhere** |
| 26 | `uncertainOpinion` | 1 | typeOf | `SBoolean` | **Alias of #6** — `SBooleanValue.uncertainOpinion()` calls `uncertaintyMaximized()` directly |
| 27 | `certainty` | 1 | typeOf | `Real` | `1 − u`. Its NaN guard `uncertainty() == (0.0/0.0)` is **dead** (`NaN == NaN` is false) |
| 28–35 | 8 collection fusions | 2 | kindOf SBoolean, **kindOf Collection (element type unchecked)** | `SBoolean` | Each converts the argument with `asSequence()` and **prepends the receiver** ⇒ fused size `1 + \|arg\|`. Empty-collection behaviour **differs per op**: `minimum`/`majority`/`beliefConstraint`/`consensusAndCompromise` **throw**; `average`/`aleatoryCumulative`/`epistemicCumulative`/`weighted` **return the receiver**. `consensusAndCompromiseFusion` is **O(4ⁿ)** and requires all base rates equal |
| 36 | `discount` | 2 | kindOf SBoolean, kindOf Collection | `SBoolean` | **The one collection op that does NOT prepend the receiver** — the collection is a *trust path*, the receiver the advisor's opinion. `p = Π projection(tᵢ)`; `b'=p·b`, `d'=p·d`, **`u' = 1 − p(b+d)`**. **Empty collection ⇒ identity**, no exception |
| 37–38 | `min` / `max` | 2 | kindOf ×2 | `SBoolean` | Whole-opinion selection by projected probability; ties go to the **receiver**. Uncertainty not combined |
| 39 | `applyOn` | 2 | kindOf SBoolean, **kindOf UBoolean** | `SBoolean` | Rebases onto `a' = x.getC()`; **`u` preserved exactly**, `b' = min(a'·b/a, 1−u)`. Explicit guards for `a==0`, `u==1`, `a==a'` |

**Six further enum constants are commented out** (lines 1281–1479) and register nothing:
`minimumFusion`, `majorityFusion`, `averageFusion`, `cumulativeFusion`,
`epistemicCumulativeFusion`, `weightedFusion` — the **binary** forms of the registered collection
fusions. Their `SBooleanValue` backing methods are still live code. Do not resurrect by accident.
Also unregistered: `SBooleanValue.createDogmaticOpinion`, `createVacuousOpinion`.

> **Type-lattice trap for the port.** `SBooleanType` must **not** gain an `isKindOfUBoolean`
> override. `UBoolean::and`/`not` test `isKindOfUBoolean(INCLUDE_VOID)`, which is `false` for
> `SBooleanType` (`TypeImpl:210`, no override anywhere up the `UncertainBooleanType → UncertainType
> → BasicType` chain). Adding one — an easy thing to do while wiring the lattice — would silently
> reroute `and`/`or`/`xor`/`implies`/`not`/`toString` on SBoolean receivers to the **UBoolean**
> registry. (Refuter R6.)

### Extractor / refuter disagreements — SBoolean

| Claim | Extractor | Refuter | **Adopted** |
|---|---|---|---|
| `xor` of two vacuous opinions | `(0, 0, 1, 0)` | **WRONG.** `a = \|a₁ − a₂\|`, and `isVacuous()` (`u==1`) does not constrain `a`. Correct: **`(0, 0, 1, \|a₁ − a₂\|)`**, which equals `(0,0,1,0)` only when the base rates coincide | **Refuter** |
| `deduceY` divisor list | six "unguarded" denominators, incl. `(yGivenX.b − yGivenNotX.b)` | **Wrong in both directions.** `(yGivenX.b − yGivenNotX.b)` (L349) executes only under `yGivenX.b > yGivenNotX.b` (L346) ⇒ strictly positive, **a false positive**. **Missed hazard**: `(yGivenNotX.b − yGivenX.b)` at **L381** runs under a `<=` guard (L378) that **permits equality**. Correct: of the four difference-divisors exactly **two** can be zero — L343 and **L381**; the four non-difference divisors `(b+a·u)`, `(d+(1−a)u)`, `y.a`, `(1−y.a)` are genuinely unguarded as stated | **Refuter** |
| `deduceY` "eight-way case analysis … selected by" | implies exclusivity | **The eight blocks are sequential `if`s, not `else if`** — a later match silently overwrites `K`. The case-II family partitions (same threshold expression); **case III does not**: III.A.1 compares against `yGivenX.b + a(1 − yGivenNotX.b − yGivenX.d)` while III.A.2/B.1/B.2 compare against `yGivenX.b + a(1 − yGivenX.b − yGivenNotX.d)`. For one straddle III.A.1 and III.B.1 both fire and **textual order decides**; for the opposite straddle **neither** fires and `K` stays `0` | **Refuter. A bug-compatible port MUST preserve the source order of the eight `if`s** — the extractor never says so |
| `K` "defaults to 0 when neither monotonicity pattern holds" | as stated | incomplete — `K` also stays `0` when the case-III pattern *does* hold but the mismatched thresholds leave all four sub-conditions false | **Refuter** |
| `deduceY` `K` numerators | not flagged | **Two type-inconsistent numerators**: III.A.2 (L368) computes `(bIy − yGivenX.d)` (belief minus **disbelief**) and III.B.1 (L374) computes `(dIy − yGivenNotX.b)` (disbelief minus **belief**), where their siblings II.A.1/II.B.2 are type-consistent. These read as **transcription errors in the oracle** and are load-bearing for a verbatim port | **Refuter** — add to the `deduceY` trap list |
| registration-order competitor list | names only `StandardOperationsBoolean` and `…Number` | **incomplete** — `UReal`(:93), `UBoolean`(:94), `UInteger`(:95), `UString`(:96) all sit between them and SBoolean(:97), and `StandardOperationsUBoolean` registers `toString`, `and`, `not`, `or`, `xor`, `implies` ahead of SBoolean. The conclusion holds, but only for a reason never checked (`isKindOfUBoolean` is false for `SBooleanType`) | **Refuter** — see the trap box above |
| D6 line list for the four infix ops | "448, 481, 514, 547, 580" | that is **five**; **547 is `equivalent`**, which has no `'equivalent'` grammar production and is correctly a dot-call. The four are 448, 481, 514, 580 | **Refuter** (cosmetic) |
| `uncertaintyMaximized` "// Replaced by another version at L300-301" | as stated | that comment is on **L301 only**; L300 is `//return this.increasedUncertainty();` | **Refuter** (cosmetic) |

## 2.6 Registration order — the highest-blast-radius constraint in the whole port

`OpGeneric.registerOperations` must keep this order exactly:

```
Any, Object, Enum, Number, String, Boolean,          <- upstream, unchanged
    UReal (:93), UBoolean (:94), UInteger (:95), UString (:96), SBoolean (:97),
Collections                                          <- upstream, unchanged
```

Because `matches` is first-match-wins and `BooleanType.isKindOfUBoolean()`/`isKindOfSBoolean()` and
`StringType.isKindOfUString()` all answer `true`, the uncertainty overloads **do** accept plain
`Boolean`/`String` operands. Only the ordering keeps existing models behaving normally.

| If you move… | …then |
|---|---|
| `StandardOperationsUBoolean` before `StandardOperationsBoolean` | **every plain-`Boolean` `and`/`or`/`not`/`implies`/`xor` in every existing model silently changes result type from `Boolean` to `UBoolean`** |
| `StandardOperationsUString` before `StandardOperationsString` | **every `'a' < 'b'` silently changes from `Boolean` to `UBoolean`** |
| `StandardOperationsSBoolean` before `StandardOperationsUBoolean` | **every `equivalent` (and `and`/`or`/`xor`/`implies`/`toString`) silently changes to `SBoolean`** (refuter finding, §2.1) |
| the uncertainty registries before `StandardOperationsNumber` (a tempting fix for **B8**) | **`Integer + Integer` retypes** — `Op_number_add.matches` falls through `isTypeOfInteger → isTypeOfReal → isTypeOfUInteger → mkUReal`; reordering changes the static type of ordinary arithmetic |

The direction that is *safe*: the core `and`/`or`/`not`/`implies`/`xor`/`toString` are all
`isKindOfBoolean`-based, and **`UBooleanType` never overrides `isKindOfBoolean`** (only definition on
its chain is `TypeImpl:233-236` → `false`), so they never accept `UBoolean` operands. Order matters
in one direction only.

---

# 3. Type lattice

Established by an executable oracle (`docs/port2/spec-parts/11-types-oracle.sh`) that compiles the
fork's own type classes verbatim and prints `conformsTo`, `allSupertypes` and
`getLeastCommonSupertype` for every pair, then does the same for the 7.5.0 classes so the two are
directly diffable.

```bash
cd /home/xoruser/msc-4/use-msc2026
bash docs/port2/spec-parts/11-types-oracle.sh     # read-only; no Maven; nothing written outside its workdir
```

## 3.1 Conformance grid, both directions

`A.conformsTo(B)` — row `A`, column `B`. `T` = true, `.` = false. **39 of the 121 ordered pairs are
`true`.**

| A \ B | UBoolean | UInteger | UReal | UString | SBoolean | Boolean | Integer | Real | String | OclVoid | OclAny |
|---|---|---|---|---|---|---|---|---|---|---|---|
| **UBoolean** | T | . | . | . | T | . | . | . | . | . | T |
| **UInteger** | . | T | T | . | . | . | . | . | . | . | T |
| **UReal**    | . | . | T | . | . | . | . | . | . | . | T |
| **UString**  | . | . | . | T | . | . | . | . | . | . | T |
| **SBoolean** | . | . | . | . | T | . | . | . | . | . | T |
| **Boolean**  | T | . | . | . | T | T | . | . | . | . | T |
| **Integer**  | . | T | T | . | . | . | T | T | . | . | T |
| **Real**     | . | . | T | . | . | . | . | T | . | . | T |
| **String**   | . | . | . | T | . | . | . | . | T | . | T |
| **OclVoid**  | T | T | T | T | T | T | T | T | T | T | T |
| **OclAny**   | . | . | . | . | . | . | . | . | . | . | T |

**The eight new edges, in the historical author's own words** (`FT/uml/ocl/type/TypeTest.java:81-160`):
`String < UString`, `UBoolean < SBoolean`, `Boolean < SBoolean`, `Boolean < UBoolean`,
`Real < UReal`, `UInteger < UReal`, `Integer < UReal`, `Integer < UInteger`.

**Naming reads backwards** — `UBoolean` is a **supertype** of `Boolean`, `UReal` of `Real`,
`UInteger` of `Integer`, `UString` of `String`, and `SBoolean` of `UBoolean`. Reviewers repeatedly
get this wrong; state it in the ported javadoc.

**Sanity check that constrains the whole port.** Restricted to
`{Boolean, Integer, Real, String, OclVoid, OclAny, UnlimitedNatural}`, the fork's 49-cell
`conformsTo` block **and** its 49-cell `getLeastCommonSupertype` block are **byte-identical** to
7.5.0's. The extension is purely additive: it introduces no new relation among the classical OCL
types and rewires none. **Any port that changes a classical-vs-classical cell has a bug.** The
oracle script ends by asserting exactly this.

The eleven `conformsTo` bodies, verbatim. **Citation convention in this block, made explicit after
audit-02 §1.3:** the line number is the **method declaration**; the quoted body is the line
**immediately after it**. All eleven are consistently off by one in the same direction
(`UBooleanType:43/44`, `UIntegerType:48/49`, `URealType:13/14`, `UStringType:31/32`,
`SBooleanType:32/33`, `BooleanType:63/64`, `IntegerType:71/72`, `RealType:62/63`,
`StringType:57/58`, `VoidType:23/24`, `OclAnyType:77/78`), and every quoted body is textually
correct. Add 1 before running `sed -n 'NNp'`. All eleven files are in the **`F/`** tree.

```java
UBooleanType.java:43   return other.equals(this) || other.isTypeOfOclAny() || other.isTypeOfSBoolean();
UIntegerType.java:48   return other.isTypeOfUInteger() || other.isTypeOfUReal() || other.isTypeOfOclAny();
URealType.java:13      return equals(t) || t.isTypeOfOclAny();
UStringType.java:31    return equals(other) || other.isTypeOfOclAny();
SBooleanType.java:32   return other.isTypeOfSBoolean() || other.isTypeOfOclAny();
BooleanType.java:63    return this.equals(other) || other.isTypeOfUBoolean() || other.isTypeOfOclAny() || other.isTypeOfSBoolean();
IntegerType.java:71    return !t.isTypeOfVoidType() && (t.isKindOfNumber(VoidHandling.EXCLUDE_VOID) || t.isTypeOfOclAny());
RealType.java:62       return equals(t) || t.isTypeOfOclAny() || t.isTypeOfUReal();
StringType.java:57     return equals(t) || t.isTypeOfOclAny() || t.isTypeOfUString();
VoidType.java:23       return true;
OclAnyType.java:77     return other.isTypeOfOclAny();
```

`equals` is class identity for every basic type (`F/uml/ocl/type/BasicType.java:52-58`,
`obj.getClass().equals(getClass())`), so `UBoolean.equals(SBoolean)` is `false` in both directions
even though they share a Java superclass.

**A new supertype relation can be installed from *either* side**, and the fork uses both idioms:

* from the **subtype's** side — `BooleanType.conformsTo` naming `other.isTypeOfUBoolean()`
  (visible in a diff of `conformsTo`);
* from the **supertype's** side — `UIntegerType.isKindOfNumber() → true`, which makes
  `IntegerType.conformsTo` accept it with **no edit to `IntegerType.conformsTo` at all**
  (**invisible** in a diff of `conformsTo`, and easy to lose — see E18).

## 3.2 `allSupertypes()`

| type | fork | 7.5.0 | delta |
|---|---|---|---|
| `UBoolean` | `{UBoolean, SBoolean, OclAny}` | — | new |
| `UInteger` | `{UInteger, UReal, OclAny}` | — | new |
| `UReal` | `{UReal, OclAny}` | — | new |
| `UString` | `{UString, OclAny}` | — | new |
| `SBoolean` | `{SBoolean, OclAny}` | — | new |
| `Boolean` | `{Boolean, UBoolean, SBoolean, OclAny}` | `{Boolean, OclAny}` | **+2, mutates upstream `testSupertype` — B5** |
| `Integer` | `{Integer, UInteger, Real, UReal, OclAny}` | `{Integer, Real, OclAny}` | **+2 — B5** |
| `Real` | `{Real, UReal, OclAny}` | `{Real, OclAny}` | **+1 — B5** |
| `String` | `{String, UString, OclAny}` | `{String, OclAny}` | **+1 — B5** |
| `OclVoid` | throws `UnsupportedOperationException` | same | none |
| `OclAny` | `{OclAny}` | same | none |
| `UnlimitedNatural` | `{UnlimitedNatural, Integer, Real, OclAny}` | same | **not extended — B11** |

**Implementation detail to pick deliberately**: `UBooleanType.allSupertypes` adds
`TypeFactory.mkUBoolean()` rather than `this` (`:35`), and `UStringType` adds `mkUString()` (`:25`),
whereas `UIntegerType` (`:43`) and `URealType` (`:35`) add `this`. Because the factory interns,
these are the same object for factory-produced instances — but **not** for a directly constructed
one, and `FT/uml/ocl/type/TypeTest.java:380-403` constructs `new UIntegerType()`, `new URealType()`,
`new UBooleanType()`, `new SBooleanType()` directly. Pick one idiom and record it.

**`getLeastCommonSupertype` needs no code.** `TypeImpl.getLeastCommonSupertype` intersects
`allSupertypes()` and is **logically identical** in both trees (fork `:80-146`, 7.5.0 `:83-149`);
`UniqueLeastCommonSupertypeDeterminator` is **byte-identical** apart from a dropped `$Id` line. Both
acquire the uncertainty behaviour for free. One subtlety: 7.5.0 short-circuits when the intersection
is `{X, OclAny}` (`T/uml/ocl/type/TypeImpl.java:123-127`); with the fork's larger supertype sets the intersection is
often size 3+ and the general loop at `:132-146` runs instead. **The answers are the same; do not
"optimise" that loop away.**

Resulting LCS table (a *consequence* of the two tables above, not separate code):

| A \ B | UBoolean | UInteger | UReal | UString | SBoolean | Boolean | Integer | Real | String | OclVoid | OclAny |
|---|---|---|---|---|---|---|---|---|---|---|---|
| **UBoolean** | UBoolean | OclAny | OclAny | OclAny | SBoolean | UBoolean | OclAny | OclAny | OclAny | UBoolean | OclAny |
| **UInteger** | OclAny | UInteger | UReal | OclAny | OclAny | OclAny | UInteger | UReal | OclAny | UInteger | OclAny |
| **UReal** | OclAny | UReal | UReal | OclAny | OclAny | OclAny | UReal | UReal | OclAny | UReal | OclAny |
| **UString** | OclAny | OclAny | OclAny | UString | OclAny | OclAny | OclAny | OclAny | UString | UString | OclAny |
| **SBoolean** | SBoolean | OclAny | OclAny | OclAny | SBoolean | SBoolean | OclAny | OclAny | OclAny | SBoolean | OclAny |
| **Boolean** | UBoolean | OclAny | OclAny | OclAny | SBoolean | Boolean | OclAny | OclAny | OclAny | Boolean | OclAny |
| **Integer** | OclAny | UInteger | UReal | OclAny | OclAny | OclAny | Integer | Real | OclAny | Integer | OclAny |
| **Real** | OclAny | UReal | UReal | OclAny | OclAny | OclAny | Real | Real | OclAny | Real | OclAny |
| **String** | OclAny | OclAny | OclAny | UString | OclAny | OclAny | OclAny | OclAny | String | String | OclAny |
| **OclVoid** | UBoolean | UInteger | UReal | UString | SBoolean | Boolean | Integer | Real | String | OclVoid | OclAny |
| **OclAny** | OclAny | … all OclAny … | | | | | | | | | OclAny |

**Why this table is the load-bearing one, not `conformsTo`:** operation resolution goes through
`getLeastCommonSupertype`. `Op_equal.matches` (`T/uml/ocl/expr/operations/StandardOperationsAny.java:45-50`)
tests `params[0].getLeastCommonSupertype(params[1]) != null`. **A new type that overrides
`conformsTo` but forgets `allSupertypes()` will type-check in isolation and then fail to resolve
`=`, `<>`, `+` and every other overloaded operator**, reported as the unhelpful
``Undefined operation `…'`` from `ExpStdOp.create` — sending the investigation to the grammar
instead of the type lattice.

## 3.3 Predicate battery (`EXCLUDE_VOID`; `.` = false)

**Bold** cells are set by an **edit to an upstream file** rather than by a new file.

| predicate | UBoolean | UInteger | UReal | UString | SBoolean | Boolean | Integer | Real | String | OclVoid | OclAny |
|---|---|---|---|---|---|---|---|---|---|---|---|
| `isKindOfNumber` | . | T | T | . | . | . | T | T | . | . | . |
| `isTypeOfUInteger` | . | T | . | . | . | . | . | . | . | . | . |
| `isKindOfUInteger` | . | T | . | . | . | . | **T** | . | . | . | . |
| `isTypeOfUReal` | . | . | T | . | . | . | . | . | . | . | . |
| `isKindOfUReal` | . | T | T | . | . | . | **T** | **T** | . | . | . |
| `isTypeOfUString` | . | . | . | T | . | . | . | . | . | . | . |
| `isKindOfUString` | . | . | . | T | . | . | . | . | **T** | . | . |
| `isTypeOfUBoolean` | T | . | . | . | . | . | . | . | . | . | . |
| `isKindOfUBoolean` | T | . | . | . | . | **T** | . | . | . | . | . |
| `isTypeOfSBoolean` | . | . | . | . | T | . | . | . | . | . | . |
| `isKindOfSBoolean` | **T** | . | . | . | T | **T** | . | . | . | . | . |
| `isKindOfOclAny` | T | T | T | T | T | T | T | T | T | . | T |

Under `INCLUDE_VOID`, `OclVoid` answers `true` to every `isKindOf*` including the five new ones.

Two asymmetries to reproduce, **not** "fix" silently:

1. **`SBooleanType.isKindOfUBoolean()` is NOT overridden** (`SBooleanType` defines only
   `isTypeOfSBoolean` and `isKindOfSBoolean`), so `UBooleanValue.uEquals(SBooleanValue)` returns
   `FALSE` while `SBooleanValue.uEquals(UBooleanValue)` computes a real answer. See also the §2.5
   trap box — adding the override would reroute six operations.
2. **`isKindOfUString` exists on `Type` but there is no `Value.isUString()`.** `UStringValue` is the
   one value type with **no** `is…()` discriminator; discrimination is by `instanceof`.

## 3.4 `TypeFactory` entries

**Edit to an upstream file** (E6). Three groups, all in `T/uml/ocl/type/TypeFactory.java`:

**(a) Five interned `private static final` singletons** (fork `:48-56`) — created at class-init,
exactly like the upstream basic types. No lazy cache, no map lookup, no synchronisation; `==` and
`.equals()` agree for factory-produced instances.

| field | declaration | fork line |
|---|---|---|
| `uRealType` | `private static final URealType uRealType = new URealType();` | 48 |
| `uStringType` | `private static final UStringType uStringType = new UStringType();` | 50 |
| `uBooleanType` | `private static final UBooleanType uBooleanType = new UBooleanType();` | 51 |
| `uIntegerType` | `private static final UIntegerType uIntegerType = new UIntegerType();` | 55 |
| `sBooleanType` | `private static final SBooleanType sBooleanType = new SBooleanType();` | 56 |

**(b) Five `mk*()` accessors** (fork `:83-111`), each returning the interned field:

| method | signature | fork line |
|---|---|---|
| `mkUInteger` | `public static UIntegerType mkUInteger()` | 83 |
| `mkUReal` | **`public static Type mkUReal()`** | 93-95 |
| `mkUString` | `public static UStringType mkUString()` | 101 |
| `mkUBoolean` | `public static UBooleanType mkUBoolean()` | 107-109 |
| `mkSBoolean` | `public static SBooleanType mkSBoolean()` | 111 |

**Signature anomaly**: `mkUReal()` is the only one of the five — and the only `mk*` for a basic type
in the whole class — that widens its return type to `Type`. There are 25 call sites in `F/src/main`
and 53 more lines in `F/src/test`; the only places the *class* `URealType` is named outside its own
file are `F/uml/ocl/type/TypeFactory.java:48` and `FT/uml/ocl/type/TypeTest.java:392-393`, so **no call site depends on the wide
return type** and narrowing it is source-compatible. Recorded as a non-blocking decision (§0).

**(c) Five `buildInTypesMap` entries** (fork `:60-67`) — the **parser's** name→type table, whose only
consumer is `T/parser/ocl/ASTSimpleType.java:47` (same call in the fork at `F/parser/ocl/ASTSimpleType.java:45`) → `TypeFactory.mkSimpleType(name)`:

| key | value | fork line |
|---|---|---|
| `"UInteger"` | `uIntegerType` | 60 |
| `"UString"` | `uStringType` | 63 |
| `"SBoolean"` | `sBooleanType` | 64 |
| `"UBoolean"` | `uBooleanType` | 65 |
| `"UReal"` | `uRealType` | 67 |

Registering these is what makes `UReal`, `UBoolean`, … usable as type names in `.use` files
(oracle: `mkSimpleType("UReal") == mkUReal()`). **`mkSimpleType` itself is unchanged**, and **no
lexer keyword is needed for a primitive type name** — this is the fact that makes the grammar's
`uncertaintyType` rule pure damage repair (§5.4).

No other `TypeFactory` member is touched: `mkEnum`, `mkCollection`, `mkSet`, `mkSequence`, `mkBag`,
`mkOrderedSet`, `mkMessageType`, `mkOclAny`, `mkVoidType`, `mkTuple`, `mkSimpleType` are
byte-identical.

## 3.5 Where `UncertainType` sits under 7.5.0's hierarchy

```
Type                    (interface,  7.5.0 Type.java:31   — fork Type.java:33, SAME)
 └── TypeImpl           (abstract,   7.5.0 :32            — fork :34, SAME)
      └── BasicType     (abstract,   7.5.0 :28            — fork :31, SAME)
           ├── BooleanType, IntegerType, RealType, StringType, UnlimitedNaturalType   (upstream)
           └── UncertainType          (abstract)      ← NEW, pure instanceof tag
                ├── UIntegerType                      ← NEW
                ├── URealType                         ← NEW
                ├── UStringType                       ← NEW
                └── UncertainBooleanType   (abstract) ← NEW, zero instanceof sites
                     ├── UBooleanType                 ← NEW
                     └── SBooleanType                 ← NEW   (B2)

Type also has exactly ONE other implementation root in 7.5.0:
 └── MClassifier (interface extends Type, …)   →  MClassifierImpl
                                                   ├── MClassImpl
                                                   ├── MAssociationImpl / MAssociationClassImpl
                                                   ├── MSignalImpl
                                                   ├── EnumType
                                                   └── MDataTypeImpl        ← NEW IN 7.5.0
```

**Correction to the port plan's working assumption.** The fork's type package is **not** a
pre-`TypeImpl`, `Type`-as-a-class design. Its `Type.java` already declares
`public interface Type extends BufferedToString` (`:33`) with
`public abstract class TypeImpl implements Type` (`:34`); 7.5.0 has exactly the same two
declarations. **The interface/impl split is not a delta.** Consequence: `UncertainType extends
BasicType` compiles unchanged against 7.5.0, and the five leaves need no structural change.

**The Java hierarchy is NOT the conformance lattice.** `SBooleanType` is a Java *sibling* of
`UBooleanType` but a conformance *supertype* of it. Do not infer conformance from `extends`.

Facts about the two abstract tags that decide whether they survive **B2**:

* `UncertainType` carries **no behaviour at all** — a bare `protected UncertainType(String t)`. It is
  used as a discriminator at **11 sites in 3 files**: `F/uml/ocl/expr/operations/StandardOperationsCollection.java:104,169,401,474`,
  `F/uml/ocl/expr/operations/StandardOperationsNumber.java:351,946,1024,1101,1179`, `F/uml/ocl/expr/operations/StandardOperationsAny.java:49,199`. Two
  further files merely *import* it without using it (`F/uml/ocl/expr/ExpQuery.java:30`,
  `FT/uml/ocl/expr/UIntegerExpOpsTest.java:6`) — **do not port those imports**.
* `UncertainBooleanType` has **zero** `instanceof` sites and **no** `TypeFactory` entry and **no**
  grammar token — it is unwritable in OCL. It exists solely to give `UBooleanType` and
  `SBooleanType` a shared Java parent. **Under B2 = option 1 it collapses**, and `UBooleanType`
  extends `UncertainType` directly.
* `UncertainBooleanValue` earns slightly more of its keep — it is the declared return type at nine
  sites — but **every one of them receives a `UBooleanValue` in practice** unless the receiver is an
  `SBooleanValue`. `SBooleanValue.uEquals` is the **sole** implementation returning anything else.
  That single override is the entire reason the abstract return type exists; it too collapses under
  B2 = option 1, with `not()` moving onto `UBooleanValue` where its only implementation already lives.

**7.5.0-only members a ported type inherits for free** — no code needed in the seven new classes:
`qualifiedName()` (added `T/uml/ocl/type/Type.java:48`, defaulted `T/uml/ocl/type/TypeImpl.java:42-46` `return toString();`,
overridden `T/uml/mm/MClassifierImpl.java:389-392`), `isKindOfDataType(VoidHandling)` and `isTypeOfDataType()`
(added `T/uml/ocl/type/Type.java:136-138`, defaulted `T/uml/ocl/type/TypeImpl.java:287-295`). The correct answer for a new
uncertain type is `false` for both DataType predicates — **if someone "helpfully" returns `true`
because `UReal` feels like a data type, conformance and `oclIsKindOf` change across the whole model.**

**Return-type narrowing differs within the fork and must be replicated deliberately**: the upstream
basic types declare `public Set<Type> allSupertypes()`, the five new uncertainty types declare
`public Set<? extends Type> allSupertypes()`. **Keep 7.5.0's `Set<Type>` on the edited upstream
files** (narrowing them would break callers assigning to `Set<Type>`) and use `Set<? extends Type>`
on the new files, exactly as the fork does.

---

# 4. Upstream shape delta — fork base vs USE 7.5.0

**Headline: the drift is far smaller than the ten-year gap suggests.** The four OCL extension
points the port needs — `Value`, `Type`/`TypeImpl`, `ExpressionVisitor`, `OpGeneric` — have barely
moved. `Value.java` and `OpGeneric.java` are *textually identical* between the trees except for the
fork's own additions. The real risk is concentrated in (a) `MClassifier`, reshaped by the 2024
data-type work, (b) the build/module story, which is entirely new, and (c) the uDataTypes
dependency, which has no Maven coordinates anywhere.

**Correction to the brief: the fork's base is ~2018, not 2015.** 163 files carry
`$Id: … 5494 2015-02-05`, but cherry-picks run to `6361 2018-04-05`
(`F/config/Options.java`, `RELEASE_VERSION = "0.142.0"`). This matters: **the fork's base already
contains `Type.VoidHandling`** (upstream `750fa544`, 2015-02-05), so every `isKindOfX(VoidHandling)`
signature already matches 7.5.0. That eliminates what would have been the single largest mechanical
adaptation in the type system.

**Language level.** Fork `source/target 1.7` (`build.xml:16-17`); target 21 (`pom.xml:18-19`,
`use-core/pom.xml:16-17`). JDK 21's `javac` cannot emit `-source 7` at all, so "copy verbatim and
hope" is not a strategy for anything that fails to compile.

## 4.1 `Value` — zero upstream drift, one systematic trap

`Value.java` is 194 L in 7.5.0, 230 L in the fork. `diff` (whitespace-normalised) produces **five
hunks**: two SVN keyword lines and the fork's four added predicates. **There is no upstream-side
change to `Value` at all.**

Contract an added value class must satisfy (7.5.0 line numbers):

| # | Obligation | Where | Kind |
|---|---|---|---|
| C1 | `protected Value(Type t)` | `:40` | ctor to chain |
| C2 | `int compareTo(Value)` | via `implements Comparable<Value>` `:36` | **not re-declared abstract** — `javac` complains only at the first *concrete* subclass |
| C3 | `abstract StringBuilder toString(StringBuilder)` | `:160` (from `BufferedToString`) | abstract |
| C4 | `abstract int hashCode()` | `:175` | abstract |
| C5 | `abstract boolean equals(Object)` — **OCL semantics**, must handle `UndefinedValue` | `:183` | abstract |
| C6–C23 | `type()` `:44`, `getRuntimeType()` `:48`, the `isX()` family `:56-150`, `isDefined()` `:84` delegating to `isUndefined()` `:92`, `final String toString()` `:153`, `toStringWithType` `:162/:168`, `setTypeToRuntimeType()` `:190` | | inherited, unchanged |
| C24–C27 | `isUInteger()` `isUReal()` `isUBoolean()` `isSBoolean()` | fork `:67, :84, :100, :108` | **the four to add (E7)** |

`compareTo` carries a documented total-order obligation in the class javadoc
(`T/uml/ocl/value/Value.java:27-31`): *"all values must be able of being compared with an `UndefinedValue`"*. The
established convention (`RealValue.compareTo`, `T/uml/ocl/value/RealValue.java:72-85`) is `return
+1` for an `UndefinedValue` argument and `toString().compareTo(o.toString())` as the fallback.
`RealValue.hashCode` carries an explicit cross-type constraint (`:66-68`): *"this must be the same
hash code as for `IntegerValue`"*. **Any new numeric value class expected to live in the same
`Set`/`Bag` inherits that constraint** — which `UIntegerValue` breaks (§7.2 F-10).

Per-class contract status of the seven new value classes:

| Class | C1 | C2 `compareTo` | C3 `toString(sb)` | C4 `hashCode` | C5 `equals` | C24–C27 |
|---|---|---|---|---|---|---|
| `UncertainValue` | ✔ | left abstract | left abstract | left abstract | left abstract | — |
| `UncertainBooleanValue` | ✔ | left abstract | left abstract | left abstract | left abstract | — |
| `UBooleanValue` | ✔ | ✔ `:251` | ✔ `:192` | ✔ `:208` | ✔ `:224` | `isUBoolean` `:179` |
| `UIntegerValue` | ✔ | ✔ `:94` — **ignores `UndefinedValue`, returns `0`** | ✔ `:46` | ✔ `:57` — **semantically wrong** | ✔ `:67` | `isUInteger` `:30` |
| `URealValue` | ✔ | ✔ `:95` — **ignores `UndefinedValue`**; dead branch `:104-107` | ✔ `:42` | ✔ `:56` | ✔ `:67` | `isUReal` `:37` |
| `UStringValue` | ✔ | ✔ `:95` — **semantically wrong** | ✔ `:67` — hard-codes `"UString('"`, does not use `type()` | ✔ `:74` | ✔ `:79` — **violates the `equals` contract** | **none — no `isUString()` exists** |
| `SBooleanValue` | ✔ | ✔ `:151` — **`return 0;` is the entire body** | ✔ `:124` | ✔ `:133` | ✔ `:138` | `isSBoolean` `:95`; **`isUBoolean` NOT overridden** (which is what makes `UBooleanValue.valueOf(SBooleanValue)` return `null`) |

**The one real trap in this subsystem is elsewhere in the package (B6).**

```bash
diff <(sed 's/[[:space:]]*$//' F/uml/ocl/value/UndefinedValue.java) \
     <(sed 's/[[:space:]]*$//' T/uml/ocl/value/UndefinedValue.java)
# 46c43
# <         return sb.append("Undefined");
# ---
# >         return sb.append("null");
```

Upstream renamed the printed form in `72ab8fd7` (2019-06-27, *"changed Undefined to null"*). Any
differential harness comparing *printed* values reports a false positive on **every** undefined
result, and any fixture lifted verbatim from the fork is wrong. **79 corpus entries** are affected.

## 4.2 `Type` / `MClassifier` — where the real drift is

**Exactly three members were added to the `Type` interface since the fork's base:**

| 7.5.0 member | Line | Introduced |
|---|---|---|
| `String qualifiedName();` | `:48` | `4dd26e4d`, 2025-06-10, model-qualified imports |
| `boolean isKindOfDataType(VoidHandling h);` | `:136` | `fb866f31`, 2024-04-22, "USE now supports data types" |
| `boolean isTypeOfDataType();` | `:138` | same |

All three have `TypeImpl` defaults, so the new types inherit correct behaviour and implement nothing.

**`MClassifier` is the drift.** Measured (`$Id`/`$ProjectVersion` and trailing whitespace excluded):
`diff … MClassifierImpl.java | grep -cE '^[<>]'` → **226**.

| Fork | 7.5.0 | Consequence |
|---|---|---|
| `MClassifier.isSubClassOf(...)` (`:114,:123`) | **renamed** `isSubClassifierOf(...)` (`:116,:125`) | rename every fork call site |
| `MClassifierImpl.attribute(...)` returned `null` unconditionally; attributes lived on `MClass` | attributes and operations **pulled up** to `MClassifier` (`:136-168`); `MClassifierImpl` owns `fAttributes`, `fOperations`, `fVTableOperations` (`:52-65`, methods `:491-573`) | fork code that downcast to `MClass` to reach attributes should be rewritten against `MClassifier` |
| — | `isQualifiedAccess()` / `setQualifiedAccess(boolean)` (`:59,:61`) | new |
| — | `hasStateMachineWhichHandles(MOperationCall)` | new |
| — | `qualifiedName()` = `model != null ? model.name() + "#" + name() : null` (`:389-392`) — **note the null guard, which `15-upstream-delta.md` drops from its quote** | new |
| — | **new sibling `MDataType` / `MDataTypeImpl`** | a **third** classifier kind exists; anything assuming `MClassifier ⇒ MClass` is now wrong and will `ClassCastException` at model-load time the moment a model declares a `dataType`. The fork could not have this bug; the port can |
| `MMVisitor` had no data-type case | `void visitDataType(MDataType e);` (`T/uml/mm/MMVisitor.java:39`) | implementors must add it |
| `ModelFactory.createOperation(name, varDeclList, resultType)` | `…(name, varDeclList, resultType, boolean isConstructor)` (`:76-78`) | **signature change**, breaks callers |
| `createClassInvariant(…, MClass cls, …)` | `…(…, MClassifier cf, …)` (`:56-60`) | widened |
| — | `createDataType(String, boolean)` (`:43-46`); `MImportedModel`, `TestModelUtil` | new files |

**`File-level copying from the fork is forbidden for every file marked "edit".** A naive copy of
`Type`/`TypeImpl`/`MClassifierImpl` silently deletes `isKindOfDataType`, `isTypeOfDataType`,
`qualifiedName`, `isQualifiedAccess`/`setQualifiedAccess`, `hasStateMachineWhichHandles`, the
`isSubClassOf` → `isSubClassifierOf` rename, and (in `MClassifierImpl`) the whole attribute/operation
table 7.5.0 pulled up. **Interleave, do not overwrite.**

## 4.3 `Expression` / `ExpressionVisitor`

`Expression` is **unchanged** — the fork↔7.5.0 diff is four hunks, all `$Id` and javadoc. Abstract
surface: `eval(EvalContext)` `:79`, `childExpressionRequiresPreState()` `:133`,
`toString(StringBuilder)` `:147`, `processWithVisitor(ExpressionVisitor)` `:178`, plus
`public final String toString()` `:138`.

`ExpressionVisitor` is a **plain, non-sealed interface** in both trees:

```bash
grep -rn '\bsealed\b\|\bpermits\b' --include=*.java use-core/src/main use-gui/src/main   # no output
```

So **dispatch is not exhaustive in the compiler's eyes**. Adding a `visitX` to the interface breaks
every implementor at compile time (desired), but adding a new `Expression` subclass whose
`processWithVisitor` calls the *wrong* case — a copy-paste from a sibling — compiles clean and
silently mis-prints or mis-covers. **There is no compiler safety net here: assert one visitor
round-trip per new expression class.**

Method counts: 7.5.0 **49**, fork **57**. The set difference is exactly the 8 new methods plus the
`visitObjOp(ExpObjOp)` ↔ `visitInstanceOp(ExpInstanceOp)` rename (upstream `46c277e7`, 2024-11-24;
`ExpObjOp` still exists but is now `public final class ExpObjOp extends ExpInstanceOp`).

**Implementor census — the exact compile-break blast radius:**

| # | File | Relationship | Action |
|---|---|---|---|
| 1 | `T/uml/ocl/expr/ExpressionPrintVisitor.java:35` | `implements ExpressionVisitor`, concrete | **EDIT — hard break** (E12) |
| 2 | `T/analysis/coverage/AbstractCoverageVisitor.java:33` | `implements ExpressionVisitor`, abstract but implements all 49 concretely | **EDIT — required in practice** (E13) |
| 3 | `T/analysis/coverage/CoverageCalculationVisitor.java:38` | `extends AbstractCoverageVisitor`, concrete | none if #2 done; **breaks if #2 left abstract** |
| 4 | `T/analysis/coverage/BasicExpressionCoverageCalulator.java:40` | `extends AbstractCoverageVisitor`, concrete | none if #2 done |
| 5 | `T/uml/ocl/expr/GenerateHTMLExpressionVisitor.java:30` | `extends ExpressionPrintVisitor` | none — overrides `quoteContent` (`:40`), **`toString` (`:45`, omitted in the original list — audit-02 §1.2)**, `formatOperation` (`:50`), `formatKeyword` (`:55`). The row's conclusion ("none") is unaffected |
| 6 | `T/uml/ocl/expr/EvalNode.java:618` inner `RelevantOperationHighlightVisitor` | 2-deep | none |
| 7 | `T/uml/ocl/expr/EvalNode.java:351` inner `SubstituteVariablesExpressionVisitor` | 3-deep | none |

Negative results, all checked: **no anonymous implementors** (`grep -rn "new ExpressionVisitor"` → 0);
**no test-side implementors** (`grep -rn "ExpressionVisitor" use-core/src/test use-gui/src/test use-gui/src/it` → 0);
**`use-gui` implements it nowhere**; `use-assembly`, `manual`, `documentation` contain no Java
implementing it.

**Risk if got wrong:** `GenerateHTMLExpressionVisitor` and `EvalNode`'s two inner subclasses inherit
from `ExpressionPrintVisitor`. If the new methods are added there with a body that prints nothing,
**the evaluation browser silently renders empty nodes for uncertain sub-expressions** — a GUI-only
defect invisible to a core-only test suite.

**Out of scope:** the fork's third implementor `AbstractMetricVisitor` belongs to
`org/tzi/use/analysis/metrics/`, a 15-file package that **does not exist in 7.5.0** (`ls
T/analysis/` → `coverage` only). It did exist upstream once and was removed; it is a separate fork
feature (GSMetric), unrelated to uncertainty.

## 4.4 `OpGeneric` and the operation registry — zero drift

```bash
diff <(sed 's/[[:space:]]*$//' F/uml/ocl/expr/operations/OpGeneric.java) \
     <(sed 's/[[:space:]]*$//' T/uml/ocl/expr/operations/OpGeneric.java)
# one hunk: the fork's own 7 lines (comment + 5 registrations + blank)
```

**That is the entire diff. Not one signature changed.** Contract, at 7.5.0 line numbers:
`OPERATION = 0` `:34`, `SPECIAL = 3` `:36`, `abstract String name()` `:38`,
`boolean isBooleanOperation()` `:40-42` (concrete, `false`), `abstract int kind()` `:44`,
`abstract boolean isInfixOrPrefix()` `:46`, `abstract Type matches(Type params[])` `:48`,
`String checkWarningUnrelatedTypes(Expression[])` `:50` (concrete, `null`),
`abstract Value eval(EvalContext, Value[], Type)` `:52`, `String stringRep(Expression[], String)`
`:54-78`, `registerOperations(Multimap)` `:80`, two `registerOperation` overloads `:105`, `:115`.

**There is no `PREDICATE` kind** in either tree — it appears only in the comment at `:32`. Do not
"restore" it. There is no `getResultType()` either; the brief's `getResultType` corresponds to
`matches(Type[])`, which returns the result type as its success signal (`null` = "this overload does
not apply").

**Three-way merge required** on exactly three files, where the fork and upstream both edited the
same functions:

| Class | fork↔7.5.0 Δ | What changed |
|---|---|---|
| `StandardOperationsNumber` | 763 | fork rewrote `add`/`sub`/`mult`/`div` and the shared `matches`; **7.5.0 independently added `Op_number_pow` and `Op_number_sqrt`** (B8). Taking the fork's file wholesale **deletes `pow` and `sqrt` from OCL** — a silent capability regression the uncertainty suite would never notice, caught only by the dormant `ExpStdOpTest` (i.e. only under **B3**) |
| `StandardOperationsAny` | 158 | fork adds `Op_identical` and **rewrites `Op_equal`/`Op_notequal`** |
| `StandardOperationsCollection` | 307 | fork registers `Op_collection_uCount`/`uCountC` and **rewrites `Op_includes`/`excludes`/`includesAll`/`excludesAll`**; the remaining ~282 lines are import-wildcard collapse and upstream edits |

`StandardOperationsBoolean`, `StandardOperationsString` and `BooleanOperation` have **0** diff lines.

**Risk if got wrong:** `matches` returning a non-`null` type for arguments `eval` cannot actually
handle is the classic `OpGeneric` bug — it type-checks and then `ClassCastException`s at evaluation.
Several fork operations already have this shape (§2.1 #1/#14, §2.3 #1/#5, §2.4 #8/#13, §2.5 #8/#9).

## 4.5 Build and module story — **the `module-info.java` answer**

**Yes, `use-core` has a module descriptor**, and so does `use-gui`:

```bash
find . -name module-info.java -not -path './.git/*'
# ./use-core/src/main/java/module-info.java
# ./use-gui/src/main/java/module-info.java
```

`use-core/src/main/java/module-info.java` is 45 lines, `module use.core`, **11 `requires`**,
**32 `exports`**, no `opens`, no qualified `exports`, no `requires static`. **Every port-relevant
export is already present:**

```java
exports org.tzi.use.uml.mm;                    // line 16
exports org.tzi.use.uml.ocl.type;              // line 25
exports org.tzi.use.uml.ocl.expr;              // line 29
exports org.tzi.use.uml.ocl.value;             // line 30
exports org.tzi.use.uml.ocl.expr.operations;   // line 31
exports org.tzi.use.parser.ocl;                // line 33
exports org.tzi.use.parser.use;                // line 15
```

### The answer, in three rules — derived from hard evidence, not memory

Evidence: the surefire report of the last real build records the JVM's actual paths.
`jdk.module.path` has **11 entries** (`use-core/target/classes`, guava + its transitive
annotations, jdt.annotation, antlr-runtime, combinatoricslib, jruby-core, vtd-xml);
`java.class.path` has **21** (`use-core/target/test-classes`, **jline**, guava-testlib, junit4,
junit-jupiter 5.7.0 + platform, archunit-junit5, opentest4j, apiguardian). Surefire is **3.5.4**,
inherited from the Maven 3.9.16 super-POM — no `<surefire>` config exists anywhere in the reactor.

1. **Main code runs as the named module `use.core` on the module path; test code runs on the
   classpath, in the unnamed module.** There is no `module-info.java` under `use-core/src/test/java`
   or `src/it/java`.
2. **A test-scoped dependency does NOT need a `requires`.** junit-jupiter, archunit and guava-testlib
   are compile-visible to tests and appear nowhere in the descriptor. This is exactly what the
   differential harness needs.
3. **A compile-scoped dependency DOES need a `requires`, and Maven derives the module path *from*
   `module-info.java`, not from the POM.** Proof: `jline` is a normal compile-scope dependency
   (`use-core/pom.xml:36-40`) with **no** `requires jline`, and it lands on `java.class.path`, not
   `jdk.module.path`. A named module cannot read the unnamed module, so `jline` is **unusable from
   `use.core` source** — it survives only because nothing imports it. **Add a compile dependency
   without a matching `requires` and it will be invisible to your code; `javac` will report "package
   … does not exist" while the jar sits happily in the dependency tree.**

### Concretely, for this port

| Change | `module-info.java` action |
|---|---|
| Classes added to `org.tzi.use.uml.ocl.{value,type,expr,expr.operations}` or `org.tzi.use.parser.ocl` | **none** — already exported |
| A **new** package used only by `use-core` main code | none |
| A **new** package used by `use-gui` | `exports` in `use-core` |
| A **new** package used by **test** code | **`exports` IS needed** — test classes are in the unnamed module and cannot read a non-exported package of a named module |
| Vendored `uDataTypes` under **A1/A2** (B1) | internal to `use.core`; `exports` required **only if** test code constructs `uDataTypes` objects directly. Decide explicitly rather than discover it via `IllegalAccessError` |
| `use-gui/src/main/java/module-info.java` | **no change.** The fork's uncertainty extension touches **no GUI file at all** (`grep -rlE 'UReal\|UInteger\|UBoolean\|SBoolean\|UString\|uDataTypes\|Uncertain' F/… \| grep '^org/tzi/use/gui'` → no output) |

**The single most likely JPMS trap for this port:** putting new product classes in a fresh,
unexported package and then writing tests against them produces `IllegalAccessError` **at runtime,
in surefire only** — the code compiles, the IDE is happy, and only `mvn test` fails. A related trap:
a test class sharing a package name with a module package (as the dormant `ValueTest` does) is in the
unnamed module while the production class is in `use.core`, so **package-private access across that
boundary is an `IllegalAccessError`**. Any revived or new test must touch only `public` members of
exported packages.

**Grammar location.** Fork: `.gpart` files sit next to the Java sources under `F/parser/{base,ocl,…}/`
and 12 ANTLR lexers/parsers are **checked in**. 7.5.0: grammars moved to
`use-core/src/main/resources/grammars/` (`99ff26c2`, 2021-07-29) and parsers are **generated** at
`generate-sources` by `merge-maven-plugin` + `antlr3-maven-plugin` + `copy-rename-maven-plugin`
(`use-core/pom.xml`, plugins block `:77-336`). **Do not port the fork's checked-in generated parsers.**

## 4.6 uDataTypes — **BLOCKING DECISION B1**

### It is on no Maven repository, under any coordinates

```bash
for q in "uDataTypes" "udatatypes" "a:udatatypes" "g:atenearesearchgroup" \
         "fc:uDataTypes.UReal" "fc:uDataTypes.SBoolean" "g:org.tzi.use"; do
  curl -s -G --data-urlencode "q=$q" --data "rows=10&wt=json" https://search.maven.org/solrsearch/select
done      # every one: numFound 0
for p in es/uma/lcc/atenea uDataTypes atenearesearchgroup; do
  curl -s -o /dev/null -w '%{http_code}\n' "https://repo1.maven.org/maven2/$p/"
done      # 404 404 404
find $UDT -name pom.xml -o -name 'build.gradle*'    # nothing -> JitPack is out
```

`fc:` is Central's **fully-qualified-class** index, so zero hits is a strong negative. For context
`org.tzi.use` is not on Central either — the project has no precedent of consuming its own or its
collaborators' artifacts from a public repo, and the reactor has **no `<repositories>`, no `system`
scope, no `systemPath`** anywhere.

### What the oracle jar actually is

39 entries, 77 674 bytes, classes under `uDataTypes/`, timestamps **2021-02-24**. It also ships
`.classpath`, `.project`, `.settings/`, `.gitignore`, `uDataTypes.iml` — **it is an IDE export, not
a release artifact**. **Zero `META-INF` entries** ⇒ no manifest, therefore **no
`Automatic-Module-Name`**; as an automatic module its name would be derived from whatever file name
it were installed under. Class-file major version **52** (Java 8), readable by JDK 21.

**A byte-identical copy is already committed in the target repository** at
`use-core/src/test/resources/historical/atenearesearchgroup.uncertainty.jar`
(md5 `a3055f54205babaa27484fa94efdda1c`; sha256 `53b2a43feb0a…`), alongside `use.jar`
(md5 `8645269c1eacbf8cb52bf7f694c07b21`; sha256 `80ac8ae433b8…`) — see `stage-01.md` §2. So the
**oracle-side need is already solved, on the test side, with no Maven coordinates involved**. What
remains unsolved is the **product** side: `use-core` main code must compile against `uDataTypes.*`.

> `20-ops-UReal.md` §2 states this jar is **not** present in the target repo. **That is refuted**
> (refuter R.3, corroborated by `stage-01.md` §2). The port needs no "obtain the jar" step.

### Is the 2023 source a safe stand-in for the 2021 jar? Yes, for this port's call paths

The `$UDT` tree holds **23** `.java` files, of which **18** are non-test
(`15-upstream-delta.md`'s "24" and "15" are both wrong — refuter F4 adopted; the five excluded are
`ExamplesSBoolean`, `SBooleanTest`, `SBooleanTest3`, `UEnumTest`, `UncertaintyTest`). It compiles
clean under JDK 21. `javap` public-API diff, jar vs source-compiled:

| Class | Delta |
|---|---|
| `UReal`, `UInteger`, `UBoolean`, `UString`, `Distribution`, `UUnlimitedNatural` | **identical** |
| `SBoolean` | **11 lines**, all **additions on the source side** (`weightedUnion(SBoolean)`, `union(SBoolean)`, 9 collection-fusion statics). §15's "14 lines" is wrong — refuter F5 adopted; its parenthetical breakdown (2 + 9 = 11) was right |

**The source is a strict API superset**: anything that compiled against the jar compiles against the
source. A 16-expression differential probe over all five types — including `SBoolean.toString`,
`hashCode`, `projection`, `and`, `cumulativeFusion`, `discount`, `createVacuousOpinion`,
`averageBeliefFusion`, `weightedBeliefFusion`, `consensusAndCompromiseFusion` — was **all identical**.

**The only divergence reproducible** is in the covariance-taking overloads:

```
                                              JAR (2021)            SRC (2023)
UReal(1,0).divideBy(UReal(2,0.3), 0.0)      → UReal(0.500, 0.000)   UReal(0.500, 0.075)
UInteger(6,0).divideBy(UInteger(3,1), 0.0)  → UInteger(2, 0.000)    UInteger(2, 0.111)
UInteger(6,0).divideByR(UInteger(3,1), 0.0) → UReal(2.000, 0.000)   UReal(2.000, 0.111)
```

Root cause: in the `this.getU()==0.0` branch the 2021 jar reads `this.getU()/(this.getX()²)` —
always 0 — where 2023 reads `r.getU()/(r.getX()²)`. **A genuine bug fix.**

**The fork never reaches those overloads.** It calls only the single-argument forms
(`UIntegerValue.java:148,:158`, `URealValue.java:180`), which are byte-identical between jar and
source and produce identical results in the probe.

> Reproducibility note: the grep `15-upstream-delta.md` quotes for this actually emits **nine**
> lines, not the three shown — it also matches the USE-level method declarations and three call
> sites in `StandardOperationsNumber`/`StandardOperationsUInteger`. The **conclusion stands**; the
> transcript was edited without saying so (refuter F6 adopted).

**Licence:** `$UDT/Libraries/Java/README.md:261-269` — **MIT**, "Copyright (c) 2023 Atenea Research
group", permission text inline. There is no `LICENSE` file. MIT is GPL-2-compatible, so vendoring
into GPL-2 USE is legally sound provided the notice travels with the copied files.

### The four options

| Option | What it is | Verdict |
|---|---|---|
| **A1** vendor 2023 source, keep package `uDataTypes` | zero edits to the copied files and to the 7 fork files that import them | §15 rejects it on a **refuted** premise (B1a). It is *not* fatally flawed against the harness that exists — but it makes oracle validity depend on a test-scoped classloader policy |
| **A2** vendor relocated to `org.tzi.use.uncertainty.udatatypes` | mechanical rewrite of the `package` line in **18** files and the `import` line in **7** | **RECOMMENDED.** The collision disappears by construction; the oracle loader can be naive and still correct. Cost: vendored sources are no longer textually identical upstream, so re-syncs need the same rewrite |
| **B** `mvn install:install-file` | invented coordinates + an out-of-band step | **Breaks `git clone && mvn test`.** Zero precedent in the reactor. Needs a `requires` on an automatic module whose name derives from the installed file name; automatic modules also read the unnamed module and cannot be `jlink`ed. Ships the IDE cruft into the product. **Freezes the 2021 `divideBy` bug for all time** |
| **C** shade/relocate the bytecode | solves the collision like A2, at bytecode level | Still needs the jar in a repository first (B's unsolved problem) or a deprecated `system` scope. Adds a shaded-artifact lifecycle to a reactor with none. Same frozen-bug consequence. Debugging relocated bytecode without sources is materially worse |
| **D** reimplement | — | **Rejected on the spot.** `UReal` is 19 582 B and `SBoolean` 59 412 B of subjective-logic arithmetic implementing two published papers; reimplementation makes the port's numeric behaviour a fresh research artifact rather than a port |

| Constraint | A1 | **A2** | B | C |
|---|---|---|---|---|
| Nothing under `.git/reference-repositories` on any source path or classpath | ✔ (copied in) | **✔** | ✘ if it points at `F/lib`; ✔ only via the committed `src/test/resources/historical/` copy | ✘ same |
| `git clone && mvn test` works with no manual step | ✔ | **✔** | ✘ | ✘ |
| Oracle isolation cannot be defeated by classloader delegation | (already holds — B1a) | **✔ by construction** | ✔ | ✔ |
| No `requires` on an unnamed automatic module | ✔ | **✔** | ✘ | ✘ |
| Licence clean | ✔ MIT | **✔ MIT** | ⚠ jar has no licence metadata | ⚠ same |

**Two follow-on obligations if A2 is taken:**

1. **Record the jar↔source delta as a known, accepted difference.** It is empty on the fork's call
   paths, but the `divideBy(…, covariance)` fix means "port ≡ oracle" is a claim about *reachable*
   behaviour, not about the libraries. If a future stage adds OCL surface for correlated division,
   port and oracle will legitimately disagree.
2. **Keep the oracle side on the committed jar** at
   `use-core/src/test/resources/historical/atenearesearchgroup.uncertainty.jar`, loaded through
   `IsolatedJarClassLoader`. Test-scope needs no POM dependency and no `requires`, because the jar
   is a **resource**, not a dependency — the harness opens it by path/URL.

## 4.7 Sizing the delta

**33 files to ADD** (7 value + 7 type + 8 expr + 5 operations + 6 parser), of which only **7** import
`uDataTypes` at all, and only **five types** are imported (`UBoolean`, `UInteger`, `UReal`, `UString`,
`SBoolean`) — `UUnlimitedNatural`, `UEnum` and `Distribution` are never imported but are still needed
on the classpath as transitive return types.

Derivation: `comm -23` of the fork's `src/main` file list against `use-core` + `use-gui` gives **62**
fork-only paths; removing the 15 `analysis/metrics` files, `main/Main.java`,
`util/input/ShellReadline.java` and the 12 checked-in ANTLR parsers leaves exactly **33**.

**26 `.java` files to EDIT**, by drift (§1.6 gives the per-file behaviour):

| File | Δ | 7.5.0 LoC | Character of the drift |
|---|---|---|---|
| `StandardOperationsNumber.java` | 763 | 911 | **both sides rewrote — hardest merge in the port** |
| `StandardOperationsCollection.java` | 307 | 787 | mostly fork import-wildcarding; ~25 lines uncertainty |
| `MClassifierImpl.java` | 226 | 588 | **upstream data-type reshape**; fork side is 10 trivial stubs |
| `ExpQuery.java` | 218 | 513 | 22 lines uncertainty, 196 upstream |
| `ASTQueryExpression.java` | 167 | 212 | fork adds the confidence argument |
| `StandardOperationsAny.java` | 158 | 201 | fork adds `Op_identical`, rewrites `Op_equal` |
| `CollectionValue.java` | 114 | 198 | 26 lines uncertainty |
| `TypeImpl.java` | 70 | 347 | 10 fork stubs + 2 upstream |
| `AbstractCoverageVisitor.java` | 68 | 352 | 8 new visitor cases + `visitQuery` |
| `ExpressionPrintVisitor.java` | 49 | 585 | 8 new visitor cases + `visitQuery` |
| `Type.java` | 37 | 155 | 10 fork members, 3 upstream members |
| `Value.java` | 34 | 194 | 4 fork predicates only |
| `VoidType.java` | 30 | 131 | fork stubs |
| `TypeFactory.java` | 25 | 140 | fork singletons + map entries |
| `BasicExpressionCoverageCalulator.java` | 17 | 98 | |
| `BooleanType.java` / `IntegerType.java` | 15 each | 64 / 74 | `conformsTo` + `allSupertypes` |
| `ExpressionVisitor.java` | 11 | 77 | 8 fork + 1 upstream rename |
| `StringType.java` | 11 | 63 | |
| `RealType.java` | 9 | 67 | |
| `OpGeneric.java` | 7 | 118 | **fork only** |
| `ExpExists.java` / `ExpForAll.java` | 5 each | 83 each | |
| `MathUtil.java`, `RealValue.java` | (keyword-invisible) | | additive only |

The exact integers in this table are **UNVERIFIABLE at the row level** — the method was re-derived
and confirmed to run, and the qualitative claims were confirmed for `OpGeneric`, `Value`, `Type`,
`TypeImpl` and `ExpressionVisitor`, but not all 23 rows were re-counted. **Nothing in the port
depends on the exact integers.**

---

# 5. Grammar surface

## 5.0 Path correction and the CRLF trap — read before diffing anything

**In 7.5.0 the grammar fragments are not under `parser/`.** They moved to a resources tree:

| | path |
|---|---|
| fork (Ant) | `F/parser/base/OCLBase.gpart` |
| 7.5.0 (Maven) | `G/base/OCLBase.gpart` |

`T/parser/base/` contains only `BaseParser.java` and `ParserHelper.java` — no `.gpart`.

**All seven 7.5.0 `.gpart` resources are CRLF; the fork's are LF.** A naive `diff` reports every
line as changed (`diff … OCLLexerRules.gpart` → `1,127c1,127`). Every measurement below was taken
after `sed 's/\r$//'`. §15's "`OCLLexerRules.gpart` is unchanged (empty diff)" is **not reproducible
as written** — the conclusion holds, the evidence does not (refuter F3 adopted). **Normalise line
endings before any grammar merge**, and match the target repo's convention when writing.

## 5.1 The minimal edit to `OCLBase.gpart` — measured

```bash
diff -u up_OCLBase.txt cand_OCLBase.txt > minimal.patch
awk '/^\+[^+]/{a++} /^-[^-]/{d++} END{print "added:",a+0," removed:",d+0}' minimal.patch
grep -c "^@@" minimal.patch
```

| metric | value |
|---|---|
| hunks | **8** |
| lines added | **35** |
| lines removed | **8** |
| net | **+30** (677 → 707 lines; `13-grammar.md:81`'s "678 → 708" is off by one — refuter R10c adopted) |
| of the 35 added, comment-only | 4 |
| **executable grammar lines added** | **31** |
| new parser rules | **2** (`identicalExpression`, `uncertaintyType`) |
| existing rules altered | **4** (`expression`, `queryExpression`, `literal`, `type`) |

**Three 7.5.0 features the fork LACKS and the port must KEEP** (the fork branched from an older USE;
these are **not** uncertainty changes and must not be carried in):

| 7.5.0 feature absent from the fork | 7.5.0 location |
|---|---|
| `modelQualifier` on operation calls (`M#op`) | `G/base/OCLBase.gpart:367-369` |
| `modelQualifier` on enum literals (`M#E::lit`) | `:484-485` |
| `modelQualifiedType ::= IDENT HASH IDENT` and its `type` alternative | `:600, :610, :670-677` |
| `'oclIsInState'` as an alias for `'oclInState'` | `:400-405` |

Upstream-side additions in `USEBase.gpart` the port must not clobber: `importStatement` /
`importClause` / `elementIdent` / `artifact` (`:9-27`) and `dataTypeDefinition` (`:86-113`).

`OCLLexerRules.gpart`: **0 lines changed.** Both files are 127 lines and byte-identical after
normalisation. Every uncertainty keyword is therefore an **implicit token** created by ANTLR from an
inline literal in the *parser* grammar — six of them:

| token | introduced at |
|---|---|
| `'UReal'` | `F/parser/base/OCLBase.gpart:496`, `:633` |
| `'UInteger'` | `:498`, `:633` |
| `'UBoolean'` | `:497`, `:633` |
| `'UString'` | `:495`, `:633` |
| `'SBoolean'` | `:499`, `:633` |
| `'equals'` | `:132` |

Confirmed against the fork's checked-in generated vocabularies (`OCLParser.java:40`,
`USEParser.java:46`, `TestSuiteParser.java:42`), each of which lists all six. **Because ANTLR gives
inline literals precedence over `IDENT`, these six words become reserved across *every* USE input
language.** That is the single largest blast-radius item in this section.

## 5.2 Literal forms, with corpus examples

Harness convention for all `.in` files: an expression, then a line beginning `->` with the expected
`toStringWithType()` output; `#` starts a comment; a line ending in `\\` continues.
Reader: `FT/parser/uncertainty/USECompilerUncertaintyTest.java:107-140`; assertion `:94`.

```bash
cd FT/parser/uncertainty/
for f in *.in; do echo "$f: $(grep -c '^->' $f)"; done
# UBooleanExpression.in 118 | UCollectionOperations.in 44 | UIntegerExpression.in 692 | URealExpression.in 573
cat *.in | grep -cE '^[[:space:]]*->'      # 1427
```

| literal | arity | operand rules | AST class | `ExpConst*` built | corpus cases |
|---|---|---|---|---|---|
| `UReal` | 2 | additive, additive | `ASTURealLiteral` | `ExpConstUReal` | **573** |
| `UInteger` | 2 | additive, additive | `ASTUIntegerLiteral` | `ExpConstUInteger` | **692** |
| `UBoolean` | 2 | **conditionalImplies**, additive | `ASTUBooleanLiteral` | `ExpConstUBoolean` | **118** |
| `UString` | 2 | additive, additive | `ASTUStringLiteral` | `ExpConstUString` | **0** |
| `SBoolean` | **4** | additive ×4 | `ASTSBooleanLiteral` | `ExpConstSBoolean` | **0** |

**`SBoolean` is the fifth literal and the port plan must account for it** — the brief names four.

### `UReal` — `'UReal' LPAREN additiveExpression COMMA additiveExpression RPAREN`

Arity 2 (value, uncertainty). Semantic check in `ASTURealLiteral.java:27-31`: both must be Integer
or Real, with bespoke messages.

| example | citation |
|---|---|
| `UReal(2, 0)` | `URealExpression.in:7` |
| `UReal(2, -2)` | `:13` → `-> UReal(2.0, 2.0) : UReal` — negative uncertainty accepted and normalised |
| `UReal(2+2, 3)` | `:25` — **proves the operand is an expression, not a numeric token** |
| `UReal(55.23, 9.34)` | `:28` |
| `UReal(55.23, -66.34)` | `:34` |
| `UReal(0.34, 55.23)` | `:37` |

### `UInteger` — `'UInteger' LPAREN additiveExpression COMMA additiveExpression RPAREN`

| example | citation |
|---|---|
| `UInteger(-5, 0.0)` | `UIntegerExpression.in:7` |
| `UInteger(-5, -0.5)` | `:13` |
| `UInteger(-5, 2)` | `:16` |
| `UInteger(3, 39)` | `:22` |
| `UInteger(Undefined, Undefined)` | `:28` — **proves `Undefined` is a legal operand** (it reaches `additiveExpression` via `primaryExpression → literal → undefinedLiteral`) |
| `UInteger(3 + 4*2-3, UReal(4, 3.3).value() + 1)` | `:37` — **proves both operands are full `additiveExpression`s including postfix calls and nested uncertainty literals** |

### `UBoolean` — `'UBoolean' LPAREN conditionalImpliesExpression COMMA additiveExpression RPAREN`

**The only literal whose first operand is not `additiveExpression`** — it must be
`conditionalImpliesExpression`, because the value is a boolean and `true or false` would not parse
as an `additiveExpression`.

| example | citation |
|---|---|
| `UBoolean(true or false, UReal(2, 3))` | `UBooleanExpression.in:13` — first operand admits `or`; also an **error case**, `-> Probability must be a Integer or Real` |
| `UBoolean(true and false, 3 / 0)` | `:16` |
| `UBoolean(true or false, 1 - 0.4)` | `:30` |
| `UBoolean(false, 0.42)` | `:39` |
| `UBoolean(false, 0.5) and UBoolean(false, 0.2)` | `:45` — **proves the literal composes as an operand of ordinary OCL boolean operators with no grammar change** |
| `UBoolean(true, 0.79) and true` | `:66` |
| `UBoolean(3 + 2, 1)` | `:7-8` — error case, `-> Value must be Boolean` |

### `UString` — `'UString' LPAREN additiveExpression COMMA additiveExpression RPAREN`

**UNVERIFIABLE — no corpus example exists.** `grep -c UString *.in` → `0` in all four files. The
grammar rule is read directly from `F/parser/base/OCLBase.gpart:495` and is certain; the *accepted concrete
syntax* is therefore known but **entirely unexercised**. **The five-examples requirement cannot be
met for `UString`.** Note also that `ASTUStringLiteral` overrides only `gen` and `getFreeVariables`
— **it has no `toString()`**, unlike the other four; add one for parity (and see §7.2 M-33: doing so
changes the text of every `SemanticException` that embeds the node).

### `SBoolean` — 4 arguments, `additiveExpression` ×4

`(belief, disbelief, uncertainty, agent)` — names from `ASTSBooleanLiteral.java:12-15`.
**UNVERIFIABLE — no corpus example exists.** `grep -c SBoolean *.in` → `0` in all four files.

### Ruling: do NOT create a single `ASTUncertainLiteral` — REFUTED

The port plan names one. **No such class exists in the historical tree, and it should not be
created.** The per-type split is load-bearing (different arities, different `gen` checks, five
distinct target expressions, different `toString()` renderings). Collapsing them would require a
runtime tag plus a five-way switch in `gen`, strictly worse than the grammar's own five-way LA(1)
dispatch that already exists at `F/parser/base/OCLBase.gpart:495-500`. **Port five classes; correct the port plan.**

## 5.3 Query surface — `uSelect` / `uSelectC`

```
source '->' 'uSelect'  '(' [ elemVarsDeclaration '|' ] expression ')'
source '->' 'uSelectC' '(' [ elemVarsDeclaration '|' ] expression ',' additiveExpression ')'
```

Both go through the **same** `queryExpression` rule; the grammar does not distinguish them.
`uSelect`/`uSelectC` are plain `IDENT`s admitted by the semantic predicate
`{ ParserHelper.isQueryIdent(input.LT(1)) }?` because `queryIdentMap` now contains them (E23).
**They do not enter the reserved-word set** — a materially lower-risk mechanism than the one used
for the literals, and the pattern to prefer.

The confidence is **optional in the grammar and mandatory only in the AST**:
`F/parser/ocl/ASTQueryExpression.java:180-181` throws `SemanticException("'uSelectC' need to specify the
confidence.")` when `Q_USELECTC_ID` is reached with `uncertainty == null`. **At most one iterator
variable** is allowed (`:141-145`).

Corpus, all from `UCollectionOperations.in` — `uSelect` 5 occurrences, `uSelectC` 4:

| # | expression | citation | expected |
|---|---|---|---|
| 1 | `Set{UReal(2, 0.5), 2.5, 3.2, 1, UReal(3, 0.25)}->uSelect(e \| e >= 2)` | `:139` | `Set{2.5,UReal(3.0, 0.25),3.2} : Set(UReal)` |
| 2 | `…->uSelect(e \| e <= 2)` | `:142` | `Set{1,UReal(2.0, 0.5)} : Set(UReal)` |
| 3 | `let A = Set{2, 3, UReal(3, 0.5)} in (A->iterate(…)).equals(A->uSelect(e\|e>2))` | `:146` | `true` |
| 4 | same over a `Sequence` | `:150` | `true` |
| 5 | same over a `Bag` | `:154` | `true` |
| 6 | `Set{UReal(2, 0.5), 2.5, 3.2, 1, UReal(3, 0.25)}->uSelectC(e \| e >= 2, 0.49)` | `:160` | `Set{2.5,UReal(3.0, 0.25),3.2,**UReal(2.0, 0.5)**} : Set(UReal)` |
| 7 | `…->uSelectC(e \| e <= 2, 0.49)` | `:163` | `Set{1,UReal(2.0, 0.5)}` |
| 8 | `… .equals( A->uSelectC(e \| e >= 2, C) )` with `let C = 0.7` | `:169` | `true` |
| 9 | same shape with `let C = 0.45` | `:173` | `true` |

**Make cases 1 vs 6 the smoke test.** Identical source and predicate, but `uSelectC` at confidence
`0.49` additionally admits `UReal(2.0, 0.5)`. **Any port that wires `uSelectC` to `ExpUSelect` still
passes every `uSelect` test and fails exactly here.**

Cases 8/9 prove the confidence may be a **variable**, not just a literal, and pin the documented
postcondition `uSelectC(e | p, C) ≡ iterate` with `(p).toBooleanC(C)`. Cases 3/4/5 establish the
same for `uSelect` over `Set`, `Sequence` and `Bag` — i.e. **`uSelect` must preserve the source
collection kind**. All nine use `.equals(...)` or a `Set(UReal)` annotation, so **`UCollectionOperations.in`
is the integration fixture for the whole grammar change**.

`evalUSelect` semantics (fork `F/uml/ocl/expr/ExpQuery.java:179-221`), with the one correction the port should make:

```
confident = evalAndAsertConfident(ctx)        # 0.5 when fUncertaintyExp == null; RuntimeException outside [0,1]
v = fRangeExp.eval(ctx)
rangeVal = (CollectionValue) v                # <-- DEFECT: cast is OUTSIDE the guard
if (!v.isUndefined()):
    require rangeVal.type().isInstantiableCollection() else RuntimeException
    for elemVal in rangeVal:
        queryVal = fQueryExp.eval(ctx)
        if queryVal.isUndefined(): queryVal = BooleanValue.FALSE
        if queryVal.isBoolean() and isTrue():                        keep
        elif queryVal.isUBoolean() and probability() >= confident:   keep
    return ((CollectionType) rangeVal.type()).createCollectionValue(resValues)
return UndefinedValue.instance                # unreachable
```

**Defect to fix during the port (one-line move).** `UndefinedValue extends Value`, not
`CollectionValue`, so an undefined range throws `ClassCastException` instead of returning
`UndefinedValue.instance`, and the final `return` is unreachable. `evalSelectOrReject` gets this
right (guard first, cast second). **Move the cast inside the guard; mark it a deliberate deviation.**

Behavioural notes worth pinning: `evalAndAsertConfident` runs **before** the range is evaluated, so
an out-of-range confidence throws even for an empty/undefined range (the fork's tests rely on this);
the failure is an **unchecked `RuntimeException`**, not `ExpInvalidException` — a runtime failure,
not a static one, whereas the confidence's *type* check is static; and plain `Boolean` predicates
are accepted, so `uSelect` degenerates to `select` on a non-uncertain predicate (consistent, because
`BooleanType.isKindOfUBoolean` is `true`).

**`UNVERIFIABLE`: whether dot-position `X.uSelect(...)` was intended or tested** — no corpus case
exercises it; `->uSelect` is used only in arrow position.

## 5.4 `uncertaintyType` — pure damage repair, and the port should know why

```
uncertaintyType returns [ASTType n]
:   name=('UReal'|'UInteger'|'UBoolean'|'UString' | 'SBoolean') { $n = new ASTSimpleType($name); }
    ;
```

This produces `new ASTSimpleType($name)` — **byte-for-byte what `simpleType` produces**
(`simpleType : name=IDENT { $n = new ASTSimpleType($name); }`), and `ASTSimpleType.gen` resolves the
name through `TypeFactory.buildInTypesMap`, which **already registers all five** (§3.4c).

**So if `UReal` still lexed as `IDENT`, `simpleType` would have resolved it with zero grammar
changes.** `uncertaintyType` exists *only* because §5.2's literal rule turned those five names into
keyword tokens and thereby broke `simpleType` for them. It is a consequence of that design, not an
independent feature. It also **drops the `setStartToken(tok)` call** the other three `type`
alternatives make — a minor inconsistency that degrades error positions for uncertainty types; add
it for parity.

`uncertaintyType` is reachable through `collectionType` too, which is what makes `Set(UReal)` work —
exercised 9 times in `UCollectionOperations.in`.

## 5.5 Ambiguity risk, per added rule

Fixtures that would catch a regression:
`use-core/src/test/resources/org/tzi/use/parser/test_expr.in` (10 199 B, driven by
`TT/parser/USECompilerTest.java:79`), the model corpus `t1.use`…`t37_imports.use` with paired `.fail` files,
and `use-gui/src/it/resources/testfiles/shell/*.{use,in,expected}` driven by `ShellIT.java`'s
JUnit-5 `@TestFactory` (`:63-80`).

| # | added rule | collides with | severity | catching fixture |
|---|---|---|---|---|
| 1 | `identicalExpression` (`'equals'`) | any user-defined operation **named** `equals`; any call `x.equals(y)` | **HIGH — confirmed live collision** | `use-gui/src/it/resources/testfiles/shell/t098.use:11`; `use-gui/src/it/resources/testfiles/shell/imports/t133_import_date.use:29`; `use-gui/src/it/resources/testfiles/shell/imports/t133_import_datetime.use:12` (paths **corrected** — `shell/` was missing; audit-02 F1/M3-M4) |
| 2 | `'UReal'`…`'SBoolean'` keyword tokens | any class / dataType / attribute / operation / role / variable with those names | HIGH in principle, **no live collision today** | `test_expr.in`, `t1.use`…`t37_imports.use`, `testfiles/shell/*.use` — all clean |
| 3 | `queryExpression`'s optional `COMMA additiveExpression` | nothing syntactically; **silently accepts** a bogus extra arg to `select`/`collect`/`forAll`/… | MEDIUM (silent acceptance) | **no upstream fixture catches this — a coverage gap the port introduces** |
| 4 | `uncertaintyType` alternative in `type` | `simpleType ::= IDENT` — disjoint by token class | LOW | `test_expr.in`, `t*.use` type declarations |
| 5 | `uSelect`/`uSelectC` in `queryIdentMap` | a model operation of that exact name on a collection | LOW, no live collision | `testfiles/shell/*.use` |

### Risk 1 in detail — real, not hypothetical (**B4**)

`'equals'` is an inline literal, so ANTLR lexes `equals` as a keyword token and **never** as `IDENT`.

**(a) Operation *declarations* break.** `operationDefinition` binds the name to `IDENT`
(`G/base/USEBase.gpart:264-269`). `t098.use:11` declares, inside `class Date1`,
`equals(t : Date1) : Boolean`. With the fork's rule, `equals` arrives as the `'equals'` token and
`name = IDENT` cannot match — **a parse failure on an upstream fixture**. `t098.in` is an
expected-error file whose three lines name only the duplicate-`isEmpty` and unrelated-types
diagnostics, so any new parse error changes the output and fails the test. Same form at
`t133_import_date.use:29`.

**(b) Call sites are silently rerouted.** `t133_import_datetime.use:12` calls
`self.date.equals(other.date)`. Even if (a) were fixed, this would be captured by
`identicalExpression` and resolved to `Op_identical` instead of the user-defined `Date::equals`
(which compares day/month/year). **Different semantics, no diagnostic.**

`identicalExpression` also changes the precedence chain (`expression → identicalExpression →
conditionalImpliesExpression`), is left-associative and **binds looser than `implies`**
(`a implies b .equals( c )` parses as `(a implies b).equals(c)`), binds tighter than `let … in`, and
is **not confined to OCL** — `OCLBase.gpart` is textually included into all six composite grammars
(`OCL.g:191`, `USE.g:700`, `Soil.g:736`, `Generator.g:967`, `TestSuite.g:298`, `ShellCommand.g:489`).

**Port directive, in order of preference: (1)** drop `identicalExpression` and register
`Op_identical` under a name that cannot collide (or reuse `=`), leaving the call to the ordinary
`operationExpression` path — the same low-risk mechanism `uSelect` uses; **(2)** keep the rule but
gate it with a semantic predicate on the token *text* so `equals` stays an `IDENT`, and resolve
user-defined `equals` operations ahead of `Op_identical`; **(3)** accept the break — **not
recommended**, it silently narrows the modelling language for every downstream user.

### Risk 2 in detail — reserved words, currently latent

Same mechanism, same reach: `class UReal`, `attributes x : … UString`, a role named `SBoolean`, a
`let UReal = …` all become parse errors. **Nothing in the repository trips it today:**

```bash
grep -rlnwE "UReal|UInteger|UBoolean|UString|SBoolean" \
  --include=*.use --include=*.soil --include=*.cmd --include=*.in --include=*.assl . \
  | grep -v reference-repositories        # no output
```

So the port can ship this **without breaking any existing fixture**, but the language is narrowed.
Unlike risk 1 that is arguably the intended design — these are genuinely new built-in type names and
`buildInTypesMap` reserves them at the semantic level anyway. **Document it as a deliberate,
breaking-in-principle change and add a negative fixture** (`class UReal … end` → expected error) so
the behaviour is pinned rather than accidental.

**Leverage available here:** because `mkSimpleType` already resolves all five names, the `literal`
rule is the *only* reason they must be keywords. A predicated form
(`{ input.LT(1).getText().equals("UReal") }? IDENT LPAREN …`) would keep them as `IDENT`s, make
`uncertaintyType` (§5.4) unnecessary, and eliminate risk 2 outright. **Worth costing before copying
the fork verbatim.**

### Risk 3 — the untested gap the port introduces

`queryExpression` is reached only through the `isQueryIdent` predicate, so the comma arm is not
offered to arbitrary operation calls — **but it is offered to every query ident.** The grammar
accepts `->collect(x | x.a, 5)` and `->forAll(x | p, 5)`; `ASTQueryExpression.gen` reads
`fUncertainty` only in the `Q_USELECTC_ID` branch, so for every other query id the extra argument is
**parsed and silently discarded**. Nothing upstream calls a query operation with a spurious second
argument, so **no fixture would notice**. Add a negative fixture and the corresponding guard in
`ASTQueryExpression.gen`, alongside the existing null-check at `:180-181`.

The confidence argument is `additiveExpression`, not `expression`, so it **cannot contain a
relational or boolean operator**: `->uSelectC(e | p, a > b)` is a **syntax error**.

### Risks 4 and 5 — low

**4.** After the port, `type` has five alternatives, LA(1)-disjoint by token class. The only genuine
LA(1) conflict in that set — `simpleType` vs `modelQualifiedType`, both starting `IDENT` — is
**pre-existing in 7.5.0** and untouched. `uncertaintyType` adds no new conflict.
**5.** `uSelect`/`uSelectC` enter `queryIdentMap`, not the token vocabulary, so they stay `IDENT`s.
No model operation of that name exists in the repository.

## 5.6 Grammar / parser port checklist

| item | action | §|
|---|---|---|
| `G/base/OCLLexerRules.gpart` | **no edit** | 5.1 |
| `G/base/OCLBase.gpart` | 8 hunks, +35 −8; **preserve 7.5.0's `modelQualifier`, `modelQualifiedType`, `oclIsInState`, `importStatement`, `dataTypeDefinition`** | 5.1 |
| `T/parser/base/ParserHelper.java` | +6 lines, 3 hunks, nothing removed | E23 |
| `T/parser/ocl/ASTQueryExpression.java` | field + `gen` dispatch (**4 switch labels**) + `toString`, **not just a ctor overload**; add the guard rejecting a confidence arg for non-`uSelectC` ops | E8, 5.5 |
| AST literal classes | port **five**; do **not** create `ASTUncertainLiteral` | 5.2 |
| `ASTSBooleanDefExpression` | **do not port** — dead code (B10) | §8.1 |
| `ASTUStringLiteral` | add the missing `toString()` — but see §7.2 M-33 | 5.2 |
| `uncertaintyType` | port, add `setStartToken(tok)` for parity; **reconsider entirely if the predicated-literal route is taken** | 5.4 |
| `'equals'` keyword | **do not ship as-is** (B4) | 5.5 |
| `UString`, `SBoolean` | write new fixtures or ship explicitly marked untested | 5.2, §6.4 |
| smoke test | `UCollectionOperations.in:139-143` vs `:160-164` | 5.3 |
| generated parsers | **do not port the fork's 12 checked-in ANTLR outputs** — 7.5.0 generates them | 4.5 |

---

# 6. Test oracle inventory

## 6.1 The ten historical artefacts

All ten are **JUnit 3** (`import junit.framework.TestCase;` + `extends TestCase`). There is not a
single `@Test` annotation anywhere in `FT/`. Suites are wired by hand through `AllTests.suite()`.

| # | File (relative to `FT/`) | Test methods | Assertion calls |
|---|---|---|---|
| 1 | `uml/ocl/value/URealValueTest.java` | 5 | 54 |
| 2 | `uml/ocl/value/UIntegerValueTest.java` | 3 | 18 |
| 3 | `uml/ocl/value/UBooleanValueTest.java` | 3 | 49 |
| 4 | `uml/ocl/expr/URealExpOpsTest.java` | 32 | 356 |
| 5 | `uml/ocl/expr/UIntegerExpOpsTest.java` | 39 | 460 |
| 6 | `uml/ocl/expr/UBooleanExpOpsTest.java` | 27 | 142 |
| 7 | `uml/ocl/expr/UCollectionExpOpTest.java` | 14 | 13 |
| 8 | `uml/ocl/expr/ExpQueryUncertaintyTest.java` | 12 | 15 |
| 9 | `uml/ocl/type/TypeTest.java` | 46 | 1452 (+9 `EqualsTester`) |
| 10 | `parser/uncertainty/USECompilerUncertaintyTest.java` | 1 (data-driven, **1427** entries) | 3 (+1427 in loop) |
| | **TOTAL** | **182** | |

```bash
cat $FILES | grep -cE '^[[:space:]]*(public|protected|private)?[[:space:]]*void[[:space:]]+test[A-Za-z0-9_]*[[:space:]]*\('   # 182
grep -l 'junit.framework.TestCase' $FILES | wc -l    # 10
grep -c '@Test' $FILES                               # 0 for every file
```

**All 182 migrate to Jupiter**: `@Test` per method, `@BeforeEach` for `setUp()`,
`Assertions.assertEquals(expected, actual, message)` — **JUnit 3 puts the message first, so every
one of the 943 three-argument call sites must have its argument order rewritten**, and the 12 sites
where all three arguments are `String` **compile silently under Jupiter with the arguments
permuted** (§7.2 CF-6/CF-7 — CRITICAL). `AllTests` suites are dropped in favour of surefire
discovery, which **loses the ordering that `Options.explicitVariableDeclarations` depends on**
(§7.2 CF-5, B12).

## 6.2 Operation coverage — the checklist S4–S7 are measured against

Derived mechanically from `ExpStdOp.create("<name>", …)` occurrences plus the expression classes
constructed directly:

```bash
for f in URealExpOpsTest UIntegerExpOpsTest UBooleanExpOpsTest UCollectionExpOpTest ExpQueryUncertaintyTest; do
  echo "== $f"
  grep -oE 'ExpStdOp\.create\([[:space:]]*"[^"]+"' "FT/uml/ocl/expr/$f.java" \
    | sed 's/.*"\(.*\)"/\1/' | sort | uniq -c | sort -rn
done
```

| Test class | Ops | Names |
|---|---|---|
| `URealExpOpsTest` | **22** | `+ - * / abs equals floor inv max min neg power round setUncertainty setValue sqrt toInteger toReal toString toUInteger uncertainty value` |
| `UIntegerExpOpsTest` | **18** | `+ - * / abs div mod neg power setUncertainty setValue sqrt toInteger toReal toString toUReal uncertainty value` — **no `min`/`max`, no `inv`, no `equals`, no `floor`/`round`, no `toUInteger`** |
| `UBooleanExpOpsTest` | **14** | `and confidence equalsC equivalent implies not or setConfidence setValue toBoolean toBooleanC toString value xor` |
| `UCollectionExpOpTest` | **5** | `excludes excludesAll includes includesAll uCount` (**`uCount` unasserted**) |
| `ExpQueryUncertaintyTest` | 2 std ops + 4 node types | `sum`, `>`/`>=`; `ExpForAll`, `ExpExists`, `ExpUSelect`, `ExpUSelectC` |

Corpus operation sets:

| File | Named operations | Infix |
|---|---|---|
| `URealExpression.in` | `abs equals floor inv max min neg oclIsKindOf oclIsTypeOf power round setUncertainty setValue sqrt toBoolean toInteger toReal toString toUInteger uncertainty value` | `+ - * / < <= > >= = <>` |
| `UIntegerExpression.in` | `abs equals inv mod neg power setUncertainty setValue sqrt toBoolean toInteger toReal toString toUReal uncertainty value` | `+ - * / div < <= > >= = <>` |
| `UBooleanExpression.in` | `confidence equals equivalent toBoolean value` | `and or xor not = + - * /` |
| `UCollectionOperations.in` | `equals excludes excludesAll exists forAll including includes includesAll iterate sum toBoolean uSelect uSelectC` | `= <> < <= > >= and or` |

**Union — the S4–S7 checklist:**
`+ - * / div mod < <= > >= = <> and or xor not implies equivalent abs neg sqrt power floor round inv
min max value setValue uncertainty setUncertainty confidence setConfidence equals equalsC toInteger
toReal toString toUReal toUInteger toBoolean toBooleanC includes includesAll excludes excludesAll
uCount sum forAll exists iterate including uSelect uSelectC oclIsTypeOf oclIsKindOf`.

Value-level API covered by the three `*ValueTest` classes (not `ExpStdOp` names): constructors,
`value()`, `uncertainty()`/`probability()`, `type()`, `toString(StringBuilder)`, `compareTo`,
`equals`, `hashCode`, and the 14 `isXxx()` predicates. Behaviour pinned that a port must reproduce:
uncertainty normalised to `Math.abs(u)`; `URealValue.compareTo` is **interval-overlap** ordering
(`UReal(0,2).compareTo(UReal(0,1)) == 0`, `…(UReal(1,1)) == 0`, `…(UReal(-1,1)) == 0`, but
`…(UReal(5,2)) == -1`) while `equals` is **exact**; `UBooleanValue` canonicalisation
(`valueOf(false, 0.2)` becomes `UBoolean(true, 0.8)`; `valueOf(false, 1)` becomes `(true, 0.0)` and
is `equals` to `FALSE` **with an equal hashCode**); `isReal()` is **false** for a `URealValue` and
`isInteger()`/`isUReal()` are both **false** for a `UIntegerValue`.

## 6.3 `TypeTest.java` — isolating the uncertainty additions

The target repo's `TT/uml/ocl/type/TypeTest.java` was verified **pristine upstream** before use:
`grep -c -iE 'UReal|UInteger|UBoolean|UString|SBoolean'` → **0**.

| Bucket | Assertion calls |
|---|---|
| In the 10 wholly-new uncertainty methods | **332** |
| Uncertainty rows added inside upstream methods | **280** |
| Untouched upstream assertions | **849** |
| **Total** | **1461** (= 1452 `assert*`/`fail` + 9 `EqualsTester`) |

The 10 wholly-new methods (`testIsTypeOfSBooloean` 23, `testIsKindOfSBoolean` 44,
`testIsTypeOfUBooloean` 23, `testIsKindOfUBoolean` 44, `testIsTypeOfUInteger` 22,
`testIsKindOfUInteger` 44, `testIsTypeOfUReal` 22, `testIsKindOfUReal` 44, `testIsTypeOfUString` 22,
`testIsKindOfUString` 44) do not exist in 7.5.0 at all and **move verbatim**. The fork's typo
`Booloean` is copied from upstream's own `testIsTypeOfBooloean`.

The 280 additions inside upstream methods are **mechanical and safely movable**: a fixed 5-row block
appended to each `testIsTypeOfX`, and a fixed 5-row block appended to **each** of the two
`VoidHandling` blocks in each `testIsKindOfX`. The upstream assertions themselves are **not
modified**. Non-`assertFalse` rows: `Boolean` → `isKindOfUBoolean`/`isKindOfSBoolean` true;
`Integer` → `isKindOfUReal`/`isKindOfUInteger` true; `Real` → `isKindOfUReal` true; `String` →
`isKindOfUString` true; **`VoidType`'s `INCLUDE_VOID` block — all five `assertTrue`**.

**Copy-paste defects to fix rather than reproduce** (four rows sit in an `INCLUDE_VOID` block but
pass `EXCLUDE_VOID`, so that mode is asserted twice and `INCLUDE_VOID` never at all):
`testIsKindOfInteger:1063`, `testIsKindOfSequence:1599`, `testIsKindOfSBoolean:674`,
`testIsKindOfUBoolean:752`. Plus four duplicated `assertFalse(type.isTypeOfUReal())` rows
(`testIsTypeOfBooloean`, `…Collection`, `…Enum`, `…Class`), one ordering slip
(`testIsKindOfOclAny:1290-1294` swaps `SBoolean`/`UString`), one mislabelled subtype row
(`testSubtype:130-132` is labelled `"UReal < OclAny"` but asserts `mkUReal().conformsTo(mkUReal())`
— assert the intended `mkOclAny()`), and one copy-paste bug in `testEquals` (`:401-404` re-asserts
the `UBoolean` pair, **never touching `sbt1`/`sbt2`**).

**`testSupertype` is the exception — it cannot be moved (B5).** It is the only method where the fork
changed the *expected value* of an upstream assertion rather than adding an independent one:
**10 of its 17 assertion blocks are MUTATED**, covering `Boolean`, `Integer`, `Real`, `String`,
`Collection(Boolean)`, `Collection(Integer)`, `Collection(Collection(Real))`, `Set(Integer)`,
`Sequence(Integer)`, `Bag(Integer)`. Five blocks are new (SBoolean, UBoolean, UInteger, UReal,
UString); two are untouched.

**Recommended port shape** — `TT/uml/ocl/type/UncertaintyTypeTest.java` (JUnit 5), **598
assertions**, with upstream's `TT/uml/ocl/type/TypeTest.java` receiving **zero** edits: the 10 verbatim methods (332,
four defects corrected) + `testSubtypeUncertainty` (17, one defect corrected) + `testSupertypeUncertainty`
holding **only the 5 new rows** + `testTypeHashCodes` (4, with `sbt1`/`sbt2` actually asserted) +
per-upstream-type predicate methods re-deriving `TypeFactory.mkX()` and asserting only the five/ten
new predicates (240 once the 4 duplicates are dropped and the 4 `EXCLUDE_VOID` rows corrected).
**The 10 mutated rows are not test material — they are B5.**

Note the framework difference: the fork imports `com.gargoylesoftware.base.testing.EqualsTester`,
7.5.0 imports `com.google.common.testing.EqualsTester`. The new class needs neither — its four added
assertions are plain `hashCode` equalities.

**Also note the scale of the alternative.** If the port instead adds the ten predicates to `Type` and
extends upstream's `TypeTest` in place, **every one of the ~39 existing `testIsTypeOf*`/`testIsKindOf*`
methods needs ten more `assertFalse` lines** — mechanical but large.

## 6.4 Corpus census and harness

| File | Bytes | Lines | Comments | Blanks | Continuations | **Entries** |
|---|---|---|---|---|---|---|
| `UBooleanExpression.in` | 9 170 | 400 | 28 | 137 | 0 | **118** |
| `UCollectionOperations.in` | 6 119 | 173 | 17 | 55 | 14 | **44** |
| `UIntegerExpression.in` | 43 871 | 2 211 | 46 | 781 | 0 | **692** |
| `URealExpression.in` | 33 561 | 1 881 | 69 | 667 | 0 | **573** |
| | | | | | | **1427** |

Expected-result type distribution across all 1427:

| Suffix | Count | | Suffix | Count |
|---|---|---|---|---|
| ` : Boolean` | 527 | | ` : Real` | 21 |
| ` : UReal` | 486 | | ` : Integer` | 13 |
| ` : UInteger` | 210 | | ` : String` | 10 |
| ` : OclVoid` | **79** | | ` : Set` | 4 |
| ` : UBoolean` | 72 | | (compile-error, no suffix) | **5** |

The 79 ` : OclVoid` entries are the ones B6 systematically offsets.

**The five compile-error entries and their exact expected text**: `Value must be Boolean` (×2),
`Value must be Integer or Real`, `Uncertainty must be Integer or Real`,
`Probability must be a Integer or Real`. Produced by `F/parser/ocl/ASTURealLiteral.java:28,:31` and
`F/uml/ocl/expr/ExpConstUBoolean.java:18,:21` — **the port must keep those message strings
byte-identical if the corpus is reused verbatim.**

**`.in` format** (reproduce exactly so the four files can be copied byte-for-byte): one entry = one
expression + one expected line; blank and `#`-prefixed lines ignored anywhere; a line ending in a
backslash continues — the corpus writes **two** backslashes and the parser removes **two**
characters (`line.length()-2`), and **`-2` is NOT a bug**: `od -c` shows the line ends `\ \ \n`.
Only `UCollectionOperations.in` uses continuations (14 lines). The expected string is
`line.substring(3)`, i.e. exactly `"-> "` — all 1427 match `^-> ` and none is indented. Two shapes:
`value : Type` (the exact `Value.toStringWithType()` output) or a bare error message.

**Harness defects to fix rather than reproduce**: the failure message `"evaluate : " + expTest` has
no `toString()` on `ExpressionTest`, so a failing error-path entry reports an identity hash (G3); the
error-path split regex `"\n(\r\n)"` means "LF **followed by** CRLF", which no platform emits, so on
Linux it degenerates to "compare the whole buffer" (G4); `sos` is not reset on the success path;
`File.listFiles` order is unspecified (**sort it**); and a failure aborts the whole run (**use
`@TestFactory` so one bad entry does not mask the other 1426**).

Note on `UBooleanExpression.in`: several entries are followed by a **commented-out** alternative
expectation, e.g. `UBoolean(true or false, 3 - 5)` → live `-> Undefined : OclVoid`, commented
`# -> Probability must be a non-uncertainty number between 0 and 1`. **The commented lines record
behaviour the authors wanted but did not have. Do not resurrect them without a decision.**

## 6.5 Which type is under-evidenced — explicit statement

> **`UString` is the under-evidenced type. It is implemented but behaviourally unpinned.**

```bash
grep -rln 'UString'  "FT/"   # only uml/ocl/type/TypeTest.java
grep -rln 'SBoolean' "FT/"   # only uml/ocl/type/TypeTest.java
ls "FT/parser/uncertainty"/*.in   # exactly four files, none for UString or SBoolean
```

* **No `UStringValueTest.java`** — `FT/uml/ocl/value/` holds only `AllTests`, `ValueTest`,
  `UBooleanValueTest`, `UIntegerValueTest`, `URealValueTest`.
* **No `UStringExpOpsTest.java`** — `FT/uml/ocl/expr/` holds `URealExpOpsTest`,
  `UIntegerExpOpsTest`, `UBooleanExpOpsTest`, `UCollectionExpOpTest`, `ExpQueryUncertaintyTest` and
  upstream's five.
* **No `UStringExpression.in`.**
* **No corpus example of the `UString(...)` literal** (§5.2).
* `UString` appears in exactly **one** test file in the whole fork test tree — `FT/uml/ocl/type/TypeTest.java`, as
  **type-lattice rows only**.

Meanwhile the product source **does** ship `UStringValue.java`, `UStringType.java` and a **21-operation**
`StandardOperationsUString.java` (§2.4). **The port has an oracle for `UString`'s place in the type
lattice and nothing else.** Any `UString` operation semantics must be justified from the
`uDataTypes` library source (or a jar probe), not from these tests — which is exactly what §2.4 does,
and why every "verified" claim there is a **jar execution**, not a fork expectation.

**The same is true of `SBoolean`** (`SBooleanValue.java`, `SBooleanType.java` and a 1502-line,
39-operation registry exist; the only test reference is `TypeTest`), though `SBoolean` is not one of
the four U-types. **Zero of those 39 operations is covered by any test in the fork.**

`UReal`, `UInteger` and `UBoolean` each have both a value test and a corpus, and are well evidenced —
with two named exceptions: **6 of `UReal`'s 18 operations (the trig family) have no test and no
corpus case at all** (§2.2), and **`uCount` has no oracle anywhere** (§6.6 G1).

## 6.6 Named gaps in the historical oracle

| # | Gap |
|---|---|
| **G1** | **`uCount` has no oracle.** `UCollectionExpOpTest#testUCount` (`:268-279`) evaluates `Set{2.0, UReal(2,3)}->uCount(UReal(2,3))` into a local and **asserts nothing**. Not in any `.in` file either (`grep -c uCount *.in` → 0 in all four). The expected value of `uCount` is **UNVERIFIABLE** from the historical tests |
| **G2** | **`UInteger / UReal` is never tested where the name promises.** `UIntegerExpOpsTest#testDivideByRWithUReal` (`:2777-2973`) is commented `// UInteger(-9, 0) / UReal(-9, 0)` but contains **zero** `URealValue` operands and 48 `UIntegerValue` ones. The overload **is** covered — by the corpus, 24 entries in `UIntegerExpression.in` from `:1128` — just not where the method name says. **Do not count that method as evidence for the overload** |
| **G3/G4** | harness failure-message and split-regex defects (§6.4) |
| **G5** | **Commented-out assertions.** `UBooleanValueTest:11-17` and `:36-42` (probability-range validation, `// FIXME: When It will be fixed in atenea library`) and `URealExpOpsTest#testURealSqrt:431-433` (`// TODO Descomentar cuando se actualice la librería`). **These behaviours were never pinned.** Note §2.2 shows the `sqrt` one is **obsolete against the binding jar** (`sqrt(0,0)` already returns `(0.0, 0.0)`) — re-enabling it is a deliberate, documented decision, not a silent one |
| **G6** | **`ExpQueryUncertaintyTest`'s confidence-range tests evaluate the wrong expression.** `:166-206` build the `ExpUSelectC` and then call `e.eval(op, state)` on the **inner comparison**, not on the `ExpUSelectC`. As written they pass only if the *constructor* throws. Whether out-of-range confidence is meant to be rejected at construction or at evaluation is **UNVERIFIABLE** from this test |
| **G7** | `testSupertype` — **B5** |
| **G8** | `File.listFiles` order is unspecified. Not observed to matter (fresh `MSystem` and `VarBindings` per entry), but **sort** |
| **G9** | **No historical test was executed.** Maven is off-limits and the fork is an Ant/Java-1.7/JUnit-3 tree. Every claim in §6 is a static reading. **Whether all 182 methods actually passed against `lib/atenearesearchgroup.uncertainty.jar` is UNVERIFIABLE** |

---

# 7. Modernization ledger

**Status: PROPOSAL ONLY.** Nothing here gates anything until the fidelity verdict for the
corresponding stage is green. Target: Java 21, Maven, **JUnit 5 Jupiter, no vintage engine** in the
product build (`use-core/pom.xml:16-17`, `:63-64`; no `junit-vintage-engine` anywhere) — note the
tension with **B3**.

| prefix | meaning |
|---|---|
| `CF-n` | **compile-forced** — will not build on the target as written. Mandatory |
| `M-n` | proposal with no floating-point exposure |
| `F-n` | **float-sensitive** — a comparison, equality, singleton selection or confidence rides on binary FP at this site |

**Classification rule applied**: a row is BEHAVIOUR-PRESERVING **only where the mechanism that makes
it so can be named**. Everything else is BEHAVIOUR-CHANGING — *including* cases where the "change"
is a bug fix. Those rows read **DEFER** and are collected in §7.2.

| bucket | n |
|---|---|
| BEHAVIOUR-PRESERVING rows | **46** |
| **BEHAVIOUR-CHANGING rows — these go to a human (B7)** | **33** |
| compile-forced rows (Table A) | 10 |
| float-sensitive rows (`F-*`) | 16 |
| **total rows** | **77** |

```bash
F=docs/port2/spec-parts/16-modernization-ledger.md
grep -cE '^\| CF-[0-9]+ \|' $F                                             # 10
grep -cE '^\| M-[0-9]+ \|'  $F                                             # 51
grep -cE '^\| \*\*F-[0-9]+\*\* \|' $F                                      # 16
grep -nE '^\| (CF-|M-|\*\*F-)' $F | grep -o 'BEHAVIOUR-PRESERVING' | wc -l  # 46
grep -nE '^\| (CF-|M-|\*\*F-)' $F | grep -o 'BEHAVIOUR-CHANGING'  | wc -l   # 33
```

46 + 33 = 79 against 77 rows because `M-48` and `M-49` each carry one preserving half and one
changing half and are counted in both buckets.

## 7.1 BEHAVIOUR-PRESERVING rows — safe to apply once fidelity is green

| id | Site | Change | Why it is preserving | Risk |
|---|---|---|---|---|
| CF-1 | all 8 in-scope test files | drop `junit.framework` + `extends TestCase`; annotate exactly the **122** existing `test*` methods with `@Test` | JUnit 3 discovery is `public void test*`; `grep -cE '^\s+public void test'` sums to 122, so annotating exactly those preserves the executed set. Both frameworks default to a fresh instance per method | **HIGH — a missed `@Test` silently deletes a test rather than failing. Re-count 122 after migration** |
| CF-2 | `URealValueTest.java:8` | delete `import static org.junit.Assert.*` (entirely shadowed) | Bytecode proof: the call binds to `junit.framework.Assert.assertEquals(String,Object,Object)` (JLS 6.4.1 — inherited members shadow static imports); `javap` confirms `junit.framework.Assert` has no `(String,double,double)` overload | LOW |
| CF-3 | 4 files | `protected void setUp()` → `@BeforeEach` (keep `protected`; Jupiter allows it) | JUnit 3 calls `setUp()` before each test on a fresh instance; `@BeforeEach` under PER_METHOD does the same | LOW |
| CF-4 | `ExpQueryUncertaintyTest.java:36` | delete `super.setUp();` | `javap -c` shows `TestCase.setUp` is `Code: 0: return` | LOW |
| CF-6 | **943 sites** | `assertEquals(msg, exp, act)` → `assertEquals(exp, act, msg)` | Jupiter has no `(String,Object,Object)` overload, so every non-String triple is a **hard compile error — the compiler finds them for you**. Semantics match (`objectsAreEqual` ≡ `expected.equals(actual)`; both use `Double.doubleToLongBits` for doubles) | MEDIUM — see CF-7 |
| CF-10 | root + `use-core` `pom.xml` | add `<project.build.sourceEncoding>UTF-8</…>` | The 6+ in-scope sources with non-ASCII bytes carry them **only in comments** (`Víctor`, `métodos`, `librería`); no string literal in scope is non-ASCII. On a US-ASCII default this is a hard `error: unmappable character`, hence Table A | LOW |
| M-1 | `UncertainValue.java:37-44` | `return uEquals(other).not();` | pure local rewrite; nothing can be interposed | LOW |
| M-2 | 4 wrapper fields | add `final` | every write is in a constructor; no setter exists in any of the four | LOW |
| M-3 | `URealValue.java:104-107` | **delete** the unreachable `else if (o instanceof URealValue)` (the guard at `:98` dominates it; its body also shadows the field and compares the argument to itself) | no input reaches it. **Do not "repair" it into a live branch** — that would be a behaviour change | LOW |
| M-4 | `URealValue.java:112-127` + 7 unguarded deref sites | add `@Nullable`; **add no defensive branch** | any guard converts today's NPE into some other result | MEDIUM |
| M-5 | 3 `equals` chains | pattern-matching `instanceof`, keeping branch order **and** the outer `obj instanceof Value` guard | pattern `instanceof` is exactly test+cast in the same order. Dropping the outer guard is a *separate* change and is not proposed | LOW |
| M-7 | 5 comment typos | fix the prose (`Rremove`, `hanlde`, `clases`, `confidance`, the FALSE javadoc) | comments have no runtime effect | LOW |
| M-13 | `UStringValue.java:165-173` | enhanced-for or stream over the `List<UString>` | `get(i)` over `0..size-1` yields the same elements in the same order for any `List`. Bonus: the current loop is O(n²) on a `LinkedList` | LOW |
| M-14 | 12 sites | drop redundant `= null` initialisers | definite assignment holds; javac emits identical bytecode | LOW |
| M-15 | `URealValueTest.java:6` | delete unused `import java.util.HashSet` | unused | LOW |
| M-16 | 15 sites | `new LinkedList<>()` | diamond infers the identical argument | LOW |
| M-17 | 9 sites | `Iterator` loop → enhanced-for | `CollectionValue implements Iterable<Value>`; no body uses `it.remove()` or an index | LOW |
| M-19 | 10 unguarded downcasts | **leave them; add no `instanceof` guard** | protected by `matches()` + `ExpStdOp` only constructing the op when `matches` returned non-null | LOW |
| M-20 | `UIntegerType.java:40` | `new HashSet<>(3)` | diamond infers `Type`; capacity retained ⇒ bucket layout unchanged | LOW |
| M-23 | `UncertainType.java:3-8` | rewrite the javadoc (copied verbatim from `BasicType`) | comment only | LOW |
| M-24 | 12 `Expression` fields | add `final` | no setter, no reassignment in any of the six classes | LOW |
| M-25 | `ExpConstUReal.java:32` | delete the stray second `;` in `ctx.enter(this);;` | empty statement compiles to nothing | LOW |
| M-34 | `ASTUBooleanLiteral.java:13-14` | `public` mutable fields → `private final` | all constructions go through the ctor from the six grammars and generated parsers; `grep -rn '\.eValue\|\.eProbability'` finds no external **reader** | LOW |
| M-35 | 22 sites | `matches(Type params[])` → `matches(Type[] params)` | pure declarator syntax. **Optional — arguably skip**: 7.5.0's own `T/uml/ocl/expr/operations/OpGeneric.java:48` still uses the C-style form, so this is cosmetic-only and costs diff hygiene | LOW |
| M-36 | `StandardOperationsUString.java:21` | delete the duplicate `Op_uString_uConcat` registration | duplicates *are* retained by `ArrayListMultimap`, but both consumers stop at the first match, no code iterates `opmap.values()`, the two instances are stateless and identical, and `removeAllOperations` matches by identity so it never removed either copy anyway | LOW |
| M-39 | `StandardOperationsSBoolean.java` | optionally convert the 45 anonymous `OpGeneric`s to named classes | **Lambdas are not applicable** — `OpGeneric` is an abstract *class* overriding five methods. Extraction is a pure refactor; registration order via `values()` is unchanged | LOW — **1502 lines touched for zero behavioural gain; recommend skipping during the fidelity port** |
| M-40 | `StandardOperationsSBoolean.java:1485` | `private final OpGeneric op;` | only write is the enum ctor | LOW |
| M-41 | `StandardOperationsSBoolean.java:~1382-1479` | delete ~100 lines of commented-out constants | comments have no runtime effect — **but they are the only record of why three `SBooleanValue` fusion methods have no caller. Move that note into the port docs first** | LOW |
| M-42 | `StandardOperationsSBoolean.java:18` | `PROYECTION` → `PROJECTION` | the constant name is never used as a string; the OCL name comes from `name()` | LOW |
| M-46 | `USECompilerUncertaintyTest.java:20,22` | add `final` to two statics | neither is assigned after declaration | LOW |
| M-47 | `USECompilerUncertaintyTest.java:31-51` | inner `StringOutputStream` → `private static class` | no member of the enclosing instance is referenced | LOW |
| M-48a | `USECompilerUncertaintyTest.java:26-29` | `ExpressionTest` → `private static class` | same reason | LOW |
| M-49a | `USECompilerUncertaintyTest.java:88` | `String errArray []` → `String[] errArray` (**declarator only**) | pure syntax | LOW |
| M-50 | `USECompilerUncertaintyTest.java:128` | `append(line, 0, line.length()-2).append('\n')` | same characters. **The `-2` is NOT a bug and must not be "fixed" to `-1`** — `od -c` shows the line ends `\ \ \n`, two literal backslashes, and `line.endsWith("\\")` is a one-character test satisfied by that pair. All 14 continuation lines use it | MEDIUM |
| **F-1** | `URealValue.java:77-82` + `MathUtil` | **port `MathUtil.round` byte-identically** (E25) and leave `equals` untouched | **101 assertions spell expected uncertainties to exactly 10 decimals** and pass *only* because `equals` truncates there. Substituting `FloatUtil.equals` (ε=10⁻⁸) or `BigDecimal` moves the boundary | **CRITICAL** |
| **F-5** | `URealValue.java:45` | keep the `value() == 0 ? 0 : value()` ternary verbatim | `-0.0 == 0` is `true` (IEEE 754) so the ternary catches negative zero; `Double.valueOf(-0.0).equals(0.0)` is `false` and `Double.compare(-0.0, 0) == 0` is `false`, so **any "simplification" reintroduces `"-0.0"`** into output `URealValueTest.java:27-29` depends on | MEDIUM |
| **F-6** | `UBooleanValue.java:240` | keep the round-to-10 comparison | `UBooleanValueTest.java:136-138` requires `valueOf(false,1).equals(FALSE)`, which holds only because both sides round identically | HIGH |
| **F-7** | `UBooleanValue.java:78,80,105,107` | keep exact `== 0` / `== 1` singleton selection; **introduce no tolerance** | `1 − 1e-17` does not collapse to `TRUE` today, and singleton identity is observable through `==`-based fast paths (F-11, the `StandardOperationsUBoolean` short-circuits). `UBooleanValueTest.java:49-52` depends on `1 - 1.0` being exactly `0.0` | HIGH |
| **F-8** | `UBooleanValue.java:100-103` | keep `probability = 1 - probability` verbatim | measured: `(1 - 0.2) == 0.8` is **exactly** true and `(1 - 0.5) == 0.5` is exactly true, which is what makes `UBooleanValueTest.java:54-57` (an exact `Double.equals`) pass. Other operands are not so lucky (`1 - 0.7` = `0.30000000000000004`), so **any restructuring is observable** | HIGH |
| **F-9** | `UBooleanValue.java:46` | keep the raw-`double` range check | the two range tests are **commented out** with `// FIXME: When It will be fixed in atenea library` — the library ctor clamps first, so this guard never fires for those inputs. Changing the comparison changes which inputs reach the library | MEDIUM |
| **F-11** | `SBooleanValue.Builder.build():57-68` | keep exact `== 1 && == 0 && == 0 && == 1` singleton selection | fed from `Double.parseDouble(value.toString())`, so `SBoolean(1,0,0,1)` reaches the singleton but `SBoolean(0.9999999999999999,0,0,1)` does not. Singleton identity is observable through `equals`'s `obj == this` fast path | HIGH |
| **F-12** | `StandardOperationsUBoolean.java:110-113` | keep the `< 0.5` print flip and `1 - probability` verbatim | at exactly `0.5` the `else` branch runs and prints `"true, 0.5"`. The text is whatever `Double.toString(1 - p)` gives — `1 - 0.7` prints `0.30000000000000004`. **Any restructuring changes user-visible output** | HIGH |
| **F-13** | `StandardOperationsUBoolean.java:155,157` | keep `>=` on raw doubles; **introduce no epsilon** | the `>=` **is** the documented "certain at confidence c" semantics; a tolerance changes the truth value of the OCL expression | HIGH |
| **F-14** | `StandardOperationsUBoolean.java:392,400,414,458,476,535,555` | keep the exact `== 0` / `== 1` short-circuits | **these guards decide which side effects run**, not just the value: at `:392` `and` returns `ub1` *without evaluating the right-hand operand*. A fuzzy comparison changes operand evaluation, observable through the evaluation tree and any side-effecting operand | **CRITICAL** |
| **F-15** | `StandardOperationsUReal.java:437,442`; `UIntegerValue.java:177-188` | keep the `float` exponent | `javap` on the oracle jar shows `public uDataTypes.UReal power(float);` — **there is no `power(double)`**. Widening will not compile; the narrowing and its precision loss are part of the oracle's behaviour | MEDIUM |
| **F-16** | `StandardOperationsUReal.java:102-103, 446-450` | keep the `isInfinite`/`isNaN` → `ArithmeticException` guards | this is the only place overflow/NaN out of `inverse()`/`power()` becomes an OCL error, and the `ArithmeticException` is what `ExpStdOp` catches. Replacing it with a range test or a returned `UndefinedValue` changes the error path | MEDIUM |

## 7.2 BEHAVIOUR-CHANGING rows — **33 rows requiring a human decision before S3 (B7)**

Every row here changes observable behaviour relative to the fork. The *proposed change* column reads
**DEFER** for all of them.

### Compile-forced but behaviour-changing (4)

| id | Site | What changes | Why it is unavoidable-but-changing | Risk |
|---|---|---|---|---|
| **CF-5** | the three `AllTests.java` suites | dropping them for surefire discovery **loses a fixed execution order** | Order is load-bearing: `USECompilerUncertaintyTest.java:61` writes the process-global `Options.explicitVariableDeclarations = false` (`F/config/Options.java:155`, declared `true`) and never restores it, and `F/uml/ocl/expr/ExpStdOp.java:56` is a `public static ListMultimap` mutated by `addOperation`/`removeAllOperations`. Surefire's default `runOrder` is `filesystem` | **HIGH — decide explicitly: pin the order, or make the global writes self-restoring (M-45). B12** |
| **CF-7** | **12 sites** where **all three arguments are `String`**: `UIntegerExpOpsTest.java:29,36,43,50,57,64,71,82,89,96` and `USECompilerUncertaintyTest.java:90,94` | under Jupiter these **compile silently** binding to `assertEquals(Object expected, Object actual, String message)` — **the message becomes `expected`, `expected` becomes `actual`, `actual` becomes the message.** No warning | these are the only 12 CF-6 sites the compiler will **not** catch | **CRITICAL — reorder by hand, verified individually** |
| **CF-8** | `USECompilerUncertaintyTest.java:22-24,56-57,63` | move the 4 `.in` fixtures to `src/test/resources` + classpath lookup, **and add `assertTrue(files.length > 0)`** | Under Maven the module root is `use-core/`, so `user.dir` no longer contains `src/test/org/…`; `listFiles` returns `null` and line 63 fails — *or*, if an empty directory exists, the loop runs zero times and **the test passes vacuously**. **Adding the non-empty assertion converts a previously-vacuous pass into a failure, which is itself a behaviour change and must be stated as one** | **CRITICAL. B12.** Note: the resource move must carry **all four** `.in` files |
| **CF-9** | `USECompilerUncertaintyTest.java:73,151` | pass `StandardCharsets.UTF_8` explicitly to `FileReader` and `getBytes()` | 3 of 4 `.in` files are UTF-8; the only non-ASCII bytes are on `# Creación` comment lines the reader skips. JEP 400 already makes Java 18+ default to UTF-8 — but pinning changes behaviour under an explicit `-Dfile.encoding=…` | LOW |

### Value-layer defects — reproducing vs fixing (11)

| id | Site | Defect | Consequence of fixing | Risk |
|---|---|---|---|---|
| **M-11** | `UStringValue.java:86-87` | `wrapper.getString().equals(ustring.wrapper)` compares a `java.lang.String` to a `uDataTypes.UString` (**always false**), and the second conjunct `wrapper.getsConf() == wrapper.getsConf()` compares the receiver's confidence **to itself** (always true, never looks at the argument). **Net: no `UStringValue` is ever `equals` to anything, including itself — `a.equals(a)` is `false`**, breaking reflexivity and therefore every `Set`/`Bag`/`Map` containing one | fixing to `wrapper.equals(ustring.wrapper)` (the delegate already compares both string and confidence) makes `Set`/`Bag` behave — and changes their printed contents | **CRITICAL — the single largest latent defect in scope.** There is **no `UStringValueTest`**, which is why it was never caught. **Recommend fix** |
| **M-12** | `UStringValue.java:95-104` | `compareTo` compares a bare `String` against the **wrapper form** `UString('x', 1.0)`. Note `:100`'s `!(o instanceof StringValue)` diverts every UString-vs-UString comparison to `toString().compareTo(...)`, which is at least self-consistent; **only the UString-vs-String case is wrong** | changes sort order and therefore printed `Set{…}` contents | HIGH |
| **M-8** | `UBooleanValue.java:233-234` | `(other.isFalse() && probability()==0 && !value())` is **dead** — `valueOf` normalises every value to `value == true`, so **`UBooleanValue.FALSE.equals(BooleanValue.FALSE)` returns `false`**. The `UBooleanValue`↔`BooleanValue` bridge only works in the `true` direction | repairing it makes the OCL expression `UBoolean(true, 0) = false` start evaluating to true | HIGH |
| **M-9** | `UIntegerValue.java:103-104` | `res = o.compareTo(this);` — delegates **without negating**. Masked only because `URealValue.compareTo(UIntegerValue)` itself returns `0` (F-below), so the composite answer is a constant `0` | `Value implements Comparable<Value>`; sort order of mixed collections — and therefore `SetValue`/`OrderedSetValue` printing — depends on it | HIGH |
| **M-10** | `UIntegerValue.java:84-86` | `obj.equals(this)` delegates to `URealValue.equals`, whose branch list covers only `URealValue`/`IntegerValue`/`RealValue` — a `UIntegerValue` argument falls through to `false`. **So `UIntegerValue.equals(URealValue)` is always `false`** | adding the missing arm changes cross-type equality | MEDIUM |
| **M-18** | `SBooleanValue.java:150-153` | `public int compareTo(Value o) { return 0; }` — **the entire body.** Every `SBooleanValue` compares equal to every `Value` including `UndefinedValue` and `StringValue` | it is also inconsistent with `equals`, and can make Java 21's TimSort throw `IllegalArgumentException: Comparison method violates its general contract` on a large enough mixed collection. `SBoolean.compareTo` already exists at `UDT/SBoolean.java:1570` | HIGH |
| **M-6** | 4 `assertKindOf*` sites | bare `throw new RuntimeException("A value kind of … expected")` | narrowing to `IllegalArgumentException`: `ExpQueryUncertaintyTest:179,200` catches `RuntimeException` (a subclass still satisfies it), but `ExpConstSBoolean:57` and `ASTSBooleanLiteral:35` both `catch (Exception)` and swallow it. **Every downstream `catch` could not be enumerated** | MEDIUM |
| **F-2** | `F/util/MathUtil.java:106-109`, called with `digits = 10` from **15** sites | `Math.round(double)` returns `long`; `value * 1e10` **overflows `long` and saturates** for `\|value\| > 9.223372036854776e8`. **Measured**: `Math.round(9.3e8*1e10)/1e10` and `Math.round(9.4e8*1e10)/1e10` both give `9.223372036854776E8` ⇒ **two unequal large `URealValue`s compare equal today** | replacing with `BigDecimal.setScale(10, HALF_UP)` fixes it. **No test exercises magnitudes that large**, so a silent change here would go undetected | HIGH |
| **F-3** | `URealValue.java:56-64` vs `:77-82` | `hashCode()` hashes the **unrounded** values; `equals()` compares the **rounded** ones ⇒ **the equals/hashCode contract is violated**: two `equals` `URealValue`s can land in different buckets | fixing changes `HashSet<Value>`/`SetValue` membership and therefore the **printed contents** of `Set{…}` in the `.in` fixtures | HIGH |
| **F-4** | `URealValue.java:85,87` | cross-type equality against `IntegerValue`/`RealValue` uses **raw `==`** and `uncertainty() == 0` with **no rounding**, unlike the URealValue arm directly above. Note the asymmetry: `RealValue` has **no** `URealValue` arm (so the reverse is false) and compares reals with `FloatUtil.equals` (ε=10⁻⁸) | `URealValueTest.java:134-136` passes on exact `==`. Unifying changes both directions | HIGH |
| **F-10** | `UIntegerValue.java:57-64` | `hash = Double.hashCode(value()); hash *= 7 * Double.hashCode(uncertainty());` — **multiplies**. `Double.hashCode(0.0) == 0`, so **every `UInteger(n, 0)` hashes to `0`** and they all collide. The stated intent (`1 = 1.0 = UReal(1,0) = UInteger(1,0)`) holds for neither this nor `URealValue.hashCode` | repairing to the additive, zero-guarded form used by `URealValue:58-63` changes `Set{…}` iteration order and hence printed output | **CRITICAL** |

### Type-layer (2)

| id | Site | Change | Why changing | Risk |
|---|---|---|---|---|
| **M-21** | `UIntegerType:39-45`, `URealType:33-38`, `UBooleanType:33-40`, `UStringType:23-28` | unify the `this` vs `TypeFactory.mkX()` self-entry idiom | `mkX()` and `this` are the same instance **only for the singletons**. `FT/uml/ocl/type/TypeTest.java:380-403` constructs `new UIntegerType()`, `new URealType()`, `new UBooleanType()`, `new SBooleanType()` **directly**, and for those objects `this != mkX()` — **the set contents genuinely differ** | MEDIUM |
| **M-22** | `UIntegerType:14` `public`, `URealType:8` `protected`, three others package-private | narrow all five to package-private | all in-repo callers are same-package, so nothing in the fork breaks — but `public UIntegerType()` is a **published constructor** and narrowing it is a source/binary-incompatible change for any plugin | MEDIUM |

### Expression / parser layer (8)

| id | Site | Change | Why changing | Risk |
|---|---|---|---|---|
| **M-26** | `ExpDefSBoolean.java:22-29` | add the missing `ctx.exit(this, result)` | every sibling balances the pair. Adding it changes the shape of the printed evaluation tree (`-vv`, the GUI browser) and the context-stack depth of anything nested inside | HIGH — **moot under B10** |
| **M-27** | `ExpDefSBoolean.java:15-16` | un-invert the guard | today `SBoolean(someUBooleanExpr)` **throws** while `SBoolean(someStringExpr)` is accepted and silently produces `null`. Inverting changes both outcomes | **CRITICAL — moot under B10** |
| **M-28** | `ExpConstUBoolean.java:47` | replace the `toString()` round-trip with direct accessors | `Double.valueOf(probability.toString())` on an `IntegerValue` yields `1.0` where `((IntegerValue) p).value()` yields `1`; and the `NumberFormatException` a malformed string would raise is what the `catch (RuntimeException)` converts to `Undefined`. **A direct-accessor rewrite removes that path** | MEDIUM |
| **M-29** | `ExpConstUBoolean.java:44` | add the missing `value.isUndefined()` guard | today an undefined `value` gives `value.toString()` = `"Undefined"`, so `Boolean.valueOf` gives `false`, and `valueOf(false, p)` flips it to `(true, 1-p)` — **a *defined* result from an undefined operand**. The siblings check both | HIGH |
| **M-30** | `ExpConstUString.java:44,48` | wrap in the `try/catch(Exception) → Undefined` the sibling uses | `ExpConstSBoolean` wraps its whole body; `ExpConstUString` does not. Wrapping converts an escaping `ClassCastException`/`NumberFormatException` into `Undefined` | HIGH |
| **M-31** | `ExpConstUReal.java:13-17` | move the type validation from `ASTURealLiteral` into the constructor | `ExpConstUReal` is constructed **directly with unvalidated arguments** by the test suite (`URealExpOpsTest.java:34,39,44,…` — **300+ sites**) and by any programmatic caller. Adding a **checked** `ExpInvalidException` breaks every one at compile time | HIGH |
| **M-32** | `ASTURealLiteral.java:23-24, :34` | hoist the doubled `eValue.gen(ctx)` / `eUncertainty.gen(ctx)` into locals | `ASTExpression.gen(Context)` is not documented as pure; for sub-expressions carrying variable declarations it registers into `ctx`. It also produces **two distinct `Expression` object graphs**, and the one actually installed is the second. Hoisting changes which graph is installed and how many times `ctx` is mutated | HIGH |
| **M-33** | `ASTUStringLiteral.java` | add the missing `toString()` (the other four have one) | AST `toString()` is interpolated into compiler diagnostics; adding it **changes the text of every `SemanticException` that embeds this node** | MEDIUM |

### Operations layer (2)

| id | Site | Change | Why changing | Risk |
|---|---|---|---|---|
| **M-37** | `StandardOperationsUInteger.java:13,17` + `Op_uInteger_value:54-64` | correct `matches` to `mkInteger()` | `ExpStdOp.create` stores the `Type` returned by `matches` as the **static type**, so this changes type-checking of every expression consuming `x.value()` / `x.toInteger()` on a `UInteger`. The sibling `Op_ureal_value` already declares `mkReal()`, so the two are inconsistent today | HIGH |
| **M-38** | `StandardOperationsUBoolean.java:474-477` | add the missing null guard | `UBooleanValue.valueOf(Value)` returns `null` unless `isUBoolean()`/`isBoolean()`; `UndefinedValue` is neither. Control reaches `:474` only when `v1` is undefined, so **`Undefined or Undefined` throws NPE today** — where `and` (`:400`) and `implies` (`:552`) return `Undefined`. **Adding the guard changes an NPE into a value** | **CRITICAL** |

### Test-harness layer (6)

| id | Site | Change | Why changing | Risk |
|---|---|---|---|---|
| **M-43** | `UBooleanValueTest.java:11-17, :36-42` | revive the two commented-out range tests as `@Disabled` | they record that `valueOf(true,-2)`/`(true,2)` do **not** throw today despite the guard at `UBooleanValue.java:46` (the library ctor clamps first). Reviving them **live** makes the suite red; reviving them `@Disabled` adds two "skipped" entries | MEDIUM |
| **M-44** | **40 sites** across 4 test files | `try { …; fail(...) } catch (X) {}` → `assertThrows(X.class, () -> …)` | **Two distinct shifts.** (a) `assertThrows` accepts any **subclass**, whereas the historical second `catch (Exception ex) { fail(…) }` narrows it — and `ExpQueryUncertaintyTest:179` catches `RuntimeException`, which today swallows even an NPE **as a pass**. (b) `ExpQueryUncertaintyTest:174-178` puts **two** statements in one `try`, so `assertThrows` must wrap both or the assertion silently narrows to the second | HIGH |
| **M-45** | `USECompilerUncertaintyTest.java:61` | save/restore `Options.explicitVariableDeclarations` in `@BeforeEach`/`@AfterEach` | today every test running **after** this one in the same JVM sees `false`. Restoring changes what those tests see — **this is exactly the coupling that makes CF-5 load-bearing** | HIGH — **B12** |
| **M-48b** | `USECompilerUncertaintyTest.java:26-29` | **do NOT convert `ExpressionTest` to a `record`** | a record changes `toString()` from the `Object` identity form to a component listing, and **that string is interpolated into the assertion message at `:90`** | MEDIUM |
| **M-49b** | `USECompilerUncertaintyTest.java:88` | fix the regex `"\n(\r\n)"` | it effectively never matches, so `errArray.length - 1 == 0` and `errMessage` is the **entire** captured stderr with `\n`/`\r` stripped. Fixing it changes which line is compared against the `-> …` expectation | HIGH |
| **M-51** | `USECompilerUncertaintyTest.java:99-101` | `throw new UncheckedIOException(msg, ex)` | changes the exception type and message the runner reports. **A preserving variant exists**: keep `RuntimeException` and pass `ex` as the cause — type and message unchanged, only the stack trace grows | LOW |

## 7.3 Float-sensitivity summary — read before touching any number

The 16 `F-*` sites, grouped by what rides on them:

1. **Value equality** — F-1 (URealValue, round-10 then `==`), F-4 (UReal vs Real/Integer, raw `==`),
   F-6 (UBoolean probability, round-10 then `==`).
2. **Hashing / collection membership** — F-3 (equals/hashCode mismatch), F-10 (hash collapses to 0
   whenever `u == 0.0`).
3. **Singleton selection** — F-7 (UBoolean at exact 0/1), F-11 (SBoolean at exact 1/0/0/1).
4. **Control flow and operand evaluation** — F-14 (`and`/`or`/`implies` short-circuit at exact 0/1 —
   decides **whether the right operand is evaluated at all**), F-13 (`toBooleanC` threshold),
   F-12 (printed form flips at 0.5).
5. **Arithmetic identities the tests depend on** — F-8 (`1 - 0.2 == 0.8` is exactly true, measured),
   F-5 (`-0.0 == 0` is true, used to suppress `"-0.0"` in output).
6. **Precision loss forced by the oracle jar** — F-15 (`UReal.power(float)`; no `power(double)` exists).
7. **Latent overflow** — F-2 (saturation above `9.223372036854776e8`, uncovered by any test).
8. **Error signalling** — F-16 (`isNaN`/`isInfinite` → `ArithmeticException`).

**`F-2`, `F-3`, `F-4`, `F-10` are defects whose repair is a behaviour change. The other twelve are
behaviour that must be carried across byte-identically.** The single highest-leverage precondition
is **F-1**: `MathUtil.round(double,int)` **does not exist in the target** and must be added verbatim.

## 7.4 Do-not-touch list for the Port role

Copy across unchanged, defects included — each has a ledger row explaining why:
`URealValue.hashCode` (F-3), `URealValue.compareTo` dead branch (M-3),
`UIntegerValue.hashCode` (F-10), `UIntegerValue.compareTo` un-negated delegation (M-9),
`UIntegerValue.equals(URealValue)` arm (M-10), `UStringValue.equals` (M-11),
`UStringValue.compareTo` (M-12), `UBooleanValue.equals` dead disjunct (M-8),
`SBooleanValue.compareTo` returning 0 (M-18), `ExpDefSBoolean`'s inverted guard (M-27) and missing
`ctx.exit` (M-26), `ExpConstUBoolean`'s missing `value.isUndefined()` check (M-29),
`ExpConstUString`'s unguarded parse (M-30), `ASTURealLiteral`'s double `gen()` (M-32),
`Op_uInteger_value`'s result-type mismatch (M-37),
`Op_uBoolean_or`'s NPE on `Undefined or Undefined` (M-38).

Plus, from §2 and §1, three more that carry no ledger id but are equally load-bearing:
`UInteger sqrt`'s **guard lines 382–386 transcribed verbatim** (§2.3 refuter R.1 — a plausible
"repair" deletes the only live trap), `SBoolean deduceY`'s **eight sequential `if`s in source
order** (§2.5 refuter R3), and `URealValue.toUInteger`'s **truncation toward zero** rather than the
library's floor-and-inflate (§2.2 #10).

**Tier-3 fix list, with observability.** If the acceptance criterion is "reproduce the fork's test
results", these are **unobserved by `FT/uml/ocl/value/`** (which contains only `AllTests`,
`UBooleanValueTest`, `UIntegerValueTest`, `URealValueTest`, `ValueTest`) and so are *safe* to fix:
M-11, M-12, M-18, M-10, M-9, F-3-adjacent `URealValue.compareTo(UIntegerValue)`, M-8, the
`compareTo`-ignores-`UndefinedValue` family, and the `CollectionValue` NPE/CCE on `SBooleanValue`
elements (§8.2). **F-10 is likewise unobserved** (no hash-bridge test exists). That does **not** make
them free — every one of them can change `Set{…}` printed contents, which the **corpus** does assert.

---

# 8. Open questions — answered

## 8.1 `ExpDefSBoolean` — is it needed?

> **VERDICT: No. It is dead code, and it is dead on two independent grounds. Do not port it.
> (B10.)**

### Ground 1 — nothing constructs it, because its AST node is orphaned

Complete reference set for the class and its visitor hook (`grep -rn "ExpDefSBoolean\|visitDefSBoolean" .`
over the fork, excluding `lib/`) — **8 hits in 6 files**:

| Hit | Kind |
|---|---|
| `F/uml/ocl/expr/ExpDefSBoolean.java:8,12,46` | the class itself |
| `F/uml/ocl/expr/ExpressionVisitor.java:40` | interface declaration |
| `F/uml/ocl/expr/ExpressionPrintVisitor.java:190` | visitor impl (`writer.write(literal(exp.toString(), exp))`) |
| `F/analysis/coverage/AbstractCoverageVisitor.java:113` | visitor impl, **empty body** |
| `F/analysis/metrics/AbstractMetricVisitor.java:121` | visitor impl, **fork-only package that does not exist in 7.5.0** |
| `F/parser/ocl/ASTSBooleanDefExpression.java:5,25` | the **only** `new ExpDefSBoolean(...)` |

And `grep -rn "ASTSBooleanDefExpression" .` returns **only its own declaration (`:11`) and
constructor (`:15`)**. Nothing else. No grammar, no generated parser, no test.

### Ground 1b — the surface syntax it would implement does not exist in any grammar

`ExpDefSBoolean.toString` prints `SBoolean(<one expr>)`. Every grammar in the fork defines exactly
**one** `SBoolean(...)` production and it is the **four-argument literal**, routing to
`ASTSBooleanLiteral` → `ExpConstSBoolean`:

```
F/parser/base/OCLBase.gpart:499-500   'SBoolean' LPAREN additive COMMA additive COMMA additive COMMA additive RPAREN
                                       { $n = new ASTSBooleanLiteral($ubve.n, $udve.n, $uuve.n, $uave.n); }
```

copied verbatim into all six generated grammars (`OCL.g:564`, `USE.g:1073`, `Soil.g:1109`,
`Generator.g:1340`, `ShellCommand.g:862`, `TestSuite.g:671`). In the generated parser only the
literal survives (`OCLParser.java:3258`); **there is no `ASTSBooleanDefExpression` in any generated
parser**, and the `.tokens` files carry only the one `SBoolean` token, consumed by the 4-arg rule.

Contrast: the analogous one-argument coercions for the four U-types **do not exist as `ExpDef*`
classes at all**. `ExpDefSBoolean` is the **sole `ExpDef*` in the whole expression package** — a
one-off with no peers and no callers.

### Ground 2 — even if it were reachable, it cannot work

**(a) The type guard is inverted.** `F/uml/ocl/expr/ExpDefSBoolean.java:15-16` (**corrected** from
`:16-17`; §7.2 M-27 already had it right — audit-02 M8):

```java
if (eBool.type().isKindOfUBoolean(Type.VoidHandling.EXCLUDE_VOID))
    throw new RuntimeException("Expression Boolean or UBoolean expected");
```

`BooleanType.isKindOfUBoolean` returns `true` (`F/uml/ocl/type/BooleanType.java:49-52`) and
`UBooleanType.isKindOfUBoolean` returns `true` (`:17-20`); `TypeImpl` returns `false` for everything
else. **The constructor throws precisely when the argument *is* one of the two inputs its own error
message says it expects, and accepts Integer, String, objects and collections.** The condition is
missing a `!`.

**(b) `eval` never calls `ctx.exit`.** `ctx.enter(this)` at `:25`, `return` at `:28`, no matching
`ctx.exit(this, res)`. Every other expression class in **both** trees pairs them
(cf. `T/uml/ocl/expr/ExpConstReal.java:47-52`, `ExpConstSBoolean.java:38-63`). Reachable use would
leave the eval-tree / `EvalContext` stack unbalanced — which would have surfaced immediately in any
evaluation-tree output. **It never did, because the class never ran.**

**(c) `eval` can return Java `null`.** Because of (a) the only arguments that reach `eval` are
non-boolean, and `SBooleanValue.valueOf` returns `null` for those
(`F/uml/ocl/value/SBooleanValue.java:71-88` handles only `isSBoolean`/`isUBoolean`/`isBoolean` and
falls through to `ret = null`). A `null` `Value` NPEs in the caller.

### The question as posed

> *is it reachable from UBoolean/UReal/UInteger/UString behaviour, or only from SBoolean?*

**Neither. It is reachable from nothing.** Its only inbound edge is itself unreachable and no grammar
emits the surface syntax it implements. Zero references from `ExpConstUBoolean`, `ExpConstUReal`,
`ExpConstUInteger`, `ExpConstUString`, `ExpQuery`, `ExpUSelect`, `ExpUSelectC`, or any value/type
class. Its *semantic* affinity is to SBoolean (it produces `SBooleanValue`, types as
`TypeFactory.mkSBoolean()`), so it is on the SBoolean side of the line — but the question is moot.

### Recommendation

**Drop `ExpDefSBoolean`, `ASTSBooleanDefExpression`, and the `ExpressionVisitor.visitDefSBoolean`
hook.** Nothing in the fork observes their absence, and dropping saves a method in **every**
`ExpressionVisitor` implementor (§4.3) for zero reachable behaviour: the interface gains **7**
methods, not 8. If the port plan insists on bit-fidelity, port it **with the three defects documented
in a class javadoc** — but record the decision explicitly either way.

**Residual gap:** whether some artefact *outside* `F/src` (a `.use` model, a shell script, a
downstream tool) references a 1-argument `SBoolean(x)` is **UNVERIFIABLE** from source alone; the
grep covers `F/src` only. **The grammar evidence makes it moot** — no parser in the fork accepts that
form, so no `.use` file could ever have used it.

## 8.2 The SBoolean scope assumption

> **VERDICT: the plan's assumption is HALF WRONG, and the wrong half is dangerous. (B2.)**

**Correct half.** None of the four U-types' *value* classes and none of their *operation registries*
mention SBoolean. No U-type operation returns, accepts, or constructs an `SBooleanValue`.
`StandardOperationsSBoolean.java` (1502 lines) is genuinely severable.

```bash
grep -n "SBoolean" StandardOperationsU{Boolean,Real,Integer,String}.java     # no output
grep -rn "import uDataTypes.SBoolean;" --include=*.java F/                   # one file: value/SBooleanValue.java
grep -rn "StandardOperationsSBoolean" --include=*.java F/
#   OpGeneric.java:97  +  the declaration.  Delete line 97 and the whole 1502-line registry is unreferenced.
```

All four U-types' `uEquals` **return `UBooleanValue`, never `SBooleanValue`**, despite the declared
return type being the abstract `UncertainBooleanValue`: `URealValue:147`, `UIntegerValue:41`
(delegates to `URealValue`), `UStringValue:58,60`, `UBooleanValue:276`. And the reverse direction is
closed too — `UBooleanValue.valueOf(Value)` accepts only `isUBoolean()`/`isBoolean()` (an
`SBooleanValue` yields `null`), and `UBooleanValue.uEquals` guards on
`other.type().isKindOfUBoolean(EXCLUDE_VOID)`, which `SBooleanType` **does not override** ⇒ `false`.
**`SBooleanValue` cannot enter the UBoolean value path.**

**Wrong half — the dependency runs in the *other* direction and it is real.**

**(A) Type lattice.** `UBooleanType` and `BooleanType` each declare SBoolean a **supertype** and each
answer `isKindOfSBoolean() == true`:

```
F/uml/ocl/type/UBooleanType.java:22-25  isKindOfSBoolean -> true
                              :33-39   allSupertypes -> { UBoolean, OclAny, SBoolean }
                              :41-44   conformsTo -> … || other.isTypeOfSBoolean()
F/uml/ocl/type/BooleanType.java:54-57   isKindOfSBoolean -> true
                              :63-64   conformsTo -> … || other.isTypeOfSBoolean()
                              :70-78   allSupertypes -> { OclAny, UBoolean, SBoolean, Boolean }
```

`URealType`, `UIntegerType`, `UStringType` do **not** override it — **the leak is confined to the
boolean family.**

*Does this corrupt LCS? No.* `LCS(Boolean, UBoolean)`: intersection `{OclAny, UBoolean, SBoolean}`;
`UBoolean` conforms to all three while `SBoolean` does not conform to `UBoolean`, so `UBoolean` wins.
**Removing SBoolean from these two `allSupertypes()` sets changes no LCS result among
Boolean/UBoolean.** That is the escape hatch for a narrow port.

**(B) `=` and `<>` name `SBooleanType` explicitly.** `Op_equal.matches:48-68` and
`Op_notequal.matches:197-217` both compute
`someOfThemIsSBooleanValue = params[1] instanceof SBooleanType || params[0] instanceof SBooleanType`
and return `mkSBoolean()` when it holds, else `mkUBoolean()`. This is a **guarded** dependency —
with no SBoolean literals and no SBoolean-typed model features the branch never fires — but
`SBooleanType` is a **compile-time** dependency of `StandardOperationsAny` (import at `:5`). A narrow
port must either keep `SBooleanType` as a class or strip these four lines.

**(C) THE ACTUAL PROBLEM — 21 SBoolean operations are callable on `UBoolean`/`Boolean` receivers.**

```bash
grep -c "isTypeOfSBoolean" StandardOperationsSBoolean.java   # 18  (strict: SBoolean only -- SAFE)
grep -c "isKindOfSBoolean" StandardOperationsSBoolean.java   # 45  = 28 live + 17 in the commented-out block
```

The strict ones are safe (`UBooleanType` inherits `isTypeOfSBoolean → false`): `projection`,
`belief`, `disbelief`, `baseRate`, `uncertainty`, `not`, `toUBoolean`, `toString`, the `is*`
predicates. The **loose** ones match `UBooleanType` and `BooleanType`. Of those, `and`, `or`, `xor`,
`implies` and `equivalent` are **shadowed** by `StandardOperationsUBoolean`, which registers first
(§2.6) — good. **But these 21 have no boolean-side competitor at all:**

`projectiveDistance`, `conjunctiveCertainty`, `degreeOfConflict`, `deduceY`, `discount`, `applyOn`,
the nine `*Fusion` operations, **and `min`/`max`**.

`min`/`max` are the sharpest case, because they *look* shadowed by `StandardOperationsNumber` (which
registers earlier) but are not: `Op_number_min` matches only number types, so a
`(UBoolean, UBoolean)` or `(Boolean, Boolean)` argument list **falls through it** and reaches
`StandardOperationsSBoolean`'s `min` (`:1202-1203`), which returns `mkSBoolean()`. Its `eval` then
calls `SBooleanValue.valueOf(args[0])`, which **deliberately coerces**: `isUBoolean` →
`new SBooleanValue(new SBoolean(ub.getuBoolean()))`; `isBoolean` → `TRUE`/`FALSE`.

> **DERIVED** (chain: `UBooleanType.isKindOfSBoolean` → true ⇒ `matches:1202` returns `mkSBoolean()`
> ⇒ `ExpStdOp.create` returns that op ⇒ `eval` coerces via `valueOf:76-78`):
> **in the fork, `UBoolean(true,0.7).min(UBoolean(true,0.3))` and even `true.min(false)` are legal
> OCL evaluating to an `SBoolean`.** Likewise
> `UBoolean(true,0.7).minimumBeliefFusion(UBoolean(true,0.3))`.
> This is a **reading-level derivation** — the fork was not executed (Ant/Java-1.7, Maven off-limits).
> Every link is cited and can be re-checked by reading those five sites.

### What the tests say

```bash
grep -rIl "SBoolean" . | grep -v "^./src/main" | grep -v "^./lib"
#   src/test/org/tzi/use/uml/ocl/type/TypeTest.java     <- ONE file
grep -rn "SBoolean" FT/parser/uncertainty/              # no output
```

One test file, exercising **only the type lattice**, never a value or an operation:
`FT/uml/ocl/type/TypeTest.java:103-108` (`SBoolean < OclAny`, `SBoolean < SBoolean`), `:111-112`
(`UBoolean < SBoolean`), `:123-124` (`Boolean < SBoolean`), `:210-216`
(`SBoolean.allSupertypes()`), `:223`, `:235`, `:286`, `:401-403`, and negatives at `:466`, `:493`,
`:516`. **No `SBooleanValueTest`, no `SBooleanExpOpsTest`, no `SBooleanExpression.in`. Zero of the
1502 lines of `StandardOperationsSBoolean` is covered by any test in the fork.**

That, plus the 21 leaked operations, plus the two hard defects in `ExpDefSBoolean` (§8.1), is a
consistent picture: **the SBoolean sub-feature was written and never exercised.**

### The three coherent options

| Option | Scope | Cost | Consequence |
|---|---|---|---|
| **1 — full omission** | drop `SBooleanType`, `SBooleanValue`, `StandardOperationsSBoolean`, `ExpConstSBoolean`, `ASTSBooleanLiteral`, `ExpDefSBoolean`, `ASTSBooleanDefExpression`, the `'SBoolean'` grammar alternatives, and the `"SBoolean"` `buildInTypesMap` entry. **Then also** remove `isKindOfSBoolean`/`isTypeOfSBoolean` from `Type`/`TypeImpl`/`MClassifierImpl`/`VoidType`, the SBoolean clauses in `UBooleanType` (`:22-25,:37,:44`) and `BooleanType` (`:54-57,:64,:75`), and the four lines in `StandardOperationsAny` (`:50,:58-59,:200,:208-209`) | cheapest overall; **also collapses `UncertainBooleanType` and `UncertainBooleanValue`** (§3.5), two more files deleted with no behaviour lost | **A deliberate, documentable behaviour change.** Per (A) no LCS result changes; per (C) the only observable losses are the 21 leaked operation names, which no corpus exercises. **But it breaks `FT/uml/ocl/type/TypeTest.java:111-112,123-124`** |
| **2 — skeleton retention** ~~⭐~~ **(recommended; NOT taken)** | keep `SBooleanType` (a ~35-line class) so the lattice and `StandardOperationsAny` port verbatim; skip `SBooleanValue` and the 1502-line registry | cheap; preserves the type-conformance semantics `TypeTest` asserts | **removes the (C) leak automatically**, because the leak lives entirely in the registry. `UncertainBooleanType` survives (8 lines); `UncertainBooleanValue` still collapses if `SBooleanValue` is not ported |
| **3 — full port** ✅ **DECIDED 2026-08-17 (B2) — THIS IS THE PLAN** | everything: all **39** operations | 1502 lines of untested subjective-logic arithmetic + `deduceY`'s six unguarded divisors and non-partitioning `if`s (§2.5), **plus** `SBooleanValue` marshalling in the differential harness (the new hard prerequisite — without it all 39 operations are `UNSUPPORTED` and the port has no evidence of any kind for them) | ~~only if a thesis result depends on subjective-logic operators. **Nothing in the corpora suggests it does**~~ — the corpus facts are unchanged and were not the reason: the user chose the full port anyway. Scope, the ten work items and `consensusAndCompromiseFusion`'s `O(4ⁿ)` hazard: **`b7-fix-plan.md` §6** |

~~**Recommendation: option 2.**~~ **DECIDED 2026-08-17: option 3, FULL PORT (B2). The recommendation
was option 2 and it was NOT taken** — and note the record disagreed with itself about *which* option was
recommended: this section and §0.0 say option 2, while `19-open-questions.md` Q2 (`:402`) says option 1,
"full omission (recommended)". **Neither was taken.** (Static-review defect **D-05**.) Whichever had been
chosen, the one thing that must be a **recorded decision and not an oversight** is whether
`isKindOfSBoolean` survives on `UBooleanType`/`BooleanType` — it is the single place where a narrow port
is observably not the fork. **Under B2 it survives**, and the removal lists in option 1 above are
therefore **not** to be executed.

**UNVERIFIABLE:** whether the fork's authors *intended* `isKindOfSBoolean` on `UBooleanType` as a
deliberate "UBoolean is a degenerate SBoolean" subsumption, or whether it is a copy-paste of the
`isKindOfUBoolean` block. Nothing in the tree settles intent. `FT/uml/ocl/type/TypeTest.java:111-112` asserts the
conformance holds, so it was at least *noticed*; it does not show the operation-level consequence
was noticed.

## 8.3 `UncertainBoolean*` vs `UBoolean*`

> **VERDICT: it is not two parallel boolean-ish types. It is a two-level hierarchy whose upper half
> is an abstract base with no surface existence. The smell is real but it is a different smell: the
> abstraction exists *solely* so that `UBoolean` and `SBoolean` can be siblings.**

```
Type   → BasicType → UncertainType(abstract) → UncertainBooleanType(abstract) → UBooleanType | SBooleanType
Value  →             UncertainValue(abstract) → UncertainBooleanValue(abstract) → UBooleanValue | SBooleanValue(final)
```

`UncertainBooleanType.java` is **eight lines** — a constructor and nothing else.
`UncertainBooleanValue.java` is a constructor plus one abstract method, `not()`.

**Which is which:**

* `UBooleanType`/`UBooleanValue` are the **concrete, user-visible** type — a `(boolean, confidence)`
  pair wrapping `uDataTypes.UBoolean`.
* `UncertainBooleanType`/`UncertainBooleanValue` are **abstract plumbing**, never instantiated,
  never named in surface syntax.

**Proof of no surface existence.** The complete built-in registry
(`F/uml/ocl/type/TypeFactory.java:58-70`) is `Integer, UInteger, UnlimitedNatural, String, UString, SBoolean,
UBoolean, Boolean, UReal, Real, OclAny, OclVoid` — **no `UncertainBoolean`**. No grammar mentions it
either (`F/parser/base/OCLBase.gpart:633` lists exactly `UReal|UInteger|UBoolean|UString|SBoolean`). **It is
unwritable in OCL, and necessarily has zero corpus occurrences.**

**Proof of no other consumer.** `grep -rn "UncertainBooleanType" --include=*.java F/ | grep -v
"type/UncertainBooleanType.java"` returns exactly **two `extends` clauses**. No
`instanceof UncertainBooleanType` anywhere. (Contrast its parent `UncertainType`, which **is** a real
discriminator at 11 sites — §3.5.)

`UncertainBooleanValue` earns slightly more of its keep: it is the declared type at **nine** sites,
all of them "the polymorphic result of comparing two uncertain values" — `UncertainValue.java:28,37-39`,
`URealValue:137`, `UIntegerValue:39`, `UStringValue:54`, `UBooleanValue:275,317`,
`SBooleanValue:100,112,221`, `CollectionValue:156`, `StandardOperationsAny:97,232`. **But every one
receives a `UBooleanValue` in practice** unless the receiver is an `SBooleanValue`.
**`SBooleanValue.uEquals` (`:100-109`, returning `valueOf(sBoolean.equivalent(...))`) is the sole
implementation returning anything else — that single override is the entire reason the abstract
return type exists.**

**What the corpora exercise:** `UBoolean` heavily (`UBooleanExpression.in`, 400 lines, 155 literal
occurrences, plus `UBooleanValueTest`); `SBoolean` **zero**; `UncertainBoolean` **zero, and
necessarily zero**.

### Does the port need both?

**It needs `UBoolean*`. It needs `UncertainBoolean*` only as a consequence of the SBoolean decision
(B2).**

| B2 | `UncertainBooleanType` | `UncertainBooleanValue` |
|---|---|---|
| **1 — full omission** | **collapses** — `UBooleanType extends UncertainType` directly | **collapses** — `UncertainValue.uEquals`/`uDistinct` re-type to `UBooleanValue`, and abstract `not()` moves onto `UBooleanValue` where its only implementation already lives. **Two files deleted, no behaviour lost** — the corpora cannot observe the difference, since they never produce a non-UBoolean `UncertainBooleanValue` |
| **2 — skeleton** | **keep** (8 lines) so the type hierarchy matches the fork | **still collapses**, because with `SBooleanValue` unported the abstract `not()` has one implementor |
| **3 — full port** | keep | keep |

Either way, **the "two parallel boolean-ish types" reading is wrong**: there is one concrete
boolean-ish uncertainty type the thesis cares about (`UBoolean`), one it does not (`SBoolean`), and
an abstract parent that exists only to join them. **Keep the parent iff you keep the second child.**

## 8.4 Cross-cutting recommendation

Q1, Q2 and Q3 all point the same way and **are best decided together, not separately**: SBoolean and
everything that exists only to accommodate it — `ExpDefSBoolean`, `ASTSBooleanDefExpression`,
`UncertainBooleanValue`, and (if you go all the way) `UncertainBooleanType` — **is one severable
unit**. The only thing that survives severing is the question of whether to keep `isKindOfSBoolean`
on `UBooleanType`/`BooleanType` for lattice fidelity with `FT/uml/ocl/type/TypeTest.java:111-112,123-124`.

---

# 9. Blocking decisions — consolidated action list

The standalone table with full evidence is **§0**, at the top of this document. This section is the
numbered action list with where each is argued in place. **Nothing in S3 should start until all
twelve have an owner and a recorded answer** — ***four now have one.*** **B3, B7, B2 and H14 were
decided by the user on 2026-08-17** and their rows below carry a `DECIDED` marker; the remaining
eight are still open. §0.0 is the authoritative record.

> **AMENDED 2026-08-17 (decision B3, and static-review defect D-04). The paragraph this replaces
> gave ONE acceptance command and the superseded 143 baseline; it is kept here as history because a
> stage worked from §9 and would have run the wrong gate:**
>
> > ~~"The acceptance gate for every one of these is `mvn -B verify -Djava.awt.headless=true`
> > (baseline **143** = 13 surefire + 130 failsafe), not `mvn test`."~~ — **superseded. It
> > contradicted C1's own amendment 2 500 lines above it, and D-04 (`upstream-oracle-static-review.md`
> > :239-256) found it standing unamended.**
>
> **There are TWO acceptance gate commands, and a stage is not accepted until both are green:**
>
> ```bash
> scripts/upstream-oracle-gate.sh    # THE gate (round 11, F-02); it runs both of these:
> mvn -q clean && mvn -B verify -Djava.awt.headless=true                     # 11 classes / 211 methods
> mvn -q clean && mvn -B verify -Pupstream-oracle -Djava.awt.headless=true   # 51 classes / 498 methods
> ```
>
> Neither is `mvn test`. Both are **floor-checked by the build itself** —
> `scripts/UpstreamOracleFloor.java`, bound in `use-core/pom.xml` and `use-gui/pom.xml` at phase
> `verify` — so a run that collects fewer tests than the pinned per-module, per-tier floor **fails**
> instead of printing a green `BUILD SUCCESS` over a shrunken suite. Requesting `-Pupstream-oracle`
> and collecting default-build counts is an error, not a pass. See `upstream-oracle-profile.md` §5.
>
> C2's warning is unchanged in substance but its population has moved: on the **default** command a
> green `verify` is still carried by a handful of assertion-bearing methods plus the 130 failsafe
> tests, which is exactly why the second command exists — under it, at most 266 assertion-bearing
> **upstream-authored** methods are live (`upstream-oracle-profile.md` §4.6 caveat 1). Per C3 the
> differential harness cannot observe `org.tzi.use.uml.ocl.type.*` or `uDataTypes.*`, so decisions
> **1**, **2**, **5**, **8** and **11** below can never be closed by a differential sweep — the
> upstream oracle, not the harness, is what covers the type layer.

| # | One-line decision | Argued in place | Recommendation |
|---|---|---|---|
| **1** | How does `uDataTypes` reach the **product** classpath? | §4.6 | **A2** — vendor the 18 MIT-licensed 2023 source files, relocated to `org.tzi.use.uncertainty.udatatypes`; keep the oracle side on the already-committed jar |
| **1a** | …and delete §15's refuted classloader justification from the record | §4.6, §0 B1a | keep A2 on **defence-in-depth** grounds, not on the refuted premise |
| **2** | SBoolean scope: omit / skeleton / full port? | §8.2 | **DECIDED 2026-08-17 (B2) — option 3, FULL PORT of `SBoolean`, all 39 operations.** The recommendation was ~~**skeleton (option 2)**~~ and it was **NOT taken**; `19-open-questions.md` Q2 recommended option 1 and is also superseded. `isKindOfSBoolean` survives on `UBooleanType`/`BooleanType`. The full-port scope, its 10 work items and the new hard prerequisite (`SBooleanValue` marshalling in the harness, without which all 39 operations are `UNSUPPORTED`) are in **`b7-fix-plan.md` §6**, which supersedes §8.2's costing |
| **3** | `junit-vintage-engine` in the product build, in a profile, or not at all? | §0 B3; C2; `stage-00-baseline.md` §3–§4; **`upstream-oracle-profile.md`** | **DECIDED 2026-08-17 (B3) — recommendation `(b)` TAKEN and BUILT:** a `-Pupstream-oracle` profile, run as part of every stage's acceptance, **on top of** `mvn -B verify`. Without it the S3–S7 unit-level gate is 13 surefire methods of which **12 contain no assertion at all** (`.evaluate()`, not `.check()` — 11 pass while the cycle report reads `Cycle count: 55`); the one that can fail is `ModelAPITest`. With it, **51 distinct classes / 498 distinct methods, 0 failures, 0 errors, 0 skipped** — *measured*, and the figure to quote (**465** of them can fail; the six ArchUnit classes assert nothing — `harness-contract.md` §0.1, defect F-04. Was 50 / 497 before round 11 added the one test method that makes the gate unsilenceable; the gate itself is `scripts/upstream-oracle-gate.sh`, not a hand-typed `-P`). The ~~45 classes / 315 methods~~ recorded here was a throwaway probe at `8789e035`, superseded (`upstream-oracle-profile.md` §4.3 reconciles both deltas). Never quote surefire's headline as a method count under the profile: it counts method **executions** and the 14 JUnit-3 `AllTests` aggregators inflate it to 1086. The counts are **asserted by the build** — `scripts/UpstreamOracleFloor.java` |
| **4** | Ship `'equals'` as a keyword, predicate it, or drop `identicalExpression`? | §5.5 | **drop it (1)**, else **predicate it (2)**. **Not (3)** — three upstream fixtures break |
| **5** | Adopt the fork's lattice and accept the `testSupertype` breakage, or keep uncertain types out of the crisp supertype closure? | §3.2, §6.3 | **adopt the lattice (1)** — option 2 breaks `getLeastCommonSupertype`, which drives overload resolution |
| **6** | `Undefined` vs `null`: normalise in the harness, rewrite 79 corpus lines, or revert `UndefinedValue`? | §4.1 | **normalise in the harness (1)** and record that "port prints `null` where the oracle prints `Undefined`" is a correct port |
| **7** | Bug-for-bug or fix, across the **33** BEHAVIOUR-CHANGING rows? | §7.2; **`b7-fix-plan.md`** | **DECIDED 2026-08-17 (B7) — FIX the historical defects, documenting each.** The recorded recommendation was ~~bug-for-bug faithfulness~~ (and, in this row, ~~"decide **one policy first**, then per-row"~~); it was **NOT taken**. The policy IS decided and it is *fix*. The per-row triage of all 33 rows — the fix, the stage that owns it, and the observable class of the change — is **`b7-fix-plan.md`**, which supersedes every `DEFER` in `spec-parts/16-modernization-ledger.md`. The `CRITICAL` five are CF-7, CF-8, M-11, F-10, M-38 |
| **8** | How to stop 7.5.0's `Op_number_sqrt`/`Op_number_pow` shadowing the uncertainty ops? | §2.6, §2.2 | **tighten `Op_number_sqrt.matches` to exclude `UncertainType` (1)** — reordering registration retypes `Integer + Integer` |
| **9** | `exists`/`forAll` over uncertain predicates: take `ExpQuery` items 7+8 or neither? | §1.6 E5/E21/E22 | decide explicitly. **Do not ship the "purely additive" middle** — it adds `assertKindOfUBoolean()` and never calls it, silently unporting a behaviour `ExpQueryUncertaintyTest#testForAllColA` pins |
| **10** | Port or drop `ExpDefSBoolean` + `ASTSBooleanDefExpression`? | §8.1 | **drop** — the `ExpressionVisitor` gains **7** methods, not 8 |
| **11** | Reproduce or fix the `UnlimitedNatural` lattice inconsistency? | §0 B11; `11-types.md` §1.8-1 | **reproduce**, plus a regression test pinning `LCS(UnlimitedNatural, UInteger) == OclAny` so the deviation is visible |
| **12** | Corpus fixture placement, the vacuous-pass guard, and the `Options` global write | §7.2 CF-5/CF-8/M-45 | move to `src/test/resources` + classpath lookup + `assertTrue(files.length > 0)`; **and** either pin suite order or make the global write self-restoring. Record that the non-empty assertion converts a vacuous pass into a failure |

**Non-blocking decisions the port may take, but must record:** `mkUReal()` return type;
`UIntegerType()` constructor visibility (and note `URealType()` is `protected`, not package-private);
the `this` vs `TypeFactory.mkX()` `allSupertypes` idiom; the fork's `Booloean` test-method typos;
whether to add an `isKindOfUncertainType()` predicate replacing the 11 `instanceof UncertainType`
sites (a **deviation** from the fork — the historical oracle does not require it); and `M-35`
(`matches(Type params[])` → `matches(Type[] params)`), which 7.5.0's own `OpGeneric` does not do.

---

# 10. Residual risk — every `UNVERIFIABLE`, collected

**There are many and they matter.** Nothing below was quietly dropped. Rows marked **CLOSED** were
opened by one pass and closed by a later refutation; they are kept so the closure is auditable.
Rows marked **RE-OPENED** were stated as fact by one pass and refuted by another.

## 10.1 The three that undercut whole categories of evidence

| # | Risk | Why it is category-wide |
|---|---|---|
| **R0a** | **The differential harness cannot reach the type layer or `uDataTypes` — see C3.** `HistoricalOracle.VALUE_PKG` is the only addressable package (`TT/uncertainty/differential/HistoricalOracle.java:123`, declared at `:71-73`, `:113-122`) | Everything in §3 (lattice), §4.6 (`uDataTypes`) and §2.5 (SBoolean, all 39 operations `UNSUPPORTED`) is **structurally outside the instrument**. Blocking decisions **B1, B2, B5, B8, B11** can never be closed by a differential sweep and no later stage may report them as "differentially verified" |
| **R0b** | ~~**75 % of this document's citations are bare basenames with no tree alias** (163 of 218; audit-02 §1.4). 40+ resolve in **both** trees with different content at the cited line~~ | **CLOSED 2026-08-17 (second pass).** Every ambiguous citation now carries an alias except ten that adjacent prose pins explicitly; the 91 bare basenames that remain were each checked mechanically and every one exists in **exactly one** alias tree. Re-measured: `222 tokens = 114 aliased/full-path + 91 bare-unique + 10 bare-but-pinned-in-prose + 7 outside every alias tree`. See the citation convention note at the head of this file for the classification and the rule. Nothing here requires guessing a tree any more. Risk retained in the ledger, struck through, because a *new* bare ambiguous citation can be reintroduced by any future edit — re-run the classifier before signing off S3 |
| **R1** | **Nothing was compiled, built, or executed against the port.** Maven is off-limits per the ground rules. **Every** compile-break claim in §1.6, §4.3 and §4.5, and **every** behavioural claim about fork code in §1, §2, §5, §7 and §8, is a **static reading of sources**, not javac or JUnit output | It means "this will not compile without X" is a *prediction*, and S3's first real acceptance run is the first evidence — **both commands** (C1 as amended: `mvn -B verify -Djava.awt.headless=true` *and* `mvn -B verify -Pupstream-oracle -Djava.awt.headless=true`), each floor-checked by the build. Budget for surprises there rather than treating this document's compile analysis as verified |
| **R2** | **No historical test was ever run.** The fork is an Ant / Java-1.7 / JUnit-3 tree. **Whether all 182 methods actually passed against `lib/atenearesearchgroup.uncertainty.jar` is unknown** (G9) | Every "the fork pins X" claim means "the fork *asserts* X", not "the fork *demonstrates* X". A fork test that was already red would be indistinguishable here |
| **R3** | **`uDataTypes` internal numerics are, in places, opaque jar bytecode.** Every 10-decimal expectation in the tests is produced by that jar. **If the target build ever resolves a *different* build of `atenearesearchgroup.uncertainty.jar`, all of F-1's evidence collapses** | This is why the jar's sha256 is verified on every `open()` (`stage-01.md` §2) and why B1's obligation 2 (keep the oracle on the committed jar) is not optional |

## 10.2 Oracle-library risks (`uDataTypes`)

| # | Risk | Status |
|---|---|---|
| R4 | **Jar-vs-source drift, general.** Everything §2 says about the delegates was read from the 2023 `$UDT` sources; the fork compiles against the **2021** jar. The public API is a strict superset and 16 probed expressions were identical (§4.6), but this is **empirical, not a bytecode diff** | open; mitigated by §4.6's probe |
| R5 | **`SBoolean` numeric bodies not compared instruction-by-instruction**: `averageBeliefFusion`, `weightedBeliefFusion`, `consensusAndCompromiseFusion`, `uncertaintyMaximized`, `deduceY`, `and`, `or`, `applyOn`. The source carries visible evidence of revision in exactly those places (`averagingFusion` has an "OLD VERSION" block commented out; `uncertaintyMaximized` carries `// Replaced by another version`) | open — **if a port needs exact numeric agreement for those eight it must be established separately against the jar.** Ground rule 2 forbids putting the reference jar on a build classpath, so **no execution oracle was run for SBoolean** — only static disassembly |
| R6 | **Provenance linking the 2021 jar to any published source revision.** The 2023 tree has no VCS metadata (`find $UDT -name .git` → nothing) and the jar has no manifest. The two artefacts **cannot be linked by provenance, only by behavioural comparison** | open, permanently |
| R7 | **Licence status of the 2021 jar itself.** The MIT grant is documented only in the 2023 `README.md`; the jar carries no `META-INF` and no licence file | open — **one more reason to prefer the source over the jar (B1)** |
| R8 | **`UReal.power` with `x = 0, s = 1`** yields `UReal(0.0, u)` per the jar plus `UDT/UReal.java:157`, but **no fork oracle covers it** — neither `.in` nor `URealExpOpsTest`. A previously unrecorded gap in the highest-traffic UReal operation | open (§2.2 refuter R.1) |
| R9 | **`UInteger sqrt` on `(0, u>0)`** derived as `Undefined`; **not pinned by any oracle or unit test** | open |
| R10 | **Reference-identity branches** (`r == this` in `UInteger.divideBy`/`mod`; `this == b` in `UBoolean.and`/`or`/`implies` and `SBoolean.and`/`or`) — **whether they are reachable from OCL is unknown**. It depends on whether the evaluator can hand the *same* object to both operand positions. No fork test exercises them, and no OCL expression was found that provably yields object identity | open — determining it needs a running evaluator |
| R11 | **`Integer.MIN_VALUE` overflow in `UInteger abs`/`neg`, and `Integer.MAX_VALUE` saturation in `UInteger power`** — derived from source plus the JLS/JDK contracts; **no oracle case** | open |
| R12 | **Exponent precision loss** from the `double → float` narrowing in `UIntegerValue:184,186` and `StandardOperationsUReal:442`. **Every exponent in the corpus (`0, 3, −3, −2, 3.5, 1.5, 4, 0.25`) is exactly representable in `float`, so no test detects it** | open — real for exponents like `0.1` |
| R13 | **Whether `UInteger setUncertainty` can be reached with a NaN or infinite `Real` argument.** OCL has no NaN literal and `1.0/0.0` is trapped as `Undefined` by `Op_number_div` first; **no reaching case was constructed** | open |
| R14 | **`deduceY`: exception vs silent NaN.** Its `b`/`d`/`u` are written **directly to the protected fields**, bypassing the constructor check, so a NaN can survive into the returned `SBoolean` *without* an exception and surface only later. **Which of the two failure modes a given input takes is unknown** without executing the jar | open |

## 10.3 Reachability and behaviour risks in the fork

| # | Risk | Status |
|---|---|---|
| R15 | **`Undefined or Undefined` NPE** (§7.2 M-38) — confirmed by reading three sibling branches, one of which lacks the guard the other two have. **Not reproduced at runtime** | open |
| R16 | **`CollectionValue.uIncludes`/`uCountC` NPE and `uExcludes` `ClassCastException` on `SBooleanValue` elements.** `((UncertainValue) elemVal).uEquals(v)` yields an `SBooleanValue`; `UBooleanValue.valueOf(Value)` returns `null` for it; `aux.probability()` then throws. `uExcludes` routes it through `assertKindOfUBoolean` → CCE. **Derived by reading. Whether the fork exercises that path is unknown** — `FT/uml/ocl/expr/UCollectionExpOpTest.java` was not read for this. **Port decision needed**: widen the fold to `UncertainBooleanValue` (matching what `uExcludes` already declares) or document the restriction to UBoolean-kind elements | open — and it is a **decision**, not just a risk |
| R17 | **`SBooleanValue` under sorting.** `compareTo` returns a constant `0`, so `Collections.sort` in `CollectionValue.getSortedElements()` leaves them in insertion order and, on a mixed collection, **can** throw TimSort's `IllegalArgumentException: Comparison method violates its general contract!`. **Whether the fork ever sorts such a collection is unknown** — no such test exists | open |
| R18 | **The mixed `Set(UBoolean)` scenario** — the premise for the two declared-vs-runtime type mismatches (§2.1 #1, #14) and the unguarded `setValue` cast. The fork's own comment (`StandardOperationsUBoolean.java:64-65`) asserts it is reachable and `Op_uBoolean_toBoolean` was evidently written to handle it, but **no model that triggers it was exhibited** | open |
| R19 | **`UInteger div` in dot-call position.** `'div'` is an implicit literal token while dot-calls bind `IDENT`, so `u.div(v)` *should* fail to parse; `grep -rn '\.div('` over both test trees → nothing. **The parser was not run.** Consequence if true: the fork's pretty-printed `a.div(b)` is **not re-parseable** — a round-tripping bug, not the "cosmetic" divergence it was first called | **RE-OPENED** — §2.3 asserted "infix **and dot**" with no evidence; refuter adopted, negative marked unverifiable |
| R20 | **Whether the USE parser can emit a unary `+` on a non-numeric operand** — needed to make the `Op_uString_uConcat.matches` `ArrayIndexOutOfBoundsException` reachable end-to-end. **The ANTLR grammar was not traced.** The code path is real regardless; the port must move the length check | open |
| R21 | **Whether dot-position `X.uSelect(...)` was intended or tested.** No corpus case exercises it; `->uSelect` appears only in arrow position | open |
| R22 | **Whether a 1-argument `SBoolean(x)` is referenced by any artefact outside `F/src`** (a `.use` model, a shell script, a downstream tool). The greps cover `F/src` only | open but **moot** — none of the six ANTLR grammars has a rule producing `ASTSBooleanDefExpression`, so no parser in the fork can accept that syntax |
| R23 | **Whether `Op_uInteger_value`'s declared-type bug (`mkUInteger` vs `mkInteger`) is depended upon anywhere.** No test that would notice was found, but enclosing-expression cases were not exhaustively enumerated | open |
| R24 | **Whether the fork's authors intended `isKindOfSBoolean` on `UBooleanType`** as a deliberate subsumption or as a copy-paste of the `isKindOfUBoolean` block. Nothing in the tree — no comment, no test, no corpus — settles intent | open (B2 depends on it only for framing, not for the decision) |
| R25 | **`ASTExpression.gen(Context)` purity** (M-32). Not every `gen` implementation reachable from a UReal literal's operands was read, so "gen is not pure" is a **stated risk, not a demonstrated one** | open |
| R26 | **The full set of `catch` sites that would see a narrowed exception type** (M-6, M-51). In-scope callers and the evaluator entry points were checked; the GUI and plugin layers were not | open |
| R27 | **Missing `@Override` annotations.** The fork could not be compiled with `-Xlint:all` (that needs the ANTLR-generated sources and would write into a build directory), so there is **no authoritative list**. Spot checks found `@Override` present everywhere it was looked for. **No ledger row claims a missing `@Override`** | open |

## 10.4 Test-coverage risks

| # | Risk | Status |
|---|---|---|
| R28 | **`uCount` has no oracle anywhere** (G1) — `testUCount` asserts nothing, and no `.in` file mentions it | open — **the port has no expected value for `uCount`** |
| R29 | **`UInteger / UReal` is not tested where the method name promises** (G2). Covered by 24 corpus entries instead. **Do not count `testDivideByRWithUReal` as evidence for that overload** | closed as a *measurement*, open as a *trap* |
| R30 | **`UString` has no value test, no expression-operations test and no corpus** — 21 operations, zero behavioural oracle (§6.5). Every "verified" claim in §2.4 is a **jar execution**, not a fork expectation | open — **this is the under-evidenced type** |
| R31 | **`SBoolean` has no value test, no operations test and no corpus** — 39 operations, zero coverage (§6.5, §8.2) | open |
| R32 | **6 of `UReal`'s 18 operations (`sin`, `cos`, `tan`, `asin`, `acos`, `atan` — 33 %) have no test and no `.in` case.** Their semantics come from the library source plus a jar probe | open — **the highest-risk part of the UReal registry** |
| R33 | **Whether out-of-range `uSelectC` confidence is meant to be rejected at construction or at evaluation** (G6) — both fork tests evaluate the **inner comparison**, not the `ExpUSelectC` they just built, so they prove only that *something* throws | open |
| R34 | **Commented-out assertions never pinned any behaviour** (G5): `UBooleanValueTest:11-17,:36-42` (probability-range validation) and `URealExpOpsTest:431-433` (`UReal(0,0).sqrt()`). Whether the port should enable them depends on which `uDataTypes` build is used — **unverifiable from the tests alone**. Note §2.2 shows the `sqrt` skip **is obsolete against the binding jar**, and §2.2 shows the `power(1/2) == sqrt()` skip is too | partly **CLOSED** by the jar probe; enabling remains a decision |
| R35 | **`Integer div UInteger` and `Integer mod UInteger`** are permitted by `matches` but exercised by **no test in the fork** | open |
| R36 | **`FT/parser/USECompilerTest.java:78` points at `src/test/org/tzi/use/parser/test_expr_uncertainty.in`, which does not exist** — a dangling reference in the fork's own test harness | open (fork defect; do not port the reference) |
| R37 | **Surefire's actual discovery order on the target machine** (CF-5's risk assessment assumes the documented `filesystem` default). Maven was not run to confirm | open — **B12** |
| R38 | **Whether the fork's own test suite exercises the `visitQuery` uncertainty traversal** added to the two visitors (§1.6 E12/E13) | open |

## 10.5 Build, module and harness risks

| # | Risk | Status |
|---|---|---|
| R39 | **The exact JVM flags surefire 3.5.4 passes for the modular-main / non-modular-test split** (`--add-opens`, `--add-reads`, `--patch-module`). The surefire XML records `jdk.module.path` and `java.class.path` but **not** the argument line, and confirming it would require running Maven. **The path split is enough to establish §4.5's three rules; the precise flag set is not** | open |
| R40 | **Whether `use-gui` contains value/type dispatch** (`instanceof RealValue`, `isTypeOfBoolean()` switches) that would need widening for uncertain values to display correctly. **The fork touches no GUI file at all**, so there is no fork-side evidence either way, and auditing the GUI's own dispatch sites was out of scope | open — see also §4.3's warning about `GenerateHTMLExpressionVisitor` and `EvalNode`'s inner visitors silently rendering empty nodes |
| R41 | **The exact per-file diff counts in §4.7's table** (763 / 307 / 226 / 218 / …). The method was re-derived and confirmed to run, and the qualitative claims were confirmed for `OpGeneric`, `Value`, `Type`, `TypeImpl` and `ExpressionVisitor`, but **not all 23 rows were re-counted.** Nothing in the port depends on the exact integers | open, low consequence |
| R42 | **Whether the four `operations/` rewrites are behaviour-preserving for *non-uncertain* operands.** `Op_number_pow`/`sqrt` were confirmed to exist only on the 7.5.0 side and `Op_equal.matches` was confirmed as quoted, but **the 763-line numeric diff was not audited hunk by hunk.** The instruction to three-way-merge those three files is prudent regardless | open — **this is the largest un-audited surface in the port** |
| R43 | **One unexplained transient.** The very first full-reactor run after the S1 change failed with `TestEngine with ID 'junit-jupiter' failed to discover tests`, `Tests run: 0`. It has not recurred in 7 subsequent runs (4 with the change, 3 with the S1 files parked aside), and the `target/` evidence was destroyed by the next `clean` before it could be examined. **It could neither be attributed to that change nor ruled out** | open (`stage-01.md` §6) |
| R44 | **Exact differential comparison may prove too strict.** `UValue.canonical()` compares via `Double.toString`, so `0.0` and `-0.0` are a `DIFFER` and `NaN` equals `NaN`. That is the right default for detecting regressions, but later stages may find legitimate `-0.0` noise. **Loosening it must be an explicit, recorded decision, never a quiet epsilon** | open (`stage-01.md` §6) |
| R45 | **The historical jars are Java 7 bytecode (major 51).** Java 21 loads them today; a future JDK that drops class-file version 51 would take the oracle with it. Nothing can mitigate this — **it is a reason to finish the port rather than depend on the oracle indefinitely** | open, permanent |
| R46 | **`uTypesResolveOnlyThroughTheOracle()` asserts the U-types are *absent* from the application loader.** Correct at S1; **it will start failing the moment the port lands.** The test says so in its own failure message: it must then be **inverted** to assert distinctness, **not deleted**. `sameNameDistinctClasses()` (written against `RealValue`) is the assertion that survives unchanged | **scheduled** — an S4 action item, not an open question |
| R47 | **The S1 stub is not the port.** Every "AGREE" at S1 is agreement between the historical jars and a 40-line re-derivation of two formulas, over **two** operations. The remaining ~70 methods of the historical surface are reachable through `UOp`/`HistoricalOracle` but have **never been exercised** | **scheduled** — S2/S3 must widen this |
| R48 | **No port-plan document exists in this repository.** `docs/port2/` contains only the stage files and `spec-parts/`, and `grep -i "VoidType\|BooleanType\|IntegerType"` over `stage-00-baseline.md` returns nothing. Several "touchpoints named in the port plan" in the source sections were therefore derived from the fork↔7.5.0 diff, **not from a plan** | open — this document now *is* the plan for §1–§9 |

## 10.6 Claims that one pass asserted and another refuted — do not act on the original

| # | Original claim | Status |
|---|---|---|
| R49 | "`TypeImpl.conformsTo` supplies a `false`-returning default" (§15 §2.1/§2.2) | **REFUTED.** It is `return this.conformsTo(other);` — unbounded recursion in **both** trees. A new `*Type` that omits `conformsTo` compiles clean and `StackOverflowError`s at first use. **§1.2's mandatory box supersedes it** |
| R50 | "The differential harness loads the historical jar through a plain parent-first `URLClassLoader` (`UValue.java:13-16`)" (§15 §7.4) | **REFUTED.** That citation is Javadoc prose; the real loader already isolates `uDataTypes.` **parent-last**, and the repository has **measured** that the suggested platform-parented remedy does **not** work under JPMS. **A2 must be re-argued on defence-in-depth grounds (B1a)** |
| R51 | "`OCLLexerRules.gpart` — empty diff" (§15 §6.3) | **REFUTED as written** — `diff` reports `1,127c1,127`; the files are identical only after CRLF normalisation. **Conclusion (no new lexer token) stands** |
| R52 | "the oracle jar is not present in `use-core/src/test/resources/historical/`" (`20-ops-UReal.md` §2) | **REFUTED** — it is there, byte-identical, md5 `a3055f54205babaa27484fa94efdda1c`. **The port needs no "obtain the jar" step** |
| R53 | "`equivalent` has no competitor / registration order does not matter for it" (`20-ops-UBoolean.md` §3.14/§4.3) | **REFUTED** — `StandardOperationsSBoolean` registers a second `equivalent` whose `matches` accepts `Boolean × Boolean` and `UBoolean × UBoolean`. **The precedence chain is Boolean → UBoolean → SBoolean** |
| R54 | "the `power`/`sqrt` FIXME is caused by the `(float)` narrowing, `1.016465997955662` vs `1.4142135623730951`" (`20-ops-UReal.md` §3 #11) | **REFUTED on all three counts** — the exponent `0.5` is exact in `float`; the cause is the discarded second-order term; against the binding jar the two agree after `MathUtil.round(·,10)`. **It is a second obsolete skip, not a trap** |
| R55 | "`UInteger sqrt` line 382 is dead; line 385 is the only guard that fires" (`20-ops-UInteger.md` §4.11/§6.3) | **REFUTED** — only 382's *first* disjunct is dead; its second tests the uncertainty and fires **first** for `(0, u>0)`. **The suggested "repair" would delete the only live infinite-uncertainty trap.** Transcribe 382–386 verbatim |
| R56 | "the confidence argument is lost on print and will break any print-then-reparse test" (`12-expressions.md` §3.3.4) | **REFUTED for the print visitor** — `visitUSelectC` delegates to `visitQuery`, which the fork edits to print it. **Only `ExpQuery.toString(StringBuilder)` drops it** |
| R57 | 18 `[in:N]` corpus citations in `20-ops-UInteger.md`'s `div`/`mod` sections | **REFUTED — all 18 point at the wrong lines.** Every claimed expression+result pair exists with exactly the stated result, so **the semantics are sound**, but **re-derive every `[in:N]` with `grep -nF` before regenerating expectations** |
| R58 | "`Op_enum_toString` and `Op_sBoolean_toString`" (`20-ops-UString.md` §2.13/§3.4) | **REFUTED — both identifiers are fabricated.** The Enum one is `final class Op_toString`; the SBoolean one is an anonymous `OpGeneric` in the enum constant `TO_STRING`. The six-way `toString` collision itself is real |
| R59 | "`xor` of two vacuous SBoolean opinions gives `(0,0,1,0)`" | **REFUTED** — `a = \|a₁ − a₂\|`, so it is `(0, 0, 1, \|a₁ − a₂\|)` |
| R60 | "`deduceY`'s eight `K` cases are a case analysis selected by …" and its six-divisor list | **REFUTED** — they are **sequential `if`s that do not partition** (source order decides), the listed `(yGivenX.b − yGivenNotX.b)` is a **false positive**, and `(yGivenNotX.b − yGivenX.b)` at L381 is a **missed** zero-divisor. Two type-inconsistent numerators (L368, L374) were also unflagged |
| R61 | "§12's `ExpQuery`/visitor/`ASTQueryExpression` edits are 'purely additive' / 'add N new methods' / 'a ctor overload plus a guard'" | **REFUTED on all three** — §1.6 E5/E8/E12/E13 and **B9** carry the corrected scope |
| R62 | "`ls T/uml/ocl/value/` lists no `U*Value.java`" | **REFUTED as literally written** (`UndefinedValue`, `UnlimitedNaturalValue` match); substantive claim correct |
| R63 | "all `U*Type` sibling constructors are package-private except `UIntegerType`" | **REFUTED** — `URealType()` is `protected` |
| R64 | "`OCLBase.gpart` 678 → 708 lines" | **REFUTED** — 677 → 707. The +30 delta and the 8-hunk/+35/−8 measurement are exactly right |
| R65 | uDataTypes source-tree file counts "24 / 15", `SBoolean` javap delta "14 lines", the `divideBy` grep transcript, `OpGeneric`'s "six lines", "11 checked-in jars", and six line-number citations in §15 | **REFUTED** — 23 / 18, 11 lines, nine lines of grep output, seven lines, 13 jars, and the corrected line numbers. **Every affected conclusion stands** |

## 10.7 Deliberately not attempted

* Locating the fork's base commit or producing a base diff (out of scope per the brief).
* Reading `origin/main` or any earlier port attempt (ground rule 3). **No claim in this document
  derives from it.**
* Auditing the fork's `org/tzi/use/analysis/metrics/` package (15 files, does not exist in 7.5.0,
  unrelated to uncertainty — a separate fork feature).
* Running Maven, or putting any reference repository on a build classpath (ground rules 2 and the
  Spec role's brief).
