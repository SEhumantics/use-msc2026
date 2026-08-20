package org.tzi.use.uml.ocl.expr;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.tzi.use.uml.mm.ModelFactory;
import org.tzi.use.uml.ocl.type.TypeFactory;
import org.tzi.use.uml.ocl.value.*;
import org.tzi.use.uml.sys.MSystem;
import org.tzi.use.uml.sys.MSystemState;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Ported from USE-Uncertainty (github.com/atenearesearchgroup/uncertainty @ 74acd0d),
 * src/test/org/tzi/use/uml/ocl/expr/ExpQueryUncertaintyTest.java. Closes B7 ledger row M-44 for
 * this file (40 sites total across four files; the other three are
 * URealExpOpsTest/UIntegerExpOpsTest/UBooleanExpOpsTest).
 *
 * <p>The fork's three uncertainty-error tests used the JUnit-3
 * {@code try { ...; fail(...); } catch (X) {} catch (Exception ex) { fail(...); }} idiom, converted
 * here to {@code assertThrows} per M-44's own prescribed fix, with the exception message asserted at
 * each site to compensate for {@code assertThrows} accepting any subclass rather than exactly the
 * declared type.
 *
 * <p>{@code testUSelectCUncertaintyHigherThanOne}/{@code testUSelectCUncertaintyLowerThanZero}
 * evaluate {@code op} (the bare {@code e1 >= 2} comparison, with {@code e1} unbound outside a
 * quantifier), not the constructed {@code ExpUSelectC} — the fork's own two-statement
 * {@code try} block. That is very likely a copy-paste slip in the fork (evaluating the
 * out-of-context comparison instead of the {@code ExpUSelectC} expression whose confidence bound is
 * actually under test), but it is reproduced here rather than silently corrected: evaluating the bare
 * {@code op} still throws — an unbound {@code ExpVariable} throws {@code RuntimeException} — so the
 * fork's own two broad catch clauses (`catch (RuntimeException)` before `catch (Exception)`) pass
 * regardless of which expression is evaluated, and the message asserted below documents exactly what
 * actually throws today rather than what the test's name implies it verifies. See the ledger note at
 * this file's port site for the follow-up this leaves open.
 */
class ExpQueryUncertaintyTest {

    private MSystemState state;
    private Evaluator e;

    /** Set{UReal(2, 0.5), 1, 2.5, 3.2, UReal(3.5, 0.25)} */
    private Expression setA;
    /** Sequence{UReal(52, 0.5), 3.2, 2, UReal(-53, 20), UReal(20, 5)} */
    private Expression seqB;
    /** Sequence{UInteger(2, 2.3), 3, UInteger(1,0.5)} */
    private Expression seqC;
    /** Sequence{UReal(2, 3), Undefined} */
    private Expression seqWithUndefined;

    @BeforeEach
    void setUp() {
        state = new MSystem(new ModelFactory().createModel("Test")).state();
        e = new Evaluator();

        Value[] args1 = new Value[]{
                new URealValue(2, 0.5),
                IntegerValue.valueOf(1),
                new RealValue(2.5),
                new RealValue(3.2),
                new URealValue(3.5, 0.25)};
        setA = new ExpressionWithValue(new SetValue(TypeFactory.mkUReal(), args1));

        Value[] args2 = new Value[]{
                new URealValue(52, 0.5),
                new RealValue(3.2),
                IntegerValue.valueOf(2),
                new URealValue(-53, 20),
                new URealValue(20, 5)
        };
        seqB = new ExpressionWithValue(new SequenceValue(TypeFactory.mkUReal(), args2));

        Value[] args3 = new Value[]{
                new UIntegerValue(2, 2.3),
                IntegerValue.valueOf(3),
                new UIntegerValue(1, 0.5)
        };
        seqC = new ExpressionWithValue(new SequenceValue(TypeFactory.mkUInteger(), args3));

        Value[] args4 = new Value[]{
                new URealValue(2, 3),
                UndefinedValue.instance
        };
        seqWithUndefined = new ExpressionWithValue(new SequenceValue(TypeFactory.mkUReal(), args4));
    }

    // TESTING FOR ALL

    @Test
    void testForAllColA() throws ExpInvalidException {
        VarDeclList elemVars = new VarDeclList(true);
        elemVars.add(new VarDecl("e1", TypeFactory.mkUReal()));
        Expression[] args = new Expression[]{
                new ExpVariable("e1", TypeFactory.mkUReal()),
                new ExpConstReal(0)
        };
        ExpStdOp op = ExpStdOp.create(">", args);
        Expression exp = new ExpForAll(elemVars, setA, op);
        assertEquals(UBooleanValue.valueOf(true, 0.999968314), e.eval(exp, state), exp.toString());
    }

    // TESTING EXISTS

    @Test
    void testExistsA() throws ExpInvalidException {
        VarDeclList elemVars = new VarDeclList(true);
        elemVars.add(new VarDecl("e1", TypeFactory.mkUReal()));
        Expression[] args = new Expression[]{
                new ExpVariable("e1", TypeFactory.mkUReal()),
                new ExpConstReal(3.2)
        };
        ExpStdOp op = ExpStdOp.create(">=", args);
        Expression exp = new ExpExists(elemVars, setA, op);
        assertEquals(UBooleanValue.TRUE, e.eval(exp, state), exp.toString());
    }

    // TESTING USELECT

    @Test
    void testUSelectColA() throws ExpInvalidException {
        VarDecl varDecl = new VarDecl("e1", TypeFactory.mkUReal());
        Expression[] args = new Expression[]{
                new ExpVariable("e1", TypeFactory.mkUReal()),
                new ExpConstReal(2)
        };
        ExpStdOp op = ExpStdOp.create(">=", args);
        Expression expUSelect = new ExpUSelect(varDecl, setA, op);
        Value[] expectedValues = new Value[]{
                new RealValue(2.5),
                new RealValue(3.2),
                new URealValue(3.5, 0.25)
        };
        assertEquals(new SetValue(TypeFactory.mkUReal(), expectedValues), e.eval(expUSelect, state), op.toString());
    }

    @Test
    void testUSelectColANoMatches() throws ExpInvalidException {
        VarDecl varDecl = new VarDecl("e1", TypeFactory.mkUReal());
        Expression[] args = new Expression[]{
                new ExpVariable("e1", TypeFactory.mkUReal()),
                new ExpConstReal(50)
        };
        ExpStdOp op = ExpStdOp.create(">=", args);
        Expression expUSelect = new ExpUSelect(varDecl, setA, op);
        Value[] expectedValues = new Value[]{ /*empty*/ };
        assertEquals(new SetValue(TypeFactory.mkUReal(), expectedValues), e.eval(expUSelect, state), op.toString());
    }

    // TESTING USELECTC

    @Test
    void testUSelectCUncertaintyErrorType() throws ExpInvalidException {
        VarDecl varDecl = new VarDecl("e1", TypeFactory.mkUReal());
        Expression[] args = new Expression[]{
                new ExpVariable("e1", TypeFactory.mkUReal()),
                new ExpConstReal(2)
        };
        ExpStdOp op = ExpStdOp.create(">=", args);
        ExpInvalidException ex = assertThrows(ExpInvalidException.class,
                () -> new ExpUSelectC(varDecl, setA, op, new ExpConstString("testing")));
        assertEquals("Type of confidence must be Real, found type 'String' in expression ''testing''",
                ex.getMessage());
    }

    @Test
    void testUSelectCUncertaintyHigherThanOne() throws ExpInvalidException {
        VarDecl varDecl = new VarDecl("e1", TypeFactory.mkUReal());
        Expression[] args = new Expression[]{
                new ExpVariable("e1", TypeFactory.mkUReal()),
                new ExpConstReal(2)
        };
        ExpStdOp op = ExpStdOp.create(">=", args);
        RuntimeException ex = assertThrows(RuntimeException.class, () -> {
            new ExpUSelectC(varDecl, setA, op, new ExpConstReal(2));
            e.eval(op, state);
        });
        assertEquals("unbound variable `e1'.", ex.getMessage());
    }

    @Test
    void testUSelectCUncertaintyLowerThanZero() throws ExpInvalidException {
        VarDecl varDecl = new VarDecl("e1", TypeFactory.mkUReal());
        Expression[] args = new Expression[]{
                new ExpVariable("e1", TypeFactory.mkUReal()),
                new ExpConstReal(2)
        };
        ExpStdOp op = ExpStdOp.create(">=", args);
        RuntimeException ex = assertThrows(RuntimeException.class, () -> {
            new ExpUSelectC(varDecl, setA, op, new ExpConstReal(-2));
            e.eval(op, state);
        });
        assertEquals("unbound variable `e1'.", ex.getMessage());
    }

    @Test
    void testUSelectCColA() throws ExpInvalidException {
        VarDecl varDecl = new VarDecl("e1", TypeFactory.mkUReal());
        Expression[] args = new Expression[]{
                new ExpVariable("e1", TypeFactory.mkUReal()),
                new ExpConstReal(2)
        };
        ExpStdOp op = ExpStdOp.create(">=", args);
        Expression expUSelect = new ExpUSelectC(varDecl, setA, op, new ExpConstReal(0.8));
        Value[] expectedValues = new Value[]{
                new RealValue(2.5),
                new RealValue(3.2),
                new URealValue(3.5, 0.25)
        };
        assertEquals(new SetValue(TypeFactory.mkUReal(), expectedValues), e.eval(expUSelect, state), op.toString());
    }

    // TESTING SUM

    @Test
    void testSum() throws ExpInvalidException {
        ExpStdOp op = ExpStdOp.create("sum", new Expression[]{setA});
        assertEquals(new URealValue(12.2, 0.5590169944), e.eval(op, state), op.toString());
    }

    @Test
    void testSumSeqB() throws ExpInvalidException {
        ExpStdOp op = ExpStdOp.create("sum", new Expression[]{seqB});
        assertEquals(new URealValue(24.2, 20.6215906273), e.eval(op, state), op.toString());
    }

    @Test
    void testSumSeqC() throws ExpInvalidException {
        ExpStdOp op = ExpStdOp.create("sum", new Expression[]{seqC});
        assertEquals(new UIntegerValue(6, 2.3537204592), e.eval(op, state), op.toString());
    }

    @Test
    void testSumSeqWithUndefined() throws ExpInvalidException {
        ExpStdOp op = ExpStdOp.create("sum", new Expression[]{seqWithUndefined});
        assertEquals(UndefinedValue.instance, e.eval(op, state), op.toString());
    }
}
