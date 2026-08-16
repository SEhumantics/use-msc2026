# S1 — The differential harness

Branch `port-uncertainty-2`. All commands run in `~/msc-4/use-msc2026` (Java 21.0.11, Maven 3.9.16).

S1 precedes any port code. Its only deliverable is the measuring instrument: a test-scoped harness
that drives the historical `URealValue` / `UIntegerValue` / `UBooleanValue` / `UStringValue` out of
the 2018 jars inside an isolated class loader, so historical and ported classes with the same
fully-qualified name can coexist in one JVM.

**Nothing under `use-core/src/main`, `use-gui` or `use-assembly` was touched. No existing test file
was modified. `module-info.java` was NOT edited.**

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
