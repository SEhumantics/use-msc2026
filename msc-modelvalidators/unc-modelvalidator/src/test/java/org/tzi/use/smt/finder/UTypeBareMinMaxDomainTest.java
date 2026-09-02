package org.tzi.use.smt.finder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
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
import org.tzi.use.smt.encode.FragmentBoundary;
import org.tzi.use.smt.encode.SmtTranslationException;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;

/**
 * End-to-end proof, through the real {@code ConfigurationReader.normalize} + {@link
 * SmtModelFinder#find} pipeline, of the round-2 census's root-caused U-type routing bug: {@code
 * ConfigurationReader} (~161-206, ~601-602) accepts a bare {@code _min}/{@code _max} for ANY
 * attribute type, including {@code UReal}, without filtering it out as the wrong domain shape for
 * a U-type. Reusing the same real {@code UReal} fixture {@link URealAttributeRoundTripTest} uses
 * (just its {@code .use} model, not its own well-formed {@code .properties}), with a hand-written
 * configuration that supplies {@code Reading_measurement_min}/{@code _max} -- the shape every OTHER
 * attribute type in this fixture would accept -- instead of the paired {@code
 * Reading_measurement_value}/{@code _uncertainty} a {@code UReal} attribute actually needs.
 *
 * <p>Before the fix, this reached {@link org.tzi.use.smt.encode.AttributeEncoder#encode} and threw
 * a raw, unwrapped {@link IllegalArgumentException} with no {@link FragmentBoundary} classification
 * -- indistinguishable, from a caller's perspective, from a real bug in this project's own code
 * rather than a configuration mismatch it should refuse cleanly and by name.
 */
public class UTypeBareMinMaxDomainTest {

  @Test
  public void aBareMinMaxURealDomainIsRefusedClosedThroughTheRealPipeline() throws Exception {
    MModel model = compile(resourcePath("URealRoundTrip.use"));
    ConfigurationVocabulary vocabulary = ConfigurationVocabulary.fromModel(model);
    AnalysisConfiguration config =
        readConfig(
            model,
            vocabulary,
            """
            Reading_min = 1
            Reading_max = 1
            Reading_measurement_min = 0.0
            Reading_measurement_max = 1.0
            """);

    SmtTranslationException thrown =
        assertThrows(SmtTranslationException.class, () -> SmtModelFinder.find(model, config));

    assertEquals(FragmentBoundary.ENCODING_SCOPE, thrown.boundary());
    assertTrue(
        "must name the offending attribute, got: " + thrown.getMessage(),
        thrown.getMessage().contains("Reading.measurement"));
    assertTrue(
        "must say what paired domains it actually needs, got: " + thrown.getMessage(),
        thrown.getMessage().contains("_value") && thrown.getMessage().contains("_uncertainty"));
  }

  private static AnalysisConfiguration readConfig(
      MModel model, ConfigurationVocabulary vocabulary, String body) throws Exception {
    Path file = Files.createTempFile("bareminmax", ".properties");
    file.toFile().deleteOnExit();
    Files.writeString(file, body);
    RawConfiguration raw = ConfigurationReader.read(file, null);
    // Deliberately NOT .requireSupported() here would still hide nothing extra -- a bare _min/_max
    // domain is not a ledger-rejected fragment gap, it normalizes cleanly and only fails later,
    // inside AttributeEncoder itself, which is exactly the site under test.
    return ConfigurationReader.normalize(raw, vocabulary).requireSupported();
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
        Objects.requireNonNull(UTypeBareMinMaxDomainTest.class.getResource("/" + name)).toURI());
  }
}
