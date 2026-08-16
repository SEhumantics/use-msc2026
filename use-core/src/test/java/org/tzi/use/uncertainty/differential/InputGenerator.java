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
 *   <li>empty string — {@link #uStringBoundaries()}</li>
 *   <li>out-of-range index — {@link #indexBoundaries()}</li>
 *   <li>NaN and infinities — {@link #uRealBoundaries()} (reachable: the historical
 *       {@code URealValue(double,double)} constructor takes raw doubles)</li>
 * </ul>
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

    private static double round6(double d) {
        if (!Double.isFinite(d)) {
            return d;
        }
        return Math.round(d * 1_000_000.0) / 1_000_000.0;
    }

    // ------------------------------------------------------------------ corpora

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
                UValue.uString("abc", Double.NaN),
                UValue.uString("abc", -1.0)));
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
