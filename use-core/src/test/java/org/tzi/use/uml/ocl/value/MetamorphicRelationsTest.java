package org.tzi.use.uml.ocl.value;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.PrintWriter;
import java.io.StringWriter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.tzi.use.uml.mm.ModelFactory;
import org.tzi.use.uml.ocl.expr.Evaluator;
import org.tzi.use.uml.ocl.expr.Expression;
import org.tzi.use.parser.ocl.OCLCompiler;

/**
 * The six metamorphic relations proposed in {@code docs/port2/stage-03-scope.md} §8.5, implemented
 * from a design that had, until now, no code behind it anywhere — not in the fork (which has no
 * property-based or metamorphic test infrastructure at all; confirmed by search) and not in this
 * port. Each relation is "a property of the ported code checked against *itself*," so — unlike
 * every other test in this port — none of it compares against the historical jar or a fork
 * fixture. That is the point: {@code SBoolean} and {@code UString} have the weakest independent
 * evidence of the five uncertain types (§2 of {@code stage-03-scope.md}), and these six relations
 * were proposed specifically to close that gap without waiting on more fork-test porting.
 *
 * <p>Package chosen deliberately: {@link SBooleanValue}'s 4-arg constructor is package-private, and
 * M-5 (interning independence) needs to construct a value equal to, but not identical to,
 * {@link SBooleanValue#TRUE} — the OCL literal {@code SBoolean(1,0,0,1)} interns to the singleton
 * (confirmed directly: {@code run("SBoolean(1,0,0,1)") == SBooleanValue.TRUE} is {@code true}), so
 * package-private constructor access is the only way to get a genuinely distinct instance.
 */
class MetamorphicRelationsTest {

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

    @Nested
    @DisplayName("M-1: crisp embedding — op(U(x,0), U(y,0)) carries the same representative as crisp op(x,y), degree 0")
    class M1CrispEmbedding {

        @Test
        void uReal() {
            // The exact toString() form asserts both halves of the relation at once: "5.0" is the
            // same representative crisp 2+3 gives, and the trailing "0.0" is the degree.
            assertEquals("UReal(5.0, 0.0)", run("UReal(2,0) + UReal(3,0)").toString());
            assertEquals("5", run("2 + 3").toString());
        }

        @Test
        void uInteger() {
            assertEquals("UInteger(5, 0.0)", run("UInteger(2,0) + UInteger(3,0)").toString());
            assertEquals("5", run("2 + 3").toString());
        }

        @Test
        void uBoolean() {
            // Degree 0 for UBoolean is encoded directly in its canonical (true, p) form: p == 0.0
            // or p == 1.0. The exact string form below asserts both the representative and the
            // degree in one check.
            assertEquals("UBoolean(true, 0.0)", run("UBoolean(true,0) and UBoolean(false,0)").toString());
            assertEquals(run("true and false").toString(),
                    run("(UBoolean(true,0) and UBoolean(false,0))->toBoolean()").toString());
        }

        @Test
        void uString() {
            assertEquals("UString('ab', 0.0)", run("UString('a',0) + UString('b',0)").toString());
            assertEquals("'ab'", run("'a'.concat('b')").toString());
        }
    }

    @Nested
    @DisplayName("M-2: degree monotonicity — raising an input's uncertainty must not lower the result's")
    class M2DegreeMonotonicity {

        @Test
        void uRealAddition() {
            double lowU = uncertaintyOf(run("UReal(2,0.1) + UReal(3,0.1)"));
            double hiU = uncertaintyOf(run("UReal(2,0.5) + UReal(3,0.1)"));
            assertTrue(hiU >= lowU, "raising one UReal operand's uncertainty (0.1 -> 0.5) must not "
                    + "lower the sum's uncertainty: " + lowU + " -> " + hiU);
        }

        @Test
        void uIntegerAddition() {
            double lowU = uncertaintyOf(run("UInteger(2,1) + UInteger(3,1)"));
            double hiU = uncertaintyOf(run("UInteger(2,3) + UInteger(3,1)"));
            assertTrue(hiU >= lowU, "raising one UInteger operand's uncertainty (1 -> 3) must not "
                    + "lower the sum's uncertainty: " + lowU + " -> " + hiU);
        }

        private double uncertaintyOf(Value v) {
            if (v instanceof URealValue r) {
                return r.uncertainty();
            }
            if (v instanceof UIntegerValue i) {
                return i.uncertainty();
            }
            throw new IllegalArgumentException("not a URealValue/UIntegerValue: " + v);
        }
    }

    @Nested
    @DisplayName("M-3: canonicalisation — UBoolean(false,p) is UBoolean(true,1-p) on every operation")
    class M3Canonicalisation {

        @Test
        void and() {
            assertEquals(run("UBoolean(true,0.7) and UBoolean(true,0.7)"),
                    run("UBoolean(false,0.3) and UBoolean(true,0.7)"));
        }

        @Test
        void or() {
            assertEquals(run("UBoolean(true,0.6) or UBoolean(true,0.2)"),
                    run("UBoolean(false,0.4) or UBoolean(true,0.2)"));
        }

        @Test
        void toStringIsCanonicalRegardlessOfInputForm() {
            // UBoolean(false, 0.35) and UBoolean(true, 0.65) both denote "true with probability
            // 0.65" -- the canonical form always reports value=true.
            assertEquals("UBoolean(true, 0.65)", run("UBoolean(false,0.35)").toString());
            assertEquals("UBoolean(true, 0.65)", run("UBoolean(true,0.65)").toString());
        }
    }

    @Nested
    @DisplayName("M-4: widening agreement — a UInteger operation and its UReal widening agree where both are defined")
    class M4WideningAgreement {

        @Test
        void addition() {
            UIntegerValue intResult = (UIntegerValue) run("UInteger(2,1) + UInteger(3,1)");
            URealValue realResult = (URealValue) run("UReal(2,1) + UReal(3,1)");
            assertEquals((double) intResult.value(), realResult.value(), 1e-9,
                    "UInteger(2,1)+UInteger(3,1) and its UReal widening must agree on the representative");
            assertEquals(intResult.uncertainty(), realResult.uncertainty(), 1e-9,
                    "UInteger(2,1)+UInteger(3,1) and its UReal widening must agree on the propagated uncertainty");
        }

        @Test
        void subtraction() {
            UIntegerValue intResult = (UIntegerValue) run("UInteger(8,2) - UInteger(3,1)");
            URealValue realResult = (URealValue) run("UReal(8,2) - UReal(3,1)");
            assertEquals((double) intResult.value(), realResult.value(), 1e-9);
            assertEquals(intResult.uncertainty(), realResult.uncertainty(), 1e-9);
        }
    }

    @Nested
    @DisplayName("M-5: interning independence — a value equal to TRUE/FALSE but not the interned instance behaves identically")
    class M5InterningIndependence {

        @Test
        void freshInstanceIsNotIdenticalButIsEqualToTheSingleton() {
            SBooleanValue fresh = new SBooleanValue(1, 0, 0, 1); // package-private ctor
            assertNotSame(SBooleanValue.TRUE, fresh, "the point of this relation is a NON-interned "
                    + "equal instance; if this ever becomes ==, the relation is untested");
            assertTrue(fresh.equals(SBooleanValue.TRUE));
        }

        @Test
        void freshInstanceBehavesIdenticallyToTheSingletonUnderAnOperation() {
            SBooleanValue fresh = new SBooleanValue(1, 0, 0, 1);
            SBooleanValue other = (SBooleanValue) run("SBoolean(0.5,0.3,0.2,1)");

            Value viaSingleton = SBooleanValue.TRUE.and(other);
            Value viaFresh = fresh.and(other);

            assertEquals(viaSingleton, viaFresh);
            assertEquals(viaSingleton.toString(), viaFresh.toString());
        }
    }

    @Nested
    @DisplayName("M-6: simplex closure — every SBoolean-returning operation satisfies |b+d+u-1| <= 0.001")
    class M6SimplexClosure {

        private static final String A = "SBoolean(0.4,0.3,0.3,1)";
        private static final String B = "SBoolean(0.2,0.5,0.3,1)";
        private static final String C = "SBoolean(0.1,0.1,0.8,1)";

        /**
         * {@code conjunctiveCertainty} and {@code degreeOfConflict} are correctly absent from this
         * set: both actually return a {@code Real} ({@link SBooleanValue#conjunctiveCertainty} /
         * {@link SBooleanValue#degreeOfConflict} wrap a {@code double} in {@code RealValue}), so
         * simplex closure does not apply to them. This was not always so — both operations'
         * {@code matches()} originally declared a {@code SBoolean} return type that never matched
         * their actual runtime value, found incidentally while first building this test. It was
         * initially left unfixed as "a distinct, independently-scoped change." That characterization
         * was revised one stage later, at S9's SBoolean test-coverage pass, on new evidence: the
         * mismatch is not merely cosmetic but crashes with a real {@code NullPointerException} the
         * moment a genuine SBoolean-only operation is chained onto the result (reproduced with
         * {@code conjunctiveCertainty(...).belief()} — see the fix and its full account at
         * {@code StandardOperationsSBoolean.java}'s {@code CONJUNCTIVE_CERTAINTY}/
         * {@code DEGREE_OF_CONFLICT} constants). Both operations' declared type is now {@code Real},
         * matching their real runtime behaviour, and are exercised directly in
         * {@code SBooleanExpOpsTest} rather than through this simplex-closure set.
         */
        private static final String[][] SBOOLEAN_RETURNING_OPERATIONS = {
                {"uncertaintyMaximized", A + ".uncertaintyMaximized()"},
                {"not", "not " + A},
                {"uncertainOpinion", A + ".uncertainOpinion()"},
                {"and", A + " and " + B},
                {"or", A + " or " + B},
                {"xor", A + " xor " + B},
                {"equivalent", A + ".equivalent(" + B + ")"},
                {"implies", A + " implies " + B},
                {"min", A + ".min(" + B + ")"},
                {"max", A + ".max(" + B + ")"},
                {"deduceY", A + ".deduceY(" + B + "," + C + ")"},
                {"applyOn", A + ".applyOn(UBoolean(true,0.5))"},
                {"minimumBeliefFusion", A + ".minimumBeliefFusion(Set{" + B + "," + C + "})"},
                {"majorityBeliefFusion", A + ".majorityBeliefFusion(Set{" + B + "," + C + "})"},
                {"beliefConstraintFusion", A + ".beliefConstraintFusion(Set{" + B + "," + C + "})"},
                {"averageBeliefFusion", A + ".averageBeliefFusion(Set{" + B + "," + C + "})"},
                {"aleatoryCumulativeBeliefFusion", A + ".aleatoryCumulativeBeliefFusion(Set{" + B + "," + C + "})"},
                {"epistemicCumulativeBeliefFusion", A + ".epistemicCumulativeBeliefFusion(Set{" + B + "," + C + "})"},
                {"weightedBeliefFusion", A + ".weightedBeliefFusion(Set{" + B + "," + C + "})"},
                {"consensusAndCompromiseFusion", A + ".consensusAndCompromiseFusion(Set{" + B + "," + C + "})"},
                {"discount", A + ".discount(Set{" + B + "," + C + "})"},
        };

        @Test
        void everySBooleanReturningOperationSatisfiesClosure() {
            for (String[] op : SBOOLEAN_RETURNING_OPERATIONS) {
                String name = op[0];
                String expr = op[1];
                SBooleanValue result = (SBooleanValue) run(expr);
                double b = result.belief().value();
                double d = result.disbelief().value();
                double u = result.uncertainty().value();
                assertTrue(Math.abs(b + d + u - 1) <= 0.001,
                        name + " (" + expr + ") -> " + result + ": |b+d+u-1| = " + Math.abs(b + d + u - 1));
            }
        }
    }
}
