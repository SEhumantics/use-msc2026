package org.tzi.use.smt.encode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.tzi.use.smt.config.InvariantOutcome;
import org.tzi.use.smt.config.QueryExpr;
import org.tzi.use.smt.config.ScenarioProfile;
import org.tzi.use.smt.config.TranslationMode;
import org.tzi.use.smt.solver.Smt;
import org.tzi.use.smt.solver.SmtTerm;

/**
 * Lowers a typed {@link QueryExpr} over the ALREADY-REIFIED {@code def[m,i]}/{@code val[m,i]}
 * symbols Milestone 4.2 bound (see {@link InvariantAssembler#reify}). It never retranslates an
 * invariant body: a macro that mentions the same invariant twice still costs one reification, which
 * is the whole reason 4.2 reified before 4.3 compiles.
 *
 * <p>Milestone 4.3 admits exactly the two witness predicates the proposal's "Common core and
 * complete query-mode semantics" fixes, both under the EXISTS profile:
 *
 * <pre>
 *   SATISFY           = B_K and every active invariant is T_U
 *   COUNTEREXAMPLE(j) = B_K and target j is F_U and every other active invariant is T_U
 * </pre>
 *
 * <p>{@code B_K} (structural/domain constraints) is asserted unconditionally by the caller and is
 * deliberately not part of what this class emits. {@code F_U} is DEFINED-false ({@code def and not
 * val}), never "not true" -- an undefined target would otherwise let invalid navigation or an
 * illegal confidence argument masquerade as a violation. Every other query shape fails closed,
 * naming the milestone that owns it, so nothing is silently degraded to a weaker query.
 */
public final class QueryCompiler {
  private QueryCompiler() {}

  /**
   * One query's solver obligation together with the per-invariant classification it CLAIMS about
   * any witness that satisfies it. The second half is what the independent USE oracle is held to
   * afterwards (see {@code QueryWitnessChecker}), so the SMT constraint and the oracle contract
   * cannot drift apart: they are produced here, once, from the same query.
   */
  public record Obligation(SmtTerm constraint, Map<String, InvariantOutcome> expectedOutcomes) {
    public Obligation {
      expectedOutcomes = Map.copyOf(expectedOutcomes);
    }
  }

  public static Obligation compile(
      QueryExpr query,
      Set<String> activeInvariants,
      Map<FragmentChecker.ClassificationKey, InvariantClassification> classifications) {
    QueryExpr body = query;
    if (query instanceof QueryExpr.Profiled profiled) {
      if (profiled.profile() != ScenarioProfile.EXISTS) {
        throw new IllegalArgumentException(
            "scenario profile "
                + profiled.profile()
                + " has no executable oracle yet; it starts at Milestone 4.6 and is never"
                + " silently degraded to EXISTS");
      }
      body = profiled.expression();
    }
    return switch (body) {
      case QueryExpr.Satisfy ignored -> satisfy(activeInvariants, classifications);
      case QueryExpr.Counterexample counterexample ->
          counterexample(counterexample.invariantName(), activeInvariants, classifications);
      case QueryExpr.InvariantIndependence ignored ->
          throw new IllegalArgumentException(
              "invariant-independence is a sweep of one counterexample obligation per active"
                  + " invariant, not a single solve; run it through"
                  + " SmtModelFinder.independenceSweep (Milestone 4.3)");
      case QueryExpr.Fragile ignored ->
          throw new IllegalArgumentException(
              "fragile(j) needs the nominal-erasure oracle and starts at Milestone 4.5");
      case QueryExpr.Classification ignored ->
          throw new IllegalArgumentException(
              "explicit true/false/undef atoms start at Milestone 4.4");
      case QueryExpr.Aggregate ignored ->
          throw new IllegalArgumentException(
              "the 'all'/'others' aggregates outside a macro start at Milestone 4.4");
      case QueryExpr.And ignored ->
          throw new IllegalArgumentException("Boolean query connectives start at Milestone 4.4");
      case QueryExpr.Or ignored ->
          throw new IllegalArgumentException("Boolean query connectives start at Milestone 4.4");
      case QueryExpr.Not ignored ->
          throw new IllegalArgumentException("Boolean query connectives start at Milestone 4.4");
      case QueryExpr.Profiled ignored ->
          throw new IllegalArgumentException("a query can have only one scenario profile");
    };
  }

  private static Obligation satisfy(
      Set<String> activeInvariants,
      Map<FragmentChecker.ClassificationKey, InvariantClassification> classifications) {
    List<SmtTerm> conjuncts = new ArrayList<>();
    Map<String, InvariantOutcome> expected = new LinkedHashMap<>();
    for (String active : activeInvariants) {
      conjuncts.add(uncertain(active, classifications).trueTerm());
      expected.put(active, InvariantOutcome.TRUE);
    }
    return new Obligation(Smt.and(conjuncts), expected);
  }

  private static Obligation counterexample(
      String target,
      Set<String> activeInvariants,
      Map<FragmentChecker.ClassificationKey, InvariantClassification> classifications) {
    if (!activeInvariants.contains(target)) {
      throw new IllegalArgumentException(
          "counterexample target '"
              + target
              + "' is not an active invariant; the witness predicate is defined over the complete"
              + " active-invariant set A_K, so an inactive target has no attribution to make");
    }
    List<SmtTerm> conjuncts = new ArrayList<>();
    Map<String, InvariantOutcome> expected = new LinkedHashMap<>();
    conjuncts.add(uncertain(target, classifications).falseTerm());
    expected.put(target, InvariantOutcome.FALSE);
    for (String active : activeInvariants) {
      if (active.equals(target)) {
        continue;
      }
      conjuncts.add(uncertain(active, classifications).trueTerm());
      expected.put(active, InvariantOutcome.TRUE);
    }
    return new Obligation(Smt.and(conjuncts), expected);
  }

  private static InvariantClassification uncertain(
      String invariantName,
      Map<FragmentChecker.ClassificationKey, InvariantClassification> classifications) {
    InvariantClassification classification =
        classifications.get(
            new FragmentChecker.ClassificationKey(invariantName, TranslationMode.UNCERTAIN));
    if (classification == null) {
      throw new IllegalArgumentException(
          "no reified uncertain classification for invariant '" + invariantName + "'");
    }
    return classification;
  }
}
