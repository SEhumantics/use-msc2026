package org.tzi.use.smt.encode;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
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
import org.tzi.use.smt.finder.ModelFinderResult;
import org.tzi.use.smt.finder.SmtModelFinder;
import org.tzi.use.smt.solver.SolverOutcome;
import org.tzi.use.smt.verify.InvariantVerdict;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;

/**
 * {@code X->reject(pred)} -- {@code X->reject(pred)} is exactly {@code X->select(not pred)}, so
 * {@link ExpressionTranslator#selectedAllInstancesPopulation} was generalized to build both from
 * the SAME {@code ExpQuery}-typed method rather than a second, independently-derived one, matching
 * {@code select()}'s own two supported source shapes (bare {@code X.allInstances()} and the
 * {@code excluding()}-wrapped primary-key idiom) and every construct that already consumes {@code
 * select()} ({@code forAll}/{@code exists}/{@code isUnique}/{@code size()}/{@code includesAll}/
 * {@code isEmpty}/{@code notEmpty}).
 *
 * <p>The one genuine semantic difference from a plain negation: an UNDEFINED predicate must stay
 * excluded from a {@code reject()} result too (Kleene negation preserves undefinedness -- {@code
 * not undefined} is still {@code undefined}, not {@code true}), so member inclusion uses {@link
 * TranslatedExpression#falseTerm()} (genuinely, definitely false), never a bare {@code
 * Smt.not(predicate.value())}.
 *
 * <p>Every discriminating test here marks the reject()-based invariant under test ACTIVE, not
 * inactive -- discovered while first authoring this file that {@code SmtModelFinder} only ever
 * hands an ACTIVE invariant to {@code ExpressionTranslator} at all (via {@code
 * QueryRequirements.requiredClassifications(config.query(), config.activeInvariants())}); an
 * INACTIVE invariant's {@code verdicts()} entry comes entirely from {@code
 * InvariantReEvaluator}'s NATIVE USE evaluation of the reconstructed witness, which never touches
 * this translator's own reject() encoding at all. An inactive-only test would silently verify
 * nothing about the encoder -- confirmed directly by adversarially reverting the encoder change
 * with the invariant left inactive and observing zero effect on any assertion. Each test therefore
 * pairs a deterministic Boolean-forcing invariant with the ACTIVE reject()-based invariant, so a
 * wrong encoding (e.g. reject() silently behaving like select()) manufactures a genuine, provable
 * contradiction -- UNSAT where SAT is expected, or vice versa -- rather than a value merely
 * observed after the fact.
 */
public class RejectTranslationTest {

  @Test
  public void sizeOfARejectFilteredAllInstancesDiscriminatesOnHowManyMatchThePredicate()
      throws Exception {
    MModel model = compileFixture();

    // flag=true for all 3 -> reject(flag) keeps none -> size()=0 is TRUE -> consistent -> SAT.
    assertEquals(
        SolverOutcome.SAT,
        outcome(model, readConfig(model, 3, "forceAllTrue", "rejectedCountIsZero")));

    // flag=false for all 3 -> reject(flag) keeps all 3 -> size()=0 is FALSE -> genuine
    // contradiction with the ACTIVE rejectedCountIsZero constraint -> UNSAT.
    assertEquals(
        SolverOutcome.UNSAT,
        outcome(model, readConfig(model, 3, "forceAllFalse", "rejectedCountIsZero")));
  }

  @Test
  public void isEmptyOverARejectFilteredAllInstancesDiscriminatesOnWhetherAnyMemberIsKept()
      throws Exception {
    MModel model = compileFixture();

    // flag=true for both -> reject(flag) keeps none -> isEmpty() is TRUE -> consistent -> SAT.
    assertEquals(
        SolverOutcome.SAT,
        outcome(model, readConfig(model, 2, "forceAllTrue", "rejectedIsEmpty")));

    // flag=false for both -> reject(flag) keeps both -> isEmpty() is FALSE -> genuine
    // contradiction with the ACTIVE rejectedIsEmpty constraint -> UNSAT.
    assertEquals(
        SolverOutcome.UNSAT,
        outcome(model, readConfig(model, 2, "forceAllFalse", "rejectedIsEmpty")));
  }

  /**
   * The excluding()-wrapped primary-key idiom's own sibling shape: {@code
   * X.allInstances()->excluding(self)->reject(pred)}, confirming the excluded slot is STILL
   * excluded under reject() the same way it is under select() -- not merely inverted into being
   * force-included.
   */
  @Test
  public void rejectOverAnExcludingWrappedSourceStillExcludesTheExcludedSlot() throws Exception {
    MModel model = compileFixture();

    // flag=false for both -> excluding(self) leaves the OTHER item, which reject(flag) keeps
    // (flag=false) -> notEmpty() is TRUE -> consistent -> SAT.
    assertEquals(
        SolverOutcome.SAT,
        outcome(model, readConfig(model, 2, "forceAllFalse", "otherItemsRejectedNotEmpty")));

    // flag=true for both -> the OTHER item is rejected (flag=true), leaving reject() empty ->
    // notEmpty() is FALSE -> genuine contradiction with the ACTIVE constraint -> UNSAT.
    assertEquals(
        SolverOutcome.UNSAT,
        outcome(model, readConfig(model, 2, "forceAllTrue", "otherItemsRejectedNotEmpty")));
  }

  /**
   * A positive control proving the ACTIVE reject() constraint is genuinely SATISFIABLE end to end
   * (translation, solving, USE-evaluator reconstruction confirmation) when consistent, not just
   * "not UNSAT" -- {@link #outcome} alone cannot distinguish a correctly-encoded SAT from an
   * accidentally-vacuous one.
   */
  @Test
  public void rejectedCountIsZeroIsReconstructedAndConfirmedByUsesOwnEvaluatorWhenConsistent()
      throws Exception {
    MModel model = compileFixture();
    AnalysisConfiguration config = readConfig(model, 3, "forceAllTrue", "rejectedCountIsZero");
    ModelFinderResult result = SmtModelFinder.find(model, config);
    assertTrue("expected SAT", result.satisfiable());
    // verdictFor by name, not allActiveInvariantsHold(): that checks EVERY declared invariant
    // regardless of active status, and forceAllFalse ("not i.flag") is genuinely false here on
    // purpose (flag is forced TRUE), which is not a translation defect to catch.
    assertTrue(
        "the active forcing invariant must be confirmed by USE's own evaluator",
        verdictFor(result, "Item::forceAllTrue").holds());
    assertTrue(
        "the active reject()-based invariant must be confirmed by USE's own evaluator over the"
            + " reconstructed witness",
        verdictFor(result, "Item::rejectedCountIsZero").holds());
  }

  @Test
  public void bareRejectStillFailsClosed() throws Exception {
    MModel model = compileBareRejectFixture();
    Path file = Files.createTempFile("reject-bare", ".properties");
    file.toFile().deleteOnExit();
    Files.writeString(file, "Item_min = 1\nItem_max = 1\nItem_bareReject = active\n");
    RawConfiguration raw = ConfigurationReader.read(file, null);
    AnalysisConfiguration config =
        ConfigurationReader.normalize(raw, ConfigurationVocabulary.fromModel(model))
            .requireSupported();

    SmtTranslationException thrown =
        assertThrows(SmtTranslationException.class, () -> SmtModelFinder.find(model, config));
    assertTrue(thrown.getMessage(), thrown.getMessage().contains("reject"));
  }

  private static InvariantVerdict verdictFor(ModelFinderResult result, String invariantName) {
    return result.verdicts().stream()
        .filter(v -> v.invariantName().equals(invariantName))
        .findFirst()
        .orElseThrow(() -> new AssertionError("no verdict for " + invariantName));
  }

  private static SolverOutcome outcome(MModel model, AnalysisConfiguration config) throws Exception {
    ModelFinderResult result = SmtModelFinder.find(model, config);
    return result.satisfiable() ? SolverOutcome.SAT : SolverOutcome.UNSAT;
  }

  /**
   * Activates exactly the forcing invariant ({@code forceAllTrue}/{@code forceAllFalse}) AND the
   * one invariant under test, together, as hard search constraints -- marking every other fixture
   * invariant inactive. Both must be active simultaneously: only then does an inconsistency
   * between them (a wrongly-encoded reject()) manufacture a genuine, provable UNSAT.
   */
  private static AnalysisConfiguration readConfig(
      MModel model, int itemCount, String forcer, String underTest) throws Exception {
    String[] all = {
      "forceAllTrue", "forceAllFalse", "rejectedCountIsZero", "rejectedIsEmpty",
      "otherItemsRejectedNotEmpty"
    };
    StringBuilder body = new StringBuilder();
    body.append("Item_min = ").append(itemCount).append('\n');
    body.append("Item_max = ").append(itemCount).append('\n');
    for (String inv : all) {
      boolean active = inv.equals(forcer) || inv.equals(underTest);
      body.append("Item_").append(inv).append(" = ").append(active ? "active" : "inactive").append('\n');
    }
    Path file = Files.createTempFile("reject", ".properties");
    file.toFile().deleteOnExit();
    Files.writeString(file, body.toString());
    RawConfiguration raw = ConfigurationReader.read(file, null);
    return ConfigurationReader.normalize(raw, ConfigurationVocabulary.fromModel(model))
        .requireSupported();
  }

  private static MModel compileFixture() throws Exception {
    String source =
        """
        model RejectScope
        class Item
        attributes
          flag : Boolean
        end
        constraints
        context i : Item inv forceAllTrue:
          i.flag
        context i : Item inv forceAllFalse:
          not i.flag
        context i : Item inv rejectedCountIsZero:
          Item.allInstances()->reject(flag)->size() = 0
        context i : Item inv rejectedIsEmpty:
          Item.allInstances()->reject(flag)->isEmpty()
        context i : Item inv otherItemsRejectedNotEmpty:
          Item.allInstances()->excluding(i)->reject(j | j.flag)->notEmpty()
        """;
    PrintWriter err = new PrintWriter(System.err);
    MModel model = USECompiler.compileSpecification(source, "RejectScope", err, new ModelFactory());
    err.flush();
    if (model == null) {
      throw new AssertionError("RejectScope fixture model did not compile");
    }
    return model;
  }

  private static MModel compileBareRejectFixture() throws Exception {
    String source =
        """
        model BareRejectScope
        class Item
        attributes
          flag : Boolean
        end
        constraints
        context i : Item inv bareReject:
          Item.allInstances()->reject(flag) = Item.allInstances()->reject(flag)
        """;
    PrintWriter err = new PrintWriter(System.err);
    MModel model =
        USECompiler.compileSpecification(source, "BareRejectScope", err, new ModelFactory());
    err.flush();
    if (model == null) {
      throw new AssertionError("BareRejectScope fixture model did not compile");
    }
    return model;
  }
}
