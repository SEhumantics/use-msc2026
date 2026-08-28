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
 * benchmark/examples/Redefines/Redefines.use} -- {@code AB} between {@code A}/{@code B}, {@code
 * CD} between {@code C < A}/{@code D < B} with {@code c redefines a}, {@code d redefines b}.
 *
 * <p>{@code Redefines} was ERROR-before-solving: {@code requireIndependentlySearchableExtent}
 * refused ANY association declaring {@code redefines}, unconditionally. Closed this session by
 * removing that refusal (a redefining association's own link grid, e.g. {@code CD}, is a
 * perfectly ordinary, independently encodable extent on its own account) and adding {@code
 * ExpressionTranslator#resolveRedefinedDestination}: navigation through a redefined role name
 * ({@code c.b}, {@code C::RedefinedRoleTagCheck}'s own shape) is redirected to the REDEFINING
 * association for a source whose declared class matches, rather than reading the redefined
 * association's own (structurally disconnected, for a {@code C}-typed source) grid -- exactly the
 * false-vacuous-truth defect {@code Redefines.use}'s own header comment documents against Kodkod's
 * translator.
 */
public class RedefinesCorpusRoundTripTest {

  @Test
  public void theRealDefaultSectionReachesSatWithEveryInvariantConfirmedByTheUseEvaluator()
      throws Exception {
    MModel model = compileRedefines();
    AnalysisConfiguration config = readSection("default");

    ModelFinderResult result = SmtModelFinder.find(model, config);

    assertTrue("expected SAT", result.satisfiable());
    assertTrue(
        "expected every real invariant -- including RedefinedRoleTagCheck, which needs the new"
            + " redirect to correctly read CD's own grid for a C-typed source rather than AB's own"
            + " (structurally disconnected) one -- to hold, confirmed by USE's own evaluator",
        result.allActiveInvariantsHold());
  }

  /**
   * The paired UNSAT scenario ({@code Redefines-UNSAT} in the manifest, section {@code
   * tagBoundGap}): deliberately does NOT touch {@code c.b}/{@code RedefinedRoleTagCheck} navigation
   * at all (per the properties file's own header comment, specifically to avoid conflating this
   * check with the translation gap {@link #theTranslationGapSectionIsCorrectlyUnsatisfiableWhereKodkodFalselyReportsSat}
   * below exercises) -- the contradiction is a plain, navigation-free {@code TagBNotEmpty}
   * (`b.tagB <> ''`) with {@code B_tagB} narrowed to the empty string, unenforceable for both the
   * direct {@code B} instance and its {@code D} (via generalization) instance.
   */
  @Test
  public void theRealTagBoundGapSectionIsGenuinelyUnsatisfiable() throws Exception {
    MModel model = compileRedefines();
    AnalysisConfiguration config = readSection("tagBoundGap");

    assertFalse(
        "no assignment lets either the direct B instance or its D instance satisfy TagBNotEmpty"
            + " when B_tagB's entire candidate domain is the empty string",
        SmtModelFinder.find(model, config).satisfiable());
  }

  /**
   * NOT wired into the manifest as its own scenario, but independently valuable: {@code
   * [translationGap]} is the real corpus's own documented KODKOD SOUNDNESS GAP demonstration --
   * {@code D_tagD} is pinned to {@code 'NOT-child-d'}, so {@code RedefinedRoleTagCheck} (`c.b->
   * forAll(x | x.tagD = 'child-d')`) must genuinely FAIL for the one forced {@code CD} link
   * (`c1`-`d1`). Kodkod's own translator reads {@code c.b} through {@code AB}'s own (empty, for a
   * CD-only-linked C object) relation, making the {@code forAll} VACUOUSLY true regardless of
   * {@code tagD} -- a confirmed false SATISFIABLE (see the properties file's own header comment).
   * unc-modelvalidator's new redirect reads {@code CD}'s own grid instead, correctly finding the
   * one forced link and the mismatched {@code tagD}, so the whole configuration (which offers no
   * alternative D object or tagD value to route around it) is genuinely UNSATISFIABLE -- a real
   * supersession case, not a bug: this is one of the two directions this session's SoICT-plan
   * "RQ2 -- Supersession" study is about.
   */
  @Test
  public void theTranslationGapSectionIsCorrectlyUnsatisfiableWhereKodkodFalselyReportsSat()
      throws Exception {
    MModel model = compileRedefines();
    AnalysisConfiguration config = readSection("translationGap");

    assertFalse(
        "the one forced CD link's tagD ('NOT-child-d') must genuinely falsify"
            + " RedefinedRoleTagCheck, with no alternative D object or tagD value configured to"
            + " route around it -- Kodkod's own translator misses this (see Redefines.use's own"
            + " header comment: c.b read through AB's own always-empty-for-C relation, vacuously"
            + " true)",
        SmtModelFinder.find(model, config).satisfiable());
  }

  private static AnalysisConfiguration readSection(String section) throws Exception {
    Path propertiesFile = examplePath("Redefines/Redefines.properties");
    RawConfiguration raw = ConfigurationReader.read(propertiesFile, section);
    return ConfigurationReader.normalize(raw, ConfigurationVocabulary.fromModel(compileRedefines()))
        .requireSupported();
  }

  private static MModel compileRedefines() throws Exception {
    Path file = examplePath("Redefines/Redefines.use");
    String source = Files.readString(file);
    PrintWriter err = new PrintWriter(System.err);
    MModel model = USECompiler.compileSpecification(source, "RedefinesWorld", err, new ModelFactory());
    err.flush();
    if (model == null) {
      throw new AssertionError("Redefines.use did not compile");
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
