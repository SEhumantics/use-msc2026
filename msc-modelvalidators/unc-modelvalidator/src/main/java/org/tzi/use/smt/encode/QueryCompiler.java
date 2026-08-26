package org.tzi.use.smt.encode;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
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
 * invariant body: a query that mentions the same invariant several times still costs one
 * reification per (invariant, mode), which is the whole reason 4.2 reified before 4.3 compiled.
 *
 * <p>Milestone 4.4 admits the complete §5.2 algebra:
 *
 * <pre>
 *   Query ::= true(Mode, InvRef) | false(Mode, InvRef) | undef(Mode, InvRef)
 *           | Query and Query | Query or Query | not Query
 *           | Mode all are true | Mode others are true
 * </pre>
 *
 * <p>Compilation happens in two steps, and the split is what keeps the SMT obligation and the
 * independent USE oracle from drifting apart. First DESUGARING rewrites macros and aggregates into
 * a core of atoms, {@code and}, {@code or}, {@code not} and constants; then LOWERING maps that core
 * onto the reified symbols. The very same core is handed to {@code QueryWitnessChecker}, which
 * evaluates it over what USE independently observed -- so an {@code others} expansion or a macro
 * definition cannot mean one thing to the solver and another to the oracle. That generalisation is
 * forced by §8's obligation 6 ("the observed true/false/undefined classifications satisfy the
 * compiled query term"): a disjunction has no single expected-outcome map, because two witnesses of
 * the same query can classify the same invariants differently.
 *
 * <p>The three §5.1 atoms stay mutually exclusive, and in particular {@code false(m,i)} is
 * DEFINED-false ({@code def and not val}), never "not true" -- otherwise invalid navigation or an
 * illegal confidence argument could masquerade as a violation. §5.3's non-distributivity is the
 * direct consequence: {@code not false(m,i)} is {@code undef(m,i) or true(m,i)} and is therefore
 * never a substitute for {@code undef(m,i)}.
 *
 * <p>{@code B_K} (structural/domain constraints) is asserted unconditionally by the caller and is
 * deliberately not part of what this class emits. Every shape a later milestone owns -- the
 * COVER/UNIFORM profiles -- still fails closed by that milestone's name, so nothing is silently
 * degraded to a weaker query.
 */
public final class QueryCompiler {
  private QueryCompiler() {}

  /**
   * One query's solver obligation together with the DESUGARED CORE the independent USE oracle is
   * held to afterwards (see {@code QueryWitnessChecker}). Both halves are produced here, once, from
   * the same query and the same desugaring, so the SMT constraint and the oracle contract cannot
   * disagree about what the query means.
   */
  public record Obligation(SmtTerm constraint, QueryExpr core) {
    public Obligation {
      if (constraint == null || core == null) {
        throw new IllegalArgumentException(
            "an obligation needs both a constraint and a core query");
      }
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
    QueryExpr core = desugar(body, activeInvariants, othersTargetOf(body));
    return new Obligation(lower(core, classifications), core);
  }

  /**
   * Rewrites the surface query into atoms, connectives and constants. Macros are expanded to
   * exactly the witness predicates the proposal's "Common core and complete query-mode semantics"
   * fixes:
   *
   * <pre>
   *   SATISFY           = every active invariant is T_U
   *   COUNTEREXAMPLE(j) = target j is F_U and every other active invariant is T_U
   *   FRAGILE(j)        = erased target j is T_N and U-aware target j is F_U
   *                       and every other active invariant is T_U
   * </pre>
   *
   * <p>All three demand DEFINEDNESS, never merely "not true": {@code T_N} and {@code F_U} are the
   * defined atoms, so invalid navigation or an illegal confidence argument can never masquerade as
   * uncertainty fragility. Because {@code W_FRAGILE(j)} is literally {@code W_CEX(j)} with the
   * extra {@code T_N(E(I_j))} conjunct, the proposal's {@code W_FRAGILE(j) => W_CEX(j)} is a
   * property of this desugaring rather than a separate claim.
   */
  private static QueryExpr desugar(QueryExpr node, Set<String> activeInvariants, String target) {
    return switch (node) {
      case QueryExpr.Constant constant -> constant;
      case QueryExpr.Classification atom -> {
        requireActive(atom.invariantName(), activeInvariants, "classification atom");
        yield atom;
      }
      case QueryExpr.And and ->
          new QueryExpr.And(
              desugar(and.left(), activeInvariants, target),
              desugar(and.right(), activeInvariants, target));
      case QueryExpr.Or or ->
          new QueryExpr.Or(
              desugar(or.left(), activeInvariants, target),
              desugar(or.right(), activeInvariants, target));
      case QueryExpr.Not not -> new QueryExpr.Not(desugar(not.operand(), activeInvariants, target));
      case QueryExpr.Aggregate aggregate ->
          aggregate.scope() == QueryExpr.AggregateScope.ALL
              ? allAreTrue(aggregate.mode(), activeInvariants)
              : allAreTrue(aggregate.mode(), others(activeInvariants, requireOthersTarget(target)));
      case QueryExpr.Satisfy ignored -> allAreTrue(TranslationMode.UNCERTAIN, activeInvariants);
      case QueryExpr.Counterexample counterexample -> {
        String name = counterexample.invariantName();
        requireActive(name, activeInvariants, "counterexample target");
        yield new QueryExpr.And(
            new QueryExpr.Classification(TranslationMode.UNCERTAIN, name, InvariantOutcome.FALSE),
            allAreTrue(TranslationMode.UNCERTAIN, others(activeInvariants, name)));
      }
      case QueryExpr.InvariantIndependence ignored ->
          throw new IllegalArgumentException(
              "invariant-independence is a sweep of one counterexample obligation per active"
                  + " invariant, not a single solve; run it through"
                  + " SmtModelFinder.independenceSweep (Milestone 4.3)");
      case QueryExpr.Fragile fragile -> {
        String name = fragile.invariantName();
        requireActive(name, activeInvariants, "fragile target");
        yield new QueryExpr.And(
            new QueryExpr.And(
                new QueryExpr.Classification(TranslationMode.NOMINAL, name, InvariantOutcome.TRUE),
                new QueryExpr.Classification(
                    TranslationMode.UNCERTAIN, name, InvariantOutcome.FALSE)),
            allAreTrue(TranslationMode.UNCERTAIN, others(activeInvariants, name)));
      }
      case QueryExpr.Profiled ignored ->
          throw new IllegalArgumentException("a query can have only one scenario profile");
    };
  }

  private static SmtTerm lower(
      QueryExpr core, Map<FragmentChecker.ClassificationKey, InvariantClassification> reified) {
    return switch (core) {
      case QueryExpr.Constant constant -> Smt.bool(constant.value());
      case QueryExpr.Classification atom -> {
        InvariantClassification classification = classificationOf(atom, reified);
        yield switch (atom.outcome()) {
          case TRUE -> classification.trueTerm();
          case FALSE -> classification.falseTerm();
          case UNDEFINED -> classification.undefinedTerm();
        };
      }
      case QueryExpr.And and -> Smt.and(flattenAnd(and, reified, new ArrayList<>()));
      case QueryExpr.Or or -> Smt.or(flattenOr(or, reified, new ArrayList<>()));
      case QueryExpr.Not not -> Smt.not(lower(not.operand(), reified));
      default ->
          throw new IllegalStateException(
              "desugaring left a non-core node in the compiled query: " + core);
    };
  }

  /**
   * Flattens a left-associated {@code and} chain into one n-ary SMT application, so the spelled-out
   * {@code uncertain all are true} emits the byte-identical term the SATISFY macro always emitted
   * rather than a differently-parenthesised equivalent. Only chains of the SAME connective are
   * absorbed -- an {@code or} nested inside an {@code and} is lowered as its own subterm, because
   * merging the two would change what the query means.
   */
  private static List<SmtTerm> flattenAnd(
      QueryExpr node,
      Map<FragmentChecker.ClassificationKey, InvariantClassification> reified,
      List<SmtTerm> collected) {
    if (node instanceof QueryExpr.And and) {
      flattenAnd(and.left(), reified, collected);
      flattenAnd(and.right(), reified, collected);
    } else {
      collected.add(lower(node, reified));
    }
    return collected;
  }

  /** The {@code or} dual of {@link #flattenAnd}, with the same same-connective-only rule. */
  private static List<SmtTerm> flattenOr(
      QueryExpr node,
      Map<FragmentChecker.ClassificationKey, InvariantClassification> reified,
      List<SmtTerm> collected) {
    if (node instanceof QueryExpr.Or or) {
      flattenOr(or.left(), reified, collected);
      flattenOr(or.right(), reified, collected);
    } else {
      collected.add(lower(node, reified));
    }
    return collected;
  }

  /**
   * The conjunction of {@code true(mode, i)} over the given invariants, left-associated. Over an
   * EMPTY set this is the constant {@code true} -- the same unit {@code Smt.and} of no conjuncts
   * already returns, and the reason the core needs a constant node at all.
   */
  private static QueryExpr allAreTrue(TranslationMode mode, Collection<String> invariantNames) {
    QueryExpr conjunction = null;
    for (String invariantName : invariantNames) {
      QueryExpr atom = new QueryExpr.Classification(mode, invariantName, InvariantOutcome.TRUE);
      conjunction = conjunction == null ? atom : new QueryExpr.And(conjunction, atom);
    }
    return conjunction == null ? new QueryExpr.Constant(true) : conjunction;
  }

  private static Set<String> others(Set<String> activeInvariants, String target) {
    Set<String> rest = new LinkedHashSet<>(activeInvariants);
    rest.remove(target);
    return rest;
  }

  private static String requireOthersTarget(String target) {
    if (target == null) {
      throw new IllegalArgumentException(
          "'others' is defined relative to one target invariant, and this query references none;"
              + " name the target explicitly");
    }
    return target;
  }

  /**
   * The one invariant {@code others} is relative to. §5.2 spells the aggregate as "Mode others are
   * true" with no target of its own, so it borrows the query's single invariant reference -- and a
   * query with several references has no unambiguous "others" to mean.
   */
  private static String othersTargetOf(QueryExpr query) {
    if (!mentionsOthers(query)) {
      return null;
    }
    Set<String> referenced = new LinkedHashSet<>();
    collectReferences(query, referenced);
    if (referenced.size() != 1) {
      throw new IllegalArgumentException(
          "'others' requires exactly one target invariant, but the query references " + referenced);
    }
    return referenced.iterator().next();
  }

  private static boolean mentionsOthers(QueryExpr node) {
    return switch (node) {
      case QueryExpr.Aggregate aggregate -> aggregate.scope() == QueryExpr.AggregateScope.OTHERS;
      case QueryExpr.And and -> mentionsOthers(and.left()) || mentionsOthers(and.right());
      case QueryExpr.Or or -> mentionsOthers(or.left()) || mentionsOthers(or.right());
      case QueryExpr.Not not -> mentionsOthers(not.operand());
      case QueryExpr.Profiled profiled -> mentionsOthers(profiled.expression());
      default -> false;
    };
  }

  private static void collectReferences(QueryExpr node, Set<String> into) {
    switch (node) {
      case QueryExpr.Classification atom -> into.add(atom.invariantName());
      case QueryExpr.Counterexample counterexample -> into.add(counterexample.invariantName());
      case QueryExpr.Fragile fragile -> into.add(fragile.invariantName());
      case QueryExpr.And and -> {
        collectReferences(and.left(), into);
        collectReferences(and.right(), into);
      }
      case QueryExpr.Or or -> {
        collectReferences(or.left(), into);
        collectReferences(or.right(), into);
      }
      case QueryExpr.Not not -> collectReferences(not.operand(), into);
      case QueryExpr.Profiled profiled -> collectReferences(profiled.expression(), into);
      default -> {
        // Aggregates, macros without a target, and constants reference nothing by name.
      }
    }
  }

  private static void requireActive(
      String invariantName, Set<String> activeInvariants, String role) {
    if (!activeInvariants.contains(invariantName)) {
      throw new IllegalArgumentException(
          role
              + " '"
              + invariantName
              + "' is not an active invariant; the witness predicates are defined over the"
              + " complete active-invariant set A_K, so an inactive reference has nothing to"
              + " state");
    }
  }

  private static InvariantClassification classificationOf(
      QueryExpr.Classification atom,
      Map<FragmentChecker.ClassificationKey, InvariantClassification> reified) {
    InvariantClassification classification =
        reified.get(new FragmentChecker.ClassificationKey(atom.invariantName(), atom.mode()));
    if (classification == null) {
      throw new IllegalArgumentException(
          "no reified "
              + atom.mode().name().toLowerCase(Locale.ROOT)
              + " classification for invariant '"
              + atom.invariantName()
              + "'");
    }
    return classification;
  }
}
