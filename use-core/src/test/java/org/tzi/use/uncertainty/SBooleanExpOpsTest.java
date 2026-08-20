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
import org.tzi.use.uml.ocl.value.Value;
import org.tzi.use.uml.ocl.value.VarBindings;
import org.tzi.use.parser.ocl.OCLCompiler;

/**
 * The 13 {@code SBoolean} operations found untested by an independent audit dispatched to double-
 * check the port's completeness beyond the closed B7 ledger: {@code projection},
 * {@code projectiveDistance}, {@code toUBoolean}, {@code toString}, {@code getRelativeWeight},
 * {@code isAbsolute}, {@code isVacuous}, {@code isCertain}, {@code isDogmatic},
 * {@code isMaximizedUncertainty}, {@code isUncertain}, {@code certainty}, plus
 * {@code conjunctiveCertainty}/{@code degreeOfConflict} (already exercised for their VALUE by
 * {@code MetamorphicRelationsTest}'s exclusion note, but not regression-tested for the crash their
 * declared-type fix eliminates). Not a B7 ledger row: these operations existed and worked before
 * this stage, they simply had no dedicated test anywhere.
 *
 * <p>The other 26 of SBoolean's 39 operations are covered elsewhere: 21 via
 * {@code MetamorphicRelationsTest}'s M-6 simplex closure, {@code and}/{@code not} via
 * {@code SBooleanMarshallingTest}, {@code belief}/{@code disbelief}/{@code baseRate}/
 * {@code uncertainty}/{@code projection} incidentally via {@code UEqualsCoverageTest} and this file.
 */
@DisplayName("SBoolean: the 13 operations found untested")
class SBooleanExpOpsTest {

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

    // belief=0.5, disbelief=0.3, uncertainty=0.2, baseRate=0.5 -- a "normal" opinion: not
    // absolute (uncertainty != 0), not vacuous (belief/disbelief != 0), not dogmatic.
    private static final String A = "SBoolean(0.5, 0.3, 0.2, 0.5)";
    private static final String B = "SBoolean(0.2, 0.5, 0.3, 0.4)";

    @Nested
    @DisplayName("scalar accessors: projection, getRelativeWeight, certainty")
    class ScalarAccessors {

        @Test
        @DisplayName("projection: belief + baseRate * uncertainty")
        void projection() {
            assertEquals("0.6", run(A + ".projection()").toString());
        }

        @Test
        @DisplayName("getRelativeWeight returns a Real")
        void getRelativeWeight() {
            assertEquals("0.0", run(A + ".getRelativeWeight()").toString());
        }

        @Test
        @DisplayName("certainty: 1 - uncertainty")
        void certainty() {
            assertEquals("0.8", run(A + ".certainty()").toString());
        }
    }

    @Nested
    @DisplayName("classification predicates")
    class Predicates {

        @Test
        @DisplayName("isAbsolute: true only when uncertainty is 0")
        void isAbsolute() {
            assertEquals("false", run(A + ".isAbsolute()").toString());
            assertEquals("true", run("SBoolean(1,0,0,1).isAbsolute()").toString());
        }

        @Test
        @DisplayName("isVacuous: true only when belief and disbelief are both 0")
        void isVacuous() {
            assertEquals("false", run(A + ".isVacuous()").toString());
            assertEquals("true", run("SBoolean(0,0,1,0.5).isVacuous()").toString());
        }

        @Test
        @DisplayName("isDogmatic: true only when uncertainty is 0 (same shape as isAbsolute)")
        void isDogmatic() {
            assertEquals("false", run(A + ".isDogmatic()").toString());
            assertEquals("true", run("SBoolean(1,0,0,1).isDogmatic()").toString());
        }

        @Test
        @DisplayName("isMaximizedUncertainty: true only for the fully-vacuous opinion")
        void isMaximizedUncertainty() {
            assertEquals("false", run(A + ".isMaximizedUncertainty()").toString());
        }

        @Test
        @DisplayName("isCertain/isUncertain: threshold comparisons against certainty/uncertainty")
        void certainAndUncertainThresholds() {
            // certainty() is 0.8 for A.
            assertEquals("false", run(A + ".isCertain(0.9)").toString());
            assertEquals("true", run(A + ".isCertain(0.1)").toString());
            // uncertainty is 0.2 for A.
            assertEquals("false", run(A + ".isUncertain(0.1)").toString());
            assertEquals("true", run(A + ".isUncertain(0.9)").toString());
        }
    }

    @Nested
    @DisplayName("conversions and pairwise operations")
    class ConversionsAndPairwise {

        @Test
        @DisplayName("toUBoolean converts to a UBoolean at the opinion's projection")
        void toUBoolean() {
            assertEquals("UBoolean(true, 0.6)", run(A + ".toUBoolean()").toString());
        }

        @Test
        @DisplayName("toString round-trips the SBoolean's own literal form")
        void toStringOp() {
            assertEquals("'SBoolean(0.5, 0.3, 0.2, 0.5)'", run(A + ".toString()").toString());
        }

        @Test
        @DisplayName("projectiveDistance answers a Real")
        void projectiveDistance() {
            assertEquals("0.28", run(A + ".projectiveDistance(" + B + ")").toString());
        }
    }

    /**
     * Regression tests for the declared-type fix applied to both operations this stage (see
     * StandardOperationsSBoolean.java's CONJUNCTIVE_CERTAINTY/DEGREE_OF_CONFLICT comments, and
     * MetamorphicRelationsTest's M6SimplexClosure exclusion note for the full history). Before the
     * fix, both operations' matches() declared SBoolean while eval() always returned a Real; the
     * static type lie let `.belief()` chained onto the result type-check and then crash at runtime
     * with a NullPointerException, because SBooleanValue.valueOf(Value) silently returns null for a
     * RealValue argument. After the fix, both operations are statically Real, so `.belief()` is
     * itself a compile error against their result -- exactly as it should be for a Real.
     */
    @Nested
    @DisplayName("conjunctiveCertainty/degreeOfConflict: declared-type fix and its regression")
    class ConjunctiveCertaintyDegreeOfConflict {

        @Test
        @DisplayName("both now have a static and runtime type of Real, not SBoolean")
        void staticTypeIsReal() {
            StringWriter err = new StringWriter();
            Expression cc = OCLCompiler.compileExpression(new ModelFactory().createModel("m"),
                    A + ".conjunctiveCertainty(" + B + ")", "test", new PrintWriter(err), new VarBindings());
            Expression doc = OCLCompiler.compileExpression(new ModelFactory().createModel("m"),
                    A + ".degreeOfConflict(" + B + ")", "test", new PrintWriter(err), new VarBindings());
            assertEquals("Real", cc.type().toString());
            assertEquals("Real", doc.type().toString());
            assertEquals("0.56", run(A + ".conjunctiveCertainty(" + B + ")").toString());
            assertEquals("0.1568", run(A + ".degreeOfConflict(" + B + ")").toString());
        }

        @Test
        @DisplayName("chaining an SBoolean-only operation onto the result is now a compile error, not a runtime crash")
        void chainingSBooleanOpNoLongerCompiles() {
            StringWriter err = new StringWriter();
            Expression e = OCLCompiler.compileExpression(new ModelFactory().createModel("m"),
                    A + ".conjunctiveCertainty(" + B + ").belief()", "test",
                    new PrintWriter(err), new VarBindings());
            assertTrue(e == null, "belief() has no Real-typed overload, so this must fail to compile now, "
                    + "not throw a NullPointerException at eval time as it did before this stage's fix");
        }
    }
}
