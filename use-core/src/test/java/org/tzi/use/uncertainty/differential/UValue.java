package org.tzi.use.uncertainty.differential;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * A plain-Java, reflection-free representation of an OCL value as it crosses the boundary between
 * the differential harness and either side under comparison.
 *
 * <p>The whole point of this class is that no caller of {@link Candidate} ever touches a
 * {@code java.lang.reflect} type or a {@code Class} object belonging to the isolated historical
 * class loader. The historical side unwraps into {@code UValue}; the ported side (from S4 onwards)
 * wraps into {@code UValue}; the sweep diffs {@code UValue} against {@code UValue}.
 *
 * <p>Comparison is by {@link #canonical()}, which is derived from {@link Double#toString(double)}
 * and is therefore <em>exact</em>: {@code 0.0} and {@code -0.0} differ, {@code NaN} equals
 * {@code NaN}. That is deliberate. A differential harness that silently rounds cannot detect a
 * rounding regression.
 *
 * <p>Test-scoped. Not part of the product.
 */
public final class UValue {

    /** Which OCL value shape this instance stands for. */
    public enum Kind {
        /** {@code URealValue}: value + uncertainty. */
        UREAL,
        /** {@code UIntegerValue}: value + uncertainty. */
        UINTEGER,
        /** {@code UBooleanValue}: value + probability. */
        UBOOLEAN,
        /** {@code UStringValue}: value + confidence. */
        USTRING,
        /** {@code RealValue}. */
        REAL,
        /** {@code IntegerValue}. */
        INTEGER,
        /** {@code BooleanValue}. */
        BOOLEAN,
        /** {@code StringValue}. */
        STRING,
        /** {@code SequenceValue} and friends. */
        SEQUENCE,
        /** A Java {@code null} came back. */
        NULL,
        /** Anything the harness does not model structurally; carries the class name and toString. */
        OPAQUE
    }

    private final Kind kind;
    private final double number;
    private final int integer;
    private final boolean flag;
    private final String text;
    /** uncertainty (UREAL/UINTEGER), probability (UBOOLEAN) or confidence (USTRING); NaN if N/A. */
    private final double aux;
    private final List<UValue> elements;

    private UValue(Kind kind, double number, int integer, boolean flag, String text, double aux,
                   List<UValue> elements) {
        this.kind = kind;
        this.number = number;
        this.integer = integer;
        this.flag = flag;
        this.text = text;
        this.aux = aux;
        this.elements = elements == null ? null : Collections.unmodifiableList(new ArrayList<>(elements));
    }

    // ------------------------------------------------------------------ factories

    public static UValue uReal(double value, double uncertainty) {
        return new UValue(Kind.UREAL, value, 0, false, null, uncertainty, null);
    }

    public static UValue uInteger(int value, double uncertainty) {
        return new UValue(Kind.UINTEGER, value, value, false, null, uncertainty, null);
    }

    public static UValue uBoolean(boolean value, double probability) {
        return new UValue(Kind.UBOOLEAN, Double.NaN, 0, value, null, probability, null);
    }

    public static UValue uString(String value, double confidence) {
        return new UValue(Kind.USTRING, Double.NaN, 0, false, Objects.requireNonNull(value, "value"),
                confidence, null);
    }

    public static UValue real(double value) {
        return new UValue(Kind.REAL, value, 0, false, null, Double.NaN, null);
    }

    public static UValue integer(int value) {
        return new UValue(Kind.INTEGER, value, value, false, null, Double.NaN, null);
    }

    public static UValue bool(boolean value) {
        return new UValue(Kind.BOOLEAN, Double.NaN, 0, value, null, Double.NaN, null);
    }

    public static UValue string(String value) {
        return new UValue(Kind.STRING, Double.NaN, 0, false, Objects.requireNonNull(value, "value"),
                Double.NaN, null);
    }

    public static UValue sequence(List<UValue> elements) {
        return new UValue(Kind.SEQUENCE, Double.NaN, 0, false, null, Double.NaN,
                Objects.requireNonNull(elements, "elements"));
    }

    public static UValue nullValue() {
        return new UValue(Kind.NULL, Double.NaN, 0, false, null, Double.NaN, null);
    }

    /** Fallback for a result shape the harness does not model; {@code repr} must be deterministic. */
    public static UValue opaque(String className, String repr) {
        return new UValue(Kind.OPAQUE, Double.NaN, 0, false,
                Objects.requireNonNull(className, "className") + "|" + String.valueOf(repr),
                Double.NaN, null);
    }

    // ------------------------------------------------------------------ accessors

    public Kind kind() {
        return kind;
    }

    /** The numeric payload of a UREAL/REAL/UINTEGER/INTEGER. */
    public double asDouble() {
        requireKind(Kind.UREAL, Kind.REAL, Kind.UINTEGER, Kind.INTEGER);
        return number;
    }

    /** The integer payload of a UINTEGER/INTEGER. */
    public int asInt() {
        requireKind(Kind.UINTEGER, Kind.INTEGER);
        return integer;
    }

    /** The boolean payload of a UBOOLEAN/BOOLEAN. */
    public boolean asBoolean() {
        requireKind(Kind.UBOOLEAN, Kind.BOOLEAN);
        return flag;
    }

    /** The string payload of a USTRING/STRING. */
    public String asString() {
        requireKind(Kind.USTRING, Kind.STRING);
        return text;
    }

    /** Uncertainty for UREAL/UINTEGER, probability for UBOOLEAN, confidence for USTRING. */
    public double aux() {
        requireKind(Kind.UREAL, Kind.UINTEGER, Kind.UBOOLEAN, Kind.USTRING);
        return aux;
    }

    /** Alias of {@link #aux()} for UREAL/UINTEGER, where the historical accessor is uncertainty(). */
    public double uncertainty() {
        requireKind(Kind.UREAL, Kind.UINTEGER);
        return aux;
    }

    /** Alias of {@link #aux()} for UBOOLEAN, where the historical accessor is probability(). */
    public double probability() {
        requireKind(Kind.UBOOLEAN);
        return aux;
    }

    /** Alias of {@link #aux()} for USTRING, where the historical accessor is confidence(). */
    public double confidence() {
        requireKind(Kind.USTRING);
        return aux;
    }

    public List<UValue> elements() {
        requireKind(Kind.SEQUENCE);
        return elements;
    }

    private void requireKind(Kind... allowed) {
        for (Kind k : allowed) {
            if (kind == k) {
                return;
            }
        }
        throw new IllegalStateException("accessor not applicable to kind " + kind + " (" + canonical() + ")");
    }

    // ------------------------------------------------------------------ canonical form

    /**
     * A deterministic, TSV-safe rendering used both for reporting and for the agreement verdict.
     * Doubles go through {@link Double#toString(double)}, so the comparison is exact.
     */
    public String canonical() {
        switch (kind) {
            case UREAL:
                return "UREAL(" + Double.toString(number) + "," + Double.toString(aux) + ")";
            case UINTEGER:
                return "UINTEGER(" + integer + "," + Double.toString(aux) + ")";
            case UBOOLEAN:
                return "UBOOLEAN(" + flag + "," + Double.toString(aux) + ")";
            case USTRING:
                return "USTRING(" + quote(text) + "," + Double.toString(aux) + ")";
            case REAL:
                return "REAL(" + Double.toString(number) + ")";
            case INTEGER:
                return "INTEGER(" + integer + ")";
            case BOOLEAN:
                return "BOOLEAN(" + flag + ")";
            case STRING:
                return "STRING(" + quote(text) + ")";
            case SEQUENCE: {
                StringBuilder sb = new StringBuilder("SEQUENCE[");
                for (int i = 0; i < elements.size(); i++) {
                    if (i > 0) {
                        sb.append(',');
                    }
                    sb.append(elements.get(i).canonical());
                }
                return sb.append(']').toString();
            }
            case NULL:
                return "NULL";
            case OPAQUE:
            default:
                return "OPAQUE(" + quote(text) + ")";
        }
    }

    /** Escapes so a canonical form can never break TSV row or column framing. */
    static String quote(String s) {
        if (s == null) {
            return "<null>";
        }
        StringBuilder sb = new StringBuilder(s.length() + 2).append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\': sb.append("\\\\"); break;
                case '"':  sb.append("\\\""); break;
                case '\t': sb.append("\\t");  break;
                case '\n': sb.append("\\n");  break;
                case '\r': sb.append("\\r");  break;
                default:
                    if (c < 0x20 || c == 0x7f) {
                        // Hand-rolled hex: String.format is locale-sensitive, canonical forms must not be.
                        sb.append("\\u");
                        for (int shift = 12; shift >= 0; shift -= 4) {
                            sb.append(Character.forDigit((c >> shift) & 0xf, 16));
                        }
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.append('"').toString();
    }

    @Override
    public String toString() {
        return canonical();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof UValue && canonical().equals(((UValue) o).canonical());
    }

    @Override
    public int hashCode() {
        return canonical().hashCode();
    }
}
