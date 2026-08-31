package org.tzi.use.smt.finder;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.PrintWriter;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.junit.Test;
import org.tzi.use.parser.use.USECompiler;
import org.tzi.use.smt.config.AnalysisConfiguration;
import org.tzi.use.smt.config.AttributeDomain;
import org.tzi.use.smt.config.ClassScope;
import org.tzi.use.smt.config.ConfigurationVocabulary;
import org.tzi.use.smt.config.QueryParser;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;

/**
 * End-to-end regression for COLLECTION-VALUED OPERATION RESULTS -- the first slice of the
 * Lists.use arc: an operation whose result type is a collection and whose body is a collection
 * literal over compile-time constants has a constant content, and every constant-content
 * consumer (size, membership, emptiness, quantifiers) serves it through the shared extractor.
 * Symbolic-element bodies keep their fail-closed refusal (pinned by
 * {@link #symbolicElementBodyStillRefutes}).
 */
public class CollResultTest {

  private static final String MODEL =
      """
      model CollResult
      class X
      attributes
        base : Integer
      operations
        picks() : Set(Integer) = Set{3, 7}
      end
      constraints
      context x : X inv sizeOfResult:
        x.picks()->size() = 2
      context x : X inv sizeWrong:
        x.picks()->size() = 3
      context x : X inv includesSeven:
        x.picks()->includes(7)
      context x : X inv excludesFive:
        x.picks()->excludes(5)
      context x : X inv includesFive:
        x.picks()->includes(5)
      context x : X inv resultEmpty:
        x.picks()->isEmpty()
      context x : X inv allMembersPositive:
        x.picks()->forAll(v | v > 0)
      """;

  /** The distinct count of the literal body is the size (USE-confirmed). */
  @Test
  public void sizeOfTheResultIsTheDistinctElementCount() throws Exception {
    ModelFinderResult match = find("X::sizeOfResult");
    assertTrue(match.satisfiable());
    assertTrue(verdictFor(match, "X::sizeOfResult").holds());

    ModelFinderResult wrong = find("X::sizeWrong");
    assertFalse("the body holds exactly 2 distinct elements", wrong.satisfiable());
  }

  /** Membership reads the constant content in both polarities. */
  @Test
  public void membershipReadsTheConstantContent() throws Exception {
    ModelFinderResult in = find("X::includesSeven");
    assertTrue(in.satisfiable());
    assertTrue(verdictFor(in, "X::includesSeven").holds());

    ModelFinderResult out = find("X::excludesFive");
    assertTrue(out.satisfiable());
    assertTrue(verdictFor(out, "X::excludesFive").holds());

    ModelFinderResult miss = find("X::includesFive");
    assertFalse("5 is not in the result", miss.satisfiable());
  }

  /** Emptiness over a non-empty constant body refutes. */
  @Test
  public void emptinessOverANonEmptyBodyRefutes() throws Exception {
    ModelFinderResult miss = find("X::resultEmpty");
    assertFalse("the body has two elements: isEmpty is violated", miss.satisfiable());
  }

  /** Quantifiers range over the constant content. */
  @Test
  public void forAllRangesOverTheConstantContent() throws Exception {
    ModelFinderResult match = find("X::allMembersPositive");
    assertTrue(match.satisfiable());
    assertTrue(verdictFor(match, "X::allMembersPositive").holds());
  }

  /**
   * The fail-closed boundary this slice keeps: a body with a SYMBOLIC element (an attribute
   * read) is not constant content and still refuses with the located message.
   */
  @Test
  public void symbolicElementBodyStillRefutes() throws Exception {
    String symbolic =
        MODEL.replace("picks() : Set(Integer) = Set{3, 7}", "picks() : Set(Integer) = Set{base, 7}");
    MModel model = compile(symbolic);
    AnalysisConfiguration config =
        new AnalysisConfiguration(
            List.of(new ClassScope("X", 1, 1, List.of("x1"))),
            List.of(),
            List.of(new AttributeDomain("X", "base", null, List.of("3"), null, null)),
            Set.of("X::sizeOfResult"),
            QueryParser.parse("satisfy", ConfigurationVocabulary.fromModel(model)),
            Duration.ofSeconds(30),
            1);
    org.tzi.use.smt.encode.SmtTranslationException thrown =
        org.junit.Assert.assertThrows(
            org.tzi.use.smt.encode.SmtTranslationException.class,
            () -> SmtModelFinder.find(model, config));
    assertTrue(
        "the refusal must name the unsupported size() shape, got: " + thrown.getMessage(),
        thrown.getMessage().contains("size() over anything other than"));
  }

  private static ModelFinderResult find(String invariant) throws Exception {
    MModel model = compile(MODEL);
    AnalysisConfiguration config =
        new AnalysisConfiguration(
            List.of(new ClassScope("X", 1, 1, List.of("x1"))),
            List.of(),
            List.of(new AttributeDomain("X", "base", null, List.of("3"), null, null)),
            Set.of(invariant),
            QueryParser.parse("satisfy", ConfigurationVocabulary.fromModel(model)),
            Duration.ofSeconds(30),
            1);
    return SmtModelFinder.find(model, config);
  }

  private static org.tzi.use.smt.verify.InvariantVerdict verdictFor(
      ModelFinderResult result, String invariantName) {
    return result.verdicts().stream()
        .filter(v -> v.invariantName().equals(invariantName))
        .findFirst()
        .orElseThrow(() -> new AssertionError("no verdict for " + invariantName));
  }

  private static MModel compile(String spec) {
    ModelFactory factory = new ModelFactory();
    java.io.StringWriter buffer = new java.io.StringWriter();
    PrintWriter err = new PrintWriter(buffer, true);
    MModel model = USECompiler.compileSpecification(spec, "CollResult", err, factory);
    err.flush();
    if (model == null) {
      throw new AssertionError("fixture model did not compile:\n" + buffer);
    }
    return model;
  }
}
