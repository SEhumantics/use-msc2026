package org.tzi.use.smt.finder;

/**
 * The aggregate answer for one scenario profile, kept deliberately three-valued.
 *
 * <p>From the proposal's "Outputs": "For COVER, success means every scenario has a checked witness;
 * one qualified UNSAT scenario refutes coverage, while any unresolved scenario makes the aggregate
 * result UNKNOWN/PARTIAL." Collapsing {@link #PARTIAL} into {@link #REFUTED} would report a solver
 * timeout as a proof of non-coverage.
 *
 * <p><b>Known limitation, recorded rather than papered over.</b> This project has an independent
 * USE-evaluator oracle only on the SAT side: reconstruction and {@code QueryWitnessChecker} run
 * exclusively over a delivered witness. A {@link #REFUTED} answer -- COVER's "one qualified UNSAT
 * scenario refutes coverage" included -- therefore rests entirely on the SMT encoding being a
 * faithful and conservative rendering of the query, and is NOT independently re-checked. It is a
 * qualified statement about the configured scopes, domains and scenario policy, never an unbounded
 * theorem.
 */
public enum ProfileOutcome {
  /** Every obligation the profile demands has an independently checked witness. */
  SATISFIED,
  /** At least one obligation came back UNSAT within the configured bounds. */
  REFUTED,
  /** No obligation was refuted, but at least one was unresolved (unknown, timeout, malformed). */
  PARTIAL
}
