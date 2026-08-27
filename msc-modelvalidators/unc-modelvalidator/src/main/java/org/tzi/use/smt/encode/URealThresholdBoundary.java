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
  private static final double MAX_WIDTH = 1.0e-8;

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
}
