package org.tzi.use.uncertainty.differential;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Adversarial tests for {@link IntendedDepartures}, written against the one question that matters:
 * <strong>can this mechanism be used to make a wrong port look right?</strong>
 *
 * <p>It is a mechanism whose entire job is to convert a red row into a not-red row. Every previous
 * defect in this harness had that shape — {@code AGREE_THROWN}, the {@code void}-operation
 * {@code AGREE}, the D-15 single-point codomain — and each was a rule under which some population
 * counted as evidence of a fidelity nobody had measured. So the tests below are organised by the
 * escape route they close, not by the method they call, and several of them deliberately construct a
 * <em>defective</em> port and assert that it still fails.
 *
 * <p>Test-scoped. Not part of the product.
 */
class IntendedDeparturesTest {

    private static final UOp EQUALS = UOp.binary("URealValue", "equals");
    private static final UOp COMPARE = UOp.binary("URealValue", "compareTo");

    private static final String RATIONALE =
            "B7 (user decision 2026-08-17): the fork's UStringValue.equals is constant false because "
            + "String.equals(UString) can never hold; the port delegates to UString.equals. "
            + "b7-fix-plan.md section 1 C1.";

    /** A candidate whose answer is a pure function of the receiver. Total control, no arithmetic. */
    private static final class Scripted implements Candidate {

        private final String name;
        private final Function<UValue, UValue> answer;

        Scripted(String name, Function<UValue, UValue> answer) {
            this.name = name;
            this.answer = answer;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public boolean supports(UOp op) {
            return true;
        }

        @Override
        public String unsupportedReason(UOp op) {
            return "supported";
        }

        @Override
        public UValue invoke(UOp op, List<UValue> args) {
            return answer.apply(args.get(0));
        }

        @Override
        public void close() {
        }
    }

    /** Receivers whose uncertainty is 0 are the "was wrongly false" population. */
    private static List<List<UValue>> tuples(double... uncertainties) {
        List<List<UValue>> out = new ArrayList<>();
        for (double u : uncertainties) {
            out.add(java.util.Arrays.asList(UValue.uReal(2.0, u), UValue.real(2.0)));
        }
        return out;
    }

    /**
     * The fork: {@code equals} answers {@code true} only above an uncertainty of 0.4, and is wrongly
     * {@code false} on the exact receivers the correction is about.
     *
     * <p>It deliberately answers <em>both</em> values across the corpora below. A reference that says
     * one thing on every row is not discriminating (D-15), the stage gate refuses it on clause 3, and
     * a fixture built that way would prove nothing about clause 2 — the first draft of this test was
     * exactly that fixture and failed for exactly that reason.
     */
    private static Candidate forkEquals() {
        return new Scripted("historical", v -> UValue.bool(v.uncertainty() > 0.4));
    }

    /** The port: as the fork, plus the corrected answer at uncertainty 0. */
    private static Candidate portEquals() {
        return new Scripted("ported",
                v -> UValue.bool(v.uncertainty() > 0.4 || v.uncertainty() == 0.0));
    }

    private static IntendedDepartures m11() {
        return IntendedDepartures.builder()
                .declare(EQUALS.key(), "M-11", "BOOLEAN(false)@BooleanValue", "BOOLEAN(true)@BooleanValue",
                        IntendedDepartures.Direction.SUBJECT_IS_WIDER, RATIONALE)
                .build();
    }

    // ------------------------------------------------------------------ the verdict itself

    @Nested
    @DisplayName("the verdict is a measurement and is not an agreement")
    class TheVerdict {

        @Test
        @DisplayName("INTENDED_DEPARTURE does not inflate the agreement count")
        void notAnAgreement() {
            assertAll(
                    () -> assertFalse(DiffVerdict.INTENDED_DEPARTURE.isAgreement(),
                            "an adjudicated difference is still a difference; scoring it an agreement "
                            + "is the AGREE_THROWN mistake in a fourth costume"),
                    () -> assertTrue(DiffVerdict.INTENDED_DEPARTURE.isMeasurement(),
                            "two values were observed and compared, so it IS evidence -- of a "
                            + "difference that was predicted in writing"));
        }

        @Test
        @DisplayName("a declared departure is measured, is not agreed, and is not a gate failure")
        void adjudicated() {
            IntendedDepartures pre = m11();
            DifferentialSweep.Result r = new DifferentialSweep(forkEquals(), portEquals(), 1L,
                    AcceptedThrowPairs.none(), pre).run(EQUALS, tuples(0.0, 0.5, 0.0, 0.25));

            assertAll(
                    () -> assertEquals(2, r.intendedDepartureCount(),
                            "the two zero-uncertainty rows are the ones that move"),
                    () -> assertEquals(2, r.agreementCount(),
                            "the two uncertain rows still genuinely agree at false"),
                    () -> assertEquals(4, r.measurementCount(),
                            "all four rows compared two observed values"),
                    () -> assertEquals(2, r.disagreements().size(),
                            "disagreements() still contains them: they are not agreements"),
                    () -> assertTrue(r.unintendedDisagreements().isEmpty(),
                            "but nothing is left over for gate clause 2"),
                    () -> assertTrue(r.isStagePass(2, AcceptedDegenerateOperations.none(), pre),
                            "so the sweep is a stage pass"));
        }

        @Test
        @DisplayName("the rationale travels onto every row it adjudicates")
        void rationaleTravels() {
            IntendedDepartures pre = m11();
            DifferentialSweep.Result r = new DifferentialSweep(forkEquals(), portEquals(), 1L,
                    AcceptedThrowPairs.none(), pre).run(EQUALS, tuples(0.0));
            String note = r.rows().get(0).note();
            assertAll(
                    () -> assertTrue(note.contains("M-11"), "the ledger row is in the note: " + note),
                    () -> assertTrue(note.contains(RATIONALE),
                            "the written rationale is in the note, so the weakness arrives with the "
                            + "number instead of living in a document the reader may never open"));
        }
    }

    // ------------------------------------------------------------------ what it must NOT excuse

    @Nested
    @DisplayName("it cannot be used to make a wrong port look right")
    class CannotLaunder {

        @Test
        @DisplayName("a pair nobody declared is still DIFFER, on the very operation that has a declaration")
        void undeclaredPairOnADeclaredOperation() {
            // The port is wrong in a SECOND way: on uncertainty 0.5 it answers UREAL instead of a
            // boolean. The M-11 declaration is in force for this operation and does not touch it.
            Candidate broken = new Scripted("ported-broken",
                    v -> v.uncertainty() == 0.0 ? UValue.bool(true) : UValue.uReal(9.0, 9.0));
            IntendedDepartures pre = m11();
            DifferentialSweep.Result r = new DifferentialSweep(forkEquals(), broken, 1L,
                    AcceptedThrowPairs.none(), pre).run(EQUALS, tuples(0.0, 0.5));

            assertAll(
                    () -> assertEquals(1, r.intendedDepartureCount()),
                    () -> assertEquals(1, r.unintendedDisagreements().size(),
                            "the undeclared pair is untouched by the declaration"),
                    () -> assertFalse(r.isStagePass(1, AcceptedDegenerateOperations.none(), pre),
                            "and the stage still fails"));
        }

        @Test
        @DisplayName("a declared pair that moves the WRONG way stays DIFFER")
        void contradictedDirectionIsRefused() {
            // Declared SUBJECT_IS_WIDER (false -> true). The port does the opposite: the reference
            // says true and the port says false. Same operation, both values named in some
            // declaration, and the row must not be excused -- this is rule 5, and it is what turns
            // "discovered" into "pre-registered".
            IntendedDepartures pre = IntendedDepartures.builder()
                    .declare(EQUALS.key(), "M-11", "BOOLEAN(true)@BooleanValue", "BOOLEAN(false)@BooleanValue",
                            IntendedDepartures.Direction.SUBJECT_IS_WIDER, RATIONALE)
                    .build();
            DifferentialSweep.Result r = new DifferentialSweep(
                    new Scripted("historical", v -> UValue.bool(true)),
                    new Scripted("ported", v -> UValue.bool(false)), 1L,
                    AcceptedThrowPairs.none(), pre).run(EQUALS, tuples(0.0, 0.0));

            assertAll(
                    () -> assertEquals(0, r.intendedDepartureCount(),
                            "the prediction was contradicted, so it adjudicates nothing"),
                    () -> assertEquals(2, r.unintendedDisagreements().size()),
                    () -> assertFalse(r.isStagePass(1, AcceptedDegenerateOperations.none(), pre)));
        }

        @Test
        @DisplayName("a declaration written against another operation does not reach this one")
        void scopedToItsOperation() {
            IntendedDepartures pre = IntendedDepartures.builder()
                    .declare(COMPARE.key(), "M-9", "BOOLEAN(false)@BooleanValue", "BOOLEAN(true)@BooleanValue",
                            IntendedDepartures.Direction.SUBJECT_IS_WIDER, RATIONALE)
                    .build();
            DifferentialSweep.Result r = new DifferentialSweep(forkEquals(), portEquals(), 1L,
                    AcceptedThrowPairs.none(), pre).run(EQUALS, tuples(0.0));
            assertEquals(1, r.unintendedDisagreements().size(),
                    "an M-9 declaration on compareTo says nothing about equals");
        }

        @Test
        @DisplayName("a fix that did not land fails the gate even though every row agrees")
        void clause4CatchesTheUnfixedDefect() {
            // THE defect this whole clause exists for. The stage pre-registered M-11 and then forgot
            // to write the fix, so the port still reproduces the fork exactly. Every row AGREEs.
            // Clauses 1, 2 and 3 all pass. Only clause 4 says anything is wrong.
            IntendedDepartures pre = m11();
            DifferentialSweep.Result r = new DifferentialSweep(forkEquals(), forkEquals(), 1L,
                    AcceptedThrowPairs.none(), pre).run(EQUALS, tuples(0.0, 0.5));

            List<String> failures =
                    r.stageGateFailures(1, AcceptedDegenerateOperations.none(), pre);
            assertAll(
                    () -> assertEquals(0, r.unintendedDisagreements().size(),
                            "nothing disagreed -- that is exactly the trap"),
                    () -> assertEquals(1, r.unusedDeclarations().size()),
                    () -> assertFalse(failures.isEmpty(),
                            "a green-looking sweep must still fail: the fix did not land"),
                    () -> assertTrue(failures.toString().contains("never fired"), failures.toString()));
        }

        @Test
        @DisplayName("a departure does not rescue a single-point codomain (clause 3 is untouched)")
        void clause3StillApplies() {
            // Every row departs, so clause 2 is satisfied and clause 4 fires. But the reference said
            // BOOLEAN(false) on every row, so the sweep could not have failed and its agreement
            // figure means nothing. D-15 is not relaxed by pre-registration.
            IntendedDepartures pre = m11();
            DifferentialSweep.Result r = new DifferentialSweep(
                    new Scripted("historical-degenerate", v -> UValue.bool(false)),
                    new Scripted("ported", v -> UValue.bool(true)), 1L,
                    AcceptedThrowPairs.none(), pre).run(EQUALS, tuples(0.0, 0.5, 0.25));

            assertAll(
                    () -> assertEquals(3, r.intendedDepartureCount()),
                    () -> assertEquals(1, r.distinctReferenceValues()),
                    () -> assertFalse(r.isStagePass(1, AcceptedDegenerateOperations.none(), pre),
                            "clause 3 refuses: the reference could not have answered otherwise"));
        }

        @Test
        @DisplayName("a departure does not rescue an empty measurement population (clause 1 is untouched)")
        void clause1StillApplies() {
            IntendedDepartures pre = m11();
            DifferentialSweep.Result r = new DifferentialSweep(forkEquals(), portEquals(), 1L,
                    AcceptedThrowPairs.none(), pre).run(EQUALS, tuples(0.0, 0.5));
            assertFalse(r.isStagePass(500, AcceptedDegenerateOperations.none(), pre),
                    "the measurement floor is not relaxed by adjudication");
        }
    }

    // ------------------------------------------------------------------ the gate cannot be bypassed

    @Nested
    @DisplayName("the gate cannot be reached without naming the mechanism")
    class GateWiring {

        @Test
        @DisplayName("the two-argument gate refuses a sweep that ran with a pre-registration")
        void twoArgFormRefuses() {
            IntendedDepartures pre = m11();
            DifferentialSweep.Result r = new DifferentialSweep(forkEquals(), portEquals(), 1L,
                    AcceptedThrowPairs.none(), pre).run(EQUALS, tuples(0.0, 0.5));
            IllegalStateException e = assertThrows(IllegalStateException.class,
                    () -> r.isStagePass(1, AcceptedDegenerateOperations.none()));
            assertTrue(e.getMessage().contains("three-argument"), e.getMessage());
        }

        @Test
        @DisplayName("the two-argument gate still works for a sweep that ran without one")
        void twoArgFormStillWorksByDefault() {
            DifferentialSweep.Result r = new DifferentialSweep(forkEquals(), forkEquals(), 1L)
                    .run(EQUALS, tuples(0.0, 0.5));
            assertTrue(r.isStagePass(1, AcceptedDegenerateOperations.none())
                            || !r.isDiscriminating(),
                    "the 64 existing call sites are unaffected");
        }

        @Test
        @DisplayName("handing the gate a different list than the sweep ran under is refused")
        void mismatchedListRefused() {
            IntendedDepartures ranUnder = m11();
            IntendedDepartures somethingElse = IntendedDepartures.builder()
                    .declare(EQUALS.key(), "F-4", "BOOLEAN(false)@BooleanValue", "BOOLEAN(true)@BooleanValue",
                            IntendedDepartures.Direction.SUBJECT_IS_WIDER, RATIONALE)
                    .build();
            DifferentialSweep.Result r = new DifferentialSweep(forkEquals(), portEquals(), 1L,
                    AcceptedThrowPairs.none(), ranUnder).run(EQUALS, tuples(0.0));
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                    () -> r.isStagePass(1, AcceptedDegenerateOperations.none(), somethingElse));
            assertTrue(e.getMessage().contains("never touched a row"), e.getMessage());
        }

        @Test
        @DisplayName("an equal list rebuilt from the same source is accepted")
        void equalListAccepted() {
            IntendedDepartures ranUnder = m11();
            DifferentialSweep.Result r = new DifferentialSweep(forkEquals(), portEquals(), 1L,
                    AcceptedThrowPairs.none(), ranUnder).run(EQUALS, tuples(0.0, 0.5));
            assertTrue(r.isStagePass(2, AcceptedDegenerateOperations.none(), m11()),
                    "comparison is by declaration identity, not object identity");
        }
    }

    // ------------------------------------------------------------------ the bounded form

    @Nested
    @DisplayName("the population form names an exact set, written out")
    class Population {

        private final List<String> pairs = java.util.Arrays.asList(
                "INTEGER(0)@IntegerValue\tINTEGER(-1)@IntegerValue",
                "INTEGER(0)@IntegerValue\tINTEGER(1)@IntegerValue");

        private IntendedDepartures m9(int rows, List<String> distinct) {
            return IntendedDepartures.builder()
                    .declarePopulation(COMPARE.key(), "M-9", rows, distinct,
                            IntendedDepartures.Direction.SUBJECT_ORDERS_WHERE_REFERENCE_TIED,
                            "B7: UIntegerValue.compareTo delegates without negating, over a delegate "
                            + "with no UIntegerValue arm, so the composite is a constant 0. "
                            + "b7-fix-plan.md section 2 M-9.")
                    .build();
        }

        /** Reference ties everything; the port orders by uncertainty. Two departing rows. */
        private DifferentialSweep.Result sweep(IntendedDepartures pre) {
            return new DifferentialSweep(
                    new Scripted("historical", v -> UValue.integer(0)),
                    new Scripted("ported", v -> UValue.integer(v.uncertainty() > 0.3 ? 1 : -1)),
                    1L, AcceptedThrowPairs.none(), pre).run(COMPARE, tuples(0.5, 0.1));
        }

        @Test
        @DisplayName("it fires on the exact population it names")
        void fires() {
            IntendedDepartures pre = m9(2, pairs);
            DifferentialSweep.Result r = sweep(pre);
            assertAll(
                    () -> assertEquals(2, r.intendedDepartureCount()),
                    () -> assertTrue(r.unintendedDisagreements().isEmpty()),
                    () -> assertTrue(r.unusedDeclarations().isEmpty()));
        }

        @Test
        @DisplayName("it lapses when the row count is off by one")
        void lapsesOnCount() {
            DifferentialSweep.Result r = sweep(m9(3, pairs));
            assertAll(
                    () -> assertEquals(0, r.intendedDepartureCount()),
                    () -> assertEquals(2, r.unintendedDisagreements().size()));
        }

        @Test
        @DisplayName("it lapses when one declared pair is not the one observed")
        void lapsesOnAChangedPair() {
            List<String> shifted = new ArrayList<>(pairs);
            shifted.set(0, "INTEGER(0)@IntegerValue\tINTEGER(-2)@IntegerValue");
            assertEquals(0, sweep(m9(2, shifted)).intendedDepartureCount(),
                    "the declared set must equal the observed set exactly");
        }

        @Test
        @DisplayName("it lapses when the observed set is a strict subset of what was declared")
        void lapsesOnAnUnobservedPair() {
            List<String> extra = new ArrayList<>(pairs);
            extra.add("INTEGER(0)@IntegerValue\tINTEGER(7)@IntegerValue");
            // Three rows, three declared pairs, but only two of them ever occur. The row count
            // matches and the direction holds; the set does not. A declaration that names an answer
            // the port never gives has not been verified by this run and must not adjudicate it.
            DifferentialSweep.Result r = new DifferentialSweep(
                    new Scripted("historical", v -> UValue.integer(0)),
                    new Scripted("ported", v -> UValue.integer(v.uncertainty() > 0.3 ? 1 : -1)),
                    1L, AcceptedThrowPairs.none(), m9(3, extra))
                    .run(COMPARE, tuples(0.5, 0.1, 0.5));
            assertAll(
                    () -> assertEquals(0, r.intendedDepartureCount()),
                    () -> assertEquals(3, r.unintendedDisagreements().size()));
        }

        @Test
        @DisplayName("a pair list longer than the row count is refused at build time")
        void moreDeclaredPairsThanRows() {
            List<String> extra = new ArrayList<>(pairs);
            extra.add("INTEGER(0)@IntegerValue\tINTEGER(7)@IntegerValue");
            assertThrows(IllegalArgumentException.class, () -> m9(2, extra),
                    "three distinct answers cannot occur across two rows");
        }

        @Test
        @DisplayName("it lapses when one pair moves the wrong way")
        void lapsesOnDirection() {
            List<String> tied = java.util.Arrays.asList(
                    "INTEGER(0)@IntegerValue\tINTEGER(0)@IntegerValue",
                    "INTEGER(0)@IntegerValue\tINTEGER(1)@IntegerValue");
            IntendedDepartures pre = m9(2, tied);
            // A port that left one tie in place. INTEGER(0) -> INTEGER(0) is an AGREE, not a
            // departure, so the residual population is one row and the declaration cannot match.
            DifferentialSweep.Result r = new DifferentialSweep(
                    new Scripted("historical", v -> UValue.integer(0)),
                    new Scripted("ported", v -> UValue.integer(v.uncertainty() > 0.3 ? 1 : 0)),
                    1L, AcceptedThrowPairs.none(), pre).run(COMPARE, tuples(0.5, 0.1));
            assertEquals(0, r.intendedDepartureCount());
        }

        @Test
        @DisplayName("a declared pair whose direction is contradicted refuses the whole population")
        void directionIsCheckedPerPair() {
            // Both pairs are observed and the count is right, but the declaration claims a widening
            // of booleans, which INTEGER(0) -> INTEGER(1) is not.
            IntendedDepartures pre = IntendedDepartures.builder()
                    .declarePopulation(COMPARE.key(), "M-9", 2, pairs,
                            IntendedDepartures.Direction.SUBJECT_IS_WIDER, RATIONALE)
                    .build();
            assertEquals(0, sweep(pre).intendedDepartureCount());
        }
    }

    // ------------------------------------------------------------------ the builder's friction

    @Nested
    @DisplayName("the builder refuses what would become a blanket exemption")
    class BuilderFriction {

        @Test
        @DisplayName("no blank argument is accepted")
        void blanksRefused() {
            IntendedDepartures.Builder b = IntendedDepartures.builder();
            assertAll(
                    () -> assertThrows(IllegalArgumentException.class, () -> b.declare(
                            "", "M-11", "a", "b",
                            IntendedDepartures.Direction.REFERENCE_WAS_WRONG, RATIONALE)),
                    () -> assertThrows(IllegalArgumentException.class, () -> b.declare(
                            EQUALS.key(), "M-11", "a", "b",
                            IntendedDepartures.Direction.REFERENCE_WAS_WRONG, "   ")),
                    () -> assertThrows(IllegalArgumentException.class, () -> b.declare(
                            EQUALS.key(), "M-11", "a", "b", null, RATIONALE)));
        }

        @Test
        @DisplayName("a ledger row that names nothing is refused")
        void ledgerRowRequired() {
            IntendedDepartures.Builder b = IntendedDepartures.builder();
            assertAll(
                    () -> assertThrows(IllegalArgumentException.class, () -> b.declare(
                            EQUALS.key(), "because I said so", "a", "b",
                            IntendedDepartures.Direction.REFERENCE_WAS_WRONG, RATIONALE)),
                    () -> assertThrows(IllegalArgumentException.class, () -> b.declare(
                            EQUALS.key(), "M11", "a", "b",
                            IntendedDepartures.Direction.REFERENCE_WAS_WRONG, RATIONALE)));
        }

        @Test
        @DisplayName("declaring an identical pair is refused: that is an AGREE, not a departure")
        void identicalPairRefused() {
            assertThrows(IllegalArgumentException.class, () -> IntendedDepartures.builder().declare(
                    EQUALS.key(), "M-11", "BOOLEAN(true)@BooleanValue", "BOOLEAN(true)@BooleanValue",
                    IntendedDepartures.Direction.REFERENCE_WAS_WRONG, RATIONALE));
        }

        @Test
        @DisplayName("the population form refuses an unreadable pair list, and an empty one")
        void populationIsCapped() {
            IntendedDepartures.Builder b = IntendedDepartures.builder();
            List<String> tooMany = new ArrayList<>();
            for (int i = 0; i <= IntendedDepartures.BOUNDED_CAP; i++) {
                tooMany.add("INTEGER(0)@IntegerValue\tINTEGER(" + i + ")@IntegerValue");
            }
            List<String> one = java.util.Arrays.asList(
                    "INTEGER(0)@IntegerValue\tINTEGER(1)@IntegerValue");
            assertAll(
                    () -> assertThrows(IllegalArgumentException.class, () -> b.declarePopulation(
                            EQUALS.key(), "F-4", 500, tooMany,
                            IntendedDepartures.Direction.SUBJECT_IS_WIDER, RATIONALE)),
                    () -> assertThrows(IllegalArgumentException.class, () -> b.declarePopulation(
                            EQUALS.key(), "F-4", 5, java.util.Collections.emptyList(),
                            IntendedDepartures.Direction.SUBJECT_IS_WIDER, RATIONALE)),
                    () -> assertThrows(IllegalArgumentException.class, () -> b.declarePopulation(
                            EQUALS.key(), "F-4", 5,
                            java.util.Arrays.asList("no-tab-here"),
                            IntendedDepartures.Direction.SUBJECT_IS_WIDER, RATIONALE)),
                    () -> assertThrows(IllegalArgumentException.class, () -> b.declarePopulation(
                            EQUALS.key(), "F-4", 0, one,
                            IntendedDepartures.Direction.SUBJECT_IS_WIDER, RATIONALE)),
                    () -> assertThrows(IllegalArgumentException.class, () -> b.declarePopulation(
                            EQUALS.key(), "F-4", 4,
                            java.util.Arrays.asList(one.get(0), one.get(0)),
                            IntendedDepartures.Direction.SUBJECT_IS_WIDER, RATIONALE)));
        }

        @Test
        @DisplayName("none() adjudicates nothing")
        void noneIsInert() {
            assertAll(
                    () -> assertTrue(IntendedDepartures.none().isEmpty()),
                    () -> org.junit.jupiter.api.Assertions.assertNull(IntendedDepartures.none()
                            .adjudicate(EQUALS.key(), "BOOLEAN(false)@BooleanValue", "BOOLEAN(true)@BooleanValue")));
        }
    }

    // ------------------------------------------------------------------ canonical splitting

    @Nested
    @DisplayName("content is split off the type token without guessing")
    class ContentSplit {

        @Test
        @DisplayName("a type-bearing canonical form loses only its suffix")
        void suffixStripped() {
            assertEquals("UREAL(2.0,0.5)",
                    IntendedDepartures.contentOf("UREAL(2.0,0.5)@URealValue"));
        }

        @Test
        @DisplayName("an at-sign inside a quoted string is not mistaken for a suffix")
        void atInsideAString() {
            assertEquals("STRING(\"a@b\")", IntendedDepartures.contentOf("STRING(\"a@b\")"));
        }

        @Test
        @DisplayName("a form with no suffix passes through")
        void noSuffix() {
            assertEquals("VOID", IntendedDepartures.contentOf("VOID"));
        }
    }
}
