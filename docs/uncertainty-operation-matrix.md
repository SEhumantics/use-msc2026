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

1422 of the 1427 entries reproduce the historical behaviour. The five that do
not are listed, with their causes, in `known-divergences.txt` next to the
corpus; the test treats that list as an exact contract in both directions, so a
new divergence fails as a regression and an entry that starts agreeing has to be
removed. All five state a collection identity through `.equals` on a collection
receiver, which the compiler expands to the collect shorthand, so they could not
yield the single Boolean they expect in the historical build either. The
identities themselves are asserted with `=` in
`UncertaintyQueryEvaluationTest`.

Two historical renderings are deliberately not reproduced because they belong to
the modern USE baseline rather than to uncertainty: the undefined value renders
as `null` rather than `Undefined`, and compiler diagnostics use current wording.

The corpus does not reach `UString` or `SBoolean`. Their signatures — including
the receiver-plus-collection shape of the fusion operations — are asserted by
`UncertaintyOperationRegistrationTest`, and their behaviour by the replayed
oracles below.

## Historical unit-test oracles

Beyond the corpus, the historical JUnit oracles are replayed too, ported with
their expectations untouched — only the JUnit 3 scaffolding and the per-kind
literal constructors the port replaced with one `ExpConstUncertain` were
rewritten:

| Oracle | Assertions |
| --- | --- |
| `URealExpOpsTest`, `UIntegerExpOpsTest`, `UBooleanExpOpsTest` | 898 |
| `URealValueTest`, `UIntegerValueTest`, `UBooleanValueTest` | 45 |
| `UCollectionExpOpTest`, `ExpQueryUncertaintyTest` | 22 |
| `SBooleanHistoricalAlgebraTest`, from uDataTypes `SBooleanTest`/`SBooleanTest3` | 9 tests |

They found three defects, all in operations the corpus never reaches: `implies`
absorbing from either side, `toBooleanC` yielding undefined for a confidence
outside [0,1], and `equalsC` accepting a plain Boolean second operand.
`UncertaintyUncoveredOperationsTest` additionally pins `equalsC`,
`setConfidence`, `implies` and the UString surface, none of which the corpus
exercises.

Uncertain values are also exercised through the model and SOIL paths by
`UncertainModelValidationTest`: uncertain attribute types, assignment by SOIL
statement, and Boolean invariants over uncertain attributes.

## Test execution

The project runs the JUnit 5 platform with no vintage engine, so the JUnit 3 and
4 classes the port inherited were never executing; `USECompilerTest`, which owns
the `test_expr.in` corpus, was among them. They are all migrated, and 61 test
classes with 488 tests now run. `TypeTest.testSupertype` carries the historical
uncertainty branch's own expectations for the widened type lattice, in which
`Boolean` gains `UBoolean` and `SBoolean` as supertypes and `Integer` gains
`UInteger` and `UReal`.

Five historical declarations are deliberately corrected. Each declares one result
type in `matches` while its `eval` returns another, so the historical declaration
would mistype every expression built on the result:

| Operation | Historically declared | Actually returned | Port declares |
| --- | --- | --- | --- |
| `UString::indexOf` | `UString` | `IntegerValue` | `Integer` |
| `UString::toString` | `UString` | `StringValue` | `String` |
| `UInteger::value` | `UInteger` | `IntegerValue` | `Integer` |
| `SBoolean::conjunctiveCertainty` | `SBoolean` | `RealValue` | `Real` |
| `SBoolean::degreeOfConflict` | `SBoolean` | `RealValue` | `Real` |

`deduceY` carries one further intentional correction. The historical
implementation writes the resulting coordinates straight onto a fresh opinion
without passing the validating constructor, so a degenerate branch that makes
its correction term `NaN` yields `(0, 0, 0, a)` — an opinion whose masses sum to
zero. Roughly 0.4% of inputs reach that branch. The port has no way to represent
such a value and evaluates to undefined instead.

## Historical to current source map

Every uncertainty-related file of the historical fork
(`.git/reference-repositories/uncertainty`), and what became of it. Paths under
`USE-Uncertainty/src/main/` unless stated otherwise.

| Historical | Current | Disposition |
| --- | --- | --- |
| `parser/ocl/ASTURealLiteral`, `ASTUIntegerLiteral`, `ASTUStringLiteral`, `ASTUBooleanLiteral`, `ASTSBooleanLiteral` | `parser/ocl/ASTUncertainLiteral` | REWRITE, 5 into 1 |
| `uml/ocl/expr/ExpConstUReal`, `ExpConstUInteger`, `ExpConstUString`, `ExpConstUBoolean`, `ExpConstSBoolean` | `uml/ocl/expr/ExpConstUncertain` | REWRITE, 5 into 1 |
| `parser/ocl/ASTSBooleanDefExpression` | — | OMIT, unreachable |
| `uml/ocl/expr/ExpDefSBoolean` | — | OMIT, unreachable and its guard is inverted |
| `uml/ocl/expr/ExpUSelect`, `ExpUSelectC` | same names | PORT |
| `uml/ocl/expr/ExpQuery`, `ExpExists`, `ExpForAll` | same names | REWRITE, see below |
| `parser/base/ParserHelper`, `parser/ocl/ASTQueryExpression` | same names | PORT |
| `parser/base/OCLBase.gpart` | `resources/grammars/base/OCLBase.gpart` | PORT, relocated |
| `uml/ocl/type/URealType`, `UIntegerType`, `UStringType`, `UBooleanType`, `SBooleanType`, `UncertainType` | same names | PORT |
| `uml/ocl/type/UncertainBooleanType` | — | OMIT, carried no behaviour |
| `uml/ocl/type/Type`, `TypeImpl`, `TypeFactory`, `BooleanType`, `IntegerType`, `RealType`, `StringType`, `VoidType` | same names | REWRITE, minimal |
| `uml/ocl/value/URealValue`, `UIntegerValue`, `UStringValue`, `UBooleanValue`, `SBooleanValue`, `UncertainValue`, `UncertainBooleanValue` | same names | REWRITE, native |
| `uml/ocl/expr/operations/StandardOperationsUReal`, `UInteger`, `UString`, `UBoolean`, `SBoolean` (3149 lines) | `StandardOperationsUncertainty` | REWRITE, 5 into 1 |
| `uml/ocl/expr/operations/StandardOperationsAny`, `StandardOperationsCollection`, `StandardOperationsNumber` | same names | REWRITE, minimal |
| `lib/atenearesearchgroup.uncertainty.jar`, `..._old.jar` (the `uDataTypes` library) | `uml/ocl/value/*` | REWRITE, no binary dependency |
| `src/test/.../uncertainty/{UReal,UInteger,UBoolean}Expression.in`, `UCollectionOperations.in` | `use-core/src/test/resources/org/tzi/use/parser/uncertainty/` | PORT, verbatim |
| `src/test/.../USECompilerUncertaintyTest` | `parser/UncertaintyCompilerCorpusTest` | REWRITE |
| `src/test/.../{URealExpOpsTest, UIntegerExpOpsTest, UBooleanExpOpsTest, ExpQueryUncertaintyTest, URealValueTest, UIntegerValueTest, UBooleanValueTest}` | same names | PORT, JUnit 5 only |
| `uDataTypes/.../SBooleanTest`, `SBooleanTest3` | `SBooleanHistoricalAlgebraTest` | PORT, oracle replay |
| `uDataTypes` `UEnum`, `UUnlimitedNatural`, `Distribution`, `N_U*`, the case studies, and the MagicDraw, Python and OCLTypes libraries | — | OMIT, never registered in the historical USE fork |

`ExpQuery` is the one rewrite worth calling out: the historical
`exists`/`forAll` accumulate by building an `ExpStdOp` for `or`/`and` on every
iteration and evaluating it. The port folds `UBooleanValue` directly. It also
declares the result type `UBoolean` when the predicate is uncertain, where the
historical constructors always declared `Boolean` while `eval` could return a
`UBooleanValue`.

## Changes to ordinary OCL

Two of the historical fork's changes reach expressions with no uncertain
operand at all, and both are ported deliberately:

- `equals` is registered on `OclAny` as the certain counterpart of `=`, so
  `1.equals(1)` now compiles. Upstream USE has no such operation; the historical
  fork does (`StandardOperationsAny.Op_identical`) and the corpus uses it.
- `Boolean`, `Integer`, `Real` and `String` gain their uncertain counterparts as
  supertypes. Least-common-supertype results for pairs of certain types are
  unaffected -- `Integer` and `Real` still meet at `Real`, because `Real`
  conforms to `UReal` and not the other way round.

## Omitted historical classes

`ExpDefSBoolean` and `ASTSBooleanDefExpression` are not ported. No grammar
production constructs `ASTSBooleanDefExpression` in the historical fork, so the
expression was unreachable, and `ExpDefSBoolean`'s own guard is inverted — it
rejects exactly the operand types its message says it expects. `SBoolean(expr)`
is therefore not part of the historically reachable surface.

`UncertainBooleanType` is likewise not ported as a class: it carried no
behaviour beyond the `isKindOfSBoolean` relation, which `UBooleanType` and
`SBooleanType` now state directly.

## Extensions beyond the historical surface

`uIncludes` and `uExcludes` are registered as operation names, which the
historical fork did not do — there they existed only as `CollectionValue`
methods reached through the uncertain branch of `includes`/`excludes`. Those
uncertain branches are ported faithfully; the two extra names are an addition.

## Known departures kept deliberately

Two review passes have been run against the port. The first confirmed ten
findings, six of which were defects and are fixed. The second compared the port
directly against the historical `uDataTypes` library over randomised inputs and
against the historically registered signatures, and found nine more; all nine
are fixed. What remains below is left alone deliberately, because changing it
would depart from the historical implementation rather than restore it:

- `Boolean`/`Integer`/`Real`/`String` conform to their uncertain counterparts,
  so a certain value can be stored into an uncertain-typed attribute with no
  conversion. The historical type lattice has the same hole.
- The five uncertain built-in names are grammar keywords and so cannot be used
  as ordinary identifiers. The historical grammar is identical.
- `uIncludesAll` short-circuits to false when the argument holds more elements
  than the receiver, which is wrong for arguments with duplicates. Byte-for-byte
  the historical implementation.
- `UString::indexOf` is 0-based while `String::indexOf` is 1-based. Historical,
  and pinned by the replayed historical assertions.
- `uSelectC` raises an unchecked exception when its confidence argument falls
  outside [0,1] rather than evaluating to undefined. The historical
  implementation raises there too, and `ExpQuery` already reports other
  evaluator-internal violations the same way.
- Opinion coordinates keep full double precision instead of the historical
  six-decimal rounding, so a derived opinion can differ from the historical one
  in the sixth decimal. Only the comparisons that historically broke ties on the
  rounded value — `min`, `max` and `minimumBeliefFusion` — round for that
  purpose.
- The fourth `SBoolean` literal argument is the base rate, but the historical
  diagnostic for a wrongly typed one calls it `Agent`. The message is kept
  verbatim so the diagnostics stay recognisable, even though the name is
  misleading; the coordinate is named `baseRate` everywhere in the code.
- A fusion or `discount` argument whose element type is not kind of `SBoolean`
  is a compile error, where the historical matcher accepted any collection and
  failed in the evaluator. The empty literal `Set{}` is still accepted, as it was
  historically, and the fusion reports an undefined result.

## Ordering of uncertain values

`uEquals` is the uncertain equality that OCL `=` evaluates: it asks whether two
distributions overlap, and it is unchanged. It cannot also serve as the ordering
used to sort a collection, because overlap is not transitive — `UReal(0,3)`
overlaps `UReal(3,3)` and `UReal(3,3)` overlaps `UReal(6,3)`, but `UReal(0,3)`
and `UReal(6,3)` do not.

Every collection rendering goes through `CollectionValue.getSortedElements()`,
which calls `Collections.sort`, so an intransitive `compareTo` made TimSort
throw `IllegalArgumentException: Comparison method violates its general
contract!` — reachable from an expression as ordinary as
`Sequence{...}->asSet()` over enough uncertain values. `UBoolean` had the same
defect against plain `Boolean`, by ordering on truth where `BooleanValue`
orders on rendering.

`compareTo` therefore orders by representation:

- `UReal` and `UInteger` order together with `Real` and `Integer`, by value and
  then by uncertainty, a certain number counting as uncertainty 0. `RealValue`
  and `IntegerValue` delegate to that side for an uncertain operand so both
  directions agree.
- `UBoolean` orders by probability; `UString` by spelling.
- Two `SBoolean`s tie, leaving opinions in place rather than inventing an order
  for them. Against any other kind an opinion orders by rendering, like the rest
  of the hierarchy. Tying there as well would not be antisymmetric, because the
  other kinds do not tie back.

That gives every kind a total order among its own values, but not across kinds:
the numeric kinds compare by value, so `UReal(1,0)` ties with `1` although the
two render differently, and against a third kind — which every kind falls back
to comparing by rendering — the two then disagree. A collection typed `OclAny`
can hold such a mixture, so `getSortedElements()` groups by kind before ordering
within a group, with the numeric kinds forming one group so that certain and
uncertain numbers stay interleaved in numeric order. That is total whatever the
collection holds, and a collection whose elements are all of one kind — every
ordinary one — sorts exactly as `compareTo` alone would.

One historical corpus rendering is not reproducible under any valid comparator
and is recorded in `known-divergences.txt`; the set contents agree, only the
order differs. `UncertainValueOrderingTest` pins the contract per kind, across
kinds, and for the mixed-kind rendering path.
