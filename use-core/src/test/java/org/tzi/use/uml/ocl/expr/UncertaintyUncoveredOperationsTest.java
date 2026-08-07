package org.tzi.use.uml.ocl.expr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.PrintWriter;
import java.io.StringWriter;

import org.junit.jupiter.api.Test;
import org.tzi.use.parser.ocl.OCLCompiler;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;
import org.tzi.use.uml.ocl.value.BooleanValue;
import org.tzi.use.uml.ocl.value.RealValue;
import org.tzi.use.uml.ocl.value.UBooleanValue;
import org.tzi.use.uml.ocl.value.UStringValue;
import org.tzi.use.uml.ocl.value.Value;
import org.tzi.use.uml.ocl.value.VarBindings;
import org.tzi.use.uml.sys.MSystem;

/**
 * Historical expectations for the operations the replayed compiler corpus never
 * reaches. {@code equalsC}, {@code setConfidence} and {@code implies} do not
 * appear in it at all, and {@code toBooleanC} barely does, so these come from
 * the historical {@code UBooleanExpOpsTest} and {@code StandardOperationsUString}
 * instead.
 */
class UncertaintyUncoveredOperationsTest {

    private static final double EPS = 1e-9;
    private final MModel model = new ModelFactory().createModel("UncoveredOperations");

    private Value eval(String source) {
        StringWriter err = new StringWriter();
        Expression e = OCLCompiler.compileExpression(model, source, "<uncovered>",
                new PrintWriter(err), new VarBindings());
        assertNotNull(e, source + "\n" + err);
        return new Evaluator().eval(e, new MSystem(model).state(), new VarBindings());
    }

    private double probability(String source) {
        Value v = eval(source);
        assertTrue(v instanceof UBooleanValue, source + " yields " + v);
        return ((UBooleanValue) v).probability();
    }

    @Test void impliesFollowsTheHistoricalProbability() {
        assertEquals(0.9, probability("UBoolean(false, 0.5) implies UBoolean(false, 0.2)"), EPS);
        assertEquals(0.68, probability("UBoolean(true, 0.4) implies UBoolean(false, 0.8)"), EPS);
        assertEquals(0.8713, probability("UBoolean(true, 0.39) implies UBoolean(true, 0.67)"), EPS);
        // two certain Booleans stay certain
        assertEquals(BooleanValue.FALSE, eval("true implies false"));
    }

    @Test void setConfidenceReplacesTheConfidenceInCanonicalForm() {
        assertEquals(0.5, probability("UBoolean(true, 0.5).setConfidence(0.5)"), EPS);
        assertEquals(0.2, probability("UBoolean(true, 0.5).setConfidence(0.2)"), EPS);
        assertEquals(0.75, probability("UBoolean(true, 0.6).setConfidence(0.75)"), EPS);
        assertEquals(1.0, probability("UBoolean(true, 0.6).setConfidence(1)"), EPS);
    }

    /** Historical equalsC holds when the confidences differ by at most 1 - c. */
    @Test void equalsCComparesConfidencesWithinTheGivenTolerance() {
        assertEquals(BooleanValue.TRUE, eval("UBoolean(true, 0.5).equalsC(UBoolean(true, 0.2), 0.4)"));
        assertEquals(BooleanValue.TRUE, eval("UBoolean(true, 0.5).equalsC(UBoolean(true, 0.2), 0.2)"));
        assertEquals(BooleanValue.TRUE, eval("UBoolean(true, 0.5).equalsC(UBoolean(true, 0.5), 0.5)"));
        // the false-valued literal is canonicalised to (true, 0.6) first
        assertEquals(BooleanValue.TRUE, eval("UBoolean(true, 0.5).equalsC(UBoolean(false, 0.4), 0.9)"));
        assertEquals(BooleanValue.FALSE, eval("UBoolean(false, 0.5).equalsC(UBoolean(true, 0.6), 0.95)"));
    }

    @Test void toBooleanCDecidesAgainstTheGivenConfidence() {
        assertEquals(BooleanValue.TRUE, eval("UBoolean(true, 0.2).toBooleanC(0)"));
        assertEquals(BooleanValue.TRUE, eval("UBoolean(true, 0.2).toBooleanC(0.2)"));
        assertEquals(BooleanValue.TRUE, eval("UBoolean(false, 0.2).toBooleanC(0.3)"));
        assertEquals(BooleanValue.FALSE, eval("UBoolean(true, 0.2).toBooleanC(0.5)"));
    }

    @Test void uncertainStringAccessorsAndConversions() {
        assertEquals("Hola", ((org.tzi.use.uml.ocl.value.StringValue) eval("UString('Hola', 0.8).value()")).value());
        assertEquals(0.8, ((RealValue) eval("UString('Hola', 0.8).confidence()")).value(), EPS);
        assertEquals("Bye", ((UStringValue) eval("UString('Hola', 0.8).setValue('Bye')")).value());
        assertEquals(0.25, ((UStringValue) eval("UString('Hola', 0.8).setConfidence(0.25)")).confidence(), EPS);
        // size carries the confidence over as a distance
        assertEquals(4, ((org.tzi.use.uml.ocl.value.UIntegerValue) eval("UString('Hola', 0.8).size()")).value());
        assertEquals(4 * 0.2, ((org.tzi.use.uml.ocl.value.UIntegerValue) eval("UString('Hola', 0.8).size()")).uncertainty(), 1e-9);
        assertEquals(1, ((org.tzi.use.uml.ocl.value.IntegerValue) eval("UString('Hola', 1).indexOf('o')")).value());
        assertEquals("ol", ((UStringValue) eval("UString('Hola', 1).substring(2, 3)")).value());
        assertEquals("H", ((UStringValue) eval("UString('Hola', 1).at(1)")).value());
        assertEquals(4, ((org.tzi.use.uml.ocl.value.CollectionValue) eval("UString('Hola', 1).character()")).size());
    }

    /** Comparing uncertain strings multiplies the two confidences. */
    @Test void uncertainStringComparisonsMultiplyConfidences() {
        assertEquals(0.5 * 0.8, probability("UString('a', 0.5) < UString('b', 0.8)"), EPS);
        assertEquals(1 - 0.5 * 0.8, probability("UString('a', 0.5) > UString('b', 0.8)"), EPS);
        assertEquals(0.5 * 0.8, probability("UString('a', 0.5) <= UString('b', 0.8)"), EPS);
        assertEquals(1 - 0.5 * 0.8, probability("UString('a', 0.5) >= UString('b', 0.8)"), EPS);
    }

    /**
     * Defects found by the code review of the port. Each one let a type-level
     * promise disagree with the value produced at evaluation, which surfaced as
     * a ClassCastException escaping the evaluator.
     */
    @Test void queriesWithoutAnUncertainCounterpartRejectUncertainPredicates() {
        for (String query : new String[] { "select", "reject", "any", "one" }) {
            StringWriter err = new StringWriter();
            assertNull(OCLCompiler.compileExpression(model,
                    "Sequence{UBoolean(true, 0.9)}->" + query + "(x | x)", "<uncovered>",
                    new PrintWriter(err), new VarBindings()),
                query + " must reject an uncertain predicate");
        }
    }

    @Test void powDoesNotClaimUncertainOperands() {
        // the uncertain algebra registers `power', so `pow' must stay certain
        assertNull(OCLCompiler.compileExpression(model, "UReal(2.0, 0.1).pow(2)", "<uncovered>",
                new PrintWriter(new StringWriter()), new VarBindings()));
        assertEquals(4.0, ((RealValue) eval("2.0.pow(2)")).value(), EPS);
    }

    @Test void equalityStaysCertainWhenItsTypeSaysSo() {
        // the operands are statically OclAny, so the result was checked as Boolean
        Value v = eval("Sequence{UReal(1.0, 0.1), 'x'}->at(1) = Sequence{UReal(1.0, 0.1), 'x'}->at(2)");
        assertTrue(v instanceof BooleanValue, "expected a certain Boolean, got " + v);
    }

    @Test void includesOnAnUndefinedCollectionKeepsItsDeclaredType() {
        Value v = eval("let s : Set(UInteger) = oclUndefined(Set(UInteger)) in s->includes(UInteger(1, 0))");
        assertTrue(v instanceof UBooleanValue, "expected a UBoolean, got " + v);
        assertEquals(0.0, ((UBooleanValue) v).probability(), EPS);
    }

    @Test void uCountCYieldsUndefinedRatherThanThrowing() {
        assertTrue(eval("Sequence{UString('a', 1)}->uCountC(UString('a', 1), 1.5)").isUndefined());
        assertTrue(eval("Sequence{UString('a', 1)}->uCountC(UString('a', 1), oclUndefined(Real))").isUndefined());
    }

    @Test void uncertainIntegerArithmeticDoesNotOverflowItsUncertainty() {
        // the squares in the propagation must be taken in double arithmetic
        Value v = eval("UInteger(50000, 1) * UInteger(50000, 1)");
        assertTrue(v instanceof org.tzi.use.uml.ocl.value.UIntegerValue, "expected a UInteger, got " + v);
        assertEquals(Math.sqrt(2) * 50000,
                ((org.tzi.use.uml.ocl.value.UIntegerValue) v).uncertainty(), 1e-6);
    }
}
