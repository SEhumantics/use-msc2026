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
 * End-to-end regression for {@code Real.round()} -- the {@code prim.integer-round-toString}
 * row's Real half. USE's semantics are Java's {@code Math.round(double)}: floor(r + 0.5),
 * confirmed against Op_real_round in use-core's StandardOperationsNumber. That is LINEAR in
 * the pinned QF_LIRA logic once SMT-LIB's own floor-valued {@code to_int} is available: the
 * encoding is {@code (to_int (+ r 0.5))}, which matches Java exactly -- including the
 * negative half case, where Math.round(-2.5) = -2 (toward positive infinity), NOT -3.
 *
 * <p>UReal.round() keeps its refusal: URealValue.round() rounds the composed (mu, sigma)
 * value and its uncertainty semantics are their own problem.
 */
public class RealRoundTest {

  private static final String MODEL =
      """
      model RealRound
      class X
      attributes
        r : Real
      end
      constraints
      context x : X inv rounds:
        x.r.round() = 7
      context x : X inv roundsNegativeHalf:
        x.r.round() = -2
      context x : X inv roundsPlus:
        x.r.round() = 8
      """;

  /** 7.3 rounds down to 7. */
  @Test
  public void roundsSevenPointThreeDown() throws Exception {
    ModelFinderResult match = find(Set.of("X::rounds"), List.of("7.3"));
    assertTrue(match.satisfiable());
    assertTrue(verdictFor(match, "X::rounds").holds());

    ModelFinderResult wrong = find(Set.of("X::rounds"), List.of("7.6"));
    assertFalse("7.6 rounds to 8, not 7", wrong.satisfiable());
  }

  /** 7.6 rounds up to 8: the value genuinely flows through the rounding. */
  @Test
  public void roundsSevenPointSixUp() throws Exception {
    ModelFinderResult match = find(Set.of("X::roundsPlus"), List.of("7.6"));
    assertTrue(match.satisfiable());
    assertTrue(verdictFor(match, "X::roundsPlus").holds());
  }

  /**
   * THE JAVA HALF-UP DISCRIMINATOR: Math.round(-2.5) = -2 (toward positive infinity), not -3.
   * A floor-without-shift or a round-half-away-from-zero encoding both fail this case.
   */
  @Test
  public void negativeHalfRoundsTowardPositiveInfinity() throws Exception {
    ModelFinderResult match = find(Set.of("X::roundsNegativeHalf"), List.of("-2.5"));
    assertTrue("Math.round(-2.5) = -2", match.satisfiable());
    assertTrue(verdictFor(match, "X::roundsNegativeHalf").holds());

    ModelFinderResult lower = find(Set.of("X::roundsNegativeHalf"), List.of("-2.7"));
    assertFalse("-2.7 rounds to -3, not -2", lower.satisfiable());
  }

  private static ModelFinderResult find(Set<String> invariants, List<String> rDomain)
      throws Exception {
    MModel model = compile();
    AnalysisConfiguration config =
        new AnalysisConfiguration(
            List.of(new ClassScope("X", 1, 1)),
            List.of(),
            List.of(new AttributeDomain("X", "r", null, rDomain, null, null)),
            invariants,
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
    java.io.StringWriter buffer = new java.io.StringWriter();
    PrintWriter err = new PrintWriter(buffer, true);
    MModel model = USECompiler.compileSpecification(MODEL, "RealRound", err, factory);
    err.flush();
    if (model == null) {
      throw new AssertionError("fixture model did not compile:\n" + buffer);
    }
    return model;
  }
}
