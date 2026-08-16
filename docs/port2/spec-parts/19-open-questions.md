# 19 — Open Questions (Refuter pass)

Scope: three questions answered **only** from the historical fork at
`/home/xoruser/msc-4/use-msc2026/.git/reference-repositories/uncertainty/USE-Uncertainty`.
All paths below are relative to that fork root unless written absolutely.

Method note / limits of proof:
- I did **not** run the fork. It is an Ant/Java-1.7 project and Maven is off-limits per the
  ground rules. Every claim below is **static**: file, line, and the exact grep that produced it.
  Where I state a runtime consequence it is labelled `DERIVED` and the derivation chain is given
  in full so it can be checked by reading.
- Target-repo baseline (read-only check, not evidence about the fork):
  `ls /home/xoruser/msc-4/use-msc2026/use-core/src/main/java/org/tzi/use/uml/ocl/{value,type}/ | grep -iE "uncertain|ubool|sbool|ureal|uinteger|ustring"`
  returns **empty** — the 7.5.0 target currently contains none of these classes.
- I did not read `origin/main`. Nothing here is derived from the earlier port.

---

## QUESTION 1 — Is `ExpDefSBoolean` needed?

### Verdict

**No. It is dead code in the fork, and it is dead on two independent grounds.**
It is not reachable from `UBoolean` / `UReal` / `UInteger` / `UString` behaviour.
It is not reachable from `SBoolean` behaviour either — it is not reachable at all.
**Do not port it.**

### The file (all 48 lines read)

`src/main/org/tzi/use/uml/ocl/expr/ExpDefSBoolean.java` — a one-argument
`SBoolean(<expr>)` coercion expression: takes a Boolean-ish expression, yields
`SBooleanValue.valueOf(boolValue)`.

### Ground 1 — nothing constructs it, because its AST node is orphaned

Complete reference set for the class and its visitor hook:

```
$ grep -rn "ExpDefSBoolean\|visitDefSBoolean" . | grep -v "^\./lib" | sort
src/main/org/tzi/use/analysis/coverage/AbstractCoverageVisitor.java:113:	public void visitDefSBoolean(ExpDefSBoolean expDefSBoolean) {
src/main/org/tzi/use/analysis/metrics/AbstractMetricVisitor.java:121:	public void visitDefSBoolean(ExpDefSBoolean expDefSBoolean) {
src/main/org/tzi/use/parser/ocl/ASTSBooleanDefExpression.java:25:            result = new ExpDefSBoolean(exprBool);
src/main/org/tzi/use/parser/ocl/ASTSBooleanDefExpression.java:5:import org.tzi.use.uml.ocl.expr.ExpDefSBoolean;
src/main/org/tzi/use/uml/ocl/expr/ExpDefSBoolean.java:12:    public ExpDefSBoolean(Expression eBool) {
src/main/org/tzi/use/uml/ocl/expr/ExpDefSBoolean.java:46:        visitor.visitDefSBoolean(this);
src/main/org/tzi/use/uml/ocl/expr/ExpDefSBoolean.java:8:public class ExpDefSBoolean extends Expression {
src/main/org/tzi/use/uml/ocl/expr/ExpressionVisitor.java:40:	void visitDefSBoolean(ExpDefSBoolean expDefSBoolean);
```

The **only** construction site is `ASTSBooleanDefExpression.java:25`. And that AST class is itself
never instantiated anywhere in the fork:

```
$ grep -rn "ASTSBooleanDefExpression" . | grep -v "^\./lib"
src/main/org/tzi/use/parser/ocl/ASTSBooleanDefExpression.java:11:public class ASTSBooleanDefExpression extends ASTExpression {
src/main/org/tzi/use/parser/ocl/ASTSBooleanDefExpression.java:15:    public ASTSBooleanDefExpression(ASTExpression eBool) {
```

Its own declaration and its own constructor. Nothing else. No grammar, no generated parser,
no test.

### Ground 1b — the surface syntax it would implement does not exist in any grammar

`ExpDefSBoolean.toString` (line 37) prints `SBoolean(<one expr>)`. Every grammar in the fork
defines exactly one `SBoolean(...)` production and it is the **four-argument literal**, which
routes to `ASTSBooleanLiteral` → `ExpConstSBoolean`, never to `ASTSBooleanDefExpression`:

```
$ grep -rn "SBoolean" --include=*.g --include=*.gpart src/main
src/main/org/tzi/use/parser/base/OCLBase.gpart:499:    | 'SBoolean' LPAREN ubve=additiveExpression COMMA udve=additiveExpression COMMA uuve=additiveExpression COMMA uave=additiveExpression RPAREN
src/main/org/tzi/use/parser/base/OCLBase.gpart:500:       { $n = new ASTSBooleanLiteral($ubve.n, $udve.n, $uuve.n, $uave.n); }
src/main/org/tzi/use/parser/base/OCLBase.gpart:633:    name=('UReal'|'UInteger'|'UBoolean'|'UString' | 'SBoolean') { $n = new ASTSimpleType($name); }
```
(the same two productions are copied verbatim into all six generated grammars:
`OCL.g:564/698`, `USE.g:1073/1207`, `Soil.g:1109/1243`, `Generator.g:1340/1474`,
`ShellCommand.g:862/996`, `TestSuite.g:671/805`.)

And in the generated parser, only the literal survives:

```
$ grep -n "ASTSBooleanLiteral\|ASTSBooleanDefExpression" src/main/org/tzi/use/parser/ocl/OCLParser.java
3258:                    if ( state.backtracking==0 ) { n = new ASTSBooleanLiteral(ubve, udve, uuve, uave); }
```

There is **no** `ASTSBooleanDefExpression` in any generated parser. The `.tokens` files carry
only the one `SBoolean` token, consumed by the 4-arg rule.

Contrast: the analogous one-argument coercions for the four U-types do not exist as `ExpDef*`
classes at all. The full `ExpDef*`/`ExpConst*` inventory is:

```
$ ls src/main/org/tzi/use/uml/ocl/expr/ | grep -E "^ExpDef|^ExpConst"
ExpConstBoolean.java  ExpConstEnum.java  ExpConstInteger.java  ExpConstReal.java
ExpConstSBoolean.java ExpConstString.java ExpConstUBoolean.java ExpConstUInteger.java
ExpConstUReal.java    ExpConstUString.java ExpConstUnlimitedNatural.java
ExpDefSBoolean.java
```

`ExpDefSBoolean` is the sole `ExpDef*` in the whole expression package. It is a one-off with no
peers and no callers.

### Ground 2 — even if it were reachable, it cannot work

Two defects show it was never once executed.

**(a) The type guard is inverted.** `ExpDefSBoolean.java:16-17`:

```java
if (eBool.type().isKindOfUBoolean(Type.VoidHandling.EXCLUDE_VOID))
    throw new RuntimeException("Expression Boolean or UBoolean expected");
```

The message says it *expects* Boolean or UBoolean, and the condition throws precisely when the
argument *is* one. `BooleanType.isKindOfUBoolean` returns `true`
(`src/main/org/tzi/use/uml/ocl/type/BooleanType.java:49-52`) and `UBooleanType.isKindOfUBoolean`
returns `true` (`src/main/org/tzi/use/uml/ocl/type/UBooleanType.java:17-20`).
`DERIVED`: every intended input throws; every input that does not throw is a non-boolean, for
which `SBooleanValue.valueOf` returns `null` (`SBooleanValue.java:71-88` handles only
`isSBoolean` / `isUBoolean` / `isBoolean` and falls through to `ret = null`), so `eval` returns
a bare `null` `Value`.

**(b) `eval` calls `ctx.enter` and never `ctx.exit`.** `ExpDefSBoolean.java:23-28`:

```java
ctx.enter(this);
boolValue = eBool.eval(ctx);
return SBooleanValue.valueOf(boolValue);
```

Compare the sibling that *is* wired up, `ExpConstSBoolean.java:38-63`, which does
`ctx.enter(this)` … `ctx.exit(this, result)`. An unbalanced eval-context stack would surface
immediately in any evaluation-tree output; it never did, because the class never ran.

### Answer to the question as posed

> is it reachable from UBoolean/UReal/UInteger/UString behaviour, or only from SBoolean?

**Neither.** It is reachable from nothing. Its only inbound edge (`ASTSBooleanDefExpression`) is
itself unreachable, and no grammar emits the surface syntax it implements. Its *semantic* affinity
is to `SBoolean` (it produces `SBooleanValue` and types as `TypeFactory.mkSBoolean()`), so it is
in any case on the SBoolean side of the line, not on the four-U-types side.

**Recommendation:** omit `ExpDefSBoolean`, `ASTSBooleanDefExpression`, and the
`ExpressionVisitor.visitDefSBoolean` hook. Nothing in the fork observes their absence. The port
plan's open question is closed: **not needed**.

---

## QUESTION 2 — True dependency footprint of SBoolean

### Verdict

The plan's assumption is **HALF WRONG**, and the wrong half is dangerous.

- **Correct half:** none of the four U-types' *value* classes and none of their *operation
  registries* mention SBoolean. No U-type operation returns, accepts, or constructs an
  `SBooleanValue`. `StandardOperationsSBoolean.java` (1502 lines) is genuinely severable.
- **Wrong half:** the dependency runs in the *other* direction and is real. `UBooleanType` and
  `BooleanType` each declare SBoolean as a supertype and each answer `isKindOfSBoolean() == true`.
  **28 of the SBoolean operations are guarded by `isKindOfSBoolean`, not `isTypeOfSBoolean`**, so
  they match `UBoolean` and plain `Boolean` receivers. Several of those op *names* are not
  otherwise defined for booleans, so they are **not shadowed** and are genuinely callable on
  `UBoolean`/`Boolean`, returning `SBoolean`.

The scope does not have to expand to *port* SBoolean, but the port **must** make a conscious
decision about `UBooleanType.isKindOfSBoolean` / `conformsTo` / `allSupertypes`, or it will
silently change UBoolean's observable behaviour relative to the fork.

### 2.1 — The four U-types do not reference SBoolean. Confirmed negative.

```
$ cd src/main/org/tzi/use/uml/ocl/expr/operations
$ grep -n "SBoolean" StandardOperationsUBoolean.java StandardOperationsUReal.java \
      StandardOperationsUInteger.java StandardOperationsUString.java
   (no output)
```

```
$ grep -rn "SBooleanValue" --include=*.java src \
    | grep -v "value/SBooleanValue.java" | grep -v "StandardOperationsSBoolean.java"
src/main/org/tzi/use/uml/ocl/expr/ExpConstSBoolean.java:5   (import)
src/main/org/tzi/use/uml/ocl/expr/ExpConstSBoolean.java:49  (new SBooleanValue.Builder())
src/main/org/tzi/use/uml/ocl/value/Value.java:105-106       (javadoc only)
src/main/org/tzi/use/uml/ocl/expr/ExpDefSBoolean.java:5,28  (the dead class from Q1)
src/main/org/tzi/use/uml/ocl/expr/operations/StandardOperationsAny.java:50,58,200,208  (SBooleanType, not SBooleanValue — see 2.3)
```

The oracle-library import is a single point:

```
$ grep -rn "import uDataTypes.SBoolean;" --include=*.java src
src/main/org/tzi/use/uml/ocl/value/SBooleanValue.java:6
```

One file. `UBooleanValue.java:3`, `URealValue.java:3-4`, `UIntegerValue.java:3`,
`UStringValue.java:3`, `StandardOperationsUReal.java:3`, `StandardOperationsNumber.java:3`
import only `uDataTypes.UBoolean` / `UReal` / `UInteger` / `UString`.

`StandardOperationsSBoolean` has exactly one registration site and no other caller:

```
$ grep -rn "StandardOperationsSBoolean" --include=*.java src
src/main/org/tzi/use/uml/ocl/expr/operations/OpGeneric.java:97:  StandardOperationsSBoolean.registerTypeOperations(opmap);
src/main/org/tzi/use/uml/ocl/expr/operations/StandardOperationsSBoolean.java:15: (declaration)
```

**Delete line `OpGeneric.java:97` and the entire 1502-line registry is unreferenced.**

### 2.2 — The four U-types' `uEquals` return `UBooleanValue`, never `SBooleanValue`

The declared return type is the abstract base `UncertainBooleanValue` in all four, which reads at
first glance like an SBoolean escape hatch. Every body returns a `UBooleanValue`:

| type | site | returns |
|---|---|---|
| `URealValue` | `src/main/org/tzi/use/uml/ocl/value/URealValue.java:136-148` | `UBooleanValue.valueOf(result)` (line 147) |
| `UIntegerValue` | `src/main/org/tzi/use/uml/ocl/value/UIntegerValue.java:38-42` | delegates to `URealValue.uEquals` (line 41) |
| `UStringValue` | `src/main/org/tzi/use/uml/ocl/value/UStringValue.java:53-63` | `UBooleanValue.FALSE` / `UBooleanValue.valueOf(...)` (lines 58, 60) |
| `UBooleanValue` | `src/main/org/tzi/use/uml/ocl/value/UBooleanValue.java:274-284` | `UBooleanValue` local `result` (line 276) |

`UncertainValue.uDistinct` (`src/main/org/tzi/use/uml/ocl/value/UncertainValue.java:37-44`) just
calls `uEquals(...).not()`; `UBooleanValue.not()` (`UBooleanValue.java:315-318`) returns
`valueOf(uBoolean.not())`, i.e. a `UBooleanValue`. No SBoolean anywhere on this path.

Closing the loop in the reverse direction: `UBooleanValue.valueOf(Value)`
(`src/main/org/tzi/use/uml/ocl/value/UBooleanValue.java:122-127`) accepts only `isUBoolean()` and
`isBoolean()` — an `SBooleanValue` yields `null`. And `UBooleanValue.uEquals` guards on
`other.type().isKindOfUBoolean(EXCLUDE_VOID)`; `SBooleanType` does **not** override
`isKindOfUBoolean`, so it inherits `false`. `SBooleanValue` therefore cannot enter the UBoolean
value path.

### 2.3 — Where SBoolean *does* touch the U-type code paths — the real call graph

**(A) Type lattice — `UBoolean` and `Boolean` declare SBoolean a supertype.**

```
src/main/org/tzi/use/uml/ocl/type/UBooleanType.java:22-25
    @Override public boolean isKindOfSBoolean(VoidHandling h) { return true; }
src/main/org/tzi/use/uml/ocl/type/UBooleanType.java:33-39
    allSupertypes() -> { UBoolean, OclAny, SBoolean }        // line 37 adds TypeFactory.mkSBoolean()
src/main/org/tzi/use/uml/ocl/type/UBooleanType.java:41-44
    conformsTo(other) -> ... || other.isTypeOfSBoolean()     // line 44

src/main/org/tzi/use/uml/ocl/type/BooleanType.java:54-57
    @Override public boolean isKindOfSBoolean(VoidHandling h) { return true; }
src/main/org/tzi/use/uml/ocl/type/BooleanType.java:63-64
    conformsTo(other) -> ... || other.isTypeOfSBoolean()
src/main/org/tzi/use/uml/ocl/type/BooleanType.java:70-78
    allSupertypes() -> { OclAny, UBoolean, SBoolean, Boolean }  // line 75
```

`URealType`, `UIntegerType`, `UStringType` do **not** override `isKindOfSBoolean` — they inherit
`TypeImpl.isKindOfSBoolean → false` (`src/main/org/tzi/use/uml/ocl/type/TypeImpl.java:374-377`)
and `isTypeOfSBoolean → false` (`TypeImpl.java:379-382`). **The leak is confined to the boolean
family.** This is confirmed by the file-level grep: `SBoolean` appears in `UBooleanType.java` and
`BooleanType.java` but in none of `URealType.java`, `UIntegerType.java`, `UStringType.java`.

`DERIVED` — does this corrupt least-common-supertype? No.
`TypeImpl.getLeastCommonSupertype` (`TypeImpl.java:81-146`) intersects `allSupertypes()` then at
lines 126-143 picks the element that conforms to all the others.
`LCS(Boolean, UBoolean)`: intersection `{OclAny, UBoolean, SBoolean}`; `UBoolean` conforms to all
three (`UBooleanType.conformsTo` line 44 → `equals` ✓, `isTypeOfOclAny` ✓, `isTypeOfSBoolean` ✓),
whereas `SBoolean` does not conform to `UBoolean` (`SBooleanType.java:31-34`). So `UBoolean` wins.
**Removing SBoolean from these two `allSupertypes()` sets does not change any LCS result among
Boolean/UBoolean.** That is the escape hatch for a narrow port.

**(B) `=` and `<>` typing rules mention `SBooleanType` explicitly.**

`src/main/org/tzi/use/uml/ocl/expr/operations/StandardOperationsAny.java`, `Op_equal.matches`
lines 48-68 and `Op_notequal.matches` lines 197-217, identical shape:

```java
boolean someOfThemIsUncertaintyValue = params[1] instanceof UncertainType || params[0] instanceof UncertainType;
boolean someOfThemIsSBooleanValue    = params[1] instanceof SBooleanType  || params[0] instanceof SBooleanType;   // :50, :200
...
    if (someOfThemIsSBooleanValue) result = TypeFactory.mkSBoolean();   // :58-59, :208-209
    else                           result = TypeFactory.mkUBoolean();
```

This is a *guarded* dependency: the SBoolean branch fires only when an operand is already
statically `SBooleanType`. With no SBoolean literals and no SBoolean-typed model features,
`params[i] instanceof SBooleanType` is never true and the `mkUBoolean()` branch always runs.
`SBooleanType` is nevertheless a **compile-time** dependency of `StandardOperationsAny` (import at
`StandardOperationsAny.java:5`). A narrow port must either keep `SBooleanType` as a class or strip
these four lines.

The corresponding `eval` side (`StandardOperationsAny.java:89-92`, `:224-227` →
`evalUncertainBooleanResult` at `:97-...` and `:232-...`) is typed `UncertainBooleanValue` and
dispatches through `UncertainValue.uEquals`, which per 2.2 yields `UBooleanValue` for all four
U-types. No SBoolean at runtime on that path.

**(C) — THE ACTUAL PROBLEM. 28 SBoolean ops are `isKindOf`-guarded and match UBoolean/Boolean.**

Guard census in `src/main/org/tzi/use/uml/ocl/expr/operations/StandardOperationsSBoolean.java`:

```
$ grep -c "isTypeOfSBoolean"  StandardOperationsSBoolean.java   -> 18   (strict: SBoolean only)
$ grep -c "isKindOfSBoolean"  StandardOperationsSBoolean.java   -> 45   (28 live + 17 in commented-out blocks, lines 1301-1469)
```

The strict ones (`isTypeOfSBoolean`) are safe — `UBooleanType` does not override
`isTypeOfSBoolean`, so it inherits `false` from `TypeImpl.java:379-382`. These include
`projection` (:37), `belief` (:68), `disbelief`, `baseRate`, `uncertainty`, `not` (:423),
`toUBoolean` (:361), `toString`, and the `is*` predicates.

The loose ones (`isKindOfSBoolean(EXCLUDE_VOID)`) **do** match `UBooleanType` (line 22-25 above)
and `BooleanType` (line 54-57 above). Live sites:

```
StandardOperationsSBoolean.java:227-228  projectiveDistance
StandardOperationsSBoolean.java:260-261  conjunctiveCertainty
StandardOperationsSBoolean.java:293-294  degreeOfConflict
StandardOperationsSBoolean.java:326-328  deduceY               (3-arg)
StandardOperationsSBoolean.java:454-455  and
StandardOperationsSBoolean.java:487-488  or
StandardOperationsSBoolean.java:520-521  xor
StandardOperationsSBoolean.java:553-554  equivalent
StandardOperationsSBoolean.java:586-587  implies
StandardOperationsSBoolean.java:905      minimumBeliefFusion
StandardOperationsSBoolean.java:938      majorityBeliefFusion
StandardOperationsSBoolean.java:971      beliefConstraintFusion
StandardOperationsSBoolean.java:1004     averageBeliefFusion
StandardOperationsSBoolean.java:1037     aleatoryCumulativeBeliefFusion
StandardOperationsSBoolean.java:1070     epistemicCumulativeBeliefFusion
StandardOperationsSBoolean.java:1103     weightedBeliefFusion
StandardOperationsSBoolean.java:1136     consensusAndCompromiseFusion
StandardOperationsSBoolean.java:1169     discount
StandardOperationsSBoolean.java:1202-1203 min
StandardOperationsSBoolean.java:1235-1236 max
StandardOperationsSBoolean.java:1268     applyOn
```

Resolution is **first match in registration order**, not best match:

```
src/main/org/tzi/use/uml/ocl/expr/ExpStdOp.java:56,60-61   opmap = ArrayListMultimap (insertion-ordered)
src/main/org/tzi/use/uml/ocl/expr/ExpStdOp.java:127-134
        for (OpGeneric op : ops) {
            Type t = op.matches(params);
            if (t != null) { ...; return new ExpStdOp(op, args, t); }   // first hit wins
        }
```

Registration order (`src/main/org/tzi/use/uml/ocl/expr/operations/OpGeneric.java:88-97`):
`...Boolean(:90) ... UReal(:93), UBoolean(:94), UInteger(:95), UString(:96), SBoolean(:97)`.

So the SBoolean registry is registered **last**, and any op name it shares with an earlier registry
is shadowed for boolean receivers. Name sets:

```
UBoolean ops: and confidence equalsC equivalent implies not or setConfidence setValue
              toBoolean toBooleanC toString value xor
Boolean  ops: and implies not or toString xor
Number   ops: abs div floor max min mod round toString
```

`and / or / xor / implies / equivalent` are shadowed — `StandardOperationsUBoolean`'s versions
(matchers at `StandardOperationsUBoolean.java:369-372 (and)`, `:439-443 (or)`,
`:512-514 (not)`, `:578-582 (implies)`, `:617-621 (xor)`, all returning `TypeFactory.mkUBoolean()`)
are found first. Good.

**But these SBoolean op names have no boolean-side competitor at all:**

`projectiveDistance`, `conjunctiveCertainty`, `degreeOfConflict`, `deduceY`, `discount`, `applyOn`,
and all nine `*Fusion` operations — plus `min` and `max`.

`min`/`max` are the sharpest case, because they *look* shadowed by `StandardOperationsNumber`
(registered at `OpGeneric.java:88`) but are not: `StandardOperationsNumber`'s `min` (name at
`StandardOperationsNumber.java:791`) matches only number types, so a `(UBoolean, UBoolean)` or
`(Boolean, Boolean)` argument list falls through it and reaches SBoolean's `min` at
`StandardOperationsSBoolean.java:1202-1203`, which returns `TypeFactory.mkSBoolean()`.

Its `eval` (`StandardOperationsSBoolean.java:1207-1211`) then does
`SBooleanValue.valueOf(args[0])` — and `SBooleanValue.valueOf`
(`src/main/org/tzi/use/uml/ocl/value/SBooleanValue.java:71-88`) **deliberately** coerces:

```java
if      (value.isSBoolean()) ret = (SBooleanValue) value;
else if (value.isUBoolean()) { UBooleanValue ub = (UBooleanValue) value;
                               ret = new SBooleanValue(new SBoolean(ub.getuBoolean())); }   // :76-78
else if (value.isBoolean())  ret = ((BooleanValue) value).value() ? TRUE : FALSE;           // :79-85
```

`DERIVED` (chain: `UBooleanType.isKindOfSBoolean`→true ⟹ `matches` at :1202 returns
`mkSBoolean()` ⟹ `ExpStdOp.create` returns that op ⟹ `eval` coerces via `valueOf` :76-78):
**in the fork, `UBoolean(true,0.7).min(UBoolean(true,0.3))` and even `true.min(false)` are legal
OCL and evaluate to an `SBoolean`.** Likewise
`UBoolean(true,0.7).minimumBeliefFusion(UBoolean(true,0.3))`.

I could not execute this (no Maven, Ant project). It is a reading-level derivation; every link is
cited above and can be re-checked by reading those five sites.

### 2.4 — Corrected assumption, and what the port must actually do

The plan's phrasing "port SBoolean only as deep as the other four types require" is right in
spirit but wrong about *what* the four require. The four types require **zero** SBoolean
*behaviour*. What the fork's boolean types require is a **type-lattice entry**, and that entry is
what drags 28 operations into reach.

Three coherent options, all consistent with the evidence:

1. **Full omission (recommended).** Drop `SBooleanType`, `SBooleanValue`,
   `StandardOperationsSBoolean`, `ExpConstSBoolean`, `ASTSBooleanLiteral`, `ExpDefSBoolean`,
   `ASTSBooleanDefExpression`, the `'SBoolean'` grammar alternatives, and the `"SBoolean"` entry in
   `TypeFactory.buildInTypesMap` (`src/main/org/tzi/use/uml/ocl/type/TypeFactory.java:64`).
   Then **also** remove `isKindOfSBoolean` / `isTypeOfSBoolean` from `Type`/`TypeImpl`/
   `MClassifierImpl`/`VoidType`, and the SBoolean clauses in `UBooleanType` (:22-25, :37, :44) and
   `BooleanType` (:54-57, :64, :75), and the four lines in `StandardOperationsAny` (:50, :58-59,
   :200, :208-209). Per 2.3(A) no LCS result changes. Per 2.3(C) the *only* observable losses are
   the 21 leaked operation names, which no corpus exercises (see 2.5).
2. **Skeleton retention.** Keep `SBooleanType` (a ~35-line class) so the lattice and
   `StandardOperationsAny` port verbatim, but skip `SBooleanValue` and the 1502-line registry.
   Cheap, and it preserves the type-conformance semantics that `TypeTest` asserts (see 2.5). This
   removes the 2.3(C) leak automatically, because the leak lives entirely in the registry.
3. **Full port.** Only if a thesis result depends on subjective-logic operators. Nothing in the
   corpora suggests it does.

Option 2 is the cheapest way to keep the fork's *type system* bit-identical while paying none of
the 1502-line cost. Option 1 is cheapest overall but is a deliberate, documentable behaviour
change.

### 2.5 — What the tests say about SBoolean

Complete non-`src/main` reference set:

```
$ grep -rIl "SBoolean" . | grep -v "^./src/main" | grep -v "^./lib"
src/test/org/tzi/use/uml/ocl/type/TypeTest.java
```

One test file. It exercises **only the type lattice**, never a value or an operation:
`TypeTest.java:103-108` (`SBoolean < OclAny`, `SBoolean < SBoolean`), `:111-112`
(`UBoolean < SBoolean`), `:123-124` (`Boolean < SBoolean`), `:210-216`
(`SBoolean.allSupertypes()`), `:223`, `:235`, `:286`, `:401-403`, and negative assertions at
`:466`, `:493`, `:516`.

The `.in` corpora contain **zero** SBoolean:

```
$ ls src/test/org/tzi/use/parser/uncertainty/
AllTests.java  UBooleanExpression.in (400 lines)  UCollectionOperations.in (173)
UIntegerExpression.in (2211)  URealExpression.in (1881)  USECompilerUncertaintyTest.java
$ grep -rn "SBoolean" src/test/org/tzi/use/parser/uncertainty/
   (no output)
```

There are **no** `SBooleanValueTest`, `SBooleanExpOpsTest`, or `SBooleanExpression.in`. Compare
the U-types, which have `src/test/org/tzi/use/uml/ocl/value/{UBooleanValueTest,UIntegerValueTest,
URealValueTest}.java` and `src/test/org/tzi/use/uml/ocl/expr/{URealExpOpsTest,UIntegerExpOpsTest}.java`.

**Zero of the 1502 lines of `StandardOperationsSBoolean` is covered by any test in the fork.**
That, plus the 21 unshadowed leaked operations in 2.3(C), plus the two hard defects in
`ExpDefSBoolean` from Q1, is a consistent picture: the SBoolean sub-feature was written and
never exercised.

`UNVERIFIABLE`: whether the fork's authors *intended* `isKindOfSBoolean` on `UBooleanType` as a
deliberate "UBoolean is a degenerate SBoolean" subsumption or whether it is a copy-paste of the
`isKindOfUBoolean` block. Nothing in the tree (no comment, no test, no corpus) settles intent.
`TypeTest.java:111-112` asserts the conformance holds, so it was at least *noticed*; it does not
show the operation-level consequence was noticed.

### 2.6 — Two gaps worth flagging, found while doing 2.5

- `USECompilerTest.java:78` points at
  `src/test/org/tzi/use/parser/test_expr_uncertainty.in`, which **does not exist**
  (`ls` → `No such file or directory`). A dangling reference in the fork's own test harness.
- There is **no `UStringExpression.in`** in `src/test/org/tzi/use/parser/uncertainty/`, though
  `UBoolean`, `UInteger` and `UReal` each have one. `UString` is the least-covered of the four
  thesis targets in the fork's corpora — relevant to any "port to parity with the fork's tests"
  plan.

---

## QUESTION 3 — `UncertainBooleanValue` / `UncertainBooleanType` vs `UBooleanValue` / `UBooleanType`

### Verdict

**It is not two parallel boolean-ish types. It is a two-level hierarchy, and the `Uncertain*` half
is an abstract base class with no surface existence.** The smell is real but it is a different
smell: the abstraction exists *solely* so that `UBoolean` and `SBoolean` can be siblings.

### The hierarchy, from the declarations

Types:
```
BasicType
 └─ UncertainType                    src/main/org/tzi/use/uml/ocl/type/UncertainType.java:10   (abstract)
     ├─ UncertainBooleanType         src/main/org/tzi/use/uml/ocl/type/UncertainBooleanType.java:3 (abstract)
     │   ├─ UBooleanType             src/main/org/tzi/use/uml/ocl/type/UBooleanType.java:6   name "UBoolean"
     │   └─ SBooleanType             src/main/org/tzi/use/uml/ocl/type/SBooleanType.java:6    name "SBoolean"
     ├─ URealType                    src/main/org/tzi/use/uml/ocl/type/URealType.java:6
     ├─ UIntegerType                 src/main/org/tzi/use/uml/ocl/type/UIntegerType.java:12
     └─ UStringType                  src/main/org/tzi/use/uml/ocl/type/UStringType.java:6
```

Values:
```
Value
 └─ UncertainValue                   src/main/org/tzi/use/uml/ocl/value/UncertainValue.java:15  (abstract)
     ├─ UncertainBooleanValue        src/main/org/tzi/use/uml/ocl/value/UncertainBooleanValue.java:5 (abstract)
     │   ├─ UBooleanValue            src/main/org/tzi/use/uml/ocl/value/UBooleanValue.java:17
     │   └─ SBooleanValue            src/main/org/tzi/use/uml/ocl/value/SBooleanValue.java:11  (final)
     ├─ URealValue                   src/main/org/tzi/use/uml/ocl/value/URealValue.java:14
     ├─ UIntegerValue                src/main/org/tzi/use/uml/ocl/value/UIntegerValue.java:8
     └─ UStringValue                 src/main/org/tzi/use/uml/ocl/value/UStringValue.java:8
```

`UncertainBooleanType.java` is **eight lines of body** — a constructor and nothing else:

```java
public abstract class UncertainBooleanType extends UncertainType {
    protected UncertainBooleanType(String t) { super(t); }
}
```

`UncertainBooleanValue.java` is barely larger — a constructor plus one abstract method:

```java
public abstract class UncertainBooleanValue extends UncertainValue {
    protected UncertainBooleanValue(Type t) { super(t); }
    public abstract UncertainBooleanValue not();
}
```

### Which is which

- `UBooleanType` / `UBooleanValue` are the **concrete, user-visible** type. It is a
  `(boolean, confidence)` pair — see the class javadoc at `UBooleanValue.java:9-14` and the field
  `private final UBoolean uBoolean;` (`UBooleanValue.java:22`) wrapping `uDataTypes.UBoolean`
  (import at `UBooleanValue.java:3`).
- `UncertainBooleanType` / `UncertainBooleanValue` are **abstract plumbing**, never instantiated,
  never named in surface syntax.

Proof of no surface existence — the complete built-in type registry
(`src/main/org/tzi/use/uml/ocl/type/TypeFactory.java:58-70`):

```
Integer, UInteger, UnlimitedNatural, String, UString, SBoolean, UBoolean, Boolean,
UReal, Real, OclAny, OclVoid
```

No `UncertainBoolean`. No grammar mentions it either (`OCL.g:698` /
`OCLBase.gpart:633` list exactly `UReal|UInteger|UBoolean|UString|SBoolean`).

Proof of no other consumer:

```
$ grep -rn "UncertainBooleanType" --include=*.java src | grep -v "type/UncertainBooleanType.java"
src/main/org/tzi/use/uml/ocl/type/SBooleanType.java:6:public class SBooleanType extends UncertainBooleanType {
src/main/org/tzi/use/uml/ocl/type/UBooleanType.java:6:public class UBooleanType extends UncertainBooleanType {
```

Two `extends` clauses. **`UncertainBooleanType` is used for nothing except being extended by
exactly two classes, one of which is SBoolean.** No `instanceof UncertainBooleanType` anywhere.
(Contrast its parent `UncertainType`, which *is* used as a real discriminator:
`StandardOperationsAny.java:49,199`, `StandardOperationsNumber.java:351,946,1024,1101,1179`,
`StandardOperationsCollection.java:104,169,401,474`, `ExpQuery.java:30`.)

`UncertainBooleanValue` earns slightly more of its keep — it is a **declared type** in nine places,
all of them "the polymorphic result of comparing two uncertain values":

```
src/main/org/tzi/use/uml/ocl/value/UncertainValue.java:28      abstract UncertainBooleanValue uEquals(Value)
src/main/org/tzi/use/uml/ocl/value/UncertainValue.java:37-39   uDistinct() local + return
src/main/org/tzi/use/uml/ocl/value/URealValue.java:137         @Override uEquals
src/main/org/tzi/use/uml/ocl/value/UIntegerValue.java:39       @Override uEquals
src/main/org/tzi/use/uml/ocl/value/UStringValue.java:54        @Override uEquals
src/main/org/tzi/use/uml/ocl/value/UBooleanValue.java:275,317  @Override uEquals, @Override not
src/main/org/tzi/use/uml/ocl/value/SBooleanValue.java:100,112,221  @Override uEquals, uDistinct, not
src/main/org/tzi/use/uml/ocl/value/CollectionValue.java:156    local variable
src/main/org/tzi/use/uml/ocl/expr/operations/StandardOperationsAny.java:97,232  return type of evalUncertainBooleanResult
```

But per 2.2, **every one of those sites receives a `UBooleanValue` in practice** unless the
receiver is an `SBooleanValue`. `SBooleanValue.uEquals` (`SBooleanValue.java:100-109`) is the sole
implementation that returns something else — it returns `SBooleanValue` via
`valueOf(sBoolean.equivalent(...))` at `:105`. That single override is the entire reason the
abstract return type exists.

### What the `.in` corpora actually exercise

- `UBoolean`: heavily. `src/test/org/tzi/use/parser/uncertainty/UBooleanExpression.in`, 400 lines,
  `grep -c "UBoolean(" → 155` literal occurrences. Plus
  `src/test/org/tzi/use/uml/ocl/value/UBooleanValueTest.java`.
- `SBoolean`: **zero** across all corpora (see 2.5).
- `UncertainBoolean`: **zero, and necessarily zero** — it is not a type name, has no
  `TypeFactory` entry and no grammar token, so it is unwritable in OCL.

The corpora therefore exercise `UBooleanValue` only, and reach `UncertainBooleanValue` only as a
static declared type on the way.

### Does the port need both?

**It needs `UBoolean*`. It needs `UncertainBoolean*` only as a consequence of the SBoolean
decision.**

- If SBoolean is dropped (Q2 option 1): `UncertainBooleanValue` has exactly one subclass and
  `UncertainBooleanType` has exactly one subclass. Both collapse. `UncertainValue.uEquals` and
  `uDistinct` re-type to `UBooleanValue`, `UBooleanType extends UncertainType` directly, and the
  `not()` abstract method moves onto `UBooleanValue` where its only implementation already lives
  (`UBooleanValue.java:315-318`). **Two files deleted, no behaviour lost** — the corpora cannot
  observe the difference, since they never produce a non-UBoolean `UncertainBooleanValue`.
- If SBoolean is retained even in skeleton form (Q2 option 2): keep `UncertainBooleanType` (8
  lines) so the type hierarchy matches the fork. `UncertainBooleanValue` still collapses if
  `SBooleanValue` is not ported, because the abstract `not()` then has one implementor.

Either way the "two parallel boolean-ish types" reading is **wrong**: there is one concrete
boolean-ish uncertainty type the thesis cares about (`UBoolean`), one it does not
(`SBoolean`), and an abstract parent that exists only to join them. Keep the parent iff you keep
the second child.

---

## Summary table

| # | Question | Verdict | Key evidence |
|---|---|---|---|
| 1 | Is `ExpDefSBoolean` needed? | **No — unreachable dead code, and broken twice over.** Do not port. | Sole caller `ASTSBooleanDefExpression.java:25` is itself never instantiated; no grammar emits 1-arg `SBoolean(x)` (`OCLBase.gpart:499-500` is the only production, 4-arg); inverted guard `ExpDefSBoolean.java:16-17`; missing `ctx.exit` `ExpDefSBoolean.java:23-28` |
| 2 | SBoolean dependency footprint | **Assumption half wrong.** No U-type *behaviour* touches SBoolean, but `UBooleanType`/`BooleanType` declare it a supertype, and 21 `isKindOf`-guarded SBoolean ops are consequently callable on `UBoolean`/`Boolean` and return SBoolean. | `grep SBoolean StandardOperationsU{Boolean,Real,Integer,String}.java` → empty; `UBooleanType.java:22-25,37,44`; `BooleanType.java:54-57,64,75`; `StandardOperationsSBoolean.java:1202-1203` + `SBooleanValue.java:76-78`; `ExpStdOp.java:127-134` first-match; `OpGeneric.java:88-97` order |
| 3 | `UncertainBoolean*` vs `UBoolean*` | **Not parallel — abstract base vs concrete type.** Exists only to make `UBoolean` and `SBoolean` siblings. Collapses if SBoolean is dropped. | `UncertainBooleanType.java` (2-line body, 2 `extends` clauses only); no `TypeFactory` entry (`TypeFactory.java:58-70`); no grammar token (`OCLBase.gpart:633`); `SBooleanValue.uEquals:100-109` is the sole non-UBoolean implementor |

### Cross-cutting recommendation

Q1, Q2 and Q3 all point the same way and are best decided together, not separately: **SBoolean and
everything that exists only to accommodate it (`ExpDefSBoolean`, `ASTSBooleanDefExpression`,
`UncertainBooleanValue`, and — if you go all the way — `UncertainBooleanType`) is one severable
unit.** The only thing that survives severing is the question of whether to keep
`isKindOfSBoolean` on `UBooleanType`/`BooleanType` for lattice fidelity with
`TypeTest.java:111-112,123-124`. Per 2.3(A) that flag changes no LCS outcome, so it can go too —
but that must be a recorded decision, not an oversight, because it is the one place where the
narrow port is observably not the fork.
