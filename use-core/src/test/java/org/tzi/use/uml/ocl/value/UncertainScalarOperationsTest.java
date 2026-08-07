package org.tzi.use.uml.ocl.value;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class UncertainScalarOperationsTest {
    @Test void realComparisonsUseHistoricalGaussianPartitioning() {
        var left=new URealValue(0,1); var right=new URealValue(0,1);
        assertEquals(0,left.lessThan(right).probability(),.01);
        assertTrue(left.uEquals(right).probability()>0.99);
        // Overlap is what OCL `=' evaluates; compareTo instead orders by value
        // then uncertainty, because overlap is not transitive and cannot sort a
        // collection. See UncertainValueOrderingTest.
        assertTrue(new URealValue(0,2).uEquals(new URealValue(1,1)).toBoolean().value());
        assertEquals(-1,new URealValue(0,2).compareTo(new URealValue(1,1)));
        assertEquals(0,new URealValue(0,0).compareTo(new RealValue(0)));
        assertEquals("UReal(1.2345678901, 0.123456789)",new URealValue(1.234567890123,0.12345678901).toString());
        assertEquals(new URealValue(1.00000000001,.1),new URealValue(1.00000000002,.100000000001));
    }
    @Test void integerMathOperationsAreAvailable() {
        var x=new UIntegerValue(9,0);
        assertEquals(3,x.sqrt().value());
        assertEquals(27,x.power(1.5).value());
    }
    @Test void realArithmeticAndConversionsFollowHistoricalUncertaintyFormulas() {
        var a=new URealValue(3,4); var b=new URealValue(1,2);
        assertEquals(Math.hypot(4,2),a.add(b).uncertainty(),1e-12);
        assertEquals(Math.sqrt(1*1*4*4+3*3*2*2),a.multiply(b).uncertainty(),1e-12);
        // The USE-level conversion truncates and keeps the uncertainty; the
        // library conversion the integer algebra uses floors and folds the
        // discarded fraction in.
        var converted=new URealValue(-1.25,.5).toUInteger();
        assertEquals(-1,converted.value());
        assertEquals(.5,converted.uncertainty(),1e-12);
        var flooring=new URealValue(-1.25,.5).toUIntegerFlooring();
        assertEquals(-2,flooring.value());
        assertEquals(Math.hypot(.5,.75),flooring.uncertainty(),1e-12);
    }
    @Test void stringConversionsAndComparisonsAreAvailable() {
        var x=new UStringValue("42",1);
        assertEquals(42,x.toInteger().value());
        assertEquals(42,x.toReal().value(),1e-12);
        var word=new UStringValue("abc",.8);
        assertEquals("a",word.at(1).value());
        assertEquals(.8,word.at(1).confidence(),1e-12);
        assertEquals("b",word.character(2).value());
        assertEquals("ab",word.substring(1,2).value());
        assertEquals(.6,word.size().uncertainty(),1e-12);
        assertEquals(1,word.indexOf("b").value());
        assertTrue(new UStringValue("b",1).greaterThan(new UStringValue("a",1)).toBoolean().value());
        assertTrue(new UStringValue("true",1).toUBoolean().toBoolean().value());
        assertEquals(.8,new UStringValue("maybe",.2).toUBoolean().probability(),1e-12);
        // Canonical form: the carried Boolean is always true and the number is
        // the probability that it holds.
        assertEquals("UBoolean(true, 0.2)",UBooleanValue.probability(.2).toString());
        assertTrue(UBooleanValue.TRUE.equals(BooleanValue.TRUE));
        // Historical asymmetry that follows from the canonical form: no
        // uncertain Boolean ever equals Boolean false.
        assertFalse(UBooleanValue.FALSE.equals(BooleanValue.FALSE));
        assertEquals(UBooleanValue.probability(.2), UBooleanValue.probability(.20000000001));
    }
}
