package org.tzi.use.smt.encode;

import java.math.BigDecimal;
import java.util.function.DoubleUnaryOperator;
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
    return bisect(confidence, URealThresholdBoundary::probabilityOfBeingAboveZero);
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
    return bisect(confidence, midpoint -> probabilityOfLessThan(0.0, midpoint));
  }

  private static double probabilityOfLessThan(double standardizedMeanA, double standardizedMeanB) {
    return new UReal(standardizedMeanA, 1.0).lt(new UReal(standardizedMeanB, 1.0)).getC();
  }

  /**
   * Shared bisection: {@code probability} is either {@link #probabilityOfBeingAboveZero} ({@code
   * enclose}) or the {@code lt}-based composition {@link #encloseSymmetric} bisects instead. Both
   * callers bisect the SAME live-evaluator CNDF approximation, so both need the same reachability
   * check.
   *
   * <p><b>Why a reachability check, not just the [0,1]-open-interval guard above.</b> Bisection's
   * own invariant -- upper always satisfies the target, lower never does -- is trustworthy only for
   * an endpoint the loop actually verified by testing a midpoint against it. The two INITIAL
   * endpoints, {@link #SEARCH_MIN} and {@link #SEARCH_MAX}, are never tested that way; the loop
   * simply assumes {@code probability(SEARCH_MIN) < target < probability(SEARCH_MAX)}. That
   * assumption can be false, because the CNDF approximation the live evaluator's {@code gt()}/
   * {@code lt()} calls has its own floating-point precision ceiling well short of 1.0 -- and,
   * symmetrically, well above 0.0 -- confirmed live: {@code 0.9999999999999999} is a syntactically
   * valid confidence (it clears the {@code target < 1.0} guard above) that {@code gt()} can never
   * actually reach anywhere in {@code [SEARCH_MIN, SEARCH_MAX]}, because the CNDF approximation's
   * own value at {@code SEARCH_MAX} tops out below it. When that happens, the loop's {@code if}
   * branch that would move {@code upper} off {@code SEARCH_MAX} is simply never taken, so {@code
   * upper} is returned still pinned at its untested initial value -- a bogus boundary, not a
   * genuine enclosure, and previously returned with no diagnostic at all. Re-checking the invariant
   * explicitly against the final {@code lower}/{@code upper}, rather than trusting it, is what turns
   * that silent degradation into a located refusal naming the unreachable confidence.
   */
  private static Enclosure bisect(BigDecimal confidence, DoubleUnaryOperator probability) {
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
      if (probability.applyAsDouble(midpoint) >= target) {
        upper = midpoint;
      } else {
        lower = midpoint;
      }
    }

    double lowerProbability = probability.applyAsDouble(lower);
    double upperProbability = probability.applyAsDouble(upper);
    if (!(upperProbability >= target) || !(lowerProbability < target)) {
      throw new SmtTranslationException(
          FragmentBoundary.UTYPE_CORE,
          "UReal confidence threshold "
              + confidence
              + " is not reachable by the live evaluator's CNDF approximation anywhere in the ["
              + SEARCH_MIN
              + ", "
              + SEARCH_MAX
              + "] search window bisection searches: the bisected boundary ["
              + lower
              + ", "
              + upper
              + "] does not bracket it (live probability "
              + lowerProbability
              + " at the lower end, "
              + upperProbability
              + " at the upper end), so it would be a bogus value pinned at the search edge rather"
              + " than a genuine enclosure");
    }
    return new Enclosure(new BigDecimal(lower), new BigDecimal(upper));
  }
}
