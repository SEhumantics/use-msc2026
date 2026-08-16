# 14 — Historical Test Inventory

Section of the port-2 specification. Subject: the ten test artefacts that the uncertainty fork
carries, which are the port's primary behavioural oracle.

**Reference root (READ-ONLY)**
`/home/xoruser/msc-4/use-msc2026/.git/reference-repositories/uncertainty/USE-Uncertainty`
— abbreviated `$FORK` below. Test root `$FORK/src/test/org/tzi/use` — abbreviated `$T`.

Every count in this document is reproducible with the commands in §7. Nothing here is taken from
the earlier port on `origin/main`; the only file read outside `$FORK` is the target repo's own
upstream `use-core/src/test/java/org/tzi/use/uml/ocl/type/TypeTest.java`, which was first verified
to contain **zero** uncertainty references (`grep -c -iE 'UReal|UInteger|UBoolean|UString|SBoolean'`
→ `0`) and is therefore pristine USE 7.5.0, not port output.

---

## 0. Summary

| # | File (relative to `$T`) | JUnit flavour | Test methods | Assertion calls |
|---|---|---|---|---|
| 1 | `uml/ocl/value/URealValueTest.java` | JUnit 3 (`extends junit.framework.TestCase`) | 5 | 54 |
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

Every one of the ten classes is JUnit 3: `import junit.framework.TestCase;` +
`public class X extends TestCase`. There is not a single `@Test` annotation anywhere in
`$FORK/src/test`. `setUp()` is declared `protected void setUp()` (JUnit 3 override, no
`@BeforeEach`). Suites are wired by hand through `AllTests.suite()` /
`TestSuite.addTestSuite(...)`:

* `$T/uml/ocl/value/AllTests.java` L40-42 adds `URealValueTest`, `UBooleanValueTest`,
  `UIntegerValueTest` (alongside upstream `ValueTest`).
* `$T/uml/ocl/expr/AllTests.java` L43,46-49 adds `URealExpOpsTest`, `UBooleanExpOpsTest`,
  `UIntegerExpOpsTest`, `UCollectionExpOpTest`, `ExpQueryUncertaintyTest`.
* `$T/uml/ocl/type/AllTests.java` L39 adds `TypeTest` (unchanged wiring — the uncertainty rows
  live *inside* upstream's file).
* `$T/parser/uncertainty/AllTests.java` L17 adds `USECompilerUncertaintyTest`.

Port note: all 182 methods migrate to JUnit 5 Jupiter — `@Test` per method, `@BeforeEach` for
`setUp()`, `Assertions.assertEquals(expected, actual, message)` (JUnit 3 puts the message
**first**; every call site must have its argument order rewritten), and `AllTests` suites are
dropped in favour of surefire discovery.

---

## 1. Per-class inventory

### 1.1 `uml/ocl/value/URealValueTest.java` — JUnit 3, 5 methods

Direct unit tests of `org.tzi.use.uml.ocl.value.URealValue` (no evaluator, no model).

| L | Method | Behaviour pinned |
|---|---|---|
| 12 | `testType` | `new URealValue(5,5).type()` equals `TypeFactory.mkUReal()`. |
| 18 | `testValues` | 18-row table (L23-42): constructor normalises uncertainty to `Math.abs(u)`; `value()` returns the double unchanged; `toString(StringBuilder)` renders exactly `UReal(<v>, <u>)` — covers positive / zero / negative value × positive / zero / negative uncertainty, integral and fractional (`5.556`, `0.593`). |
| 56 | `testIsTypeOf` | The 14-way `isXxx()` predicate vector on a `URealValue`: only `isUReal()` is true; `isBag/isCollection/isSequence/isSet/isOrderedSet/isBoolean/isInteger/isReal/isUnlimitedNatural/isObject/isLink/isUBoolean/isUInteger` are all false. Note `isReal()` is **false** for a `URealValue`. |
| 74 | `testCompareTo` | `compareTo` semantics: interval-overlap ordering. Equal-value → 0; disjoint intervals → ±1; **overlapping intervals compare equal (0)** — e.g. `UReal(0,2).compareTo(UReal(0,1)) == 0`, `UReal(0,2).compareTo(UReal(1,1)) == 0`, `UReal(0,2).compareTo(UReal(-1,1)) == 0`, while `UReal(0,2)` vs `UReal(5,2)` → -1. Also pins `compareTo` against `RealValue` (L172-205) and `IntegerValue` (L210-243). |
| 248 | `testIdentical` | `equals` is *exact* (value **and** uncertainty), unlike `compareTo`: `UReal(-2,3).equals(UReal(-2,3))` true, any differing component false. Against `RealValue`/`IntegerValue`, equality holds **only when uncertainty is 0** (`UReal(-2,0).equals(RealValue(-2))` true; `UReal(-2,3).equals(RealValue(-2))` false). Against `StringValue` → false. |

### 1.2 `uml/ocl/value/UIntegerValueTest.java` — JUnit 3, 3 methods

| L | Method | Behaviour pinned |
|---|---|---|
| 12 | `testValues` | 12-row table (L18-31): `UIntegerValue(int, double)` keeps the int value, normalises uncertainty to `Math.abs(u)` (uncertainty stays a **double**, e.g. `5.53`, `5.223`), and renders `UInteger(<int>, <double>)`. |
| 44 | `testType` | `new UIntegerValue(5,5).type()` equals `TypeFactory.mkUInteger()`. |
| 50 | `testIsTypeOf` | Predicate vector: only `isUInteger()` true; in particular `isInteger()` **and** `isUReal()` are both false. |

### 1.3 `uml/ocl/value/UBooleanValueTest.java` — JUnit 3, 3 methods

| L | Method | Behaviour pinned |
|---|---|---|
| 7 | `testValues` | The canonicalisation rule: a `UBooleanValue` is always stored as `(true, p)`. `UBooleanValue.FALSE` is `UBoolean(true, 0.0)`, `UBooleanValue.TRUE` is `UBoolean(true, 1.0)`, `valueOf(false, 0.2)` becomes `UBoolean(true, 0.8)` (probability complemented), `valueOf(false, 1)` becomes `UBoolean(true, 0.0)`. `value()` therefore always returns `true`. Two out-of-range-probability blocks (L11-17, L36-42) are **commented out** with `// FIXME: When It will be fixed in atenea library.` — i.e. rejecting `p < 0` / `p > 1` was historically *not* pinned. |
| 61 | `testIsTypeOf` | Predicate vector on `UBooleanValue.FALSE`: only `isUBoolean()` true; `isBoolean()` false. |
| 79 | `testEquals` | Documented equivalence-partition table (L80-94) over `(type, nullable, value, probability)`. Pins: `equals(null)` false; `FALSE.equals(FALSE)` true with equal `hashCode`; `FALSE.equals(valueOf(false,1))` **true** with equal `hashCode` (canonicalisation makes them the same value); every other combination false with unequal `hashCode`; `equals(StringValue)` false. |

### 1.4 `uml/ocl/expr/URealExpOpsTest.java` — JUnit 3, 32 methods

Harness (L19-22): `state = new MSystem(new ModelFactory().createModel("Test")).state();`,
`e = new Evaluator();`. Every method builds `Expression[] args`, calls
`ExpStdOp.create("<op>", args)` and asserts on `e.eval(op, state)`. Operands are built from
`ExpConstUReal(ExpConstReal|ExpConstInteger, ExpConstReal)`, `ExpConstReal`, `ExpConstInteger`,
`ExpConstString`.

| L | Method | Behaviour pinned |
|---|---|---|
| 29 | `testURealToInteger` | `toInteger : UReal -> Integer` — truncates the value, discards uncertainty. |
| 73 | `testURealToReal` | `toReal : UReal -> Real` — returns the value component, discards uncertainty. |
| 118 | `testURealToString` | `toString : UReal -> String` — the literal text `UReal(-2.0, 2.0)` etc. |
| 162 | `testURealAbs` | `abs : UReal -> UReal` — `|value|`, uncertainty unchanged. |
| 186 | `testURealNeg` | `neg : UReal -> UReal` — sign flip on the value, uncertainty unchanged. |
| 210 | `testURealFloor` | `floor : UReal -> UReal` — result stays UReal (not Integer); uncertainty unchanged. |
| 250 | `testURealRound` | `round : UReal -> UReal` — 2.5 → 3, uncertainty unchanged. |
| 283 | `testURealPower` | `power : UReal x (Integer|Real) -> UReal` — 16 cases; `x.power(0)` is **Undefined when x.value == 0**, `UReal(3,0).power(0) = UReal(1,0)`; negative exponents; uncertainty propagation, e.g. `UReal(2,4).power(4) = UReal(400,128)`, `UReal(1,2).power(0.25) = UReal(1,0.5)`. |
| 420 | `testURealSqrt` | `sqrt : UReal -> UReal` — `UReal(-3,2.3)` and `UReal(0,2)` → Undefined; `UReal(4,0)` → `UReal(2.0,0.0)`; `UReal(4,2)` → `UReal(2.0,0.5)`. The `UReal(0,0).sqrt()` case is **commented out** at L432 with `// TODO Descomentar cuando se actualice la librería` — it is the only commented-out assertion in the entire fork uncertainty test set. |
| 455 | `testURealValue` | `value : UReal -> Real`. |
| 476 | `testURealUncertainty` | `uncertainty : UReal -> Real` — returns `|u|` (`UReal(3,-2.3).uncertainty() = 2.3`). |
| 493 | `testURealInv` | `inv : UReal -> UReal` — `UReal(8,0.75).inv() = UReal(0.125, 0.01171875)`; `UReal(0,0.5).inv()` Undefined. |
| 510 | `testURealxURealMinMax` | `min`/`max : UReal x UReal -> UReal`, 37 `min` + 37 `max` create-calls across this and the next three methods. |
| 662 | `testURealxIntegerMinMax` | `min`/`max : UReal x Integer`, asserted in **both argument orders** (`args` and `args_inv`). |
| 751 | `testURealxRealMinMax` | `min`/`max : UReal x Real`, both argument orders. (Javadoc L748 mislabels it "UReal x Integer".) |
| 841 | `testURealxOtherMinMax` | `min`/`max` with an Undefined operand (`3 / 0`) → Undefined; with a `String` operand → exception at `ExpStdOp.create` time. |
| 920 | `testUrealIdentical` | `equals : UReal x UReal -> Boolean` (plain Boolean, not UBoolean) — true only when value *and* uncertainty match. |
| 962 | `testAddURealxUReal` | `+ : UReal x UReal -> UReal` with uncertainty propagation. |
| 1172 | `testAddURealxReal` | `+ : UReal x Real -> UReal`. |
| 1278 | `testAddURealxInteger` | `+ : UReal x Integer -> UReal`. |
| 1383 | `testMultiplyURealxUReal` | `* : UReal x UReal -> UReal`. |
| 1584 | `testMultiplyURealxReal` | `* : UReal x Real -> UReal`. |
| 1689 | `testMultiplyURealxInteger` | `* : UReal x Integer -> UReal`. |
| 1794 | `testMinusURealxUReal` | `- : UReal x UReal -> UReal`. |
| 1996 | `testMinusURealxReal` | `- : UReal x Real -> UReal`. |
| 2102 | `testMinusURealxInteger` | `- : UReal x Integer -> UReal`. |
| 2207 | `testDivisionURealxUReal` | `/ : UReal x UReal -> UReal` (incl. divide-by-zero → Undefined). |
| 2408 | `testDivisionURealxReal` | `/ : UReal x Real -> UReal`. |
| 2512 | `testDivisionURealxInteger` | `/ : UReal x Integer -> UReal`. |
| 2618 | `testSetValue` | `setValue : UReal x (Real|Integer) -> UReal` — replaces value, **keeps** uncertainty. |
| 2652 | `testURealSetUncertainty` | `setUncertainty : UReal x (Real|Integer) -> UReal` — replaces uncertainty with `|arg|` (`setUncertainty(-3)` → uncertainty 3). |
| 2690 | `testToUInteger` | `toUInteger : UReal -> UInteger` — truncates value toward zero (`-5.3` → `-5`), keeps uncertainty. |

### 1.5 `uml/ocl/expr/UIntegerExpOpsTest.java` — JUnit 3, 39 methods

Harness adds `ctx = new SimpleEvalContext(state, state, new VarBindings())` (L19) because the
constructor tests evaluate `Expression.eval(ctx)` directly. Arithmetic methods build operands with
`new ExpressionWithValue(new UIntegerValue(...))` rather than `ExpConstUInteger`.

| L | Method | Behaviour pinned |
|---|---|---|
| 22 | `testConstWithValidValues` | `ExpConstUInteger(value, uncertainty)` accepts Integer value + Integer/Real uncertainty; `toString()` renders `UInteger(-5, 0.0)`; `eval(ctx)` yields the matching `UIntegerValue`. |
| 75 | `testConstWithUndefined` | `ExpConstUInteger` with `ExpUndefined` in either slot: `toString()` is `UInteger(null, null)` and `eval` is `UndefinedValue.instance`. |
| 100 | `testConstWithWrongValues` | Construction rejects a Real value (`"Value must be Integer"`), a String value (same message) and a String uncertainty (`"Uncertainty must be Integer or Real"`) — thrown as `ExpInvalidException`, message asserted verbatim. |
| 147 | `testOpValue` | `value : UInteger -> Integer`. |
| 182 | `testOpValueUndefined` | `value` on an Undefined UInteger → Undefined. |
| 217 | `testOpSetValue` | `setValue : UInteger x Integer -> UInteger`, uncertainty preserved. |
| 255 | `testOpSetValueWrongValue` | `setValue` with `ExpUndefined` or a Real argument throws `ExpInvalidException` **at `ExpStdOp.create` time**, not at eval time. |
| 320 | `testOpUncertainty` | `uncertainty : UInteger -> Real`. |
| 355 | `testOpUncertaintyUndefined` | `uncertainty` on an Undefined UInteger → Undefined. |
| 390 | `testOpSetUncertainty` | `setUncertainty : UInteger x (Integer|Real) -> UInteger` — takes `|arg|` (`setUncertainty(-5)` → `UInteger(0, 5.0)`). |
| 473 | `testOpSetUncertaintyWithWrongArgs` | `setUncertainty` with a String or Undefined argument throws `ExpInvalidException` at create time. |
| 515 | `testToInteger` | `toInteger : UInteger -> Integer`. |
| 551 | `testToUReal` | `toUReal : UInteger -> UReal` (`UInteger(3,0.5)` → `UReal(3.0,0.5)`). |
| 596 | `testToReal` | `toReal : UInteger -> Real`. |
| 631 | `testToString` | `toString : UInteger -> String` = `'UInteger(5, 0.3)'`. |
| 669 | `testAddBetweenUInteger` | `+ : UInteger x UInteger -> UInteger`. |
| 888 | `testAddWithUReal` | `+ : UInteger x UReal -> **UReal**` (result widens). |
| 1086 | `testAddWithReal` | `+ : UInteger x Real -> **UReal**`. |
| 1187 | `testAddWithInteger` | `+ : UInteger x Integer -> UInteger` (stays UInteger). |
| 1288 | `testNeg` | `neg : UInteger -> UInteger`. |
| 1315 | `testSQRT` | `sqrt : UInteger -> ...`; negative operand → `UndefinedValue.instance`. |
| 1352 | `testABS` | `abs : UInteger -> UInteger`. |
| 1382 | `testMinusBetweenUInteger` | `- : UInteger x UInteger -> UInteger`. |
| 1579 | `testMinusUReal` | `- : UInteger x UReal -> UReal`. |
| 1776 | `testMinusWithReal` | `- : UInteger x Real -> UReal`. |
| 1877 | `testMinusWithInteger` | `- : UInteger x Integer -> UInteger`. |
| 1978 | `testMultBetweenUInteger` | `* : UInteger x UInteger -> UInteger`. |
| 2177 | `testMultWithUReal` | `* : UInteger x UReal -> UReal`. |
| 2375 | `testMultReal` | `* : UInteger x Real -> UReal`. |
| 2476 | `testMultInteger` | `* : UInteger x Integer -> UInteger`. |
| 2580 | `testDividyByRBetweenUInteger` | `/ : UInteger x UInteger -> **UReal**` (real division). |
| 2777 | `testDivideByRWithUReal` | Named for `/ : UInteger x UReal -> UReal`, but **every operand in the method is a `UIntegerValue`** — the `UInteger / UReal` overload is not actually exercised. See §8 gap G2. |
| 2974 | `testDivideByRWithReal` | `/ : UInteger x Real -> UReal`. |
| 3075 | `testDivideByRWithInteger` | `/ : UInteger x Integer -> UReal`. |
| 3178 | `testDivideByBetweenUInteger` | `div : UInteger x UInteger -> UInteger` (integer division). |
| 3375 | `testDivideByWithInteger` | `div : UInteger x Integer -> UInteger`. |
| 3479 | `testModBetweenUInteger` | `mod : UInteger x UInteger -> UInteger`. |
| 3676 | `testModWithInteger` | `mod : UInteger x Integer -> UInteger`. |
| 3779 | `testPower` | `power : UInteger x Real -> ...`; `UInteger(0,0).power(0)` → Undefined. |

### 1.6 `uml/ocl/expr/UBooleanExpOpsTest.java` — JUnit 3, 27 methods

| L | Method | Behaviour pinned |
|---|---|---|
| 25 | `testToBoolean` | `toBoolean : UBoolean -> Boolean` — the 0.5 threshold collapse (`UBoolean(true,0)` → `false`). |
| 77 | `testConfidence` | `confidence : UBoolean -> Real`. |
| 117 | `testSetConfidence` | `setConfidence : UBoolean x Real -> UBoolean`. |
| 229 | `testValue` | `value : UBoolean -> Boolean`. |
| 265 | `testEqualsCBetweenUBooleans` | `equalsC : UBoolean x UBoolean x Real -> Boolean` — confidence-tolerant equality. |
| 356 | `testEqualsCUBooleanxBoolean` | `equalsC : UBoolean x Boolean x Real -> Boolean`. |
| 417 | `testEqualsWrongConfidence` | `equalsC` with confidence `2.0` or `-0.1` evaluates to `UndefinedValue.instance` (it does **not** throw). |
| 453 | `testAndWithUBoolean` | `and : UBoolean x UBoolean -> UBoolean` (probabilistic conjunction). |
| 504 | `testAndWithBoolean` | `and` on plain Booleans stays Boolean; mixed Boolean/UBoolean cases. |
| 570 | `testAndWithUndefined` | `and` with Undefined operands, incl. short-circuit rows. |
| 648 | `testOrWithBoolean` | (misnamed) `or : UBoolean x UBoolean -> UBoolean`. |
| 696 | `testORWithBoolean` | `or` on plain Booleans / mixed. |
| 745 | `testORWithUndefined` | `or` with Undefined operands. |
| 823 | `testXORWithUBoolean` | `xor : UBoolean x UBoolean -> UBoolean`. |
| 885 | `testXORWithBoolean` | `xor` on plain Booleans / mixed. |
| 955 | `testXORWithUndefined` | `xor` with Undefined operands → Undefined. |
| 993 | `testNot` | `not : UBoolean -> UBoolean` (probability complement) and `not Undefined` → Undefined. |
| 1109 | `testImpliesWithUBoolean` | `implies : UBoolean x UBoolean -> UBoolean`. |
| 1156 | `testImpliesWithBoolean` | `implies` on plain Booleans / mixed. |
| 1206 | `testImpliesWithUndefined` | `implies` with Undefined operands. |
| 1284 | `testEquivalentWithUBoolean` | `equivalent : UBoolean x UBoolean -> UBoolean`. |
| 1331 | `testEquivalentWithBoolean` | `equivalent` on plain Booleans / mixed. |
| 1405 | `testEquivalentWithUndefined` | `equivalent` with Undefined operands. |
| 1460 | `testToBooleanC` | `toBooleanC : UBoolean x Real -> Boolean` — confidence-thresholded collapse. |
| 1554 | `testToBooleanC_invalidConfidence` | `toBooleanC` with confidence `-0.2`, `1.1`, `2` → Undefined (no throw). |
| 1628 | `testSetValue` | `setValue : UBoolean x Boolean -> UBoolean`. |
| 1679 | `testToString` | `toString : UBoolean -> String` — renders the **canonicalised** pair: `UBoolean(true,0.0).toString()` is `'UBoolean(false, 1.0)'`, `(true,0.3)` → `'UBoolean(false, 0.7)'`, `(true,0.5)` → `'UBoolean(true, 0.5)'`. |

### 1.7 `uml/ocl/expr/UCollectionExpOpTest.java` — JUnit 3, 14 methods

Fixture (L26-42): `setA = Set{UReal(2,0.5), 1, 2.5, 3.2, UReal(3.5,0.25)}` typed
`TypeFactory.mkUReal()`; `emptySet = Set{}` of the same element type.

| L | Method | Behaviour pinned |
|---|---|---|
| 48 | `testIncludeArgNonUncertainty` | `includes` with an exact Real member returns `UBooleanValue.TRUE`, i.e. an **UBoolean** even for a crisp argument. |
| 62 | `testIncludeArgUncertainty` | `setA->includes(UReal(2,0.2))` = `UBoolean(true, 0.5850213691)`. |
| 76 | `testIncludeElemWithMultipleUncertaintyMatch` | With several overlapping members, `includes` returns the **highest** probability: `Set{UReal(2,0.35),UReal(2,0.3)}->includes(UReal(2,0.29))` = `UBoolean(true, 0.9835952315)`. |
| 96 | `testIncludesAllEmptySet` | `Set{}->includesAll(Set{UReal(2,0.2)})` = `UBooleanValue.FALSE`. |
| 111 | `testIncludesAllContainingAllElements` | Exact-subset argument → `UBooleanValue.TRUE`. |
| 133 | `testIncludesAllWithOverlappingElements` | Overlapping argument → `UBoolean(true, 0.5745923526)` (conjunction of the per-element probabilities). |
| 155 | `testExcludesEmptyCollection` | `Set{}->excludes(x)` is always `UBooleanValue.TRUE`. |
| 172 | `testExcludesNonExistingValue` | Disjoint argument → `TRUE`. |
| 185 | `testExcludesExistingValue` | Exact member argument → `UBooleanValue.FALSE`. |
| 199 | `testExcludesOverlappingValue` | `setA->excludes(UReal(3,2))` = `UBoolean(true, 0.4746189139)`. |
| 214 | `testExcludesAllEmptySet` | `Set{}->excludesAll(...)` = `TRUE`. |
| 233 | `testExcludesAllSetDifferents` | Fully disjoint argument set → `TRUE`. |
| 253 | `testExcludesAllArgumentIsASubset` | Argument containing an exact member → `FALSE`. |
| 268 | `testUCount` | `uCount` is **smoke-only**: it calls `e.eval(op, state)` and asserts nothing (L278-279). Any port of `uCount` has no historical value oracle here — see §8 gap G1. |

### 1.8 `uml/ocl/expr/ExpQueryUncertaintyTest.java` — JUnit 3, 12 methods

Fixtures (L34-75): `setA` (as above), `seqB = Sequence{UReal(52,0.5), 3.2, 2, UReal(-53,20),
UReal(20,5)}`, `seqC = Sequence{UInteger(2,2.3), 3, UInteger(1,0.5)}`,
`seqWithUndefined = Sequence{UReal(2,3), Undefined}`.

| L | Method | Behaviour pinned |
|---|---|---|
| 79 | `testForAllColA` | `ExpForAll` over a UReal set with body `e1 > 0` evaluates to `UBoolean(true, 0.999968314)` — forAll returns an **UBoolean**, product of per-element probabilities. |
| 93 | `testExistsA` | `ExpExists` with body `e1 >= 3.2` → `UBooleanValue.TRUE`. |
| 108 | `testUSelectColA` | `ExpUSelect(varDecl, setA, e1 >= 2)` returns `Set{2.5, 3.2, UReal(3.5,0.25)}` — the uncertainty-aware select. |
| 128 | `testUSelectColANoMatches` | Same with `e1 >= 50` → empty `SetValue` of element type UReal. |
| 146 | `testUSelectCUncertaintyErrorType` | `new ExpUSelectC(..., new ExpConstString("testing"))` throws `ExpInvalidException` (confidence argument must be numeric). |
| 166 | `testUSelectCUncertaintyHigherThanOne` | Confidence `2` → `RuntimeException` expected. |
| 187 | `testUSelectCUncertaintyLowerThanZero` | Confidence `-2` → `RuntimeException` expected. |
| 208 | `testUSelectCColA` | `ExpUSelectC(..., 0.8)` over `setA` with `e1 >= 2` → `Set{2.5, 3.2, UReal(3.5,0.25)}`. |
| 231 | `testSum` | `sum` over `setA` = `UReal(12.2, 0.5590169944)` (uncertainties combine in quadrature). |
| 236 | `testSumSeqB` | `sum` over `seqB` = `UReal(24.2, 20.6215906273)`. |
| 241 | `testSumSeqC` | `sum` over a UInteger sequence = `UInteger(6, 2.3537204592)` — result stays UInteger. |
| 246 | `testSumSeqWithUndefined` | `sum` over a sequence containing Undefined = `UndefinedValue.instance`. |

Caveat for §8: `testUSelectCUncertaintyHigherThanOne` / `...LowerThanZero` construct the
`ExpUSelectC` and then evaluate `op` (the inner `>=` comparison), **not** the `ExpUSelectC` they
just built (L175-176, L196-197). As written they only pass if the constructor itself throws.

### 1.9 `uml/ocl/type/TypeTest.java` — JUnit 3, 46 methods

Upstream's own type test with uncertainty rows woven in. Full separation analysis in §3.
Ten of the 46 methods are wholly new uncertainty methods; the other 36 are upstream methods, of
which 34 carry appended uncertainty rows and 1 (`testSupertype`) carries **mutated** upstream
assertions.

| L | Method | Behaviour pinned |
|---|---|---|
| 72 | `testEquality` | upstream: `mkInteger()`/`mkSet(mkInteger())`/enum equality. No uncertainty content. |
| 81 | `testSubtype` | `conformsTo` lattice; 32 assertions of which 17 are uncertainty (§3.2). |
| 204 | `testSupertype` | `allSupertypes()` closures; 17 assertions: 2 untouched, 5 new, **10 mutated** (§3.3). |
| 365 | `testEquals` | upstream `EqualsTester` blocks + 4 inserted `hashCode` assertions for `UIntegerType`, `URealType`, `UBooleanType`, `SBooleanType` (§3.4). |
| 442/469 | `testIsTypeOfBag` / `testIsKindOfBag` | Bag's predicate vector; +5 / +10 uncertainty rows, all `assertFalse`. |
| 519/547 | `testIsTypeOfBooloean` / `testIsKindOfBoolean` | Boolean's vector; `isKindOfUBoolean` and `isKindOfSBoolean` are **true** for Boolean. |
| 599/627 | `testIsTypeOfSBooloean` / `testIsKindOfSBoolean` | **NEW** — SBoolean's full 23 / 44-assertion vector. |
| 677/705 | `testIsTypeOfUBooloean` / `testIsKindOfUBoolean` | **NEW** — UBoolean's vector; `isKindOfSBoolean` true, `isKindOfBoolean` false. |
| 755/783 | `testIsTypeOfCollection` / `testIsKindOfCollection` | Collection's vector; +5 / +10 false rows. |
| 833/861 | `testIsTypeOfEnum` / `testIsKindOfEnum` | Enum's vector; +5 / +10 false rows. |
| 911/939 | `testIsTypeOfClass` / `testIsKindOfClass` | Class's vector; +5 / +10 false rows. |
| 989/1016 | `testIsTypeOfInteger` / `testIsKindOfInteger` | Integer's vector; `isKindOfUReal` **and** `isKindOfUInteger` true. |
| 1066/1093 | `testIsTypeOfUInteger` / `testIsKindOfUInteger` | **NEW** — UInteger's vector. |
| 1143/1170 | `testIsTypeOfAssociation` / `testIsKindOfAsociation` | Association's vector; +5 / +10 false rows. |
| 1220/1247 | `testIsTypeOfOclAny` / `testIsKindOfOclAny` | OclAny's vector; +5 / +10 false rows. |
| 1297/1324 | `testIsTypeOfOrderedSet` / `testIsKindOfOrderedSet` | OrderedSet's vector; +5 / +10 false rows. |
| 1374/1401 | `testIsTypeOfUReal` / `testIsKindOfUReal` | **NEW** — UReal's vector. |
| 1451 | `testIsKindOfReal` | Real's kind vector; `isKindOfUReal` **true** in both VoidHandling modes. |
| 1501 | `testIsTypeOfReal` | Real's type vector; +5 false rows. |
| 1528/1555 | `testIsTypeOfSequence` / `testIsKindOfSequence` | Sequence's vector; +5 / +10 false rows. |
| 1605/1632 | `testIsTypeOfSet` / `testIsKindOfSet` | Set's vector; +5 / +10 false rows. |
| 1682/1709 | `testIsTypeOfString` / `testIsKindOfString` | String's vector; `isKindOfUString` **true**. |
| 1761/1788 | `testIsTypeOfUString` / `testIsKindOfUString` | **NEW** — UString's vector. |
| 1838/1865 | `testIsTypeOfTupleType` / `testIsKindOfTupleType` | Tuple's vector; +5 / +10 false rows. |
| 1915/1942 | `testIsTypeOfUnlimitedNatural` / `testIsKindOfUnlimitedNatural` | UnlimitedNatural's vector; +5 / +10 false rows. |
| 1992/2019 | `testIsTypeOfVoidType` / `testIsKindOfVoidType` | VoidType's vector; the `INCLUDE_VOID` block asserts `isKindOfUReal/UBoolean/UInteger/UString/SBoolean` **true** for Void (L2062-2066). |

### 1.10 `parser/uncertainty/USECompilerUncertaintyTest.java` — JUnit 3, 1 method

One `testUncertaintyExpression()` that walks every `*.in` file in its own package directory and
runs 1427 compile-and-evaluate assertions. Full harness spec in §4.

---

## 2. Operation coverage — the sets S4-S7 must check against

Derived mechanically from `ExpStdOp.create("<name>", …)` occurrences (see §7 command R4) plus the
expression classes constructed directly.

**`URealExpOpsTest`** — 22 operations:
`+`, `-`, `*`, `/`, `abs`, `equals`, `floor`, `inv`, `max`, `min`, `neg`, `power`, `round`,
`setUncertainty`, `setValue`, `sqrt`, `toInteger`, `toReal`, `toString`, `toUInteger`,
`uncertainty`, `value`
Expression node used: `ExpConstUReal`.

**`UIntegerExpOpsTest`** — 18 operations:
`+`, `-`, `*`, `/`, `abs`, `div`, `mod`, `neg`, `power`, `setUncertainty`, `setValue`, `sqrt`,
`toInteger`, `toReal`, `toString`, `toUReal`, `uncertainty`, `value`
Expression nodes: `ExpConstUInteger`, `ExpUndefined`, `ExpressionWithValue`.
**No `min`/`max`, no `inv`, no `equals`, no `floor`/`round`, no `toUInteger`.**

**`UBooleanExpOpsTest`** — 14 operations:
`and`, `confidence`, `equalsC`, `equivalent`, `implies`, `not`, `or`, `setConfidence`, `setValue`,
`toBoolean`, `toBooleanC`, `toString`, `value`, `xor`
Expression node: `ExpConstUBoolean`.

**`UCollectionExpOpTest`** — 5 operations:
`excludes`, `excludesAll`, `includes`, `includesAll`, `uCount` (`uCount` unasserted).

**`ExpQueryUncertaintyTest`** — 2 std ops + 4 expression node types:
`sum`, plus the comparison ops `>`/`>=` used as loop bodies; node types
`ExpForAll`, `ExpExists`, `ExpUSelect`, `ExpUSelectC`.

**Value-level API covered by the three `*ValueTest` classes** (not `ExpStdOp` names):
`URealValue`: ctor, `value()`, `uncertainty()`, `type()`, `toString(StringBuilder)`, `compareTo`,
`equals`, and the 14 `isXxx()` predicates.
`UIntegerValue`: ctor, `value()`, `uncertainty()`, `type()`, `toString(StringBuilder)`, `isXxx()`.
`UBooleanValue`: `TRUE`, `FALSE`, `valueOf(boolean,double)`, `value()`, `probability()`,
`toString()`, `equals`, `hashCode`, `isXxx()`.

**Corpus operation sets** (from the four `.in` files, command R7):

| File | Named operations | Infix operators |
|---|---|---|
| `URealExpression.in` | `abs equals floor inv max min neg oclIsKindOf oclIsTypeOf power round setUncertainty setValue sqrt toBoolean toInteger toReal toString toUInteger uncertainty value` | `+ - * / < <= > >= = <>` |
| `UIntegerExpression.in` | `abs equals inv mod neg power setUncertainty setValue sqrt toBoolean toInteger toReal toString toUReal uncertainty value` | `+ - * / div < <= > >= = <>` |
| `UBooleanExpression.in` | `confidence equals equivalent toBoolean value` | `and or xor not = + - * /` |
| `UCollectionOperations.in` | `equals excludes excludesAll exists forAll including includes includesAll iterate sum toBoolean uSelect uSelectC` | `= <> < <= > >= and or` |

Union of everything the fork's tests exercise (the S4-S7 checklist):
`+ - * / div mod < <= > >= = <> and or xor not implies equivalent abs neg sqrt power floor round
inv min max value setValue uncertainty setUncertainty confidence setConfidence equals equalsC
toInteger toReal toString toUReal toUInteger toBoolean toBooleanC includes includesAll excludes
excludesAll uCount sum forAll exists iterate including uSelect uSelectC oclIsTypeOf oclIsKindOf`.

---

## 3. `TypeTest.java` — isolating the uncertainty additions

This is load-bearing: the port must leave `use-core/src/test/java/org/tzi/use/uml/ocl/type/TypeTest.java`
untouched and put the added rows in a new class. The separation below is exact.

### 3.0 Totals

Counted by balanced-paren extraction of every `assertEquals|assertTrue|assertFalse|assertNull|
assertNotNull|assertSame|fail|new EqualsTester` call (command R5):

| Bucket | Assertion calls |
|---|---|
| In the 10 wholly-new uncertainty methods (§3.1) | **332** |
| Uncertainty rows added inside upstream methods | **280** |
| Untouched upstream assertions | **849** |
| Total | **1461** |

Breakdown of the 280 added-into-upstream: 244 in the `isTypeOf*`/`isKindOf*` methods (§3.2, =
5×12 + 6×4 appended `isTypeOf` rows + 10×16 appended `isKindOf` rows) + 17 in `testSubtype` (§3.4)
+ 15 in `testSupertype` (§3.3, 5 new + 10 mutated) + 4 in `testEquals` (§3.5).

The 849 untouched figure includes the 9 `new EqualsTester(...)` constructions in `testEquals` and
the single `fail();` in `setUp()` (L67). Excluding the `EqualsTester` calls, the plain
`assert*`/`fail` count is 1452, which is the number in the §0 table.

### 3.1 The 10 wholly-new methods — move verbatim to the new class

These do not exist in USE 7.5.0 at all. Every assertion inside them is an uncertainty addition.

| Method | Fork line range | Assertions |
|---|---|---|
| `testIsTypeOfSBooloean` | **599-625** | 23 |
| `testIsKindOfSBoolean` | **627-675** | 44 |
| `testIsTypeOfUBooloean` | **677-703** | 23 |
| `testIsKindOfUBoolean` | **705-753** | 44 |
| `testIsTypeOfUInteger` | **1066-1091** | 22 |
| `testIsKindOfUInteger` | **1093-1141** | 44 |
| `testIsTypeOfUReal` | **1374-1399** | 22 |
| `testIsKindOfUReal` | **1401-1449** | 44 |
| `testIsTypeOfUString` | **1761-1786** | 22 |
| `testIsKindOfUString` | **1788-1836** | 44 |
| | | **332** |

(Method names `testIsTypeOfSBooloean` / `testIsTypeOfUBooloean` carry the fork's typo — upstream
has the same typo in `testIsTypeOfBooloean`, so it was copied.)

### 3.2 Appended rows inside upstream `isTypeOf*` / `isKindOf*` methods — mechanical, safely movable

The fork appends a fixed 5-row block at the end of each upstream `testIsTypeOfX` body and a fixed
5-row block at the end of **each** of the two `VoidHandling` blocks in each `testIsKindOfX` body.
The upstream assertions themselves are **not modified** — verified against USE 7.5.0's
`testIsTypeOfBag` (18 assertions there, 17 of which the older fork base shares; the fork's 22 =
17 + 5) and `testIsKindOfReal` (7.5.0: 18 per VoidHandling block; fork: 17 + 5 = 22 per block).

Appended `isTypeOf` block (always in this order):
`isTypeOfUReal`, `isTypeOfUBoolean`, `isTypeOfUInteger`, `isTypeOfUString`, `isTypeOfSBoolean`.
Appended `isKindOf` block:
`isKindOfUReal`, `isKindOfUBoolean`, `isKindOfUInteger`, `isKindOfUString`, `isKindOfSBoolean`
(each with the surrounding block's `VoidHandling`).

| Upstream method | Fork lines of the added block(s) | Rows | Non-`assertFalse` rows |
|---|---|---|---|
| `testIsTypeOfBag` | 462-466 | 5 | — |
| `testIsKindOfBag` | 489-493, 512-516 | 10 | — |
| `testIsTypeOfBooloean` | 539-544 | **6** | line 539 and 540 are both `assertFalse(type.isTypeOfUReal())` — a duplicated row |
| `testIsKindOfBoolean` | 567-571, 590-594 | 10 | `isKindOfUBoolean` **true**, `isKindOfSBoolean` **true** (both blocks) |
| `testIsTypeOfCollection` | 775-780 | **6** | duplicated `isTypeOfUReal` row |
| `testIsKindOfCollection` | 803-807, 826-830 | 10 | — |
| `testIsTypeOfEnum` | 853-858 | **6** | duplicated `isTypeOfUReal` row |
| `testIsKindOfEnum` | 881-885, 904-908 | 10 | — |
| `testIsTypeOfClass` | 931-936 | **6** | duplicated `isTypeOfUReal` row |
| `testIsKindOfClass` | 959-963, 982-986 | 10 | — |
| `testIsTypeOfInteger` | 1009-1013 | 5 | — |
| `testIsKindOfInteger` | 1036-1040, 1059-1063 | 10 | `isKindOfUReal` **true**, `isKindOfUInteger` **true** (both blocks) |
| `testIsTypeOfAssociation` | 1163-1167 | 5 | — |
| `testIsKindOfAsociation` | 1190-1194, 1213-1217 | 10 | — |
| `testIsTypeOfOclAny` | 1240-1244 | 5 | — |
| `testIsKindOfOclAny` | 1267-1271, 1290-1294 | 10 | — |
| `testIsTypeOfOrderedSet` | 1317-1321 | 5 | — |
| `testIsKindOfOrderedSet` | 1344-1348, 1367-1371 | 10 | — |
| `testIsKindOfReal` | 1471-1475, 1494-1498 | 10 | `isKindOfUReal` **true** (both blocks) |
| `testIsTypeOfReal` | 1521-1525 | 5 | — |
| `testIsTypeOfSequence` | 1548-1552 | 5 | — |
| `testIsKindOfSequence` | 1575-1579, 1598-1602 | 10 | — |
| `testIsTypeOfSet` | 1625-1629 | 5 | — |
| `testIsKindOfSet` | 1652-1656, 1675-1679 | 10 | — |
| `testIsTypeOfString` | 1702-1706 | 5 | — |
| `testIsKindOfString` | 1729-1733, 1752-1756 | 10 | `isKindOfUString` **true** (both blocks) |
| `testIsTypeOfTupleType` | 1858-1862 | 5 | — |
| `testIsKindOfTupleType` | 1885-1889, 1908-1912 | 10 | — |
| `testIsTypeOfUnlimitedNatural` | 1935-1939 | 5 | — |
| `testIsKindOfUnlimitedNatural` | 1962-1966, 1985-1989 | 10 | — |
| `testIsTypeOfVoidType` | 2012-2016 | 5 | — |
| `testIsKindOfVoidType` | 2039-2043, 2062-2066 | 10 | **all five INCLUDE_VOID rows are `assertTrue`** (2062-2066) |

Copy-paste defects, to be fixed rather than reproduced in the port. Four rows sit in an
`INCLUDE_VOID` block but pass `EXCLUDE_VOID`, so that mode is asserted twice and `INCLUDE_VOID`
never at all for that predicate:

| Line | Method | Bucket | Defect |
|---|---|---|---|
| 1063 | `testIsKindOfInteger` | §3.2 appended row | `isKindOfSBoolean(EXCLUDE_VOID)` inside the `INCLUDE_VOID` block |
| 1599 | `testIsKindOfSequence` | §3.2 appended row | `isKindOfUBoolean(EXCLUDE_VOID)` inside the `INCLUDE_VOID` block |
| 674 | `testIsKindOfSBoolean` | §3.1 new method | last `INCLUDE_VOID` row uses `EXCLUDE_VOID` |
| 752 | `testIsKindOfUBoolean` | §3.1 new method | last `INCLUDE_VOID` row uses `EXCLUDE_VOID` |

Plus one harmless ordering slip: L1290-1294 in `testIsKindOfOclAny` swaps the `SBoolean` and
`UString` rows relative to the standard order (both are `assertFalse`).

**Port rule:** for every upstream `testIsTypeOfX` / `testIsKindOfX`, the uncertainty content is
exactly "the same type `X`, asserted against the five new predicates". A new test class
(`UncertaintyTypeTest`) can re-derive `Type type = TypeFactory.mkX()` and assert only those five
(or ten) predicates. Upstream's file needs no edit.

### 3.3 `testSupertype` (fork L204-361) — the one place where upstream assertions are MUTATED

This is the only method where the fork changed the *expected value* of an upstream assertion
rather than adding an independent one. Cross-checked line-by-line against USE 7.5.0's
`testSupertype` (target repo `use-core/.../TypeTest.java` L135-232, 12 assertions).

| Fork lines | Subject | Classification |
|---|---|---|
| 205-208 | `OclAny.allSupertypes()` | **untouched upstream** |
| 209-217 | `SBoolean.allSupertypes()` = `{SBoolean, OclAny}` | **new** |
| 218-227 | `UBoolean.allSupertypes()` = `{UBoolean, SBoolean, OclAny}` | **new** |
| 228-237 | `Boolean.allSupertypes()` | **MUTATED** — 7.5.0 expects `{Boolean, OclAny}`; fork adds `UBoolean`, `SBoolean` |
| 238-247 | `Integer.allSupertypes()` | **MUTATED** — 7.5.0 expects `{Integer, Real, OclAny}`; fork adds `UInteger`, `UReal` |
| 248-255 | `UInteger.allSupertypes()` = `{UInteger, UReal, OclAny}` | **new** |
| 256-262 | `Real.allSupertypes()` | **MUTATED** — 7.5.0 expects `{Real, OclAny}`; fork adds `UReal` |
| 263-266 | `UReal.allSupertypes()` = `{UReal, OclAny}` | **new** |
| 267-270 | `String.allSupertypes()` | **MUTATED** — 7.5.0 expects `{String, OclAny}`; fork adds `UString` |
| 271-274 | `UString.allSupertypes()` = `{UString, OclAny}` | **new** |
| 275-278 | `Enum.allSupertypes()` | **untouched upstream** |
| 279-288 | `Collection(Boolean).allSupertypes()` | **MUTATED** — adds `Collection(UBoolean)`, `Collection(SBoolean)` |
| 289-299 | `Collection(Integer).allSupertypes()` | **MUTATED** — adds `Collection(UReal)`, `Collection(UInteger)` |
| 300-311 | `Collection(Collection(Real)).allSupertypes()` | **MUTATED** — adds `Collection(Collection(UReal))` |
| 313-328 | `Set(Integer).allSupertypes()` | **MUTATED** — adds `Collection(UReal)`, `Collection(UInteger)`, `Set(UReal)`, `Set(UInteger)` |
| 329-344 | `Sequence(Integer).allSupertypes()` | **MUTATED** — adds `Collection(UReal)`, `Collection(UInteger)`, `Sequence(UReal)`, `Sequence(UInteger)` |
| 345-360 | `Bag(Integer).allSupertypes()` | **MUTATED** — adds `Collection(UReal)`, `Collection(UInteger)`, `Bag(UReal)`, `Bag(UInteger)` |

**Consequence for the port — state it explicitly in the plan.** These ten assertions cannot be
"moved to a new class". They are upstream assertions whose truth value changes the moment the
uncertainty types join the conformance lattice (because `Real.conformsTo(UReal)`,
`Boolean.conformsTo(UBoolean)`, `String.conformsTo(UString)`, `Integer.conformsTo(UInteger)` all
become true). If the port adds those conformance edges, upstream's untouched
`TypeTest#testSupertype` **will fail** on 10 of its 12 assertions. The port must pick one:
1. give the uncertain types the same lattice position as the fork and accept/handle the upstream
   breakage explicitly, or
2. keep uncertain types out of the *supertype closure* of the crisp types (conformance one-way
   only, if `allSupertypes()` is computed from an explicit supertype list rather than from
   `conformsTo`), leaving upstream's expectations intact.
Whichever is chosen, this is a design decision, not a test-placement decision. Everything else in
`TypeTest` is additive and movable.

### 3.4 `testSubtype` (fork L81-202) — additive, movable

32 assertions; 17 uncertainty, 15 untouched upstream. USE 7.5.0's `testSubtype` (L81-133) has
exactly 13 assertions, all of which appear verbatim in the fork (`Integer < Integer`,
`Integer < Real`, and the 11 collection rows) — plus the fork adds `String < OclAny` and
`String < String`, which are upstream-flavoured but new here.

The 17 uncertainty assertions, by fork line range and label:

| Lines | Label | Lines | Label |
|---|---|---|---|
| 90-93 | `String < UString` | 126-129 | `Boolean < UBoolean` |
| 94-97 | `UString < UString` | 130-132 | `UReal < OclAny` (**see defect below**) |
| 98-101 | `UString < OclAny` | 133-135 | `UReal < UReal` |
| 102-105 | `SBoolean < OclAny` | 136-138 | `Real < UReal` |
| 106-108 | `SBoolean < SBoolean` | 139-141 | `UInteger < UInteger` |
| 110-113 | `UBoolean < SBoolean` | 142-144 | `UInteger < UReal` |
| 114-117 | `UBoolean < UBoolean` | 151-153 | `Integer < UReal` |
| 118-121 | `UBoolean < OclAny` | 154-156 | `Integer < UInteger` |
| 122-125 | `Boolean < SBoolean` | | |

Defect to fix, not reproduce: L130-132 is labelled `"UReal < OclAny"` but the body is
`TypeFactory.mkUReal().conformsTo(TypeFactory.mkUReal())` — a duplicate of L133-135. The port's
new class should assert the intended `mkUReal().conformsTo(mkOclAny())`.

The 15 untouched upstream rows are at 82-85, 86-89, 145-147, 148-150, 157-160, 161-164, 165-168,
169-172, 173-176, 177-180, 181-184, 185-188, 189-192, 193-197, 198-201.

### 3.5 `testEquals` (fork L365-440) — additive, movable

Four inserted blocks, each `declare two instances + assertEquals on hashCode` (no `EqualsTester`,
unlike the upstream blocks):

| Lines | Content |
|---|---|
| 379-382 | `UIntegerType uit1/uit2`; `assertEquals(uit1.hashCode(), uit2.hashCode())` |
| 391-394 | `URealType urt1/urt2`; `assertEquals(urt1.hashCode(), urt2.hashCode())` |
| 396-399 | `UBooleanType ubt1/ubt2`; `assertEquals(ubt1.hashCode(), ubt2.hashCode())` |
| 401-404 | `SBooleanType sbt1/sbt2`; **`assertEquals(ubt1.hashCode(), ubt2.hashCode())`** — copy-paste bug: it re-asserts the UBoolean pair, never touching `sbt1`/`sbt2` |

All upstream `EqualsTester` blocks (Boolean, Integer, Real, Set, Bag, Sequence, Collection, Enum,
Tuple) are unchanged. Note also that the fork's file imports
`com.gargoylesoftware.base.testing.EqualsTester` (L39) while USE 7.5.0 imports
`com.google.common.testing.EqualsTester` (L38 of the target file) — the port's new class should
not need `EqualsTester` at all, since the four added assertions are plain `hashCode` equalities.

### 3.6 Port recipe

Create `use-core/src/test/java/org/tzi/use/uml/ocl/type/UncertaintyTypeTest.java` (JUnit 5)
containing:
* the 10 methods of §3.1 verbatim (332 assertions), with the four `EXCLUDE_VOID`-in-`INCLUDE_VOID`
  defects corrected;
* one `testSubtypeUncertainty` holding the 17 rows of §3.4, with the L130-132 defect corrected;
* one `testSupertypeUncertainty` holding only the 5 *new* rows of §3.3 (SBoolean, UBoolean,
  UInteger, UReal, UString) — the 10 mutated rows are **not** test material, they are a design
  question routed to the type-lattice section of the spec;
* one `testTypeHashCodes` holding the 4 rows of §3.5, with `sbt1`/`sbt2` actually asserted;
* per-upstream-type predicate methods re-deriving `TypeFactory.mkX()` and asserting only the five
  new `isTypeOf*` / `isKindOf*` predicates listed in §3.2 — 244 assertions as written, 240 once the
  4 duplicated `isTypeOfUReal` rows are dropped and the 4 `EXCLUDE_VOID`-in-`INCLUDE_VOID` rows are
  corrected.

Total for the new class: 332 + 17 + 5 + 4 + 240 = **598** assertions. Upstream's `TypeTest.java`
receives **zero** edits.

---

## 4. `USECompilerUncertaintyTest` — harness specification

File: `$T/parser/uncertainty/USECompilerUncertaintyTest.java` (171 lines, JUnit 3, one test method).

### 4.1 Fixture and discovery

```java
private static boolean VERBOSE = true;                                   // L20
private static String TEST_PATH =
        System.getProperty("user.dir")
        + "/src/test/org/tzi/use/parser/uncertainty".replace('/', File.separatorChar);   // L22-24
```

`testUncertaintyExpression()` (L53-105):
1. `MModel model = new ModelFactory().createModel("Test");` — an **empty** model. No classes, no
   associations. Every corpus expression is therefore a pure literal/operator expression.
2. `File[] files = new File(TEST_PATH).listFiles(new SuffixFileFilter(".in"));` —
   `SuffixFileFilter.accept` is `pathname.getPath().endsWith(".in")`
   (`$FORK/src/main/org/tzi/use/util/SuffixFileFilter.java` L35-37). File **order is
   filesystem-dependent**; the test does not sort.
3. `StringOutputStream sos` (private inner class, L31-51: a `StringBuilder`-backed `OutputStream`
   with `reset()` and `toString()`) wrapped in `PrintWriter pw = new PrintWriter(sos);` — this is
   the compiler's error sink.
4. `Options.explicitVariableDeclarations = false;` (L61) — required, because the corpus uses
   undeclared iterator variables (`e`, `v`) and `let`.
5. `assertNotNull(files);` then a progress banner to `System.out`.

### 4.2 `.in` file format

* One **entry** = one expression followed by one expected-result line.
* Blank lines and lines whose trimmed form starts with `#` are ignored anywhere.
* The expression is the first non-blank, non-comment line. It may be continued: a line ending in a
  backslash is a continuation. The corpus writes the marker as **two** backslashes (`\\`) and the
  parser removes **two** characters (`line.substring(0, line.length()-2)`), joining with `\n`.
  Only `UCollectionOperations.in` uses continuations (14 lines).
* The expected line must be the next non-blank, non-comment line and must start with `->`.
  The expected string is `line.substring(3)` — i.e. the parser assumes exactly `"-> "` (arrow plus
  one space). Verified: all 1427 expected lines match `^-> ` exactly, none is indented.
* Two expected-value shapes:
  * a successful evaluation, written as `value : Type` — the exact output of
    `Value.toStringWithType()`, e.g. `-> UReal(12.2, 0.5590169944) : UReal`, `-> true : Boolean`,
    `-> Undefined : OclVoid`;
  * a compile failure, written as the bare error text with no ` : Type` suffix, e.g.
    `-> Value must be Boolean`. There are exactly **5** such entries in the whole corpus.
* Tabs inside an expression are replaced by spaces (`replace("\t", " ")`).
* A trailing expression with no `->` line at EOF is **silently dropped** (`readExpressionLine`
  returns `null`, ending the file). All four corpus files end on a `->` line, so nothing is lost.
* Malformed input raises `RuntimeException`: `"missing expression"` if the first line of an entry
  starts with `->`; `"missing expected result line"` if the line after an expression does not.

### 4.3 The parsing loop (verbatim, L107-148)

```java
    private ExpressionTest readExpressionLine(BufferedReader in) throws IOException {
        ExpressionTest expTest = new ExpressionTest();
        String line;
        StringBuilder expressionBuilder = new StringBuilder();

        line = in.readLine();
        while (line != null && (expTest.expression == null || expTest.expected == null)) {
            line = line.trim();

            if (line.length() != 0 && !line.startsWith("#") ) {

                if (expTest.expression == null) {

                    if (line.startsWith("->"))
                        throw new RuntimeException("missing expression");

                    if (!line.endsWith("\\")) {
                        expressionBuilder.append(line);
                        expTest.expression = expressionBuilder.toString().replace("\t", " ");
                    }
                    else
                        expressionBuilder.append(line.substring(0, line.length()-2) + "\n");

                } else {

                    if (!line.startsWith("->"))
                        throw new RuntimeException("missing expected result line");

                    expTest.expected = line.substring(3);
                }

            }

            if (expTest.expected == null)
                line = in.readLine();
        }

        if (expTest.expected == null || expTest.expression == null )
            expTest = null; // End of file

        return expTest;
    }
```

`ExpressionTest` is a private inner class with two fields, `String expression` and
`String expected` (L26-29). It has **no** `toString()` override, so the failure message
`"evaluate : " + expTest` on L90 prints an object identity hash — see §8 gap G3.

### 4.4 Compile and evaluate (L150-168)

```java
    private Value executeExpression(MModel model, PrintWriter pwErr, ExpressionTest expressionTest) {
        InputStream stream = new ByteArrayInputStream(expressionTest.expression.getBytes());
        Value result = null;
        Expression expr =
                OCLCompiler.compileExpression(model, stream, TEST_PATH, pwErr, new VarBindings());

        if (expr != null) {
            MSystemState systemState = new MSystem(model).state();
            result = new Evaluator().eval(expr, systemState);
        }
        return result;
    }
```

A **fresh** `MSystem`/`MSystemState` is built per entry. `VarBindings` is fresh per entry, so
`let`-bound names do not leak between entries. `OCLCompiler.compileExpression` returns `null` on
any parse or semantic error and calls `err.flush()` before returning
(`$FORK/src/main/org/tzi/use/parser/ocl/OCLCompiler.java` L232), so the error text is guaranteed to
have reached `sos`. Semantic errors are printed bare: `catch (SemanticException e) {
err.println(e.getMessage()); }` (L222-223) — which is why `-> Value must be Boolean` matches with
no file/line prefix.

### 4.5 Assertion and failure behaviour (L85-95)

```java
Value result = executeExpression(model, pw, expTest);

if (result == null) {
    String errArray [] = sos.toString().split("\n(\r\n)");
    String errMessage = errArray[errArray.length - 1].replace("\n", "").replace("\r","");
    assertEquals("evaluate : " + expTest, expTest.expected, errMessage);
    sos.reset();
}
else
    assertEquals("evaluate : " + expTest.expression, expTest.expected, result.toStringWithType());
```

* Success path: compare `expTest.expected` against `result.toStringWithType()` — the value's own
  `value : Type` rendering. This is what makes the corpus a *typed* oracle.
* Failure path: take the accumulated error buffer, split on the regex `\n(\r\n)` (literal LF
  followed by CRLF — on a Unix line-ending build this **never matches**, so the array has one
  element), take the last element, strip all `\n` and `\r`, and compare to the expected string.
  Then `sos.reset()`. Net effect on Linux: the *entire* concatenated error output for that entry,
  newline-stripped, must equal the expected string — so an entry that produces two error lines can
  never pass.
* `sos` is **not** reset on the success path. Since a successful compile writes nothing, this is
  harmless in practice, but a re-implementation should reset unconditionally.
* On failure the test aborts the whole run (JUnit `assertEquals` throws) — remaining entries and
  remaining files are not executed. There is no per-entry isolation.
* `VERBOSE = true` prints every expression and expected value to `System.out` before evaluating.

### 4.6 JUnit 5 re-implementation contract

* `@TestFactory Stream<DynamicTest>` (or `@ParameterizedTest` with an `ArgumentsProvider`) —
  one dynamic test per entry, so a single bad entry does not mask the other 1426.
* Locate the corpus from the classpath (`getClass().getResource(...)`) or a Maven property, not
  from `System.getProperty("user.dir")` + a hand-built relative path.
* Sort the file list for determinism.
* Keep the format exactly as specified in §4.2 so the four `.in` files can be copied byte-for-byte,
  including the `\\` continuation marker and the `-> ` (three-character) prefix.
* Reproduce the semantics of §4.5 but fix the error-path split: compare against the trimmed,
  newline-collapsed error text, and reset the buffer before every entry.
* Preserve `Options.explicitVariableDeclarations = false` for the duration of the factory, and
  restore it afterwards (JUnit 5 runs tests in a shared JVM with other suites).

---

## 5. Corpus census

Directory: `$T/parser/uncertainty/`. An *entry* is one expression + one `->` line (§4.2), so the
entry count equals the number of lines whose trimmed form starts with `->`.

| File | Bytes | Lines | Comment lines | Blank lines | Continuation lines | **Entries** |
|---|---|---|---|---|---|---|
| `UBooleanExpression.in` | 9170 | 400 | 28 | 137 | 0 | **118** |
| `UCollectionOperations.in` | 6119 | 173 | 17 | 55 | 14 | **44** |
| `UIntegerExpression.in` | 43871 | 2211 | 46 | 781 | 0 | **692** |
| `URealExpression.in` | 33561 | 1881 | 69 | 667 | 0 | **573** |
| **TOTAL** | | | | | | **1427** |

Command (see §7, R6):

```bash
cd "$FORK/src/test/org/tzi/use/parser/uncertainty"
for f in *.in; do printf '%-28s %s\n' "$f" "$(grep -cE '^[[:space:]]*->' "$f")"; done
cat *.in | grep -cE '^[[:space:]]*->'      # 1427
```

Expected-result type distribution across all 1427 entries (`grep -hE '^->' *.in | grep -oE ' : [A-Za-z]+' | sort | uniq -c`):

| Suffix | Count |
|---|---|
| ` : Boolean` | 527 |
| ` : UReal` | 486 |
| ` : UInteger` | 210 |
| ` : OclVoid` | 79 |
| ` : UBoolean` | 72 |
| ` : Real` | 21 |
| ` : Integer` | 13 |
| ` : String` | 10 |
| ` : Set` | 4 |
| (no suffix — compile-error entries) | 5 |
| **TOTAL** | **1427** |

The five compile-error entries and their exact expected text:
`Value must be Boolean` (×2), `Value must be Integer or Real`,
`Uncertainty must be Integer or Real`, `Probability must be a Integer or Real`.
These strings are produced by
`$FORK/src/main/org/tzi/use/parser/ocl/ASTURealLiteral.java` L28, L31 and
`$FORK/src/main/org/tzi/use/uml/ocl/expr/ExpConstUBoolean.java` L18, L21 —
so the port must keep those message strings byte-identical if the corpus is reused verbatim.

Note on `UBooleanExpression.in`: several entries are followed by a **commented-out** alternative
expectation, e.g.

```
UBoolean(true or false, 3 - 5)
-> Undefined : OclVoid
# -> Probability must be a non-uncertainty number between 0 and 1
```

The live expectation is `Undefined : OclVoid`; the commented line records the behaviour the
authors wanted but did not have. Do not resurrect the commented lines without a decision.

---

## 6. The U-type with no historical coverage

**`UString` has no value test and no expression corpus, and no expression-operations test either.**

Evidence (`grep -rln "UString" $FORK/src/test` — single hit):

* There is **no** `UStringValueTest.java`. `$T/uml/ocl/value/` contains only `AllTests.java`,
  `ValueTest.java`, `UBooleanValueTest.java`, `UIntegerValueTest.java`, `URealValueTest.java`.
* There is **no** `UStringExpOpsTest.java`. `$T/uml/ocl/expr/` contains
  `URealExpOpsTest`, `UIntegerExpOpsTest`, `UBooleanExpOpsTest`, `UCollectionExpOpTest`,
  `ExpQueryUncertaintyTest` and upstream's five.
* There is **no** `UStringExpression.in`. The corpus directory holds exactly four `.in` files:
  `UBooleanExpression.in`, `UCollectionOperations.in`, `UIntegerExpression.in`,
  `URealExpression.in`.
* `UString` appears in exactly one test file in the entire fork test tree:
  `$T/uml/ocl/type/TypeTest.java` — as type-lattice rows only (`testIsTypeOfUString` 1761-1786,
  `testIsKindOfUString` 1788-1836, plus the `String < UString` / `UString < UString` /
  `UString < OclAny` subtype rows and the `UString.allSupertypes()` row).

Meanwhile the product source does ship `UStringValue.java` and `UStringType.java`
(`$FORK/src/main/org/tzi/use/uml/ocl/value/`, `.../type/`). So **`UString` is implemented but
behaviourally unpinned**: the port has an oracle for its *place in the type lattice* and nothing
else. Any `UString` operation semantics ported must be justified from the `uDataTypes` library
source, not from these tests.

The same is true of **`SBoolean`** (`SBooleanValue.java`, `SBooleanType.java` exist; the only test
reference is `TypeTest`), though `SBoolean` is not one of the four U-types.
`UReal`, `UInteger` and `UBoolean` each have both a value test and a corpus.

---

## 7. Reproduction commands

All commands assume:

```bash
FORK=/home/xoruser/msc-4/use-msc2026/.git/reference-repositories/uncertainty/USE-Uncertainty
T="$FORK/src/test/org/tzi/use"
FILES="$T/uml/ocl/value/URealValueTest.java $T/uml/ocl/value/UIntegerValueTest.java \
$T/uml/ocl/value/UBooleanValueTest.java $T/uml/ocl/expr/URealExpOpsTest.java \
$T/uml/ocl/expr/UIntegerExpOpsTest.java $T/uml/ocl/expr/UBooleanExpOpsTest.java \
$T/uml/ocl/expr/UCollectionExpOpTest.java $T/uml/ocl/expr/ExpQueryUncertaintyTest.java \
$T/uml/ocl/type/TypeTest.java $T/parser/uncertainty/USECompilerUncertaintyTest.java"
```

**R1 — test-method count per class (the §0 table):**

```bash
for f in $FILES; do
  printf '%-58s %3s\n' "${f#$T/}" \
    "$(grep -cE '^[[:space:]]*(public|protected|private)?[[:space:]]*void[[:space:]]+test[A-Za-z0-9_]*[[:space:]]*\(' "$f")"
done
```

**R2 — grand total (182):**

```bash
cat $FILES | grep -cE '^[[:space:]]*(public|protected|private)?[[:space:]]*void[[:space:]]+test[A-Za-z0-9_]*[[:space:]]*\('
```

**R3 — method names with line numbers:**

```bash
grep -nE '^[[:space:]]*(public|protected|private)?[[:space:]]*void[[:space:]]+test[A-Za-z0-9_]*[[:space:]]*\(' "$T/uml/ocl/expr/URealExpOpsTest.java"
```

**R4 — operation coverage per expression test class (§2):**

```bash
for f in URealExpOpsTest UIntegerExpOpsTest UBooleanExpOpsTest UCollectionExpOpTest ExpQueryUncertaintyTest; do
  echo "== $f"
  grep -oE 'ExpStdOp\.create\([[:space:]]*"[^"]+"' "$T/uml/ocl/expr/$f.java" \
    | sed 's/.*"\(.*\)"/\1/' | sort | uniq -c | sort -rn
done
```

**R5 — TypeTest assertion buckets (§3.0). Requires python3; reads only the fork file:**

```bash
python3 - "$T/uml/ocl/type/TypeTest.java" <<'PY'
import re,sys
raw=open(sys.argv[1],encoding='utf-8').read()
# blank out string literals: labels such as "Collection(Collection(Real))).allSupertypes()"
# contain unbalanced parens and would truncate the statement scan
body=re.sub(r'"(?:[^"\\]|\\.)*"', lambda m: '"'+' '*(len(m.group(0))-2)+'"', raw)
NEW=[(599,625),(627,675),(677,703),(705,753),(1066,1091),(1093,1141),
     (1374,1399),(1401,1449),(1761,1786),(1788,1836)]
EQ=[(379,382),(391,394),(396,399),(401,404)]
call=re.compile(r'\b(assertEquals|assertTrue|assertFalse|assertNull|assertNotNull|assertSame|fail|new EqualsTester)\s*\(')
u=re.compile(r'UReal|UInteger|UBoolean|UString|SBoolean')
new=add=up=0
for m in call.finditer(body):
    i=m.end()-1; d=0
    while i<len(body):
        if body[i]=='(': d+=1
        elif body[i]==')':
            d-=1
            if d==0: break
        i+=1
    stmt=raw[m.start():i+1]; ln=1+body[:m.start()].count('\n')
    if any(a<=ln<=b for a,b in NEW): new+=1
    elif any(a<=ln<=b for a,b in EQ) or u.search(stmt): add+=1
    else: up+=1
print("new-method assertions      :",new)   # 332
print("added-into-upstream        :",add)   # 280
print("untouched upstream         :",up)    # 849
PY
```

Cross-check of the totals:

```bash
grep -oE '\b(assertEquals|assertTrue|assertFalse|assertNull|assertNotNull|assertSame|fail)[[:space:]]*\(' \
  "$T/uml/ocl/type/TypeTest.java" | wc -l          # 1452
grep -oE 'new EqualsTester[[:space:]]*\(' "$T/uml/ocl/type/TypeTest.java" | wc -l   # 9
# 1452 + 9 = 1461 = 332 + 279 + 850
```

**R6 — corpus entry census (§5):**

```bash
cd "$T/parser/uncertainty"
for f in *.in; do printf '%-28s %s\n' "$f" "$(grep -cE '^[[:space:]]*->' "$f")"; done
cat *.in | grep -cE '^[[:space:]]*->'
```

**R7 — corpus operation sets (§2 table):**

```bash
cd "$T/parser/uncertainty"
for f in *.in; do
  echo "== $f"
  grep -vE '^[[:space:]]*(#|->|$)' "$f" \
    | grep -oE '(\.|->)[a-zA-Z][a-zA-Z0-9]*[[:space:]]*\(' \
    | sed -E 's/^(\.|->)[[:space:]]*//; s/[[:space:]]*\($//' | sort -u | tr '\n' ' '; echo
done
```

**R8 — confirm every class is JUnit 3 and none uses `@Test`:**

```bash
grep -l 'junit.framework.TestCase' $FILES | wc -l    # 10
grep -c '@Test' $FILES                               # 0 for every file
```

**R9 — confirm UString/SBoolean have no test other than TypeTest (§6):**

```bash
grep -rln 'UString'   "$FORK/src/test"   # only .../uml/ocl/type/TypeTest.java
grep -rln 'SBoolean'  "$FORK/src/test"   # only .../uml/ocl/type/TypeTest.java
ls "$T/parser/uncertainty"/*.in          # exactly four files
```

**R10 — confirm the target repo's `TypeTest.java` is pristine upstream (basis of §3.3):**

```bash
grep -c -iE 'UReal|UInteger|UBoolean|UString|SBoolean' \
  /home/xoruser/msc-4/use-msc2026/use-core/src/test/java/org/tzi/use/uml/ocl/type/TypeTest.java   # 0
```

---

## 8. Gaps and defects (`UNVERIFIABLE` / to-decide)

**G1 — `uCount` has no oracle.** `UCollectionExpOpTest#testUCount` (L268-279) evaluates
`Set{2.0, UReal(2,3)}->uCount(UReal(2,3))` into a local `Value v` and asserts nothing. The expected
value of `uCount` is **UNVERIFIABLE** from the historical tests. Neither is it in any `.in` file
(`grep -c uCount $T/parser/uncertainty/*.in` → 0 in all four).

**G2 — `UInteger / UReal` is never actually tested.** `UIntegerExpOpsTest#testDivideByRWithUReal`
(L2777-2973) is commented `// UInteger(-9, 0) / UReal(-9, 0)` but contains **zero** UReal operands:

```bash
sed -n '2777,2973p' UIntegerExpOpsTest.java | grep -c 'ExpressionWithValue(new URealValue'   # 0
sed -n '2777,2973p' UIntegerExpOpsTest.java | grep -c 'ExpressionWithValue(new UIntegerValue' # 48
```

For contrast, `testAddWithUReal` (888-1085), `testMinusUReal` (1579-1775) and `testMultWithUReal`
(2177-2374) each use 24 `URealValue` operands. The gap is covered by the corpus, not by the Java
test: `grep -cE 'UInteger\([^)]*\)[[:space:]]*/[[:space:]]*UReal\(' UIntegerExpression.in` → **24**
entries (first at L1128, `UInteger(-9, 0) / UReal(-9, 0)`). So `UInteger / UReal` has an oracle —
it just is not where the method name promises it. Do not treat `testDivideByRWithUReal` as
evidence for that overload when checking coverage in S4-S7.

**G3 — the compiler-harness failure message is useless.** `"evaluate : " + expTest` (L90) has no
`toString()` on `ExpressionTest`, so a failing error-path entry reports
`evaluate : org.tzi.use...$ExpressionTest@1b6d3586`. The port must include the expression text.

**G4 — the error-path split regex is wrong.** `sos.toString().split("\n(\r\n)")` (L88) matches
LF-CR-LF, which is not a line separator any platform emits. On Linux this degenerates to "compare
the whole buffer". Reproducing it literally would be reproducing a bug; §4.5 specifies the intent.

**G5 — commented-out assertions.** `UBooleanValueTest` L11-17 and L36-42 (probability range
validation), and `URealExpOpsTest#testURealSqrt` L431-432 and following
(`// TODO Descomentar cuando se actualice la librería`). These behaviours were **never pinned**
historically. Whether the port should enable them depends on which `uDataTypes` build is used —
**UNVERIFIABLE from the tests alone**.

**G6 — `ExpQueryUncertaintyTest` confidence-range tests evaluate the wrong expression.**
L166-206: both methods build the `ExpUSelectC` and then call `e.eval(op, state)` on the inner
comparison `op`, not on the `ExpUSelectC`. Whether `ExpUSelectC` is supposed to reject an
out-of-range confidence at construction time or at evaluation time is therefore **UNVERIFIABLE**
from this test; it only proves that *something* throws.

**G7 — `testSupertype` conflict.** As set out in §3.3, 10 upstream assertions change meaning once
the uncertainty types enter the lattice. This document does not decide the resolution; it flags it
as a blocking design question for the type-lattice section.

**G8 — file iteration order.** `File.listFiles` order is unspecified. If two corpus entries ever
interact through leaked state (they should not — fresh `MSystem` and `VarBindings` per entry), the
historical result would not be reproducible. Not observed to matter, but the port should sort.

**G9 — no historical test executes.** Nothing in this document was produced by *running* the fork's
tests; Maven is off-limits and the fork is an Ant/Java 1.7/JUnit 3 tree. Every claim is a static
reading of the sources, reproducible with §7. Whether all 182 methods actually **passed** against
`lib/atenearesearchgroup.uncertainty.jar` is **UNVERIFIABLE** here.
