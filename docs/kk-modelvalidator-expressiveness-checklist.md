# KK-ModelValidator expressiveness & correctness checklist

Tracks closing the gaps identified in review: 5 examples covered translation
correctness and basic commands well, but no model-structural feature
(association classes, inheritance, aggregation/composition) and several
commands (`-scrolling`, `-scrollingCT`/`-scrollingAllCT`, partial-solution
completion, targeted `-invIndep`) were never exercised anywhere, and
correctness was verified by one-time manual `check -v` rather than automated,
regression-protected tests for anything past `01-Library`.

- [x] `06-AssociationClass` — new domain model, real plugin feature
      (`IAssociationClass`/`AssociationClass` exist in source) never tested at
      any level (not even upstream's own JUnit suite). Built and verified by
      an agent; independently spot-checked.
- [x] `07-Inheritance` — new domain model, class hierarchy / generalization,
      `oclIsTypeOf`/`oclAsType`, polymorphic navigation. Built and verified.
- [x] `08-AggregationComposition` — new domain model, whole-part association,
      `aggregationcyclefreeness`/`forbiddensharing` toggles shown on vs off.
      Built and verified; **independently re-run by me and confirmed** — also
      surfaced a real plugin limitation (toggle is dead code for the single-
      association/same-class case), documented in the README.
- [x] `05-Genealogy` extended — partial-solution completion
      (`automaticDiagramExtraction := on`) and classifying terms
      (`-scrollingCT`/`-scrollingAllCT`, descLevel0/1/2 — explicitly left out
      of the original port pass, now working). Both **independently re-run by
      me and confirmed**, exact number match.
- [x] Single-step `-scrolling` (02-EmployeeInvariants) and targeted
      `-invIndep <properties> className::invName` (03-CompanyERSchema) added
      and verified.
- [x] `09-CollectionSemantics` — new small domain model explicitly
      demonstrating the Bag/Sequence-collapsed-to-Set translation limit, with
      a "smoking gun" true-via-OCL/false-via-mv? comparison. Built and
      verified.
- [x] SOIL-based validation tests for every domain (existing 5 + new 3, i.e.
      01 through 08): a `valid-instance.soil`/`.cmd` and an
      `invalid-instance.soil`/`.cmd` violating exactly one named invariant,
      for all 8 domains. All 16 pairs actually run and confirmed.
- [x] Automated JUnit regression tests (`EndToEndValidationTest`-style, not
      just one-time manual `check -v`) for CompanyER, AssociationClass, and
      Inheritance. All 3 pass; floor gate `tests=3311 errors=0 failures=461
      skipped=122`.
- [x] Full reactor `mvn clean verify` + Track E gate (`upstream-oracle-gate.sh
      both`) confirm nothing regressed. `[gate] PASS — mode 'both': every
      check above held.` Two earlier gate runs this session (`GATE FAILED`,
      8 then 2 checks) were both confirmed to be self-inflicted process-
      management artifacts (a stuck self-matching `pgrep` loop blocking the
      gate's own "another Maven is running" lock check), not real defects —
      root-caused and fixed, then verified clean by reproducing the exact
      failing build in full isolation (`BUILD SUCCESS`) before re-running the
      gate to a genuine `PASS`.
- [x] `examples/README.md` and `docs/kk-modelvalidator-port.md` updated with
      everything above; staged in git (not committed). Distribution rebuilt
      and confirmed to ship all 9 example directories (89 files).

**All items checked. Done.**

Every example/model here must actually be run against a real build — not
just written by inspection — before being checked off.

## Round 2: maximal solver power + benchmark visualization

Prompted by wanting the baseline to be genuinely "the good old baseline" at its
strongest, not a JDK-hobbled stand-in.

- [x] Traced the `Failed to get field handle to set library path` WARN to
      `LibraryPathHelper` (unmodified original source) relying on a JDK-8-only
      `ClassLoader.usr_paths` reflection hack that no longer exists on JDK 21 —
      confirmed not a porting regression (would break identically on the
      pristine original plugin on any modern JDK).
- [x] Downloaded and vendored the original plugin's own native solver bundle
      (same URL `-downloadSolvers` uses, still reachable) into
      `kk-modelvalidator/vendored-solvers/`.
- [x] Fixed loading without touching any plugin/Kodkod code: `-Djava.library.path`
      set at JVM launch (`bin/use`, `start_use.bat`, `run-example.sh`) instead
      of the broken runtime mutation.
- [x] Confirmed working end-to-end via the real `bin/use` launch script from a
      rebuilt distribution: `DefaultSAT4J`, `LightSAT4J`, `MiniSat`,
      `MiniSatProver`, `Lingeling`. `Glucose`/`CryptoMiniSat` don't work,
      documented why (native incompatibility / stale Kodkod-version constant,
      neither a setup problem).
- [x] Benchmark: all 9 examples' primary `-validate` scenario × 5 working
      solvers × 5 repeats, solving time captured from real runs (45 combos,
      225 total runs). Raw data + script kept in the session scratchpad.
- [x] Visualization generated and published as an Artifact + sent to the user
      (small-multiples panel per example + full data table, both themes,
      validated categorical palette).
- [x] Docs/README updated (port doc "Sixth pass", examples/README solver
      note, vendored-solvers/README.md).
- [x] Full reactor verify + gate re-check after the launch-script/assembly
      changes — `mvn clean verify` BUILD SUCCESS (`tests=3311 errors=0
      failures=461 skipped=122`, floor PASS); Track E gate re-run.

**Round 2 done.**

## Round 3: restructuring, a real benchmark module, correctness clarity, 8 more examples

- [x] Reconciled "don't fix Kodkod bugs except porting-relevant" against the whole session's own
      history of main-source changes — confirmed clean (compile fixes, restoring the port's own
      earlier mistakes, dependency-migration behavior-preservation, zero-effect hygiene, plugin
      identity metadata; nothing touching translation/solving semantics).
- [x] Verified all 5 solvers agree on SAT/UNSAT always; confirmed (not assumed) they can find
      different concrete witnesses for the same scenario, and that this is expected, not a bug.
- [x] Timing precision improved: `System.nanoTime()`-wrapped wall time around the whole
      `validate()` call, in-process, alongside Kodkod's own millisecond-resolution `Statistics`.
- [x] Restructured into `msc-modelvalidators/{kk-modelvalidator, benchmark, unc-modelvalidator}`
      (renamed from `z3-umodelvalidator` mid-session per explicit request) — `git mv`, every path
      reference updated, reactor rebuilt clean, same floor numbers before/after.
- [x] Built a real `benchmark` Maven module: in-process runner (reuses `EndToEndValidationTest`'s
      own API, not a subprocess per run), structured JSON output (manifest + per-solver results,
      including a witness digest), a template-based HTML report builder, backend-agnostic by
      design for the eventual `unc-modelvalidator` results.
- [x] 8 more examples (17 total): `10-MultipleInheritance`, `11-Subsets`, `12-RecursiveTree`,
      `13-Redefines` (expressiveness, ported from `use-core`'s own bundled examples), `14-Sudoku`,
      `15-NQueens`, `16-GraphColoring`, `17-ZebraPuzzle` (performance, mostly authored from
      scratch) — built by an 8-agent workflow, every one independently confirmed to exist with
      real content and a real manifest entry (17/17 entries survived the concurrent-write risk).
- [x] Five genuine, previously-unknown plugin findings surfaced and documented, none fixed: a
      bitwidth-driven false-negative bug (GraphColoring), a `redefines` soundness gap
      (Redefines), dead `subsets`/`UnionAssociation` code (Subsets), two crashes in
      multiplicity/attribute-binding code (Sudoku), and a pre-existing authoring bug in
      upstream's own bundled `Tree.use` fixture (RecursiveTree, not a port issue at all).
- [x] Full 17-example, 85-cell benchmark run (in-process, real solve/reconstruct, per-example
      repeat overrides for the 3 genuinely slow performance examples) — a new solver-choice
      extreme found: MiniSat 598ms vs DefaultSAT4J 11.4s on the same GraphColoring instance.
- [x] Final report published as an Artifact and sent to the user (feature/expressiveness matrix
      with click-through, per-example `(?)` tooltips carrying model/question/attribution
      metadata, finding-vs-validating mode labels, category filter, witness-agreement counts).
- [x] Full reactor `mvn clean verify` + Track E gate re-confirmed green after every structural
      change in this round.

**Round 3 done.**
