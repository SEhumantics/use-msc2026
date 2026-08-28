package org.tzi.use.smt.encode;

import static org.junit.Assert.assertEquals;

import java.io.PrintWriter;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.tzi.use.parser.use.USECompiler;
import org.tzi.use.smt.config.AssociationScope;
import org.tzi.use.smt.config.ClassScope;
import org.tzi.use.smt.config.TranslationMode;
import org.tzi.use.smt.solver.SmtScript;
import org.tzi.use.smt.solver.SmtTerm;
import org.tzi.use.uml.mm.MClassInvariant;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;

/**
 * Parser-backed regression coverage for a REFLEXIVE association (both ends the same class, e.g.
 * CivilStatus's {@code Marriage}: {@code Person [0..1] role wife -- Person [0..1] role husband}).
 *
 * <p>{@code ExpressionTranslator.linkTerm}'s ORIGINAL orientation logic resolved which side of the
 * link grid a source binding sat on purely by CLASS NAME -- genuinely ambiguous when both ends
 * share a class. It silently always resolved to the first (aEnd) branch instead of failing closed,
 * so ONE navigation direction (here, {@code .husband}) happened to translate correctly by
 * coincidence, while the OTHER ({@code .wife}) silently used the wrong grid orientation -- a real
 * soundness bug, not a refusal, that would never have surfaced as a test failure on its own. This
 * suite pins the exact SMT-LIB for BOTH directions so a regression back to class-name-only
 * resolution would be caught immediately.
 */
public class ReflexiveAssociationTranslationTest {

  @Test
  public void wifeNavigationChecksTheWifeAxisAgainstTheSourcesOwnHusbandAxisIndex()
      throws Exception {
    // p bound to Person slot 1: "does p (understood as the husband-role occupant) have a linked
    // wife?" must existentially quantify over the WIFE axis (row) at the source's OWN husband-axis
    // (column) index -- Marriage_k_1 for k in 0..2, never Marriage_1_k (that would be the
    // .husband question, checked separately below).
    TranslatedExpression translated = translateInvariant("pHasWife", 1);
    assertEquals("true", translated.defined().toSmtLib());
    assertEquals("(or Marriage_0_1 Marriage_1_1 Marriage_2_1)", translated.value().toSmtLib());
  }

  @Test
  public void husbandNavigationChecksTheHusbandAxisAgainstTheSourcesOwnWifeAxisIndex()
      throws Exception {
    TranslatedExpression translated = translateInvariant("pHasHusband", 1);
    assertEquals("true", translated.defined().toSmtLib());
    assertEquals("(or Marriage_1_0 Marriage_1_1 Marriage_1_2)", translated.value().toSmtLib());
  }

  @Test
  public void aSingleDirectedLinkMakesOnlyTheHusbandsWifeCheckAndTheWifesHusbandCheckHold()
      throws Exception {
    // Pin EXACTLY Marriage_0_1 (wife-index 0, husband-index 1) true, everything else false --
    // the strongest test of orientation: an accidentally-symmetric encoding would make BOTH
    // person0 and person1 see a defined wife/husband; the correct, directed encoding must not.
    assertHolds("pHasWife", 1, true); // person1 (husband) has wife person0
    assertHolds("pHasHusband", 0, true); // person0 (wife) has husband person1
    assertHolds("pHasWife", 0, false); // person0 is not itself a husband
    assertHolds("pHasHusband", 1, false); // person1 is not itself a wife
    assertHolds("pHasWife", 2, false); // person2 is unlinked entirely
    assertHolds("pHasHusband", 2, false);
  }

  /**
   * Builds a fresh script each call (rather than reusing one across checks -- {@link SmtScript}
   * has no copy/undo) with the SAME pinned single link (wife-index 0, husband-index 1, everything
   * else false), asserts the OPPOSITE of {@code expectedHolds} for {@code invariantName} bound to
   * Person slot {@code personSlot}, and confirms that is UNSAT -- i.e. the expected verdict is the
   * only one the pinned link admits.
   */
  private static void assertHolds(String invariantName, int personSlot, boolean expectedHolds)
      throws Exception {
    MModel model = compileFixture();
    MClassInvariant invariant = findInvariant(model, invariantName);
    SmtScript script = new SmtScript("QF_LIA");
    Map<String, ObjectSlots> slots =
        ObjectSlotEncoder.encode(script, List.of(new ClassScope("Person", 3, 3)));
    org.tzi.use.smt.encode.AssociationLinks links =
        AssociationLinkEncoder.encode(
            script,
            "Marriage",
            slots.get("Person"),
            new Multiplicity(0, 1),
            slots.get("Person"),
            new Multiplicity(0, 1),
            new AssociationScope("Marriage", 0, -1));
    for (int i = 0; i < 3; i++) {
      script.assertThat(org.tzi.use.smt.solver.Smt.sym("Person_" + i + "_exists"));
    }
    for (int i = 0; i < 3; i++) {
      for (int j = 0; j < 3; j++) {
        SmtTerm cell = org.tzi.use.smt.solver.Smt.sym(links.linkNames()[i][j]);
        script.assertThat(i == 0 && j == 1 ? cell : org.tzi.use.smt.solver.Smt.not(cell));
      }
    }
    TranslationContext context =
        new TranslationContext(
            Map.of("p", new VariableBinding("Person", personSlot)),
            Map.of(),
            Map.of(),
            slots,
            Map.of("Marriage", links));
    TranslatedExpression translated =
        ExpressionTranslator.translate(
            invariant.bodyExpression(), context, TranslationMode.UNCERTAIN);
    script.assertThat(
        expectedHolds
            ? org.tzi.use.smt.solver.Smt.not(translated.trueTerm())
            : translated.trueTerm());
    org.tzi.use.smt.solver.SolverResult result =
        new org.tzi.use.smt.solver.SolverProcess(
                org.tzi.use.smt.solver.SolverBinary.resolve(), java.time.Duration.ofSeconds(30))
            .run(script.toSmtLib());
    assertEquals(
        invariantName
            + " for Person slot "
            + personSlot
            + ": expected holds="
            + expectedHolds
            + " (asserting the OPPOSITE must be UNSAT)",
        org.tzi.use.smt.solver.SolverOutcome.UNSAT,
        result.outcome());
  }

  private static TranslatedExpression translateInvariant(String invariantName, int personSlot)
      throws Exception {
    MModel model = compileFixture();
    MClassInvariant invariant = findInvariant(model, invariantName);
    SmtScript script = new SmtScript("QF_LIA");
    Map<String, ObjectSlots> slots =
        ObjectSlotEncoder.encode(script, List.of(new ClassScope("Person", 3, 3)));
    org.tzi.use.smt.encode.AssociationLinks links =
        AssociationLinkEncoder.encode(
            script,
            "Marriage",
            slots.get("Person"),
            new Multiplicity(0, 1),
            slots.get("Person"),
            new Multiplicity(0, 1),
            new AssociationScope("Marriage", 0, -1));
    TranslationContext context =
        new TranslationContext(
            Map.of("p", new VariableBinding("Person", personSlot)),
            Map.of(),
            Map.of(),
            slots,
            Map.of("Marriage", links));
    return ExpressionTranslator.translate(invariant.bodyExpression(), context, TranslationMode.UNCERTAIN);
  }

  private static MModel compileFixture() throws Exception {
    String source =
        """
        model MarriageScope
        class Person end
        association Marriage between
          Person [0..1] role wife
          Person [0..1] role husband
        end
        constraints
        context p : Person inv pHasWife:
          p.wife.isDefined
        context p : Person inv pHasHusband:
          p.husband.isDefined
        """;
    PrintWriter err = new PrintWriter(System.err);
    MModel model =
        USECompiler.compileSpecification(source, "MarriageScope", err, new ModelFactory());
    err.flush();
    if (model == null) {
      throw new AssertionError("MarriageScope fixture model did not compile");
    }
    return model;
  }

  private static MClassInvariant findInvariant(MModel model, String name) {
    for (MClassInvariant invariant : model.classInvariants()) {
      if (invariant.name().equals(name)) {
        return invariant;
      }
    }
    throw new IllegalStateException("invariant not found: " + name);
  }
}
