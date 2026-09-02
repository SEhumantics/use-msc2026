package org.tzi.use.smt.finder;

/**
 * What the ONE baseline solve {@link SmtModelFinder#independenceSweep} runs before any
 * per-invariant obligation found out about the active invariant set as a whole.
 *
 * <p>This exists because the per-entry obligations are only interpretable when the active set has a
 * model at all. {@code COUNTEREXAMPLE(j)} is {@code F_U(j) AND (AND over i != j of T_U(i))}, so if
 * ONE active invariant is unsatisfiable within the configured bounds, then:
 *
 * <ul>
 *   <li>every OTHER invariant's obligation is unsatisfiable too -- because the pathological
 *       invariant sits in its "all others are true" conjunct -- and so reads as "not independent";
 *   <li>the pathological invariant's OWN obligation may well be satisfiable -- its own conjunct
 *       only has to be FALSE, which is exactly what it always is -- and so reads as "independent".
 * </ul>
 *
 * <p>That is the precise INVERSE of the truth, and nothing in a per-entry verdict distinguishes it
 * from a real sweep. So the sweep refuses to emit per-entry verdicts at all unless this outcome is
 * {@link #SATISFIABLE}.
 */
public enum ActiveSetOutcome {
  /**
   * The active set has an independently re-checked witness within the configured bounds, so a
   * per-invariant obligation's SAT/UNSAT answer means what it says.
   */
  SATISFIABLE,
  /**
   * No assignment satisfies every active invariant at once within the configured bounds. No
   * independence verdict is emitted, because none would be meaningful. The refutation is qualified
   * by {@link BoundedCompletenessQualification} exactly like any other UNSAT in this project: a
   * statement about these scopes and domains, not an unbounded theorem.
   */
  JOINTLY_UNSATISFIABLE,
  /**
   * The baseline solve was neither satisfied nor refuted -- a timeout, an {@code unknown}, or a
   * malformed answer. No independence verdict is emitted, because the premise the sweep rests on
   * was never established either way. Distinct from {@link #JOINTLY_UNSATISFIABLE} for the same
   * reason {@link ProfileOutcome#PARTIAL} is distinct from {@link ProfileOutcome#REFUTED}.
   */
  UNRESOLVED
}
