package org.tzi.use.uncertainty.differential;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.tzi.use.uml.ocl.value.BooleanValue;
import org.tzi.use.uml.ocl.value.IntegerValue;
import org.tzi.use.uml.ocl.value.RealValue;
import org.tzi.use.uml.ocl.value.SBooleanValue;
import org.tzi.use.uml.ocl.value.StringValue;
import org.tzi.use.uml.ocl.value.UBooleanValue;
import org.tzi.use.uml.ocl.value.UIntegerValue;
import org.tzi.use.uml.ocl.value.URealValue;
import org.tzi.use.uml.ocl.value.UStringValue;
import org.tzi.use.uml.ocl.value.Value;

/**
 * Evidence for the B7 corrections the differential sweep <strong>cannot see</strong>.
 *
 * <h2>Why these need their own test</h2>
 * The census {@code PortedFidelitySweepTest} drives comes from
 * {@code UnwrittenPortInvariantTest.reachableOperations}, which admits a method only when every
 * parameter is a {@code Value}, {@code int}, {@code double} or {@code float}.
 * {@code equals(Object)} takes an {@code Object}. So <strong>not one {@code equals} override is in
 * the 355-operation census</strong>, and M-11, M-8, M-10 and F-4 produce no row there however wrong
 * or right they are.
 *
 * <p>That is a correction to {@code docs/port2/b7-fix-plan.md} section 4.1, which lists those four
 * among the nine rows "visible to the sweep ({@code DIFFER} expected)". They are not visible, and a
 * stage quoting the sweep as evidence for them would be quoting a population that does not exist.
 * Recorded here rather than only in the plan, because this is the file a reader arrives at when
 * asking what evidence those four rows have.
 *
 * <h2>Why each test drives the fork too</h2>
 * A test that only asserts what the port does now is evidence of the port's behaviour, not evidence
 * of a <em>correction</em>: it would pass identically if the fork had always been right and the
 * ledger row were fiction. Every test below therefore drives the historical jar through
 * {@link HistoricalOracle#toHistorical} and asserts the fork's answer as well — so it fails if the
 * defect was never there, and fails if the fix stops working.
 *
 * <p>Test-scoped. Not part of the product.
 */
@DisplayName("B7 corrections the differential sweep cannot see")
class B7CorrectionsTest {

    private static HistoricalOracle oracle;

    @BeforeAll
    static void openOracle() {
        oracle = HistoricalOracle.open();
    }

    @AfterAll
    static void closeOracle() {
        if (oracle != null) {
            oracle.close();
        }
    }

    /** The fork's answer to {@code a.equals(b)}, both operands built inside the historical jar. */
    private static boolean forkEquals(UValue a, UValue b) {
        return oracle.toHistorical(a).equals(oracle.toHistorical(b));
    }

    /** The fork's answer to {@code a.compareTo(b)}, likewise. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static int forkCompare(UValue a, UValue b) {
        return ((Comparable) oracle.toHistorical(a)).compareTo(oracle.toHistorical(b));
    }

    private static int forkHash(UValue a) {
        return oracle.toHistorical(a).hashCode();
    }

    // ================================================================ M-11

    @Nested
    @DisplayName("M-11: UStringValue.equals was the constant false")
    class M11 {

        @Test
        @DisplayName("the fork breaks reflexivity; the port does not")
        void reflexivity() {
            UValue x = UValue.uString("x", 1.0);
            assertAll(
                    () -> assertFalse(forkEquals(x, x),
                            "the fork compares a java.lang.String against a uDataTypes.UString, which "
                            + "is false for every argument, and ANDs it with a comparison of the "
                            + "receiver's confidence to itself. a.equals(a) is false."),
                    () -> assertEquals(new UStringValue("x", 1.0), new UStringValue("x", 1.0),
                            "the port delegates to UString.equals"));
        }

        @Test
        @DisplayName("the fork's UStringValue cannot be found in a HashSet; the port's can")
        void hashSetMembership() {
            // The consequence that actually costs something. hashCode() already delegated correctly
            // in the fork, so the two were never consistent: values landed in the right bucket and
            // were then rejected by equals.
            UValue x = UValue.uString("x", 1.0);
            Set<Object> forkSet = new HashSet<>();
            forkSet.add(oracle.toHistorical(x));
            assertFalse(forkSet.contains(oracle.toHistorical(x)),
                    "a HashSet cannot find the fork's UStringValue even though the hash matches");

            Set<Value> portedSet = new HashSet<>();
            portedSet.add(new UStringValue("x", 1.0));
            assertTrue(portedSet.contains(new UStringValue("x", 1.0)));
        }

        @Test
        @DisplayName("confidence still discriminates: the fix is not 'return true'")
        void confidenceIsRead() {
            assertNotEquals(new UStringValue("x", 1.0), new UStringValue("x", 0.5));
            assertNotEquals(new UStringValue("x", 1.0), new UStringValue("y", 1.0));
        }

        @Test
        @DisplayName("the declared widening: UString('x',1.0) = 'x' is now true")
        void declaredWidening() {
            // valueOf lifts a StringValue to confidence 1.0, so the corrected comparison finds them
            // equal where the fork found nothing equal to anything. Declared in the javadoc on
            // UStringValue.equals and in b7-fix-plan.md section 2 M-11.
            assertTrue(new UStringValue("x", 1.0).equals(new StringValue("x")));
        }

        @Test
        @DisplayName("the declared residual: the relation stays asymmetric across String/UString")
        void declaredResidualAsymmetry() {
            // StringValue.equals has no UStringValue arm and is explicitly out of scope
            // (specification.md section 1.8). Asserted so that the residual is a measured fact in the
            // suite rather than a sentence in a document -- if someone later fixes StringValue, this
            // test fails and the residual list gets updated instead of quietly going stale.
            assertFalse(new StringValue("x").equals(new UStringValue("x", 1.0)),
                    "b7-fix-plan.md section 7.2 item 2");
        }
    }

    // ================================================================ M-8

    @Nested
    @DisplayName("M-8: UBooleanValue.equals had a dead conjunct")
    class M8 {

        @Test
        @DisplayName("UBoolean(true, 0) equals Boolean false in the port, not in the fork")
        void deadConjunct() {
            UValue ub = UValue.uBoolean(true, 0.0);
            UValue f = UValue.bool(false);
            assertAll(
                    () -> assertFalse(forkEquals(ub, f),
                            "the fork's second disjunct requires !this.value(), but valueOf "
                            + "normalises every value to true, so it can never hold"),
                    () -> assertTrue(UBooleanValue.valueOf(true, 0.0).equals(BooleanValue.FALSE)));
        }

        @Test
        @DisplayName("the surviving disjunct is untouched: UBoolean(true, 1) equals Boolean true")
        void trueArmUnchanged() {
            UValue ub = UValue.uBoolean(true, 1.0);
            UValue t = UValue.bool(true);
            assertAll(
                    () -> assertTrue(forkEquals(ub, t), "the fork already got this one right"),
                    () -> assertTrue(UBooleanValue.valueOf(true, 1.0).equals(BooleanValue.TRUE)));
        }

        @Test
        @DisplayName("the fix is not 'return true': an intermediate probability equals neither")
        void intermediateProbability() {
            assertAll(
                    () -> assertFalse(UBooleanValue.valueOf(true, 0.5).equals(BooleanValue.FALSE)),
                    () -> assertFalse(UBooleanValue.valueOf(true, 0.5).equals(BooleanValue.TRUE)));
        }
    }

    // ================================================================ M-10 and F-4

    @Nested
    @DisplayName("M-10 and F-4: URealValue.equals had no UIntegerValue arm and did not round")
    class M10AndF4 {

        @Test
        @DisplayName("M-10: UInteger(2,0.5) = UReal(2,0.5) is now true, in both directions")
        void crossTypeEquality() {
            UValue ui = UValue.uInteger(2, 0.5);
            UValue ur = UValue.uReal(2.0, 0.5);
            assertAll(
                    () -> assertFalse(forkEquals(ui, ur),
                            "UIntegerValue.equals delegates to URealValue.equals, whose arm list has "
                            + "no UIntegerValue case, so it fell through to false"),
                    () -> assertFalse(forkEquals(ur, ui), "and the same in the other direction"),
                    () -> assertTrue(new UIntegerValue(2, 0.5).equals(new URealValue(2.0, 0.5))),
                    () -> assertTrue(new URealValue(2.0, 0.5).equals(new UIntegerValue(2, 0.5))));
        }

        @Test
        @DisplayName("M-10 + F-3 together: the new equal pairs hash alike")
        void newEqualPairsHashAlike() {
            // Bundle B. M-10 creates equal pairs across the two types; if hashCode were not
            // contract-correct at the same moment they would land in different buckets and set
            // membership would depend on insertion order -- a worse defect than the one being fixed.
            UIntegerValue ui = new UIntegerValue(2, 0.5);
            URealValue ur = new URealValue(2.0, 0.5);
            assertAll(
                    () -> assertTrue(ui.equals(ur)),
                    () -> assertEquals(ur.hashCode(), ui.hashCode(),
                            "b7-fix-plan.md section 7.1 bundle B"));
        }

        @Test
        @DisplayName("F-4: the cross-type arms now round, so 0.1+0.2 equals 0.3")
        void crossTypeRounding() {
            double sum = 0.1 + 0.2;   // 0.30000000000000004
            assertNotEquals(0.3, sum, "the premise: these differ in the last bits");
            UValue ur = UValue.uReal(sum, 0.0);
            UValue r = UValue.real(0.3);
            assertAll(
                    () -> assertFalse(forkEquals(ur, r),
                            "the fork's RealValue arm used raw ==, three lines below a URealValue arm "
                            + "that rounds to ten decimals for exactly this reason"),
                    () -> assertTrue(new URealValue(sum, 0.0).equals(new RealValue(0.3))));
        }

        @Test
        @DisplayName("F-4 is a widening only: an uncertain value still equals no crisp one")
        void wideningIsOneWay() {
            assertFalse(new URealValue(2.0, 0.5).equals(new RealValue(2.0)),
                    "uncertainty() != 0 still refuses, so rounding cannot turn true into false");
            assertFalse(new URealValue(2.0, 0.0).equals(new IntegerValue(3)));
        }

        @Test
        @DisplayName("the declared residual: RealValue.equals still has no URealValue arm")
        void declaredResidualAsymmetry() {
            assertFalse(new RealValue(0.3).equals(new URealValue(0.3, 0.0)),
                    "b7-fix-plan.md section 7.2 item 1");
        }
    }

    // ================================================================ F-3 and F-10

    @Nested
    @DisplayName("F-3 and F-10: the hashCode/equals contract")
    class HashContract {

        /** Values chosen to straddle the rounding boundary and the zero-uncertainty case. */
        private List<Value> corpus() {
            List<Value> out = new ArrayList<>();
            for (int n : new int[] {0, 1, 2, -3, 42}) {
                out.add(new UIntegerValue(n, 0.0));
                out.add(new UIntegerValue(n, 0.5));
                out.add(new URealValue(n, 0.0));
                out.add(new URealValue(n, 0.5));
            }
            out.add(new URealValue(0.1 + 0.2, 0.0));
            out.add(new URealValue(0.3, 0.0));
            out.add(new URealValue(-0.0, 0.0));
            out.add(new URealValue(0.0, 0.0));
            out.add(new URealValue(2.0, 1e-15));
            out.add(new URealValue(2.0, 0.0));
            return out;
        }

        @Test
        @DisplayName("equal implies same hash, over every pair in the corpus")
        void contractHolds() {
            List<Value> values = corpus();
            List<String> violations = new ArrayList<>();
            for (Value a : values) {
                for (Value b : values) {
                    if (a.equals(b) && a.hashCode() != b.hashCode()) {
                        violations.add(a + " equals " + b + " but hashes " + a.hashCode()
                                + " vs " + b.hashCode());
                    }
                }
            }
            assertTrue(violations.isEmpty(), "hashCode/equals contract violated: " + violations);
        }

        @Test
        @DisplayName("the fork violated it on exactly the pairs F-3 names")
        void forkViolatedIt() {
            // -0.0 and 0.0: the fork's equals rounds both to 0.0 and calls them equal; its hashCode
            // hashes them unrounded, and Double.hashCode(-0.0) is Integer.MIN_VALUE.
            UValue negZero = UValue.uReal(-0.0, 0.0);
            UValue posZero = UValue.uReal(0.0, 0.0);
            assertAll(
                    () -> assertTrue(forkEquals(negZero, posZero),
                            "the fork's equals already called these equal"),
                    () -> assertNotEquals(forkHash(negZero), forkHash(posZero),
                            "and hashed them into different buckets"),
                    () -> assertEquals(new URealValue(0.0, 0.0).hashCode(),
                            new URealValue(-0.0, 0.0).hashCode(),
                            "the port rounds inside hashCode, so they agree"));
        }

        @Test
        @DisplayName("F-10: every certain UInteger hashed to zero in the fork")
        void forkCollapsedEveryCertainUInteger() {
            List<Integer> forkHashes = new ArrayList<>();
            List<Integer> portedHashes = new ArrayList<>();
            for (int n : new int[] {0, 1, 2, -3, 42, 1000}) {
                forkHashes.add(forkHash(UValue.uInteger(n, 0.0)));
                portedHashes.add(new UIntegerValue(n, 0.0).hashCode());
            }
            assertAll(
                    () -> assertEquals(List.of(0, 0, 0, 0, 0, 0), forkHashes,
                            "Double.hashCode(0.0) is 0 and the fork MULTIPLIES by it"),
                    () -> assertEquals(6, new HashSet<>(portedHashes).size(),
                            "the port gives six distinct hashes: " + portedHashes));
        }
    }

    // ================================================================ M-9 and bundle A

    @Nested
    @DisplayName("M-9 and bundle A: UInteger against UReal compared equal in both directions")
    class M9 {

        @Test
        @DisplayName("the fork tied them; the port orders them, antisymmetrically")
        void ordering() {
            UValue ui = UValue.uInteger(2, 0.0);
            UValue ur = UValue.uReal(3.0, 0.0);
            assertAll(
                    () -> assertEquals(0, forkCompare(ui, ur),
                            "UIntegerValue.compareTo delegates without negating, into a "
                            + "URealValue.compareTo with no UIntegerValue arm, so the composite is 0"),
                    () -> assertEquals(0, forkCompare(ur, ui), "and 0 in the other direction too"),
                    () -> assertTrue(new UIntegerValue(2, 0.0).compareTo(new URealValue(3.0, 0.0)) < 0),
                    () -> assertTrue(new URealValue(3.0, 0.0).compareTo(new UIntegerValue(2, 0.0)) > 0));
        }

        @Test
        @DisplayName("the negation alone would have been a no-op: the new arm is what does the work")
        void theArmIsLoadBearing() {
            // URealValue.compareTo is the side that gained the UIntegerValue arm. If it still fell
            // through, this would be 0 and so would its negation -- which is the whole reason M-9 and
            // the arm had to land together (b7-fix-plan.md section 7.1 bundle A).
            assertNotEquals(0, new URealValue(3.0, 0.0).compareTo(new UIntegerValue(2, 0.0)));
        }

        @Test
        @DisplayName("equal values still compare equal")
        void equalStillTies() {
            assertEquals(0, new UIntegerValue(2, 0.0).compareTo(new URealValue(2.0, 0.0)));
        }
    }

    // ================================================================ M-12

    @Nested
    @DisplayName("M-12: UStringValue.compareTo compared a bare string against a wrapper rendering")
    class M12 {

        @Test
        @DisplayName("the port compares bare against bare")
        void bareAgainstBare() {
            assertAll(
                    () -> assertEquals("x".compareTo("y"),
                            new UStringValue("x", 1.0).compareTo(new StringValue("y"))),
                    () -> assertEquals(0,
                            new UStringValue("x", 1.0).compareTo(new StringValue("x"))));
        }

        @Test
        @DisplayName("the fork compared against the literal text \"UString('x', 1.0)\"")
        void forkComparedAgainstTheRendering() {
            int fork = forkCompare(UValue.uString("x", 1.0), UValue.string("x"));
            assertAll(
                    () -> assertNotEquals(0, fork,
                            "the fork could not find a string equal to its own UString"),
                    () -> assertEquals("x".compareTo("UString('x', 1.0)"), fork,
                            "and this is exactly what it was comparing against"));
        }

        @Test
        @DisplayName("the untouched half: UString against UString still routes through toString()")
        void ustringAgainstUstringUnchanged() {
            // The guard is !(o instanceof StringValue), so a UStringValue argument never reaches the
            // corrected line. Left alone deliberately -- widening the guard is a separate decision.
            // b7-fix-plan.md section 2 M-12.
            assertEquals(new UStringValue("x", 1.0).toString()
                            .compareTo(new UStringValue("y", 1.0).toString()),
                    new UStringValue("x", 1.0).compareTo(new UStringValue("y", 1.0)));
        }
    }

    // ================================================================ M-18

    @Nested
    @DisplayName("M-18: SBooleanValue.compareTo was 'return 0'")
    class M18 {

        private SBooleanValue opinion(double belief) {
            return new SBooleanValue.Builder()
                    .belief(belief).disbelief(1 - belief).uncertainty(0).agent(0.5).build();
        }

        @Test
        @DisplayName("the port orders opinions; the fork tied everything, including a String")
        void ordering() {
            SBooleanValue low = opinion(0.2);
            SBooleanValue high = opinion(0.8);
            assertAll(
                    () -> assertTrue(low.compareTo(high) < 0),
                    () -> assertTrue(high.compareTo(low) > 0),
                    () -> assertEquals(0, low.compareTo(opinion(0.2))),
                    () -> assertTrue(low.compareTo(new StringValue("anything")) != 0,
                            "the fork answered 0 here, which claimed an opinion equals a string"));
        }

        @Test
        @DisplayName("it is consistent with equals")
        void consistentWithEquals() {
            SBooleanValue a = opinion(0.4);
            SBooleanValue b = opinion(0.4);
            assertAll(
                    () -> assertEquals(a, b),
                    () -> assertEquals(0, a.compareTo(b)));
        }

        @Test
        @DisplayName("a concrete intransitivity witness in the library comparator")
        void intransitivityWitness() {
            // THE reason this does not delegate to uDataTypes.SBoolean.compareTo, stated as the
            // property rather than as one of its consequences.
            //
            // A first draft asserted that sorting 40 such opinions throws
            // IllegalArgumentException("Comparison method violates its general contract"). It does
            // not, reliably: TimSort checks the contract only when a merge happens to expose the
            // inconsistency, so whether it throws depends on the input permutation. Asserting a
            // crash that may not happen is a test that passes for the wrong reason on the day it
            // passes. The intransitivity is deterministic, so that is what is asserted.
            //
            // a ~ b and b ~ c but a < c. Spacing chosen so each adjacent L1 distance (2 * 0.0004 =
            // 0.0008) is under the tolerance and the outer one (2 * 0.0008 = 0.0016) is over it.
            SBooleanValue a = opinion(0.5000);
            SBooleanValue b = opinion(0.5004);
            SBooleanValue c = opinion(0.5008);
            assertAll(
                    () -> assertEquals(0, libraryCompare(a, b)),
                    () -> assertEquals(0, libraryCompare(b, c)),
                    () -> assertNotEquals(0, libraryCompare(a, c),
                            "a == b and b == c but a != c: not an equivalence relation, so not a "
                            + "valid Comparator, so Collections.sort over it is undefined"),
                    // The port's comparator has no tolerance, so the same three are strictly ordered.
                    () -> assertTrue(a.compareTo(b) < 0),
                    () -> assertTrue(b.compareTo(c) < 0),
                    () -> assertTrue(a.compareTo(c) < 0));
        }

        @Test
        @DisplayName("sorting 40 near-identical opinions with the port's comparator is well defined")
        void sortingIsWellDefined() {
            List<SBooleanValue> values = new ArrayList<>();
            for (int i = 0; i < 40; i++) {
                values.add(opinion(0.5 + i * 0.0002));
            }
            Collections.shuffle(values, new java.util.Random(20260819L));
            Collections.sort(values);
            for (int i = 1; i < values.size(); i++) {
                assertTrue(values.get(i - 1).compareTo(values.get(i)) <= 0,
                        "the port's comparator produces a sorted list at 40 elements, which is above "
                        + "the 32-element threshold where TimSort starts checking the contract");
            }
        }

        /** {@code uDataTypes.SBoolean.compareTo}, reimplemented so its defect can be exhibited. */
        private int libraryCompare(SBooleanValue a, SBooleanValue b) {
            double x = Math.abs(a.belief().value() - b.belief().value())
                    + Math.abs(a.disbelief().value() - b.disbelief().value())
                    + Math.abs(a.uncertainty().value() - b.uncertainty().value())
                    + Math.abs(a.baseRate().value() - b.baseRate().value());
            if (x < 0.001D) {
                return 0;
            }
            return a.projection().value() - b.projection().value() < 0 ? -1 : 1;
        }
    }
}
