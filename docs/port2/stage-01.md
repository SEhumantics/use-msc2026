# S1 — The differential harness

Branch `port-uncertainty-2`. Java 21.0.11, Maven 3.9.16, `~/msc-4/use-msc2026`.

S1 precedes any port code. Its only deliverable is the measuring instrument: a test-scoped harness
that drives the historical `URealValue` / `UIntegerValue` / `UBooleanValue` / `UStringValue` out of
the 2018 jars inside an isolated class loader, so historical and ported classes with the same
fully-qualified name can coexist in one JVM. Nothing under `use-core/src/main`, `use-gui` or
`use-assembly` was touched by any commit described in this file; `module-info.java` was never edited.

> **This file's authority is §10** — "THE CONSOLIDATED RECORD", eight rounds of build-then-audit
> compressed to one verdict, one canonical defect register (§10.4) and one set of normative rules for
> what S4–S7 may say (§10.6). §1–§9 below are what was built and the historical audit narrative that
> §10 supersedes; §11 (compressed to one still-cited design point at the end of this file) and the old
> §12 round-4 snapshot (fully superseded, removed — see git history) were appendices.
> **The verdict is `SOUND_WITH_DOCUMENTED_LIMITS`.** The short answer for a human deciding
> whether S3 may start is [`foundation-verdict.md`](foundation-verdict.md); the rules a stage follows
> when it gates on a sweep are in [`harness-contract.md`](harness-contract.md).

---

## 1. What was built

Under `use-core/src/test/java/org/tzi/use/uncertainty/differential/`: `IsolatedJarClassLoader` (the
parent-last loader, §3), `HistoricalOracle` (owns the loader, hash-verifies the jars, invokes
historical operations by name), `Candidate` (the pluggable side interface S4–S7 drop the port into),
`StubCandidate` (S1 placeholder — **not the port**), `UValue`/`UOp`/`InputGenerator`/
`DifferentialSweep`/`DiffRow`/`DiffVerdict`/`DiffReportWriter`, and two JUnit 5 test classes
(`HistoricalOracleIsolationTest`, `UncertaintyDifferentialSmokeTest`). This reactor has no
`junit-vintage-engine`, so a JUnit 3/4 test would compile and silently never run — the condition that
makes most of the pre-existing `*Test.java` files dormant (`stage-00-baseline.md` §3).

## 2. Jar-location decision

**Both jars are committed at `use-core/src/test/resources/historical/`** (`use.jar` 1 440 303 B,
`atenearesearchgroup.uncertainty.jar` 77 674 B), not read from `.git/reference-repositories/` (untracked,
so a test reading from there would pass on one machine and fail everywhere else). Digests, verified
before and after the copy and matching the task-brief figures:

```
sha256  use.jar                              80ac8ae433b8345677472019991356950f094f4a104cfbce1f75783a7308788d
sha256  atenearesearchgroup.uncertainty.jar  53b2a43feb0a0a39844a60278dd80a7d4b975ef324fb05c6db28831e835e59d0
```

A jar under `src/test/resources` is copied to `target/test-classes/historical/` as an opaque data file,
never added to the test classpath as a jar — its `org.tzi.use.uml.ocl.value.*` entries are invisible to
the application loader and reachable only through the harness's own loader. **`HistoricalOracle`
re-verifies both sha256s on every `open()`**, so the committed copies cannot silently drift;
`-Duse.historical.jars.dir=…` overrides the location for out-of-tree runs and, when set, is
authoritative (no fallback), so a mis-pointed override fails loudly rather than doing the wrong thing
quietly.

## 3. The load-bearing finding — a platform-parented `URLClassLoader` is NOT isolated here

The task brief specified `ClassLoader.getPlatformClassLoader()` as the loader's parent, on the grounds
that it holds no application classes. **In this reactor that is false**, and the harness's own
built-in guard caught it on the first run: `use-core/src/main/java/module-info.java` exists, so Maven
compiles and runs `use-core` on the **module path**, and `jdk.internal.loader.BuiltinClassLoader`
consults a package-to-module map covering every boot-layer module *before* falling back to its parent —
so the platform loader resolves `org.tzi.use.uml.ocl.value.Value` straight back to the **application**
class loader, reintroducing exactly the self-comparison isolation was meant to prevent. Not
hypothetical: it is the difference between a harness that works and one that reports green while
comparing the port against itself. Measured, the alternative remedy (platform-parented `URLClassLoader`)
does not work under JPMS in this reactor.

**Fix:** `IsolatedJarClassLoader extends URLClassLoader`, overriding `loadClass` to be **parent-last**
for `org.tzi.use.` and `uDataTypes.` — those names resolve from the jars with no fallback to the
parent; everything else (`java.*`, all JDK modules) still goes to the platform loader. No
`module-info.java` edit, no `--add-exports`/`--add-opens`/`--patch-module`; the whole fix is inside the
test-scoped loader.

**Permanent regression test.** `HistoricalOracleIsolationTest` runs three loaders side by side against
`RealValue` — a class that exists **today** in both `use-core/src/main/java` and `use.jar`, a genuine
same-FQN collision available before any port lands: a bare `URLClassLoader` (system or platform parent)
resolves it to the **application's** class (self-comparison); `IsolatedJarClassLoader` resolves it to
the **jar's**. `HistoricalOracle.open()` asserts the same invariant at runtime, not only in the test.

## 4. Seed and boundary coverage

`seed = 20260817` (`InputGenerator.DEFAULT_SEED`, fed to `new java.util.Random(seed)`; no
`Math.random()`, no time-based seed anywhere). Fixed boundary corpus first, then random draws, so row
order is stable — verified byte-identical across runs. Boundary corpora cover: confidence/probability/
uncertainty at exactly 0 and 1; negative values, zero, negative zero; zero divisors (`UInteger`,
`UReal`, `Integer`, `Real`, `-0.0`); empty string; out-of-range index
(`MIN_VALUE,-1,0,1,2,3,4,MAX_VALUE`); NaN/±infinity in both the value and the uncertainty position.
Measured while building the corpus: **`UStringValue.at(int)` is 1-based** — `at(0)` throws — so index 0
is a boundary case, asserted in `thrownOutcomesAreRecorded()`.

## 5. Acceptance (S1's own commits)

Full run: `mvn -q clean && mvn -B verify -Djava.awt.headless=true` → `BUILD SUCCESS`, deterministic
across repeated runs (byte-identical reports, checked by hash), `git diff --name-status -- '*/src/main/*'`
empty on every commit described in this file. Committed goldens:
`docs/port2/differential/s1-smoke-ureal-add.tsv`, `s1-smoke-ureal-minus-faulty.tsv` — the second exists
specifically to prove the harness *detects* a known injected fault (`minus` combining uncertainties as
`|ua−ub|` instead of `sqrt(ua²+ub²)`): 226 of 784 rows `DIFFER`. Per-round test counts and golden-header
deltas are reconstructable from `git log` on `use-core/src/test/java/.../differential/` and on
`docs/port2/differential/*.tsv`; they are not repeated here because §10.4's register is what any other
document should cite.

## 6. Residual risk (as recorded when S1 was built; superseded where §10 disagrees)

1. **The stub is not the port.** `StubCandidate` is a 40-line re-derivation of two formulas, documented
   in-file as not-the-port, and — per D-43 (§10.4) — an active reproduction of a false-divergence mode
   for anyone who copies it uncritically. Delete or leave unused once S4 lands.
2. **Coverage is bounded.** `SBooleanValue` (39 operations), collection receivers, `org.tzi.use.uml.ocl.type.*`
   and `uDataTypes.*` are unreachable by design (D-9); voids, and 33 operations with no `UOp` name at all
   (`equals`/`compareTo` among them), are named limits, not gaps.
3. **Exact comparison may prove too strict.** `UValue.canonical()` compares via `Double.toString`, so
   `0.0`/`-0.0` is a `DIFFER` and `NaN` equals `NaN`. Right default for regression detection; loosening
   it must be an explicit, recorded decision, never a quiet epsilon.
4. **One unexplained transient.** The very first full-reactor run after the S1 change failed with
   `TestEngine with ID 'junit-jupiter' failed to discover tests`, `Tests run: 0`. Never recurred in 7
   subsequent full-reactor runs; the `target/` evidence was destroyed by the next `clean` before it
   could be examined. Neither attributable to the change nor ruled out — recorded rather than omitted.
5. **Locale.** Canonical forms use `Double.toString` and hand-rolled hex, both locale-independent; the
   harness deliberately never calls `Locale.setDefault` (a global side effect on every other test).
6. **The jars are Java 7 bytecode (major 51).** Java 21 loads them today; a future JDK dropping that
   class-file version takes the oracle with it — a reason to finish the port, not to depend on the
   oracle indefinitely.

## 7–9. Eight rounds of audit-then-fix, before consolidation — pointer

§7–§9 of the original record narrate, in full round-by-round detail with pasted `mvn` output, three
successive "absence of a measurement scored as agreement" defects and their fixes:

- **D1** — the harness's own marshalling failures scored `AGREE_THROWN` (fixed: `HARNESS_ERROR`, a
  distinct non-agreement, commit `cf9d2f45`, eleven fixes F1–F11).
- **D2** — two throws with matching class names scored `AGREE_THROWN` with both messages discarded, so
  a subject whose every body threw a bare `RuntimeException` (what the historical code raises for type
  errors) went 35% green (fixed: throw-agreement **deleted**, not tightened — `BOTH_THREW` is always a
  non-agreement and its note keeps both classes and messages — commit `e8b73e48`).
- **D-10** — `VOID` vs `VOID` scored `AGREE` (444 rows, every driven row of every void operation the
  harness could reach — fixed: `UNMEASURABLE`, raised only when *neither* side observes a value; `Result`
  gained a measurement floor distinct from a row count — commit `93e038ac`).

All three are closed and pinned by executing regression tests; their full figures and provenance are
carried forward into §10.2 and the D1/D2/D-10 rows of §10.4's register. The narrative itself — the
`mvn` transcripts, the specific test-class deltas per round — is git history on this branch and is not
reproduced here.

---

# 10. THE CONSOLIDATED RECORD — eight rounds, one verdict, one register

> **This section is the authority for S1.** It supersedes §1–§9 above wherever they disagree. The
> round-4 snapshot and the D-15 fix narrative that once stood as separate appendices (§11–§12) have
> been removed from this file; their content is git history, and every fact from them still cited
> elsewhere (`stage-03-scope.md`'s F4 pointer, `h14-coverage-design.md`'s "a mechanism, not a
> convention" reference to the stage-gate's mandatory-floor design) is preserved in §10.4 and §10.6
> below.

**Sources.** Every figure below is either measured in one of the round reports (`stage-00-baseline.md`
for round 0; `stage-01-verification-round5.md` through `-round8.md` for rounds 5–8, git history for
rounds 1–4) or cited to `file:line` in the tree.

## 10.1 CURRENT VERDICT — `SOUND_WITH_DOCUMENTED_LIMITS`

Confirmed independently by three reviewers (rounds 5, 7, 8). Rounds 6 and 7 both patched an instance of
the same defect and were both refuted on the next instance; round 8 removed the *class* of defect
instead of patching a third instance, and round 8's own independent refuter confirmed the demotion and
found one residual issue, D-52, which is inert at S1.

**Can S4–S7 rely on this harness to detect a real infidelity in a ported U-type?**

**Yes — for a defect the corpora reach, on an operation the harness can name, provided the stage quotes
numbers rather than a boolean.** Round 5 planted eleven infidelities on a perfect port (a second,
independently loaded `HistoricalOracle`) and round 6 added a twelfth; nine of the eleven content probes
diverged, eight of those on every operation they touched (§10.3's P0–P11 table). The twelfth — a
wrong-Java-class probe — did **not** survive scrutiny as a *divergence*: it measured what the *adapter
declared*, not what the port returned (D-43), and after two further rounds it is now measured and
published in its own dimension (`javaTypeMismatchCount()`) rather than scored as `DIFFER`.

**No — outside the region the corpora reach, and that boundary is not published anywhere in the
instrument.** A planted defect confined to receiver value `42.0` (no corpus contains it) is
stage-pass-identical to a perfect port on all 19 083 rows (D-30). Separately, 33 public operations —
`equals(Object)` and `compareTo(Object)` on all eight receivers among them — cannot be named as a `UOp`
at all and appear in no report, not even as `UNSUPPORTED`.

**What that means for a reader of an S4–S7 number, in one line each:**

* A `DIFFER`/`MIXED`/`BOTH_THREW`/`throwClassMismatch` count is **trustworthy**, and always was.
* A **`javaTypeMismatch` count is a measurement, not an attribution** (D-43). Non-zero means the two
  sides named different classes for identical content; whether that is the port's fault or the
  adapter's is in the row note's provenance clause and nowhere else — an adapter that never observes
  produces the same **3 445** whether its port is perfect or carries a real wrong-class infidelity.
  From S4 it becomes a gate clause (`harness-contract.md` §7, dated requirement).
* **An `AGREE` row may be an agreement on the payload alone** — `javaTypeMismatch` is the only figure
  that says so, and `stageStatement()` prints it unconditionally.
* An `AGREE` count is trustworthy **exactly to the extent `distinctReferenceValues() >= 2`** for that
  operation — computed, published per operation, enforced by the gate.
* A **stage pass is not a fidelity certificate.** It certifies "no divergence over the inputs we tried",
  and it is not even satisfiable by fidelity on 92 of 285 operations (D-29).
* No aggregate over the whole file is a claim about any one operation (D-21).

## 10.2 The story in order

| Round | Verdict | What was found / done |
|---|---|---|
| 0 | sound (believed) | Harness built; the load-bearing isolation finding (§3). |
| 1 | **DEFECTIVE — D1** | Harness's own marshalling failures scored `AGREE_THROWN`. Fixed `cf9d2f45`. |
| 2 | **DEFECTIVE — D2** | Two throws, matching class, messages discarded, scored agreement (35% green on an empty port). Fixed by deletion, `e8b73e48`. |
| 3 | **DEFECTIVE — D-10** | `VOID` vs `VOID` scored `AGREE` (444/444 on every void op). Fixed `93e038ac`. |
| 4 | **DEFECTIVE — D-15** | Real, equal values over a one-point codomain scored as fidelity (120–159 of 285 ops). No scorer bug — first door every prior safeguard was *right* to let through. Fixed `0a93ad4f`: `distinctReferenceValues()` computed, published, gated. |
| 5 | **`SOUND_WITH_DOCUMENTED_LIMITS`** | First direct detection-power measurement (§10.3); no new scoring defect. Gate not satisfiable by fidelity (D-29), detection bounded by an unmeasured domain (D-30), three MAJORs found in the *record*, not the instrument (D-33/34/35). |
| 6 | four defects closed, one opened | D-18 closed (right content, wrong Java class was `AGREE` on 193/285 ops — the harness compared the payload and called it the value); D-34/35/36 closed. Refutation (6R) found **D-43**: the fix's own false-divergence mode reproduces D-18's exact signature via a documented, factory-typed adapter. |
| 7 | D-43 half (a) closed, escalated | `observedFrom(Object)` added, one-argument declaring route deleted, a written-reason two-argument form required. Refutation (7R) found the reason reaches **0 rows** on the sweep it was meant to protect (D-46/47/48/49). |
| 8 | **check DEMOTED, not patched a 3rd time** | Root cause: at S1 there is no ported implementation to observe. `declaredJavaType` deleted entirely; a type-only difference is `AGREE`, counted in `javaTypeMismatchCount()`, not scored. Refutation (8R) confirmed the demotion swallows no content difference, and found **D-52**: the escape hatch moved from a `String` to an `Object` parameter. Closed for S1 by mandating the adapter's *shape* in `harness-contract.md` §7–§8; the mechanism is S4's to build. |

**The shape, across rounds.** Rounds 1–3: an absence of measurement scored as agreement. Round 4: a
degenerate codomain, no scorer bug at all. Round 5: the softest target had become the *documents*, not
the scorer. Round 6: a presence compared incompletely — two real values, correctly equal in payload,
carried by different Java classes, one of which was wrong. Rounds 7R/8/8R: a disclosure mechanism that
fires only where the instrument already noticed — closed by removing the choice from the adapter's
hands rather than by adding a fourth disclosure API.

## 10.3 Detection power — measured, not in doubt

Control: two independently loaded `HistoricalOracle` instances, 285 operations, 19 083 stage-shaped
rows — 17 199 measured, 17 199 agreed, `{AGREE=17199, BOTH_THREW=910, HARNESS_ERROR=883,
UNMEASURABLE=91}`, **0 `DIFFER`, 0 `MIXED`, 74 of 285 stage passes**. Every row below is therefore
attributable to the planted defect alone (round 5, `PortedInfidelityDetectionPowerTest`; every figure
re-measured unchanged in rounds 6–8).

| probe | planted infidelity | detected on | rows | passes lost |
|---|---|---|---|---|
| P0 / control | perfect port | 0 | 0 | 0 |
| P1 | 0-based port of the 1-based string index | 3/3 ops | 138 | 0 (D-29) |
| P2 | uncertainties combined `+` not `sqrt(ua²+ub²)` | 4/4 | 468 | 4 |
| P3 | `Math.hypot` for `sqrt(ua*ua+ub*ub)` — 1 ULP | 4/4 | 24 | 4 |
| P4 | `<=`/`>=` for `<`/`>` | 6/6 | 280 | 4 |
| P5 | results rounded to 10 dp | 7/7 | 428 | 7 |
| P6 | `uEquals` ignores the uncertainty component | 4/4 | 1119 | 2 |
| P7 | divide-by-zero returns `Undefined` not a throw | 6/6 | 167 | 3 |
| P8 | P2 hidden behind `HarnessMarshallingException` (D-17) | 0 | 0 | 4 |
| P9 | P2 hidden behind `supports()==false` (D-17) | 0 | 0 | 4 |
| P10 | P2 only at receiver `42.0` (D-30) | **0** | **0** | **0** |
| P11 | `-0.0` collapsed to `0.0` | 3/4 | 59 | 3 |
| P12 | every result boxed to its `Value` class — right content, wrong Java class | since round 8: **0 DIFFER**, `javaTypeMismatch` 3445 across 182/285 ops, **74** passes (was 45 before round 8; see D-43) |

Blind spot, unchanged across every round: `{P11-negative-zero-collapse / URealValue.round()}` — a
planted defect that still passes its stage gate. P8/P9 are the two concealment attacks: both destroy
the divergence and buy no stage pass (D-32 narrows D-17: they cost attribution, not the verdict). **P12
must not be quoted without D-43/§10.4**: the same 3445/182/45 signature is produced by a genuinely
wrong-class port *and* by a content-perfect port whose adapter never observes what it returned; since
round 8 neither reading is a divergence, both measure `DIFFER 0` / 74 passes / `javaTypeMismatch 3445`,
and the two are told apart only by the row note's provenance clause.

Also unchanged and re-confirmed each round: isolation (two independent oracles reproduce each other on
all 19 083 rows); the corpus-widening result (D-19: zero-measurement operations 61 → 11); determinism
and scope (`git diff --name-status … -- '*/src/main/*'` empty on every round's acceptance run).

## 10.4 The open-defect register — single and canonical

A bare `D-nn` anywhere in `docs/port2/` means **this table**. Round-local ids (e.g. *round5 `R5-2`*,
*round6 `D-37`*) were re-keyed here at introduction to avoid collision; the mapping itself is git
history (it lived in a now-removed §10.5) and is not needed to read this table, since every citing
document already uses the canonical key below.

| Key | Sev | State | Defect |
|---|---|---|---|
| D1 | CRITICAL | CLOSED | Harness's own marshalling failure scored `AGREE_THROWN`. Fixed by `HARNESS_ERROR`, a distinct non-agreement (`cf9d2f45`). |
| D2 | CRITICAL | CLOSED | Two throws, matching class name, scored agreement with both messages discarded (35% green on an empty-bodied port). Fixed by deleting throw-agreement outright; `BOTH_THREW` is always non-agreement (`e8b73e48`). |
| D-9 | — | declared boundary | `SBooleanValue` (39 ops), collection receivers, `org.tzi.use.uml.ocl.type.*`, `uDataTypes.*` out of reach by design; the 33 non-nameable operations (`equals`/`compareTo` first) are the same species. |
| D-10 | CRITICAL | CLOSED | `VOID` vs `VOID` scored `AGREE` — 444 rows, every driven row of every void op. Fixed by `UNMEASURABLE` (`93e038ac`). |
| D-14 | MINOR | open (for `AcceptedThrowPairs` only) | No `# accepted.*` provenance header for throw-pairs; `describe()` called from nowhere. |
| D-15 | CRITICAL | CLOSED | Degenerate codomain scored as fidelity — up to 159/285 operations single-valued over the shipped corpora; `distinctReferenceValues()` computed, published per operation, gated (`0a93ad4f`). |
| D-16 | MAJOR | CLOSED for the stage gate | `isClean()` has no coverage floor and is unchanged deliberately (Javadoc now says so); `isStagePass(int,…)` takes a mandatory floor and rejects zero. |
| D-17 | MAJOR | open, narrowed by D-32 | A subject can shrink its own `driven` denominator by raising `HarnessMarshallingException` or a false `supports()`. P8/P9 in §10.3 measure the cost. |
| D-18 | MAJOR | CLOSED (round 6) | Primitive/boxed canonical collision, 193/285 by declared return type (182/285 driven). Before: 0 `DIFFER`/74 passes. After `javaType()` attribution: 3445 `DIFFER`/182 ops/45 passes. Perfect-port control unaffected. Superseded in framing by D-43. |
| D-19 | MINOR | CLOSED | Zero-measurement operations 61 → 11 after corpus widening; the 11 are declared limits (8 void mutators + 3 always-throwing ops). |
| D-20 | MAJOR | open | `everyKindIsEitherAnObservationOrUnmeasurable` is tautological — branches on the same predicate it asserts. Non-circular fix (a value-carrying kind has ≥2 distinguishable inhabitants) still unwritten. |
| D-21 | MAJOR | closed for the header, open for the guard | `# op.<key>.*` per-operation header block added, so no number is unattributable; `writeAll`'s measurement guard is still file-level. |
| D-22 | MINOR | open, latent until S4 | `UNSUPPORTED` note asserts "could be driven" per row from a per-operation `supports()`. |
| D-23 | MINOR | open, latent | `unmeasurableNote` derives void-ness from a disjunction; correct predicate is `ref.value.kind()==VOID` alone. |
| D-24 | MINOR | annotated in place | §5.2's original pasted header/line-counts were superseded by a later golden refresh; noted where it stood. |
| D-25 | MINOR | open | `AcceptedThrowPairs.java` comment claims "plain ASCII"; file holds non-ASCII bytes. Harmless, but a documentation-integrity defect in evidence-producing code. |
| D-26 | MINOR | CLOSED and pinned | `assertMatchesGolden` compares bytes, not lines (`93e038ac`), pinned by a dedicated test. |
| D-27 | MINOR | CLOSED | The `MIXED` note now names which side threw, both directions. |
| D-28 | MINOR | open | The corpora contain exactly one `RealValue` (`REAL(0.0)`), so all 23 `RealValue.*` operations are single-valued by arithmetic alone — "N single-valued" is a joint fact about the code and the corpus. |
| D-29 | MAJOR | open | **Stage gate not satisfiable by fidelity.** A perfect port reaches `isStagePass` on **74 of 285** operations: 119 refused by clause 3 (D-15, by design), **92 refused by clause 2** for rows a faithful port cannot avoid (`BOTH_THREW`/`HARNESS_ERROR`/`UNSUPPORTED`/`UNMEASURABLE` all count as disagreement); the only route out is **154** hand-authored `AcceptedThrowPairs` entries keyed on both messages verbatim. On those 92 ops an infidelity leaves the pass bit unchanged. |
| D-30 | MAJOR | open | **Detection is bounded by the input corpus; no domain-coverage figure is computed, published or gated.** A real arithmetic defect confined to receiver `42.0` is stage-pass-identical to a perfect port on all 19 083 rows (P10, §10.3). Not a scorer bug — the boundary of what a fidelity claim from this instrument means, and the instrument does not state it. Design for closing it: `h14-coverage-design.md` (unimplemented). |
| D-31 | MINOR | open | `indexBoundaries()` mostly out of range for two-index extraction: `UStringValue.uSubstring(int,int)` is 17 measured rows of 432. Corpus fact, same species as D-19/D-28. |
| D-32 | MINOR | open, narrows D-17 | Against the **stage gate** specifically, hiding a defect behind `HarnessMarshallingException` costs zero stage passes on the affected operations (clause 2 refuses `HARNESS_ERROR` too) — the attack destroys attribution, not the verdict. |
| D-33 | MAJOR | CLOSED | A round-5 document pasted one test's output as another's evidence; corrected in place with a box naming the actual source, not silently swapped. |
| D-34 | MAJOR | CLOSED (round 6) | A 3-argument `writeAll` overload silently substituted `none()`, so a report could assert `# accepted.degenerateOperations 0` while a sign-off was in force. The eliding overloads are deleted; no default exists any more. |
| D-35 | MAJOR | CLOSED (round 6) | The standing invariant had been quietly weakened to assert only half of what it asserted the commit before. Restored as a second assertion; all 14 buckets (7 subjects × 2 halves) now checked. |
| D-36 | MAJOR | CLOSED for the acceptance test | S1's own smoke test asserted `isClean()`, which the contract forbids as a pass predicate. Now gates through `requireStagePass`. Gate remains opt-in by design elsewhere. |
| D-37 | MINOR | open | Clause 3's refusal message conflates "nothing was measured" with "this operation could not have failed" when `distinctReferenceValues()==0`; the sign-off it prescribes can never match. |
| D-38 | MINOR | open | A tautological assertion inside the D-15 test (`assertEquals` of two counters incremented in the same `if` body). The real check (`assertThrows`) does the work; the equality is not independent evidence. |
| D-39 | MINOR | open | The "169 distinct marker strings" rationale is false — all 169 rows carried one identical class-name string. The pin still holds, at 1-vs-0, not 169-vs-0. |
| D-40 | MINOR | open | `fullyAgreedOperations()` tests `agreed == driven` rather than `agreed >= driven`; a regression making `HARNESS_ERROR`/`UNSUPPORTED` count as agreement would silently drop the operation from both buckets. |
| D-41 | MINOR | open, latent | `# op.<key>.*` header keys are not unique — emitted per `Result`, not per operation, so several results for one operation collide with no aggregation. Directly in the path of an S4 stage sweeping one operation over several corpora into one report. |
| D-42 | MINOR | open | `booleanCorpus` appends random draws to an already-exhaustive two-element domain; the printed census overstates the domain (`boolean=4` for a two-inhabitant type). Harmless. |
| D-43 | MAJOR | **CLOSED BY DEMOTION (round 8, `066fe15c`)**, promotion to a gate clause is a dated REQUIREMENT on S4 (`harness-contract.md` §7) | **At S1 the ported side's Java type cannot be authentically observed — no ported implementation exists to observe.** `HistoricalOracle.fromHistorical` derived the reference's class from the object it returned; the ported side's token was whatever an adapter *declared*. **Half (a), false divergence, closed round 7:** `UValue.observedFrom(Object)` reads the class off the object a side actually returned, used on all 14 unwrapping branches; a content-perfect port with the documented factory-typed adapter had measured `DIFFER 3445 / 182 ops / 45 passes (29 lost)` — byte-identical to D-18's signature — and with an observing adapter now measures `DIFFER 0 / 74 passes`, matching the control. **Half (b), the declaring escape hatch, closed round 8 by deletion after round 7's fix was refuted:** round 7's `declaredJavaType(String,String)` required a written reason, but `declaredJavaType(referenceToken,"x")` on a genuinely wrong-class port produced a sweep byte-identical to the perfect-port control with the reason in **0 rows** (folded in as D-46, CRITICAL, closed by removing the API). **Round 8's fix:** `declaredJavaType`/`TypeProvenance.DECLARED` deleted outright; a token is `OBSERVED` (off the returned object) or `ASSUMED` (factory default) — an adapter author chooses neither. A **type-only** difference is now `AGREE`, counted (not scored) in `Result.javaTypeMismatchCount()` / `# rows.javaTypeMismatch` / `# op.<key>.javaTypeMismatch`, printed unconditionally by `stageStatement()`. **Content differences are untouched — every content probe (§10.3) is unchanged.** Measured after: control unchanged (`DIFFER 0`, 74 passes, `javaTypeMismatch 0`); factory-typed adapter on a content-perfect port now `DIFFER 0 / 74 passes / javaTypeMismatch 3445` (the false-divergence mode and its 29 lost passes are gone); a genuinely wrong-class port `DIFFER 0 / 74 passes / javaTypeMismatch 3445`, indistinguishable from the above **except by the row note's provenance clause** — that residual is the named limit `harness-contract.md` §7 dates a REQUIREMENT against (S4 must route through `observedFrom` and gate on `javaTypeMismatchCount()==0`). Round 8's own refuter confirmed the demotion (§10.10 note below) and found D-52 as the next layer. |
| D-44 | MINOR | CLOSED (round 7, documentation) | The package-insensitivity rationale (comparing the class's *simple* name) is contradicted on the `OPAQUE` branch, which embeds the fully-qualified class name in compared content: 197 rows across 17 operations (`type()`/`getRuntimeType()` × 16, `UIntegerValue.getuInteger()` × 1) would be a false divergence if a port relocated its package. Fixed by stating the two costs in `UValue`'s class comment and asserting the token/row distinction directly; the underlying `OPAQUE` limit is unchanged and stays declared. |
| D-45 | MINOR | CLOSED (round 7, documentation) | `noOperationAnswersWithTwoRuntimeClasses`'s stated reason ("a declared return type is one class") is false for **84 of 285** operations (16 declare `Type`, 21 `URealValue`, 19 `UBooleanValue`, 12 `UIntegerValue`, 9 `UncertainBooleanValue`, 5 `UStringValue`, 1 `SequenceValue`, 1 `uDataTypes.UInteger`) — more than one runtime class is legal by the API. The measured conclusion (0 of 285, over the shipped corpora) still holds; the Javadoc now states the measured fact and labels the premise a corpus fact, inheriting D-30. |
| D-46 | CRITICAL | CLOSED (round 8) — folded into D-43 half (b) | `declaredJavaType(referenceToken,"x")` laundered a wrong-class port to a control-identical sweep with the mandated reason in 0 rows, though three documents asserted it was printed. Closed by deleting `declaredJavaType` entirely. |
| D-47 | MAJOR | CLOSED (round 8) as an overclaim | The row note asserted "Both classes were OBSERVED … so this row is a statement about the two implementations" 1618 times, on a subject that fabricated the object it observed — `observedFrom` believes any object and the harness cannot check it. Note now states the limit instead of certifying the opposite. |
| D-48 | MAJOR | RECLASSIFIED (round 8) — named limit, dated requirement, not a false-divergence trap | Through a non-attributing adapter, a defect-free port and a port carrying a **real 401-row/9-operation wrong-class infidelity** produced 19 083 of 19 083 rows byte-identical, notes included — the "absorbing state". Round 8 does not make the two distinguishable (impossible without S4's real port); it makes the equality explicit (`javaTypeMismatch 3445`, `DIFFER 0`, 74 passes, both readings) rather than hiding it behind a hedge a reader might discard 401 genuine findings on. |
| D-49 | MAJOR | PARTLY CLOSED (round 8) | Full reports for a port defect and an adapter defect used to differ by one header line only. Aggregate now closed: `# rows.javaTypeMismatch` / `# op.<key>.javaTypeMismatch` exist at file and per-operation level. Not closed: *provenance* (`OBSERVED` vs `ASSUMED`) still reaches only the row note — closed by the same S4 requirement as D-48. |
| D-50 | MINOR | open, unchanged | D-44's "197/17" and D-45's "84 declare a non-final type" are stated in Javadoc/assertion messages but not computed by any assertion — stated, not measured. |
| D-51 | MINOR | CLOSED (round 8) | The worked adapter snippet tested only for `null`; a literal copy would answer `nullValue()`, not `voidValue()`, on the 8 `void` mutators. Both worked snippets now begin with the `getReturnType()==void.class` branch. Consequence was nil either way (`DifferentialSweep` already routed both non-observations to `UNMEASURABLE`). |
| D-52 | MAJOR | **open, inert at S1** — closed for S1 by wording (dated shape requirement); mechanism is S4's to build | Round 8's own refuter: **the escape hatch is not gone, it moved from a `String` parameter to an `Object` parameter.** `observedFrom(Object)` reads `getClass().getName()` off whatever object it is handed, so an author who chooses the object has chosen the token. Measured with **one port carrying a real 401-row/9-operation wrong-class defect** and two adapters differing by one line: **A**, observing the object its port actually returned, publishes `javaTypeMismatch 401` across the 9 named operations; **B**, observing an **empty stand-in class** of the reference's name, publishes **0** in every figure, a verdict tally **byte-identical to the perfect-port control**, and 0 rows carrying any type clause — with provenance reported as `OBSERVED` (worse than round 7's `DECLARED`, which at least named the act). Nineteen empty stand-ins erase the whole 3445-row dimension; the naive four-line "observe the boxed primitive" version already goes 3445→401 with no census at all. **Not `DEFECTIVE` and not a false green** — inert at S1, 74 stage passes either way, no S1 figure moves. **Fix, written into `harness-contract.md` §7–§8 by the round-8-refutation commit:** mandate the adapter's *shape*, not another call — the observed object must be the invocation's own return value, captured at one seam (`PortedInfidelityDetectionPowerTest.observeWhatThePortReturned`) — and a reviewer checks the shape, not the prose, since both A and B "state their attribution route" truthfully. |
| D-53 | MINOR | open — corrected in the contract, Javadoc not yet updated | "No agreement figure without the count beside it" is an overclaim: `agreementCount()`/`agreements()` are public and unaccompanied, and `isClean()` returns `true` on a sweep carrying type mismatches. The binding mechanism (`stageStatement()`/`summary()`) is real and sufficient; the sentence should say so, not more. |
| D-54 | MINOR | open — stated in the contract | `javaTypeMismatchCount()` is **not monotone in wrongness**: a row wrong in both dimensions is `DIFFER` and leaves the count, so adding a content defect to a wrong-class port takes it from 3445/182 ops to 1883/42. The header figure must not be read as a lower bound. |
| D-55 | MINOR | open, latent (0 of 197 `OPAQUE` reference rows reachable today) | `UValue.opaque(className, repr)` renders content as a non-injective concatenation (`"className\|repr"`); a value differing in **both** class name and representation can render equal content, demoting a genuine content difference to a counted-not-scored type mismatch. Needs a crafted collision; unreachable in today's corpus. |
| D-56 | MINOR | open, latent (0 `DIFFER` over 17 `SequenceValue` rows today) | The demotion is not applied at depth: `UValue.content()`'s `SEQUENCE` branch embeds each element's full canonical form (type token included), so a nested type-only difference is still scored `DIFFER` — round 8's own false-divergence mode, one level down. Unreachable by today's corpus, not by construction. |
| D-57 | MINOR | open — recorded as a gap | The demotion's cost **at gate level** was unrecorded: every operation passing now with `javaTypeMismatch > 0` is a pass the demotion created — measured as **29** operations for a wrong-class or unattributed port (named in the round-8-refutation report) and **4** for a receiver-echoing subject (all four on discriminating accessors, each individually reviewed and signed off). |

**Totals.** Open: 5 MAJOR (D-17/narrowed by D-32, D-20, D-29, D-30, D-52) plus D-21/D-49 open only in
one clause each and D-48 carried as a named limit; 18 MINOR. Closed: D-15 (CRITICAL), D-16, D-18, D-19,
D-26, D-27, D-33, D-34, D-35, D-36, D-43 (both halves), D-44, D-45, D-46 (CRITICAL), D-47, D-51, and the
round-1/2/3 CRITICALs D1, D2, D-10. **No defect open today is a scoring defect** — nothing open scores a
non-agreement as an agreement, and nothing open scores a faithful port as diverging. The four open
MAJORs besides D-52 are two limits of reach (D-29's 92 unsatisfiable operations, D-30's unmeasured input
domain), one attribution loss a subject can force on itself (D-17/D-32), and one tautological test
(D-20). **D-52 is the only open MAJOR that is a defect in a rule rather than a limit of reach, and it is
inert at S1** — it cannot change any figure S1 publishes; it invalidates `harness-contract.md` §7's
dated obligation as first worded, repaired by mandating the invocation-seam shape, whose mechanism only
S4 can supply because only S4 has a port to invoke.

## 10.5 The id re-keying map — pointer

Round 4 produced two independent reports that both used `D-16`…`D-19` for different defects, and round
6's refutation collided with round-5 keys already spent. Every id was re-keyed at introduction into the
single canonical table above (§10.4); the source→canonical mapping itself (which report used which
local id before re-keying) is git history on this branch, in the now-superseded round reports
(`stage-01-verification-round4.md`, `-round5.md`'s static review, `-round6.md`). **A bare `D-nn` anywhere
in `docs/port2/` means §10.4**, and every citing document already uses the canonical key, so the mapping
is not needed to read this file.

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
   since round 6). **A *runtime-class* difference with identical content is measured and published but
   not scored** — `AGREE`, counted in `javaTypeMismatch` — so **a type-only infidelity does not fail a
   gate at S1**, and a stage must say so beside any agreement figure until the S4 requirement in
   `harness-contract.md` §7 is met (D-43).
7. **A sign-off is a disclosure, not a pass.** Its rationale must say what a reader should *not*
   conclude, and it lands in the evidence file.
8. **Attribute the Java class the port *returned*, not the one your factory chose — and say in the
   stage document which you did** (D-43). Call `UValue.observedFrom(theObjectYourPortReturned)`. There
   is **no API that takes a class name from an adapter at all** any more. A token is `OBSERVED` or
   `ASSUMED`; you choose neither. **No stage may quote a type-fidelity figure without stating how its
   adapter obtained the token**, and a row whose note reads `subject ASSUMED` is a finding about the
   adapter.
9. **S4 has one extra obligation, and it is dated (2026-08-17), not optional.** Once real ported value
   classes exist in `use-core/src/main`, route the adapter through `observedFrom` and add
   `assertEquals(0, result.javaTypeMismatchCount(), result.summary())` to the gate.
10. **The obligation is a SHAPE, not a call** (D-52). The object handed to `observedFrom` must be **the
    value the invocation returned**, captured at one seam and used for nothing else. A reviewer checks
    the adapter's shape, not its prose — "the attribution route is stated" does not separate an honest
    adapter from a laundering one, because both would state it truthfully.
11. **Two figures a stage must not over-read.** `javaTypeMismatchCount()` is **not a lower bound** on
    wrong-class rows (D-54), and a **stage pass carrying `javaTypeMismatch > 0` is a pass the round-8
    demotion created** (29 of them for a wrong-class port, 4 for a receiver-echoing subject — D-57).
12. **Read `harness-contract.md` §8 before writing the sweep.** It is this list in imperative form.

## 10.7 The standing lesson, after eight rounds

Round 1: a harness failure counted as agreement. Round 2: two throws counted as agreement. Round 3: two
`VOID`s counted as agreement. Round 4: two equal values over a one-valued codomain counted as fidelity —
no bug in the scorer at all. Round 5: three of four MAJORs were in the *documents*, the same failure
mode translated up one level. Round 6: the harness was comparing the *content* of a value and calling it
the value. Rounds 7–8: **a fix is not assessed until someone measures what a faithful port does under
it** — a defect's signature and a faithful port's signature were the same number (3445 `DIFFER`), twice,
because a token *declared* by the thing under test rather than *observed* by the instrument collides
with the defect by default, not by edge case. The generalisation that survives all eight rounds: **an
artefact whose headline reads stronger than the measurement behind it** is the thing to look for in
S4–S7, and **an instrument must read something the thing under test does not choose** — where nothing
non-choosable exists yet (as at S1, where no ported class exists to observe), the honest position is a
counted, published, ungated dimension plus a dated obligation on the stage where it becomes observable,
not a fourth API that gives the author a new way to state the fact instead.

Two questions the instrument now answers and one it does not: *Could the sweep have failed?* —
`distinctReferenceValues()`, gated, **answered**. *Does silence mean anything?* — eleven content probes,
**answered: yes, inside the region the corpora reach**. *How much of the input domain did we reach?* —
**unanswered.** That is the fifth door, and it is D-30 — the design for closing it is
`h14-coverage-design.md`, unimplemented as of this record.

## 10.8 Round 7 — the closure of D-44, D-45 and half of D-43 (behaviour `4bb5b6fe`)

`UValue.observedFrom(Object)` reads `getClass().getName()` off the object a side actually returned,
called by `fromHistorical` on all fourteen unwrapping branches so both sides of a comparison are the
same kind of statement; the one-argument `asJavaType(String)` is deleted; the only stating route left,
`declaredJavaType(String javaType, String why)`, rejects a blank reason; `UValue.typeProvenance()`
(`OBSERVED`/`DECLARED`/`ASSUMED`/`NONE`) is written into every type-mismatch row's note. Measured, one
run, four subjects, 285 operations / 19 083 rows:

```
  subject                              DIFFER   ops   passes   notes ASSUMED
  P0-perfect                                0     0       74            0
  P12-boxed-primitive                    3445   182       45            0
  P13-factory-typed-adapter              3445   182       45         3445
  P14-observing-adapter                     0     0       74            0
```

`P13` is the *before* state kept as a test — a **content-perfect** port whose adapter follows the
documented worked example (`UValue.<factory>(content)`) — and it reproduces round 6's headline
3445/182/45 exactly, from a port with no defect in it. `P14`, the same port with one line changed
(`.observedFrom(returned)`), is row-for-row indistinguishable from the control. `P12`, the genuinely
wrong-class port, is unmoved (D-18 not regressed). Acceptance: `BUILD SUCCESS`, 79 surefire + 130
failsafe = 209 methods, 0 failures, two byte-identical runs, `src/main` diff empty. **Closes D-43 half
(a), D-44, D-45.** Not independently verified at this point — that came in round 7's own refutation
(7R), which found half (b) still open: see §10.4's D-43/D-46 rows. `StubCandidate` attributes through a
named method, `attributed(UValue)`, that **declares** with a written reason rather than observing —
because there is no ported `URealValue` in `use-core/src/main` to observe, writing one *is* S4 — and
`Candidate`'s Javadoc says outright that this is the one method in the stub an S4 adapter must not copy.

## 10.9 Round 8 — the Java-runtime-type check is DEMOTED, and its promotion is S4's (behaviour `066fe15c`)

Rounds 6 and 7 both patched one instance of the same defect (an author-influenced token) and neither
converged, because at S1 there is no ported implementation to observe, so every round just invented a
new way for the author to influence what the check read. Round 8 removes the ability to choose a token
at all rather than patching the newest instance: `declaredJavaType`, `TypeProvenance.DECLARED` and
`typeDeclarationReason()` are **deleted**; a token is `OBSERVED` (`observedFrom(Object)`) or `ASSUMED`
(the factory default); `opaque(String,String)` is not a type-only channel (its class name is written
into `content()` too, so a lie there is still a `DIFFER`). A type-only difference is now `AGREE`,
**counted, not scored**, in `Result.javaTypeMismatchCount()` / `# rows.javaTypeMismatch` /
`# op.<key>.javaTypeMismatch`, printed unconditionally by `stageStatement()`; `harness-contract.md` §7
carries the dated REQUIREMENT that S4 route through `observedFrom` and gate on
`javaTypeMismatchCount() == 0`. Measured after: control unchanged; the P13 factory-typed adapter on a
content-perfect port now `DIFFER 0 / 74 passes / javaTypeMismatch 3445` (the false-divergence mode and
its 29 lost passes are gone); the genuinely wrong-class port `DIFFER 0 / 74 passes / javaTypeMismatch
3445`, indistinguishable from the above except by the row note's provenance clause. Content probes
(§10.3) are all unchanged. Cost, measured against `UnwrittenPortInvariantTest`'s receiver-echoing
subject (a non-attributing adapter by construction): its agreement rows move from 4567 to **4951**, and
the +384 is exactly its `javaTypeMismatch` count; five operations regain full agreement
(`BooleanValue.value()`, `BooleanValue.isTrue()`, `IntegerValue.value()`, `StringValue.value()`,
`RealValue.value()`), each individually reviewed and signed off, each sign-off asserting that *all* of
its agreement rows are java-type mismatches — the gate-level version of this same cost is D-57's 29 (for
a wrong-class or non-attributing port) and 4 (for the echoing subject alone). Acceptance: `BUILD
SUCCESS`, 79 + 130 = 209 methods (delta 0 from round 7 — three tests renamed to say what they measure
now, none added or removed), two byte-identical runs, `src/main` diff empty; goldens refreshed
deliberately, two header lines per file, both `0`, no data row moved.

## 10.10 Round 8's refutation — `SOUND_WITH_DOCUMENTED_LIMITS`, and D-52 (`c91277ff`)

### 10.10.1 What was confirmed

The control reproduced from the refuter's **own** sweep rig, not merely re-read from the porter's:
19 083 rows, 17 199 measured and agreed, `{AGREE=17199, BOTH_THREW=910, HARNESS_ERROR=883,
UNMEASURABLE=91}`, 0 `DIFFER`, 0 `MIXED`, 74 stage passes, `javaTypeMismatch` 0 — `md5
c724bd19dbed9071ffc8762675584107`, **identical to round 7's**, from three independent extractions.
**Every content probe P1–P11 unchanged** from round 7 (the table is §10.3; the detected-operation sets
are pasted verbatim there), blind-spot set still exactly `{P11 / URealValue.round()}`. **The
false-divergence mode confirmed gone**: the P13 construction reaches the control's exact stage-pass set
with 0 `DIFFER`. **The demotion swallows no content difference**, checked at sweep scale: a content
defect alone measures `DIFFER 468`; the same defect plus a wrong-class adapter still measures `DIFFER
468` (`javaTypeMismatch 3445` on top, unrelated rows); but placed **on the very rows the type difference
lives on**, `DIFFER` goes 0 → **1831 across 143 operations, losing 3 stage passes** — so the type
dimension does not launder a content defect that overlaps it. The count is accurate against three
independent recounts (401 across nine named operations; 3445 across 182). No false green in five of the
refuter's own constructions plus the suite's eleven. **Independently closes D-43 half (b), D-46, D-47,
D-51**; correctly leaves **D-29, D-30, D-48, D-50** open and untouched. The provenance aggregate
(`OBSERVED`/`ASSUMED` counts at header level) was independently called the cheapest remaining item by
both the round-8 porter and this refuter.

### 10.10.2 D-52 — the decisive construction

One port with a **real** wrong-class defect — content-perfect on every row, answering a raw boxed
primitive where the historical answers an `org.tzi.use.uml.ocl.value.*` object, the same 401-row /
9-operation infidelity quoted in §10.9 — and **two adapters differing by one line**:

```
  R0-control (perfect port)        DIFFER=0  typeMism=0    ops=0    passes=74
  A: honest, observes its object   DIFFER=0  typeMism=401  ops=9    passes=74   (401 rows carry a type clause)
  B: same port, stand-in laundering DIFFER=0 typeMism=0    ops=0    passes=74   (0 rows carry a type clause)
  B verdict tally == control?  true      B stage-pass set == control's?  true
```

`observedFrom(anEmptyStandInClass)` — an object of the reference's class name but none of its state —
takes the defect to **0 in every published figure**, a verdict tally byte-identical to the control, with
provenance reported as `OBSERVED` (worse than round 7's `DECLARED`, which at least named the act).
Nineteen empty stand-in classes erase the whole 3445-row dimension the same way; the naive four-line
version ("observe the boxed primitive, not the object") already reaches 3445 → 401 with no census at
all, so the gradient starts shallow. **Verdict: MAJOR, open, inert at S1** — 74 stage passes either way,
so no S1 figure moves; the target is `harness-contract.md` §7's dated obligation. **Fix, written into
§7–§8 by this commit:** mandate the adapter's *shape* — the observed object must be the invocation's own
return value, captured at one seam, as `PortedInfidelityDetectionPowerTest.observeWhatThePortReturned`
(`:1116-1126`) already demonstrates — and a reviewer checks the shape, not the prose, since both A and B
"state their attribution route" truthfully. Four MINORs alongside, in §10.4: D-53–D-57.

Reproduce: `mvn -o -pl use-core test -Dtest=PortedInfidelityDetectionPowerTest` (P0–P12, §10.3, and the
D-43/D-52 constructions); `-Dtest=DifferentialHarnessRegressionTest` (the D1/D2/D-10/D-15 pins and the
type-provenance unit tests); full acceptance `mvn -q clean && mvn -B verify -Djava.awt.headless=true` →
`BUILD SUCCESS`, 209 methods, 0 failures, `git diff --name-status -- '*/src/main/*'` empty, goldens
byte-identical across runs.

---

# 11. APPENDIX — the D-15 stage-gate mechanism (compressed; see git history for the full record)

> §11 originally recorded the D-15 fix (`0a93ad4f`) in full, including the round-4 codomain census that
> motivated it. That narrative is superseded by §10 and is git history; this section keeps only the one
> design point still cited elsewhere by number.

## 11.3.2 The gate — a mechanism, not a convention

`Result.isStagePass(int minimumMeasurements, AcceptedDegenerateOperations acknowledged)` takes a
**mandatory** measurement floor — passing `0` throws `IllegalArgumentException` rather than silently
gating on nothing — plus an explicit, opt-in sign-off set (`none()` by default, never supplied
implicitly) whose key includes the single canonical value being excused, so a sign-off lapses by itself
the instant the operation stops answering what was reviewed. `isClean()` was deliberately left
unchanged and is not a pass predicate (its Javadoc says so, naming D-15). The discipline
`harness-contract.md` had stated in prose is therefore not a convention a stage document can forget to
follow — it is a mechanism a stage's own gate call cannot compile past without a floor and a sign-off
set. `AcceptedDegenerateOperations` mirrors this shape and `AcceptedThrowPairs` predates it; D-34
(§10.4) is what happened the one time an overload let a caller elide the sign-off parameter.
