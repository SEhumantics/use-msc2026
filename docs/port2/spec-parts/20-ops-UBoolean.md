# 20 — Operation table: `UBoolean`

Complete extraction of the OCL operation registry for the `UBoolean` type in the historical
uncertainty fork, with a port-facing cross-check against the USE 7.5.0 registry contract.

**Primary source (read in full, all 685 text lines):**
`/home/xoruser/msc-4/use-msc2026/.git/reference-repositories/uncertainty/USE-Uncertainty/src/main/org/tzi/use/uml/ocl/expr/operations/StandardOperationsUBoolean.java`

> Line-count note: `wc -l` reports **684** (matching the task brief) because the file has **no
> trailing newline**; the final `}` is text line **685**. All line numbers in this document are
> text line numbers (i.e. what `Read`/`sed -n` show).
> Verified: `wc -l StandardOperationsUBoolean.java` → `684`; `tail -c 1 … | xxd` → `7d` (`}`).

**Supporting sources read:**

| Role | Path |
|---|---|
| Registration contract (fork) | `…/USE-Uncertainty/src/main/org/tzi/use/uml/ocl/expr/operations/OpGeneric.java` |
| `SPECIAL`-kind base class (fork) | `…/USE-Uncertainty/src/main/org/tzi/use/uml/ocl/expr/operations/BooleanOperation.java` |
| Upstream-style conventions (fork) | `…/USE-Uncertainty/src/main/org/tzi/use/uml/ocl/expr/operations/StandardOperationsNumber.java` |
| Dispatch / undefined-arg contract (fork) | `…/USE-Uncertainty/src/main/org/tzi/use/uml/ocl/expr/ExpStdOp.java` |
| Value wrapper (fork) | `…/USE-Uncertainty/src/main/org/tzi/use/uml/ocl/value/UBooleanValue.java` |
| Underlying algebra (library source) | `…/uncertainty/uDataTypes/Libraries/Java/src/uDataTypes/UBoolean.java` |
| **Live oracle jar** | `…/USE-Uncertainty/lib/atenearesearchgroup.uncertainty.jar` → `uDataTypes/UBoolean.class` |
| 7.5.0 target registry | `/home/xoruser/msc-4/use-msc2026/use-core/src/main/java/org/tzi/use/uml/ocl/expr/operations/` |

---

## 0. Operation count and its reproduction

**Operation count: 14 distinct `OpGeneric` subclasses, registering 14 distinct OCL names.**

Reproduction command (run from the repo root; **executed**, output shown):

```bash
grep -c 'OpGeneric\.registerOperation(new Op_uBoolean_' \
  /home/xoruser/msc-4/use-msc2026/.git/reference-repositories/uncertainty/USE-Uncertainty/src/main/org/tzi/use/uml/ocl/expr/operations/StandardOperationsUBoolean.java
# → 14
```

Two independent cross-checks, also executed, both agreeing:

```bash
grep -c 'final class Op_uBoolean_.* extends ' StandardOperationsUBoolean.java          # → 14
grep -oE 'return "[a-zA-Z]+";' StandardOperationsUBoolean.java | sort -u | wc -l       # → 14
```

The third command additionally proves the 14 classes carry 14 **distinct** OCL names (no two
classes register under the same key). The enumerated names are:

```
and  confidence  equalsC  equivalent  implies  not  or
setConfidence  setValue  toBoolean  toBooleanC  toString  value  xor
```

Note the environment's `grep` is `ugrep 7.5.0` (`which grep` → `/usr/bin/grep`; `grep --version`
→ `ugrep 7.5.0`). The commands above use only POSIX BRE/ERE constructs and are portable to GNU
grep. A caution learned the hard way: ugrep rejects the alternation `^(final class|public class|})`
with `error at position 32 … empty (sub)expression`, so avoid a bare `}` branch inside a group.

---

## 1. Arity convention used throughout this document

**Arity counts the receiver as argument 0.** This is not a documentation convenience — it is
literally how the fork's registry works. `OpGeneric.matches(Type[] params)` receives the receiver
as `params[0]`, and `OpGeneric.eval(EvalContext, Value[] args, Type)` receives it as `args[0]`.
`OpGeneric.stringRep` (OpGeneric.java:65–76) renders `args[0]` as the receiver and `args[1..]` as
the parenthesised argument list for non-infix operations.

So `ub.equalsC(other, 0.9)` has **arity 3**: `args[0]=ub`, `args[1]=other`, `args[2]=0.9`.

---

## 2. Registration contract (`OpGeneric`) and how `kind()` governs undefined arguments

`StandardOperationsUBoolean.registerTypeOperations(Multimap<String, OpGeneric> opmap)`
(lines 12–28) calls `OpGeneric.registerOperation(new Op_uBoolean_XXX(), opmap)` fourteen times.
`OpGeneric.registerOperation` (OpGeneric.java:112–114) is simply `opmap.put(op.name(), op)` — the
map key is the operation's **own** `name()`, not the class name. This matters: see §3.7.

The abstract members every operation must supply (OpGeneric.java:38–52):

```java
public abstract String name();
public abstract int kind();                 // OPERATION = 0, SPECIAL = 3
public abstract boolean isInfixOrPrefix();
public abstract Type matches(Type params[]);
public abstract Value eval(EvalContext ctx, Value args[], Type resultType);
public boolean isBooleanOperation()                     { return false; }   // overridable
public String checkWarningUnrelatedTypes(Expression[] a){ return null;  }   // overridable
```

`BooleanOperation` (BooleanOperation.java) fixes `kind() == SPECIAL`, `isBooleanOperation() ==
true`, makes `eval` throw `RuntimeException("Use evalWithArgs")`, and adds
`public abstract Value evalWithArgs(EvalContext ctx, Expression args[])`. Six of the fourteen
UBoolean operations extend it (`and`, `or`, `not`, `implies`, `xor`, `equivalent`); the other
eight extend `OpGeneric` directly with `kind() == OPERATION`.

**The undefined-argument contract is enforced by the caller, not the operation.**
`ExpStdOp.eval` (fork ExpStdOp.java:278–325) does:

- If `op.isBooleanOperation()` → call `evalWithArgs(ctx, fArgs)` with the **unevaluated
  `Expression[]`**. Nothing is pre-evaluated; the operation is fully responsible for undefined
  operands and may short-circuit.
- Otherwise evaluate arguments left to right; on the first `v.isUndefined()`:
  - `kind() == OPERATION` → result is `UndefinedValue.instance`, **`eval` is never called**;
  - `kind() == SPECIAL` → keep going, the operation handles it;
  - anything else → `RuntimeException("Unexpected operation kind: …")`.
- The `eval` call is wrapped in `try { … } catch (ArithmeticException ex) { res =
  UndefinedValue.instance; }`. Note this catches **only** `ArithmeticException` — a
  `NullPointerException` or `ClassCastException` escapes to the caller. This is load-bearing for
  the defects in §5.

**Overload resolution is first-match-wins in registration order.** `ExpStdOp.opmap` is an
`ArrayListMultimap` (fork ExpStdOp.java:56–62), and `ExpStdOp.create` (fork ExpStdOp.java:117–137)
iterates `opmap.get(name)` and returns the **first** `op` whose `matches(params)` is non-null:

```java
for (OpGeneric op : ops) {
    Type t = op.matches(params);
    if (t != null) {
        checkTypeSystemWarnings(op, args, params, t);
        return new ExpStdOp(op, args, t);
    }
}
```

`ExpStdOp.exists` (fork ExpStdOp.java:85–101) uses the same first-match rule.

---

## 3. The 14 operations

Summary table first; full detail follows. `IoR` = "Integer or Real", i.e. the set admitted by
`isKindOfReal(EXCLUDE_VOID)` — **verified to exclude UnlimitedNatural** (fork
`UnlimitedNaturalType.java` does not override `isKindOfReal`, so it inherits
`TypeImpl.java:215–217` → `false`; `IntegerType.java:53–55` and `RealType.java:50–52` → `true`).

| # | OCL name | Class | Lines | Arity | Arg types (arg0 = receiver) | Result | Notation | Base / `kind()` |
|---|---|---|---|---|---|---|---|---|
| 1 | `toBoolean` | `Op_uBoolean_toBoolean` | 37–78 | 1 | `UBoolean` (typeOf) | `Boolean` | dot-call | `OpGeneric` / `OPERATION` |
| 2 | `toString` | `Op_uBoolean_toString` | 81–117 | 1 | `UBoolean` (typeOf) | `String` | dot-call | `OpGeneric` / `OPERATION` |
| 3 | `toBooleanC` | `Op_uBoolean_toBooleanC` | 121–162 | 2 | `UBoolean` (typeOf), `IoR` | `Boolean` | dot-call | `OpGeneric` / `OPERATION` |
| 4 | `value` | `Op_uBoolean_value` | 165–192 | 1 | `UBoolean` (typeOf) | `Boolean` | dot-call | `OpGeneric` / `OPERATION` |
| 5 | `setValue` | `Op_uBoolean_setValue` | 197–228 | 2 | `UBoolean` (typeOf), `Boolean` (typeOf) | `UBoolean` | dot-call | `OpGeneric` / `OPERATION` |
| 6 | `confidence` | `Op_uBoolean_confidence` | 232–260 | 1 | `UBoolean` (typeOf) | `Real` | dot-call | `OpGeneric` / `OPERATION` |
| 7 | `setConfidence` | `Op_uBoolean_setUncertainty` | 262–304 | 2 | `UBoolean` (typeOf), `IoR` | `UBoolean` | dot-call | `OpGeneric` / `OPERATION` |
| 8 | `equalsC` | `Op_uBoolean_equalsC` | 307–352 | 3 | `UBoolean` (typeOf), `UBoolean` (kindOf, no void), `IoR` | `Boolean` | dot-call | `OpGeneric` / `OPERATION` |
| 9 | `and` | `Op_uBoolean_and` | 356–424 | 2 | `UBoolean` (kindOf, **incl. void**) ×2 | `UBoolean` | **infix** | `BooleanOperation` / `SPECIAL` |
| 10 | `or` | `Op_uBoolean_or` | 427–482 | 2 | `UBoolean` (kindOf, incl. void) ×2 | `UBoolean` | **infix** | `BooleanOperation` / `SPECIAL` |
| 11 | `not` | `Op_uBoolean_not` | 485–516 | 1 | `UBoolean` (kindOf, incl. void) | `UBoolean` | **prefix** | `BooleanOperation` / `SPECIAL` |
| 12 | `implies` | `Op_uBoolean_implies` | 519–584 | 2 | `UBoolean` (kindOf, incl. void) ×2 | `UBoolean` | **infix** | `BooleanOperation` / `SPECIAL` |
| 13 | `xor` | `Op_uBoolean_xor` | 587–623 | 2 | `UBoolean` (kindOf, incl. void) ×2 | `UBoolean` | **dot-call** (see §3.13) | `BooleanOperation` / `SPECIAL` |
| 14 | `equivalent` | `Op_uBoolean_equivalent` | 626–685 | 2 | `UBoolean` (kindOf, incl. void) ×2 | `Boolean` **if both args typeOf Boolean**, else `UBoolean` | dot-call | `BooleanOperation` / `SPECIAL` |

Class start lines and top-level closing braces were obtained mechanically:
`grep -n 'final class Op_uBoolean_' …` and `awk '/^\}/{print NR": "$0}' …`.

### 3.0 Prerequisite: the canonical-form invariant

Every semantics statement below depends on one invariant enforced by the library, so it is stated
once here. `uDataTypes.UBoolean` keeps values in **canonical form** `(b = true, c = P(true))`
(UBoolean.java:8–13):

```java
private void setNormalForm() {
    if (!b) { b = true; c = 1 - c; }
}
```

`setNormalForm()` runs in every constructor and in **both** getters `getB()`/`getC()`
(UBoolean.java:59–67). Verified on the live jar: `new UBoolean(false, 0.3)` → `(b=true, c=0.7)`.

Consequences that recur throughout:

- `b` is **always `true`** for any value reachable through the public API. This is why op #4
  (`value`) can return a constant and be vacuously right.
- `c` is always **confidence-that-the-value-is-true**, never "confidence in the label you typed".
  `UBoolean(false, 0.7)` is stored as `c = 0.3`. This is the single most dangerous trap in the
  whole type — see #6 (`confidence`) and #2 (`toString`).
- `UBooleanValue.FALSE` is `new UBooleanValue(true, 0)` (UBooleanValue.java:31) — its `value()` is
  `true`, its `probability()` is `0`. The field comment on line 30 ("Its a UBoolean that always
  its false (true, 0)") is confusing but the code is consistent with the canonical form.
- `UBooleanValue.valueOf(UBoolean)` (UBooleanValue.java:74–86) **interns**: `c == 0` → the `FALSE`
  singleton, `c == 1` → the `TRUE` singleton, otherwise a fresh instance. So every operation
  result whose confidence lands exactly on 0 or 1 is a shared singleton, not a fresh object.
- `UBooleanValue.valueOf(Value)` (UBooleanValue.java:122–138) returns a `UBooleanValue` for a
  `UBooleanValue` or a `BooleanValue` (`true`→`TRUE`, `false`→`FALSE`) and **`null` for anything
  else, including `UndefinedValue`.** Several operations rely on that `null`; one forgets to
  (§5.1).

### 3.1 `toBoolean` — lines 37–78

- **Notation:** dot-call, `ub.toBoolean()` (`isInfixOrPrefix()` → `false`, line 50–52).
- **`matches`** (55–58): `params.length == 1 && params[0].isTypeOfUBoolean()` → `TypeFactory.mkBoolean()`.
  Note `isTypeOfUBoolean()`, the **exact**-type test, so a plain `Boolean` receiver does not match
  here (it is taken by the core `Boolean` registry instead).
- **Semantics:** collapses the uncertain value to a crisp `Boolean` by thresholding the confidence
  at 0.5 — `UBoolean.toBoolean()` (UBoolean.java:185–188) is `return (c >= 0.5);`, valid only
  because of the canonical form. The uncertainty component is **discarded**, not propagated.
- **`eval` (61–77), every branch:**
  - `args[0].isUBoolean()` → `((UBooleanValue) args[0]).toBoolean()` → `BooleanValue.get(c >= 0.5)`.
  - `args[0].isBoolean()` → **`UBooleanValue.valueOf(((BooleanValue) args[0]).value(), 1)`** — this
    returns a **`UBooleanValue`, not a `BooleanValue`**, contradicting the `Boolean` result type
    declared by `matches`. See §5.3. The source comment at lines 64–65 explains why the branch
    exists at all: inside `Set(UBoolean)` a `BooleanValue` can flow into a slot statically typed
    `UBoolean`. Sub-case: `valueOf(true, 1)` → `TRUE = (true, 1)`; `valueOf(false, 1)` normalises
    (UBooleanValue.java:100–103) to `value = true, probability = 1 - 1 = 0` → `FALSE = (true, 0)`.
  - else → `UndefinedValue.instance`. **Unreachable in practice**: `kind() == OPERATION`, so
    `ExpStdOp` already substituted `UndefinedValue` before `eval` was entered.
- **Boundary, verified on the live jar:** `c == 0.5` → `true`; `c == 0.4999` → `false`.
- No divisor, index, or empty-collection cases exist.

### 3.2 `toString` — lines 81–117

- **Notation:** dot-call.
- **`matches`** (99–102): arity 1, `isTypeOfUBoolean()` → `TypeFactory.mkString()`.
- **Semantics:** renders the value in **display form**, flipping back out of canonical form when
  the confidence is below 0.5, so the printed pair is always `(most-likely-label, confidence ≥ 0.5)`.
  `eval` (105–116):

  ```java
  double probability = UBooleanValue.valueOf(args[0]).probability();
  if (probability < 0.5) sb.append("false, ").append(1 - probability).append(")");
  else                   sb.append("true, ").append(probability).append(")");
  ```

  So the uncertainty component is preserved but *re-expressed*: `c = 0.3` prints as
  `UBoolean(false, 0.7)`.
- **Special cases:**
  - `probability == 0.5` exactly → takes the `else` branch → `UBoolean(true, 0.5)`.
  - Undefined receiver → `UndefinedValue` before `eval` (OPERATION kind).
  - A dynamic `BooleanValue` receiver is tolerated by `UBooleanValue.valueOf(Value)`. If the value
    were neither `UBoolean` nor `Boolean`, `valueOf` returns `null` → **NPE** on `.probability()`,
    and `ExpStdOp` does not catch NPE. Not reachable through `matches`, but the guard is absent.
  - **No rounding.** This diverges from the two other renderings of the same type:
    - `UBooleanValue.toString(StringBuilder)` (UBooleanValue.java:192–199) rounds to 3 decimals via
      `MathUtil.round(probability(), 3)`;
    - `uDataTypes.UBoolean.toString()` (UBoolean.java:175–183) uses `String.format("UBoolean(%b, %5.3f)")`.

    Consequence, from live-jar values: `0.8 or 0.6` yields `c = 0.9199999999999999`, which the
    **value display** shows as `UBoolean(true, 0.92)` but which this **`toString` operation**
    renders as `UBoolean(true, 0.9199999999999999)`. Any ported test asserting on
    `.toString()` output must expect the unrounded double. (I checked the complementary branch
    too: for `not(0.8)`, `c = 0.19999999999999996` and `1 - c` evaluates to exactly `0.8` in
    double arithmetic, so that particular case prints cleanly — the noise is not symmetric.)

### 3.3 `toBooleanC` — lines 121–162

- **Notation:** dot-call, `ub.toBooleanC(threshold)`.
- **`matches`** (139–142): `params.length == 2 && params[0].isTypeOfUBoolean() &&
  params[1].isKindOfReal(Type.VoidHandling.EXCLUDE_VOID)` → `TypeFactory.mkBoolean()`.
- **Semantics:** a *confidence-thresholded* collapse to `Boolean` — returns `true` iff the
  receiver's confidence-in-`true` is at least the caller-supplied threshold. Where `toBoolean`
  hard-codes the 0.5 cut, this parameterises it; the uncertainty component is consumed by the
  comparison and does not survive into the result.
- **`eval` (145–161), every branch:**
  - Result is **pre-set to `BooleanValue.FALSE`** (line 146), so every path that does not
    explicitly assign returns `false`.
  - Threshold read as `((RealValue) args[1]).value()` if `args[1].isReal()`, else
    `((IntegerValue) args[1]).value()`. The `IntegerValue` cast is **safe**: `isKindOfReal` admits
    only Integer and Real (verified in §3 preamble).
  - `confience < 0 || confience > 1` → `UndefinedValue.instance`. (Field name misspelled
    `confience` in the source, lines 147/151/153/155/157.)
  - `left.probability() >= confience` → `BooleanValue.TRUE`.
  - otherwise → the pre-set `FALSE`.
- **Special cases:**
  - Threshold `0` → always `true` (`p >= 0` always holds).
  - Threshold `1` → `true` only when `p == 1` exactly.
  - **Threshold `NaN` → `FALSE`, *not* Undefined.** Both range comparisons are false for NaN, and
    `p >= NaN` is false, so control falls to the pre-set value. This is an asymmetry with #7 and
    #8, which *do* yield Undefined for NaN.
  - Threshold `+Infinity` → `> 1` → Undefined. `-Infinity` → `< 0` → Undefined.
  - Either argument undefined → Undefined before `eval` (OPERATION kind).
  - Local `left` is assigned before the range check, so a dynamic non-Boolean receiver would NPE —
    again not reachable via `matches`.

### 3.4 `value` — lines 165–192

- **Notation:** dot-call.
- **`matches`** (183–186): arity 1, `isTypeOfUBoolean()` → **`TypeFactory.mkBoolean()`**.
- **The header comment on line 164 says `value : UBoolean -> Real`. The code says `Boolean`.**
  The code is authoritative; the comment is stale.
- **`eval` (189–191), in its entirety:**

  ```java
  return BooleanValue.TRUE;
  ```

- **Semantics:** returns the canonical `b` component, which by the invariant of §3.0 is
  **always `true`** — so the implementation is a constant. It is *not* wrong so much as
  informationless: it ignores `args[0]` entirely and cannot distinguish
  `UBoolean(true, 1)` from `UBoolean(true, 0)` (which displays as `false`). It contributes nothing
  from the uncertainty component. Treat this as a **stub** the port should reproduce
  bit-for-bit only if oracle-compatibility is the goal; flag it as a known defect otherwise.
- **Special cases:** none — there are no branches. Undefined receiver is intercepted upstream by
  the `OPERATION` kind, so even `oclUndefined.value()` yields Undefined rather than `true`.

### 3.5 `setValue` — lines 197–228

- **Notation:** dot-call, `ub.setValue(b)`.
- **`matches`** (215–219): `params.length == 2 && params[0].isTypeOfUBoolean() &&
  params[1].isTypeOfBoolean()` → `TypeFactory.mkUBoolean()`. Note `isTypeOfBoolean()` — the exact
  type, so a `UBoolean` second argument does **not** match.
- **Semantics:** replaces the value label while **carrying the receiver's confidence number over
  as the confidence in the *new* label**. Because the result is re-canonicalised, asserting
  `false` inverts the stored confidence.
- **`eval` (222–227):**

  ```java
  UBooleanValue ub = (UBooleanValue) args[0];      // unguarded cast
  BooleanValue  b  = (BooleanValue)  args[1];
  return UBooleanValue.valueOf(b.value(), ub.probability());
  ```

  With `UBooleanValue.valueOf(boolean, double)` (UBooleanValue.java:96–113):
  `setValue(true)` on `(true, c)` → `(true, c)` (identity);
  `setValue(false)` on `(true, c)` → normalises to `(true, 1 - c)`.
- **Special cases:**
  - Confidence lands on 0 → the `FALSE` singleton; on 1 → the `TRUE` singleton (interning).
  - Worked edge case: receiver `UBooleanValue.FALSE` = `(true, 0)`, call `setValue(false)` →
    `valueOf(false, 0)` → `value = true, probability = 1 - 0 = 1` → **`TRUE` singleton `(true, 1)`**.
    That is internally consistent ("false with confidence 0" ≡ "true with confidence 1") but is a
    surprising round-trip and a good regression-test candidate.
  - **Unguarded `(UBooleanValue) args[0]` cast** (line 223) — unlike #1 which tests
    `isUBoolean()` first. If a `BooleanValue` reaches this slot dynamically (the `Set(UBoolean)`
    scenario the fork itself documents at lines 64–65), this throws **`ClassCastException`**, which
    `ExpStdOp` does not catch (it catches only `ArithmeticException`). See §5.4.
  - Either argument undefined → Undefined before `eval`.

### 3.6 `confidence` — lines 232–260

- **Notation:** dot-call.
- **`matches`** (250–253): arity 1, `isTypeOfUBoolean()` → `TypeFactory.mkReal()`.
- **`eval` (256–259):** `new RealValue(UBooleanValue.valueOf(args[0]).probability())`.
- **Semantics — read this carefully:** returns the **canonical** confidence, i.e. `P(value = true)`,
  **not** the confidence in the label that `toString` displays. For a value that prints as
  `UBoolean(false, 0.7)` (canonical `c = 0.3`), `.confidence()` returns **`0.3`**, not `0.7`.
  `confidence` and `toString` therefore disagree by construction on every value with `c < 0.5`.
  This is the highest-value behavioural fact in this file for test design.
- **Special cases:**
  - A dynamic `BooleanValue` receiver is accepted via `valueOf(Value)`: `true` → `1.0`,
    `false` → `0.0`.
  - `valueOf` returning `null` (receiver neither `UBoolean` nor `Boolean`) → NPE, unguarded but
    unreachable through `matches`.
  - Undefined receiver → Undefined before `eval`.
  - Values of `c` are returned raw, so accumulated floating-point noise is visible here (e.g.
    `(a or b).confidence()` = `0.9199999999999999` for `a = 0.8, b = 0.6`, from the live jar).

### 3.7 `setConfidence` — class `Op_uBoolean_setUncertainty`, lines 262–304

- **Name/class mismatch — the single most likely thing to get wrong in the port.** The class is
  `Op_uBoolean_setUncertainty` but `name()` (lines 266–268) returns **`"setConfidence"`**, and
  `registerOperation` keys the multimap on `name()`. The registration call at line 25 reads
  `new Op_uBoolean_setUncertainty()`, so a reader scanning the registration block sees
  "setUncertainty" and the OCL surface says "setConfidence". The header comment on line 261
  (`setConfidence : UBoolean x Real -> UBoolean`) agrees with `name()`. There is **no** OCL
  operation called `setUncertainty`.
- **Notation:** dot-call, `ub.setConfidence(c)`.
- **`matches`** (280–284): `params.length == 2 && params[0].isTypeOfUBoolean() &&
  params[1].isKindOfReal(EXCLUDE_VOID)` → `TypeFactory.mkUBoolean()`.
- **Semantics:** replaces the confidence component outright, keeping the canonical value component
  — since that component is always `true` (§3.0), the result is effectively `(true, newC)`. It is
  the exact dual of #6: `setConfidence` writes the same quantity `confidence` reads.
- **`eval` (287–303), every branch:**
  - Reads `args[1]` as `RealValue.value()` or `IntegerValue.value()` (cast safe, see §3 preamble).
  - `uncertainty >= 0 && uncertainty <= 1` → `UBooleanValue.valueOf(ub.value(), uncertainty)`.
  - else → `UndefinedValue.instance`.
- **Special cases:**
  - `0` → `FALSE` singleton; `1` → `TRUE` singleton (interning).
  - **`NaN` → Undefined** (both `>= 0` and `<= 1` are false for NaN). Contrast #3, which returns
    `FALSE` for NaN.
  - `±Infinity` → Undefined.
  - Either argument undefined → Undefined before `eval`.
  - The local is named `uncertainty` (lines 289, 293, 295, 297, 298) though it holds a
    *confidence*; the value is used directly as `c`, **not** as `1 - c`. Verified by reading: it
    is passed unmodified to `valueOf(ub.value(), uncertainty)`.
  - Because `valueOf(boolean, double)` normalises on a `false` first argument and `ub.value()` is
    always `true`, no inversion occurs here.

### 3.8 `equalsC` — lines 307–352

- **Notation:** dot-call, `ub.equalsC(other, c)`. **Arity 3.**
- **`matches`** (325–330): `params.length == 3 && params[0].isTypeOfUBoolean() &&
  params[1].isKindOfUBoolean(EXCLUDE_VOID) && params[2].isKindOfReal(EXCLUDE_VOID)` →
  `TypeFactory.mkBoolean()`. Note the asymmetry: `params[0]` must be **exactly** `UBoolean` while
  `params[1]` need only be a **kind of** `UBoolean` — and since `BooleanType.isKindOfUBoolean()`
  returns `true` in the fork (BooleanType.java:50–52), a plain `Boolean` is admissible as
  `params[1]`.
- **Semantics:** "equal within a tolerance derived from the required confidence" — the two values
  count as equal iff their confidences differ by no more than `1 - c`. The uncertainty components
  are combined by **absolute difference**, then compared against the slack `1 - c`.
  `uDataTypes.UBoolean.equalsC` (UBoolean.java:154–158):

  ```java
  public boolean equalsC(UBoolean b, double confidence) {
      return java.lang.Math.abs(this.getC() - b.getC()) <= (1 - confidence);
  }
  ```

  Verified on the live jar: `(0.8).equalsC(0.6, 0.7)` → `true` (|0.2| ≤ 0.3);
  `(0.8).equalsC(0.6, 0.9)` → `false` (|0.2| ≰ 0.1).
- **This is *not* "`a.equivalent(b)` has confidence ≥ c".** The commented-out lines 155–156 of
  UBoolean.java show that was the earlier intent; the shipped code implements the tolerance
  formula instead. Do not "fix" this during the port.
- **`eval` (333–351):** reads `c` from `args[2]`; if `c >= 0 && c <= 1` → `left.equalsC(args[1], c)`
  (which wraps in `BooleanValue.get`, UBooleanValue.java:346–349), else `UndefinedValue.instance`.
- **Special cases:**
  - `c == 1` → demands exact confidence equality (`|Δ| <= 0`).
  - `c == 0` → always `true` (`|Δ| <= 1` always holds for confidences in [0,1]).
  - `c == NaN` → Undefined (both range tests false).
  - `c` outside [0,1] → Undefined.
  - Any argument undefined → Undefined before `eval` (OPERATION kind), so
    `UBooleanValue.assertKindOfUBoolean`'s `ClassCastException` path (UBooleanValue.java:295–302)
    is unreachable here.
  - `args[1]` as a plain `Boolean` → mapped to `TRUE` (`c=1`) / `FALSE` (`c=0`) by `valueOf`.
  - Dead code: the local `right` declared on line 334 is never assigned or read.
  - Note this uses `UBoolean.getC()` on both sides, **not** `UBoolean.equals(Object)`
    (UBoolean.java:139–148), which uses a fixed `< 0.001` tolerance. Two different equality
    notions coexist in the type.

### 3.9 `and` — lines 356–424

- **Base:** `BooleanOperation` → `kind() == SPECIAL`, `isBooleanOperation() == true`, so
  `ExpStdOp` calls `evalWithArgs(ctx, Expression[])` with **unevaluated** operands.
- **Notation:** **infix**, `(a and b)` — `isInfixOrPrefix()` → `true` (364–366), arity 2, so
  `OpGeneric.stringRep` line 64 renders `"(" + a + " and " + b + ")"`.
- **`matches`** (369–373): `params.length == 2 &&
  params[0].isKindOfUBoolean(Type.VoidHandling.INCLUDE_VOID) &&
  params[1].isKindOfUBoolean(INCLUDE_VOID)` → **`TypeFactory.mkUBoolean()` unconditionally** —
  even when both operands are plain `Boolean`. Contrast #14, which special-cases that. This is
  safe only because of registration order (§4.3).
- **`eval` (376–378)** explicitly overrides the inherited throw with its own:
  `throw new RuntimeException("Use evalWithArgs insteed");` — note the typo `insteed`, and note it
  differs from the inherited message `"Use evalWithArgs"`. Ops #10, #12, #13, #14 do **not**
  override and therefore carry the inherited message. If any test asserts on this message text,
  the two spellings must be preserved separately.
- **Semantics / confidence combination:** `UBoolean.and` (UBoolean.java:83–93) computes
  `b = b1 & b2`, `c = c1 * c2` — the product rule, i.e. an **independence assumption**.
  Verified on the live jar: `0.8 and 0.6` → `c = 0.48`.
- **`evalWithArgs` (381–423), every branch:**
  1. `result` defaults to `UndefinedValue.instance`.
  2. `v1 = args[0].eval(ctx)`.
  3. `if (ctx.isEnableEvalTree()) v2 = args[1].eval(ctx);` — **when the evaluation tree is
     enabled, the right operand is always evaluated**, i.e. short-circuiting is deliberately
     suppressed so the tree can display both operands. Every branch below re-checks
     `!ctx.isEnableEvalTree()` before evaluating `args[1]`, so it is evaluated exactly once either
     way. This eval-tree-dependent evaluation order is observable through side effects and must be
     preserved.
  4. `v1` defined:
     - `ub1.probability() == 0` → **result = `ub1`, right operand never evaluated** (short-circuit
       "false and X = false"). Note it returns the *receiver object*, preserving its identity.
     - else evaluate `v2`; `ub2 = valueOf(v2)`:
       - `ub2 != null && ub2.probability() == 0` → result = `ub2`;
       - `ub2 != null` → result = `ub1.and(ub2)`;
       - `ub2 == null` (i.e. `v2` undefined) → result stays **Undefined**.
  5. `v1` undefined:
     - evaluate `v2`; **guarded by `if (v2.isDefined())`**;
     - `ub2.probability() == 0` → result = `ub2` (so `undefined and false = false`, the OCL rule);
     - otherwise result stays **Undefined**.
- **Confidence 0 / 1:** `c == 0` is the short-circuit case on either side; `c == 1` has **no**
  special case and falls through to the product (`1 * c2 = c2`, which is correct anyway).
- **Aliasing hazard (see §5.5):** `UBoolean.and` opens with
  `if (this == b) return new UBoolean(this.b & b.b, this.c);` — **reference** identity, applying
  the idempotent law `x and x = x` instead of the product. Verified on the live jar: `p.and(p)`
  with `p = 0.8` → `0.8`, **not** `0.64`.

### 3.10 `or` — lines 427–482

- **Base:** `BooleanOperation` / `SPECIAL`. **Does not override `eval()`** — inherits
  `BooleanOperation.eval`'s `throw new RuntimeException("Use evalWithArgs")`.
- **Notation:** **infix**, `(a or b)` (434–436 → `true`).
- **`matches`** (439–444): both operands kind-of `UBoolean` (INCLUDE_VOID) →
  `TypeFactory.mkUBoolean()` unconditionally.
- **Semantics / confidence combination:** `UBoolean.or` (UBoolean.java:95–105) computes
  `b = b1 | b2`, `c = c1 + c2 - c1*c2` — the probabilistic-OR / inclusion–exclusion rule under
  independence. Verified on the live jar: `0.8 or 0.6` → `c = 0.9199999999999999` (not exactly
  0.92 — the floating-point residue is observable, see §3.2).
- **`evalWithArgs` (447–481), every branch:**
  1. `v1 = args[0].eval(ctx)`; eager `v2` when `ctx.isEnableEvalTree()`, same protocol as `and`.
  2. `v1` defined:
     - `ub1.probability() == 1` → result = `ub1`, **right operand never evaluated**
       (short-circuit "true or X = true");
     - else evaluate `v2`, `ub2 = valueOf(v2)`; `ub2 != null` → `ub1.or(ub2)`; `ub2 == null`
       (undefined `v2`) → Undefined.
  3. `v1` **undefined** (lines 470–478):

     ```java
     if (!ctx.isEnableEvalTree())
         v2 = args[1].eval(ctx);
     ub2 = UBooleanValue.valueOf(v2);
     if (ub2.probability() == 1)          // <-- no null check
         result = ub2;
     ```

     **`or` is the only one of the three short-circuiting binaries that omits the
     `v2.isDefined()` guard.** When both operands are undefined, `valueOf` returns `null` and
     `ub2.probability()` throws **`NullPointerException`** — which `ExpStdOp` does not catch. See
     §5.1. Compare `and` line 411 (`if (v2.isDefined())`) and `implies` line 552 (same guard).
- **Confidence 0 / 1:** `c == 1` is the short-circuit case; `c == 0` has no special case and falls
  through to the formula (`0 + c2 - 0 = c2`, correct).
- **Aliasing hazard:** `UBoolean.or` has the same `if (this == b)` self-branch returning `this.c`
  unchanged. Verified on the live jar: `p.or(p)` with `p = 0.8` → `0.8`, **not** `0.96`.

### 3.11 `not` — lines 485–516

- **Base:** `BooleanOperation` / `SPECIAL`. Does not override `eval()`.
- **Notation:** **prefix**, `not x`. `isInfixOrPrefix()` → `true` (507–509) **and** arity 1, and
  `OpGeneric.stringRep` lines 57–61 renders the 1-argument infix/prefix case as
  `name() + " " + args[0]` (the space is deliberate, to avoid `--` being read as a comment).
- **`matches`** (512–515): `params.length == 1 && params[0].isKindOfUBoolean(INCLUDE_VOID)` →
  `TypeFactory.mkUBoolean()`.
- **Semantics / confidence combination:** complements the confidence. `UBoolean.not()`
  (UBoolean.java:78–81) builds `new UBoolean(!getB(), getC())`, whose constructor immediately
  re-canonicalises to `(true, 1 - c)`. So `not` maps `c ↦ 1 - c` and the value label flips.
- **`evalWithArgs` (488–499):**
  - `value = args[0].eval(ctx)`;
  - `value.isDefined()` → `UBooleanValue.valueOf(value).not()`;
  - undefined → `UndefinedValue.instance`. Correctly guarded.
- **Special cases:**
  - `c == 0` → result `c = 1` → the `TRUE` singleton; `c == 1` → result `c = 0` → the `FALSE`
    singleton (interning).
  - Floating point is **not** exact: verified on the live jar, `not(0.8)` yields
    `c = 0.19999999999999996`, not `0.2`. Any ported test must use a tolerance or match this exact
    double.
  - `UBooleanValue.not()` (UBooleanValue.java:316–319) is declared to return
    `UncertainBooleanValue`, a supertype of `UBooleanValue`; the operation returns it as `Value`.
  - No short-circuit and no eval-tree branch — a unary operation has nothing to skip.

### 3.12 `implies` — lines 519–584

- **Base:** `BooleanOperation` / `SPECIAL`. Does not override `eval()`.
- **Notation:** **infix**, `(a implies b)` (573–575 → `true`).
- **`matches`** (578–583): both operands kind-of `UBoolean` (INCLUDE_VOID) →
  `TypeFactory.mkUBoolean()` unconditionally.
- **Semantics / confidence combination:** material implication lifted to confidences,
  `c = (1 - c1) + c2 - (1 - c1)·c2` — literally `not(a) or b` applied to the confidence components.
  `UBoolean.implies` (UBoolean.java:107–116) also sets `b = (!b1) | b2`. Verified on the live jar:
  `0.8 implies 0.6` → `c = 0.6799999999999999`.
- **`evalWithArgs` (522–565), every branch:**
  1. `v1 = args[0].eval(ctx)`; eager `v2` when `ctx.isEnableEvalTree()`.
  2. `v1` defined:
     - **`ub1.probability() == 0` → result = `UBooleanValue.TRUE`**, a hard-coded `(true, 1.0)`;
       right operand never evaluated (comment on line 534 states this explicitly). Note this
       *discards* the operand rather than returning it, unlike `and`/`or` which return `ub1`.
     - else evaluate `v2`; `ub2 = valueOf(v2)`; `ub2 != null` → `ub1.implies(ub2)`; `ub2 == null`
       → Undefined (comment line 545: "else, result = undefined by default").
  3. `v1` undefined:
     - evaluate `v2`; **guarded by `if (v2.isDefined())`** (line 552);
     - `ub2.probability() == 1` → result = `ub2` (so `undefined implies true = true`);
     - otherwise Undefined.
- **Confidence 0 / 1:** `c1 == 0` short-circuits to `TRUE`; `c2 == 1` is the rescue case when the
  antecedent is undefined; no special case for `c1 == 1` (formula gives `0 + c2 - 0 = c2`, correct).
- **Aliasing hazard, and why the operation masks it:** `UBoolean.implies` has the same
  `if (this == b)` self-branch, returning `this.c` unchanged. Verified on the live jar:
  `p.implies(p)` with `p = 0.8` → `0.8`, whereas the tautology `x implies x` should be `1.0`.
  For `p = FALSE` (`c = 0`) the library would return `0.0` — flatly wrong — **but the operation
  short-circuits at step 2 before ever calling the library**, returning `TRUE`. So the fork's OCL
  surface hides the worst case while still exposing the `0 < c < 1` case. Worth an explicit
  regression test.

### 3.13 `xor` — lines 587–623

- **Base:** `BooleanOperation` / `SPECIAL`. Does not override `eval()`.
- **Notation: dot-call, `a.xor(b)`** — `isInfixOrPrefix()` returns **`false`** (lines 612–614).
  **This contradicts the core `Boolean` registry**, where `xor` is infix: fork
  `StandardOperationsBoolean.java:87–88` → `true`, and 7.5.0
  `StandardOperationsBoolean.java:87–88` → `true`. `isInfixOrPrefix()` only feeds
  `OpGeneric.stringRep`, so this affects **how the expression is printed back**, not how it parses:
  a `UBoolean` `xor` expression round-trips as `a.xor(b)` while a `Boolean` one round-trips as
  `(a xor b)`. Any golden-output test over expression text is sensitive to this.
- **`matches`** (617–622): both operands kind-of `UBoolean` (INCLUDE_VOID) →
  `TypeFactory.mkUBoolean()` unconditionally.
- **Semantics / confidence combination:** `UBoolean.xor` (UBoolean.java:124–130) is
  `new UBoolean(true, |c1 - c2|)` — the value component is **hard-coded `true`** (not `b1 ^ b2`)
  and the confidence is the **absolute difference** of confidences. Verified on the live jar:
  `0.8 xor 0.6` → `c = 0.20000000000000007`.
- **`evalWithArgs` (591–604):**

  ```java
  left  = UBooleanValue.valueOf(args[0].eval(ctx));
  right = UBooleanValue.valueOf(args[1].eval(ctx));
  if (left != null && right != null) result = left.xor(right);
  else                              result = UndefinedValue.instance;
  ```

  - **No short-circuit and no `ctx.isEnableEvalTree()` branch** — both operands are always
    evaluated exactly once, in left-to-right order, regardless of eval-tree mode. `xor` genuinely
    cannot short-circuit, so this is defensible, but it makes `xor` the only binary here whose
    evaluation order does not depend on eval-tree mode.
  - Undefined operands are handled **correctly** via the `null` return of `valueOf` — this is the
    guard `or` is missing.
- **Special cases:**
  - `c1 == c2` → `0` → the `FALSE` singleton. In particular **`x xor x` → `FALSE`**, because
    `UBoolean.xor` is the one binary with **no `this == b` self-branch**. Verified on the live jar:
    `p.xor(p)` → `0.0`. So `xor` is the only operation of the four that gets idempotence right.
  - `|c1 - c2| == 1` → the `TRUE` singleton.
  - Local `result` is initialised to `null` and assigned on every path — never returned as `null`.

### 3.14 `equivalent` — lines 626–685

- **Base:** `BooleanOperation` / `SPECIAL`. Does not override `eval()`.
- **Notation: dot-call, `a.equivalent(b)`** — `isInfixOrPrefix()` → `false` (662–664).
- **`matches` (667–684) — the only *type-dependent* result type in this file:**

  ```java
  if (params.length == 2 &&
      params[0].isKindOfUBoolean(INCLUDE_VOID) &&
      params[1].isKindOfUBoolean(INCLUDE_VOID)) {
      if (params[0].isTypeOfBoolean() && params[1].isTypeOfBoolean())
          result = TypeFactory.mkBoolean();
      else
          result = TypeFactory.mkUBoolean();
  }
  ```

  Because `BooleanType.isKindOfUBoolean()` is `true` in the fork, two plain `Boolean` operands
  match — and since the core registry has **no** `equivalent` at all (verified: zero definitions of
  `return "equivalent";` under the 7.5.0 operations directory), this operation is the *sole*
  provider of `equivalent` for **both** `Boolean` and `UBoolean`. That is why it must degrade its
  result type to `Boolean`. Ops #9–#13 do not need this because the core `Boolean` registry
  already owns those names and wins by registration order (§4.3).
- **Semantics / confidence combination:** `c = 1 - |c1 - c2|`, i.e. `not(xor)`.
  `UBoolean.equivalent` (UBoolean.java:118–122) is literally `return this.xor(b).not();`.
  Verified on the live jar: `0.8 equivalent 0.6` → `c = 0.7999999999999999`.
  Since `xor` has no self-branch, `x equivalent x` → `1 - 0 = 1` → `TRUE`, which is correct.
- **`evalWithArgs` (630–654), every branch:**

  ```java
  a = args[0].eval(ctx);
  b = args[1].eval(ctx);
  resultBoolean = a.isBoolean() && b.isBoolean();
  left  = UBooleanValue.valueOf(args[0].eval(ctx));   // args re-evaluated
  right = UBooleanValue.valueOf(args[1].eval(ctx));   // args re-evaluated
  if (left != null && right != null) {
      result = left.equivalent(right);
      if (resultBoolean) result = ((UBooleanValue) result).toBoolean();
  } else
      result = UndefinedValue.instance;
  ```

  - **Both operands are evaluated twice** (lines 636–637, then again at 640–641), rather than
    reusing `a` and `b`. See §5.2.
  - `resultBoolean` is computed from the **dynamic** values, while the declared result type came
    from the **static** types. When both are dynamically `Boolean`, the `UBooleanValue` result is
    collapsed via `toBoolean()` (threshold `c >= 0.5`).
  - Undefined operands → `valueOf` returns `null` → Undefined. Correctly guarded.
- **Static/dynamic result-type mismatch (see §5.3):** if the static types are `UBoolean` (so
  `matches` returned `UBoolean`) but the runtime values are `BooleanValue`s — the `Set(UBoolean)`
  situation the fork documents at lines 64–65 — then `resultBoolean` is `true` and the operation
  returns a **`BooleanValue` from an expression statically typed `UBoolean`**.
- **Confidence 0 / 1:** no explicit special cases; `c1 == c2` → `1` → `TRUE` singleton,
  `|c1 - c2| == 1` → `0` → `FALSE` singleton (via interning).

---

## 4. Cross-check against the USE 7.5.0 registry

### 4.1 `OpGeneric`: the contract members are **identical**

`diff -u <(tr -d '\r' < fork/OpGeneric.java) <(tr -d '\r' < 7.5.0/OpGeneric.java)` produces exactly
**one** hunk, and it is not a signature change:

```
@@ -88,13 +88,6 @@
 		StandardOperationsBoolean.registerTypeOperations(opmap);
-
-		// Uncertainty Types
-        StandardOperationsUReal.registerTypeOperations(opmap);
-        StandardOperationsUBoolean.registerTypeOperations(opmap);
-        StandardOperationsUInteger.registerTypeOperations(opmap);
-        StandardOperationsUString.registerTypeOperations(opmap);
-        StandardOperationsSBoolean.registerTypeOperations(opmap);
 		
 		// Collections
```

(The raw `diff` shows the whole file changed; that is a line-ending artefact — `file` reports the
fork's `OpGeneric.java` as `ASCII text` and 7.5.0's as `ASCII text, with CRLF line terminators`.
All diffs in this document are run through `tr -d '\r'`.)

**Every abstract and overridable member is byte-identical between the two versions:** `name()`,
`kind()`, `isInfixOrPrefix()`, `matches(Type[])`, `eval(EvalContext, Value[], Type)`,
`isBooleanOperation()`, `checkWarningUnrelatedTypes(Expression[])`, `stringRep(Expression[],
String)`, both `registerOperation` overloads, and the `OPERATION = 0` / `SPECIAL = 3` constants.

**`BooleanOperation` is byte-identical** after CRLF normalisation (`diff` exit code 0 — no output).

**`ExpStdOp.eval` is byte-identical** after CRLF normalisation: the only hunks in the whole file
are a removed `$Id:` keyword line, an import reordering (`java.util.List` vs the Guava imports),
a removed `@version $ProjectVersion:` tag, and two Javadoc rewrites. The dispatch body
(fork 278–325 / 7.5.0 271–318), including the `isBooleanOperation()` branch, the
`OPERATION`/`SPECIAL` switch and the `ArithmeticException` catch, is unchanged.

**Conclusion: no `OpGeneric` contract member's signature differs.** The port does not have to
adapt any operation to a changed abstract-method shape. Each of the 14 classes can be moved across
with its five overrides intact.

### 4.2 What *is* missing in 7.5.0 — the infrastructure each operation depends on

The contract is stable; the **vocabulary** is not. Every one of the 14 operations references at
least one symbol that does not exist in 7.5.0. Verified by direct file/`grep` checks:

| Symbol the ops need | Fork location | 7.5.0 status |
|---|---|---|
| `Type.isTypeOfUBoolean()` | `type/Type.java:114` | **absent** (7.5.0 `Type.java` has `isTypeOfBoolean` at :102, no `UBoolean` anywhere) |
| `Type.isKindOfUBoolean(VoidHandling)` | `type/Type.java:112` | **absent** |
| `TypeFactory.mkUBoolean()` | `type/TypeFactory.java:107` (+ field :51) | **absent** (7.5.0 `mk*` set ends at `mkTuple`/`mkSimpleType`) |
| `UBooleanType` | `type/UBooleanType.java` | **absent** |
| `UncertainBooleanType`, `UncertainType` | `type/…` | **absent** |
| `Value.isUBoolean()` | `value/Value.java:100` | **absent** (7.5.0 `Value` `is*` set: Integer, UnlimitedNatural, Real, Boolean, Defined, Undefined, Collection, Bag, Set, Sequence, OrderedSet, Object, Link) |
| `UBooleanValue` | `value/UBooleanValue.java` | **absent** |
| `UncertainBooleanValue` | `value/UncertainBooleanValue.java` | **absent** |
| `MathUtil.round(double, int)` | `util/MathUtil.java:106` | **absent** — `MathUtil.java` exists in 7.5.0 but exposes only `max`/`min` (4 overloads). Needed by `UBooleanValue.toString`, not by the ops file itself. |
| `BooleanType.isKindOfUBoolean` → `true`; `conformsTo` admits `UBoolean` | `type/BooleanType.java:50–52, 63–65` | 7.5.0 `BooleanType.conformsTo` (:50–52) is `this.equals(other) \|\| other.isTypeOfOclAny()` — **Boolean does not conform to UBoolean** |
| `uDataTypes.UBoolean` | oracle jar `lib/atenearesearchgroup.uncertainty.jar` | not on the 7.5.0 classpath |

Everything the ops file uses that *is* present in 7.5.0 and unchanged:
`Type.VoidHandling` (7.5.0 `Type.java:33`), `Type.isKindOfReal(VoidHandling)` (:92),
`Type.isTypeOfBoolean()` (:102), `TypeFactory.mkBoolean()/mkReal()/mkString()`,
`RealValue(double)` (:34) and `RealValue.value()` (:39), `StringValue(String)` (:32),
`BooleanValue.TRUE/FALSE/get(boolean)` (:33/:38/:55), `UndefinedValue.instance`,
`IntegerValue.value()` (:46 — returns `int`), `EvalContext`, `Expression`, `Multimap`.

### 4.3 Registration order is load-bearing — do not reorder

Six of the fourteen names already exist in the 7.5.0 registry. Verified owners:

| Name | 7.5.0 owner |
|---|---|
| `and` | `StandardOperationsBoolean.java:120` |
| `or` | `StandardOperationsBoolean.java:35` |
| `not` | `StandardOperationsBoolean.java:169` |
| `implies` | `StandardOperationsBoolean.java:196` |
| `xor` | `StandardOperationsBoolean.java:84` |
| `toString` | `StandardOperationsEnum.java:46`, `StandardOperationsBoolean.java:246`, `StandardOperationsNumber.java:887` |
| `toBoolean` | `StandardOperationsString.java:547` |

The remaining seven — `value`, `confidence`, `setValue`, `setConfidence`, `equalsC`,
`toBooleanC`, `equivalent` — have **zero** definitions in 7.5.0 and are new names.

In the fork, `OpGeneric.registerOperations` calls `StandardOperationsBoolean` **before**
`StandardOperationsUBoolean` (fork OpGeneric.java:90 then :94). Combined with the first-match-wins
loop in `ExpStdOp.create` and the fact that `BooleanType.isKindOfUBoolean()` returns `true`, this
ordering is what keeps plain `Boolean` expressions behaving normally: for `true and false`, the
list under key `"and"` is `[Op_bool_and, Op_uBoolean_and]`, `Op_bool_and.matches` succeeds first,
and the `Boolean` semantics win. The `UBoolean` overload is reached only when the `Boolean`
overload's `matches` returns `null`.

**If the port registers `StandardOperationsUBoolean` before `StandardOperationsBoolean`, every
plain-`Boolean` `and`/`or`/`not`/`implies`/`xor` in every existing model silently changes result
type from `Boolean` to `UBoolean`.** This is the highest-blast-radius ordering constraint in the
port. `equivalent` is the deliberate exception: with no core competitor, it handles the
`Boolean`-`Boolean` case itself inside `matches`.

### 4.4 Conventions this file *breaks* relative to `StandardOperationsNumber`

Skimming the fork's `StandardOperationsNumber.java` (which follows upstream style closely):

- It declares the registry class **package-private** (`class StandardOperationsNumber`, line 16);
  `StandardOperationsUBoolean` is **`public`** (line 10). Cosmetic, but inconsistent.
- It factors shared `kind()`/`matches()` into an intermediate abstract base (`ArithOperation`,
  lines 54–95) reused by `Op_number_add`/`sub`/`mult`/`max`/`min`. `StandardOperationsUBoolean`
  has **no** such factoring: all eight `OPERATION`-kind classes repeat `kind()`,
  `isInfixOrPrefix()` verbatim.
- It groups registrations with comments by receiver type (`// Real`, `// Integer`).
  `StandardOperationsUBoolean`'s registration block (14–27) is unordered relative to the class
  definitions in the file (registration order: toBoolean, toString, value, confidence, setValue,
  equalsC, and, or, not, implies, toBooleanC, setConfidence, xor, equivalent; definition order:
  toBoolean, toString, toBooleanC, value, setValue, confidence, setConfidence, equalsC, and, or,
  not, implies, xor, equivalent). Harmless — `create` matches on `matches()`, not position, and no
  two UBoolean ops share a name — but it makes review harder.
- It uses `import org.tzi.use.uml.ocl.value.*` — same as `StandardOperationsUBoolean` line 8 — so
  the wildcard import is idiomatic for this codebase, not a fork-ism.

---

## 5. Defects and hazards found (each with file:line and, where possible, live-jar evidence)

These are recorded so the port makes a **deliberate** decision on each: reproduce for
oracle-compatibility, or fix and document the divergence.

### 5.1 `or` throws `NullPointerException` when both operands are undefined — **highest severity**

`StandardOperationsUBoolean.java:474–477`:

```java
ub2 = UBooleanValue.valueOf(v2);
if (ub2.probability() == 1)     // no null check
    result = ub2;
```

Reached when `v1` is undefined. If `v2` is also undefined, `UBooleanValue.valueOf(Value)`
(UBooleanValue.java:122–138) returns `null` because `UndefinedValue` is neither `isUBoolean()` nor
`isBoolean()` → NPE on `.probability()`. `ExpStdOp` catches only `ArithmeticException`
(fork ExpStdOp.java:317–320), so the NPE propagates out of expression evaluation.

`and` (line 411) and `implies` (line 552) both guard the same position with `if (v2.isDefined())`.
`or` is the outlier. Expected OCL behaviour is `Undefined`.

Establishment: code reading of the three sibling branches, not execution — I did not build the
fork (Maven is off-limits and the fork is Ant/Java 1.7). Marked **CONFIRMED BY READING,
NOT EXECUTED**.

### 5.2 `equivalent` evaluates both operands twice

`StandardOperationsUBoolean.java:636–641`: `a`/`b` are computed from `args[0].eval(ctx)` /
`args[1].eval(ctx)`, then lines 640–641 call `args[0].eval(ctx)` / `args[1].eval(ctx)` **again**
instead of reusing `a`/`b`. Consequences: duplicated side effects, duplicated evaluation-tree
nodes, and doubled cost. The fix is a two-line change (`valueOf(a)` / `valueOf(b)`), but it is a
**behaviour change** wherever the evaluation tree is inspected.

### 5.3 Declared result type vs. actual runtime value can disagree

Two independent instances:

- **`toBoolean`** (lines 55–58 vs 70–72): `matches` promises `Boolean`; the `args[0].isBoolean()`
  branch returns `UBooleanValue.valueOf(…)`, a **`UBooleanValue`**.
- **`equivalent`** (lines 667–684 vs 646–647): `matches` returns `UBoolean` whenever either static
  type is not exactly `Boolean`, but `evalWithArgs` collapses to `BooleanValue` whenever both
  **runtime** values are `isBoolean()`.

Both are reachable only in the mixed `Set(UBoolean)` scenario the fork itself documents at lines
64–65. A 7.5.0-era type-safety assertion in the evaluator could turn these into hard failures.

### 5.4 `setValue` casts the receiver without an `isUBoolean()` guard

`StandardOperationsUBoolean.java:223`: `UBooleanValue ub = (UBooleanValue) args[0];`. Op #1 guards
the identical situation with `if (args[0].isUBoolean())`. A `BooleanValue` arriving here throws
`ClassCastException`, uncaught by `ExpStdOp`.

### 5.5 Reference-identity aliasing in the library changes results

`uDataTypes.UBoolean.and`/`or`/`implies` each open with `if (this == b) …`
(UBoolean.java:85, 97, 109), applying an idempotent shortcut based on **object identity**, not
value equality. Verified against the **live oracle jar**
(`lib/atenearesearchgroup.uncertainty.jar`, `uDataTypes/UBoolean.class`, compiled 2021-02-24):

| Expression (`p = (true, 0.8)`) | Live jar result | Formula would give |
|---|---|---|
| `p.and(p)` | `0.8` | `0.64` |
| `p.or(p)` | `0.8` | `0.96` |
| `p.implies(p)` | `0.8` | `1.0` (tautology) |
| `p.xor(p)` | `0.0` | `0.0` (no self-branch — correct) |

Because `UBooleanValue.TRUE`/`FALSE` are singletons (UBooleanValue.java:27, 31) each wrapping one
`UBoolean` instance, this branch **is** hit for `true and true`, `false and false`, etc. For those
the shortcut is harmless (`1*1 = 1`, `0*0 = 0`, `1+1-1 = 1`). It becomes observable whenever the
same non-singleton `UBooleanValue` object reaches both operand positions — e.g. an OCL expression
mentioning the same attribute twice, if the evaluator returns the identical object both times.
Whether the fork's evaluator actually shares objects that way is **UNVERIFIABLE** here without
running the fork.

### 5.6 `value` is a stub

`StandardOperationsUBoolean.java:189–191` returns `BooleanValue.TRUE` unconditionally, ignoring
the receiver; and its header comment (line 164) claims a `Real` result while `matches` (line 185)
returns `Boolean`. See §3.4.

### 5.7 NaN handling is inconsistent across the three threshold-taking operations

| Op | NaN threshold | Line |
|---|---|---|
| `toBooleanC` | → `BooleanValue.FALSE` | falls through the pre-set at :146 |
| `setConfidence` | → `UndefinedValue` | :297–300 |
| `equalsC` | → `UndefinedValue` | :345–348 |

`toBooleanC` is the outlier purely because its result variable is pre-initialised to `FALSE`
rather than to `Undefined`.

### 5.8 `xor` and `equivalent` render as dot-calls while core `xor` renders infix

§3.13. Affects `OpGeneric.stringRep` output only.

### 5.9 Minor

- `Op_uBoolean_setUncertainty` registers as `"setConfidence"` (§3.7) — a naming trap, not a bug.
- `Op_uBoolean_and.eval` throws `"Use evalWithArgs insteed"` (typo, line 377); the four sibling
  `BooleanOperation`s that do not override `eval` inherit `"Use evalWithArgs"`.
- Unused local `right` in `Op_uBoolean_equalsC` (line 334).
- Misspelled local `confience` in `Op_uBoolean_toBooleanC` (lines 147–157).
- Stale comment `// toBoolean : UBoolean -> Boolean` above `Op_uBoolean_toBooleanC` (line 119).

---

## 6. The underlying algebra, verified against the live oracle jar

Confidence-combination rules, sourced from `uDataTypes/UBoolean.java` and **independently
confirmed by executing the oracle jar** (`atenearesearchgroup.uncertainty.jar`, the one
`build.xml:50` binds). Probe compiled with `javac -cp …/atenearesearchgroup.uncertainty.jar` and
run; the jar's `javap` signature list matches the source file exactly.

| Operation | Rule on confidences | Source | Live-jar check (`c₁ = 0.8`, `c₂ = 0.6`) |
|---|---|---|---|
| `not` | `1 - c` | UBoolean.java:78–81 | `not(0.8)` → `0.19999999999999996` |
| `and` | `c₁ · c₂` | :83–93 | `0.48` |
| `or` | `c₁ + c₂ - c₁c₂` | :95–105 | `0.9199999999999999` |
| `implies` | `(1-c₁) + c₂ - (1-c₁)c₂` | :107–116 | `0.6799999999999999` |
| `xor` | `\|c₁ - c₂\|` (value hard-coded `true`) | :124–130 | `0.20000000000000007` |
| `equivalent` | `1 - \|c₁ - c₂\|` (as `xor().not()`) | :118–122 | `0.7999999999999999` |
| `equalsC(b, k)` | `\|c₁ - c₂\| ≤ 1 - k` | :154–158 | `k=0.7` → `true`; `k=0.9` → `false` |
| `toBoolean` | `c ≥ 0.5` | :185–188 | `0.5` → `true`; `0.4999` → `false` |

Additional live-jar findings:

- `new UBoolean(false, 0.3)` normalises to `(b=true, c=0.7)` — the canonical form of §3.0.
- `new UBoolean(true, 1.5)` → `IllegalArgumentException: Invalid parameters`
  (UBoolean.java:36). Constructors reject `c ∉ [0,1]`.
- **`new UBoolean(true, NaN)` is *accepted*** and yields `(b=true, c=NaN)` — the range check
  `(c < 0.0) || (c > 1.0)` is false for NaN. The operations' own guards (§5.7) are therefore the
  only NaN defence, and `toBooleanC` doesn't provide one.
- `UBoolean.toString()` formats as `String.format("UBoolean(%b, %5.3f)")`, flipping to display
  form below 0.5: `new UBoolean(true, 0.3).toString()` → `UBoolean(false, 0.700)`.

**Every value in the "live-jar check" column above is exact `Double.toString` output, not a
rounded approximation.** Ported tests should either assert these exact doubles or use an explicit
epsilon; asserting `0.92`, `0.2`, or `0.68` will fail.

---

## 7. Commands run, for reproduction

All commands are read-only. No Maven was invoked; nothing under
`.git/reference-repositories` or any `target/` directory was written.

```bash
REF=/home/xoruser/msc-4/use-msc2026/.git/reference-repositories/uncertainty
FORK=$REF/USE-Uncertainty/src/main/org/tzi/use
TGT=/home/xoruser/msc-4/use-msc2026/use-core/src/main/java/org/tzi/use
OPS=$FORK/uml/ocl/expr/operations
JAR=$REF/USE-Uncertainty/lib/atenearesearchgroup.uncertainty.jar

# operation count (+ two cross-checks)
grep -c 'OpGeneric\.registerOperation(new Op_uBoolean_' $OPS/StandardOperationsUBoolean.java
grep -c 'final class Op_uBoolean_.* extends '           $OPS/StandardOperationsUBoolean.java
grep -oE 'return "[a-zA-Z]+";' $OPS/StandardOperationsUBoolean.java | sort -u | wc -l

# exact class line ranges
grep -n 'final class Op_uBoolean_' $OPS/StandardOperationsUBoolean.java
awk '/^\}/{print NR": "$0}'        $OPS/StandardOperationsUBoolean.java

# contract diffs (CRLF-normalised; 7.5.0 files are CRLF)
diff -u <(tr -d '\r' < $OPS/OpGeneric.java)        <(tr -d '\r' < $TGT/uml/ocl/expr/operations/OpGeneric.java)
diff -u <(tr -d '\r' < $OPS/BooleanOperation.java) <(tr -d '\r' < $TGT/uml/ocl/expr/operations/BooleanOperation.java)
diff -u <(tr -d '\r' < $FORK/uml/ocl/expr/ExpStdOp.java) <(tr -d '\r' < $TGT/uml/ocl/expr/ExpStdOp.java)

# 7.5.0 vocabulary gaps
grep -n "UBoolean" $TGT/uml/ocl/type/Type.java            # (no output)
grep -n "public static .* mk" $TGT/uml/ocl/type/TypeFactory.java
grep -n "public boolean is" $TGT/uml/ocl/value/Value.java
grep -nE 'public (static )?[A-Za-z<>,\[\] ]+ [a-zA-Z]+\(' $TGT/util/MathUtil.java

# name collisions in the 7.5.0 registry
for n in toString and or not implies xor toBoolean; do grep -rn "return \"$n\";" $TGT/uml/ocl/expr/operations/; done

# isKindOfReal does NOT admit UnlimitedNatural (makes the (IntegerValue) casts safe)
grep -n -A4 "isKindOfReal" $FORK/uml/ocl/type/UnlimitedNaturalType.java   # (no output)
grep -n -A4 "isKindOfReal" $FORK/uml/ocl/type/TypeImpl.java               # → false

# live oracle jar: signatures, then behaviour
unzip -l $JAR | grep -i uboolean
javap -cp $JAR uDataTypes.UBoolean
javac -cp $JAR -d . Probe.java && java -cp .:$JAR Probe   # Probe.java in scratchpad, see below
```

The behaviour probe lives at
`/tmp/claude-1000/-home-xoruser-msc-4/5a883e17-9055-4019-8f36-a743005556fa/scratchpad/ub/Probe.java`
(scratchpad, outside the project). It instantiates `uDataTypes.UBoolean` directly from the jar and
prints every row of the §6 table plus the aliasing and NaN cases.

---

## 8. `UNVERIFIABLE` — gaps I could not close

1. **Whether the fork actually compiles and runs these operations end-to-end.** I did not build
   the fork: Maven is off-limits per the ground rules, and the fork is an Ant/Java 1.7/JUnit 3
   tree. All fork-side behaviour above is established by **code reading**; only the
   `uDataTypes.UBoolean` algebra (§6) is established by **execution**.
2. **The NPE in §5.1 was not reproduced at runtime.** It is confirmed by reading three sibling
   branches, one of which lacks the guard the other two have.
3. **Whether the reference-identity aliasing of §5.5 is reachable from OCL.** That depends on
   whether the fork's evaluator can return the *same* `UBooleanValue` object for both operand
   positions. Determining this needs a running evaluator.
4. **Whether the `Set(UBoolean)` mixed-value scenario** (the premise for §5.3 and §5.4) is
   reachable in practice. The fork's own comment at
   `StandardOperationsUBoolean.java:64–65` asserts it is, and `Op_uBoolean_toBoolean` was
   evidently written to handle it, but I have not exhibited a model that triggers it.
5. **Which `toString`/`toBoolean` overload wins for a `UBoolean` receiver in the fork's multimap.**
   I established the first-match-wins rule and the registration order, and that the core
   `Boolean`/`String` matchers require their own types — but I did not enumerate every
   `isKindOf*` override on `UBooleanType`'s supertype chain (`UncertainBooleanType`,
   `TypeImpl`) to prove no core matcher accidentally accepts a `UBoolean`. Worth closing before
   the port lands.
6. **Test-suite coverage of these 14 operations in the fork.** Out of scope for this file; the
   fork's tests live under
   `.git/reference-repositories/uncertainty/USE-Uncertainty/src/test/org/tzi/use` and are
   catalogued in `14-historical-tests.md`.
7. **The uDataTypes *source* tree vs. the jar.** The two agree on every signature (`javap` vs the
   `.java`) and on every behaviour I probed, but I did not decompile the jar's bytecode to prove
   the bodies are identical everywhere. The jar (2021-02-24) is the oracle; the source tree is
   corroboration.

---

## Independent refutation

Method: I read `StandardOperationsUBoolean.java` end to end and built my own operation list
(names, arities, `matches` signatures, result types, every `eval`/`evalWithArgs` branch) **before**
opening the document above. I then re-derived the supporting facts from the fork sources
(`OpGeneric`, `BooleanOperation`, `ExpStdOp`, `UBooleanValue`, the type hierarchy,
`uDataTypes/UBoolean.java`) rather than taking them from the text. No Maven, no writes outside
this directory, nothing under `.git/reference-repositories` touched.

### R.0 Verdict on the count and the table: the count is right

**14 registered operations, 14 distinct OCL names — I independently derived exactly the same set,
with the same arities, the same argument types and the same result types as §3's summary table.
No operation is missed, none is invented, no arity or type is wrong.** In particular I confirm
the four items a reader most easily gets wrong, and the document has all four right:

- `equalsC` is **arity 3** (`UBoolean` typeOf, `UBoolean` kindOf EXCLUDE_VOID, Integer-or-Real).
- class `Op_uBoolean_setUncertainty` registers under the name **`setConfidence`** (lines 262/266);
  there is no `setUncertainty` operation.
- `value` declares **`Boolean`** (`TypeFactory.mkBoolean()`, line 185) despite the `-> Real`
  header comment at line 164, and its `eval` is the constant `BooleanValue.TRUE` (lines 189–191).
- `xor` and `equivalent` are **not** infix (`isInfixOrPrefix()` → `false`, lines 612–614 and
  662–664), unlike `and`/`or`/`not`/`implies`.

There are **no loop-registered, helper-shared or multi-name registrations in this file**: all 14
calls use the one-argument `OpGeneric.registerOperation(op, opmap)` overload (fork
`OpGeneric.java:112–114`), which keys on `op.name()`; the two-argument name-overriding overload is
never used here, and the only shared base is `BooleanOperation` (6 of 14). Only
`Op_uBoolean_and` overrides `eval()` to throw its own (misspelled) message; the other five inherit
`BooleanOperation.eval`.

Reproduction (single command, run from anywhere; each of the three numbers must be 14 and the
name list must have 14 unique entries):

```bash
F=/home/xoruser/msc-4/use-msc2026/.git/reference-repositories/uncertainty/USE-Uncertainty/src/main/org/tzi/use/uml/ocl/expr/operations/StandardOperationsUBoolean.java; \
grep -c 'OpGeneric\.registerOperation(new Op_uBoolean_' "$F"; \
grep -c '^final class Op_uBoolean_' "$F"; \
grep -o 'return "[a-zA-Z]*";' "$F" | sort -u | wc -l; \
grep -o 'return "[a-zA-Z]*";' "$F" | sort -u
```
Executed → `14`, `14`, `14`, and the names
`and confidence equalsC equivalent implies not or setConfidence setValue toBoolean toBooleanC toString value xor`.

I also re-ran the arithmetic of §6 in exact IEEE-754 double arithmetic
(`python3 -c "c1=0.8;c2=0.6; print(repr(c1*c2), repr(c1+c2-c1*c2), repr((1-c1)+c2-(1-c1)*c2), repr(abs(c1-c2)), repr(1-abs(c1-c2)), repr(1-c1))"`)
→ `0.48 0.9199999999999999 0.6799999999999999 0.20000000000000007 0.7999999999999999 0.19999999999999996`,
matching every value in the §6 table digit for digit. The `or` residue really is
`0.9199999999999999` and not `0.9200000000000002`.

### R.1 SUBSTANTIVE — `equivalent` **does** have a competitor in the fork registry; §3.14 and §4.3 are wrong

§3.14 says the UBoolean `equivalent` "is the *sole* provider of `equivalent` for **both** `Boolean`
and `UBoolean`", and §4.3 concludes "`equivalent` is the deliberate exception: with no core
competitor, it handles the `Boolean`-`Boolean` case itself inside `matches`."

The 7.5.0-core half of that is true (7.5.0 has zero `return "equivalent";`). The **fork** half is
false. `StandardOperationsSBoolean.java` registers a second `equivalent`:

```
$ grep -rln 'return "equivalent";' .../USE-Uncertainty/src/main/org/tzi/use/uml/ocl/expr/operations/
StandardOperationsUBoolean.java
StandardOperationsSBoolean.java          # enum constant EQUIVALENT at :534, name() at :538
```

and its `matches` (StandardOperationsSBoolean.java:519–522, same shape as the sibling `XOR` at
:518–522) is

```java
params.length == 2 && params[0].isKindOfSBoolean(EXCLUDE_VOID)
                   && params[1].isKindOfSBoolean(EXCLUDE_VOID) ? TypeFactory.mkSBoolean() : null;
```

Both `BooleanType.isKindOfSBoolean` (`type/BooleanType.java:55–57` → `true`) and
`UBooleanType.isKindOfSBoolean` (`type/UBooleanType.java:22–24` → `true`) return `true`. So the
SBoolean `equivalent` **also matches `Boolean × Boolean` and `UBoolean × UBoolean`**, and would
return `SBoolean`. The UBoolean overload wins only because of registration order —
`OpGeneric.java:94` (`StandardOperationsUBoolean`) runs before `OpGeneric.java:97`
(`StandardOperationsSBoolean`), and `ExpStdOp.create` is first-match-wins.

Consequences for the port, which the document currently tells the reader not to worry about:

- Registration order is load-bearing for `equivalent` **exactly as much as** for
  `and`/`or`/`not`/`implies`/`xor`. If a port lands `StandardOperationsSBoolean` before
  `StandardOperationsUBoolean`, every `equivalent` silently changes result type to `SBoolean`.
- The same applies to the other five shared names in the other direction: SBoolean also registers
  `and`, `or`, `not`, `implies`, `xor`, `toString` (`grep -rln 'return "xor";' …` → Boolean,
  SBoolean, UBoolean). The full precedence chain for those names in the fork is
  **Boolean → UBoolean → SBoolean**, not the two-element chain §4.3 describes.

### R.2 SUBSTANTIVE — §3.1's claim about a plain `Boolean` receiver of `toBoolean` is false

§3.1: "a plain `Boolean` receiver does not match here (it is taken by the core `Boolean` registry
instead)." There is no `toBoolean` for a `Boolean` receiver anywhere in the fork. The only core
`toBoolean` is `Op_string_toBoolean`, whose `matches` is
`params.length == 1 && params[0].isTypeOfString()` (`StandardOperationsString.java:545–560`); the
only other one is in `StandardOperationsUString`. Since `BooleanType` is not `isTypeOfString`, and
`Op_uBoolean_toBoolean` requires `isTypeOfUBoolean` (line 56), the expression `someBoolean.toBoolean()`
matches **nothing** and `ExpStdOp.create` throws `ExpInvalidException("Undefined operation …")`
(`ExpStdOp.java:128–139`). The document's own §4.3 table (which correctly lists
`StandardOperationsString.java:547` as the `toBoolean` owner) contradicts §3.1's parenthetical.

### R.3 SUBSTANTIVE — §3.3's NPE claim for `toBooleanC` names the wrong mechanism

§3.3: "Local `left` is assigned before the range check, so a dynamic non-Boolean receiver would
NPE." Assigning `null` to `left` at line 148 is harmless; the only dereference is
`left.probability()` at **line 157**, which sits in the `else if` and is therefore reached **only
when the threshold is inside `[0,1]`**. So with a receiver that `UBooleanValue.valueOf` cannot
convert *and* an out-of-range or `NaN` threshold, the operation returns `UndefinedValue`
(line 156) with no exception at all. The ordering claim as written is wrong; the correct statement
is "the null-deref is guarded, accidentally, by the range check."

### R.4 Minor corrections

1. **§3.9, `and`:** "`result = ub1`, … Note it returns the *receiver object*, preserving its
   identity." `ub1` is `UBooleanValue.valueOf(v1)` (line 390), which for a `BooleanValue` receiver
   returns the interned `UBooleanValue.FALSE`/`TRUE` **singleton** (`UBooleanValue.java:122–138`),
   not the receiver object. Identity is preserved only when `v1` is already a `UBooleanValue`.
2. **§3.0:** "`setNormalForm()` runs in every constructor" — the no-argument constructor
   `UBoolean()` (`uDataTypes/UBoolean.java:18–20`) does **not** call it. Behaviourally irrelevant
   (it sets `b = true, c = 0.0`, already canonical), but the invariant statement as written is
   false; the other five constructors do call it (`:24, :31, :39, :45, :53`).
3. **§3.1, third `eval` branch:** "else → `UndefinedValue.instance`. **Unreachable in practice**:
   `kind() == OPERATION`, so `ExpStdOp` already substituted `UndefinedValue`." The `OPERATION`-kind
   argument only rules out the *undefined* case. This branch is for a **defined** value that is
   neither `UBoolean` nor `Boolean`; its unreachability follows from `matches` requiring
   `isTypeOfUBoolean`, not from the operation kind.
4. **§3.4:** "so even `oclUndefined.value()` yields Undefined rather than `true`."
   `oclUndefined.value()` never gets that far: `VoidType` overrides only `isKindOfUBoolean`
   (`type/VoidType.java:58–60`) and inherits `isTypeOfUBoolean() == false`
   (`type/TypeImpl.java:205–207`), which `Op_uBoolean_value.matches` (line 184) demands — so the
   expression fails to type-check. The intended point (a `UBoolean`-typed expression that
   *evaluates* to undefined is short-circuited by `ExpStdOp`) is correct; the example is not.
5. **§5.5:** the `this == b` self-branch compares the inner `uDataTypes.UBoolean` objects, because
   `UBooleanValue.and/or/implies` call `uBoolean.and(uBooleanValue.uBoolean)`
   (`UBooleanValue.java:321–344`). Wrapper identity is sufficient but not necessary; two distinct
   `UBooleanValue` wrappers built from the same `UBoolean` (package-private
   `valueOf(UBoolean)`, `:74–86`) would also trip it. The stated conclusion is unaffected.

### R.5 One of the document's own `UNVERIFIABLE` gaps can be partly closed

§8 item 5 ("which `toString`/`toBoolean` overload wins for a `UBoolean` receiver"). Verified now,
in the **fork**:

- `Op_boolean_toString.matches` requires `params[0].isKindOfBoolean(INCLUDE_VOID)`
  (`StandardOperationsBoolean.java:256–258`), and **`UBooleanType` never overrides
  `isKindOfBoolean`** — the only definition on its chain (`UBooleanType` → `UncertainBooleanType`
  → … → `TypeImpl`) is `TypeImpl.java:233–236` → `false`. `Op_toString` (Enum) requires
  `isTypeOfEnum` (`StandardOperationsEnum.java:59`). So no core `toString` accepts a `UBoolean`,
  and `Op_uBoolean_toString` is reached.
- Likewise `Op_string_toBoolean` requires `isTypeOfString`, so `Op_uBoolean_toBoolean` is reached.
- The same `isKindOfBoolean == false` fact is what makes §4.3's ordering argument sound in the
  *other* direction too: the core `and`/`or`/`not`/`implies`/`xor` (all `isKindOfBoolean`-based)
  never accept `UBoolean` operands, while the UBoolean overloads **do** accept plain `Boolean`
  operands because `BooleanType.isKindOfUBoolean` → `true` (`type/BooleanType.java:50–52`).
  Order therefore matters in one direction only, exactly as §4.3 claims — but see R.1 for the
  SBoolean layer that §4.3 omits.

Still `UNVERIFIABLE` from my side: everything the document lists in §8 items 1–4, 6 and 7. I did
not build or run the fork either.

### R.6 Summary

| Item | Verdict |
|---|---|
| Operation count (14) | **correct** |
| Names, arities, argument types, result types (all 14) | **correct** — matches my independent derivation exactly |
| `equalsC` arity 3, `setConfidence`/`setUncertainty` name split, `value` stub returning `Boolean`, non-infix `xor`/`equivalent` | **correct** |
| `eval` branch coverage, short-circuits, eval-tree protocol, `or` NPE (§5.1), `equivalent` double evaluation (§5.2), NaN table (§5.7) | **correct** |
| §6 confidence algebra and the exact doubles | **correct** (re-derived in IEEE-754) |
| §3.14 / §4.3 "`equivalent` has no competitor / is the deliberate exception" | **WRONG** — see R.1 |
| §3.1 "a plain `Boolean` receiver … is taken by the core `Boolean` registry" | **WRONG** — see R.2 |
| §3.3 "`left` assigned before the range check, so … would NPE" | **WRONG mechanism** — see R.3 |
| 4 further minor inaccuracies | see R.4 |

Because R.1–R.3 are substantive (R.1 changes a port-ordering instruction), this refutation does
**not** fully agree with the document, even though its operation table itself is sound.
