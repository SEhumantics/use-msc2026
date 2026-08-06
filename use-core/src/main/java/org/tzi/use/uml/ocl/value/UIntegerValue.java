package org.tzi.use.uml.ocl.value;

import org.tzi.use.uml.ocl.type.TypeFactory;

/** Integer representative value with absolute uncertainty. */
public final class UIntegerValue extends UncertainValue {
    private final int value; private final double uncertainty;
    public UIntegerValue(int value, double uncertainty) {
        super(TypeFactory.mkUInteger());
        if (!Double.isFinite(uncertainty) || uncertainty < 0) throw new IllegalArgumentException("UInteger uncertainty must be finite and non-negative");
        this.value=value; this.uncertainty=uncertainty;
    }
    public int value() { return value; }
    public double uncertainty() { return uncertainty; }
    @Override public boolean isUInteger() { return true; }
    public URealValue toUReal() { return new URealValue(value, uncertainty); }
    public IntegerValue toInteger() { return IntegerValue.valueOf(value); }
    public RealValue toReal() { return new RealValue(value); }
    public UIntegerValue add(UIntegerValue o) { return new UIntegerValue(value+o.value, uncertainty+o.uncertainty); }
    public UIntegerValue subtract(UIntegerValue o) { return new UIntegerValue(value-o.value, uncertainty+o.uncertainty); }
    public UIntegerValue multiply(UIntegerValue o) { return new UIntegerValue(value*o.value, Math.abs(value)*o.uncertainty+Math.abs(o.value)*uncertainty+uncertainty*o.uncertainty); }
    public URealValue divide(UIntegerValue o) { return toUReal().divide(o.toUReal()); }
    public UIntegerValue mod(UIntegerValue o) { if(o.value==0) throw new ArithmeticException("modulo by zero"); return new UIntegerValue(value%o.value,uncertainty+o.uncertainty); }
    public UIntegerValue div(UIntegerValue o) { if(o.value==0) throw new ArithmeticException("division by zero"); return new UIntegerValue(value/o.value,uncertainty+o.uncertainty); }
    public UIntegerValue abs() { return new UIntegerValue(Math.abs(value),uncertainty); }
    public UIntegerValue negate() { return new UIntegerValue(-value,uncertainty); }
    public UIntegerValue sqrt() { if (value < 0) throw new ArithmeticException("sqrt domain"); return new UIntegerValue((int)Math.sqrt(value), uncertainty == 0 ? 0 : uncertainty/(2*Math.max(1,Math.sqrt(value)))); }
    public UIntegerValue power(double exponent) { double v=Math.pow(value,exponent); double u=Math.abs(exponent*Math.pow(value,exponent-1))*uncertainty; if(!Double.isFinite(v)||!Double.isFinite(u)) throw new ArithmeticException("invalid power"); return new UIntegerValue((int)v,u); }
    @Override public UBooleanValue uEquals(Value other) { return toUReal().uEquals(other); }
    @Override public boolean equals(Object o) {
        if(o instanceof UIntegerValue x) return value==x.value&&round(uncertainty,10)==round(x.uncertainty,10);
        if(o instanceof IntegerValue x) return value==x.value()&&uncertainty==0;
        if(o instanceof URealValue x) return x.equals(this);
        return false;
    }
    @Override public int hashCode() { return java.util.Objects.hash(value, uncertainty); }
    @Override public int compareTo(Value o) { if (o instanceof UndefinedValue) return 1; if (o instanceof UIntegerValue x) return Integer.compare(value,x.value); return toString().compareTo(o.toString()); }
    @Override public StringBuilder toString(StringBuilder b) { return b.append("UInteger(").append(value).append(", ").append(round(uncertainty,10)).append(')'); }
    private static double round(double value,int places){double scale=Math.pow(10,places);return Math.round(value*scale)/scale;}
}
