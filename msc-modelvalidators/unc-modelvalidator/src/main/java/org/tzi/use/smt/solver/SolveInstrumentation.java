package org.tzi.use.smt.solver;

/**
 * Counts solver invocations and the size of the SMT-LIB handed to them, so an experiment can
 * separate the two costs a scenario policy pays: how MANY scripts it emits and how BIG each one is.
 *
 * <p>The distinction is the whole point of measuring. COVER emits one script per configured
 * scenario and each stays the size of a single-scenario problem; UNIFORM emits ONE script that
 * carries every scenario at once. Wall-clock alone cannot tell those apart, and neither can be
 * inferred from the other.
 *
 * <p>Like {@link org.tzi.use.smt.encode.QuantileInstrumentation} this is INSTRUMENTATION ONLY:
 * nothing here is read by the encoding or the solver, and removing every call would leave identical
 * behavior. The counters are thread-confined for the same reason -- one run's encode/solve loop
 * stays on its calling thread -- and callers bracket a run with {@link #snapshot()} and
 * {@link #restore}.
 */
public final class SolveInstrumentation {

  /** {calls, totalScriptChars, solverNanos, witnessNanos} for the current thread. */
  private static final ThreadLocal<long[]> COUNTERS = ThreadLocal.withInitial(() -> new long[4]);

  private SolveInstrumentation() {}

  /** Records one {@code (check-sat)} invocation, its script size and the time the solver took. */
  static void recordSolverCall(int scriptChars, long elapsedNanos) {
    long[] cell = COUNTERS.get();
    cell[0]++;
    cell[1] += scriptChars;
    cell[2] += elapsedNanos;
  }

  /** Solver invocations on this thread since the last reset. */
  public static long solverCalls() {
    return COUNTERS.get()[0];
  }

  /** Total SMT-LIB characters handed to the solver on this thread since the last reset. */
  public static long scriptCharacters() {
    return COUNTERS.get()[1];
  }

  /** Total nanoseconds spent inside the solver on this thread since the last reset. */
  public static long solverNanos() {
    return COUNTERS.get()[2];
  }

  /**
   * Records time spent reconstructing a witness into a USE system state and re-evaluating it.
   * Public because that step lives in the finder package, not here.
   */
  public static void recordWitnessTime(long elapsedNanos) {
    COUNTERS.get()[3] += elapsedNanos;
  }

  /** Total nanoseconds spent reconstructing and re-evaluating witnesses on this thread. */
  public static long witnessNanos() {
    return COUNTERS.get()[3];
  }

  /** Reads all counters and zeroes them, returning the values for {@link #restore}. */
  public static long[] snapshot() {
    long[] cell = COUNTERS.get();
    long[] previous = cell.clone();
    java.util.Arrays.fill(cell, 0L);
    return previous;
  }

  /** Restores counters taken by {@link #snapshot()}. */
  public static void restore(long[] previous) {
    long[] cell = COUNTERS.get();
    System.arraycopy(previous, 0, cell, 0, previous.length);
  }
}
