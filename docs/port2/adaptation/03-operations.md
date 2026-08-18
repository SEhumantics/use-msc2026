# Adaptation — Part 03: the operation registries and overload resolution

**Governing policy (the user's, applied here, not debated):**

> Uncertainty meaning comes from the fork. Everything else comes from USE 7.5.0.
> Where the two collide, keep the uncertainty behaviour but express it the 7.5.0 way.

**Scope.** `org.tzi.use.uml.ocl.expr.operations.*` (the 13 upstream registry files, the 5 fork
uncertainty registry files, `OpGeneric`, `BooleanOperation`) and `ExpStdOp`'s overload resolution.
Types, values, the grammar and the `.in` harness belong to other parts and are cited here only
where an operation hard-depends on them.

**Method.** Every count, every type, every error string below was **executed**, not reasoned.
Eleven probe drivers were compiled and run — six against the historical jars, six against 7.5.0's
built classes (one, `P6`, on both sides) — plus a mechanical set-difference of the two live
`ExpStdOp.opmap`s. Claims read from source but not executed
are marked **READ_FROM_SOURCE**. Claims that could not be settled are marked **UNVERIFIABLE**.

**Provenance.**

| item | value |
|---|---|
| branch / HEAD | `port-uncertainty-2` @ `54e2745b1dc156d53a4533a6088ef606405cf7d0` |
| fork oracle | `.git/reference-repositories/uncertainty/USE-Uncertainty/lib/use.jar` md5 `8645269c1eacbf8cb52bf7f694c07b21` |
| fork uncertainty lib | `atenearesearchgroup.uncertainty.jar` md5 `a3055f54205babaa27484fa94efdda1c` |
| 7.5.0 side | `use-core/target/classes` (already built; **no Maven was run**) |

Path shorthand used throughout:

* `F/` = `.git/reference-repositories/uncertainty/USE-Uncertainty/src/main/org/tzi/use/`
* `T/` = `use-core/src/main/java/org/tzi/use/`
* `OPS/` = `…/uml/ocl/expr/operations/`

---

## 0. How to reproduce every number in this document

```sh
L=/home/xoruser/msc-4/use-msc2026/.git/reference-repositories/uncertainty/USE-Uncertainty/lib
CPF="$L/use.jar:$L/atenearesearchgroup.uncertainty.jar:$L/antlr-3.4-complete.jar:\
$L/guava-20.0.jar:$L/vtd-xml.jar"
javac -nowarn -cp "$CPF" -d out  Probe.java && java -cp "out:$CPF"  Probe    # fork side

T=/home/xoruser/msc-4/use-msc2026/use-core/target/classes
M=/home/xoruser/.m2/repository
CP75="$T:$M/org/antlr/antlr-runtime/3.5.3/antlr-runtime-3.5.3.jar:\
$M/com/google/guava/guava/33.6.0-jre/guava-33.6.0-jre.jar"
javac -nowarn -cp "$CP75" -d out75 P5.java && java -cp "out75:$CP75" P5      # 7.5.0 side
```

Drivers used this session (scratch, `/tmp/probe/` and `/tmp/probe750/`, never in the repo):

| driver | side | what it prints |
|---|---|---|
| `Probe` / `Probe2` | fork | resolved `type()` for 92 expressions (47 + 45); the complete `ExpStdOp.opmap` in registration order |
| `Winner` | fork | the winning `OpGeneric` for **74 970** `(name, signature)` cells under three registration arrangements |
| `Order` / `Order3` | fork | expression-level diff of the same three arrangements |
| `P6` | both | unary `+` / `-` on non-numeric operands |
| `P` / `P3` | 7.5.0 | resolved type, evaluated value, evaluated value's type, `stringRep` |
| `P5` | 7.5.0 | every 7.5.0 std op that accepts a **ported** `URealType` (a real `TypeImpl` subclass, not a mock) |
| `P2` / `P4` | 7.5.0 | the same sweep with `java.lang.reflect.Proxy` stubs, as a cross-check on `P5` |

Full driver sources are reproduced in Appendix A so this document is self-contained.

---

## 1. The two registries, mechanically diffed

`OpGeneric.registerOperations` is the single entry point. The two trees differ by **exactly six
lines**:

```
$ diff -u <(tr -d '\r' < F/uml/ocl/expr/operations/OpGeneric.java) \
          <(tr -d '\r' < T/uml/ocl/expr/operations/OpGeneric.java)
@@ -88,13 +88,6 @@
 		StandardOperationsNumber.registerTypeOperations(opmap);
 		StandardOperationsString.registerTypeOperations(opmap);
 		StandardOperationsBoolean.registerTypeOperations(opmap);
-
-		// Uncertainty Types
-        StandardOperationsUReal.registerTypeOperations(opmap);
-        StandardOperationsUBoolean.registerTypeOperations(opmap);
-        StandardOperationsUInteger.registerTypeOperations(opmap);
-        StandardOperationsUString.registerTypeOperations(opmap);
-        StandardOperationsSBoolean.registerTypeOperations(opmap);
 		
 		// Collections
 		StandardOperationsCollection.registerTypeOperations(opmap);
```

Fork slots: `F/OPS/OpGeneric.java:93-97`. 7.5.0's identical prefix/suffix: `T/OPS/OpGeneric.java:82-90`
and `:93-97`.

### 1.1 Per-file diff of the 13 shared registry files (CRLF normalised)

| file | changed lines | what changed |
|---|---|---|
| `BooleanOperation` | **0** | — |
| `StandardOperationsBag` | **0** | — |
| `StandardOperationsBoolean` | **0** | — |
| `StandardOperationsObject` | **0** | — |
| `StandardOperationsOrderedSet` | **0** | — |
| `StandardOperationsSet` | **0** | — |
| `StandardOperationsString` | **0** | — |
| `StandardOperationsEnum` | 3 | a dropped `// $Id$` line. Cosmetic. |
| `OpGeneric` | 8 | the six registration lines above |
| `StandardOperationsSequence` | 126 | 124 cosmetic (`Type params[]`→`Type[] params`, `opmap`→`opMap`, diamond); **1 real**: `Op_sequence_subSequence.isInfixOrPrefix()` `true`→`false` (upstream fix, no uncertainty content) |
| `StandardOperationsAny` | 183 | fork **adds** `Op_identical`; fork **rewrites** `Op_equal`, `Op_notequal` |
| `StandardOperationsCollection` | 345 | fork **adds** `Op_collection_uCount`, `Op_collection_uCountC`; fork **rewrites** `includes`, `excludes`, `includesAll`, `excludesAll`, `sum` |
| `StandardOperationsNumber` | 884 | 7.5.0 **adds** `Op_number_pow`, `Op_number_sqrt`; fork **rewrites** `ArithOperation` + 13 concrete classes |

Reproduce:

```sh
for f in OpGeneric BooleanOperation StandardOperationsAny StandardOperationsBag \
         StandardOperationsBoolean StandardOperationsCollection StandardOperationsEnum \
         StandardOperationsNumber StandardOperationsObject StandardOperationsOrderedSet \
         StandardOperationsSequence StandardOperationsSet StandardOperationsString; do
  echo "$f $(diff <(tr -d '\r' < F/OPS/$f.java) <(tr -d '\r' < T/OPS/$f.java) | wc -l)"
done
```

### 1.2 Runtime registry diff — MEASURED, and it settles item 1

Both `ExpStdOp.opmap`s were dumped at runtime as `(ocl-name, implementing-class)` pairs and
`comm`-diffed:

```
750-only pairs : 2
fork-only pairs: 109   (70 named classes + 39 anonymous SBoolean enum constants)
shared pairs   : 110
```

**The complete set of operations 7.5.0 has that the fork never saw is:**

```
pow	Op_number_pow
sqrt	Op_number_sqrt
```

That is the whole answer to "find every such case". It is not an eyeball claim: it is a set
difference over the two live multimaps. The 39 anonymous SBoolean entries independently corroborate
**B2 = 39 SBoolean operations**.

**READ_FROM_SOURCE** on the *why*: upstream `git log --follow` on `StandardOperationsNumber.java`
(`.git/reference-repositories/upstream-use`) shows `pow`/`sqrt` were added at `cc20293f`
(2024-06-27), after being removed at `29171370` (2024-06-20) with the commit message *"Removed
support for number operations 'pow' and 'sqrt' because they did not work in that implementation"*,
then `Op_number_sqrt.matches` was **widened** at `57b97a02` (2024-06-29, *"Fix for number operation
'sqrt'"*) from

```java
return (params.length == 1 && params[0].isTypeOfReal()) ? TypeFactory.mkReal() : null;   // pre-fix
```

to

```java
return (params.length == 1 && params[0].isKindOfNumber(VoidHandling.EXCLUDE_VOID)) ? TypeFactory
		.mkInteger() : null;                                                              // 7.5.0
```

**That June-2024 widening is what creates the hazard.** Against the pre-fix `isTypeOfReal`
predicate a `URealType` would have been declined and there would be nothing to adapt.

### 1.3 Other registration paths

`grep -rn 'ExpStdOp.addOperation\|removeAllOperations\|registerOperations(' T/` finds exactly one
other writer: `T/uml/ocl/extension/ExtensionManager.java:120` (`ExpStdOp.addOperation(op)`) and
`:136` (`removeAllOperations`). Extensions are appended to a live `ArrayListMultimap`, so they land
**after everything** and can never win a first-match race against a registered op. No ordering
hazard. Note that `oclextensions/Real.xml` is **byte-identical** in the two trees (`diff` on the
CRLF-normalised files is empty) and declares `sqrt`, `power`, `ceil`, `log`, `sin`, `cos`, `tan`,
`asin`, `acos`, `atan`, `e` on `source="Real"` — so `power` exists upstream too, as a Ruby extension
op, and will sit behind `Op_ureal_power`/`Op_uInteger_power` in the bucket.

---

## 2. Hazard 1 — 7.5.0 operations that admit an uncertain receiver

### 2.1 Why they admit it

The fork's lattice makes the U-types answer TRUE to upstream's kind-of predicates:

| type | override | file:line |
|---|---|---|
| `URealType` | `isKindOfNumber(h) → true` | `F/uml/ocl/type/URealType.java:28-30` |
| `UIntegerType` | `isKindOfNumber(h) → true` | `F/uml/ocl/type/UIntegerType.java:19-21` |
| `UStringType` | *(no `isKindOfString` override)* | `F/uml/ocl/type/UStringType.java` |
| `UBooleanType` | *(no `isKindOfBoolean` override)* | `F/uml/ocl/type/UBooleanType.java` |
| `SBooleanType` | *(no `isKindOfBoolean` override)* | `F/uml/ocl/type/SBooleanType.java` |

So the exposed surface splits cleanly: **`UReal` and `UInteger` are the dangerous receivers**
(they walk straight into every `isKindOfNumber` predicate); `UString`, `UBoolean`, `SBoolean` are
exposed only through `getLeastCommonSupertype(…) != null` and unconditional arity-1 predicates.

### 2.2 The exhaustive list — MEASURED against 7.5.0's live registry

Driver `P5` defines `PortedURealType extends org.tzi.use.uml.ocl.type.TypeImpl` in 7.5.0's own
package, overriding **exactly** the fork's `URealType.java:12-38` (`conformsTo`, `isKindOfNumber`,
`allSupertypes`). This is a faithful stand-in, not a mock. It then calls `matches` on every op in
`ExpStdOp.opmap` for signatures `{U}`, `{U,Int}`, `{Int,U}`, `{U,U}`, `{U,Real}`, `{Real,U}`,
`{U,Int,Int}`.

```
total accepting cells = 74      (21 distinct op classes, 20 distinct OCL names)
u.getLeastCommonSupertype(Integer) = OclAny
Integer.getLeastCommonSupertype(u) = OclAny
u.isKindOfOclAny(EXCLUDE_VOID)     = false
```

| # | OCL name | 7.5.0 class | `matches` @ `T/OPS/StandardOperationsNumber.java` (unless noted) | declared result for a `UReal` receiver | fork neutralises it how |
|---|---|---|---|---|---|
| 1 | `+` | `Op_number_add` | `ArithOperation.matches` `:63-73` | `Real` | rewrites `ArithOperation.matches` + `eval` |
| 2 | `+` | `Op_number_unaryplus` | `:365` | `UReal` | **already correct**; `eval` is a nop (`:369-372`) |
| 3 | `-` | `Op_number_sub` | `ArithOperation.matches` `:63-73` | `Real` | rewrites `matches` + `eval` |
| 4 | `-` | `Op_number_unaryminus` | `:327` | `UReal` | `matches` already correct; **`eval` `:331-341` casts to `RealValue` → CCE**; fork rewrites `eval` only |
| 5 | `*` | `Op_number_mult` | `ArithOperation.matches` `:63-73` | `Real` | rewrites `matches` + `eval` |
| 6 | `/` | `Op_number_div` | `:216-218` | `Real` | rewrites `matches` + `eval` |
| 7 | `<` | `Op_number_less` | `:628-631` | `Boolean` | rewrites → `UBoolean` |
| 8 | `>` | `Op_number_greater` | `:677-680` | `Boolean` | rewrites → `UBoolean` |
| 9 | `<=` | `Op_number_lessequal` | `:725-728` | `Boolean` | rewrites → `UBoolean` |
| 10 | `>=` | `Op_number_greaterequal` | `:773-776` | `Boolean` | rewrites → `UBoolean` |
| 11 | `max` | `Op_number_max` | `ArithOperation.matches` `:63-73` | `Real` | rewrites `eval` |
| 12 | `min` | `Op_number_min` | `ArithOperation.matches` `:63-73` | `Real` | rewrites `eval` |
| 13 | `floor` | `Op_real_floor` | `:397-398` | `Integer` | rewrites → `UReal` |
| 14 | `round` | `Op_real_round` | `:435-436` | `Integer` | rewrites → `UReal` |
| 15 | `toString` | `Op_number_toString` | `:902-903` | `String` | **benign** — fork agrees (`UReal(2,0.5).toString()` → `String`, measured) |
| 16 | `=` | `Op_equal` | `T/OPS/StandardOperationsAny.java:45-50` | `Boolean` | rewrites → `UBoolean`/`SBoolean` |
| 17 | `<>` | `Op_notequal` | `T/OPS/StandardOperationsAny.java:99-104` | `Boolean` | rewrites → `UBoolean`/`SBoolean` |
| 18 | `isDefined` | `Op_isDefined` | `StandardOperationsAny` | `Boolean` | **benign**, unmodified in fork |
| 19 | `isUndefined` / `oclIsUndefined` | `Op_isUndefined` | `StandardOperationsAny` | `Boolean` | **benign**, unmodified in fork |
| 20 | **`pow`** | **`Op_number_pow`** | **`:820-822`** | **`Real`** | **NOTHING — the fork has no `pow` at all** |
| 21 | **`sqrt`** | **`Op_number_sqrt`** | **`:866-867`** | **`Integer`** | **NOTHING — the fork's `sqrt` bucket contains only `Op_ureal_sqrt`, `Op_uInteger_sqrt`** |

Rows 18–19 and 15 are safe; rows 2 and 4's `matches` are already right; every other pre-2024 row is
handled by the three-way merge on `StandardOperationsNumber` / `StandardOperationsAny`.
**Rows 20 and 21 have no fork counterpart to merge with, and they are the only two.**

For the non-numeric uncertain types, driver `P4` (proxy stub with no kind-of overrides) gives the
much shorter list — `=`, `<>`, `count`, `includes`, `excludes`, `including`, `excluding`,
`isDefined`, `isUndefined`, `oclIsUndefined` — of which only `=`, `<>`, `includes`, `excludes` are
rewritten by the fork; the rest are already correct.

### 2.3 `sqrt` and `pow` in detail

**MEASURED, plain 7.5.0 (`P3`):**

```
4.sqrt()       declaredType=Integer  value=2.0   valueType=Real   stringRep=4.sqrt
4.0.sqrt()     declaredType=Integer  value=2.0   valueType=Real   stringRep=4.0.sqrt
2.sqrt()       declaredType=Integer  value=1.4142135623730951 valueType=Real  stringRep=2.sqrt
4.pow(2)       declaredType=Real     value=16.0  valueType=Real   stringRep=(4 pow 2)
4.pow(0.5)     declaredType=Real     value=2.0   valueType=Real   stringRep=(4 pow 0.5)
```

**MEASURED, fork (`Probe`):**

```
4.sqrt()             COMPILE-ERROR: probe:1:2: Undefined operation `Integer.sqrt()'.
4.0.sqrt()           COMPILE-ERROR: probe:1:4: Undefined operation `Real.sqrt()'.
4.pow(2)             COMPILE-ERROR: probe:1:2: Undefined operation named `pow' in expression `Integer.pow(Integer)'.
UReal(4,2).sqrt()    type=UReal
UInteger(4,0).sqrt() type=UInteger
UReal(4,2).pow(2)    COMPILE-ERROR: probe:1:11: Undefined operation named `pow' in expression `UReal.pow(Integer)'.
UReal(4,2).power(2)  type=UReal
```

Three consequences the port must own:

1. **`sqrt` mis-resolution.** With 7.5.0's order (`Number` at `:88`, uncertainty at `:93+`) and
   first-match-wins, `UReal(4,2).sqrt()` resolves to `Op_number_sqrt`, types as `Integer`, and then
   `eval` runs `((RealValue) args[0]).value()` at `T/OPS/StandardOperationsNumber.java:876` on a
   `URealValue`. `URealValue extends UncertainValue extends Value` (`F/uml/ocl/value/URealValue.java:14`,
   `UncertainValue.java:15`) — **it is not a `RealValue`**, so this is a `ClassCastException` at
   evaluation. READ_FROM_SOURCE for the CCE itself (the port does not exist yet to run it); the two
   halves — that `Op_number_sqrt.matches` accepts the type, and that `URealValue` is not a
   `RealValue` — are each measured/read directly.
2. **`pow` mis-resolution**, same mechanism, declared `Real`, `eval` casts at `:829-838`.
3. **An upstream type-soundness defect, out of scope but must be recorded:** `Op_number_sqrt`
   declares `Integer` and returns a `RealValue`. It is invisible to upstream's own test
   `use-gui/src/it/resources/testfiles/shell/t001.in:167-171`, which expects
   `? 9.sqrt` → `*-> 3.0 : Real`, because the shell prints the **value's** runtime type, not the
   expression's declared type. Under B7 the temptation is to "fix" this to `mkReal()`. **Do not.**
   It is not an uncertainty defect; the policy sends non-uncertainty behaviour to 7.5.0 unchanged.
   Record it, waive it, leave it.

---

## 3. Hazard 2 (the reverse) — fork operations 7.5.0 also defines

Three sub-cases, and only the first is a genuine collision.

### 3.1 Same OCL name, different implementing class — **1 case: `sqrt`**

`sqrt` is the only OCL name for which both trees register a class and the classes differ:

| tree | bucket contents (registration order) |
|---|---|
| fork | `Op_ureal_sqrt`, `Op_uInteger_sqrt` |
| 7.5.0 | `Op_number_sqrt` |
| port (naïve) | `Op_number_sqrt`, `Op_ureal_sqrt`, `Op_uInteger_sqrt` ← **`Op_number_sqrt` wins for `UReal`** |

Near-miss worth stating explicitly: `pow` (7.5.0) and `power` (fork) are **different names**, so they
do not collide. The port will expose both. `power` will also carry the Ruby extension entry from
`oclextensions/Real.xml` at the tail of the bucket.

### 3.2 Same class name, fork rewrote the body — **21 classes**

These are the true "collide" cases in the policy's sense; each is a three-way merge, not a
choose-a-side.

| file | classes the fork rewrote |
|---|---|
| `StandardOperationsAny` (2) | `Op_equal` `F/…:34`, `Op_notequal` `F/…:184` |
| `StandardOperationsCollection` (5) | `Op_collection_includes` `:83`, `Op_collection_excludes` `:148`, `Op_collection_includesAll` `:377`, `Op_collection_excludesAll` `:449`, `Op_collection_sum` `:576` |
| `StandardOperationsNumber` (14) | `ArithOperation` `:54` (abstract base) + `Op_number_add` `:96`, `Op_number_sub` `:175`, `Op_number_mult` `:255`, `Op_number_div` `:327`, `Op_number_unaryminus` `:505`, `Op_real_floor` `:586`, `Op_real_round` `:645`, `Op_number_max` `:712`, `Op_number_min` `:788`, `Op_number_less` `:922`, `Op_number_greater` `:1000`, `Op_number_lessequal` `:1077`, `Op_number_greaterequal` `:1155` |

**Merge rule per member, derived from the row's mechanism:**

* `matches` — take the **fork's**, except `Op_number_unaryminus`/`Op_number_unaryplus` where 7.5.0's
  `params[0]` passthrough is already correct and the fork did not touch it.
* `eval` — take the **fork's**.
* everything else (`name`, `kind`, `isInfixOrPrefix`, `checkWarningUnrelatedTypes`) — take **7.5.0's**;
  measured identical in every one of the 21 anyway.
* the two ops 7.5.0 added (`Op_number_pow`, `Op_number_sqrt`) — take **7.5.0's**, plus the §5 guard.

**Taking the fork's `StandardOperationsNumber.java` wholesale deletes `pow` and `sqrt` from OCL** and
silently breaks `use-gui/src/it/resources/testfiles/shell/t001.in:161-171`. This is the single
highest-value line in this document.

### 3.3 Fork operations 7.5.0 does not have at all — **109 registry pairs**

70 named + 39 anonymous SBoolean. Of these, 24 OCL **names** are already occupied in 7.5.0:

```
+  <  <=  >  >=  abs  and  at  div  implies  indexOf  max  min  mod
not  or  size  sqrt  substring  toBoolean  toInteger  toReal  toString  xor
```

`P5`'s exhaustive sweep proves that for 21 of those 24, the 7.5.0 incumbent **declines** the
uncertain receiver (`abs`, `and`, `at`, `div`, `implies`, `indexOf`, `mod`, `not`, `or`, `size`,
`substring`, `toBoolean`, `toInteger`, `toReal`, `xor` never appear in its output; `+`, `<`, `<=`,
`>`, `>=`, `max`, `min` appear only via the *number* op, which the fork rewrites). The three that
matter are `sqrt` (§3.1), `toString` (benign, §2.2 row 15), and — for a **String** receiver, not a
`UString` one — the reverse-direction capture analysed in §4.

One fork-only registration deserves separate mention because it is not an operation problem at all:

* **`equals` → `Op_identical`** (`F/OPS/StandardOperationsAny.java:18`, class at `:130`). 7.5.0 has
  no `equals` operation **and no `equals` token**. MEASURED on 7.5.0: `1.equals(2)` →
  ``Undefined operation named `equals' in expression `Integer.equals(Integer)'.``, and `1 equals 2` →
  `line 1:2 missing EOF at 'equals'`. The fork carries a dedicated grammar production
  `identicalExpression` (`F/parser/base/OCLBase.gpart:128-135`, replicated into the six generated `.g`
  files `OCL.g:197`, `USE.g:706`, `Soil.g:742`, `ShellCommand.g:495`, `TestSuite.g:304`,
  `Generator.g:973`). **Registering `Op_identical` without porting that production makes it dead
  code.**
  Cross-reference the grammar part (13); flagged here because the registry is where it looks alive.

---

## 4. Registration order — where the uncertainty registries must go

### 4.1 The resolution rule

`ExpStdOp.opmap` is an `ArrayListMultimap` (`T/uml/ocl/expr/ExpStdOp.java:54`), so `opmap.get(name)`
returns candidates **in registration order**, and both `create` (`:121-127`) and `exists` (`:90-95`)
return on the **first** candidate whose `matches` is non-`null`. There is no specificity ranking, no
ambiguity error, no best-match. First match wins, full stop. This is identical in the two trees —
the `ExpStdOp` diff is 100% comment/import churn plus a deleted `$Id$` line.

### 4.2 The experiment

Rather than argue, I built the registry three ways inside the running fork (reflectively replacing
`ExpStdOp.opmap`) and computed the **winning op class and declared result type for every
`(name, signature)` cell** over 17 real types
(`Integer, Real, Boolean, String, UnlimitedNatural, OclAny, OclVoid, UReal, UInteger, UBoolean,
UString, SBoolean, Set(Integer), Set(UReal), Sequence(Integer), Bag(UString), OrderedSet(Integer)`)
at arities 1, 2 and 3 — **74 970 cells**.

| arrangement | differing cells vs the fork's own order |
|---|---|
| **A** fork order (`Any, Object, Enum, Number, String, Boolean, U*, Collections`) | — (baseline) |
| **B** uncertainty **first** (`U*` then everything) | **22** |
| **C** uncertainty **last** (everything then `U*`) | **0** |

Arrangement **B**'s 22 cells, verbatim:

```
<(String,String,)          A=Op_string_less->Boolean         B=Op_uString_less->UBoolean
<=(String,String,)         A=Op_string_lessequal->Boolean    B=Op_uString_less_or_equal->UBoolean
>(String,String,)          A=Op_string_greater->Boolean      B=Op_uString_greater->UBoolean
>=(String,String,)         A=Op_string_greaterequal->Boolean B=Op_uString_greater_or_equal->UBoolean
and(Boolean,Boolean,)      A=Op_boolean_and->Boolean         B=Op_uBoolean_and->UBoolean
and(Boolean,OclVoid,)      A=Op_boolean_and->Boolean         B=Op_uBoolean_and->UBoolean
and(OclVoid,Boolean,)      A=Op_boolean_and->OclVoid         B=Op_uBoolean_and->UBoolean
and(OclVoid,OclVoid,)      A=Op_boolean_and->OclVoid         B=Op_uBoolean_and->UBoolean
implies(Boolean,Boolean,)  A=Op_boolean_implies->Boolean     B=Op_uBoolean_implies->UBoolean
implies(Boolean,OclVoid,)  A=Op_boolean_implies->Boolean     B=Op_uBoolean_implies->UBoolean
implies(OclVoid,Boolean,)  A=Op_boolean_implies->OclVoid     B=Op_uBoolean_implies->UBoolean
implies(OclVoid,OclVoid,)  A=Op_boolean_implies->OclVoid     B=Op_uBoolean_implies->UBoolean
not(Boolean,)              A=Op_boolean_not->Boolean         B=Op_uBoolean_not->UBoolean
not(OclVoid,)              A=Op_boolean_not->OclVoid         B=Op_uBoolean_not->UBoolean
or(Boolean,Boolean,)       A=Op_boolean_or->Boolean          B=Op_uBoolean_or->UBoolean
or(Boolean,OclVoid,)       A=Op_boolean_or->Boolean          B=Op_uBoolean_or->UBoolean
or(OclVoid,Boolean,)       A=Op_boolean_or->OclVoid          B=Op_uBoolean_or->UBoolean
or(OclVoid,OclVoid,)       A=Op_boolean_or->OclVoid          B=Op_uBoolean_or->UBoolean
xor(Boolean,Boolean,)      A=Op_boolean_xor->Boolean         B=Op_uBoolean_xor->UBoolean
xor(Boolean,OclVoid,)      A=Op_boolean_xor->Boolean         B=Op_uBoolean_xor->UBoolean
xor(OclVoid,Boolean,)      A=Op_boolean_xor->OclVoid         B=Op_uBoolean_xor->UBoolean
xor(OclVoid,OclVoid,)      A=Op_boolean_xor->OclVoid         B=Op_uBoolean_xor->UBoolean
```

Cause, READ_FROM_SOURCE: `Op_uString_less.matches` tests `isKindOfUString` and
`StringType.isKindOfUString` returns `true` (`F/uml/ocl/type/StringType.java:50`); `Op_uBoolean_and`
etc. test `isKindOfUBoolean` and `BooleanType.isKindOfUBoolean` returns `true`
(`F/uml/ocl/type/BooleanType.java:50`). Under the fork's lattice the **plain** types are kind-of the
**uncertain** ones, so an uncertainty-first bucket swallows ordinary String comparison and ordinary
Boolean logic.

An independent expression-level run (`Order`, 87 compiled expressions) agrees: 9 expressions change
under **B** (`'a' < 'b'`, `'a' <= 'b'`, `'a' > 'b'`, `'a' >= 'b'`, `true and false`, `not true`,
`true or false`, `true implies false`, `true xor false`), 0 under **C**.

### 4.3 The decision

> **Register the five uncertainty registries AFTER `StandardOperationsBoolean`, at the fork's slot
> (`OpGeneric.java:91-97`). Do not move them earlier.**

* **Before the 7.5.0 basic-type registries → REJECTED.** 22 measured cells of ordinary,
  non-uncertain OCL change meaning. That is exactly what the policy's second sentence forbids.
* **At the fork's slot → the fork's own semantics, by construction** (arrangement A is the baseline).
* **At the very end → measured indistinguishable** (0 of 74 970 cells). The fork's slot is therefore
  *not* load-bearing among these cells; keep it anyway, because it is the one the fork's diff
  produces and it minimises the merge.

### 4.4 Correction to the standing record on `Integer + Integer`

`spec-parts/15-upstream-delta.md:550-553` warns that *"Registering the `U*` operations in the wrong
slot changes which overload wins for `Integer + Integer`."* **MEASURED: it does not.** `+(Integer,Integer)`
is identical in all three arrangements — the fork registers **no** `+` for numbers at all (its `+`
bucket is `Op_number_add, Op_number_unaryplus, Op_string_concatinfix, Op_uString_uConcat,
Op_uString_uConcat`; the only `+` the uncertainty registries contribute is the UString concat).

The record's *second* sentence is the correct one and it is about a different thing:

> The fork's `Op_number_add.matches` explicitly falls through `isTypeOfInteger → isTypeOfReal →
> isTypeOfUInteger → mkUReal`; reordering **those branches** changes the static type of ordinary
> arithmetic.

That is a **branch-order** constraint inside `ArithOperation.matches` (`F/OPS/StandardOperationsNumber.java:61-74`),
not a registry-slot constraint. Both constraints are real; they are not the same constraint, and the
port needs to satisfy both. Concretely, `ArithOperation.matches` must keep this exact order:

```java
if (params[0].isTypeOfInteger() && params[1].isTypeOfInteger())     return TypeFactory.mkInteger();
else if (isArgIntegerOrReal(params[0]) && isArgIntegerOrReal(params[1])) return TypeFactory.mkReal();
else if (params[0].getLeastCommonSupertype(params[1]).isTypeOfUInteger()) return TypeFactory.mkUInteger();
else if (params[0].isKindOfNumber(INCLUDE_VOID) && params[1].isKindOfNumber(INCLUDE_VOID))
                                                                     return TypeFactory.mkUReal();
```

Hoisting the `isKindOfNumber` arm above the `isTypeOfInteger` arm turns `1 + 1` into `UReal`.

---

## 5. The mixed-case probe table (item 4) — MEASURED

Compiled against the fork oracle jar; `type=` is `Expression.type()`.

| expression | **fork (measured)** | plain 7.5.0 (measured) | **what the port must give** |
|---|---|---|---|
| `UReal(2,0.5) + 1` | `UReal` | ``Undefined operation `UReal'.`` | **`UReal`** |
| `1 + UReal(2,0.5)` | `UReal` | ``Undefined operation `UReal'.`` | **`UReal`** |
| `UReal(4,2).sqrt()` | `UReal` | *(n/a; `4.sqrt()` → declared `Integer`)* | **`UReal`** — requires the §6 guard |
| `UInteger(2,0) * 3` | `UInteger` | n/a | **`UInteger`** |
| `UReal(1,0) < 2` | `UBoolean` | n/a | **`UBoolean`** |
| `UString('a',1).concat('b')` | ``COMPILE-ERROR: probe:1:15: Undefined operation `UString.concat(String)'.`` | n/a | **the same compile error** |

The last row is the one that will surprise an implementer, so state it plainly: the fork's `concat`
bucket holds only `Op_string_concat`, which requires a `String` receiver. `UString` concatenation is
spelled **`+`** (`Op_uString_uConcat.name()` returns `"+"`, `F/OPS/StandardOperationsUString.java`).
Measured: `UString('a',1) + 'b'` → `UString`, `'a' + UString('b',1)` → `UString`,
`UString('a',1) + UString('b',1)` → `UString`, and `'a' + 'b'` → `String` (unchanged). A port that
"helpfully" also registers `uConcat` under `concat` invents uncertainty meaning the fork does not
have.

### 5.1 The wider measured baseline

Kept here because these are the cells a differential harness will compare, and every one is a real
run, not a prediction.

| expression | fork | | expression | fork |
|---|---|---|---|---|
| `Set{UReal(2,0.5), 1, 2.5}` | `Set(UReal)` | | `Set{UReal(2,0.5), 1, 2.5}->sum()` | `UReal` |
| `Set{1, 2.5}` | `Set(Real)` | | `1 + 1` | `Integer` |
| `2 * UReal(2,0.5)` | `UReal` | | `2 - UReal(2,0.5)` | `UReal` |
| `UReal(2,0.5) - 2` | `UReal` | | `2 / UReal(2,0.5)` | `UReal` |
| `UInteger(2,0) / 2` | `UReal` | | `UInteger(2,0) + UReal(1,0.5)` | `UReal` |
| `3 + UInteger(2,0)` | `UInteger` | | `Set{UInteger(2,0)}->sum()` | `UInteger` |
| `2 < UReal(2,0.5)` | `UBoolean` | | `2 >= UReal(2,0.5)` | `UBoolean` |
| `UInteger(2,0) < 3` | `UBoolean` | | `UReal(2,0.5) < UReal(1,0.5)` | `UBoolean` |
| `UReal(2,0.5) = 2` | `UBoolean` | | `2 = UReal(2,0.5)` | `UBoolean` |
| `UReal(2,0.5) <> 2` | `UBoolean` | | `UReal(2,0.5).equals(2)` | `Boolean` |
| `UReal(-2.5,0.5).floor()` | `UReal` | | `UReal(-2.5,0.5).round()` | `UReal` |
| `UReal(2,0.5).max(1)` | `UReal` | | `UReal(2,0.5).min(1)` | `UReal` |
| `UReal(2,0.5).abs()` | `UReal` | | `-UReal(2,0.5)` | `UReal` |
| `+ UReal(2,0.5)` | `UReal` | | `UReal(2,0.5).toString()` | `String` |
| `UReal(2,0.5).uncertainty()` | `Real` | | `UReal(2,0.5).value()` | `Real` |
| `UReal(2,0.5).toInteger()` | `Integer` | | `UReal(2,0.5).toUInteger()` | `UInteger` |
| `UInteger(2,0).toInteger()` | `UInteger` | | `UInteger(2,0).toUReal()` | `UReal` |
| `UInteger(7,0) div 2` | `UInteger` | | `UInteger(7,0).mod(2)` | `UInteger` |
| `UString('a',1).size()` | `UInteger` | | `UString('a',1).at(1)` | `UString` |
| `UString('a',1).indexOf('a')` | `UString` | | `UString('a',1).substring(1,1)` | `UString` |
| `UString('a',1).toInteger()` | `Integer` | | `UString('a',1).toUBoolean()` | `UBoolean` |
| `UString('a',1) < UString('b',1)` | `UBoolean` | | `UBoolean(true,1).toBoolean()` | `Boolean` |
| `not UBoolean(true,1)` | `UBoolean` | | `UBoolean(true,1) xor UBoolean(false,1)` | `UBoolean` |
| `Set{UReal(2,0.5)}->includes(2)` | `UBoolean` | | `Set{UReal(2,0.5)}->excludes(2)` | `UBoolean` |
| `Set{UReal(2,0.5)}->includesAll(Set{2})` | `UBoolean` | | `Set{UReal(2,0.5)}->count(2)` | `Integer` |
| `Set{UReal(2,0.5)}->uCount(2)` | `Integer` | | `Set{UReal(2,0.5)}->uCountC(2, 0.9)` | `Integer` |
| `Set{UReal(2,0.5)}->size()` | `Integer` | | `Sequence{UReal(2,0.5)}->at(1)` | `UReal` |
| `Set{UReal(2,0.5)}->max()` | `UReal` | | `Sequence{UReal(2,0.5)}->indexOf(UReal(2,0.5))` | `Integer` |

Note `UInteger(2,0).toInteger()` → **`UInteger`**, not `Integer`: `toInteger` is a second
registration of `Op_uInteger_value` under an alias
(`OpGeneric.registerOperation("toInteger", new Op_uInteger_value(), opmap)`,
`F/OPS/StandardOperationsUInteger.java:17`). This is the only use of the two-argument
`registerOperation(String, OpGeneric, Multimap)` overload in the whole fork; 7.5.0 keeps that
overload (`T/OPS/OpGeneric.java:115`), so it ports unchanged.

---

## 6. The adaptation, operation by operation

### C1 — `Op_number_sqrt` swallows `UReal` / `UInteger`  *(the flagship)*

* **fork:** `UReal(4,2).sqrt()` → `UReal`; `4.sqrt()` → compile error.
* **7.5.0:** `4.sqrt()` → declared `Integer`, value `2.0 : Real`.
* **port must give:** `UReal(4,2).sqrt()` → `UReal` **and** `4.sqrt()` → 7.5.0's answer, unchanged.
* **adaptation:** tighten `T/OPS/StandardOperationsNumber.java:866-867` to decline uncertain types:

  ```java
  return (params.length == 1
          && params[0].isKindOfNumber(VoidHandling.EXCLUDE_VOID)
          && !(params[0] instanceof UncertainType))        // ← added
         ? TypeFactory.mkInteger() : null;
  ```

  With `Op_number_sqrt` declining, first-match-wins hands the call to `Op_ureal_sqrt` /
  `Op_uInteger_sqrt` at `OpGeneric.java:93,95`. Both halves of the policy are satisfied with a
  one-predicate edit in a file that already needs a three-way merge. Do **not** instead move the
  uncertainty registries earlier (§4.2 arrangement B: 22 regressions), and do **not** "fix"
  `mkInteger()` to `mkReal()` (§2.3 note 3).

### C2 — `Op_number_pow` swallows `UReal` / `UInteger`

* **fork:** `UReal(4,2).pow(2)` → ``Undefined operation named `pow'…`` (the fork has no `pow`);
  the fork spells it `UReal(4,2).power(2)` → `UReal`.
* **7.5.0:** `4.pow(2)` → `Real`.
* **port must give:** `UReal(4,2).pow(2)` → a **clean compile error**; `4.pow(2)` → `Real`;
  `UReal(4,2).power(2)` → `UReal`.
* **adaptation:** the same guard at `:820-822` on **both** parameters. Note this is *not* an
  alias-`pow`-to-`power` decision: aliasing would invent uncertainty meaning the fork does not have,
  which rule 1 of the policy forbids. The guard reproduces the fork exactly.

### C3 — the 21 rewritten upstream classes

Three-way merge per §3.2. Take the fork's `matches` and `eval`; take 7.5.0's everything else. The
two `matches` methods the fork did **not** touch (`Op_number_unaryplus:365`,
`Op_number_unaryminus:327`) are already correct because they return `params[0]`; only
`Op_number_unaryminus.eval` needs the fork's version (7.5.0's casts to `RealValue` at `:337`).

### C4 — `Op_identical` / `equals`

Register it (`F/OPS/StandardOperationsAny.java:18`) **only together with** the `identicalExpression`
grammar production. Otherwise it is unreachable and silently inflates the registry. Cross-reference
part 13.

### C5 — the two collection additions

`Op_collection_uCount` (`F/OPS/StandardOperationsCollection.java:261`) and `Op_collection_uCountC`
(`:311`) register names 7.5.0 does not use at all (`uCount`, `uCountC` are absent from the 7.5.0
opmap). No collision; append them at the fork's slot (`:41-42`), inside the merged
`StandardOperationsCollection`.

### C6 — `Op_uString_uConcat`: duplicate registration, and an `ArrayIndexOutOfBoundsException`

Two fork defects in one class; **both are B7 fixes, and the fix restores 7.5.0's behaviour exactly.**

1. **Duplicate registration** at `F/OPS/StandardOperationsUString.java:19` and `:21`. Dead but
   harmless under `ArrayListMultimap` + first-match. Already recorded at
   `spec-parts/20-ops-UString.md:36,98-100` and `stage-02.md:304`. Register once.
2. **AIOOBE.** `Op_uString_uConcat.matches` evaluates
   `params[0].isTypeOfUString() || params[1].isTypeOfUString()` **before** the `params.length == 2`
   guard. Because the class is registered under `"+"`, and `Op_number_unaryplus` also lives in that
   bucket at arity 1, any unary `+` on a non-numeric operand reaches it with a 1-element array.
   Java's `||` short-circuit hides this when `params[0]` *is* a `UString`, and exposes it otherwise.
   Recorded statically at `spec-parts/20-ops-UString.md:289-295,690`; **confirmed end-to-end this
   session** (driver `P6`, side-by-side):

   | expression | fork | plain 7.5.0 |
   |---|---|---|
   | `+ 'a'` | `THREW java.lang.ArrayIndexOutOfBoundsException: Index 1 out of bounds for length 1` | ``COMPILE-ERROR: Undefined operation `String.+()'.`` |
   | `+ true` | `THREW …ArrayIndexOutOfBoundsException…` | ``COMPILE-ERROR: Undefined operation `Boolean.+()'.`` |
   | `+ Set{1}` | `THREW …ArrayIndexOutOfBoundsException…` | ``COMPILE-ERROR: Undefined operation `Set(Integer)->+()'.`` |
   | `+ UBoolean(true,1)` | `THREW …ArrayIndexOutOfBoundsException…` | *(n/a — no `UBoolean`)* |
   | `+ SBoolean(0.5,0.3,0.2,0.5)` | `THREW …ArrayIndexOutOfBoundsException…` | *(n/a)* |
   | `+ UString('a',1)` | ``COMPILE-ERROR: Undefined operation `UString.+()'.`` | *(n/a)* — short-circuit saves it |
   | `+ 1` | `Integer` | `Integer` |
   | `+ UReal(2,0.5)` | `UReal` | *(n/a)* |

   **Fix:** hoist the length guard. `params.length == 2 && params[0].isKindOfUString(…) && …`.
   Post-fix, `+ 'a'` must produce 7.5.0's clean ``Undefined operation `String.+()'.``

### C7 — 7.5.0's `Op_number_pow` prints unreparseable text  *(record, do not fix)*

`Op_number_pow.isInfixOrPrefix()` returns `true` (`T/OPS/StandardOperationsNumber.java:815`), so
`OpGeneric.stringRep` renders `4.pow(2)` as `(4 pow 2)` — measured — and `4 pow 2` does not parse
(measured: `line 1:2 missing EOF at 'pow'`). Once C2's guard is in place no uncertain value can reach
this printer, so it stays a pure upstream defect. Record it; do not repair it under the uncertainty
mandate.

---

## 7. The `OpGeneric` contract delta

**There is none.** Beyond the six registration lines of §1, `OpGeneric.java` is identical in the two
trees, and `BooleanOperation.java` diffs to **0 lines**. Specifically, all of the following are
present and unchanged on both sides:

| member | both trees |
|---|---|
| `OPERATION = 0`, `SPECIAL = 3` | yes |
| `abstract String name()` | yes |
| `boolean isBooleanOperation()` default `false` | yes |
| `abstract int kind()` | yes |
| `abstract boolean isInfixOrPrefix()` | yes |
| `abstract Type matches(Type[])` | yes |
| `String checkWarningUnrelatedTypes(Expression[])` default `null` | yes |
| `abstract Value eval(EvalContext, Value[], Type)` | yes |
| `String stringRep(Expression[], String)` | yes, byte-identical |
| `registerOperation(OpGeneric, Multimap)` | yes (`T:105`, `F:112`) |
| `registerOperation(String, OpGeneric, Multimap)` | yes (`T:115`, `F:122`) — needed for the `toInteger` alias |
| `BooleanOperation.evalWithArgs(EvalContext, Expression[])` | yes, byte-identical |

The five uncertainty registry files therefore compile against 7.5.0's `OpGeneric` **without any
contract change**. What they will *not* compile against is 7.5.0's `Type` interface, which has no
`isTypeOfUReal()` / `isKindOfUReal(…)` / `isTypeOfSBoolean()` / `isKindOfUString(…)` /
`isKindOfUBoolean(…)` — 7.5.0 lacks all 5 declarations the fork adds at
`F/uml/ocl/type/Type.java:86,94,106,112,116`. That is part 02's problem, but it is the reason
part 03 cannot be merged first.

Two non-contract deltas worth noting:

* `StandardOperationsUReal` is declared `public class` (`F/…:10`) where the closest 7.5.0 analogue
  `StandardOperationsNumber` is package-private (`T/…:16`). Cosmetic; 7.5.0 is itself inconsistent.
* `StandardOperationsSBoolean` is an `enum` holding 39 anonymous `OpGeneric` subclasses, registered
  by iterating `values()`. `M-39` in `spec-parts/16-modernization-ledger.md:133` recommends **not**
  refactoring it. Agreed: registration order comes from `values()`, and the measured registry has
  exactly 39 SBoolean entries, so any refactor risks reordering for zero behavioural gain.

---

## 8. Ordered work list for the implementer

1. **Part 02 first.** The `Type` predicates (`isKindOfNumber` on `URealType`/`UIntegerType`;
   `isKindOfUString` on `StringType`; `isKindOfUBoolean` on `BooleanType`; `isKindOfUReal` on
   `IntegerType`/`RealType`) *are* the resolution semantics. Nothing in part 03 can be validated
   before they exist.
2. `OpGeneric.java` — insert the fork's six lines at `:91-97`, unchanged, at the fork's slot (§4.3).
3. Three-way merge `StandardOperationsNumber`, `StandardOperationsAny`,
   `StandardOperationsCollection` per §3.2's per-member rule. **Keep `Op_number_pow` and
   `Op_number_sqrt`.**
4. Apply the C1 and C2 guards.
5. Add the five uncertainty registry files, with the C6 fixes.
6. Decide C4 jointly with part 13.
7. **Regression gate.** Re-run the §4.2 winner sweep against the *ported* tree with the ported types
   substituted for the fork's, and assert **0** differing cells against the fork baseline
   (`/tmp/probe/winner.txt`, arrangement A) *except* the two `pow`/`sqrt` buckets, where the port is
   deliberately a superset. This is a mechanical, 74 970-cell oracle for the whole of part 03 and it
   costs one JUnit test.

---

## 9. Gaps, and what is not established

1. **UNVERIFIABLE until the port exists:** that `UReal(4,2).sqrt()` actually throws
   `ClassCastException` (as opposed to some other failure) in an unguarded port. Both premises are
   established separately — `Op_number_sqrt.matches` accepts the type (measured, `P5`), and
   `URealValue` is not a `RealValue` (read at `F/uml/ocl/value/URealValue.java:14`) — but the
   composite was not executed. **Settles by:** build the port without the C1 guard and compile
   `UReal(4,2).sqrt()`.
2. **Stand-in caveat.** `P5`'s `PortedURealType` reproduces the fork's `URealType` overrides on
   7.5.0's `TypeImpl`. If the actual port gives `URealType` any *additional* override that widens a
   predicate (`isKindOfString`, `isKindOfBoolean`, `isKindOfCollection`, `isKindOfOclAny`), §2.2's
   21-class list grows. The `P5` sweep must be re-run against the real ported type as part of step 7.
   The proxy-based `P2`/`P4` cross-checks agree with `P5` on every row they share.
3. **Not swept: arity ≥ 4 and tuple/enum/class-typed operands.** The 74 970-cell sweep covers arities
   1–3 over 17 types. Operations taking 4+ arguments (there are none in either opmap —
   **READ_FROM_SOURCE**, `grep -c 'params.length == 4'` is 0 in both trees) and `MessageType` /
   `EnumType` / user-class receivers were not enumerated. Judged low risk because no uncertainty
   predicate mentions them.
4. **Not covered here:** the semantics of the 109 fork-only operations. Those live in
   `spec-parts/20-ops-*.md`; part 03 only establishes *which one wins*.
5. **`.in` corpus not replayed.** The uncertainty `.in` files require a model and the USE shell;
   the 92+87-expression and 74 970-cell sweeps are a substitute for, not a replacement of, a corpus
   replay. Step 7's gate should run against the corpus once the harness exists.

---

## Appendix A — driver sources

### A.1 `Winner.java` (fork side; the 74 970-cell sweep)

```java
import java.util.*; import java.lang.reflect.Method;
import com.google.common.collect.*;
import org.tzi.use.uml.ocl.type.*;
import org.tzi.use.uml.ocl.expr.ExpStdOp;
import org.tzi.use.uml.ocl.expr.operations.OpGeneric;
public class Winner {
  static String[] UC={"StandardOperationsUReal","StandardOperationsUBoolean","StandardOperationsUInteger",
                      "StandardOperationsUString","StandardOperationsSBoolean"};
  static String[] STD={"StandardOperationsAny","StandardOperationsObject","StandardOperationsEnum",
    "StandardOperationsNumber","StandardOperationsString","StandardOperationsBoolean",
    "StandardOperationsCollection","StandardOperationsSet","StandardOperationsBag",
    "StandardOperationsSequence","StandardOperationsOrderedSet"};
  static void inv(String c, Multimap<String,OpGeneric> nm) throws Exception {
    Class<?> k=Class.forName("org.tzi.use.uml.ocl.expr.operations."+c);
    Method m=k.getDeclaredMethod("registerTypeOperations", Multimap.class);
    m.setAccessible(true); m.invoke(null,nm);
  }
  static ListMultimap<String,OpGeneric> build(int mode) throws Exception {
    ListMultimap<String,OpGeneric> nm=ArrayListMultimap.create(150,5);
    if(mode==0){
      for(String c:new String[]{"StandardOperationsAny","StandardOperationsObject","StandardOperationsEnum",
        "StandardOperationsNumber","StandardOperationsString","StandardOperationsBoolean"}) inv(c,nm);
      for(String c:UC) inv(c,nm);
      for(String c:new String[]{"StandardOperationsCollection","StandardOperationsSet","StandardOperationsBag",
        "StandardOperationsSequence","StandardOperationsOrderedSet"}) inv(c,nm);
    } else if(mode==1){ for(String c:UC) inv(c,nm); for(String c:STD) inv(c,nm); }
    else { for(String c:STD) inv(c,nm); for(String c:UC) inv(c,nm); }
    return nm;
  }
  static Type[] TS;
  static String tn(Type t){ return t==null?"null":t.toString(); }
  public static void main(String[] x) throws Exception {
    TS=new Type[]{ TypeFactory.mkInteger(), TypeFactory.mkReal(), TypeFactory.mkBoolean(),
      TypeFactory.mkString(), TypeFactory.mkUnlimitedNatural(), TypeFactory.mkOclAny(),
      TypeFactory.mkVoidType(), TypeFactory.mkUReal(), TypeFactory.mkUInteger(),
      TypeFactory.mkUBoolean(), TypeFactory.mkUString(), TypeFactory.mkSBoolean(),
      TypeFactory.mkSet(TypeFactory.mkInteger()), TypeFactory.mkSet(TypeFactory.mkUReal()),
      TypeFactory.mkSequence(TypeFactory.mkInteger()), TypeFactory.mkBag(TypeFactory.mkUString()),
      TypeFactory.mkOrderedSet(TypeFactory.mkInteger()) };
    Map<String,String> A=winners(build(0)), B=winners(build(1)), C=winners(build(2));
    report("FORK-order (baseline)  vs  UNCERTAINTY-FIRST", A, B);
    report("FORK-order (baseline)  vs  UNCERTAINTY-LAST",  A, C);
  }
  static void report(String t, Map<String,String> a, Map<String,String> b){
    System.out.println("\n==== "+t+" ====");
    TreeSet<String> keys=new TreeSet<>(); keys.addAll(a.keySet()); keys.addAll(b.keySet());
    int n=0; for(String k:keys) if(!Objects.equals(a.get(k),b.get(k))){
      System.out.printf("%-52s  A=%-34s B=%s%n",k,a.get(k),b.get(k)); n++; }
    System.out.println("differing (name,signature) cells: "+n+" of "+a.size());
  }
  static Map<String,String> winners(ListMultimap<String,OpGeneric> map){
    Map<String,String> r=new LinkedHashMap<>();
    for(String n:new TreeSet<>(map.keySet()))
      for(Type t1:TS){ probe(map,r,n,new Type[]{t1});
        for(Type t2:TS){ probe(map,r,n,new Type[]{t1,t2});
          probe(map,r,n,new Type[]{t1,t2,TypeFactory.mkInteger()}); } }
    return r;
  }
  static void probe(ListMultimap<String,OpGeneric> map, Map<String,String> r, String n, Type[] sig){
    StringBuilder k=new StringBuilder(n).append("(");
    for(Type t:sig) k.append(tn(t)).append(","); k.append(")");
    for(OpGeneric op:map.get(n)){ Type t=null; try{t=op.matches(sig);}catch(Throwable e){}
      if(t!=null){ r.put(k.toString(), op.getClass().getSimpleName()+"->"+tn(t)); return; } }
    r.put(k.toString(), "NO-MATCH");
  }
}
```

### A.2 `PortedURealType.java` + `P5.java` (7.5.0 side; the hazard sweep)

```java
// src/org/tzi/use/uml/ocl/type/PortedURealType.java
package org.tzi.use.uml.ocl.type;
import java.util.*;
/** Faithful stand-in for the ported URealType: exactly the fork's URealType.java:12-38 overrides,
 *  re-expressed on 7.5.0's TypeImpl (7.5.0's Type has no isTypeOfUReal/isKindOfUReal to override). */
public class PortedURealType extends TypeImpl {
  @Override public boolean conformsTo(Type t){ return equals(t) || t.isTypeOfOclAny(); }
  @Override public boolean isKindOfNumber(VoidHandling h){ return true; }
  @Override public Set<? extends Type> allSupertypes(){
    Set<Type> s=new HashSet<>(); s.add(this); s.add(TypeFactory.mkOclAny()); return s; }
  @Override public StringBuilder toString(StringBuilder sb){ return sb.append("UReal"); }
  @Override public String shortName(){ return "UReal"; }
  @Override public boolean equals(Object o){ return o instanceof PortedURealType; }
  @Override public int hashCode(){ return 991; }
}
```

```java
// P5.java
import java.util.*;
import org.tzi.use.uml.ocl.type.*;
import org.tzi.use.uml.ocl.expr.ExpStdOp; import org.tzi.use.uml.ocl.expr.operations.OpGeneric;
public class P5 {
  public static void main(String[] a){
    Type u=new PortedURealType(), I=TypeFactory.mkInteger(), R=TypeFactory.mkReal();
    int n=0;
    for(String nm:new TreeSet<>(ExpStdOp.opmap.keySet()))
      for(OpGeneric op:ExpStdOp.opmap.get(nm))
        for(Type[] sig:new Type[][]{{u},{u,I},{I,u},{u,u},{u,R},{R,u},{u,I,I}}){
          Type t=null; try{t=op.matches(sig);}catch(Throwable e){}
          if(t!=null){ StringBuilder s=new StringBuilder();
            for(Type x:sig)s.append(x).append(",");
            System.out.printf("%-14s %-28s (%s) -> %s%n",nm,op.getClass().getSimpleName(),s,t); n++; } }
    System.out.println("total accepting cells = "+n);
  }
}
```

### A.3 Expression driver skeleton (both sides)

```java
import java.io.*;
import org.tzi.use.parser.ocl.OCLCompiler;
import org.tzi.use.uml.mm.ModelFactory; import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.ocl.expr.Expression;
import org.tzi.use.uml.ocl.value.VarBindings;   // NB: value, not expr — the fork moved it
public class Probe {
  public static void main(String[] a) throws Exception {
    MModel m = new ModelFactory().createModel("m");
    for (String e : new String[]{ /* expressions */ }) {
      StringWriter sw = new StringWriter(); Expression ex = null;
      try { ex = OCLCompiler.compileExpression(m, e, "probe", new PrintWriter(sw), new VarBindings()); }
      catch (Throwable t) { System.out.println(e+" THREW "+t); continue; }
      System.out.println(e + (ex==null ? " COMPILE-ERROR: "+sw.toString().trim()
                                       : " type="+ex.type()+"  rep="+ex));
    }
  }
}
```

### A.4 Registry diff

```sh
# dump each opmap as "name<TAB>ImplClass", one pair per line, then set-diff
#   (the fork dump comes from Probe2, the 7.5.0 dump from P — both print the map in order)
comm -23 map750.tsv mapfork.tsv    # -> 2 lines: pow/Op_number_pow, sqrt/Op_number_sqrt
comm -13 map750.tsv mapfork.tsv    # -> 109 lines (70 named + 39 anonymous SBoolean)
comm -12 map750.tsv mapfork.tsv    # -> 110 lines
```
