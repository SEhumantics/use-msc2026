# S1.5 — Upstream shape-delta: fork base → USE 7.5.0

**Scope.** What the port must adapt across, subsystem by subsystem. Every claim below names a file
and line, a symbol, or the shell command that produced it. Anything I could not establish is marked
`UNVERIFIABLE`.

**Path shorthand used throughout:**

```
FORK   = /home/xoruser/msc-4/use-msc2026/.git/reference-repositories/uncertainty/USE-Uncertainty
FSRC   = $FORK/src/main/org/tzi/use
TSRC   = /home/xoruser/msc-4/use-msc2026/use-core/src/main/java/org/tzi/use
UDT    = /home/xoruser/msc-4/use-msc2026/.git/reference-repositories/uncertainty/uDataTypes
UP     = /home/xoruser/msc-4/use-msc2026/.git/reference-repositories/upstream-use
```

`$FORK`, `$UDT`, `$UP` are read-only reference material and are **never** build inputs.

---

## 0. Headline: the drift is far smaller than the ten-year gap suggests

The single most important finding of this section, and the one that should drive S3 planning:

> **The four OCL extension points the port needs — `Value`, `Type`/`TypeImpl`, `ExpressionVisitor`,
> `OpGeneric` — have barely moved in ten years.** `Value.java` and `OpGeneric.java` are *textually
> identical* between the fork and 7.5.0 except for the fork's own additions. The real risk is not in
> those four files. It is concentrated in (a) `MClassifier`, which was substantially reshaped by the
> 2024 data-type work, (b) the build/module story, which is entirely new, and (c) the uDataTypes
> dependency, which has no Maven coordinates anywhere.

### 0.1 Correction to the brief: the fork's base is not 2015, it is ~2018

The task brief says "built against a USE around 4.1.1 (2015)". The SVN `$Id$` keywords still present
in the fork's sources put the bulk of the base at r5494 / 2015-02-05, but with cherry-picks running
to 2018:

```bash
cd $FORK && grep -rhoE '\$Id: [^ ]+ [0-9]+ [0-9]{4}-[0-9]{2}-[0-9]{2}' src/main --include=*.java \
  | awk '{print $3, $4}' | sort -n | uniq -c | tail -8
```
```
      1 5991 2016-06-21
      1 6117 2016-12-14
      1 6121 2016-12-22
      1 6272 2017-08-24
      3 6289 2017-11-27
      1 6361 2018-04-05
```

* 163 of the fork's files carry `$Id: … 5494 2015-02-05`.
* The newest is `$FSRC/config/Options.java` at r6361 / 2018-04-05, which declares
  `RELEASE_VERSION = "0.142.0"` (`$FSRC/config/Options.java:51`).
* `$FSRC/uml/ocl/value/RealValue.java` and `IntegerValue.java` carry r6289 / 2017-11-27.

This matters practically: **the fork's base already contains `Type.VoidHandling`**, which upstream
introduced in `750fa544` (2015-02-05, "Reintegrated PDM-branch (switch to USE Version 4)"):

```bash
git -C $UP log --all --format='%h|%ad|%s' --date=short -S'VoidHandling' -- '**/ocl/type/Type.java' | tail -2
```
```
fb866f31|2024-04-22|USE now supports data types
750fa544|2015-02-05|- Reintegrated PDM-branch (switch to USE Version 4)
```

So the fork is on the *far* side of the `VoidHandling` boundary, and every `isKindOfX(VoidHandling)`
signature in the fork already matches 7.5.0. That eliminates what would otherwise have been the
single largest mechanical adaptation in the type system.

**Language level.** The fork compiles at `source/target 1.7` (`$FORK/build.xml:16-17`). The target
compiles at 21 (`use-core/pom.xml:16-17`, root `pom.xml:19-20`). Fork sources use no removed
constructs, but they also use none of Java 8+; the port may modernise or not, and this is a style
decision with no correctness content. Note that JDK 21's `javac` cannot emit `-source 7` at all, so
"copy verbatim and hope" is not a strategy for anything that fails to compile.

---

## 1. `uml/ocl/value/Value` — the contract for an added value class

### (a) The fork's version

`$FSRC/uml/ocl/value/Value.java`, 230 lines. Abstract class, `implements Comparable<Value>,
BufferedToString`, one private field `fType`, a family of `isX()` predicates all defaulting to
`false`, and three abstract members.

The fork widened the predicate family with five methods, at these lines:

| Fork line | Added predicate |
|---|---|
| `Value.java:67` | `isUInteger()` |
| `Value.java:84` | `isUReal()` |
| `Value.java:100` | `isUBoolean()` |
| `Value.java:108` | `isSBoolean()` |

(`isUString()` is **not** on `Value`; `UStringValue` is reported only through its type. Verified:
`grep -n "isUString" $FSRC/uml/ocl/value/Value.java` → no match.)

### (b) What 7.5.0 looks like

`$TSRC/uml/ocl/value/Value.java`, 194 lines. **Identical to the fork's file** except for the fork's
four added predicates and two dropped SVN keyword comments:

```bash
diff <(sed 's/[[:space:]]*$//' $FSRC/uml/ocl/value/Value.java) \
     <(sed 's/[[:space:]]*$//' $TSRC/uml/ocl/value/Value.java)
```
Output is five hunks: `20,21d19` and `35d32` (the `$Id`/`$ProjectVersion` lines), and
`64,71d60` / `80,88d68` / `93,108d72` (the fork's `isUInteger`, `isUReal`, `isUBoolean`,
`isSBoolean`). **There is no upstream-side change to `Value` at all.**

The full contract an added value class must satisfy in 7.5.0:

| Obligation | Where |
|---|---|
| `extends Value`, call `super(Type t)` | `Value.java:36`, `Value.java:40` |
| `public abstract StringBuilder toString(StringBuilder sb)` — from `BufferedToString` | `Value.java:160`; interface at `org/tzi/use/util/BufferedToString.java` |
| `public abstract int hashCode()` | `Value.java:175` |
| `public abstract boolean equals(Object obj)` — **OCL semantics**, must handle `UndefinedValue` | `Value.java:183` |
| `int compareTo(Value)` — from `Comparable<Value>`; **not** re-declared abstract on `Value`, so `javac` will only complain when the class is concrete | `Value.java:36` |
| Override the relevant `isX()` predicate(s) | `Value.java:56-150` |
| `isUndefined()` defaults `false`; `isDefined()` is `!isUndefined()` and is **not** overridable-by-accident (it is concrete, delegating) | `Value.java:84-94` |
| `type()` / `getRuntimeType()` — `getRuntimeType()` is overridable and is what `toStringWithType` prints | `Value.java:44-50`, `Value.java:168-172` |
| `toString()` is `final` — you cannot override it, only `toString(StringBuilder)` | `Value.java:153` |
| `setTypeToRuntimeType()` — concrete, mutates `fType` | `Value.java:190` |

`compareTo` has a documented total-order obligation stated in the class Javadoc
(`Value.java:26-31`): *"all values must be able of being compared with an `UndefinedValue`"*. The
established convention, from `RealValue.compareTo` (`$TSRC/uml/ocl/value/RealValue.java:72-85`), is
`return +1` when the argument is an `UndefinedValue`, and `toString().compareTo(o.toString())` as
the fallback for unrelated types. `RealValue.hashCode` carries an explicit cross-type constraint
(`RealValue.java:66-68`): *"this must be the same hash code as for `IntegerValue`"*. Any new numeric
value class that is expected to live in the same `Set`/`Bag` as `RealValue` inherits that constraint.

### (c) The concrete adaptation

Almost none. The port adds four predicates to `Value.java` at the same places the fork did, and adds
seven new value classes (§6.1). The abstract-member set, the `final toString()`, the
`BufferedToString` contract, and the `Comparable<Value>` obligation are all unchanged from 2015.

**The one real trap in this subsystem is elsewhere in the package**:

```bash
diff <(sed 's/[[:space:]]*$//' $FSRC/uml/ocl/value/UndefinedValue.java) \
     <(sed 's/[[:space:]]*$//' $TSRC/uml/ocl/value/UndefinedValue.java)
```
```
46c43
<         return sb.append("Undefined");
---
>         return sb.append("null");
```

Upstream renamed the printed form of undefined in `72ab8fd7`, 2019-06-27, commit message
*"changed Undefined to null"*:

```bash
git -C $UP log --all --format='%h|%ad|%s' --date=short -S'sb.append("null")' -- '**/UndefinedValue.java'
```
```
72ab8fd7|2019-06-27|changed Undefined to null
```

The fork also added a static convenience `RealValue.valueOf(Value)` that 7.5.0 lacks
(fork `RealValue.java:90-100`); the fork's `StandardOperationsNumber` calls it.

### (d) Risk if got wrong

* **Highest**: `UndefinedValue` prints `null` in 7.5.0 and `Undefined` in the fork. Any differential
  harness that compares *printed* values will report a false positive on every undefined result, and
  any expected-output fixture lifted verbatim from the fork will be wrong. This is a whole-suite
  systematic offset, not a one-off — it must be normalised in the harness or in the fixtures, and
  the decision must be recorded, because "the port prints `null` where the oracle prints
  `Undefined`" is a *correct* port, not a regression.
* Forgetting `compareTo` is caught by the compiler only for concrete classes; an abstract
  intermediate (the fork has one, `UncertainValue`) can silently omit it and push the failure into
  the leaves.
* Getting `equals`/`hashCode` inconsistent between a new numeric value and `RealValue`/`IntegerValue`
  silently corrupts `Set`/`Bag` semantics — no exception, wrong OCL answers.

---

## 2. `uml/ocl/type/Type` and the `MClassifier` hierarchy

### 2.1 `Type` — class vs interface, and what moved

**Both trees:** `Type` is an **interface** (`$FSRC/uml/ocl/type/Type.java:33`,
`$TSRC/uml/ocl/type/Type.java:31`), extending only `BufferedToString`. `TypeImpl` is
`public abstract class TypeImpl implements Type` in both (fork `TypeImpl.java:34`, target
`TypeImpl.java:32`) and supplies a `false`-returning default for every predicate. `BasicType extends
TypeImpl` and adds the `fTypename` + `equals`-by-class + `hashCode`-by-class behaviour
(`$TSRC/uml/ocl/type/BasicType.java:28-60`). `MClassifier extends Type, MModelElement,
MNamedElement, UseFileLocatable` — **the same declaration, on the same line (36), in both trees**.

There are **no generics** on `Type`. The only generic signature is
`Set<? extends Type> allSupertypes()` (`Type.java:63`), identical in both. `MDataType` and `MClass`
narrow the *return type* of `parents()`/`allParents()` covariantly
(`$TSRC/uml/mm/MDataType.java:43,53`) but this does not touch `Type`.

Upstream added exactly **three** members to the `Type` interface since the fork's base:

| 7.5.0 member | Line | Introduced |
|---|---|---|
| `String qualifiedName();` | `Type.java:48` | `4dd26e4d`, 2025-06-10, model-qualified imports |
| `boolean isKindOfDataType(VoidHandling h);` | `Type.java:136` | `fb866f31`, 2024-04-22, "USE now supports data types" |
| `boolean isTypeOfDataType();` | `Type.java:138` | same |

`TypeImpl` supplies `false` defaults for the two data-type predicates
(`$TSRC/uml/ocl/type/TypeImpl.java:288-297`) and `qualifiedName() { return toString(); }`
(`TypeImpl.java:43-47`). So a new type extending `TypeImpl`/`BasicType` inherits correct behaviour
for all three and need not implement anything new.

The fork added ten members to `Type` (fork `Type.java:84,86,92,94,104,106,112,114,116,118`):
`isTypeOfUInteger`, `isKindOfUInteger`, `isTypeOfUReal`, `isKindOfUReal`, `isTypeOfUString`,
`isKindOfUString`, `isKindOfUBoolean`, `isTypeOfUBoolean`, `isKindOfSBoolean`, `isTypeOfSBoolean` —
with matching `false` defaults in `TypeImpl` and in `MClassifierImpl`.

### 2.2 How `conformsTo` dispatches

`conformsTo(Type other)` is declared on the interface (`Type.java:58`) and is **single-dispatch,
implemented per concrete type**, not table-driven. There is no registry and no double dispatch. The
default is in `TypeImpl` and each basic type overrides it. Example, 7.5.0 `RealType`:

```java
return equals(t) || t.isTypeOfOclAny();          // $TSRC/uml/ocl/type/RealType.java (7.5.0)
```

The fork widened exactly those overrides to admit its new types:

| File | Fork | 7.5.0 |
|---|---|---|
| `RealType.conformsTo` | `equals(t) \|\| t.isTypeOfOclAny() \|\| t.isTypeOfUReal()` | `equals(t) \|\| t.isTypeOfOclAny()` |
| `BooleanType.conformsTo` | `… \|\| other.isTypeOfUBoolean() \|\| … \|\| other.isTypeOfSBoolean()` | `this.equals(other) \|\| other.isTypeOfOclAny()` |
| `StringType.conformsTo` | `… \|\| t.isTypeOfUString()` | `equals(t) \|\| t.isTypeOfOclAny()` |

and correspondingly widened `allSupertypes()` in each (fork adds `TypeFactory.mkUReal()` to
`RealType`, `mkUReal()`+`mkUInteger()` to `IntegerType`, `mkUBoolean()`+`mkSBoolean()` to
`BooleanType`, `mkUString()` to `StringType`). Note the fork also had to change
`IntegerType.allSupertypes`' `HashSet` capacity hint from 3 to 5.

The *second* half of conformance is `getLeastCommonSupertype`, implemented once in
`TypeImpl.getLeastCommonSupertype` (`$TSRC/uml/ocl/type/TypeImpl.java:84-…`) as
`allSupertypes() ∩ other.allSupertypes()` narrowed by
`UniqueLeastCommonSupertypeDeterminator`. That helper is **byte-identical** between the trees apart
from a dropped `$Id` line:

```bash
diff <(sed 's/[[:space:]]*$//' $FSRC/uml/ocl/type/UniqueLeastCommonSupertypeDeterminator.java) \
     <(sed 's/[[:space:]]*$//' $TSRC/uml/ocl/type/UniqueLeastCommonSupertypeDeterminator.java)
# only: 20,21d19  ($Id)
```

This is load-bearing: **operation resolution goes through `getLeastCommonSupertype`, not through
`conformsTo`** (`Op_equal.matches` at `$TSRC/uml/ocl/expr/operations/StandardOperationsAny.java:45-50`
tests `params[0].getLeastCommonSupertype(params[1]) != null`). A new type that overrides
`conformsTo` but forgets `allSupertypes()` will type-check in isolation and then fail to resolve
`=`, `<>`, `+` and every other overloaded operator, with the unhelpful message
`Undefined operation '…'` from `ExpStdOp.create` (`$TSRC/uml/ocl/expr/ExpStdOp.java:…`, the
`throw new ExpInvalidException("Undefined operation `"` branch).

### 2.3 How a new primitive-ish type gets registered

Mechanism is unchanged. Three places, all in `TypeFactory`:

1. A private static singleton field — `$TSRC/uml/ocl/type/TypeFactory.java:42-48`.
2. An entry in the static `buildInTypesMap` initialiser — `TypeFactory.java:50-58`.
3. A `mkX()` accessor — `TypeFactory.java:66` onwards.

The parser reaches it through exactly one call site:
`$TSRC/parser/ocl/ASTSimpleType.java:47` → `TypeFactory.mkSimpleType(name)` →
`buildInTypesMap.get(typeName)` (`TypeFactory.java:132-139`). **There is no lexer keyword for a
primitive type name**; `UReal` in a model file is an `IDENT` resolved through the map. Verified: the
fork's grammar diff (§4.3) contains no new type-name token.

The fork's registrations are at `$FSRC/uml/ocl/type/TypeFactory.java:48-67, 83, 93-94, 101, 107-111`.

### 2.4 The `MClassifier` hierarchy — this is where the real drift is

`fb866f31` (2024-04-22, "USE now supports data types") reshaped `MClassifier`. Measured drift on the
one `mm` file the fork touched:

```bash
diff <(grep -vE '\$Id|\$ProjectVersion' $FSRC/uml/mm/MClassifierImpl.java | sed 's/[[:space:]]*$//') \
     <(grep -vE '\$Id|\$ProjectVersion' $TSRC/uml/mm/MClassifierImpl.java | sed 's/[[:space:]]*$//') \
  | grep -cE '^[<>]'
```
```
226
```

Concretely:

| Fork | 7.5.0 | Consequence for the port |
|---|---|---|
| `MClassifier.isSubClassOf(MClassifier)` / `(MClassifier, boolean)` (fork `MClassifier.java:114,123`) | **renamed** `isSubClassifierOf(...)` (`$TSRC/uml/mm/MClassifier.java:116,125`) | rename every call site |
| `MClassifierImpl.attribute(name, searchInherited)` returned `null` unconditionally (fork `MClassifierImpl.java:497-500`); attributes lived on `MClass` | attributes and operations **pulled up** to `MClassifier`: `attributes()`, `allAttributes()`, `operations()`, `operation(String, boolean)` declared on the interface (`MClassifier.java:136-168`); `MClassifierImpl` owns `fAttributes`, `fOperations`, `fVTableOperations` (`MClassifierImpl.java:52-65`) | any fork code that downcast to `MClass` to reach attributes should be rewritten against `MClassifier` |
| — | `isQualifiedAccess()` / `setQualifiedAccess(boolean)` (`MClassifier.java:59-63`) | new, no fork counterpart |
| — | `hasStateMachineWhichHandles(MOperationCall)` (`MClassifier.java:184`) | new |
| — | `String qualifiedName()` implemented as `model.name() + "#" + name()` (`MClassifierImpl.java:390-392`) | new |
| — | new sibling `MDataType extends MClassifier` + `MDataTypeImpl` | a *third* classifier kind exists; anything that assumed `MClassifier ⇒ MClass` is now wrong |
| `MMVisitor` had no data-type case | `void visitDataType(MDataType e);` (`$TSRC/uml/mm/MMVisitor.java:39`) | implementors must add it |
| `ModelFactory.createOperation(name, varDeclList, resultType)` | `createOperation(name, varDeclList, resultType, boolean isConstructor)` (`$TSRC/uml/mm/ModelFactory.java:76-78`) | **signature change**, breaks callers |
| `ModelFactory.createClassInvariant(name, vars, MClass cls, …)` | `…(name, vars, MClassifier cf, …)` (`ModelFactory.java:56-60`) | widened parameter type |
| — | `ModelFactory.createDataType(String, boolean)` (`ModelFactory.java:43-46`) | new |
| — | `MImportedModel`, `TestModelUtil` are new files in `uml/mm` | — |

### (c) The concrete adaptation

1. Add the fork's ten `isTypeOfX`/`isKindOfX` declarations to `Type`, plus `false` defaults in
   **both** `TypeImpl` and `MClassifierImpl` (the fork had to patch both — see fork
   `MClassifierImpl.java:201-280`; in 7.5.0 the same overrides go in the same class).
2. Add the five new type singletons + map entries + `mkX()` accessors to `TypeFactory`.
3. Widen `conformsTo` and `allSupertypes` on `RealType`, `IntegerType`, `BooleanType`, `StringType`.
4. Port the seven `*Type` classes (§6.1) against 7.5.0's `TypeImpl`, which now also demands nothing
   new — they inherit `qualifiedName`, `isKindOfDataType`, `isTypeOfDataType`.
5. Rename `isSubClassOf` → `isSubClassifierOf` anywhere the fork's code calls it.

### (d) Risk if got wrong

* **`allSupertypes()` forgotten** → silent, total failure of overload resolution for the new types,
  reported as a *parse* error, sending the investigation to the grammar instead of the type lattice.
* **Adding to `Type` without adding to `MClassifierImpl`** → `use-core` fails to compile, because
  `MClassifierImpl` implements `Type` independently of `TypeImpl`. Cheap failure, but it will look
  like a mystery if you only remember `TypeImpl`.
* **Assuming `MClassifier ⇒ MClass`** → `ClassCastException` at model-load time as soon as a model
  declares a `dataType`. The fork could not have this bug; the port can.
* **`isKindOfDataType` returning the wrong thing for a new uncertain type** — the correct answer is
  `false` (inherited). If someone "helpfully" returns `true` because `UReal` feels like a data type,
  they change conformance and `oclIsKindOf` results across the whole model.

---

## 3. `uml/ocl/expr/Expression` and `ExpressionVisitor`

### (a) The fork's version

`ExpressionVisitor` is a **plain (non-sealed) interface**, 57 `void visit…` methods
(`grep -cE '^\s*void visit' $FSRC/uml/ocl/expr/ExpressionVisitor.java` → `57`). The fork added eight
of them: `visitConstUBoolean`, `visitConstSBoolean`, `visitDefSBoolean` (lines 38-40),
`visitConstUInteger` (43), `visitConstUReal` (45), `visitConstUString` (47), `visitUSelect`,
`visitUSelectC` (85-86).

The fork has **three** implementors: `$FSRC/analysis/coverage/AbstractCoverageVisitor.java:36`,
`$FSRC/analysis/metrics/AbstractMetricVisitor.java:32`,
`$FSRC/uml/ocl/expr/ExpressionPrintVisitor.java:35`.

### (b) What 7.5.0 looks like

`ExpressionVisitor` is still a **plain, non-sealed interface**, 49 methods
(`$TSRC/uml/ocl/expr/ExpressionVisitor.java:27-77`). There are **no `sealed`/`permits` declarations
anywhere in the product**:

```bash
grep -rn '\bsealed\b\|\bpermits\b' --include=*.java use-core/src/main use-gui/src/main   # no output
```

So dispatch is **not** exhaustive in the compiler's eyes. Adding a `visitX` method to the interface
breaks every implementor at compile time — which is the desired behaviour — but adding a new
`Expression` subclass *without* touching the interface compiles fine and fails at runtime only if a
visitor is ever run over it.

`Expression` itself is unchanged. The abstract surface is:

| Member | Line |
|---|---|
| `public abstract Value eval(EvalContext ctx)` | `$TSRC/uml/ocl/expr/Expression.java:79` |
| `protected abstract boolean childExpressionRequiresPreState()` | `Expression.java:133` |
| `public abstract StringBuilder toString(StringBuilder sb)` | `Expression.java:147` |
| `public abstract void processWithVisitor(ExpressionVisitor visitor)` | `Expression.java:178` |
| `public final String toString()` | `Expression.java:138` |

```bash
diff <(sed 's/[[:space:]]*$//' $FSRC/uml/ocl/expr/Expression.java) \
     <(sed 's/[[:space:]]*$//' $TSRC/uml/ocl/expr/Expression.java)
```
→ four hunks, all `$Id` removal and Javadoc wording. **No API change.**

The one visitor-surface change upstream made:

| Fork | 7.5.0 |
|---|---|
| `void visitObjOp(ExpObjOp exp);` | `void visitInstanceOp(ExpInstanceOp exp);` (`ExpressionVisitor.java:51`) |

Introduced by `46c277e7`, 2024-11-24, *"Introduced ExpInstanceOp as parent class of ExpObjOp and
ExpInstanceConstructor"*. `ExpObjOp` still exists but is now
`public final class ExpObjOp extends ExpInstanceOp` (`$TSRC/uml/ocl/expr/ExpObjOp.java:35`), and
`ExpInstanceOp.processWithVisitor` calls `visitor.visitInstanceOp(this)`
(`$TSRC/uml/ocl/expr/ExpInstanceOp.java:44-46`).

**Implementor census in 7.5.0 — this is the exact set of files a new `visitX` method breaks:**

```bash
grep -rn 'implements ExpressionVisitor\|extends ExpressionPrintVisitor\|extends AbstractCoverageVisitor\|extends GenerateHTMLExpressionVisitor' \
  --include=*.java . --exclude-dir=reference-repositories
```
```
use-core/.../analysis/coverage/AbstractCoverageVisitor.java:33      implements ExpressionVisitor
use-core/.../analysis/coverage/CoverageCalculationVisitor.java:38   extends AbstractCoverageVisitor
use-core/.../analysis/coverage/BasicExpressionCoverageCalulator.java:40 extends AbstractCoverageVisitor
use-core/.../uml/ocl/expr/ExpressionPrintVisitor.java:35            implements ExpressionVisitor
use-core/.../uml/ocl/expr/GenerateHTMLExpressionVisitor.java:30     extends ExpressionPrintVisitor
use-core/.../uml/ocl/expr/EvalNode.java:618                         (inner) extends GenerateHTMLExpressionVisitor
```

**Only two root implementors**: `AbstractCoverageVisitor` and `ExpressionPrintVisitor`. `use-gui`
implements the interface nowhere. The fork's third implementor, `AbstractMetricVisitor`, belongs to
`org/tzi/use/analysis/metrics/` — a 15-file package that exists in the fork and **not** in 7.5.0. It
did exist upstream once (`git -C $UP log --all --oneline -- '**/analysis/metrics/*'` → `688b3f09`,
`fae77ebb`, `660c7dbd`) and was removed. It has nothing to do with uncertainty and is out of scope.

### (c) The concrete adaptation

* Add the fork's eight `visit…` declarations to `ExpressionVisitor`.
* Implement all eight in exactly **two** places: `AbstractCoverageVisitor` and
  `ExpressionPrintVisitor`. The three subclasses inherit.
* Do **not** port `visitObjOp`; the fork's `ExpressionPrintVisitor.visitObjOp` body must be
  transplanted into 7.5.0's `visitInstanceOp` if the port touches it at all — it does not, since the
  fork's edits to `ExpressionPrintVisitor` are confined to the uncertainty cases (49 diff lines,
  §6.2).

### (d) Risk if got wrong

* Because the interface is **not** sealed, a new `Exp*` class whose `processWithVisitor` is left
  calling some other visitor case (copy-paste from a sibling) compiles clean and silently mis-prints
  or mis-covers. There is no compiler safety net here. The port should assert one visitor round-trip
  per new expression class.
* `GenerateHTMLExpressionVisitor` and `EvalNode`'s two inner subclasses inherit from
  `ExpressionPrintVisitor`. If the new `visitX` methods are added *only* to `ExpressionPrintVisitor`
  with a default that prints nothing, the evaluation browser silently renders empty nodes for
  uncertain sub-expressions — a GUI-only defect invisible to a core-only test suite.

---

## 4. `OpGeneric` and the operation registry

### 4.1 `OpGeneric` — zero drift

```bash
diff <(sed 's/[[:space:]]*$//' $FSRC/uml/ocl/expr/operations/OpGeneric.java) \
     <(sed 's/[[:space:]]*$//' $TSRC/uml/ocl/expr/operations/OpGeneric.java)
```
```
92,98d91
< 		// Uncertainty Types
<         StandardOperationsUReal.registerTypeOperations(opmap);
<         StandardOperationsUBoolean.registerTypeOperations(opmap);
<         StandardOperationsUInteger.registerTypeOperations(opmap);
<         StandardOperationsUString.registerTypeOperations(opmap);
<         StandardOperationsSBoolean.registerTypeOperations(opmap);
<
```

**That is the entire diff.** The fork's own six lines, nothing else. Not one signature changed
between the fork's base and 7.5.0.

The `OpGeneric` subclass contract, at 7.5.0 line numbers
(`$TSRC/uml/ocl/expr/operations/OpGeneric.java`):

| Member | Line | Notes |
|---|---|---|
| `public static final int OPERATION = 0` | 34 | undefined arg ⇒ `UndefinedValue(resultType)` |
| `public static final int SPECIAL = 3` | 36 | operation handles undefined itself |
| `public abstract String name()` | 38 | the registry key |
| `public boolean isBooleanOperation()` | 40 | concrete, defaults `false` |
| `public abstract int kind()` | 44 | returns `OPERATION` or `SPECIAL` |
| `public abstract boolean isInfixOrPrefix()` | 46 | drives `stringRep` |
| `public abstract Type matches(Type params[])` | 48 | **result type or `null`**; this *is* overload resolution |
| `public String checkWarningUnrelatedTypes(Expression args[])` | 50 | concrete, defaults `null` |
| `public abstract Value eval(EvalContext ctx, Value args[], Type resultType)` | 52 | |
| `public String stringRep(Expression args[], String atPre)` | 54 | concrete |
| `static void registerOperations(Multimap<String, OpGeneric>)` | 80 | the master list |
| `static void registerOperation(OpGeneric, Multimap<…>)` | 105 | |
| `static void registerOperation(String name, OpGeneric, Multimap<…>)` | 115 | alias registration |

Note the comment at lines 25-33 mentions a `PREDICATE` kind; **there is no `PREDICATE` constant** in
either tree. Do not "restore" it.

There is no `PREDICATE`, no `getResultType()`, and no per-operation source-type declaration. The
brief's `getResultType` corresponds to `matches(Type[])`, which returns the result type as its
success signal. A `null` return means "this overload does not apply", and `ExpStdOp` then tries the
next `OpGeneric` registered under the same name.

### 4.2 The registry mechanism

`ExpStdOp` holds a static `ListMultimap<String, OpGeneric> opmap` populated once by
`OpGeneric.registerOperations(opmap)` (`$TSRC/uml/ocl/expr/ExpStdOp.java:55`). Resolution:

```java
List<OpGeneric> ops = opmap.get(name);            // ExpStdOp.java, create(...)
…
for (OpGeneric op : ops) {
    Type t = op.matches(params);
    if (t != null) { checkTypeSystemWarnings(op, args, params, t); return new ExpStdOp(op, args, t); }
}
throw new ExpInvalidException("Undefined operation `" + opCallSignature(name, args) + "'.");
```

**Registration order is significant**: the first `matches` that returns non-`null` wins. The fork
inserted its five `StandardOperationsU*` registrations *after* `StandardOperationsBoolean` and
*before* `StandardOperationsCollection` (fork `OpGeneric.java:92-98`). That position must be
preserved or overload precedence changes.

`ExpStdOp` also exposes `addOperation(OpGeneric)` and `removeAllOperations(List<OpGeneric>)` for
plugins (`ExpStdOp.java:60-74`), unchanged.

`ExpStdOp` itself has drifted only cosmetically: the diff is import reordering, the removal of
`import antlr.SemanticException`, `$Id` lines, and Javadoc wording. No behaviour change.

### 4.3 `StandardOperations*` — where the port must actually merge

| Class | fork↔7.5.0 diff lines | What changed |
|---|---|---|
| `StandardOperationsBoolean` | **0** | identical |
| `StandardOperationsString` | **0** | identical |
| `BooleanOperation` | **0** | identical |
| `StandardOperationsAny` | 158 | fork adds `Op_identical` (the `.equals()` operator) and rewrites `Op_equal.matches`/`eval` to return `UBoolean`/`SBoolean` when either operand is uncertain |
| `StandardOperationsNumber` | 763 | fork rewrites `Op_number_add`/`sub`/`mult`/`div` and the shared `matches` to fold in `UInteger`/`UReal`; **7.5.0 independently added `Op_number_pow` and `Op_number_sqrt`** (`$TSRC/…/StandardOperationsNumber.java:802,848`, registered at lines 31-32) |
| `StandardOperationsCollection` | 307 | fork registers `Op_collection_uCount`, `Op_collection_uCountC`; the remaining ~282 lines are import-wildcard collapse and upstream edits |

`Op_number_pow`/`Op_number_sqrt` have a checkered upstream history worth knowing:

```bash
git -C $UP log --all --format='%h|%ad|%s' --date=short -S'Op_number_pow' | tail -3
```
```
ac3d38cb|2024-06-25|Minor fixes
29171370|2024-06-20|Removed support for number operations 'pow' and 'sqrt' because they did not work in that implementation
fb866f31|2024-04-22|USE now supports data types
```

They are **present** at 7.5.0. The fork has no `Op_number_pow`. The port must not delete them while
merging its own numeric rewrite in.

### (c) The concrete adaptation

* `OpGeneric.java`: insert the fork's six lines verbatim at the same position. Nothing else.
* Add the five `StandardOperationsU*`/`StandardOperationsSBoolean` classes unchanged apart from
  imports (§6.1).
* **Three-way merge required** on `StandardOperationsNumber`, `StandardOperationsAny`,
  `StandardOperationsCollection` — these are the only files in this subsystem where the fork and
  upstream both edited the same functions. Treat them as merge conflicts to be resolved by hand
  against the fork's *intent*, not by taking either side wholesale.

### (d) Risk if got wrong

* Taking the fork's `StandardOperationsNumber` wholesale **deletes `pow` and `sqrt` from OCL** —
  a silent capability regression that the uncertainty test suite would never notice, and that the
  dormant upstream `ExpStdOpTest` would catch only once it is revived.
* Registering the `U*` operations in the wrong slot changes which overload wins for
  `Integer + Integer`. The fork's `Op_number_add.matches` explicitly falls through
  `isTypeOfInteger → isTypeOfReal → isTypeOfUInteger → mkUReal`; reordering registration or reordering
  those branches changes the static type of ordinary arithmetic.
* `matches` returning a non-`null` type for arguments `eval` cannot actually handle is the classic
  `OpGeneric` bug: it type-checks and then `ClassCastException`s at evaluation time.

---

## 5. Build and module story

### (a) The fork

Ant, single monolithic source tree, hand-maintained `lib/` with 11 checked-in jars
(`ls $FORK/lib`). `source`/`target` = `1.7` (`$FORK/build.xml:16-17`). Uncertainty classpath entry
at `$FORK/build.xml:50`:

```xml
<property name="uncertainty.jar" location="${lib.dir}/atenearesearchgroup.uncertainty.jar" />
```

Generated ANTLR lexers/parsers are **checked in** (12 files, e.g. `$FSRC/parser/ocl/OCLParser.java`).
Grammar `.gpart` files live next to the Java sources under `$FSRC/parser/{base,ocl,use,…}/`.

### (b) 7.5.0

**Maven reactor**, root `pom.xml` `org.tzi.use:use:7.5.0`, packaging `pom`, three modules:
`use-assembly`, `use-core`, `use-gui` (root `pom.xml:11-15`). `maven.compiler.source/target = 21`
(root `pom.xml:19-20`). Introduced by `767320db` (2021-08-01, "Maven Build"); grammars moved to
`use-core/src/main/resources/grammars/` by `99ff26c2` (2021-07-29). Lexers/parsers are now generated
at `generate-sources` by `merge-maven-plugin` + `antlr3-maven-plugin` + `copy-rename-maven-plugin`
(`use-core/pom.xml:78-277`).

**JPMS: yes, `use-core` has a module descriptor.**

```bash
find . -name module-info.java -not -path './.git/*'
```
```
./use-core/src/main/java/module-info.java
./use-gui/src/main/java/module-info.java
```

`use-core/src/main/java/module-info.java` declares `module use.core` with **11 `requires`** and
**32 `exports`** (45 lines total). The exports relevant to this port are already present:

```java
exports org.tzi.use.uml.ocl.type;              // line 25
exports org.tzi.use.uml.ocl.expr;              // line 29
exports org.tzi.use.uml.ocl.value;             // line 30
exports org.tzi.use.uml.ocl.expr.operations;   // line 31
exports org.tzi.use.uml.mm;                    // line 16
exports org.tzi.use.parser.ocl;                // line 33
exports org.tzi.use.parser.use;                // line 15
```

`use-gui/src/main/java/module-info.java` is `module use.gui { requires use.core; … }`.

**Third-party jars are declared as ordinary Maven coordinates and nothing else.** There is no
`<repositories>`, no `<pluginRepositories>`, no `system` scope and no `systemPath` anywhere in the
reactor:

```bash
grep -n 'systemPath\|<scope>system\|<repositories>\|<repository>\|<pluginRepositor' \
  pom.xml use-core/pom.xml use-gui/pom.xml use-assembly/pom.xml
# NONE FOUND
```

`use-core`'s dependencies (`use-core/pom.xml:20-74`): guava 33.5.0-jre, org.eclipse.jdt.annotation
2.2.600, antlr-runtime 3.5.3, jline 2.14.6, combinatoricslib 2.3, jruby-core 9.3.1.0, vtd-xml
2.13.4, plus test-scoped guava-testlib 33.5.0-jre, junit-jupiter 5.7.0, archunit-junit5 1.3.0.

### 5.1 Does adding a package or a test dependency require touching `module-info.java`?

Answered from **hard evidence**, not from memory: the surefire report of the last real build records
the JVM's actual paths.

```bash
cd use-core/target/surefire-reports && python3 - <<'EOF'
import re
s=open("TEST-org.tzi.use.uml.mm.ModelAPITest.xml").read()
for k in ["jdk.module.path","java.class.path"]:
    v=re.search(r'name="%s" value="([^"]*)"'%k,s).group(1)
    print(k, len(v.split(":")), "entries")
EOF
```

| Path | Contents |
|---|---|
| `jdk.module.path` (11 entries) | `use-core/target/classes`, guava(+failureaccess, jspecify, error_prone, j2objc), jdt.annotation, antlr-runtime, combinatoricslib, jruby-core, vtd-xml |
| `java.class.path` (21 entries) | `use-core/target/test-classes`, **jline**, guava-testlib, junit4, junit-jupiter 5.7.0 (+api, params, engine, platform-*), archunit-junit5, opentest4j, apiguardian |

Surefire itself is **3.5.4** (`surefire.real.class.path` lists
`surefire-booter-3.5.4.jar`), inherited from the Maven 3.9.16 super-POM — no `<surefire>` config
exists anywhere in the reactor.

Three conclusions, each directly supported by that split:

1. **Main code runs as the named module `use.core` on the module path.** Test code runs on the
   **classpath**, i.e. in the unnamed module. There is no `module-info.java` under
   `use-core/src/test/java` or `use-core/src/it/java` (`find … -name module-info.java` → nothing).
2. **A test-scoped dependency does NOT need a `requires`.** junit-jupiter, archunit and
   guava-testlib are compile-visible to tests and appear nowhere in `module-info.java`. This is
   exactly what the port needs for the differential oracle harness.
3. **A compile-scoped dependency DOES need a `requires`, and Maven derives the module path *from*
   `module-info.java`, not from the POM.** The proof is `jline`: it is a normal compile-scope
   dependency (`use-core/pom.xml:36-40`) with **no** `requires jline` in the descriptor, and it
   lands on `java.class.path`, not `jdk.module.path`. A named module cannot read the unnamed module,
   so `jline` is unusable from `use.core` source — it survives only because nothing imports it
   (`grep -rn 'import jline' use-core/src/main` → no match). **Add a compile dependency without a
   matching `requires` and it will be invisible to your code; `javac` will report "package … does
   not exist" while the jar sits happily in the dependency tree.**

**Adding a package:** classes added to `org.tzi.use.uml.ocl.{value,type,expr,expr.operations}` or
`org.tzi.use.parser.ocl` need **no** descriptor change — those packages are already exported. A
*new* package is a different matter:
* if only `use-core` main code uses it → no `exports` needed;
* if `use-gui` uses it → `exports` in `use-core` and possibly `requires`/nothing in `use-gui`;
* if **test** code uses it → `exports` **is** needed, because test classes are in the unnamed module
  and cannot read a non-exported package of a named module.

The fork's uncertainty extension touches **no GUI file at all**:
```bash
cd $FORK/src/main && grep -rlE 'UReal|UInteger|UBoolean|SBoolean|UString|uDataTypes|Uncertain' --include=*.java org | grep '^org/tzi/use/gui'
# no output
```
So `use-gui/src/main/java/module-info.java` should not need to change. (Whether the GUI *ought* to
render uncertain values is a product question, not a build question, and is out of scope here.)

### (d) Risk if got wrong

* Adding a `requires` for a module name that does not exist fails the build loudly — cheap.
* Adding a compile dependency and forgetting the `requires` fails at `javac` with a message that
  points at the import, not at the descriptor. Costly only in confusion.
* Putting new product classes in a fresh, unexported package and then writing tests against them
  produces `IllegalAccessError` **at runtime, in surefire only** — the code compiles, the IDE is
  happy, and only `mvn test` fails. This is the single most likely JPMS trap for this port.
* Test classes sharing a package name with a module package (e.g. `org.tzi.use.uml.ocl.value`, which
  the dormant `ValueTest` does) means the test class is in the unnamed module while the production
  class is in `use.core`. **Package-private access across that boundary is an `IllegalAccessError`.**
  Any revived or new test must touch only `public` members of `exported` packages.

---

## 6. Sizing the delta

### 6.1 Files the port must ADD (fork-only, uncertainty-related): 33

```bash
cd $FORK/src/main && find org -name '*.java' | sort > /tmp/fork_files.txt
cd /home/xoruser/msc-4/use-msc2026 && \
  (cd use-core/src/main/java && find org -name '*.java'; cd use-gui/src/main/java && find org -name '*.java') \
  | sort -u > /tmp/target_all.txt
comm -23 /tmp/fork_files.txt /tmp/target_all.txt
```

62 paths, of which 12 are checked-in ANTLR output (generated in 7.5.0), 15 are the unrelated
`analysis/metrics` package, and 2 are `main/Main.java` + `util/input/ShellReadline.java`. The
**33 uncertainty files** are:

| Package | n | Files |
|---|---|---|
| `uml/ocl/value` | 7 | `SBooleanValue`, `UBooleanValue`, `UIntegerValue`, `URealValue`, `UStringValue`, `UncertainBooleanValue`, `UncertainValue` |
| `uml/ocl/type` | 7 | `SBooleanType`, `UBooleanType`, `UIntegerType`, `URealType`, `UStringType`, `UncertainBooleanType`, `UncertainType` |
| `uml/ocl/expr` | 8 | `ExpConstSBoolean`, `ExpConstUBoolean`, `ExpConstUInteger`, `ExpConstUReal`, `ExpConstUString`, `ExpDefSBoolean`, `ExpUSelect`, `ExpUSelectC` |
| `uml/ocl/expr/operations` | 5 | `StandardOperationsSBoolean`, `StandardOperationsUBoolean`, `StandardOperationsUInteger`, `StandardOperationsUReal`, `StandardOperationsUString` |
| `parser/ocl` | 6 | `ASTSBooleanDefExpression`, `ASTSBooleanLiteral`, `ASTUBooleanLiteral`, `ASTUIntegerLiteral`, `ASTURealLiteral`, `ASTUStringLiteral` |

Only 7 of the 33 import `uDataTypes` at all:

```bash
cd $FORK && grep -rl 'import uDataTypes' --include=*.java src/   # 7 files
cd $FORK && grep -rh 'import uDataTypes' --include=*.java src/ | sort -u
```
```
import uDataTypes.SBoolean;   import uDataTypes.UBoolean;   import uDataTypes.UInteger;
import uDataTypes.UReal;      import uDataTypes.UString;
```
in `uml/ocl/value/{SBooleanValue,UBooleanValue,UIntegerValue,URealValue,UStringValue}.java` and
`uml/ocl/expr/operations/{StandardOperationsNumber,StandardOperationsUReal}.java`. **Five types.
`UUnlimitedNatural`, `UEnum` and `Distribution` are never imported** (they are still needed on the
classpath as transitive return types, e.g. `UReal.toUUnlimitedNatural()`).

> **CORRECTION 2026-08-18 — `stage-03-scope.md` §5.5.** Measured: only `UUnlimitedNatural` is a
> transitive need. Neither `UEnum` nor `Distribution` is referenced by any class in the compile
> closure, nor anywhere in `USE-Uncertainty/src/`. The transitive set is overstated by two classes.
> Under the purge decision of `stage-03-scope.md` §5 the `UUnlimitedNatural` dependency is removed
> too, leaving a vendored set of five.

### 6.2 Files the port must EDIT (present in both trees, touched by the fork): 23

Drift measured with `$Id`/`$ProjectVersion` lines and trailing whitespace excluded:

| File | diff ± | 7.5.0 LoC | Character of the drift |
|---|---|---|---|
| `uml/ocl/expr/operations/StandardOperationsNumber.java` | 763 | 911 | **both sides rewrote** — hardest merge in the port |
| `uml/ocl/expr/operations/StandardOperationsCollection.java` | 307 | 787 | mostly fork import-wildcarding; 25 lines uncertainty |
| `uml/mm/MClassifierImpl.java` | 226 | 588 | **upstream data-type reshape**; fork side is 10 trivial `false` stubs |
| `uml/ocl/expr/ExpQuery.java` | 218 | 513 | 22 lines uncertainty, 196 upstream |
| `parser/ocl/ASTQueryExpression.java` | 167 | 212 | fork adds the confidence argument |
| `uml/ocl/expr/operations/StandardOperationsAny.java` | 158 | 201 | fork adds `Op_identical`, rewrites `Op_equal` |
| `uml/ocl/value/CollectionValue.java` | 114 | 198 | 26 lines uncertainty |
| `uml/ocl/type/TypeImpl.java` | 70 | 347 | 10 fork stubs + 2 upstream (`qualifiedName`, data-type) |
| `analysis/coverage/AbstractCoverageVisitor.java` | 68 | 352 | 8 new visitor cases |
| `uml/ocl/expr/ExpressionPrintVisitor.java` | 49 | 585 | 8 new visitor cases |
| `uml/ocl/type/Type.java` | 37 | 155 | 10 fork members, 3 upstream members |
| `uml/ocl/value/Value.java` | 34 | 194 | 4 fork predicates only |
| `uml/ocl/type/VoidType.java` | 30 | 131 | fork stubs |
| `uml/ocl/type/TypeFactory.java` | 25 | 140 | fork singletons + map entries |
| `analysis/coverage/BasicExpressionCoverageCalulator.java` | 17 | 98 | |
| `uml/ocl/type/{Boolean,Integer}Type.java` | 15 each | 64 / 74 | `conformsTo` + `allSupertypes` |
| `uml/ocl/expr/ExpressionVisitor.java` | 11 | 77 | 8 fork + 1 upstream rename |
| `uml/ocl/type/StringType.java` | 11 | 63 | |
| `uml/ocl/type/RealType.java` | 9 | 67 | |
| `uml/ocl/expr/operations/OpGeneric.java` | 7 | 118 | **fork only** |
| `uml/ocl/expr/{ExpExists,ExpForAll}.java` | 5 each | 83 each | |

Reproduce the table with:

```bash
cd /home/xoruser/msc-4/use-msc2026
F=.git/reference-repositories/uncertainty/USE-Uncertainty/src/main; T=use-core/src/main/java
for f in $(cd $F && grep -rlE 'UReal|UInteger|UBoolean|SBoolean|UString|uDataTypes|Uncertain|identicalExpression' --include=*.java org | sort); do
  [ -f "$T/$f" ] || continue
  d=$(diff <(grep -vE '\$Id|\$ProjectVersion' $F/$f|sed 's/[[:space:]]*$//') \
           <(grep -vE '\$Id|\$ProjectVersion' $T/$f|sed 's/[[:space:]]*$//') | grep -cE '^[<>]')
  printf "%-64s %5s %6s\n" "$f" "$d" "$(wc -l < $T/$f)"
done
```

### 6.3 Grammar drift — the fourth extension point, easy to miss

Grammar files moved from `$FSRC/parser/{base,ocl,…}/*.gpart` to
`use-core/src/main/resources/grammars/{base,ocl,…}/*.gpart`. Diffing the base parts:

**Fork-side additions the port must re-apply** (`OCLBase.gpart`):
* a new `identicalExpression` rule (fork lines 125-137) sitting between `expression` and
  `conditionalImpliesExpression`, implementing `a.equals(b)` as a binary operator that dispatches to
  the `Op_identical` `OpGeneric`. **This changes the expression precedence chain**: 7.5.0 wires
  `expression → conditionalImpliesExpression` directly (target `OCLBase.gpart:27,36,74`), the fork
  wires `expression → identicalExpression → conditionalImpliesExpression`.
* an optional confidence argument on query expressions: fork
  `( COMMA uncerExp=additiveExpression { uncer = $uncerExp.n;} )?` feeding
  `new ASTQueryExpression($op, $range, decl, $nExp.n, uncer)` (fork `OCLBase.gpart:338-348`)
  vs. 7.5.0's four-argument `new ASTQueryExpression($op, $range, decl, $nExp.n)`
  (target `OCLBase.gpart:325-331`).

**Upstream-side additions the port must not clobber**:
* `importStatement` / `elementIdent` / `artifact` rules and `ASTImportStatement`
  (`USEBase.gpart:9-35`), plus `import` handling in `model` (`USEBase.gpart:37,48-61`).
* `dataTypeDefinition` (`USEBase.gpart:86-113`).
* model-qualified operation names: `(modelQualifier=IDENT HASH name=IDENT | name=IDENT)`
  (`OCLBase.gpart:367-369`) — a **two-argument** `ASTOperationExpression` constructor now exists
  alongside the old one.
* `( 'oclIsInState' | 'oclInState' )` (`OCLBase.gpart:400-408`).

`OCLLexerRules.gpart` is **unchanged** between the trees (empty diff) — confirming §2.3: no new
lexer token is needed for the uncertain type names.

---

## 7. BLOCKING DESIGN DECISION — how the port gets `uDataTypes` onto a Maven build

### 7.1 Is the library in any Maven repository, under any coordinates? **No.**

Searched Maven Central by artifact, group, and — decisively — by fully-qualified class name:

```bash
for q in "uDataTypes" "udatatypes" "a:udatatypes" "g:atenearesearchgroup" \
         "fc:uDataTypes.UReal" "fc:uDataTypes.SBoolean"; do
  echo -n "q=$q -> "
  curl -s -G --data-urlencode "q=$q" --data "rows=10&wt=json" https://search.maven.org/solrsearch/select \
   | python3 -c "import sys,json;r=json.load(sys.stdin)['response'];print(r['numFound'],[x['id'] for x in r['docs']])"
done
```
```
q=uDataTypes -> 0 []
q=udatatypes -> 0 []
q=a:udatatypes -> 0 []
q=g:atenearesearchgroup -> 0 []
q=fc:uDataTypes.UReal -> 0 []
q=fc:uDataTypes.SBoolean -> 0 []
```

`fc:` is Central's fully-qualified-class index. Zero hits means **no artifact on Central anywhere
contains `uDataTypes.UReal` or `uDataTypes.SBoolean`**, under any coordinates. Direct path probes
also 404:

```bash
for p in es/uma/lcc/atenea uDataTypes atenearesearchgroup; do
  echo -n "$p -> "; curl -s -o /dev/null -w '%{http_code}\n' "https://repo1.maven.org/maven2/$p/"
done      # 404, 404, 404
```

For context, `org.tzi.use` itself is **not** on Central either (`g:org.tzi.use` → 0 results), so the
project has no precedent of consuming its own or its collaborators' artifacts from a public repo.

**JitPack is not an escape hatch.** JitPack builds from a Git tag and requires a Maven or Gradle
build file in the repository. The uDataTypes tree has none:

```bash
find $UDT -name pom.xml -o -name 'build.gradle*'    # no output
```

### 7.2 What the oracle jar actually is

```bash
unzip -l $FORK/lib/atenearesearchgroup.uncertainty.jar
```
* 39 entries, 77 674 bytes, classes under `uDataTypes/`, timestamps 2021-02-24.
* Also ships `.classpath`, `.project`, `.settings/org.eclipse.jdt.core.prefs`, `.gitignore`,
  `uDataTypes.iml` — it is an IDE export, not a release artifact.
* **No `META-INF/` at all** (`unzip -l … | grep -c META-INF` → `0`): no manifest, therefore **no
  `Automatic-Module-Name`**. As an automatic module its name would be derived from the file name,
  i.e. from whatever artifactId it were installed under.
* Class file major version **52** (Java 8), readable by JDK 21
  (`javap -v -classpath … uDataTypes.UReal | grep 'major version'`).

**A byte-identical copy of this jar is already inside the target repository**, committed as a test
resource by an earlier stage:

```bash
md5sum use-core/src/test/resources/historical/atenearesearchgroup.uncertainty.jar \
       $FORK/lib/atenearesearchgroup.uncertainty.jar
```
```
a3055f54205babaa27484fa94efdda1c  use-core/src/test/resources/historical/atenearesearchgroup.uncertainty.jar
a3055f54205babaa27484fa94efdda1c  .../USE-Uncertainty/lib/atenearesearchgroup.uncertainty.jar
```
alongside `use-core/src/test/resources/historical/use.jar` (also byte-identical to `$FORK/lib/use.jar`,
md5 `8645269c1eacbf8cb52bf7f694c07b21`). The stub harness at
`use-core/src/test/java/org/tzi/use/uncertainty/differential/UValue.java:13-16` states the intent:
the historical side is loaded through *"the isolated historical class loader"*. **So the oracle-side
need is already solved, on the test side, with no Maven coordinates involved.** What remains
unsolved is the *product* side: `use-core` main code must compile against `uDataTypes.*`.

### 7.3 Is the 2023 source tree a safe stand-in for the 2021 jar? **Yes, for this port's call paths.**

`$UDT/Libraries/Java/src/uDataTypes` holds 24 `.java` files. It is *not* the source of the jar — the
README says *"This is the first version of this Java library (September 2023)"* while the jar's
classes are dated 2021-02-24. That gap has to be measured, not assumed.

Compiled the source cleanly under JDK 21 (test/demo files excluded — they need JUnit):

```bash
mkdir -p /tmp/udt && cp -r $UDT/Libraries/Java/src/uDataTypes /tmp/udt/src/
javac -nowarn -d /tmp/udt/out $(find /tmp/udt/src -name '*.java' | grep -vE 'Test|Examples')   # exit 0
```

**Public API**, jar vs. source-compiled, for the five types the fork imports:

```bash
JAR=$FORK/lib/atenearesearchgroup.uncertainty.jar
for c in UReal UInteger UBoolean UString SBoolean Distribution UUnlimitedNatural; do
  echo "## $c"; diff <(javap -classpath $JAR uDataTypes.$c|sort) <(javap -classpath /tmp/udt/out uDataTypes.$c|sort)
done
```
```
## UReal              (identical)
## UInteger           (identical)
## UBoolean           (identical)
## UString            (identical)
## SBoolean           14 lines, ALL additions on the source side (weightedUnion, union, 9 collection-fusion statics)
## Distribution       (identical)
## UUnlimitedNatural  (identical)
```

**The source is a strict API superset.** Anything that compiled against the jar compiles against the
source.

Per-method bytecode comparison (constant-pool indices normalised away) classifies every difference:

| Class | methods only in jar | changed bodies | of which **semantic** (not `invokespecial`→`invokevirtual`) |
|---|---|---|---|
| `UReal` | 0 | 6 | **1** — `divideBy(UReal, double)` |
| `UInteger` | 0 | 2 | **2** — `divideBy(UInteger, double)`, `divideByR(UInteger, double)` |
| `UBoolean` | 0 | 7 | 0 (all are `setNormalForm` visibility) |
| `UString` | 0 | 7 | **2** — `uConcat(UString)`, `at(int)` |
| `SBoolean` | 24, **all `lambda$…` synthetics** | 34 | many — the fusion operators were reimplemented |
| `UUnlimitedNatural` | 0 | 4 | 4 — all `divideBy*` overloads (**unused by the fork**) |

Differential execution probe (full script in `/tmp/claude-…/scratchpad/probe/`) — 16 expressions
over all five types, run once against the jar and once against the compiled source:

```
UReal(3.5,0.1)  UReal(1,0)/UReal(2,0.3)  UBoolean(true,0.7)  UInteger(30,1)  UString("ab",0.8)
UString.at(1)   SBoolean(.5,.3,.2,.5)    hashCode  projection  and  cumulativeFusion  discount
createVacuousOpinion  averageBeliefFusion  weightedBeliefFusion  consensusAndCompromiseFusion
```
**All 16 identical.** Including `SBoolean.toString()`, `SBoolean.hashCode()` and all three fusion
operators — so the SBoolean reimplementation is behaviour-preserving on these inputs.

The **only** divergence I could actually trigger is in the covariance-taking overloads:

```
                                                JAR (2021)             SRC (2023)
UReal(1,0).divideBy(UReal(2,0.3), 0.0)      →  UReal(0.500, 0.000)   UReal(0.500, 0.075)
UInteger(6,0).divideBy(UInteger(3,1), 0.0)  →  UInteger(2, 0.000)    UInteger(2, 0.111)
UInteger(6,0).divideByR(UInteger(3,1), 0.0) →  UReal(2.000, 0.000)   UReal(2.000, 0.111)
```

Root cause, in the `this.getU()==0.0` branch of `divideBy`
(`$UDT/Libraries/Java/src/uDataTypes/UReal.java`, `divideBy(UReal r)` shows the *fixed* shape):

```java
if (this.getU()==0.0) {                 // "this" is a scalar, r is not
    result.setX(this.getX() / r.getX());
    result.setU(r.getU()/(r.getX()*r.getX()));   // 2023
}                                                // 2021 jar reads this.getU()/(this.getX()*this.getX()) ⇒ always 0
```
i.e. the 2021 jar **drops the divisor's uncertainty**; 2023 propagates it. A genuine bug fix.

**The fork never reaches those overloads.** It calls only the single-argument forms:

```bash
cd $FORK && grep -rn 'divideBy\|divideByR' --include=*.java src/main/org/tzi/use/uml/ocl/
```
```
uml/ocl/value/UIntegerValue.java:148   uInteger.divideBy(v.uInteger)
uml/ocl/value/UIntegerValue.java:158   uInteger.divideByR(v.uInteger)
uml/ocl/value/URealValue.java:180      uReal.divideBy(castedOther.uReal)
```
and the single-argument forms are byte-identical between jar and source
(`diff <(sed -n '242,300p' ur_jar.txt) <(sed -n '242,300p' ur_src.txt)` → empty) and produce
identical results in the probe.

**Licence:** `$UDT/Libraries/Java/README.md`, section "License", states **MIT Licence, Copyright (c)
2023 Atenea Research group**, with the permission text inline. There is no `LICENSE` file in the
tree. MIT is GPL-2-compatible, so vendoring into GPL-2 USE is legally sound provided the copyright
and permission notice travel with the copied files. **`UNVERIFIABLE`: the 2021 jar carries no
licence metadata of its own** (no `META-INF`), so the MIT grant is evidenced only by the 2023
README — one more reason to prefer the source over the jar.

### 7.4 The four options

#### Option A — Vendor the source into the port

Copy the 15 non-test `.java` files from `$UDT/Libraries/Java/src/uDataTypes` into
`use-core/src/main/java/…`, retaining the MIT notice, committed to the port's repository.

*Two sub-variants, and the difference is not cosmetic:*

* **A1 — keep the package name `uDataTypes`.** Zero edits to the copied sources and to the 7 fork
  files that `import uDataTypes.*`.
  **But it creates a class-name collision with the oracle.** The differential harness loads the
  historical jar through a `URLClassLoader` (`UValue.java:13-16`). A `URLClassLoader` delegates
  **parent-first** by default, and classes of a named module on the module path are loaded by the
  application class loader — which *is* that parent. So `historicalLoader.loadClass("uDataTypes.UReal")`
  would return the **vendored 2023 class**, not the 2021 oracle class, and the differential sweep
  would silently compare the port against itself. This is the worst possible failure mode: a green
  suite that proves nothing. It is avoidable — construct the isolated loader with
  `ClassLoader.getPlatformClassLoader()` as parent, or a parent-last loader — but it makes harness
  correctness a *precondition* for oracle validity, which is a bad place to put a load-bearing
  assumption.
* **A2 — relocate to `org.tzi.use.uncertainty.udatatypes`.** A mechanical, greppable rewrite of the
  `package` line in 15 files and the `import` line in 7. The name collision disappears by
  construction; the oracle loader can be naive and still be correct. Cost: the vendored sources are
  no longer textually identical to upstream uDataTypes, so future re-syncs need the same rewrite.

*Both variants:* the package is internal to `use.core`. **`exports` is required only if test code
constructs `uDataTypes` objects directly** — see §5.1 point 3. Given the harness compares through
`UValue`, it very likely does not need to, but the port should decide this explicitly rather than
discover it via `IllegalAccessError`.

*Behavioural delta vs. the oracle:* zero on every call path the fork exercises (§7.3), one
documented improvement (`divideBy` with covariance) on paths it does not.

#### Option B — `mvn install:install-file` the jar into the local repository

Declare `<dependency>` on invented coordinates and require every developer and CI job to run an
out-of-band `install:install-file` first.

* Breaks `git clone && mvn test`. The build is no longer self-contained; it depends on hidden local
  state in `~/.m2`.
* The reactor has **zero** precedent for it: no `<repositories>`, no `system` scope (§5-b).
* Needs a `requires <automatic-module>` in `module-info.java`, where the module name is derived from
  the installed file name (the jar has no `Automatic-Module-Name`, §7.2). Automatic modules also
  read the unnamed module and cannot be `jlink`ed — a latent constraint on `use-assembly`.
* Ships the IDE cruft (`.classpath`, `.project`, `.settings/`, `.iml`) into the product.
* Freezes the 2021 `divideBy` bug into the product for all time.

#### Option C — Shade / relocate the jar's bytecode (`maven-shade-plugin`)

Solves the collision like A2, but at bytecode level.

* Still requires the jar in a repository first (i.e. Option B's problem, unsolved) or as a
  `system`-scoped path (which the reactor does not do and which is deprecated).
* Adds a build plugin and a shaded-artifact lifecycle to a reactor that currently has none of that.
* Same frozen-2021-bug consequence as B.
* Debugging relocated bytecode with no sources attached is materially worse than debugging vendored
  source.

#### Option D — Reimplement the uncertainty arithmetic inside the port

Rejected on the spot. `UReal` is 19 582 bytes and `SBoolean` 59 412 bytes of subjective-logic
arithmetic implementing two published papers; reimplementation would make the port's numeric
behaviour a fresh research artifact rather than a port.

### 7.5 Recommendation

> **Option A2 — vendor the 2023 MIT-licensed source into `use-core` under a relocated package
> (`org.tzi.use.uncertainty.udatatypes`).**

It is the only option that satisfies all four constraints simultaneously:

| Constraint | A1 | **A2** | B | C |
|---|---|---|---|---|
| "References are never build inputs" — nothing under `.git/reference-repositories` on any source path or classpath | ✔ (files are *copied in*) | **✔** | ✘ if it points at `$FORK/lib`; ✔ only via the already-committed `src/test/resources/historical/` copy | ✘ same |
| `git clone && mvn test` works with no manual step | ✔ | **✔** | ✘ | ✘ |
| Oracle isolation cannot be defeated by classloader delegation | ✘ | **✔** | ✔ | ✔ |
| No `module-info.java` `requires` on an unnamed automatic module | ✔ | **✔** | ✘ | ✘ |
| Licence clean | ✔ MIT | **✔ MIT** | ⚠ jar has no licence metadata | ⚠ same |

Two follow-on obligations if A2 is taken:

1. **Record the jar↔source delta as a known, accepted difference.** §7.3 shows it is empty on the
   fork's call paths, but the `divideBy(…, covariance)` fix means "port ≡ oracle" is a claim about
   *reachable* behaviour, not about the libraries. If a future stage adds OCL surface for
   correlated division, the port and the oracle will legitimately disagree.
2. **Keep the oracle side on the committed jar** at
   `use-core/src/test/resources/historical/atenearesearchgroup.uncertainty.jar` (already present,
   md5 `a3055f54205babaa27484fa94efdda1c`), loaded through an isolated class loader. Test-scope
   needs no POM dependency and no `requires` (§5.1 point 2) because the jar is a *resource*, not a
   dependency — the harness opens it by path/URL.

---

## 8. Gaps

* `UNVERIFIABLE` — the exact JVM flags surefire 3.5.4 passes for the modular main / non-modular test
  split (`--add-opens`, `--add-reads`, `--patch-module`). The surefire XML records
  `jdk.module.path` and `java.class.path` but not the full argument line, and confirming it would
  require running Maven, which is forbidden here. The path split is enough to establish the rules in
  §5.1; the precise flag set is not.
* `UNVERIFIABLE` — whether the 2021 jar's `uDataTypes` classes were built from a *published* source
  revision. The 2023 tree has no VCS metadata (`find $UDT -name .git` → nothing) and the jar has no
  manifest, so the two artifacts cannot be linked by provenance, only by the behavioural comparison
  in §7.3.
* `UNVERIFIABLE` — the licence status of the 2021 jar itself. The MIT grant is documented only in the
  2023 `README.md`; the jar carries no `META-INF` and no licence file.
* `UNVERIFIABLE` — whether `use-gui` contains value/type dispatch (`instanceof RealValue`,
  `isTypeOfBoolean()` switches) that would need widening for uncertain values to display correctly.
  The fork touches no GUI file, so there is no fork-side evidence either way, and auditing the GUI's
  own dispatch sites was out of scope for this section.
* Not attempted, per the brief: locating the fork's base commit or producing a base diff.
* Per the ground rules, nothing in this document is derived from the earlier port on `origin/main`.
