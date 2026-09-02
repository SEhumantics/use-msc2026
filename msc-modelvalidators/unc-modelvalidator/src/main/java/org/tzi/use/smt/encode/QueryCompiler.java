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
 * deliberately not part of what this class emits.
 *
 * <p>Milestone 4.6 adds the orthogonal SCENARIO axis. It is not another truth semantics and so does
 * not change anything above: COVER and UNIFORM lower the SAME desugared core once per configured
 * scenario, over that scenario's own reified symbols. That is why {@link #desugared} and {@link
 * #lowerCore} are separate entry points, and why holding the target fixed across scenarios needs no
 * extra machinery -- there is literally one core. What DOES need stating is the proposal's
 * disjunction rule, which {@link #requireTargetDeterminate} enforces.
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
    return lowerCore(desugared(query, activeInvariants), classifications);
  }

  /**
   * The desugared core of a query, independent of any scenario copy's reified symbols.
   *
   * <p>Milestone 4.6 needs this separately because COVER and UNIFORM lower the SAME core once per
   * scenario: desugaring is a property of the query and the active-invariant set, never of the
   * measurement quality, so it must be computed once and reused. That is also what pins the TARGET
   * outside the scenario quantifiers -- every scenario obligation is lowered from the identical
   * atom, so two scenarios cannot diagnose different invariants.
   */
  public static QueryExpr desugared(QueryExpr query, Set<String> activeInvariants) {
    QueryExpr body = query instanceof QueryExpr.Profiled profiled ? profiled.expression() : query;
    return desugar(body, activeInvariants, othersTargetOf(body));
  }

  /** Lowers an already-desugared core onto one scenario copy's reified {@code def}/{@code val}. */
  public static Obligation lowerCore(
      QueryExpr core,
      Map<FragmentChecker.ClassificationKey, InvariantClassification> classifications) {
    return new Obligation(lower(core, classifications), core);
  }

  /**
   * The binding rule the proposal states for the two stronger profiles: "For COUNTEREXAMPLE(j) and
   * FRAGILE(j) under COVER or UNIFORM, the target j is fixed outside the scenario quantifiers.
   * Version 1 permits an untargeted disjunction only with EXISTS; this prevents different scenarios
   * from silently diagnosing different target invariants under one aggregate result."
   *
   * <p>Fixing the target is structural here -- one desugared core is reused for every scenario --
   * so all that remains is the disjunction clause. A core is TARGET-DETERMINATE when every branch
   * of every DISJUNCTION diagnoses the same invariants, where "diagnoses" means constraining one to
   * anything other than plain U-aware {@code true}: a defined-false, an undefined, or a nominal
   * atom. {@code satisfy}, {@code counterexample(j)} and {@code fragile(j)} are all determinate by
   * construction; {@code false(m,i) or false(m,k)} is exactly the shape the rule excludes.
   *
   * <p>Both halves of that sentence are read on the SEMANTIC CONTENT of the core, not on its
   * literal node shape, and POLARITY is what makes the two readings differ. §5.3's
   * non-distributivity ({@code not false(m,i)} is {@code undef(m,i) or true(m,i)}) is exactly why:
   * negation does not merely relabel an atom, it changes which classifications the branch admits.
   * So the walk below carries a {@code negated} flag instead of ignoring {@code not}, and two
   * things follow that a shape-only walk gets backwards.
   *
   * <ul>
   *   <li>{@code not true(uncertain,i)} DIAGNOSES {@code i}. It asserts precisely "{@code i} is
   *       F_U or X_U", which is a diagnosis of {@code i} in every sense the rule cares about --
   *       only the UNNEGATED {@code true(uncertain,i)} is the benign "this invariant simply holds"
   *       reading that {@code satisfy} and the {@code others} aggregate are built from. Treating
   *       the negated atom as benign let {@code cover (not true(u,A) or not true(u,B))} through as
   *       a respelling of the refused {@code cover (false(u,A) or false(u,B))}, and made the mixed
   *       {@code not true(u,A) or false(u,B)} report the nonsense "branches diagnose [] and [B]".
   *   <li>An {@code or} under an ODD number of negations is a CONJUNCTION, and a conjunction pins
   *       every invariant it names in every scenario -- there is nothing for two scenarios to
   *       disagree about, so no branch-agreement obligation applies. {@code cover (not (false(u,A)
   *       or false(u,B)))} is therefore genuinely target-determinate and is accepted. Its De Morgan
   *       dual is the mirror image and is refused for the same reason it would be were it spelled
   *       out: {@code not (false(u,A) and false(u,B))} IS a disjunction.
   * </ul>
   */
  public static void requireTargetDeterminate(QueryExpr core, ScenarioProfile profile) {
    if (profile == ScenarioProfile.EXISTS) {
      return;
    }
    checkDeterminate(core, false, profile);
  }

  /**
   * @param negated whether this node sits under an ODD number of {@code not}s, i.e. whether it is
   *     read through De Morgan. An {@code and} under a negation is a disjunction and an {@code or}
   *     under a negation is a conjunction, so it is the flag -- not the node's class -- that
   *     decides which node owes the branch-agreement obligation.
   */
  private static void checkDeterminate(QueryExpr node, boolean negated, ScenarioProfile profile) {
    switch (node) {
      case QueryExpr.Or or -> {
        if (!negated) {
          requireBranchesAgree(or.left(), or.right(), false, profile);
        }
        checkDeterminate(or.left(), negated, profile);
        checkDeterminate(or.right(), negated, profile);
      }
      case QueryExpr.And and -> {
        if (negated) {
          requireBranchesAgree(and.left(), and.right(), true, profile);
        }
        checkDeterminate(and.left(), negated, profile);
        checkDeterminate(and.right(), negated, profile);
      }
      case QueryExpr.Not not -> checkDeterminate(not.operand(), !negated, profile);
      default -> {
        // Atoms and constants diagnose at most one invariant and cannot vary per scenario.
      }
    }
  }

  /**
   * @param negated the polarity the two branches are read at, which is also exactly when this
   *     obligation came from a NEGATED CONJUNCTION rather than a written-out {@code or} -- the two
   *     coincide because De Morgan is the only thing that turns one into the other.
   */
  private static void requireBranchesAgree(
      QueryExpr left, QueryExpr right, boolean negated, ScenarioProfile profile) {
    Set<String> leftDiagnosed = new LinkedHashSet<>();
    Set<String> rightDiagnosed = new LinkedHashSet<>();
    collectDiagnosed(left, negated, leftDiagnosed);
    collectDiagnosed(right, negated, rightDiagnosed);
    if (leftDiagnosed.equals(rightDiagnosed)) {
      return;
    }
    throw new IllegalArgumentException(
        "scenario profile "
            + profile
            + " refuses an untargeted disjunction"
            + (negated ? " (this negated conjunction is a disjunction by De Morgan)" : "")
            + ": its branches diagnose "
            + leftDiagnosed
            + " and "
            + rightDiagnosed
            + ", so different scenarios could silently diagnose different target invariants"
            + " under one aggregate result. Version 1 permits an untargeted disjunction only"
            + " with EXISTS; name the target explicitly instead");
  }

  /**
   * The invariants a core singles out AT THE POLARITY IT OCCURS: every atom diagnoses the invariant
   * it names except an UNNEGATED {@code true(uncertain,i)}, the one reading that says "this
   * invariant simply holds" and so targets nothing. A negated {@code true(uncertain,i)} is a
   * diagnosis -- it asserts F_U or X_U -- which is why the flag is threaded through rather than
   * dropped at the {@code not}.
   */
  private static void collectDiagnosed(QueryExpr node, boolean negated, Set<String> into) {
    switch (node) {
      case QueryExpr.Classification atom -> {
        boolean plainUncertainTrue =
            atom.mode() == TranslationMode.UNCERTAIN && atom.outcome() == InvariantOutcome.TRUE;
        if (negated || !plainUncertainTrue) {
          into.add(atom.invariantName());
        }
      }
      case QueryExpr.And and -> {
        collectDiagnosed(and.left(), negated, into);
        collectDiagnosed(and.right(), negated, into);
      }
      case QueryExpr.Or or -> {
        collectDiagnosed(or.left(), negated, into);
        collectDiagnosed(or.right(), negated, into);
      }
      case QueryExpr.Not not -> collectDiagnosed(not.operand(), !negated, into);
      default -> {
        // Constants name nothing; no other node survives desugaring.
      }
    }
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
