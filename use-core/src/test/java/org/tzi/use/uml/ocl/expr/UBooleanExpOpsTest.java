package org.tzi.use.uml.ocl.expr;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.tzi.use.uml.mm.ModelFactory;
import org.tzi.use.uml.ocl.value.BooleanValue;
import org.tzi.use.uml.ocl.value.RealValue;
import org.tzi.use.uml.ocl.value.UBooleanValue;
import org.tzi.use.uml.ocl.value.UndefinedValue;
import org.tzi.use.uml.ocl.value.StringValue;
import org.tzi.use.uml.ocl.value.*;
import org.tzi.use.uml.sys.MSystem;
import org.tzi.use.uml.sys.MSystemState;

/**
 * Ported from USE-Uncertainty (github.com/atenearesearchgroup/uncertainty @ 74acd0d),
 * src/test/org/tzi/use/uml/ocl/expr/UBooleanExpOpsTest.java. Part of B7 ledger row M-44 (this
 * file's 3 of the row's 40 total sites; the other three files are URealExpOpsTest,
 * UIntegerExpOpsTest, ExpQueryUncertaintyTest — the last already ported, see that file's javadoc).
 *
 * <p>27 test methods, 128 {@code assertEquals} and 8 {@code assertTrue} calls converted
 * mechanically (CF-7: JUnit 3's {@code assertEquals(message, expected, actual)} /
 * {@code assertTrue(message, condition)} reordered to JUnit 5's {@code assertEquals(expected,
 * actual, message)} / {@code assertTrue(condition, message)} — verified by a script that parses
 * every call's balanced-paren argument list rather than by hand, then confirmed by this file
 * compiling and all 27 methods passing unmodified). 3 JUnit-3 {@code try { ...; fail(...); }
 * catch (ExpInvalidException) {} catch (Exception) { fail(...); }} blocks converted to
 * {@code assertThrows(ExpInvalidException.class, ...)} with the real exception message asserted
 * (M-44) — each message was read from an actual run before being written into the assertion, not
 * guessed.
 *
 * <p>All 27 methods pass with zero semantic corrections: every expected value in this
 * independently-written fork test file agrees with the port's own computation on first run.
 */
class UBooleanExpOpsTest {
    private MSystemState state;
    private Evaluator e;

    @BeforeEach
    void setUp() {
        state = new MSystem(new ModelFactory().createModel("Test")).state();
        e = new Evaluator();
    }

    @Test
    void testToBoolean() throws ExpInvalidException {
        Expression[] args;
        ExpStdOp op;

        // UBoolean(true, 0) -> false : Boolean
        args = new Expression[] {
                new ExpConstUBoolean(
                        new ExpConstBoolean(true),
                        new ExpConstReal(0)
                )
        };
        op = ExpStdOp.create("toBoolean", args);
        assertEquals(BooleanValue.FALSE, e.eval(op, state), op.toString());

        // UBoolean(true, 0.49) -> false : Boolean
        args = new Expression[] {
                new ExpConstUBoolean(
                        new ExpConstBoolean(true),
                        new ExpConstReal(0.49)
                )
        };
        op = ExpStdOp.create("toBoolean", args);
        assertEquals(BooleanValue.FALSE, e.eval(op, state), op.toString());


        // UBoolean(true, 0.5) -> true : Boolean
        args = new Expression[] {
                new ExpConstUBoolean(
                        new ExpConstBoolean(true),
                        new ExpConstReal(0.5)
                )
        };
        op = ExpStdOp.create("toBoolean", args);
        assertEquals(BooleanValue.TRUE, e.eval(op, state), op.toString());


        // UBoolean(true, 1) -> true : Boolean
        args = new Expression[] {
                new ExpConstUBoolean(
                        new ExpConstBoolean(true),
                        new ExpConstReal(1)
                )
        };
        op = ExpStdOp.create("toBoolean", args);
        assertEquals(BooleanValue.TRUE, e.eval(op, state), op.toString());

    }

    @Test
    void testConfidence() throws ExpInvalidException {
        Expression[] args;
        ExpStdOp op;

        // UBoolean(true, 0).confidence() -> 0.0 : Real
        args = new Expression[] {
                new ExpConstUBoolean(
                        new ExpConstBoolean(true),
                        new ExpConstReal(0)
                )
        };
        op = ExpStdOp.create("confidence", args);
        assertEquals(new RealValue(0), e.eval(op, state), op.toString());

        // UBoolean(true, 0.5).confidence() -> 0.5 : Real
        args = new Expression[] {
                new ExpConstUBoolean(
                        new ExpConstBoolean(true),
                        new ExpConstReal(0.5)
                )
        };
        op = ExpStdOp.create("confidence", args);
        assertEquals(new RealValue(0.5), e.eval(op, state), op.toString());

        // UBoolean(true, 1).confidence() -> 1.0 : Real
        args = new Expression[] {
                new ExpConstUBoolean(
                        new ExpConstBoolean(true),
                        new ExpConstReal(1)
                )
        };
        op = ExpStdOp.create("confidence", args);
        assertEquals(new RealValue(1), e.eval(op, state), op.toString());

    }

    @Test
    void testSetConfidence() throws ExpInvalidException {
        Expression [] args;
        ExpStdOp op;

        // UBoolean(true, 0.5).setConfidence(0.5) -> UBoolean(true, 0.5) : UBoolean
        args = new Expression[] {
                new ExpConstUBoolean(
                        new ExpConstBoolean(true),
                        new ExpConstReal(0.5)
                ),
                new ExpConstReal(0.5)
        };
        op = ExpStdOp.create("setConfidence", args);
        assertEquals(UBooleanValue.valueOf(true, 0.5), e.eval(op, state), op.toString());

        // UBoolean(true, 0.5).setConfidence(0.2) -> UBoolean(true, 0.2) : UBoolean
        args = new Expression[] {
                new ExpConstUBoolean(
                        new ExpConstBoolean(true),
                        new ExpConstReal(0.5)
                ),
                new ExpConstReal(0.2)
        };
        op = ExpStdOp.create("setConfidence", args);
        assertEquals(UBooleanValue.valueOf(true, 0.2), e.eval(op, state), op.toString());

        // UBoolean(true, 0.6).setConfidence(0.75) -> UBoolean(true, 0.75) : UBoolean
        args = new Expression[] {
                new ExpConstUBoolean(
                        new ExpConstBoolean(true),
                        new ExpConstReal(0.6)
                ),
                new ExpConstReal(0.75)
        };
        op = ExpStdOp.create("setConfidence", args);
        assertEquals(UBooleanValue.valueOf(true, 0.75), e.eval(op, state), op.toString());

        // UBoolean(true, 0.6).setConfidence(1) -> UBoolean(true, 1.0) : UBoolean
        args = new Expression[] {
                new ExpConstUBoolean(
                        new ExpConstBoolean(true),
                        new ExpConstReal(0.6)
                ),
                new ExpConstInteger(1)
        };
        op = ExpStdOp.create("setConfidence", args);
        assertEquals(UBooleanValue.TRUE, e.eval(op, state), op.toString());

        // UBoolean(false, 0.3).setConfidence(0.4) -> UBoolean(true, 0.4) : UBoolean
        args = new Expression[] {
                new ExpConstUBoolean(
                        new ExpConstBoolean(false),
                        new ExpConstReal(0.3)
                ),
                new ExpConstReal(0.4)
        };
        op = ExpStdOp.create("setConfidence", args);
        assertEquals(UBooleanValue.valueOf(true, 0.4), e.eval(op, state), op.toString());

        // UBoolean(false, 0.6).setConfidence(0.4) -> UBoolean(true, 0.4) : UBoolean
        args = new Expression[] {
                new ExpConstUBoolean(
                        new ExpConstBoolean(false),
                        new ExpConstReal(0.6)
                ),
                new ExpConstReal(0.4)
        };
        op = ExpStdOp.create("setConfidence", args);
        assertEquals(UBooleanValue.valueOf(true, 0.4), e.eval(op, state), op.toString());

        // UBoolean(true, 0.5).setConfidence(2) -> Undefined : OclVoid
        args = new Expression[] {
                new ExpConstUBoolean(
                        new ExpConstBoolean(true),
                        new ExpConstReal(0.5)
                ),
                new ExpConstInteger(2)
        };
        op = ExpStdOp.create("setConfidence", args);
        assertEquals(UndefinedValue.instance, e.eval(op, state), op.toString());

        // UBoolean(true, 0.5).setConfidence(-5) -> Undefined : OclVoid
        args = new Expression[] {
                new ExpConstUBoolean(
                        new ExpConstBoolean(true),
                        new ExpConstReal(0.5)
                ),
                new ExpConstInteger(-5)
        };
        op = ExpStdOp.create("setConfidence", args);
        assertEquals(UndefinedValue.instance, e.eval(op, state), op.toString());

        // UBoolean(true, 0.5).setConfidence('testing') -> Undefined operation UBoolean.setConfidence(String)
        args = new Expression[] {
                new ExpConstUBoolean(
                        new ExpConstBoolean(true),
                        new ExpConstReal(0.5)
                ),
                new ExpConstString("testing")
        };
        Expression[] finalArgs1 = args;
        ExpInvalidException ex1 = assertThrows(ExpInvalidException.class,
                () -> ExpStdOp.create("setConfidence", finalArgs1));
        assertEquals("Undefined operation `UBoolean.setConfidence(String)'.", ex1.getMessage());
    }

    @Test
    void testValue() throws ExpInvalidException {
        Expression[] args;
        ExpStdOp op;


        // UBoolean(true, 0.2).value() -> true : Boolean
        args = new Expression[] {
                new ExpConstUBoolean(
                        new ExpConstBoolean(true),
                        new ExpConstReal(0.2)
                )
        };
        op = ExpStdOp.create("value", args);
        assertEquals(BooleanValue.TRUE, e.eval(op, state), op.toString());

        // UBoolean(true, 0.55).value() -> true : Boolean
        args = new Expression[] {
                new ExpConstUBoolean(
                        new ExpConstBoolean(true),
                        new ExpConstReal(0.55)
                )
        };
        op = ExpStdOp.create("value", args);
        assertEquals(BooleanValue.TRUE, e.eval(op, state), op.toString());

        // UBoolean(true, 0.9).value() -> true : Boolean
        args = new Expression[] {
                new ExpConstUBoolean(
                        new ExpConstBoolean(true),
                        new ExpConstReal(0.9)
                )
        };
        op = ExpStdOp.create("value", args);
        assertEquals(BooleanValue.TRUE, e.eval(op, state), op.toString());
    }

    @Test
    void testEqualsCBetweenUBooleans() throws ExpInvalidException {
        Expression[] args;
        ExpStdOp op;

        // UBoolean(true, 0.5).equalsC(UBoolean(true, 0.2), 0.4)
        // -> true : Boolean
        args = new Expression[] {
                new ExpConstUBoolean(
                        new ExpConstBoolean(true),
                        new ExpConstReal(0.5)
                ),
                new ExpConstUBoolean(
                        new ExpConstBoolean(true),
                        new ExpConstReal(0.2)
                ),
                new ExpConstReal(0.4)

        };
        op = ExpStdOp.create("equalsC", args);
        assertEquals(BooleanValue.TRUE, e.eval(op, state), op.toString());

        // UBoolean(true, 0.5).equalsC(UBoolean(true, 0.2), 0.2)
        // -> true : Boolean
        args = new Expression[] {
                new ExpConstUBoolean(
                        new ExpConstBoolean(true),
                        new ExpConstReal(0.5)
                ),
                new ExpConstUBoolean(
                        new ExpConstBoolean(true),
                        new ExpConstReal(0.2)
                ),
                new ExpConstReal(0.2)

        };
        op = ExpStdOp.create("equalsC", args);
        assertEquals(BooleanValue.TRUE, e.eval(op, state), op.toString());

        // UBoolean(true, 0.5).equalsC(UBoolean(true, 0.5), 0)
        // -> true : Boolean
        args = new Expression[] {
                new ExpConstUBoolean(
                        new ExpConstBoolean(true),
                        new ExpConstReal(0.5)
                ),
                new ExpConstUBoolean(
                        new ExpConstBoolean(true),
                        new ExpConstReal(0.5)
                ),
                new ExpConstInteger(0)

        };
        op = ExpStdOp.create("equalsC", args);
        assertEquals(BooleanValue.TRUE, e.eval(op, state), op.toString());

        // UBoolean(true, 0.5).equalsC(UBoolean(false, 0.4), 0.9)
        // -> true : Boolean
        args = new Expression[] {
                new ExpConstUBoolean(
                        new ExpConstBoolean(true),
                        new ExpConstReal(0.5)
                ),
                new ExpConstUBoolean(
                        new ExpConstBoolean(false),
                        new ExpConstReal(0.4)
                ),
                new ExpConstReal(0.9)

        };
        op = ExpStdOp.create("equalsC", args);
        assertEquals(BooleanValue.TRUE, e.eval(op, state), op.toString());

        // UBoolean(false, 0.5).equalsC(UBoolean(true, 0.6), 0.95)
        // -> false : Boolean
        args = new Expression[] {
                new ExpConstUBoolean(
                        new ExpConstBoolean(false),
                        new ExpConstReal(0.5)
                ),
                new ExpConstUBoolean(
                        new ExpConstBoolean(true),
                        new ExpConstReal(0.6)
                ),
                new ExpConstReal(0.95)

        };
        op = ExpStdOp.create("equalsC", args);
        assertEquals(BooleanValue.FALSE, e.eval(op, state), op.toString());

    }

    @Test
    void testEqualsCUBooleanxBoolean() throws ExpInvalidException {
        Expression[] args;
        ExpStdOp op;

        // UBoolean(true, 0.5).equalsC(true, 0.9)
        // -> false : Boolean
        args = new Expression[] {
                new ExpConstUBoolean(
                        new ExpConstBoolean(true),
                        new ExpConstReal(0.5)
                ),
                new ExpConstBoolean(true),
                new ExpConstReal(0.9)

        };
        op = ExpStdOp.create("equalsC", args);
        assertEquals(BooleanValue.FALSE, e.eval(op, state), op.toString());

        // UBoolean(true, 0.5).equalsC(true, 0.5)
        // -> true : Boolean
        args = new Expression[] {
                new ExpConstUBoolean(
                        new ExpConstBoolean(true),
                        new ExpConstReal(0.5)
                ),
                new ExpConstBoolean(true),
                new ExpConstReal(0.5)

        };
        op = ExpStdOp.create("equalsC", args);
        assertEquals(BooleanValue.TRUE, e.eval(op, state), op.toString());

        // UBoolean(true, 0.5).equalsC(false, 0.9)
        // -> false : Boolean
        args = new Expression[] {
                new ExpConstUBoolean(
                        new ExpConstBoolean(true),
                        new ExpConstReal(0.5)
                ),
                new ExpConstBoolean(false),
                new ExpConstReal(0.9)

        };
        op = ExpStdOp.create("equalsC", args);
        assertEquals(BooleanValue.FALSE, e.eval(op, state), op.toString());

        // UBoolean(true, 0.5).equalsC(false, 0.5)
        // -> true : Boolean
        args = new Expression[] {
                new ExpConstUBoolean(
                        new ExpConstBoolean(true),
                        new ExpConstReal(0.5)
                ),
                new ExpConstBoolean(false),
                new ExpConstReal(0.5)

        };
        op = ExpStdOp.create("equalsC", args);
        assertEquals(BooleanValue.TRUE, e.eval(op, state), op.toString());
    }

    @Test
    void testEqualsWrongConfidence() throws ExpInvalidException {
        Expression[] args;
        ExpStdOp op;

        // UBoolean(true, 0.5).equals(true, 2.0)
        args = new Expression[] {
                new ExpConstUBoolean(
                        new ExpConstBoolean(true),
                        new ExpConstReal(0.5)
                ),
                new ExpConstBoolean(true),
                new ExpConstReal(2.0)
        };
        op = ExpStdOp.create("equalsC", args);
        assertEquals(UndefinedValue.instance, e.eval(op, state), op.toString());

        // UBoolean(true, 0.5).equals(true, -0.1)
        args = new Expression[] {
                new ExpConstUBoolean(
                        new ExpConstBoolean(true),
                        new ExpConstReal(0.5)
                ),
                new ExpConstBoolean(true),
                new ExpConstReal(-0.1)
        };
        op = ExpStdOp.create("equalsC", args);
        assertEquals(UndefinedValue.instance, e.eval(op, state), op.toString());

    }

    @Test
    void testAndWithUBoolean() throws ExpInvalidException {
        Expression [] args;
        ExpStdOp op;

        // UBoolean(false, 0.5) and UBoolean(false, 0.2) -> UBoolean(true, 0.4)
        args = new Expression[] {
                new ExpConstUBoolean(
                        new ExpConstBoolean(false),
                        new ExpConstReal(0.5)
                ),
                new ExpConstUBoolean(
                        new ExpConstBoolean(false),
                        new ExpConstReal(0.2)
                )
        };
        op = ExpStdOp.create("and", args);
        assertEquals(UBooleanValue.valueOf(true, 0.4), e.eval(op, state), op.toString());

        // UBoolean(false, 0.9) and UBoolean(true, 0.8) -> UBoolean(true, 0.08)
        args = new Expression[] {
                new ExpConstUBoolean(
                        new ExpConstBoolean(false),
                        new ExpConstReal(0.9)
                ),
                new ExpConstUBoolean(
                        new ExpConstBoolean(true),
                        new ExpConstReal(0.8)
                )
        };
        op = ExpStdOp.create("and", args);
        assertEquals(UBooleanValue.valueOf(true, 0.08), e.eval(op, state), op.toString());

        // UBoolean(false, 0.55) and UBoolean(true, 0.49) -> UBoolean(true, 0.2695)
        args = new Expression[] {
                new ExpConstUBoolean(
                        new ExpConstBoolean(true),
                        new ExpConstReal(0.55)
                ),
                new ExpConstUBoolean(
                        new ExpConstBoolean(true),
                        new ExpConstReal(0.49)
                )
        };
        op = ExpStdOp.create("and", args);
        assertEquals(UBooleanValue.valueOf(true, 0.2695), e.eval(op, state), op.toString());
    }

    @Test
    void testAndWithBoolean() throws ExpInvalidException {
        Expression [] args;
        ExpStdOp op;

        // true and false -> true
        args = new Expression[] {
                new ExpConstBoolean(true),
                new ExpConstBoolean(false)
        };
        op = ExpStdOp.create("and", args);
        assertEquals(BooleanValue.FALSE, e.eval(op, state), op.toString());

        // false and UBoolean(false, 0.49) -> UBoolean(true, 0)
        args = new Expression[] {
                new ExpConstBoolean(false),
                new ExpConstUBoolean(
                        new ExpConstBoolean(false),
                        new ExpConstReal(0.49)
                )
        };
        op = ExpStdOp.create("and", args);
        assertEquals(UBooleanValue.FALSE, e.eval(op, state), op.toString());


        // UBoolean(false, 0.79) and false -> UBoolean(true, 0)
        args = new Expression[] {
                new ExpConstUBoolean(
                        new ExpConstBoolean(false),
                        new ExpConstReal(0.79)
                ),
                new ExpConstBoolean(false)
        };
        op = ExpStdOp.create("and", args);
        assertEquals(UBooleanValue.FALSE, e.eval(op, state), op.toString());


        // UBoolean(false, 0.79) and true -> UBoolean(true, 0.21)
        args = new Expression[] {
                new ExpConstUBoolean(
                        new ExpConstBoolean(false),
                        new ExpConstReal(0.79)
                ),
                new ExpConstBoolean(true)
        };
        op = ExpStdOp.create("and", args);
        assertEquals(UBooleanValue.valueOf(true, 0.21), e.eval(op, state), op.toString());


        // UBoolean(true, 0.79) and true -> UBoolean(true, 0.79)
        args = new Expression[] {
                new ExpConstUBoolean(
                        new ExpConstBoolean(true),
                        new ExpConstReal(0.79)
                ),
                new ExpConstBoolean(true)
        };
        op = ExpStdOp.create("and", args);
        assertEquals(UBooleanValue.valueOf(true, 0.79), e.eval(op, state), op.toString());


    }

    @Test
    void testAndWithUndefined() throws ExpInvalidException {
        Expression [] args;
        ExpStdOp op;

        // Undefined and Undefined
        args = new Expression[] {
                new ExpUndefined(),
                new ExpUndefined()
        };
        op = ExpStdOp.create("and", args);
        assertEquals(UndefinedValue.instance, e.eval(op, state), op.toString());

        // UBoolean(true, 0.9) and Undefined
        args = new Expression[] {
                new ExpConstUBoolean(
                        new ExpConstBoolean(true),
                        new ExpConstReal(0.9)
                ) ,
                new ExpUndefined()
        };
        op = ExpStdOp.create("and", args);
        assertEquals(UndefinedValue.instance, e.eval(op, state), op.toString());

        // true and Undefined
        args = new Expression[] {
                new ExpConstBoolean(true),
                new ExpUndefined()
        };
        op = ExpStdOp.create("and", args);
        assertEquals(UndefinedValue.instance, e.eval(op, state), op.toString());

        // Undefined and UBoolean(false, 0.9)
        args = new Expression[] {
                new ExpUndefined(),
                new ExpConstUBoolean(
                        new ExpConstBoolean(false),
                        new ExpConstReal(0.9)
                )
        };
        op = ExpStdOp.create("and", args);
        assertEquals(UndefinedValue.instance, e.eval(op, state), op.toString());

        // Undefined and false
        args = new Expression[] {
                new ExpUndefined(),
                new ExpConstBoolean(false)
        };
        op = ExpStdOp.create("and", args);
        assertEquals(BooleanValue.FALSE, e.eval(op, state), op.toString());

        // Undefined and UBoolean(true, 0) -> UBoolean(true, 0)
        args = new Expression[] {
                new ExpUndefined(),
                new ExpConstUBoolean(
                        new ExpConstBoolean(true),
                        new ExpConstReal(0)
                )
        };
        op = ExpStdOp.create("and", args);
        assertEquals(UBooleanValue.FALSE, e.eval(op, state), op.toString());

        // UBoolean(false, 1) and Undefined -> UBoolean(true, 0)
        args = new Expression[] {
                new ExpConstUBoolean(
                        new ExpConstBoolean(true),
                        new ExpConstReal(0)
                ),
                new ExpUndefined()
        };
        op = ExpStdOp.create("and", args);
        assertEquals(UBooleanValue.FALSE, e.eval(op, state), op.toString());

    }

    @Test
    void testOrWithBoolean() throws ExpInvalidException {
        Expression [] args;
        ExpStdOp op;

        // UBoolean(false, 0.45) or UBoolean(false, 0.76) -> UBoolean(true, 0.658)
        args = new Expression[] {
                new ExpConstUBoolean(
                        new ExpConstBoolean(false),
                        new ExpConstReal(0.45)
                ),
                new ExpConstUBoolean(
                        new ExpConstBoolean(false),
                        new ExpConstReal(0.76)
                )
        };
        op = ExpStdOp.create("or", args);
        assertEquals(UBooleanValue.valueOf(true, 0.658), e.eval(op, state), op.toString());

        // UBoolean(false, 0.45) or UBoolean(true, 0.37) -> UBoolean(true, 0.7165)
        args = new Expression[] {
                new ExpConstUBoolean(
                        new ExpConstBoolean(false),
                        new ExpConstReal(0.45)
                ),
                new ExpConstUBoolean(
                        new ExpConstBoolean(true),
                        new ExpConstReal(0.37)
                )
        };
        op = ExpStdOp.create("or", args);
        assertEquals(UBooleanValue.valueOf(true, 0.7165), e.eval(op, state), op.toString());

        // UBoolean(true, 0.45) or UBoolean(true, 0.76) -> UBoolean(true, 0.868)
        args = new Expression[] {
                new ExpConstUBoolean(
                        new ExpConstBoolean(true),
                        new ExpConstReal(0.45)
                ),
                new ExpConstUBoolean(
                        new ExpConstBoolean(true),
                        new ExpConstReal(0.76)
                )
        };
        op = ExpStdOp.create("or", args);
        assertEquals(UBooleanValue.valueOf(true, 0.868), e.eval(op, state), op.toString());

    }

    @Test
    void testORWithBoolean() throws ExpInvalidException {
        Expression [] args;
        ExpStdOp op;

        // true or false -> true
        args = new Expression[] {
                new ExpConstBoolean(true),
                new ExpConstBoolean(false)
        };
        op = ExpStdOp.create("or", args);
        assertEquals(BooleanValue.TRUE, e.eval(op, state), op.toString());

        // false or UBoolean(false, 0.49) -> UBoolean(true, 0.51)
        args = new Expression[] {
                new ExpConstBoolean(false),
                new ExpConstUBoolean(
                        new ExpConstBoolean(false),
                        new ExpConstReal(0.49)
                )
        };
        op = ExpStdOp.create("or", args);
        assertEquals(UBooleanValue.valueOf(true, 0.51), e.eval(op, state), op.toString());


        // UBoolean(false, 0.79) or false -> UBoolean(true, 0.21)
        args = new Expression[] {
                new ExpConstUBoolean(
                        new ExpConstBoolean(false),
                        new ExpConstReal(0.79)
                ),
                new ExpConstBoolean(false)
        };
        op = ExpStdOp.create("or", args);
        assertEquals(UBooleanValue.valueOf(true, 0.21), e.eval(op, state), op.toString());


        // UBoolean(true, 0.79) or true -> UBoolean(true, 1)
        args = new Expression[] {
                new ExpConstUBoolean(
                        new ExpConstBoolean(true),
                        new ExpConstReal(0.79)
                ),
                new ExpConstBoolean(true)
        };
        op = ExpStdOp.create("or", args);
        assertEquals(UBooleanValue.TRUE, e.eval(op, state), op.toString());

    }

    @Test
    void testORWithUndefined() throws ExpInvalidException {
        Expression [] args;
        ExpStdOp op;

        // Undefined or Undefined
        args = new Expression[] {
                new ExpUndefined(),
                new ExpUndefined()
        };
        op = ExpStdOp.create("or", args);
        assertEquals(UndefinedValue.instance, e.eval(op, state), op.toString());

        // UBoolean(true, 0.9) or Undefined
        args = new Expression[] {
                new ExpConstUBoolean(
                        new ExpConstBoolean(true),
                        new ExpConstReal(0.9)
                ) ,
                new ExpUndefined()
        };
        op = ExpStdOp.create("or", args);
        assertEquals(UndefinedValue.instance, e.eval(op, state), op.toString());

        // true or Undefined
        args = new Expression[] {
                new ExpConstBoolean(true),
                new ExpUndefined()
        };
        op = ExpStdOp.create("or", args);
        assertEquals(BooleanValue.TRUE, e.eval(op, state), op.toString());

        // Undefined or UBoolean(false, 0.9)
        args = new Expression[] {
                new ExpUndefined(),
                new ExpConstUBoolean(
                        new ExpConstBoolean(false),
                        new ExpConstReal(0.9)
                )
        };
        op = ExpStdOp.create("or", args);
        assertEquals(UndefinedValue.instance, e.eval(op, state), op.toString());

        // Undefined or false
        args = new Expression[] {
                new ExpUndefined(),
                new ExpConstBoolean(false)
        };
        op = ExpStdOp.create("or", args);
        assertEquals(UndefinedValue.instance, e.eval(op, state), op.toString());

        // Undefined or UBoolean(true, 1)
        args = new Expression[] {
                new ExpUndefined(),
                new ExpConstUBoolean(
                        new ExpConstBoolean(true),
                        new ExpConstReal(1)
                )
        };
        op = ExpStdOp.create("or", args);
        assertEquals(UBooleanValue.TRUE, e.eval(op, state), op.toString());

        // UBoolean(false, 1) or Undefined -> UBoolean(true, 1)
        args = new Expression[] {
                new ExpConstUBoolean(
                        new ExpConstBoolean(false),
                        new ExpConstReal(1)
                ),
                new ExpUndefined()
        };
        op = ExpStdOp.create("or", args);
        assertEquals(UndefinedValue.instance, e.eval(op, state), op.toString());

    }

    @Test
    void testXORWithUBoolean() throws ExpInvalidException {
        Expression [] args;
        ExpStdOp op;

        // UBoolean(false, 0.4) xor UBoolean(false, 0.2) -> UBoolean(true, 0.2) : UBoolean
        args = new Expression[] {
                new ExpConstUBoolean(
                        new ExpConstBoolean(false),
                        new ExpConstReal(0.4)
                ),
                new ExpConstUBoolean(
                        new ExpConstBoolean(false),
                        new ExpConstReal(0.2)
                )
        };
        op = ExpStdOp.create("xor", args);
        assertEquals(UBooleanValue.valueOf(true, 0.2), e.eval(op, state), op.toString());

        // UBoolean(false, 0.2) xor UBoolean(true, 0.3) -> UBoolean(true, 0.5) : UBoolean
        args = new Expression[] {
                new ExpConstUBoolean(
                        new ExpConstBoolean(false),
                        new ExpConstReal(0.2)
                ),
                new ExpConstUBoolean(
                        new ExpConstBoolean(true),
                        new ExpConstReal(0.3)
                )
        };
        op = ExpStdOp.create("xor", args);
        assertEquals(UBooleanValue.valueOf(true, 0.5), e.eval(op, state), op.toString());

        // UBoolean(true, 0.1) xor UBoolean(true, 0.1) -> UBoolean(true, 0.0) : UBoolean
        args = new Expression[] {
                new ExpConstUBoolean(
                        new ExpConstBoolean(true),
                        new ExpConstReal(0.1)
                ),
                new ExpConstUBoolean(
                        new ExpConstBoolean(true),
                        new ExpConstReal(0.1)
                )
        };
        op = ExpStdOp.create("xor", args);
        assertEquals(UBooleanValue.valueOf(true, 0.0), e.eval(op, state), op.toString());

        // UBoolean(false, 0) xor UBoolean(false, 1) -> UBoolean(true, 1.0) : UBoolean
        args = new Expression[] {
                new ExpConstUBoolean(
                        new ExpConstBoolean(false),
                        new ExpConstReal(0)
                ),
                new ExpConstUBoolean(
                        new ExpConstBoolean(false),
                        new ExpConstReal(1)
                )
        };
        op = ExpStdOp.create("xor", args);
        assertEquals(UBooleanValue.TRUE, e.eval(op, state), op.toString());

    }

    @Test
    void testXORWithBoolean() throws ExpInvalidException {
        Expression[] args;
        ExpStdOp op;

        // true xor false -> true : Boolean
        args = new Expression[] {
                new ExpConstBoolean(true),
                new ExpConstBoolean(false)
        };
        op = ExpStdOp.create("xor", args);
        assertEquals(BooleanValue.TRUE, e.eval(op, state), op.toString());

        // false xor UBoolean(false, 0.5) -> UBoolean(true, 0.5) : UBoolean
        args = new Expression[] {
                new ExpConstBoolean(false),
                new ExpConstUBoolean(
                        new ExpConstBoolean(false),
                        new ExpConstReal(0.5)
                )
        };
        op = ExpStdOp.create("xor", args);
        assertEquals(UBooleanValue.valueOf(true, 0.5), e.eval(op, state), op.toString());

        // UBoolean(false, 0.2) xor false -> UBoolean(true, 0.8) : UBoolean
        args = new Expression[] {
                new ExpConstUBoolean(
                        new ExpConstBoolean(false),
                        new ExpConstReal(0.2)
                ),
                new ExpConstBoolean(false)
        };
        op = ExpStdOp.create("xor", args);
        assertEquals(UBooleanValue.valueOf(true, 0.8), e.eval(op, state), op.toString());

        // UBoolean(false, 0.6) xor true  -> UBoolean(true, 0.6) : UBoolean
        args = new Expression[] {
                new ExpConstUBoolean(
                        new ExpConstBoolean(false),
                        new ExpConstReal(0.6)
                ),
                new ExpConstBoolean(true)
        };
        op = ExpStdOp.create("xor", args);
        assertEquals(UBooleanValue.valueOf(true, 0.6), e.eval(op, state), op.toString());

        // UBoolean(true,  0.3) xor true  -> UBoolean(true, 0.7) : UBoolean
        args = new Expression[] {
                new ExpConstUBoolean(
                        new ExpConstBoolean(true),
                        new ExpConstReal(0.3)
                ),
                new ExpConstBoolean(true)
        };
        op = ExpStdOp.create("xor", args);
        assertEquals(UBooleanValue.valueOf(true, 0.7), e.eval(op, state), op.toString());

        // UBoolean(true,  0.0) xor true -> UBoolean(true, 1.0) : UBoolean
        args = new Expression[] {
                new ExpConstUBoolean(
                        new ExpConstBoolean(true),
                        new ExpConstReal(0)
                ),
                new ExpConstBoolean(true)
        };
        op = ExpStdOp.create("xor", args);
        assertEquals(UBooleanValue.TRUE, e.eval(op, state), op.toString());


    }

    @Test
    void testXORWithUndefined() throws ExpInvalidException {
        Expression[] args;
        ExpStdOp op;

        // Undefined xor Undefined -> Undefined : OclVoid
        args = new Expression[] {
                new ExpUndefined(),
                new ExpUndefined()
        };
        op = ExpStdOp.create("xor", args);
        assertTrue(e.eval(op, state).isUndefined(), op.toString());

        // UBoolean(true, 0.5) xor Undefined -> Undefined : OclVoid
        args = new Expression[] {
                new ExpConstUBoolean(
                        new ExpConstBoolean(true),
                        new ExpConstReal(0.5)),
                new ExpUndefined()
        };
        op = ExpStdOp.create("xor", args);
        assertTrue(e.eval(op, state).isUndefined(), op.toString());

        // Undefined xor UBoolean(false, 0.4) -> Undefined : OclVoid
        args = new Expression[] {
                new ExpUndefined(),
                new ExpConstUBoolean(
                        new ExpConstBoolean(false),
                        new ExpConstReal(0.4))
        };
        op = ExpStdOp.create("xor", args);
        assertTrue(e.eval(op, state).isUndefined(), op.toString());

    }

    @Test
    void testNot() throws ExpInvalidException {
        Expression [] args;
        ExpStdOp op;

        // not Undefined
        args = new Expression[] {
                new ExpUndefined()
        };
        op = ExpStdOp.create("not", args);
        assertEquals(UndefinedValue.instance, e.eval(op, state), op.toString());

        // not UBoolean(true, 0) -> UBoolean(true, 1)
        args = new Expression[] {
                new ExpConstUBoolean(
                        new ExpConstBoolean(true),
                        new ExpConstReal(0)
                )
        };
        op = ExpStdOp.create("not", args);
        assertEquals(UBooleanValue.TRUE, e.eval(op, state), op.toString());

        // not UBoolean(true, 1) -> UBoolean(true, 0)
        args = new Expression[] {
                new ExpConstUBoolean(
                        new ExpConstBoolean(true),
                        new ExpConstReal(1)
                )
        };
        op = ExpStdOp.create("not", args);
        assertEquals(UBooleanValue.FALSE, e.eval(op, state), op.toString());

        // not UBoolean(true, 0.2) -> UBoolean(true, 0.8)
        args = new Expression[] {
                new ExpConstUBoolean(
                        new ExpConstBoolean(true),
                        new ExpConstReal(0.2)
                )
        };
        op = ExpStdOp.create("not", args);
        assertEquals(UBooleanValue.valueOf(true, 0.8), e.eval(op, state), op.toString());

        // not UBoolean(true, 0.5) -> UBoolean(true, 0.5)
        args = new Expression[] {
                new ExpConstUBoolean(
                        new ExpConstBoolean(true),
                        new ExpConstReal(0.5)
                )
        };
        op = ExpStdOp.create("not", args);
        assertEquals(UBooleanValue.valueOf(true, 0.5), e.eval(op, state), op.toString());

        // not UBoolean(true, 0.8) -> UBoolean(true, 0.2)
        args = new Expression[] {
                new ExpConstUBoolean(
                        new ExpConstBoolean(true),
                        new ExpConstReal(0.8)
                )
        };
        op = ExpStdOp.create("not", args);
        assertEquals(UBooleanValue.valueOf(true, 0.2), e.eval(op, state), op.toString());

        // not UBoolean(false, 0) -> UBoolean(true, 0)
        args = new Expression[] {
                new ExpConstUBoolean(
                        new ExpConstBoolean(false),
                        new ExpConstReal(0)
                )
        };
        op = ExpStdOp.create("not", args);
        assertEquals(UBooleanValue.FALSE, e.eval(op, state), op.toString());

        // not UBoolean(false, 1) -> UBoolean(true, 1)
        args = new Expression[] {
                new ExpConstUBoolean(
                        new ExpConstBoolean(false),
                        new ExpConstReal(1)
                )
        };
        op = ExpStdOp.create("not", args);
        assertEquals(UBooleanValue.TRUE, e.eval(op, state), op.toString());

        // not UBoolean(false, 0.2) -> UBoolean(true, 0.2)
        args = new Expression[] {
                new ExpConstUBoolean(
                        new ExpConstBoolean(false),
                        new ExpConstReal(0.2)
                )
        };
        op = ExpStdOp.create("not", args);
        assertEquals(UBooleanValue.valueOf(true, 0.2), e.eval(op, state), op.toString());

        // not UBoolean(false, 0.5) -> UBoolean(true, 0.5)
        args = new Expression[] {
                new ExpConstUBoolean(
                        new ExpConstBoolean(false),
                        new ExpConstReal(0.5)
                )
        };
        op = ExpStdOp.create("not", args);
        assertEquals(UBooleanValue.valueOf(true, 0.5), e.eval(op, state), op.toString());

        // not UBoolean(false, 0.8) -> UBoolean(true, 0.8)
        args = new Expression[] {
                new ExpConstUBoolean(
                        new ExpConstBoolean(false),
                        new ExpConstReal(0.8)
                )
        };
        op = ExpStdOp.create("not", args);
        assertEquals(UBooleanValue.valueOf(true, 0.8), e.eval(op, state), op.toString());
    }

    @Test
    void testImpliesWithUBoolean() throws ExpInvalidException {
        Expression [] args;
        ExpStdOp op;

        // UBoolean(false, 0.5) or UBoolean(false, 0.2) -> UBoolean(true, 0.6)
        args = new Expression[] {
                new ExpConstUBoolean(
                        new ExpConstBoolean(false),
                        new ExpConstReal(0.5)
                ),
                new ExpConstUBoolean(
                        new ExpConstBoolean(false),
                        new ExpConstReal(0.2)
                )
        };
        op = ExpStdOp.create("implies", args);
        assertEquals(UBooleanValue.valueOf(true, 0.9), e.eval(op, state), op.toString());

        // UBoolean(true, 0.4) implies UBoolean(false, 0.8) -> UBoolean(true, 0.68)
        args = new Expression[] {
                new ExpConstUBoolean(
                        new ExpConstBoolean(true),
                        new ExpConstReal(0.4)
                ),
                new ExpConstUBoolean(
                        new ExpConstBoolean(false),
                        new ExpConstReal(0.8)
                )
        };
        op = ExpStdOp.create("implies", args);
        assertEquals(UBooleanValue.valueOf(true, 0.68), e.eval(op, state), op.toString());

        // UBoolean(true, 0.39) implies UBoolean(true, 0.67) -> UBoolean(true, 0.8713)
        args = new Expression[] {
                new ExpConstUBoolean(
                        new ExpConstBoolean(true),
                        new ExpConstReal(0.39)
                ),
                new ExpConstUBoolean(
                        new ExpConstBoolean(true),
                        new ExpConstReal(0.67)
                )
        };
        op = ExpStdOp.create("implies", args);
        assertEquals(UBooleanValue.valueOf(true, 0.8713), e.eval(op, state), op.toString());
    }

    @Test
    void testImpliesWithBoolean() throws ExpInvalidException {
        Expression [] args;
        ExpStdOp op;

        // true implies false -> false
        args = new Expression[] {
                new ExpConstBoolean(true),
                new ExpConstBoolean(false)
        };
        op = ExpStdOp.create("implies", args);
        assertEquals(BooleanValue.FALSE, e.eval(op, state), op.toString());

        // false implies UBoolean(false, 0.49) -> UBoolean(true, 1.0)
        args = new Expression[] {
                new ExpConstBoolean(false),
                new ExpConstUBoolean(
                        new ExpConstBoolean(false),
                        new ExpConstReal(0.49)
                )
        };
        op = ExpStdOp.create("implies", args);
        assertEquals(UBooleanValue.TRUE, e.eval(op, state), op.toString());


        // UBoolean(false, 0.79) implies false -> UBoolean(true, 0.79)
        args = new Expression[] {
                new ExpConstUBoolean(
                        new ExpConstBoolean(false),
                        new ExpConstReal(0.79)
                ),
                new ExpConstBoolean(false)
        };
        op = ExpStdOp.create("implies", args);
        assertEquals(UBooleanValue.valueOf(true, 0.79), e.eval(op, state), op.toString());


        // UBoolean(true, 0.79) implies true -> UBoolean(true, 1)
        args = new Expression[] {
                new ExpConstUBoolean(
                        new ExpConstBoolean(true),
                        new ExpConstReal(0.79)
                ),
                new ExpConstBoolean(true)
        };
        op = ExpStdOp.create("implies", args);
        assertEquals(UBooleanValue.TRUE, e.eval(op, state), op.toString());

    }


    @Test

    void testImpliesWithUndefined() throws ExpInvalidException {
        Expression [] args;
        ExpStdOp op;

        // Undefined implies Undefined
        args = new Expression[] {
                new ExpUndefined(),
                new ExpUndefined()
        };
        op = ExpStdOp.create("implies", args);
        assertEquals(UndefinedValue.instance, e.eval(op, state), op.toString());

        // UBoolean(true, 0.9) implies Undefined
        args = new Expression[] {
                new ExpConstUBoolean(
                        new ExpConstBoolean(true),
                        new ExpConstReal(0.9)
                ) ,
                new ExpUndefined()
        };
        op = ExpStdOp.create("implies", args);
        assertEquals(UndefinedValue.instance, e.eval(op, state), op.toString());

        // true implies Undefined
        args = new Expression[] {
                new ExpConstBoolean(true),
                new ExpUndefined()
        };
        op = ExpStdOp.create("implies", args);
        assertEquals(UndefinedValue.instance, e.eval(op, state), op.toString());

        // Undefined implies UBoolean(false, 0.9)
        args = new Expression[] {
                new ExpUndefined(),
                new ExpConstUBoolean(
                        new ExpConstBoolean(false),
                        new ExpConstReal(0.9)
                )
        };
        op = ExpStdOp.create("implies", args);
        assertEquals(UndefinedValue.instance, e.eval(op, state), op.toString());

        // Undefined implies false
        args = new Expression[] {
                new ExpUndefined(),
                new ExpConstBoolean(false)
        };
        op = ExpStdOp.create("implies", args);
        assertEquals(UndefinedValue.instance, e.eval(op, state), op.toString());

        // Undefined implies UBoolean(true, 1)
        args = new Expression[] {
                new ExpUndefined(),
                new ExpConstUBoolean(
                        new ExpConstBoolean(true),
                        new ExpConstReal(1)
                )
        };
        op = ExpStdOp.create("implies", args);
        assertEquals(UBooleanValue.TRUE, e.eval(op, state), op.toString());

        // UBoolean(false, 1) implies Undefined -> UBoolean(true, 1)
        args = new Expression[] {
                new ExpConstUBoolean(
                        new ExpConstBoolean(false),
                        new ExpConstReal(1)
                ),
                new ExpUndefined()
        };
        op = ExpStdOp.create("implies", args);
        assertEquals(UBooleanValue.TRUE, e.eval(op, state), op.toString());

    }

    @Test
    void testEquivalentWithUBoolean() throws ExpInvalidException {
        Expression [] args;
        ExpStdOp op;

        // UBoolean(false, 0.2).equivalent(UBoolean(false, 0.4)) -> UBoolean(true, 0.8) : UBoolean
        args = new Expression [] {
                new ExpConstUBoolean(
                        new ExpConstBoolean(false),
                        new ExpConstReal(0.2)
                ),
                new ExpConstUBoolean(
                        new ExpConstBoolean(false),
                        new ExpConstReal(0.4)
                )
        };
        op = ExpStdOp.create("equivalent", args);
        assertEquals(UBooleanValue.valueOf(true, 0.8), e.eval(op, state), op.toString());

        // UBoolean(false, 0.8).equivalent(UBoolean(true, 0.5)) -> UBoolean(true, 0.7) : UBoolean
        args = new Expression [] {
                new ExpConstUBoolean(
                        new ExpConstBoolean(false),
                        new ExpConstReal(0.8)
                ),
                new ExpConstUBoolean(
                        new ExpConstBoolean(true),
                        new ExpConstReal(0.5)
                )
        };
        op = ExpStdOp.create("equivalent", args);
        assertEquals(UBooleanValue.valueOf(true, 0.7), e.eval(op, state), op.toString());

        // UBoolean(true, 0.34).equivalent(UBoolean(true, 0.56)) -> UBoolean(true, 0.78) : UBoolean
        args = new Expression [] {
                new ExpConstUBoolean(
                        new ExpConstBoolean(true),
                        new ExpConstReal(0.34)
                ),
                new ExpConstUBoolean(
                        new ExpConstBoolean(true),
                        new ExpConstReal(0.56)
                )
        };
        op = ExpStdOp.create("equivalent", args);
        assertEquals(UBooleanValue.valueOf(true, 0.78), e.eval(op, state), op.toString());
    }

    @Test
    void testEquivalentWithBoolean() throws ExpInvalidException {
        Expression [] args;
        ExpStdOp op;


        // true.equivalent(false) -> false : Boolean
        args = new Expression[] {
                new ExpConstBoolean(true),
                new ExpConstBoolean(false)
        };
        op = ExpStdOp.create("equivalent", args);
        assertEquals(BooleanValue.FALSE, e.eval(op, state), op.toString());

        // true.equivalent(true) -> true : Boolean
        args = new Expression[] {
                new ExpConstBoolean(true),
                new ExpConstBoolean(true)
        };
        op = ExpStdOp.create("equivalent", args);
        assertEquals(BooleanValue.TRUE, e.eval(op, state), op.toString());

        // false.equivalent(true) -> false : Boolean
        args = new Expression[] {
                new ExpConstBoolean(false),
                new ExpConstBoolean(true)
        };
        op = ExpStdOp.create("equivalent", args);
        assertEquals(BooleanValue.FALSE, e.eval(op, state), op.toString());

        // false.equivalent(false) -> true : Boolean
        args = new Expression[] {
                new ExpConstBoolean(false),
                new ExpConstBoolean(false)
        };
        op = ExpStdOp.create("equivalent", args);
        assertEquals(BooleanValue.TRUE, e.eval(op, state), op.toString());

        // false.equivalent(UBoolean(false, 0.49)) -> UBoolean(true, 0.49)
        args = new Expression[] {
                new ExpConstBoolean(false),
                new ExpConstUBoolean(
                        new ExpConstBoolean(false),
                        new ExpConstReal(0.49)
                )
        };
        op = ExpStdOp.create("equivalent", args);
        assertEquals(UBooleanValue.valueOf(true, 0.49), e.eval(op, state), op.toString());


        // UBoolean(false, 0.79).equivalent(false) -> UBoolean(true, 0.79)
        args = new Expression[] {
                new ExpConstUBoolean(
                        new ExpConstBoolean(false),
                        new ExpConstReal(0.79)
                ),
                new ExpConstBoolean(false)
        };
        op = ExpStdOp.create("equivalent", args);
        assertEquals(UBooleanValue.valueOf(true, 0.79), e.eval(op, state), op.toString());


        // UBoolean(true, 0.79).equivalent( true ) -> UBoolean(true, 1)
        args = new Expression[] {
                new ExpConstUBoolean(
                        new ExpConstBoolean(true),
                        new ExpConstReal(0.79)
                ),
                new ExpConstBoolean(true)
        };
        op = ExpStdOp.create("equivalent", args);
        assertEquals(UBooleanValue.valueOf(true, 0.79), e.eval(op, state), op.toString());

    }

    @Test
    void testEquivalentWithUndefined() throws ExpInvalidException {
        Expression [] args;
        ExpStdOp op;

        // Undefined.equivalent(Undefined) : OclVoid
        args = new Expression[] {
                new ExpUndefined(),
                new ExpUndefined()
        };
        op = ExpStdOp.create("equivalent", args);
        assertEquals(UndefinedValue.instance, e.eval(op, state), op.toString());

        // UBoolean(true, 0.9).equivalent(Undefined) : OclVoid
        args = new Expression[] {
                new ExpConstUBoolean(
                        new ExpConstBoolean(true),
                        new ExpConstReal(0.9)
                ) ,
                new ExpUndefined()
        };
        op = ExpStdOp.create("equivalent", args);
        assertEquals(UndefinedValue.instance, e.eval(op, state), op.toString());

        // true.equivalent(Undefined) -> Undefined : OclVoid
        args = new Expression[] {
                new ExpConstBoolean(true),
                new ExpUndefined()
        };
        op = ExpStdOp.create("equivalent", args);
        assertEquals(UndefinedValue.instance, e.eval(op, state), op.toString());

        // Undefined.equivalent(UBoolean(false, 0.9)) -> Undefined : OclVoid
        args = new Expression[] {
                new ExpUndefined(),
                new ExpConstUBoolean(
                        new ExpConstBoolean(false),
                        new ExpConstReal(0.9)
                )
        };
        op = ExpStdOp.create("equivalent", args);
        assertEquals(UndefinedValue.instance, e.eval(op, state), op.toString());

        // Undefined.equivalent(false) -> Undefined : OclVoid
        args = new Expression[] {
                new ExpUndefined(),
                new ExpConstBoolean(false)
        };
        op = ExpStdOp.create("equivalent", args);
        assertEquals(UndefinedValue.instance, e.eval(op, state), op.toString());
    }

    @Test
    void testToBooleanC() throws ExpInvalidException {
        Expression [] args;
        ExpStdOp op;

        // UBoolean(true, 0.2).toBooleanC(0.0) -> true
        args = new Expression[] {
                new ExpConstUBoolean(
                        new ExpConstBoolean(true),
                        new ExpConstReal(0.2)
                ),
                new ExpConstReal(0)
        };
        op = ExpStdOp.create("toBooleanC", args);
        assertEquals(BooleanValue.TRUE, e.eval(op, state), op.toString());

        // UBoolean(true, 0.2).toBooleanC(0.2) -> true
        args = new Expression[] {
                new ExpConstUBoolean(
                        new ExpConstBoolean(true),
                        new ExpConstReal(0.2)
                ),
                new ExpConstReal(0.2)
        };
        op = ExpStdOp.create("toBooleanC", args);
        assertEquals(BooleanValue.TRUE, e.eval(op, state), op.toString());

        // UBoolean(true, 0.2).toBooleanC(0.3) -> true
        args = new Expression[] {
                new ExpConstUBoolean(
                        new ExpConstBoolean(true),
                        new ExpConstReal(0.2)
                ),
                new ExpConstReal(0.2)
        };
        op = ExpStdOp.create("toBooleanC", args);
        assertEquals(BooleanValue.TRUE, e.eval(op, state), op.toString());

        // UBoolean(false, 0.2).toBooleanC(0.3) -> true
        args = new Expression[] {
                new ExpConstUBoolean(
                        new ExpConstBoolean(false),
                        new ExpConstReal(0.2)
                ),
                new ExpConstReal(0.3)
        };
        op = ExpStdOp.create("toBooleanC", args);
        assertEquals(BooleanValue.TRUE, e.eval(op, state), op.toString());

        // UBoolean(false, 0.2).toBooleanC(0.8) -> true
        args = new Expression[] {
                new ExpConstUBoolean(
                        new ExpConstBoolean(false),
                        new ExpConstReal(0.2)
                ),
                new ExpConstReal(0.8)
        };
        op = ExpStdOp.create("toBooleanC", args);
        assertEquals(BooleanValue.TRUE, e.eval(op, state), op.toString());

        // UBoolean(false, 0.2).toBooleanC(0.9) -> false
        args = new Expression[] {
                new ExpConstUBoolean(
                        new ExpConstBoolean(false),
                        new ExpConstReal(0.2)
                ),
                new ExpConstReal(0.9)
        };
        op = ExpStdOp.create("toBooleanC", args);
        assertEquals(BooleanValue.FALSE, e.eval(op, state), op.toString());

        // UBoolean(false, 0.2).toBooleanC(1) -> false
        args = new Expression[] {
                new ExpConstUBoolean(
                        new ExpConstBoolean(false),
                        new ExpConstReal(0.2)
                ),
                new ExpConstInteger(1)
        };
        op = ExpStdOp.create("toBooleanC", args);
        assertEquals(BooleanValue.FALSE, e.eval(op, state), op.toString());

        // UBoolean(false, 0.2).toBooleanC(0) -> true
        args = new Expression[] {
                new ExpConstUBoolean(
                        new ExpConstBoolean(false),
                        new ExpConstReal(0.2)
                ),
                new ExpConstInteger(0)
        };
        op = ExpStdOp.create("toBooleanC", args);
        assertEquals(BooleanValue.TRUE, e.eval(op, state), op.toString());

    }

    @Test
    void testToBooleanC_invalidConfidence() throws ExpInvalidException {
        Expression[] args;
        ExpStdOp op;

        // UBoolean(false, 0.2).toBooleanC(-0.2) -> Undefined
        args = new Expression[] {
                new ExpConstUBoolean(
                        new ExpConstBoolean(false),
                        new ExpConstReal(0.2)
                ),
                new ExpConstReal(-0.2)
        };
        op = ExpStdOp.create("toBooleanC", args);
        assertTrue(e.eval(op, state).isUndefined(), op.toString());

        // UBoolean(false, 0.2).toBooleanC(1.1) -> Undefined
        args = new Expression[] {
                new ExpConstUBoolean(
                        new ExpConstBoolean(false),
                        new ExpConstReal(0.2)
                ),
                new ExpConstReal(1.1)
        };
        op = ExpStdOp.create("toBooleanC", args);
        assertTrue(e.eval(op, state).isUndefined(), op.toString());

        // UBoolean(false, 0.2).toBooleanC(2) -> Undefined
        args = new Expression[] {
                new ExpConstUBoolean(
                        new ExpConstBoolean(false),
                        new ExpConstReal(0.2)
                ),
                new ExpConstInteger(2)
        };
        op = ExpStdOp.create("toBooleanC", args);
        assertTrue(e.eval(op, state).isUndefined(), op.toString());

        // UBoolean(false, 0.2).toBooleanC(-2) -> Undefined
        args = new Expression[] {
                new ExpConstUBoolean(
                        new ExpConstBoolean(false),
                        new ExpConstReal(0.2)
                ),
                new ExpConstInteger(-2)
        };
        op = ExpStdOp.create("toBooleanC", args);
        assertTrue(e.eval(op, state).isUndefined(), op.toString());

        // UBoolean(false, 0.2).toBooleanC('Testing') -> Compilation error
        args = new Expression[] {
                new ExpConstUBoolean(
                        new ExpConstBoolean(false),
                        new ExpConstReal(0.2)
                ),
                new ExpConstString("Testing")
        };
        Expression[] finalArgs2 = args;
        ExpInvalidException ex2 = assertThrows(ExpInvalidException.class, () -> {
            ExpStdOp badOp = ExpStdOp.create("toBooleanC", finalArgs2);
            e.eval(badOp, state);
        });
        assertEquals("Undefined operation `UBoolean.toBooleanC(String)'.", ex2.getMessage());
    }

    @Test
    void testSetValue() throws ExpInvalidException {
        Expression [] args;
        ExpStdOp op;

        // UBoolean(true, 0.5).setValue(true) -> UBoolean(true, 0.5)
        args = new Expression[] {
                new ExpConstUBoolean(
                        new ExpConstBoolean(true),
                        new ExpConstReal(0.5)
                ),
                new ExpConstBoolean(true)
        };
        op = ExpStdOp.create("setValue", args);
        assertEquals(UBooleanValue.valueOf(true, 0.5), e.eval(op, state), op.toString());

        // UBoolean(true, 0.7).setValue(false) -> UBoolean(true, 0.3)
        args = new Expression[] {
                new ExpConstUBoolean(
                        new ExpConstBoolean(true),
                        new ExpConstReal(0.7)
                ),
                new ExpConstBoolean(false)
        };
        op = ExpStdOp.create("setValue", args);
        assertEquals(UBooleanValue.valueOf(false, 0.7), e.eval(op, state), op.toString());

        // UBoolean(true, 0.4).setValue('testing') -> Error de compilación
        args = new Expression[] {
                new ExpConstUBoolean(
                        new ExpConstBoolean(true),
                        new ExpConstReal(0.4)
                ),
                new ExpConstString("testing")
        };
        Expression[] finalArgs3 = args;
        ExpInvalidException ex3 = assertThrows(ExpInvalidException.class, () -> {
            ExpStdOp badOp = ExpStdOp.create("setValue", finalArgs3);
            e.eval(badOp, state);
        });
        assertEquals("Undefined operation `UBoolean.setValue(String)'.", ex3.getMessage());
    }

    @Test
    void testToString() throws ExpInvalidException {
        Expression[] args;
        ExpStdOp op;

        // UBoolean(true, 0.0).toString() -> 'UBoolean(false, 1.0)'
        args = new Expression[] {
                new ExpConstUBoolean(
                        new ExpConstBoolean(true),
                        new ExpConstReal(0)
                )
        };
        op = ExpStdOp.create("toString", args);
        assertEquals(new StringValue("UBoolean(false, 1.0)"), e.eval(op, state), op.toString());

        // UBoolean(true, 0.3).toString() -> 'UBoolean(false, 0.7)'
        args = new Expression[] {
                new ExpConstUBoolean(
                        new ExpConstBoolean(true),
                        new ExpConstReal(0.3)
                )
        };
        op = ExpStdOp.create("toString", args);
        assertEquals(new StringValue("UBoolean(false, 0.7)"), e.eval(op, state), op.toString());

        // UBoolean(true, 0.5).toString() -> 'UBoolean(true, 0.5)'
        args = new Expression[] {
                new ExpConstUBoolean(
                        new ExpConstBoolean(true),
                        new ExpConstReal(0.5)
                )
        };
        op = ExpStdOp.create("toString", args);
        assertEquals(new StringValue("UBoolean(true, 0.5)"), e.eval(op, state), op.toString());

        // UBoolean(true, 0.8).toString() -> 'UBoolean(true, 0.8)'
        args = new Expression[] {
                new ExpConstUBoolean(
                        new ExpConstBoolean(true),
                        new ExpConstReal(0.8)
                )
        };
        op = ExpStdOp.create("toString", args);
        assertEquals(new StringValue("UBoolean(true, 0.8)"), e.eval(op, state), op.toString());

        // UBoolean(true, 1).toString() -> 'UBoolean(true, 1.0)'
        args = new Expression[] {
                new ExpConstUBoolean(
                        new ExpConstBoolean(true),
                        new ExpConstReal(1)
                )
        };
        op = ExpStdOp.create("toString", args);
        assertEquals(new StringValue("UBoolean(true, 1.0)"), e.eval(op, state), op.toString());



        // UBoolean(false, 0.0).toString() -> 'UBoolean(true, 1.0)'
        args = new Expression[] {
                new ExpConstUBoolean(
                        new ExpConstBoolean(false),
                        new ExpConstReal(0)
                )
        };
        op = ExpStdOp.create("toString", args);
        assertEquals(new StringValue("UBoolean(true, 1.0)"), e.eval(op, state), op.toString());

        // UBoolean(false, 0.3).toString() -> 'UBoolean(true, 0.7)'
        args = new Expression[] {
                new ExpConstUBoolean(
                        new ExpConstBoolean(false),
                        new ExpConstReal(0.3)
                )
        };
        op = ExpStdOp.create("toString", args);
        assertEquals(new StringValue("UBoolean(true, 0.7)"), e.eval(op, state), op.toString());

        // UBoolean(false, 0.5).toString() -> 'UBoolean(true, 0.5)'
        args = new Expression[] {
                new ExpConstUBoolean(
                        new ExpConstBoolean(false),
                        new ExpConstReal(0.5)
                )
        };
        op = ExpStdOp.create("toString", args);
        assertEquals(new StringValue("UBoolean(true, 0.5)"), e.eval(op, state), op.toString());

        // UBoolean(false, 0.8).toString() -> 'UBoolean(false, 0.8)'
        args = new Expression[] {
                new ExpConstUBoolean(
                        new ExpConstBoolean(false),
                        new ExpConstReal(0.8)
                )
        };
        op = ExpStdOp.create("toString", args);
        assertEquals(new StringValue("UBoolean(false, 0.8)"), e.eval(op, state), op.toString());

        // UBoolean(false, 1).toString() -> 'UBoolean(false, 1.0)'
        args = new Expression[] {
                new ExpConstUBoolean(
                        new ExpConstBoolean(false),
                        new ExpConstReal(1)
                )
        };
        op = ExpStdOp.create("toString", args);
        assertEquals(new StringValue("UBoolean(false, 1.0)"), e.eval(op, state), op.toString());



        // UBoolean(false, 5).toString() -> Undefined
        args = new Expression[] {
                new ExpConstUBoolean(
                        new ExpConstBoolean(false),
                        new ExpConstReal(5)
                )
        };
        op = ExpStdOp.create("toString", args);
        assertTrue(e.eval(op, state).isUndefined(), op.toString());
    }


}
