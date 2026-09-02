package org.tzi.use.smt.config;

import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

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
    this.activeInvariants = sortedByQualifiedName(activeInvariants);
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

  /**
   * The active-invariant set, ORDERED BY QUALIFIED NAME -- never {@code Set.copyOf}.
   *
   * <p>{@code Set.copyOf} returns an {@code ImmutableCollections.SetN} whose iteration order
   * derives from a SALT seeded from {@code System.nanoTime()} at class-initialization time, so it
   * differs on every JVM launch for one identical input (measured: 8 launches, 7 distinct orders
   * over the same five names). {@link org.tzi.use.smt.encode.QueryCompiler}'s {@code allAreTrue}
   * and {@code others} iterate exactly this set to build their conjunct list, so the emitted
   * SMT-LIB text for one fixed model+configuration was not byte-reproducible across runs -- which
   * defeats the stated purpose of {@code QueryCompiler.flattenAnd} (emit the byte-identical term
   * the SATISFY macro always emitted) and puts run-to-run noise into solve paths and benchmark
   * timings.
   *
   * <p>SORTING is the fix rather than preserving the caller's iteration order, because preserving
   * it would not have been enough: the {@code LinkedHashSet} {@code ConfigurationReader} builds is
   * filled by iterating {@code ConfigurationVocabulary.invariantNames()}, itself a {@code
   * Set.copyOf}, so the salt already leaks in one level upstream of this constructor. Sorting is
   * total and caller-independent -- a caller passing a {@code Set.of(...)} literal (as most tests
   * do) gets the same determinism as the production reader path. Nothing is lost: {@code and} and
   * {@code or} are commutative, so conjunct order carries no semantics, and verdict order is
   * driven by the model's own invariant list, not by this set.
   */
  private static Set<String> sortedByQualifiedName(Set<String> activeInvariants) {
    return Collections.unmodifiableSet(new LinkedHashSet<>(new TreeSet<>(activeInvariants)));
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
