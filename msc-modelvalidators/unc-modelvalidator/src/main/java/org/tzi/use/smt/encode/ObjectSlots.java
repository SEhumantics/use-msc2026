package org.tzi.use.smt.encode;

import java.util.ArrayList;
import java.util.List;

/**
 * The declared identity and existence variables for one class's candidate object slots, plus the
 * OBJECT NAME each slot carries.
 *
 * <p>{@code objectNames} is always exactly {@code capacity()} long. Slots the configuration
 * predefined take their configured name; the rest keep the generated {@code ClassName + index}
 * spelling {@code SystemStateReconstructor} has always used, so a scenario that predefines nothing
 * reconstructs byte-identically to before this component existed. Assigning the configured names to
 * the LEADING slots, in order, and padding the tail is the incumbent's own layout ({@code
 * ClassConfigurator.generateObjectsTuple}, kk-modelvalidator lines 20-36), and naming the
 * reconstructed object after the configured identity matches {@code ObjectStrategy.createElement}
 * (lines 34-46), which strips the {@code ClassName_} prefix off the Kodkod atom.
 */
public record ObjectSlots(
    String className, List<String> slotNames, List<String> existsNames, List<String> objectNames) {
  public ObjectSlots {
    slotNames = List.copyOf(slotNames);
    existsNames = List.copyOf(existsNames);
    objectNames = List.copyOf(objectNames);
  }

  /** Slots with no predefined identities: every slot keeps its generated name. */
  public ObjectSlots(String className, List<String> slotNames, List<String> existsNames) {
    this(className, slotNames, existsNames, generatedNames(className, slotNames.size()));
  }

  public int capacity() {
    return slotNames.size();
  }

  /** The slot carrying {@code objectName}, or {@code -1} when no slot does. */
  public int slotOf(String objectName) {
    return objectNames.indexOf(objectName);
  }

  private static List<String> generatedNames(String className, int capacity) {
    List<String> names = new ArrayList<>(capacity);
    for (int index = 0; index < capacity; index++) {
      names.add(className + index);
    }
    return names;
  }
}
