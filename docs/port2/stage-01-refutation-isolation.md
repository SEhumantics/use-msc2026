# S1 refutation — class-loader isolation and self-comparison

Dimension: **class-loader isolation and self-comparison.**
Method: static review plus executed experiments. No Maven was run. Everything below was produced
with `javap`/`unzip`/`sha256sum` and with plain `javac`/`java` against a **copy** of
`use-core/target/{classes,test-classes}` taken into a scratch directory, invoked with the exact
`jdk.module.path` and `java.class.path` recorded by the last surefire run
(`use-core/target/surefire-reports/TEST-org.tzi.use.uncertainty.differential.HistoricalOracleIsolationTest.xml`).
The working tree was not modified (`git status --short` reports only the pre-existing untracked
`docs/port2/spec-parts/`).

**Verdict: DEFECTIVE.**
The isolation mechanism itself — the part I was sent to break — survived every attack I could
construct, including a mutation test. The defects are one layer up: `DifferentialSweep`'s
*verdict classification* has a false-agreement channel that will report a green sweep in which no
operation was ever executed on either side, and which scores a cross-loader `ClassCastException`
on both sides as agreement. That is precisely failure modes 3 and 4 of the brief, arriving through
a door I did not expect.

---

## 0. Reproduction harness

```
S=<scratch>
cp -r use-core/target/classes      $S/snap/classes
cp -r use-core/target/test-classes $S/snap/test-classes
# CP2 / MP2 = the recorded surefire java.class.path / jdk.module.path with the two
#             use-core entries redirected at the snapshot
java -cp "$S/probe:$S/snap/test-classes:$CP2" --module-path "$MP2" --add-modules use.core <Probe>
```

Recorded surefire facts (from the XML, not assumed):

```
surefire.real.class.path -> .../surefire-booter/3.5.4/...        (surefire 3.5.4)
jdk.module.path          -> /home/.../use-core/target/classes:...   <-- MAIN is on the MODULE path
java.class.path          -> /home/.../use-core/target/test-classes:... <-- TESTS are on the CLASSPATH
java.version             -> 21.0.11
```

So the tests run in the **unnamed module on the classpath** while `use.core` is a **named module
resolved into the boot layer**. Every claim below is made under that configuration.

---

## 1. Attack — "is the loader genuinely parent-last?" — **NOT REFUTED**

`IsolatedJarClassLoader` (`use-core/src/test/java/org/tzi/use/uncertainty/differential/IsolatedJarClassLoader.java`)
constructs `super(name, urls, ClassLoader.getPlatformClassLoader())` and then overrides
`loadClass(String,boolean)` so that any name matching `org.tzi.use.` or `uDataTypes.` is resolved by
`findClass` from its own URLs **with no parent fallback**.

Measured (`Probe1`):

```
app loader          = jdk.internal.loader.ClassLoaders$AppClassLoader@5ef04b5
app RealValue       = class org.tzi.use.uml.ocl.value.RealValue loader=AppClassLoader module=module use.core
platform RealValue  = class org.tzi.use.uml.ocl.value.RealValue loader=AppClassLoader  SAME_AS_APP=true
oracle parent       = jdk.internal.loader.ClassLoaders$PlatformClassLoader@28864e92
historical RealValue= class org.tzi.use.uml.ocl.value.RealValue loader=IsolatedJarClassLoader[...] SAME_AS_APP=false
```

Two things are established independently of the author's write-up:

* The author's central claim is **true and reproducible**. Under JPMS the *platform* loader returns
  the *application's* `org.tzi.use.uml.ocl.value.RealValue`. `BuiltinClassLoader`'s
  package-to-module map is static and shared across the builtin loaders, so the platform loader finds
  `org.tzi.use.uml.ocl.value` in boot-layer module `use.core`, sees the app loader defines it, and
  delegates. `new URLClassLoader(urls, getPlatformClassLoader())` is genuinely not isolation here.
* The actual loader returns a distinct `Class` object defined by itself.

Dependency resolution inside the isolated world is also genuinely isolated (`Probe5`):

```
uDataTypes.UReal loader = isolated
URealValue fields:  uDataTypes.UReal
```

so the historical `URealValue` is linked against the jar's `uDataTypes.UReal`, not against anything
on the application side.

Jar provenance checks out: the two committed jars are **byte-identical** to the reference copies
(`sha256sum` on both pairs gives `80ac8ae4…8788d` and `53b2a43f…59d0`), `use.jar` contains zero
`uDataTypes`/`atenearesearchgroup` entries and the uncertainty jar contains zero `org/tzi` entries,
so the two URLs cannot shadow each other.

I also checked whether resources leak, since `getResource`/`getResources` are **not** overridden.
They do not, in this configuration (`Probe5`): `isolated.getResource(".../RealValue.class")` returns
the `jar:` URL, the app loader returns the `file:` URL under `target/classes`, `LEAKS APP BYTES = false`.
Module resources are not reachable through the platform loader. Noted as incidental, not a defect.

## 2. Attack — "does the isolation test prove anything, or only pass because no port exists?" — **NOT REFUTED**

This is the attack I expected to land, and it does not.

**(a) The test is not vacuous today.** `HistoricalOracleIsolationTest` pivots on
`org.tzi.use.uml.ocl.value.RealValue`, which exists in `use-core/src/main` *and* in the historical
`use.jar` right now. `Probe1` confirms a genuine same-FQN collision resolving to two different
`Class` objects. The three negative controls in `naiveLoadersWouldSelfCompare` assert that both
naive constructions *do* leak, so "the isolation test passes" is not an empty statement.

**(b) The guard survives a mutation.** I built the future scenario the brief asked for. I compiled a
stand-in ported `org.tzi.use.uml.ocl.value.URealValue` and dropped it into the snapshot's
`classes/` — i.e. into module `use.core`, which is where the real S4 port will live — and then
deleted the parent-last `loadClass` override from `IsolatedJarClassLoader`, recompiled the whole
harness, and put it ahead of the original on the classpath.

Baseline, port present, loader intact:

```
found=9 ok=8 failed=1
  => uTypesResolveOnlyThroughTheOracle: URealValue unexpectedly resolves on the application loader.
```

Port present, loader broken:

```
found=9 ok=0 failed=0
  => HistoricalOracleUnavailableException: class org.tzi.use.uml.ocl.value.Value was defined by
     jdk.internal.loader.ClassLoaders$AppClassLoader@5ef04b5 rather than the isolated historical
     loader ... The oracle would be comparing the port against itself; refusing to continue.
       at HistoricalOracle.assertIsolated(HistoricalOracle.java:308)
       at HistoricalOracle.<init>(HistoricalOracle.java:116)
       at HistoricalOracleIsolationTest.openOracle(HistoricalOracleIsolationTest.java:52)
```

The `@BeforeAll` blows up, the whole class errors, nothing is reported as skipped or passing. The
eager `assertIsolated` sweep in the constructor is doing real work and is not decorative. I could
not construct a self-comparison that gets past it.

**(c) The one caveat is a process risk, filed below as MINOR-5:** `uTypesResolveOnlyThroughTheOracle`
asserts the U-types are *absent* from the application loader and therefore becomes a hard failure the
day the port lands (measured above). The failure message forbids deletion, but it is the single
isolation assertion that must be hand-edited at S4.

## 3. Attack — "leakage and swallowed exceptions" — **REFUTED. This is where the harness lies.**

Object leakage across the boundary is handled correctly by design: `UValue` is reflection-free,
`fromHistorical` switches on `result.getClass().getName()` (a string, not a `Class` identity), and no
`Class` object from the isolated loader escapes into the sweep. Historical `Throwable` instances *do*
cross into `DifferentialSweep.Outcome`, but only `getClass().getName()` and `getMessage()` are read
off them.

That is exactly the problem. `DifferentialSweep.classify` (lines 121-127):

```java
if (ref.thrown != null && sub.thrown != null) {
    boolean same = ref.thrown.getClass().getName().equals(sub.thrown.getClass().getName());
    String note = same ? "" : "reference message: " + ... ;
    return new DiffRow(..., same ? DiffVerdict.AGREE_THROWN : DiffVerdict.DIFFER_THROWN, note);
}
```

Agreement is decided on the throwable's **class name alone**, and when the names match the messages —
the only remaining evidence — are **discarded** (`note = ""`). `DiffVerdict.isAgreement()` returns
true for `AGREE_THROWN`, so such rows never appear in `Result.disagreements()`, which is what both
smoke tests assert on.

Measured (`Probe3`), historical side genuinely executing, subject throwing for an unrelated reason:

```
### E. historical ArithmeticException("/ by zero") vs subject ArithmeticException(other)
  tally         = UIntegerValue.divideBy(value): 1 rows, AGREE_THROWN=1
  disagreements = 0
  row           = [0  UIntegerValue.divideBy(value)  UINTEGER(1,0.0) | UINTEGER(0,0.0)
                      THROWN:java.lang.ArithmeticException  THROWN:java.lang.ArithmeticException
                      AGREE_THROWN  ]
  note field    = ''   <-- messages discarded
```

And the case the brief names explicitly — a `ClassCastException` on both sides:

```
### G. BOTH sides ClassCastException -> ?
  tally = URealValue.add(value): 1 rows, AGREE_THROWN=1  disagreements=0
```

A cross-world `ClassCastException`, the canonical symptom of a partial isolation break, is scored as
**agreement** and leaves no trace in the report. See DEFECT MAJOR-1.

## 4. Attack — "what happens when the historical side throws?" — **REFUTED (second channel)**

`DifferentialSweep.apply` wraps the call in `catch (Throwable t)`. That catch does not distinguish
*the subject's behaviour* from *the harness's own plumbing failures*. `HistoricalOracle.invoke`
throws `IllegalArgumentException` for an arity mismatch (line 370) and for a receiver-type mismatch
(lines 377-381), and `IllegalStateException` from `toHistorical`/`fromHistorical` (lines 497, 500,
553, 556) and from `checkOpen` (line 584). All of these are caught by `apply` and rendered as if the
historical implementation had thrown them.

Measured (`Probe2`), sweeping `URealValue.add` over a corpus of the wrong receiver kind against
`StubCandidate.faithful()`:

```
### C. AGREE_THROWN false agreement: neither side ever executes the operation
  tally          = URealValue.add(value): 9 rows, AGREE_THROWN=9
  disagreements  = 0
  GREEN?         = true
  sample row     = 0  URealValue.add(value)  UINTEGER(1,0.0) | UINTEGER(1,0.0)
                      THROWN:java.lang.IllegalArgumentException
                      THROWN:java.lang.IllegalArgumentException  AGREE_THROWN
```

Nine rows, full agreement, zero disagreements, and **not one historical operation was executed**.
Both exceptions came from input validation — the oracle's receiver precheck on one side,
`StubCandidate.invoke`'s `receiver.kind() != UREAL` check on the other. The written TSV would carry
`# rows 9` and `# verdict.AGREE_THROWN 9`, which any reader will score as green. See DEFECT MAJOR-2.

A closed oracle is the milder relative of the same bug (`Probe2` section D): `supports()` still
returns `true` because `resolve` hits its cache, and every row then throws `IllegalStateException`.
Against `StubCandidate` that surfaces as `MIXED` (visible) — but against any subject that also
validates its inputs with `IllegalStateException` it becomes `AGREE_THROWN`.

## 5. Attack — "JPMS / module-info" — **NOT REFUTED**

`git diff b7aaa99c..HEAD --stat -- '*module-info*' 'use-core/src/main' 'use-gui' 'use-assembly' '*pom.xml'`
is **empty**. `module-info.java` was not edited, no `--add-exports`/`--add-opens`/`--patch-module`
was added, no pom was touched. The `git diff --name-only` for the S1 commit touches only
`docs/port2/**` and `use-core/src/test/**`. The commit message's claim on this point is accurate.

The harness has no module-path dependency of its own: it lives in the unnamed module on the
classpath, and locates the jars via `CodeSource`, `Class.getResource`, `ClassLoader.getResource` and
cwd-relative fallbacks — all of which work for a classpath directory. The historical jars sit inside
`target/test-classes/historical/`, a classpath *directory*; a `.jar` inside a classpath directory is
not itself on the classpath, which is why `Class.forName("org.tzi.use.uml.ocl.value.URealValue")` on
the app loader still throws today. Verified by the passing `uTypesResolveOnlyThroughTheOracle`.

One conditional note, filed as MINOR-3: two assertions are silently mode-dependent.

## 6. Attack — "does it fail loudly on every path?" — **PARTIALLY REFUTED**

No `assumeTrue`, no `Assumptions`, no `@Disabled` anywhere in the package (grepped). No
`junit-platform.properties`, so no parallel execution. Both test classes match surefire's default
`**/*Test.java` include and are confirmed to have run (9 and 6 methods, 0 skipped, in the recorded
surefire XML). Jar location and digest failures are genuinely fatal — `HistoricalOracleUnavailableException`
is unchecked, thrown from `open()` in `@BeforeAll`, and surfaces as a class-level error (demonstrated
in §2b). No static initialiser opens the jars, so there is no `ExceptionInInitializerError` confusion.

The exception is `HistoricalOracle.supports` (lines 355-362), which catches `RuntimeException` and
returns `false` — swallowing `HistoricalOracleUnavailableException`, the oracle's own "I cannot
function" signal. See DEFECT MAJOR-3.

---

## Defects

### MAJOR-1 — `AGREE_THROWN` is decided on throwable class name alone, and discards the messages

`use-core/src/test/java/org/tzi/use/uncertainty/differential/DifferentialSweep.java:121-127`

Two throwables with the same class and completely unrelated causes are scored `AGREE_THROWN`, which
`DiffVerdict.isAgreement()` (`DiffVerdict.java:33`) reports as agreement, which removes the row from
`Result.disagreements()` — the exact list both smoke tests assert to be empty. Because
`note = same ? "" : …`, the report retains no evidence whatsoever: the row is
`THROWN:<class>  THROWN:<class>  AGREE_THROWN  <empty>`.

The brief asks specifically whether a swallowed `ClassCastException` can be reported as agreement.
It can, measured: `Probe3` section G, two `ClassCastException`s from opposite worlds →
`AGREE_THROWN=1, disagreements=0`. `ClassCastException`, `NullPointerException`,
`IllegalArgumentException` and `IllegalStateException` are the four classes most likely to arise from
*harness* faults on both sides at once, and all four are unconditionally scored as agreement.

At minimum the messages must be recorded on `AGREE_THROWN` rows too, and message equality should
either be part of the verdict or be a separately tallied `AGREE_THROWN_DIFFERENT_MESSAGE`.

### MAJOR-2 — Harness-internal failures are indistinguishable from subject behaviour, and can be tallied as agreement

`DifferentialSweep.java:115-120` (`catch (Throwable t)`), reached from
`HistoricalOracle.java:370` (arity), `:377-381` (receiver type), `:497,:500,:553,:556` (marshalling),
`:584` (`checkOpen`).

A sweep whose op/domain wiring is wrong produces `AGREE_THROWN` on every row when the subject also
validates its inputs. Measured: 9 rows, `AGREE_THROWN=9`, `disagreements=0`, and the historical
implementation was never invoked once. The written report header would read `# rows 9` /
`# verdict.AGREE_THROWN 9`.

This is the failure mode the brief calls "worse than no harness": it is silent, it looks green, and
it scales — at S5-S7 an entire operation family could be swept with a mis-specified `UOp` receiver
type and be recorded as full agreement. Harness-raised exceptions need to be a distinct outcome
(e.g. `HARNESS_ERROR`, never an agreement) rather than being funnelled into the same channel as the
subject's own throwables. The cheap version: have `HistoricalOracle` wrap its own plumbing failures
in a dedicated exception type that `DifferentialSweep` recognises and refuses to score.

### MAJOR-3 — `HistoricalOracle.supports()` swallows the oracle's own unavailability and mislabels it as a missing historical API

`HistoricalOracle.java:355-362`

```java
public boolean supports(UOp op) {
    try { resolve(op); return true; } catch (RuntimeException e) { return false; }
}
```

`resolve` → `load` throws `HistoricalOracleUnavailableException`, which extends `IllegalStateException`
and is therefore a `RuntimeException`. Measured (`Probe4`):

```
supports(NoSuchValueClass.add) = false
direct load throws: HistoricalOracleUnavailableException
```

`DifferentialSweep.run` (lines 94-104) then writes the note
`"historical does not implement <op>"`. That is a false statement about the historical API when the
real cause is that the oracle could not resolve the class at all. `UNSUPPORTED` is at least not an
agreement, so the row survives into `disagreements()` — that is the only reason this is not a
CRITICAL — but the *attribution* is wrong, and a reviewer reading `# verdict.UNSUPPORTED N` plus that
note at S5 will conclude the historical implementation lacks the operation.

This also contradicts the class javadoc (`HistoricalOracle.java:54-58`): "The oracle never degrades
to a no-op and never signals 'skip'." On this path it does exactly that. The catch should be narrowed
to `NoSuchMethodException`-derived failures and let `HistoricalOracleUnavailableException` propagate.

### MINOR-1 — the isolation guard covers a hard-coded 12-name list, not the general `load()` path

`HistoricalOracle.java:110-117` vs `:334-345`

`assertIsolated` is called only from the constructor, over
`{Value, URealValue, UIntegerValue, UBooleanValue, UStringValue, UncertainValue,
UncertainBooleanValue, RealValue, IntegerValue, BooleanValue, StringValue, SequenceValue}`.
`historicalClass(String)` / `load(String)` perform no isolation check, and `resolve(op)` calls
`load(op.receiverType())` with a caller-supplied name. Measured (`Probe4`):
`historicalClass("UnlimitedNaturalValue")` resolves with no check (correctly, today, because the
loader policy is right). The belt-and-braces guard that actually caught the original bug does not
protect names a later stage introduces. Moving `assertIsolated` inside `load`'s mapping function
costs one line and closes it.

### MINOR-2 — the isolation prefix `org.tzi.use.` also captures the harness's own package

`IsolatedJarClassLoader.java:53-54`

`isIsolated("org.tzi.use.uncertainty.differential.UValue")` returns `true`, and measured
(`Probe1`): `loader.loadClass("org.tzi.use.uncertainty.differential.UValue")` →
`ClassNotFoundException`. Harmless today — nothing in the jars references the harness — but the
isolated world can never call back into the harness, and any future callback, listener or SPI wiring
will fail with a confusing CNFE rather than an explained one. Worth an explicit carve-out or at least
a comment.

### MINOR-3 — two isolation assertions silently degrade to nothing outside module mode

`HistoricalOracleIsolationTest.java:100-104` and `:193-201`

Both `platformLoaderIsNotACleanParentUnderJpms` and negative control (2) inside
`naiveLoadersWouldSelfCompare` catch `ClassNotFoundException` and `return` / set `null` with the
comment "classpath mode; nothing to demonstrate". If the reactor ever stops running tests on the
module path — a test `module-info.java`, `useModulePath=false`, a surefire change — those two
assertions evaporate and the test still reports 9/9 green, with nothing recorded about which mode
ran. Module mode is in force today (verified from the surefire XML, §0), so this is latent. A single
assertion that at least one of the two branches was taken, plus printing the mode, would fix it.

### MINOR-4 — the failure-path tests mutate a JVM-global system property

`HistoricalOracleIsolationTest.java:225-247` and `:251-283` set and clear
`use.historical.jars.dir` around `HistoricalOracle.open()`. No `junit-platform.properties` exists so
JUnit runs sequentially and this is safe today. If parallel execution is ever enabled, any concurrent
`HistoricalOracle.open()` would either fail against `/nonexistent/historical/jars` or be pointed at
the tamper directory. Worth a note in the file so nobody enables parallelism casually.

### MINOR-5 — the one isolation assertion that must be rewritten at S4

`HistoricalOracleIsolationTest.java:150-165`

`uTypesResolveOnlyThroughTheOracle` asserts `ClassNotFoundException` for the four U-types on the
application loader. Measured: with a stand-in ported `URealValue` in module `use.core`, the class
goes from 9/9 to 8/9 with exactly that failure. This is deliberate and the message says
"do NOT delete it", but it is a tripwire that *forces* an edit to the isolation test at the moment
the port lands, and the tempting edit (delete the loop, or relax to `assertDoesNotThrow`) removes an
assertion. The safe rewrite is the one the message hints at: assert
`assertNotSame(appClass, oracle.historicalClass(simple))` for all four types — which is what
`sameNameDistinctClasses` already does for `RealValue` and which is the assertion that actually
survives S4.

---

## What I could not refute

* The loader is genuinely parent-last for the two colliding namespaces, with the platform loader as
  parent and no fallback. Verified from source and by execution.
* The author's "platform parent is not sufficient under JPMS" finding is correct and independently
  reproducible; it is not folklore.
* `assertIsolated` at `open()` detects a self-comparing loader and fails the whole test class loudly.
  Verified by mutation with a stand-in port present.
* `module-info.java`, `use-core/src/main`, `use-gui`, `use-assembly`, every pom and every pre-existing
  test are untouched. Verified by `git diff`.
* The committed jars are byte-identical to the reference copies, the digests in the source match, and
  the two jars cannot shadow each other.
* No `assumeTrue`, no `@Disabled`, no skip path, no static-initialiser hazard.

## What I did not check

Numeric fidelity of the stub's formulas, the contents of the two committed TSVs, the S1 test-count
arithmetic, the `InputGenerator` corpora as *coverage*, and the `OPAQUE`/`SEQUENCE` unwrapping paths
beyond confirming they are deterministic across two independent oracles. Those belong to other
dimensions.
