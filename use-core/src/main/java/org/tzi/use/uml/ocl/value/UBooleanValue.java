package org.tzi.use.uml.ocl.value;

import org.tzi.use.uml.ocl.type.TypeFactory;

/** A Boolean proposition represented by its projected probability of truth. */
public final class UBooleanValue extends UncertainValue {
    public static final UBooleanValue TRUE = new UBooleanValue(1.0);
    public static final UBooleanValue FALSE = new UBooleanValue(0.0);
    private final double probability;
    private UBooleanValue(double probability) { super(TypeFactory.mkUBoolean()); this.probability = probability; }
    public static UBooleanValue probability(boolean value, double confidence) { return probability(value ? confidence : 1-confidence); }
    public static UBooleanValue probability(double probability) {
        if (!Double.isFinite(probability) || probability < 0 || probability > 1) throw new IllegalArgumentException("UBoolean probability must be in [0,1]");
        return probability == 1 ? TRUE : probability == 0 ? FALSE : new UBooleanValue(probability);
    }
    public static UBooleanValue valueOf(boolean value) { return value ? TRUE : FALSE; }
    public double probability() { return probability; }
    public boolean value() { return probability >= .5; }
    public double confidence() { return value() ? probability : 1-probability; }
    public UBooleanValue withValue(boolean value) { return probability(value ? confidence() : 1-confidence()); }
    public UBooleanValue withConfidence(double confidence) { return probability(value(),confidence); }
    @Override public boolean isUBoolean() { return true; }
    public BooleanValue toBoolean() { return BooleanValue.get(value()); }
    public BooleanValue toBooleanC(double threshold) { return BooleanValue.get(probability >= threshold); }
    public UBooleanValue not() { return probability(1-probability); }
    public UBooleanValue and(UBooleanValue o) { return probability *o.probability == 0 ? FALSE : probability(probability*o.probability); }
    public UBooleanValue or(UBooleanValue o) { return probability(1-(1-probability)*(1-o.probability)); }
    public UBooleanValue xor(UBooleanValue o) { return probability(probability*(1-o.probability)+(1-probability)*o.probability); }
    public UBooleanValue equivalent(UBooleanValue o) { return xor(o).not(); }
    public UBooleanValue implies(UBooleanValue o) { return not().or(o); }
    public BooleanValue equalsC(double threshold) { return BooleanValue.get(confidence() >= threshold); }
    @Override public UBooleanValue uEquals(Value o) { if (o instanceof BooleanValue b) return probability(b.value() ? probability : 1-probability); if (o instanceof UBooleanValue b) return equivalent(b); return FALSE; }
    @Override public boolean equals(Object o) { return o instanceof UBooleanValue x && Double.compare(probability,x.probability)==0; }
    @Override public int hashCode() { return Double.hashCode(probability); }
    @Override public int compareTo(Value o) { if (o instanceof UndefinedValue) return 1; if (o instanceof UBooleanValue x) return Double.compare(probability,x.probability); return toString().compareTo(o.toString()); }
    @Override public StringBuilder toString(StringBuilder b) { return b.append("UBoolean(").append(value()).append(", ").append(confidence()).append(')'); }
}
