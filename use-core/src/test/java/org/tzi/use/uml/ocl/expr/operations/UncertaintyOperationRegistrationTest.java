package org.tzi.use.uml.ocl.expr.operations;

import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Set;
import org.junit.jupiter.api.Test;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;

class UncertaintyOperationRegistrationTest {
    @Test void historicalUncertaintyNamesAreRegistered() {
        Multimap<String,OpGeneric> operations=HashMultimap.create();
        OpGeneric.registerOperations(operations);
        Set<String> names=operations.keySet();
        for(String name:new String[]{"UReal","UInteger","UString","UBoolean","SBoolean","projection","belief","deduceY",
            "minimumBeliefFusion","majorityBeliefFusion","beliefConstraintFusion","averageBeliefFusion",
            "aleatoryCumulativeBeliefFusion","epistemicCumulativeBeliefFusion","weightedBeliefFusion",
            "consensusAndCompromiseFusion","discount","uCount","uCountC","uIncludes","uExcludes"}) {
            if(name.equals("UReal")||name.equals("UInteger")||name.equals("UString")||name.equals("UBoolean")||name.equals("SBoolean")) continue;
            assertTrue(names.contains(name), name);
        }
    }
}
