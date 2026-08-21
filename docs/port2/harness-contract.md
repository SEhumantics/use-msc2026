# The differential harness — contract for S4–S7

**Status: normative for every stage that quotes a differential number.** This file is the rules the
harness enforces and the rules a stage must follow — not the round-by-round history of how they got
here. That history (eight review rounds; round 6–8's fixes and refutations; defects D-18, D-43, D-52
and the rest) is recorded in [`stage-01.md`](stage-01.md) §10, which is authoritative over every other
section of that file and is the place to read *how* a rule was arrived at. **This file says what the
rule is, not the story.**

**Provenance note (2026-08-21).** `upstream-oracle-verification.md`, cited elsewhere in this
document's history, was consolidated during a documentation cleanup and no longer exists as a
separate file; its findings are closed/tabulated in `upstream-oracle-profile.md` §5.2.6. Full
original content in git history.

The harness is `use-core/src/test/java/org/tzi/use/uncertainty/differential/`. The short human answer
to "may S3 start?" is [`foundation-verdict.md`](foundation-verdict.md). **If you are writing S4, read
§8 first and then come back**: it is this file in imperative form, so S4 does not rediscover the
findings behind it.

---

## 0. THE ACCEPTANCE COMMANDS, AND THE FOUR DECISIONS THIS FILE IS BOUND BY

### 0.1 THE GATE IS A SCRIPT. Hand-typing `-P` is not the gate.

```bash
scripts/upstream-oracle-gate.sh          # THE acceptance gate. This, verbatim, is what a stage quotes.
```

> **Read [`gate-threat-model.md`](gate-threat-model.md) before extending or arguing with this gate.**
> It is normative. It names every accident this gate exists to catch, states what it deliberately does
> **not** defend against — an operator who injects `-D` properties to disable their own acceptance
> check — and why declining that is the right call; its §3 lists every known bypass.
>
> `scripts/upstream-oracle-gate.sh` **is** the gate; the in-build binding is defence in depth against
> accidents; and a number produced by any other invocation is not a gate result and may not be quoted
> as one.

The two commands the script runs, for the record — **quote the script, not these**:

```bash
mvn -q clean && mvn -B verify -Djava.awt.headless=true                     # default: vintage-free
mvn -q clean && mvn -B verify -Pupstream-oracle -Djava.awt.headless=true   # + upstream's own tree
```

**A stage is not accepted until both are green, and a stage document that quotes only one of them has
not stated its acceptance** (decision **B3**; `upstream-oracle-profile.md` §5). Neither is `mvn test`,
which never runs the 130 failsafe methods.

The script adds four things Maven cannot: it hard-codes the profile id; it **fails** on
`could not be activated` anywhere in the log; it requires the floor to have printed an unqualified
`PASS` naming **both modules** and the expected mode; and it verifies each module's
`target/upstream-oracle-floor.receipt` **on disk after Maven has exited**, where no Maven property can
reach it.

Both commands also assert a **pinned, per-module, per-tier floor** — `scripts/UpstreamOracleFloor.java`,
run by Maven at phase `verify` in `use-core` and `use-gui`: *a floor chosen after the run is not a
floor*, and `0` is rejected outright.

| | `use-core` surefire | `use-gui` surefire | `use-core` failsafe | `use-gui` failsafe | total | **asserting** |
|---|---|---|---|---|---|---|
| default | 8 / 80 | 1 / 1 | 1 / 1 | 1 / 129 | **11 / 211** | **199** |
| `-Pupstream-oracle` | 41 / 351 | 8 / 17 | 1 / 1 | 1 / 129 | **51 / 498** | **465** |

**Quote the asserting figure whenever the number is used to argue scrutiny, and the total whenever it
is used to argue collection.** They differ by **12** (default) and **33** (profile) because six
ArchUnit architecture classes cannot fail: each calls `.evaluate()` and never `.check()`, computes a
violation count and prints it, so it asserts nothing (`upstream-oracle-verification.md` R-5).

What a stage must know about the floor, because these are the ways a green build is *not* a claim:

* Floors are **per module and per tier** — a reactor-wide total would hide `use-gui` vanishing behind
  `use-core`. Requesting `-Pupstream-oracle` and collecting default-build counts is an error, not a
  pass. Counts are **distinct classes/methods from the report XML**, never surefire's headline (the 14
  JUnit-3 `AllTests` aggregators inflate it to **1086** executions for 498 methods).
* **Do not lower a floor to make a run pass** (same rule as §8 step 2). If the suite legitimately
  grows, raise the floor in the commit that grows it.
* **No command-line property can switch the floor off; trying is a build failure.** `exec:exec`
  declares 22 parameters carrying a user-property expression: 7 are pinned, 8 are detected and fail
  the build if set, 14 are neither, deliberately ([`gate-threat-model.md`](gate-threat-model.md) §3
  R-3). The checker validates its entire argv against an exact option set, and
  `UpstreamOracleGateWiringTest` re-asserts the wiring from the `test` phase, where no
  `exec-maven-plugin` property reaches.
* **A truncated lifecycle does not outrun the gate's profile check**: the unactivatable-profile check
  also binds at `initialize`, so it fires under `test`/`compile`/`package` too. The count floors still
  bind only at `verify` — `mvn test` remains not a gate.
* **The allow-set escape hatch leaves a trace**: `-Duse.floor.allowProfiles` is echoed in the log and
  recorded in the receipt, and an acceptance run requires `allow-profiles=(none)`.
* **A partial reactor never says `PASS`**: `-pl use-core -Pupstream-oracle` prints `PARTIAL` and the
  wrapper rejects it. `-pl` is fine for iteration, never for acceptance.
* If an upstream test **fails** under the profile, that is a finding for the stage document, never an
  edit to the test.

### 0.2 The four decisions of 2026-08-17, and where each one's plan now lives

Recorded in `specification.md` §0.0 and `foundation-verdict.md` §3.0. Listed here because three of
them **reversed the recorded recommendation**, and a stage that works from the older prose does the
wrong thing while reporting a green gate.

| Decision | What the user chose | The plan a stage works from |
|---|---|---|
| **B3** | the `-Pupstream-oracle` profile | `upstream-oracle-profile.md`; §0.1 above |
| **B7** | **FIX** the historical defects, documenting each | [`b7-fix-plan.md`](b7-fix-plan.md) — per-row triage of all 33 behaviour-changing rows; **supersedes every `DEFER`** in `spec-parts/16-modernization-ledger.md` |
| **H14** | **BUILD** an input-domain coverage measure | [`h14-coverage-design.md`](h14-coverage-design.md) — design only; the implementing stage measures it. See §5 and §8 step 5 |
| **B2** | **FULL PORT** of `SBoolean`, all 39 operations | [`b7-fix-plan.md`](b7-fix-plan.md) §6, including the hard prerequisite: `SBooleanValue` marshalling in this harness, without which all 39 operations are `UNSUPPORTED` |

**B2's consequence for this file:** §5's list of what the harness cannot see includes `SBooleanValue`
because the harness declines it by name today. Under B2 that is no longer a costless limit — it is
work S9 must do in the harness before any SBoolean fidelity claim exists.

---

## 1. The principle

> **A differential oracle may report agreement only where it observed two comparable,
> non-degenerate values — and the claim reaches no further than the inputs it tried.**

Four clauses, each paid for:

* **two — not one.** A row where the harness failed to marshal, or where one side never ran, is the
  *absence* of a measurement, and an absence is not a measurement the two sides happen to share (D1:
  21 816 rows of this were once scored green).
* **values — not throws, and not the encodings of "no result".** Two throws are not a shared value
  however well their class names match (D2). Two `VOID`s are not a shared value (D-10). And a value is
  its **content together with its Java type**: right content in the wrong class is not a shared value
  either (defect D-18). `UValue.canonical()` ends in `@<simple class name>` for every kind that
  carries an observation, so `BOOLEAN(true)@Boolean` — a raw `boolean` — is not
  `BOOLEAN(true)@BooleanValue`.
  **Since round 8, a type-only difference is measured but no longer scored.** Where content matches
  and only the class differs, the row is `AGREE` and the difference is counted in
  `Result.javaTypeMismatchCount()` / `# rows.javaTypeMismatch` / `# op.<key>.javaTypeMismatch`, printed
  unconditionally by `stageStatement()`. **Content differences are unaffected and remain `DIFFER`.**
  That count is split by the subject's provenance at both scopes (H21) —
  `subjectTypeObservedCount()` / `subjectTypeAssumedCount()`, partitioning the mismatch population
  exactly — so `0` on one side means "all of them are the other", and that is the number that says
  whether a mismatch total is a finding about the **port** (`OBSERVED`) or about the **adapter**
  (`ASSUMED`). The reason for the demotion is dated and narrow: at S1 the ported side's token cannot be
  authentically observed, because no ported value class exists to observe — writing it *is* S4. **§7
  carries the dated REQUIREMENT that closes this at S4.** The full mechanism, with its measurements,
  is in `UValue`'s class Javadoc and `DiffVerdict.AGREE`'s Javadoc; read those before writing an
  adapter.
* **non-degenerate** — the operation must be *able* to answer differently. Two equal values over a
  single-point codomain are green by construction (D-15). Enforced: `Result.distinctReferenceValues()`
  counts the reference's answers over the measured rows, and `Result.requireStagePass` refuses below
  `DISCRIMINATING_MINIMUM = 2` without a written, value-keyed sign-off.
* **no further than the inputs it tried** — **not enforced, not measured, not published (D-30)**. A
  port carrying a real defect confined to a receiver value no shipped corpus contains is
  stage-pass-identical to a perfect port. This clause is discipline, and it is the one the instrument
  cannot keep for you.

---

## 2. Verdict vocabulary (`DiffVerdict.java`)

| Verdict | `isAgreement()` | `isMeasurement()` | Meaning |
|---|---|---|---|
| `AGREE` | **yes** | **yes** | Both sides returned a value; canonical forms identical — **including the Java type each side was observed or assumed as** (defect D-18). A subject whose adapter merely assumed its class can still agree by luck; see the type's own Javadoc and §7's third trap. |
| `ACCEPTED_THROW` | **yes** | no | Both threw, and a caller-supplied `AcceptedThrowPairs` allowlist names this exact pair — operation, both classes, both messages — with a written rationale. Opt-in; the default is empty. |
| `DIFFER` | no | **yes** | Both returned a value; canonical forms differ. |
| `INTENDED_DEPARTURE` | no | **yes** | Both returned a value, they differ, and an `IntendedDepartures` pre-registration (decision B7) names this exact pair — operation, ledger row, both canonical forms, predicted direction — with a written rationale. Not an agreement; is a measurement, and carries its own gate clause (an unused pre-registration refuses the stage). See `DiffVerdict.INTENDED_DEPARTURE`'s Javadoc. |
| `BOTH_THREW` | no | no | Both threw, unadjudicated — **whether or not the classes match.** Note carries both classes *and* both messages. |
| `MIXED` | no | no | One side returned a value, the other threw. The note names **which** side. |
| `UNMEASURABLE` | no | no | Neither side carried an observation: a `void` operation, or both sides `NULL`. Never raised when only *one* side lacks an observation — that is `DIFFER`. |
| `UNSUPPORTED` | no | no | At least one side could not be driven: the candidate does not declare the operation, or the receiver type is unmarshallable. |
| `HARNESS_ERROR` | no | no | The harness failed before any comparable value existed: marshalling, unwrapping, or a candidate returning Java `null`. |

`AGREE_THROWN` and `DIFFER_THROWN` **do not exist** (deleted in `e8b73e48`). Any document naming them
describes pre-`e8b73e48` code.

Two agreements, two measurements, and they are **different pairs**. `ACCEPTED_THROW` is an agreement a
human authored and the harness measured nothing. `DIFFER` is a measurement that is not an agreement.
**`measurementCount()` is the size of the evidence; `rowCount()` is not.**

---

## 3. The two metrics

| Metric | Method | What it answers |
|---|---|---|
| **Evidence size** | `measurementCount()` — `AGREE` + `DIFFER` + `INTENDED_DEPARTURE` rows | How many times were two values actually compared? |
| **Discriminating power** | `distinctReferenceValues()`, from `referenceValues()`; `isDiscriminating()`; `soleReferenceValue()` | Could the reference have said anything else? |

`referenceValues()` counts the **reference** column over **measured rows only**, under **exactly**
the equality the verdict uses. Both ways of getting it wrong — counting the subject's column, counting
over all rows — are pinned by
`DifferentialHarnessRegressionTest.distinctReferenceValuesCountsTheReferenceOverMeasuredRows`.

There is **no third metric for the input domain**, and there should be. See §5, D-30.

---

## 4. How a stage must gate on a sweep

### 4.1 Predicates that are NOT pass criteria

**`disagreements().isEmpty()`** is vacuously true of a sweep that compared nothing — an empty
domain, an all-`UNSUPPORTED` sweep, an all-`UNMEASURABLE` sweep. Never assert it.

**`isClean()`** (`measurementCount() > 0 && disagreements().isEmpty()`) is better and is still not a
pass criterion; its own Javadoc says so. **One** measured row passes it, and it is `true` for
**119 of 285** operations against a subject consisting of one hardcoded literal per operation. It
stays in the API because it is the right question for the harness's own regression tests, where the
codomain is known by construction. It is **not** the question a stage asks.

**`requireMeasurements(int)`** is a floor, not a gate: it says nothing about degeneracy.

**The gate is opt-in.** Nothing in the harness *forces* a stage through it: `isClean()`,
`requireMeasurements(int)` and `disagreements().isEmpty()` all still return a clean-looking answer on
a degenerate sweep. What is unavoidable is the *number*: `summary()`, `stageStatement()` and the
`# op.<key>.*` header block carry it whether or not the caller gates on it. `UncertaintyDifferentialSmokeTest`,
whose goldens are S1's committed evidence, is the worked example: it gates through
`requireStagePass(ADD_FLOOR, none())` with the floor written above the run, plus the golden comparison
and `throwClassMismatchCount() == 0` / `javaTypeMismatchCount() == 0`, and prints `isClean()` beside
the gate's verdict rather than passing on it. **Copy that test, not this warning.**

### 4.2 The criterion a stage must use — one call

```java
result.requireStagePass(minimumMeasurements, acknowledgedDegenerateOperations);
```

Three clauses (`stageGateFailures`); it throws with **every** failing clause and the numbers behind
it — a fourth clause applies under the three-argument overload that names an `IntendedDepartures`
pre-registration (decision B7): an unused declaration refuses the stage, so a mechanism that only ever
*permits* differences cannot let an unfixed defect pass as agreement.

1. **`measurementCount() >= minimumMeasurements`**, the floor derived from the corpus and **written
   into the stage document before the run**. A floor chosen after seeing the run is not a floor. `0`
   is rejected outright.
2. **No row disagreed** — and every non-agreement verdict counts as a disagreement, `BOTH_THREW`,
   `HARNESS_ERROR`, `UNSUPPORTED` and `UNMEASURABLE` included. **Read D-29 in §5 before you write a
   stage around this clause: a perfect port fails it on 92 of 285 operations.**
3. **`distinctReferenceValues() >= 2`** — the sweep *could* have failed — **or** the operation
   carries a sign-off in `AcceptedDegenerateOperations`, keyed on the operation **and** the exact
   single canonical value, with a mandatory non-blank rationale copied into `stageStatement()` and
   into the report header. The canonical value in that key is **type-bearing**
   (`BOOLEAN(true)@Boolean`, not `BOOLEAN(true)`), so a sign-off also lapses if the operation starts
   answering with the right content in a different class.

Two checks the gate does not make and a stage still must:

4. **Byte-identical golden comparison** via `DiffReportWriter.assertMatchesGolden`, so any change in
   the numbers is a diff someone has to read and approve.
5. **`throwClassMismatchCount() == 0`**, or an explanation. A port that fails on the right rows with
   the wrong exception type leaves every other aggregate bit-identical to a correct port's.
6. **`javaTypeMismatchCount() == 0`** — rows on which the content matched and the Java class did not.
   Scored `AGREE` since round 8 (see §1 and §7), so they are invisible in `rows.agreement`,
   `rows.disagreement` and every `verdict.*` line, and this is the only number that carries them.
   **At S1 this is a figure to publish; from S4 it is a REQUIREMENT and a gate clause — but only under
   the adapter shape §7 mandates** (defect D-52): the object whose class is read must be the object
   the invocation returned. `stageStatement()` prints the figure unconditionally, including when it is
   zero, and since H21 the provenance split beside it. **Two things this number is not.** It is not a
   lower bound on wrong-class rows: a row wrong in *both* dimensions is a `DIFFER` and leaves this
   count (D-54). And it is not an attribution: see §7.

### 4.3 A worked stage gate, and the naive one that is not a gate

```java
// ---------- NOT a stage gate. Every line of this is satisfied by a port with no logic. ----------
DifferentialSweep.Result r = DifferentialSweep.sweep(op, domain, oracle, port);
assertTrue(r.disagreements().isEmpty());          // vacuous if nothing was compared
assertTrue(r.isClean());                          // true for 119 of 285 ops against 119 literals
```

```java
// ---------- A stage gate. ----------
// Written in the stage document BEFORE the run:
//   floor = 500  (URealValue.add(value) draws 24x24 = 576 rows from uRealBoundaries())
//   sign-offs = none; add(value) is expected to be discriminating
static final int ADD_FLOOR = 500;

DifferentialSweep.Result r = DifferentialSweep.sweep(op, domain, oracle, port);

r.requireStagePass(ADD_FLOOR, AcceptedDegenerateOperations.none());   // clauses 1-3, or throws
assertEquals(0, r.throwClassMismatchCount(), r.summary());            // clause 5
assertEquals(0, r.javaTypeMismatchCount(), r.summary());              // clause 6 -- REQUIRED from S4,
                                                                       // and only a gate under §7's seam shape (D-52)

// The four figures that must appear in the stage document, from the harness, not by hand:
System.out.println(r.stageStatement(AcceptedDegenerateOperations.none()));

// ...plus the fifth figure the harness does NOT compute YET (D-30). Decision H14: BUILD an
// input-domain coverage measure (h14-coverage-design.md); prose-stated domains were the recorded
// recommendation and were NOT taken. Until the measure is built, state the domain in prose AND state
// that the measure is not built: "576 agreed" alone is not a fidelity claim a reader can check.

DiffReportWriter.assertMatchesGolden(
        DiffReportWriter.writeAll("s4-ureal-add.tsv", List.of(r), digests,
                                  AcceptedDegenerateOperations.none()),   // the only form there is
        Path.of("docs/port2/differential/s4-ureal-add.tsv"));             // clause 4
```

Two rules the example encodes:

* **`writeAll` and `write` both require the sign-off set, and there is no other form.** Pinned
  reflectively by `DifferentialHarnessRegressionTest.aReportCannotUnderstateItsOwnSignOffs`: no
  `write`/`writeAll` overload without the parameter may exist.
* **Compare against a recorded baseline, not against `true`.** On the 92 operations where a perfect
  port already fails clause 2, `isStagePass` is `false` before and after a real infidelity (D-29).
  Record `stageGateFailures(...)` for the perfect-port baseline and diff against it; the *clause
  list* changes even when the boolean does not.

### 4.4 Signing off a genuinely-constant operation

Some operations really are constant by specification — `isUReal()` compiles to `iconst_1; ireturn`.
They are part of the ported surface and must not be deleted from the inventory:

```java
AcceptedDegenerateOperations.builder()
    .accept("URealValue.isUReal()", "BOOLEAN(true)@Boolean",   // the key is type-bearing: D-18
            "type predicate; the historical body is iconst_1/ireturn, so BOOLEAN(true) is the whole "
          + "of its specification. Agreement shows the operation exists and is reachable; it is not "
          + "evidence about any computation.")
    .build();
```

The key includes the value, so the sign-off **lapses by itself** if the operation ever answers
otherwise. A blanket "accept all type predicates" cannot be expressed. **Do not sign one off to make
a run pass:** the rationale has to say what a reader should *not* conclude.

### 4.5 What a stage must publish alongside any fidelity figure

Use `result.stageStatement(acknowledged)` — **that method** cannot render an agreement figure without
the discrimination figure and the java-type-mismatch figure beside it, and `summary()` carries both
too. **The binding is on those two methods, not on the class** (D-53): `agreementCount()`,
`agreements()` and `isClean()` are public and return the agreement population unaccompanied, and
`isClean()` is `true` on a sweep carrying java-type mismatches. **So quote through `stageStatement()`;
do not build your own line out of `agreementCount()`.**

```
URealValue.add(value): 576 rows, 576 measured, 576 agreed, 0 disagreed, 164 distinct reference value(s) [DISCRIMINATING]
URealValue.isUReal(): 24 rows, 24 measured, 24 agreed, 0 disagreed, 1 distinct reference value(s) [NOT DISCRIMINATING: always BOOLEAN(true)@Boolean; acknowledged: ...]
```

Never a bare agreement percentage. Never a file-level total: `# rows.*` and `# verdict.*` are sums
over every result in the file and hide an operation that measured nothing (D-21). Read the
`# op.<key>.*` block — and note that those keys are **not unique** if one report holds several
results for one operation (**D-41**, open, latent).

---

## 5. What the harness cannot measure — declared limits

These are properties of the instrument, not results. A stage needing one of them needs a different
instrument and must say so rather than report a number.

| Limit | Extent | Consequence |
|---|---|---|
| **Post-state** | all 8 `void` mutators, e.g. `setTypeToRuntimeType()` | The receiver is never re-read. Every row is `UNMEASURABLE` — never an agreement, so a port cannot be called faithful on them either. |
| **`SBooleanValue`** | 39 operations | Not in `MARSHALLABLE_RECEIVERS`; no `SBoolean` marshalling, no `UValue.Kind`. Reported `UNSUPPORTED`, never silently skipped. Under decision B2 (§0.2) this is not a costless limit — it is work S9 must do before any SBoolean fidelity claim exists. |
| **Collection receivers** | — | Out of reach for the same reason. |
| **`org.tzi.use.uml.ocl.type.*`, `uDataTypes.*`** | the whole type layer and the uncertainty library | Cannot be named as receivers. Observed only indirectly, as `OPAQUE` canonical strings built by field reflection. **That string is not a free choice** (defect D-44): `UValue.opaque(className, repr)` puts the fully-qualified class name into the compared content, and `HistoricalOracle.opaqueRepresentation` adds the FQNs of every field's declaring class, so on the `OPAQUE` branch a port that **relocated the package** is a divergence — **197 rows across 17 operations**. Package-insensitivity is a property of the **token**, never of the row. |
| **Operations not nameable as a `UOp`** | **33 of 318** public instance methods on the 8 marshallable receivers | No row, no verdict, not even an `UNSUPPORTED` marker. Includes `equals(Object)` and `compareTo(Object)` on all eight receivers. **"285 operations" is not the ported surface; it is the surface this harness can name.** A port with a broken `equals` is invisible here. |
| **Primitive vs boxed results** (defect D-18; DEMOTED from a verdict to a counted dimension in round 8) | **182 of 285 / 3 445 rows** for a non-attributing adapter | `canonical()` is type-bearing; where content is identical and only class differs, the row is `AGREE` and counted in `javaTypeMismatchCount()` because at S1 the ported token cannot be authentically observed. **§7's dated REQUIREMENT closes it at S4.** Two residual costs: (a) the compared token is the class's *simple* name, so two distinct classes sharing one simple name compare equal — unreached in today's 285-operation census, but a corpus fact, and inherits D-30; (b) does not honour the `OPAQUE` branch (D-44, row above). |
| **The ported side's Java type cannot be authentically observed at S1** (defect D-43) | same 182/285, 3 445 rows | `UValue.observedFrom(Object)` is the only attributing route (see its own Javadoc for the D-52 shape requirement, and `Candidate`'s class Javadoc for the adapter-author obligation). The 3 445 is identical for a defect-free port and a genuine 401-row/9-operation wrong-class port seen through a non-attributing adapter — the **only** discriminator is the row note's provenance clause, and that choice of observed object is itself unchecked unless the invocation-seam shape is followed (D-52, §7). |
| **The type-mismatch count is not monotone in wrongness** (D-54, MINOR, open) | 3 445/182 ops → **1 883/42 ops** when a content defect is added | `javaTypeMismatchCount()` is "an `AGREE` row whose columns differ", so a row wrong in **both** dimensions is a `DIFFER` and leaves the count. **The header figure must not be read as a lower bound on wrong-class rows.** |
| **Two latent holes from the round-8 demotion** (D-55, D-56, MINOR, open; both measured unreachable in today's corpus, inherit D-30) | — | **D-55:** `opaque()` renders content as a non-injective concatenation, so two values differing in **both** class and representation can render equal content and be demoted to `AGREE`. **D-56:** the demotion is **not applied at depth** — a nested type-only difference inside a `SEQUENCE` is still `DIFFER` while the identical difference at top level is `AGREE`. |
| **The demotion's cost at gate level** (D-57, MINOR, record gap) | **29** stage passes for a wrong-class or unattributed port; **4** for a subject that only echoes its receiver | Every operation that passes now with `javaTypeMismatch > 0` is a pass the demotion created. Not a false green — the four an echoing subject gains are all discriminating accessors, each reviewed and signed off — but must not be read as measurements of a computation. |
| **Single-valued operations** (D-15, enforced) | **159 of 285** against a perfect port (11 measured-nothing / 159 single-valued / 115 discriminating) | Agreement is decided before either implementation runs. The gate refuses them; a sign-off is per operation and per value. |
| **Zero-measurement operations** (D-19) | **11 of 285** | The 8 void mutators plus 3 operations that throw on every input the corpora hold. |
| **Input-domain coverage** (D-30, open, MAJOR — decision H14: BUILD the measure; design in [`h14-coverage-design.md`](h14-coverage-design.md), unimplemented) | unmeasured everywhere | `distinctReferenceValues()` measures the **codomain**; its dual — how much of the input domain was reached — is computed nowhere, published nowhere, gated nowhere. A port carrying a real defect confined to one untested receiver value produced 0 `DIFFER` rows and a full stage pass. |
| **Corpus depth decides the census** (D-28/D-31, open, MINOR) | 23 `RealValue.*` operations; the string-index family | The corpora hold exactly one `RealValue`, so all 23 of its operations are single-valued *by arithmetic*. **"159 single-valued" is a joint fact about the implementation and the corpus.** |
| **The gate is not satisfiable by fidelity** (D-29, open, MAJOR) | 92 of 285 | A perfect port reaches a stage pass on **74 of 285**; 119 are refused by clause 3 (D-15, as designed) and **92 by clause 2**, for `BOTH_THREW` / `HARNESS_ERROR` / `UNMEASURABLE` rows a faithful port cannot avoid. The only route out is hand-authored `AcceptedThrowPairs` entries keyed on both messages verbatim — see §7's second trap. On those 92, an infidelity changes the rows and the counts **but not the pass bit**. |

---

## 6. The standing invariants, and which door each closes

Run by **both** acceptance commands (§0.1; decision **B3**). If you change the harness these are what
must still pass; if one starts failing, read it before you "fix" it.

| Invariant | The door it closes |
|---|---|
| `HistoricalOracleIsolationTest` (9 methods) | The harness comparing the port against **itself**. Pins both jar digests; a missing or altered jar fails loudly. |
| `UnwrittenPortInvariantTest.anUnwrittenPortAgreesWithNothing` — 7 subjects | **D1 / D2 / D-10.** A port that does not exist scoring agreement. Agreement rows == 0 for the non-observing subjects, and no operation is agreed on every driven row without a written review entry — asserted for both halves of the split (discriminating and single-valued) separately. All fourteen buckets are empty today and all fourteen are asserted: a live guard against future growth. |
| `UnwrittenPortInvariantTest.aNoLogicPortCannotProduceAStagePass` | **D-15.** A subject of one hardcoded literal per operation, over stage-shaped domains: all 119 clean-and-degenerate rows are refused, no operation reaches a stage pass, and a faithful port on a discriminating operation still passes — the control that stops the gate degenerating into blanket refusal. |
| `UnwrittenPortInvariantTest.aDegenerateOperationNeedsAWrittenSignOff` | The sign-off route in both directions and its exactness in both key positions: a rationale against a different value, or a different operation, does not match; a blank rationale is rejected. |
| `PortedInfidelityDetectionPowerTest.aWrongJavaTypeWithRightContentIsCountedNotScored` | **D-18, demoted round 8.** A perfect port that boxes every raw result must show **3 445** java-type mismatches across **182 of 285** operations and **0 `DIFFER`**, **0** stage passes lost, because at S1 that difference is not attributable to the port. **Never quote it without the method next door** — its Javadoc says so. |
| `PortedInfidelityDetectionPowerTest.aFactoryTypedAdapterCostsNoPassAndIsCountedNotScored` | **D-43, both readings of the same number, and the demotion.** Four subjects, one run: the control; the planted wrong-class port; a content-perfect port with a factory-typed adapter; the same port with an observing adapter. Asserts the factory-typed adapter's 3 445 rows land in `javaTypeMismatchCount()` on **exactly** the planted defect's operations — the identity that makes the figure a measurement, not an attribution — and that an independent recount matches the harness's own count. |
| `DifferentialHarnessRegressionTest.theTypeTokenIsObservedOrAssumedAndNoApiTakesOne` | **D-43 at unit resolution, pinning the shape of the API and not only the values.** `observedFrom` derives the token from `getClass().getName()`; `TypeProvenance` has **exactly** `OBSERVED` / `ASSUMED` / `NONE`; no public member of `UValue` accepts a class name, checked by reflection over the whole public surface. |
| `PortedInfidelityDetectionPowerTest.noOperationAnswersWithTwoRuntimeClasses` | **D-18's premise.** No operation answers with two different runtime classes today (0 of 285, measured against the reference alone) — a **corpus fact, not a language fact** (the earlier rationale was false, D-45; the corrected Javadoc says so). |
| `DifferentialHarnessRegressionTest.aReportCannotUnderstateItsOwnSignOffs` | **D-34.** The header carries the sign-off count and the rationale verbatim; no `write`/`writeAll` overload omitting the sign-off set may exist — checked reflectively. |
| `rightContentInTheWrongJavaTypeIsADifference` / `theTypeTokenIsPackageInsensitiveOnPurpose` | **D-18 at unit resolution.** Both shapes of "wrong type" — a `Kind` difference (always caught) and a runtime-class difference (the defect) — plus the note naming both FQNs, and the stated cost of comparing the simple name. |
| `PortedInfidelityDetectionPowerTest` (4 methods) | **Detection power itself.** A second, independently loaded `HistoricalOracle` plays a perfect port; a `MutantPort` applies one named infidelity. Asserts the control diverges nowhere, and pins the set of planted (defect, operation) pairs the instrument **cannot** see **as an exact set**, so that blindness cannot grow or shrink silently. |
| `DifferentialHarnessRegressionTest.distinctReferenceValuesCountsTheReferenceOverMeasuredRows` | The metric, against the two mistakes that would make it useless: counting the subject's column, and counting over all rows. (D-39, open, MINOR: the Javadoc's "169 distinct marker strings" claim is wrong — all 169 rows carry one identical string, so the pin holds at 1-vs-0.) |
| `DifferentialHarnessRegressionTest.theStageGateRefusesADegenerateOperation` | The gate's three clauses separately and together, a floor of zero rejected, and the sign-off opening it. |
| `DifferentialHarnessRegressionTest.theReportHeaderCarriesDiscriminatingPowerPerOperation` | That the number reaches the artefact a human reads, per operation. |
| `DifferentialHarnessRegressionTest.goldenComparisonIsBytesAndNotLines` | That `assertMatchesGolden` compares **bytes**: a trailing-newline difference and a CRLF substitution both fail. |
| `DifferentialHarnessRegressionTest` (26 methods) | Each closed door pinned individually: two throws are never agreement, `VOID` vs `VOID` is not agreement, one-sided absence is a difference and keeps its evidence, a zero-row sweep is refused, wrong throw class is visible in an aggregate, `AcceptedThrowPairs` is opt-in and exact, plus the note-content pins that stop evidence being destroyed. |
| `UncertaintyDifferentialSmokeTest` (6 methods) + `assertMatchesGolden` | Non-determinism and any unannounced output change. Goldens under `docs/port2/differential/` compare byte for byte; refreshed only with `-Duse.differential.golden.refresh=true`, deliberately, in a commit that says why. Gates through `requireStagePass` (D-36, closed) and **is the stage template**. |
| `everyKindIsEitherAnObservationOrUnmeasurable` | *Intended* to catch a new value-less `UValue.Kind` becoming a route to `AGREE`. **Do not rely on it: it is tautological (D-20, open).** Treat a new `Kind` as requiring manual review. |

---

## 7. Three traps specific to writing an S4 adapter

The full rationale and worked examples for each trap now live as `@implNote` Javadoc on the classes
each one is about — read there for the measurements behind the rule. This section states the rules.

1. **Do not reach for `HarnessMarshallingException` as a fallback** for "I am not sure this row is
   right". `HARNESS_ERROR` and `UNSUPPORTED` are excluded from the per-operation denominator
   `UnwrittenPortInvariantTest`'s no-logic-port check reads, so an adapter that declines to marshal
   wherever it would otherwise be wrong is scored fully agreed by that invariant (D-17). It buys **no
   stage pass** either — clause 2 refuses `HARNESS_ERROR`, and a subject that always throws it was
   measured to reach zero stage passes (D-32). What it destroys is **attribution**. See `Candidate`'s
   class Javadoc.
2. **Do not sign off `AcceptedThrowPairs` in bulk.** A perfect port needs on the order of 150 distinct
   entries before clause 2 could be met on the operations that throw (D-29). That pressure is exactly
   how the deleted blanket throw-agreement rule would come back. Each entry needs the operation, both
   classes, both messages and a written rationale, reviewed one at a time. See
   `DifferentialSweep.acceptedThrowPairs()`'s Javadoc.
3. **Attribute the Java class your port actually returned, at one seam, from the object the invocation
   returned — a SHAPE, not merely a call** (defect D-52). `UValue.observedFrom(Object)` reads
   `getClass().getName()` off whatever object it is handed, so an author who chooses the object has
   chosen the token: observing an empty stand-in class of the reference's name takes a real
   401-row/9-operation wrong-class defect to `javaTypeMismatchCount() == 0`, byte-identical to a
   defect-free port. The factories alone type a value `ASSUMED` as the `Value` class of its kind —
   wrong for 182 of 285 operations. See `UValue.observedFrom(Object)`'s Javadoc for the trap and
   `PortedCandidate.fromPorted(Object)` for the worked example that satisfies it.

### REQUIREMENT on S4 — dated obligation, 2026-08-17

> Once real ported value classes exist in `use-core/src/main` — which is what stage S4 writes — S4
> MUST (a) route its adapter's every result through `UValue.observedFrom(theObjectItsPortReturned)`
> **in the invocation-seam shape** (trap 3 above; `UValue.observedFrom(Object)`'s Javadoc has the
> mechanism), and (b) add `assertEquals(0, result.javaTypeMismatchCount(), result.summary())` as a
> gate clause beside `throwClassMismatchCount() == 0`.
> `PortedInfidelityDetectionPowerTest.observeWhatThePortReturned` and
> `PortedCandidate.fromPorted(Object)` are the executing examples already in the tree.
>
> **How a reviewer checks this was done.** The S4 document must quote `stageStatement()` verbatim —
> `N java-type mismatch(es) (subject token OBSERVED on a, ASSUMED on b)` — together with the report
> header's `# rows.javaTypeMismatch` / `# rows.subjectTypeObserved` / `# rows.subjectTypeAssumed`,
> quoted **per operation** (a file-level `0` can be an artefact of D-54). **"The adapter states its
> attribution route" is NOT SUFFICIENT**: an honest adapter and a laundering one both route through
> `observedFrom` and both would state that truthfully. **The check is on the shape** — exactly one
> place the port's result is obtained, the same reference passed to `observedFrom`, no stand-in class
> constructed anywhere. **A non-zero `rows.subjectTypeAssumed` at S4 or later is a defect in the
> stage's own adapter, not in the port.**
>
> Until that is done the demotion stands and must be stated as a limit wherever an agreement figure
> from this harness is quoted: **an `AGREE` row may be an agreement on the payload alone.**

---

## 8. THE S4 CHECKLIST — imperative, copy-pasteable

Work top to bottom. Nothing here is new: it is §0–§7 in the order you will need them.

**1. Write the adapter with one invocation seam.**

```java
// ONE place the port's result is obtained. Never reconstruct, re-box or look up the object again.
Object returned = portMethod.invoke(receiver, marshalledArgs);
if (portMethod.getReturnType() == void.class) { return UValue.voidValue(); }   // D-51
if (returned == null)                         { return UValue.nullValue(); }   // no class to observe
return marshalContent(returned).observedFrom(returned);                        // OBSERVED, same reference
```

* **Never hand-supply a token; never construct a stand-in, marker or placeholder class** anywhere in
  the adapter (§7, trap 3, D-52).
* **Do not reach for `HarnessMarshallingException` as a fallback** (§7, trap 1).
* **Do not sign off `AcceptedThrowPairs` in bulk** (§7, trap 2).

**2. Choose the measurement floor BEFORE the run, and write it in the stage document.**
Derive it from the domain arithmetic (`URealValue.add(value)`: 24 × 24 = 576 rows → floor 500), leave
headroom for `BOTH_THREW` / `HARNESS_ERROR` rows a faithful port cannot avoid, and never adjust it after
seeing a run. `0` is rejected outright. **A floor chosen after the run is not a floor.**

**3. Gate with one call, plus three assertions.**

```java
r.requireStagePass(FLOOR, AcceptedDegenerateOperations.none());  // clauses 1-3, throws with every failure
assertEquals(0, r.throwClassMismatchCount(), r.summary());       // clause 5
assertEquals(0, r.javaTypeMismatchCount(),  r.summary());        // clause 6 -- REQUIRED from S4, under §7's shape
DiffReportWriter.assertMatchesGolden(                            // clause 4: bytes, not lines
        DiffReportWriter.writeAll(name, List.of(r), digests, AcceptedDegenerateOperations.none()),
        Path.of("docs/port2/differential/" + name));
```

**Pass predicates that are NOT pass criteria — never assert any of these:**
`disagreements().isEmpty()` (vacuous on a sweep that compared nothing) · `isClean()` (true for 119 of 285
operations against one hardcoded literal each, and true on a sweep carrying java-type mismatches) ·
`requireMeasurements(int)` alone (a floor, not a gate) · `distinctReferenceValues() >= 2` read as
*sufficient* (`BooleanValue.value()` sits at exactly 2).

**4. Do not automate on the boolean (D-29).** A perfect port passes only **74 of 285**: 119 refused by
clause 3 by design, **92 by clause 2** for rows fidelity cannot avoid. Record `stageGateFailures(...)` for a
perfect-port baseline and **diff the clause list** — on those 92 the pass bit does not move when a real
infidelity is planted.

**5. Record, per operation, five things — four from the harness and one only you can write.**

| # | Figure | Source |
|---|---|---|
| 1 | measured rows (not row count) | `measurementCount()` |
| 2 | distinct reference values, and the `[DISCRIMINATING]` verdict | `distinctReferenceValues()` |
| 3 | **java-type mismatch count**, per operation | `# op.<key>.javaTypeMismatch` |
| 3a | **its provenance split** (H21), per operation — which half of the mismatch total is a statement about the port and which about your own adapter | `# op.<key>.subjectTypeObserved` / `# op.<key>.subjectTypeAssumed` |
| 4 | the whole line, quoted verbatim | `stageStatement(acknowledged)` |
| 5 | **the input domain — MEASURED, per decision H14, and in prose alongside it** | the coverage measure of [`h14-coverage-design.md`](h14-coverage-design.md); prose from **you** (D-30) |

Until the H14 measure exists, state the domain in prose **and say the measure is not built yet** — do
not present prose as satisfying H14, and do not treat this row as closed by a sentence. Quote per
operation, never per file (`# rows.*` and `# verdict.*` are sums — D-21; and `# op.<key>.*` keys
are not unique if one report holds several results for one operation — D-41). "576 agreed" is not a
fidelity claim. "576 agreed over 24 uReal boundary receivers × 24 arguments, no value in (2,100) other
than the two random draws, 164 distinct reference values, 0 java-type mismatches (0 observed, 0
assumed)" is.

**6. State the two sentences that stop a reader over-reading the figures.**
*"An `AGREE` row may be an agreement on the payload alone; this operation's java-type mismatch count is N,
of which `subjectTypeObserved` a and `subjectTypeAssumed` b."* and *"The adapter observes the object each
invocation returned, at one seam."* Until the second is true, the type figure means nothing about the port
(§7) — and **`b > 0` is the harness telling you the second sentence is false**, whatever the adapter's
prose claims.

**7. When a sweep refuses, do this in this order.** `requireStagePass` throws with **every** failing clause
and its numbers — read all of them, not the first.

1. **Clause 1 (floor).** Compare `rowCount()` with `measurementCount()`. A large gap is the finding: read
   the notes and classify the undriven rows (`HARNESS_ERROR` = the harness, `UNSUPPORTED` = a side declined,
   `UNMEASURABLE` = nothing to observe). **Do not lower the floor.** Widen the domain, or record the gap.
2. **Clause 2 (a row disagreed).** Distinguish `DIFFER` (a real content divergence — fix the port) from
   `BOTH_THREW` / `MIXED` / `HARNESS_ERROR` / `UNMEASURABLE` (fidelity may be intact; this is D-29). For
   `BOTH_THREW`, an `AcceptedThrowPairs` entry is the only route and costs operation + both classes + both
   messages + a written rationale.
3. **Clause 3 (not discriminating).** The operation could not have failed. Sign it off only if it is
   constant *by specification*, with a value-keyed rationale saying what a reader must **not** conclude —
   the key is type-bearing, so the sign-off lapses by itself if the answer's class ever changes.
4. **Never** replace the gate with `isClean()` because it refuses. That is the 119-operation gap.

**8. The dated obligation, 2026-08-17 (§7).** Once real ported value classes exist in `use-core/src/main`,
`javaTypeMismatchCount() == 0` is a **gate clause**, not a published figure — valid only under the
invocation-seam shape of step 1, quoted per operation, and checked by a reviewer reading the adapter's
shape rather than its prose.

**8a. Accept the stage by running THE GATE, and quote it (§0.1, decision B3).**

```bash
scripts/upstream-oracle-gate.sh     # runs both commands; hand-typing -P is NOT the gate
```

What it runs, and the figures each side must reach:

```bash
mvn -q clean && mvn -B verify -Djava.awt.headless=true                     # 11 classes / 211 methods (199 asserting)
mvn -q clean && mvn -B verify -Pupstream-oracle -Djava.awt.headless=true   # 51 classes / 498 methods (465 asserting)
```

Quote, for each: `BUILD SUCCESS`, the deduplicated class and method counts, and
failures/errors/skipped. The counts are **asserted by the build** — the `[floor]` lines in the log are
the evidence, and a run below floor fails instead of printing a green summary over a shrunken suite.
A stage document quoting only the first command **has not stated its acceptance**. Never quote
surefire's headline as a method count under the profile (1086 executions ≠ 498 methods), and quote the
**asserting** figure (199 / 465, §0.1) whenever the number is used to argue scrutiny rather than collection. If an upstream
test fails, that is a finding for this document plus, if licensed, a waiver in
`upstream-test-waivers.md` — **never** an edit to the test.

**8b. If you are fixing a historical defect, you are executing B7, so follow its plan.** The decision
is **FIX and document each row**; ~~bug-for-bug~~ was recommended and not taken. The per-row list — the
fix, the owning stage, and the observable class of the change — is
[`b7-fix-plan.md`](b7-fix-plan.md), which supersedes every `DEFER` in
`spec-parts/16-modernization-ledger.md`. A fix that lands without its row's written justification and
its print-output delta is not B7-compliant, and a deliberate deviation from the reference stays visible
only through `AcceptedDegenerateOperations` / `AcceptedThrowPairs` / `IntendedDepartures`.

**9. Before you quote anything, name what the harness could not see for this stage:** void operations
(post-state is unmeasurable), `SBooleanValue`, collection receivers, the type layer, the **33 non-nameable
operations** (`equals(Object)` first — a broken `equals` is invisible here), and every operation the corpora
left single-valued.

---

## 9. The question to ask when you extend this

Every round the harness was fixed to stop making one false claim, and the next reviewer found a
different construction producing the same false claim: harness failure as agreement, two throws as
agreement, two `VOID`s as agreement, a single-valued codomain as fidelity, an unswept domain read as
coverage, and finally the same content in a **different Java class** as agreement — on a port whose
entire subject was four new value classes, the harness comparing the payload and calling it the value.
The fix for that produced a number a *faithful* port reproduces exactly, because half of what it
compared was **declared by the thing under test** instead of observed by the instrument (D-43); the
fix for that in turn was refuted because the escape hatch had only moved from a `String` parameter to
an `Object` parameter, and an author who chooses the object has chosen the token (D-52) — so the
remedy was never a better parameter type, but a mandated **shape** that makes the object non-choosable.

The pattern across all of them: a rule that reads a property the thing under test can influence is not
measuring the thing under test. So when you extend this, ask the question of the fix as well as of the
row:

> **What does a perfect port measure under this rule, and is that different from what a defective one
> measures? Can the thing under test influence what this rule measures?**

If the thing under test can influence what a rule measures, the rule measures the wrong side, and **no
amount of mandated disclosure repairs it** — a disclosure fires only where the instrument already
noticed, and laundering is exactly the case where it did not. If either answer is bad, say so in a
test next to the one that quotes the number.

So the question is not "is this row correct?" — every row can be correct and the rule still wrong. It
is:

> **Could this sweep have failed? What would have had to be different for it to report a
> divergence — in the reference's answers, and in the inputs we chose?**

The first half is a number the harness computes and the gate acts on. **The second half is yours.**
`DISCRIMINATING_MINIMUM = 2` is a threshold, and a threshold is a place to stand, not a proof: an
operation whose range is exactly `{true, false}` clears it and is still nearly free for a subject
echoing one bit (`BooleanValue.value()` and `BooleanValue.isTrue()` sit at exactly 2 today). Look at
the number; do not just compare it to 2.
