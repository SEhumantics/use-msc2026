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
>
> ---
>
> ## → **FINAL BANNER (2026-08-17, after round 5). Read §10. It supersedes every banner above.**
>
> **S1's verdict is `SOUND_WITH_DOCUMENTED_LIMITS`.** D1, D2, D-10 and D-15 are closed and pinned by
> executing tests; round 5 planted eleven infidelities on a perfect port and measured which the
> instrument can see. **§10 is the consolidated record for all five rounds** — the verdict, the
> story, the single open-defect register and the id re-keying map. §1–§9 are the historical
> narrative, left as written; **§11 and §12 are appendices** (the D-15 fix, and the round-4 snapshot
> that used to stand at §10). All of them are superseded by §10 wherever they disagree.
>
> **The one-paragraph answer to "can I trust a differential number?" is §10.1**, and the short
> version for a human deciding whether S3 may start is
> [`foundation-verdict.md`](foundation-verdict.md). The rules a stage must follow when it gates on a
> sweep are in [`harness-contract.md`](harness-contract.md).

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

> **Correction (2026-08-17, round 4 — defect D-24).** The header block and the line counts pasted
> immediately above are the ones the writer emitted **before** commit `93e038ac`, and this section
> invites the reader to check them against the committed goldens, where they no longer match. The
> goldens are now 798 and 799 lines, and the header carries four more lines:
>
> ```
> $ wc -l docs/port2/differential/*.tsv
>   798 docs/port2/differential/s1-smoke-ureal-add.tsv
>   799 docs/port2/differential/s1-smoke-ureal-minus-faulty.tsv
> $ head -14 docs/port2/differential/s1-smoke-ureal-add.tsv
> # harness	differential-sweep/1
> # seed	20260817
> # reference	historical
> # subject	stub-faithful
> # sha256.use.jar	80ac8ae433b8345677472019991356950f094f4a104cfbce1f75783a7308788d
> # sha256.atenearesearchgroup.uncertainty.jar	53b2a43feb0a0a39844a60278dd80a7d4b975ef324fb05c6db28831e835e59d0
> # operations	URealValue.add(value)
> # rows	784
> # rows.measured	784
> # rows.agreement	784
> # rows.disagreement	0
> # rows.throwClassMismatch	0
> # verdict.AGREE	784
> index	operation	inputs	historical	ported	verdict	note
> ```
>
> The data rows are unchanged; only header lines were added. **`# rows.measured` is the line that
> matters** — see §10 and [`harness-contract.md`](harness-contract.md). The prose above is left as
> written because it records what §5 asserted at the time.

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

---

# 10. THE CONSOLIDATED RECORD — eight rounds, one verdict, one register

> **This section is the authority for S1. It supersedes every banner and every other section of this
> file, including §11 and §12, which are now appendices kept for their pasted evidence.** §1–§9 are
> the harness as it was built and reviewed round by round; §11 is the D-15 fix as written; §12 is the
> round-4 snapshot that used to stand here and used to say `DEFECTIVE`. Where any of them disagrees
> with §10, §10 wins.
>
> The normative rules a stage follows are [`harness-contract.md`](harness-contract.md). The short
> answer for a human deciding whether S3 may start is [`foundation-verdict.md`](foundation-verdict.md).

**Sources.** Everything below is either measured in one of the review reports listed below, or in
§10.8 for round 7, or cited to `file:line` in the tree. Nothing here is re-derived from memory.

| Round | Reports |
|---|---|
| 0 (build) | §1–§6 of this file; `stage-00-baseline.md` |
| 1 | `stage-01-refutation-empirical.md`, `-fidelity.md`, `-isolation.md`, `audit-01-harness.md` |
| 2 | `stage-01-verification-post-fix.md`, `stage-01-static-review-post-fix.md` |
| 3 | `stage-01-verification-round3.md`, `stage-01-static-review-round3.md` |
| 4 | `stage-01-verification-round4.md`, `stage-01-static-review-round4.md` |
| 5 | `stage-01-verification-round5.md`, `stage-01-static-review-round5.md` |
| 6 | `stage-01-round6-fixes.md` (the porter's four fixes), `stage-01-verification-round6.md` (the independent refutation, `3de8203e`) |
| 7 | §10.8 of this file (the closure of D-43, D-44 and D-45; behaviour `4bb5b6fe`); `stage-01-verification-round7.md` (the independent refutation, **`DEFECTIVE`**, D-46…D-51) |
| 8 | §10.8's correction box and §10.9 of this file (the demotion; behaviour `066fe15c`); `stage-01-verification-round8.md` (the independent refutation, **`SOUND_WITH_DOCUMENTED_LIMITS`** with D-52…D-57, `c91277ff`); §10.10 |

---

## 10.1 CURRENT VERDICT — `SOUND_WITH_DOCUMENTED_LIMITS`, confirmed by round 8's refuter; rounds 6 and 7 were both refuted on the *same* addition, round 8 demoted it, and D-52 is what remains

Both round-5 reviewers reached the standing verdict independently, one by running Maven and planting
infidelities, one by reading the tree and recomputing the metric from the committed goldens.

**Round 6's own verdict is not the porter's.** The refuter owned Maven, re-measured detection power end
to end, reproduced the *before* state from scratch in a detached worktree at `90404528`, and returned
**`DEFECTIVE`**:

> **Confirmed:** D-34, D-35 (including the failing-on-the-old-tree experiment), D-36 at the scope
> claimed, and D-18's blind spot genuinely closed — three byte-identical *before* tallies at
> `90404528` (identity, boxed, factory-typed: **0 `DIFFER`, 74 stage passes** each). The perfect-port
> **control is intact** — 17 199 measured, 17 199 agreed,
> `{AGREE=17199, BOTH_THREW=910, HARNESS_ERROR=883, UNMEASURABLE=91}`, 0 `DIFFER`, 0 `MIXED`, 0
> diverging operations, 74 of 285 stage passes — and **no false green** survived five constructions.
> Acceptance re-run: `BUILD SUCCESS`, **77 surefire + 130 failsafe = 207 methods, 0 failures**, `src/main`
> diff empty, tree clean, goldens byte-identical over two runs, baseline at `90404528` re-measured as
> **202** so the +5 is accounted for method by method.
>
> **Defective:** the Java type is **observed** on the reference side (`fromHistorical` derives it from
> `result.getClass().getName()` on every branch) and merely **declared** on the ported side, where
> `UValue.asJavaType(String)` (`UValue.java:235`) taking an arbitrary string is the only source of the
> token. A **content-perfect** port whose adapter returns `UValue.<factory>(content)` — what
> `StubCandidate` does, and `Candidate.java:30` calls it "the only worked example its adapter has to
> copy" — measures `rows 19 083, agreed 13 754, DIFFER 3 445, operations diverging 182 of 285, stage
> passes 74 → 45; lost 29`: **the same four numbers round 6 publishes as its detection power.** And a
> genuinely wrong-class port plus `.asJavaType(v.javaType())` goes `DIFFER 3 445 → 0`. Canonical
> **D-43**, MAJOR, open. Two new MINORs with it: **D-44** (the package-insensitivity rationale fails on
> the `OPAQUE` branch, 197 rows across 17 operations) and **D-45** ("an operation's declared return type
> is one class" is false for 84 of 285 operations).

**Not a request to revert the D-18 fix**, and this record does not treat it as one: the semantics are
right, the blind spot was real, the control is intact. What was defective is the **attribution** of the
figure and the **absence of the adapter obligation** from `Candidate`'s Javadoc, from `StubCandidate`
and from `harness-contract.md` §7.

> **Round 7 (`4bb5b6fe`) closed D-43, D-44 and D-45 — and the porter of round 7 does not sign off on
> his own work.** The four bullets the refuter specified are done, and one of them is done more
> strongly than asked: rather than only *publishing* an observing route, the one-argument
> `UValue.asJavaType(String)` is **deleted**, so the only way to state a class is
> `declaredJavaType(name, why)` with a non-blank reason, and `UValue.typeProvenance()` is carried into
> the note of every type-mismatch row. Measured in one run over the same 285 operations / 19 083 rows:
> the control still `DIFFER 0 / MIXED 0 / 0 diverging operations / 74 stage passes`; a content-perfect
> port with a **factory-typed** adapter `DIFFER 3 445 / 182 ops / 45 passes`, of which **3 445 rows now
> say "the subject's class was ASSUMED, not observed"**; **the same port with an observing adapter
> `DIFFER 0 / 74 passes` and a verdict tally identical to the control's** — the defect closed; and the
> planted **wrong-class** port still `DIFFER 3 445 / 182 ops / 45 passes`, **0** of whose rows say
> ASSUMED — D-18 not regressed. Acceptance: `BUILD SUCCESS`, **79 surefire + 130 failsafe = 209
> methods, 0 failures**, two byte-identical runs, goldens unchanged (`sha256` equal to the round-6
> hashes; no refresh), `src/main` diff empty. Evidence: §10.8. Still open: **H13–H16 and H18**; **H20
> is answered**.

> **Round 7 was refuted, and round 8's answer is a demotion rather than a fourth attempt at the same
> check.** The refuter of round 7 ([`stage-01-verification-round7.md`](stage-01-verification-round7.md))
> confirmed the control, D-18, every probe and half (a) of D-43, and returned **`DEFECTIVE`** on half (b):
> `declaredJavaType(referenceToken, "x")` took the **same planted wrong-class port** to a sweep
> **byte-identical to the perfect-port control** — verdict tally, stage-pass set and per-operation refusal
> map all equal — while the reason three documents said was "printed into the note of any row the
> declaration moved" appeared in **0** rows, because the type note fires only when the two class names
> differ and a laundering declaration makes them equal by construction. Two further constructions: the
> note certified a **fabricated** observation as fact (1 618 rows, D-47), and the 3 445 signature was
> shown to be an **absorbing state** — a defect-free port and a port carrying a real 401-row wrong-class
> infidelity produced 19 083 of 19 083 rows byte-identical, notes included, through a non-attributing
> adapter (D-48).
>
> **Round 8 (`066fe15c`) stopped patching the newest instance.** The root cause is that at S1 **there is
> no ported implementation to observe** — no `org.tzi.use.uml.ocl.value.URealValue` exists in
> `use-core/src/main`; writing it *is* stage S4 — so the ported token is unavoidably author-influenced and
> the check was premature. So: `declaredJavaType` is **deleted** along with `TypeProvenance.DECLARED`, a
> token is `OBSERVED` (off the object a side returned) or `ASSUMED` (the factory default) and an adapter
> author chooses neither; and a **type-only** difference is scored `AGREE` and counted in
> `Result.javaTypeMismatchCount()` / `# rows.javaTypeMismatch` / `# op.<key>.javaTypeMismatch` /
> `stageStatement()` — **content differences are untouched**. `harness-contract.md` §7 carries a **dated
> REQUIREMENT (2026-08-17)** that S4 routes through `observedFrom` and turns
> `javaTypeMismatchCount() == 0` into a gate clause once the real ported classes exist. Measured, same
> 285 operations / 19 083 rows: control `DIFFER 0 / MIXED 0 / 0 diverging / 74 passes / javaTypeMismatch
> 0`; content-perfect port + **factory-typed** adapter `DIFFER 0 / 0 diverging / **74** passes /
> javaTypeMismatch 3 445 / 3 445 notes say ASSUMED` — **the false-divergence mode and its 29 lost stage
> passes are gone**; planted **wrong-class** port `DIFFER 0 / 74 passes / javaTypeMismatch 3 445 / 0 notes
> say ASSUMED`; the same content-perfect port + **observing** adapter `0 / 74 / 0`. Every content probe
> P1–P11 unchanged, blind-spot set still exactly `{P11 / URealValue.round()}`. Acceptance:
> `BUILD SUCCESS`, **79 surefire + 130 failsafe = 209 methods, 0 failures**, twice, evidence blocks
> byte-identical between runs; goldens refreshed **deliberately** — two header lines per file, both `0`,
> no data row moved. Evidence: §10.9.

> **Round 8 WAS independently refuted, and the verdict is `SOUND_WITH_DOCUMENTED_LIMITS`**
> ([`stage-01-verification-round8.md`](stage-01-verification-round8.md), `c91277ff`). The refuter owned
> Maven, reproduced the control **from its own sweep rig** as well as from the suite — same 19 083 rows,
> same 17 199 measured, same tally, same 74 — and confirmed: the control **intact and byte-identical to
> round 7's** (`md5 c724bd19dbed9071ffc8762675584107`, three independent extractions); **every content
> probe P1–P11 unchanged** in `DIFFER`, `MIXED`, detected-operation set and stage passes, blind-spot set
> still the single `P11 / URealValue.round()` entry; the **false-divergence mode gone** (P13 reaches the
> control's exact stage-pass set, P14 is row-for-row the reference); **the demotion swallowed no content
> difference** — 468 → 468 in one construction and `DIFFER` 0 → **1 831 across 143 operations** with three
> stage passes lost in the sharper one, where the content defect sits on the very rows the type difference
> lives on; the count accurate against **three independent recounts**; and **no false green** in five of
> its own constructions plus the suite's eleven. It closes D-43 half (b), D-47 and D-51 independently.
>
> **What it found: D-52, MAJOR, open — the escape hatch is not gone, it moved from a `String` parameter to
> an `Object` parameter.** `observedFrom(Object)` reads `returned.getClass().getName()`, so an author who
> chooses the object has chosen the token. One port with a **real** 401-row / 9-operation wrong-class
> defect, two adapters differing by **one line**: A observes the object its port returned and publishes
> `javaTypeMismatch 401` across nine named operations with 401 rows of disclosure; B observes an **empty
> stand-in class** of the name the reference used and publishes **0** in every figure, a verdict tally
> **byte-identical to the perfect-port control**, and **0 rows carrying any type clause** — with provenance
> reported as `OBSERVED`, which is worse than round 7's laundering, whose provenance at least named the act.
> Nineteen empty stand-ins erase the whole 3 445-row dimension. **Inert at S1** (74 stage passes either
> way), so this is not a false green and not a `DEFECTIVE` verdict — the target is
> `harness-contract.md` §7's dated obligation, whose soundness argument assumed an attributability nothing
> enforced, and whose reviewer check ("state the attribution route") does not separate an honest adapter
> from a laundering one. **Fixed in documentation by mandating the SHAPE**: the observed object must be the
> invocation's own return value, as `PortedInfidelityDetectionPowerTest.observeWhatThePortReturned`
> (`:1116-1126`) already demonstrates — `harness-contract.md` §7 and the new **§8 checklist**. Five MINORs
> with it: **D-53** (the "no agreement figure without the count" claim is an overclaim), **D-54** (the count
> is not monotone in wrongness: 3 445/182 → 1 883/42), **D-55** and **D-56** (two latent holes the demotion
> created, both measured unreachable in today's corpus and inheriting D-30), **D-57** (the demotion's cost
> at *gate* level — 29 passes for a wrong-class port, 4 for a receiver-echoing subject — is recorded
> nowhere). Acceptance re-run: `BUILD SUCCESS`, **79 + 130 = 209 methods, 0 failures**, two runs
> byte-identical (`md5 919997f36959cf8cc6f8af4a64030ecd` over the stripped output), `src/main` diff empty,
> tree clean. Evidence: §10.10.

> **Can S4–S7 rely on this harness to detect a real infidelity in a ported U-type?**
>
> **Yes — for a defect the corpora reach, on an operation the harness can name, and provided the
> stage quotes numbers rather than a boolean.** Round 5 planted eleven infidelities on top of a
> second, independently loaded `HistoricalOracle` playing a perfect port, and round 6 added a twelfth.
> Nine of the twelve diverged; eight of those on **every** operation they touched, each costing the
> affected operations their stage pass. **The twelfth — the wrong-Java-class probe — is the one that did
> not survive scrutiny, and it is now reported in its own dimension rather than as a divergence.** It
> measured what the *adapter declared* rather than what the port returned (D-43); round 7 made the
> declaration cost a written reason and that was refuted too; round 8 removed the declaration API and
> demoted the finding to `javaTypeMismatchCount()`, where the port defect and the adapter defect still
> produce the same **3 445** and are told apart only by the row note. **So the eleven content probes are
> the detection power; the type probe is a published measurement whose attribution waits on S4.**
> `Math.hypot(ua,ub)` was separated from `sqrt(ua*ua+ub*ub)` at **one ULP** —
> `UINTEGER(696,0.3144000993956586)` against `UINTEGER(696,0.31440009939565855)` — because
> `UValue.canonical()` compares `Double.toString` exactly. Two deliberate concealment attacks
> destroyed the divergence and **bought no stage pass**.
>
> **No — outside that region, and the boundary is not published anywhere in the instrument.** One
> planted defect was invisible: the same wrong arithmetic confined to receiver value `42.0`, which no
> corpus contains. Its tally is byte-identical to a perfect port's on all 19 083 rows, all four
> affected operations reach a full stage pass, and the published statement reads
> `576 rows, 576 measured, 576 agreed, 0 disagreed, 164 distinct reference value(s) [DISCRIMINATING]`
> for a port that computes the wrong answer (D-30). Separately, **33 public operations —
> `equals(Object)` and `compareTo(Object)` on all eight receivers among them — cannot be named as a
> `UOp` at all** and appear in no report, not even as `UNSUPPORTED`.

**What that means for a reader of an S4–S7 number, in one line each:**

* A `DIFFER` / `MIXED` / `BOTH_THREW` / `throwClassMismatch` count is **trustworthy**, and always was.
  Round 6 opened one exception and round 8 closed it by moving the population out of `DIFFER`: a type-only
  difference is now an `AGREE` row counted in `javaTypeMismatch`, so no `DIFFER` count contains an
  unattributable row.
* A **`javaTypeMismatch` count is a measurement, not an attribution** (D-43). Non-zero means the two sides
  named different classes for identical content. Whether that is the port or the adapter is in the row
  note's provenance clause and nowhere else: an adapter that does not attribute produces the same **3 445**
  whether its port is perfect or carries a real wrong-class infidelity. From S4 it becomes a gate clause —
  `harness-contract.md` §7, dated requirement.
* **An `AGREE` row may be an agreement on the payload alone**, and the only figure that says so is
  `javaTypeMismatch`. Quote it beside every agreement figure; `stageStatement()` prints it unconditionally
  so that no one has to remember.
* An `AGREE` count is trustworthy **exactly to the extent that `distinctReferenceValues() >= 2`** for
  that operation — now computed, published per operation and enforced by the gate.
* A **stage pass is not a fidelity certificate.** It certifies "no divergence over the inputs we
  tried", and it is not even satisfiable by fidelity on 92 of 285 operations (D-29).
* No aggregate over the file is a claim about any operation (D-21).

---

## 10.2 The story in order

| # | State | What was found | Closed by |
|---|---|---|---|
| 0 | Harness built (§1–§6), believed sound. | Isolation was the load-bearing finding: a platform-parented `URLClassLoader` is **not** isolated in this reactor (§3). | `IsolatedJarClassLoader`, 9 isolation tests. |
| 1 | **DEFECTIVE — D1** | The harness scored **its own** marshalling failures as agreement: rows where neither side entered the operation came out `AGREE`. | `cf9d2f45` — `HARNESS_ERROR`, a distinct non-agreement (fixes F1–F11). |
| 2 | **DEFECTIVE — D2** | Two throws with matching class names were `AGREE_THROWN`, messages discarded. Against a subject whose every body throws: **21 816 of 471 471 rows green** over 27 operations. | `e8b73e48` — throw-agreement **deleted**, not tightened. Both outcomes are `BOTH_THREW`; the note keeps both classes and both messages. |
| 3 | **DEFECTIVE — D-10** | `VOID` vs `VOID` scored `AGREE`. Against an empty-body subject: **444 rows green — every driven row of every void operation**, e.g. `URealValue.setTypeToRuntimeType()` at 144/144. | `93e038ac` — `UNMEASURABLE` (neither an agreement nor a measurement), raised only when *neither* side observes; plus rows ≠ measurements. |
| 4 | **DEFECTIVE — D-15** | **No scoring bug at all.** Two real values, correctly compared, correctly equal, over an operation whose codomain is a single point. 120 of 285 operations (159 after the corpus widening); a subject of hardcoded literals is fully agreed on all of them, `isClean() == true`, report headed `# rows.disagreement 0`. | `0a93ad4f` — `distinctReferenceValues()` computed, published per operation, and **gated**: `requireStagePass` refuses below 2 without a value-keyed written sign-off. Re-measured in round 5: **119 clean-but-degenerate sweeps, 119 refused, 0 stage passes.** |
| 5 | **`SOUND_WITH_DOCUMENTED_LIMITS`** | The first direct measurement of **detection power** (`f438a365`), and no new scoring defect. What it found instead: the gate is not satisfiable by fidelity (D-29), detection is bounded by an input domain nobody measures (D-30), and three of the four MAJORs are in the **record** rather than in the instrument (D-33, D-34, D-35). | D-33 closed by this commit; D-29, D-30, D-34, D-35 open — see §10.4. |
| 6 | **the four remaining defects closed** (`d13d4858`) | The three round-5 preconditions, plus the one scoring claim still on the list: **right content in the wrong Java class was `AGREE` on 193 of 285 operations**, for a port whose entire subject is four new value classes. The harness had been comparing the payload and calling it the value. Also: a report could understate the sign-offs its own verdict rested on (D-34), a standing invariant had stopped asserting half of what it asserted the commit before (D-35), and the tree's own S1 acceptance test gated on a predicate the contract forbids a stage from using (D-36). | D-18, D-34, D-35, D-36 all **closed**; evidence in [`stage-01-round6-fixes.md`](stage-01-round6-fixes.md), all four independently confirmed in [`stage-01-verification-round6.md`](stage-01-verification-round6.md) §2–§3. |
| 6R | **DEFECTIVE — D-43** (refutation, `3de8203e`) | All four closures confirmed, control intact, no false green in five constructions — and then the round's own fix turned against the number it produced: the ported side's Java type is **declared by the adapter**, never observed by the harness, so a **content-perfect** port with the documented factory-typed adapter reproduces round 6's headline figure exactly (3 445 `DIFFER`, 182 of 285, 74 → 45, lost 29), and one line of adapter code takes a genuinely wrong-class port to 0 `DIFFER`. The obligation appears in no Javadoc and in no contract section. Two MINORs alongside: **D-44**, **D-45**. | **Closed in round 7** (`4bb5b6fe`). D-43's four bullets were done, and the mechanism was taken further than the bullets asked: the one-argument declaring route is deleted and the token's provenance is carried into the evidence. Open MAJORs **4 → 5 → 4**; no scoring defect at any point. |
| 7 | **the refutation's MAJOR and both MINORs closed** (`4bb5b6fe`) | Nothing new found; this round is the closure of D-43, D-44 and D-45. What it changes is that the *ported* side's Java class is now **measured** rather than **believed**: `UValue.observedFrom(Object)` reads it off the object a side returned and is what `fromHistorical` itself calls, the one-argument `asJavaType(String)` is deleted, the only stating route demands a written reason, and `typeProvenance()` reaches the note of every type-mismatch row — never `canonical()`, never a verdict. Both readings of the 3 445 are pinned as adjacent tests, so the ambiguity that made round 6's headline unattributable cannot be quoted one-sided again. | **D-43, D-44, D-45 closed**; D-18 explicitly not regressed (planted wrong-class port still 3 445 `DIFFER` on 182 operations). Evidence: §10.8. |
| 7R | **DEFECTIVE — D-46** (refutation, `f693b05c`) | Control, D-18 and every probe confirmed, and half (a) of D-43 reproduced — then half (b) measured still open: `declaredJavaType(referenceToken, "x")` took the same planted wrong-class port to a sweep **byte-identical to the perfect-port control** while the reason three documents said was printed reached **0 rows**. Also: the note **certified a fabricated observation** as fact on 1 618 rows (D-47), the 3 445 was shown to be an **absorbing state** (D-48), the report distinguished a port defect from an adapter defect by one header line (D-49), and two documentation MINORs (D-50, D-51). | **D-46, D-47, D-51 closed in round 8** (`066fe15c`); **D-48 reclassified** as a named limit with a dated requirement; **D-49 partly closed** (aggregate yes, provenance no); **D-50 open**. |
| 8 | **the check DEMOTED, not patched a third time** (`066fe15c`) | Nothing new found; this round removes the *class* of defect rounds 6 and 7 each patched an instance of. Root cause: at S1 **there is no ported implementation to observe**, so the ported token was author-influenced by construction. `declaredJavaType`, `TypeProvenance.DECLARED` and `typeDeclarationReason()` deleted; a token is `OBSERVED` or `ASSUMED` and an author chooses neither; a **type-only** difference is `AGREE` counted in `javaTypeMismatchCount()`. **Content differences untouched, every content probe unchanged.** The false-divergence mode and its 29 lost stage passes are gone. | **D-43 (both halves), D-46, D-47, D-51 closed**; D-48 reclassified; D-49 partly closed; the promotion of the check is a **dated REQUIREMENT on S4** in `harness-contract.md` §7. Evidence: §10.9. |
| 8R | **`SOUND_WITH_DOCUMENTED_LIMITS` — D-52** (refutation, `c91277ff`) | Control intact, byte-identical to round 7's and reproduced from the refuter's own rig; every content probe unchanged; the demotion swallows no content difference (`DIFFER` 0 → 1 831 across 143 operations when a content defect is put on the same rows); the count accurate against three independent recounts; no false green in five constructions. **Found:** the escape hatch moved from a `String` parameter to an `Object` parameter — `observedFrom(anEmptyStandInClass)` takes a **real** 401-row / 9-operation wrong-class port to 0 in every figure with the sweep byte-identical to the control and the disclosure in 0 rows. **Inert at S1**; the target is §7's dated obligation. Five MINORs: D-53…D-57. | **D-52 open (MAJOR)** — closed for S1 by wording: `harness-contract.md` §7 now mandates the invocation-seam **shape** and §8 is the S4 checklist. D-53…D-57 open. Evidence: §10.10. |

**The shape (rounds 1–6).** Rounds 1–3 were one bug class: *the absence of a measurement scored as agreement*.
Round 4 was not — it needed no bug in `DifferentialSweep`, and every safeguard from rounds 1–3 was
*right* to let it through. Round 5 was not either: the instrument survived every attack constructed
against its scoring, and the softest target had become the documents reporting it. Round 6 is a
fourth shape again: not an absence scored as agreement and not a degenerate codomain, but a
**presence compared incompletely** — two real values, both observed, correctly equal in payload and
carried by different Java classes, one of which was wrong. The round-4 register had called D-18 "out
of reach of a mutation experiment"; it was not, and the experiment is four lines.

Round 6's refutation is a fifth shape, and the first that is neither an absence nor a comparison:
**a measurement whose two readings are numerically identical**. Nothing in the scorer is wrong; the
same 3 445 `DIFFER` rows are produced by a defective port and by a faithful one whose adapter
declared the type its factory chose, and the harness has no way to tell them apart because it never
observes the ported side's class (D-43).

**Rounds 7R, 8 and 8R are all the same shape as each other, and it is the sixth: a disclosure that fires
only where the instrument already noticed.** Round 7 required a written reason for a stated token; the
reason reached 0 rows precisely on the sweep where the statement *erased* a finding. Round 8 removed the
statement — and round 8's refutation found that the object handed to `observedFrom` is the statement, one
parameter type further out, with the same 0 rows of disclosure (D-52). The escape hatch was never the
`String`; it was the fact that the author chooses what the instrument reads. That closes by mandating the
**shape** of the adapter, and only S4 can supply the object that makes the shape meaningful.

---

## 10.3 What is measured and is not in doubt

Recorded so that "documented limits" is not read as "unreliable". Every item was reproduced by an
independent reviewer, in most cases twice.

**Detection power (round 5, `PortedInfidelityDetectionPowerTest`; every row below re-measured
independently in round 6).** Control: two independently loaded `HistoricalOracle` instances over 285
operations and 19 083 stage-shaped rows — 17 199 measured, 17 199 agreed,
`{AGREE=17199, BOTH_THREW=910, HARNESS_ERROR=883, UNMEASURABLE=91}`, **0 `DIFFER`, 0 `MIXED`, 0
diverging operations, 74 of 285 stage passes**. Every divergence below is therefore attributable to the
planted defect alone. Absolute stage passes, from the round-6 run: control 74; P1 74, P2 70, P3 70,
P4 70, P5 67, P6 72, P7 71, P8 70, P9 70, P10 74, P11 71, P12 45.

> **Two corrections to this subsection, from rounds 7 and 8; the figures above are otherwise unmoved and
> were re-measured unchanged in round 8 by an independent refuter.** (1) **P12's 45 is a pre-round-8
> number.** Since the demotion a type-only difference is `AGREE` counted in `javaTypeMismatch`, so P12
> measures `DIFFER 0`, 0 diverging operations, **74** stage passes and 3 445 `javaTypeMismatch` rows across
> 182 of 285 operations (§10.9.3). Every operation that passes now with `javaTypeMismatch > 0` is a pass the
> demotion created — **29** of them, named in `stage-01-verification-round8.md` §4.5 (**D-57**). (2) The
> eleven content probes P1–P11 are the detection power; **P12 is a published measurement whose attribution
> waits on S4** (D-43, and D-52 for the shape that makes it attributable).

| probe | planted infidelity | detected on | rows | stage passes lost |
|---|---|---|---|---|
| **P0 / control** | **identity — a perfect port** | **0** | **0** | **0** |
| P1 | 0-based port of the 1-based string index | 3 of 3 | 138 | 0 — see D-29 |
| P2 | uncertainties combined with `+` instead of `sqrt(ua²+ub²)` | 4 of 4 | 468 | 4 |
| P3 | `Math.hypot(ua,ub)` for `sqrt(ua*ua+ub*ub)` | 4 of 4 | 24 | 4 |
| P4 | `<=` for `<`, `>=` for `>` | 6 of 6 | 280 | 4 |
| P5 | results rounded to 10 decimal places | 7 of 7 | 428 | 7 |
| P6 | `uEquals` ignores the uncertainty component | 4 of 4 | 1 119 | 2 |
| P7 | divide-by-zero returns `Undefined` where historical throws | 6 of 6 | 167 | 3 |
| P8 | P2's defect hidden behind `HarnessMarshallingException` (D-17) | 0 | 0 | **4** |
| P9 | P2's defect hidden behind `supports() == false` | 0 | 0 | **4** |
| P10 | P2's defect only at receiver value `42.0` | **0** | **0** | **0** |
| P11 | `-0.0` collapsed to `0.0` | 3 of 4 | 59 | 3 |
| P12 *(round 6)* | every raw result boxed into its `Value` class — right content, wrong Java type | **182 of 285** | **3 445** | **29** |

The brief asked for detection "at the 10th decimal". P3 resolves **seven significant digits finer**.
P8 and P9 are the two concealment attacks: both destroy the divergence, neither buys a pass. P10 is
D-30 and is the reason this verdict is qualified.

**P12 is round 6's addition and it was the largest single blind spot on the list.** Before the D-18
fix it detected **0** operations and **0** rows and cost **0** stage passes, with a verdict tally
byte-identical to a perfect port's; the row above is the same probe after. The refuter reproduced that
*before* state independently at `90404528` — identity, boxed and factory-typed subjects gave three
byte-identical tallies, 0 `DIFFER`, 74 stage passes. The control was re-run alongside and is unchanged,
the corpus-sensitivity table moved on no probe, and the exact set of planted defects the instrument
cannot see is still `{P11-negative-zero-collapse / URealValue.round()  [STAGE PASS]}`.
Evidence: [`stage-01-round6-fixes.md`](stage-01-round6-fixes.md),
[`stage-01-verification-round6.md`](stage-01-verification-round6.md) §2.

> **P12's row must not be quoted without D-43.** The refuter measured a **content-perfect** port whose
> adapter is factory-typed the way `StubCandidate` is and got `DIFFER 3 445, 182 of 285 operations,
> 74 → 45, lost 29` — identical to P12's row, from a port with no defect in it. The 29 lost include
> `URealValue.value()`, `URealValue.uncertainty()`, `UIntegerValue.value()` and
> `UIntegerValue.uncertainty()`. Conversely a genuinely wrong-class port plus one line,
> `.asJavaType(v.javaType())`, goes `DIFFER 3 445 → 0`. P12 measured **what the adapter declared**, not
> what the port returned. **Since round 8 (§10.9) neither reading is a divergence:** both measure
> `DIFFER 0`, 74 passes and `javaTypeMismatch 3 445`, the equality is asserted by a test, and the
> attribution waits on S4's adapter having the invocation-seam shape (D-43, D-52).

**The rest, unchanged and re-measured in round 5:**

* **Isolation.** Historical classes come from the vendored jars through a parent-last loader; the
  harness refuses to run if `org.tzi.use.uml.ocl.value.Value` resolves to the application loader.
  Two independent oracles reproduced each other on all 19 083 rows — a stronger check than the
  isolation test itself performs.
* **D1, D2, D-10 are closed and pinned**, re-run over all seven non-port subjects: `a-throws`
  `{BOTH_THREW=39880, HARNESS_ERROR=618462, MIXED=67996}`, measured 0, agreement 0;
  `b-returns-java-null` `{HARNESS_ERROR=726338}`, agreement 0; `c-empty-body` 67 268 `DIFFER` and 728
  `UNMEASURABLE`, agreement 0; `g` still **aborts loudly** on `Error` with 0 rows left behind. The
  historical `UIntegerValue.power(value)` produces 225 `BOTH_THREW` rows against a *perfect* port
  with identical class **and** identical message and is still scored a non-agreement.
* **The discrimination metric is correct**, checked by two routes that read no `DiffRow`: recomputed
  from the committed golden data rows with `awk` (258 and 389, both exact, and the ported column
  gives 391 on the faulty golden, proving the number comes from the reference side), and recomputed
  by driving the oracle directly over `URealValue.neg()`'s 24-receiver domain (same 23-element set,
  enumeration printed so a reader can count it).
* **The gate is real and bidirectional.** One implementation (`stageGateFailures`,
  `DifferentialSweep.java:604`); a floor of zero throws; the sign-off is an exact two-part key with a
  mandatory non-blank rationale that reaches `stageStatement()` and the report header; a wrong value
  or a wrong operation does not match; and the control in the same test — a faithful port passing
  `requireStagePass(100, none())` — fails if the gate degenerates into blanket refusal. No sign-off
  exists anywhere in the tree outside the tests that exercise the mechanism.
* **Corpus widening (D-19) worked.** Zero-measurement operations **61 → 11**; all 27 `BooleanValue.*`
  and all 25 `StringValue.*` operations now measure. Census after widening: **11 measured nothing /
  159 single-valued / 115 discriminating**, summing to 285.
* **Determinism, scope and acceptance.** Four full `mvn -q clean && mvn -B verify
  -Djava.awt.headless=true` runs in round 5, all `BUILD SUCCESS`; use-core surefire 67 → 71 (+4,
  exactly the new tests), failsafe 1, use-gui 1 and 129 unchanged; two consecutive runs byte-identical
  over the 557-line detection block and the 324-line invariant block; goldens reproduced without the
  refresh flag; `git diff --name-status 30d480db..HEAD -- '*/src/main/*'` **empty** in every round.
  **Round 6, re-run by the refuter and not by the porter:** `BUILD SUCCESS`, **77 surefire + 130
  failsafe = 207 methods, 0 failures** (use-core 76 + use-gui 1 surefire; use-core 1 + use-gui 129
  failsafe); baseline re-measured in a detached worktree at `90404528` as **202**, so the **+5** is
  accounted for method by method (3 regression, 2 detection-power); two smoke runs byte-identical by
  `sha256sum` and both goldens byte-identical to the committed files; `src/main` diff empty; tree clean.
  The golden refresh was audited claim by claim: header byte-identical in both files, 784 data rows
  changed each, `@`-stripped content identical, verdict multiset identical, 6 272 `@URealValue`
  occurrences.

---

## 10.4 The open-defect register — single and canonical

A bare `D-nn` anywhere in `docs/port2/` means **this table**. A report-local id must be cited with
its report (e.g. *static-review-round4 `D-17`*, *static-review-round5 `R5-2`*); §10.5 maps them all.

| Key | Sev | State | Defect |
|---|---|---|---|
| **D-29** | **MAJOR** | **open** | **The stage gate is not satisfiable by fidelity.** Clause 2 counts `BOTH_THREW`, `HARNESS_ERROR`, `UNSUPPORTED` and `UNMEASURABLE` as disagreements. A **perfect** port reaches `isStagePass(1, none())` on **74 of 285**: 119 refused by clause 3 (D-15, by design), **92 refused by clause 2** for rows a faithful port cannot avoid, and the only route out is **154** hand-authored `AcceptedThrowPairs` entries keyed on both messages verbatim. Two consequences: the incentive to fall back on the still-public `isClean()` (which says yes to 193 of 285 for the same port) is a **119-operation gap**; and on those 92 operations an infidelity leaves the pass bit **unchanged** — measured on 10 (defect, operation) pairs, the whole off-by-one probe among them, whose rows go from `{AGREE=37, BOTH_THREW=99}` to `{DIFFER=26, MIXED=26, BOTH_THREW=84}` while `requireStagePass` says `false` before and after. |
| **D-30** | **MAJOR** | **open — re-measured in round 6, unchanged: `{AGREE=17199, BOTH_THREW=910, HARNESS_ERROR=883, UNMEASURABLE=91}`, 0 `DIFFER`, 74 of 285 stage passes, i.e. identical to the perfect-port control including a full pass on `URealValue.add(value)`, the operation carrying the defect** | **Detection is bounded by the input corpus and no domain-coverage figure is computed, published or gated.** `distinctReferenceValues()` made the *codomain* an enforced quantity; its dual is measured nowhere. A real arithmetic defect at receiver `42.0` is stage-pass-identical to a perfect port, with `[DISCRIMINATING]` printed beside the number — true, and completely uninformative about the input never tried. Same in miniature at `URealValue.round()` for `-0.0 → 0.0`, which *is* caught on `floor()`, `neg()` and `mult(value)`. Not a bug in `DifferentialSweep`; it is the boundary of what a fidelity claim from this instrument means, and the instrument does not state it. |
| **D-18** | MAJOR | **CLOSED (round 6, `d13d4858`)** | **The primitive/boxed canonical collision was 193 of 285.** `UValue` now carries `javaType()` — the class a value was observed as — and `canonical()` renders its simple name, so `BOOLEAN(true)@Boolean` is not `BOOLEAN(true)@BooleanValue`; `fromHistorical` attributes every branch from `result.getClass().getName()`. It was **not** out of reach of a mutation experiment after all: the defect is expressible as a perfect port that round-trips every raw result through `toHistorical`/`fromHistorical`, so the payload is provably unchanged and only the class moves. Measured, 285 operations / 19 083 rows: **before** `{AGREE=17199, BOTH_THREW=910, HARNESS_ERROR=883, UNMEASURABLE=91}`, **0 `DIFFER`**, detected on **0 of 285**, stage passes **74 → 74**; **after** `{AGREE=13754, BOTH_THREW=910, DIFFER=3445, HARNESS_ERROR=883, UNMEASURABLE=91}`, **3 445 `DIFFER`**, detected on **182 of 285** (193 is the static return-type count, 182 the operations the corpora drive to a value), stage passes **74 → 45**. The perfect-port control still yields 0 `DIFFER` / 0 `MIXED` / 0 diverging operations, and **no operation answers with two different runtime classes (0 of 285, measured)** — that measurement holds, though the *reason* the test gives for it is false for 84 of 285 declared return types (**D-45**), so the premise is a corpus fact and inherits D-30. A `Kind` difference (`URealValue` vs `UIntegerValue`, `IntegerValue` vs `UIntegerValue`) was always caught and is now pinned. **The before/after was reproduced independently at `90404528` (three byte-identical tallies, 0 `DIFFER`, 74 stage passes), so the closure is confirmed — but the claim "the fix has no false-divergence mode" is WITHDRAWN: see D-43.** Evidence: `stage-01-round6-fixes.md` §1, `stage-01-verification-round6.md` §2.2. |
| **D-43** | MAJOR | **half (a) CLOSED (round 7, `4bb5b6fe`); half (b) CLOSED BY REMOVAL (round 8, `066fe15c`). The check is DEMOTED, and its promotion is a dated REQUIREMENT on S4** | **At S1 the ported side's Java type cannot be authentically observed, so a type-fidelity figure measured the adapter and not the port.** Found by round 6's refutation (`3de8203e`; that report's local `D-37`). `HistoricalOracle.fromHistorical` derived `javaType` from `result.getClass().getName()` on every branch and **there was no counterpart for the ported side**, where the public `UValue.asJavaType(String)`, taking an arbitrary string, was the only source of the token. **(a) False divergence through the documented path — CLOSED.** An adapter returning `UValue.<factory>(content)` is factory-typed as the `Value` class of its kind, wrong for **182 of 285** operations; measured on a **content-perfect** port, `rows 19 083, agreed 13 754, DIFFER 3 445, operations diverging 182 of 285, stage passes 74 -> 45; lost 29` — byte-identical to P12's published detection power, the 29 including `URealValue.value()`, `URealValue.uncertainty()`, `UIntegerValue.value()`, `UIntegerValue.uncertainty()`. Round 7 gave the ported side `UValue.observedFrom(Object)` (called by `fromHistorical` on all fourteen unwrapping branches), and the **same** port with an observing adapter measured `DIFFER 0 / 0 ops / 74 passes` with the control's exact verdict tally. That half was independently reproduced in round 7's refutation and is closed. **(b) One line erased the check — CLOSED IN ROUND 8, BY DELETING THE API, after round 7's fix was refuted.** Round 6: a wrong-class port plus `.asJavaType(v.javaType())` went `DIFFER 3 445 -> 0`. Round 7 deleted the one-argument form and required a written reason on `declaredJavaType(String, String)`, claiming in three documents that the reason "is printed into the note of any row the declaration moved". **Round 7's refuter measured that false:** `declaredJavaType(referenceToken, "x")` on the same planted wrong-class port produced a sweep **byte-identical to the perfect-port control** — verdict tally, stage-pass set and per-operation refusal map all equal — with the reason in **0 rows**, because the type note fires only when the two class names differ, which a laundering declaration makes false by construction. The disclosure fired when a declaration *created* a difference and was silent when it *erased* one. **Round 8's fix removes the class of defect rather than the instance.** The root cause is that at S1 **there is no ported implementation to observe** — no `org.tzi.use.uml.ocl.value.URealValue` exists in `use-core/src/main`; writing it *is* S4 — so every round invented a new way for the author to influence the token. So: **`declaredJavaType`, `TypeProvenance.DECLARED` and `typeDeclarationReason()` are deleted**; a token is `OBSERVED` (read off the object a side returned) or `ASSUMED` (the factory default), two states and an adapter author chooses neither; the only private setter is reachable from `observedFrom` alone; and a reflection test over `UValue`'s whole public surface pins that the only String-taking members are `uString`/`string`/`opaque`, whose String is **content** (`opaque`'s class name goes into `content()` too, so an untruthful `opaque` token is a content difference and stays a `DIFFER`). **And a type-only difference is no longer scored:** content identical + class different is `AGREE`, counted in `Result.javaTypeMismatchCount()` / `# rows.javaTypeMismatch` / `# op.<key>.javaTypeMismatch`, and printed unconditionally by `stageStatement()`. **Content differences are unaffected.** **Measured after the demotion, one run, four subjects over the same 285 operations / 19 083 rows:** control `DIFFER 0 / 0 divOps / 74 passes / javaTypeMismatch 0`; content-perfect + **factory-typed** adapter `DIFFER 0 / 0 divOps / **74** passes / javaTypeMismatch 3 445 / 3 445 notes say ASSUMED` (**was** `3 445 DIFFER / 182 ops / 45 passes` — the false-divergence mode and its 29 lost passes are gone); planted **wrong-class** port `DIFFER 0 / 74 passes / javaTypeMismatch 3 445 / 0 notes say ASSUMED`; the same content-perfect port + **observing** adapter `0 / 74 / 0`, tally identical to the control's. Both readings are pinned as adjacent tests (`aWrongJavaTypeWithRightContentIsCountedNotScored`, `aFactoryTypedAdapterCostsNoPassAndIsCountedNotScored`) so neither can be quoted alone, and the second cross-checks the harness's count against an independent recount of the same rows. **What is now a declared LIMIT rather than a closed defect, and the REQUIREMENT that closes it:** a non-attributing adapter produces the same **3 445** whether its port is perfect or carries a real wrong-class infidelity, so the *only* discriminator is the row note's provenance clause; a type-only infidelity therefore costs no stage pass at S1. `harness-contract.md` §7 carries the **dated obligation (2026-08-17)** that once real ported value classes exist in `use-core/src/main`, S4 routes its adapter through `observedFrom` and adds `javaTypeMismatchCount() == 0` as a gate clause. **Consequence recorded, not hidden:** `UnwrittenPortInvariantTest`'s receiver-echoing subject regains four discriminating fully-agreed operations (`BooleanValue.value()`, `BooleanValue.isTrue()`, `IntegerValue.value()`, `StringValue.value()`) plus `RealValue.value()` in the degenerate bucket — agreement 4 567 -> 4 951, `javaTypeMismatch` **384**, the +384 exactly the demoted rows — all five signed off with the reason, and every sign-off now has to **assert** that *all* of its agreement rows are java-type mismatches. **Round 8's refuter confirmed both halves closed and then found that the REQUIREMENT is not yet sound: `observedFrom` takes an `Object`, so the author still chooses the token by choosing the object (D-52) — the fix is to mandate the adapter's SHAPE, and it is written into `harness-contract.md` §7 and §8.** Evidence: §10.8 (round 7), §10.9 (round 8), §10.10 (round 8's refutation). |
| **D-46** | **CRITICAL** | **CLOSED (round 8, `066fe15c`) — folded into D-43 half (b), by deleting the mechanism** | Round 7's refutation, §5.2. `declaredJavaType(referenceToken, "x")` took a wrong-class port to a sweep byte-identical to the perfect-port control and the mandated reason reached **0 rows**, while `harness-contract.md` §7 (twice) and §5, `stage-01.md` §10.4's D-43 row and `foundation-verdict.md` all asserted it was printed. **Closed by removing `declaredJavaType` entirely** — there is no route left by which an adapter supplies a type token, so there is no laundering route to disclose; and the false claim is removed from all four places with a note that round 7 refuted it, rather than silently edited. See D-43. |
| **D-47** | MAJOR | **CLOSED (round 8, `066fe15c`) as an over-claim; the underlying uncheckability is now stated as a limit** | Round 7's refutation, §6. The note asserted "Both classes were OBSERVED from the objects the two sides returned, so this row is a statement about the two implementations" — 1 618 times, on a subject that fabricated the object it observed. `observedFrom` believes any object and the harness cannot check it. **The sentence is deleted.** The note now prints both provenances mechanically (`Provenance: reference OBSERVED, subject ASSUMED`) and, on an observed subject, says outright that "whether the object the subject observed is the one its implementation returned is not checkable by this harness". Asserted in `theTypeTokenIsObservedOrAssumedAndNoApiTakesOne`, which fails if the certification ever comes back. **The incentive is also gone:** fabricating an object was the *cheaper laundering route* only while a type-only difference was a `DIFFER`; since round 8 it buys nothing, because there is nothing to launder. |
| **D-48** | MAJOR | **RECLASSIFIED (round 8, `066fe15c`): the absorbing state is real, is no longer a false-divergence trap, and is now a NAMED LIMIT with a dated REQUIREMENT that closes it** | Round 7's refutation, §7. Through a non-attributing adapter, a port with **no defect** and a port carrying a **real 401-row / 9-operation** wrong-class infidelity produced 19 083 of 19 083 rows byte-identical, notes included: the 3 445 signature absorbed the real defect, and a reader following the note's hedge discarded 401 genuine findings. **What round 8 changes and does not change.** It does not make the two distinguishable — that is impossible while the adapter does not observe, and the demotion makes the equality explicit rather than hiding it: both now measure `javaTypeMismatch 3 445`, `DIFFER 0`, 74 stage passes. What it removes is the part that misled: neither is reported as a divergence, so no reader has to decide whether to discard 401 rows on the strength of a hedge, and no faithful port loses 29 stage passes to its adapter. What replaces it is `harness-contract.md` §7's dated REQUIREMENT, whose argument **is** this measurement: attribution is what makes the number mean anything, so S4 must observe and then gate on `javaTypeMismatchCount() == 0`, at which point the real defect measures its own 401/9 and the defect-free port measures 0. **Stated as a limit until then, in the contract, in the register and on `javaTypeMismatchCount()`'s own Javadoc.** |
| **D-49** | MAJOR | **PARTLY CLOSED (round 8, `066fe15c`); the residue is stated** | Round 7's refutation, §8. Full reports for a port defect and an adapter defect differed in exactly one header line — `# subject` — and neither golden carried a single provenance mention. **Closed for the aggregate:** the header now carries `# rows.javaTypeMismatch` and `# op.<key>.javaTypeMismatch`, so the population that was visible only in row prose has a number at file level *and* per operation (the D-21 shape), and `stageStatement()` prints it unconditionally. **Not closed, and honestly so:** the *provenance* — `OBSERVED` versus `ASSUMED` — still reaches only the row note, so two reports whose type-mismatch counts are equal are still distinguished only by reading rows. That is the same fact as D-48 and it is closed by the same requirement. **The goldens still contain 0 provenance mentions, and that is now a true statement rather than a hidden one:** S1's stub is `ASSUMED` with the *right* token, so `# rows.javaTypeMismatch 0` in both files, and there is no type-mismatch row to caption. |
| **D-52** | **MAJOR** | **open — inert at S1, and it must be settled before S4 leans on the type figure. Closed for S1 by WORDING in this commit** (`harness-contract.md` §7 mandates the shape and §8 is the S4 checklist); **the mechanism is S4's to build** | Round 8's refutation, §2. **The escape-hatch class is not gone; it moved from a `String` parameter to an `Object` parameter.** `UValue.observedFrom(Object)` reads `returned.getClass().getName()`, so **an author who chooses the object has chosen the token**, and the harness cannot know whether the object it was handed is the one the implementation produced. Measured with ONE port carrying a **real** wrong-class defect — content-perfect on every row, answering a raw boxed primitive where the historical answers an `org.tzi.use.uml.ocl.value.*` object, i.e. exactly the 401-row / 9-operation infidelity round 7 quoted — and TWO adapters differing by **one line**: **A** observes the object its port returned → `javaTypeMismatch 401` across the nine named operations, 401 rows carrying a type clause; **B** observes an **empty stand-in class** of the name the reference used → **0** in every published figure, a verdict tally **byte-identical to the perfect-port control**, **0** rows carrying any type clause, and `stageStatement()` reading `0 java-type mismatch(es)` where A's reads `2` on the same operation with the same content. Nineteen empty stand-in classes (18 nested + 4 JDK objects) erase the **whole** 3 445-row dimension, and the harness prints the class list itself in its own representation census; the naive four-line version ("observe the boxed primitive") already gets 3 445 → 401 with no census at all, so the gradient starts shallow. Provenance is reported as **`OBSERVED`** — worse than round 7's laundering, whose provenance at least named the act. Reflective routes exist too and are reported only because "there is no route" had been asserted: `setAccessible(true)` + `Field.set` on the private final `javaType` **succeeds** and leaves provenance `ASSUMED` (an `AGREE` row with an empty note); `VarHandle.set` is refused. **Not a `DEFECTIVE` verdict and not a false green:** the demotion makes the clause **inert at S1** (74 stage passes either way), and §5's D-43 row already said the token is "only as honest as the adapter's choice of object … which is not checkable". **What was stated nowhere and is load-bearing:** (1) the unchecked choice drives the count to **exactly 0** on all 182 operations with the sweep byte-identical to the control, not merely "may be optimistic"; (2) the note's own "not checkable by this harness" clause fires on **0 rows** of a laundered sweep — the identical disclosure-only-where-already-noticed pattern round 7 was marked `DEFECTIVE` for; (3) `harness-contract.md` §7's obligation therefore rested on a clause an adapter satisfies by laundering, and its reviewer check ("reject a figure from an adapter whose attribution route is not stated") does not separate A from B, since both route through `observedFrom` and both would state that truthfully. **FIX, and it is not a fourth API change:** mandate the SHAPE — the observed object must be the invocation's own return value, captured at one seam, as `PortedInfidelityDetectionPowerTest.observeWhatThePortReturned` (`:1116-1126`) already demonstrates — and say in §7 that `javaTypeMismatchCount() == 0` is a gate clause **only under that shape**, with the reviewer checking the adapter's shape rather than its prose. **Both sentences are written into `harness-contract.md` §7 and §8 by this commit; the adapter that satisfies them is S4's to write.** |
| **D-53** | MINOR | **open — corrected in the contract by this commit; the Javadoc sentence is still the old one** | Round 8's refutation, §4.1. `DifferentialSweep.java:758-760` ("there is deliberately no way to render an agreement figure from this class without the discrimination figure beside it, and since round 8 no way to render one without the `javaTypeMismatchCount()` beside it either") and its restatement in the round-8 record are **overclaims**: `agreementCount()` (`:520`) and `agreements()` (`:505`) are public and return the agreement population unaccompanied, and `isClean()` (`:591`) returns `true` on a sweep carrying two java-type mismatches. **The mechanism binds `stageStatement()` and `summary()`, which is enough and is the right mechanism** — the sentence should say so. Same shape as the pre-existing D-15 overclaim. `harness-contract.md` §4.5 now states the accurate form; the Javadoc is behaviour-adjacent and is left for the next behaviour commit. |
| **D-54** | MINOR | **open — stated in `harness-contract.md` §4.2 clause 6 and §5 by this commit** | Round 8's refutation, §4.2. `javaTypeMismatchCount()` (`:854`) is derived as "an `AGREE` row whose columns differ", so a row wrong in **both** dimensions is a `DIFFER` and leaves the count. **The number is therefore not monotone in wrongness:** adding a content defect to a wrong-class port takes it from **3 445 / 182 operations** to **1 883 / 42**. Nothing is hidden — `DIFFER` rises to 1 831 across 143 operations, three stage passes are lost, and the row notes still name the type mismatch on all 3 445 rows — so only the aggregate under-reports. **The header figure must not be read as a lower bound on wrong-class rows.** D-21's shape in the new dimension, and the round-8 porter's own predicted attack. |
| **D-55** | MINOR | **open, latent — measured unreachable in today's corpus; inherits D-30** | Round 8's refutation, §4.4 (i). `UValue.opaque(String className, String repr)` renders content as `OPAQUE("className\|repr")`, a **non-injective concatenation**, so two values differing in **both** the class name and the representation can render **equal** content and be demoted by round 8's rule to `AGREE` + a counted type mismatch — when the representation (the object's field values) differed too. **Before round 8 that case was a `DIFFER`, so round 8 created it.** The regression test's justification for `opaque` being safe ("an untruthful opaque token is a CONTENT difference and stays a `DIFFER`", `DifferentialHarnessRegressionTest.java:1214-1232`) holds only where the split point is unique. Measured: **0 of 197** `OPAQUE` reference rows have a second `\|`, so the split point is forced today; a hand-built collision was constructed to show the mechanism. Needs a crafted `opaque()` call **and** a reference representation containing a `\|`. |
| **D-56** | MINOR | **open, latent — measured unreachable in today's corpus; inherits D-30** | Round 8's refutation, §4.4 (ii). **The demotion is not applied at depth.** `UValue.content()`'s `SEQUENCE` branch (~`:569-578`) embeds each element's `canonical()`, type token included, so a **nested** type-only difference is a *content* difference and is still scored `DIFFER`, while the identical difference at top level is `AGREE` — **round 8's own false-divergence mode one level down**. Round 8's P13 probe cannot see it: `PortedInfidelityDetectionPowerTest.asAFactoryTypedAdapterWouldReturnIt` (~`:1106`) hits `default: return produced;` for `SEQUENCE` and passes the reference's element attribution straight through, so the probe never rebuilds a sequence. The refuter closed that gap in its own rig and measured **0 `DIFFER`** over the corpus's **17** `SequenceValue` rows, because their elements are `StringValue`-shaped on both sides and the factory's assumption happens to match. **Unreachable by corpus fact, not by construction.** |
| **D-57** | MINOR | **open as a record gap — recorded here and in `harness-contract.md` §5 by this commit** | Round 8's refutation, §4.5. The demotion's cost **at gate level** was recorded nowhere: §10.9.6 measured the verdict-level change (the echoing subject's agreement rows 4 567 → 4 951) but not the stage-pass change, and this record stated that the current tree can no longer produce the pre-round-8 figures. **It can**, by a construction needing no pre-round-8 harness: an operation passes iff it measured something, disagreed nowhere and is discriminating, and under round 7's rule a type-only difference was a disagreement — so **every operation passing now with `javaTypeMismatch > 0` is a pass the demotion created.** Measured: **29** for a wrong-class or unattributed port (the figure this record quotes from round 7, here recovered from the current tree with its 29 operations **named**) and **4** for a subject that only echoes its receiver (`BooleanValue.isTrue()`, `BooleanValue.value()`, `IntegerValue.value()`, `StringValue.value()`) — **all four on DISCRIMINATING accessors, so the D-15 clause does not catch them.** Not a false green: for those four the receiver's payload genuinely is the answer, each is reviewed and signed off, and each sign-off asserts `per.agreed == per.javaTypeMismatch`. **But the 4 belongs in the record beside the 29.** |
| **D-50** | MINOR | **open, unchanged** | Round 7's refutation, §9. D-44's "197 rows across 17 operations" and D-45's "84 declare an interface or a non-final class" are stated in Javadoc and in an assertion *message*; no assertion computes either figure. Round 8 did not touch it, and the honest state is: **stated, not measured.** Both should be either asserted or marked as quoted-from-a-one-off run. |
| **D-51** | MINOR | **CLOSED (round 8, `066fe15c`)** | Round 7's refutation, §9. The worked snippet an adapter is told to copy tested only for `null`, so a literal copy would answer `nullValue()` on the 8 `void` mutators where the reference answers `voidValue()` (`Method.invoke` returns `null` for a `void` method). Both snippets — `UValue.observedFrom`'s and `Candidate`'s — now begin with the `getReturnType() == void.class` branch and name the defect. Consequence was nil either way, since `DifferentialSweep` routes two non-observations to `UNMEASURABLE`; it was documentation incompleteness in the one place a copy is invited. |
| **D-44** | MINOR | **CLOSED as a documentation defect (round 7, `4bb5b6fe`)** | **The package-insensitivity rationale is contradicted on the `OPAQUE` branch.** `UValue`'s class comment justifies comparing the class's *simple* name on the ground that a fully-qualified comparison "would make every row of a port that relocated the package a false divergence", pinned by `theTypeTokenIsPackageInsensitiveOnPurpose` — which exercises only `Kind.UREAL`, whose content carries no class name. But `UValue.opaque(className, repr)` puts the **fully-qualified** name into the compared content, and `HistoricalOracle.opaqueRepresentation` adds the FQNs of every field's declaring class, so a relocated port **is** a divergence on every `OPAQUE` row: **197 rows across 17 operations** (`type()` / `getRuntimeType()` × 16, `UIntegerValue.getuInteger()` × 1). Not a scoring error and not new — it is the pre-existing `OPAQUE` limit — but round 6 rests a design decision on a rationale its own numbers contradict on 197 rows, and `harness-contract.md` §5 understated it as "a port need only reproduce a string". **Fixed by measuring it rather than by re-wording it:** `UValue`'s class comment now states the two costs of comparing the simple name — the collision (unreachable on today's census, a corpus fact, inherits D-30; the earlier "not a shape any port of this API can take" is withdrawn as an overclaim) and the OPAQUE branch — and `theTypeTokenIsPackageInsensitiveOnPurpose` now **asserts** that two `opaque()` values differing only in package have the same `typeToken()` and different `canonical()`. So package-insensitivity is a property of the **token**, never of the row. `harness-contract.md` §5 carries the 197/17 figure. The underlying OPAQUE limit is unchanged and stays a declared limit. |
| **D-45** | MINOR | **CLOSED as a documentation defect (round 7, `4bb5b6fe`)** | **`noOperationAnswersWithTwoRuntimeClasses`'s stated reason is false for 84 of 285 operations.** Its Javadoc (`PortedInfidelityDetectionPowerTest.java:857-892`) argues "a historical operation's declared return type is one class, so for any single operation there is exactly one right answer". Measured through the isolated loader over all 285 enumerated operations: **84 declare an interface or a non-final class**, so more than one runtime class is legal by the API for each. Sharpest case: the nine `UncertainBooleanValue`-declared operations return the `UBooleanValue` subclass through a superclass-declared signature, so a port returning the **declared** type — a defensible reading of the same API — reads as divergence on every driven row. The conclusion still holds over the shipped corpora, because the test measures it at **0 of 285**; what is false is the reason, and the reason is what a reader carries into S4. Same corpus boundary as D-30, now with a number. **The Javadoc now states the measured fact instead of the false reason:** the 84 are enumerated by declared type (16 `Type`, 21 `URealValue`, 19 `UBooleanValue`, 12 `UIntegerValue`, 9 `UncertainBooleanValue`, 5 `UStringValue`, 1 `SequenceValue`, 1 `uDataTypes.UInteger`), the `UncertainBooleanValue` case is named, and the premise is labelled a **corpus fact, not a language fact**, inheriting D-30, with the instruction not to repeat "a declared return type is one class" in a stage document. The assertion itself is unchanged and still measures 0 of 285 on every run. |
| D-20 | MAJOR | open | `everyKindIsEitherAnObservationOrUnmeasurable` is **tautological**: it branches on `carriesAnObservation()` and asserts the verdict `classify` derives from that same predicate. Add a `Kind`, forget to widen the predicate, add the sample: the test stays green and D-10 returns. Non-circular criterion, still unwritten: a value-carrying kind has at least two distinguishable inhabitants. |
| **D-34** | MAJOR | **CLOSED (round 6, `d13d4858`)** | The 3-argument `DiffReportWriter.writeAll` silently substituted `AcceptedDegenerateOperations.none()`, so a report could **assert** `# accepted.degenerateOperations 0` while the pass it documents was granted under a sign-off; all five call sites used it. Measured before: on a sweep of `URealValue.isUReal()` against a one-literal subject, `stage pass WITHOUT the sign-off? false / stage pass WITH the sign-off? true` printed beside `# accepted.degenerateOperations 0`. The eliding overloads are deleted — `write` and `writeAll` both require the set and there is no other form — and `aReportCannotUnderstateItsOwnSignOffs` asserts the count, the verbatim rationale, that the two headers of one sweep under `signed` and under `none()` are **unequal**, and **reflectively** that no overload omitting the parameter exists. The fix is the absence of a default, not a convention. |
| **D-35** | MAJOR | **CLOSED (round 6, `d13d4858`)** | Commit `0a93ad4f` **weakened** the standing invariant: `anUnwrittenPortAgreesWithNothing` had asserted the reviewed set against *all* fully-agreed operations and afterwards asserted only the discriminating half, leaving the degenerate half printed. Restored as a second assertion against a new per-subject `reviewedDegenerateFullyAgreed`, **keeping** the DISCRIMINATING / NOT-DISCRIMINATING split `0a93ad4f` correctly introduced. The D-20 objection does not apply: the branch is `referenceValues().size() < 2` and what is pinned is *which operations land there*, an extensional fact about the jars and the corpora that no predicate in the file computes. Verified by experiment **on the tree at `90404528`** (the round-6 fix note's phrase "unmodified HEAD behaviour" is a misleading referent, corrected here on the refuter's finding — HEAD carries the assertion and passes): adding the assertion and nothing else fails with `expected: <[]> but was: <[RealValue.value()]>` on a run that tree's own test passes — i.e. it catches an operation a receiver-echoing subject was fully agreed with on every driven row. All fourteen buckets (7 subjects × 2 halves) are empty and asserted after the D-18 fix. |
| **D-36** | MAJOR | **CLOSED (round 6, `d13d4858`) for the acceptance test; the gate remains opt-in by design** | `UncertaintyDifferentialSmokeTest` — whose goldens are S1's committed evidence — asserted `isClean()`, which its own Javadoc says is not a pass predicate, and it is the worked example S4 would copy. It now gates through `requireStagePass(ADD_FLOOR = 784, none())` with the floor derived from the corpus **above** the run, plus the golden comparison and `throwClassMismatchCount() == 0`, and prints `isClean() true <- measured, NOT the pass criterion (D-36)` beside `stage gate failures []`. The negative direction asserts the gate refuses **and names the clause** (`- 226 row(s) did not agree`). The class comment states the input domain in prose, as D-30 requires, ending "no receiver at 42". What is *not* fixed and is not a defect: nothing in the harness forces a stage through the gate — `harness-contract.md` §4.1 says so and its false claim "a stage that forgets fails rather than passes" stays withdrawn. |
| D-17 | MAJOR | open, **narrowed by D-32**; re-measured round 6 | A subject can shrink its own `driven` denominator by raising `HarnessMarshallingException` — precisely what `Candidate`'s Javadoc instructs — so the per-operation **invariant predicate** scores it fully agreed. Round-6 numbers, both concealment routes over 285 operations: **P8** (the defect hidden behind `HarnessMarshallingException`) `{AGREE=16731, BOTH_THREW=910, HARNESS_ERROR=1351, UNMEASURABLE=91}`, **0 `DIFFER`**, stage passes **74 → 70**; **P9** (`supports()` lies) `{AGREE=15597, BOTH_THREW=910, HARNESS_ERROR=883, UNMEASURABLE=91, UNSUPPORTED=1602}`, **0 `DIFFER`**, stage passes **74 → 70**. Both lose exactly the four operations they lie about, so neither buys a pass; what they cost is attribution. Contrast D-30's P10 at **74 → 74**. **Cheap-guard assessment (round 6):** for P9 a guard is cheap — the harness already calls `supports()` on both sides, so a mismatch is one boolean per operation — and is **worth building as reporting, not as a verdict**: split `UNSUPPORTED` into a subject-declined bucket with its own `# op.<key>.*` count, so a stage cannot quote undriven rows without saying whose choice that was. It must not become a `DIFFER`, because a partially-implemented port is a legitimate S4 state and a guard that calls "not ported yet" wrong pushes S4 toward the blanket sign-offs the contract warns about. For P8 no cheap guard exists: a subject's `HarnessMarshallingException` is indistinguishable by design from a real adapter limitation, and the only defence is that it costs no stage pass — which is D-32. |
| **D-32** | MINOR | **new — narrows D-17** | Measured against the **stage gate**, a subject carrying a real arithmetic defect and hiding the affected rows behind `HarnessMarshallingException` reaches **zero** stage passes on all four affected operations (clause 2 refuses `HARNESS_ERROR`) and the row note still names the subject as the side that could not be driven. **What the attack destroys is attribution, not the verdict.** The round-4 register's "the per-operation invariant predicate is still vulnerable" is correct but over-reads as residual risk to a stage. |
| D-21 | MAJOR | **closed for the header, open for the guard** | `# rows.*` and `# verdict.*` are still sums over every result in the file and `writeAll`'s measurement guard is still file-level; but the header now carries a per-operation `# op.<key>.*` block, so no number in the file is unattributable. |
| **D-31** | MINOR | **open** | `indexBoundaries()` was drawn for `at(int)` (`MIN_VALUE, -1, 0, 1, 2, 3, 4, MAX_VALUE`) and is mostly out of range for two-index extraction: `UStringValue.uSubstring(int,int)` is **17 measured rows of 432** against a perfect port (391 `BOTH_THREW`), `at(int)` / `uAt(int)` 37 of 144. Direct cause of that operation costing no stage pass under P1. Same species as D-19 and D-28: a fact about the corpus, not the historical code. |
| D-28 | MINOR | open | The corpora contain exactly **one** `RealValue` (`REAL(0.0)`), so all 23 `RealValue.*` operations are single-valued by arithmetic. **"159 single-valued" is a joint fact about the implementation and the corpus.** |
| D-22 | MINOR | open, latent until S4 | The `UNSUPPORTED` note asserts "could be driven" per row from a per-operation `supports()`. Live the moment a port with partial `supports()` coverage is plugged in. |
| D-23 | MINOR | open, latent | `unmeasurableNote` derives void-ness from a **disjunction**, so the subject alone returning `VOID` asserts a fact about the historical declaration. Correct predicate: `ref.value.kind() == VOID` alone. |
| D-25 | MINOR | open | `AcceptedThrowPairs.java:48-49` says "the source file is plain ASCII"; the file holds fifteen non-ASCII bytes. Harmless in effect; this project's own standard is that a comment in evidence-producing code must not state something the file falsifies. |
| D-14 | MINOR | open for `AcceptedThrowPairs` | No `# accepted.*` provenance header for throw-pairs; `describe()` is called from nowhere. `AcceptedDegenerateOperations` does emit its header even when zero — but see D-34. |
| **D-37** | MINOR | **open** | Clause 3's refusal message conflates *"nothing was measured"* with *"this operation could not have failed"* when `distinctReferenceValues() == 0`, and prescribes a sign-off that **can never match** (`soleReferenceValue()` is `null`, so `rationaleFor(key, null)` is `null` unconditionally). Clause 1 fires alongside, so the true reason is present next to the false one. |
| **D-38** | MINOR | **open** | `assertEquals(cleanAndDegenerate, refusedByTheGate)` in `aNoLogicPortCannotProduceAStagePass` is **tautological** — both counters increment in the same `if` body with only `assertThrows` between them. D-20's shape inside the test that closes D-15. The `assertThrows` does the real work; the equality is not a second, independent check and must not be summarised as one. |
| **D-39** | MINOR | **open** | The "169 distinct marker strings" rationale is **false**: `DiffRow.harnessError(t)` returns `"HARNESS_ERROR:" + t.getClass().getName()` and all 169 rows failed with the same class, so all 169 carry **one** string. The pin still holds, at 1-vs-0, not 169-vs-0. Stated in `DifferentialHarnessRegressionTest.java:671-677`, `:710` and (until this commit) `harness-contract.md` §4. |
| **D-40** | MINOR | **open** | `fullyAgreedOperations()` tests `agreed == driven` (`UnwrittenPortInvariantTest.java:466`) rather than `agreed >= driven`. A regression making `HARNESS_ERROR` or `UNSUPPORTED` an agreement — the exact D1/D2 defect class — yields `agreed > driven` and drops the operation out of **both** buckets silently. One-character fix. |
| **D-41** | MINOR | **open, latent** | `# op.<key>.*` header keys are **not unique**: `DiffReportWriter.java:246-263` emits a block per `Result`, not per operation, so several results for one operation give duplicate keys with different values, no aggregation and no detection. Directly in the path of an S4 stage sweeping one operation over several corpora into one report. |
| **D-42** | MINOR | **open** | `booleanCorpus(RANDOM_DRAWS)` appends random draws to an already-exhaustive two-element domain, so the corpus census printed in every run reads `boolean=4` for a type with two inhabitants. Harmless; overstates the domain in an artefact a human reads. |
| **D-33** | MAJOR | **CLOSED by this commit** | §11.4.1 pasted, as the AFTER evidence of the 119-operation attack, a refusal produced by a **different test on a different subject over a different domain** (the one-row stub-vs-stub sweep in `DifferentialHarnessRegressionTest`), naming an operation that has 576 rows and 164 distinct reference values in the run being described. Corrected **in place** with a box that says what produced it, rather than swapped for a matching block. |
| D-15 | CRITICAL | **CLOSED** (`0a93ad4f`) | A degenerate codomain scored as fidelity. Computed, published per operation, and gated. Re-measured in round 5 by an independent reviewer who attacked the gate five ways and could not get a clean pass out of a single-valued operation. |
| D-16 | MAJOR | **CLOSED for the stage gate** | `isClean()` has no coverage floor and is unchanged deliberately; `isStagePass(int, …)` takes a mandatory floor and **rejects zero**. See D-36 for what remains. |
| D-19 | MINOR | **CLOSED** | 61 → 11 zero-measurement operations; the 11 are declared limits (8 void mutators, `UIntegerValue.power(value)`, `UStringValue.toInteger()`, `UStringValue.toReal()`). |
| D-26 | MINOR | **CLOSED and pinned** | `assertMatchesGolden` compares bytes, not lines (`93e038ac`), pinned by `goldenComparisonIsBytesAndNotLines`. |
| D-27 | MINOR | **CLOSED** | The `MIXED` note names which side threw, asserted in both directions on live data. |
| D-24 | MINOR | annotated in place | §5.2 pasted a header the `93e038ac` golden refresh superseded. |
| D-9 | — | **declared boundary, not a defect** | `SBooleanValue` (39 operations), collection receivers, `org.tzi.use.uml.ocl.type.*` and `uDataTypes.*` are out of reach by design; the 33 non-nameable operations (§10.1) and post-state are the same species. Recorded so they are not mistaken for results. |

**Totals, after round 8 and its refutation.** Open: **5 MAJOR** — D-17 (narrowed by D-32), D-20, D-29,
D-30, **D-52** — plus D-21 open only in its `writeAll` guard, plus **D-48** carried as a named limit with a
dated requirement and **D-49** open only in its provenance half; and **18 MINOR** — D-14, D-22, D-23,
D-25, D-28, D-31, D-37, D-38, D-39, D-40, D-41, D-42, D-50, **D-53**, **D-54**, **D-55**, **D-56**,
**D-57**. Closed: D-15 (CRITICAL), D-16, **D-18**, D-19, D-26, D-27, D-33, **D-34**, **D-35**, **D-36**,
**D-43** (both halves), **D-44**, **D-45**, **D-46** (CRITICAL), **D-47**, **D-51**, and the round-1/2/3
CRITICALs D1, D2, D-10. **Open MAJORs went 4 → 5 → 4 → 5**: round 6 closed four and opened one, round 7
closed that one and its refutation opened three (D-46 CRITICAL, D-47, D-48, D-49), round 8 closed or
reclassified all of those, and round 8's refutation opened **D-52**.

**D-52 is the only open MAJOR that is a defect in a *rule* rather than a limit of reach, and it is inert at
S1.** It cannot change any figure S1 publishes (74 stage passes with the wrong-class defect and without
it); what it invalidates is `harness-contract.md` §7's dated obligation as originally worded, which this
commit repairs by mandating the invocation-seam shape and replacing the reviewer check. **The mechanism —
an adapter with that shape — is S4's to write, because only S4 has a port to invoke.**

**No defect open today is a scoring defect** — nothing open scores a non-agreement as an agreement, and
nothing open scores a faithful port as diverging either. None is an invariant that is printed rather
than asserted. The four open MAJORs are two limits of reach (D-29's 92 unsatisfiable operations,
D-30's unmeasured input domain), one attribution loss a subject can force on itself (D-17/D-32), and
one tautological test (D-20). D-18 was the last false-*agreement* claim on the list: the harness
compared the payload and called it the value, on 193 of 285 operations of a port whose entire subject
is four new value classes. Its successor, D-43, was the mirror image — a `DIFFER` a faithful port
earned by following the documented adapter example. Two rounds tried to close it by making the ported
side's token a *claim the author had to justify*; both claims could be false, and both were measured
false. Round 8 closed it by removing the claim: the token is observed or assumed, and a difference
between the two sides' classes alone is **measured and published but not scored**, because at S1 the
harness cannot attribute it. That is a deliberate, dated reduction in what the instrument asserts, and
`harness-contract.md` §7 carries the requirement that reverses it at S4. **Round 8's refutation then showed
that the requirement as first written could be satisfied by laundering (D-52), so the requirement now
mandates the adapter's shape and not merely its call — and that is the last move available at S1, because
the object that makes the shape meaningful does not exist until S4 writes it.**

---

## 10.5 The id re-keying map — so no evidence is orphaned

Round 4 produced two independent reports that both used `D-16`…`D-19` for different defects, and
`D-15` denoted **both** a round-3 MINOR and the round-4 CRITICAL. Round 5's static review used its
own `R5-n` space. Nothing in any round report was rewritten; this is how they are read.

| Source report | local id | canonical | defect |
|---|---|---|---|
| `stage-01.md` §8.2 (round 3) | `D-15` | **D-26** | `assertMatchesGolden` compared lines, not bytes |
| `stage-01.md` §8.2 (round 3) | `D-16` | **D-27** | the `MIXED` note did not name which side threw |
| `stage-01.md` §8.2 (round 3) | `D-10`…`D-14`, `D-11a/b/c`, `D-12a/b`, `D-13a/b` | unchanged | — |
| `stage-01-verification-round4.md` | `D-15` | **D-15** | degenerate codomain scored as fidelity |
| `stage-01-verification-round4.md` | `D-16` | **D-16** | `isClean()` has no coverage floor |
| `stage-01-verification-round4.md` | `D-17` | **D-17** | a subject controls its own denominator |
| `stage-01-verification-round4.md` | `D-18` | **D-18** | the canonical-form collision, 193 of 285 |
| `stage-01-verification-round4.md` | `D-19` | **D-19** | coverage: 61 operations measure nothing |
| `stage-01-static-review-round4.md` | `D-15` | **D-15** | same defect, static route (91 constant-`false` ops) |
| `stage-01-static-review-round4.md` | `D-16` | **D-20** | `everyKindIsEither…` is tautological |
| `stage-01-static-review-round4.md` | `D-17` | **D-21** | writer guard and headers are file-level totals |
| `stage-01-static-review-round4.md` | `D-18` | **D-22** | `UNSUPPORTED` note asserts "could be driven" |
| `stage-01-static-review-round4.md` | `D-19` | **D-23** | `unmeasurableNote` void-ness from a disjunction |
| `stage-01-static-review-round4.md` | `D-20` | **D-24** | §5.2 pasted a superseded header |
| `stage-01-static-review-round4.md` | `D-21` | **D-25** | `AcceptedThrowPairs` "plain ASCII" comment |
| `stage-01.md` §11.6 (amendment 5) | — | **D-28** | the corpora contain exactly one `RealValue` |
| `stage-01-verification-round5.md` | `D-29` | **D-29** | the gate is not satisfiable by fidelity |
| `stage-01-verification-round5.md` | `D-30` | **D-30** | detection is bounded by an unmeasured domain |
| `stage-01-verification-round5.md` | `D-31` | **D-31** | `indexBoundaries()` yields almost no measured rows |
| `stage-01-verification-round5.md` | `D-32` | **D-32** | D-17 costs attribution, not the verdict |
| `stage-01-static-review-round5.md` | `R5-1` | **D-33** | §11.4.1 attributes one test's output to another |
| `stage-01-static-review-round5.md` | `R5-2` | **D-34** | 3-arg `writeAll` substitutes `none()` |
| `stage-01-static-review-round5.md` | `R5-3` | **D-35** | the standing invariant was weakened |
| `stage-01-static-review-round5.md` | `R5-4` | **D-36** | the gate is opt-in; the smoke test gates on `isClean()` |
| `stage-01-static-review-round5.md` | `R5-5` | **D-37** | clause-3 refusal message conflates two causes |
| `stage-01-static-review-round5.md` | `R5-6` | **D-38** | tautological assertion in the D-15 test |
| `stage-01-static-review-round5.md` | `R5-7` | **D-39** | "169 distinct marker strings" is false |
| `stage-01-static-review-round5.md` | `R5-8` | **D-40** | `agreed == driven` should be `agreed >= driven` |
| `stage-01-static-review-round5.md` | `R5-9` | **D-41** | `# op.<key>.*` keys are not unique |
| `stage-01-static-review-round5.md` | `R5-10` | **D-42** | `boolean=4` over a two-inhabitant type |
| `stage-01-verification-round6.md` | `D-37` | **D-43** | the ported side's Java type is declared, not observed |
| `stage-01-verification-round6.md` | `D-38` | **D-44** | the package-insensitivity rationale fails on `OPAQUE` |
| `stage-01-verification-round6.md` | `D-39` | **D-45** | "a declared return type is one class" is false for 84 of 285 |
| `stage-01-verification-round7.md` | `D-46`…`D-51` | **unchanged** | the mandated reason reached 0 rows (CRITICAL); the note certified a fabricated observation; the absorbing state; the report's one-line difference; 197/84 stated but not measured; the `void` case in the worked snippet |
| `stage-01-verification-round8.md` | `D-52`…`D-57` | **unchanged** | the escape hatch moved to an `Object` parameter (MAJOR); the "no agreement figure without the count" overclaim; the count is not monotone in wrongness; `OPAQUE` content is non-injective; a nested type-only difference is still a `DIFFER`; the demotion's gate-level cost is unrecorded |

Round-1 and round-2 fix ids (`F1`–`F11`) are commit-scoped and unchanged; they name fixes, not
defects.

**Round 6's fixes introduced no new defect ids; its refutation introduced three, and they collided.**
`stage-01-verification-round6.md` numbered its findings `D-37`, `D-38` and `D-39` — keys this register
had already spent on round-5 static-review MINORs (R5-5, R5-6, R5-7). Their canonical keys are
**D-43**, **D-44** and **D-45**, mapped in the three rows just added; the round-6 report is not
rewritten, and a bare `D-37`/`D-38`/`D-39` anywhere in `docs/port2/` still means the round-5 MINORs in
§10.4. The fixes themselves closed D-18, D-34, D-35 and D-36 and added measured numbers to D-17/D-32
and D-30. The probe id `P12-boxed-primitive` belongs to the
`PortedInfidelityDetectionPowerTest` probe space, not to this register; the detection test that
carries it is `aWrongJavaTypeWithRightContentIsCountedNotScored` (named
`aWrongJavaTypeWithRightContentIsADivergence` until round 8 demoted the finding), which is a separate
method rather than an entry in `probes()`, so the round-5 blind-spot set and corpus-sensitivity table
stay directly comparable with round 5's.

**Rounds 7 and 8 collided with nothing.** Both refutations numbered their findings from the next free key
in this register — `D-46`…`D-51` and `D-52`…`D-57` — so those ids are canonical as written and the two
rows just added record that, rather than a mapping. **No id in `docs/port2/` now denotes two defects**, and
a bare `D-nn` anywhere still means §10.4.

---

## 10.6 What S4–S7 may and may not say

Normative form in [`harness-contract.md`](harness-contract.md) §4; this is the summary a reviewer
should hold a stage document to.

1. **Quote per operation, never per file.** `# rows.*` and `# verdict.*` are sums (D-21).
2. **Quote three numbers together or none:** measured rows, distinct reference values, and — in
   prose, because the harness does not compute it — **the input domain the sweep covered** (D-30).
   "576 agreed" is not a fidelity claim; "576 agreed over 24 boundary receivers × 24 arguments, no
   value in (2,100) other than the two random draws, 164 distinct reference values" is.
3. **Gate with `requireStagePass(floor, acknowledged)`, with the floor written down before the run**,
   and pass the sign-off set to `writeAll` — which is now the only form there is (D-34, closed).
   `UncertaintyDifferentialSmokeTest` is the worked example to copy (D-36, closed).
4. **Do not automate on the boolean.** Record the perfect-port baseline `stageGateFailures(...)` and
   diff the **clause list** against it; on 92 of 285 operations the boolean cannot move (D-29).
5. **Never `isClean()` as a pass** (D-36), never `disagreements().isEmpty()`, and never `>= 2` read
   as *sufficient* — `BooleanValue.value()` and `BooleanValue.isTrue()` sit at exactly 2 and are
   nearly free for a subject echoing one bit.
6. **Name what the harness could not see**, per stage: void operations, `SBooleanValue`, collection
   receivers, the type layer, the 33 non-nameable operations (`equals(Object)` first among them), and
   any operation the corpora leave single-valued. **A `Kind` difference is a `DIFFER`** (D-18, closed
   since round 6). **Back on the list in a narrower form since round 8:** a *runtime-class* difference
   with identical content is **measured and published but not scored** — `AGREE`, counted in
   `javaTypeMismatch` — so **a type-only infidelity does not fail a gate at S1**, and a stage must say
   so beside any agreement figure until the S4 requirement in `harness-contract.md` §7 is met (D-43).
7. **A sign-off is a disclosure, not a pass.** Its rationale must say what a reader should *not*
   conclude, and it lands in the evidence file.
8. **Attribute the Java class the port *returned*, not the one your factory chose — and say in the
   stage document which you did** (D-43; half (a) closed round 7, half (b) closed round 8 by removing the
   API). Call `UValue.observedFrom(theObjectYourPortReturned)`. There is **no API that takes a class name
   from an adapter at all** any more: `asJavaType(String)` went in round 7 and
   `declaredJavaType(String, String)` in round 8, after the second was measured doing exactly what the
   first did. A token is `OBSERVED` or `ASSUMED`; you choose neither. A factory-typed adapter makes a
   **content-perfect** port report **3 445** java-type mismatches across **182 of 285** operations — the
   same figure as the planted wrong-class defect, and the only thing that tells them apart is the row
   note's provenance. **No stage may quote a type-fidelity figure without stating how its adapter obtained
   the token**, and a row whose note reads `subject ASSUMED` is a finding about the adapter.
9. **S4 has one extra obligation, and it is dated (2026-08-17), not optional.** Once real ported value
   classes exist in `use-core/src/main`, route the adapter through `observedFrom` and add
   `assertEquals(0, result.javaTypeMismatchCount(), result.summary())` to the gate.
   `harness-contract.md` §7 carries the requirement and the measurement that forces it.
10. **The obligation is a SHAPE, not a call** (D-52, round 8's refutation). The object handed to
    `observedFrom` must be **the value the invocation returned**, captured at one seam and used for nothing
    else — `PortedInfidelityDetectionPowerTest.observeWhatThePortReturned` (`:1116-1126`) is the executing
    example. Measured: one line observing an **empty stand-in class** takes a **real** 401-row /
    9-operation wrong-class port to `javaTypeMismatch 0` with the sweep byte-identical to the perfect-port
    control and **0** rows of disclosure. **A reviewer checks the adapter's shape, not its prose:** "the
    attribution route is stated" does not separate an honest adapter from a laundering one, because both
    route through `observedFrom` and both would state it truthfully. Quote the figure **per operation**, not
    per file — a file-level `0` can also be D-54.
11. **Two figures a stage must not over-read.** `javaTypeMismatchCount()` is **not a lower bound** on
    wrong-class rows (a row wrong in both dimensions is a `DIFFER` and leaves the count: 3 445/182 →
    1 883/42 — D-54), and a **stage pass carrying `javaTypeMismatch > 0` is a pass the round-8 demotion
    created** (29 of them for a wrong-class port, 4 for a subject that only echoes its receiver — D-57).
12. **Read `harness-contract.md` §8 before writing the sweep.** It is this list in imperative form, with
    the refusal playbook and the floor rule, and it exists so that S4 does not rediscover eight rounds.

---

## 10.7 The standing lesson, after eight rounds

Round 1: a harness failure counted as agreement. Round 2: two throws counted as agreement. Round 3:
two `VOID`s counted as agreement. Round 4: two equal values over a one-valued codomain counted as
fidelity — no bug in the scorer at all. Round 5: no false claim in the scorer to find, and three of
its four MAJORs in the **documents**: a report pasting one test's output as another's (D-33), a
report able to assert zero sign-offs while one was in force (D-34), and an invariant quietly asserting
less than it did the commit before (D-35). The same failure mode, translated up one level.

Round 6: back in the scorer, and it had been sitting in the register for two rounds labelled "out of
reach of a mutation experiment". **The harness was comparing the content of a value and calling it
the value.** For an extension whose whole subject is four new value *classes*, a port that returns
`IntegerValue` where the historical returns `int` — or `URealValue` where it returns
`UIntegerValue` — is exactly the mistake to expect, and 193 of 285 operations could not see the first
half of that. The lesson is narrower than the round-5 one and worth keeping separately: **a defect
that has been sized but never reproduced has not been assessed.** "193 of 285" was in the register
from round 4; the four-line experiment that turns it into `0 → 3 445 DIFFER rows` was never run, and
the number sat there being cited as a limit instead of being closed.

Round 6's refutation adds the counterpart lesson, and it is the sharper of the two: **a fix is not
assessed until someone measures what a *faithful* port does under it.** The porter measured the defect
(3 445 `DIFFER`) and the identity control (0 `DIFFER`) and concluded there was no false-divergence
mode. The construction in between — a content-perfect port whose adapter follows the documented worked
example — was never run, and it produces the same 3 445. **When a defect's signature and a faithful
port's signature are the same number, the number is not evidence**; and where a token is *declared* by
the thing under test rather than *observed* by the instrument, that collision is the default, not the
edge case.

The generalisation that survives all five: **an artefact whose headline reads stronger than the
measurement behind it.** That is the thing to look for in S4–S7, and it is why the round-5 verifier
recorded, in place rather than quietly fixing it, that they had drafted a hand-recomputation section
from memory before the run finished and had to replace the fabricated block with pasted output. A
review whose own evidence is reconstructed is worth nothing.

Two questions the instrument now answers and one it does not:

* *Could the sweep have failed?* — `distinctReferenceValues()`, gated. **Answered.**
* *Does silence mean anything?* — round 5's eleven probes, plus round 6's twelfth.
  **Answered, and measured: yes, inside the region the corpora reach.**
* *Is a value its content, or its content and its type?* — round 6. **Answered: both**, and since
  round 7 the answer is enforced **symmetrically**: both sides attribute through
  `UValue.observedFrom(Object)`, the one-argument declaring route is deleted, and a row whose token was
  merely assumed says so in its note (D-43, closed). What is still outside the instrument is an
  adapter's *choice of object*, and a declared token — which now costs a written reason.
* *How much of the input domain did we reach?* — **unanswered, and it is the fifth door.** The next
  round should push there, and it should push on the operations that cannot be named at all —
  `equals(Object)` first, which is where round 6's finding lands hardest: a port whose `equals`
  ignores the uncertainty component is still invisible, and `equals` is exactly the method a
  type-confused port gets wrong.

---


> **The eighth round's lesson, and it is about fixes rather than about rows.** Rounds 6 and 7 both closed
> the same defect by giving the author of the thing under test a way to *state* the fact the instrument
> could not observe — first a string, then a string plus a reason. Both statements could be false, and both
> were measured false, in the same construction, one round apart. The question the seventh round's own
> §8 told the next reviewer to ask — *what does a perfect port measure under this rule, and is that
> different from what a defective one measures?* — has a companion: **can the thing under test influence
> what this rule measures? If it can, the rule is measuring the wrong side, and no amount of mandated
> disclosure fixes that** — a disclosure only fires where the instrument already noticed something, and
> laundering is precisely the case where it did not. The remedy is not a better guard on the statement; it
> is to remove the statement and to measure what can actually be observed, saying plainly what is
> therefore not yet measured, **with a dated obligation on the stage where it becomes observable.**

> **The refutation of that lesson's own fix, and where it lands (round 8R, D-52).** Round 8 removed the
> *statement* — and the refuter showed the statement had simply moved out one parameter type: `observedFrom`
> takes an `Object`, and **an author who chooses the object has chosen the token**, with the same 0 rows of
> disclosure and the same sweep byte-identical to the control. So the general form of the lesson is not
> "delete the string": it is **an instrument must read something the thing under test does not choose.** At
> S1 no such thing exists for the ported class, which is why the honest position is a counted, published,
> ungated dimension plus a dated obligation — and why the obligation must name the **shape** that makes the
> object non-choosable (the invocation seam), not merely the call. **The remaining step is S4's, because the
> object only exists once the port does.**

## 10.8 Round 7 — the closure of D-44, D-45 and half of D-43 (behaviour `4bb5b6fe`)

> **CORRECTION BOX, added with round 8 (`066fe15c`). Read this before believing any sentence below about
> `declaredJavaType`.** This section was written as the evidence for round 7 and is preserved as written,
> because a record that edits itself cannot be audited. **Two of its claims were refuted by round 7's
> independent refuter** ([`stage-01-verification-round7.md`](stage-01-verification-round7.md)) and are
> **withdrawn**:
>
> 1. **"`DifferentialSweep` prints the reason into the note of any row the declaration moved" — FALSE in
>    the only direction that matters.** `declaredJavaType(referenceToken, "x")` on the planted wrong-class
>    port produced a sweep **byte-identical to the perfect-port control** and the reason appeared in **0**
>    rows: the type note fires only when the two class names *differ*, which a laundering declaration
>    makes false by construction. So half (b) of D-43 — "one line erased the check" — was **not** closed by
>    round 7. Everything below about a written reason making the stating route safe is withdrawn with it.
> 2. **"Both classes were OBSERVED … so this row is a statement about the two implementations" — an
>    assertion the harness cannot make** (D-47). `observedFrom` believes any object; 1 618 rows asserted a
>    fabricated observation as fact.
>
> **What below still stands:** half (a) of D-43 (the false-divergence closure), independently reproduced;
> the deletion of `asJavaType(String)`; `observedFrom` on all fourteen `fromHistorical` branches; D-44 and
> D-45; and every acceptance and determinism figure. **Round 8's answer is in §10.9:**
> `declaredJavaType` is deleted rather than guarded, and a type-only difference is counted rather than
> scored. Where §10.8 and §10.9 disagree, **§10.9 is authoritative.**
>
> **Three method names below were changed in round 8 to say what they now measure**, so a grep for them in
> today's tree finds nothing: `aWrongJavaTypeWithRightContentIsADivergence` →
> `aWrongJavaTypeWithRightContentIsCountedNotScored`,
> `aFactoryTypedAdapterMeasuresExactlyWhatThePlantedWrongTypeDoes` →
> `aFactoryTypedAdapterCostsNoPassAndIsCountedNotScored`,
> `theTypeTokenIsObservedOrDeclaredAndTheRowSaysWhich` → `theTypeTokenIsObservedOrAssumedAndNoApiTakesOne`.
> The method count is unchanged; none was added or removed.

**Written by the porter of round 7, who owned Maven for it and does not sign off on his own work.**
Every number below is pasted from a run on this machine. Nothing here is a new finding: this section
is the evidence that the refutation's three findings are closed, and the record of one place where the
fix goes further than the refuter asked and one where it deliberately differs from what he specified.

### 10.8.1 What changed, and why it is a mechanism rather than a warning

D-43 was an **asymmetry**, not a scoring bug: `HistoricalOracle.fromHistorical` observed the
reference's class (`result.getClass().getName()`), while the ported side's token was whatever an
adapter passed to a public one-argument `UValue.asJavaType(String)`. Five changes, all test-scoped:

1. **`UValue.observedFrom(Object)`** — reads `getClass().getName()` off the object a side returned.
   `fromHistorical` now calls it on **all fourteen** unwrapping branches, so both halves of the comparison are the
   same kind of statement. A primitive needs nothing special: reflection and autoboxing both produce
   the `java.lang.Boolean` the reference observes for a `boolean`-declared operation.
2. **`asJavaType(String)` is deleted.** The only stating route is
   `declaredJavaType(String javaType, String why)`, which **rejects a blank reason** — the discipline
   `AcceptedDegenerateOperations` already imposes on a sign-off, for the same reason: a claim the
   instrument cannot check has to cost a sentence a reviewer reads. *This is more than bullet 1 of
   §4.4 asked for.* Publishing an observing route would have left the one-line lie in place beside it;
   the refuter's own §4.2 is the argument for removing it.
3. **`UValue.typeProvenance()`** — `OBSERVED` / `DECLARED` / `ASSUMED` / `NONE` — is written into the
   note of every type-mismatch row, together with the declaration's reason where there is one. It is
   in **neither `canonical()` nor any verdict**: a subject must not be able to talk its way out of a
   divergence by admitting that it guessed, which would be D-17's shape in a new costume. What it buys
   is the attribution the round-6 measurement lacked.
4. **`Candidate`'s Javadoc** carries the obligation as a second invariant beside the
   `HarnessMarshallingException` one, with the snippet, both measured failure modes and the incentive
   hazard stated as such.
5. **`StubCandidate`** routes every result through one named method, `attributed(UValue)`, which
   *declares* with a written reason — see §10.8.4 for why that is the honest reading of bullet 2 and
   what the alternative measures.

### 10.8.2 The measurement: control, both readings of 3 445, and the closure

One test, four subjects, same 285 stage-shaped operations / 19 083 rows, seed 20260817. Pasted from
the acceptance run (`PortedInfidelityDetectionPowerTest.aFactoryTypedAdapterMeasuresExactlyWhatThePlantedWrongTypeDoes`):

```
=== D-43: two readings of the same measurement ====================
  subject                              DIFFER        ops   passes notes ASSUMED
  P0-perfect                                0          0       74            0
  P12-boxed-primitive                    3445        182       45            0
  P13-factory-typed-adapter              3445        182       45         3445
  P14-observing-adapter                     0          0       74            0
  the port with a DEFECT loses    29 stage passes
  the port with NO defect loses   29 stage passes, to its adapter alone
  the 29 a faithful port loses to a factory-typed adapter:
      [BooleanValue.compareTo(value), BooleanValue.hashCode(), BooleanValue.isFalse(), BooleanValue.isTrue(), BooleanValue.toString(), BooleanValue.toStringWithType(), BooleanValue.value(), IntegerValue.compareTo(value), IntegerValue.hashCode(), IntegerValue.toString(), IntegerValue.toStringWithType(), IntegerValue.value(), StringValue.compareTo(value), StringValue.hashCode(), StringValue.toString(), StringValue.toStringWithType(), StringValue.value(), UIntegerValue.compareTo(value), UIntegerValue.hashCode(), UIntegerValue.toString(), UIntegerValue.toStringWithType(), UIntegerValue.uncertainty(), UIntegerValue.value(), URealValue.compareTo(value), URealValue.hashCode(), URealValue.toString(), URealValue.toStringWithType(), URealValue.uncertainty(), URealValue.value()]
  first row of the ADAPTER defect, which reads like a port defect:
      0	BooleanValue.compareTo(value)	BOOLEAN(true)@BooleanValue | BOOLEAN(true)@BooleanValue	INTEGER(0)@Integer	INTEGER(0)@IntegerValue	DIFFER	java type mismatch: reference returned java.lang.Integer (INTEGER(0)@Integer) / subject returned org.tzi.use.uml.ocl.value.IntegerValue (INTEGER(0)@IntegerValue); the content is IDENTICAL -- right content, wrong Java type (defect D-18); this row is a divergence because a port of these classes must reproduce the declared result type, not only the payload. The subject's class was ASSUMED, not observed -- the factory default for kind INTEGER, which is wrong for 182 of 285 operations: this row may be an adapter defect and not a port defect (D-43), and an adapter must attribute through UValue.observedFrom(Object).
===================================================================
```

Read the table one row at a time.

* **`P0-perfect` — the control, first.** `DIFFER 0`, 0 diverging operations, **74** stage passes.
  Unchanged from rounds 5 and 6, and asserted before anything else in the method. From the same run:

  ```
  === detection power: control (a perfect port) =====================
  seed                 20260817
  operations           285  (stage-shaped domains)
  rows                 19083
  measured rows        17199
  agreement rows       17199
  verdict tally        {AGREE=17199, BOTH_THREW=910, HARNESS_ERROR=883, UNMEASURABLE=91}
  diverging operations 0   <- MUST be 0, or nothing below is attributable to a planted defect
  stage passes         74 of 285  (isStagePass(1, none()))
  ```
* **`P13-factory-typed-adapter` — the BEFORE state of the defect, kept as a test.** A port with **no
  defect in it at all** — bit-for-bit the historical content on every row — whose adapter returns
  `UValue.<factory>(content)`. `DIFFER 3 445`, **182 of 285** operations, **29** stage passes lost,
  including all four accessors the extension is about. This is the refuter's §4.1 reproduced, and it is
  the *only* adapter shape that existed before this commit: `git show a0fc238a:…/UValue.java | grep -c
  observedFrom` → `0`. So "before" is not a hypothetical — it is what an S4 author following the
  documented example would have measured.
* **`P14-observing-adapter` — the AFTER state, and the defect closed.** The **same** content-perfect
  port, the same factory-built content, one line different: `.observedFrom(returned)`, where `returned`
  is the object the port's method handed back. `DIFFER 0`, 0 diverging operations, **74** stage passes.
  Asserted not merely as "0" but as **set equality with the control's stage passes** and **map equality
  with the control's verdict tally**, so the adapter is row-for-row indistinguishable from the reference
  itself.
* **`P12-boxed-primitive` — D-18, not regressed.** The planted wrong-class port still measures
  `DIFFER 3 445` on **182 of 285** operations and still loses the same **29** stage passes. From the
  same run, its own method:

  ```
  === D-18: right content, wrong Java type =========================
  operations           285
  control  rows        19083, measured 17199, agreed 17199  {AGREE=17199, BOTH_THREW=910, HARNESS_ERROR=883, UNMEASURABLE=91}
  boxed    rows        19083, measured 17199, agreed 13754  {AGREE=13754, BOTH_THREW=910, DIFFER=3445, HARNESS_ERROR=883, UNMEASURABLE=91}
  control DIFFER+MIXED 0   <- MUST be 0
  boxed   DIFFER rows  3445
  DETECTED on          182 of 285 operations
  ```
* **The last column is the point of the round.** `notes ASSUMED` counts rows whose note says the
  *subject's* class was never observed: **3 445** for the adapter defect, **0** for the port defect.
  The two measurements were byte-identical in every aggregate round 6 published; they are now
  distinguishable **in the rows**, which is where a reader of an evidence file looks. Both directions
  are asserted — `assertEquals(3445-equivalent, …)` on the adapter defect and
  `assertEquals(0, …)` on the port defect, the second because hedging a genuine divergence would be the
  same defect from the other side.

The equality of the two 3 445s is itself asserted, on purpose (`assertEquals(plantedDefect.count(DIFFER),
adapterDefect.count(DIFFER))`, same operation set, same 29 stage passes): if a later change ever breaks
that identity, the record's account of round 6 has become stale and someone must re-read it.

### 10.8.3 The unit resolution: the same row, three attributions

`DifferentialHarnessRegressionTest.theTypeTokenIsObservedOrDeclaredAndTheRowSaysWhich`, pasted:

```
=== D-43: the same DIFFER row, three attributions of the subject ===
  ASSUMED (factory default)
      … the content is IDENTICAL -- right content, wrong Java type (defect D-18); … The subject's class was ASSUMED, not observed -- the factory default for kind INTEGER, which is wrong for 182 of 285 operations: this row may be an adapter defect and not a port defect (D-43), and an adapter must attribute through UValue.observedFrom(Object).
  OBSERVED (off a real object of another class)
      … the content is IDENTICAL -- right content, wrong Java type (defect D-18); … Both classes were OBSERVED from the objects the two sides returned, so this row is a statement about the two implementations.
  DECLARED (with a reason)
      … the content is IDENTICAL -- right content, wrong Java type (defect D-18); … The subject's class was DECLARED, not observed, declared because: asserted by hand in a unit test.
===================================================================
```

All three are `DIFFER` — asserted, because the provenance must not move a verdict. The same test pins
that `observedFrom` derives the token from `getClass().getName()`, that a boxed primitive yields exactly
what the reference observes, that the factory default is `ASSUMED`, that `declaredJavaType` rejects a
blank reason and `observedFrom(null)` is refused, and that a declared and an observed value with the
same class name have **equal** `canonical()` and **unequal** `typeProvenance()`.

### 10.8.4 Where this deviates from `stage-01-verification-round6.md` §4.4, and why

The refuter's bullet 2 asks that `StubCandidate` "attribute explicitly through the new mechanism even
where the factory default already happens to be right". It attributes explicitly, through one named
method, `StubCandidate.attributed(UValue)` — but by **declaring** with a written reason, not by
observing. **It cannot observe: there is no ported `URealValue` in `use-core/src/main` to observe.
Writing one is S4.** The only way to give the stub something to observe is to fabricate a stand-in
class, and that is not a neutral choice. Measured, not argued — a scratch build with
`attributed()` observing a private nested `StubCandidate.URealValue` and nothing else changed:

```
$ mvn -B -o -pl use-core test -Dtest=UncertaintyDifferentialSmokeTest -Dscratch.fabricate=true
golden (matched)     …/docs/port2/differential/s1-smoke-ureal-add.tsv
[ERROR] UncertaintyDifferentialSmokeTest.smokeDetectsAWrongSubject … diverges from the committed golden
        …/s1-smoke-ureal-minus-faulty.tsv at line 52
  golden: 29	URealValue.minus(value)	…	UREAL(0.0,1.4142135623730951)@URealValue	UREAL(0.0,0.0)@URealValue	DIFFER	
  actual: 29	URealValue.minus(value)	…	UREAL(0.0,1.4142135623730951)@URealValue	UREAL(0.0,0.0)@URealValue	DIFFER	java type mismatch: reference returned org.tzi.use.uml.ocl.value.URealValue (…) / subject returned org.tzi.use.uncertainty.differential.StubCandidate$URealValue (…); the content is different as well. Both classes were OBSERVED …
$ awk -F'\t' '$6=="AGREE"' …/s1-smoke-ureal-minus-faulty.tsv | wc -l     # 558 agreement rows: unaffected
$ awk -F'\t' '$6=="DIFFER" && $7 ~ /java type mismatch/' … | wc -l        # 226 of 226 re-captioned
```

So a fabricated observation would leave all 558 agreement rows intact — the compared token is the
*simple* name — and re-caption **all 226** disagreeing rows of S1's own committed evidence as a "java
type mismatch", which is a false explanation of an arithmetic finding. The scratch change was reverted;
the golden is unchanged. A declaration with a reason is the honest reading of the bullet, and
`Candidate`'s Javadoc now says outright that the one thing in `StubCandidate` an S4 adapter must **not**
copy is this method.

Bullet 3 (the third trap in `harness-contract.md` §7, carrying the 182 / 29 figure and the incentive
hazard in one sentence) and bullet 5 (§1's withdrawn claim reworded into the property that is actually
true) had already been drafted into the contract when round 6's refutation was folded into the record
(`a0fc238a`); round 7 rewrites both to describe the mechanism that now exists rather than the obligation
that did not.

### 10.8.5 Acceptance, determinism, hygiene

```
$ mvn -q clean && mvn -B verify -Djava.awt.headless=true
[INFO] Tests run: 11, … -- in org.tzi.use.architecture.MavenCyclicDependenciesCoreTest
[INFO] Tests run: 7,  … -- in Detection power: subtle infidelities in a ported U-type
[INFO] Tests run: 6,  … -- in Uncertainty differential smoke
[INFO] Tests run: 10, … -- in Unwritten-port invariant
[INFO] Tests run: 9,  … -- in HistoricalOracle class-loader isolation
[INFO] Tests run: 34, … -- in Differential harness regressions
[INFO] Tests run: 1,  … -- in org.tzi.use.uml.mm.ModelAPITest
[INFO] Tests run: 78, Failures: 0, Errors: 0, Skipped: 0     <- use-core surefire
[INFO] Tests run: 1,  Failures: 0, Errors: 0, Skipped: 0     <- use-core failsafe (OCLExpressionIT)
[INFO] Tests run: 1,  Failures: 0, Errors: 0, Skipped: 0     <- use-gui surefire (MavenLayeredArchitectureTest)
[INFO] Tests run: 129, Failures: 0, Errors: 0, Skipped: 0    <- use-gui failsafe (ShellIT)
[INFO] BUILD SUCCESS
[INFO] Total time:  01:27 min
EXIT=0
```

**79 surefire + 130 failsafe = 209 methods, 0 failures**, and the whole delta from round 6's 207 is
two methods, both new and both named above:

| | round 6 (`854abb83`) | round 7 (`4bb5b6fe`) | delta |
|---|---|---|---|
| use-core surefire | 76 | **78** | **+2** |
| use-gui surefire | 1 | 1 | 0 |
| use-core failsafe | 1 | 1 | 0 |
| use-gui failsafe | 129 | 129 | 0 |
| **total** | **207** | **209** | **+2** |

`Differential harness regressions` 33 → 34 (`theTypeTokenIsObservedOrDeclaredAndTheRowSaysWhich`),
`Detection power` 6 → 7 (`aFactoryTypedAdapterMeasuresExactlyWhatThePlantedWrongTypeDoes`). **No
pre-existing test was removed, renamed, skipped or weakened**; four existing call sites moved off the
deleted `asJavaType`, three onto `observedFrom` (including
`UnwrittenPortInvariantTest.aDegenerateOperationNeedsAWrittenSignOff`, whose hand-written
`asJavaType("java.lang.Boolean")` the refuter named as the tree's first instance of the habit) and one
onto `declaredJavaType` (the synthetic relocated class in the package-insensitivity test, which has no
object to observe by construction).

**Determinism — two full `verify` runs, all five written reports compared byte for byte:**

```
$ diff -r run1 run2
IDENTICAL (byte-for-byte, all 5 reports)
$ sha256sum run1/s1-smoke-ureal-add.tsv run1/s1-smoke-ureal-minus-faulty.tsv
86e6a4e2403f85a235695a35613c2e3f633220943ef0743dd004d4b7a71dea50  s1-smoke-ureal-add.tsv
cd6143ff85a6083041abedfedc8c64c51e63fa4a03fa77d9f5e018967d143755  s1-smoke-ureal-minus-faulty.tsv
```

**The goldens did not move and were not refreshed.** Those two digests are the ones
`stage-01-verification-round6.md` §1.3 recorded, character for character, so the type token, the
provenance and the new tests changed no committed evidence — which is the right outcome: the note is
appended only where the two sides' *fully-qualified* classes differ, and on both goldens they do not.

```
$ git diff --name-status 30d480db..HEAD -- '*/src/main/*'
(empty)
```

No pom, no `module-info.java`, no `use-gui`, no `use-assembly`, no pre-existing upstream test. Behaviour
(`4bb5b6fe`) and documentation are separate commits.

### 10.8.6 What round 7 does NOT claim

* **It is not independently verified.** The porter wrote it and ran Maven for it; nobody has yet tried
  to break it. The obvious attacks: an adapter that observes the *wrong* object (the harness will
  believe it, and no round has measured what that costs); a `declaredJavaType` reason that is a
  non-blank lie; and whether `typeProvenance` should reach an **aggregate** in the report header rather
  than only the row note, which would have made round 6's ambiguity visible in the evidence *file*
  instead of only in a row.
* **It does not touch the four open MAJORs.** D-17/D-32, D-20, D-29 and D-30 are exactly where round 6
  left them. In particular D-30 — the unmeasured input domain — is still the largest thing wrong with
  this instrument, and D-45's closure is a reminder of it: "no operation answers with two runtime
  classes" is a corpus fact.
* **It does not make a type-fidelity figure self-attributing.** A stage must still state how its
  adapter obtained the token. The harness can now say "this row's subject class was never observed"; it
  cannot say "this adapter observed the right object".

---

## 10.9 Round 8 — the Java-runtime-type check is DEMOTED, and its promotion is S4's (behaviour `066fe15c`)

### 10.9.1 Why a demotion and not a fourth patch

Rounds 6 and 7 are both about one addition — the Java-runtime-type check — and neither converged.
Round 6 closed the blindness and created a false-*divergence* mode (a content-perfect port with a
factory-typed adapter: 3 445 `DIFFER` on 182 of 285 operations, 29 stage passes lost). Round 7 closed
that and left an escape hatch: `declaredJavaType(referenceToken, "x")` took a **wrong-class** port to a
sweep **byte-identical to the perfect-port control**, with the mandated reason in **0** rows, while three
documents asserted the reason was printed.

The pattern is the finding. The check compares the *ported side's* Java runtime class, and **at S1 there
is no ported implementation to observe**: no `org.tzi.use.uml.ocl.value.URealValue` exists in
`use-core/src/main`, and writing it is stage S4. The ported token is therefore author-influenced by
construction, and each round produced a new way for the author to influence it — first a one-argument
string, then a two-argument string with a reason nobody could be made to see. Patching the newest
instance leaves the class of defect intact.

So round 8 removes the ability to choose a token at all, and stops scoring a difference the harness
cannot attribute. **It does not stop measuring it.**

### 10.9.2 What changed, in four parts

1. **No author-chosen token exists.** `UValue.declaredJavaType(String, String)`,
   `TypeProvenance.DECLARED` and `typeDeclarationReason()` are **deleted**. A token is `OBSERVED` —
   `observedFrom(Object)` reads `getClass().getName()` off the object a side returned — or `ASSUMED`, the
   factory default for the kind. The only setter, `UValue#observed(String)`, is private and reachable
   from `observedFrom` alone. `opaque(String, String)` still names a class, and is not a type-only
   channel: the name goes into `content()` as well, so an untruthful `opaque` token is a **content**
   difference and stays a `DIFFER`.
2. **A type-only difference is `AGREE`, and counted.** `Result.javaTypeMismatchCount()`,
   `# rows.javaTypeMismatch`, `# op.<key>.javaTypeMismatch`, and `, javaTypeMismatch=N` in `summary()` —
   the same shape as `throwClassMismatchCount()`. The note still names both fully-qualified classes and
   **both** provenances. Content differences are untouched.
3. **S4 promotes it.** `stageStatement()` carries the figure unconditionally, so there is no way to
   render an agreement figure from `Result` without it — the same mechanism-not-a-convention argument as
   D-15's. `harness-contract.md` §7 carries the **dated REQUIREMENT (2026-08-17)** with its reason.
4. **The record is corrected, not silently edited.** §7's "the reason is printed into the note of any
   row the declaration moved" is quoted and marked refuted where it stood, in `harness-contract.md`,
   here, and in `foundation-verdict.md`.

### 10.9.3 The measurement — control, both readings, and the closure

One run, four subjects, 285 operations, 19 083 rows, seed 20260817. Pasted:

```
=== detection power: control (a perfect port) =====================
seed                 20260817
operations           285  (stage-shaped domains)
rows                 19083
measured rows        17199
agreement rows       17199
verdict tally        {AGREE=17199, BOTH_THREW=910, HARNESS_ERROR=883, UNMEASURABLE=91}
diverging operations 0   <- MUST be 0, or nothing below is attributable to a planted defect
stage passes         74 of 285  (isStagePass(1, none()))
why a PERFECT port is refused elsewhere:
    0 PASS   74
    2 refused: rows disagreed   41
    3 refused on more than one clause   51
    4 refused: not discriminating (D-15)   119
distinct throw-pairs a PERFECT port produces  154
===================================================================

=== D-43: two readings of the same measurement ====================
  subject                              DIFFER     divOps   passes   typeMism notes ASSUMED
  P0-perfect                                0          0       74          0            0
  P12-boxed-primitive                       0          0       74       3445            0
  P13-factory-typed-adapter                 0          0       74       3445         3445
  P14-observing-adapter                     0          0       74          0            0
  stage passes the port with a DEFECT loses    0
  stage passes the port with NO defect loses   0   <- was 29 before round 8; the false-divergence mode
  operations carrying a java-type mismatch:
      P12 182   P13 182   P14 0
===================================================================
```

**Side by side, the number the brief asked for.** The content-perfect, factory-typed adapter — the
false-divergence mode — measured `DIFFER 3 445 / 182 operations / 45 passes (29 lost)` before round 8 and
measures **`DIFFER 0` / 0 diverging operations / 74 passes / `javaTypeMismatch` 3 445** after it. The
3 445 rows did not disappear; they changed dimension. The perfect-port control is unmoved in both.

And the row still carries everything it carried, including which side looked:

```
0  BooleanValue.compareTo(value)  BOOLEAN(true)@BooleanValue | BOOLEAN(true)@BooleanValue
   INTEGER(0)@Integer  INTEGER(0)@IntegerValue  AGREE
   java type mismatch: reference returned java.lang.Integer (INTEGER(0)@Integer) / subject returned
   org.tzi.use.uml.ocl.value.IntegerValue (INTEGER(0)@IntegerValue); the content is IDENTICAL --
   right content, wrong Java type (defect D-18). This row is scored AGREE and counted in
   rows.javaTypeMismatch, not scored as a divergence: at S1 the ported side's class cannot be
   authentically observed, because no ported value class exists to observe, so a type-only difference
   measures the adapter and not the port (D-43). Provenance: reference OBSERVED, subject ASSUMED
   (OBSERVED = read off the object that side returned; ASSUMED = the factory default for the kind,
   which is wrong for 182 of 285 operations). The subject's adapter never looked at what its
   implementation returned, so this difference is a finding about the ADAPTER and not about the port
   (D-43); an adapter must attribute through UValue.observedFrom(Object).
```

The same row from **P12**, the genuinely wrong-class port, is identical except for the provenance
clause — `subject OBSERVED`, no adapter hedge, and "whether the object the subject observed is the one
its implementation returned is not checkable by this harness" in place of round 7's certification. **That
clause is the only discriminator between the two 3 445s**, which is precisely the argument for §7's
requirement rather than a reason to withhold it.

### 10.9.4 The escape hatch is gone — the API, not a grep

```
$ grep -rn "declaredJavaType\|asJavaType\|TypeProvenance.DECLARED" \
      use-core/src/test/java/org/tzi/use/uncertainty/differential/*.java
   ... 11 hits, every one inside a comment or Javadoc recording the history. No call sites.
```

A grep is not a mechanism, so the property is asserted against the API instead, in
`theTypeTokenIsObservedOrAssumedAndNoApiTakesOne`:

```
=== D-43: every public UValue member that accepts a String ========
  opaque[class java.lang.String, class java.lang.String]
  string[class java.lang.String]
  uString[class java.lang.String, double]
===================================================================
```

`assertEquals(Set.of("uString", "string", "opaque"), …)` — a new public `UValue` member taking a String
fails this test. All three take **content**: `uString`/`string` take the payload, and `opaque`'s class
name is written into `content()` as well as `javaType()`, asserted directly
(`opaque("a.b.C","x").content() != opaque("d.e.C","x").content()`). The compile-level argument is
therefore complete: the only route to an `OBSERVED` token is `observedFrom(Object)`, whose parameter is
an object and not a name; the only other state is the factory's; `TypeProvenance` is asserted to have
exactly `OBSERVED` / `ASSUMED` / `NONE`.

### 10.9.5 Content detection — every probe intact

Eleven content probes, re-measured; every figure equals round 7's.

| probe | DIFFER | ops detected | stage passes (control 74) |
|---|---|---|---|
| P0-perfect (control) | 0 | 0 | 74 |
| P1-off-by-one-index | 52 (+86 `MIXED`) | 3 | 74 |
| P2-linear-uncertainty | 468 | 4 | 70 |
| P3-hypot-uncertainty | 24 | 4 | 70 |
| P4-le-for-lt | 280 | 6 | 70 |
| P5-round-10dp | 428 | 7 | 67 |
| P6-equals-ignores-uncertainty | 1119 | 4 | 72 |
| P7-undefined-on-zero-divisor | 105 (+62 `MIXED`) | 6 | 71 |
| P8-hides-behind-harness-error | 0 | 0 | 70 |
| P9-hides-behind-unsupported | 0 | 0 | 70 |
| P10-narrow-input-window | 0 | 0 | 74 |
| P11-negative-zero-collapse | 59 | 3 | 71 |

```
=== planted defects the harness did NOT see =======================
  ??? P11-negative-zero-collapse / URealValue.round()  [STAGE PASS]
===================================================================
```

Rows are 19 083 for every probe. The blind-spot set is still exactly one entry. **No content probe
regressed.** P12, the type probe, is the one that moved, by design, and is reported in §10.9.3.

### 10.9.6 What the demotion cost, measured rather than argued

`UnwrittenPortInvariantTest`'s receiver-echoing subject (`return args.get(0)`) hands back a factory-typed
corpus value, so it is exactly a non-attributing adapter. Its numbers moved:

```
  measured after round 8, pasted:
    verdict tally    {AGREE=4951, DIFFER=63045, HARNESS_ERROR=618462, MIXED=39880}
    javaTypeMismatch 384  <- agreement rows on which only the Java class differed (D-43)
```

Round 7's refutation §9 records this subject's agreement rows as **4 567** (`f-echoes-receiver` 4567,
`e-fixed-constant` 8240). `4 951 − 4 567 = 384`, which is exactly the `javaTypeMismatch` figure — so the
whole of the change in this subject's agreement count is the demoted population and nothing else. (The
4 567 is quoted from that report, not re-measured here; the 4 951 and the 384 are from the run above.)

Five operations become fully agreed again — `BooleanValue.value()`, `BooleanValue.isTrue()`,
`IntegerValue.value()`, `StringValue.value()` in the discriminating bucket and `RealValue.value()` in the
degenerate one (single-valued, because the corpora hold exactly one `RealValue` — D-28). **All five are
signed off with the reason, and the sign-off is not prose:** for every entry of either reviewed set the
test now asserts that **all** of that operation's agreement rows are java-type mismatches, so if any part
of the agreement ever stops being explained by the class difference the sign-off fails. The
`e-fixed-constant` subject is unaffected (`AGREE=8240`, `javaTypeMismatch 0`), because a `UBOOLEAN`
constant differs from a raw `boolean` in **content**.

That is the honest cost: **verdict-level resolution against a non-attributing subject on five
operations**, in exchange for a faithful port no longer losing 29 stage passes to its adapter, and it is
what the S4 requirement reverses.

### 10.9.7 Acceptance

```
$ pgrep -f '[m]vn|[m]aven'      # clear of the other session's loop before each run
$ mvn -q clean && mvn -B verify -Djava.awt.headless=true
[INFO] use ................................................ SUCCESS [  0.002 s]
[INFO] use-core ........................................... SUCCESS [01:24 min]
[INFO] use-gui ............................................ SUCCESS [ 31.127 s]
[INFO] use-assembly ....................................... SUCCESS [  6.719 s]
[INFO] BUILD SUCCESS
```

Aggregated from the surefire/failsafe XML, not from the console:

```
surefire: files=8 tests=79 failures=0 errors=0 skipped=0
failsafe: files=2 tests=130 failures=0 errors=0 skipped=0
```

**79 + 130 = 209 methods, 0 failures.** Delta from round 7 is **0**: three test methods were renamed to
say what they now measure (`aWrongJavaTypeWithRightContentIsCountedNotScored`,
`aFactoryTypedAdapterCostsNoPassAndIsCountedNotScored`,
`theTypeTokenIsObservedOrAssumedAndNoApiTakesOne`) and none was added or removed.

Determinism, two full `verify` runs:

```
$ for f in verify1 verify2; do sed -n '/=== detection power: control/,/^ *====*$/p' $f.log | md5sum; done
c724bd19dbed9071ffc8762675584107  -
c724bd19dbed9071ffc8762675584107  -
$ for f in verify1 verify2; do sed -n '/=== D-43: two readings/,/^ *====*$/p' $f.log | md5sum; done
987f3210a74856c54ade76c9d70eceee  -
987f3210a74856c54ade76c9d70eceee  -
$ git diff --name-status 30d480db..HEAD -- '*/src/main/*'
[EMPTY]
```

The control block's digest `c724bd19…` is **byte-identical to round 7's**, which is the strongest single
statement that the control did not move.

**Goldens refreshed deliberately.** Two header lines added per file, both `0`; not one data row changed,
and the faulty-minus file's 558/226 split is unchanged:

```
$ git diff -- docs/port2/differential/
+# rows.javaTypeMismatch	0
+# op.URealValue.add(value).javaTypeMismatch	0
+# rows.javaTypeMismatch	0
+# op.URealValue.minus(value).javaTypeMismatch	0
```

### 10.9.8 What round 8 does NOT claim

* **Not that the Java type does not matter.** It claims the harness cannot attribute a type-only
  difference while the ported side has nothing to observe, and it makes that a dated obligation instead
  of a permanent softening.
* **Not that D-48 is dissolved.** A non-attributing adapter still produces the same 3 445 for a perfect
  port and for a real wrong-class infidelity. What is removed is the part that misled a reader into
  discarding real findings on the strength of a hedge; what remains is named as a limit in three places.
* **Not that `observedFrom` is checkable.** It believes any object it is handed (D-47). The note now says
  so instead of certifying the opposite, and the incentive to fabricate is gone because a type-only
  difference no longer costs anything.
* **Not that S1 measures type fidelity.** It measures and publishes a type-mismatch count. Whether that
  count means anything about a port is S4's to establish, by observing.

---

## 10.10 Round 8's refutation — `SOUND_WITH_DOCUMENTED_LIMITS`, and D-52 (`c91277ff`)

**Written by the refuter, who owned Maven for the round; every block below is pasted from
[`stage-01-verification-round8.md`](stage-01-verification-round8.md) and is quoted here, not re-measured.**
Where this section and §10.9 disagree, **this section is authoritative**, because it is the independent
measurement of §10.9's claims.

### 10.10.1 What was confirmed

* **The control is intact and byte-identical to round 7's** — same 285 operations, 19 083 rows, 17 199
  measured and agreed, `{AGREE=17199, BOTH_THREW=910, HARNESS_ERROR=883, UNMEASURABLE=91}`, 0 `DIFFER`,
  0 `MIXED`, 0 diverging operations, 74 stage passes, `javaTypeMismatch` 0;
  `md5 c724bd19dbed9071ffc8762675584107` on the printed block, the same digest §10.9.7 published, from
  three independent extractions. **Reproduced from the refuter's own sweep rig** over the same operations
  and domains: `rows=19083 measured=17199 agreed=17199 DIFFER=0 MIXED=0 typeMism=0 divOps=0 passes=74`.
* **Every content probe P1–P11 unchanged** from round 7 in `DIFFER`, `MIXED`, detected-operation **set**
  (pasted verbatim in that report §3) and stage passes; 19 083 rows on every probe; blind-spot set still
  exactly `{P11-negative-zero-collapse / URealValue.round()}`. **8 of 11 caught on every affected
  operation**, three misses named (P8/D-17, P9/D-17, P10/D-30).
* **The false-divergence mode is gone**, independently: P13 (content-perfect port, factory-typed adapter)
  reaches the control's exact stage-pass set with 0 `DIFFER`; P14 (same port, observing adapter) is
  row-for-row the reference. `stage passes the port with NO defect loses 0 <- was 29 before round 8`.
* **The demotion swallowed no content difference**, constructed at sweep scale rather than asserted in a
  unit test:

```
  R3a-content-defect only (P2 shape)  rows=19083 measured=17199 agreed=16731 DIFFER=468 typeMism=0    divOps=4 passes=70
  R3b-content defect + wrong class    rows=19083 measured=17199 agreed=16731 DIFFER=468 typeMism=3445 divOps=4 passes=70

  R3c-type only                        DIFFER=0    typeMism=3445 typeOps=182 divOps=0   passes=74
  R3c-content AND type on the same rows DIFFER=1831 typeMism=1883 typeOps=42  divOps=143 passes=71
```

  Identical `DIFFER` and diverging-operation counts when a type difference is added on top; and when the
  content defect is placed **on the raw-returning operations themselves — the exact rows the type
  difference lives on — `DIFFER` goes 0 → 1 831 across 143 operations and three stage passes are lost.**
  Structural reason, checked rather than assumed: `DifferentialSweep.classify` (~`:243-270`) demotes only
  when `ref.content().equals(sub.content())`, and `canonical() = content() + "@" + typeToken()`.
* **The count is accurate**, three independent recounts agreeing on all six of the refuter's sweeps
  (`javaTypeMismatchCount()`, a recount over rendered rows, and the suite's two per-operation recounts).
  Hand-verified: 401 across nine named operations; 3 445 across 182; `BooleanValue.value()` 2 of its 2 rows,
  columns `BOOLEAN(x)@Boolean` vs `BOOLEAN(x)@BooleanValue`.
* **No false green** in five of its own constructions plus the suite's eleven, and **no subject can exceed
  the control's 74**: clause 2 refuses any operation carrying a `BOTH_THREW` / `HARNESS_ERROR` /
  `UNMEASURABLE` row and clause 3 is computed from the **reference's** distinct values, so every subject's
  pass set is a subset of one fixed by the reference alone.
* **Closed independently: D-43 half (a) and half (b), D-46, D-47, D-51.** Correctly recorded as open and
  untouched: **D-29, D-30, D-48, D-50.**

### 10.10.2 D-52 — the decisive construction

One port with a **real** wrong-class defect (content-perfect on every row, answering a raw boxed primitive
where the historical answers an `org.tzi.use.uml.ocl.value.*` object — the 401-row / 9-operation infidelity
§10.9 quotes from round 7), and two adapters differing by **one line**:

```
=== R1d: ONE port with a REAL wrong-class defect, TWO adapters =====
  R0-control (perfect port)        DIFFER=0    MIXED=0    typeMism=0     ops=0    passes=74
      rows carrying a type clause 0
  A: honest, observes its object   DIFFER=0    MIXED=0    typeMism=401   ops=9    passes=74
      rows carrying a type clause 401  (of which 'not checkable by this harness' 401)
      operations reported wrong-class: [UBooleanValue.equalsC(value,double), UBooleanValue.toBoolean(),
      UIntegerValue.toInteger(), UIntegerValue.toReal(), URealValue.toInteger(), URealValue.toReal(),
      UStringValue.at(int), UStringValue.toBoolean(), UStringValue.uToString()]
  B: same port, stand-in laundering DIFFER=0    MIXED=0    typeMism=0     ops=0    passes=74
      rows carrying a type clause 0
  B tally == control tally?      true
  B stage passes == control's?   true
===================================================================
```

and the whole dimension erased with nineteen empty stand-in classes, whose names the harness prints itself
in its own representation census:

```
  R1a-factory-typed, honest omission  ... typeMism=3445 typeOps=182 ... anyTypeClause=3445
  R1c-stand-in laundering             ... typeMism=0    typeOps=0   ... anyTypeClause=0
  R1c verdict tally == control?  true      R1c stage-pass set == control? true
  R1c summary of one op: BooleanValue.value(): 2 rows, 2 measured, 2 agreed, 0 disagreed, 0 java-type mismatch(es), 2 distinct reference value(s) [DISCRIMINATING]
  R1a summary of one op: BooleanValue.value(): 2 rows, 2 measured, 2 agreed, 0 disagreed, 2 java-type mismatch(es), 2 distinct reference value(s) [DISCRIMINATING]
```

The last two lines are the finding in one place: **`stageStatement()` — the exact line §7's reviewer check
says to read verbatim — reads `0 java-type mismatch(es)` for the laundering adapter and `2` for the honest
one, on the same operation with the same content.** The naive four-line version ("observe the boxed
primitive, not the object") already reaches 3 445 → 401 with no census at all, so **the gradient starts
shallow**; an author reducing 3 445 red figures to 401 will not stop at 401. Round 7's laundering and round
8's differ in one respect that is not in round 8's favour: round 7's laundered rows carried a provenance
that **named the act** (`DECLARED`); round 8's report **`OBSERVED`**.

Side by side:

| | round 7 `declaredJavaType(refToken,"x")` | round 8 `observedFrom(standIn)` |
|---|---|---|
| lines of adapter code | 1 | 1 |
| finding erased | 3 445 `DIFFER` / 182 ops | 401 `javaTypeMismatch` / 9 ops |
| resulting sweep | byte-identical to control | byte-identical to control |
| rows with the mandated disclosure | 0 | 0 |
| provenance reported | `DECLARED` | **`OBSERVED`** |

**Reflective routes, reported only because "there is no route" had been asserted:** `setAccessible(true)`
+ `Field.set` on the private final `javaType` **succeeds** (test code, no module barrier) and yields a
matching token with provenance still `ASSUMED` — an `AGREE` row with an empty note; `VarHandle.set` is
refused. Neither matters, because `observedFrom(anyObject)` is public and innocent-looking. Subclassing
`Candidate` adds nothing.

**Verdict on D-52: MAJOR, open, and INERT AT S1** — 74 stage passes either way, so no S1 figure moves. The
target is `harness-contract.md` §7's dated obligation, and the fix is the **shape**, not a fourth API. Both
sentences the refuter asked for are now in §7 (the seam, and the reviewer check on shape rather than on
prose) and the checklist is §8.

### 10.10.3 The four MINORs and the two latent holes

D-53 (the "no agreement figure without the count" overclaim), D-54 (the count is not monotone in wrongness:
3 445/182 → 1 883/42), D-55 (`OPAQUE` content is a non-injective concatenation; 0 of 197 rows reachable
today), D-56 (a nested type-only difference is still a `DIFFER`; 0 `DIFFER` over 17 `SequenceValue` rows),
D-57 (the demotion's gate-level cost: **29** passes for a wrong-class or unattributed port, with the 29
operations named, and **4** for a receiver-echoing subject, all four on discriminating accessors). Full
statements in §10.4.

### 10.10.4 Acceptance and process, as the refuter reported them

`mvn -q clean && mvn -B verify -Djava.awt.headless=true` → `BUILD SUCCESS`, **79 surefire (78 use-core +
1 use-gui) + 130 failsafe (1 `OCLExpressionIT` + 129 `ShellIT`) = 209 methods, 0 failures / 0 errors /
0 skipped**, delta 0 against the stated baseline; two runs byte-identical outside Maven's own logging
(`md5 919997f36959cf8cc6f8af4a64030ecd` over the full stripped output); `git status --porcelain` empty;
`git diff --name-status 30d480db..HEAD -- '*/src/main/*'` empty; the golden refresh exactly two header keys
per file and nothing else, with the faulty golden's 226 arithmetic `DIFFER` rows unchanged and 0 type
clauses. **Maven contention (ground rule 5):** the other session's loop shell was present throughout
(PIDs 494057, later 1380275/1380299 — its command line contains `mvn -B clean test`, which is why `pgrep`
matches it while it only sleeps), **its output log does not exist, so its run never fired**; two consecutive
`verify` runs gave identical totals and byte-identical evidence and no "module not found: use.core"
appeared, so no figure here is a phantom count. The refuter's four scratch probes (`Round8Refuter`,
`…B`, `…C`, `…D`) live outside the repository and are not committed.

---

# 11. APPENDIX — amendment 5 (the D-15 fix), as originally written

> **This section is the evidence for the D-15 fix, not the verdict.** It was written on 2026-08-17
> as the authority of record; it is now an appendix to §10, which is the consolidated record and is
> authoritative wherever the two disagree. Its numbering is unchanged — `§11.1`, `§11.2`, `§11.4.1`
> and `§11.6` are cited by the round-5 reports — and its content is unchanged apart from two
> corrections annotated in place: the register in **§11.2 is superseded by the register in §10.4**
> (it predates round 5 and carries no D-29…D-32), and the pasted block in **§11.4.1 is
> misattributed**, as the round-5 static review found (R5-1); the correction box there says what
> actually produced it. §12.5 ("what IS sound") is unchanged and still holds.

Behaviour commit `0a93ad4f`, test-scoped. `git diff --name-status 30d480db..HEAD -- '*/src/main/*'`
is empty.

---

## 11.0 The one-sentence change

> The harness now **computes**, for every operation, how many distinct values the reference side
> produced — and **refuses to call a sweep a pass** when that number is one, unless a human has
> signed the operation off by name and by value with a written rationale that is copied into the
> report. The rule that was written in `harness-contract.md` as discipline is now a mechanism.

---

## 11.1 The canonical defect register, and the ID collision that made one necessary

Round 4 produced two independent reports which both used `D-16`…`D-19` for different defects, and
`D-15` denoted **both** a round-3 MINOR (the golden comparison was line-based, not byte-based) and
the round-4 CRITICAL (degenerate codomain). Two defects sharing a key is how evidence gets orphaned.

**The rule from here on.** A bare `D-nn` always means the canonical register in §11.2. A
report-local id must be cited with its report, e.g. *static-review-round4 `D-17`*. Nothing in any
round report was rewritten; the mapping below is how they are read.

### Mapping — every historical id to its canonical key

| Source | local id | canonical | defect |
|---|---|---|---|
| `stage-01.md` §8.2 (round 3) | `D-15` | **D-26** | `assertMatchesGolden` compared lines, not bytes |
| `stage-01.md` §8.2 (round 3) | `D-16` | **D-27** | the `MIXED` note did not name which side threw |
| `stage-01.md` §8.2 (round 3) | `D-10`…`D-14`, `D-11a/b/c`, `D-12a/b`, `D-13a/b` | unchanged | — |
| `stage-01-verification-round4.md` | `D-15` | **D-15** | degenerate codomain scored as fidelity |
| `stage-01-verification-round4.md` | `D-16` | **D-16** | `isClean()` has no coverage floor |
| `stage-01-verification-round4.md` | `D-17` | **D-17** | a subject controls its own denominator |
| `stage-01-verification-round4.md` | `D-18` | **D-18** | the canonical-form collision, 193 of 285 |
| `stage-01-verification-round4.md` | `D-19` | **D-19** | coverage: 61 operations measure nothing |
| `stage-01-static-review-round4.md` | `D-15` | **D-15** | same defect, static route (91 constant-`false` ops) |
| `stage-01-static-review-round4.md` | `D-16` | **D-20** | `everyKindIsEither…` is tautological |
| `stage-01-static-review-round4.md` | `D-17` | **D-21** | writer guard and headers are file-level totals |
| `stage-01-static-review-round4.md` | `D-18` | **D-22** | `UNSUPPORTED` note asserts "could be driven" |
| `stage-01-static-review-round4.md` | `D-19` | **D-23** | `unmeasurableNote` void-ness from a disjunction |
| `stage-01-static-review-round4.md` | `D-20` | **D-24** | §5.2 pasted a superseded header |
| `stage-01-static-review-round4.md` | `D-21` | **D-25** | `AcceptedThrowPairs` "plain ASCII" comment |
| this section | — | **D-28** | the corpora contain exactly one `RealValue` |

`D-26` and `D-27` are the two round-3 MINORs given fresh keys at the end of the register rather than
left colliding with round-4's. Both are now **closed** (§11.5). `D-28` is new and is **open**
(§11.6).

---

## 11.2 Register — state after this amendment

> **SUPERSEDED by §10.4.** This table is the round-4 register as it stood after the D-15 fix
> and before round 5. It is correct as of `0a93ad4f` and is kept for that reason, but it carries
> nothing from D-29 onwards — not D-29…D-32 (round 5), not D-33…D-42 (round 5's static review), not
> **D-43…D-45** (round 6's refutation) — its D-17 entry is narrowed by D-32, and its **D-18 entry is
> closed** (round 6, with D-43 open in its place). Read §10.4.

| Key | Sev | State | Defect and what changed |
|---|---|---|---|
| **D-15** | CRITICAL | **CLOSED** | A degenerate codomain scored as fidelity. `Result.distinctReferenceValues()` computes it, the report header publishes it per operation, and `Result.requireStagePass` refuses it. §11.3, §11.4. |
| D-16 | MAJOR | **CLOSED for the stage gate** | `isClean()` still has no floor and is unchanged — deliberately, the regression tests need exactly that question. `isStagePass(int, …)` takes a mandatory floor and **rejects a floor of zero**, so the stage-facing predicate cannot be satisfied by one row unless the caller writes "1" down. `isClean()`'s Javadoc now states it is not a pass predicate. |
| D-17 | MAJOR | **open** | A subject that raises `HarnessMarshallingException` everywhere shrinks its own `driven` denominator. Not addressed here. **Partly mitigated, not fixed:** the stage gate's discrimination clause is computed from measured rows, so a subject hiding behind `HARNESS_ERROR` now also drives `distinctReferenceValues()` to 0 and fails clause 3 as well as clause 1. The *per-operation invariant predicate* is still vulnerable. |
| D-18 | MAJOR | **open, and larger than it was** | The primitive/boxed canonical collision. Widening the corpora made three more instances visible and they are now on the reviewed allowlist with written reasons: `BooleanValue.value()`, `BooleanValue.isTrue()`, `StringValue.value()`. The 193-of-285 figure stands. |
| D-19 | MINOR | **CLOSED** | 61 operations measured nothing; now **11**, and all 11 are declared limits rather than gaps. §11.4. |
| D-20 | MAJOR | **open** | `everyKindIsEitherAnObservationOrUnmeasurable` is tautological. Not addressed. The non-circular criterion the static reviewer proposed — a value-carrying kind has at least two distinguishable inhabitants — is still the right fix and is still unwritten. |
| D-21 | MAJOR | **CLOSED for the header, open for the guard** | Every `# rows.*` line is still a sum, and `writeAll`'s measurement guard is still a total. But the header now carries a per-operation block, so no number in the file is unattributable. The guard remains file-level. |
| D-22 | MINOR | **open** | `UNSUPPORTED` note's "could be driven". Latent until S4. |
| D-23 | MINOR | **open** | `unmeasurableNote` derives void-ness from a disjunction. Latent. |
| D-24 | MINOR | annotated | §5.2's superseded header, corrected in place. |
| D-25 | MINOR | **open** | `AcceptedThrowPairs` says "plain ASCII" and holds fifteen non-ASCII bytes. **`AcceptedDegenerateOperations`, added by this amendment, does not repeat the claim** — its separator is an escape and its comment says only that, which is true. |
| D-26 | MINOR | **was already fixed, now PINNED** | §11.5. |
| D-27 | MINOR | **CLOSED** | §11.5. |
| D-28 | MINOR | **open, new** | §11.6. |
| D-14 | MINOR | **open for `AcceptedThrowPairs`** | No `# accepted.*` header for throw-pairs. The new `AcceptedDegenerateOperations` does emit `# accepted.degenerateOperations` **even when zero**, so the same hole was not dug twice; `AcceptedThrowPairs` still has it. |
| D-9 | MINOR | declared boundary | Coverage: `SBooleanValue`, collections, `ocl.type.*`, `uDataTypes.*`. |

---

## 11.3 What was built (D-15)

### 11.3.1 The quantity — `DifferentialSweep.Result`

```java
public java.util.SortedSet<String> referenceValues()   // reference's canonical forms, MEASURED rows
public int     distinctReferenceValues()               // its size
public String  soleReferenceValue()                    // the one value, or null
public boolean isDiscriminating()                      // >= DISCRIMINATING_MINIMUM (2)
```

Counted over `measurements()` — `AGREE` + `DIFFER` — because those are exactly the rows an agreement
figure can come from. `summary()` carries the number, so `30 rows, 30 measured, AGREE=30` is no
longer expressible; it now reads `30 rows, 30 measured, 1 distinct ref, AGREE=30`.

Pinned by `distinctReferenceValuesCountsTheReferenceOverMeasuredRows`, which asserts the two
mistakes that would make the statistic useless are not made: counting the **subject's** column, and
counting over **all** rows (169 `HARNESS_ERROR` rows carry distinct marker strings and would have
looked richly discriminating — measured as `0`).

### 11.3.2 The gate — a mechanism, not a convention

```java
public boolean isStagePass(int minimumMeasurements, AcceptedDegenerateOperations acknowledged)
public Result  requireStagePass(int minimumMeasurements, AcceptedDegenerateOperations acknowledged)
public List<String> stageGateFailures(int, AcceptedDegenerateOperations)   // every failing clause
public String  stageStatement(AcceptedDegenerateOperations)                // the line a stage prints
```

Three clauses: a measurement floor the caller must state (`0` throws `IllegalArgumentException` —
a floor of zero is not a floor), no disagreements, and `distinctReferenceValues() >= 2` **or** a
sign-off. `AcceptedDegenerateOperations` mirrors `AcceptedThrowPairs`: opt-in, `none()` by default
and never supplied implicitly, a mandatory non-blank rationale, and a key that includes **the single
canonical value** so a sign-off lapses by itself the moment the operation stops answering what was
reviewed. The rationale is copied into `stageStatement` and into the report header.

`isClean()` was **not** changed. Its Javadoc now says outright that it is not a pass predicate and
names D-15 as the reason. The harness's own regression tests need exactly the question `isClean()`
asks, because the codomain of a synthetic two-candidate sweep is known by construction.

Pinned by `theStageGateRefusesADegenerateOperation` (six scenarios, both directions, both key
positions of the sign-off) and `aDegenerateOperationNeedsAWrittenSignOff`.

### 11.3.3 The number reaches the artefact

```
# rows.disagreement	0
# op.URealValue.add(value).distinctReferenceValues	1
# op.URealValue.add(value).discriminating	false
# op.URealValue.add(value).soleReferenceValue	UREAL(2.0,0.0)
# op.URealValue.add(value).degenerate.acknowledged	reviewed: one-point domain
# accepted.degenerateOperations	1
```

Pinned by `theReportHeaderCarriesDiscriminatingPowerPerOperation`, which asserts
`# rows.disagreement 0` **first**, as the precondition — that is what the file used to say and all it
used to say.

---

## 11.4 The measurement, before and after — pasted output

Both from `mvn -B verify`, seed `20260817`, stage-shaped: each operation swept over **its own
receiver type's corpus**, which is what an S4 stage will do. The union-domain sweep the parameterised
invariant runs is the right shape for cross-type defects and the wrong shape for "would a stage read
this as a pass?" — every row of it carries `HARNESS_ERROR` receivers.

**BEFORE** (identical code, `booleanCorpus`/`stringCorpus` removed from `corpora()`):

```
corpora                    uReal=24, uInteger=15, uBoolean=11, uString=18, zeroDivisors=7, indexBoundaries=8; receivers=77
operations                 285
literals the subject holds 224  (one per operation the reference ever answered with a value)
codomain census            285 operations: 61 measured nothing, 121 single-valued (NOT DISCRIMINATING), 103 discriminating
isClean() AND degenerate   81   <- the size of the door: a stage asserting isClean() reads these as PASS
refused by the stage gate  81 of 81
stage passes (must be 0)   0
```

**AFTER** (`booleanBoundaries()` and `stringBoundaries()` in the corpora):

```
corpora                    uReal=24, uInteger=15, uBoolean=11, uString=18, boolean=4, string=16, zeroDivisors=7, indexBoundaries=8; receivers=94
operations                 285
literals the subject holds 274  (one per operation the reference ever answered with a value)
codomain census            285 operations: 11 measured nothing, 159 single-valued (NOT DISCRIMINATING), 115 discriminating
isClean() AND degenerate   119   <- the size of the door: a stage asserting isClean() reads these as PASS
refused by the stage gate  119 of 119
stage passes (must be 0)   0
```

| quantity | before | after |
|---|---|---|
| operations with **zero measurements** (D-19) | **61** | **11** |
| operations **single-valued** (D-15 population) | **121** | **159** |
| operations **discriminating** | **103** | **115** |
| operations the constant-literal subject makes `isClean()` **and** degenerate | **81** | **119** |
| of those, **refused by the stage gate** | 81 of 81 | **119 of 119** |
| **stage passes** reached by a port containing no logic | **0** | **0** |

**Round 4 predicted the +38 and it arrived.** *"Widening the corpora to reach them widens the D-15
population by roughly 40 more"* (`stage-01-verification-round4.md` §7). It is the correct trade only
because the labelling landed in the same commit: 50 more operations are measured, and every one of
them that cannot vary says so in its own header line.

**The 11 that still measure nothing are limits, not gaps** — 8 declared-`void` mutators (a scope
boundary recorded since §6) and three operations that throw on every input the corpora hold:

```
  ... BooleanValue.setTypeToRuntimeType()   2 rows, 0 measured, 0 distinct ref, MIXED=2
  ... IntegerValue.setTypeToRuntimeType()   8 rows, 0 measured, 0 distinct ref, MIXED=8
  ... RealValue.setTypeToRuntimeType()      1 rows, 0 measured, 0 distinct ref, MIXED=1
  ... StringValue.setTypeToRuntimeType()   15 rows, 0 measured, 0 distinct ref, MIXED=15
  ... UBooleanValue.setTypeToRuntimeType() 11 rows, 0 measured, 0 distinct ref, MIXED=9, HARNESS_ERROR=2
  ... UIntegerValue.setTypeToRuntimeType() 15 rows, 0 measured, 0 distinct ref, MIXED=15
  ... URealValue.setTypeToRuntimeType()    24 rows, 0 measured, 0 distinct ref, MIXED=24
  ... UStringValue.setTypeToRuntimeType()  18 rows, 0 measured, 0 distinct ref, MIXED=17, HARNESS_ERROR=1
  ... UIntegerValue.power(value)          225 rows, 0 measured, 0 distinct ref, BOTH_THREW=225
  ... UStringValue.toInteger()             18 rows, 0 measured, 0 distinct ref, BOTH_THREW=17, HARNESS_ERROR=1, throwClassMismatch=17
  ... UStringValue.toReal()                18 rows, 0 measured, 0 distinct ref, BOTH_THREW=17, HARNESS_ERROR=1, throwClassMismatch=17
```

### 11.4.1 The 120-literal subject, before and after

The attack: a `Candidate` whose every body is one hardcoded literal looked up by operation key — no
arithmetic, no branching, the receiver and the arguments are never read. The literals are obtained by
asking the historical oracle once per operation, which is mechanically what a porter typing them out
by hand would produce, and keeps the attack correct as the corpora move.

**BEFORE** (what §12.3 recorded, reproduced here stage-shaped): fully agreed on the single-valued
operations, **81** of them producing a fully measured, zero-disagreement, report-writable sweep with
`isClean() == true` and a header reading `# rows.disagreement 0`. Nothing in the tree objected.

**AFTER**: the same subject, the same operations, more of them (119). Every one of the 119 is
refused, and the refusal names the reason. **The refusal below is the shape of that message, not an
instance from this run — correction, 2026-08-17:**

> **CORRECTION (R5-1, round-5 static review).** The block that follows is genuine output, but it was
> **not** produced by `aNoLogicPortCannotProduceAStagePass` and it is **not** one of the 119. It is
> the one-row stub-vs-stub sweep in
> `DifferentialHarnessRegressionTest.theStageGateRefusesADegenerateOperation` — that is the only
> place in the tree where `UREAL(2.0,0.0)` is the sole reference value
> (`DifferentialHarnessRegressionTest.java:754`, `:786`, `:821`), and
> `UnwrittenPortInvariantTest.java:692-693` asserts on the refusal message rather than printing it.
> `URealValue.add(value)` has 576 rows and 164 distinct reference values in the run being described,
> as the CONTROL line nine lines below says, so it cannot be one of the 119. **What the block
> demonstrates is the wording and structure of a clause-3 refusal; the claim that all 119 are refused
> is carried by the assertion in that test, and was re-measured in round 5 ("refused by the stage
> gate 119 of 119", "stage passes (must be 0) 0").** The block is left in place rather than swapped,
> because a report that silently repairs its own evidence is worth less than one that says what
> happened.

```
sweep of URealValue.add(value) is not a stage pass:
  - the reference side produced 1 distinct value(s) across 1 measured row(s), always UREAL(2.0,0.0).
    This operation could not have failed over this domain, so agreement on it is decided before
    either implementation runs and is not evidence of fidelity (defect D-15). Either widen the
    domain until the reference answers differently, or sign the operation off in
    AcceptedDegenerateOperations with a written rationale -- which is copied into the report, so the
    weakness travels with the number.
  tally: URealValue.add(value): 1 rows, 1 measured, 1 distinct ref, AGREE=1
```

And the control, in the same test, so that a gate which simply refuses everything would fail it:

```
CONTROL, faithful port     URealValue.add(value): 576 rows, 576 measured, 576 agreed, 0 disagreed, 164 distinct reference value(s) [DISCRIMINATING]
```

`requireStagePass(100, none())` returns normally for that one. The sign-off route, both directions:

```
no sign-off   URealValue.isUReal(): 24 rows, 24 measured, 24 agreed, 0 disagreed, 1 distinct reference value(s) [NOT DISCRIMINATING: always BOOLEAN(true)]
signed off    URealValue.isUReal(): 24 rows, 24 measured, 24 agreed, 0 disagreed, 1 distinct reference value(s) [NOT DISCRIMINATING: always BOOLEAN(true); acknowledged: URealValue.isUReal() is a type predicate: the historical body is iconst_1/ireturn, so BOOLEAN(true) is the whole of its specification and no corpus can make it answer otherwise. Agreement here shows the operation exists and is reachable; it is not evidence about any computation.]
```

A sign-off written against `BOOLEAN(false)`, or against `URealValue.isDefined()`, does not match. A
blank rationale is rejected by the builder.

### 11.4.2 Rounds 1–3 are not regressed — the seven subjects, verbatim

```
subject              a-throws          rows 726338  measured 0      agreement 0
                     verdict tally     {BOTH_THREW=39880, HARNESS_ERROR=618462, MIXED=67996}
                     fully agreed ops, DISCRIMINATING  (none)     NOT DISCRIMINATING  (none)
subject              b-returns-java-null  rows 726338  measured 0   agreement 0
                     verdict tally     {HARNESS_ERROR=726338}
                     fully agreed ops, DISCRIMINATING  (none)     NOT DISCRIMINATING  (none)
subject              c-empty-body      rows 726338  measured 67268  agreement 0
                     verdict tally     {DIFFER=67268, HARNESS_ERROR=618462, MIXED=39880, UNMEASURABLE=728}
                     fully agreed ops, DISCRIMINATING  (none)     NOT DISCRIMINATING  (none)
subject              d-returns-null-value  rows 726338  measured 67268  agreement 0
                     verdict tally     {DIFFER=67268, HARNESS_ERROR=618462, MIXED=39880, UNMEASURABLE=728}
                     fully agreed ops, DISCRIMINATING  (none)     NOT DISCRIMINATING  (none)
subject              e-fixed-constant  rows 726338  measured 67996  agreement 8240
                     verdict tally     {AGREE=8240, DIFFER=59756, HARNESS_ERROR=618462, MIXED=39880}
                     fully agreed ops, DISCRIMINATING  (none)     NOT DISCRIMINATING  (none)
subject              f-echoes-receiver rows 726338  measured 67996  agreement 4951
                     verdict tally     {AGREE=4951, DIFFER=63045, HARNESS_ERROR=618462, MIXED=39880}
                     fully agreed ops, DISCRIMINATING
                       *** BooleanValue.isTrue()   (16/16 driven rows agreed, 752 rows total, 2 distinct reference value(s); reviewed and signed off)
                       *** BooleanValue.value()    (16/16 driven rows agreed, 752 rows total, 2 distinct reference value(s); reviewed and signed off)
                       *** IntegerValue.value()    (64/64 driven rows agreed, 752 rows total, 8 distinct reference value(s); reviewed and signed off)
                       *** StringValue.value()     (120/120 driven rows agreed, 752 rows total, 15 distinct reference value(s); reviewed and signed off)
                     fully agreed ops, NOT DISCRIMINATING  1 operation
                       --- RealValue.value()  (8/8 driven rows agreed, 752 rows total, 1 distinct reference value(s) -- always REAL(0.0) [NOT DISCRIMINATING])
subject              g-throws-error    rows 0  measured 0  agreement 0   ESCAPED -> the sweep ABORTED
```

**a, b, c, d and g still score zero agreement.** D1, D2 and D-10 stay closed: subject `c` still has
`agreement 0` with its `DIFFER` rows intact and its void rows on `UNMEASURABLE`. The row counts moved
(471 471 → 726 338) because the corpora are wider, and every proportion is unchanged in kind.

**Subject f is the interesting one and it is the mechanism working.** Its fully-agreed set went from
two operations to five, and the split says which kind of finding each is:

* Three are **new instances of D-18**, invisible before because `BooleanValue` and `StringValue` had
  no receiver corpus to be driven on. `javap -p` on the vendored `use.jar`:
  `public boolean BooleanValue.value()`, `public boolean BooleanValue.isTrue()` (body
  `aload_0; getfield fValue:Z; ireturn`), `public java.lang.String StringValue.value()`. Each is
  genuinely the receiver's own content, so echoing the receiver is the right answer — and each is
  also a place where a port returning the right content with the wrong Java type would be scored
  `AGREE`. All three are now on `ECHO_SUBJECT_REVIEWED` with that written down.
* One, **`RealValue.value()`, was removed from the sign-off list** and is not an improvement — see
  D-28 below.

---

## 11.5 D-26 and D-27 — the two round-3 MINORs

**D-26 (golden comparison compared lines, not bytes).** **It was already fixed**, in `93e038ac`, and
`stage-01-static-review-round4.md` §1.5 says so — it records `Arrays.equals(readBytes, readBytes)` at
`DiffReportWriter.java:281`. §8.2's entry above ("re-confirmed unfixed") was true when round 3 wrote
it and was superseded by the next behaviour commit; the open-defect list carried into this round
still named it, and that is the error being corrected. **Verified before touching anything**:

```
$ git log --oneline -S"Arrays.equals(readBytes" -- '*DiffReportWriter.java'
93e038ac S3 fix: a VOID is not an observation, and rows are not measurements
```

What *was* genuinely missing is that **nothing tested it**, so the correction could have been
reverted silently. `goldenComparisonIsBytesAndNotLines` now asserts, in order: identical bytes match;
a golden differing **only in its trailing newline** fails, with `Files.readAllLines` on both files
asserted **equal first** as the precondition; and a CRLF-for-LF substitution fails too.

```
differential report .../d-byte-probe.tsv differs from the committed golden .../d-byte-probe.tsv in
bytes but not in any line: the files disagree only about line terminators or a trailing newline. A
line-based comparison would have called these two files equal.
```

**D-27 (the `MIXED` note did not name which side threw).** 67 996 rows of the invariant sweep.
Closed: the lead clause is now `the reference threw and the subject returned.` or
`the subject threw and the reference returned.`, and `mixedNoteNamesBothSides` asserts both
directions plus the absence of the old unattributed phrasing.

---

## 11.6 D-28 (MINOR, open, new) — the corpora contain exactly one `RealValue`

Found while splitting the fully-agreed set, and recorded because it is the same species as D-19 and
was not caught by fixing D-19.

`RealValue.value()` left the reviewed sign-off list not because it was fixed but because it stopped
being *discriminating*: the shipped corpora contain exactly one `RealValue` — `REAL(0.0)`, from
`InputGenerator.zeroDivisors()`. With one receiver, **all 23 `RealValue.*` operations have a
one-point codomain by arithmetic**, and nothing about any of them can be measured, whatever their
agreement figure. `IntegerValue` is the next thinnest at 8 receivers, all of them index boundaries
drawn for a different purpose.

This was **not** fixed here, deliberately: FIX C was scoped to `BOOLEAN` and `STRING` so the
before/after in §11.4 attributes cleanly to one change. The remedy is the same one — a
`realBoundaries()` / `integerBoundaries()` corpus — and it will move the census again.

It is worth stating plainly what this means for the numbers above: **"159 single-valued" is not a
fact about the historical implementation.** It is a joint fact about the implementation and the
corpus, and at least 23 of the 159 are single-valued for no better reason than that nobody wrote
down more than one `RealValue`. That is exactly why the number is now published per operation
instead of being asserted once in a document.

---

## 11.7 Acceptance

`mvn -q clean && mvn -B verify -Djava.awt.headless=true`, twice from clean, both `BUILD SUCCESS`.

| module / phase | before (`fbc03663`) | after (`0a93ad4f`) | delta |
|---|---|---|---|
| use-core surefire | 61 | **67** | **+6** |
| use-core failsafe | 1 | 1 | — |
| use-gui surefire | 1 | 1 | — |
| use-gui failsafe | 129 | 129 | — |

Every one of the +6 is new; no pre-existing test was changed and none broke.

```
Tests run: 11 -- org.tzi.use.architecture.MavenCyclicDependenciesCoreTest
Tests run:  6 -- Uncertainty differential smoke
Tests run: 10 -- Unwritten-port invariant          (was 8: +2)
Tests run:  9 -- HistoricalOracle class-loader isolation
Tests run: 30 -- Differential harness regressions  (was 26: +4)
Tests run:  1 -- org.tzi.use.uml.mm.ModelAPITest
Tests run: 67 -- total
```

* **+2 invariant**: `aNoLogicPortCannotProduceAStagePass`,
  `aDegenerateOperationNeedsAWrittenSignOff`.
* **+4 regression**: `distinctReferenceValuesCountsTheReferenceOverMeasuredRows`,
  `theStageGateRefusesADegenerateOperation`,
  `theReportHeaderCarriesDiscriminatingPowerPerOperation`,
  `goldenComparisonIsBytesAndNotLines`.

**Determinism.** The harness's own stdout across both runs, 324 lines, byte-identical:

```
*** RUN1 == RUN2 byte-identical: 324 lines of harness output
a2f6931c16f33c1d9a5fea629adcb3ced950981bb84898d63c35ea9a50a29106  harness1.txt
a2f6931c16f33c1d9a5fea629adcb3ced950981bb84898d63c35ea9a50a29106  harness2.txt
```

**Goldens, refreshed deliberately.** Both files gained exactly seven header lines and **zero data
rows changed**:

```
$ diff <(grep '^#' docs/port2/differential/s1-smoke-ureal-add.tsv) <(grep '^#' use-core/target/differential/s1-smoke-ureal-add.tsv)   # before the refresh
13a14,20
> # op.URealValue.add(value).rows	784
> # op.URealValue.add(value).measured	784
> # op.URealValue.add(value).agreement	784
> # op.URealValue.add(value).disagreement	0
> # op.URealValue.add(value).distinctReferenceValues	258
> # op.URealValue.add(value).discriminating	true
> # accepted.degenerateOperations	0

$ diff <(grep -v '^#' golden) <(grep -v '^#' regenerated)   # both files
(no output)
```

Refreshed with `-Duse.differential.golden.refresh=true` in one command, then re-verified without it.
After the refresh, both match byte for byte:

```
GOLDEN == RUN2  s1-smoke-ureal-add.tsv
GOLDEN == RUN2  s1-smoke-ureal-minus-faulty.tsv
ad505092ab4078286735dda57e42ed32272c1597f2ac1e45521ae30b3687c9a5  s1-smoke-ureal-add.tsv
3862dcdec7489bd6410d6809881e2124648ff9b292ded4d7f3ad524e4cf78c3c  s1-smoke-ureal-minus-faulty.tsv
```

**Scope.**

```
$ git diff --name-status 30d480db..HEAD -- '*/src/main/*'
(no output)
```

Working tree clean after both runs; the byte-probe golden the new test writes is deleted in a
`finally` and `git status --short` shows nothing under `docs/port2/differential/` afterwards.

---

## 11.8 Verdict, and what is still true of the next round

**The instrument is now sound about the three things it has been caught on** — the absence of a
measurement (rounds 1–3) and the degeneracy of one (round 4) — and each is enforced by a test that
fails if the enforcement is removed, with a control in the same test that fails if the enforcement
becomes a blanket refusal.

**It is not sound about D-17 and D-20, and those are open.** A subject can still shrink its own
`driven` denominator, and the enum-quantified audit still restates its own implementation. Neither
was in this round's scope and neither should be read as closed.

**The two places round 4 named for the next reviewer are still unlooked-at**, and one of them is now
*measurable* for the first time: an operation whose range over the corpora is exactly **two** values
would be reported `2 distinct reference values` — above the threshold, and still nearly free for a
subject echoing one bit of its receiver. `BooleanValue.value()` and `BooleanValue.isTrue()` sit at
exactly 2 and are on the reviewed list for a different reason. **`DISCRIMINATING_MINIMUM = 2` is a
threshold, and a threshold is a place to stand, not a proof.** The honest statement is that the
harness now publishes a number where it used to publish nothing; whether 2 is enough for a given
operation is a question a reader can now ask and could not ask before.

The `OPAQUE` branch is likewise unexamined: `type()` and `getRuntimeType()` are among the operations
the constant-literal subject greens, and it greens them by reproducing a string built by field
reflection. They are now labelled non-discriminating, which is the correct label and is not the same
as having been measured.

---

# 12. APPENDIX — the round-4 snapshot (former §10), as originally written

> **This section is history, not the verdict.** It is the round-4 record exactly as it was written
> on 2026-08-17, when it stood as §10 and said `DEFECTIVE`. It is kept verbatim because its pasted
> output is the primary evidence for D-15 and for the round-4 census, and deleting it would orphan
> that evidence. **Only its subsection numbers changed** (`§10.n` → `§12.n`), because §10 is now the
> consolidated record. Where this appendix and §10 disagree — and on the verdict they do — **§10 is
> authoritative.** D-15 was closed in `0a93ad4f`; see §11.

Sources for everything below, all on `port-uncertainty-2`:

* `docs/port2/stage-01-verification-round4.md` — independent empirical verification (ran Maven, ran
  probes against the vendored jars).
* `docs/port2/stage-01-static-review-round4.md` — independent static review (no Maven; `javap` and
  reflection probes).
* The harness itself, under `use-core/src/test/java/org/tzi/use/uncertainty/differential/`.

---

## 12.1 CURRENT VERDICT — **DEFECTIVE**, and what that means for a reader

> **Can I trust a differential number from this harness?** Only with the operation named, and only
> after checking that the operation is one whose answer can vary. The harness is now sound about the
> *absence* of a measurement — three separate ways of scoring "nothing happened" as agreement were
> found and all three are closed and pinned by executing tests, and the round-4 reviewers reproduced
> each closure independently. It is **not** sound about the *degeneracy* of a measurement. **120 of
> the 285 reachable operations produce exactly one distinct reference value over every input the
> shipped corpora can supply**, so on those 120 the verdict `AGREE` is decided before either
> implementation runs: a subject consisting of 120 hardcoded literals — no arithmetic, no branching,
> never reading its receiver or arguments — scores `AGREE=8616`, `DIFFER=0`, `isClean() == true`, and
> a written report headed `# rows.disagreement 0`. Every one of those rows is *individually* correct;
> the false statement is at sweep level, which is the level a stage reads. **Therefore: a
> `DIFFER`/`BOTH_THREW`/`MIXED` count from this harness is trustworthy and always was — the
> instrument finds real defects, and nine planted defects of nine were caught. An `AGREE` count is
> trustworthy only for an operation shown to have more than one reference value. Until D-15 is fixed,
> no fidelity claim in S4–S7 may quote a per-operation agreement figure without also quoting how many
> distinct reference values that operation produced.**

---

## 12.2 The story in order

| # | State | Evidence | Closed by |
|---|---|---|---|
| 0 | Harness built (§1–§6). Believed sound. | §5 acceptance | — |
| 1 | **DEFECTIVE — D1.** The harness scored *its own* marshalling failures as agreement: rows where neither side ever entered the operation came out `AGREE`. | §7.2 | `cf9d2f45` — `HARNESS_ERROR` added, a distinct non-agreement (F1–F11). |
| 2 | **DEFECTIVE — D2.** Two throws with matching class names were scored `AGREE_THROWN` with the messages discarded. Against a subject whose every body is `throw new RuntimeException("TODO: port …")`: **21 816 of 471 471 rows green**, over 27 operations, because `RuntimeException` is what the historical code throws for type errors and is the least discriminating class in Java. | §8.1 | `e8b73e48` — throw-agreement **deleted**, not tightened. `AGREE_THROWN`/`DIFFER_THROWN` no longer exist; both outcomes are the single non-agreement `BOTH_THREW`, whose note carries both classes **and** both messages. |
| 3 | **DEFECTIVE — D-10.** `VOID` vs `VOID` was scored `AGREE`. Against a subject whose every body is empty: **444 rows green — every driven row of every void operation the harness can reach**, e.g. `URealValue.setTypeToRuntimeType()` at 144/144. | §8.2, `stage-01-verification-round3.md` §1.1 | `93e038ac` — `UNMEASURABLE` added (`isAgreement()` false, `isMeasurement()` false), raised only when *neither* side carries an observation; plus five further fixes, §9. |
| 4 | **DEFECTIVE — D-15 (now).** Two *real* values, correctly compared and correctly equal, over an operation whose codomain is a single point. **120 of 285 operations**; a 120-literal subject is fully agreed on all 120. | §12.3 | **open** |

The shape has not changed in four rounds. Each round the harness stopped making one false claim and
a reviewer found a different construction producing the same false claim. Rounds 1–3 were all "the
absence of a measurement was scored as an agreement". Round 4 is not: it requires no bug in
`DifferentialSweep` at all, and it is the first door that every existing safeguard is *right* to let
through.

---

## 12.3 D-15, the open CRITICAL, measured two independent ways

Both round-4 reviewers found it independently, by different methods, and their numbers are
consistent but not identical. Both are recorded; neither is blended.

**(a) Empirical — codomain census over the shipped sweep** (`stage-01-verification-round4.md` §3.1,
§3.2). Seed `20260817`, the shipped 285-operation inventory, the shipped six corpora. Distinct
canonical reference values counted per operation over its measured rows:

```
TOTAL single-valued-codomain operations: 120  of 285
     19 IntegerValue   23 RealValue   20 UBooleanValue
     19 UIntegerValue  19 URealValue  20 UStringValue

URealValue.isDefined()      144 measured  144 driven   the one value: BOOLEAN(true)
URealValue.type()           144 measured  144 driven   OPAQUE("org.tzi.use.uml.ocl.type.URealType|…")
UStringValue.toBoolean()    102 measured  102 driven   BOOLEAN(false)
RealValue.toStringWithType()  6 measured    6 driven   STRING("0.0 : Real")
…
```

Driven through the real `DifferentialSweep` with the *shipped* per-operation predicate
(`driven > 0 && agreed == driven`, `UnwrittenPortInvariantTest.java:416-425`):

```
--- subject j-constant-table   body: return CONST.get(op.key())  -- 120 hardcoded literals, no logic
    rows 471471 / measured 8616 / agreement 8616
    tally {AGREE=8616, BOTH_THREW=30580, HARNESS_ERROR=388695, MIXED=43580}
    FULLY AGREED OPERATIONS: 120

--- subject h-const-BOOLEAN(false)   body: return UValue.bool(false)
    rows 471471 / measured 52196 / agreement 6888
    FULLY AGREED OPERATIONS: 92
```

**Zero `DIFFER` rows anywhere in subject j's sweep.** And what a *stage* sees, which is the level
that matters (§3.3):

```
STAGE URealValue.isDefined()   subject 'const-BOOLEAN(true)'
  summary                     URealValue.isDefined(): 30 rows, 30 measured, AGREE=30
  disagreements().isEmpty()   true   <= a caller asserting this reads PASS
  isClean()                   true   <= *** THE DOCUMENTED PASS PREDICATE SAYS PASS ***
  requireMeasurements(1)      returned normally
```

**(b) Static — return-type census and bytecode** (`stage-01-static-review-round4.md` D-15). 140 of
the 285 reachable operations return `boolean`, and most of those predicates are compile-time
constants:

```
$ javap -c -p org/tzi/use/uml/ocl/value/Value.class
  public boolean isBag();       0: iconst_0   1: ireturn
  public boolean isUndefined(); 0: iconst_0   1: ireturn
  public boolean isCollection();0: iconst_0   1: ireturn
  public boolean isLink();      0: iconst_0   1: ireturn
```

Restricted to the six receiver kinds the corpora actually contain: **91 constant-`false`
operations**, predicted at 6762 agreement rows against a one-line subject `return UValue.bool(false)`.

**The two counts do not have to be reconciled to act on this, and are not reconciled here.** (a) is a
measurement of what the sweep produced (92 fully-agreed operations for that subject, 6888 rows); (b)
is a static prediction from declared return types and bytecode (91, 6762). The discrepancy is one
operation. It is recorded as unreconciled; it does not change the finding, and either number is
larger than D-10's six operations and 444 rows.

**Why every safeguard from rounds 1–3 lets it through, and is right to.** `UNMEASURABLE` cannot fire
— both sides carry an observation. `measurementCount() == rowCount()`. `isClean()` is `true`. The
writer writes. `throwClassMismatchCount()` is 0.
`DifferentialHarnessRegressionTest.everyKindIsEitherAnObservationOrUnmeasurable` passes, and correctly
so: it quantifies over `UValue.Kind`, the *value vocabulary*, whereas the degeneracy is in the
**operation's codomain**, which nothing in the tree examines.

**Why the shipped family invariant misses it.** `UnwrittenPortInvariantTest` has seven subjects, one
of which is constant-valued — `e-fixed-constant`, `return UValue.uBoolean(true, 1.0)`, canonical
`UBOOLEAN(true,1.0)`. **No operation in the 285-operation inventory returns a `UBOOLEAN` canonical
form as its result**, which is exactly why that subject prints "fully agreed ops (none)". The
subject family was built from the encodings of *"no code here"* that had already caused a failure,
not from an enumeration of what the *reference* can return. Changing that single literal to
`UValue.bool(false)` makes the shipped assertion at `UnwrittenPortInvariantTest.java:147` fail with
**92 unreviewed fully-agreed operations**.

---

## 12.4 Every defect still open

Round 4 produced two independent reports that **both** used the keys `D-16`…`D-19`, for different
defects. The register below is the single canonical one; the "origin" column maps each entry back.
Nothing in the round-4 reports was renumbered.

| Key | Sev | Defect | Origin |
|---|---|---|---|
| **D-15** | **CRITICAL** | **A degenerate codomain is scored as fidelity.** 120 of 285 operations produce one distinct reference value over the shipped corpora; a subject of 120 hardcoded literals is `AGREE=8616 / DIFFER=0` and fully agreed on all 120. 76 of them yield a stage-shaped sweep with `isClean() == true` and a written report headed `# rows.disagreement 0`. §12.3. | both, `D-15` |
| D-16 | MAJOR | **`isClean()` has no coverage floor.** It is `measurementCount() > 0 && disagreements().isEmpty()`, so **one** measured row passes. A 1×1 domain gives `1 rows, 1 measured, AGREE=1`, `isClean() == true`, `requireMeasurements(1)` returns normally, and the writer writes the report. `requireMeasurements(int)` is the only floor and its argument is chosen by the caller who wants to pass. | verification `D-16` |
| D-17 | MAJOR | **A subject controls its own per-operation denominator.** `fullyAgreedOperations()` computes `driven` as "not `HARNESS_ERROR` and not `UNSUPPORTED`", and both are raised by the *subject* about itself. `HarnessMarshallingException` is precisely what `Candidate`'s Javadoc tells an S4 adapter author to throw. Measured: a subject implementing one method and raising `HarnessMarshallingException` everywhere else scores `{AGREE=444, HARNESS_ERROR=471027}` — **444 agreement rows and zero `DIFFER` rows in a 471 471-row sweep**. `isClean()` does protect a *stage* here (those rows are disagreements); the defect is in the standing invariant's predicate, which is what the whole closure argument rests on. | verification `D-17` |
| D-18 | MAJOR | **The canonical-form collision is 193 of 285 operations, not 2.** `HistoricalOracle.fromHistorical` (lines 709–720) maps a raw `Boolean`/`Integer`/`Double`/`CharSequence` to the same `UValue.Kind` as `BooleanValue`/`IntegerValue`/`RealValue`/`StringValue`. §9.5 recorded this as a two-operation blind spot and pinned those two on `ECHO_SUBJECT_REVIEWED`; measured against declared return types in the vendored jars it is **193 of 285 (68%)**. On all of them a port returning the right content with the wrong Java type is scored `AGREE` on every row. | verification `D-18` |
| D-19 | MINOR | **Coverage, quantified.** 61 of 285 operations produce **zero measurements** across the whole 471 471-row sweep even against a perfect port; 52 have zero driven rows. `BooleanValue` and `StringValue` are in `MARSHALLABLE_RECEIVERS` so `supports()` returns `true`, but no corpus contains a `BOOLEAN` or `STRING` value, so all 27 `BooleanValue.*` and all 25 `StringValue.*` operations are 100% `HARNESS_ERROR`. Compounds D-15: nearly all 52 are type predicates or constant accessors, so widening the corpora widens the single-valued population by roughly 40 more. | verification `D-19` |
| D-20 | MAJOR | **`everyKindIsEitherAnObservationOrUnmeasurable` is tautological.** It branches on `sample.carriesAnObservation()` and asserts the verdict `DifferentialSweep.classify` derives from that same predicate, so it restates the implementation. Add a `Kind.UNDEFINED`, forget to widen `carriesAnObservation()` (`UValue.java:191-193`, hard-coded `kind != VOID && kind != NULL`), add the required sample: the test stays green and D-10 is reintroduced. §9.2 and the closure argument both claim the opposite. A non-circular criterion exists: a value-carrying kind has at least two distinguishable inhabitants. | static `D-16` |
| D-21 | MAJOR | **The writer's guard and every report header are file-level totals.** `writeAll` sums `measurementCount()` over all results and checks only the total; `# rows`, `# rows.measured`, `# rows.agreement`, `# rows.disagreement`, `# rows.throwClassMismatch` and `# verdict.*` are likewise sums, and `# operations` is a comma-joined list with no per-operation counts. A 40-operation report in which 39 measured nothing and one measured one row is written happily and its header is indistinguishable in shape from a fully-measured one. This is the D-10 lesson ("444 of 471 471 is noise in an aggregate; per operation it was 144 of 144") applied to the invariant test and **not** applied to the artefact a human reads. | static `D-17` |
| D-22 | MINOR (live at S4) | The `UNSUPPORTED` row's note asserts `reference: could be driven` / `subject: could be driven` whenever `supports()` returned true, but `supports()` is per-operation while the note is written per row with the inputs in hand; the per-row receiver check happens later (`HistoricalOracle.java:507-514`). Unreachable with today's candidates; live the moment a port with partial `supports()` coverage is plugged in. | static `D-18` |
| D-23 | MINOR (latent) | `unmeasurableNote` sets `voidOperation` from a *disjunction*, so the **subject** alone returning `VOID` is enough to assert a fact about the historical method's declaration. Not reachable with the shipped corpora (no null-returning invocation was found). Correct predicate: `ref.value.kind() == VOID` alone. | static `D-19` |
| D-24 | MINOR | §5.2 of this document pasted a report header and line counts that the golden refresh in `93e038ac` superseded, in the one place the document invites the reader to check the record against the artefact. **Annotated in place, 2026-08-17** — see the correction box in §5.2. | static `D-20` |
| D-25 | MINOR | `AcceptedThrowPairs.java:48-49` states "the source file is plain ASCII"; the same paragraph contains a U+2014 and the file has fifteen non-ASCII bytes. Harmless in effect — git's binary heuristic keys on NUL and the file has none, so the reviewability claim holds — but this round's own standard is that a comment in evidence-producing code must not state something the file falsifies. | static `D-21` |
| D-14 | MINOR | `AcceptedThrowPairs` provenance: no `# accepted.*` header, so a run with a non-empty allowlist that adjudicated nothing is byte-indistinguishable from a run with `none()`. `describe()` and `DifferentialSweep.acceptedThrowPairs()` are called from nowhere. | §9.7, still open |
| D-9 | MINOR | Coverage: 8 marshallable receivers; `SBooleanValue` (39 operations) and collection receivers are out of reach, `org.tzi.use.uml.ocl.type.*` and `uDataTypes.*` unreachable by design. A declared scope boundary, recorded so it is not mistaken for a result. | §9.7, still open |

**And one planning fact that is not a defect but is not written down anywhere else:** a **perfect**
port is `isClean() == false` on **97 of 285 operations**, because their shared error paths produce
`BOTH_THREW` rows even when both sides throw the same class with the same message — `BOTH_THREW` is a
non-agreement by design. A stage told simply to "assert `isClean()`" is therefore pushed towards
`AcceptedThrowPairs`, which is the one remaining agreement-without-two-values route in the harness.
Bulk sign-off there is exactly how a blanket throw-agreement rule gets reintroduced. See
`harness-contract.md` §6.

---

## 12.5 What IS sound, and is not in doubt

Recorded because "DEFECTIVE" must not be read as "worthless". Every item was reproduced
independently in round 4.

* **Isolation.** The historical classes come out of the vendored jars through a parent-last loader;
  the harness refuses to run if `org.tzi.use.uml.ocl.value.Value` resolves to the application loader
  (§3). Nine isolation tests.
* **D1, D2, D-10 are closed and pinned.** Subject `c-empty-body`: `rows 471471 / agreement 0`, tally
  `{DIFFER=51752, HARNESS_ERROR=388695, MIXED=30580, UNMEASURABLE=444}`, "fully agreed ops (none)" —
  the 444 rows were *relabelled*, not deleted, and the 51 752 genuine divergences are untouched.
  One-sided absence correctly stays `DIFFER`.
* **The instrument finds real defects.** Nine planted defects spanning arithmetic, comparison,
  conversion, string, exception class, zero divisor, the 0/1 confidence boundary, empty string and
  out-of-range index were **all nine** reported as divergence, every one `isClean() == false`, with a
  clean control (perfect port on `URealValue.add`: 784/784 `AGREE`, `isClean() == true`).
* **A wrong exception class has an aggregate.** `throwClassMismatchCount()` separated an otherwise
  bit-identical port 0 vs 120 where `rowCount`, `measured`, `AGREE`, `BOTH_THREW`, `HARNESS_ERROR`
  and `disagreements` were all equal.
* **The writer refuses to lie by omission.** Zero-row and zero-measurement reports are both refused,
  and the refusal is pinned by a test that also asserts the file was not created.
* **Determinism and scope.** `mvn -q clean && mvn -B verify -Djava.awt.headless=true` twice from
  clean, both `BUILD SUCCESS`; use-core surefire 61, failsafe 1; use-gui surefire 1, failsafe 129;
  identical both runs. The invariant's 205 lines of output byte-identical across runs at
  `sha256 482ddb2fef0e97f6915b6e3f44c5c3910cf574337ca0154345bf7ac153308530`, reproduced independently
  by the round-4 verifier. Both goldens byte-identical to the regenerated reports.
  `git diff --name-status 30d480db..HEAD -- '*/src/main/*'` is **empty**. No `AcceptedThrowPairs`
  sign-off exists anywhere in the tree.

---

## 12.6 The precondition on S4 — what must be true before a fidelity number is quoted

Not a wish-list; this is the gate. It is stated normatively, with the rest of the rules, in
[`harness-contract.md`](harness-contract.md).

1. **`isClean()` must not be the pass predicate on its own.** A stage must additionally assert that
   the sweep observed **at least two distinct canonical reference values** — that it *could* have
   failed. The genuinely-constant operations go on a written, reviewed allowlist, exactly like
   `ECHO_SUBJECT_REVIEWED`. The 120 are enumerable, stable, and already listed in
   `stage-01-verification-round4.md` §3.1.
2. **A constant subject per canonical kind joins the invariant family** — `bool(false)`, `bool(true)`,
   `integer(0)`, `string("")`, and an `OPAQUE` — so the family covers what the *reference* can return
   and not only the encodings of "no code here". Today it covers exactly one constant, and that
   constant is the one kind no operation returns.
3. **A subject must not be able to shrink its own denominator.** `HARNESS_ERROR` raised by the
   subject must not silently leave `driven` (D-17).
4. **Coverage facts go in the report header, per operation** — measured count and distinct reference
   values — not as file-level sums (D-21).

Until (1) and (2) hold, every fidelity claim S4 makes about a **type predicate, an accessor, or a
`type()` / `getRuntimeType()` operation** would be an artefact of the instrument rather than a
measurement of the port.

---

## 12.7 The standing lesson, after four rounds

Round 1: a harness failure counted as agreement. Round 2: two throws counted as agreement. Round 3:
two `VOID`s counted as agreement. Round 4: two equal values over a one-valued codomain counted as
fidelity. **They are getting harder to see, not easier** — the fourth required no bug in
`DifferentialSweep` at all, and was found within an hour by asking a question the harness had never
been asked: *how many distinct answers does the reference give?*

That is the diagnosis worth carrying forward. The space is being searched by a series of ad-hoc
questions, one per round, rather than by a property. The property that would have made D-10, D-15 and
D-18 visible as **one family** is a single statistic the harness does not compute: **for each
operation, the number of distinct canonical reference values observed.** An operation with one
distinct reference value over 144 rows is not evidence of fidelity whatever its agreement rate.
Publishing that number is cheap and is the first thing the next round should do.

**Neither round-4 reviewer claims the space is closed, and neither does this document.** The two
places both named for the next reviewer to look: the **two-valued** codomain (an operation whose
range over the corpora is `{true, false}` — a subject echoing one bit of its receiver would green
those, and the census only counted singletons), and the **`OPAQUE`** branch, where
`UncertainBooleanValue` (9 operations) and `uDataTypes.UInteger` (1) are rendered by field reflection
and a port need only reproduce a string.

---
