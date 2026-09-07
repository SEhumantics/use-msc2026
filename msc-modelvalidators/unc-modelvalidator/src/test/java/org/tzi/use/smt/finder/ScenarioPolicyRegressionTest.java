package org.tzi.use.smt.finder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Duration;
import java.util.ArrayList;
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
 * The scenario semantics, pinned as regressions.
 *
 * <p>Every assertion here reads {@link ModelFinderResult#outcome()} or the derived {@link
 * ResultClassification}, never {@code satisfiable()}. {@code satisfiable()} is {@code outcome ==
 * SATISFIED}, so {@code assertFalse(satisfiable())} cannot tell a refutation from a solver
 * {@code unknown} or a timeout, and a "policy separation" asserted that way would pass on a
 * degradation of the tool rather than on the property under test.
 */
public class ScenarioPolicyRegressionTest {

  private static final String CRISP =
      """
      model Crisp
      class Box
      attributes
        size : Integer
      end
      constraints
      context b : Box inv bigEnough: b.size > 3
      """;

  private static final String UREAL =
      """
      model Single
      class Sensor
      attributes
        reading : UReal
      end
      constraints
      context s : Sensor inv fast: (s.reading > 0.30).toBooleanC(0.95)
      """;

  /** The banded shape: a positive demand and a NEGATED one, so the window shifts with sigma. */
  private static final String BANDED =
      """
      model Banded
      class Sensor
      attributes
        reading : UReal
      end
      constraints
      context s : Sensor inv fast: (s.reading > 0.30).toBooleanC(0.95)
      context s : Sensor inv notTooFast: not ((s.reading > 0.36).toBooleanC(0.95))
      """;

  private static final String SYMMETRIC =
      """
      model Symmetric
      class Node
      attributes
        weight : UReal
      end
      constraints
      context n : Node inv heavy: (n.weight > 0.30).toBooleanC(0.95)
      """;

  // ------------------------------------------------------------------ crisp / degenerate spaces

  /**
   * Crisp conservativity: with no uncertainty-typed attribute there is no scenario coordinate, so
   * Sigma_K is the singleton containing the EMPTY assignment and the three policies are the same
   * question. Asserting equal CLASSIFICATIONS, not equal witnesses -- separate solves over a
   * symmetric model may legitimately return different snapshots.
   */
  @Test
  public void crispModelClassifiesIdenticallyUnderAllThreePolicies() throws Exception {
    MModel model = compile(CRISP, "Crisp");
    List<AttributeDomain> domains =
        List.of(new AttributeDomain("Box", "size", null, List.of("4", "5"), null, null));
    List<ResultClassification> seen = new ArrayList<>();
    for (String profile : profiles()) {
      ModelFinderResult result =
          find(model, profile, List.of(new ClassScope("Box", 1, 1, List.of("b1"))), domains,
              Set.of("Box::bigEnough"));
      seen.add(ResultClassification.of(result, Set.of("Box::bigEnough")));
      assertEquals(
          profile + ": no uncertainty coordinate means the singleton empty scenario",
          1,
          result.scenarios().size());
    }
    assertEquals("all three policies agree on a crisp model", 1, Set.copyOf(seen).size());
    assertEquals(ResultClassification.SAT_VALIDATED, seen.get(0));
  }

  /** A singleton scenario domain collapses the policies for the same reason: |Sigma_K| = 1. */
  @Test
  public void singletonScenarioDomainCollapsesThePolicies() throws Exception {
    MModel model = compile(UREAL, "Single");
    List<AttributeDomain> domains =
        List.of(
            new AttributeDomain("Sensor", "reading", "value", List.of("0.45"), null, null),
            new AttributeDomain("Sensor", "reading", "uncertainty", List.of("0.02"), null, null));
    for (String profile : profiles()) {
      ModelFinderResult result = find(model, profile, sensorScope(), domains, Set.of("Sensor::fast"));
      assertEquals(profile, ProfileOutcome.SATISFIED, result.outcome());
      assertEquals(profile + ": one configured deviation is one scenario", 1,
          result.scenarios().size());
    }
  }

  // ------------------------------------------------------------------------- the two separations

  /** EXISTS satisfied while COVER is REFUTED: one representative cannot meet the wider deviation. */
  @Test
  public void existsSeparatesFromCover() throws Exception {
    MModel model = compile(UREAL, "Single");
    List<AttributeDomain> domains = twoDeviations("0.35");
    assertEquals(ProfileOutcome.SATISFIED,
        find(model, "exists satisfy", sensorScope(), domains, Set.of("Sensor::fast")).outcome());
    ModelFinderResult cover =
        find(model, "cover satisfy", sensorScope(), domains, Set.of("Sensor::fast"));
    assertEquals(ProfileOutcome.REFUTED, cover.outcome());
    assertEquals("exactly the tighter-deviation scenario is met", 1,
        cover.scenarios().stream().filter(s -> s.outcome() == ScenarioOutcome.WITNESSED).count());
  }

  /**
   * COVER satisfied while UNIFORM is REFUTED. This needs the BANDED shape: with only positive
   * demands the satisfying window merely WIDENS as sigma grows, so the representative clearing the
   * widest deviation clears them all and the two policies cannot be separated at all.
   */
  @Test
  public void coverSeparatesFromUniformOnlyWithAShiftingWindow() throws Exception {
    MModel banded = compile(BANDED, "Banded");
    List<AttributeDomain> domains = twoDeviations("0.35", "0.42");
    Set<String> active = Set.of("Sensor::fast", "Sensor::notTooFast");
    assertEquals(ProfileOutcome.SATISFIED,
        find(banded, "cover satisfy", sensorScope(), domains, active).outcome());
    assertEquals("no single representative lies in both disjoint windows",
        ProfileOutcome.REFUTED,
        find(banded, "uniform satisfy", sensorScope(), domains, active).outcome());
  }

  /**
   * The monotone case. Every demand positive with theta >= 1/2 makes satisfaction ANTITONE in
   * sigma, so COVER implies UNIFORM and the two coincide -- the same configuration that separates
   * them under {@link #coverSeparatesFromUniformOnlyWithAShiftingWindow} does not separate them
   * here.
   */
  @Test
  public void monotoneModelCollapsesCoverAndUniform() throws Exception {
    MModel model = compile(UREAL, "Single");
    List<AttributeDomain> domains = twoDeviations("0.35", "0.42");
    assertEquals(ProfileOutcome.SATISFIED,
        find(model, "cover satisfy", sensorScope(), domains, Set.of("Sensor::fast")).outcome());
    assertEquals(ProfileOutcome.SATISFIED,
        find(model, "uniform satisfy", sensorScope(), domains, Set.of("Sensor::fast")).outcome());
  }

  // ------------------------------------------------------------------------- slots and symmetry

  /**
   * Sigma_K is built over the CANDIDATE object slots fixed by the bounds, not over the objects a
   * particular snapshot creates, so it is determined before any snapshot is chosen. A coordinate
   * belonging to a slot the snapshot leaves absent is therefore still enumerated -- and ignored
   * when evaluating, which is why a scope admitting an empty population is still satisfiable.
   */
  @Test
  public void coordinatesForAbsentCandidateSlotsAreEnumeratedButIgnored() throws Exception {
    MModel model = compile(UREAL, "Single");
    List<AttributeDomain> domains = twoDeviations("0.45");
    ModelFinderResult cover =
        find(model, "cover satisfy", List.of(new ClassScope("Sensor", 0, 2, List.of())), domains,
            Set.of("Sensor::fast"));
    assertEquals(ProfileOutcome.SATISFIED, cover.outcome());
    assertEquals("two candidate slots, two deviations each: |Sigma_K| = 2^2", 4,
        cover.scenarios().size());
  }

  /**
   * A symmetric model: two interchangeable objects over the same domain. The policies must agree in
   * CLASSIFICATION; the witnesses they return need not be the same object assignment, which is why
   * nothing here asserts on witness identity.
   */
  @Test
  public void symmetricModelAgreesInClassificationAcrossPolicies() throws Exception {
    MModel model = compile(SYMMETRIC, "Symmetric");
    List<AttributeDomain> domains =
        List.of(
            new AttributeDomain("Node", "weight", "value", List.of("0.45"), null, null),
            new AttributeDomain("Node", "weight", "uncertainty", List.of("0.02"), null, null));
    List<ClassScope> scope = List.of(new ClassScope("Node", 2, 2, List.of("n1", "n2")));
    List<ResultClassification> seen = new ArrayList<>();
    for (String profile : profiles()) {
      seen.add(
          ResultClassification.of(
              find(model, profile, scope, domains, Set.of("Node::heavy")), Set.of("Node::heavy")));
    }
    assertEquals("symmetry does not change the classification", 1, Set.copyOf(seen).size());
    assertEquals(ResultClassification.SAT_VALIDATED, seen.get(0));
  }

  // --------------------------------------------------------------------- numerical qualification

  /**
   * A negative result touching a U-type confidence threshold rests on an ENCLOSURE of USE's CDF
   * approximation, so it must be reported as INCONCLUSIVE_NUMERICAL and never as UNSAT_EXACT.
   * Asserted immediately below, at, and above the boundary.
   *
   * <p>The boundary for {@code (reading > 0.30).toBooleanC(0.95)} at sigma = 0.02 is
   * {@code 0.30 + z(0.95) * 0.02 = 0.33289707...}. The representatives below it (0.32, 0.3328) must
   * classify INCONCLUSIVE_NUMERICAL and never UNSAT_EXACT; those above it (0.3329, 0.34) must be
   * satisfied. The pair 0.3328/0.3329 straddles the boundary at 1e-4.
   */
  @Test
  public void negativesAtAQuantileBoundaryAreInconclusiveNotExact() throws Exception {
    MModel model = compile(UREAL, "Single");
    // Straddling the boundary at 1e-4: 0.3328 is below it and 0.3329 above.
    for (String representative : new String[] {"0.32", "0.3328"}) {
      ModelFinderResult result =
          find(model, "exists satisfy", sensorScope(), oneDeviation(representative),
              Set.of("Sensor::fast"));
      assertEquals(representative, ProfileOutcome.REFUTED, result.outcome());
      assertTrue(representative + ": a quantile enclosure was performed",
          result.quantileEnclosures() > 0);
      assertEquals(
          representative + ": a threshold negative is never an exact refutation",
          ResultClassification.INCONCLUSIVE_NUMERICAL,
          ResultClassification.of(result, Set.of("Sensor::fast")));
      assertNotEquals(ResultClassification.UNSAT_EXACT,
          ResultClassification.of(result, Set.of("Sensor::fast")));
    }
    for (String representative : new String[] {"0.3329", "0.34"}) {
      ModelFinderResult above =
          find(model, "exists satisfy", sensorScope(), oneDeviation(representative),
              Set.of("Sensor::fast"));
      assertEquals(representative + ": above the boundary the demand is met",
          ProfileOutcome.SATISFIED, above.outcome());
      assertEquals(ResultClassification.SAT_VALIDATED,
          ResultClassification.of(above, Set.of("Sensor::fast")));
    }
  }

  /** A crisp refutation performs no enclosure, so it IS an exact bounded refutation. */
  @Test
  public void crispRefutationIsExact() throws Exception {
    MModel model = compile(CRISP, "Crisp");
    ModelFinderResult result =
        find(model, "exists satisfy", List.of(new ClassScope("Box", 1, 1, List.of("b1"))),
            List.of(new AttributeDomain("Box", "size", null, List.of("1", "2"), null, null)),
            Set.of("Box::bigEnough"));
    assertEquals(ProfileOutcome.REFUTED, result.outcome());
    assertEquals("no U-type threshold, so no enclosure", 0, result.quantileEnclosures());
    assertEquals(ResultClassification.UNSAT_EXACT,
        ResultClassification.of(result, Set.of("Box::bigEnough")));
  }

  // ------------------------------------------------------------------------------------ helpers

  private static String[] profiles() {
    return new String[] {"exists satisfy", "cover satisfy", "uniform satisfy"};
  }

  private static List<ClassScope> sensorScope() {
    return List.of(new ClassScope("Sensor", 1, 1, List.of("s1")));
  }

  private static List<AttributeDomain> twoDeviations(String... representatives) {
    return List.of(
        new AttributeDomain("Sensor", "reading", "value", List.of(representatives), null, null),
        new AttributeDomain("Sensor", "reading", "uncertainty", List.of("0.02", "0.06"), null, null));
  }

  private static List<AttributeDomain> oneDeviation(String representative) {
    return List.of(
        new AttributeDomain("Sensor", "reading", "value", List.of(representative), null, null),
        new AttributeDomain("Sensor", "reading", "uncertainty", List.of("0.02"), null, null));
  }

  private static ModelFinderResult find(
      MModel model,
      String profile,
      List<ClassScope> scopes,
      List<AttributeDomain> domains,
      Set<String> active)
      throws Exception {
    ConfigurationVocabulary vocabulary = ConfigurationVocabulary.fromModel(model);
    return SmtModelFinder.find(
        model,
        new AnalysisConfiguration(
            scopes, List.of(), domains, active,
            QueryParser.parse(profile, vocabulary), Duration.ofSeconds(60), 1));
  }

  private static MModel compile(String text, String name) {
    StringWriter buffer = new StringWriter();
    MModel model =
        USECompiler.compileSpecification(
            text, name, new PrintWriter(buffer, true), new ModelFactory());
    if (model == null) {
      throw new AssertionError("fixture did not compile:\n" + buffer);
    }
    return model;
  }
}
