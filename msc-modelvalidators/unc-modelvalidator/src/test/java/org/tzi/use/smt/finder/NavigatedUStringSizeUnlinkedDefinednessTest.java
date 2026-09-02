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
import org.tzi.use.smt.config.AssociationScope;
import org.tzi.use.smt.config.AttributeDomain;
import org.tzi.use.smt.config.ClassScope;
import org.tzi.use.smt.config.ConfigurationVocabulary;
import org.tzi.use.smt.config.InvariantOutcome;
import org.tzi.use.smt.config.QueryParser;
import org.tzi.use.smt.verify.InvariantVerdict;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;

/**
 * SOUNDNESS regression for the NAVIGATED {@code UString.size()} threshold over an UNLINKED
 * receiver.
 *
 * <p>{@code (x.gauge.tag.size() > 2).toBooleanC(0.8)} reads an attribute at the far end of a
 * navigation. When {@code x} links to no Gauge at all there is no {@code tag} to measure, so
 * OCL/USE -- and this project's own U-aware oracle {@code InvariantReEvaluator} -- classify the
 * invariant body as UNDEFINED, hence the invariant itself as UNDEFINED (the implicit context
 * quantifier over an undefined body). The encoding used to fold the per-slot link guard into the
 * VALUE while emitting definedness as the constant {@code true}, so an unlinked receiver came out
 * defined-FALSE: {@code false(uncertain, ...)} was SAT (and only the witness oracle caught it, by
 * throwing), while {@code undef(uncertain, ...)} was UNSAT -- silently wrong, since the UNSAT side
 * has no oracle at all.
 *
 * <p>The association scope {@code R = 0..0} makes "unlinked" the ONLY possible world, so each
 * query below is decided by the classification of the unlinked case alone.
 */
public class NavigatedUStringSizeUnlinkedDefinednessTest {

  private static final String INVARIANT = "Station::NavTagSizeAboveTwo";

  private static final String MODEL =
      """
      model NavUStringSizeUnlinked
      class Station
      attributes
        name : String
      end
      class Gauge
      attributes
        tag : UString
      end
      association R between
        Station [0..*] role station
        Gauge [0..1] role gauge
      end
      constraints
      context x : Station inv NavTagSizeAboveTwo:
        (x.gauge.tag.size() > 2).toBooleanC(0.8)
      """;

  /**
   * The bug, from the UNSAT side: with no link possible the body is UNDEFINED, so {@code undef} is
   * the classification that must be satisfiable. It used to come back UNSAT.
   */
  @Test
  public void anUnlinkedNavigatedSizeThresholdIsUndefined() throws Exception {
    ModelFinderResult undefined = find("undef(uncertain, " + INVARIANT + ")", 0);

    assertTrue("an unlinked receiver has no tag to measure: UNDEFINED", undefined.satisfiable());
    assertEquals(
        InvariantOutcome.UNDEFINED, verdictFor(undefined, INVARIANT).outcome());
  }

  /**
   * The same bug from the SAT side: defined-FALSE is NOT a reachable classification when no link
   * can exist. This used to be SAT and then blow up inside the witness oracle with a
   * {@code WitnessAttributionException} -- the query must simply be UNSAT instead.
   */
  @Test
  public void anUnlinkedNavigatedSizeThresholdIsNotDefinedFalse() throws Exception {
    ModelFinderResult definedFalse = find("false(uncertain, " + INVARIANT + ")", 0);

    assertFalse("undefined is not false: no defined-false witness exists", definedFalse.satisfiable());
  }

  /** The mirror: defined-TRUE is unreachable too, for the same reason. */
  @Test
  public void anUnlinkedNavigatedSizeThresholdIsNotDefinedTrue() throws Exception {
    ModelFinderResult definedTrue = find("true(uncertain, " + INVARIANT + ")", 0);

    assertFalse("undefined is not true either", definedTrue.satisfiable());
  }

  /**
   * The LINKED case is untouched: allow one link and the spelling 'PGX' (length 3) makes the
   * threshold defined-TRUE, while UNDEFINED becomes unreachable because the 1..1 association scope
   * forces the link to exist.
   */
  @Test
  public void aForcedLinkStillClassifiesDefinedTrue() throws Exception {
    ModelFinderResult linked = find("true(uncertain, " + INVARIANT + ")", 1);

    assertTrue("'PGX' is 3 long, above the threshold 2", linked.satisfiable());
    assertEquals(InvariantOutcome.TRUE, verdictFor(linked, INVARIANT).outcome());
  }

  /** ... and with the link forced, the undefined classification is the unreachable one. */
  @Test
  public void aForcedLinkIsNeverUndefined() throws Exception {
    ModelFinderResult undefined = find("undef(uncertain, " + INVARIANT + ")", 1);

    assertFalse("a forced link always has a tag to measure", undefined.satisfiable());
  }

  /**
   * The degenerate end of the same family, found while fixing the above: a destination class scope
   * of {@code 0..0} leaves the navigated end with NO slots, so there is nothing to fold. That used
   * to escape as a raw {@code IndexOutOfBoundsException} from the translator's own
   * {@code slotBlocks.get(size() - 1)} (verified present at HEAD, before the definedness fix), out
   * of a translator that otherwise fails closed with a {@code FragmentBoundary} refusal. Zero
   * capacity means no link can exist, so it is the unlinked case taken to its limit: UNDEFINED.
   */
  @Test
  public void aDestinationEndWithNoSlotsAtAllIsUndefinedRatherThanACrash() throws Exception {
    ModelFinderResult undefined = find("undef(uncertain, " + INVARIANT + ")", 0, 0);

    assertTrue("no Gauge can exist, so no link can: UNDEFINED", undefined.satisfiable());
    assertEquals(InvariantOutcome.UNDEFINED, verdictFor(undefined, INVARIANT).outcome());
  }

  /**
   * @param links the association scope for R, used as BOTH bounds: 0 forbids every link (the
   *     unlinked world), 1 forces exactly one.
   */
  private static ModelFinderResult find(String query, int links) throws Exception {
    return find(query, links, 1);
  }

  private static ModelFinderResult find(String query, int links, int gauges) throws Exception {
    MModel model = compile();
    AnalysisConfiguration config =
        new AnalysisConfiguration(
            List.of(new ClassScope("Station", 1, 1), new ClassScope("Gauge", gauges, gauges)),
            List.of(new AssociationScope("R", links, links)),
            List.of(
                new AttributeDomain("Station", "name", null, List.of("north"), null, null),
                new AttributeDomain("Gauge", "tag", "value", List.of("PGX"), null, null),
                new AttributeDomain("Gauge", "tag", "confidence", List.of("0.9"), null, null)),
            Set.of(INVARIANT),
            QueryParser.parse(query, ConfigurationVocabulary.fromModel(model)),
            Duration.ofSeconds(30),
            1);
    return SmtModelFinder.find(model, config);
  }

  private static InvariantVerdict verdictFor(ModelFinderResult result, String invariantName) {
    return result.verdicts().stream()
        .filter(v -> v.invariantName().equals(invariantName))
        .findFirst()
        .orElseThrow(() -> new AssertionError("no verdict for " + invariantName));
  }

  private static MModel compile() {
    ModelFactory factory = new ModelFactory();
    PrintWriter err = new PrintWriter(System.err);
    MModel model =
        USECompiler.compileSpecification(MODEL, "NavUStringSizeUnlinked", err, factory);
    err.flush();
    if (model == null) {
      throw new AssertionError("fixture model did not compile");
    }
    return model;
  }
}
