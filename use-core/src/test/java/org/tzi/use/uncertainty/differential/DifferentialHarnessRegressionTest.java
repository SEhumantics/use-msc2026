package org.tzi.use.uncertainty.differential;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
                    "two identical harness failures must not collapse into a throw-agreement");
            assertFalse(row.verdict().isAgreement(),
                    "HARNESS_ERROR.isAgreement() must be false, or the whole fix is cosmetic");
            assertEquals(1, result.disagreements().size(),
                    "a row the harness could not measure must appear in disagreements()");
            assertEquals(0, result.agreementCount());
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
            assertEquals(0, result.agreementCount(),
                    "not one of these rows entered URealValue.add, so none may read as agreement");
            assertEquals(0, result.count(DiffVerdict.BOTH_THREW));
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
                assertEquals(0, result.agreementCount());
            }
        }
    }

    // ------------------------------------------------------------------ D-3

    @Test
    @DisplayName("D-3: the UNSUPPORTED note states what is true, not 'does not implement'")
    void unsupportedNoteIsNotAFalseStatement() throws Exception {
        UOp and = UOp.binary("SBooleanValue", "and");
        try (HistoricalOracle oracle = HistoricalOracle.open();
             StubCandidate stub = StubCandidate.faithful()) {

            // The historical class really does declare it. If this ever stops being true the note
            // below stops being the correct note, so it is asserted rather than assumed.
            assertNotNull(oracle.historicalClass("SBooleanValue")
                            .getMethod("and", oracle.historicalClass("Value")),
                    "sanity: the historical SBooleanValue declares and(Value)");

            DiffRow row = new DifferentialSweep(oracle, stub, 1L)
                    .sweepBinary(and, List.of(UValue.uBoolean(true, 0.5)),
                            List.of(UValue.uBoolean(false, 0.5)))
                    .rows().get(0);

            System.out.println("=== D-3: UNSUPPORTED note =========================================");
            System.out.println(row.toTsv());
            System.out.println("===================================================================");

            assertFalse(row.note().contains("historical does not implement"),
                    "the harness wrote a demonstrably false statement into its own evidence file: "
                            + row.note());
            assertTrue(row.note().contains("cannot marshal a SBooleanValue receiver"), row.note());
            assertTrue(row.note().contains("limit of the instrument"), row.note());

            // The other reason must still be reported as the other reason.
            assertTrue(oracle.unsupportedReason(UOp.unary("URealValue", "noSuchOperationExists"))
                            .contains("declares no method matching"),
                    oracle.unsupportedReason(UOp.unary("URealValue", "noSuchOperationExists")));
        }
    }

    /**
     * The previous version of this test asserted only that a missing method yields {@code false} and
     * that two exception classes are unrelated in the type hierarchy. Both statements hold verbatim
     * on the pre-fix {@code catch (RuntimeException e) { return false; }}, so it pinned nothing.
     *
     * <p>What it has to exercise is a failure that is <em>not</em> a missing method reaching
     * {@code supports()}. A closed oracle is that failure, and it is the reachable one: with the old
     * catch, {@code supports()} answered "the historical implementation does not have this
     * operation" — a claim about the code under test — after the instrument had been shut down.
     */
    @Test
    @DisplayName("D-4: supports() converts a missing method, and ONLY that, into false")
    void supportsSwallowsOnlyAMissingMethod() {
        UOp add = UOp.binary("URealValue", "add");
        UOp absent = UOp.unary("URealValue", "noSuchOperationExists");
        HistoricalOracle oracle = HistoricalOracle.open();
        try {
            assertFalse(oracle.supports(absent),
                    "a genuinely absent method is the one case supports() may report as false");
            assertTrue(oracle.supports(add));
        } finally {
            oracle.close();
        }

        // Now the instrument is broken. Answering the question at all would be a lie, in either
        // direction: "true" promises a drive that will fail, "false" blames the historical code.
        HarnessMarshallingException e =
                assertThrows(HarnessMarshallingException.class, () -> oracle.supports(add),
                        "a closed oracle must refuse to answer supports(), not report the operation "
                                + "as unimplemented");
        assertTrue(e.getMessage().contains("already been closed"), e.getMessage());
        assertThrows(HarnessMarshallingException.class, () -> oracle.supports(absent),
                "and it must refuse for an absent method too -- the oracle being shut is the "
                        + "stronger fact");

        // Sanity on the shape of the old defect: the swallowed type really is a RuntimeException,
        // so a catch(RuntimeException) would have absorbed it and returned false.
        assertTrue(RuntimeException.class.isAssignableFrom(HarnessMarshallingException.class));
        assertFalse(HistoricalOracle.NoSuchHistoricalMethodException.class
                        .isAssignableFrom(HarnessMarshallingException.class),
                "a broken oracle must not be reportable as an unimplemented operation");
    }

    // ------------------------------------------------------------------ F7

    @Test
    @DisplayName("a candidate returning Java null is recorded, not an NPE mid-sweep")
    void candidateReturningNullIsRecorded() {
        UOp op = UOp.binary("URealValue", "add");
        List<UValue> domain = List.of(UValue.uReal(1.0, 0.5), UValue.uReal(2.0, 0.5));
        try (Candidate nullish = new ReturnsNull();
             StubCandidate stub = StubCandidate.faithful()) {

            DifferentialSweep.Result result =
                    new DifferentialSweep(stub, nullish, 1L).sweepBinary(op, domain, domain);

            assertEquals(4, result.rowCount(), "every row must survive; the NPE used to discard them all");
            assertEquals(4, result.count(DiffVerdict.HARNESS_ERROR));
            assertEquals(0, result.agreementCount());
            assertTrue(result.rows().get(0).ported().startsWith("HARNESS_ERROR:"),
                    result.rows().get(0).toTsv());
            assertTrue(result.rows().get(0).note().contains("returned Java null"),
                    result.rows().get(0).toTsv());
        }
    }

    // ------------------------------------------------------------------ D-1

    @Test
    @DisplayName("D-1: two candidates that both return Java null are NOT in agreement")
    void twoNullReturnsAreNotAgreement() {
        UOp op = UOp.binary("URealValue", "add");
        List<UValue> domain = List.of(UValue.uReal(1.0, 0.5), UValue.uReal(2.0, 0.5));
        try (Candidate a = new ReturnsNull(); Candidate b = new ReturnsNull()) {

            DifferentialSweep.Result result =
                    new DifferentialSweep(a, b, 1L).sweepBinary(op, domain, domain);

            System.out.println("=== D-1 reproduction (null vs null) ===============================");
            System.out.println("tally                " + result.summary());
            System.out.println("row 0                " + result.rows().get(0).toTsv());
            System.out.println("===================================================================");

            // Both sides used to raise NullPointerException, which matched on class name and scored
            // AGREE_THROWN: a contract violation on both sides, reported as agreement.
            assertEquals(4, result.rowCount());
            assertEquals(0, result.agreementCount(),
                    "a Candidate contract violation is the absence of a value, not a shared value");
            assertEquals(4, result.count(DiffVerdict.HARNESS_ERROR));
            assertEquals(4, result.disagreements().size());
        }
    }

    // ------------------------------------------------------------------ D-2

    @Test
    @DisplayName("D-2: the original D1 tally does not reproduce with two stubs either")
    void twoStubsOverAnUnmarshallableReceiverAreNotAgreement() {
        try (StubCandidate a = StubCandidate.faithful();
             StubCandidate b = StubCandidate.faithful()) {

            // StubCandidate.supports() is true for URealValue.add, and every one of these 169
            // receivers is a UINTEGER, so both stubs fail in their own marshalling on every row.
            DifferentialSweep.Result result = new DifferentialSweep(a, b, 1L)
                    .sweepBinary(UOp.binary("URealValue", "add"),
                            InputGenerator.uIntegerBoundaries(), InputGenerator.uIntegerBoundaries());

            System.out.println("=== D-2 reproduction (two stubs) ==================================");
            System.out.println("tally                " + result.summary());
            System.out.println("disagreements        " + result.disagreements().size());
            System.out.println("row 0                " + result.rows().get(0).toTsv());
            System.out.println("===================================================================");

            assertEquals(169, result.rowCount(), "13 x 13 UInteger boundaries");
            assertEquals(0, result.agreementCount(),
                    "the invariant is on the Candidate interface, not on HistoricalOracle: this is "
                            + "the D1 tally reproduced with the only other shipped Candidate");
            assertEquals(169, result.count(DiffVerdict.HARNESS_ERROR));
            assertEquals(169, result.disagreements().size());
            assertTrue(result.rows().get(0).historical().startsWith("HARNESS_ERROR:"),
                    result.rows().get(0).toTsv());
        }
    }

    // ------------------------------------------------------------------ FIX 1: BOTH_THREW

    @Test
    @DisplayName("two throws of the same class are BOTH_THREW, and the note carries both messages")
    void twoThrowsAreNeverAgreementAndNeverLoseTheirMessages() {
        UOp op = UOp.binary("UIntegerValue", "power");
        try (Candidate ref = new ThrowsRuntime("ref", "UInteger.power() : expected Real or Integer exponent value");
             Candidate sub = new ThrowsRuntime("sub", "TODO: port UIntegerValue.power(value)")) {

            DiffRow row = new DifferentialSweep(ref, sub, 1L)
                    .sweepBinary(op, List.of(UValue.uInteger(2, 0.5)), List.of(UValue.uInteger(3, 0.5)))
                    .rows().get(0);

            System.out.println("=== FIX 1: two unrelated RuntimeExceptions ========================");
            System.out.println(row.toTsv());
            System.out.println("===================================================================");

            assertEquals(DiffVerdict.BOTH_THREW, row.verdict());
            assertFalse(row.verdict().isAgreement(),
                    "both sides throwing java.lang.RuntimeException is not evidence of anything");
            assertNotEquals("", row.note(), "the note must never be empty on a throw-pair");
            assertTrue(row.note().contains("java.lang.RuntimeException"), row.note());
            assertTrue(row.note().contains("expected Real or Integer exponent value"), row.note());
            assertTrue(row.note().contains("TODO: port UIntegerValue.power(value)"), row.note());
        }
    }

    @Test
    @DisplayName("an adjudicated throw-pair is an agreement, and only the exact pair signed off")
    void acceptedThrowPairsAreOptInAndExact() {
        UOp op = UOp.binary("URealValue", "divideBy");
        AcceptedThrowPairs signedOff = AcceptedThrowPairs.builder()
                .accept(op.key(),
                        "java.lang.ArithmeticException", "/ by zero",
                        "java.lang.ArithmeticException", "/ by zero",
                        "S-x review: both sides reject a zero divisor with the JDK's own message; "
                                + "shared error path, reviewed on 2026-08-17")
                .build();
        List<UValue> lhs = List.of(UValue.uReal(1.0, 0.0));
        List<UValue> rhs = List.of(UValue.uReal(0.0, 0.0));

        assertTrue(AcceptedThrowPairs.none().isEmpty(), "the default allowlist must be empty");
        assertEquals(1, signedOff.size());

        try (Candidate a = new ThrowsArithmetic("ref", "/ by zero");
             Candidate b = new ThrowsArithmetic("sub", "/ by zero")) {

            assertEquals(DiffVerdict.BOTH_THREW,
                    new DifferentialSweep(a, b, 1L).sweepBinary(op, lhs, rhs).rows().get(0).verdict(),
                    "without the allowlist the very same pair must still be a non-agreement");

            DiffRow row = new DifferentialSweep(a, b, 1L, signedOff)
                    .sweepBinary(op, lhs, rhs).rows().get(0);
            assertEquals(DiffVerdict.ACCEPTED_THROW, row.verdict());
            assertTrue(row.verdict().isAgreement());
            assertTrue(row.note().startsWith("adjudicated: S-x review:"), row.note());
            assertTrue(row.note().contains("/ by zero"), row.note());
        }

        // One character of difference in either message, and the sign-off does not apply.
        try (Candidate a = new ThrowsArithmetic("ref", "/ by zero");
             Candidate b = new ThrowsArithmetic("sub", "/ by zero ")) {
            assertEquals(DiffVerdict.BOTH_THREW,
                    new DifferentialSweep(a, b, 1L, signedOff).sweepBinary(op, lhs, rhs)
                            .rows().get(0).verdict(),
                    "the allowlist is keyed on both messages verbatim; near-misses are not signed off");
        }
        // And it is scoped to the operation it was signed off for.
        try (Candidate a = new ThrowsArithmetic("ref", "/ by zero");
             Candidate b = new ThrowsArithmetic("sub", "/ by zero")) {
            assertEquals(DiffVerdict.BOTH_THREW,
                    new DifferentialSweep(a, b, 1L, signedOff)
                            .sweepBinary(UOp.binary("URealValue", "mod"), lhs, rhs)
                            .rows().get(0).verdict());
        }

        assertThrows(IllegalArgumentException.class,
                () -> AcceptedThrowPairs.builder().accept(op.key(), "java.lang.RuntimeException", "",
                        "java.lang.RuntimeException", "", "  "),
                "an entry with no written rationale is exactly the blanket rule this replaces");
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

    /** Two unrelated failures that happen to share the least discriminating class in Java. */
    private static final class ThrowsRuntime implements Candidate {
        private final String name;
        private final String message;

        ThrowsRuntime(String name, String message) {
            this.name = name;
            this.message = message;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public UValue invoke(UOp op, List<UValue> args) {
            throw new RuntimeException(message);
        }

        @Override
        public boolean supports(UOp op) {
            return true;
        }

        @Override
        public void close() {
        }
    }

    /** A genuine shared error path: the shape an allowlist entry is meant to adjudicate. */
    private static final class ThrowsArithmetic implements Candidate {
        private final String name;
        private final String message;

        ThrowsArithmetic(String name, String message) {
            this.name = name;
            this.message = message;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public UValue invoke(UOp op, List<UValue> args) {
            throw new ArithmeticException(message);
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
