package org.tzi.use.smt.encode;

import java.util.ArrayList;
import java.util.List;
import org.tzi.use.uml.mm.MClassifier;

/**
 * The full polymorphic instance range of a class for OCL's {@code X.allInstances()} and an
 * inherited invariant's implicit context (both mean "every X, or any subtype of X") -- a class's
 * own object slots PLUS every transitive subclass's own slots, each slot still bound under its own
 * concrete class name (so attribute lookup resolves through the concrete class, matching how
 * SmtModelFinder registers one inherited attribute's values separately per concrete subclass).
 * Every translation construct before this (ForAll, an invariant's own context variable) bound only
 * to the named class's own slots; that is exactly right for a class with no subclasses (the common
 * case) but silently vacuous for one with subclasses and zero direct instances of its own (an
 * abstract superclass), and silently incomplete for one with subclasses and some direct instances
 * too.
 */
final class PolymorphicRange {
  private PolymorphicRange() {}

  /** One candidate slot in the polymorphic range: its binding, plus its own exists-guard name. */
  record Slot(VariableBinding binding, String existsName) {}

  static List<Slot> slotsOf(MClassifier cls, TranslationContext context) {
    List<Slot> result = new ArrayList<>();
    for (String className : classNamesOf(cls)) {
      ObjectSlots slots = context.slotsFor(className);
      for (int i = 0; i < slots.capacity(); i++) {
        result.add(new Slot(new VariableBinding(className, i), slots.existsNames().get(i)));
      }
    }
    return result;
  }

  private static List<String> classNamesOf(MClassifier cls) {
    List<String> names = new ArrayList<>();
    names.add(cls.name());
    for (MClassifier child : cls.allChildren()) {
      names.add(child.name());
    }
    return names;
  }
}
