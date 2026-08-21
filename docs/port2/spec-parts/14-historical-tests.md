# 14 — Historical Test Inventory

Section of the port-2 specification. Subject: the ten test artefacts that the uncertainty fork
carries, which are the port's primary behavioural oracle.

**Reference root (READ-ONLY)**
`/home/xoruser/msc-4/use-msc2026/.git/reference-repositories/uncertainty/USE-Uncertainty`
— abbreviated `$FORK` below. Test root `$FORK/src/test/org/tzi/use` — abbreviated `$T`.

Every count in this document is reproducible by grepping `$FORK/src/test` directly (balanced-paren
extraction of `assertEquals|assertTrue|assertFalse|assertNull|assertNotNull|assertSame|fail|new
EqualsTester` calls, per-class `test*` method counts, corpus `->` line counts). Nothing here is taken
from the earlier port on `origin/main`; the target repo's own upstream
`use-core/src/test/java/org/tzi/use/uml/ocl/type/TypeTest.java` was verified to contain **zero**
uncertainty references before being used as the "pristine USE 7.5.0" baseline in §5.5.

**Status.** The recipe this document originally spelled out — split `TypeTest`'s uncertainty rows
into a new JUnit 5 class, migrate the corpus harness — has been **executed**:
`use-core/src/test/java/org/tzi/use/uml/ocl/type/UncertainTypeLatticeTest.java` and
`use-core/src/test/java/org/tzi/use/parser/uncertainty/USECompilerUncertaintyTest.java` (JUnit 5,
reading `.in` fixtures from `src/test/resources/org/tzi/use/parser/uncertainty/`) both exist. The
original per-method behaviour table (§1, ~720 lines) and the reproduction-command dump (§7) were
therefore dropped from this file as redundant with the migrated code, which is the more authoritative
source. What remains below is what is not otherwise recoverable from the migrated tests: the census
numbers, the one upstream-mutation design question, the under-coverage note, and the named gaps.

---

## 0. Summary

| # | File (relative to `$T`) | JUnit flavour | Test methods | Assertion calls |
|---|---|---|---|---|
| 1 | `uml/ocl/value/URealValueTest.java` | JUnit 3 | 5 | 54 |
| 2 | `uml/ocl/value/UIntegerValueTest.java` | JUnit 3 | 3 | 18 |
| 3 | `uml/ocl/value/UBooleanValueTest.java` | JUnit 3 | 3 | 49 |
| 4 | `uml/ocl/expr/URealExpOpsTest.java` | JUnit 3 | 32 | 356 |
| 5 | `uml/ocl/expr/UIntegerExpOpsTest.java` | JUnit 3 | 39 | 460 |
| 6 | `uml/ocl/expr/UBooleanExpOpsTest.java` | JUnit 3 | 27 | 142 |
| 7 | `uml/ocl/expr/UCollectionExpOpTest.java` | JUnit 3 | 14 | 13 |
| 8 | `uml/ocl/expr/ExpQueryUncertaintyTest.java` | JUnit 3 | 12 | 15 |
| 9 | `uml/ocl/type/TypeTest.java` | JUnit 3 | 46 | 1452 |
| 10 | `parser/uncertainty/USECompilerUncertaintyTest.java` | JUnit 3 | 1 (data-driven, 1427 corpus entries) | 3 (+1427 in loop) |
| | **TOTAL** | | **182** | |

Every one of the ten classes is JUnit 3 (`extends junit.framework.TestCase`, no `@Test` anywhere,
`setUp()` not `@BeforeEach`), wired by hand through `AllTests.suite()` / `TestSuite.addTestSuite(...)`
— `uml/ocl/value/AllTests.java`, `uml/ocl/expr/AllTests.java`, `uml/ocl/type/AllTests.java` (adds
`TypeTest` unchanged — the uncertainty rows live *inside* upstream's file), `parser/uncertainty/AllTests.java`.

`TypeTest.java` is the one class where the uncertainty content is **woven into** an upstream file
rather than living in its own: 10 of its 46 methods are wholly new, 34 of the other 36 carry appended
uncertainty rows, and 1 (`testSupertype`) carries *mutated* upstream assertions — see §3.3.

---

## 3.3 `testSupertype` — the one place where upstream assertions are MUTATED

Cross-checked line-by-line against USE 7.5.0's own `testSupertype`
(`use-core/.../TypeTest.java`, 12 `assertEquals` calls, all closures of `allSupertypes()`). The fork
adds five wholly-new closures (`SBoolean`, `UBoolean`, `UInteger`, `UReal`, `UString` each closing
over themselves + `OclAny`, plus `UInteger`/`UReal` chaining), and it **mutates** ten of the twelve
upstream assertions, because the fork puts `Real ≤ UReal`, `Boolean ≤ UBoolean`, `String ≤ UString`
and `Integer ≤ UInteger` into the conformance lattice: `Boolean.allSupertypes()`,
`Integer.allSupertypes()`, `Real.allSupertypes()`, `String.allSupertypes()` and six
`Collection(...)`/`Set(...)`/`Sequence(...)`/`Bag(...)` closures each gain the corresponding uncertain
type. Only `OclAny.allSupertypes()` and `Enum.allSupertypes()` survive untouched.

**Consequence for the port.** These ten assertions cannot be "moved to a new class" the way the other
uncertainty additions to `TypeTest` can (per-predicate `isTypeOf*`/`isKindOf*` rows, `testSubtype`,
`testEquals` — all additive and movable). They are upstream assertions whose truth value changes the
moment the uncertain types join the conformance lattice. If the port adds those conformance edges,
upstream's untouched `TypeTest#testSupertype` **will fail** on 10 of its 12 assertions. The port must
pick one: (1) give the uncertain types the same lattice position as the fork and accept/handle the
upstream breakage explicitly, or (2) keep uncertain types out of the *supertype closure* of the crisp
types (conformance one-way only, if `allSupertypes()` is computed from an explicit list rather than
from `conformsTo`), leaving upstream's expectations intact. This is a lattice design decision, not a
test-placement decision — tracked as gap **G7** below and resolved as **B5** in `specification.md`.

---

## 5. Corpus census

Directory: `$T/parser/uncertainty/`. An *entry* is one expression + one `->` line, so the entry count
equals the number of lines whose trimmed form starts with `->`.

| File | Entries |
|---|---|
| `UBooleanExpression.in` | **118** |
| `UCollectionOperations.in` | **44** |
| `UIntegerExpression.in` | **692** |
| `URealExpression.in` | **573** |
| **TOTAL** | **1427** |

Expected-result type distribution across all 1427 entries:

| Suffix | Count | Suffix | Count |
|---|---|---|---|
| ` : Boolean` | 527 | ` : UBoolean` | 72 |
| ` : UReal` | 486 | ` : Real` | 21 |
| ` : UInteger` | 210 | ` : Integer` | 13 |
| ` : OclVoid` | 79 | ` : String` | 10 |
| | | ` : Set` | 4 |
| (no suffix — compile-error entries) | 5 | | |

The five compile-error entries and their exact expected text: `Value must be Boolean` (×2),
`Value must be Integer or Real`, `Uncertainty must be Integer or Real`,
`Probability must be a Integer or Real` — produced by `ASTURealLiteral.java` and
`ExpConstUBoolean.java` in the fork; the port must keep those message strings byte-identical if the
corpus is reused verbatim.

**The 79 `OclVoid` entries are load-bearing for B6.** Every one expects `-> Undefined : OclVoid`, but
`UndefinedValue.toString(StringBuilder)` prints `"Undefined"` in the fork and `"null"` in 7.5.0
(upstream `72ab8fd7`, 2019-06-27). Reusing this corpus verbatim against a 7.5.0-based port therefore
requires normalising this systematic offset in the harness (or rewriting the 79 lines) — see
`specification.md` **B6**.

One `UBooleanExpression.in` quirk: several entries carry a **commented-out** alternative expectation,
e.g. `UBoolean(true or false, 3 - 5) -> Undefined : OclVoid` followed by
`# -> Probability must be a non-uncertainty number between 0 and 1`. The live expectation is
`Undefined : OclVoid`; the commented line records behaviour the authors wanted but never implemented.
Do not resurrect commented lines without a decision.

---

## 6. The U-type with no historical coverage

**`UString` has no value test and no expression corpus, and no expression-operations test either.**

* No `UStringValueTest.java` — `$T/uml/ocl/value/` holds only `URealValueTest`, `UBooleanValueTest`,
  `UIntegerValueTest` (plus upstream's own `ValueTest`).
* No `UStringExpOpsTest.java` — `$T/uml/ocl/expr/` holds `URealExpOpsTest`, `UIntegerExpOpsTest`,
  `UBooleanExpOpsTest`, `UCollectionExpOpTest`, `ExpQueryUncertaintyTest` and upstream's five.
* No `UStringExpression.in` — the corpus directory holds exactly four `.in` files, none for `UString`.
* `UString` appears in exactly one test file in the entire fork test tree: `TypeTest.java`, as
  type-lattice rows only (`testIsTypeOfUString`, `testIsKindOfUString`, the `String < UString` /
  `UString < UString` / `UString < OclAny` subtype rows, and the `UString.allSupertypes()` row).

The product source nonetheless ships `UStringValue.java` and `UStringType.java`. So **`UString` is
implemented but behaviourally unpinned**: the port has an oracle for its place in the type lattice and
nothing else. Any `UString` operation semantics ported must be justified from the `uDataTypes` library
source, not from these tests.

The same is true of **`SBoolean`** (`SBooleanValue.java`, `SBooleanType.java` exist; the only test
reference is `TypeTest`), though `SBoolean` is not one of the four U-types. `UReal`, `UInteger` and
`UBoolean` each have both a value test and a corpus.

---

## 8. Gaps and defects

**G1 — `uCount` has no oracle.** `UCollectionExpOpTest#testUCount` evaluates
`Set{2.0, UReal(2,3)}->uCount(UReal(2,3))` into a local `Value` and asserts nothing. Not in any `.in`
file either. The expected value of `uCount` is **UNVERIFIABLE** from the historical tests.

**G2 — `UInteger / UReal` is never actually tested by the method that claims to.**
`UIntegerExpOpsTest#testDivideByRWithUReal` is commented `// UInteger(-9, 0) / UReal(-9, 0)` but
contains zero `UReal` operands (48 `UIntegerValue` operands instead). The overload *does* have an
oracle — 24 entries in `UIntegerExpression.in` (`UInteger(...) / UReal(...)`) — it just is not where
the method name promises it. Do not treat `testDivideByRWithUReal` as evidence for that overload when
checking coverage.

**G3 — the compiler-harness failure message is useless.** `USECompilerUncertaintyTest`'s
`"evaluate : " + expTest` has no `toString()` on the inner `ExpressionTest`, so a failing error-path
entry reports an object-identity hash, not the expression text. The migrated JUnit 5 harness should
include the expression text in the failure message.

**G4 — the historical error-path split regex is wrong.** `sos.toString().split("\n(\r\n)")` matches
LF-CR-LF, which no platform emits; on Linux this degenerates to "compare the whole buffer".
Reproducing it literally would be reproducing a bug, not the intent.

**G5 — commented-out assertions never pinned a behaviour.** `UBooleanValueTest` (probability
range validation, `// FIXME: When It will be fixed in atenea library.`) and
`URealExpOpsTest#testURealSqrt` (`UReal(0,0).sqrt()`, `// TODO Descomentar cuando se actualice la
librería`). Whether the port should enable them depends on which `uDataTypes` build is used —
**UNVERIFIABLE** from the tests alone.

**G6 — `ExpQueryUncertaintyTest`'s confidence-range tests evaluate the wrong expression.** Both
`testUSelectCUncertaintyHigherThanOne` / `...LowerThanZero` build an `ExpUSelectC` and then evaluate
the *inner* comparison, not the `ExpUSelectC` itself. Whether `ExpUSelectC` is meant to reject an
out-of-range confidence at construction time or at evaluation time is therefore **UNVERIFIABLE** from
this test; it only proves that *something* throws.

**G7 — `testSupertype` conflict.** See §3.3 above. Not resolved by this document; flagged as a
blocking design question for the type-lattice section (resolved as `specification.md` **B5**).

**G8 — historical file iteration order is unspecified.** `File.listFiles` order is
filesystem-dependent and the fork's harness does not sort. Not observed to matter (each corpus entry
runs against a fresh `MSystem`/`VarBindings`), but the migrated harness should sort for determinism.

**G9 — no historical test was ever executed.** Nothing in this document was produced by *running* the
fork's tests; Maven is off-limits and the fork is an Ant/Java-1.7/JUnit-3 tree. Every claim here is a
static reading of the sources. Whether all 182 methods actually **passed** against
`lib/atenearesearchgroup.uncertainty.jar` is **UNVERIFIABLE**.
