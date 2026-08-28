package org.tzi.use.smt.finder;

import static org.junit.Assert.assertTrue;

import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.Test;
import org.tzi.use.parser.use.USECompiler;
import org.tzi.use.smt.config.AnalysisConfiguration;
import org.tzi.use.smt.config.ConfigurationReader;
import org.tzi.use.smt.config.ConfigurationVocabulary;
import org.tzi.use.smt.config.RawConfiguration;
import org.tzi.use.smt.verify.InvariantVerdict;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;

/**
 * Reproduces the REAL corpus scenario, unmodified: {@code
 * benchmark/examples/CivilStatus/CivilStatus.use} loaded with the shared {@code
 * CivilStatus.properties} default section, exactly what the benchmark harness runs.
 *
 * <p>CivilStatus was ERROR-before-solving for unc-modelvalidator across three separate,
 * independently-diagnosed gaps, closed one at a time in the same session that added this test:
 * enum type support (Person.civstat/gender), isDefined/isUndefined over a single-valued
 * association navigation ({@code wife.isUndefined}/{@code husband.isUndefined}), and reflexive
 * association translation/reconstruction ({@code Marriage}: {@code Person [0..1] role wife --
 * Person [0..1] role husband}, both ends the same class). This is the first test to run the real,
 * unmodified corpus files end to end and confirm all four.
 */
public class CivilStatusCorpusRoundTripTest {

  @Test
  public void theRealCivilStatusScenarioReachesSatWithEveryActiveInvariantConfirmedByUseEvaluator()
      throws Exception {
    MModel model = compileCivilStatus();
    Path propertiesFile = examplePath("CivilStatus/CivilStatus.properties");
    RawConfiguration raw = ConfigurationReader.read(propertiesFile, null);
    AnalysisConfiguration config =
        ConfigurationReader.normalize(raw, ConfigurationVocabulary.fromModel(model))
            .requireSupported();

    ModelFinderResult result = SmtModelFinder.find(model, config);

    assertTrue("expected SAT", result.satisfiable());
    for (String invariantName :
        List.of(
            "Person::attributesDefined",
            "Person::nameIsUnique",
            "Person::femaleHasNoWife",
            "Person::maleHasNoHusband")) {
      assertTrue(
          invariantName + " is genuinely active in CivilStatus.properties and must hold,"
              + " confirmed by USE's own evaluator against the reconstructed system state",
          verdictFor(result, invariantName).holds());
    }
  }

  private static InvariantVerdict verdictFor(ModelFinderResult result, String invariantName) {
    return result.verdicts().stream()
        .filter(v -> v.invariantName().equals(invariantName))
        .findFirst()
        .orElseThrow(() -> new AssertionError("no verdict for " + invariantName));
  }

  private static MModel compileCivilStatus() throws Exception {
    Path file = examplePath("CivilStatus/CivilStatus.use");
    String source = Files.readString(file);
    PrintWriter err = new PrintWriter(System.err);
    MModel model =
        USECompiler.compileSpecification(source, "CivilStatusWorld", err, new ModelFactory());
    err.flush();
    if (model == null) {
      throw new AssertionError("CivilStatus.use did not compile");
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
