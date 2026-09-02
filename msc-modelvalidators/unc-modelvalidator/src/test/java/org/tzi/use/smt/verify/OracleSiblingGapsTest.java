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
 * Regression cover for the 5 sibling gaps the xor/equality audit ({@link
 * OracleXorAndEqualityTest}) found alongside it: {@code one}, {@code let}, {@code if}, {@code
 * isDefined}/{@code isUndefined}, and {@code self.op()} (a zero-argument query operation) each had
 * NO case in {@link ThreeValuedEvaluator}'s switch, so a node of that shape fell through to {@code
 * expression.eval(ctx)} -- USE's own raw evaluator -- and any nested {@code forAll}/{@code exists}
 * reachable inside it hit the exact same {@code evalForAll0}/{@code evalExists0} undefined-to-
 * {@code BooleanValue.FALSE} collapse {@link OracleThreeValuedDepthTest} and {@link
 * OracleXorAndEqualityTest} already cover for the connectives and quantifiers themselves.
 *
 * <p>All 5 are genuinely translated by {@code ExpressionTranslator} (not refused), so a witness
 * containing one of them is exactly the kind {@link QueryWitnessChecker} independently re-checks
 * through this class -- see {@link org.tzi.use.smt.finder.GenealogyLetAndOneCorpusRegressionTest}
 * for the same {@code let}/{@code one} gaps reproduced through the REAL, unmodified benchmark
 * corpus rather than a synthetic model.
 */
public class OracleSiblingGapsTest {

  // ---------------------------------------------------------------------------------------
  // Finding 1: `one`. ExpOne#evalAux's own "undefined query values default to false" rule
  // only guards a DIRECTLY undefined queryVal; a nested quantifier buried under an enclosing
  // connective (here `not`) has ITS undefinedness collapsed one level down, by raw eval, into
  // a DEFINED value before `one`'s own rule ever runs -- so `not` flips a should-be-non-match
  // into a wrongly-counted match. `Q.allInstances()` never depends on the `one` loop variable
  // `x`, so P having exactly one instance makes the divergence a clean TRUE-vs-FALSE flip
  // rather than merely a defined-vs-undefined one.
  // ---------------------------------------------------------------------------------------

  private static final String ONE_MODEL =
      """
      model OneDepth
      class P
      attributes
        n : Integer
      end
      class Q
      attributes
        m : Integer
      end
      constraints
      context p : P inv OneNestedNot:
        P.allInstances()->one(x | not (Q.allInstances()->forAll(y | y.m > 0)))
      """;

  /**
   * {@code m} unset: the inner {@code forAll} is genuinely UNDEFINED, so {@code not(forAll)} is
   * UNDEFINED too, and {@code one}'s own rule ("undefined does not count as a match") makes the
   * whole invariant DEFINED-FALSE (P has exactly one candidate {@code x}, and it does not match).
   * The unfixed path instead raw-evaluates the inner {@code forAll} first, which collapses its
   * one undefined element to {@code false} and returns a DEFINED-FALSE {@code forAll} -- {@code
   * not(false) = true} then WRONGLY counts as a match, so the unfixed oracle reports TRUE.
   */
  @Test
  public void oneWithANestedForAllUnderNotStaysDefinedFalseInsteadOfWronglyCountingAMatch()
      throws Exception {
    MModel model = compile(ONE_MODEL, "OneDepth");
    Session session = newSession(model);
    UseSystemApi api = UseSystemApi.create(session);
    api.createObjectEx(model.getClass("P"), "p1");
    api.createObjectEx(model.getClass("Q"), "q1"); // m deliberately unset

    List<InvariantVerdict> verdicts = InvariantReEvaluator.reevaluate(model, session.system());
    assertEquals(
        "not(UNDEFINED forAll) is UNDEFINED, and one's own rule never counts an undefined body as"
            + " a match -- the unfixed oracle reports TRUE instead",
        InvariantOutcome.FALSE,
        outcomeOf(verdicts, "P::OneNestedNot"));
  }

  /**
   * Regression guard against a degenerate fix that makes {@code one} always read FALSE (or
   * always UNDEFINED): with {@code m} DEFINED at 0, the inner {@code forAll} is genuinely
   * DEFINED-FALSE, {@code not(false) = true} genuinely matches, and {@code one} must read
   * DEFINED-TRUE -- the identical value the unfixed path happens to also produce here (the
   * divergence above only appears for the UNDEFINED case), so this pins down that the walked
   * path still computes the ordinary definite answer correctly.
   */
  @Test
  public void oneWithANestedForAllUnderNotStillReadsTrueWhenGenuinelyDefined() throws Exception {
    MModel model = compile(ONE_MODEL, "OneDepth");
    Session session = newSession(model);
    UseSystemApi api = UseSystemApi.create(session);
    api.createObjectEx(model.getClass("P"), "p1");
    api.createObjectEx(model.getClass("Q"), "q1");
    api.setAttributeValue("q1", "m", "0"); // forAll(y | y.m > 0) is DEFINED-FALSE

    List<InvariantVerdict> verdicts = InvariantReEvaluator.reevaluate(model, session.system());
    assertEquals(InvariantOutcome.TRUE, outcomeOf(verdicts, "P::OneNestedNot"));
  }

  // ---------------------------------------------------------------------------------------
  // Finding 2: `let`. ExpLet#eval's `fInExpr.eval(ctx)` has no collapse guard at all -- unlike
  // `one`'s own definedness rule, a `let` is a pure pass-through, so an undefined Boolean body
  // must stay undefined. CONFIRMED LIVE in this project's own benchmark corpus: Genealogy's
  // `parent_0_2_size_EQUIV_parent_0_2_Set_ONE` nests a `let` bound to another `let`'s body
  // (which itself nests `one`) -- reproduced directly, not just structurally, in {@link
  // org.tzi.use.smt.finder.GenealogyLetAndOneCorpusRegressionTest}.
  // ---------------------------------------------------------------------------------------

  private static final String LET_MODEL =
      """
      model LetDepth
      class P
      attributes
        n : Integer
      end
      constraints
      context p : P inv LetBooleanBody:
        let ok : Boolean = P.allInstances()->forAll(q | q.n > 0) in ok
      context p : P inv LetNestedInLet:
        let outer : Boolean = (let inner : Boolean = P.allInstances()->forAll(q | q.n > 0) in inner) in outer
      """;

  /**
   * The audit's own minimal shape: {@code n} unset makes the bound {@code forAll} UNDEFINED, and
   * a bare pass-through body ({@code ok}) must read the SAME UNDEFINED. The unfixed path raw-
   * evaluates the whole {@code let} -- {@code ExpLet#eval}'s raw {@code fVarExpr.eval(ctx)}
   * collapses the {@code forAll} to DEFINED-FALSE, that gets bound to {@code ok}, and the body
   * reads back the wrong DEFINED-FALSE.
   */
  @Test
  public void letWithABooleanBodyBoundToAnUndefinedNestedForAllStaysUndefined() throws Exception {
    MModel model = compile(LET_MODEL, "LetDepth");
    List<InvariantVerdict> verdicts = evaluate(model, "P", "p1");

    assertEquals(
        InvariantOutcome.UNDEFINED, outcomeOf(verdicts, "P::LetBooleanBody"));
  }

  /**
   * The Genealogy-shaped case: a {@code let} whose OWN initializer is ANOTHER {@code let} bound
   * to the same undefined {@code forAll}. This is not merely the same bug reproduced twice --
   * fixing ONLY {@code fInExpr} (walking just the body, matching the finding's literal "no
   * collapse guard" wording) and leaving {@code fVarExpr} on raw {@code Expression.eval} would
   * still fail this one: the OUTER let's {@code varExpr} IS the inner {@code let} node, so
   * raw-evaluating it re-enters {@code ExpLet#eval} (unfixed) and re-collapses the {@code
   * forAll} before the outer {@code let} ever gets a chance to walk anything. The fix therefore
   * reads a Boolean-typed {@code varExpr} through the SAME recursive evaluator as the body.
   */
  @Test
  public void letBoundToAnotherLetsUndefinedBooleanInitializerStaysUndefined() throws Exception {
    MModel model = compile(LET_MODEL, "LetDepth");
    List<InvariantVerdict> verdicts = evaluate(model, "P", "p1");

    assertEquals(
        InvariantOutcome.UNDEFINED, outcomeOf(verdicts, "P::LetNestedInLet"));
  }

  /** Regression guard: a genuinely DEFINED bound value still reads correctly, both ways. */
  @Test
  public void letWithABooleanBodyBoundToADefinedNestedForAllReadsTheOrdinaryValue()
      throws Exception {
    MModel model = compile(LET_MODEL, "LetDepth");

    Session allPositive = newSession(model);
    UseSystemApi allPositiveApi = UseSystemApi.create(allPositive);
    allPositiveApi.createObjectEx(model.getClass("P"), "p1");
    allPositiveApi.setAttributeValue("p1", "n", "5");
    List<InvariantVerdict> trueVerdicts =
        InvariantReEvaluator.reevaluate(model, allPositive.system());
    assertEquals(InvariantOutcome.TRUE, outcomeOf(trueVerdicts, "P::LetBooleanBody"));
    assertEquals(InvariantOutcome.TRUE, outcomeOf(trueVerdicts, "P::LetNestedInLet"));

    Session violated = newSession(model);
    UseSystemApi violatedApi = UseSystemApi.create(violated);
    violatedApi.createObjectEx(model.getClass("P"), "p1");
    violatedApi.setAttributeValue("p1", "n", "0");
    List<InvariantVerdict> falseVerdicts = InvariantReEvaluator.reevaluate(model, violated.system());
    assertEquals(InvariantOutcome.FALSE, outcomeOf(falseVerdicts, "P::LetBooleanBody"));
    assertEquals(InvariantOutcome.FALSE, outcomeOf(falseVerdicts, "P::LetNestedInLet"));
  }

  // ---------------------------------------------------------------------------------------
  // Finding 3: `if`. ExpIf#eval's raw fCondition/fThenExp/fElseExp.eval(ctx) calls carry the
  // same risk in all THREE positions. ExpIf#eval's actual code (confirmed directly, matching
  // ExpressionTranslator.visitIf's own independent confirmation) makes an undefined condition
  // collapse the WHOLE if-expression to undefined WITHOUT evaluating either branch -- it does
  // NOT fall through to the else branch the way the method's own docstring claims.
  // ---------------------------------------------------------------------------------------

  private static final String IF_MODEL =
      """
      model IfDepth
      class P
      attributes
        n : Integer
      end
      constraints
      context p : P inv IfCondition:
        if P.allInstances()->forAll(q | q.n > 0) then true else false endif
      context p : P inv IfThenBranch:
        if true then (P.allInstances()->forAll(q | q.n > 0)) else false endif
      context p : P inv IfElseBranch:
        if false then true else (P.allInstances()->forAll(q | q.n > 0)) endif
      """;

  /**
   * The CONDITION position: an undefined {@code forAll} condition must make the whole
   * if-expression UNDEFINED. Unfixed, raw {@code fCondition.eval(ctx)} collapses it to
   * DEFINED-FALSE, {@code condValue.isDefined()} then wrongly reads true, and the ELSE branch
   * (the literal {@code false}) is wrongly selected.
   */
  @Test
  public void ifWithAnUndefinedNestedForAllConditionStaysUndefined() throws Exception {
    MModel model = compile(IF_MODEL, "IfDepth");
    List<InvariantVerdict> verdicts = evaluate(model, "P", "p1");

    assertEquals(InvariantOutcome.UNDEFINED, outcomeOf(verdicts, "P::IfCondition"));
  }

  /**
   * The THEN-branch position: condition is definitely true, so the then branch is selected, and
   * an undefined {@code forAll} there must make the whole expression UNDEFINED. Unfixed, raw
   * {@code fThenExp.eval(ctx)} collapses it to DEFINED-FALSE.
   */
  @Test
  public void ifWithAnUndefinedNestedForAllInTheThenBranchStaysUndefined() throws Exception {
    MModel model = compile(IF_MODEL, "IfDepth");
    List<InvariantVerdict> verdicts = evaluate(model, "P", "p1");

    assertEquals(InvariantOutcome.UNDEFINED, outcomeOf(verdicts, "P::IfThenBranch"));
  }

  /** The ELSE-branch position, the same way. */
  @Test
  public void ifWithAnUndefinedNestedForAllInTheElseBranchStaysUndefined() throws Exception {
    MModel model = compile(IF_MODEL, "IfDepth");
    List<InvariantVerdict> verdicts = evaluate(model, "P", "p1");

    assertEquals(InvariantOutcome.UNDEFINED, outcomeOf(verdicts, "P::IfElseBranch"));
  }

  /** Regression guard: genuinely definite conditions/branches still read the ordinary value. */
  @Test
  public void ifWithGenuinelyDefinedNestedForAllsReadsTheOrdinaryValue() throws Exception {
    MModel model = compile(IF_MODEL, "IfDepth");

    Session allPositive = newSession(model);
    UseSystemApi allPositiveApi = UseSystemApi.create(allPositive);
    allPositiveApi.createObjectEx(model.getClass("P"), "p1");
    allPositiveApi.setAttributeValue("p1", "n", "5"); // forAll is DEFINED-TRUE
    List<InvariantVerdict> trueVerdicts =
        InvariantReEvaluator.reevaluate(model, allPositive.system());
    assertEquals(
        "condition is DEFINED-TRUE, then branch (literal true) is selected",
        InvariantOutcome.TRUE,
        outcomeOf(trueVerdicts, "P::IfCondition"));
    assertEquals(
        "then branch's own forAll is DEFINED-TRUE",
        InvariantOutcome.TRUE,
        outcomeOf(trueVerdicts, "P::IfThenBranch"));
    assertEquals(
        "else branch's own forAll is DEFINED-TRUE",
        InvariantOutcome.TRUE,
        outcomeOf(trueVerdicts, "P::IfElseBranch"));

    Session violated = newSession(model);
    UseSystemApi violatedApi = UseSystemApi.create(violated);
    violatedApi.createObjectEx(model.getClass("P"), "p1");
    violatedApi.setAttributeValue("p1", "n", "0"); // forAll is DEFINED-FALSE
    List<InvariantVerdict> falseVerdicts = InvariantReEvaluator.reevaluate(model, violated.system());
    assertEquals(
        "condition is DEFINED-FALSE, else branch (literal false) is selected",
        InvariantOutcome.FALSE,
        outcomeOf(falseVerdicts, "P::IfCondition"));
    assertEquals(InvariantOutcome.FALSE, outcomeOf(falseVerdicts, "P::IfThenBranch"));
    assertEquals(InvariantOutcome.FALSE, outcomeOf(falseVerdicts, "P::IfElseBranch"));
  }

  // ---------------------------------------------------------------------------------------
  // Finding 4: isDefined()/isUndefined(). ExpStdOp#eval raw-evaluates EVERY argument (including
  // a Boolean one) before Op_isDefined/Op_isUndefined -- both kind SPECIAL, meant to examine an
  // argument's OWN definedness -- ever run, so they end up examining the collapsed value instead
  // of the real one.
  // ---------------------------------------------------------------------------------------

  private static final String IS_DEFINED_MODEL =
      """
      model IsDefinedDepth
      class P
      attributes
        n : Integer
      end
      constraints
      context p : P inv IsUndefinedNestedForAll:
        (P.allInstances()->forAll(q | q.n > 0)).isUndefined
      context p : P inv IsDefinedNestedForAll:
        (P.allInstances()->forAll(q | q.n > 0)).isDefined
      """;

  /**
   * {@code n} unset: the {@code forAll} is genuinely UNDEFINED, so {@code isUndefined} must read
   * TRUE and {@code isDefined} must read FALSE. Unfixed, {@code fArgs[0].eval(ctx)} collapses
   * the {@code forAll} to DEFINED-FALSE first, so {@code Op_isUndefined}/{@code Op_isDefined}
   * examine a value that is not undefined at all -- the readings come out exactly inverted
   * (FALSE / TRUE).
   */
  @Test
  public void isUndefinedAndIsDefinedOverAnUndefinedNestedForAllReadCorrectly() throws Exception {
    MModel model = compile(IS_DEFINED_MODEL, "IsDefinedDepth");
    List<InvariantVerdict> verdicts = evaluate(model, "P", "p1");

    assertEquals(
        "the forAll is genuinely undefined, so isUndefined must be TRUE -- the unfixed oracle"
            + " reports FALSE",
        InvariantOutcome.TRUE,
        outcomeOf(verdicts, "P::IsUndefinedNestedForAll"));
    assertEquals(
        "the forAll is genuinely undefined, so isDefined must be FALSE -- the unfixed oracle"
            + " reports TRUE",
        InvariantOutcome.FALSE,
        outcomeOf(verdicts, "P::IsDefinedNestedForAll"));
  }

  /** Regression guard: a genuinely DEFINED nested forAll still reads the ordinary value. */
  @Test
  public void isUndefinedAndIsDefinedOverADefinedNestedForAllReadTheOrdinaryValue()
      throws Exception {
    MModel model = compile(IS_DEFINED_MODEL, "IsDefinedDepth");
    Session session = newSession(model);
    UseSystemApi api = UseSystemApi.create(session);
    api.createObjectEx(model.getClass("P"), "p1");
    api.setAttributeValue("p1", "n", "5"); // forAll is DEFINED-TRUE

    List<InvariantVerdict> verdicts = InvariantReEvaluator.reevaluate(model, session.system());
    assertEquals(InvariantOutcome.FALSE, outcomeOf(verdicts, "P::IsUndefinedNestedForAll"));
    assertEquals(InvariantOutcome.TRUE, outcomeOf(verdicts, "P::IsDefinedNestedForAll"));
  }

  // ---------------------------------------------------------------------------------------
  // Finding 5: self.op() for a zero-argument query operation. ExpObjOp#eval's raw {@code
  // operation.expression().eval(ctx)} carries the same risk once the operation's OWN body
  // contains a nested quantifier.
  // ---------------------------------------------------------------------------------------

  private static final String OBJ_OP_MODEL =
      """
      model ObjOpDepth
      class P
      attributes
        n : Integer
      operations
        allPositive() : Boolean = P.allInstances()->forAll(q | q.n > 0)
      end
      constraints
      context p : P inv SelfOpNestedForAll: p.allPositive()
      """;

  /**
   * {@code n} unset: {@code allPositive()}'s body is genuinely UNDEFINED, and {@code self.op()}
   * is a pure pass-through (unlike {@code one}, it applies no collapse rule of its own), so the
   * whole invariant must read UNDEFINED. Unfixed, raw {@code operation.expression().eval(ctx)}
   * collapses the {@code forAll} to DEFINED-FALSE.
   */
  @Test
  public void selfOpWhoseBodyIsAnUndefinedNestedForAllStaysUndefined() throws Exception {
    MModel model = compile(OBJ_OP_MODEL, "ObjOpDepth");
    List<InvariantVerdict> verdicts = evaluate(model, "P", "p1");

    assertEquals(InvariantOutcome.UNDEFINED, outcomeOf(verdicts, "P::SelfOpNestedForAll"));
  }

  /** Regression guard: a genuinely DEFINED operation body still reads the ordinary value. */
  @Test
  public void selfOpWhoseBodyIsAGenuinelyDefinedNestedForAllReadsTheOrdinaryValue()
      throws Exception {
    MModel model = compile(OBJ_OP_MODEL, "ObjOpDepth");

    Session allPositive = newSession(model);
    UseSystemApi allPositiveApi = UseSystemApi.create(allPositive);
    allPositiveApi.createObjectEx(model.getClass("P"), "p1");
    allPositiveApi.setAttributeValue("p1", "n", "5");
    assertEquals(
        InvariantOutcome.TRUE,
        outcomeOf(
            InvariantReEvaluator.reevaluate(model, allPositive.system()),
            "P::SelfOpNestedForAll"));

    Session violated = newSession(model);
    UseSystemApi violatedApi = UseSystemApi.create(violated);
    violatedApi.createObjectEx(model.getClass("P"), "p1");
    violatedApi.setAttributeValue("p1", "n", "0");
    assertEquals(
        InvariantOutcome.FALSE,
        outcomeOf(
            InvariantReEvaluator.reevaluate(model, violated.system()), "P::SelfOpNestedForAll"));
  }

  // ---------------------------------------------------------------------------------------

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
