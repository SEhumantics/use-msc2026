package org.tzi.use.smt.finder;

import static org.junit.Assert.assertEquals;
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
 * End-to-end regression for UNCERTAIN-VS-UNCERTAIN EQUALITY of two UBoolean attributes --
 * {@code (s.a = s.b).toBooleanC(theta)} and its {@code <>} counterpart. Semantics are USE's
 * OWN, delegated to (not hand-derived): {@code =} lowers to {@code UBooleanValue.uEquals} ->
 * {@code equivalent()} -> probability {@code 1 - |p1 - p2|}; {@code <>} lowers to
 * {@code uDistinct} -> {@code |p1 - p2|}. Both are compile-time constants per configured
 * probability-candidate pair, so the encoding enumerates the cross product of the two sides'
 * probability candidates (existential: SOME pair combination must clear theta) with guards
 * pinning both selections -- a witness can only carry a combination the probability holds for.
 */
public class UBooleanEqualityTest {

  private static final String MODEL =
      """
      model UBEq
      class S
      attributes
        a : UBoolean
        b : UBoolean
      end
      constraints
      context s : S inv EqHolds:
        (s.a = s.b).toBooleanC(0.9)
      context s : S inv OnePairPasses:
        (s.a = s.b).toBooleanC(0.85)
      context s : S inv AllPairsFail:
        (s.a = s.b).toBooleanC(0.95)
      context s : S inv NeqDiscriminates:
        (s.a <> s.b).toBooleanC(0.25)
      """;

  private static final List<ClassScope> SCOPES =
      List.of(new ClassScope("S", 1, 1));

  /** General case: equal probabilities -> equivalent() = 1.0. */
  @Test
  public void equalProbabilitiesHoldTheEquality() throws Exception {
    ModelFinderResult match = find("EqHolds",
        List.of("0.9"), List.of("0.9"));
    assertTrue(match.satisfiable());
    assertTrue(verdictFor(match, "S::EqHolds").holds());
  }

  /** Asymmetric discriminator: 1 - |0.9 - 0.2| = 0.3 < 0.9 refutes. */
  @Test
  public void farApartProbabilitiesRefuteTheEquality() throws Exception {
    ModelFinderResult miss = find("EqHolds",
        List.of("0.9"), List.of("0.2"));
    assertFalse("equivalent() = 1 - |0.9 - 0.2| = 0.3 < 0.9", miss.satisfiable());
  }

  /**
   * Cross-combination: a's candidates {0.9, 0.5} against b's {0.8} at theta 0.85 -- only the
   * (0.9, 0.8) pair clears it, so the witness MUST carry a-probability 0.9.
   */
  @Test
  public void witnessMustCarryThePassingCandidate() throws Exception {
    ModelFinderResult match = find("OnePairPasses",
        List.of("0.9", "0.5"), List.of("0.8"));
    assertTrue(match.satisfiable());
    assertTrue(verdictFor(match, "S::OnePairPasses").holds());

    MModel model = compile();
    var state = match.system().state();
    var sensor = state.objectsOfClass(model.getClass("S")).iterator().next();
    var a = (org.tzi.use.uml.ocl.value.UBooleanValue)
        sensor.state(state).attributeValue("a");
    assertEquals("the witness must carry the passing a-candidate 0.9",
        0.9, a.probability(), 1.0e-12);
  }

  /**
   * Exactly-at-and-beyond boundary: theta 0.95 excludes EVERY pair of {0.9, 0.5} x {0.8}
   * (best is 0.9) -- UNSAT.
   */
  @Test
  public void thetaBeyondEveryPairRefutes() throws Exception {
    ModelFinderResult miss = find("AllPairsFail",
        List.of("0.9", "0.5"), List.of("0.8"));
    assertFalse("best pair probability 0.9 < 0.95", miss.satisfiable());
  }

  /** <> is |p1 - p2|: with candidates {0.5} vs {0.8}, 0.3 >= 0.25 holds. */
  @Test
  public void neqHoldsWhenTheProbabilityGapIsLarge() throws Exception {
    ModelFinderResult match = find("NeqDiscriminates",
        List.of("0.5"), List.of("0.8"));
    assertTrue(match.satisfiable());
    assertTrue(verdictFor(match, "S::NeqDiscriminates").holds());
  }

  /** <> with a small gap: 0.1 < 0.25 refutes. */
  @Test
  public void neqRefutesWhenTheProbabilityGapIsSmall() throws Exception {
    ModelFinderResult miss = find("NeqDiscriminates",
        List.of("0.9"), List.of("0.8"));
    assertFalse("|0.9 - 0.8| = 0.1 < 0.25", miss.satisfiable());
  }

  private static ModelFinderResult find(
      String invariantName, List<String> aProbs, List<String> bProbs) throws Exception {
    MModel model = compile();
    AnalysisConfiguration config =
        new AnalysisConfiguration(
            SCOPES,
            List.of(),
            List.of(
                new AttributeDomain("S", "a", "probability", aProbs, null, null),
                new AttributeDomain("S", "b", "probability", bProbs, null, null)),
            Set.of("S::" + invariantName),
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
    MModel model = USECompiler.compileSpecification(MODEL, "UBEq", err, factory);
    err.flush();
    if (model == null) {
      throw new AssertionError("fixture model did not compile:\n" + err);
    }
    return model;
  }
}
