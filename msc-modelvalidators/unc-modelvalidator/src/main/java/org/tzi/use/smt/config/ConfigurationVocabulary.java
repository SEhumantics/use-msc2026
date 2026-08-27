package org.tzi.use.smt.config;

import java.util.LinkedHashSet;
import java.util.Set;
import org.tzi.use.uml.mm.MAttribute;
import org.tzi.use.uml.mm.MClass;
import org.tzi.use.uml.mm.MClassInvariant;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.ocl.type.CollectionType;
import org.tzi.use.uml.ocl.type.Type;

/**
 * Names from the loaded USE model used to disambiguate the flat legacy key vocabulary.
 *
 * <p>{@code stringAttributeNames} is the subset of {@code attributeNames} whose declared type is
 * String -- or a collection whose element type is String. It exists because the legacy {@code
 * .properties} format is untyped text and the reader has to know which enumerated domains are
 * String-typed to interpret their quoting: the incumbent's {@code
 * PropertyConfigurationVisitor.adjustElement} strips quote characters for String attributes ONLY,
 * and the same file cannot be read correctly without that distinction. It is a required component,
 * not an optional one, so a hand-built vocabulary cannot silently default to "no attribute is a
 * String" and re-open the defect this closed.
 */
public record ConfigurationVocabulary(
    Set<String> classNames,
    Set<String> associationNames,
    Set<String> attributeNames,
    Set<String> stringAttributeNames,
    Set<String> invariantNames) {
  public ConfigurationVocabulary {
    classNames = Set.copyOf(classNames);
    associationNames = Set.copyOf(associationNames);
    attributeNames = Set.copyOf(attributeNames);
    stringAttributeNames = Set.copyOf(stringAttributeNames);
    invariantNames = Set.copyOf(invariantNames);
  }

  public static ConfigurationVocabulary empty() {
    return new ConfigurationVocabulary(Set.of(), Set.of(), Set.of(), Set.of(), Set.of());
  }

  /** Attribute names use the incumbent's {@code Class_attribute} spelling. */
  public static ConfigurationVocabulary of(
      Set<String> classNames,
      Set<String> associationNames,
      Set<String> attributeNames,
      Set<String> stringAttributeNames,
      Set<String> invariantNames) {
    return new ConfigurationVocabulary(
        classNames, associationNames, attributeNames, stringAttributeNames, invariantNames);
  }

  /** True for an attribute whose declared (element) type is String, in {@code Class_attribute}. */
  public boolean isStringAttribute(String attributeName) {
    return stringAttributeNames.contains(attributeName);
  }

  /**
   * Derives the vocabulary directly from a compiled model, instead of naming every class/
   * attribute/association/invariant by hand -- every prior use of this record hand-wrote a small
   * fixed subset for one test; a real driver needs the whole model's real vocabulary.
   */
  public static ConfigurationVocabulary fromModel(MModel model) {
    Set<String> classNames = new LinkedHashSet<>();
    Set<String> attributeNames = new LinkedHashSet<>();
    Set<String> stringAttributeNames = new LinkedHashSet<>();
    for (MClass cls : model.classes()) {
      classNames.add(cls.name());
      for (MAttribute attribute : cls.attributes()) {
        String key = cls.name() + "_" + attribute.name();
        attributeNames.add(key);
        if (isStringTyped(attribute.type())) {
          stringAttributeNames.add(key);
        }
      }
    }
    Set<String> associationNames = new LinkedHashSet<>();
    model.associations().forEach(association -> associationNames.add(association.name()));
    Set<String> invariantNames = new LinkedHashSet<>();
    for (MClassInvariant invariant : model.classInvariants()) {
      invariantNames.add(invariant.cls().name() + "_" + invariant.name());
    }
    return of(classNames, associationNames, attributeNames, stringAttributeNames, invariantNames);
  }

  /**
   * Mirrors the incumbent's collection unwrap in {@code adjustElement}: a {@code Set(String)}
   * attribute's configured elements are Strings and are quoted the same way.
   *
   * <p>{@code UString} counts too, and must. Its {@code _value} component configures SPELLINGS,
   * written in the {@code .properties} file with the same {@code 'quotes'} a String domain uses --
   * {@code Camera_id_value = Set{'ALLY-7'}} -- so without this arm the quotes would survive into
   * the candidate list and every spelling would silently fail to match the exact string an
   * invariant names. The {@code _confidence} component is numeric and carries no quotes, so
   * stripping them there is a no-op rather than a hazard.
   */
  private static boolean isStringTyped(Type type) {
    Type element = type instanceof CollectionType collection ? collection.elemType() : type;
    return element.isTypeOfString() || element.isTypeOfUString();
  }
}
