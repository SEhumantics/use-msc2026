package org.tzi.use.smt.finder;

/** What happened for ONE scenario of a profile. */
public enum ScenarioOutcome {
  /** Solved SAT, reconstructed, and independently checked against the query core by USE. */
  WITNESSED,
  /** Solved UNSAT within the configured bounds -- see {@link ProfileOutcome}'s limitation note. */
  REFUTED,
  /** The solver returned unknown, timed out, or produced output the parser could not read. */
  UNRESOLVED
}
