package org.tzi.use.smt.encode;

import java.math.BigDecimal;
import org.tzi.use.uncertainty.datatypes.UReal;

/**
 * Encloses USE's executable normal-CDF threshold without copying its private approximation.
 *
 * <p>The public {@link UReal#gt(UReal)} path calls the same private CNDF implementation as OCL
 * evaluation. Bisection therefore searches the evaluator itself. The two returned decimal rationals
 * straddle the first standardized value whose probability reaches the requested confidence; callers
 * choose the upper endpoint in positive polarity and the lower endpoint in negative polarity so a
 * rounded boundary cannot create a false witness.
 */
final class URealThresholdBoundary {
  private static final double SEARCH_MIN = -8.0;
  private static final double SEARCH_MAX = 8.0;

  /**
   * Package-visible so {@code TypeAndDefinednessPreservationTest} can hold {@code
   * BoundedCompletenessQualification}'s declared numerical policy to the width actually bisected
   * to. A policy sentence that names a different number from the code is a decoration.
   */
  static final double MAX_WIDTH = 1.0e-8;

  record Enclosure(BigDecimal lower, BigDecimal upper) {}

  private URealThresholdBoundary() {}

  static Enclosure enclose(BigDecimal confidence) {
    double target = confidence.doubleValue();
    if (!(target > 0.0 && target < 1.0)) {
      // Inside 7.2's toBooleanC core, but outside the threshold shape it fixes: USE's own
      // Op_uBoolean_toBooleanC yields UndefinedValue for a confidence outside [0,1], which is a
      // definedness question this linear-boundary encoding does not answer.
      throw new SmtTranslationException(
          FragmentBoundary.UTYPE_CORE,
          "UReal confidence threshold must be strictly between 0 and 1, got " + confidence);
    }

    double lower = SEARCH_MIN;
    double upper = SEARCH_MAX;
    while (upper - lower >= MAX_WIDTH) {
      double midpoint = lower + (upper - lower) / 2.0;
      if (probabilityOfBeingAboveZero(midpoint) >= target) {
        upper = midpoint;
      } else {
        lower = midpoint;
      }
    }
    return new Enclosure(new BigDecimal(lower), new BigDecimal(upper));
  }

  private static double probabilityOfBeingAboveZero(double standardizedMean) {
    return new UReal(standardizedMean, 1.0).gt(new UReal(0.0)).getC();
  }

  /**
   * The standardized boundary for two-sided, EQUAL-uncertainty comparison: bisects, against the
   * same live evaluator, the standardized mean-difference {@code d} at which {@code P(UReal(0,1) <
   * UReal(d,1)) >= confidence}. For two UReal attributes A, B sharing one proven-equal uncertainty
   * σ, {@code P(A<B) >= confidence} reduces to {@code meanB - meanA >= 2σ·d} -- verified
   * numerically against {@link UReal#calculate}'s live equal-σ branch across many mean/σ/confidence
   * combinations before this method was written (see the master plan's own research appendix for
   * that derivation). {@code P(A>B) >= confidence} reduces to {@code meanA - meanB >= 2σ·d}, the
   * same {@code d}, by the same symmetry the underlying evaluator itself exhibits (confirmed
   * against the live evaluator: swapping which operand is queried swaps `lt`/`gt` exactly).
   *
   * <p>This is NOT the general uncertain-vs-uncertain case: {@code calculate()}'s equal-σ branch
   * hardcodes one side of the partition to zero rather than computing it from a genuine two-sided
   * tail probability (confirmed live: P(A<B) is 0 whenever mean(A) > mean(B), not a small positive
   * number, however close the means are) -- callers must not generalize this boundary to unequal σ,
   * where {@code calculate()} takes a structurally different branch (a quadratic crossing-point
   * pair) this method does not model at all.
   */
  static Enclosure encloseSymmetric(BigDecimal confidence) {
    double target = confidence.doubleValue();
    if (!(target > 0.0 && target < 1.0)) {
      throw new SmtTranslationException(
          FragmentBoundary.UTYPE_CORE,
          "UReal confidence threshold must be strictly between 0 and 1, got " + confidence);
    }

    double lower = SEARCH_MIN;
    double upper = SEARCH_MAX;
    while (upper - lower >= MAX_WIDTH) {
      double midpoint = lower + (upper - lower) / 2.0;
      if (probabilityOfLessThan(0.0, midpoint) >= target) {
        upper = midpoint;
      } else {
        lower = midpoint;
      }
    }
    return new Enclosure(new BigDecimal(lower), new BigDecimal(upper));
  }

  private static double probabilityOfLessThan(double standardizedMeanA, double standardizedMeanB) {
    return new UReal(standardizedMeanA, 1.0).lt(new UReal(standardizedMeanB, 1.0)).getC();
  }
}
