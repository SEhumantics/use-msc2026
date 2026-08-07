package org.tzi.use.uml.ocl.value;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.function.IntFunction;

import org.junit.jupiter.api.Test;
import org.tzi.use.uml.ocl.type.TypeFactory;

/**
 * {@link Value#compareTo} has to satisfy the {@link Comparable} contract for
 * every uncertain kind, because every collection rendering goes through
 * {@link CollectionValue#getSortedElements()}, which calls
 * {@code Collections.sort}. TimSort verifies the contract once a list is long
 * enough and throws {@code IllegalArgumentException} when it does not hold.
 *
 * <p>A probabilistic comparison is not transitive: UReal(0,3) overlaps
 * UReal(3,3) and UReal(3,3) overlaps UReal(6,3), but UReal(0,3) and UReal(6,3)
 * do not. Ordering therefore has to go by representation, and these tests pin
 * that.
 */
class UncertainValueOrderingTest {

    /** Long enough that TimSort runs its merge checks rather than a binary insertion sort. */
    private static final int LENGTH = 64;
    private static final int TRIALS = 500;

    private static void assertSortable(String what, IntFunction<Value> generator) {
        for (int trial = 0; trial < TRIALS; trial++) {
            Random random = new Random(trial);
            List<Value> values = new ArrayList<>(LENGTH);
            for (int i = 0; i < LENGTH; i++) {
                values.add(generator.apply(random.nextInt(40)));
            }
            int seed = trial;
            assertDoesNotThrow(() -> Collections.sort(values),
                    what + " violates the comparison contract for seed " + seed);
        }
    }

    @Test
    void overlappingUncertainRealsSortWithoutViolatingTheContract() {
        assertSortable("UReal", k -> new URealValue(k % 20, 1 + k % 5));
    }

    @Test
    void overlappingUncertainIntegersSortWithoutViolatingTheContract() {
        assertSortable("UInteger", k -> new UIntegerValue(k % 20, 1 + k % 5));
    }

    @Test
    void mixedCertainAndUncertainNumbersSortWithoutViolatingTheContract() {
        assertSortable("UReal with Real",
                k -> k % 2 == 0 ? new URealValue(k % 20, 1 + k % 5) : new RealValue(k % 20));
        assertSortable("UReal with Integer",
                k -> k % 2 == 0 ? new URealValue(k % 20, 1 + k % 5) : IntegerValue.valueOf(k % 20));
        assertSortable("UReal with UInteger",
                k -> k % 2 == 0 ? new URealValue(k % 20, 1 + k % 5) : new UIntegerValue(k % 20, 1 + k % 5));
    }

    @Test
    void uncertainBooleansSortWithoutViolatingTheContract() {
        assertSortable("UBoolean", k -> UBooleanValue.probability(k / 40.0));
        assertSortable("UBoolean with Boolean",
                k -> k % 2 == 0 ? UBooleanValue.probability(k / 40.0) : BooleanValue.get(k % 3 == 0));
    }

    @Test
    void uncertainStringsSortWithoutViolatingTheContract() {
        assertSortable("UString", k -> new UStringValue("s" + (k % 20), (k % 10) / 10.0));
        assertSortable("UString with String",
                k -> k % 2 == 0 ? new UStringValue("s" + (k % 20), (k % 10) / 10.0) : new StringValue("s" + (k % 20)));
    }

    @Test
    void subjectiveOpinionsSortWithoutViolatingTheContract() {
        assertSortable("SBoolean",
                k -> new SBooleanValue((k % 10) / 10.0, (9 - k % 10) / 10.0, 0.1, 0.5));
    }

    /** The intransitive triple that used to break the sort, stated directly. */
    @Test
    void orderingIsTransitiveForOverlappingDistributions() {
        URealValue low = new URealValue(0, 3);
        URealValue middle = new URealValue(3, 3);
        URealValue high = new URealValue(6, 3);

        assertTrue(low.compareTo(middle) < 0, "low before middle");
        assertTrue(middle.compareTo(high) < 0, "middle before high");
        assertTrue(low.compareTo(high) < 0, "low before high");

        // ... while the probabilistic comparison still calls all three pairs equal.
        assertTrue(low.uEquals(middle).toBoolean().value(), "low overlaps middle");
        assertTrue(middle.uEquals(high).toBoolean().value(), "middle overlaps high");
    }

    @Test
    void orderingIsAntisymmetricAndAgreesWithEquality() {
        URealValue a = new URealValue(2, 0.5);
        URealValue b = new URealValue(2, 0.25);
        UIntegerValue c = new UIntegerValue(2, 0.5);

        assertEquals(-a.compareTo(b), b.compareTo(a), "UReal ordering is antisymmetric");
        assertTrue(a.compareTo(b) > 0, "the larger uncertainty orders later at equal value");
        assertEquals(0, a.compareTo(c), "UInteger(2,0.5) ties with UReal(2.0,0.5)");
        assertEquals(0, c.compareTo(a), "and the tie holds in both directions");
        assertEquals(a, c, "which is what equality says too");
    }

    @Test
    void undefinedOrdersBeforeEveryUncertainValue() {
        assertTrue(new URealValue(0, 0).compareTo(UndefinedValue.instance) > 0);
        assertTrue(new UIntegerValue(0, 0).compareTo(UndefinedValue.instance) > 0);
        assertTrue(new UStringValue("", 1).compareTo(UndefinedValue.instance) > 0);
        assertTrue(SBooleanValue.TRUE.compareTo(UndefinedValue.instance) > 0);
    }

    /** Every kind an OclAny collection can hold next to an uncertain value. */
    private static List<Value> everyKind(Random random) {
        List<Value> values = new ArrayList<>();
        for (int i = 0; i < LENGTH; i++) {
            switch (random.nextInt(9)) {
            case 0 -> values.add(new URealValue(random.nextInt(20) - 10, random.nextInt(4)));
            case 1 -> values.add(new UIntegerValue(random.nextInt(20) - 10, random.nextInt(4)));
            case 2 -> values.add(IntegerValue.valueOf(random.nextInt(20) - 10));
            case 3 -> values.add(new RealValue(random.nextInt(20) - 10));
            case 4 -> values.add(UBooleanValue.probability(random.nextInt(11) / 10.0));
            case 5 -> values.add(BooleanValue.get(random.nextBoolean()));
            case 6 -> values.add(new UStringValue(String.valueOf((char) ('a' + random.nextInt(5))), 0.9));
            case 7 -> values.add(new StringValue(String.valueOf((char) ('a' + random.nextInt(5)))));
            default -> {
                double belief = random.nextInt(11) / 10.0;
                values.add(new SBooleanValue(belief, 0, 1 - belief, 0.5));
            }
            }
        }
        return values;
    }

    /**
     * A collection typed OclAny holds values of unrelated kinds, and rendering it
     * sorts them. compareTo alone does not order those transitively -- the
     * numeric kinds compare by value, so UReal(1,0) ties with 1 although they
     * render differently, and against a third kind they then disagree -- so
     * {@link CollectionValue#getSortedElements()} groups by kind first. Without
     * that, rendering threw IllegalArgumentException.
     */
    @Test
    void collectionsOfUnrelatedKindsRenderWithoutViolatingTheContract() {
        for (int trial = 0; trial < TRIALS; trial++) {
            List<Value> values = everyKind(new Random(trial));
            CollectionValue collection =
                    new SequenceValue(TypeFactory.mkOclAny(), values);
            int seed = trial;
            List<Value> sorted = assertDoesNotThrow(collection::getSortedElements,
                    "rendering a mixed-kind collection failed for seed " + seed);
            assertEquals(values.size(), sorted.size(), "sorting must not drop elements");
        }
    }

    /** Certain and uncertain numbers stay interleaved in numeric order. */
    @Test
    void numbersStayInterleavedAcrossTheCertainAndUncertainKinds() {
        List<Value> values = List.of(new URealValue(3, 0), IntegerValue.valueOf(1),
                new UIntegerValue(2, 0), new RealValue(0.5), UBooleanValue.probability(0.5));
        List<Value> sorted =
                new SequenceValue(TypeFactory.mkOclAny(), values).getSortedElements();
        assertEquals(List.of(new RealValue(0.5), IntegerValue.valueOf(1),
                new UIntegerValue(2, 0), new URealValue(3, 0),
                UBooleanValue.probability(0.5)), sorted);
    }

    /**
     * Antisymmetry across kinds. An opinion used to tie with everything, while
     * everything else ordered it by rendering and so did not tie back.
     */
    @Test
    void orderingIsAntisymmetricAcrossEveryPairOfKinds() {
        List<Value> kinds = everyKind(new Random(7));
        for (Value x : kinds) {
            for (Value y : kinds) {
                assertEquals(-Integer.signum(y.compareTo(x)), Integer.signum(x.compareTo(y)),
                        "compareTo is not antisymmetric for " + x + " and " + y);
            }
        }
    }

    /** Two opinions still tie, which is the documented behaviour. */
    @Test
    void opinionsTieWithEachOtherButNotWithOtherKinds() {
        SBooleanValue one = new SBooleanValue(0.8, 0.1, 0.1, 0.5);
        SBooleanValue other = new SBooleanValue(0.1, 0.8, 0.1, 0.5);
        assertEquals(0, one.compareTo(other), "opinions have no order among themselves");
        assertEquals(0, other.compareTo(one));
        assertTrue(one.compareTo(new URealValue(1, 0)) != 0, "but they do not tie with a UReal");
    }
}
