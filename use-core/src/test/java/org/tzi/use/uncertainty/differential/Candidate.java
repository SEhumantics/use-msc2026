package org.tzi.use.uncertainty.differential;

import java.io.Closeable;
import java.util.List;

/**
 * One side of the differential. Both the historical oracle and (from S4 onwards) the ported
 * implementation are plugged in through this interface, so {@link DifferentialSweep} never knows
 * which side it is driving.
 *
 * <p>Implementations must be deterministic: the same {@link UOp} and the same argument list must
 * always produce the same {@link UValue} or the same throwable type.
 *
 * <h2>The invariant every implementation has to obey</h2>
 * <strong>A failure of the adapter must be signalled with {@link HarnessMarshallingException}, and
 * never with the kind of exception the code under test would raise.</strong>
 *
 * <p>These are two different populations and the whole instrument depends on keeping them apart. "I
 * could not build a receiver of this type", "I was handed the wrong number of arguments", "I cannot
 * unwrap this result" are statements about the adapter; the operation was never entered and no
 * measurement exists. "The operation rejected this input" is a statement about the implementation
 * being compared. When an adapter signals the first with, say, {@link IllegalArgumentException}, the
 * sweep cannot tell it from the second — and a run in which neither side ever entered the operation
 * reads as a run in which both sides behaved identically.
 *
 * <p>This was stated on {@link HistoricalOracle} only, and the shipped {@link StubCandidate} broke
 * it: two faithful stubs swept over a receiver type they cannot take produced 169 rows of
 * "agreement" without either side executing anything. The invariant belongs here, on the interface,
 * because from S4 the <em>ported implementation</em> is the side being adapted, and
 * {@link StubCandidate} is the only worked example its adapter has to copy.
 *
 * <p>Test-scoped. Not part of the product.
 */
public interface Candidate extends Closeable {

    /** Short label used in report headers, e.g. {@code historical} or {@code ported}. */
    String name();

    /**
     * Applies {@code op}. {@code args.get(0)} is the receiver; the remaining entries correspond
     * position-by-position to {@link UOp#params()}.
     *
     * <p>Must never return Java {@code null}: use {@link UValue#nullValue()} for a genuine null
     * result and {@link UValue#voidValue()} for a {@code void} operation. A {@code null} return is
     * a contract violation and {@link DifferentialSweep} scores the row
     * {@link DiffVerdict#HARNESS_ERROR}.
     *
     * @throws HarnessMarshallingException if <em>this adapter</em> could not drive the operation —
     *                                     see the invariant on the type comment. Not a behavioural
     *                                     difference, and never comparable with the other side.
     * @throws Throwable                   whatever the implementation under test throws; the sweep
     *                                     records the throwable's class name and message and reports
     *                                     both, without ever inferring agreement from them.
     */
    UValue invoke(UOp op, List<UValue> args) throws Throwable;

    /**
     * Whether this candidate can be driven through {@code op} at all. A candidate that cannot is
     * reported as {@link DiffVerdict#UNSUPPORTED} rather than silently skipped.
     */
    boolean supports(UOp op);

    /**
     * Why {@link #supports(UOp)} said no — written verbatim into the note column of the
     * {@link DiffVerdict#UNSUPPORTED} row, so the evidence file states what is actually true.
     *
     * <p>The distinction this exists to preserve: "this implementation does not declare the
     * operation" is a fact about the code being ported, while "this harness cannot marshal that
     * receiver type" is a fact about the instrument. They are opposite findings. The sweep used to
     * assert the first for both, which put a demonstrably false sentence — {@code historical does
     * not implement SBooleanValue.and(value)}, when {@code javap} shows the historical class
     * declares exactly that method — into a file whose purpose is to be evidence.
     *
     * <p>The default is deliberately non-committal, because it is all a general adapter can honestly
     * claim. Override it whenever you know more.
     */
    default String unsupportedReason(UOp op) {
        return name() + " cannot be driven through " + op.key();
    }

    @Override
    void close();
}
