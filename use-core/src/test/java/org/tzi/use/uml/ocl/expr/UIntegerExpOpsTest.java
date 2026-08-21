package org.tzi.use.uml.ocl.expr;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.tzi.use.uml.mm.ModelFactory;
import org.tzi.use.uml.ocl.value.*;
import org.tzi.use.uml.sys.MSystem;
import org.tzi.use.uml.sys.MSystemState;

/**
 * Ported from USE-Uncertainty (github.com/atenearesearchgroup/uncertainty @ 74acd0d),
 * src/test/org/tzi/use/uml/ocl/expr/UIntegerExpOpsTest.java. Closes B7 ledger row M-44 (this is
 * the fourth and final of its four files — the other three, ExpQueryUncertaintyTest,
 * UBooleanExpOpsTest, URealExpOpsTest, are already ported; see their javadoc).
 *
 * <p>39 test methods, 444 {@code assertEquals} calls reordered mechanically (CF-7, same
 * balanced-paren-parsing script used for the previous three files; 3 pre-existing 2-arg
 * {@code assertEquals(expected, actual)} calls needed no reorder, since JUnit 3 and JUnit 5 agree
 * on that shape). All 39 methods pass with zero semantic corrections.
 *
 * <p>8 JUnit-3 {@code try { ...; fail(...); } catch (ExpInvalidException) {...} catch (Exception)
 * { fail(...); }} blocks converted to {@code assertThrows} per M-44. Three of the eight already
 * asserted the exception's message in the original ({@code "Value must be Integer"} x2,
 * {@code "Uncertainty must be Integer or Real"}); those are preserved verbatim, confirmed still
 * accurate by this file passing. The other five had no message assertion in the fork; one was
 * added to each, read from an actual run rather than guessed, e.g. {@code "Undefined operation
 * `UInteger.setValue(Real)'."}.
 *
 * <p>Two dead imports present in the fork's own source ({@code com.ximpleware.Expr},
 * {@code org.tzi.use.uml.ocl.type.UncertainType} — neither referenced anywhere in the file, most
 * likely IDE auto-import artifacts) are dropped. This is the only content change beyond the
 * mechanical JUnit 3-to-5 conversion; it touches zero assertions and zero test logic.
 */
class UIntegerExpOpsTest {
    private MSystemState state;
    private Evaluator e;
    private EvalContext ctx;

    @BeforeEach
    void setUp() {
        state = new MSystem(new ModelFactory().createModel("Test")).state();
        e = new Evaluator();
        ctx = new SimpleEvalContext(state, state, new VarBindings());
    }

    @Test
    void testConstWithValidValues() throws ExpInvalidException {
        Expression value, uncertainty, eUInteger;

        // UInteger(-5, 0.0) -> UInteger(-5, 0.0) : UInteger
        value = new ExpConstInteger(-5);
        uncertainty = new ExpConstReal(0);
        eUInteger = new ExpConstUInteger(value, uncertainty);
        assertEquals("UInteger(-5, 0.0)", eUInteger.toString(), eUInteger.toString() + ".toString()");
        assertEquals(new UIntegerValue(-5, 0), eUInteger.eval(ctx), eUInteger.toString());

        // UInteger(-5, 0.5) -> UInteger(-5, 0.5) : UInteger
        value = new ExpConstInteger(-5);
        uncertainty = new ExpConstReal(0.5);
        eUInteger = new ExpConstUInteger(value, uncertainty);
        assertEquals("UInteger(-5, 0.5)", eUInteger.toString(), eUInteger.toString() + ".toString()");
        assertEquals(new UIntegerValue(-5, 0.5), eUInteger.eval(ctx), eUInteger.toString());

        // UInteger(-5, -0.5) -> UInteger(-5, 0.5) : UInteger
        value = new ExpConstInteger(-5);
        uncertainty = new ExpConstReal(-0.5);
        eUInteger = new ExpConstUInteger(value, uncertainty);
        assertEquals("UInteger(-5, -0.5)", eUInteger.toString(), eUInteger.toString() + ".toString()");
        assertEquals(new UIntegerValue(-5, 0.5), eUInteger.eval(ctx), eUInteger.toString());

        // UInteger(-5, 2) -> UInteger(-5, 2) : UInteger
        value = new ExpConstInteger(-5);
        uncertainty = new ExpConstInteger(2);
        eUInteger = new ExpConstUInteger(value, uncertainty);
        assertEquals("UInteger(-5, 2)", eUInteger.toString(), eUInteger.toString() + ".toString()");
        assertEquals(new UIntegerValue(-5, 2), eUInteger.eval(ctx), eUInteger.toString());

        // UInteger(-5, -5) -> UInteger(-5, 5) : UInteger
        value = new ExpConstInteger(-5);
        uncertainty = new ExpConstInteger(-5);
        eUInteger = new ExpConstUInteger(value, uncertainty);
        assertEquals("UInteger(-5, -5)", eUInteger.toString(), eUInteger.toString() + ".toString()");
        assertEquals(new UIntegerValue(-5, 5), eUInteger.eval(ctx), eUInteger.toString());

        // UInteger(3, 39) -> UInteger(3, 39) : UInteger
        value = new ExpConstInteger(3);
        uncertainty = new ExpConstInteger(39);
        eUInteger = new ExpConstUInteger(value, uncertainty);
        assertEquals("UInteger(3, 39)", eUInteger.toString(), eUInteger.toString() + ".toString()");
        assertEquals(new UIntegerValue(3, 39), eUInteger.eval(ctx), eUInteger.toString());

        // UInteger(0, 0) -> UInteger(0, 0) : UInteger
        value = new ExpConstInteger(0);
        uncertainty = new ExpConstInteger(0);
        eUInteger = new ExpConstUInteger(value, uncertainty);
        assertEquals("UInteger(0, 0)", eUInteger.toString(), eUInteger.toString() + ".toString()");
        assertEquals(new UIntegerValue(0, 0), eUInteger.eval(ctx), eUInteger.toString());
    }

    @Test
    void testConstWithUndefined() throws ExpInvalidException {
        Expression value, uncertainty, eUInteger;

        // UInteger(Undefined, Undefined) -> Undefined : OclVoid
        value = new ExpUndefined();
        uncertainty = new ExpUndefined();
        eUInteger = new ExpConstUInteger(value, uncertainty);
        assertEquals("UInteger(null, null)", eUInteger.toString(), eUInteger.toString() + ".toString()");
        assertEquals(UndefinedValue.instance, eUInteger.eval(ctx), eUInteger.toString());

        // UInteger(Undefined, 0.34) -> Undefined : OclVoid
        value = new ExpUndefined();
        uncertainty = new ExpConstReal(0.34);
        eUInteger = new ExpConstUInteger(value, uncertainty);
        assertEquals("UInteger(null, 0.34)", eUInteger.toString(), eUInteger.toString() + ".toString()");
        assertEquals(UndefinedValue.instance, eUInteger.eval(ctx), eUInteger.toString());

        // UInteger(5, Undefined) -> Undefined : OclVoid
        value = new ExpConstInteger(5);
        uncertainty = new ExpUndefined();
        eUInteger = new ExpConstUInteger(value, uncertainty);
        assertEquals("UInteger(5, null)", eUInteger.toString(), eUInteger.toString() + ".toString()");
        assertEquals(UndefinedValue.instance, eUInteger.eval(ctx), eUInteger.toString());
    }

    @Test
    void testConstWithWrongValues() {
        Expression value, uncertainty, eUInteger;

        // UInteger(32.03, 5.3)
        value = new ExpConstReal(32.03);
        uncertainty = new ExpConstReal(5.3);
        Expression finalValue1 = value;
        Expression finalUncertainty1 = uncertainty;
        ExpInvalidException ex1 = assertThrows(ExpInvalidException.class,
                () -> new ExpConstUInteger(finalValue1, finalUncertainty1));
        assertEquals("Value must be Integer", ex1.getMessage());

        // UInteger('testing', 0.3)
        value = new ExpConstString("testing");
        uncertainty = new ExpConstReal(0.3);
        Expression finalValue2 = value;
        Expression finalUncertainty2 = uncertainty;
        ExpInvalidException ex2 = assertThrows(ExpInvalidException.class,
                () -> new ExpConstUInteger(finalValue2, finalUncertainty2));
        assertEquals("Value must be Integer", ex2.getMessage());

        // UInteger(3, 'testing')
        value = new ExpConstInteger(3);
        uncertainty = new ExpConstString("testing");
        Expression finalValue3 = value;
        Expression finalUncertainty3 = uncertainty;
        ExpInvalidException ex3 = assertThrows(ExpInvalidException.class,
                () -> new ExpConstUInteger(finalValue3, finalUncertainty3));
        assertEquals("Uncertainty must be Integer or Real", ex3.getMessage());
    }


    @Test

    void testOpValue() throws ExpInvalidException {
        Expression [] args;
        ExpStdOp op;

        // UInteger(3, 3.5).value() -> 3 : Integer
        args = new Expression[] {
                new ExpConstUInteger(
                        new ExpConstInteger(3),
                        new ExpConstReal(3.5)
                )
        };
        op = ExpStdOp.create("value", args);
        assertEquals(IntegerValue.valueOf(3), e.eval(op, state), op.toString());

        // UInteger(0, 2.3).value() -> 0 : Integer
        args = new Expression[] {
                new ExpConstUInteger(
                        new ExpConstInteger(0),
                        new ExpConstReal(2.3)
                )
        };
        op = ExpStdOp.create("value", args);
        assertEquals(IntegerValue.valueOf(0), e.eval(op, state), op.toString());

        // UInteger(-5, 0.2).value() -> -5 : Integer
        args = new Expression[] {
                new ExpConstUInteger(
                        new ExpConstInteger(-5),
                        new ExpConstReal(0.2)
                )
        };
        op = ExpStdOp.create("value", args);
        assertEquals(IntegerValue.valueOf(-5), e.eval(op, state), op.toString());
    }

    @Test
    void testOpValueUndefined() throws ExpInvalidException {
        Expression [] args;
        ExpStdOp op;

        // UInteger(null, null).value() -> Undefined : OclVoid
        args = new Expression[] {
                new ExpConstUInteger(
                        new ExpUndefined(),
                        new ExpUndefined()
                )
        };
        op = ExpStdOp.create("value", args);
        assertEquals(UndefinedValue.instance, e.eval(op, state), op.toString());

        // UInteger(3, null).value() -> Undefined : OclVoid
        args = new Expression[] {
                new ExpConstUInteger(
                        new ExpConstInteger(3),
                        new ExpUndefined()
                )
        };
        op = ExpStdOp.create("value", args);
        assertEquals(UndefinedValue.instance, e.eval(op, state), op.toString());

        // UInteger(null, 3).value() -> Undefined : OclVoid
        args = new Expression[] {
                new ExpConstUInteger(
                        new ExpUndefined(),
                        new ExpConstInteger(3)
                )
        };
        op = ExpStdOp.create("value", args);
        assertEquals(UndefinedValue.instance, e.eval(op, state), op.toString());
    }

    @Test
    void testOpSetValue() throws ExpInvalidException {
        Expression [] args;
        ExpStdOp op;

        // UInteger(3, 5).setValue(2) -> UInteger(2, 5.0) : UInteger
        args = new Expression[] {
                new ExpConstUInteger(
                        new ExpConstInteger(3),
                        new ExpConstInteger(5)
                ),
                new ExpConstInteger(2)
        };
        op = ExpStdOp.create("setValue", args);
        assertEquals(new UIntegerValue(2, 5), e.eval(op, state), op.toString());

        // UInteger(-2, 4).setValue(0) -> UInteger(0, 4.0) : UInteger
        args = new Expression[] {
                new ExpConstUInteger(
                        new ExpConstInteger(-2),
                        new ExpConstInteger(4)
                ),
                new ExpConstInteger(0)
        };
        op = ExpStdOp.create("setValue", args);
        assertEquals(new UIntegerValue(0, 4), e.eval(op, state), op.toString());

        // UInteger(0, 3).setValue(-55) -> UInteger(-55, 3.0) : UInteger
        args = new Expression[] {
                new ExpConstUInteger(
                        new ExpConstInteger(0),
                        new ExpConstInteger(3)
                ),
                new ExpConstInteger(-55)
        };
        op = ExpStdOp.create("setValue", args);
        assertEquals(new UIntegerValue(-55, 3), e.eval(op, state), op.toString());
    }

    @Test
    void testOpSetValueWrongValue() throws ExpInvalidException {
        Expression [] args;
        ExpStdOp op;

        // UInteger(3, 5).setValue(Undefined)
        args = new Expression[] {
                new ExpConstUInteger(
                        new ExpConstInteger(3),
                        new ExpConstInteger(5)
                ),
                new ExpUndefined()
        };
        Expression[] finalArgsSv1 = args;
        ExpInvalidException exSv1 = assertThrows(ExpInvalidException.class,
                () -> ExpStdOp.create("setValue", finalArgsSv1));
        assertEquals("Undefined operation `UInteger.setValue(OclVoid)'.", exSv1.getMessage());

        // UInteger(3, 5).setValue(2.5)
        args = new Expression[] {
                new ExpConstUInteger(
                        new ExpConstInteger(3),
                        new ExpConstInteger(5)
                ),
                new ExpConstReal(2.5)
        };
        Expression[] finalArgsSv2 = args;
        ExpInvalidException exSv2 = assertThrows(ExpInvalidException.class,
                () -> ExpStdOp.create("setValue", finalArgsSv2));
        assertEquals("Undefined operation `UInteger.setValue(Real)'.", exSv2.getMessage());

        // UInteger(3, 5).setValue('testing')
        args = new Expression[] {
                new ExpConstUInteger(
                        new ExpConstInteger(3),
                        new ExpConstInteger(5)
                ),
                new ExpConstString("testing")
        };
        Expression[] finalArgsSv3 = args;
        ExpInvalidException exSv3 = assertThrows(ExpInvalidException.class,
                () -> ExpStdOp.create("setValue", finalArgsSv3));
        assertEquals("Undefined operation `UInteger.setValue(String)'.", exSv3.getMessage());
    }




    @Test



    void testOpUncertainty() throws ExpInvalidException {
        Expression [] args;
        ExpStdOp op;

        // UInteger(3, 3.5).value() -> 3.5 : Real
        args = new Expression[] {
                new ExpConstUInteger(
                        new ExpConstInteger(3),
                        new ExpConstReal(3.5)
                )
        };
        op = ExpStdOp.create("uncertainty", args);
        assertEquals(new RealValue(3.5), e.eval(op, state), op.toString());

        // UInteger(0, 0).value() -> 0 : Real
        args = new Expression[] {
                new ExpConstUInteger(
                        new ExpConstInteger(0),
                        new ExpConstReal(0)
                )
        };
        op = ExpStdOp.create("uncertainty", args);
        assertEquals(new RealValue(0), e.eval(op, state), op.toString());

        // UInteger(-5, 0.2).value() -> 0.2 : Real
        args = new Expression[] {
                new ExpConstUInteger(
                        new ExpConstInteger(-5),
                        new ExpConstReal(0.2)
                )
        };
        op = ExpStdOp.create("uncertainty", args);
        assertEquals(new RealValue(0.2), e.eval(op, state), op.toString());
    }

    @Test
    void testOpUncertaintyUndefined() throws ExpInvalidException {
        Expression [] args;
        ExpStdOp op;

        // UInteger(null, null).value() -> Undefined : OclVoid
        args = new Expression[] {
                new ExpConstUInteger(
                        new ExpUndefined(),
                        new ExpUndefined()
                )
        };
        op = ExpStdOp.create("uncertainty", args);
        assertEquals(UndefinedValue.instance, e.eval(op, state), op.toString());

        // UInteger(3, null).value() -> Undefined : OclVoid
        args = new Expression[] {
                new ExpConstUInteger(
                        new ExpConstInteger(3),
                        new ExpUndefined()
                )
        };
        op = ExpStdOp.create("uncertainty", args);
        assertEquals(UndefinedValue.instance, e.eval(op, state), op.toString());

        // UInteger(null, 3).value() -> Undefined : OclVoid
        args = new Expression[] {
                new ExpConstUInteger(
                        new ExpUndefined(),
                        new ExpConstInteger(3)
                )
        };
        op = ExpStdOp.create("uncertainty", args);
        assertEquals(UndefinedValue.instance, e.eval(op, state), op.toString());
    }

    @Test
    void testOpSetUncertainty() throws ExpInvalidException {
        Expression [] args;
        ExpStdOp op;

        // UInteger(0, 3).setUncertainty(-5) -> UInteger(0, 5.0) : UInteger
        args = new Expression[] {
                new ExpConstUInteger(
                        new ExpConstInteger(0),
                        new ExpConstInteger(3)
                ),
                new ExpConstInteger(-5)
        };
        op = ExpStdOp.create("setUncertainty", args);
        assertEquals(new UIntegerValue(0, 5), e.eval(op, state), op.toString());

        // UInteger(5, 2).setUncertainty(0) -> UInteger(5, 0.0) : UInteger
        args = new Expression[] {
                new ExpConstUInteger(
                        new ExpConstInteger(5),
                        new ExpConstInteger(2)
                ),
                new ExpConstInteger(0)
        };
        op = ExpStdOp.create("setUncertainty", args);
        assertEquals(new UIntegerValue(5, 0), e.eval(op, state), op.toString());

        // UInteger(0, 3).setUncertainty(5) -> UInteger(0, 5.0) : UInteger
        args = new Expression[] {
                new ExpConstUInteger(
                        new ExpConstInteger(0),
                        new ExpConstInteger(3)
                ),
                new ExpConstInteger(5)
        };
        op = ExpStdOp.create("setUncertainty", args);
        assertEquals(new UIntegerValue(0, 5), e.eval(op, state), op.toString());

        // UInteger(0, 3).setUncertainty(5.3) -> UInteger(0, 5.3) : UInteger
        args = new Expression[] {
                new ExpConstUInteger(
                        new ExpConstInteger(0),
                        new ExpConstInteger(3)
                ),
                new ExpConstReal(5.3)
        };
        op = ExpStdOp.create("setUncertainty", args);
        assertEquals(new UIntegerValue(0, 5.3), e.eval(op, state), op.toString());

        // UInteger(0, 3).setUncertainty(0.2) -> UInteger(0, 0.2) : UInteger
        args = new Expression[] {
                new ExpConstUInteger(
                        new ExpConstInteger(0),
                        new ExpConstInteger(3)
                ),
                new ExpConstReal(0.2)
        };
        op = ExpStdOp.create("setUncertainty", args);
        assertEquals(new UIntegerValue(0, 0.2), e.eval(op, state), op.toString());

        // UInteger(0, 3).setUncertainty(-0.3) -> UInteger(0, 0.3) : UInteger
        args = new Expression[] {
                new ExpConstUInteger(
                        new ExpConstInteger(0),
                        new ExpConstInteger(3)
                ),
                new ExpConstReal(-0.3)
        };
        op = ExpStdOp.create("setUncertainty", args);
        assertEquals(new UIntegerValue(0, 0.3), e.eval(op, state), op.toString());

        // UInteger(0, 3).setUncertainty(0.0) -> UInteger(0, 0.0) : UInteger
        args = new Expression[] {
                new ExpConstUInteger(
                        new ExpConstInteger(0),
                        new ExpConstInteger(3)
                ),
                new ExpConstReal(0.0)
        };
        op = ExpStdOp.create("setUncertainty", args);
        assertEquals(new UIntegerValue(0, 0), e.eval(op, state), op.toString());

    }

    @Test
    void testOpSetUncertaintyWithWrongArgs() throws ExpInvalidException {
        Expression [] args;

        // UInteger(0, 3).setUncertainty('testing')
        args = new Expression[] {
                new ExpConstUInteger(
                        new ExpConstInteger(0),
                        new ExpConstInteger(3)
                ),
                new ExpConstString("testing")
        };
        Expression[] finalArgsSu1 = args;
        ExpInvalidException exSu1 = assertThrows(ExpInvalidException.class,
                () -> ExpStdOp.create("setUncertainty", finalArgsSu1));
        assertEquals("Undefined operation `UInteger.setUncertainty(String)'.", exSu1.getMessage());
        // UInteger(5, 2).setUncertainty(Undefined)
        args = new Expression[] {
                new ExpConstUInteger(
                        new ExpConstInteger(0),
                        new ExpConstInteger(3)
                ),
                new ExpUndefined()
        };
        Expression[] finalArgsSu2 = args;
        ExpInvalidException exSu2 = assertThrows(ExpInvalidException.class,
                () -> ExpStdOp.create("setUncertainty", finalArgsSu2));
        assertEquals("Undefined operation `UInteger.setUncertainty(OclVoid)'.", exSu2.getMessage());
    }

    @Test
    void testToInteger() throws ExpInvalidException {
        Expression [] args;
        ExpStdOp op;

        // UInteger(3, 0.3).toInteger() -> 3 : Integer
        args = new Expression[] {
                new ExpConstUInteger(
                        new ExpConstInteger(3),
                        new ExpConstReal(0.3)
                )
        };
        op = ExpStdOp.create("toInteger", args);
        assertEquals(IntegerValue.valueOf(3), e.eval(op, state), op.toString());

        // UInteger(0, 4).toInteger() -> 0 : Integer
        args = new Expression[] {
                new ExpConstUInteger(
                        new ExpConstInteger(0),
                        new ExpConstReal(4)
                )
        };
        op = ExpStdOp.create("toInteger", args);
        assertEquals(IntegerValue.valueOf(0), e.eval(op, state), op.toString());

        // UInteger(-5, 5).toInteger() -> -5 : Integer
        args = new Expression[] {
                new ExpConstUInteger(
                        new ExpConstInteger(-5),
                        new ExpConstReal(5)
                )
        };
        op = ExpStdOp.create("toInteger", args);
        assertEquals(IntegerValue.valueOf(-5), e.eval(op, state), op.toString());

    }

    @Test
    void testToUReal() throws ExpInvalidException {
        Expression [] args;
        ExpStdOp op;

        // UInteger(3, 0.5).toUReal() -> UReal(3.0, 0.5) : UReal
        args = new Expression[] {
                new ExpConstUInteger(
                        new ExpConstInteger(3),
                        new ExpConstReal(0.5)
                )
        };
        op = ExpStdOp.create("toUReal", args);
        assertEquals(new URealValue(3, 0.5), e.eval(op, state), op.toString());

        // UInteger(3, -0.5).toUReal() -> UReal(3.0, 0.5) : UReal
        args = new Expression[] {
                new ExpConstUInteger(
                        new ExpConstInteger(3),
                        new ExpConstReal(-0.5)
                )
        };
        op = ExpStdOp.create("toUReal", args);
        assertEquals(new URealValue(3, 0.5), e.eval(op, state), op.toString());

        // UInteger(0, 0).toUReal() -> UReal(0.0, 0.0) : UReal
        args = new Expression[] {
                new ExpConstUInteger(
                        new ExpConstInteger(0),
                        new ExpConstReal(0)
                )
        };
        op = ExpStdOp.create("toUReal", args);
        assertEquals(new URealValue(0, 0), e.eval(op, state), op.toString());

        // UInteger(-53, 5).toUReal() -> UReal(-53.0, 5.0) : UReal
        args = new Expression[] {
                new ExpConstUInteger(
                        new ExpConstInteger(-53),
                        new ExpConstReal(5)
                )
        };
        op = ExpStdOp.create("toUReal", args);
        assertEquals(new URealValue(-53, 5), e.eval(op, state), op.toString());
    }

    @Test
    void testToReal() throws ExpInvalidException {
        Expression [] args;
        ExpStdOp op;

        // UInteger(3, 0.3).toReal() -> 3.0 : Real
        args = new Expression[] {
                new ExpConstUInteger(
                        new ExpConstInteger(3),
                        new ExpConstReal(0.3)
                )
        };
        op = ExpStdOp.create("toReal", args);
        assertEquals(new RealValue(3), e.eval(op, state), op.toString());

        // UInteger(0, 0.5).toReal() -> 0.0 : Real
        args = new Expression[] {
                new ExpConstUInteger(
                        new ExpConstInteger(0),
                        new ExpConstReal(0.5)
                )
        };
        op = ExpStdOp.create("toReal", args);
        assertEquals(new RealValue(0), e.eval(op, state), op.toString());

        // UInteger(-3, -0.5).toReal() -> -3.0 : Real
        args = new Expression[] {
                new ExpConstUInteger(
                        new ExpConstInteger(-3),
                        new ExpConstReal(-0.5)
                )
        };
        op = ExpStdOp.create("toReal", args);
        assertEquals(new RealValue(-3), e.eval(op, state), op.toString());
    }

    @Test
    void testToString() throws ExpInvalidException {
        Expression [] args;
        ExpStdOp op;

        // UInteger(5, 0.3).toString() -> 'UInteger(5, 0.3)' : String
        args = new Expression[] {
                new ExpConstUInteger(
                        new ExpConstInteger(5),
                        new ExpConstReal(0.3)
                )
        };
        op = ExpStdOp.create("toString", args);
        assertEquals(new StringValue("UInteger(5, 0.3)"), e.eval(op, state), op.toString());

        // UInteger(5, -0.3).toString() -> 'UInteger(5, 0.3)' : String
        args = new Expression[] {
                new ExpConstUInteger(
                        new ExpConstInteger(5),
                        new ExpConstReal(-0.3)
                )
        };
        op = ExpStdOp.create("toString", args);
        assertEquals(new StringValue("UInteger(5, 0.3)"), e.eval(op, state), op.toString());

        // UInteger(-5, 0.3).toString() -> 'UInteger(-5, 0.3)' : String
        args = new Expression[] {
                new ExpConstUInteger(
                        new ExpConstInteger(-5),
                        new ExpConstReal(0.3)
                )
        };
        op = ExpStdOp.create("toString", args);
        assertEquals(new StringValue("UInteger(-5, 0.3)"), e.eval(op, state), op.toString());

    }

    @Test
    void testAddBetweenUInteger() throws ExpInvalidException {
        Expression [] args;
        ExpStdOp op;
        // UInteger(-9, 0) + UInteger(-9, 0) -> UInteger(-18, 0) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(-9, 0)),
                new ExpressionWithValue(new UIntegerValue(-9, 0))
        };
        op = ExpStdOp.create("+", args);
        assertEquals(new UIntegerValue(-18, 0), e.eval(op, state), op.toString());


        // UInteger(-7, 0) + UInteger(-7, 8) -> UInteger(-14, 8) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(-7, 0)),
                new ExpressionWithValue(new UIntegerValue(-7, 8))
        };
        op = ExpStdOp.create("+", args);
        assertEquals(new UIntegerValue(-14, 8), e.eval(op, state), op.toString());


        // UInteger(-10, 0) + UInteger(0, 0) -> UInteger(-10, 0) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(-10, 0)),
                new ExpressionWithValue(new UIntegerValue(0, 0))
        };
        op = ExpStdOp.create("+", args);
        assertEquals(new UIntegerValue(-10, 0), e.eval(op, state), op.toString());


        // UInteger(-8, 0) + UInteger(3, 5) -> UInteger(-5, 5) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(-8, 0)),
                new ExpressionWithValue(new UIntegerValue(3, 5))
        };
        op = ExpStdOp.create("+", args);
        assertEquals(new UIntegerValue(-5, 5), e.eval(op, state), op.toString());


        // UInteger(-6, 8) + UInteger(-6, 0) -> UInteger(-12, 8) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(-6, 8)),
                new ExpressionWithValue(new UIntegerValue(-6, 0))
        };
        op = ExpStdOp.create("+", args);
        assertEquals(new UIntegerValue(-12, 8), e.eval(op, state), op.toString());


        // UInteger(-9, 3) + UInteger(-9, 4) -> UInteger(-18, 5) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(-9, 3)),
                new ExpressionWithValue(new UIntegerValue(-9, 4))
        };
        op = ExpStdOp.create("+", args);
        assertEquals(new UIntegerValue(-18, 5), e.eval(op, state), op.toString());


        // UInteger(-9, 8) + UInteger(4, 0) -> UInteger(-5, 8) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(-9, 8)),
                new ExpressionWithValue(new UIntegerValue(4, 0))
        };
        op = ExpStdOp.create("+", args);
        assertEquals(new UIntegerValue(-5, 8), e.eval(op, state), op.toString());


        // UInteger(-3, 3) + UInteger(4, 4) -> UInteger(1, 5) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(-3, 3)),
                new ExpressionWithValue(new UIntegerValue(4, 4))
        };
        op = ExpStdOp.create("+", args);
        assertEquals(new UIntegerValue(1, 5), e.eval(op, state), op.toString());


        // UInteger(0, 0) + UInteger(0, 0) -> UInteger(0, 0) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(0, 0)),
                new ExpressionWithValue(new UIntegerValue(0, 0))
        };
        op = ExpStdOp.create("+", args);
        assertEquals(new UIntegerValue(0, 0), e.eval(op, state), op.toString());


        // UInteger(0, 0) + UInteger(0, 0) -> UInteger(0, 0) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(0, 0)),
                new ExpressionWithValue(new UIntegerValue(0, 0))
        };
        op = ExpStdOp.create("+", args);
        assertEquals(new UIntegerValue(0, 0), e.eval(op, state), op.toString());


        // UInteger(0, 0) + UInteger(9, 0) -> UInteger(9, 0) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(0, 0)),
                new ExpressionWithValue(new UIntegerValue(9, 0))
        };
        op = ExpStdOp.create("+", args);
        assertEquals(new UIntegerValue(9, 0), e.eval(op, state), op.toString());


        // UInteger(0, 0) + UInteger(8, 4) -> UInteger(8, 4) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(0, 0)),
                new ExpressionWithValue(new UIntegerValue(8, 4))
        };
        op = ExpStdOp.create("+", args);
        assertEquals(new UIntegerValue(8, 4), e.eval(op, state), op.toString());


        // UInteger(0, 8) + UInteger(0, 0) -> UInteger(0, 8) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(0, 8)),
                new ExpressionWithValue(new UIntegerValue(0, 0))
        };
        op = ExpStdOp.create("+", args);
        assertEquals(new UIntegerValue(0, 8), e.eval(op, state), op.toString());


        // UInteger(0, 3) + UInteger(0, 4) -> UInteger(0, 5) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(0, 3)),
                new ExpressionWithValue(new UIntegerValue(0, 4))
        };
        op = ExpStdOp.create("+", args);
        assertEquals(new UIntegerValue(0, 5), e.eval(op, state), op.toString());


        // UInteger(0, 6) + UInteger(8, 0) -> UInteger(8, 6) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(0, 6)),
                new ExpressionWithValue(new UIntegerValue(8, 0))
        };
        op = ExpStdOp.create("+", args);
        assertEquals(new UIntegerValue(8, 6), e.eval(op, state), op.toString());


        // UInteger(0, 3) + UInteger(5, 4) -> UInteger(5, 5) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(0, 3)),
                new ExpressionWithValue(new UIntegerValue(5, 4))
        };
        op = ExpStdOp.create("+", args);
        assertEquals(new UIntegerValue(5, 5), e.eval(op, state), op.toString());


        // UInteger(9, 0) + UInteger(9, 0) -> UInteger(18, 0) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(9, 0)),
                new ExpressionWithValue(new UIntegerValue(9, 0))
        };
        op = ExpStdOp.create("+", args);
        assertEquals(new UIntegerValue(18, 0), e.eval(op, state), op.toString());


        // UInteger(7, 0) + UInteger(7, 0) -> UInteger(14, 0) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(7, 0)),
                new ExpressionWithValue(new UIntegerValue(7, 0))
        };
        op = ExpStdOp.create("+", args);
        assertEquals(new UIntegerValue(14, 0), e.eval(op, state), op.toString());


        // UInteger(10, 0) + UInteger(8, 0) -> UInteger(18, 0) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(10, 0)),
                new ExpressionWithValue(new UIntegerValue(8, 0))
        };
        op = ExpStdOp.create("+", args);
        assertEquals(new UIntegerValue(18, 0), e.eval(op, state), op.toString());


        // UInteger(8, 0) + UInteger(8, 7) -> UInteger(16, 7) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(8, 0)),
                new ExpressionWithValue(new UIntegerValue(8, 7))
        };
        op = ExpStdOp.create("+", args);
        assertEquals(new UIntegerValue(16, 7), e.eval(op, state), op.toString());


        // UInteger(6, 5) + UInteger(6, 0) -> UInteger(12, 5) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(6, 5)),
                new ExpressionWithValue(new UIntegerValue(6, 0))
        };
        op = ExpStdOp.create("+", args);
        assertEquals(new UIntegerValue(12, 5), e.eval(op, state), op.toString());


        // UInteger(9, 3) + UInteger(9, 4) -> UInteger(18, 5) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(9, 3)),
                new ExpressionWithValue(new UIntegerValue(9, 4))
        };
        op = ExpStdOp.create("+", args);
        assertEquals(new UIntegerValue(18, 5), e.eval(op, state), op.toString());


        // UInteger(9, 1) + UInteger(8, 0) -> UInteger(17, 1) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(9, 1)),
                new ExpressionWithValue(new UIntegerValue(8, 0))
        };
        op = ExpStdOp.create("+", args);
        assertEquals(new UIntegerValue(17, 1), e.eval(op, state), op.toString());


        // UInteger(3, 3) + UInteger(4, 4) -> UInteger(7, 5) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(3, 3)),
                new ExpressionWithValue(new UIntegerValue(4, 4))
        };
        op = ExpStdOp.create("+", args);
        assertEquals(new UIntegerValue(7, 5), e.eval(op, state), op.toString());
    }

    @Test
    void testAddWithUReal() throws ExpInvalidException {
        Expression [] args;
        Expression op;

        // UInteger(-9, 0) + UReal(-9, 0) -> UInteger(-18, 0.0) : UReal
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(-9, 0)),
                new ExpressionWithValue(new URealValue(-9, 0))
        };
        op = ExpStdOp.create("+", args);
        assertEquals(new URealValue(-18, 0.0), e.eval(op, state), op.toString());

        // UInteger(-7, 0) + UReal(-7, 8) -> UInteger(-14, 8.0) : UReal
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(-7, 0)),
                new ExpressionWithValue(new URealValue(-7, 8))
        };
        op = ExpStdOp.create("+", args);
        assertEquals(new URealValue(-14, 8.0), e.eval(op, state), op.toString());

        // UInteger(-10, 0) + UReal(0, 0) -> UInteger(-10, 0.0) : UReal
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(-10, 0)),
                new ExpressionWithValue(new URealValue(0, 0))
        };
        op = ExpStdOp.create("+", args);
        assertEquals(new URealValue(-10, 0.0), e.eval(op, state), op.toString());

        // UInteger(-8, 0) + UReal(3, 5) -> UInteger(-5, 5.0) : UReal
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(-8, 0)),
                new ExpressionWithValue(new URealValue(3, 5))
        };
        op = ExpStdOp.create("+", args);
        assertEquals(new URealValue(-5, 5.0), e.eval(op, state), op.toString());

        // UInteger(-6, 8) + UReal(-6, 0) -> UInteger(-12, 8.0) : UReal
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(-6, 8)),
                new ExpressionWithValue(new URealValue(-6, 0))
        };
        op = ExpStdOp.create("+", args);
        assertEquals(new URealValue(-12, 8.0), e.eval(op, state), op.toString());

        // UInteger(-9, 3) + UReal(-9, 4) -> UInteger(-18, 5.0) : UReal
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(-9, 3)),
                new ExpressionWithValue(new URealValue(-9, 4))
        };
        op = ExpStdOp.create("+", args);
        assertEquals(new URealValue(-18, 5.0), e.eval(op, state), op.toString());

        // UInteger(-9, 8) + UReal(4, 0) -> UInteger(-5, 8.0) : UReal
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(-9, 8)),
                new ExpressionWithValue(new URealValue(4, 0))
        };
        op = ExpStdOp.create("+", args);
        assertEquals(new URealValue(-5, 8.0), e.eval(op, state), op.toString());

        // UInteger(-3, 3) + UReal(4, 4) -> UInteger(1, 5.0) : UReal
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(-3, 3)),
                new ExpressionWithValue(new URealValue(4, 4))
        };
        op = ExpStdOp.create("+", args);
        assertEquals(new URealValue(1, 5.0), e.eval(op, state), op.toString());

        // UInteger(0, 0) + UReal(0, 0) -> UInteger(0, 0.0) : UReal
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(0, 0)),
                new ExpressionWithValue(new URealValue(0, 0))
        };
        op = ExpStdOp.create("+", args);
        assertEquals(new URealValue(0, 0.0), e.eval(op, state), op.toString());

        // UInteger(0, 0) + UReal(0, 0) -> UInteger(0, 0.0) : UReal
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(0, 0)),
                new ExpressionWithValue(new URealValue(0, 0))
        };
        op = ExpStdOp.create("+", args);
        assertEquals(new URealValue(0, 0.0), e.eval(op, state), op.toString());

        // UInteger(0, 0) + UReal(9, 0) -> UInteger(9, 0.0) : UReal
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(0, 0)),
                new ExpressionWithValue(new URealValue(9, 0))
        };
        op = ExpStdOp.create("+", args);
        assertEquals(new URealValue(9, 0.0), e.eval(op, state), op.toString());

        // UInteger(0, 0) + UReal(8, 4) -> UInteger(8, 4.0) : UReal
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(0, 0)),
                new ExpressionWithValue(new URealValue(8, 4))
        };
        op = ExpStdOp.create("+", args);
        assertEquals(new URealValue(8, 4.0), e.eval(op, state), op.toString());

        // UInteger(0, 8) + UReal(0, 0) -> UInteger(0, 8.0) : UReal
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(0, 8)),
                new ExpressionWithValue(new URealValue(0, 0))
        };
        op = ExpStdOp.create("+", args);
        assertEquals(new URealValue(0, 8.0), e.eval(op, state), op.toString());

        // UInteger(0, 3) + UReal(0, 4) -> UInteger(0, 5.0) : UReal
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(0, 3)),
                new ExpressionWithValue(new URealValue(0, 4))
        };
        op = ExpStdOp.create("+", args);
        assertEquals(new URealValue(0, 5.0), e.eval(op, state), op.toString());

        // UInteger(0, 6) + UReal(8, 0) -> UInteger(8, 6.0) : UReal
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(0, 6)),
                new ExpressionWithValue(new URealValue(8, 0))
        };
        op = ExpStdOp.create("+", args);
        assertEquals(new URealValue(8, 6.0), e.eval(op, state), op.toString());

        // UInteger(0, 3) + UReal(5, 4) -> UInteger(5, 5.0) : UReal
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(0, 3)),
                new ExpressionWithValue(new URealValue(5, 4))
        };
        op = ExpStdOp.create("+", args);
        assertEquals(new URealValue(5, 5.0), e.eval(op, state), op.toString());

        // UInteger(9, 0) + UReal(9, 0) -> UInteger(18, 0.0) : UReal
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(9, 0)),
                new ExpressionWithValue(new URealValue(9, 0))
        };
        op = ExpStdOp.create("+", args);
        assertEquals(new URealValue(18, 0.0), e.eval(op, state), op.toString());

        // UInteger(7, 0) + UReal(7, 0) -> UInteger(14, 0.0) : UReal
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(7, 0)),
                new ExpressionWithValue(new URealValue(7, 0))
        };
        op = ExpStdOp.create("+", args);
        assertEquals(new URealValue(14, 0.0), e.eval(op, state), op.toString());

        // UInteger(10, 0) + UReal(8, 0) -> UInteger(18, 0.0) : UReal
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(10, 0)),
                new ExpressionWithValue(new URealValue(8, 0))
        };
        op = ExpStdOp.create("+", args);
        assertEquals(new URealValue(18, 0.0), e.eval(op, state), op.toString());

        // UInteger(8, 0) + UReal(8, 7) -> UInteger(16, 7.0) : UReal
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(8, 0)),
                new ExpressionWithValue(new URealValue(8, 7))
        };
        op = ExpStdOp.create("+", args);
        assertEquals(new URealValue(16, 7.0), e.eval(op, state), op.toString());

        // UInteger(6, 5) + UReal(6, 0) -> UInteger(12, 5.0) : UReal
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(6, 5)),
                new ExpressionWithValue(new URealValue(6, 0))
        };
        op = ExpStdOp.create("+", args);
        assertEquals(new URealValue(12, 5.0), e.eval(op, state), op.toString());

        // UInteger(9, 3) + UReal(9, 4) -> UInteger(18, 5.0) : UReal
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(9, 3)),
                new ExpressionWithValue(new URealValue(9, 4))
        };
        op = ExpStdOp.create("+", args);
        assertEquals(new URealValue(18, 5.0), e.eval(op, state), op.toString());

        // UInteger(9, 1) + UReal(8, 0) -> UInteger(17, 1.0) : UReal
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(9, 1)),
                new ExpressionWithValue(new URealValue(8, 0))
        };
        op = ExpStdOp.create("+", args);
        assertEquals(new URealValue(17, 1.0), e.eval(op, state), op.toString());

        // UInteger(3, 3) + UReal(4, 4) -> UInteger(7, 5.0) : UReal
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(3, 3)),
                new ExpressionWithValue(new URealValue(4, 4))
        };
        op = ExpStdOp.create("+", args);
        assertEquals(new URealValue(7, 5.0), e.eval(op, state), op.toString());

    }

    @Test
    void testAddWithReal() throws ExpInvalidException {
        Expression [] args;
        Expression op;

        // UInteger(-3, 0) + -3 -> UReal(-6, 0.0) : UReal
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(-3, 0)),
                new ExpressionWithValue(new RealValue(-3))
        };
        op = ExpStdOp.create("+", args);
        assertEquals(new URealValue(-6, 0.0), e.eval(op, state), op.toString());

        // UInteger(-6, 0) + -1.2 -> UReal(-7.2, 0.0) : UReal
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(-6, 0)),
                new ExpressionWithValue(new RealValue(-1.2))
        };
        op = ExpStdOp.create("+", args);
        assertEquals(new URealValue(-7.2, 0.0), e.eval(op, state), op.toString());

        // UInteger(-5, 3) + -5 -> UReal(-10, 3.0) : UReal
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(-5, 3)),
                new ExpressionWithValue(new RealValue(-5))
        };
        op = ExpStdOp.create("+", args);
        assertEquals(new URealValue(-10, 3.0), e.eval(op, state), op.toString());

        // UInteger(-8, 5) + -2 -> UReal(-10, 5.0) : UReal
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(-8, 5)),
                new ExpressionWithValue(new RealValue(-2))
        };
        op = ExpStdOp.create("+", args);
        assertEquals(new URealValue(-10, 5.0), e.eval(op, state), op.toString());

        // UInteger(0, 0) + 0 -> UReal(0, 0.0) : UReal
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(0, 0)),
                new ExpressionWithValue(new RealValue(0))
        };
        op = ExpStdOp.create("+", args);
        assertEquals(new URealValue(0, 0.0), e.eval(op, state), op.toString());

        // UInteger(0, 0) + 3 -> UReal(3, 0.0) : UReal
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(0, 0)),
                new ExpressionWithValue(new RealValue(3))
        };
        op = ExpStdOp.create("+", args);
        assertEquals(new URealValue(3, 0.0), e.eval(op, state), op.toString());

        // UInteger(0, 3) + 0 -> UReal(0, 3.0) : UReal
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(0, 3)),
                new ExpressionWithValue(new RealValue(0))
        };
        op = ExpStdOp.create("+", args);
        assertEquals(new URealValue(0, 3.0), e.eval(op, state), op.toString());

        // UInteger(0, 5) + -5 -> UReal(-5, 5.0) : UReal
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(0, 5)),
                new ExpressionWithValue(new RealValue(-5))
        };
        op = ExpStdOp.create("+", args);
        assertEquals(new URealValue(-5, 5.0), e.eval(op, state), op.toString());

        // UInteger(5, 0) + 5 -> UReal(10, 0.0) : UReal
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(5, 0)),
                new ExpressionWithValue(new RealValue(5))
        };
        op = ExpStdOp.create("+", args);
        assertEquals(new URealValue(10, 0.0), e.eval(op, state), op.toString());

        // UInteger(3, 0) + 0.6 -> UReal(3.6, 0.0) : UReal
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(3, 0)),
                new ExpressionWithValue(new RealValue(0.6))
        };
        op = ExpStdOp.create("+", args);
        assertEquals(new URealValue(3.6, 0.0), e.eval(op, state), op.toString());

        // UInteger(7, 3) + 7 -> UReal(14, 3.0) : UReal
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(7, 3)),
                new ExpressionWithValue(new RealValue(7))
        };
        op = ExpStdOp.create("+", args);
        assertEquals(new URealValue(14, 3.0), e.eval(op, state), op.toString());

        // UInteger(2, 5) + 0.5 -> UReal(2.5, 5.0) : UReal
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(2, 5)),
                new ExpressionWithValue(new RealValue(0.5))
        };
        op = ExpStdOp.create("+", args);
        assertEquals(new URealValue(2.5, 5.0), e.eval(op, state), op.toString());
    }

    @Test
    void testAddWithInteger() throws ExpInvalidException {
        Expression [] args;
        Expression op;

        // UInteger(-3, 0) + -3 -> UReal(-6, 0.0) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(-3, 0)),
                new ExpressionWithValue(IntegerValue.valueOf(-3))
        };
        op = ExpStdOp.create("+", args);
        assertEquals(new UIntegerValue(-6, 0.0), e.eval(op, state), op.toString());

        // UInteger(-6, 0) + -12 -> UReal(-18, 0.0) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(-6, 0)),
                new ExpressionWithValue(IntegerValue.valueOf(-12))
        };
        op = ExpStdOp.create("+", args);
        assertEquals(new UIntegerValue(-18, 0.0), e.eval(op, state), op.toString());

        // UInteger(-5, 3) + -5 -> UReal(-10, 3.0) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(-5, 3)),
                new ExpressionWithValue(IntegerValue.valueOf(-5))
        };
        op = ExpStdOp.create("+", args);
        assertEquals(new UIntegerValue(-10, 3.0), e.eval(op, state), op.toString());

        // UInteger(-8, 5) + -2 -> UReal(-10, 5.0) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(-8, 5)),
                new ExpressionWithValue(IntegerValue.valueOf(-2))
        };
        op = ExpStdOp.create("+", args);
        assertEquals(new UIntegerValue(-10, 5.0), e.eval(op, state), op.toString());

        // UInteger(0, 0) + 0 -> UReal(0, 0.0) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(0, 0)),
                new ExpressionWithValue(IntegerValue.valueOf(0))
        };
        op = ExpStdOp.create("+", args);
        assertEquals(new UIntegerValue(0, 0.0), e.eval(op, state), op.toString());

        // UInteger(0, 0) + 3 -> UReal(3, 0.0) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(0, 0)),
                new ExpressionWithValue(IntegerValue.valueOf(3))
        };
        op = ExpStdOp.create("+", args);
        assertEquals(new UIntegerValue(3, 0.0), e.eval(op, state), op.toString());

        // UInteger(0, 3) + 0 -> UReal(0, 3.0) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(0, 3)),
                new ExpressionWithValue(IntegerValue.valueOf(0))
        };
        op = ExpStdOp.create("+", args);
        assertEquals(new UIntegerValue(0, 3.0), e.eval(op, state), op.toString());

        // UInteger(0, 5) + -5 -> UReal(-5, 5.0) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(0, 5)),
                new ExpressionWithValue(IntegerValue.valueOf(-5))
        };
        op = ExpStdOp.create("+", args);
        assertEquals(new UIntegerValue(-5, 5.0), e.eval(op, state), op.toString());

        // UInteger(5, 0) + 5 -> UReal(10, 0.0) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(5, 0)),
                new ExpressionWithValue(IntegerValue.valueOf(5))
        };
        op = ExpStdOp.create("+", args);
        assertEquals(new UIntegerValue(10, 0.0), e.eval(op, state), op.toString());

        // UInteger(3, 0) + 56 -> UReal(59, 0.0) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(3, 0)),
                new ExpressionWithValue(IntegerValue.valueOf(56))
        };
        op = ExpStdOp.create("+", args);
        assertEquals(new UIntegerValue(59, 0.0), e.eval(op, state), op.toString());

        // UInteger(7, 3) + 7 -> UReal(14, 3.0) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(7, 3)),
                new ExpressionWithValue(IntegerValue.valueOf(7))
        };
        op = ExpStdOp.create("+", args);
        assertEquals(new UIntegerValue(14, 3.0), e.eval(op, state), op.toString());

        // UInteger(2, 5) + 65 -> UReal(67, 5.0) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(2, 5)),
                new ExpressionWithValue(IntegerValue.valueOf(65))
        };
        op = ExpStdOp.create("+", args);
        assertEquals(new UIntegerValue(67, 5.0), e.eval(op, state), op.toString());
    }

    @Test
    void testNeg() throws ExpInvalidException {
        Expression [] args;
        Expression op;

        // UInteger(3, 2.3).neg() -> UInteger(-3, 2.3) : UReal
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(3, 2.3))
        };
        op = ExpStdOp.create("neg", args);
        assertEquals(new UIntegerValue(-3, 2.3), e.eval(op, state), op.toString());

        // UInteger(0, 2.3).neg() -> UInteger(0, 2.3) : UReal
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(0, 2.3))
        };
        op = ExpStdOp.create("neg", args);
        assertEquals(new UIntegerValue(0, 2.3), e.eval(op, state), op.toString());

        // UInteger(-3, 2.3).neg() -> UInteger(3, 2.3) : UReal
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(-3, 2.3))
        };
        op = ExpStdOp.create("neg", args);
        assertEquals(new UIntegerValue(3, 2.3), e.eval(op, state), op.toString());
    }

    @Test
    void testSQRT() throws ExpInvalidException {
        Expression [] args;
        Expression op;

        // UInteger(-3, 2.3).sqrt() -> Undefined : OclVoid
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(-3, 2.3))
        };
        op = ExpStdOp.create("sqrt", args);
        assertEquals(UndefinedValue.instance, e.eval(op, state), op.toString());

        // UInteger(0, 0.0).sqrt() -> UInteger(0, 0) : OclVoid
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(0, 0))
        };
        op = ExpStdOp.create("sqrt", args);
        assertEquals(new UIntegerValue(0, 0), e.eval(op, state), op.toString());

        // UInteger(4, 0.0).sqrt() -> UInteger(2, 0.0) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(4, 0))
        };
        op = ExpStdOp.create("sqrt", args);
        assertEquals(new UIntegerValue(2, 0), e.eval(op, state), op.toString());

        // UInteger(4, 2).sqrt() -> UInteger(2, 0.5) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(4, 2))
        };
        op = ExpStdOp.create("sqrt", args);
        assertEquals(new UIntegerValue(2, 0.5), e.eval(op, state), op.toString());

    }

    @Test
    void testABS() throws ExpInvalidException {
        Expression [] args;
        Expression op;

        // UInteger(2, 3).abs() -> UInteger(2.0, 3.0) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(2, 3))
        };
        op = ExpStdOp.create("abs", args);
        assertEquals(new UIntegerValue(2, 3), e.eval(op, state), op.toString());

        // UInteger(0, 3).abs() -> UInteger(0, 3.0) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(0, 3))
        };
        op = ExpStdOp.create("abs", args);
        assertEquals(new UIntegerValue(0, 3), e.eval(op, state), op.toString());

        // UInteger(-2, 3).abs() -> UInteger(2, 3.0) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(-2, 3))
        };
        op = ExpStdOp.create("abs", args);
        assertEquals(new UIntegerValue(2, 3), e.eval(op, state), op.toString());
    }



    // Minus operation.

    @Test
    void testMinusBetweenUInteger() throws ExpInvalidException {
        Expression [] args;
        Expression op;

        // UInteger(-9, 0) + UInteger(-9, 0) -> UInteger(0, 0) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(-9, 0)),
                new ExpressionWithValue(new UIntegerValue(-9, 0))
        };
        op = ExpStdOp.create("-", args);
        assertEquals(new UIntegerValue(0, 0), e.eval(op, state), op.toString());

        // UInteger(-5, 0) + UInteger(-5, 3) -> UInteger(0, 3) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(-5, 0)),
                new ExpressionWithValue(new UIntegerValue(-5, 3))
        };
        op = ExpStdOp.create("-", args);
        assertEquals(new UIntegerValue(0, 3), e.eval(op, state), op.toString());

        // UInteger(-4, 0) + UInteger(2, 0) -> UInteger(-6, 0) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(-4, 0)),
                new ExpressionWithValue(new UIntegerValue(2, 0))
        };
        op = ExpStdOp.create("-", args);
        assertEquals(new UIntegerValue(-6, 0), e.eval(op, state), op.toString());

        // UInteger(-10, 0) + UInteger(4, 1) -> UInteger(-14, 1) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(-10, 0)),
                new ExpressionWithValue(new UIntegerValue(4, 1))
        };
        op = ExpStdOp.create("-", args);
        assertEquals(new UIntegerValue(-14, 1), e.eval(op, state), op.toString());

        // UInteger(-9, 9) + UInteger(-9, 0) -> UInteger(0, 9) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(-9, 9)),
                new ExpressionWithValue(new UIntegerValue(-9, 0))
        };
        op = ExpStdOp.create("-", args);
        assertEquals(new UIntegerValue(0, 9), e.eval(op, state), op.toString());

        // UInteger(-2, 3) + UInteger(-2, 4) -> UInteger(0, 5) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(-2, 3)),
                new ExpressionWithValue(new UIntegerValue(-2, 4))
        };
        op = ExpStdOp.create("-", args);
        assertEquals(new UIntegerValue(0, 5), e.eval(op, state), op.toString());

        // UInteger(-6, 2) + UInteger(5, 0) -> UInteger(-11, 2) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(-6, 2)),
                new ExpressionWithValue(new UIntegerValue(5, 0))
        };
        op = ExpStdOp.create("-", args);
        assertEquals(new UIntegerValue(-11, 2), e.eval(op, state), op.toString());

        // UInteger(-2, 3) + UInteger(4, 4) -> UInteger(-6, 5) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(-2, 3)),
                new ExpressionWithValue(new UIntegerValue(4, 4))
        };
        op = ExpStdOp.create("-", args);
        assertEquals(new UIntegerValue(-6, 5), e.eval(op, state), op.toString());

        // UInteger(0, 0) + UInteger(0, 0) -> UInteger(0, 0) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(0, 0)),
                new ExpressionWithValue(new UIntegerValue(0, 0))
        };
        op = ExpStdOp.create("-", args);
        assertEquals(new UIntegerValue(0, 0), e.eval(op, state), op.toString());

        // UInteger(0, 0) + UInteger(0, 4) -> UInteger(0, 4) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(0, 0)),
                new ExpressionWithValue(new UIntegerValue(0, 4))
        };
        op = ExpStdOp.create("-", args);
        assertEquals(new UIntegerValue(0, 4), e.eval(op, state), op.toString());

        // UInteger(0, 0) + UInteger(6, 0) -> UInteger(-6, 0) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(0, 0)),
                new ExpressionWithValue(new UIntegerValue(6, 0))
        };
        op = ExpStdOp.create("-", args);
        assertEquals(new UIntegerValue(-6, 0), e.eval(op, state), op.toString());

        // UInteger(0, 0) + UInteger(7, 3) -> UInteger(-7, 3) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(0, 0)),
                new ExpressionWithValue(new UIntegerValue(7, 3))
        };
        op = ExpStdOp.create("-", args);
        assertEquals(new UIntegerValue(-7, 3), e.eval(op, state), op.toString());

        // UInteger(0, 4) + UInteger(0, 0) -> UInteger(0, 4) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(0, 4)),
                new ExpressionWithValue(new UIntegerValue(0, 0))
        };
        op = ExpStdOp.create("-", args);
        assertEquals(new UIntegerValue(0, 4), e.eval(op, state), op.toString());

        // UInteger(0, 4) + UInteger(0, 3) -> UInteger(0, 5) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(0, 4)),
                new ExpressionWithValue(new UIntegerValue(0, 3))
        };
        op = ExpStdOp.create("-", args);
        assertEquals(new UIntegerValue(0, 5), e.eval(op, state), op.toString());

        // UInteger(0, 4) + UInteger(1, 0) -> UInteger(-1, 4) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(0, 4)),
                new ExpressionWithValue(new UIntegerValue(1, 0))
        };
        op = ExpStdOp.create("-", args);
        assertEquals(new UIntegerValue(-1, 4), e.eval(op, state), op.toString());

        // UInteger(0, 4) + UInteger(2, 3) -> UInteger(-2, 5) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(0, 4)),
                new ExpressionWithValue(new UIntegerValue(2, 3))
        };
        op = ExpStdOp.create("-", args);
        assertEquals(new UIntegerValue(-2, 5), e.eval(op, state), op.toString());

        // UInteger(9, 0) + UInteger(9, 0) -> UInteger(0, 0) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(9, 0)),
                new ExpressionWithValue(new UIntegerValue(9, 0))
        };
        op = ExpStdOp.create("-", args);
        assertEquals(new UIntegerValue(0, 0), e.eval(op, state), op.toString());

        // UInteger(5, 0) + UInteger(5, 3) -> UInteger(0, 3) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(5, 0)),
                new ExpressionWithValue(new UIntegerValue(5, 3))
        };
        op = ExpStdOp.create("-", args);
        assertEquals(new UIntegerValue(0, 3), e.eval(op, state), op.toString());

        // UInteger(4, 0) + UInteger(8, 0) -> UInteger(-4, 0) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(4, 0)),
                new ExpressionWithValue(new UIntegerValue(8, 0))
        };
        op = ExpStdOp.create("-", args);
        assertEquals(new UIntegerValue(-4, 0), e.eval(op, state), op.toString());

        // UInteger(10, 0) + UInteger(10, 12) -> UInteger(0, 12) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(10, 0)),
                new ExpressionWithValue(new UIntegerValue(10, 12))
        };
        op = ExpStdOp.create("-", args);
        assertEquals(new UIntegerValue(0, 12), e.eval(op, state), op.toString());

        // UInteger(9, 5) + UInteger(9, 0) -> UInteger(0, 5) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(9, 5)),
                new ExpressionWithValue(new UIntegerValue(9, 0))
        };
        op = ExpStdOp.create("-", args);
        assertEquals(new UIntegerValue(0, 5), e.eval(op, state), op.toString());

        // UInteger(2, 3) + UInteger(2, 4) -> UInteger(0, 5) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(2, 3)),
                new ExpressionWithValue(new UIntegerValue(2, 4))
        };
        op = ExpStdOp.create("-", args);
        assertEquals(new UIntegerValue(0, 5), e.eval(op, state), op.toString());

        // UInteger(6, 1) + UInteger(4, 0) -> UInteger(2, 1) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(6, 1)),
                new ExpressionWithValue(new UIntegerValue(4, 0))
        };
        op = ExpStdOp.create("-", args);
        assertEquals(new UIntegerValue(2, 1), e.eval(op, state), op.toString());

        // UInteger(2, 3) + UInteger(5, 4) -> UInteger(-3, 5) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(2, 3)),
                new ExpressionWithValue(new UIntegerValue(5, 4))
        };
        op = ExpStdOp.create("-", args);
        assertEquals(new UIntegerValue(-3, 5), e.eval(op, state), op.toString());
    }

    @Test
    void testMinusUReal() throws ExpInvalidException {
        Expression [] args;
        Expression op;

        // UInteger(-9, 0) + UReal(-9, 0) -> UReal(0.0, 0.0) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(-9, 0)),
                new ExpressionWithValue(new URealValue(-9, 0))
        };
        op = ExpStdOp.create("-", args);
        assertEquals(new URealValue(0.0, 0.0), e.eval(op, state), op.toString());

        // UInteger(-5, 0) + UReal(-5, 3) -> UReal(0.0, 3.0) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(-5, 0)),
                new ExpressionWithValue(new URealValue(-5, 3))
        };
        op = ExpStdOp.create("-", args);
        assertEquals(new URealValue(0.0, 3.0), e.eval(op, state), op.toString());

        // UInteger(-4, 0) + UReal(2, 0) -> UReal(-6.0, 0.0) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(-4, 0)),
                new ExpressionWithValue(new URealValue(2, 0))
        };
        op = ExpStdOp.create("-", args);
        assertEquals(new URealValue(-6.0, 0.0), e.eval(op, state), op.toString());

        // UInteger(-10, 0) + UReal(4, 1) -> UReal(-14.0, 1.0) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(-10, 0)),
                new ExpressionWithValue(new URealValue(4, 1))
        };
        op = ExpStdOp.create("-", args);
        assertEquals(new URealValue(-14.0, 1.0), e.eval(op, state), op.toString());

        // UInteger(-9, 9) + UReal(-9, 0) -> UReal(0.0, 9.0) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(-9, 9)),
                new ExpressionWithValue(new URealValue(-9, 0))
        };
        op = ExpStdOp.create("-", args);
        assertEquals(new URealValue(0.0, 9.0), e.eval(op, state), op.toString());

        // UInteger(-2, 3) + UReal(-2, 4) -> UReal(0.0, 5.0) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(-2, 3)),
                new ExpressionWithValue(new URealValue(-2, 4))
        };
        op = ExpStdOp.create("-", args);
        assertEquals(new URealValue(0.0, 5.0), e.eval(op, state), op.toString());

        // UInteger(-6, 2) + UReal(5, 0) -> UReal(-11.0, 2.0) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(-6, 2)),
                new ExpressionWithValue(new URealValue(5, 0))
        };
        op = ExpStdOp.create("-", args);
        assertEquals(new URealValue(-11.0, 2.0), e.eval(op, state), op.toString());

        // UInteger(-2, 3) + UReal(4, 4) -> UReal(-6.0, 5.0) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(-2, 3)),
                new ExpressionWithValue(new URealValue(4, 4))
        };
        op = ExpStdOp.create("-", args);
        assertEquals(new URealValue(-6.0, 5.0), e.eval(op, state), op.toString());

        // UInteger(0, 0) + UReal(0, 0) -> UReal(0.0, 0.0) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(0, 0)),
                new ExpressionWithValue(new URealValue(0, 0))
        };
        op = ExpStdOp.create("-", args);
        assertEquals(new URealValue(0.0, 0.0), e.eval(op, state), op.toString());

        // UInteger(0, 0) + UReal(0, 4) -> UReal(0.0, 4.0) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(0, 0)),
                new ExpressionWithValue(new URealValue(0, 4))
        };
        op = ExpStdOp.create("-", args);
        assertEquals(new URealValue(0.0, 4.0), e.eval(op, state), op.toString());

        // UInteger(0, 0) + UReal(6, 0) -> UReal(-6.0, 0.0) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(0, 0)),
                new ExpressionWithValue(new URealValue(6, 0))
        };
        op = ExpStdOp.create("-", args);
        assertEquals(new URealValue(-6.0, 0.0), e.eval(op, state), op.toString());

        // UInteger(0, 0) + UReal(7, 3) -> UReal(-7.0, 3.0) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(0, 0)),
                new ExpressionWithValue(new URealValue(7, 3))
        };
        op = ExpStdOp.create("-", args);
        assertEquals(new URealValue(-7.0, 3.0), e.eval(op, state), op.toString());

        // UInteger(0, 4) + UReal(0, 0) -> UReal(0.0, 4.0) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(0, 4)),
                new ExpressionWithValue(new URealValue(0, 0))
        };
        op = ExpStdOp.create("-", args);
        assertEquals(new URealValue(0.0, 4.0), e.eval(op, state), op.toString());

        // UInteger(0, 4) + UReal(0, 3) -> UReal(0.0, 5.0) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(0, 4)),
                new ExpressionWithValue(new URealValue(0, 3))
        };
        op = ExpStdOp.create("-", args);
        assertEquals(new URealValue(0.0, 5.0), e.eval(op, state), op.toString());

        // UInteger(0, 4) + UReal(1, 0) -> UReal(-1.0, 4.0) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(0, 4)),
                new ExpressionWithValue(new URealValue(1, 0))
        };
        op = ExpStdOp.create("-", args);
        assertEquals(new URealValue(-1.0, 4.0), e.eval(op, state), op.toString());

        // UInteger(0, 4) + UReal(2, 3) -> UReal(-2.0, 5.0) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(0, 4)),
                new ExpressionWithValue(new URealValue(2, 3))
        };
        op = ExpStdOp.create("-", args);
        assertEquals(new URealValue(-2.0, 5.0), e.eval(op, state), op.toString());

        // UInteger(9, 0) + UReal(9, 0) -> UReal(0.0, 0.0) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(9, 0)),
                new ExpressionWithValue(new URealValue(9, 0))
        };
        op = ExpStdOp.create("-", args);
        assertEquals(new URealValue(0.0, 0.0), e.eval(op, state), op.toString());

        // UInteger(5, 0) + UReal(5, 3) -> UReal(0.0, 3.0) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(5, 0)),
                new ExpressionWithValue(new URealValue(5, 3))
        };
        op = ExpStdOp.create("-", args);
        assertEquals(new URealValue(0.0, 3.0), e.eval(op, state), op.toString());

        // UInteger(4, 0) + UReal(8, 0) -> UReal(-4.0, 0.0) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(4, 0)),
                new ExpressionWithValue(new URealValue(8, 0))
        };
        op = ExpStdOp.create("-", args);
        assertEquals(new URealValue(-4.0, 0.0), e.eval(op, state), op.toString());

        // UInteger(10, 0) + UReal(10, 12) -> UReal(0.0, 12.0) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(10, 0)),
                new ExpressionWithValue(new URealValue(10, 12))
        };
        op = ExpStdOp.create("-", args);
        assertEquals(new URealValue(0.0, 12.0), e.eval(op, state), op.toString());

        // UInteger(9, 5) + UReal(9, 0) -> UReal(0.0, 5.0) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(9, 5)),
                new ExpressionWithValue(new URealValue(9, 0))
        };
        op = ExpStdOp.create("-", args);
        assertEquals(new URealValue(0.0, 5.0), e.eval(op, state), op.toString());

        // UInteger(2, 3) + UReal(2, 4) -> UReal(0.0, 5.0) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(2, 3)),
                new ExpressionWithValue(new URealValue(2, 4))
        };
        op = ExpStdOp.create("-", args);
        assertEquals(new URealValue(0.0, 5.0), e.eval(op, state), op.toString());

        // UInteger(6, 1) + UReal(4, 0) -> UReal(2.0, 1.0) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(6, 1)),
                new ExpressionWithValue(new URealValue(4, 0))
        };
        op = ExpStdOp.create("-", args);
        assertEquals(new URealValue(2.0, 1.0), e.eval(op, state), op.toString());

        // UInteger(2, 3) + UReal(5, 4) -> UReal(-3.0, 5.0) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(2, 3)),
                new ExpressionWithValue(new URealValue(5, 4))
        };
        op = ExpStdOp.create("-", args);
        assertEquals(new URealValue(-3.0, 5.0), e.eval(op, state), op.toString());
    }

    @Test
    void testMinusWithReal() throws ExpInvalidException {
        Expression [] args;
        Expression op;

        // UInteger(-3, 0) +-3.0 -> UReal(0.0, 0.0) : UReal
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(-3, 0)),
                new ExpressionWithValue(new RealValue(-3.0))
        };
        op = ExpStdOp.create("-", args);
        assertEquals(new URealValue(0.0, 0.0), e.eval(op, state), op.toString());

        // UInteger(-6, 0) +-1.2 -> UReal(-4.8, 0.0) : UReal
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(-6, 0)),
                new ExpressionWithValue(new RealValue(-1.2))
        };
        op = ExpStdOp.create("-", args);
        assertEquals(new URealValue(-4.8, 0.0), e.eval(op, state), op.toString());

        // UInteger(-5, 3) +-5.0 -> UReal(0.0, 3.0) : UReal
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(-5, 3)),
                new ExpressionWithValue(new RealValue(-5.0))
        };
        op = ExpStdOp.create("-", args);
        assertEquals(new URealValue(0.0, 3.0), e.eval(op, state), op.toString());

        // UInteger(-8, 5) +-2.0 -> UReal(-6.0, 5.0) : UReal
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(-8, 5)),
                new ExpressionWithValue(new RealValue(-2.0))
        };
        op = ExpStdOp.create("-", args);
        assertEquals(new URealValue(-6.0, 5.0), e.eval(op, state), op.toString());

        // UInteger(0, 0) +0.0 -> UReal(0.0, 0.0) : UReal
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(0, 0)),
                new ExpressionWithValue(new RealValue(0.0))
        };
        op = ExpStdOp.create("-", args);
        assertEquals(new URealValue(0.0, 0.0), e.eval(op, state), op.toString());

        // UInteger(0, 0) +3.0 -> UReal(-3.0, 0.0) : UReal
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(0, 0)),
                new ExpressionWithValue(new RealValue(3.0))
        };
        op = ExpStdOp.create("-", args);
        assertEquals(new URealValue(-3.0, 0.0), e.eval(op, state), op.toString());

        // UInteger(0, 3) +0.0 -> UReal(0.0, 3.0) : UReal
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(0, 3)),
                new ExpressionWithValue(new RealValue(0.0))
        };
        op = ExpStdOp.create("-", args);
        assertEquals(new URealValue(0.0, 3.0), e.eval(op, state), op.toString());

        // UInteger(0, 5) +-5.0 -> UReal(5.0, 5.0) : UReal
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(0, 5)),
                new ExpressionWithValue(new RealValue(-5.0))
        };
        op = ExpStdOp.create("-", args);
        assertEquals(new URealValue(5.0, 5.0), e.eval(op, state), op.toString());

        // UInteger(5, 0) +5.0 -> UReal(0.0, 0.0) : UReal
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(5, 0)),
                new ExpressionWithValue(new RealValue(5.0))
        };
        op = ExpStdOp.create("-", args);
        assertEquals(new URealValue(0.0, 0.0), e.eval(op, state), op.toString());

        // UInteger(3, 0) +0.6 -> UReal(2.4, 0.0) : UReal
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(3, 0)),
                new ExpressionWithValue(new RealValue(0.6))
        };
        op = ExpStdOp.create("-", args);
        assertEquals(new URealValue(2.4, 0.0), e.eval(op, state), op.toString());

        // UInteger(7, 3) +7.0 -> UReal(0.0, 3.0) : UReal
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(7, 3)),
                new ExpressionWithValue(new RealValue(7.0))
        };
        op = ExpStdOp.create("-", args);
        assertEquals(new URealValue(0.0, 3.0), e.eval(op, state), op.toString());

        // UInteger(2, 5) +0.5 -> UReal(1.5, 5.0) : UReal
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(2, 5)),
                new ExpressionWithValue(new RealValue(0.5))
        };
        op = ExpStdOp.create("-", args);
        assertEquals(new URealValue(1.5, 5.0), e.eval(op, state), op.toString());
    }

    @Test
    void testMinusWithInteger() throws ExpInvalidException {
        Expression [] args;
        Expression op;

        // UInteger(-3, 0) +-3 -> UInteger(0, 0) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(-3, 0)),
                new ExpressionWithValue(IntegerValue.valueOf(-3))
        };
        op = ExpStdOp.create("-", args);
        assertEquals(new UIntegerValue(0, 0), e.eval(op, state), op.toString());

        // UInteger(-6, 0) +-12 -> UInteger(6, 0) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(-6, 0)),
                new ExpressionWithValue(IntegerValue.valueOf(-12))
        };
        op = ExpStdOp.create("-", args);
        assertEquals(new UIntegerValue(6, 0), e.eval(op, state), op.toString());

        // UInteger(-5, 3) +-5 -> UInteger(0, 3) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(-5, 3)),
                new ExpressionWithValue(IntegerValue.valueOf(-5))
        };
        op = ExpStdOp.create("-", args);
        assertEquals(new UIntegerValue(0, 3), e.eval(op, state), op.toString());

        // UInteger(-8, 5) +-2 -> UInteger(-6, 5) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(-8, 5)),
                new ExpressionWithValue(IntegerValue.valueOf(-2))
        };
        op = ExpStdOp.create("-", args);
        assertEquals(new UIntegerValue(-6, 5), e.eval(op, state), op.toString());

        // UInteger(0, 0) +0 -> UInteger(0, 0) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(0, 0)),
                new ExpressionWithValue(IntegerValue.valueOf(0))
        };
        op = ExpStdOp.create("-", args);
        assertEquals(new UIntegerValue(0, 0), e.eval(op, state), op.toString());

        // UInteger(0, 0) +3 -> UInteger(-3, 0) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(0, 0)),
                new ExpressionWithValue(IntegerValue.valueOf(3))
        };
        op = ExpStdOp.create("-", args);
        assertEquals(new UIntegerValue(-3, 0), e.eval(op, state), op.toString());

        // UInteger(0, 3) +0 -> UInteger(0, 3) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(0, 3)),
                new ExpressionWithValue(IntegerValue.valueOf(0))
        };
        op = ExpStdOp.create("-", args);
        assertEquals(new UIntegerValue(0, 3), e.eval(op, state), op.toString());

        // UInteger(0, 5) +-5 -> UInteger(5, 5) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(0, 5)),
                new ExpressionWithValue(IntegerValue.valueOf(-5))
        };
        op = ExpStdOp.create("-", args);
        assertEquals(new UIntegerValue(5, 5), e.eval(op, state), op.toString());

        // UInteger(5, 0) +5 -> UInteger(0, 0) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(5, 0)),
                new ExpressionWithValue(IntegerValue.valueOf(5))
        };
        op = ExpStdOp.create("-", args);
        assertEquals(new UIntegerValue(0, 0), e.eval(op, state), op.toString());

        // UInteger(3, 0) +56 -> UInteger(-53, 0) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(3, 0)),
                new ExpressionWithValue(IntegerValue.valueOf(56))
        };
        op = ExpStdOp.create("-", args);
        assertEquals(new UIntegerValue(-53, 0), e.eval(op, state), op.toString());

        // UInteger(7, 3) +7 -> UInteger(0, 3) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(7, 3)),
                new ExpressionWithValue(IntegerValue.valueOf(7))
        };
        op = ExpStdOp.create("-", args);
        assertEquals(new UIntegerValue(0, 3), e.eval(op, state), op.toString());

        // UInteger(2, 5) +65 -> UInteger(-63, 5) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(2, 5)),
                new ExpressionWithValue(IntegerValue.valueOf(65))
        };
        op = ExpStdOp.create("-", args);
        assertEquals(new UIntegerValue(-63, 5), e.eval(op, state), op.toString());
    }

    @Test
    void testMultBetweenUInteger() throws ExpInvalidException {
        Expression [] args;
        Expression op;



        // UInteger(-9, 0) * UInteger(-9, 0) -> UInteger(81, 0.0) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(-9, 0)),
                new ExpressionWithValue(new UIntegerValue(-9, 0))
        };
        op = ExpStdOp.create("*", args);
        assertEquals(new UIntegerValue(81, 0.0), e.eval(op, state), op.toString());

        // UInteger(-5, 0) * UInteger(-5, 3) -> UInteger(25, 15.0) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(-5, 0)),
                new ExpressionWithValue(new UIntegerValue(-5, 3))
        };
        op = ExpStdOp.create("*", args);
        assertEquals(new UIntegerValue(25, 15.0), e.eval(op, state), op.toString());

        // UInteger(-4, 0) * UInteger(2, 0) -> UInteger(-8, 0.0) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(-4, 0)),
                new ExpressionWithValue(new UIntegerValue(2, 0))
        };
        op = ExpStdOp.create("*", args);
        assertEquals(new UIntegerValue(-8, 0.0), e.eval(op, state), op.toString());

        // UInteger(-10, 0) * UInteger(4, 1) -> UInteger(-40, 10.0) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(-10, 0)),
                new ExpressionWithValue(new UIntegerValue(4, 1))
        };
        op = ExpStdOp.create("*", args);
        assertEquals(new UIntegerValue(-40, 10.0), e.eval(op, state), op.toString());

        // UInteger(-9, 9) * UInteger(-9, 0) -> UInteger(81, 81.0) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(-9, 9)),
                new ExpressionWithValue(new UIntegerValue(-9, 0))
        };
        op = ExpStdOp.create("*", args);
        assertEquals(new UIntegerValue(81, 81.0), e.eval(op, state), op.toString());

        // UInteger(-2, 3) * UInteger(-2, 4) -> UInteger(4, 10.0) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(-2, 3)),
                new ExpressionWithValue(new UIntegerValue(-2, 4))
        };
        op = ExpStdOp.create("*", args);
        assertEquals(new UIntegerValue(4, 10.0), e.eval(op, state), op.toString());

        // UInteger(-6, 2) * UInteger(5, 0) -> UInteger(-30, 10.0) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(-6, 2)),
                new ExpressionWithValue(new UIntegerValue(5, 0))
        };
        op = ExpStdOp.create("*", args);
        assertEquals(new UIntegerValue(-30, 10.0), e.eval(op, state), op.toString());

        // UInteger(-2, 3) * UInteger(2, 4) -> UInteger(-4, 10.0) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(-2, 3)),
                new ExpressionWithValue(new UIntegerValue(2, 4))
        };
        op = ExpStdOp.create("*", args);
        assertEquals(new UIntegerValue(-4, 10.0), e.eval(op, state), op.toString());

        // UInteger(0, 0) * UInteger(0, 0) -> UInteger(0, 0.0) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(0, 0)),
                new ExpressionWithValue(new UIntegerValue(0, 0))
        };
        op = ExpStdOp.create("*", args);
        assertEquals(new UIntegerValue(0, 0.0), e.eval(op, state), op.toString());

        // UInteger(0, 0) * UInteger(0, 4) -> UInteger(0, 0.0) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(0, 0)),
                new ExpressionWithValue(new UIntegerValue(0, 4))
        };
        op = ExpStdOp.create("*", args);
        assertEquals(new UIntegerValue(0, 0.0), e.eval(op, state), op.toString());

        // UInteger(0, 0) * UInteger(6, 0) -> UInteger(0, 0.0) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(0, 0)),
                new ExpressionWithValue(new UIntegerValue(6, 0))
        };
        op = ExpStdOp.create("*", args);
        assertEquals(new UIntegerValue(0, 0.0), e.eval(op, state), op.toString());

        // UInteger(0, 0) * UInteger(7, 3) -> UInteger(0, 0.0) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(0, 0)),
                new ExpressionWithValue(new UIntegerValue(7, 3))
        };
        op = ExpStdOp.create("*", args);
        assertEquals(new UIntegerValue(0, 0.0), e.eval(op, state), op.toString());

        // UInteger(0, 4) * UInteger(0, 0) -> UInteger(0, 0.0) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(0, 4)),
                new ExpressionWithValue(new UIntegerValue(0, 0))
        };
        op = ExpStdOp.create("*", args);
        assertEquals(new UIntegerValue(0, 0.0), e.eval(op, state), op.toString());

        // UInteger(0, 4) * UInteger(0, 3) -> UInteger(0, 0.0) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(0, 4)),
                new ExpressionWithValue(new UIntegerValue(0, 3))
        };
        op = ExpStdOp.create("*", args);
        assertEquals(new UIntegerValue(0, 0.0), e.eval(op, state), op.toString());

        // UInteger(0, 4) * UInteger(1, 0) -> UInteger(0, 4.0) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(0, 4)),
                new ExpressionWithValue(new UIntegerValue(1, 0))
        };
        op = ExpStdOp.create("*", args);
        assertEquals(new UIntegerValue(0, 4.0), e.eval(op, state), op.toString());

        // UInteger(0, 4) * UInteger(2, 3) -> UInteger(0, 8.0) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(0, 4)),
                new ExpressionWithValue(new UIntegerValue(2, 3))
        };
        op = ExpStdOp.create("*", args);
        assertEquals(new UIntegerValue(0, 8.0), e.eval(op, state), op.toString());

        // UInteger(9, 0) * UInteger(9, 0) -> UInteger(81, 0.0) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(9, 0)),
                new ExpressionWithValue(new UIntegerValue(9, 0))
        };
        op = ExpStdOp.create("*", args);
        assertEquals(new UIntegerValue(81, 0.0), e.eval(op, state), op.toString());

        // UInteger(5, 0) * UInteger(5, 3) -> UInteger(25, 15.0) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(5, 0)),
                new ExpressionWithValue(new UIntegerValue(5, 3))
        };
        op = ExpStdOp.create("*", args);
        assertEquals(new UIntegerValue(25, 15.0), e.eval(op, state), op.toString());

        // UInteger(4, 0) * UInteger(8, 0) -> UInteger(32, 0.0) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(4, 0)),
                new ExpressionWithValue(new UIntegerValue(8, 0))
        };
        op = ExpStdOp.create("*", args);
        assertEquals(new UIntegerValue(32, 0.0), e.eval(op, state), op.toString());

        // UInteger(10, 0) * UInteger(10, 12) -> UInteger(100, 120.0) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(10, 0)),
                new ExpressionWithValue(new UIntegerValue(10, 12))
        };
        op = ExpStdOp.create("*", args);
        assertEquals(new UIntegerValue(100, 120.0), e.eval(op, state), op.toString());

        // UInteger(9, 5) * UInteger(9, 0) -> UInteger(81, 45.0) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(9, 5)),
                new ExpressionWithValue(new UIntegerValue(9, 0))
        };
        op = ExpStdOp.create("*", args);
        assertEquals(new UIntegerValue(81, 45.0), e.eval(op, state), op.toString());

        // UInteger(2, 3) * UInteger(2, 4) -> UInteger(4, 10.0) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(2, 3)),
                new ExpressionWithValue(new UIntegerValue(2, 4))
        };
        op = ExpStdOp.create("*", args);
        assertEquals(new UIntegerValue(4, 10.0), e.eval(op, state), op.toString());

        // UInteger(6, 1) * UInteger(4, 0) -> UInteger(24, 4.0) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(6, 1)),
                new ExpressionWithValue(new UIntegerValue(4, 0))
        };
        op = ExpStdOp.create("*", args);
        assertEquals(new UIntegerValue(24, 4.0), e.eval(op, state), op.toString());

        // UInteger(2, 3) * UInteger(5, 4) -> UInteger(10, 17.0) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(2, 3)),
                new ExpressionWithValue(new UIntegerValue(5, 4))
        };
        op = ExpStdOp.create("*", args);
        assertEquals(new UIntegerValue(10, 17.0), e.eval(op, state), op.toString());
    }

    @Test
    void testMultWithUReal() throws ExpInvalidException {
        Expression [] args;
        Expression op;

        // UInteger(-9, 0) * UReal(-9, 0) -> UReal(81.0, 0.0) : UReal
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(-9, 0)),
                new ExpressionWithValue(new URealValue(-9, 0))
        };
        op = ExpStdOp.create("*", args);
        assertEquals(new URealValue(81.0, 0.0), e.eval(op, state), op.toString());

        // UInteger(-5, 0) * UReal(-5, 3) -> UReal(25.0, 15.0) : UReal
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(-5, 0)),
                new ExpressionWithValue(new URealValue(-5, 3))
        };
        op = ExpStdOp.create("*", args);
        assertEquals(new URealValue(25.0, 15.0), e.eval(op, state), op.toString());

        // UInteger(-4, 0) * UReal(2, 0) -> UReal(-8.0, 0.0) : UReal
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(-4, 0)),
                new ExpressionWithValue(new URealValue(2, 0))
        };
        op = ExpStdOp.create("*", args);
        assertEquals(new URealValue(-8.0, 0.0), e.eval(op, state), op.toString());

        // UInteger(-10, 0) * UReal(4, 1) -> UReal(-40.0, 10.0) : UReal
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(-10, 0)),
                new ExpressionWithValue(new URealValue(4, 1))
        };
        op = ExpStdOp.create("*", args);
        assertEquals(new URealValue(-40.0, 10.0), e.eval(op, state), op.toString());

        // UInteger(-9, 9) * UReal(-9, 0) -> UReal(81.0, 81.0) : UReal
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(-9, 9)),
                new ExpressionWithValue(new URealValue(-9, 0))
        };
        op = ExpStdOp.create("*", args);
        assertEquals(new URealValue(81.0, 81.0), e.eval(op, state), op.toString());

        // UInteger(-2, 3) * UReal(-2, 4) -> UReal(4.0, 10.0) : UReal
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(-2, 3)),
                new ExpressionWithValue(new URealValue(-2, 4))
        };
        op = ExpStdOp.create("*", args);
        assertEquals(new URealValue(4.0, 10.0), e.eval(op, state), op.toString());

        // UInteger(-6, 2) * UReal(5, 0) -> UReal(-30.0, 10.0) : UReal
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(-6, 2)),
                new ExpressionWithValue(new URealValue(5, 0))
        };
        op = ExpStdOp.create("*", args);
        assertEquals(new URealValue(-30.0, 10.0), e.eval(op, state), op.toString());

        // UInteger(-2, 3) * UReal(2, 4) -> UReal(-4.0, 10.0) : UReal
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(-2, 3)),
                new ExpressionWithValue(new URealValue(2, 4))
        };
        op = ExpStdOp.create("*", args);
        assertEquals(new URealValue(-4.0, 10.0), e.eval(op, state), op.toString());

        // UInteger(0, 0) * UReal(0, 0) -> UReal(0.0, 0.0) : UReal
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(0, 0)),
                new ExpressionWithValue(new URealValue(0, 0))
        };
        op = ExpStdOp.create("*", args);
        assertEquals(new URealValue(0.0, 0.0), e.eval(op, state), op.toString());

        // UInteger(0, 0) * UReal(0, 4) -> UReal(0.0, 0.0) : UReal
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(0, 0)),
                new ExpressionWithValue(new URealValue(0, 4))
        };
        op = ExpStdOp.create("*", args);
        assertEquals(new URealValue(0.0, 0.0), e.eval(op, state), op.toString());

        // UInteger(0, 0) * UReal(6, 0) -> UReal(0.0, 0.0) : UReal
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(0, 0)),
                new ExpressionWithValue(new URealValue(6, 0))
        };
        op = ExpStdOp.create("*", args);
        assertEquals(new URealValue(0.0, 0.0), e.eval(op, state), op.toString());

        // UInteger(0, 0) * UReal(7, 3) -> UReal(0.0, 0.0) : UReal
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(0, 0)),
                new ExpressionWithValue(new URealValue(7, 3))
        };
        op = ExpStdOp.create("*", args);
        assertEquals(new URealValue(0.0, 0.0), e.eval(op, state), op.toString());

        // UInteger(0, 4) * UReal(0, 0) -> UReal(0.0, 0.0) : UReal
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(0, 4)),
                new ExpressionWithValue(new URealValue(0, 0))
        };
        op = ExpStdOp.create("*", args);
        assertEquals(new URealValue(0.0, 0.0), e.eval(op, state), op.toString());

        // UInteger(0, 4) * UReal(0, 3) -> UReal(0.0, 0.0) : UReal
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(0, 4)),
                new ExpressionWithValue(new URealValue(0, 3))
        };
        op = ExpStdOp.create("*", args);
        assertEquals(new URealValue(0.0, 0.0), e.eval(op, state), op.toString());

        // UInteger(0, 4) * UReal(1, 0) -> UReal(0.0, 4.0) : UReal
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(0, 4)),
                new ExpressionWithValue(new URealValue(1, 0))
        };
        op = ExpStdOp.create("*", args);
        assertEquals(new URealValue(0.0, 4.0), e.eval(op, state), op.toString());

        // UInteger(0, 4) * UReal(2, 3) -> UReal(0.0, 8.0) : UReal
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(0, 4)),
                new ExpressionWithValue(new URealValue(2, 3))
        };
        op = ExpStdOp.create("*", args);
        assertEquals(new URealValue(0.0, 8.0), e.eval(op, state), op.toString());

        // UInteger(9, 0) * UReal(9, 0) -> UReal(81.0, 0.0) : UReal
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(9, 0)),
                new ExpressionWithValue(new URealValue(9, 0))
        };
        op = ExpStdOp.create("*", args);
        assertEquals(new URealValue(81.0, 0.0), e.eval(op, state), op.toString());

        // UInteger(5, 0) * UReal(5, 3) -> UReal(25.0, 15.0) : UReal
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(5, 0)),
                new ExpressionWithValue(new URealValue(5, 3))
        };
        op = ExpStdOp.create("*", args);
        assertEquals(new URealValue(25.0, 15.0), e.eval(op, state), op.toString());

        // UInteger(4, 0) * UReal(8, 0) -> UReal(32.0, 0.0) : UReal
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(4, 0)),
                new ExpressionWithValue(new URealValue(8, 0))
        };
        op = ExpStdOp.create("*", args);
        assertEquals(new URealValue(32.0, 0.0), e.eval(op, state), op.toString());

        // UInteger(10, 0) * UReal(10, 12) -> UReal(100.0, 120.0) : UReal
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(10, 0)),
                new ExpressionWithValue(new URealValue(10, 12))
        };
        op = ExpStdOp.create("*", args);
        assertEquals(new URealValue(100.0, 120.0), e.eval(op, state), op.toString());

        // UInteger(9, 5) * UReal(9, 0) -> UReal(81.0, 45.0) : UReal
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(9, 5)),
                new ExpressionWithValue(new URealValue(9, 0))
        };
        op = ExpStdOp.create("*", args);
        assertEquals(new URealValue(81.0, 45.0), e.eval(op, state), op.toString());

        // UInteger(2, 3) * UReal(2, 4) -> UReal(4.0, 10.0) : UReal
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(2, 3)),
                new ExpressionWithValue(new URealValue(2, 4))
        };
        op = ExpStdOp.create("*", args);
        assertEquals(new URealValue(4.0, 10.0), e.eval(op, state), op.toString());

        // UInteger(6, 1) * UReal(4, 0) -> UReal(24.0, 4.0) : UReal
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(6, 1)),
                new ExpressionWithValue(new URealValue(4, 0))
        };
        op = ExpStdOp.create("*", args);
        assertEquals(new URealValue(24.0, 4.0), e.eval(op, state), op.toString());

        // UInteger(2, 3) * UReal(5, 4) -> UReal(10.0, 17.0) : UReal
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(2, 3)),
                new ExpressionWithValue(new URealValue(5, 4))
        };
        op = ExpStdOp.create("*", args);
        assertEquals(new URealValue(10.0, 17.0), e.eval(op, state), op.toString());

    }

    @Test
    void testMultReal() throws ExpInvalidException {
        Expression [] args;
        Expression op;

        // UInteger(-3, 0) * -3.0 -> UReal(9.0, 0.0) : UReal
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(-3, 0)),
                new ExpressionWithValue(new RealValue(-3.0))
        };
        op = ExpStdOp.create("*", args);
        assertEquals(new URealValue(9.0, 0.0), e.eval(op, state), op.toString());

        // UInteger(-6, 0) * -1.2 -> UReal(7.2, 0.0) : UReal
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(-6, 0)),
                new ExpressionWithValue(new RealValue(-1.2))
        };
        op = ExpStdOp.create("*", args);
        assertEquals(new URealValue(7.2, 0.0), e.eval(op, state), op.toString());

        // UInteger(-5, 3) * -5.0 -> UReal(25.0, 15.0) : UReal
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(-5, 3)),
                new ExpressionWithValue(new RealValue(-5.0))
        };
        op = ExpStdOp.create("*", args);
        assertEquals(new URealValue(25.0, 15.0), e.eval(op, state), op.toString());

        // UInteger(-8, 5) * -2.0 -> UReal(16.0, 10.0) : UReal
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(-8, 5)),
                new ExpressionWithValue(new RealValue(-2.0))
        };
        op = ExpStdOp.create("*", args);
        assertEquals(new URealValue(16.0, 10.0), e.eval(op, state), op.toString());

        // UInteger(0, 0) * 0.0 -> UReal(0.0, 0.0) : UReal
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(0, 0)),
                new ExpressionWithValue(new RealValue(0.0))
        };
        op = ExpStdOp.create("*", args);
        assertEquals(new URealValue(0.0, 0.0), e.eval(op, state), op.toString());

        // UInteger(0, 0) * 3.0 -> UReal(0.0, 0.0) : UReal
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(0, 0)),
                new ExpressionWithValue(new RealValue(3.0))
        };
        op = ExpStdOp.create("*", args);
        assertEquals(new URealValue(0.0, 0.0), e.eval(op, state), op.toString());

        // UInteger(0, 3) * 0.0 -> UReal(0.0, 0.0) : UReal
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(0, 3)),
                new ExpressionWithValue(new RealValue(0.0))
        };
        op = ExpStdOp.create("*", args);
        assertEquals(new URealValue(0.0, 0.0), e.eval(op, state), op.toString());

        // UInteger(0, 5) * -5.0 -> UReal(0.0, 25.0) : UReal
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(0, 5)),
                new ExpressionWithValue(new RealValue(-5.0))
        };
        op = ExpStdOp.create("*", args);
        assertEquals(new URealValue(0.0, 25.0), e.eval(op, state), op.toString());

        // UInteger(5, 0) * 5.0 -> UReal(25.0, 0.0) : UReal
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(5, 0)),
                new ExpressionWithValue(new RealValue(5.0))
        };
        op = ExpStdOp.create("*", args);
        assertEquals(new URealValue(25.0, 0.0), e.eval(op, state), op.toString());

        // UInteger(3, 0) * 0.6 -> UReal(1.8, 0.0) : UReal
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(3, 0)),
                new ExpressionWithValue(new RealValue(0.6))
        };
        op = ExpStdOp.create("*", args);
        assertEquals(new URealValue(1.8, 0.0), e.eval(op, state), op.toString());

        // UInteger(7, 3) * 7.0 -> UReal(49.0, 21.0) : UReal
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(7, 3)),
                new ExpressionWithValue(new RealValue(7.0))
        };
        op = ExpStdOp.create("*", args);
        assertEquals(new URealValue(49.0, 21.0), e.eval(op, state), op.toString());

        // UInteger(2, 5) * 0.5 -> UReal(1.0, 2.5) : UReal
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(2, 5)),
                new ExpressionWithValue(new RealValue(0.5))
        };
        op = ExpStdOp.create("*", args);
        assertEquals(new URealValue(1.0, 2.5), e.eval(op, state), op.toString());
    }

    @Test
    void testMultInteger() throws ExpInvalidException {
        Expression [] args;
        Expression op;

        // UInteger(-3, 0) * -3 -> UInteger(9, 0.0) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(-3, 0)),
                new ExpressionWithValue(IntegerValue.valueOf(-3))
        };
        op = ExpStdOp.create("*", args);
        assertEquals(new UIntegerValue(9, 0.0), e.eval(op, state), op.toString());

        // UInteger(-6, 0) * -12 -> UInteger(72, 0.0) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(-6, 0)),
                new ExpressionWithValue(IntegerValue.valueOf(-12))
        };
        op = ExpStdOp.create("*", args);
        assertEquals(new UIntegerValue(72, 0.0), e.eval(op, state), op.toString());

        // UInteger(-5, 3) * -5 -> UInteger(25, 15.0) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(-5, 3)),
                new ExpressionWithValue(IntegerValue.valueOf(-5))
        };
        op = ExpStdOp.create("*", args);
        assertEquals(new UIntegerValue(25, 15.0), e.eval(op, state), op.toString());

        // UInteger(-8, 5) * -2 -> UInteger(16, 10.0) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(-8, 5)),
                new ExpressionWithValue(IntegerValue.valueOf(-2))
        };
        op = ExpStdOp.create("*", args);
        assertEquals(new UIntegerValue(16, 10.0), e.eval(op, state), op.toString());

        // UInteger(0, 0) * 0 -> UInteger(0, 0.0) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(0, 0)),
                new ExpressionWithValue(IntegerValue.valueOf(0))
        };
        op = ExpStdOp.create("*", args);
        assertEquals(new UIntegerValue(0, 0.0), e.eval(op, state), op.toString());

        // UInteger(0, 0) * 3 -> UInteger(0, 0.0) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(0, 0)),
                new ExpressionWithValue(IntegerValue.valueOf(3))
        };
        op = ExpStdOp.create("*", args);
        assertEquals(new UIntegerValue(0, 0.0), e.eval(op, state), op.toString());

        // UInteger(0, 3) * 0 -> UInteger(0, 0.0) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(0, 3)),
                new ExpressionWithValue(IntegerValue.valueOf(0))
        };
        op = ExpStdOp.create("*", args);
        assertEquals(new UIntegerValue(0, 0.0), e.eval(op, state), op.toString());

        // UInteger(0, 5) * -5 -> UInteger(0, 25.0) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(0, 5)),
                new ExpressionWithValue(IntegerValue.valueOf(-5))
        };
        op = ExpStdOp.create("*", args);
        assertEquals(new UIntegerValue(0, 25.0), e.eval(op, state), op.toString());

        // UInteger(5, 0) * 5 -> UInteger(25, 0.0) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(5, 0)),
                new ExpressionWithValue(IntegerValue.valueOf(5))
        };
        op = ExpStdOp.create("*", args);
        assertEquals(new UIntegerValue(25, 0.0), e.eval(op, state), op.toString());

        // UInteger(3, 0) * 56 -> UInteger(168, 0.0) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(3, 0)),
                new ExpressionWithValue(IntegerValue.valueOf(56))
        };
        op = ExpStdOp.create("*", args);
        assertEquals(new UIntegerValue(168, 0.0), e.eval(op, state), op.toString());

        // UInteger(7, 3) * 7 -> UInteger(49, 21.0) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(7, 3)),
                new ExpressionWithValue(IntegerValue.valueOf(7))
        };
        op = ExpStdOp.create("*", args);
        assertEquals(new UIntegerValue(49, 21.0), e.eval(op, state), op.toString());

        // UInteger(2, 5) * 65 -> UInteger(130, 325.0) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(2, 5)),
                new ExpressionWithValue(IntegerValue.valueOf(65))
        };
        op = ExpStdOp.create("*", args);
        assertEquals(new UIntegerValue(130, 325.0), e.eval(op, state), op.toString());
    }

    @Test
    void testDividyByRBetweenUInteger() throws ExpInvalidException {
        Expression [] args;
        Expression op;

        // UInteger(-9, 0) / UInteger(-9, 0) -> UReal(1.0, 0.0) : UReal
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(-9, 0)),
                new ExpressionWithValue(new UIntegerValue(-9, 0))
        };
        op = ExpStdOp.create("/", args);
        assertEquals(new URealValue(1.0, 0.0), e.eval(op, state), op.toString());

        // UInteger(-5, 0) / UInteger(-5, 3) -> UReal(1.0, 0.12) : UReal
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(-5, 0)),
                new ExpressionWithValue(new UIntegerValue(-5, 3))
        };
        op = ExpStdOp.create("/", args);
        assertEquals(new URealValue(1.0, 0.12), e.eval(op, state), op.toString());

        // UInteger(-4, 0) / UInteger(2, 0) -> UReal(-2.0, 0.0) : UReal
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(-4, 0)),
                new ExpressionWithValue(new UIntegerValue(2, 0))
        };
        op = ExpStdOp.create("/", args);
        assertEquals(new URealValue(-2.0, 0.0), e.eval(op, state), op.toString());

        // UInteger(-10, 0) / UInteger(4, 1) -> UReal(-2.5, 0.0625) : UReal
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(-10, 0)),
                new ExpressionWithValue(new UIntegerValue(4, 1))
        };
        op = ExpStdOp.create("/", args);
        assertEquals(new URealValue(-2.5, 0.0625), e.eval(op, state), op.toString());

        // UInteger(-9, 9) / UInteger(-9, 0) -> UReal(1.0, -1.0) : UReal
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(-9, 9)),
                new ExpressionWithValue(new UIntegerValue(-9, 0))
        };
        op = ExpStdOp.create("/", args);
        assertEquals(new URealValue(1.0, -1.0), e.eval(op, state), op.toString());

        // UInteger(-2, 3) / UInteger(-2, 4) -> UReal(5.0, 2.915475947) : UReal
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(-2, 3)),
                new ExpressionWithValue(new UIntegerValue(-2, 4))
        };
        op = ExpStdOp.create("/", args);
        assertEquals(new URealValue(1.0, 2.9154759474), e.eval(op, state), op.toString());

        // UInteger(-6, 2) / UInteger(5, 0) -> UReal(-1.2, 0.4) : UReal
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(-6, 2)),
                new ExpressionWithValue(new UIntegerValue(5, 0))
        };
        op = ExpStdOp.create("/", args);
        assertEquals(new URealValue(-1.2, 0.4), e.eval(op, state), op.toString());

        // UInteger(-2, 3) / UInteger(2, 4) -> UReal(-5.0, 2.915475947) : UReal
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(-2, 3)),
                new ExpressionWithValue(new UIntegerValue(2, 4))
        };
        op = ExpStdOp.create("/", args);
        assertEquals(new URealValue(-1.0, 2.9154759474), e.eval(op, state), op.toString());

        // UInteger(0, 0) / UInteger(0, 0) -> Undefined : OclVoid
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(0, 0)),
                new ExpressionWithValue(new UIntegerValue(0, 0))
        };
        op = ExpStdOp.create("/", args);
        assertEquals(UndefinedValue.instance, e.eval(op, state), op.toString());

        // UInteger(0, 0) / UInteger(0, 4) -> Undefined : OclVoid
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(0, 0)),
                new ExpressionWithValue(new UIntegerValue(0, 4))
        };
        op = ExpStdOp.create("/", args);
        assertEquals(UndefinedValue.instance, e.eval(op, state), op.toString());

        // UInteger(0, 0) / UInteger(6, 0) -> UReal(0.0, 0.0) : UReal
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(0, 0)),
                new ExpressionWithValue(new UIntegerValue(6, 0))
        };
        op = ExpStdOp.create("/", args);
        assertEquals(new URealValue(0.0, 0.0), e.eval(op, state), op.toString());

        // UInteger(0, 0) / UInteger(7, 3) -> UReal(0.0, 0.06122449) : UReal
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(0, 0)),
                new ExpressionWithValue(new UIntegerValue(7, 3))
        };
        op = ExpStdOp.create("/", args);
        assertEquals(new URealValue(0.0, 0.0612244898), e.eval(op, state), op.toString());

        // UInteger(0, 4) / UInteger(0, 0) -> Undefined : OclVoid
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(0, 4)),
                new ExpressionWithValue(new UIntegerValue(0, 0))
        };
        op = ExpStdOp.create("/", args);
        assertEquals(UndefinedValue.instance, e.eval(op, state), op.toString());

        // UInteger(0, 4) / UInteger(0, 3) -> Undefined : OclVoid
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(0, 4)),
                new ExpressionWithValue(new UIntegerValue(0, 3))
        };
        op = ExpStdOp.create("/", args);
        assertEquals(UndefinedValue.instance, e.eval(op, state), op.toString());

        // UInteger(0, 4) / UInteger(1, 0) -> UReal(0.0, 4.0) : UReal
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(0, 4)),
                new ExpressionWithValue(new UIntegerValue(1, 0))
        };
        op = ExpStdOp.create("/", args);
        assertEquals(new URealValue(0.0, 4.0), e.eval(op, state), op.toString());

        // UInteger(0, 4) / UInteger(2, 3) -> UReal(0.0, 2.828427125) : UReal
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(0, 4)),
                new ExpressionWithValue(new UIntegerValue(2, 3))
        };
        op = ExpStdOp.create("/", args);
        assertEquals(new URealValue(0.0, 2.8284271247), e.eval(op, state), op.toString());

        // UInteger(9, 0) / UInteger(9, 0) -> UReal(1.0, 0.0) : UReal
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(9, 0)),
                new ExpressionWithValue(new UIntegerValue(9, 0))
        };
        op = ExpStdOp.create("/", args);
        assertEquals(new URealValue(1.0, 0.0), e.eval(op, state), op.toString());

        // UInteger(5, 0) / UInteger(5, 3) -> UReal(1.0, 0.12) : UReal
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(5, 0)),
                new ExpressionWithValue(new UIntegerValue(5, 3))
        };
        op = ExpStdOp.create("/", args);
        assertEquals(new URealValue(1.0, 0.12), e.eval(op, state), op.toString());

        // UInteger(4, 0) / UInteger(8, 0) -> UReal(0.5, 0.0) : UReal
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(4, 0)),
                new ExpressionWithValue(new UIntegerValue(8, 0))
        };
        op = ExpStdOp.create("/", args);
        assertEquals(new URealValue(0.5, 0.0), e.eval(op, state), op.toString());

        // UInteger(10, 0) / UInteger(10, 12) -> UReal(1.0, 0.12) : UReal
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(10, 0)),
                new ExpressionWithValue(new UIntegerValue(10, 12))
        };
        op = ExpStdOp.create("/", args);
        assertEquals(new URealValue(1.0, 0.12), e.eval(op, state), op.toString());

        // UInteger(9, 5) / UInteger(9, 0) -> UReal(1.0, 0.555555556) : UReal
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(9, 5)),
                new ExpressionWithValue(new UIntegerValue(9, 0))
        };
        op = ExpStdOp.create("/", args);
        assertEquals(new URealValue(1.0, 0.5555555556), e.eval(op, state), op.toString());

        // UInteger(2, 3) / UInteger(2, 4) -> UReal(5.0, 2.915475947) : UReal
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(2, 3)),
                new ExpressionWithValue(new UIntegerValue(2, 4))
        };
        op = ExpStdOp.create("/", args);
        assertEquals(new URealValue(1.0, 2.9154759474), e.eval(op, state), op.toString());

        // UInteger(6, 1) / UInteger(4, 0) -> UReal(1.5, 0.25) : UReal
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(6, 1)),
                new ExpressionWithValue(new UIntegerValue(4, 0))
        };
        op = ExpStdOp.create("/", args);
        assertEquals(new URealValue(1.5, 0.25), e.eval(op, state), op.toString());

        // UInteger(2, 3) / UInteger(5, 4) -> UReal(0.4, 1.379275172) : UReal
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(2, 3)),
                new ExpressionWithValue(new UIntegerValue(5, 4))
        };
        op = ExpStdOp.create("/", args);
        assertEquals(new URealValue(0.4, 1.379275172), e.eval(op, state), op.toString());
    }

    @Test
    void testDivideByRWithUReal() throws ExpInvalidException {
        Expression [] args;
        Expression op;

        // UInteger(-9, 0) / UReal(-9, 0) -> UReal(1.0, 0.0) : UReal
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(-9, 0)),
                new ExpressionWithValue(new UIntegerValue(-9, 0))
        };
        op = ExpStdOp.create("/", args);
        assertEquals(new URealValue(1.0, 0.0), e.eval(op, state), op.toString());

        // UInteger(-5, 0) / UReal(-5, 3) -> UReal(1.0, 0.12) : UReal
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(-5, 0)),
                new ExpressionWithValue(new UIntegerValue(-5, 3))
        };
        op = ExpStdOp.create("/", args);
        assertEquals(new URealValue(1.0, 0.12), e.eval(op, state), op.toString());

        // UInteger(-4, 0) / UReal(2, 0) -> UReal(-2.0, 0.0) : UReal
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(-4, 0)),
                new ExpressionWithValue(new UIntegerValue(2, 0))
        };
        op = ExpStdOp.create("/", args);
        assertEquals(new URealValue(-2.0, 0.0), e.eval(op, state), op.toString());

        // UInteger(-10, 0) / UReal(4, 1) -> UReal(-2.5, 0.0625) : UReal
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(-10, 0)),
                new ExpressionWithValue(new UIntegerValue(4, 1))
        };
        op = ExpStdOp.create("/", args);
        assertEquals(new URealValue(-2.5, 0.0625), e.eval(op, state), op.toString());

        // UInteger(-9, 9) / UReal(-9, 0) -> UReal(1.0, -1.0) : UReal
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(-9, 9)),
                new ExpressionWithValue(new UIntegerValue(-9, 0))
        };
        op = ExpStdOp.create("/", args);
        assertEquals(new URealValue(1.0, -1.0), e.eval(op, state), op.toString());

        // UInteger(-2, 3) / UReal(-2, 4) -> UReal(1.0, 2.9154759474) : UReal
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(-2, 3)),
                new ExpressionWithValue(new UIntegerValue(-2, 4))
        };
        op = ExpStdOp.create("/", args);
        assertEquals(new URealValue(1.0, 2.9154759474), e.eval(op, state), op.toString());

        // UInteger(-6, 2) / UReal(5, 0) -> UReal(-1.2, 0.4) : UReal
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(-6, 2)),
                new ExpressionWithValue(new UIntegerValue(5, 0))
        };
        op = ExpStdOp.create("/", args);
        assertEquals(new URealValue(-1.2, 0.4), e.eval(op, state), op.toString());

        // UInteger(-2, 3) / UReal(2, 4) -> UReal(-1.0, 2.9154759474) : UReal
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(-2, 3)),
                new ExpressionWithValue(new UIntegerValue(2, 4))
        };
        op = ExpStdOp.create("/", args);
        assertEquals(new URealValue(-1.0, 2.9154759474), e.eval(op, state), op.toString());

        // UInteger(0, 0) / UReal(0, 0) -> Undefined : OclVoid
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(0, 0)),
                new ExpressionWithValue(new UIntegerValue(0, 0))
        };
        op = ExpStdOp.create("/", args);
        assertEquals(UndefinedValue.instance, e.eval(op, state), op.toString());

        // UInteger(0, 0) / UReal(0, 4) -> Undefined : OclVoid
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(0, 0)),
                new ExpressionWithValue(new UIntegerValue(0, 4))
        };
        op = ExpStdOp.create("/", args);
        assertEquals(UndefinedValue.instance, e.eval(op, state), op.toString());

        // UInteger(0, 0) / UReal(6, 0) -> UReal(0.0, 0.0) : UReal
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(0, 0)),
                new ExpressionWithValue(new UIntegerValue(6, 0))
        };
        op = ExpStdOp.create("/", args);
        assertEquals(new URealValue(0.0, 0.0), e.eval(op, state), op.toString());

        // UInteger(0, 0) / UReal(7, 3) -> UReal(0.0, 0.0612244898) : UReal
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(0, 0)),
                new ExpressionWithValue(new UIntegerValue(7, 3))
        };
        op = ExpStdOp.create("/", args);
        assertEquals(new URealValue(0.0, 0.0612244898), e.eval(op, state), op.toString());

        // UInteger(0, 4) / UReal(0, 0) -> Undefined : OclVoid
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(0, 4)),
                new ExpressionWithValue(new UIntegerValue(0, 0))
        };
        op = ExpStdOp.create("/", args);
        assertEquals(UndefinedValue.instance, e.eval(op, state), op.toString());

        // UInteger(0, 4) / UReal(0, 3) -> Undefined : OclVoid
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(0, 4)),
                new ExpressionWithValue(new UIntegerValue(0, 3))
        };
        op = ExpStdOp.create("/", args);
        assertEquals(UndefinedValue.instance, e.eval(op, state), op.toString());

        // UInteger(0, 4) / UReal(1, 0) -> UReal(0.0, 4.0) : UReal
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(0, 4)),
                new ExpressionWithValue(new UIntegerValue(1, 0))
        };
        op = ExpStdOp.create("/", args);
        assertEquals(new URealValue(0.0, 4.0), e.eval(op, state), op.toString());

        // UInteger(0, 4) / UReal(2, 3) -> UReal(0.0, 2.8284271247) : UReal
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(0, 4)),
                new ExpressionWithValue(new UIntegerValue(2, 3))
        };
        op = ExpStdOp.create("/", args);
        assertEquals(new URealValue(0.0, 2.8284271247), e.eval(op, state), op.toString());

        // UInteger(9, 0) / UReal(9, 0) -> UReal(1.0, 0.0) : UReal
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(9, 0)),
                new ExpressionWithValue(new UIntegerValue(9, 0))
        };
        op = ExpStdOp.create("/", args);
        assertEquals(new URealValue(1.0, 0.0), e.eval(op, state), op.toString());

        // UInteger(5, 0) / UReal(5, 3) -> UReal(1.0, 0.12) : UReal
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(5, 0)),
                new ExpressionWithValue(new UIntegerValue(5, 3))
        };
        op = ExpStdOp.create("/", args);
        assertEquals(new URealValue(1.0, 0.12), e.eval(op, state), op.toString());

        // UInteger(4, 0) / UReal(8, 0) -> UReal(0.5, 0.0) : UReal
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(4, 0)),
                new ExpressionWithValue(new UIntegerValue(8, 0))
        };
        op = ExpStdOp.create("/", args);
        assertEquals(new URealValue(0.5, 0.0), e.eval(op, state), op.toString());

        // UInteger(10, 0) / UReal(10, 12) -> UReal(1.0, 0.12) : UReal
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(10, 0)),
                new ExpressionWithValue(new UIntegerValue(10, 12))
        };
        op = ExpStdOp.create("/", args);
        assertEquals(new URealValue(1.0, 0.12), e.eval(op, state), op.toString());

        // UInteger(9, 5) / UReal(9, 0) -> UReal(1.0, 0.5555555556) : UReal
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(9, 5)),
                new ExpressionWithValue(new UIntegerValue(9, 0))
        };
        op = ExpStdOp.create("/", args);
        assertEquals(new URealValue(1.0, 0.5555555556), e.eval(op, state), op.toString());

        // UInteger(2, 3) / UReal(2, 4) -> UReal(1.0, 2.9154759474) : UReal
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(2, 3)),
                new ExpressionWithValue(new UIntegerValue(2, 4))
        };
        op = ExpStdOp.create("/", args);
        assertEquals(new URealValue(1.0, 2.9154759474), e.eval(op, state), op.toString());

        // UInteger(6, 1) / UReal(4, 0) -> UReal(1.5, 0.25) : UReal
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(6, 1)),
                new ExpressionWithValue(new UIntegerValue(4, 0))
        };
        op = ExpStdOp.create("/", args);
        assertEquals(new URealValue(1.5, 0.25), e.eval(op, state), op.toString());

        // UInteger(2, 3) / UReal(5, 4) -> UReal(0.4, 1.379275172) : UReal
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(2, 3)),
                new ExpressionWithValue(new UIntegerValue(5, 4))
        };
        op = ExpStdOp.create("/", args);
        assertEquals(new URealValue(0.4, 1.379275172), e.eval(op, state), op.toString());
    }

    @Test
    void testDivideByRWithReal() throws ExpInvalidException {
        Expression [] args;
        Expression op;

        // UInteger(-3, 0) / -3.0 -> UReal(1.0, 0) : UReal
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(-3, 0)),
                new ExpressionWithValue(new RealValue(-3.0))
        };
        op = ExpStdOp.create("/", args);
        assertEquals(new URealValue(1.0, 0), e.eval(op, state), op.toString());

        // UInteger(-6, 0) / -1.2 -> UReal(5.0, 0) : UReal
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(-6, 0)),
                new ExpressionWithValue(new RealValue(-1.2))
        };
        op = ExpStdOp.create("/", args);
        assertEquals(new URealValue(5.0, 0), e.eval(op, state), op.toString());

        // UInteger(-5, 3) / -5.0 -> UReal(1.0, -0.6) : UReal
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(-5, 3)),
                new ExpressionWithValue(new RealValue(-5.0))
        };
        op = ExpStdOp.create("/", args);
        assertEquals(new URealValue(1.0, -0.6), e.eval(op, state), op.toString());

        // UInteger(-8, 5) / -2.0 -> UReal(4.0, -2.5) : UReal
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(-8, 5)),
                new ExpressionWithValue(new RealValue(-2.0))
        };
        op = ExpStdOp.create("/", args);
        assertEquals(new URealValue(4.0, -2.5), e.eval(op, state), op.toString());

        // UInteger(0, 0) / 0.0 -> Undefined : OclVoid
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(0, 0)),
                new ExpressionWithValue(new RealValue(0.0))
        };
        op = ExpStdOp.create("/", args);
        assertEquals(UndefinedValue.instance, e.eval(op, state), op.toString());

        // UInteger(0, 0) / 3.0 -> UReal(0.0, 0) : UReal
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(0, 0)),
                new ExpressionWithValue(new RealValue(3.0))
        };
        op = ExpStdOp.create("/", args);
        assertEquals(new URealValue(0.0, 0), e.eval(op, state), op.toString());

        // UInteger(0, 3) / 0.0 -> Undefined : OclVoid
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(0, 3)),
                new ExpressionWithValue(new RealValue(0.0))
        };
        op = ExpStdOp.create("/", args);
        assertEquals(UndefinedValue.instance, e.eval(op, state), op.toString());

        // UInteger(0, 5) / -5.0 -> UReal(0.0, -1) : UReal
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(0, 5)),
                new ExpressionWithValue(new RealValue(-5.0))
        };
        op = ExpStdOp.create("/", args);
        assertEquals(new URealValue(0.0, -1), e.eval(op, state), op.toString());

        // UInteger(5, 0) / 5.0 -> UReal(1.0, 0) : UReal
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(5, 0)),
                new ExpressionWithValue(new RealValue(5.0))
        };
        op = ExpStdOp.create("/", args);
        assertEquals(new URealValue(1.0, 0), e.eval(op, state), op.toString());

        // UInteger(3, 0) / 0.6 -> UReal(5.0, 0) : UReal
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(3, 0)),
                new ExpressionWithValue(new RealValue(0.6))
        };
        op = ExpStdOp.create("/", args);
        assertEquals(new URealValue(5.0, 0), e.eval(op, state), op.toString());

        // UInteger(7, 3) / 7.0 -> UReal(1.0, 0.4285714286) : UReal
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(7, 3)),
                new ExpressionWithValue(new RealValue(7.0))
        };
        op = ExpStdOp.create("/", args);
        assertEquals(new URealValue(1.0, 0.4285714286), e.eval(op, state), op.toString());

        // UInteger(2, 5) / 0.5 -> UReal(4.0, 10) : UReal
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(2, 5)),
                new ExpressionWithValue(new RealValue(0.5))
        };
        op = ExpStdOp.create("/", args);
        assertEquals(new URealValue(4.0, 10), e.eval(op, state), op.toString());
    }

    @Test
    void testDivideByRWithInteger() throws ExpInvalidException {
        Expression [] args;
        Expression op;

        // UInteger(-3, 0) / -3 -> UReal(1.0, 0) : UReal
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(-3, 0)),
                new ExpressionWithValue(IntegerValue.valueOf(-3))
        };
        op = ExpStdOp.create("/", args);
        assertEquals(new URealValue(1.0, 0), e.eval(op, state), op.toString());

        // UInteger(-6, 0) / -12 -> UReal(0.5, 0) : UReal
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(-6, 0)),
                new ExpressionWithValue(IntegerValue.valueOf(-12))
        };
        op = ExpStdOp.create("/", args);
        assertEquals(new URealValue(0.5, 0), e.eval(op, state), op.toString());

        // UInteger(-5, 3) / -5 -> UReal(1.0, -0.6) : UReal
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(-5, 3)),
                new ExpressionWithValue(IntegerValue.valueOf(-5))
        };
        op = ExpStdOp.create("/", args);
        assertEquals(new URealValue(1.0, -0.6), e.eval(op, state), op.toString());

        // UInteger(-8, 5) / -2 -> UReal(4.0, -2.5) : UReal
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(-8, 5)),
                new ExpressionWithValue(IntegerValue.valueOf(-2))
        };
        op = ExpStdOp.create("/", args);
        assertEquals(new URealValue(4.0, -2.5), e.eval(op, state), op.toString());

        // UInteger(0, 0) / 0 -> Undefined : OclVoid
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(0, 0)),
                new ExpressionWithValue(IntegerValue.valueOf(0))
        };
        op = ExpStdOp.create("/", args);
        assertEquals(UndefinedValue.instance, e.eval(op, state), op.toString());

        // UInteger(0, 0) / 3 -> UReal(0.0, 0) : UReal
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(0, 0)),
                new ExpressionWithValue(IntegerValue.valueOf(3))
        };
        op = ExpStdOp.create("/", args);
        assertEquals(new URealValue(0.0, 0), e.eval(op, state), op.toString());

        // UInteger(0, 3) / 0 -> Undefined : OclVoid
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(0, 3)),
                new ExpressionWithValue(IntegerValue.valueOf(0))
        };
        op = ExpStdOp.create("/", args);
        assertEquals(UndefinedValue.instance, e.eval(op, state), op.toString());

        // UInteger(0, 5) / -5 -> UReal(0.0, -1) : UReal
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(0, 5)),
                new ExpressionWithValue(IntegerValue.valueOf(-5))
        };
        op = ExpStdOp.create("/", args);
        assertEquals(new URealValue(0.0, -1), e.eval(op, state), op.toString());

        // UInteger(5, 0) / 5 -> UReal(1.0, 0) : UReal
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(5, 0)),
                new ExpressionWithValue(IntegerValue.valueOf(5))
        };
        op = ExpStdOp.create("/", args);
        assertEquals(new URealValue(1.0, 0), e.eval(op, state), op.toString());

        // UInteger(3, 0) / 56 -> UReal(0.0535714286, 0) : UReal
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(3, 0)),
                new ExpressionWithValue(IntegerValue.valueOf(56))
        };
        op = ExpStdOp.create("/", args);
        assertEquals(new URealValue(0.0535714286, 0), e.eval(op, state), op.toString());

        // UInteger(7, 3) / 7 -> UReal(1.0, 0.4285714286) : UReal
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(7, 3)),
                new ExpressionWithValue(IntegerValue.valueOf(7))
        };
        op = ExpStdOp.create("/", args);
        assertEquals(new URealValue(1.0, 0.4285714286), e.eval(op, state), op.toString());

        // UInteger(2, 5) / 65 -> UReal(0.0, 0.0769230769) : UReal
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(2, 5)),
                new ExpressionWithValue(IntegerValue.valueOf(65))
        };
        op = ExpStdOp.create("/", args);
        assertEquals(new URealValue(0.0307692308, 0.0769230769), e.eval(op, state), op.toString());
    }

    // Div operation

    @Test
    void testDivideByBetweenUInteger() throws ExpInvalidException {
        Expression [] args;
        Expression op;

        // UInteger(-9, 0)  div UInteger(-9, 0) -> UInteger(1, 0.0) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(-9, 0)),
                new ExpressionWithValue(new UIntegerValue(-9, 0))
        };
        op = ExpStdOp.create("div", args);
        assertEquals(new UIntegerValue(1, 0.0), e.eval(op, state), op.toString());

        // UInteger(-5, 0)  div UInteger(-5, 3) -> UInteger(1, 0.12) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(-5, 0)),
                new ExpressionWithValue(new UIntegerValue(-5, 3))
        };
        op = ExpStdOp.create("div", args);
        assertEquals(new UIntegerValue(1, 0.12), e.eval(op, state), op.toString());

        // UInteger(-4, 0)  div UInteger(2, 0) -> UInteger(-2, 0.0) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(-4, 0)),
                new ExpressionWithValue(new UIntegerValue(2, 0))
        };
        op = ExpStdOp.create("div", args);
        assertEquals(new UIntegerValue(-2, 0.0), e.eval(op, state), op.toString());

        // UInteger(-10, 0)  div UInteger(4, 1) -> UInteger(-2, 0.0625) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(-10, 0)),
                new ExpressionWithValue(new UIntegerValue(4, 1))
        };
        op = ExpStdOp.create("div", args);
        assertEquals(new UIntegerValue(-2, 0.0625), e.eval(op, state), op.toString());

        // UInteger(-9, 9)  div UInteger(-9, 0) -> UInteger(1, -1.0) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(-9, 9)),
                new ExpressionWithValue(new UIntegerValue(-9, 0))
        };
        op = ExpStdOp.create("div", args);
        assertEquals(new UIntegerValue(1, -1.0), e.eval(op, state), op.toString());

        // UInteger(-2, 3)  div UInteger(-2, 4) -> UInteger(1, 2.9154759474) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(-2, 3)),
                new ExpressionWithValue(new UIntegerValue(-2, 4))
        };
        op = ExpStdOp.create("div", args);
        assertEquals(new UIntegerValue(1, 2.9154759474), e.eval(op, state), op.toString());

        // UInteger(-6, 2)  div UInteger(5, 0) -> UInteger(-1, 0.4) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(-6, 2)),
                new ExpressionWithValue(new UIntegerValue(5, 0))
        };
        op = ExpStdOp.create("div", args);
        assertEquals(new UIntegerValue(-1, 0.4), e.eval(op, state), op.toString());

        // UInteger(-2, 3)  div UInteger(2, 4) -> UInteger(-1, 2.9154759474) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(-2, 3)),
                new ExpressionWithValue(new UIntegerValue(2, 4))
        };
        op = ExpStdOp.create("div", args);
        assertEquals(new UIntegerValue(-1, 2.9154759474), e.eval(op, state), op.toString());

        // UInteger(0, 0)  div UInteger(0, 0) -> Undefined : OclVoid
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(0, 0)),
                new ExpressionWithValue(new UIntegerValue(0, 0))
        };
        op = ExpStdOp.create("div", args);
        assertEquals(UndefinedValue.instance, e.eval(op, state), op.toString());

        // UInteger(0, 0)  div UInteger(0, 4) -> Undefined : OclVoid
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(0, 0)),
                new ExpressionWithValue(new UIntegerValue(0, 4))
        };
        op = ExpStdOp.create("div", args);
        assertEquals(UndefinedValue.instance, e.eval(op, state), op.toString());

        // UInteger(0, 0)  div UInteger(6, 0) -> UInteger(0, 0.0) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(0, 0)),
                new ExpressionWithValue(new UIntegerValue(6, 0))
        };
        op = ExpStdOp.create("div", args);
        assertEquals(new UIntegerValue(0, 0.0), e.eval(op, state), op.toString());

        // UInteger(0, 0)  div UInteger(7, 3) -> UInteger(0, 0.0612244898) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(0, 0)),
                new ExpressionWithValue(new UIntegerValue(7, 3))
        };
        op = ExpStdOp.create("div", args);
        assertEquals(new UIntegerValue(0, 0.0612244898), e.eval(op, state), op.toString());

        // UInteger(0, 4)  div UInteger(0, 0) -> Undefined : OclVoid
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(0, 4)),
                new ExpressionWithValue(new UIntegerValue(0, 0))
        };
        op = ExpStdOp.create("div", args);
        assertEquals(UndefinedValue.instance, e.eval(op, state), op.toString());

        // UInteger(0, 4)  div UInteger(0, 3) -> Undefined : OclVoid
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(0, 4)),
                new ExpressionWithValue(new UIntegerValue(0, 3))
        };
        op = ExpStdOp.create("div", args);
        assertEquals(UndefinedValue.instance, e.eval(op, state), op.toString());

        // UInteger(0, 4)  div UInteger(1, 0) -> UInteger(0, 4.0) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(0, 4)),
                new ExpressionWithValue(new UIntegerValue(1, 0))
        };
        op = ExpStdOp.create("div", args);
        assertEquals(new UIntegerValue(0, 4.0), e.eval(op, state), op.toString());

        // UInteger(0, 4)  div UInteger(2, 3) -> UInteger(0, 2.8284271247) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(0, 4)),
                new ExpressionWithValue(new UIntegerValue(2, 3))
        };
        op = ExpStdOp.create("div", args);
        assertEquals(new UIntegerValue(0, 2.8284271247), e.eval(op, state), op.toString());

        // UInteger(9, 0)  div UInteger(9, 0) -> UInteger(1, 0.0) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(9, 0)),
                new ExpressionWithValue(new UIntegerValue(9, 0))
        };
        op = ExpStdOp.create("div", args);
        assertEquals(new UIntegerValue(1, 0.0), e.eval(op, state), op.toString());

        // UInteger(5, 0)  div UInteger(5, 3) -> UInteger(1, 0.12) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(5, 0)),
                new ExpressionWithValue(new UIntegerValue(5, 3))
        };
        op = ExpStdOp.create("div", args);
        assertEquals(new UIntegerValue(1, 0.12), e.eval(op, state), op.toString());

        // UInteger(4, 0)  div UInteger(8, 0) -> UInteger(0, 0.0) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(4, 0)),
                new ExpressionWithValue(new UIntegerValue(8, 0))
        };
        op = ExpStdOp.create("div", args);
        assertEquals(new UIntegerValue(0, 0.0), e.eval(op, state), op.toString());

        // UInteger(10, 0)  div UInteger(10, 12) -> UInteger(1, 0.12) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(10, 0)),
                new ExpressionWithValue(new UIntegerValue(10, 12))
        };
        op = ExpStdOp.create("div", args);
        assertEquals(new UIntegerValue(1, 0.12), e.eval(op, state), op.toString());

        // UInteger(9, 5)  div UInteger(9, 0) -> UInteger(1, 0.5555555556) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(9, 5)),
                new ExpressionWithValue(new UIntegerValue(9, 0))
        };
        op = ExpStdOp.create("div", args);
        assertEquals(new UIntegerValue(1, 0.5555555556), e.eval(op, state), op.toString());

        // UInteger(2, 3)  div UInteger(2, 4) -> UInteger(1, 2.9154759474) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(2, 3)),
                new ExpressionWithValue(new UIntegerValue(2, 4))
        };
        op = ExpStdOp.create("div", args);
        assertEquals(new UIntegerValue(1, 2.9154759474), e.eval(op, state), op.toString());

        // UInteger(6, 1)  div UInteger(4, 0) -> UInteger(1, 0.25) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(6, 1)),
                new ExpressionWithValue(new UIntegerValue(4, 0))
        };
        op = ExpStdOp.create("div", args);
        assertEquals(new UIntegerValue(1, 0.25), e.eval(op, state), op.toString());

        // UInteger(2, 3)  div UInteger(5, 4) -> UInteger(0, 1.379275172) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(2, 3)),
                new ExpressionWithValue(new UIntegerValue(5, 4))
        };
        op = ExpStdOp.create("div", args);
        assertEquals(new UIntegerValue(0, 1.379275172), e.eval(op, state), op.toString());
    }

    @Test
    void testDivideByWithInteger() throws ExpInvalidException {
        Expression [] args;
        Expression op;

        // UInteger(-3, 0)  div -3 -> UInteger(1, 0) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(-3, 0)),
                new ExpressionWithValue(IntegerValue.valueOf(-3))
        };
        op = ExpStdOp.create("div", args);
        assertEquals(new UIntegerValue(1, 0), e.eval(op, state), op.toString());

        // UInteger(-6, 0)  div -12 -> UInteger(0, 0) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(-6, 0)),
                new ExpressionWithValue(IntegerValue.valueOf(-12))
        };
        op = ExpStdOp.create("div", args);
        assertEquals(new UIntegerValue(0, 0), e.eval(op, state), op.toString());

        // UInteger(-5, 3)  div -5 -> UInteger(1, -0.6) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(-5, 3)),
                new ExpressionWithValue(IntegerValue.valueOf(-5))
        };
        op = ExpStdOp.create("div", args);
        assertEquals(new UIntegerValue(1, -0.6), e.eval(op, state), op.toString());

        // UInteger(-8, 5)  div -2 -> UInteger(4, -2.5) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(-8, 5)),
                new ExpressionWithValue(IntegerValue.valueOf(-2))
        };
        op = ExpStdOp.create("div", args);
        assertEquals(new UIntegerValue(4, -2.5), e.eval(op, state), op.toString());

        // UInteger(0, 0)  div 0 -> Undefined : OclVoid
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(0, 0)),
                new ExpressionWithValue(IntegerValue.valueOf(0))
        };
        op = ExpStdOp.create("div", args);
        assertEquals(UndefinedValue.instance, e.eval(op, state), op.toString());

        // UInteger(0, 0)  div 3 -> UInteger(0, 0) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(0, 0)),
                new ExpressionWithValue(IntegerValue.valueOf(3))
        };
        op = ExpStdOp.create("div", args);
        assertEquals(new UIntegerValue(0, 0), e.eval(op, state), op.toString());

        // UInteger(0, 3)  div 0 -> Undefined : OclVoid
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(0, 3)),
                new ExpressionWithValue(IntegerValue.valueOf(0))
        };
        op = ExpStdOp.create("div", args);
        assertEquals(UndefinedValue.instance, e.eval(op, state), op.toString());

        // UInteger(0, 5)  div -5 -> UInteger(0, -1) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(0, 5)),
                new ExpressionWithValue(IntegerValue.valueOf(-5))
        };
        op = ExpStdOp.create("div", args);
        assertEquals(new UIntegerValue(0, -1), e.eval(op, state), op.toString());

        // UInteger(5, 0)  div 5 -> UInteger(1, 0) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(5, 0)),
                new ExpressionWithValue(IntegerValue.valueOf(5))
        };
        op = ExpStdOp.create("div", args);
        assertEquals(new UIntegerValue(1, 0), e.eval(op, state), op.toString());

        // UInteger(3, 0)  div 56 -> UInteger(0, 0) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(3, 0)),
                new ExpressionWithValue(IntegerValue.valueOf(56))
        };
        op = ExpStdOp.create("div", args);
        assertEquals(new UIntegerValue(0, 0), e.eval(op, state), op.toString());

        // UInteger(7, 3)  div 7 -> UInteger(1, 0.4285714286) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(7, 3)),
                new ExpressionWithValue(IntegerValue.valueOf(7))
        };
        op = ExpStdOp.create("div", args);
        assertEquals(new UIntegerValue(1, 0.4285714286), e.eval(op, state), op.toString());

        // UInteger(2, 5)  div 65 -> UInteger(0, 0.0769230769) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(2, 5)),
                new ExpressionWithValue(IntegerValue.valueOf(65))
        };
        op = ExpStdOp.create("div", args);
        assertEquals(new UIntegerValue(0, 0.0769230769), e.eval(op, state), op.toString());
    }


    // MOD OPERATOR

    @Test
    void testModBetweenUInteger() throws ExpInvalidException {
        Expression [] args;
        Expression op;

        // UInteger(-9, 0)  mod UInteger(-9, 0) -> UInteger(0, 0.0) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(-9, 0)),
                new ExpressionWithValue(new UIntegerValue(-9, 0))
        };
        op = ExpStdOp.create("mod", args);
        assertEquals(new UIntegerValue(0, 0.0), e.eval(op, state), op.toString());

        // UInteger(-5, 0)  mod UInteger(-5, 3) -> UInteger(0, 0.12) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(-5, 0)),
                new ExpressionWithValue(new UIntegerValue(-5, 3))
        };
        op = ExpStdOp.create("mod", args);
        assertEquals(new UIntegerValue(0, 0.12), e.eval(op, state), op.toString());

        // UInteger(-4, 0)  mod UInteger(2, 0) -> UInteger(0, 0.0) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(-4, 0)),
                new ExpressionWithValue(new UIntegerValue(2, 0))
        };
        op = ExpStdOp.create("mod", args);
        assertEquals(new UIntegerValue(0, 0.0), e.eval(op, state), op.toString());

        // UInteger(-10, 0)  mod UInteger(4, 1) -> UInteger(2, 0.0625) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(-10, 0)),
                new ExpressionWithValue(new UIntegerValue(4, 1))
        };
        op = ExpStdOp.create("mod", args);
        assertEquals(new UIntegerValue(-2, 0.0625), e.eval(op, state), op.toString());

        // UInteger(-9, 9)  mod UInteger(-9, 0) -> UInteger(0, -1.0) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(-9, 9)),
                new ExpressionWithValue(new UIntegerValue(-9, 0))
        };
        op = ExpStdOp.create("mod", args);
        assertEquals(new UIntegerValue(0, -1.0), e.eval(op, state), op.toString());

        // UInteger(-2, 3)  mod UInteger(-2, 4) -> UInteger(0, 2.9154759474) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(-2, 3)),
                new ExpressionWithValue(new UIntegerValue(-2, 4))
        };
        op = ExpStdOp.create("mod", args);
        assertEquals(new UIntegerValue(0, 2.9154759474), e.eval(op, state), op.toString());

        // UInteger(-6, 2)  mod UInteger(5, 0) -> UInteger(4, 0.4) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(-6, 2)),
                new ExpressionWithValue(new UIntegerValue(5, 0))
        };
        op = ExpStdOp.create("mod", args);
        assertEquals(new UIntegerValue(-1, 0.4), e.eval(op, state), op.toString());

        // UInteger(-2, 3)  mod UInteger(2, 4) -> UInteger(0, 2.9154759474) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(-2, 3)),
                new ExpressionWithValue(new UIntegerValue(2, 4))
        };
        op = ExpStdOp.create("mod", args);
        assertEquals(new UIntegerValue(0, 2.9154759474), e.eval(op, state), op.toString());

        // UInteger(0, 0)  mod UInteger(0, 0) -> Undefined : OclVoid
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(0, 0)),
                new ExpressionWithValue(new UIntegerValue(0, 0))
        };
        op = ExpStdOp.create("mod", args);
        assertEquals(UndefinedValue.instance, e.eval(op, state), op.toString());

        // UInteger(0, 0)  mod UInteger(0, 4) -> Undefined : OclVoid
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(0, 0)),
                new ExpressionWithValue(new UIntegerValue(0, 4))
        };
        op = ExpStdOp.create("mod", args);
        assertEquals(UndefinedValue.instance, e.eval(op, state), op.toString());

        // UInteger(0, 0)  mod UInteger(6, 0) -> UInteger(0, 0.0) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(0, 0)),
                new ExpressionWithValue(new UIntegerValue(6, 0))
        };
        op = ExpStdOp.create("mod", args);
        assertEquals(new UIntegerValue(0, 0.0), e.eval(op, state), op.toString());

        // UInteger(0, 0)  mod UInteger(7, 3) -> UInteger(0, 0.0612244898) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(0, 0)),
                new ExpressionWithValue(new UIntegerValue(7, 3))
        };
        op = ExpStdOp.create("mod", args);
        assertEquals(new UIntegerValue(0, 0.0612244898), e.eval(op, state), op.toString());

        // UInteger(0, 4)  mod UInteger(0, 0) ->  Undefined : OclVoid
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(0, 4)),
                new ExpressionWithValue(new UIntegerValue(0, 0))
        };
        op = ExpStdOp.create("mod", args);
        assertEquals(UndefinedValue.instance, e.eval(op, state), op.toString());

        // UInteger(0, 4)  mod UInteger(0, 3) -> Undefined : OclVoid
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(0, 4)),
                new ExpressionWithValue(new UIntegerValue(0, 3))
        };
        op = ExpStdOp.create("mod", args);
        assertEquals(UndefinedValue.instance, e.eval(op, state), op.toString());

        // UInteger(0, 4)  mod UInteger(1, 0) -> UInteger(0, 4.0) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(0, 4)),
                new ExpressionWithValue(new UIntegerValue(1, 0))
        };
        op = ExpStdOp.create("mod", args);
        assertEquals(new UIntegerValue(0, 4.0), e.eval(op, state), op.toString());

        // UInteger(0, 4)  mod UInteger(2, 3) -> UInteger(0, 2.8284271247) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(0, 4)),
                new ExpressionWithValue(new UIntegerValue(2, 3))
        };
        op = ExpStdOp.create("mod", args);
        assertEquals(new UIntegerValue(0, 2.8284271247), e.eval(op, state), op.toString());

        // UInteger(9, 0)  mod UInteger(9, 0) -> UInteger(0, 0.0) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(9, 0)),
                new ExpressionWithValue(new UIntegerValue(9, 0))
        };
        op = ExpStdOp.create("mod", args);
        assertEquals(new UIntegerValue(0, 0.0), e.eval(op, state), op.toString());

        // UInteger(5, 0)  mod UInteger(5, 3) -> UInteger(0, 0.12) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(5, 0)),
                new ExpressionWithValue(new UIntegerValue(5, 3))
        };
        op = ExpStdOp.create("mod", args);
        assertEquals(new UIntegerValue(0, 0.12), e.eval(op, state), op.toString());

        // UInteger(4, 0)  mod UInteger(8, 0) -> UInteger(4, 0.0) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(4, 0)),
                new ExpressionWithValue(new UIntegerValue(8, 0))
        };
        op = ExpStdOp.create("mod", args);
        assertEquals(new UIntegerValue(4, 0.0), e.eval(op, state), op.toString());

        // UInteger(10, 0)  mod UInteger(10, 12) -> UInteger(0, 0.12) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(10, 0)),
                new ExpressionWithValue(new UIntegerValue(10, 12))
        };
        op = ExpStdOp.create("mod", args);
        assertEquals(new UIntegerValue(0, 0.12), e.eval(op, state), op.toString());

        // UInteger(9, 5)  mod UInteger(9, 0) -> UInteger(0, 0.5555555556) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(9, 5)),
                new ExpressionWithValue(new UIntegerValue(9, 0))
        };
        op = ExpStdOp.create("mod", args);
        assertEquals(new UIntegerValue(0, 0.5555555556), e.eval(op, state), op.toString());

        // UInteger(2, 3)  mod UInteger(2, 4) -> UInteger(0, 2.9154759474) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(2, 3)),
                new ExpressionWithValue(new UIntegerValue(2, 4))
        };
        op = ExpStdOp.create("mod", args);
        assertEquals(new UIntegerValue(0, 2.9154759474), e.eval(op, state), op.toString());

        // UInteger(6, 1)  mod UInteger(4, 0) -> UInteger(2, 0.25) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(6, 1)),
                new ExpressionWithValue(new UIntegerValue(4, 0))
        };
        op = ExpStdOp.create("mod", args);
        assertEquals(new UIntegerValue(2, 0.25), e.eval(op, state), op.toString());

        // UInteger(2, 3)  mod UInteger(5, 4) -> UInteger(2, 1.379275172) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(2, 3)),
                new ExpressionWithValue(new UIntegerValue(5, 4))
        };
        op = ExpStdOp.create("mod", args);
        assertEquals(new UIntegerValue(2, 1.379275172), e.eval(op, state), op.toString());
    }

    @Test
    void testModWithInteger() throws ExpInvalidException {
        Expression [] args;
        Expression op;

        // UInteger(-3, 0)  mod -3 -> UInteger(0, 0) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(-3, 0)),
                new ExpressionWithValue(IntegerValue.valueOf(-3))
        };
        op = ExpStdOp.create("mod", args);
        assertEquals(new UIntegerValue(0, 0), e.eval(op, state), op.toString());

        // UInteger(-6, 0)  mod -12 -> UInteger(6, 0) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(-6, 0)),
                new ExpressionWithValue(IntegerValue.valueOf(-12))
        };
        op = ExpStdOp.create("mod", args);
        assertEquals(new UIntegerValue(-6, 0), e.eval(op, state), op.toString());

        // UInteger(-5, 3)  mod -5 -> UInteger(0, -0.6) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(-5, 3)),
                new ExpressionWithValue(IntegerValue.valueOf(-5))
        };
        op = ExpStdOp.create("mod", args);
        assertEquals(new UIntegerValue(0, -0.6), e.eval(op, state), op.toString());

        // UInteger(-8, 5)  mod -2 -> UInteger(0, -2.5) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(-8, 5)),
                new ExpressionWithValue(IntegerValue.valueOf(-2))
        };
        op = ExpStdOp.create("mod", args);
        assertEquals(new UIntegerValue(0, -2.5), e.eval(op, state), op.toString());

        // UInteger(0, 0)  mod 0 -> Undefined : OclVoid
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(0, 0)),
                new ExpressionWithValue(IntegerValue.valueOf(0))
        };
        op = ExpStdOp.create("mod", args);
        assertEquals(UndefinedValue.instance, e.eval(op, state), op.toString());

        // UInteger(0, 0)  mod 3 -> UInteger(0, 0) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(0, 0)),
                new ExpressionWithValue(IntegerValue.valueOf(3))
        };
        op = ExpStdOp.create("mod", args);
        assertEquals(new UIntegerValue(0, 0), e.eval(op, state), op.toString());

        // UInteger(0, 3)  mod 0 -> Undefined : OclVoid
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(0, 3)),
                new ExpressionWithValue(IntegerValue.valueOf(0))
        };
        op = ExpStdOp.create("mod", args);
        assertEquals(UndefinedValue.instance, e.eval(op, state), op.toString());

        // UInteger(0, 5)  mod -5 -> UInteger(0, -1) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(0, 5)),
                new ExpressionWithValue(IntegerValue.valueOf(-5))
        };
        op = ExpStdOp.create("mod", args);
        assertEquals(new UIntegerValue(0, -1), e.eval(op, state), op.toString());

        // UInteger(5, 0)  mod 5 -> UInteger(0, 0) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(5, 0)),
                new ExpressionWithValue(IntegerValue.valueOf(5))
        };
        op = ExpStdOp.create("mod", args);
        assertEquals(new UIntegerValue(0, 0), e.eval(op, state), op.toString());

        // UInteger(3, 0)  mod 56 -> UInteger(3, 0) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(3, 0)),
                new ExpressionWithValue(IntegerValue.valueOf(56))
        };
        op = ExpStdOp.create("mod", args);
        assertEquals(new UIntegerValue(3, 0), e.eval(op, state), op.toString());

        // UInteger(7, 3)  mod 7 -> UInteger(0, 0.4285714286) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(7, 3)),
                new ExpressionWithValue(IntegerValue.valueOf(7))
        };
        op = ExpStdOp.create("mod", args);
        assertEquals(new UIntegerValue(0, 0.4285714286), e.eval(op, state), op.toString());

        // UInteger(2, 5)  mod 65 -> UInteger(2, 0.0769230769) : UInteger
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(2, 5)),
                new ExpressionWithValue(IntegerValue.valueOf(65))
        };
        op = ExpStdOp.create("mod", args);
        assertEquals(new UIntegerValue(2, 0.0769230769), e.eval(op, state), op.toString());
    }

    @Test
    void testPower() throws ExpInvalidException {
        Expression [] args;
        Expression op;

        // UInteger(0, 0).power(0) -> Undefined : OclVoid
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(0, 0)),
                new ExpressionWithValue(new RealValue(0))
        };
        op = ExpStdOp.create("power", args);
        assertEquals(UndefinedValue.instance, e.eval(op, state), op.toString());

        // UInteger(0, 0).power(3) -> UInteger(0.0, 0.0)
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(0, 0)),
                new ExpressionWithValue(new RealValue(3))
        };
        op = ExpStdOp.create("power", args);
        assertEquals(new UIntegerValue(0, 0.0), e.eval(op, state), op.toString());

        // UInteger(0, 0).power(-2) -> Undefined : OclVoid
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(0, 0)),
                new ExpressionWithValue(new RealValue(-2))
        };
        op = ExpStdOp.create("power", args);
        assertEquals(UndefinedValue.instance, e.eval(op, state), op.toString());

        // UInteger(0, 0).power(3.5) -> UInteger(0.0, 0.0)
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(0, 0)),
                new ExpressionWithValue(new RealValue(3.5))
        };
        op = ExpStdOp.create("power", args);
        assertEquals(new UIntegerValue(0, 0.0), e.eval(op, state), op.toString());

        // UInteger(0, 2).power(0) -> Undefined : OclVoid
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(0, 2)),
                new ExpressionWithValue(new RealValue(0))
        };
        op = ExpStdOp.create("power", args);
        assertEquals(UndefinedValue.instance, e.eval(op, state), op.toString());

        // UInteger(0, 4).power(3) -> UInteger(0.0, 0.0)
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(0, 4)),
                new ExpressionWithValue(new RealValue(3))
        };
        op = ExpStdOp.create("power", args);
        assertEquals(new UIntegerValue(0, 0.0), e.eval(op, state), op.toString());

        // UInteger(0, 3).power(-3) -> Undefined : OclVoid
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(0, 3)),
                new ExpressionWithValue(new RealValue(-3))
        };
        op = ExpStdOp.create("power", args);
        assertEquals(UndefinedValue.instance, e.eval(op, state), op.toString());

        // UInteger(0, 1).power(3.5) -> UInteger(0.0, 0.0)
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(0, 1)),
                new ExpressionWithValue(new RealValue(3.5))
        };
        op = ExpStdOp.create("power", args);
        assertEquals(new UIntegerValue(0, 0.0), e.eval(op, state), op.toString());

        // UInteger(3, 0).power(0) -> UInteger(1.0, 0.0)
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(3, 0)),
                new ExpressionWithValue(new RealValue(0))
        };
        op = ExpStdOp.create("power", args);
        assertEquals(new UIntegerValue(1, 0.0), e.eval(op, state), op.toString());

        // UInteger(2, 0).power(3) -> UInteger(8.0, 0.0)
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(2, 0)),
                new ExpressionWithValue(new RealValue(3))
        };
        op = ExpStdOp.create("power", args);
        assertEquals(new UIntegerValue(8, 0.0), e.eval(op, state), op.toString());

        // UInteger(4, 0).power(-2) -> UInteger(0, 0.0625)
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(4, 0)),
                new ExpressionWithValue(new RealValue(-2))
        };
        op = ExpStdOp.create("power", args);
        assertEquals(new UIntegerValue(0, 0.0625), e.eval(op, state), op.toString());

        // UInteger(4, 0).power(1.5) -> UInteger(8.0, 0.0)
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(4, 0)),
                new ExpressionWithValue(new RealValue(1.5))
        };
        op = ExpStdOp.create("power", args);
        assertEquals(new UIntegerValue(8, 0.0), e.eval(op, state), op.toString());

        // UInteger(1.5, 3.2).power(0) -> UInteger(1.0, 0.0)
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(2, 3.2)), // 1.5 -> 2
                new ExpressionWithValue(new RealValue(0))
        };
        op = ExpStdOp.create("power", args);
        assertEquals(new UIntegerValue(1, 0.0), e.eval(op, state), op.toString());

        // UInteger(2, 4).power(4) -> UInteger(16.0, 128.0)
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(2, 4)),
                new ExpressionWithValue(new RealValue(4))
        };
        op = ExpStdOp.create("power", args);
        assertEquals(new UIntegerValue(16, 128.0), e.eval(op, state), op.toString());

        // UInteger(1, 3).power(-2) -> UInteger(1.0, 6.0)
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(1, 3)),
                new ExpressionWithValue(new RealValue(-2))
        };
        op = ExpStdOp.create("power", args);
        assertEquals(new UIntegerValue(1, 6.0), e.eval(op, state), op.toString());

        // UInteger(1, 2).power(0.25) -> UInteger(1.0, 0.5)
        args = new Expression[] {
                new ExpressionWithValue(new UIntegerValue(1, 2)),
                new ExpressionWithValue(new RealValue(0.25))
        };
        op = ExpStdOp.create("power", args);
        assertEquals(new UIntegerValue(1, 0.5), e.eval(op, state), op.toString());

    }

}
