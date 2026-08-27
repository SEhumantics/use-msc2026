package org.tzi.use.smt.encode;

/**
 * A fail-closed refusal to translate something.
 *
 * <p>Since Milestone 4.7 a refusal carries WHICH supported-fragment boundary it hit as well as the
 * message naming the construct. The boundary-less constructor exists only for the AGGREGATE refusal
 * {@link FragmentCoverageLedger#requireAllSupported()} raises, whose individual causes are already
 * classified in the ledger entries it reports; every other refusal must classify itself, and {@link
 * InvariantCoverage} rejects an unsupported entry that does not.
 */
public final class SmtTranslationException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  private final transient FragmentBoundary boundary;

  public SmtTranslationException(FragmentBoundary boundary, String message) {
    super(message);
    if (boundary == null) {
      throw new IllegalArgumentException("a translation refusal must name its fragment boundary");
    }
    this.boundary = boundary;
  }

  /** The aggregate form: see the class comment. */
  SmtTranslationException(String message) {
    super(message);
    this.boundary = null;
  }

  /** The boundary this refusal hit, or null for an aggregate refusal over classified entries. */
  public FragmentBoundary boundary() {
    return boundary;
  }
}
