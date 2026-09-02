package org.tzi.use.smt.finder;

/**
 * One active invariant's share of an {@link SmtModelFinder#independenceSweep}: its three-valued
 * {@link IndependenceVerdict}, the full {@link ModelFinderResult} the obligation produced (witness,
 * verdicts and all), and the CONTEXT CAPACITY the configured class scopes left its context class.
 *
 * <p>The capacity is carried per entry because it is the one bound that can explain a {@link
 * IndependenceVerdict#NOT_INDEPENDENT} entirely on its own. An invariant whose context class (and
 * every descendant of it) has zero object slots is VACUOUSLY true in every snapshot the scopes
 * admit -- there is no instance to violate it -- so {@code counterexample(j)} is unsatisfiable for
 * a reason that has nothing to do with the other invariants. Every result already carries a {@link
 * BoundedCompletenessQualification}, but that is the whole configuration; this names the one part
 * of it that decided this entry.
 *
 * @param contextCapacity how many candidate object slots the configured class scopes give this
 *     invariant's context class plus every descendant of it -- computed exactly the way {@code
 *     ObjectSlotEncoder} declares them, so an unbounded ({@code max == -1}) scope reports the
 *     candidate count that is actually declared rather than a fictional infinity.
 */
public record IndependenceEntry(
    String invariantName,
    IndependenceVerdict verdict,
    int contextCapacity,
    ModelFinderResult result) {

  public IndependenceEntry {
    if (invariantName == null || verdict == null || result == null) {
      throw new IllegalArgumentException("an independence entry needs a name, a verdict and its"
          + " underlying result");
    }
    if (contextCapacity < 0) {
      throw new IllegalArgumentException("context capacity cannot be negative: " + contextCapacity);
    }
  }

  /**
   * True when this entry's NOT INDEPENDENT verdict is fully explained by the configured scopes
   * rather than by the other invariants: the context class has no object slots at all, so the
   * invariant cannot be violated by any snapshot in scope no matter what it says.
   *
   * <p>Deliberately NOT a different verdict. "Not independent within these bounds" remains the
   * literally true answer, and silently promoting it to something else would be the unbounded claim
   * {@link BoundedCompletenessQualification} exists to refuse. This flag is the qualification made
   * visible per entry so a reader can see WHICH bound produced it.
   */
  public boolean boundedScopeArtefact() {
    return verdict == IndependenceVerdict.NOT_INDEPENDENT && contextCapacity == 0;
  }

  /** The bounds that qualify this entry's own obligation. */
  public BoundedCompletenessQualification qualification() {
    return result.qualification();
  }

  /** This entry as one line, with the qualification a NOT INDEPENDENT verdict must not lose. */
  public String statement() {
    return switch (verdict) {
      case INDEPENDENT ->
          invariantName
              + ": INDEPENDENT -- a snapshot violating only this invariant was found and"
              + " independently re-checked";
      case NOT_INDEPENDENT ->
          invariantName
              + ": NOT INDEPENDENT within the configured bounds"
              + (boundedScopeArtefact()
                  ? " -- but only because its context class has zero object slots in this"
                      + " configuration, so no snapshot in scope can violate it"
                  : "")
              + ". "
              + qualification().statement();
      case UNRESOLVED ->
          invariantName
              + ": UNRESOLVED -- the obligation timed out or came back unknown, so neither"
              + " independence nor its negation is claimed";
    };
  }
}
