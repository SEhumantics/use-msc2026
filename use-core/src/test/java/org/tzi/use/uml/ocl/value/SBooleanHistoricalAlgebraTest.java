package org.tzi.use.uml.ocl.value;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Replays the historical subjective-logic algebra oracle
 * ({@code uDataTypes.SBooleanTest} and {@code SBooleanTest3}) against the port.
 * The subjective Boolean has no counterpart in the compiler corpus, so this is
 * the only end-to-end evidence for its operators.
 *
 * <p>The historical implementation compares opinions with a tolerance of 1e-3,
 * which is what its own expectations are written to, so the comparisons here use
 * the same tolerance.
 */
class SBooleanHistoricalAlgebraTest {

    private static final double EPS = 1e-3;

    /** The historical {@code SBoolean(UBoolean)} embedding. */
    private static SBooleanValue of(UBooleanValue b) {
        return SBooleanValue.dogmatic(b.probability(), b.probability());
    }

    private static void assertOpinion(double belief, double disbelief, double uncertainty,
            double baseRate, SBooleanValue actual, String what) {
        assertEquals(belief, actual.belief(), EPS, what + " belief");
        assertEquals(disbelief, actual.disbelief(), EPS, what + " disbelief");
        assertEquals(uncertainty, actual.uncertainty(), EPS, what + " uncertainty");
        assertEquals(baseRate, actual.baseRate(), EPS, what + " base rate");
    }

    private final SBooleanValue t = SBooleanValue.TRUE;
    private final SBooleanValue f = of(UBooleanValue.probability(true, 0));
    private final SBooleanValue b1 = of(UBooleanValue.probability(true, 0.7));
    private final SBooleanValue b2 = of(UBooleanValue.probability(false, 0.7));
    private final SBooleanValue b3 = new SBooleanValue(0.2, 0.6, 0.2, 0.5);
    private final SBooleanValue b4 = new SBooleanValue(0.6, 0.2, 0.2, 0.5);

    @Test void maximizingUncertaintyPreservesTheProjection() {
        for (SBooleanValue x : new SBooleanValue[] { t, f, b1, b2, b3, b4,
                new SBooleanValue(0.7, 0.1, 0.2, 0.5),
                new SBooleanValue(0.85, 0.05, 0.10, 0.9) }) {
            assertEquals(x.projection(), x.uncertaintyMaximized().projection(), EPS,
                    "projection of uncertainty maximized " + x);
            assertTrue(x.uncertaintyMaximized().isMaximizedUncertainty(),
                    "uncertainty maximized " + x);
        }
    }

    @Test void dogmaticOperandsAbsorbConjunctionAndDisjunction() {
        assertOpinion(t.belief(), t.disbelief(), t.uncertainty(), t.baseRate(), t.or(f), "t or f");
        assertOpinion(f.belief(), f.disbelief(), f.uncertainty(), f.baseRate(), t.and(f), "t and f");
        assertOpinion(t.belief(), t.disbelief(), t.uncertainty(), t.baseRate(), t.or(b1), "t or b1");
        assertOpinion(b1.belief(), b1.disbelief(), b1.uncertainty(), b1.baseRate(), t.and(b1), "t and b1");
    }

    @Test void conjunctionAndDisjunctionOfUncertainOpinions() {
        assertOpinion(0.680, 0.173, 0.147, 0.750, b3.or(b4), "b3 or b4");
        assertOpinion(0.173, 0.680, 0.147, 0.250, b3.and(b4), "b3 and b4");
        assertOpinion(0.154, 0.150, 0.696, 0.1,
                new SBooleanValue(0.75, 0.15, 0.1, 0.5).and(new SBooleanValue(0.1, 0, 0.9, 0.2)), "b5 and");
        assertOpinion(0.837, 0.065, 0.098, 0.6,
                new SBooleanValue(0.75, 0.15, 0.1, 0.5).or(new SBooleanValue(0.35, 0, 0.65, 0.2)), "b5 or");
    }

    @Test void exclusiveOrAndEquivalence() {
        assertOpinion(0.4, 0.6, 0, 0.4, b1.xor(b2), "b1 xor b2");
        assertOpinion(0.6, 0.4, 0, 0.6, b1.equivalent(b2), "b1 equivalent b2");
        assertOpinion(0.4, 0.56, 0.04, 0, b3.xor(b4), "b3 xor b4");
        assertOpinion(0.56, 0.4, 0.04, 1, b3.equivalent(b4), "b3 equivalent b4");
    }

    @Test void projectionsAgreeWithTheUncertainBooleanAlgebra() {
        assertEquals(0.4, b1.toUBoolean().xor(b2.toUBoolean()).probability(), EPS, "b1 xor b2 projected");
        assertEquals(0.6, b1.toUBoolean().equivalent(b2.toUBoolean()).probability(), EPS, "b1 equivalent b2 projected");
        assertEquals(0.4, b3.toUBoolean().xor(b4.toUBoolean()).probability(), EPS, "b3 xor b4 projected");
        assertEquals(0.6, b3.toUBoolean().equivalent(b4.toUBoolean()).probability(), EPS, "b3 equivalent b4 projected");
    }

    @Test void equivalenceBuiltFromImplicationsInBothDirections() {
        assertOpinion(0.389, 0.477, 0.134, 0.563,
                b3.implies(b4).and(b4.implies(b3)), "b3 implies b4 and back");
    }

    @Test void deductionFollowsTheHistoricalConditionals() {
        assertOpinion(0.320, 0.480, 0.200, 0.400,
                new SBooleanValue(0.0, 0.0, 1, 0.8).deduceY(
                        new SBooleanValue(0.4, 0.5, 0.1, 0.4),
                        new SBooleanValue(0.0, 0.4, 0.6, 0.4)), "vacuous antecedent");
        assertOpinion(0.072, 0.418, 0.510, 0.400,
                new SBooleanValue(0.10, 0.8, 0.1, 0.8).deduceY(
                        new SBooleanValue(0.4, 0.5, 0.1, 0.4),
                        new SBooleanValue(0.0, 0.4, 0.6, 0.4)), "mostly disbelieved antecedent");
        assertOpinion(0.151, 0.48, 0.369, 0.382,
                new SBooleanValue(0.0, 0.40, 0.6, 0.5).deduceY(
                        new SBooleanValue(0.55, 0.3, 0.15, 0.38),
                        new SBooleanValue(0.1, 0.75, 0.15, 0.38)), "uncertain antecedent");
    }

    private static java.util.List<SBooleanValue> panel() {
        return java.util.List.of(
                new SBooleanValue(0.55, 0.3, 0.15, 0.38),
                new SBooleanValue(0.6, 0.3, 0.1, 0.38),
                new SBooleanValue(0.7, 0.2, 0.1, 0.38),
                new SBooleanValue(0.8, 0.1, 0.1, 0.38),
                new SBooleanValue(0.9, 0.05, 0.05, 0.38));
    }

    @Test void beliefFusionOperatorsMatchTheHistoricalPanel() {
        assertOpinion(0.757, 0.156, 0.087, 0.380,
                SBooleanValue.weightedBeliefFusion(panel()), "weighted");
        assertOpinion(1, 0, 0, 0.5,
                SBooleanValue.majorityBeliefFusion(panel()), "majority");
        assertOpinion(0.55, 0.3, 0.15, 0.38,
                SBooleanValue.minimumBeliefFusion(panel()), "minimum");
        assertOpinion(0.753, 0.159, 0.088, 0.38,
                SBooleanValue.averageBeliefFusion(panel()), "average");
        assertOpinion(0.810, 0.171, 0.019, 0.38,
                SBooleanValue.aleatoryCumulativeBeliefFusion(panel()), "aleatory cumulative");
        assertOpinion(0.705, 0.0, 0.295, 0.38,
                SBooleanValue.epistemicCumulativeBeliefFusion(panel()), "epistemic cumulative");
        assertOpinion(0.997, 0.003, 0, 0.38,
                SBooleanValue.beliefConstraintFusion(panel()), "belief constraint");
        assertOpinion(0.564, 0.057, 0.379, 0.38,
                SBooleanValue.consensusAndCompromiseFusion(panel()), "consensus and compromise");
    }

    @Test void consensusAndCompromiseFusionOnTheDegenerateOpinions() {
        SBooleanValue T = new SBooleanValue(1, 0, 0, 0.5);   // true, with a 0.5 base rate
        SBooleanValue F = new SBooleanValue(0, 1, 0, 0.5);   // false, with a 0.5 base rate
        SBooleanValue U = new SBooleanValue(0, 0, 1, 0.5);   // vacuous
        SBooleanValue I = new SBooleanValue(0.5, 0.5, 0, 0.5); // dogmatic ignorance

        assertOpinion(0, 0, 1, 0.5, ccFusion(T, U), "T cc U");
        assertOpinion(0, 0, 1, 0.5, ccFusion(F, U), "F cc U");
        assertOpinion(1, 0, 0, 0.5, ccFusion(T, T), "T cc T");
        assertOpinion(0, 1, 0, 0.5, ccFusion(F, F), "F cc F");
        assertOpinion(0, 0, 1, 0.5, ccFusion(U, U), "U cc U");
        assertOpinion(0, 0, 1, 0.5, ccFusion(U, T), "U cc T");
        assertOpinion(0, 0, 1, 0.5, ccFusion(U, F), "U cc F");
        assertOpinion(0, 0, 1, 0.5, ccFusion(U, I), "U cc I");
        assertOpinion(0.5, 0.5, 0, 0.5, ccFusion(I, I), "I cc I");
        assertOpinion(0, 0, 1, 0.5, ccFusion(I, U), "I cc U");
        assertOpinion(0.5, 0.0, 0.5, 0.5, ccFusion(I, T), "I cc T");
        assertOpinion(0.0, 0.5, 0.5, 0.5, ccFusion(I, F), "I cc F");
    }

    private static SBooleanValue ccFusion(SBooleanValue a, SBooleanValue b) {
        return SBooleanValue.consensusAndCompromiseFusion(java.util.List.of(a, b));
    }
}
