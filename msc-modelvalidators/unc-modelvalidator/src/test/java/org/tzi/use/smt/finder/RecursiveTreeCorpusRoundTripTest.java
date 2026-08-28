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
import org.tzi.use.smt.verify.InvariantVerdict;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;

/**
 * Reproduces the REAL, unmodified corpus scenario: {@code
 * benchmark/examples/RecursiveTree/Tree.use} loaded with the shared {@code Tree.properties}
 * default section. RecursiveTree was ERROR-before-solving for unc-modelvalidator on {@code
 * AcyclicParentshipClosure} ({@code self.child->closure(child)->excludes(self)}, RecursiveTree's
 * own comment already anticipating it: "computed via closure() (supported -- see Genealogy's
 * acyclicParenthood)") until the closure()/excludes() acyclicity idiom landed.
 */
public class RecursiveTreeCorpusRoundTripTest {

  @Test
  public void theRealRecursiveTreeScenarioReachesSatWithTheClosureInvariantConfirmedByUseEvaluator()
      throws Exception {
    MModel model = compileTree();
    Path propertiesFile = examplePath("RecursiveTree/Tree.properties");
    RawConfiguration raw = ConfigurationReader.read(propertiesFile, null);
    AnalysisConfiguration config =
        ConfigurationReader.normalize(raw, ConfigurationVocabulary.fromModel(model))
            .requireSupported();

    ModelFinderResult result = SmtModelFinder.find(model, config);

    assertTrue("expected SAT", result.satisfiable());
    assertTrue(
        "AcyclicParentshipClosure is genuinely active in Tree.properties's default section and"
            + " must hold, confirmed by USE's own evaluator",
        verdictFor(result, "TreeNode::AcyclicParentshipClosure").holds());
  }

  private static InvariantVerdict verdictFor(ModelFinderResult result, String invariantName) {
    return result.verdicts().stream()
        .filter(v -> v.invariantName().equals(invariantName))
        .findFirst()
        .orElseThrow(() -> new AssertionError("no verdict for " + invariantName));
  }

  private static MModel compileTree() throws Exception {
    Path file = examplePath("RecursiveTree/Tree.use");
    String source = Files.readString(file);
    PrintWriter err = new PrintWriter(System.err);
    MModel model = USECompiler.compileSpecification(source, "Tree", err, new ModelFactory());
    err.flush();
    if (model == null) {
      throw new AssertionError("Tree.use did not compile");
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
