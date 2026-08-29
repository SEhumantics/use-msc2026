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
 * End-to-end regression for {@code prim.string-operations}' at/indexOf slice, over
 * configured-candidate strings. Semantics confirmed against the use-core BYTECODE:
 * {@code at(i)} is UNDEFINED when i &lt; 1 or i &gt; length (excluded from the comparison, never
 * matched), else the 1-based character; {@code indexOf(sub)} is the 1-based position
 * (java indexOf + 1), which is 0 when absent or when the receiver is empty, and 1 when the
 * needle is the empty string. Both enumerate the source's configured candidates exactly like
 * the size/concat/substring slices.
 */
public class StringAtIndexOfTest {

  private static final String MODEL =
      """
      model StringAtIndexOf
      class X
      attributes
        s : String
      end
      constraints
      context x : X inv FirstCharIsA:
        x.s.at(1) = 'a'
      context x : X inv ThirdCharIsC:
        x.s.at(3) = 'c'
      context x : X inv FourthCharIsX:
        x.s.at(4) = 'x'
      context x : X inv IndexOfBIsTwo:
        x.s.indexOf('b') = 2
      context x : X inv IndexOfZIsZero:
        x.s.indexOf('z') = 0
      context x : X inv IndexOfEmptyIsOne:
        x.s.indexOf('') = 1
      """;

  /** at(1) is 'a' only for 'abc' ('de' starts with 'd'). */
  @Test
  public void atSelectsTheCandidateWhoseCharacterMatches() throws Exception {
    ModelFinderResult result = find("FirstCharIsA", List.of("abc", "de"));

    assertTrue(result.satisfiable());
    assertTrue(verdictFor(result, "X::FirstCharIsA").holds());
  }

  /** at(3) is 'c' only for 'abc'; 'de' has no third character (undefined, excluded). */
  @Test
  public void anOutOfRangeAtIsExcludedNotMatched() throws Exception {
    ModelFinderResult match = find("ThirdCharIsC", List.of("abc", "de"));
    assertTrue(match.satisfiable());
    assertTrue(verdictFor(match, "X::ThirdCharIsC").holds());

    ModelFinderResult miss = find("ThirdCharIsC", List.of("de"));
    assertFalse("'de'.at(3) is undefined and can never equal 'c'", miss.satisfiable());
  }

  /** at beyond every candidate's length: undefined everywhere, genuinely unsatisfiable. */
  @Test
  public void anAtBeyondAllCandidatesIsUnsatisfiable() throws Exception {
    ModelFinderResult result = find("FourthCharIsX", List.of("abc", "de"));

    assertFalse("no candidate is 4 long", result.satisfiable());
  }

  /** indexOf('b') = 2 is the 1-based position in 'abc' ('de' gives 0). */
  @Test
  public void indexOfEnumeratesTheConfiguredCandidates() throws Exception {
    ModelFinderResult match = find("IndexOfBIsTwo", List.of("abc", "de"));

    assertTrue(match.satisfiable());
    assertTrue(verdictFor(match, "X::IndexOfBIsTwo").holds());
  }

  /**
   * The bytecode's quirk pinned: indexOf is 0 when absent, and 0 too when the receiver is empty
   * -- with the domain {'abc','de'} neither is empty, so indexOf('z') = 0 for both.
   */
  @Test
  public void anAbsentNeedleYieldsZero() throws Exception {
    ModelFinderResult result = find("IndexOfZIsZero", List.of("abc", "de"));

    assertTrue(result.satisfiable());
    assertTrue(verdictFor(result, "X::IndexOfZIsZero").holds());
  }

  /** The other bytecode quirk: an empty needle over a non-empty receiver yields 1. */
  @Test
  public void anEmptyNeedleYieldsOne() throws Exception {
    ModelFinderResult result = find("IndexOfEmptyIsOne", List.of("abc", "de"));

    assertTrue(result.satisfiable());
    assertTrue(verdictFor(result, "X::IndexOfEmptyIsOne").holds());
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
    MModel model = USECompiler.compileSpecification(MODEL, "StringAtIndexOf", err, factory);
    err.flush();
    if (model == null) {
      throw new AssertionError("fixture model did not compile");
    }
    return model;
  }
}
