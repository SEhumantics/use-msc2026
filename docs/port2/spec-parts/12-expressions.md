# 12 — Expression classes

Port specification, expression-class section.

Scope: the OCL expression AST classes the uncertainty fork adds under
`org/tzi/use/uml/ocl/expr/`, plus the minimal edits required in upstream 7.5.0
expression/visitor code.

Reference (READ-ONLY):
`/home/xoruser/msc-4/use-msc2026/.git/reference-repositories/uncertainty/USE-Uncertainty`
(abbreviated `FORK` below; source root `FORK/src/main/org/tzi/use`).

Target: `/home/xoruser/msc-4/use-msc2026/use-core/src/main/java/org/tzi/use`
(abbreviated `T` below).

Every claim below is anchored to a file + line or to a shell command recorded in
§7. Anything not established is written `UNVERIFIABLE`.

---

## 0. Hard prerequisites (blocking; owned by other spec sections)

None of the uncertainty substrate exists in the 7.5.0 tree. Verified by:

```
grep -n "mkUBoolean\|mkUReal\|mkUInteger\|mkUString\|mkSBoolean" \
  use-core/src/main/java/org/tzi/use/uml/ocl/type/TypeFactory.java     # 0 hits
grep -n "isKindOfUBoolean\|isTypeOfUBoolean" \
  use-core/src/main/java/org/tzi/use/uml/ocl/type/Type.java            # 0 hits
grep -n "isUBoolean\|isUReal\|isUInteger\|isUString\|isSBoolean" \
  use-core/src/main/java/org/tzi/use/uml/ocl/value/Value.java          # 0 hits
ls use-core/src/main/java/org/tzi/use/uml/ocl/value/ | grep -i "^U\|^SBool"
  # only UndefinedValue.java, UnlimitedNaturalValue.java
```

So the expression classes in this section cannot compile until these land:

| Needed symbol | Used by |
|---|---|
| `TypeFactory.mkUBoolean()` | `ExpConstUBoolean` |
| `TypeFactory.mkUInteger()` | `ExpConstUInteger` (twice: ctor + `toString`) |
| `TypeFactory.mkUReal()` | `ExpConstUReal` |
| `TypeFactory.mkUString()` | `ExpConstUString` |
| `TypeFactory.mkSBoolean()` | `ExpConstSBoolean`, `ExpDefSBoolean` |
| `Type.isKindOfUBoolean(VoidHandling)` | `ExpQuery.assertKindOfUBoolean`, `ExpDefSBoolean` |
| `Value.isUBoolean()` | `ExpQuery.evalUSelect` |
| `UBooleanValue.valueOf(boolean,double)`, `.probability()` | `ExpConstUBoolean`, `ExpQuery.evalUSelect` |
| `URealValue(double,double)` | `ExpConstUReal` |
| `UIntegerValue(int,double)` | `ExpConstUInteger` |
| `UStringValue(String,double)` | `ExpConstUString` |
| `SBooleanValue.Builder` (`belief/disbelief/uncertainty/agent/build`) | `ExpConstSBoolean` |
| `SBooleanValue.valueOf(Value)` | `ExpDefSBoolean` |

These already exist in 7.5.0 and are used unchanged:
`Type.isTypeOfBoolean()`, `isTypeOfInteger()`, `isTypeOfReal()`,
`isTypeOfString()`, `isKindOfReal(VoidHandling)`, `isTypeOfVoidType()`,
`Type.VoidHandling` (T `.../ocl/type/Type.java:33,84,92,94,98,102,152`);
`Value.isReal()`, `isInteger()`, `isBoolean()`, `isDefined()`, `isUndefined()`
(T `.../ocl/value/Value.java:56,72,80,84,92`).

`Expression` (the base class) needs **no** change: the only diff between
T `.../expr/Expression.java` and FORK's is an `$Id:` line and two javadoc
edits — no member changes. (§7 cmd 1.)

---

## 1. The `ExpConst*` / `ExpDef*` classes

### 1.1 One file per type — confirmed

The port plan requires one file per type. The historical tree already does this.
`ls` of `FORK/src/main/org/tzi/use/uml/ocl/expr/` shows six separate files, and
`wc -l -c` gives (§7 cmd 2):

| File (FORK, `.../ocl/expr/`) | lines | bytes |
|---|---:|---:|
| `ExpConstUBoolean.java` | 78 | 2114 |
| `ExpConstUInteger.java` | 68 | 2051 |
| `ExpConstUReal.java` | 69 | 1775 |
| `ExpConstUString.java` | 75 | 2147 |
| `ExpConstSBoolean.java` | 88 | 3165 |
| `ExpDefSBoolean.java` | 48 | 1201 |
| `ExpUSelect.java` | 49 | 1209 |
| `ExpUSelectC.java` | 37 | 932 |

No file contains more than one top-level class. There is no combined
"ExpConstUncertain.java". **The one-file-per-type rule is satisfied by a
straight 1:1 file copy.**

### 1.2 Shape common to all five `ExpConst*` classes

All five extend `org.tzi.use.uml.ocl.expr.Expression` directly (not
`ExpConstBoolean`/`ExpConstReal`, not a shared uncertainty base class).
Verified at `ExpConstUBoolean.java:8`, `ExpConstUInteger.java:6`,
`ExpConstUReal.java:9`, `ExpConstUString.java:10`, `ExpConstSBoolean.java:9`,
`ExpDefSBoolean.java:8`.

**Important divergence from the upstream `ExpConst*` idiom.** 7.5.0's
`ExpConstReal` (T `.../expr/ExpConstReal.java:30-53`) stores a primitive
`double fValue` and builds the value in `eval`. The fork's `ExpConstU*` classes
store **`Expression` children** and evaluate them in `eval`. They are literals
only syntactically; structurally they are composite nodes. Consequences the port
must accept:

* they are **not** `final` classes (upstream `ExpConstReal` is `public final`);
* `childExpressionRequiresPreState()` returns `false` in every one of them
  (`ExpConstUBoolean.java:59-61`, `ExpConstUInteger.java:52-54`,
  `ExpConstUReal.java:50-52`, `ExpConstUString.java:60-62`,
  `ExpConstSBoolean.java:66-68`, `ExpDefSBoolean.java:32-34`) — i.e. an
  `@pre` inside a `UReal(...)` argument is silently ignored. This is a fork
  behaviour, faithfully reproduced by copying; flag it, do not "fix" it
  silently.
* no `equals`/`hashCode` is defined on any of them (grep of the six files
  returns no `equals`/`hashCode`). Same as upstream `ExpConst*`.

---

### 1.3 `ExpConstUBoolean` — **NEW FILE**

`FORK/.../expr/ExpConstUBoolean.java` (78 lines).

* **Superclass**: `Expression` (`:8`). Result type `TypeFactory.mkUBoolean()` (`:15`).
* **Fields**: `private Expression eValue; private Expression eProbability;` (`:9-10`).
* **Constructor**: `ExpConstUBoolean(Expression eValue, Expression eProbability) throws ExpInvalidException` (`:12-25`). Guards:
  * `!eValue.type().isTypeOfBoolean()` → `ExpInvalidException("Value must be Boolean")` (`:17-18`).
  * `!(eProbability.type().isTypeOfInteger() || eProbability.type().isTypeOfReal())` → `ExpInvalidException("Probability must be a Integer or Real")` (`:20-21`). Note: `isTypeOf`, not `isKindOf`; and `VoidType` is **not** accepted here (unlike `ExpConstUInteger`, §1.4).
* **Accessors**: `public String value()` → `eValue.toString()`; `public String probability()` → `eProbability.toString()` (`:27-33`). Both return `String`, not `Expression` — inconsistent with `ExpConstUString` (§1.6). Copy as-is unless the port plan states otherwise.
* **`eval(EvalContext)`** (`:35-56`):
  1. `ctx.enter(this)`;
  2. evaluate both children;
  3. if `probability.isUndefined()` → `UndefinedValue.instance`;
  4. else `UBooleanValue.valueOf(Boolean.valueOf(value.toString()), Double.valueOf(probability.toString()))` inside a `try`; any `RuntimeException` → `UndefinedValue.instance` (`:46-51`);
  5. `ctx.exit(this, res)`; return.
  Note the round-trip through `toString()` — it does not cast to `BooleanValue`/`RealValue`. `Boolean.valueOf(String)` never throws, so a non-boolean `value` silently becomes `false`. **`value.isUndefined()` is not checked** — an undefined value with a defined probability yields `UBoolean(false, p)`, not undefined. Faithful copy reproduces this.
* **`toString(StringBuilder)`** (`:63-71`): `"UBoolean(" + eValue + "," + eProbability + ")"`. Separator is a bare comma, **no space**.
* **`processWithVisitor`** (`:73-76`): `visitor.visitConstUBoolean(this)`.
* **Visitor member needed**: `void visitConstUBoolean(ExpConstUBoolean exp);`

### 1.4 `ExpConstUInteger` — **NEW FILE**

`FORK/.../expr/ExpConstUInteger.java` (68 lines).

* **Superclass**: `Expression` (`:6`). Result type `TypeFactory.mkUInteger()` (`:12`).
* **Fields**: `eValue`, `eUncertainty` (`:8-9`).
* **Constructor**: `ExpConstUInteger(Expression eValue, Expression eUncertainty) throws ExpInvalidException` (`:11-22`). Guards:
  * `!eValue.type().isTypeOfInteger() && !eValue.type().isTypeOfVoidType()` → `"Value must be Integer"` (`:14-15`);
  * `!(isTypeOfInteger || isTypeOfReal || isTypeOfVoidType)` on the uncertainty → `"Uncertainty must be Integer or Real"` (`:17-18`).
  Unlike `ExpConstUBoolean`, `VoidType` **is** tolerated on both operands.
* **No accessors.** (`value()`/`uncertainty()` absent — differs from `ExpConstUReal`.)
* **`eval`** (`:24-49`): `ctx.enter`; evaluate both; if `value.isDefined() && uncertainty.isDefined()` then unbox the uncertainty via `isInteger()` → `((IntegerValue)u).value()` else `((RealValue)u).value()` (`:36-39`), and build `new UIntegerValue(((IntegerValue) value).value(), uncertaintyValue)` (`:41`); otherwise `UndefinedValue.instance`; `ctx.exit`; return. Uses real casts (not `toString()` parsing) — different style from `ExpConstUBoolean`/`ExpConstUReal`.
* **`toString(StringBuilder)`** (`:56-62`): `TypeFactory.mkUInteger().toString()` then `"(" + eValue + ", " + eUncertainty + ")"`. **Separator is `", "` (comma + space)** — the only one of the five that spaces it, and the only one whose type name comes from `TypeFactory` rather than a string literal. If the port adds parser round-trip tests, this asymmetry matters; record it, do not normalise it without a decision.
* **`processWithVisitor`** (`:64-67`): `visitor.visitConstUInteger(this)`.
* **Visitor member needed**: `void visitConstUInteger(ExpConstUInteger exp);`

### 1.5 `ExpConstUReal` — **NEW FILE**

`FORK/.../expr/ExpConstUReal.java` (69 lines).

* **Superclass**: `Expression` (`:9`). Result type `TypeFactory.mkUReal()` (`:14`).
* **Fields**: `eValue`, `eUncertainty` (`:10-11`).
* **Constructor**: `ExpConstUReal(Expression eValue, Expression eUncertainty)` (`:13-17`) — **does not declare `throws ExpInvalidException` and performs no type checking at all.** It is the only one of the five with no guard. A `ExpConstUReal(stringExpr, objectExpr)` constructs happily and blows up at `eval`. Flag this in the port; the fix is out of scope for a faithful copy but should be listed as a known defect.
* **Accessors**: `public String value()`, `public String uncertainty()` (`:19-25`) — `String`-returning, same as `ExpConstUBoolean`.
* **`eval`** (`:27-47`): `ctx.enter(this);;` (double semicolon at `:32`, harmless); evaluate both; if either `isUndefined()` → `UndefinedValue.instance`; else `new URealValue(Double.valueOf(value.toString()), Double.valueOf(uncertainty.toString()))` (`:39-42`). **No `try/catch`** — a non-numeric child throws `NumberFormatException` out of `eval` (contrast `ExpConstUBoolean`, which swallows it). `ctx.exit(this, res)`; return.
* **`toString(StringBuilder)`** (`:54-62`): `"UReal(" + eValue + "," + eUncertainty + ")"`, bare comma.
* **`processWithVisitor`** (`:64-67`): `visitor.visitConstUReal(this)`.
* **Visitor member needed**: `void visitConstUReal(ExpConstUReal exp);`

### 1.6 `ExpConstUString` — **NEW FILE**

`FORK/.../expr/ExpConstUString.java` (75 lines).

* **Superclass**: `Expression` (`:10`). Result type `TypeFactory.mkUString()` (`:16`).
* **Fields**: `eValue`, `eConf` (`:12-13`).
* **Constructor**: `ExpConstUString(Expression eValue, Expression eConf) throws ExpInvalidException` (`:15-26`). Guards:
  * `!eConf.type().isKindOfReal(Type.VoidHandling.EXCLUDE_VOID)` → `"UString : confidance need to be kind of Real"` (`:18-19`) — note `isKindOfReal`, so Integer is accepted via Real conformance; note the misspelling "confidance", preserved verbatim if messages are asserted anywhere;
  * `!eValue.type().isTypeOfString()` → `"UString : value must be type of String"` (`:21-22`).
* **Accessors**: `public Expression valueExpression()`, `public Expression confidenceExpression()` (`:28-34`) — these return `Expression`, unlike the `String`-returning accessors on `ExpConstUBoolean`/`ExpConstUReal`.
* **`eval`** (`:36-57`): `ctx.enter`; `value = (StringValue) eValue.eval(ctx)` — **unchecked cast, no undefined guard**, so an undefined string child throws `ClassCastException`; `confidenceValue = Double.valueOf(confidence.toString())`; if `< 0 || > 1` → `UndefinedValue.instance`, else `new UStringValue(value.value(), confidenceValue)`; `ctx.exit`; return.
* **`toString(StringBuilder)`** (`:64-69`): `"UString(" + eValue + "," + eConf + ")"`, bare comma.
* **`processWithVisitor`** (`:71-74`): `visitor.visitConstUString(this)`.
* **Visitor member needed**: `void visitConstUString(ExpConstUString exp);`

### 1.7 `ExpConstSBoolean` — **NEW FILE**

`FORK/.../expr/ExpConstSBoolean.java` (88 lines).

* **Superclass**: `Expression` (`:9`). Result type `TypeFactory.mkSBoolean()` (`:17`).
* **Fields**: `beliefExpression`, `disbeliefExpression`, `uncertaintyExpression`, `agentExpression` (`:11-14`) — four children.
* **Constructor**: `ExpConstSBoolean(Expression belief, Expression disbelief, Expression uncertainty, Expression agent) throws ExpInvalidException` (`:16-35`). Four identical guards, each `!x.type().isKindOfReal(Type.VoidHandling.EXCLUDE_VOID)` → `"Belief  must be a kind of Real"` / `"Disbelief  must be…"` / `"Uncertainty  must be…"` / `"Agent  must be…"` (`:19-29`; note the double space in each message, verbatim in the source).
* **No accessors.**
* **`eval`** (`:37-63`): `ctx.enter`; evaluate all four; build via `new SBooleanValue.Builder().belief(...).disbelief(...).uncertainty(...).agent(...).build()`, each argument `Double.parseDouble(x.toString())` (`:49-54`); **any `Exception` → `UndefinedValue.instance`** (`:57-59`, catches `Exception`, the broadest of the five); `ctx.exit`; return.
* **`toString(StringBuilder)`** (`:70-82`): `"SBoolean(" + belief + "," + disbelief + "," + uncertainty + "," + agent + ")"`, bare commas — **4-argument form**.
* **`processWithVisitor`** (`:84-87`): `visitor.visitConstSBoolean(this)`.
* **Visitor member needed**: `void visitConstSBoolean(ExpConstSBoolean exp);`

### 1.8 Summary table

| Class | super | ctor arity | `throws` | guards | eval failure mode | `toString` prefix / separator | visitor method |
|---|---|---:|---|---|---|---|---|
| `ExpConstUBoolean` | `Expression` | 2 | yes | Boolean / Integer-or-Real | catches `RuntimeException` → undefined | `UBoolean(` / `,` | `visitConstUBoolean` |
| `ExpConstUInteger` | `Expression` | 2 | yes | Integer-or-Void / Integer-Real-Void | defined-check → undefined | `mkUInteger().toString()` + `(` / `, ` | `visitConstUInteger` |
| `ExpConstUReal` | `Expression` | 2 | **no** | **none** | propagates `NumberFormatException` | `UReal(` / `,` | `visitConstUReal` |
| `ExpConstUString` | `Expression` | 2 | yes | kindOfReal / String | propagates `ClassCastException`; range → undefined | `UString(` / `,` | `visitConstUString` |
| `ExpConstSBoolean` | `Expression` | 4 | yes | 4× kindOfReal | catches `Exception` → undefined | `SBoolean(` / `,` | `visitConstSBoolean` |
| `ExpDefSBoolean` | `Expression` | 1 | **no** (throws unchecked) | inverted, see §2 | **no `ctx.exit`**; can return Java `null` | `SBoolean(` / n/a | `visitDefSBoolean` |

---

## 2. `ExpDefSBoolean` — the open question, answered

`FORK/src/main/org/tzi/use/uml/ocl/expr/ExpDefSBoolean.java`, 48 lines, 1201 bytes.

### 2.1 What it does, literally

```java
public class ExpDefSBoolean extends Expression {            // :8
    private Expression eBool;                               // :10
    public ExpDefSBoolean(Expression eBool) {               // :12
        super(TypeFactory.mkSBoolean());                    // :13
        if (eBool.type().isKindOfUBoolean(Type.VoidHandling.EXCLUDE_VOID))
            throw new RuntimeException("Expression Boolean or UBoolean expected");   // :15-16
        this.eBool = eBool;                                 // :18
    }
    @Override public Value eval(EvalContext ctx) {          // :22
        Value boolValue = null;
        ctx.enter(this);                                    // :25
        boolValue = eBool.eval(ctx);                        // :26
        return SBooleanValue.valueOf(boolValue);            // :28   <-- no ctx.exit
    }
    @Override protected boolean childExpressionRequiresPreState() { return false; }  // :32-34
    @Override public StringBuilder toString(StringBuilder sb) {                      // :37
        sb.append("SBoolean(").append(eBool.toString()).append(")"); return sb; }
    @Override public void processWithVisitor(ExpressionVisitor visitor) {
        visitor.visitDefSBoolean(this); }                   // :46
}
```

### 2.2 What it is *for*

Intended purpose (from the type, the message and `SBooleanValue.valueOf`): a
**1-argument `SBoolean(x)` lifting constructor** — coerce a `Boolean` or
`UBoolean` expression into a subjective-logic `SBoolean`, complementing the
4-argument `ExpConstSBoolean` (`SBoolean(b,d,u,a)`). `SBooleanValue.valueOf(Value)`
(`FORK/.../ocl/value/SBooleanValue.java:71-88`) handles exactly the three cases
`isSBoolean` → identity, `isUBoolean` → `new SBooleanValue(new SBoolean(ub.getuBoolean()))`,
`isBoolean` → `TRUE`/`FALSE`, and **returns `null` for anything else**.

### 2.3 Three defects, all latent

1. **The type guard is inverted.** `BooleanType.isKindOfUBoolean(h)` returns
   `true` (`FORK/.../ocl/type/BooleanType.java:50-52`) and
   `UBooleanType.isKindOfUBoolean(h)` returns `true`
   (`FORK/.../ocl/type/UBooleanType.java:18-20`); `TypeImpl` returns `false`
   for everything else (`FORK/.../ocl/type/TypeImpl.java:210-212`). The
   constructor throws **when the argument *is* Boolean or UBoolean** — i.e. it
   rejects precisely the two inputs its own error message says it expects, and
   accepts Integer, String, objects, collections. The condition is missing a
   `!`.
2. **`eval` never calls `ctx.exit`.** `ctx.enter(this)` at `:25`, `return` at
   `:28` with no matching `ctx.exit(this, res)`. Every other expression class in
   both trees pairs them (cf. T `ExpConstReal.java:47-52`). Reachable use would
   leave the eval-tree/`EvalContext` stack unbalanced.
3. **`eval` can return Java `null`.** Because of defect 1 the only arguments that
   reach `eval` are non-boolean, and `SBooleanValue.valueOf` returns `null` for
   those (`SBooleanValue.java:72,85-87`). A `null` `Value` NPEs in the caller.

Defects 2 and 3 never fire because the class is unreachable — see next.

### 2.4 Call sites — evidence

`grep -rn "ExpDefSBoolean\|visitDefSBoolean" src/` over
`FORK/src` (§7 cmd 3) returns exactly 8 hits in 6 files:

| Hit | Kind |
|---|---|
| `src/main/org/tzi/use/uml/ocl/expr/ExpDefSBoolean.java:8,12,46` | the class itself |
| `src/main/org/tzi/use/uml/ocl/expr/ExpressionVisitor.java:40` | interface declaration |
| `src/main/org/tzi/use/uml/ocl/expr/ExpressionPrintVisitor.java:190` | visitor impl (`writer.write(literal(exp.toString(), exp))`) |
| `src/main/org/tzi/use/analysis/coverage/AbstractCoverageVisitor.java:113` | visitor impl, **empty body** |
| `src/main/org/tzi/use/analysis/metrics/AbstractMetricVisitor.java:121` | visitor impl (fork-only package) |
| `src/main/org/tzi/use/parser/ocl/ASTSBooleanDefExpression.java:5,25` | the **only** `new ExpDefSBoolean(...)` |

So the sole constructor call is
`ASTSBooleanDefExpression.gen()` at `FORK/.../parser/ocl/ASTSBooleanDefExpression.java:25`.

Now the decisive grep — is `ASTSBooleanDefExpression` itself ever built?
`grep -rn "ASTSBooleanDefExpression\|ASTSBooleanLiteral" src/` (§7 cmd 4) returns
**17 hits**. Every construction site (`new ASTSBooleanLiteral(...)`) is for the
**4-argument** literal, in all six ANTLR grammars and their generated parsers:

```
src/main/org/tzi/use/parser/base/OCLBase.gpart:500
src/main/org/tzi/use/parser/ocl/OCL.g:565            + OCLParser.java:3258
src/main/org/tzi/use/parser/use/USE.g:1074           + USEParser.java:7268
src/main/org/tzi/use/parser/soil/Soil.g:1110         + SoilParser.java:5734
src/main/org/tzi/use/parser/shell/ShellCommand.g:863 + ShellCommandParser.java:4888
src/main/org/tzi/use/parser/generator/Generator.g:1341 + GeneratorParser.java:8875
src/main/org/tzi/use/parser/testsuite/TestSuite.g:672 + TestSuiteParser.java:4149
```

`ASTSBooleanDefExpression` appears **only** in its own declaration
(`ASTSBooleanDefExpression.java:11,15`). There is **no `new ASTSBooleanDefExpression`
anywhere in `FORK/src`**, main or test. No grammar rule produces it.

### 2.5 Answers

* **Is `ExpDefSBoolean` reachable?** **No.** The whole chain
  grammar → `ASTSBooleanDefExpression` → `ExpDefSBoolean` is severed at the
  first arrow. The 1-argument `SBoolean(x)` syntax was never wired into any
  grammar. `ExpDefSBoolean` and `ASTSBooleanDefExpression` are dead code in the
  fork.
* **Does anything in the UBoolean / UReal / UInteger / UString path depend on
  it?** **No.** The 6 files that name it are: itself, the visitor interface, two
  visitor implementations with pass-through/empty bodies, the fork-only metrics
  visitor, and the dead AST node. Zero references from `ExpConstUBoolean`,
  `ExpConstUReal`, `ExpConstUInteger`, `ExpConstUString`, `ExpQuery`,
  `ExpUSelect`, `ExpUSelectC`, or any value/type class. Confirmed by
  `grep -rl` per class (§7 cmd 5) — none of the U-type files list
  `ExpDefSBoolean`.
* **Is it needed for `ExpConstSBoolean`?** No. `ExpConstSBoolean` is reachable
  (`ASTSBooleanLiteral.java:33`, wired into all six grammars) and does not touch
  `ExpDefSBoolean`.

### 2.6 Recommendation

**Do not port `ExpDefSBoolean` or `ASTSBooleanDefExpression`.** Porting it means
carrying a class that (a) no syntax can reach, (b) has an inverted guard, (c)
breaks the `enter`/`exit` contract, and (d) can return `null` — and it forces
`visitDefSBoolean` into `ExpressionVisitor`, costing a method in every
implementor (§5) for zero reachable behaviour.

If the port plan insists on bit-fidelity with the fork, port it **with the three
defects documented in a class javadoc** and with `visitDefSBoolean` added to the
visitor. Either way, record the decision explicitly — this is the open question
and the answer is "dead code, drop it".

Residual gap: whether some *external* artefact outside `FORK/src` (a `.use`
model, a shell script, a downstream tool) references a 1-argument `SBoolean(x)`
is `UNVERIFIABLE` from source alone; the grep covers `FORK/src` only. The
grammar evidence makes it moot — no parser accepts that form, so no `.use` file
could ever have used it.

---

## 3. `ExpQuery`, `ExpUSelect`, `ExpUSelectC`

### 3.1 `ExpUSelect` — **NEW FILE**

`FORK/.../expr/ExpUSelect.java`, 49 lines / 1209 bytes. Author tag
`@author Víctor M. Ortiz` (`:8`).

* `public class ExpUSelect extends ExpQuery` (`:11`).
* Constructor `ExpUSelect(VarDecl elemVarDecl, Expression rangeExp, Expression queryExp) throws ExpInvalidException` (`:20-30`). Calls
  `super(rangeExp.type(), elemVarDecl != null ? new VarDeclList(elemVarDecl) : new VarDeclList(true), rangeExp, queryExp)` — **result type is the range type**, exactly like `ExpSelect` (T `ExpSelect.java`). Then `assertKindOfUBoolean()` (`:29`) instead of `assertBooleanQuery()`.
* `eval` (`:32-38`): `ctx.enter(this); Value res = evalUSelect(ctx); ctx.exit(this, res); return res;`
* `name()` → `"uSelect"` (`:40-43`).
* `processWithVisitor` → `visitor.visitUSelect(this)` (`:45-48`).
* **Visitor member needed**: `void visitUSelect(ExpUSelect exp);`

### 3.2 `ExpUSelectC` — **NEW FILE**

`FORK/.../expr/ExpUSelectC.java`, 37 lines / 932 bytes. No javadoc.

* `public class ExpUSelectC extends ExpQuery` (`:5`).
* Constructor `ExpUSelectC(VarDecl elemVarDecl, Expression rangeExp, Expression queryExp, Expression uncertaintyExp) throws ExpInvalidException` (`:7-18`). Calls the **5-argument** `ExpQuery` constructor (`:13-15`), then `assertKindOfUBoolean()` (`:17`).
* `eval` (`:20-26`): identical body to `ExpUSelect` — both call the same `evalUSelect(ctx)`; the only difference is that `fUncertaintyExp` is non-null here.
* `name()` → `"uSelectC"` (`:28-31`).
* `processWithVisitor` → `visitor.visitUSelectC(this)` (`:33-36`).
* **Visitor member needed**: `void visitUSelectC(ExpUSelectC exp);`

Parser wiring (context, out of scope for this section but load-bearing):
`FORK/.../parser/ocl/ASTQueryExpression.java` gained a 5th ctor parameter
`ASTExpression uncertainty` (`:46,52,57`), the two ids
`ParserHelper.Q_USELECT_ID` / `Q_USELECTC_ID` in the single-variable branch
(`:129-130`), and the two construction sites at `:153` and `:183`. `uSelectC`
with a null confidence is a `SemanticException` (`:180-181`).

### 3.3 `ExpQuery` — **EDIT TO UPSTREAM FILE**

Target file: `T/uml/ocl/expr/ExpQuery.java` (513 lines).
Fork file: `FORK/.../expr/ExpQuery.java` (711 lines, 24111 bytes).

The 7.5.0 file in the working tree is **clean upstream** — it has no
`fUncertaintyExp`, no `assertKindOfUBoolean`, no `evalUSelect` (verified by
reading it in full; `grep -c "^\s*void visit"` and the field list confirm).

#### 3.3.1 Full delta fork-vs-7.5.0

| # | Delta | Fork lines | Additive? |
|---|---|---|---|
| 1 | field `protected Expression fUncertaintyExp;` | 59-62 | yes |
| 2 | 5-arg ctor `ExpQuery(Type, VarDeclList, Expression rangeExp, Expression queryExp, Expression uncertaintyExp)` — delegates to 4-arg, then requires `uncertaintyExp.type().isKindOfReal(EXCLUDE_VOID)` else `ExpInvalidException("Type of confident must be Real, found type '…' in expressión '…'")` | 99-112 | yes |
| 3 | `protected void assertKindOfUBoolean() throws ExpInvalidException` — `!fQueryExp.type().isKindOfUBoolean(EXCLUDE_VOID)` → `ExpInvalidException` | 127-132 | yes |
| 4 | `protected final Value evalUSelect(EvalContext ctx)` | 179-221 | yes |
| 5 | `private double evalAndAsertConfident(EvalContext ctx)` — default `0.5` when `fUncertaintyExp == null`; unboxes `RealValue`/`IntegerValue`; `RuntimeException` if outside `[0,1]` | 223-240 | yes |
| 6 | `public Expression getUncertaintyExpression()` | 708-710 | yes |
| 7 | `evalExistsOrForAll` rewritten to dispatch to new `evalForAll0` / `evalExists0` returning `Value` (folding with `ExpStdOp.create("and"/"or", …)` over `ExpressionWithValue`) instead of `BooleanValue.get(evalExistsOrForAll0(...))` | 247-357 | **NO — behaviour change** |
| 8 | old `evalExistsOrForAll0` kept, marked `@deprecated`, now **dead** (only self-recursive calls remain; §7 cmd 6) | 359-420 | dead code |
| 9 | `assertBooleanQuery` message: fork `"must have Boolean type"` vs 7.5.0 `"must have boolean type"` | 119-125 vs T:94-100 | cosmetic |
| 10 | `evalSortedBy`: fork always returns `SequenceValue`; **7.5.0 additionally returns `OrderedSetValue` when `this.type().isTypeOfOrderedSet()`** (T:460-464) | fork 615-659 | **fork is older — do NOT regress** |
| 11 | unused `import org.tzi.use.uml.ocl.type.UncertainType;` | 30 | **do not port** |
| 12 | `$Id:` / `@version $ProjectVersion` header noise | 20, 37 | do not port |

#### 3.3.2 The MINIMAL behavioural change to 7.5.0's `ExpQuery`

For `uSelect` / `uSelectC` **alone**, only items 1–6 are required, and all six
are **purely additive**: no existing method body changes, so `ExpSelect`,
`ExpReject`, `ExpCollect`, `ExpExists`, `ExpForAll`, `ExpIterate`, `ExpAny`,
`ExpOne`, `ExpClosure`, `ExpSortedBy`, `ExpIsUnique` are all bit-identical in
behaviour before and after. **This is the minimal edit.**

Concretely, in `T/uml/ocl/expr/ExpQuery.java`:

* after the `fQueryExp` field (T:53) add `fUncertaintyExp`;
* after the existing constructor (T ends at :87) add the 5-arg constructor;
* after `assertBooleanQuery()` (T:94-100) add `assertKindOfUBoolean()`;
* after `evalSelectOrReject` (T ends at :145) add `evalUSelect` and the private
  `evalAndAsertConfident`;
* after `getVariableDeclarations()` (T:510-512) add `getUncertaintyExpression()`.

Items 7 + 8 (`exists`/`forAll` returning `UBoolean`) are a **separate, larger**
behavioural change, driven by `ExpExists`/`ExpForAll` swapping
`assertBooleanQuery()` → `assertKindOfUBoolean()`
(`FORK/.../expr/ExpExists.java:43-47`, `ExpForAll.java:43-47`; §7 cmd 7). Under
7.5.0's `evalExistsOrForAll` the query value is cast `(BooleanValue) queryVal`
(T:206,208), so relaxing the constructor assertion **without** item 7 gives a
`ClassCastException` at eval time for a `UBoolean` predicate. Rule: **items 7+8
and the `ExpExists`/`ExpForAll` assertion swap are one atomic unit — take both
or neither.** Item 7 also changes `exists`/`forAll` short-circuiting: the fork
folds over the *whole* range with no early exit and drops the
`ctx.isEnableEvalTree()` fast-path, so a `forAll` over a large collection is
strictly slower and no longer stops at the first `false`.

Item 10 is a regression trap: the fork's `evalSortedBy` predates 7.5.0's
ordered-set fix. **Keep 7.5.0's `evalSortedBy` verbatim.**

`ExpSelect` and `ExpReject` are otherwise identical between the trees (only
`$Id`/`@version` header noise; §7 cmd 7) — **no edit needed**.

#### 3.3.3 `evalUSelect` semantics (fork `ExpQuery.java:179-221`)

```
confident = evalAndAsertConfident(ctx)          # 0.5 if fUncertaintyExp == null
v = fRangeExp.eval(ctx)
rangeVal = (CollectionValue) v                  # <-- see defect below
if (!v.isUndefined()):
    require rangeVal.type().isInstantiableCollection() else RuntimeException
    push binding for fElemVarDecls.varDecl(0) if any
    for elemVal in rangeVal:
        setPeekValue(elemVal)
        queryVal = fQueryExp.eval(ctx)
        if queryVal.isUndefined(): queryVal = BooleanValue.FALSE
        if queryVal.isBoolean() and ((BooleanValue)queryVal).isTrue():  keep
        elif queryVal.isUBoolean() and ((UBooleanValue)queryVal).probability() >= confident: keep
    pop binding
    return ((CollectionType) rangeVal.type()).createCollectionValue(resValues)
return UndefinedValue.instance                  # unreachable, see defect
```

**Defect to fix during the port (one-line move).** The cast
`CollectionValue rangeVal = (CollectionValue) v;` at `:185` happens
**before** the `if (!v.isUndefined())` guard at `:187`. `UndefinedValue extends
Value` (`FORK/.../ocl/value/UndefinedValue.java:32`), not `CollectionValue`
(`CollectionValue.java:46`), so an undefined range throws
`ClassCastException` instead of returning `UndefinedValue.instance`, and the
`return result` at `:220` is unreachable. `evalSelectOrReject` gets this right
(fork `:139-142`, T `:107-110`: guard first, cast second). **Recommended minimal
correction:** move the cast inside the guard, matching `evalSelectOrReject`.
Mark it as a deliberate deviation from the fork.

Behavioural notes worth recording:

* `evalAndAsertConfident` runs **before** the range is evaluated, so a
  confidence outside `[0,1]` throws even for an empty/undefined range. The fork
  tests rely on this (`FORK/src/test/.../ExpQueryUncertaintyTest.java:166-205`,
  `testUSelectCUncertaintyHigherThanOne` / `…LowerThanZero` expect
  `RuntimeException`).
* The out-of-range failure is an **unchecked** `RuntimeException`, not
  `ExpInvalidException` — a runtime failure, not a static one. `ExpUSelectC`'s
  *type* check on the confidence is static (`ExpInvalidException`,
  `ExpQuery.java:106-109`; test at `:146-164` passes an `ExpConstString`).
* Plain `Boolean` predicates are accepted (`isBoolean()` branch, `:207`) — so
  `uSelect` degenerates to `select` on a non-uncertain predicate. But
  `assertKindOfUBoolean` uses `isKindOfUBoolean`, and
  `BooleanType.isKindOfUBoolean` is `true`, so a Boolean predicate passes the
  constructor check too. Consistent.
* Golden case, from `ExpQueryUncertaintyTest.testUSelectCColA` (`:208-227`):
  range `Set{UReal(2,0.5), 1, 2.5, 3.2, UReal(3.5,0.25)}`, predicate `e1 >= 2`,
  confidence `0.8` ⇒ `Set{2.5, 3.2, UReal(3.5,0.25)}`. Use this as the port's
  acceptance test.

#### 3.3.4 Print / round-trip gap

Neither `ExpQuery.toString(StringBuilder)` (fork `:680-694`, unchanged from
T `:486-500`) nor `ExpressionPrintVisitor.visitUSelectC` (fork `:455-457`,
delegates to `visitQuery`) prints `fUncertaintyExp`. So
`uSelectC(e | p, 0.8)` renders as `…->uSelectC(e | p)` — **the confidence
argument is lost on print**, and the printed form no longer re-parses (the
parser rejects `uSelectC` without a confidence,
`ASTQueryExpression.java:180-181`). This is a real fidelity bug in the fork.
Decide explicitly: reproduce it, or override `toString`/`visitUSelectC` to emit
the confidence. Not fixing it will break any print-then-reparse test.

---

## 4. What the `ExpressionVisitor` interface needs

**EDIT TO UPSTREAM FILE**: `T/uml/ocl/expr/ExpressionVisitor.java`.

7.5.0's interface declares **49** `void visit…` methods; the fork's declares
**57** (§7 cmd 8). The delta is exactly **8 new methods** — the fork's
`visitObjOp(ExpObjOp)` (fork `:52`) corresponds 1:1 to 7.5.0's
`visitInstanceOp(ExpInstanceOp)` (T `:51`), so the counts line up.

New members (fork `ExpressionVisitor.java:38,39,40,43,45,47,85,86`):

```java
void visitConstUBoolean(ExpConstUBoolean exp);
void visitConstSBoolean(ExpConstSBoolean exp);
void visitDefSBoolean(ExpDefSBoolean expDefSBoolean);   // drop if §2.6 accepted -> 7 methods
void visitConstUInteger(ExpConstUInteger exp);
void visitConstUReal(ExpConstUReal exp);
void visitConstUString(ExpConstUString exp);
void visitUSelect(ExpUSelect expUSelect);
void visitUSelectC(ExpUSelectC expUSelectC);
```

Insertion points in T (to keep the fork's ordering): `visitConstUBoolean` /
`visitConstSBoolean` / `visitDefSBoolean` after `visitConstBoolean` (T:35);
`visitConstUInteger` after `visitConstInteger` (T:37); `visitConstUReal` after
`visitConstReal` (T:38); `visitConstUString` after `visitConstString` (T:39);
`visitUSelect` / `visitUSelectC` at the end (after T:76).

**Do NOT copy the fork's `ExpressionVisitor.java` wholesale.** It is a 7.0-era
file: it declares `visitObjOp(ExpObjOp)`, and `ExpObjOp` does not exist in 7.5.0
(`T/.../expr/` has `ExpInstanceOp.java` and `ExpInstanceConstructor.java`
instead). Add the 8 (or 7) methods to the *existing* 7.5.0 interface by hand.

---

## 5. Compile-break work-list — every `ExpressionVisitor` implementation in 7.5.0

Enumerated across **all** modules (`use-core`, `use-gui`, `use-assembly`) by
`grep -rn "implements[^{]*ExpressionVisitor"` plus transitive
`extends` closure (§7 cmds 9-11). There are exactly **two** direct implementors;
everything else inherits.

### 5.1 Direct implementors — MUST gain the new methods

| # | File (absolute) | Status | Note |
|---|---|---|---|
| 1 | `/home/xoruser/msc-4/use-msc2026/use-core/src/main/java/org/tzi/use/uml/ocl/expr/ExpressionPrintVisitor.java` | **EDIT — hard compile break** | `public class ExpressionPrintVisitor implements ExpressionVisitor` (`:35`), concrete. Add all 8 methods. Fork bodies to mirror: `visitConstUBoolean` (`:180-182`), `visitConstSBoolean` (`:185-187`), `visitDefSBoolean` (`:190-192`), `visitConstUInteger` (`:207-209`), `visitConstUReal` (`:217-219`), `visitConstUString` (`:229-231`) — each `writer.write(literal(exp.toString(), exp));`; `visitUSelect` (`:452`) and `visitUSelectC` (`:455-457`) — each `visitQuery(exp);`. `literal(String, Expression)` exists in T at `:96`; `visitQuery(ExpQuery)` at T `:395`. |
| 2 | `/home/xoruser/msc-4/use-msc2026/use-core/src/main/java/org/tzi/use/analysis/coverage/AbstractCoverageVisitor.java` | **EDIT — required in practice** | `public abstract class AbstractCoverageVisitor implements ExpressionVisitor` (`:33`). It is abstract, so javac would *technically* let the new methods go unimplemented — but it currently implements all 49 concretely (`grep -c "public void visit"` → 49), and its two concrete subclasses (5.2 #3, #4) would then fail. Add all 8. Fork bodies (verified, `FORK/.../coverage/AbstractCoverageVisitor.java`): `visitConstUBoolean` (`:103-105`), `visitConstSBoolean` (`:108-110`), `visitDefSBoolean` (`:113-115`), `visitConstUInteger` (`:127-129`), `visitConstUReal` (`:136-138`), `visitConstUString` (`:145-147`) — **all six empty**; `visitUSelect` (`:278-280`) and `visitUSelectC` (`:283-285`) — both `visitQuery(exp);`. This matches the existing 7.5.0 precedent: `visitConstBoolean` (T `:100`) is empty, `visitSelect` (T `:242`) delegates to `visitQuery`. |

### 5.2 Transitive subclasses — inherit, but on the compile-break blast radius

These compile unchanged **only if** #1 and #2 are edited. List them so nobody
declares victory before checking them.

| # | File (absolute) | Relationship | Action |
|---|---|---|---|
| 3 | `/home/xoruser/msc-4/use-msc2026/use-core/src/main/java/org/tzi/use/analysis/coverage/CoverageCalculationVisitor.java` | `extends AbstractCoverageVisitor` (`:38`), concrete | none if #2 done; **breaks if #2 left abstract** |
| 4 | `/home/xoruser/msc-4/use-msc2026/use-core/src/main/java/org/tzi/use/analysis/coverage/BasicExpressionCoverageCalulator.java` | `extends AbstractCoverageVisitor` (`:40`), concrete | none if #2 done. NB: the fork version of this file `import`s `ExpConstUReal` (`:29`) but declares **no** `visitConstUReal` — a stray unused import. Do not port the import. |
| 5 | `/home/xoruser/msc-4/use-msc2026/use-core/src/main/java/org/tzi/use/uml/ocl/expr/GenerateHTMLExpressionVisitor.java` | `extends ExpressionPrintVisitor` (`:30`) | none — it overrides only `quoteContent` (`:40`), `formatOperation` (`:50`), `formatKeyword` (`:55`). Inherits the 8 new methods. |
| 6 | `/home/xoruser/msc-4/use-msc2026/use-core/src/main/java/org/tzi/use/uml/ocl/expr/EvalNode.java` — inner `private class RelevantOperationHighlightVisitor extends GenerateHTMLExpressionVisitor` (`:618`) | 2-deep from `ExpressionPrintVisitor` | none |
| 7 | `/home/xoruser/msc-4/use-msc2026/use-core/src/main/java/org/tzi/use/uml/ocl/expr/EvalNode.java` — inner `private class SubstituteVariablesExpressionVisitor extends RelevantOperationHighlightVisitor` (`:351`) | 3-deep | none. Instantiated at `EvalNode.java:243,345`. |

### 5.3 Files that merely *reference* the type — no method to add

| File | Reference |
|---|---|
| `/home/xoruser/msc-4/use-msc2026/use-core/src/main/java/org/tzi/use/uml/mm/MMPrintVisitor.java` | `protected ExpressionVisitor createExpressionVisitor()` (`:422`), call sites `:265,289,294,415,497,532` |
| `/home/xoruser/msc-4/use-msc2026/use-gui/src/main/java/org/tzi/use/gui/util/MMHTMLPrintVisitor.java` | overrides `createExpressionVisitor()` (`:47-49`) returning `GenerateHTMLExpressionVisitor` |
| `/home/xoruser/msc-4/use-msc2026/use-gui/src/main/java/org/tzi/use/gui/utilFX/MMHTMLPrintVisitor.java` | same (`:47-49`) |
| all `T/.../expr/Exp*.java` | `processWithVisitor(ExpressionVisitor)` parameter type only |

### 5.4 Negative results (checked, nothing found)

* No anonymous implementors: `grep -rn "new ExpressionVisitor"` over `use-core`
  + `use-gui`, **0 hits**.
* No test-side implementors: `grep -rn "ExpressionVisitor" use-core/src/test`,
  **0 hits**.
* No implementor in `use-gui` at all — the GUI only *uses*
  `GenerateHTMLExpressionVisitor`.
* `use-assembly`, `manual`, `documentation` contain no Java implementing it
  (covered by the repo-wide greps in §7 cmds 9-10).

### 5.5 Fork-only visitors that are **out of scope**

The fork's `org/tzi/use/analysis/metrics/` package
(`AbstractMetricVisitor.java:80,85,121,…`, `GSMetricVisitor.java:124`) also
implements `ExpressionVisitor`. **That whole package does not exist in 7.5.0**
(§7 cmd 12: `find use-core/src/main/java/org/tzi/use/analysis` lists only
`coverage/`, 7 files; the fork lists `coverage/` + `metrics/`, 7 + 15 files). It
is a separate fork feature (GSMetric / shell metric commands), unrelated to
uncertainty. Do not port it as part of this section; if it is ever ported it
will need the same 8 methods.

---

## 6. New-file / edit manifest

### 6.1 NEW FILES (straight port, `T/uml/ocl/expr/`)

| File | Source | Decision |
|---|---|---|
| `ExpConstUBoolean.java` | fork, 78 L | port |
| `ExpConstUInteger.java` | fork, 68 L | port |
| `ExpConstUReal.java` | fork, 69 L | port (add the missing type guards? — flag as open) |
| `ExpConstUString.java` | fork, 75 L | port |
| `ExpConstSBoolean.java` | fork, 88 L | port |
| `ExpUSelect.java` | fork, 49 L | port |
| `ExpUSelectC.java` | fork, 37 L | port |
| `ExpDefSBoolean.java` | fork, 48 L | **DROP — dead code, see §2.6** |

### 6.2 EDITS TO UPSTREAM FILES

| File | Edit |
|---|---|
| `T/uml/ocl/expr/ExpQuery.java` | add `fUncertaintyExp`, 5-arg ctor, `assertKindOfUBoolean()`, `evalUSelect()`, `evalAndAsertConfident()`, `getUncertaintyExpression()`. Purely additive. Keep 7.5.0's `evalSortedBy` (OrderedSet branch) and 7.5.0's `evalExistsOrForAll`. Apply the undefined-range cast fix (§3.3.3). |
| `T/uml/ocl/expr/ExpressionVisitor.java` | add 7 methods (8 if `ExpDefSBoolean` is kept). Hand-edit; do not copy the fork file. |
| `T/uml/ocl/expr/ExpressionPrintVisitor.java` | implement the 7/8 new methods. |
| `T/analysis/coverage/AbstractCoverageVisitor.java` | implement the 7/8 new methods (empty for literals; `visitQuery(exp)` for the two uSelects). |

### 6.3 EDITS DEFERRED / CONDITIONAL

| File | Edit | Condition |
|---|---|---|
| `T/uml/ocl/expr/ExpExists.java` | `assertBooleanQuery()` → `assertKindOfUBoolean()` | only together with `ExpQuery` items 7+8 |
| `T/uml/ocl/expr/ExpForAll.java` | same | same |
| `T/uml/ocl/expr/ExpSelect.java`, `ExpReject.java` | **none** — identical to fork modulo `$Id`/`@version` headers | — |
| `T/uml/ocl/expr/Expression.java` | **none** — no member differences | — |

### 6.4 Do NOT port

* `ExpDefSBoolean.java` + `parser/ocl/ASTSBooleanDefExpression.java` (dead).
* `import org.tzi.use.uml.ocl.type.UncertainType;` in `ExpQuery` (unused).
* the fork's deprecated `evalExistsOrForAll0` (dead in the fork too).
* the fork's `ExpressionVisitor.visitObjOp(ExpObjOp)` (7.5.0 uses
  `visitInstanceOp(ExpInstanceOp)`).
* `$Id:` / `@version $ProjectVersion:` header lines.
* the fork's `evalSortedBy` (older than 7.5.0's).

---

## 7. Reproduce

Run from
`/home/xoruser/msc-4/use-msc2026/.git/reference-repositories/uncertainty/USE-Uncertainty`
unless the command shows another `cd`. All commands are read-only.

```bash
# 1 — Expression base class is unchanged
cd /home/xoruser/msc-4/use-msc2026 && diff -u --strip-trailing-cr \
  use-core/src/main/java/org/tzi/use/uml/ocl/expr/Expression.java \
  .git/reference-repositories/uncertainty/USE-Uncertainty/src/main/org/tzi/use/uml/ocl/expr/Expression.java

# 2 — one file per type, with sizes
cd /home/xoruser/msc-4/use-msc2026/.git/reference-repositories/uncertainty/USE-Uncertainty/src/main/org/tzi/use/uml/ocl/expr \
  && wc -l -c ExpConstUBoolean.java ExpConstUInteger.java ExpConstUReal.java \
              ExpConstUString.java ExpConstSBoolean.java ExpDefSBoolean.java \
              ExpUSelect.java ExpUSelectC.java ExpQuery.java ExpressionVisitor.java

# 3 — every ExpDefSBoolean reference in the fork  (8 hits, 6 files)
cd /home/xoruser/msc-4/use-msc2026/.git/reference-repositories/uncertainty/USE-Uncertainty \
  && grep -rn "ExpDefSBoolean\|visitDefSBoolean" src/ | sort

# 4 — is ASTSBooleanDefExpression ever constructed?  (no `new` anywhere)
grep -rn "ASTSBooleanDefExpression\|ASTSBooleanLiteral" src/ | sort

# 5 — per-class file lists (shows no U-type file depends on ExpDefSBoolean)
for s in ExpConstUBoolean ExpConstUInteger ExpConstUReal ExpConstUString \
         ExpConstSBoolean ExpDefSBoolean ExpUSelect ExpUSelectC; do \
  echo "--- $s ---"; grep -rl "\b$s\b" src/ | sort; done

# 6 — evalExistsOrForAll0 is dead in the fork
grep -n "evalExistsOrForAll0\|evalExists0\|evalForAll0\|evalUSelect\|assertKindOfUBoolean" \
  src/main/org/tzi/use/uml/ocl/expr/ExpQuery.java

# 7 — query-subclass diffs fork vs 7.5.0
cd /home/xoruser/msc-4/use-msc2026 && for f in ExpExists ExpForAll ExpSelect ExpReject; do \
  echo "===== $f ====="; diff -u use-core/src/main/java/org/tzi/use/uml/ocl/expr/$f.java \
  .git/reference-repositories/uncertainty/USE-Uncertainty/src/main/org/tzi/use/uml/ocl/expr/$f.java; done

# 8 — visitor method counts: 49 (7.5.0) vs 57 (fork)
cd /home/xoruser/msc-4/use-msc2026 \
  && grep -c "^\s*void visit" use-core/src/main/java/org/tzi/use/uml/ocl/expr/ExpressionVisitor.java \
  && grep -c "^\s*void visit" .git/reference-repositories/uncertainty/USE-Uncertainty/src/main/org/tzi/use/uml/ocl/expr/ExpressionVisitor.java

# 9 — direct implementors in the 7.5.0 tree  (2 hits)
cd /home/xoruser/msc-4/use-msc2026 \
  && grep -rn "implements[^{]*ExpressionVisitor" --include=*.java . | grep -v reference-repositories

# 10 — transitive subclasses + anonymous implementors
grep -rn "extends ExpressionPrintVisitor\|extends AbstractCoverageVisitor\|extends GenerateHTMLExpressionVisitor\|extends RelevantOperationHighlightVisitor\|new ExpressionVisitor" \
  --include=*.java . | grep -v reference-repositories

# 11 — AbstractCoverageVisitor implements all 49 concretely
grep -c "public void visit" use-core/src/main/java/org/tzi/use/analysis/coverage/AbstractCoverageVisitor.java

# 12 — analysis package: fork has metrics/, 7.5.0 does not
find use-core/src/main/java/org/tzi/use/analysis -name "*.java" | sort
find .git/reference-repositories/uncertainty/USE-Uncertainty/src/main/org/tzi/use/analysis -name "*.java" | sort

# 13 — none of the uncertainty substrate exists in 7.5.0
grep -n "mkUBoolean\|mkUReal\|mkUInteger\|mkUString\|mkSBoolean" \
  use-core/src/main/java/org/tzi/use/uml/ocl/type/TypeFactory.java
grep -n "isKindOfUBoolean\|isTypeOfUBoolean" use-core/src/main/java/org/tzi/use/uml/ocl/type/Type.java
grep -n "isUBoolean\|isUReal\|isUInteger\|isUString\|isSBoolean" use-core/src/main/java/org/tzi/use/uml/ocl/value/Value.java
ls use-core/src/main/java/org/tzi/use/uml/ocl/value/ | grep -i "^U\|^SBool"

# 14 — inverted guard evidence for ExpDefSBoolean
cd /home/xoruser/msc-4/use-msc2026/.git/reference-repositories/uncertainty/USE-Uncertainty/src/main/org/tzi/use/uml/ocl/type \
  && grep -n -A2 "isKindOfUBoolean" BooleanType.java UBooleanType.java TypeImpl.java

# 15 — UndefinedValue is not a CollectionValue (evalUSelect cast defect)
cd /home/xoruser/msc-4/use-msc2026/.git/reference-repositories/uncertainty/USE-Uncertainty/src/main/org/tzi/use/uml/ocl/value \
  && grep -n "class UndefinedValue" UndefinedValue.java && grep -n "class CollectionValue" CollectionValue.java
```

---

## 8. Gaps — things marked UNVERIFIABLE

1. **Reachability of `SBoolean(x)` outside `FORK/src`.** The greps in §2.4 cover
   `FORK/src` only. Whether some `.use` model, example, or downstream tool
   shipped outside the source tree uses a 1-argument `SBoolean(x)` is
   `UNVERIFIABLE` from source. Mitigation: none of the six ANTLR grammars has a
   rule producing `ASTSBooleanDefExpression`, so no parser in the fork can
   accept that syntax — the question is moot in practice.
2. **Nothing was compiled.** Per the ground rules Maven was not run. All
   compile-break claims in §5 are derived from reading declarations and
   inheritance edges, not from javac output. `UNVERIFIABLE` until the port is
   actually built.
3. **Whether `ExpStdOp` in 7.5.0 can fold `UBoolean` operands** — required by
   `ExpQuery` items 7+8 (`ExpStdOp.create("and"/"or", …)` over
   `ExpressionWithValue`). That depends on the operations/`StandardOperations`
   section of the port, which this section did not read. `UNVERIFIABLE` here;
   flagged as a cross-section dependency.
4. **Intended `toString` separator convention.** `ExpConstUInteger` uses `", "`
   while the other four use `","`. Whether that asymmetry is deliberate or a
   typo is `UNVERIFIABLE`; no test in `FORK/src/test` asserts a `toString` form
   for these literals (the uncertainty tests assert `Value` equality, e.g.
   `ExpQueryUncertaintyTest.java:88,102,223,233`).
5. **`ExpConstUReal`'s missing type guards** — whether the omission was
   deliberate (to allow Integer-typed operands without a `isKindOfReal` call) or
   an oversight is `UNVERIFIABLE`. Recorded as an open decision in §6.1.
