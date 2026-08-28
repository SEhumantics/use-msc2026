package org.tzi.use.smt.finder;

import static org.junit.Assert.assertFalse;
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
 * Reproduces the REAL corpus scenario, unmodified: {@code
 * benchmark/examples/AggregationComposition/FileSystem.use}'s {@code [cycle]} section -- {@code
 * PrimaryContains}/{@code AltContains}, two independent self-referential {@code composition}
 * associations on {@code Folder}, forced into a 2-hop cycle that only closes ACROSS both
 * associations together (neither one alone contains a self-loop).
 *
 * <p>{@code AggregationComposition} was ERROR-before-solving for unc-modelvalidator across two
 * separate gaps closed in the same session that added this test: {@code aggregationcyclefreeness}
 * had no encoding at all (the config key was unconditionally refused), and {@code
 * Folder::notOwnPrimaryParent} ({@code f.primaryParent <> f}) needed a translation shape --
 * single-valued navigation compared against a bare object variable -- nothing had exercised
 * before ({@link org.tzi.use.smt.encode.ExpressionTranslator#navigationEqualsVariable}).
 *
 * <p>Both the manifest's own wired section ({@code FileSystem_default.properties[cycle]}, {@code
 * aggregationcyclefreeness = on}, expected UNSATISFIABLE) and its natural SAT counterpart ({@code
 * FileSystem_off.properties[cycle]}, same forced cycle, toggle {@code off}) are exercised here --
 * both files already ship in the real corpus, and testing them together is the same "the toggle
 * genuinely gates the behavior, not just always-on or always-off" soundness discipline this
 * session's other closures (e.g. CivilStatus-UNSAT's paired gender invariants) already established,
 * confirmed live against the real Z3 binary and USE's own evaluator, not merely asserted.
 */
public class AggregationCompositionCorpusRoundTripTest {

  @Test
  public void theRealCycleSectionWithFreedomOnIsGenuinelyUnsatisfiable() throws Exception {
    MModel model = compileFileSystem();
    AnalysisConfiguration config = readSection("FileSystem_default.properties", "cycle");

    assertTrue("expected aggregationcyclefreeness to be read as on", config.requireAggregationCycleFreedom());
    assertFalse(
        "the forced 2-hop cross-association cycle must be genuinely unreachable when"
            + " aggregationcyclefreeness = on, not an artifact of a mistranslated union skip or"
            + " comparison shape",
        SmtModelFinder.find(model, config).satisfiable());
  }

  /**
   * The same forced cycle, same model, toggle OFF: proves {@code aggregationcyclefreeness}
   * genuinely GATES the constraint rather than the encoder rejecting this shape unconditionally
   * for some other reason. Also independently confirms {@code notOwnPrimaryParent}'s new
   * translation shape holds correctly against USE's own evaluator on a real, non-trivial witness
   * (the forced cycle survives reconstruction, matching the properties file's own documented
   * "USE core's own checkStructure() graph walk" warning).
   */
  @Test
  public void theSameForcedCycleWithFreedomOffReachesSatWithEveryInvariantConfirmedByUseEvaluator()
      throws Exception {
    MModel model = compileFileSystem();
    AnalysisConfiguration config = readSection("FileSystem_off.properties", "cycle");

    assertFalse("expected aggregationcyclefreeness to be read as off", config.requireAggregationCycleFreedom());
    ModelFinderResult result = SmtModelFinder.find(model, config);

    assertTrue("expected SAT", result.satisfiable());
    assertTrue(
        "expected every real invariant (including the new navigationEqualsVariable shape,"
            + " notOwnPrimaryParent) to hold, confirmed by USE's own evaluator",
        result.allActiveInvariantsHold());
  }

  private static AnalysisConfiguration readSection(String propertiesFileName, String section)
      throws Exception {
    Path propertiesFile = examplePath("AggregationComposition/" + propertiesFileName);
    RawConfiguration raw = ConfigurationReader.read(propertiesFile, section);
    return ConfigurationReader.normalize(
            raw, ConfigurationVocabulary.fromModel(compileFileSystem()))
        .requireSupported();
  }

  private static MModel compileFileSystem() throws Exception {
    Path file = examplePath("AggregationComposition/FileSystem.use");
    String source = Files.readString(file);
    PrintWriter err = new PrintWriter(System.err);
    MModel model =
        USECompiler.compileSpecification(source, "FileSystemWorld", err, new ModelFactory());
    err.flush();
    if (model == null) {
      throw new AssertionError("FileSystem.use did not compile");
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
