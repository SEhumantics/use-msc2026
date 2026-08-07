package org.tzi.use.uml.ocl.value;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.tzi.use.uml.ocl.type.TypeFactory;

class UIntegerValueTest {

    /**
     * Testing constructor, value(), uncertainty() and toString()
     */

    @Test

    public void testValues() {
        UIntegerValue uiv;
        int value;
        double uncertainty;
        String stringUInteger;

        Object [][] testCases= new Object [][] {
                {5,   5.0,    "UInteger(5, 5.0)"},
                {5,   0.0,    "UInteger(5, 0.0)"},
                {5,  -5.0,    "UInteger(5, 5.0)"},
                {5,  -5.53,    "UInteger(5, 5.53)"},
                {0,   5.0,    "UInteger(0, 5.0)"},
                {0,   0.0,    "UInteger(0, 0.0)"},
                {0,  -5.0,    "UInteger(0, 5.0)"},
                {0,  -5.45,    "UInteger(0, 5.45)"},
                {-5,  5.0,    "UInteger(-5, 5.0)"},
                {-5,  0.0,    "UInteger(-5, 0.0)"},
                {-5, -5.0,    "UInteger(-5, 5.0)"},
                {-5, -5.223,    "UInteger(-5, 5.223)"}
        };

        for (int i = 0 ; i < testCases.length ; i++) {
            value = (Integer) testCases[i][0];
            uncertainty = (Double) testCases[i][1];
            stringUInteger = (String) testCases[i][2];
            uiv = new UIntegerValue(value, uncertainty);
            assertEquals( value, uiv.value(),"UIntegerValue.value");
            assertEquals( Math.abs(uncertainty), uiv.uncertainty(),"UIntegerValue.uncertainty");
            assertEquals( stringUInteger, uiv.toString(new StringBuilder()).toString(),"UIntegerValue.toString");
        }
    }

    @Test

    public void testType() {
        UIntegerValue uiv = new UIntegerValue(5,5);

        assertEquals( TypeFactory.mkUInteger(), uiv.type(),"UInteger.type");
    }

    @Test

    public void testIsTypeOf() {
        UIntegerValue uiv = new UIntegerValue(5, 5);
        assertFalse( uiv.isBag(),"UInteger.isBag");
        assertFalse( uiv.isCollection(),"UInteger.isCollection");
        assertFalse( uiv.isSequence(),"UInteger.isSequence");
        assertFalse( uiv.isSet(),"UInteger.isSet");
        assertFalse( uiv.isOrderedSet(),"UInteger.isOrderedSet");
        assertFalse( uiv.isBoolean(),"UInteger.isBoolean");
        assertFalse( uiv.isInteger(),"UInteger.isInteger");
        assertFalse( uiv.isReal(),"UInteger.isReal");
        assertFalse( uiv.isUReal(),"UInteger.isUReal");
        assertFalse( uiv.isUnlimitedNatural(),"UInteger.isUnlimitedNatural");
        assertFalse( uiv.isObject(),"UInteger.isObject");
        assertFalse( uiv.isLink(),"UInteger.isLink");
        assertFalse( uiv.isUBoolean(),"UInteger.isUBoolean");
        assertTrue ( uiv.isUInteger(),"UInteger.isUInteger");
    }

    /**
     * The historical library divides in int arithmetic before flooring, so the
     * uncertain quotient truncates towards zero like the scalar branches. Flooring
     * the real quotient instead shifted every negative result by one.
     */
    @Test
    public void testUncertainDivTruncatesTowardsZero() {
        assertEquals(-1, new UIntegerValue(5,1.13).div(new UIntegerValue(-3,2.84)).value(),
                "5 div -3 with uncertainty on both sides");
        assertEquals(1, new UIntegerValue(5,1.13).div(new UIntegerValue(3,2.84)).value());
        assertEquals(-1, new UIntegerValue(-5,1.13).div(new UIntegerValue(3,2.84)).value());
        // the scalar branches were already truncating
        assertEquals(-1, new UIntegerValue(5,1.13).div(new UIntegerValue(-3,0)).value());
    }

    /**
     * Ordering has to stay antisymmetric across the two uncertain numeric kinds,
     * otherwise sortedBy over a mixed collection silently mis-orders.
     */
    @Test
    public void testOrderingAgainstURealIsAntisymmetric() {
        UIntegerValue small = new UIntegerValue(1,0), large = new UIntegerValue(9,0);
        URealValue middle = new URealValue(5.0,0);

        assertEquals(-1, Integer.signum(small.compareTo(middle)));
        assertEquals( 1, Integer.signum(middle.compareTo(small)));
        assertEquals( 1, Integer.signum(large.compareTo(middle)));
        assertEquals(-1, Integer.signum(middle.compareTo(large)));

        java.util.List<Value> mixed = new java.util.ArrayList<>(
                java.util.List.of(large, middle, small));
        java.util.Collections.sort(mixed);
        assertEquals(java.util.List.of(small, middle, large), mixed, "mixed UInteger/UReal ordering");
    }
}
