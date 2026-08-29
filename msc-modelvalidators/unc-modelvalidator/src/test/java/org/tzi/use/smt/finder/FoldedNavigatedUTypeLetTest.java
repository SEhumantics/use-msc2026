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
 * End-to-end regression for {@code ocl.let}'s FOLDED navigated U-type initializers: {@code let u
 * : UReal = x.gauge.speed in ...} where the {@code gauge} end's slot view folds a configured
 * subclass. Each destination slot carries its CONCRETE class's configured candidate domains, so
 * the candidate-enumerating consumers read per-slot domains -- a PrecisionGauge slot's
 * spelling/confidence differ from a plain Gauge slot's, and the let binding's slot list preserves
 * exactly that.
 *
 * <p>Before this slice a folded navigated initializer refused ("the configured subclasses carry
 * per-concrete-class candidate domains, which is a slice of its own"); only unfolded end views
 * let.
 */
public class FoldedNavigatedUTypeLetTest {

  private static final String MODEL =
      """
      model FoldedNavUTypeLet
      class Station
      attributes
        name : String
      end
      class Gauge
      attributes
        speed : UReal
        tag : UString
      end
      class PrecisionGauge < Gauge
      end
      association R between
        Station [0..*] role station
        Gauge [0..1] role gauge
      end
      constraints
      context x : Station inv FoldedNavFast:
        let u : UReal = x.gauge.speed in (u > 0.30).toBooleanC(0.95)
      context x : Station inv FoldedNavSlow:
        let u : UReal = x.gauge.speed in (u > 0.30).toBooleanC(0.95)
      context x : Station inv FoldedNavTagPG:
        let s : UString = x.gauge.tag in (s = 'PG').toBooleanC(0.8)
      context x : Station inv FoldedNavTagG:
        let s : UString = x.gauge.tag in (s = 'G').toBooleanC(0.5)
      """;

  /**
   * Distinct per-concrete-class values: Gauge.speed = {0.31} (fails the 95% threshold),
   * PrecisionGauge.speed = {0.34} (clears it). The demand forces the link to the
   * PrecisionGauge slot through the folded view.
   */
  @Test
  public void theLetSelectsTheConcreteSlotWhoseValueClearsTheThreshold() throws Exception {
    ModelFinderResult result =
        find(
            "Station::FoldedNavFast",
            List.of("0.31"),
            List.of("0.34"),
            List.of("G"),
            List.of("PG"));

    assertTrue(
        "the link to the PrecisionGauge (0.34) must clear the 0.30 threshold at 95%",
        result.satisfiable());
    assertTrue(verdictFor(result, "Station::FoldedNavFast").holds());
  }

  /** The polarity pin: when NO concrete class offers a clearing value, the demand fails. */
  @Test
  public void whenNoConcreteClassClearsTheThresholdTheDemandFails() throws Exception {
    MModel model = compile();
    AnalysisConfiguration config =
        new AnalysisConfiguration(
            List.of(
                new ClassScope("Station", 1, 1),
                new ClassScope("Gauge", 1, 1),
                new ClassScope("PrecisionGauge", 1, 1)),
            List.of(new AssociationScope("R", 0, 1)),
            List.of(
                new AttributeDomain("Station", "name", null, List.of("north"), null, null),
                new AttributeDomain("Gauge", "speed", "value", List.of("0.31"), null, null),
                new AttributeDomain("Gauge", "speed", "uncertainty", List.of("0.02"), null, null),
                new AttributeDomain(
                    "PrecisionGauge", "speed", "value", List.of("0.31"), null, null),
                new AttributeDomain(
                    "PrecisionGauge", "speed", "uncertainty", List.of("0.02"), null, null),
                new AttributeDomain("Gauge", "tag", "value", List.of("G"), null, null),
                new AttributeDomain("Gauge", "tag", "confidence", List.of("0.6"), null, null),
                new AttributeDomain("PrecisionGauge", "tag", "value", List.of("PG"), null, null),
                new AttributeDomain(
                    "PrecisionGauge", "tag", "confidence", List.of("0.9"), null, null)),
            Set.of("Station::FoldedNavSlow"),
            QueryParser.parse("satisfy", ConfigurationVocabulary.fromModel(model)),
            Duration.ofSeconds(30),
            1);

    assertFalse(
        "both concrete slots offer 0.31/sigma 0.02, which fails the 95% threshold",
        SmtModelFinder.find(model, config).satisfiable());
  }

  /**
   * The UString folded discriminator: the spelling/confidence domains differ per concrete class
   * ('G' with c=0.6 on Gauge, 'PG' with c=0.9 on PrecisionGauge); each demand forces the link to
   * the matching concrete slot.
   */
  @Test
  public void aUStringLetReadsThePerConcreteClassSpellingAndConfidence() throws Exception {
    ModelFinderResult pg =
        find(
            "Station::FoldedNavTagPG",
            List.of("0.31"),
            List.of("0.34"),
            List.of("G"),
            List.of("PG"));
    assertTrue("'PG' is the PrecisionGauge slot's spelling", pg.satisfiable());
    assertTrue(verdictFor(pg, "Station::FoldedNavTagPG").holds());

    ModelFinderResult g =
        find(
            "Station::FoldedNavTagG",
            List.of("0.31"),
            List.of("0.34"),
            List.of("G"),
            List.of("PG"));
    assertTrue("'G' is the plain Gauge slot's spelling", g.satisfiable());
    assertTrue(verdictFor(g, "Station::FoldedNavTagG").holds());
  }

  private static ModelFinderResult find(
      String invariantName,
      List<String> gaugeSpeed,
      List<String> precisionSpeed,
      List<String> gaugeTag,
      List<String> precisionTag)
      throws Exception {
    MModel model = compile();
    AnalysisConfiguration config =
        new AnalysisConfiguration(
            List.of(
                new ClassScope("Station", 1, 1),
                new ClassScope("Gauge", 1, 1),
                new ClassScope("PrecisionGauge", 1, 1)),
            List.of(new AssociationScope("R", 0, 1)),
            List.of(
                new AttributeDomain("Station", "name", null, List.of("north"), null, null),
                new AttributeDomain("Gauge", "speed", "value", gaugeSpeed, null, null),
                new AttributeDomain("Gauge", "speed", "uncertainty", List.of("0.02"), null, null),
                new AttributeDomain("PrecisionGauge", "speed", "value", precisionSpeed, null, null),
                new AttributeDomain(
                    "PrecisionGauge", "speed", "uncertainty", List.of("0.02"), null, null),
                new AttributeDomain("Gauge", "tag", "value", gaugeTag, null, null),
                new AttributeDomain("Gauge", "tag", "confidence", List.of("0.6"), null, null),
                new AttributeDomain("PrecisionGauge", "tag", "value", precisionTag, null, null),
                new AttributeDomain(
                    "PrecisionGauge", "tag", "confidence", List.of("0.9"), null, null)),
            Set.of(invariantName),
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
    MModel model = USECompiler.compileSpecification(MODEL, "FoldedNavUTypeLet", err, factory);
    err.flush();
    if (model == null) {
      throw new AssertionError("fixture model did not compile");
    }
    return model;
  }
}
