package org.tzi.use.uml.ocl.value;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class SBooleanValueTest {
    private static final double EPS=1e-9;
    @Test void discountMatchesHistoricalGoldenCase() {
        var opinion=new SBooleanValue(.8,.05,.15,.2);
        var discounted=opinion.discount(SBooleanValue.dogmatic(.98,.2));
        assertEquals(.784,discounted.belief(),EPS);
        assertEquals(.049,discounted.disbelief(),EPS);
        assertEquals(.167,discounted.uncertainty(),EPS);
        assertEquals(.2,discounted.baseRate(),EPS);
    }
    @Test void averageFusionIsGenuinelyNary() {
        var a=new SBooleanValue(0,.9,.1,.5); var b=new SBooleanValue(.6,.3,.1,.5); var c=new SBooleanValue(.6,.3,.1,.5);
        assertEquals(.4,SBooleanValue.averageBeliefFusion(List.of(a,b,c)).belief(),EPS);
        assertEquals(.45,SBooleanValue.averageBeliefFusion(List.of(SBooleanValue.averageBeliefFusion(List.of(a,b)),c)).belief(),EPS);
    }
    @Test void malformedMassIsRejectedWithoutNormalization() { assertThrows(IllegalArgumentException.class,()->new SBooleanValue(.4,.4,.4,.5)); }
    @Test void stableComparisonIsNotTheHistoricalAlwaysZeroPlaceholder() { assertNotEquals(0,new SBooleanValue(.6,.2,.2,.5).compareTo(new SBooleanValue(.2,.6,.2,.5))); }
    @Test void collectionDiscountMultipliesProjectedTrust() {
        var source=new SBooleanValue(.8,.05,.15,.2);
        var result=source.discount(List.of(SBooleanValue.dogmatic(.98,.2),SBooleanValue.dogmatic(.5,.2)));
        assertEquals(.392,result.belief(),EPS); assertEquals(.0245,result.disbelief(),EPS);
    }
    @Test void beliefConstraintFusionUsesConflictNormalization() {
        var a=new SBooleanValue(.6,.2,.2,.5); var b=new SBooleanValue(.4,.3,.3,.5);
        var result=SBooleanValue.beliefConstraintFusion(List.of(a,b));
        assertEquals(.50/.74,result.belief(),EPS); assertEquals(.06/.74,result.uncertainty(),EPS);
    }
}
