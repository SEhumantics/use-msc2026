package org.tzi.use.smt.verify;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.tzi.use.smt.config.InvariantOutcome;
import org.tzi.use.smt.config.QueryExpr;
import org.tzi.use.smt.config.TranslationMode;

/**
 * Holds a delivered witness to the query its solve claimed, using ONLY the independent
 * USE-evaluator verdicts -- never the solver's own {@code def}/{@code val} assignment. This is
 * `THESIS_SMT_MODEL_FINDER_PLAN.md` §8's obligation 6: a result is delivered as SAT only when "the
 * observed true/false/undefined classifications satisfy the compiled query term".
 *
 * <p>Until Milestone 4.3 this was implemented by pinning each active invariant to one expected
 * outcome, which happens to be enough for SATISFY and COUNTEREXAMPLE because both really do pin
 * every active invariant. It cannot express a disjunction or a negation: {@code uncertain j is
 * false or uncertain k is false} has no single expected-outcome map, since one witness may satisfy
 * it with {@code j} false and {@code k} true and another with exactly the opposite. Milestone 4.4
 * therefore EVALUATES the compiled query's desugared core over the observed verdicts. Weakening or
 * skipping the check for the shapes a map cannot describe would have gutted the safety net rather
 * than generalised it.
 *
 * <p>Two rules keep that evaluation from quietly becoming weaker than the map it replaces:
 *
 * <ul>
 *   <li>The connectives are CLASSICAL over the three-valued atoms, exactly as the SMT lowering is
 *       classical over {@code def}/{@code val}. So {@code not false(m,i)} is satisfied by a
 *       defined-TRUE reading as well as an undefined one -- §5.3's non-distributivity -- and is
 *       never treated as {@code undef(m,i)}.
 *   <li>An atom whose (invariant, mode) the oracle never reported on is a hard refusal, not a
 *       vacuous truth and not a vacuous falsity. A missing verdict means nothing checked that atom,
 *       which is precisely the state this class exists to prevent from being delivered.
 * </ul>
 */
public final class QueryWitnessChecker {
  private QueryWitnessChecker() {}

  /**
   * @param core the desugared query from {@link
   *     org.tzi.use.smt.encode.QueryCompiler.Obligation#core()} -- the same tree the SMT constraint
   *     was lowered from, so the solver and the oracle cannot disagree about what was asked
   * @param observedByMode what USE independently reported per translation mode
   */
  public static void requireQuerySatisfied(
      QueryExpr core, Map<TranslationMode, List<InvariantVerdict>> observedByMode) {
    Map<TranslationMode, Map<String, InvariantOutcome>> observed = index(observedByMode);
    if (!evaluate(core, observed)) {
      throw new WitnessAttributionException(
          "the reconstructed witness does not satisfy the query its solve claimed"
              + " (a translation or numerical error, not a finding): query = "
              + render(core)
              + "; USE independently observed "
              + describe(core, observed));
    }
  }

  private static boolean evaluate(
      QueryExpr core, Map<TranslationMode, Map<String, InvariantOutcome>> observed) {
    return switch (core) {
      case QueryExpr.Constant constant -> constant.value();
      case QueryExpr.Classification atom -> outcomeOf(atom, observed) == atom.outcome();
      case QueryExpr.And and -> evaluate(and.left(), observed) && evaluate(and.right(), observed);
      case QueryExpr.Or or -> evaluate(or.left(), observed) || evaluate(or.right(), observed);
      case QueryExpr.Not not -> !evaluate(not.operand(), observed);
      default ->
          throw new IllegalArgumentException(
              "the witness oracle evaluates only desugared core queries, but was given: " + core);
    };
  }

  private static InvariantOutcome outcomeOf(
      QueryExpr.Classification atom, Map<TranslationMode, Map<String, InvariantOutcome>> observed) {
    InvariantOutcome outcome =
        observed.getOrDefault(atom.mode(), Map.of()).get(atom.invariantName());
    if (outcome == null) {
      throw new WitnessAttributionException(
          "no independent USE verdict for "
              + modeName(atom.mode())
              + " '"
              + atom.invariantName()
              + "', so nothing checked the atom "
              + render(atom)
              + "; refusing to deliver a witness the oracle never examined");
    }
    return outcome;
  }

  private static Map<TranslationMode, Map<String, InvariantOutcome>> index(
      Map<TranslationMode, List<InvariantVerdict>> observedByMode) {
    Map<TranslationMode, Map<String, InvariantOutcome>> indexed = new LinkedHashMap<>();
    observedByMode.forEach(
        (mode, verdicts) -> {
          Map<String, InvariantOutcome> byName = new LinkedHashMap<>();
          verdicts.forEach(verdict -> byName.put(verdict.invariantName(), verdict.outcome()));
          indexed.put(mode, byName);
        });
    return indexed;
  }

  /** Every (mode, invariant) the query actually asked about, with what USE said about it. */
  private static String describe(
      QueryExpr core, Map<TranslationMode, Map<String, InvariantOutcome>> observed) {
    Set<QueryExpr.Classification> atoms = new LinkedHashSet<>();
    collectAtoms(core, atoms);
    List<String> described = new ArrayList<>();
    Set<String> seen = new LinkedHashSet<>();
    for (QueryExpr.Classification atom : atoms) {
      String key = modeName(atom.mode()) + " " + atom.invariantName();
      if (!seen.add(key)) {
        continue;
      }
      InvariantOutcome outcome =
          observed.getOrDefault(atom.mode(), Map.of()).get(atom.invariantName());
      described.add(key + " = " + (outcome == null ? "NO VERDICT" : outcome.name()));
    }
    return described.isEmpty() ? "no classifications at all" : String.join("; ", described);
  }

  private static void collectAtoms(QueryExpr core, Set<QueryExpr.Classification> into) {
    switch (core) {
      case QueryExpr.Classification atom -> into.add(atom);
      case QueryExpr.And and -> {
        collectAtoms(and.left(), into);
        collectAtoms(and.right(), into);
      }
      case QueryExpr.Or or -> {
        collectAtoms(or.left(), into);
        collectAtoms(or.right(), into);
      }
      case QueryExpr.Not not -> collectAtoms(not.operand(), into);
      default -> {
        // Constants mention nothing; no other node survives desugaring.
      }
    }
  }

  /** The core query in §5.2's functional spelling, for failure messages. */
  private static String render(QueryExpr core) {
    return switch (core) {
      case QueryExpr.Constant constant -> Boolean.toString(constant.value());
      case QueryExpr.Classification atom ->
          switch (atom.outcome()) {
                case TRUE -> "true";
                case FALSE -> "false";
                case UNDEFINED -> "undef";
              }
              + "("
              + modeName(atom.mode())
              + ", "
              + atom.invariantName()
              + ")";
      case QueryExpr.And and -> "(" + render(and.left()) + " and " + render(and.right()) + ")";
      case QueryExpr.Or or -> "(" + render(or.left()) + " or " + render(or.right()) + ")";
      case QueryExpr.Not not -> "not " + render(not.operand());
      default -> core.toString();
    };
  }

  private static String modeName(TranslationMode mode) {
    return mode.name().toLowerCase(Locale.ROOT);
  }
}
