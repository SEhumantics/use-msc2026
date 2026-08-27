package org.tzi.use.smt.finder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.PrintWriter;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import org.junit.Test;
import org.tzi.use.parser.use.USECompiler;
import org.tzi.use.smt.config.AnalysisConfiguration;
import org.tzi.use.smt.config.ConfigurationReader;
import org.tzi.use.smt.config.ConfigurationVocabulary;
import org.tzi.use.smt.verify.InvariantVerdict;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;
import org.tzi.use.uml.ocl.value.UBooleanValue;
import org.tzi.use.uml.sys.MObject;
import org.tzi.use.uncertainty.datatypes.UBoolean;

/**
 * End-to-end oracle tests for {@code (UBoolean-expression).toBooleanC(theta)}.
 *
 * <p>The composed shape is {@code (self.hitsTarget and self.isClear).toBooleanC(0.81)}, whose rule
 * is the proposal's own {@code p1 and p2 = p1 * p2} -- a PRODUCT, and therefore the one place this
 * family could have escaped {@code QF_LIRA}. It does not: both operand probabilities are finite
 * configured choices, so the product is evaluated at translation time by USE's own {@code UBoolean}
 * arithmetic and the solver only ever sees which choice was taken.
 *
 * <p>Every expected number below is recomputed from USE's own {@code UBoolean} inside the test
 * rather than hard-coded from a document, so the tests fail if that arithmetic ever moves.
 */
public class UBooleanThresholdRoundTripTest {

  private static final double CONFIDENCE = 0.81;

  @Test
  public void atTheExactThresholdSolvesReconstructsAndUseEvaluatorConfirmsTrue() throws Exception {
    assertEquals(
        "the [at] section must sit EXACTLY on the threshold, or the >= tie is not exercised",
        CONFIDENCE,
        conjunction(0.9, 0.9),
        0.0);

    MModel model = compile(resourcePath("ReliableSignal.use"));
    ModelFinderResult result = SmtModelFinder.find(model, configuration(model, "at"));

    assertTrue(result.satisfiable());
    assertEquals(1, result.verdicts().size());
    assertEquals(new InvariantVerdict("Detection::ReliableSignal", true), result.verdicts().get(0));
    assertEquals(0.9, reconstructed(model, result, "hitsTarget").probability(), 0.0);
    assertEquals(0.9, reconstructed(model, result, "isClear").probability(), 0.0);
  }

  @Test
  public void aboveTheThresholdSolvesReconstructsAndUseEvaluatorConfirmsTrue() throws Exception {
    assertTrue(conjunction(0.95, 0.95) > CONFIDENCE);

    MModel model = compile(resourcePath("ReliableSignal.use"));
    ModelFinderResult result = SmtModelFinder.find(model, configuration(model, "above"));

    assertTrue(result.satisfiable());
    assertEquals(new InvariantVerdict("Detection::ReliableSignal", true), result.verdicts().get(0));
    assertEquals(0.95, reconstructed(model, result, "hitsTarget").probability(), 0.0);
  }

  /**
   * The discrepancy this family exists to show. Both stored probabilities are above the {@code p >=
   * 0.5} nominal-erasure line, so erasing the uncertainty reads the conjunction as plainly {@code
   * true and true}; the real uncertain conjunction is only {@code 0.801}, under the {@code 0.81}
   * demanded, and the encoding must refuse it once the invariant is enforced.
   */
  @Test
  public void belowTheThresholdIsFalseToUseAndBecomesUnsatWhenEnforced() throws Exception {
    MModel model = compile(resourcePath("ReliableSignal.use"));

    ModelFinderResult unchecked = SmtModelFinder.find(model, configuration(model, "belowInactive"));
    assertTrue(unchecked.satisfiable());
    assertEquals(
        new InvariantVerdict("Detection::ReliableSignal", false), unchecked.verdicts().get(0));
    assertTrue(
        "crisp nominal erasure would read both stored probabilities as true",
        reconstructed(model, unchecked, "hitsTarget").probability() >= 0.5
            && reconstructed(model, unchecked, "isClear").probability() >= 0.5);
    assertTrue(
        "USE's uncertain conjunction must fall under the demanded confidence",
        conjunction(0.9, 0.89) < CONFIDENCE);

    assertFalse(SmtModelFinder.find(model, configuration(model, "below")).satisfiable());
  }

  /**
   * The finite-domain search itself. Each attribute is offered two candidate probabilities, so
   * there are four combinations and exactly ONE of them clears the threshold. The solver has to
   * pick it -- which is the whole point of expanding the product over the configured choices rather
   * than emitting a symbolic multiplication.
   */
  @Test
  public void theSolverPicksTheOneCombinationOfConfiguredProbabilitiesThatClearsTheThreshold()
      throws Exception {
    assertTrue(conjunction(0.5, 0.5) < CONFIDENCE);
    assertTrue(conjunction(0.5, 0.9) < CONFIDENCE);
    assertTrue(conjunction(0.9, 0.5) < CONFIDENCE);
    assertTrue(conjunction(0.9, 0.9) >= CONFIDENCE);

    MModel model = compile(resourcePath("ReliableSignal.use"));
    ModelFinderResult result = SmtModelFinder.find(model, configuration(model, "choice"));

    assertTrue(result.satisfiable());
    assertEquals(new InvariantVerdict("Detection::ReliableSignal", true), result.verdicts().get(0));
    assertEquals(0.9, reconstructed(model, result, "hitsTarget").probability(), 0.0);
    assertEquals(0.9, reconstructed(model, result, "isClear").probability(), 0.0);
  }

  /** USE's own uncertain conjunction, not a reimplementation of it. */
  private static double conjunction(double left, double right) {
    return new UBoolean(true, left).and(new UBoolean(true, right)).getC();
  }

  private static UBooleanValue reconstructed(
      MModel model, ModelFinderResult result, String attribute) {
    MObject object =
        result.system().state().objectsOfClass(model.getClass("Detection")).iterator().next();
    Object value = object.state(result.system().state()).attributeValue(attribute);
    assertTrue(
        "expected a reconstructed UBooleanValue, got " + value, value instanceof UBooleanValue);
    return (UBooleanValue) value;
  }

  private static AnalysisConfiguration configuration(MModel model, String section)
      throws URISyntaxException {
    return ConfigurationReader.normalize(
            ConfigurationReader.read(resourcePath("ReliableSignal.properties"), section),
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
        Objects.requireNonNull(UBooleanThresholdRoundTripTest.class.getResource("/" + name))
            .toURI());
  }
}
