package org.tzi.use.smt.verify;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.PrintWriter;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.junit.Test;
import org.tzi.use.api.UseSystemApi;
import org.tzi.use.main.Session;
import org.tzi.use.parser.use.USECompiler;
import org.tzi.use.smt.config.AnalysisConfiguration;
import org.tzi.use.smt.config.AttributeDomain;
import org.tzi.use.smt.config.ClassScope;
import org.tzi.use.smt.config.ConfigurationVocabulary;
import org.tzi.use.smt.config.InvariantOutcome;
import org.tzi.use.smt.config.QueryParser;
import org.tzi.use.smt.config.TranslationMode;
import org.tzi.use.smt.finder.ModelFinderResult;
import org.tzi.use.smt.finder.SmtModelFinder;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;
import org.tzi.use.uml.ocl.value.UBooleanValue;
import org.tzi.use.uml.sys.MObject;
import org.tzi.use.uml.sys.MSystem;

/**
 * Coverage for the one nominal-erasure rule that had never been reachable.
 *
 * <p>{@code NominalErasureEvaluator} has always carried the proposal's rule "a STORED UBoolean
 * probability uses the {@code p >= 0.5} rule", and until the UBoolean family landed that arm was
 * DEAD CODE: no stored UBoolean could exist, because no UBoolean attribute could be configured,
 * encoded or reconstructed. Every other erasure arm was exercised; this one was carried on trust.
 * These tests execute it.
 *
 * <p>They also settle the {@code p = 0.5} tie by MEASUREMENT rather than by quotation. The proposal
 * says the tie maps to {@code true}, "matching the existing USE {@code toBoolean()} operation", and
 * asks for the strict alternative to be repeated as a sensitivity check. Both are here: USE's own
 * {@code toBoolean()} is asserted at the tie, and the strict alternative is computed alongside so
 * the one verdict the convention actually changes is visible rather than asserted away.
 */
public class NominalErasureUBooleanTest {

  private static final String INVARIANT = "Detection::Signalled";

  /**
   * The rule itself, swept across the tie. {@code 0.49} erases to {@code false}, {@code 0.50} and
   * {@code 0.51} to {@code true} -- and the middle one is the whole question.
   */
  @Test
  public void theStoredProbabilityErasesAtTheHalfLineWithTheTieMappingToTrue() throws Exception {
    MModel model = compileModel();

    assertEquals(InvariantOutcome.FALSE, nominalVerdict(model, 0.49));
    assertEquals(
        "the exact tie maps to TRUE, which is the convention the proposal fixes",
        InvariantOutcome.TRUE,
        nominalVerdict(model, 0.50));
    assertEquals(InvariantOutcome.TRUE, nominalVerdict(model, 0.51));
  }

  /**
   * The convention is USE's, not this project's. Measured against the real operation rather than
   * assumed from the proposal's sentence about it.
   */
  @Test
  public void useSOwnToBooleanAgreesWithTheConventionAtTheTie() {
    assertFalse(UBooleanValue.valueOf(true, 0.49).toBoolean().isTrue());
    assertTrue(
        "USE's UBoolean.toBoolean() is literally (c >= 0.5), so the tie is TRUE there too",
        UBooleanValue.valueOf(true, 0.50).toBoolean().isTrue());
    assertTrue(UBooleanValue.valueOf(true, 0.51).toBoolean().isTrue());
  }

  /**
   * The sensitivity check the proposal asks for, run rather than described: repeat the analysis
   * under the STRICT alternative {@code p > 0.5} and report exactly which verdicts move. Exactly
   * one does -- the tie -- and nothing else in the sweep is convention-dependent.
   */
  @Test
  public void onlyTheExactTieIsSensitiveToTheStrictAlternative() throws Exception {
    MModel model = compileModel();
    List<Double> swept = List.of(0.0, 0.25, 0.49, 0.50, 0.51, 0.75, 1.0);

    List<Double> moved = new java.util.ArrayList<>();
    for (double probability : swept) {
      boolean implemented = nominalVerdict(model, probability) == InvariantOutcome.TRUE;
      boolean strict = probability > 0.5;
      if (implemented != strict) {
        moved.add(probability);
      }
    }
    assertEquals(
        "the implemented p >= 0.5 rule and the strict p > 0.5 alternative may differ at the tie and"
            + " nowhere else",
        List.of(0.50),
        moved);
  }

  /**
   * The arm reached END TO END, through the solver rather than through a hand-built snapshot -- and
   * as the discrepancy query it exists to serve. {@code fragile(Detection::Signalled)} asks for a
   * snapshot the erased reading ACCEPTS and the uncertainty-aware reading REJECTS.
   *
   * <p>Two candidate probabilities are configured and only ONE of them is fragile: {@code 0.99}
   * clears {@code toBooleanC(0.95)} outright, so it satisfies both readings and is no discrepancy
   * at all, while the tie {@code 0.5} erases to a plain {@code true} and is refused by the
   * confidence. The solver has to find the tie rather than take the easy candidate, which is what
   * makes this a test of the erasure arm and not merely of satisfiability.
   */
  @Test
  public void aStoredUBooleanIsFragileEndToEndWhenErasureAcceptsWhatConfidenceRejects()
      throws Exception {
    MModel model = compileModel();

    assertTrue(
        "the U-aware reading is perfectly satisfiable -- 0.99 clears 0.95 -- so a fragile witness"
            + " has to be sought, not stumbled into",
        SmtModelFinder.find(model, configuration(model, "satisfy")).satisfiable());

    ModelFinderResult fragile =
        SmtModelFinder.find(model, configuration(model, "fragile(Detection::Signalled)"));

    assertTrue("a fragile witness exists", fragile.satisfiable());
    assertEquals(InvariantOutcome.FALSE, fragile.verdicts().get(0).outcome());
    assertEquals(
        "nominal erasure independently confirms the very same snapshot as TRUE",
        InvariantOutcome.TRUE,
        fragile.nominalVerdicts().get(0).outcome());
    MObject detection =
        fragile.system().state().objectsOfClass(model.getClass("Detection")).iterator().next();
    UBooleanValue flag =
        (UBooleanValue) detection.state(fragile.system().state()).attributeValue("flag");
    assertEquals(
        "0.99 satisfies both readings and is no discrepancy; only the p = 0.5 tie is fragile, and"
            + " it is fragile precisely BECAUSE the stored-UBoolean erasure rule maps the tie to"
            + " true",
        0.5,
        flag.probability(),
        0.0);
    assertTrue(
        "the discrepancy is the erasure rule's own: p >= 0.5 accepts while p >= 0.95 refuses",
        flag.probability() >= 0.5 && flag.probability() < 0.95);
  }

  // ---------------------------------------------------------------------------------------------

  private static InvariantOutcome nominalVerdict(MModel model, double probability)
      throws Exception {
    Session session = new Session();
    session.setSystem(new MSystem(model));
    UseSystemApi api = UseSystemApi.create(session);
    MObject detection = api.createObjectEx(model.getClass("Detection"), "d1");
    api.setAttributeValueEx(
        detection,
        model.getClass("Detection").attribute("flag", true),
        UBooleanValue.valueOf(true, probability));
    return InvariantReEvaluator.reevaluate(
            model, session.system(), TranslationMode.NOMINAL, Set.of(INVARIANT))
        .get(0)
        .outcome();
  }

  private static AnalysisConfiguration configuration(MModel model, String query) {
    return new AnalysisConfiguration(
        List.of(new ClassScope("Detection", 1, 1)),
        List.of(),
        List.of(
            new AttributeDomain(
                "Detection", "flag", "probability", List.of("0.5", "0.99"), null, null)),
        Set.of(INVARIANT),
        QueryParser.parse(query, ConfigurationVocabulary.fromModel(model)),
        Duration.ofSeconds(30),
        1);
  }

  private static MModel compileModel() {
    String source =
        """
        model Detection
        class Detection
        attributes
          flag : UBoolean
        end
        constraints
        context self : Detection inv Signalled:
          self.flag.toBooleanC(0.95)
        """;
    PrintWriter err = new PrintWriter(System.err);
    MModel model = USECompiler.compileSpecification(source, "Detection", err, new ModelFactory());
    err.flush();
    if (model == null) {
      throw new AssertionError("model did not compile");
    }
    return model;
  }
}
