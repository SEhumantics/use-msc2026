package org.tzi.use.uml.ocl.value;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.tzi.use.uml.ocl.type.TypeFactory;

import java.util.HashSet;

import static org.junit.Assert.*;

class URealValueTest {

    @Test

    public void testType() {
        URealValue urv = new URealValue(5,5);

        assertEquals( TypeFactory.mkUReal(), urv.type(),"UReal.type");
    }

    @Test

    public void testValues() {
        URealValue urv;
        double value, uncertainty;
        String stringUreal;

        Object [][] testCases= new Object [][] {
                {5.0,   5.0,    "UReal(5.0, 5.0)"},
                {5.0,   0.0,    "UReal(5.0, 0.0)"},
                {5.0,  -5.0,    "UReal(5.0, 5.0)"},
                {0.0,   5.0,    "UReal(0.0, 5.0)"},
                {0.0,   0.0,    "UReal(0.0, 0.0)"},
                {0.0,  -5.0,    "UReal(0.0, 5.0)"},
                {-5.0,  5.0,    "UReal(-5.0, 5.0)"},
                {-5.0,  0.0,    "UReal(-5.0, 0.0)"},
                {-5.0, -5.0,    "UReal(-5.0, 5.0)"},
                {5.556, 5.556,  "UReal(5.556, 5.556)"},
                {5.556, 0.593,  "UReal(5.556, 0.593)"},
                {5.556,-5.556,  "UReal(5.556, 5.556)"},
                {0.593, 5.556,  "UReal(0.593, 5.556)"},
                {0.593, 0.593,  "UReal(0.593, 0.593)"},
                {0.593,-5.556,  "UReal(0.593, 5.556)"},
                {-5.556, 5.556, "UReal(-5.556, 5.556)"},
                {-5.556, 0.593, "UReal(-5.556, 0.593)"},
                {-5.556,-5.556, "UReal(-5.556, 5.556)"}
        };

        for (int i = 0 ; i < testCases.length ; i++) {
            value = (Double) testCases[i][0];
            uncertainty = (Double) testCases[i][1];
            stringUreal = (String) testCases[i][2];
            urv = new URealValue(value, uncertainty);
            assertEquals( value, urv.value(),"URealValue.value");
            assertEquals( Math.abs(uncertainty), urv.uncertainty(),"URealValue.uncertainty");
            assertEquals( stringUreal, urv.toString(new StringBuilder()).toString(),"URealValue.value");
        }

    }

    @Test

    public void testIsTypeOf() {
        URealValue urv = new URealValue(5.0, 5);
        assertFalse( urv.isBag(),"UReal.isBag");
        assertFalse( urv.isCollection(),"UReal.isCollection");
        assertFalse( urv.isSequence(),"UReal.isSequence");
        assertFalse( urv.isSet(),"UReal.isSet");
        assertFalse( urv.isOrderedSet(),"UReal.isOrderedSet");
        assertFalse( urv.isBoolean(),"UReal.isBoolean");
        assertFalse( urv.isInteger(),"UReal.isInteger");
        assertFalse( urv.isReal(),"UReal.isReal");
        assertTrue( urv.isUReal(),"UReal.isUReal");
        assertFalse( urv.isUnlimitedNatural(),"UReal.isUnlimitedNatural");
        assertFalse( urv.isObject(),"UReal.isObject");
        assertFalse( urv.isLink(),"UReal.isLink");
        assertFalse( urv.isUBoolean(),"UReal.isUBoolean");
        assertFalse( urv.isUInteger(),"UReal.isUInteger");
    }

    @Test

    public void testCompareTo() {
        URealValue a;
        Value b;

        // Tipos UReal
        a = new URealValue(0, 0);
        b = new URealValue(0, 0);
        assertEquals(
                0,
                a.compareTo(b),"UReal(0, 0) = UReal(0, 0)");

        a = new URealValue(0, 0);
        b = new URealValue(1, 0);
        assertEquals(
                -1,
                a.compareTo(b),"UReal(0, 0) < UReal(1, 0)");

        a = new URealValue(3, 0);
        b = new URealValue(0, 0);
        assertEquals(
                1,
                a.compareTo(b),"UReal(3, 0) > UReal(0, 0)");

        a = new URealValue(0, 0);
        b = new URealValue(3, 2);
        assertEquals(
                -1,
                a.compareTo(b),"UReal(0, 0) < UReal(3, 2)");

        a = new URealValue(3, 0);
        b = new URealValue(0, 2);
        assertEquals(
                1,
                a.compareTo(b),"UReal(3, 0) > UReal(0, 2)");

        a = new URealValue(0, 2);
        b = new URealValue(3, 0);
        assertEquals(
                -1,
                a.compareTo(b),"UReal(0, 2) < UReal(3, 0)");

        a = new URealValue(3, 2);
        b = new URealValue(0, 0);
        assertEquals(
                1,
                a.compareTo(b),"UReal(3, 2) > UReal(0, 0)");

        a = new URealValue(0, 2);
        b = new URealValue(0, 2);
        assertEquals(
                0,
                a.compareTo(b),"UReal(0, 2) = UReal(0, 2)");

        a = new URealValue(0, 2);
        b = new URealValue(0, 1);
        assertEquals(
                0,
                a.compareTo(b),"UReal(0, 2) = UReal(0, 1)");

        a = new URealValue(0, 2);
        b = new URealValue(1, 1);
        assertEquals(
                0,
                a.compareTo(b),"UReal(0, 2) = UReal(1, 1)");

        a = new URealValue(0, 2);
        b = new URealValue(-1, 1);
        assertEquals(
                0,
                a.compareTo(b),"UReal(0, 2) = UReal(-1, 1)");

        a = new URealValue(0, 2);
        b = new URealValue(5, 2);
        assertEquals(
                -1,
                a.compareTo(b),"UReal(0, 2) = UReal(5, 2)");

        a = new URealValue(5, 2);
        b = new URealValue(0, 2);
        assertEquals(
                1,
                a.compareTo(b),"UReal(5, 2) = UReal(0, 2)");

        // Real

        a = new URealValue(0, 0);
        b = new RealValue(0);
        assertEquals(
                0,
                a.compareTo(b),"UReal(0, 0) = 0.0");

        a = new URealValue(0, 0);
        b = new RealValue(1);
        assertEquals(
                -1,
                a.compareTo(b),"UReal(0, 0) < 1.0");

        a = new URealValue(1, 0);
        b = new RealValue(0);
        assertEquals(
                1,
                a.compareTo(b),"UReal(0, 0) > 1.0");

        a = new URealValue(1, 2);
        b = new RealValue(3);
        assertEquals(
                -1,
                a.compareTo(b),"UReal(1, 2) < 3.0");

        a = new URealValue(3, 2);
        b = new RealValue(0);
        assertEquals(
                1,
                a.compareTo(b),"UReal(1, 2) > 3.0");


        // Integer

        a = new URealValue(0, 0);
        b = IntegerValue.valueOf(0);
        assertEquals(
                0,
                a.compareTo(b),"UReal(0, 0) = 0");

        a = new URealValue(0, 0);
        b = IntegerValue.valueOf(1);
        assertEquals(
                -1,
                a.compareTo(b),"UReal(0, 0) < 1");

        a = new URealValue(1, 0);
        b = IntegerValue.valueOf(0);
        assertEquals(
                1,
                a.compareTo(b),"UReal(0, 0) > 0");

        a = new URealValue(1, 2);
        b = IntegerValue.valueOf(3);
        assertEquals(
                -1,
                a.compareTo(b),"UReal(1, 2) < 3");

        a = new URealValue(3, 2);
        b = IntegerValue.valueOf(0);
        assertEquals(
                1,
                a.compareTo(b),"UReal(1, 2) > 0");


    }

    @Test

    public void testIdentical() {
        URealValue a;
        Value b;

        a = new URealValue(-2, 3);
        b = new URealValue(-2, 3);
        assertTrue( a.equals(b),"UReal(-2, 3).equals(UReal(-2, 3))");

        a = new URealValue(-2, 3);
        b = new URealValue(0, 3);
        assertFalse( a.equals(b),"not UReal(-2, 3).equals(UReal(0, 3))");

        a = new URealValue(2, 3);
        b = new URealValue(2, 0);
        assertFalse( a.equals(b),"not UReal(2, 3).equals(UReal(2, 0))");

        a = new URealValue(2, 3);
        b = new URealValue(1, 0);
        assertFalse( a.equals(b),"not UReal(2, 3).equals(UReal(1, 0))");

        // Real
        a = new URealValue(-2, 3);
        b = new RealValue(-2);
        assertFalse( a.equals(b),"UReal(-2, 3).equals(-2)");

        a = new URealValue(-2, 0);
        b = new RealValue(-2);
        assertTrue( a.equals(b),"UReal(-2, 3).equals(-2)");

        a = new URealValue(-2, 0);
        b = new RealValue(1);
        assertFalse( a.equals(b),"UReal(-2, 3).equals(2)");

        a = new URealValue(-2, 3);
        b = new RealValue(1);
        assertFalse( a.equals(b),"UReal(-2, 3).equals(2)");

        // Integer
        a = new URealValue(-2, 3);
        b = IntegerValue.valueOf(-2);
        assertFalse( a.equals(b),"UReal(-2, 3).equals(-2)");

        a = new URealValue(-2, 0);
        b = IntegerValue.valueOf(-2);
        assertTrue( a.equals(b),"UReal(-2, 3).equals(-2)");

        a = new URealValue(-2, 0);
        b = IntegerValue.valueOf(1);
        assertFalse( a.equals(b),"UReal(-2, 3).equals(2)");

        a = new URealValue(-2, 3);
        b = IntegerValue.valueOf(1);
        assertFalse( a.equals(b),"UReal(-2, 3).equals(2)");

        // Otro
        a = new URealValue(-2, 3);
        b = new StringValue("testing");
        assertFalse( a.equals(b),"UReal(-2, 3).equals(-2)");


    }

}