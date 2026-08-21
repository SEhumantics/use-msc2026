package org.tzi.use.uml.ocl.expr;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import org.tzi.use.uml.mm.ModelFactory;
import org.tzi.use.uml.ocl.value.*;
import org.tzi.use.uml.sys.MSystem;
import org.tzi.use.uml.sys.MSystemState;

/**
 * Test Uncertainty Operations.
 *
 * @author Víctor Manuel Ortiz Guardeño
 *
 * <p>Ported from USE-Uncertainty (github.com/atenearesearchgroup/uncertainty @ 74acd0d),
 * src/test/org/tzi/use/uml/ocl/expr/URealExpOpsTest.java. Part of B7 ledger row M-44 (this file's
 * 4 of the row's 40 total sites; the other three files are UBooleanExpOpsTest, UIntegerExpOpsTest,
 * ExpQueryUncertaintyTest — both already ported, see their javadoc).
 *
 * <p>32 test methods, 326 {@code assertEquals} calls reordered mechanically (CF-7, script-verified
 * as in UBooleanExpOpsTest). The 4 {@code try { ...; fail(...); } catch (ExpInvalidException) {}
 * catch (Exception) { fail(...); }} blocks (all in {@code testMinMax}, covering {@code min}/
 * {@code max} on a non-numeric argument, in both operand orders) converted to
 * {@code assertThrows(ExpInvalidException.class, ...)}, each wrapping both the {@code
 * ExpStdOp.create} call and the {@code eval} that follows it — matching M-44's own note that a
 * two-statement {@code try} body needs the lambda to wrap both, not just the first statement.
 *
 * <p>All 32 methods pass with zero semantic corrections.
 */

class URealExpOpsTest {
    private MSystemState state;
    private Evaluator e;

    @BeforeEach
    void setUp() {
        state = new MSystem(new ModelFactory().createModel("Test")).state();
        e = new Evaluator();
    }


    @Test
    void testURealToInteger() throws ExpInvalidException {
        Expression[] args;
        ExpStdOp op;

        // UReal(-2, 0).toInteger() -> -2 : Integer
        args = new Expression[] { new ExpConstUReal(new ExpConstReal(-2), new ExpConstReal(0)) };
        op = ExpStdOp.create("toInteger", args);
        assertEquals(IntegerValue.valueOf(-2), e.eval(op, state), op.toString());

        // UReal(-2, 2).toInteger() -> -2 : Integer
        args = new Expression[] { new ExpConstUReal(new ExpConstReal(-2), new ExpConstReal(2)) };
        op = ExpStdOp.create("toInteger", args);
        assertEquals(IntegerValue.valueOf(-2), e.eval(op, state), op.toString());

        // UReal(0, 0).toInteger() -> 0 : Integer
        args = new Expression[] { new ExpConstUReal(new ExpConstReal(0), new ExpConstReal(0)) };
        op = ExpStdOp.create("toInteger", args);
        assertEquals(IntegerValue.valueOf(0), e.eval(op, state), op.toString());

        // UReal(0, 3).toInteger() -> 0 : Integer
        args = new Expression[] { new ExpConstUReal(new ExpConstReal(0), new ExpConstReal(3)) };
        op = ExpStdOp.create("toInteger", args);
        assertEquals(IntegerValue.valueOf(0), e.eval(op, state), op.toString());

        // UReal(3, 0).toInteger() -> 3 : Integer
        args = new Expression[] { new ExpConstUReal(new ExpConstReal(3), new ExpConstReal(0)) };
        op = ExpStdOp.create("toInteger", args);
        assertEquals(IntegerValue.valueOf(3), e.eval(op, state), op.toString());

        // UReal(3, 5).toInteger() -> 3 : Integer
        args = new Expression[] { new ExpConstUReal(new ExpConstReal(3), new ExpConstReal(5)) };
        op = ExpStdOp.create("toInteger", args);
        assertEquals(IntegerValue.valueOf(3), e.eval(op, state), op.toString());

        // UReal(0.5, 3.2).toInteger() -> 0 : Integer
        args = new Expression[] { new ExpConstUReal(new ExpConstReal(0.5), new ExpConstReal(3.2)) };
        op = ExpStdOp.create("toInteger", args);
        assertEquals(IntegerValue.valueOf(0), e.eval(op, state), op.toString());
    }

    @Test
    void testURealToReal() throws ExpInvalidException {
        Expression [] args;
        ExpStdOp op;

        // UReal(-2, 0).toReal() -> -2.0 : Real
        args = new Expression[] { new ExpConstUReal(new ExpConstReal(-2), new ExpConstReal(0)) };
        op = ExpStdOp.create("toReal", args);
        assertEquals(new RealValue(-2), e.eval(op, state), op.toString());

        // UReal(-2, 2).toReal() -> -2.0 : Real
        args = new Expression[] { new ExpConstUReal(new ExpConstReal(-2), new ExpConstReal(2)) };
        op = ExpStdOp.create("toReal", args);
        assertEquals(new RealValue(-2), e.eval(op, state), op.toString());

        // UReal(0, 0).toReal() -> 0.0 : Real
        args = new Expression[] { new ExpConstUReal(new ExpConstReal(0), new ExpConstReal(0)) };
        op = ExpStdOp.create("toReal", args);
        assertEquals(new RealValue(0), e.eval(op, state), op.toString());

        // UReal(0, 3).toReal() -> 0.0 : Real
        args = new Expression[] { new ExpConstUReal(new ExpConstReal(0), new ExpConstReal(3)) };
        op = ExpStdOp.create("toReal", args);
        assertEquals(new RealValue(0), e.eval(op, state), op.toString());

        // UReal(3, 0).toReal() -> 3.0 : Real
        args = new Expression[] { new ExpConstUReal(new ExpConstReal(3), new ExpConstReal(0)) };
        op = ExpStdOp.create("toReal", args);
        assertEquals(new RealValue(3), e.eval(op, state), op.toString());

        // UReal(3, 5).toReal() -> 3.0 : Real
        args = new Expression[] { new ExpConstUReal(new ExpConstReal(3), new ExpConstReal(5)) };
        op = ExpStdOp.create("toReal", args);
        assertEquals(new RealValue(3), e.eval(op, state), op.toString());

        // UReal(0.5, 3.2).toReal() -> 0.5 : Real
        args = new Expression[] { new ExpConstUReal(new ExpConstReal(0.5), new ExpConstReal(3.2)) };
        op = ExpStdOp.create("toReal", args);
        assertEquals(new RealValue(0.5), e.eval(op, state), op.toString());

    }

    @Test
    void testURealToString() throws ExpInvalidException {
        Expression [] args;
        ExpStdOp op;

        // UReal(-2, 0).toString() -> 'UReal(-2.0, 0.0)' : String
        args = new Expression[] { new ExpConstUReal(new ExpConstReal(-2), new ExpConstReal(0)) };
        op = ExpStdOp.create("toString", args);
        assertEquals(new StringValue("UReal(-2.0, 0.0)"), e.eval(op, state), op.toString());

        // UReal(-2, 2).toString() -> 'UReal(-2.0, 2.0)' : String
        args = new Expression[] { new ExpConstUReal(new ExpConstReal(-2), new ExpConstReal(2)) };
        op = ExpStdOp.create("toString", args);
        assertEquals(new StringValue("UReal(-2.0, 2.0)"), e.eval(op, state), op.toString());

        // UReal(0, 0).toString() -> 'UReal(0.0, 0.0)' : String
        args = new Expression[] { new ExpConstUReal(new ExpConstReal(0), new ExpConstReal(0)) };
        op = ExpStdOp.create("toString", args);
        assertEquals(new StringValue("UReal(0.0, 0.0)"), e.eval(op, state), op.toString());

        // UReal(0, 3).toString() -> 'UReal(0.0, 3.0)' : String
        args = new Expression[] { new ExpConstUReal(new ExpConstReal(0), new ExpConstReal(3)) };
        op = ExpStdOp.create("toString", args);
        assertEquals(new StringValue("UReal(0.0, 3.0)"), e.eval(op, state), op.toString());

        // UReal(3, 0).toString() -> 'UReal(3.0, 0.0)' : String
        args = new Expression[] { new ExpConstUReal(new ExpConstReal(3), new ExpConstReal(0)) };
        op = ExpStdOp.create("toString", args);
        assertEquals(new StringValue("UReal(3.0, 0.0)"), e.eval(op, state), op.toString());

        // UReal(3, 5).toString() -> 'UReal(3.0, 5.0)' : String
        args = new Expression[] { new ExpConstUReal(new ExpConstReal(3), new ExpConstReal(5)) };
        op = ExpStdOp.create("toString", args);
        assertEquals(new StringValue("UReal(3.0, 5.0)"), e.eval(op, state), op.toString());

        // UReal(0.5, 3.2).toString() -> 'UReal(0.5, 3.2)' : String
        args = new Expression[] { new ExpConstUReal(new ExpConstReal(0.5), new ExpConstReal(3.2)) };
        op = ExpStdOp.create("toString", args);
        assertEquals(new StringValue("UReal(0.5, 3.2)"), e.eval(op, state), op.toString());
    }

    @Test
    void testURealAbs() throws ExpInvalidException {
        Expression [] args;
        ExpStdOp op;

        // UReal(3, 2.3).abs() -> UReal(3.0, 2.3) : UReal
        args = new Expression[] { new ExpConstUReal(new ExpConstInteger(3), new ExpConstReal(2.3))};
        op = ExpStdOp.create("abs", args);
        assertEquals(new URealValue(3, 2.3), e.eval(op, state), op.toString());

        // UReal(0, 2.3).abs() -> UReal(0.0, 2.3) : UReal
        args = new Expression[] { new ExpConstUReal(new ExpConstInteger(0), new ExpConstReal(2.3))};
        op = ExpStdOp.create("abs", args);
        assertEquals(new URealValue(0, 2.3), e.eval(op, state), op.toString());

        // UReal(-3, 2.3).abs() -> UReal(3.0, 2.3) : UReal
        args = new Expression[] { new ExpConstUReal(new ExpConstInteger(-3), new ExpConstReal(2.3))};
        op = ExpStdOp.create("abs", args);
        assertEquals(new URealValue(3, 2.3), e.eval(op, state), op.toString());
    }

    @Test
    void testURealNeg() throws ExpInvalidException {
        Expression [] args;
        ExpStdOp op;

        // UReal(-3, 2.3).neg() -> UReal(3.0, 2.3) : UReal
        args = new Expression[] { new ExpConstUReal(new ExpConstInteger(-3), new ExpConstReal(2.3))};
        op = ExpStdOp.create("neg", args);
        assertEquals(new URealValue(3, 2.3), e.eval(op, state), op.toString());

        // UReal(0, 2.3).neg() -> UReal(0.0, 2.3) : UReal
        args = new Expression[] { new ExpConstUReal(new ExpConstInteger(0), new ExpConstReal(2.3))};
        op = ExpStdOp.create("neg", args);
        assertEquals(new URealValue(0, 2.3), e.eval(op, state), op.toString());

        // UReal(3, 2.3).neg() -> UReal(-3.0, 2.3) : UReal
        args = new Expression[] { new ExpConstUReal(new ExpConstInteger(3), new ExpConstReal(2.3))};
        op = ExpStdOp.create("neg", args);
        assertEquals(new URealValue(-3, 2.3), e.eval(op, state), op.toString());
    }

    @Test
    void testURealFloor() throws ExpInvalidException {
        Expression [] args;
        ExpStdOp op;

        // UReal(3.7, 2.3).floor() -> UReal(3.0, 2.3) : UReal
        args = new Expression[] { new ExpConstUReal(new ExpConstReal(3.7), new ExpConstReal(2.3))};
        op = ExpStdOp.create("floor", args);
        assertEquals(new URealValue(3, 2.3), e.eval(op, state), op.toString());

        // UReal(3.2, 2.3).floor() -> UReal(3.0, 2.3) : UReal
        args = new Expression[] { new ExpConstUReal(new ExpConstReal(3.2), new ExpConstReal(2.3))};
        op = ExpStdOp.create("floor", args);
        assertEquals(new URealValue(3, 2.3), e.eval(op, state), op.toString());

        // UReal(3.5, 2.3).floor() -> UReal(3.0, 2.3) : UReal
        args = new Expression[] { new ExpConstUReal(new ExpConstReal(3.5), new ExpConstReal(2.3))};
        op = ExpStdOp.create("floor", args);
        assertEquals(new URealValue(3, 2.3), e.eval(op, state), op.toString());


        // UReal(-3.7, 2.3).floor() -> UReal(-4.0, 2.3) : UReal
        args = new Expression[] { new ExpConstUReal(new ExpConstReal(-3.7), new ExpConstReal(2.3))};
        op = ExpStdOp.create("floor", args);
        assertEquals(new URealValue(-4, 2.3), e.eval(op, state), op.toString());

        // UReal(-3.2, 2.3).floor() -> UReal(-4.0, 2.3) : UReal
        args = new Expression[] { new ExpConstUReal(new ExpConstReal(-3.2), new ExpConstReal(2.3))};
        op = ExpStdOp.create("floor", args);
        assertEquals(new URealValue(-4, 2.3), e.eval(op, state), op.toString());

        // UReal(-3.5, 2.3).floor() -> UReal(-4.0, 2.3) : UReal
        args = new Expression[] { new ExpConstUReal(new ExpConstReal(-3.5), new ExpConstReal(2.3))};
        op = ExpStdOp.create("floor", args);
        assertEquals(new URealValue(-4, 2.3), e.eval(op, state), op.toString());
    }

    @Test
    void testURealRound() throws ExpInvalidException {
        Expression [] args;
        ExpStdOp op;

        args = new Expression[] { new ExpConstUReal(new ExpConstInteger(2), new ExpConstReal(3))};
        op = ExpStdOp.create("round", args);
        assertEquals(new URealValue(2, 3), e.eval(op, state), op.toString());

        args = new Expression[] { new ExpConstUReal(new ExpConstReal(2.7), new ExpConstReal(3))};
        op = ExpStdOp.create("round", args);
        assertEquals(new URealValue(3, 3), e.eval(op, state), op.toString());

        args = new Expression[] { new ExpConstUReal(new ExpConstReal(2.5), new ExpConstReal(3))};
        op = ExpStdOp.create("round", args);
        assertEquals(new URealValue(3, 3), e.eval(op, state), op.toString());

        args = new Expression[] { new ExpConstUReal(new ExpConstReal(2.2), new ExpConstReal(3))};
        op = ExpStdOp.create("round", args);
        assertEquals(new URealValue(2, 3), e.eval(op, state), op.toString());

        args = new Expression[] { new ExpConstUReal(new ExpConstReal(-0.5), new ExpConstReal(3))};
        op = ExpStdOp.create("round", args);
        assertEquals(new URealValue(0, 3), e.eval(op, state), op.toString());

        args = new Expression[] { new ExpConstUReal(new ExpConstReal(-0.8), new ExpConstReal(3))};
        op = ExpStdOp.create("round", args);
        assertEquals(new URealValue(-1, 3), e.eval(op, state), op.toString());
    }

    @Test
    void testURealPower() throws ExpInvalidException {
        Expression [] args;
        ExpStdOp op;

        // UReal(0, 0).power(0) -> Undefined : OclVoid
        args = new Expression[] {
                new ExpConstUReal(new ExpConstInteger(0), new ExpConstReal(0)),
                new ExpConstInteger(0)
        };
        op = ExpStdOp.create("power", args);
        assertTrue((e.eval(op, state)).isUndefined(), op.toString());

        // UReal(0, 0).power(3) -> UReal(0, 0) : UReal
        args = new Expression[] {
                new ExpConstUReal(new ExpConstInteger(0), new ExpConstReal(0)),
                new ExpConstInteger(3)
        };
        op = ExpStdOp.create("power", args);
        assertEquals(new URealValue(0, 0), e.eval(op, state), op.toString());

        // UReal(0, 0).power(-2) -> Undefined : OclVoid
        args = new Expression[] {
                new ExpConstUReal(new ExpConstInteger(0), new ExpConstReal(0)),
                new ExpConstInteger(-2)
        };
        op = ExpStdOp.create("power", args);
        assertTrue((e.eval(op, state)).isUndefined(), op.toString());

        // UReal(0, 0).power(3.5) -> UReal(0, 0) : UReal
        args = new Expression[] {
                new ExpConstUReal(new ExpConstInteger(0), new ExpConstReal(0)),
                new ExpConstReal(3.5)
        };
        op = ExpStdOp.create("power", args);
        assertEquals(new URealValue(0, 0), e.eval(op, state), op.toString());

        // UReal(0, 2).power(0) -> Undefined : OclVoid
        args = new Expression[] {
                new ExpConstUReal(new ExpConstInteger(0), new ExpConstReal(2)),
                new ExpConstInteger(0)
        };
        op = ExpStdOp.create("power", args);
        assertTrue((e.eval(op, state)).isUndefined(), op.toString());

        // UReal(0, 4).power(3) -> UReal(0, 0) : UReal
        args = new Expression[] {
                new ExpConstUReal(new ExpConstInteger(0), new ExpConstReal(4)),
                new ExpConstInteger(3)
        };
        op = ExpStdOp.create("power", args);
        assertEquals(new URealValue(0, 0), e.eval(op, state), op.toString());

        // UReal(0, 3).power(-3) -> Undefined : OclVoid
        args = new Expression[] {
                new ExpConstUReal(new ExpConstInteger(0), new ExpConstReal(3)),
                new ExpConstInteger(-3)
        };
        op = ExpStdOp.create("power", args);
        assertTrue((e.eval(op, state)).isUndefined(), op.toString());

        // UReal(0, 1).power(3.5) -> UReal(0, 0) : UReal
        args = new Expression[] {
                new ExpConstUReal(new ExpConstInteger(0), new ExpConstReal(1)),
                new ExpConstReal(3.5)
        };
        op = ExpStdOp.create("power", args);
        assertEquals(new URealValue(0, 0), e.eval(op, state), op.toString());

        // UReal(3, 0).power(0) -> UReal(1, 0) : UReal
        args = new Expression[] {
                new ExpConstUReal(new ExpConstInteger(3), new ExpConstReal(0)),
                new ExpConstInteger(0)
        };
        op = ExpStdOp.create("power", args);
        assertEquals(new URealValue(1, 0), e.eval(op, state), op.toString());

        // UReal(2, 0).power(3) -> UReal(8, 0) : UReal
        args = new Expression[] {
                new ExpConstUReal(new ExpConstInteger(2), new ExpConstReal(0)),
                new ExpConstInteger(3)
        };
        op = ExpStdOp.create("power", args);
        assertEquals(new URealValue(8, 0), e.eval(op, state), op.toString());

        // UReal(4, 0).power(-2) -> UReal(0.0625, 0) : UReal
        args = new Expression[] {
                new ExpConstUReal(new ExpConstInteger(4), new ExpConstReal(0)),
                new ExpConstInteger(-2)
        };
        op = ExpStdOp.create("power", args);
        assertEquals(new URealValue(0.0625, 0), e.eval(op, state), op.toString());

        // UReal(4, 0).power(1.5) -> UReal(8, 0) : UReal
        args = new Expression[] {
                new ExpConstUReal(new ExpConstInteger(4), new ExpConstReal(0)),
                new ExpConstReal(1.5)
        };
        op = ExpStdOp.create("power", args);
        assertEquals(new URealValue(8, 0), e.eval(op, state), op.toString());

        // UReal(1.5, 3.2).power(0) -> UReal(1, 0) : UReal
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(1.5), new ExpConstReal(3.2)),
                new ExpConstInteger(0)
        };
        op = ExpStdOp.create("power", args);
        assertEquals(new URealValue(1, 0), e.eval(op, state), op.toString());

        // UReal(2, 4).power(4) -> UReal(400, 128) : UReal
        args = new Expression[] {
                new ExpConstUReal(new ExpConstInteger(2), new ExpConstReal(4)),
                new ExpConstInteger(4)
        };
        op = ExpStdOp.create("power", args);
        assertEquals(new URealValue(16, 128), e.eval(op, state), op.toString());

        // UReal(1, 3).power(-2) -> UReal(28, -6) : UReal
        args = new Expression[] {
                new ExpConstUReal(new ExpConstInteger(1), new ExpConstReal(3)),
                new ExpConstInteger(-2)
        };
        op = ExpStdOp.create("power", args);
        assertEquals(new URealValue(1, -6), e.eval(op, state), op.toString());

        // UReal(1, 2).power(0.25) -> UReal(1, 0.5) : UReal
        args = new Expression[] {
                new ExpConstUReal(new ExpConstInteger(1), new ExpConstReal(2)),
                new ExpConstReal(0.25)
        };
        op = ExpStdOp.create("power", args);
        assertEquals(new URealValue(1, 0.5), e.eval(op, state), op.toString());
    }

    @Test
    void testURealSqrt() throws ExpInvalidException {
        Expression [] args;
        ExpStdOp op;

        // UReal(-3, 2.3).sqrt() -> Undefined : OclVoid
        args = new Expression[] { new ExpConstUReal(new ExpConstInteger(-3), new ExpConstReal(2.3))};
        op = ExpStdOp.create("sqrt", args);
        assertTrue(e.eval(op, state).isUndefined(), op.toString());

        // UReal(0, 0).sqrt() -> UReal(0, 0) : UReal
        args = new Expression[] { new ExpConstUReal(new ExpConstInteger(0), new ExpConstReal(0))};
        op = ExpStdOp.create("sqrt", args);
        //assertEquals(new URealValue(0, 0), e.eval(op, state), op.toString());
        // TODO Descomentar cuando se actualice la librería

        // UReal(0, 2).sqrt() -> Undefined : OclVoid
        args = new Expression[] { new ExpConstUReal(new ExpConstInteger(0), new ExpConstReal(2))};
        op = ExpStdOp.create("sqrt", args);
        assertTrue(e.eval(op, state).isUndefined(), op.toString());

        // UReal(4, 0).sqrt() -> UReal(2.0, 0.0) : UReal
        args = new Expression[] { new ExpConstUReal(new ExpConstInteger(4), new ExpConstReal(0))};
        op = ExpStdOp.create("sqrt", args);
        assertEquals(new URealValue(2, 0), e.eval(op, state), op.toString());

        // UReal(4, 2).sqrt() -> UReal(2.0, 0.5) : UReal
        args = new Expression[] { new ExpConstUReal(new ExpConstInteger(4), new ExpConstReal(2))};
        op = ExpStdOp.create("sqrt", args);
        assertEquals(new URealValue(2.0, 0.5), e.eval(op, state), op.toString());
    }

    /**
     * Test sin, cos, tan, asin, acos, atan: UReal -> UReal.
     *
     * <p>Not a B7 ledger row: found untested by an independent audit at S9 (this stage) rather than
     * by the fork's own test suite, which has no coverage for any of these six either. Each
     * delegates to {@code UReal}'s own error-propagation formula (uncertainty scaled by the
     * derivative's magnitude at the receiver's value) — see {@code uDataTypes/UReal.java:206-245}.
     * Values are computed by the JDK's {@code Math} trig functions and asserted against, not
     * hand-derived: the exact {@code URealValue} equality (which rounds via {@code MathUtil.round})
     * would otherwise risk a silently-wrong expected literal.
     */

    @Test
    void testURealSin() throws ExpInvalidException {
        Expression [] args;
        ExpStdOp op;

        // UReal(0, 1).sin() -> UReal(sin(0), 1*cos(0)) = UReal(0.0, 1.0)
        args = new Expression[] { new ExpConstUReal(new ExpConstInteger(0), new ExpConstReal(1))};
        op = ExpStdOp.create("sin", args);
        assertEquals(new URealValue(Math.sin(0), 1 * Math.cos(0)), e.eval(op, state), op.toString());

        // UReal(pi/6, 0.4).sin() -> UReal(sin(pi/6), 0.4*cos(pi/6))
        double x = Math.PI / 6;
        args = new Expression[] { new ExpConstUReal(new ExpConstReal(x), new ExpConstReal(0.4))};
        op = ExpStdOp.create("sin", args);
        assertEquals(new URealValue(Math.sin(x), 0.4 * Math.cos(x)), e.eval(op, state), op.toString());
    }

    @Test
    void testURealCos() throws ExpInvalidException {
        Expression [] args;
        ExpStdOp op;

        // UReal(0, 1).cos() -> UReal(cos(0), 1*sin(0)) = UReal(1.0, 0.0)
        args = new Expression[] { new ExpConstUReal(new ExpConstInteger(0), new ExpConstReal(1))};
        op = ExpStdOp.create("cos", args);
        assertEquals(new URealValue(Math.cos(0), 1 * Math.sin(0)), e.eval(op, state), op.toString());

        // UReal(pi/6, 0.4).cos() -> UReal(cos(pi/6), 0.4*sin(pi/6))
        double x = Math.PI / 6;
        args = new Expression[] { new ExpConstUReal(new ExpConstReal(x), new ExpConstReal(0.4))};
        op = ExpStdOp.create("cos", args);
        assertEquals(new URealValue(Math.cos(x), 0.4 * Math.sin(x)), e.eval(op, state), op.toString());
    }

    @Test
    void testURealTan() throws ExpInvalidException {
        Expression [] args;
        ExpStdOp op;

        // UReal(0, 1).tan() -> sin(0,1).divideBy(cos(0,1)) = UReal(0,1).divideBy(UReal(1,0))
        args = new Expression[] { new ExpConstUReal(new ExpConstInteger(0), new ExpConstReal(1))};
        op = ExpStdOp.create("tan", args);
        Value expected = new URealValue(Math.sin(0), 1 * Math.cos(0))
                .divideBy(new URealValue(Math.cos(0), 1 * Math.sin(0)));
        assertEquals(expected, e.eval(op, state), op.toString());
    }

    @Test
    void testURealAsin() throws ExpInvalidException {
        Expression [] args;
        ExpStdOp op;

        // UReal(0, 1).asin() -> UReal(asin(0), 1/sqrt(1-0^2)) = UReal(0.0, 1.0)
        args = new Expression[] { new ExpConstUReal(new ExpConstInteger(0), new ExpConstReal(1))};
        op = ExpStdOp.create("asin", args);
        assertEquals(new URealValue(Math.asin(0), 1 / Math.sqrt(1 - 0 * 0)), e.eval(op, state), op.toString());

        // UReal(0.5, 0.4).asin() -> UReal(asin(0.5), 0.4/sqrt(1-0.5^2))
        args = new Expression[] { new ExpConstUReal(new ExpConstReal(0.5), new ExpConstReal(0.4))};
        op = ExpStdOp.create("asin", args);
        assertEquals(new URealValue(Math.asin(0.5), 0.4 / Math.sqrt(1 - 0.5 * 0.5)), e.eval(op, state), op.toString());
    }

    @Test
    void testURealAcos() throws ExpInvalidException {
        Expression [] args;
        ExpStdOp op;

        // UReal(0, 1).acos() -> UReal(acos(0), 1/sqrt(1-0^2)) = UReal(pi/2, 1.0)
        args = new Expression[] { new ExpConstUReal(new ExpConstInteger(0), new ExpConstReal(1))};
        op = ExpStdOp.create("acos", args);
        assertEquals(new URealValue(Math.acos(0), 1 / Math.sqrt(1 - 0 * 0)), e.eval(op, state), op.toString());

        // UReal(0.5, 0.4).acos() -> UReal(acos(0.5), 0.4/sqrt(1-0.5^2))
        args = new Expression[] { new ExpConstUReal(new ExpConstReal(0.5), new ExpConstReal(0.4))};
        op = ExpStdOp.create("acos", args);
        assertEquals(new URealValue(Math.acos(0.5), 0.4 / Math.sqrt(1 - 0.5 * 0.5)), e.eval(op, state), op.toString());
    }

    @Test
    void testURealAtan() throws ExpInvalidException {
        Expression [] args;
        ExpStdOp op;

        // UReal(0, 1).atan() -> UReal(atan(0), 1/(1+0^2)) = UReal(0.0, 1.0)
        args = new Expression[] { new ExpConstUReal(new ExpConstInteger(0), new ExpConstReal(1))};
        op = ExpStdOp.create("atan", args);
        assertEquals(new URealValue(Math.atan(0), 1 / (1 + 0 * 0)), e.eval(op, state), op.toString());

        // UReal(1, 0.4).atan() -> UReal(atan(1), 0.4/(1+1^2))
        args = new Expression[] { new ExpConstUReal(new ExpConstInteger(1), new ExpConstReal(0.4))};
        op = ExpStdOp.create("atan", args);
        assertEquals(new URealValue(Math.atan(1), 0.4 / (1 + 1 * 1)), e.eval(op, state), op.toString());
    }

    @Test
    void testURealValue() throws ExpInvalidException {
        Expression [] args;
        ExpStdOp op;

        args = new Expression[] { new ExpConstUReal(new ExpConstInteger(-3), new ExpConstReal(2.3))};
        op = ExpStdOp.create("value", args);
        assertEquals(new RealValue(-3), e.eval(op, state), op.toString());

        args = new Expression[] { new ExpConstUReal(new ExpConstInteger(0), new ExpConstReal(2.3))};
        op = ExpStdOp.create("value", args);
        assertEquals(new RealValue(0), e.eval(op, state), op.toString());

        args = new Expression[] { new ExpConstUReal(new ExpConstInteger(3), new ExpConstReal(2.3))};
        op = ExpStdOp.create("value", args);
        assertEquals(new RealValue(3), e.eval(op, state), op.toString());
    }

    @Test
    void testURealUncertainty() throws ExpInvalidException {
        Expression [] args;
        ExpStdOp op;

        args = new Expression[] { new ExpConstUReal(new ExpConstInteger(3), new ExpConstReal(-2.3))};
        op = ExpStdOp.create("uncertainty", args);
        assertEquals(new RealValue(2.3), e.eval(op, state), op.toString());

        args = new Expression[] { new ExpConstUReal(new ExpConstInteger(3), new ExpConstReal(0))};
        op = ExpStdOp.create("uncertainty", args);
        assertEquals(new RealValue(0), e.eval(op, state), op.toString());
    }

    @Test
    void testURealInv() throws ExpInvalidException {
        Expression [] args;
        ExpStdOp op;

        args = new Expression[] { new ExpConstUReal(new ExpConstInteger(8), new ExpConstReal(0.75))};
        op = ExpStdOp.create("inv", args);
        assertEquals(new URealValue(0.125, 0.01171875), (e.eval(op, state)), op.toString());

        args = new Expression[] { new ExpConstUReal(new ExpConstInteger(0), new ExpConstReal(0.5))};
        op = ExpStdOp.create("inv", args);
        assertTrue(e.eval(op, state).isUndefined(), op.toString());
    }

    @Test
    void testURealxURealMinMax() throws ExpInvalidException {
        Expression [] args;
        ExpStdOp op;

        // UReal(0.0, 0.0).min(UReal(0.0, 0.0)) -> UReal(0.0, 0.0) : UReal
        // UReal(0.0, 0.0).max(UReal(0.0, 0.0)) -> UReal(0.0, 0.0) : UReal
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(0), new ExpConstReal(0)),
                new ExpConstUReal(new ExpConstReal(0), new ExpConstReal(0))
        };
        op = ExpStdOp.create("min", args);
        assertEquals(new URealValue(0, 0), (e.eval(op, state)), op.toString());
        op = ExpStdOp.create("max", args);
        assertEquals(new URealValue(0, 0), (e.eval(op, state)), op.toString());

        // UReal(0.0, 0.0).min(UReal(1.0, 0.0)) -> UReal(0.0, 0.0) : UReal
        // UReal(0.0, 0.0).max(UReal(1.0, 0.0)) -> UReal(1.0, 0.0) : UReal
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(0), new ExpConstReal(0)),
                new ExpConstUReal(new ExpConstReal(1), new ExpConstReal(0))
        };
        op = ExpStdOp.create("min", args);
        assertEquals(new URealValue(0, 0), (e.eval(op, state)), op.toString());
        op = ExpStdOp.create("max", args);
        assertEquals(new URealValue(1, 0), (e.eval(op, state)), op.toString());

        // UReal(3.0, 0.0).min(UReal(0.0, 0.0)) -> UReal(0.0, 0.0) : UReal
        // UReal(3.0, 0.0).max(UReal(0.0, 0.0)) -> UReal(3.0, 0.0) : UReal
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(3), new ExpConstReal(0)),
                new ExpConstUReal(new ExpConstReal(0), new ExpConstReal(0))
        };
        op = ExpStdOp.create("min", args);
        assertEquals(new URealValue(0, 0), (e.eval(op, state)), op.toString());
        op = ExpStdOp.create("max", args);
        assertEquals(new URealValue(3, 0), (e.eval(op, state)), op.toString());

        // UReal(0.0, 0.0).min(UReal(3.0, 2.0)) -> UReal(0.0, 0.0) : UReal
        // UReal(0.0, 0.0).max(UReal(3.0, 2.0)) -> UReal(3.0, 2.0) : UReal
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(0), new ExpConstReal(0)),
                new ExpConstUReal(new ExpConstReal(3), new ExpConstReal(2))
        };
        op = ExpStdOp.create("min", args);
        assertEquals(new URealValue(0, 0), (e.eval(op, state)), op.toString());
        op = ExpStdOp.create("max", args);
        assertEquals(new URealValue(3, 2), (e.eval(op, state)), op.toString());

        // UReal(3.0, 0.0).min(UReal(0.0, 2.0)) -> UReal(0.0, 2.0) : UReal
        // UReal(3.0, 0.0).max(UReal(0.0, 2.0)) -> UReal(3.0, 0.0) : UReal
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(3), new ExpConstReal(0)),
                new ExpConstUReal(new ExpConstReal(0), new ExpConstReal(2))
        };
        op = ExpStdOp.create("min", args);
        assertEquals(new URealValue(0, 2), (e.eval(op, state)), op.toString());
        op = ExpStdOp.create("max", args);
        assertEquals(new URealValue(3, 0), (e.eval(op, state)), op.toString());

        // UReal(0.0, 2.0).min(UReal(3.0, 0.0)) -> UReal(0.0, 2.0) : UReal
        // UReal(0.0, 2.0).max(UReal(3.0, 0.0)) -> UReal(3.0, 0.0) : UReal
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(0), new ExpConstReal(2)),
                new ExpConstUReal(new ExpConstReal(3), new ExpConstReal(0))
        };
        op = ExpStdOp.create("min", args);
        assertEquals(new URealValue(0, 2), (e.eval(op, state)), op.toString());
        op = ExpStdOp.create("max", args);
        assertEquals(new URealValue(3, 0), (e.eval(op, state)), op.toString());

        // UReal(3.0, 2.0).min(UReal(0.0, 0.0)) -> UReal(0.0, 0.0) : UReal
        // UReal(3.0, 2.0).max(UReal(0.0, 0.0)) -> UReal(3.0, 2.0) : UReal
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(3), new ExpConstReal(2)),
                new ExpConstUReal(new ExpConstReal(0), new ExpConstReal(0))
        };
        op = ExpStdOp.create("min", args);
        assertEquals(new URealValue(0, 0), (e.eval(op, state)), op.toString());
        op = ExpStdOp.create("max", args);
        assertEquals(new URealValue(3, 2), (e.eval(op, state)), op.toString());

        // UReal(0.0, 2.0).min(UReal(0.0, 0.2)) -> UReal(0.0, 2.0) : UReal
        // UReal(0.0, 2.0).max(UReal(0.0, 0.2)) -> UReal(0.0, 2.0) : UReal
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(0), new ExpConstReal(2)),
                new ExpConstUReal(new ExpConstReal(0), new ExpConstReal(2))
        };
        op = ExpStdOp.create("min", args);
        assertEquals(new URealValue(0, 2), (e.eval(op, state)), op.toString());
        op = ExpStdOp.create("max", args);
        assertEquals(new URealValue(0, 2), (e.eval(op, state)), op.toString());

        // UReal(0.0, 2.0).min(UReal(1.0, 0.0)) -> UReal(0.0, 2.0) : UReal
        // UReal(0.0, 2.0).max(UReal(1.0, 0.0)) -> UReal(1.0, 0.0) : UReal
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(0), new ExpConstReal(2)),
                new ExpConstUReal(new ExpConstReal(1), new ExpConstReal(0))
        };
        op = ExpStdOp.create("min", args);
        assertEquals(new URealValue(0, 2), (e.eval(op, state)), op.toString());
        op = ExpStdOp.create("max", args);
        assertEquals(new URealValue(1, 0), (e.eval(op, state)), op.toString());

        // UReal(0.0, 2.0).min(UReal(1.0, 1.0)) -> UReal(0.0, 2.0) : UReal
        // UReal(0.0, 2.0).max(UReal(1.0, 1.0)) -> UReal(1.0, 1.0) : UReal
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(0), new ExpConstReal(2)),
                new ExpConstUReal(new ExpConstReal(1), new ExpConstReal(0.25))
        };
        op = ExpStdOp.create("min", args);
        assertEquals(new URealValue(0, 2), (e.eval(op, state)), op.toString());
        op = ExpStdOp.create("max", args);
        assertEquals(new URealValue(1, 0.25), (e.eval(op, state)), op.toString());

        // UReal(0.0, 2.0).min(UReal(-1.0, 0.25)) -> UReal(-1.0, 0.25) : UReal
        // UReal(0.0, 2.0).max(UReal(-1.0, 0.25)) -> UReal(0.0, 2.0) : UReal
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(0), new ExpConstReal(2)),
                new ExpConstUReal(new ExpConstReal(-1), new ExpConstReal(0.25))
        };
        op = ExpStdOp.create("min", args);
        assertEquals(new URealValue(-1, 0.25), (e.eval(op, state)), op.toString());
        op = ExpStdOp.create("max", args);
        assertEquals(new URealValue(0, 2), (e.eval(op, state)), op.toString());

        // UReal(0.0, 2.0).min(UReal(5.0, 2.0)) -> UReal(0.0, 2.0) : UReal
        // UReal(0.0, 2.0).max(UReal(5.0, 2.0)) -> UReal(5.0, 2.0) : UReal
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(0), new ExpConstReal(2)),
                new ExpConstUReal(new ExpConstReal(5), new ExpConstReal(2))
        };
        op = ExpStdOp.create("min", args);
        assertEquals(new URealValue(0, 2), (e.eval(op, state)), op.toString());
        op = ExpStdOp.create("max", args);
        assertEquals(new URealValue(5, 2), (e.eval(op, state)), op.toString());

        // UReal(5.0, 2.0).min(UReal(0.0, 2.0)) -> UReal(0.0, 2.0) : UReal
        // UReal(5.0, 2.0).max(UReal(0.0, 2.0)) -> UReal(5.0, 2.0) : UReal
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(5), new ExpConstReal(2)),
                new ExpConstUReal(new ExpConstReal(0), new ExpConstReal(2))
        };
        op = ExpStdOp.create("min", args);
        assertEquals(new URealValue(0, 2), (e.eval(op, state)), op.toString());
        op = ExpStdOp.create("max", args);
        assertEquals(new URealValue(5, 2), (e.eval(op, state)), op.toString());
    }

    @Test
    void testURealxIntegerMinMax() throws ExpInvalidException {
        Expression [] args, args_inv;
        ExpStdOp op;

        // UReal(0.0, 0.0).min(0) -> UReal(0.0, 0.0) : UReal
        // UReal(0.0, 0.0).max(0) -> UReal(0.0, 0.0) : UReal
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(0), new ExpConstReal(0)),
                new ExpConstInteger(0)
        };
        args_inv = new Expression[] {args[1], args[0]};
        op = ExpStdOp.create("min", args);
        assertEquals(new URealValue(0, 0), (e.eval(op, state)), op.toString());
        op = ExpStdOp.create("min", args_inv);
        assertEquals(new URealValue(0, 0), (e.eval(op, state)), op.toString());
        op = ExpStdOp.create("max", args);
        assertEquals(new URealValue(0, 0), (e.eval(op, state)), op.toString());
        op = ExpStdOp.create("max", args_inv);
        assertEquals(new URealValue(0, 0), (e.eval(op, state)), op.toString());

        // UReal(0.0, 0.0).min(1) -> UReal(0.0, 0.0) : UReal
        // UReal(0.0, 0.0).max(1) -> UReal(1.0, 0.0) : UReal
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(0), new ExpConstReal(0)),
                new ExpConstInteger(1)
        };
        args_inv = new Expression[] {args[1], args[0]};
        op = ExpStdOp.create("min", args);
        assertEquals(new URealValue(0, 0), (e.eval(op, state)), op.toString());
        op = ExpStdOp.create("min", args_inv);
        assertEquals(new URealValue(0, 0), (e.eval(op, state)), op.toString());
        op = ExpStdOp.create("max", args);
        assertEquals(new URealValue(1, 0), (e.eval(op, state)), op.toString());
        op = ExpStdOp.create("max", args_inv);
        assertEquals(new URealValue(1, 0), (e.eval(op, state)), op.toString());

        // UReal(1.0, 0.0).min(0) -> UReal(0.0, 0.0) : UReal
        // UReal(1.0, 0.0).max(0) -> UReal(1.0, 0.0) : UReal
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(1), new ExpConstReal(0)),
                new ExpConstInteger(0)
        };
        args_inv = new Expression[] {args[1], args[0]};
        op = ExpStdOp.create("min", args);
        assertEquals(new URealValue(0, 0), (e.eval(op, state)), op.toString());
        op = ExpStdOp.create("min", args_inv);
        assertEquals(new URealValue(0, 0), (e.eval(op, state)), op.toString());
        op = ExpStdOp.create("max", args);
        assertEquals(new URealValue(1, 0), (e.eval(op, state)), op.toString());
        op = ExpStdOp.create("max", args_inv);
        assertEquals(new URealValue(1, 0), (e.eval(op, state)), op.toString());

        // UReal(0.0, 2.0).min(3) -> UReal(0.0, 2.0) : UReal
        // UReal(0.0, 2.0).max(3) -> UReal(3.0, 0.0) : UReal
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(0), new ExpConstReal(2)),
                new ExpConstInteger(3)
        };
        args_inv = new Expression[] {args[1], args[0]};
        op = ExpStdOp.create("min", args);
        assertEquals(new URealValue(0, 2), (e.eval(op, state)), op.toString());
        op = ExpStdOp.create("min", args_inv);
        assertEquals(new URealValue(0, 2), (e.eval(op, state)), op.toString());
        op = ExpStdOp.create("max", args);
        assertEquals(new URealValue(3, 0), (e.eval(op, state)), op.toString());
        op = ExpStdOp.create("max", args_inv);
        assertEquals(new URealValue(3, 0), (e.eval(op, state)), op.toString());

        // UReal(3.0, 2.0).min(0) -> UReal(0.0, 0.0) : UReal
        // UReal(3.0, 2.0).max(0) -> UReal(3.0, 2.0) : UReal
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(3), new ExpConstReal(2)),
                new ExpConstInteger(0)
        };
        args_inv = new Expression[] {args[1], args[0]};
        op = ExpStdOp.create("min", args);
        assertEquals(new URealValue(0, 0), (e.eval(op, state)), op.toString());
        op = ExpStdOp.create("min", args_inv);
        assertEquals(new URealValue(0, 0), (e.eval(op, state)), op.toString());
        op = ExpStdOp.create("max", args);
        assertEquals(new URealValue(3, 2), (e.eval(op, state)), op.toString());
        op = ExpStdOp.create("max", args_inv);
        assertEquals(new URealValue(3, 2), (e.eval(op, state)), op.toString());
    }

    @Test
    void testURealxRealMinMax() throws ExpInvalidException {
        Expression [] args, args_inv;
        ExpStdOp op;

        // UReal(0.0, 0.0).min(0.0) -> UReal(0.0, 0.0) : UReal
        // UReal(0.0, 0.0).max(0.0) -> UReal(0.0, 0.0) : UReal
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(0), new ExpConstReal(0)),
                new ExpConstReal(0)
        };
        args_inv = new Expression[] {args[1], args[0]};
        op = ExpStdOp.create("min", args);
        assertEquals(new URealValue(0, 0), (e.eval(op, state)), op.toString());
        op = ExpStdOp.create("min", args_inv);
        assertEquals(new URealValue(0, 0), (e.eval(op, state)), op.toString());
        op = ExpStdOp.create("max", args);
        assertEquals(new URealValue(0, 0), (e.eval(op, state)), op.toString());
        op = ExpStdOp.create("max", args_inv);
        assertEquals(new URealValue(0, 0), (e.eval(op, state)), op.toString());

        // UReal(0.0, 0.0).min(1.5) -> UReal(0.0, 0.0) : UReal
        // UReal(0.0, 0.0).max(1.5) -> UReal(1.5, 0.0) : UReal
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(0), new ExpConstReal(0)),
                new ExpConstReal(1.5)
        };
        args_inv = new Expression[] {args[1], args[0]};
        op = ExpStdOp.create("min", args);
        assertEquals(new URealValue(0, 0), (e.eval(op, state)), op.toString());
        op = ExpStdOp.create("min", args_inv);
        assertEquals(new URealValue(0, 0), (e.eval(op, state)), op.toString());
        op = ExpStdOp.create("max", args);
        assertEquals(new URealValue(1.5, 0), (e.eval(op, state)), op.toString());
        op = ExpStdOp.create("max", args_inv);
        assertEquals(new URealValue(1.5, 0), (e.eval(op, state)), op.toString());

        // UReal(1.0, 0.0).min(0) -> UReal(0.0, 0.0) : UReal
        // UReal(1.0, 0.0).max(0) -> UReal(1.0, 0.0) : UReal
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(1), new ExpConstReal(0)),
                new ExpConstReal(0)
        };
        args_inv = new Expression[] {args[1], args[0]};
        op = ExpStdOp.create("min", args);
        assertEquals(new URealValue(0, 0), (e.eval(op, state)), op.toString());
        op = ExpStdOp.create("min", args_inv);
        assertEquals(new URealValue(0, 0), (e.eval(op, state)), op.toString());
        op = ExpStdOp.create("max", args);
        assertEquals(new URealValue(1, 0), (e.eval(op, state)), op.toString());
        op = ExpStdOp.create("max", args_inv);
        assertEquals(new URealValue(1, 0), (e.eval(op, state)), op.toString());

        // UReal(0.0, 2.0).min(2.5) -> UReal(0.0, 2.0) : UReal
        // UReal(0.0, 2.0).max(2.5) -> UReal(2.5, 0.0) : UReal
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(0), new ExpConstReal(2)),
                new ExpConstReal(2.5)
        };
        args_inv = new Expression[] {args[1], args[0]};
        op = ExpStdOp.create("min", args);
        assertEquals(new URealValue(0, 2), (e.eval(op, state)), op.toString());
        op = ExpStdOp.create("min", args_inv);
        assertEquals(new URealValue(0, 2), (e.eval(op, state)), op.toString());
        op = ExpStdOp.create("max", args);
        assertEquals(new URealValue(2.5, 0), (e.eval(op, state)), op.toString());
        op = ExpStdOp.create("max", args_inv);
        assertEquals(new URealValue(2.5, 0), (e.eval(op, state)), op.toString());

        // UReal(3.0, 2.0).min(0) -> UReal(0.0, 0.0) : UReal
        // UReal(3.0, 2.0).max(0) -> UReal(3.0, 2.0) : UReal
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(3), new ExpConstReal(2)),
                new ExpConstReal(0)
        };
        args_inv = new Expression[] {args[1], args[0]};
        op = ExpStdOp.create("min", args);
        assertEquals(new URealValue(0, 0), (e.eval(op, state)), op.toString());
        op = ExpStdOp.create("min", args_inv);
        assertEquals(new URealValue(0, 0), (e.eval(op, state)), op.toString());
        op = ExpStdOp.create("max", args);
        assertEquals(new URealValue(3, 2), (e.eval(op, state)), op.toString());
        op = ExpStdOp.create("max", args_inv);
        assertEquals(new URealValue(3, 2), (e.eval(op, state)), op.toString());

    }

    @Test
    void testURealxOtherMinMax() throws ExpInvalidException {
        Expression [] args, args_inv;
        ExpStdOp op;

        // UReal(0.0, 0.0).min(3 / 0) -> Undefined : OclVoid
        // UReal(3.0, 2.0).max(3 / 0) -> Undefined : OclVoid
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(0), new ExpConstReal(0)),
                ExpStdOp.create("/",
                        new Expression[] {
                                new ExpConstInteger(3),
                                new ExpConstInteger(0)
                        })
        };
        args_inv = new Expression[] {args[1], args[0]};
        op = ExpStdOp.create("min", args);
        assertTrue(e.eval(op, state).isUndefined());
        op = ExpStdOp.create("min", args_inv);
        assertTrue(e.eval(op, state).isUndefined());
        args_inv = new Expression[] {args[1], args[0]};
        op = ExpStdOp.create("max", args);
        assertTrue(e.eval(op, state).isUndefined());
        op = ExpStdOp.create("max", args_inv);
        assertTrue(e.eval(op, state).isUndefined());

        // UReal(0.0, 0.0).min('Testing') -> Exception
        // UReal(0.0, 0.0).max('Testing') -> Exception
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(0), new ExpConstReal(0)),
                new ExpConstString("Testing")
        };
        args_inv = new Expression[] {args[1], args[0]};

        Expression[] finalArgsMinMax = args;
        Expression[] finalArgsMinMaxInv = args_inv;
        assertThrows(ExpInvalidException.class, () -> {
            ExpStdOp badOp = ExpStdOp.create("min", finalArgsMinMax);
            e.eval(badOp, state);
        });
        assertThrows(ExpInvalidException.class, () -> {
            ExpStdOp badOp = ExpStdOp.create("min", finalArgsMinMaxInv);
            e.eval(badOp, state);
        });
        assertThrows(ExpInvalidException.class, () -> {
            ExpStdOp badOp = ExpStdOp.create("max", finalArgsMinMax);
            e.eval(badOp, state);
        });
        assertThrows(ExpInvalidException.class, () -> {
            ExpStdOp badOp = ExpStdOp.create("max", finalArgsMinMaxInv);
            e.eval(badOp, state);
        });
    }

    @Test
    void testUrealIdentical() throws ExpInvalidException {
        Expression [] args;
        ExpStdOp op;

        // An UReal is identical to itself.
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(-2), new ExpConstReal(3)),
                new ExpConstUReal(new ExpConstReal(-2), new ExpConstReal(3))
        };
        op = ExpStdOp.create("equals", args);
        assertEquals(BooleanValue.TRUE, e.eval(op, state), op.toString());

        // An UReal isn't identical to another with different value.
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(-2), new ExpConstReal(3)),
                new ExpConstUReal(new ExpConstReal(0), new ExpConstReal(3))
        };
        op = ExpStdOp.create("equals", args);
        assertEquals(BooleanValue.FALSE, e.eval(op, state), op.toString());

        // An UReal isn't identical to another with different uncertainty.
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(2), new ExpConstReal(3)),
                new ExpConstUReal(new ExpConstReal(2), new ExpConstReal(0))
        };
        op = ExpStdOp.create("equals", args);
        assertEquals(BooleanValue.FALSE, e.eval(op, state), op.toString());

        // An UReal isn't identical to another with different value and uncertainty.
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(2), new ExpConstReal(3)),
                new ExpConstUReal(new ExpConstReal(1), new ExpConstReal(0))
        };
        op = ExpStdOp.create("equals", args);
        assertEquals(BooleanValue.FALSE, e.eval(op, state), op.toString());

    }

    @Test
    void testAddURealxUReal() throws ExpInvalidException {
        Expression [] args;
        ExpStdOp op;

        // UReal(-9, 0) + UReal(-9, 0) -> UReal(-18, 0) : UReal
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(-9), new ExpConstReal(0)),
                new ExpConstUReal(new ExpConstReal(-9), new ExpConstReal(0))
        };
        op = ExpStdOp.create("+", args);
        assertEquals((new URealValue(-18, 0)), (e.eval(op, state)), op.toString());

//        UReal(-9, 0) + UReal(-9, 0) -> UReal(-18, 0) : UReal
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(-3), new ExpConstReal(0)),
                new ExpConstUReal(new ExpConstReal(-3), new ExpConstReal(9))
        };
        op = ExpStdOp.create("+", args);
        assertEquals((new URealValue(-6, 9)), (e.eval(op, state)), op.toString());

//        UReal(-3, 0) + UReal(-3, 9) -> UReal(-6, 9) : UReal
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(-3), new ExpConstReal(0)),
                new ExpConstUReal(new ExpConstReal(-3), new ExpConstReal(9))
        };
        op = ExpStdOp.create("+", args);
        assertEquals((new URealValue(-6, 9)), (e.eval(op, state)), op.toString());

//        UReal(-7, 0) + UReal(3, 0) -> UReal(-4, 0) : UReal
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(-7), new ExpConstReal(0)),
                new ExpConstUReal(new ExpConstReal(3), new ExpConstReal(0))
        };
        op = ExpStdOp.create("+", args);
        assertEquals((new URealValue(-4, 0)), (e.eval(op, state)), op.toString());

//        UReal(-2, 0) + UReal(10, 7) -> UReal(8, 7) : UReal
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(-2), new ExpConstReal(0)),
                new ExpConstUReal(new ExpConstReal(10), new ExpConstReal(7))
        };
        op = ExpStdOp.create("+", args);
        assertEquals((new URealValue(8, 7)), (e.eval(op, state)), op.toString());

//        UReal(-9, 7) + UReal(-9, 0) -> UReal(-18, 7) : UReal
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(-9), new ExpConstReal(7)),
                new ExpConstUReal(new ExpConstReal(-9), new ExpConstReal(0))
        };
        op = ExpStdOp.create("+", args);
        assertEquals((new URealValue(-18, 7)), (e.eval(op, state)), op.toString());

//        UReal(-3, 3) + UReal(-3, 4) -> UReal(-6, 5) : UReal
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(-3), new ExpConstReal(3)),
                new ExpConstUReal(new ExpConstReal(-3), new ExpConstReal(4))
        };
        op = ExpStdOp.create("+", args);
        assertEquals((new URealValue(-6, 5)), (e.eval(op, state)), op.toString());

//        UReal(-9, 3) + UReal(7, 0) -> UReal(-2, 3) : UReal
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(-9), new ExpConstReal(3)),
                new ExpConstUReal(new ExpConstReal(7), new ExpConstReal(0))
        };
        op = ExpStdOp.create("+", args);
        assertEquals((new URealValue(-2, 3)), (e.eval(op, state)), op.toString());

//        UReal(-6, 3) + UReal(10, 4) -> UReal(4, 5) : UReal
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(-6), new ExpConstReal(3)),
                new ExpConstUReal(new ExpConstReal(10), new ExpConstReal(4))
        };
        op = ExpStdOp.create("+", args);
        assertEquals((new URealValue(4, 5)), (e.eval(op, state)), op.toString());

//        UReal(0, 0) + UReal(0, 0) -> UReal(0, 0) : UReal
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(0), new ExpConstReal(0)),
                new ExpConstUReal(new ExpConstReal(0), new ExpConstReal(0))
        };
        op = ExpStdOp.create("+", args);
        assertEquals((new URealValue(0, 0)), (e.eval(op, state)), op.toString());

//        UReal(0, 0) + UReal(0, 1) -> UReal(0, 1) : UReal
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(0), new ExpConstReal(0)),
                new ExpConstUReal(new ExpConstReal(0), new ExpConstReal(1))
        };
        op = ExpStdOp.create("+", args);
        assertEquals((new URealValue(0, 1)), (e.eval(op, state)), op.toString());

//        UReal(0, 0) + UReal(6, 0) -> UReal(6, 0) : UReal
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(0), new ExpConstReal(0)),
                new ExpConstUReal(new ExpConstReal(6), new ExpConstReal(0))
        };
        op = ExpStdOp.create("+", args);
        assertEquals((new URealValue(6, 0)), (e.eval(op, state)), op.toString());

//        UReal(0, 0) + UReal(9, 4) -> UReal(9, 4) : UReal
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(0), new ExpConstReal(0)),
                new ExpConstUReal(new ExpConstReal(9), new ExpConstReal(4))
        };
        op = ExpStdOp.create("+", args);
        assertEquals((new URealValue(9, 4)), (e.eval(op, state)), op.toString());

//        UReal(0, 2) + UReal(0, 0) -> UReal(0, 2) : UReal
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(0), new ExpConstReal(2)),
                new ExpConstUReal(new ExpConstReal(0), new ExpConstReal(0))
        };
        op = ExpStdOp.create("+", args);
        assertEquals((new URealValue(0, 2)), (e.eval(op, state)), op.toString());

//        UReal(0, 3) + UReal(0, 4) -> UReal(0, 5) : UReal
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(0), new ExpConstReal(3)),
                new ExpConstUReal(new ExpConstReal(0), new ExpConstReal(4))
        };
        op = ExpStdOp.create("+", args);
        assertEquals((new URealValue(0, 5)), (e.eval(op, state)), op.toString());

//        UReal(0, 4) + UReal(2, 0) -> UReal(2, 4) : UReal
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(0), new ExpConstReal(4)),
                new ExpConstUReal(new ExpConstReal(2), new ExpConstReal(0))
        };
        op = ExpStdOp.create("+", args);
        assertEquals((new URealValue(2, 4)), (e.eval(op, state)), op.toString());

//        UReal(0, 3) + UReal(8, 4) -> UReal(8, 5) : UReal
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(0), new ExpConstReal(3)),
                new ExpConstUReal(new ExpConstReal(8), new ExpConstReal(4))
        };
        op = ExpStdOp.create("+", args);
        assertEquals((new URealValue(8, 5)), (e.eval(op, state)), op.toString());

//        UReal(9, 0) + UReal(9, 0) -> UReal(18, 0) : UReal
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(9), new ExpConstReal(0)),
                new ExpConstUReal(new ExpConstReal(9), new ExpConstReal(0))
        };
        op = ExpStdOp.create("+", args);
        assertEquals((new URealValue(18, 0)), (e.eval(op, state)), op.toString());

//        UReal(3, 0) + UReal(3, 1) -> UReal(6, 1) : UReal
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(3), new ExpConstReal(0)),
                new ExpConstUReal(new ExpConstReal(3), new ExpConstReal(1))
        };
        op = ExpStdOp.create("+", args);
        assertEquals((new URealValue(6, 1)), (e.eval(op, state)), op.toString());

//        UReal(7, 0) + UReal(8, 0) -> UReal(15, 0) : UReal
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(7), new ExpConstReal(0)),
                new ExpConstUReal(new ExpConstReal(8), new ExpConstReal(0))
        };
        op = ExpStdOp.create("+", args);
        assertEquals((new URealValue(15, 0)), (e.eval(op, state)), op.toString());

//        UReal(2, 0) + UReal(7, 8) -> UReal(9, 8) : UReal
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(2), new ExpConstReal(0)),
                new ExpConstUReal(new ExpConstReal(7), new ExpConstReal(8))
        };
        op = ExpStdOp.create("+", args);
        assertEquals((new URealValue(9, 8)), (e.eval(op, state)), op.toString());

//        UReal(9, 9) + UReal(9, 0) -> UReal(18, 9) : UReal
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(9), new ExpConstReal(9)),
                new ExpConstUReal(new ExpConstReal(9), new ExpConstReal(0))
        };
        op = ExpStdOp.create("+", args);
        assertEquals((new URealValue(18, 9)), (e.eval(op, state)), op.toString());

//        UReal(3, 3) + UReal(3, 4) -> UReal(6, 5) : UReal
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(3), new ExpConstReal(3)),
                new ExpConstUReal(new ExpConstReal(3), new ExpConstReal(4))
        };
        op = ExpStdOp.create("+", args);
        assertEquals((new URealValue(6, 5)), (e.eval(op, state)), op.toString());

//        UReal(9, 2) + UReal(10, 0) -> UReal(19, 2) : UReal
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(9), new ExpConstReal(2)),
                new ExpConstUReal(new ExpConstReal(10), new ExpConstReal(0))
        };
        op = ExpStdOp.create("+", args);
        assertEquals((new URealValue(19, 2)), (e.eval(op, state)), op.toString());

//        UReal(6, 3) + UReal(1, 4) -> UReal(7, 5) : UReal
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(6), new ExpConstReal(3)),
                new ExpConstUReal(new ExpConstReal(1), new ExpConstReal(4))
        };
        op = ExpStdOp.create("+", args);
        assertEquals((new URealValue(7, 5)), (e.eval(op, state)), op.toString());

    }

    @Test
    void testAddURealxReal() throws ExpInvalidException {
        Expression [] args;
        ExpStdOp op;

//        UReal(-3, 0) + -3.0 -> UReal(-6, 0) : UReal
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(-3), new ExpConstReal(0)),
                new ExpConstReal(-3)
        };
        op = ExpStdOp.create("+", args);
        assertEquals((new URealValue(-6, 0)), (e.eval(op, state)), op.toString());

//        UReal(-6, 0) + -1.2 -> UReal(-7,2, 0) : UReal
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(-6), new ExpConstReal(0)),
                new ExpConstReal(-1.2)
        };
        op = ExpStdOp.create("+", args);
        assertEquals((new URealValue(-7.2, 0)), (e.eval(op, state)), op.toString());

//        UReal(-5, 3) + -5.0 -> UReal(-10, 3) : UReal
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(-5), new ExpConstReal(3)),
                new ExpConstReal(-5)
        };
        op = ExpStdOp.create("+", args);
        assertEquals((new URealValue(-10, 3)), (e.eval(op, state)), op.toString());

//        UReal(-8, 5) + -2.0 -> UReal(-10, 5) : UReal
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(-8), new ExpConstReal(5)),
                new ExpConstReal(-2)
        };
        op = ExpStdOp.create("+", args);
        assertEquals((new URealValue(-10, 5)), (e.eval(op, state)), op.toString());

//        UReal(0, 0) + 0.0 -> UReal(0, 0) : UReal
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(0), new ExpConstReal(0)),
                new ExpConstReal(0)
        };
        op = ExpStdOp.create("+", args);
        assertEquals((new URealValue(0, 0)), (e.eval(op, state)), op.toString());

//        UReal(0, 0) + 3.0 -> UReal(3, 0) : UReal
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(0), new ExpConstReal(0)),
                new ExpConstReal(3)
        };
        op = ExpStdOp.create("+", args);
        assertEquals((new URealValue(3, 0)), (e.eval(op, state)), op.toString());

//        UReal(0, 3) + 0.0 -> UReal(0, 3) : UReal
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(0), new ExpConstReal(3)),
                new ExpConstReal(0)
        };
        op = ExpStdOp.create("+", args);
        assertEquals((new URealValue(0, 3)), (e.eval(op, state)), op.toString());

//        UReal(0, 5) + -5.0 -> UReal(-5, 5) : UReal
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(0), new ExpConstReal(5)),
                new ExpConstReal(-5)
        };
        op = ExpStdOp.create("+", args);
        assertEquals((new URealValue(-5, 5)), (e.eval(op, state)), op.toString());

//        UReal(5, 0) + 5.0 -> UReal(10, 0) : UReal
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(5), new ExpConstReal(0)),
                new ExpConstReal(5)
        };
        op = ExpStdOp.create("+", args);
        assertEquals((new URealValue(10, 0)), (e.eval(op, state)), op.toString());

//        UReal(3, 0) + 0.6 -> UReal(3.6, 0) : UReal
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(3), new ExpConstReal(0)),
                new ExpConstReal(0.6)
        };
        op = ExpStdOp.create("+", args);
        assertEquals((new URealValue(3.6, 0)), (e.eval(op, state)), op.toString());

//        UReal(7, 3) + 7.0 -> UReal(14, 3) : UReal
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(7), new ExpConstReal(3)),
                new ExpConstReal(7)
        };
        op = ExpStdOp.create("+", args);
        assertEquals((new URealValue(14, 3)), (e.eval(op, state)), op.toString());

//        UReal(2, 5) + 0.5 -> UReal(2.5, 5) : UReal
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(2), new ExpConstReal(5)),
                new ExpConstReal(0.5)
        };
        op = ExpStdOp.create("+", args);
        assertEquals((new URealValue(2.5, 5)), (e.eval(op, state)), op.toString());

    }

    @Test
    void testAddURealxInteger() throws ExpInvalidException {
        Expression [] args;
        ExpStdOp op;

//        UReal(-3, 0) + -3.0 -> UReal(-6, 0) : UReal
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(-3), new ExpConstReal(0)),
                new ExpConstInteger(-3)
        };
        op = ExpStdOp.create("+", args);
        assertEquals((new URealValue(-6, 0)), (e.eval(op, state)), op.toString());

//        UReal(-6, 0) + -12 -> UReal(-18, 0) : UReal
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(-6), new ExpConstReal(0)),
                new ExpConstInteger(-12)
        };
        op = ExpStdOp.create("+", args);
        assertEquals((new URealValue(-18, 0)), (e.eval(op, state)), op.toString());

//        UReal(-5, 3) + -5 -> UReal(-10, 3) : UReal
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(-5), new ExpConstReal(3)),
                new ExpConstInteger(-5)
        };
        op = ExpStdOp.create("+", args);
        assertEquals((new URealValue(-10, 3)), (e.eval(op, state)), op.toString());

//        UReal(-8, 5) + -2 -> UReal(-10, 5) : UReal
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(-8), new ExpConstReal(5)),
                new ExpConstInteger(-2)
        };
        op = ExpStdOp.create("+", args);
        assertEquals((new URealValue(-10, 5)), (e.eval(op, state)), op.toString());

//        UReal(0, 0) + 0 -> UReal(0, 0) : UReal
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(0), new ExpConstReal(0)),
                new ExpConstInteger(0)
        };
        op = ExpStdOp.create("+", args);
        assertEquals((new URealValue(0, 0)), (e.eval(op, state)), op.toString());

//        UReal(0, 0) + 3 -> UReal(3, 0) : UReal
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(0), new ExpConstReal(0)),
                new ExpConstInteger(3)
        };
        op = ExpStdOp.create("+", args);
        assertEquals((new URealValue(3, 0)), (e.eval(op, state)), op.toString());

//        UReal(0, 3) + 0 -> UReal(0, 3) : UReal
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(0), new ExpConstReal(3)),
                new ExpConstInteger(0)
        };
        op = ExpStdOp.create("+", args);
        assertEquals((new URealValue(0, 3)), (e.eval(op, state)), op.toString());

//        UReal(0, 5) + -5 -> UReal(-5, 5) : UReal
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(0), new ExpConstReal(5)),
                new ExpConstInteger(-5)
        };
        op = ExpStdOp.create("+", args);
        assertEquals((new URealValue(-5, 5)), (e.eval(op, state)), op.toString());

//        UReal(5, 0) + 5.0 -> UReal(10, 0) : UReal
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(5), new ExpConstReal(0)),
                new ExpConstInteger(5)
        };
        op = ExpStdOp.create("+", args);
        assertEquals((new URealValue(10, 0)), (e.eval(op, state)), op.toString());

//        UReal(3, 0) + 56 -> UReal(59, 0) : UReal
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(3), new ExpConstReal(0)),
                new ExpConstInteger(56)
        };
        op = ExpStdOp.create("+", args);
        assertEquals((new URealValue(59, 0)), (e.eval(op, state)), op.toString());

//        UReal(7, 3) + 7.0 -> UReal(14, 3) : UReal
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(7), new ExpConstReal(3)),
                new ExpConstInteger(7)
        };
        op = ExpStdOp.create("+", args);
        assertEquals((new URealValue(14, 3)), (e.eval(op, state)), op.toString());

//        UReal(2, 5) + 65 -> UReal(67, 5) : UReal
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(2), new ExpConstReal(5)),
                new ExpConstInteger(65)
        };
        op = ExpStdOp.create("+", args);
        assertEquals((new URealValue(67, 5)), (e.eval(op, state)), op.toString());
    }

    @Test
    void testMultiplyURealxUReal() throws ExpInvalidException {
        Expression[] args;
        ExpStdOp op;

        // UReal(-9, 0) * UReal(-9, 0)  -> UReal(81.0, 0.0) : UReal
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(-9), new ExpConstReal(0)),
                new ExpConstUReal(new ExpConstReal(-9), new ExpConstReal(0))
        };
        op = ExpStdOp.create("*", args);
        assertEquals(new URealValue(81.0, 0.0), e.eval(op, state), op.toString());

        // UReal(-5, 0) * UReal(-5, 3)  -> UReal(25.0, 15.0) : UReal
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(-5), new ExpConstReal(0)),
                new ExpConstUReal(new ExpConstReal(-5), new ExpConstReal(3))
        };
        op = ExpStdOp.create("*", args);
        assertEquals(new URealValue(25.0, 15.0), e.eval(op, state), op.toString());

        // UReal(-4, 0) * UReal(2, 0)  -> UReal(-8.0, 0.0) : UReal
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(-4), new ExpConstReal(0)),
                new ExpConstUReal(new ExpConstReal(2), new ExpConstReal(0))
        };
        op = ExpStdOp.create("*", args);
        assertEquals(new URealValue(-8.0, 0.0), e.eval(op, state), op.toString());

        // UReal(-10, 0) * UReal(4, 1)  -> UReal(-40.0, 10.0) : UReal
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(-10), new ExpConstReal(0)),
                new ExpConstUReal(new ExpConstReal(4), new ExpConstReal(1))
        };
        op = ExpStdOp.create("*", args);
        assertEquals(new URealValue(-40.0, 10.0), e.eval(op, state), op.toString());

        // UReal(-9, 9) * UReal(-9, 0)  -> UReal(81.0, 81.0) : UReal
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(-9), new ExpConstReal(9)),
                new ExpConstUReal(new ExpConstReal(-9), new ExpConstReal(0))
        };
        op = ExpStdOp.create("*", args);
        assertEquals(new URealValue(81.0, 81.0), e.eval(op, state), op.toString());

        // UReal(-2, 3) * UReal(-2, 4)  -> UReal(4.0, 10.0) : UReal
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(-2), new ExpConstReal(3)),
                new ExpConstUReal(new ExpConstReal(-2), new ExpConstReal(4))
        };
        op = ExpStdOp.create("*", args);
        assertEquals(new URealValue(4.0, 10.0), e.eval(op, state), op.toString());

        // UReal(-6, 2) * UReal(5, 0)  -> UReal(-30.0, 10.0) : UReal
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(-6), new ExpConstReal(2)),
                new ExpConstUReal(new ExpConstReal(5), new ExpConstReal(0))
        };
        op = ExpStdOp.create("*", args);
        assertEquals(new URealValue(-30.0, 10.0), e.eval(op, state), op.toString());

        // UReal(-2, 3) * UReal(2, 4)  -> UReal(-4.0, 10.0) : UReal
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(-2), new ExpConstReal(3)),
                new ExpConstUReal(new ExpConstReal(2), new ExpConstReal(4))
        };
        op = ExpStdOp.create("*", args);
        assertEquals(new URealValue(-4.0, 10.0), e.eval(op, state), op.toString());

        // UReal(0, 0) * UReal(0, 0)  -> UReal(0.0, 0.0) : UReal
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(0), new ExpConstReal(0)),
                new ExpConstUReal(new ExpConstReal(0), new ExpConstReal(0))
        };
        op = ExpStdOp.create("*", args);
        assertEquals(new URealValue(0.0, 0.0), e.eval(op, state), op.toString());

        // UReal(0, 0) * UReal(0, 4)  -> UReal(0.0, 0.0) : UReal
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(0), new ExpConstReal(0)),
                new ExpConstUReal(new ExpConstReal(0), new ExpConstReal(4))
        };
        op = ExpStdOp.create("*", args);
        assertEquals(new URealValue(0.0, 0.0), e.eval(op, state), op.toString());

        // UReal(0, 0) * UReal(6, 0)  -> UReal(0.0, 0.0) : UReal
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(0), new ExpConstReal(0)),
                new ExpConstUReal(new ExpConstReal(6), new ExpConstReal(0))
        };
        op = ExpStdOp.create("*", args);
        assertEquals(new URealValue(0.0, 0.0), e.eval(op, state), op.toString());

        // UReal(0, 0) * UReal(7, 3)  -> UReal(0.0, 0.0) : UReal
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(0), new ExpConstReal(0)),
                new ExpConstUReal(new ExpConstReal(7), new ExpConstReal(3))
        };
        op = ExpStdOp.create("*", args);
        assertEquals(new URealValue(0.0, 0.0), e.eval(op, state), op.toString());

        // UReal(0, 4) * UReal(0, 0)  -> UReal(0.0, 0.0) : UReal
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(0), new ExpConstReal(4)),
                new ExpConstUReal(new ExpConstReal(0), new ExpConstReal(0))
        };
        op = ExpStdOp.create("*", args);
        assertEquals(new URealValue(0.0, 0.0), e.eval(op, state), op.toString());

        // UReal(0, 4) * UReal(0, 3)  -> UReal(0.0, 0.0) : UReal
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(0), new ExpConstReal(4)),
                new ExpConstUReal(new ExpConstReal(0), new ExpConstReal(3))
        };
        op = ExpStdOp.create("*", args);
        assertEquals(new URealValue(0.0, 0.0), e.eval(op, state), op.toString());

        // UReal(0, 4) * UReal(1, 0)  -> UReal(0.0, 4.0) : UReal
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(0), new ExpConstReal(4)),
                new ExpConstUReal(new ExpConstReal(1), new ExpConstReal(0))
        };
        op = ExpStdOp.create("*", args);
        assertEquals(new URealValue(0.0, 4.0), e.eval(op, state), op.toString());

        // UReal(0, 4) * UReal(2, 3)  -> UReal(0.0, 8.0) : UReal
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(0), new ExpConstReal(4)),
                new ExpConstUReal(new ExpConstReal(2), new ExpConstReal(3))
        };
        op = ExpStdOp.create("*", args);
        assertEquals(new URealValue(0.0, 8.0), e.eval(op, state), op.toString());

        // UReal(9, 0) * UReal(9, 0)  -> UReal(81.0, 0.0) : UReal
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(9), new ExpConstReal(0)),
                new ExpConstUReal(new ExpConstReal(9), new ExpConstReal(0))
        };
        op = ExpStdOp.create("*", args);
        assertEquals(new URealValue(81.0, 0.0), e.eval(op, state), op.toString());

        // UReal(5, 0) * UReal(5, 3)  -> UReal(25.0, 15.0) : UReal
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(5), new ExpConstReal(0)),
                new ExpConstUReal(new ExpConstReal(5), new ExpConstReal(3))
        };
        op = ExpStdOp.create("*", args);
        assertEquals(new URealValue(25.0, 15.0), e.eval(op, state), op.toString());

        // UReal(4, 0) * UReal(8, 0)  -> UReal(32.0, 0.0) : UReal
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(4), new ExpConstReal(0)),
                new ExpConstUReal(new ExpConstReal(8), new ExpConstReal(0))
        };
        op = ExpStdOp.create("*", args);
        assertEquals(new URealValue(32.0, 0.0), e.eval(op, state), op.toString());

        // UReal(10, 0) * UReal(10, 12)  -> UReal(100.0, 120.0) : UReal
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(10), new ExpConstReal(0)),
                new ExpConstUReal(new ExpConstReal(10), new ExpConstReal(12))
        };
        op = ExpStdOp.create("*", args);
        assertEquals(new URealValue(100.0, 120.0), e.eval(op, state), op.toString());

        // UReal(9, 5) * UReal(9, 0)  -> UReal(81.0, 45.0) : UReal
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(9), new ExpConstReal(5)),
                new ExpConstUReal(new ExpConstReal(9), new ExpConstReal(0))
        };
        op = ExpStdOp.create("*", args);
        assertEquals(new URealValue(81.0, 45.0), e.eval(op, state), op.toString());

        // UReal(2, 3) * UReal(2, 4)  -> UReal(4.0, 10.0) : UReal
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(2), new ExpConstReal(3)),
                new ExpConstUReal(new ExpConstReal(2), new ExpConstReal(4))
        };
        op = ExpStdOp.create("*", args);
        assertEquals(new URealValue(4.0, 10.0), e.eval(op, state), op.toString());

        // UReal(6, 1) * UReal(4, 0)  -> UReal(24.0, 4.0) : UReal
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(6), new ExpConstReal(1)),
                new ExpConstUReal(new ExpConstReal(4), new ExpConstReal(0))
        };
        op = ExpStdOp.create("*", args);
        assertEquals(new URealValue(24.0, 4.0), e.eval(op, state), op.toString());

        // UReal(2, 3) * UReal(5, 4)  -> UReal(10.0, 17.0) : UReal
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(2), new ExpConstReal(3)),
                new ExpConstUReal(new ExpConstReal(5), new ExpConstReal(4))
        };
        op = ExpStdOp.create("*", args);
        assertEquals(new URealValue(10.0, 17.0), e.eval(op, state), op.toString());
    }

    @Test
    void testMultiplyURealxReal() throws ExpInvalidException {
        Expression[] args;
        ExpStdOp op;

        // UReal(-3, 0) * -3.0  -> UReal(9.0, 0.0) : UReal
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(-3), new ExpConstReal(0)),
                new ExpConstReal(-3.0)
        };
        op = ExpStdOp.create("*", args);
        assertEquals(new URealValue(9.0, 0.0), e.eval(op, state), op.toString());

        // UReal(-6, 0) * -1.2  -> UReal(7.2, 0.0) : UReal
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(-6), new ExpConstReal(0)),
                new ExpConstReal(-1.2)
        };
        op = ExpStdOp.create("*", args);
        assertEquals(new URealValue(7.2, 0.0), e.eval(op, state), op.toString());

        // UReal(-5, 3) * -5.0  -> UReal(25.0, 15.0) : UReal
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(-5), new ExpConstReal(3)),
                new ExpConstReal(-5.0)
        };
        op = ExpStdOp.create("*", args);
        assertEquals(new URealValue(25.0, 15.0), e.eval(op, state), op.toString());

        // UReal(-8, 5) * -2.0  -> UReal(16.0, 10.0) : UReal
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(-8), new ExpConstReal(5)),
                new ExpConstReal(-2.0)
        };
        op = ExpStdOp.create("*", args);
        assertEquals(new URealValue(16.0, 10.0), e.eval(op, state), op.toString());

        // UReal(0, 0) * 0.0  -> UReal(0.0, 0.0) : UReal
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(0), new ExpConstReal(0)),
                new ExpConstReal(0.0)
        };
        op = ExpStdOp.create("*", args);
        assertEquals(new URealValue(0.0, 0.0), e.eval(op, state), op.toString());

        // UReal(0, 0) * 3.0  -> UReal(0.0, 0.0) : UReal
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(0), new ExpConstReal(0)),
                new ExpConstReal(3.0)
        };
        op = ExpStdOp.create("*", args);
        assertEquals(new URealValue(0.0, 0.0), e.eval(op, state), op.toString());

        // UReal(0, 3) * 0.0  -> UReal(0.0, 0.0) : UReal
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(0), new ExpConstReal(3)),
                new ExpConstReal(0.0)
        };
        op = ExpStdOp.create("*", args);
        assertEquals(new URealValue(0.0, 0.0), e.eval(op, state), op.toString());

        // UReal(0, 5) * -5.0  -> UReal(0.0, 25.0) : UReal
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(0), new ExpConstReal(5)),
                new ExpConstReal(-5.0)
        };
        op = ExpStdOp.create("*", args);
        assertEquals(new URealValue(0.0, 25.0), e.eval(op, state), op.toString());

        // UReal(5, 0) * 5.0  -> UReal(25.0, 0.0) : UReal
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(5), new ExpConstReal(0)),
                new ExpConstReal(5.0)
        };
        op = ExpStdOp.create("*", args);
        assertEquals(new URealValue(25.0, 0.0), e.eval(op, state), op.toString());

        // UReal(3, 0) * 0.6  -> UReal(1.8, 0.0) : UReal
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(3), new ExpConstReal(0)),
                new ExpConstReal(0.6)
        };
        op = ExpStdOp.create("*", args);
        assertEquals(new URealValue(1.8, 0.0), e.eval(op, state), op.toString());

        // UReal(7, 3) * 7.0  -> UReal(49.0, 21.0) : UReal
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(7), new ExpConstReal(3)),
                new ExpConstReal(7.0)
        };
        op = ExpStdOp.create("*", args);
        assertEquals(new URealValue(49.0, 21.0), e.eval(op, state), op.toString());

        // UReal(2, 5) * 0.5  -> UReal(1.0, 2.5) : UReal
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(2), new ExpConstReal(5)),
                new ExpConstReal(0.5)
        };
        op = ExpStdOp.create("*", args);
        assertEquals(new URealValue(1.0, 2.5), e.eval(op, state), op.toString());
    }

    @Test
    void testMultiplyURealxInteger() throws ExpInvalidException {
        Expression[] args;
        ExpStdOp op;

        // UReal(-3, 0) * -3  -> UReal(9.0, 0.0) : UReal
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(-3), new ExpConstReal(0)),
                new ExpConstInteger(-3)
        };
        op = ExpStdOp.create("*", args);
        assertEquals(new URealValue(9.0, 0.0), e.eval(op, state), op.toString());

        // UReal(-6, 0) * -12  -> UReal(72.0, 0.0) : UReal
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(-6), new ExpConstReal(0)),
                new ExpConstInteger(-12)
        };
        op = ExpStdOp.create("*", args);
        assertEquals(new URealValue(72.0, 0.0), e.eval(op, state), op.toString());

        // UReal(-5, 3) * -5  -> UReal(25.0, 15.0) : UReal
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(-5), new ExpConstReal(3)),
                new ExpConstInteger(-5)
        };
        op = ExpStdOp.create("*", args);
        assertEquals(new URealValue(25.0, 15.0), e.eval(op, state), op.toString());

        // UReal(-8, 5) * -2  -> UReal(16.0, 10.0) : UReal
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(-8), new ExpConstReal(5)),
                new ExpConstInteger(-2)
        };
        op = ExpStdOp.create("*", args);
        assertEquals(new URealValue(16.0, 10.0), e.eval(op, state), op.toString());

        // UReal(0, 0) * 0  -> UReal(0.0, 0.0) : UReal
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(0), new ExpConstReal(0)),
                new ExpConstInteger(0)
        };
        op = ExpStdOp.create("*", args);
        assertEquals(new URealValue(0.0, 0.0), e.eval(op, state), op.toString());

        // UReal(0, 0) * 3  -> UReal(0.0, 0.0) : UReal
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(0), new ExpConstReal(0)),
                new ExpConstInteger(3)
        };
        op = ExpStdOp.create("*", args);
        assertEquals(new URealValue(0.0, 0.0), e.eval(op, state), op.toString());

        // UReal(0, 3) * 0  -> UReal(0.0, 0.0) : UReal
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(0), new ExpConstReal(3)),
                new ExpConstInteger(0)
        };
        op = ExpStdOp.create("*", args);
        assertEquals(new URealValue(0.0, 0.0), e.eval(op, state), op.toString());

        // UReal(0, 5) * -5  -> UReal(0.0, 25.0) : UReal
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(0), new ExpConstReal(5)),
                new ExpConstInteger(-5)
        };
        op = ExpStdOp.create("*", args);
        assertEquals(new URealValue(0.0, 25.0), e.eval(op, state), op.toString());

        // UReal(5, 0) * 5  -> UReal(25.0, 0.0) : UReal
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(5), new ExpConstReal(0)),
                new ExpConstInteger(5)
        };
        op = ExpStdOp.create("*", args);
        assertEquals(new URealValue(25.0, 0.0), e.eval(op, state), op.toString());

        // UReal(3, 0) * 56  -> UReal(168.0, 0.0) : UReal
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(3), new ExpConstReal(0)),
                new ExpConstInteger(56)
        };
        op = ExpStdOp.create("*", args);
        assertEquals(new URealValue(168.0, 0.0), e.eval(op, state), op.toString());

        // UReal(7, 3) * 7  -> UReal(49.0, 21.0) : UReal
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(7), new ExpConstReal(3)),
                new ExpConstInteger(7)
        };
        op = ExpStdOp.create("*", args);
        assertEquals(new URealValue(49.0, 21.0), e.eval(op, state), op.toString());

        // UReal(2, 5) * 65  -> UReal(130.0, 325.0) : UReal
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(2), new ExpConstReal(5)),
                new ExpConstInteger(65)
        };
        op = ExpStdOp.create("*", args);
        assertEquals(new URealValue(130.0, 325.0), e.eval(op, state), op.toString());
    }

    @Test
    void testMinusURealxUReal() throws ExpInvalidException {
        Expression[] args;
        ExpStdOp op;

        // UReal(-9, 0) - UReal(-9, 0) -> UReal(0, 0)
        args = new Expression[]{
                new ExpConstUReal(new ExpConstReal(-9), new ExpConstReal(0)),
                new ExpConstUReal(new ExpConstReal(-9), new ExpConstReal(0))
        };
        op = ExpStdOp.create("-", args);
        assertEquals((new URealValue(0, 0)), (e.eval(op, state)), op.toString());

        // UReal(-5, 0) - UReal(-5, 3) -> UReal(0, 3)
        args = new Expression[]{
                new ExpConstUReal(new ExpConstReal(-5), new ExpConstReal(0)),
                new ExpConstUReal(new ExpConstReal(-5), new ExpConstReal(3))
        };
        op = ExpStdOp.create("-", args);
        assertEquals((new URealValue(0, 3)), (e.eval(op, state)), op.toString());

        // UReal(-4, 0) - UReal(2, 0) -> UReal(-6, 0)
        args = new Expression[]{
                new ExpConstUReal(new ExpConstReal(-4), new ExpConstReal(0)),
                new ExpConstUReal(new ExpConstReal(2), new ExpConstReal(0))
        };
        op = ExpStdOp.create("-", args);
        assertEquals((new URealValue(-6, 0)), (e.eval(op, state)), op.toString());

        // UReal(-10, 0) - UReal(4, 1) -> UReal(-14, 1)
        args = new Expression[]{
                new ExpConstUReal(new ExpConstReal(-10), new ExpConstReal(0)),
                new ExpConstUReal(new ExpConstReal(4), new ExpConstReal(1))
        };
        op = ExpStdOp.create("-", args);
        assertEquals((new URealValue(-14, 1)), (e.eval(op, state)), op.toString());

        // UReal(-9, 9) - UReal(-9, 0) -> UReal(0, 9)
        args = new Expression[]{
                new ExpConstUReal(new ExpConstReal(-9), new ExpConstReal(9)),
                new ExpConstUReal(new ExpConstReal(-9), new ExpConstReal(0))
        };
        op = ExpStdOp.create("-", args);
        assertEquals((new URealValue(0, 9)), (e.eval(op, state)), op.toString());

        // UReal(-2, 3) - UReal(-2, 4) -> UReal(0, 5)
        args = new Expression[]{
                new ExpConstUReal(new ExpConstReal(-2), new ExpConstReal(3)),
                new ExpConstUReal(new ExpConstReal(-2), new ExpConstReal(4))
        };
        op = ExpStdOp.create("-", args);
        assertEquals((new URealValue(0, 5)), (e.eval(op, state)), op.toString());

        // UReal(-6, 2) - UReal(5, 0) -> UReal(-11, 2)
        args = new Expression[]{
                new ExpConstUReal(new ExpConstReal(-6), new ExpConstReal(2)),
                new ExpConstUReal(new ExpConstReal(5), new ExpConstReal(0))
        };
        op = ExpStdOp.create("-", args);
        assertEquals((new URealValue(-11, 2)), (e.eval(op, state)), op.toString());

        // UReal(-2, 3) - UReal(4, 4) -> UReal(-6, 5)
        args = new Expression[]{
                new ExpConstUReal(new ExpConstReal(-2), new ExpConstReal(3)),
                new ExpConstUReal(new ExpConstReal(4), new ExpConstReal(4))
        };
        op = ExpStdOp.create("-", args);
        assertEquals((new URealValue(-6, 5)), (e.eval(op, state)), op.toString());

        // UReal(0, 0) - UReal(0, 0) -> UReal(0, 0)
        args = new Expression[]{
                new ExpConstUReal(new ExpConstReal(0), new ExpConstReal(0)),
                new ExpConstUReal(new ExpConstReal(0), new ExpConstReal(0))
        };
        op = ExpStdOp.create("-", args);
        assertEquals((new URealValue(0, 0)), (e.eval(op, state)), op.toString());

        // UReal(0, 0) - UReal(0, 4) -> UReal(0, 4)
        args = new Expression[]{
                new ExpConstUReal(new ExpConstReal(0), new ExpConstReal(0)),
                new ExpConstUReal(new ExpConstReal(0), new ExpConstReal(4))
        };
        op = ExpStdOp.create("-", args);
        assertEquals((new URealValue(0, 4)), (e.eval(op, state)), op.toString());

        // UReal(0, 0) - UReal(6, 0) -> UReal(-6, 0)
        args = new Expression[]{
                new ExpConstUReal(new ExpConstReal(0), new ExpConstReal(0)),
                new ExpConstUReal(new ExpConstReal(6), new ExpConstReal(0))
        };
        op = ExpStdOp.create("-", args);
        assertEquals((new URealValue(-6, 0)), (e.eval(op, state)), op.toString());

        // UReal(0, 0) - UReal(7, 3) -> UReal(-7, 3)
        args = new Expression[]{
                new ExpConstUReal(new ExpConstReal(0), new ExpConstReal(0)),
                new ExpConstUReal(new ExpConstReal(7), new ExpConstReal(3))
        };
        op = ExpStdOp.create("-", args);
        assertEquals((new URealValue(-7, 3)), (e.eval(op, state)), op.toString());

        // UReal(0, 4) - UReal(0, 0) -> UReal(0, 4)
        args = new Expression[]{
                new ExpConstUReal(new ExpConstReal(0), new ExpConstReal(4)),
                new ExpConstUReal(new ExpConstReal(0), new ExpConstReal(0))
        };
        op = ExpStdOp.create("-", args);
        assertEquals((new URealValue(0, 4)), (e.eval(op, state)), op.toString());

        // UReal(0, 4) - UReal(0, 3) -> UReal(0, 5)
        args = new Expression[]{
                new ExpConstUReal(new ExpConstReal(0), new ExpConstReal(4)),
                new ExpConstUReal(new ExpConstReal(0), new ExpConstReal(3))
        };
        op = ExpStdOp.create("-", args);
        assertEquals((new URealValue(0, 5)), (e.eval(op, state)), op.toString());

        // UReal(0, 4) - UReal(1, 0) -> UReal(-1, 4)
        args = new Expression[]{
                new ExpConstUReal(new ExpConstReal(0), new ExpConstReal(4)),
                new ExpConstUReal(new ExpConstReal(1), new ExpConstReal(0))
        };
        op = ExpStdOp.create("-", args);
        assertEquals((new URealValue(-1, 4)), (e.eval(op, state)), op.toString());

        // UReal(0, 4) - UReal(2, 3) -> UReal(-2, 5)
        args = new Expression[]{
                new ExpConstUReal(new ExpConstReal(0), new ExpConstReal(4)),
                new ExpConstUReal(new ExpConstReal(2), new ExpConstReal(3))
        };
        op = ExpStdOp.create("-", args);
        assertEquals((new URealValue(-2, 5)), (e.eval(op, state)), op.toString());

        // UReal(9, 0) - UReal(9, 0) -> UReal(0, 0)
        args = new Expression[]{
                new ExpConstUReal(new ExpConstReal(9), new ExpConstReal(0)),
                new ExpConstUReal(new ExpConstReal(9), new ExpConstReal(0))
        };
        op = ExpStdOp.create("-", args);
        assertEquals((new URealValue(0, 0)), (e.eval(op, state)), op.toString());

        // UReal(5, 0) - UReal(5, 3) -> UReal(0, 3)
        args = new Expression[]{
                new ExpConstUReal(new ExpConstReal(5), new ExpConstReal(0)),
                new ExpConstUReal(new ExpConstReal(5), new ExpConstReal(3))
        };
        op = ExpStdOp.create("-", args);
        assertEquals((new URealValue(0, 3)), (e.eval(op, state)), op.toString());

        // UReal(4, 0) - UReal(8, 0) -> UReal(-4, 0)
        args = new Expression[]{
                new ExpConstUReal(new ExpConstReal(4), new ExpConstReal(0)),
                new ExpConstUReal(new ExpConstReal(8), new ExpConstReal(0))
        };
        op = ExpStdOp.create("-", args);
        assertEquals((new URealValue(-4, 0)), (e.eval(op, state)), op.toString());

        // UReal(10, 0) - UReal(10, 12) -> UReal(0, 12)
        args = new Expression[]{
                new ExpConstUReal(new ExpConstReal(10), new ExpConstReal(0)),
                new ExpConstUReal(new ExpConstReal(10), new ExpConstReal(12))
        };
        op = ExpStdOp.create("-", args);
        assertEquals((new URealValue(0, 12)), (e.eval(op, state)), op.toString());

        // UReal(9, 5) - UReal(9, 0) -> UReal(0, 5)
        args = new Expression[]{
                new ExpConstUReal(new ExpConstReal(9), new ExpConstReal(5)),
                new ExpConstUReal(new ExpConstReal(9), new ExpConstReal(0))
        };
        op = ExpStdOp.create("-", args);
        assertEquals((new URealValue(0, 5)), (e.eval(op, state)), op.toString());

        // UReal(2, 3) - UReal(2, 4) -> UReal(0, 5)
        args = new Expression[]{
                new ExpConstUReal(new ExpConstReal(2), new ExpConstReal(3)),
                new ExpConstUReal(new ExpConstReal(2), new ExpConstReal(4))
        };
        op = ExpStdOp.create("-", args);
        assertEquals((new URealValue(0, 5)), (e.eval(op, state)), op.toString());

        // UReal(6, 1) - UReal(4, 0) -> UReal(2, 1)
        args = new Expression[]{
                new ExpConstUReal(new ExpConstReal(6), new ExpConstReal(1)),
                new ExpConstUReal(new ExpConstReal(4), new ExpConstReal(0))
        };
        op = ExpStdOp.create("-", args);
        assertEquals((new URealValue(2, 1)), (e.eval(op, state)), op.toString());

        // UReal(2, 3) - UReal(5, 4) -> UReal(-3, 5)
        args = new Expression[]{
                new ExpConstUReal(new ExpConstReal(2), new ExpConstReal(3)),
                new ExpConstUReal(new ExpConstReal(5), new ExpConstReal(4))
        };
        op = ExpStdOp.create("-", args);
        assertEquals((new URealValue(-3, 5)), (e.eval(op, state)), op.toString());

    }

    @Test
    void testMinusURealxReal() throws ExpInvalidException {
        Expression[] args;
        ExpStdOp op;

        // UReal(-3.0, 0.0) - -3.0 -> UReal(0.0, 0.0)
        args = new Expression[]{
                new ExpConstUReal(new ExpConstReal(-3.0), new ExpConstReal(0.0)),
                new ExpConstReal(-3.0)
        };
        op = ExpStdOp.create("-", args);
        assertEquals((new URealValue(0.0, 0.0)), (e.eval(op, state)), op.toString());

        // UReal(-6.0, 0.0) - -1.2 -> UReal(-4.8, 0.0)
        args = new Expression[]{
                new ExpConstUReal(new ExpConstReal(-6.0), new ExpConstReal(0.0)),
                new ExpConstReal(-1.2)
        };
        op = ExpStdOp.create("-", args);
        assertEquals((new URealValue(-4.8, 0.0)), (e.eval(op, state)), op.toString());

        // UReal(-5.0, 3.0) - -5.0 -> UReal(0.0, 3.0)
        args = new Expression[]{
                new ExpConstUReal(new ExpConstReal(-5.0), new ExpConstReal(3.0)),
                new ExpConstReal(-5.0)
        };
        op = ExpStdOp.create("-", args);
        assertEquals((new URealValue(0.0, 3.0)), (e.eval(op, state)), op.toString());

        // UReal(-8.0, 5.0) - -2.0 -> UReal(-6.0, 5.0)
        args = new Expression[]{
                new ExpConstUReal(new ExpConstReal(-8.0), new ExpConstReal(5.0)),
                new ExpConstReal(-2.0)
        };
        op = ExpStdOp.create("-", args);
        assertEquals((new URealValue(-6.0, 5.0)), (e.eval(op, state)), op.toString());

        // UReal(0.0, 0.0) - 0.0 -> UReal(0.0, 0.0)
        args = new Expression[]{
                new ExpConstUReal(new ExpConstReal(0.0), new ExpConstReal(0.0)),
                new ExpConstReal(0.0)
        };
        op = ExpStdOp.create("-", args);
        assertEquals((new URealValue(0.0, 0.0)), (e.eval(op, state)), op.toString());

        // UReal(0.0, 0.0) - 3.0 -> UReal(-3.0, 0.0)
        args = new Expression[]{
                new ExpConstUReal(new ExpConstReal(0.0), new ExpConstReal(0.0)),
                new ExpConstReal(3.0)
        };
        op = ExpStdOp.create("-", args);
        assertEquals((new URealValue(-3.0, 0.0)), (e.eval(op, state)), op.toString());

        // UReal(0.0, 3.0) - 0.0 -> UReal(0.0, 3.0)
        args = new Expression[]{
                new ExpConstUReal(new ExpConstReal(0.0), new ExpConstReal(3.0)),
                new ExpConstReal(0.0)
        };
        op = ExpStdOp.create("-", args);
        assertEquals((new URealValue(0.0, 3.0)), (e.eval(op, state)), op.toString());

        // UReal(0.0, 5.0) - -5.0 -> UReal(5.0, 5.0)
        args = new Expression[]{
                new ExpConstUReal(new ExpConstReal(0.0), new ExpConstReal(5.0)),
                new ExpConstReal(-5.0)
        };
        op = ExpStdOp.create("-", args);
        assertEquals((new URealValue(5.0, 5.0)), (e.eval(op, state)), op.toString());

        // UReal(5.0, 0.0) - 5.0 -> UReal(0.0, 0.0)
        args = new Expression[]{
                new ExpConstUReal(new ExpConstReal(5.0), new ExpConstReal(0.0)),
                new ExpConstReal(5.0)
        };
        op = ExpStdOp.create("-", args);
        assertEquals((new URealValue(0.0, 0.0)), (e.eval(op, state)), op.toString());

        // UReal(3.0, 0.0) - 0.6 -> UReal(2.4, 0.0)
        args = new Expression[]{
                new ExpConstUReal(new ExpConstReal(3.0), new ExpConstReal(0.0)),
                new ExpConstReal(0.6)
        };
        op = ExpStdOp.create("-", args);
        assertEquals((new URealValue(2.4, 0.0)), (e.eval(op, state)), op.toString());

        // UReal(7.0, 3.0) - 7.0 -> UReal(0.0, 3.0)
        args = new Expression[]{
                new ExpConstUReal(new ExpConstReal(7.0), new ExpConstReal(3.0)),
                new ExpConstReal(7.0)
        };
        op = ExpStdOp.create("-", args);
        assertEquals((new URealValue(0.0, 3.0)), (e.eval(op, state)), op.toString());

        // UReal(2.0, 5.0) - 0.5 -> UReal(1.5, 5.0)
        args = new Expression[]{
                new ExpConstUReal(new ExpConstReal(2.0), new ExpConstReal(5.0)),
                new ExpConstReal(0.5)
        };
        op = ExpStdOp.create("-", args);
        assertEquals((new URealValue(1.5, 5.0)), (e.eval(op, state)), op.toString());

    }

    @Test
    void testMinusURealxInteger() throws ExpInvalidException {
        Expression[] args;
        ExpStdOp op;

        // UReal(-3, 0) - -3 -> UReal(0.0, 0.0)
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(-3), new ExpConstReal(0)),
                new ExpConstInteger(-3)
        };
        op = ExpStdOp.create("-", args);
        assertEquals((new URealValue(0.0, 0.0)), (e.eval(op, state)), op.toString());

        // UReal(-6, 0) - -12 -> UReal(6.0, 0.0)
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(-6), new ExpConstReal(0)),
                new ExpConstInteger(-12)
        };
        op = ExpStdOp.create("-", args);
        assertEquals((new URealValue(6.0, 0.0)), (e.eval(op, state)), op.toString());

        // UReal(-5, 3) - -5 -> UReal(0.0, 3.0)
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(-5), new ExpConstReal(3)),
                new ExpConstInteger(-5)
        };
        op = ExpStdOp.create("-", args);
        assertEquals((new URealValue(0.0, 3.0)), (e.eval(op, state)), op.toString());

        // UReal(-8, 5) - -2 -> UReal(-6.0, 5.0)
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(-8), new ExpConstReal(5)),
                new ExpConstInteger(-2)
        };
        op = ExpStdOp.create("-", args);
        assertEquals((new URealValue(-6.0, 5.0)), (e.eval(op, state)), op.toString());

        // UReal(0, 0) - 0 -> UReal(0.0, 0.0)
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(0), new ExpConstReal(0)),
                new ExpConstInteger(0)
        };
        op = ExpStdOp.create("-", args);
        assertEquals((new URealValue(0.0, 0.0)), (e.eval(op, state)), op.toString());

        // UReal(0, 0) - 3 -> UReal(-3.0, 0.0)
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(0), new ExpConstReal(0)),
                new ExpConstInteger(3)
        };
        op = ExpStdOp.create("-", args);
        assertEquals((new URealValue(-3.0, 0.0)), (e.eval(op, state)), op.toString());

        // UReal(0, 3) - 0 -> UReal(0.0, 3.0)
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(0), new ExpConstReal(3)),
                new ExpConstInteger(0)
        };
        op = ExpStdOp.create("-", args);
        assertEquals((new URealValue(0.0, 3.0)), (e.eval(op, state)), op.toString());

        // UReal(0, 5) - -5 -> UReal(5.0, 5.0)
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(0), new ExpConstReal(5)),
                new ExpConstInteger(-5)
        };
        op = ExpStdOp.create("-", args);
        assertEquals((new URealValue(5.0, 5.0)), (e.eval(op, state)), op.toString());

        // UReal(5, 0) - 5 -> UReal(0.0, 0.0)
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(5), new ExpConstReal(0)),
                new ExpConstInteger(5)
        };
        op = ExpStdOp.create("-", args);
        assertEquals((new URealValue(0.0, 0.0)), (e.eval(op, state)), op.toString());

        // UReal(3, 0) - 56 -> UReal(-53.0, 0.0)
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(3), new ExpConstReal(0)),
                new ExpConstInteger(56)
        };
        op = ExpStdOp.create("-", args);
        assertEquals((new URealValue(-53.0, 0.0)), (e.eval(op, state)), op.toString());

        // UReal(7, 3) - 7 -> UReal(0.0, 3.0)
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(7), new ExpConstReal(3)),
                new ExpConstInteger(7)
        };
        op = ExpStdOp.create("-", args);
        assertEquals((new URealValue(0.0, 3.0)), (e.eval(op, state)), op.toString());

        // UReal(2, 5) - 65 -> UReal(-63.0, 5.0)
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(2), new ExpConstReal(5)),
                new ExpConstInteger(65)
        };
        op = ExpStdOp.create("-", args);
        assertEquals((new URealValue(-63.0, 5.0)), (e.eval(op, state)), op.toString());
    }

    @Test
    void testDivisionURealxUReal() throws ExpInvalidException {
        Expression[] args;
        ExpStdOp op;

        // UReal(-9, 0) / UReal(-9, 0) -> UReal(1.0, 0.0)
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(-9), new ExpConstReal(0)),
                new ExpConstUReal(new ExpConstReal(-9), new ExpConstReal(0))
        };
        op = ExpStdOp.create("/", args);
        assertEquals((new URealValue(1.0, 0.0)), e.eval(op, state), op.toString());

        // UReal(-5, 0) / UReal(-5, 3) -> UReal(1.0, 0.12)
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(-5), new ExpConstReal(0)),
                new ExpConstUReal(new ExpConstReal(-5), new ExpConstReal(3))
        };
        op = ExpStdOp.create("/", args);
        assertEquals((new URealValue(1.0, 0.12)), e.eval(op, state), op.toString());

        // UReal(-4, 0) / UReal(2, 0) -> UReal(-2.0, 0.0)
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(-4), new ExpConstReal(0)),
                new ExpConstUReal(new ExpConstReal(2), new ExpConstReal(0))
        };
        op = ExpStdOp.create("/", args);
        assertEquals((new URealValue(-2.0, 0.0)), e.eval(op, state), op.toString());

        // UReal(-10, 0) / UReal(4, 1) -> UReal(-2.5, 0.0625)
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(-10), new ExpConstReal(0)),
                new ExpConstUReal(new ExpConstReal(4), new ExpConstReal(1))
        };
        op = ExpStdOp.create("/", args);
        assertEquals((new URealValue(-2.5, 0.0625)), e.eval(op, state), op.toString());

        // UReal(-9, 9) / UReal(-9, 0) -> UReal(1.0, -1.0)
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(-9), new ExpConstReal(9)),
                new ExpConstUReal(new ExpConstReal(-9), new ExpConstReal(0))
        };
        op = ExpStdOp.create("/", args);
        assertEquals((new URealValue(1.0, -1.0)), e.eval(op, state), op.toString());

        // UReal(-2, 3) / UReal(-2, 4) -> UReal(5.0, 2.915475947)
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(-2), new ExpConstReal(3)),
                new ExpConstUReal(new ExpConstReal(-2), new ExpConstReal(4))
        };
        op = ExpStdOp.create("/", args);
        assertEquals((new URealValue(1.0, 2.9154759474)), e.eval(op, state), op.toString());

        // UReal(-6, 2) / UReal(5, 0) -> UReal(-1.2, 0.4)
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(-6), new ExpConstReal(2)),
                new ExpConstUReal(new ExpConstReal(5), new ExpConstReal(0))
        };
        op = ExpStdOp.create("/", args);
        assertEquals((new URealValue(-1.2, 0.4)), e.eval(op, state), op.toString());

        // UReal(-2, 3) / UReal(2, 4) -> UReal(-5.0, 2.915475947)
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(-2), new ExpConstReal(3)),
                new ExpConstUReal(new ExpConstReal(2), new ExpConstReal(4))
        };
        op = ExpStdOp.create("/", args);
        assertEquals((new URealValue(-1.0, 2.9154759474)), e.eval(op, state), op.toString());

        // UReal(0, 0) / UReal(0, 0) -> Undefined : OclVoid
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(0), new ExpConstReal(0)),
                new ExpConstUReal(new ExpConstReal(0), new ExpConstReal(0))
        };
        op = ExpStdOp.create("/", args);
        assertTrue(e.eval(op, state).isUndefined(), op.toString());

        // UReal(0, 0) / UReal(0, 4) -> Undefined : OclVoid
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(0), new ExpConstReal(0)),
                new ExpConstUReal(new ExpConstReal(0), new ExpConstReal(4))
        };
        op = ExpStdOp.create("/", args);
        assertTrue(e.eval(op, state).isUndefined(), op.toString());

        // UReal(0, 0) / UReal(6, 0) -> UReal(0.0, 0.0)
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(0), new ExpConstReal(0)),
                new ExpConstUReal(new ExpConstReal(6), new ExpConstReal(0))
        };
        op = ExpStdOp.create("/", args);
        assertEquals((new URealValue(0.0, 0.0)), e.eval(op, state), op.toString());

        // UReal(0, 0) / UReal(7, 3) -> UReal(0.0, 0.06122449)
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(0), new ExpConstReal(0)),
                new ExpConstUReal(new ExpConstReal(7), new ExpConstReal(3))
        };
        op = ExpStdOp.create("/", args);
        assertEquals((new URealValue(0.0, 0.0612244898)), e.eval(op, state), op.toString());

        // UReal(0, 4) / UReal(0, 0) -> Undefined : OclVoid
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(0), new ExpConstReal(4)),
                new ExpConstUReal(new ExpConstReal(0), new ExpConstReal(0))
        };
        op = ExpStdOp.create("/", args);
        assertTrue(e.eval(op, state).isUndefined(), op.toString());

        // UReal(0, 4) / UReal(0, 3) -> Undefined : OclVoid
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(0), new ExpConstReal(4)),
                new ExpConstUReal(new ExpConstReal(0), new ExpConstReal(3))
        };
        op = ExpStdOp.create("/", args);
        assertTrue(e.eval(op, state).isUndefined(), op.toString());

        // UReal(0, 4) / UReal(1, 0) -> UReal(0.0, 4.0)
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(0), new ExpConstReal(4)),
                new ExpConstUReal(new ExpConstReal(1), new ExpConstReal(0))
        };
        op = ExpStdOp.create("/", args);
        assertEquals((new URealValue(0.0, 4.0)), e.eval(op, state), op.toString());

        // UReal(0, 4) / UReal(2, 3) -> UReal(0.0, 2.828427125)
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(0), new ExpConstReal(4)),
                new ExpConstUReal(new ExpConstReal(2), new ExpConstReal(3))
        };
        op = ExpStdOp.create("/", args);
        assertEquals((new URealValue(0.0, 2.8284271247)), e.eval(op, state), op.toString());

        // UReal(9, 0) / UReal(9, 0) -> UReal(1.0, 0.0)
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(9), new ExpConstReal(0)),
                new ExpConstUReal(new ExpConstReal(9), new ExpConstReal(0))
        };
        op = ExpStdOp.create("/", args);
        assertEquals((new URealValue(1.0, 0.0)), e.eval(op, state), op.toString());

        // UReal(5, 0) / UReal(5, 3) -> UReal(1.0, 0.12)
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(5), new ExpConstReal(0)),
                new ExpConstUReal(new ExpConstReal(5), new ExpConstReal(3))
        };
        op = ExpStdOp.create("/", args);
        assertEquals((new URealValue(1.0, 0.12)), e.eval(op, state), op.toString());

        // UReal(4, 0) / UReal(8, 0) -> UReal(0.5, 0.0)
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(4), new ExpConstReal(0)),
                new ExpConstUReal(new ExpConstReal(8), new ExpConstReal(0))
        };
        op = ExpStdOp.create("/", args);
        assertEquals((new URealValue(0.5, 0.0)), e.eval(op, state), op.toString());

        // UReal(10, 0) / UReal(10, 12) -> UReal(1.0, 0.12)
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(10), new ExpConstReal(0)),
                new ExpConstUReal(new ExpConstReal(10), new ExpConstReal(12))
        };
        op = ExpStdOp.create("/", args);
        assertEquals((new URealValue(1.0, 0.12)), e.eval(op, state), op.toString());

        // UReal(9, 5) / UReal(9, 0) -> UReal(1.0, 0.555555556)
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(9), new ExpConstReal(5)),
                new ExpConstUReal(new ExpConstReal(9), new ExpConstReal(0))
        };
        op = ExpStdOp.create("/", args);
        assertEquals((new URealValue(1.0, 0.5555555556)), e.eval(op, state), op.toString());

        // UReal(2, 3) / UReal(2, 4) -> UReal(5.0, 2.915475947)
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(2), new ExpConstReal(3)),
                new ExpConstUReal(new ExpConstReal(2), new ExpConstReal(4))
        };
        op = ExpStdOp.create("/", args);
        assertEquals((new URealValue(1.0, 2.9154759474)), e.eval(op, state), op.toString());

        // UReal(6, 1) / UReal(4, 0) -> UReal(1.5, 0.25)
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(6), new ExpConstReal(1)),
                new ExpConstUReal(new ExpConstReal(4), new ExpConstReal(0))
        };
        op = ExpStdOp.create("/", args);
        assertEquals((new URealValue(1.5, 0.25)), e.eval(op, state), op.toString());

        // UReal(2, 3) / UReal(5, 4) -> UReal(0.656, 1.379275172)
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(2), new ExpConstReal(3)),
                new ExpConstUReal(new ExpConstReal(5), new ExpConstReal(4))
        };
        op = ExpStdOp.create("/", args);
        assertEquals((new URealValue(0.4, 1.379275172)), e.eval(op, state), op.toString());
    }

    @Test
    void testDivisionURealxReal() throws ExpInvalidException {
        Expression[] args;
        ExpStdOp op;        // UReal(-3, 0) / -3.0 -> UReal(1.0, 0)
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(-3), new ExpConstReal(0)),
                new ExpConstReal(-3.0)
        };
        op = ExpStdOp.create("/", args);
        assertEquals((new URealValue(1.0, 0)), e.eval(op, state), op.toString());

        // UReal(-6, 0) / -1.2 -> UReal(5.0, 0)
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(-6), new ExpConstReal(0)),
                new ExpConstReal(-1.2)
        };
        op = ExpStdOp.create("/", args);
        assertEquals((new URealValue(5.0, 0)), e.eval(op, state), op.toString());

        // UReal(-5, 3) / -5.0 -> UReal(1.0, -0.6)
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(-5), new ExpConstReal(3)),
                new ExpConstReal(-5.0)
        };
        op = ExpStdOp.create("/", args);
        assertEquals((new URealValue(1.0, -0.6)), e.eval(op, state), op.toString());

        // UReal(-8, 5) / -2.0 -> UReal(4.0, -2.5)
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(-8), new ExpConstReal(5)),
                new ExpConstReal(-2.0)
        };
        op = ExpStdOp.create("/", args);
        assertEquals((new URealValue(4.0, -2.5)), e.eval(op, state), op.toString());

        // UReal(0, 0) / 0.0 -> Undefined : OclVoid
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(0), new ExpConstReal(0)),
                new ExpConstReal(0.0)
        };
        op = ExpStdOp.create("/", args);
        assertTrue(e.eval(op, state).isUndefined(), op.toString());

        // UReal(0, 0) / 3.0 -> UReal(0.0, 0)
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(0), new ExpConstReal(0)),
                new ExpConstReal(3.0)
        };
        op = ExpStdOp.create("/", args);
        assertEquals((new URealValue(0.0, 0)), e.eval(op, state), op.toString());

        // UReal(0, 3) / 0.0 -> Undefined : OclVoid
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(0), new ExpConstReal(3)),
                new ExpConstReal(0.0)
        };
        op = ExpStdOp.create("/", args);
        assertTrue(e.eval(op, state).isUndefined(), op.toString());

        // UReal(0, 5) / -5.0 -> UReal(0.0, -1)
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(0), new ExpConstReal(5)),
                new ExpConstReal(-5.0)
        };
        op = ExpStdOp.create("/", args);
        assertEquals((new URealValue(0.0, -1)), e.eval(op, state), op.toString());

        // UReal(5, 0) / 5.0 -> UReal(1.0, 0)
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(5), new ExpConstReal(0)),
                new ExpConstReal(5.0)
        };
        op = ExpStdOp.create("/", args);
        assertEquals((new URealValue(1.0, 0)), e.eval(op, state), op.toString());

        // UReal(3, 0) / 0.6 -> UReal(5.0, 0)
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(3), new ExpConstReal(0)),
                new ExpConstReal(0.6)
        };
        op = ExpStdOp.create("/", args);
        assertEquals((new URealValue(5.0, 0)), e.eval(op, state), op.toString());

        // UReal(7, 3) / 7.0 -> UReal(1.0, 0.4285714286)
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(7), new ExpConstReal(3)),
                new ExpConstReal(7.0)
        };
        op = ExpStdOp.create("/", args);
        assertEquals((new URealValue(1.0, 0.4285714286)), e.eval(op, state), op.toString());

        // UReal(2, 5) / 0.5 -> UReal(4.0, 10)
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(2), new ExpConstReal(5)),
                new ExpConstReal(0.5)
        };
        op = ExpStdOp.create("/", args);
        assertEquals((new URealValue(4.0, 10)), e.eval(op, state), op.toString());

    }

    @Test
    void testDivisionURealxInteger() throws ExpInvalidException {
        Expression[] args;
        ExpStdOp op;

        // UReal(-3, 0) / -3 -> UReal(1.0, 0)
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(-3), new ExpConstReal(0)),
                new ExpConstInteger(-3)
        };
        op = ExpStdOp.create("/", args);
        assertEquals((new URealValue(1.0, 0)), e.eval(op, state), op.toString());

        // UReal(-6, 0) / -12 -> UReal(0.5, 0)
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(-6), new ExpConstReal(0)),
                new ExpConstInteger(-12)
        };
        op = ExpStdOp.create("/", args);
        assertEquals((new URealValue(0.5, 0)), e.eval(op, state), op.toString());

        // UReal(-5, 3) / -5 -> UReal(1.0, -0.6)
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(-5), new ExpConstReal(3)),
                new ExpConstInteger(-5)
        };
        op = ExpStdOp.create("/", args);
        assertEquals((new URealValue(1.0, -0.6)), e.eval(op, state), op.toString());

        // UReal(-8, 5) / -2 -> UReal(4.0, -2.5)
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(-8), new ExpConstReal(5)),
                new ExpConstInteger(-2)
        };
        op = ExpStdOp.create("/", args);
        assertEquals((new URealValue(4.0, -2.5)), e.eval(op, state), op.toString());

        // UReal(0, 0) / 0 -> Undefined : OclVoid
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(0), new ExpConstReal(0)),
                new ExpConstInteger(0)
        };
        op = ExpStdOp.create("/", args);
        assertTrue(e.eval(op, state).isUndefined(), op.toString());

        // UReal(0, 0) / 3 -> UReal(0.0, 0)
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(0), new ExpConstReal(0)),
                new ExpConstInteger(3)
        };
        op = ExpStdOp.create("/", args);
        assertEquals((new URealValue(0.0, 0)), e.eval(op, state), op.toString());

        // UReal(0, 3) / 0 -> Undefined : OclVoid
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(0), new ExpConstReal(3)),
                new ExpConstInteger(0)
        };
        op = ExpStdOp.create("/", args);
        assertTrue(e.eval(op, state).isUndefined(), op.toString());

        // UReal(0, 5) / -5 -> UReal(0.0, -1)
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(0), new ExpConstReal(5)),
                new ExpConstInteger(-5)
        };
        op = ExpStdOp.create("/", args);
        assertEquals((new URealValue(0.0, -1)), e.eval(op, state), op.toString());

        // UReal(5, 0) / 5 -> UReal(1.0, 0)
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(5), new ExpConstReal(0)),
                new ExpConstInteger(5)
        };
        op = ExpStdOp.create("/", args);
        assertEquals((new URealValue(1.0, 0)), e.eval(op, state), op.toString());

        // UReal(3, 0) / 56 -> UReal(0.0535714286, 0)
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(3), new ExpConstReal(0)),
                new ExpConstInteger(56)
        };
        op = ExpStdOp.create("/", args);
        assertEquals((new URealValue(0.0535714286, 0)), e.eval(op, state), op.toString());

        // UReal(7, 3) / 7 -> UReal(1.0, 0.4285714286)
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(7), new ExpConstReal(3)),
                new ExpConstInteger(7)
        };
        op = ExpStdOp.create("/", args);
        assertEquals((new URealValue(1.0, 0.4285714286)), e.eval(op, state), op.toString());

        // UReal(2, 5) / 65 -> UReal(0.0, 0.0769230769)
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(2), new ExpConstReal(5)),
                new ExpConstInteger(65)
        };
        op = ExpStdOp.create("/", args);
        assertEquals((new URealValue(0.0307692308, 0.0769230769)), e.eval(op, state), op.toString());

    }

    @Test
    void testSetValue() throws ExpInvalidException {
        ExpStdOp op;
        Expression [] args;

        // UReal(-2, 3).setValue(0.0) -> UReal(0.0, 3.0) : UReal
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(-2), new ExpConstReal(3)),
                new ExpConstReal(0)
        };
        op = ExpStdOp.create("setValue", args);
        assertEquals(new URealValue(0, 3), e.eval(op, state), op.toString());

        // UReal(-2, 3).setValue(-2.0) -> UReal(-2.0, 3.0) : UReal
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(-2), new ExpConstReal(3)),
                new ExpConstReal(-2)
        };
        op = ExpStdOp.create("setValue", args);
        assertEquals(new URealValue(-2, 3), e.eval(op, state), op.toString());

        // UReal(-2, 3).setValue(-2) -> UReal(-2.0, 3.0) : UReal
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(-2), new ExpConstReal(3)),
                new ExpConstInteger(-2)
        };
        op = ExpStdOp.create("setValue", args);
        assertEquals(new URealValue(-2, 3), e.eval(op, state), op.toString());

    }

    @Test
    void testURealSetUncertainty() throws ExpInvalidException {
        Expression[] args;
        ExpStdOp op;

        // UReal(-3, 0).setUncertainty(-3)
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(-3), new ExpConstReal(0)),
                new ExpConstInteger(-3)
        };
        op = ExpStdOp.create("setUncertainty", args);
        assertEquals((new URealValue(-3, 3)), e.eval(op, state), op.toString());

        // UReal(-3, 0).setUncertainty(3)
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(-3), new ExpConstReal(0)),
                new ExpConstInteger(3)
        };
        op = ExpStdOp.create("setUncertainty", args);
        assertEquals((new URealValue(-3, 3)), e.eval(op, state), op.toString());

        // UReal(-3, 0).setUncertainty(3)
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(-3), new ExpConstReal(0)),
                new ExpConstInteger(0)
        };
        op = ExpStdOp.create("setUncertainty", args);
        assertEquals((new URealValue(-3, 0)), e.eval(op, state), op.toString());

        // UReal(-3, 0).setUncertainty(3.0)
        args = new Expression[] {
                new ExpConstUReal(new ExpConstReal(-3), new ExpConstReal(0)),
                new ExpConstReal(3)
        };
        op = ExpStdOp.create("setUncertainty", args);
        assertEquals((new URealValue(-3, 3)), e.eval(op, state), op.toString());

    }

    @Test
    void testToUInteger() throws ExpInvalidException {
        Expression[] args;
        ExpStdOp op;

        // UReal(5.0, 0.3).toUInteger() -> UInteger(5, 0.3) : UInteger
        args = new Expression[]{
                new ExpConstUReal(
                        new ExpConstReal(5),
                        new ExpConstReal(0.3)
                )
        };
        op = ExpStdOp.create("toUInteger", args);
        assertEquals(new UIntegerValue(5, 0.3), e.eval(op, state), op.toString());

        // UReal(5.5, 5).toUInteger() -> UInteger(5, 5.0) : UInteger
        args = new Expression[]{
                new ExpConstUReal(
                        new ExpConstReal(5),
                        new ExpConstReal(5)
                )
        };
        op = ExpStdOp.create("toUInteger", args);
        assertEquals(new UIntegerValue(5, 5), e.eval(op, state), op.toString());

        // UReal(0, -5).toUInteger() -> UInteger(0, 5.0) : UInteger
        args = new Expression[]{
                new ExpConstUReal(
                        new ExpConstReal(0),
                        new ExpConstReal(-5)
                )
        };
        op = ExpStdOp.create("toUInteger", args);
        assertEquals(new UIntegerValue(0, 5), e.eval(op, state), op.toString());

        // UReal(-5.3, 3.75).toUInteger() -> UInteger(-5, 3.75) : UInteger
        args = new Expression[]{
                new ExpConstUReal(
                        new ExpConstReal(-5.3),
                        new ExpConstReal(3.75)
                )
        };
        op = ExpStdOp.create("toUInteger", args);
        assertEquals(new UIntegerValue(-5, 3.75), e.eval(op, state), op.toString());

    }

}