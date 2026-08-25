package org.tzi.use.smt.solver;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;

public class SolverProcessTest {

  private SolverProcess solver;

  @Before
  public void setUp() {
    solver = new SolverProcess(SolverBinary.resolve(), Duration.ofSeconds(30));
  }

  @Test
  public void satisfiableScriptReportsSat() {
    SmtScript script = new SmtScript("QF_LIRA");
    script.declareConst("x", SmtSort.REAL);
    script.assertThat(Smt.app(">", Smt.sym("x"), Smt.realLit(new BigDecimal("0.30"))));

    SolverResult result = solver.run(script.toSmtLib());

    assertEquals(SolverOutcome.SAT, result.outcome());
    assertTrue(result.modelText(), result.modelText().contains("define-fun x"));
  }

  @Test
  public void unsatisfiableScriptReportsUnsat() {
    SmtScript script = new SmtScript("QF_LIRA");
    script.declareConst("x", SmtSort.REAL);
    script.assertThat(Smt.app(">", Smt.sym("x"), Smt.realLit(new BigDecimal("1.0"))));
    script.assertThat(Smt.app("<", Smt.sym("x"), Smt.realLit(new BigDecimal("0.0"))));

    assertEquals(SolverOutcome.UNSAT, solver.run(script.toSmtLib()).outcome());
  }

  @Test
  public void malformedInputIsReportedAsMalformedNotAsUnknown() {
    SolverResult result = solver.run("(this is not valid smt-lib");
    assertEquals(SolverOutcome.MALFORMED, result.outcome());
  }

  /** Guards the timeout outcome against a blocking read before {@code waitFor}. */
  @Test(timeout = 60_000)
  public void aSolverThatExceedsTheDeadlineIsReportedAsTimeout() {
    SolverProcess impatient = new SolverProcess(SolverBinary.resolve(), Duration.ofMillis(1));
    SmtScript script = new SmtScript("QF_LIRA");
    for (int i = 0; i < 60; i++) {
      script.declareConst("x" + i, SmtSort.INT);
    }
    List<SmtTerm> distinct = new ArrayList<>();
    for (int i = 0; i < 60; i++) {
      for (int j = i + 1; j < 60; j++) {
        distinct.add(Smt.not(Smt.eq(Smt.sym("x" + i), Smt.sym("x" + j))));
      }
    }
    script.assertThat(Smt.and(distinct));
    for (int i = 0; i < 60; i++) {
      script.assertThat(Smt.app(">", Smt.sym("x" + i), Smt.intLit(BigInteger.ZERO)));
      script.assertThat(Smt.app("<", Smt.sym("x" + i), Smt.intLit(BigInteger.valueOf(60))));
    }

    SolverOutcome outcome = impatient.run(script.toSmtLib()).outcome();
    assertTrue(
        "expected TIMEOUT or a fast SAT, got " + outcome,
        outcome == SolverOutcome.TIMEOUT || outcome == SolverOutcome.SAT);
  }

  @Test
  public void unsatFollowedByAModelNotAvailableErrorIsStillUnsat() {
    SmtScript script = new SmtScript("QF_LIRA");
    script.declareConst("x", SmtSort.REAL);
    script.assertThat(Smt.app(">", Smt.sym("x"), Smt.realLit(new BigDecimal("1.0"))));
    script.assertThat(Smt.app("<", Smt.sym("x"), Smt.realLit(new BigDecimal("0.0"))));

    SolverResult result = solver.run(script.toSmtLib());

    assertEquals(SolverOutcome.UNSAT, result.outcome());
    assertTrue(
        "raw output should retain the solver's error line for evidence",
        result.rawOutput().contains("model is not available"));
  }

  @Test
  public void resultCarriesRawOutputForEvidenceCapture() {
    SmtScript script = new SmtScript("QF_LIRA");
    script.declareConst("p", SmtSort.BOOL);
    script.assertThat(Smt.sym("p"));

    SolverResult result = solver.run(script.toSmtLib());

    assertTrue(result.rawOutput().contains("sat"));
    assertTrue(result.millis() >= 0);
  }

  @Test
  public void solvedValuesDecodeIntoUsableNumbers() {
    SmtScript script = new SmtScript("QF_LIRA");
    script.declareConst("x", SmtSort.REAL);
    script.assertThat(Smt.app(">", Smt.sym("x"), Smt.realLit(new BigDecimal("0.30"))));
    script.assertThat(Smt.app("<", Smt.sym("x"), Smt.realLit(new BigDecimal("0.40"))));

    SolverResult result = solver.run(script.toSmtLib());
    assertEquals(SolverOutcome.SAT, result.outcome());

    SmtValue.Rational x = (SmtValue.Rational) SmtModelParser.parse(result.modelText()).get("x");
    BigDecimal decoded = x.asBigDecimal(6);
    assertTrue(
        decoded.toPlainString(),
        decoded.compareTo(new BigDecimal("0.30")) > 0
            && decoded.compareTo(new BigDecimal("0.40")) < 0);
  }

  @Test
  public void persistentModeReportsTheSameOutcomesAsOneShot() {
    try (SolverProcess persistent =
        SolverProcess.persistent(SolverBinary.resolve(), Duration.ofSeconds(30))) {
      SmtScript sat = new SmtScript("QF_LIRA");
      sat.declareConst("x", SmtSort.REAL);
      sat.assertThat(Smt.app(">", Smt.sym("x"), Smt.realLit(new BigDecimal("0.30"))));
      assertEquals(SolverOutcome.SAT, persistent.run(sat.toSmtLib()).outcome());

      SmtScript unsat = new SmtScript("QF_LIRA");
      unsat.declareConst("x", SmtSort.REAL);
      unsat.assertThat(Smt.app(">", Smt.sym("x"), Smt.realLit(new BigDecimal("1.0"))));
      unsat.assertThat(Smt.app("<", Smt.sym("x"), Smt.realLit(new BigDecimal("0.0"))));
      assertEquals(SolverOutcome.UNSAT, persistent.run(unsat.toSmtLib()).outcome());

      // A syntactically COMPLETE but invalid command, not a truncated/unbalanced one: Z3's
      // interactive parser blocks waiting for a closing paren on unbalanced input, since more text
      // could still be coming on stdin -- confirmed by direct reproduction (deliberately, once),
      // not assumed. Genuinely truncated input is out of scope for persistent mode's own testing
      // for exactly that reason; it is also not a real production input shape (SmtScript.toSmtLib()
      // always emits well-formed, balanced text) -- see this class's own javadoc.
      assertEquals(SolverOutcome.MALFORMED, persistent.run("(check-sta)").outcome());
    }
  }

  /**
   * The genuinely discriminating case: proves {@code (reset)} between calls actually isolates two
   * unrelated scripts sharing one persistent process, by deliberately reusing the SAME const name
   * "x" with contradictory meanings across three consecutive calls -- if reset were a no-op (or
   * missing), the second/third call would either see a stale declaration error or a leaked
   * assertion from the first call, not the fresh, correct answer each call actually asks for.
   */
  @Test
  public void reusingTheSameConstNameAcrossCallsIsIsolatedByReset() {
    try (SolverProcess persistent =
        SolverProcess.persistent(SolverBinary.resolve(), Duration.ofSeconds(30))) {
      SmtScript first = new SmtScript("QF_LIA");
      first.declareConst("x", SmtSort.INT);
      first.assertThat(Smt.app("=", Smt.sym("x"), Smt.intLit(BigInteger.valueOf(5))));
      SolverResult firstResult = persistent.run(first.toSmtLib());
      assertEquals(SolverOutcome.SAT, firstResult.outcome());
      assertEquals(
          BigInteger.valueOf(5),
          ((SmtValue.Int) SmtModelParser.parse(firstResult.modelText()).get("x")).value());

      SmtScript second = new SmtScript("QF_LIA");
      second.declareConst("x", SmtSort.INT);
      second.assertThat(Smt.app(">", Smt.sym("x"), Smt.intLit(BigInteger.ZERO)));
      second.assertThat(Smt.app("<", Smt.sym("x"), Smt.intLit(BigInteger.ONE)));
      assertEquals(SolverOutcome.UNSAT, persistent.run(second.toSmtLib()).outcome());

      SmtScript third = new SmtScript("QF_LIA");
      third.declareConst("x", SmtSort.INT);
      third.assertThat(Smt.app("=", Smt.sym("x"), Smt.intLit(BigInteger.valueOf(42))));
      SolverResult thirdResult = persistent.run(third.toSmtLib());
      assertEquals(SolverOutcome.SAT, thirdResult.outcome());
      assertEquals(
          BigInteger.valueOf(42),
          ((SmtValue.Int) SmtModelParser.parse(thirdResult.modelText()).get("x")).value());
    }
  }

  /**
   * Proves Z3's own {@code (set-option :timeout ...)} genuinely bounds one call without killing the
   * process: a call hard enough to exceed a tiny budget must come back UNKNOWN (Z3's own
   * self-reported give-up, not a Java-side TIMEOUT), and the SAME instance must still answer a
   * trivial follow-up call correctly afterward.
   */
  @Test(timeout = 30_000)
  public void aCallThatExceedsItsOwnBudgetReportsUnknownAndTheProcessSurvives() {
    try (SolverProcess persistent =
        SolverProcess.persistent(SolverBinary.resolve(), Duration.ofMillis(100))) {
      SmtScript hard = new SmtScript("QF_NIA");
      hard.declareConst("a", SmtSort.INT);
      hard.declareConst("b", SmtSort.INT);
      hard.assertThat(Smt.app(">", Smt.sym("a"), Smt.intLit(BigInteger.ONE)));
      hard.assertThat(Smt.app(">", Smt.sym("b"), Smt.intLit(BigInteger.ONE)));
      hard.assertThat(
          Smt.app(
              "=",
              Smt.app("*", Smt.sym("a"), Smt.sym("b")),
              Smt.intLit(new BigInteger("1000000007000000009"))));
      assertEquals(SolverOutcome.UNKNOWN, persistent.run(hard.toSmtLib()).outcome());

      SmtScript trivial = new SmtScript("QF_LIA");
      trivial.declareConst("y", SmtSort.INT);
      trivial.assertThat(Smt.app("=", Smt.sym("y"), Smt.intLit(BigInteger.ONE)));
      assertEquals(SolverOutcome.SAT, persistent.run(trivial.toSmtLib()).outcome());
    }
  }

  @Test
  public void closeOnAOneShotInstanceIsANoOp() {
    SolverProcess oneShot = new SolverProcess(SolverBinary.resolve(), Duration.ofSeconds(30));
    oneShot.close();
  }
}
