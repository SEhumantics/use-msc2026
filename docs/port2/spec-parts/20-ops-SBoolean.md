# 20 — SBoolean operation table (fork registry)

**Subject file**
`/home/xoruser/msc-4/use-msc2026/.git/reference-repositories/uncertainty/USE-Uncertainty/src/main/org/tzi/use/uml/ocl/expr/operations/StandardOperationsSBoolean.java`
(1502 lines, read in full).

**Supporting files read**

| Role | Path | Lines used |
|---|---|---|
| Registration contract (fork) | `.../USE-Uncertainty/src/main/org/tzi/use/uml/ocl/expr/operations/OpGeneric.java` | 1–125 (full) |
| Registration contract (7.5.0) | `/home/xoruser/msc-4/use-msc2026/use-core/src/main/java/org/tzi/use/uml/ocl/expr/operations/OpGeneric.java` | 1–118 (full) |
| Upstream registry conventions | `use-core/.../operations/StandardOperationsNumber.java` | 1–90, 460, 505, 887 |
| Value layer (fork) | `.../USE-Uncertainty/src/main/org/tzi/use/uml/ocl/value/SBooleanValue.java` | 1–476 (full) |
| Oracle algebra (source) | `.../uncertainty/uDataTypes/Libraries/Java/src/uDataTypes/SBoolean.java` | 1–1584 (full) |
| Oracle algebra (jar, live) | `.../USE-Uncertainty/lib/atenearesearchgroup.uncertainty.jar` → `uDataTypes/SBoolean.class` | `javap` disassembly |
| Dispatch / undefined handling | `use-core/.../uml/ocl/expr/ExpStdOp.java` and `.../USE-Uncertainty/.../expr/ExpStdOp.java` | 275–318 / 282–325 |

> Evidence policy for this document: every claim below is tagged with a file + line
> (or the shell command that produced it). Claims I could not establish are marked
> **UNVERIFIABLE**. Nothing here is taken from `origin/main`; that ref was not consulted.

---

## 1. Operation count and its reproducer

**39 operations are registered.** Six further operations exist only as commented-out
enum constants (lines 1281–1479) and are *not* registered.

Reproducer (run from the fork's operations directory):

```bash
cd /home/xoruser/msc-4/use-msc2026/.git/reference-repositories/uncertainty/USE-Uncertainty/src/main/org/tzi/use/uml/ocl/expr/operations
grep -cE '^\s+[A-Z][A-Z_0-9]*\(new OpGeneric\(\) \{' StandardOperationsSBoolean.java
```

Executed output: `39`.

Two independent cross-checks, both executed, both `39`:

```bash
grep -cE '^ {8}public String name\(\) \{'  StandardOperationsSBoolean.java   # -> 39
grep -cE '^ {12}return "'                  StandardOperationsSBoolean.java   # -> 39
```

A naive `grep -c 'new OpGeneric()'` returns **45** because it also counts the six
commented-out constants — do not use it.

Further executed counts over the same file:

| Command | Result | Meaning |
|---|---|---|
| `grep -cE '^ {12}return OPERATION;'` | 39 | every registered op has `kind() == OpGeneric.OPERATION` |
| `grep -nE '^ {12}return true;'` | only line 418 | exactly one op is infix/prefix (`not`) |
| `grep -cE '^ {12}return false;'` | 38 | the other 38 are dot-calls |
| `grep -c 'isBooleanOperation'` | 0 | none overrides it → all inherit `false` (OpGeneric.java:40–42) |
| `grep -c 'checkWarningUnrelatedTypes'` | 0 | none overrides it |
| `grep -c 'stringRep'` | 0 | none overrides it |

---

## 2. Registration contract in the fork

`StandardOperationsSBoolean` is a **Java `enum`**, not a set of top-level classes
(line 15: `public enum StandardOperationsSBoolean {`). Each constant wraps one
anonymous `OpGeneric` instance; the plumbing is at lines 1484–1500:

```java
private OpGeneric op;                                       // 1485
StandardOperationsSBoolean(OpGeneric op) { this.op = op; }   // 1487–1489
public OpGeneric getOp() { return op; }                      // 1491–1493

public static void registerTypeOperations(Multimap<String, OpGeneric> opmap) {   // 1495
    for (StandardOperationsSBoolean op : StandardOperationsSBoolean.values())
        OpGeneric.registerOperation(op.getOp(), opmap);                          // 1498
}
```

`OpGeneric.registerOperation(op, opmap)` (fork OpGeneric.java:112–114) does
`opmap.put(op.name(), op)`. The registry is hooked into the global op map at fork
`OpGeneric.java:97` (`StandardOperationsSBoolean.registerTypeOperations(opmap)`),
inside a block labelled `// Uncertainty Types` at lines 92–97, placed **after**
`StandardOperationsBoolean` (line 90) and **before** the collection registries (line 100).

**Registration order is semantically load-bearing.** `ExpStdOp.opmap` is an
`ArrayListMultimap` (fork `ExpStdOp.java:60`), and `ExpStdOp.create` returns the
**first** `OpGeneric` whose `matches(params)` is non-null (fork `ExpStdOp.java:128–135`;
the same first-match loop backs `ExpStdOp.exists` at L95–102).
Because `StandardOperationsBoolean` and `StandardOperationsNumber` register earlier,
the pre-existing `Boolean::and/or/xor/implies/not` and `Number::min/max/toString`
overloads win for their own operand types, and the SBoolean overloads are only
reached when the earlier `matches` fail. This is what keeps the SBoolean additions
non-breaking; a port that changes registration order changes behaviour.

### Arity convention used throughout this document

The registry has **no separate receiver**. `matches(Type[] params)` and
`eval(EvalContext, Value[] args, Type)` receive the receiver as `params[0]` / `args[0]`.
**Arity below counts the receiver as argument 0.** So `belief` has arity 1
(`params.length == 1`), `and` has arity 2, `deduceY` has arity 3. The surface OCL
call for a dot-call op of arity *n* has *n − 1* parenthesised arguments.

### Argument-type vocabulary

Two distinct receiver predicates are used, and the choice is *inconsistent across
the file*:

* **`params[0].isTypeOfSBoolean()`** — exact type `SBoolean` only. Used by all
  unary ops plus `isCertain` and `isUncertain`.
* **`params[0].isKindOfSBoolean(Type.VoidHandling.EXCLUDE_VOID)`** — used by all
  binary/ternary ops.

The `isKindOf` form is much wider than it looks. In the fork's type lattice:

| Type | `isKindOfSBoolean(h)` | Evidence |
|---|---|---|
| `SBooleanType` | `true` | `type/SBooleanType.java:17–20` |
| `UBooleanType` | `true` | `type/UBooleanType.java:22–25` |
| `BooleanType` | `true` | `type/BooleanType.java:54–57` |
| `VoidType` | `h == INCLUDE_VOID` | `type/VoidType.java:127–130` |
| everything else | `false` (default) | `type/TypeImpl.java:374–377` |

So **every binary SBoolean operation also accepts plain `Boolean` and `UBoolean` in
either position**, and rejects `null`/`Undefined` at compile time because
`EXCLUDE_VOID` is passed. Unary operations, using `isTypeOfSBoolean()`, accept
**only** exact `SBoolean` — a `UBoolean` receiver cannot call `.belief()`.

Coercion at eval time is by `SBooleanValue.valueOf(Value)`
(`value/SBooleanValue.java:71–88`): `SBoolean` passes through; `UBoolean` becomes
`new SBoolean(ub.getuBoolean())` (b = c, d = 1 − c, u = 0, a = c — `uDataTypes/SBoolean.java:80–86`);
`Boolean` becomes the constants `SBooleanValue.TRUE = (1,0,0,1)` /
`FALSE = (0,1,0,1)` (`SBooleanValue.java:13–14`); anything else yields `null`.

> ⚠️ **Constant mismatch worth carrying as a port decision**: `SBooleanValue.FALSE`
> is `(b=0, d=1, u=0, a=1)`, but `uDataTypes.SBoolean(boolean false)` builds
> `(0,1,0,0)` — base rate `0`, not `1` (`uDataTypes/SBoolean.java:34`). The two
> "false" opinions differ in base rate and therefore differ under `not`, `and`,
> `or`, `xor` and `applyOn`. The registry always goes through `SBooleanValue`, so
> `a = 1` is the one that is actually observable from OCL.

---

## 3. Shared eval-time behaviour (applies to all 39)

1. **Undefined arguments never reach `eval`.** All 39 ops declare
   `kind() == OPERATION`. `ExpStdOp.eval` (7.5.0 lines 286–307; fork lines 293–314)
   evaluates arguments left to right and, on the first `v.isUndefined()`, short-circuits
   to `UndefinedValue.instance` **without calling `eval`**. So for every operation in
   this file the rule is: *any undefined argument ⇒ result is `Undefined`*. There is
   no per-op undefined handling anywhere in the file.
2. **Undefined *elements inside a collection argument* are NOT covered by rule 1.**
   The fusion/discount ops take a `Collection`; the strictness check only inspects
   top-level `args[i]`. An `Undefined` element reaches
   `SBooleanValue.assertKindOfSBoolean` (`SBooleanValue.java:155–162`), where
   `valueOf` returns `null` and the method throws
   `RuntimeException("A value kind of SBoolean expected")`. That escapes `eval`;
   `ExpStdOp` only catches `ArithmeticException` (7.5.0 `ExpStdOp.java:310–313`).
3. **`ArithmeticException` is the only exception softened to `Undefined`**
   (7.5.0 `ExpStdOp.java:310–313`). Since the whole SBoolean algebra is `double`
   arithmetic, division by zero produces `Infinity`/`NaN` rather than throwing, so
   this safety net does **not** fire for SBoolean. Instead a bad intermediate result
   trips the `SBoolean` constructor invariant and throws
   `IllegalArgumentException("SBoolean constructor: Invalid parameters. b:…")`
   (`uDataTypes/SBoolean.java:49–52`) — an uncaught crash of the evaluator.
4. **Every scalar written into an `SBoolean` is rounded to 6 decimals**
   by `adjust()` (`uDataTypes/SBoolean.java:39–41`,
   `Math.round(value * 1e6) / 1e6`), and the constructor enforces
   `|b + d + u − 1| ≤ 0.001` and `b,d,u,a ∈ [0,1]` (lines 49–52).
5. **String rendering**: `not` is prefix (`stringRep` → `not x`, OpGeneric.java:57–61);
   all other 38 render as dot-calls `recv.op(a1, …)` (OpGeneric.java:65–75) — including
   `and`/`or`/`xor`/`implies`, which are *parsed* infix (see §6, defect D6).

---

## 4. Summary table (39 rows, source order)

Arity counts the receiver as argument 0. "Form" is the `isInfixOrPrefix()` verdict
combined with `OpGeneric.stringRep`.

| # | OCL name | Enum constant | Lines | Arity | Argument types (in order) | Result type | Form |
|---:|---|---|---|---:|---|---|---|
| 1 | `projection` | `PROYECTION` | 18–46 | 1 | `params[0]`: exactly SBoolean | `Real` | dot-call |
| 2 | `belief` | `BELIEF` | 49–77 | 1 | exactly SBoolean | `Real` | dot-call |
| 3 | `disbelief` | `DISBELIEF` | 80–108 | 1 | exactly SBoolean | `Real` | dot-call |
| 4 | `baseRate` | `BASE_RATE` | 113–141 | 1 | exactly SBoolean | `Real` | dot-call |
| 5 | `uncertainty` | `UNCERTAINTY` | 146–174 | 1 | exactly SBoolean | `Real` | dot-call |
| 6 | `uncertaintyMaximized` | `UNCERTAINTY_MAXIMIZED` | 177–205 | 1 | exactly SBoolean | `SBoolean` | dot-call |
| 7 | `projectiveDistance` | `PROJECTIVE_DISTANCE` | 208–238 | 2 | kindOf SBoolean, kindOf SBoolean (both EXCLUDE_VOID) | `Real` | dot-call |
| 8 | `conjunctiveCertainty` | `CONJUNCTIVE_CERTAINTY` | 241–271 | 2 | kindOf SBoolean ×2 | **declared `SBoolean`, actually `Real`** (defect D1) | dot-call |
| 9 | `degreeOfConflict` | `DEGREE_OF_CONFLICT` | 274–304 | 2 | kindOf SBoolean ×2 | **declared `SBoolean`, actually `Real`** (defect D1) | dot-call |
| 10 | `deduceY` | `DEDUCE_Y` | 307–339 | 3 | kindOf SBoolean ×3 | `SBoolean` | dot-call |
| 11 | `toUBoolean` | `TO_UBOOLEAN` | 342–370 | 1 | exactly SBoolean | `UBoolean` | dot-call |
| 12 | `toString` | `TO_STRING` | 373–401 | 1 | exactly SBoolean | `String` | dot-call |
| 13 | `not` | `NOT` | 404–432 | 1 | exactly SBoolean | `SBoolean` | **prefix** |
| 14 | `and` | `AND` | 435–465 | 2 | kindOf SBoolean ×2 | `SBoolean` | dot-call (parsed infix — D6) |
| 15 | `or` | `OR` | 468–498 | 2 | kindOf SBoolean ×2 | `SBoolean` | dot-call (parsed infix — D6) |
| 16 | `xor` | `XOR` | 501–531 | 2 | kindOf SBoolean ×2 | `SBoolean` | dot-call (parsed infix — D6) |
| 17 | `equivalent` | `EQUIVALENT` | 534–564 | 2 | kindOf SBoolean ×2 | `SBoolean` | dot-call |
| 18 | `implies` | `IMPLIES` | 567–597 | 2 | kindOf SBoolean ×2 | `SBoolean` | dot-call (parsed infix — D6) |
| 19 | `getRelativeWeight` | `GETRELATIVEWEIGHT` | 601–629 | 1 | exactly SBoolean | `Real` | dot-call |
| 20 | `isAbsolute` | `ISABSOLUTE` | 632–660 | 1 | exactly SBoolean | `Boolean` | dot-call |
| 21 | `isVacuous` | `ISVACUOUS` | 663–691 | 1 | exactly SBoolean | `Boolean` | dot-call |
| 22 | `isCertain` | `ISCERTAIN` | 694–725 | 2 | exactly SBoolean, kindOf Real (EXCLUDE_VOID) | `Boolean` | dot-call |
| 23 | `isDogmatic` | `ISDOGMATIC` | 728–756 | 1 | exactly SBoolean | `Boolean` | dot-call |
| 24 | `isMaximizedUncertainty` | `ISMAXIMIZEDUNCERTAINTY` | 759–787 | 1 | exactly SBoolean | `Boolean` | dot-call |
| 25 | `isUncertain` | `ISUNCERTAIN` | 790–821 | 2 | exactly SBoolean, kindOf Real (EXCLUDE_VOID) | `Boolean` | dot-call |
| 26 | `uncertainOpinion` | `UNCERTAINOPINION` | 824–852 | 1 | exactly SBoolean | `SBoolean` | dot-call |
| 27 | `certainty` | `CERTAINTY` | 855–883 | 1 | exactly SBoolean | `Real` | dot-call |
| 28 | `minimumBeliefFusion` | `MINIMUMBELIEFFUSION` | 886–916 | 2 | kindOf SBoolean, kindOf **Collection** (any element type) | `SBoolean` | dot-call |
| 29 | `majorityBeliefFusion` | `MAJORITYBELIEFFUSION` | 919–949 | 2 | kindOf SBoolean, kindOf Collection | `SBoolean` | dot-call |
| 30 | `beliefConstraintFusion` | `BELIEFCONSTRAINTFUSION` | 952–982 | 2 | kindOf SBoolean, kindOf Collection | `SBoolean` | dot-call |
| 31 | `averageBeliefFusion` | `AVERAGEBELIEFFUSION` | 985–1015 | 2 | kindOf SBoolean, kindOf Collection | `SBoolean` | dot-call |
| 32 | `aleatoryCumulativeBeliefFusion` | `CUMULATIVEBELIEFFUSION` | 1018–1048 | 2 | kindOf SBoolean, kindOf Collection | `SBoolean` | dot-call |
| 33 | `epistemicCumulativeBeliefFusion` | `EPISTEMICCUMULATIVEBELIEFFUSION` | 1051–1081 | 2 | kindOf SBoolean, kindOf Collection | `SBoolean` | dot-call |
| 34 | `weightedBeliefFusion` | `WEIGHTEDBELIEFFUSION` | 1084–1114 | 2 | kindOf SBoolean, kindOf Collection | `SBoolean` | dot-call |
| 35 | `consensusAndCompromiseFusion` | `CONSENSUSANDCOMPROMISEFUSION` | 1117–1147 | 2 | kindOf SBoolean, kindOf Collection | `SBoolean` | dot-call |
| 36 | `discount` | `DISCOUNT` | 1150–1180 | 2 | kindOf SBoolean, kindOf Collection | `SBoolean` | dot-call |
| 37 | `min` | `MIN` | 1183–1213 | 2 | kindOf SBoolean ×2 | `SBoolean` | dot-call |
| 38 | `max` | `MAX` | 1216–1246 | 2 | kindOf SBoolean ×2 | `SBoolean` | dot-call |
| 39 | `applyOn` | `APPLYON` | 1249–1279 | 2 | kindOf SBoolean, kindOf **UBoolean** (EXCLUDE_VOID) | `SBoolean` | dot-call |

Line ranges are the enum-constant span (`NAME(new OpGeneric() {` … `}),`). Each is
preceded by a one-line `//` comment two lines earlier; several of those comments are
wrong (see defects D1/D2/D5).

---

## 5. Per-operation detail

Notation: an opinion is ω = (b, d, u, a) with b + d + u = 1; the **projected
probability** is P = b + a·u. "confidence" = 1 − u (`certainty()`). Each entry gives
the delegation chain registry → `SBooleanValue` → `uDataTypes.SBoolean`.

### 1. `projection` — lines 18–46
* Chain: `eval` L42–45 → `SBooleanValue.projection()` L211–213 → `SBoolean.projection()` L112–114.
* Semantics: returns the projected probability `P = adjust(b + a·u)` as a `Real`;
  this is the single scalar that collapses the belief/uncertainty split into a
  probability — the uncertainty mass `u` is redistributed according to the base rate `a`.
* Special cases in `eval`: none. Undefined receiver ⇒ `Undefined` by the OPERATION rule.
  `u = 0` ⇒ `P = b`; `u = 1` (vacuous) ⇒ `P = a`. No division, no NaN path.

### 2. `belief` — lines 49–77
* Chain: `eval` L73–76 → `SBooleanValue.belief()` L195–197 → `SBoolean.belief()` L91.
* Semantics: projects out the belief mass `b` as a `Real`; the uncertainty component is
  discarded, not combined.
* Special cases: none.

### 3. `disbelief` — lines 80–108
* Chain: `eval` L104–107 → `SBooleanValue.disbelief()` L199–201 → `SBoolean.disbelief()` L92.
* Semantics: projects out the disbelief mass `d`; uncertainty discarded.
* Special cases: none.

### 4. `baseRate` — lines 113–141
* Chain: `eval` L137–140 → `SBooleanValue.baseRate()` L207–209 → `SBoolean.baseRate()` L94.
* Semantics: returns the prior `a` — the parameter that governs how `u` converts to
  probability; independent of b/d/u.
* Special cases: none.

### 5. `uncertainty` — lines 146–174
* Chain: `eval` L170–173 → `SBooleanValue.uncertainty()` L203–205 → `SBoolean.uncertainty()` L93.
* Semantics: returns the uncommitted mass `u` — i.e. the uncertainty component itself,
  as a `Real` in [0,1].
* Special cases: none.

### 6. `uncertaintyMaximized` — lines 177–205
* Chain: `eval` L201–204 → `SBooleanValue.uncertaintyMaximized()` L181–183 → `SBoolean.uncertaintyMaximized()` L298–313.
* Semantics: returns the **uncertainty-maximised equivalent opinion** — the opinion with
  the same projected probability `P` and same base rate `a` but with `u` pushed as high
  as the constraints allow, i.e. one of `b` or `d` driven to 0. Formally:
  if `P < a` → `(0, 1 − P/a, P/a, a)`; else → `((P − a)/(1 − a), 0, (1 − P)/(1 − a), a)`.
  The relative weight is carried over via `getRelativeWeight()` (line 305–311).
* Special cases (all in `SBoolean.uncertaintyMaximized`, lines 303–311):
  * `a == 1 && P == 1` → vacuous `(0,0,1,a)` (L305).
  * `a == 1 && u == 1` → vacuous `(0,0,1,a)` (L306).
  * `a == 0 && b == 0` → vacuous `(0,0,1,a)` (L307).
  * Those three guards are exactly what prevents division by `a` (branch `P < a`
    is unreachable when `a == 0` because `P ≥ 0`) and by `1 − a` (`a == 1` is
    fully handled: with `a == 1`, `P = b + u`; if `P < 1` the `P < a` branch is
    taken, and `P == 1` is caught at L305).
  * A dogmatic input (`u = 0`) is *not* special-cased: it is mapped to a genuinely
    uncertain opinion with the same `P`.
  * `relativeWeight` becomes 0 whenever the input is non-dogmatic, because
    `getRelativeWeight()` returns 0 for `u != 0` (L97–99).

### 7. `projectiveDistance` — lines 208–238
* Chain: `eval` L233–237 → `SBooleanValue.projectiveDistance(Value)` L166–169 → `SBoolean.projectiveDistance` L116–118.
* Semantics: `adjust(|P(this) − P(other)|)` — the absolute distance between the two
  *projected probabilities*, i.e. the uncertainty of each operand is folded into its own
  projection first and only then compared; the result carries no uncertainty component.
* Result type: `Real` (`matches` L226–230 returns `TypeFactory.mkReal()`). **The `//`
  comment at line 207 says `-> SBoolean` and is wrong**; code and comment disagree,
  the code is `Real` and is consistent with the value layer (`RealValue`, L168).
* Special cases: `assertKindOfSBoolean` (L167) throws
  `RuntimeException("A value kind of SBoolean expected")` if the argument is not
  SBoolean/UBoolean/Boolean — unreachable from OCL given `matches`. No division.

### 8. `conjunctiveCertainty` — lines 241–271
* Chain: `eval` L266–270 → `SBooleanValue.conjunctiveCertainty(Value)` L171–174 → `SBoolean.conjunctiveCertainty` L120–122.
* Semantics: `adjust((1 − u₁)·(1 − u₂))` — the product of the two **confidences**;
  a pure uncertainty-combination operator, ignoring b/d entirely.
* **Defect D1**: `matches` (L259–262) declares the static result type
  `TypeFactory.mkSBoolean()`, but `eval` returns a `RealValue`
  (`SBooleanValue.java:173`). The declared type and the runtime value disagree.
* Special cases: `assertKindOfSBoolean` as above. `u₁ = 1` or `u₂ = 1` ⇒ result `0`;
  both dogmatic (`u = 0`) ⇒ result `1`. No division.

### 9. `degreeOfConflict` — lines 274–304
* Chain: `eval` L299–303 → `SBooleanValue.degreeOfConflict(Value)` L176–179 → `SBoolean.degreeOfConflict` L124–126.
* Semantics: `adjust(projectiveDistance(s) · conjunctiveCertainty(s))` — how far apart
  the two projected probabilities are, **weighted by how confident both opinions are**;
  disagreement between two vacuous opinions counts as zero conflict.
* **Defect D1** applies here too: `matches` (L292–295) declares `SBoolean`, `eval`
  returns `RealValue` (`SBooleanValue.java:178`).
* Special cases: as for #7/#8; `u₁ = 1` or `u₂ = 1` forces `0`. No division.

### 10. `deduceY` — lines 307–339
* Chain: `eval` L333–338 → `SBooleanValue.deduceY(Value, Value)` L185–189 → `SBoolean.deduceY` L315–389.
* Arity **3**: receiver X (arg 0), `yGivenX` (arg 1), `yGivenNotX` (arg 2). All three
  `isKindOfSBoolean(EXCLUDE_VOID)` (`matches` L325–330).
* Semantics: subjective-logic **binomial deduction** — computes ω(Y) from ω(X) and the
  two conditionals ω(Y|X), ω(Y|¬X). The deduced base rate is
  `a_Y = (a·b_{Y|X} + (1−a)·b_{Y|¬X}) / (1 − a·u_{Y|X} − (1−a)·u_{Y|¬X})` (L319–320);
  the raw `(bIy, dIy, uIy)` triple is the a-priori mixture (L323–328), and an
  uncertainty-correction term `K`, selected by an eight-way case analysis
  (cases II.A.1 … III.B.2, L335–382), is then **moved into the uncertainty mass**:
  `b = bIy − a_Y·K`, `d = dIy − (1 − a_Y)·K`, `u = uIy + K` (L384–386). The result's
  relative weight is `yGivenX.getRelativeWeight() + yGivenNotX.getRelativeWeight()` (L387).
* Special cases:
  * `u_{Y|X} + u_{Y|¬X} == 2` (both conditionals vacuous) ⇒ base-rate formula is
    bypassed and `a_Y = yGivenX.a` (ternary at L319–320) — this is the guard against
    the `0/0` in the base-rate denominator.
  * `K` defaults to `0` (case I, L332) when neither of the two monotonicity patterns holds.
  * **Unguarded divisions** in the eight `K` formulas: denominators include
    `(b + a·u)`, `(d + (1 − a)·u)`, `y.a`, `(1 − y.a)`,
    `(yGivenNotX.d − yGivenX.d)` and `(yGivenX.b − yGivenNotX.b)`.
    Any of these can be `0` for admissible inputs (e.g. `y.a == 0`, or a dogmatic
    X with `b = u = 0`), yielding `Infinity`/`NaN` in `K`. Java `double` division
    does not throw, so `ExpStdOp`'s `ArithmeticException` net (7.5.0 L310–313) does
    **not** catch it; instead the `SBoolean(b,d,u,a)` constructor invariant at
    `uDataTypes/SBoolean.java:49–52` throws `IllegalArgumentException`, which
    propagates out of the evaluator. This is the highest-risk operation to port.
  * Note `y.b/y.d/y.u` are assigned directly to the protected fields (L384–386),
    bypassing the constructor check; the check happens only in `new SBoolean()` at
    L316, which builds dogmatic-true `(1,0,0,1)`. So a NaN can survive into the
    returned `SBoolean` **without** an exception, and only surfaces later.
    **UNVERIFIABLE** without executing the jar (not done — see §8) which of the two
    failure modes (exception vs. silent NaN) a given input takes.

### 11. `toUBoolean` — lines 342–370
* Chain: `eval` L366–369 → `SBooleanValue.toUBoolean()` L191–193 → `SBoolean.toUBoolean()` L1562–1564.
* Semantics: **collapses the opinion to a `UBoolean`** by returning
  `new UBoolean(true, this.projection())` — value `true` with confidence equal to the
  projected probability `P = b + a·u`. This is the lossy dual of `uncertaintyMaximized`:
  the uncertainty mass is fully absorbed into the confidence number and `u` is gone.
* Special cases: none; total function.

### 12. `toString` — lines 373–401
* `eval` L397–400: `return new StringValue(sbool.toString())` where `sbool` is an
  **`SBooleanValue`**, not a `uDataTypes.SBoolean`.
* `Value.toString()` is `final` and delegates to `toString(StringBuilder)`
  (fork `value/Value.java:189–196`), so the format is
  `SBooleanValue.toString(StringBuilder)` at `SBooleanValue.java:124–130`:
  `"<TypeName>(" + round(b,3) + ", " + round(d,3) + ", " + round(u,3) + ", " + round(a,3) + ")"`,
  e.g. `SBoolean(0.5, 0.3, 0.2, 0.5)`. Rounding is `org.tzi.use.util.MathUtil.round`
  (fork `util/MathUtil.java:106`).
* **This is not** `uDataTypes.SBoolean.toString()`'s
  `String.format("SBoolean(%5.3f, %5.3f, %5.3f, %5.3f)", …)` (`uDataTypes/SBoolean.java:1558–1560`,
  confirmed present in the jar as string constant `SBoolean(%5.3f, %5.3f, %5.3f, %5.3f)`).
  A port that routes through the uDataTypes `toString` would produce
  space-padded fixed-width fields and differ. All four components including the
  uncertainty are rendered.
* Special cases: none.

### 13. `not` — lines 404–432
* The **only** op with `isInfixOrPrefix() == true` (L417–419) ⇒ rendered prefix
  (`not x`) by `OpGeneric.stringRep` L57–61; parsed prefix via `OCL.g:317`
  (`('not' | MINUS | PLUS)` in `unaryExpression`).
* Chain: `eval` L428–431 → `SBooleanValue.not()` L220–223 → `SBoolean.not()` L234–242.
* Semantics: complement — swaps belief and disbelief, **leaves the uncertainty mass `u`
  untouched**, and complements the base rate to `1 − a`. Result: `(d, b, u, 1 − a)`.
* Special cases: `relativeWeight` is passed through as the raw field (L240), *not* via
  `getRelativeWeight()` — so unlike `and`/`or`/`xor` it is preserved even for a
  non-dogmatic opinion. `not` of a vacuous opinion `(0,0,1,a)` is `(0,0,1,1−a)`.
* Receiver predicate is `isTypeOfSBoolean()` (L423) — exact SBoolean only; the
  pre-registered `Boolean::not` (7.5.0 `StandardOperationsBoolean.java:169`) handles
  plain Booleans and is tried first.

### 14. `and` — lines 435–465
* Chain: `eval` L460–464 → `SBooleanValue.and(Value)` L215–218 → `SBoolean.and` L244–258.
* Semantics: subjective-logic **binomial multiplication (independent conjunction)**.
  `b = b₁b₂ + [(1−a₁)a₂b₁u₂ + a₁(1−a₂)u₁b₂] / (1 − a₁a₂)`,
  `d = d₁ + d₂ − d₁d₂`, `u = 1 − d − b` (the uncertainty is computed as the residual, not
  by the textbook formula — see the commented-out alternative at L253),
  `a = a₁a₂`. The correction term in `b` is precisely the part of the uncertainty mass
  that conjunction re-attributes to belief.
* Special cases:
  * `this == s` (**reference identity**, L246): returns `this.clone()` — the idempotence
    shortcut `x and x = x`. Note this is `==`, not `equals`, so it only fires when the
    very same `SBoolean` object is on both sides.
  * `a₁·a₂ == 1` ⇒ the ternary at L248 substitutes `0.0` for the correction term,
    guarding the `1 − a₁a₂` division by zero.
  * `relativeWeight` of the result is `this.getRelativeWeight() + s.getRelativeWeight()`
    (L255), which is `0 + 0` unless both operands are dogmatic (L97–99).
* Registration order note: `Boolean::and` (`kind() == SPECIAL`, short-circuiting,
  `BooleanOperation.java:14–26`) is registered first, so plain-Boolean `and` keeps its
  short-circuit semantics. The SBoolean `and` is a **strict, non-short-circuiting**
  `OPERATION`: both operands are always evaluated, and either being `Undefined` yields
  `Undefined` rather than `false`.

### 15. `or` — lines 468–498
* Chain: `eval` L493–497 → `SBooleanValue.or(Value)` L225–228 → `SBoolean.or` L260–274.
* Semantics: subjective-logic **binomial comultiplication (independent disjunction)**.
  `b = b₁ + b₂ − b₁b₂`,
  `d = d₁d₂ + [a₁(1−a₂)d₁u₂ + a₂(1−a₁)u₁d₂] / (a₁ + a₂ − a₁a₂)`,
  `u = 1 − b − d` (residual), `a = a₁ + a₂ − a₁a₂`. The correction term re-attributes
  part of the uncertainty mass to disbelief.
* Special cases: `this == s` (reference identity) ⇒ `this.clone()` (L262);
  `a₁ + a₂ == a₁a₂` (i.e. denominator 0, which for a ∈ [0,1] means `a₁ = a₂ = 0`)
  ⇒ correction term forced to `0.0` (L265). `relativeWeight` as in `and` (L271).

### 16. `xor` — lines 501–531
* Chain: `eval` L526–530 → `SBooleanValue.xor(Value)` L230–233 → `SBoolean.xor` L286–296.
* Semantics: `b = |b₁ − b₂|`, `u = u₁·u₂` (**the uncertainties multiply**),
  `d = 1 − |b₁ − b₂| − u₁u₂` (residual), `a = |a₁ − a₂|`.
* Special cases: none — no branch, no division. Two vacuous opinions give
  `(0, 0, 1, 0)`. Note this is *not* the classical `b₁(1−b₂) + (1−b₁)b₂` (that form is
  present but commented out at L288–289), so `xor` is **not** consistent with the
  Boolean-type `xor`; the constructor invariant `b + d + u = 1` holds by construction.
* The `//` comment above the constant (line 500) reads `// or: SBoolean x SBoolean -> SBoolean` — a copy-paste slip (defect D5).

### 17. `equivalent` — lines 534–564
* Chain: `eval` L559–563 → `SBooleanValue.equivalent(Value)` L235–238 → `SBoolean.equivalent` L281–284.
* Semantics: defined as `this.xor(s).not()` (L283) ⇒
  `b = 1 − |b₁ − b₂| − u₁u₂`, `d = |b₁ − b₂|`, `u = u₁u₂`, `a = 1 − |a₁ − a₂|`.
  The uncertainty of the result is again the product of the operand uncertainties.
* Special cases: inherits `xor`'s (none) plus `not`'s (none). The alternative definition
  `this.implies(s).and(s.implies(this))` is present but commented out (L282).
* Cross-reference: the same function backs `SBooleanValue.uEquals` (L100–109), i.e. the
  `=` operator on SBoolean via `StandardOperationsAny` (fork `StandardOperationsAny.java:50–59`).
* Comment slip at line 533 (defect D5).

### 18. `implies` — lines 567–597
* Chain: `eval` L592–596 → `SBooleanValue.implies(Value)` L240–243 → `SBoolean.implies` L276–279.
* Semantics: material implication, defined as `this.not().or(s)` — explicitly chosen
  "to be consistent with UBoolean, because in Subjective Logic this is not the case"
  (comment, L278). So the uncertainty combination is exactly `or`'s: the residual
  `u = 1 − b − d` after `or`'s belief/disbelief formulas applied to `(d₁, b₁, u₁, 1−a₁)` and `(b₂, d₂, u₂, a₂)`.
* Special cases: `or`'s only (`this == s` cannot fire, since `this.not()` is a fresh
  object; the `a₁ + a₂ == a₁a₂` guard can, when `1 − a₁ = 0` and `a₂ = 0`).

### 19. `getRelativeWeight` — lines 601–629
* Chain: `eval` L625–628 → `SBooleanValue.getRelativeWeight()` L245–247 → `SBoolean.getRelativeWeight()` L97–99.
* Semantics: returns the fusion **relative weight** as a `Real`, but **only if the
  opinion is dogmatic**: `this.isDogmatic() ? this.relativeWeight : 0.0`. For any
  opinion with `u != 0` the answer is `0.0` regardless of the stored weight.
* Special cases: the `u != 0 ⇒ 0.0` collapse is the whole behaviour. There is no OCL
  operation in this file that *sets* a relative weight; weights only arise internally
  from `and`/`or`/`xor`/`not`/`deduceY`/fusion results.

### 20. `isAbsolute` — lines 632–660
* Chain: `eval` L656–659 → `SBooleanValue.isAbsolute()` L249–251 → `SBoolean.isAbsolute()` L139–141.
* Semantics: `b == 1.0 || d == 1.0` — true iff the opinion is a *hard* true or false;
  implies `u == 0`.
* Special cases: exact `double` equality against `1.0`; safe in practice because
  `adjust()` rounds to 6 decimals, but `0.9999996` rounds to `1.0` and *would* count
  as absolute. Result type `Boolean` (not `UBoolean`/`SBoolean`) — the answer carries no
  uncertainty.

### 21. `isVacuous` — lines 663–691
* Chain: `eval` L687–690 → `SBooleanValue.isVacuous()` L253–255 → `SBoolean.isVacuous()` L143–145.
* Semantics: `u == 1.0` — the opinion carries no committed belief at all.
* Special cases: exact `double` equality (same `adjust` caveat).

### 22. `isCertain` — lines 694–725
* Arity 2: receiver (exactly SBoolean, L714) and a threshold that is
  `isKindOfReal(EXCLUDE_VOID)` (L715) — so `Real` **or `Integer`**
  (`type/IntegerType.java:53–55`), and *not* `UReal` (no override ⇒ `TypeImpl` default false).
* Chain: `eval` L720–724 (`RealValue.valueOf(args[1])`, fork `value/RealValue.java:90–101`)
  → `SBooleanValue.isCertain(Value)` L257–260 → `SBoolean.isCertain(double)` L147–149.
* Semantics: `!isUncertain(t)`, i.e. **confidence ≥ threshold**: `1 − u >= t`.
* Special cases: `t = 0` ⇒ always true (`1 − u >= 0` always holds). `t = 1` ⇒ true only
  for dogmatic opinions (`u == 0`). `t > 1` ⇒ always false; `t < 0` ⇒ always true;
  no range validation on the threshold anywhere. `RealValue.valueOf` returns `null`
  for a non-Real/Integer, which would NPE — unreachable given `matches`.

### 23. `isDogmatic` — lines 728–756
* Chain: `eval` L752–755 → `SBooleanValue.isDogmatic()` L262–264 → `SBoolean.isDogmatic()` L151–153.
* Semantics: `u == 0.0` — no uncertainty mass at all; the opinion reduces to a
  probability.
* Special cases: exact `double` equality.

### 24. `isMaximizedUncertainty` — lines 759–787
* Chain: `eval` L783–786 → `SBooleanValue.isMaximizedUncertainty()` L266–268 → `SBoolean.isMaximizedUncertainty()` L155–157.
* Semantics: `d == 0.0 || b == 0.0` — true iff at least one of the two committed masses
  is zero, which is the structural signature of an uncertainty-maximised opinion
  (cf. `uncertaintyMaximized`, which always drives one of them to 0).
* **Trap**: despite the name it does **not** test `this.equals(this.uncertaintyMaximized())`,
  and it returns `true` for dogmatic extremes such as `(1,0,0,a)` and `(0,1,0,a)` which
  have *minimum* uncertainty. Port it verbatim; do not "fix" it.

### 25. `isUncertain` — lines 790–821
* Arity 2, same argument typing as `isCertain` (L809–812).
* Chain: `eval` L816–820 → `SBooleanValue.isUncertain(Value)` L270–273 → `SBoolean.isUncertain(double)` L159–161.
* Semantics: **confidence < threshold**: `1 − u < t`. Strict `<`.
* Special cases: exact complement of `isCertain` (L148: `return !isUncertain(threshold)`),
  so `isCertain(t) xor isUncertain(t)` is a tautology. `t = 0` ⇒ always false.
  `t = 1` ⇒ true for every non-dogmatic opinion. No threshold range check.
  Verified in the jar bytecode: `1.0 - uncertainty()` compared with `dcmpg / ifge`, i.e. `<`.

### 26. `uncertainOpinion` — lines 824–852
* Chain: `eval` L848–851 → `SBooleanValue.uncertainOpinion()` L275–277 → **`SBoolean.uncertaintyMaximized()`** L298–313.
* Semantics: **identical to `uncertaintyMaximized` (#6)** — `SBooleanValue.uncertainOpinion()`
  calls `sBoolean.uncertaintyMaximized()` directly (L276), and even the uDataTypes
  `uncertainOpinion()` is itself just `return this.uncertaintyMaximized();`
  (`uDataTypes/SBoolean.java:163–165`). The two OCL names are aliases.
* Special cases: exactly those of #6.

### 27. `certainty` — lines 855–883
* Chain: `eval` L879–882 → `SBooleanValue.certainty()` L279–281 → `SBoolean.certainty()` L167–171.
* Semantics: returns the **confidence** `1 − u` as a `Real`; the exact complement of
  `uncertainty` (#5).
* Special cases: the guard `if (this.uncertainty() == (0.0D / 0.0D)) return (0.0D / 0.0D);`
  (L169) is **dead code** — `0.0/0.0` is `NaN` and `NaN == NaN` is false in Java, so the
  branch can never be taken. There is no other NaN handling. Port the effective
  behaviour (`1 − u`); reproducing the dead guard is harmless but pointless.

### 28. `minimumBeliefFusion` — lines 886–916
* `matches` L904–907: `params[0].isKindOfSBoolean(EXCLUDE_VOID)` and
  `params[1].isKindOfCollection(EXCLUDE_VOID)` — **the element type of the collection
  is not checked** (defect D3).
* Chain: `eval` L911–915 (`(CollectionValue) args[1]`) → `SBooleanValue.minimumBeliefFusion(Value)`
  L342–353 → `SBoolean.minimumBeliefFusion(Collection)` (jar) / `SBoolean.minimumFusion` source L498–508.
* Collection handling (`SBooleanValue.java:343–351`): the argument is converted with
  `asSequence()` (so a `Set`'s iteration order decides ties), and **the receiver is
  prepended** — `collection.add(this.sBoolean)` before the loop (L345). Fused input
  size is therefore `1 + |arg|`.
* Semantics: MIN fusion — reduces with `min` (see #37), returning a **clone of the whole
  opinion (b, d, u, a) that has the lowest projected probability**. The uncertainty
  component is not averaged or combined; the winning opinion's `u` is carried through
  verbatim.
* Special cases:
  * `|arg| == 0` ⇒ fused size 1 ⇒
    `IllegalArgumentException("MBF: Cannot fuse null opinions, or only one opinion was passed")`
    (source L499–500; the exact string is present in the jar — verified via
    `javap -c` string-constant dump).
  * `null` element in the collection: `Collection.contains(null)` check (source L499);
    unreachable from OCL because `assertKindOfSBoolean` throws first.
  * Non-SBoolean or `Undefined` element ⇒ `RuntimeException("A value kind of SBoolean expected")`
    from `SBooleanValue.assertKindOfSBoolean` (L155–162).
  * Ties on projection: `min` uses `<=`, so the **earlier** operand wins (source L1538–1540).

### 29. `majorityBeliefFusion` — lines 919–949
* Same typing/collection handling as #28 (`matches` L937–940; `SBooleanValue` L355–366,
  receiver prepended at L358).
* Chain → `SBoolean.majorityBeliefFusion(Collection)` (jar) / `majorityFusion` source L526–539.
* Semantics: counts opinions whose projection is above (`pos`) or below (`neg`) their own
  base rate, ignoring the undecided ones (`P == a`), then returns a **fresh dogmatic
  opinion with base rate 0.5**: `pos > neg` → `(1,0,0,0.5)`; `pos < neg` → `(0,1,0,0.5)`;
  tie → the **vacuous** `(0,0,1,0.5)`. The input uncertainties influence the result only
  through each `P = b + a·u`; the output uncertainty is 0 or 1, never anything else, and
  the input base rates are discarded.
* Special cases: fused size < 2 ⇒ `IllegalArgumentException("MajBF: Cannot fuse null opinions, or only one opinion was passed")`
  (source L527–528; string present in the jar). Tie ⇒ vacuous, not undefined.
  Element checks as in #28.

### 30. `beliefConstraintFusion` — lines 952–982
* Same typing/collection handling (`matches` L970–973; `SBooleanValue` L368–379, receiver
  prepended at L371; note the fully qualified call `uDataTypes.SBoolean.beliefConstraintFusion` at L378).
* Chain → `SBoolean.beliefConstraintFusion(Collection)` (jar) / `cbFusion` source L473–483,
  which left-folds the binary `bcFusion` (source L1290–1306).
* Semantics: **Dempster-style belief constraint fusion**. Per pair:
  `harmony = b₁u₂ + u₁b₂ + b₁b₂`, `conflict = b₁d₂ + d₁b₂`,
  `b = harmony/(1 − conflict)`, `u = u₁u₂/(1 − conflict)`, `d = 1 − b − u`,
  and the base rate is the **confidence-weighted average**
  `a = [a₁(1−u₁) + a₂(1−u₂)] / (2 − u₁ − u₂)`. So the uncertainty mass is the
  *normalised product* of the operand uncertainties — fusion sharply reduces uncertainty —
  and the base rate blends by confidence.
* Special cases:
  * `conflict == 1.0` (totally conflicting: one says b=1, the other d=1) ⇒
    `IllegalArgumentException("BCF: Cannot fuse totally conflicting opinions")` (source L1298–1299;
    string present in the jar).
  * `u₁ + u₂ == 2.0` (both vacuous) ⇒ the base-rate ternary switches to the plain mean
    `(a₁ + a₂)/2` (source L1302–1304), guarding the `2 − u₁ − u₂` division by zero.
  * Fused size < 2 ⇒ `IllegalArgumentException("BCF: Cannot fuse null opinions, or only one opinion was passed")`
    (source L474–475; string present in the jar). So an empty collection argument throws.
  * Left fold ⇒ the operation is order-dependent unless all base rates agree
    (documented at source L465–467).

### 31. `averageBeliefFusion` — lines 985–1015
* Same typing/collection handling (`matches` L1003–1006; `SBooleanValue` L381–392,
  receiver prepended at L384).
* Chain → `SBoolean.averageBeliefFusion(Collection)` (jar) / `averagingFusion` source L554–629.
* Semantics: **averaging belief fusion** (Eq. 32 of Jøsang et al. FUSION 2017 — see the
  comment at source L556–558). With `PU = Πuᵢ`:
  if `PU != 0`, `u' = Σ(PU/uᵢ)`, `b = Σ(bᵢ·PU/uᵢ)/u'`, `a = (Σaᵢ)/n`,
  `u = n·PU/u'`, `d = 1 − b − u`. Each opinion is therefore weighted by the product of
  *the other* opinions' uncertainties, so lower-uncertainty sources dominate.
* Special cases:
  * **At least one dogmatic opinion** (`PU == 0`): the else-branch (source L589–604)
    **discards every non-dogmatic opinion** and averages only the dogmatic ones:
    `b = Σb/count`, `a = Σa/count`, `u = 0`, `d = 1 − b`.
  * `|arg| == 0` ⇒ fused size **1**, which is *allowed* here (the guard at L560–561 only
    rejects `null`/empty, not size 1) and the result is the receiver itself
    (`b/1`, `u = 1·PU/(PU/u) = u`). Unlike #28/#29/#30/#35, an empty collection does **not** throw.
  * Empty overall ⇒ `IllegalArgumentException("AVF: Cannot average null opinions")`
    (string present in the jar) — unreachable, because the receiver is always prepended.
  * Element checks as in #28.

### 32. `aleatoryCumulativeBeliefFusion` — lines 1018–1048
* Enum constant is named `CUMULATIVEBELIEFFUSION` but `name()` (L1022) is
  `"aleatoryCumulativeBeliefFusion"` — the OCL name is the latter.
* Same typing/collection handling (`matches` L1036–1039; `SBooleanValue` L394–405,
  receiver prepended at L397; note it calls `SBoolean.cumulativeBeliefFusion`, L404).
* Chain → `SBoolean.cumulativeBeliefFusion(Collection)` (jar) / `aleatoryCumulativeFusion` source L649–725.
* Semantics: **aleatory cumulative belief fusion** (accumulation of independent evidence).
  With no dogmatic opinion present: `PU = Πuᵢ`, `numerator = Σ(PU/uᵢ) − (n−1)·PU`,
  `b = Σ(PU/uᵢ · bᵢ)/numerator`, `d = Σ(PU/uᵢ · dᵢ)/numerator`,
  **`u = PU/numerator`** — i.e. fusing independent sources multiplies uncertainties and
  the fused uncertainty is strictly lower than any input's. The base rate is taken from
  the **first** opinion in iteration order (source L666–669) — which, thanks to the
  prepend, is always the receiver's `a`.
* Special cases:
  * `|arg| == 0` ⇒ fused size 1 ⇒ early `return opinions.iterator().next().clone()`
    (source L654–656): the receiver, unchanged. Does not throw.
  * **At least one dogmatic opinion** (`u == 0`): the else-branch (source L706–721)
    ignores all non-dogmatic ones and takes a `getRelativeWeight()`-weighted average of
    the dogmatic ones, with `u = 0` and `resultRelativeWeight = totalWeight`.
    If every dogmatic opinion has `getRelativeWeight() == 0` this is `0/0 = NaN`.
    (For a freshly parsed literal `relativeWeight` is 1 and `getRelativeWeight()` returns
    1 for a dogmatic opinion, so `totalWeight ≥ 1` in the common case.)
  * `resultAtomicity` is initialised to `-1` (source L659) and only overwritten inside the
    iteration; with a non-empty collection this always happens.
  * Empty overall ⇒ `IllegalArgumentException("aCBF: Cannot average null opinions")`
    (string present in the jar) — unreachable due to the prepend.

### 33. `epistemicCumulativeBeliefFusion` — lines 1051–1081
* Same typing/collection handling (`matches` L1069–1072; `SBooleanValue` L407–418,
  receiver prepended at L410).
* Chain → `SBoolean.epistemicCumulativeBeliefFusion(Collection)` (jar) /
  `epistemicCumulativeFusion` source L749–831.
* Semantics: identical arithmetic to #32, **followed by `uncertaintyMaximized()` on the
  result** (source L830). Verified directly in the jar: the last instructions of
  `epistemicCumulativeBeliefFusion` are `invokevirtual uncertaintyMaximized:()LuDataTypes/SBoolean;`
  then `areturn`. So the fused opinion is re-expressed with maximal uncertainty at the
  same projected probability — the correct treatment when opinions encode knowledge
  rather than observations.
* Special cases: same as #32, **plus**: for `|arg| == 0` the early `size() == 1` return
  (source L754–756) fires *before* the uncertainty-maximisation, so the receiver is
  returned **un-maximised**. That asymmetry is real and observable.
  Empty overall ⇒ `IllegalArgumentException("eCBF: Cannot average null opinions")`
  (string present in the jar).

### 34. `weightedBeliefFusion` — lines 1084–1114
* Same typing/collection handling (`matches` L1102–1105; `SBooleanValue` L420–431,
  receiver prepended at L423).
* Chain → `SBoolean.weightedBeliefFusion(Collection)` (jar) / `weightedFusion` source L849–935.
* Semantics: **confidence-weighted averaging fusion** (van der Heijden et al., FUSION 2018).
  Three disjoint cases:
  * *Case 1* — no dogmatic opinion **and** at least one with `certainty() > 0`
    (source L868–894): each opinion is weighted by `prod = PU/uᵢ` **and by its confidence**
    `(1 − uᵢ)`; `numerator = Σprod − n·PU`;
    `b = Σ(prod·bᵢ·certᵢ)/numerator`, `d = Σ(prod·dᵢ·certᵢ)/numerator`,
    `u = (n − Σuᵢ)·PU/numerator`, and the base rate is the confidence-weighted mean
    `a = Σ(aᵢ·certᵢ)/(n − Σuᵢ)`. High-certainty sources dominate.
  * *Case 3* — **all vacuous** (`uᵢ == 1` for all, source L895–911): result is
    `(0, 0, 1, a)` where `a` is — because of the `first` flag bug at L900–909 — only the
    **first** opinion's base rate divided by `n`, not the mean. Port verbatim if
    bug-compatibility is required; this is a genuine upstream defect.
  * *Case 2* — dogmatic opinions present (source L913–929): relative-weight-weighted
    average over the dogmatic ones, `u = 0`, `a` taken from the **first** opinion overall.
* Special cases: `|arg| == 0` ⇒ size 1 ⇒ early clone return (source L853–855), no throw.
  Empty overall ⇒ `IllegalArgumentException("WBF: Cannot average null opinions")`
  (string present in the jar). `0/0` NaN is possible in case 2 when all dogmatic weights are 0.

### 35. `consensusAndCompromiseFusion` — lines 1117–1147
* Same typing/collection handling (`matches` L1135–1138; `SBooleanValue` L433–444,
  receiver prepended at L436).
* Chain → `SBoolean.consensusAndCompromiseFusion(Collection)` (jar) / `ccFusion` source L951–1187.
* Semantics: **CC fusion** in three phases (Jøsang §12.6). *Consensus*: the shared belief
  is `min bᵢ`, the shared disbelief is `min dᵢ`. *Compromise*: the residues
  `bᵢ − minb`, `dᵢ − mind` are recombined over all `4ⁿ` domain permutations
  (`tabulateOptions`, source L1264–1283) to yield compromise belief/disbelief and a
  compromise mass that becomes uncertainty. *Normalisation*: the compromise part is
  scaled by `(1 − consensusMass − Πuᵢ)/compromiseMass` and
  **`u = 1 − b − d`** is recovered as the residual (source L1173–1184). The fused base
  rate is the (common) input base rate.
* Special cases:
  * **All base rates must be equal**, else
    `IllegalArgumentException("CCF: Base rates for CC Fusion must be the same")`
    (source L957–964; string present in the jar). Since the receiver is prepended, this
    also constrains the receiver.
  * Fused size < 2 (i.e. empty collection argument) ⇒
    `IllegalArgumentException("CCF: Cannot fuse null opinions, or only one opinion was passed")`
    (source L952–953; string present in the jar).
  * `uᵢ == 0` ⇒ `uWithoutI` forced to `0.0` instead of `PU/uᵢ` (source L1001) — explicit
    zero-divisor guard.
  * `compromiseMass == 0` ⇒ normalisation factor forced to `1.0` (source L1173–1174) —
    second explicit zero-divisor guard.
  * Cost is `O(4ⁿ)` in the number of fused opinions (`tabulateOptions`), so a large
    collection argument is a practical hazard.

### 36. `discount` — lines 1150–1180
* `matches` L1168–1171: `kindOf SBoolean` × `kindOf Collection` — same shape as the fusions,
  **but the meaning of the collection is completely different**.
* Chain: `eval` L1175–1179 → `SBooleanValue.discount(Value)` L446–458 →
  `SBoolean.discount(Collection)` source L1457–1474 (present in the jar as
  `public final SBoolean discount(java.util.Collection<SBoolean>)`).
* **The receiver is NOT prepended here** (compare `SBooleanValue.java:449` with L345/358/371/384/…):
  the collection is the *trust path* `[A₁;A₂], …, [Aₙ₋₁;Aₙ]`, and the receiver is the
  advisor's opinion `[Aₙ:X]`.
* Semantics: **probability-sensitive trust discounting over a multi-edge path**
  (Jøsang §14.3.4). `p = Π projection(tᵢ)` over the trust referrals; then
  `b = p·b_recv`, `d = p·d_recv`, **`u = 1 − p·(b_recv + d_recv)`**, `a = a_recv`.
  The committed masses shrink by the product of projected trusts and the freed mass goes
  entirely into uncertainty; the base rate is untouched.
* Special cases:
  * **Empty collection ⇒ identity**: `reduce(1.0, ·)` gives `p = 1`, so the receiver is
    returned unchanged (up to a new object). No exception — unlike the fusions.
  * `p = 0` (any fully distrusted referral) ⇒ `(0, 0, 1, a)`, the vacuous opinion with the
    receiver's base rate.
  * `null` collection ⇒ `IllegalArgumentException("Discountion operator parameter cannot be null")`
    (source L1458–1459; string present in the jar) — unreachable from OCL.
  * Element checks as in #28 (`assertKindOfSBoolean`, `SBooleanValue.java:453`).
  * The `//` comment at line 1149 says `SBoolean x Collection -> SBoolean`, which is right,
    but the sibling comments above the fusions use the same shape while meaning something
    different — see defect D4.
  * The alternative `discountB` (belief-based rather than projection-based, source L1421–1432
    and L1496–1508) is **not** registered.

### 37. `min` — lines 1183–1213
* `matches` L1201–1204: `kindOf SBoolean` × `kindOf SBoolean` (EXCLUDE_VOID).
* Chain: `eval` L1208–1212 → `SBooleanValue.min(Value)` L466–469 → `SBoolean.min` L1538–1540.
* Semantics: `this.projection() <= opinion.projection() ? this : opinion` — selects the
  **whole opinion** (b, d, u, a) whose projected probability is smaller. The uncertainty is
  not combined; the loser's opinion is dropped entirely.
* Special cases: on a tie (`<=`) the **receiver** wins. The uDataTypes method returns the
  operand object itself (aliasing), though `SBooleanValue.min` wraps it in a fresh
  `SBooleanValue` (L468) so the OCL-visible value is a new wrapper over a shared `SBoolean`.
* Overload note: `Number::min` is registered first (7.5.0 `StandardOperationsNumber.java:505`)
  and `Collection::min` later (`StandardOperationsCollection.java:706`); neither matches
  SBoolean operands.

### 38. `max` — lines 1216–1246
* Chain: `eval` L1241–1245 → `SBooleanValue.max(Value)` L471–474 → `SBoolean.max` L1542–1544.
* Semantics: `this.projection() >= opinion.projection() ? this : opinion` — dual of #37.
* Special cases: on a tie (`>=`) the **receiver** wins. Same aliasing note.

### 39. `applyOn` — lines 1249–1279
* `matches` L1267–1270: `params[0].isKindOfSBoolean(EXCLUDE_VOID)` and
  `params[1].isKindOfUBoolean(EXCLUDE_VOID)` — the latter accepts `UBoolean` and
  **`Boolean`** (`type/BooleanType.java:49–52`), rejects `SBoolean`
  (`TypeImpl.java:210` default false) and `null`.
* Chain: `eval` L1274–1278 (`UBooleanValue.valueOf(args[1])`, fork
  `value/UBooleanValue.java:122–138`, which maps `Boolean` to `UBooleanValue.TRUE/FALSE`)
  → `SBooleanValue.applyOn(Value)` L461–464 (unchecked cast to `UBooleanValue`, safe
  because of the preceding `valueOf`) → `SBoolean.applyOn(UBoolean)` source L185–201.
* Semantics: **rebases the opinion onto a new base rate** taken from the argument's
  confidence. Let `a' = x.getC()`. If `a' == a`, returns a clone. Otherwise the
  **uncertainty mass `u` is preserved exactly**, and the belief is rescaled in proportion
  to `b/a`: `b' = min(a'·b/a, 1 − u)`, `d' = 1 − b' − u`, `a_new = a'`.
* Special cases (source L185–201):
  * `a' < 0 || a' > 1` ⇒ `IllegalArgumentException("applyOn(): baseRate must be between 0 and 1")`
    (string present in the jar). Not reachable through `UBoolean`, whose confidence is
    already constrained to [0,1].
  * `a == a'` ⇒ `this.clone()`, no change.
  * `u == 1` (vacuous) ⇒ `new SBoolean(0, 0, 1, a')` — only the base rate changes.
  * `a == 0` (and therefore `a' != 0`) ⇒ separate formula `b' = b + d·a'` (L195–196),
    guarding the `b/a` division by zero.
  * `a' == 0` ⇒ `b' = min(0, 1−u) = 0` and the whole belief is transferred to disbelief
    (documented at source L182–183).
  * The `min(…, 1 − u)` clamp is what keeps `b' + d' + u = 1` when the rescaling would
    otherwise overflow.

---

## 6. Not registered — commented-out block (lines 1281–1479)

Six enum constants are commented out in full and therefore contribute **nothing** to
the op map. They are listed here so a porter does not resurrect them by accident:

| Would-be OCL name | Lines | Delegate that still exists |
|---|---|---|
| `minimumFusion` | 1281–1312 | `SBooleanValue.minimumFusion(Value)` L294–300 |
| `majorityFusion` | 1315–1346 | `SBooleanValue.majorityFusion(Value)` L302–308 |
| `averageFusion` | 1348–1379 | `SBooleanValue.averageFusion(Value)` L310–316 |
| `cumulativeFusion` | 1382–1413 | `SBooleanValue.cumulativeFusion(Value)` L318–324 |
| `epistemicCumulativeFusion` | 1415–1446 | `SBooleanValue.epistemicCumulativeFusion(Value)` L326–332 |
| `weightedFusion` | 1448–1479 | `SBooleanValue.weightedFusion(Value)` L334–340 |

Each is the **binary** (`SBoolean × SBoolean`) form of one of the registered
collection-based fusions; the `SBooleanValue` methods that back them are still live code
(they build a two-element `LinkedList` and call the same static). Also unregistered but
present in the value layer: `SBooleanValue.createDogmaticOpinion` (L283–287),
`createVacuousOpinion` (L289–292), `uEquals` (L100–109), `uDistinct` (L112–121) —
the latter two are reached through `StandardOperationsAny`'s `=`/`<>`, not through this file.

---

## 7. Defects and traps found in the fork registry

These are things the port must decide about explicitly. Each is a code/contract
disagreement inside the fork, not an opinion about design.

**D1 — declared result type contradicts the returned value (2 ops).**
`conjunctiveCertainty` (`matches` L259–262) and `degreeOfConflict` (`matches` L292–295)
declare `TypeFactory.mkSBoolean()`, but their `eval` returns a `RealValue`
(`SBooleanValue.java:173` and `:178`). The static type of the expression is `SBoolean`
while the runtime value is `Real`. Anything downstream that trusts the static type
(further `matches` calls, `oclIsTypeOf`, the shell's type-annotated output) is wrong.
The correct declaration is `mkReal()`, matching the sibling `projectiveDistance`
(L226–230) which does declare `mkReal()`. **Recommendation: fix to `mkReal()` in the
port, and record the deviation** — but note this changes observable typing, so it needs
to be a conscious decision, not a silent one.

**D2 — three `//` comments state the wrong signature.**
Line 207 says `projectiveDistance : SBoolean x SBoolean -> SBoolean` (it is `-> Real`);
line 240 says `conjuntiveCertainty` (typo) `-> SBoolean`; line 273 says
`degreeOfConflict … -> SBoolean`. Only the last two match the (buggy) `matches`.

**D3 — collection arguments are unconstrained in the element type.**
All eight fusion ops plus `discount` accept `params[1].isKindOfCollection(EXCLUDE_VOID)`
with no element-type check. `sb.minimumBeliefFusion(Set{1, 2})` type-checks and then
throws `RuntimeException("A value kind of SBoolean expected")` at evaluation
(`SBooleanValue.java:159`). Upstream USE style would constrain the element type in
`matches` (cf. `StandardOperationsCollection`). A port can either reproduce the loose
check or tighten it; tightening turns a runtime crash into a compile-time error and is
therefore a behaviour change for models that never actually evaluate the expression.

**D4 — `discount` is shaped like a fusion but is not one.**
Every fusion prepends the receiver to the collection
(`SBooleanValue.java:345, 358, 371, 384, 397, 410, 423, 436`); `discount` does **not**
(L449). Same `matches` shape, opposite convention. An empty collection means
"throw" for four of the fusions, "identity" for `discount` and for
`averageBeliefFusion`/`aleatory…`/`epistemic…`/`weighted…`.

**D5 — copy-paste comment slips.** Lines 500 and 533 both read
`// or: SBoolean x SBoolean -> SBoolean` above `XOR` and `EQUIVALENT`.
The enum constant `PROYECTION` (line 18) is a Spanish spelling of "projection"
(the OCL name at line 22 is correct). `CUMULATIVEBELIEFFUSION` (line 1018) declares the
OCL name `aleatoryCumulativeBeliefFusion`.

**D6 — infix operators render as dot-calls.**
`and`, `or`, `xor`, `implies` are grammar-level infix
(`parser/ocl/OCL.g:209, 221, 233, 245`, each building an `ASTBinaryExpression`), so
`s1 and s2` parses and resolves to the SBoolean overload. But those four ops return
`isInfixOrPrefix() == false` (lines 448, 481, 514, 547, 580), so
`OpGeneric.stringRep` (L65–75) prints them as `s1.and(s2)`. An expression therefore
does not round-trip through its own `toString`. `not` is the only one that sets `true`
(line 418) and prints as `not s`. Upstream `Op_boolean_or` sets `true`
(7.5.0 `StandardOperationsBoolean.java:37–39`), so the fork is inconsistent with its own
base for exactly these four.

**D7 — `isMaximizedUncertainty` does not mean what it says.**
`d == 0 || b == 0` (`uDataTypes/SBoolean.java:155–157`) is true for the dogmatic
extremes `(1,0,0,a)` and `(0,1,0,a)`. See #24.

**D8 — dead NaN guard in `certainty`.** `uDataTypes/SBoolean.java:169`,
`this.uncertainty() == (0.0D/0.0D)` is never true. See #27.

**D9 — `uncertaintyMaximized` and `uncertainOpinion` are aliases** (#6, #26); registering
both is harmless but doubles the surface to test.

**D10 — `deduceY` has six unguarded divisors** (#10). This is the operation most likely
to throw `IllegalArgumentException` out of the evaluator, or to produce a silent NaN
opinion via the direct field writes at `uDataTypes/SBoolean.java:384–386`.

---

## 8. Cross-check against the 7.5.0 `OpGeneric` contract

Executed:

```bash
cd /home/xoruser/msc-4/use-msc2026
diff -u --strip-trailing-cr \
  use-core/src/main/java/org/tzi/use/uml/ocl/expr/operations/OpGeneric.java \
  .git/reference-repositories/uncertainty/USE-Uncertainty/src/main/org/tzi/use/uml/ocl/expr/operations/OpGeneric.java
```

**Result: the only difference is the 7-line insertion of the `// Uncertainty Types`
block into `registerOperations` (fork lines 92–97).** Every contract member is
byte-identical:

| Member | 7.5.0 | Fork | Delta |
|---|---|---|---|
| `public static final int OPERATION = 0` | L34 | L34 | none |
| `public static final int SPECIAL = 3` | L36 | L36 | none |
| `public abstract String name()` | L38 | L38 | none |
| `public boolean isBooleanOperation()` (default `false`) | L40–42 | L40–42 | none |
| `public abstract int kind()` | L44 | L44 | none |
| `public abstract boolean isInfixOrPrefix()` | L46 | L46 | none |
| `public abstract Type matches(Type params[])` | L48 | L48 | none |
| `public String checkWarningUnrelatedTypes(Expression args[])` | L50 | L50 | none |
| `public abstract Value eval(EvalContext ctx, Value args[], Type resultType)` | L52 | L52 | none |
| `public String stringRep(Expression args[], String atPre)` | L54–78 | L54–78 | none |
| `registerOperation(OpGeneric, Multimap)` | L105–107 | L112–114 | body identical, line-shifted |
| `registerOperation(String, OpGeneric, Multimap)` | L115–117 | L122–124 | body identical, line-shifted |
| `registerOperations(Multimap)` | L80–98 | L80–105 | **fork adds 5 uncertainty registries** |

> **No `OpGeneric` member signature has to be adapted.** The 39 operations can be
> ported by copying the anonymous-class bodies unchanged. Note the files differ in line
> endings — 7.5.0 `OpGeneric.java` is CRLF, the fork's is LF (`file` output); use
> `--strip-trailing-cr` when diffing, and match the target repo's convention when writing.

### What *is* missing on the 7.5.0 side

The registry compiles only if these are added first. All verified by grep on
`use-core/src/main/java/org/tzi/use/uml/ocl/…`:

| Needed by | Symbol | Status in 7.5.0 |
|---|---|---|
| all 39 `matches` | `TypeFactory.mkSBoolean()` | **absent** — `TypeFactory.java` has only `mkInteger/mkUnlimitedNatural/mkReal/mkString/mkBoolean/mkEnum/mkCollection/mkSet/mkSequence/mkBag/mkOrderedSet/mkMessageType/mkOclAny/mkVoidType/mkTuple/mkSimpleType` (L66–132) |
| `toUBoolean` | `TypeFactory.mkUBoolean()` | **absent** (same list) |
| unary ops | `Type.isTypeOfSBoolean()` | **absent** from the `Type` interface (7.5.0 `type/Type.java:82–152` has no SBoolean/UBoolean entries) |
| binary ops | `Type.isKindOfSBoolean(VoidHandling)` | **absent** |
| `applyOn` | `Type.isKindOfUBoolean(VoidHandling)` | **absent** |
| all types | `TypeImpl` false-defaults for the above | **absent** (fork has them at `TypeImpl.java:204–212` for UBoolean and `374–382` for SBoolean) |
| null rejection | `VoidType.isKindOfSBoolean(h) { return h == INCLUDE_VOID; }` | **absent** (fork `VoidType.java:127–130`; 7.5.0 `VoidType.java` stops at `isKindOfAssociation`, L113) |
| `Boolean`→`SBoolean` widening | `BooleanType.isKindOfSBoolean/isKindOfUBoolean` + `conformsTo` + `allSupertypes` | **absent** (fork `BooleanType.java:49–77`) |
| new types | `SBooleanType`, `UBooleanType`, `UncertainBooleanType`, `UncertainType` | **absent** (fork `type/SBooleanType.java`, `UBooleanType.java`, `UncertainBooleanType.java`, `UncertainType.java`) |
| eval | `SBooleanValue`, `UBooleanValue`, `UncertainBooleanValue`, `UncertainValue` | **absent** (fork `value/`) |
| `isCertain`/`isUncertain` | `RealValue.valueOf(Value)` (static) | **absent** — 7.5.0 `RealValue` has only `new RealValue(double)` (L34) and `value()` (L39); the fork adds `valueOf(Value)` at `value/RealValue.java:90–101` |
| `SBooleanValue.toString` | `MathUtil.round(double,int)` | **absent** — 7.5.0 `util/MathUtil.java` has only `max/min` overloads (L35, 52, 67, 84); the fork adds `round` at L106 |

Available unchanged in 7.5.0 (no work needed): `BooleanValue.get(boolean)`
(`value/BooleanValue.java:55`), `CollectionValue.asSequence()`
(`value/CollectionValue.java:190`), `new StringValue(String)` (`value/StringValue.java:32`),
`Value.toString()` final + `toString(StringBuilder)` abstract
(`value/Value.java:153–160`), `Type.VoidHandling`, and the `ExpStdOp` strictness
machinery (`expr/ExpStdOp.java:286–313`).

### Registry style convention

7.5.0 uses **package-private top-level classes + an explicit `registerTypeOperations`
listing** (`StandardOperationsNumber.java:16–46`: `class StandardOperationsNumber { public
static void registerTypeOperations(Multimap<String, OpGeneric> opmap) { OpGeneric.registerOperation(new Op_number_add(), opmap); … } }`,
with `final class Op_number_add extends ArithOperation` at L78 etc., and shared behaviour
factored into an `abstract class ArithOperation extends OpGeneric` at L57–72).
The fork's SBoolean registry instead uses an **enum of anonymous `OpGeneric` instances**.
Both satisfy the identical `OpGeneric` contract, and `registerTypeOperations(Multimap)`
has the same signature in both. Converting to the upstream style is optional and
purely cosmetic; if it is done, the 39 `matches` bodies repeat two patterns
(`isTypeOfSBoolean` unary → `mkReal`/`mkBoolean`/`mkSBoolean`, and
`isKindOfSBoolean × isKindOfSBoolean/Collection/UBoolean` binary) that would factor
cleanly into two abstract bases, mirroring `ArithOperation`.

---

## 9. Oracle: source tree vs. the live jar

The semantics in §5 are read from
`.../uDataTypes/Libraries/Java/src/uDataTypes/SBoolean.java`. That source tree is
**newer than the jar the fork actually links against**. Evidence, from
`javap -classpath …/lib/atenearesearchgroup.uncertainty.jar uDataTypes.SBoolean`:

* The jar exposes the fusion statics under the `*BeliefFusion` names only —
  `beliefConstraintFusion`, `minimumBeliefFusion`, `majorityBeliefFusion`,
  `averageBeliefFusion`, `cumulativeBeliefFusion`, `epistemicCumulativeBeliefFusion`,
  `weightedBeliefFusion`, `consensusAndCompromiseFusion`. In the source these are
  `@Deprecated` one-line wrappers around renamed primaries (`cbFusion`, `minimumFusion`,
  `majorityFusion`, `averagingFusion`, `aleatoryCumulativeFusion`,
  `epistemicCumulativeFusion`, `weightedFusion`, `ccFusion`), and the *renamed*
  primaries do **not** exist in the jar.
* The jar has no `union(SBoolean)` / `weightedUnion(...)` (source L406–455).
* Everything the fork's `SBooleanValue` calls **is** present in the jar: `projection`,
  `projectiveDistance`, `conjunctiveCertainty`, `degreeOfConflict`,
  `uncertaintyMaximized`, `deduceY`, `toUBoolean`, `belief`, `disbelief`, `uncertainty`,
  `baseRate`, `and/or/xor/equivalent/implies/not`, `getRelativeWeight`,
  `isAbsolute/isVacuous/isCertain/isDogmatic/isMaximizedUncertainty/isUncertain`,
  `uncertainOpinion`, `certainty`, all eight fusion statics, `discount(Collection)`,
  `applyOn`, `min`, `max`. So the registry compiles and runs against the jar.

Two checks were run to test whether the jar's bodies match the source's renamed
primaries, both **positive**:

1. `javap -c` on the jar's `minimumBeliefFusion` shows the *implementation*
   (null/size-2 guard, `String MBF: Cannot fuse null opinions, or only one opinion was
   passed`, the `min`-reduce loop, final `clone()`) — byte-for-byte the shape of the
   source's `minimumFusion` (L498–508), not a delegating wrapper.
2. The jar's complete string-constant set, extracted with
   `javap -p -c … | grep -o '// String .*' | sort -u`, is exactly the set of exception
   messages and formats in the source's primaries:
   `AVF:`, `BCF: Cannot fuse null opinions…`, `BCF: Cannot fuse totally conflicting opinions`,
   `CCF: Base rates for CC Fusion must be the same`, `CCF: Cannot fuse null opinions…`,
   `Create Dogmatic Opinion: …`, `CreateVacuousOpinion: …`,
   `Discountion operator parameter cannot be null`, `MBF:`, `MajBF:`, `WBF:`, `aCBF:`, `eCBF:`,
   `applyOn(): baseRate must be between 0 and 1`, the three `SBoolean constructor…` messages,
   and `SBoolean(%5.3f, %5.3f, %5.3f, %5.3f)`. Nothing from `union`/`weightedUnion`
   (`union: invalid argument`, `Opinions must not be empty`) appears.
3. The jar's `epistemicCumulativeBeliefFusion` ends with
   `invokevirtual uncertaintyMaximized:()LuDataTypes/SBoolean;` + `areturn`, confirming
   the post-maximisation described in #33.

**Residual risk / UNVERIFIABLE**: the numeric bodies of `averageBeliefFusion`,
`weightedBeliefFusion`, `consensusAndCompromiseFusion`, `uncertaintyMaximized`,
`deduceY`, `and`, `or` and `applyOn` were **not** compared instruction-by-instruction
against the source. The source carries visible evidence of revision in exactly those
places (`averagingFusion` has an "OLD VERSION" block commented out at source L606–628;
`uncertaintyMaximized` carries `// Replaced by another version` at L300–301), so a
numeric divergence between the 2021-02-24 jar and this source tree is possible for them.
Ground rule 1 forbids putting the reference jar on a build classpath, so **no execution
oracle was run**; only static disassembly was used. If a port needs exact numeric
agreement for those eight, it must be established separately against the jar.

**Also UNVERIFIABLE from the fork itself**: there is no test coverage for these
operations. Executed:
`grep -rln 'SBoolean' .../USE-Uncertainty/src/test` returns exactly one file,
`src/test/org/tzi/use/uml/ocl/type/TypeTest.java`, and
`grep -rln 'minimumBeliefFusion\|conjunctiveCertainty\|deduceY' --include='*.use' --include='*.soil' --include='*.cmd' … .`
over the whole fork returns only the two production files
(`value/SBooleanValue.java`, `operations/StandardOperationsSBoolean.java`).
**No fork-side test or example model exercises any of the 39 operations.** The port
therefore has no historical oracle to diff against and must build its own.

---

## 10. Reachability: how an `SBoolean` receiver comes into existence

For completeness, since every operation here needs one. The literal syntax is
`SBoolean(b, d, u, a)` — a four-argument literal, distinct from `UBoolean(v, c)`:

* `parser/ocl/OCL.g` and the generated `parser/soil/SoilParser.java:5378` /
  `parser/testsuite/TestSuiteParser.java:3793` list
  `'SBoolean' LPAREN additiveExpression COMMA … COMMA … COMMA … RPAREN`
  building `ASTSBooleanLiteral`.
* The type keyword `'SBoolean'` is part of `uncertaintyType`
  (`SoilParser.java:6563`: `'UReal' | 'UInteger' | 'UBoolean' | 'UString' | 'SBoolean'`).
* `ExpConstSBoolean` (`expr/ExpConstSBoolean.java:17, 49`) builds the value via
  `SBooleanValue.Builder` (`value/SBooleanValue.java:28–69`), which returns the shared
  `TRUE`/`FALSE` constants for `(1,0,0,1)`/`(0,1,0,1)`.
* `=` and `<>` on SBoolean operands are handled outside this file, in
  `StandardOperationsAny` (fork L50–59 and L200–209, which return `TypeFactory.mkSBoolean()`
  when either operand is an `SBooleanType`), dispatching to
  `SBooleanValue.uEquals` (→ `equivalent`) and `uDistinct` (→ `xor`).

These are named only as cross-references; they are outside the scope of this file and
should be specified in the value/type/grammar spec parts.

---

## Independent refutation

Written by a second reader who derived the table from
`.../operations/StandardOperationsSBoolean.java` (all 1502 lines),
`.../value/SBooleanValue.java` (all 476 lines) and
`.../uDataTypes/SBoolean.java` **before** reading anything above. Every claim below
names a file+line or the command that produced it. Nothing was taken from `origin/main`.

### A. What the refutation confirms

**The operation count is right: 39.** Independently reproduced three ways, all `39`:

```bash
cd /home/xoruser/msc-4/use-msc2026/.git/reference-repositories/uncertainty/USE-Uncertainty/src/main/org/tzi/use/uml/ocl/expr/operations
grep -cE '^ {4}[A-Z][A-Z_0-9]*\(new OpGeneric' StandardOperationsSBoolean.java   # 39 enum constants
grep -cE '^ {12}return "'                       StandardOperationsSBoolean.java   # 39 name() bodies
grep -oE '^ {12}return "[a-zA-Z]+";'            StandardOperationsSBoolean.java | sort -u | wc -l   # 39 DISTINCT names
```

The third one is the check §1 does not make: **all 39 names are distinct**, so no
operation is registered twice and nothing is hidden behind a shared name. Six further
constants are commented out (L1282, 1316, 1349, 1383, 1416, 1449) and the naive
`grep -c 'new OpGeneric()'` returning 45 is confirmed as the trap §1 says it is.

The **whole of §4's 39-row table** — OCL name, enum constant, arity, argument predicate,
result type, form — was re-derived independently by pairing the 39 `name()` bodies with
the 39 `TypeFactory.mk*` lines:

```bash
paste <(grep -n 'return "' StandardOperationsSBoolean.java | sed -n 1,39p) \
      <(grep -n 'TypeFactory.mk' StandardOperationsSBoolean.java | grep -v '^[0-9]*://' | sed -n 1,39p)
```

It matches row for row. Also independently confirmed: the arity-1 / `isTypeOfSBoolean()`
vs. arity-2/3 / `isKindOfSBoolean(EXCLUDE_VOID)` split (`grep -n 'params.length'`, 39 hits);
`not` is the sole `isInfixOrPrefix() == true` (L418); the `isKindOfSBoolean` lattice table
in §2 is *complete* (`grep -rn 'public boolean isKindOfSBoolean' type/*.java` returns exactly
SBooleanType:18, UBooleanType:23, BooleanType:55, VoidType:128, TypeImpl:375 — no sixth
override); `isKindOfReal` is overridden only by IntegerType:53, RealType:50, VoidType:48
(so §5 #22's "Real or Integer, not UReal" is right); `SBooleanType` does **not** override
`isKindOfUBoolean` (it extends `UncertainBooleanType` → `UncertainType` → `BasicType`, none
of which override it), so §5 #39's "`applyOn` rejects an SBoolean second argument" holds.

Also re-verified line-exact and correct: **D1** (`conjunctiveCertainty` L262 and
`degreeOfConflict` L295 declare `mkSBoolean()` while `SBooleanValue.java:171/176` return
`RealValue`); the `CUMULATIVEBELIEFFUSION` → `"aleatoryCumulativeBeliefFusion"` name
mismatch (L1018/L1022); the receiver-prepend in all eight fusions
(`SBooleanValue.java:345, 358, 371, 384, 397, 410, 423, 436`) versus its **absence** in
`discount` (L449) — **D4**; `OpGeneric` registration at L97 inside the L92–97 block;
`ExpStdOp` first-match dispatch at L128–135 and the OPERATION strictness loop at L293–314;
and the uDataTypes citations for `not` (L234–242), `and` (L244–258), `or` (L260–274),
`xor` (L286–296), `equivalent` (L281–284), `implies` (L276–279), `uncertaintyMaximized`
(L298–313), `applyOn` (L185–201), `min`/`max` (L1538–1544), `toUBoolean` (L1562–1564),
`toString` (L1558–1560), the `isX` predicates (L139–161), the dead NaN guard (L169),
and the fusion guards (L474–475, L499–500, L527–528, L560–561, L651–656, L751–756,
L830, L952–953, L1457–1459).

**No operation is missed, none is invented, no arity is wrong, no argument or result type
is wrong.** The disagreements below are all in the semantic prose of §5 and §7.

### B. Substantive discrepancies

**R1 — §5 #16 `xor`, wrong special-case result.**
The text says "Two vacuous opinions give `(0, 0, 1, 0)`". Wrong. `xor`'s base rate is
`|a₁ − a₂|` (`uDataTypes/SBoolean.java:291`). Two vacuous opinions are `(0,0,1,a₁)` and
`(0,0,1,a₂)`, giving `b = |0−0| = 0`, `u = 1·1 = 1`, `d = 1−0−1 = 0`, and
`a = |a₁ − a₂|`. The result is **`(0, 0, 1, |a₁ − a₂|)`**, which equals `(0,0,1,0)` only
when the two base rates coincide. Nothing in `isVacuous()` (`SBoolean.java:143–145`,
`u == 1.0`) constrains `a`.

**R2 — §5 #10 / §7 D10 `deduceY`, the divisor list is wrong in both directions.**
The text lists six "unguarded" denominators, among them `(yGivenX.b − yGivenNotX.b)`.

* `(yGivenX.b − yGivenNotX.b)` appears **only** in case II.B.1 (`SBoolean.java:349`), whose
  own branch condition at L346 is `(yGivenX.b > yGivenNotX.b) && …`. It is therefore
  **strictly positive whenever that division executes** — it is not a hazard. Listing it
  is a false positive.
* Symmetrically, `(yGivenX.d − yGivenNotX.d)` at L362 (case III.A.1) is guarded by
  `yGivenX.d > yGivenNotX.d` at L359 — also not a hazard, correctly absent.
* **Missed hazard**: `(yGivenNotX.b − yGivenX.b)` at **L381** (case III.B.2) is divided by
  under the guard `yGivenX.b <= yGivenNotX.b` (L378), which **permits equality**. This can
  be zero for admissible inputs and is not in the list.

The correct statement: of the four difference-divisors, exactly **two** can be zero inside
their own branch — `(yGivenNotX.d − yGivenX.d)` at L343 under the `<=` guard at L340
(the document has this one) and `(yGivenNotX.b − yGivenX.b)` at L381 under the `<=` guard
at L378 (the document does not). The four non-difference divisors
`(b + a·u)`, `(d + (1−a)·u)`, `y.a`, `(1 − y.a)` are genuinely unguarded, as stated.

**R3 — §5 #10, missed special case: the eight `K` blocks are sequential `if`s and do not
partition.** The document calls it "an eight-way case analysis … selected by", which
implies exclusivity. The eight blocks at L335, L340, L346, L352, L359, L366, L372, L378 are
independent `if` statements, **not `else if`**, so a later match silently overwrites `K`.
The case-II family does partition (all four compare `pyxhat` against the *same* expression
`yGivenNotX.b + y.a·(1 − yGivenNotX.b − yGivenX.d)`, L336/341/347/353). The case-III family
**does not**: III.A.1 compares against `yGivenX.b + y.a·(1 − yGivenNotX.b − yGivenX.d)`
(L360) while III.A.2, III.B.1 and III.B.2 compare against
`yGivenX.b + y.a·(1 − yGivenX.b − yGivenNotX.d)` (L367, L373, L379) — a **different**
expression (`yGivenNotX.b`/`yGivenX.d` vs. `yGivenX.b`/`yGivenNotX.d`). For inputs where
the two thresholds straddle `pyxhat`, III.A.1 and III.B.1 can both fire and III.B.1's
assignment wins by textual order; for the opposite straddle **neither** fires and `K`
stays `0`. A bug-compatible port must preserve the source order of the eight `if`s; the
document does not say so.

**R4 — §5 #10, consequence of R3.** "`K` defaults to `0` (case I, L332) when neither of the
two monotonicity patterns holds" is incomplete: per R3, `K` also stays `0` when the
case-III monotonicity pattern *does* hold but the mismatched thresholds leave all four
sub-conditions false.

**R5 — §5 #10, two oracle quirks in the `K` numerators are not flagged.**
Case III.A.2 (L368) computes `(bIy − yGivenX.d)` — the **belief** accumulator minus a
**disbelief** — and case III.B.1 (L374) computes `(dIy − yGivenNotX.b)` — the **disbelief**
accumulator minus a **belief**. Their type-consistent siblings are II.A.1 L337
`(bIy − yGivenNotX.b)` and II.B.2 L354 `(dIy − yGivenX.d)`. These read as transcription
errors in the oracle. Either way they are load-bearing for a verbatim port and belong in
§5 #10 next to the other `deduceY` traps.

**R6 — §2, the registration-order competitor list is incomplete.**
§2 names only `StandardOperationsBoolean` and `StandardOperationsNumber` as earlier
registries. Four more sit between them and SBoolean: `StandardOperationsUReal`
(`OpGeneric.java:93`), `StandardOperationsUBoolean` (L94), `StandardOperationsUInteger`
(L95), `StandardOperationsUString` (L96), with SBoolean last at L97.
`StandardOperationsUBoolean` registers `toString` (L85), `and` (L360), `not` (L503) and
also `or`/`xor`/`implies` — the same names, ahead of SBoolean.
The conclusion still holds, but only for a reason §2 never checks: `UBoolean::and`
(`matches` L370) and `UBoolean::not` (`matches` L513) test
`isKindOfUBoolean(INCLUDE_VOID)`, which is `false` for `SBooleanType`
(`TypeImpl.java:210`, no override anywhere up the `UncertainBooleanType → UncertainType →
BasicType` chain), so no SBoolean call is shadowed by them. A port that gives `SBooleanType`
an `isKindOfUBoolean` override — an easy thing to do while wiring the type lattice — would
silently reroute `and/or/xor/implies/not/toString` on SBoolean receivers to the UBoolean
registry. That is worth stating explicitly.

### C. Cosmetic corrections

**R7 — §7 D6** lists "lines 448, 481, 514, 547, 580" as the `isInfixOrPrefix()` sites of
"those four ops" (`and`, `or`, `xor`, `implies`). That is five line numbers; **547 is
`equivalent`**, which is not grammar-infix (no `'equivalent'` production in `OCL.g`) and is
correctly typed as a plain dot-call in the §4 table. The four are 448, 481, 514, 580.

**R8 — §5 #6** cites "`// Replaced by another version` at L300–301". That comment is on
**L301 only**; L300 is `//return this.increasedUncertainty();`.

### D. Verdict

Table: **agrees, 39 operations, no missing/invented/mis-typed rows.**
Prose: **six substantive corrections (R1–R6)**, five of them concentrated in `deduceY`,
which the document itself calls the highest-risk operation to port and which turns out to
be under-specified in exactly the places that decide whether a ported `deduceY` reproduces
the oracle.
