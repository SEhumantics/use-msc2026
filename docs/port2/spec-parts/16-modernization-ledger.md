# 16 — Modernization Ledger (Modernize role: proposes, never applies)

**Status:** PROPOSAL ONLY. Nothing in this file gates anything until the fidelity verdict for the
corresponding stage is green. No file under
`/home/xoruser/msc-4/use-msc2026/.git/reference-repositories/` was modified to produce it.

**Source of truth (read-only):**
`/home/xoruser/msc-4/use-msc2026/.git/reference-repositories/uncertainty/USE-Uncertainty`
(all `file:line` references below are relative to that root unless prefixed `use-core/`).

**Target:** Java 21, Maven, JUnit 5 Jupiter, **no vintage engine**
(evidence: `use-core/pom.xml:16-17` `<maven.compiler.source>21</…>`; `use-core/pom.xml:63-64`
`org.junit.jupiter:junit-jupiter`; no `junit-vintage-engine` anywhere in either pom).

**Oracle library:** `lib/atenearesearchgroup.uncertainty.jar`, package `uDataTypes/` (given).

---

## Reading the table

| prefix | meaning |
|---|---|
| `CF-n` | **compile-forced** — will not build on the target as written. Mandatory, listed in its own table below. |
| `M-n`  | modernization proposal with no floating-point exposure |
| `F-n`  | **float-sensitive** — a comparison, an equality, a singleton selection, or a confidence value rides on binary floating point at this site |

Classification rule applied: a row is **BEHAVIOUR-PRESERVING** only where I can name the mechanism
that makes it so. Everything else is **BEHAVIOUR-CHANGING**, including cases where the "change" is a
bug fix I am recommending *against* during the fidelity port. ~~For those rows the *proposed change*
column reads **DEFER** — they are the rows a human must rule on.~~ **The human ruled on 2026-08-17 —
see the box immediately below. Every `DEFER` in this file is a record, not an instruction.**

> ## DECIDED 2026-08-17 — **B7 = FIX the historical defects, documenting each. EVERY `DEFER` BELOW IS SUPERSEDED.**
>
> **Read this before working a single row.** The human ruled. There are **29 `DEFER` occurrences** left
> in this file and **not one of them is still an instruction**: they record the state of the question
> *before* it was answered, which is why they are not deleted.
>
> * **The recommendation was bug-for-bug reproduction, and it was NOT taken.** `DEFER` meant "a human
>   must rule on this"; the human ruled **fix**, per row, with a written justification and the
>   print-output delta where one exists (`specification.md` §0 **B7**; `foundation-verdict.md` §3.0).
> * **The per-row plan is [`b7-fix-plan.md`](../b7-fix-plan.md)** — it triages all 33
>   behaviour-changing rows, names the fix, the owning stage (S3–S9) and the observable class of each
>   change, and it **supersedes the `proposed change` column of every `DEFER` row in Tables A and B**.
>   Static-review defect **D-06** (`upstream-oracle-static-review.md:272-286`) found that document
>   referenced from nowhere while `specification.md:183` still sent stages here: *"an S4–S7 stage works
>   its per-row list from `16-modernization-ledger.md` Tables A+B, reads `DEFER` on 29 rows, and
>   reproduces the defects — i.e. implements bug-for-bug, the recommendation that was explicitly not
>   taken."* That is the failure this box exists to stop.
> * **A row still marked `DEFER` here that `b7-fix-plan.md` does not cover is an open question, not a
>   licence to reproduce the defect.** Raise it; do not read silence as `DEFER`.
> * The classification columns (`BEHAVIOUR-PRESERVING` / `BEHAVIOUR-CHANGING`, `CF-`/`M-`/`F-`
>   prefixes, the float-sensitivity marking and every `file:line`) are **unaffected** and remain the
>   evidence base. Only the *advice* moved.

---

## Table A — compile-forced (MANDATORY)

| id | file:line | what the code does now | proposed change | class | evidence / what moves | risk |
|---|---|---|---|---|---|---|
| CF-1 | all 8 in-scope test files, e.g. `src/test/org/tzi/use/uml/ocl/value/URealValueTest.java:3,10`; `.../UBooleanValueTest.java:3,5`; `.../UIntegerValueTest.java:3,6`; `.../uml/ocl/expr/URealExpOpsTest.java:3,15`; `.../UIntegerExpOpsTest.java:4`; `.../UBooleanExpOpsTest.java:3`; `.../ExpQueryUncertaintyTest.java:3,10`; `.../parser/uncertainty/USECompilerUncertaintyTest.java:3,18` | `import junit.framework.TestCase;` + `extends TestCase`; the JUnit 3 runner reflects over every `public void` no-arg method whose name starts with `test` | drop the import and the superclass; annotate exactly the 122 existing `test*` methods with `@org.junit.jupiter.api.Test` | BEHAVIOUR-PRESERVING | `junit.framework` is absent from the target classpath (`use-core/pom.xml` has jupiter only). JUnit 3 discovery = `public void test*`; `grep -cE '^\s+public void test'` over the 8 files sums to **122**, so annotating exactly those 122 preserves the executed set. Both frameworks default to a **fresh instance per test method** (Jupiter `Lifecycle.PER_METHOD`), so no fixture sharing appears. | HIGH — a missed `@Test` silently deletes a test rather than failing; re-count 122 after migration |
| CF-2 | `src/test/org/tzi/use/uml/ocl/value/URealValueTest.java:8` | `import static org.junit.Assert.*;` — a JUnit 4 static import that is **entirely shadowed** by the `junit.framework.Assert` members inherited through `TestCase` | delete the import; use `org.junit.jupiter.api.Assertions` | BEHAVIOUR-PRESERVING | Direct bytecode proof: compiling `class Probe extends TestCase { … assertEquals("msg", double, double) … }` against `lib/junit.jar` emits `invokestatic assertEquals:(Ljava/lang/String;Ljava/lang/Object;Ljava/lang/Object;)V` — i.e. `junit.framework.Assert`, not `org.junit.Assert`. Inherited members shadow static imports (JLS 6.4.1). `javap -cp lib/junit.jar junit.framework.Assert` confirms there is **no** `assertEquals(String,double,double)` overload, so the call boxed to `(String,Object,Object)`. | LOW |
| CF-3 | `.../URealExpOpsTest.java:19`, `.../UBooleanExpOpsTest.java:20`, `.../UIntegerExpOpsTest.java:16`, `.../ExpQueryUncertaintyTest.java:35` | `protected void setUp() throws Exception` — JUnit 3 per-test fixture hook | rename to `@BeforeEach void setUp()` (keep `protected`; Jupiter allows it) | BEHAVIOUR-PRESERVING | JUnit 3 calls `setUp()` immediately before each test on a fresh instance; `@BeforeEach` under `PER_METHOD` does the same. | LOW |
| CF-4 | `.../ExpQueryUncertaintyTest.java:36` | `super.setUp();` | delete the line | BEHAVIOUR-PRESERVING | `javap -c -cp lib/junit.jar junit.framework.TestCase` shows `protected void setUp() … Code: 0: return` — the body is empty. | LOW |
| CF-5 | `src/test/org/tzi/use/uml/ocl/value/AllTests.java:37-44`, `src/test/org/tzi/use/uml/ocl/expr/AllTests.java:37-50`, `src/test/org/tzi/use/parser/uncertainty/AllTests.java:15-19` | `junit.framework.TestSuite` enumerates the test classes **in a fixed order** (`value`: ValueTest → URealValueTest → UBooleanValueTest → UIntegerValueTest; `expr`: 10 classes in a written order) | delete the suites; let Surefire discover `*Test` classes | **BEHAVIOUR-CHANGING** | The suites pin execution order; Surefire's default `runOrder` is `filesystem`. Order is load-bearing in this suite: `USECompilerUncertaintyTest.java:61` writes the process-global `Options.explicitVariableDeclarations = false` (`src/main/org/tzi/use/config/Options.java:155` — `public static boolean … = true`) and never restores it, and `src/main/org/tzi/use/uml/ocl/expr/ExpStdOp.java:56` is a `public static ListMultimap` mutated by `addOperation`/`removeAllOperations`. | HIGH — decide explicitly: pin the order, or make the global writes self-restoring (see M-45) |
| CF-6 | 943 sites (954 `assertEquals(` occurrences minus the 11 two-arg forms), e.g. `URealValueTest.java:15`, `ExpQueryUncertaintyTest.java:233`, `URealExpOpsTest.java:36` | `assertEquals(String message, expected, actual)` — message **first** | reorder to `assertEquals(expected, actual, message)` | BEHAVIOUR-PRESERVING **provided argument order is preserved** | Jupiter has no `(String, Object, Object)` overload, so every non-String triple is a hard compile error — the compiler finds them for you. JUnit 3 `Assert.assertEquals(String,Object,Object)` evaluates `expected.equals(actual)`; Jupiter's `AssertionUtils.objectsAreEqual` evaluates the same. Jupiter's `assertEquals(int,int,String)` and `assertEquals(double,double,String)` match the JUnit 3 primitive overloads (both use `Double.doubleToLongBits` equality for doubles, as does `Double.equals`). | MEDIUM — see CF-7 for the sites the compiler will **not** catch |
| CF-7 | 12 sites where **all three arguments are `String`**: `UIntegerExpOpsTest.java:29,36,43,50,57,64,71,82,89,96` and `USECompilerUncertaintyTest.java:90,94` | binds to `junit.framework.Assert.assertEquals(String message, String expected, String actual)` | reorder to `assertEquals(expected, actual, message)` — **by hand, verified individually** | **BEHAVIOUR-CHANGING if left as written** | These are the only 12 sites that still **compile silently** under Jupiter, binding to `assertEquals(Object expected, Object actual, String message)`. The message becomes `expected`, `expected` becomes `actual`, `actual` becomes the message. No warning is emitted. Example: `UIntegerExpOpsTest.java:29 assertEquals(eUInteger.toString() + ".toString()", "UInteger(-5, 0.0)", eUInteger.toString())`. | **CRITICAL** |
| CF-8 | `USECompilerUncertaintyTest.java:22-24, 56-57, 63` | `TEST_PATH = System.getProperty("user.dir") + "/src/test/org/tzi/use/parser/uncertainty"`, then `dir.listFiles(new SuffixFileFilter(".in"))`, guarded only by `assertNotNull(files)` | move the 4 `.in` fixtures to `use-core/src/test/resources/…` and resolve via the classpath; **and add `assertTrue(files.length > 0)`** | **BEHAVIOUR-CHANGING** | Under Maven the module root is `use-core/`, not the repo root, so `user.dir` no longer contains `src/test/org/…`. `listFiles` then returns `null` and line 63 fails — *or*, if a directory exists but is empty, the `for` at line 68 runs zero times and the test **passes vacuously**. Adding the non-empty assertion converts a previously-vacuous pass into a failure, which is itself a behaviour change and must be stated as one. | **CRITICAL** |
| CF-9 | `USECompilerUncertaintyTest.java:73` `new FileReader(testFile)`; `:151` `expressionTest.expression.getBytes()` | platform-default charset for both the fixture read and the expression byte stream | pass `StandardCharsets.UTF_8` explicitly to both | **BEHAVIOUR-CHANGING** (in principle) | `file src/test/org/tzi/use/parser/uncertainty/*.in` → 3 of 4 are "Unicode text, UTF-8 text". The only non-ASCII bytes are on `# Creación` comment lines (line 5 of each), which `readExpressionLine` skips at line 116 (`line.startsWith("#")`). JEP 400 already makes Java 18+ default to UTF-8, so pinning UTF-8 matches Java 21's default — but it changes behaviour under an explicit `-Dfile.encoding=…`. | LOW |
| CF-10 | `/home/xoruser/msc-4/use-msc2026/pom.xml`, `/home/xoruser/msc-4/use-msc2026/use-core/pom.xml` — no `<project.build.sourceEncoding>` (`grep -n encoding` finds only the XML prolog) | javac uses the platform default charset; the fork's `build.xml` also has no `encoding=` attribute (`grep -n encoding build.xml` → no hits) | add `<project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>` | BEHAVIOUR-PRESERVING | The 6 in-scope sources carrying non-ASCII bytes (`UncertainValue.java:11`, `UBooleanValue.java:12`, `URealValue.java:11`, `UStringValue.java:107`, `UncertainType.java:7`, `UIntegerType.java:9`, plus `URealExpOpsTest.java:12,433`, `UBooleanExpOpsTest.java:1654`) carry them **only in comments/Javadoc** — `Víctor`, `métodos`, `librería`. No string literal in scope is non-ASCII. On a US-ASCII platform default this is a hard `error: unmappable character` rather than mere mojibake, hence its place in this table. | LOW |

---

## Table B — modernization ledger

| id | file:line | what the code does now | proposed change | class | evidence that it is preserving / what exactly moves | risk |
|---|---|---|---|---|---|---|
| M-1 | `src/main/.../value/UncertainValue.java:37-44` | `uDistinct` declares two locals and assigns before returning | `return uEquals(other).not();` | BEHAVIOUR-PRESERVING | Pure local rewrite; no side effect can be interposed between the assignment and the return. | LOW |
| M-2 | `.../value/URealValue.java:16`; `UIntegerValue.java:10`; `UStringValue.java:10`; `SBooleanValue.java:16` | wrapper fields (`uReal`, `uInteger`, `wrapper`, `sBoolean`) are non-`final` but never reassigned | add `final` | BEHAVIOUR-PRESERVING | Every write is in a constructor: `URealValue.java:20,25`; `UIntegerValue.java:14`; `UStringValue.java:14`; `SBooleanValue.java:20,25`. No setter exists in any of the four. | LOW |
| M-3 | `.../value/URealValue.java:104-107` | `else if (o instanceof URealValue) { URealValue uReal = (URealValue) o; res = uReal.compareTo(new UIntegerValue((int) uReal.value(), uReal.uncertainty())); }` — an unreachable duplicate of the guard at line 98, whose local `uReal` also shadows the field and would recurse | delete lines 104-107 | BEHAVIOUR-PRESERVING | Line 98 `if (o instanceof URealValue)` dominates every path to line 104; no input reaches it. **Do not "repair" it into a live branch** — that would be a behaviour change. | LOW |
| M-4 | `.../value/URealValue.java:112-127` (`valueOf` returns `null`); dereferenced unguarded at `StandardOperationsUReal.java:64,136,173,253,286,397,435` | a null-returning factory whose result is dereferenced without a check | add `@Nullable` to the factory; **add no defensive branch** | BEHAVIOUR-PRESERVING (annotation only) | Any guard converts today's `NullPointerException` into some other result. The argument types are constrained by `matches()` before `eval` runs (`ExpStdOp.java:129-135`), so the NPE is unreachable through `create` — but `evalWithArgs` bypasses `matches` (see M-38). | MEDIUM |
| M-5 | `.../value/URealValue.java:70-89`; `UBooleanValue.java:230-242`; `UIntegerValue.java:70-88` | nested `instanceof` + explicit cast chains | rewrite as pattern-matching `instanceof` (Java 16+), keeping branch order **and** the outer `obj instanceof Value` guard | BEHAVIOUR-PRESERVING | Pattern `instanceof` is exactly `test + cast` with the same order of evaluation. Dropping the outer `instanceof Value` guard is a *separate* change and is not proposed. | LOW |
| M-6 | `.../value/URealValue.java:160`; `UIntegerValue.java:124`; `UStringValue.java:48`; `SBooleanValue.java:159` | bare `throw new RuntimeException("A value kind of … expected")` | **DEFER** — narrowing to `IllegalArgumentException` | **BEHAVIOUR-CHANGING** | `ExpQueryUncertaintyTest.java:179,200` asserts specifically `catch (RuntimeException exp)`, which a subclass still satisfies; but `ExpConstSBoolean.java:57` and `ASTSBooleanLiteral.java:35` both `catch (Exception)` and swallow it into `UndefinedValue`/`SemanticException`. I cannot enumerate every downstream `catch` in the evaluator, so this is classified changing. | MEDIUM |
| **F-1** | `.../value/URealValue.java:77-82` | `equals` rounds `this.value`, `other.value`, `this.uncertainty`, `other.uncertainty` with `MathUtil.round(x, 10)` and then compares with `==` on `double` | port `MathUtil.round` **byte-identically** into `use-core/.../util/MathUtil.java` (which currently has `max`/`min` only — `grep -n round use-core/src/main/java/org/tzi/use/util/MathUtil.java` → **no hit**), and leave `equals` untouched | BEHAVIOUR-PRESERVING **only if `round` is copied verbatim** | 101 assertions in the ported tests spell expected uncertainties to exactly 10 decimals (`grep -hoE '[0-9]\.[0-9]{8,}'` over the 8 test files = 101), e.g. `ExpQueryUncertaintyTest.java:233 new URealValue(12.2, 0.5590169944)` and `URealExpOpsTest.java:2257 new URealValue(1.0, 2.9154759474)`. Those pass *only* because `equals` truncates at the 10th decimal. Substituting `FloatUtil.equals` (ε = 10⁻⁸, from `Options.java:200 DEFAULT_FLOAT_PRECISION = 8`) or `BigDecimal` moves the boundary. | **CRITICAL** |
| **F-2** | `src/main/org/tzi/use/util/MathUtil.java:106-109` — `Math.round(value * Math.pow(10, digits)) / exp`, called with `digits = 10` from 15 sites | `Math.round(double)` returns `long`; `value * 1e10` **overflows `long`** and saturates for `\|value\| > 9.223372036854776e8` | **DEFER** — replacing with `BigDecimal.setScale(10, HALF_UP)` | **BEHAVIOUR-CHANGING** | Measured: `Math.round(9.3e8*1e10)/1e10` → `9.223372036854776E8` and `Math.round(9.4e8*1e10)/1e10` → `9.223372036854776E8`. Two *unequal* large URealValues therefore compare **equal** today. No test in the ported set exercises magnitudes that large (`grep -hoE '[0-9]{9,}'` over the 8 test files returns only 10-decimal *fractions*), so a silent change here would go undetected. | HIGH |
| **F-3** | `.../value/URealValue.java:56-64` vs `:77-82` | `hashCode()` hashes the **unrounded** `value()`/`uncertainty()`; `equals()` compares the **rounded** ones | **DEFER** — making `hashCode` round to 10 decimals | **BEHAVIOUR-CHANGING** | The equals/hashCode contract is violated today: two `equals` URealValues can land in different buckets. Fixing it changes `HashSet<Value>` / `SetValue` membership and therefore the *printed contents* of `Set{…}` in the `.in` fixtures. | HIGH |
| **F-4** | `.../value/URealValue.java:85,87` | cross-type equality against `IntegerValue`/`RealValue` uses raw `==` on `double` and `uncertainty() == 0`, with **no** rounding — unlike the URealValue-vs-URealValue arm directly above | **DEFER** — unifying the two arms | **BEHAVIOUR-CHANGING** | `URealValueTest.java:134-136` (`new URealValue(-2,0).equals(new RealValue(-2))` must be true) passes on exact `==`. Note the asymmetry: `RealValue.java:57-65` has **no** URealValue arm, so the reverse is false; and `RealValue` compares reals with `FloatUtil.equals` (fuzzy, ε = 10⁻⁸) while `URealValue` uses `==`. Unifying changes both directions. | HIGH |
| **F-5** | `.../value/URealValue.java:45` | `double valueCorrected = value() == 0 ? 0 : value();` — maps `-0.0` to `+0.0` for printing | keep verbatim; do **not** "simplify" to `Math.abs`, `+ 0.0`, or `Double.valueOf(v).equals(0.0)` | BEHAVIOUR-PRESERVING if untouched | Measured: `-0.0 == 0` is `true` (IEEE 754), so the ternary catches negative zero; `Double.valueOf(-0.0).equals(0.0)` is `false` and `Double.compare(-0.0, 0) == 0` is `false`, so either rewrite reintroduces `"-0.0"` into output that `URealValueTest.java:27-29` (`{0.0, -5.0, "UReal(0.0, 5.0)"}`) depends on. The intent is stated in the comment at lines 43-44. | MEDIUM |
| M-7 | `.../value/UBooleanValue.java:27-31,45,66,99`; `ExpConstUString.java:19` | Javadoc/comment typos: FALSE is documented as "always its **true** (true, 0)"; `Rremove`, `hanlde`, `clases`, `confidance` | fix the prose | BEHAVIOUR-PRESERVING | Comments have no runtime effect. | LOW |
| M-8 | `.../value/UBooleanValue.java:233-234` | `(other.isFalse() && this.probability() == 0 && !this.value())` is **dead**: `UBooleanValue.FALSE` is built at line 31 as `new UBooleanValue(true, 0)`, so `!this.value()` is false ⇒ `UBooleanValue.FALSE.equals(BooleanValue.FALSE)` returns **false** | **DEFER** — repairing the disjunct | **BEHAVIOUR-CHANGING** | `valueOf(boolean,double)` (lines 100-103) normalises every value to `value = true`, so no reachable UBooleanValue has `value() == false`. Repairing this makes the OCL expression `UBoolean(true, 0) = false` start evaluating to true. | HIGH |
| **F-6** | `.../value/UBooleanValue.java:240` | `MathUtil.round(other.probability(), 10) == MathUtil.round(this.probability(), 10)` | keep verbatim | BEHAVIOUR-PRESERVING if untouched | `UBooleanValueTest.java:136-138`: `b = UBooleanValue.valueOf(false, 1)` flips to `(true, 1-1)` and `a.equals(b)` must be **true**; that holds only because both sides round identically. | HIGH |
| **F-7** | `.../value/UBooleanValue.java:78,80,105,107` | the `TRUE`/`FALSE` singletons are selected by exact `== 0` / `== 1` on a `double` probability | keep verbatim; do **not** introduce a tolerance | BEHAVIOUR-PRESERVING if untouched | A probability of `1 - 1e-17` does **not** collapse to `TRUE` today, and the singleton identity is observable through `==`-based fast paths elsewhere (`F-11`, `StandardOperationsUBoolean` short-circuits). `UBooleanValueTest.java:49-52` depends on `1 - 1.0` being exactly `0.0`. | HIGH |
| **F-8** | `.../value/UBooleanValue.java:100-103` | negation by `probability = 1 - probability` in binary FP | keep verbatim | BEHAVIOUR-PRESERVING if untouched | Measured: `(1 - 0.2) == 0.8` is **exactly true** and `(1 - 0.5) == 0.5` is exactly true, which is what makes `UBooleanValueTest.java:54-57` (`assertEquals(0.8, UBooleanValue.valueOf(false, 0.2).probability())`, an exact `Double.equals`) pass. Other operands are not so lucky (`1 - 0.7` = `0.30000000000000004`), so any restructuring of this arithmetic is observable. | HIGH |
| **F-9** | `.../value/UBooleanValue.java:46` | `if (uBoolean.getC() < 0 \|\| uBoolean.getC() > 1) throw new RuntimeException(…)` — range check on a raw `double` | keep verbatim | BEHAVIOUR-PRESERVING if untouched | `UBooleanValueTest.java:11-17,36-42` show the two range tests are **commented out** with `// FIXME: When It will be fixed in atenea library`, i.e. the library `UBoolean` ctor is already clamping and this guard never fires for those inputs. Changing the comparison changes which inputs reach the library. | MEDIUM |
| M-9 | `.../value/UIntegerValue.java:103-104` | `else if (o instanceof URealValue) res = o.compareTo(this);` — delegates **without negating the sign** | **DEFER** — inserting the negation | **BEHAVIOUR-CHANGING** | `Value implements Comparable<Value>` (`use-core/src/main/java/org/tzi/use/uml/ocl/value/Value.java:36`). Today `UInteger(1,0).compareTo(UReal(5,0))` returns `+1` because `URealValue.compareTo` reports UReal(5) > UInt(1). Sort order of mixed collections — and therefore `SetValue`/`OrderedSetValue` printing — depends on this. | HIGH |
| M-10 | `.../value/UIntegerValue.java:84-86` | `else if (obj instanceof URealValue) eq = obj.equals(this);` — delegates to a method that has no `UIntegerValue` arm, so it is always `false` | **DEFER** — adding the missing arm to `URealValue.equals` | **BEHAVIOUR-CHANGING** | `URealValue.java:70-89` handles `URealValue`, `IntegerValue`, `RealValue` only; there is no `instanceof UIntegerValue`, so control falls through to `eq = false`. | MEDIUM |
| **F-10** | `.../value/UIntegerValue.java:57-64` | `int hash = Double.hashCode(value()); hash *= 7 * Double.hashCode(uncertainty()); return hash;` — **multiplies** by the uncertainty hash | **DEFER** — repairing to the additive, zero-guarded form used by `URealValue.java:58-63` | **BEHAVIOUR-CHANGING** | Measured: `Double.hashCode(0.0) == 0`. Therefore **every** `UInteger(n, 0)` hashes to `0` and they all collide in one bucket. The stated intent in the comment at lines 59-60 ("`1 = 1.0 = UReal(1,0) = UInteger(1,0)`") holds for neither this method nor `URealValue.hashCode`. Repairing it changes `Set{…}` iteration order and hence the printed output the `.in` fixtures assert on. | **CRITICAL** |
| M-11 | `.../value/UStringValue.java:86-87` | `eq = wrapper.getString().equals(ustring.wrapper) && wrapper.getsConf() == wrapper.getsConf();` — `String.equals(UString)` is **always false**, and the second conjunct compares a field **to itself** | **DEFER** — this is the single largest latent defect in scope | **BEHAVIOUR-CHANGING** | `javap -cp lib/atenearesearchgroup.uncertainty.jar uDataTypes.UString` shows `getString()` returns `java.lang.String`; `String.equals(Object)` returns false unless the argument is a `String`, and `ustring.wrapper` is a `UString`. The receiver on both sides of `==` is `wrapper` (this), **not** `ustring.wrapper`. Net effect: `UStringValue.equals` returns `false` for every argument including an identical UString. | **CRITICAL** — port broken; fix only under a separate, human-approved behaviour change |
| M-12 | `.../value/UStringValue.java:95-104` | `compareTo` returns `wrapper.getString().compareTo(valueOf(o).toString())` — compares a bare `String` against the **wrapper form** `UString('x', 1.0)` | **DEFER** | **BEHAVIOUR-CHANGING** | `toString()` routes through `toString(StringBuilder)` at lines 67-71, which emits `UString('…', c)`. Note also that line 100 (`!(o instanceof StringValue)`) diverts every UString-vs-UString comparison to `toString().compareTo(o.toString())`, so line 103 is reached only for `StringValue` arguments. | HIGH |
| M-13 | `.../value/UStringValue.java:165-173` | `uCharacters()` builds `Value[]` with an index loop over a `List<UString>` returned by the jar | `for (UString s : sequence)` or `sequence.stream().map(UStringValue::new).toArray(Value[]::new)` | BEHAVIOUR-PRESERVING | `get(i)` over `0..size-1` yields the same elements in the same order as iteration for any `List`; the resulting array contents and order are identical. Bonus: if the jar returns a `LinkedList` the current loop is O(n²). | LOW |
| M-14 | 12 sites: `URealValue.java:96,113,140`; `UIntegerValue.java:95,110`; `UStringValue.java:56`; `ExpConstUBoolean.java:37`; `ExpConstSBoolean.java:40`; `ExpDefSBoolean.java:23`; `ASTUBooleanLiteral.java:23`; `ASTUIntegerLiteral.java:23`; `ASTSBooleanLiteral.java:26` | redundant `= null` initialisers on locals that are definitely assigned on every path | drop the initialiser | BEHAVIOUR-PRESERVING | Definite assignment holds on every path; javac emits identical bytecode. | LOW |
| M-15 | `src/test/org/tzi/use/uml/ocl/value/URealValueTest.java:6` | `import java.util.HashSet;` — never used (`grep -n HashSet` → line 6 only) | delete | BEHAVIOUR-PRESERVING | Unused import. | LOW |
| M-16 | `.../value/SBooleanValue.java:296,304,312,320,328,336,345,358,371,384,397,410,423,436,449` (15 sites) | `new LinkedList<SBoolean>()` — explicit type argument | `new LinkedList<>()` | BEHAVIOUR-PRESERVING | Diamond infers the identical type argument. | LOW |
| M-17 | `.../value/SBooleanValue.java:346-351,359-364,372-377,385-390,398-403,411-416,424-429,437-442,450-455` (9 sites) | `Iterator<Value> it = seq.iterator(); while (it.hasNext()) { Value v = it.next(); … }` | `for (Value v : seq) { … }` | BEHAVIOUR-PRESERVING | `SequenceValue extends CollectionValue` (`SequenceValue.java:41`) and `CollectionValue … implements Iterable<Value>` (`CollectionValue.java:46`), so the enhanced-for compiles to the same iterator protocol. No loop body uses `it.remove()` or an index. | LOW |
| M-18 | `.../value/SBooleanValue.java:150-153` | `public int compareTo(Value o) { return 0; }` — every SBooleanValue compares equal to **every** Value including `UndefinedValue` and `StringValue` | **DEFER** — implementing a real order | **BEHAVIOUR-CHANGING** | `Value implements Comparable<Value>` and OCL set/orderedset printing sorts with it. Note this is also inconsistent with `equals` (lines 138-148) and can make Java 21's TimSort throw `IllegalArgumentException: Comparison method violates its general contract` on a large enough mixed collection. | HIGH |
| **F-11** | `.../value/SBooleanValue.java:57-68` (`Builder.build`) | `if (belief == 1 && disbelief == 0 && uncertainty == 0 && agent == 1)` — TRUE/FALSE singleton selection by exact `==` on four `double`s | keep verbatim | BEHAVIOUR-PRESERVING if untouched | `ExpConstSBoolean.java:50-53` feeds these from `Double.parseDouble(value.toString())`, so `SBoolean(1,0,0,1)` reaches the singleton but `SBoolean(0.9999999999999999,0,0,1)` does not. Singleton identity is observable through `SBooleanValue.equals` (line 140 `obj == this`). | HIGH |
| M-19 | `.../value/SBooleanValue.java:343,356,369,382,395,408,421,434,447` (`(CollectionValue) value`) and `:462` (`(UBooleanValue) value`) | unguarded downcasts | leave the casts; add no `instanceof` guard | BEHAVIOUR-PRESERVING (no change) | The casts are protected by `matches()` — e.g. `StandardOperationsSBoolean.java:904-908` requires `params[1].isKindOfCollection(EXCLUDE_VOID)` — and `ExpStdOp.java:129-135` only constructs the op when `matches` returned non-null. | LOW |
| M-20 | `.../type/UIntegerType.java:40` | `new HashSet<Type>(3)` while the four siblings use `new HashSet<>()` (`URealType.java:34`, `UStringType.java:24`, `UBooleanType.java:34`, `SBooleanType.java:24`) | `new HashSet<>(3)` | BEHAVIOUR-PRESERVING | Diamond infers `Type`; the capacity argument is retained, so bucket layout is unchanged. | LOW |
| M-21 | `.../type/UIntegerType.java:39-45` returns `{OclAny, UReal, this}`; `URealType.java:33-38` returns `{this, OclAny}`; `UBooleanType.java:33-40` returns `{mkUBoolean(), OclAny, mkSBoolean()}`; `UStringType.java:23-28` returns `{mkUString(), OclAny}` | `this` and `TypeFactory.mkX()` used interchangeably for the self-entry | **DEFER** — unifying on one form | **BEHAVIOUR-CHANGING** | `TypeFactory.java:48-56` holds `private static final` singletons, so `mkX()` and `this` *are* the same instance **for the singleton instances only**. `src/test/org/tzi/use/uml/ocl/type/TypeTest.java:380-403` constructs `new UIntegerType()`, `new URealType()`, `new UBooleanType()`, `new SBooleanType()` directly — for those objects `this != mkX()`, and the set contents genuinely differ. | MEDIUM |
| M-22 | `.../type/UIntegerType.java:14` `public UIntegerType()`; `URealType.java:8` `protected URealType()`; `UBooleanType.java:8`, `UStringType.java:8`, `SBooleanType.java:8` package-private | three different constructor visibilities across five sibling types | **DEFER** — narrowing all five to package-private | **BEHAVIOUR-CHANGING** (API surface) | All in-repo callers are `TypeFactory.java:48-56` (same package) and `src/test/org/tzi/use/uml/ocl/type/TypeTest.java:380-403` (also package `org.tzi.use.uml.ocl.type`), so nothing in the fork breaks — but `public UIntegerType()` is a published constructor and narrowing it is a source/binary-incompatible change for any plugin. | MEDIUM |
| M-23 | `.../type/UncertainType.java:3-8` | Javadoc reads "Abstract base class for basic types (Integer, Real, Boolean, and String)" — copied verbatim from `BasicType` | rewrite the Javadoc | BEHAVIOUR-PRESERVING | Comment only. | LOW |
| M-24 | `.../expr/ExpConstUBoolean.java:9-10`; `ExpConstUInteger.java:8-9`; `ExpConstUReal.java:10-11`; `ExpConstUString.java:12-13`; `ExpConstSBoolean.java:11-14`; `ExpDefSBoolean.java:10` | 12 `private Expression e…;` fields, non-`final`, each assigned exactly once in the constructor | add `final` | BEHAVIOUR-PRESERVING | No setter and no reassignment exists in any of the six classes. | LOW |
| M-25 | `.../expr/ExpConstUReal.java:32` | `ctx.enter(this);;` — a stray empty statement | delete the second `;` | BEHAVIOUR-PRESERVING | An empty statement compiles to nothing. | LOW |
| M-26 | `.../expr/ExpDefSBoolean.java:22-29` | `eval` calls `ctx.enter(this)` at line 25 but **never** calls `ctx.exit(…)` before returning at line 28 | **DEFER** — adding the missing `ctx.exit(this, result)` | **BEHAVIOUR-CHANGING** | Every sibling balances the pair: `ExpConstSBoolean.java:60`, `ExpConstUReal.java:44`, `ExpConstUBoolean.java:53`, `ExpConstUInteger.java:46`, `ExpConstUString.java:54`. Adding it here changes the shape of the printed evaluation tree (`-vv`, the GUI evaluation browser) and the context stack depth of anything nested inside an `SBoolean(b)` expression. | HIGH |
| M-27 | `.../expr/ExpDefSBoolean.java:15-16` | `if (eBool.type().isKindOfUBoolean(EXCLUDE_VOID)) throw new RuntimeException("Expression Boolean or UBoolean expected");` — the condition is **inverted** relative to its own message | **DEFER** | **BEHAVIOUR-CHANGING** | Today `SBoolean(someUBooleanExpr)` throws, while `SBoolean(someStringExpr)` is accepted and then silently produces `null` from `SBooleanValue.valueOf` (lines 71-88 return `null` for a non-boolean argument). Inverting the guard changes both outcomes. | **CRITICAL** |
| M-28 | `.../expr/ExpConstUBoolean.java:47` | `UBooleanValue.valueOf(Boolean.valueOf(value.toString()), Double.valueOf(probability.toString()))` — round-trips two already-typed `Value`s through their `String` form and re-parses | **DEFER** — replacing with direct accessors | **BEHAVIOUR-CHANGING** | `Double.valueOf(probability.toString())` on an `IntegerValue` yields `1.0` where `((IntegerValue) probability).value()` yields `1`; and the `NumberFormatException` a malformed string would raise is what the `catch (RuntimeException)` at lines 49-51 converts to `UndefinedValue`. A direct-accessor rewrite removes that path. | MEDIUM |
| M-29 | `.../expr/ExpConstUBoolean.java:44` | only `probability.isUndefined()` is checked; `value` is never checked | **DEFER** — adding a `value.isUndefined()` guard | **BEHAVIOUR-CHANGING** | Today an undefined `value` yields `value.toString()` = `"Undefined"`, so `Boolean.valueOf` gives `false`, and `UBooleanValue.valueOf(false, p)` flips it to `(true, 1-p)` — a *defined* result from an undefined operand. The siblings check both: `ExpConstUInteger.java:34` (`value.isDefined() && uncertainty.isDefined()`), `ExpConstUReal.java:36`. | HIGH |
| M-30 | `.../expr/ExpConstUString.java:44,48` | `(StringValue) eValue.eval(ctx)` is an unguarded cast and `Double.valueOf(confidence.toString())` has no `isUndefined()` guard, so `ClassCastException`/`NumberFormatException` escape `eval` | **DEFER** — wrapping in the `try/catch(Exception) → UndefinedValue` used by the sibling | **BEHAVIOUR-CHANGING** | `ExpConstSBoolean.java:48-59` wraps its whole body in `try { … } catch (Exception ex) { result = UndefinedValue.instance; }`; `ExpConstUString` does not. Wrapping converts an escaping exception into `Undefined`. | HIGH |
| M-31 | `.../expr/ExpConstUReal.java:13-17` | the constructor performs **no** type validation, unlike `ExpConstUBoolean.java:17-21`, `ExpConstUInteger.java:14-18`, `ExpConstUString.java:18-22`, all of which throw `ExpInvalidException`; the check lives instead in `ASTURealLiteral.java:27-31` | **DEFER** — moving the check into the constructor | **BEHAVIOUR-CHANGING** | `ExpConstUReal` is constructed directly, with unvalidated `ExpConstReal` arguments, by the test suite (`URealExpOpsTest.java:34,39,44,…` — 300+ sites) and by any programmatic API caller. Adding a **checked** `ExpInvalidException` to the constructor breaks every one of those call sites at compile time. | HIGH |
| M-32 | `src/main/.../parser/ocl/ASTURealLiteral.java:23-24` and `:34` | `eValue.gen(ctx)` and `eUncertainty.gen(ctx)` are each called **twice** — once for the type check, once to build the real expression | **DEFER** — hoisting into locals and reusing | **BEHAVIOUR-CHANGING** | `ASTExpression.gen(Context)` is not documented as pure; for sub-expressions carrying variable declarations it registers into `ctx`. It also produces two distinct `Expression` object graphs today, and the one actually installed is the second. Hoisting changes which graph is installed and how many times `ctx` is mutated. | HIGH |
| M-33 | `src/main/.../parser/ocl/ASTUStringLiteral.java` (whole file) | has **no** `toString()` override, unlike the other four AST literals (`ASTUBooleanLiteral.java:43-46`, `ASTUIntegerLiteral.java:43-46`, `ASTURealLiteral.java:43-46`, `ASTSBooleanLiteral.java:50-54`) | **DEFER** — adding one for symmetry | **BEHAVIOUR-CHANGING** | AST `toString()` is interpolated into compiler diagnostics; adding an override changes the text of every `SemanticException` that embeds this node. | MEDIUM |
| M-34 | `src/main/.../parser/ocl/ASTUBooleanLiteral.java:13-14` | `public ASTExpression eValue; public ASTExpression eProbability;` — **public mutable fields**, whereas the four sibling AST literals declare the same fields `private` | make them `private final` | BEHAVIOUR-PRESERVING | The only constructions are through the constructor, from the six ANTLR grammars (`OCL.g:562`, `OCLBase.gpart:497`, `USE.g:1071`, `Soil.g:1107`, `ShellCommand.g:860`, `TestSuite.g:669`, `Generator.g:1338`) and the generated parsers (`OCLParser.java:3188`, `SoilParser.java:5664`, `TestSuiteParser.java:4079`) — all `new ASTUBooleanLiteral(a, b)`. `grep -rn '\.eValue\|\.eProbability' src/` finds no external *reader* of these fields (the hits are `ExpConst*`'s own private fields). | LOW |
| M-35 | 22 sites: `StandardOperationsUReal.java:57,91,129,161,214,246,278,325,357,390,423,478,518,558,589,621,662,702`; `StandardOperationsUInteger.java:261,372,413,457` | `public Type matches(Type params[])` — C-style array declarator | `public Type matches(Type[] params)` | BEHAVIOUR-PRESERVING | Pure declarator syntax; identical erasure. **Optional** — the target's own `use-core/.../operations/OpGeneric.java:48` still declares `matches(Type params[])`, so this is cosmetic-only and arguably should be skipped for diff hygiene. | LOW |
| M-36 | `.../operations/StandardOperationsUString.java:19` and `:21` | `OpGeneric.registerOperation(new Op_uString_uConcat(), opmap)` is executed **twice** | delete line 21 | BEHAVIOUR-PRESERVING | `ExpStdOp.java:60` uses `ArrayListMultimap.create(150,5)`, so duplicates *are* retained — but both consumers stop at the first match: `ExpStdOp.java:129-135` (`create`) and `ExpStdOp.java:96-101` (`exists`). `grep -rn opmap src/main` finds no code that iterates `opmap.values()/entries()/keySet()`. The two instances are stateless and behave identically. The only corner is `ExpStdOp.removeAllOperations` (lines 76-80), which matches by object identity and therefore never removed either copy anyway. | LOW |
| M-37 | `.../operations/StandardOperationsUInteger.java:13,17` + `Op_uInteger_value` at `:54-64` | `Op_uInteger_value` is registered under both `"value"` and `"toInteger"`; its `matches` declares the result type `TypeFactory.mkUInteger()` while its `eval` returns an `IntegerValue` | **DEFER** — correcting `matches` to `mkInteger()` | **BEHAVIOUR-CHANGING** | `ExpStdOp.create` (`ExpStdOp.java:130-133`) stores the `Type` returned by `matches` as the *static type* of the expression, so this changes type-checking of every expression that consumes `x.value()` / `x.toInteger()` on a UInteger. The sibling `Op_ureal_value` (`StandardOperationsUReal.java:246-248`) correctly declares `mkReal()`, so the two are already inconsistent. | HIGH |
| M-38 | `.../operations/StandardOperationsUBoolean.java:474-477` (`Op_uBoolean_or.evalWithArgs`) | `ub2 = UBooleanValue.valueOf(v2); if (ub2.probability() == 1)` — **no null check**, unlike the guarded siblings at lines 400 and 411-414 | **DEFER** — adding the guard | **BEHAVIOUR-CHANGING** | `UBooleanValue.valueOf(Value)` (`UBooleanValue.java:122-138`) returns `null` unless the argument `isUBoolean()` or `isBoolean()`; `UndefinedValue` is neither. Control reaches line 474 only when `v1` is undefined, so `Undefined or Undefined` throws `NullPointerException` today — where `Op_uBoolean_and` (line 400) and `Op_uBoolean_implies` (line 552) return `UndefinedValue`. Adding the guard changes an NPE into a value. | **CRITICAL** |
| **F-12** | `.../operations/StandardOperationsUBoolean.java:110-113` (`Op_uBoolean_toString`) | `if (probability < 0.5) sb.append("false, ").append(1 - probability)` — the *printed form* flips at an exact `0.5` boundary and prints `1 - probability` computed in binary FP | keep verbatim | BEHAVIOUR-PRESERVING if untouched | At exactly `0.5` the `else` branch runs and prints `"true, 0.5"`. The printed text is whatever `Double.toString(1 - p)` gives — e.g. `1 - 0.7` prints `0.30000000000000004`. Any restructuring changes user-visible output. | HIGH |
| **F-13** | `.../operations/StandardOperationsUBoolean.java:155,157` (`Op_uBoolean_toBooleanC`) | `if (confience < 0 \|\| confience > 1) → Undefined; else if (left.probability() >= confience) → TRUE` | keep verbatim; introduce no epsilon | BEHAVIOUR-PRESERVING if untouched | The `>=` on raw doubles *is* the documented "certain at confidence c" semantics; a tolerance changes which UBooleans are certain enough, i.e. the truth value of the OCL expression. | HIGH |
| **F-14** | `.../operations/StandardOperationsUBoolean.java:392,400,414,458,476,535,555` (7 sites) | the short-circuit rules of `and`/`or`/`implies` fire only on an **exact** `probability() == 0` / `== 1` | keep verbatim | BEHAVIOUR-PRESERVING if untouched | These guards decide **which side effects run**, not just the value: at line 392 `Op_uBoolean_and` returns `ub1` *without* evaluating the right-hand operand (line 396 `args[1].eval(ctx)` is skipped). A fuzzy comparison would change operand evaluation, which is observable through the evaluation tree and through any operand with a side effect. | **CRITICAL** |
| **F-15** | `.../operations/StandardOperationsUReal.java:437,442`; `.../value/UIntegerValue.java:177-188` | `float exponent; … exponent = (float) ((RealValue) args[1]).value();` — the `power` exponent is **narrowed from double to float** | keep `float` | BEHAVIOUR-PRESERVING (and forced) | `javap -cp lib/atenearesearchgroup.uncertainty.jar uDataTypes.UReal` → `public uDataTypes.UReal power(float);` — there is **no** `power(double)`. Widening the local to `double` will not compile; the narrowing is unavoidable and its precision loss is part of the oracle's behaviour. | MEDIUM |
| **F-16** | `.../operations/StandardOperationsUReal.java:102-103` and `:446-450` | `if (Double.isInfinite(result.value()) \|\| Double.isNaN(result.value())) throw new ArithmeticException();` | keep verbatim | BEHAVIOUR-PRESERVING if untouched | This is the only place overflow/NaN out of `UReal.inverse()`/`power()` is turned into an OCL error; the `ArithmeticException` is caught upstream in the evaluator. Replacing it with a range test or a returned `UndefinedValue` changes the error path. | MEDIUM |
| M-39 | `.../operations/StandardOperationsSBoolean.java:18-1481` | 45 anonymous `OpGeneric` subclasses inside an enum (`grep -c 'new OpGeneric()'` = 45) | optionally convert to named `final class Op_sbool_*`, matching the style of the other four `StandardOperationsU*` files | BEHAVIOUR-PRESERVING | **Lambdas are not applicable**: `OpGeneric` is an abstract *class* whose constants override five methods (`name`, `kind`, `isInfixOrPrefix`, `matches`, `eval`), so it is not a functional interface. Extracting to named classes is a pure refactor with identical registration order via `values()` at line 1497. | LOW — 1502 lines touched for zero behavioural gain; recommend skipping during the fidelity port |
| M-40 | `.../operations/StandardOperationsSBoolean.java:1485` | `private OpGeneric op;` — non-final, never reassigned | add `final` | BEHAVIOUR-PRESERVING | The only write is the enum constructor at line 1488. | LOW |
| M-41 | `.../operations/StandardOperationsSBoolean.java:~1382-1479` | ~100 lines of commented-out enum constants (`CUMULATIVEFUSION`, `EPISTEMICCUMULATIVEFUSION`, `WEIGHTEDFUSION`) | delete | BEHAVIOUR-PRESERVING | Comments have no runtime effect — **but** they are the only record of why `SBooleanValue.cumulativeFusion` (line 318), `epistemicCumulativeFusion` (line 326) and `weightedFusion` (line 334) have no caller. If deleted, move that note into the port docs. | LOW |
| M-42 | `.../operations/StandardOperationsSBoolean.java:18` | enum constant `PROYECTION` (Spanish spelling) for the operation whose OCL name is `"projection"` | rename to `PROJECTION` | BEHAVIOUR-PRESERVING | The constant name is never used as a string; the OCL name comes from `name()` at line 22. `grep -rn PROYECTION src` finds only the declaration. | LOW |
| M-43 | `src/test/.../value/UBooleanValueTest.java:11-17` and `:36-42` | two commented-out `try { … fail(…) } catch` blocks marked `// FIXME: When It will be fixed in atenea library.` | **DEFER** — reviving them as `@Disabled` Jupiter tests | **BEHAVIOUR-CHANGING** | These record that `UBooleanValue.valueOf(true, -2)` / `(true, 2)` do **not** throw today despite the guard at `UBooleanValue.java:46` (the library `UBoolean` ctor clamps first). Reviving them as *live* tests makes the suite red; reviving them as `@Disabled` adds two "skipped" entries to the report. | MEDIUM |
| M-44 | 40 sites across `URealExpOpsTest.java:875-911`, `UIntegerExpOpsTest.java:106-142,267-313,484-510`, `UBooleanExpOpsTest.java:217-225,1611-1619,1663-1671`, `ExpQueryUncertaintyTest.java:154-206` | JUnit 3 expected-exception idiom: `try { …; fail("X expected"); } catch (X e) { } catch (Exception ex) { fail(…) }` | **DEFER** — converting to `assertThrows(X.class, () -> …)` | **BEHAVIOUR-CHANGING** | Two distinct shifts. (a) `assertThrows(X.class, …)` accepts any **subclass** of X, whereas the historical second `catch (Exception ex) { fail(…) }` narrows it — and `ExpQueryUncertaintyTest.java:179` catches `RuntimeException`, which today swallows even a `NullPointerException` as a pass. (b) `ExpQueryUncertaintyTest.java:174-178` puts **two** statements inside one `try`, so `assertThrows` must wrap both or the assertion silently narrows to the second. | HIGH |
| M-45 | `src/test/.../parser/uncertainty/USECompilerUncertaintyTest.java:61` | `Options.explicitVariableDeclarations = false;` — set once, never restored | **DEFER** — save/restore in `@BeforeEach`/`@AfterEach` | **BEHAVIOUR-CHANGING** | `src/main/org/tzi/use/config/Options.java:155` declares `public static boolean explicitVariableDeclarations = true`. Today every test that runs *after* this one in the same JVM sees `false`. Restoring it changes what those tests see — this is exactly the coupling that makes CF-5 (suite ordering) load-bearing. | HIGH |
| M-46 | `USECompilerUncertaintyTest.java:20,22` | `private static boolean VERBOSE = true;` / `private static String TEST_PATH = …` — non-final statics | add `final` | BEHAVIOUR-PRESERVING | Neither is assigned after its declaration (one `=` hit each in the file). | LOW |
| M-47 | `USECompilerUncertaintyTest.java:31-51` | `private class StringOutputStream extends OutputStream` — a non-static inner class holding an implicit outer reference | `private static class` | BEHAVIOUR-PRESERVING | No member of the enclosing instance is referenced in the class body (lines 32-50 touch only `fBuffer`). | LOW |
| M-48 | `USECompilerUncertaintyTest.java:26-29` | `private class ExpressionTest { String expression; String expected; }` | `private static class` — but **do not** convert to a `record` | `static`: BEHAVIOUR-PRESERVING; `record`: **BEHAVIOUR-CHANGING** | `static` is safe for the same reason as M-47. A `record` would change `ExpressionTest.toString()` from the default `Object` identity form to a component listing, and that string is interpolated into the assertion message at line 90 (`"evaluate : " + expTest`). | MEDIUM |
| M-49 | `USECompilerUncertaintyTest.java:88` | `String errArray [] = sos.toString().split("\n(\r\n)");` — C-style declarator, and a regex meaning "LF **followed by** CRLF" (almost certainly `\n\|\r\n` was intended) | fix the **declarator only** (`String[] errArray`); leave the regex | declarator: BEHAVIOUR-PRESERVING; regex: **DEFER, BEHAVIOUR-CHANGING** | The current regex effectively never matches, so `errArray.length - 1 == 0` and `errMessage` at line 89 is the *entire* captured stderr with `\n`/`\r` stripped. Fixing the regex changes which line is compared against the `-> …` expectation in the `.in` fixtures. | HIGH |
| M-50 | `USECompilerUncertaintyTest.java:128` | `expressionBuilder.append(line.substring(0, line.length()-2) + "\n");` — string concatenation inside `append` | `append(line, 0, line.length() - 2).append('\n')` | BEHAVIOUR-PRESERVING | Same characters appended. **The `-2` is NOT a bug and must not be "fixed" to `-1`**: `od -c` on `UCollectionOperations.in:74` shows the line ends `\ \ \n` — two literal backslashes — and `line.endsWith("\\")` (a one-character test) is satisfied by that pair. All 14 continuation lines in that file use the same two-backslash terminator (`grep -c '\\$'` → 14 in `UCollectionOperations.in`, 0 in the other three). | MEDIUM |
| M-51 | `USECompilerUncertaintyTest.java:99-101` | `catch (IOException ex) { throw new RuntimeException("Couldn't open file " + testFileName); }` — drops the cause | **DEFER** — `throw new UncheckedIOException(msg, ex)` | **BEHAVIOUR-CHANGING** | Changes the exception type and message the runner reports. A *preserving* variant exists: keep `RuntimeException` and pass `ex` as the cause — the type and message are unchanged and only the stack trace grows. | LOW |

---

## Float-sensitivity summary (read this before touching any number)

16 sites (`F-1` … `F-16`) carry behaviour on binary floating point. Grouped by what rides on them:

1. **Value equality**: `F-1` (URealValue, round-to-10 then `==`), `F-4` (UReal vs Real/Integer, raw `==`), `F-6` (UBoolean probability, round-to-10 then `==`).
2. **Hashing / collection membership**: `F-3` (URealValue equals/hashCode mismatch), `F-10` (UIntegerValue hash collapses to 0 whenever uncertainty is 0.0).
3. **Singleton selection**: `F-7` (UBoolean TRUE/FALSE at exact 0/1), `F-11` (SBoolean TRUE/FALSE at exact 1/0/0/1).
4. **Control flow and operand evaluation**: `F-14` (and/or/implies short-circuit at exact 0/1 — decides *whether the right operand is evaluated at all*), `F-13` (`toBooleanC` threshold), `F-12` (printed form flips at 0.5).
5. **Arithmetic identities the tests depend on**: `F-8` (`1 - 0.2 == 0.8` is exactly true — measured), `F-5` (`-0.0 == 0` is true, used to suppress `"-0.0"` in output).
6. **Precision loss forced by the oracle jar**: `F-15` (`UReal.power(float)` — no `power(double)` exists).
7. **Latent overflow**: `F-2` (`MathUtil.round(v, 10)` saturates for `|v| > 9.223372036854776e8`; measured — two unequal large UReals compare equal and no test covers it).
8. **Error signalling**: `F-16` (`Double.isNaN`/`isInfinite` → `ArithmeticException`).

Of these, **`F-2`, `F-3`, `F-4`, `F-10`** are *defects* whose repair is a behaviour change; **`F-1`, `F-5`, `F-6`,
`F-7`, `F-8`, `F-9`, `F-11`, `F-12`, `F-13`, `F-14`, `F-15`, `F-16`** are behaviour that must be carried across
byte-identically. The single highest-leverage precondition is `F-1`: **`MathUtil.round(double,int)` does
not exist in the target** and must be added verbatim from `src/main/org/tzi/use/util/MathUtil.java:106-109`.

---

## Do-not-touch list (for the Port role)

Copy these across unchanged, defects included. Each has a ledger row explaining why:
`URealValue.hashCode` (F-3), `URealValue.compareTo` dead branch (M-3), `UIntegerValue.hashCode` (F-10),
`UIntegerValue.compareTo` unnegated delegation (M-9), `UIntegerValue.equals` URealValue arm (M-10),
`UStringValue.equals` (M-11), `UStringValue.compareTo` (M-12), `UBooleanValue.equals` dead disjunct (M-8),
`SBooleanValue.compareTo` returning 0 (M-18), `ExpDefSBoolean` inverted guard (M-27) and missing
`ctx.exit` (M-26), `ExpConstUBoolean` missing `value.isUndefined()` check (M-29), `ExpConstUString`
unguarded parse (M-30), `ASTURealLiteral` double `gen()` (M-32), `Op_uInteger_value` result-type
mismatch (M-37), `Op_uBoolean_or` NPE on `Undefined or Undefined` (M-38).

---

## Reproduce every count in this file

```sh
cd /home/xoruser/msc-4/use-msc2026/.git/reference-repositories/uncertainty/USE-Uncertainty

# in-scope main sources (30 files)
SCOPE="src/main/org/tzi/use/uml/ocl/value/UncertainValue.java \
src/main/org/tzi/use/uml/ocl/value/UBooleanValue.java \
src/main/org/tzi/use/uml/ocl/value/UIntegerValue.java \
src/main/org/tzi/use/uml/ocl/value/URealValue.java \
src/main/org/tzi/use/uml/ocl/value/UStringValue.java \
src/main/org/tzi/use/uml/ocl/value/SBooleanValue.java \
src/main/org/tzi/use/uml/ocl/value/UncertainBooleanValue.java \
src/main/org/tzi/use/uml/ocl/type/UncertainType.java \
src/main/org/tzi/use/uml/ocl/type/UncertainBooleanType.java \
src/main/org/tzi/use/uml/ocl/type/UBooleanType.java \
src/main/org/tzi/use/uml/ocl/type/UIntegerType.java \
src/main/org/tzi/use/uml/ocl/type/URealType.java \
src/main/org/tzi/use/uml/ocl/type/UStringType.java \
src/main/org/tzi/use/uml/ocl/type/SBooleanType.java \
src/main/org/tzi/use/uml/ocl/expr/ExpConstUBoolean.java \
src/main/org/tzi/use/uml/ocl/expr/ExpConstUInteger.java \
src/main/org/tzi/use/uml/ocl/expr/ExpConstUReal.java \
src/main/org/tzi/use/uml/ocl/expr/ExpConstUString.java \
src/main/org/tzi/use/uml/ocl/expr/ExpConstSBoolean.java \
src/main/org/tzi/use/uml/ocl/expr/ExpDefSBoolean.java \
src/main/org/tzi/use/uml/ocl/expr/operations/StandardOperationsUBoolean.java \
src/main/org/tzi/use/uml/ocl/expr/operations/StandardOperationsUInteger.java \
src/main/org/tzi/use/uml/ocl/expr/operations/StandardOperationsUReal.java \
src/main/org/tzi/use/uml/ocl/expr/operations/StandardOperationsUString.java \
src/main/org/tzi/use/uml/ocl/expr/operations/StandardOperationsSBoolean.java \
src/main/org/tzi/use/parser/ocl/ASTUBooleanLiteral.java \
src/main/org/tzi/use/parser/ocl/ASTUIntegerLiteral.java \
src/main/org/tzi/use/parser/ocl/ASTURealLiteral.java \
src/main/org/tzi/use/parser/ocl/ASTUStringLiteral.java \
src/main/org/tzi/use/parser/ocl/ASTSBooleanLiteral.java"

# in-scope tests (8 files)
TESTS="src/test/org/tzi/use/uml/ocl/value/UBooleanValueTest.java \
src/test/org/tzi/use/uml/ocl/value/UIntegerValueTest.java \
src/test/org/tzi/use/uml/ocl/value/URealValueTest.java \
src/test/org/tzi/use/uml/ocl/expr/ExpQueryUncertaintyTest.java \
src/test/org/tzi/use/uml/ocl/expr/UBooleanExpOpsTest.java \
src/test/org/tzi/use/uml/ocl/expr/UIntegerExpOpsTest.java \
src/test/org/tzi/use/uml/ocl/expr/URealExpOpsTest.java \
src/test/org/tzi/use/parser/uncertainty/USECompilerUncertaintyTest.java"

grep -l 'extends TestCase' $TESTS | wc -l                       # CF-1  -> 8
grep -hcE '^\s+public void test' $TESTS | paste -sd+ | bc        # CF-1  -> 122
grep -h 'assertEquals(' $TESTS | wc -l                           # CF-6  -> 954
grep -nE 'assertEquals\([^;]*,\s*"[^"]*"\s*,\s*[A-Za-z_][A-Za-z0-9_.]*\.toString\(\)\s*\)' $TESTS | wc -l   # CF-7 -> 10 (+2 in USECompilerUncertaintyTest.java:90,94)
grep -h 'MathUtil.round' $SCOPE | wc -l                          # F-1   -> 15
grep -hoE '[0-9]\.[0-9]{8,}' $TESTS | wc -l                      # F-1   -> 101
grep -h 'params\[\]' $SCOPE | wc -l                              # M-35  -> 22
grep -hc 'new LinkedList<SBoolean>()' $SCOPE | paste -sd+ | bc   # M-16  -> 15
grep -h 'Iterator<Value> it = seq.iterator();' $SCOPE | wc -l    # M-17  -> 9
grep -c 'new OpGeneric()' src/main/org/tzi/use/uml/ocl/expr/operations/StandardOperationsSBoolean.java  # M-39 -> 45
grep -n 'round' /home/xoruser/msc-4/use-msc2026/use-core/src/main/java/org/tzi/use/util/MathUtil.java   # F-1  -> no output (round is MISSING in the target)

# overload-resolution proof for CF-2 / CF-6
cat > /tmp/Probe.java <<'EOF'
import junit.framework.TestCase;
import static org.junit.Assert.*;
public class Probe extends TestCase {
  public void testA() { double a=0.1+0.2,b=0.3; assertEquals("msg",a,b); assertEquals("msg2",1,1); }
}
EOF
javac -nowarn -source 8 -target 8 -cp lib/junit.jar -d /tmp /tmp/Probe.java
javap -c -p -cp /tmp Probe | grep -E 'invokestatic|valueOf'
# -> Double.valueOf(D) x2 then assertEquals:(Ljava/lang/String;Ljava/lang/Object;Ljava/lang/Object;)V

# float facts (F-2, F-5, F-8, F-10)
cat > /tmp/P2.java <<'EOF'
public class P2 { public static void main(String[] a) {
  System.out.println((1-0.2)==0.8);                       // true
  System.out.println(Double.hashCode(0.0));               // 0
  System.out.println(Math.round(9.3e8*1e10)/1e10);        // 9.223372036854776E8
  System.out.println(Math.round(9.4e8*1e10)/1e10);        // 9.223372036854776E8  (saturated)
  System.out.println(-0.0 == 0);                          // true
}}
EOF
javac -d /tmp /tmp/P2.java && java -cp /tmp P2

# oracle-jar signatures (F-15, M-11, M-16)
javap -cp lib/atenearesearchgroup.uncertainty.jar uDataTypes.UReal   | grep -E 'power|equals|hashCode'
javap -cp lib/atenearesearchgroup.uncertainty.jar uDataTypes.UString | grep -E 'equals|hashCode|getString'
javap -cp lib/atenearesearchgroup.uncertainty.jar uDataTypes.SBoolean | grep -i fusion
```

---

## Counts

| bucket | n |
|---|---|
| BEHAVIOUR-PRESERVING rows (both tables) | 46 |
| BEHAVIOUR-CHANGING rows (both tables) — **these go to a human** | 33 |
| compile-forced rows (Table A) | 10 |
| float-sensitive rows (`F-*`) | 16 |
| total rows | 77 |

Breakdown by table: Table A = 10 rows (6 preserving, 4 changing). Table B = 67 rows — 51 `M-*`
(26 preserving, 23 changing, 2 dual) and 16 `F-*` (12 preserving, 4 changing).

`M-48` (inner class → `static` vs → `record`) and `M-49` (declarator vs regex) each carry one preserving
half and one changing half, so each is counted once in **both** buckets. That is why 46 + 33 = 79
against 77 rows.

Verify with:

```sh
F=/home/xoruser/msc-4/use-msc2026/docs/port2/spec-parts/16-modernization-ledger.md
grep -cE '^\| CF-[0-9]+ \|' $F                                    # 10
grep -cE '^\| M-[0-9]+ \|' $F                                     # 51
grep -cE '^\| \*\*F-[0-9]+\*\* \|' $F                             # 16
grep -nE '^\| (CF-|M-|\*\*F-)' $F | grep -o 'BEHAVIOUR-PRESERVING' | wc -l   # 46
grep -nE '^\| (CF-|M-|\*\*F-)' $F | grep -o 'BEHAVIOUR-CHANGING'  | wc -l   # 33
```

---

## Gaps — things I could not establish

- **UNVERIFIABLE — whether the historical suite actually passed.** I did not run Ant, Maven, or any
  test. Every "this test depends on X" claim is a reading of the source plus a measured Java fact, not
  an observed green run.
- **UNVERIFIABLE — `ASTExpression.gen(Context)` purity (M-32).** I did not read every `gen`
  implementation reachable from a UReal literal's operands, so "gen is not pure" is a stated risk, not
  a demonstrated one.
- **UNVERIFIABLE — the full set of `catch` sites that would see a narrowed exception type (M-6, M-51).**
  I checked the in-scope callers and the evaluator entry points, not the GUI or plugin layers.
- **UNVERIFIABLE — `uDataTypes` internal numerics.** `UReal.divideBy`, `UBoolean.and/or/implies`,
  `SBoolean.*Fusion` are opaque jar bytecode. Every 10-decimal expectation in the tests is produced by
  that jar; if the target build resolves a *different* build of `atenearesearchgroup.uncertainty.jar`,
  all of `F-1`'s evidence collapses.
- **UNVERIFIABLE — missing `@Override` annotations.** I could not compile the fork with `-Xlint:all`
  (that needs the ANTLR-generated sources and would write into a build directory), so I have no
  authoritative list. Spot checks found `@Override` present on every `matches`/`eval`/`name`/`kind`
  override I read in the five `StandardOperations*` files and on every `equals`/`hashCode`/`compareTo`/
  `toString(StringBuilder)` in the five value classes. **No ledger row claims a missing `@Override`.**
- **Out of scope but coupled.** `src/test/org/tzi/use/uml/ocl/expr/UCollectionExpOpTest.java` and
  `src/test/org/tzi/use/uml/ocl/type/TypeTest.java` are JUnit 3 (`extends TestCase`) and are named by
  `uml/ocl/expr/AllTests.java:47` and by the type-package suite, but neither is in the port plan I was
  given. `src/test/org/tzi/use/parser/uncertainty/UCollectionOperations.in` **is** globbed by
  `USECompilerUncertaintyTest` (`listFiles(".in")`) even though no ledger row covers its test class —
  CF-8's resource move must carry all **four** `.in` files, not three.
- **UNVERIFIABLE — Surefire's actual discovery order** on the target machine. CF-5's risk assessment
  assumes the documented `filesystem` default; I did not run Maven to confirm.
- **Not consulted.** I did not read `origin/main` or any earlier port attempt.
