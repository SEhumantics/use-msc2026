# S4 — the value classes, and the first real differential evidence

**Status: the four U-types and SBoolean are ported and measured against the historical reference.**

| | |
|---|---|
| `94309dbf` | vendor `uDataTypes` as `org.tzi.use.uncertainty.datatypes`, purged of UUnlimitedNatural |
| `b78b4171` | the five value classes + the two abstract bases |
| *(this)* | `PortedCandidate` — the ported side of the harness — and the first real sweep |

---

## 1. The measurement

39 operations, ~12,700 rows, historical jar versus the ported classes in one JVM.

**0 `DIFFER`. 0 `MIXED`.**

`distinctRef` is the number of distinct values the *reference* produced. It is reported for every
operation because an agreement count alone is not a fidelity claim: an operation whose codomain is a
single point agrees for free (D-15).

| Operation | rows | distinctRef | verdicts |
|---|---|---|---|
| `URealValue.add/minus/mult/divideBy` | 784 ea. | 258 / 389 / 195 / 409 | all `AGREE` |
| `URealValue.min/max` | 784 ea. | 27 | all `AGREE` |
| `URealValue.lt/gt/le/ge` | 784 ea. | 37 | all `AGREE` |
| `URealValue` 9 unary ops | 28 ea. | 14–27 | all `AGREE` |
| `UIntegerValue.add/minus/mult` | 361 ea. | 154 / 260 / 115 | all `AGREE` |
| `UBooleanValue.and/or` | 225 ea. | 38 / 39 | `AGREE`=169, `HARNESS_ERROR`=56 |
| `UBooleanValue.not` | 15 | 10 | `AGREE`=13, `HARNESS_ERROR`=2 |
| `UStringValue.uConcat` | 484 | **373** | `AGREE`=441, `HARNESS_ERROR`=43 |
| `UStringValue.lt/gt/le/ge` | 484 ea. | 81 | `AGREE`=441, `HARNESS_ERROR`=43 |
| `UStringValue.uToString/toUBoolean/uCharacters` | 22 ea. | 17 / 5 / 20 | `AGREE`=21 |
| `UStringValue.toBoolean/toInteger/toReal` | 22 ea. | **1** | **degenerate — see §3** |
| **`SBooleanValue.and`** | **529** | **160** | `AGREE`=361, `HARNESS_ERROR`=168 |
| **`SBooleanValue.not`** | **23** | **19** | `AGREE`=19, `HARNESS_ERROR`=4 |

The two SBoolean rows are the ones that did not exist before this stage: those operations had **no
evidence source of any kind** as recently as `stage-03-scope.md` §2.1.

### What `HARNESS_ERROR` means here, and what it does not

Every one is an input that **one or both sides refused to construct** — a negative probability
(`UBOOLEAN(false,-1.0)`), an opinion outside the `0.001` simplex tolerance. The operation under
comparison was never entered. Under D1 these are not measurements and are not agreement. They are
corpus boundary probes doing their job, not defects.

---

## 2. Two harness defects this run found — both false *divergence*, not false agreement

Both were found by running, not by reading, and both are asymmetries in the **instrument**.

**Defect A — the B1 relocation made SBoolean unrenderable.** `opaqueRepresentation` refused any class
that was not isolated, and the vendored datatypes are deliberately *not* isolated. All **529** rows of
`SBooleanValue.and` came back `HARNESS_ERROR`. Worse, had it rendered, the representation embedded the
**fully qualified** class name — `uDataTypes.SBoolean` on one side, `org.tzi.use.uncertainty.datatypes.SBoolean`
on the other — so every such row would have differed on package alone.

Fixed by rendering the **simple** class name, which is the policy `UValue` already documents for its
type token and for exactly this reason: *"comparing fully-qualified names would make every row of a
port that relocated the package a false divergence — a difference in where the file lives rather than
in what the operation answered."* Field declarations in the renderer had always used simple names, so
this also made it internally consistent.

**Defect B — `SequenceValue` fell to `OPAQUE` on the ported side only.** 21 of 22
`UStringValue.uCharacters()` rows read `DIFFER` on **identical content**: the reference rendered
`SEQUENCE[]`, the port rendered the opaque field dump. My omission in `PortedCandidate.fromPorted`,
not a port infidelity. Fixed by mirroring the reference's `SequenceValue` branch exactly.

Both defects produced **false `DIFFER`**, which is the visible failure mode. Neither could have
produced a false `AGREE`.

---

## 3. Degeneracy — H15, closed

Three operations agreed for free on the first run: `UStringValue.toBoolean/toInteger/toReal`, each
with **one** distinct reference value.

**The cause was the corpus, not the operations.** `uStringBoundaries()` held sixteen strings — `""`,
`" "`, `"abc"`, tabs, quotes, unicode — and not one parsed as a boolean, an integer or a real. So
`toBoolean()` could only ever answer false, and the other two could only ever throw, which they did
on 20 of 22 rows. Signing them off as "genuinely single-valued" would have been wrong.

Ten parseable spellings were added (`true`/`false`/`TRUE`, `0`/`42`/`-7`/`2147483647`,
`3.14`/`-0.5`/`1e10`):

| | before | after |
|---|---|---|
| `toBoolean()` | 1 | **2** — the whole Boolean codomain, so complete rather than improved |
| `toInteger()` | 1 | **5** |
| `toReal()` | 1 | **8** |
| `uConcat()` | 373 | **863** (rows 484 → 1024) |

### The one genuine exemption

`UBooleanValue.value()` is single-point **by construction**, and no corpus can change it:

```java
// UBooleanValue.java:127-130, in valueOf(boolean, double)
if (!value) { value = true; probability = 1 - probability; }
```

Every `UBooleanValue` reachable through the public factory canonicalises to *"true with probability
p"*, so `value()` has one inhabitant. The information lives in `probability()`, which measures
`distinctRef=10` over the same corpus. It is the sole entry in
`FirstRealDifferentialTest.DEGENERATE`, with that reason written at the entry.

That contrast is the point of the list: UString's three looked identical to this one and were not
structural. **Widen the corpus first; exempt only what cannot be widened, in writing.**

## 3a. D-52 — the type-token clause, now asserted

`harness-contract.md` §7 required that the ported adapter *observe* the class of the object it
returned rather than let a factory assume it, and made `javaTypeMismatchCount() == 0` an S4 gate
clause. Until S4 it could not be asserted: there was no ported object to observe.

`PortedCandidate` calls `UValue.observedFrom(returned)` on every result. Measured across all
operations, including the primitive-returning accessors the contract names specifically —
`value()`, `uncertainty()`, `probability()`, `confidence()`, where reflection hands back a boxed
`java.lang.Double`/`Integer`/`Boolean` rather than a `Value`:

**`java-type mismatches: NONE`.**

The contract records that a factory-typed adapter measures **3,445** such rows across **182 of 285**
operations *from a port with no defect in it*. Zero is therefore a statement about the adapter, and
the assertion is now a clause in `FirstRealDifferentialTest` rather than a line in a report.

---

## 4. The precondition this stage established

`HistoricalOracleIsolationTest#uTypesResolveOnlyThroughTheOracle` asserted, from S1 until now, that
the ported U-types do **not** resolve on the application loader — because there was no port. S4 makes
that false, and the S1 author left instructions in the test body for this exact moment: *"When S4
lands, this assertion is expected to be inverted … do NOT delete it."*

It is inverted, not deleted. It now asserts **distinctness** for all five value classes: two `Class`
objects, one fully qualified name, two loaders, neither the same object. That property is what the
entire differential design rests on, and until this stage it was untestable for the U-types because
only one side existed.

---

## 5. Open

* **The remaining operation surface.** 39 operations are measured here; the full census is 285. The
  rest are S5–S8.
* **`URealValue.compareTo` carries dead code** — a second, unreachable `else if (o instanceof
  URealValue)`. Ported as-is; it belongs on the `b7-fix-plan.md` ledger with its own justification.
* **The printing corpus.** Shared opaque rendering means print fidelity is *not* established by any
  figure here; it remains a separate obligation (`adaptation/05-printing-corpus.md`).
