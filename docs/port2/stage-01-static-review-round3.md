# S1 static review, round 3 — commit `e8b73e48`

**Reviewer role:** static refuter. No Maven was run (another agent owns it). Every claim below is
backed by a `file:line` citation, a `git`/`javap` invocation with its real output, or a
reflection-only probe run with `java Enum.java` against the vendored jars.

**Commit under review:** `e8b73e48` — *"S3 fix: a differential oracle may claim agreement only where
it saw two values"*, on `port-uncertainty-2`.

**Verdict: DEFECTIVE.** The headline claim is *not* established as committed. Two MAJOR defects are
of exactly the class the commit says it closed — an agreement verdict reachable without two observed
values, and a note that destroys the evidence it exists to carry — plus a MAJOR reviewability defect
that makes the diff of the new adjudication mechanism unreadable by `git diff`. Four of the seven
review questions come back clean.

---

## 0. What is confirmed fixed

These I checked and could not break.

| Claim | Status | Evidence |
|---|---|---|
| Scope discipline (ground rule 2) | **CONFIRMED** | `git diff --name-status 30d480db..HEAD -- '*/src/main/*'` → empty. `git show --name-only --format= e8b73e48` touches only `use-core/src/test/java/org/tzi/use/uncertainty/differential/*` and the two goldens. No pom, no `module-info.java`, no `use-gui`, no `use-assembly`, no pre-existing upstream test. |
| Jupiter only (Q6) | **CONFIRMED** | `grep -n 'import org.junit\|import junit\|extends TestCase' *.java` returns only `org.junit.jupiter.api.*` across all four test classes. No `junit.framework`, no `org.junit.Test`. |
| `AGREE_THROWN`/`DIFFER_THROWN` really are gone from the vocabulary | **CONFIRMED** | `DiffVerdict.java:48-100` declares `AGREE, ACCEPTED_THROW, DIFFER, BOTH_THREW, MIXED, UNSUPPORTED, HARNESS_ERROR`. Remaining textual hits are Javadoc narrative only. |
| D-4 is genuinely pinned this time | **CONFIRMED** | `supportsSwallowsOnlyAMissingMethod` (`DifferentialHarnessRegressionTest.java:207-238`) calls `oracle.supports(add)` **while open** at line 216, which caches the `Method` in `HistoricalOracle.methods`, then asserts a throw at line 224 after `close()`. On the pre-fix code (`resolve()` without `checkOpen()`) the cached lookup returns the method and `supports()` answers `true` — nothing thrown, `assertThrows` fails. The test cannot pass on the old code. |
| Golden delta is exactly the two header lines | **CONFIRMED** | `git diff e8b73e48^ e8b73e48 -- docs/port2/differential/` shows `+# rows.agreement` / `+# rows.disagreement` in each file and nothing else. |
| The 285-operation inventory | **INDEPENDENTLY REPRODUCED** | I re-derived it without the harness (URLClassLoader over the two jars, same predicate as `UnwrittenPortInvariantTest.reachableOperations`): `total reachable operations = 285`. |
| The pre-fix `AGREE_THROWN` mechanism | **CORROBORATED IN BYTECODE** | `javap -c -p ... org.tzi.use.uml.ocl.value.UIntegerValue` shows `new class java/lang/RuntimeException` … `ldc // String UInteger.power() : expected Real or Integer exponent value`. The historical code really does raise bare `java.lang.RuntimeException`, so a subject throwing `new RuntimeException("TODO: …")` matched it on class name. The *mechanism* of the BEFORE measurement is real; I did not and cannot verify the count 21816 without Maven. |

`disagreements()`/`agreements()` are one predicate over two partitions
(`DifferentialSweep.java:334-355`), `count()`/`tally()` iterate `DiffVerdict.values()`
(`DifferentialSweep.java:284-291`, `DiffReportWriter.java:124`), and the smoke-test assertions
(`UncertaintyDifferentialSmokeTest.java:72-74,110`) reference only `AGREE`/`DIFFER`/`disagreements()`.
**Q2 is clean:** I found no consumer for which `BOTH_THREW` is anything other than a non-agreement.

---

## D-10 (MAJOR) — agreement is still reachable with **zero** observed values, via `VOID`

This is Q1, and it fails.

**The path.** `HistoricalOracle.invoke`, `HistoricalOracle.java:527-534`:

```java
if (method.getReturnType() == void.class) {
    // Method.invoke returns null for void, and fromHistorical maps null to Kind.NULL. Without
    // this branch every void operation compares equal to every other void operation forever,
    // so an empty-bodied ported mutator would agree on every row.
    return UValue.voidValue();
}
```

`UValue.canonical()` for `Kind.VOID` is the constant string `"VOID"` (`UValue.java:260-261`), and
`classify` scores agreement on canonical-string equality (`DifferentialSweep.java:222-224`):

```java
boolean agree = ref.value.canonical().equals(sub.value.canonical());
```

The `Candidate` contract then *instructs* the other side to produce the same constant
(`Candidate.java:43-44`):

> Must never return Java `null`: use `UValue.nullValue()` for a genuine null result and
> `UValue.voidValue()` for a `void` operation.

So for every `void` operation, a subject adapter that follows the documented contract yields
`AGREE` **unconditionally** — the verdict is decided by `method.getReturnType() == void.class` on the
reference side and by the documented boilerplate on the subject side. Neither implementation's
behaviour is observed. This is `AGREE_THROWN` in different clothing: a verdict that is green by
construction rather than by measurement.

**Reachability, measured.** Eight such operations are inside the harness's reach — reflection probe
over the two jars, same predicate as `UnwrittenPortInvariantTest.reachableOperations`:

```
total reachable operations = 285
VOID-returning reachable operations = 8
   BooleanValue.setTypeToRuntimeType()   [declared on Value]
   IntegerValue.setTypeToRuntimeType()   [declared on Value]
   RealValue.setTypeToRuntimeType()      [declared on Value]
   StringValue.setTypeToRuntimeType()    [declared on Value]
   UBooleanValue.setTypeToRuntimeType()  [declared on Value]
   UIntegerValue.setTypeToRuntimeType()  [declared on Value]
   URealValue.setTypeToRuntimeType()     [declared on Value]
   UStringValue.setTypeToRuntimeType()   [declared on Value]
```

`javap -cp use.jar:atenearesearchgroup.uncertainty.jar org.tzi.use.uml.ocl.value.Value` confirms
`public void setTypeToRuntimeType();`. The porter's own test already asserts the operation is driven:
`DifferentialHarnessRegressionTest.java:427-429` does `assertTrue(oracle.supports(mutator))` and
invokes it. All 8 are in the 285 the invariant sweeps.

**The code comment about it is false.** Both `HistoricalOracle.java:529-531` and
`UValue.java:62-66` claim this branch prevents *"an empty-bodied ported mutator [agreeing] with the
historical one on every row, forever"*. It does not. Before the branch, both sides rendered `NULL`
and agreed unconditionally; after it, both sides render `VOID` and agree unconditionally. The
constant separates `VOID` from a genuine `null` **result** — a real and useful distinction — but it
buys nothing at all against the defect its own comment names. That is a D-3-class false statement,
sitting in the documentation of the fix.

**Why the invariant does not catch it.** `UnwrittenPortInvariantTest.UnwrittenPort.invoke`
(line 291-293) throws for *every* operation, so the 8 void ops land in `MIXED`. The invariant is
parameterised over exactly one shape of unwritten port. The other equally natural shape — a port
whose mutators are empty bodies returning `UValue.voidValue()`, which is literally what the
`Candidate` Javadoc tells the S4 author to write — is not covered and scores free green. The class
comment claims the test "closes the whole family"; it closes the throwing member of the family.

**Not currently green anywhere:** the two goldens contain only `URealValue.add`/`minus`, and the
invariant sweep's subject throws. This is a latent route that opens the moment S4 lands a real
adapter, not a false number in the tree today. Hence MAJOR, not CRITICAL.

*(Adjacent, weaker: `Kind.NULL` agrees the same way — `HistoricalOracle.fromHistorical(null)` →
`UValue.nullValue()`, `UValue.java:258-259` renders `"NULL"`. That one is defensible, because a Java
`null` return really is an observed outcome on both sides. `VOID` is not: there is no outcome.)*

---

## D-11 (MAJOR) — the `HARNESS_ERROR` note destroys one side's evidence

`DifferentialSweep.classify`, `DifferentialSweep.java:186-199`:

```java
Throwable first = ref.harnessError != null ? ref.harnessError : sub.harnessError;
StringBuilder note = new StringBuilder("no measurement on ");
if (ref.harnessError != null && sub.harnessError != null) {
    note.append("either side");
} else { ... }
note.append("; no comparison was made. ").append(safeMessage(first));
```

When **both** sides fail, the note says "either side" and then quotes exactly **one** message — the
reference's — with no attribution. The subject's failure reason is written nowhere. It is not
recoverable from the columns either: `column()` (`DifferentialSweep.java:228-236`) renders
`HARNESS_ERROR:<class>`, and both sides carry the *same* class,
`org.tzi.use.uncertainty.differential.HarnessMarshallingException`, on every such row.

This is visible in the porter's own pasted evidence. The D-1 reproduction row (reference = oracle,
subject = `StubCandidate`, both failing for **different** reasons):

```
0  URealValue.add(value)  UINTEGER(0,0.0) | UINTEGER(0,0.0)
   HARNESS_ERROR:…HarnessMarshallingException  HARNESS_ERROR:…HarnessMarshallingException
   HARNESS_ERROR  no measurement on either side; no comparison was made. URealValue.add(value)
   expects a receiver of org.tzi.use.uml.ocl.value.URealValue but the supplied UINTEGER(0,0.0)
   maps to org.tzi.use.uml.ocl.value.UIntegerValue
```

That is the **oracle's** message. The stub failed with `"URealValue.add(value) needs a UREAL
receiver, got UINTEGER(0,0.0)"` (`StubCandidate.java:97`), which appears nowhere in the row. A reader
naturally reads the single quoted sentence as describing "either side".

The sharp point: the sibling branch of the *same method*, ten lines below
(`DifferentialSweep.java:200-213`), was rewritten in this very commit specifically to carry **both**
classes and **both** messages, with the rationale spelled out in `DiffVerdict.java:70-72`:

> The harness holds the evidence that two throws are unrelated; writing nothing is how that evidence
> used to be destroyed.

The identical argument applies verbatim to two harness errors, and it was not applied. Fix by
building the note the same way `BOTH_THREW` does — `reference: <msg> / subject: <msg>` — whenever
both sides are non-null.

---

## D-12 (MAJOR) — `AcceptedThrowPairs.java` is committed as a **binary** file; its diff is unreviewable

```
$ git show --stat e8b73e48
 .../differential/AcceptedThrowPairs.java           | Bin 0 -> 6884 bytes

$ git diff e8b73e48^ e8b73e48 -- .../AcceptedThrowPairs.java
Binary files /dev/null and b/use-core/.../AcceptedThrowPairs.java differ

$ file AcceptedThrowPairs.java
AcceptedThrowPairs.java: data
```

Cause: the file contains **raw NUL bytes (0x00) inside character literals** rather than the escape
`'\0'`. Byte offsets 2992, 3967, 3990, 4031, 4052, 6263. Rendered with NULs made visible:

```
line  68:  out.add(e.getKey().replace('<NUL>', '|') + " -> " + e.getValue());
line  92:  return operationKey + '<NUL>' + referenceClass + '<NUL>' + referenceMessage
line  93:          + '<NUL>' + subjectClass + '<NUL>' + subjectMessage;
line 133:          + "different rationales: " + key.replace('<NUL>', '|'));
```

A bare NUL is a legal `SingleCharacter` per JLS §3.10.4, so this compiles and behaves correctly —
this is not a functional bug. It is a **process** bug, and a pointed one:

- `git diff`, `git show -p`, `git log -p` and every code-review UI that consumes them render this
  file as `Binary files … differ`. There is no reviewable diff of it, now or on any future change.
- `git grep` reports `Binary file … matches` instead of the matching line.
- The class it hides is `AcceptedThrowPairs` — **the only remaining route by which a run can score
  green without two observed values** (`DiffVerdict.ACCEPTED_THROW.isAgreement() == true`,
  `DiffVerdict.java:109-111`). The porter's own hand-off says its safeguard "is social … a reviewed,
  written rationale per exact pair". A safeguard whose enforcement is human review has been committed
  in the one encoding that defeats human review.

Fix: replace the six raw NULs with `' '` or, better, `''`/`""` as a visible unit
separator. One-character-per-site change; no behaviour change (the separator is internal to the map
key and is stripped by `replace(sep,'|')` before it reaches any message).

---

## D-13 (MINOR) — `StubCandidate` still has a fourth exit that breaks the `Candidate` invariant

Q4. The invariant is now correctly stated on the interface (`Candidate.java:14-31`) and three of
`StubCandidate`'s exits were converted (`:93`, `:97`, `:132`). The fourth was not —
`StubCandidate.java:116-118`:

```java
default:
    throw new UnsupportedOperationException("stub does not implement " + op.key());
```

"I do not implement this operation" is an adapter statement, and the interface says adapter failures
must be `HarnessMarshallingException`. The class comment added in this commit
(`StubCandidate.java:83-89`) says "The **three** failure exits below raise
`HarnessMarshallingException`" — there are four.

Currently unreachable through a sweep: `run()` consults `supports()` first
(`DifferentialSweep.java:109-131`), and `SUPPORTED` (`StubCandidate.java:42-48`) is exactly the set
the `switch` handles. The hazard is that the two lists are kept in sync by hand; adding a key to
`SUPPORTED` without a `case` opens it. And since `SUPPORTED` is `static`, *both* shipped stub
instances would take the same exit, which is precisely the D-2 shape. Post-fix that shape lands in
`BOTH_THREW`, not agreement, so this is MINOR rather than a returning defect — but the invariant is
stated as universal and one shipped implementation still does not obey it.

---

## D-14 (MINOR) — the report never records *which* allowlist was in force

`ACCEPTED_THROW` is the deliberate exception to "agreement requires two values", and the design rests
on the sign-off being auditable. But:

- `AcceptedThrowPairs.describe()` (line 65-71), whose Javadoc says it is *"for a report header or a
  stage document"*, is **called from nowhere**: `grep -rn 'describe()' *.java` → no hits outside its
  own declaration.
- `DifferentialSweep.acceptedThrowPairs()` (`DifferentialSweep.java:53-55`) is likewise never read.
- `DiffReportWriter.writeAll` (`DiffReportWriter.java:94-162`) takes only `results` and
  `jarDigests`; it has no access to the allowlist and writes no `# accepted.*` header.

Consequence: a sweep run **with** a non-empty allowlist that happens to adjudicate zero rows is
byte-indistinguishable in the report from a sweep run with `none()`. And `# verdict.ACCEPTED_THROW`
only appears when the count is non-zero (`DiffReportWriter.java:147-149`), so the header the porter
tells future auditors to look for is absent in exactly the case where the allowlist was present but
inert. The per-row rationale in the note is good and does cover adjudicated rows; the *provenance of
the run* is not recorded.

---

## D-15 (MINOR) — "byte for byte" is still false (D-5, unfixed, confirmed)

`DiffReportWriter.assertMatchesGolden`'s Javadoc (`DiffReportWriter.java:196`) says *"Compares a
freshly written report, **byte for byte**, against the committed golden"*. The implementation
(`:213-249`) uses `readLines` → `Files.readAllLines` (`:251-257`). That comparison is blind to line
terminators (`\n` vs `\r\n` vs `\r`) and to a missing final newline — `readAllLines` on `"a\nb\n"`
and on `"a\nb"` both yield `[a, b]`. Two files that differ in bytes compare equal.

The porter flags this himself and correctly notes his determinism evidence rests on `sha256sum`, not
on this assertion. It stays filed because it is the same species as D-3: a class whose product is
evidence, stating in writing a guarantee it does not provide. Both goldens are currently LF with a
trailing newline (`tail -c 1` → `0a`; `file` → `ASCII text`), so nothing is wrong in the tree today.

Also dead: `List<String> actual = readLines(written);` at `:215` is computed and then unused on the
refresh branch (`:216-224`).

---

## D-16 (MINOR) — the `MIXED` note does not say which side threw

`DifferentialSweep.java:215-221` writes `"one side threw: " + safeMessage(...)` without naming the
side. Recoverable from the columns (one holds `THROWN:`, the other a canonical form), so much weaker
than D-11 — but `MIXED` is 52 196 rows of the invariant sweep and the note could say `reference` /
`subject` at no cost.

---

## Q3 — do the new tests pin the property, or would they pass on the old code?

Reasoned through per test. Note that most of these do not *compile* against the pre-fix tree
(`BOTH_THREW`, `ACCEPTED_THROW`, `agreementCount()` are all new — `agreementCount()` is absent from
`git show e8b73e48^:…/DifferentialSweep.java`), so the meaningful question is whether each asserts
the *behavioural* difference, not just the new vocabulary.

| Test | Pins the property? |
|---|---|
| `supportsSwallowsOnlyAMissingMethod` (D-4) | **YES.** Cache-then-close ordering makes the pre-fix path answer `true` silently. See §0. The porter's two-variant demonstration is consistent with the code. |
| `twoStubsOverAnUnmarshallableReceiverAreNotAgreement` (D-2) | **YES.** Pre-fix `StubCandidate` threw `IllegalArgumentException` on both sides → `AGREE_THROWN=169`; the test asserts `count(HARNESS_ERROR)==169`, which was 0. |
| `twoNullReturnsAreNotAgreement` (D-1) | **YES.** Pre-fix `Objects.requireNonNull` inside the `try` produced `NullPointerException` on both sides → `AGREE_THROWN`; asserts `count(HARNESS_ERROR)==4`, was 0. |
| `twoThrowsAreNeverAgreementAndNeverLoseTheirMessages` | **YES.** Pre-fix the same-class branch set `note = ""` (`git show e8b73e48^` line `String note = same ? "" : …`); the test asserts `assertNotEquals("", row.note())` and that both messages appear. |
| `unsupportedNoteIsNotAFalseStatement` (D-3) | **YES, and well built.** Lines 171-173 assert by reflection that the historical `SBooleanValue` really declares `and(Value)` *before* asserting the note's wording, so the note stays correct only while the underlying fact holds. Good practice. |
| `acceptedThrowPairsAreOptInAndExact` | **YES** for the new mechanism (message-exact, operation-scoped, blank rationale rejected). |
| `anUnwrittenPortAgreesWithNothing` | **YES**, on mechanism. Corroborated in bytecode: the historical `UIntegerValue.power` really constructs `java.lang.RuntimeException("UInteger.power() : expected Real or Integer exponent value")`, so a subject throwing bare `RuntimeException` matched it on class name pre-fix. I cannot verify the count 21816 without Maven — **UNVERIFIED, not disputed**. **But its coverage is narrower than its class comment claims** — see D-10. |
| `agreementIsOnlyEverAnObservedValue` | **PARTIALLY.** The `values()` loop (`:158-163`) does pin the vocabulary: re-adding a green throw-verdict fails it. The partition half (`:169-183`) exercises a population with **zero** agreement rows, so `assertEquals(mixed.rowCount(), agreements().size() + disagreements().size())` would also pass against a `partition(true)` that always returned empty. Weak in the direction it was written to check. The all-agreement side is covered by `UncertaintyDifferentialSmokeTest:72-74`. |

**No repeat of D-4.** The tests are materially stronger than last round.

One coverage note: the D-4 rewrite *deleted* the previously-committed assertions about
`HistoricalOracleUnavailableException` (`git show e8b73e48 | grep '^-'` — the
`isAssignableFrom(HistoricalOracleUnavailableException)` pair is gone). Both old and new are pure
type-hierarchy tautologies, so no behavioural coverage was lost; and the Javadoc now honestly admits
the narrowed catch is otherwise unobservable because the constructor pre-caches every reachable class
(`HistoricalOracle.java:453-459`). Noted, not filed.

---

## Q5 — every string literal that reaches a report column or note

| Producer | Verdict |
|---|---|
| `HistoricalOracle.unsupportedReason` branch 1 (`:485-489`) | **TRUE.** Guarded by the same `MARSHALLABLE_RECEIVERS` test as `supports()` (`:465`). D-3 is genuinely fixed. |
| `HistoricalOracle.unsupportedReason` branch 2 (`:490-491`) | **TRUE.** Only reachable when `resolve()` raised `NoSuchHistoricalMethodException`, i.e. `Class.getMethod` found nothing — and `getMethod` searches inherited public methods too, so "declares no method matching …" is if anything conservative. All 8 marshallable receivers resolve through `VALUE_PKG` (`:414-424`), so the package prefix in the message is correct. |
| `Candidate.unsupportedReason` default (`:77-79`) | **TRUE** and appropriately non-committal. |
| `StubCandidate.unsupportedReason` (`:79-81`) | **TRUE.** Backed by a `TreeSet`, so the rendering is stable across runs — a real determinism fix. |
| `DiffRow.thrown` / `harnessError` (`:74-85`) | **TRUE**, and textually distinguishable as claimed. |
| `BOTH_THREW` / `ACCEPTED_THROW` notes (`:205-213`) | **TRUE** and complete. |
| **`HARNESS_ERROR` note (`:190-196`)** | **INCOMPLETE — see D-11.** Says "either side" while quoting one side's message. |
| **`Kind.VOID` Javadoc (`UValue.java:62-66`) and `HistoricalOracle.java:529-531`** | **FALSE — see D-10.** Claims the constant stops an empty-bodied mutator agreeing on every row. It does not. |
| **`assertMatchesGolden` Javadoc (`DiffReportWriter.java:196`)** | **FALSE — see D-15.** "byte for byte" over `Files.readAllLines`. |
| `StubCandidate` class comment (`:83-89`) | **FALSE in detail — see D-13.** "The three failure exits below"; there are four. |

---

## Recommendations, in order

1. **D-10** — decide what a `void` operation means to a differential oracle. Either give `VOID` its
   own non-agreement verdict (`NOT_OBSERVABLE`), or exclude void-returning methods from
   `reachableOperations` and record the exclusion. Then correct the two comments that claim the
   `VOID` constant already solved this. Whichever route, extend `UnwrittenPortInvariantTest` with a
   second subject — a port whose methods return `UValue.voidValue()`/`UValue.nullValue()` instead of
   throwing — so the invariant covers the family it claims to.
2. **D-11** — build the `HARNESS_ERROR` note from both sides, exactly as `BOTH_THREW` does.
3. **D-12** — de-NUL `AcceptedThrowPairs.java` so its diff is reviewable. Consider a
   `.gitattributes` entry (`*.java text diff=java`) so this cannot recur silently.
4. **D-14** — have `DiffReportWriter` emit `# accepted.pairs <n>` plus `describe()` lines, and have
   the sweep hand it the allowlist.
5. **D-15** — either compare bytes (`Files.readAllBytes` / `Arrays.equals`, or `mismatch`) or amend
   the Javadoc to say "line by line".
6. **D-13**, **D-16** — one-line corrections.

Items 1 and 2 are behaviour changes; 3–6 are a mix. Ground rule 3 keeps behaviour and documentation
in separate commits.

---

## Standing caveats I did not clear

- **No Maven was run.** Every count the porter reported (471471 rows, 21816 → 0 agreements, 285
  operations, 169-row tallies, surefire/failsafe totals) is unverified by me except where noted
  above. I independently reproduced only the *inventory size* (285) and the *bytecode mechanism*
  behind the BEFORE figure.
- The porter's inability to reproduce the earlier verifier's `rows 43136 / AGREE_THROWN 15081` stands
  as he described it: `git ls-files | xargs grep -l '43136\|15081'` finds nothing, so that sweep's
  selection is unrecoverable from the tree. His substitute is a larger, in-tree, replayable
  measurement — not the same number, and he does not claim it is. Reasonable.
- Coverage limits he already logged remain: `MARSHALLABLE_RECEIVERS` is 8 receivers, `SBooleanValue`
  and collection receivers are out of reach, and `EXTRA_PARAM_SLICE = 3` / `RANDOM_DRAWS = 2` make
  the invariant a strong sample rather than exhaustive.
- Documentation under `docs/port2/` (`stage-01.md`, `audit-*.md`, the two post-fix reports) still
  discusses `AGREE_THROWN`/`DIFFER_THROWN` as live verdicts. Confirmed by `git grep -ln`. A
  documentation-only forward pointer is still owed.
