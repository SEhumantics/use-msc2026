# S3 — the type-system foundation

**Status: COMPLETE.** Five commits, one waiver, gate green on both builds.

| | |
|---|---|
| `486f7f6c` | S3(1/2) — the five uncertain types and their predicate surface. Additive; lattice untouched; **zero** waivers |
| `c71f35d1` | S3(2/2) — the fork's lattice. Behaviour change; **waiver W-01** |
| `fa355639` | TupleType memoisation + structural immutability (perf, no semantic change) |
| `8a279683` | `getLeastCommonSupertype` tuple short-circuit (perf, provably equivalent) |
| `8e05ba59` | Track B — SBoolean marshalling; 39 operations become driveable |

Gate at S3 close (`scripts/upstream-oracle-gate.sh`, mode `both`, **PASS**):

| Build | use-core surefire | use-core failsafe | use-gui surefire | use-gui failsafe |
|---|---|---|---|---|
| default | 9 / 85 | 1 / 1 | 1 / 1 | 1 / 129 |
| `-Pupstream-oracle` | 42 / 356 (944 exec) | 1 / 1 | 8 / 17 | 1 / 129 |

0 failures, 0 errors in either.

---

## 1. What landed

**7 new files** — `UncertainType`, `UncertainBooleanType` (pure `instanceof` tags) and the five
leaves `URealType`, `UIntegerType`, `UBooleanType`, `UStringType`, `SBooleanType`.

**5 upstream product files edited** — `Type` (+10 declarations), `TypeImpl` (+10 default bodies),
`MClassifierImpl` (+10 default bodies), `TypeFactory` (+5 singletons, +5 `mk*`, +5 name
registrations), and the four crisp basic types for the lattice.

**1 new test file** — `UncertainTypeLatticeTest`, 5 methods, pinning the *purpose* of the lattice
change independently of the modified upstream test.

## 2. The finding the specification missed — `MClassifierImpl`

Adding the ten predicates to the `Type` **interface** broke six classes that implement `Type`
without extending `TypeImpl`: `EnumType`, `MClassImpl`, `MAssociationImpl`, `MAssociationClassImpl`,
`MSignalImpl`, and the 7.5.0-only `MDataTypeImpl`.

The fix was measured, not invented: the fork carries the same ten predicates in its own
`MClassifierImpl` (`isTypeOfUString` at `:401`). Applying the same remedy fixes all six through the
shared base. `MDataTypeImpl` does not exist in the fork and is covered for free.

This is the second time this area has surprised the plan, and both times in the direction of *less*
work than budgeted elsewhere: the earlier audit found the assumed `Type` interface-vs-class migration
did not exist at all (C-01).

## 3. The lattice change is conservative — measured

Fingerprints over a 12-type crisp universe, before and after, same build:

| Metric | Before | After | |
|---|---|---|---|
| `conformsTo`, 144 cells | `-1429835451` | `-1429835451` | identical |
| pairwise LCS, 144 cells | `1606464704` | `1606464704` | identical |

Only `allSupertypes()` moves. Independently consistent with the round-8 refutation (0 of 324, 0 of
324, 0 of 1100 cells). Empirically: **one** failing method across the whole reactor.

## 4. Validation — the change achieves its purpose

`UniqueLeastCommonSupertypeDeterminator` over the fork's worked example:

```
{UReal, Integer, Real}         -> UReal      <- element type of Set{UReal(2,0.5), 1, 2.5}
{UInteger, Integer}            -> UInteger
{UBoolean, Boolean}            -> UBoolean
{SBoolean, UBoolean, Boolean}  -> SBoolean
{UString, String}              -> UString
controls: {Integer, Real} -> Real     {Integer, String} -> OclAny
conformance is one-directional: Real <= UReal true, UReal <= Real FALSE
```

Locked in by `UncertainTypeLatticeTest`.

---

## 5. CLOSED — the `TupleType` cost

`TupleType.allSupertypes()` enumerates the Cartesian product of its parts' supertype sets
(`genAllSuperTypes`, `TupleType.java:241`). Over `Integer` parts the per-part set grew 3 → 5, so the
result grows `3ⁿ+1` → `5ⁿ+1`. **Measured on this build, not interpolated:**

| arity | before | after | slowdown |
|---|---|---|---|
| 5 | 244 / 3 ms | 3,126 / 24 ms | 8× |
| 6 | 730 / **6 ms** | 15,626 / **89 ms** | 15× |
| 7 | 2,188 / **16 ms** | 78,126 / **1,310 ms** | 82× |
| 8 | 6,562 / **72 ms** | 390,626 / **29,322 ms** | **407×** |

**It is on a hot path.** `TypeImpl.java:109-110` — `getLeastCommonSupertype` — calls
`allSupertypes()` on *both* operands, and `UniqueLeastCommonSupertypeDeterminator:48` calls it for
every collection literal. So any type comparison involving a high-arity tuple pays this.

Three facts that bound the problem:

1. **We did not introduce the exponential; we raised its base.** Upstream is already `3ⁿ`. The
   practical cliff moves from roughly arity 10 to roughly arity 7.
2. **The fork has the identical hazard and did nothing.** `diff` of the fork's `TupleType.java`
   against 7.5.0's is **empty**. It adopted the lattice and never addressed the consequence — so
   bug-for-bug fidelity says do nothing, and the fork is simply untested here.
3. **Nothing shipped reaches it.** The highest tuple arity in USE's example models and test corpus is
   **2**. It is reachable only by a user writing a 7+-part tuple.

### 5.1 Resolved in two steps

**Step 1 — memoise (`fa355639`).** `fParts` made final and `getParts()` made unmodifiable *first*, so
the cache's precondition is structural rather than a survey; the cached set is handed out
unmodifiable too. A 31-entry fingerprint over arities 1–5 across six element types was **identical**
before and after (`-1607660059`). 1000 repeat calls dropped to **0 ms** at every arity.

**Step 2 — short-circuit (`8a279683`).** `genAllSuperTypes` constructs only `TupleType`s plus `this`
and `OclAny`, and a non-tuple's supertype set never contains a tuple. So when the argument is a tuple
and the receiver is not, the intersection at `TypeImpl:109-110` is **provably exactly `{OclAny}`** —
confirmed on all 45 simple×tuple pairs before relying on it. Guarding it removes the enumeration from
the reachable path entirely, first call included.

| | before | after |
|---|---|---|
| LCS fingerprint, 784 cells over a 28-type universe incl. 8 tuple types | `-894195394` | `-894195394` |
| `conformsTo` fingerprint, 784 cells | `-1330322348` | `-1330322348` |
| `Integer.getLeastCommonSupertype(Tuple/8)` | ~31 s | **0 ms** |

Pinned by `TupleTypeSupertypeCostTest` (4 methods).

### 5.2 Residual, still open and stated

`Set(Tuple{...}).allSupertypes()` still enumerates — a collection's supertype set genuinely needs one
entry per element-type supertype. Memoisation bounds it to once per instance. The pre-existing
upstream asymmetry `Set(Integer).lcs(Tuple) = null` vs `Tuple.lcs(Set(Integer)) = OclAny` is untouched:
it is not ours, and changing it would be a behaviour change needing its own evidence.

## 6. Track B — SBoolean marshalling (`8e05ba59`)

All five changes C-1..C-5 of `stage-03-scope.md` §7.5 landed. `SBooleanValue.and` now executes against
the historical jar and returns genuine subjective-logic conjunctions — `(0.3,0.2,0.5,0.5)` with itself
gives `b=0.19, d=0.36, u=0.45, a=0.25`, and 6 distinct results over a 3×3 opinion grid, so the
operation discriminates.

Three of our own harness invariants caught the change and were **re-pointed to `SetValue.includes`,
not weakened** — that receiver has the same shape SBooleanValue used to have (method exists on the
historical class, receiver not constructible), which is the distinction those tests exist to check.

**No comparison is produced until S9**, because no ported `SBooleanValue` exists. The instrument is
built before the thing it measures, which is why this was S3 work.
