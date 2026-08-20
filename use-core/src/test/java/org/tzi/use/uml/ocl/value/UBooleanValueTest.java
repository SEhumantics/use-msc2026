package org.tzi.use.uml.ocl.value;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ported from USE-Uncertainty (github.com/atenearesearchgroup/uncertainty @ 74acd0d),
 * src/test/org/tzi/use/uml/ocl/value/UBooleanValueTest.java. Closes B7 ledger row M-43.
 *
 * <p>The ledger's recommendation for M-43 was to revive the fork's two commented-out
 * {@code try { valueOf(true, -2/2); fail(...); } catch (Exception) {}} blocks as {@code @Disabled}
 * tests, reasoning (from the fork's own {@code // FIXME: When It will be fixed in atenea library}
 * comment) that the vendored library's constructor clamped out-of-range probabilities rather than
 * throwing, so a live revival would fail. **That reasoning does not hold for this port.** Probed
 * directly, both against this port's own {@link UBooleanValue#valueOf(boolean, double)} and — more
 * importantly — against the real historical jar via {@code HistoricalOracle} reflection (not read
 * from source, invoked):
 *
 * <pre>
 * HISTORICAL valueOf(true,-2) THREW java.lang.IllegalArgumentException: Invalid parameters
 * HISTORICAL valueOf(true,2) THREW java.lang.IllegalArgumentException: Invalid parameters
 * </pre>
 *
 * The vendored {@code org.tzi.use.uncertainty.datatypes.UBoolean(boolean, double)} constructor —
 * unchanged by this port's vendoring (B1); byte-identical to
 * {@code .git/reference-repositories/uncertainty/uDataTypes/Libraries/Java/src/uDataTypes/UBoolean.java:35-36}
 * — validates {@code c < 0 || c > 1} and throws {@code IllegalArgumentException("Invalid parameters")}
 * before {@link UBooleanValue}'s own constructor guard ever runs. This is true of the real historical
 * jar as well as the port, so whatever prompted the fork author's FIXME either did not apply to this
 * code path or was itself already fixed by the time of the {@code 74acd0d} snapshot this port is
 * built from. Reviving these two blocks live, as {@link #valueOfRejectsProbabilityBelowZero()} and
 * {@link #valueOfRejectsProbabilityAboveOne()} below, is therefore the correct discharge of M-43 —
 * not the {@code @Disabled} form the ledger anticipated.
 */
class UBooleanValueTest {

    @Test
    void values() {
        UBooleanValue uBoolean;

        uBoolean = UBooleanValue.FALSE;
        assertTrue(uBoolean.value());
        assertEquals(0.0, uBoolean.probability());
        assertEquals("UBoolean(true, 0.0)", uBoolean.toString());

        uBoolean = UBooleanValue.valueOf(true, 0.5);
        assertTrue(uBoolean.value());
        assertEquals(0.5, uBoolean.probability());
        assertEquals("UBoolean(true, 0.5)", uBoolean.toString());

        uBoolean = UBooleanValue.TRUE;
        assertTrue(uBoolean.value());
        assertEquals(1.0, uBoolean.probability());
        assertEquals("UBoolean(true, 1.0)", uBoolean.toString());

        uBoolean = UBooleanValue.valueOf(false, 0.5);
        assertTrue(uBoolean.value());
        assertEquals(0.5, uBoolean.probability());
        assertEquals("UBoolean(true, 0.5)", uBoolean.toString());

        uBoolean = UBooleanValue.valueOf(false, 1);
        assertTrue(uBoolean.value());
        assertEquals(0.0, uBoolean.probability());
        assertEquals("UBoolean(true, 0.0)", uBoolean.toString());

        uBoolean = UBooleanValue.valueOf(false, 0.2);
        assertTrue(uBoolean.value());
        assertEquals(0.8, uBoolean.probability());
        assertEquals("UBoolean(true, 0.8)", uBoolean.toString());
    }

    @Test
    void valueOfRejectsProbabilityBelowZero() {
        assertThrows(IllegalArgumentException.class, () -> UBooleanValue.valueOf(true, -2));
    }

    @Test
    void valueOfRejectsProbabilityAboveOne() {
        assertThrows(IllegalArgumentException.class, () -> UBooleanValue.valueOf(true, 2));
    }

    @Test
    void isTypeOf() {
        Value ubv = UBooleanValue.FALSE;
        assertFalse(ubv.isBag(), "UBoolean.isBag");
        assertFalse(ubv.isCollection(), "UBoolean.isCollection");
        assertFalse(ubv.isSequence(), "UBoolean.isSequence");
        assertFalse(ubv.isSet(), "UBoolean.isSet");
        assertFalse(ubv.isOrderedSet(), "UBoolean.isOrderedSet");
        assertFalse(ubv.isBoolean(), "UBoolean.isBoolean");
        assertFalse(ubv.isInteger(), "UBoolean.isInteger");
        assertFalse(ubv.isReal(), "UBoolean.isReal");
        assertFalse(ubv.isUReal(), "UBoolean.isUReal");
        assertFalse(ubv.isUnlimitedNatural(), "UBoolean.isUnlimitedNatural");
        assertFalse(ubv.isObject(), "UBoolean.isObject");
        assertFalse(ubv.isLink(), "UBoolean.isLink");
        assertTrue(ubv.isUBoolean(), "UBoolean.isUBoolean");
        assertFalse(ubv.isUInteger(), "UBoolean.isUInteger");
    }

    @Test
    void testEquals() {
        /*
         Partition :
         1. this
            1.1. value          (true)
            1.2. probability    (zero)
         2. other
            2.1. type           (UBoolean, other)
            2.2. nullable       (yes, no)
            2.3. value          (same this.value, distinct this.value)
            2.4. probability    (same this.u, 1 - this.u, distinct this.u)

         Conditions.
         - If type isn't UBoolean, doesn't matter to test more of 2.*
         - If other is nullable, cannot have any value or probability
         */
        UBooleanValue a;
        Value b;

        // (true, zero, UBoolean, yes, -, -)
        a = UBooleanValue.FALSE;
        b = null;
        assertFalse(a.equals(b));

        // (true, zero, UBoolean, no, same, same)
        a = UBooleanValue.FALSE;
        b = UBooleanValue.FALSE;
        assertTrue(a.equals(b));
        assertEquals(a.hashCode(), b.hashCode());

        // (true, zero, UBoolean, no, same, distinct)
        a = UBooleanValue.FALSE;
        b = UBooleanValue.valueOf(true, 0.5);
        assertFalse(a.equals(b));
        assertFalse(a.hashCode() == b.hashCode());

        // (true, zero, UBoolean, no, same, 1 - this.u)
        a = UBooleanValue.FALSE;
        b = UBooleanValue.TRUE;
        assertFalse(a.equals(b));
        assertFalse(a.hashCode() == b.hashCode());

        // (true, zero, UBoolean, no, distinct, same)
        a = UBooleanValue.FALSE;
        b = UBooleanValue.valueOf(false, 0);
        assertFalse(a.equals(b));
        assertFalse(a.hashCode() == b.hashCode());

        // (true, zero, UBoolean, no, distinct, distinct)
        a = UBooleanValue.FALSE;
        b = UBooleanValue.valueOf(false, 0.5);
        assertFalse(a.equals(b));
        assertFalse(a.hashCode() == b.hashCode());

        // (true, zero, UBoolean, no, distinct, 1 - this.u)
        a = UBooleanValue.FALSE;
        b = UBooleanValue.valueOf(false, 1);
        assertTrue(a.equals(b));
        assertEquals(a.hashCode(), b.hashCode());

        // (true, zero, other, -, -, -)
        a = UBooleanValue.FALSE;
        b = new StringValue("Testing");
        assertFalse(a.equals(b));
        assertFalse(a.hashCode() == b.hashCode());
    }
}
