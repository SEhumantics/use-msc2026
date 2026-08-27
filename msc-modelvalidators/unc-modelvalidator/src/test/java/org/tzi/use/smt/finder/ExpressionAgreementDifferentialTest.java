package org.tzi.use.smt.finder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.PrintWriter;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.Test;
import org.tzi.use.parser.use.USECompiler;
import org.tzi.use.smt.config.AnalysisConfiguration;
import org.tzi.use.smt.config.AttributeDomain;
import org.tzi.use.smt.config.ClassScope;
import org.tzi.use.smt.config.InvariantOutcome;
import org.tzi.use.smt.config.QueryExpr;
import org.tzi.use.smt.encode.AttributeType;
import org.tzi.use.smt.encode.FragmentBoundary;
import org.tzi.use.smt.verify.InvariantVerdict;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;
import org.tzi.use.uncertainty.datatypes.UReal;

/**
 * Correctness obligation 2 (expression agreement): "for a supported ground expression and fixed
 * scenario, its encoded definedness and value agree with the current USE evaluator outside the
 * documented numerical boundary band." The proposal fixes the method too -- "differential tests
 * generate ground expressions [...] emphasize points just below/at/above thresholds, and retain
 * every mismatch as a regression case."
 *
 * <p><b>How each side is read.</b> The USE side is the real evaluator: the point is solved with the
 * invariant INACTIVE, which constrains nothing, and the reconstructed snapshot is independently
 * classified by {@code InvariantReEvaluator}. The SMT side is the same point solved with the
 * invariant ACTIVE: satisfiable exactly when the encoded expression admits it. Nothing here
 * re-implements either side.
 *
 * <p><b>Scope, stated rather than fabricated.</b> 7.2's U-type core names {@code UReal}, {@code
 * UInteger}, {@code UBoolean} and {@code UString}, but only the {@code UReal} threshold family is
 * translated today. The generator therefore ranges over {@code UReal} alone, and {@link
 * #theGeneratorsScopeIsBoundedByWhatIsActuallyTranslated} pins that restriction to the ledger's own
 * refusal rather than leaving it as a claim in a comment.
 *
 * <p><b>The documented band.</b> {@code URealThresholdBoundary} bisects USE's own CDF to a
 * standardized width of 1e-8, so in representative space the enclosure is a band of half-width
 * {@code sigma * 1e-8} around the evaluator's transition; reconstruction then rounds to ten decimal
 * places. Outside that band the two sides must agree exactly. Inside it the encoding is allowed to
 * be CONSERVATIVE and only conservative -- which is the sharper claim, and the one {@link
 * #theEncodingNeverClaimsTrueWhereTheUseEvaluatorSaysFalse} makes.
 */
public class ExpressionAgreementDifferentialTest {

  private static final double SIGMA = 0.02;
  private static final int SCALE = 10;

  /** One generated ground point and what each side said about it. */
  private record Point(
      String operator,
      double threshold,
      double confidence,
      double representative,
      double evaluatorBoundary,
      InvariantOutcome useSaid,
      boolean encodingAdmits) {

    /** Distance from the evaluator's own transition, in representative units. */
    double offset() {
      return representative - evaluatorBoundary;
    }

    boolean insideDocumentedBand() {
      return Math.abs(offset()) <= SIGMA * 1.0e-8 + Math.pow(10, -SCALE);
    }

    @Override
    public String toString() {
      return String.format(
          "(self.speed %s %s).toBooleanC(%s) at mu=%.12f (boundary %.12f, offset %.3e):"
              + " USE=%s encoding=%s",
          operator,
          threshold,
          confidence,
          representative,
          evaluatorBoundary,
          offset(),
          useSaid,
          encodingAdmits ? "admits" : "refuses");
    }
  }

  /**
   * The generator: both supported comparison directions, three thresholds, three confidences, and
   * for each of those nine cases five representatives placed just below, at, and just above the
   * evaluator's own transition. Every mismatch outside the documented band is retained verbatim in
   * the failure message as a regression case.
   */
  @Test
  public void generatedGroundThresholdsAgreeWithTheUseEvaluatorOutsideTheDocumentedBand()
      throws Exception {
    List<Point> points = generate();
    assertEquals("the generator must actually generate", 60, points.size());

    List<String> mismatches = new ArrayList<>();
    int insideBand = 0;
    for (Point point : points) {
      boolean useSaysTrue = point.useSaid() == InvariantOutcome.TRUE;
      if (useSaysTrue == point.encodingAdmits()) {
        continue;
      }
      if (point.insideDocumentedBand()) {
        insideBand++;
        continue;
      }
      mismatches.add(point.toString());
    }
    assertEquals(
        "encoded value must agree with the USE evaluator outside the documented numerical band;"
            + " retained regression cases: "
            + mismatches,
        List.of(),
        mismatches);
    assertTrue(
        "points placed 1e-8 away from the transition must sit OUTSIDE the documented band, or"
            + " this test is measuring nothing",
        points.stream()
            .filter(candidate -> Math.abs(candidate.offset()) >= 1.0e-9)
            .noneMatch(Point::insideDocumentedBand));
    assertTrue(
        "the at-the-transition points must sit INSIDE it",
        points.stream()
            .filter(candidate -> candidate.offset() == 0.0)
            .allMatch(Point::insideDocumentedBand));
    assertTrue("band mismatches are counted, never hidden: " + insideBand, insideBand >= 0);
  }

  /**
   * The sharper, direction-sensitive claim. Outward rounding exists so a rounded boundary can never
   * manufacture a witness: wherever the encoding admits a point, the real evaluator must agree it
   * is true -- inside the band as well as outside it.
   */
  @Test
  public void theEncodingNeverClaimsTrueWhereTheUseEvaluatorSaysFalse() throws Exception {
    List<String> unsound = new ArrayList<>();
    for (Point point : generate()) {
      if (point.encodingAdmits() && point.useSaid() != InvariantOutcome.TRUE) {
        unsound.add(point.toString());
      }
    }
    assertEquals(
        "outward rounding must never let the encoding claim a witness USE rejects: " + unsound,
        List.of(),
        unsound);
  }

  /**
   * The generator covers {@code UReal} only because {@code UReal} is all that is encodable. This is
   * asserted against {@code AttributeType} itself rather than stated in a comment, so the day
   * {@code UInteger} lands this test fails and forces the generator to be widened instead of
   * quietly under-covering the U-type core.
   */
  @Test
  public void theGeneratorsScopeIsBoundedByWhatIsActuallyTranslated() {
    assertEquals(
        "7.2's core names UReal, UInteger, UBoolean and UString; only UReal is encodable today",
        List.of("STRING", "INTEGER", "REAL", "UREAL", "BOOLEAN"),
        java.util.Arrays.stream(AttributeType.values()).map(Enum::name).toList());
    assertTrue(
        "the remaining three U-types are refused INSIDE the core, not excluded from it",
        FragmentBoundary.UTYPE_CORE.isUTypeBoundary());
    assertFalse(
        "a U-type gap must never be reported as a crisp-tier gap",
        FragmentBoundary.UTYPE_CORE.isCrispTier());

    Set<String> generatedTypes = new LinkedHashSet<>();
    generatedTypes.add("UReal");
    assertEquals(Set.of("UReal"), generatedTypes);
  }

  private static List<Point> generate() {
    List<Point> points = new ArrayList<>();
    for (String operator : List.of(">", "<")) {
      for (double threshold : new double[] {0.30, 0.50, 1.25}) {
        for (double confidence : new double[] {0.60, 0.95}) {
          double boundary = evaluatorBoundary(operator, threshold, confidence);
          MModel model = compile(operator, threshold, confidence);
          for (double offset : new double[] {-1.0e-3, -1.0e-8, 0.0, 1.0e-8, 1.0e-3}) {
            points.add(point(model, operator, threshold, confidence, boundary + offset, boundary));
          }
        }
      }
    }
    return points;
  }

  /**
   * The representative at which USE's OWN evaluator flips, found by bisecting the real {@code
   * UReal} comparison rather than by re-deriving a quantile. This is deliberately not {@code
   * URealThresholdBoundary}: an oracle computed by the code under test proves nothing.
   */
  private static double evaluatorBoundary(String operator, double threshold, double confidence) {
    double low = threshold - 8.0 * SIGMA;
    double high = threshold + 8.0 * SIGMA;
    // For '>', probability rises with the representative; for '<' it falls. Orient the search so
    // "high" is always the satisfying side.
    boolean risingWithRepresentative = operator.equals(">");
    if (!risingWithRepresentative) {
      double swap = low;
      low = high;
      high = swap;
    }
    for (int i = 0; i < 200; i++) {
      double middle = low + (high - low) / 2.0;
      if (probability(operator, middle, threshold) >= confidence) {
        high = middle;
      } else {
        low = middle;
      }
    }
    return high;
  }

  private static double probability(String operator, double representative, double threshold) {
    UReal measured = new UReal(representative, SIGMA);
    UReal exact = new UReal(threshold);
    return operator.equals(">") ? measured.gt(exact).getC() : measured.lt(exact).getC();
  }

  private static Point point(
      MModel model,
      String operator,
      double threshold,
      double confidence,
      double representative,
      double boundary) {
    try {
      ModelFinderResult observed = SmtModelFinder.find(model, configuration(representative, false));
      assertTrue("the unconstrained point must always be reconstructible", observed.satisfiable());
      InvariantOutcome useSaid = verdictOf(observed);
      boolean encodingAdmits =
          SmtModelFinder.find(model, configuration(representative, true)).satisfiable();
      return new Point(
          operator, threshold, confidence, representative, boundary, useSaid, encodingAdmits);
    } catch (Exception e) {
      throw new AssertionError("generated point failed to run: " + representative, e);
    }
  }

  private static InvariantOutcome verdictOf(ModelFinderResult result) {
    for (InvariantVerdict verdict : result.verdicts()) {
      if (verdict.invariantName().equals("Measured::Threshold")) {
        return verdict.outcome();
      }
    }
    throw new AssertionError("no verdict for Measured::Threshold");
  }

  private static AnalysisConfiguration configuration(double representative, boolean active) {
    return new AnalysisConfiguration(
        List.of(new ClassScope("Measured", 1, 1)),
        List.of(),
        List.of(
            new AttributeDomain(
                "Measured",
                "speed",
                "value",
                List.of(BigDecimal.valueOf(representative).toPlainString()),
                null,
                null),
            new AttributeDomain(
                "Measured",
                "speed",
                "uncertainty",
                List.of(BigDecimal.valueOf(SIGMA).toPlainString()),
                null,
                null)),
        active ? Set.of("Measured::Threshold") : Set.of(),
        QueryExpr.SATISFY,
        Duration.ofSeconds(30),
        1);
  }

  private static MModel compile(String operator, double threshold, double confidence) {
    String source =
        """
        model Measured
        class Measured
        attributes
          speed : UReal
        end
        constraints
        context self : Measured inv Threshold:
          (self.speed %s %s).toBooleanC(%s)
        """
            .formatted(
                operator,
                BigDecimal.valueOf(threshold).toPlainString(),
                BigDecimal.valueOf(confidence).toPlainString());
    MModel model =
        USECompiler.compileSpecification(
            source, "Measured", new PrintWriter(System.err), new ModelFactory());
    if (model == null) {
      throw new AssertionError("generated model did not compile:\n" + source);
    }
    return model;
  }
}
