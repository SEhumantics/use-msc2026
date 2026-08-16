# 11 — Type system

Port specification, TYPE-SYSTEM section.
Scope: `org.tzi.use.uml.ocl.type` plus the one type-implementing class outside it
(`org.tzi.use.uml.mm.MClassifierImpl`).

---

## 0. Provenance, evidence rules, and method

| | path |
|---|---|
| historical fork | `/home/xoruser/msc-4/use-msc2026/.git/reference-repositories/uncertainty/USE-Uncertainty` (git HEAD `74acd0d29443e0cbfbc8fba0a0975f9e3dc5585e`) |
| fork type package | `<fork>/src/main/org/tzi/use/uml/ocl/type/` |
| fork type test | `<fork>/src/test/org/tzi/use/uml/ocl/type/TypeTest.java` (2074 lines) |
| target (7.5.0) | `/home/xoruser/msc-4/use-msc2026/use-core/src/main/java/org/tzi/use/uml/ocl/type/` |
| target type test | `/home/xoruser/msc-4/use-msc2026/use-core/src/test/java/org/tzi/use/uml/ocl/type/TypeTest.java` (1423 lines) |

Target sources were read at commit `b7aaa99c`; `git diff --stat b7aaa99c..bc2970a0 -- use-core/.../ocl/type use-core/.../uml/mm` is empty, so every 7.5.0 line number below is still valid at `bc2970a0`.

Throughout, a claim is tagged:

* **(C)** — read directly out of the named file at the named lines.
* **(O)** — produced by the executable oracle `docs/port2/spec-parts/11-types-oracle.sh`, which
  compiles the fork's own type classes *verbatim* (only `TypeFactory` is reduced to its interned
  simple-type accessors, whose bodies are copied character-for-character) and prints
  `conformsTo`, `allSupertypes` and `getLeastCommonSupertype` for every pair. The same script
  compiles the 7.5.0 classes the same way, so fork and baseline are directly diffable.
* **(T)** — asserted by the fork's own JUnit test, i.e. it is the historical author's intent, not
  just an accident of the code.

Nothing here is taken from `origin/main` or from any earlier port attempt.

---

## 1. The historical uncertainty type lattice

### 1.1 New Java classes and their hierarchy (C)

```
Type                         (interface,  Type.java:33)
 └── TypeImpl                (abstract,   TypeImpl.java:34)
      └── BasicType          (abstract,   BasicType.java:31)
           ├── BooleanType, IntegerType, RealType, StringType, UnlimitedNaturalType   (upstream)
           └── UncertainType (abstract,   UncertainType.java:10)      ← NEW
                ├── UIntegerType          (UIntegerType.java:12)      ← NEW
                ├── URealType             (URealType.java:6)          ← NEW
                ├── UStringType           (UStringType.java:6)        ← NEW
                └── UncertainBooleanType  (abstract, UncertainBooleanType.java:3) ← NEW
                     ├── UBooleanType     (UBooleanType.java:6)       ← NEW
                     └── SBooleanType     (SBooleanType.java:6)       ← NEW
```

Two facts about this Java hierarchy that matter for the port:

* `UncertainType` and `UncertainBooleanType` carry **no behaviour at all** — each is a bare
  `protected Ctor(String t) { super(t); }` (`UncertainType.java:12-14`,
  `UncertainBooleanType.java:5-7`). They exist purely as `instanceof` tags. The fork uses that tag
  at 11 sites in 3 files outside the type package: `StandardOperationsCollection.java:104,169,401,474`,
  `StandardOperationsNumber.java:351,946,1024,1101,1179`, `StandardOperationsAny.java:49,199`
  (grep for `instanceof UncertainType`). Two further files merely *import* the class without using
  it — `ExpQuery.java:30` and `UIntegerExpOpsTest.java:6` — so the port need not carry those imports.
  There is **no** `isKindOfUncertain…()` predicate on `Type`; the "is this any uncertain type"
  question is answered only by `instanceof`. `UncertainBooleanType` has **no** `instanceof` site at
  all: it exists only to give `UBooleanType` and `SBooleanType` a shared Java parent.
* The Java hierarchy is **not** the conformance lattice. `SBooleanType` is a Java *sibling* of
  `UBooleanType` but a conformance *supertype* of it (§1.3). Do not infer conformance from
  `extends`.

Constructor visibility in the fork is inconsistent (C):
`UBooleanType()` 8, `UStringType()` 8, `SBooleanType()` 8 are package-private;
`URealType()` 8 is `protected`; **`UIntegerType()` 14 is `public`**. The public one lets outside
code build a second `UInteger` instance; because `BasicType.equals` compares `getClass()`
(`BasicType.java:52-58`), such an instance is `.equals()` to the interned one but not `==`.
Recommendation for the port: make all five package-private, matching `BooleanType()`,
`IntegerType()`, `RealType()`, `StringType()` in 7.5.0.

### 1.2 Type names (C)

`UncertainType(String t)` feeds `BasicType.fTypename`, printed by `BasicType.toString(sb)`
(`BasicType.java:45-47`). The literal names are `"UBoolean"` (`UBooleanType.java:9`),
`"UInteger"` (`UIntegerType.java:15`), `"UReal"` (`URealType.java:9`),
`"UString"` (`UStringType.java:9`), `"SBoolean"` (`SBooleanType.java:9`).

### 1.3 Conformance grid, both directions (O)

`A.conformsTo(B)` — row `A`, column `B`. `T` = true, `.` = false.

| A \ B | UBoolean | UInteger | UReal | UString | SBoolean | Boolean | Integer | Real | String | OclVoid | OclAny |
|---|---|---|---|---|---|---|---|---|---|---|---|
| **UBoolean** | T | . | . | . | T | . | . | . | . | . | T |
| **UInteger** | . | T | T | . | . | . | . | . | . | . | T |
| **UReal**    | . | . | T | . | . | . | . | . | . | . | T |
| **UString**  | . | . | . | T | . | . | . | . | . | . | T |
| **SBoolean** | . | . | . | . | T | . | . | . | . | . | T |
| **Boolean**  | T | . | . | . | T | T | . | . | . | . | T |
| **Integer**  | . | T | T | . | . | . | T | T | . | . | T |
| **Real**     | . | . | T | . | . | . | . | T | . | . | T |
| **String**   | . | . | . | T | . | . | . | . | T | . | T |
| **OclVoid**  | T | T | T | T | T | T | T | T | T | T | T |
| **OclAny**   | . | . | . | . | . | . | . | . | . | . | T |

39 of the 121 ordered pairs are `true` (O).

Read as a Hasse diagram, the fork adds exactly five upward edges into the uncertain world plus one
edge inside it:

```
                       OclAny
        ┌────────┬────────┬─────────┴──┬──────────┐
     SBoolean  UReal              UString      (classifiers, enums, …)
        │      ├── UInteger           │
     UBoolean  │       │              │
        │      │       │              │
     Boolean  Real   ─ Integer ─      String
                (Integer ≤ Real, UInteger, UReal)
        └──────────── OclVoid ────────────┘   (OclVoid ≤ everything)
```

New edges, in the historical author's own words (T, `TypeTest.java:81-160`):
`String < UString`, `UBoolean < SBoolean`, `Boolean < SBoolean`, `Boolean < UBoolean`,
`Real < UReal`, `UInteger < UReal`, `Integer < UReal`, `Integer < UInteger`.

**Sanity check that constrains the whole port (O):** restricted to
`{Boolean, Integer, Real, String, OclVoid, OclAny, UnlimitedNatural}`, the fork's 49-cell
`conformsTo` block *and* its 49-cell `getLeastCommonSupertype` block are **byte-identical** to
7.5.0's. The extension is purely additive: it introduces no new relation among the classical OCL
types and rewires none. Any port that changes a classical-vs-classical cell has a bug. The
oracle script ends by asserting exactly this and prints
`IDENTICAL — the uncertainty extension changes no classic-type cell`.

### 1.4 All 121 ordered pairs with the deciding code path (O + C)

"deciding disjunct" names the sub-expression of the row type's `conformsTo` that evaluated `true`
(all disjuncts were evaluated independently by the oracle; more than one can fire). Line ranges are
`<fork>/src/main/org/tzi/use/uml/ocl/type/<file>` and span the method signature to its closing brace.

| A | B | A conforms to B? | deciding method | deciding disjunct |
|---|---|---|---|---|
| `UBoolean` | `UBoolean` | **yes** | `UBooleanType.java:43-45` | other.equals(this) |
| `UBoolean` | `UInteger` | **no** | `UBooleanType.java:43-45` | no disjunct true |
| `UBoolean` | `UReal` | **no** | `UBooleanType.java:43-45` | no disjunct true |
| `UBoolean` | `UString` | **no** | `UBooleanType.java:43-45` | no disjunct true |
| `UBoolean` | `SBoolean` | **yes** | `UBooleanType.java:43-45` | other.isTypeOfSBoolean() |
| `UBoolean` | `Boolean` | **no** | `UBooleanType.java:43-45` | no disjunct true |
| `UBoolean` | `Integer` | **no** | `UBooleanType.java:43-45` | no disjunct true |
| `UBoolean` | `Real` | **no** | `UBooleanType.java:43-45` | no disjunct true |
| `UBoolean` | `String` | **no** | `UBooleanType.java:43-45` | no disjunct true |
| `UBoolean` | `OclVoid` | **no** | `UBooleanType.java:43-45` | no disjunct true |
| `UBoolean` | `OclAny` | **yes** | `UBooleanType.java:43-45` | other.isTypeOfOclAny() |
| `UInteger` | `UBoolean` | **no** | `UIntegerType.java:48-52` | no disjunct true |
| `UInteger` | `UInteger` | **yes** | `UIntegerType.java:48-52` | other.isTypeOfUInteger() |
| `UInteger` | `UReal` | **yes** | `UIntegerType.java:48-52` | other.isTypeOfUReal() |
| `UInteger` | `UString` | **no** | `UIntegerType.java:48-52` | no disjunct true |
| `UInteger` | `SBoolean` | **no** | `UIntegerType.java:48-52` | no disjunct true |
| `UInteger` | `Boolean` | **no** | `UIntegerType.java:48-52` | no disjunct true |
| `UInteger` | `Integer` | **no** | `UIntegerType.java:48-52` | no disjunct true |
| `UInteger` | `Real` | **no** | `UIntegerType.java:48-52` | no disjunct true |
| `UInteger` | `String` | **no** | `UIntegerType.java:48-52` | no disjunct true |
| `UInteger` | `OclVoid` | **no** | `UIntegerType.java:48-52` | no disjunct true |
| `UInteger` | `OclAny` | **yes** | `UIntegerType.java:48-52` | other.isTypeOfOclAny() |
| `UReal` | `UBoolean` | **no** | `URealType.java:13-15` | no disjunct true |
| `UReal` | `UInteger` | **no** | `URealType.java:13-15` | no disjunct true |
| `UReal` | `UReal` | **yes** | `URealType.java:13-15` | equals(t) |
| `UReal` | `UString` | **no** | `URealType.java:13-15` | no disjunct true |
| `UReal` | `SBoolean` | **no** | `URealType.java:13-15` | no disjunct true |
| `UReal` | `Boolean` | **no** | `URealType.java:13-15` | no disjunct true |
| `UReal` | `Integer` | **no** | `URealType.java:13-15` | no disjunct true |
| `UReal` | `Real` | **no** | `URealType.java:13-15` | no disjunct true |
| `UReal` | `String` | **no** | `URealType.java:13-15` | no disjunct true |
| `UReal` | `OclVoid` | **no** | `URealType.java:13-15` | no disjunct true |
| `UReal` | `OclAny` | **yes** | `URealType.java:13-15` | t.isTypeOfOclAny() |
| `UString` | `UBoolean` | **no** | `UStringType.java:31-34` | no disjunct true |
| `UString` | `UInteger` | **no** | `UStringType.java:31-34` | no disjunct true |
| `UString` | `UReal` | **no** | `UStringType.java:31-34` | no disjunct true |
| `UString` | `UString` | **yes** | `UStringType.java:31-34` | equals(other) |
| `UString` | `SBoolean` | **no** | `UStringType.java:31-34` | no disjunct true |
| `UString` | `Boolean` | **no** | `UStringType.java:31-34` | no disjunct true |
| `UString` | `Integer` | **no** | `UStringType.java:31-34` | no disjunct true |
| `UString` | `Real` | **no** | `UStringType.java:31-34` | no disjunct true |
| `UString` | `String` | **no** | `UStringType.java:31-34` | no disjunct true |
| `UString` | `OclVoid` | **no** | `UStringType.java:31-34` | no disjunct true |
| `UString` | `OclAny` | **yes** | `UStringType.java:31-34` | other.isTypeOfOclAny() |
| `SBoolean` | `UBoolean` | **no** | `SBooleanType.java:32-35` | no disjunct true |
| `SBoolean` | `UInteger` | **no** | `SBooleanType.java:32-35` | no disjunct true |
| `SBoolean` | `UReal` | **no** | `SBooleanType.java:32-35` | no disjunct true |
| `SBoolean` | `UString` | **no** | `SBooleanType.java:32-35` | no disjunct true |
| `SBoolean` | `SBoolean` | **yes** | `SBooleanType.java:32-35` | other.isTypeOfSBoolean() |
| `SBoolean` | `Boolean` | **no** | `SBooleanType.java:32-35` | no disjunct true |
| `SBoolean` | `Integer` | **no** | `SBooleanType.java:32-35` | no disjunct true |
| `SBoolean` | `Real` | **no** | `SBooleanType.java:32-35` | no disjunct true |
| `SBoolean` | `String` | **no** | `SBooleanType.java:32-35` | no disjunct true |
| `SBoolean` | `OclVoid` | **no** | `SBooleanType.java:32-35` | no disjunct true |
| `SBoolean` | `OclAny` | **yes** | `SBooleanType.java:32-35` | other.isTypeOfOclAny() |
| `Boolean` | `UBoolean` | **yes** | `BooleanType.java:63-65` | other.isTypeOfUBoolean() |
| `Boolean` | `UInteger` | **no** | `BooleanType.java:63-65` | no disjunct true |
| `Boolean` | `UReal` | **no** | `BooleanType.java:63-65` | no disjunct true |
| `Boolean` | `UString` | **no** | `BooleanType.java:63-65` | no disjunct true |
| `Boolean` | `SBoolean` | **yes** | `BooleanType.java:63-65` | other.isTypeOfSBoolean() |
| `Boolean` | `Boolean` | **yes** | `BooleanType.java:63-65` | this.equals(other) |
| `Boolean` | `Integer` | **no** | `BooleanType.java:63-65` | no disjunct true |
| `Boolean` | `Real` | **no** | `BooleanType.java:63-65` | no disjunct true |
| `Boolean` | `String` | **no** | `BooleanType.java:63-65` | no disjunct true |
| `Boolean` | `OclVoid` | **no** | `BooleanType.java:63-65` | no disjunct true |
| `Boolean` | `OclAny` | **yes** | `BooleanType.java:63-65` | other.isTypeOfOclAny() |
| `Integer` | `UBoolean` | **no** | `IntegerType.java:71-73` | no disjunct true |
| `Integer` | `UInteger` | **yes** | `IntegerType.java:71-73` | t.isKindOfNumber(EXCLUDE_VOID) |
| `Integer` | `UReal` | **yes** | `IntegerType.java:71-73` | t.isKindOfNumber(EXCLUDE_VOID) |
| `Integer` | `UString` | **no** | `IntegerType.java:71-73` | no disjunct true |
| `Integer` | `SBoolean` | **no** | `IntegerType.java:71-73` | no disjunct true |
| `Integer` | `Boolean` | **no** | `IntegerType.java:71-73` | no disjunct true |
| `Integer` | `Integer` | **yes** | `IntegerType.java:71-73` | t.isKindOfNumber(EXCLUDE_VOID) |
| `Integer` | `Real` | **yes** | `IntegerType.java:71-73` | t.isKindOfNumber(EXCLUDE_VOID) |
| `Integer` | `String` | **no** | `IntegerType.java:71-73` | no disjunct true |
| `Integer` | `OclVoid` | **no** | `IntegerType.java:71-73` | BLOCKED by !t.isTypeOfVoidType() |
| `Integer` | `OclAny` | **yes** | `IntegerType.java:71-73` | t.isTypeOfOclAny() |
| `Real` | `UBoolean` | **no** | `RealType.java:62-64` | no disjunct true |
| `Real` | `UInteger` | **no** | `RealType.java:62-64` | no disjunct true |
| `Real` | `UReal` | **yes** | `RealType.java:62-64` | t.isTypeOfUReal() |
| `Real` | `UString` | **no** | `RealType.java:62-64` | no disjunct true |
| `Real` | `SBoolean` | **no** | `RealType.java:62-64` | no disjunct true |
| `Real` | `Boolean` | **no** | `RealType.java:62-64` | no disjunct true |
| `Real` | `Integer` | **no** | `RealType.java:62-64` | no disjunct true |
| `Real` | `Real` | **yes** | `RealType.java:62-64` | equals(t) |
| `Real` | `String` | **no** | `RealType.java:62-64` | no disjunct true |
| `Real` | `OclVoid` | **no** | `RealType.java:62-64` | no disjunct true |
| `Real` | `OclAny` | **yes** | `RealType.java:62-64` | t.isTypeOfOclAny() |
| `String` | `UBoolean` | **no** | `StringType.java:57-61` | no disjunct true |
| `String` | `UInteger` | **no** | `StringType.java:57-61` | no disjunct true |
| `String` | `UReal` | **no** | `StringType.java:57-61` | no disjunct true |
| `String` | `UString` | **yes** | `StringType.java:57-61` | t.isTypeOfUString() |
| `String` | `SBoolean` | **no** | `StringType.java:57-61` | no disjunct true |
| `String` | `Boolean` | **no** | `StringType.java:57-61` | no disjunct true |
| `String` | `Integer` | **no** | `StringType.java:57-61` | no disjunct true |
| `String` | `Real` | **no** | `StringType.java:57-61` | no disjunct true |
| `String` | `String` | **yes** | `StringType.java:57-61` | equals(t) |
| `String` | `OclVoid` | **no** | `StringType.java:57-61` | no disjunct true |
| `String` | `OclAny` | **yes** | `StringType.java:57-61` | t.isTypeOfOclAny() |
| `OclVoid` | `UBoolean` | **yes** | `VoidType.java:23-25` | unconditional `return true` |
| `OclVoid` | `UInteger` | **yes** | `VoidType.java:23-25` | unconditional `return true` |
| `OclVoid` | `UReal` | **yes** | `VoidType.java:23-25` | unconditional `return true` |
| `OclVoid` | `UString` | **yes** | `VoidType.java:23-25` | unconditional `return true` |
| `OclVoid` | `SBoolean` | **yes** | `VoidType.java:23-25` | unconditional `return true` |
| `OclVoid` | `Boolean` | **yes** | `VoidType.java:23-25` | unconditional `return true` |
| `OclVoid` | `Integer` | **yes** | `VoidType.java:23-25` | unconditional `return true` |
| `OclVoid` | `Real` | **yes** | `VoidType.java:23-25` | unconditional `return true` |
| `OclVoid` | `String` | **yes** | `VoidType.java:23-25` | unconditional `return true` |
| `OclVoid` | `OclVoid` | **yes** | `VoidType.java:23-25` | unconditional `return true` |
| `OclVoid` | `OclAny` | **yes** | `VoidType.java:23-25` | unconditional `return true` |
| `OclAny` | `UBoolean` | **no** | `OclAnyType.java:77-79` | no disjunct true |
| `OclAny` | `UInteger` | **no** | `OclAnyType.java:77-79` | no disjunct true |
| `OclAny` | `UReal` | **no** | `OclAnyType.java:77-79` | no disjunct true |
| `OclAny` | `UString` | **no** | `OclAnyType.java:77-79` | no disjunct true |
| `OclAny` | `SBoolean` | **no** | `OclAnyType.java:77-79` | no disjunct true |
| `OclAny` | `Boolean` | **no** | `OclAnyType.java:77-79` | no disjunct true |
| `OclAny` | `Integer` | **no** | `OclAnyType.java:77-79` | no disjunct true |
| `OclAny` | `Real` | **no** | `OclAnyType.java:77-79` | no disjunct true |
| `OclAny` | `String` | **no** | `OclAnyType.java:77-79` | no disjunct true |
| `OclAny` | `OclVoid` | **no** | `OclAnyType.java:77-79` | no disjunct true |
| `OclAny` | `OclAny` | **yes** | `OclAnyType.java:77-79` | other.isTypeOfOclAny() |

The eleven `conformsTo` bodies, verbatim (C):

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

`equals` is class identity for every basic type (`BasicType.java:52-58`:
`obj.getClass().equals(getClass())`), so `UBoolean.equals(SBoolean)` is `false` in both
directions even though they share a Java superclass (O).

Note `IntegerType.conformsTo` is a **predicate-driven** rule, not an enumeration: it says
"`Integer` conforms to anything numeric". `UIntegerType.isKindOfNumber` and
`URealType.isKindOfNumber` both return `true` (`UIntegerType.java:18-21`, `URealType.java:27-30`),
which is what produces `Integer ≤ UInteger` and `Integer ≤ UReal` *without any edit to
`IntegerType.conformsTo` itself*. That is the single most important mechanical fact in this
section: **`IntegerType.conformsTo` is byte-identical in the fork and in 7.5.0**
(`diff` of `IntegerType.java` shows the only changes are two added `isKindOf*` overrides and two
added `allSupertypes` entries).

### 1.5 `allSupertypes()` (O, confirmed by T at `TypeTest.java:204-274`)

| type | `allSupertypes()` in the fork | in 7.5.0 |
|---|---|---|
| `UBoolean` | `{UBoolean, SBoolean, OclAny}` | — |
| `UInteger` | `{UInteger, UReal, OclAny}` | — |
| `UReal` | `{UReal, OclAny}` | — |
| `UString` | `{UString, OclAny}` | — |
| `SBoolean` | `{SBoolean, OclAny}` | — |
| `Boolean` | `{Boolean, UBoolean, SBoolean, OclAny}` | `{Boolean, OclAny}` |
| `Integer` | `{Integer, UInteger, Real, UReal, OclAny}` | `{Integer, Real, OclAny}` |
| `Real` | `{Real, UReal, OclAny}` | `{Real, OclAny}` |
| `String` | `{String, UString, OclAny}` | `{String, OclAny}` |
| `OclVoid` | throws `UnsupportedOperationException` | same (`VoidType.java:8-10`) |
| `OclAny` | `{OclAny}` | same |
| `UnlimitedNatural` | `{UnlimitedNatural, Integer, Real, OclAny}` | same |

Implementation detail worth copying deliberately: `UBooleanType.allSupertypes` adds
`TypeFactory.mkUBoolean()` rather than `this` (`UBooleanType.java:35`), and `UStringType` adds
`TypeFactory.mkUString()` (`UStringType.java:25`), whereas `UIntegerType` and `URealType` add
`this` (`UIntegerType.java:43`, `URealType.java:35`). Because the factory interns, these are the
same object (O: `mkUReal() == mkUReal()` is `true`), so the two idioms are equivalent — but pick
one for the port.

### 1.6 Derived operations (O)

`TypeImpl.getLeastCommonSupertype` (fork `TypeImpl.java:80-146`, 7.5.0 `TypeImpl.java:83-149` —
**identical logic**) intersects `allSupertypes()`, so the LCS table below is a *consequence* of
§1.5 and needs no separate code:

| A \ B | UBoolean | UInteger | UReal | UString | SBoolean | Boolean | Integer | Real | String | OclVoid | OclAny |
|---|---|---|---|---|---|---|---|---|---|---|---|
| **UBoolean** | UBoolean | OclAny | OclAny | OclAny | SBoolean | UBoolean | OclAny | OclAny | OclAny | UBoolean | OclAny |
| **UInteger** | OclAny | UInteger | UReal | OclAny | OclAny | OclAny | UInteger | UReal | OclAny | UInteger | OclAny |
| **UReal** | OclAny | UReal | UReal | OclAny | OclAny | OclAny | UReal | UReal | OclAny | UReal | OclAny |
| **UString** | OclAny | OclAny | OclAny | UString | OclAny | OclAny | OclAny | OclAny | UString | UString | OclAny |
| **SBoolean** | SBoolean | OclAny | OclAny | OclAny | SBoolean | SBoolean | OclAny | OclAny | OclAny | SBoolean | OclAny |
| **Boolean** | UBoolean | OclAny | OclAny | OclAny | SBoolean | Boolean | OclAny | OclAny | OclAny | Boolean | OclAny |
| **Integer** | OclAny | UInteger | UReal | OclAny | OclAny | OclAny | Integer | Real | OclAny | Integer | OclAny |
| **Real** | OclAny | UReal | UReal | OclAny | OclAny | OclAny | Real | Real | OclAny | Real | OclAny |
| **String** | OclAny | OclAny | OclAny | UString | OclAny | OclAny | OclAny | OclAny | String | String | OclAny |
| **OclVoid** | UBoolean | UInteger | UReal | UString | SBoolean | Boolean | Integer | Real | String | OclVoid | OclAny |
| **OclAny** | OclAny | … all OclAny … | | | | | | | | | OclAny |

`UniqueLeastCommonSupertypeDeterminator.calculateFor` is **byte-identical** in fork and 7.5.0
(`diff --strip-trailing-cr` shows only the removed `// $Id$` line). It rides on `conformsTo` +
`allSupertypes`, so it acquires the uncertainty behaviour for free (O):
`{Boolean, UBoolean} → UBoolean`, `{Boolean, SBoolean} → SBoolean`,
`{UBoolean, SBoolean} → SBoolean`, `{Integer, UInteger} → UInteger`,
`{Integer, UReal} → UReal`, `{Real, UReal} → UReal`, `{UInteger, UReal} → UReal`,
`{String, UString} → UString`, `{Boolean, Integer} → OclAny`, `{UBoolean, UInteger} → OclAny`.

Collection lifting is also free. `CollectionType`/`SetType`/`SequenceType`/`BagType`/
`OrderedSetType` are **unedited** by the fork (their only diff vs 7.5.0 is import order and a
javadoc `<` → `&lt;` escape). Their `allSupertypes` maps over the element type's supertypes, so
`Collection(Boolean).allSupertypes()` automatically contains `Collection(UBoolean)` and
`Collection(SBoolean)` — asserted at `TypeTest.java:280-360` (T).

### 1.7 Predicate battery in the fork (O)

`EXCLUDE_VOID` column values; `.` = false.

| predicate | UBoolean | UInteger | UReal | UString | SBoolean | Boolean | Integer | Real | String | OclVoid | OclAny |
|---|---|---|---|---|---|---|---|---|---|---|---|
| `isKindOfNumber` | . | T | T | . | . | . | T | T | . | . | . |
| `isTypeOfUInteger` | . | T | . | . | . | . | . | . | . | . | . |
| `isKindOfUInteger` | . | T | . | . | . | . | **T** | . | . | . | . |
| `isTypeOfUReal` | . | . | T | . | . | . | . | . | . | . | . |
| `isKindOfUReal` | . | T | T | . | . | . | **T** | **T** | . | . | . |
| `isTypeOfUString` | . | . | . | T | . | . | . | . | . | . | . |
| `isKindOfUString` | . | . | . | T | . | . | . | . | **T** | . | . |
| `isTypeOfUBoolean` | T | . | . | . | . | . | . | . | . | . | . |
| `isKindOfUBoolean` | T | . | . | . | . | **T** | . | . | . | . | . |
| `isTypeOfSBoolean` | . | . | . | . | T | . | . | . | . | . | . |
| `isKindOfSBoolean` | **T** | . | . | . | T | **T** | . | . | . | . | . |
| `isKindOfOclAny` | T | T | T | T | T | T | T | T | T | . | T |

Bold cells are the ones set by an **edit to an upstream file** rather than by a new file. Under
`INCLUDE_VOID`, `OclVoid` answers `true` to every `isKindOf*` including the five new ones
(`VoidType.java` fork lines 37-40, 57-60, 122-135).

### 1.8 Defects in the historical lattice — decide before porting

1. **`UnlimitedNatural` is inconsistent with the new numeric edges.**
   `UnlimitedNatural.conformsTo(UInteger)` and `…(UReal)` are both `true` (O) — they fall out of
   the predicate-driven rule at `UnlimitedNaturalType.java:61-63`, which is identical in 7.5.0 —
   yet `UnlimitedNatural.allSupertypes()` was **not** extended and contains neither
   (`UnlimitedNaturalType.java:69-76`, unedited by the fork). The consequence is observable:
   `UnlimitedNatural.getLeastCommonSupertype(UInteger)` returns `OclAny`, not `UInteger` (O),
   although `conformsTo` says `UnlimitedNatural ≤ UInteger`. Note the same shape of defect
   already exists upstream (`Integer.conformsTo(UnlimitedNatural)` is `true` while
   `UnlimitedNatural ∉ Integer.allSupertypes()`), so this is a *pre-existing* upstream pattern
   the fork merely widened. **Port decision required:** reproduce the fork's behaviour bit-for-bit
   (recommended, so the historical tests stay green), or fix it and record the deviation.

2. **`TypeImpl.conformsTo` is unbounded recursion** in *both* trees:
   `return this.conformsTo(other);` (fork `TypeImpl.java:75-78`, 7.5.0 `TypeImpl.java:78-81`).
   It never fires today only because every concrete type overrides it. **Every new concrete
   uncertainty type must override `conformsTo`** or it will `StackOverflowError`. All five leaves
   do (§1.4); the two abstract tags do not, which is fine.

3. **Naming reads backwards.** `UBoolean` is a *supertype* of `Boolean`, `UReal` of `Real`,
   `UInteger` of `Integer`, `UString` of `String`, and `SBoolean` of `UBoolean`. Reviewers
   repeatedly get this wrong; state it in the ported javadoc.

---

## 2. Every `TypeFactory` entry the fork adds

Fork file `<fork>/src/main/org/tzi/use/uml/ocl/type/TypeFactory.java`; 7.5.0 file
`use-core/src/main/java/org/tzi/use/uml/ocl/type/TypeFactory.java`. **Edit to an upstream file.**

### 2.1 New interned static fields (C, fork lines 48-56)

| field | declaration | fork line |
|---|---|---|
| `uRealType` | `private static final URealType uRealType = new URealType();` | 48 |
| `uStringType` | `private static final UStringType uStringType = new UStringType();` | 50 |
| `uBooleanType` | `private static final UBooleanType uBooleanType = new UBooleanType();` | 51 |
| `uIntegerType` | `private static final UIntegerType uIntegerType = new UIntegerType();` | 55 |
| `sBooleanType` | `private static final SBooleanType sBooleanType = new SBooleanType();` | 56 |

All five are `static final` singletons created at class-init — i.e. **interned exactly like the
upstream basic types**; there is no lazy cache, no map lookup, no synchronisation. `==` and
`.equals()` agree for factory-produced instances (O: `mkUReal() == mkUReal()` is `true`).

### 2.2 New factory methods (C, fork lines 83-111)

| method | signature | returns | caches / interns? | fork line |
|---|---|---|---|---|
| `mkUInteger` | `public static UIntegerType mkUInteger()` | the `uIntegerType` singleton | yes — returns the interned field | 83 |
| `mkUReal` | `public static Type mkUReal()` | the `uRealType` singleton | yes | 93-95 |
| `mkUString` | `public static UStringType mkUString()` | the `uStringType` singleton | yes | 101 |
| `mkUBoolean` | `public static UBooleanType mkUBoolean()` | the `uBooleanType` singleton | yes | 107-109 |
| `mkSBoolean` | `public static SBooleanType mkSBoolean()` | the `sBooleanType` singleton | yes | 111 |

**Signature anomaly:** `mkUReal()` is declared to return `Type`, not `URealType` — the only one of
the five (and the only `mk*` for a basic type in the whole class) that widens its return type.
Every sibling (`mkInteger`, `mkReal`, `mkString`, `mkBoolean`, `mkUInteger`, `mkUString`,
`mkUBoolean`, `mkSBoolean`) returns its concrete class. There are 25 `mkUReal()` call sites in
`<fork>/src/main` and 53 more lines in `<fork>/src/test`; the only places the *class* `URealType`
is named outside its own file are `TypeFactory.java:48` and `TypeTest.java:392-393`
(`URealType urt1 = new URealType();`), so no call site depends on the narrow return type. The port
may narrow `mkUReal()` to `URealType` for symmetry; that is source-compatible with every fork call
site. Note that `TypeTest.java:392-393` constructs `URealType` directly, so the ported constructor
must stay accessible from the same package.

### 2.3 New `buildInTypesMap` entries (C, fork lines 60-67)

`buildInTypesMap` is the **parser's** name → type resolution table; its only consumer is
`ASTSimpleType.java:47` in 7.5.0 / `:45` in the fork (`TypeFactory.mkSimpleType(name)`).
The fork registers five names:

| key | value | fork line |
|---|---|---|
| `"UInteger"` | `uIntegerType` | 60 |
| `"UString"` | `uStringType` | 63 |
| `"SBoolean"` | `sBooleanType` | 64 |
| `"UBoolean"` | `uBooleanType` | 65 |
| `"UReal"` | `uRealType` | 67 |

Registering these is what makes `UReal`, `UBoolean`, … usable as type names in `.use` files
(O: `mkSimpleType("UReal") == mkUReal()`). `mkSimpleType` itself is unchanged.

No other `TypeFactory` member is touched: `mkEnum`, `mkCollection`, `mkSet`, `mkSequence`,
`mkBag`, `mkOrderedSet`, `mkMessageType`, `mkOclAny`, `mkVoidType`, `mkTuple`, `mkSimpleType`
are byte-identical.

---

## 3. THE SHAPE DELTA — 4.1.x-era fork base vs upstream 7.5.0

Before anything else, one correction to the working assumption behind the port plan. The fork's
type package is **not** a pre-`TypeImpl`, `Type`-as-a-class design. Its `Type.java` carries
`// $Id: Type.java 5494 2015-02-05 12:59:25Z lhamann $` and already declares
`public interface Type extends BufferedToString` (fork `Type.java:33`), with
`public abstract class TypeImpl implements Type` (fork `TypeImpl.java:34`). 7.5.0 has exactly the
same two declarations (`Type.java:31`, `TypeImpl.java:32`). **The interface/impl split is not a
delta.** The real deltas are narrower and are enumerated below.

### 3.1 `Type`: class vs interface, and `TypeImpl` presence

| | fork base | 7.5.0 | adaptation |
|---|---|---|---|
| `Type` | `public interface Type extends BufferedToString` (`Type.java:33`) | identical (`Type.java:31`) | none |
| `TypeImpl` | present, `public abstract class TypeImpl implements Type` (`TypeImpl.java:34`) | present, identical (`TypeImpl.java:32`) | none |
| `BasicType` | `public abstract class BasicType extends TypeImpl` (`BasicType.java:31`) | identical (`BasicType.java:28`) | none |
| `TypeImpl` size | 386 lines | 347 lines | difference is exactly the 10 uncertainty no-ops (fork only) minus the 2 DataType no-ops (7.5.0 only) |

**Consequence for the port:** `UncertainType extends BasicType` compiles unchanged against 7.5.0.
The five new leaf classes need no structural change at all — only the `Type` interface they
implement has moved (§3.2, §3.8).

### 3.2 How `conformsTo` is dispatched

Unchanged in shape: `boolean conformsTo(Type other)` declared on the interface
(fork `Type.java:54`, 7.5.0 `Type.java:58`); dispatch is ordinary Java virtual dispatch on the
receiver; there is **no** double dispatch, no visitor, no table. The *asymmetric* consequence is
what the port must respect: `A.conformsTo(B)` consults only `A`'s code plus `B`'s `isTypeOf*` /
`isKindOf*` answers. Therefore a new supertype relation can be installed from **either** side:

* from the subtype's side — `BooleanType.conformsTo` naming `other.isTypeOfUBoolean()`
  (an edit to an upstream file), or
* from the supertype's side — `UIntegerType.isKindOfNumber() → true`, which makes
  `IntegerType.conformsTo` accept it with **no edit to `IntegerType.conformsTo`**.

The fork uses both idioms. §4 records which is which, because the second kind is invisible in a
diff of `IntegerType.conformsTo` and is easy to lose.

### 3.3 Generics on `Type`

`Type` is **not** generic in either tree; there are no type parameters to reconcile. The only
generic signature is `Set<? extends Type> allSupertypes()` (fork `Type.java:59`,
7.5.0 `Type.java:63`), identical in both.

Return-type narrowing differs *within* the fork and must be replicated deliberately:
the upstream basic types declare `public Set<Type> allSupertypes()` (`BooleanType.java:71`,
`IntegerType.java:79`, `RealType.java:69`, `StringType.java:66`), whereas the five new
uncertainty types declare `public Set<? extends Type> allSupertypes()`
(`UBooleanType.java:33`, `UIntegerType.java:39`, `URealType.java:33`, `UStringType.java:23`,
`SBooleanType.java:23`). **Adaptation:** keep 7.5.0's `Set<Type>` on the edited upstream files
(narrowing them would break callers that assign to `Set<Type>`), and use
`Set<? extends Type>` on the new files, exactly as the fork does.

### 3.4 The `MClassifier` relationship

| | fork | 7.5.0 | adaptation |
|---|---|---|---|
| `MClassifier` | `public interface MClassifier extends Type, MModelElement, MNamedElement, UseFileLocatable` (`MClassifier.java:36`) | identical (`MClassifier.java:36`) | none |
| `MClassifierImpl` | `public abstract class MClassifierImpl extends MModelElementImpl implements MClassifier` (`:40`) | identical (`:34`) | none |
| subclasses | `MClassImpl`, `MAssociationImpl` | `MClassImpl:42`, `MAssociationImpl:42`, **`MDataTypeImpl:32`** (new in 7.5.0) | `MDataTypeImpl extends MClassifierImpl`, so it inherits the new no-ops for free |
| `conformsTo` | `MClassifierImpl.conformsTo` — **unedited by the fork** | `MClassifierImpl.java:121-130` | none; classifiers still conform only to themselves, their ancestors, and `OclAny` |
| predicate battery | fork adds 10 no-op overrides (`MClassifierImpl.java:200-206, 220-226, 260-276, 400-406`) | absent | **must be added** |

This is the load-bearing item of the whole section: `Type` is an interface, and it has **exactly
two implementation roots** in 7.5.0 —
`TypeImpl` (`use-core/.../ocl/type/TypeImpl.java`) and `MClassifierImpl`
(`use-core/.../uml/mm/MClassifierImpl.java`). Verified by
`grep -rn "public boolean isTypeOfOclAny()" --include=*.java .` over the whole repo, which returns
exactly three hits: `TypeImpl.java:313`, `MClassifierImpl.java:355`, `OclAnyType.java:33`.
Therefore **every method added to the `Type` interface must be given a default `false`
implementation in both `TypeImpl` and `MClassifierImpl`, or the build breaks** — including
`use-gui`, which has no `Type` implementor of its own but compiles against the interface.

### 3.5 `getLeastCommonSupertype`

| | fork | 7.5.0 | adaptation |
|---|---|---|---|
| declaration | `Type getLeastCommonSupertype(Type other)` (`Type.java:66`) | identical (`Type.java:70`) | none |
| `TypeImpl` body | lines 80-146 | lines 83-149, **logically identical** (verified by `diff --strip-trailing-cr`, whose only hunks in this file are the uncertainty no-ops and the DataType no-ops) | **no edit needed** |
| `MClassifierImpl` body | lines 152-… | lines 139-… — differs only by a for-loop → `addAll` refactor at 7.5.0 `:172-176` | none |
| behaviour | uncertainty-aware | classic | acquired *for free* from the edited `allSupertypes()` (§1.5) |

Because `getLeastCommonSupertype` intersects `allSupertypes()`, one subtlety changes the code path
without changing the answer: 7.5.0 short-circuits when the intersection is `{X, OclAny}`
(`TypeImpl.java:123-127`); with the fork's larger supertype sets the intersection is often size 3+
and the general loop at `:132-146` runs instead. The answers are the same (O: the LCS block for
classical types is identical), but the port must not "optimise" that loop away.

### 3.6 `UniqueLeastCommonSupertypeDeterminator`

Byte-identical between fork and 7.5.0 apart from a deleted `// $Id$` line. **No edit needed.**
It is a plain class (not an interface, not injected): `new UniqueLeastCommonSupertypeDeterminator()`
then `calculateFor(Set<Type>)` (7.5.0 `:30-71`). It calls only `isVoidOrElementTypeIsVoid()`,
`allSupertypes()` and `conformsTo()`, so it inherits uncertainty behaviour automatically once
§1.4/§1.5 are in place (verified in §1.6).

### 3.7 Any visitor over types

**There is none, in either tree.** `grep -rn "TypeVisitor"` over `use-core` returns nothing;
`grep -rn "visit(.*Type "` over `use-core` and `use-gui` returns nothing. The visitors that do
exist — `MMVisitor`/`MMPrintVisitor` (`use-core/.../uml/mm/`) and the `ExpressionVisitor` family
(`use-core/.../ocl/expr/`) — dispatch over *model elements* and *expressions*, never over
`Type` subclasses. Since `UBooleanType` & co. are not `MClassifier`s and not `Expression`s,
**no visitor needs a new case.** Type discrimination in this codebase is done exclusively by the
`isTypeOf*`/`isKindOf*` predicate battery plus `instanceof UncertainType`.

### 3.8 The `VoidType` / `BooleanType` / `IntegerType` touchpoints

> The task brief refers to touchpoints "named in the port plan". **UNVERIFIABLE:** no port-plan
> document exists in this repository — `docs/port2/` contains only `stage-00-baseline.md`, and
> `grep -i "VoidType\|BooleanType\|IntegerType"` over it returns nothing. What follows is derived
> from the fork↔7.5.0 diff, not from a plan.

**`VoidType`** — 7.5.0 `use-core/.../ocl/type/VoidType.java` (131 lines).
4.1.x-era fork: 151 lines (`wc -l`). The fork adds five `isKindOf*` overrides, each
`return h == VoidHandling.INCLUDE_VOID;` — `isKindOfUInteger` (fork `:37-40`),
`isKindOfUBoolean` (`:57-60`), `isKindOfUReal` (`:122-125`), `isKindOfSBoolean` (`:127-130`),
`isKindOfUString` (`:132-135`).
7.5.0 additionally has `isKindOfDataType` (`:92-95`) which the fork lacks; leave it alone.
Insertion points in 7.5.0: after `isKindOfInteger` (`:32-35`), after `isKindOfBoolean` (`:52-55`),
and before `isTypeOfVoidType` (`:117-120`).
**Minimal behavioural change:** `OclVoid` answers `true` to the five new `isKindOf*` queries only
under `INCLUDE_VOID`. Nothing else about `VoidType` moves — in particular `conformsTo` stays
`return true` and `allSupertypes()` keeps throwing.

**`BooleanType`** — 7.5.0 `use-core/.../ocl/type/BooleanType.java`, class body `:30-63`, `final class`.
Three edits (fork `:49-57`, `:62-65`, `:70-78`):
1. add `isKindOfUBoolean(h) → true`;
2. add `isKindOfSBoolean(h) → true`;
3. `conformsTo`: 7.5.0 `this.equals(other) || other.isTypeOfOclAny()` →
   `this.equals(other) || other.isTypeOfUBoolean() || other.isTypeOfOclAny() || other.isTypeOfSBoolean()`;
4. `allSupertypes`: add `TypeFactory.mkUBoolean()` and `TypeFactory.mkSBoolean()`
   (7.5.0 line 60-61 region). Note the fork leaves the `new HashSet<Type>(2)` capacity hint at 2
   while inserting four elements — harmless, but do not copy it as if it were meaningful.
**Minimal behavioural change:** `Boolean ≤ UBoolean` and `Boolean ≤ SBoolean`; nothing else.

**`IntegerType`** — 7.5.0 `use-core/.../ocl/type/IntegerType.java`, class body `:29-73`, `final class`.
Two edits (fork `:57-65`, `:79-87`):
1. add `isKindOfUReal(h) → true` and `isKindOfUInteger(h) → true`;
2. `allSupertypes`: add `TypeFactory.mkUReal()` and `TypeFactory.mkUInteger()`, capacity 3 → 5.
**`conformsTo` is NOT edited** — `Integer ≤ UInteger` and `Integer ≤ UReal` arise entirely from
`UIntegerType.isKindOfNumber` / `URealType.isKindOfNumber` returning `true` (§3.2). A reviewer
looking only at `IntegerType.conformsTo` will see no change and must not "fix" it.

Two further touchpoints the brief does not name but which are symmetric and equally required:
**`RealType`** (add `isKindOfUReal → true`; `conformsTo` gains `|| t.isTypeOfUReal()`;
`allSupertypes` gains `mkUReal()` — 7.5.0 `:46-49`, `:54-56`, `:61-66`) and
**`StringType`** (add `isKindOfUString → true`; `conformsTo` gains `|| t.isTypeOfUString()`;
`allSupertypes` gains `mkUString()` — 7.5.0 `:41-44`, `:49-51`, `:56-61`).
`UnlimitedNaturalType` and `OclAnyType` are **not** edited by the fork (see §1.8 defect 1).

### 3.9 `qualifiedName()` — a 7.5.0-only interface method

7.5.0 adds `String qualifiedName();` to `Type` (`Type.java:44-48`), defaulted in
`TypeImpl.java:43-46` (`return toString();`) and overridden in
`MClassifierImpl.java:389-392` (`model.name() + "#" + name()`). The fork's `Type` has no such
method. **Adaptation:** the five new type classes inherit `TypeImpl.qualifiedName()` and need no
code; but any *new* class that implements `Type` directly (there should be none) would.

### 3.10 `isKindOfDataType` / `isTypeOfDataType` — a 7.5.0-only pair

7.5.0 adds these to `Type` (`Type.java:136-138`), `TypeImpl` (`:287-295`),
`MClassifierImpl` (`:325-333`) and `VoidType` (`:92-95`), backed by the new `MDataType` /
`MDataTypeImpl`. The fork has none of it. **Adaptation:** interleave, do not overwrite. When the
port inserts the ten uncertainty predicates into `Type`, `TypeImpl` and `MClassifierImpl`, the
DataType pair must survive. A naive "copy the fork's file over" would silently delete
`isKindOfDataType`, `isTypeOfDataType`, `qualifiedName`, `isQualifiedAccess`/`setQualifiedAccess`,
`hasStateMachineWhichHandles`, the rename `isSubClassOf` → `isSubClassifierOf`, and (in
`MClassifierImpl`) the whole attribute/operation table that 7.5.0 pulled up from `MClassImpl` —
fields at `MClassifierImpl.java:54-61`, methods `attribute`/`attributes`/`allAttributes`/
`operation`/`operations` at `:491-573`, none of which exists in the fork's 511-line version.
**File-level copying from the fork is forbidden for every file marked "edit" in §4.**

### 3.11 Test-suite shape

| | fork | 7.5.0 |
|---|---|---|
| framework | `junit.framework.TestCase` (JUnit 3) | same, `TypeTest.java:47` |
| equality helper | `com.gargoylesoftware.base.testing.EqualsTester` (`TypeTest.java:38`) | `com.google.common.testing.EqualsTester` (Guava) |
| `TypeTest` size | 2074 lines, 47 test methods | 1423 lines, 39 test methods |
| fork-only methods | `testIsTypeOfSBooloean`, `testIsKindOfSBoolean`, `testIsTypeOfUBooloean`, `testIsKindOfUBoolean`, `testIsTypeOfUInteger`, `testIsKindOfUInteger`, `testIsTypeOfUReal`, `testIsKindOfUReal`, `testIsTypeOfUString`, `testIsKindOfUString` (note the historical `Booloean` typos — keep them or rename, but decide) | — |
| 7.5.0-only methods | — | `testIsTypeOfDataType`, `testIsKindOfDataType`; `isTypeOfDataType()` appears on 17 lines of the file (`grep -c`), i.e. inside most existing `testIsTypeOf*` blocks |

Each of the ~39 existing `testIsTypeOfX` / `testIsKindOfX` methods is an exhaustive
assert-every-predicate block (e.g. 7.5.0 `TypeTest.java:313-333`). Adding ten predicates to `Type`
means **every one of those methods needs ten more `assertFalse` lines**, exactly as the fork did
(fork `TypeTest.java:462-466` shows the five `isTypeOf*` additions appended to
`testIsTypeOfBag`). This is mechanical but large; budget for it.

---

## 4. File manifest — new file vs edit to an upstream file

Target paths are under `use-core/src/main/java/org/tzi/use/`. "Fork source" is the corresponding
file under `<fork>/src/main/org/tzi/use/`.

### 4.1 New files (7)

| # | target path | fork source | content |
|---|---|---|---|
| 1 | `uml/ocl/type/UncertainType.java` | same | **new file.** `public abstract class UncertainType extends BasicType`, one `protected UncertainType(String t)`. 16 lines. Pure `instanceof` tag. |
| 2 | `uml/ocl/type/UncertainBooleanType.java` | same | **new file.** `public abstract class UncertainBooleanType extends UncertainType`, one protected ctor. 10 lines. |
| 3 | `uml/ocl/type/UIntegerType.java` | same | **new file.** `isKindOfNumber`, `isTypeOfUInteger`, `isKindOfUReal`, `isKindOfUInteger` → `true`; `allSupertypes` = `{this, UReal, OclAny}`; `conformsTo` per §1.4. Make ctor package-private (fork has it `public`). |
| 4 | `uml/ocl/type/URealType.java` | same | **new file.** `isTypeOfUReal`, `isKindOfUReal`, `isKindOfNumber` → `true`; `allSupertypes` = `{this, OclAny}`; `conformsTo` = `equals(t) \|\| t.isTypeOfOclAny()`. |
| 5 | `uml/ocl/type/UStringType.java` | same | **new file.** `isTypeOfUString`, `isKindOfUString` → `true`; `allSupertypes` = `{UString, OclAny}`. |
| 6 | `uml/ocl/type/UBooleanType.java` | same | **new file.** `isKindOfOclAny`, `isKindOfUBoolean`, `isKindOfSBoolean`, `isTypeOfUBoolean` → `true`; `allSupertypes` = `{UBoolean, SBoolean, OclAny}`; `conformsTo` accepts self, `OclAny`, `SBoolean`. |
| 7 | `uml/ocl/type/SBooleanType.java` | same | **new file.** `isTypeOfSBoolean`, `isKindOfSBoolean` → `true`; `allSupertypes` = `{SBoolean, OclAny}`. |

### 4.2 Edits to upstream files (9)

| # | target path | minimal behavioural change | 7.5.0 anchor lines |
|---|---|---|---|
| 1 | `uml/ocl/type/Type.java` | **edit.** Declare 10 new predicates: `isTypeOfUInteger`, `isKindOfUInteger(VoidHandling)`, `isTypeOfUReal`, `isKindOfUReal(VoidHandling)`, `isTypeOfUString`, `isKindOfUString(VoidHandling)`, `isTypeOfUBoolean`, `isKindOfUBoolean(VoidHandling)`, `isTypeOfSBoolean`, `isKindOfSBoolean(VoidHandling)`. No other member changes; `qualifiedName` (`:48`) and the DataType pair (`:136-138`) stay. | insert after `:86`, `:90`, `:98`, `:102` |
| 2 | `uml/ocl/type/TypeImpl.java` | **edit.** 10 no-op `return false;` overrides for the same 10 predicates. No change to `conformsTo`, `getLeastCommonSupertype`, `shortName`, `qualifiedName`, `toString`, or the DataType no-ops. | insert around `:175`, `:185`, `:195`, `:215` |
| 3 | `uml/ocl/type/TypeFactory.java` | **edit.** 5 interned `static final` fields, 5 `mk*` methods, 5 `buildInTypesMap` entries (§2). Nothing else. | fields `:42-48`; static block `:50-58`; methods `:66-84` |
| 4 | `uml/ocl/type/BooleanType.java` | **edit.** `Boolean ≤ UBoolean` and `Boolean ≤ SBoolean`: add 2 `isKindOf*`, widen `conformsTo` by 2 disjuncts, add 2 `allSupertypes` entries. | `:41-44`, `:49-52`, `:57-63` |
| 5 | `uml/ocl/type/IntegerType.java` | **edit.** Add `isKindOfUReal → true` and `isKindOfUInteger → true`; add `mkUReal()`, `mkUInteger()` to `allSupertypes` (capacity 3 → 5). **`conformsTo` untouched.** | `:50-53`, `:66-73` |
| 6 | `uml/ocl/type/RealType.java` | **edit.** `Real ≤ UReal`: add `isKindOfUReal → true`; `conformsTo` gains `\|\| t.isTypeOfUReal()`; `allSupertypes` gains `mkUReal()`. | `:46-49`, `:54-56`, `:61-66` |
| 7 | `uml/ocl/type/StringType.java` | **edit.** `String ≤ UString`: add `isKindOfUString → true`; `conformsTo` gains `\|\| t.isTypeOfUString()`; `allSupertypes` gains `mkUString()`. | `:41-44`, `:49-51`, `:56-61` |
| 8 | `uml/ocl/type/VoidType.java` | **edit.** 5 new `isKindOf*` overrides, each `return h == VoidHandling.INCLUDE_VOID;`. `conformsTo`, `allSupertypes`, `isTypeOfVoidType`, `isVoidOrElementTypeIsVoid`, `isKindOfDataType` untouched. | after `:35`, after `:55`, before `:117` |
| 9 | `uml/mm/MClassifierImpl.java` | **edit.** 10 no-op `return false;` overrides for the 10 new predicates, so `MClass`/`MDataType`/`MAssociation` answer "no" to every uncertainty query. **`conformsTo` (`:121-130`), `allSupertypes` (`:132-137`), `getLeastCommonSupertype` (`:139-…`) untouched.** | after `isKindOfInteger` (`:205-208`), after `isKindOfUnlimitedNatural` (`:215-218`), after `isTypeOfBoolean` (`:245-248`), and before `toString(StringBuilder)` (`:380`) — mirroring the fork's placement |

### 4.3 Explicitly NOT changed (verified by `diff --strip-trailing-cr`, fork vs 7.5.0)

`BasicType.java`, `OclAnyType.java`, `UnlimitedNaturalType.java`,
`UniqueLeastCommonSupertypeDeterminator.java`, `EnumType.java`, `CollectionType.java`,
`SetType.java`, `SequenceType.java`, `BagType.java`, `OrderedSetType.java`, `TupleType.java`,
`MessageType.java`, `MClassifier.java` — under `diff -u --strip-trailing-cr` the only hunks are
removed `$Id$`/`$ProjectVersion$` tags, import reordering, and one javadoc `<` → `&lt;` escape;
`MessageType.java` produces no hunks at all (it differs from 7.5.0 only in line endings — the fork
tree is LF, `use-core` is CRLF, so `cmp` reports a difference at byte 3 while
`diff --strip-trailing-cr` reports none). Collection conformance for uncertain element types
therefore requires **zero** collection-type edits (§1.6).

### 4.4 Test files

| target path | kind |
|---|---|
| `use-core/src/test/java/org/tzi/use/uml/ocl/type/TypeTest.java` | **edit.** Port the fork's 10 new test methods and append the 10 new `assertFalse` lines to each of the ~39 existing predicate methods (§3.11). Keep the 7.5.0 `com.google.common.testing.EqualsTester` import; the fork's `com.gargoylesoftware…` one is not on the 7.5.0 classpath. |

---

## 5. Decisions this section forces

1. **`UnlimitedNatural` vs the uncertain numerics** (§1.8-1) — reproduce the fork's inconsistency
   or fix it? Recommended: reproduce, and add a regression test pinning
   `UnlimitedNatural.getLeastCommonSupertype(UInteger) == OclAny` so the deviation is visible.
2. **`mkUReal()` return type** — `Type` (fork) or `URealType` (symmetric)? Narrowing is
   source-compatible with all 27 fork call sites.
3. **`UIntegerType()` constructor visibility** — fork has it `public`, all siblings package-private.
4. **`allSupertypes` idiom** — `this` vs `TypeFactory.mkX()`; the fork mixes both.
5. **Test method names** — the fork's `testIsTypeOfUBooloean` / `testIsTypeOfSBooloean` typos.
6. **Predicate naming** — should the port add an `isKindOfUncertainType()` predicate to replace the
   five `instanceof UncertainType` sites (§1.1)? That would be a *deviation* from the fork and must
   be recorded as such; the historical oracle does not require it.

---

## 6. Reproduction

```bash
cd /home/xoruser/msc-4/use-msc2026

# (a) the conformance / supertype / LCS oracle, fork and 7.5.0 side by side,
#     ending in the assertion that the classic 7x7 block is unchanged.
#     Read-only; no Maven; nothing written outside the workdir it prints.
bash docs/port2/spec-parts/11-types-oracle.sh

# (b) which type files the fork adds vs edits
diff -rq \
  .git/reference-repositories/uncertainty/USE-Uncertainty/src/main/org/tzi/use/uml/ocl/type/ \
  use-core/src/main/java/org/tzi/use/uml/ocl/type/

# (c) the exact per-file behavioural delta (CRLF-normalised)
for f in Type TypeImpl TypeFactory BooleanType IntegerType RealType StringType VoidType; do
  echo "===== $f ====="
  diff -u --strip-trailing-cr \
    .git/reference-repositories/uncertainty/USE-Uncertainty/src/main/org/tzi/use/uml/ocl/type/$f.java \
    use-core/src/main/java/org/tzi/use/uml/ocl/type/$f.java
done

# (d) the one Type implementor outside the type package
diff -u --strip-trailing-cr \
  .git/reference-repositories/uncertainty/USE-Uncertainty/src/main/org/tzi/use/uml/mm/MClassifierImpl.java \
  use-core/src/main/java/org/tzi/use/uml/mm/MClassifierImpl.java

# (e) proof that Type has exactly two implementation roots in 7.5.0
grep -rn "public boolean isTypeOfOclAny()" --include=*.java use-core use-gui

# (f) proof that no visitor dispatches over Type subclasses
grep -rn "TypeVisitor" --include=*.java use-core use-gui ; echo "exit=$?  (1 = no hits)"

# (g) the fork's own oracle assertions for the lattice
sed -n '81,290p' \
  .git/reference-repositories/uncertainty/USE-Uncertainty/src/test/org/tzi/use/uml/ocl/type/TypeTest.java
```
