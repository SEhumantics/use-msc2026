package org.tzi.use.smt.encode;

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
import org.tzi.use.smt.finder.ModelFinderResult;
import org.tzi.use.smt.finder.SmtModelFinder;
import org.tzi.use.smt.verify.InvariantVerdict;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;

/**
 * {@code X->isEmpty()} / {@code X->notEmpty()} -- found chasing CompanyERSchema's own remaining
 * gaps: {@code Project::pname_primary_key} and {@code ProjectWork::fname_lname_pname_primary_key}
 * both end in exactly this shape, e.g. {@code Project.allInstances()->
 * select(p2|p2<>p1 and p2.pname=p1.pname)->isEmpty()} -- the standard OCL "no other instance
 * shares my key" primary-key idiom. Reduces to whichever population primitive already matches the
 * receiver ({@link ExpressionTranslator#populationOf} or {@link
 * ExpressionTranslator#selectedAllInstancesPopulation}) rather than a new one, matching {@code
 * size()}/{@code includesAll}'s own established reuse.
 *
 * <p>Every test goes through the FULL {@link SmtModelFinder#find} pipeline (encoding,
 * reconstruction, USE-evaluator re-check together), and the primary-key-idiom test specifically
 * mirrors the real corpus shape (self-exclusion via {@code <>}, an equality predicate) with a
 * genuine duplicate-vs-distinct discriminating pair, not just an isolated true/false check.
 */
public class EmptyTranslationTest {

  @Test
  public void navigationIsEmptyAndNotEmptyDiscriminateOnRealLinks() throws Exception {
    MModel model = compileFixture();
    AnalysisConfiguration linked =
        readConfig(
            model,
            """
            A_min = 1
            A_max = 1
            A_tag = Set{1}
            B_min = 1
            B_max = 1
            B_bval = Set{1}
            Link_min = 1
            Link_max = -1
            A_LinkIsEmpty = inactive
            A_LinkNotEmpty = active
            A_AllInstancesEmpty = inactive
            A_NoDuplicateTag = inactive
            """);
    ModelFinderResult linkedResult = SmtModelFinder.find(model, linked);
    assertTrue("expected SAT", linkedResult.satisfiable());
    assertTrue(
        "a real link exists, notEmpty must hold, confirmed by USE's own evaluator",
        verdictFor(linkedResult, "A::LinkNotEmpty").holds());

    AnalysisConfiguration unlinked =
        readConfig(
            model,
            """
            A_min = 1
            A_max = 1
            A_tag = Set{1}
            B_min = 0
            B_max = 0
            Link_min = 0
            Link_max = 0
            A_LinkIsEmpty = active
            A_LinkNotEmpty = inactive
            A_AllInstancesEmpty = inactive
            A_NoDuplicateTag = inactive
            """);
    ModelFinderResult unlinkedResult = SmtModelFinder.find(model, unlinked);
    assertTrue("expected SAT", unlinkedResult.satisfiable());
    assertTrue(
        "no B exists to link to, isEmpty must hold, confirmed by USE's own evaluator",
        verdictFor(unlinkedResult, "A::LinkIsEmpty").holds());
  }

  @Test
  public void selectedAllInstancesIsEmptyDiscriminatesOnWhetherAnyMemberMatches() throws Exception {
    MModel model = compileFixture();
    AnalysisConfiguration noMatch =
        readConfig(
            model,
            """
            A_min = 1
            A_max = 1
            A_tag = Set{1}
            B_min = 0
            B_max = 0
            Link_min = 0
            Link_max = 0
            A_LinkIsEmpty = inactive
            A_LinkNotEmpty = inactive
            A_AllInstancesEmpty = active
            A_NoDuplicateTag = inactive
            """);
    ModelFinderResult result = SmtModelFinder.find(model, noMatch);
    assertTrue("expected SAT", result.satisfiable());
    assertTrue(
        "no A instance has tag > 100, select(...)->isEmpty() must hold, confirmed by USE's own"
            + " evaluator",
        verdictFor(result, "A::AllInstancesEmpty").holds());
  }

  @Test
  public void primaryKeyIdiomIsGenuinelyFalseOnADuplicateTagNotJustVacuouslyTrue() throws Exception {
    // Mirrors the real CompanyERSchema shape exactly: self-exclusion (x <> a) plus an equality
    // predicate. Two A instances sharing a tag must make NoDuplicateTag genuinely FALSE for both
    // -- proving the translation actually finds the duplicate, not vacuously true regardless of
    // content.
    MModel model = compileFixture();
    AnalysisConfiguration duplicate =
        readConfig(
            model,
            """
            A_min = 2
            A_max = 2
            A_tag = Set{7}
            B_min = 0
            B_max = 0
            Link_min = 0
            Link_max = 0
            A_LinkIsEmpty = inactive
            A_LinkNotEmpty = inactive
            A_AllInstancesEmpty = inactive
            A_NoDuplicateTag = inactive
            """);
    ModelFinderResult duplicateResult = SmtModelFinder.find(model, duplicate);
    assertTrue("expected SAT", duplicateResult.satisfiable());
    for (InvariantVerdict v : duplicateResult.verdicts()) {
      if (v.invariantName().equals("A::NoDuplicateTag")) {
        assertTrue(
            "both A instances share tag=7, so select(x <> a and x.tag = a.tag)->isEmpty() must"
                + " genuinely be FALSE for each, confirmed by USE's own evaluator, not vacuously"
                + " true",
            !v.holds());
      }
    }

    AnalysisConfiguration distinct =
        readConfig(
            model,
            """
            A_min = 2
            A_max = 2
            A_tag = Set{7, 8}
            B_min = 0
            B_max = 0
            Link_min = 0
            Link_max = 0
            A_LinkIsEmpty = inactive
            A_LinkNotEmpty = inactive
            A_AllInstancesEmpty = inactive
            A_NoDuplicateTag = active
            """);
    ModelFinderResult distinctResult = SmtModelFinder.find(model, distinct);
    assertTrue("expected SAT with distinct tags", distinctResult.satisfiable());
    // verdictFor-by-name, not allActiveInvariantsHold(): B_min=B_max=0 here makes LinkNotEmpty
    // genuinely FALSE (correctly, no B exists) even though it's marked inactive -- the same
    // "checks every invariant regardless of active status" trap this session keeps re-learning.
    for (InvariantVerdict v : distinctResult.verdicts()) {
      if (v.invariantName().equals("A::NoDuplicateTag")) {
        assertTrue(
            "distinct tags: NoDuplicateTag must hold for each instance, confirmed by USE's own"
                + " evaluator",
            v.holds());
      }
    }
  }

  private static InvariantVerdict verdictFor(ModelFinderResult result, String invariantName) {
    return result.verdicts().stream()
        .filter(v -> v.invariantName().equals(invariantName))
        .findFirst()
        .orElseThrow(() -> new AssertionError("no verdict for " + invariantName));
  }

  private static AnalysisConfiguration readConfig(MModel model, String body) throws Exception {
    Path file = Files.createTempFile("empty", ".properties");
    file.toFile().deleteOnExit();
    Files.writeString(file, body);
    RawConfiguration raw = ConfigurationReader.read(file, null);
    return ConfigurationReader.normalize(raw, ConfigurationVocabulary.fromModel(model))
        .requireSupported();
  }

  private static MModel compileFixture() throws Exception {
    String source =
        """
        model EmptyScope
        class A
        attributes
          tag : Integer
        end
        class B
        attributes
          bval : Integer
        end
        association Link between
          A [1] role a
          B [*] role bs
        end
        constraints
        context a : A inv LinkIsEmpty:
          a.bs->isEmpty()
        context a : A inv LinkNotEmpty:
          a.bs->notEmpty()
        context a : A inv AllInstancesEmpty:
          A.allInstances()->select(x | x.tag > 100)->isEmpty()
        context a : A inv NoDuplicateTag:
          A.allInstances()->select(x | x <> a and x.tag = a.tag)->isEmpty()
        """;
    PrintWriter err = new PrintWriter(System.err);
    MModel model = USECompiler.compileSpecification(source, "EmptyScope", err, new ModelFactory());
    err.flush();
    if (model == null) {
      throw new AssertionError("EmptyScope fixture model did not compile");
    }
    return model;
  }
}
