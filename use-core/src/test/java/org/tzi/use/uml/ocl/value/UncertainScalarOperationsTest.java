package org.tzi.use.uml.ocl.value;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class UncertainScalarOperationsTest {
    @Test void realComparisonsUseDistributionProbabilities() {
        var left=new URealValue(0,1); var right=new URealValue(0,1);
        assertEquals(.5,left.lessThan(right).probability(),.01);
        assertTrue(left.uEquals(right).probability()>0.99);
    }
    @Test void integerMathOperationsAreAvailable() {
        var x=new UIntegerValue(9,0);
        assertEquals(3,x.sqrt().value());
        assertEquals(27,x.power(1.5).value());
    }
    @Test void stringConversionsAndComparisonsAreAvailable() {
        var x=new UStringValue("42",1);
        assertEquals(42,x.toInteger().value());
        assertEquals(42,x.toReal().value(),1e-12);
        assertTrue(new UStringValue("b",1).greaterThan(new UStringValue("a",1)).value());
        assertEquals(.8,new UStringValue("maybe",.2).toUBoolean().probability(),1e-12);
    }
}
