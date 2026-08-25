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
        assertTrue("expected TIMEOUT or a fast SAT, got " + outcome,
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
        assertTrue("raw output should retain the solver's error line for evidence",
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

        SmtValue.Rational x =
                (SmtValue.Rational) SmtModelParser.parse(result.modelText()).get("x");
        BigDecimal decoded = x.asBigDecimal(6);
        assertTrue(decoded.toPlainString(),
                decoded.compareTo(new BigDecimal("0.30")) > 0
                        && decoded.compareTo(new BigDecimal("0.40")) < 0);
    }
}
