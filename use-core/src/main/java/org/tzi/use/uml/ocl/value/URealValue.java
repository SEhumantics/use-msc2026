package org.tzi.use.uml.ocl.value;

import org.tzi.use.uml.ocl.type.TypeFactory;

/** A real representative value with non-negative absolute uncertainty. */
public final class URealValue extends UncertainValue {
    private final double value;
    private final double uncertainty;
    public URealValue(double value, double uncertainty) {
        super(TypeFactory.mkUReal());
        if (!Double.isFinite(value) || !Double.isFinite(uncertainty) || uncertainty < 0)
            throw new IllegalArgumentException("UReal uncertainty must be finite and non-negative");
        this.value = value; this.uncertainty = uncertainty;
    }
    public double value() { return value; }
    public double uncertainty() { return uncertainty; }
    @Override public boolean isUReal() { return true; }
    public URealValue add(URealValue o) { return new URealValue(value + o.value, uncertainty + o.uncertainty); }
    public URealValue subtract(URealValue o) { return new URealValue(value - o.value, uncertainty + o.uncertainty); }
    public URealValue multiply(URealValue o) {
        return new URealValue(value * o.value, Math.abs(value) * o.uncertainty + Math.abs(o.value) * uncertainty + uncertainty * o.uncertainty);
    }
    public URealValue divide(URealValue o) {
        if (Math.abs(o.value) <= o.uncertainty) throw new ArithmeticException("uncertain divisor contains zero");
        double v = value / o.value;
        return new URealValue(v, (Math.abs(value) * o.uncertainty + Math.abs(o.value) * uncertainty) / (o.value * o.value));
    }
    public URealValue negate() { return new URealValue(-value, uncertainty); }
    public URealValue abs() { return new URealValue(Math.abs(value), uncertainty); }
    public URealValue inverse() { if (Math.abs(value)<=uncertainty) throw new ArithmeticException("uncertain value contains zero"); return new URealValue(1/value, uncertainty/(value*value)); }
    public URealValue power(double exponent) { double v=Math.pow(value,exponent); double u=Math.abs(exponent*Math.pow(value,exponent-1))*uncertainty; return new URealValue(v,u); }
    public URealValue sqrt() { if(value<0) throw new ArithmeticException("sqrt domain"); return power(.5); }
    public URealValue floorValue() { return new URealValue(Math.floor(value),uncertainty); }
    public URealValue roundValue() { return new URealValue(Math.rint(value),uncertainty); }
    public URealValue sin() { return new URealValue(Math.sin(value),Math.abs(Math.cos(value))*uncertainty); }
    public URealValue cos() { return new URealValue(Math.cos(value),Math.abs(Math.sin(value))*uncertainty); }
    public URealValue tan() { return new URealValue(Math.tan(value),uncertainty/(Math.cos(value)*Math.cos(value))); }
    public URealValue asin() { return new URealValue(Math.asin(value),uncertainty/Math.sqrt(1-value*value)); }
    public URealValue acos() { return new URealValue(Math.acos(value),uncertainty/Math.sqrt(1-value*value)); }
    public URealValue atan() { return new URealValue(Math.atan(value),uncertainty/(1+value*value)); }
    public RealValue toReal() { return new RealValue(value); }
    public IntegerValue toInteger() { return IntegerValue.valueOf((int) value); }
    public UIntegerValue toUInteger() { return new UIntegerValue((int) value, uncertainty); }
    public UBooleanValue lessThan(URealValue o) { return UBooleanValue.probability(value < o.value, overlap(o)); }
    public UBooleanValue greaterThan(URealValue o) { return o.lessThan(this); }
    private double overlap(URealValue o) { return (uncertainty == 0 && o.uncertainty == 0) ? 1 : 0.5; }
    @Override public UBooleanValue uEquals(Value other) {
        if (other instanceof IntegerValue i) return uEquals(new URealValue(i.value(), 0));
        if (other instanceof RealValue r) return uEquals(new URealValue(r.value(), 0));
        if (other instanceof UIntegerValue i) return uEquals(i.toUReal());
        if (!(other instanceof URealValue o)) return UBooleanValue.FALSE;
        double distance = Math.abs(value-o.value), width=uncertainty+o.uncertainty;
        return UBooleanValue.probability(true, width == 0 ? (distance == 0 ? 1 : 0) : Math.max(0, 1-distance/width));
    }
    @Override public boolean equals(Object o) { return o instanceof URealValue x && Double.compare(value,x.value)==0 && Double.compare(uncertainty,x.uncertainty)==0; }
    @Override public int hashCode() { return java.util.Objects.hash(value, uncertainty); }
    @Override public int compareTo(Value o) { if (o instanceof UndefinedValue) return 1; if (o instanceof URealValue x) return Double.compare(value,x.value); return toString().compareTo(o.toString()); }
    @Override public StringBuilder toString(StringBuilder b) { return b.append("UReal(").append(value).append(", ").append(uncertainty).append(')'); }
}
