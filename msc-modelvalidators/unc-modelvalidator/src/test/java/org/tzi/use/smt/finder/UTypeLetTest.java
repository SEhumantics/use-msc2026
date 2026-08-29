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
 * End-to-end regression for {@code ocl.let}'s U-type slice: {@code let u : UReal = self.speed in
 * (u > 0.30).toBooleanC(0.95)} -- the let binding carries BOTH components of the paired U-type
 * encoding (the Int/Real representative and the non-negative Real uncertainty), and the
 * supported confidence-threshold consumer reads them from the binding exactly as it reads them
 * from an attribute's own symbols. UInteger lets pin the Int-representative {@code to_real}
 * lift through the binding.
 *
 * <p>Before this slice a U-typed let variable was refused outright ("only primitive Integer,
 * Boolean, Real, String, and Enum let bindings are supported"). The discriminator for the pair:
 * with speed = 0.31 and sigma = 0.02, crisp nominal erasure would ACCEPT 0.31 > 0.30, while the
 * uncertain reading at 95% confidence rejects -- so a representative-only binding is wrong in
 * exactly the way the nominal-erasure query exists to expose.
 */
public class UTypeLetTest {

  private static final String UREAL_MODEL =
      """
      model UTypeLet
      class C
      attributes
        speed : UReal
      end
      constraints
      context self : C inv LetThreshold:
        let u : UReal = self.speed in (u > 0.30).toBooleanC(0.95)
      """;

  private static final String UINTEGER_MODEL =
      """
      model UTypeLetInt
      class C
      attributes
        count : UInteger
      end
      constraints
      context self : C inv LetThresholdInt:
        let n : UInteger = self.count in (n > 2).toBooleanC(0.9)
      """;

  /** Above the boundary: the confidence-threshold reading holds (0.34 with sigma 0.02, 95%). */
  @Test
  public void aURealLetBindingCarriesThePairedEncodingAboveTheBoundary() throws Exception {
    ModelFinderResult result = find(UREAL_MODEL, "C", "speed", List.of("0.34"), List.of("0.02"));

    assertTrue("0.34 with sigma 0.02 clears 0.30 at 95% confidence", result.satisfiable());
    assertTrue(verdictFor(result, "C::LetThreshold").holds());
  }

  /**
   * The pair discriminator: 0.31 > 0.30 nominally, but the uncertain reading at 95% confidence
   * rejects -- so enforcing the invariant is UNSATISFIABLE. A representative-only binding (the
   * nominal-erasure bug) would wrongly report SAT here.
   */
  @Test
  public void theUncertaintyComponentFlowsThroughTheBinding() throws Exception {
    ModelFinderResult result = find(UREAL_MODEL, "C", "speed", List.of("0.31"), List.of("0.02"));

    assertFalse(
        "0.31 with sigma 0.02 does not clear 0.30 at 95% confidence -- the binding must"
            + " carry the uncertainty, not just the representative",
        result.satisfiable());
  }

  /** Zero uncertainty collapses to the crisp comparison: 0.31 > 0.30 holds. */
  @Test
  public void zeroUncertaintyCollapsesToTheCrispComparison() throws Exception {
    ModelFinderResult result = find(UREAL_MODEL, "C", "speed", List.of("0.31"), List.of("0"));

    assertTrue("sigma 0 makes the threshold exact: 0.31 > 0.30", result.satisfiable());
    assertTrue(verdictFor(result, "C::LetThreshold").holds());
  }

  /** UInteger: the Int representative lifts with to_real through the binding (rounding rule). */
  @Test
  public void aUIntegerLetBindingLiftsItsIntRepresentative() throws Exception {
    ModelFinderResult below =
        find(UINTEGER_MODEL, "C", "count", List.of("3"), List.of("0.5"));
    assertTrue("3 with sigma 0.5 clears 2 at 90% confidence", below.satisfiable());
    assertTrue(verdictFor(below, "C::LetThresholdInt").holds());

    ModelFinderResult above =
        find(UINTEGER_MODEL, "C", "count", List.of("1"), List.of("0.5"));
    assertFalse(
        "1 with sigma 0.5 does not clear 2 at 90% confidence", above.satisfiable());
  }

  private static ModelFinderResult find(
      String modelText,
      String className,
      String attributeName,
      List<String> valueDomain,
      List<String> uncertaintyDomain)
      throws Exception {
    MModel model = compile(modelText);
    AnalysisConfiguration config =
        new AnalysisConfiguration(
            List.of(new ClassScope(className, 1, 1)),
            List.of(),
            List.of(
                new AttributeDomain(className, attributeName, "value", valueDomain, null, null),
                new AttributeDomain(
                    className, attributeName, "uncertainty", uncertaintyDomain, null, null)),
            Set.of(className + "::" + (attributeName.equals("speed") ? "LetThreshold" : "LetThresholdInt")),
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

  private static MModel compile(String modelText) {
    ModelFactory factory = new ModelFactory();
    PrintWriter err = new PrintWriter(System.err);
    MModel model = USECompiler.compileSpecification(modelText, "UTypeLet", err, factory);
    err.flush();
    if (model == null) {
      throw new AssertionError("fixture model did not compile");
    }
    return model;
  }
}
