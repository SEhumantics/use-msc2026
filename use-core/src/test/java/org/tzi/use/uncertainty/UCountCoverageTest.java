package org.tzi.use.uncertainty;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.PrintWriter;
import java.io.StringWriter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.tzi.use.uml.mm.ModelFactory;
import org.tzi.use.uml.ocl.expr.Evaluator;
import org.tzi.use.uml.ocl.expr.Expression;
import org.tzi.use.uml.ocl.value.Value;
import org.tzi.use.uml.ocl.value.VarBindings;
import org.tzi.use.parser.ocl.OCLCompiler;

/**
 * {@code uCount}/{@code uCountC} — implemented from scratch this stage. Not a B7 ledger row (the
 * ledger tracks behaviour that changed during porting; these two operations were simply absent
 * before now), but a real fork feature, found missing by an earlier adversarial audit
 * (docs/port2/stage-09.md §5).
 *
 * <p>Ported from USE-Uncertainty (github.com/atenearesearchgroup/uncertainty @ 74acd0d):
 * {@code StandardOperationsCollection.java}'s {@code Op_collection_uCount}/
 * {@code Op_collection_uCountC} classes and {@code CollectionValue.java}'s {@code uCountC} method.
 * Semantics unchanged from the fork. Unlike {@code uSelect}/{@code uSelectC} (S9, the
 * uSelect/collection-membership commit), neither operation needed a grammar change or a new
 * {@code Expression} subclass — both are ordinary two/three-argument {@code OpGeneric} standard
 * operations, registered exactly like {@code count} itself, which they are the uncertain-equality
 * analogue of: an element counts if {@code uEquals} between it and the target meets a confidence
 * threshold (fixed at {@code 0.5} for {@code uCount}, explicit for {@code uCountC}), rather than
 * requiring crisp {@link Object#equals}.
 *
 * <p>The fork's own coverage ({@code UCollectionExpOpTest.testUCount}) evaluates one expression and
 * asserts nothing about the result — it would pass even if {@code uCount} always returned {@code 0}.
 * This file replaces it with assertions on the actual returned count, checked against values this
 * session's own {@code UEqualsCoverageTest} and prior {@code uEquals} evidence already established:
 * a non-degenerate {@code UReal} compared to an exact crisp point has probability ~0 (a continuous
 * distribution never lands on exactly one value), so it is never counted by the default {@code 0.5}
 * threshold; two distinct but overlapping {@code UReal}s can compare with an intermediate
 * probability (here, {@code 0.774}), which a {@code uCountC} threshold can be tuned to include or
 * exclude.
 */
@DisplayName("uCount/uCountC")
class UCountCoverageTest {

    private static Value run(String expr) {
        StringWriter err = new StringWriter();
        Expression e = OCLCompiler.compileExpression(
                new ModelFactory().createModel("m"), expr, "test",
                new PrintWriter(err), new VarBindings());
        if (e == null) {
            throw new IllegalStateException(expr + " did not compile: " + err);
        }
        return new Evaluator().eval(e, null, null, new VarBindings(), null, "");
    }

    @Test
    @DisplayName("uCount matches an exact crisp element, but not a non-degenerate UReal at the same point")
    void uCountCrispMatchOnly() {
        // Set{2, UReal(2,0.5), 5}: 2 matches 2 exactly (prob 1); UReal(2,0.5) vs 2 has prob ~0
        // (a continuous distribution has zero probability of landing on exactly one value); 5
        // doesn't match at all. Only the crisp 2 clears the default 0.5 threshold.
        assertEquals("1 : Integer", run("Set{2, UReal(2,0.5), 5}->uCount(2)").toStringWithType());
    }

    @Test
    @DisplayName("uCount finds every occurrence in a Bag, not just the first")
    void uCountCountsDuplicatesInABag() {
        // A Set would silently dedupe two equal UReal(2,0.5) elements into one; a Bag preserves
        // both, so this is the genuine "count > 1" case count()'s own contract promises.
        assertEquals("2 : Integer", run("Bag{UReal(2,0.5), UReal(2,0.5), 7}->uCount(UReal(2,0.5))").toStringWithType());
    }

    @Test
    @DisplayName("uCount(x) is exactly uCountC(x, 0.5)")
    void uCountIsUCountCAtDefaultThreshold() {
        assertEquals(run("Bag{UReal(2,0.5), UReal(2,0.5), 7}->uCount(UReal(2,0.5))"),
                run("Bag{UReal(2,0.5), UReal(2,0.5), 7}->uCountC(UReal(2,0.5), 0.5)"));
    }

    @Test
    @DisplayName("a stricter uCountC threshold excludes a below-threshold match")
    void higherThresholdIsStricter() {
        // UReal(2,0.5) = UReal(2.3,0.6) has probability 0.774 (confirmed directly, not assumed).
        // A threshold above that excludes it; a threshold below includes it. The self-match
        // (UReal(2,0.5) vs itself, probability 1.0) and the crisp 7 (never matches) are constant
        // across both thresholds, isolating exactly the one element under test.
        assertEquals("1 : Integer",
                run("Set{UReal(2,0.5), UReal(2.3,0.6), 7}->uCountC(UReal(2,0.5), 0.9)").toStringWithType());
        assertEquals("2 : Integer",
                run("Set{UReal(2,0.5), UReal(2.3,0.6), 7}->uCountC(UReal(2,0.5), 0.1)").toStringWithType());
    }

    @Test
    @DisplayName("uCount against Undefined matches nothing")
    void uCountAgainstUndefinedMatchesNothing() {
        assertEquals("0 : Integer", run("Set{2, 3, 5}->uCount(Undefined)").toStringWithType());
    }

    @Test
    @DisplayName("uCountC rejects a confidence outside [0, 1]")
    void uCountCRejectsOutOfRangeConfidence() {
        RuntimeException tooHigh = assertThrows(RuntimeException.class,
                () -> run("Set{2, 3}->uCountC(2, 1.5)"));
        assertEquals("Expression 'uCountC' needs confident between 0 and 1, found 1.5", tooHigh.getMessage());

        RuntimeException tooLow = assertThrows(RuntimeException.class,
                () -> run("Set{2, 3}->uCountC(2, -0.5)"));
        assertEquals("Expression 'uCountC' needs confident between 0 and 1, found -0.5", tooLow.getMessage());
    }
}
