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
 * End-to-end regression for {@code ocl.let}'s MULTI-CANDIDATE U-type arithmetic: {@code let u :
 * UReal = self.temp + self.hum in (u > lit).toBooleanC(conf)} where the uncertainty domains
 * carry MULTIPLE configured candidates. The composed uncertainty enumerates the candidate PAIRS
 * -- each pair's quadrature sqrt(s1^2 + s2^2) is a compile-time constant guarded by both
 * uncertainty symbols -- linear in the pinned logic, capped at the 256-combination convention.
 *
 * <p>Before this slice the arithmetic initializer required proven-singleton uncertainties. The
 * discriminator: with rep = 1.5 and composed sigmas {0.0224, 0.0447, 0.051, 0.064}, the demand
 * (u &gt; 1.47) is satisfied only through the small-sigma pairs -- a sigmaL-only
 * under-approximation (boundary lit + 1.2816*0.02 = 1.4956) would wrongly report SAT.
 */
public class MultiCandidateUTypeArithmeticTest {

  private static final String MODEL =
      """
      model MultiCandidateUArith
      class Sensor
      attributes
        temp : UReal
        hum : UReal
      end
      constraints
      context self : Sensor inv MultiSumClears:
        let u : UReal = self.temp + self.hum in (u > 1.4).toBooleanC(0.9)
      context self : Sensor inv QuadratureComposesBothSigmas:
        let u : UReal = self.temp + self.hum in (u > 1.47).toBooleanC(0.9)
      context self : Sensor inv MultiSumAboveFails:
        let u : UReal = self.temp + self.hum in (u > 1.55).toBooleanC(0.9)
      """;

  /** Every pair clears 1.4 (rep 1.5, composed sigmas 0.0224..0.064): SAT with holds. */
  @Test
  public void allPairsClearALowThreshold() throws Exception {
    ModelFinderResult result =
        find("MultiSumClears", List.of("1.2"), List.of("0.02", "0.05"), List.of("0.3"), List.of("0.01", "0.04"));

    assertTrue(result.satisfiable());
    assertTrue(verdictFor(result, "Sensor::MultiSumClears").holds());
  }

  /**
   * The pair-enumeration discriminator: rep 1.5 clears 1.47 through the SMALL pair
   * (sigma_t = 0.02, sigma_h = 0.01 -> composed 0.0224; boundary 1.4987 <= 1.5) but NOT through
   * the large pair (sigma 0.051/0.064 -> boundaries 1.5354/1.5520). A max-composition mutation
   * (sigma_c = 0.05) would push the boundary to ~1.5341 and wrongly report UNSAT -- the SAT
   * here proves both uncertainty symbols gate and enumerate.
   */
  @Test
  public void theDemandSplitsThePairs() throws Exception {
    ModelFinderResult result =
        find("QuadratureComposesBothSigmas", List.of("1.2"), List.of("0.02", "0.05"), List.of("0.3"), List.of("0.01", "0.04"));

    assertTrue(
        "the small-sigma pair (0.02, 0.01) clears 1.47; the enumeration must offer it",
        result.satisfiable());
    assertTrue(verdictFor(result, "Sensor::QuadratureComposesBothSigmas").holds());
  }

  /** No pair clears 1.55 (boundary 1.55 + 1.2816*0.064 > 1.5): genuinely unsatisfiable. */
  @Test
  public void anUnreachableThresholdIsUnsatisfiable() throws Exception {
    ModelFinderResult miss =
        find("MultiSumAboveFails", List.of("1.2"), List.of("0.02", "0.05"), List.of("0.3"), List.of("0.01", "0.04"));

    assertFalse("rep 1.5 never clears 1.55 plus any composed sigma", miss.satisfiable());
  }

  /**
   * The same discriminator over the REVERSED candidate order: the large pair now sits at index 0,
   * so a mutant that drops the per-pair guards (always taking the ite chain's FIRST branch)
   * composes 0.064 and wrongly reports UNSAT, exactly as a max-composition mutant does. Together
   * with {@link #theDemandSplitsThePairs()} (whose list order puts the large pair LAST, catching a
   * fallback-only mutant) both guard positions are load-bearing.
   */
  @Test
  public void theDemandSplitsThePairsWithReversedCandidateOrder() throws Exception {
    ModelFinderResult result =
        find("QuadratureComposesBothSigmas", List.of("1.2"), List.of("0.05", "0.02"), List.of("0.3"), List.of("0.04", "0.01"));

    assertTrue(
        "the small-sigma pair (0.02, 0.01) still clears 1.47 under the reversed candidate order",
        result.satisfiable());
    assertTrue(verdictFor(result, "Sensor::QuadratureComposesBothSigmas").holds());
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
    MModel model = USECompiler.compileSpecification(MODEL, "MultiCandidateUArith", err, factory);
    err.flush();
    if (model == null) {
      throw new AssertionError("fixture model did not compile");
    }
    return model;
  }
}
