# Adaptation — Part 02: value classes and the `Value` contract

**Governing policy (the user's, applied here, not debated):**

> Uncertainty meaning comes from the fork. Everything else comes from USE 7.5.0.
> Where the two collide, keep the uncertainty behaviour but express it the 7.5.0 way.

**Scope.** The seven new uncertain value classes, the three upstream `org.tzi.use.uml.ocl.value`
files that must be edited, and `util/MathUtil`. Types (`URealType`, `TypeFactory`, the lattice),
expressions and the `.in` harness belong to other parts and are cited here only where a value
class hard-depends on them.

**Method.** Everything numeric or textual below was **executed**, not reasoned. Ten probe drivers
were compiled and run: nine against the historical jars, one against 7.5.0's built classes.
Statements that were read from source rather than executed are marked
**READ_FROM_SOURCE**; statements that could not be settled are marked **UNVERIFIABLE**.

## 0. How to reproduce every number in this document

```sh
L=/home/xoruser/msc-4/use-msc2026/.git/reference-repositories/uncertainty/USE-Uncertainty/lib
CP="$L/use.jar:$L/atenearesearchgroup.uncertainty.jar:$L/antlr-3.4-complete.jar"
javac -nowarn -cp "$CP" -d out P1.java && java -cp "out:$CP" P1        # fork side

T=/home/xoruser/msc-4/use-msc2026/use-core/target/classes
M=/home/xoruser/.m2/repository
CP75="$T:$M/org/antlr/antlr-runtime/3.5.3/antlr-runtime-3.5.3.jar:\
$M/com/google/guava/guava/33.6.0-jre/guava-33.6.0-jre.jar:$M/jline/jline/2.14.6/jline-2.14.6.jar"
javac -nowarn -cp "$CP75" -d out75 Q1.java && java -cp "out75:$CP75" Q1   # 7.5.0 side
```

The drivers used in this session live in `/tmp/probe2/` (scratch, outside the repo):
`P1` printed forms · `P2` equals/hashCode/compareTo/uEquals + delegate `toString` ·
`P3` end-to-end OCL compile+eval (fork) · `P4` collection u-ops, set ordering, SBoolean sorting ·
`P5` simulated post-F-10 hash (subclass of `UIntegerValue`) · `P6` undefined-in-collection ·
`P7` replay of the four order-pinning corpus entries · `P8`/`P9` the eight high-precision UBoolean
corpus entries · `PA` the 48-point `UIntegerValue.hashCode` sweep · `Q1` the 7.5.0 control.

**Harness validation.** Before trusting any measurement, `P7` replayed the four corpus entries
whose expectations pin collection print order. All four reproduced byte-for-byte:

```
Set{UReal(2, 0.5), 2.5, 3.2, 1, UReal(3, 0.25)}->uSelect(e | e >= 2)
-> Set{2.5,UReal(3.0, 0.25),3.2} : Set(UReal)          [corpus UCollectionOperations.in:139-140]
Set{UReal(2, 0.5), 2.5, 3.2, 1, UReal(3, 0.25)}->uSelectC(e | e >= 2, 0.49)
-> Set{2.5,UReal(3.0, 0.25),3.2,UReal(2.0, 0.5)} : Set(UReal)   [.in:160-161]
```

So the probe rig is the same oracle the corpus was (mostly) written against — which is what makes
§7's eight-row exception a finding rather than a rig failure.

---

## 1. The `Value` contract: measured, nothing moved

The single most useful negative result in this part.

```
$ diff -u use-core/src/main/java/org/tzi/use/uml/ocl/value/Value.java \
         .git/reference-repositories/.../src/main/org/tzi/use/uml/ocl/value/Value.java
```

The diff contains exactly four behavioural hunks — the insertion of `isUInteger()`, `isUReal()`,
`isUBoolean()`, `isSBoolean()`, each `public boolean` returning `false` — plus a `$Id:` line and a
`@version $ProjectVersion: 0.393 $` javadoc tag. **Nothing was removed, renamed, re-typed or made
abstract between the 2015 fork and 7.5.0.**

7.5.0's surface, from `use-core/src/main/java/org/tzi/use/uml/ocl/value/Value.java`:

| # | Member | Line | Kind | Adaptation needed |
|---|---|---|---|---|
| C1 | `protected Value(Type t)` | :40 | ctor to chain | none |
| C2 | `int compareTo(Value)` via `implements Comparable<Value>` | :36 | abstract obligation | none — signature identical |
| C3 | `abstract StringBuilder toString(StringBuilder)` via `BufferedToString` | :160 | abstract obligation | none |
| C4 | `abstract int hashCode()` | :175 | abstract obligation | none |
| C5 | `abstract boolean equals(Object)` | :183 | abstract obligation | none |
| C6–C7 | `type()`, `getRuntimeType()` | :44,:48 | inherited | none |
| C8–C20 | `isInteger` … `isLink` | :56–:148 | inherited `false` | none |
| C21 | `final String toString()` | :153 | **cannot be overridden** | none |
| C22 | `toStringWithType()` / `(StringBuilder)` | :162,:168 | inherited | none |
| C23 | `setTypeToRuntimeType()` | :190 | inherited | none |
| C24–C27 | `isUInteger`/`isUReal`/`isUBoolean`/`isSBoolean` | — | **absent in 7.5.0** | add four `false` defaults |

**Consequence for the port.** There is *no* signature adaptation work in this area. Every one of
the seven historical value classes satisfies 7.5.0's `Value` obligations with the same method
signatures it already has. The only mechanical change is that the four `@Override`s on
`isUInteger`/`isUReal`/`isUBoolean`/`isSBoolean` need §5.1's edit to become legal.

Per-class contract status (all **READ_FROM_SOURCE**, and all confirmed to link and run by the
probes, which instantiate every class):

| Class | C2 | C3 | C4 | C5 | C24–C27 |
|---|---|---|---|---|---|
| `UncertainValue` (abstract) | left abstract | left abstract | left abstract | left abstract | — |
| `UncertainBooleanValue` (abstract) | left abstract | left abstract | left abstract | left abstract | — |
| `UBooleanValue` | `:251` | `:192` | `:208` | `:224` | C24 `:179` |
| `UIntegerValue` | `:94` | `:46` | `:57` | `:67` | C25 `:30` |
| `URealValue` | `:95` | `:42` | `:56` | `:67` | C26 `:37` |
| `UStringValue` | `:95` | `:67` | `:74` | `:79` | **none** — there is no `isUString()` and none is wanted |
| `SBooleanValue` | `:151` | `:124` | `:133` | `:138` | C27 `:95` |

---

## 2. The printed form

### 2.1 Measured: what every uncertain value prints

`Value.toString()` is `final` at 7.5.0 `:153`, so the printed form is entirely decided by each
class's `toString(StringBuilder)`. Measured through the historical jars (driver `P1`):

| Construction | `toString()` | `toStringWithType()` |
|---|---|---|
| `new URealValue(2.0, 0.5)` | `UReal(2.0, 0.5)` | `UReal(2.0, 0.5) : UReal` |
| `new URealValue(2.0, 0.0)` | `UReal(2.0, 0.0)` | |
| `new URealValue(-0.0, 0.0)` | `UReal(0.0, 0.0)` | negative zero explicitly corrected |
| `new URealValue(1.0/3, 0.5)` | `UReal(0.3333333333, 0.5)` | **10 decimals** |
| `new URealValue(2.0, -0.5)` | `UReal(2.0, 0.5)` | uncertainty absolutised |
| `new UIntegerValue(5, 0.5)` | `UInteger(5, 0.5)` | `… : UInteger` |
| `new UIntegerValue(5, -5.0)` | `UInteger(5, 5.0)` | |
| `new UIntegerValue(5, 1.0/3)` | `UInteger(5, 0.3333333333)` | **10 decimals** |
| `UBooleanValue.valueOf(true, 0.5)` | `UBoolean(true, 0.5)` | `… : UBoolean` |
| `UBooleanValue.valueOf(false, 1.0)` | `UBoolean(true, 0.0)` | false is encoded as confidence 0 |
| `UBooleanValue.valueOf(true, 1.0/3)` | `UBoolean(true, 0.333)` | **3 decimals** |
| `new UStringValue("abc", 0.5)` | `UString('abc', 0.5)` | `… : UString` |
| `new UStringValue("abc", 1.0/3)` | `UString('abc', 0.3333333333333333)` | **no rounding at all** |
| `SBooleanValue.Builder(0.5,0.3,0.2,0.5)` | `SBoolean(0.5, 0.3, 0.2, 0.5)` | `… : SBoolean` |
| `…Builder(1/3,1/3,1/3,1/3)` | `SBoolean(0.333, 0.333, 0.333, 0.333)` | **3 decimals** |
| `UndefinedValue.instance` | `Undefined` | `Undefined : OclVoid` |

The same strings come out of the full OCL path (driver `P3`), e.g.
`UReal(2,0.5)` → `UReal(2.0, 0.5) : UReal`, `UString('abc',0.5)` → `UString('abc', 0.5) : UString`,
`SBoolean(0.5,0.3,0.2,0.5)` → `SBoolean(0.5, 0.3, 0.2, 0.5) : SBoolean`.

**Port rule.** Emit these strings verbatim, including the three different rounding regimes
(UReal/UInteger 10 dp, UBoolean/SBoolean 3 dp, UString none) and including `UString`'s hard-coded
`UString('` prefix rather than `type()`. The inconsistency *is* the uncertainty behaviour; unifying
it would be a silent divergence. It also affects nothing in 7.5.0, which has no opinion on how a
U-value prints.

### 2.2 The `%5.3f` question — answered: it never reaches the user

`javap` on the jar confirms the delegates format with `%5.3f`:

```
$ javap -p -c -cp atenearesearchgroup.uncertainty.jar uDataTypes.UReal | grep 5.3f
       0: ldc  #88   // String UReal(%5.3f, %5.3f)
```

and the delegates really do print that way (driver `P2`):

```
new UReal(2,0.5).toString()             => UReal(2.000, 0.500)
new UInteger(5,0.5).toString()          => UInteger(5, 0.500)
new UBoolean(true,0.5).toString()       => UBoolean(true, 0.500)
new UString("abc",0.5).toString()       => UString(abc, 0.500)
new SBoolean(0.5,0.3,0.2,0.5).toString()=> SBoolean(0.500, 0.300, 0.200, 0.500)
```

**But no `org.tzi.use` code path stringifies a delegate.** Each `*Value.toString(StringBuilder)`
composes the text from its own accessors. The one place a raw delegate crosses a package boundary
is `StandardOperationsCollection.java:630`
(`new UIntegerValue(uisum.getuInteger().add(aux.getuInteger()))`), and that is arithmetic, not
printing (grep over the whole fork main source for `getuReal()|getuInteger()|getuBoolean()|wrapper`
outside the five value classes returns that one line plus five unrelated javadoc hits).

**Port rule.** Never route a printed form through a `uDataTypes` `toString()`. The `%5.3f` form
must not appear anywhere in the port's output. Note the trap: `UBoolean`'s *value-class* rounding
(3 dp) and the delegate's `%5.3f` (3 dp) coincide numerically but differ in text — `0.5` vs `0.500`.

### 2.3 `Undefined` → `null` (B6)

Measured, both sides:

| | fork (`use.jar`) | plain USE 7.5.0 (`use-core/target/classes`) |
|---|---|---|
| `UndefinedValue.instance.toString()` | `Undefined` | `null` |
| `…toStringWithType()` | `Undefined : OclVoid` | `null : OclVoid` |
| `1/0` | `Undefined : OclVoid` | `null : OclVoid` |
| `Sequence{}->first()` | `Undefined : OclVoid` | `null : OclVoid` |
| `Set{1, Undefined}` | `Set{Undefined,1}` | `Set{null,1}` |

The last row matters: the substitution is not confined to a top-level result. It happens inside
every collection, tuple and nested rendering, because `UndefinedValue.toString(StringBuilder)` is
the only producer. `grep -c -- '^-> Undefined'` across the four `.in` files totals **79**, matching
B6's stated count.

**Port rule (B6, already decided).** The port prints `null`. Nothing in the value classes needs to
change to achieve it — `UndefinedValue` is upstream and untouched. The 79 corpus entries are
normalised at comparison time by the harness, not by reverting the port. No uncertain value class
ever emits the literal `Undefined` itself.

---

## 3. Cross-type equality, hashing and ordering — measured

Driver `P2`, historical jars. `IntegerValue(2)` is written `2`, `RealValue(2.0)` is `2.0`.

### 3.1 `equals`

| Expression | fork answers | Port must answer | Why |
|---|---|---|---|
| `URealValue(2,0).equals(2)` | `true` | `true` | uncertainty meaning; keep |
| `URealValue(2,0).equals(2.0)` | `true` | `true` | keep |
| `2.equals(URealValue(2,0))` | `false` | `false` (residual, see V15) | `IntegerValue` is upstream |
| `2.0.equals(URealValue(2,0))` | `false` | `false` (residual) | |
| `UIntegerValue(2,0).equals(2)` | `true` | `true` | keep |
| `UIntegerValue(2,0).equals(2.0)` | `false` | `false` | UInteger↔Real bridge was never claimed |
| `UIntegerValue(2,0).equals(URealValue(2,0))` | **`false`** | **`true`** | **M-10 fix** |
| `URealValue(2,0).equals(UIntegerValue(2,0))` | **`false`** | **`true`** | **M-10 fix (the edit lands in `URealValue`)** |
| `UStringValue(abc,1).equals(itself)` | **`false`** | **`true`** | **M-11 fix** — reflexivity |
| `UStringValue(abc,1).equals(UStringValue(abc,1))` | **`false`** | **`true`** | **M-11 fix** |
| `UStringValue(abc,1).equals(StringValue(abc))` | `false` | **`true`** | **M-11 fix, cross-type side effect.** `equals` already lifts via `valueOf((Value) obj)` (`UStringValue.java:82`), which maps `StringValue(s)` to `UStringValue(s, 1.0)`; once the comparison is `wrapper.equals(ustring.wrapper)` the confidences match at `1.0`. `UStringValue(abc,0.5).equals(StringValue(abc))` stays `false` |
| `UBooleanValue.TRUE.equals(BooleanValue.TRUE)` | `true` | `true` | keep |
| `UBooleanValue.FALSE.equals(BooleanValue.FALSE)` | **`false`** | **`true`** | **M-8 fix** |
| `BooleanValue.TRUE.equals(UBooleanValue.TRUE)` | `false` | `false` (residual) | upstream `BooleanValue` |
| `SBooleanValue.TRUE.equals(BooleanValue.TRUE)` | `false` | `false` | no bridge is claimed |
| `SBooleanValue.TRUE.equals(UBooleanValue.TRUE)` | `false` | `false` | asymmetric with `uEquals`, deliberately (§3.4) |

### 3.2 `hashCode`

```
IntegerValue(1).hashCode()      => 1072693248
RealValue(1.0).hashCode()       => 1072693248
URealValue(1,0).hashCode()      => 1072693248     <- bridge intact
UIntegerValue(1,0).hashCode()   => 0              <- bridge broken (F-10)
UIntegerValue(5,0).hashCode()   => 0
UIntegerValue(5,0.5).hashCode() => 0              <- 43 of 48 probed combos hash to 0
UStringValue('abc',1).hashCode()=> 1075680222     (StringValue('abc') => 96354)
UBooleanValue.TRUE.hashCode()   => 1072693279     (BooleanValue.TRUE  => 1231)
SBooleanValue(.5,.5,0,.5)       => 50550
```

`UIntegerValue.hashCode` collapses to `0` far beyond the `u == 0` case the record names. The body
is `hash = Double.hashCode(value()); hash *= 7 * Double.hashCode(uncertainty());`, and both factors
carry large powers of two, so the 32-bit product is usually `0`. Swept over
`value` in `{0,1,2,3,7,-5,100,123456}` x `uncertainty` in `{0, 0.25, 0.5, 1.0, 3.3, 7.0}`
(driver `PA`): **43 of 48 combinations hash to `0`**, including every `u == 0` case and every
combination for `value` in `{0,1,2,3,7,-5,100}`. The five non-zero results all have
`value = 123456` and are themselves degenerate — `-1073741824`, `-2147483648`, `1073741824`,
`1342177280`, `-1879048192`, every one a multiple of 2^28.

### 3.3 `compareTo`

```
UIntegerValue(1,0).compareTo(URealValue(5,0))  => 0     <- should be negative
URealValue(5,0).compareTo(UIntegerValue(1,0))  => 0     <- should be positive
UIntegerValue(5,0).compareTo(UIntegerValue(1,0)) => 1
URealValue(5,0).compareTo(URealValue(2,0))     => 1
URealValue(2,0).compareTo(IntegerValue(2))     => 0
UIntegerValue(2,0).compareTo(RealValue(2.5))   => 0     <- Real lifted by C-truncation
URealValue(2,0).compareTo(UndefinedValue)      => 0     <- contract violation
UIntegerValue(2,0).compareTo(UndefinedValue)   => 0     <- contract violation
SBooleanValue.TRUE.compareTo(UndefinedValue)   => 0     <- contract violation
UStringValue('abc',1).compareTo(UndefinedValue)=> 1     <- correct
UBooleanValue.TRUE.compareTo(UndefinedValue)   => 1     <- correct
UndefinedValue.compareTo(URealValue(2,0))      => -1
URealValue(2,0).compareTo(StringValue('abc'))  => 0
SBooleanValue.TRUE.compareTo(SBooleanValue(.5,.5,0,.5)) => 0
UStringValue('abc',1).compareTo(StringValue('abc'))     => 12
StringValue('abc').compareTo(UStringValue('abc',1))     => -46
```

The `12` is `'a'(97) - 'U'(85)`: the fork compares the raw string `abc` against the *rendered*
form `UString('abc', 1.0)` (M-12). The `-46` is `'\''(39) - 'U'(85)`, the upstream
`StringValue.compareTo` falling back to `toString()`.

7.5.0's control (driver `Q1`): `IntegerValue(1).compareTo(UndefinedValue) => 1` and
`UndefinedValue.compareTo(IntegerValue(1)) => -1`. `Value.java:27-31` states the obligation
in prose: *"all values must be able of being compared with an `UndefinedValue`"*.

**Port must answer**, after B7:

| Expression | port |
|---|---|
| `UIntegerValue(1,0).compareTo(URealValue(5,0))` | **negative** (`-1`) |
| `URealValue(5,0).compareTo(UIntegerValue(1,0))` | **positive** (`+1`) |
| `URealValue(2,0).compareTo(UndefinedValue)` | **`+1`** |
| `UIntegerValue(2,0).compareTo(UndefinedValue)` | **`+1`** |
| `SBooleanValue.compareTo(UndefinedValue)` | **`+1`** |
| `SBooleanValue.TRUE.compareTo(SBooleanValue(.5,.5,0,.5))` | **positive**, by lexicographic `Double.compare` on `(b,d,u,a)` — **not** by delegating to `SBoolean.compareTo`, whose 0.001 dead band is non-transitive |
| `UStringValue('abc',1).compareTo(StringValue('abc'))` | **`0`** (M-12: compare `abc` against `abc`) |
| everything else in §3.3 | unchanged |

### 3.4 `uEquals` — the uncertainty-aware answer, unchanged

`uEquals` is uncertainty meaning end to end and carries none of the defects. Measured:

```
URealValue(2,0).uEquals(IntegerValue(2))    => UBoolean(true, 1.0)
URealValue(2,0).uEquals(UIntegerValue(2,0)) => UBoolean(true, 1.0)
UIntegerValue(2,0).uEquals(URealValue(2,0)) => UBoolean(true, 1.0)
URealValue(2,0).uEquals(StringValue('abc')) => UBoolean(true, 0.0)
UStringValue('abc',1).uEquals(StringValue('abc')) => UBoolean(true, 1.0)
UBooleanValue.TRUE.uEquals(BooleanValue.TRUE)     => UBoolean(true, 1.0)
SBooleanValue.TRUE.uEquals(UBooleanValue.TRUE)    => SBoolean(1.0, 0.0, 0.0, 1.0)
UBooleanValue.TRUE.uEquals(SBooleanValue.TRUE)    => UBoolean(true, 0.0)
```

The last pair is the deliberate asymmetry recorded at `spec-parts/10-values.md` §13 Tier 0
(`SBooleanType.isKindOfUBoolean()` is not overridden). **Reproduce it; it is not on the B7 list.**

Note `UStringValue.uEquals(StringValue)` answers `1.0` while `UStringValue.equals(StringValue)`
answers `false` — the OCL-level `=` and the Java-level `equals` are different questions in this
extension, and the port keeps them different.

---

## 4. Registered collisions

Each row is an intended departure or a deliberate reproduction. Every "fork" cell is measured
unless marked otherwise.

| id | where | fork behaviour | 7.5.0 behaviour | what the port does | conf |
|---|---|---|---|---|---|
| **V1** | `UndefinedValue.toString(StringBuilder)` | prints `Undefined`, incl. inside `Set{Undefined,1}` | prints `null`, incl. `Set{null,1}` | print `null`; normalise the 79 corpus rows at comparison time (B6) | MEASURED |
| **V2** | `Value.java` (both trees) | adds `isUInteger/isUReal/isUBoolean/isSBoolean` | lacks them; every other member is byte-identical | add exactly those four as non-abstract `false`; **no signature adaptation anywhere else** | MEASURED |
| **V3** | `U*Value.toString(StringBuilder)` | three rounding regimes: 10 dp (UReal, UInteger), 3 dp (UBoolean, SBoolean), none (UString) | no U-types | reproduce all three verbatim; do not unify | MEASURED |
| **V4** | `uDataTypes.*.toString()` | `%5.3f` → `UReal(2.000, 0.500)` | n/a | never reachable from `Value.toString()`; the port must keep it unreachable | MEASURED |
| **V5** | `UStringValue.java:67` | hard-codes `"UString('"` instead of `type()` | n/a | keep — the printed form is pinned by V3 | READ_FROM_SOURCE |
| **V6** | `URealValue:95`, `UIntegerValue:94`, `SBooleanValue:151` | `compareTo(UndefinedValue)` → `0` | `IntegerValue.compareTo(Undefined)` → `+1`; `Value.java:27-31` makes it mandatory | return `+1` from all three (`UBooleanValue`/`UStringValue` already do) | MEASURED |
| **V7** | `UStringValue.java:79-91` (M-11) | `equals` is constant `false`; `a.equals(a)` is `false`; `Set{UString('a',1),UString('a',1)}->size()` = **2**; `Bag{…}->count(…)` = **0** | `Value.java:183` requires OCL equality semantics | `wrapper.equals(ustring.wrapper)`; size becomes **1**, count becomes **2**, and `UString('x',1.0).equals(StringValue('x'))` becomes **`true`** because the existing `valueOf` lift at `:82` gives the `StringValue` confidence `1.0` | MEASURED |
| **V8** | `UIntegerValue.java:57-63` (F-10) | every `UIntegerValue` hashes to `0`; `HashSet{1, UInteger(1,0)}.size()` = **2** | `equals`/`hashCode` consistency | copy `URealValue:56-63`'s zero-guarded body; `HashSet{1, UInteger(1,0)}.size()` becomes **1** | MEASURED (simulated by subclass, `P5`) |
| **V9** | `UIntegerValue.java:103-104` (M-9) + `URealValue.java:95-110` | `UInteger(1,0).compareTo(UReal(5,0))` = `0` and the reverse = `0` | `Comparable` antisymmetry | negate the delegation **and** add a `UIntegerValue` arm to `URealValue.compareTo`; answers become `-1`/`+1`. M-9 alone is a no-op | MEASURED |
| **V10** | `UIntegerValue.java:84-86` (M-10) | `UInteger(2,0).equals(UReal(2,0))` = `false`, both directions | n/a | add a `UIntegerValue` arm to `URealValue.equals` lifting via `toUReal()`; both become `true`. Must land with F-3 | MEASURED |
| **V11** | `UBooleanValue.java:233-234` (M-8) | `UBoolean FALSE.equals(BooleanValue.FALSE)` = `false` (dead `&& !value()`) | `BooleanValue` bridge should be total | delete `&& !this.value()`; becomes `true` | MEASURED |
| **V12** | `SBooleanValue.java:151` (M-18) | `compareTo` is `return 0;` — equal to `UndefinedValue`, `StringValue`, everything. Measured: sorting 33 `SBooleanValue`s does **not** throw, because a constant `0` is a *consistent* comparator | 7.5.0 sorts with `Collections.sort` at `CollectionValue.java:169-173` | local total order: `Undefined → +1`; lexicographic `Double.compare` on `(b,d,u,a)`; else `toString().compareTo(...)`. **Do not delegate to `SBoolean.compareTo`** — its 0.001 dead band is non-transitive and *would* trip Java 21 TimSort | MEASURED |
| **V13** | `UStringValue.java:95-104` (M-12) | `UString('abc',1).compareTo(StringValue('abc'))` = `12` (raw vs rendered) | `StringValue.compareTo` compares characters | fix only the `StringValue` arm → `0`; leave the UString-vs-UString `toString()` route alone | MEASURED |
| **V14** | `CollectionValue.uIncludes/uExcludes/uCountC` | with `SBooleanValue` elements: `uIncludes` → `NullPointerException: Cannot invoke "…UBooleanValue.probability()" because "<local3>" is null`; `uExcludes` → `ClassCastException: A value kind of UBoolean expected`; `uCountC` → same NPE | n/a | widen the fold to `UncertainBooleanValue`, or document the restriction to UBoolean-kind elements. **Resolves `10-values.md` §15 gap 3, which was UNVERIFIABLE** | MEASURED |
| **V15** | asymmetric cross-type `equals` × `HashSet` | `Set{1, UReal(1,0)}` → **`Set{1}`**, size 1 — the UReal is silently **dropped**; `Set{UReal(1,0), 1}` → `Set{1,UReal(1.0, 0.0)}`, size 2. Membership depends on literal order | 7.5.0 has no cross-type numeric `equals`, so no asymmetry exists | **NOT on the 33-row B7 list.** Needs a user decision — see §7 gap 2. Doing nothing keeps order-dependent sets; the only symmetric fix edits upstream `IntegerValue`/`RealValue.equals`, which exceeds the recorded minimal edits | MEASURED |
| **V16** | `URealValue.java:56-64` vs `:67-91` (F-3) | `hashCode` hashes unrounded, `equals` compares rounded to 10 dp | contract requires consistency | round inside `hashCode` with the same `MathUtil.round(x,10)` | READ_FROM_SOURCE |
| **V17** | collection literals | `Set{UReal(2,0.5), 1, 2.5}` is `Set(UReal)` but holds a raw `IntegerValue` and `RealValue`: `Set{1,2.5,UReal(2.0, 0.5)}` | plain USE: compile error `Undefined operation 'UReal'` | keep — elements are **not** lifted; the lattice does the work at the type level only. Every value class must therefore keep accepting `IntegerValue`/`RealValue` operands | MEASURED |
| **V18** | corpus vs fork code | **eight** `.in` expectations print a UBoolean confidence with more than 3 decimals; the fork jar *and* the fork source both round to 3, so those eight cannot pass against the historical implementation | n/a | the port emits 3 decimals (fork code wins over fork corpus); pre-register the eight rows. See §7 gap 1 | MEASURED |

---

## 5. `Value`, `CollectionValue`, `RealValue`, `MathUtil` — the minimal 7.5.0 equivalent

### 5.1 `Value.java` — four predicates, nothing else

> `Value` must answer four more type-discrimination questions — "are you a UInteger / UReal /
> UBoolean / SBoolean?" — defaulting to *no*, exactly as it already does for `isInteger`,
> `isReal`, `isBoolean` and the collection predicates.

Four `public boolean` methods returning `false`. No field, no signature change, no new abstract
member — so **no existing 7.5.0 value class is touched**, including the two classes that did not
exist in 2015 (`DataTypeValueValue`, `InstanceValue`, both present in
`use-core/target/classes/org/tzi/use/uml/ocl/value/`). They inherit the new defaults.

**Do not add `isUString()`.** The fork did not, `UStringValue` does not need one (it discriminates
with `instanceof` at `:30-33`), and adding it would leave a predicate no class ever answers `true`
to.

### 5.2 `CollectionValue.java` — five methods, all concrete

> Every collection must additionally answer membership and counting questions *with a degree of
> confidence*: how confident it includes a value, includes all of a collection, excludes a value,
> excludes all of a collection, and how many elements match at or above a threshold.

Five public non-abstract members — `uIncludes(Value)`, `uIncludesAll(CollectionValue)`,
`uExcludes(Value)`, `uExcludesAll(CollectionValue)`, `uCountC(Value,double)` — so `SetValue`,
`BagValue`, `SequenceValue` and `OrderedSetValue` need **no change at all**. Verified by
`diff -u` of the two `CollectionValue.java`: the only other hunks are the `$Id:` line, a
`@version` tag, the import style (7.5.0 consolidated eight explicit imports into `java.util.*`),
one reworded javadoc sentence, and a trailing blank line. **Do not revert the 7.5.0 import
consolidation or javadoc wording** — the fork is merely older there.

Shared dispatch rule in all five: *if the element is an `UncertainValue`, ask the element; else if
the probe is an `UncertainValue`, ask the probe; else `UBooleanValue.valueOf(v.equals(elem), 1)`.*

Measured behaviour of that rule (driver `P4`):

```
Set(UReal){UReal(2,0)}.uIncludes(IntegerValue 2)   => UBoolean(true, 1.0)
Set(UReal){UReal(2,0)}.uExcludes(IntegerValue 2)   => UBoolean(true, 0.0)
Set(UReal){UReal(2,0)}.uCountC(IntegerValue 2,0.5) => 1
Set(Integer){2}.uIncludes(URealValue(2,0))         => UBoolean(true, 1.0)   <- probe-side branch
Set(SBoolean){TRUE}.uIncludes(SBoolean TRUE)       => NullPointerException  <- V14
Set(SBoolean){TRUE}.uExcludes(SBoolean TRUE)       => ClassCastException    <- V14
```

and through OCL, `Set{Undefined}->includes(UReal(2, 3))` → `UBoolean(true, 0.0)` — the
`UndefinedValue` element is handled correctly by the probe-side branch.

### 5.3 `RealValue.java` — one static lift

`SBooleanValue` calls `RealValue.valueOf(Value)` at `:258,:271,:284,:285,:290`; it exists only in
the fork. `diff -u` shows it is the sole behavioural difference between the two `RealValue.java`
files. Add it (additive, matches the historical shape) rather than inlining the coercion into
`SBooleanValue`.

### 5.4 `util/MathUtil.java` — `round(double,int)`

Required by all four rounding value classes (`UBooleanValue:197,240`; `UIntegerValue:51,75-76`;
`URealValue:48-50,77-80`; `SBooleanValue:125-128`). Port the body byte-identically first
(so the 10-decimal assertions can be shown green against the verbatim body), then fix the
`Math.round` saturation above `9.2e8` (F-2) in a **second** commit.

### 5.5 Packaging invariants that must not be broken

- `UBooleanValue(UBoolean)` `:42`, `valueOf(UBoolean)` `:74` and `getuBoolean()` `:148` are
  package-private and are used from `SBooleanValue` `:78,:192,:463`. Both classes **must** land in
  `org.tzi.use.uml.ocl.value`.
- `SBooleanValue`'s two constructors are package-private; `SBooleanValue.Builder` is the public
  cross-package entry point. Do not widen them — that creates a second, non-canonicalising
  construction path.

---

## 6. Corrections to the existing record

1. **`b7-fix-plan.md` §2, row F-10, Δoutput `NONE`** — the stated reason is *"`HashSet` still
   consults `equals`, so set contents are unchanged."* That reason is wrong in general, and
   measured to be wrong (`P5`):

   ```
   IntegerValue(1).hashCode()                            => 1072693248
   UIntegerValue(1,0).hashCode()   [today]               => 0
   FixedUInt(1,0).hashCode()       [after F-10]          => 1072693248
   FixedUInt(1,0).equals(IntegerValue(1))                => true
   HashSet{ 1 , UInteger(1,0) }.size()   TODAY           => 2  [1, UInteger(1, 0.0)]
   HashSet{ 1 , UInteger(1,0) }.size()   AFTER F-10      => 1  [1]
   HashSet{ 1 , UReal(1,0) }.size()      CONTROL         => 1  [1]
   ```

   Today the two never share a bucket, so `equals` is *never* consulted. After F-10 they do, and
   `UIntegerValue.equals(IntegerValue)` is `true`, so the element is dropped. The correct Δ is
   **`SET`**, not `NONE`.

   The *corpus* conclusion still holds: `grep -cE '(Set|Bag|Sequence|OrderedSet)\{[^}]*UInteger'`
   over all four `.in` files returns `0`, so corpus exposure is `0`. Only the justification changes
   — and it changes from "cannot happen" to "happens, but nothing observes it here", which is a
   different claim and needs the F-10 test in §7.3 of that document to assert it.

2. **`10-values.md` §15 gap 2 (SBoolean under sorting) — RESOLVED, benign.** Sorting a 33-element
   `SequenceValue` of `SBooleanValue`s via `getSortedElements()` returns insertion order and throws
   nothing. A constant-`0` comparator is *consistent*; TimSort has nothing to detect. The crash
   risk is created only by "fixing" it via `SBoolean.compareTo`, which is why M-18 correctly refuses
   that route.

3. **`10-values.md` §15 gap 3 (CollectionValue u-ops with SBoolean elements) — RESOLVED, real.**
   Both failures reproduce, with the exact exception messages in V14.

4. **`10-values.md` §15 gap 1 (jar-vs-source drift) — narrowed.** Every behavioural claim in this
   document was taken from `atenearesearchgroup.uncertainty.jar` and `use.jar`, not from
   `uDataTypes/*.java`, so the drift risk does not apply to §2 or §3. It still applies to formula
   attributions made in `10-values.md` and the `20-ops-*` parts. Spot check performed:
   `javap -c` on `use.jar`'s `UBooleanValue.toString(StringBuilder)` shows `iconst_3` before
   `MathUtil.round(DI)D`, matching the source's `MathUtil.round(probability(), 3)` exactly.

---

## 7. Gaps and open decisions

1. **DECISION NEEDED — eight corpus rows cannot pass against the fork.** Measured (`P9`),
   `MATCH=2  MISMATCH=8` where the two matches are controls:

   ```
   MISMATCH UBooleanExpression.in:52    got -> UBoolean(true, 0.22)  | expected -> UBoolean(true, 0.2205)
   MISMATCH UBooleanExpression.in:126   got -> UBoolean(true, 0.717) | expected -> UBoolean(true, 0.7165)
   MISMATCH UCollectionOperations.in:13 got -> UBoolean(true, 0.579) | expected -> UBoolean(true, 0.5792596878)
   MISMATCH UCollectionOperations.in:16 got -> UBoolean(true, 0.136) | expected -> UBoolean(true, 0.1360612114)
   MISMATCH UCollectionOperations.in:33 got -> UBoolean(true, 0.5)   | expected -> UBoolean(true, 0.4999999995)
   MISMATCH UCollectionOperations.in:48 got -> UBoolean(true, 0.585) | expected -> UBoolean(true, 0.5850213691)
   MISMATCH UCollectionOperations.in:51 got -> UBoolean(true, 0.984) | expected -> UBoolean(true, 0.9835952315)
   MISMATCH UCollectionOperations.in:65 got -> UBoolean(true, 0.758) | expected -> UBoolean(true, 0.758018702)
   MATCH    UBooleanExpression.in:49    got -> UBoolean(true, 0.08)  | expected -> UBoolean(true, 0.08)
   MATCH    UCollectionOperations.in:140 (the Set print-order control)
   ```

   Every "got" is exactly the 3-decimal rounding of the "expected", so the corpus was generated by
   a build that rounded to 10 (or not at all) and predates
   `MathUtil.round(probability(), 3)`. Both the fork **source** and the fork **jar** round to 3
   (source `UBooleanValue.java:197`; bytecode `iconst_3` at offset 32). `grep` over `docs/port2/`
   finds no acknowledgement of this. **The port must choose:** (a) follow the fork code, emit
   3 decimals, and pre-register these 8 rows as known non-agreements — the reading consistent with
   B6's precedent of normalising the corpus rather than the port; or (b) follow the corpus, emit
   10 decimals, and diverge from the fork's own `toString`. Recommendation is (a), but it is not
   mine to decide and it changes the denominator of Study A.

2. **DECISION NEEDED — V15, order-dependent set membership.** `Set{1, UReal(1,0)}` silently drops
   the `UReal` (size 1, prints `Set{1}`) while `Set{UReal(1,0), 1}` keeps both (size 2). This is
   not on the 33-row B7 list and B7 therefore does not authorise a fix. Fixing it symmetrically
   requires adding `UIntegerValue`/`URealValue` arms to upstream `IntegerValue.equals` /
   `RealValue.equals`, which exceeds the recorded minimal upstream edits (§5.1–§5.4) and would
   change how *plain* USE values behave. Leaving it reproduces the fork faithfully. **After V8
   lands, the same asymmetry appears for `UInteger`** (currently masked by the collapsed hash), so
   this decision must be made before or with F-10, not after.

3. **UNVERIFIABLE — whether the port's `compareTo` fixes move any corpus print order.** Four corpus
   expectations pin `Collections.sort` output over mixed sets
   (`UCollectionOperations.in:140,143,161,164`). None of the four sets contains a `UInteger`
   (`grep` = 0), and the V9/V13 edits touch only the `UIntegerValue` and `StringValue` arms, so the
   *expected* exposure is zero. But `Set{UReal(1,0.5),UReal(1,0.75),1.2}` prints
   `Set{UReal(1.0, 0.75),1.2,UReal(1.0, 0.5)}` — not numerically sorted — which proves the
   comparator over `URealValue` is already non-transitive under fuzzy comparison. Any change in
   that neighbourhood is not safely predictable by reading. **Settles by:** running all 1427 entries
   before and after each compareTo commit.

4. **UNVERIFIABLE — SBoolean and UString have zero corpus coverage.** Measured:
   `grep -c 'SBoolean'` and `grep -c 'UString'` over all four `.in` files return `0` for every
   file. The entire V7/V12/V13/V14 fix set is unobserved by the historical oracle, so it can only
   be defended by purpose-built tests, never by corpus agreement. This is the strongest argument
   for the B2 full-SBoolean port carrying its own test suite.

5. **Not re-measured here:** F-2 (`MathUtil.round` saturation above `9.2e8`), F-3's bucket effect,
   and F-4's rounding widening. All three are `READ_FROM_SOURCE` in this document.

6. **Not consulted:** `origin/main`. No claim here derives from the earlier port. Reference
   repositories were read only; nothing under `.git/reference-repositories` was built or modified.

---

## 8. Command index

```sh
R=/home/xoruser/msc-4/use-msc2026/.git/reference-repositories/uncertainty/USE-Uncertainty
T=/home/xoruser/msc-4/use-msc2026/use-core/src/main/java/org/tzi/use
F=$R/src/main/org/tzi/use
D=$R/src/test/org/tzi/use/parser/uncertainty

# §1 — the Value contract did not move
diff -u $T/uml/ocl/value/Value.java $F/uml/ocl/value/Value.java
grep -n 'class Value\|abstract\|public \|protected ' $T/uml/ocl/value/Value.java

# §5.2 / §5.3 — the only behavioural deltas in the two edited upstream files
diff -u $T/uml/ocl/value/CollectionValue.java $F/uml/ocl/value/CollectionValue.java
diff -u $T/uml/ocl/value/RealValue.java       $F/uml/ocl/value/RealValue.java

# §2.2 — the delegate %5.3f form, and its (non-)reachability
javap -p -c -cp $R/lib/atenearesearchgroup.uncertainty.jar uDataTypes.UReal | grep 5.3f
javap -p -c -cp $R/lib/use.jar org.tzi.use.uml.ocl.value.UBooleanValue \
  | sed -n '/toString(java.lang.StringBuilder)/,/^$/p'
grep -rnE 'getu(Real|Integer|Boolean)\(\)|wrapper' $F --include=*.java \
  | grep -vE 'value/(URealValue|UIntegerValue|UBooleanValue|UStringValue|SBooleanValue)\.java'

# §2.3 / §7 — corpus counts
grep -hc -- '^-> Undefined' $D/*.in | paste -sd+ | bc                       # 79
grep -nE '^-> .*UBoolean\((true|false), [0-9]+\.[0-9]{4,}\)' $D/*.in        # the 8 stale rows
grep -nE '^-> *(Set|Bag|Sequence|OrderedSet)\{' $D/*.in                     # the 4 order-pinning rows
grep -cE '(Set|Bag|Sequence|OrderedSet)\{[^}]*UInteger' $D/*.in             # 0 — F-10 corpus exposure
grep -c 'UString' $D/*.in ; grep -c 'SBoolean' $D/*.in                      # 0 in every file
```

---

## Appendix A — the two load-bearing drivers, inline

The probe drivers were scratch files under `/tmp/probe2/` and are not committed. The two that
carry a *correction* to the existing record are reproduced here so the claims survive the scratch
directory.

### A.1 `P5` — the F-10 simulation behind §6.1

Must live in package `org.tzi.use.uml.ocl.value` (it subclasses `UIntegerValue`).

```java
package org.tzi.use.uml.ocl.value;
import java.util.*;
public class P5 {
  static class FixedUInt extends UIntegerValue {           // post-F-10 hash
    FixedUInt(int v, double u){ super(v,u); }
    @Override public int hashCode(){
      int hash = Double.hashCode(value());
      if (uncertainty() != 0) hash = hash*7 + Double.hashCode(uncertainty());
      return hash;                                          // == URealValue.java:56-63
    }
  }
  static void p(String l,Object o){System.out.println(String.format("%-58s",l)+" => "+o);}
  public static void main(String[] a){
    p("IntegerValue(1).hashCode()", IntegerValue.valueOf(1).hashCode());
    p("URealValue(1,0).hashCode()   [F-10 target formula]", new URealValue(1.0,0.0).hashCode());
    p("UIntegerValue(1,0).hashCode()  [today, fork]", new UIntegerValue(1,0.0).hashCode());
    p("FixedUInt(1,0).hashCode()      [after F-10]", new FixedUInt(1,0.0).hashCode());
    p("FixedUInt(1,0).equals(IntegerValue(1))", new FixedUInt(1,0.0).equals(IntegerValue.valueOf(1)));
    Set<Value> s1 = new HashSet<>(); s1.add(IntegerValue.valueOf(1)); s1.add(new UIntegerValue(1,0.0));
    p("HashSet{ 1 , UInteger(1,0) }.size()   TODAY", s1.size()+" "+s1);
    Set<Value> s3 = new HashSet<>(); s3.add(IntegerValue.valueOf(1)); s3.add(new FixedUInt(1,0.0));
    p("HashSet{ 1 , UInteger(1,0) }.size()   AFTER F-10", s3.size()+" "+s3);
    Set<Value> s5 = new HashSet<>(); s5.add(IntegerValue.valueOf(1)); s5.add(new URealValue(1.0,0.0));
    p("HashSet{ 1 , UReal(1,0) }.size()      CONTROL", s5.size()+" "+s5);
  }
}
```

Run: `javac -nowarn -cp "$CP" -d out5 P5.java && java -cp "out5:$CP" org.tzi.use.uml.ocl.value.P5`

### A.2 `P9` — the eight stale corpus rows behind §7 gap 1

```java
import org.tzi.use.parser.ocl.OCLCompiler; import org.tzi.use.uml.mm.*;
import org.tzi.use.uml.ocl.expr.*; import org.tzi.use.uml.ocl.value.*; import org.tzi.use.uml.sys.*;
import java.io.*;
public class P9 {
  static MModel m; static MSystem s; static int miss=0, ok=0;
  static void ev(String src,String e,String expected){ StringWriter sw=new StringWriter();
    Expression x=OCLCompiler.compileExpression(m,e,"p",new PrintWriter(sw),new VarBindings());
    String v; if(x==null) v="COMPILE-ERROR "+sw.toString().trim();
    else try{v=new Evaluator().eval(x,s.state()).toStringWithType();}catch(Throwable t){v="EVAL-THROWS "+t;}
    boolean match=v.equals(expected); if(match) ok++; else miss++;
    System.out.println((match?"MATCH   ":"MISMATCH")+" "+src+"  got -> "+v+"   |  expected -> "+expected); }
  public static void main(String[] a) throws Exception {
    m=new ModelFactory().createModel("m"); s=new MSystem(m);
    ev("UBooleanExpression.in:52","UBoolean(false, 0.55) and UBoolean(true, 0.49)","UBoolean(true, 0.2205) : UBoolean");
    ev("UBooleanExpression.in:126","UBoolean(false, 0.45) or UBoolean(true, 0.37)","UBoolean(true, 0.7165) : UBoolean");
    ev("UCollectionOperations.in:13","Set{1, 2, UReal(2,5)}->forAll(e | e >= 1)","UBoolean(true, 0.5792596878) : UBoolean");
    ev("UCollectionOperations.in:16","Set{UReal(1, 0.5),UReal(1,0.75), 1.2}->forAll(e | e >= 1.2)","UBoolean(true, 0.1360612114) : UBoolean");
    ev("UCollectionOperations.in:33","Set{0, 1, UReal(3, 0.5)}->exists(e | e >= 3)","UBoolean(true, 0.4999999995) : UBoolean");
    ev("UCollectionOperations.in:48","Set{UReal(2, 0.5), 1, 2.5, 3.2, UReal(3.5, 0.25)}->includes(UReal(2, 0.2))","UBoolean(true, 0.5850213691) : UBoolean");
    ev("UCollectionOperations.in:51","Set{UReal(2, 0.35), UReal(2, 0.3)}->includes(UReal(2, 0.29))","UBoolean(true, 0.9835952315) : UBoolean");
    ev("UCollectionOperations.in:65","Set{UReal(2, 0.5), 1, 2.5, 3.2, UReal(3.5, 0.25)}->includesAll(Set{2.5, UReal(3.5, 0.15)})","UBoolean(true, 0.758018702) : UBoolean");
    ev("UBooleanExpression.in:49 (control)","UBoolean(true, 0.4) and UBoolean(true, 0.2)","UBoolean(true, 0.08) : UBoolean");
    ev("UCollectionOperations.in:140 (control)","Set{UReal(2, 0.5), 2.5, 3.2, 1, UReal(3, 0.25)}->uSelect(e | e >= 2)","Set{2.5,UReal(3.0, 0.25),3.2} : Set(UReal)");
    System.out.println("MATCH="+ok+"  MISMATCH="+miss);
  }
}
```

Expected output is quoted verbatim in §7 gap 1: `MATCH=2  MISMATCH=8`.
