package org.tzi.use.uncertainty.differential;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Produces the inputs a sweep runs on. Two sources, in a fixed order: a hand-written boundary
 * corpus first, then pseudo-random values drawn from {@code new java.util.Random(seed)}.
 *
 * <p>The seed is supplied by the caller and recorded in the report header. {@code Math.random()},
 * {@code System.nanoTime()} and any other ambient source of entropy are deliberately absent: a
 * differential result that cannot be replayed is not evidence.
 *
 * <p>Boundary coverage required by the later stages, and where it lives:
 * <ul>
 *   <li>uncertainty / confidence / probability exactly {@code 0.0} and exactly {@code 1.0} —
 *       every {@code *Boundaries()} list</li>
 *   <li>negative values and zero — {@link #uRealBoundaries()}, {@link #uIntegerBoundaries()}</li>
 *   <li>zero divisor — {@link #zeroDivisors()}</li>
 *   <li>empty string — {@link #uStringBoundaries()}, {@link #stringBoundaries()}</li>
 *   <li>out-of-range index — {@link #indexBoundaries()}</li>
 *   <li>NaN and infinities — {@link #uRealBoundaries()} (reachable: the historical
 *       {@code URealValue(double,double)} constructor takes raw doubles)</li>
 *   <li>plain (non-uncertain) booleans and strings — {@link #booleanBoundaries()},
 *       {@link #stringBoundaries()}</li>
 * </ul>
 *
 * <p><strong>A receiver type with no corpus is not covered, whatever {@code supports()} says.</strong>
 * {@code BooleanValue} and {@code StringValue} were marshallable from the start, so every one of
 * their 52 operations reported {@code supports() == true}, produced rows, and then failed the
 * per-row receiver check on all of them: 52 operations at 100 % {@code HARNESS_ERROR} and
 * <em>zero</em> measurements against a perfect port (defect D-19). {@link #booleanBoundaries()} and
 * {@link #stringBoundaries()} exist to close that, and closing it is expected to <em>enlarge</em> the
 * population of single-valued operations, because most of what those 52 do is answer a type
 * predicate. That is the correct trade: more surface measured, with its weakness labelled by
 * {@link DifferentialSweep.Result#distinctReferenceValues()}, rather than less surface measured and
 * the weakness invisible.
 *
 * <p>Test-scoped. Not part of the product.
 */
public final class InputGenerator {

    /**
     * The recorded seed for stage S1. Any run that does not override it reproduces byte for byte.
     * Chosen as the S1 date, so it is traceable rather than arbitrary.
     */
    public static final long DEFAULT_SEED = 20260817L;

    private final long seed;
    private final Random random;

    public InputGenerator(long seed) {
        this.seed = seed;
        this.random = new Random(seed);
    }

    public InputGenerator() {
        this(DEFAULT_SEED);
    }

    /** The seed this generator was constructed with; goes verbatim into the report header. */
    public long seed() {
        return seed;
    }

    // ------------------------------------------------------------------ random draws

    /** Value in [-100, 100], uncertainty in [0, 1], both rounded to 6 dp for a legible report. */
    public UValue randomUReal() {
        return UValue.uReal(round6(random.nextDouble() * 200.0 - 100.0), round6(random.nextDouble()));
    }

    /** Value in [-1000, 1000], uncertainty in [0, 1]. */
    public UValue randomUInteger() {
        return UValue.uInteger(random.nextInt(2001) - 1000, round6(random.nextDouble()));
    }

    /** Probability in [0, 1]. */
    public UValue randomUBoolean() {
        return UValue.uBoolean(random.nextBoolean(), round6(random.nextDouble()));
    }

    /** Length 0..8 over a small deterministic alphabet, confidence in [0, 1]. */
    public UValue randomUString() {
        final String alphabet = "abcXYZ019 ";
        int length = random.nextInt(9);
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(alphabet.charAt(random.nextInt(alphabet.length())));
        }
        return UValue.uString(sb.toString(), round6(random.nextDouble()));
    }

    /** A plain (non-uncertain) {@code BooleanValue}. */
    public UValue randomBoolean() {
        return UValue.bool(random.nextBoolean());
    }

    /** A plain (non-uncertain) {@code StringValue}, length 0..8 over the same alphabet. */
    public UValue randomString() {
        final String alphabet = "abcXYZ019 ";
        int length = random.nextInt(9);
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(alphabet.charAt(random.nextInt(alphabet.length())));
        }
        return UValue.string(sb.toString());
    }

    private static double round6(double d) {
        if (!Double.isFinite(d)) {
            return d;
        }
        return Math.round(d * 1_000_000.0) / 1_000_000.0;
    }

    // ------------------------------------------------------------------ corpora

    /**
     * Subjective-logic opinions. Boundaries first, then {@code randomCount} random opinions drawn
     * <em>on the simplex</em>.
     *
     * <p>Sampling four independent components would be useless: the historical constructor requires
     * {@code |b + d + u - 1| <= 0.001} ({@code uDataTypes/SBoolean.java:43-52}), which a random
     * 4-tuple essentially never satisfies, so nearly every row would throw on both sides. Since D2
     * that scores {@link DiffVerdict#BOTH_THREW} rather than agreement, so it would fail visibly --
     * but it would still be zero evidence. The three masses are therefore drawn and normalised, and
     * the base rate {@code a} is drawn independently on {@code [0,1]} because it is not in the sum.
     */
    public List<UValue> sBooleanCorpus(int randomCount) {
        return corpus(sBooleanBoundaries(), randomCount, this::randomSBoolean);
    }

    /** One opinion, uniformly on the simplex for (b,d,u), independently on [0,1] for a. */
    public UValue randomSBoolean() {
        double b = random.nextDouble(), d = random.nextDouble(), u = random.nextDouble();
        double sum = b + d + u;
        if (sum == 0.0) {
            b = 1.0; d = 0.0; u = 0.0; sum = 1.0;
        }
        return UValue.sBoolean(b / sum, d / sum, u / sum, random.nextDouble());
    }

    /**
     * SBoolean boundaries. Every named predicate the fork exposes gets at least one witness --
     * otherwise that predicate is single-valued over the corpus and gives agreement away for free
     * (D-15) -- plus the two edges of the {@code 0.001} sum tolerance, on both sides of it.
     */
    public static List<UValue> sBooleanBoundaries() {
        List<UValue> out = new ArrayList<>();
        // absolute true / false. Builder.build() INTERNS these two: it returns the shared TRUE and
        // FALSE constants rather than a fresh instance, so they take a different code path.
        out.add(UValue.sBoolean(1.0, 0.0, 0.0, 1.0));
        out.add(UValue.sBoolean(0.0, 1.0, 0.0, 1.0));
        // the non-interned twins: identical masses, base rate 0.5, so NOT the interned instances.
        // These are what catch an implementation comparing by identity rather than by value.
        out.add(UValue.sBoolean(1.0, 0.0, 0.0, 0.5));
        out.add(UValue.sBoolean(0.0, 1.0, 0.0, 0.5));
        // vacuous / maximised uncertainty
        out.add(UValue.sBoolean(0.0, 0.0, 1.0, 0.5));
        out.add(UValue.sBoolean(0.0, 0.0, 1.0, 0.0));
        out.add(UValue.sBoolean(0.0, 0.0, 1.0, 1.0));
        // dogmatic (u == 0) but not absolute
        out.add(UValue.sBoolean(0.5, 0.5, 0.0, 0.5));
        out.add(UValue.sBoolean(0.25, 0.75, 0.0, 0.5));
        // generic uncertain opinions
        out.add(UValue.sBoolean(0.3, 0.2, 0.5, 0.5));
        out.add(UValue.sBoolean(0.6, 0.1, 0.3, 0.9));
        // base-rate extremes against a fixed mass triple: `a` is not in the sum, so varying it is
        // free discriminating power for baseRate/projection/applyOn.
        out.add(UValue.sBoolean(0.3, 0.2, 0.5, 0.0));
        out.add(UValue.sBoolean(0.3, 0.2, 0.5, 1.0));
        // the 0.001 tolerance band: these construct
        out.add(UValue.sBoolean(0.3005, 0.2, 0.5, 0.5));
        out.add(UValue.sBoolean(0.2995, 0.2, 0.5, 0.5));
        // just outside it: these must throw on BOTH sides
        out.add(UValue.sBoolean(0.3015, 0.2, 0.5, 0.5));
        out.add(UValue.sBoolean(0.2985, 0.2, 0.5, 0.5));
        // out of range in a single component
        out.add(UValue.sBoolean(-0.1, 0.6, 0.5, 0.5));
        out.add(UValue.sBoolean(0.3, 0.2, 0.5, 1.5));
        return out;
    }

    /** Boundaries first (fixed order), then {@code randomCount} random UReals. */
    public List<UValue> uRealCorpus(int randomCount) {
        return corpus(uRealBoundaries(), randomCount, this::randomUReal);
    }

    public List<UValue> uIntegerCorpus(int randomCount) {
        return corpus(uIntegerBoundaries(), randomCount, this::randomUInteger);
    }

    public List<UValue> uBooleanCorpus(int randomCount) {
        return corpus(uBooleanBoundaries(), randomCount, this::randomUBoolean);
    }

    public List<UValue> uStringCorpus(int randomCount) {
        return corpus(uStringBoundaries(), randomCount, this::randomUString);
    }

    /**
     * Plain {@code BooleanValue} inputs. See {@link #booleanBoundaries()} for why this corpus had to
     * exist before 27 operations could be measured at all.
     */
    public List<UValue> booleanCorpus(int randomCount) {
        return corpus(booleanBoundaries(), randomCount, this::randomBoolean);
    }

    /** Plain {@code StringValue} inputs. See {@link #stringBoundaries()}. */
    public List<UValue> stringCorpus(int randomCount) {
        return corpus(stringBoundaries(), randomCount, this::randomString);
    }

    private List<UValue> corpus(List<UValue> boundaries, int randomCount,
                                java.util.function.Supplier<UValue> draw) {
        if (randomCount < 0) {
            throw new IllegalArgumentException("randomCount must not be negative: " + randomCount);
        }
        List<UValue> out = new ArrayList<>(boundaries);
        for (int i = 0; i < randomCount; i++) {
            out.add(draw.get());
        }
        return Collections.unmodifiableList(out);
    }

    // ------------------------------------------------------------------ boundary corpora

    /**
     * UReal boundaries. Includes zero, negative zero, negatives, the uncertainty endpoints 0.0 and
     * 1.0, and NaN / both infinities in both the value and the uncertainty position.
     */
    public static List<UValue> uRealBoundaries() {
        return Collections.unmodifiableList(Arrays.asList(
                UValue.uReal(0.0, 0.0),
                UValue.uReal(0.0, 1.0),
                UValue.uReal(-0.0, 0.0),
                UValue.uReal(1.0, 0.0),
                UValue.uReal(1.0, 1.0),
                UValue.uReal(-1.0, 0.0),
                UValue.uReal(-1.0, 1.0),
                UValue.uReal(-1.0, 0.5),
                UValue.uReal(0.5, 0.5),
                UValue.uReal(-0.5, 0.25),
                UValue.uReal(2.0, 0.0),
                UValue.uReal(100.0, 0.001),
                UValue.uReal(-100.0, 0.001),
                UValue.uReal(Double.MIN_VALUE, 0.0),
                UValue.uReal(Double.MAX_VALUE, 0.0),
                UValue.uReal(-Double.MAX_VALUE, 0.0),
                UValue.uReal(Double.NaN, 0.0),
                UValue.uReal(Double.POSITIVE_INFINITY, 0.0),
                UValue.uReal(Double.NEGATIVE_INFINITY, 0.0),
                UValue.uReal(1.0, Double.NaN),
                UValue.uReal(1.0, Double.POSITIVE_INFINITY),
                UValue.uReal(1.0, -1.0)));
    }

    /** UInteger boundaries: zero, negatives, int extrema, uncertainty endpoints. */
    public static List<UValue> uIntegerBoundaries() {
        return Collections.unmodifiableList(Arrays.asList(
                UValue.uInteger(0, 0.0),
                UValue.uInteger(0, 1.0),
                UValue.uInteger(1, 0.0),
                UValue.uInteger(1, 1.0),
                UValue.uInteger(-1, 0.0),
                UValue.uInteger(-1, 1.0),
                UValue.uInteger(2, 0.5),
                UValue.uInteger(-2, 0.5),
                UValue.uInteger(7, 0.25),
                UValue.uInteger(Integer.MAX_VALUE, 0.0),
                UValue.uInteger(Integer.MIN_VALUE, 0.0),
                UValue.uInteger(1, Double.NaN),
                UValue.uInteger(1, -1.0)));
    }

    /** UBoolean boundaries: both truth values against probability 0.0, 0.5 and 1.0, plus NaN. */
    public static List<UValue> uBooleanBoundaries() {
        return Collections.unmodifiableList(Arrays.asList(
                UValue.uBoolean(true, 0.0),
                UValue.uBoolean(true, 0.5),
                UValue.uBoolean(true, 1.0),
                UValue.uBoolean(false, 0.0),
                UValue.uBoolean(false, 0.5),
                UValue.uBoolean(false, 1.0),
                UValue.uBoolean(true, Double.NaN),
                UValue.uBoolean(false, -1.0),
                UValue.uBoolean(false, 2.0)));
    }

    /**
     * UString boundaries: the empty string, whitespace, mixed case, characters that would break a
     * naive TSV writer (tab, newline, quote, backslash) and a non-BMP-adjacent unicode sample, each
     * against the confidence endpoints.
     */
    public static List<UValue> uStringBoundaries() {
        return Collections.unmodifiableList(Arrays.asList(
                UValue.uString("", 0.0),
                UValue.uString("", 1.0),
                UValue.uString(" ", 0.5),
                UValue.uString("a", 0.0),
                UValue.uString("a", 1.0),
                UValue.uString("abc", 0.5),
                UValue.uString("ABC", 0.5),
                UValue.uString("aBc", 0.75),
                UValue.uString("abc abc", 0.5),
                UValue.uString("\t", 0.5),
                UValue.uString("\n", 0.5),
                UValue.uString("\"quoted\"", 0.5),
                UValue.uString("back\\slash", 0.5),
                UValue.uString("é中", 0.5),
                // Parseable spellings. Without these the conversion operations are DEGENERATE:
                // measured at S4, toBoolean() produced ONE distinct reference value over the whole
                // corpus and toInteger()/toReal() threw on 20 of 22 rows, because not one string
                // here parsed as anything. Agreement on a single-point codomain is free and is not
                // evidence (D-15) -- the corpus, not the port, was the limit.
                UValue.uString("true", 1.0),
                UValue.uString("false", 1.0),
                UValue.uString("TRUE", 0.5),
                UValue.uString("0", 1.0),
                UValue.uString("42", 0.5),
                UValue.uString("-7", 0.5),
                UValue.uString("2147483647", 0.5),
                UValue.uString("3.14", 0.5),
                UValue.uString("-0.5", 0.5),
                UValue.uString("1e10", 0.5),
                UValue.uString("abc", Double.NaN),
                UValue.uString("abc", -1.0)));
    }

    /**
     * <strong>Plain {@code BooleanValue} boundaries: both truth values.</strong>
     *
     * <p>A two-element corpus is the whole domain of the type, so "boundary" and "exhaustive" are
     * the same list here.
     *
     * <p>This corpus is why it exists at all: {@code BooleanValue} has always been in
     * {@code HistoricalOracle.MARSHALLABLE_RECEIVERS}, so {@code supports()} answered {@code true}
     * for every {@code BooleanValue.*} operation and the sweep dutifully produced rows for all 27 of
     * them — and then failed the per-row receiver check on every single one, because not one value
     * in any shipped corpus was a {@code BOOLEAN}. All 27 operations were 100 % {@code HARNESS_ERROR}
     * and contributed <em>zero</em> measurements to a 471 471-row sweep, against a perfect port
     * (defect D-19). A receiver type the harness can marshal but never marshals is a coverage claim
     * the instrument makes and does not honour.
     */
    public static List<UValue> booleanBoundaries() {
        return Collections.unmodifiableList(Arrays.asList(
                UValue.bool(true),
                UValue.bool(false)));
    }

    /**
     * <strong>Plain {@code StringValue} boundaries.</strong> Same story as
     * {@link #booleanBoundaries()}: all 25 {@code StringValue.*} operations were 100 %
     * {@code HARNESS_ERROR} for want of a single {@code STRING} in any corpus.
     *
     * <p>Deliberately parallel to {@link #uStringBoundaries()} — empty, whitespace, mixed case,
     * multi-word, the characters that would break a naive TSV writer (tab, newline, quote,
     * backslash), non-ASCII — plus two the uncertain corpus does not carry:
     * <ul>
     *   <li>a <strong>very long</strong> string, 256 characters, which is where a length-dependent
     *       or buffer-dependent difference between two implementations would show;</li>
     *   <li>a <strong>supplementary-plane</strong> character (U+1F600, a surrogate pair), where
     *       {@code String.length()} and the number of code points disagree — the single most likely
     *       place for a re-implemented {@code size()}, {@code at()} or {@code substring()} to
     *       diverge from the original without anyone noticing.</li>
     * </ul>
     */
    public static List<UValue> stringBoundaries() {
        StringBuilder longString = new StringBuilder(256);
        while (longString.length() < 256) {
            longString.append("abcdefghij");
        }
        return Collections.unmodifiableList(Arrays.asList(
                UValue.string(""),
                UValue.string(" "),
                UValue.string("a"),
                UValue.string("abc"),
                UValue.string("ABC"),
                UValue.string("aBc"),
                UValue.string("abc abc"),
                UValue.string("\t"),
                UValue.string("\n"),
                UValue.string("\"quoted\""),
                UValue.string("back\\slash"),
                UValue.string("é中"),
                UValue.string("😀"),
                UValue.string(longString.substring(0, 256))));
    }

    /**
     * Divisors that are exactly zero, in every shape a divide-like operation can receive one.
     * These are the inputs that make {@code divideBy}, {@code divideByR}, {@code mod} and
     * {@code inverse} interesting.
     */
    public static List<UValue> zeroDivisors() {
        return Collections.unmodifiableList(Arrays.asList(
                UValue.uInteger(0, 0.0),
                UValue.uInteger(0, 1.0),
                UValue.uReal(0.0, 0.0),
                UValue.uReal(0.0, 1.0),
                UValue.uReal(-0.0, 0.0),
                UValue.integer(0),
                UValue.real(0.0)));
    }

    /**
     * Index arguments for {@code UStringValue.at(int)} / {@code uAt(int)} / {@code uSubstring(int,int)}.
     * Includes indices that are out of range for every string in {@link #uStringBoundaries()}.
     *
     * <p>Measured during S1: the historical {@code UStringValue.at} is 1-based — {@code at(0)} on
     * {@code "abc"} throws {@code IndexOutOfBoundsException: idx = 0} from
     * {@code uDataTypes.UString.at}. Index 0 is therefore a boundary, not a normal case.
     */
    public static List<UValue> indexBoundaries() {
        return Collections.unmodifiableList(Arrays.asList(
                UValue.integer(Integer.MIN_VALUE),
                UValue.integer(-1),
                UValue.integer(0),
                UValue.integer(1),
                UValue.integer(2),
                UValue.integer(3),
                UValue.integer(4),
                UValue.integer(Integer.MAX_VALUE)));
    }
}
