package org.tzi.use.smt.verify;

/** One invariant's real-evaluator verdict against a reconstructed state. */
public record InvariantVerdict(String invariantName, boolean holds) {}
