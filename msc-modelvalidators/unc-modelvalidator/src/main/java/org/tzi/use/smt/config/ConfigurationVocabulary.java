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
 *
 * <p>{@code abstractClassNames} is the subset of {@code classNames} whose {@code
 * MClassifier.isAbstract()} (use-core/src/main/java/org/tzi/use/uml/mm/MClassifier.java:57) is
 * true. It exists so {@link ConfigurationReader#normalize} can refuse to let an abstract class's
 * own direct-instance bound be configured nonzero -- see that method for why: without it,
 * ConfigurationReader has no way to tell an abstract class from a concrete one at all, and
 * defaults every unconfigured class's {@code _min}/{@code _max} to 1/1 identically (docs/
 * modelvalidator-feature-matrix.json, feature {@code class.abstract}).
 */
public record ConfigurationVocabulary(
    Set<String> classNames,
    Set<String> associationNames,
    Set<String> attributeNames,
    Set<String> stringAttributeNames,
    Set<String> invariantNames,
    Set<String> abstractClassNames) {
  public ConfigurationVocabulary {
    classNames = Set.copyOf(classNames);
    associationNames = Set.copyOf(associationNames);
    attributeNames = Set.copyOf(attributeNames);
    stringAttributeNames = Set.copyOf(stringAttributeNames);
    invariantNames = Set.copyOf(invariantNames);
    abstractClassNames = Set.copyOf(abstractClassNames);
  }

  public static ConfigurationVocabulary empty() {
    return new ConfigurationVocabulary(
        Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Set.of());
  }

  /**
   * Attribute names use the incumbent's {@code Class_attribute} spelling. {@code
   * abstractClassNames} defaults to empty -- every prior call site of this 5-argument overload
   * hand-writes a small fixed vocabulary for one test, none of which name an abstract class.
   */
  public static ConfigurationVocabulary of(
      Set<String> classNames,
      Set<String> associationNames,
      Set<String> attributeNames,
      Set<String> stringAttributeNames,
      Set<String> invariantNames) {
    return new ConfigurationVocabulary(
        classNames,
        associationNames,
        attributeNames,
        stringAttributeNames,
        invariantNames,
        Set.of());
  }

  /** True for an attribute whose declared (element) type is String, in {@code Class_attribute}. */
  public boolean isStringAttribute(String attributeName) {
    return stringAttributeNames.contains(attributeName);
  }

  /** True for a class name declared {@code abstract} in the loaded USE model. */
  public boolean isAbstractClass(String className) {
    return abstractClassNames.contains(className);
  }

  /**
   * The {@code Class::invariant} spelling of a flat {@code Class_invariant} vocabulary key -- the
   * form {@code MClassInvariant.qualifiedName()} uses, and therefore the only form {@code
   * SmtModelFinder} and {@code QueryCompiler} can match an invariant by.
   *
   * <p>Resolved by {@link #owningClass} against the model's ACTUAL class names, not by splitting at
   * the first underscore. USE's {@code IDENT} grammar permits underscores inside a class name, so
   * {@code Order_Item_PriceIsFortyTwo} qualifies to {@code Order_Item::PriceIsFortyTwo}; the
   * first-underscore split produced {@code Order::Item_PriceIsFortyTwo}, a name no invariant in any
   * model carries, which made an ACTIVE or NEGATED invariant on such a class abort the solve
   * outright and made every {@code query} spelling of it unreachable.
   */
  public String qualifiedInvariantName(String invariantKey) {
    String owner = owningClass(invariantKey, classNames);
    if (owner == null) {
      throw new ConfigurationReadException(
          "invariant vocabulary entry '"
              + invariantKey
              + "' does not begin with any of the model's class names followed by '_'; expected"
              + " Class_invariant");
    }
    return owner + "::" + invariantKey.substring(owner.length() + 1);
  }

  /**
   * The owning class of a flat {@code Class_member} vocabulary key, or {@code null} when no class
   * name prefixes it at all: among every real class name that is a {@code ClassName_} prefix of
   * {@code key}, the LONGEST one wins -- the "maximal munch" rule that resolves the {@code
   * Class_Item_member} ambiguity correctly whenever both {@code Class} and {@code Class_Item} are
   * declared classes. Shared by the attribute keys ({@code ConfigurationReader.splitAttribute}) and
   * the invariant keys ({@link #qualifiedInvariantName}), which are built to the same {@code
   * name + "_" + member} shape and so must be taken apart by the same rule.
   */
  static String owningClass(String key, Set<String> classNames) {
    String owner = null;
    for (String className : classNames) {
      String prefix = className + "_";
      if (key.startsWith(prefix)
          && key.length() > prefix.length()
          && (owner == null || className.length() > owner.length())) {
        owner = className;
      }
    }
    return owner;
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
    Set<String> abstractClassNames = new LinkedHashSet<>();
    for (MClass cls : model.classes()) {
      classNames.add(cls.name());
      if (cls.isAbstract()) {
        abstractClassNames.add(cls.name());
      }
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
    return new ConfigurationVocabulary(
        classNames,
        associationNames,
        attributeNames,
        stringAttributeNames,
        invariantNames,
        abstractClassNames);
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
