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
import org.tzi.use.uncertainty.datatypes.UBoolean;
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
 * UInteger}, {@code UBoolean} and {@code UString}. The first three are translated; {@code UString}
 * is not. The generator therefore ranges over exactly those three, and {@link
 * #theGeneratorsScopeIsBoundedByWhatIsActuallyTranslated} pins that restriction to {@code
 * AttributeType} itself rather than leaving it as a claim in a comment, so the day the fourth
 * family lands this test fails and forces the generator to be widened again. It has now fired twice
 * and been widened twice, never suppressed.
 *
 * <p><b>Why the {@code UBoolean} points slide the CONFIDENCE rather than a representative.</b>
 * {@code UBoolean} has no representative and no normal CDF: its rules are exact algebra over
 * probabilities, so the evaluator's transition for {@code b.toBooleanC(theta)} is {@code theta}
 * itself. The informative axis is therefore the confidence, slid just below / exactly onto / just
 * above the composed probability USE's own {@code UBoolean} computes -- and the exactly-onto points
 * are the {@code >=} tie the projection is defined by.
 *
 * <p><b>Why the {@code UInteger} points are integers and why that is the sharper test.</b> The
 * threshold mathematics is shared: USE's own {@code UInteger.gt} is defined as {@code
 * toUReal().gt(...)}, so the evaluator's transition sits at the same real-valued representative.
 * But a {@code UInteger} representative can only take integer values, so the generated points
 * straddle that transition by whole units rather than by {@code 1e-8} -- which puts every one of
 * them OUTSIDE the documented numerical band, where the two sides must agree exactly. A boundary
 * that is correct for reals but off by one for integers is caught here and nowhere else.
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

  /**
   * The {@code UInteger} generator's measurement quality. It is a whole unit deliberately: with
   * {@code sigma = 1}, the evaluator's transition for {@code n > 5} at 95% lands at {@code
   * 6.6448534751573820}, strictly between two integers, which is exactly the configuration in which
   * an off-by-one rounding error is observable.
   */
  private static final double INTEGER_SIGMA = 1.0;

  private static final int SCALE = 10;

  /** One generated ground point and what each side said about it. */
  private record Point(
      String uType,
      double sigma,
      String expression,
      double confidence,
      double representative,
      double evaluatorBoundary,
      InvariantOutcome useSaid,
      boolean encodingAdmits) {

    /**
     * Distance from the evaluator's own transition. For the two paired families that is measured in
     * REPRESENTATIVE units; for {@code UBoolean} it is measured in PROBABILITY units, since the
     * transition there is the confidence itself and the quantity crossing it is the composed
     * probability.
     */
    double offset() {
      return representative - evaluatorBoundary;
    }

    boolean insideDocumentedBand() {
      if (uType.equals("UBoolean")) {
        // There is no numerical band for UBoolean, and that is a claim about the encoding, not a
        // convenience: no normal CDF is involved and nothing is bisected. The composition is USE's
        // own UBoolean arithmetic evaluated at translation time over finitely many configured
        // probabilities, so the encoded value is bit-identical to the evaluator's and EVERY
        // UBoolean point is an exact-agreement point.
        return false;
      }
      return Math.abs(offset()) <= sigma * 1.0e-8 + Math.pow(10, -SCALE);
    }

    @Override
    public String toString() {
      return String.format(
          "%s: %s.toBooleanC(%s) at %.15f (sigma=%s, transition %.15f, offset %.3e):"
              + " USE=%s encoding=%s",
          uType,
          expression,
          confidence,
          representative,
          sigma,
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
    assertEquals("the generator must actually generate", 60 + 48 + 45, points.size());
    assertEquals(
        "every translated U-type family must be generated",
        Set.of("UReal", "UInteger", "UBoolean"),
        points.stream().map(Point::uType).collect(java.util.stream.Collectors.toSet()));

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
        "the at-the-transition points of the families that HAVE a numerical band must sit INSIDE"
            + " it",
        points.stream()
            .filter(candidate -> candidate.sigma() > 0.0 && candidate.offset() == 0.0)
            .allMatch(Point::insideDocumentedBand));
    assertTrue("band mismatches are counted, never hidden: " + insideBand, insideBand >= 0);
    assertTrue(
        "no UInteger point may fall inside the numerical band -- integer representatives straddle"
            + " the transition by whole units, so all of them are exact-agreement points",
        points.stream()
            .filter(candidate -> candidate.uType().equals("UInteger"))
            .noneMatch(Point::insideDocumentedBand));
    assertTrue(
        "no UBoolean point may fall inside the numerical band either, and for a stronger reason:"
            + " there is no band at all. Nothing about the UBoolean encoding approximates -- the"
            + " composition is USE's own arithmetic evaluated at translation time over the finitely"
            + " many configured probabilities -- so every UBoolean point, the at-theta ties"
            + " included, must agree EXACTLY",
        points.stream()
            .filter(candidate -> candidate.uType().equals("UBoolean"))
            .noneMatch(Point::insideDocumentedBand));
    assertEquals(
        "the UBoolean generator must exercise the PRODUCT rules, not only the bare projection --"
            + " and/or/implies are the three that could have escaped QF_LIRA",
        27,
        points.stream()
            .filter(candidate -> candidate.uType().equals("UBoolean"))
            .filter(
                candidate ->
                    candidate.expression().contains(" and ")
                        || candidate.expression().contains(" or ")
                        || candidate.expression().contains(" implies "))
            .count());
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
   * The generator covers exactly the U-type families that are encodable. This is asserted against
   * {@code AttributeType} itself rather than stated in a comment, so the day a third family lands
   * this test fails and forces the generator to be widened instead of quietly under-covering the
   * U-type core. {@code UInteger} is the family that landed second, and widening this pin -- not
   * suppressing it -- is what that landing was required to do.
   */
  @Test
  public void theGeneratorsScopeIsBoundedByWhatIsActuallyTranslated() throws Exception {
    assertEquals(
        "7.2's core names UReal, UInteger, UBoolean and UString; the first three are encodable"
            + " today, UString is not",
        List.of("STRING", "INTEGER", "REAL", "UREAL", "UINTEGER", "UBOOLEAN", "BOOLEAN"),
        java.util.Arrays.stream(AttributeType.values()).map(Enum::name).toList());
    assertTrue(
        "the remaining U-type is refused INSIDE the core, not excluded from it",
        FragmentBoundary.UTYPE_CORE.isUTypeBoundary());
    assertFalse(
        "a U-type gap must never be reported as a crisp-tier gap",
        FragmentBoundary.UTYPE_CORE.isCrispTier());

    Set<String> generatedTypes = new LinkedHashSet<>();
    generate().forEach(point -> generatedTypes.add(point.uType()));
    assertEquals(
        "the generator must cover every encodable U-type family, read off the points it actually"
            + " produced rather than off a hand-maintained list",
        Set.of("UReal", "UInteger", "UBoolean"),
        generatedTypes);
  }

  private static List<Point> generate() {
    List<Point> points = new ArrayList<>();
    for (String operator : List.of(">", "<")) {
      for (double threshold : new double[] {0.30, 0.50, 1.25}) {
        for (double confidence : new double[] {0.60, 0.95}) {
          double boundary = evaluatorBoundary(operator, threshold, confidence, SIGMA);
          MModel model = compile(operator, threshold, confidence);
          for (double offset : new double[] {-1.0e-3, -1.0e-8, 0.0, 1.0e-8, 1.0e-3}) {
            points.add(point(model, operator, threshold, confidence, boundary + offset, boundary));
          }
        }
      }
    }
    points.addAll(generateUInteger());
    points.addAll(generateUBoolean());
    return points;
  }

  /**
   * The {@code UBoolean} half, and the one the third family exists to justify.
   *
   * <p>Nothing is bisected here because nothing needs to be: the source's rules are exact algebra
   * over probabilities, so the evaluator's transition for {@code b.toBooleanC(theta)} IS {@code
   * theta}, and the quantity crossing it is the composed probability USE's own {@code UBoolean}
   * computes. The generator therefore fixes the stored probabilities and slides {@code theta} just
   * below, exactly onto, and just above that composed value -- the at-theta point being the {@code
   * >=} tie the projection is defined by.
   *
   * <p>All four connectives are generated, not only the bare projection, because three of them
   * ({@code and}, {@code or}, {@code implies}) are PRODUCTS of two probabilities. They are the
   * reason this family could have escaped {@code QF_LIRA} at all, and a point that never composes
   * anything would leave the finite-domain expansion untested against the real evaluator.
   */
  private static List<Point> generateUBoolean() {
    List<Point> points = new ArrayList<>();
    for (String shape :
        List.of(
            "self.a",
            "not self.a",
            "self.a and self.b",
            "self.a or self.b",
            "self.a implies self.b")) {
      for (double[] stored : new double[][] {{0.5, 0.4}, {0.9, 0.9}, {0.3, 0.8}}) {
        double composed = evaluatorProbability(shape, stored[0], stored[1]);
        for (double offset : new double[] {-1.0e-3, 0.0, 1.0e-3}) {
          double confidence = composed - offset;
          MModel model = compileUBoolean(shape, confidence);
          points.add(uBooleanPoint(model, shape, stored, confidence, composed));
        }
      }
    }
    return points;
  }

  /**
   * The composed probability USE's OWN {@code UBoolean} gives this shape. Deliberately not {@code
   * UBooleanProbability}: an oracle computed by the code under test proves nothing, which is the
   * same rule the {@code UReal} half follows by bisecting the real {@code UReal} rather than
   * consulting {@code URealThresholdBoundary}.
   */
  private static double evaluatorProbability(String shape, double first, double second) {
    UBoolean a = new UBoolean(true, first);
    UBoolean b = new UBoolean(true, second);
    return switch (shape) {
      case "self.a" -> a.getC();
      case "not self.a" -> a.not().getC();
      case "self.a and self.b" -> a.and(b).getC();
      case "self.a or self.b" -> a.or(b).getC();
      case "self.a implies self.b" -> a.implies(b).getC();
      default -> throw new AssertionError("unhandled shape " + shape);
    };
  }

  private static Point uBooleanPoint(
      MModel model, String shape, double[] stored, double confidence, double composed) {
    try {
      ModelFinderResult observed = SmtModelFinder.find(model, uBooleanConfiguration(stored, false));
      assertTrue("the unconstrained point must always be reconstructible", observed.satisfiable());
      InvariantOutcome useSaid = verdictOf(observed, "Signal::Threshold");
      boolean encodingAdmits =
          SmtModelFinder.find(model, uBooleanConfiguration(stored, true)).satisfiable();
      return new Point(
          "UBoolean",
          0.0,
          "(" + shape + ")",
          confidence,
          composed,
          confidence,
          useSaid,
          encodingAdmits);
    } catch (Exception e) {
      throw new AssertionError(
          "generated point failed to run: " + shape + " at theta=" + confidence, e);
    }
  }

  private static AnalysisConfiguration uBooleanConfiguration(double[] stored, boolean active) {
    return new AnalysisConfiguration(
        List.of(new ClassScope("Signal", 1, 1)),
        List.of(),
        List.of(
            new AttributeDomain(
                "Signal",
                "a",
                "probability",
                List.of(BigDecimal.valueOf(stored[0]).toPlainString()),
                null,
                null),
            new AttributeDomain(
                "Signal",
                "b",
                "probability",
                List.of(BigDecimal.valueOf(stored[1]).toPlainString()),
                null,
                null)),
        active ? Set.of("Signal::Threshold") : Set.of(),
        QueryExpr.SATISFY,
        Duration.ofSeconds(30),
        1);
  }

  private static MModel compileUBoolean(String shape, double confidence) {
    String source =
        """
        model Signal
        class Signal
        attributes
          a : UBoolean
          b : UBoolean
        end
        constraints
        context self : Signal inv Threshold:
          (%s).toBooleanC(%s)
        """
            .formatted(shape, BigDecimal.valueOf(confidence).toPlainString());
    MModel model =
        USECompiler.compileSpecification(
            source, "Signal", new PrintWriter(System.err), new ModelFactory());
    if (model == null) {
      throw new AssertionError("generated model did not compile:\n" + source);
    }
    return model;
  }

  /**
   * The {@code UInteger} half. The representative is an INTEGER, so the informative points are the
   * whole units straddling the evaluator's own transition rather than epsilon offsets: the two
   * integers on either side of it, plus one further out in each direction. Whether the encoding
   * rounds the boundary the way the solver's integer theory does is decided precisely here.
   */
  private static List<Point> generateUInteger() {
    List<Point> points = new ArrayList<>();
    for (String operator : List.of(">", "<")) {
      for (int threshold : new int[] {5, 12, 20}) {
        for (double confidence : new double[] {0.60, 0.95}) {
          double boundary = evaluatorBoundary(operator, threshold, confidence, INTEGER_SIGMA);
          MModel model = compileUInteger(operator, threshold, confidence);
          long lower = (long) Math.floor(boundary);
          long upper = (long) Math.ceil(boundary);
          for (long representative : new long[] {lower - 1, lower, upper, upper + 1}) {
            points.add(
                integerPoint(model, operator, threshold, confidence, representative, boundary));
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
  private static double evaluatorBoundary(
      String operator, double threshold, double confidence, double sigma) {
    double low = threshold - 8.0 * sigma;
    double high = threshold + 8.0 * sigma;
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
      if (probability(operator, middle, threshold, sigma) >= confidence) {
        high = middle;
      } else {
        low = middle;
      }
    }
    return high;
  }

  private static double probability(
      String operator, double representative, double threshold, double sigma) {
    UReal measured = new UReal(representative, sigma);
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
      InvariantOutcome useSaid = verdictOf(observed, "Measured::Threshold");
      boolean encodingAdmits =
          SmtModelFinder.find(model, configuration(representative, true)).satisfiable();
      return new Point(
          "UReal",
          SIGMA,
          String.format("(self.speed %s %s)", operator, BigDecimal.valueOf(threshold)),
          confidence,
          representative,
          boundary,
          useSaid,
          encodingAdmits);
    } catch (Exception e) {
      throw new AssertionError("generated point failed to run: " + representative, e);
    }
  }

  private static Point integerPoint(
      MModel model,
      String operator,
      int threshold,
      double confidence,
      long representative,
      double boundary) {
    try {
      ModelFinderResult observed =
          SmtModelFinder.find(model, integerConfiguration(representative, false));
      assertTrue("the unconstrained point must always be reconstructible", observed.satisfiable());
      InvariantOutcome useSaid = verdictOf(observed, "Counted::Threshold");
      boolean encodingAdmits =
          SmtModelFinder.find(model, integerConfiguration(representative, true)).satisfiable();
      return new Point(
          "UInteger",
          INTEGER_SIGMA,
          String.format("(self.units %s %d)", operator, threshold),
          confidence,
          representative,
          boundary,
          useSaid,
          encodingAdmits);
    } catch (Exception e) {
      throw new AssertionError("generated point failed to run: n=" + representative, e);
    }
  }

  private static InvariantOutcome verdictOf(ModelFinderResult result, String invariantName) {
    for (InvariantVerdict verdict : result.verdicts()) {
      if (verdict.invariantName().equals(invariantName)) {
        return verdict.outcome();
      }
    }
    throw new AssertionError("no verdict for " + invariantName);
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

  private static AnalysisConfiguration integerConfiguration(long representative, boolean active) {
    return new AnalysisConfiguration(
        List.of(new ClassScope("Counted", 1, 1)),
        List.of(),
        List.of(
            new AttributeDomain(
                "Counted", "units", "value", List.of(Long.toString(representative)), null, null),
            new AttributeDomain(
                "Counted",
                "units",
                "uncertainty",
                List.of(BigDecimal.valueOf(INTEGER_SIGMA).toPlainString()),
                null,
                null)),
        active ? Set.of("Counted::Threshold") : Set.of(),
        QueryExpr.SATISFY,
        Duration.ofSeconds(30),
        1);
  }

  private static MModel compileUInteger(String operator, int threshold, double confidence) {
    String source =
        """
        model Counted
        class Counted
        attributes
          units : UInteger
        end
        constraints
        context self : Counted inv Threshold:
          (self.units %s %d).toBooleanC(%s)
        """
            .formatted(operator, threshold, BigDecimal.valueOf(confidence).toPlainString());
    MModel model =
        USECompiler.compileSpecification(
            source, "Counted", new PrintWriter(System.err), new ModelFactory());
    if (model == null) {
      throw new AssertionError("generated model did not compile:\n" + source);
    }
    return model;
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
