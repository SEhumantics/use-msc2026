package org.tzi.use.smt.solver;

/** What the solver said. TIMEOUT and MALFORMED are deliberately distinct from UNKNOWN. */
public enum SolverOutcome {
  SAT,
  UNSAT,
  UNKNOWN,
  TIMEOUT,
  MALFORMED
}
