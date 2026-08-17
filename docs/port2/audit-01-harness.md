# Audit 01 — Is the differential harness fit to be the oracle for S4–S7?

> **⚠ FORWARD POINTER (added 2026-08-17, after round 4) — the verdict vocabulary has changed since
> this document was written.** This report is left exactly as it was written; it was true on its
> date. Two changes to `DiffVerdict` have happened since:
>
> * **`AGREE_THROWN` and `DIFFER_THROWN` were deleted** in commit `e8b73e48`. Two throws are never
>   an agreement, whatever their classes. Wherever this document names either constant as a live
>   verdict it is describing the code as it stood before `e8b73e48`. They are replaced by the single
>   non-agreement **`BOTH_THREW`** (whose note always carries both classes *and* both messages), plus
>   the opt-in, human-authored **`ACCEPTED_THROW`**.
> * **`UNMEASURABLE` was added** in commit `93e038ac`, for rows where neither side carried an
>   observation (`VOID`/`VOID`, `NULL`/`NULL`). Those rows used to be scored `AGREE`.
>
> The current vocabulary is `AGREE, ACCEPTED_THROW, DIFFER, BOTH_THREW, MIXED, UNMEASURABLE,
> UNSUPPORTED, HARNESS_ERROR`, defined in
> `use-core/src/test/java/org/tzi/use/uncertainty/differential/DiffVerdict.java` and summarised in
> [`harness-contract.md`](harness-contract.md). **The current S1 verdict is `stage-01.md` §10 — not
> this file.**


Independent adversarial audit of the S1 harness on branch `port-uncertainty-2`.
Read-only on the repository except for this file. **No Maven was run.** Everything below was
executed with `javac` / `java` / `javap` / `unzip` against the already-built
`use-core/target/classes` and `use-core/target/test-classes`, or read straight from source.
Java 21.0.11. All output is pasted verbatim; scratch drivers live in
`/tmp/claude-1000/-home-xoruser-msc-4/5a883e17-9055-4019-8f36-a743005556fa/scratchpad/`.

**Verdict: UNSOUND as an oracle for S4–S7 in its current shape. Sound as a class-loader isolation
mechanism.**

The isolation — the part everyone worries about — is real, and I could not break it. The
*scoring* layer is the problem. D1 is confirmed exactly as filed, and it is worse than the S1
refutation records: the single largest operation block in the S2 specification (SBoolean, 39
operations) is a ready-made D1 instance in which `supports()` returns `true`, every row throws from
the harness's own marshalling, and the sweep would report 100 % agreement. Separately, the harness's
headline exactness guarantee ("comparison is exact, a harness that silently rounds cannot detect a
rounding regression", `UValue.java:17-21`) is **false on the `OPAQUE` branch**, which rounds to three
decimal places and is locale-dependent.

Fix surface: ~40 lines across `DiffVerdict`, `DifferentialSweep`, `DiffRow`, `HistoricalOracle`,
`DiffReportWriter`. Small. But S4 must not start until it is done, because every one of these
defects makes a *green* number, not a red one.

---

## 0. Summary of findings

| # | Finding | Severity | Verdict |
|---|---|---|---|
| **H1** | D1 reproduced: `AGREE_THROWN` scores harness-marshalling throws as agreement | CRITICAL | CONFIRMED |
| **H2** | SBoolean (39 ops, largest S2 block) is a live D1 instance — `supports()` says `true`, nothing runs | CRITICAL | NEW |
| **H3** | `OPAQUE` canonical form rounds to 3 dp — refutes `UValue.java:17-21` exactness | MAJOR | NEW |
| **H4** | `OPAQUE` canonical form is locale-dependent — refutes `stage-01.md` §6.6 and the byte-identical report guarantee | MAJOR | NEW |
| **H5** | `void` and `null` both canonicalise to `NULL`; no post-state observation at all | MAJOR | NEW |
| **H6** | `HistoricalOracle.supports()` swallows `HistoricalOracleUnavailableException` — a broken oracle reports as "not implemented" | MAJOR | NEW |
| **H7** | A `Candidate` returning Java `null` aborts the whole sweep with an NPE inside `classify()` | MAJOR | NEW |
| **H8** | Zero-row sweep reads as clean and writes a report (D3) | MAJOR | CONFIRMED |
| **H9** | `apply()` catches `Throwable`, so `Error`s become comparable data points | MINOR | NEW |
| **H10** | Primitive marshalling is lossy and the `inputs` column shows the pre-coercion value | MINOR | NEW |
| **H11** | `assertIsolated()` polices a hard-coded 12-name list in one package; `uDataTypes.*` is never checked | MINOR | CONFIRMED (already noted in the S1 empirical refutation) |
| **H12** | The S1 refutation's "SWALLOW C … passes the smoke test's own pass criterion" is overstated | MINOR | PARTIALLY_WRONG |
| — | **Isolation is parent-last with no fallback, and holds** | — | CONFIRMED SOUND |
| — | **The JPMS rationale for `IsolatedJarClassLoader` is correct** | — | CONFIRMED SOUND (independently reproduced) |
| — | `uTypesResolveOnlyThroughTheOracle()` will fail when S4 lands | — | CONFIRMED (and pre-declared in source + `stage-01.md` §6.3) |

---

## 1. D1, reproduced from a cold start

### 1.1 The source claim, line by line

`DiffVerdict.java:32-34`

```java
    public boolean isAgreement() {
        return this == AGREE || this == AGREE_THROWN;
    }
```

`DifferentialSweep.java:115-121` — the marshalling of *both* sides happens inside the `try`:

```java
    private static Outcome apply(Candidate candidate, UOp op, List<UValue> tuple) {
        try {
            return Outcome.returned(candidate.invoke(op, tuple));
        } catch (Throwable t) {
            return Outcome.threw(t);
        }
    }
```

`DifferentialSweep.java:124-131` — comparison is class name only, and the messages are recorded
**only when they differ**:

```java
        if (ref.thrown != null && sub.thrown != null) {
            boolean same = ref.thrown.getClass().getName().equals(sub.thrown.getClass().getName());
            String note = same ? "" : "reference message: " + safeMessage(ref.thrown)
                    + " / subject message: " + safeMessage(sub.thrown);
```

`DiffRow.java:71-73` — the result columns keep the class name and nothing else:

```java
    public static String thrown(Throwable t) {
        return "THROWN:" + t.getClass().getName();
    }
```

`DifferentialSweep.java:226-234` — `disagreements()`, the documented pass criterion
("An empty list is the only clean outcome"), filters on `isAgreement()`.

And `HistoricalOracle.invoke()` throws before the historical method is ever reached, at
`HistoricalOracle.java:369-381`:

```java
        if (args.size() != op.arity()) {
            throw new IllegalArgumentException(...);
        }
        Method method = resolve(op);
        Object receiver = toHistorical(args.get(0));
        Class<?> receiverClass = load(op.receiverType());
        if (!receiverClass.isInstance(receiver)) {
            throw new IllegalArgumentException(
                    op.key() + " expects a receiver of " + receiverClass.getName() + " but the supplied "
                            + args.get(0).canonical() + " maps to " + receiver.getClass().getName());
        }
```

plus `toHistorical()` at `HistoricalOracle.java:492-501` (`IllegalArgumentException` for an
unmappable kind, `IllegalStateException` when the historical constructor rejects the value).

The chain is complete on inspection: **a throw raised by the harness's own marshalling is
indistinguishable, in every column of the report, from a throw raised by the code under test.**

### 1.2 Executed

```bash
SP=/tmp/.../scratchpad; R=/home/xoruser/msc-4/use-msc2026
CP="$SP:$R/use-core/target/test-classes:$R/use-core/target/classes"
javac -cp "$CP" -d $SP AuditD1.java && (cd /tmp/auditrun && java -cp "$CP" AuditD1)
```

The driver sweeps `URealValue.add(value)` over `InputGenerator.uIntegerBoundaries()` — 13 values,
so 169 tuples. Neither side can build a receiver: the oracle rejects it at the
`receiverClass.isInstance` check above, `StubCandidate` rejects it at `StubCandidate.java:84-86`.

```
### D1 REPRODUCTION
corpus size          13
summary              URealValue.add(value): 169 rows, AGREE_THROWN=169
rowCount             169
disagreements()      0
AGREE_THROWN         169
isAgreement()        true
agreement rate       100.0%
row 0                0	URealValue.add(value)	UINTEGER(0,0.0) | UINTEGER(0,0.0)	THROWN:java.lang.IllegalArgumentException	THROWN:java.lang.IllegalArgumentException	AGREE_THROWN	
row 168              168	URealValue.add(value)	UINTEGER(1,-1.0) | UINTEGER(1,-1.0)	THROWN:java.lang.IllegalArgumentException	THROWN:java.lang.IllegalArgumentException	AGREE_THROWN	
SMOKE-STYLE GATE assertEquals(List.of(), disagreements()) -> PASSES (green)
```

**D1 is CONFIRMED, character for character with the S1 refutation's `SWALLOW-B` block.** 169 rows,
100 % agreement, zero disagreements, empty `note` column, and the historical `URealValue.add` was
never entered once.

### 1.3 One correction to how D1 was written up (H12)

`stage-01-refutation-empirical.md` §5 heads a block "**SWALLOW C — this passes the smoke test's own
pass criterion**". That is true of the *assertion it quotes*
(`UncertaintyDifferentialSmokeTest.java:70`) but **not of the test**.
`UncertaintyDifferentialSmokeTest.java:72` is a second, stronger assertion:

```java
        assertEquals(result.rowCount(), result.count(DiffVerdict.AGREE));
```

On the no-op sweep `count(AGREE) == 0` and `rowCount() == 169`, so `smokeURealAdd` as it stands
would fail. The distinction matters: a reader could conclude the *existing* S1 smoke test is already
vacuous, and it is not. What is genuinely vulnerable is every S4–S7 sweep driver that gates on
`Result.disagreements()`, which is the criterion `DifferentialSweep.java:225` documents as
"the only clean outcome". Severity of the substance is unchanged; only the framing is wrong.

---

## 2. SBoolean is a live D1 instance, and it is the largest block in the spec (H2, NEW)

This is the finding D1 missed, and it is the one that decides whether S4 can start.

The S2 specification counts **SBoolean 39 operations** — the largest single operation block. The
historical `org.tzi.use.uml.ocl.value.SBooleanValue` **is** in `use.jar`
(`unzip -l use.jar | grep SBooleanValue` → `SBooleanValue.class`, 8 713 B, plus
`SBooleanValue$Builder.class`), so `HistoricalOracle.load("SBooleanValue")` resolves it and
`resolve(op)` finds `and(Value)`. Therefore:

```
### SCOPE OF THE ORACLE API
UValue.Kind values: [UREAL, UINTEGER, UBOOLEAN, USTRING, REAL, INTEGER, BOOLEAN, STRING, SEQUENCE, NULL, OPAQUE]
historicalClass("SBooleanValue") -> class org.tzi.use.uml.ocl.value.SBooleanValue loader=IsolatedJarClassLoader[historical-oracle, ...]
supports(SBooleanValue.and(value)) -> true
invoke -> java.lang.IllegalArgumentException: SBooleanValue.and(value) expects a receiver of org.tzi.use.uml.ocl.value.SBooleanValue but the supplied UBOOLEAN(true,0.8) maps to org.tzi.use.uml.ocl.value.UBooleanValue
```

`UValue.Kind` has **no `SBOOLEAN`**, and `HistoricalOracle.toHistorical()`
(`HistoricalOracle.java:463-502`) has no branch that can produce an `SBooleanValue`. So:

* `supports()` returns **`true`** — the sweep is *not* diverted to the visible `UNSUPPORTED` verdict;
* every single row throws `IllegalArgumentException` from `HistoricalOracle.java:378`;
* an S4 ported candidate, asked for an SBoolean receiver it also cannot build from a `UValue`, will
  throw `IllegalArgumentException` from its own marshalling;
* result: **39 operations × N inputs, 100 % `AGREE_THROWN`, zero disagreements, nothing executed.**

The same applies to `UnlimitedNaturalValue` / `uDataTypes.UUnlimitedNatural` and `uDataTypes.UEnum`.

There is a second, structural half to this. `HistoricalOracle.java:90` hard-codes the package:

```java
    private static final String VALUE_PKG = "org.tzi.use.uml.ocl.value.";
```

and `load()` (`HistoricalOracle.java:334-345`) is the *only* class resolver. `historicalClass()`,
`UOp.receiverType()` and `resolve()` all funnel through it. Measured:

```
historicalClass("SBooleanType")      -> HistoricalOracleUnavailableException: historical class org.tzi.use.uml.ocl.value.SBooleanType not present in ...
historicalClass("../type/URealType") -> HistoricalOracleUnavailableException: historical class org.tzi.use.uml.ocl.value.../type/URealType not present in ...
historicalClass("uDataTypes.SBoolean") -> HistoricalOracleUnavailableException: historical class org.tzi.use.uml.ocl.value.uDataTypes.SBoolean not present in ...
```

So the oracle can address **exactly one package**. `org.tzi.use.uml.ocl.type.*` (the type
registrations the S2 spec counts — "UInteger 12 classes / 13 registrations"),
`org.tzi.use.uml.ocl.expr.operations.StandardOperations*` and all of `uDataTypes.*` are unreachable
by design. The harness's addressable surface is strictly narrower than the port's surface, and
nothing in the harness says so.

---

## 3. Other swallow channels D1 missed

### H3 — `OPAQUE` rounds to three decimal places (MAJOR, NEW)

`UValue.java:17-21` states the harness's central guarantee:

> Comparison is by `canonical()`, which is derived from `Double#toString(double)` and is therefore
> *exact* … That is deliberate. A differential harness that silently rounds cannot detect a rounding
> regression.

That holds for `UREAL`/`UINTEGER`/`UBOOLEAN`/`USTRING`/`REAL`/`INTEGER`/`BOOLEAN`/`STRING`/`SEQUENCE`.
It **does not hold for `OPAQUE`**. `HistoricalOracle.java:550`:

```java
                    return UValue.opaque(className, String.valueOf(result));
```

`String.valueOf` calls the *historical object's* `toString()`. Every `uDataTypes` class formats with
`%5.3f`. Bytecode, `javap -c -p uDataTypes/UInteger.class`:

```
  public java.lang.String toString();
    Code:
       0: ldc           #41                 // String UInteger(%d, %5.3f)
      ...
      26: invokestatic  #45                 // Method java/lang/String.format:(Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/String;
```

The same `String.format` pattern is present in **all seven** uncertainty classes:

```bash
for f in uDataTypes/*.class; do javap -c -p "$f" | grep -qE 'String.format|DecimalFormat|NumberFormat' && echo "$f"; done
```
```
uDataTypes/SBoolean.class
uDataTypes/UBoolean.class
uDataTypes/UEnum.class
uDataTypes/UInteger.class
uDataTypes/UReal.class
uDataTypes/UString.class
uDataTypes/UUnlimitedNatural.class
```
(`uDataTypes.UReal.toString` is `UReal(%5.3f, %5.3f)`; `uDataTypes.SBoolean.toString` is
`SBoolean(%5.3f, %5.3f, %5.3f, %5.3f)`.)

Demonstrated through the public `UIntegerValue.getuInteger()`:

```
### OPAQUE ROUNDS (uDataTypes toString is %5.3f)
uncertainty 0.2500     -> OPAQUE("uDataTypes.UInteger|UInteger(3, 0.250)")
uncertainty 0.2504999  -> OPAQUE("uDataTypes.UInteger|UInteger(3, 0.250)")
harness verdict        -> AGREE
actually the same?     -> false
```

**A difference of 5 × 10⁻⁴ is scored `AGREE`.** The comparison the harness advertises as exact is,
on this branch, a three-decimal-place comparison. The spec's risk row R44 ("exact comparison may
prove too strict") is therefore only half the story: on `OPAQUE` it is far too *loose*, and nothing
records that.

Reach: 36 of 732 historical calls in a surface probe over the four U-types landed in `OPAQUE`
(§3.6 below). It becomes the dominant path the moment S4–S7 touch SBoolean or the type classes.

### H4 — `OPAQUE` is locale-dependent (MAJOR, NEW)

`stage-01.md` §6.6 states:

> **`Locale`.** Canonical forms use `Double.toString` and hand-rolled hex, both locale-independent.

`UValue.quote()` (`UValue.java:250`) even carries the comment *"String.format is locale-sensitive,
canonical forms must not be"*. But `UValue.opaque()` re-imports exactly that dependency through the
foreign `toString()`. Same driver, two locales:

```
--- EN ---
default locale   en
getuInteger()    OPAQUE("uDataTypes.UInteger|UInteger(3, 0.250)")

--- DE ---
default locale   de_DE
getuInteger()    OPAQUE("uDataTypes.UInteger|UInteger(3, 0,250)")
```

Consequences: (a) `stage-01.md` §6.6 is wrong as written; (b) `DiffReportWriter`'s documented
guarantee — *"Nothing time-dependent is written: two runs with the same seed and the same jars
produce byte-identical files"* (`DiffReportWriter.java:33-35`) — fails on any machine with a
non-`en` default locale as soon as a sweep contains an `OPAQUE` row; (c) since the smoke tests write
into the tracked `docs/port2/differential/`, that turns into a dirty working tree on a European CI
box. The S1 determinism evidence is real but was taken on one locale only.

### H5 — `void` and `null` both canonicalise to `NULL`; no post-state is ever observed (MAJOR, NEW)

`HistoricalOracle.java:505-508`:

```java
    public UValue fromHistorical(Object result) {
        if (result == null) {
            return UValue.nullValue();
        }
```

`Method.invoke` returns `null` for a `void` method, so a mutator is indistinguishable from an
accessor that returned `null`. Measured on `URealValue.setTypeToRuntimeType()`:

```
void method      kind=NULL canonical=NULL
void == null-return? true
```

The harness compares **return values only**. It never re-reads the receiver after the call. A ported
`setTypeToRuntimeType()` whose body is empty scores `AGREE` on every row, forever. Any operation
with a side effect on the receiver is outside the instrument's reach and nothing marks it as such.

### H6 — `supports()` swallows a broken oracle (MAJOR, NEW)

`HistoricalOracle.java:354-362`:

```java
    @Override
    public boolean supports(UOp op) {
        try {
            resolve(op);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }
```

`resolve()` calls `load()`, which throws `HistoricalOracleUnavailableException` — a subclass of
`IllegalStateException`, hence a `RuntimeException` — when a class is genuinely missing from the
jars. This is the one place in the harness where the documented failure policy
(`HistoricalOracle.java:54-58`: *"The oracle never degrades to a no-op and never signals 'skip'"*)
is violated. Measured:

```
### supports() SWALLOW
supports(URealValue.addd)   -> false
supports(UNoSuchValue.add)  -> false
summary                     UNoSuchValue.add(value): 484 rows, UNSUPPORTED=484
no DIFFER rows?             true
row 0                       0	UNoSuchValue.add(value)	UREAL(0.0,0.0) | UREAL(0.0,0.0)	UNSUPPORTED	UNSUPPORTED	UNSUPPORTED	historical does not implement UNoSuchValue.add(value); stub-faithful does not implement UNoSuchValue.add(value)
```

Two failure modes converge on the same benign-looking verdict: a **typo in an operation name**
(`addd`) and a **corrupt or wrong-version oracle jar** both report as "the historical side does not
implement this". `UNSUPPORTED` *is* counted as a disagreement (`DiffVerdict.java:32`), so a gate on
`disagreements()` catches it — but the diagnosis handed to the operator is wrong, and any driver
that gates on "no `DIFFER` rows" passes.

### H7 — a `null`-returning candidate aborts the entire sweep (MAJOR, NEW)

`apply()` wraps `candidate.invoke(...)` but never validates the returned reference;
`classify()` at `DifferentialSweep.java:139` dereferences it outside any `try`:

```java
        boolean agree = ref.value.canonical().equals(sub.value.canonical());
```

Measured with a `Candidate` whose `invoke` returns `null`:

```
### NULL RESULT FROM A CANDIDATE
sweep BLEW UP:       java.lang.NullPointerException: Cannot invoke "org.tzi.use.uncertainty.differential.UValue.canonical()" because "sub.value" is null
  at               org.tzi.use.uncertainty.differential.DifferentialSweep.classify(DifferentialSweep.java:139)
```

The NPE escapes `run()`, so **every row already computed is discarded**. `Candidate.invoke`'s
contract (`Candidate.java:21-28`) never says the result must be non-null, and returning `null` is
the natural mistake for a ported operation that maps to `UndefinedValue`. This is loud rather than
silent, so it is a robustness defect, not a swallow — but it will cost an S4 debugging session.

### H8 — a zero-row sweep reads as clean and gets a report written (MAJOR, D3 CONFIRMED)

`DiffReportWriter.java:62-65` guards the wrong quantity:

```java
        if (results.isEmpty()) {
            throw new IllegalArgumentException("refusing to write an empty differential report: "
                    + "a report with no rows would read as agreement");
        }
```

`results` is the list of `Result` objects, not the rows. A single `Result` with `rowCount() == 0`
sails through. `DifferentialSweep.sweep()` produces one whenever any domain is empty
(`buildTuples`, `DifferentialSweep.java:72-83`), and `Result` has no minimum-row guard anywhere.

```
### ZERO-ROW SWEEP
rowCount             0
summary              URealValue.add(value): 0 rows
disagreements empty  true
report written       /tmp/docs/port2/differential/audit-zero-row.tsv
report body:
   |# harness	differential-sweep/1
   |# seed	20260817
   |# reference	historical
   |# subject	stub-faithful
   |# sha256.use.jar	80ac8ae433b8345677472019991356950f094f4a104cfbce1f75783a7308788d
   |# sha256.atenearesearchgroup.uncertainty.jar	53b2a43feb0a0a39844a60278dd80a7d4b975ef324fb05c6db28831e835e59d0
   |# operations	URealValue.add(value)
   |# rows	0
   |index	operation	inputs	historical	ported	verdict	note
```

The report was written outside the repository (working directory `/tmp/auditrun`); nothing under
`docs/port2/differential/` was touched.

### H9 — `apply()` catches `Throwable`, so `Error`s become comparable data (MINOR, NEW)

`DifferentialSweep.java:118` catches `Throwable`, not `Exception`. A `StackOverflowError`,
`OutOfMemoryError`, `AssertionError` (from a `-ea` ported implementation) or — most plausibly here —
a `NoClassDefFoundError` raised by the deliberately fallback-free `IsolatedJarClassLoader` becomes a
row rather than a fatal. Two sides that both blow the stack on a recursive input are scored
`AGREE_THROWN`. Combined with class-name-only comparison (H1/D2) this is the difference between "the
oracle broke" and "the port matches".

### H10 — lossy primitive marshalling, and the `inputs` column lies about it (MINOR, NEW)

`HistoricalOracle.java:436-458`:

```java
            case INT:    return numeric(value).intValue();
            case DOUBLE: return numeric(value).doubleValue();
            case FLOAT:  return numeric(value).floatValue();
```

`marshal(INT, UValue.uInteger(3, 0.25))` silently discards the uncertainty; `marshal(FLOAT, …)`
narrows. Meanwhile `DifferentialSweep.java:93-96` populates the `inputs` column from
`v.canonical()`, i.e. the value *before* coercion. A report row for
`URealValue.power(float)` therefore records `UREAL(1.0E300,0.0)` for a call that actually passed a
`float` `Infinity`. The spec's F-15 row already establishes that the `float` narrowing is part of
the oracle's behaviour, so the coercion is right — but the report should say what was passed.

### H11 — `assertIsolated()` polices a hard-coded list in one package (MINOR, CONFIRMED)

`HistoricalOracle.java:112-117` checks twelve simple names, all resolved through
`load()`/`VALUE_PKG`, so **no `uDataTypes.*` class is ever isolation-checked**. The S1 empirical
refutation already noted this and demonstrated by poisoning that the loader — not the guard — is
what actually holds the line. I re-confirmed the loader independently (§5) so this stays MINOR, but
the guard's Javadoc ("*Resolve, and isolation-check, every class the harness depends on*") overstates
what it does.

### 3.6 Result-kind surface probe

To size H3/H5, I drove every zero-or-`Value`/`int`/`double`/`float`-parameter public instance method
of the four historical U-types against the first four values of each boundary corpus:

```
### RESULT-KIND SURFACE PROBE (historical side only)
calls            732
result kinds     {UREAL=84, UINTEGER=38, UBOOLEAN=112, USTRING=16, REAL=28, INTEGER=44, BOOLEAN=288, STRING=42, SEQUENCE=4, NULL=16, OPAQUE=36}
throwable kinds  {java.lang.ArithmeticException=6, java.lang.IndexOutOfBoundsException=2, java.lang.NumberFormatException=8, java.lang.RuntimeException=4, java.lang.StringIndexOutOfBoundsException=4}
OPAQUE/NULL samples:
   URealValue.type() -> OPAQUE("org.tzi.use.uml.ocl.type.URealType|UReal")
   URealValue.getRuntimeType() -> OPAQUE("org.tzi.use.uml.ocl.type.URealType|UReal")
   URealValue.setTypeToRuntimeType() -> NULL
   UIntegerValue.getuInteger() -> OPAQUE("uDataTypes.UInteger|UInteger(0, 0.000)")
   UIntegerValue.type() -> OPAQUE("org.tzi.use.uml.ocl.type.UIntegerType|UInteger")
   UBooleanValue.type() -> OPAQUE("org.tzi.use.uml.ocl.type.UBooleanType|UBoolean")
   UStringValue.type() -> OPAQUE("org.tzi.use.uml.ocl.type.UStringType|UString")
   …
```

Every `type()` / `getRuntimeType()` collapses to *class name + `toString()`*. The S2 spec's type
registrations are therefore compared on two strings only — a ported `URealType` with the wrong
supertype, wrong conformance or wrong `toString`-equal name would score `AGREE`.

---

## 4. Fix surface — one line per defect

Not applied. Sizes are for judging whether S4 can start.

| # | Minimal correct fix | Size |
|---|---|---|
| H1/D1 | Have `Candidate.invoke` marshal *before* the timed region — or, cheaper and sufficient: make `HistoricalOracle.invoke`/`toHistorical` throw a dedicated `HarnessMarshallingException`, catch it separately in `apply()`, and emit a new non-agreement verdict `HARNESS_ERROR`. | ~20 lines, 3 files |
| H2 | Add `SBOOLEAN` (and `UUNLIMITEDNATURAL`, `UENUM`) to `UValue.Kind` + `toHistorical`/`fromHistorical` branches; **or**, if SBoolean is out of scope for the harness, make `supports()` return `false` for any op whose receiver type has no `toHistorical` branch, so it lands on the visible `UNSUPPORTED`. | ~15 lines (the `supports()` route) / ~60 lines (full SBoolean support) |
| H3 | Never let a foreign `toString()` be the comparison key: in `fromHistorical`'s default branch, build the opaque repr from the object's declared fields via reflection using `Double.toString`, or refuse (`UNSUPPORTED`) rather than guess. | ~15 lines, 1 file |
| H4 | Falls out of H3. Interim: `String.format(Locale.ROOT, …)` is not available (the format is inside the jar) — so H3's field-based repr is the only real fix. | — |
| H5 | Distinguish `void` from `null`: check `method.getReturnType() == void.class` in `invoke()` and return a new `UValue.Kind.VOID`. Post-state observation is a separate, larger design change and should be recorded as a scope limit rather than built now. | ~8 lines + 1 doc paragraph |
| H6 | Let `HistoricalOracleUnavailableException` escape `supports()`; catch only `IllegalArgumentException` (the genuine "no such method"). | 1 line |
| H7 | `Objects.requireNonNull(candidate.invoke(...), …)` inside `apply()`'s `try`, so a null result becomes a recorded throw instead of an aborted sweep. | 1 line |
| H8 | Change `DiffReportWriter.writeAll`'s guard to test total row count, and add the same guard to `DifferentialSweep.Result` or to the S4 sweep driver. | 2 lines |
| H9 | Catch `Exception` in `apply()` and re-throw `Error`. | 2 lines |
| H10 | Record the marshalled argument, not the pre-coercion `UValue`, in the `inputs` column. | ~5 lines |
| H11 | Extend `assertIsolated`'s eager list to `uDataTypes.UReal`/`UInteger`/`UBoolean`/`UString`, which needs `load()` to accept a fully-qualified name. | ~10 lines |
| H12 | Correct one sentence in `stage-01-refutation-empirical.md` §5. | 1 line |

Total ≈ 40 lines for the blocking set (H1, H2-via-`supports`, H3, H6, H7, H8, H9). **None of these
is a redesign.** All of them turn a green number into an honest one, which is why none can wait until
after S4.

---

## 5. Does the isolation actually hold? Yes.

### 5.1 Parent-last, no fallback — source

`IsolatedJarClassLoader.java:75-93`:

```java
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        synchronized (getClassLoadingLock(name)) {
            Class<?> loaded = findLoadedClass(name);
            if (loaded == null) {
                if (isIsolated(name)) {
                    // Parent-last, and no fallback: if the jars do not have it, it does not exist.
                    loaded = findClass(name);
                } else {
                    loaded = super.loadClass(name, false);
                }
            }
```

`findClass` is called bare — no `try`, no delegation on failure. `ISOLATED_PREFIXES` is
`List.of("org.tzi.use.", "uDataTypes.")` (`IsolatedJarClassLoader.java:51-52`). The JVM resolves a
defined class's symbolic references through its *defining* loader's `loadClass(String)`, which
routes into this override, so transitive loads are covered too.

### 5.2 Measured

```
### ISOLATION
loader          org.tzi.use.uncertainty.differential.IsolatedJarClassLoader
parent          jdk.internal.loader.ClassLoaders$PlatformClassLoader@7ab2bfe1
parent==platform true
appRV loader    jdk.internal.loader.ClassLoaders$AppClassLoader@639fee48
hisRV loader    IsolatedJarClassLoader[historical-oracle, parent-last for org.tzi.use., uDataTypes., urls=[…/use.jar, …/atenearesearchgroup.uncertainty.jar]]
distinct?       true
  org.tzi.use.uml.ocl.value.DataTypeValueValue
      inApp=true  viaIsolated=ClassNotFoundException (no fallback)
  org.tzi.use.uncertainty.differential.UValue
      inApp=true  viaIsolated=ClassNotFoundException (no fallback)
  uDataTypes.NoSuchThing
      inApp=false  viaIsolated=ClassNotFoundException (no fallback)
  iso.getResource("historical/use.jar") -> null
  URealValue superclass loader IsolatedJarClassLoader[…]
  uDataTypes.UReal loader      IsolatedJarClassLoader[…]
```

Two classes that **exist on the application loader** and not in the jars
(`DataTypeValueValue`, and the harness's own `UValue`) are refused rather than delegated. The
historical `URealValue`'s superclass and its `uDataTypes.UReal` field type both come from the
isolated loader. `getResource` is still parent-first (inherited from `URLClassLoader`) but the
platform parent has no application resources, so nothing leaks in practice.

### 5.3 No shadowing between the two jars

```bash
unzip -l atenearesearchgroup.uncertainty.jar | grep 'org/tzi'   # (no output)
unzip -l use.jar | awk '{print $4}' | grep -oE '^[^/]+/' | sort -u
```
```
META-INF/
org/
```
The two jars' namespaces are disjoint, so URL ordering cannot silently pick a wrong copy.

### 5.4 The JPMS rationale is correct — reproduced independently

The whole design rests on the claim that a platform-parented `URLClassLoader` is *not* sufficient
under JPMS. I built a minimal module `use.core` exporting `org.tzi.use.uml.ocl.value` and probed it
both ways (`/tmp/jpmsprobe`):

```
=== MODULE PATH RUN ===
app loader          jdk.internal.loader.ClassLoaders$AppClassLoader@3feba861
RealValue via app   jdk.internal.loader.ClassLoaders$AppClassLoader@3feba861
module              module use.core
RealValue via PLATFORM loader -> RESOLVED, defined by jdk.internal.loader.ClassLoaders$AppClassLoader@3feba861  sameClass=true
=== CLASSPATH RUN ===
RealValue via PLATFORM loader -> ClassNotFoundException (platform loader clean)
```

**Confirmed.** On the module path the platform loader returns the application's class, exactly as
`IsolatedJarClassLoader.java:16-26` documents. And this reactor *is* on the module path:
`use-core/src/main/java/module-info.java` exists, `use-core/src/test/java` has no `module-info.java`,
so surefire patches tests into `use.core` — visible in the stack frames the S1 refutation captured
(`at use.core@7.5.0/org.tzi.use.uncertainty.differential.HistoricalOracle.materialise`).

**Caveat, filed as a design note rather than a defect:** two of the isolation test's assertions have
a classpath escape hatch — `platformLoaderIsNotACleanParentUnderJpms()` returns early at
`HistoricalOracleIsolationTest.java:102-106`, and negative control (2) sets `viaPlatform = null` at
`:195-197`. In classpath mode both become vacuous passes. Negative control (1) — the plain
`new URLClassLoader(urls)` — demonstrates the hazard in *both* modes, so the suite is never fully
vacuous. Worth an explicit "which mode am I in" print rather than a silent `return`.

---

## 6. The forward-compatibility trap

**Confirmed, and pre-declared.** `HistoricalOracleIsolationTest.java:150-166`:

```java
    void uTypesResolveOnlyThroughTheOracle() {
        ClassLoader app = HistoricalOracleIsolationTest.class.getClassLoader();
        for (String simple : new String[] { "URealValue", "UIntegerValue", "UBooleanValue", "UStringValue" }) {
            Class<?> historical = oracle.historicalClass(simple);
            assertSame(oracle.loader(), historical.getClassLoader(), …);
            // At S1 there is no port, so this must not resolve. When S4 lands, this assertion is
            // expected to be inverted; sameNameDistinctClasses() is the assertion that survives.
            assertThrows(ClassNotFoundException.class,
                    () -> Class.forName("org.tzi.use.uml.ocl.value." + simple, false, app), …);
```

Line 160 fails the instant S4 puts `org.tzi.use.uml.ocl.value.URealValue` on the application
classpath. This is **not a hidden landmine**: the failure message at `:162-164` says so, and
`stage-01.md` §6.3 files it as residual risk. The S1 empirical refutation already triggered it under
a simulated port. Nothing to add except the exact edit list.

### Which assertions survive S4, method by method

| Test method | Line | After S4 | Action |
|---|---|---|---|
| `loaderIsParentLast` | 62-79 | **Survives unchanged** — asserts loader type, parent, prefix policy, and that the app loader is not in the parent chain. Strictly stronger once a port exists. | none |
| `platformLoaderIsNotACleanParentUnderJpms` | 93-110 | **Survives** — uses `RealValue`, which already collides today. | none (but see §5.4 on the silent classpath `return`) |
| `sameNameDistinctClasses` | 112-132 | **Survives** — this is the assertion the author deliberately wrote against `RealValue` so it would outlive S1. | **extend** to loop over the four U-types once they exist |
| `instancesDoNotCrossTheBoundary` | 134-148 | **Survives** — `RealValue`-based. | **extend** to a U-type; a historical `URealValue` instance must not be assignable to the ported `URealValue` |
| `uTypesResolveOnlyThroughTheOracle` | 150-166 | **BREAKS at line 160.** | **invert**: replace `assertThrows(ClassNotFoundException…)` with `assertNotSame(appClass, historical)` + `assertNotSame(appClass.getClassLoader(), historical.getClassLoader())`. Line 156 (`assertSame(oracle.loader(), historical.getClassLoader())`) survives as-is. Do **not** delete. |
| `naiveLoadersWouldSelfCompare` | 173-210 | **Survives**, and gets *stronger* if `COLLIDING_CLASS` is switched to `URealValue` after S4 — the negative control then demonstrates the hazard on the exact class the port introduces. | **retarget** (optional but recommended) |
| `jarsAreTheRecordedOnes` | 212-221 | Survives. | none |
| `missingJarFailsLoudly` | 223-243 | Survives. Note it mutates the process-global `use.historical.jars.dir` and restores it in a `finally`; there is no parallel-execution configuration in this reactor (`grep -rn "parallel\|forkCount" pom.xml use-core/pom.xml` → no matches), so this is safe today and becomes a race if parallelism is ever enabled. | none; note the constraint |
| `wrongDigestFailsLoudly` | 245-276 | Survives, same system-property caveat. | none |

Net: **one method to invert, two to extend, one optional retarget.** The isolation test remains
meaningful after S4 — in fact more meaningful, because the U-types become a real collision instead
of a hypothetical one. Its structure was designed for this and it holds up.

---

## 7. What I checked and could not break

* **Isolation.** Parent-last with no fallback, verified in source and by probing three classes that
  exist on the application loader and are correctly refused (§5.2).
* **The JPMS rationale.** Independently reproduced with a minimal module (§5.4). The platform-parent
  "obvious fix" really does return application classes on the module path.
* **Jar namespace disjointness.** `use.jar` ships only `org/` + `META-INF/`; the uncertainty jar
  ships only `uDataTypes/`. No URL-order shadowing (§5.3).
* **Digest pinning.** `sha256sum` on both vendored jars matches the constants at
  `HistoricalOracle.java:71-76` exactly.
* **The `fromHistorical` accessor contract.** `javap` confirms `RealValue.value():double`,
  `IntegerValue.value():int`, `BooleanValue.value():boolean`, `StringValue.value():String`,
  `URealValue.value()/uncertainty():double`, `UIntegerValue.value():int`,
  `UBooleanValue.value():boolean`/`probability():double`, `UStringValue.value():String`/
  `confidence():double`. Every cast in `HistoricalOracle.java:564-578` is correct.
* **`UValue` structural canonicalisation** for the nine non-`OPAQUE` kinds. `-0.0` vs `0.0` do
  differ; `NaN` does equal `NaN`; `quote()` escapes tab/CR/LF/quote/backslash/control characters
  correctly, so no value in `uStringBoundaries()` can break TSV framing.
* **`UNSUPPORTED` is genuinely not an agreement** (`DiffVerdict.java:32-34`) and appears in
  `disagreements()` — verified by running a sweep of an op neither side implements (§3, H6 output).
* **The harness's external dependency surface.** Constant-pool scan of every class in the historical
  `org/tzi/use/uml/ocl/value/` package and all of `uDataTypes/` finds exactly one non-JDK,
  non-`org.tzi.use`, non-`uDataTypes` reference — `junit.framework.TestSuite` from a bundled
  `AllTests.class` that is never loaded. There is no third-party library that could leak in through
  the non-isolated delegation path.
* **No repository mutation.** `git status --short` is empty apart from this file; all scratch
  drivers and the zero-row report were written under `/tmp`.

---

## 8. Bottom line for S3/S4 planning

The instrument's *mechanism* — isolation, jar pinning, seeded corpora, exact structural
canonicalisation, fault injection — is sound and was built by someone who understood the hazards.
The instrument's *scoring* is not yet trustworthy: it has at least eight distinct ways to produce a
green row that means nothing, one of which (H2) lands squarely on the largest block of work in the
S2 specification.

S4 should not begin until the blocking set (H1, H2, H3, H6, H7, H8, H9) is fixed and the fixes are
themselves fault-injected — specifically, a regression test that asserts a marshalling failure on
both sides produces a **non-agreement** verdict, and one that asserts a zero-row sweep **fails**.
Roughly forty lines of change and two new tests.
