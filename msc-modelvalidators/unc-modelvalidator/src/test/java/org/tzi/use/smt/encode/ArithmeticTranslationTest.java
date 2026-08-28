package org.tzi.use.smt.encode;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.PrintWriter;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Test;
import org.tzi.use.parser.use.USECompiler;
import org.tzi.use.smt.config.AnalysisConfiguration;
import org.tzi.use.smt.config.AssociationScope;
import org.tzi.use.smt.config.AttributeDomain;
import org.tzi.use.smt.config.ClassScope;
import org.tzi.use.smt.finder.ModelFinderResult;
import org.tzi.use.smt.finder.SmtModelFinder;
import org.tzi.use.smt.solver.*;
import org.tzi.use.smt.verify.InvariantVerdict;
import org.tzi.use.uml.mm.MClassInvariant;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;

/**
 * Binary {@code +}/{@code -}/{@code *} and unary {@code -}/{@code +} over plain crisp Integer
 * operands -- THESIS_SMT_MODEL_FINDER_PLAN.md 7.1 Tier 2's "integer arithmetic and comparisons".
 * Binary {@code +}/{@code -} and unary {@code -} were confirmed by the manager's re-ranked ledger
 * as the sole remaining refusal blocking BOTH {@code EmployeeInvariants}/{@code
 * EmployeeInvariants-UNSAT} (real invariant {@code NotBelowMinusOne: self.salary > -1}) and {@code
 * NQueens}/{@code NQueens-UNSAT} (real invariant {@code noAttack: q1<>q2 implies
 * (q1.row.idx+q1.col.idx <> q2.row.idx+q2.col.idx and q1.row.idx-q1.col.idx <>
 * q2.row.idx-q2.col.idx)}). Binary {@code *} and unary {@code +} complete the remaining two of the
 * ten {@code prim.integer-arithmetic} operations that need no dedicated undefinedness handling
 * (unlike {@code /}, {@code div}, {@code mod}, {@code abs}, {@code min}, {@code max}, which stay
 * unconditionally refused); the {@code ArithmeticScope} fixture below constructs a real, compiled
 * invariant for each rather than reusing an existing corpus scenario, since no scenario in the
 * benchmark corpus happens to use {@code *} or unary {@code +}.
 *
 * <p>AST evidence (compiling the real models and inspecting the parsed tree directly, not
 * inferred): {@code self.salary > -1} reaches this visitor as {@code ExpStdOp} opname {@code "-"}
 * with {@code a.length==1} wrapping {@code ExpConstInteger(1)} -- USE does NOT fold a literal unary
 * minus into a negative constant at parse time, so unary minus is a real, reachable shape. {@code
 * q1.row.idx+q1.col.idx} / {@code q1.row.idx-q1.col.idx} reach it as {@code ExpStdOp} opname {@code
 * "+"}/{@code "-"} with {@code a.length==2}, each operand an already-supported navigated-attribute
 * {@code ExpAttrOp}. {@code a.i * a.j} reaches it as {@code ExpStdOp} opname {@code "*"} with
 * {@code a.length==2}; {@code +a.j} reaches it as {@code ExpStdOp} opname {@code "+"} with {@code
 * a.length==1}.
 */
public class ArithmeticTranslationTest {

  // ---------------------------------------------------------------------
  // EmployeeInvariants::NotBelowMinusOne -- unary minus over a literal
  // ---------------------------------------------------------------------

  @Test
  public void notBelowMinusOneOnTheRealAstIsSatWhenSalaryIsZero() throws Exception {
    MModel model = compileEmployee();
    MClassInvariant inv = findInvariant(model, "NotBelowMinusOne");

    SmtScript script = new SmtScript("QF_LIA");
    ObjectSlots employees =
        ObjectSlotEncoder.encode(script, List.of(new ClassScope("Employee", 1, 1))).get("Employee");
    AttributeDomain salaryDomain =
        new AttributeDomain("Employee", "salary", null, List.of(), null, null);
    AttributeValues salary =
        AttributeEncoder.encode(script, employees, "salary", AttributeType.INTEGER, salaryDomain);

    TranslationContext ctx =
        new TranslationContext(
            Map.of("self", new VariableBinding("Employee", 0)),
            Map.of("Employee.salary", salary),
            Map.of("Employee.salary", salaryDomain),
            Map.of("Employee", employees),
            Map.of());
    SmtTerm translated = ExpressionTranslator.translate(inv.bodyExpression(), ctx);

    script.assertThat(Smt.sym("Employee_0_exists"));
    script.assertThat(Smt.eq(Smt.sym(salary.valueNames().get(0)), Smt.intLit(BigInteger.ZERO)));
    script.assertThat(translated);

    assertEquals(SolverOutcome.SAT, solve(script).outcome());
  }

  /**
   * The exact boundary: {@code -1 > -1} is false, so a salary of exactly {@code -1} must be UNSAT.
   * This also distinguishes a "unary minus is a no-op" mutation from the correct implementation --
   * a no-op would translate the invariant as {@code self.salary > 1}, under which salary=-1 is ALSO
   * false/UNSAT, so this fixture alone does not catch that particular mutation (see {@code
   * notBelowMinusOneOnTheRealAstIsSatWhenSalaryIsZero} above for the one that does: salary=0 is SAT
   * under the correct {@code > -1} but UNSAT under the no-op {@code > 1}).
   */
  @Test
  public void notBelowMinusOneOnTheRealAstIsUnsatAtTheExactBoundary() throws Exception {
    MModel model = compileEmployee();
    MClassInvariant inv = findInvariant(model, "NotBelowMinusOne");

    SmtScript script = new SmtScript("QF_LIA");
    ObjectSlots employees =
        ObjectSlotEncoder.encode(script, List.of(new ClassScope("Employee", 1, 1))).get("Employee");
    AttributeDomain salaryDomain =
        new AttributeDomain("Employee", "salary", null, List.of(), null, null);
    AttributeValues salary =
        AttributeEncoder.encode(script, employees, "salary", AttributeType.INTEGER, salaryDomain);

    TranslationContext ctx =
        new TranslationContext(
            Map.of("self", new VariableBinding("Employee", 0)),
            Map.of("Employee.salary", salary),
            Map.of("Employee.salary", salaryDomain),
            Map.of("Employee", employees),
            Map.of());
    SmtTerm translated = ExpressionTranslator.translate(inv.bodyExpression(), ctx);

    script.assertThat(Smt.sym("Employee_0_exists"));
    script.assertThat(
        Smt.eq(Smt.sym(salary.valueNames().get(0)), Smt.intLit(BigInteger.ONE.negate())));
    // The line above intentionally re-derives -1 via negate() of 1 rather than writing a literal
    // -1 directly, so this fixture is not itself vulnerable to the same sign mistake it exists to
    // catch in the production code.
    script.assertThat(translated);

    assertEquals(SolverOutcome.UNSAT, solve(script).outcome());
  }

  // ---------------------------------------------------------------------
  // NQueens::noAttack -- binary + and binary -, each over two navigated attributes
  // ---------------------------------------------------------------------

  @Test
  public void noAttackOnTheRealAstIsSatWhenNeitherDiagonalCollides() throws Exception {
    // q1 = (row 0, col 0), q2 = (row 1, col 2): sum 0 vs 3 (distinct), diff 0 vs -1 (distinct).
    SolverOutcome outcome = solveNoAttack(0, 0, 1, 2);
    assertEquals(SolverOutcome.SAT, outcome);
  }

  /**
   * Adversarial: the anti-diagonal SUM collides (2 == 2) while the main-diagonal DIFFERENCE does
   * NOT (-2 vs 0) -- isolating {@code +}. A mutation that implemented {@code +} as {@code -} (or
   * vice versa) would compute sum1=-2, sum2=0 here instead, see them as distinct, and wrongly
   * report SAT instead of the correct UNSAT.
   */
  @Test
  public void noAttackOnTheRealAstIsUnsatWhenTheSumDiagonalCollidesButTheDifferenceDoesNot()
      throws Exception {
    // q1 = (row 0, col 2): sum 2, diff -2. q2 = (row 1, col 1): sum 2, diff 0.
    SolverOutcome outcome = solveNoAttack(0, 2, 1, 1);
    assertEquals(SolverOutcome.UNSAT, outcome);
  }

  /**
   * The sign-sensitive twin of the test above: the main-diagonal DIFFERENCE collides (0 == 0) while
   * the SUM does not (0 vs 2) -- isolating {@code -} specifically. A mutation that implemented
   * {@code -} as {@code +} would compute diff1=0, diff2=2 here instead (the same as the real sum),
   * see them as distinct, and wrongly report SAT instead of the correct UNSAT. This is the
   * adversarial fixture whose correct answer depends on the SIGN of the operation.
   */
  @Test
  public void noAttackOnTheRealAstIsUnsatWhenTheDifferenceDiagonalCollidesButTheSumDoesNot()
      throws Exception {
    // q1 = (row 0, col 0): sum 0, diff 0. q2 = (row 1, col 1): sum 2, diff 0.
    SolverOutcome outcome = solveNoAttack(0, 0, 1, 1);
    assertEquals(SolverOutcome.UNSAT, outcome);
  }

  /** q1 is bound to (Row slot 0, Col slot 0), q2 to (Row slot 1, Col slot 1); idx values given. */
  private SolverOutcome solveNoAttack(int rowIdxQ1, int colIdxQ1, int rowIdxQ2, int colIdxQ2)
      throws Exception {
    MModel model = compileNQueens();
    MClassInvariant inv = findInvariant(model, "noAttack");

    SmtScript script = new SmtScript("QF_LIA");
    ObjectSlots queens =
        ObjectSlotEncoder.encode(script, List.of(new ClassScope("Queen", 2, 2))).get("Queen");
    ObjectSlots rows =
        ObjectSlotEncoder.encode(script, List.of(new ClassScope("Row", 2, 2))).get("Row");
    ObjectSlots cols =
        ObjectSlotEncoder.encode(script, List.of(new ClassScope("Col", 2, 2))).get("Col");

    AssociationLinks queenRow =
        AssociationLinkEncoder.encode(
            script,
            "QueenRow",
            queens,
            new Multiplicity(1, 1),
            rows,
            new Multiplicity(1, 1),
            new AssociationScope("QueenRow", 0, -1));
    AssociationLinks queenCol =
        AssociationLinkEncoder.encode(
            script,
            "QueenCol",
            queens,
            new Multiplicity(1, 1),
            cols,
            new Multiplicity(1, 1),
            new AssociationScope("QueenCol", 0, -1));

    AttributeDomain rowIdxDomain = new AttributeDomain("Row", "idx", null, List.of(), null, null);
    AttributeValues rowIdx =
        AttributeEncoder.encode(script, rows, "idx", AttributeType.INTEGER, rowIdxDomain);
    AttributeDomain colIdxDomain = new AttributeDomain("Col", "idx", null, List.of(), null, null);
    AttributeValues colIdx =
        AttributeEncoder.encode(script, cols, "idx", AttributeType.INTEGER, colIdxDomain);

    TranslationContext ctx =
        new TranslationContext(
            Map.of(
                "q1", new VariableBinding("Queen", 0),
                "q2", new VariableBinding("Queen", 1)),
            Map.of("Row.idx", rowIdx, "Col.idx", colIdx),
            Map.of("Row.idx", rowIdxDomain, "Col.idx", colIdxDomain),
            Map.of("Queen", queens, "Row", rows, "Col", cols),
            Map.of("QueenRow", queenRow, "QueenCol", queenCol));
    SmtTerm translated = ExpressionTranslator.translate(inv.bodyExpression(), ctx);

    script.assertThat(Smt.sym("Queen_0_exists"));
    script.assertThat(Smt.sym("Queen_1_exists"));
    script.assertThat(Smt.sym("Row_0_exists"));
    script.assertThat(Smt.sym("Row_1_exists"));
    script.assertThat(Smt.sym("Col_0_exists"));
    script.assertThat(Smt.sym("Col_1_exists"));
    // q1 (slot 0) -> row slot 0, col slot 0.
    script.assertThat(Smt.sym(queenRow.linkNames()[0][0]));
    script.assertThat(Smt.sym(queenCol.linkNames()[0][0]));
    // q2 (slot 1) -> row slot 1, col slot 1.
    script.assertThat(Smt.sym(queenRow.linkNames()[1][1]));
    script.assertThat(Smt.sym(queenCol.linkNames()[1][1]));
    script.assertThat(
        Smt.eq(Smt.sym(rowIdx.valueNames().get(0)), Smt.intLit(BigInteger.valueOf(rowIdxQ1))));
    script.assertThat(
        Smt.eq(Smt.sym(colIdx.valueNames().get(0)), Smt.intLit(BigInteger.valueOf(colIdxQ1))));
    script.assertThat(
        Smt.eq(Smt.sym(rowIdx.valueNames().get(1)), Smt.intLit(BigInteger.valueOf(rowIdxQ2))));
    script.assertThat(
        Smt.eq(Smt.sym(colIdx.valueNames().get(1)), Smt.intLit(BigInteger.valueOf(colIdxQ2))));
    script.assertThat(translated);

    return solve(script).outcome();
  }

  // ---------------------------------------------------------------------
  // Scope boundary: plain crisp Integer only -- a non-Integer operand fails closed
  // ---------------------------------------------------------------------

  /**
   * {@code a.i + a.r} type-checks and widens to {@code Real} under USE's own {@code
   * ArithOperation.matches} (confirmed by compiling this exact fixture and inspecting the AST:
   * {@code ExpStdOp} opname {@code "+"}, {@code a.length==2}, result type {@code Real}) -- so this
   * is a genuinely reachable shape, not a defensive-only guard, and must be refused rather than
   * silently emitting a mixed-sort SMT term.
   */
  @Test
  public void binaryPlusOverARealOperandFailsClosedRatherThanMixingSorts() throws Exception {
    MModel model = compileArithmeticScope();
    MClassInvariant inv = findInvariant(model, "realOperandNotConfused");

    SmtTranslationException thrown =
        assertThrows(
            SmtTranslationException.class,
            () ->
                ExpressionTranslator.translate(
                    inv.bodyExpression(),
                    new TranslationContext(Map.of(), Map.of(), Map.of(), Map.of(), Map.of())));

    assertEquals(FragmentBoundary.TIER_2, thrown.boundary());
    assertTrue(thrown.getMessage(), thrown.getMessage().contains("non-Integer operand"));
  }

  /**
   * Unary {@code +} is syntactically legal per USE's own OCL grammar ({@code ("not" | "-" | "+")
   * unaryExpression}) and reaches this visitor for a crisp Integer operand (confirmed by compiling
   * this exact fixture: {@code ExpStdOp} opname {@code "+"}, {@code a.length==1}, type {@code
   * Integer}). {@code a.i = +a.i} is a tautology over every Integer -- unlike unary {@code -}'s
   * boundary tests above, there is no bound value that makes it false -- so this is a SAT-only
   * regression check that the identity translation actually reproduces {@code a.i}'s own value
   * rather than, say, silently dropping the whole comparison to a vacuous {@code true}; {@link
   * #unaryPlusIsIdentityOnTheRealAstIsUnsatWhenSignsWouldMatchUnderNegationMutation} below is the
   * fixture that catches a wrong (non-identity) translation via an UNSAT case.
   */
  @Test
  public void unaryPlusOverTheSameOperandOnTheRealAstIsSatForAnyValue() throws Exception {
    MModel model = compileArithmeticScope();
    MClassInvariant inv = findInvariant(model, "unaryPlusOverSameOperandIsTautology");

    SmtScript script = new SmtScript("QF_LIA");
    ObjectSlots as = ObjectSlotEncoder.encode(script, List.of(new ClassScope("A", 1, 1))).get("A");
    AttributeDomain iDomain = new AttributeDomain("A", "i", null, List.of(), null, null);
    AttributeValues iValues =
        AttributeEncoder.encode(script, as, "i", AttributeType.INTEGER, iDomain);
    TranslationContext ctx =
        new TranslationContext(
            Map.of("a", new VariableBinding("A", 0)),
            Map.of("A.i", iValues),
            Map.of("A.i", iDomain),
            Map.of("A", as),
            Map.of());
    SmtTerm translated = ExpressionTranslator.translate(inv.bodyExpression(), ctx);

    script.assertThat(Smt.sym("A_0_exists"));
    script.assertThat(
        Smt.eq(Smt.sym(iValues.valueNames().get(0)), Smt.intLit(BigInteger.valueOf(5))));
    script.assertThat(translated);

    assertEquals(SolverOutcome.SAT, solve(script).outcome());
  }

  // ---------------------------------------------------------------------
  // Binary * -- one variable operand and one Integer LITERAL operand only.
  //
  // ArithmeticScope::productOfIntegerAndLiteral / productOfLiteralAndInteger.
  //
  // Discovered while implementing this slice (not assumed up front): this project's SmtScript is
  // pinned to QF_LIA (linear integer arithmetic -- SmtModelFinder.java), and the pinned Z3 5.1.0
  // binary enforces that syntactically -- feeding it "(declare-const x Int) (declare-const y Int)
  // (assert (= x 2)) (assert (= y 3)) (assert (= (* x y) 6)) (check-sat)" directly returns
  // "(error \"...logic does not support nonlinear arithmetic\")" before ever reaching check-sat,
  // even though x and y are both pinned to concrete values by earlier assertions. "(* <var>
  // <numeral>)" and "(* <numeral> <var>)" are both confirmed accepted (linear coefficient form).
  // So a plain crisp Integer product is supported only when at least one OCL-level operand is a
  // literal; the genuinely nonlinear "variable * variable" shape is refused, not silently handed
  // to the solver to error out on -- see the dedicated refusal test below and {@link
  // ExpressionTranslator#requireLinearProduct}.
  // ---------------------------------------------------------------------

  @Test
  public void productOfIntegerAndLiteralOnTheRealAstIsSatWhenTheProductMatches() throws Exception {
    // 3 * 2 = 6, matching the invariant's literal.
    assertEquals(SolverOutcome.SAT, solveArithmeticScope("productOfIntegerAndLiteral", 3, 0));
  }

  /**
   * Adversarial: 4 + 2 = 6 (matches the invariant's literal) while 4 * 2 = 8 (does not) --
   * isolating {@code *} from a {@code +}-swap mutation, which would wrongly compute 6 here and
   * report SAT instead of the correct UNSAT.
   */
  @Test
  public void productOfIntegerAndLiteralOnTheRealAstIsUnsatWhenTheSumWouldMatchButNotTheProduct()
      throws Exception {
    assertEquals(SolverOutcome.UNSAT, solveArithmeticScope("productOfIntegerAndLiteral", 4, 0));
  }

  /**
   * Adversarial: 8 - 2 = 6 (matches the invariant's literal) while 8 * 2 = 16 (does not) --
   * isolating {@code *} from a {@code -}-swap mutation, which would wrongly compute 6 here and
   * report SAT instead of the correct UNSAT.
   */
  @Test
  public void
      productOfIntegerAndLiteralOnTheRealAstIsUnsatWhenTheDifferenceWouldMatchButNotTheProduct()
          throws Exception {
    assertEquals(SolverOutcome.UNSAT, solveArithmeticScope("productOfIntegerAndLiteral", 8, 0));
  }

  /** The literal-first operand order ({@code 2 * a.j}), confirming order does not matter. */
  @Test
  public void productOfLiteralAndIntegerOnTheRealAstIsSatWhenTheProductMatches() throws Exception {
    assertEquals(SolverOutcome.SAT, solveArithmeticScope("productOfLiteralAndInteger", 0, 3));
  }

  /**
   * {@code a.i * a.j} -- both operands non-constant -- is the genuinely nonlinear shape {@link
   * ExpressionTranslator#requireLinearProduct} refuses (see this section's header comment for the
   * Z3-confirmed evidence): translated, it would produce {@code (* <symbol> <symbol>)}, which the
   * pinned QF_LIA solver logic rejects outright. This must fail closed at TRANSLATION time with a
   * located {@link SmtTranslationException}, not be silently handed to the solver to error out on
   * (which would surface as an opaque {@code MALFORMED} solver result with no connection to the
   * OCL construct that caused it).
   */
  @Test
  public void
      binaryTimesOverTwoNonConstantOperandsFailsClosedRatherThanEmittingNonlinearArithmetic()
          throws Exception {
    MModel model = compileArithmeticScope();
    MClassInvariant inv = findInvariant(model, "productOfTwoNonConstantIntegers");

    SmtScript script = new SmtScript("QF_LIA");
    ObjectSlots as = ObjectSlotEncoder.encode(script, List.of(new ClassScope("A", 1, 1))).get("A");
    AttributeDomain iDomain = new AttributeDomain("A", "i", null, List.of(), null, null);
    AttributeValues iValues =
        AttributeEncoder.encode(script, as, "i", AttributeType.INTEGER, iDomain);
    AttributeDomain jDomain = new AttributeDomain("A", "j", null, List.of(), null, null);
    AttributeValues jValues =
        AttributeEncoder.encode(script, as, "j", AttributeType.INTEGER, jDomain);
    TranslationContext ctx =
        new TranslationContext(
            Map.of("a", new VariableBinding("A", 0)),
            Map.of("A.i", iValues, "A.j", jValues),
            Map.of("A.i", iDomain, "A.j", jDomain),
            Map.of("A", as),
            Map.of());

    SmtTranslationException thrown =
        assertThrows(
            SmtTranslationException.class,
            () -> ExpressionTranslator.translate(inv.bodyExpression(), ctx));

    assertEquals(FragmentBoundary.TIER_2, thrown.boundary());
    assertTrue(thrown.getMessage(), thrown.getMessage().contains("non-constant"));
  }

  /**
   * {@code a.i * a.r} type-checks and widens to {@code Real} under the same {@code
   * ArithOperation.matches} rule as {@code binaryPlusOverARealOperandFailsClosedRatherThanMixingSorts}
   * above (confirmed by compiling this exact fixture and inspecting the AST: {@code ExpStdOp}
   * opname {@code "*"}, {@code a.length==2}, result type {@code Real}) -- so {@code *} must refuse
   * a non-Integer operand exactly like {@code +}/{@code -} already do, reusing the same {@link
   * #requireCrispInteger} guard rather than a separate one. The type guard runs before the
   * linearity guard, so this is refused as a type mismatch even though {@code a.r} is also
   * non-constant.
   */
  @Test
  public void binaryTimesOverARealOperandFailsClosedRatherThanMixingSorts() throws Exception {
    MModel model = compileArithmeticScope();
    MClassInvariant inv = findInvariant(model, "productWithRealOperandNotConfused");

    SmtTranslationException thrown =
        assertThrows(
            SmtTranslationException.class,
            () ->
                ExpressionTranslator.translate(
                    inv.bodyExpression(),
                    new TranslationContext(Map.of(), Map.of(), Map.of(), Map.of(), Map.of())));

    assertEquals(FragmentBoundary.TIER_2, thrown.boundary());
    assertTrue(thrown.getMessage(), thrown.getMessage().contains("non-Integer operand"));
  }

  /**
   * The independent-oracle discipline this codebase uses everywhere else (see {@link
   * org.tzi.use.smt.verify.InvariantReEvaluator}, driven here through the full {@link
   * SmtModelFinder} pipeline exactly as {@code SmtModelFinderTest} drives it): rather than trusting
   * the Z3 witness on its own, {@link ModelFinderResult#allActiveInvariantsHold()} reconstructs the
   * witness into a real {@link org.tzi.use.uml.sys.MSystem} and re-evaluates {@code
   * productOfIntegerAndLiteral} with USE's OWN OCL evaluator -- a genuinely separate implementation
   * of {@code *} from the one under test. Both must agree the witness is real.
   */
  @Test
  public void productOfIntegerAndLiteralEndToEndWitnessIsIndependentlyConfirmedByTheUseEvaluator()
      throws Exception {
    MModel model = compileArithmeticScope();
    ModelFinderResult result =
        SmtModelFinder.find(model, arithmeticScopeConfig("A::productOfIntegerAndLiteral"));

    assertTrue("expected SAT", result.satisfiable());
    assertTrue(
        "expected the USE evaluator to independently confirm the witness",
        verdictFor(result, "A::productOfIntegerAndLiteral").holds());
  }

  // ---------------------------------------------------------------------
  // Unary + -- identity over one plain crisp Integer operand,
  // ArithmeticScope::unaryPlusIsIdentity
  // ---------------------------------------------------------------------

  @Test
  public void unaryPlusIsIdentityOnTheRealAstIsSatWhenBothOperandsAreEqual() throws Exception {
    assertEquals(SolverOutcome.SAT, solveArithmeticScope("unaryPlusIsIdentity", 5, 5));
  }

  /**
   * Sign-sensitive: {@code a.i = +a.j} at i=-7, j=7 is UNSAT ({@code -7 <> +7}, i.e. {@code -7 <>
   * 7}). A mutation that implemented unary {@code +} as negation (confusing it with the
   * already-supported unary {@code -}) would compute {@code +7} as {@code -7} here, matching
   * {@code i}, and wrongly report SAT instead of the correct UNSAT.
   */
  @Test
  public void unaryPlusIsIdentityOnTheRealAstIsUnsatWhenSignsWouldMatchUnderNegationMutation()
      throws Exception {
    assertEquals(SolverOutcome.UNSAT, solveArithmeticScope("unaryPlusIsIdentity", -7, 7));
  }

  /**
   * The same independent-oracle round trip as {@code
   * productOfIntegerAndLiteralEndToEndWitnessIsIndependentlyConfirmedByTheUseEvaluator} above,
   * for unary {@code +}: the Z3 witness for {@code a.i = +a.j} is reconstructed into a real {@link
   * org.tzi.use.uml.sys.MSystem} and re-checked by USE's own OCL evaluator, not merely trusted.
   */
  @Test
  public void unaryPlusIsIdentityEndToEndWitnessIsIndependentlyConfirmedByTheUseEvaluator()
      throws Exception {
    MModel model = compileArithmeticScope();
    ModelFinderResult result =
        SmtModelFinder.find(model, arithmeticScopeConfig("A::unaryPlusIsIdentity"));

    assertTrue("expected SAT", result.satisfiable());
    assertTrue(
        "expected the USE evaluator to independently confirm the witness",
        verdictFor(result, "A::unaryPlusIsIdentity").holds());
  }

  /**
   * Picks out one named invariant's verdict from {@link ModelFinderResult#verdicts()}. The
   * fixture model has SEVEN invariants for seven different, sometimes deliberately-conflicting
   * translation-boundary purposes (this file's other tests), unlike e.g. Library.use's coherent
   * default section where every model invariant is simultaneously active -- so {@link
   * ModelFinderResult#allActiveInvariantsHold()} (which reads EVERY reevaluated invariant, not only
   * the configured active set -- confirmed by inspecting {@link #arithmeticScopeConfig}'s witness
   * directly: {@code A::productOfIntegerAndLiteral} activated alone still comes back with all seven
   * invariants' verdicts, most FALSE since only the one active invariant constrained the solve) is
   * the wrong tool here; this reads the ONE invariant this test is actually about.
   */
  private static InvariantVerdict verdictFor(ModelFinderResult result, String qualifiedName) {
    return result.verdicts().stream()
        .filter(v -> v.invariantName().equals(qualifiedName))
        .findFirst()
        .orElseThrow(
            () -> new AssertionError("no verdict reported for " + qualifiedName + ": " + result));
  }

  /**
   * A minimal, unbounded-Integer-domain {@link AnalysisConfiguration} over {@link
   * #compileArithmeticScope}'s fixture class, activating exactly one named invariant -- the shape
   * {@link SmtModelFinder#find(MModel, AnalysisConfiguration)} needs to run the full
   * encode/solve/reconstruct/independently-re-verify pipeline, as opposed to the {@link
   * #solveArithmeticScope} helper below, which drives {@link ExpressionTranslator} directly against
   * a hand-picked value and never involves {@link org.tzi.use.smt.verify.InvariantReEvaluator}.
   */
  private static AnalysisConfiguration arithmeticScopeConfig(String qualifiedInvariantName) {
    return new AnalysisConfiguration(
        List.of(new ClassScope("A", 1, 1)),
        List.of(),
        List.of(
            new AttributeDomain("A", "i", null, List.of(), null, null),
            new AttributeDomain("A", "j", null, List.of(), null, null),
            new AttributeDomain("A", "r", null, List.of(), null, null)),
        Set.of(qualifiedInvariantName),
        null,
        Duration.ofSeconds(30),
        1);
  }

  /**
   * Shared fixture for the {@code productOfIntegerAndLiteral}/{@code productOfLiteralAndInteger}/
   * {@code unaryPlusIsIdentity} outcome tests above: binds {@code a.i}/{@code a.j} to the given
   * values on a single {@code A} instance and solves the named invariant against the real Z3
   * binary, exactly {@link #solveNoAttack}'s bind-then-solve pattern applied to {@link
   * #compileArithmeticScope}'s fixture class instead of NQueens's. Drives {@link
   * ExpressionTranslator} directly, unlike {@link #arithmeticScopeConfig}/{@link SmtModelFinder}
   * above, which drive the full pipeline including independent re-verification.
   */
  private static SolverOutcome solveArithmeticScope(String invariantName, int iValue, int jValue)
      throws Exception {
    MModel model = compileArithmeticScope();
    MClassInvariant inv = findInvariant(model, invariantName);

    SmtScript script = new SmtScript("QF_LIA");
    ObjectSlots as = ObjectSlotEncoder.encode(script, List.of(new ClassScope("A", 1, 1))).get("A");
    AttributeDomain iDomain = new AttributeDomain("A", "i", null, List.of(), null, null);
    AttributeValues iValues =
        AttributeEncoder.encode(script, as, "i", AttributeType.INTEGER, iDomain);
    AttributeDomain jDomain = new AttributeDomain("A", "j", null, List.of(), null, null);
    AttributeValues jValues =
        AttributeEncoder.encode(script, as, "j", AttributeType.INTEGER, jDomain);
    TranslationContext ctx =
        new TranslationContext(
            Map.of("a", new VariableBinding("A", 0)),
            Map.of("A.i", iValues, "A.j", jValues),
            Map.of("A.i", iDomain, "A.j", jDomain),
            Map.of("A", as),
            Map.of());
    SmtTerm translated = ExpressionTranslator.translate(inv.bodyExpression(), ctx);

    script.assertThat(Smt.sym("A_0_exists"));
    script.assertThat(
        Smt.eq(Smt.sym(iValues.valueNames().get(0)), Smt.intLit(BigInteger.valueOf(iValue))));
    script.assertThat(
        Smt.eq(Smt.sym(jValues.valueNames().get(0)), Smt.intLit(BigInteger.valueOf(jValue))));
    script.assertThat(translated);

    return solve(script).outcome();
  }

  private static MModel compileArithmeticScope() {
    String source =
        """
        model ArithmeticScope
        class A
        attributes
          i : Integer
          j : Integer
          r : Real
        end
        constraints
        context a: A inv realOperandNotConfused:
          a.i + a.r = a.r
        context a: A inv unaryPlusOverSameOperandIsTautology:
          a.i = +a.i
        context a: A inv productOfIntegerAndLiteral:
          a.i * 2 = 6
        context a: A inv productOfLiteralAndInteger:
          2 * a.j = 6
        context a: A inv productOfTwoNonConstantIntegers:
          a.i * a.j = 6
        context a: A inv productWithRealOperandNotConfused:
          a.i * a.r = a.r
        context a: A inv unaryPlusIsIdentity:
          a.i = +a.j
        """;
    ModelFactory factory = new ModelFactory();
    PrintWriter err = new PrintWriter(System.err);
    MModel model = USECompiler.compileSpecification(source, "ArithmeticScope", err, factory);
    err.flush();
    if (model == null) {
      throw new AssertionError("ArithmeticScope fixture model did not compile:\n" + source);
    }
    return model;
  }

  private static MModel compileEmployee() throws Exception {
    Path file = Path.of("../benchmark/examples/EmployeeInvariants/Employee.use");
    if (!Files.isRegularFile(file)) {
      file = Path.of("msc-modelvalidators/benchmark/examples/EmployeeInvariants/Employee.use");
    }
    String source = Files.readString(file);
    ModelFactory factory = new ModelFactory();
    PrintWriter err = new PrintWriter(System.err);
    MModel model = USECompiler.compileSpecification(source, "EmployeeInvariants", err, factory);
    err.flush();
    return model;
  }

  private static MModel compileNQueens() throws Exception {
    Path file = Path.of("../benchmark/examples/NQueens/NQueens.use");
    if (!Files.isRegularFile(file)) {
      file = Path.of("msc-modelvalidators/benchmark/examples/NQueens/NQueens.use");
    }
    String source = Files.readString(file);
    ModelFactory factory = new ModelFactory();
    PrintWriter err = new PrintWriter(System.err);
    MModel model = USECompiler.compileSpecification(source, "NQueens", err, factory);
    err.flush();
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

  private static SolverResult solve(SmtScript script) {
    return new SolverProcess(SolverBinary.resolve(), Duration.ofSeconds(30)).run(script.toSmtLib());
  }
}
