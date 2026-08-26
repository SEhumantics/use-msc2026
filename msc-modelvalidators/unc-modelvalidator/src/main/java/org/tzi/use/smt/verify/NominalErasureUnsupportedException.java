package org.tzi.use.smt.verify;

/**
 * Raised when an invariant has no type-directed nominal-erasure rule, and is therefore ineligible
 * for {@code fragile(j)} or any other NOMINAL-mode classification.
 *
 * <p>The proposal's own wording: "If no type-directed erasure rule exists, the invariant is
 * ineligible for FRAGILE and the tool returns UNSUPPORTED rather than guessing." Approximating an
 * erasure would silently answer a different question than the one asked, so this is a refusal, not
 * a fallback.
 */
public class NominalErasureUnsupportedException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  public NominalErasureUnsupportedException(String message) {
    super("no nominal erasure for this invariant, so it is ineligible for FRAGILE: " + message);
  }
}
