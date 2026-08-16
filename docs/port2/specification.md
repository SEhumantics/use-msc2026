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

---

# 0. BLOCKING DECISIONS — read this section first

Twelve decisions a human must make **before S3 starts**. Each is repeated in place with full
evidence; this list is written to stand alone. Nothing here is a judgement call the port may take
unilaterally.

| # | Decision | Options | Recommendation | Evidence | Blocks |
|---|---|---|---|---|---|
| **B1** | **How `uDataTypes` reaches the product classpath.** It is on **no** Maven repository under any coordinates (`fc:uDataTypes.UReal` → 0 hits on Central; `repo1/{es/uma/lcc/atenea,uDataTypes,atenearesearchgroup}/` → 404,404,404), has no `pom.xml`/`build.gradle` (so JitPack is out), and the 2021 jar has **no `META-INF`** hence no `Automatic-Module-Name`. | **A1** vendor 2023 MIT source keeping package `uDataTypes`; **A2** vendor relocated to `org.tzi.use.uncertainty.udatatypes`; **B** `mvn install:install-file`; **C** shade the jar; **D** reimplement | **A2**, but **on re-argued grounds** — see the correction in B1a below | §4.6; `15-upstream-delta.md` §7; `18-refutation-delta.md` F2, F4 | every `use-core` main-source compile of the 7 files that `import uDataTypes.*` |
| **B1a** | **Correction to B1's stated justification.** §15 selects A2 over A1 because "the harness uses a plain parent-first `URLClassLoader` (`UValue.java:13-16`)". That citation is **empty prose** (277 lines, no `loadClass` anywhere), and the real loader `TT/uncertainty/differential/IsolatedJarClassLoader.java:51-52,80-83` already isolates the `uDataTypes.` prefix **parent-last**, asserted by `HistoricalOracleIsolationTest.java:69-70`. The repository has also **measured** that §15's proposed alternative remedy (platform-parented `URLClassLoader`) does **not** work under JPMS here (`IsolatedJarClassLoader.java:16-26`; `stage-01.md` §3). | keep A2 on defence-in-depth grounds; or re-open A1 | keep **A2**, and delete the refuted premise from the record | `18-refutation-delta.md` F2; `stage-01.md` §3 | B1 |
| **B2** | **SBoolean scope.** No U-type *behaviour* touches SBoolean, but `UBooleanType` and `BooleanType` declare it a **supertype** and answer `isKindOfSBoolean() == true`, which drags **21 unshadowed** SBoolean operations into reach on `UBoolean`/`Boolean` receivers (`UBoolean(true,0.7).min(UBoolean(true,0.3))` is legal OCL returning an `SBoolean`). Zero of `StandardOperationsSBoolean`'s 1502 lines is covered by any fork test. | **1** full omission (also strip `isKindOfSBoolean` from `Type`/`TypeImpl`/`MClassifierImpl`/`VoidType`, the SBoolean clauses in `UBooleanType`/`BooleanType`, and 4 lines of `StandardOperationsAny`); **2** skeleton — keep `SBooleanType` only; **3** full port | **2 (skeleton)** — cheapest way to keep the fork's *type system* bit-identical (`TypeTest.java:111-112,123-124` assert it) while paying none of the 1502-line registry cost; the operation leak lives entirely in the registry | §8.2; `19-open-questions.md` Q2 | §1 rows for `SBoolean*`, §2.5, §3, §4.4 |
| **B3** | **`junit-vintage-engine`.** The 7.5.0 reactor has none, so **38 of 41** `*Test.java` never execute; baseline is 13 methods / 3 classes. A probe at `b7aaa99c` adding vintage 5.7.0 test-scope to `use-core`+`use-gui` — **no test file touched** — produced **43 classes / 300 methods, 0 failures**. Without it, "full suite green" is a near-vacuous S3–S7 gate and ground rule 4 has no automatic signal. | **(a)** in the product build; **(b)** in a `-Pupstream-oracle` profile | **(b)**, run as part of every stage's acceptance | `stage-00-baseline.md` §3–§4 | the acceptance gate of S3, S4, S5, S6, S7, S10 |
| **B4** | **The `'equals'` keyword.** `identicalExpression` (`F/parser/base/OCLBase.gpart:124-135`) makes `equals` an **implicit ANTLR token**, reserved across OCL, USE, SOIL, ASSL, TestSuite and the shell. This is a **confirmed live collision** with three upstream fixtures. | **1** drop `identicalExpression`, register `Op_identical` under a non-colliding name or reuse `=`; **2** keep the rule behind a semantic predicate on token *text* so `equals` stays `IDENT`; **3** accept the break and amend the three fixtures | **1**, else **2**. **Not 3.** | §5.5; `13-grammar.md` §13.5.2 — `use-gui/src/it/resources/testfiles/shell/t098.use:11`, `…/imports/t133_import_date.use:29`, `…/imports/t133_import_datetime.use:12` | grammar port, `StandardOperationsAny` port |
| **B5** | **`TypeTest#testSupertype` conflict.** The moment `Real ≤ UReal`, `Boolean ≤ UBoolean`, `String ≤ UString`, `Integer ≤ UInteger` enter `allSupertypes()`, **10 of the 12 assertions in upstream's own untouched `testSupertype`** become false. This cannot be fixed by moving tests to a new class — it is a lattice design question. | **1** adopt the fork's lattice and handle the upstream breakage explicitly; **2** keep uncertain types out of the crisp types' supertype closure (conformance one-way only) | **1** — option 2 breaks `getLeastCommonSupertype`, which is what drives overload resolution | §3.1, §6.3; `14-historical-tests.md` §3.3, G7 | S3 type-lattice landing, and interacts with **B3** (under (b), this failure becomes visible) |
| **B6** | **`UndefinedValue` printed form.** 7.5.0 prints `null`; the fork prints `Undefined` (upstream commit `72ab8fd7`, 2019-06-27 "changed Undefined to null"). The historical corpus contains **79 entries** expecting `-> Undefined : OclVoid`. This is a whole-suite systematic offset, not a one-off. | **1** normalise in the harness; **2** rewrite the 79 corpus lines; **3** revert `UndefinedValue` | **1** — "the port prints `null` where the oracle prints `Undefined`" is a *correct* port, not a regression | §4.1; `15-upstream-delta.md` §1(d); `14-historical-tests.md` §5 | every corpus-driven S4–S7 comparison |
| **B7** | **Bug-for-bug vs. fix: 33 BEHAVIOUR-CHANGING ledger rows.** The fork carries defects that are *unobserved* by its own tests (`UStringValue.equals` is constant `false`, breaking reflexivity; `SBooleanValue.compareTo` returns `0`; `UIntegerValue.hashCode` collapses to `0` whenever `u == 0`). Fixing any of them changes `Set`/`Bag` membership and therefore the **printed output** the `.in` fixtures assert on. | per-row: reproduce, or fix and record | decide **as one policy** first, then per-row; §7.2 lists all 33 | §7; `16-modernization-ledger.md` Tables A+B | S4–S7 fidelity verdicts |
| **B8** | **`Op_number_sqrt` / `Op_number_pow` shadowing.** 7.5.0 **added** these (`T/uml/ocl/expr/operations/StandardOperationsNumber.java:848`, `:802`, registered `:32`, `:31`); the fork has neither. Their `matches` is `isKindOfNumber(EXCLUDE_VOID)`, which `URealType`/`UIntegerType` answer `true`. Registered **before** the uncertainty registries ⇒ `UReal(4,2).sqrt()` resolves to `Op_number_sqrt`, types as `Integer`, then `ClassCastException`. | **1** tighten `Op_number_sqrt.matches` to exclude `UncertainType`; **2** register uncertainty ops first (**changes `Integer+Integer` typing — see §2.6**); **3** teach `Op_number_sqrt` about `UReal` | **1** | §2.6; `20-ops-UReal.md` §4.4 | any `sqrt`/`pow` result being trusted |
| **B9** | **`ExpQuery` items 7+8 — `exists`/`forAll` over uncertain predicates.** §12 declares its `ExpQuery` edit "purely additive" while keeping 7.5.0's `evalExistsOrForAll`. The refuter shows that then `assertKindOfUBoolean()` is **added and never called** and `exists`/`forAll` over a `UBoolean` predicate is **silently unported** — yet `ExpQueryUncertaintyTest#testForAllColA` pins `UBoolean(true, 0.999968314)`. Items 7+8 and the `ExpExists`/`ExpForAll` assertion swap are **one atomic unit: take both or neither**. | **1** take both (loses short-circuiting and the `isEnableEvalTree` fast path); **2** take neither and record `exists`/`forAll` as out of scope | decide explicitly; do **not** ship the "additive" middle | §2.4; `17-refutation-classification.md` R6; `12-expressions.md` §3.3.2 | `ExpQueryUncertaintyTest` parity |
| **B10** | **`ExpDefSBoolean` + `ASTSBooleanDefExpression`.** Unreachable dead code (sole `new ExpDefSBoolean` is at `F/parser/ocl/ASTSBooleanDefExpression.java:25`; that AST class is **never instantiated** anywhere, and no grammar produces it), with an **inverted** type guard, a missing `ctx.exit`, and an `eval` that can return Java `null`. | drop, or port with the three defects documented | **drop** — it also saves `visitDefSBoolean` in `ExpressionVisitor` and both visitors | §8.1; `12-expressions.md` §2; `19-open-questions.md` Q1 | `ExpressionVisitor` method count (7 vs 8) |
| **B11** | **`UnlimitedNatural` lattice inconsistency.** `UnlimitedNatural.conformsTo(UInteger)` and `…(UReal)` are `true` (predicate-driven, `UnlimitedNaturalType.java:61-63`, identical in 7.5.0), yet `UnlimitedNatural.allSupertypes()` was **not** extended ⇒ `UnlimitedNatural.getLeastCommonSupertype(UInteger)` returns `OclAny`. The same shape of defect **pre-exists upstream** for `Integer`/`UnlimitedNatural`. | reproduce bit-for-bit, or fix | **reproduce**, plus a regression test pinning `LCS(UnlimitedNatural, UInteger) == OclAny` so the deviation is visible | §3.4; `11-types.md` §1.8-1 | S3 lattice |
| **B12** | **Corpus harness placement and global state.** `USECompilerUncertaintyTest` resolves its four `.in` files from `System.getProperty("user.dir") + "/src/test/org/tzi/use/parser/uncertainty"` — under Maven the module root is `use-core/`, so `listFiles` returns `null`; **or**, if an empty directory exists, the loop runs zero times and the test **passes vacuously**. It also sets the process-global `Options.explicitVariableDeclarations = false` and never restores it, which is why the JUnit-3 `AllTests` **suite ordering is load-bearing**. | move fixtures to `use-core/src/test/resources/…` + classpath lookup + `assertTrue(files.length > 0)`; and either pin ordering or make the global write self-restoring | do both; note the non-empty assertion converts a previously-vacuous pass into a failure — that is itself a behaviour change and must be recorded as one | §7.2 (CF-5, CF-8, M-45); `16-modernization-ledger.md` | corpus-driven S4–S7 |

**Decisions deliberately NOT escalated** (recorded, port may take them): `mkUReal()` return type
(`Type` vs `URealType` — narrowing is source-compatible with all 27 fork call sites);
`UIntegerType()` constructor visibility (fork `public`, siblings not — note `URealType()` is
`protected`, not package-private, correcting `11-types.md:747`); the `allSupertypes` `this` vs
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
| `T/uml/ocl/value/SBooleanValue.java` | same | 476 L | **new file, the only `final` one.** Wraps `uDataTypes.SBoolean`. Both ctors **package-private**; the public cross-package entry point is the nested `Builder` (`:28-69`), used by `ExpConstSBoolean.java:49`. `Builder().build()` with no setters **throws** (`0+0+0 ≠ 1`). Note `FALSE` is `(0,1,0,a=1)` while the delegate's own `new SBoolean(false)` is `(0,1,0,a=0)` — the two "false" opinions are **not** `equals` (base rates differ by 1.0, tolerance 0.001, `UDT/SBoolean.java:1530`). Gated by **B2**. |

## 1.2 New files — `org/tzi/use/uml/ocl/type/` (7)

| Target path | Size | Contract / behaviour |
|---|---|---|
| `T/uml/ocl/type/UncertainType.java` | 16 L | **new file.** `abstract … extends BasicType`, one `protected UncertainType(String)`. **Pure `instanceof` tag** — used at 11 sites in 3 files: `StandardOperationsCollection.java:104,169,401,474`, `StandardOperationsNumber.java:351,946,1024,1101,1179`, `StandardOperationsAny.java:49,199`. There is **no** `isKindOfUncertain…()` predicate on `Type`. |
| `T/uml/ocl/type/UncertainBooleanType.java` | 10 L | **new file.** Two-line body. **Zero `instanceof` sites** — exists solely to give `UBooleanType` and `SBooleanType` a shared Java parent. Collapses if B2 = option 1. |
| `T/uml/ocl/type/UIntegerType.java` | — | **new file.** `isKindOfNumber`, `isTypeOfUInteger`, `isKindOfUReal`, `isKindOfUInteger` → `true`; `allSupertypes` = `{this, UReal, OclAny}`; `conformsTo` = `isTypeOfUInteger \|\| isTypeOfUReal \|\| isTypeOfOclAny`. Fork ctor is **`public`** (`:14`) — the only one. |
| `T/uml/ocl/type/URealType.java` | — | **new file.** `isTypeOfUReal`, `isKindOfUReal`, `isKindOfNumber` → `true`; `allSupertypes` = `{this, OclAny}`; `conformsTo` = `equals(t) \|\| t.isTypeOfOclAny()`. Ctor is **`protected`** (`:9`) — correcting `11-types.md:747`'s "all siblings package-private" (refuter R10b adopted). |
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
(`ASTURealLiteral.java:27-31` pre-checks with bespoke messages, the others translate
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
