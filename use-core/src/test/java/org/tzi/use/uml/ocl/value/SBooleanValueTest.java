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
    @Test void consensusCompromiseMatchesHistoricalThreeOpinionGolden() {
        var result=SBooleanValue.consensusAndCompromiseFusion(List.of(
            new SBooleanValue(.1,.3,.6,.5),new SBooleanValue(.4,.2,.4,.5),new SBooleanValue(.7,.1,.2,.5)));
        assertEquals(.629,result.belief(),.002); assertEquals(.182,result.disbelief(),.002); assertEquals(.189,result.uncertainty(),.002);
    }
    @Test void historicalFusionSuiteGoldenValues() {
        var opinions=List.of(new SBooleanValue(.55,.3,.15,.38),new SBooleanValue(.6,.3,.1,.38),
            new SBooleanValue(.7,.2,.1,.38),new SBooleanValue(.8,.1,.1,.38),new SBooleanValue(.9,.05,.05,.38));
        var weighted=SBooleanValue.weightedBeliefFusion(opinions);
        assertEquals(.757,weighted.belief(),.002); assertEquals(.156,weighted.disbelief(),.002); assertEquals(.087,weighted.uncertainty(),.002);
        var epistemic=SBooleanValue.epistemicCumulativeBeliefFusion(opinions);
        assertEquals(.705,epistemic.belief(),.002); assertEquals(0,epistemic.disbelief(),.002); assertEquals(.295,epistemic.uncertainty(),.002);
        var ccf=SBooleanValue.consensusAndCompromiseFusion(opinions);
        assertEquals(.564,ccf.belief(),.002); assertEquals(.057,ccf.disbelief(),.002); assertEquals(.379,ccf.uncertainty(),.002);
    }
    @Test void weightedFusionHonorsRelativeWeights() {
        var low=new SBooleanValue(1,0,0,.5,1); var high=new SBooleanValue(0,1,0,.5,3);
        var result=SBooleanValue.weightedBeliefFusion(List.of(low,high));
        assertEquals(.25,result.belief(),EPS);
        assertEquals(4,result.relativeWeight(),EPS);
        assertEquals(0,SBooleanValue.vacuous(.5).relativeWeight(),EPS);
    }
    @Test void deductionMatchesHistoricalExamples() {
        var yx=new SBooleanValue(.4,.5,.1,.4); var ynx=new SBooleanValue(0,.4,.6,.4);
        var y=new SBooleanValue(0,0,1,.8).deduceY(yx,ynx);
        assertEquals(.320,y.belief(),.002); assertEquals(.480,y.disbelief(),.002); assertEquals(.200,y.uncertainty(),.002);
        y=new SBooleanValue(.10,.8,.1,.8).deduceY(yx,ynx);
        assertEquals(.072,y.belief(),.002); assertEquals(.418,y.disbelief(),.002); assertEquals(.510,y.uncertainty(),.002);
    }
    @Test void degenerateFusionCasesMatchHistoricalBehavior() {
        var t=new SBooleanValue(1,0,0,.5); var f=new SBooleanValue(0,1,0,.5); var v=SBooleanValue.vacuous(.5);
        var tied=SBooleanValue.consensusAndCompromiseFusion(List.of(f,t));
        assertEquals(0,tied.belief(),EPS); assertEquals(0,tied.disbelief(),EPS); assertEquals(1,tied.uncertainty(),EPS);
        var average=SBooleanValue.averageBeliefFusion(List.of(f,t));
        assertEquals(.5,average.belief(),EPS); assertEquals(.5,average.disbelief(),EPS); assertEquals(0,average.uncertainty(),EPS);
        assertEquals(1,SBooleanValue.consensusAndCompromiseFusion(List.of(t,v)).uncertainty(),EPS);
        assertEquals(1,SBooleanValue.consensusAndCompromiseFusion(List.of(v,v)).uncertainty(),EPS);
    }
    @Test void logicalOperatorsUseHistoricalBaseRateFormulas() {
        var left=new SBooleanValue(.2,.6,.2,.5); var right=new SBooleanValue(.6,.2,.2,.5);
        var or=left.or(right); assertEquals(.680,or.belief(),.002); assertEquals(.173,or.disbelief(),.002); assertEquals(.147,or.uncertainty(),.002); assertEquals(.75,or.baseRate(),EPS);
        var and=left.and(right); assertEquals(.173,and.belief(),.002); assertEquals(.680,and.disbelief(),.002); assertEquals(.147,and.uncertainty(),.002); assertEquals(.25,and.baseRate(),EPS);
        var xor=left.xor(right); assertEquals(.4,xor.belief(),EPS); assertEquals(.56,xor.disbelief(),EPS); assertEquals(.04,xor.uncertainty(),EPS);
    }
    @Test void uncertaintyMaximizationPreservesHistoricalBoundaryCases() {
        var zeroBase=new SBooleanValue(.4,.4,.2,0).uncertaintyMaximized();
        assertEquals(.4,zeroBase.belief(),EPS); assertEquals(0,zeroBase.disbelief(),EPS); assertEquals(.6,zeroBase.uncertainty(),EPS);
        var zeroProjection=new SBooleanValue(0,.4,.6,0).uncertaintyMaximized();
        assertEquals(1,zeroProjection.uncertainty(),EPS);
    }
}
