package org.tzi.use.smt.finder;

/**
 * One active invariant's independence answer, kept three-valued for the same reason {@link
 * ProfileOutcome} is.
 *
 * <p>Reading independence off {@code ModelFinderResult.satisfiable()} alone -- which is {@code
 * outcome() == SATISFIED} -- collapses {@link ProfileOutcome#PARTIAL} into {@link
 * ProfileOutcome#REFUTED}: a solver timeout on {@code counterexample(j)} becomes indistinguishable
 * from a genuine proof that no violating instance exists in scope, and is reported as the
 * substantive claim "j is implied by the other invariants". {@link #UNRESOLVED} keeps the two
 * apart.
 */
public enum IndependenceVerdict {
  /**
   * {@code counterexample(j)} was satisfied: there is an independently re-checked snapshot in which
   * every other active invariant is defined-TRUE and {@code j} alone is defined-FALSE. So {@code j}
   * is not implied by the others -- it carries its own constraint.
   */
  INDEPENDENT,
  /**
   * {@code counterexample(j)} was refuted within the configured bounds: no such snapshot exists, so
   * within these scopes and domains {@code j} adds nothing the other active invariants do not
   * already force. A BOUNDED statement -- see {@link IndependenceEntry#boundedScopeArtefact()} for
   * the case where the bound alone explains it.
   */
  NOT_INDEPENDENT,
  /**
   * The obligation was neither satisfied nor refuted (timeout, {@code unknown}, malformed answer).
   * Nothing is claimed about {@code j} either way.
   */
  UNRESOLVED;

  /**
   * The reading of one {@code counterexample(j)} obligation's aggregate outcome. Total over {@link
   * ProfileOutcome}, so a fourth outcome could not be silently folded into a verdict it does not
   * mean.
   */
  public static IndependenceVerdict of(ProfileOutcome outcome) {
    return switch (outcome) {
      case SATISFIED -> INDEPENDENT;
      case REFUTED -> NOT_INDEPENDENT;
      case PARTIAL -> UNRESOLVED;
    };
  }
}
