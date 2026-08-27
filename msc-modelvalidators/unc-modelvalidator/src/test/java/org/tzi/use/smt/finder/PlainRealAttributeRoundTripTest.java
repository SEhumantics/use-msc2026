package org.tzi.use.smt.finder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

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
import org.tzi.use.smt.config.RawConfiguration;
import org.tzi.use.smt.encode.SmtTranslationException;
import org.tzi.use.smt.verify.InvariantVerdict;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;
import org.tzi.use.uml.ocl.value.RealValue;
import org.tzi.use.uml.sys.MObject;
import org.tzi.use.uml.sys.MSystemState;

/**
 * End-to-end coverage for a PLAIN {@code Real} attribute -- {@link
 * org.tzi.use.smt.encode.AttributeType#REAL}, not a {@code UReal} component.
 *
 * <p>Why this exists: {@code AttributeType.REAL} and {@link
 * org.tzi.use.smt.encode.AttributeEncoder}'s real guard were written in Phase 2 and every later
 * test that looked like it exercised them actually exercised the {@code _value}/{@code
 * _uncertainty} components of a {@code UReal} instead. Before Study B's real-valued supersession
 * row could rest on this path, the path itself needed a test that a plain {@code Real} attribute
 * really is declared, really is constrained by its configured bounds, really is solved over exact
 * SMT rationals, and really is reconstructed into a USE {@link RealValue} the USE evaluator then
 * confirms.
 *
 * <p>The second test pins the other half of that boundary. The translated fragment does NOT include
 * Real LITERALS: {@code self.x > 0.25} is refused explicitly with a source location rather than
 * silently weakened. That refusal is load-bearing for the corpus models built on this path (they
 * state their thresholds with Integer literals for exactly this reason) and for the contrast with
 * the incumbent, whose transformer truncates {@code 0.25} to {@code 0} and carries on.
 */
public class PlainRealAttributeRoundTripTest {

  @Test
  public void aPlainRealAttributeIsSolvedExactlyAndReconstructedAsARealValue() throws Exception {
    MModel model = compile(resourcePath("PlainRealRoundTrip.use"));
    ConfigurationVocabulary vocabulary = ConfigurationVocabulary.fromModel(model);
    RawConfiguration raw =
        ConfigurationReader.read(resourcePath("PlainRealRoundTrip.properties"), null);
    AnalysisConfiguration config =
        ConfigurationReader.normalize(raw, vocabulary).requireSupported();

    ModelFinderResult result = SmtModelFinder.find(model, config);

    assertTrue(
        "expected SAT for three ordered cuts inside the unit interval", result.satisfiable());
    assertTrue(
        "the USE evaluator must independently confirm the reconstructed witness",
        result.allActiveInvariantsHold());
    assertEquals(2, result.verdicts().size());
    for (InvariantVerdict verdict : result.verdicts()) {
      assertTrue(verdict.invariantName() + " must be re-evaluated TRUE", verdict.holds());
    }

    MSystemState state = result.system().state();
    MObject calibration = state.objectsOfClass(model.getClass("Calibration")).iterator().next();
    double low = real(calibration, state, "lowCut");
    double mid = real(calibration, state, "midCut");
    double high = real(calibration, state, "highCut");

    // Ground truth restated here in plain arithmetic, independently of either solver.
    assertTrue("lowCut must be strictly positive, was " + low, low > 0.0);
    assertTrue("lowCut < midCut, was " + low + " / " + mid, low < mid);
    assertTrue("midCut < highCut, was " + mid + " / " + high, mid < high);
    assertTrue("highCut must be strictly below 1, was " + high, high < 1.0);

    // The configured range is exactly the incumbent's default real range, so the witness is inside
    // it -- what the incumbent cannot do is land BETWEEN its 0.5 grid points. Three distinct values
    // are required inside (0, 1) and that interval holds exactly ONE multiple of 0.5, so at least
    // two of these three are necessarily off that grid. Assert it directly rather than by argument.
    assertTrue("witness must stay inside the configured range", low >= -2.0 && high <= 2.0);
    assertTrue(
        "at least two of the three cuts must be off the incumbent's 0.5 grid",
        offHalfGrid(low) && offHalfGrid(mid)
            || offHalfGrid(low) && offHalfGrid(high)
            || offHalfGrid(mid) && offHalfGrid(high));
    assertNotEquals(low, mid, 0.0);
    assertNotEquals(mid, high, 0.0);
  }

  @Test
  public void aRealLiteralIsRefusedExplicitlyRatherThanTruncated() throws Exception {
    MModel model = compile(resourcePath("PlainRealLiteral.use"));
    ConfigurationVocabulary vocabulary = ConfigurationVocabulary.fromModel(model);
    RawConfiguration raw =
        ConfigurationReader.read(resourcePath("PlainRealLiteral.properties"), null);
    AnalysisConfiguration config =
        ConfigurationReader.normalize(raw, vocabulary).requireSupported();

    try {
      SmtModelFinder.find(model, config);
      fail("a Real literal is outside the translated fragment and must be refused, not answered");
    } catch (SmtTranslationException expected) {
      assertTrue(
          "the refusal must name the construct it refuses, got: " + expected.getMessage(),
          expected.getMessage().contains("Real literal"));
      assertTrue(
          "the refusal must name the invariant it refuses, got: " + expected.getMessage(),
          expected.getMessage().contains("Calibration::AboveAQuarter"));
    }
  }

  private static boolean offHalfGrid(double value) {
    return Math.abs(value * 2.0 - Math.rint(value * 2.0)) > 1.0e-9;
  }

  private static double real(MObject object, MSystemState state, String attribute) {
    Object value = object.state(state).attributeValue(attribute);
    assertTrue(
        attribute + " must reconstruct as a USE RealValue, was " + value.getClass().getName(),
        value instanceof RealValue);
    return ((RealValue) value).value();
  }

  private static MModel compile(Path file) throws Exception {
    String source = Files.readString(file);
    PrintWriter err = new PrintWriter(System.err);
    MModel model =
        USECompiler.compileSpecification(
            source, file.getFileName().toString(), err, new ModelFactory());
    err.flush();
    return model;
  }

  private static Path resourcePath(String name) throws URISyntaxException {
    return Path.of(
        Objects.requireNonNull(PlainRealAttributeRoundTripTest.class.getResource("/" + name))
            .toURI());
  }
}
