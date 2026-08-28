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
 * Reproduces the REAL, unmodified corpus scenario: {@code
 * benchmark/examples/Genealogy/Genealogy.use} loaded with the shared {@code corleone.properties}
 * default section. Genealogy was ERROR-before-solving for unc-modelvalidator on {@code
 * acyclicParenthood} ({@code p.parent->closure(parent)->excludes(p)}) until the
 * closure()/excludes() acyclicity idiom landed -- this is the first test to run the real,
 * unmodified corpus files end to end and confirm it.
 */
public class GenealogyCorpusRoundTripTest {

  @Test
  public void theRealGenealogyScenarioReachesSatWithEveryActiveInvariantConfirmedByUseEvaluator()
      throws Exception {
    MModel model = compileGenealogy();
    Path propertiesFile = examplePath("Genealogy/corleone.properties");
    RawConfiguration raw = ConfigurationReader.read(propertiesFile, null);
    AnalysisConfiguration config =
        ConfigurationReader.normalize(raw, ConfigurationVocabulary.fromModel(model))
            .requireSupported();

    ModelFinderResult result = SmtModelFinder.find(model, config);

    assertTrue("expected SAT", result.satisfiable());
    for (String invariantName :
        List.of("Person::nameUnique", "Person::acyclicParenthood", "Person::parentOlderChild")) {
      assertTrue(
          invariantName + " is genuinely active in corleone.properties's default section and"
              + " must hold, confirmed by USE's own evaluator",
          verdictFor(result, invariantName).holds());
    }
  }

  private static InvariantVerdict verdictFor(ModelFinderResult result, String invariantName) {
    return result.verdicts().stream()
        .filter(v -> v.invariantName().equals(invariantName))
        .findFirst()
        .orElseThrow(() -> new AssertionError("no verdict for " + invariantName));
  }

  private static MModel compileGenealogy() throws Exception {
    Path file = examplePath("Genealogy/Genealogy.use");
    String source = Files.readString(file);
    PrintWriter err = new PrintWriter(System.err);
    MModel model =
        USECompiler.compileSpecification(source, "Genealogy", err, new ModelFactory());
    err.flush();
    if (model == null) {
      throw new AssertionError("Genealogy.use did not compile");
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
