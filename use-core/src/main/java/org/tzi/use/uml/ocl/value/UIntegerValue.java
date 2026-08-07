package org.tzi.use.uml.ocl.value;

import org.tzi.use.uml.ocl.type.TypeFactory;

/**
 * An integer representative value with an absolute uncertainty.
 *
 * <p>The comparisons and the equality are those of {@link URealValue}: a
 * uncertain integer widens to an uncertain real and the Gaussian algorithm
 * decides. Only the arithmetic is integral, and it keeps the historical
 * propagation formulas.
 */
public final class UIntegerValue extends UncertainValue {

    private final int value;
    private final double uncertainty;

    public UIntegerValue(int value, double uncertainty) {
        super(TypeFactory.mkUInteger());
        if (!Double.isFinite(uncertainty)) {
            throw new IllegalArgumentException("UInteger uncertainty must be finite");
        }
        // Historical uDataTypes.UInteger normalizes the uncertainty through setU().
        this.value = value;
        this.uncertainty = Math.abs(uncertainty);
    }

    public int value() {
        return value;
    }

    public double uncertainty() {
        return uncertainty;
    }

    @Override
    public boolean isUInteger() {
        return true;
    }

    // --------------------------------------------------------------- conversions

    public URealValue toUReal() {
        return new URealValue(value, uncertainty);
    }

    public IntegerValue toInteger() {
        return IntegerValue.valueOf(value);
    }

    public RealValue toReal() {
        return new RealValue(value);
    }

    // ---------------------------------------------------------------- arithmetic

    public UIntegerValue add(UIntegerValue o) {
        return new UIntegerValue(value + o.value, Math.hypot(uncertainty, o.uncertainty));
    }

    public UIntegerValue subtract(UIntegerValue o) {
        return new UIntegerValue(value - o.value,
                this == o ? 0 : Math.hypot(uncertainty, o.uncertainty));
    }

    /**
     * The squares are taken in double arithmetic; in int arithmetic they overflow
     * for ordinary operands and turn the uncertainty into NaN.
     */
    public UIntegerValue multiply(UIntegerValue o) {
        double x = value;
        double y = o.value;
        return new UIntegerValue(value * o.value, Math.sqrt(
                y * y * uncertainty * uncertainty + x * x * o.uncertainty * o.uncertainty));
    }

    /** Historical UInteger division promotes to UReal. */
    public URealValue divide(UIntegerValue o) {
        if (this == o) return new URealValue(1, 0);
        if (o.value == 0) throw new ArithmeticException("division by zero");
        double quotient = (double) value / o.value;
        if (o.uncertainty == 0) return new URealValue(quotient, Math.abs(uncertainty / o.value));
        if (uncertainty == 0) {
            return new URealValue(quotient, o.uncertainty / ((double) o.value * o.value));
        }
        return new URealValue(quotient, propagatedQuotientUncertainty(o));
    }

    public UIntegerValue mod(UIntegerValue o) {
        if (this == o) return new UIntegerValue(0, 0);
        if (o.value == 0) throw new ArithmeticException("modulo by zero");
        return new UIntegerValue(value % o.value, quotientUncertainty(o));
    }

    public UIntegerValue div(UIntegerValue o) {
        if (this == o) return new UIntegerValue(1, 0);
        if (o.value == 0) throw new ArithmeticException("division by zero");
        // The historical library divides in int arithmetic before flooring, so the
        // flooring is a no-op and the quotient truncates towards zero.
        return new UIntegerValue(value / o.value, quotientUncertainty(o));
    }

    /** Shared uncertainty propagation of the three integral division operations. */
    private double quotientUncertainty(UIntegerValue o) {
        if (o.uncertainty == 0) return Math.abs(uncertainty / o.value);
        if (uncertainty == 0) return o.uncertainty / ((double) o.value * o.value);
        return propagatedQuotientUncertainty(o);
    }

    private double propagatedQuotientUncertainty(UIntegerValue o) {
        double byNumerator = Math.abs((uncertainty * uncertainty) / o.value);
        double byDenominator = ((double) value * value * o.uncertainty * o.uncertainty)
                / Math.pow(o.value, 4);
        return Math.sqrt(byNumerator + byDenominator);
    }

    public UIntegerValue abs() {
        return new UIntegerValue(Math.abs(value), uncertainty);
    }

    public UIntegerValue negate() {
        return new UIntegerValue(-value, uncertainty);
    }

    public UIntegerValue sqrt() {
        return toUReal().sqrt().toUIntegerFlooring();
    }

    public UIntegerValue power(double exponent) {
        return toUReal().power(exponent).toUIntegerFlooring();
    }

    // ---------------------------------------------------------- equality, order

    @Override
    public UBooleanValue uEquals(Value other) {
        return toUReal().uEquals(other);
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof UIntegerValue x) {
            return value == x.value && round(uncertainty, 10) == round(x.uncertainty, 10);
        }
        if (o instanceof IntegerValue x) return value == x.value() && uncertainty == 0;
        if (o instanceof URealValue x) return x.equals(this);
        return false;
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(value, round(uncertainty, 10));
    }

    /**
     * Ordering is delegated to {@link URealValue#compareTo}, which orders certain
     * and uncertain numbers together; see the contract note there.
     */
    @Override
    public int compareTo(Value o) {
        if (o == this) return 0;
        if (o instanceof UndefinedValue) return 1;
        return URealValue.asUReal(o) != null
                ? toUReal().compareTo(o) : toString().compareTo(o.toString());
    }

    @Override
    public StringBuilder toString(StringBuilder b) {
        return b.append("UInteger(").append(value)
                .append(", ").append(round(uncertainty, 10)).append(')');
    }

    private static double round(double value, int places) {
        double scale = Math.pow(10, places);
        return Math.round(value * scale) / scale;
    }
}
