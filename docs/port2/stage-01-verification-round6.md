# S1 round-6 empirical verification — DEFECTIVE (one new MAJOR, D-37)

**Role.** Refuter. I did not write the round-6 fixes. I own Maven for this round.
**Tree.** `port-uncertainty-2`, behaviour `d13d4858`, documentation `854abb83`.
**Every number below is pasted from a run I made. Where I could not verify a claim I say so.**

---

## 0. Verdict

| | |
|---|---|
| **Verdict** | **DEFECTIVE** — one new MAJOR (**D-37**), two new MINORs. |
| Control | **INTACT.** A perfect port still yields 0 `DIFFER`, 0 `MIXED`, 0 diverging operations over 285 operations / 19 083 rows, and the same 74 stage passes. The D-18 fix is **not** over-strict in the sense the brief defined. |
| False green | **None found.** Five constructions tried, including two that are new this round. The scoring rules hold. |
| D-34 | **CLOSED**, independently confirmed. |
| D-35 | **CLOSED**, and the experiment the brief demanded **reproduces**: the restored assertion FAILS on the pre-round-6 tree with the exact message the porter quoted. |
| D-36 | **CLOSED for the acceptance test**, as scoped. |
| D-18 | **The blind spot is really closed** (I reproduced the *before* state independently: 0 `DIFFER`). **But the number the round publishes as its detection power is not attributable to a property of the port.** That is D-37. |
| Acceptance | `mvn -q clean && mvn -B verify -Djava.awt.headless=true` → **BUILD SUCCESS**, 77 surefire + 130 failsafe = **207** methods, 0 failures. |

**Why DEFECTIVE and not SOUND_WITH_DOCUMENTED_LIMITS.** The brief's rule for this round was
explicit: *"Any false `DIFFER` is a defect."* I produced 3 445 of them from a **content-perfect**
port, and the resulting measurement is **byte-identical** to the one the round quotes as detection
power (§4). The scoring code is not wrong; what is wrong is that the round's headline figure
("3 445 `DIFFER` rows, 182 of 285 operations, 74 → 45 stage passes") is equally the signature of a
faithful port whose adapter follows the documented worked example. The number does not mean what the
record says it means, and the obligation that would make it mean that is absent from
`Candidate`'s Javadoc and from `harness-contract.md` §7. The fix is test-scoped, cheap, and named in
§4.4.

---

## 1. Acceptance run

```
$ mvn -q clean && mvn -B verify -Djava.awt.headless=true
```

```
[INFO] Tests run: 76, Failures: 0, Errors: 0, Skipped: 0          <- use-core surefire
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0           <- use-core failsafe (OCLExpressionIT)
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0           <- use-gui surefire (MavenLayeredArchitectureTest)
[INFO] Tests run: 129, Failures: 0, Errors: 0, Skipped: 0         <- use-gui failsafe (ShellIT)
[INFO] BUILD SUCCESS
[INFO] Total time:  01:26 min
EXIT=0
```

Per-class, use-core surefire:

```
Tests run: 11 -- in org.tzi.use.architecture.MavenCyclicDependenciesCoreTest
Tests run:  6 -- in Detection power: subtle infidelities in a ported U-type
Tests run:  6 -- in Uncertainty differential smoke
Tests run: 10 -- in Unwritten-port invariant
Tests run:  9 -- in HistoricalOracle class-loader isolation
Tests run: 33 -- in Differential harness regressions
Tests run:  1 -- in org.tzi.use.uml.mm.ModelAPITest
```

**Totals: 77 surefire + 130 failsafe = 207 methods, 0 failures.** I ran the whole thing twice
(once truncated, once with the full log kept); both were `BUILD SUCCESS` with the same counts.

### 1.1 Delta accounting — the brief's baseline is stale, the porter's is right

I re-measured the baseline myself, in a detached worktree at `90404528`, after deleting my own
scratch file:

```
$ cd <worktree at 90404528> && mvn -q clean && mvn -B -o -pl use-core test
[INFO] Tests run: 30, ... -- in Differential harness regressions
[INFO] Tests run: 71, Failures: 0, Errors: 0, Skipped: 0
```

| | at `90404528` | at `854abb83` | delta |
|---|---|---|---|
| use-core surefire | **71** | **76** | **+5** |
| use-gui surefire | 1 | 1 | 0 |
| use-core failsafe | 1 | 1 | 0 |
| use-gui failsafe | 129 | 129 | 0 |
| **total** | **202** | **207** | **+5** |

The +5 are accounted for exactly:

```
$ git diff 90404528..d13d4858 -- '*.java' | grep -E "^\+.*(@Test|void [a-z])"
+    @Test
+    void aReportCannotUnderstateItsOwnSignOffs()          <- D-34
+    @Test
+    void rightContentInTheWrongJavaTypeIsADifference()    <- D-18 unit
+    @Test
+    void theTypeTokenIsPackageInsensitiveOnPurpose()      <- D-18 cost
+    @Test
+    void aWrongJavaTypeWithRightContentIsADivergence()    <- D-18 detection
+    @Test
+    void noOperationAnswersWithTwoRuntimeClasses()        <- D-18 premise
```

Regressions 30 → 33 (+3), detection power 4 → 6 (+2). **Nothing pre-existing was removed or
skipped.** The brief's "68 surefire" is stale by four methods (it predates `f438a365`); the
porter recorded this in `stage-01-round6-fixes.md` §5 rather than adjusting silently, and his
figure of 202 is the one I measured.

### 1.2 Hygiene

```
$ git diff --name-status 30d480db..HEAD -- '*/src/main/*'
SRC_MAIN_EMPTY                      <- the marker echoed after an empty result
$ git status --short
TREE_CLEAN                          <- likewise; nothing modified, nothing untracked
```

No pom, no `module-info.java`, no `use-gui`, no `use-assembly`, no pre-existing upstream test
appears in `git diff --name-status 30d480db..HEAD`. Behaviour (`d13d4858`) and documentation
(`854abb83`) are separate commits, as required.

### 1.3 Determinism — byte-identical, measured

```
$ rm -f use-core/target/differential/*.tsv
$ mvn -B -o -pl use-core test -Dtest=UncertaintyDifferentialSmokeTest   # run 1, saved
$ rm -f use-core/target/differential/*.tsv
$ mvn -B -o -pl use-core test -Dtest=UncertaintyDifferentialSmokeTest   # run 2, saved
$ diff -r run1 run2
IDENTICAL
$ sha256sum run1/*.tsv run2/*.tsv
86e6a4e2403f85a235695a35613c2e3f633220943ef0743dd004d4b7a71dea50  run1/s1-smoke-ureal-add.tsv
cd6143ff85a6083041abedfedc8c64c51e63fa4a03fa77d9f5e018967d143755  run1/s1-smoke-ureal-minus-faulty.tsv
86e6a4e2403f85a235695a35613c2e3f633220943ef0743dd004d4b7a71dea50  run2/s1-smoke-ureal-add.tsv
cd6143ff85a6083041abedfedc8c64c51e63fa4a03fa77d9f5e018967d143755  run2/s1-smoke-ureal-minus-faulty.tsv
$ cmp run1/s1-smoke-ureal-add.tsv docs/port2/differential/s1-smoke-ureal-add.tsv
s1-smoke-ureal-add.tsv byte-identical to golden
$ cmp run1/s1-smoke-ureal-minus-faulty.tsv docs/port2/differential/s1-smoke-ureal-minus-faulty.tsv
s1-smoke-ureal-minus-faulty.tsv byte-identical to golden
```

### 1.4 The golden refresh, audited claim by claim

Every claim the porter made about the two refreshed goldens is true. Measured against
`git show 90404528:docs/port2/differential/<f>`:

| claim | `s1-smoke-ureal-add.tsv` | `s1-smoke-ureal-minus-faulty.tsv` |
|---|---|---|
| `#` header block byte-identical | **YES** | **YES** |
| line count unchanged | 805 → 805 | 806 → 806 |
| data rows changed | **784** | **784** |
| content with the `@…` token stripped identical | **YES** | **YES** |
| verdict-column multiset identical | **YES** | **YES** |
| `@URealValue` occurrences | 3 136 | 3 136 (**6 272** total, as claimed) |

The headers still read `distinctReferenceValues 258` and `389`, and
`# accepted.degenerateOperations 0` in both. So the refresh is exactly the appended type token and
nothing else.

---

## 2. Detection power re-measured end to end — the control comes first

Pasted from the acceptance run. **The control is intact**, and it is identical to the round-5
figures.

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
```

### 2.1 The full table, P0–P11 plus the new wrong-type probe

| probe | defect | measured | agreed | tally | detected ops | stage passes |
|---|---|---|---|---|---|---|
| **control / P0** | identity | 17 199 | 17 199 | `{AGREE=17199, BOTH_THREW=910, HARNESS_ERROR=883, UNMEASURABLE=91}` | **0** | **74** |
| P1 | 0-based string index | 17 160 | 17 108 | `AGREE=17108, BOTH_THREW=863, DIFFER=52, HARNESS_ERROR=883, MIXED=86, UNMEASURABLE=91` | 3 of 3 | 74 |
| P2 | uncertainties added linearly | 17 199 | 16 731 | `AGREE=16731, …, DIFFER=468, …` | 4 of 4 | 70 |
| P3 | `Math.hypot` for `sqrt(a²+b²)` | 17 199 | 17 175 | `…, DIFFER=24, …` | 4 of 4 | 70 |
| P4 | `<=` for `<` | 17 199 | 16 919 | `…, DIFFER=280, …` | 6 of 6 | 70 |
| P5 | rounded to 10 dp | 17 199 | 16 771 | `…, DIFFER=428, …` | 7 of 7 | 67 |
| P6 | `uEquals` ignores uncertainty | 17 199 | 16 080 | `…, DIFFER=1119, …` | 4 of 4 | 72 |
| P7 | `UndefinedValue` where historical throws | 17 199 | 17 094 | `…, DIFFER=105, …, MIXED=62, …` | 6 of 6 | 71 |
| **P8** | P2 + `HarnessMarshallingException` cover | 16 731 | 16 731 | `AGREE=16731, BOTH_THREW=910, HARNESS_ERROR=1351, UNMEASURABLE=91` | **0** | 70 |
| **P9** | P2 + `supports()` lies | 15 597 | 15 597 | `AGREE=15597, …, UNSUPPORTED=1602` | **0** | 70 |
| **P10** | P2 confined to receiver 42.0 | 17 199 | 17 199 | `{AGREE=17199, BOTH_THREW=910, HARNESS_ERROR=883, UNMEASURABLE=91}` | **0** | **74** |
| P11 | −0.0 normalised to 0.0 | 17 199 | 17 140 | `…, DIFFER=59, …` | 3 of 4 | 71 |
| **P12 (new)** | right content, wrong Java class | 17 199 | 13 754 | `AGREE=13754, BOTH_THREW=910, DIFFER=3445, HARNESS_ERROR=883, UNMEASURABLE=91` | **182 of 285** | **45** |

Corpus sensitivity is unchanged from round 5 — no probe's full-vs-finite counts moved:

```
probe                          detecting rows   ops detected
                               full  finite     full  finite
  P0-perfect                       0      0         0      0
  P1-off-by-one-index            138    128         3      3
  P2-linear-uncertainty          468    456         4      4
  P3-hypot-uncertainty            24     20         4      4
  P4-le-for-lt                   280    274         6      6
  P5-round-10dp                  428    374         7      7
  P6-equals-ignores-uncertainty  1119    828         4      4
  P7-undefined-on-zero-divisor   167    146         6      6
  P8-hides-behind-harness-error     0      0         0      0
  P9-hides-behind-unsupported      0      0         0      0
  P10-narrow-input-window          0      0         0      0
  P11-negative-zero-collapse      59     55         3      3
```

The blind-spot set is unchanged and asserted as an exact set:
`{P11-negative-zero-collapse / URealValue.round() [STAGE PASS]}`. P8/P9/P10 remain at 0 detection
and remain recorded as such. Nothing about the D-18 fix perturbed any of this — confirmed by the
run, not by argument.

### 2.2 The *before* state reproduced independently

I did not take the porter's word for the before/after. I wrote my own scratch subject in a detached
worktree at `90404528` and swept the same 285-operation inventory:

```
=== SCRATCH BASELINE (90404528): D-18 before the fix ==================
  identity (control)           rows 19083, measured 17199, agreed 17199, DIFFER 0, stage passes 74  {AGREE=17199, BOTH_THREW=910, HARNESS_ERROR=883, UNMEASURABLE=91}
  boxed into Value class       rows 19083, measured 17199, agreed 17199, DIFFER 0, stage passes 74  {AGREE=17199, BOTH_THREW=910, HARNESS_ERROR=883, UNMEASURABLE=91}
  re-typed by the factories    rows 19083, measured 17199, agreed 17199, DIFFER 0, stage passes 74  {AGREE=17199, BOTH_THREW=910, HARNESS_ERROR=883, UNMEASURABLE=91}
======================================================================
```

**Three byte-identical tallies.** D-18 was real, it was exactly as large as the register said, and
the fix closes it. I confirm that without reservation.

---

## 3. D-34, D-35, D-36 — each verified independently

### 3.1 D-34 — closed

Only two public writers survive, and both demand the sign-off set:

```
$ grep -n "public static .*write" DiffReportWriter.java
99:    public static Path write(String fileName, DifferentialSweep.Result result,
153:    public static Path writeAll(String fileName, List<DifferentialSweep.Result> results,
```

`git diff 90404528 d13d4858 -- …/DiffReportWriter.java` shows the two eliding overloads deleted, not
deprecated. The reflective guard in `aReportCannotUnderstateItsOwnSignOffs` iterates
`DiffReportWriter.class.getMethods()` and fails on any `write`/`writeAll` whose parameter list omits
`AcceptedDegenerateOperations`, so the overload cannot come back quietly. Live proof from the
acceptance run that the header now moves with the sign-off:

```
=== D-34: the same sweep, two sign-off sets =======================
  signed:  # accepted.degenerateOperations	1
  signed:  # accepted.degenerateOperation	URealValue.add(value)|UREAL(2.0,0.0)@URealValue -> reviewed: a one-point domain, kept as a reachability check only; nothing here is evidence about the addition rule
  none:    # accepted.degenerateOperations	0
===================================================================
```

with `assertNotEquals(underSignOff, underNone)` beside it. **Closed.**

### 3.2 D-35 — closed, and the experiment reproduces

Static half, verified from git rather than from the record:

```
$ git show '0a93ad4f^:…/UnwrittenPortInvariantTest.java' | grep -n fullyAgreed
147:        assertEquals(subject.reviewedFullyAgreed, tally.fullyAgreedOperations().keySet(),
$ git show '0a93ad4f:…/UnwrittenPortInvariantTest.java'  | grep -n "assertEquals(subject"
147:        assertEquals(subject.reviewedFullyAgreed, tally.discriminatingFullyAgreedOperations().keySet(),
```

So `0a93ad4f` did stop asserting the degenerate half. The claim is exact.

Live half — **the brief's requirement, that the restored assertion would FAIL on the earlier code**.
In a detached worktree at `90404528` I added the restored assertion and *nothing else*:

```java
        assertEquals(java.util.Set.of(), tally.degenerateFullyAgreedOperations().keySet(),
                "D-35 EXPERIMENT: the degenerate fully-agreed bucket, asserted");
```

```
$ mvn -B -o -pl use-core test -Dtest=UnwrittenPortInvariantTest
[ERROR] Tests run: 10, Failures: 1, Errors: 0, Skipped: 0 <<< FAILURE! -- in Unwritten-port invariant
[ERROR] org.tzi.use.uncertainty.differential.UnwrittenPortInvariantTest.anUnwrittenPortAgreesWithNothing(Subject)[6]
org.opentest4j.AssertionFailedError: D-35 EXPERIMENT: the degenerate fully-agreed bucket, asserted ==> expected: <[]> but was: <[RealValue.value()]>
[ERROR] Tests run: 10, Failures: 1, Errors: 0, Skipped: 0
```

One failure out of ten: the pre-existing assertion passed on the same run, so the added one is
carrying its own weight and `RealValue.value()` really was a finding that had leaked from an
asserted list into a printed one. **The assertion is restored, not decorative.**

*Caveat, and the porter states it too.* On today's tree all fourteen buckets (7 subjects × 2 halves)
are empty, so the assertion currently reads `{} == {}`:

```
fully agreed ops, DISCRIMINATING (a finding about the subject)  (none)
fully agreed ops, NOT DISCRIMINATING (a finding about the corpus)  (none)   [ASSERTED against reviewedDegenerateFullyAgreed since the D-35 fix]
```

× 7 subjects. That is a live guard against future growth and it is not vacuous in the D-20 sense
(it pins an extensional set, not a predicate the file computes), but it proves nothing about today.
Correctly flagged by the porter; I confirm it.

*A wording correction for the record.* `stage-01-round6-fixes.md` and the commit message both say
the experiment was run "on unmodified **HEAD** behaviour". It was not — it can't have been, because
HEAD contains that assertion with an empty expected set and HEAD passes. The run is on the tree at
`90404528`, which was HEAD when the porter did the work. Substance right, referent misleading; worth
one sentence of amendment since the whole point of these records is that a reader can re-run them.

### 3.3 D-36 — closed for the acceptance test, as scoped

`ADD_FLOOR = 784` is a compile-time constant declared above the run, derived from
`InputGenerator.uRealBoundaries()` (22) + `RANDOM_DRAWS` (6) = 28, 28² = 784 — i.e. from the corpus
and not from the output. The gate is `result.requireStagePass(ADD_FLOOR, acknowledged)`, and
`isClean()` is printed beside it rather than asserted:

```
report               …/use-core/target/differential/s1-smoke-ureal-add.tsv
golden (matched)     …/docs/port2/differential/s1-smoke-ureal-add.tsv
isClean()            true   <- measured, NOT the pass criterion (D-36)
stage gate failures  []
STAGE STATEMENT      URealValue.add(value): 784 rows, 784 measured, 784 agreed, 0 disagreed, 258 distinct reference value(s) [DISCRIMINATING]
```

Negative direction, and it names the clause:

```
refused              sweep of URealValue.minus(value) is not a stage pass: - 226 row(s) did not agree.
                     tally: URealValue.minus(value): 784 rows, 784 measured, 389 distinct ref, AGREE=558, DIFFER=226
```

`assertThrows(IllegalStateException.class, …)` with `assertTrue(refusal.getMessage().contains("did not
agree"))`. The class comment states the input domain in prose and ends "no receiver at 42" (D-30).
**Closed at the scope claimed.** The gate remains opt-in — the porter says so, `harness-contract.md`
§4.1's withdrawn claim stays withdrawn, and I have nothing to add.

---

## 4. D-37 (NEW, MAJOR) — the type token is *observed* on the reference side and merely *declared* on the ported side

This is the round's own fix turned against the number it produced. Three measurements.

### 4.1 A **perfect** port, adapter written the documented way, diverges on 182 of 285 operations

The subject is a second, independently loaded `HistoricalOracle` — the same construction the
detection-power class uses for a perfect port — wrapped in an adapter that returns
`UValue.<factory>(content)` and never calls `asJavaType`. That is what `StubCandidate` does, and
`Candidate`'s own Javadoc names `StubCandidate` "the only worked example its adapter has to copy".
The content on every row is bit-for-bit the historical content.

```
=== SCRATCH C: a PERFECT port, adapter typed by the factories ==========
rows                 19083, agreed 13754
DIFFER rows          3445
operations diverging 182 of 285
stage passes         reflective-adapter 74 -> factory-typed 45; lost 29
  index	operation	inputs	historical	ported	verdict	note
  0	BooleanValue.compareTo(value)	BOOLEAN(true)@BooleanValue | BOOLEAN(true)@BooleanValue	INTEGER(0)@Integer	INTEGER(0)@IntegerValue	DIFFER	java type mismatch: reference returned java.lang.Integer (INTEGER(0)@Integer) / subject returned org.tzi.use.uml.ocl.value.IntegerValue (INTEGER(0)@IntegerValue); the content is IDENTICAL -- right content, wrong Java type (defect D-18); this row is a divergence because a port of these classes must reproduce the declared result type, not only the payload.
=======================================================================
```

Set that beside what `d13d4858` publishes as its detection power:

```
boxed    rows 19083, measured 17199, agreed 13754  {AGREE=13754, BOTH_THREW=910, DIFFER=3445, HARNESS_ERROR=883, UNMEASURABLE=91}
boxed   DIFFER rows  3445
DETECTED on          182 of 285 operations
stage passes         control 74 -> boxed 45; lost 29
```

**3 445 / 182 / 74 → 45 / lost 29 — the same four numbers, from a port with no defect in it.** The
harness cannot distinguish "the port's method returns the wrong class" from "the adapter did not
attribute the class it observed", and the second is what the documented factory path produces for
182 of 285 operations. The 29 stage passes lost are the U-types' own accessors:

```
[BooleanValue.compareTo(value), BooleanValue.hashCode(), BooleanValue.isFalse(), BooleanValue.isTrue(),
 BooleanValue.toString(), BooleanValue.toStringWithType(), BooleanValue.value(),
 IntegerValue.compareTo(value), IntegerValue.hashCode(), IntegerValue.toString(),
 IntegerValue.toStringWithType(), IntegerValue.value(), StringValue.compareTo(value),
 StringValue.hashCode(), StringValue.toString(), StringValue.toStringWithType(), StringValue.value(),
 UIntegerValue.compareTo(value), UIntegerValue.hashCode(), UIntegerValue.toString(),
 UIntegerValue.toStringWithType(), UIntegerValue.uncertainty(), UIntegerValue.value(),
 URealValue.compareTo(value), URealValue.hashCode(), URealValue.toString(),
 URealValue.toStringWithType(), URealValue.uncertainty(), URealValue.value()]
```

`URealValue.value()`, `URealValue.uncertainty()`, `UIntegerValue.value()`,
`UIntegerValue.uncertainty()` — the four accessors the whole extension is about.

### 4.2 …and one line of adapter code makes a genuinely wrong class invisible again

Same boxing defect the round plants — the port really returns `IntegerValue` where the historical
returns `int` — with an adapter that states the type the historical signature declares:

```java
return boxIntoValueClass(v).asJavaType(v.javaType());   // one line
```

```
=== SCRATCH B: does a declared type token survive contact with a liar? ==
boxing port, adapter attributes honestly   DIFFER rows 3445
boxing port, adapter declares ref's type    DIFFER rows 0
=======================================================================
```

`asJavaType(String)` is public and takes an arbitrary string. The porter's argument that there is
"deliberately no wildcard 'unattributed' state, because that would be D-17's shape" is true of the
*absence* of an attribution and beside the point for a *false* one: instead of one wildcard a
subject has an unbounded set of specific lies, each costing one line and each indistinguishable
from honesty. `fromHistorical` derives `javaType` from `result.getClass().getName()`; **there is no
counterpart for the ported side anywhere in the harness.**

This is not hypothetical inside the tree either. The round had to write exactly this move into
`UnwrittenPortInvariantTest.aDegenerateOperationNeedsAWrittenSignOff`
(`UnwrittenPortInvariantTest.java:790`):

```java
literals.put(isUReal.key(), UValue.bool(true).asJavaType("java.lang.Boolean"));
```

a hand-declared attribution on a hand-written literal, added to keep a test green. The porter reads
that as "the fix working on its first contact with the tree's own code". It is also the first
instance of the pattern D-37 is about, and the accompanying comment is the only place in the whole
tree where the obligation is written down — inside one test, as an aside.

### 4.3 Where the obligation is, and is not, documented

| place a S4 adapter author reads | says an adapter must attribute the observed Java class? |
|---|---|
| `Candidate` Javadoc (the interface every adapter implements) | **no mention of `javaType`/`asJavaType` at all** |
| `StubCandidate` (named in `Candidate` as "the only worked example its adapter has to copy") | **no** — factory-typed throughout; correct only because its three operations happen to return `URealValue` |
| `harness-contract.md` §7 "**Two** traps specific to writing an S4 adapter" | **no** — the two traps are `HarnessMarshallingException` and bulk `AcceptedThrowPairs` |
| `harness-contract.md` §1 clause "values" | states the *rule* (a value is content + Java type); says nothing about who supplies the type |
| `UValue` class Javadoc | closest thing: "the type an adapter returning that kind is claiming" — an aside, not an obligation, and no warning that the factory default is wrong for 182 of 285 operations |
| `stage-01-round6-fixes.md` §1.4 | one paragraph, framed as an anecdote about a test |

```
$ grep -rn "asJavaType" docs/
docs/port2/stage-01-round6-fixes.md:287:`UValue.bool(true).asJavaType("java.lang.Boolean")` and the sign-off key is `BOOLEAN(true)@Boolean`.
```

One hit, in a round record, in a sentence about a test fixture.

### 4.4 Why this is a defect and what closes it

Two consequences, and the second is the worse one.

1. **The published number is unattributable.** `harness-contract.md` §1 now asserts "the fix has no
   false-divergence mode" and cites `stage-01-round6-fixes.md` §1.5. §4.1 above is a false-divergence
   mode, it is reachable through the documented worked example, and it produces the identical
   measurement to the planted defect. An S4 fidelity claim quoting "182 of 285" would be quoting a
   property of its adapter.
2. **The incentive runs the wrong way.** An S4 author who meets 3 445 spurious `DIFFER` rows will
   clear them by writing `asJavaType(...)` literals — and §4.2 shows that once that habit exists the
   check is worth nothing. This is the same shape as the blanket-sign-off pressure
   `harness-contract.md` §7 warns about, arriving through the fix that was supposed to strengthen the
   instrument.

Cheap, test-scoped closure, in the order I would do it:

* **A derived attribution for the ported side.** Publish the mechanism `fromHistorical` already has —
  a `UValue.observedFrom(Object)` / `Candidate` helper that takes the object the port actually
  returned and reads `getClass().getName()` — and say in `Candidate`'s Javadoc that an adapter which
  does not route through it is declaring, not observing.
* **A third trap in `harness-contract.md` §7**, with §4.1's number in it: *a factory-typed adapter
  costs 182 of 285 operations and 29 stage passes on a perfect port.*
* **Make `StubCandidate` teach it** — even where the factory default is already right, attribute
  explicitly, with a comment saying why.
* **Pin §4.1 as a test.** The measurement that a content-perfect port with a factory-typed adapter
  loses exactly 29 stage passes belongs beside `aWrongJavaTypeWithRightContentIsADivergence`, so
  that the two readings of 3 445 sit next to each other and no later stage can quote one without
  seeing the other.

None of this touches `src/main` or weakens the D-18 fix. **I am not asking for the fix to be
reverted** — D-18 was real (§2.2), the semantics are right (a `boolean`-declared method answered
with a `BooleanValue` *is* an infidelity), and the control is intact.

---

## 5. New MINORs

### 5.1 D-38 (MINOR) — the package-insensitivity rationale does not hold on the `OPAQUE` branch

The justification for comparing simple names is that "comparing fully-qualified names would make
every row of a port that relocated the package a false divergence"
(`UValue.java`, class comment), pinned by `theTypeTokenIsPackageInsensitiveOnPurpose`. But that test
only exercises `Kind.UREAL`, whose content carries no class name. For `Kind.OPAQUE`,
`UValue.opaque(className, repr)` puts the **fully-qualified** name into `text` — and
`HistoricalOracle.opaqueRepresentation` puts the FQNs of the object's field-declaring classes in as
well:

```
SBooleanValue.TRUE   canonical OPAQUE("org.tzi.use.uml.ocl.value.SBooleanValue|org.tzi.use.uml.ocl.value.SBooleanValue{Value.fType=org.tzi.use.uml.ocl.type.SBooleanType{BasicType.fTypename=\"SBoolean\"},SBooleanValue.sBoolean=uDataTypes.SBoolean{SBoolean.a=1.0,SBoolean.b=1.0,SBoolean.d=0.0,SBoolean.relativeWeight=1.0,SBoolean.u=0.0}}")@SBooleanValue
```

So a relocated port **is** a divergence on every OPAQUE row. From the representation census, that is
**197 rows across 17 operations** (`type()`/`getRuntimeType()` × 16, `UIntegerValue.getuInteger()` × 1):

```
  4	org.tzi.use.uml.ocl.type.BooleanType
  16	org.tzi.use.uml.ocl.type.IntegerType
  2	org.tzi.use.uml.ocl.type.RealType
  30	org.tzi.use.uml.ocl.type.StringType
  18	org.tzi.use.uml.ocl.type.UBooleanType
  30	org.tzi.use.uml.ocl.type.UIntegerType
  48	org.tzi.use.uml.ocl.type.URealType
  34	org.tzi.use.uml.ocl.type.UStringType
  15	uDataTypes.UInteger
```

`harness-contract.md` §249 understates this as "a port need only reproduce a string". The string
contains the FQN of the class, the FQNs of every field's declaring class, and every field name. Not
a scoring error — it is the pre-existing OPAQUE limit — but round 6 now rests a design decision on
a rationale its own report contradicts on 197 rows, and that contradiction is undocumented.

### 5.2 D-39 (MINOR) — "an operation's return type is one class" is false for 84 of 285 operations

The census test's Javadoc argues: *"A historical operation's declared return type is one class, so
for any single operation there is exactly one right answer and 'the port used the other class' is a
defect and not a representation choice."* I checked the declared return types directly, through the
isolated loader, over all 285 enumerated operations:

```
=== SCRATCH A: DECLARED return type of every enumerated operation =====
operations resolved        285 of 285
--- declared return types, with how many operations declare each -------
  140	boolean
  6	double
  18	int
  18	java.lang.String
  16	org.tzi.use.uml.ocl.type.Type
  3	org.tzi.use.uml.ocl.value.BooleanValue
  3	org.tzi.use.uml.ocl.value.IntegerValue
  3	org.tzi.use.uml.ocl.value.RealValue
  1	org.tzi.use.uml.ocl.value.SequenceValue
  2	org.tzi.use.uml.ocl.value.StringValue
  19	org.tzi.use.uml.ocl.value.UBooleanValue
  12	org.tzi.use.uml.ocl.value.UIntegerValue
  21	org.tzi.use.uml.ocl.value.URealValue
  5	org.tzi.use.uml.ocl.value.UStringValue
  9	org.tzi.use.uml.ocl.value.UncertainBooleanValue
  1	uDataTypes.UInteger
  8	void
--- operations whose DECLARED return type is not a single class --------
  count 84
  16	org.tzi.use.uml.ocl.type.Type [interface]
  1	org.tzi.use.uml.ocl.value.SequenceValue [non-final class]
  19	org.tzi.use.uml.ocl.value.UBooleanValue [non-final class]
  12	org.tzi.use.uml.ocl.value.UIntegerValue [non-final class]
  21	org.tzi.use.uml.ocl.value.URealValue [non-final class]
  5	org.tzi.use.uml.ocl.value.UStringValue [non-final class]
  9	org.tzi.use.uml.ocl.value.UncertainBooleanValue [non-final class]
  1	uDataTypes.UInteger [non-final class]
```

**84 of 285** declare an interface or a non-final class, so more than one runtime class is legal by
the API for each of them. The nine `UncertainBooleanValue`-declared operations are the sharpest
case: the census shows kind `UBOOLEAN` is only ever carried by `UBooleanValue`, i.e. those nine
return a subclass through a superclass-declared signature. A port that returned the *declared* type
there — a perfectly defensible reading of the same API — reads as divergence on every driven row.

The stated conclusion (over the shipped corpora, "the port used the other class" is a defect) still
holds, because `noOperationAnswersWithTwoRuntimeClasses` measures it and it is 0 of 285. What is
false is the *reason* given, and the reason is what a reader will carry into S4. This is the same
corpus boundary the porter already named as the obvious next attack in `foundation-verdict.md`; §5.2
supplies the number.

---

## 6. Things I attacked and could not break

Recorded so the next reviewer does not spend the round here.

* **False green — five constructions, none succeeded.** (i) A port that returns the right content in
  the wrong class *and* declares it honestly: caught, 3 445 rows. (ii) The type token exploited to
  satisfy the D-15 discrimination clause on a representation difference instead of a behavioural
  range — `distinctReferenceValues()` counts `canonical()`, which now carries the class, so an
  operation with constant content and varying class would clear `DISCRIMINATING_MINIMUM = 2` for
  free. Measured:

  ```
  === SCRATCH D: type token vs the D-15 discrimination metric ============
  operations with observations        274
  NOT DISCRIMINATING by canonical()   159
  NOT DISCRIMINATING by content()     159
  operations where the two disagree    0  {}
  ======================================================================
  ```

  0 of 274 today, and the precondition for it ever becoming non-zero is exactly what
  `noOperationAnswersWithTwoRuntimeClasses` asserts against — so the route is guarded, indirectly
  but soundly. **Not a defect.** (iii) A sign-off authored against a pre-round-6, content-only key:
  fails closed, because `AcceptedDegenerateOperations` is matched against `soleReferenceValue()`,
  which is now type-bearing. (iv) Loosening: the type suffix is an *append*, and the `SEQUENCE` and
  `OPAQUE` branches recurse through `canonical()`, so no pair that differed before can compare equal
  now. (v) `simpleName` cutting at `$` collapses `A$1` and `B$1` to `1`; no inner or anonymous class
  appears anywhere in the 285-operation census, so this is not reachable.
* **The `Kind` distinction the porter refused to overclaim.** `URealValue(3,0)` vs
  `UIntegerValue(3,0)`, and `IntegerValue` vs `UIntegerValue`, were already `DIFFER` before this
  round because the kind leads the canonical form. He pinned it rather than claiming to have fixed
  it, and §1.1 of his record says so plainly. Correct, and correctly flagged.
* **`ECHO_SUBJECT_REVIEWED` is now empty and asserted**, and `RealValue.value()` is detected. Both
  buckets of `f-echoes-receiver` empty, both asserted. Confirmed from the run.
* **P8/P9** remain at 0 detection, cost 74 → 70 each, and lose exactly the four operations they lie
  about. The porter's judgement — a `supports()`-declined `UNSUPPORTED` sub-bucket as *reporting*,
  never as a verdict, because a partially-implemented port is a legitimate S4 state; nothing cheap
  for P8 — is the right call and I would not change a word of it.
* **D-30 unchanged.** P10: 0 `DIFFER`, 74 → 74, stage-pass-identical to the control including a full
  pass on the defective `URealValue.add(value)`.
* **D-29 untouched**, and the 29 stage passes the boxing port loses is further evidence for its
  recommendation. A decision for the human, not for this round.

---

## 7. Open register after round 6

| id | severity | state |
|---|---|---|
| D-17 / D-32 | MAJOR | open, re-measured, recorded |
| D-20 | MAJOR | open (`everyKindIsEitherAnObservationOrUnmeasurable` is tautological) |
| D-29 | MAJOR | open, human decision |
| D-30 | MAJOR | open, re-measured unchanged |
| **D-37** | **MAJOR** | **NEW — §4.** The ported side's Java type is declared, not observed; the round's detection figure is reproduced exactly by a content-perfect port with a factory-typed adapter, and erased by one line of adapter code. |
| **D-38** | **MINOR** | **NEW — §5.1.** The package-insensitivity rationale is contradicted on the `OPAQUE` branch: 197 rows across 17 operations compare fully-qualified names. |
| **D-39** | **MINOR** | **NEW — §5.2.** "An operation's declared return type is one class" is false for 84 of 285 operations; the conclusion survives on the census, the stated reason does not. |
| D-18 | CLOSED | blind spot verified closed against an independently reproduced *before* state |
| D-34 | CLOSED | verified |
| D-35 | CLOSED | verified, including the failing-on-the-old-tree experiment |
| D-36 | CLOSED | verified at the scope claimed (acceptance test only) |

Open MAJORs: 4 → **5**. Three of the four preconditions the brief named are genuinely met. The
fourth — D-18, the detection hole — is closed as a *blind spot* and has opened a *false-divergence
mode* in its place, which is what D-37 records.

**Before S4 quotes any number from this instrument, D-37's four bullets in §4.4 should be done.**
They are test-scoped, they are small, and without them the strongest figure round 6 produced can be
read two ways.

---

*Refuter, round 6. Everything above is pasted from a run on this machine; the scratch subjects used
for §4 and §5 were deleted and the worktree removed before this file was committed, so the tree is
clean and `mvn -B verify` at `854abb83` is the run reported in §1.*
