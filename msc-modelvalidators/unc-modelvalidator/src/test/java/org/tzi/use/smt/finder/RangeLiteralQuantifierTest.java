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
 * End-to-end regression for Integer range literals ({@code Set{a..b}}) as quantifier ranges.
 * Each Integer in the closed interval binds the loop variable exactly as a singleton-content
 * candidate through the same per-element SMT let machinery as the set-literal quantifier
 * slice.
 *
 * <p>Pre-fix, range literals were refused outright. Post-fix, Set{2..4}->exists(k | k = x.i)
 * is SAT when i can be 2, 3, or 4 and UNSAT otherwise (with i domain {5,6}); forAll over
 * {2..4} requires every element to satisfy the body.
 */
public class RangeLiteralQuantifierTest {

  private static final String MODEL =
      """
      model RangeLitQuant
      class X
      attributes
        i : Integer
      end
      constraints
      context x : X inv RangeExistsMatches:
        Set{2..4}->exists(k | k = x.i)
      context x : X inv RangeForAllHolds:
        Set{2..4}->forAll(k | k <= x.i)
      """;

  /** exists over {2..4}: satisfiable exactly when i can be one of {2, 3, 4}. */
  @Test
  public void rangeLiteralExistsMatchesAnElementOfTheRange() throws Exception {
    ModelFinderResult match = find("RangeExistsMatches", List.of("3"));
    assertTrue("i can be 3, a member of {2..4}", match.satisfiable());
    assertTrue(verdictFor(match, "X::RangeExistsMatches").holds());

    ModelFinderResult miss = find("RangeExistsMatches", List.of("5"));
    assertFalse("i can only be 5, which is not in {2..4}", miss.satisfiable());
  }

  /** forAll over {2..4}: every element must satisfy the body. */
  @Test
  public void rangeLiteralForAllRequiresEveryElementToSatisfyTheBody() throws Exception {
    ModelFinderResult holds = find("RangeForAllHolds", List.of("9"));
    assertTrue("i = 9 satisfies k <= 9 for all k in {2..4}", holds.satisfiable());
    assertTrue(verdictFor(holds, "X::RangeForAllHolds").holds());

    ModelFinderResult fails = find("RangeForAllHolds", List.of("3"));
    assertFalse("i = 3 violates k = 4's conjunct (4 <= 3 is false)",
        fails.satisfiable());
  }

  private static ModelFinderResult find(String invariantName, List<String> iDomain)
      throws Exception {
    MModel model = compile();
    AnalysisConfiguration config =
        new AnalysisConfiguration(
            List.of(new ClassScope("X", 1, 1)),
            List.of(),
            List.of(new AttributeDomain("X", "i", null, iDomain, null, null)),
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
    MModel model = USECompiler.compileSpecification(MODEL, "RangeLitQuant", err, factory);
    err.flush();
    if (model == null) {
      throw new AssertionError("fixture model did not compile");
    }
    return model;
  }
}
