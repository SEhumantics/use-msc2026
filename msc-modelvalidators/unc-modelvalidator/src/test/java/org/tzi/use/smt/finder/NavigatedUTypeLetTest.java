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
import org.tzi.use.smt.config.AssociationScope;
import org.tzi.use.smt.config.AttributeDomain;
import org.tzi.use.smt.config.ClassScope;
import org.tzi.use.smt.config.ConfigurationVocabulary;
import org.tzi.use.smt.config.QueryParser;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;

/**
 * End-to-end regression for {@code ocl.let}'s NAVIGATED U-type initializers: {@code let u : UReal
 * = x.gauge.speed in (u > 0.30).toBooleanC(0.95)} -- the initializer is a single-valued navigation
 * hop, so the binding's symbols are the PER-SLOT selections over the end's slot view (the value
 * chain the ordinary navigated attribute read builds, plus the uncertainty twin), the binding's
 * definedness is the link itself, and the candidate-enumerating consumers (UBoolean) nest their
 * configured candidates under each slot's link guard.
 *
 * <p>Before this slice a navigated initializer refused ("only a bare U-typed attribute access on
 * a context variable, or another U-type let variable"). Folded navigated ends (a configured
 * subclass under a superclass-typed end) keep refusing: their slots carry per-concrete-class
 * configured domains, and enumerating candidates over them is a slice of its own.
 */
public class NavigatedUTypeLetTest {

  private static final String MODEL =
      """
      model NavUTypeLet
      class Station
      attributes
        name : String
      end
      class Gauge
      attributes
        speed : UReal
        reliable : UBoolean
      end
      association Link between
        Station [0..*] role station
        Gauge [0..1] role gauge
      end
      constraints
      context x : Station inv NavLetThreshold:
        let u : UReal = x.gauge.speed in (u > 0.30).toBooleanC(0.95)
      context x : Station inv NavLetReliable:
        let b : UBoolean = x.gauge.reliable in b.toBooleanC(0.85)
      context x : Station inv NavLetReliableAbove:
        let b : UBoolean = x.gauge.reliable in b.toBooleanC(0.95)
      context x : Station inv NavLetTwoGauges:
        let b : UBoolean = x.gauge.reliable in b.toBooleanC(0.85)
      """;

  /** 0.34 with sigma 0.02 clears 0.30 at 95% through the navigated let. */
  @Test
  public void aNavigatedURealLetCarriesValueAndUncertainty() throws Exception {
    ModelFinderResult result =
        find("NavLetThreshold", List.of("0.34"), List.of("0.02"), List.of("0.9"), 1);

    assertTrue(result.satisfiable());
    assertTrue(verdictFor(result, "Station::NavLetThreshold").holds());
  }

  /** The pair discriminator through the navigation: 0.31/sigma 0.02 fails the 95% threshold. */
  @Test
  public void theUncertaintyFlowsThroughTheNavigatedLet() throws Exception {
    ModelFinderResult result =
        find("NavLetThreshold", List.of("0.31"), List.of("0.02"), List.of("0.9"), 1);

    assertFalse(
        "nominal erasure would accept 0.31 > 0.30; the navigated let must carry the uncertainty",
        result.satisfiable());
  }

  /** The UBoolean family through the navigation: p = 0.9 clears 0.85, never 0.95. */
  @Test
  public void aNavigatedUBooleanLetCarriesItsStoredProbability() throws Exception {
    ModelFinderResult holds =
        find("NavLetReliable", List.of("0.34"), List.of("0.02"), List.of("0.9"), 1);
    assertTrue("p = 0.9 clears the 0.85 threshold through the navigated let", holds.satisfiable());
    assertTrue(verdictFor(holds, "Station::NavLetReliable").holds());

    ModelFinderResult miss =
        find("NavLetReliableAbove", List.of("0.34"), List.of("0.02"), List.of("0.9"), 1);
    assertFalse("p = 0.9 does not clear the 0.95 threshold", miss.satisfiable());
  }

  /** The navigation's definedness IS the link: with links forbidden the let is undefined. */
  @Test
  public void anUnlinkedNavigationMakesTheLetUndefined() throws Exception {
    ModelFinderResult result =
        find("NavLetThreshold", List.of("0.34"), List.of("0.02"), List.of("0.9"), 0);

    assertFalse(
        "with no links x.gauge is undefined, so the let is undefined and the invariant fails",
        result.satisfiable());
  }

  /**
   * With TWO gauges whose shared probability domain offers {0.5, 0.9}, the case enumeration must
   * guard each candidate under ITS slot's link term: the solver links the station to the gauge
   * carrying 0.9 and the threshold holds. A guard-less enumeration would admit the unlinked
   * slot's 0.9 while the LINKED gauge carries 0.5 -- USE's re-evaluation of that witness fails.
   */
  @Test
  public void aTwoGaugeScenarioSelectsTheLinkedSlotsCandidates() throws Exception {
    MModel model = compile();
    AnalysisConfiguration config =
        new AnalysisConfiguration(
            List.of(new ClassScope("Station", 1, 1), new ClassScope("Gauge", 2, 2)),
            List.of(new AssociationScope("Link", 1, 1)),
            List.of(
                new AttributeDomain("Station", "name", null, List.of("north"), null, null),
                new AttributeDomain("Gauge", "speed", "value", List.of("0.34"), null, null),
                new AttributeDomain("Gauge", "speed", "uncertainty", List.of("0.02"), null, null),
                new AttributeDomain(
                    "Gauge", "reliable", "probability", List.of("0.5", "0.9"), null, null)),
            Set.of("Station::NavLetTwoGauges"),
            QueryParser.parse("satisfy", ConfigurationVocabulary.fromModel(model)),
            Duration.ofSeconds(30),
            1);
    ModelFinderResult result = SmtModelFinder.find(model, config);

    assertTrue("the linked gauge can carry 0.9, clearing 0.85", result.satisfiable());
    assertTrue(verdictFor(result, "Station::NavLetTwoGauges").holds());
  }

  private static ModelFinderResult find(
      String invariantName,
      List<String> speedValues,
      List<String> speedUncertainties,
      List<String> reliableProbabilities,
      int maxLinks)
      throws Exception {
    MModel model = compile();
    AnalysisConfiguration config =
        new AnalysisConfiguration(
            List.of(new ClassScope("Station", 1, 1), new ClassScope("Gauge", 1, 1)),
            List.of(new AssociationScope("Link", 0, maxLinks)),
            List.of(
                new AttributeDomain("Station", "name", null, List.of("north"), null, null),
                new AttributeDomain("Gauge", "speed", "value", speedValues, null, null),
                new AttributeDomain("Gauge", "speed", "uncertainty", speedUncertainties, null, null),
                new AttributeDomain(
                    "Gauge", "reliable", "probability", reliableProbabilities, null, null)),
            Set.of("Station::" + invariantName),
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
    MModel model = USECompiler.compileSpecification(MODEL, "NavUTypeLet", err, factory);
    err.flush();
    if (model == null) {
      throw new AssertionError("fixture model did not compile");
    }
    return model;
  }
}
