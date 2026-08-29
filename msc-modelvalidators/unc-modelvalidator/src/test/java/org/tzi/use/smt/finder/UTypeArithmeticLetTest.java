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
 * End-to-end regression for {@code ocl.let}'s U-type ARITHMETIC initializers: {@code let u :
 * UReal = self.temp + self.hum in (u > 1.0).toBooleanC(0.9)} (and the minus/scalar variants).
 * USE's UReal arithmetic composes uncertainties by QUADRATURE ({@code sqrt(&sigma;1&sup2; +
 * &sigma;2&sup2;)} on doubles) -- which is linearly encodable exactly when both operands'
 * uncertainties are PROVEN SINGLETONS: the composed uncertainty is then a compile-time
 * constant, the representative a linear sum/difference of the source representatives, and the
 * threshold consumer's boundary arithmetic stays linear.
 *
 * <p>Before this slice only a bare attribute initializer let; composed initializers refused.
 * Non-boundary test values keep USE's double arithmetic and the solver's exact rationals in
 * agreement.
 */
public class UTypeArithmeticLetTest {

  private static final String MODEL =
      """
      model UArithLet
      class Sensor
      attributes
        temp : UReal
        hum : UReal
      end
      constraints
      context self : Sensor inv SumClears:
        let u : UReal = self.temp + self.hum in (u > 1.0).toBooleanC(0.9)
      context self : Sensor inv SumNegativeFails:
        let u : UReal = self.temp + self.hum in (u > 1.0).toBooleanC(0.9)
      context self : Sensor inv DifferenceClears:
        let u : UReal = self.temp - self.hum in (u > 0.8).toBooleanC(0.9)
      context self : Sensor inv DifferenceAboveFails:
        let u : UReal = self.temp - self.hum in (u > 0.95).toBooleanC(0.9)
      context self : Sensor inv ScalarPlusClears:
        let u : UReal = self.temp + 0.1 in (u > 1.0).toBooleanC(0.9)
      context self : Sensor inv QuadratureUsesBothSigmas:
        let u : UReal = self.temp + self.hum in (u > 1.0).toBooleanC(0.9)
      """;

  /** 1.2 + 0.3 = 1.5 with quadrature uncertainty ~0.0224 clears 1.0 at 90%. */
  @Test
  public void aSumInitializerComputesRepresentativeAndQuadratureUncertainty() throws Exception {
    ModelFinderResult result =
        find("SumClears", List.of("1.2"), List.of("0.02"), List.of("0.3"), List.of("0.01"));

    assertTrue(result.satisfiable());
    assertTrue(verdictFor(result, "Sensor::SumClears").holds());
  }

  /** -0.5 + 0.3 = -0.2: the same sum shape is genuinely unsatisfiable. */
  @Test
  public void aNegativeSumIsGenuinelyUnsatisfiable() throws Exception {
    ModelFinderResult result =
        find("SumNegativeFails", List.of("-0.5"), List.of("0.02"), List.of("0.3"), List.of("0.01"));

    assertFalse("-0.5 + 0.3 = -0.2 never clears 1.0", result.satisfiable());
  }

  /** Subtraction: 1.2 - 0.3 = 0.9 clears 0.8 (boundary 0.8368) but not 0.95 (0.9868). */
  @Test
  public void aDifferenceInitializerSplitsOnTheThreshold() throws Exception {
    ModelFinderResult below =
        find("DifferenceClears", List.of("1.2"), List.of("0.02"), List.of("0.3"), List.of("0.01"));
    assertTrue(below.satisfiable());
    assertTrue(verdictFor(below, "Sensor::DifferenceClears").holds());

    ModelFinderResult above =
        find("DifferenceAboveFails", List.of("1.2"), List.of("0.02"), List.of("0.3"), List.of("0.01"));
    assertFalse("0.9 does not clear 0.95 at 90% confidence", above.satisfiable());
  }

  /** A crisp scalar operand contributes zero uncertainty: 0.95 + 0.1 = 1.05 clears 1.0. */
  @Test
  public void aScalarOperandAddsItsValueWithoutUncertainty() throws Exception {
    ModelFinderResult result =
        find("ScalarPlusClears", List.of("0.95"), List.of("0.02"), List.of("0.3"), List.of("0.01"));

    assertTrue("0.95 + 0.1 = 1.05 with sigma 0.02 clears 1.0 at 90%", result.satisfiable());
    assertTrue(verdictFor(result, "Sensor::ScalarPlusClears").holds());
  }

  /**
   * The quadrature must compose BOTH sigmas: temp = 1.04 (sigma 0.01) + hum = 0.0 (sigma
   * 0.05) gives representative 1.04 and composed uncertainty ~0.051, so the 90%-confidence
   * boundary 1.0654 is NOT cleared -- UNSAT. A sigmaL-only under-approximation (boundary
   * ~1.0128) would wrongly report SAT.
   */
  @Test
  public void theQuadratureComposesBothSigmas() throws Exception {
    ModelFinderResult result =
        find("QuadratureUsesBothSigmas", List.of("1.04"), List.of("0.01"), List.of("0.0"), List.of("0.05"));

    assertFalse(
        "the composed uncertainty ~0.051 pushes the 90% boundary to ~1.065, above the"
            + " representative 1.04 -- a sigmaL-only under-approximation (boundary ~1.013)"
            + " would wrongly report SAT",
        result.satisfiable());
  }

  private static ModelFinderResult find(
      String invariantName,
      List<String> tempValues,
      List<String> tempUnc,
      List<String> humValues,
      List<String> humUnc)
      throws Exception {
    MModel model = compile();
    AnalysisConfiguration config =
        new AnalysisConfiguration(
            List.of(new ClassScope("Sensor", 1, 1)),
            List.of(),
            List.of(
                new AttributeDomain("Sensor", "temp", "value", tempValues, null, null),
                new AttributeDomain("Sensor", "temp", "uncertainty", tempUnc, null, null),
                new AttributeDomain("Sensor", "hum", "value", humValues, null, null),
                new AttributeDomain("Sensor", "hum", "uncertainty", humUnc, null, null)),
            Set.of("Sensor::" + invariantName),
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
    MModel model = USECompiler.compileSpecification(MODEL, "UArithLet", err, factory);
    err.flush();
    if (model == null) {
      throw new AssertionError("fixture model did not compile");
    }
    return model;
  }
}
