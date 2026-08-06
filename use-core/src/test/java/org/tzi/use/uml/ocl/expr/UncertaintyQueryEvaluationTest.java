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
}
