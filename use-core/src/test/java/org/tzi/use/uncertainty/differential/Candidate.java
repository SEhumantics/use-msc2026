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
 * <p>Test-scoped. Not part of the product.
 */
public interface Candidate extends Closeable {

    /** Short label used in report headers, e.g. {@code historical} or {@code ported}. */
    String name();

    /**
     * Applies {@code op}. {@code args.get(0)} is the receiver; the remaining entries correspond
     * position-by-position to {@link UOp#params()}.
     *
     * @throws Throwable whatever the implementation under test throws; the sweep records the
     *                   throwable's class name and compares it across sides rather than swallowing it.
     */
    UValue invoke(UOp op, List<UValue> args) throws Throwable;

    /**
     * Whether this candidate implements {@code op} at all. A candidate that does not is reported as
     * {@link DiffVerdict#UNSUPPORTED} rather than silently skipped.
     */
    boolean supports(UOp op);

    @Override
    void close();
}
