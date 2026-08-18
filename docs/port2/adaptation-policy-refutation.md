# Refutation of `adaptation-policy.md`

**Role:** refuter. I wrote none of the policy or the six area documents. I own Maven for this round
and also probed the fork and 7.5.0 directly with `javac`/`java`.

**Verdict: SOUND_WITH_DOCUMENTED_LIMITS — fit to govern S3, subject to two must-fix text
corrections recorded in §7.**

The two loads that matter most both hold, and I made them stronger than the policy did:

* the **worked example** is right (§1, measured on both sides);
* the **waiver count is one** (§2), and I bounded it far more tightly than §3.2 did — the fork
  lattice changes **0 of 324** `conformsTo` cells, **0 of 324** pairwise `LCS` cells and **0 of
  1100** `ULCS` cells over crisp types; only `allSupertypes()` itself moves.

I also ran the gate the policy could not (ground rule 3 forbade it there): **1086 upstream tests are
green today**, `ShellIT` included.

What I found wrong is in the *justifications*, not the answers. Two named artifacts — **W-01's
"why the alteration is correct" field** and **P-03/R-A/B12's error-message count** — are refuted by
measurement. Both are text fixes. Neither changes a port decision.

**Environment.** HEAD `54e2745b`, branch `port-uncertainty-2`, working tree clean apart from the two
untracked documents under review. `use-core/target/classes` is newer than every file in
`use-core/src/main` (checked with `find -printf '%T@'`), so §6.4's first risk — "if that build tree
is stale relative to HEAD, the entire 7.5.0 column needs re-measuring" — **does not apply**.

```
L=.git/reference-repositories/uncertainty/USE-Uncertainty/lib
CPF="$L/use.jar:$L/atenearesearchgroup.uncertainty.jar:$L/antlr-3.4-complete.jar"
CP750="use-core/target/classes:~/.m2/.../antlr-runtime-3.5.3.jar:~/.m2/.../guava-33.6.0-jre.jar"
```
All fork/7.5.0 comparisons below were run under `-XX:+UnlockExperimentalVMOptions -XX:hashCode=2`
where identity-hash order could matter (see §2.4). Scratch drivers live in `/tmp/refute`, never in
the repo. The reference repositories were read only.

---

## 1. Is the worked example right? — **CONFIRMED, all four rows**

Fork, via `OCLCompiler.compileExpression` against the 2021 jars:

```
Set{UReal(2,0.5), 1, 2.5}
   ==>  TYPE=Set(UReal)   VALUE=Set{1,2.5,UReal(2.0, 0.5)} : Set(UReal)
Set{UReal(2,0.5), 1, 2.5}->sum()
   ==>  TYPE=UReal   VALUE=UReal(5.5, 0.5) : UReal
Set{1, 2.5}
   ==>  TYPE=Set(Real)   VALUE=Set{1,2.5} : Set(Real)
1/0
   ==>  TYPE=Real   VALUE=Undefined : OclVoid
```

Plain USE 7.5.0, same driver, `use-core/target/classes`:

```
Set{UReal(2,0.5), 1, 2.5}
   ==>  COMPILE-ERROR: probe:1:4: Undefined operation `UReal'.
Set{1, 2.5}
   ==>  TYPE=Set(Real)   VALUE=Set{1,2.5} : Set(Real)
1/0
   ==>  TYPE=Real   VALUE=null : OclVoid
```

`Set(UReal)`, `UReal`, the `Set(Real)` control and the `Undefined`→`null` row all reproduce exactly.
§1.1 of the policy is sound.

*Cosmetic only:* the policy renders the 7.5.0 diagnostic as `Undefined operation 'UReal'.`; the real
bytes use an asymmetric backtick, `` Undefined operation `UReal'. ``. Anything pinning that string
byte-exactly must use the backtick form.

---

## 2. Is the waiver count right? — **CONFIRMED AT ONE, and better bounded than §3.2 claims**

### 2.1 I searched the trees §3.2 did not

§3.2 searched `use-core/src/test` and `use-gui/src/test`. There are **four** test roots. I added
`use-core/src/it` and `use-gui/src/it`:

```
=== use-core/src/test : allSupertypes ===   24
=== use-gui/src/test : allSupertypes ===     0
=== use-core/src/it   : allSupertypes ===    0
=== use-gui/src/it    : allSupertypes ===    0

     12 use-core/.../ocl/type/TypeTest.java ... allSupertypes()",
     12 use-core/.../ocl/type/TypeTest.java ... allSupertypes());
```

```
=== getLeastCommonSupertype / LeastCommonSupertypeDeterminator, all 4 trees ===
(exit 1)          <- zero hits
```

`conformsTo` over all four trees returns 22 hits: `TypeTest` ×13, `StatementEffectTest` ×6,
`VariableSetTest` ×2, `SymbolTableTest` ×1. §3.2's tallies are correct and extend unchanged to
`src/it`.

One of those is **not an assertion** and §3.2 does not say so: `VariableSetTest.java:235`,
`if (otherType.conformsTo(type))`, is *control flow* that decides whether `assertTrue(containsSubType)`
passes. I read the fixture — its types are built at `:55-59` from `mkInteger, mkReal, mkString,
mkBoolean, mkOclAny` only. No U-type can enter, and §2.2 below shows crisp-to-crisp `conformsTo` is
untouched. **Not falsified**, but it is a control-flow site, not an assertion site, and a future
reader should know that.

### 2.2 The decisive measurement: what the lattice actually changes

I built the same 18-type crisp table in both binaries and dumped `allSupertypes`, the full 18×18
`conformsTo` matrix and the full 18×18 pairwise `getLeastCommonSupertype` matrix — 666 cells each.

```
=== DIFF fork vs 7.5.0 over crisp lattice (666 cells each) ===
=== counts by kind ===
     15 SUP
```

**Fifteen `allSupertypes` rows change. Zero `conformsTo` cells change. Zero pairwise `LCS` cells
change.** Sample of the change:

```
< SUP Integer = [Integer, OclAny, Real]
> SUP Integer = [Integer, OclAny, Real, UInteger, UReal]
< SUP Boolean = [Boolean, OclAny]
> SUP Boolean = [Boolean, OclAny, SBoolean, UBoolean]
< SUP Set(Integer)  = [Collection(Integer), Collection(OclAny), Collection(Real), Set(Integer), Set(OclAny), Set(Real)]
> SUP Set(Integer)  = [Collection(Integer), Collection(OclAny), Collection(Real), Collection(UInteger), Collection(UReal), Set(Integer), Set(OclAny), Set(Real), Set(UInteger), Set(UReal)]
```

This independently confirms W-01's "one design decision" field cell for cell: `Boolean` **+2**
(`SBoolean`, `UBoolean`), `Integer` **+2** (`UInteger`, `UReal`), `Real` **+1**, `String` **+1**.

### 2.3 `testSupertype`: 10 of 12, re-derived

Reading `TypeTest.java:135-227` and matching each of the 12 assertions against the measured fork
`allSupertypes`: the four scalar rows (`Boolean`, `Integer`, `Real`, `String`) and the six collection
rows (`Collection(Boolean)`, `Collection(Integer)`, `Collection(Collection(Real))`, `Set(Integer)`,
`Sequence(Integer)`, `Bag(Integer)`) are all falsified, because every one asserts **exact set
equality**. The two survivors, measured in both trees:

```
--- FORK ---                            --- 7.5.0 ---
Enum.allSupertypes()   = [Color, OclAny]   Enum.allSupertypes()   = [Color, OclAny]
OclAny.allSupertypes() = [OclAny]          OclAny.allSupertypes() = [OclAny]
```

**10 of 12. Exactly as §3.1 states, and the two survivors are the two it names.**

### 2.4 The one place fork and 7.5.0 appeared to differ was T-08, not the lattice

`ULCS` (`UniqueLeastCommonSupertypeDeterminator`) is the routine T-07 says drives the worked
example, and §3.2 never searched for it. I swept it over 1100 crisp pair/triple cells. A naive run
showed 22 differing cells, **all** containing `{Integer, UnlimitedNatural}`. That is T-08, not the
lattice — proved by forcing identity-hash order **inside plain 7.5.0, same binary, same driver**:

```
=== PLAIN 7.5.0, varying -XX:hashCode ===
hashCode=0 -> ULCS2 {Integer,UnlimitedNatural} = Integer
hashCode=1 -> ULCS2 {Integer,UnlimitedNatural} = UnlimitedNatural
hashCode=2 -> ULCS2 {Integer,UnlimitedNatural} = UnlimitedNatural
hashCode=3 -> ULCS2 {Integer,UnlimitedNatural} = UnlimitedNatural
hashCode=4 -> ULCS2 {Integer,UnlimitedNatural} = UnlimitedNatural
hashCode=5 -> ULCS2 {Integer,UnlimitedNatural} = Integer
```

Under a matched regime the two binaries agree completely:

```
=== FULL ULCS DIFF under a MATCHED hash regime (hashCode=2 both) ===
differing cells: 0
```

**T-08 / B11b is real, is present in plain 7.5.0, and is the sole source of fork-vs-7.5.0 ULCS
disagreement on crisp types. The lattice contributes zero.** §3.3's "that is luck, not safety" is
right, and DEP-30 is justified.

### 2.5 "Any test asserting a type name in output" — replayed, not reasoned

This is the channel §3.2 never searched. I replayed all 121 entries of the upstream corpus
`use-core/src/test/resources/org/tzi/use/parser/test_expr.in` — the type-assertion-dense fixture
`USECompilerTest` consumes — through **both** binaries and diffed printed types:

```
=== DIFF over 121 upstream test_expr.in entries (matched hash regime) ===
< [11] 3 / 0     ==>   TYPE=Real | null : OclVoid
> [11] 3 / 0     ==>   TYPE=Real | Undefined : OclVoid
< [12] 3 div 0   ==>   TYPE=Integer | null : OclVoid
> [12] 3 div 0   ==>   TYPE=Integer | Undefined : OclVoid
< [117] Set{-2..3}->sortedBy(I|I*I)        ==> TYPE=OrderedSet(Integer) | OrderedSet{0,-1,1,-2,2,3}
> [117] Set{-2..3}->sortedBy(I|I*I)        ==> TYPE=Sequence(Integer)   | Sequence{0,-1,1,-2,2,3}
< [118] OrderedSet{-2..3}->sortedBy(I|I*I) ==> TYPE=OrderedSet(Integer) | OrderedSet{0,-1,1,-2,2,3}
> [118] OrderedSet{-2..3}->sortedBy(I|I*I) ==> TYPE=Sequence(Integer)   | Sequence{0,-1,1,-2,2,3}

=== differing entries: 4 ===
```

Four of 121, and **zero attributable to the lattice**: [11]/[12] are V01/P-01 (B6), [117]/[118] are
K-13 (upstream drift, which DEP-28 gives to 7.5.0 — so the port answers 7.5.0's `OrderedSet` and
these two stop differing). **No upstream fixture's asserted type name is at risk from B5.**

### 2.6 I ran the gate — the policy could not

Ground rule 3 blocked Maven for the six readers; §6.4 lists "No Maven was run anywhere in this
round" and "`ShellIT` was not executed" as open risks. Both are now closed.

```
$ mvn -o -Pupstream-oracle test
[floor] initialize: requested profiles [upstream-oracle], declared in this reactor [upstream-oracle]
[INFO] Tests run: 38, Failures: 0, Errors: 0, Skipped: 0 -- in org.tzi.use.uml.ocl.type.TypeTest
[INFO] Tests run: 939, Failures: 0, Errors: 0, Skipped: 0      <- use-core
[INFO] Tests run: 17,  Failures: 0, Errors: 0, Skipped: 0      <- use-gui
[INFO] BUILD SUCCESS

$ mvn -o -Pupstream-oracle verify -DskipTests=false
[INFO] Tests run: 1,   Failures: 0, Errors: 0, Skipped: 0 - in org.tzi.use.OCLExpressionIT
[INFO] Tests run: 129, Failures: 0, Errors: 0, Skipped: 0 - in org.tzi.use.main.shell.ShellIT
[INFO] BUILD SUCCESS
```

**Baseline for S3: 939 + 17 + 1 + 129 = 1086 upstream tests green, BUILD SUCCESS.** `TypeTest` runs
38 methods, so `testSupertype` really does execute under the profile and really will turn red — the
waiver is not theoretical. `ShellIT`'s 129 figure in §3.2/§6.4 was a `ls | wc -l` count; it is now a
measured pass count.

*Side effect:* `upstream-test-waivers.md`'s "Standing caution — 38 of 41 `*Test.java` files never
execute" is **stale** under `-Pupstream-oracle`. S3 should update it when it writes W-01.

### 2.7 The fixture searches reproduce

```
=== the five U-type names as whole words in ALL fixtures (348 files) ===
hits: 0
=== '*' inside a collection literal ===
use-gui/src/it/resources/testfiles/shell/t001.in:431:? Set{1..2*2}
use-core/src/test/resources/org/tzi/use/parser/test_expr.in:160:Set{1..2*2}
=== 'equals' as a whole word in fixtures ===
t098.use:11, t133_import_date.use:29, t133_import_datetime.use:12   <- the 3 real ones
t019.in:6                                                            <- '#' comment
t113.use:1493,1506,1518,1531,1546                                    <- inside /* ... */
```

The two `*` hits are **multiplication**, not the `UnlimitedNatural` literal — `Set{1..2*2}` is
`-> Set{1,2,3,4} : Set(Integer)` in the fixture. T-08 exposure in upstream fixtures is genuinely
zero. The five `t113.use` `equals` hits sit inside a `/* UML 2.3 p. 107: ... */` block comment; §3.2
described the six false alarms as "a `#` comment, and 5× `equalsIgnoreCase`", which does not match
what is in the fixtures — but the clearing is correct either way, and G-01's zero-grammar-edit
answer makes the whole question moot.

### 2.8 Verdict on the count

**One waiver, W-01, and the bound is tighter than §3.2 claims.** Beyond §3.2's search I add: zero
hits across both `src/it` trees; zero of 324 `conformsTo` cells; zero of 324 pairwise `LCS` cells;
zero of 1100 `ULCS` cells; zero lattice-attributable diffs over 121 replayed upstream corpus
entries; and 1086 currently-green upstream tests as the delta baseline. **No second assertion is at
risk.**

---

## 3. Are the INFERRED / READ_FROM_SOURCE rows inferable? — **nine probed, nine survive**

The policy marks only **one** row `INFERRED` (G-03, unprobeable without regenerating the parser) and
twelve `READ_FROM_SOURCE`. I checked the census arithmetic first: 59 rows tagged MEASURED + 11
READ_FROM_SOURCE + K-08 (hybrid) + G-03 = **72 rows**; restoring the three merged double-findings
gives **62 / 12 / 1 = 75 collisions**. §2's header arithmetic is exactly right.

I then probed nine non-MEASURED rows against the fork.

| row | claim | result |
|---|---|---|
| **T-01** | `Type` is an interface in both; the "class vs interface" collision does not exist | **CONFIRMED** |
| **T-12** | `TypeImpl.conformsTo` self-recurses; all 5 concrete U-types must override, the 2 abstract tags must not | **CONFIRMED** |
| **K-04** | `includes/excludes/includesAll/excludesAll` lift `Boolean`→`UBoolean` | **CONFIRMED (upgraded to MEASURED)** |
| **K-13** | fork's `sortedBy` predates the `OrderedSet` branch | **CONFIRMED** (§2.5, entries 117/118) |
| **K-16** | `evalExistsOrForAll0` is dead in the fork, live in 7.5.0 | **CONFIRMED** |
| **K-17** | `uIncludesAll` short-circuits on size; `uExcludesAll` does not | **CONFIRMED and STRENGTHENED** |
| **V05** | `UStringValue.toString` hard-codes the prefix, leaves confidence unrounded | **CONFIRMED** |
| **V16** | `URealValue.hashCode` unrounded while `equals` rounds to 10 dp | **CONFIRMED (upgraded to MEASURED)** |
| **P-09 / V03** | three rounding regimes in one family | **CONFIRMED (upgraded to MEASURED)** |

**T-12** — 7.5.0 `TypeImpl.java:78-81` is `return this.conformsTo(other);`. Bytecode of the fork's
seven new types, counting `conformsTo` declarations:

```
URealType              1     UncertainType          0
UIntegerType           1     UncertainBooleanType   0
UBooleanType           1
UStringType            1
SBooleanType           1
```
Five concrete overrides, two abstract non-overrides. Exactly the row.

**K-04 / K-17.** The four collection predicates lift as claimed:

```
Set{UReal(2,0.5), 1}->includes(1)          ==>  TYPE=UBoolean  VALUE=UBoolean(true, 1.0)
Set{1,2}->includes(1)                      ==>  TYPE=Boolean   VALUE=true
Set{UReal(2,0.5), 1}->includesAll(Set{1})  ==>  TYPE=UBoolean  VALUE=UBoolean(true, 1.0)
```

K-17 has **no OCL name** — `uIncludesAll` is `Undefined operation named 'uIncludesAll'`. But K-04's
eval dispatch reaches it, so the defect **is** observable from the language, which the K-17 row does
not say:

```
Set{UReal(2,0.5)}->includesAll(Set{1,2,3})       ==>  UBoolean(true, 0.0)   <- size shortcut, no element examined
Set{UReal(2,0.5),1,2,3}->includesAll(Set{1,2,3}) ==>  UBoolean(true, 1.0)
Set{UReal(2,0.5)}->excludesAll(Set{1,2,3})       ==>  UBoolean(true, 1.0)   <- no matching shortcut
```

**K-16.** `javap -c` on the fork's `ExpQuery`: `evalExistsOrForAll0` still declared, with exactly
**two** `invokespecial` references, both its own recursive calls. 7.5.0 has three references —
`:161` (live caller), `:183`, `:189`. The external caller is gone in the fork; the method is dead.

**V16.** Bytecode: `hashCode()` calls `value()`/`uncertainty()` straight through `Double.hashCode`
with **no** `MathUtil.round`; `equals` calls `MathUtil.round:(DI)D` **four** times. Demonstrated:

```
UReal(1.0, 0.5)  vs UReal(1.0, 0.5)   equals=true  hashEq=false  (-9121932 / -8806680)
UReal(2.0, 0.0)  vs UReal(2.0, 0.0)   equals=true  hashEq=false  (1073764342 / 1073831896)
```

**V03 / P-09 — the three regimes, one probe:**

```
SBoolean(0.333333333333,0.333333333333,0.333333333334,0.123456789012)
                             ==>  SBoolean(0.333, 0.333, 0.333, 0.123)      <- 3 dp
UBoolean(true,0.123456789012345)  ==>  UBoolean(true, 0.123)                <- 3 dp
UReal(1.123456789012345,0.987654321098765)
                             ==>  UReal(1.123456789, 0.9876543211)          <- 10 dp
UInteger(7,0.123456789012345)     ==>  UInteger(7, 0.123456789)             <- 10 dp
UString('a',0.123456789012345)    ==>  UString('a', 0.123456789012345)      <- none
```

Along the way I also re-confirmed three MEASURED rows independently: **V08** (`UIntegerValue`
hashCode is `0` for all six probed uncertainties, while `IntegerValue(1)` and `RealValue(1.0)` both
hash to `1072693248`), **K-06** (exactly the five new `CollectionValue` methods, by `javap`), and
**B2** (`StandardOperationsSBoolean$1` … `$39` — exactly 39; they are anonymous *inner classes*, not
"enum constants" as §1.3 words it, but the count is exact).

**V18 / P-04 / B14 is real.** The corpus wants 10 decimals; the shipped jar gives 3:

```
UBoolean(true,0.5792596878)  ==>  UBoolean(true, 0.579)
```

§5.5 correction #2 — "1427 passes is unachievable by any correct port" — stands.

---

## 4. Is anything MISSING? — **one MAJOR gap, two MINOR ones; four areas checked clean**

### 4.1 MAJOR — P-03 / R-A / B12 undercount the position-less messages 4 → **11**, and misattribute them

P-03 says "**four** bare messages ... thrown at `ASTURealLiteral` :28,31 and `ASTUBooleanLiteral`
:31". R-A's guard says "pin **the four strings** as constants in one place". §5.2's B12 criterion says
"5 via the error path with **the four message constants** unmodified".

Binary-scanning every class in the fork `use.jar` for the producers gives **five classes across two
packages**, and extracting their constant pools gives **eleven** distinct strings:

```
=== org/tzi/use/parser/ocl/ASTURealLiteral ===          (parser package)
// String Uncertainty must be Integer or Real
// String Value must be Integer or Real
=== org/tzi/use/uml/ocl/expr/ExpConstUInteger ===       (expression package)
// String Uncertainty must be Integer or Real
// String Value must be Integer
=== org/tzi/use/uml/ocl/expr/ExpConstUBoolean ===
// String Probability must be a Integer or Real
// String Value must be Boolean
=== org/tzi/use/uml/ocl/expr/ExpConstUString ===
// String UString : value must be type of String
=== org/tzi/use/uml/ocl/expr/ExpConstSBoolean ===
// String Agent  must be a kind of Real
// String Belief  must be a kind of Real
// String Disbelief  must be a kind of Real
// String Uncertainty  must be a kind of Real
```

All eleven are position-less at run time — `cat -A`, so `$` is end-of-line and there is no
`probe:L:C:` prefix:

```
UReal(1,true)                    -> Uncertainty must be Integer or Real$
UInteger(1,true)                 -> Uncertainty must be Integer or Real$
UBoolean(true,true)              -> Probability must be a Integer or Real$
SBoolean(0.5,true,0.5,0.5)       -> Disbelief  must be a kind of Real$
SBoolean(0.5,0.5,true,0.5)       -> Uncertainty  must be a kind of Real$
SBoolean(0.5,0.5,0.5,true)       -> Agent  must be a kind of Real$
UReal(true,0.5)                  -> Value must be Integer or Real$
UInteger(true,0.5)               -> Value must be Integer$
UBoolean(1,0.5)                  -> Value must be Boolean$
UString(1,0.5)                   -> UString : value must be type of String$
SBoolean(true,0.1,0.2,0.3)       -> Belief  must be a kind of Real$
```

Three concrete defects:

1. **Misattribution.** The `UBoolean` message is in `ExpConstUBoolean` (`uml/ocl/expr`), not
   `ASTUBooleanLiteral` (`parser/ocl`). `ASTUStringLiteral` and `ASTSBooleanLiteral` carry no message
   at all. An S8 author following P-03's file:line will not find the throws.
2. **Undercount.** Eleven strings, five classes, two packages — not four strings in one place.
   The four `SBoolean` messages each carry a **double space** (`Belief  must`), and
   `ExpConstUBoolean`'s reads "a Integer", not "an Integer". Anything pinning these must be
   byte-exact.
3. **The guard does not cover what it is for.** R-A exists to stop an implementer "improving" the
   position-less throws into positioned ones. Seven of the eleven have **no corpus witness at all** —
   §6.3 records `grep -c 'UString\|SBoolean'` = 0 in all four `.in` files — so nothing would catch
   their regression. A guard scoped to four of eleven strings is not a guard.

The narrow defence is available: B12's "four message constants" may mean only those the five corpus
error-path entries exercise. That reading is defensible for the *acceptance criterion*. It is not
available for **R-A**, whose stated purpose is protecting the throws themselves.

### 4.2 MINOR — DEP-28 understates its own observable change

DEP-28 describes K-13's effect as "`->sortedBy` over an `OrderedSet` keeps returning an
`OrderedSetValue`". Measured, it is wider than that in two ways. 7.5.0's branch fires for a **`Set`**
receiver too (§2.5, entry [117]), and it fires for **uncertain** element types:

```
FORK:  Set{1,2,UReal(2,0.5)}->sortedBy(e|e)  ==>  TYPE=Sequence(UReal)
```

The port will answer `OrderedSet(UReal)`. That is a fork-vs-port difference on an *uncertainty*
expression, which is exactly the class of thing §4 exists to pre-register, and DEP-28's "observable
change" column does not describe it. It is still the **right** call under clause 3 — the collection
kind is upstream drift, the `UReal` element type is untouched — but S7 comparing port against fork
will see a type change §4 does not predict. **DEP-28's observable-change column needs one more
sentence.**

### 4.3 MINOR — no row covers ranges

`ASTCollectionItem.java:63` gates `ExpRange` on `isTypeOfInteger()` for **both** bounds. Nothing in
the 72 rows mentions ranges. Measured fork behaviour:

```
Set{UInteger(1,0)..UInteger(3,0)}  ==>  COMPILE-ERROR: Ranges must be of type Integer.
Set{1..UInteger(3,0)}              ==>  COMPILE-ERROR: Ranges must be of type Integer.
Set{1..3}                          ==>  TYPE=Set(Integer)   VALUE=Set{1,2,3}
```

Because the guard is `isTypeOf` (exact), not `isKindOf`, the fork lattice cannot change it and the
port inherits the rejection for free. So this is a **row that should exist and does not**, rather
than a risk — but it is a documented uncertainty-surface boundary (uncertain ranges are rejected)
that the port must be shown to reproduce, and today nothing asserts it.

### 4.4 Areas I checked and found **clean** — no row needed

* **`ExpressionVisitor` implementors (K-14).** K-14 counts only `implements`. There are three
  further *subclasses* — `GenerateHTMLExpressionVisitor:30`, and `EvalNode`'s inner
  `RelevantOperationHighlightVisitor:618` / `SubstituteVariablesExpressionVisitor:351`. I checked
  whether DEP-24's `visitUSelectC` fix would fail to propagate: `GenerateHTMLExpressionVisitor` is 58
  lines and overrides **no** `visit*` method, so all three inherit the fix. **K-14's Tier A of 3 is
  correct both for compilation and for behaviour.** Its arithmetic is also exactly right:

  ```
  fork methods: 57   7.5.0 methods: 49
  === ONLY IN FORK ===  visitConstSBoolean, visitConstUBoolean, visitConstUInteger,
                        visitConstUReal, visitConstUString, visitDefSBoolean,
                        visitObjOp, visitUSelect, visitUSelectC
  === ONLY IN 7.5.0 === visitInstanceOp
  ```
  49 methods ✓; 8 genuine additions once `visitObjOp` is recognised as the pre-rename form of
  `visitInstanceOp` ✓ (DEP-34's −1 ✓); 7 after dropping B10's `visitDefSBoolean` ✓.
* **`TypeFactory` interning.** `TypeFactory.java:36-57` — a private static `buildInTypesMap` plus
  seven `static final` singletons; collection types are constructed fresh and compared structurally.
  Adding five singletons and five map entries introduces no interning hazard. T-10/T-13 are adequate.
* **XML / state serialisation of uncertain values.** There is none in this version — no `toXML`, no
  state writer over `Value`. State is rendered through `toString`, which P-05 owns. The only
  `instanceof`-over-`Value` chains outside the value package are `soil/EvalUtil.java` (`ObjectValue`,
  `StringValue`) and `util/rubyintegration/RubyHelper.java`. Neither is on an uncertainty path.
* **`analysis/coverage` and DEP-33 / B10.** 7.5.0 has `analysis/coverage` only; the fork additionally
  has `analysis/metrics` (`AbstractMetricVisitor`, `GSMetricVisitor`, `GSMetric`,
  `GSMetricConfiguration`, `Measurement`, `CSVFileReader`, …). Dropping it is right — no port target,
  not uncertainty meaning. DEP-33 names only 2 of ~10 classes, which understates the package but
  changes nothing. **B10's dead-code claim verified:** `ASTSBooleanDefExpression` is referenced by
  **nothing but itself** in the whole jar — no generated parser class reaches it — and
  `ExpDefSBoolean` only by visitors that must cover every expression kind. Dropping both drops
  nothing observable.

### 4.5 Partially closing §6.3's last gap

§6.3 records "`->closure` and `->iterate` over uncertain elements were **not probed at all**". Probed:

```
Set{1,2,UReal(2,0.5)}->iterate(e; acc : Real = 0.0 | acc + 1)  ==>  TYPE=Real            VALUE=3.0
Set{1,2,UReal(2,0.5)}->collect(e|e)                            ==>  TYPE=Bag(UReal)
Set{1,2,UReal(2,0.5)}->isUnique(e|e)                           ==>  TYPE=Boolean         VALUE=true
Set{1,2,UReal(2,0.5)}->asSequence()                            ==>  TYPE=Sequence(UReal)
```

And the TimSort concern is **real but not a crash** — a 34-element uncertain `sortedBy` returns
without throwing, in a wildly non-monotonic order (`…7.7,9.9,UReal(1.0, 0.5),UReal(1.0, 0.75)…`).
So the hazard for DEP-06/07/09/10 is *silently reshuffled output*, not an exception. §6.3's entry
should say so; it currently anticipates a throw.

---

## 5. Does any adaptation contradict the policy? — **no**

I looked specifically for a row that drops uncertainty behaviour for convenience. The four best
candidates all survive:

* **B10 / DEP-33** — dropping `ExpDefSBoolean` and `analysis/metrics`. Verified unreachable (§4.4).
  Nothing uncertain is lost; B2's "SBoolean in full" is unaffected, and the 39 operations are present
  and counted.
* **DEP-28 (K-13)** — the only row where the port's answer differs from the fork on an *uncertain*
  expression (§4.2). The element type stays `UReal`; only the collection kind moves, and that is
  upstream drift under clause 3. Policy-consistent; under-described.
* **DEP-26 (K-18)** — moving the out-of-range `uSelectC` confidence check from eval to compile time
  does change an uncertainty operation's observable behaviour, but it is a B7 defect expressed the
  7.5.0 way (clause 2 + clause 4), and the row explicitly retains the runtime check for computed
  thresholds. No meaning lost.
* **K-12 / O-02 / O-10** — all three *preserve* fork behaviour where it would be tempting to
  "improve" it (select/reject keep rejecting `UBoolean` predicates; `pow` is not aliased to `power`;
  `UString(…).concat(…)` keeps erroring). These are the rule applied correctly in the harder
  direction.

**No row quietly inverts the rule.**

---

## 6. Is the decision re-scoring honest? — **one MAJOR defect; the other four are sound**

### 6.1 B5 — the answer is right; **its stated reason is refuted by measurement**

§5.1 and W-01's "why the alteration is correct" field both say:

> "B5 option 2 (conformance one-way only …) breaks `getLeastCommonSupertype`, which is what drives
> overload resolution **and the worked example**."

I read `UniqueLeastCommonSupertypeDeterminator.calculateFor:40-70`. Step 1 seeds a candidate pool
from `allSupertypes()`; **steps 2 and 3 select using `conformsTo` only** (`typeIsSupertypeOfAll`,
`typeIsComparableToAll`). And the fork's `UReal.allSupertypes()` contains `UReal` itself. So under
option 2 — `conformsTo` widened, `allSupertypes` not — `UReal` still enters the pool from its own
closure and still wins selection.

I re-implemented both routines verbatim against the fork's real `conformsTo`, with an option-2
supertype closure:

```
=== ingredient facts (fork, MEASURED) ===
UReal.allSupertypes()     = [OclAny, UReal]
Integer.conformsTo(UReal) = true
Real.conformsTo(UReal)    = true
UReal.conformsTo(Real)    = false
real ULCS{UReal,Integer,Real} = UReal

=== SIMULATION of B5 OPTION 2: conformsTo widened, allSupertypes NOT ===
ULCS_opt2{UReal,Integer,Real}   = UReal        <- THE WORKED EXAMPLE STILL WORKS
ULCS_opt2{UInteger,Integer}     = UInteger
ULCS_opt2{UReal,Integer}        = UReal
pairwiseLCS_opt2(Integer,UReal) = OclAny       <- pairwise LCS DOES collapse
pairwiseLCS_opt2(Real,UReal)    = OclAny
```

**Under option 2, `Set{UReal(2,0.5), 1, 2.5}` would still be `Set(UReal)`.** The clause "and the
worked example" is false. It also contradicts the policy's own **T-07**, which states that ULCS,
"**this**, not pairwise `Type.getLeastCommonSupertype`, is what drives the worked example". T-07 and
B5 cannot both be right; T-07 is the one that is.

**The verdict (option 1) survives, on a stronger reason than the policy gives.** Option 2 collapses
*pairwise* LCS to `OclAny`, and pairwise LCS has roughly sixty call sites — `ExpIf:42,48` and the
`StandardOperationsSet/Bag/Sequence/OrderedSet/Collection/Any` registries. Measured, these are
uncertainty expressions that depend on it:

```
Set{UReal(2,0.5)}->including(1)           ==>  TYPE=Set(UReal)   VALUE=Set{1,UReal(2.0, 0.5)}
if true then 1 else UReal(2,0.5) endif    ==>  TYPE=UReal        VALUE=1 : Integer
```

Under option 2 the first becomes `Set(OclAny)` (via `StandardOperationsSet:97`) and the second
`OclAny` (via `ExpIf:42`). That is decisive, and it is a much larger surface than "the worked
example".

**Why this matters more than a wording nit.** §3.4 instructs S3 to write W-01 into
`upstream-test-waivers.md` "citing §3.1–§3.2 rather than re-arguing the ten assertions". The field
S3 would copy is the one containing the refuted sentence. **A permanent waiver record would enshrine
a claim that the next reader can refute in twenty minutes**, and the natural response to a refuted
waiver justification is to re-open B5. The sentence must be corrected before W-01 is written.

### 6.2 B4 — the answer is right; **the stated measurement does not replicate as written**

§5.1 lists as MEASURED "with **zero grammar edit** on 7.5.0: `(1).equals(1)`→`Boolean true`;
`Set{1}->equals(Set{1})`→`true`; `(1).equals(1) and true`→`true`". On stock 7.5.0 it does not:

```
(1).equals(1)          ==> COMPILE-ERROR: probe:1:4: Undefined operation named `equals' in expression `Integer.equals(Integer)'.
Set{1}->equals(Set{1}) ==> COMPILE-ERROR: probe:1:8: Undefined operation named `equals' ...
(1).equals(1) and true ==> COMPILE-ERROR: probe:1:4: Undefined operation named `equals' ...
let equals : Integer = 1 in equals ==> TYPE=Integer  VALUE=1 : Integer      <- this one does hold
1 equals 2             ==> COMPILE-ERROR: probe:line 1:2 missing EOF at 'equals'
```

This also **contradicts the policy's own O-04 row**, whose 7.5.0 column correctly says
"`1.equals(2)`→`Undefined operation named 'equals'`".

The missing precondition is the opmap registration. `ExpStdOp.opmap` is a public static
`ArrayListMultimap`, so I tested the mechanism directly: I subclassed `OpGeneric` with
`name() = "equals"`, called `OpGeneric.registerOperation(...)`, and changed **no grammar**:

```
(1).equals(1)                      ==> TYPE=Boolean  VALUE=true : Boolean
(1).equals(2)                      ==> TYPE=Boolean  VALUE=false : Boolean
Set{1}->equals(Set{1})             ==> TYPE=Boolean  VALUE=true : Boolean
(1).equals(1) and true             ==> TYPE=Boolean  VALUE=true : Boolean     <- fork defect D1 fixed
true and (1).equals(1)             ==> TYPE=Boolean  VALUE=true : Boolean     <- fork defect D2 fixed
let equals : Integer = 1 in equals ==> TYPE=Integer  VALUE=1 : Integer
```

**Every claimed result reproduces, D1 and D2 included, once the operation is registered.** The
substantive claim is true and B4 option 1 is correct. The failure is only that the recipe as
written omits its precondition — and §5.5 correction #5 ("On 7.5.0 with no grammar edit,
`(1).equals(1)` works") states it in the form most likely to be replicated verbatim and to fail.
Graded MINOR because the same row's port instruction names the registration; but it should be fixed
before S6 tries to reproduce it.

The uncertainty meaning B4 preserves is real, and the exact pair the policy cites reproduces:

```
UReal(2,0.5) = UReal(2.4,0.5)        ==>  TYPE=UBoolean  VALUE=UBoolean(true, 0.689)
UReal(2,0.5).equals(UReal(2.4,0.5))  ==>  TYPE=Boolean   VALUE=false
```

### 6.3 B6, B8, B9 — honest

* **B6** — the policy does not overclaim: it says "Already the user's decision; this round confirms
  the mechanism and bounds it". Listing it under ANSWERED slightly inflates the count of decisions
  *this* document removes from the user, but the text is candid. The mechanism (N1, whole-string,
  `contains` guard, `assertEquals(79, n1Fired)`, `replace` explicitly refused) is genuinely
  determined. My §2.5 replay independently shows the token is the *only* value-level difference on
  crisp expressions.
* **B8** — genuinely **determined**, not preferred. Option 2 changes 22 cells of ordinary
  non-uncertain OCL; the policy's second sentence ("Everything else comes from USE 7.5.0") forbids
  that outright. Nothing is left to taste. The row's two riders — `pow` needs the guard on **both**
  parameters, and `mkInteger()→mkReal()` must be left alone — are both correctly derived.
* **B9** — genuinely **determined**. The "additive middle" is impossible, and I confirmed the
  mechanism at source: `ExpQuery.java:136`, `:206`, `:208` all cast `(BooleanValue) queryVal`, and
  `:161` is `boolean res = evalExistsOrForAll0(0, rangeVal, ctx, doExists);`. Admitting a
  `UBooleanValue` past the swapped guard reaches those casts. The accepted cost (loss of
  short-circuiting) is stated honestly rather than buried, and DEP-27 is correctly flagged INFERRED.

### 6.4 Minor factual slips

| where | says | measured |
|---|---|---|
| T-02, fork column | "`MClassifierImpl` had **2** subclasses" | **4** — `MClassImpl`, `MAssociationImpl`, `MAssociationClassImpl`, `EnumType`. The 7.5.0 count of **6** is right, the two roots (`TypeImpl:313`, `MClassifierImpl:355`, plus `OclAnyType:33`, none in `use-gui`) are right, and the port instruction is right — only the fork column is wrong |
| §1.3 / B2 | "39 anonymous `SBoolean` **enum constants**" | 39 anonymous **inner classes** (`StandardOperationsSBoolean$1`…`$39`). Count exact, kind mis-stated |
| §2 vs §6.1 | "1 INFERRED" vs "INFERRED … (2)" | Not a contradiction — §2 counts *collision rows*, §6.1 counts *rows + departures* (G-03 and DEP-27). The 62/12/1 = 75 arithmetic checks out exactly. Worth one clarifying word |
| §3.2, `equals` false alarms | "a `#` comment, and 5× `equalsIgnoreCase`" | In the fixtures the five are prose inside a `/* … */` block in `t113.use`. Clearing is still correct |

---

## 7. Conditions for S3

The policy is fit to govern S3. Two corrections must land first, and both are text.

1. **Before W-01 is written into `upstream-test-waivers.md`** — replace the "why the alteration is
   correct" clause "*and the worked example*" (§3.1, and the same sentence in §5.1's B5 row). Option
   2 does **not** break the worked example (§6.1, simulated). The correct and stronger reason is that
   option 2 collapses **pairwise** `getLeastCommonSupertype` to `OclAny`, which ~60 call sites depend
   on, including `ExpIf:42` and `StandardOperationsSet:97` — measured on
   `if true then 1 else UReal(2,0.5) endif` and `Set{UReal(2,0.5)}->including(1)`.
2. **Before S8 relies on R-A** — correct P-03, R-A and B12 from four message constants in one place
   to **eleven strings across five classes in two packages** (§4.1), byte-exact including the
   `SBoolean` double spaces and `ExpConstUBoolean`'s "a Integer".

Recommended but not blocking: add DEP-28's uncertain-`sortedBy` sentence (§4.2); add a ranges row
(§4.3); fix T-02's fork-column subclass count; note that `upstream-test-waivers.md`'s "38 of 41 tests
never execute" caution is stale under `-Pupstream-oracle`, and record the **1086-test green
baseline** (§2.6) that S3's delta will be measured against.

**Waiver count after S3: one. Verified independently, and bounded by search, not assumption.**
