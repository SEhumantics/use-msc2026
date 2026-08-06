package org.tzi.use.uml.ocl.expr;

import static org.junit.jupiter.api.Assertions.*;
import java.io.PrintWriter;
import java.io.StringWriter;
import org.junit.jupiter.api.Test;
import org.tzi.use.parser.ocl.OCLCompiler;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;
import org.tzi.use.uml.ocl.value.*;
import org.tzi.use.uml.sys.MSystem;

class UncertaintyQueryEvaluationTest {
    private static final double EPS=1e-9;
    private final MModel model=new ModelFactory().createModel("UncertaintyEvaluation");
    private Value eval(String source) {
        Expression e=OCLCompiler.compileExpression(model,source,"<uncertainty-eval>",new PrintWriter(new StringWriter()),new VarBindings());
        assertNotNull(e,source);
        return new Evaluator().eval(e,new MSystem(model).state(),new VarBindings());
    }
    private Value eval(Expression expression) {
        return new Evaluator().eval(expression,new MSystem(model).state(),new VarBindings());
    }
    @Test void uncertainCollectionMembershipAndCountsEvaluate() {
        var includes=eval("Sequence{UString('a', 1), UString('b', 0.8)}->includes(UString('a', 1))");
        assertTrue(includes instanceof UBooleanValue); assertEquals(1,((UBooleanValue)includes).probability(),EPS);
        assertEquals(1,((IntegerValue)eval("Sequence{UString('a', 1), UString('b', 0.8)}->uCount(UString('a', 1))")).value());
        assertEquals(1,((IntegerValue)eval("Sequence{UString('a', 1), UString('b', 0.8)}->uCountC(UString('b', 1), 0.7)")).value());
        assertTrue(eval("Sequence{UString('a', 1)}->includesAll(Sequence{UString('a', 1)})") instanceof UBooleanValue);
        assertTrue(eval("Sequence{UString('a', 1)}->excludesAll(Sequence{UString('b', 1)})") instanceof UBooleanValue);
    }
    @Test void uncertainSelectExistsForAllEvaluateWithThresholds() {
        var selected=eval("Sequence{UBoolean(true, 0.9), UBoolean(false, 0.9)}->uSelect(x | x)");
        assertEquals(1,((CollectionValue)selected).size());
        assertEquals(1,((CollectionValue)eval("Sequence{UBoolean(true, 0.9), UBoolean(false, 0.9)}->uSelectC(x | x, 0.8)")).size());
        var exists=eval("Sequence{UBoolean(false, 0.8), UBoolean(true, 0.7)}->exists(x | x)");
        assertTrue(exists instanceof UBooleanValue); assertTrue(((UBooleanValue)exists).probability()>.5);
        var forall=eval("Sequence{UBoolean(true, 0.8), UBoolean(false, 0.7)}->forAll(x | x)");
        assertTrue(forall instanceof UBooleanValue); assertTrue(((UBooleanValue)forall).probability()<.5);
    }
    @Test void scalarAndSubjectiveOperationsEvaluateThroughLookup() throws Exception {
        Expression ur=new ExpConstUncertain(ExpConstUncertain.Kind.UREAL,new ExpConstReal(4),new ExpConstReal(0));
        assertEquals(2,((RealValue)eval(op("value",op("sqrt",ur)))).value(),EPS);
        Expression ui=new ExpConstUncertain(ExpConstUncertain.Kind.UINTEGER,new ExpConstInteger(9),new ExpConstReal(0));
        assertEquals(81,((IntegerValue)eval(op("toInteger",op("power",ui,new ExpConstInteger(2))))).value());
        Expression us=new ExpConstUncertain(ExpConstUncertain.Kind.USTRING,new ExpConstString("AB"),new ExpConstReal(1));
        assertEquals("ab",((StringValue)eval(op("value",op("toLowerCase",us)))).value());
        Expression ub=new ExpConstUncertain(ExpConstUncertain.Kind.UBOOLEAN,new ExpConstBoolean(true),new ExpConstReal(.9));
        assertTrue(((BooleanValue)eval(op("toBooleanC",ub,new ExpConstReal(.8)))).value());
        Expression sb=new ExpConstUncertain(ExpConstUncertain.Kind.SBOOLEAN,new ExpConstReal(.6),new ExpConstReal(.1),new ExpConstReal(.3),new ExpConstReal(.5));
        assertEquals(.6,((RealValue)eval(op("belief",sb))).value(),EPS);
        var fused=eval("SBoolean(0.6, 0.1, 0.3, 0.5)->averageBeliefFusion(Sequence{SBoolean(0.4, 0.2, 0.4, 0.5)})");
        assertTrue(fused instanceof SBooleanValue);
    }
    @Test void invalidUncertaintyValuesEvaluateUndefined() {
        assertTrue(eval("SBoolean(0.4, 0.4, 0.4, 0.5)").isUndefined());
        assertTrue(eval("UString('x', 1.2)").isUndefined());
    }
    private Expression op(String name,Expression... args) throws Exception { return ExpStdOp.create(name,args); }
}
