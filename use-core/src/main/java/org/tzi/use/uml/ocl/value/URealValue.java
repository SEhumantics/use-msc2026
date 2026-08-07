package org.tzi.use.uml.ocl.value;

import org.tzi.use.uml.ocl.type.TypeFactory;

/**
 * A real representative value with a non-negative absolute uncertainty.
 *
 * <p>The value is read as the mean of a normal distribution whose standard
 * deviation is the uncertainty, which is what makes the comparisons
 * probabilistic: <code>UReal(2,1) &lt; UReal(3,1)</code> evaluates to a
 * <code>UBoolean</code> rather than to a Boolean.
 */
public final class URealValue extends UncertainValue {

    private final double value;
    private final double uncertainty;

    public URealValue(double value, double uncertainty) {
        super(TypeFactory.mkUReal());
        if (!Double.isFinite(value) || !Double.isFinite(uncertainty)) {
            throw new IllegalArgumentException("UReal value and uncertainty must be finite");
        }
        // Historical uDataTypes.UReal normalizes the uncertainty through setU(),
        // so a negative literal or setUncertainty argument is accepted.
        this.value = value;
        this.uncertainty = Math.abs(uncertainty);
    }

    public double value() {
        return value;
    }

    public double uncertainty() {
        return uncertainty;
    }

    @Override
    public boolean isUReal() {
        return true;
    }

    // ---------------------------------------------------------------- arithmetic

    public URealValue add(URealValue o) {
        return new URealValue(value + o.value, Math.hypot(uncertainty, o.uncertainty));
    }

    public URealValue subtract(URealValue o) {
        return new URealValue(value - o.value, this == o ? 0 : Math.hypot(uncertainty, o.uncertainty));
    }

    public URealValue multiply(URealValue o) {
        return new URealValue(value * o.value, Math.sqrt(
                o.value * o.value * uncertainty * uncertainty
                        + value * value * o.uncertainty * o.uncertainty));
    }

    public URealValue divide(URealValue o) {
        if (this == o) return new URealValue(1, 0);
        if (o.uncertainty == 0) return new URealValue(value / o.value, Math.abs(uncertainty / o.value));
        if (uncertainty == 0) return new URealValue(value / o.value, o.uncertainty / (o.value * o.value));
        double byNumerator = (uncertainty * uncertainty) / Math.abs(o.value);
        double byDenominator = (value * value * o.uncertainty * o.uncertainty)
                / (o.value * o.value * o.value * o.value);
        return new URealValue(value / o.value, Math.sqrt(byNumerator + byDenominator));
    }

    public URealValue negate() {
        return new URealValue(-value, uncertainty);
    }

    public URealValue abs() {
        return new URealValue(Math.abs(value), uncertainty);
    }

    /** Historical min/max select the operand whose probabilistic comparison wins. */
    public URealValue min(URealValue o) {
        return o.lessThan(this).toBoolean().value()
                ? new URealValue(o.value, o.uncertainty) : new URealValue(value, uncertainty);
    }

    public URealValue max(URealValue o) {
        return o.greaterThan(this).toBoolean().value()
                ? new URealValue(o.value, o.uncertainty) : new URealValue(value, uncertainty);
    }

    public URealValue inverse() {
        return new URealValue(1, 0).divide(this);
    }

    public URealValue power(double exponent) {
        double result = Math.pow(value, exponent);
        double propagated = Math.abs(exponent * uncertainty * Math.pow(value, exponent - 1));
        if (!Double.isFinite(result) || !Double.isFinite(propagated)) {
            throw new ArithmeticException("invalid power");
        }
        return new URealValue(result, propagated);
    }

    public URealValue sqrt() {
        if (value == 0 && uncertainty == 0) return new URealValue(0, 0);
        if (value < 0) throw new ArithmeticException("sqrt domain");
        return new URealValue(Math.sqrt(value), uncertainty / (2 * Math.sqrt(value)));
    }

    public URealValue floorValue() {
        return new URealValue(Math.floor(value), uncertainty);
    }

    public URealValue roundValue() {
        return new URealValue(Math.round(value), uncertainty);
    }

    // The trigonometric uncertainties are the first-order propagation |f'(x)|*u.
    public URealValue sin() {
        return new URealValue(Math.sin(value), Math.abs(Math.cos(value)) * uncertainty);
    }

    public URealValue cos() {
        return new URealValue(Math.cos(value), Math.abs(Math.sin(value)) * uncertainty);
    }

    public URealValue tan() {
        return sin().divide(cos());
    }

    public URealValue asin() {
        return new URealValue(Math.asin(value),
                Math.abs(value) == 1 ? uncertainty : uncertainty / Math.sqrt(1 - value * value));
    }

    public URealValue acos() {
        return new URealValue(Math.acos(value),
                Math.abs(value) == 1 ? uncertainty : uncertainty / Math.sqrt(1 - value * value));
    }

    public URealValue atan() {
        return new URealValue(Math.atan(value), uncertainty / (1 + value * value));
    }

    // --------------------------------------------------------------- conversions

    public RealValue toReal() {
        return new RealValue(value);
    }

    public IntegerValue toInteger() {
        return IntegerValue.valueOf((int) Math.floor(value));
    }

    /**
     * The USE-level toUInteger operation truncates towards zero and carries the
     * uncertainty over unchanged.
     */
    public UIntegerValue toUInteger() {
        return new UIntegerValue((int) value, uncertainty);
    }

    /**
     * The library conversion the uncertain integer algebra is built on: it floors
     * and folds the discarded fraction into the uncertainty.
     */
    public UIntegerValue toUIntegerFlooring() {
        int floored = (int) Math.floor(value);
        return new UIntegerValue(floored, Math.hypot(uncertainty, value - floored));
    }

    // --------------------------------------------------------------- comparisons

    public UBooleanValue lessThan(URealValue o) {
        return UBooleanValue.probability(true, calculate(o).lt);
    }

    public UBooleanValue greaterThan(URealValue o) {
        return UBooleanValue.probability(true, calculate(o).gt);
    }

    /** The three outcome probabilities of comparing two normal distributions. */
    private static final class Comparison {
        double lt;
        double eq;
        double gt;

        Comparison(double lt, double eq, double gt) {
            this.lt = lt;
            this.eq = eq;
            this.gt = gt;
        }

        Comparison swapped() {
            return new Comparison(gt, eq, lt);
        }
    }

    /**
     * Historical Gaussian comparison algorithm used by UReal and UInteger. The
     * operands are ordered by value first so that only one side of each case has
     * to be written out; {@code swap} undoes that at the end.
     */
    private Comparison calculate(URealValue other) {
        double m1;
        double m2;
        double s1;
        double s2;
        boolean swap = false;
        if (value <= other.value) {
            m1 = value; m2 = other.value; s1 = uncertainty; s2 = other.uncertainty;
        } else {
            m1 = other.value; m2 = value; s1 = other.uncertainty; s2 = uncertainty;
            swap = true;
        }

        Comparison result;

        // Both certain: an ordinary numeric comparison.
        if (s1 == 0 && s2 == 0) {
            result = m1 == m2 ? new Comparison(0, 1, 0)
                    : m1 < m2 ? new Comparison(1, 0, 0) : new Comparison(0, 0, 1);
            return swap ? result.swapped() : result;
        }

        // Exactly one certain: the point is measured against the other density,
        // and equality has probability zero.
        if (s1 == 0) {
            result = new Comparison(1 - cndf(m1, m2, s2), 0, cndf(m1, m2, s2));
            return swap ? result.swapped() : result;
        }
        if (s2 == 0) {
            result = new Comparison(cndf(m2, m1, s1), 0, 1 - cndf(m2, m1, s1));
            return swap ? result.swapped() : result;
        }

        // Equal spread: the densities cross once, halfway between the means.
        if (s1 == s2) {
            double crossing = (m1 + m2) / 2;
            result = new Comparison(cndf(crossing, m1, s1) - cndf(crossing, m2, s2), 0, 0);
            result.eq = 1 - result.lt;
            return swap ? result.swapped() : result;
        }

        // Different spreads: the densities cross twice, at the roots below.
        double radicand = (m1 - m2) * (m1 - m2) - 2 * (s1 * s1 - s2 * s2) * Math.log(s2 / s1);
        if (radicand < 0 || !Double.isFinite(radicand)) {
            // No real crossing: fall back to the difference of the two normals.
            double p = cndf((m2 - m1) / Math.hypot(s1, s2));
            result = new Comparison(p, 0, 1 - p);
            return swap ? result.swapped() : result;
        }
        double root = s1 * s2 * Math.sqrt(radicand);
        double firstCrossing = -(-m2 * s1 * s1 + m1 * s2 * s2 + root) / (s1 * s1 - s2 * s2);
        double secondCrossing = (m2 * s1 * s1 - m1 * s2 * s2 + root) / (s1 * s1 - s2 * s2);
        double lower = Math.min(firstCrossing, secondCrossing);
        double upper = Math.max(firstCrossing, secondCrossing);
        if (s1 < s2) {
            result = new Comparison(
                    1 - cndf(upper, m2, s2) - (1 - cndf(upper, m1, s1)), 0,
                    cndf(lower, m2, s2) - cndf(lower, m1, s1));
        } else {
            result = new Comparison(
                    cndf(lower, m1, s1) - cndf(lower, m2, s2), 0,
                    1 - cndf(upper, m1, s1) - (1 - cndf(upper, m2, s2)));
        }
        result.eq = 1 - result.lt - result.gt;
        return swap ? result.swapped() : result;
    }

    /** Zelen and Severo's rational approximation of the standard normal CDF. */
    private static double cndf(double x) {
        boolean negative = x < 0;
        if (negative) x = -x;
        double k = 1 / (1 + 0.2316419 * x);
        double series = ((((1.330274429 * k - 1.821255978) * k + 1.781477937) * k
                - 0.356563782) * k + 0.319381530) * k;
        double y = 1 - 0.398942280401 * Math.exp(-0.5 * x * x) * series;
        return negative ? 1 - y : y;
    }

    private static double cndf(double x, double mean, double sigma) {
        return cndf((x - mean) / sigma);
    }

    // ---------------------------------------------------------- equality, order

    @Override
    public UBooleanValue uEquals(Value other) {
        if (other instanceof IntegerValue i) return uEquals(new URealValue(i.value(), 0));
        if (other instanceof RealValue r) return uEquals(new URealValue(r.value(), 0));
        if (other instanceof UIntegerValue i) return uEquals(i.toUReal());
        if (!(other instanceof URealValue o)) return UBooleanValue.FALSE;
        return UBooleanValue.probability(true, calculate(o).eq);
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof URealValue x) {
            return round(value, 10) == round(x.value, 10)
                    && round(uncertainty, 10) == round(x.uncertainty, 10);
        }
        if (o instanceof IntegerValue x) return value == x.value() && uncertainty == 0;
        if (o instanceof RealValue x) return value == x.value() && uncertainty == 0;
        if (o instanceof UIntegerValue x) return equals(x.toUReal());
        return false;
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(round(value, 10), round(uncertainty, 10));
    }

    /**
     * Ordering is by representation, not by the probabilistic comparison.
     * {@link #uEquals} calls two overlapping distributions equal, which is not
     * transitive -- UReal(0,3) and UReal(3,3) overlap, UReal(3,3) and UReal(6,3)
     * overlap, UReal(0,3) and UReal(6,3) do not -- so using it here made
     * {@code Collections.sort} throw once a collection was large enough for
     * TimSort to notice. Every caller of this method sorts: set and bag
     * rendering, asSequence, asOrderedSet and ExpOne all go through
     * {@link CollectionValue#getSortedElements()}.
     *
     * <p>Certain and uncertain numbers order together, by value and then by
     * uncertainty, with a certain number carrying uncertainty 0. That keeps a
     * mixed collection in numeric order, which is what the historical rendering
     * shows. {@link RealValue} and {@link IntegerValue} delegate back here for an
     * uncertain operand so that both directions agree.
     */
    @Override
    public int compareTo(Value o) {
        if (o == this) return 0;
        if (o instanceof UndefinedValue) return 1;
        URealValue other = asUReal(o);
        return other != null ? compareRepresentation(other) : toString().compareTo(o.toString());
    }

    /** The numeric value an uncertain real orders itself against, or null. */
    static URealValue asUReal(Value v) {
        if (v instanceof URealValue x) return x;
        if (v instanceof UIntegerValue x) return x.toUReal();
        if (v instanceof IntegerValue x) return new URealValue(x.value(), 0);
        if (v instanceof RealValue x) return new URealValue(x.value(), 0);
        return null;
    }

    /** Consistent with {@link #equals}: both compare at ten decimal places. */
    private int compareRepresentation(URealValue o) {
        int byValue = Double.compare(round(value, 10), round(o.value, 10));
        return byValue != 0 ? byValue
                : Double.compare(round(uncertainty, 10), round(o.uncertainty, 10));
    }

    @Override
    public StringBuilder toString(StringBuilder b) {
        return b.append("UReal(").append(value == 0 ? 0 : round(value, 10))
                .append(", ").append(round(uncertainty, 10)).append(')');
    }

    private static double round(double value, int places) {
        double scale = Math.pow(10, places);
        return Math.round(value * scale) / scale;
    }
}
