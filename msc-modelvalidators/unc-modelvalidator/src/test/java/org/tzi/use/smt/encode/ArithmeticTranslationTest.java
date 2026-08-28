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
import org.junit.Test;
import org.tzi.use.parser.use.USECompiler;
import org.tzi.use.smt.config.AssociationScope;
import org.tzi.use.smt.config.AttributeDomain;
import org.tzi.use.smt.config.ClassScope;
import org.tzi.use.smt.solver.*;
import org.tzi.use.uml.mm.MClassInvariant;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;

/**
 * Binary {@code +}/{@code -} and unary {@code -} over plain crisp Integer operands --
 * THESIS_SMT_MODEL_FINDER_PLAN.md 7.1 Tier 2's "integer arithmetic and comparisons", confirmed by
 * the manager's re-ranked ledger as the sole remaining refusal blocking BOTH {@code
 * EmployeeInvariants}/{@code EmployeeInvariants-UNSAT} (real invariant {@code NotBelowMinusOne:
 * self.salary > -1}) and {@code NQueens}/{@code NQueens-UNSAT} (real invariant {@code noAttack:
 * q1<>q2 implies (q1.row.idx+q1.col.idx <> q2.row.idx+q2.col.idx and q1.row.idx-q1.col.idx <>
 * q2.row.idx-q2.col.idx)}).
 *
 * <p>AST evidence (compiling the real models and inspecting the parsed tree directly, not
 * inferred): {@code self.salary > -1} reaches this visitor as {@code ExpStdOp} opname {@code "-"}
 * with {@code a.length==1} wrapping {@code ExpConstInteger(1)} -- USE does NOT fold a literal unary
 * minus into a negative constant at parse time, so unary minus is a real, reachable shape. {@code
 * q1.row.idx+q1.col.idx} / {@code q1.row.idx-q1.col.idx} reach it as {@code ExpStdOp} opname {@code
 * "+"}/{@code "-"} with {@code a.length==2}, each operand an already-supported navigated-attribute
 * {@code ExpAttrOp}.
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
   * unaryExpression}) and DOES reach this visitor for a crisp Integer operand (confirmed by
   * compiling this exact fixture: {@code ExpStdOp} opname {@code "+"}, {@code a.length==1}, type
   * {@code Integer}) -- but it is not needed by either target invariant and is deliberately out of
   * this slice's scope, so it must fail closed rather than being silently treated as a no-op or
   * folded into the binary case.
   */
  @Test
  public void unaryPlusFailsClosedRatherThanBeingSilentlyGeneralized() throws Exception {
    MModel model = compileArithmeticScope();
    MClassInvariant inv = findInvariant(model, "unaryPlusNotSupported");

    // A real (non-empty) context: the invariant's LEFT operand (a.i, an ordinary attribute
    // access) is evaluated before the right, so it must resolve successfully for the failure to
    // come from the RIGHT operand's unary "+" -- the thing this test actually targets -- rather
    // than from an unrelated missing-binding error on the left.
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

    SmtTranslationException thrown =
        assertThrows(
            SmtTranslationException.class,
            () -> ExpressionTranslator.translate(inv.bodyExpression(), ctx));

    assertEquals(FragmentBoundary.TIER_2, thrown.boundary());
    assertTrue(thrown.getMessage(), thrown.getMessage().contains("operator '+'"));
  }

  private static MModel compileArithmeticScope() {
    String source =
        """
        model ArithmeticScope
        class A
        attributes
          i : Integer
          r : Real
        end
        constraints
        context a: A inv realOperandNotConfused:
          a.i + a.r = a.r
        context a: A inv unaryPlusNotSupported:
          a.i = +a.i
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
