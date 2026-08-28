package org.tzi.use.smt.encode;

import static org.junit.Assert.assertEquals;

import java.io.PrintWriter;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.tzi.use.parser.use.USECompiler;
import org.tzi.use.smt.config.AssociationScope;
import org.tzi.use.smt.config.AttributeDomain;
import org.tzi.use.smt.config.ClassScope;
import org.tzi.use.smt.solver.Smt;
import org.tzi.use.smt.solver.SmtScript;
import org.tzi.use.smt.solver.SmtTerm;
import org.tzi.use.smt.solver.SolverBinary;
import org.tzi.use.smt.solver.SolverOutcome;
import org.tzi.use.smt.solver.SolverProcess;
import org.tzi.use.uml.mm.MClassInvariant;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;

/**
 * {@link ExpressionTranslator#resolveRedefinedDestination} was originally wired into ONLY {@link
 * ExpressionTranslator#populationOf} (the {@code Redefines.use} corpus shape, {@code
 * c.b->forAll(...)}, is collection-valued throughout). {@link
 * ExpressionTranslator#singleValuedNavigationDefined}, {@link
 * ExpressionTranslator#navigationEqualsVariable}, and {@link
 * ExpressionTranslator#navigatedAttribute} stayed unredirected -- an honest, documented gap, not a
 * silent unsoundness: a subclass-typed source hitting one of them threw {@code "association ...
 * does not connect class ..."} (the redefined association's grid genuinely has no row/column for
 * the subclass), rather than reading the correct redefining grid. Closed here by applying the SAME
 * redirect those three single-valued-navigation call sites too, verified with a REFLEXIVE
 * redefines shape ({@code SpecialNode < Node}, both {@code NodeLink} and its redefining {@code
 * SpecialLink} navigate the SAME class each side) so all three now-fixed shapes are actually
 * reachable ({@code Redefines.use} itself only has {@code [*]} (collection) roles).
 *
 * <p>Every test deliberately omits {@code NodeLink} from the hand-built {@link TranslationContext}
 * entirely -- if the redirect ever regresses, {@code destination.association().name()} resolves to
 * {@code "NodeLink"} and {@link TranslationContext#linksFor} throws immediately (no grid registered
 * for it), so a regression fails loudly instead of silently reading the wrong grid.
 */
public class RedefinedSingleValuedNavigationTest {

  @Test
  public void nextIsDefinedRedirectsThroughSpecialLinkForASpecialNodeSource() throws Exception {
    // One pinned link, sprev-axis (row) 0 -> snext-axis (col) 1: slot 0 has a next, slot 1 does
    // not -- mirrors ReflexiveAssociationTranslationTest's own single-directed-link convention.
    assertHolds("NextIsDefined", 0, true);
    assertHolds("NextIsDefined", 1, false);
  }

  @Test
  public void notSelfNextDistinguishesASelfLoopFromNoLinkAtAllThroughSpecialLink() throws Exception {
    // Pin a SELF-loop (slot 0 -> slot 0): NotSelfNext must fail for slot 0 (next IS self) and hold
    // for slot 1 (no link at all, so "next" is undefined, and undefined <> a defined object is
    // true under USE's own total-equality semantics).
    assertSelfLoopHolds(0, false);
    assertSelfLoopHolds(1, true);
  }

  @Test
  public void nextTagIsChildReadsTheTargetsAttributeThroughTheRedirectedGrid() throws Exception {
    // A STRING literal comparison after a navigation hop would hit an unrelated, pre-existing
    // limitation in resolve(ExpConstString,...) (it only resolves a bare-variable receiver's own
    // attribute domain index, unlike navigatedAttribute itself) -- so this uses an Integer
    // attribute instead, exercising navigatedAttribute's own redirect via the generic
    // argResult/useEquality fallback path, exactly like the redefines fix is meant to cover.
    assertTagHolds(42, true);
    assertTagHolds(7, false);
  }

  /**
   * Shared bind-then-solve helper for the {@code NextIsDefined} pair: one pinned link (slot 0's
   * {@code snext} axis -> slot 1), asserts the OPPOSITE of {@code expectedHolds} is UNSAT.
   */
  private static void assertHolds(String invariantName, int specialNodeSlot, boolean expectedHolds)
      throws Exception {
    Fixture fx = buildFixture();
    MClassInvariant inv = findInvariant(fx.model, invariantName);
    TranslationContext ctx =
        new TranslationContext(
            Map.of("s", new VariableBinding("SpecialNode", specialNodeSlot)),
            Map.of(),
            Map.of(),
            Map.of("SpecialNode", fx.specialNodes),
            Map.of("SpecialLink", fx.specialLink));
    SmtTerm translated = ExpressionTranslator.translate(inv.bodyExpression(), ctx);

    fx.script.assertThat(Smt.sym("SpecialNode_0_exists"));
    fx.script.assertThat(Smt.sym("SpecialNode_1_exists"));
    pinOneLink(fx.script, fx.specialLink, 0, 1);
    fx.script.assertThat(expectedHolds ? Smt.not(translated) : translated);

    assertEquals(
        invariantName + " for SpecialNode slot " + specialNodeSlot + ", expected holds=" + expectedHolds,
        SolverOutcome.UNSAT,
        solve(fx.script).outcome());
  }

  private static void assertSelfLoopHolds(int specialNodeSlot, boolean expectedHolds) throws Exception {
    Fixture fx = buildFixture();
    MClassInvariant inv = findInvariant(fx.model, "NotSelfNext");
    TranslationContext ctx =
        new TranslationContext(
            Map.of("s", new VariableBinding("SpecialNode", specialNodeSlot)),
            Map.of(),
            Map.of(),
            Map.of("SpecialNode", fx.specialNodes),
            Map.of("SpecialLink", fx.specialLink));
    SmtTerm translated = ExpressionTranslator.translate(inv.bodyExpression(), ctx);

    fx.script.assertThat(Smt.sym("SpecialNode_0_exists"));
    fx.script.assertThat(Smt.sym("SpecialNode_1_exists"));
    pinOneLink(fx.script, fx.specialLink, 0, 0); // slot 0 links to itself; slot 1 links nowhere
    fx.script.assertThat(expectedHolds ? Smt.not(translated) : translated);

    assertEquals(
        "NotSelfNext for SpecialNode slot " + specialNodeSlot + ", expected holds=" + expectedHolds,
        SolverOutcome.UNSAT,
        solve(fx.script).outcome());
  }

  private static void assertTagHolds(int tagValue, boolean expectedHolds) throws Exception {
    Fixture fx = buildFixture();
    MClassInvariant inv = findInvariant(fx.model, "NextTagIsChild");
    AttributeDomain tagDomain = new AttributeDomain("SpecialNode", "tagSpecial", null, List.of(), null, null);
    AttributeValues tagValues =
        AttributeEncoder.encode(fx.script, fx.specialNodes, "tagSpecial", AttributeType.INTEGER, tagDomain);
    TranslationContext ctx =
        new TranslationContext(
            Map.of("s", new VariableBinding("SpecialNode", 0)),
            Map.of("SpecialNode.tagSpecial", tagValues),
            Map.of("SpecialNode.tagSpecial", tagDomain),
            Map.of("SpecialNode", fx.specialNodes),
            Map.of("SpecialLink", fx.specialLink));
    SmtTerm translated = ExpressionTranslator.translate(inv.bodyExpression(), ctx);

    fx.script.assertThat(Smt.sym("SpecialNode_0_exists"));
    fx.script.assertThat(Smt.sym("SpecialNode_1_exists"));
    pinOneLink(fx.script, fx.specialLink, 0, 1); // slot 0 -> slot 1
    fx.script.assertThat(
        Smt.eq(
            Smt.sym(tagValues.valueNames().get(1)),
            Smt.intLit(java.math.BigInteger.valueOf(tagValue))));
    fx.script.assertThat(expectedHolds ? Smt.not(translated) : translated);

    assertEquals(
        "NextTagIsChild with slot 1 tagSpecial=" + tagValue + ", expected holds=" + expectedHolds,
        SolverOutcome.UNSAT,
        solve(fx.script).outcome());
  }

  /** Pins exactly one directed cell true and every other cell of the 2x2 grid false. */
  private static void pinOneLink(SmtScript script, AssociationLinks links, int from, int to) {
    for (int i = 0; i < 2; i++) {
      for (int j = 0; j < 2; j++) {
        SmtTerm cell = Smt.sym(links.linkNames()[i][j]);
        script.assertThat(i == from && j == to ? cell : Smt.not(cell));
      }
    }
  }

  private record Fixture(
      MModel model, SmtScript script, ObjectSlots specialNodes, AssociationLinks specialLink) {}

  private static Fixture buildFixture() throws Exception {
    MModel model = compileFixture();
    SmtScript script = new SmtScript("QF_LIA");
    ObjectSlots specialNodes =
        ObjectSlotEncoder.encode(script, List.of(new ClassScope("SpecialNode", 2, 2)))
            .get("SpecialNode");
    AssociationLinks specialLink =
        AssociationLinkEncoder.encode(
            script,
            "SpecialLink",
            specialNodes,
            new Multiplicity(0, 1),
            specialNodes,
            new Multiplicity(0, 1),
            new AssociationScope("SpecialLink", 0, -1));
    return new Fixture(model, script, specialNodes, specialLink);
  }

  private static MModel compileFixture() throws Exception {
    String source =
        """
        model RedefinedSingleValuedScope
        class Node end
        class SpecialNode < Node
        attributes
          tagSpecial : Integer
        end
        association NodeLink between
          Node[0..1] role prev
          Node[0..1] role next
        end
        association SpecialLink between
          SpecialNode[0..1] role sprev redefines prev
          SpecialNode[0..1] role snext redefines next
        end
        constraints
        context s: SpecialNode inv NextIsDefined:
          s.next.isDefined
        context s: SpecialNode inv NotSelfNext:
          s.next <> s
        context s: SpecialNode inv NextTagIsChild:
          s.next.tagSpecial = 42
        """;
    PrintWriter err = new PrintWriter(System.err);
    MModel model =
        USECompiler.compileSpecification(source, "RedefinedSingleValuedScope", err, new ModelFactory());
    err.flush();
    if (model == null) {
      throw new AssertionError("RedefinedSingleValuedScope fixture model did not compile");
    }
    return model;
  }

  private static MClassInvariant findInvariant(MModel model, String name) {
    for (MClassInvariant inv : model.classInvariants()) {
      if (inv.name().equals(name)) {
        return inv;
      }
    }
    throw new IllegalStateException("invariant not found: " + name);
  }

  private static org.tzi.use.smt.solver.SolverResult solve(SmtScript script) {
    return new SolverProcess(SolverBinary.resolve(), Duration.ofSeconds(30)).run(script.toSmtLib());
  }
}
