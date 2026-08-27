package org.tzi.use.smt.config;

import java.util.List;

/**
 * Inclusive link-count bounds for one UML association, plus the PREDEFINED LINKS its bare {@code
 * AssociationName} key configured (empty when it configured none).
 *
 * <p>Each element of {@code links} is one tuple of object names in the association's DECLARED end
 * order -- position {@code i} belongs to {@code associationEnds().get(i)}, which is the order
 * {@code PropertyConfigurationVisitor.readComplexElements} (kk-modelvalidator, lines 461-467)
 * validates each element against and the order {@code AssociationConfigurator.lowerBound} (lines
 * 49-64) then builds its atoms in. The tuples are FORCED, not permitted: they go into the
 * relation's LOWER bound. Nothing infers symmetry, which is why {@code GraphColoring.properties}
 * spells out both directions of every undirected edge.
 */
public record AssociationScope(String associationName, int min, int max, List<List<String>> links) {
  public AssociationScope {
    links = links.stream().map(List::copyOf).toList();
  }

  /** An association whose bare key configured no links -- the shape every prior call site used. */
  public AssociationScope(String associationName, int min, int max) {
    this(associationName, min, max, List.of());
  }
}
