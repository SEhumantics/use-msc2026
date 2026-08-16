package org.tzi.use.uncertainty.differential;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A placeholder {@link Candidate} for stage S1, where no ported implementation exists yet.
 *
 * <p>It covers three {@code URealValue} operations in plain Java. The formulas were <em>measured</em>
 * against the historical jars during S1, not assumed:
 * <pre>
 *   (1.5,0.25) add   (2.5,0.5) -> (4.0,  0.5590169943749475)
 *   (1.5,0.25) minus (2.5,0.5) -> (-1.0, 0.5590169943749475)
 *   (1.0,NaN)  add   (1.0,0.0) -> (2.0,  NaN)
 * </pre>
 * so the uncertainty of an additive combination is {@code sqrt(ua*ua + ub*ub)} — specifically
 * <em>not</em> {@link Math#hypot}, which would return {@code Infinity} rather than {@code NaN} for
 * the third case. {@code neg()} negates the value and leaves the uncertainty alone.
 *
 * <p><strong>This class is not, and must not become, the port.</strong> It exists so S1 can
 * demonstrate that the harness produces agreement rows against a known-good side and disagreement
 * rows against a known-bad one. From S4 onwards the real port replaces it and this file should be
 * deleted or left unused.
 *
 * <p>Test-scoped. Not part of the product.
 */
public final class StubCandidate implements Candidate {

    /** How closely the stub reproduces the measured historical behaviour. */
    public enum Fidelity {
        /** Reproduces the measured historical formulas. Expected to agree on every input. */
        FAITHFUL,
        /**
         * Identical to {@link #FAITHFUL} except that {@code minus} combines uncertainties as
         * {@code |ua - ub|} instead of in quadrature. Used to prove the harness reports a
         * disagreement when there is one, rather than only ever printing green.
         */
        FAULTY_MINUS
    }

    private static final Set<String> SUPPORTED = new HashSet<>();

    static {
        SUPPORTED.add(UOp.binary("URealValue", "add").key());
        SUPPORTED.add(UOp.binary("URealValue", "minus").key());
        SUPPORTED.add(UOp.unary("URealValue", "neg").key());
    }

    private final Fidelity fidelity;
    private final String name;

    private StubCandidate(Fidelity fidelity, String name) {
        this.fidelity = fidelity;
        this.name = name;
    }

    /** A stub that should agree with the oracle on every supported operation. */
    public static StubCandidate faithful() {
        return new StubCandidate(Fidelity.FAITHFUL, "stub-faithful");
    }

    /** A stub with one deliberately wrong operation, used to exercise disagreement reporting. */
    public static StubCandidate faultyMinus() {
        return new StubCandidate(Fidelity.FAULTY_MINUS, "stub-faulty-minus");
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public boolean supports(UOp op) {
        return SUPPORTED.contains(op.key());
    }

    @Override
    public UValue invoke(UOp op, List<UValue> args) {
        if (args.size() != op.arity()) {
            throw new IllegalArgumentException(op.key() + " needs " + op.arity() + " values, got " + args.size());
        }
        UValue receiver = args.get(0);
        if (receiver.kind() != UValue.Kind.UREAL) {
            throw new IllegalArgumentException(op.key() + " needs a UREAL receiver, got " + receiver.canonical());
        }
        double a = receiver.asDouble();
        double ua = receiver.uncertainty();

        switch (op.key()) {
            case "URealValue.neg()":
                return UValue.uReal(-a, ua);
            case "URealValue.add(value)": {
                double[] rhs = asUReal(op, args.get(1));
                return UValue.uReal(a + rhs[0], quadrature(ua, rhs[1]));
            }
            case "URealValue.minus(value)": {
                double[] rhs = asUReal(op, args.get(1));
                double uncertainty = fidelity == Fidelity.FAULTY_MINUS
                        ? Math.abs(ua - rhs[1])
                        : quadrature(ua, rhs[1]);
                return UValue.uReal(a - rhs[0], uncertainty);
            }
            default:
                throw new UnsupportedOperationException("stub does not implement " + op.key());
        }
    }

    /**
     * The historical argument coercion is {@code URealValue.valueOf(Value)}, so a plain REAL or
     * INTEGER argument is promoted to uncertainty 0. The stub mirrors that.
     */
    private static double[] asUReal(UOp op, UValue v) {
        switch (v.kind()) {
            case UREAL:    return new double[] { v.asDouble(), v.uncertainty() };
            case REAL:     return new double[] { v.asDouble(), 0.0 };
            case INTEGER:  return new double[] { v.asInt(), 0.0 };
            case UINTEGER: return new double[] { v.asInt(), v.uncertainty() };
            default:
                throw new IllegalArgumentException(op.key() + " cannot take " + v.canonical());
        }
    }

    /** Measured historical combination rule. Not Math.hypot: see the class comment. */
    private static double quadrature(double ua, double ub) {
        return Math.sqrt(ua * ua + ub * ub);
    }

    @Override
    public void close() {
        // Nothing to release.
    }

    @Override
    public String toString() {
        return "StubCandidate[" + name + "]";
    }
}
