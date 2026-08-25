package org.tzi.use.smt.encode;

import java.util.List;

/**
 * SMT symbols for one attribute on one concrete class's object slots.
 *
 * <p>A crisp attribute uses only {@link #valueNames}. A {@link AttributeType#UREAL} attribute uses
 * the value list for its nominal component and a same-sized parallel {@link #uncertaintyNames}
 * list. Keeping the pair in one record prevents reconstruction and threshold translation from
 * accidentally looking up components belonging to different slots.
 */
public record AttributeValues(
    String className,
    String attributeName,
    AttributeType type,
    List<String> valueNames,
    List<String> uncertaintyNames) {
  public AttributeValues {
    valueNames = List.copyOf(valueNames);
    uncertaintyNames = List.copyOf(uncertaintyNames);
    if (type == AttributeType.UREAL && valueNames.size() != uncertaintyNames.size()) {
      throw new IllegalArgumentException("UReal value/uncertainty symbol counts must match");
    }
    if (type != AttributeType.UREAL && !uncertaintyNames.isEmpty()) {
      throw new IllegalArgumentException("only UReal attributes may have uncertainty symbols");
    }
  }
}
