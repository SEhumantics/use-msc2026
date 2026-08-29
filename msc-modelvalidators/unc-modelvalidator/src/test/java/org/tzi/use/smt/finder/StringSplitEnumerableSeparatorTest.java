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
 * End-to-end regression for {@code prim.string-operations}' enumerable-separator split: {@code
 * x.s.split(x.sep)} where BOTH the source and the separator are configured-candidate strings.
 * Per candidate PAIR the part list is computed at translation time with the same java
 * {@code String.split} USE calls, and the consumers (--&gt;size(), --&gt;includes/excludes)
 * enumerate the per-pair part lists guarded by BOTH sources' configured-index guards.
 *
 * <p>Before this slice split required a literal separator. The pair discriminator: with source
 * 'a,b,c' and separators {'.', ','}, demanding size 3 forces the comma separator while the dot
 * leaves the string unsplit (size 1).
 */
public class StringSplitEnumerableSeparatorTest {

  private static final String MODEL =
      """
      model StringSplitSep
      class X
      attributes
        s : String
        sep : String
      end
      constraints
      context x : X inv SplitSizeIsThree:
        x.s.split(x.sep)->size() = 3
      context x : X inv SplitSizeIsOne:
        x.s.split(x.sep)->size() = 1
      context x : X inv SplitIncludesB:
        x.s.split(x.sep)->includes('b')
      context x : X inv SplitExcludesB:
        x.s.split(x.sep)->excludes('b')
      """;

  /** The comma separator splits 'a,b,c' into three parts. */
  @Test
  public void anEnumerableSeparatorSplitsTheSource() throws Exception {
    ModelFinderResult result = find("SplitSizeIsThree", List.of(",", ";"));

    assertTrue(result.satisfiable());
    assertTrue(verdictFor(result, "X::SplitSizeIsThree").holds());
  }

  /** The dot separator leaves the string unsplit (size 1) -- the other pair's answer. */
  @Test
  public void theOtherSeparatorSelectsTheOtherAnswer() throws Exception {
    ModelFinderResult result = find("SplitSizeIsOne", List.of(",", ";"));

    assertTrue("'.' does not split 'a,b,c' -- one part", result.satisfiable());
    assertTrue(verdictFor(result, "X::SplitSizeIsOne").holds());
  }

  /** Membership reads the selected pair's parts: 'b' is a part only under the comma split. */
  @Test
  public void membershipReadsTheSelectedPairsParts() throws Exception {
    ModelFinderResult includes = find("SplitIncludesB", List.of(",", ";"));

    assertTrue("'a,b,c'.split(',') contains 'b'", includes.satisfiable());
    assertTrue(verdictFor(includes, "X::SplitIncludesB").holds());

    ModelFinderResult excludes = find("SplitExcludesB", List.of(","));

    assertFalse(
        "the '.' split's single part IS 'b'-containing ('a,b,c'), so excludes('b') fails",
        excludes.satisfiable());
  }

  /**
   * The pair discriminator: with separators {'.', ','} configured, the size demand 3 can only
   * be met by the comma pair, and the size demand 1 only by the dot pair.
   */
  @Test
  public void theSeparatorChoiceIsForcedByTheSizeDemand() throws Exception {
    ModelFinderResult comma = find("SplitSizeIsThree", List.of(";", ","));

    assertTrue("'a,b;c'.split(',') stays one part... the comma demand needs the ',' separator",
        comma.satisfiable());
    assertTrue(verdictFor(comma, "X::SplitSizeIsThree").holds());

    ModelFinderResult dot = find("SplitSizeIsOne", List.of(";", ","));

    assertTrue("the ';' split's single part satisfies size 1", dot.satisfiable());
    assertTrue(verdictFor(dot, "X::SplitSizeIsOne").holds());
  }

  private static ModelFinderResult find(String invariantName, List<String> sepDomain)
      throws Exception {
    MModel model = compile();
    AnalysisConfiguration config =
        new AnalysisConfiguration(
            List.of(new ClassScope("X", 1, 1)),
            List.of(),
            List.of(
                new AttributeDomain("X", "s", null, List.of("a,b,c"), null, null),
                new AttributeDomain("X", "sep", null, sepDomain, null, null)),
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
    MModel model = USECompiler.compileSpecification(MODEL, "StringSplitSep", err, factory);
    err.flush();
    if (model == null) {
      throw new AssertionError("fixture model did not compile");
    }
    return model;
  }
}
