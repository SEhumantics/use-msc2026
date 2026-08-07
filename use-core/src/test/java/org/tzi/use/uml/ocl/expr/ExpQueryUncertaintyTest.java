package org.tzi.use.uml.ocl.expr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.tzi.use.uml.mm.ModelFactory;
import org.tzi.use.uml.ocl.type.TypeFactory;
import org.tzi.use.uml.ocl.value.*;
import org.tzi.use.uml.sys.MSystem;
import org.tzi.use.uml.sys.MSystemState;

class ExpQueryUncertaintyTest {

    private MSystemState state;
    private Evaluator e;

    /**
     * Set{UReal(2, 0.5), 1, 2.5, 3.2, UReal(3.5, 0.25)}
     */
    private Expression setA;
    /**
     * Sequence{UReal(52, 0.5), 3.2, 2, UReal(-53, 20), UReal(20, 5)}
     */
    private Expression seqB;

    /**
     * Sequence{UInteger(2, 2.3), 3, UInteger(1,0.5)}
     */
    private Expression seqC;

    /**
     * Sequence{UReal(2, 3), Undefined}
     */
    private Expression seqWithUndefined;

    @BeforeEach
    void setUp() throws Exception {
        state = new MSystem(new ModelFactory().createModel("Test")).state();
        e = new Evaluator();

        // Set{UReal(2, 0.5), 1, 2.5, 3.2, UReal(3.5, 0.25)}
        Value[] args1 = new Value[]{
                new URealValue(2, 0.5),
                IntegerValue.valueOf(1),
                new RealValue(2.5),
                new RealValue(3.2),
                new URealValue(3.5, 0.25)};
        setA = new ExpressionWithValue(new SetValue(TypeFactory.mkUReal(), args1));


        // Sequence{UReal(52, 0.5), 3.2, 2, UReal(-53, 20), UReal(20, 5)}
        Value [] args2 = new Value [] {
                new URealValue(52, 0.5),
                new RealValue(3.2),
                IntegerValue.valueOf(2),
                new URealValue(-53, 20),
                new URealValue(20, 5)
        };
        seqB = new ExpressionWithValue(new SequenceValue(TypeFactory.mkUReal(), args2));

        // Sequence{UInteger(2, 2.3), 3, UInteger(1,0.5)}
        Value [] args3 = new Value [] {
                new UIntegerValue(2, 2.3),
                IntegerValue.valueOf(3),
                new UIntegerValue(1, 0.5)
        };
        seqC = new ExpressionWithValue(new SequenceValue(TypeFactory.mkUInteger(), args3));

        // Sequence{UReal(2, 3), Undefined}
        Value [] args4 = new Value [] {
                new URealValue(2, 3),
                UndefinedValue.instance
        };
        seqWithUndefined = new ExpressionWithValue(new SequenceValue(TypeFactory.mkUReal(), args4));

    }

    // TESTING FOR ALL

    @Test

    public void testForAllColA() throws ExpInvalidException {
        VarDeclList elemVars = new VarDeclList(true);
        elemVars.add(new VarDecl("e1", TypeFactory.mkUReal()));
        Expression [] args = new Expression [] {
                new ExpVariable("e1", TypeFactory.mkUReal()),
                new ExpConstReal(0)
        };
        ExpStdOp op = ExpStdOp.create(">", args);
        Expression exp = new ExpForAll(elemVars, setA, op);
        assertEquals( UBooleanValue.probability(true, 0.999968314), e.eval(exp, state),exp.toString());
    }

    // TESTING EXISTS

    @Test

    public void testExistsA() throws ExpInvalidException{
        VarDeclList elemVars = new VarDeclList(true);
        elemVars.add(new VarDecl("e1", TypeFactory.mkUReal()));
        Expression [] args = new Expression [] {
                new ExpVariable("e1", TypeFactory.mkUReal()),
                new ExpConstReal(3.2)
        };
        ExpStdOp op = ExpStdOp.create(">=", args);
        Expression exp = new ExpExists(elemVars, setA, op);
        assertEquals( UBooleanValue.TRUE, e.eval(exp, state),exp.toString());
    }


    // TESTING USELECT

    @Test

    public void testUSelectColA() throws ExpInvalidException {
        VarDecl varDecl = new VarDecl("e1", TypeFactory.mkUReal());
        Expression [] args = new Expression[]{
                new ExpVariable("e1", TypeFactory.mkUReal()),
                new ExpConstReal(2)
        };
        ExpStdOp op = ExpStdOp.create(">=", args);
        Expression expUSelect = new ExpUSelect(varDecl, setA, op);
        // Collection expected
        Value [] expectedValues = new Value[]{
                new RealValue(2.5),
                new RealValue(3.2),
                new URealValue(3.5, 0.25)
        };
        // Assert
        assertEquals(
                new SetValue(TypeFactory.mkUReal(), expectedValues),
                e.eval(expUSelect, state),op.toString());
    }

    @Test

    public void testUSelectColANoMatches() throws ExpInvalidException {
        VarDecl varDecl = new VarDecl("e1", TypeFactory.mkUReal());
        Expression [] args = new Expression[]{
                new ExpVariable("e1", TypeFactory.mkUReal()),
                new ExpConstReal(50)
        };
        ExpStdOp op = ExpStdOp.create(">=", args);
        Expression expUSelect = new ExpUSelect(varDecl, setA, op);
        // Collection expected
        Value [] expectedValues = new Value[]{ /*empty*/ };
        // Assert
        assertEquals(
                new SetValue(TypeFactory.mkUReal(), expectedValues),
                e.eval(expUSelect, state),op.toString());
    }

    // TESTING USELECTC

    @Test

    public void testUSelectCUncertaintyErrorType() throws ExpInvalidException {
        VarDecl varDecl = new VarDecl("e1", TypeFactory.mkUReal());
        Expression [] args = new Expression[]{
                new ExpVariable("e1", TypeFactory.mkUReal()),
                new ExpConstReal(2)
        };
        ExpStdOp op = ExpStdOp.create(">=", args);
        // Assert
        try {
            new ExpUSelectC(varDecl, setA, op, new ExpConstString("testing"));
            fail("ExpInvalidException expected!");
        }
        catch (ExpInvalidException exp) {
            // Success
        }
        catch (Exception ex) {
            fail("ExpInvalidException expected, found : " + ex.getClass().getName());
        }
    }

    @Test

    public void testUSelectCUncertaintyHigherThanOne() throws ExpInvalidException {
        VarDecl varDecl = new VarDecl("e1", TypeFactory.mkUReal());
        Expression [] args = new Expression[]{
                new ExpVariable("e1", TypeFactory.mkUReal()),
                new ExpConstReal(2)
        };
        ExpStdOp op = ExpStdOp.create(">=", args);
        // Assert
        try {
            new ExpUSelectC(varDecl, setA, op, new ExpConstReal(2));
            e.eval(op, state);
            fail("RuntimeException expected!");
        }
        catch (RuntimeException exp) {
            // Success
        }
        catch (Exception ex) {
            fail("RuntimeException expected, found : " + ex.getClass().getName());
        }
    }

    @Test

    public void testUSelectCUncertaintyLowerThanZero() throws ExpInvalidException {
        VarDecl varDecl = new VarDecl("e1", TypeFactory.mkUReal());
        Expression [] args = new Expression[]{
                new ExpVariable("e1", TypeFactory.mkUReal()),
                new ExpConstReal(2)
        };
        ExpStdOp op = ExpStdOp.create(">=", args);
        // Assert
        try {
            new ExpUSelectC(varDecl, setA, op, new ExpConstReal(-2));
            e.eval(op, state);
            fail("RuntimeException expected!");
        }
        catch (RuntimeException exp) {
            // Success
        }
        catch (Exception ex) {
            fail("RuntimeException expected, found : " + ex.getClass().getName());
        }
    }

    @Test

    public void testUSelectCColA() throws ExpInvalidException {
        VarDecl varDecl = new VarDecl("e1", TypeFactory.mkUReal());
        Expression [] args = new Expression[]{
                new ExpVariable("e1", TypeFactory.mkUReal()),
                new ExpConstReal(2)
        };
        ExpStdOp op = ExpStdOp.create(">=", args);
        Expression expUSelect = new ExpUSelectC(varDecl, setA, op, new ExpConstReal(0.8));
        // Collection expected
        Value [] expectedValues = new Value[]{
                new RealValue(2.5),
                new RealValue(3.2),
                new URealValue(3.5, 0.25)
        };
        // Assert
        assertEquals(
                new SetValue(TypeFactory.mkUReal(), expectedValues),
                e.eval(expUSelect, state),op.toString());
    }


    // TESTING SUM

    @Test

    public void testSum() throws ExpInvalidException  {
        ExpStdOp op = ExpStdOp.create("sum", new Expression [] {setA});
        assertEquals( new URealValue(12.2, 0.5590169944), e.eval(op, state),op.toString());
    }

    @Test

    public void testSumSeqB() throws ExpInvalidException {
        ExpStdOp op = ExpStdOp.create("sum", new Expression[] {seqB});
        assertEquals( new URealValue(24.2, 20.6215906273), e.eval(op, state),op.toString());
    }

    @Test

    public void testSumSeqC() throws ExpInvalidException {
        ExpStdOp op = ExpStdOp.create("sum", new Expression[] {seqC});
        assertEquals( new UIntegerValue(6, 2.3537204592), e.eval(op, state),op.toString());
    }

    @Test

    public void testSumSeqWithUndefined() throws ExpInvalidException {
        ExpStdOp op = ExpStdOp.create("sum", new Expression[]{seqWithUndefined});
        assertEquals( UndefinedValue.instance, e.eval(op, state),op.toString());
    }
}
