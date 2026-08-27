package org.tzi.use.smt.config;

import java.util.List;

/**
 * Inclusive object-count bounds for one UML class, plus the PREDEFINED OBJECT NAMES its bare {@code
 * ClassName} key configured (empty when it configured none).
 *
 * <p>The names label slots; they do not size the population. That is the incumbent's rule, not a
 * choice made here: {@code PropertyConfigurationVisitor.setClassConfigurator} (kk-modelvalidator,
 * lines 328-333) hands the name list to {@code ClassConfigurator.setSpecificValues} -- which sets
 * min=max=|names| (ClassConfigurator lines 49-52) -- and then UNCONDITIONALLY calls {@code
 * setLimits(Class_min, Class_max)} with {@code DefaultConfigurationValues.objectsPerClassMin/Max}
 * (1/1) as the error values, overwriting them again. {@code ClassConfigurator.generateObjectsTuple}
 * (lines 20-36) then assigns the names to the first slots in order and pads whatever is left with
 * generated ones, which is exactly how {@link org.tzi.use.smt.encode.ObjectSlots} carries them.
 */
public record ClassScope(String className, int min, int max, List<String> objectNames) {
  public ClassScope {
    objectNames = List.copyOf(objectNames);
  }

  /** A class whose bare key configured no object names -- the shape every prior call site used. */
  public ClassScope(String className, int min, int max) {
    this(className, min, max, List.of());
  }
}
