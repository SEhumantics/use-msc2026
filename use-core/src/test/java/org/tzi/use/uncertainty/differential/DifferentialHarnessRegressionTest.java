package org.tzi.use.uncertainty.differential;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
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
 * <p>JUnit 5 Jupiter only. The default build carries no {@code junit-vintage-engine}, so a JUnit 3/4
 * test written here would compile, be committed, and never execute. (The {@code -Pupstream-oracle}
 * profile adds a vintage engine so that <em>upstream's</em> own JUnit 3/4 files run unedited — see
 * {@code docs/port2/upstream-oracle-profile.md} — but nothing in this package may depend on that
 * profile being active, because the default build is still the primary gate.)
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
                    () -> DiffReportWriter.writeAll("must-not-be-written.tsv", List.of(empty), Map.of(),
                            AcceptedDegenerateOperations.none()));
            assertTrue(e.getMessage().contains("0 rows"), e.getMessage());

            // The old guard tested results.isEmpty(), so a non-empty list of empty Results slipped
            // through. Pin that specific shape.
            assertThrows(IllegalArgumentException.class,
                    () -> DiffReportWriter.writeAll("must-not-be-written.tsv", List.of(empty, empty),
                            Map.of(), AcceptedDegenerateOperations.none()));
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

    /**
     * The {@code VOID}/{@code NULL} separation, and <em>only</em> that. This test used to carry a
     * comment claiming it pinned "the exact shape of the 'empty-bodied mutator agrees forever'
     * defect". It never did: it asserts that two constants are different, which leaves
     * {@code VOID} vs {@code VOID} free to score {@code AGREE}, and it did — 22 rows out of 22 for
     * this very operation. {@link #d10VoidVersusVoidIsNotAgreement()} is the test that pins it.
     */
    @Test
    @DisplayName("a void historical operation unwraps to VOID, never to NULL")
    void voidIsDistinctFromNull() throws Throwable {
        try (HistoricalOracle oracle = HistoricalOracle.open()) {
            // Value.setTypeToRuntimeType() is public and void, and is inherited by URealValue.
            UOp mutator = UOp.unary("URealValue", "setTypeToRuntimeType");
            assertTrue(oracle.supports(mutator));
            UValue produced = oracle.invoke(mutator, List.of(UValue.uReal(1.0, 0.25)));

            assertEquals(UValue.Kind.VOID, produced.kind(),
                    "Method.invoke returns null for void; mapping that to NULL would make a void "
                            + "operation indistinguishable from one that returned null");
            assertEquals("VOID", produced.canonical());
            assertNotEquals(UValue.nullValue(), produced);
            assertNotEquals(UValue.nullValue().canonical(), produced.canonical());
            assertFalse(produced.carriesAnObservation(),
                    "VOID is the absence of a result, so it is not an observation");
            assertFalse(UValue.nullValue().carriesAnObservation(),
                    "nor is a null result: 'I produced no value' is not a value");
            assertTrue(UValue.uReal(1.0, 0.25).carriesAnObservation());
            assertTrue(UValue.opaque("x", "y").carriesAnObservation(),
                    "an OPAQUE form is a class name plus field-derived content, which is content");
        }
    }

    // ------------------------------------------------------------------ D-10

    @Test
    @DisplayName("D-10: two VOID results are UNMEASURABLE, not an agreement")
    void d10VoidVersusVoidIsNotAgreement() {
        UOp mutator = UOp.unary("URealValue", "setTypeToRuntimeType");
        List<UValue> receivers = InputGenerator.uRealBoundaries();
        try (HistoricalOracle oracle = HistoricalOracle.open();
             Candidate doNothing = new ReturnsFixed("do-nothing-port", UValue.voidValue())) {

            DifferentialSweep.Result result =
                    new DifferentialSweep(oracle, doNothing, 20260817L).sweepUnary(mutator, receivers);

            System.out.println("=== D-10 reproduction (VOID vs VOID) ==============================");
            System.out.println("tally                " + result.summary());
            System.out.println("measurements         " + result.measurementCount());
            System.out.println("agreements           " + result.agreementCount());
            System.out.println("row 0                " + result.rows().get(0).toTsv());
            System.out.println("===================================================================");

            assertEquals(receivers.size(), result.rowCount());
            assertEquals(receivers.size(), result.count(DiffVerdict.UNMEASURABLE));
            assertEquals(0, result.agreementCount(),
                    "the harness observed nothing on either side: an empty-bodied mutator used to "
                            + "score every one of these rows as agreement, forever");
            assertEquals(0, result.measurementCount(), "and it measured nothing");
            assertEquals(receivers.size(), result.disagreements().size(),
                    "a row that was not measured belongs in disagreements(), like every other "
                            + "non-agreement");
            assertFalse(result.isClean(), "a sweep that compared nothing is not a pass");

            DiffRow row = result.rows().get(0);
            assertEquals("VOID", row.historical());
            assertEquals("VOID", row.ported());
            assertTrue(row.note().contains("declared void"), row.note());
            assertTrue(row.note().contains("no post-state was observed on either side"), row.note());
            assertTrue(row.note().contains("reference returned VOID"), row.note());
            assertTrue(row.note().contains("subject returned VOID"), row.note());
        }
    }

    @Test
    @DisplayName("D-10: a value on ONE side only is still a difference, not 'unmeasurable'")
    void oneSidedAbsenceIsADifferenceAndKeepsItsEvidence() {
        UOp mutator = UOp.unary("URealValue", "setTypeToRuntimeType");
        List<UValue> receivers = List.of(UValue.uReal(1.0, 0.25));
        try (HistoricalOracle oracle = HistoricalOracle.open();
             Candidate returnsValue = new ReturnsFixed("returns-a-value", UValue.uReal(7.0, 0.0))) {

            // The historical side has no result (void); the subject invented one. The harness saw
            // something distinguishing, so calling this "no measurement" would destroy evidence.
            DiffRow row = new DifferentialSweep(oracle, returnsValue, 1L)
                    .sweepUnary(mutator, receivers).rows().get(0);

            assertEquals(DiffVerdict.DIFFER, row.verdict(), row.toTsv());
            assertEquals("VOID", row.historical());
            assertEquals("UREAL(7.0,0.0)@URealValue", row.ported());
        }
    }

    @Test
    @DisplayName("two null RESULTS are unmeasurable, but a null against a value is a difference")
    void twoNullValuesAreNotAgreementEither() {
        UOp op = UOp.binary("URealValue", "add");
        List<UValue> domain = List.of(UValue.uReal(1.0, 0.5), UValue.uReal(2.0, 0.5));
        try (Candidate a = new ReturnsFixed("null-a", UValue.nullValue());
             Candidate b = new ReturnsFixed("null-b", UValue.nullValue())) {

            DifferentialSweep.Result both = new DifferentialSweep(a, b, 1L).sweepBinary(op, domain, domain);
            assertEquals(4, both.count(DiffVerdict.UNMEASURABLE));
            assertEquals(0, both.agreementCount(),
                    "two sides that each produced no value did not produce the same value");
            assertEquals(0, both.measurementCount());
            assertTrue(both.rows().get(0).note().contains("not a shared value"),
                    both.rows().get(0).note());
        }
        try (Candidate a = new ReturnsFixed("null-a", UValue.nullValue());
             StubCandidate stub = StubCandidate.faithful()) {

            DifferentialSweep.Result mixed = new DifferentialSweep(stub, a, 1L)
                    .sweepBinary(op, domain, domain);
            assertEquals(4, mixed.count(DiffVerdict.DIFFER),
                    "a null result where the other side produced a value is a real divergence");
            assertEquals(4, mixed.measurementCount());
        }
    }

    /**
     * The audit behind D-10, mechanised so it cannot go stale: <strong>every</strong>
     * {@link UValue.Kind} is either an observation, in which case a candidate that produces it on
     * both sides genuinely agrees, or it is not, in which case both sides producing it is
     * {@link DiffVerdict#UNMEASURABLE}. There is no third option and no kind is unclassified.
     *
     * <p>D-10 was one kind — {@code VOID} — falling into the first bucket while meaning the second.
     * Naming {@code VOID} in a test would pin that instance; iterating {@code Kind.values()} pins the
     * property, so a kind added later that carries no value cannot quietly become a route to
     * {@code AGREE}. The expected membership of the "carries nothing" set is asserted outright as
     * well, so re-classifying a kind is a decision someone has to make in this file.
     */
    @Test
    @DisplayName("no Kind that carries no value can pair with itself into an agreement")
    void everyKindIsEitherAnObservationOrUnmeasurable() {
        Map<UValue.Kind, UValue> samples = new java.util.EnumMap<>(UValue.Kind.class);
        samples.put(UValue.Kind.UREAL, UValue.uReal(1.5, 0.25));
        samples.put(UValue.Kind.UINTEGER, UValue.uInteger(3, 0.5));
        samples.put(UValue.Kind.UBOOLEAN, UValue.uBoolean(true, 0.75));
        samples.put(UValue.Kind.USTRING, UValue.uString("abc", 0.5));
        samples.put(UValue.Kind.REAL, UValue.real(1.5));
        samples.put(UValue.Kind.INTEGER, UValue.integer(3));
        samples.put(UValue.Kind.BOOLEAN, UValue.bool(true));
        samples.put(UValue.Kind.STRING, UValue.string("abc"));
        samples.put(UValue.Kind.SEQUENCE, UValue.sequence(List.of(UValue.integer(1))));
        samples.put(UValue.Kind.NULL, UValue.nullValue());
        samples.put(UValue.Kind.VOID, UValue.voidValue());
        samples.put(UValue.Kind.OPAQUE, UValue.opaque("uDataTypes.SBoolean", "t=1.0,f=0.0"));
        assertEquals(UValue.Kind.values().length, samples.size(),
                "every Kind needs a representative here, including any added since this was written");

        java.util.Set<UValue.Kind> carriesNothing = new java.util.TreeSet<>();
        UOp op = UOp.binary("URealValue", "add");
        List<UValue> domain = List.of(UValue.uReal(1.0, 0.0));
        for (Map.Entry<UValue.Kind, UValue> e : samples.entrySet()) {
            UValue sample = e.getValue();
            assertEquals(e.getKey(), sample.kind(), "sample mislabelled");
            try (Candidate a = new ReturnsFixed("a", sample);
                 Candidate b = new ReturnsFixed("b", sample)) {

                DiffRow row = new DifferentialSweep(a, b, 1L).sweepBinary(op, domain, domain)
                        .rows().get(0);
                if (sample.carriesAnObservation()) {
                    assertEquals(DiffVerdict.AGREE, row.verdict(),
                            e.getKey() + " carries a value, so two of them really are the same value: "
                                    + row.toTsv());
                } else {
                    carriesNothing.add(e.getKey());
                    assertEquals(DiffVerdict.UNMEASURABLE, row.verdict(),
                            e.getKey() + " carries no value, so two of them are not a shared value: "
                                    + row.toTsv());
                    assertFalse(row.verdict().isAgreement(), row.toTsv());
                    assertFalse(row.verdict().isMeasurement(), row.toTsv());
                }
            }
        }
        assertEquals(java.util.Set.of(UValue.Kind.NULL, UValue.Kind.VOID), carriesNothing,
                "exactly two kinds stand for the absence of a result. Changing this set changes what "
                        + "the harness is willing to call an agreement, so it is asserted rather than "
                        + "derived");
    }

    // ------------------------------------------------------------------ D-11 / D-12: measurements

    @Test
    @DisplayName("D-11: a report with rows but no measurements is refused")
    void aReportWithNoMeasurementsIsRefused() {
        UOp mutator = UOp.unary("URealValue", "setTypeToRuntimeType");
        try (HistoricalOracle oracle = HistoricalOracle.open();
             Candidate doNothing = new ReturnsFixed("do-nothing-port", UValue.voidValue())) {

            DifferentialSweep.Result voidOnly = new DifferentialSweep(oracle, doNothing, 1L)
                    .sweepUnary(mutator, InputGenerator.uRealBoundaries());

            assertTrue(voidOnly.rowCount() > 0, "this is not the zero-row trap; there are rows");
            assertEquals(0, voidOnly.measurementCount());

            IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                    () -> DiffReportWriter.writeAll("no-measurements.tsv", List.of(voidOnly), Map.of(),
                            AcceptedDegenerateOperations.none()),
                    "the old guard counted rows, so a maximally green report over zero comparisons "
                            + "was written happily");
            System.out.println("=== D-11: writer refusal ==========================================");
            System.out.println(e.getMessage());
            System.out.println("===================================================================");
            assertTrue(e.getMessage().contains("no measurements"), e.getMessage());
            assertFalse(java.nio.file.Files.exists(
                            DiffReportWriter.reportDir().resolve("no-measurements.tsv")),
                    "the refused report must not have been created");
        }
    }

    @Test
    @DisplayName("D-12: 'no disagreements' is not a pass; isClean and requireMeasurements say so")
    void zeroMeasurementSweepsCannotReadAsSuccess() {
        UOp op = UOp.binary("URealValue", "add");
        try (StubCandidate a = StubCandidate.faithful(); StubCandidate b = StubCandidate.faithful()) {

            // (1) The zero-row trap: a stage that asserts only disagreements().isEmpty() passes.
            DifferentialSweep.Result empty = new DifferentialSweep(a, b, 1L)
                    .sweepBinary(op, List.of(), List.of(UValue.uReal(1.0, 0.0)));
            assertEquals(List.of(), empty.disagreements(), "which is exactly the trap");
            assertEquals(0, empty.measurementCount());
            assertFalse(empty.isClean(), "a sweep that never ran is not a clean sweep");
            IllegalStateException e = assertThrows(IllegalStateException.class,
                    () -> empty.requireMeasurements(1));
            assertTrue(e.getMessage().contains("measured 0 row(s)"), e.getMessage());

            // (2) A sweep that ran and compared something is clean, and says so.
            DifferentialSweep.Result real = new DifferentialSweep(a, b, 1L)
                    .sweepBinary(op, List.of(UValue.uReal(1.0, 0.5)), List.of(UValue.uReal(2.0, 0.5)));
            assertTrue(real.isClean());
            assertEquals(1, real.measurementCount());
            assertSame(real, real.requireMeasurements(1));
        }

        // (3) Rows that exist but were never comparisons: not clean either.
        try (HistoricalOracle oracle = HistoricalOracle.open();
             StubCandidate stub = StubCandidate.faithful()) {
            DifferentialSweep.Result unmarshallable = new DifferentialSweep(oracle, stub, 1L)
                    .sweepBinary(op, InputGenerator.uIntegerBoundaries(),
                            InputGenerator.uIntegerBoundaries());
            assertEquals(169, unmarshallable.rowCount());
            assertEquals(0, unmarshallable.measurementCount());
            assertFalse(unmarshallable.isClean());
        }
    }

    // ------------------------------------------------------------------ D-15: degenerate codomain

    /**
     * The measurement the harness never took. {@code distinctReferenceValues()} must count the
     * <em>reference's</em> answers, over the <em>measured</em> rows, and nothing else — the two
     * mistakes that would make it useless are counting the subject's column (a constant subject then
     * looks degenerate whatever the reference did, which is accidentally the right answer here and
     * the wrong answer for a real port) and counting over all rows (throw and harness-error markers
     * are distinct strings, so an all-{@code HARNESS_ERROR} sweep would look richly discriminating).
     */
    @Test
    @DisplayName("D-15: distinctReferenceValues counts the reference, over measured rows only")
    void distinctReferenceValuesCountsTheReferenceOverMeasuredRows() {
        UOp op = UOp.binary("URealValue", "add");
        try (StubCandidate faithful = StubCandidate.faithful();
             Candidate constant = new ReturnsFixed("const", UValue.uReal(3.0, 0.0))) {

            // Three receivers, three different sums: the reference varies.
            List<UValue> receivers = List.of(UValue.uReal(1.0, 0.0), UValue.uReal(2.0, 0.0),
                    UValue.uReal(3.0, 0.0));
            List<UValue> argument = List.of(UValue.uReal(0.0, 0.0));

            DifferentialSweep.Result varied = new DifferentialSweep(faithful, constant, 1L)
                    .sweepBinary(op, receivers, argument);
            assertEquals(3, varied.measurementCount());
            assertEquals(3, varied.distinctReferenceValues(),
                    "the REFERENCE gave three answers; the subject gave one. " + varied.summary());
            assertEquals(List.of("UREAL(1.0,0.0)@URealValue", "UREAL(2.0,0.0)@URealValue",
                            "UREAL(3.0,0.0)@URealValue"),
                    List.copyOf(varied.referenceValues()));
            assertNull(varied.soleReferenceValue(), "there is no sole value when there are three");
            assertTrue(varied.isDiscriminating());

            // One receiver: the reference cannot say anything else, and the subject is right.
            DifferentialSweep.Result degenerate = new DifferentialSweep(faithful, constant, 1L)
                    .sweepBinary(op, List.of(UValue.uReal(3.0, 0.0)), argument);
            assertTrue(degenerate.isClean(), "the old predicate says pass: " + degenerate.summary());
            assertEquals(1, degenerate.distinctReferenceValues());
            assertEquals("UREAL(3.0,0.0)@URealValue", degenerate.soleReferenceValue());
            assertFalse(degenerate.isDiscriminating());
        }

        // Rows that are not measurements contribute nothing, however many distinct markers they
        // carry. Two different harness failures and two different throws are still zero.
        try (HistoricalOracle oracle = HistoricalOracle.open();
             StubCandidate stub = StubCandidate.faithful()) {
            DifferentialSweep.Result unmarshallable = new DifferentialSweep(oracle, stub, 1L)
                    .sweepBinary(op, InputGenerator.uIntegerBoundaries(),
                            InputGenerator.uIntegerBoundaries());
            assertEquals(169, unmarshallable.rowCount());
            assertEquals(0, unmarshallable.measurementCount());
            assertEquals(0, unmarshallable.distinctReferenceValues(),
                    "169 rows of absence are not 169 observations of a range");
            assertFalse(unmarshallable.isDiscriminating());
            assertNull(unmarshallable.soleReferenceValue());
        }
    }

    /**
     * The gate itself, in all three of its clauses and in both directions. This is the mechanical
     * form of the rule {@code harness-contract.md} used to state as discipline — "no fidelity claim
     * may quote a per-operation agreement figure without also quoting distinct reference values" —
     * because discipline is not enforcement.
     */
    @Test
    @DisplayName("D-15: the stage gate refuses a single-valued operation, and says why")
    void theStageGateRefusesADegenerateOperation() {
        UOp op = UOp.binary("URealValue", "add");
        List<UValue> one = List.of(UValue.uReal(1.0, 0.0));
        List<UValue> many = List.of(UValue.uReal(1.0, 0.0), UValue.uReal(2.0, 0.0),
                UValue.uReal(4.0, 0.0), UValue.uReal(8.0, 0.0));

        try (StubCandidate a = StubCandidate.faithful(); StubCandidate b = StubCandidate.faithful()) {

            // (1) One measured row, agreed, single-valued: isClean() says pass, the gate does not.
            DifferentialSweep.Result degenerate = new DifferentialSweep(a, b, 1L)
                    .sweepBinary(op, one, one);
            assertTrue(degenerate.isClean(), "precondition: the documented pass predicate says yes");
            assertSame(degenerate, degenerate.requireMeasurements(1), "and so does the floor of 1");
            assertFalse(degenerate.isStagePass(1, AcceptedDegenerateOperations.none()));

            IllegalStateException refusal = assertThrows(IllegalStateException.class,
                    () -> degenerate.requireStagePass(1, AcceptedDegenerateOperations.none()));
            System.out.println("=== D-15: the stage gate ==========================================");
            System.out.println(refusal.getMessage());
            assertTrue(refusal.getMessage().contains("1 distinct value(s)"), refusal.getMessage());
            assertTrue(refusal.getMessage().contains("UREAL(2.0,0.0)"), refusal.getMessage());
            assertTrue(refusal.getMessage().contains("D-15"), refusal.getMessage());

            // (2) The measurement floor is a separate clause and reports separately.
            List<String> tooFew = degenerate.stageGateFailures(50, AcceptedDegenerateOperations.none());
            assertEquals(2, tooFew.size(), tooFew.toString());
            assertTrue(tooFew.get(0).contains("needed at least 50"), tooFew.toString());

            // (3) A floor of zero is not a floor and must not be expressible.
            assertThrows(IllegalArgumentException.class,
                    () -> degenerate.stageGateFailures(0, AcceptedDegenerateOperations.none()));

            // (4) Widen the domain until the reference varies, and the same subject passes.
            DifferentialSweep.Result discriminating = new DifferentialSweep(a, b, 1L)
                    .sweepBinary(op, many, many);
            assertTrue(discriminating.isDiscriminating(), discriminating.summary());
            assertSame(discriminating, discriminating.requireStagePass(
                    16, AcceptedDegenerateOperations.none()));
            System.out.println("PASSES: " + discriminating.stageStatement(
                    AcceptedDegenerateOperations.none()));

            // (5) A disagreement is still a disagreement in a discriminating sweep.
            try (Candidate wrong = new ReturnsFixed("const", UValue.uReal(1.0, 0.0))) {
                DifferentialSweep.Result bad = new DifferentialSweep(a, wrong, 1L)
                        .sweepBinary(op, many, many);
                assertFalse(bad.isStagePass(1, AcceptedDegenerateOperations.none()));
                assertTrue(bad.stageGateFailures(1, AcceptedDegenerateOperations.none()).get(0)
                        .contains("did not agree"));
            }

            // (6) The sign-off, and its exactness. The rationale reaches the evidence.
            AcceptedDegenerateOperations signed = AcceptedDegenerateOperations.builder()
                    .accept(op.key(), "UREAL(2.0,0.0)@URealValue", "reviewed: a one-point domain, kept as a "
                            + "reachability check only")
                    .build();
            assertTrue(degenerate.isStagePass(1, signed));
            assertTrue(degenerate.stageStatement(signed).contains("acknowledged: reviewed:"),
                    degenerate.stageStatement(signed));
            assertFalse(degenerate.isStagePass(1, AcceptedDegenerateOperations.builder()
                    .accept(op.key(), "UREAL(9.9,0.0)@URealValue", "a different value").build()));
            assertFalse(degenerate.isStagePass(1, AcceptedDegenerateOperations.builder()
                    .accept("URealValue.minus(value)", "UREAL(2.0,0.0)@URealValue", "a different op").build()));
            assertThrows(NullPointerException.class, () -> degenerate.isStagePass(1, null));
            System.out.println("===================================================================");
        }
    }

    /**
     * The number has to reach the artefact a human reads, per operation and not as a file-level sum.
     * A report whose header says {@code # rows.disagreement 0} and nothing else is exactly what the
     * 120-literal subject produces.
     */
    @Test
    @DisplayName("D-15: the report header carries distinct reference values, per operation")
    void theReportHeaderCarriesDiscriminatingPowerPerOperation() throws java.io.IOException {
        UOp add = UOp.binary("URealValue", "add");
        UOp minus = UOp.binary("URealValue", "minus");
        List<UValue> one = List.of(UValue.uReal(1.0, 0.0));
        List<UValue> many = List.of(UValue.uReal(1.0, 0.0), UValue.uReal(2.0, 0.0),
                UValue.uReal(4.0, 0.0));

        try (StubCandidate a = StubCandidate.faithful(); StubCandidate b = StubCandidate.faithful()) {
            DifferentialSweep sweep = new DifferentialSweep(a, b, 1L);
            DifferentialSweep.Result degenerate = sweep.sweepBinary(add, one, one);
            DifferentialSweep.Result varied = sweep.sweepBinary(minus, many, many);

            AcceptedDegenerateOperations signed = AcceptedDegenerateOperations.builder()
                    .accept(add.key(), "UREAL(2.0,0.0)@URealValue", "reviewed: one-point domain")
                    .build();
            java.nio.file.Path written = DiffReportWriter.writeAll("d15-header.tsv",
                    List.of(degenerate, varied), Map.of(), signed);
            List<String> header = new java.util.ArrayList<>();
            for (String line : java.nio.file.Files.readAllLines(written,
                    java.nio.charset.StandardCharsets.UTF_8)) {
                if (!line.startsWith("#")) {
                    break;
                }
                header.add(line);
            }
            System.out.println("=== D-15: the report header =======================================");
            header.forEach(System.out::println);
            System.out.println("===================================================================");

            assertTrue(header.contains("# rows.disagreement\t0"),
                    "precondition: the file-level header reads as a clean run");
            assertTrue(header.contains("# op.URealValue.add(value).distinctReferenceValues\t1"), header.toString());
            assertTrue(header.contains("# op.URealValue.add(value).discriminating\tfalse"), header.toString());
            assertTrue(header.contains(
                    "# op.URealValue.add(value).soleReferenceValue\tUREAL(2.0,0.0)@URealValue"),
                    header.toString());
            assertTrue(header.contains("# op.URealValue.add(value).degenerate.acknowledged\t"
                    + "reviewed: one-point domain"), header.toString());
            assertTrue(header.contains("# op.URealValue.minus(value).discriminating\ttrue"), header.toString());
            assertTrue(header.contains("# accepted.degenerateOperations\t1"), header.toString());
        }
    }

    // ------------------------------------------------------------------ D-34: the header cannot lie

    /**
     * <strong>D-34: a report must not be able to understate the sign-offs its own verdict rests
     * on.</strong>
     *
     * <p>{@code DiffReportWriter} used to offer a three-argument {@code writeAll} that substituted
     * {@link AcceptedDegenerateOperations#none()}, and all five call sites in the tree used it. So a
     * stage could take a pass that <em>only exists because of a written sign-off</em> and publish a
     * report whose header asserts {@code # accepted.degenerateOperations 0}. Measured before the fix
     * on exactly the sweep below:
     * <pre>
     *   sign-off in force?   1  [URealValue.add(value)|UREAL(2.0,0.0)@URealValue -&gt; ...]
     *   stage pass WITHOUT the sign-off? false
     *   stage pass WITH    the sign-off? true
     *   # accepted.degenerateOperations   0
     * </pre>
     *
     * <p>Two halves, and the second is the one that keeps the fix:
     * <ol>
     *   <li>the header of a report written under a sign-off carries the count <em>and</em> the
     *       rationale verbatim, and the report of the same sweep written under
     *       {@code none()} is a <em>different file</em>, so the two runs are never
     *       byte-indistinguishable;</li>
     *   <li>no {@code write}/{@code writeAll} overload exists that omits the parameter. Asserted
     *       reflectively rather than by inspection, because "all five call sites pass it today" is a
     *       fact about today and a sixth call site is one line of typing. The hole was a default
     *       value, so the fix is the absence of a default.</li>
     * </ol>
     */
    @Test
    @DisplayName("D-34: a report cannot understate the sign-offs its verdict was granted under")
    void aReportCannotUnderstateItsOwnSignOffs() throws java.io.IOException {
        UOp op = UOp.binary("URealValue", "add");
        List<UValue> one = List.of(UValue.uReal(1.0, 0.0));

        try (StubCandidate a = StubCandidate.faithful(); StubCandidate b = StubCandidate.faithful()) {
            DifferentialSweep.Result degenerate = new DifferentialSweep(a, b, 1L)
                    .sweepBinary(op, one, one);

            String sole = degenerate.soleReferenceValue();
            assertNotNull(sole, "precondition: one receiver, one answer");
            AcceptedDegenerateOperations signed = AcceptedDegenerateOperations.builder()
                    .accept(op.key(), sole, "reviewed: a one-point domain, kept as a reachability "
                            + "check only; nothing here is evidence about the addition rule")
                    .build();

            // The pass exists ONLY because of the sign-off. That is the premise of the defect.
            assertFalse(degenerate.isStagePass(1, AcceptedDegenerateOperations.none()));
            assertTrue(degenerate.isStagePass(1, signed));

            List<String> underSignOff = headerOf(DiffReportWriter.write(
                    "d34-under-sign-off.tsv", degenerate, Map.of(), signed));
            List<String> underNone = headerOf(DiffReportWriter.write(
                    "d34-under-none.tsv", degenerate, Map.of(),
                    AcceptedDegenerateOperations.none()));

            System.out.println("=== D-34: the same sweep, two sign-off sets =======================");
            underSignOff.stream().filter(l -> l.startsWith("# accepted"))
                    .forEach(l -> System.out.println("  signed:  " + l));
            underNone.stream().filter(l -> l.startsWith("# accepted"))
                    .forEach(l -> System.out.println("  none:    " + l));
            System.out.println("===================================================================");

            assertTrue(underSignOff.contains("# accepted.degenerateOperations\t1"),
                    underSignOff.toString());
            assertTrue(underSignOff.stream().anyMatch(l -> l.startsWith("# accepted.degenerateOperation\t")
                            && l.contains("reviewed: a one-point domain")),
                    "the rationale itself must travel with the number: " + underSignOff);
            assertTrue(underNone.contains("# accepted.degenerateOperations\t0"), underNone.toString());
            assertNotEquals(underSignOff, underNone,
                    "a run with a sign-off in force must not be indistinguishable from one without");

            // (2) The overload that made the understatement possible must not exist.
            for (java.lang.reflect.Method m : DiffReportWriter.class.getMethods()) {
                if (!m.getName().equals("write") && !m.getName().equals("writeAll")) {
                    continue;
                }
                assertTrue(List.of(m.getParameterTypes()).contains(AcceptedDegenerateOperations.class),
                        "DiffReportWriter." + m.getName() + java.util.Arrays.toString(m.getParameterTypes())
                                + " can write a report without being told which sign-offs were in "
                                + "force, and will substitute none(). That is defect D-34: the header "
                                + "then asserts '# accepted.degenerateOperations 0' about a pass that "
                                + "a sign-off granted.");
            }
        }
    }

    private static List<String> headerOf(java.nio.file.Path report) throws java.io.IOException {
        List<String> header = new java.util.ArrayList<>();
        for (String line : java.nio.file.Files.readAllLines(report,
                java.nio.charset.StandardCharsets.UTF_8)) {
            if (!line.startsWith("#")) {
                break;
            }
            header.add(line);
        }
        return header;
    }

    // ------------------------------------------------------------------ D-18: the type-bearing form

    /**
     * <strong>D-18, at unit resolution: the two shapes of "wrong type", and which of them was already
     * caught.</strong>
     *
     * <p>The brief for this fix names two cases and they had different starting points, which the
     * record should not blur:
     * <ul>
     *   <li>a <strong>{@link UValue.Kind} difference</strong> — {@code URealValue(3,0)} where the
     *       historical answers {@code UIntegerValue(3,0)}, or {@code IntegerValue} where
     *       {@code UIntegerValue} is required — was <em>always</em> a {@link DiffVerdict#DIFFER}, because
     *       the kind is the leading token of the canonical form. Pinned here so that stays true;</li>
     *   <li>a <strong>runtime-class difference inside one kind</strong> — a raw {@code java.lang.Integer}
     *       against an {@code org.tzi.use.uml.ocl.value.IntegerValue}, same payload — was
     *       {@link DiffVerdict#AGREE} on 193 of 285 operations, <em>invisibly</em>, and is the defect.</li>
     * </ul>
     * The note on such a row must name both fully-qualified types and say the content was identical,
     * because "the port returned the right number in the wrong class" and "the port returned the wrong
     * number" are different findings and the two columns look nearly the same.
     *
     * <p><strong>Round 8 demoted the second case from a verdict to a counted dimension</strong>
     * (defect <strong>D-43</strong> half (b)). It is scored {@link DiffVerdict#AGREE} and counted by
     * {@link DifferentialSweep.Result#javaTypeMismatchCount()}, because at S1 the ported side's token
     * cannot be authentically observed — no ported value class exists in {@code use-core/src/main} to
     * observe, so a type-only divergence measures the adapter and not the port. The blindness D-18 was
     * opened to close is <em>not</em> back: the difference is on the row, in the note, in the summary, in
     * {@code stageStatement()} and in the report header, and it is asserted here in all of those places.
     * What changed is which population it is counted in. A {@link UValue.Kind} difference is a content
     * difference and stays {@link DiffVerdict#DIFFER}, as does a row whose content differs as well.
     */
    @Test
    @DisplayName("D-18/D-43: a Kind difference is DIFFER; a runtime-class difference with identical "
            + "content is AGREE and counted in javaTypeMismatch, with both types and both provenances "
            + "in the note")
    void rightContentInTheWrongJavaTypeIsADifference() {
        // (1) The Kind difference. Already caught before the fix; must not regress.
        assertNotEquals(UValue.uReal(3.0, 0.0).canonical(), UValue.uInteger(3, 0.0).canonical());
        assertNotEquals(UValue.integer(3).canonical(), UValue.uInteger(3, 0.0).canonical());
        assertNotEquals(UValue.bool(true).canonical(), UValue.uBoolean(true, 0.0).canonical());

        // (2) The runtime-class difference inside one kind. This is the defect. The raw side's class is
        //     OBSERVED off the very object an int-returning operation answers with once reflection or
        //     autoboxing has boxed it (D-43); the boxed side is the factory default, i.e. ASSUMED.
        UValue raw = UValue.integer(7).observedFrom(Integer.valueOf(7));
        UValue boxed = UValue.integer(7);
        assertEquals("INTEGER(7)@Integer", raw.canonical());
        assertEquals("INTEGER(7)@IntegerValue", boxed.canonical());
        assertEquals(raw.content(), boxed.content(), "the payload is the same; only the type moved");
        assertNotEquals(raw.canonical(), boxed.canonical());
        assertNotEquals(raw, boxed, "and UValue equality is canonical equality");

        // (3) The note, on a real row.
        UOp op = UOp.unary("URealValue", "neg");
        try (Candidate ref = new ReturnsFixed("ref", raw);
             Candidate sub = new ReturnsFixed("sub", boxed)) {
            DifferentialSweep.Result result = new DifferentialSweep(ref, sub, 1L)
                    .sweepUnary(op, List.of(UValue.uReal(1.0, 0.0)));
            DiffRow row = result.rows().get(0);
            System.out.println("=== D-18: the type-mismatch note =================================");
            System.out.println(row.toTsv());
            System.out.println("===================================================================");

            // Round 8: AGREE, not DIFFER -- the content is identical and only the class moved, and
            // at S1 the ported token cannot be authentically observed, so that difference measures
            // the adapter. It is still measured, in its own dimension, on this very row.
            assertEquals(DiffVerdict.AGREE, row.verdict(), row.toTsv());
            assertNotEquals(row.historical(), row.ported(),
                    "the two columns must still SHOW the difference: an AGREE row that renders "
                            + "identically would have lost the observation, not demoted it");
            assertEquals(1, result.javaTypeMismatchCount(),
                    "and the row must be counted where the demotion put it: " + result.summary());
            assertEquals(0, result.disagreements().size(), result.summary());
            assertTrue(result.summary().contains("javaTypeMismatch=1"), result.summary());
            assertTrue(row.note().contains("java.lang.Integer"), row.note());
            assertTrue(row.note().contains("org.tzi.use.uml.ocl.value.IntegerValue"), row.note());
            assertTrue(row.note().contains("IDENTICAL"), row.note());
            assertTrue(row.note().contains("D-18"), row.note());
            assertTrue(row.note().contains("rows.javaTypeMismatch"),
                    "the note must name the dimension the row was counted in: " + row.note());
            assertTrue(row.note().contains("Provenance: reference OBSERVED, subject ASSUMED"),
                    "and must name both provenances: " + row.note());
        }

        // (4) A CONTENT divergence is untouched by the demotion -- still DIFFER, still the empty note
        //     it always had, because the two columns already say everything.
        try (Candidate ref = new ReturnsFixed("ref", UValue.uReal(1.0, 0.0));
             Candidate sub = new ReturnsFixed("sub", UValue.uReal(2.0, 0.0))) {
            DifferentialSweep.Result result = new DifferentialSweep(ref, sub, 1L)
                    .sweepUnary(op, List.of(UValue.uReal(1.0, 0.0)));
            DiffRow row = result.rows().get(0);
            assertEquals(DiffVerdict.DIFFER, row.verdict());
            assertEquals("", row.note(), "only a TYPE mismatch earns a note here");
            assertEquals(0, result.javaTypeMismatchCount(),
                    "a content divergence must NOT be diluted into the type dimension");
        }

        // (4b) Content AND type both wrong is a content finding: DIFFER, and not counted as a type
        //      mismatch. The demotion must not give a wrong answer a discount for also being the
        //      wrong class.
        try (Candidate ref = new ReturnsFixed("ref", UValue.integer(7).observedFrom(Integer.valueOf(7)));
             Candidate sub = new ReturnsFixed("sub", UValue.integer(8))) {
            DifferentialSweep.Result result = new DifferentialSweep(ref, sub, 1L)
                    .sweepUnary(op, List.of(UValue.uReal(1.0, 0.0)));
            DiffRow row = result.rows().get(0);
            assertEquals(DiffVerdict.DIFFER, row.verdict(), row.toTsv());
            assertEquals(0, result.javaTypeMismatchCount(), result.summary());
            assertTrue(row.note().contains("different as well"), row.note());
        }

        // (5) NULL and VOID stand for the absence of a result, so they carry no observed class and
        //     render exactly as they always did. A non-result cannot be re-typed.
        assertEquals("NULL", UValue.nullValue().canonical());
        assertEquals("VOID", UValue.voidValue().canonical());
        assertNull(UValue.nullValue().javaType());
        assertEquals(UValue.TypeProvenance.NONE, UValue.nullValue().typeProvenance());
        assertThrows(IllegalStateException.class, () -> UValue.voidValue().observedFrom("whatever"));
        assertThrows(IllegalStateException.class,
                () -> UValue.nullValue().observedFrom(Integer.valueOf(1)));
    }

    /**
     * The canonical form compares the <em>simple</em> class name, not the package, and that is a
     * deliberate choice with a cost: two classes of one simple name in different packages compare
     * equal. It is the right trade because the historical side is loaded from a vendored jar by an
     * isolated class loader while the ported side comes from the reactor, and a port that relocated
     * {@code URealValue} into another package would otherwise show every row as a divergence — a
     * difference in where a file lives, not in what an operation answered.
     *
     * <p>Nothing is discarded: the fully-qualified name survives on {@link UValue#javaType()} and is
     * what {@link DifferentialSweep}'s type note prints.
     *
     * <p><strong>The rationale covers the type token and nothing else</strong> (defect
     * <strong>D-44</strong>). This test exercises {@link UValue.Kind#UREAL}, whose content carries no
     * class name. On the {@link UValue.Kind#OPAQUE} branch the fully-qualified name is part of the
     * compared <em>content</em> — {@link UValue#opaque(String, String)} writes it in and
     * {@link HistoricalOracle#opaqueRepresentation(Object)} adds the FQNs of every field's declaring
     * class — so there a relocated port really is a divergence on every row: 197 rows across 17
     * operations. Asserted below so the boundary is measured rather than assumed, and so nobody reads
     * "the harness is package-insensitive" as a property of the row.
     */
    @Test
    @DisplayName("D-18: the compared type token is the simple name; the FQN survives for the note")
    void theTypeTokenIsPackageInsensitiveOnPurpose() {
        UValue here = UValue.uReal(1.0, 0.0);
        // The relocated token is OBSERVED off a real object of a real class whose simple name is
        // URealValue and whose package is not org.tzi.use.uml.ocl.value -- a nested class in THIS file
        // is exactly that, and simpleName() cuts at the last '.' or '$'. It used to be produced by
        // declaredJavaType("com.example.port.value.URealValue", ...); that method is gone (D-43 half
        // (b)) and an observation of a genuinely relocated class is the stronger construction anyway,
        // because nothing here states a name.
        UValue relocated = UValue.uReal(1.0, 0.0).observedFrom(new URealValue());
        assertEquals(UValue.TypeProvenance.OBSERVED, relocated.typeProvenance());
        assertEquals(getClass().getName() + "$URealValue", relocated.javaType());
        assertEquals("URealValue", here.typeToken());
        assertEquals("URealValue", relocated.typeToken());
        assertEquals(here.canonical(), relocated.canonical(),
                "a relocated port of the same class must not read as 576 divergences per operation");
        assertEquals("org.tzi.use.uml.ocl.value.URealValue", here.javaType());
        assertNotEquals(here.javaType(), relocated.javaType(), "the packages really do differ");

        // A nested class is named by its own simple name, not by Outer$Inner.
        assertEquals("Inner", UValue.simpleName("a.b.Outer$Inner"));
        assertEquals("Plain", UValue.simpleName("Plain"));
        assertNull(UValue.simpleName(null));

        // D-44: and on the OPAQUE branch the package is NOT insensitive, because the FQN is content.
        UValue opaqueHere = UValue.opaque("org.tzi.use.uml.ocl.type.URealType", "URealType{}");
        UValue opaqueThere = UValue.opaque("com.example.port.type.URealType", "URealType{}");
        assertEquals(opaqueHere.typeToken(), opaqueThere.typeToken(), "the TOKEN is insensitive");
        assertNotEquals(opaqueHere.canonical(), opaqueThere.canonical(),
                "but the ROW is not: opaque() puts the fully-qualified name into the compared content, "
                        + "so a relocated port diverges on all 197 OPAQUE rows (D-44)");
        assertNotEquals(opaqueHere.content(), opaqueThere.content());
    }

    /**
     * A stand-in for a port that relocated {@code URealValue} into another package. Its only job is to
     * be a real class, of a real package that is not {@code org.tzi.use.uml.ocl.value}, whose simple
     * name is {@code URealValue} — so that
     * {@link #theTypeTokenIsPackageInsensitiveOnPurpose()} can <em>observe</em> a relocated token
     * instead of declaring one. A nested class's binary name is {@code Outer$URealValue} and
     * {@link UValue#simpleName(String)} cuts at the last {@code '.'} or {@code '$'}, so the token is
     * {@code URealValue} exactly as a top-level relocated class's would be.
     */
    static final class URealValue {
    }

    /**
     * <strong>D-43, at unit resolution: a class token is OBSERVED or it is ASSUMED, there is no third
     * state, and no API takes one from an adapter author.</strong>
     *
     * <p>The D-18 fix made a Java-class difference visible. What it did not do was make the two halves of
     * that comparison the same kind of statement: {@link HistoricalOracle#fromHistorical} <em>observes</em>
     * the reference's class, while the ported side's token was whatever an adapter passed to a
     * one-argument {@code asJavaType(String)} (round 6) and then to
     * {@code declaredJavaType(String javaType, String why)} (round 7). Both were measured erasing the
     * check with one line: a genuinely wrong-class port plus the reference's own token produced a sweep
     * byte-identical to the perfect-port control, and in round 7's case the mandatory reason reached
     * <strong>0</strong> rows, because the type note fires only when the two class names differ and a
     * laundering declaration makes them equal by construction.
     *
     * <p>Round 8's answer is removal, not a third patch. The property asserted here is therefore about
     * the <em>shape of the API</em> as much as about values:
     * <ol>
     *   <li>{@link UValue#observedFrom(Object)} derives the token from {@code getClass().getName()}, and a
     *       boxed primitive is exactly what the reference observes for a raw return;</li>
     *   <li>the factory default is {@link UValue.TypeProvenance#ASSUMED}, and
     *       <strong>{@link UValue.TypeProvenance} has exactly three constants</strong> — two that carry a
     *       class, plus {@code NONE} for the absence of a result. No {@code DECLARED};</li>
     *   <li><strong>no public member of {@link UValue} can be handed a class name.</strong> Checked by
     *       reflection over the whole public surface, because the two escape hatches that had to be
     *       deleted were both ordinary-looking public methods and a grep is not a mechanism;</li>
     *   <li>the provenance reaches the row note in both shapes and reaches neither the canonical form nor
     *       the verdict. A subject must not be able to move a row in either direction by how it came by
     *       its token.</li>
     * </ol>
     */
    @Test
    @DisplayName("D-43: a class token is OBSERVED or ASSUMED, no API accepts one from an adapter, and "
            + "the note says which without moving any verdict")
    void theTypeTokenIsObservedOrAssumedAndNoApiTakesOne() {
        // (1) Observation reads the object's own class. A raw `boolean`/`int` return arrives boxed,
        //     through Method.invoke or through autoboxing, and that is what the reference sees too.
        UValue observedRawBoolean = UValue.bool(true).observedFrom(Boolean.TRUE);
        assertEquals("java.lang.Boolean", observedRawBoolean.javaType());
        assertEquals("BOOLEAN(true)@Boolean", observedRawBoolean.canonical());
        assertEquals(UValue.TypeProvenance.OBSERVED, observedRawBoolean.typeProvenance());
        assertEquals("java.lang.Integer", UValue.integer(5).observedFrom(5).javaType());
        assertThrows(IllegalArgumentException.class, () -> UValue.bool(true).observedFrom(null),
                "there is no class on a null result: that is nullValue(), not an observation");

        // (2) The factory default is a guess, and it is the ONLY other state.
        assertEquals(UValue.TypeProvenance.ASSUMED, UValue.bool(true).typeProvenance());
        assertEquals("org.tzi.use.uml.ocl.value.BooleanValue", UValue.bool(true).javaType());
        assertEquals(List.of("OBSERVED", "ASSUMED", "NONE"),
                java.util.Arrays.stream(UValue.TypeProvenance.values()).map(Enum::name)
                        .collect(java.util.stream.Collectors.toList()),
                "two states carry a class and neither is author-chosen; a DECLARED constant is exactly "
                        + "the escape hatch rounds 6 and 7 shipped, and it is not coming back");

        // (3) THE ESCAPE HATCH IS GONE, checked against the API and not against a grep. No public
        //     member of UValue accepts a class name from a caller. The three String-taking factories
        //     are enumerated by hand below and each is justified: their String is CONTENT.
        Map<String, String> stringTakingMembers = new java.util.TreeMap<>();
        for (java.lang.reflect.Method m : UValue.class.getMethods()) {
            if (m.getDeclaringClass() != UValue.class) {
                continue;                                   // Object's own methods
            }
            for (Class<?> parameter : m.getParameterTypes()) {
                if (parameter == String.class || parameter == CharSequence.class) {
                    stringTakingMembers.put(m.getName(),
                            m.getName() + java.util.Arrays.toString(m.getParameterTypes()));
                }
            }
        }
        System.out.println("=== D-43: every public UValue member that accepts a String ========");
        stringTakingMembers.values().forEach(v -> System.out.println("  " + v));
        System.out.println("===================================================================");
        assertEquals(Set.of("uString", "string", "opaque"), stringTakingMembers.keySet(),
                "a new public UValue member taking a String is how the type-token escape hatch comes "
                        + "back. These three take CONTENT: uString/string take the payload, and "
                        + "opaque(className, repr) writes the name it is given into content() as well "
                        + "as into javaType(), so an untruthful opaque token is a CONTENT difference "
                        + "and stays a DIFFER. Nothing here turns a String into a type token while "
                        + "leaving the content alone. Found instead: " + stringTakingMembers);
        for (java.lang.reflect.Method m : UValue.class.getMethods()) {
            if (m.getDeclaringClass() != UValue.class) {
                continue;
            }
            assertFalse(m.getName().toLowerCase(Locale.ROOT).contains("javatype")
                            && m.getParameterCount() > 0,
                    "UValue." + m.getName() + " takes arguments and names the type token; the only "
                            + "way to set that token is observedFrom(Object). " + m);
        }
        // opaque's String really is content, asserted rather than argued: two opaque values differing
        // only in the class name they were given differ in content(), so the row is a DIFFER.
        assertNotEquals(UValue.opaque("a.b.C", "x").content(), UValue.opaque("d.e.C", "x").content());

        // (4) The note. Same content, same class difference, two attributions of the subject's class --
        //     and the reader can tell them apart. Neither attribution moves the verdict: both rows are
        //     AGREE (the content is identical) and both are counted in javaTypeMismatch.
        UOp op = UOp.unary("URealValue", "neg");
        UValue referenceSide = UValue.integer(7).observedFrom(Integer.valueOf(7));   // a raw int
        Map<String, UValue> subjects = new java.util.LinkedHashMap<>();
        subjects.put("ASSUMED (factory default)", UValue.integer(7));
        subjects.put("OBSERVED (off a real object of another class)",
                UValue.integer(7).observedFrom(new java.util.concurrent.atomic.AtomicInteger(7)));

        System.out.println("=== D-43: the same type-mismatch row, two attributions of the subject ===");
        Map<String, String> notes = new java.util.LinkedHashMap<>();
        subjects.forEach((label, subjectValue) -> {
            try (Candidate ref = new ReturnsFixed("ref", referenceSide);
                 Candidate sub = new ReturnsFixed("sub", subjectValue)) {
                DifferentialSweep.Result result = new DifferentialSweep(ref, sub, 1L)
                        .sweepUnary(op, List.of(UValue.uReal(1.0, 0.0)));
                DiffRow row = result.rows().get(0);
                assertEquals(DiffVerdict.AGREE, row.verdict(),
                        "the provenance must not move the verdict, and a type-only difference is no "
                                + "longer a divergence: " + row.toTsv());
                assertEquals(1, result.javaTypeMismatchCount(),
                        "but it must be counted: " + result.summary());
                notes.put(label, row.note());
                System.out.println("  " + label);
                System.out.println("      " + row.note());
            }
        });
        System.out.println("===================================================================");

        // Both provenances are named on EVERY type-mismatch row, whatever they are. Round 7's note
        // only spoke up when a provenance was not OBSERVED, which is why a laundered row said nothing.
        notes.forEach((label, note) -> assertTrue(note.contains("Provenance: reference OBSERVED"),
                label + " -> " + note));
        assertTrue(notes.get("ASSUMED (factory default)").contains("subject ASSUMED"),
                notes.get("ASSUMED (factory default)"));
        assertTrue(notes.get("ASSUMED (factory default)").contains("D-43"),
                "a row a stage might mistake for a port defect must name the defect that explains it");
        assertTrue(notes.get("ASSUMED (factory default)").contains("observedFrom"),
                "and must name the call that would have made it a measurement");
        assertTrue(notes.get("OBSERVED (off a real object of another class)")
                        .contains("subject OBSERVED"),
                notes.get("OBSERVED (off a real object of another class)"));
        // The hedge -- "this is a finding about the ADAPTER" -- must appear only when the subject's
        // class was assumed. (The D-43 id itself now appears on every type-only row, because it is the
        // id of the demotion the row's verdict rests on; the hedge is the part that attributes.)
        assertTrue(notes.get("ASSUMED (factory default)").contains("finding about the ADAPTER"),
                notes.get("ASSUMED (factory default)"));
        assertFalse(notes.get("OBSERVED (off a real object of another class)")
                        .contains("finding about the ADAPTER"),
                "an observed-vs-observed difference is not an adapter finding and must not be hedged "
                        + "as one: " + notes.get("OBSERVED (off a real object of another class)"));
        assertFalse(notes.get("OBSERVED (off a real object of another class)")
                        .contains("never looked"),
                notes.get("OBSERVED (off a real object of another class)"));
        // And the harness must not certify what it cannot check (D-47): observedFrom believes any
        // object, so the note may not assert that the object came from the implementation.
        notes.values().forEach(note -> assertFalse(note.contains("Both classes were OBSERVED"),
                "the harness cannot vouch for an adapter's choice of object: " + note));
        assertTrue(notes.get("OBSERVED (off a real object of another class)")
                        .contains("not checkable by this harness"),
                notes.get("OBSERVED (off a real object of another class)"));
    }

    // ------------------------------------------------------------------ H21: the provenance aggregate

    /**
     * <strong>H21: {@code rows.javaTypeMismatch} gets a cause, as a header number and not only as
     * prose in a note.</strong>
     *
     * <p>Round 8 closed D-43 by demoting a type-only difference out of the verdict and giving it its
     * own count, {@link DifferentialSweep.Result#javaTypeMismatchCount()}, and by printing both sides'
     * provenance on every such row. What it left open is the aggregate. The count answers "how many
     * rows named two different classes for one payload"; its own Javadoc then says the question that
     * decides what to <em>do</em> about them — a wrong-class port, or an adapter that never looked —
     * is "in the row note's provenance clause, not in this count". So the two cases were
     * distinguishable only by opening the data rows and reading English, and 3 445 rows of it.
     *
     * <p>The construction below is the blindness, then the fix. Two sweeps, both content-perfect, both
     * with <em>exactly the same</em> {@code javaTypeMismatch} total, and opposite causes:
     * <ul>
     *   <li>{@code add} — the subject's adapter takes the factory default. {@code ASSUMED}: nobody
     *       looked, so nothing here is a statement about the port (D-43).</li>
     *   <li>{@code minus} — the subject observed a real object of a genuinely different class.
     *       {@code OBSERVED}: a statement about two implementations, though not a certified one, since
     *       {@code observedFrom} believes any object it is handed (D-47).</li>
     * </ul>
     * Before H21 the two reports' headers were identical on every type line. The last block asserts
     * they are not any more, which is the whole of what H21 buys.
     *
     * <p>Also pinned here: the identity {@code observed + assumed == javaTypeMismatch}, which is what
     * makes the split safe to read as a partition rather than as two loosely related numbers; and the
     * two ways a row can carry no provenance at all — the subject threw ({@code null}) or the
     * subject's result stands for the absence of a result ({@code NONE}) — neither of which may be
     * counted into either half.
     */
    @Test
    @DisplayName("H21: the type-mismatch total is split by the subject's type provenance, in the "
            + "header and per operation")
    void theTypeMismatchTotalIsSplitBySubjectTypeProvenance() throws java.io.IOException {
        UOp assumedOp = UOp.binary("URealValue", "add");
        UOp observedOp = UOp.binary("URealValue", "minus");
        // What Method.invoke hands back for a raw `int` return, which is what the reference observes.
        UValue reference = UValue.integer(7).observedFrom(Integer.valueOf(7));
        UValue assumedSubject = UValue.integer(7);
        UValue observedSubject = UValue.integer(7)
                .observedFrom(new java.util.concurrent.atomic.AtomicInteger(7));
        assertEquals(UValue.TypeProvenance.ASSUMED, assumedSubject.typeProvenance());
        assertEquals(UValue.TypeProvenance.OBSERVED, observedSubject.typeProvenance());
        assertEquals(reference.content(), assumedSubject.content(), "both subjects must be "
                + "content-perfect, or the rows are content findings and belong in DIFFER");
        assertEquals(reference.content(), observedSubject.content());

        List<UValue> domain = List.of(UValue.uReal(1.0, 0.0), UValue.uReal(2.0, 0.0));

        DifferentialSweep.Result assumed;
        DifferentialSweep.Result observed;
        try (Candidate ref = new ReturnsFixed("ref", reference);
             Candidate sub = new ReturnsFixed("sub-assumed", assumedSubject)) {
            assumed = new DifferentialSweep(ref, sub, 1L).sweepBinary(assumedOp, domain, domain);
        }
        try (Candidate ref = new ReturnsFixed("ref", reference);
             Candidate sub = new ReturnsFixed("sub-observed", observedSubject)) {
            observed = new DifferentialSweep(ref, sub, 1L).sweepBinary(observedOp, domain, domain);
        }

        // (1) THE BLINDNESS, asserted as a precondition rather than described. Every figure the
        //     harness published before H21 is equal across the two sweeps.
        assertEquals(4, assumed.javaTypeMismatchCount(), assumed.summary());
        assertEquals(assumed.rowCount(), observed.rowCount());
        assertEquals(assumed.measurementCount(), observed.measurementCount());
        assertEquals(assumed.agreementCount(), observed.agreementCount());
        assertEquals(assumed.disagreements().size(), observed.disagreements().size());
        assertEquals(assumed.javaTypeMismatchCount(), observed.javaTypeMismatchCount(),
                "the two sweeps must be indistinguishable on the pre-H21 numbers, or this test is "
                        + "not measuring the blindness it claims to close");

        // (2) THE SPLIT.
        assertEquals(0, assumed.subjectTypeObservedCount(), assumed.summary());
        assertEquals(4, assumed.subjectTypeAssumedCount(), assumed.summary());
        assertEquals(4, observed.subjectTypeObservedCount(), observed.summary());
        assertEquals(0, observed.subjectTypeAssumedCount(), observed.summary());

        // (3) THE IDENTITY: the two halves partition the population exactly, so a reader may treat a
        //     zero on one side as "all of them are the other". A future change that lets a third
        //     provenance into the AGREE-with-differing-columns population fails here.
        for (DifferentialSweep.Result r : List.of(assumed, observed)) {
            assertEquals(r.javaTypeMismatchCount(),
                    r.subjectTypeObservedCount() + r.subjectTypeAssumedCount(),
                    "observed + assumed must exhaust the type-mismatch population: " + r.summary());
        }

        // (4) Both one-line renderings carry the split. stageStatement prints it unconditionally --
        //     including the zero -- because a stage quoting a pass must not be able to avoid seeing
        //     whether its mismatch rows are about the port or about the adapter.
        System.out.println("=== H21: the same mismatch total, two causes =======================");
        System.out.println("  summary  ASSUMED  " + assumed.summary());
        System.out.println("  summary  OBSERVED " + observed.summary());
        System.out.println("  stage    ASSUMED  "
                + assumed.stageStatement(AcceptedDegenerateOperations.none()));
        System.out.println("  stage    OBSERVED "
                + observed.stageStatement(AcceptedDegenerateOperations.none()));
        System.out.println("===================================================================");
        assertTrue(assumed.summary().contains("javaTypeMismatch=4 (subjectType OBSERVED=0 ASSUMED=4)"),
                assumed.summary());
        assertTrue(observed.summary().contains("javaTypeMismatch=4 (subjectType OBSERVED=4 ASSUMED=0)"),
                observed.summary());
        assertTrue(assumed.stageStatement(AcceptedDegenerateOperations.none())
                        .contains("4 java-type mismatch(es) (subject token OBSERVED on 0, ASSUMED on 4)"),
                assumed.stageStatement(AcceptedDegenerateOperations.none()));
        assertTrue(observed.stageStatement(AcceptedDegenerateOperations.none())
                        .contains("4 java-type mismatch(es) (subject token OBSERVED on 4, ASSUMED on 0)"),
                observed.stageStatement(AcceptedDegenerateOperations.none()));

        // (5) A row that carries no provenance is counted into neither half, and there are exactly two
        //     such shapes. The subject threw: no value, so null.
        // (ThrowsIndexOutOfBounds reads args.get(1).asInt(), so this one sub-check needs an integer
        // domain; the stubs ignore the inputs otherwise.)
        List<UValue> intDomain = List.of(UValue.uInteger(1, 0.0), UValue.uInteger(2, 0.0));
        try (Candidate ref = new ReturnsFixed("ref", reference);
             Candidate sub = new ThrowsIndexOutOfBounds("sub-throws")) {
            DifferentialSweep.Result mixed = new DifferentialSweep(ref, sub, 1L)
                    .sweepBinary(assumedOp, intDomain, intDomain);
            assertEquals(DiffVerdict.MIXED, mixed.rows().get(0).verdict(),
                    mixed.rows().get(0).toTsv());
            assertNull(mixed.rows().get(0).subjectTypeProvenance(),
                    "a subject that produced no value has no class token to have a provenance");
            assertEquals(0, mixed.subjectTypeObservedCount() + mixed.subjectTypeAssumedCount(),
                    mixed.summary());
        }
        // The subject returned the ABSENCE of a result: a provenance of NONE reaches the row, and the
        // row is a DIFFER on its content, so neither half counts it.
        try (Candidate ref = new ReturnsFixed("ref", reference);
             Candidate sub = new ReturnsFixed("sub-null", UValue.nullValue())) {
            DifferentialSweep.Result oneSided = new DifferentialSweep(ref, sub, 1L)
                    .sweepBinary(assumedOp, domain, domain);
            DiffRow row = oneSided.rows().get(0);
            assertEquals(DiffVerdict.DIFFER, row.verdict(), row.toTsv());
            assertEquals(UValue.TypeProvenance.NONE, row.subjectTypeProvenance(), row.toTsv());
            assertEquals(0, oneSided.javaTypeMismatchCount(), oneSided.summary());
            assertEquals(0, oneSided.subjectTypeObservedCount() + oneSided.subjectTypeAssumedCount(),
                    oneSided.summary());
        }

        // (6) THE HEADER, per operation and as a file total.
        java.nio.file.Path both = DiffReportWriter.writeAll("h21-both.tsv",
                List.of(assumed, observed), Map.of(), AcceptedDegenerateOperations.none());
        List<String> header = headerOf(both);
        System.out.println("=== H21: the report header ========================================");
        header.forEach(System.out::println);
        System.out.println("===================================================================");
        assertTrue(header.contains("# rows.javaTypeMismatch\t8"), header.toString());
        assertTrue(header.contains("# rows.subjectTypeObserved\t4"), header.toString());
        assertTrue(header.contains("# rows.subjectTypeAssumed\t4"), header.toString());
        assertTrue(header.contains("# op.URealValue.add(value).subjectTypeObserved\t0"),
                header.toString());
        assertTrue(header.contains("# op.URealValue.add(value).subjectTypeAssumed\t4"),
                header.toString());
        assertTrue(header.contains("# op.URealValue.minus(value).subjectTypeObserved\t4"),
                header.toString());
        assertTrue(header.contains("# op.URealValue.minus(value).subjectTypeAssumed\t0"),
                header.toString());

        // (7) AND THE POINT OF ALL OF IT: two reports whose type-mismatch totals are equal and whose
        //     causes are opposite are no longer byte-indistinguishable in the header. Before H21 the
        //     assertion below could not have been written -- there was no line to write it against.
        List<String> assumedHeader = headerOf(DiffReportWriter.write("h21-assumed.tsv", assumed,
                Map.of(), AcceptedDegenerateOperations.none()));
        List<String> observedHeader = headerOf(DiffReportWriter.write("h21-observed.tsv", observed,
                Map.of(), AcceptedDegenerateOperations.none()));
        assertTrue(assumedHeader.contains("# rows.javaTypeMismatch\t4"), assumedHeader.toString());
        assertTrue(observedHeader.contains("# rows.javaTypeMismatch\t4"),
                "precondition: the one header line that existed before H21 is the SAME in both files");
        assertEquals(List.of("# rows.subjectTypeObserved\t0", "# rows.subjectTypeAssumed\t4"),
                assumedHeader.stream().filter(l -> l.startsWith("# rows.subjectType"))
                        .collect(java.util.stream.Collectors.toList()));
        assertEquals(List.of("# rows.subjectTypeObserved\t4", "# rows.subjectTypeAssumed\t0"),
                observedHeader.stream().filter(l -> l.startsWith("# rows.subjectType"))
                        .collect(java.util.stream.Collectors.toList()));
    }

    // ------------------------------------------------------------------ golden byte comparison

    /**
     * {@code assertMatchesGolden}'s Javadoc says "byte for byte" and it used to compare
     * {@code Files.readAllLines} to {@code Files.readAllLines}, which is blind to line terminators
     * and to a missing final newline: {@code "a\nb\n"} and {@code "a\nb"} both read back as
     * {@code [a, b]}. The comparison was corrected; nothing pinned it, so this does.
     */
    @Test
    @DisplayName("a golden that differs only in its trailing newline is a failure, not a match")
    void goldenComparisonIsBytesAndNotLines() throws java.io.IOException {
        java.nio.file.Path golden = DiffReportWriter.goldenDir().resolve("d-byte-probe.tsv");
        java.nio.file.Path written = DiffReportWriter.reportDir().resolve("d-byte-probe.tsv");
        java.nio.file.Files.createDirectories(golden.getParent());
        java.nio.file.Files.createDirectories(written.getParent());
        try {
            byte[] withNewline = "# harness\tprobe\nindex\n0\n".getBytes(
                    java.nio.charset.StandardCharsets.UTF_8);
            byte[] withoutNewline = "# harness\tprobe\nindex\n0".getBytes(
                    java.nio.charset.StandardCharsets.UTF_8);

            java.nio.file.Files.write(golden, withNewline);
            java.nio.file.Files.write(written, withNewline);
            assertEquals(golden, DiffReportWriter.assertMatchesGolden(written, "d-byte-probe.tsv"),
                    "identical bytes must match");

            java.nio.file.Files.write(written, withoutNewline);
            assertEquals(java.nio.file.Files.readAllLines(golden),
                    java.nio.file.Files.readAllLines(written),
                    "precondition: a LINE comparison cannot tell these two files apart");
            AssertionError e = assertThrows(AssertionError.class,
                    () -> DiffReportWriter.assertMatchesGolden(written, "d-byte-probe.tsv"));
            System.out.println("=== golden byte comparison ========================================");
            System.out.println(e.getMessage());
            System.out.println("===================================================================");
            assertTrue(e.getMessage().contains("in bytes but not in any line"), e.getMessage());

            // A CRLF-for-LF substitution is the same class of difference, and also caught.
            java.nio.file.Files.write(written, "# harness\tprobe\r\nindex\r\n0\r\n".getBytes(
                    java.nio.charset.StandardCharsets.UTF_8));
            assertThrows(AssertionError.class,
                    () -> DiffReportWriter.assertMatchesGolden(written, "d-byte-probe.tsv"));
        } finally {
            java.nio.file.Files.deleteIfExists(golden);
            java.nio.file.Files.deleteIfExists(written);
        }
    }

    // ------------------------------------------------------------------ D-13: wrong throw class

    @Test
    @DisplayName("D-13: a wrong exception class is visible in an aggregate, not only row by row")
    void wrongThrowClassIsVisibleInAnAggregate() {
        UOp op = UOp.of("UStringValue", "at", UOp.ParamKind.INT);
        List<UValue> receivers = List.of(UValue.uString("abc", 0.5));
        List<UValue> indices = List.of(UValue.integer(0), UValue.integer(99));

        try (Candidate ref = new ThrowsIndexOutOfBounds("ref");
             Candidate faithful = new ThrowsIndexOutOfBounds("sub");
             Candidate wrongClass = new ThrowsIllegalState("sub-wrong-class")) {

            DifferentialSweep.Result matched = new DifferentialSweep(ref, faithful, 1L)
                    .sweepBinary(op, receivers, indices);
            DifferentialSweep.Result mismatched = new DifferentialSweep(ref, wrongClass, 1L)
                    .sweepBinary(op, receivers, indices);

            System.out.println("=== D-13: wrong exception class ===================================");
            System.out.println("same class  " + matched.summary());
            System.out.println("wrong class " + mismatched.summary());
            System.out.println("===================================================================");

            // Every other aggregate is bit-identical between the two, which is the defect.
            assertEquals(matched.tally(), mismatched.tally());
            assertEquals(matched.rowCount(), mismatched.rowCount());
            assertEquals(matched.agreementCount(), mismatched.agreementCount());
            assertEquals(matched.disagreements().size(), mismatched.disagreements().size());

            assertEquals(0, matched.throwClassMismatchCount(),
                    "identical throwable classes on both sides: nothing to flag");
            assertEquals(2, mismatched.throwClassMismatchCount(),
                    "the right failure with the wrong exception class must be countable");
            assertTrue(mismatched.summary().contains("throwClassMismatch=2"), mismatched.summary());
            assertTrue(mismatched.rows().get(0).note().contains("java.lang.IllegalStateException"),
                    mismatched.rows().get(0).note());
        }
    }

    // ------------------------------------------------------------------ notes carry both sides

    @Test
    @DisplayName("a two-sided harness failure names BOTH failures, not just the reference's")
    void harnessErrorNoteCarriesBothSides() {
        UOp op = UOp.binary("URealValue", "add");
        try (Candidate a = new MarshallingFailure("ref");
             Candidate b = new MarshallingFailure("sub")) {

            DiffRow row = new DifferentialSweep(a, b, 1L)
                    .sweepBinary(op, List.of(UValue.uReal(1.0, 0.5)), List.of(UValue.uReal(2.0, 0.5)))
                    .rows().get(0);

            System.out.println("=== both-sided HARNESS_ERROR note =================================");
            System.out.println(row.note());
            System.out.println("===================================================================");

            // Both columns read HARNESS_ERROR:...HarnessMarshallingException, so if the note does
            // not carry both messages the subject's reason is recoverable from nowhere at all.
            assertEquals(row.historical(), row.ported(), "sanity: the columns cannot tell them apart");
            assertTrue(row.note().contains("either side"), row.note());
            assertTrue(row.note().contains("reference could not be driven"), row.note());
            assertTrue(row.note().contains("subject could not be driven"), row.note());
            assertTrue(row.note().contains(
                    "cannot marshal UREAL(1.0,0.5)@URealValue for URealValue.add(value) [ref]"),
                    row.note());
            assertTrue(row.note().contains(
                    "cannot marshal UREAL(1.0,0.5)@URealValue for URealValue.add(value) [sub]"),
                    row.note());
        }
    }

    @Test
    @DisplayName("a MIXED note says which side threw and what the other side returned")
    void mixedNoteNamesBothSides() {
        UOp op = UOp.binary("URealValue", "add");
        List<UValue> domain = List.of(UValue.uReal(1.0, 0.5));
        try (StubCandidate stub = StubCandidate.faithful();
             Candidate thrower = new ThrowsRuntime("sub", "TODO: port URealValue.add(value)")) {

            DiffRow row = new DifferentialSweep(stub, thrower, 1L)
                    .sweepBinary(op, domain, domain).rows().get(0);

            assertEquals(DiffVerdict.MIXED, row.verdict());
            assertTrue(row.note().contains("reference returned UREAL(2.0,"), row.note());
            assertTrue(row.note().contains("subject threw java.lang.RuntimeException: TODO: port"),
                    row.note());

            // The lead clause names the side too. It used to read "one side threw and the other
            // returned", which is 67 996 rows of the standing invariant sweep telling the reader to
            // go and work it out from the columns.
            assertTrue(row.note().startsWith("the subject threw and the reference returned."),
                    row.note());
            assertFalse(row.note().contains("one side threw"), row.note());
        }

        // ...and the other way round, which is the case the unattributed phrasing hid.
        try (Candidate thrower = new ThrowsRuntime("ref", "historical blew up");
             StubCandidate stub = StubCandidate.faithful()) {

            DiffRow row = new DifferentialSweep(thrower, stub, 1L)
                    .sweepBinary(op, domain, domain).rows().get(0);

            System.out.println("=== MIXED note, both directions ===================================");
            System.out.println(row.note());
            System.out.println("===================================================================");
            assertEquals(DiffVerdict.MIXED, row.verdict());
            assertTrue(row.note().startsWith("the reference threw and the subject returned."),
                    row.note());
            assertTrue(row.note().contains("reference threw java.lang.RuntimeException: historical "
                    + "blew up"), row.note());
            assertTrue(row.note().contains("subject returned UREAL(2.0,"), row.note());
        }
    }

    @Test
    @DisplayName("an UNSUPPORTED note attributes each reason to the side it came from")
    void unsupportedNoteAttributesEachSide() {
        try (HistoricalOracle oracle = HistoricalOracle.open();
             StubCandidate stub = StubCandidate.faithful()) {

            DiffRow row = new DifferentialSweep(oracle, stub, 1L)
                    .sweepBinary(UOp.binary("SBooleanValue", "and"),
                            List.of(UValue.uBoolean(true, 0.5)), List.of(UValue.uBoolean(false, 0.5)))
                    .rows().get(0);

            assertTrue(row.note().startsWith("no measurement. reference: "), row.note());
            assertTrue(row.note().contains(" / subject: "), row.note());
            assertTrue(row.note().contains("cannot marshal a SBooleanValue receiver"), row.note());
            assertTrue(row.note().contains("implements only"), row.note());
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
            // The name is in the message so that a two-sided failure has two distinguishable
            // reasons; both columns render the same throwable class, so the note is the only place
            // the difference can survive.
            throw new HarnessMarshallingException("cannot marshal " + args.get(0).canonical()
                    + " for " + op.key() + " [" + name + "]");
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

    /**
     * A port that returns the same thing whatever it is asked: the several encodings of "no code
     * here yet". {@code voidValue()} is the one the {@link Candidate} contract asks an adapter
     * author to write for a {@code void} operation.
     */
    private static final class ReturnsFixed implements Candidate {
        private final String name;
        private final UValue fixed;

        ReturnsFixed(String name, UValue fixed) {
            this.name = name;
            this.fixed = fixed;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public UValue invoke(UOp op, List<UValue> args) {
            return fixed;
        }

        @Override
        public boolean supports(UOp op) {
            return true;
        }

        @Override
        public void close() {
        }
    }

    /** The right failure for an out-of-range index: what the historical uDataTypes code raises. */
    private static final class ThrowsIndexOutOfBounds implements Candidate {
        private final String name;

        ThrowsIndexOutOfBounds(String name) {
            this.name = name;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public UValue invoke(UOp op, List<UValue> args) {
            throw new IndexOutOfBoundsException("idx = " + args.get(1).asInt());
        }

        @Override
        public boolean supports(UOp op) {
            return true;
        }

        @Override
        public void close() {
        }
    }

    /** The right failure on the right rows, with the wrong exception class. The D-13 defect. */
    private static final class ThrowsIllegalState implements Candidate {
        private final String name;

        ThrowsIllegalState(String name) {
            this.name = name;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public UValue invoke(UOp op, List<UValue> args) {
            throw new IllegalStateException("index out of range: idx = " + args.get(1).asInt());
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
