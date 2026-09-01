package org.tzi.use.smt.encode;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.math.BigDecimal;
import org.junit.Test;
import org.tzi.use.uncertainty.datatypes.UReal;

public class URealThresholdBoundaryTest {

  @Test
  public void enclosureStraddlesTheRealUseEvaluatorTransition() {
    URealThresholdBoundary.Enclosure enclosure =
        URealThresholdBoundary.enclose(new BigDecimal("0.95"));

    assertTrue(probability(enclosure.lower()) < 0.95);
    assertTrue(probability(enclosure.upper()) >= 0.95);
    assertTrue(enclosure.upper().subtract(enclosure.lower()).doubleValue() < 1.0e-8);
  }

  /**
   * BUG B: {@code 0.9999999999999999} clears the {@code 0 < confidence < 1} guard -- it is a
   * syntactically valid {@code BigDecimal} strictly less than one -- but the live evaluator's CNDF
   * approximation this class bisects against never actually returns a probability that high
   * anywhere in the {@code [-8, 8]} search window (confirmed live: {@code gt()}'s own ceiling
   * there, {@code UReal(8,1).gt(UReal(0)).getC()}, is {@code 0.9999999999999993}, strictly below the
   * target). Before the fix {@code enclose} returned a bogus enclosure pinned at the search edge
   * with no diagnostic; it must now refuse with a located exception instead.
   */
  @Test
  public void refusesAConfidenceTheLiveEvaluatorCanNeverReachInTheSearchWindow() {
    BigDecimal unreachable = new BigDecimal("0.9999999999999999");

    // The audit's own reproduction: the live evaluator's ceiling at the search window's edge is
    // strictly below the target, so no point in the window can ever satisfy it.
    assertTrue(
        "the evaluator's own ceiling must be BELOW the unreachable target for this to be a genuine"
            + " reproduction, not a stale one",
        probability(BigDecimal.valueOf(8.0)) < unreachable.doubleValue());

    SmtTranslationException refused =
        assertThrows(
            SmtTranslationException.class, () -> URealThresholdBoundary.enclose(unreachable));

    assertEquals(FragmentBoundary.UTYPE_CORE, refused.boundary());
    assertTrue(refused.getMessage(), refused.getMessage().contains("0.9999999999999999"));
  }

  /** The same unreachable-confidence reproduction, through {@code encloseSymmetric} instead. */
  @Test
  public void encloseSymmetricAlsoRefusesTheSameUnreachableConfidence() {
    BigDecimal unreachable = new BigDecimal("0.9999999999999999");

    SmtTranslationException refused =
        assertThrows(
            SmtTranslationException.class,
            () -> URealThresholdBoundary.encloseSymmetric(unreachable));

    assertEquals(FragmentBoundary.UTYPE_CORE, refused.boundary());
    assertTrue(refused.getMessage(), refused.getMessage().contains("0.9999999999999999"));
  }

  private static double probability(BigDecimal standardizedMean) {
    return new UReal(standardizedMean.doubleValue(), 1.0).gt(new UReal(0.0)).getC();
  }
}
