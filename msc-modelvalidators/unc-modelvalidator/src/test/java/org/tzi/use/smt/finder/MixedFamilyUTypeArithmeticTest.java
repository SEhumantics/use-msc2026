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
 * End-to-end regression for {@code ocl.let}'s MIXED-FAMILY U-type arithmetic: {@code let u :
 * UReal = self.count + self.ratio in ...} where {@code count} is UInteger and {@code ratio} is
 * UReal. USE widens the UInteger operand through UReal, so the composed representative lifts the
 * Int-sorted part with {@code to_real}, the composed uncertainty is still the quadrature of the
 * two proven-singleton sigmas, and the composed family is UREAL.
 *
 * <p>Before this slice mixed-family initializers refused ("mixed UInteger/UReal arithmetic is
 * not supported; both operands must be the same family").
 */
public class MixedFamilyUTypeArithmeticTest {

  private static final String MODEL =
      """
      model MixedFamilyUArith
      class Camera
      attributes
        count : UInteger
        ratio : UReal
      end
      constraints
      context self : Camera inv MixedSumClears:
        let u : UReal = self.count + self.ratio in (u > 1.0).toBooleanC(0.9)
      context self : Camera inv MixedQuadratureComposesBothSigmas:
        let u : UReal = self.count + self.ratio in (u > 1.0).toBooleanC(0.9)
      context self : Camera inv MixedSumZeroUncertainty:
        let u : UReal = self.count + self.ratio in (u > 2.2).toBooleanC(0.9)
      """;

  /** 1.4 + 0.6 = 2.0 with quadrature sigma ~0.447 clears the 1.0 threshold at 90%. */
  @Test
  public void aMixedFamilySumComputesTheWidenedRepresentative() throws Exception {
    ModelFinderResult result =
        find("MixedSumClears", List.of("1"), List.of("0.4"), List.of("0.6"), List.of("0.2"));

    assertTrue(result.satisfiable());
    assertTrue(verdictFor(result, "Camera::MixedSumClears").holds());
  }

  /**
   * The quadrature composes BOTH sigmas across families: count's sigma 0.9 dominates, pushing
   * the 90% boundary to ~2.155 above the representative sum 1.1 -- UNSAT. A sigma_ratio-only
   * under-approximation (boundary ~1.064) would wrongly report SAT.
   */
  @Test
  public void theMixedQuadratureComposesBothSigmas() throws Exception {
    ModelFinderResult result =
        find("MixedQuadratureComposesBothSigmas", List.of("1"), List.of("0.9"), List.of("0.1"), List.of("0.05"));

    assertFalse(
        "count's sigma 0.9 dominates the quadrature: the boundary ~2.155 exceeds 1.1",
        result.satisfiable());
  }

  /** Zero sigmas collapse to the crisp comparison: 2 + 0.3 = 2.3 clears 2.2 exactly. */
  @Test
  public void zeroSigmasCollapseToTheCrispComparison() throws Exception {
    ModelFinderResult result =
        find("MixedSumZeroUncertainty", List.of("2"), List.of("0"), List.of("0.3"), List.of("0"));

    assertTrue(result.satisfiable());
    assertTrue(verdictFor(result, "Camera::MixedSumZeroUncertainty").holds());
  }

  private static ModelFinderResult find(
      String invariantName,
      List<String> countValues,
      List<String> countSigma,
      List<String> ratioValues,
      List<String> ratioSigma)
      throws Exception {
    MModel model = compile();
    AnalysisConfiguration config =
        new AnalysisConfiguration(
            List.of(new ClassScope("Camera", 1, 1)),
            List.of(),
            List.of(
                new AttributeDomain("Camera", "count", "value", countValues, null, null),
                new AttributeDomain("Camera", "count", "uncertainty", countSigma, null, null),
                new AttributeDomain("Camera", "ratio", "value", ratioValues, null, null),
                new AttributeDomain("Camera", "ratio", "uncertainty", ratioSigma, null, null)),
            Set.of("Camera::" + invariantName),
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
    MModel model = USECompiler.compileSpecification(MODEL, "MixedFamilyUArith", err, factory);
    err.flush();
    if (model == null) {
      throw new AssertionError("fixture model did not compile");
    }
    return model;
  }
}
