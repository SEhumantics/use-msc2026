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
}
