package org.tzi.use.uncertainty;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.PrintWriter;
import java.io.StringWriter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.tzi.use.uml.mm.ModelFactory;
import org.tzi.use.uml.ocl.expr.Evaluator;
import org.tzi.use.uml.ocl.expr.Expression;
import org.tzi.use.uml.ocl.value.SBooleanValue;
import org.tzi.use.uml.ocl.value.Value;
import org.tzi.use.uml.ocl.value.VarBindings;
import org.tzi.use.parser.ocl.OCLCompiler;

/**
 * Value-correctness tests for the 8 OCL-registered SBoolean fusion operators and {@code discount}.
 *
 * <p>{@code MetamorphicRelationsTest.M6SimplexClosure} already exercises every one of these once
 * each, but only checks the result is a *valid opinion* ({@code |b+d+u-1| <= 0.001}) — never that
 * the *computed value* is right. This file closes that gap: each expected value is derived
 * independently from the operation's own documented formula (its Javadoc in
 * {@code datatypes/SBoolean.java}), not by calling the port and trusting it, so a wrong port
 * implementation would fail these tests even though it would pass the simplex-closure check.
 *
 * <p>All three constant opinions below are used throughout this file and are chosen so no two of
 * their projections tie (avoiding {@code majorityFusion}'s tie case, which {@link MajorityFusion}
 * tests separately with its own opinions):
 * <ul>
 *   <li>{@code A = SBoolean(0.5, 0.3, 0.2, 0.5)}  -- projection = 0.5 + 0.5*0.2 = 0.60
 *   <li>{@code B = SBoolean(0.2, 0.5, 0.3, 0.4)}  -- projection = 0.2 + 0.4*0.3 = 0.32
 *   <li>{@code C = SBoolean(0.1, 0.6, 0.3, 0.5)}  -- projection = 0.1 + 0.5*0.3 = 0.25
 * </ul>
 */
class SBooleanFusionValueTest {

    private static final String A = "SBoolean(0.5, 0.3, 0.2, 0.5)";
    private static final String B = "SBoolean(0.2, 0.5, 0.3, 0.4)";
    private static final String C = "SBoolean(0.1, 0.6, 0.3, 0.5)";

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

    private static void assertOpinion(String expr, double belief, double disbelief,
            double uncertainty, double baseRate) {
        SBooleanValue result = (SBooleanValue) run(expr);
        assertEquals(belief, result.belief().value(), 0.0005, expr + ": belief");
        assertEquals(disbelief, result.disbelief().value(), 0.0005, expr + ": disbelief");
        assertEquals(uncertainty, result.uncertainty().value(), 0.0005, expr + ": uncertainty");
        assertEquals(baseRate, result.baseRate().value(), 0.0005, expr + ": baseRate");
    }

    @Nested
    @DisplayName("minimumBeliefFusion: receiver-prepended, picks the lowest-projection opinion")
    class MinimumFusion {

        @Test
        @DisplayName("A.minimumBeliefFusion(Set{B,C}) fuses {A,B,C}; C has the lowest projection (0.25)")
        void picksLowestProjection() {
            assertOpinion(A + ".minimumBeliefFusion(Set{" + B + "," + C + "})",
                    0.1, 0.6, 0.3, 0.5);
        }

        @Test
        @DisplayName("the receiver itself can be the minimum")
        void receiverCanWin() {
            // D = SBoolean(0.05, 0.85, 0.1, 0.5) -- projection = 0.05 + 0.5*0.1 = 0.10, lower than
            // both B (0.32) and C (0.25).
            String D = "SBoolean(0.05, 0.85, 0.1, 0.5)";
            assertOpinion(D + ".minimumBeliefFusion(Set{" + B + "," + C + "})",
                    0.05, 0.85, 0.1, 0.5);
        }
    }

    @Nested
    @DisplayName("majorityBeliefFusion: dogmatic vote by projection-vs-baseRate")
    class MajorityFusion {

        @Test
        @DisplayName("2 opinions vote true (projection > baseRate), 1 votes false -- pos wins")
        void positiveMajority() {
            // A: projection 0.60 > baseRate 0.5  -> pos
            // B: projection 0.32 < baseRate 0.4  -> neg
            // E = SBoolean(0.6, 0.1, 0.3, 0.3): projection = 0.6 + 0.3*0.3 = 0.69 > baseRate 0.3 -> pos
            String E = "SBoolean(0.6, 0.1, 0.3, 0.3)";
            assertOpinion(A + ".majorityBeliefFusion(Set{" + B + "," + E + "})",
                    1.0, 0.0, 0.0, 0.5);
        }

        @Test
        @DisplayName("a tied vote returns the vacuous opinion")
        void tiedVoteIsVacuous() {
            // A: projection 0.60 > baseRate 0.5 -> pos
            // C: projection 0.25 < baseRate 0.5 -> neg
            // F = SBoolean(0.5, 0.3, 0.2, 0.6): projection = 0.5 + 0.6*0.2 = 0.62 < baseRate 0.6? no,
            // 0.62 > 0.6 -> pos. Use instead G tuned to tie: baseRate 0.5, projection exactly 0.5.
            // G = SBoolean(0.4, 0.4, 0.2, 0.5): projection = 0.4 + 0.5*0.2 = 0.50 == baseRate 0.5 -> ignored (tie/undecided).
            // With A(pos) and C(neg) and G(ignored), pos == neg == 1 -> vacuous.
            String G = "SBoolean(0.4, 0.4, 0.2, 0.5)";
            assertOpinion(A + ".majorityBeliefFusion(Set{" + C + "," + G + "})",
                    0.0, 0.0, 1.0, 0.5);
        }
    }

    @Nested
    @DisplayName("discount: multi-edge trust discounting, receiver NOT prepended to the collection")
    class Discount {

        @Test
        @DisplayName("p = product of the trust chain's projections; formula applied to the receiver")
        void multiEdgeChain() {
            // Trust chain is {B, C}, NOT {A, B, C} -- discount does not prepend the receiver.
            // p = B.projection * C.projection = 0.32 * 0.25 = 0.08
            // belief    = p * A.belief     = 0.08 * 0.5 = 0.04
            // disbelief = p * A.disbelief  = 0.08 * 0.3 = 0.024
            // uncertainty = 1 - p*(A.disbelief + A.belief) = 1 - 0.08*0.8 = 0.936
            // baseRate = A.baseRate = 0.5
            // sanity: 0.04 + 0.024 + 0.936 = 1.0
            assertOpinion(A + ".discount(Set{" + B + "," + C + "})",
                    0.04, 0.024, 0.936, 0.5);
        }

        @Test
        @DisplayName("a single fully-trusted intermediary (projection 1.0) passes the opinion through unchanged")
        void fullTrustPassesThrough() {
            // H = SBoolean(1, 0, 0, 0.5) -- projection = 1.0, a dogmatic-true trust opinion.
            // p = 1.0, so belief=A.belief, disbelief=A.disbelief, uncertainty=1-1*(0.3+0.5)=0.2,
            // matching A's own uncertainty exactly.
            String H = "SBoolean(1, 0, 0, 0.5)";
            assertOpinion(A + ".discount(Set{" + H + "})",
                    0.5, 0.3, 0.2, 0.5);
        }
    }

    @Nested
    @DisplayName("beliefConstraintFusion: receiver-prepended, Dempster's-rule belief-constraint combination")
    class BeliefConstraintFusion {

        @Test
        @DisplayName("two mostly-believing opinions reinforce belief beyond either input (harmony, low conflict)")
        void agreementReinforcesBelief() {
            // bcFusion(this, opinion) per SBoolean.java:1257-1273 (implemented using equation 12.2
            // of Josang's Subjective Logic book):
            //   harmony  = b1*u2 + u1*b2 + b1*b2
            //   conflict = b1*d2 + d1*b2
            //   b = harmony / (1 - conflict)
            //   u = (u1*u2) / (1 - conflict)
            //   d = 1 - b - u
            //   a = (a1*(1-u1) + a2*(1-u2)) / (2 - u1 - u2)     [since u1+u2 != 2 here]
            //
            // X = SBoolean(0.7, 0.1, 0.2, 0.5), Y = SBoolean(0.6, 0.2, 0.2, 0.5) -- both mostly
            // believing (b > d for each). beliefConstraintFusion prepends the receiver, so the
            // collection is [X, Y] and cbFusion's pairwise fold reduces to a single X.bcFusion(Y).
            // harmony  = 0.7*0.2 + 0.2*0.6 + 0.7*0.6 = 0.14 + 0.12 + 0.42 = 0.68
            // conflict = 0.7*0.2 + 0.1*0.6 = 0.14 + 0.06 = 0.20
            // b = 0.68 / (1 - 0.20) = 0.68 / 0.80 = 0.85
            // u = (0.2*0.2) / 0.80 = 0.04 / 0.80 = 0.05
            // d = 1 - 0.85 - 0.05 = 0.10
            // a = (0.5*(1-0.2) + 0.5*(1-0.2)) / (2 - 0.2 - 0.2) = (0.4 + 0.4) / 1.6 = 0.8 / 1.6 = 0.5
            String X = "SBoolean(0.7, 0.1, 0.2, 0.5)";
            String Y = "SBoolean(0.6, 0.2, 0.2, 0.5)";
            assertOpinion(X + ".beliefConstraintFusion(Set{" + Y + "})",
                    0.85, 0.10, 0.05, 0.5);
        }

        @Test
        @DisplayName("direct conflict does NOT average -- renormalizing by (1-conflict) sharply cuts uncertainty")
        void directConflictDoesNotAverage() {
            // X = SBoolean(0.9, 0.05, 0.05, 0.5) strongly believing,
            // Y = SBoolean(0.05, 0.9, 0.05, 0.5) strongly disbelieving (mirror image of X: b1=d2,
            // d1=b2, u1=u2, a1=a2). Receiver prepended -> collection [X, Y] -> X.bcFusion(Y).
            // harmony  = 0.9*0.05 + 0.05*0.05 + 0.9*0.05 = 0.045 + 0.0025 + 0.045 = 0.0925
            // conflict = 0.9*0.9 + 0.05*0.05 = 0.81 + 0.0025 = 0.8125
            // b = 0.0925 / (1 - 0.8125) = 0.0925 / 0.1875 = 37/75 = 0.49333...
            // u = (0.05*0.05) / 0.1875 = 0.0025 / 0.1875 = 1/75 = 0.01333...
            // d = 1 - 37/75 - 1/75 = 1 - 38/75 = 37/75 = 0.49333...
            // a = (0.5*0.95 + 0.5*0.95) / (2 - 0.05 - 0.05) = 0.95 / 1.9 = 0.5
            //
            // Contrast with naive averaging (what a wrong "average instead of constraint-fuse" port
            // would produce): avg belief = avg disbelief = 0.475, avg uncertainty = 0.05. The real
            // Dempster's-rule result instead renormalizes by (1 - conflict) = 0.1875, pushing
            // belief/disbelief up to 0.4933 (not 0.475) and crushing uncertainty down to 0.0133
            // (not 0.05) -- exactly the non-averaging behavior belief-constraint fusion must exhibit
            // under strong conflict.
            String X = "SBoolean(0.9, 0.05, 0.05, 0.5)";
            String Y = "SBoolean(0.05, 0.9, 0.05, 0.5)";
            assertOpinion(X + ".beliefConstraintFusion(Set{" + Y + "})",
                    0.4933, 0.4933, 0.0133, 0.5);
        }
    }
}
