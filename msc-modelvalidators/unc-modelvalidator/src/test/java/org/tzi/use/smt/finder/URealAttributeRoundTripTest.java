package org.tzi.use.smt.finder;

import static org.junit.Assert.assertEquals;
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
import org.tzi.use.smt.config.RawConfiguration;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;
import org.tzi.use.uml.ocl.value.URealValue;
import org.tzi.use.uml.sys.MObject;
import org.tzi.use.uml.sys.MSystemState;

/** End-to-end proof that one configured UReal attribute survives solve and reconstruction. */
public class URealAttributeRoundTripTest {

  @Test
  public void configuredValueAndUncertaintyReconstructAsAGenuineURealValue() throws Exception {
    MModel model = compile(resourcePath("URealRoundTrip.use"));
    ConfigurationVocabulary vocabulary = ConfigurationVocabulary.fromModel(model);
    RawConfiguration raw =
        ConfigurationReader.read(resourcePath("URealRoundTrip.properties"), null);
    AnalysisConfiguration config =
        ConfigurationReader.normalize(raw, vocabulary).requireSupported();

    ModelFinderResult result = SmtModelFinder.find(model, config);

    assertTrue("expected SAT", result.satisfiable());
    MSystemState state = result.system().state();
    MObject reading = state.objectsOfClass(model.getClass("Reading")).iterator().next();
    Object reconstructed = reading.state(state).attributeValue("measurement");
    assertTrue(reconstructed instanceof URealValue);
    URealValue measurement = (URealValue) reconstructed;
    assertEquals(0.31, measurement.value(), 0.0);
    assertEquals(0.02, measurement.uncertainty(), 0.0);
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
        Objects.requireNonNull(URealAttributeRoundTripTest.class.getResource("/" + name)).toURI());
  }
}
