package org.tzi.use.uncertainty.differential;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end smoke test for the differential harness: historical oracle versus a stub, over a
 * seeded input corpus, written out as TSV.
 *
 * <p>Two directions are exercised on purpose.
 * <ul>
 *   <li>{@code URealValue.add} against {@link StubCandidate#faithful()} must be <em>all</em>
 *       agreement. That shows the plumbing works.</li>
 *   <li>{@code URealValue.minus} against {@link StubCandidate#faultyMinus()} must contain
 *       disagreements. That shows the harness is capable of saying no. A harness that has only ever
 *       printed green is not evidence of anything.</li>
 * </ul>
 *
 * <h2>This test gates the way S4 must gate (defect D-36)</h2>
 * It used to assert {@link DifferentialSweep.Result#isClean()}, whose own Javadoc says it is not a
 * pass predicate — {@code isClean()} is {@code true} for 119 of 285 operations against a subject
 * consisting of one hardcoded literal each. The tree's own S1 acceptance test asserting a predicate
 * the contract forbids a stage from asserting is worse than a documentation defect: it is the worked
 * example S4 would copy.
 *
 * <p>So the positive direction now goes through
 * {@link DifferentialSweep.Result#requireStagePass(int, AcceptedDegenerateOperations)} with
 * {@link #ADD_FLOOR} — a floor derived from the corpus and written down here, above the run, rather
 * than read off it — plus the two checks the gate deliberately does not make: the byte-identical
 * golden comparison, and {@code throwClassMismatchCount() == 0}. That is exactly the shape of
 * {@code harness-contract.md} §4.3's worked stage gate. {@code isClean()} is still <em>measured</em>
 * below, and printed beside the gate's verdict, because the gap between the two is the number D-36
 * is about; it is no longer what the test passes on.
 *
 * <p>Two things this test still is not: it is not a fidelity claim about a port ({@link StubCandidate}
 * is three hand-written formulas, not a port), and it does not state its input domain in prose, which
 * D-30 requires of a stage. The domain is one sentence and it is here: 28 {@code UReal} receivers ×
 * the same 28 as arguments, being {@link InputGenerator#uRealBoundaries()}'s 22 boundary values —
 * 0, ±0.0, ±1, ±0.5, 2, ±100, {@code MIN_VALUE}, {@code MAX_VALUE}, NaN, ±Infinity, paired with
 * uncertainties 0, 1, 0.5, 0.25, 0.001 and NaN — plus 6 seeded random draws rounded to six decimals
 * in [-100, 100]. No value in (2, 100) other than those draws; no denormal other than
 * {@code MIN_VALUE}; no receiver at 42.
 *
 * <p>JUnit 5 Jupiter — this reactor has no junit-vintage-engine, so JUnit 3/4 tests never execute.
 */
@DisplayName("Uncertainty differential smoke")
class UncertaintyDifferentialSmokeTest {

    /** Number of random values appended to the boundary corpus. Fixed, so row counts are stable. */
    private static final int RANDOM_DRAWS = 6;

    /**
     * The measurement floor for {@code URealValue.add(value)}, <strong>written here before the
     * run</strong> as clause 1 of the stage gate requires. Derived from the corpus and not from the
     * output: {@link InputGenerator#uRealBoundaries()} holds 22 values, {@link #RANDOM_DRAWS} adds 6,
     * and every cell of the 28 × 28 product is marshallable and returns on both sides, so 784 rows
     * must all be measurements. A floor chosen after seeing the run is not a floor, and a floor of
     * "1" would be satisfied by a sweep that compared one row.
     */
    private static final int ADD_FLOOR = 784;

    @Test
    @DisplayName("historical URealValue.add agrees with the faithful stub on every input")
    void smokeURealAdd() {
        InputGenerator generator = new InputGenerator(InputGenerator.DEFAULT_SEED);
        List<UValue> corpus = generator.uRealCorpus(RANDOM_DRAWS);

        try (HistoricalOracle oracle = HistoricalOracle.open();
             StubCandidate stub = StubCandidate.faithful()) {

            System.out.println("=== S1 differential smoke =========================================");
            System.out.println("seed                 " + generator.seed());
            System.out.println("reference            " + oracle.name() + "  " + oracle.useJarPath());
            System.out.println("subject              " + stub.name());
            oracle.loadedDigests().forEach((k, v) -> System.out.println("sha256 " + k + "  " + v));
            System.out.println("corpus size          " + corpus.size()
                    + "  (" + InputGenerator.uRealBoundaries().size() + " boundary + "
                    + RANDOM_DRAWS + " random)");

            DifferentialSweep sweep = new DifferentialSweep(oracle, stub, generator.seed());
            UOp add = UOp.binary("URealValue", "add");
            DifferentialSweep.Result result = sweep.sweepBinary(add, corpus, corpus);

            System.out.println("rows                 " + result.rowCount());
            System.out.println("measured             " + result.measurementCount());
            System.out.println("tally                " + result.summary());
            System.out.println("--- first 12 rows -------------------------------------------------");
            System.out.println(DiffRow.TSV_HEADER);
            result.rows().stream().limit(12).forEach(r -> System.out.println(r.toTsv()));

            // No degenerate operation is signed off here and none needs to be: add(value) is
            // expected to be richly discriminating over this corpus. Named explicitly because
            // DiffReportWriter has no overload that would let this file understate it (D-34).
            AcceptedDegenerateOperations acknowledged = AcceptedDegenerateOperations.none();

            Path report = DiffReportWriter.write("s1-smoke-ureal-add.tsv", result,
                    oracle.loadedDigests(), acknowledged);
            Path golden = DiffReportWriter.assertMatchesGolden(report, "s1-smoke-ureal-add.tsv");
            System.out.println("report               " + report);
            System.out.println("golden (matched)     " + golden);
            System.out.println("isClean()            " + result.isClean()
                    + "   <- measured, NOT the pass criterion (D-36)");
            System.out.println("stage gate failures  "
                    + result.stageGateFailures(ADD_FLOOR, acknowledged));
            System.out.println("STAGE STATEMENT      " + result.stageStatement(acknowledged));
            System.out.println("===================================================================");

            assertEquals(corpus.size() * corpus.size(), result.rowCount(),
                    "the sweep must visit the full cartesian product");
            assertTrue(Files.isReadable(report), "the report must have been written to " + report);

            // ---- THE GATE. Not isClean(): see the class comment and D-36. -----------------------
            // Clauses 1-3 in one call, with the floor written down above the run, and it throws with
            // every failing clause rather than returning a bare false.
            result.requireStagePass(ADD_FLOOR, acknowledged);
            // Clause 5, which the gate deliberately does not make: a port that fails on the right
            // rows with the wrong exception class leaves every other aggregate identical.
            assertEquals(0, result.throwClassMismatchCount(), result.summary());
            // And the discrimination figure itself, so this file states the number the gate acted on.
            assertTrue(result.isDiscriminating(),
                    "a stage pass on a single-valued operation would be D-15: " + result.summary());

            assertEquals(result.rowCount(), result.measurementCount(),
                    "every row of this sweep is a genuine comparison of two observed values");
            assertEquals(result.rowCount(), result.count(DiffVerdict.AGREE));
        }
    }

    @Test
    @DisplayName("the harness reports disagreement when the subject is wrong")
    void smokeDetectsAWrongSubject() {
        InputGenerator generator = new InputGenerator(InputGenerator.DEFAULT_SEED);
        List<UValue> corpus = generator.uRealCorpus(RANDOM_DRAWS);

        try (HistoricalOracle oracle = HistoricalOracle.open();
             StubCandidate stub = StubCandidate.faultyMinus()) {

            DifferentialSweep sweep = new DifferentialSweep(oracle, stub, generator.seed());
            DifferentialSweep.Result result =
                    sweep.sweepBinary(UOp.binary("URealValue", "minus"), corpus, corpus);

            List<DiffRow> disagreements = result.disagreements();
            System.out.println("=== S1 fault-injection check ======================================");
            System.out.println("seed                 " + generator.seed());
            System.out.println("subject              " + stub.name() + "  (minus uses |ua-ub|)");
            System.out.println("rows                 " + result.rowCount());
            System.out.println("measured             " + result.measurementCount());
            System.out.println("tally                " + result.summary());
            System.out.println("--- first 5 disagreements -----------------------------------------");
            System.out.println(DiffRow.TSV_HEADER);
            disagreements.stream().limit(5).forEach(r -> System.out.println(r.toTsv()));

            AcceptedDegenerateOperations acknowledged = AcceptedDegenerateOperations.none();
            Path report = DiffReportWriter.write("s1-smoke-ureal-minus-faulty.tsv", result,
                    oracle.loadedDigests(), acknowledged);
            Path golden = DiffReportWriter.assertMatchesGolden(report, "s1-smoke-ureal-minus-faulty.tsv");
            System.out.println("report               " + report);
            System.out.println("golden (matched)     " + golden);
            System.out.println("===================================================================");

            assertFalse(disagreements.isEmpty(),
                    "an injected fault that was not reported means the harness cannot detect anything");
            assertTrue(result.count(DiffVerdict.DIFFER) > 0, "the fault must surface as DIFFER rows");
            // The gate, in the negative direction, and by the same predicate the positive test uses.
            // requireStagePass throws with every failing clause; the clause that must be in that list
            // is the one about rows disagreeing, not the measurement floor.
            IllegalStateException refusal = org.junit.jupiter.api.Assertions.assertThrows(
                    IllegalStateException.class,
                    () -> result.requireStagePass(ADD_FLOOR, acknowledged),
                    "a sweep carrying 226 diverging rows must not be a stage pass");
            assertTrue(refusal.getMessage().contains("did not agree"), refusal.getMessage());
            System.out.println("refused              " + refusal.getMessage()
                    .replace('\n', ' ').replaceAll(" +", " "));
            assertEquals(result.rowCount(), result.measurementCount(),
                    "every row here is a comparison; the fault is in what was compared, not in "
                            + "whether anything was");
        }
    }

    @Test
    @DisplayName("the seeded generator is replayable")
    void seededGenerationIsReplayable() {
        List<UValue> first = new InputGenerator(InputGenerator.DEFAULT_SEED).uRealCorpus(50);
        List<UValue> second = new InputGenerator(InputGenerator.DEFAULT_SEED).uRealCorpus(50);
        assertEquals(first, second, "two generators with the same seed must produce identical corpora");

        List<UValue> other = new InputGenerator(InputGenerator.DEFAULT_SEED + 1).uRealCorpus(50);
        assertFalse(first.equals(other),
                "a different seed must produce a different corpus, otherwise the seed does nothing");
    }

    @Test
    @DisplayName("boundary corpora carry the inputs the later stages need")
    void boundaryCoverage() {
        assertTrue(InputGenerator.uRealBoundaries().contains(UValue.uReal(0.0, 0.0)), "zero value");
        assertTrue(InputGenerator.uRealBoundaries().contains(UValue.uReal(-1.0, 1.0)), "negative value");
        assertTrue(InputGenerator.uRealBoundaries().contains(UValue.uReal(1.0, 0.0)), "uncertainty 0");
        assertTrue(InputGenerator.uRealBoundaries().contains(UValue.uReal(1.0, 1.0)), "uncertainty 1");
        assertTrue(InputGenerator.uRealBoundaries().contains(UValue.uReal(Double.NaN, 0.0)), "NaN");
        assertTrue(InputGenerator.uRealBoundaries().contains(UValue.uReal(Double.POSITIVE_INFINITY, 0.0)),
                "positive infinity");
        assertTrue(InputGenerator.uRealBoundaries().contains(UValue.uReal(Double.NEGATIVE_INFINITY, 0.0)),
                "negative infinity");

        assertTrue(InputGenerator.uStringBoundaries().contains(UValue.uString("", 0.0)), "empty string");
        assertTrue(InputGenerator.uStringBoundaries().contains(UValue.uString("", 1.0)), "confidence 1");

        assertTrue(InputGenerator.uBooleanBoundaries().contains(UValue.uBoolean(true, 0.0)), "probability 0");
        assertTrue(InputGenerator.uBooleanBoundaries().contains(UValue.uBoolean(true, 1.0)), "probability 1");

        assertTrue(InputGenerator.zeroDivisors().contains(UValue.uInteger(0, 0.0)), "zero UInteger divisor");
        assertTrue(InputGenerator.zeroDivisors().contains(UValue.uReal(0.0, 0.0)), "zero UReal divisor");

        assertTrue(InputGenerator.indexBoundaries().contains(UValue.integer(0)), "index 0");
        assertTrue(InputGenerator.indexBoundaries().contains(UValue.integer(-1)), "negative index");
        assertTrue(InputGenerator.indexBoundaries().contains(UValue.integer(Integer.MAX_VALUE)),
                "far out-of-range index");
    }

    @Test
    @DisplayName("the oracle can construct and unwrap every U-type without leaking reflective types")
    void allFourUTypesRoundTrip() {
        try (HistoricalOracle oracle = HistoricalOracle.open()) {
            UValue real = oracle.call("URealValue", "add",
                    UValue.uReal(1.5, 0.25), UValue.uReal(2.5, 0.5));
            assertEquals(UValue.Kind.UREAL, real.kind());
            assertEquals(4.0, real.asDouble());
            assertEquals(Math.sqrt(0.25 * 0.25 + 0.5 * 0.5), real.uncertainty());

            UValue integer = oracle.call("UIntegerValue", "add",
                    UValue.uInteger(3, 0.5), UValue.uInteger(4, 0.5));
            assertEquals(UValue.Kind.UINTEGER, integer.kind());
            assertEquals(7, integer.asInt());

            UValue bool = oracle.call("UBooleanValue", "and",
                    UValue.uBoolean(true, 0.8), UValue.uBoolean(false, 0.3));
            assertEquals(UValue.Kind.UBOOLEAN, bool.kind());

            UValue size = oracle.call("UStringValue", "uSize", UValue.uString("abc", 0.3));
            assertEquals(UValue.Kind.UINTEGER, size.kind());
            assertEquals(3, size.asInt());

            // A non-U result type must also come back as plain Java.
            UValue plain = oracle.call("UStringValue", "uToString", UValue.uString("abc", 0.3));
            assertEquals(UValue.Kind.STRING, plain.kind());
            assertEquals("abc", plain.asString());
        }
    }

    @Test
    @DisplayName("a throwing historical operation is recorded, not swallowed")
    void thrownOutcomesAreRecorded() throws Throwable {
        try (HistoricalOracle oracle = HistoricalOracle.open()) {
            UOp at = UOp.of("UStringValue", "at", UOp.ParamKind.INT);
            // Measured at S1: the historical UStringValue.at is 1-based, so index 0 throws.
            Throwable thrown = org.junit.jupiter.api.Assertions.assertThrows(
                    IndexOutOfBoundsException.class,
                    () -> oracle.invoke(at, List.of(UValue.uString("abc", 0.5), UValue.integer(0))));
            assertTrue(thrown.getMessage().contains("idx = 0"),
                    "expected the historical uDataTypes message, got: " + thrown.getMessage());

            UValue ok = oracle.invoke(at, List.of(UValue.uString("abc", 0.5), UValue.integer(1)));
            assertEquals(UValue.Kind.STRING, ok.kind());
            assertEquals("a", ok.asString());
        }
    }
}
