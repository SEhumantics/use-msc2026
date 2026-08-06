package org.tzi.use.uml.ocl.value;

import org.tzi.use.uml.ocl.type.TypeFactory;

/** Integer representative value with absolute uncertainty. */
public final class UIntegerValue extends UncertainValue {
    private final int value; private final double uncertainty;
    public UIntegerValue(int value, double uncertainty) {
        super(TypeFactory.mkUInteger());
        if (!Double.isFinite(uncertainty)) throw new IllegalArgumentException("UInteger uncertainty must be finite");
        // Historical uDataTypes.UInteger normalizes uncertainty through setU().
        this.value=value; this.uncertainty=Math.abs(uncertainty);
    }
    public int value() { return value; }
    public double uncertainty() { return uncertainty; }
    @Override public boolean isUInteger() { return true; }
    public URealValue toUReal() { return new URealValue(value, uncertainty); }
    public IntegerValue toInteger() { return IntegerValue.valueOf(value); }
    public RealValue toReal() { return new RealValue(value); }
    public UIntegerValue add(UIntegerValue o) { return new UIntegerValue(value+o.value, Math.hypot(uncertainty,o.uncertainty)); }
    public UIntegerValue subtract(UIntegerValue o) { return new UIntegerValue(value-o.value, this == o ? 0 : Math.hypot(uncertainty,o.uncertainty)); }
    public UIntegerValue multiply(UIntegerValue o) { return new UIntegerValue(value*o.value, Math.sqrt(o.value*o.value*uncertainty*uncertainty + value*value*o.uncertainty*o.uncertainty)); }
    /** Historical UInteger division promoted to UReal. */
    public URealValue divide(UIntegerValue o) {
        if (this == o) return new URealValue(1,0);
        if (o.value == 0) throw new ArithmeticException("division by zero");
        double quotient=(double)value/o.value;
        if (o.uncertainty == 0) return new URealValue(quotient, Math.abs(uncertainty/o.value));
        if (uncertainty == 0) return new URealValue(quotient, o.uncertainty/(o.value*o.value));
        double c=Math.abs((uncertainty*uncertainty)/o.value);
        double d=(value*value*o.uncertainty*o.uncertainty)/(Math.pow(o.value,4));
        return new URealValue(quotient,Math.sqrt(c+d));
    }
    public UIntegerValue mod(UIntegerValue o) {
        if (this == o) return new UIntegerValue(0,0);
        if(o.value==0) throw new ArithmeticException("modulo by zero");
        if (o.uncertainty == 0) return new UIntegerValue(value%o.value, Math.abs(uncertainty/o.value));
        if (uncertainty == 0) return new UIntegerValue(value%o.value, o.uncertainty/(o.value*o.value));
        double c=Math.abs((uncertainty*uncertainty)/o.value);
        double d=(value*value*o.uncertainty*o.uncertainty)/(Math.pow(o.value,4));
        return new UIntegerValue(value%o.value,Math.sqrt(c+d));
    }
    public UIntegerValue div(UIntegerValue o) {
        if (this == o) return new UIntegerValue(1,0);
        if(o.value==0) throw new ArithmeticException("division by zero");
        if (o.uncertainty == 0) return new UIntegerValue(value/o.value, Math.abs(uncertainty/o.value));
        if (uncertainty == 0) return new UIntegerValue(value/o.value, o.uncertainty/(o.value*o.value));
        double c=Math.abs((uncertainty*uncertainty)/o.value);
        double d=(value*value*o.uncertainty*o.uncertainty)/(Math.pow(o.value,4));
        // The historical library floors the uncertain quotient (the scalar
        // branches retain Java integer division semantics).
        return new UIntegerValue((int)Math.floor((double)value/o.value),Math.sqrt(c+d));
    }
    public UIntegerValue abs() { return new UIntegerValue(Math.abs(value),uncertainty); }
    public UIntegerValue negate() { return new UIntegerValue(-value,uncertainty); }
    public UIntegerValue sqrt() { return toUReal().sqrt().toUIntegerFlooring(); }
    public UIntegerValue power(double exponent) { return toUReal().power(exponent).toUIntegerFlooring(); }
    @Override public UBooleanValue uEquals(Value other) { return toUReal().uEquals(other); }
    @Override public boolean equals(Object o) {
        if(o instanceof UIntegerValue x) return value==x.value&&round(uncertainty,10)==round(x.uncertainty,10);
        if(o instanceof IntegerValue x) return value==x.value()&&uncertainty==0;
        if(o instanceof URealValue x) return x.equals(this);
        return false;
    }
    @Override public int hashCode() { return java.util.Objects.hash(value, round(uncertainty,10)); }
    @Override public int compareTo(Value o) {
        if (o instanceof UndefinedValue) return 1;
        if (o instanceof URealValue) return o.compareTo(this);
        if (o instanceof UIntegerValue || o instanceof IntegerValue || o instanceof RealValue)
            return toUReal().compareTo(o instanceof UIntegerValue x ? x.toUReal() : o);
        return 0;
    }
    @Override public StringBuilder toString(StringBuilder b) { return b.append("UInteger(").append(value).append(", ").append(round(uncertainty,10)).append(')'); }
    private static double round(double value,int places){double scale=Math.pow(10,places);return Math.round(value*scale)/scale;}
}
