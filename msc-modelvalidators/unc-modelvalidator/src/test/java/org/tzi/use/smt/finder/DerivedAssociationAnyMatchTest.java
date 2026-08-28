package org.tzi.use.smt.finder;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;
import org.tzi.use.parser.use.USECompiler;
import org.tzi.use.smt.config.AnalysisConfiguration;
import org.tzi.use.smt.config.ConfigurationReader;
import org.tzi.use.smt.config.ConfigurationVocabulary;
import org.tzi.use.smt.config.RawConfiguration;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;

/**
 * A derived association end whose derivation expression is exactly {@code
 * T.allInstances()->any(x | predicate)} -- the "foreign-key lookup" idiom {@code
 * CompanyERSchema.CompanyER.use} builds all twelve of its own associations around -- is now
 * constrained (not refused) via {@link org.tzi.use.smt.encode.DerivedAssociationEncoder}, found
 * while scoping T1 work: refusing every `derived` end unconditionally was correct when the whole
 * association-scope loop had no way to represent one, but this exact shape is the same
 * finite-disjunction selection {@code ExpressionTranslator#objectAnyLet} already implements and
 * tests for the analogous `let x = T.allInstances()->any(pred) in body` case.
 *
 * <p>Hand-built fixture (a smaller, standalone model, not the real corpus one -- CompanyERSchema
 * itself still ERRORs on an unrelated, pre-existing config gap, {@code Component.containedname}
 * having no registered value domain, confirmed live and NOT claimed as fixed by this feature).
 * Every test here goes through the FULL {@link SmtModelFinder#find} pipeline (not a hand-rolled
 * translation context), so encoding, reconstruction, AND {@code InvariantReEvaluator}'s real USE-
 * evaluator re-check are all exercised together -- if {@code SystemStateReconstructor}'s new
 * derived-association skip were wrong, or the derived grid's content disagreed with what USE's
 * own {@code DerivedLinkControllerDerivedEnd} independently recomputes, the evaluator re-check
 * would catch it, not just this translator's own opinion of itself.
 */
public class DerivedAssociationAnyMatchTest {

  @Test
  public void derivedEndResolvesToTheMatchingCandidateConfirmedByUseEvaluator() throws Exception {
    MModel model = compileFixture();
    AnalysisConfiguration config =
        readConfig(
            model,
            """
            Widget_min = 2
            Widget_max = 2
            Widget_wname = Set{'alpha', 'beta'}
            Gadget_min = 1
            Gadget_max = 1
            Gadget_targetname = Set{'beta'}
            Gadget_WidgetNameMatches = active
            Gadget_NoMatchIsUndefined = inactive
            """);

    ModelFinderResult result = SmtModelFinder.find(model, config);

    // verdictFor-by-name, not allActiveInvariantsHold(): result.verdicts() reports EVERY declared
    // invariant regardless of active status (the same trap EnumTranslationTest's own fullRoundTrip
    // test documents), and NoMatchIsUndefined is genuinely, correctly FALSE in this scenario (a
    // match DOES exist) even though it is marked inactive here.
    assertTrue("expected SAT -- a Widget named 'beta' exists to match", result.satisfiable());
    assertTrue(
        "the derived link must resolve to the Widget whose wname genuinely equals targetname,"
            + " confirmed by USE's own evaluator re-checking the reconstructed state -- not just"
            + " this translator's own opinion",
        verdictFor(result, "Gadget::WidgetNameMatches").holds());
  }

  @Test
  public void noMatchingCandidateLeavesTheDerivedEndUndefinedConfirmedByUseEvaluator()
      throws Exception {
    MModel model = compileFixture();
    AnalysisConfiguration config =
        readConfig(
            model,
            """
            Widget_min = 2
            Widget_max = 2
            Widget_wname = Set{'alpha', 'beta'}
            Gadget_min = 1
            Gadget_max = 1
            Gadget_targetname = Set{'gamma'}
            Gadget_WidgetNameMatches = inactive
            Gadget_NoMatchIsUndefined = active
            """);

    ModelFinderResult result = SmtModelFinder.find(model, config);

    assertTrue("expected SAT -- no Widget named 'gamma' exists, but that is not a contradiction",
        result.satisfiable());
    assertTrue(
        "the derived end must genuinely be undefined when no candidate matches, confirmed by"
            + " USE's own evaluator",
        verdictFor(result, "Gadget::NoMatchIsUndefined").holds());
  }

  @Test
  public void twoGadgetsWithDifferentTargetsResolveToDifferentWidgetsGenuinelyNotCoincidentally()
      throws Exception {
    // A real discriminating case: if the derived grid were accidentally satisfied by BOTH
    // gadgets resolving to the SAME widget (a translation bug that would still let each
    // individual match-check pass), this invariant -- which requires the two resolved widgets to
    // be DIFFERENT objects -- would genuinely fail to hold, and USE's own evaluator would catch
    // it even if this translator's own reasoning about its formula did not.
    MModel model = compileTwoGadgetFixture();
    AnalysisConfiguration config =
        readConfig(
            model,
            """
            Widget_min = 2
            Widget_max = 2
            Widget_wname = Set{'alpha', 'beta'}
            Gadget_min = 2
            Gadget_max = 2
            Gadget_targetname = Set{'alpha', 'beta'}
            Gadget_ResolvedWidgetsAreDistinct = active
            """);

    ModelFinderResult result = SmtModelFinder.find(model, config);

    assertTrue("expected SAT", result.satisfiable());
    assertTrue(
        "the two gadgets' distinct targetnames must resolve to genuinely distinct widgets,"
            + " confirmed by USE's own evaluator -- not merely two independently-plausible"
            + " single-cell checks",
        result.allActiveInvariantsHold());
  }

  /**
   * Regression for a genuine soundness bug found while probing a candidate corpus scenario for
   * this same encoder: the {@code any()} selection formula's own predicate ({@code w.wname =
   * self.targetname}, a bare String-attribute-to-bare-String-attribute equality) used to compare
   * raw per-attribute-local domain INDICES rather than actual string content ({@code
   * ExpressionTranslator#crossDomainStringOrEnumEquality} is the fix). Neither existing test above
   * combines "no Widget matches any configured targetname" with "{@code WidgetNameMatches} is
   * ACTIVE" -- the first test only asserts the invariant when a match genuinely exists, the second
   * only asserts {@code NoMatchIsUndefined} (a different invariant) when none does. That untested
   * combination is exactly where the bug surfaced: before the fix, this configuration made the
   * solver claim a match existed (index collision between {@code Widget_wname}'s domain and {@code
   * Gadget_targetname}'s domain) that {@code InvariantReEvaluator}'s real USE evaluator then denied,
   * crashing with {@code WitnessAttributionException} instead of either finding a correct witness or
   * refusing cleanly. With no Widget named 'gamma', {@code g.widget} is genuinely undefined for
   * every Gadget, so the active equality can never hold -- the correct answer is a clean
   * UNSATISFIABLE, not a crash.
   */
  @Test
  public void activeWidgetNameMatchesWithNoMatchingCandidateIsGenuinelyUnsatisfiable()
      throws Exception {
    MModel model = compileFixture();
    AnalysisConfiguration config =
        readConfig(
            model,
            """
            Widget_min = 2
            Widget_max = 2
            Widget_wname = Set{'alpha', 'beta'}
            Gadget_min = 1
            Gadget_max = 1
            Gadget_targetname = Set{'gamma'}
            Gadget_WidgetNameMatches = active
            Gadget_NoMatchIsUndefined = inactive
            """);

    ModelFinderResult result = SmtModelFinder.find(model, config);

    assertFalse(
        "no Widget named 'gamma' exists, so the active WidgetNameMatches constraint is genuinely"
            + " unsatisfiable -- not a translation crash",
        result.satisfiable());
  }

  @Test
  public void deriveExpressionOutsideTheAnyMatchShapeStillRefusesCleanly() throws Exception {
    // select(...) instead of any(...) -- the encoder must return empty and fall through to the
    // existing refusal, not silently misinterpret a different shape as if it were this one.
    MModel model =
        compileModel(
            """
            model DerivedSelectScope
            class Widget
            attributes
              wname : String
            end
            class Gadget
            attributes
              targetname : String
            end
            association FK_Gadget_Widget between
              Gadget [*] role gadget
              Widget [*] role widgets derived =
                Widget.allInstances()->select(w | w.wname = self.targetname)
            end
            constraints
            context g : Gadget inv Dummy:
              true
            """,
            "DerivedSelectScope");
    AnalysisConfiguration config =
        readConfig(
            model,
            """
            Widget_min = 1
            Widget_max = 1
            Widget_wname = Set{'alpha'}
            Gadget_min = 1
            Gadget_max = 1
            Gadget_targetname = Set{'alpha'}
            """);

    org.tzi.use.smt.encode.SmtTranslationException thrown =
        org.junit.Assert.assertThrows(
            org.tzi.use.smt.encode.SmtTranslationException.class,
            () -> SmtModelFinder.find(model, config));
    assertTrue(thrown.getMessage(), thrown.getMessage().contains("declares a 'derived' end"));
  }

  private static org.tzi.use.smt.verify.InvariantVerdict verdictFor(
      ModelFinderResult result, String invariantName) {
    return result.verdicts().stream()
        .filter(v -> v.invariantName().equals(invariantName))
        .findFirst()
        .orElseThrow(() -> new AssertionError("no verdict for " + invariantName));
  }

  private static AnalysisConfiguration readConfig(MModel model, String body) throws Exception {
    Path file = Files.createTempFile("derived-fk", ".properties");
    file.toFile().deleteOnExit();
    Files.writeString(file, body);
    RawConfiguration raw = ConfigurationReader.read(file, null);
    return ConfigurationReader.normalize(raw, ConfigurationVocabulary.fromModel(model))
        .requireSupported();
  }

  private static MModel compileFixture() throws Exception {
    return compileModel(
        """
        model DerivedFKScope
        class Widget
        attributes
          wname : String
        end
        class Gadget
        attributes
          targetname : String
        end
        association FK_Gadget_Widget between
          Gadget [*] role gadget
          Widget [1] role widget derived =
            Widget.allInstances()->any(w | w.wname = self.targetname)
        end
        constraints
        context g : Gadget inv WidgetNameMatches:
          g.widget.wname = g.targetname
        context g : Gadget inv NoMatchIsUndefined:
          not g.widget.isDefined
        """,
        "DerivedFKScope");
  }

  private static MModel compileTwoGadgetFixture() throws Exception {
    return compileModel(
        """
        model DerivedFKTwoGadgetScope
        class Widget
        attributes
          wname : String
        end
        class Gadget
        attributes
          targetname : String
        end
        association FK_Gadget_Widget between
          Gadget [*] role gadget
          Widget [1] role widget derived =
            Widget.allInstances()->any(w | w.wname = self.targetname)
        end
        constraints
        context g1, g2 : Gadget inv ResolvedWidgetsAreDistinct:
          g1 <> g2 implies g1.widget <> g2.widget
        """,
        "DerivedFKTwoGadgetScope");
  }

  private static MModel compileModel(String source, String name) throws Exception {
    PrintWriter err = new PrintWriter(System.err);
    MModel model = USECompiler.compileSpecification(source, name, err, new ModelFactory());
    err.flush();
    if (model == null) {
      throw new AssertionError(name + " fixture model did not compile:\n" + source);
    }
    return model;
  }
}
