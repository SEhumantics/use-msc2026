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
    return ConfigurationReader.normalize(
            ConfigurationReader.read(resourcePath("ReliablyFast.properties"), section),
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
