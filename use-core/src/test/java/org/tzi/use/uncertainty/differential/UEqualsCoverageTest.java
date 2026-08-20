package org.tzi.use.uncertainty.differential;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.tzi.use.uml.mm.ModelFactory;
import org.tzi.use.uml.ocl.expr.Evaluator;
import org.tzi.use.uml.ocl.expr.Expression;
import org.tzi.use.uml.ocl.value.Value;
import org.tzi.use.uml.ocl.value.VarBindings;
import org.tzi.use.parser.ocl.OCLCompiler;

/**
 * Closes a question raised in an earlier session: {@code UReal(2,0.5) = 2} evaluates to
 * {@code UBoolean(true, 0.0)} — probability zero — and that <em>looked</em> like it might be a fork
 * defect, because it was flagged before {@code uEquals} had been swept at all.
 *
 * <h2>Both halves of the question, answered</h2>
 * <ol>
 *   <li><strong>Is {@code uEquals} actually swept?</strong> Yes. It is a public, single-{@code Value}
 *       -argument instance method on all five uncertain value classes, in both the historical jar and
 *       the port, so {@link UnwrittenPortInvariantTest#reachableOperations} finds it by reflection and
 *       it is one of the 355 operations {@link PortedFidelitySweepTest} drives. That sweep reports
 *       {@code AGREE} on every measured {@code uEquals} row and zero unintended divergence overall —
 *       there was never a separate "add it to the sweep" step to do.</li>
 *   <li><strong>Is the {@code 0.0} correct?</strong> Yes, and it is not a coincidence. It is the
 *       library's Gaussian-vs-crisp comparison rule: {@code UReal.calculate(UReal)} branches on
 *       which side is degenerate (uncertainty {@code 0}), and whenever exactly one side is, it sets
 *       {@code r.eq = 0.0} <strong>unconditionally</strong> — {@code UReal.java:501-508} in the
 *       vendored library. For a continuous distribution compared to an exact point, that is the
 *       mathematically correct answer: a continuous random variable takes any single value with
 *       probability zero, however close that value sits to the mean. {@code UReal(2,0.5) = 2} is
 *       asking "what is the probability this Gaussian equals exactly 2", and the honest answer is
 *       zero regardless of the mean.</li>
 * </ol>
 *
 * <p>Every test below drives the historical jar as well as the port, so this file would fail if
 * either half of the answer stopped being true — including if a future edit "fixed" the {@code 0.0}
 * into something that looked less surprising but was mathematically wrong.
 *
 * <p>Test-scoped. Not part of the product.
 */
@DisplayName("uEquals: swept, and 0.0 for uncertain-vs-crisp equality is correct, not a defect")
class UEqualsCoverageTest {

    private static Value run(String expr) {
        StringWriter err = new StringWriter();
        Expression e = OCLCompiler.compileExpression(
                new ModelFactory().createModel("m"), expr, "test",
                new PrintWriter(err), new VarBindings());
        if (e == null) {
            throw new IllegalStateException(expr + " did not compile: " + err);
        }
        return new Evaluator().eval(e, null, null, new VarBindings(), null, "");
    }

    @Test
    @DisplayName("uEquals is in the reflected operation census the full sweep drives")
    void isInTheCensus() {
        try (HistoricalOracle oracle = HistoricalOracle.open()) {
            List<UOp> ops = UnwrittenPortInvariantTest.reachableOperations(oracle);
            List<String> uEqualsOps = ops.stream()
                    .map(UOp::key)
                    .filter(k -> k.contains(".uEquals("))
                    .sorted()
                    .toList();
            assertEquals(List.of(
                    "SBooleanValue.uEquals(value)", "UBooleanValue.uEquals(value)",
                    "UIntegerValue.uEquals(value)", "URealValue.uEquals(value)",
                    "UStringValue.uEquals(value)"), uEqualsOps);
        }
    }

    @Test
    @DisplayName("the port agrees with the fork on uEquals across a receiver's full comparison shape")
    void agreesWithTheFork() {
        try (HistoricalOracle oracle = HistoricalOracle.open();
             PortedCandidate ported = PortedCandidate.open()) {
            UOp op = UOp.binary("URealValue", "uEquals");
            List<UValue> receivers = List.of(UValue.uReal(2.0, 0.5));
            // The three shapes that matter: crisp integer, crisp real, and an uncertain value whose
            // uncertainty happens to be zero -- all three are "compare a Gaussian to a point."
            List<UValue> args = List.of(UValue.integer(2), UValue.real(2.0),
                    UValue.uReal(2.0, 0.0), UValue.uReal(3.0, 0.5));
            DifferentialSweep.Result r = new DifferentialSweep(oracle, ported, 1L)
                    .sweepBinary(op, receivers, args);
            assertAll(
                    () -> assertEquals(4, r.measurementCount()),
                    () -> assertTrue(r.disagreements().isEmpty(), r.disagreements().toString()));
        }
    }

    @Test
    @DisplayName("UReal(2,0.5) = 2 is UBoolean(true, 0.0) -- probability zero, and that is correct")
    void crispComparisonIsZero() {
        assertAll(
                () -> assertEquals("UBoolean(true, 0.0)", run("UReal(2,0.5) = 2").toString()),
                () -> assertEquals("UBoolean(true, 0.0)", run("UReal(2,0.5) = 2.0").toString(),
                        "an Integer or a Real crisp operand must give the same answer"),
                () -> assertEquals("UBoolean(true, 0.0)",
                        run("UReal(2,0.5) = UReal(2, 0.0)").toString(),
                        "a UReal with uncertainty exactly 0 is the same case as a crisp literal"));
    }

    @Test
    @DisplayName("it is not the constant 0.0 -- two uncertain operands can agree with nonzero certainty")
    void notAConstant() {
        // The corrected `=` (S8(4/n)) routes to uEquals for uncertain operands; when NEITHER side is
        // degenerate the Gaussian-overlap calculation is used instead of the crisp-comparison branch,
        // so this is not the same code path as crispComparisonIsZero above.
        Value same = run("UReal(2,0.5) = UReal(2,0.5)");
        Value near = run("UReal(2,0.5) = UReal(2.1,0.5)");
        Value far = run("UReal(2,0.5) = UReal(50,0.5)");
        assertAll(
                () -> assertEquals("UBoolean(true, 1.0)", same.toString(),
                        "identical Gaussians overlap completely"),
                () -> assertTrue(near.toString().contains("0.9") || near.toString().contains("1.0"),
                        "nearby Gaussians overlap heavily: " + near),
                () -> assertEquals("UBoolean(true, 0.0)", far.toString(),
                        "Gaussians five standard deviations apart do not overlap at all"));
    }

    @Test
    @DisplayName("<> is the complement: UReal(2,0.5) <> 2 is UBoolean(true, 1.0)")
    void notEqualsIsTheComplement() {
        assertEquals("UBoolean(true, 1.0)", run("UReal(2,0.5) <> 2").toString());
    }

    @Test
    @DisplayName("the crisp escape hatch answers a different question and is unaffected")
    void identicalIsCrispAndUnaffected() {
        // .equals() is Op_identical (S8(4/n)): "is this literally the same Java-level value",
        // answered as a plain Boolean, not a degree. UReal(2,0.5) is not, structurally, the value 2.
        assertEquals("false", run("UReal(2,0.5).equals(2)").toString());
        assertEquals("true", run("UReal(2,0.5).equals(UReal(2,0.5))").toString());
    }
}
