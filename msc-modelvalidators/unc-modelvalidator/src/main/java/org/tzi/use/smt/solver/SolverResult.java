package org.tzi.use.smt.solver;

/** One solver invocation, including raw output retained as experimental evidence. */
public record SolverResult(SolverOutcome outcome, String rawOutput, String modelText, long millis) {
}
