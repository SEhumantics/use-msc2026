package org.tzi.use.smt.encode;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.io.PrintWriter;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Test;
import org.tzi.use.parser.use.USECompiler;
import org.tzi.use.smt.config.AssociationScope;
import org.tzi.use.smt.config.ClassScope;
import org.tzi.use.smt.config.TranslationMode;
import org.tzi.use.smt.solver.Smt;
import org.tzi.use.smt.solver.SmtScript;
import org.tzi.use.smt.solver.SmtTerm;
import org.tzi.use.smt.solver.SolverBinary;
import org.tzi.use.smt.solver.SolverOutcome;
import org.tzi.use.smt.solver.SolverProcess;
import org.tzi.use.smt.solver.SolverResult;
import org.tzi.use.uml.mm.MClassInvariant;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;

/**
 * Parser-backed regression coverage for {@code role->closure(role)->excludes(self)} -- the
 * "acyclicity of a single-valued-or-not-repeated navigation" idiom Genealogy's {@code
 * acyclicParenthood} and RecursiveTree's {@code AcyclicParentshipClosure} both use. Deliberately
 * NOT general {@code closure()} support: {@link ExpressionTranslator#closureReachability} refuses
 * anything whose body does not directly re-navigate the exact same association end, and
 * {@code closure()->size()} (Genealogy's own inactive-by-default {@code balancedBinaryTree})
 * remains unimplemented -- both narrowing decisions are deliberate, not oversights.
 */
public class ClosureExcludesTranslationTest {

  private static final String MODEL =
      """
      model AcyclicScope
      class Person
      end
      association Parenthood between
        Person[0..2] role parent
        Person[*] role child
      end
      constraints
      context p : Person inv acyclicParenthood:
        p.parent->closure(parent)->excludes(p)
      """;

  @Test
  public void aDirectLinkWithNoCycleSatisfiesAcyclicity() throws Exception {
    assertHolds(Set.of(edge(0, 1)), 0, true);
  }

  @Test
  public void aTwoHopChainWithNoCycleBackSatisfiesAcyclicity() throws Exception {
    // Confirms multi-hop propagation actually happens, not just the direct 1-hop case.
    assertHolds(Set.of(edge(0, 1), edge(1, 2)), 0, true);
  }

  @Test
  public void aDirectTwoCycleViolatesAcyclicity() throws Exception {
    assertHolds(Set.of(edge(0, 1), edge(1, 0)), 0, false);
  }

  @Test
  public void aThreeCycleViolatesAcyclicity() throws Exception {
    assertHolds(Set.of(edge(0, 1), edge(1, 2), edge(2, 0)), 0, false);
  }

  @Test
  public void aSelfLoopViolatesAcyclicity() throws Exception {
    assertHolds(Set.of(edge(0, 0)), 0, false);
  }

  @Test
  public void excludesOverARangeLiteralCollectionIsRefused() throws Exception {
    String source =
        """
        model NonClosureScope
        class Person
        attributes
          age : Integer
        end
        constraints
        context p : Person inv notInSet:
          Set{1..3}->excludes(p.age)
        """;
    PrintWriter err = new PrintWriter(System.err);
    MModel model = USECompiler.compileSpecification(source, "NonClosureScope", err, new ModelFactory());
    err.flush();
    MClassInvariant inv = findInvariant(model, "notInSet");
    SmtTranslationException thrown =
        assertThrows(
            SmtTranslationException.class,
            () ->
                ExpressionTranslator.translate(
                    invariantBody(inv),
                    new TranslationContext(Map.of(), Map.of(), Map.of(), Map.of(), Map.of()),
                    TranslationMode.UNCERTAIN));
    assertEquals(FragmentBoundary.TIER_3, thrown.boundary());
  }

  private static int[] edge(int parentSlot, int childSlot) {
    return new int[] {parentSlot, childSlot};
  }

  /**
   * Pins the Parenthood link grid to EXACTLY {@code parentEdges} (every other cell forced false),
   * translates {@code acyclicParenthood} for {@code checkSlot}, and asserts the OPPOSITE of {@code
   * expectHolds} is UNSAT -- i.e. the pinned link structure admits only the expected verdict, not
   * merely that translation succeeds.
   */
  private static void assertHolds(Set<int[]> parentEdges, int checkSlot, boolean expectHolds)
      throws Exception {
    PrintWriter err = new PrintWriter(System.err);
    MModel model = USECompiler.compileSpecification(MODEL, "AcyclicScope", err, new ModelFactory());
    err.flush();
    MClassInvariant inv = findInvariant(model, "acyclicParenthood");

    SmtScript script = new SmtScript("QF_LIA");
    ObjectSlots persons =
        ObjectSlotEncoder.encode(script, List.of(new ClassScope("Person", 3, 3))).get("Person");
    AssociationLinks links =
        AssociationLinkEncoder.encode(
            script,
            "Parenthood",
            persons,
            new Multiplicity(0, -1),
            persons,
            new Multiplicity(0, 2),
            new AssociationScope("Parenthood", 0, -1));
    for (int k = 0; k < 3; k++) {
      script.assertThat(Smt.sym("Person_" + k + "_exists"));
    }
    Set<String> forcedTrue = new HashSet<>();
    for (int[] edge : parentEdges) {
      forcedTrue.add(links.linkNames()[edge[0]][edge[1]]);
    }
    for (int i = 0; i < 3; i++) {
      for (int j = 0; j < 3; j++) {
        String cell = links.linkNames()[i][j];
        script.assertThat(forcedTrue.contains(cell) ? Smt.sym(cell) : Smt.not(Smt.sym(cell)));
      }
    }

    TranslationContext context =
        new TranslationContext(
            Map.of("p", new VariableBinding("Person", checkSlot)),
            Map.of(),
            Map.of(),
            Map.of("Person", persons),
            Map.of("Parenthood", links));
    TranslatedExpression translated =
        ExpressionTranslator.translate(invariantBody(inv), context, TranslationMode.UNCERTAIN);
    script.assertThat(expectHolds ? Smt.not(translated.trueTerm()) : translated.trueTerm());

    SolverResult result =
        new SolverProcess(SolverBinary.resolve(), Duration.ofSeconds(30)).run(script.toSmtLib());
    assertEquals(
        "expectHolds=" + expectHolds + " for Person slot " + checkSlot + " under edges "
            + parentEdges.size() + " (asserting the OPPOSITE must be UNSAT)",
        SolverOutcome.UNSAT,
        result.outcome());
  }

  private static org.tzi.use.uml.ocl.expr.Expression invariantBody(MClassInvariant inv) {
    return inv.bodyExpression();
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
