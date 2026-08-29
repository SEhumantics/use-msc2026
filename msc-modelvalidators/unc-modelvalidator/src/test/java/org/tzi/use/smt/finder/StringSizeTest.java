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
 * End-to-end regression for the first {@code prim.string-operations} slice: {@code String.size()}
 * over a configured-candidate string. The configured spellings ARE the content space, so the
 * size is a candidate enumeration -- an ite chain over the attribute's configured candidates,
 * each branch the compile-time length of that spelling -- linear in the pinned QF_LIA logic, no
 * string theory. Composes with arithmetic and with String lets (whose candidate list the binding
 * already records).
 *
 * <p>Before this slice {@code size} over a String receiver refused with the collection-oriented
 * failure; the whole {@code prim.string-operations} row was {@code unsupported}.
 */
public class StringSizeTest {

  private static final String MODEL =
      """
      model StringSize
      class X
      attributes
        s : String
      end
      constraints
      context x : X inv SizeIsThree:
        x.s.size() = 3
      context x : X inv SizeIsTwo:
        x.s.size() = 2
      context x : X inv SizeGtTwo:
        x.s.size() > 2
      context x : X inv LetSizeIsThree:
        let s : String = x.s in s.size() = 3
      """;

  /** Both configured candidates are 3 long ('abc'): satisfiable, and the verdict holds. */
  @Test
  public void sizeReadsTheConfiguredSpellingsLength() throws Exception {
    ModelFinderResult result = find("SizeIsThree", List.of("abc"));

    assertTrue(result.satisfiable());
    assertTrue(verdictFor(result, "X::SizeIsThree").holds());
  }

  /**
   * The branch-selection discriminator: with candidates {'abc','de'}, demanding size 2 forces
   * 'de' and demanding size 3 forces 'abc' -- each demand pins WHICH spelling the solver chose.
   */
  @Test
  public void sizeSelectsTheBranchMatchingTheDemand() throws Exception {
    ModelFinderResult two = find("SizeIsTwo", List.of("abc", "de"));
    assertTrue("only 'de' has size 2", two.satisfiable());
    assertTrue(verdictFor(two, "X::SizeIsTwo").holds());

    ModelFinderResult three = find("SizeIsThree", List.of("abc", "de"));
    assertTrue("only 'abc' has size 3", three.satisfiable());
    assertTrue(verdictFor(three, "X::SizeIsThree").holds());
  }

  /** Ordered comparison composes: size > 2 selects 'abc' over 'de'. */
  @Test
  public void orderedComparisonComposesWithSize() throws Exception {
    ModelFinderResult result = find("SizeGtTwo", List.of("abc", "de"));

    assertTrue(result.satisfiable());
    assertTrue(verdictFor(result, "X::SizeGtTwo").holds());
  }

  /** No candidate has length 3 ('de' only): genuinely unsatisfiable. */
  @Test
  public void anUnreachableSizeIsGenuinelyUnsatisfiable() throws Exception {
    ModelFinderResult miss = find("SizeIsThree", List.of("de"));

    assertFalse("'de' has size 2, never 3", miss.satisfiable());
  }

  /** A String let binding already carries its candidate list: size composes through it. */
  @Test
  public void sizeComposesThroughAStringLetBinding() throws Exception {
    ModelFinderResult result = find("LetSizeIsThree", List.of("abc"));

    assertTrue(result.satisfiable());
    assertTrue(verdictFor(result, "X::LetSizeIsThree").holds());
  }

  private static ModelFinderResult find(String invariantName, List<String> domain)
      throws Exception {
    MModel model = compile();
    AnalysisConfiguration config =
        new AnalysisConfiguration(
            List.of(new ClassScope("X", 1, 1)),
            List.of(),
            List.of(new AttributeDomain("X", "s", null, domain, null, null)),
            Set.of("X::" + invariantName),
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

  private static MModel compile() {
    ModelFactory factory = new ModelFactory();
    PrintWriter err = new PrintWriter(System.err);
    MModel model = USECompiler.compileSpecification(MODEL, "StringSize", err, factory);
    err.flush();
    if (model == null) {
      throw new AssertionError("fixture model did not compile");
    }
    return model;
  }
}
