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
import org.tzi.use.uml.ocl.value.UIntegerValue;
import org.tzi.use.uml.sys.MObject;
import org.tzi.use.uml.sys.MSystemState;

/**
 * End-to-end proof that one configured UInteger attribute survives solve and reconstruction.
 *
 * <p>The UReal twin of this test is {@link URealAttributeRoundTripTest}. What differs is exactly
 * what {@code archive2/robust_utype_model_finding_proposal.md}'s UInteger section says differs: the
 * representative lives on the SMT Int sort while the uncertainty stays a Real, and reconstruction
 * therefore produces a genuine {@link UIntegerValue} rather than a {@code URealValue}.
 */
public class UIntegerAttributeRoundTripTest {

  @Test
  public void configuredValueAndUncertaintyReconstructAsAGenuineUIntegerValue() throws Exception {
    MModel model = compile(resourcePath("UIntegerRoundTrip.use"));
    ConfigurationVocabulary vocabulary = ConfigurationVocabulary.fromModel(model);
    RawConfiguration raw =
        ConfigurationReader.read(resourcePath("UIntegerRoundTrip.properties"), null);
    AnalysisConfiguration config =
        ConfigurationReader.normalize(raw, vocabulary).requireSupported();

    ModelFinderResult result = SmtModelFinder.find(model, config);

    assertTrue("expected SAT", result.satisfiable());
    MSystemState state = result.system().state();
    MObject sample = state.objectsOfClass(model.getClass("Sample")).iterator().next();
    Object reconstructed = sample.state(state).attributeValue("count");
    assertTrue(
        "a UInteger attribute must reconstruct as a UIntegerValue, not a URealValue: "
            + reconstructed,
        reconstructed instanceof UIntegerValue);
    UIntegerValue count = (UIntegerValue) reconstructed;
    assertEquals(7, count.value());
    assertEquals(1.0, count.uncertainty(), 0.0);
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
        Objects.requireNonNull(UIntegerAttributeRoundTripTest.class.getResource("/" + name))
            .toURI());
  }
}
