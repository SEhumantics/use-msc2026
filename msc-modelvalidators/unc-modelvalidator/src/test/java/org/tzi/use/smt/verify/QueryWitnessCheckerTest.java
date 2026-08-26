package org.tzi.use.smt.verify;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.tzi.use.smt.config.InvariantOutcome;
import org.tzi.use.smt.config.QueryExpr;
import org.tzi.use.smt.config.TranslationMode;

/**
 * Negative-path cover for the safety net itself. It began as regression cover for defect B4 --
 * Milestone 4.3's central claim, that a delivered witness is held to the classification its query
 * claimed, had ZERO negative-path coverage, and replacing the whole checker body with an immediate
 * {@code return;} left the suite fully green.
 *
 * <p>Milestone 4.4 generalised the check from "compare against an expected outcome map" to
 * "evaluate the compiled query's desugared core over the observed USE verdicts", because a
 * disjunction has no expected-outcome map at all. Every case the map form covered is carried over
 * here in its query spelling, and the cases only the query form can state are added. The
 * independent-oracle property is unchanged: these are USE-evaluator verdicts, never the solver's
 * own {@code def}/{@code val} assignment.
 */
public class QueryWitnessCheckerTest {

  private static final String ONE = "A::One";
  private static final String TWO = "A::Two";

  @Test
  public void aWitnessMatchingEveryClaimedAtomIsAccepted() {
    QueryWitnessChecker.requireQuerySatisfied(
        and(atom(ONE, InvariantOutcome.TRUE), atom(TWO, InvariantOutcome.FALSE)),
        uncertain(
            verdict(ONE, InvariantOutcome.TRUE),
            verdict(TWO, InvariantOutcome.FALSE),
            verdict("A::Unclaimed", InvariantOutcome.UNDEFINED)));
  }

  /** A SATISFY witness whose invariant USE independently calls false is a translation error. */
  @Test
  public void aFalseWhereTheQueryClaimedTrueIsRejected() {
    WitnessAttributionException exception =
        assertThrows(
            WitnessAttributionException.class,
            () ->
                QueryWitnessChecker.requireQuerySatisfied(
                    atom(ONE, InvariantOutcome.TRUE),
                    uncertain(verdict(ONE, InvariantOutcome.FALSE))));
    assertTrue(exception.getMessage().contains(ONE));
    assertTrue(exception.getMessage().contains("true(uncertain, A::One)"));
    assertTrue(exception.getMessage().contains("FALSE"));
  }

  /**
   * The distinction the whole classification algebra rests on: {@code counterexample(j)} claims
   * DEFINED-false, so an UNDEFINED reading of the target must be refused, never quietly accepted as
   * "not true".
   */
  @Test
  public void anUndefinedTargetDoesNotSatisfyADefinedFalseClaim() {
    WitnessAttributionException exception =
        assertThrows(
            WitnessAttributionException.class,
            () ->
                QueryWitnessChecker.requireQuerySatisfied(
                    atom("A::Target", InvariantOutcome.FALSE),
                    uncertain(verdict("A::Target", InvariantOutcome.UNDEFINED))));
    assertTrue(exception.getMessage().contains("A::Target"));
    assertTrue(exception.getMessage().contains("UNDEFINED"));
  }

  /** An UNDEFINED non-target is equally not a TRUE one. */
  @Test
  public void anUndefinedNonTargetDoesNotSatisfyATrueClaim() {
    assertThrows(
        WitnessAttributionException.class,
        () ->
            QueryWitnessChecker.requireQuerySatisfied(
                atom("A::Other", InvariantOutcome.TRUE),
                uncertain(verdict("A::Other", InvariantOutcome.UNDEFINED))));
  }

  /** A claimed invariant the oracle never reported on cannot be assumed to have held. */
  @Test
  public void anInvariantAbsentFromTheOracleVerdictsIsRejected() {
    WitnessAttributionException exception =
        assertThrows(
            WitnessAttributionException.class,
            () ->
                QueryWitnessChecker.requireQuerySatisfied(
                    atom("A::Missing", InvariantOutcome.TRUE),
                    uncertain(verdict("A::Other", InvariantOutcome.TRUE))));
    assertTrue(exception.getMessage().contains("A::Missing"));
  }

  /** Every atom the query asked about is named in the failure, not just the first to disagree. */
  @Test
  public void everyClassificationTheQueryAskedAboutIsNamedInOneFailure() {
    WitnessAttributionException exception =
        assertThrows(
            WitnessAttributionException.class,
            () ->
                QueryWitnessChecker.requireQuerySatisfied(
                    and(atom(ONE, InvariantOutcome.TRUE), atom(TWO, InvariantOutcome.FALSE)),
                    uncertain(
                        verdict(ONE, InvariantOutcome.FALSE),
                        verdict(TWO, InvariantOutcome.TRUE))));
    assertTrue(exception.getMessage().contains(ONE));
    assertTrue(exception.getMessage().contains(TWO));
  }

  /**
   * The shape that forced the generalisation, from both sides: ONE disjunctive query accepts two
   * witnesses whose outcome maps are disjoint, so no single expected-outcome map could have been
   * the contract for it -- and it still rejects the witness that satisfies neither disjunct.
   */
  @Test
  public void aDisjunctionAcceptsEitherDisjunctAndRejectsNeither() {
    QueryExpr joint = or(atom(ONE, InvariantOutcome.FALSE), atom(TWO, InvariantOutcome.FALSE));

    QueryWitnessChecker.requireQuerySatisfied(
        joint,
        uncertain(verdict(ONE, InvariantOutcome.FALSE), verdict(TWO, InvariantOutcome.TRUE)));
    QueryWitnessChecker.requireQuerySatisfied(
        joint,
        uncertain(verdict(ONE, InvariantOutcome.TRUE), verdict(TWO, InvariantOutcome.FALSE)));

    assertThrows(
        WitnessAttributionException.class,
        () ->
            QueryWitnessChecker.requireQuerySatisfied(
                joint,
                uncertain(
                    verdict(ONE, InvariantOutcome.TRUE), verdict(TWO, InvariantOutcome.TRUE))));
  }

  /**
   * §5.3 at the oracle: {@code not false(m,i)} is satisfied by a DEFINED-TRUE verdict, so it is
   * strictly weaker than {@code undef(m,i)}, which the very same verdict must fail. If the checker
   * ever collapsed the two, the second half of this test would pass and the query language's
   * central distinction would be unenforced on delivered witnesses.
   */
  @Test
  public void notFalseIsSatisfiedByTrueWhileUndefIsNot() {
    QueryWitnessChecker.requireQuerySatisfied(
        not(atom(ONE, InvariantOutcome.FALSE)), uncertain(verdict(ONE, InvariantOutcome.TRUE)));
    QueryWitnessChecker.requireQuerySatisfied(
        not(atom(ONE, InvariantOutcome.FALSE)),
        uncertain(verdict(ONE, InvariantOutcome.UNDEFINED)));

    assertThrows(
        "a defined-true reading is not an undefined one",
        WitnessAttributionException.class,
        () ->
            QueryWitnessChecker.requireQuerySatisfied(
                atom(ONE, InvariantOutcome.UNDEFINED),
                uncertain(verdict(ONE, InvariantOutcome.TRUE))));
  }

  /**
   * §5.2's first "the incumbent cannot state" query, checked over verdicts from BOTH modes: the
   * nominal reading is true while uncertainty makes the same requirement UNDEFINED, not merely
   * false. The checker is already mode-general; Milestone 4.5 supplies the nominal verdicts, and
   * until it does {@code SmtModelFinder} refuses such a query before solving.
   */
  @Test
  public void aNominalVersusUncertainDiscrepancyIsEvaluatedPerMode() {
    QueryExpr query =
        and(
            new QueryExpr.Classification(TranslationMode.NOMINAL, ONE, InvariantOutcome.TRUE),
            atom(ONE, InvariantOutcome.UNDEFINED));

    QueryWitnessChecker.requireQuerySatisfied(
        query,
        Map.of(
            TranslationMode.NOMINAL, List.of(verdict(ONE, InvariantOutcome.TRUE)),
            TranslationMode.UNCERTAIN, List.of(verdict(ONE, InvariantOutcome.UNDEFINED))));

    assertThrows(
        "the same outcome in the wrong mode does not satisfy it",
        WitnessAttributionException.class,
        () ->
            QueryWitnessChecker.requireQuerySatisfied(
                query,
                Map.of(
                    TranslationMode.NOMINAL, List.of(verdict(ONE, InvariantOutcome.UNDEFINED)),
                    TranslationMode.UNCERTAIN, List.of(verdict(ONE, InvariantOutcome.TRUE)))));
  }

  /**
   * §5.2's second one, the over-conservative requirement: nominal erasure rejects what uncertainty
   * accepts.
   */
  @Test
  public void anOverConservativeRequirementIsExpressibleAndChecked() {
    QueryExpr query =
        and(
            new QueryExpr.Classification(TranslationMode.NOMINAL, ONE, InvariantOutcome.FALSE),
            atom(ONE, InvariantOutcome.TRUE));

    QueryWitnessChecker.requireQuerySatisfied(
        query,
        Map.of(
            TranslationMode.NOMINAL, List.of(verdict(ONE, InvariantOutcome.FALSE)),
            TranslationMode.UNCERTAIN, List.of(verdict(ONE, InvariantOutcome.TRUE))));
  }

  /**
   * A mode the oracle never reported on is a hard refusal, never a vacuous truth -- including under
   * a negation, where silently reading a missing verdict as "did not match" would have turned an
   * unchecked atom into a satisfied one.
   */
  @Test
  public void anAtomInAModeTheOracleNeverReportedOnIsRefusedEvenUnderNegation() {
    QueryExpr nominalAtom =
        new QueryExpr.Classification(TranslationMode.NOMINAL, ONE, InvariantOutcome.TRUE);

    WitnessAttributionException exception =
        assertThrows(
            WitnessAttributionException.class,
            () ->
                QueryWitnessChecker.requireQuerySatisfied(
                    nominalAtom, uncertain(verdict(ONE, InvariantOutcome.TRUE))));
    assertTrue(exception.getMessage().contains("nominal"));
    assertTrue(exception.getMessage().contains(ONE));

    assertThrows(
        WitnessAttributionException.class,
        () ->
            QueryWitnessChecker.requireQuerySatisfied(
                not(nominalAtom), uncertain(verdict(ONE, InvariantOutcome.TRUE))));
  }

  /** An aggregate over no active invariants desugars to the constant true and is satisfied. */
  @Test
  public void theEmptyConjunctionIsSatisfiedByAnyWitness() {
    QueryWitnessChecker.requireQuerySatisfied(new QueryExpr.Constant(true), uncertain());
    assertThrows(
        WitnessAttributionException.class,
        () ->
            QueryWitnessChecker.requireQuerySatisfied(new QueryExpr.Constant(false), uncertain()));
  }

  /** Only DESUGARED cores are evaluable: an un-expanded aggregate would be a silent hole. */
  @Test
  public void anUndesugaredNodeIsRefusedRatherThanIgnored() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            QueryWitnessChecker.requireQuerySatisfied(
                new QueryExpr.Aggregate(TranslationMode.UNCERTAIN, QueryExpr.AggregateScope.ALL),
                uncertain(verdict(ONE, InvariantOutcome.TRUE))));
  }

  private static QueryExpr atom(String invariantName, InvariantOutcome outcome) {
    return new QueryExpr.Classification(TranslationMode.UNCERTAIN, invariantName, outcome);
  }

  private static QueryExpr and(QueryExpr left, QueryExpr right) {
    return new QueryExpr.And(left, right);
  }

  private static QueryExpr or(QueryExpr left, QueryExpr right) {
    return new QueryExpr.Or(left, right);
  }

  private static QueryExpr not(QueryExpr operand) {
    return new QueryExpr.Not(operand);
  }

  private static InvariantVerdict verdict(String invariantName, InvariantOutcome outcome) {
    return new InvariantVerdict(invariantName, outcome);
  }

  private static Map<TranslationMode, List<InvariantVerdict>> uncertain(
      InvariantVerdict... verdicts) {
    return Map.of(TranslationMode.UNCERTAIN, List.of(verdicts));
  }
}
