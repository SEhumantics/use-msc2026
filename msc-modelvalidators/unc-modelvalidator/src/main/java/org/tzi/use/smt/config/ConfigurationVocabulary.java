package org.tzi.use.smt.config;

import java.util.LinkedHashSet;
import java.util.Set;
import org.tzi.use.uml.mm.MAttribute;
import org.tzi.use.uml.mm.MClass;
import org.tzi.use.uml.mm.MClassInvariant;
import org.tzi.use.uml.mm.MModel;

/** Names from the loaded USE model used to disambiguate the flat legacy key vocabulary. */
public record ConfigurationVocabulary(
    Set<String> classNames,
    Set<String> associationNames,
    Set<String> attributeNames,
    Set<String> invariantNames) {
  public ConfigurationVocabulary {
    classNames = Set.copyOf(classNames);
    associationNames = Set.copyOf(associationNames);
    attributeNames = Set.copyOf(attributeNames);
    invariantNames = Set.copyOf(invariantNames);
  }

  public static ConfigurationVocabulary empty() {
    return new ConfigurationVocabulary(Set.of(), Set.of(), Set.of(), Set.of());
  }

  /** Attribute names use the incumbent's {@code Class_attribute} spelling. */
  public static ConfigurationVocabulary of(
      Set<String> classNames,
      Set<String> associationNames,
      Set<String> attributeNames,
      Set<String> invariantNames) {
    return new ConfigurationVocabulary(
        classNames, associationNames, attributeNames, invariantNames);
  }

  /**
   * Derives the vocabulary directly from a compiled model, instead of naming every class/
   * attribute/association/invariant by hand -- every prior use of this record hand-wrote a small
   * fixed subset for one test; a real driver needs the whole model's real vocabulary.
   */
  public static ConfigurationVocabulary fromModel(MModel model) {
    Set<String> classNames = new LinkedHashSet<>();
    Set<String> attributeNames = new LinkedHashSet<>();
    for (MClass cls : model.classes()) {
      classNames.add(cls.name());
      for (MAttribute attribute : cls.attributes()) {
        attributeNames.add(cls.name() + "_" + attribute.name());
      }
    }
    Set<String> associationNames = new LinkedHashSet<>();
    model.associations().forEach(association -> associationNames.add(association.name()));
    Set<String> invariantNames = new LinkedHashSet<>();
    for (MClassInvariant invariant : model.classInvariants()) {
      invariantNames.add(invariant.cls().name() + "_" + invariant.name());
    }
    return of(classNames, associationNames, attributeNames, invariantNames);
  }
}
