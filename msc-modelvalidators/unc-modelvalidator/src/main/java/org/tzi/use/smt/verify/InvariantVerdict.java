package org.tzi.use.smt.verify;

import org.tzi.use.smt.config.InvariantOutcome;

/**
 * One invariant's real-evaluator verdict against a reconstructed state, as one of the three
 * mutually exclusive outcomes THESIS_SMT_MODEL_FINDER_PLAN s5.1 fixes -- not a Boolean.
 * Defined-false and undefined are deliberately not interchangeable: s5.3's non-distributivity
 * argument and the COUNTEREXAMPLE/FRAGILE witness predicates all require "defined false", never
 * merely "not true".
 */
public record InvariantVerdict(String invariantName, InvariantOutcome outcome) {
  public InvariantVerdict {
    if (invariantName == null || outcome == null) {
      throw new IllegalArgumentException("an invariant name and an outcome are required");
    }
  }

  /**
   * Convenience for the crisp two-valued cases every pre-Phase-4 caller and test already writes.
   */
  public InvariantVerdict(String invariantName, boolean holds) {
    this(invariantName, holds ? InvariantOutcome.TRUE : InvariantOutcome.FALSE);
  }

  /** True only for a defined, true invariant; an undefined one never holds. */
  public boolean holds() {
    return outcome == InvariantOutcome.TRUE;
  }
}
