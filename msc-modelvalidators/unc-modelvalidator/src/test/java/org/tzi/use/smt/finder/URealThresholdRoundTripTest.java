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
import org.tzi.use.uml.ocl.value.URealValue;
import org.tzi.use.uml.sys.MObject;

/** End-to-end oracle tests for the first supported UReal confidence-threshold shape. */
public class URealThresholdRoundTripTest {

  @Test
  public void aboveBoundarySolvesReconstructsAndUseEvaluatorConfirmsTrue() throws Exception {
    MModel model = compile(resourcePath("ReliablyFast.use"));

    ModelFinderResult result = SmtModelFinder.find(model, configuration(model, "above"));

    assertTrue(result.satisfiable());
    assertEquals(1, result.verdicts().size());
    assertEquals(
        new InvariantVerdict("UnidentifiedObject::ReliablyFast", true), result.verdicts().get(0));
    URealValue speed = reconstructedSpeed(model, result);
    assertEquals(0.34, speed.value(), 0.0);
    assertEquals(0.02, speed.uncertainty(), 0.0);
  }

  @Test
  public void belowBoundaryIsFalseToUseAndBecomesUnsatWhenEnforced() throws Exception {
    MModel model = compile(resourcePath("ReliablyFast.use"));

    ModelFinderResult unchecked = SmtModelFinder.find(model, configuration(model, "belowInactive"));
    assertTrue(unchecked.satisfiable());
    assertEquals(1, unchecked.verdicts().size());
    assertEquals(
        new InvariantVerdict("UnidentifiedObject::ReliablyFast", false),
        unchecked.verdicts().get(0));
    URealValue speed = reconstructedSpeed(model, unchecked);
    assertEquals(0.31, speed.value(), 0.0);
    assertEquals(0.02, speed.uncertainty(), 0.0);
    assertTrue("crisp nominal erasure would accept 0.31 > 0.30", speed.value() > 0.30);

    ModelFinderResult enforced = SmtModelFinder.find(model, configuration(model, "belowActive"));
    assertFalse(enforced.satisfiable());
  }

  @Test
  public void belowAtAndAboveEvaluatorBoundaryHaveTheExpectedOutcomes() throws Exception {
    MModel model = compile(resourcePath("ReliablyFast.use"));

    ModelFinderResult belowUnchecked =
        SmtModelFinder.find(model, configuration(model, "boundaryBelowInactive"));
    assertTrue(belowUnchecked.satisfiable());
    assertEquals(
        new InvariantVerdict("UnidentifiedObject::ReliablyFast", false),
        belowUnchecked.verdicts().get(0));
    URealValue below = reconstructedSpeed(model, belowUnchecked);
    assertEquals(0.3328970695, below.value(), 0.0);
    assertEquals(0.02, below.uncertainty(), 0.0);

    ModelFinderResult belowEnforced =
        SmtModelFinder.find(model, configuration(model, "boundaryBelowActive"));
    assertFalse(belowEnforced.satisfiable());

    ModelFinderResult at = SmtModelFinder.find(model, configuration(model, "boundaryAtActive"));
    assertTrue(at.satisfiable());
    assertEquals(
        new InvariantVerdict("UnidentifiedObject::ReliablyFast", true), at.verdicts().get(0));
    URealValue atSpeed = reconstructedSpeed(model, at);
    assertEquals(0.3328970696, atSpeed.value(), 0.0);
    assertEquals(0.02, atSpeed.uncertainty(), 0.0);

    ModelFinderResult above =
        SmtModelFinder.find(model, configuration(model, "boundaryAboveActive"));
    assertTrue(above.satisfiable());
    assertEquals(
        new InvariantVerdict("UnidentifiedObject::ReliablyFast", true), above.verdicts().get(0));
    URealValue aboveSpeed = reconstructedSpeed(model, above);
    assertEquals(0.3328970697, aboveSpeed.value(), 0.0);
    assertEquals(0.02, aboveSpeed.uncertainty(), 0.0);
  }

  @Test
  public void lessThanBelowAtAndAboveBoundaryMatchTheUseEvaluator() throws Exception {
    assertLessDirectionBoundary(
        "ReliablySlow.use", "ReliablySlow.properties", "UnidentifiedObject::ReliablySlow");
  }

  @Test
  public void lessThanOrEqualBelowAtAndAboveBoundaryMatchTheUseEvaluator() throws Exception {
    assertLessDirectionBoundary(
        "ReliablySlowOrEqual.use",
        "ReliablySlowOrEqual.properties",
        "UnidentifiedObject::ReliablySlowOrEqual");
  }

  @Test
  public void implicationConsequentKeepsItsParentsPositivePolarity() throws Exception {
    assertEvaluatorFalseThenUnsatWhenEnforced(
        "ThresholdInConsequent.use",
        "ThresholdInConsequent.properties",
        "UnidentifiedObject::ThresholdInConsequent");
  }

  @Test
  public void doubleNegationRestoresPositivePolarity() throws Exception {
    assertEvaluatorFalseThenUnsatWhenEnforced(
        "DoubleNegatedThreshold.use",
        "DoubleNegatedThreshold.properties",
        "UnidentifiedObject::DoubleNegatedThreshold");
  }

  @Test
  public void andAndOrOperandsKeepTheirParentsPolarity() throws Exception {
    MModel model = compile(resourcePath("ThresholdInAndOr.use"));

    ModelFinderResult unchecked =
        SmtModelFinder.find(
            model, configuration(model, "ThresholdInAndOr.properties", "bothInactive"));
    assertTrue(unchecked.satisfiable());
    assertEquals(
        new InvariantVerdict("UnidentifiedObject::ThresholdInAnd", false),
        verdict(unchecked, "UnidentifiedObject::ThresholdInAnd"));
    assertEquals(
        new InvariantVerdict("UnidentifiedObject::ThresholdInOr", false),
        verdict(unchecked, "UnidentifiedObject::ThresholdInOr"));

    assertFalse(
        SmtModelFinder.find(model, configuration(model, "ThresholdInAndOr.properties", "andActive"))
            .satisfiable());
    assertFalse(
        SmtModelFinder.find(model, configuration(model, "ThresholdInAndOr.properties", "orActive"))
            .satisfiable());
  }

  @Test
  public void andAndOrRightOperandsKeepTheirParentsPolarity() throws Exception {
    MModel model = compile(resourcePath("ThresholdInAndOr.use"));

    ModelFinderResult unchecked =
        SmtModelFinder.find(
            model, configuration(model, "ThresholdInAndOr.properties", "bothInactive"));
    assertTrue(unchecked.satisfiable());
    assertEquals(
        new InvariantVerdict("UnidentifiedObject::ThresholdInAndRight", false),
        verdict(unchecked, "UnidentifiedObject::ThresholdInAndRight"));
    assertEquals(
        new InvariantVerdict("UnidentifiedObject::ThresholdInOrRight", false),
        verdict(unchecked, "UnidentifiedObject::ThresholdInOrRight"));

    assertFalse(
        SmtModelFinder.find(
                model, configuration(model, "ThresholdInAndOr.properties", "andRightActive"))
            .satisfiable());
    assertFalse(
        SmtModelFinder.find(
                model, configuration(model, "ThresholdInAndOr.properties", "orRightActive"))
            .satisfiable());
  }

  private static void assertEvaluatorFalseThenUnsatWhenEnforced(
      String modelResource, String configurationResource, String invariantName) throws Exception {
    MModel model = compile(resourcePath(modelResource));

    ModelFinderResult unchecked =
        SmtModelFinder.find(model, configuration(model, configurationResource, "inactive"));
    assertTrue(unchecked.satisfiable());
    assertEquals(new InvariantVerdict(invariantName, false), verdict(unchecked, invariantName));
    URealValue speed = reconstructedSpeed(model, unchecked);
    assertEquals(0.3328970695, speed.value(), 0.0);
    assertEquals(0.02, speed.uncertainty(), 0.0);

    assertFalse(
        SmtModelFinder.find(model, configuration(model, configurationResource, "active"))
            .satisfiable());
  }

  private static InvariantVerdict verdict(ModelFinderResult result, String invariantName) {
    return result.verdicts().stream()
        .filter(candidate -> candidate.invariantName().equals(invariantName))
        .findFirst()
        .orElseThrow(() -> new AssertionError("missing evaluator verdict for " + invariantName));
  }

  private static void assertLessDirectionBoundary(
      String modelResource, String configurationResource, String invariantName) throws Exception {
    MModel model = compile(resourcePath(modelResource));

    ModelFinderResult below =
        SmtModelFinder.find(
            model, configuration(model, configurationResource, "boundaryBelowActive"));
    assertTrue(below.satisfiable());
    assertEquals(new InvariantVerdict(invariantName, true), below.verdicts().get(0));
    URealValue belowSpeed = reconstructedSpeed(model, below);
    assertEquals(0.2671029303, belowSpeed.value(), 0.0);
    assertEquals(0.02, belowSpeed.uncertainty(), 0.0);

    ModelFinderResult at =
        SmtModelFinder.find(model, configuration(model, configurationResource, "boundaryAtActive"));
    assertTrue(at.satisfiable());
    assertEquals(new InvariantVerdict(invariantName, true), at.verdicts().get(0));
    URealValue atSpeed = reconstructedSpeed(model, at);
    assertEquals(0.2671029304, atSpeed.value(), 0.0);
    assertEquals(0.02, atSpeed.uncertainty(), 0.0);

    ModelFinderResult aboveUnchecked =
        SmtModelFinder.find(
            model, configuration(model, configurationResource, "boundaryAboveInactive"));
    assertTrue(aboveUnchecked.satisfiable());
    assertEquals(new InvariantVerdict(invariantName, false), aboveUnchecked.verdicts().get(0));
    URealValue aboveSpeed = reconstructedSpeed(model, aboveUnchecked);
    assertEquals(0.2671029305, aboveSpeed.value(), 0.0);
    assertEquals(0.02, aboveSpeed.uncertainty(), 0.0);

    ModelFinderResult aboveEnforced =
        SmtModelFinder.find(
            model, configuration(model, configurationResource, "boundaryAboveActive"));
    assertFalse(aboveEnforced.satisfiable());
  }

  private static URealValue reconstructedSpeed(MModel model, ModelFinderResult result) {
    MObject object =
        result
            .system()
            .state()
            .objectsOfClass(model.getClass("UnidentifiedObject"))
            .iterator()
            .next();
    return (URealValue) object.state(result.system().state()).attributeValue("speed");
  }

  private static AnalysisConfiguration configuration(MModel model, String section)
      throws URISyntaxException {
    return configuration(model, "ReliablyFast.properties", section);
  }

  private static AnalysisConfiguration configuration(MModel model, String resource, String section)
      throws URISyntaxException {
    return ConfigurationReader.normalize(
            ConfigurationReader.read(resourcePath(resource), section),
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
        Objects.requireNonNull(URealThresholdRoundTripTest.class.getResource("/" + name)).toURI());
  }
}
