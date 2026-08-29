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
 * End-to-end regression for {@code prim.string-operations}' split slice: {@code
 * x.s.split(',')->size()} and {@code x.s.split(',')->includes('b')} over configured-candidate
 * strings. USE's split is java {@code String.split(sep)} -- a regex split into a Sequence of
 * strings, trailing empties removed -- and this encoding expands it per configured candidate at
 * translation time by calling the SAME java method on the SAME spelling, so agreement is exact
 * by construction (regex-special separators cancel). The consumers enumerate the per-candidate
 * part lists: size over each list's length, membership over each list's contains, each guarded
 * by the candidate's configured index.
 *
 * <p>Before this slice split refused via the visitStdOp default.
 */
public class StringSplitTest {

  private static final String MODEL =
      """
      model StringSplit
      class X
      attributes
        s : String
      end
      constraints
      context x : X inv SplitSizeIsThree:
        x.s.split(',')->size() = 3
      context x : X inv SplitSizeIsOne:
        x.s.split(',')->size() = 1
      context x : X inv SplitIncludesB:
        x.s.split(',')->includes('b')
      context x : X inv SplitExcludesB:
        x.s.split(',')->excludes('b')
      context x : X inv SplitSizeIsFive:
        x.s.split(',')->size() = 5
      context x : X inv SplitTrailingEmptyRemoved:
        x.s.split(',')->size() = 2
      """;

  /** Only 'a,b,c' splits into three parts. */
  @Test
  public void splitSizeSelectsTheCandidateWithTheMatchingPartCount() throws Exception {
    ModelFinderResult result = find("SplitSizeIsThree", List.of("a,b,c", "ab"));

    assertTrue(result.satisfiable());
    assertTrue(verdictFor(result, "X::SplitSizeIsThree").holds());
  }

  /** The single-part candidate satisfies size 1 -- the other branch of the enumeration. */
  @Test
  public void splitSizeSelectsTheSinglePartCandidate() throws Exception {
    ModelFinderResult result = find("SplitSizeIsOne", List.of("a,b,c", "ab"));

    assertTrue(result.satisfiable());
    assertTrue(verdictFor(result, "X::SplitSizeIsOne").holds());
  }

  /** Membership: 'a,b,c' splits into parts containing 'b'. */
  @Test
  public void splitIncludesReadsThePartsOfTheSelectedCandidate() throws Exception {
    ModelFinderResult result = find("SplitIncludesB", List.of("a,b,c", "ab"));

    assertTrue(result.satisfiable());
    assertTrue(verdictFor(result, "X::SplitIncludesB").holds());
  }

  /** excludes forces the candidate whose parts lack 'b': 'ab' splits to ['ab']. */
  @Test
  public void splitExcludesSelectsTheCandidateWithoutTheElement() throws Exception {
    ModelFinderResult result = find("SplitExcludesB", List.of("a,b,c", "ab"));

    assertTrue("only 'ab' (parts ['ab']) excludes 'b'", result.satisfiable());
    assertTrue(verdictFor(result, "X::SplitExcludesB").holds());
  }

  /** No candidate splits to five parts: genuinely unsatisfiable. */
  @Test
  public void anUnreachablePartCountIsUnsatisfiable() throws Exception {
    MModel model = compile();
    AnalysisConfiguration config =
        new AnalysisConfiguration(
            List.of(new ClassScope("X", 1, 1)),
            List.of(),
            List.of(new AttributeDomain("X", "s", null, List.of("a,b,c", "ab"), null, null)),
            Set.of("X::SplitSizeIsFive"),
            QueryParser.parse("satisfy", ConfigurationVocabulary.fromModel(model)),
            Duration.ofSeconds(30),
            1);
    ModelFinderResult result = SmtModelFinder.find(model, config);

    assertFalse(
        "neither candidate splits into five parts", result.satisfiable());
  }

  /**
   * java split(sep) -- which USE calls -- REMOVES trailing empty strings: 'a,b,' splits into
   * [a, b], size 2, not 3. Pinned because a split(sep, -1) implementation would answer 3.
   */
  @Test
  public void trailingEmptyPartsAreRemoved() throws Exception {
    ModelFinderResult result = find("SplitTrailingEmptyRemoved", List.of("a,b,"));

    assertTrue("'a,b,' splits to [a, b] -- the trailing empty is removed", result.satisfiable());
    assertTrue(verdictFor(result, "X::SplitTrailingEmptyRemoved").holds());
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
    MModel model = USECompiler.compileSpecification(MODEL, "StringSplit", err, factory);
    err.flush();
    if (model == null) {
      throw new AssertionError("fixture model did not compile");
    }
    return model;
  }
}
