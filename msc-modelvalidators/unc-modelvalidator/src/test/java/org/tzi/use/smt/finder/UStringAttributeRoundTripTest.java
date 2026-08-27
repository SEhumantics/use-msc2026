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
import org.tzi.use.uml.ocl.value.UStringValue;
import org.tzi.use.uml.sys.MObject;
import org.tzi.use.uml.sys.MSystemState;

/**
 * End-to-end proof that one configured {@code UString} attribute survives solve and reconstruction.
 *
 * <p>{@code UString} is the FOURTH and final U-type family of {@code
 * THESIS_SMT_MODEL_FINDER_PLAN.md} 7.2, and the only one whose representative is not a number:
 * {@code archive2/robust_utype_model_finding_proposal.md} describes it as "a representative
 * spelling and confidence", and its solver-representation table as "finite enum value (s) plus
 * confidence (c)". Its configured components are therefore {@code _value} (the finite spelling
 * enumeration) and {@code _confidence}.
 *
 * <p><b>Why {@code _confidence} and not {@code _uncertainty}, which is what the Java constructor
 * parameter is called.</b> {@code UStringValue(String str, double uncertainty)} stores its second
 * argument straight into {@code UString.sConf} and reads it back through {@code confidence()}; the
 * evaluator's own {@code UString.calculateConf} is {@code this.sConf * u.sConf}. The stored
 * quantity is a CONFIDENCE, and the constructor parameter's name is the misnomer -- which this test
 * pins by measurement rather than by argument.
 */
public class UStringAttributeRoundTripTest {

  @Test
  public void aConfiguredSpellingAndConfidenceReconstructAsAGenuineUStringValue() throws Exception {
    MModel model = compile(resourcePath("UStringRoundTrip.use"));
    ConfigurationVocabulary vocabulary = ConfigurationVocabulary.fromModel(model);
    RawConfiguration raw =
        ConfigurationReader.read(resourcePath("UStringRoundTrip.properties"), null);
    AnalysisConfiguration config =
        ConfigurationReader.normalize(raw, vocabulary).requireSupported();

    ModelFinderResult result = SmtModelFinder.find(model, config);

    assertTrue("expected SAT", result.satisfiable());
    MSystemState state = result.system().state();
    MObject camera = state.objectsOfClass(model.getClass("Camera")).iterator().next();
    Object reconstructed = camera.state(state).attributeValue("id");
    assertTrue(
        "expected a genuine UStringValue, got " + reconstructed,
        reconstructed instanceof UStringValue);
    UStringValue id = (UStringValue) reconstructed;
    assertEquals("ALLY-7", id.value());
    assertEquals(0.7, id.confidence(), 0.0);
  }

  /**
   * The naming subtlety, settled against the real class instead of against the parameter name: the
   * second constructor argument IS what {@code confidence()} returns, unchanged and uncomplemented.
   */
  @Test
  public void theSecondConstructorArgumentIsTheConfidenceNotItsComplement() {
    assertEquals(0.7, new UStringValue("ALLY-7", 0.7).confidence(), 0.0);
    assertEquals(
        1.0,
        UStringValue.valueOf(new org.tzi.use.uml.ocl.value.StringValue("x")).confidence(),
        0.0);
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
        Objects.requireNonNull(UStringAttributeRoundTripTest.class.getResource("/" + name))
            .toURI());
  }
}
