package org.tzi.use.smt.config;

import java.math.BigDecimal;
import java.util.List;

/** Candidate values or numeric bounds configured for one attribute component. */
public record AttributeDomain(
    String className,
    String attributeName,
    String component,
    List<String> enumeratedValues,
    BigDecimal lowerBound,
    BigDecimal upperBound) {
  public AttributeDomain {
    enumeratedValues = List.copyOf(enumeratedValues);
  }
}
