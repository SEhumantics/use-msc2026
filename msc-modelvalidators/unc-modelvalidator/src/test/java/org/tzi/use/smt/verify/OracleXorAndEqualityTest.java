package org.tzi.use.smt.verify;

import static org.junit.Assert.assertEquals;

import java.io.PrintWriter;
import java.util.List;
import java.util.Set;
import org.junit.Test;
import org.tzi.use.api.UseSystemApi;
import org.tzi.use.main.Session;
import org.tzi.use.parser.use.USECompiler;
import org.tzi.use.smt.config.InvariantOutcome;
import org.tzi.use.smt.config.TranslationMode;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;
import org.tzi.use.uml.sys.MSystem;

/**
 * Regression cover for the safety-net gap in {@link ThreeValuedEvaluator}'s connective switch:
 * {@code xor} had NO case, so an {@code xor} expression fell through to {@code
 * expression.eval(ctx)} -- USE's own DEFAULT evaluator. That evaluator is not the Kleene-strict
 * path {@code ThreeValuedEvaluator} otherwise walks: {@code Op_boolean_xor.evalWithArgs} calls
 * {@code args[i].eval(ctx)} directly on each operand, so a nested {@code forAll}/{@code exists}
 * operand hits {@code ExpQuery.evalForAll0}/{@code evalExists0}'s undefined-to-{@code FALSE}
 * collapse (the same "defect B3" {@link OracleThreeValuedDepthTest} covers for {@code
 * and}/{@code or}/{@code not}/{@code implies}) before {@code xor} ever sees the value.
 *
 * <p>{@code ExpressionTranslator.booleanXor} documents the correct rule: {@code xor} has no
 * ABSORBING value under Kleene three-valued logic, so it is defined exactly when BOTH operands
 * are defined; otherwise it is the ordinary boolean xor of the two definite values.
 */
public class OracleXorAndEqualityTest {

  private static final String MODEL_SOURCE =
      """
      model XorDepth
      class P
      attributes
        n : Integer
      end
      constraints
      context p : P inv XorNestedForAll: (P.allInstances()->forAll(q | q.n > 0)) xor false
      context p : P inv EqualsNestedForAll: (P.allInstances()->forAll(q | q.n > 0)) = false
      """;

  /**
   * The audit's own repro: one object with {@code n} unset, so the inner {@code forAll} is
   * UNDEFINED (its one element is undefined, and none is defined-false). {@code UNDEFINED xor
   * false} is UNDEFINED under Kleene logic (xor has no absorbing value), so the whole invariant
   * must read UNDEFINED -- not the bogus DEFINED-FALSE the collapse bug produces.
   */
  @Test
  public void xorWithAnUndefinedNestedForAllOperandStaysUndefinedInsteadOfCollapsingToFalse()
      throws Exception {
    MModel model = compile(MODEL_SOURCE, "XorDepth");
    List<InvariantVerdict> verdicts = evaluate(model, "P", "p1");

    assertEquals(
        "(forAll q | q.n > 0) xor false is UNDEFINED because the forAll is UNDEFINED",
        InvariantOutcome.UNDEFINED,
        outcomeOf(verdicts, "P::XorNestedForAll"));
  }

  /**
   * The same crisp invariant read through the NOMINAL-erasure path: {@link
   * NominalErasureEvaluator#eval} delegates a crisp expression straight to {@link
   * ThreeValuedEvaluator#eval} ("a crisp expression erases to itself"), so the xor fix must
   * resolve the identical gap there too.
   */
  @Test
  public void theNominalErasurePathAgreesBecauseTheCrispXorDelegatesToTheFixedEvaluator()
      throws Exception {
    MModel model = compile(MODEL_SOURCE, "XorDepth");
    Session session = newSession(model);
    UseSystemApi api = UseSystemApi.create(session);
    api.createObjectEx(model.getClass("P"), "p1"); // n deliberately unset

    List<InvariantVerdict> verdicts =
        InvariantReEvaluator.reevaluate(
            model, session.system(), TranslationMode.NOMINAL, Set.of("P::XorNestedForAll"));
    assertEquals(
        InvariantOutcome.UNDEFINED, outcomeOf(verdicts, "P::XorNestedForAll"));
  }

  /**
   * A regression guard against a fix that degenerates {@code xor} to always-UNDEFINED: once the
   * nested {@code forAll} is genuinely DEFINED (every element positive), {@code xor} must compute
   * the ordinary truth table over the two definite values.
   */
  @Test
  public void xorOfTwoDefiniteOperandsStillComputesTheOrdinaryTruthTable() throws Exception {
    MModel model = compile(MODEL_SOURCE, "XorDepth");
    Session session = newSession(model);
    UseSystemApi api = UseSystemApi.create(session);
    api.createObjectEx(model.getClass("P"), "p1");
    api.setAttributeValue("p1", "n", "1"); // forAll is DEFINED-TRUE

    List<InvariantVerdict> verdicts = InvariantReEvaluator.reevaluate(model, session.system());
    assertEquals(
        "true xor false = true",
        InvariantOutcome.TRUE,
        outcomeOf(verdicts, "P::XorNestedForAll"));
  }

  /**
   * A defined-false nested {@code forAll} must dominate the same way it does for {@code and}/
   * {@code or}: {@code xor} of a definite FALSE and a definite FALSE is definite FALSE, not
   * UNDEFINED and not a bogus TRUE.
   */
  @Test
  public void xorOfTwoDefiniteFalseOperandsIsDefinedFalse() throws Exception {
    MModel model = compile(MODEL_SOURCE, "XorDepth");
    Session session = newSession(model);
    UseSystemApi api = UseSystemApi.create(session);
    api.createObjectEx(model.getClass("P"), "p1");
    api.setAttributeValue("p1", "n", "0"); // forAll is DEFINED-FALSE

    List<InvariantVerdict> verdicts = InvariantReEvaluator.reevaluate(model, session.system());
    assertEquals(
        "false xor false = false",
        InvariantOutcome.FALSE,
        outcomeOf(verdicts, "P::XorNestedForAll"));
  }

  /**
   * The related-but-unverified check the audit also flagged: does the SAME collapse reach {@code
   * =}/{@code <>} when a Boolean-typed nested quantifier sits on one side? {@code
   * ExpressionTranslator.comparison}'s general fallback ({@code useEquality}) implements USE's
   * OWN total-equality rule ({@code Op_equal.eval}, kind {@code SPECIAL}: always defined, true
   * iff both sides are undefined or both defined and equal) -- so the CORRECT answer for {@code
   * (UNDEFINED forAll) = false} is definite FALSE (an undefined left side can only equal an
   * undefined right side). This test pins that correct answer down by construction, independent
   * of whatever {@link ThreeValuedEvaluator} currently does with it.
   */
  @Test
  public void anUndefinedNestedForAllComparedToFalseIsDefinedFalseUnderTotalEquality()
      throws Exception {
    MModel model = compile(MODEL_SOURCE, "XorDepth");
    List<InvariantVerdict> verdicts = evaluate(model, "P", "p1");

    assertEquals(
        "OCL's total '=' only equates undefined with undefined, so (UNDEFINED) = false is FALSE,"
            + " not UNDEFINED and not TRUE",
        InvariantOutcome.FALSE,
        outcomeOf(verdicts, "P::EqualsNestedForAll"));
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
