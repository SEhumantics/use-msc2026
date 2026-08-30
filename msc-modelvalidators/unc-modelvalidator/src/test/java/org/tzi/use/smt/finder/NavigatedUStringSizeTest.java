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
 * End-to-end regression for the NAVIGATED UString size over a FOLDED end view: {@code
 * (x.gauge.tag.size() > 2).toBooleanC(0.8)} where the gauge end's slot view folds a configured
 * subclass. The representative is the per-slot spelling length -- an ite chain over each slot's
 * OWN configured spellings (per-concrete-class domains, concrete-dispatched) -- selected by the
 * slot's link guard, with the slot's confidence symbol as the uncertainty.
 *
 * <p>Gauge carries the spellings {'G','OK'} (lengths 1 and 2) and PrecisionGauge the spelling
 * {'PGX'} (length 3): the folded view offers sizes {1, 2, 3}. Before this slice the size operand
 * resolved only for a bare UString attribute on a context variable; a navigated receiver
 * refused.
 */
public class NavigatedUStringSizeTest {

  private static final String MODEL =
      """
      model NavUStringSize
      class Station
      attributes
        name : String
      end
      class Gauge
      attributes
        tag : UString
      end
      class PrecisionGauge < Gauge
      end
      association R between
        Station [0..*] role station
        Gauge [0..1] role gauge
      end
      constraints
      context x : Station inv NavTagSizeAboveTwo:
        (x.gauge.tag.size() > 2).toBooleanC(0.8)
      context x : Station inv NavTagSizeAboveZero:
        (x.gauge.tag.size() > 0).toBooleanC(0.8)
      context x : Station inv NavTagSizeAboveNine:
        (x.gauge.tag.size() > 9).toBooleanC(0.8)
      """;

  /** Size 1 (spelling 'G') is above 0 through the plain Gauge slot. */
  @Test
  public void thePlainGaugeSlotSatisfiesSizeAboveZero() throws Exception {
    ModelFinderResult result = find("NavTagSizeAboveZero");

    assertTrue(result.satisfiable());
    assertTrue(verdictFor(result, "Station::NavTagSizeAboveZero").holds());
  }

  /** Size 3 exists ONLY on the PrecisionGauge slot: the demand forces the folded-view branch. */
  @Test
  public void sizeAboveTwoForcesThePrecisionGaugeSlot() throws Exception {
    ModelFinderResult result = find("NavTagSizeAboveTwo");

    assertTrue("only 'PGX' on the PrecisionGauge slot is 3 long", result.satisfiable());
    assertTrue(verdictFor(result, "Station::NavTagSizeAboveTwo").holds());
  }

  /** No spelling on any slot is 9 long: genuinely unsatisfiable. */
  @Test
  public void anUnreachableSizeIsUnsatisfiable() throws Exception {
    ModelFinderResult miss = find("NavTagSizeAboveNine");

    assertFalse("'G'(1), 'OK'(2), 'PGX'(3) -- none is 9 long", miss.satisfiable());
  }

  private static ModelFinderResult find(String invariantName) throws Exception {
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
                new AttributeDomain("Gauge", "tag", "value", List.of("G", "OK"), null, null),
                new AttributeDomain("Gauge", "tag", "confidence", List.of("0.7"), null, null),
                new AttributeDomain(
                    "PrecisionGauge", "tag", "value", List.of("PGX"), null, null),
                new AttributeDomain(
                    "PrecisionGauge", "tag", "confidence", List.of("0.9"), null, null)),
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
    MModel model = USECompiler.compileSpecification(MODEL, "NavUStringSize", err, factory);
    err.flush();
    if (model == null) {
      throw new AssertionError("fixture model did not compile");
    }
    return model;
  }
}
