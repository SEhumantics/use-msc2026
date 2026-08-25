package org.tzi.use.smt.encode;

import java.util.List;

/** The declared identity and existence variables for one class's candidate object slots. */
public record ObjectSlots(String className, List<String> slotNames, List<String> existsNames) {
    public ObjectSlots {
        slotNames = List.copyOf(slotNames);
        existsNames = List.copyOf(existsNames);
    }

    public int capacity() {
        return slotNames.size();
    }
}
