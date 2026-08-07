package org.tzi.use.uml.ocl.value;

import java.util.ArrayList;
import java.util.List;

import org.tzi.use.uml.ocl.type.TypeFactory;

/**
 * A string with a confidence in its representative spelling.
 *
 * <p>The confidence propagates through the operations: concatenation weights the
 * two confidences by length, and a comparison of two uncertain strings holds
 * only as far as both spellings do, so its confidence is their product.
 */
public final class UStringValue extends UncertainValue {

    private final String value;
    private final double confidence;

    public UStringValue(String value, double confidence) {
        super(TypeFactory.mkUString());
        if (value == null || !Double.isFinite(confidence) || confidence < 0 || confidence > 1) {
            throw new IllegalArgumentException("UString confidence must be in [0,1]");
        }
        this.value = value;
        this.confidence = confidence;
    }

    public String value() {
        return value;
    }

    public double confidence() {
        return confidence;
    }

    @Override
    public boolean isUString() {
        return true;
    }

    public StringValue toStringValue() {
        return new StringValue(value);
    }

    // ---------------------------------------------------------------- operations

    /** The confidence of the result is the length-weighted mean of the operands'. */
    public UStringValue concat(UStringValue o) {
        String combined = value + o.value;
        double length = combined.length();
        double doubt = value.length() * (1 - confidence) + o.value.length() * (1 - o.confidence);
        return new UStringValue(combined, length == 0 ? 1 : Math.max(0, 1 - doubt / length));
    }

    public UStringValue lower() {
        return new UStringValue(value.toLowerCase(), confidence);
    }

    public UStringValue upper() {
        return new UStringValue(value.toUpperCase(), confidence);
    }

    /** The length is as uncertain as the spelling is, scaled by that length. */
    public UIntegerValue size() {
        return new UIntegerValue(value.length(), value.length() * (1 - confidence));
    }

    public boolean isInRange(int index) {
        return index >= 1 && index <= value.length();
    }

    /** Historical uncertain operation: at returns a UString, keeping the confidence. */
    public UStringValue at(int index) {
        return character(index);
    }

    public UStringValue character(int index) {
        if (!isInRange(index)) throw new IndexOutOfBoundsException("index=" + index);
        return new UStringValue(String.valueOf(value.charAt(index - 1)), confidence);
    }

    public SequenceValue characters() {
        List<Value> chars = new ArrayList<>();
        for (int i = 1; i <= value.length(); i++) {
            chars.add(character(i));
        }
        return new SequenceValue(TypeFactory.mkUString(), chars);
    }

    /** Historically 0-based, unlike the 1-based {@code String::indexOf}. */
    public IntegerValue indexOf(String needle) {
        return IntegerValue.valueOf(value.indexOf(needle));
    }

    /**
     * An invalid range yields the empty string, exactly as {@code String::substring}
     * does in {@link org.tzi.use.uml.ocl.expr.operations.StandardOperationsString}.
     * Throwing here let an IndexOutOfBoundsException escape the evaluator, which no
     * other USE operation does.
     */
    public UStringValue substring(int start, int end) {
        if (start < 1 || end < start || end > value.length()) {
            return new UStringValue("", confidence);
        }
        return new UStringValue(value.substring(start - 1, end), confidence);
    }

    // --------------------------------------------------------------- conversions

    public IntegerValue toInteger() {
        return IntegerValue.valueOf(Integer.parseInt(value));
    }

    public RealValue toReal() {
        return new RealValue(Double.parseDouble(value));
    }

    public BooleanValue toBoolean() {
        return BooleanValue.get(Boolean.parseBoolean(value));
    }

    /**
     * A spelling that is confidently "true" or "false" converts to that truth
     * value; anything else carries no information either way and yields 0.5.
     */
    public UBooleanValue toUBoolean() {
        UBooleanValue yes = uEqualsIgnoreCase(new UStringValue("TRUE", 1));
        UBooleanValue no = uEqualsIgnoreCase(new UStringValue("FALSE", 1));
        if (yes.probability() >= .5) return yes;
        if (no.probability() >= .5) return no.not();
        return UBooleanValue.probability(.5);
    }

    public UBooleanValue uEqualsIgnoreCase(UStringValue other) {
        return UBooleanValue.probability(
                value.equalsIgnoreCase(other.value), confidence * other.confidence);
    }

    // --------------------------------------------------------------- comparisons

    private UBooleanValue compare(UStringValue other, int relation) {
        int cmp = value.compareTo(other.value);
        boolean holds = relation < 0 ? cmp < 0 : relation > 0 ? cmp > 0 : cmp <= 0;
        return UBooleanValue.probability(holds, confidence * other.confidence);
    }

    public UBooleanValue lessThan(UStringValue o) {
        return compare(o, -1);
    }

    public UBooleanValue lessOrEqual(UStringValue o) {
        return compare(o, 0);
    }

    public UBooleanValue greaterThan(UStringValue o) {
        return compare(o, 1);
    }

    public UBooleanValue greaterOrEqual(UStringValue o) {
        return UBooleanValue.probability(
                value.compareTo(o.value) >= 0, confidence * o.confidence);
    }

    // ---------------------------------------------------------- equality, order

    @Override
    public UBooleanValue uEquals(Value other) {
        if (other instanceof StringValue s) {
            return UBooleanValue.probability(
                    value.equals(s.value()) ? confidence : 1 - confidence);
        }
        if (other instanceof UStringValue s) {
            double both = confidence * s.confidence;
            return UBooleanValue.probability(value.equals(s.value) ? both : 1 - both);
        }
        return UBooleanValue.FALSE;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof UStringValue x
                && value.equals(x.value)
                && Double.compare(confidence, x.confidence) == 0;
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(value, confidence);
    }

    /**
     * Ordering is by spelling, which is a total order; see the contract note on
     * {@link URealValue#compareTo}. Two spellings that differ only in confidence
     * tie, which is consistent enough for sorting because the relation stays
     * transitive.
     */
    @Override
    public int compareTo(Value o) {
        if (o == this) return 0;
        if (o instanceof UndefinedValue) return 1;
        if (o instanceof UStringValue x) return value.compareTo(x.value);
        return toString().compareTo(o.toString());
    }

    @Override
    public StringBuilder toString(StringBuilder b) {
        return b.append("UString('").append(value).append("', ").append(confidence).append(')');
    }
}
