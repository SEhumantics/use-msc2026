package org.tzi.use.smt.config;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Determines which invariant/mode pairs must be translated before a query can be compiled. */
public final class QueryRequirements {
  private QueryRequirements() {}

  public static Map<String, Set<TranslationMode>> requiredClassifications(
      QueryExpr query, Set<String> activeInvariants) {
    Map<String, Set<TranslationMode>> result = new LinkedHashMap<>();
    collect(query, activeInvariants, result);
    Map<String, Set<TranslationMode>> immutable = new LinkedHashMap<>();
    result.forEach((name, modes) -> immutable.put(name, Set.copyOf(modes)));
    return Map.copyOf(immutable);
  }

  private static void collect(
      QueryExpr query,
      Set<String> activeInvariants,
      Map<String, Set<TranslationMode>> result) {
    switch (query) {
      case QueryExpr.Profiled profiled -> collect(profiled.expression(), activeInvariants, result);
      case QueryExpr.Classification atom -> add(result, atom.invariantName(), atom.mode());
      case QueryExpr.Aggregate aggregate -> addAll(result, activeInvariants, aggregate.mode());
      case QueryExpr.And and -> {
        collect(and.left(), activeInvariants, result);
        collect(and.right(), activeInvariants, result);
      }
      case QueryExpr.Or or -> {
        collect(or.left(), activeInvariants, result);
        collect(or.right(), activeInvariants, result);
      }
      case QueryExpr.Not not -> collect(not.operand(), activeInvariants, result);
      case QueryExpr.Satisfy ignored ->
          addAll(result, activeInvariants, TranslationMode.UNCERTAIN);
      case QueryExpr.Counterexample counterexample -> {
        addAll(result, activeInvariants, TranslationMode.UNCERTAIN);
        add(result, counterexample.invariantName(), TranslationMode.UNCERTAIN);
      }
      case QueryExpr.InvariantIndependence ignored ->
          addAll(result, activeInvariants, TranslationMode.UNCERTAIN);
      case QueryExpr.Fragile fragile -> {
        addAll(result, activeInvariants, TranslationMode.UNCERTAIN);
        add(result, fragile.invariantName(), TranslationMode.NOMINAL);
      }
    }
  }

  private static void addAll(
      Map<String, Set<TranslationMode>> result,
      Set<String> invariantNames,
      TranslationMode mode) {
    invariantNames.forEach(name -> add(result, name, mode));
  }

  private static void add(
      Map<String, Set<TranslationMode>> result, String invariantName, TranslationMode mode) {
    result.computeIfAbsent(invariantName, ignored -> new LinkedHashSet<>()).add(mode);
  }
}
