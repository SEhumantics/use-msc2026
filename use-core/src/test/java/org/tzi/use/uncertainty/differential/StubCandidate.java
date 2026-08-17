package org.tzi.use.uncertainty.differential;

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
 * <p>Two things in this file are worth copying and one is not. Copy the
 * {@link HarnessMarshallingException} discipline on all four failure exits, and copy the fact that
 * every result is attributed through one named method. Do <strong>not</strong> copy what that method
 * does: {@link #attributed(UValue)} <em>declares</em> its Java class, because S1 has no ported object
 * to observe; an S4 adapter holds one and must call {@link UValue#observedFrom(Object)} (D-43).
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

    /** Sorted, so {@link #unsupportedReason(UOp)} renders identically on every run. */
    private static final Set<String> SUPPORTED = new java.util.TreeSet<>();

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
    public String unsupportedReason(UOp op) {
        return name() + " implements only " + SUPPORTED + ", not " + op.key();
    }

    /**
     * All <strong>four</strong> failure exits below raise {@link HarnessMarshallingException}, not
     * {@link IllegalArgumentException} and not {@link UnsupportedOperationException}, because they
     * are failures of <em>this adapter</em> and not of anything under comparison — see the invariant
     * on {@link Candidate}. With the old type, two faithful stubs swept over a non-UREAL receiver
     * corpus produced {@code 169 rows, disagreements 0} with neither side executing a line of
     * arithmetic.
     *
     * <p>The fourth, the {@code default} of the switch, was left as an
     * {@code UnsupportedOperationException} when the other three were converted, and the comment
     * here said "three". It is unreachable through a sweep today — {@link DifferentialSweep#run}
     * consults {@link #supports(UOp)} first, and {@link #SUPPORTED} is exactly the set the switch
     * handles — but the two lists are kept in sync by hand, and {@code SUPPORTED} is static, so a
     * key added to one and not the other would send <em>both</em> shipped stub instances down this
     * exit at once. That is the D-2 shape exactly.
     */
    @Override
    public UValue invoke(UOp op, List<UValue> args) {
        if (args.size() != op.arity()) {
            throw new HarnessMarshallingException(op.key() + " needs " + op.arity() + " values, got " + args.size());
        }
        UValue receiver = args.get(0);
        if (receiver.kind() != UValue.Kind.UREAL) {
            throw new HarnessMarshallingException(op.key() + " needs a UREAL receiver, got " + receiver.canonical());
        }
        double a = receiver.asDouble();
        double ua = receiver.uncertainty();

        switch (op.key()) {
            case "URealValue.neg()":
                return attributed(UValue.uReal(-a, ua));
            case "URealValue.add(value)": {
                double[] rhs = asUReal(op, args.get(1));
                return attributed(UValue.uReal(a + rhs[0], quadrature(ua, rhs[1])));
            }
            case "URealValue.minus(value)": {
                double[] rhs = asUReal(op, args.get(1));
                double uncertainty = fidelity == Fidelity.FAULTY_MINUS
                        ? Math.abs(ua - rhs[1])
                        : quadrature(ua, rhs[1]);
                return attributed(UValue.uReal(a - rhs[0], uncertainty));
            }
            default:
                throw new HarnessMarshallingException("this stub adapter has no case for " + op.key()
                        + ", although supports() claimed it; that is an adapter defect, not a "
                        + "statement about any implementation under comparison");
        }
    }

    /**
     * <strong>Every result goes through here, and this is the one thing in this file an S4 adapter must
     * not copy.</strong> A value is its content <em>plus</em> the Java class the implementation answered
     * with (defect D-18), and there are two routes to that class — observing it off the returned object
     * or stating it. Which route was taken is itself a measurement, because a <em>stated</em> class
     * measures the adapter and not the implementation (defect <strong>D-43</strong>).
     *
     * <p>This stub takes the stating route, deliberately and visibly:
     * {@link UValue#declaredJavaType(String, String)} with the reason below. It has to. It computes in
     * plain Java and <strong>no ported object exists in S1 to observe</strong> — there is no
     * {@code URealValue} in {@code use-core/src/main} yet; writing one is S4. Fabricating an object
     * merely to have something to observe would be a worse lie than an honest declaration, and a
     * measurable one: the harness compares the class's <em>simple</em> name but
     * {@link DifferentialSweep}'s type note compares the fully-qualified names, so a stand-in class in
     * this package would leave every agreement row intact and re-caption all 226 disagreeing rows of
     * {@code s1-smoke-ureal-minus-faulty.tsv} as a "java type mismatch" — a false explanation of an
     * arithmetic finding, in S1's own committed evidence.
     *
     * <p><strong>An S4 adapter has the object and must observe it instead:</strong>
     * <pre>
     *   Object returned = portMethod.invoke(receiver, marshalledArgs);
     *   return UValue.uReal(v, u).observedFrom(returned);   // OBSERVED -- see Candidate
     * </pre>
     * The attribution is explicit here even though these three operations happen to make the factory
     * default right, because "the default was right this time" is what made D-43 invisible: the factory
     * types every value as the {@code Value} class of its kind, which is correct for
     * {@code URealValue.add}/{@code minus}/{@code neg} and wrong for 182 of the 285 enumerated
     * operations. An adapter that says nothing is not agreeing with the harness; it is guessing, and it
     * guesses wrong on two thirds of the surface.
     */
    private static UValue attributed(UValue content) {
        return content.declaredJavaType(UValue.VALUE_PACKAGE + "URealValue",
                "DECLARED, not observed: this stub computes in plain Java and S1 has no ported "
                        + "URealValue object to read a class off. The class named is what the "
                        + "historical operation was measured to return during S1 -- javap -p on the "
                        + "vendored use.jar declares add(Value), minus(Value) and neg() as returning "
                        + "org.tzi.use.uml.ocl.value.URealValue -- and the token compared is the simple "
                        + "name, so a port in another package still matches. An S4 adapter must call "
                        + "UValue.observedFrom(theObjectItsPortReturned) instead: see Candidate.");
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
                throw new HarnessMarshallingException(op.key() + " cannot take " + v.canonical());
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
