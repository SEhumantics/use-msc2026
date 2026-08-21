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
