package org.tzi.use.uncertainty.differential;

/**
 * Outcome of comparing one operation application across the two candidates.
 *
 * <h2>The rule this enum exists to enforce</h2>
 * <strong>A differential oracle may report agreement only where it observed two comparable
 * values.</strong> Everything else — a throw on both sides, a harness failure, an operation one side
 * does not have — is the <em>absence</em> of a measurement, and the absence of a measurement is not
 * a measurement that the two sides happen to share.
 *
 * <p>The one exception is {@link #ACCEPTED_THROW}, and it is not an exception to the rule so much as
 * a place to record that a human made the judgement instead of the harness: see
 * {@link AcceptedThrowPairs}. It cannot occur unless a caller deliberately built an allowlist naming
 * the operation, both throwable classes, both messages, and a written rationale.
 *
 * <h2>Why {@code AGREE_THROWN} was deleted rather than tightened</h2>
 * This enum used to carry {@code AGREE_THROWN}: "both sides threw, and the throwable class names are
 * identical", with {@code isAgreement() == true} and an empty note. That was measured against a
 * subject whose every method body was {@code throw new RuntimeException("TODO: port " + op.key())}
 * — a port that does not exist — over every operation the harness can reach and every corpus it
 * ships:
 * <pre>
 *   rows 471471   AGREE_THROWN 21816   over 27 operations on three receiver types
 * </pre>
 * 21 816 rows of "agreement" against a subject containing no code, because
 * {@code java.lang.RuntimeException} is what the historical uncertainty code throws for its type
 * errors and is the least discriminating class in Java:
 * <pre>
 *   historical: java.lang.RuntimeException: UInteger.power() : expected Real or Integer exponent value
 *   ported:     java.lang.RuntimeException: TODO: port UIntegerValue.power(value)
 *   verdict:    AGREE_THROWN     note: (empty)
 * </pre>
 * Tightening the comparison (also matching messages, say) would have kept the shape of the mistake:
 * a rule under which some pair of throws counts as evidence about behaviour that neither side
 * exhibited. Both throw-outcomes are now the single non-agreement {@link #BOTH_THREW}, whose note
 * always carries both classes <em>and</em> both messages, so the reader can see what the harness saw.
 * The class-name comparison the old verdict performed is not lost either: the two result columns
 * hold {@code THROWN:<class>} for each side, so it is one glance away and no longer disguised as a
 * finding.
 *
 * <p>Note that {@link #UNSUPPORTED} is likewise a distinct, visible outcome rather than a silent
 * skip: a differential run where half the operations were never exercised must not be readable as
 * agreement.
 *
 * <p>Test-scoped. Not part of the product.
 */
public enum DiffVerdict {

    /** Both sides returned a value and the canonical forms are identical. */
    AGREE,

    /**
     * Both sides threw, and a caller-supplied {@link AcceptedThrowPairs} allowlist names this exact
     * pair — operation, both throwable classes, both messages — together with a written rationale.
     *
     * <p>The only verdict other than {@link #AGREE} for which {@link #isAgreement()} is true, and
     * the only one a human has to author by hand. The default allowlist is empty
     * ({@link AcceptedThrowPairs#none()}), so no ordinary run can produce this.
     */
    ACCEPTED_THROW,

    /** Both sides returned a value and the canonical forms differ. */
    DIFFER,

    /**
     * Both sides threw and no allowlist adjudicated the pair. <strong>Not an agreement</strong>,
     * whether or not the throwable classes match: see the class comment.
     *
     * <p>The note on such a row always carries both throwable classes and both messages, never the
     * empty string. The harness holds the evidence that two throws are unrelated; writing nothing is
     * how that evidence used to be destroyed.
     */
    BOTH_THREW,

    /** One side returned a value, the other threw. */
    MIXED,

    /**
     * At least one side could not be driven through the operation at all.
     *
     * <p>Two quite different facts land here, and the row's note says which: the candidate does not
     * declare the operation, or <em>this harness</em> cannot marshal the receiver type it is
     * declared on. See {@link Candidate#unsupportedReason(UOp)} — the note used to assert the first
     * of those in both cases, which put a false statement into the evidence file.
     */
    UNSUPPORTED,

    /**
     * At least one side could not be measured at all: the harness failed to marshal the input into a
     * candidate, or to unwrap its result, or the candidate broke its own contract by returning Java
     * {@code null}, and {@link HarnessMarshallingException} was raised — <em>before</em> any
     * comparable value existed.
     *
     * <p>This is deliberately not an agreement and deliberately not merged with the throw
     * populations. A harness failure looked exactly like a matching throw by both implementations
     * until this verdict existed, which let a sweep report full agreement over rows where neither
     * side ran the operation.
     */
    HARNESS_ERROR;

    /**
     * True for the two outcomes a green run is allowed to contain, and for nothing else.
     *
     * <p>{@link #AGREE} means two values were compared. {@link #ACCEPTED_THROW} means a human
     * compared two throws, in writing, in advance. Every other verdict is a non-agreement and lands
     * in {@link DifferentialSweep.Result#disagreements()}.
     */
    public boolean isAgreement() {
        return this == AGREE || this == ACCEPTED_THROW;
    }
}
