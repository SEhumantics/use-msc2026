# Port specification — Part 10: Value classes

Scope: the uncertainty value classes of `org.tzi.use.uml.ocl.value`, plus the minimal
behavioural changes required in the two upstream files `Value.java` and `CollectionValue.java`.

All statements below were established by reading the files named. Where a claim could not be
established from the sources in this checkout it is marked **UNVERIFIABLE**.

## 0. Path abbreviations used in every citation

| Alias | Absolute path |
|---|---|
| `FORK/` | `/home/xoruser/msc-4/use-msc2026/.git/reference-repositories/uncertainty/USE-Uncertainty/src/main/org/tzi/use/` |
| `FORKTEST/` | `/home/xoruser/msc-4/use-msc2026/.git/reference-repositories/uncertainty/USE-Uncertainty/src/test/org/tzi/use/` |
| `UDT/` | `/home/xoruser/msc-4/use-msc2026/.git/reference-repositories/uncertainty/uDataTypes/Libraries/Java/src/uDataTypes/` |
| `TARGET/` | `/home/xoruser/msc-4/use-msc2026/use-core/src/main/java/org/tzi/use/` |

The oracle jar is `lib/atenearesearchgroup.uncertainty.jar`, package `uDataTypes` (given).
`UDT/` is the *source* of that library and is used here only to state the delegate contract.
It is a reference, never a build input.

## 1. File inventory and disposition

| File in `org/tzi/use/uml/ocl/value/` | Disposition | Historical size |
|---|---|---|
| `UncertainValue.java` | **new file** | `FORK/uml/ocl/value/UncertainValue.java` 47 lines |
| `UncertainBooleanValue.java` | **new file** | 13 lines |
| `UBooleanValue.java` | **new file** | 351 lines |
| `UIntegerValue.java` | **new file** | 223 lines |
| `URealValue.java` | **new file** | 281 lines |
| `UStringValue.java` | **new file** | 206 lines |
| `SBooleanValue.java` | **new file** | 476 lines |
| `Value.java` | **edit to an upstream file** | `TARGET/uml/ocl/value/Value.java` |
| `CollectionValue.java` | **edit to an upstream file** | `TARGET/uml/ocl/value/CollectionValue.java` |
| `RealValue.java` | **edit to an upstream file** (see §11) | `TARGET/uml/ocl/value/RealValue.java` |

Verified absent from 7.5.0: `ls TARGET/uml/ocl/value/` lists no `U*Value.java`,
no `SBooleanValue.java`, no `Uncertain*Value.java`.

## 2. The 7.5.0 `Value` contract (the checklist used in every class section)

From `TARGET/uml/ocl/value/Value.java`:

| # | Member | Where | Kind |
|---|---|---|---|
| C1 | `protected Value(Type t)` | :40 | constructor that must be chained |
| C2 | `int compareTo(Value)` | via `implements Comparable<Value>` :36 | **abstract obligation** |
| C3 | `abstract StringBuilder toString(StringBuilder sb)` | :160 (also `BufferedToString` :36) | **abstract obligation** |
| C4 | `abstract int hashCode()` | :175 | **abstract obligation** |
| C5 | `abstract boolean equals(Object)` | :183 | **abstract obligation** |
| C6 | `Type type()` | :44 | inherited, non-abstract |
| C7 | `Type getRuntimeType()` | :48 | inherited, non-abstract |
| C8 | `boolean isInteger()` | :56 | inherited default `false` |
| C9 | `boolean isUnlimitedNatural()` | :64 | inherited default `false` |
| C10 | `boolean isReal()` | :72 | inherited default `false` |
| C11 | `boolean isBoolean()` | :80 | inherited default `false` |
| C12 | `boolean isDefined()` | :84 | inherited, delegates to `isUndefined()` |
| C13 | `boolean isUndefined()` | :92 | inherited default `false` |
| C14 | `boolean isCollection()` | :100 | inherited default `false` |
| C15 | `boolean isBag()` | :108 | inherited default `false` |
| C16 | `boolean isSet()` | :116 | inherited default `false` |
| C17 | `boolean isSequence()` | :124 | inherited default `false` |
| C18 | `boolean isOrderedSet()` | :132 | inherited default `false` |
| C19 | `boolean isObject()` | :140 | inherited default `false` |
| C20 | `boolean isLink()` | :148 | inherited default `false` |
| C21 | `final String toString()` | :153 | inherited, **cannot be overridden** |
| C22 | `String toStringWithType()` / `void toStringWithType(StringBuilder)` | :162 / :168 | inherited |
| C23 | `void setTypeToRuntimeType()` | :190 | inherited |

Members the historical `Value` has that 7.5.0 does **not**:
`isUInteger()` (`FORK/uml/ocl/value/Value.java:67`), `isUReal()` (:84), `isUBoolean()` (:100),
`isSBoolean()` (:108). Call these **C24–C27**; they are the subject of §10.

Confirmed by `diff -u TARGET/uml/ocl/value/Value.java FORK/uml/ocl/value/Value.java`: the only
non-comment difference is the insertion of those four predicates. Nothing was *removed* between
the historical fork and 7.5.0 in this file.

## 3. `UncertainValue` — **new file**

Source: `FORK/uml/ocl/value/UncertainValue.java`.

**Class declaration** (`:15`): `public abstract class UncertainValue extends Value`. No interfaces
beyond those inherited from `Value`, no type parameters, no other modifiers.

**Fields**: none.

**Constructors**:

| Signature | Line | Validation / normalisation |
|---|---|---|
| `protected UncertainValue(Type t)` | :17 | none; pure `super(t)` chain to C1 |

**Methods**:

| Signature | Line | Semantics |
|---|---|---|
| `public abstract UncertainBooleanValue uEquals(Value other)` | :28 | Uncertainty-aware equality; returns the *degree* to which `this` equals `other`, not a `boolean`. |
| `public UncertainBooleanValue uDistinct(Value other)` | :37 | `uEquals(other).not()` — the whole body; no null check on `other`, no short-circuit. |

**Delegate**: none. This class touches no `uDataTypes` type.

**Edge cases explicitly handled**: none. `uDistinct(null)` propagates whatever the concrete
`uEquals` does with `null` (for `UBooleanValue` that is an NPE on `other.type()`,
`FORK/.../UBooleanValue.java:278`).

**7.5.0 `Value` contract**:

| Contract member | Status |
|---|---|
| C1 constructor chain | supplied (`:17`) |
| C2 `compareTo` | **not supplied** — left abstract for subclasses. Legal because the class is abstract. |
| C3 `toString(StringBuilder)` | **not supplied** — left abstract. |
| C4 `hashCode` | **not supplied** — left abstract. |
| C5 `equals` | **not supplied** — left abstract. |
| C6–C23 | inherited unchanged |

**Adaptation work**: none beyond copying. The file compiles against 7.5.0 `Value` as-is.

## 4. `UncertainBooleanValue` — **new file**

Source: `FORK/uml/ocl/value/UncertainBooleanValue.java` (13 lines, no Javadoc).

**Class declaration** (`:5`): `public abstract class UncertainBooleanValue extends UncertainValue`.

**Fields**: none.

**Constructors**: `protected UncertainBooleanValue(Type t)` (`:7`) — pure `super(t)`, no validation.

**Methods**: `public abstract UncertainBooleanValue not()` (`:11`) — logical negation in whatever
uncertainty algebra the subclass implements. This is the single member that lets
`UncertainValue.uDistinct` (`FORK/.../UncertainValue.java:41`) work for both `UBooleanValue`
and `SBooleanValue`.

**Delegate**: none.

**Edge cases**: none.

**7.5.0 `Value` contract**: same as §3 — C1 supplied, C2–C5 still abstract, C6–C23 inherited.

**Adaptation work**: none.

## 5. `UBooleanValue` — **new file**

Source: `FORK/uml/ocl/value/UBooleanValue.java`. Delegate: `UDT/UBoolean.java`.

### 5.1 Declaration and fields

`public class UBooleanValue extends UncertainBooleanValue` (`:17`). Not `final`.

| Field | Type | Mutability | Line |
|---|---|---|---|
| `uBoolean` | `uDataTypes.UBoolean` | `private final` | :22 |
| `TRUE` | `UBooleanValue` | `public static final`, value `new UBooleanValue(true, 1)` | :27 |
| `FALSE` | `UBooleanValue` | `public static final`, value `new UBooleanValue(true, 0)` | :31 |

Note the Javadoc at `:30` says "(true, 0)" while calling the constant `FALSE`: both constants carry
`b == true`; falsity is encoded as confidence `0`, never as `b == false`. This is forced by the
delegate — see §5.5.

### 5.2 Constructors

| Signature | Visibility | Line | Validates / normalises |
|---|---|---|---|
| `UBooleanValue(UBoolean uBoolean)` | **package-private** | :42 | Throws `RuntimeException("Probability must be a non-uncertainty number between 0 and 1")` if `uBoolean.getC() < 0 \|\| > 1` (`:46-47`). Sets type via `TypeFactory.mkUBoolean()` (`:43`). |
| `UBooleanValue(boolean value, double probability)` | `private` | :59 | Delegates to `this(new UBoolean(value, probability))`; the range check therefore happens twice — once in `UBoolean(boolean,double)` (`UDT/UBoolean.java:36`, `IllegalArgumentException`) and once at `:46`. The `UBoolean` check fires **first**, so the `RuntimeException` at `:47` is unreachable through this path. |

`assert`-style static factories:

| Signature | Visibility | Line | Semantics |
|---|---|---|---|
| `static UBooleanValue valueOf(UBoolean value)` | **package-private** | :74 | Canonicalising lift: `c == 0` → the `FALSE` singleton, `c == 1` → `TRUE`, else a fresh instance. |
| `public static UBooleanValue valueOf(boolean value, double probability)` | public | :96 | If `!value`, first rewrites the pair to `(true, 1 - probability)` (`:100-103`); then `probability == 0` → `FALSE`, `== 1` → `TRUE`, else fresh. This is the only public factory that builds from raw components (both real constructors are package-private/private) and is what `ExpConstUBoolean` uses across package boundaries (`FORK/uml/ocl/expr/ExpConstUBoolean.java:47`). |
| `public static UBooleanValue valueOf(Value arg)` | public | :122 | Widening cast: returns the argument itself if `arg.isUBoolean()`; maps `BooleanValue` true/false to `TRUE`/`FALSE`; **returns `null` for anything else** (including `SBooleanValue` and `UndefinedValue`). Dereferences `arg` without a null guard. |

### 5.3 Instance methods

| Signature | Line | Semantics (one line) |
|---|---|---|
| `UBoolean getuBoolean()` (package-private) | :148 | Escape hatch handing the raw delegate to same-package code; used by `SBooleanValue.valueOf` (`SBooleanValue.java:78`) and `SBooleanValue.applyOn` (`:463`). |
| `public boolean value()` | :158 | `uBoolean.getB()` — always `true` after normalisation (§5.5). |
| `public double probability()` | :168 | `uBoolean.getC()` — the confidence in `[0,1]`. |
| `public boolean isUBoolean()` | :179 | `true`; overrides C24. |
| `public StringBuilder toString(StringBuilder sb)` | :192 | Appends `type()` + `"(" + value() + ", " + MathUtil.round(probability(), 3) + ")"`. Fork test pins the exact strings `"UBoolean(true, 0.0)"`, `"UBoolean(true, 0.5)"`, `"UBoolean(true, 1.0)"` (`FORKTEST/uml/ocl/value/UBooleanValueTest.java:22,28,32`). Satisfies C3. |
| `public int hashCode()` | :208 | Straight delegation to `UBoolean.hashCode()` = `31*(b?1:0) + bits(c)` (`UDT/UBoolean.java:161-168`). Satisfies C4. |
| `public boolean equals(Object obj)` | :224 | Identity fast path; vs `BooleanValue`: true iff (`other.isTrue() && probability()==1 && value()`) or (`other.isFalse() && probability()==0 && !value()`); vs `UBooleanValue`: `value()` equal **and** `MathUtil.round(probability(),10)` equal. Everything else → `false`. Satisfies C5. |
| `public int compareTo(Value o)` | :251 | `0` for identity; `+1` vs `UndefinedValue`; for non-Boolean/non-UBoolean falls back to `toString().compareTo(o.toString())`; otherwise compares `uBoolean.toBoolean()` (i.e. `c >= 0.5`) against the other's boolean, `true > false`. Satisfies C2. |
| `public UncertainBooleanValue uEquals(Value other)` | :275 | `FALSE` unless `other.type().isKindOfUBoolean(EXCLUDE_VOID)`; otherwise `valueOf(uBoolean.equivalent(otherUBoolean))`. |
| `private UBooleanValue assertKindOfUBoolean(Value value)` | :295 | `valueOf(value)` or `ClassCastException("A value kind of UBoolean expected")`. |

### 5.4 Wrapper (OCL operation) methods

| Signature | Line | Delegate call |
|---|---|---|
| `public BooleanValue toBoolean()` | :312 | `BooleanValue.get(uBoolean.toBoolean())` — `toBoolean()` is `c >= 0.5` (`UDT/UBoolean.java:187`). |
| `public UncertainBooleanValue not()` | :317 | `valueOf(uBoolean.not())`; `UBoolean.not()` returns `(!b, c)` which normalises to `(true, 1-c)` (`UDT/UBoolean.java:78-81` + `:11-13`). |
| `public UBooleanValue and(Value other)` | :321 | `uBoolean.and(...)`, `c1*c2` (`UDT/UBoolean.java:83`); self-`and` short-circuits to `(b, c)`. |
| `public UBooleanValue or(Value other)` | :326 | `c1+c2-c1*c2` (`UDT/UBoolean.java:95`). |
| `public UBooleanValue xor(Value other)` | :331 | `(true, \|c1-c2\|)` (`UDT/UBoolean.java:124`). |
| `public UBooleanValue equivalent(Value other)` | :336 | `xor(...).not()` (`UDT/UBoolean.java:118`). |
| `public UBooleanValue implies(Value other)` | :341 | `(1-c1)+c2-(1-c1)*c2` (`UDT/UBoolean.java:107`). |
| `public BooleanValue equalsC(Value other, double c)` | :346 | `BooleanValue.get(uBoolean.equalsC(other, c))`, i.e. `\|c1-c2\| <= 1-c` (`UDT/UBoolean.java:154-158`). Note: returns a plain `BooleanValue`. |

All eight route the argument through `assertKindOfUBoolean`, so a non-Boolean/non-UBoolean argument
throws `ClassCastException`, not a type error.

### 5.5 Delegate contract (`UDT/UBoolean.java`)

- Representation `(b: boolean, c: double)`. **Canonical form is enforced on every getter**:
  `setNormalForm()` (`:11-13`) rewrites `(false, c)` to `(true, 1-c)`, and `getB()`/`getC()`
  call it (`:59-67`). Consequence: `value()` on a `UBooleanValue` is *always* `true`.
- Range: `UBoolean(double)` (`:27`) and `UBoolean(boolean,double)` (`:35`) throw
  `IllegalArgumentException("Invalid parameters")` for `c < 0` or `c > 1`. `UBoolean(boolean)`
  (`:22`) and `UBoolean(String)` (`:42`) cannot violate it.
- `UBoolean.equals(Object)` (`:140`) is **tolerant**: `|c1-c2| < 0.001`. `UBooleanValue.equals`
  does *not* use it; it uses 10-digit rounding instead (`UBooleanValue.java:240`).
- `compareTo` (`:194`) has a 0.001 dead band and compares confidences, not truth values —
  again *not* what `UBooleanValue.compareTo` does.

### 5.6 Edge cases the code explicitly handles

| Case | Handling | Citation |
|---|---|---|
| confidence exactly 0 | canonicalised to the `FALSE` singleton | `:78-79`, `:105-106` |
| confidence exactly 1 | canonicalised to the `TRUE` singleton | `:80-81`, `:107-108` |
| confidence < 0 or > 1 | `RuntimeException` at `:46-47` (shadowed in practice by `IllegalArgumentException` from `UDT/UBoolean.java:36`) | `:46` |
| `value == false` | rewritten to `(true, 1-p)` before the singleton test | `:100-103` |
| argument is `BooleanValue` | lifted to `TRUE`/`FALSE` | `:127-134` |
| argument is neither Boolean nor UBoolean | `valueOf` returns `null`; `assertKindOfUBoolean` converts that to `ClassCastException` | `:122-138`, `:295-302` |
| comparison against `UndefinedValue` | `compareTo` returns `+1` | `:254-255` |
| `null` argument | **not handled**: `valueOf(Value)` dereferences at `:125`, `uEquals` at `:278` |
| NaN / infinity confidence | **not handled**: `Double.NaN` passes `c<0 || c>1` and reaches the delegate | `:46` |

### 5.7 Known defects to carry or fix, decided deliberately

1. **`FALSE.equals(BooleanValue.FALSE)` is `false`.** The second disjunct at `:234` requires
   `!this.value()`, but `value()` can never be `false` (§5.5). The `UBooleanValue`↔`BooleanValue`
   bridge therefore only works in the `true` direction.
2. `equals` is asymmetric with `BooleanValue.equals`, which rejects all non-`BooleanValue`
   (`TARGET/uml/ocl/value/BooleanValue.java:82-88`).
3. `hashCode` uses `Double.doubleToLongBits(c)` via the delegate, while `equals` rounds `c` to 10
   digits — two confidences that are `equals` can hash differently. The fork test only exercises
   exactly-representable confidences (`FORKTEST/.../UBooleanValueTest.java:104-141`), so it does
   not catch this.
4. `equals`/`hashCode` are also inconsistent with `BooleanValue.hashCode()` (`1231`/`1237`,
   `TARGET/.../BooleanValue.java:91-93`), so `TRUE` and `BooleanValue.TRUE` never collide in a
   hash container even when `equals` says they are equal.

### 5.8 7.5.0 `Value` contract work-list

| Contract | Status in the historical class |
|---|---|
| C1 constructor chain | supplied via `UncertainBooleanValue` → `UncertainValue` → `Value(Type)` |
| C2 `compareTo(Value)` | **supplied, same signature** (`:251`) |
| C3 `toString(StringBuilder)` | **supplied, same signature** (`:192`) |
| C4 `hashCode()` | **supplied** (`:208`) |
| C5 `equals(Object)` | **supplied** (`:224`) |
| C6–C23 | inherited unchanged; none overridden |
| C24 `isUBoolean()` | **supplied** (`:179`) — *requires the base-class predicate to exist*, see §10 |
| C25–C27 | not overridden (correctly inherits `false`) |

**External symbols this file needs that 7.5.0 does not currently have**
(each is a hard compile dependency, not a nicety):

| Symbol | Used at | Present in 7.5.0? |
|---|---|---|
| `Value.isUBoolean()` | `:179` (`@Override`) | **no** — §10 |
| `Value.isBoolean()` | `:127` | yes (`TARGET/.../Value.java:80`) |
| `TypeFactory.mkUBoolean()` | `:43` | **no** (`TARGET/uml/ocl/type/TypeFactory.java` has no `mkUBoolean`) |
| `Type.isKindOfUBoolean(VoidHandling)` | `:278` | **no** (`TARGET/uml/ocl/type/Type.java` has `isKindOfBoolean` :100 but no `isKindOfUBoolean`) |
| `Type.VoidHandling` | `:278` | yes (`TARGET/uml/ocl/type/Type.java:33`) |
| `MathUtil.round(double,int)` | `:197`, `:240` | **no** (`TARGET/util/MathUtil.java` ends at `min(boolean,int...)`; `round` exists only at `FORK/util/MathUtil.java`, added by the fork with `@author Víctor Manuel Ortiz`) |
| `uDataTypes.UBoolean` | `:3` | supplied by `atenearesearchgroup.uncertainty.jar` |

## 6. `UIntegerValue` — **new file**

Source: `FORK/uml/ocl/value/UIntegerValue.java`. Delegate: `UDT/UInteger.java`.

### 6.1 Declaration and fields

`public class UIntegerValue extends UncertainValue` (`:8`). Not `final`.

| Field | Type | Mutability | Line |
|---|---|---|---|
| `uInteger` | `uDataTypes.UInteger` | `private`, **not final** (never reassigned, but nothing forbids it) | :10 |

No constants.

### 6.2 Constructors

| Signature | Visibility | Line | Validates / normalises |
|---|---|---|---|
| `public UIntegerValue(UInteger uInteger)` | public | :12 | **Nothing.** Accepts `null` silently (every later call then NPEs). Type from `TypeFactory.mkUInteger()`. |
| `public UIntegerValue(int value, double uncertainty)` | public | :17 | Delegates to `new UInteger(value, uncertainty)`, which stores `Math.abs(u)` (`UDT/UInteger.java:19-21`). Negative uncertainty is therefore **silently absolutised**, confirmed by `FORKTEST/uml/ocl/value/UIntegerValueTest.java:22` (`{5, -5.0, "UInteger(5, 5.0)"}`). |

Static factory:

| Signature | Line | Semantics |
|---|---|---|
| `public static UIntegerValue valueOf(Value v)` | :109 | `v` itself if `isUInteger()`; `new UIntegerValue(intValue, 0)` if `isInteger()`; **`null` otherwise** (including `RealValue` and `URealValue`). |

### 6.3 Methods

| Signature | Line | Semantics |
|---|---|---|
| `public int value()` | :21 | `uInteger.getX()`. |
| `public double uncertainty()` | :25 | `uInteger.getU()`, always ≥ 0. |
| `public boolean isUInteger()` | :30 | `true`; overrides C25. |
| `public UInteger getuInteger()` | :34 | **public** raw-delegate escape hatch (contrast `UBooleanValue.getuBoolean()`, which is package-private). |
| `public UncertainBooleanValue uEquals(Value other)` | :39 | Widens `this` to a `URealValue` via `URealValue.valueOf(this)` and re-dispatches, i.e. UInteger equality *is* UReal equality. |
| `public StringBuilder toString(StringBuilder sb)` | :46 | `type() + "(" + value() + ", " + MathUtil.round(uncertainty(),10) + ")"`; exact expected strings in `FORKTEST/.../UIntegerValueTest.java:19-30`. Satisfies C3. |
| `public int hashCode()` | :57 | `hash = Double.hashCode(value()); hash *= 7 * Double.hashCode(uncertainty());` Satisfies C4 — but see §6.6.1. |
| `public boolean equals(Object obj)` | :67 | vs `UIntegerValue`: `value()` equal and both uncertainties rounded to 10 digits equal; vs `IntegerValue`: values equal **and** `uncertainty() == 0`; vs `URealValue`: delegates `obj.equals(this)`. Anything else (incl. `RealValue`) → `false`. Satisfies C5. |
| `public int compareTo(Value o)` | :94 | Dispatches to `UInteger.compareTo` for `UIntegerValue`; lifts `RealValue` via `new UInteger((int) realValue, 0)` (**truncating**); lifts `IntegerValue` via `new UInteger(int)`; for `URealValue` returns `o.compareTo(this)` **without negating**. Anything else → `0`. Satisfies C2. |
| `private UIntegerValue assertKindOfUInteger(Value value)` | :120 | `valueOf` or `RuntimeException("A value kind of UInteger expected")` (a `RuntimeException`, *not* `ClassCastException` — differs from `UBooleanValue`). |

### 6.4 Wrapper (OCL operation) methods

| Signature | Line | Delegate |
|---|---|---|
| `public UIntegerValue add(Value)` | :131 | `UInteger.add` — `u = sqrt(u1²+u2²)` (`UDT/UInteger.java:55`). |
| `public UIntegerValue minus(Value)` | :136 | `UInteger.minus`; self-subtraction is special-cased to `u = 0` (`UDT/UInteger.java:66`). |
| `public UIntegerValue mult(Value)` | :141 | `UDT/UInteger.java:72`. |
| `public UIntegerValue divideBy(Value)` | :146 | `UDT/UInteger.java:84`; four-way case split (self, scalar divisor, scalar dividend, general). |
| `public UIntegerValue mod(Value)` | :151 | `UDT/UInteger.java:149`. |
| `public URealValue divideByR(Value)` | :156 | `UDT/UInteger.java:117` — integer division that returns a **UReal**. |
| `public UIntegerValue abs()` | :161 | `UDT/UInteger.java:322`. |
| `public UIntegerValue inverse()` | :165 | `UInteger(1,0).divideBy(this)` (`UDT/UInteger.java:351`). |
| `public UIntegerValue neg()` | :169 | `UDT/UInteger.java:332`. |
| `public UIntegerValue sqrt()` | :173 | `toUReal().sqrt().toUInteger()` (`UDT/UInteger.java:347`). |
| `public UIntegerValue power(Value)` | :177 | Type-checks the exponent with `value.type().isKindOfReal(EXCLUDE_VOID)` and throws `RuntimeException("UInteger.power() : expected Real or Integer exponent value")` otherwise (`:180-181`); coerces to `float`; delegates to `UDT/UInteger.java:342`. |
| `public IntegerValue toInteger()` | :191 | `IntegerValue.valueOf(uInteger.toInteger())`. |
| `public RealValue toReal()` | :195 | `new RealValue(uInteger.toReal())`. |
| `public URealValue toUReal()` | :199 | `new URealValue(uInteger.toUReal())` — exact widening, `(x, u)` preserved (`UDT/UInteger.java:528`). |
| `public UBooleanValue lt/gt/le/ge(Value)` | :203/:208/:213/:218 | Each lifts to `UReal` inside the delegate and returns a probability-carrying `UBoolean` (`UDT/UInteger.java:436-451`). |

### 6.5 Delegate contract (`UDT/UInteger.java`)

- Representation `(x: int, u: double)`, `u` stored as `Math.abs(u)` in every constructor and in
  `setU` (`:19-21`, `:45-47`).
- **Mutable**: public `setX(int)` / `setU(double)` (`:39`, `:45`). `UIntegerValue` never calls
  them, but `getuInteger()` (`UIntegerValue.java:34`) publishes the mutable delegate, so the
  "immutable value" invariant of `org.tzi.use.uml.ocl.value` is **not** actually enforced.
- `equals(UInteger)` (`:428`) returns a **`UBoolean`**, not a `boolean`; there is **no**
  `equals(Object)` override, so `UInteger` inherits identity equality from `Object`.
  `hashcode()` (`:540`) is lower-case `c` and therefore not an override of `Object.hashCode()`.
  Both facts mean `UIntegerValue` cannot delegate `equals`/`hashCode`, which is why `:57` and
  `:67` are hand-written.
- `compareTo(UInteger)` (`:494`) is `0` if `equals(...).toBoolean()`, else `-1` if
  `lt(...).toBoolean()`, else `+1`.
- Division/mod by a zero divisor: **no guard**. `divideBy` with `r.getU()==0` reaches
  `this.getX() / r.getX()` (`:93`) → `ArithmeticException` for `x/0`. `divideByR` with a
  zero-`u` divisor reaches a `double` division (`:126`) → `Infinity`/`NaN`, no exception.
- `toUUnlimitedNatural()` (`:532`) throws `RuntimeException("Conversion error, from negative
  UInteger to UUnlimitedNatural")` for negative values — **not reachable** from `UIntegerValue`,
  which exposes no such operation.

### 6.6 Edge cases and defects

| Case | Handling |
|---|---|
| negative uncertainty | absolutised by the delegate (`UDT/UInteger.java:20`), never rejected |
| zero divisor | **unhandled**; `ArithmeticException` from `divideBy`/`mod`, `Infinity` from `divideByR` |
| `null` delegate in `UIntegerValue(UInteger)` | **unhandled** (`:12-15`) |
| exponent not Real/Integer | explicit `RuntimeException` (`:180-181`) |
| argument not Integer-kind | `valueOf` → `null` → `RuntimeException` from `assertKindOfUInteger` (`:120-127`) |
| `UndefinedValue` argument to `compareTo` | **unhandled** — falls through every branch and returns `0` (`:94-107`), violating the `Value` doc contract at `TARGET/.../Value.java:27-31` ("all values must be able of being compared with an `UndefinedValue`") |

**6.6.1 — `hashCode` does not meet its own stated goal.** The comment at `:59-60` asserts
`1 = 1.0 = UReal(1,0) = UInteger(1,0)` must hash alike. `Double.hashCode(0.0) == 0`, so for every
`u == 0` the multiply at `:62` zeroes the hash: `new UIntegerValue(1,0).hashCode() == 0` while
`IntegerValue.valueOf(1).hashCode() == Double.valueOf(1.0).hashCode()`
(`TARGET/.../IntegerValue.java:75`, matching `TARGET/.../RealValue.java:68`) and
`new URealValue(1,0).hashCode()` likewise
(`URealValue.java:58-63`, which correctly *skips* the uncertainty term when `u == 0`). The
`UIntegerValue`/`IntegerValue` hash bridge is broken; the `URealValue`/`IntegerValue` one is not.

**6.6.2 — `equals` against `URealValue` is always `false`.** `:84-86` delegates to
`URealValue.equals(UIntegerValue)`, whose branch list (`URealValue.java:72-88`) covers only
`URealValue`, `IntegerValue` and `RealValue` — a `UIntegerValue` argument falls through to
`false`.

**6.6.3 — `compareTo` is not antisymmetric across the U-types.** `:104` returns `o.compareTo(this)`
un-negated. It is masked only because `URealValue.compareTo(UIntegerValue)` itself returns `0`
(§7.6.2), so the composite answer is a constant `0`.

**6.6.4 — `Double.hashCode(double)` (`:61-62`) is a Java 8 API** while
`/home/xoruser/msc-4/use-msc2026/.git/reference-repositories/uncertainty/USE-Uncertainty/build.xml:16-17`
declares `source`/`target` `1.7`. This compiles only because `-source` constrains language
features, not the JDK API surface. Harmless on the Java 17 target.

### 6.7 7.5.0 `Value` contract work-list

| Contract | Status |
|---|---|
| C1 | supplied via `UncertainValue(Type)` |
| C2 `compareTo(Value)` | supplied, same signature (`:94`) — but see §6.6.3 and the `UndefinedValue` gap |
| C3 `toString(StringBuilder)` | supplied, same signature (`:46`) |
| C4 `hashCode()` | supplied (`:57`) — semantically wrong, §6.6.1 |
| C5 `equals(Object)` | supplied (`:67`) |
| C6–C23 | inherited unchanged |
| C25 `isUInteger()` | supplied (`:30`) — needs the base-class predicate, §10 |

**Missing 7.5.0 symbols**: `Value.isUInteger()` (`:30`), `Value.isInteger()` (present, `:114`),
`Value.isUReal()` — used indirectly through `URealValue.valueOf` — `TypeFactory.mkUInteger()`
(`:13`), `MathUtil.round(double,int)` (`:51`, `:75-76`), `uDataTypes.UInteger`.
`Type.isKindOfReal(VoidHandling)` (`:180`) **is** present in 7.5.0 (`TARGET/uml/ocl/type/Type.java:92`).

## 7. `URealValue` — **new file**

Source: `FORK/uml/ocl/value/URealValue.java`. Delegate: `UDT/UReal.java`.

### 7.1 Declaration and fields

`public class URealValue extends UncertainValue` (`:14`). Not `final`.

| Field | Type | Mutability | Line |
|---|---|---|---|
| `uReal` | `uDataTypes.UReal` | `private`, not final | :16 |

No constants.

### 7.2 Constructors

| Signature | Visibility | Line | Validates / normalises |
|---|---|---|---|
| `public URealValue(double value, double uncertainty)` | public | :18 | None of its own; `new UReal(v,u)` stores `Math.abs(u)` (`UDT/UReal.java:41-43`). Confirmed by `FORKTEST/uml/ocl/value/URealValueTest.java:26` (`{5.0, -5.0, "UReal(5.0, 5.0)"}`). |
| `public URealValue(UReal uReal)` | public | :23 | **Nothing**; accepts `null`. |

Static factory:

| Signature | Line | Semantics |
|---|---|---|
| `public static URealValue valueOf(Value value)` | :112 | `RealValue` → `(v, 0)`; `IntegerValue` → `(v, 0)`; `UIntegerValue` → `toUReal()`; `URealValue` → itself; **`null` otherwise**. This is the widest lift in the whole set — it is the one that makes `UIntegerValue.uEquals` work (`UIntegerValue.java:41`). |

### 7.3 Methods

| Signature | Line | Semantics |
|---|---|---|
| `public double value()` | :28 | `uReal.getX()`. |
| `public double uncertainty()` | :32 | `uReal.getU()`, always ≥ 0. |
| `public boolean isUReal()` | :37 | `true`; overrides C26. |
| `public StringBuilder toString(StringBuilder sb)` | :42 | `type() + "(" + round(value,10) + ", " + round(uncertainty,10) + ")"`, with an explicit **negative-zero correction** at `:45` (`value() == 0 ? 0 : value()`) so `-0.0` never prints as `-0.0`. Satisfies C3. |
| `public int hashCode()` | :56 | `Double.hashCode(value())`, then `hash*7 + Double.hashCode(uncertainty())` **only if `uncertainty() != 0`** (`:60-61`). This preserves the `1 == 1.0 == UReal(1,0)` hash bridge that `UIntegerValue` breaks. Satisfies C4. |
| `public boolean equals(Object obj)` | :67 | vs `URealValue`: both `x` and `u` rounded to 10 digits must match; vs `IntegerValue` / `RealValue`: value equal **and** `uncertainty() == 0` (exact `==`, unrounded). Everything else → `false`. Pinned by `FORKTEST/.../URealValueTest.java:245-292`. Satisfies C5. |
| `public int compareTo(Value o)` | :95 | `URealValue` → `UReal.compareTo`; `RealValue` → lift to `new UReal(v)`; `IntegerValue` → lift to `new UReal(v)`; anything else → `0`. Satisfies C2. |
| `public UncertainBooleanValue uEquals(Value other)` | :137 | `valueOf(other)`; if `null` returns `UBooleanValue.valueOf(new UBoolean(false, 1))`, which canonicalises to `(true, 0)` = `UBooleanValue.FALSE`; otherwise `uReal.uEquals(otherUReal)` — the Gaussian-overlap probability from `UDT/UReal.java:551-554`. |
| `private URealValue assertKindOfUReal(Value value)` | :156 | `valueOf` or `RuntimeException("A value kind of UReal expected")`. |

### 7.4 Wrapper (OCL operation) methods

All take `Value`, run it through `assertKindOfUReal`, and re-wrap the `UReal` result.

| Signature | Line | Delegate (`UDT/UReal.java`) |
|---|---|---|
| `add` :168 / `minus` :173 / `divideBy` :178 / `mult` :183 | | `:76` / `:83` / `:106` / `:91` |
| `min` :188 / `max` :193 | | `:628` / `:632`, both decided by the fuzzy `lt`/`gt` |
| `sin` :198 / `cos` :202 / `tan` :206 / `asin` :210 / `acos` :214 / `atan` :218 | no-arg | `:179` / `:186` / `:193` / `:212` / `:204` / `:197` |
| `inverse` :222 / `floor` :226 / `round` :230 / `abs` :234 / `neg` :238 / `sqrt` :242 | no-arg | `:220` / `:224` / `:228` / `:137` / `:144` / `:163` |
| `public URealValue power(float value)` :246 | **takes a raw `float`, not a `Value`** — unlike every sibling | `:151` |
| `public RealValue toReal()` :250 / `public IntegerValue toInteger()` :254 | | `:652` (returns `x`) / `:648` (`(int) Math.floor(x)`) |
| `public UIntegerValue toUInteger()` :258 | | **does not use** `UReal.toUInteger()`; builds `new UIntegerValue((int) value(), uncertainty())`, i.e. C-style truncation with the uncertainty copied verbatim, whereas the library's `toUInteger` (`:656`) floors and *inflates* `u` by the truncation residual. A deliberate divergence from the delegate. |
| `public UBooleanValue lt/gt/le/ge(Value)` :262/:267/:272/:277 | | `:560` / `:570` / `:565` / `:576` — all built on the crossing-point integration in `calculate` (`:472`). |

### 7.5 Delegate contract (`UDT/UReal.java`)

- Representation `(x: double, u: double)`; `u` absolutised in `UReal(double,double)` (`:41`) and
  `setU` (`:67`). **Mutable** via public `setX`/`setU` (`:61`, `:67`).
- `equals(UReal)` (`:340`) is an **overload, not an override** — there is no `equals(Object)`.
  `hashcode()` (`:687`) is lower-case. `URealValue` therefore hand-writes both, as expected.
- `uEquals` (`:551`) returns `new UBoolean(true, r.eq)` where `r.eq` comes from `calculate`
  (`:472-549`), a five-way case split: both `u == 0` (exact real comparison), one `u == 0`
  (degenerate/CNDF), equal `u`s (single crossing), unequal `u`s (two crossings).
- `sqrt` explicitly special-cases `x == 0 && u == 0` → `(0,0)` (`:165-168`); otherwise
  `u/(2*sqrt(x))` produces `Infinity` at `x == 0` with `u != 0` and `NaN` for `x < 0`.
- `acos`/`asin` explicitly special-case `|x| == 1` to avoid division by zero (`:207`, `:215`).
- `divideBy` (`:106`) special-cases self-division to `(1, 0)` and each scalar side; **no zero-divisor
  guard** — `x/0.0` yields `Infinity`, not an exception, because the arithmetic is `double`.
- `calculate` (`:352`) guards `Double.isNaN(S)` and falls back to the degenerate comparison.
- `compareTo` (`:622`) is `0` when `equals`, `-1` when `lt(...).toBoolean()`, else `+1`.

### 7.6 Edge cases and defects

| Case | Handling |
|---|---|
| negative uncertainty | absolutised by the delegate (`UDT/UReal.java:42`) |
| negative zero value | explicitly corrected in `toString` (`:45`) |
| `x == 0 && u == 0` under `sqrt` | explicitly handled in the delegate (`UDT/UReal.java:165`) |
| `\|x\| == 1` under `asin`/`acos` | explicitly handled in the delegate (`:207`, `:215`) |
| NaN separation factor in the fuzzy comparison | explicitly handled (`UDT/UReal.java:352`, `:316` comment) |
| divisor zero | **unhandled**; IEEE `Infinity`/`NaN` propagates into a `URealValue` |
| non-numeric argument | `valueOf` → `null` → `RuntimeException` from `assertKindOfUReal` (`:156-163`) |
| `uEquals` with a non-numeric argument | explicit: yields `UBooleanValue.FALSE` (`:142-143`) |
| `null` argument | **unhandled**; `valueOf` dereferences at `:115` |
| `UndefinedValue` in `compareTo` | **unhandled**; returns `0` (`:95-110`) |

**7.6.1 — dead branch.** `:104` `else if (o instanceof URealValue)` is unreachable: the same test
already fired at `:98`. Its body (`:105-106`) is also self-referential nonsense — it shadows
`uReal` with the *argument* and compares the argument to itself. Delete on port; there is no
behaviour to preserve.

**7.6.2 — `compareTo(UIntegerValue)` returns `0` unconditionally**, because `UIntegerValue`
matches none of the three live branches. Combined with §6.6.3 this makes `URealValue` and
`UIntegerValue` mutually incomparable-but-equal for sorting purposes, which silently corrupts
`CollectionValue.getSortedElements()` (`TARGET/.../CollectionValue.java:169-173`) for mixed
collections.

### 7.7 7.5.0 `Value` contract work-list

| Contract | Status |
|---|---|
| C1 | supplied via `UncertainValue(Type)` |
| C2 `compareTo(Value)` | supplied, same signature (`:95`) |
| C3 `toString(StringBuilder)` | supplied, same signature (`:42`) |
| C4 `hashCode()` | supplied (`:56`) |
| C5 `equals(Object)` | supplied (`:67`) |
| C6–C23 | inherited unchanged |
| C26 `isUReal()` | supplied (`:37`) — needs the base-class predicate, §10 |

**Missing 7.5.0 symbols**: `Value.isUReal()` (`:37`), `Value.isUInteger()` (`:119`),
`TypeFactory.mkUReal()` (`:20`, `:24` — note the fork declares it returning plain `Type`, not a
`URealType`: `FORK/uml/ocl/type/TypeFactory.java:93`), `MathUtil.round(double,int)` (`:48-50`,
`:77-80`), `uDataTypes.UReal`, `uDataTypes.UBoolean`.

## 8. `UStringValue` — **new file**

Source: `FORK/uml/ocl/value/UStringValue.java`. Delegate: `UDT/UString.java`.

### 8.1 Declaration and fields

`public class UStringValue extends UncertainValue` (`:8`). Not `final`.

| Field | Type | Mutability | Line |
|---|---|---|---|
| `wrapper` | `uDataTypes.UString` | `private`, not final | :10 |

No constants. **No `isUString()` predicate is defined anywhere** — neither in the class nor in the
historical `Value` (the `diff` in §2 shows only four new predicates, and `isUString` is not among
them). Type discrimination for UString goes exclusively through `instanceof` in `valueOf` (`:30-33`).

### 8.2 Constructors

| Signature | Visibility | Line | Validates / normalises |
|---|---|---|---|
| `public UStringValue(UString ustring)` | public | :12 | None; accepts `null`. |
| `public UStringValue(String str, double uncertainty)` | public | :17 | Delegates to `new UString(str, u)`, which throws `IllegalArgumentException("Invalid parameters")` for `u < 0 \|\| u > 1` — but **after** assigning both fields (`UDT/UString.java:17-22`). The second parameter is named `uncertainty` here but is a **confidence** in the delegate (`sConf`), and every accessor calls it `confidence` (`:113`); the naming is inconsistent, the semantics are "confidence". |

Static factory:

| Signature | Line | Semantics |
|---|---|---|
| `public static UStringValue valueOf(Value value)` | :27 | `StringValue` → `new UStringValue(s, 1)` (confidence 1); `UStringValue` → itself; **`null` otherwise**. Note it uses `instanceof`, not an `isUString()` predicate. |

### 8.3 Methods

| Signature | Line | Semantics |
|---|---|---|
| `private UStringValue assertKindOfUString(Value)` | :44 | `valueOf` or `RuntimeException("A value kind of UString expected")`. |
| `public UncertainBooleanValue uEquals(Value other)` | :54 | `UBooleanValue.FALSE` if `other` is not String-kind; else `UBooleanValue.valueOf(wrapper.uEquals(other.wrapper))`. |
| `public StringBuilder toString(StringBuilder sb)` | :67 | Hard-codes the literal `"UString('"` — it does **not** use `type()`, unlike all four siblings — then `string`, `"', "`, the **unrounded** `sConf`, `")"`. Satisfies C3. |
| `public int hashCode()` | :74 | `wrapper.hashCode()` = `31*string.hashCode() + bits(sConf)` (`UDT/UString.java:122-129`). Satisfies C4. |
| `public boolean equals(Object obj)` | :79 | See §8.6.1 — as written it is a constant `false`. Satisfies C5 syntactically. |
| `public int compareTo(Value o)` | :95 | `0` for identity; `+1` vs `UndefinedValue`; if `o` is **not** a `StringValue` (which includes every other `UStringValue`) → `toString().compareTo(o.toString())`; else `wrapper.getString().compareTo(valueOf(o).toString())`. See §8.6.2. Satisfies C2. |
| `public String value()` | :109 | `wrapper.getString()`. |
| `public double confidence()` | :113 | `wrapper.getsConf()`. |

### 8.4 Wrapper (OCL operation) methods

| Signature | Line | Delegate (`UDT/UString.java`) |
|---|---|---|
| `public UBooleanValue ge/lt/gt/le(Value)` | :117/:122/:127/:132 | `:220` / `:198` / `:206` / `:213` — lexicographic `String.compareTo` for the boolean, confidence = **product** of the two confidences (`calculateConf`, `:232-235`). |
| `public StringValue at(int index)` | :137 | `:147` — **1-based**; throws `IndexOutOfBoundsException("idx = " + idx)` for `idx < 1` or `idx > length`. |
| `public UStringValue uAt(int index)` | :141 | `:152` → `uSubstring(idx, idx)`. |
| `public BooleanValue toBoolean()` | :145 | `:180`, `Boolean.parseBoolean` (never throws; non-"true" → `false`). |
| `public IntegerValue toInteger()` | :149 | `:176`, `Integer.parseInt` → `NumberFormatException` on garbage, including on the empty string. |
| `public RealValue toReal()` | :153 | `:172`, `Double.parseDouble` → `NumberFormatException`/`NullPointerException`. |
| `public StringValue uToString()` | :157 | `:168`, drops the confidence. |
| `public UBooleanValue toUBoolean()` | :161 | `:187` — fuzzy parse: compares case-insensitively against `"TRUE"` then `"FALSE"` at threshold `0.5`, falling back to `(true, 0.5)` (maximal ignorance) when neither matches. |
| `public SequenceValue uCharacters()` | :165 | `:156` — builds a `SequenceValue` of `UStringValue` with element type `TypeFactory.mkUString()` (`:172`). **Empty string yields an empty sequence**, no exception. |
| `public UStringValue uConcat(Value)` | :175 | `:80` — confidences combined through the distance domain: `distToConf(d1+d2, len1+len2)`. |
| `public UBooleanValue uEqualsIgnoreCase(Value)` | :180 | `:106` — upper-cases both sides then `uEquals`. |
| `public UIntegerValue uSize()` | :185 | `:131` — `UInteger(length, length*(1-sConf))`, i.e. the length's uncertainty grows with unconfidence. |
| `public UStringValue uSubstring(int lower, int upper)` | :189 | `:88` — **1-based**, explicit `IllegalArgumentException("lower should be greater than 0")` for `lower < 1`; `upper` is *not* checked here, so `String.substring` raises `StringIndexOutOfBoundsException`. |
| `public UStringValue uToLowerCase()` / `uToUpperCase()` | :193 / :197 | `:139` / `:135` — confidence preserved verbatim. |
| `public IntegerValue indexOf(StringValue string)` | :201 | `:143` — **takes `StringValue`, not `Value`**, unlike every sibling; returns 0-based `String.indexOf`, `-1` when absent. |

### 8.5 Delegate contract (`UDT/UString.java`)

- Representation `(string: String, sConf: double)`, both `private final` (`:10-11`) — the only
  genuinely immutable delegate of the four.
- Range check on `sConf ∈ [0,1]` at `:20`, thrown *after* field assignment.
- `THRESHOLD = 0.95` (`:9`) is declared but unused by any operation reachable from `UStringValue`.
- `equals(Object)` (`:111`) and `hashCode()` (`:122`) **are** proper overrides here (unlike
  `UReal`/`UInteger`), so `UStringValue.hashCode()`'s delegation at `:74` is sound.
- `uEquals` (`:96`) returns `new UBoolean(b, conf)` where `b` is exact `String` equality and
  `conf` is the confidence product — the Levenshtein version is commented out (`:97-99`).
- `confToDist()` (`:38`) = `length * (1 - sConf)`; `distToConf(d, n)` (`:47`) = `max(1 - d/n, 0)`.

### 8.6 Defects

**8.6.1 — `equals(Object)` is unconditionally `false`.** At `:86-87`:
`wrapper.getString().equals(ustring.wrapper)` compares a `java.lang.String` against a
`uDataTypes.UString` (always `false`), and the second conjunct
`wrapper.getsConf() == wrapper.getsConf()` compares the receiver's confidence **to itself**
(always `true`, and never looks at `ustring`). Net effect: no `UStringValue` is ever `equals`
to anything, including itself — `a.equals(a)` is `false`, breaking reflexivity and therefore
every `Set`/`Bag`/`Map` containing a `UStringValue`. There is **no `UStringValueTest`** in the
fork (`FORKTEST/uml/ocl/value/` contains only `AllTests`, `UBooleanValueTest`,
`UIntegerValueTest`, `URealValueTest`, `ValueTest`), which is why this was never caught.
The port must decide: reproduce bug-for-bug, or fix to
`wrapper.equals(ustring.wrapper)` (the delegate's own `equals` at `UDT/UString.java:111` already
compares both string and confidence). Recommend **fix**, and record the divergence.

**8.6.2 — `compareTo` compares a raw string against a decorated one.** At `:103`,
`valueOf(o).toString()` returns `"UString('…', …)"` (via `:67`), so a `UStringValue` is ordered
against the *rendered* form of the `StringValue`, never against its characters. And because the
guard at `:100` tests `instanceof StringValue`, two `UStringValue`s take the
`toString().compareTo(...)` path instead — which is at least self-consistent. Only the
`UStringValue` vs `StringValue` case is wrong.

**8.6.3 — imports are clean.** `java.util.List` (`:6`) is genuinely used at `:166`
(`uCharacters`); there is nothing to prune on port.

### 8.7 7.5.0 `Value` contract work-list

| Contract | Status |
|---|---|
| C1 | supplied via `UncertainValue(Type)` |
| C2 `compareTo(Value)` | supplied, same signature (`:95`) — semantically wrong, §8.6.2 |
| C3 `toString(StringBuilder)` | supplied, same signature (`:67`) |
| C4 `hashCode()` | supplied (`:74`) |
| C5 `equals(Object)` | supplied (`:79`) — **violates the `equals` contract**, §8.6.1 |
| C6–C23 | inherited unchanged |
| C24–C27 | none overridden. **There is no `isUString()` to override** — this class is the one value type with no `is…()` discriminator. |

**Missing 7.5.0 symbols**: `TypeFactory.mkUString()` (`:13`, `:172`), `uDataTypes.UString`.
`MathUtil.round` is *not* used here. Everything else it touches (`StringValue`, `BooleanValue`,
`IntegerValue.valueOf`, `RealValue`, `SequenceValue(Type, Value[])`) exists unchanged in 7.5.0.

## 9. `SBooleanValue` — **new file**

Source: `FORK/uml/ocl/value/SBooleanValue.java`. Delegate: `UDT/SBoolean.java` (binomial
subjective-logic opinions).

### 9.1 Declaration and fields

`public final class SBooleanValue extends UncertainBooleanValue` (`:11`). **The only `final`
value class of the five.**

| Field | Type | Mutability | Line |
|---|---|---|---|
| `TRUE` | `SBooleanValue` | `public static final` = `(b=1, d=0, u=0, a=1)` | :13 |
| `FALSE` | `SBooleanValue` | `public static final` = `(b=0, d=1, u=0, a=1)` | :14 |
| `sBoolean` | `uDataTypes.SBoolean` | `private`, not final | :16 |

Note `FALSE` uses base rate `a = 1`, whereas the delegate's own `new SBoolean(false)` uses
`a = 0` (`UDT/SBoolean.java:34`). The two "false" opinions are therefore **not** `equals`
(base rates differ by 1.0, far above the 0.001 tolerance at `UDT/SBoolean.java:1530`).

### 9.2 Constructors and the Builder

| Signature | Visibility | Line | Validates |
|---|---|---|---|
| `SBooleanValue(double b, double d, double u, double a)` | **package-private** | :18 | Delegates to `new SBoolean(b,d,u,a)` which rounds each to 6 decimals and throws `IllegalArgumentException` unless `\|b+d+u-1\| <= 0.001` and all four lie in `[0,1]` (`UDT/SBoolean.java:43-53`). |
| `SBooleanValue(SBoolean sBoolean)` | **package-private** | :23 | Nothing; accepts `null`. |

Because both constructors are package-private, the **only** cross-package way to build one is
the nested `public static class Builder` (`:28-69`), which is what
`FORK/uml/ocl/expr/ExpConstSBoolean.java:49` uses.

| Builder member | Line | Semantics |
|---|---|---|
| `public Builder()` | :34 | All four components default to `0`. |
| `belief(double)` / `disbelief(double)` / `uncertainty(double)` / `agent(double)` | :37/:42/:47/:52 | Fluent setters; no validation. `agent` is the base rate `a`. |
| `public SBooleanValue build()` | :57 | Canonicalises `(1,0,0,1)` → the `TRUE` singleton and `(0,1,0,1)` → `FALSE`; otherwise `new SBooleanValue(b,d,u,a)`. **A default-constructed `Builder().build()` throws `IllegalArgumentException`** because `0+0+0 ≠ 1`. |

Static factories:

| Signature | Line | Semantics |
|---|---|---|
| `public static SBooleanValue valueOf(Value value)` | :71 | `SBooleanValue` → itself; `UBooleanValue` → `new SBooleanValue(new SBoolean(ub.getuBoolean()))`, the type-embedding at `UDT/SBoolean.java:80` (`b = c`, `d = 1-c`, `u = 0`, `a = c`); `BooleanValue` → `TRUE`/`FALSE`; **`null` otherwise**. Requires same-package access to `UBooleanValue.getuBoolean()`. |
| `private static SBooleanValue valueOf(SBoolean)` | :90 | Plain wrap, no canonicalisation (asymmetric with `Builder.build()`). |
| `public static SBooleanValue assertKindOfSBoolean(Value)` | :155 | `valueOf` or `RuntimeException("A value kind of SBoolean expected")`. **`public static`** here, unlike the `private` `assertKindOf…` in the other four classes. |
| `public static SBooleanValue createDogmaticOpinion(Value projection, Value baseRate)` | :283 | Coerces both through `RealValue.valueOf` then `UDT/SBoolean.java:210`, which throws `IllegalArgumentException` if either is outside `[0,1]`. |
| `public static SBooleanValue createVacuousOpinion(Value projection)` | :289 | `UDT/SBoolean.java:222` — `(0, 0, 1, p)`; throws if `p ∉ [0,1]`. |

### 9.3 Core methods

| Signature | Line | Semantics |
|---|---|---|
| `public boolean isSBoolean()` | :95 | `true`; overrides C27. |
| `public UncertainBooleanValue uEquals(Value other)` | :100 | `SBooleanValue.FALSE` unless `other.type().isKindOfSBoolean(EXCLUDE_VOID)`; else `valueOf(sBoolean.equivalent(other))`. |
| `public UncertainBooleanValue uDistinct(Value other)` | :112 | **Overrides** `UncertainValue.uDistinct` (`FORK/.../UncertainValue.java:37`): returns `SBooleanValue.TRUE` for non-SBoolean arguments; else `xor` rather than `equivalent().not()`. |
| `public StringBuilder toString(StringBuilder sb)` | :124 | `type() + "(" + round(b,3) + ", " + round(d,3) + ", " + round(u,3) + ", " + round(a,3) + ")"` — four components, rounded to 3 digits. Satisfies C3. |
| `public int hashCode()` | :133 | Delegates to `SBoolean.hashCode()` = a decimal-shifted sum of the four components ×100 (`UDT/SBoolean.java:1547-1552`). Satisfies C4. |
| `public boolean equals(Object obj)` | :138 | Identity fast path; `false` for anything that is not an `SBooleanValue` (so **no** `BooleanValue` or `UBooleanValue` bridge, unlike `UBooleanValue.equals`); otherwise `SBoolean.equals` with a 0.001 tolerance per component (`UDT/SBoolean.java:1527-1530`). Satisfies C5. |
| `public int compareTo(Value o)` | :151 | **`return 0;` — the entire body.** Satisfies C2 syntactically and violates it semantically: every `SBooleanValue` compares equal to every `Value`, including `UndefinedValue`. `Collections.sort` in `CollectionValue.getSortedElements()` (`TARGET/.../CollectionValue.java:169-173`) will therefore leave `SBooleanValue`s in insertion order and, on a mixed collection, can throw `IllegalArgumentException: Comparison method violates its general contract!` from TimSort. **UNVERIFIABLE**: whether the fork ever sorts a mixed collection containing `SBooleanValue`s — no such test exists in `FORKTEST/`. |

### 9.4 Wrapper methods — scalar accessors and predicates

| Signature | Line | Delegate (`UDT/SBoolean.java`) |
|---|---|---|
| `public RealValue belief()` :195 / `disbelief()` :199 / `uncertainty()` :203 / `baseRate()` :207 / `projection()` :211 | | `:91` / `:92` / `:93` / `:94` / `:112` (`b + a*u`, rounded to 6 dp) |
| `public RealValue getRelativeWeight()` | :245 | `:97` — returns the stored weight only when the opinion is dogmatic, else `0.0`. |
| `public RealValue certainty()` | :279 | `:167` — `1 - u`, with an explicit `NaN` guard (`0.0/0.0` comparison at `:169`). |
| `public RealValue projectiveDistance(Value)` :166 / `conjunctiveCertainty(Value)` :171 / `degreeOfConflict(Value)` :176 | | `:116` / `:120` / `:124` |
| `public BooleanValue isAbsolute()` :249 / `isVacuous()` :253 / `isDogmatic()` :262 / `isMaximizedUncertainty()` :266 | | `:139` (`b==1 \|\| d==1`) / `:143` (`u==1`) / `:151` (`u==0`) / `:155` (`d==0 \|\| b==0`) |
| `public BooleanValue isCertain(Value threshold)` :257 / `isUncertain(Value threshold)` :270 | | `:147` / `:159` (`1-u < threshold`); both coerce via `RealValue.valueOf(threshold)` and **NPE if the threshold is not Real/Integer** (`RealValue.valueOf` returns `null`, `FORK/.../RealValue.java:90-99`). |
| `public UBooleanValue toUBoolean()` | :191 | `:1562` — `new UBoolean(true, projection())`; constructed through the **package-private** `UBooleanValue(UBoolean)` ctor, so the two classes must stay in one package. |

### 9.5 Wrapper methods — logic and opinion algebra

| Signature | Line | Delegate |
|---|---|---|
| `public SBooleanValue and(Value)` :215 / `or(Value)` :225 / `xor(Value)` :230 / `equivalent(Value)` :235 / `implies(Value)` :240 | | `:244` / `:260` / `:286` / `:281` / `:276`. `implies` is defined as `not().or(s)` "to be consistent with UBoolean" — an explicit divergence from textbook subjective logic (`UDT/SBoolean.java:278`). |
| `public UncertainBooleanValue not()` | :220 | `:234` — swaps `b`/`d` and complements `a`. |
| `public SBooleanValue uncertaintyMaximized()` :181 / `uncertainOpinion()` :275 | | Both call `:298`. `uncertainOpinion` therefore duplicates `uncertaintyMaximized` rather than the library's `:163`. |
| `public SBooleanValue deduceY(Value yGivenX, Value yGivenNotX)` | :185 | `:315` — binomial deduction with `this` as the antecedent. |
| `public SBooleanValue applyOn(Value)` | :461 | `:185` — rebases the opinion on a `UBoolean`'s confidence. **Unguarded cast** `(UBooleanValue) value` at `:462` → `ClassCastException` for anything else. |
| `public SBooleanValue min(Value)` :466 / `max(Value)` :471 | | `:1538` / `:1542` — ordered by projection. |

### 9.6 Wrapper methods — fusion

Two families with confusingly similar names:

**(a) Pairwise, argument is a single opinion** — each builds a two-element `LinkedList` containing
`this` and the argument, then calls the library's static collection form:

| Signature | Line | Static delegate |
|---|---|---|
| `minimumFusion(Value)` | :294 | `SBoolean.minimumBeliefFusion` (`:511`) |
| `majorityFusion(Value)` | :302 | `SBoolean.majorityBeliefFusion` (`:542`) |
| `averageFusion(Value)` | :310 | `SBoolean.averageBeliefFusion` (`:632`) |
| `cumulativeFusion(Value)` | :318 | `SBoolean.cumulativeBeliefFusion` (`:728`) |
| `epistemicCumulativeFusion(Value)` | :326 | `SBoolean.epistemicCumulativeBeliefFusion` (`:834`) |
| `weightedFusion(Value)` | :334 | `SBoolean.weightedBeliefFusion` (`:938`) |

**(b) Collection-valued, argument is a `CollectionValue`** — each casts `value` to
`CollectionValue`, takes `asSequence()`, prepends `this.sBoolean`, then calls the static form:

| Signature | Line | Static delegate |
|---|---|---|
| `minimumBeliefFusion(Value)` | :342 | `minimumBeliefFusion` (`:511`) |
| `majorityBeliefFusion(Value)` | :355 | `majorityBeliefFusion` (`:542`) |
| `beliefConstraintFusion(Value)` | :368 | `uDataTypes.SBoolean.beliefConstraintFusion` (`:486`) — fully qualified at `:378`, the only such call |
| `averageBeliefFusion(Value)` | :381 | `averageBeliefFusion` (`:632`) |
| `aleatoryCumulativeBeliefFusion(Value)` | :394 | **`cumulativeBeliefFusion`** (`:728`), *not* `aleatoryCumulativeFusion` (`:649`) — name/target mismatch, carried as-is |
| `epistemicCumulativeBeliefFusion(Value)` | :407 | `epistemicCumulativeBeliefFusion` (`:834`) |
| `weightedBeliefFusion(Value)` | :420 | `weightedBeliefFusion` (`:938`) |
| `consensusAndCompromiseFusion(Value)` | :433 | `consensusAndCompromiseFusion` (`:1190`) |
| `discount(Value)` | :446 | `this.sBoolean.discount(collection)` (`:1457`) — **the one collection method that does NOT prepend `this.sBoolean`** (compare `:449` with `:345`), correctly so, since `this` is the discounted opinion rather than a fusion participant. `UDT/SBoolean.java:1458-1459` explicitly rejects a `null` collection with `IllegalArgumentException`. |

All eleven collection-valued methods cast with an **unguarded** `(CollectionValue) value`
(e.g. `:343`), so a non-collection argument yields `ClassCastException`.

### 9.7 Delegate contract (`UDT/SBoolean.java`)

- Representation `(b, d, u, a)` plus a `relativeWeight` used only by fusion (`:16-21`).
- Every component is `adjust`ed — rounded to 6 decimals (`:39-41`) — before the invariant check.
- Invariant enforced in **all four** public/private constructors: `|b+d+u-1| <= 0.001` and each of
  `b,d,u,a ∈ [0,1]`, else `IllegalArgumentException` naming the offending values
  (`:49-52`, `:61-64`, `:74-77`).
- `SBoolean(UBoolean)` (`:80`) is the *only* constructor with no invariant check — it cannot
  violate it, since `b = c`, `d = 1-c`, `u = 0`.
- `equals(Object)` (`:1514`) **is** a proper override, tolerance 0.001 per component;
  `hashCode()` (`:1547`) **is** a proper override. Delegation from `SBooleanValue` at `:133`/`:147`
  is therefore sound — this is the best-behaved of the four delegates.
- `compareTo(SBoolean)` (`:1570`) has a real implementation (L1 distance dead band, then projection
  order) — which `SBooleanValue.compareTo` **does not use** (§9.3).
- `UNCERTAIN` constant `(0,0,1,0.5)` (`:14`) is unused by `SBooleanValue`.
- `discount(Collection)` (`:1457`) uses `java.util.stream` — Java 8+, fine for the Java 17 target.

### 9.8 Edge cases explicitly handled

| Case | Handling |
|---|---|
| `b+d+u ≠ 1` | `IllegalArgumentException` from the delegate constructor (`UDT/SBoolean.java:49-52`) |
| any component outside `[0,1]` | same check |
| `uncertainty == 1` (vacuous) | explicit early return in `applyOn` (`UDT/SBoolean.java:192`) and in `increasedUncertainty` (`:129`) |
| `uncertainty == 0` (dogmatic) | drives `getRelativeWeight` (`:97-99`) and `isDogmatic` (`:151`) |
| base rate `a == 0` or `a == 1` | explicit branches in `applyOn` (`:195-199`) and `uncertaintyMaximized` (`:305-307`) |
| `NaN` uncertainty | explicit guard in `certainty()` (`:169`) |
| projection / base rate outside `[0,1]` in the factories | explicit `IllegalArgumentException` (`:211-213`, `:223-225`) |
| `null` fusion collection | explicit `IllegalArgumentException` in `discount` (`:1458`) |
| `(1,0,0,1)` / `(0,1,0,1)` in `Builder.build()` | canonicalised to the singletons (`:60-63`) |
| `null` argument to any `SBooleanValue` method | **unhandled** — `valueOf` dereferences at `:74` |
| non-SBoolean argument to `uEquals` / `uDistinct` | explicit: `FALSE` / `TRUE` (`:101`, `:113`) |

### 9.9 7.5.0 `Value` contract work-list

| Contract | Status |
|---|---|
| C1 | supplied via `UncertainBooleanValue(Type)` |
| C2 `compareTo(Value)` | supplied, same signature (`:151`) — **semantically broken**, §9.3 |
| C3 `toString(StringBuilder)` | supplied, same signature (`:124`) |
| C4 `hashCode()` | supplied (`:133`) |
| C5 `equals(Object)` | supplied (`:138`) |
| C6–C23 | inherited unchanged |
| C27 `isSBoolean()` | supplied (`:95`) — needs the base-class predicate, §10 |
| C24 `isUBoolean()` | **not** overridden — an `SBooleanValue` answers `false` to `isUBoolean()`, which is what makes `UBooleanValue.valueOf(SBooleanValue)` return `null` (§12.2) |

**Missing 7.5.0 symbols**: `Value.isSBoolean()` (`:95`), `Value.isUBoolean()` (`:76`),
`Value.isBoolean()` (present), `TypeFactory.mkSBoolean()` (`:19`, `:24`),
`Type.isKindOfSBoolean(VoidHandling)` (`:103`, `:115`), `MathUtil.round(double,int)` (`:125-128`),
**`RealValue.valueOf(Value)`** (`:258`, `:271`, `:284`, `:285`, `:290`) — see §11 —
`uDataTypes.SBoolean`, and same-package access to `UBooleanValue.getuBoolean()` (`:78`, `:463`)
and the package-private `UBooleanValue(UBoolean)` ctor (`:192`).

## 10. `Value.java` — **edit to an upstream file**

Target: `TARGET/uml/ocl/value/Value.java`.

**Minimal change, stated as behaviour:**

> `Value` must now also answer four additional type-discrimination questions —
> "are you a UInteger?", "are you a UReal?", "are you a UBoolean?", "are you an SBoolean?" —
> defaulting to *no* for every value that does not say otherwise, exactly as it already does for
> `isInteger`, `isReal`, `isBoolean` and the collection predicates.

Nothing else in `Value` changes. Specifically:

- No field is added or removed; `fType` stays as-is.
- No existing method's signature or body changes.
- No abstract member is added, so **no existing 7.5.0 value class needs touching** — every one of
  them inherits the new `false` defaults (verified: the fork's own `BooleanValue`, `IntegerValue`,
  `RealValue`, `StringValue` are byte-identical to 7.5.0's apart from `$Id$` comment lines and the
  one `RealValue.valueOf` addition — see the diffs in §14).
- The four predicates must be `public boolean`, non-abstract, returning `false`, mirroring
  `FORK/uml/ocl/value/Value.java:67`, `:84`, `:100`, `:108`.

**Why this is minimal:** the only things the new value classes require of `Value` are (a) the
`protected Value(Type)` constructor, which already exists, and (b) an overridable `false`-returning
predicate per new value kind, which does not. Everything else they need — `type()`,
`getRuntimeType()`, the `final toString()`, the abstract `equals`/`hashCode`/`toString(StringBuilder)`
triple — is already present and unchanged.

**Note on `isUString`:** the fork did **not** add one, and `UStringValue` does not need one
(§8.1). Do not add it "for symmetry"; doing so would be an unwarranted divergence and would leave
a predicate no class ever answers `true` to.

## 11. `RealValue.java` — **edit to an upstream file**

Not in the original brief but forced by `SBooleanValue`.

**Minimal change:**

> `RealValue` must now also offer a static widening lift that accepts any numeric `Value` and
> produces a `RealValue`, answering `null` when the argument is neither Real nor Integer.

`SBooleanValue` calls `RealValue.valueOf(threshold)` at `:258`, `:271`, `:284`, `:285`, `:290`.
The method exists only in the fork (`FORK/uml/ocl/value/RealValue.java:90-99`) and is the *sole*
behavioural difference between the two `RealValue.java` files — confirmed by
`diff -u TARGET/.../RealValue.java FORK/.../RealValue.java`, whose only other hunks are a `$Id$`
line and a `@version` Javadoc tag.

**Alternative that avoids the edit:** inline the two-branch coercion inside `SBooleanValue`.
Recommend the edit: it is additive, matches the historical shape, and keeps `SBooleanValue`
free of type-dispatch logic.

## 12. `CollectionValue.java` — **edit to an upstream file**

Target: `TARGET/uml/ocl/value/CollectionValue.java`.

**Minimal change, stated as behaviour:**

> Every collection value must now also be able to answer membership and counting questions
> *with a degree of confidence instead of a yes/no*: "how confident are you that you contain this
> value", "…that you contain all of these", "…that you contain none of these", and "how many
> elements match this value at or above a given confidence threshold". These answers must treat an
> element (or the probe) that carries uncertainty by asking it for its `uEquals`/`uDistinct`
> degree, and must fall back to ordinary `equals` at confidence 1 when neither side is uncertain.

Concretely, five new members, all with default (non-abstract) bodies so that `SetValue`,
`BagValue`, `SequenceValue` and `OrderedSetValue` need **no change at all**:

| Member | Historical line | Behaviour |
|---|---|---|
| `public UBooleanValue uIncludes(Value v)` | `FORK/.../CollectionValue.java:112` | Maximum over elements of the pairwise `uEquals` confidence; starts at `FALSE`; early-exits once confidence reaches 1. |
| `public UBooleanValue uIncludesAll(CollectionValue coll2)` | :134 | `FALSE` immediately if `coll2` is larger than `this`; else the `and`-fold of `uIncludes` over `coll2`; early-exits at confidence 0. |
| `public UBooleanValue uExcludes(Value v)` | :154 | `and`-fold of the pairwise `uDistinct` degree over all elements, starting at `TRUE`; early-exits at 0. |
| `public UBooleanValue uExcludesAll(CollectionValue coll2)` | :177 | `and`-fold of `uExcludes` over `coll2`. |
| `public int uCountC(Value value, double confidence)` | :190 | Counts elements whose match confidence is `>= confidence`; returns a plain `int`. |

Dispatch rule shared by all five (`:120-125`, `:163-168`, `:196-201`): *if the element is an
`UncertainValue`, ask the element; else if the probe is an `UncertainValue`, ask the probe; else
`UBooleanValue.valueOf(v.equals(elemVal), 1)`.*

**Everything else in `CollectionValue` is unchanged.** The `diff` in §14 shows the remaining hunks
are cosmetic: `$Id$`, a `@version` tag, an import style change (`java.util.*` in 7.5.0 vs eight
explicit imports in the fork), one reworded Javadoc sentence at 7.5.0 `:74`, and one trailing blank
line. None of these are behaviour. **Do not** revert the 7.5.0 import consolidation or Javadoc
wording; the fork is older there, not better.

### 12.1 Callers that pin the semantics

`FORK/uml/ocl/expr/operations/StandardOperationsCollection.java` — `uIncludes` at `:124`,
`uExcludes` at `:189`, `uCountC` with a hard-wired `0.5` at `:288` and with a user threshold at
`:354`, `uIncludesAll` at `:420`, `uExcludesAll` at `:492`, plus the registration of
`Op_collection_uCountC` at `:42`/`:311`. These belong to the expression spec part, but they are
the reason the five methods must live on `CollectionValue` rather than on the concrete subclasses.

### 12.2 Two latent failures in the shared dispatch rule

Both arise when a collection holds `SBooleanValue`s:

1. **`uIncludes` NPEs.** At `:121` the result of `((UncertainValue) elemVal).uEquals(v)` is an
   `SBooleanValue`; `UBooleanValue.valueOf(Value)` returns `null` for it
   (`UBooleanValue.java:122-138` handles only `isUBoolean()` and `isBoolean()`); `aux` is then
   `null` and `aux.probability()` at `:127` throws.
2. **`uExcludes` throws `ClassCastException`.** At `:171` `result.and(aux)` routes an
   `SBooleanValue` through `UBooleanValue.assertKindOfUBoolean`
   (`UBooleanValue.java:295-302`), which raises
   `ClassCastException("A value kind of UBoolean expected")`.

`uCountC` (`:197`) has the same shape as (1). **UNVERIFIABLE**: whether the fork's own test suite
ever puts an `SBooleanValue` in a collection and calls these — `FORKTEST/uml/ocl/expr/UCollectionExpOpTest.java`
exists but was not read for this section. Port decision needed: either widen the fold to
`UncertainBooleanValue` (matching what `uExcludes` already declares at `:156`) or document the
restriction to `UBoolean`-kind elements.

## 13. Cross-cutting adaptation work-list

Ordered by what blocks what.

**Tier 0 — prerequisites owned by other spec parts (this part only records the dependency):**

| Need | Used by | Present in 7.5.0? |
|---|---|---|
| `TypeFactory.mkUBoolean()`, `mkUInteger()`, `mkUReal()`, `mkUString()`, `mkSBoolean()` | every value class' constructor | **no** — `TARGET/uml/ocl/type/TypeFactory.java` stops at `mkTuple`/`mkSimpleType` |
| `Type.isKindOfUBoolean`, `isKindOfSBoolean` (`VoidHandling`) | `UBooleanValue:278`, `SBooleanValue:103,115` | **no** |
| `BooleanType.isKindOfUBoolean() == true` | makes `UBooleanValue.uEquals(BooleanValue)` work | fork: `FORK/uml/ocl/type/BooleanType.java:50` |
| `UBooleanType.isKindOfSBoolean() == true` | makes `SBooleanValue.uEquals(UBooleanValue)` work | fork: `FORK/uml/ocl/type/UBooleanType.java:23` |
| `IntegerType.isKindOfUReal/isKindOfUInteger == true` | numeric widening | fork: `FORK/uml/ocl/type/IntegerType.java:58,63` |
| `Type.isKindOfReal(VoidHandling)` | `UIntegerValue:180` | **yes** (`TARGET/uml/ocl/type/Type.java:92`) |
| `Type.VoidHandling` | several | **yes** (`TARGET/uml/ocl/type/Type.java:33`) |

Asymmetry worth recording: `SBooleanType.isKindOfUBoolean()` is **not** overridden
(`FORK/uml/ocl/type/SBooleanType.java` defines only `isTypeOfSBoolean` and `isKindOfSBoolean`),
so `UBooleanValue.uEquals(SBooleanValue)` returns `FALSE` while
`SBooleanValue.uEquals(UBooleanValue)` computes a real answer. Reproduce, do not "fix"
silently.

**Tier 1 — utility, owned by this part:**

- `MathUtil.round(double value, int digits)` must be added to `TARGET/util/MathUtil.java`.
  Body from `FORK/util/MathUtil.java:106` (last method, `Math.round(value * 10^digits) / 10^digits`,
  attributed `@author Víctor Manuel Ortiz`).
  Required by `UBooleanValue:197,240`, `UIntegerValue:51,75-76`, `URealValue:48-50,77-80`,
  `SBooleanValue:125-128`. **edit to an upstream file.**
- `RealValue.valueOf(Value)` — §11. **edit to an upstream file.**

**Tier 2 — the value classes themselves:** copy the seven files, changing only

1. the `@Override` on `isUBoolean`/`isUInteger`/`isUReal`/`isSBoolean` — valid once §10 lands;
2. nothing else, if a bug-for-bug port is chosen.

**Tier 3 — deliberate fixes, each needing an explicit decision recorded in the port log:**

| # | Defect | Section | Recommendation |
|---|---|---|---|
| 1 | `UStringValue.equals` is constant `false`, breaking reflexivity | §8.6.1 | **fix** |
| 2 | `UStringValue.compareTo` compares raw vs decorated strings | §8.6.2 | fix |
| 3 | `SBooleanValue.compareTo` returns a constant `0` | §9.3 | fix (delegate to `SBoolean.compareTo`, which already exists at `UDT/SBoolean.java:1570`) |
| 4 | `UIntegerValue.hashCode` collapses to `0` whenever `u == 0` | §6.6.1 | fix (mirror `URealValue:56-63`) |
| 5 | `UIntegerValue.equals(URealValue)` always `false` | §6.6.2 | fix |
| 6 | `UIntegerValue.compareTo(URealValue)` un-negated delegation | §6.6.3 | fix |
| 7 | `URealValue.compareTo` dead branch at `:104-107` | §7.6.1 | delete |
| 8 | `URealValue.compareTo(UIntegerValue)` returns `0` | §7.6.2 | fix |
| 9 | `UBooleanValue.FALSE.equals(BooleanValue.FALSE)` is `false` | §5.7.1 | fix |
| 10 | `UIntegerValue`/`URealValue`/`UStringValue` `compareTo` ignore `UndefinedValue` | §6.6, §7.6 | fix — `TARGET/.../Value.java:27-31` states this is mandatory |
| 11 | `CollectionValue.uIncludes`/`uCountC` NPE, `uExcludes` CCE on `SBooleanValue` elements | §12.2 | fix or document |

Every entry in Tier 3 changes observable behaviour relative to the historical fork. If the port's
acceptance criterion is "reproduce the fork's test results", entries 1, 2, 3, 5, 6, 8, 9, 10, 11
are unobserved by `FORKTEST/uml/ocl/value/` (which contains only `AllTests.java`,
`UBooleanValueTest.java`, `UIntegerValueTest.java`, `URealValueTest.java`, `ValueTest.java`) and
so are safe to fix; entry 4 is likewise unobserved (no hash-bridge test exists).

**Tier 4 — packaging invariants that must not be broken:**

- `UBooleanValue(UBoolean)` (`:42`), `UBooleanValue.valueOf(UBoolean)` (`:74`) and
  `getuBoolean()` (`:148`) are package-private and are used from `SBooleanValue`
  (`:78`, `:192`, `:463`). Both classes **must** land in `org.tzi.use.uml.ocl.value`.
- `SBooleanValue`'s two constructors are package-private; `SBooleanValue.Builder` is the public
  cross-package entry point (`FORK/uml/ocl/expr/ExpConstSBoolean.java:49`). Do not widen the
  constructors — that would silently create a second, non-canonicalising construction path.
- `CollectionValue(Type,Type)` is package-private in both trees
  (`TARGET/.../CollectionValue.java:40`); unchanged.

## 14. Reproducing every structural claim

```bash
R=/home/xoruser/msc-4/use-msc2026/.git/reference-repositories/uncertainty
T=/home/xoruser/msc-4/use-msc2026/use-core/src/main/java/org/tzi/use
F=$R/USE-Uncertainty/src/main/org/tzi/use

# which files are new vs edits
ls $F/uml/ocl/value/ $T/uml/ocl/value/

# Value.java: the only behavioural delta is four is*() predicates
diff -u $T/uml/ocl/value/Value.java $F/uml/ocl/value/Value.java

# CollectionValue.java: the only behavioural delta is five u* methods
diff -u $T/uml/ocl/value/CollectionValue.java $F/uml/ocl/value/CollectionValue.java

# RealValue.java: the only behavioural delta is valueOf(Value)
diff -u $T/uml/ocl/value/RealValue.java $F/uml/ocl/value/RealValue.java

# IntegerValue/BooleanValue/StringValue: no behavioural delta at all
diff -u $T/uml/ocl/value/IntegerValue.java  $F/uml/ocl/value/IntegerValue.java
diff -u $T/uml/ocl/value/BooleanValue.java  $F/uml/ocl/value/BooleanValue.java
diff -u $T/uml/ocl/value/StringValue.java   $F/uml/ocl/value/StringValue.java

# MathUtil.round exists only in the fork
diff -u $T/util/MathUtil.java $F/util/MathUtil.java

# TypeFactory: no mkU*/mkSBoolean in 7.5.0
grep -n 'public static.*mk' $T/uml/ocl/type/TypeFactory.java
grep -n 'public static.*mk' $F/uml/ocl/type/TypeFactory.java

# Type: no isKindOfU*/isKindOfSBoolean in 7.5.0
grep -n 'isKindOf\|VoidHandling' $T/uml/ocl/type/Type.java
grep -n 'isKindOf\|VoidHandling' $F/uml/ocl/type/Type.java

# who calls the five new CollectionValue methods
grep -rn 'uIncludes\|uExcludes\|uCountC' $F --include=*.java | grep -v value/CollectionValue.java

# every main-source consumer of the new value classes
grep -rln 'UBooleanValue\|URealValue\|UIntegerValue\|UStringValue\|SBooleanValue\|UncertainValue' \
  $F --include=*.java | sort

# the fork's own value tests (note: no UStringValueTest, no SBooleanValueTest)
ls $R/USE-Uncertainty/src/test/org/tzi/use/uml/ocl/value/
```

## 15. Gaps

1. **UNVERIFIABLE — jar-vs-source drift.** Everything said about `uDataTypes` here comes from
   `$R/uDataTypes/Libraries/Java/src/uDataTypes/*.java`. The fork actually compiles against
   `lib/atenearesearchgroup.uncertainty.jar` (`build.xml:50`, `:278`). The jar's bytecode was
   **not** disassembled, so no claim here is proof that the jar's `UReal`/`UInteger`/`UBoolean`/
   `UString`/`SBoolean` are identical to these sources. Every formula and every exception message
   attributed to a delegate should be re-confirmed against the jar before it is treated as an
   oracle for test expectations.
2. **UNVERIFIABLE — `SBooleanValue` behaviour under sorting.** No fork test places an
   `SBooleanValue` in a sorted collection, so the practical consequence of the constant-`0`
   `compareTo` (§9.3) — benign reordering vs a TimSort contract violation — is not established.
3. **UNVERIFIABLE — `CollectionValue` u-ops with `SBooleanValue` elements.** The NPE and CCE in
   §12.2 were derived by reading, not by execution;
   `FORKTEST/uml/ocl/expr/UCollectionExpOpTest.java` was not read for this section, so it is
   unknown whether the fork exercises that path.
4. **Not covered here (other spec parts):** the `org.tzi.use.uml.ocl.type` hierarchy
   (`UncertainType`, `UBooleanType`, `SBooleanType`, `UIntegerType`, `URealType`, `UStringType`
   and the `TypeFactory`/`Type` extensions), and the `org.tzi.use.uml.ocl.expr` layer
   (`ExpConstUBoolean`, `ExpConstSBoolean`, `ExpConstUInteger`, `ExpConstUReal`,
   `ExpConstUString`, `ExpDefSBoolean`, `ExpQuery`, and the six
   `StandardOperations{Any,Collection,Number,SBoolean,UBoolean,UInteger,UReal,UString}` files).
   §13 Tier 0 lists only the type-layer facts the value classes *depend on*.
5. **Not consulted:** `origin/main`. No claim in this document derives from the earlier port.
