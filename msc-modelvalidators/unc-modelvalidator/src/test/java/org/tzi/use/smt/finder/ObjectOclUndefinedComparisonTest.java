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
 * End-to-end regression for the LAST shape the {@code prim.object-reference-equality-undefined} row
 * named as its reason for staying {@code degraded}: an OBJECT-typed {@code oclUndefined(SomeClass)}
 * literal compared against a bare object variable ({@code c <> oclUndefined(C)}), plus the sibling
 * {@code isDefined}/{@code oclIsUndefined} over that same bare variable.
 *
 * <p>An object reference has no standalone SMT value in this encoding, but it does not need one:
 * comparison() short-circuits any {@code oclUndefined} operand to a definedness question about the
 * OTHER side ({@code x = oclUndefined(T)} is true exactly when x is undefined, its total-equality
 * negation for {@code <>}), and for a context-bound object variable that definedness is precisely
 * the variable's own slot-exists symbol -- a bare {@code C} slot the solver may or may not populate,
 * never a term-level comparison. The navigation-side shapes ({@code c.d = oclUndefined(D)} over a
 * link-presence boolean) were already correct and are locked in here end to end as regression
 * coverage, including the USE-evaluator-confirmed semantic that an unlinked single-valued
 * navigation IS undefined.
 */
public class ObjectOclUndefinedComparisonTest {

  private static final String MODEL =
      """
      model ObjectOclUndefinedComparison
      class C
      attributes
        s : String
      end
      class D
      attributes
        t : String
      end
      association R between
        C[1] role c
        D[0..1] role d
      end
      constraints
      context c : C inv VarEqUndef:
        c = oclUndefined(C)
      context c : C inv VarNeqUndef:
        c <> oclUndefined(C)
      context c : C inv VarIsDef:
        c.isDefined()
      context c : C inv VarIsUndef:
        c.oclIsUndefined()
      context c : C inv NavEqUndef:
        c.d = oclUndefined(D)
      context c : C inv NavNeqUndef:
        c.d <> oclUndefined(D)
      """;

  /**
   * With C forced to exactly one existing object, {@code c = oclUndefined(C)} is a genuine
   * contradiction: the slot exists, so c is not undefined.
   */
  @Test
  public void objectVariableEqualityWithOclUndefinedIsContradictedByAnExistingSlot()
      throws Exception {
    ModelFinderResult result = find("VarEqUndef", 1, 1, 0, 0, 0, 0);

    assertFalse(
        "the context slot is forced to exist, so c = oclUndefined(C) cannot hold",
        result.satisfiable());
  }

  /** The polarity control: {@code c <> oclUndefined(C)} holds for an existing slot. */
  @Test
  public void objectVariableInequalityWithOclUndefinedHoldsForAnExistingSlot() throws Exception {
    ModelFinderResult result = find("VarNeqUndef", 1, 1, 0, 0, 0, 0);

    assertTrue(
        "the context slot is forced to exist, so c <> oclUndefined(C) holds",
        result.satisfiable());
    assertTrue(verdictFor(result, "C::VarNeqUndef").holds());
  }

  /** isDefined/oclIsUndefined over the bare variable follow the same slot existence. */
  @Test
  public void isDefinedOverABareObjectVariableFollowsSlotExistence() throws Exception {
    ModelFinderResult defined = find("VarIsDef", 1, 1, 0, 0, 0, 0);
    assertTrue("an existing slot is defined", defined.satisfiable());
    assertTrue(verdictFor(defined, "C::VarIsDef").holds());

    ModelFinderResult undef = find("VarIsUndef", 1, 1, 0, 0, 0, 0);
    assertFalse("an existing slot is not oclUndefined", undef.satisfiable());
  }

  /**
   * Navigation vs. oclUndefined tracks link presence: with no D objects at all (hence no link),
   * {@code c.d} IS undefined, so the equality holds and the inequality cannot; with the link
   * forced, the opposite. Both SAT witnesses are independently confirmed by USE's own
   * re-evaluation (the javadoc-confirmed evaluator semantic this encoding mirrors).
   */
  @Test
  public void navigationVersusOclUndefinedTracksLinkPresence() throws Exception {
    ModelFinderResult unlinkedEq = find("NavEqUndef", 1, 1, 0, 0, 0, 0);
    assertTrue("an unlinked navigation is undefined, so = oclUndefined holds",
        unlinkedEq.satisfiable());
    assertTrue(verdictFor(unlinkedEq, "C::NavEqUndef").holds());

    ModelFinderResult unlinkedNeq = find("NavNeqUndef", 1, 1, 0, 0, 0, 0);
    assertFalse("an unlinked navigation is undefined, so <> oclUndefined cannot hold",
        unlinkedNeq.satisfiable());

    ModelFinderResult linkedEq = find("NavEqUndef", 1, 1, 1, 1, 1, 1);
    assertFalse("a forced link makes c.d defined, so = oclUndefined cannot hold",
        linkedEq.satisfiable());

    ModelFinderResult linkedNeq = find("NavNeqUndef", 1, 1, 1, 1, 1, 1);
    assertTrue("a forced link makes c.d defined, so <> oclUndefined holds",
        linkedNeq.satisfiable());
    assertTrue(verdictFor(linkedNeq, "C::NavNeqUndef").holds());
  }

  /**
   * Class scopes: C always 1..1 (the context slot is forced to exist, making the variable-shape
   * expectations non-vacuous); D and R per test (both 0..0 = no link, both 1..1 = one forced
   * link; note {@code role c : C[1]} then forces that link to land on the one C object).
   */
  private static ModelFinderResult find(
      String invariantName, int cMin, int cMax, int dMin, int dMax, int rMin, int rMax)
      throws Exception {
    MModel model = compile();
    AnalysisConfiguration config =
        new AnalysisConfiguration(
            List.of(new ClassScope("C", cMin, cMax), new ClassScope("D", dMin, dMax)),
            List.of(new AssociationScope("R", rMin, rMax)),
            List.of(
                new AttributeDomain("C", "s", null, List.of("alpha"), null, null),
                new AttributeDomain("D", "t", null, List.of("beta"), null, null)),
            Set.of("C::" + invariantName),
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
    MModel model =
        USECompiler.compileSpecification(MODEL, "ObjectOclUndefinedComparison", err, factory);
    err.flush();
    if (model == null) {
      throw new AssertionError("fixture model did not compile");
    }
    return model;
  }
}
