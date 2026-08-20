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
 * <p>One deliberate exception: the {@code ConsensusAndCompromiseFusion} tests below are
 * property/hazard checks (a fixed-point, a genuine-fusion, and an n=6 scale probe), not
 * formula-derived exact values. {@code consensusAndCompromiseFusion}'s algorithm is O(4^n) over the
 * opinion count, which makes hand-deriving an exact expected value impractical beyond the smallest
 * cases; this was a deliberate, sanctioned scope choice for that operation rather than an oversight.
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

    @Nested
    @DisplayName("averageBeliefFusion: receiver-prepended, equation (32) of JWZ2017-FUSION (not the book formula)")
    class AverageFusion {

        @Test
        @DisplayName("ordinary 3-opinion case, all uncertainties > 0 (averagingFusion's 'case I')")
        void ordinaryThreeOpinionCase() {
            // averagingFusion per SBoolean.java (case I -- PU = product of uncertainties != 0):
            //   PU = u1*u2*u3
            //   for each o: u += PU/o.u ; b += o.belief * PU/o.u ; a += o.baseRate
            //   belief = b/u ; atomicity = a/N ; uncertainty = N*PU/u ; disbelief = 1-belief-uncertainty
            //
            // averageBeliefFusion prepends the receiver, so the collection is [A, B, C].
            // PU = 0.2*0.3*0.3 = 0.018
            // PU/u_A = 0.018/0.2 = 0.09 ; PU/u_B = 0.018/0.3 = 0.06 ; PU/u_C = 0.018/0.3 = 0.06
            // u_sum = 0.09+0.06+0.06 = 0.21
            // b_sum = 0.5*0.09 + 0.2*0.06 + 0.1*0.06 = 0.045+0.012+0.006 = 0.063
            // belief = 0.063/0.21 = 0.3
            // uncertainty = 3*0.018/0.21 = 0.054/0.21 = 0.257143
            // disbelief = 1 - 0.3 - 0.257143 = 0.442857
            // baseRate = (0.5+0.4+0.5)/3 = 1.4/3 = 0.466667
            // sanity: 0.3+0.442857+0.257143 = 1.0
            assertOpinion(A + ".averageBeliefFusion(Set{" + B + "," + C + "})",
                    0.3, 0.4429, 0.2571, 0.4667);
        }

        @Test
        @DisplayName("dogmatic short-circuit: when any opinion is dogmatic, non-dogmatic opinions are ignored entirely")
        void dogmaticShortCircuitIgnoresUncertainOpinions() {
            // averagingFusion's 'case II' triggers when PU (product of uncertainties) == 0, i.e. at
            // least one opinion is dogmatic (u==0). In that branch, ONLY opinions with u==0 are
            // summed; non-dogmatic opinions contribute nothing at all (not even partially).
            //
            // Ad = SBoolean(0.7, 0.3, 0, 0.5) (dogmatic, receiver)
            // Ed = SBoolean(0.9, 0.1, 0, 0.3) (dogmatic)
            // B  = SBoolean(0.2, 0.5, 0.3, 0.4) (NOT dogmatic, u=0.3 -- must be fully excluded)
            // count = 2 (Ad, Ed) ; b = 0.7+0.9 = 1.6 ; a = 0.5+0.3 = 0.8
            // belief = 1.6/2 = 0.8 ; atomicity = 0.8/2 = 0.4 ; uncertainty = 0 ; disbelief = 1-0.8-0 = 0.2
            String Ad = "SBoolean(0.7, 0.3, 0, 0.5)";
            String Ed = "SBoolean(0.9, 0.1, 0, 0.3)";
            assertOpinion(Ad + ".averageBeliefFusion(Set{" + Ed + "," + B + "})",
                    0.8, 0.2, 0.0, 0.4);
        }
    }

    @Nested
    @DisplayName("aleatoryCumulativeBeliefFusion: receiver-prepended, Josang's cumulative fusion for i.i.d. sources")
    class AleatoryCumulativeFusion {

        @Test
        @DisplayName("ordinary 3-opinion case, no dogmatic opinions (Eq16 of 10.23919/ICIF.2017.8009820)")
        void ordinaryThreeOpinionCase() {
            // aleatoryCumulativeFusion per SBoolean.java, non-dogmatic branch:
            //   PU = product of all uncertainties
            //   for each o: prod = PU/o.u ; beliefAcc += prod*o.belief ; disbeliefAcc += prod*o.disbelief
            //               numerator += prod
            //   numerator -= (N-1)*PU
            //   belief = beliefAcc/numerator ; disbelief = disbeliefAcc/numerator ; uncertainty = PU/numerator
            //   atomicity = baseRate of the FIRST opinion in iteration order (always the prepended
            //   receiver, since it is added to the LinkedList before the passed-in Set's elements)
            //
            // aleatoryCumulativeBeliefFusion prepends the receiver: collection = [A, B, C].
            // PU = 0.2*0.3*0.3 = 0.018
            // prod_A = 0.018/0.2 = 0.09 ; prod_B = 0.018/0.3 = 0.06 ; prod_C = 0.018/0.3 = 0.06
            // beliefAcc    = 0.5*0.09 + 0.2*0.06 + 0.1*0.06 = 0.045+0.012+0.006 = 0.063
            // disbeliefAcc = 0.3*0.09 + 0.5*0.06 + 0.6*0.06 = 0.027+0.030+0.036 = 0.093
            // numerator = (0.09+0.06+0.06) - 2*0.018 = 0.21 - 0.036 = 0.174
            // belief = 0.063/0.174 = 0.362069
            // disbelief = 0.093/0.174 = 0.534483
            // uncertainty = 0.018/0.174 = 0.103448
            // baseRate = A.baseRate = 0.5 (receiver is first in iteration order)
            // sanity: 0.362069+0.534483+0.103448 = 1.0
            assertOpinion(A + ".aleatoryCumulativeBeliefFusion(Set{" + B + "," + C + "})",
                    0.3621, 0.5345, 0.1034, 0.5);
        }

        @Test
        @DisplayName("dogmatic branch: non-dogmatic receiver is entirely excluded from belief/disbelief")
        void dogmaticBranchExcludesNonDogmaticReceiver() {
            // When at least one fused opinion is dogmatic (u==0), the algorithm switches to a
            // relative-weight-weighted average over ONLY the dogmatic opinions; non-dogmatic
            // opinions (including a non-dogmatic receiver) contribute nothing to belief/disbelief.
            //
            // D1 = SBoolean(0.9, 0.1, 0, 0.5) (dogmatic), D2 = SBoolean(0.3, 0.7, 0, 0.6) (dogmatic)
            // A (receiver, u=0.2, NOT dogmatic) is prepended but excluded from the weighted average.
            // Both D1, D2 have default relativeWeight = 1 (getRelativeWeight() returns relativeWeight
            // when isDogmatic(), else 0), so totalWeight = 2.
            // belief    = (1/2)*0.9 + (1/2)*0.3 = 0.45+0.15 = 0.6
            // disbelief = (1/2)*0.1 + (1/2)*0.7 = 0.05+0.35 = 0.4
            // uncertainty = 0
            // baseRate = A.baseRate = 0.5 (receiver is still first in iteration order, dogmatic or not)
            String D1 = "SBoolean(0.9, 0.1, 0, 0.5)";
            String D2 = "SBoolean(0.3, 0.7, 0, 0.6)";
            assertOpinion(A + ".aleatoryCumulativeBeliefFusion(Set{" + D1 + "," + D2 + "})",
                    0.6, 0.4, 0.0, 0.5);
        }
    }

    @Nested
    @DisplayName("epistemicCumulativeBeliefFusion: same accumulation as aleatory, then projected onto the uncertainty-maximized boundary")
    class EpistemicCumulativeFusion {

        @Test
        @DisplayName("ordinary 3-opinion case: intermediate result matches aleatory's, but uncertaintyMaximized() then redistributes b/d/u")
        void ordinaryThreeOpinionCase() {
            // epistemicCumulativeFusion per SBoolean.java: computes an IDENTICAL intermediate
            // result to aleatoryCumulativeFusion (same non-dogmatic-branch Eq16 accumulation --
            // see AleatoryCumulativeFusion.ordinaryThreeOpinionCase above for that arithmetic),
            // but then returns intermediate.uncertaintyMaximized() instead of the intermediate
            // directly. uncertaintyMaximized() preserves the projection p = b + a*u and the
            // atomicity a, but pushes all evidence to the uncertainty-maximized boundary:
            //   if p < a: belief=0, disbelief=1-p/a, uncertainty=p/a
            //   else:     belief=(p-a)/(1-a), disbelief=0, uncertainty=(1-p)/(1-a)
            //
            // Intermediate (identical to aleatory): belief=0.362069, disbelief=0.534483,
            // uncertainty=0.103448, atomicity=0.5 (A's baseRate, receiver is first in iteration).
            // p = 0.362069 + 0.5*0.103448 = 0.362069+0.051724 = 0.413793
            // p < a (0.413793 < 0.5), so:
            //   belief = 0
            //   disbelief = 1 - p/a = 1 - 0.413793/0.5 = 1 - 0.827586 = 0.172414
            //   uncertainty = p/a = 0.827586
            // sanity: 0+0.172414+0.827586 = 1.0 ; projection check: 0+0.5*0.827586 = 0.413793 = p (preserved)
            assertOpinion(A + ".epistemicCumulativeBeliefFusion(Set{" + B + "," + C + "})",
                    0.0, 0.1724, 0.8276, 0.5);
        }

        @Test
        @DisplayName("dogmatic branch: same intermediate as aleatory's dogmatic case, then uncertainty-maximized")
        void dogmaticBranchThenUncertaintyMaximized() {
            // Same D1, D2 as AleatoryCumulativeFusion.dogmaticBranchExcludesNonDogmaticReceiver:
            // the dogmatic-branch intermediate is IDENTICAL: belief=0.6, disbelief=0.4,
            // uncertainty=0, atomicity=0.5 (A's baseRate). Then uncertaintyMaximized() is applied:
            // p = 0.6 + 0.5*0 = 0.6 ; a = 0.5 ; p >= a, so:
            //   belief = (p-a)/(1-a) = (0.6-0.5)/0.5 = 0.2
            //   disbelief = 0
            //   uncertainty = (1-p)/(1-a) = (1-0.6)/0.5 = 0.8
            // sanity: 0.2+0+0.8 = 1.0 ; projection check: 0.2+0.5*0.8 = 0.6 = p (preserved)
            String D1 = "SBoolean(0.9, 0.1, 0, 0.5)";
            String D2 = "SBoolean(0.3, 0.7, 0, 0.6)";
            assertOpinion(A + ".epistemicCumulativeBeliefFusion(Set{" + D1 + "," + D2 + "})",
                    0.2, 0.0, 0.8, 0.5);
        }
    }

    @Nested
    @DisplayName("aleatoryCumulativeBeliefFusion vs epistemicCumulativeBeliefFusion: same input must diverge")
    class AleatoryVsEpistemicDivergence {

        @Test
        @DisplayName("same {A,B,C} input set produces materially different belief and uncertainty under each rule")
        void divergesOnSameInput() {
            // Both calls fuse the SAME opinion set {A, B, C} (receiver A prepended in both, per
            // AleatoryCumulativeFusion.ordinaryThreeOpinionCase and
            // EpistemicCumulativeFusion.ordinaryThreeOpinionCase above):
            //   aleatory:  belief=0.362069, uncertainty=0.103448
            //   epistemic: belief=0.000000, uncertainty=0.827586
            // These differ by ~0.36 on belief and ~0.72 on uncertainty -- far beyond floating-point
            // noise. This guards against a port bug where epistemicCumulativeBeliefFusion
            // accidentally delegates to (or copies) aleatoryCumulativeBeliefFusion, which would
            // otherwise pass every other check in this plan (both algorithms share the same
            // non-dogmatic accumulation step, so a missing uncertaintyMaximized() call would only
            // be caught by comparing the two side by side on identical input).
            SBooleanValue aleatory = (SBooleanValue) run(A + ".aleatoryCumulativeBeliefFusion(Set{" + B + "," + C + "})");
            SBooleanValue epistemic = (SBooleanValue) run(A + ".epistemicCumulativeBeliefFusion(Set{" + B + "," + C + "})");

            double beliefDiff = Math.abs(aleatory.belief().value() - epistemic.belief().value());
            double uncertaintyDiff = Math.abs(aleatory.uncertainty().value() - epistemic.uncertainty().value());

            assertTrue(beliefDiff > 0.1,
                    "belief should diverge materially: aleatory=" + aleatory.belief().value()
                            + " epistemic=" + epistemic.belief().value());
            assertTrue(uncertaintyDiff > 0.1,
                    "uncertainty should diverge materially: aleatory=" + aleatory.uncertainty().value()
                            + " epistemic=" + epistemic.uncertainty().value());
        }
    }

    @Nested
    @DisplayName("weightedBeliefFusion: receiver-prepended, confidence-weighted averaging (FUSION-2018 van der Heijden et al.)")
    class WeightedFusion {

        @Test
        @DisplayName("ordinary 3-opinion case, no dogmatic opinions, not all vacuous ('case 1')")
        void ordinaryThreeOpinionCase() {
            // weightedFusion per SBoolean.java ('case 1' -- dogmatic set empty, at least one
            // opinion has certainty > 0):
            //   PU = product of uncertainties ; sumU = sum of uncertainties
            //   for each o: prod = PU/o.u
            //     beliefAcc += prod*o.belief*o.certainty ; disbeliefAcc += prod*o.disbelief*o.certainty
            //     atomicityAcc += o.baseRate*o.certainty ; numerator += prod
            //   numerator -= N*PU
            //   belief = beliefAcc/numerator ; disbelief = disbeliefAcc/numerator
            //   uncertainty = (N-sumU)*PU/numerator ; atomicity = atomicityAcc/(N-sumU)
            //
            // weightedBeliefFusion prepends the receiver: collection = [A, B, C].
            // PU = 0.2*0.3*0.3 = 0.018 ; sumU = 0.2+0.3+0.3 = 0.8
            // prod_A = 0.09, prod_B = 0.06, prod_C = 0.06 (same as prior fusions)
            // certainty_A = 1-0.2 = 0.8 ; certainty_B = 1-0.3 = 0.7 ; certainty_C = 1-0.3 = 0.7
            // beliefAcc    = 0.09*0.5*0.8 + 0.06*0.2*0.7 + 0.06*0.1*0.7 = 0.036+0.0084+0.0042 = 0.0486
            // disbeliefAcc = 0.09*0.3*0.8 + 0.06*0.5*0.7 + 0.06*0.6*0.7 = 0.0216+0.021+0.0252 = 0.0678
            // atomicityAcc = 0.5*0.8 + 0.4*0.7 + 0.5*0.7 = 0.4+0.28+0.35 = 1.03
            // numerator = (0.09+0.06+0.06) - 3*0.018 = 0.21 - 0.054 = 0.156
            // belief = 0.0486/0.156 = 0.311538
            // disbelief = 0.0678/0.156 = 0.434615
            // uncertainty = (3-0.8)*0.018/0.156 = 2.2*0.018/0.156 = 0.0396/0.156 = 0.253846
            // atomicity = 1.03/(3-0.8) = 1.03/2.2 = 0.468182
            // sanity: 0.311538+0.434615+0.253846 = 1.0 (within rounding)
            assertOpinion(A + ".weightedBeliefFusion(Set{" + B + "," + C + "})",
                    0.3115, 0.4346, 0.2538, 0.4682);
        }

        @Test
        @DisplayName("all-vacuous case ('case 3'): resultAtomicity should be the PLAIN AVERAGE of all baseRates per the code's own comment")
        void allVacuousCaseAveragesBaseRates() {
            // weightedFusion's 'case 3' triggers when every opinion has uncertainty == 1 (fully
            // vacuous). Per SBoolean.java's own comment immediately above the accumulation loop:
            //   "all confidences are zero, so the weight for each opinion is the same -> use a
            //    plain average for the resultAtomicity"
            // i.e. resultAtomicity should be (sum of all N baseRates) / N.
            //
            // R  = SBoolean(0, 0, 1, 0.5) (vacuous, receiver)
            // V1 = SBoolean(0, 0, 1, 0.3) (vacuous)
            // V2 = SBoolean(0, 0, 1, 0.4) (vacuous)
            // Intended per the comment: atomicity = (0.5+0.3+0.4)/3 = 1.2/3 = 0.4
            // belief = 0, disbelief = 0, uncertainty = 1 (both branches of case 3 agree on these)
            String R = "SBoolean(0, 0, 1, 0.5)";
            String V1 = "SBoolean(0, 0, 1, 0.3)";
            String V2 = "SBoolean(0, 0, 1, 0.4)";
            assertOpinion(R + ".weightedBeliefFusion(Set{" + V1 + "," + V2 + "})",
                    0.0, 0.0, 1.0, 0.4);
        }
    }

    @Nested
    @DisplayName("consensusAndCompromiseFusion: O(4^n) hazard and degenerate cases")
    class ConsensusAndCompromiseFusion {

        @Test
        @DisplayName("fusing N identical opinions returns that same opinion")
        void identicalOpinionsAreAFixedPoint() {
            // Sequence{}, not Set{}: SBooleanValue has value-based equals/hashCode, so a HashSet-backed
            // Set{A,A,A} would silently collapse the three identical literals to one element, leaving
            // ccFusion to see n=2 opinions (receiver + the single surviving element) rather than the
            // n=4 this test is meant to exercise. Sequence{} permits duplicates, so all three copies of
            // A reach ccFusion alongside the receiver.
            String expr = A + ".consensusAndCompromiseFusion(Sequence{" + A + "," + A + "," + A + "})";
            assertOpinion(expr, 0.5, 0.3, 0.2, 0.5);
        }

        @Test
        @DisplayName("two dogmatic, totally conflicting opinions still fuse to a valid opinion, not a pass-through")
        void conflictingDogmaticOpinionsProduceGenuineFusion() {
            String dogTrue = "SBoolean(1, 0, 0, 0.5)";
            String dogFalse = "SBoolean(0, 1, 0, 0.5)";
            SBooleanValue result = (SBooleanValue) run(dogTrue + ".consensusAndCompromiseFusion(Set{" + dogFalse + "})");
            double b = result.belief().value(), d = result.disbelief().value(), u = result.uncertainty().value();
            assertEquals(1.0, b + d + u, 0.001, "result must be a valid opinion");
            boolean isDogTruePassThrough = Math.abs(b - 1.0) < 0.001 && Math.abs(d) < 0.001;
            boolean isDogFalsePassThrough = Math.abs(d - 1.0) < 0.001 && Math.abs(b) < 0.001;
            assertTrue(!isDogTruePassThrough && !isDogFalsePassThrough,
                    "fusing two conflicting dogmatic opinions must not just echo one of the inputs back");
        }

        @Test
        @DisplayName("6 distinct opinions (4^6 = 4096 candidate combinations) completes and returns a valid opinion")
        void scalesToSixOpinionsWithoutHanging() {
            // ccFusion requires every fused opinion to share the same baseRate (it throws
            // IllegalArgumentException otherwise -- see SBoolean.java's baseRate-equality check at
            // the top of ccFusion). All six opinions below use baseRate 0.5; belief/disbelief/
            // uncertainty still differ across all six so the permutation search is genuinely
            // exercised at n=6 (4^6 = 4096 combinations).
            String[] opinions = {
                    "SBoolean(0.5,0.3,0.2,0.5)", "SBoolean(0.2,0.5,0.3,0.5)", "SBoolean(0.1,0.6,0.3,0.5)",
                    "SBoolean(0.6,0.1,0.3,0.5)", "SBoolean(0.4,0.4,0.2,0.5)", "SBoolean(0.05,0.85,0.1,0.5)"
            };
            String set = "Set{" + String.join(",", opinions[1], opinions[2], opinions[3], opinions[4], opinions[5]) + "}";
            long start = System.nanoTime();
            SBooleanValue result = (SBooleanValue) run(opinions[0] + ".consensusAndCompromiseFusion(" + set + ")");
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;
            double sum = result.belief().value() + result.disbelief().value() + result.uncertainty().value();
            assertEquals(1.0, sum, 0.001, "result must be a valid opinion at n=6");
            assertTrue(elapsedMs < 30_000,
                    "consensusAndCompromiseFusion at n=6 (4^6=4096 combinations) took " + elapsedMs
                            + "ms -- if this regresses badly, the O(4^n) hazard documented in "
                            + "b7-fix-plan.md section 6 has become a real performance problem");
        }
    }
}
