package org.tzi.use.smt.finder;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The whole outcome of {@link SmtModelFinder#independenceSweep}: the ONE baseline solve over the
 * active invariant set, and -- only if that baseline delivered a witness -- one {@link
 * IndependenceEntry} per active invariant.
 *
 * <p>This type replaces the bare {@code Map<String, ModelFinderResult>} the sweep used to return,
 * because that map could not express the answer "the question does not apply". A caller reading
 * {@code satisfiable()} off a per-entry result had no way to notice that the active set has NO
 * model at all, in which case every entry's SAT/UNSAT answer is inverted (see {@link
 * ActiveSetOutcome}). Here the two are structurally separated:
 *
 * <ul>
 *   <li>{@link #entries()} is EMPTY unless {@link #activeSet()} is {@link
 *       ActiveSetOutcome#SATISFIABLE}. There are no verdicts to misread, because none were
 *       computed.
 *   <li>{@link #independent()}, {@link #notIndependent()} and {@link #unresolved()} THROW rather
 *       than return an empty set when the active set was not satisfiable. An empty set is itself a
 *       claim ("nothing is independent"); an exception is not.
 * </ul>
 *
 * @param entries one entry per active invariant, in the sweep's own iteration order; empty exactly
 *     when {@code activeSet != SATISFIABLE}.
 */
public record IndependenceSweepResult(
    ActiveSetOutcome activeSet,
    ModelFinderResult baseline,
    Map<String, IndependenceEntry> entries) {

  public IndependenceSweepResult {
    if (activeSet == null || baseline == null) {
      throw new IllegalArgumentException("a sweep result must carry its baseline solve");
    }
    entries = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(entries));
    if (activeSet != ActiveSetOutcome.SATISFIABLE && !entries.isEmpty()) {
      // The whole point of the type: an unusable premise cannot come with usable-looking verdicts.
      throw new IllegalArgumentException(
          "independence entries are only meaningful over a satisfiable active set, but the"
              + " baseline came back "
              + activeSet);
    }
  }

  /** The sweep that never got past its baseline: the active set has no usable model. */
  static IndependenceSweepResult withoutEntries(ModelFinderResult baseline) {
    ActiveSetOutcome outcome =
        baseline.outcome() == ProfileOutcome.REFUTED
            ? ActiveSetOutcome.JOINTLY_UNSATISFIABLE
            : ActiveSetOutcome.UNRESOLVED;
    return new IndependenceSweepResult(outcome, baseline, Map.of());
  }

  /** True exactly when the per-invariant entries exist and mean what they say. */
  public boolean hasVerdicts() {
    return activeSet == ActiveSetOutcome.SATISFIABLE;
  }

  /** The invariants that carry their own constraint. Never callable on an unusable sweep. */
  public Set<String> independent() {
    return namesWith(IndependenceVerdict.INDEPENDENT);
  }

  /** The invariants the others already force, within the configured bounds. */
  public Set<String> notIndependent() {
    return namesWith(IndependenceVerdict.NOT_INDEPENDENT);
  }

  /** The invariants whose obligation timed out or came back unknown -- claimed neither way. */
  public Set<String> unresolved() {
    return namesWith(IndependenceVerdict.UNRESOLVED);
  }

  /** One entry by qualified invariant name. Never callable on an unusable sweep. */
  public IndependenceEntry entry(String invariantName) {
    requireVerdicts();
    IndependenceEntry entry = entries.get(invariantName);
    if (entry == null) {
      throw new IllegalArgumentException(
          invariantName + " is not in this sweep; it swept " + entries.keySet());
    }
    return entry;
  }

  /**
   * The whole sweep as text, phrased so the jointly-unsatisfiable case cannot be skimmed as a list
   * of independence verdicts.
   */
  public String statement() {
    return switch (activeSet) {
      case SATISFIABLE ->
          entries.values().stream()
              .map(IndependenceEntry::statement)
              .collect(Collectors.joining("\n"));
      case JOINTLY_UNSATISFIABLE ->
          "no independence verdict was computed: the active invariant set is JOINTLY"
              + " UNSATISFIABLE, so no snapshot satisfies all of it and every per-invariant"
              + " obligation would be vacuous. "
              + baseline.qualification().statement();
      case UNRESOLVED ->
          "no independence verdict was computed: the baseline solve over the active invariant set"
              + " was unresolved (timeout or unknown), so the sweep's premise was never"
              + " established. "
              + baseline.qualification().statement();
    };
  }

  private Set<String> namesWith(IndependenceVerdict verdict) {
    requireVerdicts();
    return entries.values().stream()
        .filter(entry -> entry.verdict() == verdict)
        .map(IndependenceEntry::invariantName)
        .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
  }

  private void requireVerdicts() {
    if (!hasVerdicts()) {
      throw new IllegalStateException(
          "this sweep has no independence verdicts to read -- " + statement());
    }
  }
}
