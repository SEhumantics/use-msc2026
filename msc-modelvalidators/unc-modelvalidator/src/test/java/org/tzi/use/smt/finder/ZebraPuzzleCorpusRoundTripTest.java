package org.tzi.use.smt.finder;

import static org.junit.Assert.assertTrue;

import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;
import org.tzi.use.parser.use.USECompiler;
import org.tzi.use.smt.config.AnalysisConfiguration;
import org.tzi.use.smt.config.ConfigurationReader;
import org.tzi.use.smt.config.ConfigurationVocabulary;
import org.tzi.use.smt.config.RawConfiguration;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;

/**
 * Reproduces the REAL, unmodified corpus scenario: {@code
 * benchmark/examples/ZebraPuzzle/ZebraPuzzle.use} loaded with the shared {@code
 * ZebraPuzzle.properties} default section (14 clues, 6 all-different constraints, position range).
 *
 * <p>ZebraPuzzle was ERROR-before-solving for unc-modelvalidator across two separate,
 * independently-diagnosed gaps closed in the same session that added this test: enum type support
 * (House.nationality/color/drink/smoke/pet, the same fix CivilStatus needed) and multi-variable
 * {@code forAll}/{@code exists} (the six {@code Distinct*} invariants are two-variable {@code
 * forAll}; four clues are single-variable {@code exists}, a shape that had NO support at all
 * before this session, over any range). This is the first test to run the real, unmodified corpus
 * files end to end and confirm the classic zebra-puzzle solution is found and independently
 * verified in full -- not just that translation no longer throws.
 */
public class ZebraPuzzleCorpusRoundTripTest {

  @Test
  public void theRealZebraPuzzleScenarioReachesSatWithEveryInvariantConfirmedByUseEvaluator()
      throws Exception {
    MModel model = compileZebraPuzzle();
    Path propertiesFile = examplePath("ZebraPuzzle/ZebraPuzzle.properties");
    RawConfiguration raw = ConfigurationReader.read(propertiesFile, null);
    AnalysisConfiguration config =
        ConfigurationReader.normalize(raw, ConfigurationVocabulary.fromModel(model))
            .requireSupported();

    ModelFinderResult result = SmtModelFinder.find(model, config);

    assertTrue("expected SAT (a solution to the classic zebra puzzle)", result.satisfiable());
    long failing = result.verdicts().stream().filter(v -> !v.holds()).count();
    assertTrue(
        "every invariant (all 22 clues/constraints declared on House) must hold, confirmed by"
            + " USE's own evaluator against the reconstructed system state; "
            + failing
            + " did not",
        failing == 0);
  }

  private static MModel compileZebraPuzzle() throws Exception {
    Path file = examplePath("ZebraPuzzle/ZebraPuzzle.use");
    String source = Files.readString(file);
    PrintWriter err = new PrintWriter(System.err);
    MModel model =
        USECompiler.compileSpecification(source, "ZebraPuzzle", err, new ModelFactory());
    err.flush();
    if (model == null) {
      throw new AssertionError("ZebraPuzzle.use did not compile");
    }
    return model;
  }

  private static Path examplePath(String relative) {
    Path fromModule = Path.of("../benchmark/examples").resolve(relative);
    if (Files.isRegularFile(fromModule)) {
      return fromModule;
    }
    return Path.of("msc-modelvalidators/benchmark/examples").resolve(relative);
  }
}
