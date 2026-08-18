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
linkage fact, not a language feature, and `specification.md:1448` already records it correctly.

### 3.4 If the research later wants uncertain multiplicities

The place to add it is the **model finder's type mapping**, not the OCL type lattice — the blocker in
§3.2(b) is in the Kodkod transformer, and the crisp type would have to be supported first. That is
solver work, and ground rule 6 puts it out of scope. Recorded here so the option is not lost.

---

## 4. Net effect on S3

Added: five obligations (§2.3), all in `use-core/src/test`, none touching the type-system work.
Removed: nothing. The UUnlimitedNatural determination is **no new port work** — one line of vendoring
that was already required.
