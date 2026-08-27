package org.tzi.use.smt.verify;

import static org.junit.Assert.assertEquals;
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
import org.tzi.use.uml.ocl.value.UStringValue;
import org.tzi.use.uml.sys.MObject;
import org.tzi.use.uml.sys.MSystem;
import org.tzi.use.uncertainty.datatypes.UString;

/**
 * Coverage for the second nominal-erasure arm that had never been reachable.
 *
 * <p>{@code NominalErasureEvaluator} has always carried the proposal's row "{@code UString(s,c)}
 * -&gt; representative spelling {@code s}", and until this family landed that arm was DEAD CODE: no
 * stored UString could exist, because no UString attribute could be configured, encoded or
 * reconstructed. These tests execute it.
 *
 * <p>The erasure is the SPELLING and nothing else -- the confidence is discarded outright, not
 * thresholded at {@code 0.5} the way a stored UBoolean's probability is. That distinction is what
 * makes UString's nominal-erasure discrepancy so blunt: a matching spelling is accepted by the
 * crisp reading at ANY confidence, including one the uncertainty-aware reading rejects. It is the
 * proposal's own worked example -- "{@code UString('ALLY-7',0.70)} compared with exact {@code
 * 'ALLY-7'} yields equality probability (0.70); a required confidence of (0.80) rejects it despite
 * a matching spelling."
 */
public class NominalErasureUStringTest {

  private static final String INVARIANT = "Camera::ConfidentIdentity";

  /**
   * The rule itself, swept across the whole confidence range. The erased verdict depends only on
   * the spelling, so it is constant in the confidence -- which is the claim, not an accident of the
   * sample.
   */
  @Test
  public void erasureKeepsTheSpellingAndDiscardsTheConfidenceEntirely() throws Exception {
    MModel model = compileModel();

    for (double confidence : List.of(0.0, 0.25, 0.49, 0.5, 0.51, 0.7, 1.0)) {
      assertEquals(
          "a matching spelling erases to TRUE at confidence " + confidence,
          InvariantOutcome.TRUE,
          nominalVerdict(model, "ALLY-7", confidence));
      assertEquals(
          "a differing spelling erases to FALSE at confidence " + confidence,
          InvariantOutcome.FALSE,
          nominalVerdict(model, "ALLY-8", confidence));
    }
  }

  /**
   * The erasure and the uncertainty-aware reading disagree exactly where the proposal says they do,
   * measured against USE's own {@code UString} rather than quoted.
   */
  @Test
  public void theTwoReadingsDisagreeAtTheProposalsOwnWorkedExample() throws Exception {
    MModel model = compileModel();

    double probability = new UString("ALLY-7", 0.70).uEquals(new UString("ALLY-7", 1.0)).getC();
    assertEquals(0.70, probability, 0.0);
    assertTrue("the confidence rule rejects", probability < 0.80);
    assertEquals(
        "the erasure accepts anyway, because the spelling matches",
        InvariantOutcome.TRUE,
        nominalVerdict(model, "ALLY-7", 0.70));
  }

  /**
   * The arm reached END TO END, through the solver rather than through a hand-built snapshot, and
   * as the discrepancy query it exists to serve. {@code fragile(Camera::ConfidentIdentity)} asks
   * for a snapshot the erased reading ACCEPTS and the uncertainty-aware reading REJECTS.
   *
   * <p>Two candidate confidences are configured and only ONE of them is fragile: {@code 0.99}
   * clears {@code toBooleanC(0.80)} outright and so satisfies both readings, while {@code 0.70}
   * carries the matching spelling the erasure accepts and a probability the confidence refuses. The
   * solver has to find the fragile one rather than take the easy candidate.
   */
  @Test
  public void aStoredUStringIsFragileEndToEndWhenErasureAcceptsWhatConfidenceRejects()
      throws Exception {
    MModel model = compileModel();

    assertTrue(
        "the U-aware reading is perfectly satisfiable -- 0.99 clears 0.80 -- so a fragile witness"
            + " has to be sought, not stumbled into",
        SmtModelFinder.find(model, configuration(model, "satisfy")).satisfiable());

    ModelFinderResult fragile =
        SmtModelFinder.find(model, configuration(model, "fragile(" + INVARIANT + ")"));

    assertTrue("a fragile witness exists", fragile.satisfiable());
    assertEquals(InvariantOutcome.FALSE, fragile.verdicts().get(0).outcome());
    assertEquals(
        "nominal erasure independently confirms the very same snapshot as TRUE",
        InvariantOutcome.TRUE,
        fragile.nominalVerdicts().get(0).outcome());

    MObject camera =
        fragile.system().state().objectsOfClass(model.getClass("Camera")).iterator().next();
    UStringValue id = (UStringValue) camera.state(fragile.system().state()).attributeValue("id");
    assertEquals(
        "the discrepancy needs the MATCHING spelling -- that is what the erasure reads",
        "ALLY-7",
        id.value());
    assertEquals(
        "0.99 satisfies both readings and is no discrepancy; only 0.70 is fragile",
        0.70,
        id.confidence(),
        0.0);
  }

  // ---------------------------------------------------------------------------------------------

  private static InvariantOutcome nominalVerdict(MModel model, String spelling, double confidence)
      throws Exception {
    Session session = new Session();
    session.setSystem(new MSystem(model));
    UseSystemApi api = UseSystemApi.create(session);
    MObject camera = api.createObjectEx(model.getClass("Camera"), "c1");
    api.setAttributeValueEx(
        camera,
        model.getClass("Camera").attribute("id", true),
        new UStringValue(spelling, confidence));
    return InvariantReEvaluator.reevaluate(
            model, session.system(), TranslationMode.NOMINAL, Set.of(INVARIANT))
        .get(0)
        .outcome();
  }

  private static AnalysisConfiguration configuration(MModel model, String query) {
    return new AnalysisConfiguration(
        List.of(new ClassScope("Camera", 1, 1)),
        List.of(),
        List.of(
            new AttributeDomain("Camera", "id", "value", List.of("ALLY-7", "ALLY-8"), null, null),
            new AttributeDomain("Camera", "id", "confidence", List.of("0.7", "0.99"), null, null)),
        Set.of(INVARIANT),
        QueryParser.parse(query, ConfigurationVocabulary.fromModel(model)),
        Duration.ofSeconds(30),
        1);
  }

  private static MModel compileModel() {
    String source =
        """
        model Camera
        class Camera
        attributes
          id : UString
        end
        constraints
        context self : Camera inv ConfidentIdentity:
          (self.id = 'ALLY-7').toBooleanC(0.80)
        """;
    PrintWriter err = new PrintWriter(System.err);
    MModel model = USECompiler.compileSpecification(source, "Camera", err, new ModelFactory());
    err.flush();
    if (model == null) {
      throw new AssertionError("model did not compile");
    }
    return model;
  }
}
