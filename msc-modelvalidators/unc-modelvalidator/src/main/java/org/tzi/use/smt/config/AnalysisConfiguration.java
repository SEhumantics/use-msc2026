package org.tzi.use.smt.config;

import java.time.Duration;
import java.util.List;
import java.util.Set;

/** Immutable, solver-independent interpretation of one selected configuration section. */
public final class AnalysisConfiguration {
  private final List<ClassScope> classScopes;
  private final List<AssociationScope> associationScopes;
  private final List<AttributeDomain> attributeDomains;
  private final Set<String> activeInvariants;
  private final QueryExpr query;
  private final Duration timeout;
  private final int modelLimit;
  private final boolean requireAggregationCycleFreedom;

  public AnalysisConfiguration(
      List<ClassScope> classScopes,
      List<AssociationScope> associationScopes,
      List<AttributeDomain> attributeDomains,
      Set<String> activeInvariants,
      QueryExpr query,
      Duration timeout,
      int modelLimit) {
    this(
        classScopes,
        associationScopes,
        attributeDomains,
        activeInvariants,
        query,
        timeout,
        modelLimit,
        false);
  }

  /**
   * @param requireAggregationCycleFreedom the configuration's own {@code aggregationcyclefreeness
   *     = on} toggle -- see {@code SmtModelFinder}'s association-scope handling and {@code
   *     CompositionCycleFreenessEncoder} for what this activates. Defaults to {@code false} via
   *     the 7-argument constructor, deliberately NOT matching the incumbent's own on-by-default
   *     behavior: every existing corpus scenario with a composition/aggregation association
   *     (Sudoku) never mentions this key at all, so defaulting to enforced would add a brand new
   *     constraint to scenarios that never asked for it; only a section that explicitly sets it
   *     activates the check.
   */
  public AnalysisConfiguration(
      List<ClassScope> classScopes,
      List<AssociationScope> associationScopes,
      List<AttributeDomain> attributeDomains,
      Set<String> activeInvariants,
      QueryExpr query,
      Duration timeout,
      int modelLimit,
      boolean requireAggregationCycleFreedom) {
    this.classScopes = List.copyOf(classScopes);
    this.associationScopes = List.copyOf(associationScopes);
    this.attributeDomains = List.copyOf(attributeDomains);
    this.activeInvariants = Set.copyOf(activeInvariants);
    this.query = query == null ? QueryExpr.SATISFY : query;
    if (timeout == null || timeout.isNegative() || timeout.isZero()) {
      throw new IllegalArgumentException("timeout must be positive");
    }
    if (modelLimit < 1) {
      throw new IllegalArgumentException("modelLimit must be at least one");
    }
    this.timeout = timeout;
    this.modelLimit = modelLimit;
    this.requireAggregationCycleFreedom = requireAggregationCycleFreedom;
  }

  public List<ClassScope> classScopes() {
    return classScopes;
  }

  public List<AssociationScope> associationScopes() {
    return associationScopes;
  }

  public List<AttributeDomain> attributeDomains() {
    return attributeDomains;
  }

  public Set<String> activeInvariants() {
    return activeInvariants;
  }

  public QueryExpr query() {
    return query;
  }

  public Duration timeout() {
    return timeout;
  }

  public int modelLimit() {
    return modelLimit;
  }

  public boolean requireAggregationCycleFreedom() {
    return requireAggregationCycleFreedom;
  }
}
