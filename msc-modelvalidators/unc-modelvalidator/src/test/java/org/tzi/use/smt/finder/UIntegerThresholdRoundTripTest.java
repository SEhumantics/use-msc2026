package org.tzi.use.smt.finder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.PrintWriter;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.junit.Test;
import org.tzi.use.parser.use.USECompiler;
import org.tzi.use.smt.config.AnalysisConfiguration;
import org.tzi.use.smt.config.AttributeDomain;
import org.tzi.use.smt.config.ClassScope;
import org.tzi.use.smt.config.ConfigurationReader;
import org.tzi.use.smt.config.ConfigurationVocabulary;
import org.tzi.use.smt.config.QueryExpr;
import org.tzi.use.smt.verify.InvariantVerdict;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;
import org.tzi.use.uml.ocl.value.UIntegerValue;
import org.tzi.use.uml.sys.MObject;
import org.tzi.use.uncertainty.datatypes.UInteger;

/**
 * End-to-end oracle tests for {@code (UInteger attribute op crisp literal).toBooleanC(theta)}.
 *
 * <p>This is the proposal's own worked UInteger example: {@code (self.units > 5).toBooleanC(0.95)}
 * with {@code sigma = 1.0}. The threshold MATHEMATICS is not new -- {@code UInteger.gt} widens to
 * {@code UReal.gt} in USE itself, so the very same evaluator-derived boundary applies. What is new
 * is that the representative lives on the SMT Int sort, so the solver's own integer theory performs
 * the rounding the proposal describes: the real-valued transition sits at {@code mu =
 * 6.6448534751573820}, and the least admissible INTEGER representative is therefore 7.
 *
 * <p>Every expected number below is recomputed from USE's own evaluator inside the test rather than
 * hard-coded from a document, so the tests fail if that evaluator ever moves.
 */
public class UIntegerThresholdRoundTripTest {

  private static final double SIGMA = 1.0;
  private static final int LITERAL = 5;
  private static final double CONFIDENCE = 0.95;

  @Test
  public void atBoundarySolvesReconstructsAndUseEvaluatorConfirmsTrue() throws Exception {
    MModel model = compile(resourcePath("ReliableCount.use"));

    ModelFinderResult result = SmtModelFinder.find(model, configuration(model, "at"));

    assertTrue(result.satisfiable());
    assertEquals(1, result.verdicts().size());
    assertEquals(new InvariantVerdict("Batch::ReliableCount", true), result.verdicts().get(0));
    UIntegerValue units = reconstructedUnits(model, result);
    assertEquals(7, units.value());
    assertEquals(SIGMA, units.uncertainty(), 0.0);
  }

  @Test
  public void aboveBoundarySolvesReconstructsAndUseEvaluatorConfirmsTrue() throws Exception {
    MModel model = compile(resourcePath("ReliableCount.use"));

    ModelFinderResult result = SmtModelFinder.find(model, configuration(model, "above"));

    assertTrue(result.satisfiable());
    assertEquals(new InvariantVerdict("Batch::ReliableCount", true), result.verdicts().get(0));
    UIntegerValue units = reconstructedUnits(model, result);
    assertEquals(8, units.value());
    assertEquals(SIGMA, units.uncertainty(), 0.0);
  }

  /**
   * The nominal-erasure discrepancy in its UInteger form: crisp {@code 6 > 5} is plainly true, so
   * erasing the uncertainty would accept this snapshot. USE's own uncertainty-aware evaluator gives
   * the comparison a probability of only about 0.84134, well under the 0.95 demanded, and the
   * encoding must refuse it once the invariant is enforced.
   */
  @Test
  public void belowBoundaryIsFalseToUseAndBecomesUnsatWhenEnforced() throws Exception {
    MModel model = compile(resourcePath("ReliableCount.use"));

    ModelFinderResult unchecked = SmtModelFinder.find(model, configuration(model, "belowInactive"));
    assertTrue(unchecked.satisfiable());
    assertEquals(new InvariantVerdict("Batch::ReliableCount", false), unchecked.verdicts().get(0));
    UIntegerValue units = reconstructedUnits(model, unchecked);
    assertEquals(6, units.value());
    assertEquals(SIGMA, units.uncertainty(), 0.0);
    assertTrue("crisp nominal erasure would accept 6 > 5", units.value() > LITERAL);
    assertTrue(
        "USE's uncertainty-aware reading must be below the demanded confidence",
        probability(6) < CONFIDENCE);

    assertFalse(SmtModelFinder.find(model, configuration(model, "below")).satisfiable());
  }

  /**
   * The adversarial heart of this slice, and the one place a boundary correct for reals can be off
   * by one for integers. The real-valued transition is strictly between 6 and 7; the ONLY reason
   * the answer is 7 is that the representative is an Int. Both candidates are offered to the
   * solver, and it must return 7.
   */
  @Test
  public void theSolversIntegerTheoryRoundsTheRealValuedBoundaryUpToTheLeastAdmissibleInteger()
      throws Exception {
    double boundary = evaluatorBoundary();
    assertTrue(
        "the real-valued transition must fall strictly between two integers, or this test proves"
            + " nothing about rounding: "
            + boundary,
        boundary > 6.0 && boundary < 7.0);
    assertTrue("6 must fail USE's own evaluator", probability(6) < CONFIDENCE);
    assertTrue("7 must pass USE's own evaluator", probability(7) >= CONFIDENCE);

    MModel model = compile(resourcePath("ReliableCount.use"));
    ModelFinderResult result = SmtModelFinder.find(model, configuration(model, "rounding"));

    assertTrue(result.satisfiable());
    assertEquals(new InvariantVerdict("Batch::ReliableCount", true), result.verdicts().get(0));
    UIntegerValue units = reconstructedUnits(model, result);
    assertEquals("both 6 and 7 were offered; only the Int sort rules 6 out", 7, units.value());
  }

  /**
   * The sharpest form of the rounding claim, and the one an enumerated domain cannot make: the
   * representative is left FREE over a whole integer range, so nothing but the Int sort stands
   * between the solver and the non-integral boundary itself.
   *
   * <p>The linear constraint the encoding emits is satisfied by every real from {@code
   * 6.6448534751573820} upward. A Real representative would be free to answer {@code 6.645}, which
   * USE's evaluator would agree is true but which is not a UInteger at all. Declaring the symbol on
   * the Int sort is what forces the solver to round -- and this test is the one that fails if that
   * declaration ever regresses to a Real, which is precisely the off-by-one a boundary correct for
   * reals invites.
   */
  @Test
  public void aFreeIntegerRepresentativeIsRoundedByTheSolverRatherThanByTheDecoder()
      throws Exception {
    double boundary = evaluatorBoundary();
    assertTrue(
        "the boundary must be non-integral, or a Real representative could not differ from an"
            + " integer one and this test would prove nothing: "
            + boundary,
        boundary != Math.rint(boundary));

    MModel model = compile(resourcePath("ReliableCount.use"));
    ModelFinderResult result = SmtModelFinder.find(model, freeRangeConfiguration());

    assertTrue(result.satisfiable());
    assertEquals(new InvariantVerdict("Batch::ReliableCount", true), result.verdicts().get(0));
    UIntegerValue units = reconstructedUnits(model, result);
    assertEquals(SIGMA, units.uncertainty(), 0.0);
    assertTrue(
        "the solver must return an integer at or above the least admissible one, not the"
            + " real-valued boundary it also satisfies: got "
            + units.value(),
        units.value() >= (int) Math.ceil(boundary));
    assertTrue(
        "and USE's own evaluator must independently agree the returned integer clears the"
            + " confidence demand",
        probability(units.value()) >= CONFIDENCE);
  }

  /**
   * A representative free over the whole integer range 0..20, with no enumerated candidates at all.
   * Built in code rather than in the {@code .properties} corpus because the legacy key format
   * carries no range syntax for a U-type COMPONENT -- only enumerated sets -- and an enumerated set
   * would decide the rounding question before the solver ever saw it.
   */
  private static AnalysisConfiguration freeRangeConfiguration() {
    return new AnalysisConfiguration(
        List.of(new ClassScope("Batch", 1, 1)),
        List.of(),
        List.of(
            new AttributeDomain(
                "Batch",
                "units",
                "value",
                List.of(),
                java.math.BigDecimal.ZERO,
                java.math.BigDecimal.valueOf(20)),
            new AttributeDomain(
                "Batch",
                "units",
                "uncertainty",
                List.of(java.math.BigDecimal.valueOf(SIGMA).toPlainString()),
                null,
                null)),
        Set.of("Batch::ReliableCount"),
        QueryExpr.SATISFY,
        java.time.Duration.ofSeconds(30),
        1);
  }

  /**
   * The widening the proposal names, asserted against USE rather than assumed: {@code UInteger.gt}
   * is defined as {@code toUReal().gt(...)}, so the UInteger and UReal readings of the same
   * representative agree bit for bit. This is why no second threshold path exists.
   */
  @Test
  public void theUIntegerComparisonIsExactlyItsURealWidening() {
    for (int n = 3; n <= 9; n++) {
      assertEquals(
          "UInteger(" + n + ", " + SIGMA + ") > " + LITERAL + " must widen to the UReal reading",
          new org.tzi.use.uncertainty.datatypes.UReal(n, SIGMA)
              .gt(new org.tzi.use.uncertainty.datatypes.UReal(LITERAL))
              .getC(),
          probability(n),
          0.0);
    }
  }

  /** USE's own probability for {@code UInteger(n, SIGMA) > LITERAL}. */
  private static double probability(int n) {
    return new UInteger(n, SIGMA).gt(new UInteger(LITERAL)).getC();
  }

  /**
   * The representative at which USE's OWN evaluator flips, found by bisecting the real comparison
   * over a CONTINUOUS representative. Deliberately not {@code URealThresholdBoundary}: an oracle
   * computed by the code under test proves nothing.
   */
  private static double evaluatorBoundary() {
    double low = LITERAL - 8.0 * SIGMA;
    double high = LITERAL + 8.0 * SIGMA;
    for (int i = 0; i < 200; i++) {
      double middle = low + (high - low) / 2.0;
      if (new org.tzi.use.uncertainty.datatypes.UReal(middle, SIGMA)
              .gt(new org.tzi.use.uncertainty.datatypes.UReal(LITERAL))
              .getC()
          >= CONFIDENCE) {
        high = middle;
      } else {
        low = middle;
      }
    }
    return high;
  }

  private static UIntegerValue reconstructedUnits(MModel model, ModelFinderResult result) {
    MObject object =
        result.system().state().objectsOfClass(model.getClass("Batch")).iterator().next();
    Object value = object.state(result.system().state()).attributeValue("units");
    assertTrue(
        "expected a reconstructed UIntegerValue, got " + value, value instanceof UIntegerValue);
    return (UIntegerValue) value;
  }

  private static AnalysisConfiguration configuration(MModel model, String section)
      throws URISyntaxException {
    return ConfigurationReader.normalize(
            ConfigurationReader.read(resourcePath("ReliableCount.properties"), section),
            ConfigurationVocabulary.fromModel(model))
        .requireSupported();
  }

  private static MModel compile(Path file) throws Exception {
    PrintWriter err = new PrintWriter(System.err);
    MModel model =
        USECompiler.compileSpecification(
            Files.readString(file), file.getFileName().toString(), err, new ModelFactory());
    err.flush();
    if (model == null) {
      throw new IllegalStateException("model did not compile: " + file);
    }
    return model;
  }

  private static Path resourcePath(String name) throws URISyntaxException {
    return Path.of(
        Objects.requireNonNull(UIntegerThresholdRoundTripTest.class.getResource("/" + name))
            .toURI());
  }
}
