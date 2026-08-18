# S3 — scope of record

Supersedes nothing; **adds** to the S3 scope implied by `adaptation-policy.md` (normative for S3–S9)
and `foundation-verdict.md` §1. Two additions, both requested 2026-08-18:

1. Resolve the **SBoolean** and **UString** evidence blindspots (§2).
2. A determination on **UUnlimitedNatural** (§3): **do not add.**

Every claim below names the file, symbol or command that produced it. Nothing here is argued from
plausibility.

---

## 1. S3 as previously scoped (unchanged)

The type-system foundation: `URealType`, `UIntegerType`, `UBooleanType`, `UStringType`,
`SBooleanType`, the two abstract tags `UncertainType` / `UncertainBooleanType`, `TypeFactory`
registration, and the lattice change (`Real ≤ UReal`, `Integer ≤ UInteger`, `Boolean ≤ UBoolean`,
`String ≤ UString`).

One upstream waiver lands here — `TypeTest#testSupertype`, 10 of 12 assertions — and is bounded by
measurement: the lattice moves **0 of 324** `conformsTo` cells, **0 of 324** pairwise LCS cells and
**0 of 1100** ULCS cells. Only `allSupertypes()` itself changes
(`adaptation-policy-refutation.md`). Two hazards carried in from the same review: `TupleType`
`allSupertypes` growth 3ⁿ+1 → 5ⁿ+1 (**730 → 15,626** at arity 6) and the order-dependence of
`UniqueLeastCommonSupertypeDeterminator` on the mutually-conformant `{Integer, UnlimitedNatural}`
pair, a latent 7.5.0 property this change makes visible.

---

## 2. ADDED — the SBoolean and UString blindspots

### 2.1 What was actually measured

A U-type has at most two independent evidence sources: the fork's own tests, and the differential
harness. The census:

| Type | Fork tests | Harness marshalling | Independent sources |
|---|---|---|---|
| `UReal` | `URealValueTest`, `URealExpOpsTest` | `UValue.java:274` | **2** |
| `UInteger` | `UIntegerValueTest`, `UIntegerExpOpsTest` | `UValue.java:279` | **2** |
| `UBoolean` | `UBooleanValueTest`, `UBooleanExpOpsTest` | `UValue.java:284` | **2** |
| `UString` | **none** | `UValue.java:289` (+ corpus, boundaries, `indexBoundaries()`) | **1** |
| `SBoolean` | **none** | **none** | **0** |

Fork test tree measured by `ls USE-Uncertainty/src/test/org/tzi/use/uml/ocl/{value,expr}/`. The
`SBooleanTest.java` / `SBooleanTest3.java` files are in the **uDataTypes library**, not the USE
integration, and test the datatype rather than the OCL binding.

The two blindspots are **not the same severity**, and an earlier note in this session overstated
UString's. Correction of record:

* **`UString` — single-sourced, not blind.** The harness marshals `UStringValue`
  (`UValue.java:289`), and `InputGenerator` carries `randomUString()`, `uStringCorpus()`,
  `uStringBoundaries()` and the UString-specific `indexBoundaries()` (`InputGenerator.java:330-337`,
  which records the measured 1-based `at`). Evidence exists; it just has no second source.
* **`SBoolean` — genuinely zero.** Marshalling is *deliberately* absent
  (`HistoricalOracle.java:130`), and `DifferentialHarnessRegressionTest.java:149` **asserts** it:
  `assertFalse(oracle.supports(UOp.binary("SBooleanValue", "and")))`. So its **39** operations (enum constants in
  the 1502-line `StandardOperationsSBoolean.java`) — by `wc -l` the largest file in the port, two
  lines longer than `StandardOperationsUString.java` (780) and `StandardOperationsUReal.java` (720)
  put together — currently have **no evidence source of any kind**.

### 2.2 SBoolean is also structurally unlike the other four

`grep -cE "class Op_" StandardOperationsSBoolean.java` → **0**; the same probe on
`StandardOperationsUReal.java` → **18**. SBoolean is a Java `enum` whose constants hold anonymous
`OpGeneric` instances, registered by looping `values()` (`StandardOperationsSBoolean.java:1495-1498`).
S3–S7 cannot copy the per-type pattern into S9. It also has a *definition* expression form with no
counterpart on any other U-type: `ExpDefSBoolean` / `ASTSBooleanDefExpression`.

### 2.3 S3 obligations (added)

**O-1 — `SBooleanValue` marshalling.** Extend `UValue` and `HistoricalOracle` to construct, drive and
canonicalise `SBooleanValue` on both sides. The print format is already measured and recorded:
`SBoolean(%5.3f, %5.3f, %5.3f, %5.3f)` verified by `javap -c` (`UValue.java:28`), and
`HistoricalOracle.java:808` records that `SBooleanValue.toString(StringBuilder)` routes every
component. Canonical form must be **type-bearing** (D-18) and must not reconstruct through `%5.3f`
(F4, `stage-01.md:505`) — rebuild from declared fields.

**O-2 — an SBoolean corpus that satisfies the opinion invariant.** A subjective-logic opinion is
`(belief, disbelief, uncertainty, apriori)` with `b + d + u = 1`. A corpus that ignores that yields
rows where *both* sides throw — which now scores `BOTH_THREW`, not agreement (D2), so it would
register as zero evidence rather than false green. Boundaries must include the named degenerate
classes the fork itself exposes as predicates: `isVacuous`, `isDogmatic`, `isAbsolute`, `isCertain`,
`isUncertain`, `isMaximizedUncertainty`.

**O-3 — flip the standing assertion.** `DifferentialHarnessRegressionTest.java:149` currently pins
`supports(...) == false`. It must be inverted in the same commit that lands O-1, or the suite will
pin the blindspot open.

**O-4 — discriminating power must be measured, not assumed, for both types.** Per D-15 a
single-point codomain gives agreement away for free. Report `discriminatingOperations` for `UString`
and `SBoolean` separately; an operation that is single-valued over its corpus is **not** evidence and
must be listed, not counted.

**O-5 — UString's single-sourcing is stated, not hidden.** Any fidelity claim about `UString` names
the harness as its *only* source. This is a disclosure obligation, not extra work.

**Ordering note.** O-1..O-4 are harness work in `use-core/src/test`, disjoint from the type-system
work in `use-core/src/main`. They may land in separate commits — and must, under ground rule 4, since
O-3 changes an assertion while O-1 changes behaviour.

---

## 3. ADDED — UUnlimitedNatural: **do not add**

### 3.1 What exists

| Where | Status |
|---|---|
| `uDataTypes/UUnlimitedNatural.java` | **complete**, 476 lines. Plus `UUnlimitedNaturals.java`, `N_UUnlimitedNatural.java` |
| `USE-Uncertainty/src/` (the fork's OCL binding) | **absent.** `grep -rn "UUnlimited" src/` → **zero lines** |
| Grammar `OCL.g:698` | the uncertain type-name rule is exactly `('UReal'\|'UInteger'\|'UBoolean'\|'UString'\|'SBoolean')` |
| Operation registry | no `toUUnlimitedNatural` registered anywhere under `expr/operations/` |

So the datatype layer has it and the language binding was never written. This is a **deliberate
non-integration, not a capability gap** — the library class is finished.

### 3.2 Why adding it is not indicated

**(a) It is outside what a port can verify.** The fork *is* the semantic oracle (proposal §7 claim 2).
An operation with no counterpart in the fork cannot be differentially verified — it would be the only
part of the tree with zero possible evidence, by construction. That is the exact failure mode S3–S10
exist to prevent. Adding it is language design, not porting.

**(b) The model finder refuses the crisp type already.** In `use-plugins/ModelValidator/trunk`:

```java
// src/org/tzi/use/kodkod/transform/ocl/SimpleExpressionVisitor.java:302-304
public void visitConstUnlimitedNatural(ExpConstUnlimitedNatural e) {
    throw new TransformationException("UnlimitedNatural not supported");
}
```

Its whole type vocabulary is `AnyType, BooleanType, EnumType, IntegerType, ObjectType, RealType,
SetType, StringType, UndefinedType` — no UnlimitedNatural of any kind. An uncertain version would
inherit that refusal on the first transformation. For **model finding for Uncertain-OCL specifically,
this is the decisive fact**: the work would not reach the solver.

**(c) The interesting value is the one the library forbids.** `UUnlimitedNatural` encodes `*` as
`x == -1`, and its own invariant excludes uncertainty there:

```java
if ((x == -1) && (u != 0.0)) throw new RuntimeException("Uncertainty of -1 is 0.0");
```

`*` is the entire reason UnlimitedNatural exists as a distinct OCL type — and it is precisely the
point that cannot carry uncertainty. What remains is `UInteger` with a non-negativity constraint plus
an isolated crisp point. The uncertainty semantics are close to vacuous.

**(d) In USE 7.5.0 the crisp type has one reachable value.** `ASTUnlimitedNaturalLiteral.gen()`
returns `new ExpConstUnlimitedNatural()` with no argument, and `ExpConstUnlimitedNatural.java:43`
always yields `UnlimitedNaturalValue.UNLIMITED`. `UnlimitedNaturalValue.valueOf(int)` is **never
called** from `use-core/src/main/java` or `use-gui/src/main/java` (0 hits). The remaining references
are argument casts in `StandardOperationsNumber.java` (lines 640–791) on the comparison operations.
So the type the uncertain version would extend is, in practice, the singleton `*`.

### 3.3 What is still required

**Vendoring is unaffected — `UUnlimitedNatural.java` must still ship.** `UReal.toUUnlimitedNatural()`
returns it, so it is in the compile closure even though no USE code calls it. Of the 23 library
`.java` files, only **five** are imported by the fork (`SBoolean`, `UBoolean`, `UInteger`, `UReal`,
`UString`, by `grep -rhoE "import uDataTypes\.[A-Za-z_]+"`); the rest are transitive. This is a
linkage fact, not a language feature. `specification.md:1448` records it, but overstates the
transitive set by two classes — see the correction in §5.5. Under the §5 purge this dependency is
removed outright and the vendored set drops to five classes.

### 3.4 If the research later wants uncertain multiplicities

The place to add it is the **model finder's type mapping**, not the OCL type lattice — the blocker in
§3.2(b) is in the Kodkod transformer, and the crisp type would have to be supported first. That is
solver work, and ground rule 6 puts it out of scope. Recorded here so the option is not lost.

---

## 4. Net effect on S3

Added: five obligations (§2.3), all in `use-core/src/test`, none touching the type-system work.
Removed: nothing from the type-system line. The UUnlimitedNatural determination **removes** three
classes and seven methods from the vendoring set (§5) and adds no port work at all.

Sections 5 and 6 below record the purge recipe and the model-finder position. Neither blocks S3's
type-system work; §5 executes when vendoring does (B1).

---

## 5. The UUnlimitedNatural purge — recipe, and when it can run

Decision 2026-08-18: **DECIDED — purge, not add** (user directive, "Purge it"). §3 gives the reasons.
This section gives the exact edit, and records that it **cannot execute yet** — `uDataTypes` has not been vendored (B1 is open), so this is a
vendoring-plan decision that lands when vendoring does.

### 5.1 Do not confuse the two types

`UnlimitedNatural` (crisp, USE 7.5.0 core) is **not** in scope and must not be touched. It is upstream
product source; removing it would breach ground rules 3 and 5 and would break `StandardOperationsNumber`
(argument casts at lines 640-791) and `ExpConstUnlimitedNatural`. **Only `UUnlimitedNatural` — the
uncertain one, which exists solely in the `uDataTypes` library — is purged.**

### 5.2 The measured compile closure

Seeded from the five classes the fork actually imports
(`grep -rhoE "import uDataTypes\.[A-Za-z_]+" USE-Uncertainty/src/` → `SBoolean`, `UBoolean`,
`UInteger`, `UReal`, `UString`) and followed transitively over the 23 library `.java` files:

| | Classes |
|---|---|
| **Closure as-is (6)** | `SBoolean`, `UBoolean`, `UInteger`, `UReal`, `UString`, **`UUnlimitedNatural`** |
| **Excluded already (17)** | `Distribution`, `DistributionGenerator`, `ExamplesSBoolean`, `N_UBoolean`, `N_UInteger`, `N_UReal`, `N_UUnlimitedNatural`, `SBooleanTest`, `SBooleanTest3`, `SBooleans`, `UBooleans`, `UEnum`, `UEnumTest`, `UIntegers`, `UReals`, `UUnlimitedNaturals`, `UncertaintyTest` |

`UUnlimitedNatural` is in the closure for exactly one reason: `UReal` and `UInteger` declare
conversions to it. Remove those and the closure drops to **five**.

### 5.3 The edit

Delete **7 methods**, then **3 classes**:

| File | Line | Member |
|---|---|---|
| `UReal.java` | 670 | `toUUnlimitedNatural()` |
| `UReal.java` | 677 | `toBestUUnlimitedNatural()` |
| `UInteger.java` | 532 | `toUUnlimitedNatural()` |
| `N_UInteger.java` | 465 | `toUUnlimitedNatural()` — *excluded anyway* |
| `UIntegers.java` | 157 | `static toUUnlimitedNatural(UInteger)` — *excluded anyway* |
| `UReals.java` | 330 | `static toUUnlimitedNatural(UReal)` — *excluded anyway* |
| `UReals.java` | 334 | `static toBestUUnlimitedNatural(UReal)` — *excluded anyway* |

Four of the seven sit in classes that are already outside the closure, so **only two files are really
edited**: `UReal.java` and `UInteger.java`. Then drop `UUnlimitedNatural.java`,
`UUnlimitedNaturals.java`, `N_UUnlimitedNatural.java`.

### 5.4 Why this is invisible to the oracle

`grep -rn "toUUnlimitedNatural" USE-Uncertainty/src/` returns **zero lines**, and no
`toUUnlimitedNatural` is registered under `expr/operations/`. The differential harness drives
*registered OCL operations* by name, so **no differential row can reach any deleted member**. The
historical jar is unchanged and its hash still verifies; removal is source-side only.

This is a **modernization/pruning** change under ground rule 4 and must not share a commit with any
behaviour change.

### 5.5 Correction to `specification.md`

`specification.md:1448` and `spec-parts/15-upstream-delta.md:729-730` state that `UUnlimitedNatural`,
**`UEnum` and `Distribution`** are "never imported but still needed on the classpath as transitive
return types." Measured: **only `UUnlimitedNatural` is.** Neither `Distribution` nor `UEnum` is
referenced by any class in the closure — `grep -n "Distribution\|UEnum" {UReal,UInteger,UBoolean,UString,SBoolean,UUnlimitedNatural}.java`
returns nothing, and neither appears anywhere in `USE-Uncertainty/src/`. The spec overstates the
transitive set by two classes. `UUnlimitedNatural` itself pulls in only `UBoolean`, `UInteger`,
`UReal`, all already present.

---

## 6. The model-finder position — what may and may not be claimed

Recorded because this travels into the thesis, where an imprecise version invites an easy objection.

### 6.1 What is true, measured

1. USE's Kodkod model finder **refuses the OCL `UnlimitedNatural` value type**:
   `ModelValidator/trunk/src/org/tzi/use/kodkod/transform/ocl/SimpleExpressionVisitor.java:302-304`,
   `throw new TransformationException("UnlimitedNatural not supported")`.
2. Its type vocabulary contains no UnlimitedNatural in any form: `AnyType`, `BooleanType`, `EnumType`,
   `IntegerType`, `ObjectType`, `RealType`, `SetType`, `StringType`, `UndefinedType`
   (`ls src/org/tzi/kodkod/model/type/`).
3. Refusing a construct is **normal practice** for this transformer, not an anomaly: 14 unsupported
   sites, `oclInState` refused at the adjacent line 283.

### 6.2 The caveat that must be stated with it

**`*` in a multiplicity is fully supported and is a different mechanism.** The finder has its own
`org.tzi.kodkod.model.impl.Multiplicity` with `public static final int MANY = -1` and a `Range` list,
built by `MultiplicityTransformator` (`ModelTransformator.java:156,218`). It never routes a
multiplicity through `UnlimitedNaturalValue`. So `0..*` associations model-find normally.

A claim phrased as "the model finder does not handle `*`" is **false** and will be caught. The true
claim is narrower: *the finder does not accept the OCL `UnlimitedNatural` **value type** in
expressions.*

### 6.3 The claim that is safe

> USE's model finder does not support the OCL `UnlimitedNatural` value type in expressions; it raises
> a transformation error. Uncertain-OCL model finding inherits no obligation to support an uncertain
> counterpart, and `USE-Uncertainty` defines none.

### 6.4 One refinement worth making instead of "we do the same"

The two situations are not identical, and ours is the **more** consistent of the two:

| | Evaluator | Model finder | Shape |
|---|---|---|---|
| Crisp USE | `UnlimitedNatural` **exists** | **refuses** it | asymmetric — a type you can write but cannot solve |
| This work | `UUnlimitedNatural` **absent** | absent | symmetric — nothing to refuse |

Upstream carries a gap between what its evaluator accepts and what its solver accepts. We do not
reproduce that gap; we have no such type on either side. That is a defensible position and a stronger
one than parity. Note also that `USE-Uncertainty` ships **no model finder at all** — it is an
evaluator — so no fork behaviour is being departed from here.

### 6.5 Residual, stated

If uncertain multiplicities ever become a research target, the crisp type would have to be supported
in the transformer **first** (§6.1 item 1 is the blocker), and the work belongs in the finder's type
mapping, not the OCL lattice. Ground rule 6 puts it out of scope now.

---

## 7. Coverage build-out — the design, measured

Directive 2026-08-18: **better test-case coverage for the U-types and SBoolean.** This section is the
implementable design. Every signature and constant below was read from source, not assumed.

### 7.1 Where coverage stands today

`InputGenerator` boundary sets, counted:

| Set | Entries | Known defect |
|---|---|---|
| `uRealBoundaries()` | 22 | — |
| `uIntegerBoundaries()` | 13 | — |
| `uStringBoundaries()` | 16 | **D-31**: `uSubstring(int,int)` reaches **17 measured rows of 432** |
| `uBooleanBoundaries()` | 9 | — |
| `stringBoundaries()` | 14 | — |
| `booleanBoundaries()` | — | **D-42**: reports `boolean=4` for a 2-inhabitant type |
| `zeroDivisors()` | 7 | — |
| `indexBoundaries()` | 8 | source of D-31 |
| **`sBooleanBoundaries()`** | **absent** | the whole of §2.1 |
| `realBoundaries()` | — | **D-28**: a single `RealValue` |

### 7.2 SBoolean marshalling — the exact route

**The 4-arg constructor is package-private.** `SBooleanValue.java:18`,
`SBooleanValue(double b, double d, double u, double a)` — no modifier. The harness must **not**
`setAccessible`; it takes the documented public path, exactly as it already does for `UBooleanValue`
whose `uDataTypes.UBoolean` constructor is package-private (`HistoricalOracle.java` `case UBOOLEAN`).

The public path is the **Builder** (`SBooleanValue.java:28-70`):

```java
new SBooleanValue.Builder().belief(b).disbelief(d).uncertainty(u).agent(a).build()
```

**`build()` interns two points.** `belief==1 && disbelief==0 && uncertainty==0 && agent==1` returns the
shared `TRUE` constant; `(0,1,0,1)` returns `FALSE`. Those two rows therefore take a *different code
path* from every other opinion and must both be in the corpus — an identity-versus-equality difference
is exactly the kind of thing this harness exists to catch.

### 7.3 The validity invariant — corpus-defining

`uDataTypes/SBoolean.java:43-52`, after `adjust()` on each component:

```java
if (Math.abs(this.b + this.d + this.u - 1.0D) > 0.001D ||
    this.b < 0.0 || this.d < 0.0 || this.u < 0.0 || this.a < 0.0 ||
    this.b > 1.0 || this.d > 1.0 || this.u > 1.0 || this.a > 1.0)
    throw new IllegalArgumentException("SBoolean constructor: Invalid parameters. ...");
```

Three facts that a naive corpus would miss:

1. **The sum tolerance is `0.001`, not exact.** `b+d+u` may miss 1.0 by up to a thousandth and still
   construct. So `±0.0005` (constructs) and `±0.0015` (throws) are real boundaries, and any corpus that
   only emits exact-sum triples never probes the tolerance edge.
2. **`a` is not in the sum.** The base rate is independent; its only constraint is `[0,1]`. A corpus
   that varies `a` while holding `(b,d,u)` fixed is therefore *free* discriminating power.
3. **Random 4-tuples are useless.** Almost none satisfy the sum, so almost every row would throw on
   both sides. Since D2 that scores `BOTH_THREW`, not agreement — it fails visibly rather than falsely,
   but it is still zero evidence. **The generator must construct on the simplex**, e.g. draw
   `(b,d,u)` from a Dirichlet-style normalisation and `a` independently on `[0,1]`.

### 7.4 `sBooleanBoundaries()` — the required classes

The fork exposes named predicates that partition the opinion space; each must have at least one
witness, or that predicate's operations are single-valued and give agreement away for free (D-15):

| Class | Witness `(b, d, u, a)` | Reached predicate |
|---|---|---|
| absolute true (interned) | `(1, 0, 0, 1)` | `isAbsolute`, `isDogmatic`, `isCertain` — **`TRUE` identity** |
| absolute false (interned) | `(0, 1, 0, 1)` | `isAbsolute` — **`FALSE` identity** |
| vacuous | `(0, 0, 1, a)` | `isVacuous`, `isMaximizedUncertainty` |
| dogmatic, non-absolute | `(0.5, 0.5, 0, a)` | `isDogmatic`, not `isAbsolute` |
| uncertain, generic | `(0.3, 0.2, 0.5, 0.5)` | `isUncertain` |
| base-rate extremes | `(b, d, u, 0)` and `(b, d, u, 1)` | `baseRate`, `projection`, `applyOn` |
| tolerance edge, valid | sum `= 1 ± 0.0005` | the `0.001` band |
| tolerance edge, invalid | sum `= 1 ± 0.0015` | must throw on **both** sides |
| non-interned unit points | `(1, 0, 0, 0.5)`, `(0, 1, 0, 0.5)` | same masses, **not** `TRUE`/`FALSE` |

The last row is the one that catches an implementation that compares by identity rather than by value.

### 7.5 Harness changes required

| # | File | Change |
|---|---|---|
| C-1 | `UValue.java` | `Kind.SBOOLEAN`; a 4-component factory. **Do not widen the private constructor** — it has ten call sites on an audited file. Carry the components in the existing `elements` list as four `REAL` values, and give `canonical()` an `SBOOLEAN` branch. Type-bearing per D-18. |
| C-2 | `HistoricalOracle.java` | `case SBOOLEAN` in `toHistorical`, via the Builder of §7.2 — never `setAccessible`. |
| C-3 | `HistoricalOracle.java` | add `"SBooleanValue"` to `MARSHALLABLE_RECEIVERS` (`:134-136`). The comment at `:128` requires this set to stay in step with the `toHistorical` switch, so C-2 and C-3 land together or neither. |
| C-4 | `InputGenerator.java` | `sBooleanBoundaries()` per §7.4 and `sBooleanCorpus(int)` per §7.3 item 3. |
| C-5 | `DifferentialHarnessRegressionTest.java:149` | invert the `assertFalse(oracle.supports(...))` pin. Same commit as C-2/C-3, else the suite pins the blindspot open. |

Results need no work: `fromHistorical` already routes an unmodelled `Value` to `Kind.OPAQUE` through
`opaqueRepresentation`, which rebuilds from declared fields and so is not subject to the `%5.3f`
rounding trap (F4).

### 7.6 Sequencing — what this does and does not buy

**It does not produce evidence on its own.** The ported side has no `SBooleanValue` class yet — the
ported side today is `StubCandidate`, and the real one arrives at S9. So C-1..C-5 make SBoolean
*drivable* and let the historical reference corpus be recorded now; the comparison becomes meaningful
when S9 lands. That is the correct order — building the instrument before the thing it measures — and
it is why these are S3 obligations rather than S9 ones. Doing it at S9 would mean porting 1502 lines
with the instrument still unbuilt.

### 7.7 U-type widening

Separate, smaller, and independent of SBoolean:

* **D-28** — `realBoundaries()` holds one `RealValue`. Widen to match `uRealBoundaries()`'s crisp
  points (zero, negative zero, negatives, extrema).
* **D-42** — the census prints `boolean=4` for a two-inhabitant type. That is a census bug, not a
  corpus gap; fix the count, do not add inputs.
* **D-31** — `uSubstring(int,int)` sits at 17 of 432 because `indexBoundaries()` is drawn for the
  1-slot `at(int)`. A 2-slot product for the 2-slot operation is what closes it. H14 §5 already
  records that the cell census will read near zero here until it is done.

Each widening changes measured tallies, so under ground rule 4 each lands in its own commit with the
before/after numbers in the message.

---

## 8. Test design for the U-types — the general scheme

§7 handled SBoolean because it had nothing. This section is the scheme for **all five**, and it exists
because per-type boundary lists alone do not test the thing that makes a U-type a U-type.

### 8.1 The shape of the problem

Every U-type is a **pair**: a representative and a degree.

| Type | Representative | Degree | Note |
|---|---|---|---|
| `UReal` | real | standard uncertainty ≥ 0 | normal standard deviation |
| `UInteger` | integer | uncertainty | widens to `UReal` for comparison |
| `UBoolean` | truth value | probability | **canonicalised** — see 8.3 |
| `UString` | spelling | confidence | |
| `SBoolean` | — | `(b, d, u, a)` on a simplex | 4 components, not 2 |

So a unary operation has a **2-dimensional** input domain and a binary operation a **4-dimensional**
one. A boundary list is a set of *points*; it says nothing about whether the two dimensions were ever
varied against each other. That gap is where uncertainty-specific defects live, because the
uncertainty-propagation rule is precisely the part that reads both dimensions at once.

Current corpora are point lists (§7.1). This is the measurable weakness they share, and it is not
fixed by making the lists longer.

### 8.2 Rule 1 — vary the degree against the representative, not beside it

For each type, the corpus must contain at least one **pair of inputs sharing a representative and
differing only in degree**, and one sharing a degree and differing only in representative.

Why it is not optional: an operation that silently drops the uncertainty component — returns the right
representative with degree `0`, or copies the receiver's degree instead of propagating — agrees on
every single-point corpus that never holds the representative fixed. This is the same species as
D-15: the corpus, not the port, decides what can be seen.

`uRealBoundaries()` has 22 entries and does contain such pairs; `uBooleanBoundaries()` at 9 is the
thinnest. Each type's boundary set must be **audited for this property and the count reported**, not
assumed.

### 8.3 Rule 2 — the canonicalisation identities are test cases

`UBoolean` canonicalises to one probability of truth: `UBoolean(false, 0.9)` becomes probability
`0.1` (proposal §2, "Verified U-types fork/port facts"). So `UBoolean(false, p)` and
`UBoolean(true, 1−p)` are **the same value reached by two constructions**.

Both constructions must be in the corpus, and their results must be identical on every operation. A
port that canonicalises on one path and not the other passes a corpus containing only one of them.
The same obligation applies to `SBoolean`'s interned `TRUE`/`FALSE` (§7.2) — the non-interned twins in
§7.4 exist for exactly this reason.

### 8.4 Rule 3 — pairwise, not Cartesian

Full Cartesian coverage of a binary operation over a 22-point corpus is 484 rows per operation, and
`uSubstring(int,int)` shows where that leads: **17 measured rows of 432** (D-31). The tractable
standard is **all-pairs over dimensions**: every pair of (dimension, equivalence-class) values appears
together in at least one row.

For a binary `UReal` operation the dimensions are ⟨receiver representative, receiver uncertainty,
argument representative, argument uncertainty⟩. All-pairs over 4 dimensions of ~5 classes each is
tens of rows, not hundreds — and it is a *stated, checkable* criterion, which a hand-picked list is
not. This is the concrete form of H14's candidate C3, and D-31 is the standing evidence that the
ad-hoc alternative under-covers by an order of magnitude.

### 8.5 Rule 4 — metamorphic relations, which need no second source

**This is the part that closes the SBoolean and UString blindspot without waiting for S9.** Each
relation below is a property of the ported code checked against *itself*, so it yields evidence where
there is no fork test and no ported counterpart yet:

| # | Relation | Applies to | What it catches |
|---|---|---|---|
| M-1 | **Crisp embedding.** `op(U(x, 0), U(y, 0))` must carry the same representative as the crisp `op(x, y)`, degree `0` | UReal, UInteger, UBoolean, UString | a propagation rule that perturbs a certain input |
| M-2 | **Degree monotonicity.** Raising an input's uncertainty must not lower the result's | UReal, UInteger | an inverted or dropped propagation term |
| M-3 | **Canonicalisation.** `UBoolean(false, p)` ≡ `UBoolean(true, 1−p)` on every operation | UBoolean | 8.3 |
| M-4 | **Widening agreement.** A `UInteger` operation and its `UReal` widening must agree where both are defined | UInteger | the documented widening (§2 of the proposal) |
| M-5 | **Interning independence.** A value equal to `TRUE`/`FALSE` but not the interned instance behaves identically | SBoolean | identity-vs-equality comparison |
| M-6 | **Simplex closure.** Any operation returning an `SBoolean` returns one satisfying `\|b+d+u−1\| ≤ 0.001` | SBoolean | a fusion operator that leaves the simplex |

M-6 is worth singling out: SBoolean's 39 operations are mostly **fusion operators**, whose entire
correctness condition is that they map opinions to opinions. Checking closure is a real oracle for all
of them and costs one assertion per row. For a type with **zero** other evidence, that is the
difference between "unverified" and "verified against its defining invariant."

M-1..M-6 are ordinary JUnit tests in `use-core/src/test`, independent of the differential harness, and
they do not need the historical jar.

### 8.6 What this scheme still cannot see

Unchanged from `foundation-verdict.md` §2.1 and restated so no figure travels without it: a defect
reachable only at an input no corpus generates stays invisible, and every coverage figure here is
**corpus- and seed-conditional**. All-pairs is a stated criterion, not a guarantee — it bounds the
2-way interaction space, not the 3-way one. D-30 is not closed by any of this.

---

## 9. Impact on the thesis proposal

Checked against `output/latex/robust_utype_model_finding_proposal.tex` (2571 lines) on 2026-08-18.

### 9.1 UUnlimitedNatural — no change required, one addition recommended

`grep -n "UnlimitedNatural\|UUnlimited"` over the proposal returns **zero lines**. Nothing in the aim,
the research questions, the version-1 fragment, the studies or the success criteria depends on it, so
the §5 purge invalidates no claim.

The proposal already carries **independent corroboration** of §6's finding, from a different file than
the one measured here: §2 records that `TypeConverter.convert(...)`
(`ModelValidator/trunk/src/org/tzi/use/kodkod/transform/TypeConverter.java:63-76`) is a six-arm
dispatch — "void, **the four OCL primitives sharing one arm**, enum, class, `OclAny`, collection".
Four, not five. So the Kodkod baseline excludes `UnlimitedNatural` at the *type* layer as well as at
the expression layer (`SimpleExpressionVisitor.java:304`). Two independent sites, same conclusion.

**Recommended addition, one line.** The version-1 fragment (§4) states an "Outside version 1" column
per area. `UnlimitedNatural` should be named there explicitly under "OCL core". Silence invites the
question; an explicit exclusion answers it — and the answer is now strong, because the capability
baseline does not support it either, so nothing is conceded in the comparison. This is an addition for
completeness, not a correction.

### 9.2 SBoolean — a genuine scope tension, for a human to settle

`SBoolean` appears **twice in 2571 lines**: once at `:1040`, in the version-1 fragment's *"Outside
version 1"* column, and once at `:2084` in related work. Against that, "four U-types" / "four-type" /
"all four" appears **28 times**, Study A is *"four-type semantic agreement"*, and success criterion 1
reads "All four U-types are synthesized and reconstructed".

**The proposal is consistently a four-type thesis. SBoolean is explicitly out of version 1.**

The port decision (B2) is *full port*, which makes S9 = SBoolean: **1502 lines, 39 operations, zero
fork tests, and new harness marshalling** — the single largest cost item in the port — supporting **no
version-1 thesis claim**.

Both positions are defensible and this is not the porter's call:

* **Keep the full port.** The port is the semantic oracle and a faithful oracle is hygiene; selective
  porting is what made the previous attempt unauditable. SBoolean is also the natural version-2 target.
* **Descope S9.** No version-1 claim needs it, and the proposal already excludes it in writing.

**Recommendation: keep the port, but split the cost and do not let it gate anything.** The harness
work (§7.5, C-1..C-5, ~110 lines) is cheap and should proceed — it makes SBoolean drivable and lets the
historical reference corpus be recorded. The 1502-line port itself stays last in the order and is the
natural thing to defer under time pressure. Nothing else in S3–S8 depends on it.

Note that §8.5's M-5 and M-6 raise SBoolean from *zero* evidence to *invariant-checked* for a few
dozen lines of test, independent of both the fork and S9. That materially changes the descope
calculus: the cheap part of SBoolean assurance no longer depends on the expensive part.

### 9.3 The Z3 / Kodkod framing is already correct

No change needed. §8 already states: "Only Z3 is implemented. The baselines compare semantics and
capabilities, not solver brands," and "The current Model Validator remains a capability baseline."
Crisp-type support evaluated against the Kodkod finder is consistent with that as written.

One consequence worth making explicit somewhere in §8: because the baseline drops U-typed attributes
**silently** (`TypeConverter` logs and returns `null`; `ModelTransformator.java:141-151` drops the
attribute with no `else`), a capability comparison must report *what the baseline silently omitted*,
not merely that it returned an answer. The proposal already names this — "a silent drop with a logged
cause, which is worse for this thesis than a crash" — but it appears in §2 as a provenance fact rather
than in §8 as an evaluation obligation.
