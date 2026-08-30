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
 * End-to-end regression for {@code prim.integer-arithmetic}'s Real-operand slice: {@code +}/
 * {@code -}/{@code *} over crisp Real-typed operands (and mixed Integer/Real). USE's own
 * {@code ArithOperation.matches} widens {@code (Integer|Real) op (Integer|Real)} to {@code Real}
 * and the evaluators compute via {@code evalRealResult}, so the encoding lifts the Int-sorted
 * side with {@code to_real} (the {@code realDivision} dividend precedent) and emits a
 * Real-sorted term that composes with the existing mixed-sort guards in
 * {@code comparison}/{@code orderedComparison}.
 *
 * <p>Before this slice a Real operand reached {@code arithmetic()} and was refused by the crisp
 * Integer guard ("only plain crisp Integer arithmetic is supported"). The linearity restriction
 * on {@code *} carries over unchanged: a product of two non-constant operands -- Integer or Real
 * -- is still refused at translation time. Test values are exactly representable in both the
 * solver's exact rationals and USE's double arithmetic (halves, quarters), so the two agree
 * without any rounding-enclosure machinery.
 */
public class RealArithmeticTest {

  private static final String MODEL =
      """
      model RealArithmetic
      class X
      attributes
        i : Integer
        r : Real
        r2 : Real
      end
      constraints
      context x : X inv MixedSumMatches:
        x.i + x.r = x.r2
      context x : X inv RealSumMatches:
        x.r + x.r2 = 4
      context x : X inv RealDifferenceSplits:
        x.r - x.r2 = 1
      context x : X inv RealTimesTwo:
        x.r * 2 = x.r2
      context x : X inv UnaryMinusOnReal:
        -x.r = x.r2
      context x : X inv MixedOrderedComparison:
        x.i + x.r > 3
      context x : X inv NonlinearRealProduct:
        x.r * x.r2 = 4
      """;

  /** 2 + 1.5 = 3.5: the mixed Integer/Real sum with a USE-confirmed witness. */
  @Test
  public void aMixedIntegerRealSumCarriesTheWidenedValue() throws Exception {
    ModelFinderResult match = find("MixedSumMatches", List.of("2"), List.of("1.5"), List.of("3.5"));

    assertTrue("2 + 1.5 = 3.5", match.satisfiable());
    assertTrue(verdictFor(match, "X::MixedSumMatches").holds());
  }

  /** 2 + 1.5 = 3.5, never 3.6: a wrong demanded sum is genuinely unsatisfiable. */
  @Test
  public void aWrongMixedSumIsGenuinelyUnsatisfiable() throws Exception {
    ModelFinderResult miss = find("MixedSumMatches", List.of("2"), List.of("1.5"), List.of("3.6"));

    assertFalse("2 + 1.5 = 3.5, never 3.6", miss.satisfiable());
  }

  /** 1.5 + 2.5 = 4.0 over two Real attributes, against the integer literal 4. */
  @Test
  public void aRealRealSumComposesWithAnIntegerLiteralComparison() throws Exception {
    ModelFinderResult match = find("RealSumMatches", List.of("0"), List.of("1.5"), List.of("2.5"));

    assertTrue("1.5 + 2.5 = 4.0", match.satisfiable());
    assertTrue(verdictFor(match, "X::RealSumMatches").holds());

    ModelFinderResult miss = find("RealSumMatches", List.of("0"), List.of("1.5"), List.of("2.4"));
    assertFalse("1.5 + 2.4 = 3.9, never 4", miss.satisfiable());
  }

  /** 2.5 - 1.5 = 1.0: the Real-sorted subtraction, split on the demand. */
  @Test
  public void aRealDifferenceSplitsOnTheDemand() throws Exception {
    ModelFinderResult match = find("RealDifferenceSplits", List.of("0"), List.of("2.5"), List.of("1.5"));

    assertTrue("2.5 - 1.5 = 1.0", match.satisfiable());
    assertTrue(verdictFor(match, "X::RealDifferenceSplits").holds());

    ModelFinderResult miss = find("RealDifferenceSplits", List.of("0"), List.of("2.5"), List.of("1.0"));
    assertFalse("2.5 - 1.0 = 1.5, never 1", miss.satisfiable());
  }

  /** 1.25 * 2 = 2.5: the literal-coefficient product over a Real attribute. */
  @Test
  public void aLiteralCoefficientProductCarriesTheExactValue() throws Exception {
    ModelFinderResult match = find("RealTimesTwo", List.of("0"), List.of("1.25"), List.of("2.5"));

    assertTrue("1.25 * 2 = 2.5", match.satisfiable());
    assertTrue(verdictFor(match, "X::RealTimesTwo").holds());

    ModelFinderResult miss = find("RealTimesTwo", List.of("0"), List.of("1.3"), List.of("2.5"));
    assertFalse("1.3 * 2 = 2.6, never 2.5", miss.satisfiable());
  }

  /** -1.5 = -1.5: SMT-LIB's negation form is sort-generic over Reals. */
  @Test
  public void unaryMinusNegatesARealOperand() throws Exception {
    ModelFinderResult match = find("UnaryMinusOnReal", List.of("0"), List.of("1.5"), List.of("-1.5"));

    assertTrue("-1.5 = -1.5", match.satisfiable());
    assertTrue(verdictFor(match, "X::UnaryMinusOnReal").holds());
  }

  /** The widened sum composes with an ordered comparison: 2 + 1.5 > 3 holds, 1 + 1.5 does not. */
  @Test
  public void theWidenedSumComposesWithOrderedComparison() throws Exception {
    ModelFinderResult above = find("MixedOrderedComparison", List.of("2"), List.of("1.5"), List.of("0"));
    assertTrue("2 + 1.5 = 3.5 > 3", above.satisfiable());

    ModelFinderResult below = find("MixedOrderedComparison", List.of("1"), List.of("1.5"), List.of("0"));
    assertFalse("1 + 1.5 = 2.5, not > 3", below.satisfiable());
  }

  /** Real * Real over two non-constant operands is still refused, at translation time. */
  @Test
  public void aProductOfTwoNonConstantRealsStillFailsClosed() throws Exception {
    org.tzi.use.smt.encode.SmtTranslationException thrown =
        org.junit.Assert.assertThrows(
            org.tzi.use.smt.encode.SmtTranslationException.class,
            () -> find("NonlinearRealProduct", List.of("0"), List.of("2.0"), List.of("2.0")));
    assertTrue(
        thrown.getMessage(), thrown.getMessage().contains("non-constant numeric operands"));
  }

  private static ModelFinderResult find(
      String invariantName, List<String> iDomain, List<String> rDomain, List<String> r2Domain)
      throws Exception {
    MModel model = compile();
    AnalysisConfiguration config =
        new AnalysisConfiguration(
            List.of(new ClassScope("X", 1, 1)),
            List.of(),
            List.of(
                new AttributeDomain("X", "i", null, iDomain, null, null),
                new AttributeDomain("X", "r", null, rDomain, null, null),
                new AttributeDomain("X", "r2", null, r2Domain, null, null)),
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
    MModel model = USECompiler.compileSpecification(MODEL, "RealArithmetic", err, factory);
    err.flush();
    if (model == null) {
      throw new AssertionError("fixture model did not compile");
    }
    return model;
  }
}
