# Refutation of `adaptation-policy.md`

**Role:** refuter, pre-implementation verification pass. I wrote none of the policy or the six area
documents. I owned Maven for this round and probed the fork and 7.5.0 directly with `javac`/`java`.

**Verdict: SOUND_WITH_DOCUMENTED_LIMITS — fit to govern S3, subject to two must-fix text
corrections (§1). Both are now resolved; see the status note under each.**

This pass confirmed, by independent measurement, that the policy's two most load-bearing claims both
hold: the worked example is right, and the waiver count is exactly one, bounded tighter than the
policy itself argued (0 of 324 `conformsTo` cells, 0 of 324 pairwise `LCS` cells, 0 of 1100 `ULCS`
cells move; only `allSupertypes()` itself does). It also ran the gate the policy could not run under
ground rule 3: **1086 upstream tests green**, `ShellIT` included. Everything this pass confirmed by
replaying source and executing probes is now superseded by the shipped, tested port — S3–S9 are done
and both acceptance commands are green (`harness-contract.md` §0.1) — so the confirmatory work itself
(the worked-example replay, the waiver-count search, the nine-row INFERRED/READ_FROM_SOURCE probe
table, the operation-lattice sweeps) is not reproduced here. What follows is the verdict and the two
corrections, which are the only parts of this document with continuing reference value.

---

## 1. The two must-fix corrections

### 1.1 B5's stated reason for the worked example — CORRECTED, in `upstream-test-waivers.md`

`adaptation-policy.md` §5.1's B5 row and W-01's original "why the alteration is correct" field both
said option 2 (conformance widened one-way, `allSupertypes()` left alone) "breaks
`getLeastCommonSupertype`, which is what drives overload resolution **and the worked example**." That
last clause is false: `UniqueLeastCommonSupertypeDeterminator.calculateFor` selects using `conformsTo`
alone (`allSupertypes()` only seeds the candidate pool with each input type's own closure), and under
option 2 `UReal` still enters and wins on `Set{UReal(2,0.5), 1, 2.5}` — the worked example survives.

The real, stronger reason the verdict (option 1) is still correct: option 2 collapses **pairwise**
`getLeastCommonSupertype` to `OclAny` on ~60 call sites (`ExpIf:42,48` and the
`StandardOperationsSet/Bag/Sequence/OrderedSet/Collection/Any` registries) — measured on
`Set{UReal(2,0.5)}->including(1)` (becomes `Set(OclAny)` under option 2) and
`if true then 1 else UReal(2,0.5) endif` (becomes `OclAny`). That is a much larger surface than "the
worked example" and it is the argument that actually holds.

**Status: applied.** `upstream-test-waivers.md` §3 ("why the alteration is correct") now carries the
corrected sentence; confirmed by reading that file's current text. Not re-touched here.

The worked example itself, for reference (measured on both sides, cited from `UncertainExpressionTypingTest.java`
and `stage-05-08.md` by this document's name):

| expression | fork (2021 jars) | plain USE 7.5.0 |
|---|---|---|
| `Set{UReal(2,0.5), 1, 2.5}` | `Set(UReal)` / `Set{1,2.5,UReal(2.0, 0.5)}` | compile error, `` Undefined operation `UReal'. `` |
| `Set{UReal(2,0.5), 1, 2.5}->sum()` | `UReal` / `UReal(5.5, 0.5)` | compile error |
| `Set{1, 2.5}` *(control)* | `Set(Real)` / `Set{1,2.5}` | `Set(Real)` / `Set{1,2.5}` — both agree |
| `1/0` | `Undefined : OclVoid` | `null : OclVoid` |

### 1.2 P-03/R-A/B12's message-constant count — four is wrong, it is eleven

P-03 said "four bare messages... thrown at `ASTURealLiteral` :28,31 and `ASTUBooleanLiteral` :31". R-A's
guard said "pin the four strings as constants in one place". B12's acceptance criterion said "5 via the
error path with the four message constants unmodified". Binary-scanning every class in the fork
`use.jar` for the producers found **five classes across two packages** (`ASTURealLiteral`,
`ExpConstUInteger`, `ExpConstUBoolean`, `ExpConstUString`, `ExpConstSBoolean`) and **eleven** distinct
position-less strings, not four in one place — and the `UBoolean` message is actually in
`ExpConstUBoolean` (`uml/ocl/expr`), not `ASTUBooleanLiteral` (`parser/ocl`) as P-03 claimed. All eleven
lack a `src:line:col` prefix (`new SemanticException(String)` with a null `SrcPos`); only four of the
eleven have a corpus witness, so a guard scoped to four is not a guard for the other seven.

**Status: applied.** `adaptation-policy.md`'s P-03, R-A and B12 rows (§2 Tier 3 and §6) now state the
corrected count and attribution. This document's own confirmatory probe (the eleven-string dump, the
byte-exact double-space and "a Integer" details) is not reproduced here; it lives in git history and
in the corrected rows themselves.

---

## 2. What else this pass confirmed, briefly

Everything below was re-verified against the fork and 7.5.0 by direct measurement and found sound; no
further corrections followed from it, so it is recorded here as a one-line verdict per topic rather
than replayed:

* **The 72-row adaptation table's nine non-MEASURED rows** (one INFERRED, eight probed
  READ_FROM_SOURCE) — all nine survived probing, three (K-04, V16, P-09/V03) were strong enough to
  upgrade to MEASURED.
* **No row in the table quietly drops uncertainty meaning for convenience** — checked specifically
  against the four best candidates (B10/DEP-33, DEP-28, DEP-26, K-12/O-02/O-10); all four are
  policy-consistent.
* **B4's registration precondition** — the policy's claimed 7.5.0 measurements for `(1).equals(1)`
  etc. do not replicate on stock 7.5.0 without first registering `Op_identical` in the opmap; once
  registered (zero grammar edit), every claimed result reproduces, including fork defects D1/D2 fixed.
  MINOR, since the port instruction already named the registration — B4's substantive answer (option 1)
  is correct.
* **B8, B9 re-checked as genuinely determined**, not merely preferred: B8 because the alternative
  registration order changes 22 of 74,970 cells of ordinary non-uncertain OCL, forbidden outright by
  the policy's second sentence; B9 because the "additive middle" reaches an unguarded
  `(BooleanValue)` cast and throws.
* **Minor factual slips found and left for the owning document to fix on its own schedule**: T-02's
  fork-column subclass count (2 stated, 4 measured — the 7.5.0 count of 6 and the port instruction
  were both already right); §1.3's "39 enum constants" should read "39 anonymous inner classes" (count
  exact, kind mis-stated); a missing DEP-28 sentence on uncertain-`sortedBy`; no row covering OCL
  ranges (low-risk, since the `isTypeOf`-exact guard means the fork lattice cannot reach it anyway).

**Recommended but not blocking, left as-is:** the `upstream-test-waivers.md` "38 of 41 tests never
execute" caution is stale under `-Pupstream-oracle` and the 1086-test green baseline this pass measured
supersedes it; whoever next edits that file should fold it in.
