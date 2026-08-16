# 20 — UString operation table (fork registry)

**Single source file under study**

```
/home/xoruser/msc-4/use-msc2026/.git/reference-repositories/uncertainty/USE-Uncertainty/src/main/org/tzi/use/uml/ocl/expr/operations/StandardOperationsUString.java
```

Read in full. `wc -l` reports **780** because the final line (`}`, line 781) is not newline-terminated:

```
$ wc -l .../StandardOperationsUString.java     ->  780
$ tail -c1 .../StandardOperationsUString.java | xxd -p   ->  7d   ("}")
```

so line numbers below run 1..781.

Supporting files read for the contract and for semantics (paths abbreviated to `FORK/` =
`/home/xoruser/msc-4/use-msc2026/.git/reference-repositories/uncertainty/USE-Uncertainty/src/main/org/tzi/use`,
`UDT/` = `/home/xoruser/msc-4/use-msc2026/.git/reference-repositories/uncertainty/uDataTypes/Libraries/Java/src/uDataTypes`,
`T750/` = `/home/xoruser/msc-4/use-msc2026/use-core/src/main/java/org/tzi/use`):

- `FORK/uml/ocl/expr/operations/OpGeneric.java` (registration contract)
- `FORK/uml/ocl/expr/operations/StandardOperationsNumber.java` (upstream-style conventions)
- `FORK/uml/ocl/value/UStringValue.java` (the wrapper every `eval` delegates to)
- `UDT/UString.java`, `UDT/UBoolean.java`, `UDT/UInteger.java` (the actual arithmetic)
- `FORK/uml/ocl/type/UStringType.java`, `StringType.java`, `TypeImpl.java`, `VoidType.java`, `URealType.java`, `UIntegerType.java`
- `FORK/uml/ocl/expr/ExpStdOp.java`, `EvalContext.java` (dispatch + undefined-argument contract)
- `T750/uml/ocl/expr/operations/OpGeneric.java`, `StandardOperationsString.java`, `T750/uml/ocl/type/Type.java`, `TypeFactory.java`

---

## 0. Operation count and how to reproduce it

**21 distinct `OpGeneric` subclasses** are defined and registered by this file.
**22 `registerOperation` calls** are made — `Op_uString_uConcat` is registered **twice**
(lines 19 and 21), so calls ≠ classes.

Reproduce the class count (run verbatim; produced `21`):

```bash
grep -cE '^final class Op_uString_[A-Za-z_]+ extends OpGeneric \{$' \
  /home/xoruser/msc-4/use-msc2026/.git/reference-repositories/uncertainty/USE-Uncertainty/src/main/org/tzi/use/uml/ocl/expr/operations/StandardOperationsUString.java
```

Corroborating counts (both run, outputs as shown):

```bash
# 22 -- registration call sites
grep -cE 'OpGeneric\.registerOperation\(new Op_uString_' .../StandardOperationsUString.java

# 21 -- distinct classes named in those call sites
grep -oE 'new Op_uString_[A-Za-z_]+' .../StandardOperationsUString.java | sort -u | wc -l
```

`grep -o 'new Op_uString_[A-Za-z_]*' ... | sort | uniq -c` shows `2 new Op_uString_uConcat`
and `1` for every other class — that is the whole discrepancy.

### Arity convention used throughout

**The receiver is argument 0 and IS counted.** `arity` below equals `args.length` /
`params.length` as seen by `matches` and `eval`. So `value` has arity 1 (receiver only),
`setValue` arity 2 (receiver + 1 explicit argument), `substring` arity 3.

### Call form

Every one of the 21 classes returns `false` from `isInfixOrPrefix()`. Consequence via
`OpGeneric.stringRep` (`FORK/.../OpGeneric.java:54-78`): all 21 render and parse as
**dot-calls** — including `+`, `<`, `<=`, `>`, `>=`, which upstream renders infix.
See §3.2; this is a deviation from the 7.5.0 convention, not a typo I am smoothing over.

### Undefined-argument contract

All 21 return `OPERATION` from `kind()`, and none override `isBooleanOperation()`.
`ExpStdOp.eval` (`FORK/.../ExpStdOp.java:285-322`; byte-identical to `T750/.../ExpStdOp.java:278-315`)
therefore short-circuits: **if any argument evaluates to undefined, the result is
`UndefinedValue.instance` and `eval` is never entered.** Only `ArithmeticException` is caught
around `eval` (`ExpStdOp.java:318-321`); every other `RuntimeException` escapes the evaluator.
That is load-bearing for `at`, `setConfidence` and `+` below.

---

## 1. Registration block

`StandardOperationsUString.registerTypeOperations(Multimap<String, OpGeneric>)` —
**lines 12–35**, called from `OpGeneric.registerOperations` at `FORK/.../OpGeneric.java:96`,
positioned after `StandardOperationsString` (line 89) and before the collections (line 100).
**That ordering is semantically load-bearing** — see §3.4.

| line | registers | OCL name |
|---|---|---|
| 13 | `Op_uString_value` | `value` |
| 14 | `Op_uString_confidence` | `confidence` |
| 15 | `Op_uString_setValue` | `setValue` |
| 16 | `Op_uString_setConfidence` | `setConfidence` |
| 17 | `Op_uString_at` | `at` |
| 18 | `Op_uString_character` | `character` |
| 19 | `Op_uString_uConcat` | `+` |
| 20 | `Op_uString_size` | `size` |
| 21 | `Op_uString_uConcat` **(duplicate)** | `+` |
| 22 | `Op_uString_indexOf` | `indexOf` |
| 23 | `Op_uString_substring` | `substring` |
| 24 | `Op_uString_toLowerCase` | `toLowerCase` |
| 25 | `Op_uString_toUpperCase` | `toUpperCase` |
| 26 | `Op_uString_toBoolean` | `toBoolean` |
| 27 | `Op_uString_toInteger` | `toInteger` |
| 28 | `Op_uString_toReal` | `toReal` |
| 29 | `Op_uString_toUBoolean` | `toUBoolean` |
| 30 | `Op_uString_toString` | `toString` |
| 31 | `Op_uString_less` | `<` |
| 32 | `Op_uString_less_or_equal` | `<=` |
| 33 | `Op_uString_greater` | `>` |
| 34 | `Op_uString_greater_or_equal` | `>=` |

The duplicate at line 21 is a copy/paste defect: line 21 sits between `size` (20) and
`indexOf` (22), i.e. exactly where a distinct third registration was presumably intended.
`ExpStdOp.opmap` is an `ArrayListMultimap` (`FORK/.../ExpStdOp.java:56,60`) which permits
duplicates; `create` returns on the **first** match (`ExpStdOp.java:129-135`), so the second
instance is dead but harmless.

**Never registered, though `UStringValue` implements them** — genuine gaps, not oversights I
should invent operations for:

| unwired wrapper method | site |
|---|---|
| `UStringValue.at(int) : StringValue` (1-based, proper `IndexOutOfBoundsException`) | `FORK/uml/ocl/value/UStringValue.java:137-139` |
| `UStringValue.uEqualsIgnoreCase(Value) : UBooleanValue` | `FORK/uml/ocl/value/UStringValue.java:180-183` |

And relative to 7.5.0's `String` family there is **no** UString `concat`, `split`,
`equalsIgnoreCase`, `characters` (plural), `toLower`, or `toUpper`.

`=`, `<>`, `equals`, `isDefined`, `isUndefined` on `UString` are **not** in this file — they
are handled generically by `Op_equal` in `FORK/.../StandardOperationsAny.java:34-69`, whose
`matches` returns `TypeFactory.mkUBoolean()` when either operand `instanceof UncertainType`.
Out of scope here, but must be ported alongside or `=` on UString silently yields `Boolean`.

---

## 2. The 21 operations

Notation: `c` = confidence component (`UString.sConf`, a double in `[0,1]`);
`|s|` = `String.length()`. "conf unchanged" = the receiver's `sConf` is copied verbatim.

All UBoolean results pass through `UBoolean`'s **normal form** (`UDT/UBoolean.java:11-13`):
`setNormalForm()` rewrites `(false, c)` to `(true, 1-c)`, and `getB()`/`getC()` normalise on
read (`UDT/UBoolean.java:59-67`). So every UBoolean reported below reads as `b=true` and
`c` = degree of belief in *true*. Then `UBooleanValue.valueOf(UBoolean)`
(`FORK/uml/ocl/value/UBooleanValue.java:74-86`) canonicalises `c==0` to the shared
`UBooleanValue.FALSE` and `c==1` to `UBooleanValue.TRUE`.

### 2.1 `value` — lines 45–73 (doc comment line 44)

- **arity 1** (receiver only — the receiver is argument 0 and is counted).
- **args** `(UString)`
- **result** `String` (`TypeFactory.mkString()`, line 65)
- **form** dot-call — `u.value()`
- **matches** `params.length == 1 && params[0].isTypeOfUString()` (line 64)
- **semantics** Projects out the value component: returns `new StringValue(wrapper.getString())`
  (lines 70-71). **The confidence component is discarded entirely.**
- **special cases** Undefined receiver → `UndefinedValue` (never enters `eval`).
  `UStringValue.valueOf` would also accept a `StringValue` (lifting it to `c=1`,
  `UStringValue.java:30-31`), but `matches` demands `isTypeOfUString` exactly, so that path is
  unreachable here. No exceptions, no NaN handling.

### 2.2 `confidence` — lines 77–105 (comment 76)

- **arity 1** (receiver only) — **args** `(UString)` — **result** `Real` (line 97) — **dot-call**
- **matches** `params.length == 1 && params[0].isTypeOfUString()` (line 96)
- **semantics** Projects out the uncertainty component: `new RealValue(wrapper.getsConf())`
  (line 103). This is the only way to observe `c` directly.
- **special cases** Undefined receiver → `UndefinedValue`. `c` is normally in `[0,1]`, but a
  **NaN confidence is observable here** — `+` on two empty strings produces `UString('', NaN)`
  (see §2.7), and `UString`'s constructor guard does not reject NaN (`UDT/UString.java:20`,
  NaN comparisons are false). `c==0` and `c==1` are ordinary values, no special handling.

### 2.3 `setValue` — lines 109–138 (comment 108)

- **arity 2** (receiver = arg 0, plus one `String`)
- **args** `(UString, String)` — `params[1].isTypeOfString()` is **strict**: a `UString` second
  argument does *not* match (line 128).
- **result** `UString` (line 129) — **dot-call** — `u.setValue('abc')`
- **semantics** Replaces the value component, **keeps the receiver's confidence**:
  `new UStringValue(string.value(), ustring.confidence())` (line 136).
- **special cases** Either arg undefined → `UndefinedValue`. The re-construction re-runs
  `UString`'s `[0,1]` guard (`UDT/UString.java:20`), but the confidence came from an existing
  `UString`, so it can only be out of range if it was NaN — and NaN passes the guard. No throw
  in practice. `c==0`/`c==1` unremarkable.

### 2.4 `setConfidence` — lines 142–177 (comment 141)

- **arity 2** (receiver + one number)
- **args** `(UString, Real | Integer)` — `params[1].isKindOfReal(VoidHandling.EXCLUDE_VOID)`
  (line 161). In the fork that predicate is `true` only for `RealType` and `IntegerType`
  (`FORK/uml/ocl/type/TypeImpl.java:215-217` default `false`; `IntegerType.java:53-55` `true`;
  `URealType.java` and `UIntegerType.java` do **not** override it). So a `UReal`/`UInteger`
  argument does **not** match, and the `isInteger()` branch at line 170 is exhaustive.
- **result** `UString` (line 162) — **dot-call**
- **semantics** Keeps the value component, replaces the confidence with the supplied number
  (lines 170-175). Integer arguments are widened to `double`.
- **special cases**
  - Either arg undefined → `UndefinedValue`.
  - **Confidence outside `[0,1]` throws.** `new UString(s, c)` raises
    `IllegalArgumentException("Invalid parameters")` (`UDT/UString.java:20`). `ExpStdOp.eval`
    catches only `ArithmeticException`, so this **escapes the evaluator as a crash, not as
    `Undefined`**. Verified against the oracle jar: `new UString("x", 1.5)` and
    `new UString("x", -0.1)` both throw.
  - **NaN is silently accepted**: `new UString("x", Double.NaN)` → `UString(x, NaN)` (verified).
    NaN < 0.0 and NaN > 1.0 are both false.
  - `c == 0` and `c == 1` are legal and stored as-is.

### 2.5 `at` — lines 180–209 (comment 179)

> The doc comment at line 179 says `at : UString x Integer -> String`. **The comment is wrong** —
> `matches` returns `TypeFactory.mkUString()` (line 200) and `eval` returns a `UStringValue`.

- **arity 2** (receiver + `Integer` index) — **args** `(UString, Integer)` — **result** `UString`
- **form** dot-call — `u.at(2)`
- **matches** `params.length == 2 && params[0].isTypeOfUString() && params[1].isTypeOfInteger()` (199)
- **semantics** `ustring.uAt(index.value())` (line 207) → `UString.uAt(idx) = uSubstring(idx, idx)`
  (`UDT/UString.java:152-154`) → the **1-based** single character at `idx`, with the receiver's
  **confidence carried through unchanged**. Verified: `UString("abc",0.9).uAt(1) = UString(a, 0.900)`,
  `.uAt(3) = UString(c, 0.900)`.
- **special cases** — all of these **throw and escape the evaluator**, in sharp contrast to
  upstream `Op_string_at` (`T750/.../StandardOperationsString.java`) which returns
  `UndefinedValue.instance` for both out-of-range directions:
  - `idx < 1` → `IllegalArgumentException("lower should be greater than 0")`
    (`UDT/UString.java:89`). Verified with `idx = 0`.
  - `idx > |s|` → `StringIndexOutOfBoundsException`. Verified: `UString("abc",0.9).uAt(4)` →
    `Range [3, 4) out of bounds for length 3`; `.uAt(5)` → `Range [4, 5) out of bounds for length 3`.
  - Undefined receiver or index → `UndefinedValue` (short-circuited, no throw).
  - `UStringValue.at(int)` — the *guarded* variant returning `StringValue` — is **not** called.

### 2.6 `character` — lines 213–241 (comment 212)

> Name is **singular** `"character"` (line 217). Upstream's String equivalent is
> `"characters"` (plural) — `T750/.../StandardOperationsString.java`, `Op_string_characters`.

- **arity 1** (receiver only) — **args** `(UString)`
- **result** `Sequence(UString)` — `TypeFactory.mkSequence(TypeFactory.mkUString())` (line 233)
- **form** dot-call — `u.character()` (note: **not** `->`, because `stringRep` only emits `->`
  when `args[0]` is a collection, `OpGeneric.java:69-72`; the receiver is a `UString`)
- **semantics** `ustring.uCharacters()` (line 239) → `UStringValue.uCharacters()`
  (`FORK/uml/ocl/value/UStringValue.java:165-173`) builds a `SequenceValue` with element type
  `mkUString()` from `UString.uCharacters()` (`UDT/UString.java:156-162`), which is
  `uAt(1) .. uAt(|s|)`. **Every element carries the receiver's confidence unchanged** — the
  confidence is replicated, not divided.
  Verified: `UString("abc",0.9).uCharacters()` → `[UString(a, 0.900), UString(b, 0.900), UString(c, 0.900)]`.
- **special cases** Empty string → **empty `Sequence(UString)`**, no exception (verified:
  `UString("",1.0).uCharacters()` → `[]`). Undefined receiver → `UndefinedValue`. `uAt` cannot
  go out of range here because the loop is `1..|s|`.

### 2.7 `+` (`Op_uString_uConcat`) — lines 245–276 (comment 244)

- **arity 2** (receiver = arg 0, plus one operand)
- **args** `(UString|String, UString|String)` with **at least one exactly `UString`** —
  `matches` (lines 263-268): both `isKindOfUString(EXCLUDE_VOID)` **and**
  `someOfThemIsUString = params[0].isTypeOfUString() || params[1].isTypeOfUString()`.
  `isKindOfUString` is `true` for `UStringType` and for `StringType`
  (`FORK/uml/ocl/type/StringType.java:50-52`) and `false` by default
  (`TypeImpl.java:370-372`); `VoidType` returns `h == INCLUDE_VOID`
  (`VoidType.java:133-135`) so `EXCLUDE_VOID` excludes `Void`.
- **result** `UString` (line 268)
- **form** **dot-call** (`isInfixOrPrefix()` returns `false`, line 259) — this is the sharpest
  deviation from upstream, where `Op_string_concatinfix.isInfixOrPrefix()` returns `true`.
  `stringRep` therefore renders `a.+(b)` instead of `(a + b)`.
- **semantics** `UStringValue.valueOf(args[0]).uConcat(args[1])` (lines 273-274).
  `valueOf` lifts a `StringValue` operand to confidence `1.0` (`UStringValue.java:30-31`);
  `assertKindOfUString` (`UStringValue.java:44-51`) throws
  `RuntimeException("A value kind of UString expected")` for anything else (unreachable given
  `matches`). The arithmetic (`UDT/UString.java:80-86`):
  - value = `a.string + b.string`
  - `dist = |a|·(1-c_a) + |b|·(1-c_b)` (each `confToDist()`, `UDT/UString.java:38-40`)
  - `c = max(1 - dist/(|a|+|b|), 0)` (`distToConf`, `UDT/UString.java:47-49`)

  i.e. **the result confidence is the character-length-weighted mean of the operand
  confidences, clamped below at 0.** Verified: `('abc',0.9) + ('de',0.5)` → `UString(abcde, 0.740)`
  (= (3·0.9 + 2·0.5)/5).
- **special cases**
  - **Both operands empty ⇒ NaN confidence.** `|a|+|b| == 0` makes `dist/size = 0.0/0.0 = NaN`,
    `1 - NaN = NaN`, and `Math.max(NaN, 0.0) = NaN`; the `UString` `[0,1]` guard does not reject
    NaN. **Verified against the oracle jar:** `UString("",1.0).uConcat(UString("",1.0))` →
    `UString(, NaN)`, `Double.isNaN(...getsConf()) == true`. This NaN then propagates through
    `confidence()`, comparisons, `setValue`, etc.
  - Confidence is **clamped at 0**, never negative. Verified `('ab',0.0) + ('cd',0.0)` →
    `UString(abcd, 0.000)`.
  - Confidence 1 on both sides → 1. `String + UString` lifts the `String` to `c = 1`, so a plain
    string literal never drags the mean down.
  - Either arg undefined → `UndefinedValue`.
  - **Latent `ArrayIndexOutOfBoundsException` in `matches`.** Line 264 dereferences `params[1]`
    *before* line 266 checks `params.length == 2`. Java evaluates the line-264 statement
    unconditionally, so a **1-argument** `+` reaches it and throws. Registration order under key
    `"+"` is `Op_number_add`, `Op_number_unaryplus`, `Op_string_concatinfix`,
    `Op_uString_uConcat`, `Op_uString_uConcat`; the first three all guard their length correctly
    (`ArithOperation.matches` checks `params.length == 2` first,
    `StandardOperationsNumber.java:63`; `Op_number_unaryplus` requires
    `params.length == 1 && isKindOfNumber`, `StandardOperationsNumber.java:571`;
    `Op_string_concatinfix` short-circuits on `params.length == 2`). So a unary `+` on a
    **non-numeric** operand (e.g. `+'abc'`) falls through to line 264 and crashes
    `ExpStdOp.create`, which catches nothing (`ExpStdOp.java:112-140`).
    **Whether the USE parser ever emits a unary `+` on a non-numeric operand is UNVERIFIED** —
    I did not trace the grammar. The code path is real regardless; the port must reorder the
    length check.
  - Registered twice (lines 19, 21); the second instance is unreachable.

### 2.8 `indexOf` — lines 280–308 (comment 279)

- **arity 2** (receiver + one `String`) — **args** `(UString, String)` (strict `isTypeOfString`,
  line 300) — **form** dot-call
- **result** *declared* `UString` (line 300) — **but `eval` returns an `IntegerValue`**
  (line 306 → `UStringValue.indexOf(StringValue)` →
  `IntegerValue.valueOf(...)`, `FORK/uml/ocl/value/UStringValue.java:201-203`).
  **This is a result-type defect: the static type is `UString`, the runtime value is
  `IntegerValue`.** The comment on line 279 also says `-> UString`.
- **semantics** Raw `java.lang.String.indexOf` (`UDT/UString.java:143-145`), **0-based**,
  `-1` when the needle is absent. **The confidence component is discarded entirely** — the
  result carries no uncertainty at all.
- **special cases**
  - Needle absent → `-1`. Verified: `UString("abc",0.9).indexOf("z")` → `-1`.
  - Empty needle → `0`. Verified: `.indexOf("")` → `0`.
  - Found → 0-based offset. Verified: `.indexOf("bc")` → `1`.
  - **Diverges from upstream `Op_string_indexOf`** (`T750/.../StandardOperationsString.java`,
    `Op_string_indexOf.eval`), which is **1-based** and special-cases empty receiver → `0`,
    empty needle → `1`, otherwise `self.indexOf(s) + 1`. Porting this verbatim leaves `String`
    and `UString` `indexOf` disagreeing by one and on the sentinel.
  - Either arg undefined → `UndefinedValue`. No exceptions.

### 2.9 `substring` — lines 312–357 (comment 311)

- **arity 3** (receiver = arg 0, plus two `Integer`s)
- **args** `(UString, Integer, Integer)` (line 331-333) — **result** `UString` — **dot-call**
- **semantics** `ustringA.uSubstring(first.value(), end.value())` (line 346) →
  `UDT/UString.java:88-94`: requires `lower >= 1`, then `string.substring(lower-1, upper)` —
  i.e. **1-based lower bound, inclusive; `upper` used directly as Java's exclusive end, so the
  upper bound is also inclusive in 1-based terms.** `substring(1,2)` on `"abc"` = `"ab"`
  (verified: `UString("abc",0.9).uSubstring(1,2)` → `UString(ab, 0.900)`).
  **Confidence is copied unchanged from the receiver**, regardless of how much of the string
  survives — contrast `+`, which length-weights. Taking one character out of a 100-character
  string keeps the full-string confidence.
- **special cases** — `eval` wraps the call in `try { } catch (Exception ex) { }` (lines 345-353):
  - **Any exception is swallowed and replaced by `new UStringValue("", 1)`** — the empty string
    with **confidence 1.0**, not `UndefinedValue`, not the receiver's confidence.
  - The inline comment at lines 349-350 claims this is "the same behavior as `String.substring`".
    **That claim is false** — `java.lang.String.substring` throws.
  - Concrete throw sites now masked: `lower < 1` →
    `IllegalArgumentException("lower should be greater than 0")` (verified via `uSubstring(0,2)`);
    `upper > |s|` or `upper < lower-1` → `StringIndexOutOfBoundsException` (verified via
    `uSubstring(2,9)` on `"abc"` → `Range [1, 9) out of bounds for length 3`).
  - Any argument undefined → `UndefinedValue` (short-circuit, before the try block).

### 2.10 `toLowerCase` — lines 361–389 (comment 360)

- **arity 1** — **args** `(UString)` — **result** `UString` (line 381) — **dot-call**
- **semantics** `ustringA.uToLowerCase()` (line 387) → `UDT/UString.java:139-141`:
  `new UString(string.toLowerCase(), sConf)` — **confidence carried through unchanged.**
  Verified: `UString("ABC",0.9).uToLowerCase()` → `UString(abc, 0.900)`.
- **special cases** No-argument `String.toLowerCase()` ⇒ **default-locale sensitive** (Turkish-I
  hazard), same as upstream. Undefined receiver → `UndefinedValue`. Empty string → empty string.
  No exceptions.
- **registration gap** Registered only under `"toLowerCase"` (line 24). Upstream registers
  `Op_string_toLower` under **both** `"toLower"` and `"toLowerCase"`
  (`T750/.../StandardOperationsString.java:35-39`). There is no UString `toLower` alias.

### 2.11 `toUpperCase` — lines 393–421 (comment 392)

Identical in shape to §2.10: **arity 1**, `(UString) -> UString` (line 413), dot-call,
`uToUpperCase()` (line 419) → `UDT/UString.java:135-137`, confidence unchanged. Verified:
`UString("abc",0.9).uToUpperCase()` → `UString(ABC, 0.900)`. Default-locale sensitive.
Registered only as `"toUpperCase"` (line 25) — no `"toUpper"` alias, unlike upstream.

### 2.12 `size` — lines 425–453 (comment 424)

- **arity 1** (receiver only) — **args** `(UString)`
- **result** **`UInteger`** (`TypeFactory.mkUInteger()`, line 445) — *not* `Integer`, unlike
  upstream `Op_string_size`.
- **form** dot-call
- **semantics** `ustringA.uSize()` (line 451) → `UStringValue.uSize()` wraps
  `UDT/UString.java:131-133`: `new UInteger(|s|, confToDist())` where
  `confToDist() = |s|·(1 - c)`.
  **The value component is the exact character count; the uncertainty component is an
  edit-distance budget expressed in characters, NOT a confidence.** This is the one place in the
  file where the uncertainty changes *representation*: `UInteger.u` is an absolute magnitude
  (`UDT/UInteger.java:20-22`, `this.u = Math.abs(u)`), not a probability in `[0,1]`.
- **special cases** Verified against the oracle jar:
  - `('abc', 0.9)` → `UInteger(x=3, u=0.29999999999999993)` — floating-point residue is real,
    `3·(1-0.9)` is not exactly `0.3`.
  - `('', 1.0)` → `UInteger(x=0, u=0.0)` — empty string is fine, no divide-by-zero (unlike `+`).
  - `('abc', 0.0)` → `UInteger(x=3, u=3.0)` — confidence 0 makes the uncertainty as large as the
    whole string.
  - `c == 1` → `u == 0` exactly.
  - Undefined receiver → `UndefinedValue`. No exceptions.

### 2.13 `toString` — lines 457–485 (comment 456, which reads `uToString : UString -> String`)

- **arity 1** — **args** `(UString)` — **dot-call**
- **result** *declared* **`UString`** (`TypeFactory.mkUString()`, line 477) — **but `eval`
  returns a `StringValue`** (line 483 → `UStringValue.uToString()`,
  `FORK/uml/ocl/value/UStringValue.java:157-159`, → `UDT/UString.java:168-170` = `getString()`).
  **Result-type defect**, the mirror of §2.8. The comment on line 456 states the *intended*
  `-> String`; `matches` contradicts it.
- **semantics** Returns the bare value component as a `String`. **The confidence is dropped and
  the `"UString(s, c)"` rendering is NOT produced** — contrast `UStringValue.toString(StringBuilder)`
  (`FORK/uml/ocl/value/UStringValue.java:67-71`), which does emit `UString('...', c)` and is not
  reachable through this operation.
  Functionally identical to `value` (§2.1) except for the bogus declared type.
  Verified: `UString("abc",0.9).uToString()` → `"abc"`.
- **special cases** Undefined receiver → `UndefinedValue`. No exceptions. Name collides with
  `Op_number_toString`, `Op_boolean_toString`, `Op_enum_toString`, `Op_uBoolean_toString`,
  `Op_sBoolean_toString` — all disambiguated by their own `matches` (see §3.4).

### 2.14 `toInteger` — lines 489–526 (comment 488)

- **arity 1** — **args** `(UString)` — **result** `Integer` (line 509) — **dot-call**
- **semantics** `ustringA.toInteger()` (line 518) → `IntegerValue.valueOf(Integer.parseInt(s))`
  (`FORK/uml/ocl/value/UStringValue.java:149-151`, `UDT/UString.java:176-178`).
  **The confidence is discarded entirely** — there is no `toUInteger` counterpart, so parsing a
  UString to a number loses all uncertainty. Verified: `UString("42",0.3).toInteger()` → `42`.
- **special cases**
  - **Unparseable input returns Java `null`, not `UndefinedValue`.** Lines 517-524 catch
    `Exception` and assign `result = null`, and line 524 returns it. `ExpStdOp.eval` stores that
    in `res` and returns it (`ExpStdOp.java:315-325`); `EvalContext.exit` only logs
    (`FORK/.../EvalContext.java:155-158`). **A `null` `Value` therefore escapes the evaluator**
    and will NPE in whatever consumes it. This is a defect, and the port must substitute
    `UndefinedValue.instance` (that is what upstream `Op_string_toInteger` does).
    Verified throw site: `UString("abc",0.9).toInteger()` →
    `NumberFormatException: For input string: "abc"`.
  - Undefined receiver → `UndefinedValue` (short-circuit, before the try).
  - `Integer.parseInt` accepts a leading `+`/`-` and rejects whitespace, `"1.0"`, and overflow.

### 2.15 `toReal` — lines 530–567 (comment 529)

- **arity 1** — **args** `(UString)` — **result** `Real` (line 550) — **dot-call**
- **semantics** `new RealValue(Double.parseDouble(s))`
  (`FORK/uml/ocl/value/UStringValue.java:153-155`, `UDT/UString.java:172-174`).
  **Confidence discarded entirely.**
- **special cases**
  - Unparseable → **Java `null`** (lines 558-563), same escaping-null defect as §2.14. Verified:
    `UString("abc",0.9).toReal()` → `NumberFormatException`.
  - **`"NaN"` and `"Infinity"` parse successfully.** Verified: `UString("NaN",0.3).toReal()` →
    `NaN`; `UString("Infinity",0.3).toReal()` → `Infinity`. `Double.parseDouble` also accepts
    `-Infinity`, hex-float literals, and `d`/`f` suffixes. So a `Real` with a non-finite value is
    reachable from OCL through this operation — no guard rejects it.
  - Undefined receiver → `UndefinedValue`.

### 2.16 `toBoolean` — lines 571–608 (comment 570)

- **arity 1** — **args** `(UString)` — **result** `Boolean` (line 591) — **dot-call**
- **semantics** `BooleanValue.get(Boolean.parseBoolean(s))`
  (`FORK/uml/ocl/value/UStringValue.java:145-147`, `UDT/UString.java:180-182`).
  **The confidence is discarded entirely** — this is the lossy sibling of `toUBoolean` (§2.17).
- **special cases**
  - `Boolean.parseBoolean` **never throws** (it is null-safe and case-insensitive), so the
    `catch` at lines 601-604 and its `result = null` are **dead code** — unlike §2.14/§2.15,
    no null can escape here.
  - **Every input other than a case-insensitive `"true"` yields `false`** — including `""`,
    `"1"`, `"yes"`, and arbitrary garbage. There is no "unparseable" outcome. Verified:
    `("abc")` → `false`; `("TrUe")` → `true`.
  - Undefined receiver → `UndefinedValue`.

### 2.17 `toUBoolean` — lines 612–649 (comment 611)

- **arity 1** — **args** `(UString)` — **result** `UBoolean` (`TypeFactory.mkUBoolean()`, line 632)
  — **dot-call**
- **semantics** The only conversion that **preserves** uncertainty.
  `ustringA.toUBoolean()` (line 641) → `UBooleanValue.valueOf(wrapper.uToUBoolean())`
  (`FORK/uml/ocl/value/UStringValue.java:161-163`). The algorithm (`UDT/UString.java:187-196`):
  1. `rT = uEqualsIgnoreCase(UString("TRUE", 1.0))`, `rF = uEqualsIgnoreCase(UString("FALSE", 1.0))`,
     where `uEqualsIgnoreCase(u) = uToUpperCase().uEquals(u.uToUpperCase())`
     (`UDT/UString.java:106-108`) and `uEquals` sets `b = (strings compare equal)` and
     `conf = c_this · c_other` (`UDT/UString.java:96-104`; note the Levenshtein-distance version
     is **commented out** at lines 97-99 — only exact equality × confidence product is live),
     then `UBoolean`'s normal form rewrites `(false, k)` to `(true, 1-k)`.
  2. If `rT.getC() >= 0.5` → `UBoolean(true, rT.getC())`;
     else if `rF.getC() >= 0.5` → `UBoolean(false, rF.getC())` (immediately normalised to
     `(true, 1 - rF.getC())`);
     else → `UBoolean(true, 0.5)`.
  3. `UBooleanValue.valueOf` canonicalises `c==0` → `UBooleanValue.FALSE`, `c==1` → `UBooleanValue.TRUE`.
- **special cases** — all verified against the oracle jar, reported as `(b, c)` after normal form
  (`c` = belief in *true*):

  | input | result | note |
  |---|---|---|
  | `("true", 0.9)` | `(true, 0.9)` | confidence carried straight through |
  | `("true", 1.0)` | `(true, 1.0)` | canonicalised to the shared `UBooleanValue.TRUE` |
  | `("true", 0.0)` | `(true, 0.0)` | canonicalised to the shared `UBooleanValue.FALSE` — confidence-0 `"true"` flips to certain-false |
  | `("false", 0.9)` | `(true, 0.09999999999999998)` | i.e. mostly-false, with float residue |
  | `("false", 0.4)` | `(true, 0.6)` | **a low-confidence `"false"` is read as probably TRUE** — the `rT` branch fires first because `rT.getC() = 1-0.4 = 0.6 >= 0.5` |
  | `("zzz", 0.9)` | `(true, 0.5)` | unparseable ⇒ maximal ignorance, never an error |
  | `("", 1.0)` | `(true, 0.5)` | empty string ⇒ maximal ignorance |

  Case-insensitive by construction (both sides uppercased). Never throws for a non-null string;
  the `catch`/`null` at lines 640-645 is only reachable if the string component is `null` (which
  NPEs inside `uToUpperCase` — verified that a `null` string component NPEs, via
  `new UString(null,1.0).uSize()`), and would then leak a `null` `Value` as in §2.14.
  Undefined receiver → `UndefinedValue`.

### 2.18–2.21 `<`, `<=`, `>`, `>=` — lines 653–682, 686–715, 719–748, 752–781

Four structurally identical classes. Comments at lines 652, 685, 718, 751; **the line-751
comment is a copy/paste error, reading `> :` where it should read `>= :`.**

| op | class | lines | wrapper call | predicate on `a.compareTo(b)` |
|---|---|---|---|---|
| `<` | `Op_uString_less` | 653–682 | `lt` (line 680) | `< 0` (`UDT/UString.java:198-203`) |
| `<=` | `Op_uString_less_or_equal` | 686–715 | `le` (line 713) | `<= 0` (`UDT/UString.java:213-218`) |
| `>` | `Op_uString_greater` | 719–748 | `gt` (line 746) | `> 0` (`UDT/UString.java:206-211`) |
| `>=` | `Op_uString_greater_or_equal` | 752–781 | `ge` (line 779) | `>= 0` (`UDT/UString.java:220-225`) |

- **arity 2** for each (receiver = arg 0, plus one operand).
- **args** `(UString|String, UString|String)` — `matches` (e.g. lines 671-675) requires
  `params.length == 2 && params[0].isKindOfUString(EXCLUDE_VOID) && params[1].isKindOfUString(EXCLUDE_VOID)`.
  **Unlike `+`, there is NO `someOfThemIsUString` guard**, so `String < String` also matches
  these. Length is checked first here, so the §2.7 AIOOBE hazard does not apply.
- **result** `UBoolean` (`TypeFactory.mkUBoolean()`).
- **form** **dot-call** — `isInfixOrPrefix()` returns `false` in all four. Upstream
  `Op_string_less`/`Op_number_less` etc. all return `true`. `stringRep` renders `a.<(b)`.
- **semantics** `UStringValue.valueOf(args[0]).lt(args[1])` etc.; `valueOf` lifts a `StringValue`
  receiver to `c=1`; `assertKindOfUString` throws `RuntimeException("A value kind of UString expected")`
  for any other operand type (unreachable given `matches`).
  - **Value component**: `java.lang.String.compareTo` — **case-sensitive, UTF-16 code-unit
    lexicographic order** (so `'Z' < 'a'` is true). Verified: `('Z',1.0) < ('a',1.0)` → `(true, 1.0)`.
  - **Uncertainty component**: `conf = c_a · c_b` — the plain **product** of the two confidences
    (`UString.calculateConf`, `UDT/UString.java:232-235`). Note `calculateConf_05`
    (`UDT/UString.java:241-244`), which would floor the result at 0.5, is present but **not used**.
  - Then the `UBoolean` normal form: a true comparison reports `(true, conf)`, a false comparison
    reports `(true, 1-conf)`.
- **special cases** — verified against the oracle jar:

  | expression | result `(b, c)` | note |
  |---|---|---|
  | `('abc',0.9) < ('de',0.5)` | `(true, 0.45)` | true, conf = 0.9·0.5 |
  | `('de',0.5) < ('abc',0.9)` | `(true, 0.55)` | false with conf 0.45, normalised to 1-0.45 |
  | `('abc',0.9) > ('de',0.5)` | `(true, 0.55)` | same normalisation |
  | `('abc',0.9) <= ('abc',0.9)` | `(true, 0.81)` | equal ⇒ true, conf = 0.9² |
  | `('abc',0.9) >= ('abc',0.9)` | `(true, 0.81)` | |
  | `('abc',1.0) < ('abc',1.0)` | `(true, 0.0)` | false with conf 1 ⇒ `(true, 0)` ⇒ canonicalised to `UBooleanValue.FALSE` |
  | `('abc',0.0) < ('de',1.0)` | `(true, 0.0)` | **confidence 0 ⇒ a TRUE comparison becomes certain-FALSE** |
  | `('de',0.0) < ('abc',1.0)` | `(true, 1.0)` | **confidence 0 ⇒ a FALSE comparison becomes certain-TRUE** |

  The last two are the sign flip the library's own Spanish comment warns about
  (`UDT/UString.java:227-231`: *"Aparecen problemas para confianzas inferiores a 0.7 … 'conmuta'
  de true a false y viceversa"*). Confidence exactly 0 or 1 hits the `UBooleanValue.FALSE`/`TRUE`
  canonicalisation (`FORK/uml/ocl/value/UBooleanValue.java:74-86`); note
  `UBooleanValue.FALSE` is itself `new UBooleanValue(true, 0)` (`UBooleanValue.java:31`).
  - Either argument undefined → `UndefinedValue` (`kind() == OPERATION`). These do **not**
    degrade to `false` — they are not `PREDICATE`s, and `isBooleanOperation()` is not overridden,
    so `BooleanOperation.evalWithArgs` is not used.
  - No exceptions, no NaN handling — but a NaN confidence from §2.7 would propagate: `NaN · c = NaN`,
    and `new UBoolean(b, NaN)` passes the `[0,1]` guard (`UDT/UBoolean.java:36`). **Not empirically
    tested; inferred from the same guard that §2.4 verified.**

---

## 3. Cross-check against the 7.5.0 registry (`T750/uml/ocl/expr/operations/`)

### 3.1 `OpGeneric` contract members — **no signature differs**

```
$ diff -u T750/.../OpGeneric.java FORK/.../OpGeneric.java
```

The **only** semantic difference is inside `registerOperations`: the fork inserts

```java
// Uncertainty Types
StandardOperationsUReal.registerTypeOperations(opmap);
StandardOperationsUBoolean.registerTypeOperations(opmap);
StandardOperationsUInteger.registerTypeOperations(opmap);
StandardOperationsUString.registerTypeOperations(opmap);
StandardOperationsSBoolean.registerTypeOperations(opmap);
```

between `StandardOperationsBoolean` and the collection registries
(fork lines 92-97 vs 7.5.0 lines 91-92). Everything else in the diff is CRLF-vs-LF noise on
every line.

Verified member-by-member as **identical** in both files:

| member | fork line | 7.5.0 line |
|---|---|---|
| `public static final int OPERATION = 0` | 34 | 34 |
| `public static final int SPECIAL = 3` | 36 | 36 |
| `public abstract String name()` | 38 | 38 |
| `public boolean isBooleanOperation()` (default `false`) | 40-42 | 40-42 |
| `public abstract int kind()` | 44 | 44 |
| `public abstract boolean isInfixOrPrefix()` | 46 | 46 |
| `public abstract Type matches(Type params[])` | 48 | 48 |
| `public String checkWarningUnrelatedTypes(Expression args[])` | 50 | 50 |
| `public abstract Value eval(EvalContext ctx, Value args[], Type resultType)` | 52 | 52 |
| `public String stringRep(Expression args[], String atPre)` | 54-78 | 54-78 |
| `registerOperation(OpGeneric, Multimap)` | 112-114 | 105-107 |
| `registerOperation(String, OpGeneric, Multimap)` | 122-124 | 115-117 |

**So the port does not have to adapt any `OpGeneric` member signature.** No fork operation in
this file overrides `isBooleanOperation` or `checkWarningUnrelatedTypes`; all 21 override exactly
`name`, `kind`, `isInfixOrPrefix`, `matches`, `eval`.

`ExpStdOp.eval`'s undefined-argument block is likewise identical
(`FORK/.../ExpStdOp.java:285-322` vs `T750/.../ExpStdOp.java:278-315`), so the strict-evaluation
semantics assumed above hold unchanged in 7.5.0.

### 3.2 Convention deviations from an upstream-style registry

Measured against `T750/.../StandardOperationsString.java` and
`FORK/.../StandardOperationsNumber.java`:

| convention | upstream 7.5.0 | this file |
|---|---|---|
| operator ops are infix | `Op_string_concatinfix.isInfixOrPrefix()` → `true`; `Op_string_less` → `true`; `Op_number_*` → `true` | **all 21 return `false`**, including `+ < <= > >=` |
| case-conversion aliases | `Op_string_toLower` registered under `"toLower"` **and** `"toLowerCase"` (7.5.0 lines 35-39); same for upper | only `"toLowerCase"` / `"toUpperCase"` |
| `characters` naming | `Op_string_characters` → `"characters"` | `"character"` (singular) |
| `indexOf` indexing | 1-based, empty-receiver → 0, empty-needle → 1 | raw 0-based Java, `-1` sentinel |
| out-of-range access | `Op_string_at` returns `UndefinedValue.instance` | `at` **throws**; `substring` returns `UString('',1.0)` |
| unparseable conversion | `Op_string_toInteger` returns `UndefinedValue.instance` | returns Java **`null`** |
| `matches` guards length first | yes everywhere (`ArithOperation.matches`, `Op_string_concatinfix`) | `Op_uString_uConcat` dereferences `params[1]` first (line 264) |
| declared type == runtime type | yes | **violated by `indexOf` (§2.8) and `toString` (§2.13)** |
| one registration per class | yes | `Op_uString_uConcat` registered twice |
| shared abstract base for a family | `ArithOperation` (`StandardOperationsNumber.java:56`) factors the shared `matches` | the four comparison classes duplicate `matches` verbatim four times |
| unused imports | none | `org.tzi.use.uml.ocl.expr.ExpInvalidException` (line 5) is never referenced |

### 3.3 What 7.5.0 does **not** provide (the port surface for this file)

Absent from 7.5.0 and required by `StandardOperationsUString.java`:

| symbol used | 7.5.0 status |
|---|---|
| `TypeFactory.mkUString()` (lines 129, 162, 200, 233, 268, 300, 333, 381, 413, 477) | **missing** — `T750/uml/ocl/type/TypeFactory.java` has `mk*` only for Integer, UnlimitedNatural, Real, String, Boolean, Enum, Collection, Set, Sequence, Bag, OrderedSet, MessageType, OclAny, VoidType, Tuple, SimpleType |
| `TypeFactory.mkUInteger()` (line 445) | **missing** |
| `TypeFactory.mkUBoolean()` (lines 632, 674, 707, 740, 773) | **missing** |
| `Type.isTypeOfUString()` (lines 64, 96, 128, 161, 199, 232, 264, 299, 331, 380, 412, 444, 476, 508, 549, 590, 631) | **missing** — `T750/uml/ocl/type/Type.java:82-152` has no UString/UReal/UInteger/UBoolean/SBoolean predicates |
| `Type.isKindOfUString(VoidHandling)` (lines 266-267, 672-673, 705-706, 738-739, 771-772) | **missing** |
| `org.tzi.use.uml.ocl.value.UStringValue` | **missing** (whole class) |
| `org.tzi.use.uml.ocl.value.UIntegerValue`, `UBooleanValue`, `UncertainValue`, `UncertainBooleanValue` | **missing** |
| `org.tzi.use.uml.ocl.type.UStringType`, `UIntegerType`, `UBooleanType`, `UncertainType` | **missing** |
| `package uDataTypes` (`UString`, `UInteger`, `UBoolean`) | **missing** — supplied by `lib/atenearesearchgroup.uncertainty.jar` |

Present and usable **unchanged** in 7.5.0: `EvalContext`, `Type`, `Type.VoidHandling`,
`TypeFactory.mkString/mkReal/mkInteger/mkBoolean/mkSequence`, `StringValue`, `RealValue`,
`IntegerValue`, `BooleanValue`, `SequenceValue(Type, Value[])`
(`T750/uml/ocl/value/SequenceValue.java:56`), `Value.isInteger()`
(`T750/uml/ocl/value/Value.java:56`), `com.google.common.collect.Multimap`.

Two further edits to **existing** 7.5.0 files that this file's `matches` depends on:

1. `StringType` must answer `isKindOfUString(h) → true` and `conformsTo(UStringType) → true`
   and include `UString` in `allSupertypes()` — the fork does this at
   `FORK/uml/ocl/type/StringType.java:50-52, 57-61, 63-71`. Without it, `'a' + uStr`,
   `'a' < uStr` etc. stop matching.
2. `OpGeneric.registerOperations` must gain the `StandardOperationsUString` call **after**
   `StandardOperationsString` (see §3.4).

One member 7.5.0 has that the fork's `Type` lacks: `isTypeOfDataType()` /
`isKindOfDataType(VoidHandling)` (`T750/uml/ocl/type/Type.java:136-138`). Not referenced by this
file, and a ported `UStringType` inherits `false` for both from
`T750/uml/ocl/type/TypeImpl.java:288-295` — so no action is required, but the fork's
`UStringType` will not compile against the 7.5.0 `Type` interface without that inheritance chain
(`UStringType extends UncertainType extends BasicType`, `FORK/uml/ocl/type/UncertainType.java:11`).

### 3.4 Name collisions and why registration order is load-bearing

`ExpStdOp.opmap` is an `ArrayListMultimap` and `ExpStdOp.create` returns the **first** candidate
whose `matches` is non-null, in insertion order (`FORK/.../ExpStdOp.java:56-60, 129-135`).
`T750/.../StandardOperationsString.java` is **byte-identical to the fork's** (verified:
`diff -q <(tr -d '\r' < T750/...) <(tr -d '\r' < FORK/...)` reports no difference), so the fork
adds a parallel UString family rather than modifying the String family.

Collisions this file introduces, and their resolution:

| name | competing 7.5.0/fork ops | resolved by |
|---|---|---|
| `value`, `confidence`, `setValue`, `setConfidence` | `StandardOperationsUReal/UInteger/UBoolean` register the same names | each `matches` demands its own `isTypeOf*` |
| `size` | `Op_string_size`, `Op_collection_size` | `isTypeOfUString` |
| `at` | `Op_string_at`, `Op_sequence_at`, `Op_orderedSet_at` | `isTypeOfUString` |
| `indexOf` | `Op_string_indexOf`, `Op_sequence_indexOf`, `Op_orderedSet_indexOf` | `isTypeOfUString` |
| `substring`, `toInteger`, `toReal`, `toBoolean`, `toLowerCase`, `toUpperCase` | `Op_string_*` | `isTypeOfUString` |
| `toString` | `Op_number_toString`, `Op_boolean_toString`, `Op_enum_toString`, `Op_uBoolean_toString`, `Op_sBoolean_toString` | `isTypeOfUString` |
| `+` | `Op_number_add`, `Op_number_unaryplus`, `Op_string_concatinfix` | the `someOfThemIsUString` guard (line 264) keeps `String+String` on `Op_string_concatinfix` |
| **`<`, `<=`, `>`, `>=`** | `Op_number_*`, `Op_string_less/greater/lessequal/greaterequal` | **ONLY by registration order** — the UString versions accept `String × String` because they use `isKindOfUString`, which `StringType` answers `true`. If `StandardOperationsUString` were registered **before** `StandardOperationsString`, every `'a' < 'b'` in every existing model would silently change result type from `Boolean` to `UBoolean`. |

That last row is the single most dangerous porting constraint in this file.

---

## 4. Defect ledger for the port

Ordered by risk. Each entry names the exact site.

1. **`<`,`<=`,`>`,`>=` over-match plain `String`** (lines 672-673, 705-706, 738-739, 771-772) —
   only registration order prevents silent retyping of every existing string comparison.
   Add a `someOfThemIsUString` guard as `+` has.
2. **`Op_uString_uConcat.matches` reads `params[1]` before the length check** (line 264 vs 266) —
   `ArrayIndexOutOfBoundsException` on a 1-argument `+` that earlier candidates decline.
   Parser reachability **UNVERIFIED**.
3. **`toInteger`/`toReal`/`toUBoolean` return Java `null` on failure** (lines 521, 562, 644) —
   a `null` `Value` escapes `ExpStdOp.eval` (`EvalContext.exit` only logs). Must become
   `UndefinedValue.instance`.
4. **`indexOf` declares `UString`, returns `IntegerValue`** (line 300 vs 306).
5. **`toString` declares `UString`, returns `StringValue`** (line 477 vs 483).
6. **`at` throws instead of returning `Undefined`** on `idx < 1` and `idx > |s|` (lines 204-208),
   diverging from `Op_string_at`. Neither exception is caught by `ExpStdOp`.
7. **`setConfidence` throws `IllegalArgumentException` for `c ∉ [0,1]`** (line 175 →
   `UDT/UString.java:20`) and escapes the evaluator; and **silently accepts NaN**.
8. **`+` on two empty strings yields `c = NaN`** (0/0 in `distToConf`, `UDT/UString.java:48`),
   which then leaks through `confidence`, comparisons and `setValue` unguarded.
9. **`substring` swallows all exceptions into `UString('', 1.0)`** (lines 348-352) — confidence
   **1.0**, not the receiver's, and the accompanying comment's claim about `String.substring` is
   false.
10. **`Op_uString_uConcat` registered twice** (lines 19, 21); the intended third registration at
    line 21 is missing.
11. **`indexOf` is 0-based** while upstream `String::indexOf` is 1-based — an off-by-one between
    the two families.
12. **Naming/aliasing drift**: `character` (singular) vs `characters`; no `toLower`/`toUpper`
    aliases; all operator ops non-infix.
13. **`at` comment (line 179) says `-> String`** but returns UString; **`>=` comment (line 751)
    says `>`**.
14. **Unused import** `ExpInvalidException` (line 5).
15. **Unwired wrapper methods**: `UStringValue.at(int)→StringValue` and
    `UStringValue.uEqualsIgnoreCase` have no operation.

---

## 5. Evidence method

Semantics claims marked "verified" were produced by running the **live oracle jar**
`.../USE-Uncertainty/lib/atenearesearchgroup.uncertainty.jar` directly (the jar whose classes are
in package `uDataTypes/`, per the established fact), with a standalone probe compiled into the
scratchpad — **nothing in the repo was written to, and Maven was never invoked**:

```bash
JAR=/home/xoruser/msc-4/use-msc2026/.git/reference-repositories/uncertainty/USE-Uncertainty/lib/atenearesearchgroup.uncertainty.jar
javac -cp "$JAR" -d <scratchpad> Probe.java && java -cp "$JAR:<scratchpad>" Probe
```

Probe source and full transcript:
`/tmp/claude-1000/-home-xoruser-msc-4/5a883e17-9055-4019-8f36-a743005556fa/scratchpad/ustringprobe/Probe.java`

The jar's `uDataTypes.UString` API was confirmed to match `UDT/UString.java` method-for-method:

```bash
javap -classpath "$JAR" uDataTypes.UString
```

lists exactly the public members of the source file, one for one (`uConcat`, `uSubstring`, `uEquals`,
`uEqualsIgnoreCase`, `uSize`, `uToUpperCase`, `uToLowerCase`, `indexOf`, `at`, `uAt`,
`uCharacters`, `uToString`, `toReal`, `toInteger`, `toBoolean`, `uToUBoolean`, `lt`, `gt`, `le`,
`ge`, `getString`, `getsConf`, `confToDist`/`distToConf`/`levenshteinDist`), and
`javap -c` on `uSize` and `uToUBoolean` shows bytecode matching the source line-for-line
(`UInteger.<init>(ID)` from `length()` and `confToDist()`; the `0.5d` thresholds and the
`TRUE`/`FALSE` string constants). **The checked-out `uDataTypes` source is therefore a faithful
oracle for this type.**

### Explicitly UNVERIFIED

- Whether the USE parser can emit a unary `+` applied to a non-numeric operand (needed to make
  defect #2 reachable end-to-end). I did not trace the ANTLR grammar.
- The NaN-confidence propagation through `<`/`<=`/`>`/`>=` (§2.18-2.21 final bullet) is inferred
  from the `UBoolean` `[0,1]` guard, not measured.
- The fork contains **no test** for these operations: `grep -rln "UString\|uString"` over
  `.../USE-Uncertainty/src/test/org/tzi/use` matches only `uml/ocl/type/TypeTest.java`. There is
  no behavioural regression suite to port alongside.
- I did not consult `origin/main`.

---

## Independent refutation

Independent second pass. I read
`StandardOperationsUString.java` (781 lines) end to end and built my own registry table
*before* opening anything above, then re-ran every checkable claim. Sources consulted:
`FORK/uml/ocl/value/UStringValue.java`, `UDT/UString.java`, `UDT/UBoolean.java`,
`FORK/uml/ocl/expr/ExpStdOp.java`, `FORK/uml/ocl/expr/operations/OpGeneric.java` and all
sibling `StandardOperations*.java` in both the fork and 7.5.0,
`FORK/uml/ocl/type/{StringType,UStringType,IntegerType,UnlimitedNaturalType,TypeImpl}.java`,
plus a fresh probe against the live oracle jar. No file outside this directory was written.

### R.0 The count is right

**21 distinct operation classes, 22 `registerOperation` calls** — I derived exactly the same
list, independently, with the same duplicate (`Op_uString_uConcat` at lines 19 and 21).
Proof (run verbatim; outputs `21`, `22`, `21`, then `2 new Op_uString_uConcat`):

```bash
F=/home/xoruser/msc-4/use-msc2026/.git/reference-repositories/uncertainty/USE-Uncertainty/src/main/org/tzi/use/uml/ocl/expr/operations/StandardOperationsUString.java
grep -cE '^final class Op_uString_[A-Za-z_]+ extends OpGeneric \{$' "$F"
grep -cE 'OpGeneric\.registerOperation\(new Op_uString_' "$F"
grep -oE 'new Op_uString_[A-Za-z_]+' "$F" | sort -u | wc -l
grep -oE 'new Op_uString_[A-Za-z_]+' "$F" | sort | uniq -c | sort -rn | head -1
```

The 21 OCL names, from `grep -n 'return "' "$F"` (lines 49,81,113,146,184,217,249,284,316,365,
397,429,461,493,534,575,616,657,690,723,756), are exactly:
`value, confidence, setValue, setConfidence, at, character, +, indexOf, substring, toLowerCase,
toUpperCase, size, toString, toInteger, toReal, toBoolean, toUBoolean, <, <=, >, >=`.

**No operation is missing from §2 and none is invented.** Every arity, argument type and
declared result type in §2 matches what I read. `grep -c 'return OPERATION;'` = **21** and
`grep -A2 'public boolean isInfixOrPrefix' | grep -c 'return false;'` = **21**, confirming §0.
Their line-number citations for `mkUString` (129,162,200,233,268,300,333,381,413,477),
`mkUBoolean` (632,674,707,740,773), `isTypeOfUString` (17 sites) and `isKindOfUString`
(266,267,672,673,705,706,738,739,771,772) all reproduce exactly under `grep -n`.

### R.1 Semantics re-verified against the oracle jar — all reproduce

I wrote my own probe (not theirs) and ran it against
`.../USE-Uncertainty/lib/atenearesearchgroup.uncertainty.jar`:

```bash
SP=<scratchpad>/refute-ustring
JAR=/home/xoruser/msc-4/use-msc2026/.git/reference-repositories/uncertainty/USE-Uncertainty/lib/atenearesearchgroup.uncertainty.jar
javac -cp "$JAR" -d $SP $SP/R.java && java -cp "$JAR:$SP" R
```

Every numeric claim in §2 reproduced **character for character**, including the awkward ones:
`abc(0.9)+de(0.5)` → `UString(abcde, 0.740)`; `""+""` → `UString(, NaN)` with
`Double.isNaN == true`; `uSize("abc",0.9)` → `UInteger(3, u=0.29999999999999993)`;
`uAt(0)` → `IllegalArgumentException: lower should be greater than 0`;
`uAt(4)` → `StringIndexOutOfBoundsException: Range [3, 4) out of bounds for length 3`;
`uSubstring(2,9)` → `Range [1, 9) out of bounds for length 3`; `indexOf("")` → `0`,
`indexOf("bc")` → `1`, `indexOf("z")` → `-1`; `toReal("NaN")` → `NaN`,
`toReal("Infinity")` → `Infinity`; the whole `uToUBoolean` table including
`("false",0.9)` → `(true, 0.09999999999999998)` and `("false",0.4)` → `(true, 0.6)`;
the whole comparison table including `('de',0.0) < ('abc',1.0)` → `(true, 1.0)`;
`new UString("x",1.5)` / `("x",-0.1)` → `IllegalArgumentException: Invalid parameters`;
`new UString("x",NaN)` → accepted. **I found no fabricated result.**

Structural claims re-verified from source, all correct:
`ExpStdOp.eval` short-circuits `OPERATION` on undefined args and catches only
`ArithmeticException`, then returns `res` unguarded — so a `null` from
`toInteger`/`toReal`/`toUBoolean` really does escape (§2.14, §4.3);
`ExpStdOp.create` returns the **first** matching candidate and catches nothing;
`Op_string_at` really does return `UndefinedValue.instance` on both out-of-range directions
(`T750/.../StandardOperationsString.java:499-500`); `Op_string_indexOf` really is 1-based with
the `self.length()==0 → 0` / `s.length()==0 → 1` special cases (`:438-441`);
`Op_string_toUpper`/`Op_string_toLower` really are double-registered under the short and long
names (`:29-38`); `Op_string_size` really returns `mkInteger()`;
`diff` of `OpGeneric.java` (CR-stripped) is exactly `90a91,97`, the five uncertainty
registrations and nothing else; `StringType.isKindOfUString` → `true` (`:50-52`);
`isKindOfReal` is overridden only in `RealType:50`, `IntegerType:53`, `VoidType:48`
(`grep -n isKindOfReal FORK/uml/ocl/type/*.java`), so §2.4's exhaustiveness argument holds.

I also closed one of their own **UNVERIFIED** items: `new UBoolean(true, Double.NaN)` is
accepted by the `[0,1]` guard and prints `UBoolean(true,   NaN)` (probe output). The NaN
propagation through `<`/`<=`/`>`/`>=` in §2.18-2.21 is therefore **measured, not inferred**.

I additionally probed a hazard §2.4 does not discuss and found it is **not** one:
`UnlimitedNaturalType extends BasicType` and does **not** override `isKindOfReal`
(`FORK/uml/ocl/type/UnlimitedNaturalType.java:32`), so an `UnlimitedNatural` second argument
cannot reach `setConfidence` and cause a `ClassCastException` on the `(RealValue)` cast at
line 173. Their claim that the `isInteger()` branch is exhaustive survives.

### R.2 Discrepancies found

All of them are in §2.13/§3.2/§3.4 — the cross-check apparatus, not the operation table.

1. **`Op_enum_toString` and `Op_sBoolean_toString` do not exist** (§2.13 last bullet, and the
   `toString` row of §3.4). The *collision* is real — six classes answer `"toString"` — but two
   of the five names given are fabricated identifiers that grep to nothing:
   - the Enum one is `final class Op_toString`, `FORK/.../StandardOperationsEnum.java:46`
     (registered at `:41`), with no `enum` in the name;
   - the SBoolean one is an **anonymous `OpGeneric`** held by the enum constant `TO_STRING`,
     `FORK/.../StandardOperationsSBoolean.java:373-380` — that file contains **zero**
     `final class Op_` declarations (`grep -c 'final class Op_' StandardOperationsSBoolean.java`
     → `0`); it uses a completely different registry idiom from every other file in the package.
   `Op_number_toString` (`StandardOperationsNumber.java:1231`), `Op_boolean_toString`
   (`StandardOperationsBoolean.java:244`) and `Op_uBoolean_toString`
   (`StandardOperationsUBoolean.java:81`) are correct as written. Reproduce:
   `grep -rn 'return "toString";' FORK/uml/ocl/expr/operations/*.java`.

2. **§3.4 row 1 is factually wrong for `confidence` and `setConfidence`.** It claims
   "`StandardOperationsUReal/UInteger/UBoolean` register the same names" for the group
   *`value`, `confidence`, `setValue`, `setConfidence`*. `UReal` and `UInteger` register
   **`uncertainty` / `setUncertainty`**, not `confidence` / `setConfidence`
   (`StandardOperationsUReal.java:22-23`, `StandardOperationsUInteger.java:14-15`; full name
   lists via `grep -oE 'return "[^"]+";'` contain `uncertainty`/`setUncertainty` and no
   `confidence`). Only `StandardOperationsUBoolean` shares those two names with UString
   (`:236`, `:266`). Reproduce:
   `grep -rn 'return "confidence";\|return "setConfidence";' FORK/uml/ocl/expr/operations/*.java`
   → four hits, all UString or UBoolean. This matters for the port: the naming convention across
   the uncertain types is **inconsistent** (`confidence` for UString/UBoolean vs `uncertainty`
   for UReal/UInteger), which the table as written hides.

3. **§3.4's collision inventory for `toInteger` / `toReal` is incomplete.** It lists only
   `Op_string_*`. Also registered under those names, and registered **before**
   `StandardOperationsUString` (`OpGeneric.java:93-96`):
   `Op_ureal_toInteger` (`StandardOperationsUReal.java:31`), `Op_ureal_toReal` (`:30`),
   `Op_uInteger_toReal` (`StandardOperationsUInteger.java:19`), and — the one a reader will
   certainly skip — the **alias registration** `OpGeneric.registerOperation("toInteger",
   new Op_uInteger_value(), opmap)` at `StandardOperationsUInteger.java:17`, which registers a
   class named `..._value` under the name `toInteger`. Also unlisted: `toUBoolean` collides with
   the SBoolean `TO_U_BOOLEAN` entry. None of these change the resolution (each `matches`
   demands its own `isTypeOf*`), so the porting conclusion stands — but the inventory should not
   be read as exhaustive.

4. **"byte-identical" is the wrong word** (§3.4). `T750/.../StandardOperationsString.java` and
   the fork's are identical only **after CR stripping** — the fork's copy is CRLF. Their own
   command shows the `tr -d '\r'`, so the evidence is sound; only the adjective overstates it.
   `diff -q` on the raw files reports a difference; `diff -q <(tr -d '\r' < A) <(tr -d '\r' < B)`
   reports none.

5. **Two off-by-N source citations.** §3.2 cites `ArithOperation` at
   `StandardOperationsNumber.java:56`; the declaration is line **54**
   (`grep -n 'class ArithOperation'`). §2.7 cites the length guard at `:63`; `if (params.length
   == 2)` is line **62**. Cosmetic, but both are load-bearing citations for the §2.7/§4.2 AIOOBE
   argument, which I otherwise confirm: line 264 dereferences `params[1]` before line 266 checks
   the length, and `Op_number_add` / `Op_number_unaryplus` (`:571`) / `Op_string_concatinfix`
   (`T750 String:126-128`) all guard length first, so a 1-argument non-numeric `+` does fall
   through to line 264. I did not trace the grammar either — **their UNVERIFIED stands**.

6. **§2.17 step 1 omits an identity shortcut.** `UString.uEquals` is
   `double conf = (this==u) ? 1.0 : calculateConf(u);` (`UDT/UString.java:102`), not
   unconditionally `c_this · c_other` as described. Unreachable from `uToUBoolean` (which always
   builds a fresh `new UString("TRUE", 1.0)`), so no reported value changes — but a port that
   reimplements `uEquals` from that description would drop a reference-equality special case.

### R.3 What I could not refute

- The 21-row operation table (§2): names, arities, argument types, declared result types,
  wrapper delegations, and every "verified" number. Independently derived and independently
  measured; **no disagreement**.
- The two declared-vs-runtime type defects (`indexOf` → `IntegerValue`, `toString` →
  `StringValue`). Confirmed at `UStringValue.java:201-203` and `:157-159`.
- The duplicate `+` registration and its harmlessness under `ArrayListMultimap` + first-match
  `create`.
- The `<`,`<=`,`>`,`>=` over-match of plain `String` and the registration-order dependency
  (§4.1). Confirmed: `matches` uses `isKindOfUString`, `StringType` answers `true`, and
  `StandardOperationsString` is registered at `OpGeneric.java:89` vs UString at `:96`.
- The defect ledger §4 items 1-15, each of which I re-checked at the cited site.

### R.4 Verdict

The operation table is **correct and complete**. The discrepancies above are confined to the
comparative sections: two invented class identifiers, one false claim about which sibling
registries share `confidence`/`setConfidence`, one incomplete collision inventory, one
overstated "byte-identical", and two off-by-N line citations.

