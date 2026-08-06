package org.tzi.use.uml.ocl.value;

import org.tzi.use.uml.ocl.type.TypeFactory;
import java.util.*;

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
    public UStringValue concat(UStringValue o) { String combined=value+o.value; double length=combined.length(); double uncertainty=(value.length()*(1-confidence))+(o.value.length()*(1-o.confidence)); return new UStringValue(combined,length==0?1:Math.max(0,1-uncertainty/length)); }
    public UStringValue lower() { return new UStringValue(value.toLowerCase(), confidence); }
    public UStringValue upper() { return new UStringValue(value.toUpperCase(), confidence); }
    public UIntegerValue size() { return new UIntegerValue(value.length(), value.length()*(1-confidence)); }
    public StringValue at(int index) { if(index<1||index>value.length()) throw new IndexOutOfBoundsException("index="+index); return new StringValue(String.valueOf(value.charAt(index-1))); }
    public UStringValue character(int index) { if(index<1||index>value.length()) throw new IndexOutOfBoundsException("index="+index); return new UStringValue(String.valueOf(value.charAt(index-1)),confidence); }
    public SequenceValue characters() { List<Value> chars=new ArrayList<>(); for(int i=1;i<=value.length();i++) chars.add(character(i)); return new SequenceValue(TypeFactory.mkUString(),chars); }
    public UIntegerValue indexOf(String needle) { return new UIntegerValue(value.indexOf(needle),confidence); }
    public UStringValue substring(int start,int end) { if(start<1||end<start||end>value.length()) throw new IndexOutOfBoundsException(); return new UStringValue(value.substring(start-1,end),confidence); }
    public IntegerValue toInteger() { return IntegerValue.valueOf(Integer.parseInt(value)); }
    public RealValue toReal() { return new RealValue(Double.parseDouble(value)); }
    public BooleanValue toBoolean() { return BooleanValue.get(Boolean.parseBoolean(value)); }
    public UBooleanValue toUBoolean() {
        UBooleanValue yes=uEquals(new UStringValue("TRUE",1));
        UBooleanValue no=uEquals(new UStringValue("FALSE",1));
        if (yes.probability() >= .5) return yes;
        if (no.probability() >= .5) return no.not();
        return UBooleanValue.probability(.5);
    }
    private UBooleanValue compare(UStringValue other, int relation) {
        int cmp=value.compareTo(other.value);
        return UBooleanValue.probability(relation < 0 ? cmp < 0 : relation > 0 ? cmp > 0 : cmp <= 0, confidence*other.confidence);
    }
    public UBooleanValue lessThan(UStringValue o) { return compare(o,-1); }
    public UBooleanValue lessOrEqual(UStringValue o) { return compare(o,0); }
    public UBooleanValue greaterThan(UStringValue o) { return compare(o,1); }
    public UBooleanValue greaterOrEqual(UStringValue o) { int cmp=value.compareTo(o.value); return UBooleanValue.probability(cmp>=0,confidence*o.confidence); }
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
