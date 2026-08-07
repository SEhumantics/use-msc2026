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

}