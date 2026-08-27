package org.tzi.use.smt.verify;

import java.util.List;

/**
 * The correctness-contract obligations of {@code
 * output/archive2/robust_utype_model_finding_proposal.md} 7 "Correctness strategy", each pointing
 * at the test that actually discharges it.
 *
 * <p>Why this exists at all. 7 states seven claims and a list of proof obligations in prose, and
 * several of them were ALREADY tested by Milestones 4.3-4.6 -- but only someone who had read all of
 * those tests could say which claim was covered and which was not. A prose contract whose coverage
 * cannot be enumerated is not a contract. This enum makes the mapping a first-class, discoverable
 * artefact, and {@code CorrectnessObligationRegistryTest} resolves every {@link #discharges()}
 * entry by reflection, so renaming or deleting a discharging test breaks the build instead of
 * silently un-covering a claim.
 *
 * <p>It deliberately does NOT duplicate the tests it names. Wiring an existing test in is the
 * point; re-asserting the same property somewhere else would give two things to keep in step and no
 * extra evidence.
 *
 * <p><b>What is NOT here, and why.</b> 7's proof-obligation list also names blocking-clause
 * distinctness and finite termination for {@code ENUMERATE}. This slice has no {@code ENUMERATE}
 * wrapper at all, so an obligation for it would be an assertion about code that does not exist. The
 * variance-form equivalence for affine numeric operations is likewise absent: only the {@code
 * UReal} threshold family is translated today (see {@code FragmentBoundary#UTYPE_CORE}), and affine
 * arithmetic over U-values is not yet in the fragment.
 */
public enum CorrectnessObligation {

  /**
   * Claim 1: "every accepted expression produces the intended Z3 sort together with an explicit
   * definedness condition."
   */
  TYPE_AND_DEFINEDNESS_PRESERVATION(
      "every accepted expression produces the intended Z3 sort together with an explicit"
          + " definedness condition",
      List.of(
          "org.tzi.use.smt.encode.TypeAndDefinednessPreservationTest"
              + "#everyAcceptedExpressionIsWellSortedBooleanToTheRealSolver",
          "org.tzi.use.smt.encode.TypeAndDefinednessPreservationTest"
              + "#definednessIsAnExplicitTermAndNotConstantTrueWhereTheSourceCanBeUndefined",
          "org.tzi.use.smt.encode.TypeAndDefinednessPreservationTest"
              + "#atranslatedExpressionCannotExistWithoutADefinednessTerm")),

  /**
   * Claim 2: "for a supported ground expression and fixed scenario, its encoded definedness and
   * value agree with the current USE evaluator outside the documented numerical boundary band."
   */
  EXPRESSION_AGREEMENT(
      "encoded definedness and value agree with the current USE evaluator outside the documented"
          + " numerical boundary band",
      List.of(
          "org.tzi.use.smt.finder.ExpressionAgreementDifferentialTest"
              + "#generatedGroundThresholdsAgreeWithTheUseEvaluatorOutsideTheDocumentedBand",
          "org.tzi.use.smt.finder.ExpressionAgreementDifferentialTest"
              + "#theEncodingNeverClaimsTrueWhereTheUseEvaluatorSaysFalse",
          "org.tzi.use.smt.finder.ExpressionAgreementDifferentialTest"
              + "#theGeneratorsScopeIsBoundedByWhatIsActuallyTranslated")),

  /**
   * Claim 3: "every delivered solver assignment reconstructs to evidence that satisfies the
   * requested W_Q under USE re-evaluation." Discharged by Milestones 4.3-4.4's oracle and its
   * negative paths -- wired in, not duplicated.
   */
  QUERY_WITNESS_SOUNDNESS(
      "every delivered solver assignment reconstructs to evidence that satisfies the requested"
          + " witness predicate under USE re-evaluation",
      List.of(
          "org.tzi.use.smt.verify.QueryWitnessCheckerTest#aWitnessMatchingEveryClaimedAtomIsAccepted",
          "org.tzi.use.smt.verify.QueryWitnessCheckerTest#aFalseWhereTheQueryClaimedTrueIsRejected",
          "org.tzi.use.smt.verify.QueryWitnessCheckerTest"
              + "#anInvariantAbsentFromTheOracleVerdictsIsRejected",
          "org.tzi.use.smt.verify.QueryWitnessCheckerTest"
              + "#anAtomInAModeTheOracleNeverReportedOnIsRefusedEvenUnderNegation")),

  /**
   * Claim 4: "a delivered counterexample makes exactly the selected target false while every other
   * active invariant is true; undefined is not accepted as violation." Milestone 4.3.
   */
  COUNTEREXAMPLE_ATTRIBUTION(
      "a delivered counterexample makes exactly the selected target false while every other active"
          + " invariant is true; undefined is not accepted as violation",
      List.of(
          "org.tzi.use.smt.finder.CounterexampleQueryTest"
              + "#targetedCounterexampleIsolatesExactlyTheTargetAsDefinedFalse",
          "org.tzi.use.smt.finder.CounterexampleQueryTest"
              + "#anUndefinedTargetIsRejectedRatherThanReportedAsAViolation",
          "org.tzi.use.smt.finder.CounterexampleQueryTest"
              + "#aFalseNonTargetPreventsAttributionToTheTarget",
          "org.tzi.use.smt.verify.QueryWitnessCheckerTest"
              + "#anUndefinedTargetDoesNotSatisfyADefinedFalseClaim")),

  /**
   * Claim 5 and its named implication W_FRAGILE(j) => W_CEX(j). Milestone 4.5 already proves this
   * end to end over a real reconstructed witness.
   */
  FRAGILE_IMPLICATION_AND_ATTRIBUTION(
      "a delivered fragile witness additionally makes the selected invariant nominally true, and is"
          + " therefore a counterexample with a demonstrated nominal/U-aware discrepancy",
      List.of(
          "org.tzi.use.smt.finder.FragileQueryTest#everyFragileWitnessIsAlsoACounterexample",
          "org.tzi.use.smt.finder.FragileQueryTest"
              + "#fragileFindsTheNominalErasureWitnessThatSatisfyCorrectlyRejects",
          "org.tzi.use.smt.finder.FragileQueryTest#anUndefinedTargetIsNotFragility",
          "org.tzi.use.smt.finder.FragileQueryTest#aFalseNonTargetPreventsFragileAttribution")),

  /**
   * Claim 6: "COVER checks every finite scenario, UNIFORM shares one snapshot across them".
   * Milestone 4.6's scenario-sharing evidence.
   */
  SCENARIO_PROFILE_AND_SHARING(
      "COVER checks every finite scenario and UNIFORM shares ONE snapshot across them; no profile"
          + " is accepted by silently running a weaker one",
      List.of(
          "org.tzi.use.smt.finder.ScenarioProfileTest"
              + "#uniformSharesOneSnapshotAcrossEveryScenarioAndIsCheckedInEachOne",
          "org.tzi.use.smt.finder.ScenarioProfileTest"
              + "#coverSucceedsWithADIFFERENTSnapshotPerScenarioWhereUniformIsRefuted",
          "org.tzi.use.smt.finder.ScenarioProfileTest#existsSucceedsWhereCoverAndUniformAreBothRefuted",
          "org.tzi.use.smt.finder.ScenarioProfileTest"
              + "#uniformIsRefutedWhenTheSharedSnapshotFailsASingleScenario")),

  /**
   * Claim 7: "exact encodings are complete only for the configured scopes and value domains [...]
   * so UNSAT is never presented as an unbounded or numerically exact theorem."
   *
   * <p>This is deliberately NOT an independent UNSAT oracle, and building one is out of scope: 7
   * closes by stating that a successful post-validation "cannot strengthen the deliberately
   * qualified meaning of UNSAT". What is executable is that the qualification is ATTACHED and
   * matches the configuration that produced it.
   */
  QUALIFIED_BOUNDED_COMPLETENESS(
      "UNSAT is never presented as an unbounded or numerically exact theorem: every result carries"
          + " the scopes, domains, scenario policy and numerical policy that qualify it",
      List.of(
          "org.tzi.use.smt.finder.BoundedCompletenessQualificationTest"
              + "#everyResultIncludingARefutationCarriesItsQualification",
          "org.tzi.use.smt.finder.BoundedCompletenessQualificationTest"
              + "#theQualificationRepeatsTheConfiguredScopesDomainsAndScenarioPolicy",
          "org.tzi.use.smt.finder.BoundedCompletenessQualificationTest"
              + "#theStatementQualifiesRefutationRatherThanClaimingATheorem",
          "org.tzi.use.smt.finder.BoundedCompletenessQualificationTest"
              + "#aProfiledRefutationNamesEveryScenarioItWasQualifiedOver",
          "org.tzi.use.smt.encode.TypeAndDefinednessPreservationTest"
              + "#theDeclaredNumericalPolicyMatchesTheEncodingItDescribes")),

  /** Proof obligation: "bounded quantifiers ranging over exactly the alive object slots." */
  ALIVE_SLOT_QUANTIFIER_RANGE(
      "bounded quantifiers range over exactly the alive object slots",
      List.of(
          "org.tzi.use.smt.encode.AliveSlotQuantifierRangeTest"
              + "#anEmptyAliveRangeIsVacuouslyTrueUniversallyAndFalseExistentially",
          "org.tzi.use.smt.encode.AliveSlotQuantifierRangeTest"
              + "#aDeadSlotsViolatingAttributeValueCannotFalsifyTheInvariant",
          "org.tzi.use.smt.encode.AliveSlotQuantifierRangeTest"
              + "#thatSameViolatingValueDoesFalsifyTheInvariantOnceItsSlotIsAlive")),

  /** Proof obligation: "reconstruction producing a snapshot within configured scopes." */
  RECONSTRUCTION_WITHIN_SCOPE(
      "reconstruction produces a snapshot within the configured class scopes, association scopes"
          + " and attribute domains",
      List.of(
          "org.tzi.use.smt.finder.ReconstructionWithinScopeTest"
              + "#everyLibraryWitnessRespectsItsConfiguredScopesAndDomains",
          "org.tzi.use.smt.finder.ReconstructionWithinScopeTest"
              + "#everyURealWitnessRespectsItsConfiguredComponentDomains",
          "org.tzi.use.smt.finder.ReconstructionWithinScopeTest"
              + "#aWitnessOutsideItsConfiguredScopeWouldBeCaught")),

  /**
   * Milestone 4.7's own definition of done: "every active invariant referenced directly or by an
   * aggregate is accounted for before solving".
   */
  FRAGMENT_LEDGER_COMPLETENESS(
      "every invariant/mode pair the query references, directly or through an aggregate, has a"
          + " classified ledger entry before any solver call",
      List.of(
          "org.tzi.use.smt.encode.FragmentLedgerBoundaryTest"
              + "#aRequiredInvariantModePairWithNoLedgerEntryFailsClosed",
          "org.tzi.use.smt.encode.FragmentLedgerBoundaryTest"
              + "#aLedgerCoveringEveryRequestedPairIsAccountedFor",
          "org.tzi.use.smt.encode.FragmentLedgerBoundaryTest"
              + "#everyRefusalIsClassifiedAtItsTierOrUTypeBoundary")),

  /**
   * 7.3: "A crisp-only model is the degenerate case where nominal and uncertain translations
   * coincide -- the same code path serves it, which is what makes parity achievable rather than a
   * separate implementation." Asserted, per the milestone brief, rather than special-cased.
   */
  CRISP_MODEL_DEGENERACY(
      "on a crisp-only model the nominal and uncertain translations coincide, produced by the same"
          + " code path rather than a special case",
      List.of(
          "org.tzi.use.smt.encode.TypeAndDefinednessPreservationTest"
              + "#aCrispOnlyModelTranslatesIdenticallyInBothModes",
          "org.tzi.use.smt.encode.FragmentCheckerTest"
              + "#allNineLibraryInvariantsAssembleAndAreJointlySatisfiable"));

  private final String claim;
  private final List<String> discharges;

  CorrectnessObligation(String claim, List<String> discharges) {
    this.claim = claim;
    this.discharges = List.copyOf(discharges);
  }

  /** The obligation in the proposal's own words. */
  public String claim() {
    return claim;
  }

  /** {@code fully.qualified.TestClass#testMethod} for every test that discharges this. */
  public List<String> discharges() {
    return discharges;
  }
}
