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
 * <p><strong>A type-only difference is measured but no longer scored (round 8).</strong> The token is
 * still part of {@link #canonical()}, so the two sides' classes are still compared on every row and the
 * difference is still reported — but where the {@link #content()} is identical and only the class
 * differs, {@link DifferentialSweep} records {@link DiffVerdict#AGREE} and counts the row in
 * {@code Result.javaTypeMismatchCount()} instead of calling it a divergence. The reason is dated and
 * specific: at S1 the ported side's token cannot be authentically observed, because no ported value
 * class exists to observe, so a type-only divergence measures the adapter rather than the port. Content
 * differences are untouched and remain {@link DiffVerdict#DIFFER}. See {@code harness-contract.md} §7
 * for the S4 obligation that turns the count back into a gate clause.
 *
 * <h3>What is compared, and what deliberately is not</h3>
 * The rendered token is the class's <strong>simple name</strong>, not its package. The historical
 * side's classes are loaded from a vendored jar by an isolated class loader and the ported side's
 * from the reactor; comparing fully-qualified names would make every row of a port that relocated
 * the package a false divergence, which is a difference in <em>where the file lives</em> rather than
 * in <em>what the operation answered</em>. The fully qualified names of both sides are written into
 * the row note by {@link DifferentialSweep} whenever they differ, so nothing is discarded.
 *
 * <p>Two costs of that choice, both measured, and neither of them hypothetical:
 * <ul>
 *   <li>Two distinct classes sharing one simple name compare equal. No inner or anonymous class and
 *       no name collision appears anywhere in the 285-operation census (round 6R §6 (v)), so it is
 *       not reachable today — but it is a corpus fact, not a property of the API, and it inherits
 *       D-30. The earlier wording here — "not a shape any port of this API can take" — was an
 *       overclaim and is withdrawn.</li>
 *   <li><strong>The rationale does not extend to {@link Kind#OPAQUE}</strong> (defect <strong>D-44</strong>).
 *       {@link #opaque(String, String)} puts the <em>fully-qualified</em> class name into
 *       {@link #content()}, and {@link HistoricalOracle#opaqueRepresentation(Object)} adds the FQNs
 *       of every field's declaring class as well, so on that branch a port that <em>relocated the
 *       package</em> is a divergence on every row: <strong>197 rows across 17 operations</strong>
 *       ({@code type()} / {@code getRuntimeType()} × 16, {@code UIntegerValue.getuInteger()} × 1).
 *       That is the pre-existing OPAQUE limit — see {@code harness-contract.md} §5 — and it means
 *       package-insensitivity is a property of the <em>type token</em> only, never of the row.</li>
 * </ul>
 *
 * <h3>Where a value's type comes from: OBSERVED or merely ASSUMED — two states, neither chosen</h3>
 * A value's Java type is <strong>observed</strong> or it is <strong>assumed</strong>, and
 * {@link #typeProvenance()} says which. There is no third state and, deliberately, <strong>no route by
 * which an adapter author can hand this class a type token of their choosing</strong>:
 * <ul>
 *   <li>{@link TypeProvenance#OBSERVED} — {@link #observedFrom(Object)} reads
 *       {@code returned.getClass().getName()} off the object a side actually returned. This is what
 *       {@link HistoricalOracle#fromHistorical(Object)} does on every branch, and it is what an
 *       adapter for a real port <strong>must</strong> do from S4 onwards. It is the only route under
 *       which a type difference is a statement about the code being compared.</li>
 *   <li>{@link TypeProvenance#ASSUMED} — nobody looked. A value built by a factory is typed as
 *       <em>the {@code org.tzi.use.uml.ocl.value} class of its kind</em>, which is what a corpus entry
 *       marshals to and is <strong>wrong for 182 of 285 operations</strong>, because most of the
 *       enumerated surface returns a raw {@code boolean} / {@code int} / {@code double} /
 *       {@code String}. Measured: a <em>content-perfect</em> port whose adapter never attributes
 *       differs from the reference's token on 3 445 rows across 182 of 285 operations.</li>
 * </ul>
 *
 * <h3>Why there is no third, author-chosen state (defect D-43, round 8)</h3>
 * Rounds 6 and 7 carried a {@code DECLARED} state: an adapter could name a class and, from round 7,
 * had to write a reason for it. Both shapes were refuted by measurement, and the second refutation is
 * the reason this class now has two states:
 * <ul>
 *   <li>round 6's one-argument {@code asJavaType(String)} took a genuinely wrong-class port from
 *       3 445 {@code DIFFER} rows to <strong>0</strong>;</li>
 *   <li>round 7's {@code declaredJavaType(String javaType, String why)} did exactly the same thing —
 *       {@code declaredJavaType(referenceToken, "x")} produced a sweep <em>byte-identical</em> to the
 *       perfect-port control — and the mandated reason reached <strong>0 rows</strong>, because the
 *       type note only fires when the two class names differ, which a laundering declaration makes
 *       false by construction.</li>
 * </ul>
 * The pattern is the point: the ported side's token is author-influenced at S1 because <strong>there is
 * no ported implementation to observe</strong>. No {@code org.tzi.use.uml.ocl.value.URealValue} exists
 * in {@code use-core/src/main}; writing it <em>is</em> stage S4. Each round therefore invented a new way
 * for the author to influence the token, and patching the newest instance leaves the class of defect
 * intact. Removing the declaration API removes the class: with nothing to declare, there is nothing to
 * declare falsely.
 *
 * <p>The one remaining place a caller names a class is {@link #opaque(String, String)}, and it is not a
 * type-only channel: the name it is given goes into {@link #content()} as well as into
 * {@link #javaType()}, so an untruthful {@code opaque} token is a content difference, and content
 * differences are {@link DiffVerdict#DIFFER} exactly as before. Nothing on this class turns a String
 * into a type token while leaving the content alone.
 *
 * <p>The provenance is deliberately <strong>not</strong> part of {@link #canonical()}: it must never
 * change a verdict, or a subject could excuse a real divergence by admitting it guessed. It is
 * carried into the note of a type-mismatch row instead, so a reader of the evidence can tell
 * "the port returned the wrong class" from "the adapter never looked".
 *
 * <p>There is deliberately no "unattributed" state that matches everything: a wildcard would let a
 * subject opt out of the check by not answering the question, which is defect D-17's shape.
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
        /**
         * {@code SBooleanValue}: a subjective-logic opinion, four components
         * {@code (belief, disbelief, uncertainty, apriori)} rather than the two every other
         * uncertain kind carries.
         *
         * <p>The components ride in {@link UValue#elements} as four {@link #REAL} values, in that
         * fixed order. That is deliberate: widening the private constructor would have touched ten
         * call sites on a file three independent reviews have already audited, for no gain the
         * existing field could not provide.
         */
        SBOOLEAN,
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

    /**
     * How a value came by its {@link #javaType()} — the distinction defect <strong>D-43</strong> is
     * about. See the class comment, "where a value's type comes from".
     *
     * <p><strong>Exactly two states carry a class, and an adapter author chooses neither.</strong> A
     * {@code DECLARED} constant existed in rounds 6 and 7 and is gone: see the class comment for the
     * two measurements that removed it.
     */
    public enum TypeProvenance {
        /**
         * Read off the object a side actually returned, by {@link UValue#observedFrom(Object)}. The
         * only route under which a type difference between the two sides is a statement about the two
         * implementations.
         */
        OBSERVED,
        /**
         * Nobody looked. The factory's default for the kind — the {@code org.tzi.use.uml.ocl.value}
         * class — which is wrong for 182 of the 285 enumerated operations.
         */
        ASSUMED,
        /**
         * There is no class to have a provenance: {@link Kind#NULL} and {@link Kind#VOID} stand for
         * the absence of a result.
         */
        NONE
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
    /** Which of the two routes {@link #javaType} came by. Never part of {@link #canonical()}. */
    private final TypeProvenance typeProvenance;

    /**
     * The factory constructor. Every value built here is {@link TypeProvenance#ASSUMED} — the factory
     * chose the {@code Value} class of the kind and nobody looked at an object — or
     * {@link TypeProvenance#NONE} for the two kinds that stand for the absence of a result. The one
     * attributing route goes through {@link #observed(String)}.
     */
    private UValue(Kind kind, double number, int integer, boolean flag, String text, double aux,
                   List<UValue> elements, String javaType) {
        this(kind, number, integer, flag, text, aux, elements, javaType,
                javaType == null ? TypeProvenance.NONE : TypeProvenance.ASSUMED);
    }

    private UValue(Kind kind, double number, int integer, boolean flag, String text, double aux,
                   List<UValue> elements, String javaType, TypeProvenance typeProvenance) {
        this.kind = kind;
        this.number = number;
        this.integer = integer;
        this.flag = flag;
        this.text = text;
        this.aux = aux;
        this.elements = elements == null ? null : Collections.unmodifiableList(new ArrayList<>(elements));
        this.javaType = javaType;
        this.typeProvenance = typeProvenance;
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

    /**
     * A subjective-logic opinion. The historical side validates
     * {@code |b + d + u - 1| <= 0.001} with each component in {@code [0,1]}
     * ({@code uDataTypes/SBoolean.java:43-52}); this factory does <em>not</em> pre-validate, because
     * rejecting an invalid opinion here would hide the historical side's own rejection behind a
     * harness error instead of measuring it.
     */
    public static UValue sBoolean(double belief, double disbelief, double uncertainty,
                                  double apriori) {
        return new UValue(Kind.SBOOLEAN, Double.NaN, 0, false, null, Double.NaN,
                List.of(real(belief), real(disbelief), real(uncertainty), real(apriori)),
                VALUE_PACKAGE + "SBooleanValue");
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
     * <strong>The same content, typed by the class of the object a side actually returned.</strong>
     * This is the attribution every adapter must use, and the only one that makes a type difference a
     * statement about the code being compared.
     *
     * <p>{@link HistoricalOracle#fromHistorical(Object)} calls it on every branch — that is how the
     * reference side has been attributed since the D-18 fix, and the ported side had no counterpart
     * until this method existed (defect <strong>D-43</strong>). An adapter holds the object its port
     * returned, so it can do exactly the same thing:
     *
     * <pre>
     *   if (portMethod.getReturnType() == void.class) {   // invoke() answers null for a void method,
     *       return UValue.voidValue();                    // which is NOT the same as a null result
     *   }
     *   Object returned = portMethod.invoke(receiver, marshalledArgs);   // or a direct call
     *   if (returned == null) {
     *       return UValue.nullValue();
     *   }
     *   return UValue.uReal(value, uncertainty).observedFrom(returned);  // OBSERVED, not assumed
     * </pre>
     *
     * <p>The {@code void} line is not decoration: {@code Method.invoke} hands back {@code null} for a
     * {@code void}-declared method, so a snippet that tested only for {@code null} would answer
     * {@link #nullValue()} where the reference answers {@link #voidValue()} — on the 8 {@code void}
     * mutators of the enumerated surface (defect D-51).
     *
     * <p>A primitive result needs nothing special: {@code Method.invoke} boxes a {@code boolean} into
     * a {@code java.lang.Boolean} and so does autoboxing at a direct call site, which is precisely
     * what the reference observes for the 140 {@code boolean}-declared operations, the 18 {@code int},
     * the 6 {@code double} and the 18 {@code String}.
     *
     * <p>It is not a blanket cure and must not be read as one: {@code observedFrom} on a genuinely
     * wrong object reports the wrong class, which is the D-18 divergence and is meant to be one.
     * What it removes is the ability to be wrong <em>by omission</em>.
     *
     * @implNote This method cannot itself enforce which object a caller hands it, and that gap is
     *     defect D-52: {@code observedFrom} reads {@code getClass().getName()} off whatever it is
     *     given, so an adapter that hands it an empty stand-in class of the reference's name — rather
     *     than the object the port actually returned — erases a real wrong-class defect to zero in
     *     every published figure, with a verdict tally byte-identical to a defect-free port. The
     *     safety property is therefore on the <em>caller's shape</em>, not on this method's contract:
     *     the object passed here must be the invocation's own return value, captured at one seam and
     *     used for nothing else before this call — see {@link PortedCandidate#fromPorted(Object)} for
     *     the worked example, where the parameter that was invoked and the argument passed here are
     *     the same reference throughout. Do not construct a marker, placeholder or type-shaped
     *     stand-in anywhere in an adapter.
     * @param returned the object the side under comparison returned; may be a boxed primitive, and
     *                 must not be {@code null} — a {@code null} result is {@link #nullValue()} and a
     *                 {@code void} operation is {@link #voidValue()}, neither of which has a class
     * @throws IllegalArgumentException if {@code returned} is {@code null}
     * @throws IllegalStateException    if this value carries no observation — {@link Kind#NULL} and
     *                                  {@link Kind#VOID} mean "no result", and a non-result cannot
     *                                  have been observed as anything
     * @see "docs/port2/harness-contract.md &sect;7 REQUIREMENT on S4, &sect;8 step 1 -- deviation ledger (decided 2026-08-17)"
     */
    public UValue observedFrom(Object returned) {
        if (returned == null) {
            throw new IllegalArgumentException("there is no class to observe on a null result: use "
                    + "UValue.nullValue() for a method that returned null and UValue.voidValue() for "
                    + "a void method (kind " + kind + ", content " + content() + ")");
        }
        return observed(returned.getClass().getName());
    }

    /**
     * The single private path that sets an {@link TypeProvenance#OBSERVED} token, reachable only from
     * {@link #observedFrom(Object)}.
     *
     * <p>It is private, and there is no public counterpart, on purpose: a public method taking a class
     * name is exactly the escape hatch rounds 6 and 7 shipped twice — {@code asJavaType(String)} and
     * then {@code declaredJavaType(String, String)} — and each time a wrong-class port erased the type
     * check with one line. See the class comment.
     */
    private UValue observed(String javaType) {
        Objects.requireNonNull(javaType, "javaType");
        if (!carriesAnObservation()) {
            throw new IllegalStateException("kind " + kind + " stands for the absence of a result, "
                    + "so it cannot have been observed as " + javaType);
        }
        return new UValue(kind, number, integer, flag, text, aux, elements, javaType,
                TypeProvenance.OBSERVED);
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
     * Which of the two routes {@link #javaType()} came by — observed off a returned object, or assumed
     * by a factory. Defect D-43 is the difference between them, and this accessor is what lets the
     * evidence say which one a row rests on.
     *
     * <p>Never part of {@link #canonical()} and never part of {@link #equals(Object)}: provenance must
     * not be able to change a verdict.
     */
    public TypeProvenance typeProvenance() {
        return typeProvenance;
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

    /** Subjective-logic belief mass. */
    public double belief() {
        requireKind(Kind.SBOOLEAN);
        return elements.get(0).number;
    }

    /** Subjective-logic disbelief mass. */
    public double disbelief() {
        requireKind(Kind.SBOOLEAN);
        return elements.get(1).number;
    }

    /** Subjective-logic uncertainty mass. Distinct from {@link #uncertainty()}, which is the
     *  two-component kinds' degree and is not applicable to an opinion. */
    public double uncertaintyMass() {
        requireKind(Kind.SBOOLEAN);
        return elements.get(2).number;
    }

    /** Subjective-logic base rate (prior probability of truth). Not constrained by the sum. */
    public double apriori() {
        requireKind(Kind.SBOOLEAN);
        return elements.get(3).number;
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
            case SBOOLEAN:
                return "SBOOLEAN(" + Double.toString(elements.get(0).number)
                        + "," + Double.toString(elements.get(1).number)
                        + "," + Double.toString(elements.get(2).number)
                        + "," + Double.toString(elements.get(3).number) + ")";
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
