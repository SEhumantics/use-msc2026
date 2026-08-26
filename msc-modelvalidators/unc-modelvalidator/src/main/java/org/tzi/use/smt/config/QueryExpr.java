package org.tzi.use.smt.config;

/** Typed syntax tree for the recursive witness-query language. */
public sealed interface QueryExpr
    permits QueryExpr.Profiled,
        QueryExpr.Constant,
        QueryExpr.Classification,
        QueryExpr.Aggregate,
        QueryExpr.And,
        QueryExpr.Or,
        QueryExpr.Not,
        QueryExpr.Satisfy,
        QueryExpr.Counterexample,
        QueryExpr.InvariantIndependence,
        QueryExpr.Fragile {
  QueryExpr SATISFY = new Profiled(ScenarioProfile.EXISTS, new Satisfy());

  record Profiled(ScenarioProfile profile, QueryExpr expression) implements QueryExpr {
    public Profiled {
      if (profile == null || expression == null) {
        throw new IllegalArgumentException("query profile and expression are required");
      }
      if (expression instanceof Profiled) {
        throw new IllegalArgumentException("a query can have only one scenario profile");
      }
    }
  }

  /**
   * A constant, deliberately NOT part of §5.2's surface grammar: {@link QueryParser} never produces
   * one. Desugaring an aggregate over an empty invariant set has to denote something, and the empty
   * conjunction is {@code true} exactly as {@code Smt.and} of no conjuncts already is -- writing
   * that down beats silently dropping the aggregate.
   */
  record Constant(boolean value) implements QueryExpr {}

  record Classification(TranslationMode mode, String invariantName, InvariantOutcome outcome)
      implements QueryExpr {}

  enum AggregateScope {
    ALL,
    OTHERS
  }

  record Aggregate(TranslationMode mode, AggregateScope scope) implements QueryExpr {}

  record And(QueryExpr left, QueryExpr right) implements QueryExpr {}

  record Or(QueryExpr left, QueryExpr right) implements QueryExpr {}

  record Not(QueryExpr operand) implements QueryExpr {}

  /** Incumbent-compatible macro for uncertain/all/true. */
  record Satisfy() implements QueryExpr {}

  record Counterexample(String invariantName) implements QueryExpr {}

  record InvariantIndependence() implements QueryExpr {}

  record Fragile(String invariantName) implements QueryExpr {}
}
