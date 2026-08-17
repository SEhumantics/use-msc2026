package org.tzi.use.uncertainty.differential;

/**
 * Outcome of comparing one operation application across the two candidates.
 *
 * <p>Note that {@link #UNSUPPORTED} is a distinct, visible outcome rather than a silent skip: a
 * differential run where half the operations were never exercised must not be readable as agreement.
 *
 * <p>Test-scoped. Not part of the product.
 */
public enum DiffVerdict {

    /** Both sides returned a value and the canonical forms are identical. */
    AGREE,

    /** Both sides returned a value and the canonical forms differ. */
    DIFFER,

    /** Both sides threw, and the throwable class names are identical. */
    AGREE_THROWN,

    /** Both sides threw, but with different throwable classes. */
    DIFFER_THROWN,

    /** One side returned a value, the other threw. */
    MIXED,

    /** At least one side does not implement the operation at all. */
    UNSUPPORTED,

    /**
     * At least one side could not be driven at all: the harness failed to marshal the input into a
     * candidate, or to unwrap its result, and threw {@link HarnessMarshallingException} — <em>before</em>
     * the operation under comparison was ever entered.
     *
     * <p>This is deliberately not an agreement and deliberately not merged with
     * {@link #AGREE_THROWN}. A harness failure looked exactly like a matching throw by both
     * implementations until this verdict existed, which let a sweep report full agreement over rows
     * where neither side ran the operation.
     */
    HARNESS_ERROR;

    /** True for the two outcomes a green run is allowed to contain. */
    public boolean isAgreement() {
        return this == AGREE || this == AGREE_THROWN;
    }
}
