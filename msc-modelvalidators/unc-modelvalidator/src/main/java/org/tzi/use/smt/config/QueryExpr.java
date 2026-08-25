package org.tzi.use.smt.config;

/**
 * Query intent carried by an analysis configuration.
 *
 * <p>The recursive query language is introduced in Phase 4. Until then every configuration has
 * the explicit {@link #SATISFY} intent rather than a null placeholder.
 */
public sealed interface QueryExpr permits QueryExpr.Satisfy {
    QueryExpr SATISFY = new Satisfy();

    /** Search for a witness satisfying all active invariants. */
    record Satisfy() implements QueryExpr {
    }
}
