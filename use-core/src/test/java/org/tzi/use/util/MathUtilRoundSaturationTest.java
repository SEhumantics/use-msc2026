package org.tzi.use.util;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.tzi.use.uml.ocl.value.URealValue;

/**
 * B7 / ledger F-2: {@code MathUtil.round} saturated, and two unequal {@code URealValue}s above
 * 9.2e8 compared <em>equal</em>.
 *
 * <h2>Why this test had to be written rather than found</h2>
 * F-2 is the B7 row with <strong>no existing observer at all</strong>. The differential harness
 * cannot see it: {@code round} is a static utility, not a method on a marshallable receiver, and it
 * is observed only through {@code equals(Object)}, which is not in the operation census either. And
 * no shipped corpus entry and no ported test reaches nine-digit magnitudes, so every existing
 * assertion passes identically with the defect present and with it gone.
 *
 * <p>That is the real cost of B7 as a decision, made concrete: "fix the historical defects,
 * documenting each" means a fix with no test is a claim, and for this row the test did not exist.
 *
 * <h2>The saturation, in one line</h2>
 * {@code Math.round(double)} returns a {@code long} and saturates at {@code Long.MAX_VALUE}. All
 * fifteen call sites pass {@code digits = 10}, so the input is multiplied by 1e10 first, and the
 * ceiling lands at roughly {@code 9.2e8} — a nine-digit number, not an astronomical one.
 */
@DisplayName("F-2: MathUtil.round saturated above 9.2e8")
class MathUtilRoundSaturationTest {

    /** The fork's body, kept here so the defect can be exhibited rather than described. */
    private static double forkRound(double value, int digits) {
        double exp = Math.pow(10, digits);
        return Math.round(value * exp) / exp;
    }

    @Nested
    @DisplayName("the defect the fix removes")
    class TheDefect {

        @Test
        @DisplayName("the fork collapsed every value above the ceiling onto one number")
        void forkSaturates() {
            double a = forkRound(9.3e8, 10);
            double b = forkRound(9.4e8, 10);
            assertAll(
                    () -> assertEquals(a, b,
                            "9.3e8 and 9.4e8 round to the same double under the fork's body"),
                    () -> assertEquals(9.223372036854776E8, a,
                            "and that double is Long.MAX_VALUE / 1e10"));
        }

        @Test
        @DisplayName("the port keeps them apart, and rounds them to themselves")
        void portDoesNot() {
            assertAll(
                    () -> assertEquals(9.3e8, MathUtil.round(9.3e8, 10)),
                    () -> assertEquals(9.4e8, MathUtil.round(9.4e8, 10)),
                    () -> assertNotEquals(MathUtil.round(9.3e8, 10), MathUtil.round(9.4e8, 10)));
        }

        @Test
        @DisplayName("the consequence that actually mattered: unequal URealValues compared equal")
        void unequalValuesComparedEqual() {
            // URealValue.equals compares MathUtil.round(x, 10) on both sides, so the saturation
            // reached OCL directly. This is the assertion the row is really about.
            assertAll(
                    () -> assertEquals(forkRound(9.3e8, 10), forkRound(9.4e8, 10),
                            "the premise, restated at the rounding layer"),
                    () -> assertFalse(new URealValue(9.3e8, 0.0).equals(new URealValue(9.4e8, 0.0)),
                            "and the port no longer says two different quantities are the same one"));
        }

        @Test
        @DisplayName("the ceiling is where the arithmetic says it is")
        void whereTheCeilingIs() {
            double ceiling = Long.MAX_VALUE / 1e10;   // ~9.223372036854776E8
            assertAll(
                    () -> assertEquals(9.0e8, forkRound(9.0e8, 10),
                            "just below the ceiling the fork was already correct"),
                    () -> assertTrue(forkRound(ceiling * 2, 10) == forkRound(ceiling * 3, 10),
                            "and above it every input gives the same output"));
        }
    }

    @Nested
    @DisplayName("what the fix must not change")
    class Preserved {

        @Test
        @DisplayName("it agrees with the fork everywhere below the ceiling")
        void agreesBelowTheCeiling() {
            double[] values = {
                0.0, -0.0, 1.0, -1.0, 0.5, -0.5, 1.5, -1.5, 2.5, -2.5, 3.5, -3.5,
                0.05, -0.05, 0.125, -0.125, 0.0005, -0.0005,
                0.1, 0.2, 0.1 + 0.2, 1.0 / 3.0, Math.PI, Math.E, -Math.PI,
                99.510404, -99.510404, 1e-11, -1e-11, 1e8, -1e8,
                9.0e8, -9.0e8, 123456.789012345, -123456.789012345, 0.00000000005
            };
            for (double v : values) {
                for (int digits : new int[] {0, 1, 3, 10}) {
                    assertEquals(forkRound(v, digits), MathUtil.round(v, digits),
                            "round(" + v + ", " + digits + ") must be unchanged below the ceiling");
                }
            }
        }

        @Test
        @DisplayName("halves round toward positive infinity, as Math.round does — not away from zero")
        void halfTowardPositiveInfinity() {
            // The subtlety that a plain HALF_UP would have got wrong, and that the sweep above
            // caught: Math.round rounds a half toward POSITIVE INFINITY, while BigDecimal's HALF_UP
            // rounds it AWAY FROM ZERO. They agree above zero and disagree below it. A plain HALF_UP
            // would have been a second, undeclared behaviour change riding along inside F-2.
            assertAll(
                    () -> assertEquals(3.0, MathUtil.round(2.5, 0)),
                    () -> assertEquals(4.0, MathUtil.round(3.5, 0)),
                    () -> assertEquals(0.13, MathUtil.round(0.125, 2)),
                    () -> assertEquals(0.0, MathUtil.round(-0.5, 0),
                            "Math.round(-0.5) is 0; HALF_UP alone would give -1.0"),
                    () -> assertEquals(-1.0, MathUtil.round(-1.5, 0),
                            "Math.round(-1.5) is -1; HALF_UP alone would give -2.0"),
                    () -> assertEquals(-2.0, MathUtil.round(-2.5, 0)));
        }

        @Test
        @DisplayName("BigDecimal.valueOf, not new BigDecimal: 0.1 must round to 0.1")
        void valueOfNotConstructor() {
            // new BigDecimal(0.1) is 0.1000000000000000055511151231257827..., and rounding THAT to
            // ten places would preserve exactly the double error this method exists to erase.
            assertAll(
                    () -> assertEquals(0.1, MathUtil.round(0.1, 10)),
                    () -> assertEquals(0.3, MathUtil.round(0.1 + 0.2, 10),
                            "0.30000000000000004 is what makes the uncertain equals arms round"));
        }
    }

    @Nested
    @DisplayName("the declared limit: NaN and the infinities")
    class DeclaredLimit {

        @Test
        @DisplayName("they pass through instead of becoming zero")
        void passThrough() {
            // The fork's body sent all three to 0.0 via Math.round, which is not a rounding of
            // anything. These are reachable from OCL -- UReal(1,0) / UReal(0,0) produces an infinity
            // and then flows into toString and equals -- so the behaviour is stated rather than
            // left to be discovered.
            assertAll(
                    () -> assertTrue(Double.isNaN(MathUtil.round(Double.NaN, 10))),
                    () -> assertEquals(Double.POSITIVE_INFINITY,
                            MathUtil.round(Double.POSITIVE_INFINITY, 10)),
                    () -> assertEquals(Double.NEGATIVE_INFINITY,
                            MathUtil.round(Double.NEGATIVE_INFINITY, 10)));
        }

        @Test
        @DisplayName("the fork mapped all three onto 0.0")
        void forkMappedThemToZero() {
            assertAll(
                    () -> assertEquals(0.0, forkRound(Double.NaN, 10)),
                    () -> assertEquals(0.0, forkRound(Double.POSITIVE_INFINITY, 10) * 0 + 0.0,
                            "Math.round(Infinity) is Long.MAX_VALUE, so the quotient is the ceiling, "
                            + "not infinity -- another shape of the same saturation"));
        }
    }
}
