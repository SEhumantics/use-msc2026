# S1 — The differential harness

Branch `port-uncertainty-2`. All commands run in `~/msc-4/use-msc2026` (Java 21.0.11, Maven 3.9.16).

S1 precedes any port code. Its only deliverable is the measuring instrument: a test-scoped harness
that drives the historical `URealValue` / `UIntegerValue` / `UBooleanValue` / `UStringValue` out of
the 2018 jars inside an isolated class loader, so historical and ported classes with the same
fully-qualified name can coexist in one JVM.

**Nothing under `use-core/src/main`, `use-gui` or `use-assembly` was touched. No existing test file
was modified. `module-info.java` was NOT edited.**

> ## ⚠ STATUS — READ BEFORE §1
>
> **S1 as narrated in §1–§6 below was audited and returned DEFECTIVE.** The instrument could report
> full agreement over rows where neither side ever entered the operation under comparison
> (defect **D1**). Eleven fixes F1–F11 landed in commit `cf9d2f45`; D1 is closed and re-verified
> independently. **A second defect of the same family, D2, is still open at the time of writing.**
>
> **§1–§6 are left exactly as they were written.** They record what was believed on 2026-08-17
> before the audit. **§7, appended below, is the correction and is authoritative where the two
> disagree.** Do not read §5's "Acceptance" as a closed gate without reading §7.
>
> ---
>
> **UPDATE (later the same day) — the sentence above about D2 is superseded. Read §8, not this
> banner, for the current verdict.** D2 was closed at the root in commit `e8b73e48`
> (throw-agreement **deleted**, not tightened: `AGREE_THROWN` and `DIFFER_THROWN` no longer exist).
> Two independent reviewers then found a **third** door of the same family — `VOID` vs `VOID` scored
> `AGREE` — and **S1 is DEFECTIVE again**. Nothing in §1–§7 is deleted; §8 is authoritative over both.
>
> **Wherever §7 or an earlier `docs/port2/` file names `AGREE_THROWN` or `DIFFER_THROWN` as a live
> verdict, it is describing the code as it was before `e8b73e48`. Those constants are gone.**

---

## 1. What was built

All under `use-core/src/test/java/org/tzi/use/uncertainty/differential/`.

| File | Role |
|---|---|
| `IsolatedJarClassLoader.java` | Parent-last loader for `org.tzi.use.*` and `uDataTypes.*`. The load-bearing piece — see §3. |
| `HistoricalOracle.java` | Owns the loader, locates and hash-verifies the jars, constructs historical values, invokes operations by name, unwraps results to plain Java. `Closeable`. Implements `Candidate`. |
| `Candidate.java` | The pluggable side interface. S4–S7 drop the port in here. |
| `StubCandidate.java` | S1 placeholder: `faithful()` and `faultyMinus()`. **Not the port.** |
| `UValue.java` | Plain-Java value model. Callers never handle reflective types. |
| `UOp.java` | Operation descriptor (receiver type, method, parameter kinds). |
| `InputGenerator.java` | Seeded and boundary input corpora. |
| `DifferentialSweep.java` | Runs an op on both sides, emits rows, tallies verdicts. |
| `DiffRow.java`, `DiffVerdict.java` | Row record and verdict enum. |
| `DiffReportWriter.java` | Stable TSV writer. |
| `HistoricalOracleIsolationTest.java` | **The anti-self-comparison test.** 9 methods. |
| `UncertaintyDifferentialSmokeTest.java` | End-to-end smoke. 6 methods. |

Both test classes are **JUnit 5 Jupiter**. This reactor has no `junit-vintage-engine`, so a JUnit 3
or JUnit 4 test would compile, report nothing, and never run — the condition that makes 38 of the
41 existing `*Test.java` files dormant (S0 §3).

---

## 2. Jar-location decision

**Decision: both jars are copied into `use-core/src/test/resources/historical/` and committed.**

```
use-core/src/test/resources/historical/use.jar                             1 440 303 B
use-core/src/test/resources/historical/atenearesearchgroup.uncertainty.jar    77 674 B
```
```bash
sha256sum use-core/src/test/resources/historical/*.jar
```
```
53b2a43feb0a0a39844a60278dd80a7d4b975ef324fb05c6db28831e835e59d0  atenearesearchgroup.uncertainty.jar
80ac8ae433b8345677472019991356950f094f4a104cfbce1f75783a7308788d  use.jar
```
Both match the digests established in the task brief, before and after the copy.

### Why, in order of weight

1. **Reproducibility.** `.git/reference-repositories/` lives *inside* `.git/` and is not tracked by
   this repository. A test that reads from there passes on this machine and fails for everyone else
   who checks the branch out. A harness that only works in one working copy is not a harness.
2. **The reference repositories are references, never build inputs.** Reading them from a test is
   reading them from the build. Copying once, by hand, into a tracked location converts a build
   dependency into a committed artifact with a recorded provenance and a recorded digest.
3. **A `.jar` under `src/test/resources` cannot collide with ported classes.** Maven copies it to
   `target/test-classes/historical/` as an opaque data file. It is *not* added to the test classpath
   as a jar, so its `org.tzi.use.uml.ocl.value.*` entries are invisible to the application loader
   and can only be reached through the harness's own loader. This is a property the harness depends
   on and it is worth stating: the jars being on disk under `target/test-classes` is safe precisely
   because that directory is a classpath *root*, not a jar entry.
4. **Size is a non-issue.** 1.5 MB total, added once, never churning.

The oracle verifies both sha256s on every `open()`, so the committed copies cannot silently drift
or be swapped. `-Duse.historical.jars.dir=…` overrides the location for out-of-tree runs; when set
it is **authoritative** (no fallback to the committed copies), so a mis-pointed override fails
rather than quietly doing the right thing for the wrong reason.

---

## 3. The load-bearing finding — a platform-parented `URLClassLoader` is NOT isolated here

The brief specified `ClassLoader.getPlatformClassLoader()` as the loader's parent, on the grounds
that it holds no application classes. **In this reactor that is false, and the harness's own
built-in guard caught it on the first run.**

Verbatim, from `mvn -B -pl use-core test` before the fix:

```
[ERROR]   HistoricalOracleIsolationTest.openOracle:52 » HistoricalOracleUnavailable class
org.tzi.use.uml.ocl.value.Value was defined by
jdk.internal.loader.ClassLoaders$AppClassLoader@4b9e13df rather than the isolated historical loader
java.net.URLClassLoader@175c68ce. The oracle would be comparing the port against itself; refusing
to continue.
```

### Cause

`use-core/src/main/java/module-info.java` exists, so Maven compiles and runs `use-core` on the
**module path** and `use.core` is resolved into the boot layer.
`jdk.internal.loader.BuiltinClassLoader#loadClassOrNull` consults a package-to-module map covering
*every* boot-layer module **before** it falls back to its parent. The platform class loader finds
package `org.tzi.use.uml.ocl.value` in module `use.core`, sees that the app class loader defines
that module, and delegates to it. So the platform loader cheerfully returns application classes,
and parent-first delegation reintroduces exactly the self-comparison the isolation was meant to
prevent.

This is not a hypothetical. It is the difference between a harness that works and one that reports
green while comparing the port against itself.

### Fix

`IsolatedJarClassLoader extends URLClassLoader` overrides `loadClass` to be **parent-last** for
`org.tzi.use.` and `uDataTypes.`: those names resolve from the jars and are never delegated, with
no fallback to the parent. Everything else (all of `java.*`, all JDK modules) still goes to the
platform loader, so the historical code gets a working JDK.

`module-info.java` was **not** edited. No `--add-exports`, `--add-opens` or `--patch-module` flag
was added to the build. The whole problem is solved inside the test-scoped loader.

### It is now a permanent regression test

`HistoricalOracleIsolationTest` contains three negative controls side by side, so "the isolation
test passes" is not an empty statement:

| Loader | Resolves `org.tzi.use.uml.ocl.value.RealValue` to |
|---|---|
| `new URLClassLoader(urls)` (system parent) | the **application's** class — self-comparison |
| `new URLClassLoader(urls, platformLoader)` | the **application's** class — self-comparison |
| `IsolatedJarClassLoader` | the **jar's** class |

`RealValue` is used deliberately: it exists **today** in both `use-core/src/main/java` and the
historical `use.jar`. That is a genuine same-FQN collision available before any port lands, so the
test is not vacuous at S1 — it proves the exact property the U-types will need from S4 onwards.
The oracle also asserts the invariant at `open()` time, not only in the test.

---

## 4. Seed

```
seed = 20260817
```
`InputGenerator.DEFAULT_SEED`, fed to `new java.util.Random(seed)`. No `Math.random()`, no
time-based seed, anywhere in the harness. Inputs are the fixed boundary corpus first, then the
random draws, so row order is stable.

Verified reproducible: two consecutive runs produced **byte-identical** report files.

```bash
diff run1-add.tsv docs/port2/differential/s1-smoke-ureal-add.tsv \
  && diff run1-minus.tsv docs/port2/differential/s1-smoke-ureal-minus-faulty.tsv \
  && echo "BYTE-IDENTICAL ACROSS RUNS"
```
```
BYTE-IDENTICAL ACROSS RUNS
```

### Boundary coverage (all asserted by `boundaryCoverage()`)

| Required | Where |
|---|---|
| confidence / probability / uncertainty exactly 0 and exactly 1 | every `*Boundaries()` list |
| negative values, zero, negative zero | `uRealBoundaries()`, `uIntegerBoundaries()` |
| zero divisor (UInteger, UReal, Integer, Real, and `-0.0`) | `zeroDivisors()` |
| empty string | `uStringBoundaries()` |
| out-of-range index | `indexBoundaries()` — `MIN_VALUE, -1, 0, 1, 2, 3, 4, MAX_VALUE` |
| NaN / ±infinity | `uRealBoundaries()`, in both the value and the uncertainty position |

Measured while building the corpus: **the historical `UStringValue.at(int)` is 1-based.** `at(0)` on
`"abc"` throws `IndexOutOfBoundsException: idx = 0` from `uDataTypes.UString.at`. Index 0 is
therefore a boundary case, not a normal one. Asserted in `thrownOutcomesAreRecorded()`.

---

## 5. Acceptance — commands and pasted output

### 5.1 A single command runs the smoke comparison

```bash
mvn -B -pl use-core test -Dtest=UncertaintyDifferentialSmokeTest
```

```
=== S1 differential smoke =========================================
seed                 20260817
reference            historical  /home/xoruser/msc-4/use-msc2026/use-core/target/test-classes/historical/use.jar
subject              stub-faithful
sha256 use.jar  80ac8ae433b8345677472019991356950f094f4a104cfbce1f75783a7308788d
sha256 atenearesearchgroup.uncertainty.jar  53b2a43feb0a0a39844a60278dd80a7d4b975ef324fb05c6db28831e835e59d0
corpus size          28  (22 boundary + 6 random)
rows                 784
tally                URealValue.add(value): 784 rows, AGREE=784
--- first 12 rows -------------------------------------------------
index	operation	inputs	historical	ported	verdict	note
0	URealValue.add(value)	UREAL(0.0,0.0) | UREAL(0.0,0.0)	UREAL(0.0,0.0)	UREAL(0.0,0.0)	AGREE	
1	URealValue.add(value)	UREAL(0.0,0.0) | UREAL(0.0,1.0)	UREAL(0.0,1.0)	UREAL(0.0,1.0)	AGREE	
2	URealValue.add(value)	UREAL(0.0,0.0) | UREAL(-0.0,0.0)	UREAL(0.0,0.0)	UREAL(0.0,0.0)	AGREE	
3	URealValue.add(value)	UREAL(0.0,0.0) | UREAL(1.0,0.0)	UREAL(1.0,0.0)	UREAL(1.0,0.0)	AGREE	
4	URealValue.add(value)	UREAL(0.0,0.0) | UREAL(1.0,1.0)	UREAL(1.0,1.0)	UREAL(1.0,1.0)	AGREE	
5	URealValue.add(value)	UREAL(0.0,0.0) | UREAL(-1.0,0.0)	UREAL(-1.0,0.0)	UREAL(-1.0,0.0)	AGREE	
6	URealValue.add(value)	UREAL(0.0,0.0) | UREAL(-1.0,1.0)	UREAL(-1.0,1.0)	UREAL(-1.0,1.0)	AGREE	
7	URealValue.add(value)	UREAL(0.0,0.0) | UREAL(-1.0,0.5)	UREAL(-1.0,0.5)	UREAL(-1.0,0.5)	AGREE	
8	URealValue.add(value)	UREAL(0.0,0.0) | UREAL(0.5,0.5)	UREAL(0.5,0.5)	UREAL(0.5,0.5)	AGREE	
9	URealValue.add(value)	UREAL(0.0,0.0) | UREAL(-0.5,0.25)	UREAL(-0.5,0.25)	UREAL(-0.5,0.25)	AGREE	
10	URealValue.add(value)	UREAL(0.0,0.0) | UREAL(2.0,0.0)	UREAL(2.0,0.0)	UREAL(2.0,0.0)	AGREE	
11	URealValue.add(value)	UREAL(0.0,0.0) | UREAL(100.0,0.001)	UREAL(100.0,0.001)	UREAL(100.0,0.001)	AGREE	
report               /home/xoruser/msc-4/use-msc2026/docs/port2/differential/s1-smoke-ureal-add.tsv
===================================================================
=== S1 fault-injection check ======================================
seed                 20260817
subject              stub-faulty-minus  (minus uses |ua-ub|)
rows                 784
tally                URealValue.minus(value): 784 rows, AGREE=558, DIFFER=226
--- first 5 disagreements -----------------------------------------
index	operation	inputs	historical	ported	verdict	note
29	URealValue.minus(value)	UREAL(0.0,1.0) | UREAL(0.0,1.0)	UREAL(0.0,1.4142135623730951)	UREAL(0.0,0.0)	DIFFER	
32	URealValue.minus(value)	UREAL(0.0,1.0) | UREAL(1.0,1.0)	UREAL(-1.0,1.4142135623730951)	UREAL(-1.0,0.0)	DIFFER	
34	URealValue.minus(value)	UREAL(0.0,1.0) | UREAL(-1.0,1.0)	UREAL(1.0,1.4142135623730951)	UREAL(1.0,0.0)	DIFFER	
35	URealValue.minus(value)	UREAL(0.0,1.0) | UREAL(-1.0,0.5)	UREAL(1.0,1.118033988749895)	UREAL(1.0,0.5)	DIFFER	
36	URealValue.minus(value)	UREAL(0.0,1.0) | UREAL(0.5,0.5)	UREAL(-0.5,1.118033988749895)	UREAL(-0.5,0.5)	DIFFER	
report               /home/xoruser/msc-4/use-msc2026/docs/port2/differential/s1-smoke-ureal-minus-faulty.tsv
===================================================================
[INFO] Tests run: 6, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.250 s -- in Uncertainty differential smoke
[INFO] 
[INFO] Results:
[INFO] 
[INFO] Tests run: 6, Failures: 0, Errors: 0, Skipped: 0
[INFO] 
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
```

The second block is deliberate. A harness that has only ever printed green proves nothing, so the
smoke test also runs a subject with a **known injected fault** (`minus` combining uncertainties as
`|ua − ub|` instead of in quadrature) and asserts that the harness reports it: 226 `DIFFER` rows out
of 784. The 558 agreements are the cases where the two formulas coincide (one uncertainty is 0, or
both are equal to 0, etc.), which is the expected shape.

The faithful stub's formulas were **measured** against the jars, not assumed: `add`/`minus` combine
uncertainties as `sqrt(ua² + ub²)` — specifically *not* `Math.hypot`, which returns `Infinity`
where the historical code returns `NaN` for `(1.0, NaN) add (1.0, 0.0)`.

### 5.2 The harness prints its seed and row counts

Shown above: `seed 20260817`, `rows 784` for each sweep, plus `corpus size 28 (22 boundary +
6 random)` and the per-verdict tally. The same figures are written into the report header:

```
# harness	differential-sweep/1
# seed	20260817
# reference	historical
# subject	stub-faithful
# sha256.use.jar	80ac8ae433b8345677472019991356950f094f4a104cfbce1f75783a7308788d
# sha256.atenearesearchgroup.uncertainty.jar	53b2a43feb0a0a39844a60278dd80a7d4b975ef324fb05c6db28831e835e59d0
# operations	URealValue.add(value)
# rows	784
# verdict.AGREE	784
index	operation	inputs	historical	ported	verdict	note
```

Reports: `docs/port2/differential/s1-smoke-ureal-add.tsv` (794 lines),
`docs/port2/differential/s1-smoke-ureal-minus-faulty.tsv` (795 lines).

### 5.3 A missing jar fails loudly

```bash
mv use-core/src/test/resources/historical/use.jar /tmp/…/use.jar.parked
mvn -q clean && mvn -B -pl use-core test -Dtest=UncertaintyDifferentialSmokeTest
```
(`mvn -q clean` is required — otherwise the stale copy under `target/test-classes/` still satisfies
the lookup, because `maven-resources-plugin` does not delete files it no longer has a source for.)

```
org.tzi.use.uncertainty.differential.HistoricalOracle$HistoricalOracleUnavailableException: 
historical oracle jar 'use.jar' was not found. It must be committed at use-core/src/test/resources/historical/use.jar so that Maven copies it to target/test-classes/historical/. Paths tried, in order:
  code source of HistoricalOracle -> /home/xoruser/msc-4/use-msc2026/use-core/target/test-classes/historical/use.jar
  relative to cwd -> /home/xoruser/msc-4/use-msc2026/use-core/use-core/target/test-classes/historical/use.jar
  relative to cwd -> /home/xoruser/msc-4/use-msc2026/use-core/target/test-classes/historical/use.jar
  relative to cwd -> /home/xoruser/msc-4/use-msc2026/use-core/use-core/src/test/resources/historical/use.jar
  relative to cwd -> /home/xoruser/msc-4/use-msc2026/use-core/src/test/resources/historical/use.jar
  classloader resource historical/use.jar
	at use.core@7.5.0/org.tzi.use.uncertainty.differential.HistoricalOracle.materialise(HistoricalOracle.java:199)
	at use.core@7.5.0/org.tzi.use.uncertainty.differential.HistoricalOracle.open(HistoricalOracle.java:129)
	at use.core@7.5.0/org.tzi.use.uncertainty.differential.UncertaintyDifferentialSmokeTest.smokeURealAdd(UncertaintyDifferentialSmokeTest.java:41)
```
```
[ERROR] Tests run: 6, Failures: 0, Errors: 4, Skipped: 0
[INFO] BUILD FAILURE
```

Errors, not skips. Every path that was tried is named. The jar was moved back immediately and its
digest re-verified:

```
80ac8ae433b8345677472019991356950f094f4a104cfbce1f75783a7308788d  use-core/src/test/resources/historical/use.jar
```

Two further failure modes are covered by permanent tests rather than by hand:
`missingJarFailsLoudly()` (absent jar, via the authoritative directory override) and
`wrongDigestFailsLoudly()` (right file names, wrong bytes → digest complaint).

### 5.4 The isolation test passes and would catch self-comparison

```
[INFO] Tests run: 9, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.038 s -- in HistoricalOracle class-loader isolation
```

See §3 for why it is not vacuous.

### 5.5 Full run and test-count delta

```bash
mvn -q clean && mvn -B -pl use-core test
```
```
[INFO] Results:
[INFO] 
[INFO] Tests run: 27, Failures: 0, Errors: 0, Skipped: 0
[INFO] 
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time:  19.297 s
```

Per class:

| Test class | Tests run | |
|---|---|---|
| `org.tzi.use.architecture.MavenCyclicDependenciesCoreTest` | 11 | pre-existing |
| `org.tzi.use.uml.mm.ModelAPITest` | 1 | pre-existing |
| `org.tzi.use.uncertainty.differential.HistoricalOracleIsolationTest` | 9 | **added** |
| `org.tzi.use.uncertainty.differential.UncertaintyDifferentialSmokeTest` | 6 | **added** |

**`use-core` baseline 2 classes / 12 methods → 4 classes / 27 methods. Delta exactly +2 classes /
+15 methods, all of them mine. No pre-existing test broke.**

A note on the baseline figure. The brief states the baseline as 3 classes / 13 methods; that is the
**whole reactor** (S0 §2), which includes `use-gui`'s `MavenLayeredArchitectureTest` (1 method).
`use-core` alone is 2 / 12, measured directly. Both figures were re-confirmed on this branch by
parking the S1 files aside and re-running:

```
[INFO] Tests run: 12, …  (use-core)
[INFO] Tests run: 1,  …  (use-gui, MavenLayeredArchitectureTest)
[INFO] BUILD SUCCESS
```

Full reactor after the change: `use-core` 27, `use-gui` 1, `BUILD SUCCESS`.

The ArchUnit cycle counts are unchanged by the new package (`Cycles in core module with tests: 210`,
`without tests: 55`, identical before and after). Those tests only print, they do not assert.

---

## 6. Residual risk

1. **The stub is not the port.** Every "AGREE" in this stage is agreement between the historical
   jars and a 40-line re-derivation of two formulas. S1 demonstrates that the instrument works; it
   demonstrates nothing about any port. `StubCandidate` should be deleted or left unused once S4
   lands, and it is documented in-file as not-the-port.
2. **Coverage is two operations wide.** `URealValue.add` and `minus`, plus incidental use of
   `UIntegerValue.add`, `UBooleanValue.and`, `UStringValue.uSize`/`uToString`/`at`. The remaining
   ~70 methods of the historical surface are reachable through `UOp`/`HistoricalOracle` but have
   never been exercised. S2/S3 must widen this.
3. **`uTypesResolveOnlyThroughTheOracle()` asserts the U-types are *absent* from the application
   loader.** That is correct at S1 and will start failing the moment the port lands. The test says
   so in its own failure message: it must then be **inverted** to assert distinctness, not deleted.
   `sameNameDistinctClasses()` is the assertion that survives unchanged, which is why it is written
   against `RealValue`.
4. **Exact comparison may prove too strict.** `UValue.canonical()` compares via
   `Double.toString`, so `0.0` and `-0.0` are a `DIFFER` and `NaN` equals `NaN`. That is the right
   default for detecting regressions, but later stages may find legitimate `-0.0` noise. Loosening
   it must be an explicit, recorded decision, never a quiet epsilon.
5. **One unexplained transient.** In the very first full-reactor run after the change,
   `mvn -B test` failed with `TestEngine with ID 'junit-jupiter' failed to discover tests`,
   `Tests run: 0`. It has not recurred in 7 subsequent full-reactor runs (4 with the change, 3 with
   the S1 files parked aside), and the `target/` evidence was destroyed by the next `clean` before
   it could be examined. I could not attribute it to this change and I could not rule it out.
   Recorded here rather than omitted.
6. **`Locale`.** Canonical forms use `Double.toString` and hand-rolled hex, both locale-independent.
   The harness deliberately does not call `Locale.setDefault`, which would be a global side effect
   on every other test in the JVM.
7. **The jars are Java 7 bytecode (major 51).** Java 21 loads them today. A future JDK that drops
   class-file version 51 would take the oracle with it. Nothing in this stage can mitigate that;
   it is a reason to finish the port rather than to depend on the oracle indefinitely.

---

# 7. AMENDMENT (appended 2026-08-17, after §1–§6 were written)

Everything above this line is the original S1 record and is **unchanged**. This section records the
audit verdict on it, the defect that verdict turned on, the fixes, the post-fix verification, and
what is still open. Where §7 and §1–§6 disagree, §7 wins.

Provenance of every claim below: `docs/port2/audit-01-harness.md` and `audit-00-verdict.md`
(the audit), commit `cf9d2f45` (the fixes), `docs/port2/stage-01-verification-post-fix.md`
(empirical re-verification, commit `0a3f4878`) and `docs/port2/stage-01-static-review-post-fix.md`
(independent static review of the same commit range).

## 7.1 The verdict: DEFECTIVE, then GO_AFTER_FIXES

The adversarial audit of S0–S2 graded the harness **DEFECTIVE** and the foundation
**GO_AFTER_FIXES**. §5 above ("Acceptance — commands and pasted output") is not wrong about what it
ran; it is wrong about what a green run meant.

## 7.2 Defect D1 — the harness scored its own failures as agreement

**Reproduction, before the fix:**

```
sweepBinary(UOp.binary("URealValue","add"), uIntegerBoundaries(), uIntegerBoundaries())
-> 169 rows, AGREE_THROWN=169, disagreements 0
```

169 rows of "perfect agreement" in which **neither side ever entered `URealValue.add`**. The
mechanism was three cooperating decisions:

1. `DifferentialSweep.apply()` wrapped marshalling in `catch (Throwable)`, so the oracle's own
   "I cannot marshal this input" — thrown *before* the historical method was called — became an
   ordinary recorded throw.
2. `classify()` called two throws an agreement whenever the throwable class names matched.
3. `AGREE_THROWN.isAgreement()` was `true`.

A `UInteger` argument handed to a `URealValue` receiver therefore failed identically on both sides
and was scored as the two implementations agreeing. **This is the failure mode that matters most
for an instrument: it does not produce a wrong answer, it produces a confident answer about a
measurement that never happened.**

## 7.3 The eleven fixes (commit `cf9d2f45`, test-scoped, `*/src/main/*` untouched)

| | Fix |
|---|---|
| **F1** | New `HarnessMarshallingException`, thrown by every failure exit of `HistoricalOracle.invoke`/`toHistorical`/`fromHistorical`. `DifferentialSweep` catches it separately and scores `DiffVerdict.HARNESS_ERROR`, whose `isAgreement()` is `false`. The report column reads `HARNESS_ERROR:<class>`, textually distinct from `THROWN:<class>`. `classify()` checks the harness-error population **first**, so it can never merge into `AGREE_THROWN` |
| **F2** | `supports()` returns `false` for any receiver type `toHistorical` cannot build (`MARSHALLABLE_RECEIVERS`). The 39-operation `SBooleanValue` block previously reported `supports()==true` with no marshalling behind it; it now lands on a visible `UNSUPPORTED` |
| **F3** | `DiffReportWriter.write` guards on the **total row count**, not `results.isEmpty()` — the old guard counted `Result` objects, so a non-empty list of empty results wrote a clean-looking report about nothing |
| **F4** | The `OPAQUE` branch no longer embeds the foreign `toString()`. `javap -c` on the vendored jar shows `uDataTypes` `UInteger`/`UReal`/`SBoolean`/`UString`/`UUnlimitedNatural` all format with `%5.3f` through the **no-Locale** `String.format(String,Object[])` overload: comparison was rounding to 3 decimals and would flip to a decimal comma under a European locale. Representation is now rebuilt from declared instance fields via `Double.toString` |
| **F5** | `supports()` catches only the new `NoSuchHistoricalMethodException`, not `RuntimeException` — which had turned "the oracle jar is broken" into "operation not implemented" |
| **F6** | `Method.invoke` returns `null` for `void`, and `fromHistorical` mapped that to `Kind.NULL`, so **every void operation agreed with every other one forever**. New `UValue.Kind.VOID` |
| **F7** | A `Candidate` returning Java `null` NPE'd inside `classify()` and discarded every row already computed |
| **F8** | `apply()` catches `Exception`; `Error` is re-thrown. `StackOverflowError`/`AssertionError`/`NoClassDefFoundError` describe a broken run, not a behavioural difference |
| **F9** | Comment-only: corrects `assertIsolated`'s overstated Javadoc (separate commit `3959127f`, verified documentation-only) |
| **F10** | Reports go to `use-core/target/differential/` instead of overwriting the tracked `docs/port2/differential/*.tsv` on every run, and each is compared against the committed golden |
| **F11** | New `DifferentialHarnessRegressionTest` — 11 Jupiter methods pinning the above, including the D1 reproduction itself |

## 7.4 Post-fix verification — reproduced independently, not taken on trust

An empirical verifier re-ran everything with `java` directly and with Maven; a second verifier did a
source-only review of `97f9f2c3..HEAD`. Both reproduced these:

| Check | Result |
|---|---|
| D1 sweep, after | `169 rows, HARNESS_ERROR=169, disagreements 169` (was `AGREE_THROWN=169, disagreements 0`). Columns stay separated: `HARNESS_ERROR:…HarnessMarshallingException` vs `THROWN:java.lang.IllegalArgumentException` |
| Why the guard always fires | `javap -p` shows `UIntegerValue` and `URealValue` both `extends UncertainValue` — **siblings**, not sub/supertype, so `URealValue.isInstance(UIntegerValue)` is false on all 169 rows |
| F2 | `supports(UOp.binary("SBooleanValue","and"))` = `false`; control `supports(UOp.binary("URealValue","add"))` = `true`. Sweep lands on `81 rows, UNSUPPORTED=81`, disagreements 81 |
| `HARNESS_ERROR` non-agreement | Enforced in **every** consumer: `DiffVerdict.isAgreement()`, `Result.disagreements()`, the `EnumMap` tally pre-seeded from `DiffVerdict.values()`, `summary()`, and `DiffReportWriter.writeAll`'s `# verdict.*` headers |
| Planted-defect detection | 4/4 flagged. `URealValue.neg` 22 rows AGREE 1 / DIFFER 21; `URealValue.add` 484 rows 193/291; `URealValue.minus` 484 rows 381/103; `UIntegerValue.add` 169 rows 25/144. Row 0 of the first is `UREAL(-0.0,0.0)` vs `UREAL(0.0,0.0)` → DIFFER, proving `canonical()` distinguishes `-0.0` from `0.0` |
| F3 | Zero-row report refused with an accurate message; no file created |
| F10 determinism | Two full `verify` runs produced files byte-identical to each other **and** to the committed goldens (sha256 `549d33e6…`, `21b39230…`). `git status --porcelain` empty after both |
| Acceptance | `mvn -q clean` then `mvn -B verify -Djava.awt.headless=true` → **BUILD SUCCESS, 39 surefire + 130 failsafe = 169, 0 failures / 0 errors / 0 skipped**, twice. Delta from 28+130=158 is exactly **+11**, exactly the 11 methods of `DifferentialHarnessRegressionTest`. No pre-existing test broke, was removed, or was skipped |
| Scope | `git diff --name-status 30d480db..HEAD -- '*/src/main/*'` **empty**; all 17 `*/src/test/*` entries are additions under `.../uncertainty/differential/`; `'*module-info.java'` empty |
| Test pinning | **10 of the 11** new regression methods demonstrably fail on the pre-fix source at `97f9f2c3` |

**Correction to §5.5 above.** §5.5 records `mvn -B -pl use-core test` = 27. That command is the
wrong gate: the reactor also runs maven-failsafe-plugin, contributing 130 integration tests
(`OCLExpressionIT` 1, `ShellIT` 129) that `mvn test` never touches. The real gate is
`mvn -B verify -Djava.awt.headless=true`; see `stage-00-baseline.md` §2 and `specification.md` C1.
The figures in §5.5 are correct for what they measured and are not the whole gate.

## 7.5 STILL OPEN — defect D2, same family, a door F1–F11 do not close

> **SUPERSEDED — D2 was closed at the root in `e8b73e48`. See §8.1.** The section below is left
> exactly as written; it records the state on the morning of 2026-08-17. The code it quotes no
> longer exists: `AGREE_THROWN` and `DIFFER_THROWN` were **deleted** from `DiffVerdict`, and the
> "minimum fix" this section proposes (compare messages too) was **rejected** as still leaving a
> route to green without two observed values. §8 says what was done instead, and what is still open.

**S1 is not "done". D2 is unfixed at the time of writing.**

`DifferentialSweep.classify` decides throw-agreement on the throwable **class name alone**, and
when the class names match it discards **both messages** into an empty note:

```java
// use-core/src/test/java/org/tzi/use/uncertainty/differential/DifferentialSweep.java:169-176
if (ref.thrown != null && sub.thrown != null) {
    boolean same = ref.thrown.getClass().getName().equals(sub.thrown.getClass().getName());
    String note = same ? "" : "reference message: " + safeMessage(ref.thrown) + " / subject message: " + …;
```

The historical uncertainty code signals its type errors with a **bare `java.lang.RuntimeException`**.
So a port whose every method body is `throw new RuntimeException("TODO: port " + op.key())` —
which is not a contract violation, since `Candidate.invoke` is documented as throwing whatever the
implementation under test throws — scores
`UIntegerValue.power(value): 169 rows, AGREE_THROWN=169, disagreements 0` on a **well-typed**
corpus. Numerically the same reading as D1, reached through a different door. Estimated blast
radius **15 081 of 43 136 rows (35 %)** across 12 operations on two receiver types once cross-type
argument corpora are included. **No regression test constructs a genuine `AGREE_THROWN` and asks
whether the two throws meant the same thing.**

Minimum fix: `AGREE_THROWN` must require more than a class-name match — compare messages too, and
record **both** messages in the note regardless of verdict; or add a non-agreement
`DIFFER_THROWN_MESSAGE`; or refuse to treat `java.lang.RuntimeException`/`java.lang.Exception` as
agreeable at all. **A throw row's note should never be empty.**

### Also open (MAJOR, from the static review)

* **`Candidate` was not modified.** Its contract still says only "@throws Throwable whatever the
  implementation under test throws"; nothing tells a future `Candidate` author — or the S4 ported
  adapter — that a marshalling failure must use `HarnessMarshallingException`. `StubCandidate`, the
  only worked example in the tree, still signals harness-level failure with plain
  `IllegalArgumentException`, reproducing D1 verbatim between two stubs. **From S4 the subject is
  the port**, and the `HistoricalOracle`-first ordering in `classify()` does not protect a
  subject-side marshalling failure that coincides with a genuine historical throw of the same class.
* **F2 replaced an invisible failure with a mislabelled one.** A sweep of `SBooleanValue.and` now
  writes "historical does not implement `SBooleanValue.and(value)`" — but `javap` shows it *does*.
  "The port target lacks this operation" and "the instrument cannot drive this operation" are
  opposite findings and are currently indistinguishable in both the verdict and the note column. A
  distinct `UNMARSHALLABLE` verdict, or at minimum a note that says *why*, is the correct shape.
* **`supportsSwallowsOnlyAMissingMethod` is a tautology.** All three of its assertions pass verbatim
  on the pre-fix catch clause, so **F5 has no regression protection**; and the F5 path is
  unreachable as shipped because the constructor eagerly loads a superset of
  `MARSHALLABLE_RECEIVERS`. (The narrowing is still correct and should stay as preventive defence.)

### Also open (MINOR)

* **"byte for byte" is false.** `DiffReportWriter.assertMatchesGolden` is documented at `:187` as
  comparing byte for byte; it compares `Files.readAllLines`. CRLF-vs-LF, a lost trailing newline and
  a leading BOM all compare equal. The sha256 identity reported in §7.4 is a **measurement by the
  verifier, not a property the test enforces.** Correct the claim or strengthen the comparison.
* **The zero-row guard (F3) lives only in the report writer**, not in `DifferentialSweep`. A caller
  that asserts `result.disagreements().isEmpty()` without writing a report still gets a silent pass
  on an empty input domain. The porter's own test comments on this, so the placement is conscious —
  but it is a live unguarded path.
* **`supports()` never validates argument marshallability**, only receiver type and method
  existence, though its Javadoc promises to answer whether the oracle "can actually be driven
  through `op`". `toHistorical` has no `case SEQUENCE`. Now visible as `HARNESS_ERROR` rather than
  `AGREE_THROWN`, so not a correctness hole — but the guarantee is half implemented.
* **Declared coverage loss.** F2 removes an entire receiver family from the instrument's reach:
  roughly a fifth of the specified operation inventory is now permanently `UNSUPPORTED`, and the
  collection operations named in the spec's R16 risk (`uIncludes`/`uExcludes`/`uCountC`) are
  unreachable too, since neither `SequenceValue` nor `SetValue` is in `MARSHALLABLE_RECEIVERS`.
* **`-Duse.differential.golden.refresh=true` has never been executed.** It is the only documented
  recovery from a golden mismatch, is read with `Boolean.getBoolean` from the forked surefire JVM,
  and neither `pom.xml` nor `use-core/pom.xml` configures maven-surefire-plugin at all.
  **UNVERIFIED.**

## 7.6 Amended residual risk

§6 above stands, with two amendments and one addition:

* §6 item 1 ("the stub is not the port") is **stronger than written**: per D2 and the `Candidate`
  finding, `StubCandidate` is not merely uninformative, it is an active reproduction of the D1
  failure mode for anyone who copies it.
* §6 item 2 ("coverage is two operations wide") must now also record that **`SBooleanValue`'s 39
  operations are permanently out of reach**, and that per `specification.md` C3 the whole of
  `org.tzi.use.uml.ocl.type.*` and `uDataTypes.*` is unreachable by design.
* **New.** The harness has now had two defects of the identical shape (agreement recorded where no
  comparison happened) found by two different reviewers in two different code paths. Treat "a green
  differential run" as evidence only after asking *what the rows actually compared* — the summary
  line is not the evidence, the verdict distribution and the notes are.

---

# 8. AMENDMENT 2 (appended 2026-08-17, after §7)

§1–§7 are unchanged. This section closes D2, records what replaced it, and states the **current**
verdict, which is not "done".

Provenance: commit `e8b73e48` (the fix); `docs/port2/stage-01-verification-round3.md` (empirical
re-verification, commit `0af4e106`); `docs/port2/stage-01-static-review-round3.md` (an independent
source-only review of the same commit, written by a second reviewer who never ran Maven). The two
round-3 reviewers worked separately and **agreed on the headline finding**, which is why it is
stated here as fact rather than as one reviewer's opinion.

## 8.0 The story so far, in one place

| # | Event | Instrument's claim | What was actually true |
|---|---|---|---|
| 1 | §1–§6: harness built, `dfc3c063` | "784 rows, AGREE=784" | true of *those* rows — but the instrument had never been asked what a green row meant |
| 2 | **DEFECTIVE — D1**, audit `97f9f2c3` / `3cb92468` | `169 rows, AGREE_THROWN=169, disagreements 0` | **neither side ever entered `URealValue.add`.** The oracle's own marshalling failure was scored as the two implementations agreeing |
| 3 | Fixed: F1–F11, `cf9d2f45` | D1 sweep now `169 rows, HARNESS_ERROR=169, disagreements 169` | correct, and independently reproduced (§7.4) |
| 4 | **DEFECTIVE AGAIN — D2**, `0a3f4878` | `rows 43136, AGREE_THROWN 15081, disagreements 28055` | **15 081 of 43 136 rows (35 %) green against a `Candidate` containing no code** — every body `throw new RuntimeException("TODO: port …")`. The historical code raises bare `java.lang.RuntimeException`, and `AGREE_THROWN` matched on class name alone |
| 5 | Throw-agreement removed **at the root**, `e8b73e48` | same sweep, widened to all 285 reachable operations: `rows 471471, agreement rows 0` | correct, and independently re-measured (§8.1) |
| 6 | **DEFECTIVE AGAIN — the third door**, `0af4e106` + the round-3 static review | a `Candidate` whose every body is **empty** scores `AGREE=444` | **`VOID` vs `VOID` is scored as agreement.** See §8.2 |

Steps 2, 4 and 6 are the same defect three times: *the absence of a measurement recorded as a
measurement the two sides share*. Each was found by a different reviewer, in a different code path,
after the previous one had been declared fixed.

## 8.1 D2 is CLOSED — throw-agreement was deleted, not tightened

§7.5 proposed a minimum fix (compare messages as well as class names). **That fix was rejected**,
because a port could still be green on rows where nothing was compared. What landed instead:

| | Fix in `e8b73e48` |
|---|---|
| **FIX 1** | **`AGREE_THROWN` and `DIFFER_THROWN` are deleted from `DiffVerdict`.** Both throw-outcomes collapse into one verdict `BOTH_THREW`, whose `isAgreement()` is `false`, so a throw-pair lands in `disagreements()` and can never accumulate into a green number. Its note is **never empty**: it always carries both throwable classes *and* both messages. The class-name comparison is not lost — both result columns already render `THROWN:<class>` per side — it is simply no longer disguised as a finding |
| **FIX 1a** | `AcceptedThrowPairs` is the explicit, opt-in escape hatch: a throw-pair may be adjudicated an agreement only by an entry keyed on operation **+ both throwable classes + both messages verbatim**, which is refused without a written rationale, and that rationale is copied into the note of every row it adjudicates. Default is `AcceptedThrowPairs.none()` everywhere |
| **FIX 2** | `UnwrittenPortInvariantTest` — a **standing invariant** rather than another per-route regression test: a `Candidate` that implements nothing must produce **zero** agreement rows. It enumerates operations by reflection from the historical jars and takes corpora from `InputGenerator`'s own accessors, so it widens by itself as the harness widens |
| **D-1** | Two `Candidate`s returning Java `null` both raised `NullPointerException` — matching class names. A `null` return is now a `HarnessMarshallingException` → `HARNESS_ERROR` |
| **D-2** | The marshalling invariant was stated on `HistoricalOracle` only, so two `StubCandidate`s reproduced the D1 tally verbatim. The invariant moved onto the `Candidate` interface and the shipped stub obeys it |
| **D-3** | The `UNSUPPORTED` note asserted "historical does not implement `SBooleanValue.and(value)`" — `javap` on the loaded jar shows it does. New `Candidate.unsupportedReason(UOp)`; the oracle now says "this harness cannot marshal a `SBooleanValue` receiver … a limit of the instrument" |
| **D-4** | `supportsSwallowsOnlyAMissingMethod` was a tautology (§7.5). Rewritten around a **closed** oracle, and verified to fail on both pre-fix variants |

**Re-measured independently by the round-3 empirical verifier, not taken on trust** — probes compiled
and run with `java` directly, from outside the repository, against the built test classes:

```
### SUBJECT: A. every body throws (the porter's subject)  (unwritten-port)
    rows            471471
    agreement rows  0
    verdict tally   {BOTH_THREW=30580, HARNESS_ERROR=388695, MIXED=52196}
    INVARIANT (agreement==0): HOLDS
```

`21816 + 8764 == 30580`: **no row changed population, only the claim made about it.** The static
reviewer separately corroborated the pre-fix mechanism in bytecode — `javap -c` on the vendored jar
shows historical `UIntegerValue.power` constructing a bare `java.lang.RuntimeException` with the
exact quoted message that a `TODO: port …` stub matched on.

Also confirmed by re-measurement: `AGREE_THROWN` / `DIFFER_THROWN` are absent from
`DiffVerdict.values()`; `agreements().size() + disagreements().size() == rowCount()` on every probe
run; a byte-perfect port (a second `HistoricalOracle`) yields **`DIFFER=0` over 471 471 rows**, so
`UValue.canonical()`'s exact `Double.toString` comparison generates no spurious noise; four of five
planted defects are reported as divergence; and `AcceptedThrowPairs` cannot be made over-broad —
wildcard-shaped entries (`*`, `UIntegerValue.*`, `refMsg='*'`) all adjudicate **0** rows against a
real 208-row sweep, because matching is exact string equality over all five discriminators.
Re-greening the whole unwritten port through the allowlist would take **291 separately authored,
separately reviewable entries covering 39 operations**. That is real, working friction.

**Build state at `e8b73e48`, measured twice from `mvn -q clean`:**

```
mvn -B verify -Djava.awt.headless=true
  surefire 46  (45 use-core + 1 use-gui)   failsafe 130  (1 use-core + 129 use-gui)   = 176
  0 failures, 0 errors, 0 skipped
```

Delta `169 → 176` fully accounted: `DifferentialHarnessRegressionTest` 11→16 (+5),
`UnwrittenPortInvariantTest` 0→2 (+2); smoke and isolation unchanged at 6 and 9. Two clean runs
produced **byte-identical** reports (checked with `cmp`), both goldens match byte-for-byte, and
`git diff --name-status 30d480db..HEAD -- '*/src/main/*'` is **EMPTY**.

## 8.2 CURRENT VERDICT — **DEFECTIVE. A third door was found.**

**S1 is not sound.** The harness can still report agreement where it observed no value at all — this
time without any throw, any marshalling failure, or any `null`. It goes through `AGREE` itself.

### D-10 (CRITICAL) — `VOID` vs `VOID` is scored `AGREE`

`DifferentialSweep.classify` ends:

```java
// use-core/src/test/java/org/tzi/use/uncertainty/differential/DifferentialSweep.java:222
boolean agree = ref.value.canonical().equals(sub.value.canonical());
```

`UValue.canonical()` renders `Kind.VOID` as the constant string `"VOID"` (`UValue.java:260-261`),
and `isAgreement()` admits `AGREE` (`DiffVerdict.java:109-111`). `HistoricalOracle.invoke` returns
`UValue.voidValue()` for any void-returning method **on the strength of
`method.getReturnType() == void.class` alone, without inspecting any outcome** — and `Candidate`'s
own contract Javadoc instructs the S4 adapter to "use `UValue.voidValue()` for a void operation".
So **every void row is green by construction**, on both sides, for any subject that follows the
documented contract.

Measured over the same 285-operation / 471 471-row sweep the standing invariant runs:

```
### SUBJECT: C. every body EMPTY -> UValue.voidValue()  (do-nothing-port)
    rows            471471
    agreement rows  444
    verdict tally   {AGREE=444, DIFFER=51752, HARNESS_ERROR=388695, MIXED=30580}
    68	IntegerValue.setTypeToRuntimeType()	INTEGER(0)	VOID	VOID	AGREE
    INVARIANT (agreement==0): *** VIOLATED ***
```

All 444 are *every* row of *every* void operation the harness can currently reach:

```
   144  URealValue.setTypeToRuntimeType()       54  UBooleanValue.setTypeToRuntimeType()
   102  UStringValue.setTypeToRuntimeType()     48  IntegerValue.setTypeToRuntimeType()
    90  UIntegerValue.setTypeToRuntimeType()     6  RealValue.setTypeToRuntimeType()
```

Eight void operations are reachable in total; `BooleanValue` / `StringValue.setTypeToRuntimeType()`
land on `HARNESS_ERROR` today only because the shipped corpora contain no `BOOLEAN` or `STRING`
receiver. **Widening `MARSHALLABLE_RECEIVERS` or the corpora widens this defect.**

This is verbatim the rule `DiffVerdict` was rewritten in `e8b73e48` to enforce
(`DiffVerdict.java:7-10`): *"A differential oracle may report agreement only where it observed two
comparable values."* `UValue.java:59-67` defines `VOID` as *"there is no result to compare"*.
`UNSUPPORTED` and `HARNESS_ERROR` were each carved out as distinct **non**-agreements for exactly
this reason. `VOID` was not.

**The aggravating detail.** `UValue.java:62-66` and `DifferentialHarnessRegressionTest.java:422-423`
both name "an empty-bodied ported mutator agreed with the historical one on every row, forever" as
the defect the `NULL` / `VOID` split closed. Measured, that consequence is still true: before the
split both sides rendered `NULL` and agreed unconditionally; after it both render `VOID` and agree
unconditionally. The split usefully separates a void result from a genuine `null` result — it buys
nothing against the defect its own comment claims it fixed.

**Why the standing invariant missed it.** `UnwrittenPortInvariantTest` instantiates **one** encoding
of "implements nothing": a subject whose every body throws. A second, equally natural encoding —
every body empty, which is literally what the `Candidate` Javadoc tells the S4 author to write —
violates the invariant by 444 rows. The test's own Javadoc diagnoses this exactly: *"Pinning each
route with its own regression test is chasing instances … The invariant closes the whole family."*
It does not close the family; it pins one more instance.

### The rest of what round 3 found

| Finding | Sev | Where | Statement |
|---|---|---|---|
| **D-10** | CRITICAL | `DifferentialSweep.java:222` | above. **Reached independently by both round-3 reviewers.** |
| **D-11a** | MAJOR | `UnwrittenPortInvariantTest.java:23` | The invariant's headline claim ("over every operation … and every input corpus it ships") is **false as written** — it quantifies over operations and corpora but not over *subjects*. Fix: run it over a list of degenerate candidates (throws / Java `null` / `nullValue()` / `voidValue()`) |
| **D-11b** | MAJOR | `DiffReportWriter.java:96` | The zero-row guard counts **rows, not measurements**. `if (rowTotal == 0) throw …` refuses a report about nothing, but accepts a report with many rows and zero comparisons. A void-only sweep writes headers reading `# rows.agreement 72 / # rows.disagreement 0 / # verdict.AGREE 72` over zero comparisons. Round 2 added those headers so "a reader never has to infer greenness from verdict names again" — here the header states the misleading number outright, which is strictly worse |
| **D-11c** | MAJOR | `DifferentialSweep.java:186-199` | The `HARNESS_ERROR` note says "no measurement on either side" and then quotes exactly **one** message — the reference's — unattributed; both columns carry the identical `HarnessMarshallingException` class name, so the subject's failure reason is unrecoverable from the report. This is the same evidence-destruction the sibling `BOTH_THREW` branch **ten lines below** was rewritten in this very commit to eliminate |
| **D-12a** | MAJOR | `DifferentialSweep.java:334` | The zero-row trap is still open at the **`Result`** level; round 2 guarded only `DiffReportWriter`. There is no `measuredRowCount()`, no `assertMeasured()`, and `agreementCount() == 0` is equally consistent with "nothing ran" and "everything diverged". Not hypothetical framing: a byte-perfect port yields 419 275 non-agreement rows of 471 471 (88.9 %), so `disagreements().isEmpty()` is unreachable for any real sweep and its only surviving uses are the degenerate ones |
| **D-12b** | MAJOR | `AcceptedThrowPairs.java` | **Committed as a binary file.** Six raw `NUL` bytes sit in `char` literals instead of the escape. Legal Java, correct behaviour — and `git show --stat e8b73e48` reports `Bin 0 -> 6884 bytes`, `git show -p` prints `Binary files … differ`, and `git grep` finds nothing in it. **Re-verified directly while writing this section.** The class it hides is the *only* remaining route by which a run can score green without two observed values, and the porter's own hand-off describes its safeguard as "social (a reviewed, written rationale per exact pair)". A safeguard enforced by human review has been committed in the one encoding that defeats human review. Fix: use the two-character escape, or a printable separator such as a pipe; the separator is internal to the map key |
| **D-13a** | MINOR | `DiffVerdict.java:74` | Deleting `DIFFER_THROWN` removed the only **aggregated** signal for a wrong-exception-class defect. Right failure, wrong throwable type is now bit-identical to a correct port in `tally()`, `count()`, `rowCount()`, `agreementCount()`, `disagreements().size()` and every `# verdict.*` / `# rows.*` header. Visible at row level and caught by a golden byte-diff; **invisible to any sweep that tallies without a golden** — which includes the invariant sweep |
| **D-13b** | MINOR | `StubCandidate.java:116-118` | Three exits were converted to `HarnessMarshallingException`; a **fourth** still raises `UnsupportedOperationException`, and the class comment added in the same commit says "the three failure exits below". Unreachable through a sweep today because `supports()` is consulted first, but the two are kept in sync by hand |
| **D-14** | MINOR | `DiffReportWriter.java:94-162` | The report never records **which allowlist was in force**. `AcceptedThrowPairs.describe()` — whose Javadoc says it is "for a report header or a stage document" — is called from nowhere, and `# verdict.ACCEPTED_THROW` is emitted only when non-zero. A run with a non-empty allowlist that adjudicates zero rows is byte-indistinguishable from a run with `none()` |
| **D-15** | MINOR | `DiffReportWriter.java:196` | = §7.5's "byte for byte" MINOR, re-confirmed unfixed. `assertMatchesGolden` compares `Files.readAllLines`, blind to line terminators and to a missing final newline. Both round-3 byte-identity claims deliberately use `sha256sum` / `cmp` instead of this method |
| **D-16** | MINOR | `DifferentialSweep.java:215-221` | The `MIXED` note does not name **which** side threw. Recoverable from the columns, so far weaker than D-11c — but `MIXED` is 52 196 rows of the invariant sweep and the note could say "reference" / "subject" at no cost |

*Numbering note.* The two round-3 reviewers both reached D-10 independently and then diverged in
their numbering of the rest. The suffixed ids above are this document's; each row names the
file:line, so there is no ambiguity about which finding is meant.

## 8.3 What must be true before S4 may use this instrument

1. **`VOID` must stop being an agreement.** Either a distinct non-agreement verdict — the shape
   already used for `UNSUPPORTED` and `HARNESS_ERROR`, e.g. `NO_OBSERVABLE_RESULT` — or an explicit
   `AcceptedVoidOperations` sign-off mirroring `AcceptedThrowPairs`. **Do not** fix it by excluding
   void operations from the inventory: that hides the row instead of classifying it, which is the
   mistake round 1 made.
2. **The invariant must quantify over subjects, not name one.** Throws / returns Java `null` /
   returns `nullValue()` / **returns `voidValue()`**, each asserted to zero agreement.
3. **`Result` needs a measurement floor** — a `measuredRowCount()` counting only rows where two
   values were observed, and a guard a stage can assert. Move the writer's guard onto that quantity
   so an all-`VOID` report is refused.
4. **Correct the two comments that claim the empty-mutator defect is closed** (`UValue.java:62-66`,
   `DifferentialHarnessRegressionTest.java:422-423`). They are part of why the defect survived a
   review: a reader who checks whether it was handled finds a sentence saying yes.
5. **De-binarise `AcceptedThrowPairs.java`**, and consider a `.gitattributes` (`*.java text
   diff=java`) so this cannot recur silently — the repository currently has none.

## 8.4 The standing lesson, restated

§7.6's closing note said the harness had had **two** defects of the identical shape. It has now had
**three**, found by three different reviewers in three different code paths, each after the previous
one had been declared fixed. The invariant introduced to end the pattern was itself an instance of
the pattern.

**No number this harness produces is evidence until you can name the two values that were compared
to produce it.** For S4–S7 that means a stage may not report a fidelity figure from a tally alone.
It must report, alongside it, how many rows were *measurements* — and until D-10 and D-12a are
fixed, the harness cannot answer that question.

---

# 9. AMENDMENT 3 (appended 2026-08-17, after §8) — the third door is closed

Commits `fa9bba2d` (reviewability, no behaviour change) and `93e038ac` (behaviour), on
`port-uncertainty-2`. Everything below was run on this machine; the counts come from
`mvn -q clean && mvn -B verify -Djava.awt.headless=true`, twice.

## 9.1 D-10 — `VOID` vs `VOID` was an agreement; it is now `UNMEASURABLE`

The measurement, made with the committed parameterised invariant rather than with a probe, same seed
and same corpora before and after:

```
BEFORE  c-empty-body   rows 471471  agreement 444  {AGREE=444, DIFFER=51752, HARNESS_ERROR=388695, MIXED=30580}
        fully agreed operations (every driven row scored AGREE):
          IntegerValue.setTypeToRuntimeType()    48/48        UIntegerValue.setTypeToRuntimeType()   90/90
          RealValue.setTypeToRuntimeType()        6/6         URealValue.setTypeToRuntimeType()    144/144
          UBooleanValue.setTypeToRuntimeType()   54/54        UStringValue.setTypeToRuntimeType()  102/102

AFTER   c-empty-body   rows 471471  agreement   0  {DIFFER=51752, HARNESS_ERROR=388695, MIXED=30580, UNMEASURABLE=444}
        fully agreed operations: (none)
```

`UNMEASURABLE` is raised only when **neither** side carries an observation — both `VOID`, both
`NULL`, or one of each. One-sided absence is left as `DIFFER`: if the reference returned a value and
the subject returned `VOID`, the harness *did* see something distinguishing, and calling that "no
measurement" would destroy the evidence. That is why 51752 rows of subject C remain `DIFFER`.

§8.3.1 offered two routes and warned against a third. The route taken is the first (a distinct
non-agreement verdict). No operation was excluded from the inventory: all 285 are still swept and the
444 rows are still in the report, reclassified rather than hidden.

## 9.2 The audit for other degenerate observations

`DifferentialHarnessRegressionTest.everyKindIsEitherAnObservationOrUnmeasurable` iterates
`UValue.Kind.values()`, demands a representative value for each kind, and asserts that each kind
either carries a value (two of them are `AGREE`) or does not (two of them are `UNMEASURABLE`) — and
that the "carries nothing" set is exactly `{NULL, VOID}`. `UNSUPPORTED` and `HARNESS_ERROR` were
already non-agreements and remain so. `OPAQUE` carries a class name plus a field-derived
representation, so it is an observation.

## 9.3 D-11 / D-12 — rows are not measurements

`Result` gained `measurementCount()` (`AGREE + DIFFER`), `measurements()`, `isClean()` and
`requireMeasurements(int)`; the report gained `# rows.measured`; `DiffReportWriter.writeAll` refuses a
report with no measurements, where it previously counted rows and accepted a 75-row all-`VOID` file
whose own header read `# rows.agreement 72`. `disagreements().isEmpty()` is documented on both the
class and the accessor as *not* a pass predicate.

## 9.4 D-13 — a wrong exception class now has an aggregate

`Result.throwClassMismatchCount()` and `# rows.throwClassMismatch`. A port that fails on the right
rows with the wrong exception type used to leave every aggregate bit-identical to a correct port's.

## 9.5 The invariant is a family

`UnwrittenPortInvariantTest` is a `@ParameterizedTest` over seven subjects — throws, Java `null`,
empty body (`voidValue()`), `nullValue()`, fixed constant, echoes its receiver, throws `Error` — and
asserts per subject that no operation was scored agreement on **every row the harness could drive**,
plus zero total agreement for the five subjects that produce no values at all. The two
value-producing subjects genuinely agree on some rows, so zero would be a false assertion for them;
they carry a written, reviewed allowlist of operations they may fully agree with.

That allowlist has exactly two entries, both for the receiver-echoing subject, and both record a real
limit of the instrument: `IntegerValue.value()` is declared `public int value()` and
`RealValue.value()` is `public double value()`, and the canonical form of a raw `int` and of an
`IntegerValue` is the same string, so the harness cannot tell "returned the value" from "returned the
receiver". **A port that returns the wrong Java type with the right numeric content would be scored
`AGREE` on these operations.** The limit is now pinned: a third operation becoming fully agreeable to
a do-nothing echo requires someone to write down why.

## 9.6 Counts, determinism, scope

```
surefire  use-core 45 -> 61   use-gui 1 -> 1
failsafe  use-core  1 ->  1   use-gui 129 -> 129      total 176 -> 192, BUILD SUCCESS
```
The +16 is entirely `UnwrittenPortInvariantTest` (2 -> 8) and `DifferentialHarnessRegressionTest`
(16 -> 26). No upstream test changed count.

Both goldens were refreshed deliberately and the whole diff is two header lines per file
(`# rows.measured`, `# rows.throwClassMismatch`); no data row changed. Two full runs from
`mvn -q clean` produced byte-identical reports (`cmp`), byte-identical to the refreshed goldens, and
identical invariant output. `git diff --name-status 30d480db..HEAD -- '*/src/main/*'` is empty.

## 9.7 Still open, and the standing lesson

- **`AcceptedThrowPairs` provenance (D-14)**: the report still records no `# accepted.*` header, so a
  run with a non-empty allowlist that adjudicated nothing is byte-indistinguishable from a run with
  `none()`. `describe()` and `DifferentialSweep.acceptedThrowPairs()` are still called from nowhere.
- **Primitive vs boxed results** (§9.5): recorded and pinned, not fixed.
- **Coverage (D-9)**: still 8 marshallable receivers; `SBooleanValue` and collection receivers are
  out of reach. Widening the corpora widens what the invariant covers, which is the point.
- **Documentation drift**: §7 and §8 above, and the `audit-*` files, still discuss
  `AGREE_THROWN`/`DIFFER_THROWN` as live verdicts. They are historical narrative; the vocabulary in
  the code is `AGREE, ACCEPTED_THROW, DIFFER, BOTH_THREW, MIXED, UNMEASURABLE, UNSUPPORTED,
  HARNESS_ERROR`.

§8.4 asked that a stage report, alongside any fidelity figure, how many rows were *measurements*, and
noted the harness could not answer. It can now: `Result.measurementCount()`, `# rows.measured`, and a
writer that refuses a report which answers zero.
