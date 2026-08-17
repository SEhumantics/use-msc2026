package org.tzi.use.uncertainty.differential;

/**
 * Thrown when the <em>harness</em> cannot get an input into, or a result out of, a candidate — as
 * opposed to the candidate itself failing.
 *
 * <h2>Why this type has to exist</h2>
 * Before it did, {@link HistoricalOracle} signalled "I cannot marshal this input" with a plain
 * {@link IllegalArgumentException}, {@link DifferentialSweep} caught it in the same
 * {@code catch (Throwable)} that catches a genuine failure of the code under test, and
 * {@link DiffVerdict#AGREE_THROWN} scored it as agreement whenever the other side happened to throw
 * the same class. A whole sweep could therefore report 169 rows of agreement without either side
 * ever entering the method being compared. That was measured, not hypothesised:
 * {@code sweepBinary(UOp.binary("URealValue","add"), uIntegerBoundaries(), uIntegerBoundaries())}
 * produced {@code 169 rows, AGREE_THROWN=169, disagreements 0}.
 *
 * <p>A throwable of this class is therefore caught <em>separately</em> by
 * {@link DifferentialSweep} and scored {@link DiffVerdict#HARNESS_ERROR}, whose
 * {@link DiffVerdict#isAgreement()} is {@code false}. It never merges with a throw by the code
 * under test, and it can never make a run look green.
 *
 * <p>Unchecked so that {@link Candidate#invoke(UOp, java.util.List)} and
 * {@link HistoricalOracle#toHistorical(UValue)} keep their signatures; the sweep's catch order
 * (this type first, {@code Exception} second) is what keeps the two populations apart.
 *
 * <p>Test-scoped. Not part of the product.
 */
public final class HarnessMarshallingException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public HarnessMarshallingException(String message) {
        super(message);
    }

    public HarnessMarshallingException(String message, Throwable cause) {
        super(message, cause);
    }
}
