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
import org.tzi.use.uml.ocl.value.UBooleanValue;
import org.tzi.use.uml.sys.MObject;
import org.tzi.use.uml.sys.MSystemState;

/**
 * End-to-end proof that one configured {@code UBoolean} attribute survives solve and
 * reconstruction.
 *
 * <p>{@code UBoolean} is the THIRD U-type family and the first that is not a representative plus an
 * uncertainty: the proposal canonicalises it to a SINGLE truth probability {@code p} in {@code
 * [0,1]}, so its configured component is {@code _probability} rather than the {@code _value}/{@code
 * _uncertainty} pair {@code UReal} and {@code UInteger} share.
 */
public class UBooleanAttributeRoundTripTest {

  @Test
  public void aConfiguredProbabilityReconstructsAsAGenuineUBooleanValue() throws Exception {
    MModel model = compile(resourcePath("UBooleanRoundTrip.use"));
    ConfigurationVocabulary vocabulary = ConfigurationVocabulary.fromModel(model);
    RawConfiguration raw =
        ConfigurationReader.read(resourcePath("UBooleanRoundTrip.properties"), null);
    AnalysisConfiguration config =
        ConfigurationReader.normalize(raw, vocabulary).requireSupported();

    ModelFinderResult result = SmtModelFinder.find(model, config);

    assertTrue("expected SAT", result.satisfiable());
    MSystemState state = result.system().state();
    MObject detection = state.objectsOfClass(model.getClass("Detection")).iterator().next();
    Object reconstructed = detection.state(state).attributeValue("hitsTarget");
    assertTrue(
        "expected a genuine UBooleanValue, got " + reconstructed,
        reconstructed instanceof UBooleanValue);
    UBooleanValue hits = (UBooleanValue) reconstructed;
    assertEquals(0.9, hits.probability(), 0.0);
    assertTrue("USE canonicalises a UBoolean to (true, p)", hits.value());
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
        Objects.requireNonNull(UBooleanAttributeRoundTripTest.class.getResource("/" + name))
            .toURI());
  }
}
