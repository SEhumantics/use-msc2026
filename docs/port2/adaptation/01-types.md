# 01 — Adaptation: the type system and the lattice

**Area:** `org.tzi.use.uml.ocl.type` + `org.tzi.use.uml.mm.MClassifierImpl` (the second `Type` root).

**Governing policy (user's, not open for debate):**

> Uncertainty meaning comes from the fork. Everything else comes from USE 7.5.0.
> Where the two collide, keep the uncertainty behaviour but express it the 7.5.0 way.

**Status: compressed 2026-08-21.** This was originally a 712-line "area reader" analysis (two
independent oracles — a 2021 JAR and source-compiled classes — cross-checked cell-for-cell against
plain 7.5.0) that fed the T-* decision rows in `docs/port2/adaptation-policy.md`. The port is now
shipped and tested. What follows keeps only what is still load-bearing: the one decision that is
still open (§1, kept in full because live code cites it — do not renumber this section), and a table
of where everything else migrated to.

---

## 1. C-08 / B11b — `UniqueLeastCommonSupertypeDeterminator` is nondeterministic on mutually-conformant pairs

**This section is cited by name from live code**
(`UniqueLeastCommonSupertypeDeterminator.java`, `UncertainTypeLatticeTest.java`) — its anchor
`C-08` must not be renamed or removed even as this document is otherwise compressed.

`Integer` and `UnlimitedNatural` are the only mutually-conformant pair in either the fork's lattice
or plain 7.5.0's (`Integer.conformsTo(UnlimitedNatural)` and the reverse are both `true`). The
`calculateFor` tie-break was `else if (t.conformsTo(result)) result = t;`, a greedy scan with no
fixpoint for a mutually-conformant pair — the winner was whichever element a `HashSet` (ordered by
`BasicType.hashCode()`, the JVM's lazily-assigned identity hash of the type's `Class` object)
happened to yield last. Measured: same fork binary, only the order in which identity hashes were
first requested varied, and the answer for `ULCS({Integer, UnlimitedNatural})` flipped between
`Integer` and `UnlimitedNatural` deterministically with that order. **The same instability was
measured in plain 7.5.0**, not just the fork — it is a pre-existing upstream latent defect that the
wider uncertain lattice merely made *reachable* (`Set{*, 1}` observably returns different types
across runs pre-fix), because growing `allSupertypes()` grows the candidate set the greedy scan
walks.

This is **not** covered by B11's waiver (B11 is about `UnlimitedNatural`'s missing `allSupertypes`
entries; this is about `calculateFor`'s tie-break, and it bites even with B11 reproduced verbatim).
Named **B11b**, a new decision this document raised.

**Fixed.** `calculateFor` now iterates a `TreeSet` ordered by `Type::toString` instead of a
`HashSet`, making every step a pure function of the input types rather than of JVM-internal
identity-hash order. Full rationale and the worked trace are in the `@implNote` on
`UniqueLeastCommonSupertypeDeterminator.calculateFor()`; regression coverage is
`UncertainTypeLatticeTest#mutuallyConformantPairResolvesToTheSameDeterministicAnswer`. **Which** of
the two mutually-conformant types wins is unchanged and still not itself decided — only that the
answer is now the same call every time.

---

## 2. Where everything else migrated

| id | finding | now lives at |
|---|---|---|
| C-01 | assumed interface/class collision doesn't exist | moot — `adaptation-policy.md` row T-01 |
| C-02 | 10 new `Type` predicates, two implementation roots, six classifier leaves | `TypeImpl` + `MClassifierImpl` class-level `@implNote`; `adaptation-policy.md` row T-02 |
| C-03 | 7.5.0-only members (`qualifiedName`, DataType pair) a file copy would delete | moot post-port — no file-level copy occurred |
| C-04 | `IntegerType.conformsTo` unedited; edge installed from the supertype side | `IntegerType.conformsTo()` `@implNote`; `adaptation-policy.md` row T-04 |
| C-05 | `allSupertypes()` generics: wide on new types, narrow on edited upstream ones | settled as shipped — `adaptation-policy.md` row T-05; no further note needed |
| C-06 | `VoidHandling` overrides | settled as shipped — `adaptation-policy.md` row T-06; no further note needed |
| C-07 | `UniqueLeastCommonSupertypeDeterminator` byte-identical but load-bearing; disagrees with pairwise LCS | `TypeImpl.getLeastCommonSupertype()` `@implNote`; `adaptation-policy.md` row T-07 |
| C-08 | ULCS nondeterminism on mutually-conformant pairs (B11b) | §1 above (kept in full — live-cited) |
| C-09 | `TupleType.allSupertypes()` cartesian blow-up, base 3→5 | `TupleType.allSupertypes()` `@implNote`; `adaptation-policy.md` row T-09 |
| C-10 | five U-type names reserved via `buildInTypesMap`, shadow user classifiers | `TypeFactory.mkSimpleType()` `@implNote`; `adaptation-policy.md` rows T-10/G-04 |
| C-11 | `TypeImpl` LCS fast-path stops firing with wider supertype sets, same answer | settled as shipped — `adaptation-policy.md` row T-11; no further note needed |
| C-12 | `TypeImpl.conformsTo` self-recursive stub, `StackOverflowError` if unoverridden | `TypeImpl.conformsTo()` `@implNote`; `adaptation-policy.md` row T-12 |
| C-13 | cosmetic fork anomalies (`mkUReal()` return type, ctor visibility, `this` vs `mkX()`) | applied as shipped exactly per the recommendation — `adaptation-policy.md` row T-13; no further note needed, nothing left ambiguous for a reader of the code |
| C-14 | `EnumType extends MClassifierImpl`, not `TypeImpl` | folded into `MClassifierImpl`'s C-02 `@implNote`; `adaptation-policy.md` row T-14 |
| C-15 | = B11, `UnlimitedNatural` left out of numeric widening | `UnlimitedNaturalType.allSupertypes()` `@implNote`; **still open**, `adaptation-policy.md` row T-15 |
| §6 waiver verdict (`TypeTest#testSupertype`, 10/12 assertions) | one waiver, correctly scoped | `docs/port2/upstream-test-waivers.md` |

For the full measured grids (conformance, `allSupertypes`, both LCS routines, the end-to-end
compiler evidence) and the T-* decision table, see `docs/port2/adaptation-policy.md` §2 and
`docs/port2/specification.md` §3 — both untouched by this compression and safe to cite directly.

---

## 3. Reproduction

```bash
cd /home/xoruser/msc-4/use-msc2026
bash docs/port2/spec-parts/11-types-oracle.sh /tmp/typeoracle   # §1-3 conformance/allSupertypes/LCS, fork sources + 7.5.0 baseline
```

The 2021-JAR cross-check and the `/tmp/{forkprobe,cprobe,ulcs,tupprobe,upstest}/` driver programs
this document originally used are scratch files (not checked in) and are regenerable from the
listings that were in the pre-compression version of this file if the independent-oracle
cross-check ever needs to be redone; the source-oracle path above is the one worth keeping handy.
