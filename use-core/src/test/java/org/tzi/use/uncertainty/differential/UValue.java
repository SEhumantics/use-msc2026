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
 * rounding regression. Nothing in {@code canonical()} goes through {@code String.format} or any
 * other locale-sensitive path, so the same values render identically under every default locale.
 *
 * <p>The guarantee holds on the {@link Kind#OPAQUE} branch too, but only because
 * {@link HistoricalOracle#opaqueRepresentation(Object)} rebuilds the representation from the
 * object's declared fields. It did <em>not</em> hold when that branch embedded the foreign
 * {@code toString()}: the vendored historical classes format with {@code %5.3f}
 * ({@code UInteger(%d, %5.3f)}, {@code UReal(%5.3f, %5.3f)},
 * {@code SBoolean(%5.3f, %5.3f, %5.3f, %5.3f)} — verified with {@code javap -c} on
 * {@code atenearesearchgroup.uncertainty.jar}) via the no-Locale
 * {@code String.format(String,Object[])} overload, so OPAQUE comparison rounded to three decimals
 * and flipped to a decimal comma under a European default locale.
 *
 * <h2>The canonical form is type-bearing (defect D-18)</h2>
 * Every value that carries an observation also carries {@link #javaType()}, the Java class it was
 * observed as, and {@link #canonical()} renders it. Two consequences, and both of them are the
 * point:
 * <ul>
 *   <li>A <strong>{@link Kind} difference</strong> was always a difference — {@code UREAL(3.0,0.0)}
 *       and {@code UINTEGER(3,0.0)} have never compared equal — so a port answering
 *       {@code URealValue} where the historical answers {@code UIntegerValue} was already a
 *       {@link DiffVerdict#DIFFER}.</li>
 *   <li>A <strong>runtime-class difference inside one kind</strong> was not. {@code fromHistorical}
 *       maps a raw {@code Boolean}/{@code Integer}/{@code Double}/{@code CharSequence} to the same
 *       kind as {@code BooleanValue}/{@code IntegerValue}/{@code RealValue}/{@code StringValue}, so
 *       right content with the wrong Java type scored {@code AGREE} on <strong>193 of 285</strong>
 *       operations. Measured before the fix: a perfect port that boxes every raw result into its
 *       {@code Value} class produced a verdict tally byte-identical to a perfect port's —
 *       {@code {AGREE=17199, BOTH_THREW=910, HARNESS_ERROR=883, UNMEASURABLE=91}}, 0 {@code DIFFER},
 *       0 diverging operations, and the same 74 stage passes.</li>
 * </ul>
 *
 * <h3>What is compared, and what deliberately is not</h3>
 * The rendered token is the class's <strong>simple name</strong>, not its package. The historical
 * side's classes are loaded from a vendored jar by an isolated class loader and the ported side's
 * from the reactor; comparing fully-qualified names would make every row of a port that relocated
 * the package a false divergence, which is a difference in <em>where the file lives</em> rather than
 * in <em>what the operation answered</em>. A difference the simple name cannot see — two distinct
 * classes with one simple name — is not a shape any port of this API can take, and the fully
 * qualified names of both sides are written into the row note by
 * {@link DifferentialSweep} whenever they differ, so nothing is discarded.
 *
 * <h3>Where an unobserved value gets its type</h3>
 * The factories are unchanged and keep taking content only. A value built by a factory is typed as
 * <em>the {@code org.tzi.use.uml.ocl.value} class of its kind</em> — the type a corpus entry
 * marshals to and the type an adapter returning that kind is claiming. A value unwrapped from a real
 * object is typed as what it actually was, through {@link #asJavaType(String)}. There is deliberately
 * no "unattributed" state that matches everything: a wildcard would let a subject opt out of the
 * check by not answering the question, which is defect D-17's shape.
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
        /**
         * A Java {@code null} came back from a method that is not {@code void}.
         *
         * <p>Not an observation: see {@link UValue#carriesAnObservation()}. It is an outcome, and it
         * is recorded, but "this side produced no value" is not a value, so two of them are not a
         * shared value either.
         */
        NULL,
        /**
         * The operation is declared {@code void}, so there is no result to compare.
         *
         * <p>Distinct from {@link #NULL} on purpose: {@code Method.invoke} returns {@code null} for
         * a {@code void} method, so without this constant a {@code void} operation would be
         * indistinguishable from an operation that genuinely returned {@code null}.
         *
         * <p><strong>The separation alone does not stop an empty-bodied mutator agreeing forever,
         * and this comment used to claim that it did.</strong> It does not, and the claim was
         * measured false: a subject whose every body is empty — returning {@link #voidValue()}, as
         * {@link Candidate}'s own contract instructs — scored 444 agreement rows, every driven row
         * of all six reachable {@code setTypeToRuntimeType()} operations, because {@code VOID} vs
         * {@code VOID} compared equal. What stops it is {@link DiffVerdict#UNMEASURABLE}: a row on
         * which neither side produced a value is not a measurement, so it can never be an agreement.
         */
        VOID,
        /**
         * Anything the harness does not model structurally; carries the class name and a
         * representation built from the object's declared fields.
         *
         * <p>The representation is <em>not</em> the foreign {@code toString()}: see
         * {@link HistoricalOracle#opaqueRepresentation(Object)} for why that would silently round
         * and would change with the default locale.
         */
        OPAQUE
    }

    /** The package the eight modelled {@code Value} classes live in, on both sides. */
    static final String VALUE_PACKAGE = "org.tzi.use.uml.ocl.value.";

    private final Kind kind;
    private final double number;
    private final int integer;
    private final boolean flag;
    private final String text;
    /** uncertainty (UREAL/UINTEGER), probability (UBOOLEAN) or confidence (USTRING); NaN if N/A. */
    private final double aux;
    private final List<UValue> elements;
    /**
     * The fully-qualified name of the Java class this value was observed as, or {@code null} for
     * {@link Kind#NULL} and {@link Kind#VOID}, which stand for the <em>absence</em> of a result and
     * therefore have no observed class. See the class comment, "the canonical form is type-bearing".
     */
    private final String javaType;

    private UValue(Kind kind, double number, int integer, boolean flag, String text, double aux,
                   List<UValue> elements, String javaType) {
        this.kind = kind;
        this.number = number;
        this.integer = integer;
        this.flag = flag;
        this.text = text;
        this.aux = aux;
        this.elements = elements == null ? null : Collections.unmodifiableList(new ArrayList<>(elements));
        this.javaType = javaType;
    }

    // ------------------------------------------------------------------ factories

    public static UValue uReal(double value, double uncertainty) {
        return new UValue(Kind.UREAL, value, 0, false, null, uncertainty, null,
                VALUE_PACKAGE + "URealValue");
    }

    public static UValue uInteger(int value, double uncertainty) {
        return new UValue(Kind.UINTEGER, value, value, false, null, uncertainty, null,
                VALUE_PACKAGE + "UIntegerValue");
    }

    public static UValue uBoolean(boolean value, double probability) {
        return new UValue(Kind.UBOOLEAN, Double.NaN, 0, value, null, probability, null,
                VALUE_PACKAGE + "UBooleanValue");
    }

    public static UValue uString(String value, double confidence) {
        return new UValue(Kind.USTRING, Double.NaN, 0, false, Objects.requireNonNull(value, "value"),
                confidence, null, VALUE_PACKAGE + "UStringValue");
    }

    public static UValue real(double value) {
        return new UValue(Kind.REAL, value, 0, false, null, Double.NaN, null,
                VALUE_PACKAGE + "RealValue");
    }

    public static UValue integer(int value) {
        return new UValue(Kind.INTEGER, value, value, false, null, Double.NaN, null,
                VALUE_PACKAGE + "IntegerValue");
    }

    public static UValue bool(boolean value) {
        return new UValue(Kind.BOOLEAN, Double.NaN, 0, value, null, Double.NaN, null,
                VALUE_PACKAGE + "BooleanValue");
    }

    public static UValue string(String value) {
        return new UValue(Kind.STRING, Double.NaN, 0, false, Objects.requireNonNull(value, "value"),
                Double.NaN, null, VALUE_PACKAGE + "StringValue");
    }

    public static UValue sequence(List<UValue> elements) {
        return new UValue(Kind.SEQUENCE, Double.NaN, 0, false, null, Double.NaN,
                Objects.requireNonNull(elements, "elements"), VALUE_PACKAGE + "SequenceValue");
    }

    public static UValue nullValue() {
        return new UValue(Kind.NULL, Double.NaN, 0, false, null, Double.NaN, null, null);
    }

    /** The result of an operation declared {@code void}. Never equal to {@link #nullValue()}. */
    public static UValue voidValue() {
        return new UValue(Kind.VOID, Double.NaN, 0, false, null, Double.NaN, null, null);
    }

    /** Fallback for a result shape the harness does not model; {@code repr} must be deterministic. */
    public static UValue opaque(String className, String repr) {
        return new UValue(Kind.OPAQUE, Double.NaN, 0, false,
                Objects.requireNonNull(className, "className") + "|" + String.valueOf(repr),
                Double.NaN, null, Objects.requireNonNull(className, "className"));
    }

    /**
     * The same content, re-declared as having been observed as {@code javaType}.
     *
     * <p>This is how {@link HistoricalOracle#fromHistorical(Object)} records what a side
     * <em>actually</em> returned, as opposed to what the factory for that kind assumes. It is the
     * whole of the D-18 fix: a raw {@code java.lang.Boolean} and an
     * {@code org.tzi.use.uml.ocl.value.BooleanValue} carry the same content and are no longer the
     * same canonical form.
     *
     * @throws IllegalStateException if this value carries no observation — {@link Kind#NULL} and
     *         {@link Kind#VOID} mean "no result", and a non-result cannot have been observed as
     *         anything
     */
    public UValue asJavaType(String javaType) {
        Objects.requireNonNull(javaType, "javaType");
        if (!carriesAnObservation()) {
            throw new IllegalStateException("kind " + kind + " stands for the absence of a result, "
                    + "so it cannot have been observed as " + javaType);
        }
        return new UValue(kind, number, integer, flag, text, aux, elements, javaType);
    }

    // ------------------------------------------------------------------ accessors

    public Kind kind() {
        return kind;
    }

    /**
     * The fully-qualified Java class this value was observed as, or {@code null} for
     * {@link Kind#NULL} / {@link Kind#VOID}. {@link #canonical()} renders {@link #typeToken()}, the
     * simple name; this accessor keeps the whole of it, for a note that has to name both sides.
     */
    public String javaType() {
        return javaType;
    }

    /**
     * The part of {@link #javaType()} the canonical form compares: the simple class name, with the
     * package and any enclosing class stripped. See the class comment for why the package is
     * deliberately not compared.
     */
    public String typeToken() {
        return simpleName(javaType);
    }

    /** {@code org.tzi.use.uml.ocl.value.URealValue} -&gt; {@code URealValue}; {@code null} passes through. */
    static String simpleName(String fullyQualified) {
        if (fullyQualified == null) {
            return null;
        }
        int cut = Math.max(fullyQualified.lastIndexOf('.'), fullyQualified.lastIndexOf('$'));
        return cut < 0 ? fullyQualified : fullyQualified.substring(cut + 1);
    }

    /**
     * Whether this instance is an <em>observation</em> — a value the harness can hold up against the
     * other side — as opposed to one of the two kinds that stand for the absence of a result.
     *
     * <p>{@link Kind#VOID} and {@link Kind#NULL} both mean "this side produced no value". Everything
     * else, {@link Kind#OPAQUE} included, carries content: {@code OPAQUE} is a class name plus a
     * representation rebuilt from the object's declared fields, and two of those being equal is a
     * real finding.
     *
     * <p>{@link DifferentialSweep} uses this to decide that a row is
     * {@link DiffVerdict#UNMEASURABLE}: when <em>neither</em> side carries an observation there is
     * nothing to compare, and a comparison that was never made must not be reported as one that
     * succeeded. When only one side does, the sides demonstrably differ — one produced a value and
     * the other did not — and that is a genuine measurement of divergence, so it stays
     * {@link DiffVerdict#DIFFER} and keeps both canonical forms in its columns.
     */
    public boolean carriesAnObservation() {
        return kind != Kind.VOID && kind != Kind.NULL;
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
     *
     * <p>Ends in {@code @<simple class name>} for every kind that carries an observation — the
     * D-18 fix. {@link Kind#NULL} and {@link Kind#VOID} have no observed class and render bare, as
     * they always did. The suffix is an append rather than a new prefix so that every {@code KIND(}
     * form already quoted in the record still reads the same way from the left.
     */
    public String canonical() {
        String content = content();
        return javaType == null ? content : content + "@" + typeToken();
    }

    /** {@link #canonical()} without the type suffix: the content alone. */
    public String content() {
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
            case VOID:
                return "VOID";
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
