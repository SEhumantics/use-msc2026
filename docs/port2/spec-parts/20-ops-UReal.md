# 20 — Operation table: `UReal` (fork registry `StandardOperationsUReal.java`)

**Scope of this document.** The complete registry of OCL operations defined by the single fork file

```
/home/xoruser/msc-4/use-msc2026/.git/reference-repositories/uncertainty/USE-Uncertainty/src/main/org/tzi/use/uml/ocl/expr/operations/StandardOperationsUReal.java   (720 lines)
```

plus the OpGeneric registration contract it obeys, plus a cross-check against the 7.5.0 registry at
`/home/xoruser/msc-4/use-msc2026/use-core/src/main/java/org/tzi/use/uml/ocl/expr/operations/`.

Every claim below cites a file+line, a symbol, or a shell command that was actually run. Anything
that could not be established is marked **UNVERIFIABLE**.

---

## 0. Operation count and how to reproduce it

**18 operations.** All are `OpGeneric` subclasses declared as package-private *top-level* `final class`es
in the same compilation unit (not static nested classes), and all 18 are registered in the single static
block `StandardOperationsUReal.registerTypeOperations(Multimap<String,OpGeneric>)` at
`StandardOperationsUReal.java:13-33`.

Reproduce command (run, output `18`):

```bash
grep -cE '^[[:space:]]*OpGeneric\.registerOperation\(new Op_ureal_[A-Za-z]+\(\), opmap\);$' \
  /home/xoruser/msc-4/use-msc2026/.git/reference-repositories/uncertainty/USE-Uncertainty/src/main/org/tzi/use/uml/ocl/expr/operations/StandardOperationsUReal.java
```

Two independent cross-checks, both run, both `18`:

```bash
# 1. class declarations
grep -cE '^final class Op_ureal_[A-Za-z]+ extends OpGeneric \{$' <same file>     # -> 18
# 2. distinct OCL names returned by name()
grep -oP '(?<=return ")[^"]+(?=";)' <same file> | sort -u | wc -l                # -> 18
```

The 18 OCL names are all distinct (no two classes share a `name()`), so the file contributes 18 entries
to 18 different buckets of the operation multimap:

```
abs acos asin atan cos inv neg power setUncertainty setValue sin sqrt tan toInteger toReal toUInteger uncertainty value
```

**Registration order** (matters — see §4.1, first match wins) is the order of
`StandardOperationsUReal.java:15-32`:
`abs, sin, cos, tan, asin, acos, atan, uncertainty, setUncertainty, value, setValue, neg, power, sqrt, inv, toReal, toInteger, toUInteger`.

### What this file does *not* contain (important for the port)

The 18 ops here are **not** the full `UReal` OCL surface. The fork also gave `UReal` behaviour by editing
`StandardOperationsNumber.java` in place. Those are out of scope for this document but must not be
forgotten:

| OCL name | Fork location (not this file) | Note |
|---|---|---|
| `+`, `-` (binary), `*` | `StandardOperationsNumber.java:96-162`, `175-242`, `255-321` via `ArithOperation.matches` at `:60-74` | `matches` returns `mkUReal()` when both params `isKindOfNumber(INCLUDE_VOID)` and neither branch above fired |
| `/` | `StandardOperationsNumber.java:327-433` | `matches` returns `mkUReal()` when either param `instanceof UncertainType` (`:351`) |
| `-` (unary) | `StandardOperationsNumber.java:505-548` | infix/prefix; `eval` `:542-543` handles the `URealValue` case |
| `+` (unary) | `StandardOperationsNumber.java:553-579` | identity, no UReal-specific code |
| `floor`, `round` | `StandardOperationsNumber.java:586-638`, `645-698` | `matches` returns `mkUReal()` for a `UReal` receiver (`:608-609`, `:668-669`); `eval` calls `URealValue.floor()`/`.round()` |
| `max`, `min` | `StandardOperationsNumber.java:712-774`, `788-850` | `evalURealResult` at `:745-752` / `:821-828` |
| `<`, `>`, `<=`, `>=` | `StandardOperationsNumber.java:922-995`, `1000-1072`, `1077-1150`, `1155-1228` | return **`UBoolean`** when either param `instanceof UncertainType` |
| `toString` | `StandardOperationsNumber.java:1231-1258` | `matches` is `isKindOfNumber(EXCLUDE_VOID)`, and `URealType.isKindOfNumber()` is `true` (`URealType.java:28-30`), so it already covers `UReal` unchanged |

---

## 1. The registration / evaluation contract

### 1.1 `OpGeneric` (fork copy: `.../USE-Uncertainty/src/main/org/tzi/use/uml/ocl/expr/operations/OpGeneric.java`, 125 lines)

Abstract members every operation in this file implements:

| Member | Line (fork) | Notes |
|---|---|---|
| `public abstract String name()` | 38 | the OCL operation symbol; the multimap key |
| `public boolean isBooleanOperation()` | 40-42 | default `false`; **not overridden by any of the 18** |
| `public abstract int kind()` | 44 | all 18 return `OPERATION` (`= 0`, line 34) |
| `public abstract boolean isInfixOrPrefix()` | 46 | all 18 return `false` |
| `public abstract Type matches(Type params[])` | 48 | `params[0]` is the **receiver**; returns the result type or `null` |
| `public String checkWarningUnrelatedTypes(Expression[])` | 50 | default `null`; **not overridden by any of the 18** |
| `public abstract Value eval(EvalContext, Value[], Type resultType)` | 52 | `args[0]` is the receiver |
| `public String stringRep(Expression[], String atPre)` | 54-78 | since `isInfixOrPrefix()==false`, renders `receiver.name(arg1,…)` (non-collection receiver → `.`) |
| `static void registerOperation(OpGeneric, Multimap)` | 112-114 | `opmap.put(op.name(), op)` — the overload used by all 18 |
| `static void registerOperation(String, OpGeneric, Multimap)` | 122-124 | unused by this file |

`OpGeneric.registerOperations` (fork `:80-105`) invokes `StandardOperationsUReal.registerTypeOperations(opmap)`
at **line 93**, i.e. *after* `StandardOperationsNumber` (line 88) and before the collection registries.

### 1.2 Undefined-argument handling is **not** in this file

All 18 return `kind() == OPERATION`. `ExpStdOp.eval` (fork
`.../uml/ocl/expr/ExpStdOp.java:288-322`) evaluates arguments left-to-right and, on the **first**
undefined argument, sets the result to `UndefinedValue.instance` and **never calls `eval`**
(`:298-304`). It then wraps the `eval` call in `try { … } catch (ArithmeticException ex) { res = UndefinedValue.instance; }`
(`:316-321`).

Consequences used throughout the table below:

* "undefined receiver or undefined argument → `Undefined : OclVoid`" is contract-level, not per-op.
* Every `throw new ArithmeticException()` inside these `eval` bodies is the idiom for
  "result is `Undefined`". None of them carries a message.
* The explicit `args[1].isUndefined()` branches inside `Op_ureal_setUncertainty` (`:177-178`) and
  `Op_ureal_setValue` (`:290`, `:298-299`) are therefore **dead code** — `ExpStdOp` short-circuits first.
  They are harmless, but a port need not reproduce them.

The 7.5.0 `ExpStdOp.eval` (`use-core/.../expr/ExpStdOp.java:281-315`) is character-identical in this
region, so this part of the contract needs no adaptation.

---

## 2. Backing library (`uDataTypes.UReal`) — the actual arithmetic

The registry classes are thin; the numerics live in the wrapper `URealValue` and in the library class
`uDataTypes.UReal`.

* Wrapper: `.../USE-Uncertainty/src/main/org/tzi/use/uml/ocl/value/URealValue.java` (281 lines).
  `value()` = `uReal.getX()` (`:28-30`), `uncertainty()` = `uReal.getU()` (`:32-34`),
  ctor `URealValue(double,double)` → `new UReal(value,uncertainty)` (`:18-21`).
* Library **source**: `.../uncertainty/uDataTypes/Libraries/Java/src/uDataTypes/UReal.java` (694 lines).
* Library **oracle jar** (the binding one, per the established fact):
  `.../USE-Uncertainty/lib/atenearesearchgroup.uncertainty.jar`, md5 `a3055f54205babaa27484fa94efdda1c`
  (`md5sum`, run). Note: there is **no** copy at `use-core/src/test/resources/historical/atenearesearchgroup.uncertainty.jar`
  in this working tree — `md5sum` reported `No such file or directory` for that path.

**Jar-vs-source verification (run).** `javap -classpath <jar> uDataTypes.UReal` lists exactly the public
API used here (`add/minus/mult/divideBy/abs/neg/power(float)/sqrt/sin/cos/tan/atan/acos/asin/inverse/floor/round/toInteger/toReal/toUInteger/min/max/lt/le/gt/ge/uEquals`).
A probe program compiled against the jar and executed
(`/tmp/claude-1000/-home-xoruser-msc-4/5a883e17-9055-4019-8f36-a743005556fa/scratchpad/probe/Probe.java`;
`javac -cp <jar> -d . Probe.java && java -cp .:<jar> Probe`) reproduced the formulas in the source file
at every point probed. Selected observed outputs:

```
ctor(1,-0.5)        -> x=1.0                u=0.5              (setU applies Math.abs)
abs(-3,0.5)         -> x=3.0                u=0.5
neg(3,0.5)          -> x=-3.0               u=0.5
sin(0.5,0.1)        -> x=0.479425538604203  u=0.08775825618903728   ( = |0.1*cos 0.5| )
cos(0.5,0.1)        -> x=0.8775825618903728 u=0.0479425538604203    ( = |0.1*sin 0.5| )
cos(2.0,0.1)        -> x=-0.4161468365471424 u=0.09092974268256818  ( = |0.1*sin 2| )
tan(0.5,0.1)        -> x=0.5463024898437905 u=0.09831850394390179
tan(pi/2,0.1)       -> x=1.633123935319537E16  u=2.6670937881135713E31   (finite! no exception)
asin(0.5,0.1)       -> u=0.11547005383792516  ( = 0.1/sqrt(1-0.25) )
asin(1,0.1)         -> x=1.5707963267948966 u=0.1        (|x|==1 special case)
asin(2,0.1)         -> x=NaN u=NaN
acos(1,0.1)         -> x=0.0 u=0.1 ;  acos(2,0.1) -> NaN/NaN
atan(0.5,0.1)       -> u=0.08                ( = 0.1/(1+0.25) )
inverse(2,0.1)      -> x=0.5  u=0.025        ( = 0.1/2^2 )
inverse(2,0)        -> x=0.5  u=0.0
inverse(0,0)        -> x=Infinity u=NaN ;  inverse(0,0.1) -> Infinity/Infinity
sqrt(4,0.2)         -> x=2.0  u=0.05         ( = 0.2/(2*2) )
sqrt(0,0)           -> x=0.0  u=0.0          (explicit special case, UReal.java:165-168)
sqrt(0,0.1)         -> x=0.0  u=Infinity
sqrt(-1,0)          -> NaN/NaN ; sqrt(-1,0.1) -> NaN/NaN
power(2,0.1)^3      -> x=8.0  u=1.2000000000000002   ( = 3*0.1*2^2 )
power(2,0.1)^0.5    -> x=1.4142135623730951 u=0.03535533905932738
power(2,0.1)^0      -> x=1.0  u=0.0
power(0,0.1)^-1     -> Infinity/Infinity
power(0,0)^-1       -> x=Infinity u=NaN
power(-2,0.1)^0.5   -> NaN/NaN
toReal(3.7,0.2)=3.7 ; toInteger(3.7,0.2)=3 ; toInteger(-3.2,0.2)=-4      (floor, not truncation)
lib UReal.toUInteger(3.7,0.2)  -> x=3  u=0.728010988928052
lib UReal.toUInteger(-3.7,0.2) -> x=-4 u=0.3605551275463988
```

The last two lines matter: **the fork does not use the library's `toUInteger`** — see op #10.

**UNVERIFIABLE:** whether the jar's bytecode bodies match the `uDataTypes` source *for inputs not probed*.
The probe covers every operation in the table at representative and edge points and matched the source
formulas everywhere, but this is empirical, not a bytecode diff.

---

## 3. The operation table

Conventions used in every row:

* **Arity counts the receiver as argument 0.** `abs` has arity 1 (receiver only); `power` has arity 2
  (receiver + exponent). `matches(Type params[])` receives that same array, so `params.length == 1`
  means a receiver-only call.
* **Call form: every one of the 18 is a dot-call** — all override `isInfixOrPrefix()` to return `false`,
  and `OpGeneric.stringRep` (`:65-75`) then renders `receiver.name(args…)` because a `UReal` receiver is
  not a collection. **None is infix, none is prefix.** (Unary minus on `UReal` is the *separate*
  `Op_number_unaryminus`, name `"-"`, `isInfixOrPrefix()==true`.)
* "Guards" = the NaN/Infinity checks inside `eval` that throw `ArithmeticException` and thereby yield
  `Undefined : OclVoid`.
* "`.in` " = the golden expression file
  `.../USE-Uncertainty/src/test/org/tzi/use/parser/uncertainty/URealExpression.in` (1881 lines).
  "test" = `.../USE-Uncertainty/src/test/org/tzi/use/uml/ocl/expr/URealExpOpsTest.java` (2735 lines).

### Summary table

| # | OCL name | Arity | Argument types (in order, arg0 = receiver) | Result type | Class | Class lines | `eval` lines | Form |
|---|---|---|---|---|---|---|---|---|
| 1 | `abs` | 1 | `UReal` | `UReal` | `Op_ureal_abs` | 40-67 | 62-66 | dot-call |
| 2 | `inv` | 1 | `UReal` | `UReal` | `Op_ureal_inv` | 74-107 | 96-106 | dot-call |
| 3 | `uncertainty` | 1 | `UReal` | `Real` | `Op_ureal_uncertainty` | 112-139 | 134-138 | dot-call |
| 4 | `setUncertainty` | 2 | `UReal`, (`Integer` \| `Real`) | `UReal` | `Op_ureal_setUncertainty` | 144-191 | 171-190 | dot-call |
| 5 | `neg` | 1 | `UReal` | `UReal` | `Op_ureal_neg` | 197-224 | 219-223 | dot-call |
| 6 | `value` | 1 | `UReal` | `Real` | `Op_ureal_value` | 229-256 | 251-255 | dot-call |
| 7 | `setValue` | 2 | `UReal`, (`Integer` \| `Real`) | `UReal` | `Op_ureal_setValue` | 261-303 | 284-302 | dot-call |
| 8 | `toReal` | 1 | `UReal` | `Real` | `Op_ureal_toReal` | 308-335 | 330-334 | dot-call |
| 9 | `toInteger` | 1 | `UReal` | `Integer` | `Op_ureal_toInteger` | 340-367 | 362-366 | dot-call |
| 10 | `toUInteger` | 1 | `UReal` | `UInteger` | `Op_ureal_toUInteger` | 373-400 | 395-399 | dot-call |
| 11 | `power` | 2 | `UReal`, (`Integer` \| `Real`) | `UReal` | `Op_ureal_power` | 406-454 | 433-453 | dot-call |
| 12 | `sqrt` | 1 | `UReal` | `UReal` | `Op_ureal_sqrt` | 461-496 | 483-495 | dot-call |
| 13 | `atan` | 1 | `UReal` | `UReal` | `Op_ureal_atan` | 501-536 | 523-535 | dot-call |
| 14 | `sin` | 1 | `UReal` | `UReal` | `Op_ureal_sin` | 541-568 | 563-567 | dot-call |
| 15 | `cos` | 1 | `UReal` | `UReal` | `Op_ureal_cos` | 572-599 | 594-598 | dot-call |
| 16 | `tan` | 1 | `UReal` | `UReal` | `Op_ureal_tan` | 604-640 | 626-639 | dot-call |
| 17 | `asin` | 1 | `UReal` | `UReal` | `Op_ureal_asin` | 645-680 | 667-679 | dot-call |
| 18 | `acos` | 1 | `UReal` | `UReal` | `Op_ureal_acos` | 685-720 | 707-719 | dot-call |

Argument-type notes that apply to the whole table:

* Where the table says `UReal` for arg0, `matches` tests `params[0].isTypeOfUReal()` — the **exact** type,
  not a kind-of test. `Real`/`Integer` receivers therefore fall through to the `Op_real_*`/`Op_integer_*`
  entries in the same multimap bucket.
* Where the table says "(`Integer` | `Real`)":
  * ops #4 and #7 test `params[1].isKindOfReal(Type.VoidHandling.EXCLUDE_VOID)`. In the fork's type
    lattice that predicate is `true` **only** for `RealType` (`RealType.java:50-52`) and `IntegerType`
    (`IntegerType.java:53-55`); `TypeImpl` defaults to `false` (`TypeImpl.java:215-217`), `VoidType`
    returns `h == INCLUDE_VOID` (`VoidType.java:48-50`), and `URealType`/`UIntegerType` do **not**
    override it. So a `UReal` or `UInteger` second argument does **not** type-check.
  * op #11 tests `params[1].isTypeOfInteger() || params[1].isTypeOfReal()` — exact types, same effective
    admissible set.

---

### 1. `abs` — `Op_ureal_abs`, lines 40-67

* `name()` `:42-44` → `"abs"`; `kind()` `:46-49` → `OPERATION`; `isInfixOrPrefix()` `:51-54` → `false`.
* `matches` `:56-60`: `params.length == 1 && params[0].isTypeOfUReal()` → `TypeFactory.mkUReal()`.
* `eval` `:62-66`: `URealValue.valueOf(args[0]).abs()`.
* **Semantics:** returns `UReal(|x|, u)` — the value component is replaced by its magnitude and the
  uncertainty component is **carried through unchanged** (`UReal.java:137-142`: `setX(Math.abs(x)); setU(u)`),
  which is correct because `|·|` is a rigid reflection and leaves the standard uncertainty invariant.
* **Special cases in `eval`: none.** No NaN/Infinity guard, no zero handling, no undefined test
  (contract-level, §1.2). `u` can never be negative on entry (the `UReal` ctor/`setU` absolutize,
  `UReal.java:41-43`, `:67-69`), so `abs` never changes `u`.
* Evidence: `.in:96-103` (`UReal(-2,3).abs() -> UReal(2.0, 3.0) : UReal`), test `:162-181`.
* Port note: `URealValue.valueOf(args[0])` (`URealValue.java:112-127`) is a no-op cast here because
  `matches` already pinned the receiver to `UReal`.

### 2. `inv` — `Op_ureal_inv`, lines 74-107

* `matches` `:90-94`: receiver `isTypeOfUReal()` → `mkUReal()`.
* `eval` `:96-106`: **`(URealValue) args[0]` — a raw cast, line 98.** This is the only op in the file that
  does not go through `URealValue.valueOf`; behaviourally equivalent given `matches`.
* **Semantics:** `uReal.inverse()` = `new UReal(1.0, 0.0).divideBy(this)` (`UReal.java:220-222`).
  Because the numerator is the exact scalar `(1,0)`, `divideBy` takes one of two branches
  (`UReal.java:106-135`):
  * receiver `u == 0` → result `(1/x, 0.0/x)` — an exact scalar reciprocal;
  * receiver `u != 0` → result `(1/x, u/x²)` — first-order propagation `|d(1/x)/dx|·u = u/x²`.
  So the uncertainty is divided by the square of the value; it is **amplified** for `|x| < 1`.
* **Special cases in `eval` (`:101-103`):** `if (Double.isInfinite(result.value()) || Double.isNaN(result.value())) throw new ArithmeticException();`
  → `Undefined`. Concretely a **zero receiver value** (`x == 0`) gives `1/0 = ±Infinity` → `Undefined`,
  for both `u == 0` and `u != 0` (probe: `inverse(0,0) -> Infinity/NaN`, `inverse(0,0.1) -> Infinity/Infinity`).
  **The uncertainty component is *not* guarded** — asymmetric with #11-#13, #16-#18. In practice no
  finite-value/non-finite-uncertainty result is reachable here (both `divideBy` branches produce a
  non-finite `u` only when `x == 0`, which already trips the value guard).
* Evidence: `.in:299-303` (`UReal(8,0.75).inv() -> UReal(0.125, 0.01171875)`, i.e. `0.75/64`;
  `UReal(0,0.5).inv() -> Undefined : OclVoid`), test `:493-505`.

### 3. `uncertainty` — `Op_ureal_uncertainty`, lines 112-139

* `matches` `:128-132`: receiver `isTypeOfUReal()` → `TypeFactory.mkReal()`.
* `eval` `:134-138`: `new RealValue(URealValue.valueOf(args[0]).uncertainty())`.
* **Semantics:** projects out the uncertainty component as a plain `Real`. The returned number is always
  `>= 0`, because `UReal`'s constructor and `setU` apply `Math.abs` (`UReal.java:41-43`, `:67-69`) — so
  `UReal(-3, -2.3).uncertainty()` is `2.3`, not `-2.3`.
* **Special cases in `eval`: none.** No guard; a confidence of `0` is returned as `0.0` like any other value.
* Evidence: `.in:131-135` (`UReal(-3, -2.3).uncertainty() -> 2.3 : Real`), test `:476-488`.

### 4. `setUncertainty` — `Op_ureal_setUncertainty`, lines 144-191

* `matches` `:160-169`: `params.length == 2 && params[0].isTypeOfUReal() && params[1].isKindOfReal(EXCLUDE_VOID)`
  → `mkUReal()`. Admissible arg1 types: `Integer`, `Real` only (see §3 notes).
* `eval` `:171-190`:
  * `:177-178` `if (args[1].isUndefined()) result = UndefinedValue.instance;` — **dead** (see §1.2).
  * `:181-184` reads the new uncertainty as `int` or `double` depending on `args[1].isInteger()`.
  * `:186` `result = new URealValue(uRealValue.value(), newUncertainty)`.
* **Semantics:** returns a new `UReal` with the **receiver's value component preserved** and the
  uncertainty component **replaced wholesale** by the argument, absolutized: the `URealValue(double,double)`
  ctor (`URealValue.java:18-21`) forwards to `new UReal(x,u)` which does `this.u = Math.abs(u)`
  (`UReal.java:41-43`). No propagation, no combination — this is the uncertainty *setter*.
* **Special cases in `eval`:** negative argument → absolutized (`setUncertainty(-3)` ≡ `setUncertainty(3)`);
  argument `0` → an exact/degenerate `UReal` (`u == 0`), which then changes the behaviour of every
  downstream operation that special-cases `u == 0` (`divideBy`, `calculate`); undefined argument →
  `Undefined` via `ExpStdOp`. No NaN/Infinity guard: a `Real` argument that is NaN/Infinity would be stored
  as-is — **UNVERIFIABLE** whether such a `RealValue` is constructible from OCL source in the fork
  (`3/0` throws `ArithmeticException` in `Op_number_div` `:429-430` and yields `Undefined` first, per `.in:148-149`).
* Evidence: `.in:139-149`
  (`UReal(-3,0).setUncertainty(-3) -> UReal(-3.0, 3.0)`; `.setUncertainty(3.0) -> UReal(-3.0, 3.0)`;
  `.setUncertainty(3 / 0) -> Undefined : OclVoid`), test `:2652-2688`.

### 5. `neg` — `Op_ureal_neg`, lines 197-224

* **The OCL name is `"neg"`** (`:200-202`), and `isInfixOrPrefix()` is `false` (`:208-211`), so this is the
  dot-call `x.neg()`. The header comment at line 196 reads `/* - : UReal -> UReal */`, which is **stale** —
  the infix/prefix unary minus for `UReal` is `Op_number_unaryminus` in
  `StandardOperationsNumber.java:505-548`. A port that reads only the comment will register the wrong symbol.
* `matches` `:213-217`: receiver `isTypeOfUReal()` → `mkUReal()`.
* `eval` `:219-223`: `URealValue.valueOf(args[0]).neg()`.
* **Semantics:** `UReal(-x, u)` — value negated, **uncertainty unchanged** (`UReal.java:144-149`), correct
  because negation is a rigid reflection.
* **Special cases in `eval`: none.** (Note `-0.0`: `URealValue.toString` corrects a negative zero to `0`
  for display, `URealValue.java:44-45`, but `neg` itself does not normalise it; `.in:242-243` shows
  `UReal(0.0, 2.3).neg() -> UReal(0.0, 2.3)`.)
* Evidence: `.in:238-246`, test `:186-205`.

### 6. `value` — `Op_ureal_value`, lines 229-256

* `matches` `:245-249`: receiver `isTypeOfUReal()` → `mkReal()`.
* `eval` `:251-255`: `new RealValue(URealValue.valueOf(args[0]).value())` = `UReal.getX()`.
* **Semantics:** projects out the value component and **discards the uncertainty entirely** (the result is
  a plain `Real`, so the confidence information is lost, not merged).
* **Special cases in `eval`: none.**
* Evidence: `.in:108-115`, test `:455-470`.
* Functionally identical to `toReal` (#8) — two registry entries, same observable result.

### 7. `setValue` — `Op_ureal_setValue`, lines 261-303

* `matches` `:277-282`: `params.length == 2 && params[0].isTypeOfUReal() && params[1].isKindOfReal(EXCLUDE_VOID)`
  → `mkUReal()`. Same admissible arg1 set as #4.
* `eval` `:284-302`:
  * `:290-297` if arg1 is defined: `newValue` from `IntegerValue.value()` or `RealValue.value()`;
    `result = new URealValue(newValue, uRealValue.uncertainty())`.
  * `:298-299` else `UndefinedValue.instance` — **dead** (see §1.2).
  * `:287` `double newValue = 0;` — if arg1 were neither integer nor real the new value would silently be
    `0.0`; unreachable given `matches`.
* **Semantics:** returns a new `UReal` with the **uncertainty component preserved unchanged** and the
  value component replaced wholesale by the argument. The dual of #4.
* **Special cases in `eval`:** undefined argument → `Undefined` (`.in:127-128`, `UReal(-2,3).setValue(3 / 0) -> Undefined : OclVoid`).
  No NaN/Infinity guard. Integer arguments are widened to `double`.
* Evidence: `.in:118-128`, test `:2618-2647`.

### 8. `toReal` — `Op_ureal_toReal`, lines 308-335

* `matches` `:324-328`: receiver `isTypeOfUReal()` → `mkReal()`.
* `eval` `:330-334`: `URealValue.toReal()` (`URealValue.java:250-252`) = `new RealValue(uReal.toReal())`,
  and `UReal.toReal()` is `return this.getX()` (`UReal.java:652-654`).
* **Semantics:** conversion `UReal → Real` that keeps the value component and **drops the uncertainty**
  (no rounding, no widening of any interval).
* **Special cases in `eval`: none.**
* Evidence: `.in:548-566` (`UReal(3,5).toReal() -> 3.0 : Real`), test `:73-111`.

### 9. `toInteger` — `Op_ureal_toInteger`, lines 340-367

* `matches` `:356-360`: receiver `isTypeOfUReal()` → `mkInteger()`.
* `eval` `:362-366`: `URealValue.toInteger()` (`URealValue.java:254-256`) = `IntegerValue.valueOf(uReal.toInteger())`,
  and `UReal.toInteger()` is `(int) Math.floor(this.getX())` (`UReal.java:648-650`).
* **Semantics:** conversion `UReal → Integer` by **flooring toward −∞** (not truncation toward zero) of the
  value component; the uncertainty is **discarded**. Probe: `toInteger(-3.2, 0.2) = -4`.
* **Special cases in `eval`: none.** No NaN/Infinity guard, and **no range check** — the `(int)` narrowing
  of an out-of-`int`-range double saturates to `Integer.MIN_VALUE`/`MAX_VALUE` silently (Java semantics).
* Evidence: `.in:572-590` (`UReal(0.5, 3.2).toInteger() -> 0`; `UReal(-2, 2).toInteger() -> -2`), test `:29-67`.
* **Inconsistency to preserve or fix deliberately:** `toInteger` floors, but `toUInteger` (#10) truncates —
  they disagree on negative non-integral values.

### 10. `toUInteger` — `Op_ureal_toUInteger`, lines 373-400

* `matches` `:389-393`: receiver `isTypeOfUReal()` → `TypeFactory.mkUInteger()`.
* `eval` `:395-399`: `URealValue.toUInteger()`.
* **Semantics — the fork deliberately does *not* use the library conversion.**
  `URealValue.toUInteger()` (`URealValue.java:258-260`) is
  `return new UIntegerValue((int) value(), uncertainty());`
  i.e. **C-style truncation toward zero** of the value component and the uncertainty **carried over
  unchanged**. The library's `UReal.toUInteger()` (`UReal.java:656-661`) instead **floors** and **inflates**
  the uncertainty by the discarded fractional residue, `u' = sqrt(u² + (x − floor x)²)`. Probe, on the
  oracle jar: library `toUInteger(-3.7, 0.2) = (-4, 0.3605551275463988)`, whereas the fork's wrapper yields
  `(-3, 0.2)`.
  The fork's golden files pin the **fork** behaviour: `.in:596-606`
  `UReal(-5.3, 3.75).toUInteger() -> UInteger(-5, 3.75) : UInteger` (truncation to `-5`, `u` unchanged),
  test `:2690-2734` asserts the same. **Do not "fix" this by calling the library method — it would break
  the historical oracle.**
* **Special cases in `eval`: none.** No NaN/Infinity guard, no range check on the `(int)` narrowing.
  `UReal(0, -5).toUInteger() -> UInteger(0, 5.0)` (`.in:602-603`) — the `-5` was already absolutized at
  construction, not by this op.

### 11. `power` — `Op_ureal_power`, lines 406-454

* Header comments `:404-405` document both `UReal x Integer -> UReal` and `UReal x Real -> UReal`.
* `matches` `:422-431`: `params.length == 2 && params[0].isTypeOfUReal() && (params[1].isTypeOfInteger() || params[1].isTypeOfReal())`
  → `mkUReal()`. A `UReal` exponent does **not** type-check.
* `eval` `:433-453`:
  * `:437` the exponent local is declared **`float`**, not `double`.
  * `:439-442` `IntegerValue.value()` (an `int`) is widened to `float`; a `RealValue.value()` (a `double`)
    is **narrowed** via `(float)`.
  * `:444` `result = uRealValue.power(exponent)` → `UReal.power(float s)` (`UReal.java:151-161`).
* **Semantics:** `UReal(xˢ, |s · u · x^(s−1)|)`. The value component is `Math.pow(x, s)` only — the
  second-order correction term `b = (s(s−1)/2)·x^(s−2)·u²` **is computed at `UReal.java:155` and then
  discarded**, because line 156 is `result.setX(a); //result.setX(a + b);`. The uncertainty component is
  the first-order propagation `|∂(xˢ)/∂x|·u = |s·u·x^(s−1)|`, absolutized by `setU`.
* **Special cases in `eval` (`:446-450`) — two separate guards, both throwing `ArithmeticException` → `Undefined`:**
  1. `Double.isNaN(result.value()) || Double.isInfinite(result.value())`
  2. `Double.isNaN(result.uncertainty()) || Double.isInfinite(result.uncertainty())`

  Reachable cases, all corroborated by `.in:173-218` and by probe:

  | receiver | exponent | why | result |
  |---|---|---|---|
  | `x < 0` | non-integral | `Math.pow` → NaN | `Undefined` (`.in:224-225` relies on this) |
  | `x = 0` | `s < 0` | `0^s = +Infinity` | `Undefined` (`.in:179-180`, `:191-192`) |
  | `x = 0`, `u > 0` | `s = 0` | `u' = 0·u·0^(−1) = 0·∞ = NaN` | `Undefined` (`.in:185-186`) |
  | `x = 0`, `u = 0` | `s = 0` | value `1.0` is fine, but `u' = 0·0·∞ = NaN` | `Undefined` (`.in:173-174`, test `:288-293`) |
  | `x = 0`, any `u` | `s > 0` | `u' = s·u·0^(s−1) = 0` | `UReal(0.0, 0.0)` (`.in:176-177`, `:188-189`, `:194-195`) |
  | `x ≠ 0`, `u` any | `s = 0` | `u' = 0` | `UReal(1.0, 0.0)` (`.in:197-198`, `:209-210`) |

  Pinned non-trivial values: `UReal(2,4).power(4) -> UReal(16.0, 128.0)` (`4·4·2³ = 128`, `.in:212-213`);
  `UReal(1,3).power(-2) -> UReal(1.0, 6.0)` (`|−2·3·1⁻³| = 6`, `.in:215-216`);
  `UReal(1,2).power(0.25) -> UReal(1.0, 0.5)` (`.in:218-219`).
* **Float-precision hazard for the port:** the `(float)` narrowing at `:442` means a `Real` exponent is
  evaluated at `float` precision (e.g. `0.1` becomes `0.100000001490116…`), and an `Integer` exponent above
  2²⁴ loses precision. This is observable and must be reproduced verbatim if bit-identical results are
  required. The fork itself documents a related mismatch at `.in:229-234`: `UReal(2,3).power(1/2)` and
  `UReal(2,3).sqrt()` **do not** agree (`1.016465997955662` vs `1.4142135623730951`), and that assertion is
  commented out with a `FIXME`.

### 12. `sqrt` — `Op_ureal_sqrt`, lines 461-496

* `matches` `:477-481`: receiver `isTypeOfUReal()` → `mkUReal()`.
* `eval` `:483-495`: `uRealValue.sqrt()` → `UReal.sqrt()` (`UReal.java:163-177`).
* **Semantics:**
  * `x == 0 && u == 0` → the exact result `UReal(0.0, 0.0)` (explicit special case, `UReal.java:165-168`).
  * otherwise `UReal(√x, u / (2√x))` — first-order propagation. As with `power`, a second-order term is
    written and **discarded** (`UReal.java:171`, `double b = 0.0; //…`; then `setX(a - b)`).
* **Special cases in `eval` — note the copy/paste slip:**
  * `:488` `if (Double.isNaN(result.value()) || Double.isInfinite(result.`**`uncertainty()`**`)) throw …`
    — the second disjunct tests the *uncertainty* for infinity, not the value.
  * `:491` `if (Double.isNaN(result.uncertainty()) || Double.isInfinite(result.uncertainty())) throw …`

  Net observable behaviour: `Undefined` iff the value is NaN (`x < 0`) **or** the uncertainty is NaN/Infinite
  (`x == 0` with `u > 0`, since `u/(2·0) = +∞`). An infinite *value* with a finite uncertainty would slip
  through both guards; that is unreachable for finite `x` (`√finite` is finite), so it is a latent defect,
  not an observable one.
* Evidence: `.in:154-168` — `UReal(-3,2.3).sqrt() -> Undefined`; `UReal(0,2).sqrt() -> Undefined`;
  `UReal(4,0).sqrt() -> UReal(2.0, 0.0)`; `UReal(4,2).sqrt() -> UReal(2.0, 0.5)`. Test `:420-451` matches.
* **Stale skip to be aware of:** both the `.in` (`:157-159`) and the test (`:429-432`) comment out the
  `UReal(0,0).sqrt() -> UReal(0.0, 0.0)` case with a note "TODO descomentar cuando se arregle / se actualice
  la librería". Against the jar that `build.xml:50` actually binds, that case **does** already return
  `(0.0, 0.0)` (probe: `sqrt(0,0) -> x=0.0 u=0.0`). The skip is obsolete; the port may re-enable it, but
  should do so as a deliberate, documented decision rather than silently.

### 13. `atan` — `Op_ureal_atan`, lines 501-536

* `matches` `:517-521`: receiver `isTypeOfUReal()` → `mkUReal()`.
* `eval` `:523-535`: `uRealValue.atan()` → `UReal.atan()` (`UReal.java:197-202`).
* **Semantics:** `UReal(atan x, u / (1 + x²))` — first-order propagation with `d(atan x)/dx = 1/(1+x²)`;
  the uncertainty is always **contracted** (the factor is ≤ 1). Probe: `atan(0.5,0.1) -> u = 0.08`.
* **Special cases in `eval` (`:528-532`):** two guards, value NaN/Infinite → `Undefined`, uncertainty
  NaN/Infinite → `Undefined`. Both are unreachable for finite inputs (`atan` is total and bounded, and
  `1+x² ≥ 1`), so the guards are defensive only.
* **No fork test or `.in` coverage** — see §5.

### 14. `sin` — `Op_ureal_sin`, lines 541-568

* `matches` `:557-561`: receiver `isTypeOfUReal()` → `mkUReal()`.
* `eval` `:563-567`: `return uRealValue.sin();` — **no guards at all**.
* **Semantics:** `UReal(sin x, |u · cos x|)` (`UReal.java:179-184`; the absolute value comes from `setU`).
  First-order propagation. At `x = π/2 + kπ` the uncertainty collapses to ~0 even for large `u`, because
  `cos x ≈ 0` — a genuine property of the linearisation, and a place where a naive port that clamps or
  averages will diverge.
* **Special cases in `eval`: none** — no NaN/Infinity guard, unlike `tan`/`asin`/`acos`/`atan`. Harmless,
  since `sin` is total on the reals.
* **No fork test or `.in` coverage** — see §5.

### 15. `cos` — `Op_ureal_cos`, lines 572-599

* `matches` `:588-592`: receiver `isTypeOfUReal()` → `mkUReal()`.
* `eval` `:594-598`: `return uRealValue.cos();` — **no guards at all**.
* **Semantics:** `UReal(cos x, |u · sin x|)` (`UReal.java:186-191`). The library writes `setU(u*Math.sin(x))`
  — i.e. `+sin`, dropping the minus sign of `d(cos x)/dx = −sin x` — but `setU`'s `Math.abs` makes this
  equal to the correct magnitude `|u · sin x|` regardless. Probe: `cos(2.0,0.1) -> u = 0.09092974268256818 = |0.1·sin 2|`.
* **Special cases in `eval`: none.**
* **No fork test or `.in` coverage** — see §5.

### 16. `tan` — `Op_ureal_tan`, lines 604-640

* `matches` `:620-624`: receiver `isTypeOfUReal()` → `mkUReal()`.
* `eval` `:626-639`: `uRealValue.tan()` → `UReal.tan()`, which is **not** a closed formula but
  `return this.sin().divideBy(this.cos());` (`UReal.java:193-195`).
* **Semantics — this is the subtle one.** With `s = (sin x, |u·cos x|)` and `c = (cos x, |u·sin x|)`,
  `UReal.divideBy` (`UReal.java:106-135`) picks a branch:
  * `c.u == 0` (i.e. `u = 0` or `sin x = 0`) → `(sin x / cos x, |u·cos x| / cos x)`;
  * else `s.u == 0` (i.e. `cos x = 0`) → `(sin x / cos x, |u·sin x| / cos²x)`;
  * else both uncertain → value `sin x / cos x` and
    `u' = sqrt( (u·cos x)² / |cos x| + (sin²x · (u·sin x)²) / cos⁴x )`.

  Note the third branch is **not** the textbook `u/cos²x`, and its first term has `|cos x|` (not squared) in
  the denominator — an artefact of the library's `divideBy`. Probe: `tan(0.5, 0.1) -> (0.5463024898437905, 0.09831850394390179)`,
  whereas `u/cos²x` would be `0.1/0.7702² = 0.1685`. **Any port must route `tan` through `sin`/`cos`/`divideBy`
  to reproduce this number; a direct `u/cos²x` implementation will silently disagree.**
* **Special cases in `eval` (`:631-636`):** a `// FIXME: refractorize after studing better the case.`
  comment at `:631`, then value NaN/Infinite → `Undefined`, uncertainty NaN/Infinite → `Undefined`.
  In IEEE doubles `Math.tan(Math.PI/2)` is finite-but-huge (probe: `1.633123935319537E16`, `u = 2.667e31`),
  so **the guards do not fire at the poles** and `tan` near π/2 returns an enormous, finite `UReal`
  rather than `Undefined`.
* **No fork test or `.in` coverage** — see §5.

### 17. `asin` — `Op_ureal_asin`, lines 645-680

* `matches` `:661-665`: receiver `isTypeOfUReal()` → `mkUReal()`.
* `eval` `:667-679`: `uRealValue.asin()` → `UReal.asin()` (`UReal.java:212-218`).
* **Semantics:** `UReal(asin x, u / √(1 − x²))` — **except** when `Math.abs(x) == 1.0` exactly, where the
  library sidesteps the singularity and sets `u' = u` unchanged (`UReal.java:215-216`). Probe:
  `asin(1, 0.1) -> (1.5707963267948966, 0.1)`, `asin(0.5, 0.1) -> u = 0.11547005383792516`.
* **Special cases in `eval` (`:672-676`):** value NaN/Infinite → `Undefined`; uncertainty NaN/Infinite →
  `Undefined`. Reachable: `|x| > 1` → `Math.asin` = NaN → `Undefined` (probe: `asin(2, 0.1) -> NaN/NaN`).
  The `|x| == 1` case is *not* undefined thanks to the library's special case.
* **No fork test or `.in` coverage** — see §5.

### 18. `acos` — `Op_ureal_acos`, lines 685-720

* `matches` `:701-705`: receiver `isTypeOfUReal()` → `mkUReal()`.
* `eval` `:707-719`: `uRealValue.acos()` → `UReal.acos()` (`UReal.java:204-210`).
* **Semantics:** identical shape to `asin`: `UReal(acos x, u / √(1 − x²))`, and `u' = u` unchanged when
  `Math.abs(x) == 1.0` exactly (`UReal.java:207-208`). Probe: `acos(1, 0.1) -> (0.0, 0.1)`,
  `acos(0.5, 0.1) -> u = 0.11547005383792516`.
* **Special cases in `eval` (`:712-716`):** same two guards; `|x| > 1` → `Undefined`
  (probe: `acos(2, 0.1) -> NaN/NaN`).
* **No fork test or `.in` coverage** — see §5.

---

## 4. Cross-check against the 7.5.0 registry

### 4.1 `OpGeneric` itself: **no member signature differs**

Verified by diff modulo line endings (run):

```bash
diff -u <(sed 's/\r$//' <fork>/OpGeneric.java) <(sed 's/\r$//' <7.5.0>/OpGeneric.java)
```

The **only** hunk is the removal of the six uncertainty registration lines
(`// Uncertainty Types` + `StandardOperationsUReal/UBoolean/UInteger/UString/SBoolean.registerTypeOperations(opmap);`),
fork `OpGeneric.java:92-97`. Everything else — the `OPERATION = 0` / `SPECIAL = 3` constants, `name()`,
`isBooleanOperation()`, `kind()`, `isInfixOrPrefix()`, `matches(Type[])`,
`checkWarningUnrelatedTypes(Expression[])`, `eval(EvalContext, Value[], Type)`, `stringRep(Expression[], String)`,
and both `registerOperation` overloads — is identical. **No operation in this file needs a signature change
to satisfy the 7.5.0 `OpGeneric` contract.** (A trivial mechanical note: 7.5.0's file is stored with **CRLF**
line terminators, the fork's with LF — `file` on both, run.)

`ExpStdOp.eval`'s undefined/`ArithmeticException` handling is likewise identical
(fork `:288-322` vs 7.5.0 `:281-315`).

### 4.2 What 7.5.0 is missing — the operations cannot compile as-is

None of the following exists anywhere in `use-core/src/main/java/org/tzi/use/uml/ocl/`
(`grep -rln "UReal\|UInteger\|UBoolean\|uDataTypes"` over that tree, run: **no matches**):

| Used by this file | 7.5.0 status | Fork definition |
|---|---|---|
| `Type.isTypeOfUReal()` | absent (`Type.java` has no uncertainty predicates) | fork `Type.java:92` |
| `Type.isKindOfUReal(VoidHandling)` | absent | fork `Type.java:94` |
| `TypeFactory.mkUReal()` | absent | fork `TypeFactory.java:93-95` (returns `Type`, not `URealType` — an inconsistency with its `mkUInteger()`/`mkUBoolean()` siblings) |
| `TypeFactory.mkUInteger()` | absent | fork `TypeFactory.java:83` |
| `URealType`, `UncertainType` | absent from `ocl/type/` | fork `type/URealType.java` (39 lines), `type/UncertainType.java` |
| `URealValue`, `UIntegerValue`, `UncertainValue` | absent from `ocl/value/` | fork `value/URealValue.java` (281 lines) |
| `Value.isUReal()` / `isUInteger()` | absent (`Value.java` has `isInteger/isReal/isBoolean/...` only) | fork `value/Value.java:84`, `:67` |

Available unchanged in 7.5.0 and used by this file: `Type.VoidHandling` (`Type.java:33`),
`Type.isKindOfReal(VoidHandling)` (`:92`), `Type.isTypeOfInteger()` (`:84`), `Type.isTypeOfReal()` (`:94`),
`TypeFactory.mkReal()`, `TypeFactory.mkInteger()`, `new RealValue(double)` (`RealValue.java:34`),
`IntegerValue.valueOf(int)` (`IntegerValue.java:102`), `UndefinedValue.instance`, `EvalContext`.
Fork-only helper `RealValue.valueOf(Value)` (fork `RealValue.java:90`) is **not** used by this file.

### 4.3 Style conventions the port should follow

7.5.0's own registries use exactly the structure this file already has: a registry class with a static
`registerTypeOperations(Multimap<String,OpGeneric>)` followed by top-level package-private
`final class Op_… extends OpGeneric` declarations (`use-core/.../StandardOperationsNumber.java:16`, then
`:81, 121, 161, 198, 245, 277, 309, …`). One deviation to fix: the fork declares
`public class StandardOperationsUReal` (`StandardOperationsUReal.java:10`), whereas the closest 7.5.0
analogue is package-private `class StandardOperationsNumber` (`:16`). (7.5.0 is itself inconsistent — most
other registries are `public`.)

### 4.4 **Resolution hazard: 7.5.0 added a `sqrt` that will shadow `Op_ureal_sqrt`**

`ExpStdOp.create` resolves **first match wins** in registration order within a name bucket
(`use-core/.../ExpStdOp.java:114-121`, iterating `opmap.get(name)` on an `ArrayListMultimap` created at `:53`).

7.5.0's `StandardOperationsNumber` contains **two operations the fork's version does not have**:

* `Op_number_sqrt` (`use-core/.../StandardOperationsNumber.java:848-879`), name `"sqrt"`, registered at `:32`.
  Its `matches` is `params[0].isKindOfNumber(VoidHandling.EXCLUDE_VOID)` → `TypeFactory.mkInteger()`
  (`:865-868` — note it declares `Integer` but its `eval` returns a `RealValue`, an upstream quirk), and its
  `eval` casts `args[0]` to `IntegerValue`/`RealValue` (`:873-876`).
* `Op_number_pow` (`:802-844`), name `"pow"`, `matches` both params `isKindOfNumber(EXCLUDE_VOID)` →
  `mkReal()` (`:819-823`); registered at `:31`.

Because the fork's `URealType.isKindOfNumber(...)` returns `true` (`URealType.java:28-30`) and
`StandardOperationsNumber` is registered **before** the uncertainty registries in `registerOperations`
(fork `OpGeneric.java:88` vs `:93`), porting the fork faithfully onto 7.5.0 would make
`UReal(4,2).sqrt()` resolve to `Op_number_sqrt`, type as `Integer`, and then throw `ClassCastException`
at evaluation — instead of returning `UReal(2.0, 0.5)` as `.in:167-168` requires. **This must be handled
explicitly**, by one of: tightening `Op_number_sqrt.matches` to exclude `UncertainType`; registering the
uncertainty ops first; or teaching `Op_number_sqrt` about `UReal`. The same shadowing applies to
`pow` (no fork counterpart, so `UReal(2,3).pow(2)` would type-check and then `ClassCastException`).

The same `isKindOfNumber`-based `matches` in 7.5.0 will capture `UReal` for the *other* number
operations too — `/` (`:214-218`), `floor` (`:395-398`), `round` (`:433-436`), `<`, `>`, `<=`, `>=`
(`:626-631`, and the parallel blocks at `:659`, `:707`, `:755`), `-` unary (`:326-329`) — all of which the
fork had to modify in place. `toString` (`:898-903`) is the one that works unmodified. `ArithOperation.matches`
in 7.5.0 (`:63-73`) falls through to `mkReal()` for two kind-of-number params, so `+`/`-`/`*`/`max`/`min`
on `UReal` would type as `Real` and then `ClassCastException`. All of that is the subject of the
`StandardOperationsNumber` port, not this file, but it determines whether these 18 ops are reachable at all.

---

## 5. Coverage gaps in the fork's own oracles

`grep -rn "sin()\|cos()\|tan()" <fork>/src/test/` (run) returns **nothing**. There is:

* **No test and no `.in` golden case for any of `sin`, `cos`, `tan`, `asin`, `acos`, `atan`** — 6 of the 18
  operations (33%) are entirely unpinned by the historical suite. Their semantics in §3 come from the
  library source plus my probe against the oracle jar, **not** from a fork-authored expectation.
* Good coverage for `abs`, `neg`, `value`, `setValue`, `uncertainty`, `setUncertainty`, `sqrt`, `power`,
  `inv`, `toReal`, `toInteger`, `toUInteger` — both in `URealExpression.in` and in `URealExpOpsTest.java`
  (see per-op "Evidence" lines).
* Two deliberately disabled cases that a port will trip over: `UReal(0,0).sqrt()` (`.in:157-159`,
  test `:429-432`) and `UReal(2,3).power(1/2) = UReal(2,3).sqrt()` (`.in:229-234`).

---

## 6. Port checklist distilled

1. Build the type/value substrate first (§4.2): `URealType`/`UncertainType`, `TypeFactory.mkUReal()/mkUInteger()`,
   `Type.isTypeOfUReal()/isKindOfUReal()`, `Value.isUReal()`, `URealValue` (with the fork's `toUInteger`
   override, §3 #10), `UIntegerValue`.
2. Resolve the `sqrt` / `pow` shadowing introduced by 7.5.0's `StandardOperationsNumber` (§4.4) **before**
   trusting any `sqrt` result.
3. Port the 18 classes unchanged in shape — the `OpGeneric` contract is identical (§4.1). Keep
   `kind() == OPERATION` and the `throw new ArithmeticException()` idiom; do not convert them to explicit
   `UndefinedValue` returns, since `ExpStdOp` distinguishes the two paths.
4. Preserve verbatim, as they are observable: `power`'s `float` exponent (`:437`), `tan` = `sin/cos` via
   `divideBy`, `toUInteger`'s truncation-toward-zero, `toInteger`'s floor, `neg`'s name `"neg"`, and
   `sqrt`'s guard asymmetry (`:488`).
5. Decide explicitly what to do about the six untested trig ops (§5) — they are the highest-risk part of
   this file.

---

## Independent refutation

Derived independently from `StandardOperationsUReal.java` (read in full, lines 1-720) **before** reading
anything above, then diffed against the table above. Every claim below names a file+line or the shell
command that produced it. All probe runs are against the oracle jar
`.../USE-Uncertainty/lib/atenearesearchgroup.uncertainty.jar` (`md5sum`: `a3055f54205babaa27484fa94efdda1c`).

### R.0 Verdict on the count and the shape of the table: **the count is right**

My independent enumeration of `registerTypeOperations` (`StandardOperationsUReal.java:15-32`) is, in
registration order:

```
abs, sin, cos, tan, asin, acos, atan, uncertainty, setUncertainty, value,
setValue, neg, power, sqrt, inv, toReal, toInteger, toUInteger
```

**18 operations, 18 distinct OCL names.** Proof command (run; prints `18 18 18`):

```bash
F=/home/xoruser/msc-4/use-msc2026/.git/reference-repositories/uncertainty/USE-Uncertainty/src/main/org/tzi/use/uml/ocl/expr/operations/StandardOperationsUReal.java
echo $(grep -cE '^[[:space:]]*OpGeneric\.registerOperation\(new Op_ureal_[A-Za-z]+\(\), opmap\);$' $F) \
     $(grep -cE '^final class Op_ureal_[A-Za-z]+ extends OpGeneric \{$' $F) \
     $(grep -oP '(?<=return ")[^"]+(?=";)' $F | sort -u | wc -l)
```

I found **no missed operation, no invented operation, no wrong arity, and no wrong argument or result
type.** There is no loop, no shared helper, and no multi-name registration in this file (every class calls
the one-argument `OpGeneric.registerOperation(op, opmap)` overload, so each name comes from exactly one
`name()` body; `grep -oP '(?<=return ")[^"]+(?=";)' $F | wc -l` is also `18`, i.e. no duplicate name
strings). `grep -c 'return OPERATION;'` = 18 and `grep -c 'return false;'` = 18 confirm the "all
`OPERATION`, none infix/prefix" claim. Per-class line ranges and `eval` ranges in the summary table were
checked one by one against `grep -nE '^final class'` and all match.

Independently re-verified and **agreed**: the `inv` raw cast at `:98`; the `sqrt` guard slip at `:488`
(`isInfinite(result.uncertainty())` where `isInfinite(result.value())` was meant); `power`'s `float`
exponent at `:437`; `neg`'s OCL name being `"neg"` against a stale `/* - */` comment at `:196`;
`toInteger` flooring (`UReal.java:648-650`) vs `toUInteger` truncating (`URealValue.java:258-260`) and the
fork deliberately bypassing `UReal.toUInteger()` (`UReal.java:656-661`); the `tan` = `sin/cos` routing
through `UReal.divideBy` (`UReal.java:193-195`, `:106-135`) and the three-branch uncertainty formula; the
`asin`/`acos` `|x| == 1` sidestep (`UReal.java:215-216`, `:207-208`); the dead `isUndefined` branches
(`ExpStdOp.java:293-314` short-circuits on *any* undefined arg including the receiver, so `eval` is never
entered); `isKindOfReal(EXCLUDE_VOID)` being true only for `RealType`/`IntegerType`
(`UnlimitedNaturalType.java:32` extends `BasicType` and does not override it, so the "same effective
admissible set" claim for #4/#7 vs #11 holds); zero trig coverage
(`grep -rn "sin()\|cos()\|tan()" src/test/` returns nothing); and §4.4's `Op_number_sqrt`
(`use-core/.../StandardOperationsNumber.java:848-879`, `matches` `isKindOfNumber` → `mkInteger`, registered
`:32`) / `Op_number_pow` (`:802-844`, registered `:31`) shadowing hazard — the fork's own Number registry
has neither (`grep -oP '(?<=return ")[^"]+(?=";)'` on the fork's `StandardOperationsNumber.java` yields
`* + - / < <= > >= abs div floor max min mod round toString`; 7.5.0 additionally yields `pow` and `sqrt`).
I also checked the one hazard §4.4 does *not* list, `abs`: it is safe, because both the fork's and 7.5.0's
`Op_real_abs`/`Op_integer_abs` use exact `isTypeOfReal()`/`isTypeOfInteger()` matches, so they cannot
capture a `UReal` receiver.

**But `agrees` is false**: three substantive discrepancies follow.

---

### R.1 **WRONG — §3 #11, `power` special-case table, row "`x = 0`, any `u` | `s > 0`"**

The table above asserts:

| receiver | exponent | why | result |
|---|---|---|---|
| `x = 0`, any `u` | `s > 0` | `u' = s·u·0^(s−1) = 0` | `UReal(0.0, 0.0)` |

This is a **over-generalisation from two data points** (`.in:176-177` and `:188-189`, both `s = 3`, plus
`:194-195`, `s = 3.5`). `u' = s·u·Math.pow(0, s−1)` (`UReal.java:157`) is `0` only when `s > 1`. The row is
false on both remaining sub-ranges of `s > 0`:

* **`s = 1`** — `Math.pow(0, 0)` is `1.0` in Java, so `u' = 1·u·1 = u`. The result is `UReal(0.0, u)`, not
  `UReal(0.0, 0.0)`.
* **`0 < s < 1`** — `Math.pow(0, s−1)` is `+Infinity`, so `u' = s·u·∞`, which is `+Infinity` for `u > 0` and
  `NaN` for `u = 0`. Either way the **second guard at `:449-450` fires** and the result is **`Undefined`**,
  not `UReal(0.0, 0.0)`.

Probe (compiled against the oracle jar with
`javac -cp <jar> P.java && java -cp .:<jar> P`, source at
`/tmp/claude-1000/-home-xoruser-msc-4/5a883e17-9055-4019-8f36-a743005556fa/scratchpad/refprobe/P.java`):

```
power(0,4)^1    -> x=0.0 u=4.0          <-- table predicts u=0.0
power(0,4)^0.5  -> x=0.0 u=Infinity     <-- guard :449 fires -> Undefined
power(0,0)^0.5  -> x=0.0 u=NaN          <-- guard :449 fires -> Undefined
power(0,4)^0.99 -> x=0.0 u=Infinity     <-- guard :449 fires -> Undefined
power(0,4)^2    -> x=0.0 u=0.0          (row correct only from s>1 up)
power(0,4)^3    -> x=0.0 u=0.0
Math.pow(0,0)=1.0   Math.pow(0,-0.5)=Infinity
```

The fork's **own golden file contradicts the row too**: `URealExpression.in:227-228` is

```
UReal(0, 5).power(1/2).equals( UReal(0, 5).sqrt() )
-> true : Boolean
```

`1/2` types as `Real` (fork `StandardOperationsNumber.java:344-362`, `Op_number_div.matches` returns
`mkReal()` for two `Integer`s), so this is `x = 0, u = 5, s = 0.5 > 0`. The table predicts
`UReal(0.0, 0.0)`; the pinned behaviour is `Undefined` on both sides (`Op_identical.eval`,
`StandardOperationsAny.java:163-167`, returns `true` when `args[0].isUndefined() && args[1].isUndefined()`).

**Corrected row** (replace the single `s > 0` row with three):

| receiver | exponent | `u' = s·u·0^(s−1)` | result |
|---|---|---|---|
| `x = 0`, any `u` | `s > 1` | `0^(s−1) = 0` → `u' = 0` | `UReal(0.0, 0.0)` |
| `x = 0`, any `u` | `s = 1` | `0^0 = 1` → `u' = u` | `UReal(0.0, u)` — **unpinned by any oracle** |
| `x = 0`, any `u` | `0 < s < 1` | `0^(s−1) = ∞` → `u' = ∞` (`u>0`) or `NaN` (`u=0`) | `Undefined` (`.in:227-228`) |

The `s = 1` case is not covered by `.in` or by `URealExpOpsTest.testURealPower` (`:283-419`, inspected) —
it is a genuine, previously unrecorded oracle gap in this file's highest-traffic operation.

### R.2 **WRONG CAUSE and WRONG NUMBERS — §3 #11 "Float-precision hazard", and the §5 / §6 claims that depend on it**

The document writes:

> The fork itself documents a related mismatch at `.in:229-234`: `UReal(2,3).power(1/2)` and
> `UReal(2,3).sqrt()` **do not** agree (`1.016465997955662` vs `1.4142135623730951`) …

placed under the `(float)`-narrowing bullet, and repeats it in §5 as one of "two deliberately disabled
cases that a port will trip over" and in §6 item 4. Three errors:

1. **The `(float)` narrowing is not the cause and cannot be.** The exponent is `1/2 = 0.5`, which is
   *exactly* representable in `float`. (So are every other exponent pinned in `.in`: `0, 3, −2, 3.5, 1.5,
   4, 0.25`. The narrowing hazard at `:442` is real as a *statement about the code*, but it has **zero
   observable effect anywhere in the fork's own oracle** — the document should say so rather than cite an
   unrelated FIXME as its illustration.)
2. **The real cause is the discarded second-order term** `b` at `UReal.java:155-156`. `1.016465997955662`
   is exactly `a + b` for `x=2, u=3, s=0.5`: `sqrt(2) + ((0.5·−0.5)/2)·2^(−1.5)·3² = 1.4142135623730951 −
   0.39774756441743296 = 1.016465997955662` (computed; scratchpad `refprobe/R.java`). The `.in` comment was
   written against a library build in which `power` still did `result.setX(a + b)`; the current source has
   that commented out (`result.setX(a); //result.setX(a + b);`).
3. **The stated numbers do not describe the oracle jar.** Probe against the binding jar:

   ```
   power(2,3)^0.5  -> x=1.4142135623730951 u=1.0606601717798214
   sqrt(2,3)       -> x=1.4142135623730951 u=1.0606601717798212
   ```

   The **values are bit-identical**; only the *uncertainty* differs, by 1 ULP. And `URealValue.equals`
   (`URealValue.java:66-90`) compares after `MathUtil.round(·,10)` (`MathUtil.java:106-109`), which maps
   both `1.0606601717798214` and `1.0606601717798212` to `1.0606601718` (computed). So against the jar that
   `build.xml:50` binds, the disabled assertion `UReal(2,3).power(1/2).equals(UReal(2,3).sqrt())` would
   evaluate to **`true`** — i.e. it is *not* a case "a port will trip over"; it is a second obsolete skip,
   exactly like the `UReal(0,0).sqrt()` one the document correctly identifies at `.in:157-159` /
   test `:429-433`. §5's second bullet and §6 item 4 should be rewritten accordingly.

### R.3 **FACTUALLY WRONG — §2, "there is no copy of the oracle jar in `use-core/src/test/resources/historical/`"**

The document states:

> Note: there is **no** copy at `use-core/src/test/resources/historical/atenearesearchgroup.uncertainty.jar`
> in this working tree — `md5sum` reported `No such file or directory` for that path.

It is there, and it is byte-identical to the fork's `lib/` copy (run):

```
$ md5sum /home/xoruser/msc-4/use-msc2026/use-core/src/test/resources/historical/atenearesearchgroup.uncertainty.jar \
         /home/xoruser/msc-4/use-msc2026/.git/reference-repositories/uncertainty/USE-Uncertainty/lib/atenearesearchgroup.uncertainty.jar
a3055f54205babaa27484fa94efdda1c  .../use-core/src/test/resources/historical/atenearesearchgroup.uncertainty.jar
a3055f54205babaa27484fa94efdda1c  .../USE-Uncertainty/lib/atenearesearchgroup.uncertainty.jar
```

(`ls -la` on that directory also shows `use.jar`, 1 440 303 bytes.) This matters for the port plan: the
oracle jar **is already vendored into the target repo's test resources**, so §6 does not need a
"obtain/vendor the jar" step, and test-time classpath work can point at the in-tree copy.

### R.4 Minor / completeness gaps (not counted as substantive)

* **§3 #4 vs #7 asymmetry not noted.** `Op_ureal_setUncertainty.eval:181-184` reads
  `if (args[1].isInteger()) … else ((RealValue) args[1]).value();` — an **unconditional** `RealValue`
  cast — whereas `Op_ureal_setValue.eval:291-294` uses `else if (args[1].isReal())` with a
  `double newValue = 0` fallback (`:287`). The document flags the `setValue` fallback but calls
  `setUncertainty` "the dual of #4", glossing this over. Both paths are unreachable given `matches`, so it
  is cosmetic — but a port that "harmonises" the two bodies is making a change, not a cleanup.
* **Off-by-one `.in` / test ranges** (all start-of-range, all harmless): `neg` cited `.in:238-246`, content
  is `239-246`; the `power`/`sqrt` FIXME cited `.in:229-234`, block is `230-234`; `toReal` cited
  `.in:548-566`, last pair ends at `567`; `toInteger` cited `.in:572-590`, last pair ends at `591`; the
  disabled sqrt test cited `:429-432`, the `TODO Descomentar` line is `:433`.
* **§0 wording**: `registerTypeOperations` is a static *method*, not a "static block".
* **§4.4 wording**: "the parallel blocks at `:659`, `:707`, `:755`" are the `Op_number_greater` /
  `Op_number_lessequal` / `Op_number_greaterequal` **class-declaration** lines in
  `use-core/.../StandardOperationsNumber.java`, not the `matches` bodies.

### R.5 Summary

| Item | Verdict |
|---|---|
| Operation count (18) | **agrees** |
| Names, arities, argument types, result types (all 18) | **agrees** — no misses, no inventions |
| `matches` predicates, call form, registration order | **agrees** |
| `eval` special cases per op | **agrees except `power`** (R.1) |
| `power` `x = 0, s > 0` semantics | **REFUTED** (R.1) |
| `power`/`sqrt` FIXME attribution and numbers (§3 #11, §5, §6) | **REFUTED** (R.2) |
| Oracle jar not vendored in target repo (§2) | **REFUTED** (R.3) |

**UNVERIFIABLE:** the `s = 1`, `x = 0` behaviour of `power` has no fork-authored expectation anywhere
(neither `.in` nor `URealExpOpsTest`); my `UReal(0.0, u)` claim rests on the oracle jar plus
`UReal.java:157`, not on a fork oracle.
