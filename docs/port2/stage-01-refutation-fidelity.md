# S1 refutation — oracle fidelity, determinism, comparison semantics

**Reviewer role:** refuter. **Dimension:** oracle fidelity, determinism, comparison semantics.
**Subject:** commit `dfc3c063` "S1: differential harness for the uncertainty port" on `port-uncertainty-2`.
**Method:** static reading plus `javap` against the jars, plus a *scratch* re-compilation of the
harness's non-JUnit classes outside the repository (`javac`/`java` only — **no Maven was run**),
driven against the committed jars via `-Duse.historical.jars.dir=use-core/src/test/resources/historical`.
Nothing in the repository was modified except this file.

**Verdict: DEFECTIVE.** The harness is genuinely well isolated, genuinely deterministic, and its
comparison is genuinely exact — those three claims survive attack and I confirmed them
independently. It nevertheless has one failure mode that produces a *green* result from a totally
broken subject, and its `inputs` column misreports what the oracle was actually fed on 55 of the
784 rows already committed.

---

## 0. What survived the attack (stated first, so the defects are read in proportion)

| Claim | How I checked | Result |
|---|---|---|
| Right overload bound | `HistoricalOracle.resolve` → `owner.getMethod(name, valueClass)` where `valueClass = Class.forName("org.tzi.use.uml.ocl.value.Value", loader)`. Cross-checked every driven signature against `javap -cp use.jar:atenearesearchgroup.uncertainty.jar`. | **SOUND.** `add/minus/mult/divideBy/…` are declared `(Value)`; the harness asks for exactly `(Value)`. `int`/`double`/`float` params are asked for as primitives, matching `at(int)`, `uSubstring(int,int)`, `equalsC(Value,double)`, `power(float)`. No accidental binding. |
| Arguments built inside the historical loader | `toHistorical` resolves every class through `load()` → `Class.forName(…, loader)`. Verified at runtime: `invoke` succeeds with `Value.class` taken from the isolated loader, and `receiverClass.isInstance(receiver)` guards the receiver. | **SOUND.** |
| `UBooleanValue` package-private ctor avoided | `grep -rn "setAccessible\|getDeclaredConstructor\|getDeclaredMethod" use-core/src/test/java/org/tzi/use/uncertainty/` → only a comment. The public `valueOf(boolean,double)` is used. | **SOUND.** No `setAccessible` anywhere; nothing to break under a security manager or a stricter JDK. |
| Comparison tolerance | `DifferentialSweep.classify` → `ref.value.canonical().equals(sub.value.canonical())`; `UValue.canonical()` renders every double through `Double.toString`. | **SOUND and exact.** Tolerance is **zero**, applied identically to the value component **and** to the uncertainty/probability/confidence component (both go through `Double.toString` in the same string). |
| NaN handling | `Double.toString(NaN)` = `"NaN"` on both sides → string equality → AGREE. This is `Double.compare` semantics, not `==`. | **CORRECT choice.** 110 of the 784 committed `add` rows carry a NaN result and all 110 are AGREE. `==` would have made every one of them a spurious DIFFER. |
| Determinism | Ran the smoke sweep twice from clean scratch dirs under `-Duser.language=de -Duser.country=DE -Duser.timezone=Asia/Tokyo`. | **PROVEN.** `cmp` → byte-identical, **and** `diff` against the committed `docs/port2/differential/s1-smoke-ureal-add.tsv` → identical. No entropy sources (`grep -nE 'Math\.random\|currentTimeMillis\|nanoTime\|new Random\(\)\|UUID\|\.now\(\)'` → only doc comments). Every map that is iterated is an `EnumMap`, `LinkedHashMap` or `DiffVerdict.values()`; the single `HashSet` (`StubCandidate.SUPPORTED`) is only ever `contains()`-ed. Line terminator is a hard-coded `'\n'`. |
| Stub is not a tautology | `StubCandidate` is 40 lines of plain Java arithmetic. It does not hold a reference to `HistoricalOracle` and never calls it. | **SOUND.** A real divergence in `add`/`minus` *would* be detected — proven by the injected-fault direction (226 DIFFER rows) and by my own `neg()` run below, which found a divergence the author's smoke test does not. |
| TSV framing | Swept `uConcat` over strings containing `\t`, `\n`, `"`, `\`, `" | "` and the empty string. Split every emitted row on `\t`. | **SOUND.** 25/25 rows had exactly 7 fields. `UValue.quote()` escapes control chars with hand-rolled hex (deliberately not `String.format`, which is locale-sensitive), and `DiffRow.scrub()` is a second line of defence. |
| Row-count self-consistency | `# rows 784` + 9 `#` lines + 1 column header = 794 lines (`wc -l` = 794). `# verdict.*` sums to `# rows` in both committed files. | **SOUND.** |
| Header records seed and jar hashes | `# seed 20260817`, `# sha256.use.jar 80ac…788d`, `# sha256.atenearesearchgroup.uncertainty.jar 53b2…59d0`. Committed jars re-hashed: both match the established values. | **SOUND.** |
| No silent capping/sampling | `DifferentialSweep.sweep` builds the full cartesian product; `run` iterates every tuple; `writeAll` writes every row; the smoke test asserts `corpus.size()²  == rowCount()`. The `limit(12)` / `limit(5)` calls are console display only. | **SOUND.** |
| Scope discipline | `git show --name-only HEAD` → nothing outside `src/test` and `docs/`. `git diff b7aaa99c..HEAD --name-only | grep -E 'module-info|pom.xml|use-gui|use-assembly'` → none. No `reference-repositories` path in any pom or java file. | **SOUND.** `module-info.java` was not edited. |

---

## D1 — CRITICAL: harness-internal marshalling failures are laundered into the historical column, and two failing sides read as **AGREE_THROWN**

### The mechanism

`HistoricalOracle.invoke` performs three things that can throw *before* the historical
implementation is ever entered:

* arity validation → `IllegalArgumentException`
* receiver-kind validation → `IllegalArgumentException`
* `toHistorical(...)` / `marshal(...)` on the receiver and on every `VALUE` argument →
  `IllegalStateException("historical constructor threw for …")` (`HistoricalOracle.java:497`)

`DifferentialSweep.apply` catches `Throwable` indiscriminately and turns any of these into
`Outcome.threw(t)`, i.e. into the **historical** column. `classify` then compares *only*
`getClass().getName()`, and if both sides threw the same class emits `AGREE_THROWN`, whose
`isAgreement()` is `true`, so `disagreements()` is empty and the sweep is green.

### Demonstration (actually run)

A `Candidate` that implements **nothing at all** and merely throws `IllegalStateException`:

```
=== (C) LAUNDERING: a candidate that implements NOTHING, swept over ===
===     inputs the harness itself cannot marshal                    ===
UStringValue.uToUpperCase(): 3 rows, AGREE_THROWN=3
index	operation	inputs	historical	ported	verdict	note
0	UStringValue.uToUpperCase()	USTRING("abc",-1.0)	THROWN:java.lang.IllegalStateException	THROWN:java.lang.IllegalStateException	AGREE_THROWN	
1	UStringValue.uToUpperCase()	USTRING("x",2.0)	THROWN:java.lang.IllegalStateException	THROWN:java.lang.IllegalStateException	AGREE_THROWN	
2	UStringValue.uToUpperCase()	USTRING("y",-0.5)	THROWN:java.lang.IllegalStateException	THROWN:java.lang.IllegalStateException	AGREE_THROWN	
disagreements() -> 0   (0 means a totally broken port passes this sweep)
```

Note the `note` column is **empty** — `classify` only populates it for `DIFFER_THROWN` and `MIXED`.
Nothing in the TSV reveals that neither side executed anything.

### This is reachable from the corpora already shipped

`InputGenerator.uStringBoundaries()` contains `uString("abc", -1.0)`; `uBooleanBoundaries()`
contains `uBoolean(false, -1.0)` and `uBoolean(false, 2.0)`. Round-tripping the shipped boundary
lists through `toHistorical` (run):

```
USTRING("abc",-1.0)   -> THROWN java.lang.IllegalStateException: historical constructor threw for USTRING("abc",-1.0)
UBOOLEAN(false,-1.0)  -> THROWN java.lang.IllegalStateException: historical constructor threw for UBOOLEAN(false,-1.0)
UBOOLEAN(false,2.0)   -> THROWN java.lang.IllegalStateException: historical constructor threw for UBOOLEAN(false,2.0)
```

Any S2+ sweep over `uStringBoundaries()` or `uBooleanBoundaries()` therefore contains rows whose
"historical" result was produced by the harness, not by the jars.

### The reported throwable class is *fabricated*

The true historical cause is not `IllegalStateException`. Run:

```
USTRING("abc",-1.0)
    harness reports : java.lang.IllegalStateException
    true historical : java.lang.IllegalArgumentException: Invalid parameters
UBOOLEAN(false,-1.0)
    harness reports : java.lang.IllegalStateException
    true historical : java.lang.IllegalArgumentException: Invalid parameters
```

Confirmed at the bytecode level —
`javap -p -c uDataTypes.UString` `<init>(String,double)`:

```
26: new  #4   // class java/lang/IllegalArgumentException
30: ldc  #5   // String Invalid parameters
```

So the harness makes **both** comparison errors at once on these inputs:

* a *correct* port that faithfully throws `IllegalArgumentException("Invalid parameters")` is
  reported as **DIFFER_THROWN** — a false divergence;
* a port that wraps its own construction failures in `IllegalStateException` (the very pattern this
  harness models) is reported as **AGREE_THROWN** — a false agreement.

`IllegalArgumentException` makes this worse than a labelling nit: it is simultaneously the harness's
own arity/receiver-mismatch exception *and* a genuine historical outcome, so the two are
indistinguishable in the report.

**What a fix looks like (not applied):** marshalling and validation failures must never enter
`Outcome.threw`. They need a distinct, non-agreeing verdict (e.g. `HARNESS_ERROR`) raised out of the
sweep, and only exceptions unwrapped from `InvocationTargetException` — i.e. thrown by the historical
code itself — may populate the historical column.

---

## D2 — MAJOR: `toHistorical` silently normalises inputs; the `inputs` column of the committed TSV misreports 55 of 784 rows

`uDataTypes.UReal(double,double)` applies `Math.abs` to the uncertainty
(`javap -p -c uDataTypes.UReal`):

```
19: aload_0
20: dload_3
21: invokestatic  #4   // Method java/lang/Math.abs:(D)D
24: putfield      #3   // Field u:D
```

`UBooleanValue.valueOf(boolean,double)` flips a `false` into `true` with the complementary
probability, then collapses the endpoints onto the shared `TRUE`/`FALSE` singletons — and
`static {}` initialises `TRUE = new UBooleanValue(true, 1.0)` and `FALSE = new UBooleanValue(true, 0.0)`,
so `value()` on the historical side is `true` in the normal form regardless of what was asked for.

Round-tripping the shipped corpora `UValue → toHistorical → fromHistorical` (run):

```
---- UBOOLEAN
UBOOLEAN(false,0.0)  -> UBOOLEAN(true,1.0)   <<< ROUND TRIP LOSSY
UBOOLEAN(false,0.5)  -> UBOOLEAN(true,0.5)   <<< ROUND TRIP LOSSY
UBOOLEAN(false,1.0)  -> UBOOLEAN(true,0.0)   <<< ROUND TRIP LOSSY
   COLLAPSE: [UBOOLEAN(true,0.0), UBOOLEAN(false,1.0)]  all map to  UBOOLEAN(true,0.0)
   COLLAPSE: [UBOOLEAN(true,0.5), UBOOLEAN(false,0.5)]  all map to  UBOOLEAN(true,0.5)
   COLLAPSE: [UBOOLEAN(true,1.0), UBOOLEAN(false,0.0)]  all map to  UBOOLEAN(true,1.0)
---- UREAL
UREAL(1.0,-1.0)      -> UREAL(1.0,1.0)       <<< ROUND TRIP LOSSY
---- UINTEGER
UINTEGER(1,-1.0)     -> UINTEGER(1,1.0)      <<< ROUND TRIP LOSSY
```

Consequences, in order of seriousness:

1. **The report lies about its own inputs.** The committed `s1-smoke-ureal-add.tsv` has **55 rows**
   (`awk -F'\t' '$3 ~ /UREAL\(1\.0,-1\.0\)/' | wc -l` → 55) claiming an input of uncertainty
   `-1.0`. Row 21 is the plainest:
   ```
   21	URealValue.add(value)	UREAL(0.0,0.0) | UREAL(1.0,-1.0)	UREAL(1.0,1.0)	UREAL(1.0,1.0)	AGREE
   ```
   The oracle computed on `UREAL(1.0,1.0)`. Replaying that row from its `inputs` column against a
   direct historical construction does not reproduce it.
2. **Silent coverage loss.** `uBooleanBoundaries()` advertises "both truth values against
   probability 0.0, 0.5 and 1.0" — 6 entries. The oracle sees **3 distinct objects**. Of 9
   `uBooleanBoundaries()` entries, 3 are duplicates of other entries and 2 cannot be constructed at
   all, leaving 4. `uRealBoundaries()` is 22 entries / 21 distinct objects; `uIntegerBoundaries()`
   13 / 12. Nothing in the TSV, the header, or `docs/port2/stage-01.md` says so. The stage-01
   coverage table's row "confidence / probability / uncertainty exactly 0 and exactly 1 — every
   `*Boundaries()` list" is true of the declared inputs and false of the inputs the oracle received.
3. The boolean component of `UBOOLEAN(…)` carries almost no discriminating information from the
   oracle, because the historical normal form pins `value()` to `true`.

---

## D3 — MAJOR: the "faithful" stub is **not** faithful on the S1 corpus, and the smoke test happens to sweep only the two operations where that is invisible

`StubCandidate` documents itself as "Reproduces the measured historical formulas. Expected to agree
on every input," and declares support for three operations: `add`, `minus`, `neg`.
`UncertaintyDifferentialSmokeTest` sweeps **`add` (faithful)** and **`minus` (faulty)**. It never
sweeps `neg`. I did (run):

```
=== (A) neg() over uRealBoundaries: oracle vs the *FAITHFUL* stub ===
URealValue.neg(): 22 rows, AGREE=21, DIFFER=1
  DISAGREE: 21	URealValue.neg()	UREAL(1.0,-1.0)	UREAL(-1.0,1.0)	UREAL(-1.0,-1.0)	DIFFER	
```

Root cause is D2: the oracle's `URealValue(double,double)` absorbs the sign into `Math.abs`, the
stub does not. `add` and `minus` combine uncertainties as `sqrt(ua² + ub²)`, which **squares the
sign away**, so both operations are blind to the discrepancy:

```
=== (B) minus over uRealBoundaries: faithful stub ===
URealValue.minus(value): 484 rows, AGREE=484
```

So the headline "784 rows, AGREE=784" is an artifact of which two operations were chosen, not
evidence that the faithful side reproduces the oracle on the S1 corpus. Had the smoke test swept
the third operation the stub declares, it would be red today. This is the exact shape of the
"harness that has only ever printed green" the author's own stage-01 report warns against, one
level up.

---

## D4 — MINOR: `AGREE_THROWN` discards the throwable message

`DifferentialSweep.classify` sets `note = ""` when the two throwable class names match. The
messages are never written anywhere. A port that throws `IndexOutOfBoundsException("index 47")`
where the oracle throws `IndexOutOfBoundsException("idx = 0")` is recorded as agreement with no
auditable trace. This is the reason D1's laundering is invisible on inspection of the TSV. At
minimum both messages should be recorded on `AGREE_THROWN` rows too.

---

## D5 — MINOR: `# operations` header is ambiguous for multi-op reports

`DiffReportWriter` writes `String.join(",", operations)`, but `UOp.key()` itself contains commas for
multi-parameter operations. A two-op report emits (run):

```
UStringValue.uSubstring(int,int),URealValue.add(value)
```

which no consumer can split back into two operations. Not yet triggered — both committed reports are
single-op — but it will be the moment `writeAll` is used as designed.

---

## D6 — MINOR: `# seed` does not identify the corpus

The header records the seed but not the number of random draws (`RANDOM_DRAWS = 6`, a private
constant of the test class), nor a corpus/harness revision. Two reports both stamped
`# seed 20260817` can have different inputs. The console prints `corpus size 28 (22 boundary + 6
random)`; the machine-readable artifact does not. Add `# random.draws`, `# boundary.size` and a
harness revision to the header.

---

## D7 — MINOR: `UOp.ParamKind` cannot express a `StringValue` parameter

`UOp.ParamKind` is `{VALUE, INT, DOUBLE, FLOAT}`. `UStringValue.indexOf` is declared
`indexOf(org.tzi.use.uml.ocl.value.StringValue)` — not `Value`. `getMethod("indexOf", Value.class)`
therefore misses, and the operation is permanently unreachable (run):

```
oracle.supports(UStringValue.indexOf(value)) = false
UStringValue.indexOf(value): 25 rows, UNSUPPORTED=25   disagreements=25
```

This is **honest** — `UNSUPPORTED.isAgreement()` is `false`, so the rows count as disagreements and
cannot be mistaken for coverage. But it must be fixed before S2/S3 can claim full-surface coverage.

---

## D8 — MINOR: `marshal(INT, …)` silently truncates

`HistoricalOracle.numeric()` accepts a `UREAL`/`REAL` for an `int` parameter and applies
`Number.intValue()`. `marshal(INT, uReal(2.7, 0))` passes `2` with no note in the row. Not triggered
today (`indexBoundaries()` is all `UValue.integer`), but it is a silent value change on the input
path, which is the same class of problem as D2.

---

## D9 — MINOR: two isolation negative controls pass vacuously in classpath mode

`platformLoaderIsNotACleanParentUnderJpms()` and control (2) of `naiveLoadersWouldSelfCompare()`
both `return` / skip silently when `Class.forName(COLLIDING_CLASS, …, platformLoader)` throws
`ClassNotFoundException`. If surefire ever stops running tests on the module path, those assertions
evaporate and the test still reports PASS with no signal. Control (1) (system-parented
`URLClassLoader`) and `sameNameDistinctClasses()` do survive, so the isolation property itself
remains genuinely tested — this is a signal-loss issue, not a correctness one. It should print or
record the mode it detected.

---

## Answers to the specific questions posed

**1. Reflection fidelity.** Correct. The right `(Value)` overloads are bound; arguments are
constructed inside the isolated loader; no `setAccessible`; the public `valueOf(boolean,double)`
factory is used rather than the package-private `UBooleanValue(uDataTypes.UBoolean)` constructor. The
*fidelity* problem is not overload resolution, it is that `valueOf(boolean,double)` normalises
(D2) and that its failures are relabelled (D1).

**2. Comparison semantics.** Zero tolerance, applied to the value and the uncertainty/probability/
confidence component alike, via `Double.toString` string equality. The code therefore makes the
**strict** error, not the loose one — no epsilon can hide a real divergence. NaN is handled as
`Double.compare` (NaN equals NaN), which is the right choice and is exercised: 110 of 784 committed
rows have NaN results and all agree. `-0.0` vs `0.0` is a DIFFER; row 58 of the committed report
shows `-0.0` surviving as a legitimate result on both sides, so the risk is real but currently
benign. The author documents this trade-off explicitly (`stage-01.md` §6.4). No defect.

**3. Determinism.** Proven, not merely claimed. Two scratch runs under a `de_DE` locale and
`Asia/Tokyo` timezone produced byte-identical output, and that output is byte-identical to the
committed `s1-smoke-ureal-add.tsv`. Fixed seed `20260817`, recorded in the header, no ambient
entropy, no order-dependent hash iteration.

**4. The stub.** Not a tautology — independent arithmetic, no reference to the oracle. A real
divergence *is* detected; I found one the author's own smoke test does not (D3). The converse
fails, though: a mismatch **can** be lost, via `AGREE_THROWN` (D1), demonstrated on inputs drawn
from the shipped corpora.

**5. Report integrity.** Escaping and framing are solid (25/25 pathological rows split into exactly
7 fields); row counts are self-consistent; the header carries seed and both jar sha256s. The
integrity failure is semantic, not syntactic: the `inputs` column is not what the oracle was given
(D2, 55 committed rows), and the corpus identity is under-recorded (D6).

**6. Coverage honesty.** No capping, sampling or skipping — full cartesian product, every row
written, and `UNSUPPORTED` is a non-agreeing verdict rather than a silent skip. The dishonesty is
one level down: the boundary corpora collapse inside `toHistorical` and neither the TSV nor
`stage-01.md` discloses it (D2), and `indexOf(StringValue)` is structurally unreachable (D7).

---

## Bottom line

The instrument's *frame* — isolation, determinism, exactness, escaping, fail-loud jar verification —
holds up under attack and I could not break it. What does not hold up is the boundary between
"the historical implementation did X" and "the harness could not get as far as calling the
historical implementation". Every later fidelity claim rests on that boundary. D1 must be fixed
before any S4+ agreement number is quotable; D2/D3 must be fixed before any *input* in a
differential report can be trusted to mean what it says.
