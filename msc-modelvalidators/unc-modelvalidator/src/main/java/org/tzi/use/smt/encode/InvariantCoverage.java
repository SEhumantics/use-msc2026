package org.tzi.use.smt.encode;

import org.tzi.use.smt.config.TranslationMode;

/**
 * One (invariant, translation mode) pair's ledger entry.
 *
 * <p>An unsupported entry MUST name the {@link FragmentBoundary} it hit. That is a fail-closed
 * invariant of the record itself rather than a convention: an unclassified refusal would silently
 * re-introduce the free-text-only ledger Milestone 4.7 exists to replace.
 */
public record InvariantCoverage(
    String invariantName,
    TranslationMode mode,
    boolean supported,
    FragmentBoundary boundary,
    String reason) {

  public InvariantCoverage {
    if (!supported && boundary == null) {
      throw new IllegalArgumentException(
          "unsupported invariant '"
              + invariantName
              + "' ["
              + mode
              + "] must record which supported-fragment boundary it hit");
    }
    if (supported && boundary != null) {
      throw new IllegalArgumentException(
          "supported invariant '" + invariantName + "' [" + mode + "] hit no boundary");
    }
  }

  /** A supported entry, which by construction has neither a boundary nor a reason. */
  public static InvariantCoverage supported(String invariantName, TranslationMode mode) {
    return new InvariantCoverage(invariantName, mode, true, null, null);
  }

  /** A refusal, classified by boundary and still carrying the original located message. */
  public static InvariantCoverage refused(
      String invariantName, TranslationMode mode, SmtTranslationException cause) {
    return new InvariantCoverage(invariantName, mode, false, cause.boundary(), cause.getMessage());
  }
}
