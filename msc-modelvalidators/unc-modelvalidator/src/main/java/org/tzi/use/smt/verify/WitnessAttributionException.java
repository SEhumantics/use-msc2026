package org.tzi.use.smt.verify;

/**
 * Raised when a solver-delivered, reconstructed witness does not carry the classification its own
 * query claimed for it. Per the Phase 4 target architecture, such a mismatch is an explicit
 * translation/numerical error -- never a successful witness -- so it must not be reported as a
 * finding.
 */
public final class WitnessAttributionException extends IllegalStateException {
  private static final long serialVersionUID = 1L;

  public WitnessAttributionException(String message) {
    super(message);
  }
}
