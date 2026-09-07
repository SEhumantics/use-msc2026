package org.tzi.use.smt.encode;

/**
 * Counts the quantile enclosures performed while encoding, so a caller can tell an EXACT bounded
 * refutation from one qualified by the numerical approximation WITHOUT re-deriving the condition.
 *
 * <p>This is INSTRUMENTATION ONLY. Nothing here participates in the encoding, and the count never
 * changes a term, a bound or a verdict; removing every call would leave identical SMT-LIB. It
 * exists because the distinction it records is otherwise unrecoverable downstream: {@link
 * URealThresholdBoundary} is the single point at which USE's CDF approximation enters the encoding
 * (both {@code enclose} and {@code encloseSymmetric} funnel through one bisection), so counting
 * there is exact by construction, whereas re-deriving "does this configuration use a U-type
 * confidence threshold?" from the model would duplicate the translator's own condition and could
 * drift from it silently.
 *
 * <p>The counter is thread-confined. One {@code SmtModelFinder.find} runs its encode/solve loop on
 * the calling thread -- COVER emits one script per scenario on that same thread -- so a
 * {@link ThreadLocal} needs no synchronization and cannot leak between concurrent solves. Callers
 * bracket a run with {@link #snapshotAndReset()} and {@link #restore(int)} so a nested or
 * subsequent run cannot inherit a stale count.
 */
public final class QuantileInstrumentation {

  private static final ThreadLocal<int[]> ENCLOSURES = ThreadLocal.withInitial(() -> new int[1]);

  private QuantileInstrumentation() {}

  /** Records one enclosure of USE's CDF transition. Called only from the bisection itself. */
  static void recordEnclosure() {
    ENCLOSURES.get()[0]++;
  }

  /** Enclosures performed on this thread since the last reset. */
  public static int count() {
    return ENCLOSURES.get()[0];
  }

  /** Reads the current count and zeroes it, returning the value to hand back to {@link #restore}. */
  public static int snapshotAndReset() {
    int[] cell = ENCLOSURES.get();
    int previous = cell[0];
    cell[0] = 0;
    return previous;
  }

  /** Restores a count taken by {@link #snapshotAndReset()}. */
  public static void restore(int value) {
    ENCLOSURES.get()[0] = value;
  }
}
