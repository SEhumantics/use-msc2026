# Uncertainty operation coverage

This matrix records the historical USE operation names and the current native
implementation/test that covers each family. The historical deprecated binary
fusion aliases (`minimumFusion`, `majorityFusion`, `averageFusion`,
`weightedFusion`, `cumulativeFusion`, and `epistemicCumulativeFusion`) were
commented out in the historical USE registration and are intentionally not
registered.

| Historical operation family | Current implementation | Coverage |
| --- | --- | --- |
| `UReal`: `value`, `uncertainty`, `setValue`, `setUncertainty`, `abs`, `inv`, `neg`, `min`, `max`, `floor`, `round`, `toReal`, `toInteger`, `toUInteger`, `power`, `sqrt`, `atan`, `sin`, `cos`, `tan`, `asin`, `acos` | `StandardOperationsUncertainty`, `StandardOperationsNumber`, `URealValue` | `UncertainScalarOperationsTest`, `UncertaintyQueryEvaluationTest` |
| `UInteger`: `value`, `uncertainty`, `setValue`, `setUncertainty`, `abs`, `neg`, `min`, `max`, `div`, `mod`, `sqrt`, `power`, `toReal`, `toUReal` | `StandardOperationsUncertainty`, `UIntegerValue` | `UncertainScalarOperationsTest`, `UncertaintyQueryEvaluationTest` |
| `UString`: `value`, `confidence`, `setValue`, `setConfidence`, `at`, `character`, `+`, `indexOf`, `substring`, `toLowerCase`, `toUpperCase`, `size`, `toString`, `toInteger`, `toReal`, `toBoolean`, `toUBoolean`, `<`, `<=`, `>`, `>=` | `StandardOperationsUncertainty`, `UStringValue` | `UncertainScalarOperationsTest`, `UncertaintyQueryEvaluationTest` |
| `UBoolean`: `value`, `confidence`, `setValue`, `setConfidence`, `toBoolean`, `toBooleanC`, `toString`, `equalsC`, `and`, `or`, `not`, `implies`, `xor`, `equivalent` | `StandardOperationsUncertainty`, `UBooleanValue` | `UncertainScalarOperationsTest`, `UncertaintyQueryEvaluationTest` |
| `SBoolean`: accessors, predicates, projections, logical operators, `deduceY`, `applyOn`, `min`, `max`, `discount` | `StandardOperationsUncertainty`, `SBooleanValue` | `SBooleanValueTest`, `UncertaintyQueryEvaluationTest` |
| `SBoolean` collection fusion: minimum, majority, belief-constraint, average, aleatory cumulative, epistemic cumulative, weighted, consensus-and-compromise | `SBooleanValue` algebra and collection operation registration | `SBooleanValueTest`, `UncertaintyQueryEvaluationTest` |
| Collection uncertainty: `includes`, `excludes`, `includesAll`, `excludesAll`, `uCount`, `uCountC`, uncertain `sum` aggregation | `CollectionValue`, `StandardOperationsCollection` | `UncertaintyQueryEvaluationTest`, `UncertaintyOperationRegistrationTest` |
| Query uncertainty: `uSelect`, `uSelectC`, uncertain `exists`, uncertain `forAll` | `ExpUSelect`, `ExpUSelectC`, `ExpQuery`, parser AST/grammar | `UncertaintyParserTest`, `UncertaintyQueryEvaluationTest` |
| Equality: `=`, `<>`, `equals` lifted to `UBoolean`, or to `SBoolean` when a subjective opinion is involved | `StandardOperationsAny`, `UncertainBooleanValue` | `UncertaintyCompilerCorpusTest`, `UncertaintyQueryEvaluationTest` |
| Mixed numeric dispatch: an uncertain operand yields `UInteger` or `UReal` following the least common supertype | `StandardOperationsNumber`, `StandardOperationsUncertainty` | `UncertaintyCompilerCorpusTest`, `UncertaintyOperationRegistrationTest` |
| Literals: argument type diagnostics, malformed input, uncertain built-ins as type names | `ExpConstUncertain`, `ASTUncertainLiteral`, `grammars/base/OCLBase.gpart` | `UncertaintyCompilerCorpusTest`, `UncertaintyParserTest` |
| Undefined handling: non-finite results, undefined operands of the logical operators | `StandardOperationsUncertainty`, `VoidType` | `UncertaintyCompilerCorpusTest`, `UncertaintyQueryEvaluationTest` |

Operation name registration is checked against the complete exposed historical
name set by `UncertaintyOperationRegistrationTest`. The evaluator tests also
verify that uncertain overloads win over generic numeric overloads.

## Runtime behaviour against the historical corpus

Name-level registration is not the acceptance criterion. `UncertaintyCompilerCorpusTest`
replays the historical `USECompilerUncertaintyTest` corpus — all 1427 entries of
`URealExpression.in`, `UIntegerExpression.in`, `UBooleanExpression.in` and
`UCollectionOperations.in` — through the current compiler and evaluator and
compares the rendered result with the historical expectation.

1421 of the 1427 entries reproduce the historical behaviour. The six that do not
are listed, with their causes, in `known-divergences.txt` next to the corpus;
the test treats that list as an exact contract in both directions, so a new
divergence fails as a regression and an entry that starts agreeing has to be
removed. All six have been traced to causes outside the uncertainty semantics
(collect-shorthand expansion of `.equals` on a collection receiver, and
multi-variable `exists` being broken in the USE baseline for ordinary Boolean
queries too), and the behaviour each was asserting is covered directly by
`UncertaintyQueryEvaluationTest`.

Two historical renderings are deliberately not reproduced because they belong to
the modern USE baseline rather than to uncertainty: the undefined value renders
as `null` rather than `Undefined`, and compiler diagnostics use current wording.
