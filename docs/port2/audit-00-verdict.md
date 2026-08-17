# Audit verdict — S0/S1/S2 foundation, branch `port-uncertainty-2`

**Adjudicator's verdict: GO_AFTER_FIXES.**

Four auditors attacked the foundation. Nothing they found is a design error. Everything they found
is a *measurement* error: the foundation's load-bearing mechanisms (class-loader isolation, the
vendored oracle, the specification's counts and citations) survived deliberate attack, but three
independent instruments that later stages will gate on — the differential harness's verdict
algebra, the `mvn test` acceptance command, and the ArchUnit "suite green" signal — can each report
a pass over nothing at all. The port can proceed. It cannot proceed *citing numbers from those
instruments* until they are repaired.

Everything below that is marked CONFIRMED was re-verified by me at source or by execution, not
adopted on an auditor's word. Commands were run with cwd `/tmp/adjrun`; no Maven was run; the
repository is unmodified except this file.

---

## 1. What I re-verified myself

| Claim | Verdict | Evidence I produced |
|---|---|---|
| D1 — harness marshalling failures score as agreement | **CONFIRMED** | ran the sweep, output below |
| SBoolean is a ready-made D1 (`supports()` true, no marshalling path) | **CONFIRMED** | ran it, output below |
| `supports()` converts "oracle broken" into "not implemented" | **CONFIRMED** | ran it, output below |
| Smoke test line 72 saves the shipped test from vacuity | **CONFIRMED** | `grep -n assert` on the test |
| Zero-row sweep passes the report guard | **CONFIRMED** | `DiffReportWriter.java:62-65` guards `results.isEmpty()`, a `List<Result>` |
| OPAQUE canonical form is rounded and locale-bound | **CONFIRMED** | `javap` on the vendored jar |
| 12 of 13 baseline methods contain no assertion | **CONFIRMED** | `grep -c assert` = 0, plus surefire vs cycle report |
| `specification.md` is stale on the acceptance gate | **CONFIRMED** | `grep -c "mvn verify"` = 0 |
| Vendored jars have no recorded provenance or licence | **CONFIRMED** | `grep`, `unzip -p` |

Reproduction of D1 and its worst instance, in one run
(`java -cp use-core/target/classes:use-core/target/test-classes:/tmp/adjrun -Duse.historical.jars.dir=…`):

```
D1 summary        URealValue.add(value): 169 rows, AGREE_THROWN=169
D1 disagreements  0
D1 row0           0	URealValue.add(value)	UINTEGER(0,0.0) | UINTEGER(0,0.0)	THROWN:java.lang.IllegalArgumentException	THROWN:java.lang.IllegalArgumentException	AGREE_THROWN	
supports(SBool)   true
SBool invoke      java.lang.IllegalArgumentException: SBooleanValue.and(value) expects a receiver of
                  org.tzi.use.uml.ocl.value.SBooleanValue but the supplied UBOOLEAN(true,0.8) maps to
                  org.tzi.use.uml.ocl.value.UBooleanValue
supports(typo)    false
supports(absent)  false
```

169 rows, 100 % agreement, zero disagreements, and neither side ever entered `URealValue.add`.
The four `supports()` lines are the same defect seen from two ends: it says *yes* to an operation
no input can reach, and *no* to an oracle that is broken.

---

## 2. Contradictions between auditors — settled

**(a) Is the shipped smoke test vacuous?** Auditor 1 states the pass criterion is
`assertEquals(List.of(), result.disagreements())` at `UncertaintyDifferentialSmokeTest.java:70`.
Auditor 0 says line 72 is a second, stronger assertion. **Auditor 0 is right; I adopt it.**
`grep -n assert` on that file returns both lines, and line 72 reads
`assertEquals(result.rowCount(), result.count(DiffVerdict.AGREE));`. On a 169-row all-`AGREE_THROWN`
sweep, `count(AGREE)` is 0 and the test *fails*. The shipped S1 smoke test is **not** vacuous.
This does not soften D1 by one inch — it relocates it. What is vulnerable is not the test that
exists, it is every S4–S7 driver that gates on `disagreements()`, which
`DifferentialSweep.java:225` documents as "the only clean outcome". One sentence in
`stage-01-refutation-empirical.md` §5 overstates this and should be corrected so nobody fixes the
wrong thing.

**(b) Is S0's baseline still wrong?** Auditor 1 files it as CRITICAL: `mvn test` omits ~130
integration tests and `stage-00-baseline.md` never says so. Auditor 2 reports that commit
`8789e035` landed *during* the audit adding exactly that correction. **Both are right about
different moments; I adopt Auditor 2's narrower live framing.** `git show --stat 8789e035` confirms
a 47-line correction to `stage-00-baseline.md` restating the baseline as 143 and the gate as
`mvn verify`. So the S0 *document* is repaired. What is **not** repaired is `specification.md` —
the document S3 is actually executed from — where `grep -c` returns **0** for each of
`mvn verify`, `failsafe`, `OCLExpressionIT`, while line 34 (B3) still reads "baseline is 13 methods
/ 3 classes" and line 2381 (§9) still reads "the S3–S7 'suite green' gate asserts 13 methods".
The finding survives, reduced from "the baseline is wrong" to "the baseline was corrected in one
document and not propagated to the one that matters". That is still blocking.

**(c) Auditor 0's D1 vs Auditor 1's D1.** Same defect, two corpora (169 rows on `URealValue.add`
over UInteger inputs; 32 of 81 free rows on `UBooleanValue.and`). Merged into one finding.
Independent reproduction from two directions raises confidence, it does not make two defects.

**(d) Nobody contradicted Auditor 2's F1** because nobody else looked at it. I checked:
`grep -c "assert" use-core/src/test/java/org/tzi/use/architecture/MavenCyclicDependenciesCoreTest.java`
returns **0**; the rule terminates in `.evaluate(classes)` at line 175, which returns an
`EvaluationResult` and never throws. Surefire records `Tests run: 11, Failures: 0` while
`docs/archunit-results/cycles-current-failure-report.txt` ends `Cycle count: 55`. This is an
**inherited upstream property, not a defect the port introduced** — but it means the phrase "full
suite green" has, for the whole of S0–S2, rested on a single assertion-bearing method
(`ModelAPITest`) that touches none of the uncertainty subsystem.

---

## 3. Downgraded for lack of evidence

* **S0's "43 classes / 300 methods with vintage" probe — UNVERIFIABLE.** Auditor 1 declares this
  honestly: the probe worktree was removed and no surefire XML survives. Under the no-Maven rule
  nobody can re-derive it. It is not disputed, it is unsupported. It matters because **blocking
  decision B3 is argued from it.** Re-measure before B3 is answered.
* **Rule-4-vs-rule-3 wording contradiction** (`specification.md:34` says ground rule 4;
  `stage-00-baseline.md` §3 says rule 3): confirmed as a textual inconsistency between two
  documents. I did not adjudicate which rule number is correct — that requires the ground-rule list,
  which is not in the repository. Filed as a documentation defect, not a technical one.
* Nothing else in the four reports lacked a file:line or pasted output. That is unusual and worth
  saying: the auditors did not launder speculation.

---

## 4. MUST FIX — ranked

Ranked by what threatens the port, not by how alarming it sounds. The ordering principle: **every
defect in tier 1 manufactures a green number rather than a red one.** A defect that fails loudly
costs a debugging session; a defect that passes quietly costs the thesis its evidence.

### Tier 1 — before any stage cites a differential number (i.e. before S4 opens, and cheap enough to do now)

| # | Defect | Where | Minimal fix |
|---|---|---|---|
| **1** | **D1.** `apply()` wraps `candidate.invoke(...)` — marshalling included — in `catch (Throwable t)`; `classify()` scores two throws as agreement on matching class *name* alone; `AGREE_THROWN.isAgreement()` is true. The oracle's own "cannot marshal this input" is indistinguishable in every report column from a throw by the code under test. | `DifferentialSweep.java:115-131`, `DiffVerdict.java:32-34`, `HistoricalOracle.java:369-381`, `:492-501` | Dedicated `HarnessMarshallingException` thrown by `HistoricalOracle.invoke`/`toHistorical`; caught separately in `apply()`; scored as a new **non-agreement** verdict `HARNESS_ERROR`. ~20 lines, 3 files. |
| **2** | **D1's worst instance — SBoolean.** `supports(SBooleanValue.and)` returns **true**, so the block is not diverted to the visible `UNSUPPORTED`; but no `UValue.Kind` marshals to `SBooleanValue`. 39 operations — the largest single block in the S2 spec — would report 100 % agreement with nothing executed. | `HistoricalOracle.java:378`, `:463-500` (no SBoolean branch), `UValue.java:27-48` (no `SBOOLEAN` kind) | Either add the kinds + marshalling branches (~60 lines), **or** make `supports()` return false when the receiver type has no `toHistorical` branch so it lands on the visible `UNSUPPORTED` (~15 lines). Second option is correct now; first is needed before SBoolean is ported. |
| **3** | **Addressable-package limit is undeclared.** `VALUE_PKG` is hard-coded at `HistoricalOracle.java:90` and `load()` is the only resolver, so `org.tzi.use.uml.ocl.type.*` (the S2 type registrations — "UInteger 12 classes / 13 registrations") and all of `uDataTypes.*` are unreachable **by design**, and nothing says so. | `HistoricalOracle.java:90`, `:334-345` | No code fix required. Write it into the spec as a declared scope boundary, so nobody later reports the type-registration work as "differentially verified". |
| **4** | **D3 — a zero-row sweep reads clean and gets a report written.** The guard tests the wrong quantity: `results.isEmpty()` is a `List<Result>`, not rows. Its own error message states the property it fails to enforce. | `DiffReportWriter.java:62-65`; `DifferentialSweep.buildTuples` yields zero tuples on any empty domain | Guard on total row count in `writeAll`. 2 lines. |
| **5** | **The headline exactness guarantee is false on the OPAQUE branch, and locale-bound.** `UValue.java:17-21` promises exact comparison via `Double.toString`; `HistoricalOracle.java:550` instead embeds the foreign `toString()`, and `javap` on the vendored jar shows every uncertainty class formats with `%5.3f` (`UInteger(%d, %5.3f)`, `UReal(%5.3f, %5.3f)`, `SBoolean(%5.3f, %5.3f, %5.3f, %5.3f)`). So OPAQUE comparison rounds to 3 decimals **and** flips to a decimal comma under a European locale — refuting `stage-01.md` §6 item 6 and `DiffReportWriter`'s byte-identical-report guarantee. | `UValue.java:17-21`, `:228-230`; `HistoricalOracle.java:550` | Build the opaque repr from the object's declared fields via `Double.toString`, or refuse (`UNSUPPORTED`) rather than guess. ~15 lines. Fixes both the rounding and the locale defect. |
| **6** | **`supports()` swallows `HistoricalOracleUnavailableException`** (it `extends IllegalStateException`, hence `RuntimeException`), so a broken or wrong-version oracle jar reports as "not implemented". The single violation of the harness's own stated no-degradation policy at `HistoricalOracle.java:54-58`. | `HistoricalOracle.java:354-362`, `:627` | Let it escape; catch only the genuine "no such method". 1 line. |
| **7** | **Void methods and null returns are indistinguishable, and both auto-AGREE.** `Method.invoke` returns null for void, and `fromHistorical` maps null to `NULL`. The harness never re-reads the receiver, so an empty-bodied mutator agrees forever. | `HistoricalOracle.java:505-508` | Check `method.getReturnType() == void.class` and return a new `Kind.VOID`. ~8 lines. Post-state observation is a larger change — record it as a declared scope limit, do not build it now. |
| **8** | **A `Candidate` returning Java null NPEs inside `classify()`** and destroys every row already computed. Returning null is the natural mistake for a ported operation that maps to `UndefinedValue`, so S4 will hit it. | `DifferentialSweep.java:117`, `:139` | `Objects.requireNonNull` **inside** `apply()`'s try, so it becomes a recorded throw. 1 line. |
| **9** | **`apply()` catches `Throwable`,** so `StackOverflowError`, `AssertionError` and — given the deliberately fallback-free loader — `NoClassDefFoundError` become comparable data, and two sides hitting the same Error class score AGREE_THROWN. | `DifferentialSweep.java:118` | Catch `Exception`, re-throw `Error`. 2 lines. |
| **10** | **Two regression tests, or the fixes will regress.** | new | (a) a marshalling failure on both sides must produce a **non-agreement** verdict; (b) a zero-row sweep must **fail**. |

Total tier-1 fix surface: roughly 40–50 lines plus two tests, across `DiffVerdict`,
`DifferentialSweep`, `DiffRow`, `HistoricalOracle`, `DiffReportWriter`. **No redesign.** That is the
single most important sentence in this report: the harness is well-built and wrongly scored.

### Tier 2 — before S3 opens

| # | Defect | Where | Minimal fix |
|---|---|---|---|
| **11** | **`specification.md` is stale on the acceptance gate.** S3 is executed from this document, and it tells the implementer the gate is 13 surefire methods. `grep -c` returns 0 for `mvn verify`, `failsafe` and `OCLExpressionIT`. The 130-method failsafe tier contains `OCLExpressionIT` (OCL expressions — the subsystem the port changes most) and `ShellIT` (131 `.use` fixtures, the only thing that catches the B4 `equals` collision). | `specification.md:34`, `:2381` | Propagate `8789e035`'s correction into B3 and §9: gate is `mvn verify`, baseline 143. |
| **12** | **"Full suite green" is close to meaningless and the spec does not say so.** 12 of 13 baseline methods contain no assertion — `.evaluate()`, not `.check()`. Proof: 11 tests pass while the cycle report reads `Cycle count: 55`. | `MavenCyclicDependenciesCoreTest.java:173-175`, `MavenLayeredArchitectureTest.java:39-54` | Record it in §9 and in B3's premise. B3 is currently argued from "13 methods, 11 of which are ArchUnit cycle checks"; the true premise is "**1** assertion-bearing method", which materially *strengthens* B3. Optionally record the 55-cycle count as a tracked baseline — `.gitignore` now hides the only artifact carrying it. |
| **13** | **`mvn test` overwrites two tracked files** (`docs/port2/differential/*.tsv`, both in `git ls-files`) and no assertion compares the regenerated report against the committed one. It is a log presented as a baseline. | `DiffReportWriter.java:42`, `:126-137`; `UncertaintyDifferentialSmokeTest.java:63`, `:99` | Either write to `target/` and commit a golden copy separately, or add a golden-file comparison. Until then, no later stage may cite those TSVs as pinned evidence. |
| **14** | **S2 has no stage report and no acceptance section anywhere** — `ls docs/port2/` shows no `stage-02*.md`, and `grep -in acceptance specification.md` returns 4 hits, all about other stages' gates. S0 and S1 both have one. | — | Write the S2 acceptance section. Two auditors independently re-ran every operation count, the file inventory, the 1427-entry corpus, the 33 BEHAVIOUR-CHANGING rows and the 11×11 conformance grid, and **all reproduced** — so this is a paperwork gap, not a numbers gap. Harvest their transcripts. |
| **15** | **S1's stage report was never amended after the DEFECTIVE verdict.** `grep -n "D1\|AGREE_THROWN\|DEFECTIVE" docs/port2/stage-01.md` returns nothing; a reader asking "is S1 done?" gets an unqualified yes. | `stage-01.md` | Amend, or record a waiver. Also correct the one overstated sentence in `stage-01-refutation-empirical.md` §5 (see §2(a)). |

---

## 5. INCOMPLETE but not wrong — proceed, track

* **The forward-compatibility trap is real and pre-declared.**
  `HistoricalOracleIsolationTest.uTypesResolveOnlyThroughTheOracle()` asserts the U-types are
  *absent* from the application loader and breaks at line 160 the moment S4 lands. It is documented
  in the source and in `stage-01.md` §6. S4 must budget one inversion (`assertNotSame` instead of
  `assertThrows`) and two extensions, or the build goes red on the first port commit. The test gets
  *stronger* afterwards — the U-types become a real FQN collision instead of a hypothetical one.
  This is a scheduled cost, not a defect.
* **`assertIsolated()`'s Javadoc overstates it** — it polices 12 hard-coded simple names, all through
  `VALUE_PKG`, so no `uDataTypes.*` class is ever isolation-checked despite the comment claiming
  "every class the harness depends on". Low risk: the loader, not this guard, is what holds the
  line, and the loader is sound. Fix the Javadoc at minimum.
* **The report's `inputs` column shows the pre-coercion value**, so a `DIFFER` row in the
  float/int operations is un-diagnosable from the report alone. ~5 lines, low urgency.
* **The differential harness cannot reach the type registrations at all.** See must-fix #3. Track as
  a scope boundary; S4–S7 fidelity claims for that work will need a different instrument.

---

## 6. NOT defects — decisions for a human

1. **B3 conflicts with the ground rules.** Its recommended remedy (a `-Pupstream-oracle` profile
   reviving use-gui's tests) requires editing `use-gui/pom.xml`, which rule 5 forbids. No document
   reconciles this, and the use-core-only figure (35 classes / 283 methods, in
   `stage-00-baseline.md` §4's per-module table) is not carried into B3, which quotes only the
   combined 43/300. **And the 43/300 figure is UNVERIFIABLE** — re-measure before answering B3.
2. **Vendored jar provenance and licence should be a 13th blocking decision.** `stage-01.md` §2
   claims "a recorded provenance"; `grep` for a URL or the word "licence" in that file returns
   nothing. `use.jar` is a GPL-2 fork object build whose source is absent from the repository;
   `atenearesearchgroup.uncertainty.jar` has no `META-INF` at all (`unzip -l` finds none), so the
   MIT licence on record at `specification.md:1206` is evidence for the 2023 *source*, not the 2021
   *binary* committed here. Both are now in git history and cannot be removed without a rewrite.
   Not a technical blocker; a publication blocker for a thesis artifact. It also means the oracle's
   identity is pinned only by sha256, with no statement of which upstream commit produced those
   bytes — nobody can independently reconstruct the reference side of the differential.
3. **Was reading `origin/main` permitted to eliminate a port-2 remedy?** S0's central diagnosis
   measures `origin/main` to conclude "a migration-based revival is not an acceptable fix here",
   which is what pushes B3 toward vintage-engine rather than a Jupiter migration. Measuring
   `origin/main` to *describe* `origin/main` is defensible; letting it *eliminate an option* is a
   governance question nobody has adjudicated.
4. **Is `docs/port2/differential/*.tsv` a log or a baseline?** See must-fix #13. The answer changes
   what the acceptance ritual looks like for every later stage.

---

## 7. What survived attack — and it is a lot

I record this because a report that only lists defects misrepresents the foundation.

* **Class-loader isolation is real, parent-last, and has no fallback.** Auditor 0 tried to break it
  and could not: classes that exist on the application loader are correctly refused; the two
  vendored jars have disjoint namespaces so URL ordering cannot shadow; a constant-pool scan finds
  no third-party dependency that could leak through the delegating path; every reflective accessor
  cast in `fromHistorical` is correct against `javap`.
* **The JPMS rationale for hand-rolling the loader is correct** — independently reproduced with a
  minimal module: on the module path the platform loader *does* return the application's class;
  on the classpath it does not. The "obvious fix" really is a trap.
* **`UNSUPPORTED` is genuinely not an agreement**, so the visible-not-silent-skip design claim holds
  where it is reachable.
* **S1's smoke comparison reproduces byte-identically without Maven** — 784 AGREE, 558/226 for the
  injected fault, matching `stage-01.md` §5.1 exactly, across a different JVM invocation, cwd,
  entry point and classloader parentage. The fault-injection half of the smoke test is not vacuous.
* **Every S2 number that was re-counted, reproduced.** All five operation counts, the 539/24 file
  inventory, 182 historical test methods, the 1427-entry corpus, the 33 BEHAVIOUR-CHANGING rows,
  the modernization ledger's 122/954/10/101/45, and the full 11×11 conformance grid rebuilt from
  the executable oracle script — cell for cell. Eight randomly chosen citations all resolved.
  Blocking-decision numbering is internally consistent with no dangling references.
* **Every formal rule question is clean.** No product, pom or `module-info` change; no use-gui or
  use-assembly change; `main` and `upstream-main` byte-identical to their remotes; no path by which
  the vendored jars reach a product artifact (four leak paths checked and closed); no
  `origin/main` file contamination.

Two known documentation defects are real but negligible: commit `d0bf18aa`'s message describes a
working-tree action the commit does not contain, and `specification.md:925-932` presents a silently
trimmed diff transcript — a second, unflagged instance of the exact defect the document itself
flags at line 1201. Substance survives in both cases. The lesson is narrow: **anything in the spec
presented as a pasted transcript should be re-run rather than trusted; the `# N` count annotations
proved reliable.** Four of five operation-count grep blocks also give an elided, unrunnable path
(`F=…/StandardOperationsUReal.java`); the full paths exist in `spec-parts/`, so the cost is
reviewer time plus a live risk of an S3 reviewer computing a different total from a different file
set and wrongly reporting a mismatch.

---

## 8. Residual risk if you say GO now

Say GO and you are accepting these, knowingly:

1. **Until tier 1 lands, no differential number means anything.** A 100 %-agreement report over zero
   executed operations is not a hypothetical — it is pasted in §1 of this document. The largest
   operation block in the spec (SBoolean, 39 ops) is currently in exactly that state.
2. **Until tier 2 #11 lands, S3 will be built against a gate the project has already retracted.**
   The implementer reads `specification.md`, which says 13 methods and `mvn test`; the project
   decided five minutes after S2 was declared done that the answer is 143 and `mvn verify`.
3. **"Full suite green" will remain close to meaningless** for architecture regressions. The port
   can add arbitrary package cycles with no signal of any kind: not asserted, not tracked, not in
   `git status`, not baselined anywhere.
4. **The rounding floor on the OPAQUE branch is 3 decimal places, and the reports are not
   reproducible off an English locale.** Any rounding regression in a value that reaches OPAQUE —
   the whole SBoolean surface, plus every `type()`/`getRuntimeType()` comparison — is invisible.
5. **Mutators are outside the instrument's reach entirely** and nothing marks them as such. A ported
   `setTypeToRuntimeType()` with an empty body agrees on every row, forever.
6. **B3 is unanswerable as written**: its evidence is unverifiable and its recommended remedy is
   out of bounds under rule 5.
7. **The jars are a permanent publication risk** already baked into git history.

Risks 1, 4, 5 are the ones that end up in a thesis as a false fidelity claim. They are also the
cheapest to fix — which is the whole reason this verdict is GO_AFTER_FIXES and not NO_GO.

**Do tier 1 and tier 2 first. They are one focused session, no redesign. Then S3 is safe.**
