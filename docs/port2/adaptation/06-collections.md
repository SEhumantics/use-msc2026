# 06 — Collections and query expressions

**Area:** collection literals, collection standard operations, `ExpQuery` and its subclasses,
`ExpUSelect` / `ExpUSelectC`, and the `ExpressionVisitor` implementations they touch.
This is the home of the worked example.

**Governing policy applied throughout:**

> Uncertainty meaning comes from the fork. Everything else comes from USE 7.5.0.
> Where the two collide, keep the uncertainty behaviour but express it the 7.5.0 way.

Path aliases follow `specification.md`:

| Alias | Absolute path |
|---|---|
| `F/` | `/home/xoruser/msc-4/use-msc2026/.git/reference-repositories/uncertainty/USE-Uncertainty/src/main/org/tzi/use/` |
| `FT/` | `…/USE-Uncertainty/src/test/org/tzi/use/` |
| `T/` | `/home/xoruser/msc-4/use-msc2026/use-core/src/main/java/org/tzi/use/` |
| `L/` | `…/USE-Uncertainty/lib/` |

Confidence tags: **MEASURED** = produced by running one of the two implementations and pasted below;
**READ_FROM_SOURCE** = read out of a cited file; **INFERRED** = reasoned, and labelled as such.

---

## §0. Method — how every number below was obtained

Two executable oracles were used. Nothing in this document is reasoned where it could be run.

**Fork oracle** (the 2015 historical implementation, run from its shipped jars):

```bash
L=/home/xoruser/msc-4/use-msc2026/.git/reference-repositories/uncertainty/USE-Uncertainty/lib
CP="$L/use.jar:$L/atenearesearchgroup.uncertainty.jar:$L/antlr-3.4-complete.jar"
javac -cp "$CP" -d out Probe.java && java -cp "out:$CP" Probe
```

**Plain USE 7.5.0 oracle** (already-built classes; no Maven was run):

```bash
CP750="/home/xoruser/msc-4/use-msc2026/use-core/target/classes:\
/home/xoruser/.m2/repository/org/antlr/antlr-runtime/3.5.3/antlr-runtime-3.5.3.jar:\
/home/xoruser/.m2/repository/com/google/guava/guava/33.6.0-jre/guava-33.6.0-jre.jar:\
/home/xoruser/.m2/repository/jline/jline/2.14.6/jline-2.14.6.jar"
javac -cp "$CP750" -d . P750.java && java -cp ".:$CP750" P750
```

Both drivers call
`OCLCompiler.compileExpression(new ModelFactory().createModel("m"), expr, "probe", new PrintWriter(sw), new VarBindings())`
for the **static** type and `new Evaluator().eval(exp, new MSystem(model).state(), new VarBindings())`
for the **value**. Driver sources live in this session's scratchpad
(`…/scratchpad/coll/`), never in the repo.

> **Hygiene note.** An early run used `/tmp/probe`, which turned out to be shared with another
> concurrently-running session; one 7.5.0 run there returned another agent's expression list. Every
> 7.5.0 figure in this document was **re-run in an isolated per-session scratchpad directory** and is
> the output of the driver shown above. No contaminated output is cited.

---

## §1. The worked example, reproduced and extended — MEASURED

### 1.1 Fork — raw output

Verbatim from `java -cp "out:$CP" Probe` against `L/use.jar` + `L/atenearesearchgroup.uncertainty.jar`:

```
EXPR: Set{UReal(2,0.5), 1, 2.5}
  TYPE: Set(UReal)          TYPE-CLASS: org.tzi.use.uml.ocl.type.SetType
  EXP-CLASS: org.tzi.use.uml.ocl.expr.ExpSetLiteral
  VALUE: Set{1,2.5,UReal(2.0, 0.5)}     VALUE-TYPE: Set(UReal)

EXPR: Set{UReal(2,0.5), 1, 2.5}->sum()
  TYPE: UReal               VALUE: UReal(5.5, 0.5)        VALUE-TYPE: UReal

EXPR: Set{UReal(2,0.5), 1, 2.5}->max()
  TYPE: UReal               VALUE: UReal(2.5, 0.0)        VALUE-TYPE: UReal

EXPR: Set{UReal(2,0.5), 1, 2.5}->min()
  TYPE: UReal               VALUE: UReal(1.0, 0.0)        VALUE-TYPE: UReal

EXPR: Sequence{UReal(52,0.5), 3.2, 2}
  TYPE: Sequence(UReal)     VALUE: Sequence{UReal(52.0, 0.5),3.2,2}

EXPR: Bag{UInteger(1,0), 2}
  TYPE: Bag(UInteger)       VALUE: Bag{2,UInteger(1, 0.0)}

EXPR: Set{UBoolean(true,0.9), true}
  TYPE: Set(UBoolean)       VALUE: Set{UBoolean(true, 0.9),true}

EXPR: Set{UString('a',1), 'b'}
  TYPE: Set(UString)        VALUE: Set{'b',UString('a', 1.0)}

EXPR: Set{1, 2, UReal(2,5)}->forAll(e | e >= 1)
  TYPE: Boolean   [ExpForAll]
  VALUE: UBoolean(true, 0.579)          VALUE-TYPE: UBoolean

EXPR: Set{0, 1, UReal(3,0.5)}->exists(e | e = 0)
  TYPE: Boolean   [ExpExists]
  VALUE: UBoolean(true, 1.0)            VALUE-TYPE: UBoolean

EXPR: Set{Set{UReal(1,0)}, Set{1}}
  TYPE: Set(Set(UReal))     VALUE: Set{Set{UReal(1.0, 0.0)}}

EXPR: Set{1, 2.5}                       (control)
  TYPE: Set(Real)           VALUE: Set{1,2.5}

EXPR: Set{1, 2.5}->sum()                (control)
  TYPE: Real                VALUE: 3.5
```

### 1.2 Plain USE 7.5.0 — raw output, same expressions

```
Set{UReal(2,0.5), 1, 2.5}                     COMPILE-ERROR: probe:1:4: Undefined operation `UReal'.
Set{UReal(2,0.5), 1, 2.5}->sum()              COMPILE-ERROR: probe:1:4: Undefined operation `UReal'.
Set{UReal(2,0.5), 1, 2.5}->max()              COMPILE-ERROR: probe:1:4: Undefined operation `UReal'.
Set{UReal(2,0.5), 1, 2.5}->min()              COMPILE-ERROR: probe:1:4: Undefined operation `UReal'.
Sequence{UReal(52,0.5), 3.2, 2}               COMPILE-ERROR: probe:1:9: Undefined operation `UReal'.
Bag{UInteger(1,0), 2}                         COMPILE-ERROR: probe:1:4: Undefined operation `UInteger'.
Set{UBoolean(true,0.9), true}                 COMPILE-ERROR: probe:1:4: Undefined operation `UBoolean'.
Set{UString('a',1), 'b'}                      COMPILE-ERROR: probe:1:4: Undefined operation `UString'.
Set{1, 2, UReal(2,5)}->forAll(e | e >= 1)     COMPILE-ERROR: probe:1:10: Undefined operation `UReal'.
Set{0, 1, UReal(3,0.5)}->exists(e | e = 0)    COMPILE-ERROR: probe:1:10: Undefined operation `UReal'.
Set{Set{UReal(1,0)}, Set{1}}                  COMPILE-ERROR: probe:1:8: Undefined operation `UReal'.
Set{1, 2, UReal(2,5)}->uSelect(e | e >= 1)    COMPILE-ERROR: probe:line 1:33 mismatched input '|' expecting )
Set{1,2,3}->uSelect(e | e >= 1)               COMPILE-ERROR: probe:line 1:22 mismatched input '|' expecting )
Set{1,2,3}->uCount(1)                         COMPILE-ERROR: probe:1:12: Undefined operation named `uCount'
                                                             in expression `Set(Integer)->uCount(Integer)'.
Set{1, 2.5}                                   type=Set(Real)        value=Set{1,2.5} : Set(Real)
Set{1, 2.5}->sum()                            type=Real             value=3.5 : Real
Set{1,2.5}->max()                             type=Real             value=2.5 : Real
Set{1,2.5}->min()                             type=Real             value=1.0 : Real
Set{1, 2, 3}->forAll(e | e >= 1)              type=Boolean          value=true : Boolean
Set{0, 1, 3}->exists(e | e = 0)               type=Boolean          value=true : Boolean
Set{Set{1}, Set{2}}                           type=Set(Set(Integer)) value=Set{Set{1},Set{2}} : Set(Set(Integer))
```

### 1.3 The extended worked-example table

| # | expression | fork static type | fork value | plain 7.5.0 | conf. |
|---|---|---|---|---|---|
| 1 | `Set{UReal(2,0.5), 1, 2.5}` | `Set(UReal)` | `Set{1,2.5,UReal(2.0, 0.5)}` | compile error `Undefined operation 'UReal'` | MEASURED |
| 2 | `…->sum()` | `UReal` | `UReal(5.5, 0.5)` | compile error | MEASURED |
| 3 | `…->max()` | `UReal` | `UReal(2.5, 0.0)` | compile error | MEASURED |
| 4 | `…->min()` | `UReal` | `UReal(1.0, 0.0)` | compile error | MEASURED |
| 5 | `Sequence{UReal(52,0.5), 3.2, 2}` | `Sequence(UReal)` | `Sequence{UReal(52.0, 0.5),3.2,2}` | compile error | MEASURED |
| 6 | `Bag{UInteger(1,0), 2}` | `Bag(UInteger)` | `Bag{2,UInteger(1, 0.0)}` | compile error | MEASURED |
| 7 | `Set{UBoolean(true,0.9), true}` | `Set(UBoolean)` | `Set{UBoolean(true, 0.9),true}` | compile error | MEASURED |
| 8 | `Set{UString('a',1), 'b'}` | `Set(UString)` | `Set{'b',UString('a', 1.0)}` | compile error | MEASURED |
| 9 | `Set{1,2,UReal(2,5)}->forAll(e\|e>=1)` | **`Boolean`** | **`UBoolean(true, 0.579)`** | compile error | MEASURED |
| 10 | `Set{0,1,UReal(3,0.5)}->exists(e\|e=0)` | **`Boolean`** | **`UBoolean(true, 1.0)`** | compile error | MEASURED |
| 11 | `Set{Set{UReal(1,0)}, Set{1}}` | `Set(Set(UReal))` | **`Set{Set{UReal(1.0, 0.0)}}` — one element** | compile error | MEASURED |
| C1 | `Set{1, 2.5}` | `Set(Real)` | `Set{1,2.5}` | `Set(Real)` / `Set{1,2.5}` — **agree** | MEASURED |
| C2 | `Set{1, 2.5}->sum()` | `Real` / `3.5` | | `Real` / `3.5` — **agree** | MEASURED |

Rows 9, 10 and 11 are the three that carry consequences. They are developed in §1.4–§1.6.

### 1.4 Where `Set(UReal)` actually comes from — no collection code is involved

Rows 1, 5, 6, 7, 8 need **zero** edit in the collection layer. The literal's element type is the
least common supertype of the element types, and the fork's lattice makes that `UReal` on its own:

```
=== LATTICE ===  (fork, MEASURED)
Real.conformsTo(UReal)       = true
Integer.conformsTo(UReal)    = true
UReal.conformsTo(Real)       = false
Boolean.conformsTo(UBoolean) = true
UBoolean.conformsTo(Boolean) = false
```

This is the `TypeTest.java:138,153,156` lattice that **B5** already pays for. Rows 1/5/6/7/8 are
therefore *consequences of the type area*, not work items for this area. **MEASURED.**

Likewise rows 3 and 4 (`max`, `min`): §2.2 shows `Op_collection_max` / `Op_collection_min` are
**byte-identical** between the two trees. They return `UReal` purely because
`Op_collection_max.matches` returns `t.elemType()` after asking
`ExpStdOp.exists("max", {UReal, UReal})`. That is a *scalar* obligation, discharged by
`F/uml/ocl/expr/operations/StandardOperationsUReal.java`, not by this area.

### 1.5 Rows 9 and 10 — the fork's static type is UNSOUND

The fork's `exists`/`forAll` declare `Boolean` but evaluate to a `UBooleanValue`, and
`UBoolean.conformsTo(Boolean) = false`. The declared type does not admit the produced value.

Source of the declaration, `F/uml/ocl/expr/ExpForAll.java:43-47` (`ExpExists.java` identical):

```java
super(TypeFactory.mkBoolean(), elemVarDecls, rangeExp, queryExp);
// queryExp must be a kind of UBoolean expression
assertKindOfUBoolean();
```

The fork changed the *guard* (`assertBooleanQuery()` → `assertKindOfUBoolean()`) and the *evaluator*
(§3.2) but left `TypeFactory.mkBoolean()` untouched. **READ_FROM_SOURCE + MEASURED.**

Extra measurements pinning the shape of the defect:

```
Set{1, 2, UReal(2,5)}->forAll(e | e >= 3)   TYPE: Boolean   VALUE: UBoolean(true, 0.0)
Set{1, 2, UReal(2,5)}->exists(e | e >= 3)   TYPE: Boolean   VALUE: UBoolean(true, 0.421)
Set{UReal(2,5)}->forAll(e | e >= 1)         TYPE: Boolean   VALUE: UBoolean(true, 0.579)
Set{UReal(2,5)}->exists(e | e >= 1)         TYPE: Boolean   VALUE: UBoolean(true, 0.579)
```

and the **no-regression** control — a purely-Boolean predicate still yields a real `BooleanValue`,
so fixing the static type must not disturb these:

```
Set{1,2,3}->forAll(e | e >= 1)     static=Boolean  value=true   runtime=Boolean
Set{1,2,3}->exists(e | e = 1)      static=Boolean  value=true   runtime=Boolean
Set{1,2,3}->forAll(e | e >= 9)     static=Boolean  value=false  runtime=Boolean
Set{true,false}->forAll(e|e)       static=Boolean  value=false  runtime=Boolean
true and UBoolean(true,0.5)        static=UBoolean value=UBoolean(true, 0.5)  runtime=UBoolean
```

Note the last line: at the **`ExpStdOp`** level the fork *already* computes the correct static type
for a mixed `and`. Only `ExpForAll`/`ExpExists` hard-code `mkBoolean()`. The defect is local.

**Adaptation.** The port keeps the uncertainty behaviour (a `UBoolean` result when the predicate is
uncertain) but expresses it the 7.5.0 way: the result type is computed from the predicate, not
hard-coded.

```java
// ExpForAll / ExpExists ctor
super(queryExp.type().isTypeOfBoolean() ? TypeFactory.mkBoolean()
                                        : TypeFactory.mkUBoolean(),
      elemVarDecls, rangeExp, queryExp);
assertKindOfUBoolean();
```

Then rows 9/10 become static `UBoolean` with the fork's values unchanged, the purely-Boolean
controls stay static `Boolean`, and `value.type().conformsTo(expression.type())` holds. This is a
**B7 fix, not a reproduction** — see §5, defect **D-C3**.

### 1.6 Row 11 — the nested case is an order-dependent set collapse

`Set{Set{UReal(1,0)}, Set{1}}` loses an element. The reverse order does not:

```
Set{Set{UReal(1,0)}, Set{1}}  ->  Set{Set{UReal(1.0, 0.0)}}              (1 element)
Set{Set{1}, Set{UReal(1,0)}}  ->  Set{Set{1},Set{UReal(1.0, 0.0)}}       (2 elements)
Set{Set{UReal(1,0)}, Set{1}}->size()  ->  1
```

The same asymmetry at the top level, which is the more alarming form:

```
Set{UReal(1,0), 1}          ->  Set{1,UReal(1.0, 0.0)}     ->size() = 2
Set{1, UReal(1,0)}          ->  Set{1}                     ->size() = 1
Bag{UReal(1,0), 1}          ->  Bag{1,UReal(1.0, 0.0)}
Sequence{UReal(1,0), 1}     ->  Sequence{UReal(1.0, 0.0),1}
```

Root cause, measured directly on the values:

```
UReal(1,0).compareTo(1)  =  0        1.compareTo(UReal(1,0))  = -36
UReal(1,0).equals(1)     =  true     1.equals(UReal(1,0))     =  false
hash UReal(1,0) = 1072693248         hash 1 = 1072693248
```

and one level up:

```
A = Set{UReal(1,0)}   B = Set{1}
A.compareTo(B) = 0    B.compareTo(A) = -36
A.equals(B)    = false  B.equals(A)  = true
A.hashCode()   = B.hashCode() = 1072693248
```

`UReal(1,0)` and `1` hash identically, so they land in the same `HashSet` bucket; `equals` and
`compareTo` are then **asymmetric**, violating both the `Object.equals` symmetry contract and the
`Comparable` sign-reversal contract. `HashMap.putVal` compares `newKey.equals(existingKey)`, so
whichever element is written *second* decides — hence the literal's result depends on source order.
Note that `Set{UReal(1,0)} = Set{1}` evaluates to `false` at the OCL level, so the set literal is
also inconsistent with the language's own `=`. **MEASURED.**

**Adaptation.** This is squarely a **B7** latent defect and is FIXED, not reproduced. The fix does
not belong to this area — it belongs to the U-value `equals`/`hashCode`/`compareTo` contracts (values
area). What this area owes is (a) the measurement above, (b) the acceptance criterion:

> After the fix, `Set{UReal(1,0), 1}->size()` and `Set{1, UReal(1,0)}->size()` must agree, and must
> agree with `Set{UReal(1,0)} = Set{1}`.

Recorded as defect **D-C4** in §5. This document does **not** assert which of `1` or `2` the fixed
answer should be — that is the values area's call, and calling it here would be inventing a result.
Flagged in §6.

---

## §2. The minimal behavioural change in `CollectionValue` / `StandardOperationsCollection`

### 2.1 The headline: the earlier port's +959/−788 was almost entirely noise

`T/uml/ocl/expr/operations/StandardOperationsCollection.java` is **787 lines with CRLF line
terminators**; the fork's copy is 1002 lines with LF:

```
$ file <7.5.0 file> <fork file>
…/use-core/…/StandardOperationsCollection.java: ASCII text, with CRLF line terminators
…/USE-Uncertainty/…/StandardOperationsCollection.java: Java source, ASCII text
$ grep -c $'\r' <7.5.0>  -> 787      $ grep -c $'\r' <fork>  -> 0
```

A naive `diff -u` therefore reports **every line** as changed:

```
$ diff -u <7.5.0> <fork> | grep -c '^+'  -> 1003
$ diff -u <7.5.0> <fork> | grep -c '^-'  ->  788
```

`−788` against a 787-line file is a whole-file replacement. The earlier port's reported **+959/−788**
is that same shape: it replaced 7.5.0's file with the fork's, discarding ten years of upstream drift.

With line endings normalised the real delta is **an order of magnitude smaller**:

```
$ diff -u --strip-trailing-cr <7.5.0> <fork>
added: 212   removed: 47   hunks: 12
```

### 2.2 13 of the 18 collection operations are byte-identical

Per-class comparison after stripping CR and trailing whitespace (md5 of each class body):

```
Op_collection_size:         IDENTICAL (b8b16b18)
Op_collection_count:        IDENTICAL (093d99f0)
Op_collection_isEmpty:      IDENTICAL (f2f3837f)
Op_collection_notEmpty:     IDENTICAL (28179277)
Op_collection_product:      IDENTICAL (c26484c1)
Op_collection_flatten:      IDENTICAL (e1509e31)
Op_collection_asBag:        IDENTICAL (1300c99a)
Op_collection_asSet:        IDENTICAL (6c64fae5)
Op_collection_asSequence:   IDENTICAL (0780effd)
Op_collection_asOrderedSet: IDENTICAL (41f654d7)
Op_collection_max:          IDENTICAL (d28c9d9c)
Op_collection_min:          IDENTICAL (ea93b184)
Op_collection_single:       IDENTICAL (1be8d380)
```

Only **5 change** — `includes`, `excludes`, `includesAll`, `excludesAll`, `sum` — and **2 are new**:
`uCount`, `uCountC`. `max`/`min` returning `UReal` (rows 3/4) costs **nothing** here.

### 2.3 The minimal edit, hunk by hunk

Per-hunk counts of the CR-normalised diff, and what the minimal port takes from each:

| # | hunk | fork Δ | minimal port Δ | verdict |
|---|---|---|---|---|
| 1 | imports | +2 / −10 | **+5 / −0** | fork collapsed 10 explicit imports to two `.*` wildcards. **Reject** — keep 7.5.0's explicit imports, add only `UncertainType`, `UncertainValue`, `UBooleanValue`, `UIntegerValue`, `URealValue` |
| 2 | `registerOperation` block | +3 / −1 | **+3 / −0** | take (2 registrations + comment); the `−1` is whitespace churn |
| 3 | `Op_collection_includes.matches` | +6 / −3 | +6 / −3 | take — `UBoolean` when elem type or arg is uncertain |
| 4 | `Op_collection_includes.eval` | +8 / −2 | +8 / −2 | take — dispatch on `resultType`, call `coll.uIncludes` |
| 5 | `Op_collection_excludes.matches` | +7 / −2 | +7 / −2 | take |
| 6 | `Op_collection_excludes.eval` | +6 / −2 | +6 / −2 | take |
| 7 | **new** `Op_collection_uCount`, `Op_collection_uCountC` | +93 / −0 | +93 / −0 | take — purely additive |
| 8 | `Op_collection_includesAll.matches` | +8 / −2 | +8 / −2 | take |
| 9 | `Op_collection_includesAll.eval` | +8 / −2 | +8 / −2 | take |
| 10 | `Op_collection_excludesAll.matches` | +8 / −2 | +8 / −2 | take |
| 11 | `Op_collection_excludesAll.eval` | +6 / −2 | +6 / −2 | take |
| 12 | `Op_collection_sum` | +57 / −19 | **+30 / −1** | take the 2 new `matches` branches and 2 new `eval` arms + 2 helpers; **reject** the fork's extract-method refactor of the existing Integer/Real paths (that is the whole `−19`) |
| | **TOTAL** | **+212 / −47** | **+188 / −18** | |

**The minimal edit to `StandardOperationsCollection.java` is +188 / −18** — versus the earlier port's
**+959 / −788**. It is **80 %** additive, and it leaves 13 of 18 operations untouched. **MEASURED**
(hunk counts) + **INFERRED** (the split of hunk 12 into "semantic" vs "refactor" is my
classification, stated as such).

The two behaviour-carrying shapes, `READ_FROM_SOURCE` from
`F/uml/ocl/expr/operations/StandardOperationsCollection.java`:

```java
// matches(): lift the result type when the operands are uncertain     (fork lines 97-108)
Type elemType = params[1].getLeastCommonSupertype(coll.elemType());
if (elemType != null)
    if (!(elemType instanceof UncertainType || params[1] instanceof UncertainValue))
        return TypeFactory.mkBoolean();
    else
        return TypeFactory.mkUBoolean();

// eval(): dispatch on the already-computed result type                (fork lines 115-127)
if (resultType.isTypeOfBoolean()) { return BooleanValue.get(coll.includes(args[1])); }
else                              { return coll.uIncludes(args[1]); }

// sum(): two new matches branches                                     (fork lines 596-599)
else if (c.elemType().isTypeOfUInteger()) return TypeFactory.mkUInteger();
else if (c.elemType().isTypeOfUReal())    return TypeFactory.mkUReal();
```

**Note on `sum` and the empty collection.** `evalUIntegerResult` / `evalURealResult` seed the
accumulator with `new UIntegerValue(0, 0)` / `new URealValue(0, 0)`, so `Set{}->sum()` under a
`UReal` element type yields `UReal(0.0, 0.0)` rather than `Undefined`. This matches the plain
`Integer`/`Real` seeding (`isum = 0`, `rsum = 0.0`) and is therefore **consistent with 7.5.0's own
convention** — take it as-is, no adaptation needed. **READ_FROM_SOURCE.**

### 2.4 `CollectionValue` — purely additive, +99 / −0

The CR-normalised diff of `T/uml/ocl/value/CollectionValue.java` is `+85 / −3`, but the `−3` and part
of the `+` are `$Id$`/`@version` comment lines and an import restyle (the fork expands 7.5.0's
`import java.util.*` into eight explicit imports — **reject**, 7.5.0's form is the modern one).

The behaviour is a single hunk, `@@ -99,6 +109,105 @@` = **+99 lines, 0 removals, 0 modifications**:

| new method | signature | note |
|---|---|---|
| `uIncludes` | `UBooleanValue uIncludes(Value v)` | max over element-wise `uEquals`; early-exits at probability 1 |
| `uIncludesAll` | `UBooleanValue uIncludesAll(CollectionValue c2)` | `and`-folds `uIncludes` over `c2` |
| `uExcludes` | `UBooleanValue uExcludes(Value v)` | `and`-folds element-wise `uDistinct` |
| `uExcludesAll` | `UBooleanValue uExcludesAll(CollectionValue c2)` | `and`-folds `uExcludes` over `c2` |
| `uCountC` | `int uCountC(Value v, double confidence)` | counts elements whose `uEquals` probability ≥ threshold |

**No existing method of `CollectionValue` changes.** `compareTo`, `getRuntimeType`, `product`,
`getSortedElements`, `asBag`/`asSet`/`asOrderedSet`/`asSequence`, and every abstract declaration are
untouched. The subclasses `SetValue` / `BagValue` / `SequenceValue` / `OrderedSetValue` need **no**
edit at all: the five new methods are concrete on the base class and ride on `iterator()`/`size()`.

This is the strongest single statement this area can make about minimality: **the entire uncertain
collection semantics is 5 new methods and 0 modified ones.** **MEASURED + READ_FROM_SOURCE.**

Two defects in these five methods are carried to §5: `uIncludesAll`'s unguarded `size()` shortcut
(**D-C5**) and the independence assumption in the `and`-fold (**D-C6**).

---

## §3. `ExpQuery` / `ExpUSelect` / `ExpUSelectC`, and decision **B9**

### 3.1 What the fork added to `ExpQuery`

CR-normalised diff of `T/uml/ocl/expr/ExpQuery.java` (513 lines) vs `F/…/ExpQuery.java` (711 lines):
`+163 / −9` across 8 hunks. Classified:

| hunk | fork Δ | what | port |
|---|---|---|---|
| `@@ -17,20 +17,24 @@` | +4 / −1 | `$Id$` / `@version` header | **reject** — noise |
| `@@ -52,6 +56,12 @@` | +4 / −0 | field `protected Expression fUncertaintyExp` | take |
| `@@ -86,6 +96,21 @@` | +12 / −0 | 5-arg ctor; asserts `uncertaintyExp.type().isKindOfReal(EXCLUDE_VOID)` | take |
| `@@ -95,10 +120,17 @@` | +7 / −1 | `assertKindOfUBoolean()` | take |
| `@@ -144,12 +176,77 @@` | +46 / −0 | `evalUSelect()` + `private evalAndAsertConfident()` | take |
| `@@ -158,10 +255,112 @@` | +86 / −2 | `evalExists0()` / `evalForAll0()`, rewiring `evalExistsOrForAll` | take — **this is B9** |
| `@@ -456,12 +655,7 @@` | +1 / −5 | fork lacks 7.5.0's `isTypeOfOrderedSet()` branch in `sortedBy` | **reject** — upstream drift; applying it would *regress* 7.5.0 |
| `@@ -510,4 +704,8 @@` | +3 / −0 | `getUncertaintyExpression()` | take |

The `@@ -456 @@` hunk is worth naming explicitly, because it is the clearest case in this area of
the policy's second clause. 7.5.0 has:

```java
Type rangeElemType = ((CollectionType) fRangeExp.type()).elemType();
if (this.type().isTypeOfOrderedSet()) { return new OrderedSetValue(rangeElemType, result); }
else                                  { return new SequenceValue(rangeElemType, result); }
```

The fork, being older, has only the `SequenceValue` line. This is **not** uncertainty meaning, so
7.5.0 wins and the hunk is dropped.

`ExpUSelect` (49 lines) and `ExpUSelectC` (37 lines) are **new files**, no upstream counterpart:

```java
// F/uml/ocl/expr/ExpUSelect.java:20-30
public ExpUSelect(VarDecl elemVarDecl, Expression rangeExp, Expression queryExp)
        throws ExpInvalidException {
    super(rangeExp.type(),
          elemVarDecl != null ? new VarDeclList(elemVarDecl) : new VarDeclList(true),
          rangeExp, queryExp);
    assertKindOfUBoolean();
}
public Value eval(EvalContext ctx) { … evalUSelect(ctx) … }
public String name() { return "uSelect"; }
public void processWithVisitor(ExpressionVisitor v) { v.visitUSelect(this); }
```

`ExpUSelectC` is identical but takes a fourth `uncertaintyExp` argument and calls the 5-arg
`ExpQuery` ctor. Both return `rangeExp.type()` — the range type is preserved. Wiring is
`F/parser/base/ParserHelper.java:19-20,33-34,50-51` (`Q_USELECT_ID = 12`, `Q_USELECTC_ID = 13`;
7.5.0's `T/parser/base/ParserHelper.java:20-30` stops at `Q_CLOSURE_ID = 11`, so **no id collision**)
and `F/parser/ocl/ASTQueryExpression.java:129-130,152-153,178-183`. **READ_FROM_SOURCE.**

Measured behaviour of the two new operations:

```
Set{1, 2, UReal(2,5)}->uSelect(e | e >= 1)        TYPE: Set(UReal)     VALUE: Set{1,2,UReal(2.0, 5.0)}
Set{1, 2, UReal(2,5)}->uSelect(e | e >= 3)        TYPE: Set(UReal)     VALUE: Set{}
Set{1, 2, UReal(2,5)}->uSelectC(e | e >= 1, 0.9)  TYPE: Set(UReal)     VALUE: Set{1,2}
Set{1, 2, UReal(2,5)}->uSelectC(e | e >= 1, 0.1)  TYPE: Set(UReal)     VALUE: Set{1,2,UReal(2.0, 5.0)}
Set{1, 2, UReal(2,5)}->uSelectC(e | e >= 1, 1.5)  EVAL-THREW java.lang.RuntimeException:
                                                     Confident value must be between 0 and 1, found '1.5'
Set{1,2,3}->uSelect(e | e >= 1)                   TYPE: Set(Integer)   VALUE: Set{1,2,3}
Set{UBoolean(true,0.9), UBoolean(false,0.2)}->uSelect(e | e)
                                                  TYPE: Set(UBoolean)
                                                  VALUE: Set{UBoolean(true, 0.9),UBoolean(true, 0.8)}
```

Default threshold is `0.5` (`F/uml/ocl/expr/ExpQuery.java:225`); out-of-range throws
`RuntimeException` at **eval** time, not compile time. **MEASURED.**

`uSelect` accepts a *plain* `Boolean` predicate too (row 6 above), because
`assertKindOfUBoolean()` tests `isKindOfUBoolean` and `Boolean.conformsTo(UBoolean) = true`.

### 3.2 Why `select`/`reject`/`any`/`one` are asymmetric with `exists`/`forAll` — MEASURED

This is the fact that makes `ExpUSelect` exist at all:

```
Set{UReal(2,0.5), 1, 2.5}->select(e | e > 1)
  COMPILE-ERR: probe:1:27: Argument expression of `select' must have Boolean type, found `UBoolean'.
Set{1, 2, UReal(2,5)}->reject(e | e >= 1)
  COMPILE-ERR: probe:1:23: Argument expression of `reject' must have Boolean type, found `UBoolean'.
Set{1, 2, UReal(2,5)}->one(e | e >= 1)
  COMPILE-ERR: probe:1:23: Argument expression of `one' must have Boolean type, found `UBoolean'.
Set{1, 2, UReal(2,5)}->any(e | e >= 1)
  COMPILE-ERR: probe:1:23: Argument expression of `any' must have Boolean type, found `UBoolean'.

Set{1, 2, UReal(2,5)}->exists(e | e >= 1)   TYPE: Boolean   VALUE: UBoolean(true, 1.0)
Set{1, 2, UReal(2,5)}->forAll(e | e >= 1)   TYPE: Boolean   VALUE: UBoolean(true, 0.579)
```

The fork deliberately kept `assertBooleanQuery()` on `ExpSelect`/`ExpReject`/`ExpAny`/`ExpOne`
(`T/uml/ocl/expr/ExpSelect.java:45,60`, `ExpReject.java:45,56` — unchanged in the fork; the whole
`ExpSelect` diff is `+2 / −0` and both lines are `$Id$`/`@version` comments) and introduced
`uSelect`/`uSelectC` as *separate* operations instead. Only `ExpExists`/`ExpForAll` had their guard
swapped. **The port must preserve this asymmetry** — it is uncertainty design, not an accident.

### 3.3 What `exists` / `forAll` over an uncertain predicate must return

The fork replaced 7.5.0's short-circuiting boolean recursion with a value-level `or`/`and` fold.
`F/uml/ocl/expr/ExpQuery.java:266-310` (`evalExists0`; `evalForAll0` is the dual):

```java
Value res = BooleanValue.FALSE;
for (Value elemVal : rangeVal) {
    …
    Value queryVal = fQueryExp.eval(ctx);
    if (queryVal.isUndefined()) queryVal = BooleanValue.FALSE;
    try {
        expOr = ExpStdOp.create("or", new Expression[]{
                    new ExpressionWithValue(res), new ExpressionWithValue(queryVal)});
        res = expOr.eval(ctx);
    } catch (ExpInvalidException ex) { res = BooleanValue.FALSE; }
}
return res;
```

So the answer to "what must `exists`/`forAll` return over an uncertain predicate" is:
**the `or`- / `and`-fold of the per-element predicate values, evaluated through `ExpStdOp` so that
the Boolean/UBoolean mixed dispatch decides the result kind** — `UBoolean(p)` when any element
contributes uncertainty, plain `BooleanValue` when none does. Both branches are measured in §1.5.

The fold resolves through `F/uml/ocl/expr/operations/StandardOperationsUBoolean.java:356,427`
(`Op_uBoolean_and`, `Op_uBoolean_or`), whose `matches` is

```java
return params.length == 2 && params[0].isKindOfUBoolean(INCLUDE_VOID)
                          && params[1].isKindOfUBoolean(INCLUDE_VOID)
       ? TypeFactory.mkUBoolean() : null;
```

**Cross-area dependency, flagged:** for `(Boolean, Boolean)` **both** `Op_boolean_or`
(`T/…/StandardOperationsBoolean.java`, returns `params[0]`) and `Op_uBoolean_or` match. The measured
fork behaviour is that the plain operation wins (`Set{1,2,3}->forAll(...)` → `BooleanValue`), but
that is `opmap` iteration order, not a stated rule. The port must make this deterministic. Owned by
the scalars/ops area; **this area's acceptance criterion** is the five no-regression rows in §1.5.

### 3.4 **B9** — probed, and what the policy implies

B9 (`specification.md:197`) states that `ExpQuery` items 7+8 and the `ExpExists`/`ExpForAll`
assertion swap are one atomic unit: **take both or neither**, and forbids the "additive middle".

**Why the middle is fatal — verified in 7.5.0 source.** `T/uml/ocl/expr/ExpQuery.java` casts the
query value to `BooleanValue` at three points:

```
T/uml/ocl/expr/ExpQuery.java:136   if (((BooleanValue) queryVal).value() == doSelect)
T/uml/ocl/expr/ExpQuery.java:206   if (res != doExists && ((BooleanValue) queryVal).value() == doExists)
T/uml/ocl/expr/ExpQuery.java:208   else if (!ctx.isEnableEvalTree() && ((BooleanValue) queryVal).value() == doExists)
```

Lines 206/208 are inside `evalExistsOrForAll0`, which `evalExistsOrForAll` calls at
`T/uml/ocl/expr/ExpQuery.java:161`. Swapping `assertBooleanQuery()` → `assertKindOfUBoolean()` in
`ExpExists`/`ExpForAll` (`T/…/ExpExists.java:44`, `T/…/ExpForAll.java:44`) while keeping 7.5.0's
evaluator therefore admits a `UBooleanValue` into a `(BooleanValue)` cast → `ClassCastException` at
eval time. The spec's claim is **confirmed READ_FROM_SOURCE**.

**What the fork actually does — MEASURED.** It takes *both*: guard swapped **and** evaluator
replaced. `Set{1,2,UReal(2,5)}->forAll(e|e>=1)` returns `UBoolean(true, 0.579)`; it does not throw.
`assertKindOfUBoolean()` is called, from `ExpExists`, `ExpForAll`, `ExpUSelect` and `ExpUSelectC`.

**The policy implies option 1 — take both.** Three independent reasons:

1. `exists`/`forAll` over an uncertain predicate **is uncertainty meaning**, and the policy's first
   clause assigns uncertainty meaning to the fork unconditionally. Option 2 ("record `exists`/`forAll`
   as out of scope") deletes a behaviour the fork has, which the first clause does not permit.
2. The oracle role is decisive: `FT/uml/ocl/expr/ExpQueryUncertaintyTest.java:79-88` pins
   `assertEquals(exp.toString(), UBooleanValue.valueOf(true, 0.999968314), e.eval(exp, state))` for
   `testForAllColA`, and `:93-102` pins `UBooleanValue.TRUE` for `testExistsA`. Under option 2 both
   tests are unportable. Study A's agreement measure would have a hole exactly where the thesis's
   worked example lives.
3. Option 2 is not even self-consistent: `uSelect`/`uSelectC` are in scope under **B2**-style full
   porting and they already depend on `assertKindOfUBoolean()` and on `ExpQuery`'s 5-arg ctor
   (`F/uml/ocl/expr/ExpUSelect.java:29`, `ExpUSelectC.java:17`). Items 7+8 arrive with them
   regardless.

**Cost of option 1, stated honestly.** The fork's fold evaluates the predicate for **every** element;
7.5.0's `evalExistsOrForAll0` breaks out early (`T/…/ExpQuery.java:193, 211`) and has an
`isEnableEvalTree()` fast path. Taking both **loses short-circuiting** for `exists`/`forAll`. This is
a real, accepted regression in evaluation cost — and, for `exists`, a semantic change too: a
predicate that is undefined or divergent on a later element is now evaluated where 7.5.0 would have
stopped. It is recorded, not hidden.

**Adaptation — the 7.5.0 way.** Take both, plus three refinements the fork did not make:

* **Restore short-circuiting on the certain path.** Break out of the fold as soon as `res` is a
  plain `BooleanValue` equal to the absorbing element (`TRUE` for `exists`, `FALSE` for `forAll`).
  A `UBooleanValue` accumulator never short-circuits. This recovers 7.5.0's behaviour for every
  expression that has no uncertainty in it — i.e. all 79 corpus entries and the whole upstream suite
  — and confines the regression to genuinely uncertain predicates. **INFERRED**, and it must be
  validated against the five no-regression rows of §1.5 plus `ExpQueryTest`.
* **Delete the dead code.** The fork *keeps* `evalExistsOrForAll0` after replacing it, marked
  `@deprecated` (`F/uml/ocl/expr/ExpQuery.java:360-364`); grep shows its only remaining callers are
  its own recursive calls at `:382,388`. The port deletes 7.5.0's copy
  (`T/uml/ocl/expr/ExpQuery.java:165-221`, 57 lines) rather than carrying a dead duplicate. **B7.**
* **Fix the static type** (§1.5) so `value.type().conformsTo(expression.type())` holds.

Minimal `ExpQuery` edit under these decisions: **+158 / −60**
(+4 field, +12 ctor, +7/−1 guard, +46 `evalUSelect`, +86/−2 fold, +3 accessor, −57 dead method),
against the fork's own `+163 / −9`. Rejecting the `sortedBy` hunk and the header hunk is what
prevents the port from silently regressing 7.5.0.

### 3.5 One more fork defect in this area: `uSelectC` does not print its threshold

```
Set{1, 2, UReal(2,5)}->uSelectC(e | e >= 1, 0.9)
  toString = Set {1,2,UReal(2,5)}->uSelectC(e : UReal | (e >= 1))
```

The confidence argument is absent from the rendering, so `uSelectC(…, 0.9)` and `uSelectC(…, 0.1)` —
which **measurably differ** (`Set{1,2}` vs `Set{1,2,UReal(2.0, 5.0)}`) — print identically. Cause:
`F/uml/ocl/expr/ExpressionPrintVisitor.java:455-456` routes `visitUSelectC` to the generic
`visitQuery(exp)`, which knows nothing of `fUncertaintyExp`. Recorded as **D-C7**; the fix is a
dedicated `visitUSelectC` that appends `getUncertaintyExpression()`. **MEASURED + READ_FROM_SOURCE.**

---

## §4. `ExpressionVisitor` implementations in 7.5.0 that need new cases

`T/uml/ocl/expr/ExpressionVisitor.java` declares **49** `void visit…` methods today. This area adds
**exactly 2**: `visitUSelect(ExpUSelect)` and `visitUSelectC(ExpUSelectC)`
(`F/uml/ocl/expr/ExpressionVisitor.java:85-86`).

*(The fork's full interface diff is `+10 / −1`; the other 6 additions — `visitConstUBoolean`,
`visitConstSBoolean`, `visitDefSBoolean`, `visitConstUInteger`, `visitConstUReal`, `visitConstUString`
— belong to the literals/values areas, and the `−1` is upstream drift, `visitObjOp` → `visitInstanceOp`,
which must **not** be reverted.)*

### 4.1 Complete enumeration — every file, by search not by memory

```bash
$ grep -rn "implements ExpressionVisitor" --include=*.java . | grep -v reference-repositories
use-core/src/main/java/org/tzi/use/analysis/coverage/AbstractCoverageVisitor.java:33
use-core/src/main/java/org/tzi/use/uml/ocl/expr/ExpressionPrintVisitor.java:35
```

**Tier A — MUST gain both new methods or the module does not compile** (2 files + the interface):

| # | file | line | today | note |
|---|---|---|---|---|
| A0 | `use-core/src/main/java/org/tzi/use/uml/ocl/expr/ExpressionVisitor.java` | 49 methods | interface | declare the 2 new methods |
| A1 | `use-core/src/main/java/org/tzi/use/uml/ocl/expr/ExpressionPrintVisitor.java` | `:35` | 51 `visit` methods | fork routes both to `visitQuery` (`F/…:452-456`) — but see **D-C7**: `visitUSelectC` needs a real body, not `visitQuery` |
| A2 | `use-core/src/main/java/org/tzi/use/analysis/coverage/AbstractCoverageVisitor.java` | `:33` | 50 `visit` methods | fork routes both to `visitQuery` (`F/…:278-283`) — correct as-is |

**Tier B — inherit from Tier A, compile unchanged, but are in scope behaviourally** (6 files):

| # | file | relationship | needs an override? |
|---|---|---|---|
| B1 | `use-core/src/main/java/org/tzi/use/uml/ocl/expr/GenerateHTMLExpressionVisitor.java:30` | `extends ExpressionPrintVisitor` | **No** — declares 0 `visit` methods; inherits A1 |
| B2 | `use-core/src/main/java/org/tzi/use/uml/ocl/expr/EvalNode.java:618` (`RelevantOperationHighlightVisitor`) | `extends GenerateHTMLExpressionVisitor` | **No** — overrides only `visitNavigation`, `visitAttrOp` |
| B3 | `use-core/src/main/java/org/tzi/use/uml/ocl/expr/EvalNode.java:351` (`SubstituteVariablesExpressionVisitor`) | `extends RelevantOperationHighlightVisitor` | **Yes, behaviourally** — it overrides 33 `visit` methods **including `visitSelect` (`:558`), `visitReject` (`:546`), `visitExists` (`:474`), `visitForAll` (`:480`), `visitOne` (`:540`)**. Every other query form is handled; omitting `visitUSelect`/`visitUSelectC` silently falls through to the HTML printer and the evaluation browser loses variable substitution for uSelect nodes. **The one non-obvious entry in this table.** |
| B4 | `use-core/src/main/java/org/tzi/use/analysis/coverage/CoverageCalculationVisitor.java:38` | `extends AbstractCoverageVisitor` | **No** — overrides only `visitConstUnlimitedNatural` (`:156`) |
| B5 | `use-core/src/main/java/org/tzi/use/analysis/coverage/BasicExpressionCoverageCalulator.java:40` | `extends AbstractCoverageVisitor` | **No** — overrides only `visitObjectByUseId` (`:83`), `visitRange` (`:94`) |
| B6 | `use-gui/src/main/java/org/tzi/use/gui/views/evalbrowser/EvalNodeVarAssignment.java:37` and `use-gui/src/main/java/org/tzi/use/gui/viewsFX/evalbrowser/EvalNodeVarAssignment.java:37` | `extends EvalNode` | **No** — no `visit` overrides; inherit B3 |

**Not visitors** (they *use* an `ExpressionVisitor`, they do not implement one — checked, so they can
be dismissed rather than left ambiguous): `use-core/src/main/java/org/tzi/use/uml/mm/MMPrintVisitor.java:422`
and `use-gui/src/main/java/org/tzi/use/gui/util/MMHTMLPrintVisitor.java:47` /
`use-gui/src/main/java/org/tzi/use/gui/utilFX/MMHTMLPrintVisitor.java:47`, all of which merely have a
`createExpressionVisitor()` factory returning a `GenerateHTMLExpressionVisitor`.

**Summary: 3 files must change to compile; 1 more (B3) must change to be correct; 6 need no edit.**
The fork itself also has `F/analysis/metrics/{AbstractMetricVisitor,GSMetricVisitor}.java`
implementing the two methods — **7.5.0 has no `analysis/metrics` package at all**
(`ls use-core/src/main/java/org/tzi/use/analysis/` → `coverage` only), so those two files have no
port target and are correctly dropped.

---

## §5. Defects found in this area (feed to `b7-fix-plan.md`)

| id | where | what | evidence | disposition |
|---|---|---|---|---|
| **D-C3** | `F/uml/ocl/expr/ExpForAll.java:43`, `ExpExists.java:43` | static type hard-coded `Boolean` while the value is `UBooleanValue`; `UBoolean.conformsTo(Boolean) = false`, so the value does not conform to its own expression's type | MEASURED §1.5 | **FIX** — derive the type from the predicate (§1.5) |
| **D-C4** | U-value `equals`/`hashCode`/`compareTo` (values area), surfacing in every collection literal | asymmetric `equals` and `compareTo` with equal hash codes ⇒ **source-order-dependent** set literals: `Set{1,UReal(1,0)}->size() = 1` but `Set{UReal(1,0),1}->size() = 2` | MEASURED §1.6 | **FIX** in values area; this area supplies the acceptance criterion |
| **D-C5** | `F/uml/ocl/value/CollectionValue.java`, `uIncludesAll` | `if (coll2.size() > size()) result = FALSE;` returns without examining any element; `uExcludesAll` has no matching shortcut — asymmetric, and wrong for `Bag` where duplicates make size a non-criterion | READ_FROM_SOURCE §2.4 | **FIX** — drop the shortcut, or guard it to non-Bag receivers |
| **D-C6** | same file, `uIncludesAll` / `uExcludesAll` | `and`-folding per-element probabilities treats element memberships as **independent** events; not stated anywhere in the fork | READ_FROM_SOURCE §2.4 | **DOCUMENT** — a modelling choice, not a bug; must be written down, since it silently determines every `includesAll` probability |
| **D-C7** | `F/uml/ocl/expr/ExpressionPrintVisitor.java:455-456` | `uSelectC`'s confidence argument is dropped from `toString()`; two expressions with measurably different results print identically | MEASURED §3.5 | **FIX** — dedicated `visitUSelectC` printing `getUncertaintyExpression()` |
| **D-C8** | `F/uml/ocl/expr/ExpQuery.java:360-364` | `evalExistsOrForAll0` retained as `@deprecated` dead code after being replaced; only self-recursive callers (`:382,388`) | READ_FROM_SOURCE §3.4 | **FIX** — delete rather than carry (57 lines in `T/…:165-221`) |
| **D-C9** | `F/uml/ocl/expr/ExpQuery.java:224-240` (throw at `:236`) | out-of-range confidence throws a bare `RuntimeException` at **eval** time; `uSelectC(…, 1.5)` compiles cleanly and fails at run time | MEASURED §3.1 | **FIX** — reject a *constant* threshold at compile time via `ExpInvalidException`; keep the runtime check for computed thresholds |

---

## §6. Gaps and UNVERIFIABLE items

1. **The fixed value of `Set{1, UReal(1,0)}->size()` is not decided here.** D-C4's fix belongs to the
   values area; whether `UReal(1,0)` and `1` should be one element or two follows from whichever
   `equals` contract that area adopts. Asserting an answer here would be inventing a result.
2. **`Op_boolean_or` vs `Op_uBoolean_or` dispatch for `(Boolean, Boolean)` is order-dependent.**
   Both `matches`. The fork's observed outcome (plain wins) is `opmap` iteration order, not a rule.
   I did not find a tie-break rule in `OpGeneric`. **UNVERIFIABLE as a specified behaviour** — must
   be *made* deterministic by the ops area.
3. **The short-circuit refinement in §3.4 is INFERRED, not measured.** No implementation of it exists
   in either tree; it must be validated against `ExpQueryTest` and the five §1.5 no-regression rows.
4. **`UBoolean(true, 0.579)` and `UBoolean(true, 0.421)` were not derived.** They are reported as the
   fork emits them. The arithmetic behind `UReal(2,5) >= 1` lives in the scalars/`uDataTypes` area;
   whether it is *correct* is outside this area's remit.
5. **`->collect`, `->sortedBy`, `->isUnique`, `->closure`, `->iterate` over uncertain elements were
   spot-checked, not systematically covered.** Measured: `collect(e|e)` → `Bag(UReal)`,
   `sortedBy(e|e)` → `Sequence(UReal)` with value `Sequence{1,2,UReal(2.0, 5.0)}`, `isUnique(e|e)`
   → `Boolean`/`true`. `closure` and `iterate` were **not probed** — gap.
6. **`->sortedBy` over uncertain elements depends on the same broken `compareTo` as D-C4.**
   `Sequence{1,2,UReal(2.0, 5.0)}` is a *measured* output, but with an asymmetric comparator the sort
   is not well-defined and Java's `TimSort` may throw `"Comparison method violates its general
   contract!"` on larger inputs. **Not reproduced** — flagged as a risk to test after D-C4 is fixed.
7. **No Maven was run** (ground rule 3). All 7.5.0 figures come from `use-core/target/classes` as it
   stood; if that tree is stale relative to `HEAD`, the 7.5.0 column would need re-measuring.
