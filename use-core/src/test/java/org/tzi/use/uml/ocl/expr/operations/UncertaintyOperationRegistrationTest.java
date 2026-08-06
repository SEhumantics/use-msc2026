package org.tzi.use.uml.ocl.expr.operations;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Set;
import org.junit.jupiter.api.Test;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import org.tzi.use.uml.ocl.type.TypeFactory;
import org.tzi.use.uml.ocl.value.IntegerValue;
import org.tzi.use.uml.ocl.value.UStringValue;
import org.tzi.use.uml.ocl.value.UndefinedValue;
import org.tzi.use.uml.ocl.value.Value;

class UncertaintyOperationRegistrationTest {
    @Test void historicalUncertaintyNamesAreRegistered() {
        Multimap<String,OpGeneric> operations=HashMultimap.create();
        OpGeneric.registerOperations(operations);
        Set<String> names=operations.keySet();
        for(String name:new String[]{"value","uncertainty","setUncertainty","setValue","abs","inv","neg","toReal","toInteger","toUInteger","power","sqrt","atan","sin","cos","tan","asin","acos",
            "div","mod","toUReal","confidence","setConfidence","at","character","+","indexOf","substring","toLowerCase","toUpperCase","size","toString","toBoolean","toUBoolean","<","<=",">",">=",
            "toBooleanC","equalsC","and","or","not","implies","xor","equivalent","projection","belief","disbelief","baseRate","certainty","toUBoolean","getRelativeWeight","uncertaintyMaximized","uncertainOpinion","isAbsolute","isVacuous","isDogmatic","isMaximizedUncertainty","isCertain","isUncertain","projectiveDistance","conjunctiveCertainty","degreeOfConflict","min","max","applyOn","deduceY",
            "minimumBeliefFusion","majorityBeliefFusion","beliefConstraintFusion","averageBeliefFusion","aleatoryCumulativeBeliefFusion","epistemicCumulativeBeliefFusion","weightedBeliefFusion","consensusAndCompromiseFusion","discount","uCount","uCountC","uIncludes","uExcludes"}) {
            assertTrue(names.contains(name), name);
        }
    }
    @Test void uncertainCountPreservesHistoricalUndefinedCollectionHandling() {
        Value result=new Op_collection_uCount().eval(null,
            new Value[]{UndefinedValue.instance,new UStringValue("x",1)},TypeFactory.mkInteger());
        assertTrue(result instanceof IntegerValue);
        assertEquals(0,((IntegerValue)result).value());
    }
}
