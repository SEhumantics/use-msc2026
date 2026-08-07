package org.tzi.use.uml.ocl.expr.operations;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Set;
import org.junit.jupiter.api.Test;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import org.tzi.use.uml.ocl.type.TypeFactory;
import org.tzi.use.uml.ocl.value.IntegerValue;
import org.tzi.use.uml.ocl.expr.ExpStdOp;
import org.tzi.use.uml.ocl.value.UStringValue;
import org.tzi.use.uml.ocl.value.UndefinedValue;
import org.tzi.use.uml.ocl.value.Value;
import org.tzi.use.uml.ocl.type.Type;

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
    @Test void uncertainNumericMinMaxOverloadsAreReachable() {
        assertTrue(ExpStdOp.exists("min", new Type[]{TypeFactory.mkUReal(), TypeFactory.mkInteger()}));
        assertTrue(ExpStdOp.exists("max", new Type[]{TypeFactory.mkInteger(), TypeFactory.mkUReal()}));
        assertTrue(ExpStdOp.exists("min", new Type[]{TypeFactory.mkUInteger(), TypeFactory.mkUInteger()}));
        assertTrue(ExpStdOp.exists("floor", new Type[]{TypeFactory.mkUReal()}));
        assertTrue(ExpStdOp.exists("round", new Type[]{TypeFactory.mkUReal()}));
    }
    /**
     * The historical registrations pin down argument types, not just names.
     * These are the signatures of the families the replayed compiler corpus
     * does not reach, taken from StandardOperationsUString and
     * StandardOperationsSBoolean.
     */
    @Test void historicalUStringAndSBooleanSignaturesAreReachable() {
        Type uString=TypeFactory.mkUString(), string=TypeFactory.mkString();
        Type integer=TypeFactory.mkInteger(), real=TypeFactory.mkReal();
        Type sBoolean=TypeFactory.mkSBoolean(), uBoolean=TypeFactory.mkUBoolean();
        Type opinions=TypeFactory.mkSequence(sBoolean);

        for (String name : new String[]{"value","confidence","toLowerCase","toUpperCase","size","toString",
                "toInteger","toReal","toBoolean","toUBoolean","character"})
            assertTrue(ExpStdOp.exists(name, new Type[]{uString}), "UString." + name);
        assertTrue(ExpStdOp.exists("setValue", new Type[]{uString, string}));
        assertTrue(ExpStdOp.exists("setConfidence", new Type[]{uString, real}));
        assertTrue(ExpStdOp.exists("at", new Type[]{uString, integer}));
        assertTrue(ExpStdOp.exists("indexOf", new Type[]{uString, string}));
        assertTrue(ExpStdOp.exists("substring", new Type[]{uString, integer, integer}));
        for (String name : new String[]{"+","<","<=",">",">="})
            assertTrue(ExpStdOp.exists(name, new Type[]{uString, uString}), "UString " + name);

        for (String name : new String[]{"projection","belief","disbelief","baseRate","uncertainty","certainty",
                "getRelativeWeight","uncertaintyMaximized","uncertainOpinion","isAbsolute","isVacuous",
                "isDogmatic","isMaximizedUncertainty","toUBoolean","toString","not"})
            assertTrue(ExpStdOp.exists(name, new Type[]{sBoolean}), "SBoolean." + name);
        for (String name : new String[]{"isCertain","isUncertain"})
            assertTrue(ExpStdOp.exists(name, new Type[]{sBoolean, real}), "SBoolean." + name);
        for (String name : new String[]{"and","or","xor","implies","equivalent","min","max"})
            assertTrue(ExpStdOp.exists(name, new Type[]{sBoolean, sBoolean}), "SBoolean " + name);
        for (String name : new String[]{"projectiveDistance","conjunctiveCertainty","degreeOfConflict"})
            assertTrue(ExpStdOp.exists(name, new Type[]{sBoolean, sBoolean}), "SBoolean." + name);
        assertTrue(ExpStdOp.exists("deduceY", new Type[]{sBoolean, sBoolean, sBoolean}));
        assertTrue(ExpStdOp.exists("applyOn", new Type[]{sBoolean, uBoolean}));
        // Fusion is receiver plus a collection of further opinions.
        for (String name : new String[]{"minimumBeliefFusion","majorityBeliefFusion","beliefConstraintFusion",
                "averageBeliefFusion","aleatoryCumulativeBeliefFusion","epistemicCumulativeBeliefFusion",
                "weightedBeliefFusion","consensusAndCompromiseFusion","discount"})
            assertTrue(ExpStdOp.exists(name, new Type[]{sBoolean, opinions}), "SBoolean." + name);
    }
    @Test void uncertainCountPreservesHistoricalUndefinedCollectionHandling() {
        Value result=new Op_collection_uCount().eval(null,
            new Value[]{UndefinedValue.instance,new UStringValue("x",1)},TypeFactory.mkInteger());
        assertTrue(result instanceof IntegerValue);
        assertEquals(0,((IntegerValue)result).value());
    }

    /**
     * The historical subjective operations accept operands that are <em>kind of</em>
     * SBoolean, so a plain or uncertain Boolean embeds as an opinion. Narrowing
     * them to type-of SBoolean made these historically legal expressions
     * un-compilable and left the value-level coercion unreachable.
     */
    @Test void subjectiveOperandsAcceptKindOfSBoolean() {
        Type sBoolean=TypeFactory.mkSBoolean(), uBoolean=TypeFactory.mkUBoolean(), bool=TypeFactory.mkBoolean();
        Type opinions=TypeFactory.mkSequence(sBoolean);
        for(String name:new String[]{"min","max","projectiveDistance","conjunctiveCertainty","degreeOfConflict"}) {
            assertTrue(ExpStdOp.exists(name,new Type[]{uBoolean,sBoolean}),name+"(UBoolean, SBoolean)");
            assertTrue(ExpStdOp.exists(name,new Type[]{sBoolean,uBoolean}),name+"(SBoolean, UBoolean)");
            assertTrue(ExpStdOp.exists(name,new Type[]{bool,sBoolean}),name+"(Boolean, SBoolean)");
            // two ordinary Booleans must still not reach the subjective operator
            assertFalse(ExpStdOp.exists(name,new Type[]{bool,bool}),name+"(Boolean, Boolean)");
        }
        assertTrue(ExpStdOp.exists("deduceY",new Type[]{uBoolean,sBoolean,sBoolean}));
        assertTrue(ExpStdOp.exists("deduceY",new Type[]{bool,sBoolean,sBoolean}));
        assertFalse(ExpStdOp.exists("deduceY",new Type[]{bool,bool,bool}));
        assertTrue(ExpStdOp.exists("applyOn",new Type[]{uBoolean,uBoolean}));
        assertTrue(ExpStdOp.exists("minimumBeliefFusion",new Type[]{uBoolean,opinions}));
        assertTrue(ExpStdOp.exists("discount",new Type[]{bool,opinions}));
    }

    /**
     * A fusion argument whose elements cannot become opinions has to be rejected
     * while compiling; it used to type-check and then escape the evaluator as a
     * ClassCastException.
     */
    @Test void fusionRejectsCollectionsOfUnrelatedElements() {
        Type sBoolean=TypeFactory.mkSBoolean();
        for(String name:new String[]{"minimumBeliefFusion","averageBeliefFusion","weightedBeliefFusion",
                "consensusAndCompromiseFusion","discount"}) {
            assertFalse(ExpStdOp.exists(name,new Type[]{sBoolean,TypeFactory.mkSequence(TypeFactory.mkInteger())}),name+" over Sequence(Integer)");
            assertFalse(ExpStdOp.exists(name,new Type[]{sBoolean,TypeFactory.mkSequence(TypeFactory.mkUString())}),name+" over Sequence(UString)");
            assertTrue(ExpStdOp.exists(name,new Type[]{sBoolean,TypeFactory.mkSequence(sBoolean)}),name+" over Sequence(SBoolean)");
            assertTrue(ExpStdOp.exists(name,new Type[]{sBoolean,TypeFactory.mkSequence(TypeFactory.mkUBoolean())}),name+" over Sequence(UBoolean)");
        }
    }
}
