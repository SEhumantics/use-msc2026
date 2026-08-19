package org.tzi.use.parser.ocl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.PrintWriter;
import java.io.StringWriter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;
import org.tzi.use.uml.ocl.expr.Evaluator;
import org.tzi.use.uml.ocl.expr.Expression;
import org.tzi.use.uml.ocl.value.VarBindings;
import org.tzi.use.uml.sys.MSystem;

/**
 * End-to-end typing of uncertain OCL expressions: text in, {@code Type} out, through the real
 * grammar, the real AST classes and the real operation registry.
 *
 * <p>This is the narrowest statement of what the port is for. The fork's worked example is
 * {@code Set{UReal(2,0.5), 1, 2.5} : Set(UReal)} — measured on both sides in
 * {@code adaptation-policy-refutation.md} — and in plain USE 7.5.0 the same text is a compile error,
 * {@code Undefined operation 'UReal'}. Every crisp control here must keep its 7.5.0 answer, because
 * an uncertainty port that changes crisp typing has broken the language rather than extended it.
 */
public class UncertainExpressionTypingTest {

    /** Compiles {@code expr} against an empty model and returns its type, or null if it failed. */
    private static String typeOf(String expr) {
        StringWriter err = new StringWriter();
        Expression e = OCLCompiler.compileExpression(
                new ModelFactory().createModel("m"), expr, "test",
                new PrintWriter(err), new VarBindings());
        return e == null ? null : e.type().toString();
    }

    @Test
    @DisplayName("the fork's worked example: a mixed collection literal takes the uncertain type")
    void mixedCollectionLiteralTakesTheUncertainElementType() {
        assertEquals("Set(UReal)", typeOf("Set{UReal(2,0.5), 1, 2.5}"));
        assertEquals("Sequence(UReal)", typeOf("Sequence{UReal(1,0.1), 2}"));
    }

    @Test
    @DisplayName("each uncertain literal parses and types")
    void uncertainLiteralsType() {
        assertEquals("UReal", typeOf("UReal(2,0.5)"));
        assertEquals("UInteger", typeOf("UInteger(4,0.25)"));
        assertEquals("UBoolean", typeOf("UBoolean(true, 0.8)"));
        assertEquals("UString", typeOf("UString('abc', 0.9)"));
        assertEquals("SBoolean", typeOf("SBoolean(0.3,0.2,0.5,0.5)"));
    }

    /**
     * Mixed arithmetic must PROPAGATE the uncertainty. Before the StandardOperationsNumber edit this
     * expression typed as plain {@code Real} — it compiled, it evaluated, and it silently discarded
     * the uncertainty. A wrong answer that does not fail is the worst outcome available, so it is
     * pinned here explicitly.
     */
    @Test
    @DisplayName("mixed uncertain/crisp arithmetic propagates the uncertainty, it does not drop it")
    void mixedArithmeticPropagatesUncertainty() {
        assertEquals("UReal", typeOf("UReal(2,0.5) + 3"));
        assertEquals("UReal", typeOf("3 + UReal(2,0.5)"));
        assertEquals("UReal", typeOf("UReal(2,0.5) - 1.5"));
        assertEquals("UReal", typeOf("UReal(2,0.5) * 2"));
    }

    /** Crisp typing must be exactly what 7.5.0 answered. */
    @Test
    @DisplayName("crisp expressions keep their 7.5.0 types")
    void crispTypingIsUnchanged() {
        assertEquals("Set(Real)", typeOf("Set{1, 2.5}"));
        assertEquals("Set(Integer)", typeOf("Set{1, 2}"));
        assertEquals("Integer", typeOf("1 + 2"));
        assertEquals("Real", typeOf("1 + 2.5"));
        assertEquals("Real", typeOf("2.5 * 2.0"));
        assertEquals("Boolean", typeOf("true and false"));
        assertEquals("String", typeOf("'a'.concat('b')"));
        assertNotNull(typeOf("Sequence{1..9}"));
    }

    /** A type name that does not exist must still be an error, not a silent identifier. */
    @Test
    @DisplayName("an unknown operation is still an error")
    void unknownOperationsStillFail() {
        assertNull(typeOf("UReal(2,0.5)->noSuchOperation()"));
    }

    /** Compiles and EVALUATES {@code expr}, returning USE's own "value : Type" rendering. */
    private static String evalOf(String expr) throws Exception {
        MModel model = new ModelFactory().createModel("m");
        MSystem sys = new MSystem(model);
        StringWriter err = new StringWriter();
        Expression e = OCLCompiler.compileExpression(
                model, expr, "test", new PrintWriter(err), new VarBindings());
        assertNotNull(e, "did not compile: " + expr + " -- " + err);
        return new Evaluator().eval(e, sys.state()).toStringWithType();
    }

    /**
     * The worked example, evaluated rather than merely typed.
     *
     * <p>A type is half the claim. These are the exact strings measured on BOTH sides in
     * {@code adaptation-policy-refutation.md}, so this is the narrowest end-to-end statement that
     * the port reproduces the fork: the same value, printed the same way, from the same source text.
     */
    @Test
    @DisplayName("the worked example evaluates to the fork's measured VALUE, not just its type")
    void workedExampleEvaluatesToTheForkResult() throws Exception {
        assertEquals("Set{1,2.5,UReal(2.0, 0.5)} : Set(UReal)",
                evalOf("Set{UReal(2,0.5), 1, 2.5}"));
        assertEquals("UReal(5.5, 0.5) : UReal",
                evalOf("Set{UReal(2,0.5), 1, 2.5}->sum()"));
    }

    @Test
    @DisplayName("uncertainty propagates through arithmetic with the right magnitude")
    void uncertaintyPropagatesWithTheRightMagnitude() throws Exception {
        // adding an exact quantity shifts the value and leaves the uncertainty alone
        assertEquals("UReal(5.0, 0.5) : UReal", evalOf("UReal(2,0.5) + 3"));
        // scaling by an exact factor scales the uncertainty by the same factor
        assertEquals("UReal(4.0, 1.0) : UReal", evalOf("UReal(2,0.5) * 2"));
        assertEquals("UInteger(5, 0.25) : UInteger", evalOf("UInteger(4,0.25) + 1"));
    }

    @Test
    @DisplayName("crisp evaluation is unchanged")
    void crispEvaluationIsUnchanged() throws Exception {
        assertEquals("3.5 : Real", evalOf("Set{1, 2.5}->sum()"));
        assertEquals("3 : Integer", evalOf("Set{1, 2}->sum()"));
        assertEquals("3 : Integer", evalOf("1 + 2"));
    }
}
