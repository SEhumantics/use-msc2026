# 01 — Adaptation: the type system and the lattice

**Area:** `org.tzi.use.uml.ocl.type` + `org.tzi.use.uml.mm.MClassifierImpl` (the second `Type` root).

**Governing policy (user's, not open for debate — this document only applies it):**

> Uncertainty meaning comes from the fork. Everything else comes from USE 7.5.0.
> Where the two collide, keep the uncertainty behaviour but express it the 7.5.0 way.

---

## 0. Evidence rules and provenance

| tag | meaning |
|---|---|
| **MEASURED** | produced by executing code in this session. The command and its real stdout are pasted. |
| **READ_FROM_SOURCE** | read out of a named file at named lines. |
| **INFERRED** | reasoned, not executed. Flagged as such everywhere it occurs. |

Two *independent* oracles were used for every lattice claim, and they agree cell-for-cell:

| oracle | what it is |
|---|---|
| **JAR** | the historical 2021 binary `.git/reference-repositories/uncertainty/USE-Uncertainty/lib/use.jar` (+ `atenearesearchgroup.uncertainty.jar`), driven by `/tmp/forkprobe/TProbe.java`. This is the fork *as it actually ran*. |
| **SRC** | the fork's `.java` sources compiled verbatim by the pre-existing `docs/port2/spec-parts/11-types-oracle.sh`. |

```
$ diff <(grep -E '^(CONF|LCS) ' /tmp/forkprobe/jar.txt | sort) \
       <(grep -E '^(CONF|LCS) ' /tmp/typeoracle/fork.txt | sort)
IDENTICAL: jar-probe (2021 binary) == source-compiled oracle, all 288 cells
```

The 7.5.0 baseline is `use-core/target/classes` at HEAD `54e2745b` on `port-uncertainty-2`.
That tree is **plain 7.5.0** — the port does not exist yet:

```
$ ls use-core/target/classes/org/tzi/use/uml/ocl/type/ | grep -iE 'ureal|uinteger|uboolean|ustring|sboolean|uncertain'
NONE (target/classes is plain 7.5.0)
```

Maven was not run. No file outside `docs/port2/adaptation/` was written.

---

## 1. The conformance relation the port must reproduce (MEASURED)

`A.conformsTo(B)` — row `A`, column `B`. `T` = true, `.` = false.
All 144 cells, both oracles agreeing.

| A \ B | UBoolean | UInteger | UReal | UString | SBoolean | Boolean | Integer | Real | String | UnlimitedNatural | OclVoid | OclAny |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| **UBoolean** | T | . | . | . | T | . | . | . | . | . | . | T |
| **UInteger** | . | T | T | . | . | . | . | . | . | . | . | T |
| **UReal** | . | . | T | . | . | . | . | . | . | . | . | T |
| **UString** | . | . | . | T | . | . | . | . | . | . | . | T |
| **SBoolean** | . | . | . | . | T | . | . | . | . | . | . | T |
| **Boolean** | T | . | . | . | T | T | . | . | . | . | . | T |
| **Integer** | . | T | T | . | . | . | T | T | . | T | . | T |
| **Real** | . | . | T | . | . | . | . | T | . | . | . | T |
| **String** | . | . | . | T | . | . | . | . | T | . | . | T |
| **UnlimitedNatural** | . | T | T | . | . | . | T | T | . | T | . | T |
| **OclVoid** | T | T | T | T | T | T | T | T | T | T | T | T |
| **OclAny** | . | . | . | . | . | . | . | . | . | . | . | T |

`# conformsTo true = 47 of 144` (identical from JAR and SRC).

Restricted to the 7 classical types the fork's `conformsTo` block and its `getLeastCommonSupertype`
block are byte-identical to 7.5.0's — the oracle script asserts it and prints
`IDENTICAL — the uncertainty extension changes no classic-type cell`. **That guarantee holds
pairwise only. §5 C-08 shows a classic-only *expression* whose answer the lattice does change.**

### 1.1 The eleven deciding bodies (READ_FROM_SOURCE, fork `src/main/org/tzi/use/uml/ocl/type/`)

```java
UBooleanType.java:43   return other.equals(this) || other.isTypeOfOclAny() || other.isTypeOfSBoolean();
UIntegerType.java:48   return other.isTypeOfUInteger() || other.isTypeOfUReal() || other.isTypeOfOclAny();
URealType.java:13      return equals(t) || t.isTypeOfOclAny();
UStringType.java:31    return equals(other) || other.isTypeOfOclAny();
SBooleanType.java:32   return other.isTypeOfSBoolean() || other.isTypeOfOclAny();
BooleanType.java:63    return this.equals(other) || other.isTypeOfUBoolean() || other.isTypeOfOclAny() || other.isTypeOfSBoolean();
IntegerType.java:71    return !t.isTypeOfVoidType() && (t.isKindOfNumber(VoidHandling.EXCLUDE_VOID) || t.isTypeOfOclAny());
RealType.java:62       return equals(t) || t.isTypeOfOclAny() || t.isTypeOfUReal();
StringType.java:57     return equals(t) || t.isTypeOfOclAny() || t.isTypeOfUString();
VoidType.java:23       return true;
OclAnyType.java:77     return other.isTypeOfOclAny();
```

7.5.0's counterparts (READ_FROM_SOURCE, `use-core/src/main/java/org/tzi/use/uml/ocl/type/`):

```java
BooleanType.java:50    return this.equals(other) || other.isTypeOfOclAny();
IntegerType.java:59    return !t.isTypeOfVoidType() && (t.isKindOfNumber(VoidHandling.EXCLUDE_VOID) || t.isTypeOfOclAny());   // ← byte-identical to the fork
RealType.java:54       return equals(t) || t.isTypeOfOclAny();
StringType.java:49     return equals(t) || t.isTypeOfOclAny();
```

**`IntegerType.conformsTo` is byte-identical in both trees.** `Integer ≤ UInteger` and
`Integer ≤ UReal` arise *entirely* from `UIntegerType.isKindOfNumber()` / `URealType.isKindOfNumber()`
returning `true`. A reviewer diffing `IntegerType.conformsTo` sees nothing and must not "fix" it.
See C-04.

### 1.2 The predicate battery that carries the relation (MEASURED, `/tmp/forkprobe/PProbe.java`)

`EXCLUDE_VOID`:

```
predicate           UBoolean  UInteger  UReal  UString  SBoolean  Boolean  Integer  Real  String  UnlimNat  OclVoid  OclAny
isKindOfNumber      .         T         T      .        .         .        T        T     .       T         .        .
isTypeOfUInteger    .         T         .      .        .         .        .        .     .       .         .        .
isKindOfUInteger    .         T         .      .        .         .        T        .     .       .         .        .
isTypeOfUReal       .         .         T      .        .         .        .        .     .       .         .        .
isKindOfUReal       .         T         T      .        .         .        T        T     .       .         .        .
isTypeOfUString     .         .         .      T        .         .        .        .     .       .         .        .
isKindOfUString     .         .         .      T        .         .        .        .     T       .         .        .
isTypeOfUBoolean    T         .         .      .        .         .        .        .     .       .         .        .
isKindOfUBoolean    T         .         .      .        .         T        .        .     .       .         .        .
isTypeOfSBoolean    .         .         .      .        T         .        .        .     .       .         .        .
isKindOfSBoolean    T         .         .      .        T         T        .        .     .       .         .        .
isKindOfOclAny      T         T         T      T        T         T        T        T     T       T         .        T
```

Under `INCLUDE_VOID` the only change is the `OclVoid` column: it answers `T` to
`isKindOfNumber`, `isKindOfUInteger`, `isKindOfUReal`, `isKindOfUString`, `isKindOfUBoolean`,
`isKindOfSBoolean`, `isKindOfOclAny` — i.e. the five new `VoidType` overrides are each
`return h == VoidHandling.INCLUDE_VOID;`.

Note the two cells that are **inconsistent with the conformance matrix**: `UnlimitedNatural`
answers `.` to `isKindOfUInteger` and `isKindOfUReal` although `UnlimitedNatural.conformsTo(UInteger)`
and `…(UReal)` are both `T`. That is B11 (C-15).

---

## 2. `allSupertypes()` — the thing that actually drives element types (MEASURED)

```
===== FORK (USE-Uncertainty) =====        ===== 7.5.0 BASELINE =====
SUP UBoolean [OclAny, SBoolean, UBoolean]
SUP UInteger [OclAny, UInteger, UReal]
SUP UReal    [OclAny, UReal]
SUP UString  [OclAny, UString]
SUP SBoolean [OclAny, SBoolean]
SUP Boolean  [Boolean, OclAny, SBoolean, UBoolean]      SUP Boolean  [Boolean, OclAny]
SUP Integer  [Integer, OclAny, Real, UInteger, UReal]   SUP Integer  [Integer, OclAny, Real]
SUP Real     [OclAny, Real, UReal]                      SUP Real     [OclAny, Real]
SUP String   [OclAny, String, UString]                  SUP String   [OclAny, String]
SUP UnlimitedNatural [Integer, OclAny, Real, UnlimitedNatural]   ← unchanged by the fork
SUP OclVoid  THROWS UnsupportedOperationException       SUP OclVoid  THROWS UnsupportedOperationException
SUP OclAny   [OclAny]                                   SUP OclAny   [OclAny]
```

Four upstream files gain entries: `BooleanType` (+2), `IntegerType` (+2), `RealType` (+1),
`StringType` (+1). `UnlimitedNaturalType` and `OclAnyType` gain none — deliberately or not, that is
what the fork does, and it is the whole of B11.

Collection lifting is **free**: `CollectionType`/`SetType`/`SequenceType`/`BagType`/`OrderedSetType`
map `allSupertypes` over the element type and are unedited by the fork. Measured growth
(`/tmp/tupprobe/TupProbe.java`):

| type | fork | 7.5.0 |
|---|---|---|
| `Integer` | 5 | 3 |
| `Set(Integer)` | 10 | 6 |
| `Set(Set(Integer))` | 20 | 12 |
| `Set(Set(Set(Integer)))` | 40 | 24 |

---

## 3. Least common supertype (MEASURED)

### 3.1 Pairwise `Type.getLeastCommonSupertype` — the full 144-cell block

| A \ B | UBoolean | UInteger | UReal | UString | SBoolean | Boolean | Integer | Real | String | UnlimNat | OclVoid | OclAny |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| **UBoolean** | UBoolean | OclAny | OclAny | OclAny | SBoolean | UBoolean | OclAny | OclAny | OclAny | OclAny | UBoolean | OclAny |
| **UInteger** | OclAny | UInteger | UReal | OclAny | OclAny | OclAny | UInteger | UReal | OclAny | OclAny | UInteger | OclAny |
| **UReal** | OclAny | UReal | UReal | OclAny | OclAny | OclAny | UReal | UReal | OclAny | OclAny | UReal | OclAny |
| **UString** | OclAny | OclAny | OclAny | UString | OclAny | OclAny | OclAny | OclAny | UString | OclAny | UString | OclAny |
| **SBoolean** | SBoolean | OclAny | OclAny | OclAny | SBoolean | SBoolean | OclAny | OclAny | OclAny | OclAny | SBoolean | OclAny |
| **Boolean** | UBoolean | OclAny | OclAny | OclAny | SBoolean | Boolean | OclAny | OclAny | OclAny | OclAny | Boolean | OclAny |
| **Integer** | OclAny | UInteger | UReal | OclAny | OclAny | OclAny | Integer | Real | OclAny | Integer | Integer | OclAny |
| **Real** | OclAny | UReal | UReal | OclAny | OclAny | OclAny | Real | Real | OclAny | Real | Real | OclAny |
| **String** | OclAny | OclAny | OclAny | UString | OclAny | OclAny | OclAny | OclAny | String | OclAny | String | OclAny |
| **UnlimNat** | OclAny | **OclAny** | **OclAny** | OclAny | OclAny | OclAny | Integer | Real | OclAny | UnlimNat | UnlimNat | OclAny |
| **OclVoid** | UBoolean | UInteger | UReal | UString | SBoolean | Boolean | Integer | Real | String | UnlimNat | OclVoid | OclAny |
| **OclAny** | OclAny | OclAny | OclAny | OclAny | OclAny | OclAny | OclAny | OclAny | OclAny | OclAny | OclAny | OclAny |

**The five pairs that make the worked example work** (bold = what the port must answer):

| pair | fork LCS |
|---|---|
| `LCS(Real, UReal)` | **UReal** |
| `LCS(Integer, UReal)` | **UReal** |
| `LCS(Integer, UInteger)` | **UInteger** |
| `LCS(Boolean, UBoolean)` | **UBoolean** |
| `LCS(String, UString)` | **UString** |

The two bold `OclAny` cells in the `UnlimitedNatural` row are B11 (C-15).

### 3.2 `UniqueLeastCommonSupertypeDeterminator.calculateFor` — the *other* LCS (MEASURED)

This is a **different code path** and it does not always agree with §3.1.

```
ULCS [Boolean, UBoolean] -> UBoolean          ULCS [String, UString] -> UString
ULCS [Boolean, SBoolean] -> SBoolean          ULCS [Integer, Real, UReal] -> UReal
ULCS [UBoolean, SBoolean] -> SBoolean         ULCS [UReal, Integer, Real] -> UReal
ULCS [Integer, UInteger] -> UInteger          ULCS [Boolean, Integer] -> OclAny
ULCS [Integer, UReal] -> UReal                ULCS [UnlimitedNatural, UInteger] -> UInteger   ← §3.1 says OclAny
ULCS [Real, UReal] -> UReal                   ULCS [UnlimitedNatural, UReal] -> UReal         ← §3.1 says OclAny
ULCS [UInteger, UReal] -> UReal               ULCS [Integer, Real, UInteger] -> UReal
                                              ULCS [OclVoid, UReal] -> UReal
```

Which path is used where (READ_FROM_SOURCE, `grep -rn getLeastCommonSupertype use-core/src/main`):

| path | callers |
|---|---|
| `UniqueLeastCommonSupertypeDeterminator` | `ExpCollectionLiteral.java:75` (**every collection literal**), `CollectionValue.java:60` (runtime element type) |
| pairwise `Type.getLeastCommonSupertype` | `ExpIf.java:42,48`, and ~50 sites across `StandardOperationsSet/Bag/Sequence/OrderedSet/Collection/Any` |

The disagreement is observable end-to-end (MEASURED, fork jars, §4).

---

## 4. End-to-end compiler evidence (MEASURED)

Driver: `/tmp/cprobe/CProbe.java`, `OCLCompiler.compileExpression(new ModelFactory().createModel("m"), e, "probe", …)`, `.type()` printed.

### 4.1 The worked example, both implementations

```
=== FORK (2021 jars) ===
Set{UReal(2,0.5), 1, 2.5}                  => TYPE Set(UReal)
Set{UReal(2,0.5), 1, 2.5}->sum()           => TYPE UReal
Set{1, 2.5}                                => TYPE Set(Real)

=== PLAIN USE 7.5.0 (use-core/target/classes) ===
Set{UReal(2,0.5), 1, 2.5}  =>  COMPILE-ERROR probe:1:4: Undefined operation `UReal'.
Set{1, 2.5}                =>  TYPE Set(Real)
```

Reproduces the brief's table exactly. The port must answer `Set(UReal)` / `UReal` / `Set(Real)`.

### 4.2 The rest of the lifting behaviour the port must reproduce

```
Set{UInteger(1,1), 2}                              => TYPE Set(UInteger)
Set{UInteger(1,1), UReal(2,0.5)}                   => TYPE Set(UReal)
Sequence{UReal(2,0.5), 1}                          => TYPE Sequence(UReal)
Bag{UInteger(1,1), 1, 2.5}                         => TYPE Bag(UReal)
Set{UBoolean(true,0.1), true}                      => TYPE Set(UBoolean)
Set{SBoolean(1.0,0.0,0.0,0.0), true}               => TYPE Set(SBoolean)
Set{UBoolean(true,0.1), SBoolean(1.0,0.0,0.0,0.0)} => TYPE Set(SBoolean)
Set{UString('b',1), 'a'}                           => TYPE Set(UString)
Set{UReal(2,0.5), true}                            => TYPE Set(OclAny)
Set{UBoolean(true,0.1), UString('b',1)}            => TYPE Set(OclAny)
Set{UString('b',1), UReal(2,0.5)}                  => TYPE Set(OclAny)
Set{Set{UReal(2,0.5)}, Set{1}}                     => TYPE Set(Set(UReal))
Set{Set{1}, Set{2.5}}                              => TYPE Set(Set(Real))
if true then 1 else UReal(2,0.5) endif             => TYPE UReal
if true then UInteger(1,1) else 2.5 endif          => TYPE UReal
if true then true else UBoolean(true,0.1) endif    => TYPE UBoolean
Set{1,2}->including(UReal(2,0.5))                  => TYPE Set(UReal)
Sequence{1,2}->append(UReal(2,0.5))                => TYPE Sequence(UReal)
Set{}->union(Set{UReal(2,0.5)})                    => TYPE Set(UReal)
Set{UReal(2,0.5), 1, 2.5}->asSequence()            => TYPE Sequence(UReal)
Set{UReal(2,0.5), 1, Undefined}                    => TYPE Set(UReal)
Set{UReal(2,0.5), oclUndefined(Integer)}           => TYPE Set(UReal)
Set{UReal(2,0.5),1}->sum()                         => TYPE UReal
Set{UInteger(1,1),1}->sum()                        => TYPE UInteger
Set{true, UBoolean(true,0.1)}->size()              => TYPE Integer
```

Literal arities, measured, because they are not obvious:
`UReal(Real,Real)`, `UInteger(Integer,Integer)`, `UBoolean(Boolean,Real)`,
`UString(String,Integer)`, `SBoolean(Real,Real,Real,Real)`.

### 4.3 The B11 divergence, observable (MEASURED, fork jars)

```
Set{*, UInteger(1,1)}                              => TYPE Set(UInteger)     ← ULCSD path
Set{*, UReal(2,0.5)}                               => TYPE Set(UReal)        ← ULCSD path
Set{oclUndefined(UnlimitedNatural), UInteger(1,1)} => TYPE Set(UInteger)     ← ULCSD path
if true then * else UInteger(1,1) endif            => TYPE OclAny            ← pairwise path
Sequence{*}->append(UInteger(1,1))                 => TYPE Sequence(OclAny)  ← pairwise path
    (+ "Warning: Operation call `Sequence(UnlimitedNatural)->append(UInteger)' results in type `Sequence(OclAny)'.")
```

Same two types, two different answers, depending only on which LCS routine the caller happens to use.

---

## 5. The collisions

Full table is in the structured output. Prose for the ones that need it:

### C-01 — the assumed "`Type` class vs interface" collision does not exist
`grep -n "public interface Type"` gives fork `Type.java:33` and 7.5.0 `Type.java:31`; both are
`public interface Type extends BufferedToString`, both have `public abstract class TypeImpl implements Type`
(fork `:34`, 7.5.0 `:32`), both have `abstract class BasicType extends TypeImpl`. The fork's base is a
2015 tree (`// $Id: Type.java 5494 2015-02-05 …`) that already had the split.
**Adaptation: none. `UncertainType extends BasicType` compiles unchanged.** Any port plan budgeting
for an interface/impl migration here is budgeting for nothing.

### C-02 — 10 new interface methods, TWO implementation roots, SIX classifier leaves
Adding `isTypeOfUInteger`, `isKindOfUInteger(VoidHandling)`, `isTypeOfUReal`, `isKindOfUReal`,
`isTypeOfUString`, `isKindOfUString`, `isTypeOfUBoolean`, `isKindOfUBoolean`, `isTypeOfSBoolean`,
`isKindOfSBoolean` to `Type` breaks the build unless every root implements them.

```
$ grep -rn "public boolean isTypeOfOclAny()" --include=*.java use-core/src/main use-gui/src/main
use-core/.../uml/mm/MClassifierImpl.java:355
use-core/.../uml/ocl/type/TypeImpl.java:313
use-core/.../uml/ocl/type/OclAnyType.java:33
```

Exactly two roots: `TypeImpl` and `MClassifierImpl`. `use-gui` has none of its own.
But 7.5.0 has **six** `MClassifierImpl` subclasses where the fork had two:

```
$ grep -rn "extends MClassifierImpl" --include=*.java use-core/src/main use-gui/src/main
MClassImpl.java:42   MAssociationImpl.java:42   MAssociationClassImpl.java:43
MSignalImpl.java:42  MDataTypeImpl.java:32      EnumType.java:39
```

`EnumType` and `MDataTypeImpl` are the surprises — `EnumType extends MClassifierImpl`, **not**
`TypeImpl`, so enums pick up their uncertainty no-ops from `MClassifierImpl`, and `MDataTypeImpl`
did not exist in the fork at all. **Adaptation:** put the ten `return false;` no-ops on
`TypeImpl` and `MClassifierImpl` only, exactly as the fork did (fork `MClassifierImpl.java:201,206,221,226,261,266,271,276,401,406` — all `return false;`), and all six leaves inherit them.
That is the 7.5.0 way of expressing the fork's two-root patch.

### C-03 — 7.5.0-only members that a file-level copy would silently delete
`diff -u --strip-trailing-cr` of `Type.java` shows 7.5.0 adds, and the fork lacks:
`String qualifiedName();` (`Type.java:48`, defaulted `TypeImpl.java:44` → `return toString();`,
overridden `MClassifierImpl.java:390`) and `isKindOfDataType(VoidHandling)` / `isTypeOfDataType()`
(`Type.java:136-138`). `MClassifierImpl` is 588 lines in 7.5.0 vs 511 in the fork; the extra 77 lines
are the attribute/operation tables pulled up from `MClassImpl`, plus `isSubClassifierOf`,
`hasStateMachineWhichHandles`, `isQualifiedAccess`.
**Adaptation: interleave, never copy a file. File-level copying from the fork is forbidden for every
file marked "edit".**

### C-04 — `conformsTo` dispatch: an edge can be installed from either side
No double dispatch, no visitor, no table — plain virtual dispatch on the receiver
(`Type.java:58` in 7.5.0, `:54` in the fork). So `A.conformsTo(B)` reads only `A`'s body plus `B`'s
`isTypeOf*`/`isKindOf*` answers, and a new edge can come from either end:

* **subtype side** — `BooleanType.conformsTo` naming `other.isTypeOfUBoolean()` (visible in a diff);
* **supertype side** — `URealType.isKindOfNumber() → true`, which makes the *unmodified*
  `IntegerType.conformsTo` accept `UReal` (invisible in a diff of `IntegerType`).

The fork uses both. `Integer ≤ UInteger` and `Integer ≤ UReal` are entirely supertype-side.
**Adaptation:** replicate both idioms and put a javadoc note on `IntegerType.conformsTo` saying it is
deliberately unchanged, or a later reviewer will "restore symmetry" and silently widen the lattice.

### C-05 — generics: two different `allSupertypes` signatures, on purpose
7.5.0 declares `Set<? extends Type> allSupertypes();` on the interface (`Type.java:63`) and narrows to
`Set<Type>` on every concrete upstream type (`BooleanType.java:58`, `IntegerType.java:67`,
`RealType.java:61`, `StringType.java:56`, `UnlimitedNaturalType.java:67`, `OclAnyType.java:45`,
`VoidType.java:8`, `CollectionType.java:79`, `MClassifierImpl.java:133`). The fork's five new types
all declare the *wide* `Set<? extends Type>` (`UBooleanType.java:33`, `UIntegerType.java:39`,
`URealType.java:33`, `UStringType.java:23`, `SBooleanType.java:23`).
**Adaptation:** keep `Set<Type>` on the edited upstream files — narrowing them would break callers
that assign to `Set<Type>` — and use `Set<? extends Type>` on the five new ones, exactly as the fork
does. `Type` is not generic in either tree, so there is nothing else to reconcile.

### C-06 — `VoidHandling`
Identical enum in both (`INCLUDE_VOID`, `EXCLUDE_VOID`; fork `Type.java:35`, 7.5.0 `Type.java:33`).
The five new `VoidType` overrides are each `return h == VoidHandling.INCLUDE_VOID;` — confirmed by the
`INCLUDE_VOID` half of the §1.2 battery. `VoidType.conformsTo` stays `return true` and
`VoidType.allSupertypes()` keeps throwing `UnsupportedOperationException`.
**Adaptation: none beyond adding the five overrides. Do not touch 7.5.0's `isKindOfDataType`
(`VoidType.java:93-95`), which the fork does not have.**

### C-07 — `UniqueLeastCommonSupertypeDeterminator` is byte-identical but is the load-bearing path
```
$ diff -u --strip-trailing-cr <fork>/…/UniqueLeastCommonSupertypeDeterminator.java use-core/…/UniqueLeastCommonSupertypeDeterminator.java
-// $Id$          ← the only hunk
```
It calls only `isVoidOrElementTypeIsVoid()`, `allSupertypes()` and `conformsTo()`, so it acquires the
uncertainty behaviour for free. **Adaptation: do not edit it.** But know that it — not pairwise LCS —
is what `ExpCollectionLiteral` uses, so it is the routine that decides the worked example, and it
disagrees with pairwise LCS on `{UnlimitedNatural, UInteger|UReal}` (§3.2).

### C-08 — the classic-only cell the lattice DOES change, and why it is not really the lattice's fault
`Set{*, 1}` is a classic-only expression. Measured, same fork binary, two different driver programs:

```
=== FORK, program B2, 10 JVMs ===        10  Set{*, 1}  =>  TYPE Set(UnlimitedNatural)
=== FORK, program Sweep, 10 JVMs ===     10  Set{*, 1}      Set(Integer)
=== 7.5.0, program B2, 10 JVMs ===       10  Set{*, 1}  =>  TYPE Set(Integer)
=== 7.5.0, program Sweep, 10 JVMs ===    10  Set{*, 1}      Set(Integer)
```

Traced to the bottom (`/tmp/ulcs/U2.java`, which re-implements `calculateFor` step by step):

```
=== FORK ===
step2 allCommonSuperTypes (HashSet order) = [UReal, Integer, UnlimitedNatural, UInteger, Real, OclAny]   size=6
step3 trace: [init->UReal] [Integer.conformsTo(UReal)=true -> Integer] [UnlimitedNatural.conformsTo(Integer)=true -> UnlimitedNatural] …
RESULT = UnlimitedNatural
=== 7.5.0 ===
step2 allCommonSuperTypes (HashSet order) = [UnlimitedNatural, Real, Integer, OclAny]   size=4
step3 trace: [init->UnlimitedNatural] [Real rejected] [Integer.conformsTo(UnlimitedNatural)=true -> Integer] [OclAny rejected]
RESULT = Integer
```

Root cause, in three facts:

1. `Integer` and `UnlimitedNatural` are **mutually conformant** — `Integer.conformsTo(UnlimitedNatural)`
   and `UnlimitedNatural.conformsTo(Integer)` are both `true`. Scanning all 144 fork cells and all 49
   baseline cells, that is the **only** such pair in either tree:
   `mutually-conformant distinct pairs in the FORK: [('Integer', 'UnlimitedNatural')]`
   `mutually-conformant distinct pairs in 7.5.0:   [('Integer', 'UnlimitedNatural')]`
2. `calculateFor` step 3 is a greedy `else if (t.conformsTo(result)) result = t;`
   (`UniqueLeastCommonSupertypeDeterminator.java:58-70`). With a mutually-conformant pair it has no
   fixpoint — the winner is whichever of the two the iterator yields last.
3. `BasicType.hashCode()` is `getClass().hashCode()` (`BasicType.java:57-59`, identical in both trees),
   i.e. the JVM identity hash of a `Class` object, assigned lazily on first request. So `HashSet`
   iteration order over types depends on the program's *class-hashing history*.

Decisive experiment — same binary, only the order in which identity hashes are first requested varies
(`/tmp/ulcs/U3.java`):

```
=== FORK ===                                                        === 7.5.0 ===
order=none  ULCS=UnlimitedNatural                                   order=none  ULCS=Integer
order=A     ULCS=Integer                                            order=A     ULCS=UnlimitedNatural
order=B     ULCS=Integer                                            order=B     ULCS=UnlimitedNatural
```

**So `ULCS({UnlimitedNatural, Integer})` is unstable in PLAIN 7.5.0 too.** This is a pre-existing
upstream latent defect, not something the lattice introduces; what the lattice does is grow
`allCommonSuperTypes` from 4 to 6 elements and thereby *change which answer you happen to observe*.

Pairwise `TypeImpl.getLeastCommonSupertype` is **not** affected: its final loop
(`TypeImpl.java:129-146`) requires the winner to conform to *every* candidate, and the intersection
`cs` can only contain `UnlimitedNatural` when one operand already is `UnlimitedNatural`, in which case
the `equals` short-circuit fires. Measured `LCS(UnlimitedNatural, Integer) = Integer` in both trees,
stably. (INFERRED for the general argument; MEASURED for the cell.)

**Adaptation.** This is a B7-class latent defect: not uncertainty meaning, present in both parents,
and *poisonous to the thesis* because Study A defines agreement against this port — a
nondeterministic cell would make agreement unmeasurable. The port should give `calculateFor` a
deterministic tie-break (e.g. iterate a `TreeSet` ordered by `toString()`, or require
`result.conformsTo(t)` to be false before replacing) and record the deviation, rather than inherit a
coin-flip. It is **not** covered by the existing B11 waiver, which is about `UnlimitedNatural`'s
missing `allSupertypes` entries; this is about `calculateFor`'s tie-break and it bites even with B11
reproduced verbatim. **New open decision — call it B11b.**

### C-09 — `TupleType.allSupertypes()` is a cartesian product; the lattice raises its base from 3 to 5
`TupleType.genAllSuperTypes` (`TupleType.java:241-264`) recurses over the cartesian product of each
part's `allSupertypes()`. With `Integer` parts the size is exactly `3^n + 1` in 7.5.0 and `5^n + 1`
in the fork. Measured (`/tmp/tupprobe/`):

| n parts (`Tuple(p1..pn : Integer)`) | fork | 7.5.0 |
|---|---|---|
| 2 | 26 | 10 |
| 3 | 126 | 28 |
| 4 | 626 | 82 |
| 5 | 3126 | 244 |
| 6 | 15626 (130 ms) | 730 (17 ms) |
| 7 | 78126 (1616 ms) | 2188 (14 ms) |

At n = 9 the fork driver did not finish inside a 2-minute cap. **Adaptation:** no code change is
required for correctness — `TupleType` is unedited by the fork and must stay unedited — but the port
must not add uncertainty predicates in a way that further widens `allSupertypes`, and any
tuple-heavy regression fixture in the acceptance corpus needs an arity cap. Flag for the harness
area. (Not previously recorded anywhere in `docs/port2/`.)

### C-10 — the five names become reserved, and it is the grammar, not just `TypeFactory`
`ASTSimpleType.gen` (`use-core/.../parser/ocl/ASTSimpleType.java:47`) consults
`TypeFactory.mkSimpleType(name)` **before** enums, before `ctx.model().getClassifier(name)`, and
before the variable type table. Registering `"UReal"`, `"UInteger"`, `"UBoolean"`, `"UString"`,
`"SBoolean"` in `buildInTypesMap` therefore shadows any user-declared classifier of that name.
Measured (`/tmp/cprobe/C4.java`, model declaring `class UReal` and `class Person`):

```
=== FORK: model declares class UReal ===
UReal.allInstances()   =>  COMPILE-ERROR probe:line 1:5 mismatched input '.' expecting (
Person.allInstances()  =>  TYPE Set(Person)
UReal(2,0.5)           =>  TYPE UReal
```

The failure is at the *lexer/parser* level ("expecting `(`"), i.e. the fork also made `UReal` a
grammar token, so the shadowing is even harder than `mkSimpleType` alone. **Adaptation:** the type
area registers the five `buildInTypesMap` entries and nothing more; the grammar half belongs to the
grammar area, and the two must agree on the same five names. No upstream test resource uses any of
the five names (`grep -rlnwE 'UReal|UInteger|UBoolean|UString|SBoolean' use-core/src use-gui/src
--include=*.use …` returns only the thesis harness under
`use-core/src/test/java/org/tzi/use/uncertainty/differential/`), so nothing upstream breaks — but a
user model can.

### C-11 — `TypeImpl.getLeastCommonSupertype`'s fast path stops firing
7.5.0 short-circuits when the intersection is exactly `{X, OclAny}` (`TypeImpl.java:123-127`). With
the fork's wider supertype sets the intersection is often larger and the general loop at `:129-146`
runs instead. Example: `LCS(Integer, Real)` intersects to `{Real, OclAny}` (size 2, fast path) in
7.5.0 and to `{Real, UReal, OclAny}` (size 3, general loop) in the fork — **same answer `Real`, both
measured**. **Adaptation: do not "optimise" that loop away and do not extend the size-2 shortcut to
size 3.** The general loop is what keeps the answers right.

### C-12 — `TypeImpl.conformsTo` is unbounded recursion in BOTH trees
`return this.conformsTo(other);` — fork `TypeImpl.java:76-78`, 7.5.0 `TypeImpl.java:78-81`
(read verbatim at 7.5.0 `:79-81`). It never fires only because every concrete type overrides it.
**Adaptation: every one of the five new concrete types MUST override `conformsTo`** or it
`StackOverflowError`s. All five fork leaves do; the two abstract tags (`UncertainType`,
`UncertainBooleanType`) correctly do not. Add a unit test that calls `conformsTo` on each of the five.

### C-13 — three cosmetic fork anomalies to normalise the 7.5.0 way
`mkUReal()` is declared `public static Type mkUReal()` (fork `TypeFactory.java:93-95`) — the only
`mk*` for a basic type that widens its return; every sibling returns its concrete class.
`UIntegerType()` is `public` (fork `UIntegerType.java:14`) while `UBooleanType()`, `UStringType()`,
`SBooleanType()` are package-private and `URealType()` is `protected`. `allSupertypes` adds `this` in
`UIntegerType`/`URealType` but `TypeFactory.mkX()` in `UBooleanType`/`UStringType` (equivalent,
because the factory interns — `INTERN mkUReal()==mkUReal() : true`, MEASURED).
**Adaptation:** narrow `mkUReal()` to `URealType`, make all five constructors package-private to match
`BooleanType()`/`IntegerType()`/`RealType()`/`StringType()`, and use `this` uniformly. All three are
source-compatible with every fork call site and none changes a single lattice cell.
Caveat (READ_FROM_SOURCE): the fork's `TypeTest.java:392-393` does `URealType urt1 = new URealType();`,
so the ported constructor must remain reachable from the same package's tests.

### C-14 — `EnumType` is a `MClassifierImpl`, not a `TypeImpl`
`EnumType.java:39: public final class EnumType extends MClassifierImpl`. Easy to miss when placing the
ten no-ops; if they were added only to `TypeImpl`, `EnumType` would not compile. Measured
consequence of getting it right: `Enum.allSupertypes() = [E, OclAny]` is one of the only two
`testSupertype` assertions that still passes (§6).

### C-15 — B11: `UnlimitedNatural` is left out of the numeric widening
`UnlimitedNatural.conformsTo(UInteger)` and `…(UReal)` are `true` (§1, falling out of the unedited
predicate rule at `UnlimitedNaturalType.java:59-61` (7.5.0) / `:61-63` (fork)), but `UnlimitedNaturalType.allSupertypes()` was
not extended (fork `:69-76`, 7.5.0 `:67-74`, unedited) and `UnlimitedNatural.isKindOfUInteger/isKindOfUReal` are `false`
(§1.2). Consequence, MEASURED: `LCS(UnlimitedNatural, UInteger) = OclAny` while
`ULCS({UnlimitedNatural, UInteger}) = UInteger`, observable as
`if true then * else UInteger(1,1) endif : OclAny` vs `Set{*, UInteger(1,1)} : Set(UInteger)`.
This is the SAME shape as a pre-existing upstream defect (`Integer.conformsTo(UnlimitedNatural)` is
true while `UnlimitedNatural ∉ Integer.allSupertypes()`), which the fork merely widened.
**B11 is still open in `spec-parts/11-types.md §1.8-1` and `specification.md §9 row 11`; the standing
recommendation there is REPRODUCE plus a pinning regression test. This document does not overturn it,
but it adds two facts that decision did not have:** the divergence is observable at expression level,
and the two LCS routines disagree, so "reproduce" means reproducing a *self-inconsistent* pair of
answers. Note also that B7 (fix the 33 modernization-ledger defects) does not decide B11, and B11's
"reproduce" is not licence to reproduce a B7 row.

---

## 6. Every upstream assertion the lattice falsifies — MEASURED, not assumed

Method: `grep -rn` for `allSupertypes`, `conformsTo`, `getLeastCommonSupertype`,
`UniqueLeastCommonSupertypeDeterminator` across `use-core/src/test` and `use-gui/src/test`; then
every hit was executed against the fork's real lattice by `/tmp/upstest/UpsTest.java`, which
re-states each upstream assertion verbatim (same expected sets, same `HashSet` comparison).

```
$ grep -rn "getLeastCommonSupertype\|LeastCommonSupertypeDeterminator" use-core/src/test use-gui/src/test
   (no output — zero hits)

$ grep -rn "allSupertypes" use-core/src/test use-gui/src/test
   24 hits, ALL in use-core/src/test/java/org/tzi/use/uml/ocl/type/TypeTest.java:137-227
   (= the 12 assertions of testSupertype)

$ grep -rln "conformsTo" use-core/src/test use-gui/src/test   (with counts)
   13  use-core/.../uml/ocl/type/TypeTest.java          ← testSubtype
    6  use-core/.../uml/sys/soil/StatementEffectTest.java
    2  use-core/.../utilcore/soil/VariableSetTest.java
    1  use-core/.../utilcore/soil/SymbolTableTest.java
```

### 6.1 `TypeTest#testSupertype` — 10 of 12 break (MEASURED)

```
PASS  OclAny.allSupertypes()                       expects [OclAny]                        fork [OclAny]
BREAK Boolean.allSupertypes()                      expects [Boolean, OclAny]               fork [Boolean, UBoolean, SBoolean, OclAny]
BREAK Integer.allSupertypes()                      expects [Integer, Real, OclAny]         fork [UReal, UInteger, Integer, Real, OclAny]
BREAK Real.allSupertypes()                         expects [Real, OclAny]                  fork [UReal, Real, OclAny]
BREAK String.allSupertypes()                       expects [String, OclAny]                fork [UString, String, OclAny]
PASS  Enum.allSupertypes()                         expects [E, OclAny]                     fork [E, OclAny]
BREAK Collection(Boolean).allSupertypes()          … fork adds Collection(UBoolean), Collection(SBoolean)
BREAK Collection(Integer).allSupertypes()          … fork adds Collection(UReal), Collection(UInteger)
BREAK Collection(Collection(Real)).allSupertypes() … fork adds Collection(Collection(UReal))
BREAK Set(Integer).allSupertypes()                 … 6 expected vs 10 actual
BREAK Sequence(Integer).allSupertypes()            … 6 expected vs 10 actual
BREAK Bag(Integer).allSupertypes()                 … 6 expected vs 10 actual
testSupertype: 10 of 12 assertions BREAK under the fork lattice
```

The record's "10 of 12" is **confirmed by measurement**, and the two survivors are now named:
`OclAny` (no supertype but itself) and `Enum` (a `MClassifierImpl`, so its `allSupertypes` comes from
`MClassifierImpl.java:133-138`, which the fork does not edit — C-14).

### 6.2 `TypeTest#testSubtype` — 0 of 13 break (MEASURED)

```
PASS  Integer < Integer / Integer < Real / Set(Integer) < Set(Integer) / Set(Integer) < Set(Real)
PASS  Set(Integer) < Collection(Integer) / Bag(Integer) < Bag(Integer) / Bag(Integer) < Collection(Integer)
PASS  Set(Set(Integer)) < Collection(Collection(Integer)) / Sequence(Integer) < Sequence(Integer)
PASS  !(Set(Integer) < Set(String)) / !(Collection(Integer) < Set(Integer))
PASS  !(Collection(Integer) < Bag(Integer)) / !(Bag(Integer) < Set(Integer))
testSubtype: 0 of 13 assertions BREAK under the fork lattice
```

### 6.3 The other nine `conformsTo` sites — none break (READ_FROM_SOURCE)

`SymbolTableTest.java:68` and `VariableSetTest.java:100` are both
`assertTrue(integerType.conformsTo(realType))` — still true (§1).
`VariableSetTest.java:235` is a search loop over model-declared types, no uncertain type in scope.
`StatementEffectTest.java:715,746,779,783,807,811` are all
`assertTrue(varVal1.type().conformsTo(fOldVarEnv.lookUp(…).type()))` over classifier/`OclVoid` types,
which the lattice does not touch. No new edge involves a type these tests can construct.

### 6.4 The six architecture tests cannot break (READ_FROM_SOURCE)

```
$ for f in use-core/.../architecture/*.java use-gui/.../architecture/*.java; do echo "$f : $(grep -c assert $f) assert-lines"; done
AntCyclicDependenciesCoreTest.java   : 0
MavenCyclicDependenciesCoreTest.java : 0
AntCyclicDependenciesGUITest.java    : 0
AntLayeredArchitectureTest.java      : 0
MavenCyclicDependenciesGUITest.java  : 0
MavenLayeredArchitectureTest.java    : 0
```

All six write a report file and assert nothing (`writeResultsToFile`). Adding seven classes to
`org.tzi.use.uml.ocl.type` cannot fail them.

### 6.5 Nothing implements `Type` outside `use-core/src/main` (MEASURED by grep)

```
$ grep -rn "implements Type\b|implements MClassifier\b|extends MClassifierImpl|extends TypeImpl\b" --include=*.java .   (excluding .git/)
   14 hits, ALL under use-core/src/main/java/org/tzi/use/
$ grep -rn "implements Type\b|extends TypeImpl|extends BasicType|implements MClassifier" use-core/src/test use-gui/src/test
   NONE
```
Modules are `use-assembly`, `use-core`, `use-gui` only (`pom.xml <modules>`). So the ten new
interface methods create **zero** compile breaks in any test source tree.

### 6.6 VERDICT on the waiver count

> **One waiver is enough, and it is the right one.** Exactly one upstream test *method* —
> `use-core/src/test/java/org/tzi/use/uml/ocl/type/TypeTest.java#testSupertype` — is falsified, at
> 10 of its 12 assertions, by exactly one design decision (adopting the fork's `allSupertypes`
> widening on `BooleanType`/`IntegerType`/`RealType`/`StringType`). No second upstream test method,
> in either module, asserts anything the lattice change falsifies. The rule-3 waiver in
> `docs/port2/upstream-test-waivers.md` should cite the measurement in §6.1-6.5 rather than
> re-arguing each of the ten assertions.

Two things that are **not** waivers and must not be smuggled into one:

* `TypeTest`'s ~39 `testIsTypeOf*`/`testIsKindOf*` methods each want **ten more `assertFalse` lines**
  once `Type` gains ten predicates (the fork did exactly this — e.g. fork `TypeTest.java:462-466`
  appends the five `isTypeOf*` to `testIsTypeOfBag`). Omitting them fails nothing; it is a coverage
  gap, not a broken assertion. Budget for it, do not waive it.
* C-08/B11b is a *nondeterminism* in `calculateFor`, present in plain 7.5.0. It currently breaks no
  upstream test only because no upstream `.use`/`.soil` fixture puts `*` in a collection literal
  (`grep` over `use-core/src/test/resources` for `Set{*`, `Bag{*`, `Sequence{*`, `OrderedSet{*`,
  `, *` returns nothing). That is luck, not safety.

---

## 7. Reproduction

```bash
cd /home/xoruser/msc-4/use-msc2026
L=.git/reference-repositories/uncertainty/USE-Uncertainty/lib
CPF="$L/use.jar:$L/atenearesearchgroup.uncertainty.jar:$L/antlr-3.4-complete.jar:$L/guava-20.0.jar"
M=~/.m2/repository
CPB="use-core/target/classes:$M/org/antlr/antlr-runtime/3.5.3/antlr-runtime-3.5.3.jar:$M/com/google/guava/guava/33.6.0-jre/guava-33.6.0-jre.jar:$M/jline/jline/2.14.6/jline-2.14.6.jar"

# §1-§3  conformance / allSupertypes / LCS from the fork SOURCES, plus the 7.5.0 baseline
bash docs/port2/spec-parts/11-types-oracle.sh /tmp/typeoracle

# §1-§3  the same, from the 2021 JAR — the independent second oracle
javac -cp "$CPF" -d /tmp/forkprobe/out /tmp/forkprobe/TProbe.java && java -cp "/tmp/forkprobe/out:$CPF" TProbe
diff <(grep -E '^(CONF|LCS) ' /tmp/forkprobe/jar.txt | sort) \
     <(grep -E '^(CONF|LCS) ' /tmp/typeoracle/fork.txt  | sort)   # must be empty

# §1.2  predicate battery under both VoidHandling modes
java -cp "/tmp/forkprobe/out:$CPF" PProbe

# §4    end-to-end compiler typing, fork then 7.5.0
java -cp "/tmp/cprobe/out:$CPF" CProbe ; java -cp "/tmp/bprobe/out:$CPB" BProbe

# §5 C-08  the nondeterminism, same binary, hash-order forced
for o in none A B; do java -cp "/tmp/ulcs/outf:$CPF" U3 $o; done
for o in none A B; do java -cp "/tmp/ulcs/outb:$CPB" U3 $o; done

# §5 C-09  tuple supertype blow-up
java -cp "/tmp/tupprobe/outf:$CPF" TupProbe ; java -cp "/tmp/tupprobe/outb:$CPB" TupProbe

# §6    the upstream assertions, executed against the fork lattice
java -cp "/tmp/upstest/out:$CPF" UpsTest

# §6    the greps that bound the search
grep -rn "allSupertypes" use-core/src/test use-gui/src/test
grep -rn "getLeastCommonSupertype\|LeastCommonSupertypeDeterminator" use-core/src/test use-gui/src/test
grep -rn "conformsTo" use-core/src/test use-gui/src/test
grep -rn "public boolean isTypeOfOclAny()" --include=*.java use-core/src/main use-gui/src/main
grep -rn "extends MClassifierImpl" --include=*.java use-core/src/main use-gui/src/main
```

All driver sources are in `/tmp/{forkprobe,cprobe,bprobe,ulcs,tupprobe,upstest,sweep}/`; they are
scratch files, per the ground rules, and are regenerable from the listings above.

---

## 8. Open decisions this area hands back

| id | decision | recommendation |
|---|---|---|
| **B11** (open, pre-existing) | reproduce or fix `UnlimitedNatural`'s missing `allSupertypes` widening | unchanged from `11-types.md §1.8-1`: reproduce + pin `LCS(UnlimitedNatural, UInteger) == OclAny`. New input: the divergence is expression-observable and the two LCS routines disagree (§4.3). |
| **B11b** (NEW, this document) | `UniqueLeastCommonSupertypeDeterminator.calculateFor` returns an order-dependent answer for mutually-conformant candidates, in 7.5.0 *and* the fork | **fix** — give it a deterministic tie-break and record the deviation. A nondeterministic cell in the semantic oracle makes Study A's agreement metric unmeasurable. |
| C-09 | tuple-arity cap in the acceptance corpus | hand to the harness area; `5^n+1` supertypes at n=7 already costs 1.6 s |
| C-10 | five reserved type names shadow user classifiers | type area registers the names; grammar area must agree on the same five |
| C-13 | `mkUReal()` return type, constructor visibility, `this` vs `mkX()` | normalise the 7.5.0 way; zero lattice cells change |
