# Uncertainty operation coverage

This matrix records the historical USE operation names and the current native
implementation/test that covers each family. The historical deprecated binary
fusion aliases (`minimumFusion`, `majorityFusion`, `averageFusion`,
`weightedFusion`, `cumulativeFusion`, and `epistemicCumulativeFusion`) were
commented out in the historical USE registration and are intentionally not
registered.

| Historical operation family | Current implementation | Coverage |
| --- | --- | --- |
| `UReal`: `value`, `uncertainty`, `setValue`, `setUncertainty`, `abs`, `inv`, `neg`, `toReal`, `toInteger`, `toUInteger`, `power`, `sqrt`, `atan`, `sin`, `cos`, `tan`, `asin`, `acos` | `StandardOperationsUncertainty`, `URealValue` | `UncertainScalarOperationsTest`, `UncertaintyQueryEvaluationTest` |
| `UInteger`: `value`, `uncertainty`, `setValue`, `setUncertainty`, `abs`, `neg`, `div`, `mod`, `sqrt`, `power`, `toReal`, `toUReal` | `StandardOperationsUncertainty`, `UIntegerValue` | `UncertainScalarOperationsTest`, `UncertaintyQueryEvaluationTest` |
| `UString`: `value`, `confidence`, `setValue`, `setConfidence`, `at`, `character`, `+`, `indexOf`, `substring`, `toLowerCase`, `toUpperCase`, `size`, `toString`, `toInteger`, `toReal`, `toBoolean`, `toUBoolean`, `<`, `<=`, `>`, `>=` | `StandardOperationsUncertainty`, `UStringValue` | `UncertainScalarOperationsTest`, `UncertaintyQueryEvaluationTest` |
| `UBoolean`: `value`, `confidence`, `setValue`, `setConfidence`, `toBoolean`, `toBooleanC`, `toString`, `equalsC`, `and`, `or`, `not`, `implies`, `xor`, `equivalent` | `StandardOperationsUncertainty`, `UBooleanValue` | `UncertainScalarOperationsTest`, `UncertaintyQueryEvaluationTest` |
| `SBoolean`: accessors, predicates, projections, logical operators, `deduceY`, `applyOn`, `min`, `max`, `discount` | `StandardOperationsUncertainty`, `SBooleanValue` | `SBooleanValueTest`, `UncertaintyQueryEvaluationTest` |
| `SBoolean` collection fusion: minimum, majority, belief-constraint, average, aleatory cumulative, epistemic cumulative, weighted, consensus-and-compromise | `SBooleanValue` algebra and collection operation registration | `SBooleanValueTest`, `UncertaintyQueryEvaluationTest` |
| Collection uncertainty: `includes`, `excludes`, `includesAll`, `excludesAll`, `uCount`, `uCountC`, uncertain `sum` aggregation | `CollectionValue`, `StandardOperationsCollection` | `UncertaintyQueryEvaluationTest`, `UncertaintyOperationRegistrationTest` |
| Query uncertainty: `uSelect`, `uSelectC`, uncertain `exists`, uncertain `forAll` | `ExpUSelect`, `ExpUSelectC`, `ExpQuery`, parser AST/grammar | `UncertaintyParserTest`, `UncertaintyQueryEvaluationTest` |

Operation name registration is checked against the complete exposed historical
name set by `UncertaintyOperationRegistrationTest`. The evaluator tests also
verify that uncertain overloads win over generic numeric overloads.
