package org.tzi.use.smt.config;

import java.util.List;
import java.util.Set;

/** Names from the loaded USE model used to disambiguate the flat legacy key vocabulary. */
public record ConfigurationVocabulary(Set<String> classNames,
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
    public static ConfigurationVocabulary of(Set<String> classNames, Set<String> associationNames,
                                             Set<String> attributeNames, Set<String> invariantNames) {
        return new ConfigurationVocabulary(classNames, associationNames, attributeNames, invariantNames);
    }
}
