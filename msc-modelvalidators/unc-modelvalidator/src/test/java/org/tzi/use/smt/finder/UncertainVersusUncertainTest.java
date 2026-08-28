package org.tzi.use.smt.finder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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
import org.tzi.use.smt.encode.SmtTranslationException;
import org.tzi.use.smt.verify.InvariantVerdict;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;

/**
 * End-to-end oracle tests for the one verified "uncertain versus uncertain" shape: two UReal
 * attributes on the same object, compared directly, with a PROVEN-equal configured uncertainty.
 * See the master plan's own research appendix for the derivation -- USE's live evaluator computes
 * P(A&lt;B) via a crossing-point method (P(A&lt;B)=0 whenever mean(A)&gt;mean(B), not a small tail
 * probability), not the naive independent-difference formula, and this shape's soundness rests on
 * both sides' uncertainty being a proven-equal, singleton configured constant, not a solver-free
 * variable that could legitimately differ.
 */
public class UncertainVersusUncertainTest {

  @Test
  public void equalUncertaintyAboveConfidenceReachesSatAndEvaluatorAgreesTrue() throws Exception {
    MModel model = compile(resourcePath("UncertainComparison.use"));

    ModelFinderResult result = SmtModelFinder.find(model, configuration(model, "above"));

    assertTrue(result.satisfiable());
    assertEquals(
        new InvariantVerdict("Sensor::fasterThanSelf", true), verdict(result, "Sensor::fasterThanSelf"));
  }

  @Test
  public void equalUncertaintyBelowConfidenceIsFalseToUseAndUnsatWhenEnforced() throws Exception {
    MModel model = compile(resourcePath("UncertainComparison.use"));

    ModelFinderResult unchecked = SmtModelFinder.find(model, configuration(model, "belowInactive"));
    assertTrue(unchecked.satisfiable());
    assertEquals(
        new InvariantVerdict("Sensor::fasterThanSelf", false),
        verdict(unchecked, "Sensor::fasterThanSelf"));

    ModelFinderResult enforced = SmtModelFinder.find(model, configuration(model, "belowActive"));
    assertFalse(
        "meanB-meanA=0.01 over sigma=0.05 gives P(A<B)~0.08, far under the 0.95 confidence"
            + " required, so enforcing the invariant must be UNSAT",
        enforced.satisfiable());
  }

  @Test
  public void unequalConfiguredUncertaintyIsRefusedNotSilentlyCompared() throws Exception {
    MModel model = compile(resourcePath("UncertainComparison.use"));
    AnalysisConfiguration config = configuration(model, "unequalUncertainty");

    SmtTranslationException thrown =
        assertThrows(SmtTranslationException.class, () -> SmtModelFinder.find(model, config));

    assertTrue(
        "must name the real reason, not a generic message: " + thrown.getMessage(),
        thrown.getMessage().contains("not a proven-equal")
            || thrown.getMessage().contains("uncertain-vs-uncertain"));
  }

  private static InvariantVerdict verdict(ModelFinderResult result, String invariantName) {
    return result.verdicts().stream()
        .filter(candidate -> candidate.invariantName().equals(invariantName))
        .findFirst()
        .orElseThrow(() -> new AssertionError("missing evaluator verdict for " + invariantName));
  }

  private static AnalysisConfiguration configuration(MModel model, String section)
      throws URISyntaxException {
    return ConfigurationReader.normalize(
            ConfigurationReader.read(resourcePath("UncertainComparison.properties"), section),
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
        Objects.requireNonNull(UncertainVersusUncertainTest.class.getResource("/" + name)).toURI());
  }
}
