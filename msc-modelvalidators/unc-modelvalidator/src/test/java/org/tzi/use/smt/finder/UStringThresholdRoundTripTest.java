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
import org.tzi.use.uml.ocl.value.UStringValue;
import org.tzi.use.uml.sys.MObject;
import org.tzi.use.uncertainty.datatypes.UString;

/**
 * End-to-end oracle tests for {@code (<UString equality>).toBooleanC(theta)}, the fourth U-type
 * family's only projection.
 *
 * <p>The rules are {@code archive2/robust_utype_model_finding_proposal.md}'s own. Against an EXACT
 * string {@code r}, {@code UString(s, c_s)} yields {@code c_s} when {@code s = r} and {@code 1 -
 * c_s} otherwise; between two UStrings, with {@code b = c_s * c_r}, it yields {@code b} when the
 * spellings match and {@code 1 - b} otherwise. Every expected number below is recomputed from USE's
 * own {@code UString} inside the test rather than hard-coded, so these tests fail if that
 * arithmetic ever moves.
 *
 * <p>The {@code [below]} section is the proposal's own worked example: "{@code
 * UString('ALLY-7',0.70)} compared with exact {@code 'ALLY-7'} yields equality probability (0.70);
 * a required confidence of (0.80) rejects it despite a matching spelling."
 */
public class UStringThresholdRoundTripTest {

  private static final double CONFIDENCE = 0.80;
  private static final double PAIR_CONFIDENCE = 0.55;

  @Test
  public void atTheExactThresholdSolvesReconstructsAndUseEvaluatorConfirmsTrue() throws Exception {
    assertEquals(
        "the [at] section must sit EXACTLY on the threshold, or the >= tie is not exercised",
        CONFIDENCE,
        againstExactString("ALLY-7", 0.80, "ALLY-7"),
        0.0);

    MModel model = compile(resourcePath("CameraIdentity.use"));
    ModelFinderResult result = SmtModelFinder.find(model, configuration(model, "at"));

    assertTrue(result.satisfiable());
    assertEquals(new InvariantVerdict("Camera::ConfidentIdentity", true), result.verdicts().get(0));
    assertEquals("ALLY-7", reconstructed(model, result, "id").value());
    assertEquals(0.8, reconstructed(model, result, "id").confidence(), 0.0);
  }

  @Test
  public void aboveTheThresholdSolvesReconstructsAndUseEvaluatorConfirmsTrue() throws Exception {
    assertTrue(againstExactString("ALLY-7", 0.95, "ALLY-7") > CONFIDENCE);

    MModel model = compile(resourcePath("CameraIdentity.use"));
    ModelFinderResult result = SmtModelFinder.find(model, configuration(model, "above"));

    assertTrue(result.satisfiable());
    assertEquals(new InvariantVerdict("Camera::ConfidentIdentity", true), result.verdicts().get(0));
    assertEquals(0.95, reconstructed(model, result, "id").confidence(), 0.0);
  }

  /**
   * The discrepancy this family exists to show, and the proposal's own worked example. The spelling
   * MATCHES exactly, so nominal erasure -- which reads a {@code UString(s,c)} as its representative
   * spelling {@code s} and nothing else -- accepts outright. The real equality probability is only
   * {@code 0.70}, under the {@code 0.80} demanded, and the encoding must refuse it once enforced.
   */
  @Test
  public void belowTheThresholdIsFalseToUseAndBecomesUnsatWhenEnforced() throws Exception {
    MModel model = compile(resourcePath("CameraIdentity.use"));

    ModelFinderResult unchecked = SmtModelFinder.find(model, configuration(model, "belowInactive"));
    assertTrue(unchecked.satisfiable());
    assertEquals(
        "the spelling matches, so a crisp reading accepts",
        "ALLY-7",
        reconstructed(model, unchecked, "id").value());
    assertEquals(
        "USE's equality probability must fall under the demanded confidence",
        0.7,
        againstExactString("ALLY-7", 0.7, "ALLY-7"),
        0.0);
    assertTrue(againstExactString("ALLY-7", 0.7, "ALLY-7") < CONFIDENCE);

    assertFalse(SmtModelFinder.find(model, configuration(model, "below")).satisfiable());
  }

  /**
   * The finite spelling ENUMERATION doing real work. Two candidate spellings are configured and
   * only one of them matches the exact string the invariant names, so the solver has to pick it:
   * the other yields {@code 1 - 0.95 = 0.05}, nowhere near the threshold.
   */
  @Test
  public void theSolverPicksTheSpellingThatMatchesRatherThanItsComplement() throws Exception {
    assertTrue(againstExactString("ALLY-8", 0.95, "ALLY-7") < CONFIDENCE);

    MModel model = compile(resourcePath("CameraIdentity.use"));
    ModelFinderResult result = SmtModelFinder.find(model, configuration(model, "spelling"));

    assertTrue(result.satisfiable());
    assertEquals(new InvariantVerdict("Camera::ConfidentIdentity", true), result.verdicts().get(0));
    assertEquals("ALLY-7", reconstructed(model, result, "id").value());
  }

  /**
   * The {@code b = c_s * c_r} rule, which only UString-to-UString equality can state. Both stored
   * confidences are below the demanded one on their own; their PRODUCT is what clears it.
   */
  @Test
  public void twoUStringsComposeThroughTheProductOfTheirConfidences() throws Exception {
    double product = betweenUStrings("ALLY-7", 0.7, "ALLY-7", 0.85);
    assertEquals("the source's b = c_s * c_r rule", 0.7 * 0.85, product, 0.0);
    assertTrue(product >= PAIR_CONFIDENCE);

    MModel model = compile(resourcePath("CameraIdentity.use"));
    ModelFinderResult result = SmtModelFinder.find(model, configuration(model, "corroborated"));

    assertTrue(result.satisfiable());
    assertTrue(
        "USE's own evaluator must independently confirm the ENFORCED invariant on the"
            + " reconstructed snapshot: "
            + result.verdicts(),
        result.verdicts().contains(new InvariantVerdict("Camera::Corroborated", true)));
    assertEquals(0.7, reconstructed(model, result, "id").confidence(), 0.0);
    assertEquals(0.85, reconstructed(model, result, "witness").confidence(), 0.0);
  }

  /** USE's own exact-string equality probability, not a reimplementation of it. */
  private static double againstExactString(String spelling, double confidence, String exact) {
    // UStringValue.valueOf lifts a plain StringValue to confidence 1, which is what makes the
    // exact-string rule the c_s * 1 special case of the two-UString rule.
    return new UString(spelling, confidence).uEquals(new UString(exact, 1.0)).getC();
  }

  /** USE's own UString-to-UString equality probability. */
  private static double betweenUStrings(String left, double leftC, String right, double rightC) {
    return new UString(left, leftC).uEquals(new UString(right, rightC)).getC();
  }

  private static UStringValue reconstructed(
      MModel model, ModelFinderResult result, String attribute) {
    MObject object =
        result.system().state().objectsOfClass(model.getClass("Camera")).iterator().next();
    Object value = object.state(result.system().state()).attributeValue(attribute);
    assertTrue(
        "expected a reconstructed UStringValue, got " + value, value instanceof UStringValue);
    return (UStringValue) value;
  }

  private static AnalysisConfiguration configuration(MModel model, String section)
      throws URISyntaxException {
    return ConfigurationReader.normalize(
            ConfigurationReader.read(resourcePath("CameraIdentity.properties"), section),
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
        Objects.requireNonNull(UStringThresholdRoundTripTest.class.getResource("/" + name))
            .toURI());
  }
}
