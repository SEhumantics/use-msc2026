# 11 — Type system

Port specification, TYPE-SYSTEM section.
Scope: `org.tzi.use.uml.ocl.type` plus the one type-implementing class outside it
(`org.tzi.use.uml.mm.MClassifierImpl`).

**Status: compressed 2026-08-21.** This was originally a 762-line formal specification of the
uncertainty type lattice (conformance/allSupertypes/LCS grids for all 121 classical+uncertain type
pairs, a full per-pair deciding-code-path table, a file manifest, and a defects list). The port is
now shipped and tested (685+ tests passing), and every finding here has one of three homes:

1. The grids and file manifest are decided fact, unchanged since this doc was written — they now
   live in `docs/port2/specification.md` §3 (§3.1 conformance, §3.2 `allSupertypes`, §3.3 predicate
   battery, §3.4 `TypeFactory` entries, §3.5 hierarchy) and `adaptation-policy.md` §2 (the T-*
   decision rows). Read those for the full 121-cell tables — they are not reproduced here.
2. The maintainer-hazard findings (things a future reviewer could plausibly get wrong by looking only
   at a diff) are now `@implNote` Javadoc on the classes they concern, per the table in §2 below.
3. The still-open decisions (B11, and B11b raised by the companion adaptation doc) are tracked in
   `adaptation-policy.md` §2.4/§5 and are **not** re-litigated here.

The executable oracle this document was built from still exists and still runs against both trees:

```bash
cd /home/xoruser/msc-4/use-msc2026
bash docs/port2/spec-parts/11-types-oracle.sh
```

It compiles the historical fork's own type classes verbatim (`TypeFactory` reduced to its interned
simple-type accessors, copied character-for-character) and the 7.5.0 classes the same way, prints
`conformsTo`/`allSupertypes`/`getLeastCommonSupertype` for every pair, and asserts that the 49-cell
block restricted to classical types (`Boolean, Integer, Real, String, OclVoid, OclAny,
UnlimitedNatural`) is byte-identical between fork and 7.5.0 — i.e. the uncertainty extension is
purely additive and rewires no classic-type cell. That guarantee is the one fact from this document
worth restating outside the grids: **any port change that alters a classical-vs-classical
`conformsTo`/`allSupertypes`/LCS cell has a bug**, full stop, independent of anything else here.

---

## 1. Provenance (unchanged since the original write-up)

| | path |
|---|---|
| historical fork | `.git/reference-repositories/uncertainty/USE-Uncertainty` (HEAD `74acd0d2`) |
| fork type package | `<fork>/src/main/org/tzi/use/uml/ocl/type/` |
| target (7.5.0) | `use-core/src/main/java/org/tzi/use/uml/ocl/type/` |

Nothing in the original document was taken from `origin/main` or an earlier port attempt; every
grid cell was either read directly out of the named file (tagged **C**), produced by the oracle
script above (tagged **O**), or asserted by the fork's own JUnit test (tagged **T**). Those tags are
preserved in `specification.md` §3, which inherited this document's content wholesale.

### 1.1 The lattice, in one paragraph

Five new leaf classes (`UBooleanType`, `UIntegerType`, `URealType`, `UStringType`, `SBooleanType`,
all `extends BasicType` via the tag classes `UncertainType`/`UncertainBooleanType`) sit above their
crisp counterparts: `String < UString`, `Boolean < UBoolean < SBoolean`, `Real < UReal`,
`Integer < UInteger < UReal`, all also `< OclAny`. 39 of 121 ordered `conformsTo` pairs are `true`.
The Java `extends` hierarchy is **not** the conformance lattice — `SBooleanType` is a Java sibling of
`UBooleanType` but its conformance supertype; never infer conformance from `extends`. Full grids:
`specification.md` §3.1–§3.2.

---

## 2. Where each finding migrated

| finding (original section) | now lives at |
|---|---|
| Conformance / `allSupertypes` / LCS grids (§1.3–§1.6) | `specification.md` §3.1–§3.2 |
| Predicate battery (§1.7) | `specification.md` §3.3 |
| `TypeFactory` new entries (§2) | `specification.md` §3.4; `TypeFactory.mkSimpleType` `@implNote` (reserved-name shadowing hazard, C-10) |
| B11 — `UnlimitedNatural` lattice inconsistency (§1.8-1) | `UnlimitedNaturalType.allSupertypes()` `@implNote`; still **open**, tracked at `adaptation-policy.md` row T-15 |
| `TypeImpl.conformsTo` self-recursive stub (§1.8-2) | `TypeImpl.conformsTo()` `@implNote` |
| "Naming reads backwards" (§1.8-3) | folded into the `UBooleanType`/`UIntegerType`/etc. class Javadoc already present on those files |
| `IntegerType.conformsTo` deliberately unedited (§1.4, §3.8) | `IntegerType.conformsTo()` `@implNote` |
| Two `Type` implementation roots, ten no-op predicates (§3.4) | `TypeImpl` and `MClassifierImpl` class-level `@implNote` |
| `qualifiedName()` / `isKindOfDataType` — 7.5.0-only members a file copy would delete (§3.9–§3.10) | moot: no file-level copying occurred: this codebase was built by targeted edits from the start, not by pulling the fork's files over 7.5.0's |
| Two disagreeing LCS routines (§1.6, §3.5–§3.6) | `TypeImpl.getLeastCommonSupertype()` `@implNote`; full worked case on `UniqueLeastCommonSupertypeDeterminator` (B11b) |
| Tuple `allSupertypes` cartesian blow-up (implicit in §1.6, spelled out in `adaptation/01-types.md` C-09) | `TupleType.allSupertypes()` `@implNote` |
| File manifest — new vs edited files (§4) | moot post-port: `git log`/`git blame` on `use-core/src/main/java/org/tzi/use/uml/ocl/type/` is now the authoritative record of what was added vs edited |
| Test-suite shape delta (§3.11) | moot post-port: `TypeTest.java` and `UncertainTypeLatticeTest.java` are the current, passing record |

---

## 3. What is *not* superseded

- **B11** (`UnlimitedNatural` vs the uncertain numerics) is still an open decision, reproduced
  bit-for-bit per the standing recommendation (`adaptation-policy.md` row T-15,
  `specification.md` §9 row 11). It is not a stale finding — it is a live, accepted deviation.
- The oracle script (`11-types-oracle.sh`) is still the fastest way to re-verify any lattice claim
  against both the fork and 7.5.0 without touching Maven. Prefer running it over trusting a stale
  grid transcription.

---

## 4. Reproduction

```bash
cd /home/xoruser/msc-4/use-msc2026

# the conformance / supertype / LCS oracle, fork and 7.5.0 side by side, ending in the
# assertion that the classic 7x7 block is unchanged
bash docs/port2/spec-parts/11-types-oracle.sh

# which type files the fork adds vs edits (informational; the port itself is long since built)
diff -rq \
  .git/reference-repositories/uncertainty/USE-Uncertainty/src/main/org/tzi/use/uml/ocl/type/ \
  use-core/src/main/java/org/tzi/use/uml/ocl/type/

# proof that Type has exactly two implementation roots in 7.5.0
grep -rn "public boolean isTypeOfOclAny()" --include=*.java use-core use-gui
```
