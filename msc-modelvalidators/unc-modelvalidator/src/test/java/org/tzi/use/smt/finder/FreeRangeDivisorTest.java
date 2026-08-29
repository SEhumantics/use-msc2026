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
 * End-to-end regression for {@code prim.integer-arithmetic}'s free-range divisor slice: {@code
 * mod}/{@code div} by a divisor with NO finite configured candidate proof -- an arithmetic
 * expression ({@code x.n.mod(x.a + x.b)}) or an attribute left on the type-wide fallback range.
 *
 * <p>The encoding discovers that the long-documented "symbolic divisor is nonlinear" premise was
 * too coarse for the pinned binary: Z3 5.1.0 accepts {@code (mod a d)}/{@code (div a d)} with a
 * variable divisor under QF_LIRA (Euclidean semantics -- the remainder is always non-negative
 * and takes the DIVISOR's sign, verified per sign case against the real binary). The Java pair
 * (truncation toward zero, dividend-signed remainder) is recovered linearly: the quotient gains
 * a {+1,-1,0} correction when the signs of the operands differ and the Euclidean remainder is
 * nonzero, and the remainder is {@code mod_e - |d|} under exactly that condition.
 *
 * <p>Zero divisors keep the established semantics: undefined (excluded from the comparison's
 * definedness), never rescued by a fallback value.
 */
public class FreeRangeDivisorTest {

  private static final String MODEL =
      """
      model FreeRangeDivisor
      class X
      attributes
        n : Integer
        a : Integer
        b : Integer
        d : Integer
      end
      constraints
      context x : X inv ModArithmeticDivisor:
        x.n.mod(x.a + x.b) = 1
      context x : X inv ModNegativeDivisorIsMinusOne:
        x.n.mod(x.a + x.b) = -1
      context x : X inv ModEuclideanLeak:
        x.n.mod(x.a + x.b) = 1
      context x : X inv DivArithmeticDivisor:
        x.n div (x.a + x.b) = 2
      context x : X inv ModFreeRangeDivisor:
        x.n.mod(x.d) = 1
      context x : X inv ModNegativeDivisorCorrection:
        x.n.mod(x.a + x.b) = -1
      """;

  /** 7 mod 2 = 1 with the divisor an arithmetic expression. */
  @Test
  public void anArithmeticDivisorComputesTheTruncatedMod() throws Exception {
    ModelFinderResult result =
        find("ModArithmeticDivisor", List.of("7"), List.of("1"), List.of("1"));

    assertTrue("7 mod (1+1) = 1", result.satisfiable());
    assertTrue(verdictFor(result, "X::ModArithmeticDivisor").holds());
  }

  /**
   * THE sign-correction discriminator: with divisor 2, Java gives (-7) mod 2 = -1 while the
   * Euclidean remainder is 1. The demand for -1 must be satisfiable -- an encoding that leaks
   * the Euclidean (non-negative) remainder would wrongly report UNSAT here.
   */
  @Test
  public void theNegativeDividendKeepsItsSignUnderTheMod() throws Exception {
    ModelFinderResult result =
        find("ModNegativeDivisorIsMinusOne", List.of("-7"), List.of("1"), List.of("1"));

    assertTrue("(-7) mod 2 = -1 in Java/USE (truncation toward zero)", result.satisfiable());
    assertTrue(verdictFor(result, "X::ModNegativeDivisorIsMinusOne").holds());
  }

  /** And the Euclidean leak polarity: with divisor 2, (-7) mod 2 = 1 is genuinely unsatisfiable. */
  @Test
  public void theEuclideanValueIsNotTheAnswer() throws Exception {
    ModelFinderResult miss = find("ModEuclideanLeak", List.of("-7"), List.of("1"), List.of("1"));

    assertFalse("Java's (-7) mod 2 is -1; the Euclidean 1 must not leak", miss.satisfiable());
  }

  /** 7 div 3 = 2 with the divisor an arithmetic expression, and its UNSAT polarity. */
  @Test
  public void divByAnArithmeticDivisorComputesTheTruncatedQuotient() throws Exception {
    ModelFinderResult match = find("DivArithmeticDivisor", List.of("7"), List.of("2"), List.of("1"));
    assertTrue("7 div (2+1) = 2", match.satisfiable());
    assertTrue(verdictFor(match, "X::DivArithmeticDivisor").holds());

    ModelFinderResult miss =
        find("DivArithmeticDivisor", List.of("7"), List.of("2"), List.of("2"));
    assertFalse("7 div (2+2) = 1, never 2", miss.satisfiable());
  }

  /**
   * The free-range discriminator: the divisor attribute has NO configured domain (the type-wide
   * fallback range), so no finite candidate set exists -- the direct Euclidean encoding answers
   * where the case-split machinery could not.
   */
  @Test
  public void aFreeRangeDivisorIsHandledDirectly() throws Exception {
    MModel model = compile();
    AnalysisConfiguration config =
        new AnalysisConfiguration(
            List.of(new ClassScope("X", 1, 1)),
            List.of(),
            List.of(
                new AttributeDomain("X", "n", null, List.of("7"), null, null),
                new AttributeDomain(
                    "X",
                    "d",
                    null,
                    List.of(),
                    java.math.BigDecimal.valueOf(-100),
                    java.math.BigDecimal.valueOf(100))),
            Set.of("X::ModFreeRangeDivisor"),
            QueryParser.parse("satisfy", ConfigurationVocabulary.fromModel(model)),
            Duration.ofSeconds(30),
            1);
    ModelFinderResult result = SmtModelFinder.find(model, config);

    assertTrue(
        "a free-range divisor needs no candidate enumeration: the solver picks d with 7 mod d = 1",
        result.satisfiable());
    assertTrue(verdictFor(result, "X::ModFreeRangeDivisor").holds());
  }

  /**
   * The |d| in the remainder correction must be the DIVISOR's absolute value: with a negative
   * divisor (-2) and a negative dividend (-7), the Euclidean remainder is 1 and the Java
   * remainder is 1 - 2 = -1. An encoding that subtracts d itself (-2) instead of |d| would
   * answer +1 -- wrongly SATISFIABLE for the demanded -1 only if the demanded value differs...
   * here the demand is -1, which the corrected encoding satisfies and the d-subtracting
   * mutation cannot (it answers +1, failing the equality).
   */
  @Test
  public void theRemainderCorrectionUsesTheDivisorsAbsoluteValue() throws Exception {
    ModelFinderResult result =
        find("ModNegativeDivisorCorrection", List.of("-7"), List.of("-3"), List.of("1"));

    assertTrue("(-7) mod (-2) = -1 (truncation; |d| = 2)", result.satisfiable());
    assertTrue(verdictFor(result, "X::ModNegativeDivisorCorrection").holds());
  }

  private static ModelFinderResult find(
      String invariantName, List<String> nDomain, List<String> aDomain, List<String> bDomain)
      throws Exception {
    MModel model = compile();
    AnalysisConfiguration config =
        new AnalysisConfiguration(
            List.of(new ClassScope("X", 1, 1)),
            List.of(),
            List.of(
                new AttributeDomain("X", "n", null, nDomain, null, null),
                new AttributeDomain("X", "a", null, aDomain, null, null),
                new AttributeDomain("X", "b", null, bDomain, null, null)),
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
    MModel model = USECompiler.compileSpecification(MODEL, "FreeRangeDivisor", err, factory);
    err.flush();
    if (model == null) {
      throw new AssertionError("fixture model did not compile");
    }
    return model;
  }
}
