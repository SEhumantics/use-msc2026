package org.tzi.use.uml.ocl.value;

import org.tzi.use.uml.ocl.type.TypeFactory;

/** String with a confidence in its representative spelling. */
public final class UStringValue extends UncertainValue {
    private final String value; private final double confidence;
    public UStringValue(String value, double confidence) {
        super(TypeFactory.mkUString());
        if (value == null || !Double.isFinite(confidence) || confidence < 0 || confidence > 1) throw new IllegalArgumentException("UString confidence must be in [0,1]");
        this.value=value; this.confidence=confidence;
    }
    public String value() { return value; }
    public double confidence() { return confidence; }
    @Override public boolean isUString() { return true; }
    public StringValue toStringValue() { return new StringValue(value); }
    public UStringValue concat(UStringValue o) { return new UStringValue(value+o.value, confidence*o.confidence); }
    public UStringValue lower() { return new UStringValue(value.toLowerCase(), confidence); }
    public UStringValue upper() { return new UStringValue(value.toUpperCase(), confidence); }
    public UIntegerValue size() { return new UIntegerValue(value.length(), 0); }
    @Override public UBooleanValue uEquals(Value other) {
        if (other instanceof StringValue s) return UBooleanValue.probability(value.equals(s.value()) ? confidence : 1-confidence);
        if (other instanceof UStringValue s) return UBooleanValue.probability(value.equals(s.value) ? confidence*s.confidence : 1-confidence*s.confidence);
        return UBooleanValue.FALSE;
    }
    @Override public boolean equals(Object o) { return o instanceof UStringValue x && value.equals(x.value) && Double.compare(confidence,x.confidence)==0; }
    @Override public int hashCode() { return java.util.Objects.hash(value, confidence); }
    @Override public int compareTo(Value o) { if (o instanceof UndefinedValue) return 1; if (o instanceof UStringValue x) return value.compareTo(x.value); return toString().compareTo(o.toString()); }
    @Override public StringBuilder toString(StringBuilder b) { return b.append("UString('").append(value).append("', ").append(confidence).append(')'); }
}
