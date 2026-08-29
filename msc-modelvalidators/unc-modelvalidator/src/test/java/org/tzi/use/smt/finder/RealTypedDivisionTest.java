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
 * End-to-end regression for {@code prim.integer-arithmetic}'s Real-typed `/`: a REAL dividend
 * (`x.ratio / 2.0`), a Real constant divisor over an Integer dividend ({@code x.n / 4.0}), and
 * a variable Real divisor with a finite configured domain ({@code x.ratio / x.w}) -- the latter
 * enumerating the divisor's configured candidates exactly like the integer mod/div slices,
 * each branch a division by a nonzero numeral (linear in QF_LIRA).
 *
 * <p>Before this slice `/` required both operands crisp Integer; Real-typed operands refused.
 * Test values are exactly representable in both the solver's rationals and USE's doubles.
 */
public class RealTypedDivisionTest {

  private static final String MODEL =
      """
      model RealTypedDivision
      class R
      attributes
        ratio : Real
        w : Real
        n : Integer
      end
      constraints
      context x : R inv RealDivByConstant:
        x.ratio / 2.0 = 1.5
      context x : R inv RealDivConstantWrong:
        x.ratio / 2.0 = 1.4
      context x : R inv IntegerDivByRealConstant:
        x.n / 4.0 = 1.75
      context x : R inv RealDivByRealAttribute:
        x.ratio / x.w = 2.0
      context x : R inv ForcedZeroRealDivisor:
        x.w = 0.0 and x.ratio / x.w = 2.0
      """;

  /** 3.0 / 2.0 = 1.5: the basic Real dividend, Real constant divisor. */
  @Test
  public void aRealDividendDividedByARealConstant() throws Exception {
    ModelFinderResult result = find("RealDivByConstant", List.of("3.0"), List.of("2.0"));

    assertTrue(result.satisfiable());
    assertTrue(verdictFor(result, "R::RealDivByConstant").holds());
  }

  /** 1.5 never equals 1.4: the polarity pin. */
  @Test
  public void aWrongQuotientIsGenuinelyUnsatisfiable() throws Exception {
    ModelFinderResult miss = find("RealDivConstantWrong", List.of("3.0"), List.of("2.0"));

    assertFalse("3.0 / 2.0 = 1.5, never 1.4", miss.satisfiable());
  }

  /** An Integer dividend widens through to_real: 7 / 4.0 = 1.75. */
  @Test
  public void anIntegerDividendLiftsToReal() throws Exception {
    ModelFinderResult result = find("IntegerDivByRealConstant", List.of("7"), List.of("2.0"));

    assertTrue(result.satisfiable());
    assertTrue(verdictFor(result, "R::IntegerDivByRealConstant").holds());
  }

  /** The variable Real divisor with a finite configured domain: branch per candidate. */
  @Test
  public void aRealAttributeDivisorEnumeratesItsConfiguredCandidates() throws Exception {
    ModelFinderResult match = find("RealDivByRealAttribute", List.of("4.0"), List.of("2.0"));
    assertTrue("4.0 / 2.0 = 2.0", match.satisfiable());
    assertTrue(verdictFor(match, "R::RealDivByRealAttribute").holds());

    ModelFinderResult miss = find("RealDivByRealAttribute", List.of("4.0"), List.of("3.0"));
    assertFalse("4.0 / 3.0 is 1.33..., never 2.0", miss.satisfiable());
  }

  /**
   * The zero divisor is undefined, never a fallback value: with w forced to 0.0 the division
   * is undefined and the equality cannot hold -- even though the domain also offers 3.0 whose
   * quotient would equal 2.0. An encoding whose chain falls through to the nonzero branch's
   * result would wrongly report SAT.
   */
  @Test
  public void aForcedZeroRealDivisorIsNotRescuedByASiblingBranch() throws Exception {
    ModelFinderResult result = find("ForcedZeroRealDivisor", List.of("6.0"), List.of("0.0", "3.0"));

    assertFalse(
        "w forced to 0.0 makes the division undefined; the 3.0 branch's quotient (2.0) must"
            + " not leak",
        result.satisfiable());
  }

  private static ModelFinderResult find(
      String invariantName, List<String> ratioDomain, List<String> wDomain) throws Exception {
    MModel model = compile();
    AnalysisConfiguration config =
        new AnalysisConfiguration(
            List.of(new ClassScope("R", 1, 1)),
            List.of(),
            List.of(
                new AttributeDomain("R", "ratio", null, ratioDomain, null, null),
                new AttributeDomain("R", "w", null, wDomain, null, null),
                new AttributeDomain("R", "n", null, List.of("7"), null, null)),
            Set.of("R::" + invariantName),
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
    MModel model = USECompiler.compileSpecification(MODEL, "RealTypedDivision", err, factory);
    err.flush();
    if (model == null) {
      throw new AssertionError("fixture model did not compile");
    }
    return model;
  }
}
