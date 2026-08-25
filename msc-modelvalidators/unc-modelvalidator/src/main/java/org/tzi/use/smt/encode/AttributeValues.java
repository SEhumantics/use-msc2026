package org.tzi.use.smt.encode;

import java.util.List;

public record AttributeValues(String className, String attributeName, List<String> valueNames) {
  public AttributeValues {
    valueNames = List.copyOf(valueNames);
  }
}
