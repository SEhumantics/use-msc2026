# Stage S1 — static review of the post-audit fix commits

**Reviewer role:** static refuter. **No Maven was run** (another agent owns Maven this phase).
Every claim below is produced by `git`, `grep`, `sed`, `unzip` and `javap` against the working tree
at `HEAD`, or is an explicitly-labelled derivation from reading the code. Nothing here is a
measured test result, and nothing here is presented as one.

**Range reviewed:** `97f9f2c3..HEAD` on `port-uncertainty-2`.

```
$ git log --oneline 97f9f2c3..HEAD
3959127f docs(port2): correct assertIsolated's overstated Javadoc (F9)
cf9d2f45 S3 fix: stop the differential harness scoring its own failures as agreement
3cb92468 docs(port2): audit of the S2 specification — citation hit rate, B5/B8/B6/B11, inventory completeness
```

**Verdict: DEFECTIVE.** The headline defect (D1) is genuinely and correctly fixed, and I could not
refute it. But the fix is *one-sided*: the "harness failure must never be scored as agreement"
invariant is enforced inside `HistoricalOracle` only, and the same failure shape survives in two
other places that the fix left untouched — `DifferentialSweep`'s own null-return handling and
`StubCandidate`. In addition, F2 replaced an invisible failure with a **mislabelled** one, and one
of the eleven new regression tests pins nothing.

---

## 0. Scope discipline — CLEAN

```
$ git diff --name-status 30d480db..HEAD -- '*/src/main/*'
(empty)

$ git diff --name-status 97f9f2c3..HEAD
A	docs/port2/audit-02-specification.md          <- commit 3cb92468, another agent
M	use-core/src/test/java/org/tzi/use/uncertainty/differential/DiffReportWriter.java
M	.../DiffRow.java
M	.../DiffVerdict.java
A	.../DifferentialHarnessRegressionTest.java
M	.../DifferentialSweep.java
A	.../HarnessMarshallingException.java
M	.../HistoricalOracle.java
M	.../UValue.java
M	.../UncertaintyDifferentialSmokeTest.java
```

No `src/main`, no pom, no `module-info.java`, no pre-existing upstream test. `HistoricalOracleIsolationTest.java`
and `InputGenerator.java` are untouched. Commit separation holds: `cf9d2f45` is behaviour,
`3959127f` is documentation-only — verified by stripping comment lines from its diff, which leaves
nothing:

```
$ git show 3959127f -- . | grep -E "^[+-]" | grep -vE "^[+-][+-]" | grep -vE "^[+-]\s*(\*|/\*|//)"
(empty)
```

## 1. Jupiter only — CLEAN

```
$ grep -rn "org.junit.Test\|junit.framework\|org.junit.Assert\|@RunWith\|org.junit.Before\b\|org.junit.After\b" \
    use-core/src/test/java/org/tzi/use/uncertainty/
(no matches, exit 1)
```

`DifferentialHarnessRegressionTest.java:3-14` imports only `org.junit.jupiter.api.*`. `use-core/pom.xml:63-67`
declares `junit-jupiter 5.7.0` and there is no `junit-vintage-engine` anywhere in the reactor, so a
JUnit 3/4 test would indeed have been silently dead. The class is package-private with a default
constructor and its name ends in `Test`, so default surefire includes pick it up.

---

# Defects

## D-1 (MAJOR) — a `Candidate` returning Java `null` is still scored as agreement when both sides do it

`DifferentialSweep.java:133-146`:

```java
private static Outcome apply(Candidate candidate, UOp op, List<UValue> tuple) {
    try {
        UValue produced = candidate.invoke(op, tuple);
        Objects.requireNonNull(produced, () -> candidate.name() + " returned Java null from " ...);
        return Outcome.returned(produced);
    } catch (HarnessMarshallingException e) {
        return Outcome.harnessError(e);
    } catch (Exception e) {
        return Outcome.threw(e);
```

`Objects.requireNonNull` throws `NullPointerException`. `NullPointerException` is not a
`HarnessMarshallingException`, so it falls into `catch (Exception e)` and becomes
`Outcome.threw` — i.e. **a throw by the code under test**. `classify` (`DifferentialSweep.java:169-176`)
then compares throwable class names:

```java
boolean same = ref.thrown.getClass().getName().equals(sub.thrown.getClass().getName());
... same ? DiffVerdict.AGREE_THROWN : DiffVerdict.DIFFER_THROWN
```

Both sides return `null` ⇒ both raise `java.lang.NullPointerException` ⇒ **`AGREE_THROWN`** ⇒
`isAgreement() == true` ⇒ zero disagreements. That is precisely the D1 shape: a violation of the
`Candidate` contract, detected by the harness, before any comparison is possible, rendered as
agreement.

The fix's own Javadoc calls this out as deliberate — "It is now a recorded throw"
(`DifferentialSweep.java:120-123`) — which is the wrong call by the fix's own stated principle:
`HarnessMarshallingException`'s Javadoc says a harness failure "is the absence of a measurement, not
a measurement that two sides happen to share". A `null` return is exactly that. It should raise
`HarnessMarshallingException`, not `NullPointerException`.

The regression test does not catch it: `candidateReturningNullIsRecorded`
(`DifferentialHarnessRegressionTest.java:180-194`) pairs `ReturnsNull` against
`StubCandidate.faithful()`, i.e. null-vs-value, which lands on `MIXED`. The null-vs-null case is
never exercised.

## D-2 (MAJOR) — the `HarnessMarshallingException` invariant is enforced on one side only; D1 survives verbatim in `StubCandidate`

`Candidate.java` was **not modified** by the fix (see the `git diff --name-status` above), even
though `HarnessMarshallingException`'s Javadoc claims the type is "Unchecked so that
`Candidate#invoke(UOp, List)` … keep their signatures". The interface contract
(`Candidate.java:21-28`) still says only "`@throws Throwable` whatever the implementation under test
throws". Nothing tells a future `Candidate` author that a marshalling failure has to be signalled
with the new type, and nothing enforces it.

`StubCandidate.java` was also not modified, and it still signals harness-level failures with plain
`IllegalArgumentException`:

```
$ grep -n "IllegalArgumentException" .../StubCandidate.java
81:  throw new IllegalArgumentException(op.key() + " needs " + op.arity() + " values, got " + args.size());
85:  throw new IllegalArgumentException(op.key() + " needs a UREAL receiver, got " + receiver.canonical());
120: throw new IllegalArgumentException(op.key() + " cannot take " + v.canonical());
```

**Derivation (not a measurement — Maven was not run).** `StubCandidate.supports()` returns true for
`URealValue.add(value)` (`StubCandidate.java:45,73-75`). `InputGenerator.uIntegerBoundaries()` has
13 entries (counted in the source), so a `sweepBinary` over it is 169 rows. For every row,
`receiver.kind() == UINTEGER != UREAL`, so line 85 throws. Therefore

```java
new DifferentialSweep(StubCandidate.faithful(), StubCandidate.faithful(), 1L)
    .sweepBinary(UOp.binary("URealValue", "add"),
                 InputGenerator.uIntegerBoundaries(), InputGenerator.uIntegerBoundaries())
```

yields, by `classify` line 169, `169 rows, AGREE_THROWN=169, disagreements 0` — the exact tally the
fix commit message and `HarnessMarshallingException`'s Javadoc cite as the defect being eliminated.
It is eliminated for `HistoricalOracle` and left intact for the only other committed `Candidate`.

Why this matters beyond a test double: from S4 the **ported implementation is the subject**. If its
adapter follows `StubCandidate`'s convention (which is the only worked example in the tree), a
subject-side marshalling failure that coincides with a genuine historical throw of the same class
scores `AGREE_THROWN`. The `HistoricalOracle`-first ordering in `classify` does not protect against
that, because the historical side did not fail.

## D-3 (MAJOR) — F2 makes the harness write a factually false statement into its own evidence file

`supports()` now returns `false` for any receiver outside `MARSHALLABLE_RECEIVERS`
(`HistoricalOracle.java:441-443`). `DifferentialSweep.run` then emits an `UNSUPPORTED` row whose
note is built at `DifferentialSweep.java:100-102`:

```java
String note = (!refSupports ? reference.name() + " does not implement " + op.key() : "")
```

so a sweep of `SBooleanValue.and` writes `historical does not implement SBooleanValue.and(value)`
into `target/differential/*.tsv`. And `DiffVerdict.java:28` documents the verdict as
"At least one side does not implement the operation at all."

Both statements are false. From the vendored jar that the harness itself loads
(`use.jar`, sha256 `80ac8ae4…`, matching `HistoricalOracle.USE_JAR_SHA256`):

```
$ javap -p org/tzi/use/uml/ocl/value/SBooleanValue.class | grep -E "\band\b|\bnot\b"
  public org.tzi.use.uml.ocl.value.SBooleanValue and(org.tzi.use.uml.ocl.value.Value);
  public org.tzi.use.uml.ocl.value.UncertainBooleanValue not();
```

F2 traded an *invisible* failure for a *mislabelled* one. "The historical implementation lacks this
operation" and "this harness cannot construct this receiver" are semantically opposite findings —
the first is a fact about the port's target, the second is a fact about the instrument — and after
this fix they are indistinguishable in both the verdict column and the note column. A reader of the
report, or a later stage that tallies `UNSUPPORTED` as "nothing to port here", is misled.

The minimal correct shape is a distinct verdict (e.g. `UNMARSHALLABLE`) or at minimum a note that
says why. The regression test `unmarshallableReceiverTypeIsUnsupported`
(`DifferentialHarnessRegressionTest.java:138-155`) asserts only `DiffVerdict.UNSUPPORTED` and never
inspects `row.note()`, so it locks the wrong text in.

## D-4 (MAJOR) — `supportsSwallowsOnlyAMissingMethod` pins nothing; it passes verbatim on the pre-fix code

`DifferentialHarnessRegressionTest.java:157-174` contains three assertions:

1. `assertFalse(oracle.supports(UOp.unary("URealValue", "noSuchOperationExists")))`
2. `assertTrue(RuntimeException.class.isAssignableFrom(HistoricalOracleUnavailableException.class))`
3. `assertFalse(NoSuchHistoricalMethodException.class.isAssignableFrom(HistoricalOracleUnavailableException.class))`

The pre-fix `supports()` was (`git show 97f9f2c3:.../HistoricalOracle.java`):

```java
public boolean supports(UOp op) {
    try { resolve(op); return true; } catch (RuntimeException e) { return false; }
}
```

Assertion 1 passes under that body too: the resolution failure is a `RuntimeException` and is
swallowed either way. Assertions 2 and 3 are statements about the static type hierarchy
(`HistoricalOracle.java:911` `extends IllegalStateException`; `:932` `extends IllegalArgumentException`)
and are independent of the `catch` clause entirely. **Reverting `catch (NoSuchHistoricalMethodException e)`
to `catch (RuntimeException e)` leaves all three assertions green.** The test therefore does not
regress-protect F5 at all.

Compounding this, the F5 path is unreachable as shipped. `supports()` reaches `load()` only through
`resolve()`, and the constructor (`HistoricalOracle.java:163-170`) eagerly loads and caches all
twelve names — which is a superset of the eight in `MARSHALLABLE_RECEIVERS`
(`HistoricalOracle.java:127-129`) that `supports()` will even look at. `load()` returns from
`classes` (a `ConcurrentHashMap`) without executing its mapping function, so
`HistoricalOracleUnavailableException` cannot originate there. F5 is currently dead defensive code
guarded by a tautological test.

## D-5 (MINOR) — "byte for byte" is false; the golden comparison is line-based and line-ending-blind

`DiffReportWriter.java:16-17` ("compares it against a committed golden") and `:187` ("Compares a
freshly written report, byte for byte") are contradicted by the implementation, which reads both
sides with `Files.readAllLines` (`:206`, `:221`, `:242-247`) and compares `String`s.
`Files.readAllLines` treats `\n`, `\r\n` and `\r` all as terminators and discards them, and cannot
see a missing or extra trailing newline, or a leading BOM. A golden checked out under
`core.autocrlf=true`, or one that lost its final newline to an editor, compares equal.

This is not a behavioural hazard in itself, but the porter's report and the class Javadoc both use
"byte-for-byte" as the evidence claim for determinism, and the code does not establish it.

## D-6 (MINOR) — `supports()` validates the receiver only, while its Javadoc claims more

`HistoricalOracle.java:421` — "Whether this oracle can actually be driven through `op`" — and the
enumerated "Two independent conditions, and both are load-bearing" are both about the *receiver*
and the *method*. Nothing checks that the **argument** kinds are marshallable, and
`toHistorical` has no `case SEQUENCE`, so `UValue.sequence(...)` as an argument yields
`supports() == true` followed by `HarnessMarshallingException` on every row. That is now visible
(`HARNESS_ERROR` rather than `AGREE_THROWN`), so it is no longer a correctness hole — but the
Javadoc promises a guarantee the method does not provide, and F2's stated design ("the whole basis
on which `supports(UOp)` decides that an operation is reachable", `HistoricalOracle.java:122-124`)
is only half implemented.

## D-7 (MINOR) — three harness-side throw paths in `HistoricalOracle.invoke` still raise non-`HarnessMarshallingException` types

Audit item 2 asked for every throw site. `invoke()` (`HistoricalOracle.java:453-494`) can still exit
with a plain `Exception` for a reason that is the harness's, not the code under test's:

| Site | Type | Scored by `DifferentialSweep.java:142` as |
|---|---|---|
| `checkOpen()` — `HistoricalOracle.java:868` (reached from `invoke` :454 and `toHistorical` :576) | `IllegalStateException` | code-under-test throw |
| `resolve()` → `HistoricalOracle.java:523`, class at `:932` | `NoSuchHistoricalMethodException extends IllegalArgumentException` | code-under-test throw |
| `load()` → `HistoricalOracle.java:406`, class at `:911` | `HistoricalOracleUnavailableException extends IllegalStateException` | code-under-test throw |

Inside a sweep all three are screened off by the `supports()` pre-check and by the eager class-cache,
so I do **not** claim a reachable D1 recurrence here. They are reachable through the public
`invoke(UOp, List)` and `call(...)` entry points, which the smoke test uses directly
(`UncertaintyDifferentialSmokeTest.java:158-198`). Two oracles closed mid-run would produce
`AGREE_THROWN` on `IllegalStateException`.

I checked the paths that *are* on the hot path and they are clean: `marshal` (:535, :545),
`numeric` (:558), the receiver `isInstance` guard (:466-470), the `toHistorical` constructor
wrappers (:610-615), and the `fromHistorical` unwrap wrappers (:673-678) all raise
`HarnessMarshallingException`. The `(Double)/(Integer)/(Boolean)/(String)` casts in `d/i/b/s`
(:847-861) could produce an unwrapped `ClassCastException`, but I confirmed by `javap -p` that every
accessor the switch names returns the matching primitive/`String` on all eight modelled classes, so
that branch is not reachable today:

```
== URealValue      public double value();   public double uncertainty();
== UIntegerValue   public int value();      public double uncertainty();
== UBooleanValue   public boolean value();  public double probability();
== UStringValue    public java.lang.String value();  public double confidence();
== RealValue/IntegerValue/BooleanValue/StringValue  public double/int/boolean/String value();
```

## D-8 (MINOR) — `reportDir()`/`goldenDir()` asymmetry, and an unexercised refresh path

`reportDir()` (`DiffReportWriter.java:165-167`) is unconditionally `$cwd/target/differential`;
`goldenDir()` (`:174-184`) still walks up one level. Under surefire (`$cwd` = module dir) both
resolve correctly, and a repository-root invocation still resolves *consistently* (root/target vs
root/docs/port2), so no comparison breaks — but the two helpers now use different location
strategies for no stated reason, and neither invocation is tested.

Separately, the `-Duse.differential.golden.refresh=true` escape hatch
(`DiffReportWriter.java:207-215`) is the only documented recovery from a golden mismatch and, per
the porter's own report, has never been executed. It is also read with `Boolean.getBoolean`, i.e.
from the *forked surefire JVM's* system properties; whether the reactor's surefire configuration
forwards command-line `-D` into the fork is not established anywhere in the tree (neither
`pom.xml` nor `use-core/pom.xml` configures `maven-surefire-plugin` at all —
`use-core/pom.xml:311` configures failsafe only). **UNVERIFIED**: I did not run Maven and cannot
confirm the documented recovery command works.

## D-9 (MINOR, consequence not defect) — declared coverage loss

F2 removes an entire receiver family from the instrument's reach. `docs/port2/spec-parts/`
carries five operation tables of comparable size (`grep -c '^| \`'`: SBoolean 34, UBoolean 36,
UInteger 33, UReal 33, UString 37), so roughly a fifth of the specified operation inventory is now
permanently `UNSUPPORTED`. The collection operations named in the spec's R16 risk
(`CollectionValue.uIncludes`/`uExcludes`/`uCountC`) are likewise unreachable, since neither
`SequenceValue` nor `SetValue` is in `MARSHALLABLE_RECEIVERS`. This is honest and documented, but it
should be carried forward as an explicit acceptance-criteria constraint, not left in a Javadoc — and
see D-3 for why the report currently mislabels it.

---

# Confirmed fixed

These I actively tried to refute and could not.

### `HARNESS_ERROR` is genuinely non-agreement in every consumer (audit item 1)

I enumerated every reference to the enum and to the verdict accessors across the whole test tree:

```
$ grep -rn "DiffVerdict\.\|isAgreement\|AGREE_THROWN\|disagreements()\|count(" \
    use-core/src/test/java/org/tzi/use/uncertainty/
```

Every partition routes through the single predicate:

* `DiffVerdict.isAgreement()` (`DiffVerdict.java:44-46`) — `AGREE || AGREE_THROWN`; `HARNESS_ERROR` false.
* `Result.disagreements()` (`DifferentialSweep.java:289-296`) — `!row.verdict().isAgreement()`; includes it.
* `Result` tally (`:246-251`) — pre-seeds an `EnumMap` from `DiffVerdict.values()`, so the new
  constant is counted, not dropped.
* `Result.summary()` (`:301-310`) — prints every non-zero entry of that map.
* `DiffReportWriter.writeAll` (`:117-126`, `:138-140`) — iterates `DiffVerdict.values()`, so a
  `# verdict.HARNESS_ERROR` header is emitted whenever the count is non-zero.
* `UncertaintyDifferentialSmokeTest.java:72` asserts `disagreements()` is empty, so a
  `HARNESS_ERROR` row fails `smokeURealAdd`. `smokeDetectsAWrongSubject` (`:108-110`) only asserts
  `DIFFER > 0`, but its golden pins the exact header block (`# verdict.AGREE 558`,
  `# verdict.DIFFER 226`, nothing else), so a stray `HARNESS_ERROR` would fail the golden compare.

`classify` checks the harness-error population **first** (`DifferentialSweep.java:155-168`), before
either throw branch, so it can never be merged into `AGREE_THROWN`. Catch order in `apply`
(`:141-143`) is `HarnessMarshallingException` then `Exception`, which is legal and correct
(`HarnessMarshallingException extends RuntimeException`). The column marker is textually distinct
(`DiffRow.java:78-84`, `HARNESS_ERROR:` vs `THROWN:`).

### The D1 mechanism is genuinely closed

`HistoricalOracle.invoke` :464-470 loads the declared receiver class and rejects a mismatched
instance with `HarnessMarshallingException` *before* `method.invoke`. I checked the one way this
could silently not fire — a subtype relation between the marshalled class and the declared receiver:

```
$ javap -p org/tzi/use/uml/ocl/value/UIntegerValue.class | head -1
public class org.tzi.use.uml.ocl.value.UIntegerValue extends org.tzi.use.uml.ocl.value.UncertainValue
$ javap -p org/tzi/use/uml/ocl/value/URealValue.class | head -1
public class org.tzi.use.uml.ocl.value.URealValue extends org.tzi.use.uml.ocl.value.UncertainValue
```

They are siblings, not sub/superclass (unlike upstream `IntegerValue`/`RealValue`), so
`URealValue.isInstance(UIntegerValue)` is false and all 169 rows take the guard. The 169 is
`13 × 13` from `InputGenerator.uIntegerBoundaries()`, which I counted in the source. The D1
regression test (`DifferentialHarnessRegressionTest.java:76-99`) asserts
`count(AGREE_THROWN) == 0`, `count(HARNESS_ERROR) == 169` and `disagreements() == 169`, all of which
fail on the pre-fix `classify`. **This test genuinely pins.**

### `supports()` does not undershoot (audit item 3)

`MARSHALLABLE_RECEIVERS` (`HistoricalOracle.java:127-129`) is exactly the eight classes the
`toHistorical` switch (`:579-604`) can build, one per `UValue.Kind` that has a constructor case. I
listed every candidate receiver in the vendored jar:

```
$ ls org/tzi/use/uml/ocl/value/ | grep -iE "^U|^S"
SBooleanValue  SBooleanValue$Builder  SequenceValue  SetValue  StringValue
UBooleanValue  UIntegerValue  URealValue  UStringValue
UncertainBooleanValue  UncertainValue  UndefinedValue  UnlimitedNaturalValue
```

`UncertainValue` and `UncertainBooleanValue` are `abstract` (`javap` first line), so they cannot be
receivers of a marshalled instance anyway; `SequenceValue`/`SetValue` have no `toHistorical` case,
so excluding them is correct. **No operation the harness can actually drive was newly excluded.**
The only residual over-reach is naming an operation on a supertype (`UOp.of("Value", …)`), which no
committed op does; `UOp.key()` includes the receiver type, so the `methods` cache cannot alias two
receivers onto one `Method`.

### The OPAQUE/locale fix is sound (audit item 4)

The `%5.3f` claim is confirmed against the vendored `atenearesearchgroup.uncertainty.jar`
(sha256 `53b2a43f…`, matching `HistoricalOracle.UNCERTAINTY_JAR_SHA256`):

```
$ javap -p -c uDataTypes/UInteger.class  →  ldc "UInteger(%d, %5.3f)"           String.format(String;[Object;)
$ javap -p -c uDataTypes/UReal.class     →  ldc "UReal(%5.3f, %5.3f)"           String.format(String;[Object;)
$ javap -p -c uDataTypes/SBoolean.class  →  ldc "SBoolean(%5.3f, %5.3f, %5.3f, %5.3f)"  String.format(String;[Object;)
$ javap -p -c uDataTypes/UString.class   →  ldc "UString(%s, %5.3f)"            String.format(String;[Object;)
$ javap -p -c uDataTypes/UUnlimitedNatural.class → ldc "UUnlimitedNatural(%d, %5.3f)"   String.format(String;[Object;)
```

All five use the no-`Locale` overload, so lossy and locale-bound as claimed. (A sixth,
`UBoolean(%b, %5.3f)`, has the same shape and is not listed in the Javadoc — harmless omission.)

The replacement is genuinely locale-free and exact: `appendOpaque` (`HistoricalOracle.java:722-806`)
uses only `Double.toString` / `Float.toString` / `StringBuilder.append` / `UValue.quote`, and
`UValue.quote` (`UValue.java` hex branch) explicitly hand-rolls hex "because `String.format` is
locale-sensitive". No `String.format`, `%f`, `NumberFormat` or foreign `toString()` remains on the
canonical path — I grepped for all of them.

I also checked that the new representation is **stable**, which the fix depends on but does not
test. `SBooleanValue.TRUE` renders as `Value.fType` + `SBooleanValue.sBoolean`:

```
$ javap -p org/tzi/use/uml/ocl/value/SBooleanValue.class
  public static final SBooleanValue TRUE;
  private uDataTypes.SBoolean sBoolean;
$ javap -p org/tzi/use/uml/ocl/value/Value.class      → private org.tzi.use.uml.ocl.type.Type fType;
$ javap -p uDataTypes/SBoolean.class                  → protected double b, d, u, a, relativeWeight;
$ javap -p org/tzi/use/uml/ocl/type/{SBooleanType,UncertainBooleanType,UncertainType,TypeImpl}.class
  (no instance fields anywhere in the chain)
```

So the `Type` recursion terminates at `…SBooleanType{}` with no fields — deterministic, well inside
`OPAQUE_MAX_DEPTH = 6`, and no unordered collection is reached. `IsolatedJarClassLoader.ISOLATED_PREFIXES`
is `["org.tzi.use.", "uDataTypes."]` (`IsolatedJarClassLoader.java:51-52`), so the `uDataTypes.SBoolean`
field is rendered rather than refused, and the test's `contains("uDataTypes.SBoolean")` assertion
(`DifferentialHarnessRegressionTest.java:267`) is satisfied by construction.

### F3, F6, F7(partial), F8 pin correctly

Comparing against `git show 97f9f2c3:.../DifferentialSweep.java`:

* Old `apply` was `try { return Outcome.returned(candidate.invoke(op, tuple)); } catch (Throwable t) { return Outcome.threw(t); }`.
  So `errorsAreNotComparableData` (`assertThrows(StackOverflowError.class, …)`) **fails on the old
  code** (the sweep returned normally). Pins F8.
* `candidateReturningNullIsRecorded` **fails on the old code** (classify NPE'd on
  `sub.value.canonical()` and lost every row). Pins F7's crash, though see D-1 for what it misses.
* `zeroRowSweepIsRefused` **fails on the old code**: the old guard tested `results.isEmpty()`, and
  the test deliberately passes `List.of(empty)` and `List.of(empty, empty)`. The new guard is on
  `rowTotal` (`DiffReportWriter.java:94-103`) and its message contains `"0 rows in total"`, so the
  `contains("0 rows")` assertion holds. Pins F3.
* `voidIsDistinctFromNull` requires `UValue.voidValue()` / `Kind.VOID`, both new; the old
  `fromHistorical` mapped `Method.invoke`'s `null` for void to `Kind.NULL`. `setTypeToRuntimeType`
  is confirmed `public void` on `Value` (`javap`), inherited by `URealValue`. Pins F6.
* `unmarshallableReceiverTypeIsUnsupported` **fails on the old code** (`supports()` returned true).
  Pins F2's mechanism — but locks in the wrong note text, see D-3.
* `marshallingFailureOnBothSidesIsNotAgreement` / `…OnOneSide…` pin F1's classification.
* `opaqueRepresentationIsExactAndLocaleIndependent` / `unrepresentableObjectIsRefused` pin F4.

**Ten of eleven new tests pin a property that fails pre-fix. The exception is D-4.**

---

# Summary table

| ID | Sev | File | One line |
|---|---|---|---|
| D-1 | MAJOR | `DifferentialSweep.java:133-146,169-176` | both candidates returning Java `null` ⇒ `AGREE_THROWN`; a contract violation scored as agreement |
| D-2 | MAJOR | `Candidate.java` (untouched), `StubCandidate.java:81,85,120` | the new invariant is not on the interface and the other `Candidate` violates it; D1's 169/AGREE_THROWN tally reproduces with two stubs |
| D-3 | MAJOR | `DifferentialSweep.java:100-102`, `DiffVerdict.java:28` | report asserts "historical does not implement SBooleanValue.and(value)"; `javap` shows it does |
| D-4 | MAJOR | `DifferentialHarnessRegressionTest.java:157-174` | all three assertions pass on the pre-fix `catch (RuntimeException)`; F5 is unpinned and unreachable |
| D-5 | MINOR | `DiffReportWriter.java:187,206,221,242` | "byte for byte" is line-based via `readAllLines`; blind to line endings and trailing newline |
| D-6 | MINOR | `HistoricalOracle.java:421-443` | `supports()` checks the receiver only; its Javadoc claims the operation is drivable |
| D-7 | MINOR | `HistoricalOracle.java:406,523,868` | three harness-side throw paths still raise plain `IllegalState`/`IllegalArgument` |
| D-8 | MINOR | `DiffReportWriter.java:165-184,207-215` | `reportDir`/`goldenDir` use different strategies; refresh path never executed, forwarding of `-D` into the surefire fork UNVERIFIED |
| D-9 | MINOR | `HistoricalOracle.java:122-129` | ~1/5 of the specified operation inventory plus all collection ops are now permanently outside the instrument |

**Nothing in this document was produced by running the test suite.** Every "fails on the old code"
statement is a derivation from the pre-fix source at `97f9f2c3` compared against the assertion text,
and is labelled as such.
