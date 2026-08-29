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
 * End-to-end regression for {@code prim.string-operations}' concat/substring slice: equality-
 * shaped comparisons over a VIRTUAL string -- {@code x.s.concat('b') = 'ab'},
 * {@code x.s.substring(0, 1) = 'a'} -- where the source string's configured candidates are the
 * content space. Per candidate the operation's result is computed at compile time (Java concat /
 * substring), the comparison against the literal is a compile-time equality, and the surviving
 * candidates become the guarded disjunction over the source symbol's configured indices.
 *
 * <p>Semantics notes pinned here: substring indices out of a candidate's range make that
 * candidate's case undefined (excluded, never matched); a no-candidate match makes the whole
 * comparison false; {@code <>} is the negation. Before this slice concat/substring refused via
 * the visitStdOp default (the row's remaining refusals after String.size()).
 */
public class StringConcatSubstringTest {

  private static final String MODEL =
      """
      model StringOps
      class X
      attributes
        s : String
      end
      constraints
      context x : X inv ConcatIsAb:
        x.s.concat('b') = 'ab'
      context x : X inv ConcatIsAbb:
        x.s.concat('b') = 'abb'
      context x : X inv ConcatIsZzz:
        x.s.concat('b') = 'zzz'
      context x : X inv ConcatNotAbb:
        x.s.concat('b') <> 'abb'
      context x : X inv SubstringSecondChar:
        x.s.substring(2, 2) = 'b'
      context x : X inv SubstringOutOfRange:
        x.s.substring(2, 2) = 'a'
      """;

  /** s = 'a' gives 'a' + 'b' = 'ab'. */
  @Test
  public void concatSelectsTheCandidateWhoseResultMatches() throws Exception {
    ModelFinderResult result = find("ConcatIsAb", List.of("a", "ab"));

    assertTrue(result.satisfiable());
    assertTrue(verdictFor(result, "X::ConcatIsAb").holds());
  }

  /** s = 'ab' gives 'abb' -- the other candidate satisfies the other demand. */
  @Test
  public void concatSelectsTheOtherCandidateForTheOtherDemand() throws Exception {
    ModelFinderResult result = find("ConcatIsAbb", List.of("a", "ab"));

    assertTrue(result.satisfiable());
    assertTrue(verdictFor(result, "X::ConcatIsAbb").holds());
  }

  /** No candidate's result is 'zzz': genuinely unsatisfiable. */
  @Test
  public void anUnreachableConcatResultIsUnsatisfiable() throws Exception {
    ModelFinderResult miss = find("ConcatIsZzz", List.of("a", "ab"));

    assertFalse("'a'+'b' = 'ab' and 'ab'+'b' = 'abb', never 'zzz'", miss.satisfiable());
  }

  /** The negation reads the same enumeration: s = 'a' makes concat <> 'abb' true. */
  @Test
  public void concatNegationSelectsTheNonMatchingCandidate() throws Exception {
    ModelFinderResult result = find("ConcatNotAbb", List.of("a", "ab"));

    assertTrue(result.satisfiable());
    assertTrue(verdictFor(result, "X::ConcatNotAbb").holds());
  }

  /**
   * USE's substring is 1-based INCLUSIVE (bytecode: java substring(start-1, end)), so
   * substring(2,2) is 'b' only for the 'ab' candidate -- 'a' has no second character and
   * (per the same bytecode's exception handler) yields the EMPTY STRING.
   */
  @Test
  public void substringSelectsTheCandidateWhoseResultMatches() throws Exception {
    ModelFinderResult result = find("SubstringSecondChar", List.of("a", "ab"));

    assertTrue(result.satisfiable());
    assertTrue(verdictFor(result, "X::SubstringSecondChar").holds());
  }

  /**
   * Out-of-range candidates yield the EMPTY STRING (the bytecode's exception handler), never
   * the demanded value: 'a'.substring(2,2) = "" and 'ab'.substring(2,2) = 'b', so 'a' matches
   * for neither candidate.
   */
  @Test
  public void anOutOfRangeSubstringCandidateYieldsTheEmptyString() throws Exception {
    ModelFinderResult onlyA = find("SubstringOutOfRange", List.of("a"));
    assertFalse("'a'.substring(2,2) is out of range -> empty string, never 'a'", onlyA.satisfiable());

    ModelFinderResult withB = find("SubstringOutOfRange", List.of("a", "ab"));
    assertFalse(
        "neither candidate yields 'a': 'a' yields empty, 'ab' yields 'b'",
        withB.satisfiable());
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
    MModel model = USECompiler.compileSpecification(MODEL, "StringOps", err, factory);
    err.flush();
    if (model == null) {
      throw new AssertionError("fixture model did not compile");
    }
    return model;
  }
}
