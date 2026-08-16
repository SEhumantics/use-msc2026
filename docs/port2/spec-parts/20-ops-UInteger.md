# 20 — Operation table: `UInteger`

Source of record (read-only reference, never a build input):

```
FORK_OPS = .git/reference-repositories/uncertainty/USE-Uncertainty/src/main/org/tzi/use/uml/ocl/expr/operations
FORK_VAL = .git/reference-repositories/uncertainty/USE-Uncertainty/src/main/org/tzi/use/uml/ocl/value
UDT      = .git/reference-repositories/uncertainty/uDataTypes/Libraries/Java/src/uDataTypes
```

Primary file: `$FORK_OPS/StandardOperationsUInteger.java`
(465 newlines / 466 physical lines — the last line `}` has no trailing newline;
verified: `wc -l` → 465, `awk 'END{print NR}'` → 466, `tail -c 20 | xxd` ends `7d 0a 7d`).

All paths below are relative to `/home/xoruser/msc-4/use-msc2026`.
Every claim carries a file+line, a symbol, or the shell command that produced it.
Anything I could not establish is marked **UNVERIFIABLE**.

---

## 1. Operation count and the grep that reproduces it

**12 distinct `OpGeneric` subclasses**, registered under **13 OCL names**
(one class, `Op_uInteger_value`, is registered twice — under `value` and under the alias `toInteger`).

Reproducer (run, output pasted):

```bash
grep -c '^final class Op_uInteger_[A-Za-z]* extends OpGeneric {$' \
  /home/xoruser/msc-4/use-msc2026/.git/reference-repositories/uncertainty/USE-Uncertainty/src/main/org/tzi/use/uml/ocl/expr/operations/StandardOperationsUInteger.java
# → 12
```

Companion (registration sites, = 13):

```bash
grep -c 'OpGeneric.registerOperation(' \
  /home/xoruser/msc-4/use-msc2026/.git/reference-repositories/uncertainty/USE-Uncertainty/src/main/org/tzi/use/uml/ocl/expr/operations/StandardOperationsUInteger.java
# → 13
```

Class list with declaration lines (`grep -n` of the same pattern):

| line | class |
|---|---|
| 36 | `Op_uInteger_value` |
| 68 | `Op_uInteger_setUncertainty` |
| 111 | `Op_uInteger_uncertainty` |
| 143 | `Op_uInteger_setValue` |
| 178 | `Op_uInteger_toUReal` |
| 210 | `Op_uInteger_toReal` |
| 244 | `Op_uInteger_abs` |
| 275 | `Op_uInteger_div` |
| 315 | `Op_uInteger_mod` |
| 355 | `Op_uInteger_sqrt` |
| 396 | `Op_uInteger_power` |
| 440 | `Op_uInteger_neg` |

Registration block: `StandardOperationsUInteger.registerTypeOperations`, lines **11–26**.
Registration order (this is load-bearing — see §6.4):

```
13  value            -> new Op_uInteger_value()
14  setUncertainty   -> new Op_uInteger_setUncertainty()
15  uncertainty      -> new Op_uInteger_uncertainty()
16  setValue         -> new Op_uInteger_setValue()
17  toInteger        -> new Op_uInteger_value()      <-- alias, second instance of the same class
18  toUReal          -> new Op_uInteger_toUReal()
19  toReal           -> new Op_uInteger_toReal()
20  abs              -> new Op_uInteger_abs()
21  div              -> new Op_uInteger_div()
22  mod              -> new Op_uInteger_mod()
23  sqrt             -> new Op_uInteger_sqrt()
24  power            -> new Op_uInteger_power()
25  neg              -> new Op_uInteger_neg()
```

---

## 2. Registration and evaluation contract (fork)

`$FORK_OPS/OpGeneric.java` — abstract members an operation must supply
(lines 38–52):

```java
public abstract String  name();                                   // 38
public          boolean isBooleanOperation() { return false; }    // 40-42  (non-abstract)
public abstract int     kind();                                   // 44
public abstract boolean isInfixOrPrefix();                        // 46
public abstract Type    matches(Type params[]);                   // 48
public          String  checkWarningUnrelatedTypes(Expression[]); // 50     (non-abstract, null)
public abstract Value   eval(EvalContext ctx, Value args[], Type resultType); // 52
```

Constants (lines 34, 36): `OPERATION = 0`, `SPECIAL = 3`.
Every one of the 12 UInteger classes returns `OPERATION` from `kind()` — verified:
`grep -c 'return OPERATION;' StandardOperationsUInteger.java` → 12.

**Undefined-argument policy.** `kind() == OPERATION` means strict evaluation:
`$FORK/src/main/org/tzi/use/uml/ocl/expr/ExpStdOp.java` lines 299–308 — if *any* evaluated
argument `isUndefined()`, the result is `UndefinedValue.instance` and `eval()` is **never called**.
Therefore **no** UInteger `eval()` body needs an undefined check, and none has one.

**Arithmetic-exception policy.** `ExpStdOp.java` lines 308–315:

```java
try   { res = getOperation().eval(ctx, argValues, type()); }
catch (ArithmeticException ex) { res = UndefinedValue.instance; }   // "catch e.g. division by zero"
```

So `throw new ArithmeticException()` inside an `eval` (and any JVM-raised `/ by zero`) surfaces
as OCL `Undefined`. Identical code exists at 7.5.0
(`use-core/src/main/java/org/tzi/use/uml/ocl/expr/ExpStdOp.java:308-315`) — no adaptation needed.

**Overload resolution.** `ExpStdOp.opmap` is an `ArrayListMultimap` (`ExpStdOp.java:56,60`),
so `opmap.get(name)` preserves registration order, and `create()` returns the **first** candidate
whose `matches(params)` is non-null (`ExpStdOp.java:128-135`). Registration order therefore decides
overloads for the shared names `abs`, `div`, `mod`, `toInteger`, `power`, `sqrt`.

**`isInfixOrPrefix()` affects printing only.** It is consumed exclusively by
`OpGeneric.stringRep(...)` (lines 54–78). Parsing is decided by the grammar, not by this flag —
see §4 note on `div`.

---

## 3. The operation table

Arity counts the **receiver as argument 0**, matching the fork's own convention:
`matches(Type[] params)` receives the receiver in `params[0]` and `eval(..., Value[] args, ...)`
receives it in `args[0]`.

Call-form column: **dot** = `recv.op(...)`; **infix** = `a op b` accepted by the grammar.
`isInfix` column is the literal return of `isInfixOrPrefix()` (printing only).

| # | OCL name | arity | argument types (in order) | result type (`matches`) | lines | `isInfix` | call form actually parsed |
|---|---|---|---|---|---|---|---|
| 1 | `value` | 1 | `UInteger` | **`UInteger`** (declared) — value produced is `Integer` | 36–65 | false | dot |
| 2 | `setUncertainty` | 2 | `UInteger`, `Integer \| Real` | `UInteger` | 68–107 | false | dot |
| 3 | `uncertainty` | 1 | `UInteger` | `Real` | 111–140 | false | dot |
| 4 | `setValue` | 2 | `UInteger`, `Integer` | `UInteger` | 143–176 | false | dot |
| 5 | `toInteger` (alias of #1) | 1 | `UInteger` | **`UInteger`** (declared) — value produced is `Integer` | 36–65 (reg. at 17) | false | dot |
| 6 | `toUReal` | 1 | `UInteger` | `UReal` | 178–207 | false | dot |
| 7 | `toReal` | 1 | `UInteger` | `Real` | 210–239 | false | dot |
| 8 | `abs` | 1 | `UInteger` | `UInteger` | 244–270 | false | dot |
| 9 | `div` | 2 | (`UInteger`\|`Integer`) × (`UInteger`\|`Integer`), at least one `UInteger` | `UInteger` | 275–310 | false | **infix** (grammar) and dot |
| 10 | `mod` | 2 | (`UInteger`\|`Integer`) × (`UInteger`\|`Integer`), at least one `UInteger` | `UInteger` | 315–350 | false | dot |
| 11 | `sqrt` | 1 | `UInteger` | `UInteger` | 355–390 | false | dot |
| 12 | `power` | 2 | `UInteger`, `Integer \| Real` | `UInteger` | 396–435 | false | dot |
| 13 | `neg` | 1 | `UInteger` | `UInteger` | 440–466 | false | dot |

Type-predicate ground truth used above (fork, `src/main/org/tzi/use/uml/ocl/type/`):

* `isKindOfUInteger(EXCLUDE_VOID)` is `true` for **exactly** `IntegerType` (`IntegerType.java:63`) and
  `UIntegerType` (`UIntegerType.java:34`). `TypeImpl` default is `false` (`TypeImpl.java:180`);
  `VoidType` returns `h == INCLUDE_VOID` (`VoidType.java:38`). `RealType`, `URealType`,
  `UnlimitedNaturalType` do **not** override it → `false`.
* `isKindOfReal(EXCLUDE_VOID)` is `true` for **exactly** `IntegerType` (`IntegerType.java:53`) and
  `RealType` (`RealType.java:50`). `TypeImpl` default `false` (`TypeImpl.java:215`);
  `UnlimitedNaturalType` does not override it → `false`; `UIntegerType`/`URealType` do not
  override it → `false`.
  ⇒ `setUncertainty`'s second argument can only ever be an `IntegerValue` or a `RealValue`,
  so the unguarded `(RealValue) args[1]` cast at line 101 is safe.
* `isTypeOfUInteger()` is `true` only for `UIntegerType` (`UIntegerType.java:24`).

---

## 4. Per-operation detail

Convention below: a UInteger value is written `(x, u)` where `x = UIntegerValue.value() : int`
(`$FORK_VAL/UIntegerValue.java:21-23`) and `u = UIntegerValue.uncertainty() : double`
(`ibid.:25-27`). The backing `uDataTypes.UInteger` constructor **normalises `u` to `Math.abs(u)`**
(`$UDT/UInteger.java:20`, and `setU` at line 46) — this normalisation is why *every* uncertainty
in the tables below is non-negative regardless of how it was computed.

Oracle citations `[in:N]` are lines of
`.git/reference-repositories/uncertainty/USE-Uncertainty/src/test/org/tzi/use/parser/uncertainty/UIntegerExpression.in`,
whose harness (`.../uncertainty/USECompilerUncertaintyTest.java:92`) compares against
`result.toStringWithType()` — i.e. the **dynamic value's** type, not the static expression type.
Citations `[jt:N]` are lines of
`.../src/test/org/tzi/use/uml/ocl/expr/UIntegerExpOpsTest.java`.

---

### 1 / 5. `value` — and its alias `toInteger`

* Lines **36–65** (header comment line 35: `// value : UInteger -> Integer`).
* `name()` → `"value"` (39–41). `kind()` → `OPERATION` (44–46). `isInfixOrPrefix()` → `false` (49–51).
* `matches` (54–57): `params.length == 1 && params[0].isTypeOfUInteger()` → **`TypeFactory.mkUInteger()`**.
* `eval` (60–64): `return IntegerValue.valueOf(((UIntegerValue) args[0]).value());`
* **Semantics**: projects out the value component `x` as an OCL `Integer`; the uncertainty
  component `u` is **discarded entirely** (not folded in, not carried).
* Special cases in `eval`: **none**. No undefined check (handled upstream by `OPERATION` kind),
  no zero/NaN/infinity path — `x` is an `int` and cannot be NaN or infinite.
* Oracle: `UInteger(3, 3.5).value() -> 3 : Integer` `[in:43-44]`;
  `UInteger(-5, 0.2).value() -> -5 : Integer` `[in:49-50]`;
  `UInteger(Undefined, 3).value() -> Undefined : OclVoid` `[in:58-59]`. `[jt:147-215]`.
* Alias: line 17 registers a **second, distinct instance** under the name `toInteger`.
  Oracle `UInteger(3, 0.3).toInteger() -> 3 : Integer` `[in:134-135]`, `[jt:515-549]`.

> **DEFECT — declared vs produced type disagree.** `matches` declares the static result type
> `UInteger`, but `eval` returns an `IntegerValue`. The class's own header comment (line 35)
> says `-> Integer`, and the sibling `Op_ureal_value` in `$FORK_OPS/StandardOperationsUReal.java`
> lines 245–249/252–256 does it correctly (`matches` → `TypeFactory.mkReal()`, `eval` → `RealValue`).
> So this is an isolated typo in the UInteger registry, not a design choice.
> The `.in` oracle cannot detect it because it prints the *dynamic* value type (§4 preamble).
> The observable consequence is that `ExpStdOp.type()` for `u.value()` is `UInteger` while the
> runtime value is `IntegerValue`, so downstream static typing of `u.value()` is wrong.
>
> **Port decision required.** Reproducing the bug preserves bug-for-bug fidelity with the oracle
> jar; fixing it (`mkInteger()`) changes the static type of `u.value()` and `u.toInteger()`
> and could alter which overload is selected in enclosing expressions. This is a policy call for
> the port owner, not something the sources decide. **UNVERIFIABLE**: whether any fork test or
> example depends on the buggy static type — I found none, but I did not exhaustively evaluate
> every enclosing-expression case.

> **Alias printing gotcha.** `stringRep` uses `name()` (`OpGeneric.java:66`), and the aliased
> instance still reports `name()` → `"value"`. So an expression written `u.toInteger()`
> pretty-prints as `u.value`. Verified by reading `OpGeneric.stringRep` lines 54–78 together
> with `StandardOperationsUInteger.java:17` and `:39-41`. Not exercised by any test I found.

---

### 2. `setUncertainty`

* Lines **68–107** (header comment 67: `// setUncertainty : UInteger x (Real + Integer) -> UInteger`).
* `matches` (86–90): `params.length == 2 && params[0].isTypeOfUInteger() && params[1].isKindOfReal(Type.VoidHandling.EXCLUDE_VOID)` → `mkUInteger()`.
* `eval` (93–106):
  ```java
  if (args[1].isInteger()) uncertainty = ((IntegerValue) args[1]).value();
  else                     uncertainty = ((RealValue)    args[1]).value();
  result = new UIntegerValue(uInteger.value(), uncertainty);
  ```
* **Semantics**: returns `(x, |newU|)` — the receiver's value component is kept unchanged and its
  **existing uncertainty is replaced wholesale** (not combined, not accumulated) with the
  absolute value of the argument. The `Math.abs` is applied inside
  `uDataTypes.UInteger(int, double)` at `$UDT/UInteger.java:20`, not in the operation.
* Special cases in `eval`:
  * **Undefined argument** — impossible to reach `eval` (OPERATION kind); moreover
    `VoidType.isKindOfReal(EXCLUDE_VOID)` is `false` (`VoidType.java:48-50`), so
    `setUncertainty(null)` fails **at compile time** with `ExpInvalidException`, pinned by
    `[jt:494-511]`.
  * **Negative argument** — normalised to its absolute value: `UInteger(0,3).setUncertainty(-5) -> UInteger(0, 5.0)` `[in:96-97]`, `UInteger(0,3).setUncertainty(-0.3) -> UInteger(0, 0.3)` `[in:111-112]`.
  * **Zero argument** — accepted, yields `u = 0.0`: `UInteger(5,2).setUncertainty(0) -> UInteger(5, 0.0)` `[in:99-100]`.
  * No NaN/infinity guard. Passing a `Real` NaN/±∞ would be stored verbatim
    (`Math.abs(NaN) = NaN`). **UNVERIFIABLE** by test — no oracle case; OCL has no NaN literal,
    but `1.0/0.0` is trapped as Undefined by `Op_number_div` (`StandardOperationsNumber.java:429-430`),
    so I could not construct a reaching case.
  * No zero-divisor, empty/absent, or index concept applies.

---

### 3. `uncertainty`

* Lines **111–140** (header comment 110: `// uncertainty : UInteger -> Real`).
* `matches` (129–132): `params.length == 1 && params[0].isTypeOfUInteger()` → `TypeFactory.mkReal()`.
* `eval` (135–139): `return new RealValue(uInteger.uncertainty());`
* **Semantics**: projects out the uncertainty component `u` as an OCL `Real`; the value component
  is discarded. The returned number is always ≥ 0 because `u` was normalised at construction.
* Special cases in `eval`: **none**. Uncertainty 0 is returned as plain `0.0`
  (`UInteger(0,0).uncertainty() -> 0.0 : Real` `[in:79-80]`) — there is no special "certain" marker.
* Oracle: `[in:74-92]`, `[jt:320-388]`.

---

### 4. `setValue`

* Lines **143–176** (header comment 142: `// setValue : UInteger x Integer -> UInteger`).
* `matches` (161–165): `params.length == 2 && params[0].isTypeOfUInteger() && params[1].isTypeOfInteger()` → `mkUInteger()`. Note `isTypeOfInteger`, **not** `isKindOf…` — a `Real` argument does not match.
* `eval` (168–175): `new UIntegerValue(((IntegerValue) args[1]).value(), uInteger.uncertainty())`.
* **Semantics**: dual of `setUncertainty` — replaces the value component with the argument and
  **carries the receiver's uncertainty across unchanged**.
* Special cases in `eval`: **none**.
  Compile-time rejections pinned by `[jt:255-315]`: `setValue(Undefined)`, `setValue(2.5)`
  (Real), and `setValue('testing')` all raise `ExpInvalidException` from `ExpStdOp.create`
  because `matches` returns `null`.
* Oracle: `UInteger(3,5).setValue(2) -> UInteger(2, 5.0)` `[in:64-65]`;
  `UInteger(0,3).setValue(-55) -> UInteger(-55, 3.0)` `[in:70-71]`.

---

### 6. `toUReal`

* Lines **178–207** (header comment 177).
* `matches` (196–199): `isTypeOfUInteger` → `TypeFactory.mkUReal()`.
* `eval` (202–206): `new URealValue(uInteger.value(), uInteger.uncertainty())` — the only
  `URealValue` constructor taking two numbers is `URealValue(double, double)`
  (`$FORK_VAL/URealValue.java:18`), so the `int` value widens to `double`.
* **Semantics**: lossless widening `(x:int, u) ↦ (x:double, u)`. The uncertainty component is
  **carried across bit-for-bit**; nothing is recomputed.
* Special cases in `eval`: **none**.
* Oracle: `UInteger(3,-0.5).toUReal() -> UReal(3.0, 0.5) : UReal` `[in:124-125]` (note the `-0.5`
  was already normalised to `0.5` at construction, not by this operation);
  `UInteger(-53,5).toUReal() -> UReal(-53.0, 5.0)` `[in:130-131]`. `[jt:551-594]`.

---

### 7. `toReal`

* Lines **210–239** (header comment 209).
* `matches` (228–231): `isTypeOfUInteger` → `TypeFactory.mkReal()`.
* `eval` (234–238): `new RealValue(uInteger.value())`.
* **Semantics**: projects the value component to `Real` and **destroys the uncertainty component**.
  Contrast `toUReal` (#6), which keeps it. This asymmetry is intentional in the fork
  (`toReal` returns the certain OCL `Real` type, which has no uncertainty slot).
* Special cases in `eval`: **none**.
* Oracle: `UInteger(-3,-0.5).toReal() -> -3.0 : Real` `[in:151-152]`. `[jt:596-629]`.

---

### 8. `abs`

* Lines **244–270** (header comment 243: `/* abs : UInteger -> UInteger */`).
* `matches` (261–264): `isTypeOfUInteger` → `mkUInteger()`. Signature is written
  `matches(Type params[])` (C-style array) here, vs `matches(Type[] params)` in ops #1–#7 —
  same erasure, no semantic difference.
* `eval` (267–269): `return ((UIntegerValue) args[0]).abs();`
  → `$FORK_VAL/UIntegerValue.java:161-163` → `$UDT/UInteger.java:322-329`.
* **Semantics**: `(x, u) ↦ (|x|, u)`. The uncertainty component is **passed through untouched** —
  reflecting a distribution reflected about 0, not a re-derived spread.
* Special cases in `eval`: **none** — no undefined, zero, NaN or infinity handling of any kind.
  `Math.abs(Integer.MIN_VALUE) == Integer.MIN_VALUE` (silent 2's-complement overflow) is therefore
  propagated unguarded. **UNVERIFIABLE** by test — no oracle case for `Integer.MIN_VALUE`;
  this is read off `$UDT/UInteger.java:325` plus the JDK contract for `Math.abs(int)`.
* Oracle: `UInteger(-2,3).abs() -> UInteger(2, 3.0)` `[in:178-179]`;
  `UInteger(0,3).abs() -> UInteger(0, 3.0)` `[in:175-176]`. `[jt:1352-1376]`.

---

### 9. `div`

* Lines **275–310** (header comments 272–274 declare the three accepted signatures).
* `matches` (293–303):
  ```java
  if (params.length == 2 && params[0].isKindOfUInteger(EXCLUDE_VOID)
                         && params[1].isKindOfUInteger(EXCLUDE_VOID))
      if (params[1].isTypeOfUInteger() || params[0].isTypeOfUInteger())
          result = TypeFactory.mkUInteger();
  ```
  Because `isKindOfUInteger` is true only for `Integer` and `UInteger` (§3), the accepted set is
  exactly `{UInteger×UInteger, UInteger×Integer, Integer×UInteger}`. `Integer×Integer` is
  deliberately excluded by the inner guard and falls through to upstream `Op_integer_idiv`.
* `eval` (306–308): `return UIntegerValue.valueOf(args[0]).divideBy(args[1]);`
  * `UIntegerValue.valueOf` (`$FORK_VAL/UIntegerValue.java:109-118`) promotes an `IntegerValue`
    to `(v, 0)` and returns `null` for anything else — unreachable given `matches`.
  * `divideBy` (`ibid.:146-149`) calls `assertKindOfUInteger` (`ibid.:120-127`), which throws
    `RuntimeException("A value kind of UInteger expected")` — also unreachable given `matches`.
    Note this is a plain `RuntimeException`, **not** an `ArithmeticException`, so it would
    *not* be converted to Undefined by `ExpStdOp`; it would propagate.
* Core arithmetic: `$UDT/UInteger.java:84-113`, four branches:

  | branch | guard (source line) | value `x'` | uncertainty `u'` |
  |---|---|---|---|
  | A | `r == this` — **reference identity**, line 87 | `1` | `0.0` |
  | B | `r.getU() == 0.0` (r is a scalar), line 92 | `this.x / r.x` (**int** division, truncates toward zero) | `this.u / r.x` (double) |
  | C | `this.getU() == 0.0` (this is a scalar), line 97 | `this.x / r.x` (**int** division) | `r.u / (r.x·r.x)` |
  | D | both uncertain, lines 104–110 | `(int) Math.floor(a)` where `double a = this.x / r.x` — **int division first, then widened**, so still truncating | `sqrt( \|this.u²/r.x\| + (this.x²·r.u²)/r.x⁴ )` |

  Line 105 shows the intended `b` correction term commented out and hard-set to `0.0`;
  line 109's `d` term is the propagated denominator variance.
  The stored `u'` is finally `Math.abs`'d by `setU` (`$UDT/UInteger.java:46`), which is why
  branch B's `5 / -2 = -2.5` surfaces as `2.5`.

* **Semantics (one sentence)**: integer-truncating quotient of the two value components, with the
  uncertainty rebuilt from whichever operand is uncertain — a plain scaling `u/r.x` when the
  divisor is exact, `r.u/r.x²` when the dividend is exact, and the quadrature sum
  `sqrt(|u₁²/x₂| + x₁²u₂²/x₂⁴)` when both carry uncertainty.
* Special cases in `eval` / the arithmetic:
  * **Zero divisor** → `this.getX() / r.getX()` (branches B, C) or `double a = this.x / r.x`
    (branch D) is an **int** division, so the JVM raises `ArithmeticException: / by zero`, which
    `ExpStdOp` converts to **Undefined**. Pinned four ways:
    `UInteger(0,0) div UInteger(0,0) -> Undefined` `[in:1345-1346]`;
    `UInteger(0,0) div UInteger(0,4) -> Undefined` `[in:1348-1349]`;
    `UInteger(0,4) div UInteger(0,0) -> Undefined` `[in:1357-1358]`;
    `UInteger(0,3) div 0 -> Undefined` `[in:1394-1395]`.
  * **Aliased operand (branch A)** requires the *same Java object* on both sides. Two separately
    evaluated equal constants are distinct objects and take branch B instead — confirmed by
    `UInteger(-9,0) div UInteger(-9,0) -> UInteger(1, 0.0)` `[in:1310-1311]`, which is branch B
    (`-9/-9 = 1`, `0/-9 = -0.0 → 0.0`), not branch A. Branch A is therefore reachable only when
    the evaluator hands the identical `UIntegerValue` instance twice.
    **UNVERIFIABLE**: no test in the fork exercises branch A; I could not construct an OCL
    expression that provably yields object identity.
  * **Confidence 0 / 1**: there is no confidence concept for `UInteger` — only a standard
    uncertainty `u`. `u = 0` selects branch B or C (exact-operand shortcuts); `u = 0` on both
    sides gives `u' = 0`. There is no `u = 1` special case.
  * **NaN / infinity**: `eval` has **no guard at all** (contrast `sqrt` #11 and `power` #12,
    which do guard, and `Op_number_div` at `$FORK_OPS/StandardOperationsNumber.java:389-393`,
    which also guards). With `r.x ≠ 0` all four branches are finite, so I found no reaching case,
    but the absence of the guard is a real asymmetry the port must decide about consciously.
  * **Empty/absent, index out of range**: not applicable (no collection, no index).
* Worked oracle checks (each recomputed by hand from the branch table and matched):
  * `UInteger(-5,0) div UInteger(-5,3) -> UInteger(1, 0.12)` `[in:1313-1314]` — branch C: `3/25 = 0.12`.
  * `UInteger(9,5) div UInteger(9,0) -> UInteger(1, 0.5555555556)` `[in:1370-1371]` — branch B: `5/9`.
  * `UInteger(2,3) div UInteger(5,4) -> UInteger(0, 1.379275172)` `[in:1379-1380]` — branch D:
    `2/5 = 0` (int), `sqrt(|9/5| + 4·16/625) = sqrt(1.9024)`. This case is the proof that
    branch D truncates rather than dividing as reals.
  * `UInteger(-8,5) div -2 -> UInteger(4, 2.5)` `[in:1391-1392]` — Integer promoted to `(-2, 0)`, branch B.
  * `Integer div UInteger` is **allowed by `matches`** but has **no oracle case** —
    `grep -n "^-\?[0-9][0-9]*  *div" UIntegerExpression.in` → no output, and
    `UIntegerExpOpsTest` has `testDivideByWithInteger` `[jt:3375]` (UInteger receiver) but no
    Integer-receiver counterpart. **Untested path.**

> **Call-form note.** `isInfixOrPrefix()` returns `false` (288–290), yet the oracle writes `div`
> **infix**: `UInteger(-9, 0) div UInteger(-9, 0)` `[in:1310]`. Both are true and not in conflict:
> the OCL grammar hard-codes `div` as a multiplicative infix operator —
> fork `src/main/org/tzi/use/parser/ocl/OCL.g:302` `(STAR | SLASH | 'div')`, and identically at
> 7.5.0 in `use-core/src/main/resources/grammars/base/OCLBase.gpart:224`. `isInfixOrPrefix()`
> only drives `stringRep`, so the fork **prints** `a.div(b)` for something the user **wrote**
> `a div b`. Upstream `Op_integer_idiv` returns `true` here
> (`$FORK_OPS/StandardOperationsNumber.java:900-902`, and 7.5.0
> `StandardOperationsNumber.java:589-591`), so the fork's UInteger `div` prints inconsistently
> with the Integer `div` it overloads. Cosmetic, but a visible divergence.

---

### 10. `mod`

* Lines **315–350** (header comments 312–314).
* `matches` (333–343): character-for-character the same shape as `div`'s → same accepted set
  `{UInteger×UInteger, UInteger×Integer, Integer×UInteger}` → `mkUInteger()`.
* `eval` (346–348): `return UIntegerValue.valueOf(args[0]).mod(args[1]);`
  → `$FORK_VAL/UIntegerValue.java:151-154` → `$UDT/UInteger.java:149-178`.
* Core arithmetic branches (`$UDT/UInteger.java:149-178`) — identical structure to `div`,
  differing only in the value component:

  | branch | guard | value `x'` | uncertainty `u'` |
  |---|---|---|---|
  | A | `r == this` (line 152) | `0` | `0.0` |
  | B | `r.getU() == 0.0` (line 157) | `this.x % r.x` | `this.u / r.x` |
  | C | `this.getU() == 0.0` (line 162) | `this.x % r.x` | `r.u / (r.x·r.x)` |
  | D | both uncertain (169–175) | `(int) Math.floor(this.x % r.x)` | `sqrt( \|this.u²/r.x\| + (this.x²·r.u²)/r.x⁴ )` |

  Note branch D reuses **exactly** `div`'s uncertainty formula — the uncertainty of `a mod b` is
  computed as if it were the uncertainty of `a div b`. That is what the source does; whether it is
  mathematically intended is not something the source states.
* **Semantics (one sentence)**: Java `%` remainder of the two value components (so the result
  takes the **sign of the dividend**), with the uncertainty component computed by the same
  three-way rule as `div` — `u/r.x` for an exact divisor, `r.u/r.x²` for an exact dividend, and
  the `div` quadrature formula when both are uncertain.
* Special cases in `eval` / the arithmetic:
  * **Zero divisor** → `%` by zero raises `ArithmeticException` → **Undefined**. Pinned:
    `UInteger(0,0).mod(UInteger(0,0)) -> Undefined` `[in:1457-1458]`;
    `UInteger(0,4).mod(UInteger(0,3)) -> Undefined` `[in:1469-1470]`;
    `UInteger(0,3).mod(0) -> Undefined` `[in:1518-1519]`.
  * **Aliased operand (branch A)** → `(0, 0.0)`; same identity caveat and same
    **UNVERIFIABLE** status as `div` branch A. Note `UInteger(-9,0).mod(UInteger(-9,0)) -> UInteger(0, 0.0)`
    `[in:1423-1424]` reaches the same answer via branch B (`-9 % -9 = 0`), so it does not
    discriminate branch A.
  * **Sign of the result** follows Java: `UInteger(-6,0).mod(-12) -> UInteger(-6, 0.0)` `[in:1509-1510]`
    and `UInteger(-6,2).mod(UInteger(5,0)) -> UInteger(-1, 0.4)` `[in:1445-1446]` — OCL `mod` here
    is **not** the mathematical modulus.
  * **Confidence 0/1, NaN/infinity, empty/index**: same as `div` — `u = 0` picks a shortcut
    branch, there is no confidence concept, and `eval` carries **no** NaN/infinity guard.
* Worked oracle checks: `UInteger(4,0).mod(UInteger(8,0)) -> UInteger(4, 0.0)` `[in:1475-1476]`
  (`4 % 8 = 4`); `UInteger(-10,0).mod(UInteger(4,1)) -> UInteger(-2, 0.0625)` `[in:1436-1437]`
  (branch C: `-10 % 4 = -2`, `1/16`); `UInteger(2,3).mod(UInteger(5,4)) -> UInteger(2, 1.379275172)`
  `[in:1500-1501]` (branch D — value `2 % 5 = 2` but uncertainty identical to the `div` case above).
* Call form: the oracle always writes `mod` as a **dot-call** (`a.mod(b)`), and
  `grep -n "'mod'" OCL.g` in the fork returns **no output** — `mod` is *not* an infix grammar
  keyword. `isInfixOrPrefix()` → `false` (328–330) is therefore consistent with parsing here,
  but still diverges from upstream `Op_integer_mod`, which returns `true`
  (`$FORK_OPS/StandardOperationsNumber.java:867-869`; 7.5.0 `StandardOperationsNumber.java:556-558`).
  `Integer mod UInteger` is likewise **allowed by `matches` but untested**.

---

### 11. `sqrt`

* Lines **355–390** (header comment 354).
* `matches` (372–375): `isTypeOfUInteger` → `mkUInteger()`.
* `eval` (378–389):
  ```java
  UIntegerValue result = uInteger.sqrt();
  if (Double.isNaN(result.value()) || Double.isInfinite(result.uncertainty()))     // 382
      throw new ArithmeticException();
  if (Double.isNaN(result.uncertainty()) || Double.isInfinite(result.uncertainty())) // 385
      throw new ArithmeticException();
  return result;
  ```
* Core arithmetic: `$UDT/UInteger.java:347-349` → `this.toUReal().sqrt().toUInteger()`, i.e.
  1. `toUReal` (`ibid.:528-530`): `(x, u) → UReal(x, u)`.
  2. `UReal.sqrt` (`$UDT/UReal.java:163-177`): if `x == 0.0 && u == 0.0` → `(0.0, 0.0)`;
     otherwise `x' = Math.sqrt(x)`, `u' = u / (2·Math.sqrt(x))`.
  3. `UReal.toUInteger` (`$UDT/UReal.java:656-661`): `r.x = (int) Math.floor(x')` and
     `r.u = sqrt(u'² + (x' − r.x)²)` — **the discarded fractional part is folded into the
     uncertainty**, so truncation is accounted for rather than silently lost.
* **Semantics (one sentence)**: floor of the real square root of the value component, with the
  uncertainty set to the quadrature sum of the linearly propagated spread `u/(2√x)` and the
  fractional residue lost to the floor.
* Special cases in `eval`:
  * **Negative receiver** → `Math.sqrt(x) = NaN` → `u'` NaN → `r.u` NaN → guard at **385** fires →
    **Undefined**. Pinned: `UInteger(-3, 2.3).sqrt() -> Undefined : OclVoid` `[in:184-185]`, `[jt:1319-1324]`.
  * **`(0, 0)`** → `UReal.sqrt`'s explicit `x==0 && u==0` shortcut → `(0, 0.0)`, no exception.
    Pinned: `[in:187-188]`, `[jt:1326-1331]`.
  * **`(0, u>0)`** → the shortcut is skipped, `u' = u/(2·0) = +∞`, `r.u = sqrt(∞² + 0) = ∞` →
    guard at **385** fires → Undefined. **Derived, not pinned** — no oracle or unit-test case for
    a zero value with non-zero uncertainty under `sqrt`.
  * **Confidence 0/1**: no confidence concept; `u = 0` on a positive `x` gives `u' = 0` and then
    `r.u` = the pure floor residue (e.g. `UInteger(4,0).sqrt() -> UInteger(2, 0.0)` `[in:190-191]`,
    residue 0 because 4 is a perfect square).
* > **DEFECT — guard at line 382 is dead and mis-typed.** `result.value()` returns `int`
  > (`$FORK_VAL/UIntegerValue.java:21-23`), so `Double.isNaN(result.value())` widens an `int` to
  > `double` and is **always false**. Its second disjunct then re-tests
  > `Double.isInfinite(result.uncertainty())` — the same predicate line 385 already tests — instead
  > of `result.value()`. So line 382 contributes nothing that line 385 does not already cover, and
  > the apparently-intended "value is NaN or infinite" check does not exist. Line 385 is the only
  > guard that ever fires. Behaviour-preserving ports may keep both lines verbatim; a cleaned-up
  > port may drop 382–383 with **no observable change**.
* Oracle: `UInteger(4,2).sqrt() -> UInteger(2, 0.5)` `[in:193-194]` — `x' = 2.0`, `u' = 2/4 = 0.5`,
  floor residue 0, `r.u = sqrt(0.25) = 0.5`. `[jt:1315-1347]`.

---

### 12. `power`

* Lines **396–435** (header comments 394–395: `power : UInteger x Integer -> UInteger`,
  `power : UInteger x Real -> UInteger`).
* `matches` (413–421): `params.length == 2 && params[0].isTypeOfUInteger() && (params[1].isTypeOfInteger() || params[1].isTypeOfReal())` → `mkUInteger()`.
  Strict `isTypeOf…` on the exponent ⇒ a `UInteger` or `UReal` exponent does **not** match, and
  the receiver must be exactly `UInteger` (an `Integer` receiver does not match here).
* `eval` (424–434):
  ```java
  UIntegerValue result = ((UIntegerValue) args[0]).power(args[1]);
  if (Double.isNaN(result.value())       || Double.isInfinite(result.value()))       // 427
      throw new ArithmeticException();
  if (Double.isNaN(result.uncertainty()) || Double.isInfinite(result.uncertainty())) // 430
      throw new ArithmeticException();
  return result;
  ```
* Exponent handling: `$FORK_VAL/UIntegerValue.java:177-189` re-checks
  `value.type().isKindOfReal(EXCLUDE_VOID)` (throwing a plain `RuntimeException`, unreachable
  given `matches`), then **narrows the exponent to `float`** — `(float)((IntegerValue) v).value()`
  or `(float)((RealValue) v).value()`.
* Core arithmetic: `$UDT/UInteger.java:342-344` → `this.toUReal().power(s).toUInteger()`, with
  `UReal.power(float s)` at `$UDT/UReal.java:151-161`:
  `x' = Math.pow(x, s)`; `u' = s · u · Math.pow(x, s−1)`.
  (A second-order term `b` is computed on line 155 but **not** added — line 156 sets
  `result.setX(a)` with `a + b` commented out.)
  Then `toUInteger` (`$UDT/UReal.java:656-661`) applies the same
  `floor` + `sqrt(u'² + residue²)` rule as `sqrt`.
* **Semantics (one sentence)**: floor of `x^s`, with the uncertainty set to the quadrature sum of
  the first-order propagated spread `|s·u·x^(s−1)|` and the fractional residue discarded by the
  floor — the exponent itself is treated as exact and is narrowed to `float` first.
* Special cases in `eval`:
  * **`x = 0` with `s = 0`** → `x' = Math.pow(0,0) = 1.0`, but `u' = 0·u·Math.pow(0,−1) = 0·u·∞ = NaN`
    → `r.u = NaN` → guard **430** fires → **Undefined**. Pinned twice:
    `UInteger(0,0).power(0) -> Undefined` `[in:198-199]`, `[jt:3783-3789]`, and
    `UInteger(0,2).power(0) -> Undefined` `[in:210-211]`.
    Note `UInteger(3,0).power(0) -> UInteger(1, 0.0)` `[in:225-226]` — non-zero base makes
    `Math.pow(3,−1)` finite, so `u' = 0` and the same `s = 0` succeeds.
  * **`x = 0` with negative `s`** → `x' = +∞`, `u' = NaN` or `−∞` → Undefined.
    Pinned: `UInteger(0,0).power(-2) -> Undefined` `[in:204-205]`;
    `UInteger(0,3).power(-3) -> Undefined` `[in:216-217]`.
  * **`x = 0` with positive `s`** → finite: `UInteger(0,0).power(3) -> UInteger(0, 0.0)` `[in:201-202]`;
    `UInteger(0,1).power(3.5) -> UInteger(0, 0.0)` `[in:219-220]`.
  * **Negative uncertainty from `u' = s·u·x^(s−1)` with `s < 0`** is squared away by
    `sqrt(u'² + …)` and so surfaces positive: `UInteger(1,3).power(-2) -> UInteger(1, 6.0)`
    `[in:237-238]` (`u' = −2·3·1 = −6` → `sqrt(36) = 6.0`).
  * **Fractional result folded into uncertainty**: `UInteger(4,0).power(-2) -> UInteger(0, 0.0625)`
    `[in:228-229]` — `x' = 0.0625`, `u' = 0`, `r.x = floor(0.0625) = 0`,
    `r.u = sqrt(0 + 0.0625²) = 0.0625`. This is the clearest demonstration that `toUInteger`
    converts truncation loss into uncertainty.
  * **Confidence 0/1**: no confidence concept; `u = 0` gives `u' = 0`, after which `r.u` is purely
    the floor residue.
  * **Integer overflow of the value component**: `(int) Math.floor(x')` saturates at
    `Integer.MAX_VALUE` for a large finite `x'`, and because `r.u = sqrt(u'² + (x' − r.x)²)` stays
    finite, **neither guard fires** and a silently saturated value is returned. Derived from
    `$UDT/UReal.java:658-659` plus the JLS narrowing rule; **UNVERIFIABLE** by test — no oracle case.
  * **Exponent precision loss** from the `double → float` narrowing at
    `$FORK_VAL/UIntegerValue.java:184,186`. All exponents in the oracle (`0, 3, −2, 3.5, 1.5, 4, 0.25`)
    are exactly representable in `float`, so **no test detects this**. It is a real behavioural
    difference for exponents like `0.1`.
* > **DEFECT — guard at line 427 is dead.** As with `sqrt`, `result.value()` returns `int`, so
  > both `Double.isNaN(int)` and `Double.isInfinite(int)` are always false. Line 427 can never
  > fire. Every Undefined outcome above is produced by line 430 via the uncertainty component.
  > Unlike `sqrt`'s line 382, this one is at least *symmetrically written* — it is simply
  > ineffective. Same port note: removing 427–428 is behaviour-preserving.

---

### 13. `neg`

* Lines **440–466** (header comment 439: `/* neg : UInteger -> UInteger */`).
* `matches` (457–460): `isTypeOfUInteger` → `mkUInteger()`.
* `eval` (463–465): `return ((UIntegerValue) args[0]).neg();`
  → `$FORK_VAL/UIntegerValue.java:169-171` → `$UDT/UInteger.java:332-339`: `x' = -x`, `u' = u`.
* **Semantics**: `(x, u) ↦ (−x, u)` — negating the value component **leaves the uncertainty
  component identical**, since reflection about the origin does not change spread.
* Special cases in `eval`: **none**. `-Integer.MIN_VALUE == Integer.MIN_VALUE` overflows silently
  (derived from `$UDT/UInteger.java:335`; **UNVERIFIABLE** by test — no oracle case).
  `UInteger(0, u).neg()` returns `(0, u)` — `-0` is `0` for `int`, pinned by
  `UInteger(0,2.3).neg() -> UInteger(0, 2.3)` `[in:249-250]`.
* Oracle: `[in:244-253]`, `[jt:1288-1312]`.
* > **Redundancy note.** Prefix `-` already covers this: `Op_number_unaryminus` dispatches
  > `args[0].isUInteger()` to the very same `((UIntegerValue) args[0]).neg()`
  > (`$FORK_OPS/StandardOperationsNumber.java:538-540`). So `neg` is a dot-call synonym for the
  > prefix operator, with no upstream 7.5.0 counterpart. Registering it is optional for
  > *expressiveness* but **required for oracle parity**, since `[in:244-253]` writes `.neg()`.

---

## 5. Operations on `UInteger` that are NOT in this file

Do not treat `StandardOperationsUInteger.java` as the complete `UInteger` surface. These
`UInteger` behaviours live in `$FORK_OPS/StandardOperationsNumber.java` and must be ported with it:

| OCL name | where | UInteger dispatch |
|---|---|---|
| `+` | `Op_number_add` | `StandardOperationsNumber.java:119-125` |
| `-` (binary) | `Op_number_sub` | `:198-204` (note `((UIntegerValue) args[1]).minus(args[0]).neg()` when the UInteger is on the right) |
| `*` | `Op_number_mult` | `:278-284` |
| `/` | `Op_number_div` | `:372-373` → `evalUIntegerResult` `:384-396`, which returns a **`UReal`** via `divideByR`, and **does** guard NaN/∞ on both components |
| `-` (unary) | `Op_number_unaryminus` | `:538-540` |
| `<`, `>`, `<=`, `>=` | `Op_number_less` / `_greater` / `_lessequal` / `_greaterequal` | `:970-974`, `:1048-1052`, `:1125-1129`, `:1203-1207` — all route through `URealValue.valueOf(args[0])` and return a `UBoolean` |
| `toString` | `Op_number_toString` | `:1248-1252` — matches via `isKindOfNumber`, which `UIntegerType` sets true (`UIntegerType.java:19-21`); pinned by `UInteger(5, 0.3).toString() -> 'UInteger(5, 0.3)' : String` `[in:157-158]` |
| `max`, `min` | `Op_number_max` / `_min` | `:745-752`, `:821-828` — a `UInteger` operand is widened to `URealValue`, so the **result type is `UReal`, not `UInteger`** |

The type-lattice hook that makes `+ - *` return `UInteger` (rather than `UReal`) is
`ArithOperation.matches` at `StandardOperationsNumber.java:67-68`:
`params[0].getLeastCommonSupertype(params[1]).isTypeOfUInteger()` → `TypeFactory.mkUInteger()`.
That clause sits **before** the general `UReal` fallback at `:69-71`; the ordering is significant.

---

## 6. Cross-check against the 7.5.0 registry conventions

Target: `use-core/src/main/java/org/tzi/use/uml/ocl/expr/operations/`

### 6.1 `OpGeneric` — no contract member differs

```bash
diff --strip-trailing-cr -u \
  use-core/src/main/java/org/tzi/use/uml/ocl/expr/operations/OpGeneric.java \
  .git/reference-repositories/uncertainty/USE-Uncertainty/src/main/org/tzi/use/uml/ocl/expr/operations/OpGeneric.java
```

The **only** hunk is 7 added lines inside `registerOperations` (fork lines 92–97), registering the
five uncertainty registries. **Every abstract and concrete member — `name()`, `kind()`,
`isBooleanOperation()`, `isInfixOrPrefix()`, `matches(Type[])`, `checkWarningUnrelatedTypes(Expression[])`,
`eval(EvalContext, Value[], Type)`, `stringRep(Expression[], String)`, both
`registerOperation` overloads, and the `OPERATION`/`SPECIAL` constants — is byte-identical.**

(The unfiltered `diff` shows the whole file as changed: 7.5.0's copy is CRLF, the fork's is LF —
`file` reports "ASCII text, with CRLF line terminators" vs "Java source, ASCII text". That is a
line-ending artefact, not a content difference. Any port must not import the fork's LF endings
into a CRLF tree wholesale, or every file will show as fully rewritten.)

**Consequence for the port: no `OpGeneric` signature adaptation is required.** All twelve classes
can be transcribed with their `@Override` sets unchanged. What must be adapted is everything the
`matches`/`eval` bodies *call*.

### 6.2 Missing dependencies in 7.5.0 that every one of these operations needs

| needed by | symbol | fork definition | 7.5.0 status |
|---|---|---|---|
| all 12 `matches` | `Type.isTypeOfUInteger()` | `type/Type.java:84` | **absent** — `use-core/.../type/Type.java` declares no `isTypeOfUInteger` (verified by `grep -n "isTypeOf\|isKindOf" Type.java`) |
| `div`, `mod` | `Type.isKindOfUInteger(VoidHandling)` | `type/Type.java:86` | **absent** |
| `toUReal` | `Type.isTypeOfUReal()` / `TypeFactory.mkUReal()` | `type/Type.java:92`, `TypeFactory.java:93` | **absent** |
| all 12 `matches` | `TypeFactory.mkUInteger()` | `type/TypeFactory.java:83` | **absent** — 7.5.0's `mk*` list has no `mkUInteger`/`mkUReal`/`mkUBoolean` |
| all 12 | `UIntegerType` | `type/UIntegerType.java` | **absent** (7.5.0 `type/` has no `U*Type`) |
| all 12 `eval` | `UIntegerValue` | `value/UIntegerValue.java` | **absent** (7.5.0 `value/` has no `U*Value`) |
| `toUReal` | `URealValue(double,double)` | `value/URealValue.java:18` | **absent** |
| `div`, `mod`, `power`, `sqrt` | `Value.isUInteger()` | `value/Value.java:67` | **absent** — 7.5.0 `Value` has `isInteger/isUnlimitedNatural/isReal/isBoolean/isDefined/isUndefined/isCollection/isBag/isSet/isSequence/isOrderedSet/isObject/isLink` only |
| `UIntegerValue.toString` | `MathUtil.round(double,int)` | `util/MathUtil.java:106` | **absent** — 7.5.0 `util/MathUtil.java` has only `max`/`min` overloads (whole file read) |
| all `eval` | `uDataTypes.UInteger` | `atenearesearchgroup.uncertainty.jar` | not on the 7.5.0 classpath |

Also required: the type-lattice edges. `IntegerType.isKindOfUInteger` → `true`
(`IntegerType.java:63`) and `IntegerType.allSupertypes()` including `mkUInteger()`/`mkUReal()`
(`IntegerType.java:78-86`, with `res.add(TypeFactory.mkUInteger())` at line 84) are what let `UInteger div Integer` and `Integer div UInteger` match at
all; `VoidType.isKindOfUInteger(h) == (h == INCLUDE_VOID)` (`VoidType.java:38-40`) is what makes
`EXCLUDE_VOID` reject `null` at compile time. Both must be replicated or the `matches` bodies
silently change meaning.

Available unchanged in 7.5.0 (no adaptation): `IntegerValue.valueOf(int)`
(`value/IntegerValue.java:102`), `RealValue(double)` (`value/RealValue.java:34`),
`Type.VoidHandling`, `EvalContext`, and the `ExpStdOp` undefined/`ArithmeticException` policy
(`ExpStdOp.java:299-315`).

### 6.3 Conventions the 7.5.0 registries follow, and where this file departs

Read for calibration: `use-core/.../StandardOperationsNumber.java` (7.5.0) and the fork's copy.

| upstream convention | evidence | UInteger file |
|---|---|---|
| package-private registry class | 7.5.0 `StandardOperationsNumber.java:16` `class StandardOperationsNumber {` | **departs** — `public class StandardOperationsUInteger` (line 9) |
| entry point `static void registerTypeOperations(Multimap<String, OpGeneric>)` | 7.5.0 `:17` | follows (line 11) |
| operation classes are `final class Op_<type>_<op> extends OpGeneric`, package-private, file-local | 7.5.0 `:438,470,505,…` | follows, but naming case differs: upstream uses all-lowercase type segments (`Op_integer_abs`, `Op_real_floor`); this file uses `Op_uInteger_*` while the sibling UReal file uses `Op_ureal_*` (`$FORK_OPS/StandardOperationsUReal.java:40,74,112,…`). Inconsistent within the fork itself. |
| `matches(Type params[])` C-style array param | 7.5.0 `:455,487,522,…` | mixed: ops #1–#7 use `matches(Type[] params)`, ops #8–#13 use `matches(Type params[])` (lines 261, 293, 333, 372, 413, 457). Cosmetic. |
| shared behaviour factored into an abstract base (`ArithOperation`) | 7.5.0 `:54-84` | **departs** — `div`/`mod` duplicate a 10-line `matches` verbatim (293–303 vs 333–343), and `sqrt`/`power` duplicate the two-guard block. No base class. |
| header comment giving the OCL signature above each class | 7.5.0 `:437,469,504,583,…` | follows (lines 35, 67, 110, 142, 177, 209, 243, 272–274, 312–314, 354, 394–395, 439) — but the line-35 comment **contradicts** the code (§4.1). |
| `isInfixOrPrefix()` matches the grammar's treatment of the symbol | 7.5.0 `Op_integer_idiv:589-591` → `true`, `Op_integer_mod:556-558` → `true` | **departs** — fork's `div` (288–290) and `mod` (328–330) both return `false` |
| NaN/∞ results converted to Undefined via `throw new ArithmeticException()` | 7.5.0 `Op_number_div` `evalRealResult:429-430`; fork `evalUIntegerResult:389-393` | followed by `sqrt` (382–386) and `power` (427–431) — but **both first guards are dead code** (§4.11, §4.12) — and **not** followed by `div`/`mod`, which have no guard at all |

### 6.4 Registration-order requirement

`OpGeneric.registerOperations` in the fork (lines 80–105) registers, in order:
Any, Object, Enum, **Number, String, Boolean**, then **UReal, UBoolean, UInteger, UString, SBoolean**,
then the collection registries. Because `ArrayListMultimap` preserves insertion order and
`ExpStdOp.create` takes the first non-null `matches` (`ExpStdOp.java:128-135`), the port must
insert the uncertainty registries **after** `StandardOperationsNumber`/`String`/`Boolean` and
**before** the collections, exactly as fork lines 92–97 do. Names where this matters for UInteger:

* `abs` — `Op_real_abs`, `Op_integer_abs` come first; both return `null` for a `UInteger`
  argument (`isTypeOfReal` / `isTypeOfInteger` are false for `UIntegerType`), so `Op_uInteger_abs` wins.
* `div` — `Op_integer_idiv` first; requires **both** `isTypeOfInteger`
  (7.5.0 `StandardOperationsNumber.java:593-597`), so `Integer div Integer` keeps upstream
  behaviour and only mixed/UInteger cases fall through to `Op_uInteger_div`.
* `mod` — same reasoning against `Op_integer_mod`.
* `toInteger` — 7.5.0 registers this name **only** for `String`
  (`StandardOperationsString.java:378`, verified by `grep -rn '"toInteger"'` over the 7.5.0 ops
  directory, single hit). The fork adds three more (`StandardOperationsUReal.java:343`,
  `StandardOperationsUString.java:493`, and the `Op_uInteger_value` alias). All are disjoint on
  receiver type, so order is not semantically load-bearing for this name — but it is for the
  three above.

### 6.5 Grammar parity

`div` is an infix multiplicative operator in **both** trees:
fork `src/main/org/tzi/use/parser/ocl/OCL.g:302` and 7.5.0
`use-core/src/main/resources/grammars/base/OCLBase.gpart:224`, both `(STAR | SLASH | 'div')`.
`mod` is **not** an infix keyword in either (`grep -n "'mod'" OCL.g` → no output in the fork).
**No grammar change is needed** to support `UInteger div UInteger` — only the registry entry.

---

## 7. Verification ledger

Commands actually run for this document (all read-only; no `mvn`, no writes outside
`docs/port2/spec-parts/`):

```bash
# operation count (§1)
grep -c '^final class Op_uInteger_[A-Za-z]* extends OpGeneric {$' $FORK_OPS/StandardOperationsUInteger.java   # 12
grep -n  '^final class Op_uInteger_[A-Za-z]* extends OpGeneric {$' $FORK_OPS/StandardOperationsUInteger.java
grep -c  'OpGeneric.registerOperation('                            $FORK_OPS/StandardOperationsUInteger.java   # 13
grep -c  'return OPERATION;'                                       $FORK_OPS/StandardOperationsUInteger.java   # 12

# file size (§ preamble)
wc -l $FORK_OPS/StandardOperationsUInteger.java              # 465
awk 'END{print NR}' $FORK_OPS/StandardOperationsUInteger.java # 466
tail -c 20 $FORK_OPS/StandardOperationsUInteger.java | xxd    # ... 7d 0a 7d  (no trailing newline)

# OpGeneric contract parity (§6.1)
diff --strip-trailing-cr -u use-core/.../operations/OpGeneric.java $FORK_OPS/OpGeneric.java   # one hunk, lines 88..97

# oracle-jar API parity with the checked-in uDataTypes source (§4)
unzip -l $FORK/lib/atenearesearchgroup.uncertainty.jar | grep -i uInteger
javap -cp $FORK/lib/atenearesearchgroup.uncertainty.jar uDataTypes.UInteger
#  -> public method set matches $UDT/UInteger.java exactly (add/minus/mult/divideBy/divideByR/mod
#     with and without covariance; abs/neg/power(float)/sqrt/inverse; lt/le/gt/ge/equals/distinct
#     and the *Zero variants; compareTo/min/max; toInteger/toReal/toUReal/toUUnlimitedNatural;
#     hashcode/clone/toString)

# 7.5.0 gaps (§6.2)
grep -n "public static .* mk" use-core/.../type/TypeFactory.java
grep -n "isTypeOf\|isKindOf"  use-core/.../type/Type.java
grep -n "boolean is"          use-core/.../value/Value.java
cat                           use-core/.../util/MathUtil.java
ls                            use-core/.../expr/operations/

# grammar parity (§6.5)
grep -n "'div'" $FORK/src/main/org/tzi/use/parser/ocl/OCL.g
grep -n "'mod'" $FORK/src/main/org/tzi/use/parser/ocl/OCL.g          # no output
grep -n "'div'" use-core/src/main/resources/grammars/base/OCLBase.gpart

# untested-path checks (§4.9, §4.10)
grep -n "^-\?[0-9][0-9]*  *div" .../uncertainty/UIntegerExpression.in   # no output
grep -n "public void test" .../UIntegerExpOpsTest.java
```

Files read in full: `StandardOperationsUInteger.java` (466 lines), `OpGeneric.java` (fork and
7.5.0), `StandardOperationsNumber.java` (fork, 1258 lines), `$FORK_VAL/UIntegerValue.java`,
`$UDT/UInteger.java` (550 lines), `use-core/.../util/MathUtil.java`,
`.../uncertainty/USECompilerUncertaintyTest.java`.
Read in part: `$UDT/UReal.java` (lines 137–185, 656–678), `ExpStdOp.java` (both trees, lines
103–160 and 280–345), `UIntegerExpOpsTest.java` (lines 1–1121, 1288–1382, 3779–3914),
`UIntegerExpression.in` (section index plus lines 118–255 and 1308–1532).

### Open items marked UNVERIFIABLE

1. Whether the `Op_uInteger_value` declared-type bug (`mkUInteger` vs `mkInteger`) is depended
   upon anywhere. I found no test that would notice, but did not exhaustively enumerate
   enclosing-expression cases.
2. Reachability of the `r == this` reference-identity branch in `UInteger.divideBy` /
   `UInteger.mod` from OCL source. No fork test exercises it.
3. Behaviour of `sqrt` on `(0, u>0)` — derived as Undefined from `$UDT/UReal.java:163-177` +
   `:656-661`; not pinned by any oracle or unit-test case.
4. `Integer.MIN_VALUE` overflow behaviour of `abs` and `neg`, and `Integer.MAX_VALUE` saturation in
   `power` — derived from source plus JLS/JDK contracts; no test case.
5. Exponent precision loss from the `double → float` narrowing in
   `$FORK_VAL/UIntegerValue.java:184,186` — no oracle case uses an exponent that is inexact in `float`.
6. Whether `setUncertainty` can be reached with a NaN or infinite `Real` argument.
7. `Integer div UInteger` and `Integer mod UInteger`: permitted by `matches` but exercised by
   **no** test in the fork.

### Sources deliberately not consulted

Per the ground rules, the earlier port on `origin/main` was **not** read, and nothing in this
document derives from it.

---

## Independent refutation

Derived independently from `$FORK_OPS/StandardOperationsUInteger.java` (read in full, all 466
physical lines) **before** reading anything above. Nothing here derives from `origin/main`.

### R.0 The count and the table itself: no discrepancy

My independent enumeration of `registerTypeOperations` (lines 11–26) yields **13 registration
calls**, **12 `OpGeneric` subclasses**, **13 distinct OCL names** — `value`, `setUncertainty`,
`uncertainty`, `setValue`, `toInteger`, `toUReal`, `toReal`, `abs`, `div`, `mod`, `sqrt`,
`power`, `neg` — with `Op_uInteger_value` registered twice (line 13 under `name()` = `"value"`,
line 17 under the explicit alias `"toInteger"` as a **second instance**).

**Their §1 count is right.** Proof:

```bash
F=/home/xoruser/msc-4/use-msc2026/.git/reference-repositories/uncertainty/USE-Uncertainty/src/main/org/tzi/use/uml/ocl/expr/operations/StandardOperationsUInteger.java
grep -c '^final class Op_uInteger' "$F"          # 12  (classes)
grep -c 'OpGeneric.registerOperation(' "$F"      # 13  (registrations)
sed -n '11,26p' "$F" | grep -c 'registerOperation' # 13 (all inside registerTypeOperations)
```

Row by row, their §3 table agrees with mine on **every** name, arity, argument type, and declared
result type. **No operation missed, none invented, no wrong arity, no wrong argument or result
type.** The loop/shared-helper trap does not exist here (there is no loop and no shared base
class); the multi-name trap — the `toInteger` alias — they caught, including the `stringRep`
consequence, which I re-verified against `OpGeneric.stringRep` lines 54–78.

Independently re-derived and **confirmed** (not merely accepted): the `value`/`toInteger`
declared-vs-produced type defect (`matches` line 56 → `mkUInteger()`, `eval` line 63 →
`IntegerValue`, header comment line 35 says `-> Integer`); the `power` dead guard at 427
(`UIntegerValue.value()` returns `int`, `$FORK_VAL/UIntegerValue.java:21`, so **both** disjuncts
of 427 are constant-false); `isKindOfUInteger(EXCLUDE_VOID) == {Integer, UInteger}` and
`isKindOfReal(EXCLUDE_VOID) == {Integer, Real}` — I additionally checked that
`UnlimitedNaturalType extends BasicType` (`UnlimitedNaturalType.java:32`) and overrides neither
predicate, which is what actually makes their "the unguarded `(RealValue)` cast at line 101 is
safe" conclusion sound; the four-branch `div`/`mod` tables against `$UDT/UInteger.java:84–113`
and `:149–178` (guards at 87/92/97 and 152/157/162, `b` hard-set to `0.0`, `setU`'s `Math.abs`
at `:46`); `UReal.power` `:151–161` with the unused second-order `b` at 155 and `setX(a)` at 156;
`UReal.sqrt` `:163–177`; `UReal.toUInteger` `:656–661` folding the floor residue into `u`;
grammar parity (`OCL.g:302`, `OCLBase.gpart:224`, no `'mod'`); the `OpGeneric` diff (exactly one
hunk); and the §6.4 overload-order analysis (`Op_real_abs`/`Op_integer_abs` gate on
`isTypeOfReal`/`isTypeOfInteger`; `Op_integer_idiv`/`Op_integer_mod` require **both**
`isTypeOfInteger`; `grep -rn '"toInteger"'` over `use-core/.../operations/` → single hit at
`StandardOperationsString.java:378`). I also recomputed their worked oracle arithmetic
(`2/5`-div, `-5/-5`-div, `9/9`-div, `-10 mod 4`, `-6 mod 5`, `power(4,-2)`, `power(1,-2)`,
`sqrt(4,2)`) — all correct.

**But the document contains four substantive errors and four minor ones.**

### R.1 SUBSTANTIVE — §4.11 attributes the `sqrt` throw to the wrong line, and the DEFECT box is wrong

§4.11 states: *"line 382 contributes nothing that line 385 does not already cover … **Line 385 is
the only guard that ever fires**"*, and the `(0, u>0)` bullet says *"`r.u = sqrt(∞² + 0) = ∞` →
guard at **385** fires"*. Both are false.

Line 382 is `Double.isNaN(result.value()) || Double.isInfinite(result.uncertainty())`. Only the
**first disjunct** is dead (`value()` returns `int`). The **second disjunct tests the uncertainty**,
and it is evaluated *before* line 385. So for an infinite uncertainty — exactly the `(0, u>0)`
case they describe — it is **line 382–383 that throws**, not 385. Line 385 fires only for the
NaN-uncertainty case (negative receiver, `[in:184-185]`), where 382's `isInfinite(NaN)` is false.

§6.3's table row likewise says *"both first guards are dead code (§4.11, §4.12)"*. True for
`power` (427 tests `value()` in both disjuncts); **false for `sqrt`**.

This is not a cosmetic slip. The document's stated intent — *"the apparently-intended 'value is
NaN or infinite' check does not exist"* — invites a porter to repair 382 to
`Double.isNaN(result.value()) || Double.isInfinite(result.value())`. That "repair" **deletes the
only live infinite-uncertainty trap that runs before 385 fires**, and if a porter also drops 385
as "redundant with the repaired 382" (a natural next step given the doc says the two overlap),
`UInteger(0, u>0).sqrt()` silently changes from `Undefined` to `UInteger(0, Infinity)`. The
correct port instruction is: **transcribe 382–386 verbatim; the disjunct that matters is
`isInfinite(result.uncertainty())` on line 382.**

### R.2 SUBSTANTIVE — 18 of the `[in:N]` oracle citations point at the wrong lines

The document's own standard is *"Every claim carries a file+line"*. For the `div`/`mod` sections
and four earlier ones, the line numbers do not check out.

I verified each claimed expression with `grep -nF` against
`$FORK/src/test/org/tzi/use/parser/uncertainty/UIntegerExpression.in`. **Good news first: every
claimed expression+result pair really exists in the oracle with exactly the stated result — the
semantics are not fabricated.** But the cited lines are wrong:

| doc cites | actual lines | content actually at the cited line |
|---|---|---|
| `[in:124-125]` `UInteger(3,-0.5).toUReal()` | **123-124** | 124 is the `->` result line |
| `[in:130-131]` `UInteger(-53,5).toUReal()` | **129-130** | 130 is the `->` result line |
| `[in:157-158]` `UInteger(5,0.3).toString()` | **156-157** | 157 is the `->` result line |
| `[in:225-226]` `UInteger(3,0).power(0)` | **222-223** | 225 is `UInteger(2, 0).power(3)` |
| `[in:1345-1346]` `(0,0) div (0,0)` | **1334-1335** | 1346 is `UInteger(0, 4) div UInteger(0, 0)` |
| `[in:1348-1349]` `(0,0) div (0,4)` | **1337-1338** | different case |
| `[in:1357-1358]` `(0,4) div (0,0)` | **1346-1347** | different case |
| `[in:1391-1392]` `(-8,5) div -2` | **1392-1393** | 1391 is blank |
| `[in:1394-1395]` `(0,3) div 0` | **1401-1402** | 1395 is `UInteger(0, 0) div 0` |
| `[in:1423-1424]` `(-9,0).mod((-9,0))` | **1422-1423** | off by one |
| `[in:1436-1437]` `(-10,0).mod((4,1))` | **1431-1432** | 1437 is `UInteger(-2, 3).mod(UInteger(-2, 4))` |
| `[in:1445-1446]` `(-6,2).mod((5,0))` | **1440-1441** | 1446 is `UInteger(0, 0).mod(UInteger(0, 0))` |
| `[in:1457-1458]` `(0,0).mod((0,0))` | **1446-1447** | different case |
| `[in:1469-1470]` `(0,4).mod((0,3))` | **1461-1462** | different case |
| `[in:1475-1476]` `(4,0).mod((8,0))` | **1476-1477** | off by one |
| `[in:1500-1501]` `(2,3).mod((5,4))` | **1491-1492** | 1501 is `UInteger(-5, 3).mod(-5)` |
| `[in:1509-1510]` `(-6,0).mod(-12)` | **1498-1499** | 1510 is `UInteger(0, 0).mod(3)` |
| `[in:1518-1519]` `(0,3).mod(0)` | **1513-1514** | 1519 is `UInteger(5, 0).mod(5)` |

Reproduce any row with:

```bash
IN=/home/xoruser/msc-4/use-msc2026/.git/reference-repositories/uncertainty/USE-Uncertainty/src/test/org/tzi/use/parser/uncertainty/UIntegerExpression.in
n=$(grep -nF 'UInteger(2, 3).mod(UInteger(5, 4))' "$IN" | cut -d: -f1); sed -n "${n},$((n+1))p" "$IN"
# 1491: UInteger(2, 3).mod(UInteger(5, 4))
# 1492: -> UInteger(2, 1.379275172) : UInteger      (doc cites 1500-1501)
```

Citations that **are** correct: `[in:43-44]`, `[in:49-50]`, `[in:58-59]`, `[in:64-65]`,
`[in:70-71]`, `[in:79-80]`, `[in:96-97]`, `[in:99-100]`, `[in:111-112]`, `[in:134-135]`,
`[in:151-152]`, `[in:175-176]`, `[in:178-179]`, `[in:184-185]`, `[in:187-188]`, `[in:193-194]`,
`[in:198-199]`, `[in:201-202]`, `[in:204-205]`, `[in:210-211]`, `[in:216-217]`, `[in:219-220]`,
`[in:228-229]`, `[in:237-238]`, `[in:249-250]`, `[in:1310-1311]`, `[in:1313-1314]`,
`[in:1370-1371]`, `[in:1379-1380]`. All `[jt:…]` citations I spot-checked land inside the right
test method (`testOpValue`:147, `testOpSetValueWrongValue`:255, `testOpUncertainty`:320,
`testOpSetUncertaintyWithWrongArgs`:473 with the Undefined case at 494–510, `testToInteger`:515,
`testToUReal`:551, `testToReal`:596, `testNeg`:1288, `testSQRT`:1315, `testABS`:1352,
`testDivideByWithInteger`:3375, `testPower`:3779).

**Port impact:** low on semantics, high on trust — a reviewer regenerating the oracle expectations
from these line numbers gets the wrong cases for `mod` entirely.

### R.3 SUBSTANTIVE — §3 claims `div` is callable in dot form; that is unsupported and probably false

§3, row 9, "call form actually parsed" column: *"**infix** (grammar) and dot"*.

The "and dot" half has no evidence behind it and the evidence points the other way:
`'div'` is an implicit string-literal token in the parser rule `OCL.g:302`
(`(STAR | SLASH | 'div')`), while the dot-call rule binds `name=IDENT` (`OCL.g:449`,
`operationExpression`). In an ANTLR combined grammar the literal token wins over `IDENT` for the
exact text `div`, so `u.div(v)` should fail to parse. Corroborating:

```bash
grep -rn '\.div(' /home/xoruser/msc-4/use-msc2026/.git/reference-repositories/uncertainty/USE-Uncertainty/src/test   # no output
grep -rn '\.div(' /home/xoruser/msc-4/use-msc2026/use-core/src/test                                                  # no output
```

The oracle writes `div` **only** infix; `mod` **only** dot — consistent with `'mod'` not being a
grammar literal. I did not run the parser, so I mark the negative **UNVERIFIABLE**, but the
document asserts the positive as established fact with no citation, and it should not.

The stronger finding they missed: since `isInfixOrPrefix()` returns `false`, `stringRep` prints
`a.div(b)` (their own §4.1/§4.9 observation) — and if the dot form does not parse, the fork's
**pretty-printed output for `UInteger div` is not re-parseable**. That is a concrete
round-tripping bug for the port to decide on, not the "cosmetic" divergence §4.9 calls it.

### R.4 SUBSTANTIVE (factual, cosmetic subject) — the `matches` signature-style split is stated wrong

§6.3 row 4: *"ops #1–#7 use `matches(Type[] params)`, ops #8–#13 use `matches(Type params[])`
(lines 261, 293, 333, 372, 413, 457)"*, repeated in §4.8. Lines **293 (`div`) and 333 (`mod`) use
`matches(Type[] params)`**, not the C-style form.

```bash
grep -n 'public Type matches' "$F"
#  54,  86, 129, 161, 196, 228 : Type[] params
# 261                          : Type params[]      <- abs
# 293, 333                     : Type[] params      <- div, mod  (doc says C-style: WRONG)
# 372, 413, 457                : Type params[]      <- sqrt, power, neg
```

Actual split: C-style in `abs`, `sqrt`, `power`, `neg` only; the other eight use `Type[] params`.

### R.5 MINOR — `ExpStdOp` line citations are the 7.5.0 numbering applied to the fork

§2 cites *"`$FORK/src/main/org/tzi/use/uml/ocl/expr/ExpStdOp.java` lines 299–308"* for the
undefined-argument switch and *"`ExpStdOp.java` lines 308–315"* for the `ArithmeticException`
catch. In the **fork**: `if (v.isUndefined())` is at **298**, `res = getOperation().eval(...)` at
**317**, `catch (ArithmeticException ex)` at **318**. In **7.5.0**: 291, 310, 311 respectively.
So `308–315` matches neither tree exactly and is attributed to the wrong one.

```bash
grep -n 'catch (ArithmeticException\|res = getOperation().eval\|if (v.isUndefined())' \
  .../USE-Uncertainty/src/main/org/tzi/use/uml/ocl/expr/ExpStdOp.java   # 298, 317, 318
grep -n 'catch (ArithmeticException\|res = getOperation().eval\|if (v.isUndefined())' \
  use-core/src/main/java/org/tzi/use/uml/ocl/expr/ExpStdOp.java          # 291, 310, 311
```

The *policy* they describe (strict evaluation for `OPERATION`; `ArithmeticException` → `Undefined`;
identical in both trees) is correct — I confirmed both bodies by reading them.

### R.6 MINOR — §4.12's "all exponents in the oracle" enumeration is incomplete

*"All exponents in the oracle (`0, 3, −2, 3.5, 1.5, 4, 0.25`)"* omits **`−3`**
(`UIntegerExpression.in:216`, `UInteger(0, 3).power(-3)` — which §4.12 itself cites two bullets
earlier as `[in:216-217]`). `grep -n '\.power(' "$IN"` returns 15 cases with exponent multiset
`{0,3,-2,3.5,0,3,-3,3.5,0,3,-2,1.5,4,-2,0.25}`. The conclusion (no oracle exponent is inexact in
`float`, so no test detects the `double→float` narrowing) survives, since `−3` is exact in `float`.

### R.7 MINOR — the `OpGeneric` diff hunk size is stated two different ways

§6.1 says *"7 added lines … (fork lines 92–97)"* — 92–97 is six lines; the hunk actually adds
**seven** lines, fork **91–97** (the blank line at 91 is part of the addition). §7's ledger writes
the same hunk as *"lines 88..97"*. The claim that it is the **only** hunk is correct — I re-ran
`diff --strip-trailing-cr -u` and got exactly one hunk, `@@ -88,6 +88,13 @@`.

### R.8 MINOR — transcription

§4.11 quotes the oracle as `UInteger(4,0).sqrt()`; the file reads `UInteger(4, 0.0).sqrt()`
(`UIntegerExpression.in:190`). No semantic effect.

### Verdict

The **operation table is correct** — right count, right names, right arities, right argument and
result types, and the two genuinely skippable items (the `toInteger` alias sharing a class, and
the `div`/`mod` `matches` bodies being duplicated rather than shared) were both caught. The
document does **not** agree in full, because of **R.1** (wrong guard line for `sqrt`, with a
port instruction that would change behaviour if followed), **R.2** (18 mis-cited oracle lines,
concentrated in `mod`), **R.3** (an asserted dot-call form for `div` with no evidence and
contrary evidence), and **R.4** (a line-cited signature claim that is false for `div` and `mod`).

Nothing above was taken from `origin/main`; the earlier port was not consulted.
