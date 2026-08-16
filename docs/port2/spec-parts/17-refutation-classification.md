# 17 — Refutation pass: new-file vs upstream-edit classification

Adversarial re-check of the file classifications in `10-values.md`, `11-types.md`,
`12-expressions.md`, `13-grammar.md`. Every verdict below names the file+line or the exact
shell command that produced it. Read-only pass: no Maven, no writes outside this directory,
no reference repository touched.

Path aliases (same as the audited sections):
`T/` = `/home/xoruser/msc-4/use-msc2026/use-core/src/main/java/org/tzi/use/`,
`F/` = `/home/xoruser/msc-4/use-msc2026/.git/reference-repositories/uncertainty/USE-Uncertainty/src/main/org/tzi/use/`.

---

## 0. Method — the master list of upstream files the fork actually modifies

Rather than trust the four sections' lists, I rebuilt the ground truth. For every `.java`
path present in *both* trees, count the fork-side added lines that mention an uncertainty
symbol:

```bash
cd /home/xoruser/msc-4/use-msc2026
FORK=.git/reference-repositories/uncertainty/USE-Uncertainty/src/main/org/tzi/use
TGT=use-core/src/main/java/org/tzi/use
(cd $FORK && find . -name '*.java' | sed 's|^\./||' | sort) > /tmp/fork.txt
(cd $TGT  && find . -name '*.java' | sed 's|^\./||' | sort) > /tmp/tgt.txt
comm -12 /tmp/fork.txt /tmp/tgt.txt > /tmp/both.txt      # 539 common files
while read f; do
  n=$(diff --strip-trailing-cr -u "$TGT/$f" "$FORK/$f" | grep -E '^\+' | grep -vE '^\+\+\+' \
      | grep -cE 'UBoolean|UReal|UInteger|UString|SBoolean|Uncertain|uEquals|uDistinct|uSelect|uIncludes|uExcludes|uCountC|uncertainty|Uncertainty|isUReal|isUInteger|toBooleanC|uDataTypes')
  [ "$n" -gt 0 ] && echo "$n  $f"
done < /tmp/both.txt | sort -rn
```

Output (24 upstream files, uncertainty-attributable added lines):

```
133  uml/ocl/expr/operations/StandardOperationsNumber.java
 34  uml/ocl/expr/operations/StandardOperationsCollection.java
 30  uml/ocl/expr/operations/StandardOperationsAny.java
 29  uml/ocl/value/CollectionValue.java
 20  uml/ocl/expr/ExpQuery.java
 15  uml/ocl/type/TypeFactory.java
 12  uml/ocl/value/Value.java
 11  parser/ocl/ASTQueryExpression.java
 10  uml/ocl/type/TypeImpl.java
 10  uml/ocl/type/Type.java
 10  uml/mm/MClassifierImpl.java
  8  uml/ocl/expr/ExpressionPrintVisitor.java
  7  analysis/coverage/AbstractCoverageVisitor.java
  6  uml/ocl/expr/operations/OpGeneric.java
  6  uml/ocl/expr/ExpressionVisitor.java
  5  uml/ocl/type/VoidType.java
  5  uml/ocl/type/BooleanType.java
  4  uml/ocl/type/IntegerType.java
  3  uml/ocl/type/StringType.java
  3  uml/ocl/type/RealType.java
  2  uml/ocl/expr/ExpForAll.java
  2  uml/ocl/expr/ExpExists.java
  2  parser/base/ParserHelper.java
  1  analysis/coverage/BasicExpressionCoverageCalulator.java
```

Two files the keyword filter cannot see, checked by hand and both correctly listed:
`util/MathUtil.java` (adds `round(double,int)`, `F/util/MathUtil.java:96-109`) and
`uml/ocl/value/RealValue.java` (adds `valueOf(Value)`, `F/uml/ocl/value/RealValue.java:90-99`).

Everything in the sections' lists appears above. **Four files above appear in no list in
§10–§13** — see finding R1.

---

## 1. Findings

### R1 — REFUTED. §12's upstream-edit list is understated by four files in `uml/ocl/expr/`

`12-expressions.md:5-7` states its scope as "the OCL expression AST classes the uncertainty
fork adds under `org/tzi/use/uml/ocl/expr/`, **plus the minimal edits required in upstream
7.5.0 expression/visitor code**". Its edit manifest (`12-expressions.md:619-624`, §6.2) lists
exactly four files: `ExpQuery.java`, `ExpressionVisitor.java`, `ExpressionPrintVisitor.java`,
`AbstractCoverageVisitor.java`.

`org/tzi/use/uml/ocl/expr/operations/` is a subpackage of that scope and contains four
further **modified** upstream files, none mentioned in §12 (`grep -n
"StandardOperations\|OpGeneric" 12-expressions.md` → one hit, line 737, a passing aside):

| file | nature of the change |
|---|---|
| `T/uml/ocl/expr/operations/OpGeneric.java` | `registerOperation` static block gains 5 lines registering `StandardOperationsU{Real,Boolean,Integer,String}` and `…SBoolean` (`F/…/OpGeneric.java:91-97`). Pure registration, but still an upstream edit. |
| `T/uml/ocl/expr/operations/StandardOperationsAny.java` | **existing classes rewritten.** `final class Op_equal` (T `:32`) and `final class Op_notequal` (T `:86`) have their `matches` and `eval` bodies replaced so `=` / `<>` return `UBoolean`/`SBoolean` when either operand is uncertain. Plus a new `final class Op_identical` and its registration. |
| `T/uml/ocl/expr/operations/StandardOperationsCollection.java` | **existing classes rewritten** — `Op_includes`, `Op_excludes`, `Op_includesAll`, `Op_excludesAll` lose their `return BooleanValue.get(res)` bodies in favour of the `uIncludes`/`uExcludes` fold. |
| `T/uml/ocl/expr/operations/StandardOperationsNumber.java` | 559 added lines; the entire arithmetic/relational operation family is rewritten. |

Evidence for the two "rewritten, not added" claims:

```bash
diff -u --strip-trailing-cr \
  use-core/src/main/java/org/tzi/use/uml/ocl/expr/operations/StandardOperationsAny.java \
  .git/reference-repositories/.../operations/StandardOperationsAny.java \
| grep -nE '^@@|^\+.*class Op_'
# 25:@@ -43,10 +45,27 @@   <- inside Op_equal   (T:32..85)
# 57:@@ -64,11 +83,90 @@   <- inside Op_equal
# 106:+final class Op_identical extends OpGeneric {
# 149:@@ -97,16 +195,60 @@  <- inside Op_notequal (T:86..131)
grep -n '^final class Op_' use-core/.../StandardOperationsAny.java
# 32:Op_equal  86:Op_notequal  132:Op_isDefined  169:Op_isUndefined
```

`20-ops-*.md` and `15-upstream-delta.md` do reference `StandardOperationsNumber` /
`OpGeneric` (`grep -ln StandardOperationsNumber 20-ops-*.md` → all five), so the port as a
whole is not blind to them. But §12 is the section a porter reads to learn which
*expression-package* upstream files it will have to modify, and as written it promises four
and delivers eight. `10-values.md:895` explicitly defers `StandardOperationsCollection` to
"the expression spec part", which never picks it up. **This is exactly the understated-edit
failure mode the review is meant to catch: `Op_equal`/`Op_notequal` are core OCL semantics
being silently rewritten.**

### R2 — REFUTED. The `ExpressionPrintVisitor` and `AbstractCoverageVisitor` edits are not "add N new methods"

`12-expressions.md:552` (row 1) says "Add all 8 methods"; `:553` (row 2) says "Add all 8";
`:622-623` (§6.2) says "implement the 7/8 new methods". Both files also need an **existing
method body modified**:

`ExpressionPrintVisitor.visitQuery(ExpQuery, VarInitializer)` — fork adds, after
`exp.getQueryExpression().processWithVisitor(this)`:

```java
if (exp.getUncertaintyExpression() != null) {
    writer.write(",");  writer.write(ws());
    exp.getUncertaintyExpression().processWithVisitor(this);
}
```
`F/uml/ocl/expr/ExpressionPrintVisitor.java:421-425`; diff hunk `@@ -387,6 +417,13 @@` against
`T/uml/ocl/expr/ExpressionPrintVisitor.java`.

`AbstractCoverageVisitor.visitQuery(ExpQuery)` — fork adds
`if (exp.getUncertaintyExpression() != null) exp.getQueryExpression().processWithVisitor(this);`
(`F/analysis/coverage/AbstractCoverageVisitor.java:257-259`; hunk `@@ -226,6 +255,8 @@`).
Note the fork's own bug there: it re-visits `getQueryExpression()`, not
`getUncertaintyExpression()`. Neither the bug nor the edit is recorded in §12.

Reproduce:
```bash
diff -u --strip-trailing-cr use-core/.../ExpressionPrintVisitor.java   FORK/.../ExpressionPrintVisitor.java
diff -u --strip-trailing-cr use-core/.../AbstractCoverageVisitor.java  FORK/.../AbstractCoverageVisitor.java
```

### R3 — REFUTED. §12 §3.3.4's "the confidence argument is lost on print" is false for the print visitor

`12-expressions.md:494-499`: *"Neither `ExpQuery.toString(StringBuilder)` … nor
`ExpressionPrintVisitor.visitUSelectC` (fork `:455-457`, delegates to `visitQuery`) prints
`fUncertaintyExp`. So `uSelectC(e | p, 0.8)` renders as `…->uSelectC(e | p)`."*

`visitUSelectC` does delegate to `visitQuery` — and the fork's `visitQuery` prints the
confidence (see R2, `F/…/ExpressionPrintVisitor.java:421-425`). The print visitor round-trips
correctly. Only `ExpQuery.toString(StringBuilder)` (fork `:680-694`, unchanged from T `:486-500`)
drops it. The stated conclusion, the recommended fix, and the "will break any print-then-reparse
test" risk are all wrong for `ExpressionPrintVisitor`. Half the claim survives; the diagnosis
does not.

### R4 — REFUTED. §13.6's checklist omits the `StandardOperationsAny.java` edit that Change A depends on

`13-grammar.md:753-762` (§13.6 port checklist) lists `OCLBase.gpart`, `ParserHelper.java`,
`ASTQueryExpression`, the AST literal classes, `ASTSBooleanDefExpression`,
`ASTUStringLiteral`, `uncertaintyType`, the `'equals'` keyword, and fixtures. It does **not**
list `StandardOperationsAny.java`.

But §13's own body (`13-grammar.md:144-150`) establishes that Change A
(`identicalExpression`, the `.equals(…)` level) hands the token to `ASTBinaryExpression`,
which resolves it by name against `Op_identical` — *fork-only*, living in
`F/uml/ocl/expr/operations/StandardOperationsAny.java:130-179`, registered by a line added to
`registerTypeOperations`. Verified 7.5.0 has no such operation:

```bash
grep -n '"equals"' use-core/src/main/java/org/tzi/use/uml/ocl/expr/operations/StandardOperationsAny.java
# (no output)
```

A porter following only the §13.6 checklist ships a grammar level whose operator resolves to
nothing. The file must be on the edit list (and it is the same file R1 flags for the
`Op_equal`/`Op_notequal` rewrite).

### R5 — PARTIALLY WRONG. `ExpQuery.java` is claimed minimal but the same result needs zero `ExpQuery` edits for 3 of its 4 members

`12-expressions.md:620` states the edit as "add `fUncertaintyExp`, 5-arg ctor,
`assertKindOfUBoolean()`, `evalUSelect()`, `evalAndAsertConfident()`,
`getUncertaintyExpression()`. Purely additive."

`ExpQuery`'s three fields are `protected` — `T/uml/ocl/expr/ExpQuery.java:43` (`fElemVarDecls`),
`:48` (`fRangeExp`), `:53` (`fQueryExp`) — and the only consumers of the new members are
`ExpUSelect` / `ExpUSelectC`, which are **new files in the same package**
(`F/uml/ocl/expr/ExpUSelect.java:1`, `ExpUSelectC.java:1` both `package org.tzi.use.uml.ocl.expr;`).
`fUncertaintyExp`, `assertKindOfUBoolean()`, `evalUSelect()` and `evalAndAsertConfident()` can
therefore all live in a new `abstract class ExpUQuery extends ExpQuery` with **no upstream edit
at all**. Only `getUncertaintyExpression()` has a reason to sit on `ExpQuery` — because the
print and coverage visitors call it through an `ExpQuery`-typed reference (R2). The minimality
argument as written is not made and does not hold for the other four members.

### R6 — PARTIALLY WRONG. "Purely additive" + "keep 7.5.0's `evalExistsOrForAll`" leaves `assertKindOfUBoolean` dead and exists/forAll unported

`12-expressions.md:620` says to keep 7.5.0's `evalExistsOrForAll`; `:628-629` (§6.3) defers the
`ExpExists`/`ExpForAll` change to "only together with `ExpQuery` items 7+8" — items that are
not enumerated anywhere in §6.2. The fork does **not** merely add: it replaces
`evalExistsOrForAll`'s body and adds `evalExists0` / `evalForAll0`, which fold the per-element
result through `ExpStdOp.create("or"/"and", …)` so `UBoolean` predicates work
(`F/uml/ocl/expr/ExpQuery.java:255-360`; diff hunk `@@ -158,10 +255,112 @@`). Those two methods
are absent from §6.2's member list.

Consequence of shipping §6.2 exactly as written: `assertKindOfUBoolean()` is added and never
called (its only fork callers are `ExpExists.java:47`, `ExpForAll.java:47`, `ExpUSelect.java:29`,
`ExpUSelectC.java:17` — the first two are deferred), and `exists`/`forAll` over an uncertain
predicate is silently unported. Deferring is a legitimate decision; labelling the result
"purely additive" without saying which fork behaviour is being dropped is not.

Evidence: `grep -rn "assertKindOfUBoolean\|getUncertaintyExpression" FORK/src/` (excluding
`ExpQuery.java`) → the four call sites above plus
`ExpressionPrintVisitor.java:421`, `AbstractCoverageVisitor.java:258`,
`analysis/metrics/AbstractMetricVisitor.java:223` (out of scope).

### R7 — PARTIALLY WRONG. §13.6's `ASTQueryExpression` row understates the edit

`13-grammar.md:756`: *"add 5-arg ctor as an **overload**; add guard rejecting a confidence arg
for non-`uSelectC` ops"*. The actual delta (`diff -u --strip-trailing-cr
T/parser/ocl/ASTQueryExpression.java F/parser/ocl/ASTQueryExpression.java`) also requires:

* a new field `private ASTExpression fUncertainty;` (fork `:47`);
* in `gen(Context)`: `Expression uncertainty = null;` and
  `if (fUncertainty != null) uncertainty = fUncertainty.gen(ctx);` (fork `:112-115`);
* **two new labels in the outer `switch`** — `case ParserHelper.Q_USELECT_ID:` and
  `case ParserHelper.Q_USELECTC_ID:` added to the single-element-variable group (fork `:124-125`);
* **two new arms in the inner `switch`** — `res = new ExpUSelect(decl, range, expr);` (fork `:139`)
  and the `Q_USELECTC_ID` arm with the null-confidence `SemanticException` (fork `:178-184`);
* `toString()` rewritten to append `", " + fUncertainty` (fork `:224-229`).

§13.1.4 and §13.4.1 do cite the `gen` dispatch lines, so the section is not ignorant of this —
but the checklist, which is the artefact a porter executes, describes a ctor overload plus a
guard. As specified it would compile and parse `uSelect`/`uSelectC` and then throw
"Internal error: unknown query operation" at `gen` time.

### R8 — PARTIALLY WRONG (minimality). `CollectionValue.java` needs no edit at all

`10-values.md:863-880` states the five new members must "live on `CollectionValue` rather than
on the concrete subclasses" (§12.1, justified by the `StandardOperationsCollection` call
sites). That justification rules out the *subclass* alternative but not the *helper* one.

All five bodies use only `iterator()` — available because
`T/uml/ocl/value/CollectionValue.java:36` reads `public abstract class CollectionValue extends
Value implements Iterable<Value>` — plus `size()` and public `Value` API. Nothing is
`protected` or package-private. `uIncludes`, `uIncludesAll`, `uExcludes`, `uExcludesAll`,
`uCountC` can therefore be `static` methods on a **new** class taking a `CollectionValue`
first argument, and `StandardOperationsCollection` (which must be edited anyway per R1) calls
those instead. That is strictly fewer upstream files touched.

§11 offers exactly this kind of alternative for `RealValue.valueOf` ("inline the two-branch
coercion inside `SBooleanValue`") and recommends against it with reasons. §12 offers no
alternative and asserts the placement is forced. It is not forced; it is preferred.

### R9 — PARTIALLY WRONG (minimality). Four of §11's nine type edits are avoidable

`11-types.md:673-681` lists `Type.java`, `TypeImpl.java`, `VoidType.java` and
`MClassifierImpl.java` as edits whose content is *ten new predicate declarations* plus twenty
no-op `return false` overrides plus five `return h == VoidHandling.INCLUDE_VOID` overrides.
Verified accurate as a description of the fork (diffs below). But the section asserts these are
the minimal way to get the behaviour, and they are not the only way: the predicates are pure
functions of the type instance, and the five uncertain types all descend from a single new
`UncertainType`/`UncertainBooleanType` root plus five upstream special cases
(`BooleanType→UBoolean/SBoolean`, `IntegerType→UReal/UInteger`, `RealType→UReal`,
`StringType→UString`, `VoidType→all`). A static helper
`UncertainTypes.isKindOfUBoolean(Type, VoidHandling)` encoding that table reproduces every
answer with **zero** edits to `Type`, `TypeImpl`, `VoidType` and `MClassifierImpl` — the
11 external call sites the section itself enumerates (`11-types.md:59-60`) are all in files
that get edited anyway.

I am not recommending the helper — adding to the `Type` interface is more faithful and far
more readable, and `conformsTo`/`allSupertypes`/`getLeastCommonSupertype` on
`BooleanType`/`IntegerType`/`RealType`/`StringType` must be edited either way. The finding is
narrow: **"minimal" is asserted, not argued, for these four files, and a cheaper option exists.**

### R10 — Minor factual errors

| # | claim | verdict |
|---|---|---|
| a | `10-values.md:37-38`: *"`ls TARGET/uml/ocl/value/` lists no `U*Value.java`"* | False as literally written — `UndefinedValue.java` and `UnlimitedNaturalValue.java` match `U*Value.java`. The substantive claim (no uncertainty value classes) is correct. `12-expressions.md:33-34` states the same check correctly. |
| b | `11-types.md:747`: *"`UIntegerType()` constructor visibility — fork has it `public`, all siblings package-private."* | `URealType`'s ctor is `protected URealType()` (`F/uml/ocl/type/URealType.java:9`), not package-private. `UStringType`, `UBooleanType`, `SBooleanType` are package-private. |
| c | `13-grammar.md:81`: *"net line delta +30 (678 → 708 lines)"* | Line counts are 677 → 707 (`wc -l up_OCLBase.txt cand_OCLBase.txt`; both files end in `\n`, verified `tail -c 1 \| xxd`). The +30 delta and the 8-hunk/+35/−8 measurement are exactly right. |

---

## 2. Confirmed — claims that survived the attack

### 2.1 Every "new file" claim is sound (no name collision in 7.5.0)

```bash
comm -23 <(cd FORK && find . -name '*.java' | sed 's|^\./||' | sort) \
         <(cd TGT  && find . -name '*.java' | sed 's|^\./||' | sort)
```
confirms all 27 claimed new files are absent from 7.5.0:

* §10 values (7): `UncertainValue`, `UncertainBooleanValue`, `UBooleanValue`, `UIntegerValue`,
  `URealValue`, `UStringValue`, `SBooleanValue`.
* §11 types (7): `UncertainType`, `UncertainBooleanType`, `UIntegerType`, `URealType`,
  `UStringType`, `UBooleanType`, `SBooleanType`.
* §12 expressions (8): `ExpConstUBoolean`, `ExpConstUInteger`, `ExpConstUReal`,
  `ExpConstUString`, `ExpConstSBoolean`, `ExpDefSBoolean`, `ExpUSelect`, `ExpUSelectC`.
* §13 parser (5 + 1): `ASTUBooleanLiteral`, `ASTUIntegerLiteral`, `ASTURealLiteral`,
  `ASTUStringLiteral`, `ASTSBooleanLiteral`, plus the dead `ASTSBooleanDefExpression`.

No near-miss collisions: 7.5.0's `ASTUndefinedLiteral` / `ASTUnlimitedNaturalLiteral` /
`ExpConstUnlimitedNatural` / `UndefinedValue` / `UnlimitedNaturalValue` /
`UnlimitedNaturalType` are unrelated names.

`13-grammar.md:512-538` (§13.3.7) correctly **refutes** the port plan's single
`ASTUncertainLiteral`, and `§13.3.8`'s "`ASTSBooleanDefExpression` is dead" is confirmed:
`grep -rn ASTSBooleanDefExpression --include=*.java --include=*.gpart --include=*.g FORK/src/`
returns only its own declaration and ctor. `ExpDefSBoolean` is likewise referenced only by
that dead AST class and by the three visitors.

### 2.2 §10 upstream-edit claims — all three verified exactly

* `Value.java` — `diff -u T/uml/ocl/value/Value.java F/uml/ocl/value/Value.java`: the only
  non-`$Id`/`@version` hunks add four **non-abstract** `public boolean isU*/isSBoolean()
  { return false; }` predicates. Nothing removed, no signature changed, no abstract member
  added ⇒ the claim "no existing 7.5.0 value class needs touching" holds. Corroborated: the
  same diff against `BooleanValue.java`, `IntegerValue.java`, `StringValue.java` yields only
  `$Id`/`@version` hunks. No `isUString()` was added by the fork — §10's warning not to add one
  "for symmetry" is right.
* `RealValue.java` — `valueOf(Value)` (fork `:90-99`) is the *sole* behavioural hunk; the other
  two are `$Id` and `@version`. §11's "alternative that avoids the edit" is correctly offered.
* `CollectionValue.java` — the five methods are exactly as tabulated (`uIncludes` max-fold with
  `probability() < 1` early exit; `uIncludesAll` size guard then `and`-fold with `> 0` early
  exit; `uExcludes` `uDistinct` `and`-fold; `uExcludesAll`; `uCountC` returning `int`), all
  non-abstract ⇒ `SetValue`/`BagValue`/`SequenceValue`/`OrderedSetValue` need no change. The
  remaining hunks are exactly the ones §12 names: `$Id`, `@version`, `java.util.*` → eight
  explicit imports, one reworded javadoc at T `:74`, one trailing blank line.
* Tier-1 `MathUtil.round(double,int)` confirmed at `F/util/MathUtil.java:96-109`
  (`Math.round(value * 10^digits) / 10^digits`, `@author Víctor Manuel Ortiz`); the only other
  hunks are `$Id` and two `<br/>` → `</br>` javadoc regressions that must **not** be ported.
* `UBooleanValue.valueOf(Value)` returning `null` for an `SBooleanValue` — the basis of §12.2's
  latent-NPE finding — confirmed at `F/uml/ocl/value/UBooleanValue.java:122-138`
  (`isUBoolean()` and `isBoolean()` branches only).

### 2.3 §11 upstream-edit claims — all nine verified exactly

Per-file diffs (`diff -u --strip-trailing-cr T/uml/ocl/type/$f.java F/uml/ocl/type/$f.java`):

| file | claim | verdict |
|---|---|---|
| `Type.java` | 10 new interface declarations, nothing else | confirmed (10 `boolean isTypeOf*/isKindOf*` added; `qualifiedName`, DataType pair untouched) |
| `TypeImpl.java` | 10 `return false` no-ops | confirmed |
| `TypeFactory.java` | 5 interned fields + 5 `mk*` + 5 map entries | confirmed exactly; note `mkUReal()` returns `Type` while the other four return concrete types — §5.2 already flags this |
| `BooleanType.java` | 2 `isKindOf*`, `conformsTo` +2 disjuncts, `allSupertypes` +2 | confirmed |
| `IntegerType.java` | 2 `isKindOf*`, `allSupertypes` +2 (capacity 3→5), **`conformsTo` untouched** | confirmed — `conformsTo` really is absent from the diff |
| `RealType.java` | 1 `isKindOf*`, `conformsTo` `\|\| t.isTypeOfUReal()`, `allSupertypes` +1 | confirmed |
| `StringType.java` | same shape for `UString` | confirmed |
| `VoidType.java` | 5 `return h == VoidHandling.INCLUDE_VOID` | confirmed (the apparent `isKindOfDataType` removal is 7.5.0-only drift, not a fork edit) |
| `MClassifierImpl.java` | 10 `return false` no-ops, `conformsTo`/`allSupertypes`/`getLeastCommonSupertype` untouched | confirmed |

§4.3's "explicitly NOT changed" list also holds. `BasicType`, `OclAnyType`,
`UnlimitedNaturalType`, `UniqueLeastCommonSupertypeDeterminator`, `EnumType`, `OrderedSetType`,
`TupleType`, `MessageType` produce zero substantive lines; `CollectionType`, `SetType`,
`SequenceType`, `BagType` produce exactly one javadoc `&lt;` → `<` escape each.

The new-type-class content summaries in §4.1 are accurate — spot-verified `UIntegerType`
(`allSupertypes = {OclAny, UReal, this}`, `conformsTo` = UInteger/UReal/OclAny),
`URealType` (`{this, OclAny}`), `UStringType` (`{UString, OclAny}`),
`UBooleanType` (`isKindOfOclAny→true`, `{UBoolean, OclAny, SBoolean}`, `conformsTo` accepts
self/OclAny/SBoolean).

**"Exactly two `Type` implementation roots" — verified, and the list is complete.**

```bash
grep -rn "implements Type\b" --include=*.java use-core/src use-gui/src
#   -> use-core/.../ocl/type/TypeImpl.java:32   (only hit)
grep -rn "extends Type\b\|extends Type," --include=*.java use-core/src use-gui/src | grep -v TypeImpl
#   -> uml/mm/MClassifier.java:36  "interface MClassifier extends Type, …"  (only sub-interface)
grep -rn "implements MClassifier\|extends MClassifierImpl" --include=*.java use-core/src use-gui/src
#   -> MClassifierImpl.java:34 (implements MClassifier); MClassImpl, MDataTypeImpl,
#      MAssociationImpl, MAssociationClassImpl, MSignalImpl, EnumType (extends MClassifierImpl)
grep -rn "mock(Type\|new Type\s*(" --include=*.java use-core/src/test use-gui/src   # 0 hits
```

So adding 10 methods to the `Type` interface compile-breaks precisely `TypeImpl` and
`MClassifierImpl`, both listed. No test double, no anonymous implementor, nothing in `use-gui`.

**No `Type`-dispatching visitor exists**, so nothing else can break structurally:
```bash
grep -rn "TypeVisitor"  --include=*.java use-core/src use-gui/src   # 0 hits
grep -rn "ValueVisitor" --include=*.java use-core/src use-gui/src   # 0 hits
```

### 2.4 §12's `ExpressionVisitor` compile-break enumeration is COMPLETE and correct

This is the section's strongest claim and it holds in full.

```bash
grep -rn "ExpressionVisitor" --include=*.java use-core/src use-gui/src \
  | grep -iE "implements|extends|new ExpressionVisitor"
```
yields exactly five class declarations, matching §5.1/§5.2 rows 1, 2, 5, 6, 7:

```
use-core/.../uml/ocl/expr/ExpressionPrintVisitor.java:35  implements ExpressionVisitor
use-core/.../analysis/coverage/AbstractCoverageVisitor.java:33  implements ExpressionVisitor
use-core/.../uml/ocl/expr/GenerateHTMLExpressionVisitor.java:30  extends ExpressionPrintVisitor
use-core/.../uml/ocl/expr/EvalNode.java:618  RelevantOperationHighlightVisitor extends GenerateHTMLExpressionVisitor
use-core/.../uml/ocl/expr/EvalNode.java:351  SubstituteVariablesExpressionVisitor extends RelevantOperationHighlightVisitor
```

and the transitive closure adds exactly rows 3 and 4:

```bash
grep -rn "extends AbstractCoverageVisitor\|extends ExpressionPrintVisitor\|extends GenerateHTMLExpressionVisitor\|extends CoverageCalculationVisitor" \
  --include=*.java use-core/src use-gui/src
#  -> BasicExpressionCoverageCalulator.java:40, CoverageCalculationVisitor.java:38  (+ the two above)
```

Negative results also confirmed: `grep -rn "ExpressionVisitor" use-core/src/test use-gui/src/test use-gui/src/it` → 0 hits;
`grep -rn "new ExpressionVisitor"` → 0 hits; no implementor in `use-gui`.

Counts confirmed: 7.5.0's interface declares 49 `void visit…`; the fork's 57
(`grep -c "void visit"`). Set difference:

```bash
comm -13 <(grep -oE "void visit[A-Za-z]+" T/.../ExpressionVisitor.java | sort) \
         <(grep -oE "void visit[A-Za-z]+" F/.../ExpressionVisitor.java | sort)
# visitConstSBoolean visitConstUBoolean visitConstUInteger visitConstUReal
# visitConstUString visitDefSBoolean visitObjOp visitUSelect visitUSelectC
comm -23 …   # visitInstanceOp
```
i.e. **exactly the 8 new methods plus the `visitObjOp` ↔ `visitInstanceOp` 7.0-era rename**,
exactly as `12-expressions.md:508-538` states. `AbstractCoverageVisitor` implements all 49
concretely (`grep -c "public void visit"` → 49), so §5.1 row 2's "abstract in theory, required
in practice" reasoning is right.

`Expression.java` needing no change is confirmed (`diff` shows `$Id` + javadoc only), as is
`ExpSelect.java` / `ExpReject.java` needing none.

### 2.5 §13 grammar claims — verified

* `OCLLexerRules.gpart` unchanged: `diff --strip-trailing-cr -u
  use-core/src/main/resources/grammars/base/OCLLexerRules.gpart FORK/.../OCLLexerRules.gpart`
  → empty. §13.2's "0 lines / file is identical" holds.
* The §13.0 path correction is right: `find use-core/src -name '*.gpart'` puts the grammar at
  `use-core/src/main/resources/grammars/base/`, not under `parser/`.
* §13.1.2's measurement reproduces exactly (I re-ran it against the live 7.5.0 file, having
  first confirmed `up_OCLBase.txt` is byte-identical to
  `sed 's/\r$//' use-core/src/main/resources/grammars/base/OCLBase.gpart`):
  `diff -u up_OCLBase.txt cand_OCLBase.txt` → **8 hunks, 35 added, 8 removed**. Only the
  absolute line counts are off by one (R10c).
* `ParserHelper.java` — **exactly** 6 added lines in 3 hunks
  (`Q_USELECT`/`Q_USELECTC` strings, ids 12/13, two `queryIdentMap.put`), nothing removed.
  §13.1.4's quoted block is verbatim correct.

---

## 3. Bottom line for the port log

Add to the upstream-edit ledger, currently missing from §10–§13:

1. `T/uml/ocl/expr/operations/StandardOperationsAny.java` — **modifies `Op_equal` and
   `Op_notequal`**, adds `Op_identical` (required by §13's grammar Change A) and its
   registration. (R1, R4)
2. `T/uml/ocl/expr/operations/StandardOperationsCollection.java` — **modifies `Op_includes`,
   `Op_excludes`, `Op_includesAll`, `Op_excludesAll`**. (R1)
3. `T/uml/ocl/expr/operations/StandardOperationsNumber.java` — large rewrite. (R1)
4. `T/uml/ocl/expr/operations/OpGeneric.java` — 5 registration lines. (R1)
5. `T/uml/ocl/expr/ExpressionPrintVisitor.java` — **existing `visitQuery` body**, not just
   8 new methods. (R2)
6. `T/analysis/coverage/AbstractCoverageVisitor.java` — **existing `visitQuery` body**, not
   just 8 new methods. (R2)
7. `T/parser/ocl/ASTQueryExpression.java` — field + `gen` dispatch (4 switch labels) +
   `toString`, not just a ctor overload. (R7)

Items 1–4 are documented in `20-ops-*.md` / `15-upstream-delta.md`; items 5–7 appear nowhere.

Re-open as decisions, not facts: the minimality of `CollectionValue` (R8), of `ExpQuery` (R5),
and of `Type`/`TypeImpl`/`VoidType`/`MClassifierImpl` (R9).

**UNVERIFIABLE / not attempted in this pass:** whether the four `operations/` files' rewrites
are behaviour-preserving for non-uncertain operands (that is `20-ops-*.md`'s subject); the
runtime correctness of any ported behaviour (no build was run, per the ground rules); and
whether the fork's own test suite exercises the `visitQuery` uncertainty traversal.
