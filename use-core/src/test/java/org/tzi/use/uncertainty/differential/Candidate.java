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
 * because from S4 the <em>ported implementation</em> is the side being adapted.
 *
 * <h2>The second invariant: OBSERVE the Java class your port returned</h2>
 * <strong>A value is its content <em>together with its Java class</em> (defect D-18), and the class
 * must be read off the object your port actually returned:</strong>
 *
 * <pre>
 *   if (portMethod.getReturnType() == void.class) {
 *       return UValue.voidValue();                                   // NOT nullValue(): invoke()
 *   }                                                                // answers null for void too
 *   Object returned = portMethod.invoke(receiver, marshalledArgs);   // or a direct call
 *   if (returned == null) {
 *       return UValue.nullValue();                                   // no class to observe
 *   }
 *   return UValue.uReal(v, u).observedFrom(returned);                 // &lt;-- the whole obligation
 * </pre>
 *
 * <p>The {@code void} branch is part of the obligation and used to be missing from this snippet
 * (defect <strong>D-51</strong>): {@code Method.invoke} answers {@code null} for a {@code void}-declared
 * method, so an adapter that tested only for {@code null} would return {@link UValue#nullValue()} on the
 * 8 {@code void} mutators where the reference returns {@link UValue#voidValue()}.
 *
 * <p><strong>There are exactly two states a class token can be in, and you choose neither:</strong>
 * {@link UValue.TypeProvenance#OBSERVED}, if you route through {@link UValue#observedFrom(Object)}, or
 * {@link UValue.TypeProvenance#ASSUMED}, the factory default, if you do not. There is deliberately no
 * API on {@link UValue} that takes a class name from an adapter — rounds 6 and 7 each shipped one and
 * each was measured erasing the check with a single line. See {@link UValue}'s class comment.
 *
 * <p>The reference side is observed: {@link HistoricalOracle#fromHistorical(Object)} derives the class
 * from {@code result.getClass().getName()} on every branch. If your side is not observed too, the two
 * halves of the comparison are not the same question. That asymmetry is defect <strong>D-43</strong>:
 * <ul>
 *   <li><strong>Where it does its damage.</strong> {@code UValue.uReal(...)}, {@code UValue.bool(...)}
 *       and the other factories type a value as the {@code org.tzi.use.uml.ocl.value} class of its kind
 *       — {@link UValue.TypeProvenance#ASSUMED} — which is <strong>wrong for 182 of the 285 enumerated
 *       operations</strong>, because most of that surface returns a raw {@code boolean} (140
 *       declarations), {@code int} (18), {@code double} (6) or {@code String} (18). Measured on a
 *       <em>content-perfect</em> port with such an adapter: <strong>3 445 rows on which the two sides
 *       named different classes for identical content</strong>, across 182 of 285 operations, including
 *       {@code URealValue.value()}, {@code URealValue.uncertainty()}, {@code UIntegerValue.value()} and
 *       {@code UIntegerValue.uncertainty()} — a figure numerically <em>indistinguishable</em> from the
 *       planted wrong-class defect it is not.</li>
 *   <li><strong>Why it is not scored against you at S1, and why that is temporary.</strong> Because that
 *       figure cannot be attributed while there is no ported implementation to observe, a type-only
 *       difference is scored {@link DiffVerdict#AGREE} and counted in
 *       {@code Result.javaTypeMismatchCount()} rather than reported as a divergence. It is
 *       <em>reported</em>, in the row note, in {@code stageStatement()} and in the report header — and
 *       {@code harness-contract.md} §7 carries the dated requirement that S4, which is where the real
 *       ported classes get written, routes through {@code observedFrom} and turns that count into a gate
 *       clause. An adapter that does not attribute will publish a non-zero figure with your stage's
 *       name on it.</li>
 * </ul>
 *
 * <p>{@link StubCandidate} is the only worked example an adapter has to copy, and on this point it
 * <strong>cannot</strong> be copied: it computes in plain Java, has no port object in existence, and its
 * values are therefore assumed. It says so at the call site. Copy its
 * {@link HarnessMarshallingException} discipline and its single named attribution point; for the class
 * token copy the snippet above.
 *
 * <p>Test-scoped. Not part of the product.
 *
 * @implNote Do not reach for {@link HarnessMarshallingException} as a fallback for "I am not sure
 *     this row is right" (defect D-17, measured D-32). {@code HARNESS_ERROR} and
 *     {@link DiffVerdict#UNSUPPORTED} rows are excluded from the per-operation denominator that
 *     {@code UnwrittenPortInvariantTest}'s no-logic-port check reads, so an adapter that declines to
 *     marshal wherever it might otherwise be wrong is scored fully agreed by that invariant, not
 *     merely neutral. It buys no stage pass either — clause 2 of the stage gate refuses any
 *     {@code HARNESS_ERROR} row, and a subject that always throws it was measured to reach zero stage
 *     passes on every operation it touches. What it destroys is attribution: a reader is told the
 *     harness could not drive those rows, not that the port answered them wrongly. Reserve it for a
 *     genuine adapter failure — wrong argument count, an unmarshallable receiver type, a result the
 *     adapter's contract forbids — never for "the implementation might be wrong here".
 * @see "docs/port2/harness-contract.md &sect;7 first trap -- deviation ledger (decided 2026-08-17)"
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
     * <p>The returned {@link UValue} must carry the Java class the implementation answered with, read
     * off the returned object by {@link UValue#observedFrom(Object)} — see "the second invariant" on
     * the type comment. A factory-built value is typed by assumption, and the assumption is wrong for
     * 182 of the 285 enumerated operations.
     *
     * <p>Returning {@link UValue#voidValue()} buys no credit. A row on which neither side produced a
     * value is {@link DiffVerdict#UNMEASURABLE} — a non-agreement — because this harness does not
     * re-read the receiver after a call and therefore observes nothing about a {@code void}
     * operation on either side. Writing the documented boilerplate for every mutator does not make a
     * port agree with anything; it used to.
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
