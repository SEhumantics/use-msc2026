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

    public AnalysisConfiguration(List<ClassScope> classScopes,
                                 List<AssociationScope> associationScopes,
                                 List<AttributeDomain> attributeDomains,
                                 Set<String> activeInvariants,
                                 QueryExpr query,
                                 Duration timeout,
                                 int modelLimit) {
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
    }

    public List<ClassScope> classScopes() { return classScopes; }
    public List<AssociationScope> associationScopes() { return associationScopes; }
    public List<AttributeDomain> attributeDomains() { return attributeDomains; }
    public Set<String> activeInvariants() { return activeInvariants; }
    public QueryExpr query() { return query; }
    public Duration timeout() { return timeout; }
    public int modelLimit() { return modelLimit; }
}
