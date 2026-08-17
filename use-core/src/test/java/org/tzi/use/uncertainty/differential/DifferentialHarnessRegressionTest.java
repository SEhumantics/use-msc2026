package org.tzi.use.uncertainty.differential;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression tests for the ways this harness was measured to score its own failures as agreement.
 *
 * <p>Every test here corresponds to a defect that was <em>reproduced</em>, not imagined. The
 * headline one is {@link #d1TypeMismatchIsNotAgreement()}: before the fix,
 * {@code sweepBinary(UOp.binary("URealValue","add"), uIntegerBoundaries(), uIntegerBoundaries())}
 * reported {@code 169 rows, AGREE_THROWN=169, disagreements 0} with neither side ever entering
 * {@code URealValue.add}.
 *
 * <p>JUnit 5 Jupiter only — this reactor has no {@code junit-vintage-engine}, so a JUnit 3/4 test
 * would compile, be committed, and never execute.
 */
@DisplayName("Differential harness regressions")
class DifferentialHarnessRegressionTest {

    // ------------------------------------------------------------------ F11(a) / F1

    @Test
    @DisplayName("a marshalling failure on both sides is NOT an agreement")
    void marshallingFailureOnBothSidesIsNotAgreement() {
        UOp op = UOp.binary("URealValue", "add");
        try (Candidate a = new MarshallingFailure("ref");
             Candidate b = new MarshallingFailure("sub")) {

            DifferentialSweep sweep = new DifferentialSweep(a, b, 1L);
            DifferentialSweep.Result result = sweep.sweepBinary(op,
                    List.of(UValue.uReal(1.0, 0.5)), List.of(UValue.uReal(2.0, 0.5)));

            assertEquals(1, result.rowCount());
            DiffRow row = result.rows().get(0);
            assertEquals(DiffVerdict.HARNESS_ERROR, row.verdict(),
                    "two identical harness failures must not collapse into AGREE_THROWN");
            assertFalse(row.verdict().isAgreement(),
                    "HARNESS_ERROR.isAgreement() must be false, or the whole fix is cosmetic");
            assertEquals(1, result.disagreements().size(),
                    "a row the harness could not measure must appear in disagreements()");
            assertEquals(0, result.count(DiffVerdict.AGREE_THROWN));
            assertTrue(row.historical().startsWith("HARNESS_ERROR:"),
                    "the column itself must distinguish a harness failure from a THROWN: " + row.toTsv());
            assertTrue(row.ported().startsWith("HARNESS_ERROR:"), row.toTsv());
        }
    }

    @Test
    @DisplayName("a harness failure on one side alone is also not an agreement")
    void marshallingFailureOnOneSideIsNotAgreement() {
        UOp op = UOp.binary("URealValue", "add");
        try (Candidate broken = new MarshallingFailure("ref");
             StubCandidate stub = StubCandidate.faithful()) {

            DifferentialSweep.Result result = new DifferentialSweep(broken, stub, 1L)
                    .sweepBinary(op, List.of(UValue.uReal(1.0, 0.5)), List.of(UValue.uReal(2.0, 0.5)));

            assertEquals(DiffVerdict.HARNESS_ERROR, result.rows().get(0).verdict());
            assertEquals(1, result.disagreements().size());
        }
    }

    // ------------------------------------------------------------------ D1, the reproduced defect

    @Test
    @DisplayName("D1: URealValue.add over UInteger inputs is flagged, not scored as agreement")
    void d1TypeMismatchIsNotAgreement() {
        try (HistoricalOracle oracle = HistoricalOracle.open();
             StubCandidate stub = StubCandidate.faithful()) {

            DifferentialSweep.Result result = new DifferentialSweep(oracle, stub, 20260817L)
                    .sweepBinary(UOp.binary("URealValue", "add"),
                            InputGenerator.uIntegerBoundaries(), InputGenerator.uIntegerBoundaries());

            System.out.println("=== D1 reproduction ===============================================");
            System.out.println("tally                " + result.summary());
            System.out.println("disagreements        " + result.disagreements().size());
            System.out.println("row 0                " + result.rows().get(0).toTsv());
            System.out.println("===================================================================");

            assertEquals(169, result.rowCount(), "13 x 13 UInteger boundaries");
            assertEquals(0, result.count(DiffVerdict.AGREE_THROWN),
                    "not one of these rows entered URealValue.add, so none may read as agreement");
            assertEquals(169, result.count(DiffVerdict.HARNESS_ERROR));
            assertEquals(169, result.disagreements().size(),
                    "every row the harness could not measure must be visible as a non-agreement");
        }
    }

    // ------------------------------------------------------------------ F11(b) / F3

    @Test
    @DisplayName("a zero-row sweep is refused by the report writer")
    void zeroRowSweepIsRefused() {
        try (StubCandidate a = StubCandidate.faithful();
             StubCandidate b = StubCandidate.faithful()) {

            // An empty domain makes the cartesian product empty, so the sweep yields no rows at all
            // -- and reports no disagreements, which is exactly why it must not be writable.
            DifferentialSweep.Result empty = new DifferentialSweep(a, b, 1L)
                    .sweepBinary(UOp.binary("URealValue", "add"),
                            List.of(), List.of(UValue.uReal(1.0, 0.0)));

            assertEquals(0, empty.rowCount());
            assertEquals(List.of(), empty.disagreements(),
                    "a zero-row sweep looks clean, which is the trap");

            IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                    () -> DiffReportWriter.writeAll("must-not-be-written.tsv", List.of(empty), Map.of()));
            assertTrue(e.getMessage().contains("0 rows"), e.getMessage());

            // The old guard tested results.isEmpty(), so a non-empty list of empty Results slipped
            // through. Pin that specific shape.
            assertThrows(IllegalArgumentException.class,
                    () -> DiffReportWriter.writeAll("must-not-be-written.tsv", List.of(empty, empty),
                            Map.of()));
            assertFalse(java.nio.file.Files.exists(
                            DiffReportWriter.reportDir().resolve("must-not-be-written.tsv")),
                    "the refused report must not have been created");
        }
    }

    // ------------------------------------------------------------------ F2 / F5

    @Test
    @DisplayName("a receiver type the harness cannot marshal reports UNSUPPORTED, not agreement")
    void unmarshallableReceiverTypeIsUnsupported() {
        try (HistoricalOracle oracle = HistoricalOracle.open()) {
            assertFalse(oracle.supports(UOp.binary("SBooleanValue", "and")),
                    "SBooleanValue.and exists on the historical class, but the harness has no "
                            + "SBoolean marshalling, so it must not claim support");
            assertFalse(oracle.supports(UOp.unary("SBooleanValue", "not")));
            assertTrue(oracle.supports(UOp.binary("URealValue", "add")),
                    "a receiver type the harness does marshal must still be supported");

            try (StubCandidate stub = StubCandidate.faithful()) {
                DifferentialSweep.Result result = new DifferentialSweep(oracle, stub, 1L)
                        .sweepBinary(UOp.binary("SBooleanValue", "and"),
                                List.of(UValue.uBoolean(true, 0.5)), List.of(UValue.uBoolean(false, 0.5)));
                assertEquals(DiffVerdict.UNSUPPORTED, result.rows().get(0).verdict());
                assertEquals(1, result.disagreements().size());
            }
        }
    }

    @Test
    @DisplayName("supports() converts a missing method, and only that, into false")
    void supportsSwallowsOnlyAMissingMethod() {
        try (HistoricalOracle oracle = HistoricalOracle.open()) {
            assertFalse(oracle.supports(UOp.unary("URealValue", "noSuchOperationExists")),
                    "a genuinely absent method is the one case supports() may report as false");

            // The type that used to be swallowed. It is a RuntimeException -- which is why the old
            // catch(RuntimeException) turned "the oracle jar is broken" into "not implemented" -- and
            // it is not the type the narrowed catch names, so it can no longer be absorbed here.
            assertTrue(RuntimeException.class.isAssignableFrom(
                            HistoricalOracle.HistoricalOracleUnavailableException.class),
                    "sanity: it really was inside the old catch");
            assertFalse(HistoricalOracle.NoSuchHistoricalMethodException.class.isAssignableFrom(
                            HistoricalOracle.HistoricalOracleUnavailableException.class),
                    "a broken oracle must not be reportable as an unimplemented operation");
        }
    }

    // ------------------------------------------------------------------ F7

    @Test
    @DisplayName("a candidate returning Java null is recorded as a throw, not an NPE mid-sweep")
    void candidateReturningNullIsRecorded() {
        UOp op = UOp.binary("URealValue", "add");
        List<UValue> domain = List.of(UValue.uReal(1.0, 0.5), UValue.uReal(2.0, 0.5));
        try (Candidate nullish = new ReturnsNull();
             StubCandidate stub = StubCandidate.faithful()) {

            DifferentialSweep.Result result =
                    new DifferentialSweep(stub, nullish, 1L).sweepBinary(op, domain, domain);

            assertEquals(4, result.rowCount(), "every row must survive; the NPE used to discard them all");
            assertEquals(4, result.count(DiffVerdict.MIXED));
            assertTrue(result.rows().get(0).ported().contains("NullPointerException"),
                    result.rows().get(0).toTsv());
        }
    }

    // ------------------------------------------------------------------ F8

    @Test
    @DisplayName("an Error from a candidate propagates instead of becoming comparable data")
    void errorsAreNotComparableData() {
        UOp op = UOp.binary("URealValue", "add");
        List<UValue> domain = List.of(UValue.uReal(1.0, 0.5));
        try (Candidate boom = new ThrowsError();
             StubCandidate stub = StubCandidate.faithful()) {

            DifferentialSweep sweep = new DifferentialSweep(stub, boom, 1L);
            assertThrows(StackOverflowError.class, () -> sweep.sweepBinary(op, domain, domain),
                    "a StackOverflowError describes a broken run, not a behavioural difference");
        }
    }

    // ------------------------------------------------------------------ F6

    @Test
    @DisplayName("a void historical operation unwraps to VOID, never to NULL")
    void voidIsDistinctFromNull() throws Throwable {
        try (HistoricalOracle oracle = HistoricalOracle.open()) {
            // Value.setTypeToRuntimeType() is public and void, and is inherited by URealValue: the
            // exact shape of the "empty-bodied mutator agrees forever" defect.
            UOp mutator = UOp.unary("URealValue", "setTypeToRuntimeType");
            assertTrue(oracle.supports(mutator));
            UValue produced = oracle.invoke(mutator, List.of(UValue.uReal(1.0, 0.25)));

            assertEquals(UValue.Kind.VOID, produced.kind(),
                    "Method.invoke returns null for void; mapping that to NULL makes every void "
                            + "operation agree with every other one");
            assertEquals("VOID", produced.canonical());
            assertNotEquals(UValue.nullValue(), produced);
            assertNotEquals(UValue.nullValue().canonical(), produced.canonical());
        }
    }

    // ------------------------------------------------------------------ F4

    @Test
    @DisplayName("the OPAQUE representation is exact and locale-independent")
    void opaqueRepresentationIsExactAndLocaleIndependent() throws Exception {
        try (HistoricalOracle oracle = HistoricalOracle.open()) {
            // A value whose decimal expansion no fixed-width format can carry.
            Object historical = oracle.toHistorical(UValue.uReal(1.0 / 3.0, 2.0 / 3.0));
            // An object that really does reach the OPAQUE branch: SBooleanValue is not modelled by
            // UValue, so fromHistorical falls through to it.
            Object sbooleanTrue = oracle.historicalClass("SBooleanValue").getField("TRUE").get(null);

            String viaToString = String.valueOf(historical);
            String exact = oracle.opaqueRepresentation(historical);
            UValue opaque = oracle.fromHistorical(sbooleanTrue);

            System.out.println("=== OPAQUE representation =========================================");
            System.out.println("foreign toString()   " + viaToString);
            System.out.println("field-derived        " + exact);
            System.out.println("SBooleanValue.TRUE   toString  " + sbooleanTrue);
            System.out.println("SBooleanValue.TRUE   canonical " + opaque.canonical());
            System.out.println("===================================================================");

            assertTrue(exact.contains(Double.toString(1.0 / 3.0)),
                    "the exact value must survive: " + exact);
            assertTrue(exact.contains(Double.toString(2.0 / 3.0)), exact);
            assertFalse(viaToString.contains(Double.toString(1.0 / 3.0)),
                    "sanity: the foreign toString() rounds, which is what this replaces, got "
                            + viaToString);

            assertEquals(UValue.Kind.OPAQUE, opaque.kind());
            assertFalse(opaque.canonical().contains(String.valueOf(sbooleanTrue)),
                    "the OPAQUE canonical form must no longer embed the foreign toString(): "
                            + opaque.canonical());
            assertTrue(opaque.canonical().contains("uDataTypes.SBoolean"),
                    "it must be built from the declared fields instead: " + opaque.canonical());

            Locale original = Locale.getDefault();
            try {
                Locale.setDefault(Locale.GERMANY);
                assertEquals(exact, oracle.opaqueRepresentation(historical),
                        "the representation must not move with the default locale");
                assertEquals(opaque.canonical(), oracle.fromHistorical(sbooleanTrue).canonical(),
                        "nor may the canonical form of an OPAQUE result");
                // No decimal comma anywhere: commas separate fields, never digits.
                assertFalse(exact.matches("(?s).*\\d,\\d.*"),
                        "a digit-comma-digit sequence is a European decimal comma: " + exact);
                assertFalse(oracle.opaqueRepresentation(sbooleanTrue).matches("(?s).*\\d,\\d.*"),
                        oracle.opaqueRepresentation(sbooleanTrue));
            } finally {
                Locale.setDefault(original);
            }
        }
    }

    @Test
    @DisplayName("an object the harness cannot represent exactly is refused, not approximated")
    void unrepresentableObjectIsRefused() {
        try (HistoricalOracle oracle = HistoricalOracle.open()) {
            assertThrows(HarnessMarshallingException.class,
                    () -> oracle.opaqueRepresentation(new java.util.HashSet<>(List.of("a", "b"))),
                    "a type with no stable ordering must not be rendered by guesswork");
        }
    }

    // ------------------------------------------------------------------ fakes

    /** A candidate that fails the way the harness itself fails. */
    private static final class MarshallingFailure implements Candidate {
        private final String name;

        MarshallingFailure(String name) {
            this.name = name;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public UValue invoke(UOp op, List<UValue> args) {
            throw new HarnessMarshallingException("cannot marshal " + args.get(0).canonical()
                    + " for " + op.key());
        }

        @Override
        public boolean supports(UOp op) {
            return true;
        }

        @Override
        public void close() {
        }
    }

    /** The natural mistake for a ported operation whose result maps to {@code UndefinedValue}. */
    private static final class ReturnsNull implements Candidate {
        @Override
        public String name() {
            return "returns-null";
        }

        @Override
        public UValue invoke(UOp op, List<UValue> args) {
            return null;
        }

        @Override
        public boolean supports(UOp op) {
            return true;
        }

        @Override
        public void close() {
        }
    }

    private static final class ThrowsError implements Candidate {
        @Override
        public String name() {
            return "throws-error";
        }

        @Override
        public UValue invoke(UOp op, List<UValue> args) {
            throw new StackOverflowError("simulated");
        }

        @Override
        public boolean supports(UOp op) {
            return true;
        }

        @Override
        public void close() {
        }
    }
}
