package org.tzi.use.smt.verify;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.tzi.use.smt.config.InvariantOutcome;

/**
 * Regression cover for defect B4: Milestone 4.3's central claim -- that a delivered witness is held
 * to the classification its query claimed for it -- had ZERO negative-path coverage. Replacing the
 * whole body of {@link QueryWitnessChecker#requireExpectedOutcomes} with an immediate {@code
 * return;} left the suite fully green, so nothing tested the safety net itself. Every test here
 * fails under that mutation.
 */
public class QueryWitnessCheckerTest {

  @Test
  public void matchingOutcomesAreAccepted() {
    QueryWitnessChecker.requireExpectedOutcomes(
        expected("A::One", InvariantOutcome.TRUE, "A::Two", InvariantOutcome.FALSE),
        List.of(
            new InvariantVerdict("A::One", InvariantOutcome.TRUE),
            new InvariantVerdict("A::Two", InvariantOutcome.FALSE),
            new InvariantVerdict("A::Unclaimed", InvariantOutcome.UNDEFINED)));
  }

  /** A SATISFY witness whose invariant USE independently calls false is a translation error. */
  @Test
  public void aFalseWhereTheQueryClaimedTrueIsRejected() {
    WitnessAttributionException exception =
        assertThrows(
            WitnessAttributionException.class,
            () ->
                QueryWitnessChecker.requireExpectedOutcomes(
                    expected("A::One", InvariantOutcome.TRUE),
                    List.of(new InvariantVerdict("A::One", InvariantOutcome.FALSE))));
    assertTrue(exception.getMessage().contains("A::One"));
    assertTrue(exception.getMessage().contains("expected TRUE"));
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
                QueryWitnessChecker.requireExpectedOutcomes(
                    expected("A::Target", InvariantOutcome.FALSE),
                    List.of(new InvariantVerdict("A::Target", InvariantOutcome.UNDEFINED))));
    assertTrue(exception.getMessage().contains("A::Target"));
    assertTrue(exception.getMessage().contains("UNDEFINED"));
  }

  /** An UNDEFINED non-target is equally not a TRUE one. */
  @Test
  public void anUndefinedNonTargetDoesNotSatisfyATrueClaim() {
    assertThrows(
        WitnessAttributionException.class,
        () ->
            QueryWitnessChecker.requireExpectedOutcomes(
                expected("A::Other", InvariantOutcome.TRUE),
                List.of(new InvariantVerdict("A::Other", InvariantOutcome.UNDEFINED))));
  }

  /** A claimed invariant the oracle never reported on cannot be assumed to have held. */
  @Test
  public void anInvariantAbsentFromTheOracleVerdictsIsRejected() {
    WitnessAttributionException exception =
        assertThrows(
            WitnessAttributionException.class,
            () ->
                QueryWitnessChecker.requireExpectedOutcomes(
                    expected("A::Missing", InvariantOutcome.TRUE),
                    List.of(new InvariantVerdict("A::Other", InvariantOutcome.TRUE))));
    assertTrue(exception.getMessage().contains("A::Missing"));
  }

  /** Every mismatch is reported, not just the first -- an attribution report needs all of them. */
  @Test
  public void everyMismatchIsNamedInOneFailure() {
    WitnessAttributionException exception =
        assertThrows(
            WitnessAttributionException.class,
            () ->
                QueryWitnessChecker.requireExpectedOutcomes(
                    expected("A::One", InvariantOutcome.TRUE, "A::Two", InvariantOutcome.FALSE),
                    List.of(
                        new InvariantVerdict("A::One", InvariantOutcome.FALSE),
                        new InvariantVerdict("A::Two", InvariantOutcome.TRUE))));
    assertTrue(exception.getMessage().contains("A::One"));
    assertTrue(exception.getMessage().contains("A::Two"));
  }

  private static Map<String, InvariantOutcome> expected(Object... pairs) {
    Map<String, InvariantOutcome> expected = new LinkedHashMap<>();
    for (int i = 0; i < pairs.length; i += 2) {
      expected.put((String) pairs[i], (InvariantOutcome) pairs[i + 1]);
    }
    return expected;
  }
}
