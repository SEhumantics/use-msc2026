package org.tzi.use.smt.encode;

import java.util.LinkedHashMap;
import java.util.Map;
import org.tzi.use.smt.config.AttributeDomain;

public record TranslationContext(Map<String, VariableBinding> variables, Map<String, AttributeValues> attributes,
                                 Map<String, AttributeDomain> domains, Map<String, ObjectSlots> slotsByClass) {
    public AttributeValues attributeValues(String className, String attributeName) {
        return require(attributes, className, attributeName, "attribute values");
    }

    public AttributeDomain attributeDomain(String className, String attributeName) {
        return require(domains, className, attributeName, "attribute domain");
    }

    public VariableBinding binding(String variableName) {
        VariableBinding binding = variables.get(variableName);
        if (binding == null) {
            throw new SmtTranslationException("unbound OCL variable '" + variableName + "'");
        }
        return binding;
    }

    public ObjectSlots slotsFor(String className) {
        ObjectSlots slots = slotsByClass.get(className);
        if (slots == null) {
            throw new SmtTranslationException("no object slots registered for class " + className);
        }
        return slots;
    }

    /** Returns a new context with one additional (or replaced) variable binding. */
    public TranslationContext withBinding(String variableName, VariableBinding binding) {
        Map<String, VariableBinding> extended = new LinkedHashMap<>(variables);
        extended.put(variableName, binding);
        return new TranslationContext(extended, attributes, domains, slotsByClass);
    }

    private static <T> T require(Map<String, T> map, String className, String attributeName, String what) {
        T value = map.get(className + "." + attributeName);
        if (value == null) {
            throw new SmtTranslationException("no " + what + " registered for " + className + "." + attributeName);
        }
        return value;
    }
}
