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
 * <p>JUnit 5 Jupiter — this reactor has no junit-vintage-engine, so JUnit 3/4 tests never execute.
 */
@DisplayName("Uncertainty differential smoke")
class UncertaintyDifferentialSmokeTest {

    /** Number of random values appended to the boundary corpus. Fixed, so row counts are stable. */
    private static final int RANDOM_DRAWS = 6;

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
            System.out.println("tally                " + result.summary());
            System.out.println("--- first 12 rows -------------------------------------------------");
            System.out.println(DiffRow.TSV_HEADER);
            result.rows().stream().limit(12).forEach(r -> System.out.println(r.toTsv()));

            Path report = DiffReportWriter.write("s1-smoke-ureal-add.tsv", result, oracle.loadedDigests());
            Path golden = DiffReportWriter.assertMatchesGolden(report, "s1-smoke-ureal-add.tsv");
            System.out.println("report               " + report);
            System.out.println("golden (matched)     " + golden);
            System.out.println("===================================================================");

            assertEquals(corpus.size() * corpus.size(), result.rowCount(),
                    "the sweep must visit the full cartesian product");
            assertTrue(Files.isReadable(report), "the report must have been written to " + report);
            assertEquals(List.of(), result.disagreements(),
                    "the faithful stub reproduces the measured historical formula, so every row must agree");
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
            System.out.println("tally                " + result.summary());
            System.out.println("--- first 5 disagreements -----------------------------------------");
            System.out.println(DiffRow.TSV_HEADER);
            disagreements.stream().limit(5).forEach(r -> System.out.println(r.toTsv()));

            Path report = DiffReportWriter.write("s1-smoke-ureal-minus-faulty.tsv", result,
                    oracle.loadedDigests());
            Path golden = DiffReportWriter.assertMatchesGolden(report, "s1-smoke-ureal-minus-faulty.tsv");
            System.out.println("report               " + report);
            System.out.println("golden (matched)     " + golden);
            System.out.println("===================================================================");

            assertFalse(disagreements.isEmpty(),
                    "an injected fault that was not reported means the harness cannot detect anything");
            assertTrue(result.count(DiffVerdict.DIFFER) > 0, "the fault must surface as DIFFER rows");
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
