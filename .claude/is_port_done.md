Work in the WSL repository:

`/home/xoruser/use-bel`

Perform a complete, read-only technical review of the uncertain-OCL and subjective-Boolean port after it has been implemented.

Compare the port against:

- the pre-port analysis and source map;
- the historical repository at  
  `/home/xoruser/use-bel/.git/reference-repositories/uncertainty`;
- the underlying Java or Python uncertainty implementations where relevant;
- current USE architecture and conventions;
- the complete historical user-visible uncertainty surface.

Review the implementation on the branch or worktree dedicated to `port-uncertainty`.

Preserve these branch roles:

- `upstream-main`: upstream USE tracking only.
- `port-uncertainty`: complete native uncertain-OCL and subjective-Boolean port.
- `bel-main`: later integration outside this review.

## Scope boundary

Review only:

- uncertain OCL;
- uncertain types and values;
- subjective Boolean types, values, algebra, and operators;
- uncertain literals;
- parser and AST integration;
- operation registration and evaluation;
- uncertain collection and query behavior;
- tests, documentation, and port completeness.

Do not review or propose:

- BeL;
- BeL-Q;
- proposition or opinion stores;
- BeL observation identity;
- agents or source declarations for BeL;
- `lift`;
- BeL plugins or shell commands;
- BeL language syntax or semantics;
- thesis or paper content;
- case-study design;
- thesis evaluation plans.

Do not judge the uncertainty port by whether it is sufficient for BeL. Judge it against the complete historically exposed uncertain-OCL and subjective-Boolean functionality.

## Read-only requirement

Do not modify tracked files.

Permitted actions are:

- inspect source and history;
- compare branches and commits;
- run builds and tests;
- generate ignored build artifacts;
- inspect generated parser artifacts;
- report findings.

Do not:

- edit source or tests;
- apply fixes;
- commit;
- merge;
- push;
- open a pull request.

If problems are found, describe precise fixes without implementing them.

## Review objective

Determine whether the port is:

1. functionally complete;
2. semantically correct;
3. correctly integrated with current USE;
4. appropriately modernized;
5. adequately tested;
6. free from unjustified historical baggage;
7. ready for later integration.

A successful review must detect both:

- missing historical functionality;
- unnecessary or incorrect files copied from the old fork.

## 1. Establish the review baseline

Report:

- current branch and commit;
- worktree status;
- base commit;
- relationship to `origin/port-uncertainty`;
- relationship to `upstream-main`;
- changed files;
- generated files;
- build configuration changes;
- dependency changes;
- test framework and commands.

Confirm that no unrelated BeL or BeL-Q work has entered the port.

## 2. Review the complete historical-to-current source map

Compare every uncertainty-related historical file with the implemented port.

For each historical file, determine whether it was correctly classified as:

- `PORT`
- `REWRITE`
- `GENERATE`
- `DEFER`
- `OMIT`
- `UNCHANGED`

Report:

- historical path;
- current path or replacement;
- supported functionality;
- correctness of the classification;
- missing behavior;
- unnecessary copied structure;
- relevant tests.

Flag:

- omitted files that contain reachable functionality;
- copied generated files;
- obsolete dependencies;
- dead AST or expression classes;
- redundant abstractions;
- undocumented architectural rewrites.

## 3. Review functional completeness by type

Review the complete implementation of:

- `UReal`
- `UInteger`
- `UString`
- `UBoolean`
- `SBoolean`

For every type, verify:

- type registration;
- construction;
- conformance;
- runtime representation;
- literal syntax;
- AST construction;
- expression evaluation;
- rendering;
- equality;
- ordering where defined;
- undefined values;
- invalid values;
- accessors;
- conversions;
- projections;
- arithmetic;
- comparisons;
- logical operations;
- collection interactions;
- all historically exposed standard operations.

Produce a parity matrix with these statuses:

- `COMPLETE`
- `PARTIAL`
- `MISSING`
- `INTENTIONAL CORRECTION`
- `NOT HISTORICALLY REACHABLE`
- `OPTIONAL NEWER EXTENSION`

Do not count a source file as complete merely because it compiles. Verify that the feature is reachable through USE syntax or operation dispatch.

## 4. Review the complete `SBoolean` algebra

Verify every historically exposed subjective-Boolean operation, including:

- construction;
- belief;
- disbelief;
- uncertainty;
- base rate;
- projected probability;
- negation;
- conjunction;
- disjunction;
- deduction;
- discounting;
- minimum;
- maximum;
- operation application;
- minimum fusion;
- majority fusion;
- belief-constraint fusion;
- averaging fusion;
- aleatory cumulative fusion;
- epistemic cumulative fusion;
- weighted fusion;
- consensus or compromise fusion;
- any additional operation found in the historical inventory.

For each operation, verify:

- exact name;
- overloads;
- operand types;
- result type;
- arity;
- binary versus n-ary behavior;
- base-rate behavior;
- undefined propagation;
- degenerate cases;
- numerical stability;
- test coverage;
- consistency with the verified historical semantics.

Flag any n-ary operator implemented through binary nesting when that changes the result.

Separate newer-library-only operations from historical compatibility requirements.

## 5. Review uncertain collection and query behavior

Verify all historically exposed behavior involving:

- `uSelect`;
- `uSelectC`;
- uncertainty-aware `select`;
- uncertainty-aware `exists`;
- uncertainty-aware `forAll`;
- custom query expression classes;
- uncertain result aggregation;
- uncertain collection values or operations;
- empty and singleton collections;
- evaluation order;
- short-circuit behavior;
- undefined propagation.

Check that current USE typing, collection dispatch, and evaluator behavior are preserved.

Flag a custom class if the grammar or evaluator cannot reach it.

Flag a standard-operation rewrite if it changes evaluation semantics from the historical implementation.

## 6. Review parser and AST integration

Inspect the grammar sources and generated parser output.

Verify:

- all supported uncertain literals parse;
- all uncertainty-specific expressions parse;
- malformed syntax is rejected;
- parser AST nodes use current conventions;
- generated Java files were regenerated rather than copied;
- current parser-generation commands reproduce the checked-in artifacts where applicable;
- no stale historical lexer or parser code remains;
- ordinary OCL syntax is unaffected;
- error locations and messages remain reasonable;
- parser tests cover positive and negative cases.

Check for grammar ambiguity or syntax that parses but constructs the wrong expression type.

## 7. Review current USE architectural integration

Inspect integration with:

- `TypeFactory`;
- the current type hierarchy;
- the current `Value` hierarchy;
- `ExpStdOp`;
- operation lookup;
- overload resolution;
- AST generation;
- evaluator dispatch;
- undefined values;
- value comparison;
- collection typing;
- rendering;
- Maven modules;
- Java version requirements;
- test conventions.

Determine whether the implementation uses the smallest correct set of core changes.

Flag:

- plugin mechanisms used where core registration is required;
- broad changes to system state without a verified need;
- GUI or shell changes unrelated to uncertain OCL;
- duplicated operation registries;
- dependencies that bypass current project conventions;
- opaque binary dependencies;
- unnecessary changes to upstream behavior.

## 8. Review historical defect handling

Verify how the port handled:

- the apparently inverted `ExpDefSBoolean` type check;
- unreachable `ExpDefSBoolean` or AST code;
- `SBooleanValue.compareTo()` returning zero;
- numeric conversion through formatted strings;
- broad exception handling;
- malformed opinions;
- six-decimal internal rounding;
- mass validation tolerance;
- normalization;
- floating-point drift;
- invalid base rates;
- misleading naming of the base-rate coordinate as an agent;
- binary nesting of n-ary fusion;
- division-by-zero;
- vacuous and dogmatic opinions.

For each issue, report whether it was:

- correctly preserved for compatibility;
- correctly fixed;
- incorrectly changed;
- left ambiguous;
- insufficiently tested.

Do not require preservation of a demonstrable defect merely for literal source compatibility.

Do require documentation and tests for intentional behavioral changes.

## 9. Review numeric policy

Determine whether the implementation has one coherent policy for:

- valid opinion coordinates;
- `b + d + u = 1`;
- tolerance;
- normalization;
- internal precision;
- display rounding;
- equality;
- invalid results;
- base rates;
- division by zero;
- floating-point drift;
- degenerate fusion.

Check whether the policy is consistently applied across:

- constructors;
- parser literals;
- operations;
- fusion;
- rendering;
- equality;
- tests.

Flag hidden rounding, inconsistent tolerances, or automatic normalization that changes semantic results without documentation.

## 10. Recalculate golden examples

Independently recalculate and test these cases against the implementation.

### Direct trust discounting

Input opinion:

`(0.8, 0.05, 0.15, 0.2)`

Trust probability:

`0.98`

Expected result, approximately:

`(0.784, 0.049, 0.167, 0.2)`

### Evidence-derived trust

Evidence:

- positive `47`;
- negative `3`;
- prior weight `2`;
- base rate `1`.

Expected projected trust probability, approximately:

`0.9423076923076923`

### Evidence-based discounting

Input opinion:

`(0.65, 0.10, 0.25, 0.2)`

Expected discounted result, approximately:

`(0.6125, 0.09423076923076923, 0.29326923076923084, 0.2)`

### Aleatory cumulative fusion

Expected result, approximately:

`(0.8077218903786134, 0.07320015429753708, 0.11907795532384946, 0.2)`

Expected projected probability, approximately:

`0.8315374814433834`

### Genuine n-ary averaging

Verify a three-opinion case where:

- genuine n-ary averaging has belief `0.4`;
- nested binary averaging has belief `0.45`.

Confirm that the implementation calls a genuine n-ary operation.

If a value differs, determine whether the cause is:

- a defect;
- a different verified historical formula;
- base-rate treatment;
- rounding;
- normalization;
- an incorrect test assumption.

Do not treat implementations with shared lineage as independent confirmation.

## 11. Review test completeness and quality

Map every historical type, literal, expression, and operation to tests.

Verify coverage of:

- valid inputs;
- invalid inputs;
- undefined values;
- parser success;
- parser failure;
- evaluator behavior;
- overload resolution;
- mixed certain and uncertain operands;
- edge cases;
- empty collections;
- singleton collections;
- n-ary inputs;
- numerical degeneracy;
- historical defects;
- ordinary OCL regression;
- end-to-end USE execution.

Flag tests that:

- only test helper libraries without exercising USE;
- reproduce implementation formulas instead of independently checking results;
- use overly loose tolerances;
- validate only binary forms of n-ary operations;
- assert only that evaluation does not throw;
- omit result types;
- omit parser-to-evaluator integration;
- depend on iteration order accidentally.

Run the smallest relevant tests first, then the complete test suite.

Report exact commands and results.

## 12. Review build and dependency quality

Verify:

- the project builds with its declared Java version;
- parser generation is reproducible;
- clean builds work;
- test dependencies follow current conventions;
- no historical binary JAR was copied without justification;
- library licensing and provenance are documented;
- no machine-specific paths were added;
- no Windows-only or WSL-only assumptions entered the build;
- no generated or build artifacts were accidentally tracked.

## Finding severity

Classify findings as:

- `P0`: unsafe or fundamentally invalid port;
- `P1`: missing major historical functionality, incorrect semantics, parser breakage, or widespread regression;
- `P2`: localized functional defect, important edge case, or substantial test gap;
- `P3`: maintainability, documentation, or minor coverage issue.

For every finding provide:

- severity;
- concise title;
- current source path and line;
- historical source or expected behavior;
- evidence;
- user-visible impact;
- precise recommended correction;
- test that should detect the correction.

Do not report speculative findings without evidence.

## Required final report

Return:

1. review baseline;
2. build and test results;
3. findings ordered by severity;
4. complete feature-parity matrix;
5. complete type and operation matrix;
6. `SBoolean` algebra review;
7. collection and query review;
8. parser and AST review;
9. source-level port-map audit;
10. omitted-file audit;
11. redundant-file audit;
12. architectural integration assessment;
13. historical-defect disposition;
14. numeric-policy assessment;
15. golden-example results;
16. test-coverage matrix;
17. dependency and reproducibility assessment;
18. ordinary OCL regression assessment;
19. required fixes;
20. optional improvements;
21. final readiness verdict.

End with exactly one of:

- `PORT ACCEPTED`
- `PORT ACCEPTED WITH MINOR FIXES`
- `PORT INCOMPLETE`
- `PORT REJECTED`

A port cannot be accepted if any reachable historical uncertain-OCL or subjective-Boolean feature is missing without a justified replacement or explicit compatibility decision.

Do not implement fixes during this review.