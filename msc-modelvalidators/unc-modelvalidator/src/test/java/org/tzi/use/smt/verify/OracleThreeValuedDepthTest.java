package org.tzi.use.smt.verify;

import static org.junit.Assert.assertEquals;

import java.io.PrintWriter;
import java.util.List;
import org.junit.Test;
import org.tzi.use.api.UseSystemApi;
import org.tzi.use.main.Session;
import org.tzi.use.parser.use.USECompiler;
import org.tzi.use.smt.config.InvariantOutcome;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;
import org.tzi.use.uml.sys.MSystem;

/**
 * Regression cover for defect B3: the three-valued oracle was only ONE LEVEL deep. It rewrote the
 * outermost collapse by evaluating the invariant BODY per context instance, but handed that whole
 * body to USE's evaluator -- and USE collapses undefined to false at EVERY quantifier level ({@code
 * ExpQuery.evalForAll0}/{@code evalExists0}: {@code if (queryVal.isUndefined()) queryVal =
 * BooleanValue.FALSE;}). A nested {@code forAll}/{@code exists} body therefore came back as a bogus
 * DEFINED-FALSE, which is exactly what {@code QueryWitnessChecker} accepts as a valid
 * counterexample attribution.
 *
 * <p>This is not an edge case: all three Library key invariants have {@code forAll} bodies, so
 * Milestone 4.3's own parity evidence rides on this path -- which is why failing closed on a nested
 * quantifier is not an acceptable answer here.
 */
public class OracleThreeValuedDepthTest {

  /**
   * {@code Flat} and {@code NestedForAll} are the same predicate over the same one-object state,
   * and OCL calls both UNDEFINED because {@code n} is unset. Only the flat one was read correctly.
   */
  @Test
  public void aNestedQuantifierBodyStaysUndefinedInsteadOfCollapsingToFalse() throws Exception {
    MModel model =
        compile(
            """
            model NestedDepth
            class P
            attributes
              n : Integer
            end
            constraints
            context p : P inv Flat: p.n > 0
            context p : P inv NestedForAll: P.allInstances()->forAll(q | q.n > 0)
            context p : P inv NestedExists: P.allInstances()->exists(q | q.n > 0)
            """,
            "NestedDepth");
    List<InvariantVerdict> verdicts = evaluate(model, "P", "p1");

    assertEquals(InvariantOutcome.UNDEFINED, outcomeOf(verdicts, "P::Flat"));
    assertEquals(
        "P::NestedForAll is UNDEFINED in OCL, exactly like P::Flat",
        InvariantOutcome.UNDEFINED,
        outcomeOf(verdicts, "P::NestedForAll"));
    assertEquals(
        "P::NestedExists is UNDEFINED in OCL, exactly like P::Flat",
        InvariantOutcome.UNDEFINED,
        outcomeOf(verdicts, "P::NestedExists"));
  }

  /**
   * The {@code NestedForAll} coverage above nests only ONE level deep: an IMPLICIT context
   * quantifier around a single EXPLICIT {@code forAll}, whose body ({@code q.n > 0}) is a plain
   * comparison. {@link ThreeValuedEvaluator}'s per-element base case therefore falls through to
   * {@code outcomeOf(expression, expression.eval(ctx))} either way it is written, so a mutation that
   * swaps its recursive {@code eval(body, ctx)} call for exactly that expression is invisible there.
   *
   * <p>This test nests a genuinely EXPLICIT {@code forAll} inside another EXPLICIT {@code forAll}'s
   * body, so the base case's {@code body} is itself an {@code ExpForAll} -- the recursive call is
   * load-bearing here: bypassing it hands the inner quantifier to USE's own evaluator, which
   * collapses its undefined element to {@code BooleanValue.FALSE} ({@code ExpQuery.evalForAll0}),
   * turning this invariant's true UNDEFINED reading into a bogus DEFINED-FALSE -- the B3 defect one
   * level deeper than the outermost collapse.
   */
  @Test
  public void anExplicitForAllNestedInsideAnotherExplicitForAllBodyStaysUndefined()
      throws Exception {
    MModel model =
        compile(
            """
            model DoublyNestedDepth
            class P
            attributes
              n : Integer
            end
            constraints
            context p : P inv DoubleNestedForAll: P.allInstances()->forAll(x | P.allInstances()->forAll(y | y.n > 0))
            """,
            "DoublyNestedDepth");
    List<InvariantVerdict> verdicts = evaluate(model, "P", "p1");

    assertEquals(
        "the inner forAll's undefined element must stay undefined two levels deep, not collapse"
            + " to false",
        InvariantOutcome.UNDEFINED,
        outcomeOf(verdicts, "P::DoubleNestedForAll"));
  }

  /**
   * A genuinely violated nested quantifier must still be read as DEFINED-FALSE -- the fix must make
   * undefined visible without making every nested body undefined. This is the shape Library's three
   * key invariants use, so it is the parity evidence's own path.
   */
  @Test
  public void aGenuinelyViolatedNestedQuantifierIsStillDefinedFalse() throws Exception {
    MModel model =
        compile(
            """
            model NestedViolation
            class P
            attributes
              n : Integer
            end
            constraints
            context p : P inv NestedForAll: P.allInstances()->forAll(q | q.n > 0)
            context p : P inv NestedExists: P.allInstances()->exists(q | q.n > 99)
            """,
            "NestedViolation");
    Session session = newSession(model);
    UseSystemApi api = UseSystemApi.create(session);
    api.createObjectEx(model.getClass("P"), "p1");
    api.setAttributeValue("p1", "n", "0");

    List<InvariantVerdict> verdicts = InvariantReEvaluator.reevaluate(model, session.system());
    assertEquals(InvariantOutcome.FALSE, outcomeOf(verdicts, "P::NestedForAll"));
    assertEquals(InvariantOutcome.FALSE, outcomeOf(verdicts, "P::NestedExists"));
  }

  /**
   * A defined-false element must still dominate an undefined one, exactly as {@code
   * InvariantAssembler.classify} encodes it into SMT: the two sides of the cross-check have to
   * agree on the rule, not merely both be three-valued.
   */
  @Test
  public void aDefinedFalseElementDominatesAnUndefinedOneInsideANestedForAll() throws Exception {
    MModel model =
        compile(
            """
            model NestedMixed
            class P
            attributes
              n : Integer
            end
            constraints
            context p : P inv NestedForAll: P.allInstances()->forAll(q | q.n > 0)
            """,
            "NestedMixed");
    Session session = newSession(model);
    UseSystemApi api = UseSystemApi.create(session);
    api.createObjectEx(model.getClass("P"), "p1");
    api.setAttributeValue("p1", "n", "0");
    api.createObjectEx(model.getClass("P"), "p2"); // n deliberately unset -> undefined element

    List<InvariantVerdict> verdicts = InvariantReEvaluator.reevaluate(model, session.system());
    assertEquals(InvariantOutcome.FALSE, outcomeOf(verdicts, "P::NestedForAll"));
  }

  /**
   * The multi-variable fallback path invented a definite FALSE for a genuinely UNDEFINED invariant:
   * {@code b.n} is unset, so {@code p1.n &lt; p2.n} is undefined for every ordered pair of distinct
   * objects and defined-false for none.
   */
  @Test
  public void theMultiVariableContextIsThreeValuedRatherThanInventingFalse() throws Exception {
    MModel model =
        compile(
            """
            model MultiVariableContext
            class P
            attributes
              n : Integer
            end
            constraints
            context p1, p2 : P inv PairOrdered: p1 <> p2 implies p1.n < p2.n
            """,
            "MultiVariableContext");
    Session session = newSession(model);
    UseSystemApi api = UseSystemApi.create(session);
    api.createObjectEx(model.getClass("P"), "a");
    api.createObjectEx(model.getClass("P"), "b");
    api.setAttributeValue("a", "n", "1"); // b.n deliberately unset

    List<InvariantVerdict> verdicts = InvariantReEvaluator.reevaluate(model, session.system());
    assertEquals(
        "no ordered pair is defined-false, so OCL says UNDEFINED",
        InvariantOutcome.UNDEFINED,
        outcomeOf(verdicts, "P::PairOrdered"));
  }

  /** The same fallback, on a multi-variable context that really is violated. */
  @Test
  public void aViolatedMultiVariableContextIsStillDefinedFalse() throws Exception {
    MModel model =
        compile(
            """
            model MultiVariableViolation
            class P
            attributes
              n : Integer
            end
            constraints
            context p1, p2 : P inv PairDistinct: p1 <> p2 implies p1.n <> p2.n
            """,
            "MultiVariableViolation");
    Session session = newSession(model);
    UseSystemApi api = UseSystemApi.create(session);
    api.createObjectEx(model.getClass("P"), "a");
    api.createObjectEx(model.getClass("P"), "b");
    api.setAttributeValue("a", "n", "1");
    api.setAttributeValue("b", "n", "1");

    List<InvariantVerdict> verdicts = InvariantReEvaluator.reevaluate(model, session.system());
    assertEquals(InvariantOutcome.FALSE, outcomeOf(verdicts, "P::PairDistinct"));
  }

  /** The existential fallback: every instance's body is undefined, so OCL says UNDEFINED. */
  @Test
  public void anExistentialInvariantIsThreeValuedRatherThanInventingFalse() throws Exception {
    MModel model =
        compile(
            """
            model ExistentialUndefined
            class P
            attributes
              n : Integer
            end
            constraints
            context p : P existential inv SomePositive: p.n > 0
            """,
            "ExistentialUndefined");
    List<InvariantVerdict> verdicts = evaluate(model, "P", "a");

    assertEquals(InvariantOutcome.UNDEFINED, outcomeOf(verdicts, "P::SomePositive"));
  }

  /**
   * And the two definite existential readings, so the fix cannot degenerate to always-UNDEFINED.
   */
  @Test
  public void anExistentialInvariantIsTrueWhenOneInstanceSatisfiesItAndFalseWhenNoneDo()
      throws Exception {
    MModel model =
        compile(
            """
            model ExistentialDefinite
            class P
            attributes
              n : Integer
            end
            constraints
            context p : P existential inv SomePositive: p.n > 0
            """,
            "ExistentialDefinite");

    Session satisfied = newSession(model);
    UseSystemApi satisfiedApi = UseSystemApi.create(satisfied);
    satisfiedApi.createObjectEx(model.getClass("P"), "a");
    satisfiedApi.setAttributeValue("a", "n", "0");
    satisfiedApi.createObjectEx(model.getClass("P"), "b");
    satisfiedApi.setAttributeValue("b", "n", "3");
    assertEquals(
        InvariantOutcome.TRUE,
        outcomeOf(InvariantReEvaluator.reevaluate(model, satisfied.system()), "P::SomePositive"));

    Session violated = newSession(model);
    UseSystemApi violatedApi = UseSystemApi.create(violated);
    violatedApi.createObjectEx(model.getClass("P"), "a");
    violatedApi.setAttributeValue("a", "n", "0");
    assertEquals(
        InvariantOutcome.FALSE,
        outcomeOf(InvariantReEvaluator.reevaluate(model, violated.system()), "P::SomePositive"));

    Session empty = newSession(model);
    UseSystemApi.create(empty);
    assertEquals(
        "OCL's exists over an empty collection is FALSE, not vacuously true",
        InvariantOutcome.FALSE,
        outcomeOf(InvariantReEvaluator.reevaluate(model, empty.system()), "P::SomePositive"));
  }

  private static List<InvariantVerdict> evaluate(MModel model, String className, String objectName)
      throws Exception {
    Session session = newSession(model);
    UseSystemApi api = UseSystemApi.create(session);
    api.createObjectEx(model.getClass(className), objectName);
    return InvariantReEvaluator.reevaluate(model, session.system());
  }

  private static Session newSession(MModel model) {
    Session session = new Session();
    session.setSystem(new MSystem(model));
    return session;
  }

  private static InvariantOutcome outcomeOf(List<InvariantVerdict> verdicts, String name) {
    return verdicts.stream()
        .filter(verdict -> verdict.invariantName().equals(name))
        .findFirst()
        .orElseThrow(() -> new AssertionError("no verdict for " + name))
        .outcome();
  }

  private static MModel compile(String source, String name) {
    ModelFactory factory = new ModelFactory();
    PrintWriter err = new PrintWriter(System.err);
    MModel model = USECompiler.compileSpecification(source, name, err, factory);
    err.flush();
    return model;
  }
}
