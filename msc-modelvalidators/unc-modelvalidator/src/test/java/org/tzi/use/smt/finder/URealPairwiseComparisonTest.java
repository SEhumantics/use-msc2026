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
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;

/**
 * End-to-end regression for the GENERAL uncertain-vs-uncertain ordered comparison
 * ({@code (s.speedA < s.speedB).toBooleanC(theta)}) over MULTI-CANDIDATE value and uncertainty
 * domains -- the shape the proven-equal-singleton slice deliberately refused.
 *
 * <p>The encoding enumerates the cross product of the two sides' (value, uncertainty) candidate
 * pairs, evaluates each pair with USE'S OWN evaluator classes ({@code URealValue.lt/gt/le/ge} --
 * bit-exact by construction, crossing-point model and all), and admits exactly the pairs whose
 * probability clears theta. A satisfying pair's guard pins all four selections, so a witness
 * must carry the candidate combination the probability actually holds for -- verified here down
 * to the reconstructed uncertainty value.
 */
public class URealPairwiseComparisonTest {

  @Test
  public void multiCandidateValuesCompareBelowWithUseConfirming() throws Exception {
    ModelFinderResult result = find("multiValue", "Sensor::aBelowB");
    assertTrue(result.satisfiable());
    assertEquals(
        "Sensor::aBelowB must hold on the reconstructed witness",
        true,
        verdict(result, "Sensor::aBelowB").holds());
  }

  /** The crossing-point model gives P(smaller-mean A > B) = 0 under equal sigmas: UNSAT. */
  @Test
  public void multiCandidateValuesRefuteTheImpossibleDirection() throws Exception {
    ModelFinderResult result = find("aboveActive", "Sensor::aAboveB");
    assertFalse("P(A > B) is 0 for every candidate pair; nothing may satisfy",
        result.satisfiable());
  }

  /** The non-strict comparator (le = lt + eq under the crossing model) holds a fortiori. */
  @Test
  public void nonStrictComparatorHolds() throws Exception {
    ModelFinderResult result = find("lePath", "Sensor::aAtMostB");
    assertTrue(result.satisfiable());
    assertTrue(verdict(result, "Sensor::aAtMostB").holds());
  }

  /**
   * Exactly one (value, uncertainty) pair clears theta = 0.999 -- the u_A = 0.0 degenerate
   * branch -- so the SAT witness MUST carry that uncertainty selection, and USE's own
   * re-evaluation rejects any other.
   */
  @Test
  public void witnessMustCarryTheSatisfyingUncertaintyChoice() throws Exception {
    ModelFinderResult result = find("uncertaintyChoice", "Sensor::tightSigma");
    assertTrue(result.satisfiable());
    assertTrue(verdict(result, "Sensor::tightSigma").holds());

    MModel model = compile(resourcePath("URealPairwise.use"));
    var state = result.system().state();
    var sensor = state.objectsOfClass(model.getClass("Sensor")).iterator().next();
    Object speedA = sensor.state(state).attributeValue("speedA");
    var uReal = (org.tzi.use.uml.ocl.value.URealValue) speedA;
    assertEquals("the witness must have selected the degenerate (u = 0) uncertainty candidate",
        0.0, uReal.uncertainty(), 1.0e-12);
  }

  /** The same tight threshold without the degenerate candidate cannot be satisfied. */
  @Test
  public void tightThresholdWithoutADegenerateCandidateIsUnsat() throws Exception {
    ModelFinderResult result = find("tightBelow", "Sensor::tightSigma");
    assertFalse("P ~ 0.9973 < 0.999 for the only pair", result.satisfiable());
  }

  /** A range-bound (non-enumerated) uncertainty domain refuses with a located message. */
  @Test
  public void rangeBoundUncertaintyRefuses() throws Exception {
    // Refused at CONFIGURATION-READ time, one gate earlier than the translation: the reader
    // requires paired ENUMERATED component domains, so a range-bound uncertainty never reaches
    // the pairwise enumeration (whose own non-enumerable refusal remains for shapes that reach
    // it by other routes).
    assertThrows(org.tzi.use.smt.config.ConfigurationReadException.class,
        () -> find("rangeUncertainty", "Sensor::aBelowB"));
  }

  private static org.tzi.use.smt.verify.InvariantVerdict verdict(
      ModelFinderResult result, String invariantName) {
    return result.verdicts().stream()
        .filter(candidate -> candidate.invariantName().equals(invariantName))
        .findFirst()
        .orElseThrow(() -> new AssertionError("missing evaluator verdict for " + invariantName));
  }

  private static ModelFinderResult find(String section, String invariantName) throws Exception {
    MModel model = compile(resourcePath("URealPairwise.use"));
    AnalysisConfiguration config =
        ConfigurationReader.normalize(
            ConfigurationReader.read(resourcePath("URealPairwise.properties"), section),
            ConfigurationVocabulary.fromModel(model))
        .requireSupported();
    return SmtModelFinder.find(model, config);
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
        Objects.requireNonNull(URealPairwiseComparisonTest.class.getResource("/" + name)).toURI());
  }
}
