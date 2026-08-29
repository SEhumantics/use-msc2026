package org.tzi.use.smt.encode;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.tzi.use.smt.config.AttributeDomain;
import org.tzi.use.uml.mm.MOperation;

public record TranslationContext(
    Map<String, VariableBinding> variables,
    Map<String, AttributeValues> attributes,
    Map<String, AttributeDomain> domains,
    Map<String, ObjectSlots> slotsByClass,
    Map<String, AssociationLinks> linksByAssociation,
    Map<String, Map<String, MOperation>> operationDispatch) {
  /**
   * Convenience constructor for call sites that do not (yet) carry an operation-dispatch
   * table: operation dispatch then falls back to the statically-declared operation.
   */
  public TranslationContext(
      Map<String, VariableBinding> variables,
      Map<String, AttributeValues> attributes,
      Map<String, AttributeDomain> domains,
      Map<String, ObjectSlots> slotsByClass,
      Map<String, AssociationLinks> linksByAssociation) {
    this(variables, attributes, domains, slotsByClass, linksByAssociation, Collections.emptyMap());
  }

  /**
   * The most specific redefinition of {@code operationName} visible on the receiver's CONCRETE
   * class ({@code className} -- a folded slot's concrete class), or null when the dispatch
   * table has no entry (the statically-declared operation then applies).
   */
  public MOperation dispatchOperation(String className, String operationName) {
    Map<String, MOperation> byName = operationDispatch.get(className);
    return byName == null ? null : byName.get(operationName);
  }
  public AttributeValues attributeValues(String className, String attributeName) {
    return require(attributes, className, attributeName, "attribute values");
  }

  public AttributeDomain attributeDomain(String className, String attributeName) {
    return require(domains, className, attributeName, "attribute domain");
  }

  public AttributeDomain attributeDomain(String className, String attributeName, String component) {
    AttributeDomain domain = domains.get(className + "." + attributeName + "." + component);
    if (domain == null) {
      throw new SmtTranslationException(
          FragmentBoundary.ENCODING_SCOPE,
          "no " + component + " domain registered for " + className + "." + attributeName);
    }
    return domain;
  }

  public VariableBinding binding(String variableName) {
    VariableBinding binding = variables.get(variableName);
    if (binding == null) {
      throw new SmtTranslationException(
          FragmentBoundary.ENCODING_SCOPE, "unbound OCL variable '" + variableName + "'");
    }
    return binding;
  }

  public ObjectSlots slotsFor(String className) {
    ObjectSlots slots = slotsByClass.get(className);
    if (slots == null) {
      throw new SmtTranslationException(
          FragmentBoundary.ENCODING_SCOPE, "no object slots registered for class " + className);
    }
    return slots;
  }

  public AssociationLinks linksFor(String associationName) {
    AssociationLinks links = linksByAssociation.get(associationName);
    if (links == null) {
      throw new SmtTranslationException(
          FragmentBoundary.ENCODING_SCOPE,
          "no association links registered for " + associationName);
    }
    return links;
  }

  /** Returns a new context with one additional (or replaced) variable binding. */
  public TranslationContext withBinding(String variableName, VariableBinding binding) {
    Map<String, VariableBinding> extended = new LinkedHashMap<>(variables);
    extended.put(variableName, binding);
    return new TranslationContext(
        extended, attributes, domains, slotsByClass, linksByAssociation, operationDispatch);
  }

  private static <T> T require(
      Map<String, T> map, String className, String attributeName, String what) {
    T value = map.get(className + "." + attributeName);
    if (value == null) {
      throw new SmtTranslationException(
          FragmentBoundary.ENCODING_SCOPE,
          "no " + what + " registered for " + className + "." + attributeName);
    }
    return value;
  }
}
