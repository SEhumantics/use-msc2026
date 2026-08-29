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
 *
 * <p>{@code concreteBindings} names the CONCRETE class and slot index each entry stands for. For
 * a plain class view this is the identity ({@code className}, {@code i}); a FOLDED view -- one
 * association end whose declared class has configured subclasses, see {@code SmtModelFinder}'s
 * end-view construction -- concatenates several classes' slots, so grid index k maps to concrete
 * binding {@code concreteBindings().get(k)} and attribute reads dispatch through that binding's
 * class name (inherited attributes are registered per concrete subclass).
 */
public record ObjectSlots(
    String className,
    List<String> slotNames,
    List<String> existsNames,
    List<String> objectNames,
    List<VariableBinding> concreteBindings) {
  public ObjectSlots {
    slotNames = List.copyOf(slotNames);
    existsNames = List.copyOf(existsNames);
    objectNames = List.copyOf(objectNames);
    concreteBindings = List.copyOf(concreteBindings);
  }

  /** Slots with no predefined identities: every slot keeps its generated name. */
  public ObjectSlots(String className, List<String> slotNames, List<String> existsNames) {
    this(className, slotNames, existsNames, generatedNames(className, slotNames.size()));
  }

  public ObjectSlots(
      String className, List<String> slotNames, List<String> existsNames, List<String> objectNames) {
    this(
        className,
        slotNames,
        existsNames,
        objectNames,
        identityBindings(className, slotNames.size()));
  }

  public int capacity() {
    return slotNames.size();
  }

  /** The slot carrying {@code objectName}, or {@code -1} when no slot does. */
  public int slotOf(String objectName) {
    return objectNames.indexOf(objectName);
  }

  /**
   * The grid index of one concrete slot binding, or {@code -1} when this view has no such slot.
   * Identity for a plain class view ({@code (C, i)} is at index {@code i}); a folded view's index
   * depends on the fold order.
   */
  public int indexOf(VariableBinding binding) {
    return concreteBindings.indexOf(binding);
  }

  /** The reconstruction key ({@code concreteClass#index}) of the slot at {@code index}. */
  public String slotKeyAt(int index) {
    VariableBinding concrete = concreteBindings.get(index);
    return concrete.className() + "#" + concrete.slotIndex();
  }

  private static List<VariableBinding> identityBindings(String className, int capacity) {
    List<VariableBinding> bindings = new ArrayList<>(capacity);
    for (int index = 0; index < capacity; index++) {
      bindings.add(new VariableBinding(className, index));
    }
    return bindings;
  }

  private static List<String> generatedNames(String className, int capacity) {
    List<String> names = new ArrayList<>(capacity);
    for (int index = 0; index < capacity; index++) {
      names.add(className + index);
    }
    return names;
  }
}
