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
    /**
     * The historical corpus states these collection identities through
     * {@code .equals(...)} on a collection receiver, which the compiler expands
     * to a collect shorthand and so cannot yield a single Boolean. The
     * identities themselves are checked here with {@code =} instead.
     */
    @Test void uncertainCollectionIdentitiesFromTheHistoricalCorpusHold() {
        assertTrue(((BooleanValue)eval(
            "let A = Set{2, 3, UReal(3, 0.5)} in "
            + "(A->iterate(v; acc : Set(UReal) = Set{} | "
            + "if (v > 2).toBoolean() then acc->including(v) else acc endif)) = A->uSelect(e | e > 2)")).value());
        assertTrue(((BooleanValue)eval(
            "let A = Set{UReal(2, 0.5), 2.5, 3.2, 1, UReal(3, 0.25)} in let C = 0.7 in "
            + "(A->iterate(v; acc : Set(UReal) = Set{} | "
            + "if (v >= 2).toBooleanC(C) then acc->including(v) else acc endif)) = A->uSelectC(e | e >= 2, C)")).value());
        var includesAll=eval(
            "let A = Set{UReal(2, 0.5), 1, 2.5, 3.2, UReal(3.5, 0.25)} in let B = Set{UReal(2, 0.5), 1, 3.2} in "
            + "(B->forAll(e | A->includes(e))) = A->includesAll(B)");
        assertTrue(((UBooleanValue)includesAll).toBoolean().value());
        var excludes=eval(
            "let A = Set{UReal(2, 0.5), 1, 2.5, 3.2, UReal(3.5, 0.25)} in let B = UReal(59, 2) in "
            + "(A->forAll(e | e <> B)) = A->excludes(B)");
        assertTrue(((UBooleanValue)excludes).toBoolean().value());
    }
    @Test void uncertainCollectionSumPreservesUncertainResultType() {
        var integerSum=eval("Sequence{UInteger(2, 0.1), UInteger(3, 0.2)}->sum()");
        assertTrue(integerSum instanceof UIntegerValue);
        assertEquals(5,((UIntegerValue)integerSum).value());
        assertEquals(Math.hypot(.1,.2),((UIntegerValue)integerSum).uncertainty(),EPS);
        var realSum=eval("Sequence{UReal(2.0, 0.1), UReal(3.0, 0.2)}->sum()");
        assertTrue(realSum instanceof URealValue);
        assertEquals(5,((URealValue)realSum).value(),EPS);
        assertEquals(Math.hypot(.1,.2),((URealValue)realSum).uncertainty(),EPS);
    }
    @Test void uncertainSelectExistsForAllEvaluateWithThresholds() {
        var selected=eval("Sequence{UBoolean(true, 0.9), UBoolean(false, 0.9)}->uSelect(x | x)");
        assertEquals(1,((CollectionValue)selected).size());
        // select, reject, any and one keep requiring a certain Boolean, as they
        // do historically; only exists, forAll and the uSelect family take an
        // uncertain predicate
        assertNull(OCLCompiler.compileExpression(model,
            "Sequence{UBoolean(true, 0.9)}->select(x | x)", "<uncertainty-eval>",
            new PrintWriter(new StringWriter()), new VarBindings()));
        assertEquals(1,((CollectionValue)eval("Sequence{UBoolean(true, 0.9), UBoolean(false, 0.9)}->uSelectC(x | x, 0.8)")).size());
        var exists=eval("Sequence{UBoolean(false, 0.8), UBoolean(true, 0.7)}->exists(x | x)");
        assertTrue(exists instanceof UBooleanValue); assertTrue(((UBooleanValue)exists).probability()>.5);
        var forall=eval("Sequence{UBoolean(true, 0.8), UBoolean(false, 0.7)}->forAll(x | x)");
        assertTrue(forall instanceof UBooleanValue); assertTrue(((UBooleanValue)forall).probability()<.5);
    }
    @Test void scalarAndSubjectiveOperationsEvaluateThroughLookup() throws Exception {
        Expression ur=new ExpConstUncertain(ExpConstUncertain.Kind.UREAL,new ExpConstReal(4),new ExpConstReal(0));
        assertEquals(2,((RealValue)eval(op("value",op("sqrt",ur)))).value(),EPS);
        assertEquals(4,((URealValue)eval(op("floor",ur))).value(),EPS);
        assertEquals(4,((URealValue)eval(op("round",ur))).value(),EPS);
        assertEquals(0,((URealValue)eval(op("min",ur,new ExpConstInteger(0)))).value(),EPS);
        assertEquals(4,((URealValue)eval(op("max",ur,new ExpConstInteger(0)))).value(),EPS);
        Expression ui=new ExpConstUncertain(ExpConstUncertain.Kind.UINTEGER,new ExpConstInteger(9),new ExpConstReal(0));
        assertEquals(81,((IntegerValue)eval(op("toInteger",op("power",ui,new ExpConstInteger(2))))).value());
        assertEquals(9,((IntegerValue)eval(op("floor",ui))).value());
        var uiSum=(UIntegerValue)eval(op("+",ui,new ExpConstInteger(2)));
        assertEquals(11,uiSum.value());
        assertEquals(4.5,((URealValue)eval(op("/",ui,new ExpConstInteger(2)))).value(),EPS);
        assertEquals(4,((UIntegerValue)eval(op("div",ui,new ExpConstInteger(2)))).value());
        assertEquals(1,((UIntegerValue)eval(op("mod",ui,new ExpConstInteger(2)))).value());
        assertEquals(-9,((UIntegerValue)eval(op("-",ui))).value());
        assertEquals(2,((UIntegerValue)eval(op("min",ui,new ExpConstInteger(2)))).value());
        Expression us=new ExpConstUncertain(ExpConstUncertain.Kind.USTRING,new ExpConstString("AB"),new ExpConstReal(1));
        assertTrue(eval(op("at",us,new ExpConstInteger(1))) instanceof UStringValue);
        assertEquals("ab",((StringValue)eval(op("value",op("toLowerCase",us)))).value());
        assertEquals("AB!",((UStringValue)eval(op("+",us,new ExpConstString("!")))).value());
        assertTrue(((UBooleanValue)eval(op("<",us,new ExpConstString("Z")))).value());
        Expression ub=new ExpConstUncertain(ExpConstUncertain.Kind.UBOOLEAN,new ExpConstBoolean(true),new ExpConstReal(.9));
        assertTrue(((BooleanValue)eval(op("toBooleanC",ub,new ExpConstReal(.8)))).value());
        Expression ub2=new ExpConstUncertain(ExpConstUncertain.Kind.UBOOLEAN,new ExpConstBoolean(true),new ExpConstReal(.8));
        assertTrue(((BooleanValue)eval(op("equalsC",ub,ub2,new ExpConstReal(.8)))).value());
        assertTrue(eval(op("equalsC",ub,ub2,new ExpConstReal(1.1))).isUndefined());
        Expression sb=new ExpConstUncertain(ExpConstUncertain.Kind.SBOOLEAN,new ExpConstReal(.6),new ExpConstReal(.1),new ExpConstReal(.3),new ExpConstReal(.5));
        assertEquals(.6,((RealValue)eval(op("belief",sb))).value(),EPS);
        var mixed=(SBooleanValue)eval(op("and",sb,ub2));
        assertEquals(.4,mixed.baseRate(),EPS);
        var fused=eval("SBoolean(0.6, 0.1, 0.3, 0.5)->averageBeliefFusion(Sequence{SBoolean(0.4, 0.2, 0.4, 0.5)})");
        assertTrue(fused instanceof SBooleanValue);
    }
    /**
     * The historical toString operations do not simply render the value: on a
     * UString it yields the underlying string, and on a UBoolean it reports the
     * more likely side rather than the canonical one.
     */
    @Test void toStringOperationsFollowTheHistoricalRendering() {
        assertEquals("Hola",((StringValue)eval("UString('Hola', 0.8).toString()")).value());
        assertEquals("UBoolean(true, 0.8)",((StringValue)eval("UBoolean(true, 0.8).toString()")).value());
        assertEquals("UBoolean(false, 0.8)",((StringValue)eval("UBoolean(false, 0.8).toString()")).value());
        // The value itself still renders canonically.
        assertEquals("UBoolean(true, 0.2)",eval("UBoolean(false, 0.8)").toString());
    }
    @Test void invalidUncertaintyValuesEvaluateUndefined() {
        assertTrue(eval("SBoolean(0.4, 0.4, 0.4, 0.5)").isUndefined());
        assertTrue(eval("UString('x', 1.2)").isUndefined());
        assertEquals(.5, ((URealValue)eval("UReal(2, -0.5)")).uncertainty(), EPS);
        assertEquals(5, ((UIntegerValue)eval("UInteger(2, -5)")).uncertainty(), EPS);
    }
    private Expression op(String name,Expression... args) throws Exception { return ExpStdOp.create(name,args); }
}
